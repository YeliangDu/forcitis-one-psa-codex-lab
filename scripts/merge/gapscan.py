"""Gap scan of the open pull requests, run by `mq.py run` every 30 minutes whether or not the queue is empty (GOV-1022-MRG-05).

A pull request nobody moves is found here, not when the queue happens to run dry. Reasons (REASONS): CONFLICT (text conflict with main), CI_RED (a check whose latest run failed),
APPROVED_NOT_QUEUED (a dispatcher comment `调度批准入队 head <sha>` for the current head and the pull request is not queued), STALE (no commit and no comment for STALE_AFTER).
A pull request in the queue is the queue's business (it logs CONFLICT/HOLD itself) and is skipped; so is a draft. The same pull request with the same reason is reported once per REPEAT_AFTER.

The report is one GAPS line in merge.log and the table in <queue dir>/gaps.md (pull request, author session, reason): `mq.py run` cannot message a session, the session watching merge.log forwards it to the coordinator.
"""
import datetime
import json
import os
import re

SCAN_EVERY = 30 * 60
OPEN_LIMIT = 41   # gh pr list with commits and comments: 50 and more pull requests exceed GitHub's GraphQL node limit; 41 returned means the list was cut off at 40
STALE_AFTER = 60 * 60
REPEAT_AFTER = 4 * 60 * 60
REASONS = ("CONFLICT", "CI_RED", "APPROVED_NOT_QUEUED", "STALE")
APPROVER = "YeliangDu"
APPROVAL = re.compile(r"调度批准入队\s*head\s+([0-9a-f]{7,40})", re.I)
REQUIRED = ("backend verify", "frontend verify", "secret scan")   # the checks the queue merges on (mq.py REQUIRED); anything else red is not a gap
FAILING = ("FAILURE", "TIMED_OUT", "STARTUP_FAILURE", "ERROR")
POLICY = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "policy.json")


def parse_time(text):
    return datetime.datetime.fromisoformat(text.replace("Z", "+00:00")) if text else None


def load_owners(path=POLICY):
    """Session name -> itself, for every key of policy.json sessions.config, the one list of session names. Old names are no owners any more (#856):
    a branch still named after one is reported as unowned, not guessed."""
    with open(path, encoding="utf-8") as f:
        sessions = json.load(f)["sessions"]
    return {name: name for name in sessions["config"]}


def author_session(branch, owners):
    """The session that owns a branch `[claude/]<name>[-|/]...`: <name> is the longest session name (load_owners) the branch starts with, up to a dash or slash.
    No such name: `?(<branch>)`, so that nothing is silently unowned or guessed."""
    rest = branch[len("claude/"):] if branch.startswith("claude/") else branch
    best = max((n for n in owners if rest == n or rest.startswith(n + "-") or rest.startswith(n + "/")), key=len, default=None)
    return owners[best] if best else f"?({branch})"


def approved_head(comments, head):
    """True when the newest dispatcher approval comment names `head` (a head that moved since makes the approval void: the newest comment decides)."""
    head = head.lower()
    heads = [m.group(1).lower() for c in sorted(comments, key=lambda c: c["createdAt"]) if c.get("author") == APPROVER for m in [APPROVAL.search(c.get("body") or "")] if m]
    return bool(heads) and head.startswith(heads[-1])


def red_checks(rollup):
    """Names of the required checks (REQUIRED, a name starting with one of them; the " (light)" run of the docs lane is not one) whose latest run failed: an old red run that a rerun replaced (or that is running again)
    is no longer red; a cancelled or skipped run says nothing and is not looked at. A check run has name/conclusion/startedAt, a commit status context/state."""
    latest = {}
    for c in rollup:
        name = c.get("name") or c.get("context") or ""
        outcome = c.get("conclusion") or c.get("state") or ""
        if outcome in ("CANCELLED", "SKIPPED") or not name.startswith(REQUIRED) or name.endswith(" (light)"):
            continue
        key = (c.get("workflowName") or "", name)
        if key not in latest or (c.get("startedAt") or "") > (latest[key][0] or ""):
            latest[key] = (c.get("startedAt") or "", outcome)
    return sorted(k[1] for k, (_, outcome) in latest.items() if outcome in FAILING)


def last_move(pr):
    times = [parse_time(c["committedDate"]) for c in pr.get("commits", []) if c.get("committedDate")] + [parse_time(c["createdAt"]) for c in pr.get("comments", []) if c.get("createdAt")]
    return max(times) if times else parse_time(pr.get("createdAt"))


