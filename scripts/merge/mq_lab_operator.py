"""One explicit Lab operator command; no background or legacy writer fallback."""
from contextlib import contextmanager
import importlib.util
import json
import os
from pathlib import Path
import re
import stat

import issue_gate
from mq_lab import _path, _state
from mq_lab_active import admit, run_once
from mq_lab_authorization import load_approval
from mq_lab_journal import ReleaseJournal
from mq_lab_proof import verify_intent
from mq_lab_trust import bootstrap_check, trusted_source
import post_review


ROOT = Path(__file__).resolve().parents[2]
REPOSITORY = "YeliangDu/forcitis-one-psa-codex-lab"
IDENTITY = ("number", "head", "base", "target")


def _require(value, reason):
    if not value:
        raise ValueError(reason)


def _exact(proof, intent):
    return (isinstance(proof, dict) and proof.get("action") == "eligible"
            and type(proof.get("number")) is int
            and all(proof.get(k) == v for k, v in intent.items())
            and isinstance(proof.get("reason"), str) and bool(proof["reason"].strip())
            and isinstance(proof.get("release_key"), str) and bool(proof["release_key"].strip()))


def _intent(approval, number):
    _require(isinstance(approval, dict) and type(approval.get("number")) is int
             and approval["number"] == number and approval.get("target") == "main",
             "missing-or-wrong-local-approval")
    for field in ("head", "base"):
        _require(isinstance(approval.get(field), str) and re.fullmatch(r"[0-9a-f]{40}", approval[field]),
                 "invalid-approved-head-or-base")
    return {k: approval[k] for k in IDENTITY}


class _RequestedQueue:
    def __init__(self, queue, number):
        self.queue, self.number = queue, number

    def __getattr__(self, name):
        return getattr(self.queue, name)

    @contextmanager
    def merge_lock(self):
        with self.queue.merge_lock():
            present, numbers = _state(Path(self.queue.path))
            _require(present and numbers and numbers[0] == self.number, "requested-pr-is-not-fifo-head")
            _require(not self.queue.deps(self.number), "dependency-resolution-not-bound")
            yield


class _LazyJournal:
    def __init__(self, path):
        self.path, self.journal = path, None

    def __getattr__(self, name):
        if name not in ("load", "reserve", "confirm"):
            raise AttributeError(name)
        if self.journal is None:
            self.journal = ReleaseJournal(self.path)
        return getattr(self.journal, name)


class _LazyWriter:
    def __init__(self, ports, authorize):
        self.ports, self.authorize, self.writer = ports, authorize, None

    def _get(self):
        if self.writer is None:
            self.writer = self.ports.writer(self.authorize)
        return self.writer

    def send(self, intent):
        _require(self.authorize(dict(intent)) is True, "write-boundary-proof-held")
        return self._get().send(dict(intent))

    def readback(self, intent):
        return self._get().readback(dict(intent))


