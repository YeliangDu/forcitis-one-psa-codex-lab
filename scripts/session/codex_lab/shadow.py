"""Classify supplied Lab evidence; never invoke residents or remote services."""
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import uuid


REPOSITORY = "YeliangDu/forcitis-one-psa-codex-lab"
EFFECTS = dict(sends=0, starts=0, merges=0, schedules=0, remote_writes=0)


def _result(status, decisions):
    return dict(status=status, decisions=decisions, effects=dict(EFFECTS))


def _absolute(value):
    if not isinstance(value, str) or not value or ".." in value.split("/"):
        raise ValueError("invalid-path")
    path = Path(value)
    if not path.is_absolute():
        raise ValueError("relative-path")
    return path


def _directory(parts, parent=None):
    """Walk without following links and keep the resulting directory pinned."""
    fd = os.open("/", os.O_RDONLY | os.O_DIRECTORY) if parent is None else os.dup(parent)
    try:
        for part in parts:
            child = os.open(part, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=fd)
            os.close(fd)
            fd = child
        return fd
    except BaseException:
        os.close(fd)
        raise


def _worktree(value, root, root_fd):
    relative = _absolute(value).relative_to(root)
    fd = _directory(relative.parts, root_fd)
    os.close(fd)
    return str(root / relative)


def _sha(value):
    return isinstance(value, str) and re.fullmatch(r"[0-9a-f]{40}", value) is not None


def _key(record):
    identity = {k: v for k, v in record.items() if k != "status"}
    return hashlib.sha256(json.dumps(identity, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def _regular(fd):
    info = os.fstat(fd)
    if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1:
        raise ValueError("unsafe-state-file")


def _read_state(parent, name):
    try:
        fd = os.open(name, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=parent)
    except FileNotFoundError:
        return {"assignments": {}}
    try:
        _regular(fd)
        with os.fdopen(fd, "r", closefd=False) as stream:
            state = json.load(stream)
    finally:
        os.close(fd)
    if not isinstance(state, dict) or set(state) != {"assignments"} or not isinstance(state["assignments"], dict):
        raise ValueError("invalid-state")
    lanes = set()
    numbers = set()
    for key, record in state["assignments"].items():
        fields = {"number", "role", "resident_id", "actual_cwd", "base_sha", "head_sha", "status"}
        if not isinstance(record, dict) or set(record) != fields:
            raise ValueError("invalid-assignment")
        if (record["status"] != "pending" or type(record["number"]) is not int
                or record["number"] <= 0 or not _sha(record["base_sha"]) or not _sha(record["head_sha"])
                or any(not isinstance(record[k], str) or not record[k] for k in ("role", "resident_id", "actual_cwd"))
                or key != _key(record) or record["role"] in lanes or record["number"] in numbers):
            raise ValueError("invalid-assignment")
        _absolute(record["actual_cwd"])
        lanes.add(record["role"])
        numbers.add(record["number"])
    return state


def _save_state(parent, name, state):
    temporary = "." + name + "." + uuid.uuid4().hex
    fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600, dir_fd=parent)
    try:
        with os.fdopen(fd, "w") as stream:
            json.dump(state, stream, sort_keys=True, indent=2)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, name, src_dir_fd=parent, dst_dir_fd=parent)
        os.fsync(parent)
    finally:
        try:
            os.unlink(temporary, dir_fd=parent)
        except FileNotFoundError:
            pass


def _eligibility(card, snapshot, config, root, root_fd):
    if type(card.get("number")) is not int or card["number"] <= 0:
        return "invalid-card"
    if "hold_reason" not in card or card["hold_reason"] is not None:
        return "explicit-or-missing-hold"
    if not isinstance(card.get("role"), str) or not card["role"]:
        return "invalid-role"
    resident = config["residents"].get(card.get("role"))
    if not isinstance(resident, dict) or resident.get("available") is not True:
        return "resident-unavailable"
    if not isinstance(resident.get("id"), str) or not resident["id"] or card.get("resident_id") != resident["id"]:
        return "resident-id-mismatch"
    try:
        expected = _worktree(resident.get("worktree"), root, root_fd)
        actual = _worktree(card.get("actual_cwd"), root, root_fd)
        if expected != actual:
            return "cwd-mismatch"
    except (OSError, ValueError, TypeError):
        return "unverified-worktree"
    if not _sha(snapshot.get("base_sha")) or card.get("base_sha") != snapshot["base_sha"]:
        return "stale-or-missing-base"
    head = card.get("head_sha")
    if not _sha(head) or card.get("ci_head") != head or card.get("ci_ok") is not True:
        return "missing-or-stale-ci"
    if card.get("review_head") != head or card.get("review_ok") is not True:
        return "missing-or-stale-review"
    return None


