"""Fixed private-Lab GET transport; no queue, admission or action writer."""
import io
import json
from pathlib import PurePosixPath
import re
import stat
import subprocess
import zipfile


REPOSITORY = "YeliangDu/forcitis-one-psa-codex-lab"
ARCHIVE_LIMIT = 32 * 1024 * 1024
RECEIPT_LIMIT = 256 * 1024
RECEIPT_PATHS = {"receipt.json", "_temp/lab-pr-evidence/receipt.json"}
HEALTH_PATHS = {"health.json", "_temp/lab-main-health/health.json"}
ARTIFACT_NAME = re.compile(r"lab-pr-([1-9][0-9]*)-([0-9a-f]{40})-([1-9][0-9]*)")


def _require(condition, reason):
    if not condition:
        raise ValueError(reason)


def _positive(value):
    _require(type(value) is int and value > 0, "Expected a positive integer")
    return value


def _object(pairs):
    result = {}
    for key, value in pairs:
        _require(key not in result, "Duplicate JSON key")
        result[key] = value
    return result


def _constant(value):
    raise ValueError("Nonfinite JSON constant")


def _json(value):
    return json.loads(value, object_pairs_hook=_object, parse_constant=_constant)


def decode_receipt(blob):
    """Validate ZIP metadata and decode exactly one bounded in-memory receipt."""
    return _decode_archive(blob, "receipt.json", RECEIPT_PATHS)


def decode_health(blob):
    """Decode the separate main signal with the same bounds and ZIP checks."""
    return _decode_archive(blob, "health.json", HEALTH_PATHS)


def _decode_archive(blob, basename, allowed_paths):
    _require(isinstance(blob, bytes) and 0 < len(blob) <= ARCHIVE_LIMIT, "Invalid archive size/type")
    with zipfile.ZipFile(io.BytesIO(blob)) as archive:
        receipts, names = [], set()
        total = 0
        for member in archive.infolist():
            name = member.filename
            _require(member.orig_filename == name and bool(name) and "\\" not in name
                     and not name.startswith("/") and ":" not in name
                     and not any(ord(c) < 32 or ord(c) == 127 for c in name), "Unsafe ZIP path")
            path = name[:-1] if name.endswith("/") else name
            _require(bool(path) and all(p not in ("", ".", "..") for p in path.split("/")), "Unsafe ZIP components")
            _require(name not in names, "Duplicate ZIP member")
            names.add(name)
            _require(not member.flag_bits & 1, "Encrypted ZIP member")
            kind = stat.S_IFMT(member.external_attr >> 16)
            allowed = (0, stat.S_IFDIR) if member.is_dir() else (0, stat.S_IFREG)
            _require(kind in allowed, "Nonregular ZIP member")
            _require(member.file_size >= 0 and member.compress_size >= 0, "Invalid ZIP size")
            total += member.file_size
            _require(total <= ARCHIVE_LIMIT, "Oversize expanded archive")
            if PurePosixPath(path).name == basename:
                _require(not member.is_dir() and name in allowed_paths, "Unapproved receipt path")
                _require(member.file_size <= RECEIPT_LIMIT, "Oversize receipt")
                receipts.append(member)
        _require(len(receipts) == 1, "Missing or duplicate receipt")
        _require(archive.testzip() is None, "Corrupt ZIP member")
        with archive.open(receipts[0], "r") as stream:
            payload = stream.read(RECEIPT_LIMIT + 1)
        _require(len(payload) <= RECEIPT_LIMIT, "Oversize receipt")
        try:
            receipt = _json(payload.decode("utf-8"))
        except (UnicodeError, ValueError) as error:
            raise ValueError("Invalid receipt JSON") from error
        _require(isinstance(receipt, dict), "Receipt must be an object")
        return receipt


def _request(path, pages=False, binary=False):
    """Only internally generated relative paths reach this fixed GET command."""
    patterns = (
        r"", r"git/ref/heads/main", r"pulls/[1-9][0-9]*",
        r"actions/runs\?event=pull_request&head_sha=[0-9a-f]{40}&per_page=100",
        r"actions/runs/[1-9][0-9]*/attempts/[1-9][0-9]*/jobs\?per_page=100",
        r"pulls/[1-9][0-9]*/reviews\?per_page=100",
        r"actions/runs/[1-9][0-9]*/artifacts\?per_page=100",
        r"actions/runs\?event=push&branch=main&head_sha=[0-9a-f]{40}&per_page=100",
        r"actions/artifacts/[1-9][0-9]*/zip",
    )
    _require(isinstance(path, str) and any(re.fullmatch(p, path) for p in patterns), "Unapproved GET path")
    is_archive = re.fullmatch(patterns[-1], path) is not None
    is_paged = "per_page=100" in path
    _require(type(pages) is bool and type(binary) is bool
             and binary == is_archive and pages == is_paged, "Invalid GET mode")
    endpoint = "repos/" + REPOSITORY + ("/" + path if path else "")
    command = ["gh", "api", "--hostname", "github.com", "--method", "GET", endpoint]
    if pages:
        command += ["--paginate", "--slurp"]
    result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=120, check=False)
    if result.returncode != 0:
        # Do not expose CLI stderr, credentials or arbitrary response content.
        raise ValueError("Private Lab GET failed")
    if binary:
        return result.stdout
    try:
        return _json(result.stdout.decode("utf-8"))
    except (UnicodeError, ValueError) as error:
        raise ValueError("Malformed GitHub JSON") from error