def run_command(command, number, common_dir, ports):
    """Plan without state creation; admit/once use the one canonical lock."""
    try:
        _require(command in ("plan", "enqueue", "once") and type(number) is int and number > 0,
                 "unsupported-command-or-pr")
        path = _path(common_dir)
        for suffix in ("", ".lock", ".merge-lock", ".heads", ".heads-lock"):
            try:
                info = Path(str(path) + suffix).lstat()
            except FileNotFoundError:
                continue
            _require(stat.S_ISREG(info.st_mode) and info.st_nlink == 1 and info.st_uid == os.geteuid(),
                     "unsafe-canonical-queue-state")
        present, numbers = _state(path)
        _require(present, "canonical-queue-missing")
        if command == "once":
            _require(numbers and numbers[0] == number, "requested-pr-is-not-fifo-head")
        journal_path = path.parent / "releases.sqlite3"
        last_key = [None]

        def verify(intent):
            approval = ports.load(number)
            approved = _intent(approval, number)
            _require(all(approved.get(k) == v for k, v in intent.items()), "approval-moved")
            proof = ports.verify(dict(intent), approval)
            _require(_exact(proof, approved), "live-proof-held-or-inexact")
            last_key[0] = proof["release_key"]
            return proof

        if command == "plan":
            approval = ports.load(number)
            intent = _intent(approval, number)
            proof = ports.verify(dict(intent), approval)
            _require(_exact(proof, intent), "live-proof-held-or-inexact")
            return proof
        from mq_queue import Queue
        if command == "enqueue":
            intent = _intent(ports.load(number), number)
            return admit(Queue(str(path)), intent, verify)
        # No new database is created solely because an unapproved PR was named.
        if not journal_path.exists() and ports.load(number) is None:
            return dict(action="hold", reason="missing-approval-and-no-recovery-journal")

        def authorize(intent):
            try:
                expected_key = last_key[0]
                proof = verify(dict(intent))
                return expected_key is not None and proof["release_key"] == expected_key
            except Exception:
                return False

        queue = _RequestedQueue(Queue(str(path)), number)
        return run_once(queue, _LazyJournal(journal_path), verify,
                        _LazyWriter(ports, authorize), allow_write=True)
    except Exception:
        return dict(action="hold", reason="operator-command-held-or-unavailable")


