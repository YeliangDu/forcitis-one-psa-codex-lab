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
    open(os.path.join(author, FRAG), "w").write(fragment()); [open(os.path.join(author, "docs/architecture/events", f), "w").write(agg(1)) for f in ("asyncapi.yaml", "_x.yaml")]
    g(author, "add", "-A"); g(author, "commit", "-q", "-m", "base"); g(author, "push", "-q", "origin", "main")
    g(base, "clone", "-q", origin, runner)
    heads = {
        1: pr(author, 1, {FRAG: fragment(a_fields="id,more")}),                                  # an existing payload gains a field
        2: pr(author, 2, {FRAG: fragment(a_desc="a new description")}),                          # only a description changes
        3: pr(author, 3, {FRAG: fragment(extra="    Gamma:\n      name: Gamma\n      payload:\n        type: object\n")}),   # a new message
        4: pr(author, 4, {"docs/architecture/events/asyncapi.yaml": agg(2)}),                    # the generated aggregate is not a fragment
        8: pr(author, 8, {"docs/architecture/events/_x.yaml": agg(2)}),                          # nor is a `_` file
        10: pr(author, 10, {FRAG: fragment().replace("    Alpha:", "    Gone:", 1).replace("name: Alpha", "name: Gone", 1), "docs/architecture/events/moved.yaml": fragment(a_fields="id,moved").split("    Beta:")[0]}),   # Alpha moves to another fragment, payload changed
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
        assert mq_event_payload.changed(runner, 6, heads[6]) is None and "not run for #6" in err.getvalue(), "an unreadable fragment fails open, said out loud"
    g(author, "checkout", "-q", "main")   # main moves on after pr2 left it: that change is not the pull request's
    open(os.path.join(author, FRAG), "w").write(fragment(b_fields="id,later")); g(author, "commit", "-q", "-am", "main moves"); g(author, "push", "-q", "origin", "main")
    with contextlib.redirect_stderr(io.StringIO()):
        assert mq_event_payload.changed(runner, 2, heads[2]) == [], "the merge base, not main's tip, is the base"
    assert [mq_event_payload.impl_type(t) for t in ("feat(x): a", "fix: b", "refactor(y): c", "perf: d", "docs(z): e", "design: f", "ci(m): g", "plain")] == ["feat", "fix", "refactor", "perf", None, None, None, None]

    class Probe:   # through the CLI: an impl PR that changes a payload is refused, the same change as docs is not
        titles = {1: "feat(x): 变更载荷（#1）", 2: "refactor(x): 改说明（#2）", 5: "fix(x): 改载荷（#5）", 7: "docs(x): 设计签字（#7）"}
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
                mq.main(["--queue", queue, "enqueue", "2", "1", "5"])
            raise AssertionError("enqueue of an implementation pull request that changes a payload must exit")
        except SystemExit as e:
            assert "nothing queued" in str(e) and "#1 (feat)" in str(e) and "#5 (fix)" in str(e) and "#2" not in str(e).split("\n", 1)[1], str(e)
        assert not os.path.exists(queue) or mq.Queue(queue).numbers() == [], "refusing one refuses the whole call"
        with contextlib.redirect_stderr(io.StringIO()):
            mq.main(["--queue", queue, "enqueue", "2", "7"])   # a description change by an impl PR, and a design PR that changes a payload
        assert mq.Queue(queue).numbers() == [2, 7]
    finally:
        mq.Gh, mq.repo_root = real_gh, real_root
