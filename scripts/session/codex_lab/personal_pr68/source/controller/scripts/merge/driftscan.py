"""Drift check of the things that went wrong silently on 10-29/30 (GOV-1030-LEAD-01). Report only: it changes, merges, closes and restarts nothing.

Run `driftscan.py [--quiet]` by hand (lead at session start): one line per finding `<REASON> <subject> <detail>`, exit 0 (--quiet prints nothing when clean). `mq.py run` runs it at start and every 24 hours (run_drift):
one DRIFT line in merge.log when there are new findings, the full list in <queue dir>/drift.md; the same finding (reason and subject) is reported at most once per REPEAT_AFTER.
Reasons (REASONS): DROPPED (an open, non-draft pull request outside the queue whose last START/DEQUEUE in merge.log is a DEQUEUE other than MERGED, with no commit since; approved-but-not-queued stays in gapscan),
MARKER_MISSING / MARKER_UNKNOWN / MARKER_DUPLICATE (worktree session marker `<git dir>/one-session`, #856: absent, not a key of policy.json sessions.config, shared by two worktrees),
RUNNER_STALE (the `mq.py run` process started before the last commit on main that touched scripts/merge or policy.json), OWNER_ABSENT (an open issue labelled `会话:<S>`, not `挂起`/hold, and no worktree carries marker S),
HANDOFF_STALE (a #n under an `- Open`/`- Waiting`/`In flight` line of the lead handoff that GitHub reports closed; an open pull request of a one-lead branch the handoff does not cite; no handoff file).
A source that cannot answer (gh down, no git) skips its checks and is named in the errors, never a crash.
"""
import argparse
import datetime
import json
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gapscan   # noqa: E402  (session names, branch -> session, time parsing, atomic write: one copy)

EVERY = 24 * 60 * 60
REPEAT_AFTER = 24 * 60 * 60
REASONS = ("DROPPED", "MARKER_MISSING", "MARKER_UNKNOWN", "MARKER_DUPLICATE", "RUNNER_STALE", "OWNER_ABSENT", "HANDOFF_STALE")
LEAD = "one-lead"
HANDOFF = os.path.expanduser("~/.modernize/psa/state/one-lead/handoff.md")
RESTART = "launchctl kickstart -k gui/$(id -u)/com.forcitis.mq"
HOLD_LABELS = ("挂起", "hold")
OPEN_SECTION = re.compile(r"^\s*(?:-\s*)?(?:Open|Waiting|In flight)\b")
EVENT = re.compile(r"^(\S+) (START|DEQUEUE) pr=#(\d+) run=\S+ elapsed=\d+s(?: lane=\w+)?\s*(.*)$")
PS_LINE = re.compile(r"^\s*(\d+)\s+(\w{3} \w{3}\s+\d+ [\d:]{8} \d{4})\s+(.*)$")


def find_dropped(prs, queued, log_lines):
    """Open, non-draft pull requests that left the queue (the last START/DEQUEUE of merge.log is a DEQUEUE other than MERGED) with no commit since and are not queued again."""
    last = {}
    for line in log_lines:
        m = EVENT.match(line)
        if m:
            last[int(m.group(3))] = (m.group(1), m.group(2), m.group(4))
    found = []
    for pr in prs:
        n = pr["number"]
        event = last.get(n)
        if pr.get("isDraft") or n in queued or not event or event[1] != "DEQUEUE" or event[2] == "MERGED":
            continue
        commits = [gapscan.parse_time(c["committedDate"]) for c in pr.get("commits", []) if c.get("committedDate")]
        if commits and max(commits) > gapscan.parse_time(event[0]):
            continue
        found.append(("DROPPED", f"#{n}", f"left the queue at {event[0]} as {event[2] or '-'} and nothing was pushed since"))
    return found


def find_marker_drift(worktrees, sessions):
    """`worktrees`: [(path, marker text or None)] without the main checkout; `sessions`: the keys of policy.json sessions.config."""
    found, holders = [], {}
    for path, marker in worktrees:
        if marker is None:
            found.append(("MARKER_MISSING", path, "no one-session file in its git dir: python3 ~/.modernize/psa/tools/one-session/start_sheet/start_sheet.py --set <session>"))
        elif marker not in sessions:
            found.append(("MARKER_UNKNOWN", path, f"marker {marker!r} is not a key of policy.json sessions.config"))
        else:
            holders.setdefault(marker, []).append(path)
    found += [("MARKER_DUPLICATE", name, "carried by " + ", ".join(sorted(paths))) for name, paths in sorted(holders.items()) if len(paths) > 1]
    return found


def find_runner_stale(started, last_change):
    """`started`: start time of `mq.py run` (None: not running, nothing to say), `last_change`: time of the last commit on main touching scripts/merge or policy.json (None: unknown)."""
    if started is None or last_change is None or started >= last_change:
        return []
    return [("RUNNER_STALE", "mq.py run", f"started {started.isoformat(timespec='seconds')}, main changed scripts/merge or policy.json at {last_change.isoformat(timespec='seconds')}: {RESTART} (when the queue is empty)")]