def _module(name):
    path = ROOT / "scripts/session/codex_lab" / (name + ".py")
    spec = importlib.util.spec_from_file_location("mq_operator_" + name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class ProductionPorts:
    """Fixed live ports, constructed only after mq_lab's physical Lab checks.

    No approval is created here. An explicit once command still requires that
    exact independently installed local approval and all current live proof.
    """
    def __init__(self, common_dir, sh):
        self.directory = _path(common_dir).parent
        self.sh = sh
        self.github = _module("lab_queue_live").GithubRead()
        self.writer_type = _module("lab_queue_git_writer").GitLabMainWriter

    def git(self, args):
        result = self.sh(["git", *args])
        _require(result.returncode == 0, "fixed-Lab-git-command-failed")
        return result.stdout

    def load(self, number):
        return load_approval(self.directory, number)

    def _issue_run(self, args):
        # Adapt only issue_gate's fixed read vocabulary to scoped API ports.
        scope = ["-R", REPOSITORY]
        if len(args) != 7 or args[3:5] != scope or args[5] != "--json":
            return 1, ""
        number = int(args[2])
        _require(number > 0 and args[1] == "view", "invalid-issue-gate-read")
        if args[0] == "pr" and args[6] == "body,title":
            row = self.github.pull(number)
        elif args[0] == "pr" and args[6] == "closingIssuesReferences":
            row = self.github.closing_issues(number)
        elif args[0] == "issue" and args[6] == "state,labels,title,body":
            row = dict(self.github.issue(number))
            _require(row.get("state") in ("open", "closed"), "invalid-issue-state")
            row["state"] = row["state"].upper()
        else:
            return 1, ""
        return 0, json.dumps(row)

    def _provenance(self, pr, approval, files):
        registry = self.directory.parent.parent / ".codex-lab/session-copy/runtime/roles.json"
        _require(registry.resolve(strict=True) == registry, "unsafe-resident-registry-path")
        fd = os.open(registry, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
        with os.fdopen(fd, "rb") as stream:
            info = os.fstat(stream.fileno())
            _require(stat.S_ISREG(info.st_mode) and info.st_uid == os.geteuid()
                     and info.st_nlink == 1 and info.st_size <= 65536, "unsafe-resident-registry")
            raw = stream.read(65537)
        _require(len(raw) <= 65536, "resident-registry-too-large")
        roles = json.loads(raw)["roles"]
        _require(isinstance(roles, list) and all(isinstance(r, dict) for r in roles), "invalid-resident-registry")
        identities = {}
        for row in roles:
            _require(isinstance(row.get("role"), str) and row["role"] not in identities
                     and isinstance(row.get("id"), str)
                     and re.fullmatch(r"[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}", row["id"]), "invalid-resident-identity")
            identities[row["role"]] = row["id"]
        _require(len(set(identities.values())) == len(identities), "shared-resident-session")
        _require(all(name.split("-", 2)[2] in identities for name in approval["reviewers"].values()),
                 "unregistered-reviewer-session")
        for label, role in (("Codex", approval["author"]),):
            _require(role in identities and re.search(r"^" + label + r":\s*" + re.escape(identities[role]) + r"\s*$",
                                                     pr.get("body", ""), re.M), "missing-current-Codex-provenance")
        if any(f["filename"].startswith("backend/src/test/java/com/forcitis/one/acceptance/") for f in files):
            _require("one-test" in identities and re.search(r"^Codex-Acceptance:\s*" + re.escape(identities["one-test"]) + r"\s*$",
                                                            pr.get("body", ""), re.M), "missing-Codex-acceptance-provenance")

    def verify(self, intent, approval):
        number = intent["number"]
        _require(issue_gate.unscheduled(number, self._issue_run, ["-R", REPOSITORY]) is None,
                 "issue-not-exactly-scoped-and-scheduled")
        pr = self.github.pull(number)
        labels = pr.get("labels")
        _require(isinstance(labels, list) and all(isinstance(x, dict) and isinstance(x.get("name"), str) for x in labels),
                 "missing-pr-label-proof")
        _require(not {"hold", "挂起"} & {x["name"] for x in labels}, "pr-held")
        files = self.github.files(number)
        _require(bool(files), "missing-pr-file-proof")
        policy = json.loads((ROOT / "policy.json").read_text())
        paths = policy["review"]["final_paths"]
        _require(isinstance(paths, list) and paths and all(isinstance(p, str) and p for p in paths), "invalid-guard-path-policy")
        guarded = False
        for row in files:
            names = [row.get("filename")]
            if row.get("previous_filename") is not None:
                names.append(row["previous_filename"])
            for name in names:
                _require(isinstance(name, str) and name and not name.startswith("/") and "\\" not in name
                         and not any(ord(c) < 32 for c in name) and all(p not in ("", ".", "..") for p in name.split("/")),
                         "unsafe-pr-path")
                guarded |= name.startswith(tuple(paths))
        _require(not guarded or {"首轮", "终审"} <= set(approval["required_reviews"]), "missing-guarded-final-review")
        self._provenance(pr, approval, files)
        self._review_ids(self.github.reviews(number), approval)
        owner = self

        class CheckedGithub:
            def __getattr__(self, name):
                return getattr(owner.github, name)

            def pull(self, current_number):
                row = owner.github.pull(current_number)
                _require(row.get("body") == pr.get("body") and row.get("title") == pr.get("title")
                         and row.get("labels") == pr.get("labels"), "pr-metadata-moved-during-proof")
                return row

            def reviews(self, current_number):
                rows = owner.github.reviews(current_number)
                owner._review_ids(rows, approval)
                return rows

        checked = CheckedGithub()
        return verify_intent(intent, approval, checked,
                             lambda i: trusted_source(i, approval["mode"], self.git),
                             lambda i, a: bootstrap_check(i, a, checked))

    @staticmethod
    def _review_ids(reviews, approval):
        _require(isinstance(reviews, list) and all(isinstance(row, dict) for row in reviews), "malformed-review-records")
        fields = post_review.load_policy(ROOT / "policy.json")[0]["header_fields"]
        for kind in approval["required_reviews"]:
            matching = []
            for row in reviews:
                header = post_review.parse_header(row.get("body"), fields)
                if (header and header["kind"] == kind and header["reviewer"] == approval["reviewers"][kind]
                        and header["commit"] == approval["head"] and row.get("commit_id") == approval["head"]):
                    matching.append((row, header))
            _require(bool(matching), "missing-assigned-review")
            row, header = matching[-1]
            _require(type(row.get("id")) is int and row["id"] == approval["review_ids"][kind]
                     and row.get("state") in ("COMMENTED", "APPROVED") and header["verdict"] == post_review.PASS,
                     "approved-review-id-stale-or-dismissed")

    def writer(self, authorize):
        return self.writer_type(self.git, self.github._single, authorize)
