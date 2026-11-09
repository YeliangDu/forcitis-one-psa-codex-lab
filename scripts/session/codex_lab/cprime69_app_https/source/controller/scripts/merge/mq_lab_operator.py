"""One explicit Lab operator command; no background or legacy writer fallback."""
from contextlib import contextmanager, ExitStack
import importlib.util
import json
import fcntl
import hashlib
import os
from pathlib import Path
import re
import stat

import issue_gate
from mq_lab import _path, _state
from mq_lab_active import admit, run_once
from mq_lab_runtime import _valid_record, _matches, exact_release_health
from mq_lab_authorization import load_approval
from mq_lab_journal_v2 import ReleaseJournalV2, _schema
from mq_lab_proof import verify_intent
from mq_lab_trust import bootstrap_check, trusted_source
import post_review


ROOT = Path(__file__).resolve().parents[2]
REPOSITORY = "YeliangDu/forcitis-one-psa-codex-lab"
IDENTITY = ("number", "head", "base", "target")


def _require(value, reason):
    if not value:
        raise ValueError(reason)


def _filename(value):
    _require(isinstance(value, str) and bool(value) and not value.startswith("/")
             and "\\" not in value and not any(ord(c) < 32 or 127 <= ord(c) <= 159
                                                or c in "\u2028\u2029" for c in value)
             and all(part not in ("", ".", "..") for part in value.split("/")), "unsafe-pr-filename")
    return value


def final_readback_count(pr, observed_pr):
    """Missing/loosely typed counts cannot certify the final file snapshot."""
    if not isinstance(pr, dict) or not isinstance(observed_pr, dict):
        return False
    before, after = pr.get("changed_files"), observed_pr.get("changed_files")
    return type(before) is int and before > 0 and type(after) is int and after == before


def complete_files(pr, files):
    """Require every declared changed file exactly once, including rename proof.

    The live port fetches all pages. Comparing their validated unique current
    filenames to PR.changed_files detects truncation, API caps and extra rows.
    """
    _require(isinstance(pr, dict) and type(pr.get("changed_files")) is int
             and pr["changed_files"] > 0, "missing-or-invalid-pr-file-count")
    _require(isinstance(files, list) and len(files) == pr["changed_files"], "incomplete-pr-file-list")
    seen, result = set(), []
    statuses = {"added", "removed", "modified", "renamed", "copied", "changed", "unchanged"}
    for row in files:
        _require(isinstance(row, dict), "malformed-pr-file-record")
        name = _filename(row.get("filename"))
        _require(name not in seen, "duplicate-pr-filename")
        _require(isinstance(row.get("status"), str) and row["status"] in statuses, "invalid-pr-file-status")
        seen.add(name)
        if row["status"] == "renamed":
            _filename(row.get("previous_filename"))
        elif "previous_filename" in row:
            _filename(row["previous_filename"])
        result.append(dict(row))
    return result


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
    def __init__(self, queue, number, scope=None):
        self.queue, self.number, self.scope = queue, number, scope

    def __getattr__(self, name):
        return getattr(self.queue, name)

    @contextmanager
    def merge_lock(self):
        lock_identity = self.scope["lock_ids"]["merge.queue.merge-lock"] if self.scope else None
        with self.queue.merge_lock(expected_identity=lock_identity):
            present, numbers = _state(Path(self.queue.path))
            _require(present and numbers and numbers[0] == self.number, "requested-pr-is-not-fifo-head")
            _require(not self.queue.deps(self.number), "dependency-resolution-not-bound")
            yield