def find_owner_absent(issues, markers):
    found = []
    for issue in issues:
        names = [label["name"] for label in issue.get("labels", [])]
        if any(name.lower() in HOLD_LABELS for name in names):
            continue
        for name in names:
            owner = name[len("会话:"):] if name.startswith("会话:") else None
            if owner and owner not in markers:
                found.append(("OWNER_ABSENT", f"#{issue['number']}", f"labelled {name} and no worktree carries marker {owner}"))
    return found


def cited_open(text):
    """The numbers cited under the `- Open`, `- Waiting` and `In flight` lines of the handoff."""
    return sorted({int(n) for line in text.splitlines() if OPEN_SECTION.match(line) for n in re.findall(r"#(\d+)", line)})


def find_handoff_stale(text, states, prs, owners):
    """`states`: {number: "open"|"closed"} for cited_open(text); `prs`: the open pull requests."""
    if text is None:
        return [("HANDOFF_STALE", "handoff", f"{HANDOFF} is missing")]
    found = [("HANDOFF_STALE", f"#{n}", "cited as open in the handoff, GitHub says closed") for n, state in sorted(states.items()) if state == "closed"]
    cited = {int(n) for n in re.findall(r"#(\d+)", text)}
    found += [("HANDOFF_STALE", f"#{pr['number']}", f"open pull request of {pr['headRefName']} is not in the handoff") for pr in prs
              if pr["number"] not in cited and gapscan.author_session(pr["headRefName"], owners) == LEAD]
    return found


def select_new(findings, state, now):
    """The findings to report now; `state` ({"REASON subject": iso time of the last report}) is updated. Same rule as gapscan.select_new, window REPEAT_AFTER."""
    fresh = []
    for f in findings:
        key = f"{f[0]} {f[1]}"
        last = gapscan.parse_time(state.get(key)) if isinstance(state.get(key), str) else None
        if last is None or (now - last).total_seconds() >= REPEAT_AFTER:
            fresh.append(f)
            state[key] = now.isoformat(timespec="seconds")
    for key in list(state):
        last = gapscan.parse_time(state[key]) if isinstance(state[key], str) else None
        if last is None or (now - last).total_seconds() >= REPEAT_AFTER:
            del state[key]
    return fresh


def check(src, queued, log_lines, owners=None, sessions=None):
    """All findings and the names of the sources that could not answer. `src` has open_prs, issues, states, worktrees, runner_started, main_changed, handoff (each None when it cannot say)."""
    sessions = sessions if sessions is not None else set(gapscan.load_owners())
    owners = owners if owners is not None else gapscan.load_owners()
    findings, errors = [], []
    prs, issues, worktrees = src.open_prs(), src.issues(), src.worktrees()
    for name, value in (("pull requests", prs), ("issues", issues), ("worktrees", worktrees)):
        if value is None:
            errors.append(name)
    if prs is not None:
        findings += find_dropped(prs, queued, log_lines)
    if worktrees is not None:
        findings += find_marker_drift(worktrees, sessions)
        if issues is not None:
            findings += find_owner_absent(issues, {m for _, m in worktrees if m})
    started, changed = src.runner_started(), src.main_changed()
    if started is False:
        errors.append("runner process")
        started = None
    if changed is None:
        errors.append("main history")
    findings += find_runner_stale(started, changed)
    text = src.handoff()
    states = src.states(cited_open(text)) if text else {}
    if states is None or prs is None:
        errors.append("handoff cross-check")
    else:
        findings += find_handoff_stale(text, states, prs, owners)
    return findings, errors


def render(findings):
    return "".join(f"{reason} {subject} {detail}\n" for reason, subject, detail in findings)


def scan(src, queue, directory, log, now=None, owners=None, sessions=None):
    """One scan of the runner: new findings to the log and drift.md, errors to the log. Returns the findings reported as new."""
    now = now or datetime.datetime.now(datetime.timezone.utc)
    try:
        with open(os.path.join(directory, "merge.log"), encoding="utf-8") as f:
            lines = f.read().splitlines()
    except OSError:
        lines = []
    findings, errors = check(src, set(queue.numbers()), lines, owners, sessions)
    if errors:
        log.event("ERROR", None, None, None, "drift check: could not read " + ", ".join(errors))
    state_path = os.path.join(directory, "drift.state.json")
    try:
        with open(state_path, encoding="utf-8") as f:
            state = json.load(f)
        if not isinstance(state, dict):
            raise ValueError("not an object")
    except FileNotFoundError:
        state = {}
    except ValueError as error:
        log.event("ERROR", None, None, None, f"drift check: {state_path} is damaged ({error}), starting from an empty state: everything is reported once more")
        state = {}
    fresh = select_new(findings, state, now)
    if fresh:
        marked = set(fresh)
        gapscan.write_atomic(os.path.join(directory, "drift.md"), f"# Drift ({now.isoformat(timespec='seconds')}), new since the last report marked *\n\n"
                             + "".join(("* " if f in marked else "  ") + line for f, line in zip(findings, render(findings).splitlines(True))))
        log.event("DRIFT", None, None, None, f"{len(fresh)} findings: " + "; ".join(f"{r} {s}" for r, s, _ in fresh[:6]) + (" ..." if len(fresh) > 6 else "") + f" (all in {os.path.join(directory, 'drift.md')})")
    gapscan.write_atomic(state_path, json.dumps(state))   # last: a failed drift.md write must leave the findings unreported, not spent
    return fresh


