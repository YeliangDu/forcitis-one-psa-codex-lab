#!/usr/bin/env python3
"""Self-test of the enqueue-time first-draft check (draft_check.py, #1561): a fake gh, a real temporary git repository with an origin and a session worktree, a temporary sessions directory.
The rules themselves are tested in scripts/gates/draft_selftest.py; this tests the gathering and the refusal at `mq.py enqueue`.
Usage: draft_check_selftest.py"""
import contextlib
import datetime
import io
import json
import os
import subprocess
import sys
import tempfile
from unittest import mock

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import draft_check  # noqa: E402

SID = "d1a18901-dd25-71b0-addd-7a5fcb6d205b"


def git(cwd, *args):
    r = subprocess.run(["git", "-c", "user.name=t", "-c", "user.email=t@t", "-c", "commit.gpgsign=false", *args], cwd=cwd, capture_output=True, text=True)
    assert r.returncode == 0, (args, r.stderr)
    return r.stdout.strip()


class FakeGh:
    def __init__(self, files, body, branch, head, comments=(), edited="2026-11-06T10:00:00Z", state="OPEN"):
        self.files, self.body, self.branch, self.head, self.edited, self.state = files, body, branch, head, edited, state
        self.comment_list = None if comments is None else list(comments)

    def view(self, n):
        return {"state": self.state, "merge": "CLEAN", "labels": [], "branch": self.branch, "head": self.head, "title": "t", "body": self.body}

    def file_stats(self, n):
        return self.files

    def comments(self, n):
        return self.comment_list

    def body_edited_at(self, n):
        return self.edited


def run(root, gh, sessions):
    err = io.StringIO()
    try:
        with contextlib.redirect_stderr(err):
            draft_check.enforce(root, gh, [7], sessions_dir=sessions)
    except SystemExit as e:
        return str(e), err.getvalue()
    return None, err.getvalue()


def wiring_test(tmp):
    """`mq.py enqueue` runs the check: a refusal from it leaves the queue file empty, and it runs after the checks that were there."""
    import mq
    queue = os.path.join(tmp, "queue")
    calls = []
    def refuse(*a):
        calls.append("draft")
        raise SystemExit("nothing queued: draft check said no")
    with mock.patch.object(mq.mq_title, "enforce", lambda *a: calls.append("title")), mock.patch.object(mq.mq_acceptance, "enforce", lambda *a: calls.append("acceptance")), \
            mock.patch.object(mq.mq_event_payload, "enforce", lambda *a: calls.append("payload")), mock.patch.object(mq.mq_enqueue, "enforce", refuse), mock.patch.object(mq, "Gh", lambda: object()):
        try:
            mq.main(["--queue", queue, "enqueue", "7"])
            raise AssertionError("the refusal of the enqueue check must stop the enqueue")
        except SystemExit as e:
            assert "draft check said no" in str(e), e
    assert calls == ["title", "acceptance", "payload", "draft"], calls
    assert not os.path.exists(queue) or "7" not in open(queue).read(), "nothing queued"


def gh_calls_test(tmp):
    """The two gh calls the check added, against a fake gh binary: the GraphQL query carries the real owner and name (gh fills {owner} only in REST paths), the file list parses."""
    import mq_github
    bin_dir = os.path.join(tmp, "bin")
    os.makedirs(bin_dir)
    log = os.path.join(tmp, "calls.log")
    fake = os.path.join(bin_dir, "gh")
    with open(fake, "w") as f:
        f.write(f"""#!/bin/sh
echo "$*" >> {log}
case "$*" in
  "repo view"*) echo "acme/widgets" ;;
  *graphql*) case "$*" in *'owner:"acme",name:"widgets"'*) echo "2026-11-06T10:00:00Z" ;; *) echo "not found" >&2; exit 1 ;; esac ;;
  *"/files"*) printf 'backend/src/main/A.java\\t\\t3\\t1\\nold.md\\tbackend/src/main/B.java\\t0\\t0\\n' ;;
  *) exit 1 ;;
esac
""")
    os.chmod(fake, 0o755)
    old = os.environ["PATH"]
    os.environ["PATH"] = bin_dir + os.pathsep + old
    try:
        gh = mq_github.Gh()
        assert gh.body_edited_at(7) == "2026-11-06T10:00:00Z", "the query must name the repository, not a {owner} placeholder"
        assert gh.file_stats(7) == [("backend/src/main/A.java", None, 3, 1), ("old.md", "backend/src/main/B.java", 0, 0)], gh.file_stats(7)
    finally:
        os.environ["PATH"] = old
    with open(fake, "w") as f:
        f.write("#!/bin/sh\nexit 1\n")
    os.environ["PATH"] = bin_dir + os.pathsep + old
    try:
        assert gh.body_edited_at(7) is None and gh.file_stats(7) is None, "a failing gh is None, which the check treats as fail open"
    finally:
        os.environ["PATH"] = old