class _LazyJournal:
    """Existing migrated storage only; readback never grants a new request."""
    def __init__(self, path):
        self.path, self.journal = path, None

    def _get(self):
        if self.journal is None:
            # V2 checks exact existing schema/private storage. Missing/V1 state
            # refuses: no initialize, migration, downgrade or legacy fallback.
            self.journal = ReleaseJournalV2(self.path)
        return self.journal

    def require_new_write_ready(self):
        # Called only by the once verifier/reserve within run_iteration's
        # canonical merge lock, shared with the protected installer.
        journal = self._get()
        with journal.storage.connection() as db:
            db.execute("BEGIN")
            try:
                _schema(db, 2)  # Includes complete release/binding correspondence.
                pending = db.execute("SELECT upgrade_id FROM upgrade_bindings WHERE phase <> 'adopted'").fetchall()
                _require(not pending, "protected-upgrade-not-durably-adopted")
            finally:
                db.rollback()

    def __getattr__(self, name):
        if name not in ("load", "confirm"):
            raise AttributeError(name)
        # Existing ordinary reservations recover without new approval/proof or
        # a pending-upgrade send gate. V2 itself refuses control-upgrade rows.
        return getattr(self._get(), name)

    def reserve(self, record):
        self.require_new_write_ready()
        return self._get().reserve(record)


class _LazyWriter:
    def __init__(self, ports, authorize, scope=None):
        self.ports, self.authorize, self.writer = ports, authorize, None
        self.scope = scope

    def _get(self):
        if self.writer is None:
            self.writer = self.ports.writer(self.authorize)
        return self.writer

    def send(self, intent):
        _require(self.authorize(dict(intent)) is True, "write-boundary-proof-held")
        return self._get().send(dict(intent))

    def readback(self, intent):
        proof = self._get().readback(dict(intent))
        if self.scope is not None and isinstance(proof, dict) and proof.get("merged") is True:
            merge = proof.get("merge_sha")
            _require(isinstance(merge, str) and re.fullmatch("[0-9a-f]{40}", merge), "first-card-merge-shape")
            commit = self.ports.github._single("commits/" + merge)
            _require(commit.get("sha") == merge and commit.get("commit", {}).get("tree", {}).get("sha")
                     == self.scope["expected_tree"], "first-card-readback-tree-mismatch")
        return proof


def _first_card_locks(path, scope):
    if scope is None:
        return
    for name, expected in scope["lock_ids"].items():
        entry = path.parent / name
        st = entry.lstat()
        _require(stat.S_ISREG(st.st_mode) and st.st_uid == os.geteuid() and st.st_nlink == 1
                 and not st.st_mode & 0o022 and [st.st_dev, st.st_ino] == expected, "first-card-lock-identity-moved")


