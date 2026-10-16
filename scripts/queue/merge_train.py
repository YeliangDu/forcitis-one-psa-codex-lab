#!/usr/bin/env python3
"""Code merge train: test up to N reviewed pull requests with one CI run instead of N.

  merge_train.py build  --branch claude/one-coordinator-train-14 [--max 3] [--push [--open-pr]] <pr> <pr> [<pr>]
        merge the heads of the pull requests, in the order given, onto origin/main in a scratch worktree (--no-ff), add a commit that records the members
        ("Train-Member: #123 <head sha>"), print the result. --push pushes the branch (refused when the branch already exists locally or on origin); --open-pr (needs --push) opens the
        train pull request, whose body repeats the member lines.
  merge_train.py docs   --branch claude/one-coordinator-docs-0616 [--max 20] [--push [--open-pr]] <会话分支>@<提交号> [<会话分支>@<提交号> ...]
        collect documentation commits that sessions pushed to their own branches (no pull request of their own): fetch each branch, require the commit to be on it and not yet in main,
        require that what the commit brings (diff origin/main...commit) touches only docs/, merge the commits ONE BY ONE with --no-ff (never an octopus merge: that fires the acceptance-test
        signature guard), re-check docs/-only on the result, add a commit that records "Train-Doc: <branch> <sha>". --push / --open-pr as for build.
  merge_train.py split  --pr <failed-train-pr> --prefix claude/one-coordinator-train-14       members from the train pull request body; prints the two `build` commands for the halves
  merge_train.py finish --pr <merged-train-pr> [--squashed]
        close the member pull requests GitHub did not mark merged. A member is closed only when its head still is the recorded one (a member that got new commits after the train was built
        stays open) and that head is in origin/main; with --squashed (the train was squash-merged, so no member head is in main) the head check alone decides.

Why: the strict queue re-runs the whole CI for every pull request (about half of all CI minutes went to runs nobody used). A train of N tests one tree for N pull requests; a failure
costs a bisection (split) instead of N reruns. The queue stays the arbiter: the train pull request is labelled `queued` like any other (scripts/ci/heavy_gate.py) and merged with a MERGE COMMIT,
so GitHub marks each member merged once its head is in main. Members are taken from `pull/<n>/head`, and the fetched sha must equal the pull request's recorded head.
A conflict names the pull request and what it conflicts with and builds nothing (take that pull request out, or have it updated from main). Whether the members are reviewed and complete is the
queue's decision, not checked here. A semantic conflict between members (no text conflict, red CI) is found by split.

Usage: merge_train.py build|docs|split|finish ...   |   merge_train.py --self-test
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


class Gh:
    """The GitHub side; the self-test replaces it."""

    def pr(self, n):
        return json.loads(run(["gh", "pr", "view", str(n), "--json", "headRefOid,state,isDraft,baseRefName,body"]).stdout)

    def close(self, n, comment):
        run(["gh", "pr", "close", str(n), "--comment", comment])

    def create(self, branch, title, body):
        return run(["gh", "pr", "create", "--base", "main", "--head", branch, "--title", title, "--body", body]).stdout.strip()


def merge_members(repo, base_ref, branch, members):
    """members: [(label, ref)] -> (ok, message). Builds `branch` in `repo` from base_ref by merging each ref with --no-ff."""
    git(repo, "checkout", "-q", "-B", branch, base_ref)
    done = []
    for label, ref in members:
        r = git(repo, "merge", "--no-ff", "--no-edit", "-m", f"train: merge {label}", ref, check=False)
        if r.returncode:
            git(repo, "merge", "--abort", check=False)
            return False, f"{label} conflicts with {'main' if not done else 'main plus ' + ', '.join(done)}"
        done.append(label)
    return True, "ok"


def member_lines(members):
    return "\n".join(f"Train-Member: #{n} {sha}" for n, sha in members)


def train_message(branch, members):
    return f"train {branch}: " + ", ".join(f"#{n}" for n, _ in members) + "\n\n" + member_lines(members) + "\n"


def members_of(text):
    return [(int(n), sha) for n, sha in MEMBER.findall(text)]


def record(repo, branch, members):
    git(repo, "commit", "-q", "--allow-empty", "-m", train_message(branch, members))
    return git(repo, "rev-parse", "HEAD").stdout.strip()


def halves(members):
    mid = (len(members) + 1) // 2
    return members[:mid], members[mid:]


def branch_exists(repo, branch):
    if git(repo, "rev-parse", "--verify", "-q", f"refs/heads/{branch}", check=False).returncode == 0:
        return "locally"
    if git(repo, "ls-remote", "--exit-code", "--heads", "origin", branch, check=False).returncode == 0:
        return "on origin"
    return None


def fetch_head(repo, n):
    git(repo, "fetch", "-q", "origin", f"pull/{n}/head")
    return git(repo, "rev-parse", "FETCH_HEAD").stdout.strip()


def build(args, gh, repo="."):
    if len(args.prs) > args.max:
        raise SystemExit(f"at most {args.max} pull requests per train (--max)")
    if args.open_pr and not args.push:
        raise SystemExit("--open-pr needs --push")
    where = branch_exists(repo, args.branch)
    if where:
        raise SystemExit(f"branch {args.branch} already exists {where}: pick another name")
    git(repo, "fetch", "-q", "origin", "main")
    members, refs = [], []
    for n in args.prs:
        d = gh.pr(n)
        if d["state"] != "OPEN" or d["baseRefName"] != "main":
            raise SystemExit(f"#{n} is not an open pull request into main ({d['state']}, base {d['baseRefName']})")
        if d["isDraft"] and not args.force_members:
            raise SystemExit(f"#{n} is a draft")
        sha = fetch_head(repo, n)
        if sha != d["headRefOid"]:
            raise SystemExit(f"#{n} head moved while fetching ({sha[:10]} vs {d['headRefOid'][:10]}): run again")
        members.append((n, sha))
        refs.append((f"#{n}", sha))
    wt = None
    try:
        wt = tempfile.mkdtemp(prefix="train-")
        git(repo, "worktree", "add", "-q", "--detach", wt, "origin/main")
        ok, why = merge_members(wt, "origin/main", args.branch, refs)
        if not ok:
            raise SystemExit(f"no train built: {why}; take that pull request out or update it from main")
        commit = record(wt, args.branch, members)
        print(f"train {args.branch} at {commit[:10]} with {', '.join(f'#{n}' for n, _ in members)}")
        if args.push:
            git(wt, "push", "-q", "origin", f"{args.branch}:refs/heads/{args.branch}")
            print(f"pushed {args.branch}")
            if args.open_pr:
                body = "Train of reviewed pull requests, tested once. Merge it with a MERGE COMMIT (not squash). Members:\n\n" + member_lines(members) + \
                       "\n\nAfter it merges, `scripts/queue/merge_train.py finish --pr <this>` closes the members GitHub did not mark merged.\n\n🤖 Generated with [Claude Code](https://claude.com/claude-code)\n"
                print(gh.create(args.branch, f"train: {', '.join(f'#{n}' for n, _ in members)}", body))
    finally:
        if wt:
            git(repo, "worktree", "remove", "-f", wt, check=False)
            git(repo, "branch", "-D", args.branch, check=False)   # the scratch branch lives in the pushed ref, not here


SPEC = re.compile(r"^([A-Za-z0-9._/-]+)@([0-9a-f]{7,40})$")


def non_docs(repo, ref):
    """Paths that origin/main...ref (what `ref` brings in since its merge base) changes outside docs/."""
    out = git(repo, "diff", "--name-only", "-z", "origin/main...%s" % ref).stdout
    return [p for p in out.split("\0") if p and not p.startswith("docs/")]


def fetch_branch_commit(repo, branch, short):
    """Full sha of `short`, which must be an ancestor of (or equal to) origin's `branch` and not yet in origin/main."""
    if git(repo, "fetch", "-q", "origin", f"refs/heads/{branch}", check=False).returncode:
        raise SystemExit(f"branch {branch} not found on origin")
    tip = git(repo, "rev-parse", "FETCH_HEAD").stdout.strip()
    r = git(repo, "rev-parse", "--verify", "-q", f"{short}^{{commit}}", check=False)
    if r.returncode:
        raise SystemExit(f"{branch}@{short}: commit unknown here (not pushed to origin?)")
    sha = r.stdout.strip()
    if git(repo, "merge-base", "--is-ancestor", sha, tip, check=False).returncode:
        raise SystemExit(f"{branch}@{short} is not on origin/{branch}: push it first")
    if git(repo, "merge-base", "--is-ancestor", sha, "origin/main", check=False).returncode == 0:
        raise SystemExit(f"{branch}@{short} is already in origin/main: nothing to collect")
    return sha


