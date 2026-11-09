"""Isolated receipt provenance reader; injected read seam only, no authority."""
import copy
import hashlib
import io
import json
from pathlib import PurePosixPath
import re
import stat
import zipfile

ARCHIVE_LIMIT = 32 * 1024 * 1024
RECEIPT_LIMIT = 256 * 1024
RECEIPT_PATHS = {"receipt.json", "_temp/lab-pr-evidence/receipt.json"}


def _require(condition, reason):
    if not condition:
        raise ValueError(reason)


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


def _id(value):
    """Lossless normalization of positive integer or canonical decimal IDs."""
    if type(value) is int:
        _require(value > 0, 'Invalid positive ID')
        return value
    _require(type(value) is str and re.fullmatch(r'[1-9][0-9]{0,19}', value) is not None,
             'Invalid decimal ID')
    return int(value)


def _sha(value, length):
    _require(type(value) is str and re.fullmatch(r'[0-9a-f]{' + str(length) + '}', value) is not None,
             'Invalid SHA')
    return value


class GithubRead:
    """Verify supplied observations, never fetch via a default network transport.

    The caller selects independent expected pins and authenticates the injected
    read seam. A valid receipt conveys no eligibility, admission or write grant.
    """

    def __init__(self, request=None, *, expected=None):
        _require(callable(request), 'Explicit read seam required')
        _require(type(expected) is dict and set(expected) == {
            'repository', 'repository_id', 'number', 'run_id', 'attempt',
            'artifact_id', 'head', 'base', 'zip_sha256', 'profile'
        }, 'Independent expected tuple required')
        self._request = request
        self._expected = copy.deepcopy(expected)
        e = self._expected
        for key in ('repository_id', 'number', 'run_id', 'attempt', 'artifact_id'):
            e[key] = _id(e[key])
        _require(type(e['repository']) is str and re.fullmatch(
            r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', e['repository']) is not None,
            'Invalid repository')
        _sha(e['head'], 40)
        _sha(e['base'], 40)
        _sha(e['zip_sha256'], 64)
        profile = e['profile']
        _require(type(profile) is dict and set(profile) == {'name', 'workflow', 'applicability'},
                 'Invalid independent profile')
        _require(type(profile['name']) is str and 0 < len(profile['name']) <= 256,
                 'Missing profile identity')
        workflow = profile['workflow']
        _require(type(workflow) is str and re.fullmatch(
            r'\.github/workflows/[A-Za-z0-9_-]+\.ya?ml', workflow) is not None,
            'Invalid workflow path')
        lanes = profile['applicability']
        _require(type(lanes) is dict and 0 < len(lanes) <= 64, 'Missing selected applicability')
        for name, applicability in lanes.items():
            _require(type(name) is str and 0 < len(name) <= 128
                     and type(applicability) is str and applicability in ('required', 'not-applicable'),
                     'Invalid selected applicability')

    def _get(self, path):
        value = self._request(path)
        _require(type(value) is dict, 'Missing observation object')
        return value

    def _repository(self, value):
        e = self._expected
        _require(type(value) is dict and _id(value.get('id')) == e['repository_id']
                 and value.get('full_name') == e['repository'], 'Wrong repository')

    def _run(self, run):
        e = self._expected
        _require(_id(run.get('id')) == e['run_id'] and _id(run.get('run_attempt')) == e['attempt'],
                 'Wrong run or attempt')
        self._repository(run.get('repository'))
        _require(run.get('head_sha') == e['head'] and run.get('event') == 'pull_request'
                 and run.get('path') == e['profile']['workflow']
                 and run.get('status') == 'completed' and run.get('conclusion') == 'success',
                 'Wrong run head/workflow/state')
        branch = run.get('head_branch')
        _require(type(branch) is str and bool(branch), 'Missing run branch')
        prs = run.get('pull_requests')
        _require(type(prs) is list and len(prs) == 1 and type(prs[0]) is dict,
                 'Ambiguous run PR binding')
        pr = prs[0]
        _require(_id(pr.get('number')) == e['number'], 'Wrong PR')
        for key, sha in (('head', e['head']), ('base', e['base'])):
            value = pr.get(key)
            _require(type(value) is dict and value.get('sha') == sha, 'Wrong PR SHA')
            self._repository(value.get('repo'))
        _require(pr['head'].get('ref') == branch, 'Wrong head branch')
        return branch

    def artifacts(self, run_id, attempt):
        """Return only fully verified downloaded receipts, or refuse with ValueError."""
        e = self._expected
        _require(_id(run_id) == e['run_id'] and _id(attempt) == e['attempt'], 'Wrong selected run/attempt')
        self._repository(self._get(''))
        branch = self._run(self._get(f"actions/runs/{e['run_id']}/attempts/{e['attempt']}"))
        # Also require the current run snapshot, excluding a stale rerun attempt.
        _require(self._run(self._get(f"actions/runs/{e['run_id']}")) == branch, 'Run snapshots differ')
        pages = self._request(f"actions/runs/{e['run_id']}/artifacts?per_page=100", pages=True)
        _require(type(pages) is list and 0 < len(pages) <= 100, 'Missing artifact pages')
        rows, ids, totals = [], set(), []
        for page in pages:
            _require(type(page) is dict and type(page.get('artifacts')) is list,
                     'Invalid artifact page')
            total = page.get('total_count')
            _require(type(total) is int and 0 <= total <= 10000, 'Invalid artifact count')
            totals.append(total)
            for row in page['artifacts']:
                _require(type(row) is dict, 'Invalid artifact record')
                identity = _id(row.get('id'))
                _require(identity not in ids, 'Duplicate artifact ID')
                ids.add(identity)
                rows.append(row)
                _require(len(rows) <= 10000, 'Artifact bound exceeded')
        _require(all(total == len(rows) for total in totals), 'Incomplete artifact listing')
        selected = [row for row in rows if _id(row['id']) == e['artifact_id']]
        if not selected:
            return []
        row = selected[0]
        _require(row.get('expired') is False and row.get('name') ==
                 f"lab-pr-{e['number']}-{e['head']}-{e['attempt']}", 'Expired or stale artifact')
        link = row.get('workflow_run')
        _require(type(link) is dict and _id(link.get('id')) == e['run_id']
                 and _id(link.get('repository_id')) == e['repository_id']
                 and _id(link.get('head_repository_id')) == e['repository_id']
                 and link.get('head_sha') == e['head'] and link.get('head_branch') == branch,
                 'Wrong artifact/run/repository/head association')
        _require(row.get('digest') == 'sha256:' + e['zip_sha256'], 'Missing or wrong declared ZIP digest')
        blob = self._request(f"actions/artifacts/{e['artifact_id']}/zip", binary=True)
        _require(type(blob) is bytes and 0 < len(blob) <= ARCHIVE_LIMIT, 'Invalid ZIP bytes')
        actual = hashlib.sha256(blob).hexdigest()
        _require(actual == e['zip_sha256'] and row['digest'] == 'sha256:' + actual,
                 'Downloaded ZIP digest mismatch')
        try:
            receipt = decode_receipt(blob)
        except (zipfile.BadZipFile, RuntimeError, OSError, EOFError, NotImplementedError) as error:
            raise ValueError('Invalid receipt archive') from error
        _require(receipt.get('head_sha') == e['head'] and receipt.get('observed_head') == e['head']
                 and receipt.get('base_sha') == e['base']
                 and _id(receipt.get('run_id')) == e['run_id']
                 and _id(receipt.get('run_attempt')) == e['attempt'], 'Wrong receipt identity')
        lanes = receipt.get('lanes')
        _require(type(lanes) is dict and set(lanes) == set(e['profile']['applicability']),
                 'Wrong receipt lane selection')
        for name, applicability in e['profile']['applicability'].items():
            _require(type(lanes[name]) is dict and lanes[name].get('applicability') == applicability,
                     'Receipt redefined profile applicability')
        result = copy.deepcopy(row)
        result.pop('receipt', None)
        result['receipt'] = receipt
        return [result]