class GithubRead:
    """Read-only port for observe_release; injected requests are fixture seams."""

    def __init__(self, request=None):
        _require(request is None or callable(request), "Request must be callable")
        self._request = _request if request is None else request

    def _single(self, path):
        result = self._request(path)
        _require(isinstance(result, dict), "Expected GitHub object")
        return result

    def _pages(self, path, field=None):
        pages = self._request(path, pages=True)
        _require(isinstance(pages, list) and bool(pages), "Missing complete pages")
        rows, counts, ids = [], [], set()
        for page in pages:
            if field is None:
                entries = page
            else:
                _require(isinstance(page, dict) and field in page, "Malformed page object")
                entries = page[field]
                if "total_count" in page:
                    count = page["total_count"]
                    _require(type(count) is int and count >= 0, "Invalid page total")
                    counts.append(count)
            _require(isinstance(entries, list), "Malformed page records")
            for row in entries:
                _require(isinstance(row, dict), "Malformed GitHub record")
                record_id = _positive(row.get("id"))
                _require(record_id not in ids, "Duplicate paged record")
                ids.add(record_id)
                rows.append(dict(row))
        if counts:
            _require(len(counts) == len(pages) and len(set(counts)) == 1
                     and len(rows) == counts[0], "Incomplete or changing pagination")
        return rows

    def repository(self):
        return self._single("")

    def main_ref(self):
        return self._single("git/ref/heads/main")

    def pull(self, number):
        return self._single(f"pulls/{_positive(number)}")

    def runs(self, head_sha):
        _require(isinstance(head_sha, str) and re.fullmatch(r"[0-9a-f]{40}", head_sha) is not None,
                 "Expected full lowercase SHA")
        return self._pages(f"actions/runs?event=pull_request&head_sha={head_sha}&per_page=100", "workflow_runs")

    def jobs(self, run_id, attempt):
        return self._pages(f"actions/runs/{_positive(run_id)}/attempts/{_positive(attempt)}/jobs?per_page=100", "jobs")

    def main_runs(self, head_sha):
        _require(isinstance(head_sha, str) and re.fullmatch(r"[0-9a-f]{40}", head_sha) is not None,
                 "Expected full lowercase SHA")
        return self._pages(f"actions/runs?event=push&branch=main&head_sha={head_sha}&per_page=100", "workflow_runs")

    def main_artifacts(self, run_id, attempt, head_sha):
        _positive(run_id)
        _positive(attempt)
        _require(isinstance(head_sha, str) and re.fullmatch(r"[0-9a-f]{40}", head_sha) is not None,
                 "Expected full lowercase SHA")
        rows = self._pages(f"actions/runs/{run_id}/artifacts?per_page=100", "artifacts")
        name = f"lab-main-health-{head_sha}-{attempt}"
        for row in rows:
            _require(isinstance(row.get("name"), str) and bool(row["name"])
                     and type(row.get("expired")) is bool, "Malformed artifact metadata")
            row.pop("health", None)
            if row["expired"] or row["name"] != name:
                continue
            blob = self._request(f"actions/artifacts/{row['id']}/zip", binary=True)
            row["health"] = decode_health(blob)
        return rows

    def reviews(self, number):
        return self._pages(f"pulls/{_positive(number)}/reviews?per_page=100")

    def artifacts(self, run_id, attempt):
        _positive(run_id)
        _positive(attempt)
        rows = self._pages(f"actions/runs/{run_id}/artifacts?per_page=100", "artifacts")
        for row in rows:
            _require(isinstance(row.get("name"), str) and bool(row["name"])
                     and type(row.get("expired")) is bool, "Malformed artifact metadata")
            # A supplied metadata field can never stand in for downloaded proof.
            row.pop("receipt", None)
            match = ARTIFACT_NAME.fullmatch(row["name"])
            if row["expired"] or match is None or match[3] != str(attempt):
                continue
            blob = self._request(f"actions/artifacts/{row['id']}/zip", binary=True)
            row["receipt"] = decode_receipt(blob)
        return rows
