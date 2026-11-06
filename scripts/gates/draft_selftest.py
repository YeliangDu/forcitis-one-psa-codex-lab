#!/usr/bin/env python3
"""Self-test of the first-draft check's rules and the PR-opening gate (#1561): pr_body, draft_rules and pr_open_gate, through the dispatcher too. Temporary directories only; HOME is never read (the sessions directory is passed or patched).
Usage: draft_selftest.py (also pr_open_gate.py --self-test). The merge queue's side (draft_check.py: gathering from gh and git) is scripts/merge/draft_check_selftest.py."""
import datetime
import io
import json
import os
import shlex
import subprocess
import sys
import tempfile
from unittest import mock

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import draft_rules as rules  # noqa: E402
import pr_body  # noqa: E402
import pr_open_gate as gate  # noqa: E402
import pre_tool_dispatch as dispatcher  # noqa: E402

SID = "01a18901-dd25-71b0-addd-7a5fcb6d205b"
SID2 = "01a18902-dd25-71b0-addd-7a5fcb6d205c"
NOW = datetime.datetime(2026, 11, 6, 12, 0, tzinfo=datetime.timezone.utc)
BASE = NOW - datetime.timedelta(hours=2)


def session(root, sid, cwd, originator="codex_exec", when=None, first=None, name=None, meta_id=None):
    """Write ~/.codex/sessions/<y>/<m>/<d>/rollout-<time>-<id>.jsonl; `first` replaces the first record."""
    when = when or NOW
    folder = os.path.join(root, f"{when.year}", f"{when.month:02d}", f"{when.day:02d}")
    os.makedirs(folder, exist_ok=True)
    path = os.path.join(folder, name or f"rollout-{when.strftime('%Y-%m-%dT%H-%M-%S')}-{sid}.jsonl")
    record = first if first is not None else json.dumps({"timestamp": when.isoformat(), "type": "session_meta", "payload": {"id": meta_id or sid, "timestamp": when.isoformat().replace("+00:00", "Z"), "cwd": cwd, "originator": originator}})
    with open(path, "w", encoding="utf-8") as f:
        f.write(record + "\n" + json.dumps({"type": "turn_context"}) + "\n")
    return path


def lines_of(n_add, n_del=0, path="backend/src/main/java/A.java"):
    return [(path, None, n_add, n_del)]


def body_ids(*ids):
    return "Closes #1\n\n" + "".join(f"Codex: {i}\n" for i in ids)