def _classify(config, snapshot, state, root, root_fd):
    decisions = []
    changed = False
    assignments = state["assignments"]
    for card in snapshot["cards"]:
        row = dict(number=card.get("number"), action="hold", reason="invalid-card")
        reason = _eligibility(card, snapshot, config, root, root_fd)
        if reason:
            row["reason"] = reason
        else:
            record = {k: card[k] for k in ("number", "role", "resident_id", "actual_cwd", "base_sha", "head_sha")}
            record["actual_cwd"] = str(_absolute(record["actual_cwd"]))
            record["status"] = "pending"
            key = _key(record)
            pending = [r for r in assignments.values() if r["role"] == card["role"] or r["number"] == card["number"]]
            if key in assignments:
                row.update(action="skip", reason="already-pending", assignment_key=key)
            elif pending:
                row["reason"] = "pending-lane-or-changed-evidence"
            else:
                assignments[key] = record
                changed = True
                row.update(action="would-dispatch", reason="exact-head-proof", assignment_key=key)
        decisions.append(row)
    return decisions, changed


def run_tick(config, snapshot, state_path):
    """Return shadow decisions, persisting only deduplicated pending assignments."""
    root_fd = parent_fd = lock_fd = None
    cards = []
    try:
        if (not isinstance(config, dict) or not isinstance(snapshot, dict)
                or config.get("repo") != REPOSITORY or snapshot.get("repo") != REPOSITORY):
            return _result("rejected", [])
        root = _absolute(config.get("root"))
        if _absolute(snapshot.get("root")) != root:
            return _result("rejected", [])
        root_fd = _directory(root.parts[1:])
        relative = _absolute(state_path).relative_to(root)
        if not relative.parts:
            return _result("rejected", [])
        parent_fd = _directory(relative.parts[:-1], root_fd)
        name = relative.name
        # Validate existing state and lock paths before creating/opening either.
        for candidate in (name, name + ".lock"):
            try:
                info = os.stat(candidate, dir_fd=parent_fd, follow_symlinks=False)
            except FileNotFoundError:
                continue
            if not stat.S_ISREG(info.st_mode) or info.st_nlink != 1:
                return _result("rejected", [])
        cards = snapshot.get("cards")
        if not isinstance(cards, list) or not all(isinstance(c, dict) for c in cards):
            return _result("rejected", [])
        if not isinstance(config.get("residents"), dict):
            return _result("ok", [dict(number=c.get("number"), action="hold", reason="missing-roster") for c in cards])
        lock_fd = os.open(name + ".lock", os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW | os.O_NONBLOCK, 0o600, dir_fd=parent_fd)
        _regular(lock_fd)
        fcntl.flock(lock_fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        try:
            state = _read_state(parent_fd, name)
            for record in state["assignments"].values():
                _worktree(record["actual_cwd"], root, root_fd)
            decisions, changed = _classify(config, snapshot, state, root, root_fd)
            if changed:
                _save_state(parent_fd, name, state)
            return _result("ok", decisions)
        except (OSError, ValueError, TypeError, KeyError):
            return _result("ok", [dict(number=c.get("number"), action="hold", reason="invalid-or-unavailable-state") for c in cards])
    except (OSError, ValueError, TypeError):
        if cards:
            return _result("ok", [dict(number=c.get("number"), action="hold", reason="state-locked-or-unavailable") for c in cards])
        return _result("rejected", [])
    finally:
        for fd in (lock_fd, parent_fd, root_fd):
            if fd is not None:
                os.close(fd)