def main():
    with tempfile.TemporaryDirectory() as tmp:
        tmp = os.path.realpath(tmp)
        origin, main_co = os.path.join(tmp, "origin.git"), os.path.join(tmp, "main")
        git(tmp, "init", "-q", "--bare", "-b", "main", origin)
        git(tmp, "clone", "-q", origin, main_co)
        open(os.path.join(main_co, "a.txt"), "w").write("a\n")
        git(main_co, "add", "."), git(main_co, "commit", "-q", "-m", "base"), git(main_co, "push", "-q", "origin", "HEAD:main")
        wt = os.path.join(main_co, ".claude", "worktrees", "w1")
        git(main_co, "worktree", "add", "-q", wt, "-b", "feat")
        head = git(wt, "rev-parse", "HEAD")
        git(origin, "update-ref", "refs/pull/7/head", head)   # what `git fetch origin refs/pull/7/head` reads
        assert draft_check.worktree_of(main_co, "feat") == wt and draft_check.worktree_of(main_co, "nope") is None
        cut = draft_check.branch_cut_time(main_co, "feat", head)
        assert abs((datetime.datetime.now(datetime.timezone.utc) - cut).total_seconds()) < 3600
        sessions = os.path.join(tmp, "sessions")
        when = datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(minutes=5)
        folder = os.path.join(sessions, str(when.year), f"{when.month:02d}", f"{when.day:02d}")
        os.makedirs(folder)
        with open(os.path.join(folder, f"rollout-{when.strftime('%Y-%m-%dT%H-%M-%S')}-{SID}.jsonl"), "w") as f:
            f.write(json.dumps({"type": "session_meta", "payload": {"id": SID, "timestamp": when.isoformat(), "cwd": wt, "originator": "codex_exec"}}) + "\n")
        java = [("backend/src/main/java/A.java", None, 40, 0)]
        fake = lambda **kw: FakeGh(**{"files": java, "body": f"Closes #1\n\nCodex: {SID}\n", "branch": "feat", "head": head, **kw})
        # a pull request that is not backend runtime code is never asked
        for files in ([("frontend/a.ts", None, 90, 0)], [("backend/src/test/java/T.java", None, 90, 0)], []):
            assert run(main_co, fake(files=files, body="x"), sessions)[0] is None
        why, err = run(main_co, fake(body="Closes #1\n"), sessions)
        assert why and why.startswith("nothing queued") and "#7" in why and head[:10] in why and "no `Codex:` line" in why, why
        assert run(main_co, fake(), sessions) == (None, "")
        why, _ = run(main_co, fake(body="Codex: 11111111-2222-3333-4444-555555555555\n"), sessions)
        assert why and "no file rollout" in why, why
        # skips: small from the diff, anything else from a lead comment posted after the last body edit
        assert run(main_co, fake(files=[("backend/src/main/A.java", None, 5, 5)], body="Codex: skip small\n"), sessions)[0] is None
        assert "fewer than 20" in run(main_co, fake(body="Codex: skip small\n"), sessions)[0]
        assert "needs a comment" in run(main_co, fake(body="Codex: skip boring\n"), sessions)[0]
        lead = [{"author": "lead", "body": "lead 准许跳过 Codex", "at": "2026-11-06T11:00:00Z"}]
        assert run(main_co, fake(body="Codex: skip boring\n", comments=lead), sessions)[0] is None
        assert "needs a comment" in run(main_co, fake(body="Codex: skip boring\n", comments=lead, edited="2026-11-06T12:00:00Z"), sessions)[0], "a body edited after the permission may hold a new line"
        # a tool that cannot answer lets it through and says so
        for name, gh in (("state", fake(state="MERGED")), ("files", fake(files=None)), ("comments", fake(comments=None)), ("edited", fake(edited=None))):
            why, err = run(main_co, gh, sessions)
            assert why is None and "first-draft check not run for #7" in err and "queued without the check" in err, (name, why, err)
        why, err = run(main_co, fake(), os.path.join(tmp, "no-such-sessions"))
        assert why is None and "not run" in err, (why, err)
        why, err = run(os.path.join(tmp, "not-a-repo"), fake(), sessions)
        assert why is None and "not run" in err, (why, err)
        # a branch that is no longer checked out anywhere: any session worktree of the repository counts
        gh = fake(branch="gone-branch")
        git(main_co, "branch", "gone-branch")
        why, err = run(main_co, gh, sessions)
        assert why is None, (why, err)
        os.makedirs(os.path.join(tmp, "w"))
        wiring_test(os.path.join(tmp, "w"))
        os.makedirs(os.path.join(tmp, "g"))
        gh_calls_test(os.path.join(tmp, "g"))
    print("draft_check_selftest: ok")


if __name__ == "__main__":
    main()