def test_scope(tmp):
    acc = rules.ACCEPTANCE + "A.java"
    log = "\x01test-engineer\n\n%s\nfoo.txt\n\x01\n\n%sB.java\n\x01other,test-engineer\n\nbackend/src/main/x.java\n%sC.java\n\x01somebody\n\n%sD.java\n" % (acc, rules.ACCEPTANCE, rules.ACCEPTANCE, rules.ACCEPTANCE)
    assert rules.acceptance_files(log) == {acc, rules.ACCEPTANCE + "C.java"}, rules.acceptance_files(log)
    assert rules.acceptance_files("") == set() and rules.acceptance_files("\x01\n") == set()
    # the log carries the committer time after the trailers; the oldest trailer commit's time is the scope's `oldest`
    dated = "\x01test-engineer\x022026-11-06T09:00:00+00:00\n\n%s\n\x01test-engineer\x022026-11-06T08:00:00+00:00\n\n%sB.java\n\x01\x022026-11-05T01:00:00+00:00\n\nbackend/src/main/x.java\n" % (acc, rules.ACCEPTANCE)
    assert rules.acceptance_files(dated).oldest == datetime.datetime(2026, 11, 6, 8, 0, tzinfo=datetime.timezone.utc), "the oldest commit that carries the trailer, not an older one without it"
    assert rules.acceptance_files(log).oldest is None and rules.acceptance_files("").oldest is None
    root = os.path.join(tmp, "sessions")
    ok = lambda stats, body, scoped=(): rules.judge(stats, body, [], None, None, BASE, root, scoped)
    stats = [(acc, None, 40, 0)]
    assert ok(stats, "none") is None, "without the trailer commit it is not in scope"
    why = ok(stats, "none", {acc})
    assert why and "acceptance tests of one-test" in why and "no `Codex-Acceptance:` line" in why, why
    assert ok(stats, "Codex: skip small\n", {acc}) and "no `Codex-Acceptance:` line" in ok(stats, "Codex: skip small\n", {acc}), "the implementation's own line does not cover the acceptance part"
    assert ok(stats, "Codex-Acceptance: skip small\n", {acc}) and "fewer than 20" in ok(stats, "Codex-Acceptance: skip small\n", {acc})
    assert ok([(acc, None, 10, 9)], "Codex-Acceptance: skip small\n", {acc}) is None, "19 acceptance lines"
    # the parts count apart: a big backend change does not make acceptance lines big, nor the other way round
    both = lines_of(10, 5) + [(acc, None, 3, 2)]
    assert ok(both, "Codex: skip small\nCodex-Acceptance: skip small\n", {acc}) is None, "15 backend lines and 5 acceptance lines, each under 20"
    assert "no `Codex-Acceptance:` line" in ok(both, "Codex: skip small\n", {acc}), "both parts need their own line"
    assert "no `Codex:` line" in ok(both, "Codex-Acceptance: skip small\n", {acc})
    assert ok(lines_of(40) + [(acc, None, 3, 2)], "Codex-Acceptance: skip small\n", {acc}) and "no `Codex:` line" in ok(lines_of(40) + [(acc, None, 3, 2)], "Codex-Acceptance: skip small\n", {acc})
    assert ok(lines_of(18) + [(acc, None, 30, 0)], "Codex: skip small\n", {acc}) and "no `Codex-Acceptance:` line" in ok(lines_of(18) + [(acc, None, 30, 0)], "Codex: skip small\n", {acc}), "small backend lines do not excuse a big acceptance part"
    # a one-test session: another worktree, before the acceptance commits; the implementation branch began after it
    other = os.path.join(tmp, "repo", ".claude", "worktrees", "one-test-w")
    t_commit = NOW - datetime.timedelta(hours=1)
    scope = rules.Scope({acc})
    scope.oldest = t_commit
    mine = "11a18901-dd25-71b0-addd-7a5fcb6d205b"
    session(root, mine, other, when=NOW - datetime.timedelta(hours=5))   # before the implementation branch's start (BASE = NOW - 2 h), before the acceptance commit
    accept_line = f"Codex-Acceptance: {mine}\n"
    assert ok(stats, accept_line, scope) is None, "a one-test session in another worktree, from before the branch and before the commit, holds for the acceptance part"
    session(root, SID, other, when=NOW)   # the implementation's own run: after the branch began
    both_parts = stats + lines_of(40)
    assert ok(both_parts, accept_line + body_ids(SID), scope) is None, "an implementation PR with unchanged acceptance commits: its own run plus one-test's run hold"
    why = ok(both_parts, body_ids(SID), scope)
    assert why and "no `Codex-Acceptance:` line" in why, "the same without the one-test run is refused: " + str(why)
    late = "22a18901-dd25-71b0-addd-7a5fcb6d205b"
    session(root, late, other, when=NOW)
    why = ok(stats, f"Codex-Acceptance: {late}\n", scope)
    assert why and "not before the oldest acceptance commit" in why, why
    away = "33a18901-dd25-71b0-addd-7a5fcb6d205b"
    session(root, away, os.path.join(tmp, "elsewhere"), when=NOW - datetime.timedelta(hours=5))
    why = ok(stats, f"Codex-Acceptance: {away}\n", scope)
    assert why and "not a session worktree" in why, why
    assert "no file" in ok(stats, "Codex-Acceptance: 44a18901-dd25-71b0-addd-7a5fcb6d205b\n", scope)
    assert "is not a session id" in ok(stats, "Codex-Acceptance: nothing\n", scope)
    assert "without a session id" in ok(stats, "Codex-Acceptance:\n", scope)
    # the same one-test session does not prove the backend part: that needs the author's worktree and a time after the branch began
    why = ok(both_parts, f"Codex: {mine}\nCodex-Acceptance: {mine}\n", scope)
    assert why and f"session {mine}: started" in why and "not after the branch was cut" in why, why


def test_pr_body():
    assert pr_body.lines("a\nCodex: x, y\nb\n  Codex: z  \n", "Codex") == ["x, y", "z"]
    assert pr_body.lines("```\nCodex: quoted\n```\nCodex: real\n~~~\nCodex: also\n~~~", "Codex") == ["real"], "a fenced line is a quotation, not a declaration"
    assert pr_body.lines("codex: x\nCodex : y\nxCodex: z\n- Codex: w\nCodex:\n", "Codex") == [""], "exact key, first column, the colon right after it"
    assert pr_body.lines("Codex: a\r\nCodex: b\r\n", "Codex") == ["a", "b"] and pr_body.lines(None, "Codex") == []
    assert pr_body.ids(" a, ,b ,") == ["a", "b"]


