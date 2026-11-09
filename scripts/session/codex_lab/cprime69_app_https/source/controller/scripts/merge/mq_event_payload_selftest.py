"""Self-test of the event payload check at enqueue (#1327): real git (a bare origin, a clone, refs/pull/N/head) and real catalogue fragments."""
import contextlib
import io
import os
import subprocess

import mq_event_payload

FRAG = "docs/architecture/events/demo.yaml"


def fragment(a_fields="id", b_fields="id", a_desc="d", extra=""):
    def message(name, fields, desc):
        props = "".join(f"          {f}:\n            type: string\n" for f in fields.split(","))
        return f"    {name}:\n      name: {name}\n      description: {desc}\n      payload:\n        type: object\n        properties:\n{props}"
    return "components:\n  messages:\n" + message("Alpha", a_fields, a_desc) + message("Beta", b_fields, "d") + extra


def agg(v):   # a file that has a message but is not a fragment (the generated aggregate, a `_` file)
    return f"components:\n  messages:\n    Agg:\n      payload:\n        v: {v}\n"


def run(mq, d):
    def g(cwd, *a):
        r = subprocess.run(["git", "-c", "commit.gpgsign=false", "-c", "user.name=t", "-c", "user.email=t@t", *a], cwd=cwd, capture_output=True, text=True)
        assert r.returncode == 0, (a, r.stderr)
        return r.stdout.strip()

    def pr(author, n, files):   # a branch off main with the given files, published as refs/pull/n/head; returns its head
        g(author, "checkout", "-q", "-b", f"pr{n}", "main")
        for path, text in files.items():
            if text is None:
                g(author, "rm", "-q", path)
                continue
            os.makedirs(os.path.dirname(os.path.join(author, path)), exist_ok=True)
            if isinstance(text, tuple):   # a symlink to the given target
                os.symlink(text[0], os.path.join(author, path))
            else:
                open(os.path.join(author, path), "w").write(text)
            g(author, "add", path)
        g(author, "commit", "-q", "-m", f"change {n}")
        g(author, "push", "-q", "origin", f"HEAD:refs/pull/{n}/head")
        return g(author, "rev-parse", "HEAD")
    base = os.path.join(d, "evp")
    os.makedirs(base)
    origin, author, runner = (os.path.join(base, n) for n in ("o.git", "author", "runner"))
    g(base, "init", "-q", "--bare", "-b", "main", origin); g(base, "clone", "-q", origin, author)
    os.makedirs(os.path.join(author, "docs/architecture/events"))
    open(os.path.join(author, FRAG), "w").write(fragment()); [open(os.path.join(author, p), "w").write(agg(1)) for p in ("docs/architecture/events/asyncapi.yaml", "docs/architecture/events/_x.yaml")]
    g(author, "add", "-A"); g(author, "commit", "-q", "-m", "base"); g(author, "push", "-q", "origin", "main")
    g(base, "clone", "-q", origin, runner)
    heads = {
        1: pr(author, 1, {FRAG: fragment(a_fields="id,more")}),                                  # an existing payload gains a field
        2: pr(author, 2, {FRAG: fragment(a_desc="a new description")}),                          # only a description changes
        3: pr(author, 3, {FRAG: fragment(extra="    Gamma:\n      name: Gamma\n      payload:\n        type: object\n")}),   # a new message
        4: pr(author, 4, {"docs/architecture/events/asyncapi.yaml": agg(2)}),                    # the generated aggregate is not a fragment
        8: pr(author, 8, {"docs/architecture/events/_x.yaml": agg(2)}),                          # nor is a `_` file
        10: pr(author, 10, {FRAG: fragment().replace("    Alpha:", "    Gone:", 1).replace("name: Alpha", "name: Gone", 1), "docs/architecture/events/moved.yaml": fragment(a_fields="id,moved").split("    Beta:")[0]}),   # Alpha moves to another fragment, payload changed
        12: pr(author, 12, {FRAG: "components:\n  messages: {}\n", "docs/other/real.yaml": fragment(a_fields="id,sneaky"), "docs/architecture/events/zz.yaml": ("../../other/real.yaml",)}),   # the payload hides behind a symlinked fragment
        13: pr(author, 13, {FRAG: fragment().split("    Beta:")[0]}),                        # a message is deleted: documented pass
        11: pr(author, 11, {FRAG: None, "docs/architecture/events/renamed.yaml": fragment(a_fields="id,renamed")}),   # the fragment is renamed, payload changed
        5: pr(author, 5, {FRAG: fragment(b_fields="id,x")}),                                     # another message's payload
        6: pr(author, 6, {FRAG: "components: [broken\n"}),                                       # a fragment that cannot be read
    }
    err = io.StringIO()
    with contextlib.redirect_stderr(err):
        assert mq_event_payload.changed(runner, 1, heads[1]) == [f"Alpha ({FRAG})"] and mq_event_payload.changed(runner, 5, heads[5]) == [f"Beta ({FRAG})"], "a changed payload is named"
        assert mq_event_payload.changed(runner, 2, heads[2]) == [] and mq_event_payload.changed(runner, 3, heads[3]) == [], "a description, a new message pass"
        assert mq_event_payload.changed(runner, 4, heads[4]) == [] and mq_event_payload.changed(runner, 8, heads[8]) == [], "asyncapi.yaml is generated and _x.yaml is not read: neither is a fragment"
        moved, renamed = (mq_event_payload.changed(runner, n, heads[n]) for n in (10, 11))
        assert moved == [f"Alpha ({FRAG} -> docs/architecture/events/moved.yaml)"], moved   # matched by name across fragments, so a move hides nothing
        assert renamed == [f"Alpha ({FRAG} -> docs/architecture/events/renamed.yaml)"], renamed
        assert mq_event_payload.changed(runner, 9, heads[1]) is None and "cannot fetch" in err.getvalue(), "a pull request that cannot be fetched fails open, said out loud"
        unreadable = mq_event_payload.changed(runner, 6, heads[6])
        assert len(unreadable) == 1 and "unreadable" in unreadable[0], unreadable   # fail closed: refused, not guessed
        assert mq_event_payload.changed(runner, 12, heads[12]) and "unreadable" in mq_event_payload.changed(runner, 12, heads[12])[0], "a symlinked fragment is not a way round the check"
        assert mq_event_payload.changed(runner, 13, heads[13]) == [], "deleting a message is a documented pass (EventCatalogueTest is the backstop)"
    g(author, "checkout", "-q", "main")   # main moves on after pr2 left it: that change is not the pull request's
    open(os.path.join(author, FRAG), "w").write(fragment(b_fields="id,later")); g(author, "commit", "-q", "-am", "main moves"); g(author, "push", "-q", "origin", "main")
    with contextlib.redirect_stderr(io.StringIO()):
        assert mq_event_payload.changed(runner, 2, heads[2]) == [], "the merge base, not main's tip, is the base"
    assert [mq_event_payload.title_type(t) for t in ("feat(x): a", "docs(z): e", "ci(m): g", "plain", None)] == ["feat", mq_event_payload.DESIGN_TYPE, "ci", "", ""]

    class Probe:   # through the CLI: a non-docs PR (feat, fix, ci: an allowlist) that changes a payload is refused, the same change as docs is not
        titles = {1: "feat(x): 变更载荷（#1）", 2: "refactor(x): 改说明（#2）", 5: "fix(x): 改载荷（#5）", 7: "docs(x): 设计签字（#7）", 10: "ci(x): 改载荷（#10）"}
        def view(self, n):
            return {"state": "OPEN", "merge": "CLEAN", "labels": [], "branch": "b", "head": heads.get(n, heads[1]), "title": self.titles[n], "body": f"Closes #{n}\n"}
    pr(author, 7, {FRAG: fragment(a_fields="id,design")})   # same file, fetched by the runner under its own number
    heads[7] = g(author, "rev-parse", "HEAD")
    real_gh, real_root = mq.Gh, mq.repo_root
    mq.Gh, mq.repo_root = Probe, lambda: runner
    queue = os.path.join(d, "evp-q", "merge.queue")
    try:
        try:
            with contextlib.redirect_stderr(io.StringIO()):
                mq.main(["--queue", queue, "enqueue", "2", "1", "5", "10"])
            raise AssertionError("enqueue of a non-docs pull request that changes a payload must exit")
        except SystemExit as e:
            assert "nothing queued" in str(e) and "#1 (feat)" in str(e) and "#5 (fix)" in str(e) and "#10 (ci)" in str(e) and "#2" not in str(e).split("\n", 1)[1], str(e)
        assert not os.path.exists(queue) or mq.Queue(queue).numbers() == [], "refusing one refuses the whole call"
        with contextlib.redirect_stderr(io.StringIO()):
            mq.main(["--queue", queue, "enqueue", "2", "7"])   # a description change by an impl PR, and a design PR that changes a payload
        assert mq.Queue(queue).numbers() == [2, 7]
    finally:
        mq.Gh, mq.repo_root = real_gh, real_root
    # #1406: a code PR may ship a payload signed in an open docs PR whose newest review on its current head is 通过, equal message by message
    def frag(**k):   # main has moved on: its Beta is `later`
        return fragment(**{"b_fields": "id,later", **k})
    sign = {20: pr(author, 20, {FRAG: frag(a_fields="id,sig")}),                                   # the signed text: Alpha gains `sig`
            21: pr(author, 21, {FRAG: frag(a_fields="id,sig")}),                                   # code PR shipping exactly it
            22: pr(author, 22, {FRAG: frag(a_fields="id,other")}),                                 # code PR shipping a different Alpha
            23: pr(author, 23, {FRAG: frag(a_fields="id,sig", b_fields="id,extra")}),              # Alpha as signed, Beta not signed anywhere
            24: pr(author, 24, {FRAG: frag(b_fields="id,beta")}),                                  # a second signed text for Beta
            26: pr(author, 26, {FRAG: "components:\n  messages:\n" + "    Beta:" + frag(a_fields="id,sig").split("    Beta:")[1]}),   # a docs head that holds no Alpha at all
            25: pr(author, 25, {FRAG: frag(a_fields="id,sig", b_fields="id,beta")})}               # Alpha and Beta both as the two signers have them
    def verdict(head, word="通过", kind="首轮", at="2026-11-05T10:00:00Z", commit=None):
        return {"body": f"复审结论：{word}\ncommit：{head}\n复审者：opus-{kind}-x\n\n正文", "commit": commit or head, "at": at}
    states = {20: ("docs(x): 设计签字（#20）", [verdict(sign[20])]), 24: ("docs(x): 设计签字（#24）", [verdict(sign[24])])}
    bodies = {}
    closed, merged = set(), set()

    class Signing:
        def view(self, n):
            title = states[n][0] if n in states else {n: f"feat(x): 实现（#{n}）" for n in (21, 22, 23, 25)}.get(n)
            return {"state": "MERGED" if n in merged else "CLOSED" if n in closed else "OPEN", "merge": "CLEAN", "labels": [], "branch": "b", "head": sign[n], "title": title, "body": bodies.get(n, "Closes #1\n")} if title else None
        def reviews(self, n):
            return states[n][1]
    def refused_for(n, body, over=()):
        over = dict(over)
        bodies[n] = body
        saved = {k: states.get(k) for k in over}
        states.update(over)
        try:
            with contextlib.redirect_stderr(io.StringIO()):
                mq_event_payload.enforce(runner, Signing(), [n])
            return None
        except SystemExit as e:
            return str(e)
        finally:
            for k, v in saved.items():
                states.pop(k, None) if v is None else states.__setitem__(k, v)
    assert refused_for(21, "Closes #1\nPayload-Signed: #20\n") is None, "a signed payload ships with the code"
    assert "#21" in (refused_for(21, "Closes #1\n") or ""), "no Payload-Signed line: refused"
    assert "#21" in (refused_for(21, "Payload-Signed: #20 and more text\n") or ""), "the line must be exactly the form"
    assert "#22" in (refused_for(22, "Payload-Signed: #20\n") or ""), "a payload that differs from the signed one is refused"
    assert "#21" in (refused_for(21, "Payload-Signed: #99\n") or ""), "an unknown signer signs nothing"
    assert "#21" in (refused_for(21, "Payload-Signed: #20\n", {20: ("feat(x): 不是设计（#20）", [verdict(sign[20])])}) or ""), "a signer that is not of the docs type signs nothing"
    closed.add(20)
    assert refused_for(21, "Payload-Signed: #20\n") is None, "a signer closed unmerged (after the code PR was queued) still signs: the code PR can be queued again"
    closed.clear()
    merged.add(20)
    assert "#21" in (refused_for(21, "Payload-Signed: #20\n") or ""), "a merged signer signs nothing (its text is on main already)"
    merged.clear()
    assert "#21" in (refused_for(21, "Payload-Signed: #26\n", {26: ("docs(x): 设计签字（#26）", [verdict(sign[26])])}) or ""), "a docs head with no such message signs nothing"
    assert "#21" in (refused_for(21, "Payload-Signed: #20\n", {20: ("docs(x): 设计签字（#20）", [])}) or ""), "no verdict: refused"
    assert "#21" in (refused_for(21, "Payload-Signed: #20\n", {20: ("docs(x): 设计签字（#20）", [verdict(sign[20], word="必改")])}) or ""), "a 必改 verdict signs nothing"
    assert "#21" in (refused_for(21, "Payload-Signed: #20\n", {20: ("docs(x): 设计签字（#20）", [verdict(sign[20]), verdict(sign[20], word="必改", at="2026-11-05T11:00:00Z")])}) or ""), "the newest statement decides"
    assert "#21" in (refused_for(21, "Payload-Signed: #20\n", {20: ("docs(x): 设计签字（#20）", [verdict(heads[1], commit=heads[1])])}) or ""), "a verdict on another commit than the signer's head signs nothing"
    assert "#21" in (refused_for(21, "Payload-Signed: #20\n", {20: ("docs(x): 设计签字（#20）", [verdict(sign[20], commit=heads[1])])}) or ""), "a review posted on another commit than its header names signs nothing"
    partial = refused_for(23, "Payload-Signed: #20\n") or ""
    assert "Beta" in partial and "Alpha" not in partial, "only the signed message passes, the other is still refused: " + partial
    assert refused_for(23, "Payload-Signed: #20\nPayload-Signed: #24\n") is not None, "Beta differs from the second signer's Beta too"
    assert refused_for(25, "Payload-Signed: #20\nPayload-Signed: #24\n") is None, "two signers cover the two messages"