def find_gaps(prs, queued, now, owners):
    gaps = []
    for pr in prs:
        n = pr["number"]
        if pr.get("isDraft") or n in queued:
            continue
        who = author_session(pr["headRefName"], owners)
        found = []
        if pr.get("mergeable") == "CONFLICTING":
            found.append(("CONFLICT", "text conflict with main"))
        red = red_checks(pr.get("statusCheckRollup", []))
        if red:
            found.append(("CI_RED", ", ".join(red)))
        if approved_head(pr.get("comments", []), pr["headRefOid"]):
            found.append(("APPROVED_NOT_QUEUED", f"approved head {pr['headRefOid'][:9]}"))
        idle = now - last_move(pr)
        if idle.total_seconds() > STALE_AFTER:
            found.append(("STALE", f"no commit or comment for {int(idle.total_seconds() // 3600)}h"))
        gaps += [dict(pr=n, session=who, reason=r, detail=d, title=pr.get("title", "")[:60]) for r, d in found]
    return gaps


def select_new(gaps, state, now):
    """The gaps to report now; `state` ({"#n:REASON": iso time of the last report}) is updated for them. A key is kept for REPEAT_AFTER whether or not the gap is there in between (a conflict that flickers
    through mergeable=UNKNOWN after every merge into main, a pull request that leaves the list or the queue and comes back are not new): the same pull request and reason is reported once per REPEAT_AFTER."""
    fresh = []
    for g in gaps:
        key = f"#{g['pr']}:{g['reason']}"
        last = parse_time(state.get(key)) if isinstance(state.get(key), str) else None
        if last is None or (now - last).total_seconds() >= REPEAT_AFTER:
            fresh.append(g)
            state[key] = now.isoformat(timespec="seconds")
    for key in list(state):
        last = parse_time(state[key]) if isinstance(state[key], str) else None
        if last is None or (now - last).total_seconds() >= REPEAT_AFTER:
            del state[key]
    return fresh


def render(gaps):
    return "".join(f"#{g['pr']} [{g['session']}] {g['reason']}: {g['detail']} ({g['title']})\n" for g in gaps)


def scan(gh, queue, directory, log, now=None, owners=None):
    """One scan: report to the log and to gaps.md. Returns the gaps reported as new (None when gh could not list the pull requests: logged, nothing else changes)."""
    now = now or datetime.datetime.now(datetime.timezone.utc)
    prs = gh.open_prs()
    if prs is None:
        log.event("ERROR", None, None, None, "gap scan: gh pr list failed")
        return None
    if len(prs) >= OPEN_LIMIT:
        prs = prs[:OPEN_LIMIT - 1]
        log.event("ERROR", None, None, None, f"gap scan: more than {OPEN_LIMIT - 1} open pull requests, the list is cut off: the oldest are not looked at")
    gaps = find_gaps(prs, set(queue.numbers()), now, owners if owners is not None else load_owners())
    state_path = os.path.join(directory, "gaps.state.json")
    try:
        with open(state_path, encoding="utf-8") as f:
            state = json.load(f)
        if not isinstance(state, dict):
            raise ValueError("not an object")
    except FileNotFoundError:
        state = {}
    except ValueError as error:
        log.event("ERROR", None, None, None, f"gap scan: {state_path} is damaged ({error}), starting from an empty state: everything is reported once more")
        state = {}
    fresh = select_new(gaps, state, now)
    if fresh:
        new = {(g["pr"], g["reason"]) for g in fresh}
        text = f"# Open pull requests nobody moves ({now.isoformat(timespec='seconds')}), new since the last report marked *\n\n"
        text += "".join(("* " if (g["pr"], g["reason"]) in new else "  ") + line for g, line in zip(gaps, render(gaps).splitlines(True)))
        write_atomic(os.path.join(directory, "gaps.md"), text)
        log.event("GAPS", None, None, None, f"{len(fresh)} new of {len(gaps)}: " + "; ".join(f"#{g['pr']} {g['session']} {g['reason']}" for g in fresh[:6]) + (" ..." if len(fresh) > 6 else "") + f" (all in {os.path.join(directory, 'gaps.md')})")
    write_atomic(state_path, json.dumps(state))   # last: a failed gaps.md write must leave the gaps unreported, not spent
    return fresh


def write_atomic(path, text):
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        f.write(text)
    os.replace(tmp, path)
