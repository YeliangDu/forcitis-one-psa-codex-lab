"""Self-test of the merge queue's answer to a label added after `queued` (#977)."""
import mq_selftest as t


def run(mq, d):
    # a label other than `queued` started an all-skipped run that hides the green checks: the merge is refused as BLOCKED; `queued` goes off and back on, then the merge goes through
    gh = t.FakeGh(); gh.add(70, merge="BLOCKED", merge_results=((False, "base branch policy prohibits the merge"), (True, "")))
    result, ev, log, slept = t.once(mq, d, "lbl1", gh, 70)
    labels = [c[2:] for c in gh.calls if c[0] == "label"]
    assert result == "MERGED" and "RELABEL" in ev and labels == [("queued", True), ("queued", False), ("queued", True)], labels
    # a refusal in any other merge state leaves the label alone
    gh = t.FakeGh(); gh.add(71, merge_results=((False, "no"), (True, "")))
    result, ev, _, _ = t.once(mq, d, "lbl2", gh, 71)
    assert result == "MERGED" and "RELABEL" not in ev
