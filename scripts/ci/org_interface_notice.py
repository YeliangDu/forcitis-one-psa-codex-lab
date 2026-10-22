#!/usr/bin/env python3
"""Fail when a pull request changes the signature snapshot of the organization interfaces the platform layer uses without saying the platform owner was told
(ADR-0066 section "需同步修改的守护规则" item 4; backend/architecture/platform-org-interfaces.signatures.txt).
The pull request body must contain the words 已通知平台 owner. Runs for pull requests only; the body comes from the PR_BODY environment variable.
Usage: org_interface_notice.py <base-ref>  (reads GITHUB_EVENT_NAME, PR_BODY)  |  org_interface_notice.py --self-test"""
import os
import re
import sys

from ci_git import CiError, comparison_point, commit_of, git, toplevel

SNAPSHOT = "backend/architecture/platform-org-interfaces.signatures.txt"
NOTICE = re.compile(r"已通知平台\s*owner", re.IGNORECASE)


def snapshot_changed(base, root):
    start = comparison_point(commit_of(base, root), root)
    return SNAPSHOT in git("diff", "--name-only", "--no-renames", start, "HEAD", cwd=root).splitlines()


def check(base, body, root):
    if not snapshot_changed(base, root):
        return []
    if NOTICE.search(body or ""):
        return []
    return [f"{SNAPSHOT} changed: the pull request body must say 已通知平台 owner (the platform owner was told of the change to the organization interfaces)"]


def self_test():
    assert NOTICE.search("已通知平台 owner（one-architecture，10-22）") and NOTICE.search("已通知平台owner")
    assert not NOTICE.search("已通知 owner") and not NOTICE.search("")
    print("org_interface_notice self-test: ok")
    return 0


def main():
    if sys.argv[1:] == ["--self-test"]:
        return self_test()
    if len(sys.argv) != 2:
        print(__doc__, file=sys.stderr)
        return 2
    if os.environ.get("GITHUB_EVENT_NAME") != "pull_request":
        print("not a pull request: nothing to check")
        return 0
    try:
        found = check(sys.argv[1], os.environ.get("PR_BODY", ""), toplevel())
    except CiError as e:
        print(f"::error title=Organization interfaces::{e}")
        return 1
    for message in found:
        print(f"::error title=Organization interfaces::{message}")
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(main())
