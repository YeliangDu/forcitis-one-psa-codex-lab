"""Self-test of the merge queue's title check right before the merge (#1121): a title edited after the review is held, not merged; and at enqueue (#1289): a bad title is refused before it is queued."""
import contextlib
import io
import os

import mq_selftest as t


def run(mq, d):
    gh = t.FakeGh(); gh.add(80)
    result, ev, _, _ = t.once(mq, d, "ttl1", gh, 80)
    assert result == "MERGED" and "HOLD" not in ev, "a well-formed title merges"
    for i, (name, title, body) in enumerate((("English summary", "fix(q): fix the thing（#81）", None), ("no card", "fix(q): 修复", None), ("card differs from the first line", "fix(q): 修复（#99）", None),
                                             ("half-width brackets", "fix(q): 修复(#81)", None), ("first line is not a link", "fix(q): 修复（#81）", "no link"))):
        gh = t.FakeGh(); p = gh.add(81 + i)
        p["title"], p["body"] = title.replace("81", str(81 + i)) if "99" not in title else title, body or p["body"]
        result, ev, log, _ = t.once(mq, d, f"ttl{i + 2}", gh, 81 + i)
        assert result != "MERGED" and not t.merged(gh) and "HOLD" in ev, (name, result, ev)
        assert any("title" in l for l in log.lines if " HOLD " in l), (name, log.lines)

    # at enqueue (#1289): nothing is queued for a bad title, or when gh does not answer; a good title is queued
    class Probe:
        titles = {1: "fix(q): fix the thing（#1）", 2: "fix(q): 修复（#2）", 3: "fix(q): 修复(#3)"}

        def view(self, n):
            return {"state": "OPEN", "merge": "CLEAN", "labels": [], "branch": "b", "head": f"{n:040x}", "title": self.titles[n], "body": f"Closes #{n}\n"} if n in self.titles else None
    real_gh, real_root, real_acc = mq.Gh, mq.repo_root, mq.mq_acceptance.enforce
    mq.Gh, mq.mq_acceptance.enforce = Probe, lambda *args: None   # the acceptance guard has its own case
    queue = os.path.join(d, "ttl-q", "merge.queue")
    try:
        for bad in ([1], [2, 3], [9]):   # English summary; half-width brackets next to a good one; gh does not answer
            try:
                with contextlib.redirect_stderr(io.StringIO()):
                    mq.main(["--queue", queue, "enqueue", *map(str, bad)])
                raise AssertionError(("enqueue must exit", bad))
            except SystemExit as e:
                assert "nothing queued" in str(e) and f"#{bad[-1]}" in str(e), (bad, str(e))
                if bad == [1]:
                    assert "类型(范围): 中文摘要（#1）" in str(e), "the message shows the shape the title should have"
            assert not os.path.exists(queue) or mq.Queue(queue).numbers() == [], ("refusing one refuses the whole call", bad)
        with contextlib.redirect_stderr(io.StringIO()):
            mq.main(["--queue", queue, "enqueue", "2"])
        assert mq.Queue(queue).numbers() == [2], "a good title is queued"
    finally:
        mq.Gh, mq.mq_acceptance.enforce = real_gh, real_acc
