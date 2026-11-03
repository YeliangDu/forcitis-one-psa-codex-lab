"""The acceptance guard at enqueue (#1158): a pull request whose commits change acceptance tests without the trailer is refused before it is queued, not after the queue's CI
(4-25 minutes, then a HOLD and a re-release). The guard is scripts/guards/acceptance_guard.py, the same one light.sh and CI run, against origin/main and the head the pull request has now.
Fails open: when the head cannot be fetched or the guard itself cannot run, the pull request is queued and the output says the check did not happen."""
import os
import re
import sys

from mq_github import sh

GUARD = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "guards", "acceptance_guard.py")
VIOLATIONS = re.compile(r"(?<![0-9])[1-9][0-9]* violating commit\(s\)")   # the guard's last line when it ran and found some (any count, 10 and 20 too); a guard that could not run (shallow history, old git) never prints it


def check(root, number, head):
    """The guard's own text when the head breaks the rule, else None (passed, or could not be checked: said on stderr)."""
    fetched = sh(["git", "fetch", "-q", "origin", "main", f"refs/pull/{number}/head"], cwd=root, timeout=60)
    if fetched.returncode:
        print(f"mq: acceptance guard not run for #{number}: cannot fetch it ({(fetched.stderr or '').strip()[:200]}); queued without the check", file=sys.stderr)
        return None
    ran = sh([sys.executable, GUARD, "origin/main", head], cwd=root, timeout=60)
    text = (ran.stdout + ran.stderr).strip()
    if ran.returncode == 0:
        return None
    if ran.returncode == 1 and VIOLATIONS.search(text):
        return text
    print(f"mq: acceptance guard could not run for #{number} (exit {ran.returncode}): {text[:300]}; queued without the check", file=sys.stderr)
    return None


def enforce(root, gh, numbers):
    """Exit with nothing queued when any pull request breaks the rule; a pull request gh cannot read is let through, said out loud."""
    refused = []
    for n in numbers:
        v = gh.view(n)
        if not (v and v["state"] == "OPEN"):
            print(f"mq: acceptance guard not run for #{n}: gh cannot say its head; queued without the check", file=sys.stderr)
            continue
        text = check(root, n, v["head"])
        if text:
            refused.append(f"#{n} (head {v['head'][:10]}):\n{text}")
    if refused:
        raise SystemExit("nothing queued: acceptance tests changed without a trailer. Fix the commits (or squash them with the trailer, `Acceptance-Test-Author: test-engineer` or "
                         "`Acceptance-Ruling: <id>`), push, and enqueue again.\n" + "\n".join(refused))
