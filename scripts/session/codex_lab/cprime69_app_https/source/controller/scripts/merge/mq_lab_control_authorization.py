"""Read-only explicit upgrade record correspondence, never write authority.

The caller independently selects the private directory and verified expected
subject/proof/reviews. Matching stored assertions cannot authenticate API facts,
review independence, workflow isolation or control dependency discovery. Approved
returns only unconsumed record data; there is no consume, journal, reserve, send,
normal/bootstrap fallback, network, repair or production binding in this module.
"""
import json
import os
from pathlib import Path
import re
import stat
import uuid


REPOSITORY = "YeliangDu/forcitis-one-psa-codex-lab"
REPOSITORY_ID = 1449415666
FIELDS = {"version", "kind", "upgrade_id", "repository", "repository_id", "number", "base", "head", "target",
          "old_manifest", "new_manifest", "author", "proof", "reviews"}
PROOF = {"source_commit", "workflow_path", "workflow_blob", "run_id", "attempt", "artifact_id", "sha256"}
KINDS = {"首轮", "终审"}
REVIEW = {"id", "reviewer", "head", "verdict"}
RESIDENT = re.compile(r"one-(?:lead|design|platform|test|dev-[a-z][a-z0-9-]*)")
LIMIT = 16 * 1024


def _require(value, reason):
    if not value:
        raise ValueError(reason)


def _shape(value, fields):
    _require(type(value) is dict and set(value) == fields, "missing-or-unknown-upgrade-fields")


def _positive(value):
    return type(value) is int and value > 0


def _sha(value, length=40):
    return type(value) is str and re.fullmatch(r"[0-9a-f]{" + str(length) + r"}", value) is not None


def _uuid(value):
    _require(type(value) is str and re.fullmatch(r"[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}", value),
             "upgrade-id-must-be-canonical-UUID")
    parsed = uuid.UUID(value)
    _require(str(parsed) == value and parsed.variant == uuid.RFC_4122 and parsed.version in range(1, 9),
             "invalid-explicit-upgrade-UUID")


def _validate(value):
    _shape(value, FIELDS)
    _uuid(value["upgrade_id"])  # Must pass before any identity-based path selection.
    _require(type(value["version"]) is int and value["version"] == 1
             and type(value["kind"]) is str and value["kind"] == "control-upgrade"
             and type(value["repository"]) is str and value["repository"] == REPOSITORY
             and type(value["repository_id"]) is int and value["repository_id"] == REPOSITORY_ID
             and _positive(value["number"]) and _sha(value["base"]) and _sha(value["head"])
             and type(value["target"]) is str and value["target"] == "main", "invalid-fixed-private-upgrade-subject")
    _require(_sha(value["old_manifest"], 64) and _sha(value["new_manifest"], 64)
             and value["old_manifest"] != value["new_manifest"], "invalid-or-identical-control-manifest-transition")
    _require(type(value["author"]) is str and RESIDENT.fullmatch(value["author"]) is not None, "invalid-Codex-author-role")
    proof = value["proof"]
    _shape(proof, PROOF)
    _require(_sha(proof["source_commit"]) and proof["source_commit"] != value["head"]
             and _sha(proof["workflow_blob"]) and _sha(proof["sha256"], 64)
             and all(_positive(proof[k]) for k in ("run_id", "attempt", "artifact_id")), "invalid-independent-proof-identity")
    path = proof["workflow_path"]
    _require(type(path) is str and re.fullmatch(r"\.github/workflows/[A-Za-z0-9_-][A-Za-z0-9._-]*\.ya?ml", path),
             "unsafe-independent-workflow-path")
    reviews = value["reviews"]
    _shape(reviews, KINDS)
    owners, ids = set(), set()
    for kind in ("首轮", "终审"):
        review = reviews[kind]
        _shape(review, REVIEW)
        _require(_positive(review["id"]) and review["id"] not in ids
                 and _sha(review["head"]) and review["head"] == value["head"]
                 and type(review["verdict"]) is str and review["verdict"] == "pass", "missing-independent-current-passing-review")
        prefix = "codex-" + kind + "-"
        reviewer = review["reviewer"]
        _require(type(reviewer) is str and reviewer.startswith(prefix), "reviewer-model-or-kind-mismatch")
        owner = reviewer[len(prefix):]
        _require(RESIDENT.fullmatch(owner) is not None and owner != value["author"] and owner not in owners,
                 "self-review-or-shared-review-session")
        owners.add(owner)
        ids.add(review["id"])


