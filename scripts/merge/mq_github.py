"""The GitHub and git side of the merge queue (mq.py): the gh calls, patch-id, the ssh update. mq_selftest.py replaces Gh with a fake and runs the real one against a fake gh binary."""
import datetime
import json
import os
import re
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


LOG_PREFIX = re.compile(r"^[^\t]*\t[^\t]*\t\S+Z ")   # `gh run view --log-failed` lines: job, step, timestamp, text
LIGHT_FAIL = re.compile(r"^FAIL  (.+?)(?:\s+\d+ s|$)")   # scripts/ci/light.sh: `FAIL  <check name>   3 s（退出 1）`
TEST_FAIL = re.compile(r"^\[ERROR\]\s+([\w.$]+(?:Test|IT)(?:\.\w+)?)\b")   # surefire and failsafe: `[ERROR]   FooIT.bar:42 ...`, `[ERROR] FooIT.bar`
SHELLCHECK_FILE = re.compile(r"^In (\S+) line \d+:$")   # shellcheck: `In scripts/gates/install.sh line 14:`
LOG_TIMEOUT = 30
DETAIL_NAMES, DETAIL_NAME_LEN, DETAIL_LEN, DETAIL_LINE_LEN = 4, 70, 400, 300


def failure_names(log):
    """The first few failing check and test names in the text of a failed run's log (names only, never log lines), in order of appearance."""
    found = []
    for line in log.splitlines():
        line = "".join(ch for ch in LOG_PREFIX.sub("", line[:DETAIL_LINE_LEN]).strip() if ch.isprintable())   # a cut line keeps the patterns linear; control characters never reach the log
        m = LIGHT_FAIL.match(line) or TEST_FAIL.match(line) or SHELLCHECK_FILE.match(line)
        name = ("shellcheck " + m.group(1) if m.re is SHELLCHECK_FILE else m.group(1))[:DETAIL_NAME_LEN] if m else None
        if name and name not in found:
            found.append(name)
    return found[:DETAIL_NAMES]


def failure_detail(gh, runs, steps):
    """Text for the RERUN-FAILED and HOLD log lines: the failed steps and the first failing check or test names, so a later review can tell flaky from real once the CI logs have expired; short, never raises."""
    try:
        failed = sorted({x for r in runs for x in (steps.get(r) or [])})
        names = []
        for r in runs[:2]:
            got = gh.failure_names(r)
            if got is None:
                return " | details not available" if not failed else f" | steps: {', '.join(failed)[:DETAIL_LEN]} | names not available"
            names += [x for x in got if x not in names]
        text = (f" | steps: {', '.join(failed)}" if failed else "") + (f" | failed: {', '.join(names[:DETAIL_NAMES])}" if names else "")
        return text[:DETAIL_LEN] or " | details not available"   # nothing to say is not the same as not having looked
    except Exception:   # a log line must never stop the queue
        return " | details not available"


class SignalError(Exception):
    """gh could not say how main's runs ended."""


