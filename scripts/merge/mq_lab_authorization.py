"""Read a trusted local approval for one exact Lab release; never write it."""
import json
import os
from pathlib import Path
import re
import stat


INITIAL_BASE = "a8454ced28ab18fce37d495f21d0daae2fa6ce8d"
BOOTSTRAP_RUN = 42418209077
KINDS = {"首轮", "终审"}
FIELDS = {"number", "head", "base", "target", "repo_id", "required_reviews",
          "reviewers", "review_ids", "run_id", "run_attempt", "mode", "author"}
SIZE = 16 * 1024
RESIDENT = re.compile(r"one-(?:lead|design|platform|test|dev-[a-z][a-z0-9-]*)")
SHA = re.compile(r"[0-9a-f]{40}")


def _positive(value):
    return type(value) is int and value > 0


def _object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("duplicate approval key")
        result[key] = value
    return result


def _invalid_constant(_value):
    raise ValueError("invalid approval JSON constant")


def _check(value, number):
    if not isinstance(value, dict) or value.get("mode") not in ("normal", "bootstrap"):
        raise ValueError("invalid approval mode")
    bootstrap = value["mode"] == "bootstrap"
    expected = FIELDS | ({"baseline_run_id"} if bootstrap else set())
    if (set(value) != expected or not all(_positive(value[k]) for k in ("number", "repo_id", "run_id", "run_attempt"))
            or value["number"] != number or value["target"] != "main"
            or any(not isinstance(value[k], str) or not SHA.fullmatch(value[k]) for k in ("head", "base"))):
        raise ValueError("invalid approval identity")
    if bootstrap and (number != 15 or value["base"] != INITIAL_BASE or value["baseline_run_id"] != BOOTSTRAP_RUN):
        raise ValueError("invalid one-time bootstrap evidence")
    author, required = value["author"], value["required_reviews"]
    reviewers, ids = value["reviewers"], value["review_ids"]
    if (not isinstance(author, str) or not RESIDENT.fullmatch(author)
            or not isinstance(required, list) or not all(isinstance(k, str) and k in KINDS for k in required)
            or "首轮" not in required or len(set(required)) != len(required)
            or not isinstance(reviewers, dict) or not isinstance(ids, dict)
            or set(reviewers) != set(required) or set(ids) != set(required)
            or not all(_positive(n) for n in ids.values()) or len(set(ids.values())) != len(ids)):
        raise ValueError("invalid approval review provenance")
    owners = set()
    for kind, reviewer in reviewers.items():
        prefix = f"codex-{kind}-"
        if not isinstance(reviewer, str) or not reviewer.startswith(prefix):
            raise ValueError("invalid approved reviewer")
        resident = reviewer[len(prefix):]
        if not RESIDENT.fullmatch(resident) or resident == author or resident in owners:
            raise ValueError("approval requires independent reviewer identities")
        owners.add(resident)
    return value


def _read(directory, number):
    dirs = os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW
    base = os.open(directory, dirs)
    try:
        try:
            folder = os.open("approvals", dirs, dir_fd=base)
        except FileNotFoundError:
            return None
        try:
            try:
                fd = os.open(f"{number}.json", os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=folder)
            except FileNotFoundError:
                return None
            with os.fdopen(fd, "rb") as stream:
                info = os.fstat(stream.fileno())
                if (not stat.S_ISREG(info.st_mode) or stat.S_IMODE(info.st_mode) != 0o600
                        or info.st_uid != os.geteuid() or info.st_nlink != 1 or info.st_size > SIZE):
                    raise ValueError("unsafe approval file")
                raw = stream.read(SIZE + 1)
                if len(raw) > SIZE:
                    raise ValueError("approval file too large")
                return raw
        finally:
            os.close(folder)
    finally:
        os.close(base)


def load_approval(directory, number):
    """Return exact approval or None. Unsafe state is never treated as absence."""
    directory = Path(directory)
    if (not _positive(number) or not directory.is_absolute()
            or directory.resolve(strict=True) != directory or not directory.is_dir()):
        raise ValueError("invalid physical approval directory or PR")
    raw = _read(directory, number)
    if raw is None:
        return None
    value = json.loads(raw.decode("utf-8"), object_pairs_hook=_object, parse_constant=_invalid_constant)
    return _check(value, number)