def test_rules(tmp):
    root, wt = os.path.join(tmp, "sessions"), os.path.join(tmp, "repo", ".claude", "worktrees", "w1")
    os.makedirs(os.path.join(wt, "sub"))
    session(root, SID, wt)
    ok = lambda stats, body, **kw: rules.judge(stats, body, kw.get("comments", []), kw.get("edited"), kw.get("worktree", wt), kw.get("base", BASE), kw.get("root", root))
    refused = lambda needle, *a, **kw: (lambda why: why and needle in why or (_ for _ in ()).throw(AssertionError((needle, a, why))))(ok(*a, **kw))
    big = lines_of(40, 5)
    # who needs a line
    for stats in ([], [("frontend/x.ts", None, 90, 0)], [("scripts/a.py", None, 90, 0)], [("backend/src/test/java/T.java", None, 90, 0)], [("docs/a.md", None, 9, 0)], [("backend/pom.xml", None, 9, 0)]):
        assert ok(stats, "no line at all") is None, stats
    refused("no `Codex:` line", big, "Closes #1")
    refused("no `Codex:` line", [("backend/src/main/A.java", None, 1, 0)], "```\nCodex: " + SID + "\n```")
    refused("no `Codex:` line", [("docs/old.md", "backend/src/main/A.java", 0, 0)], "x")   # a file renamed away from backend/src/main still touches it
    # a true line
    assert ok(big, body_ids(SID)) is None and ok(big, body_ids(SID, SID)) is None
    assert ok(big, f"Codex: {SID}, {SID}\n") is None
    session(os.path.join(root), SID2, os.path.join(wt, "sub"))
    assert ok(big, body_ids(SID, SID2)) is None, "a run in a subdirectory of the worktree is inside it"
    # lines that are not true
    refused("no file rollout", big, body_ids("11111111-2222-3333-4444-555555555555"))
    refused("not a session id", big, body_ids("*"))
    refused("not a session id", big, body_ids("dd25-71b0-addd-7a5fcb6d205b"))          # the tail of a real id is not the id
    refused("not a session id", big, body_ids(SID.upper()))
    refused("not a session id", big, body_ids("../" + SID))
    refused("without a session id", big, "Codex: ,\n")
    refused("no file rollout", big, body_ids(SID[:-1] + "c"))
    refused("originator", big, body_ids(SID2.replace("a18902", "b18902")), root=_with(root, SID2.replace("a18902", "b18902"), wt, originator="Codex Desktop"))
    other = os.path.join(tmp, "elsewhere")
    os.makedirs(other)
    refused("not inside the author's worktree", big, body_ids("21a18901-dd25-71b0-addd-7a5fcb6d205b"), root=_with(root, "21a18901-dd25-71b0-addd-7a5fcb6d205b", other))
    refused("not inside the author's worktree", big, body_ids("31a18901-dd25-71b0-addd-7a5fcb6d205b"), root=_with(root, "31a18901-dd25-71b0-addd-7a5fcb6d205b", wt + "-sibling"))   # a prefix of the name is not inside
    os.symlink(wt, os.path.join(tmp, "link-to-wt"))
    assert ok(big, body_ids("41a18901-dd25-71b0-addd-7a5fcb6d205b"), root=_with(root, "41a18901-dd25-71b0-addd-7a5fcb6d205b", os.path.join(tmp, "link-to-wt"))) is None, "a symlink to the worktree resolves to it"
    refused("not after the branch was cut", big, body_ids(SID), base=NOW)                               # equal is not after
    refused("not after the branch was cut", big, body_ids(SID), base=NOW + datetime.timedelta(seconds=1))
    refused("another id", big, body_ids("51a18901-dd25-71b0-addd-7a5fcb6d205b"), root=_with(root, "51a18901-dd25-71b0-addd-7a5fcb6d205b", wt, meta_id="61a18901-dd25-71b0-addd-7a5fcb6d205b"))
    refused("not a session_meta", big, body_ids("71a18901-dd25-71b0-addd-7a5fcb6d205b"), root=_with(root, "71a18901-dd25-71b0-addd-7a5fcb6d205b", wt, first=json.dumps({"type": "turn_context", "payload": {}})))
    refused("not readable", big, body_ids("81a18901-dd25-71b0-addd-7a5fcb6d205b"), root=_with(root, "81a18901-dd25-71b0-addd-7a5fcb6d205b", wt, first="not json"))
    refused("no timestamp", big, body_ids("91a18901-dd25-71b0-addd-7a5fcb6d205b"), root=_with(root, "91a18901-dd25-71b0-addd-7a5fcb6d205b", wt, first=json.dumps({"type": "session_meta", "payload": {"id": "91a18901-dd25-71b0-addd-7a5fcb6d205b", "cwd": wt, "originator": "codex_exec"}})))
    session(root, "a1a18901-dd25-71b0-addd-7a5fcb6d205b", wt, name="rollout-notatime-a1a18901-dd25-71b0-addd-7a5fcb6d205b.jsonl")
    refused("no file rollout", big, body_ids("a1a18901-dd25-71b0-addd-7a5fcb6d205b"))                    # the file name has the shape rollout-<date>T<time>-<id>.jsonl
    refused("do not hold", big, body_ids(SID, "11111111-2222-3333-4444-555555555555"))                   # every id must hold, not one of them
    refused("do not hold", big, f"Codex: {SID}\nCodex: 11111111-2222-3333-4444-555555555555\n")
    assert ok(big, body_ids(SID), worktree=None) is None, "an author whose worktree is gone: any session worktree of the repository"
    refused("not a session worktree", big, body_ids("21a18901-dd25-71b0-addd-7a5fcb6d205b"), worktree=None, root=_with(root, "21a18901-dd25-71b0-addd-7a5fcb6d205b", other))
    assert ok(big, body_ids(SID), base=None) is None
    # skip small: under SMALL changed lines of backend/src/main, added + deleted
    for stats, accepted in ((lines_of(19), True), (lines_of(20), False), (lines_of(10, 9), True), (lines_of(10, 10), False), (lines_of(5, 5) + lines_of(5, 5, "backend/src/main/B.java"), False), (lines_of(1, 1) + [("frontend/a.ts", None, 500, 0)], True)):
        assert (ok(stats, "Codex: skip small\n") is None) is accepted, (stats, accepted)
    refused("fewer than 20", lines_of(20), "Codex: skip small\n")
    refused("needs a reason", big, "Codex: skip\n")
    refused("needs a comment", big, "Codex: skip too boring\n", comments=[])
    refused("needs a comment", big, "Codex: skip too boring\n", comments=[], edited=BASE)
    lead = lambda at, text="lead 准许跳过 Codex": [{"author": "x", "body": text, "at": at.isoformat()}]
    assert ok(big, "Codex: skip too boring\n", comments=lead(NOW), edited=BASE) is None
    refused("needs a comment", big, "Codex: skip too boring\n", comments=lead(BASE), edited=NOW)       # the body was edited after the comment: the line may be new
    refused("needs a comment", big, "Codex: skip too boring\n", comments=lead(BASE), edited=BASE)       # same instant is not after
    refused("needs a comment", big, "Codex: skip too boring\n", comments=lead(NOW, "lead says nothing"), edited=BASE)
    refused("needs a comment", big, "Codex: skip too boring\n", comments=lead(NOW), edited=None)
    assert ok(big, "Codex: skip too boring\n", comments=None) is None, "at PR opening there is no comment yet: judged at enqueue"
    assert ok(lines_of(50), "Codex: skip small\n", comments=lead(NOW), edited=BASE) is None, "lead may allow any skip, a big `skip small` too"
    refused("fewer than 20", big, f"Codex: skip small\nCodex: {SID}\n")                               # every line must hold
    # a tool that cannot answer is not a refusal
    for missing in (os.path.join(tmp, "no-such-dir"), os.path.join(tmp, "a-file")):
        open(os.path.join(tmp, "a-file"), "w").close()
        try:
            ok(big, body_ids(SID), root=missing)
            raise AssertionError("an unreadable sessions directory must raise ToolError")
        except rules.ToolError:
            pass
    assert ok(big, "Codex: skip too boring\n", comments=lead(NOW), edited=BASE, root=os.path.join(tmp, "no-such-dir")) is None, "a skip needs no sessions directory"


