"""Self-test of the end of a merge (#1392): a merge call that answers with an error although GitHub merged is MERGED, not a retry and not a branch update."""
import os
import subprocess

import mq_selftest as t

ONMAIN = "c" * 40
GIT = {**os.environ, "GIT_AUTHOR_NAME": "t", "GIT_AUTHOR_EMAIL": "t@t", "GIT_COMMITTER_NAME": "t", "GIT_COMMITTER_EMAIL": "t@t", "GIT_CONFIG_GLOBAL": "/dev/null", "GIT_CONFIG_SYSTEM": "/dev/null"}


def git(cwd, *args):
    return subprocess.run(["git", "-C", cwd, *args], env=GIT, check=True, capture_output=True, text=True).stdout.strip()


def run(mq, d):
    # the merge call answers 502, the head is on main and GitHub shows MERGED: one MERGED line with the main commit, no retry, no branch update, no second merge call
    gh = t.FakeGh(); gh.add(40, merge_results=((False, "502 Bad Gateway"),)); gh.prs[40].update(landed=ONMAIN, readback=("MERGED", ["m" * 40, gh.prs[40]["head"]]))
    result, ev, log, _ = t.once(mq, d, "e1", gh, 40)
    assert result == "MERGED" and ev.count("MERGED") == 1 and "RETRY-MERGE" not in ev and "MERGED-UNRECORDED" not in ev, (result, log.lines)
    assert "already on main in cccccccccc" in [x for x in log.lines if " MERGED " in x][0], log.lines
    assert not [c for c in gh.calls if c[0] == "update-branch"] and len([c for c in gh.calls if c[0] == "merge"]) == 1, gh.calls
    # on main but GitHub shows it open: said once for lead, never closed, relabelled or retried
    gh = t.FakeGh(); gh.add(41, merge_results=((False, "502 Bad Gateway"),)); gh.prs[41].update(landed=ONMAIN, readback=("OPEN", []))
    result, ev, log, _ = t.once(mq, d, "e2", gh, 41)
    assert result == "MERGED" and ev.count("MERGED-UNRECORDED") == 1 and "RETRY-MERGE" not in ev and "lead decides" in [x for x in log.lines if "MERGED-UNRECORDED" in x][0], log.lines
    assert not [c for c in gh.calls if c[0] == "update-branch" or (c[0] == "label" and not c[3])] and gh.prs[41]["state"] == "OPEN", gh.calls
    # head not on main: the old path, a retry
    gh = t.FakeGh(); gh.add(42, merge_results=((False, "required status check expected"), (True, "")))
    result, ev, log, _ = t.once(mq, d, "e3", gh, 42)
    assert result == "MERGED" and ev.count("RETRY-MERGE") == 1 and "MERGED-UNRECORDED" not in ev, log.lines
    # landed_on_main on real git: the earliest first-parent commit of main that holds the head; None when the head is not on main or git cannot say
    origin, work = os.path.join(d, "e-origin.git"), os.path.join(d, "e-work")
    git(d, "init", "-q", "--bare", "-b", "main", origin)
    git(d, "clone", "-q", origin, work)
    open(os.path.join(work, "a"), "w").write("a\n")
    git(work, "add", "a"); git(work, "commit", "-qm", "base"); git(work, "push", "-q", "origin", "HEAD:main")
    base = git(work, "rev-parse", "HEAD"); git(work, "checkout", "-qb", "pr"); open(os.path.join(work, "b"), "w").write("b\n")
    git(work, "add", "b"); git(work, "commit", "-qm", "pr work"); head = git(work, "rev-parse", "HEAD")
    git(work, "push", "-q", "origin", "pr")
    git(work, "checkout", "-q", "main"); git(work, "merge", "-q", "--no-ff", "-m", "Merge pr", "pr"); merge = git(work, "rev-parse", "HEAD")
    git(work, "checkout", "-qb", "later"); open(os.path.join(work, "c"), "w").write("c\n")
    git(work, "add", "c"); git(work, "commit", "-qm", "later"); git(work, "checkout", "-q", "main"); git(work, "merge", "-q", "--no-ff", "-m", "Merge later", "later")
    git(work, "push", "-q", "origin", "main")
    git(work, "checkout", "-qb", "open", base); open(os.path.join(work, "z"), "w").write("z\n")
    git(work, "add", "z"); git(work, "commit", "-qm", "not merged"); open_head = git(work, "rev-parse", "HEAD"); git(work, "push", "-q", "origin", "open")
    real = mq.Gh()
    real.fetch = lambda repo, n, commit: subprocess.run(["git", "-C", repo, "fetch", "-q", "origin", "main"], env=GIT).returncode == 0
    assert real.landed_on_main(work, 7, head) == merge, "the earliest main commit that brought the head in, not a later one"
    assert real.landed_on_main(work, 7, open_head) is None, "a head that is not on main"
    assert real.landed_on_main(os.path.join(d, "no-such-repo"), 7, head) is None