def build_docs(args, gh, repo="."):
    if len(args.specs) > args.max:
        raise SystemExit(f"at most {args.max} commits per docs train (--max)")
    if args.open_pr and not args.push:
        raise SystemExit("--open-pr needs --push")
    specs = []
    for x in args.specs:
        m = SPEC.match(x)
        if not m:
            raise SystemExit(f"{x!r}: write <session branch>@<commit sha>")
        specs.append(m.groups())
    where = branch_exists(repo, args.branch)
    if where:
        raise SystemExit(f"branch {args.branch} already exists {where}: pick another name")
    git(repo, "fetch", "-q", "origin", "main")
    members = []
    for branch, short in specs:
        sha = fetch_branch_commit(repo, branch, short)
        bad = non_docs(repo, sha)
        if bad:
            raise SystemExit(f"{branch}@{short} touches files outside docs/ ({', '.join(bad[:5])}{' ...' if len(bad) > 5 else ''}): code goes in a pull request, not into a docs train")
        members.append((branch, sha))
    wt = None
    try:
        wt = tempfile.mkdtemp(prefix="train-")
        git(repo, "worktree", "add", "-q", "--detach", wt, "origin/main")
        ok, why = merge_members(wt, "origin/main", args.branch, [(f"{b}@{sha[:10]}", sha) for b, sha in members])
        if not ok:
            raise SystemExit(f"no docs train built: {why}; take that commit out or have its branch updated from main")
        msg = f"docs train {args.branch}: " + ", ".join(f"{b}@{sha[:10]}" for b, sha in members) + "\n\n" + "\n".join(f"Train-Doc: {b} {sha}" for b, sha in members) + "\n"
        git(wt, "commit", "-q", "--allow-empty", "-m", msg)
        commit = git(wt, "rev-parse", "HEAD").stdout.strip()
        print(f"docs train {args.branch} at {commit[:10]} with {', '.join(f'{b}@{sha[:10]}' for b, sha in members)}")
        if args.push:
            git(wt, "push", "-q", "origin", f"{args.branch}:refs/heads/{args.branch}")
            print(f"pushed {args.branch}")
            if args.open_pr:
                body = "Documentation commits collected from session branches (docs/ only, merged one by one). Merge it with a MERGE COMMIT (not squash). Commits:\n\n" + \
                       "\n".join(f"Train-Doc: {b} {sha}" for b, sha in members) + "\n\n🤖 Generated with [Claude Code](https://claude.com/claude-code)\n"
                print(gh.create(args.branch, f"docs train: {len(members)} commit(s)", body))
    finally:
        if wt:
            git(repo, "worktree", "remove", "-f", wt, check=False)
            git(repo, "branch", "-D", args.branch, check=False)


