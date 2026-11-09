"""Inert canonical manifests for an independently authorized old-control set.

This module performs no I/O, imports no candidate code and discovers no paths.
Matching the supplied set does not establish completeness of its dependency
closure, independent hosted proof, upgrade approval or production activation.
"""
import hashlib
import json
import re
import unicodedata


VERSION = 1
ENTRY_FIELDS = {"path", "mode", "blob", "content"}
REGULAR_MODES = {"100644", "100755"}


def _require(condition, reason):
    if not condition:
        raise ValueError(reason)


def _path(value):
    """Reject ambiguity instead of normalizing candidate-supplied filenames."""
    _require(type(value) is str and bool(value), "Expected an exact relative path string")
    _require(not value.startswith("/") and "\\" not in value and ":" not in value,
             "Absolute, drive or backslash paths are forbidden")
    _require(not any(unicodedata.category(c) in ("Cc", "Cf", "Cs")
                     or c in "\u2028\u2029" for c in value), "Control characters in path")
    _require(unicodedata.normalize("NFC", value) == value, "Noncanonical Unicode path")
    parts = value.split("/")
    _require(all(part not in ("", ".", "..") and part == part.strip()
                 and not part.endswith(".") for part in parts), "Noncanonical path component")
    return value


def _authorized(paths):
    _require(type(paths) is list and bool(paths), "A nonempty fixed authorized path list is required")
    authorized, spellings = set(), {}
    for item in paths:
        path = _path(item)
        _require(path not in authorized, "Duplicate authorized path")
        authorized.add(path)
        parts = path.split("/")
        for length in range(1, len(parts) + 1):
            prefix = "/".join(parts[:length])
            folded = prefix.casefold()
            _require(folded not in spellings or spellings[folded] == prefix,
                     "Case-conflicting authorized path")
            spellings[folded] = prefix
    folded_files = {path.casefold() for path in authorized}
    for path in authorized:
        parts = path.split("/")
        _require(not any("/".join(parts[:length]).casefold() in folded_files
                         for length in range(1, len(parts))), "A control file cannot also be a directory")
    return authorized


def build_manifest(authorized_paths, observed_entries):
    """Return versioned sorted entries and SHA-256 of their canonical encoding.

    Raw content is hashed verbatim, including line endings, NUL and non-UTF-8
    bytes. Git's SHA-1 blob identity is independently recomputed with its header;
    it is never substituted for the raw-content or canonical-manifest SHA-256.
    """
    authorized = _authorized(authorized_paths)
    _require(type(observed_entries) is list and len(observed_entries) == len(authorized),
             "Missing or extra observed controls")
    seen, rows = set(), []
    for entry in observed_entries:
        _require(type(entry) is dict and set(entry) == ENTRY_FIELDS, "Invalid observed control shape")
        path = _path(entry["path"])
        _require(path in authorized and path not in seen, "Unexpected or duplicate observed path")
        mode, blob, content = entry["mode"], entry["blob"], entry["content"]
        _require(type(mode) is str and mode in REGULAR_MODES, "Only regular Git file modes are allowed")
        _require(type(blob) is str and re.fullmatch(r"[0-9a-f]{40}", blob) is not None,
                 "Expected full lowercase Git blob SHA-1")
        _require(type(content) is bytes, "Control content must be raw bytes")
        header = b"blob " + str(len(content)).encode("ascii") + b"\x00"
        actual_blob = hashlib.sha1(header + content, usedforsecurity=False).hexdigest()
        _require(actual_blob == blob, "Observed Git blob does not match raw content")
        seen.add(path)
        rows.append(dict(path=path, mode=mode, blob=blob, sha256=hashlib.sha256(content).hexdigest()))
    _require(seen == authorized, "Observed controls do not equal the authorized set")
    rows.sort(key=lambda row: row["path"])
    payload = dict(version=VERSION, entries=rows)
    canonical = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return dict(payload, digest=hashlib.sha256(canonical).hexdigest())
