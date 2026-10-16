#!/usr/bin/env python3
"""Code merge train: test up to N reviewed pull requests with one CI run instead of N.

  merge_train.py build  --branch claude/one-coordinator-train-14 --max 3 <pr> <pr> [<pr>]   merge the heads of the pull requests, in the order given, onto origin/main in a scratch
                                                                                              worktree and print the train commit; with --push also push the branch, with --open-pr open the
                                                                                              train pull request (its body lists the members)
  merge_train.py split  --branch <failed-train-branch> --prefix claude/one-coordinator-train-14   after a failed train: build two trains from the halves of its member list
  merge_train.py finish --pr <train-pr>                                                           after the train pull request merged: close the member pull requests that GitHub did not mark merged

Why: the strict queue re-runs the whole CI for every pull request (about half of all CI minutes went to runs nobody used). A train of N tests one tree for N pull requests; a failure
costs a bisection (split) instead of N reruns. Members are recorded in the train commit message ("Train-Member: #123 <head sha>") so split and finish need no side file.
The merge queue stays the arbiter: the train pull request is labelled `queued` by the queue like any other pull request (scripts/ci/heavy_gate.py), then merged with a merge commit; GitHub marks
a member merged once its head commit is in main.
Rules the script enforces: a member must be an open pull request into main and not a draft (unless --force-members); a train has at most --max members; a merge conflict names the pull request
and what it conflicts with, and builds nothing (take that pull request out, or have it updated from main). Whether the members are reviewed and complete is the queue's decision, not checked here.

Usage: merge_train.py build|split|finish ...   |   merge_train.py --self-test
"""
import argparse
import json
import os
import re
import subprocess
import sys
import tempfile

MEMBER = re.compile(r"^Train-Member: #(\d+) ([0-9a-f]{40})$", re.M)


def run(args, cwd=None, check=True, env=None):
    r = subprocess.run(args, cwd=cwd, capture_output=True, text=True, env=env)
    if check and r.returncode:
        raise SystemExit(f"{' '.join(args[:3])} failed ({r.returncode}): {(r.stderr or r.stdout).strip()[:400]}")
    return r


def git(repo, *args, check=True):
    return run(["git", *args], cwd=repo, check=check)


def merge_members(repo, base_ref, branch, members):
    """members: [(label, ref)] -> (ok, message). Builds `branch` in `repo` (a clean worktree or clone) from base_ref by merging each ref with --no-ff."""
    git(repo, "checkout", "-q", "-B", branch, base_ref)
    done = []
    for label, ref in members:
        r = git(repo, "merge", "--no-ff", "--no-edit", "-m", f"train: merge {label}", ref, check=False)
        if r.returncode:
            git(repo, "merge", "--abort", check=False)
            return False, f"{label} conflicts with {'main' if not done else 'main plus ' + ', '.join(done)}"
        done.append(label)
    return True, "ok"


def train_message(branch, members):
    lines = [f"train {branch}: " + ", ".join(f"#{n}" for n, _ in members), ""]
    lines += [f"Train-Member: #{n} {sha}" for n, sha in members]
    return "\n".join(lines) + "\n"


def members_of(message):
    return [(int(n), sha) for n, sha in MEMBER.findall(message)]


def record(repo, branch, members):
    """An empty commit on top of the merges that carries the member list."""
    git(repo, "commit", "-q", "--allow-empty", "-m", train_message(branch, members))
    return git(repo, "rev-parse", "HEAD").stdout.strip()


def halves(members):
    mid = (len(members) + 1) // 2
    return members[:mid], members[mid:]


def gh_json(*args):
    return json.loads(run(["gh", *args]).stdout)


def pr_head(n):
    d = gh_json("pr", "view", str(n), "--json", "headRefOid,headRefName,state,isDraft,baseRefName,mergeStateStatus")
    return d


def build(args):
    if len(args.prs) > args.max:
        raise SystemExit(f"at most {args.max} pull requests per train (--max)")
    run(["git", "fetch", "-q", "origin", "main"])
    members, refs = [], []
    for n in args.prs:
        d = pr_head(n)
        if d["state"] != "OPEN" or d["baseRefName"] != "main":
            raise SystemExit(f"#{n} is not an open pull request into main ({d['state']}, base {d['baseRefName']})")
        if d["isDraft"] and not args.force_members:
            raise SystemExit(f"#{n} is a draft")
        members.append((n, d["headRefOid"]))
        refs.append((f"#{n}", d["headRefOid"]))
    run(["git", "fetch", "-q", "origin", *[sha for _, sha in members]])
    wt = tempfile.mkdtemp(prefix="train-")
    run(["git", "worktree", "add", "-q", "--detach", wt, "origin/main"])
    try:
        ok, why = merge_members(wt, "origin/main", args.branch, refs)
        if not ok:
            raise SystemExit(f"no train built: {why}; take that pull request out or update it from main")
        commit = record(wt, args.branch, members)
        print(f"train {args.branch} at {commit[:10]} with {', '.join(f'#{n}' for n, _ in members)}")
        if args.push:
            git(wt, "push", "-q", "origin", f"{args.branch}:{args.branch}")
            print(f"pushed {args.branch}")
            if args.open_pr:
                body = "Train of reviewed pull requests, tested once. Members:\n" + "\n".join(f"- #{n}" for n, _ in members) + \
                       "\n\nAfter this merges the member pull requests are closed by `merge_train.py finish`.\n\n🤖 Generated with [Claude Code](https://claude.com/claude-code)\n"
                url = run(["gh", "pr", "create", "--base", "main", "--head", args.branch, "--title", f"train: {', '.join(f'#{n}' for n, _ in members)}", "--body", body]).stdout.strip()
                print(url)
    finally:
        run(["git", "worktree", "remove", "-f", wt], check=False)