def split(args, gh):
    d = gh.pr(args.pr)
    members = members_of(d["body"])
    if len(members) < 2:
        raise SystemExit(f"#{args.pr} lists {len(members)} member(s): nothing to split (that pull request itself fails)")
    for i, part in enumerate(halves(members), 1):
        print(f"python3 scripts/queue/merge_train.py build --branch {args.prefix}-{i} --push --open-pr " + " ".join(str(n) for n, _ in part))


def finish(args, gh, repo="."):
    train = gh.pr(args.pr)
    if train["state"] != "MERGED":
        raise SystemExit(f"train pull request #{args.pr} is {train['state']}, not MERGED")
    members = members_of(train["body"])
    if not members:
        raise SystemExit(f"#{args.pr} lists no Train-Member lines (was the body edited?): nothing closed")
    git(repo, "fetch", "-q", "origin", "main")
    for n, sha in members:
        d = gh.pr(n)
        if d["state"] != "OPEN":
            print(f"#{n} already {d['state']}")
        elif d["headRefOid"] != sha:
            print(f"#{n} KEPT OPEN: its head moved since the train ({sha[:10]} -> {d['headRefOid'][:10]}), the new commits are not in main")
        elif not args.squashed and git(repo, "merge-base", "--is-ancestor", sha, "origin/main", check=False).returncode != 0:
            print(f"#{n} KEPT OPEN: its head {sha[:10]} is not in origin/main (was the train squash-merged? then use --squashed)")
        else:
            gh.close(n, f"Merged through train #{args.pr}.")
            print(f"closed #{n}")