class Live:
    """The real sources: git, gh, ps, the handoff file. Every method returns None when its source cannot answer."""
    def __init__(self, handoff=HANDOFF):
        self.handoff_path = handoff

    @staticmethod
    def run(args, cwd=None):
        try:
            r = subprocess.run(args, cwd=cwd, capture_output=True, text=True, timeout=120)
        except (OSError, subprocess.TimeoutExpired):
            return None
        return r.stdout if r.returncode == 0 else None

    def gh_json(self, args):
        out = self.run(["gh", *args])
        try:
            return json.loads(out) if out is not None else None
        except ValueError:
            return None

    def open_prs(self):
        prs = self.gh_json(["pr", "list", "--state", "open", "--limit", str(gapscan.OPEN_LIMIT), "--json", "number,headRefName,isDraft,commits"])   # more than 100 nodes per page is refused by GitHub (see gapscan.OPEN_LIMIT)
        return prs[:gapscan.OPEN_LIMIT - 1] if prs is not None else None

    def issues(self):
        return self.gh_json(["issue", "list", "--state", "open", "--limit", "200", "--json", "number,labels"])

    def states(self, numbers):
        states = {}
        for n in numbers:
            out = self.run(["gh", "api", f"repos/{{owner}}/{{repo}}/issues/{n}", "--jq", ".state"])
            if out is None:
                return None
            states[n] = out.strip()
        return states

    def worktrees(self):
        out = self.run(["git", "worktree", "list", "--porcelain"])
        if out is None:
            return None
        found = []
        for block in out.split("\n\n")[1:]:   # the first entry is the main checkout
            lines = block.splitlines()
            if not lines or not lines[0].startswith("worktree ") or "bare" in lines or any(l.startswith("prunable") for l in lines):
                continue
            path = lines[0][len("worktree "):]
            git_dir = self.run(["git", "rev-parse", "--absolute-git-dir"], cwd=path)
            marker = None
            if git_dir:
                try:
                    with open(os.path.join(git_dir.strip(), "one-session"), encoding="utf-8") as f:
                        marker = f.read().strip() or None
                except OSError:
                    pass
            found.append((path, marker))
        return found

    def runner_started(self):
        """Start time of the oldest `mq.py run` process, None when none runs, False when ps cannot say. The runner finds itself here: no process is skipped."""
        out = self.run(["ps", "-axo", "pid=,lstart=,command="])
        if out is None:
            return False
        times = []
        for line in out.splitlines():
            m = PS_LINE.match(line)
            words = m.group(3).split() if m else []
            if len(words) >= 2 and words[-2].endswith("mq.py") and words[-1] == "run":
                times.append(datetime.datetime.strptime(m.group(2), "%a %b %d %H:%M:%S %Y").astimezone())
        return min(times) if times else None

    def main_changed(self):
        out = self.run(["git", "log", "-1", "--format=%cI", "main", "--", "scripts/merge", "policy.json"])
        return gapscan.parse_time(out.strip()) if out and out.strip() else None

    def handoff(self):
        try:
            with open(self.handoff_path, encoding="utf-8") as f:
                return f.read()
        except OSError:
            return None


def main(argv=None):
    argv = sys.argv[1:] if argv is None else argv
    if argv == ["--self-test"]:
        import driftscan_selftest
        driftscan_selftest.run()
        print("driftscan self-test: ok")
        return
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--quiet", action="store_true", help="print nothing when clean")
    p.add_argument("--queue", help="the queue file (default: <git common dir>/merge-queue/merge.queue)")
    a = p.parse_args(argv)
    sys.path.insert(0, HERE)
    import mq
    queue = mq.Queue(a.queue or mq.default_queue())
    try:
        with open(os.path.join(os.path.dirname(os.path.abspath(queue.path)), "merge.log"), encoding="utf-8") as f:
            lines = f.read().splitlines()
    except OSError:
        lines = []
    findings, errors = check(Live(), set(queue.numbers()), lines)
    for source in errors:
        print(f"driftscan: could not read {source}", file=sys.stderr)
    if findings or not a.quiet:
        print(render(findings) or "no drift", end="")


if __name__ == "__main__":
    main()
