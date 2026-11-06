"""The enqueue checks of #1562, on the kind of pull request (the kinds are pr_kinds.py): decisions made by the user on 2026-11-06 that prose rules of the same kind did not keep.
- no `占号` in a title (a placeholder pull request that takes a number or a slot for later);
- a records PR (every file under docs/decisions/ or docs/rules/rulings/): one per calendar day (Europe/Berlin), merged or queued;
- a rule PR (CLAUDE.md, backend/CLAUDE.md, docs/rules/rules.md, docs/rules/roles/**, docs/rules/playbooks/**): `User-Decision: <path>` or `Incident: #a, #b`, and without a User-Decision at most one per ISO week (Europe/Berlin), merged or queued;
- a PR that adds a gate, guard or hook file (not its self-test): `Incident:` or `User-Decision:`.
A runtime-only PR is never refused here (only the title check applies to it); a PR that also edits a rule file or adds a check file is judged on that part, runtime code or not (lead's ruling of 2026-11-06, option A). Whatever cannot be read (gh, git, the time zone data) lets the pull request through, said on stderr (ops playbook: fail open); a refusal exits with nothing queued."""
import datetime
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "gates"))
import pr_body  # noqa: E402
import pr_kinds  # noqa: E402
from mq_github import sh  # noqa: E402

PLACEHOLDER = "占号"
ZONE = "Europe/Berlin"
FORMS = ("`User-Decision: <path>` (a fragment under docs/decisions/ on main or in this PR, or a file this PR changes that has a line with a 「…」 quote of the user and its date) "
         "or `Incident: #<a>, #<b>` (two different closed issues), each as a line of its own in the PR body")
FORMS_CHECK = ("`Incident: #<a>, #<b>` (two different closed issues) or `User-Decision: <path>` (a fragment under docs/decisions/ on main or in this PR, or a file this PR changes that has a line with a 「…」 quote of the user and its date), "
               "as a line of its own in the PR body")
QUOTE = re.compile(r"「[^」]+」")
DATE = re.compile(r"\d{4}-\d{2}-\d{2}")
ISSUE_REF = re.compile(r"#(\d+)")


class ToolError(Exception):
    """gh, git or the time zone data could not answer: the check did not happen, which is not a refusal."""


def zone():
    try:
        import zoneinfo
        return zoneinfo.ZoneInfo(ZONE)
    except Exception as error:   # no tz database on the machine
        raise ToolError(f"time zone {ZONE} unavailable ({error.__class__.__name__})")


def local(text):
    """The Europe/Berlin datetime of an ISO time (UTC `Z` or offset)."""
    return datetime.datetime.fromisoformat(text.replace("Z", "+00:00")).astimezone(zone())


def week_start(day):
    return day - datetime.timedelta(days=day.isoweekday() - 1)


def git_text(root, ref_path):
    r = sh(["git", "show", ref_path], cwd=root, timeout=60)
    return r.stdout if r.returncode == 0 else None


def user_decision_problem(root, value, paths, n):
    """Why the value of a `User-Decision:` line does not hold for pull request n (changed `paths`), or None."""
    path = value.strip()
    parts = path.split("/")
    if not path or path.startswith("/") or ".." in parts or "." in parts or "\\" in path or " " in path:
        return f"`User-Decision: {value}` is not a repository path"
    in_pr = path in paths
    if path.startswith("docs/decisions/"):
        if in_pr or sh(["git", "cat-file", "-e", f"origin/main:{path}"], cwd=root, timeout=60).returncode == 0:
            return None
        return f"`User-Decision: {path}`: no such fragment on main or in this PR"
    if not in_pr:
        return f"`User-Decision: {path}`: not under docs/decisions/ and not a file this PR changes"
    text = git_text(root, f"refs/mq-records/{n}:{path}")
    if text is None:
        raise ToolError(f"cannot read {path} at the head of #{n}")
    if not any(QUOTE.search(line) and DATE.search(line) for line in text.splitlines()):
        return f"`User-Decision: {path}`: the file has no line with both a 「…」 quote and a date"
    return None


def incident_problem(gh, value):
    """Why the value of an `Incident:` line does not hold, or None; raises ToolError when gh cannot say."""
    rest = ISSUE_REF.sub("", value).replace(",", "").strip()
    refs = [int(x) for x in ISSUE_REF.findall(value)]
    if rest or len(set(refs)) < 2:
        return f"`Incident: {value}` must name two different issues as `#<a>, #<b>`"
    for number in sorted(set(refs)):
        state = gh.issue_state(number)
        if state is None:
            raise ToolError(f"gh cannot say what #{number} is")
        if state != "closed":
            return f"`Incident: {value}`: #{number} is {state}, not a closed issue"
    return None


def grounds(root, gh, body, paths, n):
    """(grounded by User-Decision?, problems): the problems of the declared lines, or the missing declaration; an empty list with True/False when a line holds."""
    decisions, incidents = pr_body.lines(body, "User-Decision"), pr_body.lines(body, "Incident")
    problems = [p for p in (user_decision_problem(root, v, paths, n) for v in decisions) if p]
    problems += [p for p in (incident_problem(gh, v) for v in incidents) if p]
    return bool(decisions) and not problems, problems, bool(decisions or incidents)


def some(items, shown=3):
    return ", ".join(items[:shown]) + (f" and {len(items) - shown} more" if len(items) > shown else "")