def self_test():
    env = dict(os.environ, GIT_AUTHOR_NAME="t", GIT_AUTHOR_EMAIL="t@t", GIT_COMMITTER_NAME="t", GIT_COMMITTER_EMAIL="t@t", GIT_CONFIG_GLOBAL="/dev/null", GIT_CONFIG_SYSTEM="/dev/null")

    def sh(cwd, *a, check=True):
        r = subprocess.run(a, cwd=cwd, capture_output=True, text=True, env=env)
        if check and r.returncode:
            raise AssertionError(f"{a}: {r.stderr}")
        return r.stdout.strip()

    class FakeGh:
        def __init__(self):
            self.prs, self.closed, self.created = {}, [], []

        def pr(self, n):
            return dict(self.prs[n])

        def close(self, n, comment):
            self.closed.append(n); self.prs[n]["state"] = "CLOSED"

        def create(self, branch, title, body):
            self.created.append((branch, title, body)); return "https://example/pull/999"

    with tempfile.TemporaryDirectory() as d:
        origin = os.path.join(d, "origin.git")
        sh(d, "git", "init", "-q", "--bare", "-b", "main", origin)
        work = os.path.join(d, "work")
        sh(d, "git", "clone", "-q", origin, work)
        sh(work, "git", "config", "commit.gpgsign", "false")

        def commit(path, text, msg, repo=work):
            os.makedirs(os.path.dirname(os.path.join(repo, path)), exist_ok=True)
            with open(os.path.join(repo, path), "w") as f:
                f.write(text)
            sh(repo, "git", "add", path); sh(repo, "git", "commit", "-q", "-m", msg)
            return sh(repo, "git", "rev-parse", "HEAD")

        commit("base.txt", "base\n", "base")
        sh(work, "git", "push", "-q", "origin", "main")
        gh, heads = FakeGh(), {}
        for n, (path, text) in {1: ("a.txt", "a\n"), 2: ("b.txt", "b\n"), 3: ("c.txt", "c\n"), 4: ("a.txt", "conflicting a\n")}.items():
            sh(work, "git", "checkout", "-q", "-B", f"pr{n}", "main")
            heads[n] = commit(path, text, f"pr{n}")
            sh(work, "git", "push", "-q", "origin", f"pr{n}:refs/pull/{n}/head")
            gh.prs[n] = dict(headRefOid=heads[n], state="OPEN", isDraft=False, baseRefName="main", body="")
        sh(work, "git", "checkout", "-q", "main")

        def fails(fn, fragment):
            try:
                fn()
            except SystemExit as e:
                assert fragment in str(e), (fragment, str(e))
                return
            raise AssertionError(f"expected failure containing {fragment!r}")

        # a clean train pushed and opened; the local scratch branch and worktree are gone afterwards
        build(argparse.Namespace(branch="train-1", prs=[1, 2, 3], max=3, push=True, open_pr=True, force_members=False), gh, work)
        assert gh.created and members_of(gh.created[0][2]) == [(n, heads[n]) for n in (1, 2, 3)]
        assert branch_exists(work, "train-1") == "on origin" and not sh(work, "git", "worktree", "list", "--porcelain").count("train-")
        sh(work, "git", "fetch", "-q", "origin")
        assert members_of(sh(work, "git", "log", "-1", "--format=%B", "origin/train-1")) == [(n, heads[n]) for n in (1, 2, 3)]
        assert sh(work, "git", "rev-list", "--merges", "--count", "origin/main..origin/train-1") == "3", "one merge commit per member, even when a merge could fast-forward"
        for n in (1, 2, 3):
            assert subprocess.run(["git", "merge-base", "--is-ancestor", heads[n], "origin/train-1"], cwd=work).returncode == 0
        # refusals: existing branch (on origin), --open-pr without --push, too many, closed, draft, moved head, conflict
        fails(lambda: build(argparse.Namespace(branch="train-1", prs=[1], max=3, push=False, open_pr=False, force_members=False), gh, work), "already exists on origin")
        fails(lambda: build(argparse.Namespace(branch="t-x", prs=[1], max=3, push=False, open_pr=True, force_members=False), gh, work), "--open-pr needs --push")
        fails(lambda: build(argparse.Namespace(branch="t-x", prs=[1, 2, 3, 4], max=3, push=False, open_pr=False, force_members=False), gh, work), "at most 3")
        gh.prs[2]["isDraft"] = True
        fails(lambda: build(argparse.Namespace(branch="t-y", prs=[1, 2], max=3, push=False, open_pr=False, force_members=False), gh, work), "#2 is a draft")
        gh.prs[2]["isDraft"] = False
        moved = dict(gh.prs[1]); gh.prs[1]["headRefOid"] = "0" * 40
        fails(lambda: build(argparse.Namespace(branch="t-z", prs=[1], max=3, push=False, open_pr=False, force_members=False), gh, work), "head moved")
        gh.prs[1] = moved
        fails(lambda: build(argparse.Namespace(branch="t-c", prs=[1, 4], max=3, push=False, open_pr=False, force_members=False), gh, work), "#4 conflicts with main plus #1")
        fails(lambda: build(argparse.Namespace(branch="t-c2", prs=[4, 1], max=3, push=False, open_pr=False, force_members=False), gh, work), "#1 conflicts with main plus #4")
        assert branch_exists(work, "t-c") is None and sh(work, "git", "worktree", "list", "--porcelain").count("worktree ") == 1, "nothing is left behind"
        # a scratch branch name that exists locally is refused too (checkout -B must never reset it)
        sh(work, "git", "branch", "keepme", "main")
        fails(lambda: build(argparse.Namespace(branch="keepme", prs=[1], max=3, push=False, open_pr=False, force_members=False), gh, work), "already exists locally")
        # docs train: session-branch commits collected one by one, docs/ only
        def mk(branch, files, msg):
            sh(work, "git", "checkout", "-q", "-B", branch, "main")
            for path, text in files:
                commit(path, text, msg)
            sh(work, "git", "push", "-q", "origin", branch)
            return sh(work, "git", "rev-parse", "HEAD")

        d1 = mk("d1", [("docs/a1.md", "one\n")], "doc one")
        d2 = mk("d2", [("docs/a2.md", "two\n")], "doc two")
        d3 = mk("d3", [("docs/a1.md", "conflicting\n")], "doc conflicting")
        d4 = mk("d4", [("docs/x.md", "x\n"), ("code/y.py", "y\n")], "mixed")
        mk("d5", [("code/z.py", "z\n")], "code first")
        d5 = commit("docs/q.md", "q\n", "docs after code")
        sh(work, "git", "push", "-q", "origin", "d5")
        sh(work, "git", "checkout", "-q", "main")
        base_sha = sh(work, "git", "rev-parse", "main")

        def docs(branch, specs, **kw):
            ns = dict(branch=branch, specs=specs, max=20, push=False, open_pr=False)
            ns.update(kw)
            return lambda: build_docs(argparse.Namespace(**ns), gh, work)

        docs("docs-1", [f"d1@{d1[:10]}", f"d2@{d2}"], push=True, open_pr=True)()
        assert gh.created[-1][2].count("Train-Doc: ") == 2 and f"Train-Doc: d1 {d1}" in gh.created[-1][2] and f"Train-Doc: d2 {d2}" in gh.created[-1][2]
        sh(work, "git", "fetch", "-q", "origin")
        assert sh(work, "git", "rev-list", "--merges", "--count", "origin/main..origin/docs-1") == "2", "one merge commit per commit, no octopus"
        assert all(len(l.split()) == 3 for l in sh(work, "git", "rev-list", "--merges", "--parents", "origin/main..origin/docs-1").splitlines()), "every merge has exactly two parents"
        assert sh(work, "git", "diff", "--name-only", "origin/main", "origin/docs-1").split() == ["docs/a1.md", "docs/a2.md"]
        assert f"Train-Doc: d1 {d1}" in sh(work, "git", "log", "-1", "--format=%B", "origin/docs-1")
        fails(docs("docs-2", [f"d4@{d4}"]), f"d4@{d4} touches files outside docs/ (code/y.py)")
        fails(docs("docs-2", [f"d5@{d5}"]), f"d5@{d5} touches files outside docs/ (code/z.py)")
        fails(docs("docs-2", [f"d1@{d1}", f"d3@{d3}"]), "d3@" + d3[:10] + " conflicts with main plus d1@" + d1[:10])
        fails(docs("docs-2", [f"nobranch@{d1}"]), "branch nobranch not found on origin")
        fails(docs("docs-2", [f"d1@{d2}"]), f"d1@{d2} is not on origin/d1")
        fails(docs("docs-2", [f"d1@{base_sha}"]), "already in origin/main")
        fails(docs("docs-2", ["d1@deadbeefdead"]), "commit unknown here")
        fails(docs("docs-2", ["d1"]), "write <session branch>@<commit sha>")
        fails(docs("docs-1", [f"d1@{d1}"]), "already exists on origin")
        fails(docs("docs-2", [f"d1@{d1}", f"d2@{d2}"], max=1), "at most 1")
        fails(docs("docs-2", [f"d1@{d1}"], open_pr=True), "--open-pr needs --push")
        assert branch_exists(work, "docs-2") is None and sh(work, "git", "worktree", "list", "--porcelain").count("worktree ") == 1, "nothing is left behind"
        # main moving on with a code change must not make an older docs commit look like a code change (three-dot diff)
        sh(work, "git", "checkout", "-q", "-B", "advance", "main")
        commit("code/new.py", "n\n", "code lands in main")
        sh(work, "git", "push", "-q", "origin", "advance:main")
        sh(work, "git", "checkout", "-q", "main")
        docs("docs-3", [f"d1@{d1}"])()
        sh(work, "git", "push", "-q", "-f", "origin", f"{base_sha}:main")
        sh(work, "git", "branch", "-D", "advance")
        sh(work, "git", "fetch", "-q", "origin")
        assert non_docs(work, d1) == [] and non_docs(work, d4) == ["code/y.py"]
        # split reads the pull request body, so a strict update (extra commit on the branch) does not matter
        gh.prs[900] = dict(headRefOid="a" * 40, state="OPEN", isDraft=False, baseRefName="main", body=gh.created[0][2])
        import io, contextlib
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            split(argparse.Namespace(pr=900, prefix="train-9"), gh)
        assert out.getvalue().splitlines() == ["python3 scripts/queue/merge_train.py build --branch train-9-1 --push --open-pr 1 2",
                                               "python3 scripts/queue/merge_train.py build --branch train-9-2 --push --open-pr 3"], out.getvalue()
        gh.prs[901] = dict(gh.prs[900], body="Train-Member: #7 " + "b" * 40)
        fails(lambda: split(argparse.Namespace(pr=901, prefix="x"), gh), "nothing to split")
        # finish: merge the train (merge commit) into main; a member that moved on is kept open, the others are handled
        sh(work, "git", "checkout", "-q", "-B", "tmp", "origin/train-1")
        sh(work, "git", "push", "-q", "origin", "tmp:main")
        gh.prs[900] = dict(gh.prs[900], state="MERGED", body=gh.created[0][2])
        gh.prs[1]["state"] = "MERGED"          # GitHub already marked #1 merged
        gh.prs[2]["headRefOid"] = "c" * 40     # #2 got a new commit after the train was built
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            finish(argparse.Namespace(pr=900, squashed=False), gh, work)
        assert gh.closed == [3] and "#1 already MERGED" in out.getvalue() and "#2 KEPT OPEN" in out.getvalue(), (gh.closed, out.getvalue())
        gh.prs[903] = dict(gh.prs[900], body="edited by hand")
        fails(lambda: finish(argparse.Namespace(pr=903, squashed=False), gh, work), "lists no Train-Member lines")
        gh.prs[902] = dict(gh.prs[900], state="OPEN")
        fails(lambda: finish(argparse.Namespace(pr=902, squashed=False), gh, work), "not MERGED")
        # a squash-merged train: member heads are not in main, so the plain run keeps them open and --squashed decides on the recorded head
        sh(work, "git", "fetch", "-q", "origin")
        sh(work, "git", "checkout", "-q", "-B", "sq", "origin/main")
        sh(work, "git", "commit", "-q", "--allow-empty", "-m", "squashed train")
        sh(work, "git", "push", "-q", "origin", "sq:main")
        sh(work, "git", "update-ref", "-d", "refs/heads/tmp")
        gh2 = FakeGh()
        gh2.prs = {50: dict(headRefOid="d" * 40, state="OPEN", isDraft=False, baseRefName="main", body=""),
                   51: dict(headRefOid="e" * 40, state="OPEN", isDraft=False, baseRefName="main", body=""),
                   52: dict(headRefOid="f" * 40, state="MERGED", isDraft=False, baseRefName="main",
                            body=member_lines([(50, "d" * 40), (51, "1" * 40)]))}
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            finish(argparse.Namespace(pr=52, squashed=False), gh2, work)
        assert gh2.closed == [] and out.getvalue().count("KEPT OPEN") == 2
        with contextlib.redirect_stdout(io.StringIO()):
            finish(argparse.Namespace(pr=52, squashed=True), gh2, work)
        assert gh2.closed == [50], gh2.closed   # #51's head differs from the recorded one: still kept
    assert halves([(1, "a"), (2, "b"), (3, "c")]) == ([(1, "a"), (2, "b")], [(3, "c")]) and halves([(1, "a"), (2, "b")]) == ([(1, "a")], [(2, "b")])
    assert members_of("train x\n\nTrain-Member: #5 " + "a" * 40 + "\nnot a member\nTrain-Member: #6 abc\nTrain-Member: #7 " + "b" * 40 + " extra") == [(5, "a" * 40)]