def _cleanup_confirmed_orphan(number, common_dir, ports, scope=None):
    """Reconcile ONLY a requested absent-FIFO ordinary confirmed item.

    Never reads approval/eligibility or sends. Existing canonical locks and strict
    V2 state plus independent exact merge proof are required before any cleanup.
    """
    path = _path(common_dir)
    with ExitStack() as stack:
        held = []
        for suffix in (".merge-lock", ".lock", ".heads-lock"):
            lock = Path(str(path) + suffix)
            fd = os.open(lock, os.O_RDWR | os.O_NOFOLLOW | os.O_NONBLOCK)
            stack.callback(os.close, fd)
            info = os.fstat(fd)
            current = lock.lstat()
            _require(stat.S_ISREG(info.st_mode) and info.st_uid == os.geteuid() and info.st_nlink == 1
                     and not info.st_mode & 0o022
                     and (info.st_dev, info.st_ino) == (current.st_dev, current.st_ino), "unsafe-existing-cleanup-lock")
            fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
            stack.callback(fcntl.flock, fd, fcntl.LOCK_UN)
            held.append((lock, fd))
        _first_card_locks(path, scope)
        _require(_path(common_dir) == path, "cleanup-canonical-path-moved")

        def read(file):
            fd = os.open(file, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
            with os.fdopen(fd, "r", encoding="utf-8") as stream:
                before = os.fstat(stream.fileno())
                _require(stat.S_ISREG(before.st_mode) and before.st_uid == os.geteuid()
                         and before.st_nlink == 1 and not before.st_mode & 0o022
                         and before.st_size <= 1024 * 1024, "unsafe-cleanup-state-file")
                text = stream.read(1024 * 1024 + 1)
                identity = lambda value: (value.st_dev, value.st_ino, value.st_size, value.st_mtime_ns, value.st_ctime_ns)
                _require(len(text.encode("utf-8")) == before.st_size
                         and identity(before) == identity(os.fstat(stream.fileno())) == identity(Path(file).lstat()),
                         "cleanup-state-moved")
                return text

        fifo = read(path)
        present, numbers = _state(path)
        _require(present and number not in numbers, "cleanup-cannot-skip-queued-item")
        head_path = Path(str(path) + ".heads")
        raw_heads = read(head_path)
        _require(not raw_heads or raw_heads.endswith("\n"), "incomplete-head-stamp-file")
        heads = {}
        for line in raw_heads.splitlines():
            match = re.fullmatch(r"([1-9][0-9]*) ([0-9a-f]{40})", line)
            _require(match is not None and int(match[1]) not in heads, "duplicate-or-malformed-head-stamp")
            heads[int(match[1])] = match[2]
        _require(all(queued in heads for queued in numbers), "missing-following-admission-head")
        journal = _LazyJournal(path.parent / "releases.sqlite3")
        record = journal.load(number)  # Existing-only strict V2 ordinary adapter.
        _require(isinstance(record, dict) and record.get("status") == "confirmed"
                 and _valid_record(record, number, heads.get(number, record.get("head"))),
                 "orphan-not-exact-durable-ordinary-confirmation")
        intent = {field: record[field] for field in IDENTITY}
        if scope is not None:
            _require(intent == scope["subject"], "first-card-orphan-scope-mismatch")
        reader = _LazyWriter(ports, lambda _intent: False, scope)
        proof = reader.readback(dict(intent))
        _require(_matches(proof, intent) and proof.get("merged") is True
                 and proof.get("target_contains_merge") is True and proof.get("merge_sha") == record["merge_sha"],
                 "confirmed-orphan-merge-proof-not-exact")
        _require(exact_release_health(getattr(ports, "release_health", None), record["merge_sha"]),
                 "confirmed-orphan-needs-exact-current-main-health")
        _require(journal.load(number) == record and read(path) == fifo and read(head_path) == raw_heads
                 and _state(path) == (True, numbers), "confirmed-cleanup-snapshot-moved")
        for lock, fd in held:
            opened, current = os.fstat(fd), lock.lstat()
            _require(stat.S_ISREG(current.st_mode) and current.st_uid == os.geteuid()
                     and current.st_nlink == 1 and not current.st_mode & 0o022
                     and (opened.st_dev, opened.st_ino) == (current.st_dev, current.st_ino),
                     "cleanup-lock-name-moved-before-publication")
        _first_card_locks(path, scope)
        _require(_path(common_dir) == path, "cleanup-common-dir-moved-before-publication")
        if number in heads:
            from mq_queue import Queue
            # Both sidefile locks are already held; do not call drop_head or
            # Queue public readers here (they would reacquire the same locks).
            kept = [line for line in raw_heads.splitlines() if not line.startswith(str(number) + " ")]
            Queue(str(path))._store(".heads", kept)
            _require(read(head_path) == "".join(line + "\n" for line in kept)
                     and read(path) == fifo, "confirmed-orphan-cleanup-not-exact")
        return dict(action="skip", reason="confirmed-orphan-cleaned-with-readback-only", merge_sha=record["merge_sha"])


def run_command(command, number, common_dir, ports, *, first_card_scope=None, permit_check=None, recover_only=False):
    """Plan without state creation; admit/once use the one canonical lock."""
    try:
        _require(command in ("plan", "enqueue", "once") and type(number) is int and number > 0,
                 "unsupported-command-or-pr")
        if command == "once" and getattr(ports, "requires_first_card_scope", False):
            _require(isinstance(first_card_scope, dict) and first_card_scope["subject"]["number"] == number == 69
                     and callable(permit_check) and type(recover_only) is bool, "first-card-launcher-required")
        path = _path(common_dir)
        _first_card_locks(path, first_card_scope)
        if first_card_scope is not None:
            def check_send_permit():
                if recover_only or not callable(permit_check) or permit_check() is not True:
                    return False
                _first_card_locks(path, first_card_scope)
                from mq_queue import merge_transport_fds
                fds = merge_transport_fds(["git", "push"])
                if len(fds) != 1:
                    return False
                lease = os.fstat(fds[0])
                return [lease.st_dev, lease.st_ino] == first_card_scope["lock_ids"]["merge.queue.merge-lock"]
            ports._send_permit_check = check_send_permit
        for suffix in ("", ".lock", ".merge-lock", ".heads", ".heads-lock"):
            try:
                info = Path(str(path) + suffix).lstat()
            except FileNotFoundError:
                continue
            _require(stat.S_ISREG(info.st_mode) and info.st_nlink == 1 and info.st_uid == os.geteuid(),
                     "unsafe-canonical-queue-state")
        present, numbers = _state(path)
        _require(present, "canonical-queue-missing")
        if command == "once" and (not numbers or numbers[0] != number):
            return _cleanup_confirmed_orphan(number, common_dir, ports, first_card_scope)
        journal_path = path.parent / "releases.sqlite3"
        journal = _LazyJournal(journal_path)  # No storage I/O until used under the merge lock.
        last_key = [None]

        def verify(intent):
            _first_card_locks(path, first_card_scope)
            if command == "once":
                journal.require_new_write_ready()
            approval = ports.load(number)
            approved = _intent(approval, number)
            if first_card_scope is not None:
                _require(check_send_permit(), "first-card-send-permit-expired-or-moved")
                approval_digest = hashlib.sha256(json.dumps(approval, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode()).hexdigest()
                _require(approved == first_card_scope["subject"] and approval_digest == first_card_scope["ordinary_approval_sha256"],
                         "first-card-ordinary-approval-moved")
                tree = ports.git(["merge-tree", "--write-tree", approved["base"], approved["head"]])
                _require(tree.strip() == first_card_scope["expected_tree"], "first-card-expected-tree-mismatch")
            _require(all(approved.get(k) == v for k, v in intent.items()), "approval-moved")
            proof = ports.verify(dict(intent), approval)
            _require(_exact(proof, approved), "live-proof-held-or-inexact")
            _first_card_locks(path, first_card_scope)
            if first_card_scope is not None:
                _require(check_send_permit(), "first-card-send-permit-expired-after-proof")
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

        queue = _RequestedQueue(Queue(str(path)), number, first_card_scope)
        return run_once(queue, journal, verify,
                        _LazyWriter(ports, authorize, first_card_scope), allow_write=not recover_only,
                        healthy=getattr(ports, "release_health", None),
                        expected_intent=None if first_card_scope is None else first_card_scope["subject"])
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
    requires_first_card_scope = True

    def release_health(self, merge):
        # Current exact hosted push proof, re-read under the canonical lock.
        return _module("lab_main_health").check_main_health(self.github)

    def __init__(self, common_dir, sh):
        self.directory = _path(common_dir).parent
        self.sh = sh
        self.github = _module("lab_queue_live").GithubRead()
        self.writer_type = _module("lab_queue_git_writer").GitLabMainWriter

    def git(self, args):
        if args and args[0] == "push":
            check = getattr(self, "_send_permit_check", None)
            _require(callable(check) and check() is True, "first-card-permit-refused-at-git-send")
            https = getattr(self, "_isolated_https", None)
            if https is not None:
                from lab_https_transport import BoundCPrimeHTTPS
                _require(type(https) is BoundCPrimeHTTPS, "isolated-https-binding-required")
                return https(args, check)
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
        if any(name.startswith("backend/src/test/java/com/forcitis/one/acceptance/")
               for row in files for name in (row["filename"], row.get("previous_filename", row["filename"]))):
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
        files = complete_files(pr, self.github.files(number))
        policy = json.loads((ROOT / "policy.json").read_text())
        paths = policy["review"]["final_paths"]
        _require(isinstance(paths, list) and paths and all(isinstance(p, str) and p for p in paths), "invalid-guard-path-policy")
        guarded = False
        for row in files:
            names = [row.get("filename")]
            if row.get("previous_filename") is not None:
                names.append(row["previous_filename"])
            for name in names:
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
                _require(final_readback_count(pr, row), "pr-file-count-moved-or-missing-on-readback")
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
