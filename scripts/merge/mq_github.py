"""The GitHub and git side of the merge queue (mq.py): the gh calls, patch-id, the ssh update. mq_selftest.py replaces Gh with a fake and runs the real one against a fake gh binary."""
import json
import os
import shutil
import subprocess
import time

import gapscan


def sh(args, cwd=None, check=False, timeout=180):
    """A call that hangs (gh without network, a stuck ssh push) must not block a lane or the pause check: after `timeout` seconds it is a failed call (exit 124)."""
    try:
        r = subprocess.run(args, cwd=cwd, capture_output=True, text=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        r = subprocess.CompletedProcess(args, 124, "", f"timed out after {timeout} s")
    if check and r.returncode:
        raise SystemExit(f"{' '.join(args[:3])} failed ({r.returncode}): {(r.stderr or r.stdout).strip()[:300]}")
    return r


class SignalError(Exception):
    """gh could not say how main's runs ended."""


class Gh:
    """The GitHub side; the self-test replaces it. A call that fails returns None (or False), never a value that looks like a result."""
    RETRY_PAUSE = 2

    def view(self, n):
        r = sh(["gh", "pr", "view", str(n), "--json", "state,mergeStateStatus,labels,headRefName,headRefOid"])
        try:
            d = json.loads(r.stdout)
            return {"state": d["state"], "merge": d["mergeStateStatus"], "labels": [l["name"] for l in d["labels"]], "branch": d["headRefName"], "head": d["headRefOid"]}
        except (ValueError, KeyError):
            return None

    def open_prs(self):
        """Every open pull request with what the gap scan (gapscan.py) needs, or None when gh cannot say."""
        r = sh(["gh", "pr", "list", "--state", "open", "--limit", str(gapscan.OPEN_LIMIT), "--json", "number,title,headRefName,headRefOid,isDraft,mergeable,statusCheckRollup,commits,comments,createdAt"], timeout=120)
        try:
            prs = json.loads(r.stdout)
        except ValueError:
            return None
        if r.returncode:
            return None
        for pr in prs:
            pr["comments"] = [dict(c, author=(c.get("author") or {}).get("login")) for c in pr.get("comments", [])]
        return prs

    def behind_by(self, n, head):
        r = sh(["gh", "api", f"repos/{{owner}}/{{repo}}/compare/main...{head}", "--jq", ".behind_by"])
        return int(r.stdout.strip()) if r.returncode == 0 and r.stdout.strip().isdigit() else None

    def behind_files(self, head):
        """Paths main changed since the branch point of `head` (what the head lacks), or None when gh cannot say or the list is cut off (the compare API lists at most 300 files)."""
        r = sh(["gh", "api", f"repos/{{owner}}/{{repo}}/compare/{head}...main", "--jq", "[.files[] | .filename, .previous_filename] | map(select(. != null)) | .[]"])
        if r.returncode:
            return None
        files = [x for x in r.stdout.splitlines() if x]
        n = sh(["gh", "api", f"repos/{{owner}}/{{repo}}/compare/{head}...main", "--jq", ".files | length"])
        return files if n.returncode == 0 and n.stdout.strip().isdigit() and int(n.stdout.strip()) < 300 else None

    def files(self, n):
        r = sh(["gh", "api", "--paginate", f"repos/{{owner}}/{{repo}}/pulls/{n}/files", "--jq", ".[] | .filename, .previous_filename"])
        return [x for x in r.stdout.splitlines() if x and x != "null"] if r.returncode == 0 else None

    def checks(self, n):
        r = sh(["gh", "pr", "checks", str(n), "--json", "name,bucket,link"])
        try:
            return json.loads(r.stdout)
        except ValueError:
            # a pull request whose CI has not registered anything yet: empty stdout, exit 0, "no checks reported on the '<branch>' branch" on stderr
            return [] if "no checks reported" in r.stderr else None

    def failed_steps(self, run):
        """Names of the steps that failed in a ci run, or None when gh cannot say."""
        r = sh(["gh", "run", "view", str(run), "--json", "jobs", "--jq", '.jobs[].steps[] | select(.conclusion == "failure") | .name'])
        return r.stdout.splitlines() if r.returncode == 0 else None

    def docker_ok(self):
        return sh(["docker", "info"], timeout=60).returncode == 0

    def update_branch(self, n):
        return sh(["gh", "pr", "update-branch", str(n)]).returncode == 0

    def label(self, n, name, add):
        return sh(["gh", "pr", "edit", str(n), "--add-label" if add else "--remove-label", name]).returncode == 0

    def active_runs(self, branch):
        r = sh(["gh", "run", "list", "--branch", branch, "--workflow", "ci", "--json", "databaseId,status"])
        try:
            return [x["databaseId"] for x in json.loads(r.stdout) if x["status"] != "completed"]
        except ValueError:
            return []

    def ci_started(self, branch, head):
        """True when a ci run for exactly this head exists (any state); False when none is listed yet or gh does not answer."""
        r = sh(["gh", "run", "list", "--branch", branch, "--workflow", "ci", "--limit", "10", "--json", "headSha"])
        try:
            return any(x.get("headSha") == head for x in json.loads(r.stdout))
        except ValueError:
            return False

    def cancel_run(self, run):
        return sh(["gh", "run", "cancel", str(run)]).returncode == 0

    def rerun(self, run):
        return sh(["gh", "run", "rerun", str(run), "--failed"]).returncode == 0 or sh(["gh", "run", "rerun", str(run)]).returncode == 0

    def main_run(self, event):
        """The verdict run of main for `event` (push | schedule): the newest run that has ended and was not cancelled or skipped ({'id', 'conclusion', 'sha'}), or None when there is none.
        A push run is cancelled by the next push (concurrency), so the newest run alone says nothing. Raises SignalError when gh cannot answer."""
        args = ["gh", "run", "list", "--workflow", "ci", "--event", event, "--limit", "20", "--json", "databaseId,status,conclusion,headSha,headBranch"]
        if event == "push":
            args += ["--branch", "main"]
        r = sh(args)
        try:
            rows = json.loads(r.stdout)
        except ValueError:
            raise SignalError(f"gh run list ({event}) gave no JSON: {(r.stderr or r.stdout).strip()[:120]}")
        for row in rows:
            if row.get("headBranch", "main") == "main" and row["status"] == "completed" and row["conclusion"] not in ("cancelled", "skipped", None, ""):
                return {"id": row["databaseId"], "status": "completed", "conclusion": row["conclusion"], "sha": row["headSha"]}
        return None

    def merge(self, n, head):
        r = sh(["gh", "pr", "merge", str(n), "--merge", "--match-head-commit", head])
        return r.returncode == 0, (r.stderr or r.stdout).strip()

    def reviews(self, n):
        """[{body, commit, at}] of every review of the pull request, or None when gh cannot say."""
        r = sh(["gh", "api", "--paginate", "--slurp", f"repos/{{owner}}/{{repo}}/pulls/{n}/reviews"])
        try:
            pages = json.loads(r.stdout)
        except ValueError:
            return None
        if r.returncode or not isinstance(pages, list) or not all(isinstance(p, list) for p in pages):
            return None
        return [{"body": x.get("body") or "", "commit": x.get("commit_id"), "at": x.get("submitted_at") or ""} for page in pages for x in page if isinstance(x, dict)]

    def comments(self, n):
        """[{author, body, at}] of the pull request's comments, or None when gh cannot say."""
        r = sh(["gh", "pr", "view", str(n), "--json", "comments"])
        try:
            rows = json.loads(r.stdout)["comments"]
            return [{"author": (c.get("author") or {}).get("login"), "body": c.get("body") or "", "at": c.get("createdAt") or ""} for c in rows]
        except (ValueError, KeyError, TypeError, AttributeError):
            return None

    def patch_id(self, repo, n, commit):
        """`git patch-id --verbatim` of the change `commit` makes against main (`git diff origin/main...<commit>`), or None when git cannot say.
        --verbatim, not --stable: a change that differs only in whitespace (indentation in Python) is another change. A commit no longer on the branch (rebased away) is fetched by its sha."""
        for attempt in range(3):   # the other lane's fetch in the same repository may hold a ref lock for a moment
            if sh(["git", "-C", repo, "fetch", "-q", "--no-write-fetch-head", "origin", "+refs/heads/main:refs/remotes/origin/main", f"refs/pull/{n}/head"]).returncode == 0:
                break
            time.sleep(self.RETRY_PAUSE)
        else:
            return None
        if sh(["git", "-C", repo, "cat-file", "-e", f"{commit}^{{commit}}"]).returncode and len(commit) == 40:
            sh(["git", "-C", repo, "fetch", "-q", "--no-write-fetch-head", "origin", commit])
        diff = sh(["git", "-C", repo, "diff", "--no-color", "--no-ext-diff", f"origin/main...{commit}"])
        if diff.returncode or not diff.stdout:
            return None
        out = subprocess.run(["git", "patch-id", "--verbatim"], input=diff.stdout, capture_output=True, text=True, timeout=60)
        first = out.stdout.split()
        return first[0] if out.returncode == 0 and first else None

    def readback(self, n):
        """(state, parents of the merge commit) as GitHub shows them after the merge, or None when gh cannot say."""
        r = sh(["gh", "pr", "view", str(n), "--json", "state,mergeCommit"])
        try:
            d = json.loads(r.stdout)
            state, oid = d["state"], (d.get("mergeCommit") or {}).get("oid")
        except (ValueError, KeyError, AttributeError):
            return None
        if not oid:
            return state, []
        c = sh(["gh", "api", f"repos/{{owner}}/{{repo}}/commits/{oid}", "--jq", '[.parents[].sha] | join(" ")'])
        return (state, c.stdout.split()) if c.returncode == 0 else None


def update_over_ssh(repo, branch, wt):
    """Local merge of origin/main into the pull request branch, pushed over ssh (git, not the gh token): 'ok' | 'conflict' | 'error'."""
    if sh(["git", "-C", repo, "fetch", "-q", "origin"]).returncode:
        return "error"
    sh(["git", "-C", repo, "worktree", "remove", "-f", wt])
    sh(["git", "-C", repo, "worktree", "prune"])
    if os.path.exists(wt):   # a plain directory an earlier, killed run left behind; only our own scratch directory is ever removed
        if not os.path.basename(wt).startswith("mq-wt"):
            return "error"
        shutil.rmtree(wt, ignore_errors=True)
    if sh(["git", "-C", repo, "worktree", "add", "-q", "--detach", wt, f"origin/{branch}"]).returncode:
        return "error"
    try:
        if sh(["git", "-C", wt, "merge", "-q", "--no-edit", "origin/main"]).returncode:
            conflicted = [name for name in sh(["git", "-C", wt, "diff", "--name-only", "-z", "--diff-filter=U"]).stdout.split("\0") if name]
            if not conflicted:
                sh(["git", "-C", wt, "merge", "--abort"])
                return "error"
            sh(["git", "-C", wt, "merge", "--abort"])
            return "conflict"
        return "ok" if sh(["git", "-C", wt, "push", "-q", "origin", f"HEAD:{branch}"]).returncode == 0 else "error"
    finally:
        sh(["git", "-C", repo, "worktree", "remove", "-f", wt])
