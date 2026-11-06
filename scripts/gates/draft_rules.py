"""The rules of the first-draft check (#1561), shared by the PR-opening gate (scripts/gates/pr_open_gate.py) and the merge queue's enqueue check (scripts/merge/draft_check.py). Only the standard library: the installed hooks hold scripts/gates alone.

A pull request that changes backend runtime code (`backend/src/main/**`) must say, in its body, that Codex drafted it, and the claim must be true.
The body carries one or more lines `Codex: <session-id>[, <session-id>...]` (one line per module drafted) or `Codex: skip <reason>`.
- Each session id must name a file ~/.codex/sessions/**/rollout-<time>-<id>.jsonl whose first record is a `session_meta` with originator `codex_exec`, a cwd inside the author's worktree and a timestamp after the branch was cut from main.
- `Codex: skip small` is accepted when the `backend/src/main/**` part of the diff changes fewer than SMALL lines (added + deleted). Any other `Codex: skip <reason>` (and `skip small` on a bigger diff) is accepted only when the pull request has a comment holding LEAD_OK posted after the body was last edited (at PR opening there is no comment yet: comments=None, such a line passes there and is judged at enqueue).
- A missing line, an id that matches nothing, or a skip without its condition is a refusal. A tool that cannot answer (no ~/.codex) is not: it raises ToolError and the caller lets the pull request through with a warning (ops playbook: fail open)."""
import datetime
import glob
import json
import os
import re

import pr_body

KEY = "Codex"
BACKEND = "backend/src/main/"
SMALL = 20
LEAD_OK = "lead 准许跳过 Codex"
ORIGINATOR = "codex_exec"
SESSION_ID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
FORMS = ("Accepted forms in the pull request body (a line of its own): `Codex: <session-id>[, <session-id>...]` (the id of each `codex exec` run that drafted the backend change, one line per module; "
         "the run's file is ~/.codex/sessions/<y>/<m>/<d>/rollout-<time>-<id>.jsonl), `Codex: skip small` (only when backend/src/main changes fewer than "
         f"{SMALL} lines), or `Codex: skip <reason>` together with a comment `{LEAD_OK}` posted after the body was last edited.")




class ToolError(Exception):
    """A tool could not answer (gh, git, ~/.codex): the check did not happen, which is not a refusal."""


def parse_time(text):
    try:
        return datetime.datetime.fromisoformat(str(text).replace("Z", "+00:00"))
    except ValueError:
        return None


def backend_part(stats):
    """(files, changed lines) of the runtime backend part of a diff; stats is [(path, previous path or None, additions, deletions)]."""
    mine = [s for s in stats if s[0].startswith(BACKEND) or (s[1] or "").startswith(BACKEND)]
    return len(mine), sum(s[2] + s[3] for s in mine)


def session_problem(sid, sessions_dir, worktree, base_time):
    """Why the session id does not prove a Codex draft of this branch, or None. Raises ToolError when ~/.codex cannot be read."""
    if not SESSION_ID.fullmatch(sid):
        return f"`{sid}` is not a session id (lower-case 8-4-4-4-12 hex)"
    if not os.path.isdir(sessions_dir) or not os.access(sessions_dir, os.R_OK | os.X_OK):
        raise ToolError(f"cannot read {sessions_dir}")
    found = [f for f in glob.glob(os.path.join(glob.escape(sessions_dir), "**", f"rollout-*-{sid}.jsonl"), recursive=True)
             if re.fullmatch(r"rollout-\d{4}-\d\d-\d\dT\d\d-\d\d-\d\d-" + re.escape(sid) + r"\.jsonl", os.path.basename(f))]
    if not found:
        return f"session {sid}: no file rollout-*-{sid}.jsonl under {sessions_dir}"
    try:
        with open(found[0], encoding="utf-8") as f:
            first = json.loads(f.readline())
        meta = first["payload"] if first.get("type") == "session_meta" else None
    except (OSError, ValueError, KeyError, TypeError, AttributeError) as error:
        return f"session {sid}: first record is not readable ({error.__class__.__name__})"
    if not isinstance(meta, dict):
        return f"session {sid}: first record is not a session_meta"
    if meta.get("id") != sid:
        return f"session {sid}: the file's session_meta names another id"
    if meta.get("originator") != ORIGINATOR:
        return f"session {sid}: originator is {meta.get('originator')!r}, not {ORIGINATOR!r} (only `codex exec` runs count, not Codex Desktop)"
    cwd = meta.get("cwd")
    if not isinstance(cwd, str) or not os.path.isabs(cwd):
        return f"session {sid}: no usable cwd"
    here = os.path.realpath(cwd)
    if worktree is not None:
        top = os.path.realpath(worktree)
        if not (here == top or here.startswith(top + os.sep)):
            return f"session {sid}: ran in {cwd}, not inside the author's worktree {worktree}"
    elif os.sep + os.path.join(".claude", "worktrees") + os.sep not in here + os.sep:
        return f"session {sid}: ran in {cwd}, which is not a session worktree (.claude/worktrees/<name>)"
    at = parse_time(meta.get("timestamp"))
    if at is None:
        return f"session {sid}: no timestamp"
    if at.tzinfo is None:
        at = at.replace(tzinfo=datetime.timezone.utc)
    if base_time is not None and at <= base_time:
        return f"session {sid}: started {at.isoformat()}, not after the branch was cut from main ({base_time.isoformat()})"
    return None


def judge(stats, body, comments, edited_at, worktree, base_time, sessions_dir):
    """The refusal text, or None when the pull request needs no Codex line or has valid ones. Raises ToolError when a tool cannot answer."""
    files, changed = backend_part(stats)
    if not files:
        return None
    values = pr_body.lines(body, KEY)
    if not values:
        return f"backend/src/main is changed ({files} files, {changed} lines) but the body has no `Codex:` line. {FORMS}"
    problems = []
    for value in values:
        if value.lower().startswith("skip"):
            reason = value[4:].strip()
            if not reason:
                problems.append("`Codex: skip` needs a reason (`Codex: skip small` or `Codex: skip <reason>`)")
            elif reason == "small" and changed < SMALL:
                continue
            elif comments is None or any(LEAD_OK in c["body"] and parse_time(c["at"]) and edited_at and parse_time(c["at"]) > edited_at for c in comments):
                continue
            elif reason == "small":
                problems.append(f"`Codex: skip small` is for fewer than {SMALL} changed lines of backend/src/main; this change has {changed}")
            else:
                problems.append(f"`Codex: skip {reason}` needs a comment `{LEAD_OK}` posted after the body was last edited"
                                f"{f' ({edited_at.isoformat()})' if edited_at else ''}; none found")
            continue
        listed = pr_body.ids(value)
        if not listed:
            problems.append("`Codex:` line without a session id")
        for sid in listed:
            why = session_problem(sid, sessions_dir, worktree, base_time)
            if why:
                problems.append(why)
    return None if not problems else "backend/src/main is changed and the `Codex:` lines do not hold:\n- " + "\n- ".join(problems) + "\n" + FORMS


def sessions_root():
    return os.path.join(os.path.expanduser("~"), ".codex", "sessions")
