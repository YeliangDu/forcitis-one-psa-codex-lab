"""Self-test of the waiting label (#1300): put on at enqueue, off on every way out of the queue, never on a pull request that carries `queued`."""
import os
import re

import mq_selftest as t

WAITING = "排队中"


def adds(gh, n):
    return [c[3] for c in gh.calls if c[0] == "label" and c[1] == n and c[2] == WAITING]


def lane(mq, proc, rounds=8):
    stops = iter([False] * rounds + [True] * 100)
    mq.run_lane(proc, "code", idle=1, stop=lambda: next(stops))


def run(mq, d):
    import mq_waiting
    assert mq_waiting.label() == WAITING
    # mark: waiting pull requests get it; one that carries `queued` does not (a label added after `queued` hides the green checks, #977); once only
    gh = t.FakeGh(); gh.add(10); gh.add(11, labels={"queued"}); gh.add(12, labels={WAITING})
    assert mq_waiting.mark(gh, [10, 11, 12]) == [10] and adds(gh, 10) == [True] and adds(gh, 11) == [] and adds(gh, 12) == []
    # reconcile: off for every open pull request that is not in the queue, kept for those in it; a merged or closed one loses it too; gh failing changes nothing and never raises
    gh = t.FakeGh(); [gh.add(n, labels={WAITING}) for n in (20, 21, 22)]; gh.add(23, labels={WAITING}, state="CLOSED")
    assert mq_waiting.reconcile(gh, [21]) == [20, 22, 23] and gh.prs[21]["labels"] == {WAITING} and gh.prs[23]["labels"] == set()
    gh.labelled = lambda name: (_ for _ in ()).throw(ValueError("gh down"))
    assert mq_waiting.reconcile(gh, []) == [] and gh.prs[21]["labels"] == {WAITING}
    # every way out of the queue ends without the label; while the pull request is processed (queued on) it stays, so no all-skipped run follows `queued`
    for name, setup in (("merged", lambda gh, n: None), ("head-changed", lambda gh, n: gh.prs[n].update(head="f" * 40, merge="BEHIND", behind=1)),
                        ("conflict", lambda gh, n: gh.prs[n].update(merge="BEHIND", behind=1, update_ok=False))):
        n = 30 + len(name)
        gh = t.FakeGh(); gh.add(n, labels={WAITING}); setup(gh, n)
        proc, q, log, _ = t._proc(mq, d, "w-" + name, gh, [n], updater=lambda repo, branch, wt: "conflict")
        if name == "head-changed":
            q.set_head(n, f"{n:040x}")
        lane(mq, proc)
        assert q.numbers() == [] and WAITING not in gh.prs[n]["labels"], (name, gh.prs[n]["labels"], t.events(log))
        assert adds(gh, n) == [False] and name.upper() in t.events(log), (name, adds(gh, n), t.events(log))
    # a runner HOLD after three failures in a row
    gh = t.FakeGh(); gh.add(40, labels={WAITING})
    proc, q, log, _ = t._proc(mq, d, "w-hold", gh, [40])
    proc.process = lambda n: (_ for _ in ()).throw(RuntimeError("always"))
    lane(mq, proc)
    assert q.numbers() == [] and "HOLD" in t.events(log) and WAITING not in gh.prs[40]["labels"]
    # the waiting label is still there while the worker holds the pull request, and the run it labels `queued` is the full one
    gh = t.FakeGh(); gh.add(50, labels={WAITING})
    proc, q, log, _ = t._proc(mq, d, "w-turn", gh, [50])
    seen = []
    real = gh.checks
    gh.checks = lambda n: (seen.append(sorted(gh.prs[n]["labels"])), real(n))[1]
    assert proc.process(50) == "MERGED" and seen and all(WAITING in labels and "queued" in labels for labels in seen), seen
    # mq.py enqueue labels, mq.py remove takes it off (a pull request no worker holds)
    qpath = os.path.join(d, "w-cli", "merge.queue")
    os.makedirs(os.path.dirname(qpath), exist_ok=True)
    gh = t.FakeGh(); gh.add(60); gh.add(61)
    real_gh, real_enforce = mq.Gh, mq.mq_acceptance.enforce
    mq.Gh, mq.mq_acceptance.enforce = lambda: gh, lambda *a: None
    try:
        mq.main(["--queue", qpath, "--log", qpath + ".log", "enqueue", "60", "61"])
        assert gh.prs[60]["labels"] == {WAITING} and gh.prs[61]["labels"] == {WAITING}
        mq.main(["--queue", qpath, "--log", qpath + ".log", "remove", "60"])
        assert gh.prs[60]["labels"] == set() and gh.prs[61]["labels"] == {WAITING}
    finally:
        mq.Gh, mq.mq_acceptance.enforce = real_gh, real_enforce
    # ci.yml: evaluate every job's `if:` for each event x label combination (parsed, not matched as text)
    import itertools
    import yaml
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
    with open(os.path.join(root, ".github/workflows/ci.yml"), encoding="utf-8") as f:
        ci = f.read()
    jobs = yaml.safe_load(ci)["jobs"]
    assert set(jobs) == {"queue-notice", "api-contract", "backend", "frontend", "gitleaks"}, sorted(jobs)

    def runs(job, event, action, label, labels):
        expr = str(jobs[job].get("if", "true")).strip()
        expr = re.sub(r"^\$\{\{(.*)\}\}$", r"\1", expr, flags=re.S)
        expr = re.sub(r"contains\(github\.event\.pull_request\.labels\.\*\.name, '([^']*)'\)", r"('\1' in L)", expr)
        expr = expr.replace("always()", "True").replace("github.event_name", "E").replace("github.event.action", "A").replace("github.event.label.name", "N")
        expr = re.sub(r"!(?!=)", " not ", expr).replace("&&", " and ").replace("||", " or ")
        return bool(eval(expr, {"E": event, "A": action, "N": label, "L": labels}))
    for event, action, label in [("pull_request", a, None) for a in ("opened", "synchronize", "reopened")] + [("pull_request", "labeled", l) for l in ("queued", "急", WAITING)] + [("push", None, None), ("schedule", None, None)]:
        for labels in ([], [WAITING], [WAITING, "queued"], ["queued"], ["急"]):
            if event != "pull_request" and labels:
                continue
            waiting = WAITING in labels and "queued" not in labels
            other_label = action == "labeled" and label != "queued"
            ctx = (event, action, label, labels)
            for job in ("api-contract", "backend", "frontend", "gitleaks"):
                assert runs(job, *ctx) == (not waiting and not other_label), (job, ctx)
            assert runs("queue-notice", *ctx) == (waiting and not other_label), ("queue-notice", ctx)
    assert "排队中，合入时由合并队列检查" in jobs["queue-notice"]["name"] and "checked by the merge queue at merge time" in jobs["queue-notice"]["name"]