def records_problem(n, title, paths, merged, queued_paths, today):
    """Why this records PR is refused (another one today), or None."""
    same_day = [f"#{p['number']} (merged)" for p in merged if p["number"] != n and pr_kinds.is_records(p["paths"]) and local(p["merged_at"]).date() == today]
    same_day += [f"#{q} (queued)" for q, qp in queued_paths.items() if q != n and pr_kinds.is_records(qp)]
    if same_day:
        return (f"records PR (every file under docs/decisions/ or docs/rules/rulings/): one per calendar day ({ZONE}), and today has {some(same_day)}. "
                "Add this change to that day's PR if it is still open, otherwise wait for the next day")
    return None


def rule_cap_problem(n, merged, queued_bodies, today):
    """Why this rule PR without a User-Decision is refused (the week's one is taken), or None."""
    start = week_start(today)
    taken = [f"#{p['number']} (merged)" for p in merged if p["number"] != n and pr_kinds.is_rule(p["paths"]) and not pr_body.lines(p["body"], "User-Decision") and week_start(local(p["merged_at"]).date()) == start]
    taken += [f"#{q} (queued)" for q, (qp, qb) in queued_bodies.items() if q != n and pr_kinds.is_rule(qp) and not pr_body.lines(qb, "User-Decision")]
    if taken:
        return f"at most one rule PR per ISO week ({ZONE}) goes through without a `User-Decision:`, and this week has {some(taken)}; add a `User-Decision:` or wait for next week"
    return None


def judge(root, gh, n, view, paths, added, merged, queued, today):
    """The refusal text for pull request n, or None. `queued` is {number: (paths, body)} of the other queued pull requests; raises ToolError when something cannot be read."""
    if PLACEHOLDER in view["title"]:
        return f"the title has `{PLACEHOLDER}`: a placeholder pull request is not queued; open it when the change is ready"
    new_checks = [p for p, previous in added if pr_kinds.is_check_file(p) and not (previous and pr_kinds.is_check_file(previous))]
    rule = pr_kinds.is_rule(paths)
    if not (pr_kinds.is_records(paths) or rule or new_checks):
        return None
    problems = []
    if pr_kinds.is_records(paths):
        if merged is None:
            raise ToolError("gh cannot list today's merged pull requests")
        why = records_problem(n, view["title"], paths, merged, {q: qp for q, (qp, qb) in queued.items()}, today)
        if why:
            problems.append(why)
    if rule or new_checks:
        decided, declared, any_line = grounds(root, gh, view["body"], paths, n)
        problems += declared
        if rule and not declared:
            if not any_line:
                problems.append(f"rule PR (CLAUDE.md, backend/CLAUDE.md, docs/rules/rules.md, docs/rules/roles/**, docs/rules/playbooks/**) without {FORMS}")
            elif not decided:   # an Incident alone: allowed once a week
                if merged is None:
                    raise ToolError("gh cannot list this week's merged pull requests")
                why = rule_cap_problem(n, merged, queued, today)
                if why:
                    problems.append(why)
        elif new_checks and not declared and not any_line:
            problems.append(f"it adds {', '.join(new_checks[:3])}{' …' if len(new_checks) > 3 else ''} under scripts/gates, scripts/guards or scripts/hooks without {FORMS_CHECK}")
    return "; ".join(problems) if problems else None


def enforce(root, gh, numbers, queued_numbers=(), now=None):
    """Exit with nothing queued when an open pull request breaks one of the rules above; whatever cannot be read is let through, said on stderr."""
    refused, cache = [], {}
    for n in numbers:
        try:
            view = gh.view(n)
            if not (view and view["state"] == "OPEN"):
                raise ToolError("gh cannot say its state")
            paths = gh.file_stats(n)
            if paths is None:
                raise ToolError("gh cannot list its files")
            paths = [x for p in paths for x in (p[0], p[1]) if x]
            added = gh.added_paths(n)
            if added is None:
                raise ToolError("gh cannot list the files it adds")
            now_local = now or datetime.datetime.now(zone())
            today = now_local.astimezone(zone()).date()
            kind = pr_kinds.kind(paths)
            if kind not in ("records", "rules") and not any(pr_kinds.is_check_file(p) for p, _ in added) and PLACEHOLDER not in view["title"]:
                continue   # nothing here concerns it: no gh listing, no git
            if "merged" not in cache:
                cache["merged"] = gh.merged_since_day((week_start(today) - datetime.timedelta(days=1)).isoformat())
                cache["fetch"] = sh(["git", "fetch", "-q", "origin", "main"] + [f"+refs/pull/{x}/head:refs/mq-records/{x}" for x in numbers], cwd=root, timeout=120).returncode
            if cache["fetch"]:
                raise ToolError("git cannot fetch main and the pull request")
            queued = {}
            for q in dict.fromkeys([*queued_numbers, *numbers]):   # the others enqueued in this same call are about to be queued: they count
                if q != n:
                    qv, qs = gh.view(q), gh.file_stats(q)
                    if not qv or qs is None:
                        raise ToolError(f"gh cannot say what queued #{q} holds")
                    queued[q] = ([x for p in qs for x in (p[0], p[1]) if x], qv["body"])
            why = judge(root, gh, n, view, paths, added, cache["merged"], queued, today)
        except Exception as error:   # ToolError, or anything else the gathering hits (a missing checkout, a parse): the check is a help, never a reason to stop the queue
            print(f"mq: records and rules check not run for #{n}: {error}; queued without the check", file=sys.stderr)
            continue
        if why:
            refused.append(f"#{n}: {why}")
    if refused:
        raise SystemExit("nothing queued: the change records, rules and checks of this repository are made on the user's decision (2026-11-06); fix what is named, then enqueue again.\n" + "\n".join(refused))