def split(args):
    run(["git", "fetch", "-q", "origin", args.branch])
    message = git(".", "log", "-1", "--format=%B", f"origin/{args.branch}").stdout
    members = members_of(message)
    if len(members) < 2:
        raise SystemExit(f"{args.branch} has {len(members)} member(s): nothing to split, that pull request itself fails")
    left, right = halves(members)
    for i, part in enumerate((left, right), 1):
        print(f"python3 scripts/queue/merge_train.py build --branch {args.prefix}-{i} --push --open-pr " + " ".join(str(n) for n, _ in part))


def finish(args):
    train = gh_json("pr", "view", str(args.pr), "--json", "state,headRefName,body,mergeCommit")
    if train["state"] != "MERGED":
        raise SystemExit(f"train pull request #{args.pr} is {train['state']}, not MERGED")
    members = [int(n) for n in re.findall(r"^- #(\d+)$", train["body"], re.M)]
    for n in members:
        state = gh_json("pr", "view", str(n), "--json", "state")["state"]
        if state == "OPEN":
            run(["gh", "pr", "close", str(n), "--comment", f"Merged through train #{args.pr}."])
            print(f"closed #{n}")
        else:
            print(f"#{n} already {state}")


def self_test():
    def sh(cwd, *a):
        return subprocess.run(a, cwd=cwd, capture_output=True, text=True, check=True, env=dict(os.environ, GIT_AUTHOR_NAME="t", GIT_AUTHOR_EMAIL="t@t", GIT_COMMITTER_NAME="t", GIT_COMMITTER_EMAIL="t@t")).stdout.strip()

    with tempfile.TemporaryDirectory() as d:
        sh(d, "git", "init", "-q", "-b", "main", "repo")
        repo = os.path.join(d, "repo")
        sh(repo, "git", "config", "commit.gpgsign", "false")

        def commit(path, text, msg):
            with open(os.path.join(repo, path), "w") as f:
                f.write(text)
            sh(repo, "git", "add", path); sh(repo, "git", "commit", "-q", "-m", msg)
            return sh(repo, "git", "rev-parse", "HEAD")

        commit("base.txt", "base\n", "base")
        heads = {}
        for n, (path, text) in {1: ("a.txt", "a\n"), 2: ("b.txt", "b\n"), 3: ("c.txt", "c\n")}.items():
            sh(repo, "git", "checkout", "-q", "-b", f"pr{n}", "main")
            heads[n] = commit(path, text, f"pr{n}")
        sh(repo, "git", "checkout", "-q", "-b", "pr4", "main")
        heads[4] = commit("a.txt", "conflicting a\n", "pr4 touches a.txt like pr1")
        sh(repo, "git", "checkout", "-q", "main")
        # clean train of three
        ok, why = merge_members(repo, "main", "train-1", [(f"#{n}", f"pr{n}") for n in (1, 2, 3)])
        assert ok, why
        sha = record(repo, "train-1", [(n, heads[n]) for n in (1, 2, 3)])
        assert all(os.path.exists(os.path.join(repo, f)) for f in ("a.txt", "b.txt", "c.txt", "base.txt"))
        assert members_of(sh(repo, "git", "log", "-1", "--format=%B", sha)) == [(n, heads[n]) for n in (1, 2, 3)]
        for n in (1, 2, 3):   # every member head is in the train: GitHub will mark it merged once the train is in main
            assert subprocess.run(["git", "merge-base", "--is-ancestor", heads[n], "train-1"], cwd=repo).returncode == 0
        # a conflict names the pull request and the content it conflicts with, and builds nothing
        sh(repo, "git", "checkout", "-q", "main")
        ok, why = merge_members(repo, "main", "train-2", [("#1", "pr1"), ("#4", "pr4")])
        assert not ok and why == "#4 conflicts with main plus #1", why
        assert sh(repo, "git", "status", "--porcelain") == "" and sh(repo, "git", "rev-parse", "--abbrev-ref", "HEAD") == "train-2"
        ok, why = merge_members(repo, "main", "train-3", [("#4", "pr4"), ("#1", "pr1")])
        assert not ok and why == "#1 conflicts with main plus #4", why
        ok, why = merge_members(repo, "main", "train-4", [("#1", "pr1"), ("#2", "pr2")])
        assert ok
    # halves: the first half is the larger one, a pair splits into two singles, a single cannot split
    ms = [(1, "a"), (2, "b"), (3, "c")]
    assert halves(ms) == ([(1, "a"), (2, "b")], [(3, "c")]) and halves(ms[:2]) == ([(1, "a")], [(2, "b")])
    assert members_of("train x\n\nTrain-Member: #5 " + "a" * 40 + "\nnot a member\n") == [(5, "a" * 40)]
    assert members_of("Train-Member: #5 abc") == []
    print("merge_train self-test: ok")


def main():
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        return
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)
    b = sub.add_parser("build"); b.add_argument("--branch", required=True); b.add_argument("--max", type=int, default=3)
    b.add_argument("--push", action="store_true"); b.add_argument("--open-pr", action="store_true"); b.add_argument("--force-members", action="store_true")
    b.add_argument("prs", type=int, nargs="+")
    s = sub.add_parser("split"); s.add_argument("--branch", required=True); s.add_argument("--prefix", required=True)
    f = sub.add_parser("finish"); f.add_argument("--pr", type=int, required=True)
    args = p.parse_args()
    {"build": build, "split": split, "finish": finish}[args.cmd](args)


if __name__ == "__main__":
    main()
