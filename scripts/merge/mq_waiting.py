"""The label of a pull request that waits in the merge queue (#1300).

`mq.py enqueue` puts the label (policy.json labels.queue_waiting) on a queued pull request. While it is there and `queued` is not, ci.yml skips every job of the pull
request's own runs (they would be discarded: the queue's run at the pull request's turn, with main merged in, is the only one that decides the merge). The worker
adds `queued` at the turn, which starts the full run; the waiting label stays until the pull request leaves the queue.

The label follows queue membership, so every way out is covered by one rule: `reconcile` takes the label off every pull request, merged or closed too, that is not in the queue. It runs
before each pick of a lane and after `mq.py remove`, so MERGED, CONFLICT, HOLD, HEAD-CHANGED, CANCELLED, a runner HOLD and a manual remove all end the same way.
The label is never put on a pull request that carries `queued`: a label added after `queued` starts a run that skips every job and hides the green checks (#977).
Labelling is best effort: a failed call is logged and never stops the queue, and the queue's decision does not depend on the label.
"""
import json
import os

POLICY = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "policy.json")


def label():
    with open(POLICY, encoding="utf-8") as f:
        return json.load(f)["labels"]["queue_waiting"]


def mark(gh, prs, log=None):
    """Label the pull requests that wait for their turn; returns the numbers labelled."""
    done = []
    for n in prs:
        try:
            v = gh.view(n)
            if v and "queued" not in v["labels"] and label() not in v["labels"] and gh.label(n, label(), True):
                done.append(n)
        except Exception as e:   # best effort
            if log:
                log.event("ERROR", n, None, None, f"waiting label: {type(e).__name__}: {e}")
    return done


def reconcile(gh, in_queue, log=None):
    """Takes the label off every pull request that is no longer in the queue (merged and closed ones too); returns the numbers cleaned."""
    cleaned = []
    try:
        for n in sorted(set(gh.labelled(label())) - set(in_queue)):
            if gh.label(n, label(), False):
                cleaned.append(n)
    except Exception as e:   # best effort
        if log:
            log.event("ERROR", None, None, None, f"waiting label: {type(e).__name__}: {e}")
    return cleaned