class Gh:
    """The GitHub side; the self-test replaces it. A call that fails returns None (or False), never a value that looks like a result."""
    RETRY_PAUSE = 2

    def view(self, n):
        r = sh(["gh", "pr", "view", str(n), "--json", "state,mergeStateStatus,labels,headRefName,headRefOid,title,body"])
        try:
            d = json.loads(r.stdout)
            return {"state": d["state"], "merge": d["mergeStateStatus"], "labels": [l["name"] for l in d["labels"]], "branch": d["headRefName"], "head": d["headRefOid"], "title": d["title"], "body": d["body"]}
        except (ValueError, KeyError):
            return None

    def open_prs(self):
        """Every open pull request with what the gap scan (gapscan.py) needs, or None when gh cannot say."""
        r = sh(["gh", "pr", "list", "--state", "open", "--limit", str(gapscan.OPEN_LIMIT), "--json", "number,title,body,headRefName,headRefOid,isDraft,mergeable,statusCheckRollup,commits,comments,createdAt"], timeout=120)
        try:
            prs = json.loads(r.stdout)
        except ValueError:
            return None
        if r.returncode:
            return None
        for pr in prs:
            pr["comments"] = [dict(c, author=(c.get("author") or {}).get("login")) for c in pr.get("comments", [])]
        return prs

    def merged_prs(self, since):
        """The pull requests merged since `since` (number, body, mergedAt; at most gapscan.MERGED_LIMIT), or None when gh cannot say."""
        stamp = since.astimezone(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        r = sh(["gh", "pr", "list", "--state", "merged", "--search", f"merged:>={stamp}", "--limit", str(gapscan.MERGED_LIMIT), "--json", "number,body,mergedAt"], timeout=120)
        try:
            prs = json.loads(r.stdout)
        except ValueError:
            return None
        return None if r.returncode else prs

    def card(self, n):
        """The state, label names and title of an issue, or None when gh cannot say."""
        r = sh(["gh", "issue", "view", str(n), "--json", "state,labels,title"])
        try:
            d = json.loads(r.stdout)
            return {"state": d["state"], "labels": [l["name"] for l in d["labels"]], "title": d["title"]}
        except (ValueError, KeyError):
            return None

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

    def file_stats(self, n):
        """[(path, previous path or None, additions, deletions)] of the pull request's files, or None when gh cannot say."""
        r = sh(["gh", "api", "--paginate", f"repos/{{owner}}/{{repo}}/pulls/{n}/files", "--jq", '.[] | [.filename, (.previous_filename // ""), .additions, .deletions] | @tsv'])
        if r.returncode:
            return None
        try:
            return [(p[0], p[1] or None, int(p[2]), int(p[3])) for p in (line.split("\t") for line in r.stdout.splitlines() if line)]
        except (IndexError, ValueError):
            return None

    def body_edited_at(self, n):
        """When the pull request's body was last edited (its creation time if never), ISO text, or None when gh cannot say."""
        repo = sh(["gh", "repo", "view", "--json", "nameWithOwner", "--jq", ".nameWithOwner"])   # gh fills {owner}/{repo} in REST paths only, not in a GraphQL query
        owner, _, name = repo.stdout.strip().partition("/")
        if repo.returncode or not owner or not name or not re.fullmatch(r"[\w.-]+", owner + name):
            return None
        q = f'query($n:Int!){{repository(owner:"{owner}",name:"{name}"){{pullRequest(number:$n){{createdAt lastEditedAt}}}}}}'
        r = sh(["gh", "api", "graphql", "-F", f"n={n}", "-f", f"query={q}", "--jq", ".data.repository.pullRequest | (.lastEditedAt // .createdAt)"])
        text = r.stdout.strip()
        return text if r.returncode == 0 and text and text != "null" else None

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

    def failure_names(self, run):
        """Failing check and test names read from the run's failed-step log, or None when gh cannot say (log expired, gh down)."""
        r = sh(["gh", "run", "view", str(run), "--log-failed"], timeout=LOG_TIMEOUT)   # read before the rerun starts: a slow gh must not hold the rerun for long
        return failure_names(r.stdout) if r.returncode == 0 else None

    def docker_ok(self):
        return sh(["docker", "info"], timeout=60).returncode == 0

    def update_branch(self, n):
        return sh(["gh", "pr", "update-branch", str(n)]).returncode == 0

    def label(self, n, name, add):
        return sh(["gh", "pr", "edit", str(n), "--add-label" if add else "--remove-label", name]).returncode == 0

    def labelled(self, name):
        """The pull requests (any state: a merged one is cleaned too) that still carry the label; raises ValueError when gh does not answer (the caller leaves the labels as they are)."""
        r = sh(["gh", "pr", "list", "--state", "all", "--label", name, "--limit", "200", "--json", "number"])
        if r.returncode != 0:
            raise ValueError(r.stderr.strip()[:200] or "gh pr list failed")
        return [x["number"] for x in json.loads(r.stdout)]

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

    def fetch(self, repo, n, commit):
        """Main and the pull request's head into `repo`, and `commit` too when it is no longer on the branch (rebased away: fetched by its sha); False when git cannot."""
        for attempt in range(3):   # the other lane's fetch in the same repository may hold a ref lock for a moment
            if sh(["git", "-C", repo, "fetch", "-q", "--no-write-fetch-head", "origin", "+refs/heads/main:refs/remotes/origin/main", f"refs/pull/{n}/head"]).returncode == 0:
                break
            time.sleep(self.RETRY_PAUSE)
        else:
            return False
        if sh(["git", "-C", repo, "cat-file", "-e", f"{commit}^{{commit}}"]).returncode and len(commit) == 40:
            sh(["git", "-C", repo, "fetch", "-q", "--no-write-fetch-head", "origin", commit])
        return True

    def landed_on_main(self, repo, n, head):
        """The commit on main's first-parent chain that brought `head` in (the earliest one), when `head` is already on origin/main; None when it is not or git cannot say. For a merge call that answered with an error."""
        if not self.fetch(repo, n, head):
            return None
        chain = sh(["git", "-C", repo, "log", "--first-parent", "--ancestry-path", "--format=%H", f"{head}..origin/main"])   # --ancestry-path: only commits that descend from head, so a head that is not on main gives none
        found = chain.stdout.split()
        return found[-1] if chain.returncode == 0 and found else None

    def patch_id(self, repo, n, commit):
        """`git patch-id --verbatim` of the change `commit` makes against main (`git diff origin/main...<commit>`), or None when git cannot say.
        --verbatim, not --stable: a change that differs only in whitespace (indentation in Python) is another change."""
        if not self.fetch(repo, n, commit):
            return None
        diff = sh(["git", "-C", repo, "diff", "--no-color", "--no-ext-diff", f"origin/main...{commit}"])
        if diff.returncode or not diff.stdout:
            return None
        out = subprocess.run(["git", "patch-id", "--verbatim"], input=diff.stdout, capture_output=True, text=True, timeout=60)
        first = out.stdout.split()
        return first[0] if out.returncode == 0 and first else None

    def main_merge_parent(self, repo, n, commit):
        """The first parent of `commit` when `commit` only merges main into it: two parents, the second one on main, and exactly the tree a clean merge of the two gives
        (nothing added in the merge itself). The merge the queue makes when it brings a pull request up to date is such a commit. None otherwise, or when git cannot say."""
        if not self.fetch(repo, n, commit):
            return None
        parents = sh(["git", "-C", repo, "rev-list", "--parents", "-n", "1", commit]).stdout.split()
        if len(parents) != 3 or sh(["git", "-C", repo, "merge-base", "--is-ancestor", parents[2], "origin/main"]).returncode:
            return None
        clean = sh(["git", "-C", repo, "merge-tree", "--write-tree", parents[1], parents[2]])
        tree = sh(["git", "-C", repo, "rev-parse", f"{commit}^{{tree}}"]).stdout.strip()
        return parents[1] if clean.returncode == 0 and tree and clean.stdout.split()[:1] == [tree] else None

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