def _open_directory(directory):
    raw = os.fspath(directory)
    _require(type(raw) is str and bool(raw) and ".." not in raw.split("/"), "invalid-upgrade-directory-path")
    path = Path(raw)
    _require(path.is_absolute() and str(path) == raw, "upgrade-directory-must-be-canonical-absolute")
    flags = os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW | getattr(os, "O_CLOEXEC", 0)
    fd = os.open("/", flags)
    try:
        for part in path.parts[1:]:
            child = os.open(part, flags, dir_fd=fd)
            os.close(fd)
            fd = child
        _private_directory(os.fstat(fd))
        return path, fd
    except BaseException:
        os.close(fd)
        raise


def _private_directory(info):
    _require(stat.S_ISDIR(info.st_mode) and info.st_uid == os.geteuid() and stat.S_IMODE(info.st_mode) == 0o700,
             "upgrade-directory-must-be-owned-private-0700")


def _private_file(info):
    _require(stat.S_ISREG(info.st_mode) and info.st_uid == os.geteuid() and stat.S_IMODE(info.st_mode) == 0o600
             and info.st_nlink == 1 and 0 < info.st_size <= LIMIT, "unsafe-or-oversize-upgrade-file")


def _file_identity(info):
    return (info.st_dev, info.st_ino, info.st_mode, info.st_uid, info.st_nlink,
            info.st_size, info.st_mtime_ns, info.st_ctime_ns)


def _directory_identity(info):
    return info.st_dev, info.st_ino, info.st_mode, info.st_uid


def _unique(pairs):
    result = {}
    for key, value in pairs:
        _require(key not in result, "duplicate-upgrade-JSON-key")
        result[key] = value
    return result


def _constant(_value):
    raise ValueError("nonfinite-upgrade-JSON")


def load_upgrade_approval(directory, expected):
    """Read only upgrades/<UUID>.json; return no approval on missing/bad proof."""
    root = upgrades = None
    try:
        _validate(expected)  # Hard invariants apply even to matching bad inputs.
        path, root = _open_directory(directory)
        root_info = os.fstat(root)
        upgrades = os.open("upgrades", os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW
                           | getattr(os, "O_CLOEXEC", 0), dir_fd=root)
        upgrades_info = os.fstat(upgrades)
        _private_directory(upgrades_info)
        name = expected["upgrade_id"] + ".json"
        fd = os.open(name, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK
                     | getattr(os, "O_CLOEXEC", 0), dir_fd=upgrades)
        with os.fdopen(fd, "rb") as stream:
            before = os.fstat(stream.fileno())
            _private_file(before)
            raw = stream.read(LIMIT + 1)
            _require(len(raw) <= LIMIT, "upgrade-file-read-bound")
            after = os.fstat(stream.fileno())
            _private_file(after)
            _require(_file_identity(before) == _file_identity(after), "upgrade-file-changed-during-read")
        record = json.loads(raw.decode("utf-8"), object_pairs_hook=_unique, parse_constant=_constant)
        _validate(record)
        _require(record == expected, "upgrade-record-does-not-match-independently-frozen-binding")
        named = os.stat(name, dir_fd=upgrades, follow_symlinks=False)
        _private_file(named)
        _require(_file_identity(named) == _file_identity(after), "upgrade-file-inode-or-content-replaced")
        named_upgrades = os.stat("upgrades", dir_fd=root, follow_symlinks=False)
        _private_directory(named_upgrades)
        _require(_directory_identity(named_upgrades) == _directory_identity(upgrades_info), "upgrade-subdirectory-replaced")
        _path, fresh = _open_directory(path)
        try:
            _require(_directory_identity(os.fstat(fresh)) == _directory_identity(root_info), "upgrade-root-directory-replaced")
        finally:
            os.close(fresh)
        return dict(action="approved", reason="explicit-private-upgrade-record-matches-frozen-binding", approval=record)
    except FileNotFoundError:
        return dict(action="hold", reason="explicit-upgrade-approval-not-installed")
    except ValueError as error:
        return dict(action="hold", reason=str(error) or "invalid-explicit-upgrade-record")
    except Exception:
        return dict(action="hold", reason="upgrade-record-unreadable-or-unsafe")
    finally:
        for fd in (upgrades, root):
            if fd is not None:
                os.close(fd)
