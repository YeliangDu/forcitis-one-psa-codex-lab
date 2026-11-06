"""The first-draft check at `mq.py enqueue` (#1561): a pull request that changes backend/src/main without valid `Codex:` lines in its body is refused before it is queued.
The rules are scripts/gates/draft_rules.py, the same ones the PR-opening gate applies; this gathers the facts from what GitHub and the queue's checkout hold now (the head the queue would merge).
Whatever cannot be read (gh, git, ~/.codex) fails open: the pull request is queued and stderr says the check did not happen."""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "gates"))
import draft_rules  # noqa: E402
from draft_rules import ToolError, parse_time  # noqa: E402
from mq_github import sh  # noqa: E402


def worktree_of(root, branch):
    """The path of the worktree that has `branch` checked out, or None."""
    r = sh(["git", "worktree", "list", "--porcelain"], cwd=root, timeout=30)
    if r.returncode:
        raise ToolError(f"git worktree list failed: {(r.stderr or '').strip()[:200]}")
    path = None
    for line in r.stdout.splitlines():
        if line.startswith("worktree "):
            path = line[9:]
        elif line == f"branch refs/heads/{branch}":
            return path
    return None


def branch_cut_time(root, branch, head):
    """When the branch was cut from main: the oldest entry of its reflog (a rebase or an update from main moves the merge base, not this), else the committer date of the merge base with origin/main."""
    r = sh(["git", "reflog", "show", "--format=%cI", f"refs/heads/{branch}"], cwd=root, timeout=30)
    dates = [x for x in r.stdout.split() if x] if r.returncode == 0 else []
    if dates:
        return parse_time(dates[-1])
    mb = sh(["git", "merge-base", "origin/main", head], cwd=root, timeout=30)
    if mb.returncode or not mb.stdout.strip():
        raise ToolError(f"cannot find the merge base of {head[:10]} with origin/main")
    when = sh(["git", "show", "-s", "--format=%cI", mb.stdout.strip()], cwd=root, timeout=30)
    if when.returncode:
        raise ToolError("git show failed on the merge base")
    return parse_time(when.stdout.strip())


def enforce(root, gh, numbers, sessions_dir=None):
    """Exit with nothing queued when an open pull request changes backend/src/main without valid `Codex:` lines; whatever cannot be checked is let through, said on stderr."""
    refused = []
    for n in numbers:
        try:
            v = gh.view(n)
            if not (v and v["state"] == "OPEN"):
                raise ToolError("gh cannot say its state")
            stats = gh.file_stats(n)
            if stats is None:
                raise ToolError("gh cannot list its files")
            if not draft_rules.backend_part(stats)[0]:
                continue
            comments, edited = gh.comments(n), gh.body_edited_at(n)
            if comments is None or edited is None:
                raise ToolError("gh cannot read its comments or when the body was edited")
            fetched = sh(["git", "fetch", "-q", "origin", "main", f"refs/pull/{n}/head"], cwd=root, timeout=60)
            if fetched.returncode:
                raise ToolError(f"cannot fetch it ({(fetched.stderr or '').strip()[:200]})")
            why = draft_rules.judge(stats, v["body"], comments, parse_time(edited), worktree_of(root, v["branch"]), branch_cut_time(root, v["branch"], v["head"]), sessions_dir or draft_rules.sessions_root())
        except Exception as error:   # ToolError, or anything else the gathering hits (a missing checkout, a parse): the check is a help, never a reason to stop the queue
            print(f"mq: first-draft check not run for #{n}: {error}; queued without the check", file=sys.stderr)
            continue
        if why:
            refused.append(f"#{n} (head {v['head'][:10]}): {why}")
    if refused:
        raise SystemExit("nothing queued: backend runtime code needs its Codex first draft on record.\n" + "\n".join(refused))
