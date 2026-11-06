#!/usr/bin/env python3
"""What merged since a time, by kind (#1562): counts of merged pull requests by runtime, test, scripts, docs, records and rules (the kinds are pr_kinds.py), and for the runtime PRs that change backend/src/main how many carry a `Codex:` line in the body.
Read-only: gh only, aggregates only (no titles, no bodies, no authors). lead reports the output to the user daily.
Usage: pr_mix.py --since <ISO time, e.g. 2026-11-02T09:00:00Z>   |   pr_mix.py --self-test"""
import argparse
import datetime
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "gates"))
import pr_body  # noqa: E402
import pr_kinds  # noqa: E402


def parse_since(text):
    try:
        at = datetime.datetime.fromisoformat(text.replace("Z", "+00:00"))
    except ValueError:
        raise SystemExit(f"--since: `{text}` is not an ISO time such as 2026-11-02T09:00:00Z")
    return at if at.tzinfo else at.replace(tzinfo=datetime.timezone.utc)


def mix(prs, since):
    """({kind: count}, total, (backend runtime PRs, of them with a `Codex:` line)) for the merged pull requests (dicts of merged_at, paths, body) merged at or after `since`."""
    counts = dict.fromkeys(pr_kinds.KINDS, 0)
    total = backend = with_line = 0
    for pr in prs:
        at = datetime.datetime.fromisoformat(pr["merged_at"].replace("Z", "+00:00"))
        if at < since:
            continue
        kind = pr_kinds.kind(pr["paths"])
        counts[kind] += 1
        total += 1
        if kind == "runtime" and pr_kinds.is_backend_runtime(pr["paths"]):
            backend += 1
            with_line += bool(pr_body.lines(pr["body"], "Codex"))
    return counts, total, (backend, with_line)


def report(counts, total, backend, since):
    lines = [f"merged since {since.isoformat()}: {total}"]
    lines += [f"  {kind:8} {counts[kind]:4}" for kind in pr_kinds.KINDS]
    lines.append(f"runtime backend PRs (change backend/src/main): {backend[0]}, with a `Codex:` line: {backend[1]}")
    return "\n".join(lines)


def main(argv=None, gh=None):
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("--since", help="ISO time; merged pull requests from then on")
    p.add_argument("--self-test", action="store_true")
    a = p.parse_args(argv)
    if a.self_test:
        from pr_mix_selftest import run
        return run()
    if a.since is None:
        p.error("--since is required")
    since = parse_since(a.since)
    if gh is None:
        from mq_github import Gh
        gh = Gh()
    prs = gh.merged_since_day((since - datetime.timedelta(days=1)).date().isoformat())   # the search is by UTC day; the time filter is here
    if prs is None:
        raise SystemExit("pr_mix: gh cannot list the merged pull requests")
    counts, total, backend = mix(prs, since)
    print(report(counts, total, backend, since))
    return 0


if __name__ == "__main__":
    sys.exit(main())
