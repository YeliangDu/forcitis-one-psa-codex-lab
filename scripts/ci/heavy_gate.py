#!/usr/bin/env python3
"""Decide whether the heavy CI steps (backend surefire/failsafe, OpenAPI check, SQL Server tests, frontend verify) run for this event.
Writes run=true|false to $GITHUB_OUTPUT.

  pull_request  heavy only when the pull request carries the label `queued` (the merge queue puts it on the head of the queue; a synchronize
                after that, such as the strict update from main, keeps the label and so runs the heavy steps again)
  push (main)   never: strict mode makes the merged tree the tree the labelled pull request already tested; the nightly run covers main in full
  anything else heavy (workflow_dispatch and the like: an explicit request for a full run)

The light steps (secret scan, docs and release-script checks, migration/acceptance guards) run on every event.
Usage: heavy_gate.py   (reads GITHUB_EVENT_NAME and LABELS_JSON, the pull request's label names as a JSON array)  |  heavy_gate.py --self-test
"""
import json
import os
import sys

LABEL = "queued"


def decide(event, labels_json):
    if event == "push":
        return False, "push to main: light checks only; the labelled pull request already ran the heavy steps on this tree, the nightly run covers main"
    if event == "pull_request":
        try:
            labels = json.loads(labels_json or "[]")
        except ValueError:
            raise SystemExit(f"LABELS_JSON is not JSON: {labels_json!r}")
        if not isinstance(labels, list):
            raise SystemExit(f"LABELS_JSON must be an array: {labels_json!r}")
        if LABEL in labels:
            return True, f"label `{LABEL}` present: heavy steps run"
        return False, f"pull request without label `{LABEL}`: light checks only; the queue adds the label when this pull request reaches the head of the queue, and the heavy steps run then"
    return True, f"{event}: heavy steps run"


def main():
    run, why = decide(os.environ.get("GITHUB_EVENT_NAME", ""), os.environ.get("LABELS_JSON", "[]"))
    print(f"{'heavy steps run' if run else 'heavy steps skipped'}: {why}")
    if not run:
        print(f"::notice title=Heavy steps skipped::{why}")
    out = os.environ.get("GITHUB_OUTPUT")
    if out:
        with open(out, "a", encoding="utf-8") as f:
            f.write(f"run={'true' if run else 'false'}\n")


def self_test():
    assert decide("push", "[]")[0] is False and decide("push", '["queued"]')[0] is False
    assert decide("pull_request", "[]")[0] is False and decide("pull_request", '["docs"]')[0] is False
    assert decide("pull_request", '["queued"]')[0] is True and decide("pull_request", '["docs","queued"]')[0] is True
    assert decide("pull_request", '["queued-later"]')[0] is False and decide("pull_request", '["Queued"]')[0] is False
    assert decide("pull_request", "")[0] is False
    assert decide("workflow_dispatch", "[]")[0] is True
    for bad in ("not json", '{"queued": 1}'):
        try:
            decide("pull_request", bad)
            raise AssertionError(bad)
        except SystemExit:
            pass
    print("heavy_gate self-test: ok")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    else:
        main()