def main():
    if sys.argv[1:] == ["--self-test"]:
        import contextlib, io
        with contextlib.redirect_stdout(io.StringIO()):
            self_test()
        print("merge_train self-test: ok")
        return
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)
    b = sub.add_parser("build"); b.add_argument("--branch", required=True); b.add_argument("--max", type=int, default=3)
    b.add_argument("--push", action="store_true"); b.add_argument("--open-pr", action="store_true"); b.add_argument("--force-members", action="store_true")
    b.add_argument("prs", type=int, nargs="+")
    d = sub.add_parser("docs"); d.add_argument("--branch", required=True); d.add_argument("--max", type=int, default=20)
    d.add_argument("--push", action="store_true"); d.add_argument("--open-pr", action="store_true"); d.add_argument("specs", nargs="+")
    s = sub.add_parser("split"); s.add_argument("--pr", type=int, required=True); s.add_argument("--prefix", required=True)
    f = sub.add_parser("finish"); f.add_argument("--pr", type=int, required=True); f.add_argument("--squashed", action="store_true")
    args = p.parse_args()
    gh = Gh()
    {"build": lambda: build(args, gh), "docs": lambda: build_docs(args, gh), "split": lambda: split(args, gh), "finish": lambda: finish(args, gh)}[args.cmd]()


if __name__ == "__main__":
    main()
