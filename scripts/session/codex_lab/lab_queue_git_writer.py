"""Disposable-only exact-base Git writer over trusted injected argv/GET ports.

No default transport, queue or production adapter is provided. Callers must
hold the shared release lock and persist a reservation before calling send.
Only independent GitHub merged/parent/ancestry readback confirms an effect.
"""
import re


REPOSITORY = "YeliangDu/forcitis-one-psa-codex-lab"
IDENTITY = {"number", "base", "head", "target"}
ORIGINS = {
    prefix + REPOSITORY + suffix
    for prefix in ("git@github.com:", "https://github.com/", "ssh://git@github.com/")
    for suffix in ("", ".git")
}


def _require(condition, reason):
    if not condition:
        raise ValueError(reason)


def _sha(value):
    return isinstance(value, str) and re.fullmatch(r"[0-9a-f]{40}", value) is not None


def _intent(value):
    _require(isinstance(value, dict) and set(value) == IDENTITY, "Invalid intent shape")
    _require(type(value["number"]) is int and value["number"] > 0
             and _sha(value["base"]) and _sha(value["head"])
             and isinstance(value["target"], str)
             and re.fullmatch(r"lab/disposable-[a-z0-9-]+", value["target"]) is not None,
             "Only exact disposable release intents are allowed")
    return dict(value)


def _repository(value):
    _require(isinstance(value, dict) and value.get("full_name") == REPOSITORY
             and value.get("private") is True and type(value.get("id")) is int
             and value["id"] > 0, "Not the private Lab repository")
    return value["id"]


def _pr(value, intent, repo_id, merged):
    _require(isinstance(value, dict) and type(value.get("number")) is int
             and value["number"] == intent["number"], "Wrong PR readback")
    for branch in ("head", "base"):
        row = value.get(branch)
        _require(isinstance(row, dict) and row.get("sha") == intent[branch], "Changed PR head/base")
        repo = row.get("repo")
        _require(isinstance(repo, dict) and type(repo.get("id")) is int
                 and repo["id"] == repo_id and repo.get("full_name") == REPOSITORY,
                 "Foreign PR branch")
    _require(value["base"].get("ref") == intent["target"], "Wrong disposable target")
    _require(value.get("merged") is merged and value.get("draft") is False
             and value.get("state") == ("closed" if merged else "open"), "Wrong PR state")


class GitDisposableWriter:
    def __init__(self, git, read):
        _require(callable(git) and callable(read), "Expected injected argv and relative GET ports")
        self._git = git
        self._read = read

    def _origin(self):
        output = self._git(["remote", "-v"])
        _require(isinstance(output, str), "Malformed remote listing")
        lines = output.splitlines()
        _require(len(lines) == 2, "Expected sole personal origin fetch and push")
        found = {}
        for line in lines:
            parts = line.split()
            _require(len(parts) == 3 and parts[0] == "origin" and parts[1] in ORIGINS
                     and parts[2] in ("(fetch)", "(push)") and parts[2] not in found,
                     "Foreign or ambiguous remote")
            found[parts[2]] = parts[1]
        _require(set(found) == {"(fetch)", "(push)"}, "Missing origin direction")

    def _git_sha(self, args):
        output = self._git(args)
        _require(isinstance(output, str) and _sha(output.strip()), "Malformed Git SHA output")
        return output.strip()

    def send(self, intent):
        intent = _intent(intent)  # Before any port I/O, including remote reads.
        ref = "refs/heads/" + intent["target"]
        self._origin()
        repo_id = _repository(self._read(""))
        _pr(self._read(f"pulls/{intent['number']}"), intent, repo_id, merged=False)
        base, head = intent["base"], intent["head"]
        tree = self._git_sha(["merge-tree", "--write-tree", base, head])
        merge = self._git_sha(["commit-tree", tree, "-p", base, "-p", head,
                               "-m", f"Lab disposable release #{intent['number']}"])
        parents = self._git(["show", "-s", "--format=%P", merge])
        _require(isinstance(parents, str) and parents.split() == [base, head], "Wrong merge parents")
        # Nonzero must raise through the argv port; first-parent verification
        # plus this ancestry check establishes a fast-forward from pinned base.
        self._git(["merge-base", "--is-ancestor", base, merge])
        # Recheck both destinations and the genuine PR at the send boundary.
        # A concurrent base update is additionally rejected by the exact lease.
        self._origin()
        _require(_repository(self._read("")) == repo_id, "Repository identity moved")
        _pr(self._read(f"pulls/{intent['number']}"), intent, repo_id, merged=False)
        _intent(intent)
        self._git(["push", "--porcelain", f"--force-with-lease={ref}:{base}",
                   "origin", f"{merge}:{ref}"])
        return dict(merge_sha=merge)  # Request result only, never confirmation.

    def readback(self, intent):
        intent = _intent(intent)
        try:
            repo_id = _repository(self._read(""))
            pr = self._read(f"pulls/{intent['number']}")
            _pr(pr, intent, repo_id, merged=True)
            merge = pr.get("merge_commit_sha")
            _require(_sha(merge), "Missing GitHub merge SHA")
            commit = self._read(f"commits/{merge}")
            _require(isinstance(commit, dict) and commit.get("sha") == merge
                     and isinstance(commit.get("parents"), list) and len(commit["parents"]) == 2,
                     "Malformed merge commit")
            _require(all(isinstance(p, dict) for p in commit["parents"])
                     and [p.get("sha") for p in commit["parents"]] == [intent["base"], intent["head"]],
                     "Inexact merge parents")
            target = self._read("git/ref/heads/" + intent["target"])
            _require(isinstance(target, dict) and target.get("ref") == "refs/heads/" + intent["target"]
                     and isinstance(target.get("object"), dict)
                     and _sha(target["object"].get("sha"))
                     and target["object"].get("type", "commit") == "commit", "Invalid target readback")
            tip = target["object"]["sha"]
            if tip != merge:
                comparison = self._read(f"compare/{merge}...{tip}")
                _require(isinstance(comparison, dict) and comparison.get("status") == "ahead"
                         and isinstance(comparison.get("merge_base_commit"), dict)
                         and comparison["merge_base_commit"].get("sha") == merge,
                         "Target does not contain the exact merge")
            return dict(intent, merged=True, merge_sha=merge, target_contains_merge=True)
        except (ValueError, TypeError, KeyError):
            return None
