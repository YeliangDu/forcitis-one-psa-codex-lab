"""Self-test of the acceptance guard at enqueue (#1158): real git (a bare origin, a clone, refs/pull/N/head) and the real guard script."""
import contextlib
import io
import os
import subprocess

import mq_acceptance

ACCEPTANCE_FILE = "backend/src/test/java/com/forcitis/one/acceptance/x/FooIT.java"


def run(mq, d):
    def g(cwd, *a):
        r = subprocess.run(["git", "-c", "commit.gpgsign=false", "-c", "user.name=t", "-c", "user.email=t@t", *a], cwd=cwd, capture_output=True, text=True)
        assert r.returncode == 0, (a, r.stderr)
        return r.stdout.strip()

    def commit(work, path, text, msg):
        os.makedirs(os.path.dirname(os.path.join(work, path)), exist_ok=True)
        open(os.path.join(work, path), "w").write(text)
        g(work, "add", path); g(work, "commit", "-q", "-m", msg)
        return g(work, "rev-parse", "HEAD")
    base = os.path.join(d, "acc")
    os.makedirs(base)
    origin, author, runner = (os.path.join(base, n) for n in ("o.git", "author", "runner"))
    g(base, "init", "-q", "--bare", "-b", "main", origin); g(base, "clone", "-q", origin, author)
    commit(author, "a.txt", "a\n", "base"); g(author, "push", "-q", "origin", "main")
    g(base, "clone", "-q", origin, runner)   # the checkout the queue runs in: has never seen the pull requests
    g(author, "checkout", "-q", "-b", "pr1")
    bad = commit(author, ACCEPTANCE_FILE, "class FooIT {}\n", "change an acceptance test"); g(author, "push", "-q", "origin", "HEAD:refs/pull/1/head")
    g(author, "checkout", "-q", "-b", "pr2", "main")
    good = commit(author, ACCEPTANCE_FILE, "class FooIT {}\n", "change an acceptance test\n\nAcceptance-Test-Author: test-engineer"); g(author, "push", "-q", "origin", "HEAD:refs/pull/2/head")
    g(author, "checkout", "-q", "-b", "pr3", "main")
    plain = commit(author, "backend/src/main/Foo.java", "class Foo {}\n", "ordinary change"); g(author, "push", "-q", "origin", "HEAD:refs/pull/3/head")

    g(author, "checkout", "-q", "-b", "pr4", "main")
    for i in range(10):   # a count that ends in 0 is still a violation
        many = commit(author, ACCEPTANCE_FILE, f"class FooIT {{ int i = {i}; }}\n", f"change {i}")
    g(author, "push", "-q", "origin", "HEAD:refs/pull/4/head")

    err = io.StringIO()
    with contextlib.redirect_stderr(err):
        text = mq_acceptance.check(runner, 1, bad)
        assert text and bad[:10] in text and "Acceptance-Test-Author" in text, ("no trailer is refused with the guard's own message", text)
        assert "10 violating commit(s)" in (mq_acceptance.check(runner, 4, many) or ""), "ten violating commits are refused, not read as a guard that could not run"
        assert mq_acceptance.check(runner, 2, good) is None and mq_acceptance.check(runner, 3, plain) is None, "a trailer, or no acceptance file, passes"
        assert mq_acceptance.check(runner, 9, bad) is None and "cannot fetch" in err.getvalue(), "a pull request that cannot be fetched fails open, said out loud"
    err = io.StringIO()
    with contextlib.redirect_stderr(err):
        shallow = os.path.join(base, "shallow")
        g(base, "clone", "-q", "--depth", "1", f"file://{origin}", shallow)
        assert mq_acceptance.check(shallow, 1, bad) is None and "could not run" in err.getvalue(), "a guard that cannot run (shallow history) fails open, said out loud"

    # through the CLI: the refused pull request is not queued, a fixed one is
    class Probe:
        def view(self, n):
            return {"state": "OPEN", "merge": "CLEAN", "labels": [], "branch": "b", "head": {1: bad, 2: good, 3: plain}[n]}
    real_gh, real_root = mq.Gh, mq.repo_root
    mq.Gh, mq.repo_root = Probe, lambda: runner
    queue = os.path.join(d, "acc-q", "merge.queue")
    try:
        try:
            with contextlib.redirect_stderr(io.StringIO()):
                mq.main(["--queue", queue, "enqueue", "3", "1"])
            raise AssertionError("enqueue of a pull request that breaks the rule must exit")
        except SystemExit as e:
            assert "nothing queued" in str(e) and "#1" in str(e) and "#3" not in str(e).split("\n", 1)[1], str(e)
        assert not os.path.exists(queue) or mq.Queue(queue).numbers() == [], "refusing one refuses the whole call"
        with contextlib.redirect_stderr(io.StringIO()):
            mq.main(["--queue", queue, "enqueue", "3", "2"])
        assert mq.Queue(queue).numbers() == [3, 2]
    finally:
        mq.Gh, mq.repo_root = real_gh, real_root