def _with(root, sid, cwd, **kw):
    session(root, sid, cwd, **kw)
    return root


def git(cwd, *args):
    r = subprocess.run(["git", "-c", "user.name=t", "-c", "user.email=t@t", "-c", "commit.gpgsign=false", *args], cwd=cwd, capture_output=True, text=True)
    assert r.returncode == 0, (args, r.stderr)
    return r.stdout.strip()


def test_gate(tmp):
    """The gate in a real worktree: origin, main, a session worktree whose branch changes backend/src/main."""
    origin, main, wt = (os.path.join(tmp, n) for n in ("origin.git", "main", "main/.claude/worktrees/w1"))
    git(tmp, "init", "-q", "--bare", "-b", "main", origin)
    git(tmp, "clone", "-q", origin, main)
    open(os.path.join(main, "a.txt"), "w").write("a\n")
    git(main, "add", "."), git(main, "commit", "-q", "-m", "base"), git(main, "push", "-q", "origin", "HEAD:main")
    git(main, "fetch", "-q", "origin")
    git(main, "worktree", "add", "-q", wt, "-b", "feat")
    root = os.path.join(tmp, "gate-sessions")
    sid = "b1a18901-dd25-71b0-addd-7a5fcb6d205b"
    later = datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(minutes=5)
    session(root, sid, wt, when=later)
    java = os.path.join(wt, "backend/src/main/java/A.java")
    os.makedirs(os.path.dirname(java))
    open(java, "w").write("class A {}\n" * 30)
    git(wt, "add", "."), git(wt, "commit", "-q", "-m", "change backend")

    def ask(command, cwd=wt, tool="Bash"):
        with mock.patch.object(rules, "sessions_root", lambda: root):
            return gate.decide({"cwd": cwd, "tool_name": tool}, command)
    create = lambda body: f"gh pr create --title 'fix(x): y（#1）' --body {shlex.quote(body)}"
    line = f"Closes #1\n\nCodex: {sid}\n"
    assert ask(create(line)) == (0, "", ""), ask(create(line))
    code, out, err = ask(create("Closes #1\n"))
    assert code == 2 and "拒绝" in err and "no `Codex:` line" in err and "Accepted forms" in err, (code, err)
    assert ask(create("Closes #1\n\nCodex: 11111111-2222-3333-4444-555555555555\n"))[0] == 2
    assert ask(create("Closes #1\n\nCodex: skip small\n"))[0] == 0, "any skip may be lead-approved: judged at enqueue, where the comments are"
    assert ask(create("Closes #1\n\nCodex: skip boring\n")) == (0, "", ""), "a lead-approved skip is judged at enqueue, there is no comment to look at yet"
    # the body in the forms a session writes
    # acceptance tests of one-test: in scope when the commit has the trailer
    acc = rules.ACCEPTANCE + "T.java"
    git(main, "worktree", "add", "-q", os.path.join(main, ".claude/worktrees/w3"), "-b", "acc")
    w3 = os.path.join(main, ".claude/worktrees/w3")
    os.makedirs(os.path.join(w3, os.path.dirname(acc)))
    open(os.path.join(w3, acc), "w").write("class T {}\n" * 30)
    git(w3, "add", "."), git(w3, "commit", "-q", "-m", "acceptance by hand")
    assert ask(create("Closes #1\n"), cwd=w3) == (0, "", ""), "acceptance tests without the trailer are not in scope"
    open(os.path.join(w3, acc), "a").write("class U {}\n")
    git(w3, "add", "."), git(w3, "commit", "-q", "-m", "acceptance\n\nAcceptance-Test-Author: test-engineer")
    code, out, err = ask(create("Closes #1\n"), cwd=w3)
    assert code == 2 and "acceptance tests of one-test" in err and "no `Codex-Acceptance:` line" in err, (code, err)
    heredoc = f"gh pr create --title 'x' --body \"$(cat <<'EOF'\nCloses #1\n\nCodex: {sid}\nEOF\n)\""
    assert ask(heredoc) == (0, "", ""), ask(heredoc)
    assert ask(heredoc.replace(sid, "11111111-2222-3333-4444-555555555555"))[0] == 2
    assert ask("gh pr create --title 'x' --body \"$(cat <<'EOF'\nCloses #1\nEOF\n)\"")[0] == 2
    assert ask(f"gh pr create --title x --body-file - <<'EOF'\nCloses #1\n\nCodex: {sid}\nEOF")[0] == 0
    assert ask("gh pr create --title x --body-file - <<'EOF'\nCloses #1\nEOF")[0] == 2
    open(os.path.join(wt, "BODY.md"), "w").write("Closes #1\n")
    assert ask("gh pr create --title x --body-file BODY.md")[0] == 2 and ask("gh pr create --title x -F BODY.md")[0] == 2 and ask("gh pr create --title x --body=hello")[0] == 2
    open(os.path.join(wt, "BODY.md"), "w").write(line)
    assert ask("gh pr create --title x --body-file BODY.md") == (0, "", "") and ask("gh pr create -t x -b 'Closes #1'")[0] == 2
    # what the text does not show is not judged here (the enqueue check holds it)
    for command in ("gh pr create --title x", "gh pr create --fill", 'gh pr create --title x --body "$B"', "gh pr create --title x --body \"$(python3 mk.py)\"", "gh pr create --title x --body-file \"$F\"", "gh pr create --title x --body-file NOPE.md"):
        assert ask(command) == (0, "", ""), command
    # not a PR opening, or not a place this gate judges
    for command in ("gh pr view 1", "gh pr list --search create", "git commit -m 'gh pr create --body x'", "echo gh pr create --body x", "ls", "gh issue create --body x"):
        assert ask(command) == (0, "", ""), command
    assert ask(create("Closes #1\n"), cwd=main) == (0, "", ""), "the main checkout is the main-checkout gate's business"
    assert ask(create("Closes #1\n"), cwd=os.path.join(tmp, "gone")) == (0, "", "")
    # a branch that touches no backend runtime code needs no line
    git(main, "worktree", "add", "-q", os.path.join(main, ".claude/worktrees/w2"), "-b", "docs")
    w2 = os.path.join(main, ".claude/worktrees/w2")
    open(os.path.join(w2, "d.md"), "w").write("d\n"), git(w2, "add", "."), git(w2, "commit", "-q", "-m", "docs")
    assert ask(create("Closes #1\n"), cwd=w2) == (0, "", "")
    # a session that began before the branch was cut does not count
    old = "c1a18901-dd25-71b0-addd-7a5fcb6d205b"
    session(root, old, wt, when=datetime.datetime.now(datetime.timezone.utc) - datetime.timedelta(days=2))
    code, out, err = ask(create(f"Closes #1\n\nCodex: {old}\n"))
    assert code == 2 and "not after the branch was cut" in err, err
    # fail open, with a warning
    with mock.patch.object(rules, "sessions_root", lambda: os.path.join(tmp, "no-codex-home")):
        code, out, err = gate.decide({"cwd": wt}, create(line))
    assert code == 0 and "additionalContext" in out and "enqueue" in out and not err, (code, out, err)
    with mock.patch.object(gate, "local_facts", side_effect=OSError("boom")):
        code, out, err = gate.decide({"cwd": wt}, create(line))
    assert code == 0 and "additionalContext" in out, (code, out)
    # through the dispatcher: refused as the hook sees it, a gate that crashes lets the command through, the production gate still runs
    with mock.patch.object(rules, "sessions_root", lambda: root):
        raw = lambda command: json.dumps({"tool_name": "Bash", "tool_input": {"command": command}, "cwd": wt})
        code, out, err = dispatcher.dispatch(raw(create("Closes #1\n")))
        assert code == 2 and "no `Codex:` line" in err, (code, err)
        assert dispatcher.dispatch(raw(create(line))) == (0, "", "")
        boom = lambda: mock.Mock(decide=mock.Mock(side_effect=RuntimeError("boom")))
        assert dispatcher.dispatch(raw(create("Closes #1\n")), pr_open_loader=boom) == (0, "", ""), "the PR-opening gate fails open"
        def broken():
            raise ImportError("no pr_open_gate")
        assert dispatcher.dispatch(raw(create("Closes #1\n")), pr_open_loader=broken)[0] == 0


def main():
    test_pr_body()
    with tempfile.TemporaryDirectory() as tmp:
        tmp = os.path.realpath(tmp)
        os.makedirs(os.path.join(tmp, "r"))
        test_rules(os.path.join(tmp, "r"))
        test_scope(os.path.join(tmp, "r"))
        os.makedirs(os.path.join(tmp, "g"))
        test_gate(os.path.join(tmp, "g"))
    print("draft_selftest: ok")


def pr_open_gate_test():
    main()
    return 0


if __name__ == "__main__":
    main()
