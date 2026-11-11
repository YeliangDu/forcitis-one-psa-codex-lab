#!/usr/bin/env bash
# Lab #54 workflow-identity slice, VERIFY ONLY; no signing/deploy capabilities.
# verify ARCHIVE STATEMENT EXTERNAL_SIGNERS VERSION COMMIT [ARCHIVE_SHA256 [SIGNERS_SHA256]]
# The frozen acceptance supplies external artifact/key trust before this call.
# Legacy format/principal/namespace compatibility is deliberate, not the final
# independent Lab signature namespace or an actual native-workflow attestation.
set -euo pipefail
exec python3 -I -S -B - "$@" <<'PY'
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import tarfile

WORKFLOW = 'YeliangDu/forcitis-one-psa-codex-lab/.github/workflows/lab-isolated-release.yml@refs/heads/main'
PRINCIPAL = 'forcitis-one-release'
NAMESPACE = 'forcitis-one-release'
FORMAT = 'forcitis-one-release-statement/1'
FIELDS = {'format', 'version', 'commit', 'bundle', 'bundle-sha256',
          'manifest-sha256', 'sha256sums-sha256', 'workflow', 'run'}


def need(ok, reason):
    if not ok:
        raise ValueError(reason)


def sha(value):
    return type(value) is str and re.fullmatch('[0-9a-f]{64}', value) is not None


def identity(info):
    return (info.st_dev, info.st_ino, info.st_uid, info.st_mode, info.st_nlink,
            info.st_size, info.st_mtime_ns, info.st_ctime_ns)


def checked_path(value, limit):
    path = Path(os.path.abspath(value))
    need(path.resolve(strict=True) == path, 'input must be a physical file, not a symlink')
    info = path.lstat()
    need(stat.S_ISREG(info.st_mode) and info.st_nlink == 1 and info.st_uid in (0, os.geteuid())
         and not info.st_mode & 0o022 and 0 < info.st_size <= limit, 'unsafe input owner/mode/size')
    return path, identity(info)


def raw_file(path, expected):
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(fd, 'rb') as stream:
        need(identity(os.fstat(stream.fileno())) == expected, 'input changed before read')
        raw = stream.read(expected[5] + 1)
        need(len(raw) == expected[5] and identity(os.fstat(stream.fileno())) == expected
             and identity(path.lstat()) == expected, 'input changed during read')
        return raw


def archive_digest(path, expected):
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    result = hashlib.sha256()
    with os.fdopen(fd, 'rb') as stream:
        need(identity(os.fstat(stream.fileno())) == expected, 'archive changed before hash')
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            result.update(block)
        need(identity(os.fstat(stream.fileno())) == expected and identity(path.lstat()) == expected,
             'archive changed during hash')
    return result.hexdigest()


def unique(pairs):
    result = {}
    for key, value in pairs:
        need(key not in result, 'duplicate manifest field')
        result[key] = value
    return result


def invalid(value):
    raise ValueError('nonfinite manifest JSON')


def verify(args):
    need(len(args) in (6, 7, 8) and args[0] == 'verify',
         'usage: verify ARCHIVE STATEMENT EXTERNAL_SIGNERS VERSION COMMIT [ARCHIVE_SHA256 [SIGNERS_SHA256]]')
    _, archive_arg, statement_arg, signers_arg, version, commit, *pins = args
    need(re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+(?:-rc\.[0-9]+)?', version), 'invalid expected version')
    need(re.fullmatch('[0-9a-f]{40}', commit), 'expected source must be a full lowercase commit')
    need(all(sha(pin) for pin in pins), 'invalid external SHA256 pin')
    archive, archive_id = checked_path(archive_arg, 1024 * 1024 * 1024)
    statement, statement_id = checked_path(statement_arg, 64 * 1024)
    signers, signers_id = checked_path(signers_arg, 64 * 1024)
    signature, signature_id = checked_path(str(statement) + '.sig', 64 * 1024)
    need(len({(value[0], value[1]) for value in (archive_id, statement_id, signers_id, signature_id)}) == 4,
         'archive/statement/signature/external signers must be distinct files')
    statement_raw, signer_raw = raw_file(statement, statement_id), raw_file(signers, signers_id)
    raw_file(signature, signature_id)
    signer_rows = [line.split() for line in signer_raw.decode('utf-8').splitlines()
                   if line.strip() and not line.lstrip().startswith('#')]
    need(len(signer_rows) == 1 and len(signer_rows[0]) >= 3
         and signer_rows[0][:2] == [PRINCIPAL, 'ssh-ed25519'], 'one external ephemeral Ed25519 signer required')
    base64.b64decode(signer_rows[0][2], validate=True)
    if len(pins) == 2:
        need(hashlib.sha256(signer_raw).hexdigest() == pins[1], 'external allowed-signers digest mismatch')
    # Trust is the explicitly selected EXTERNAL signer file, never a member/key
    # extracted from the archive or a key named by the candidate statement.
    result = subprocess.run(['ssh-keygen', '-Y', 'verify', '-f', str(signers), '-I', PRINCIPAL,
                             '-n', NAMESPACE, '-s', str(signature)], input=statement_raw,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=30, check=False)
    need(result.returncode == 0, 'the release statement signature does not verify against external signers')
    fields = {}
    for line in statement_raw.decode('utf-8').splitlines():
        key, separator, value = line.partition(': ')
        need(separator and key in FIELDS and key not in fields and value
             and not any(ord(c) < 32 or ord(c) == 127 for c in value), 'invalid or duplicate statement field')
        fields[key] = value
    need(set(fields) == FIELDS, 'statement fields missing')
    name = 'forcitis-one-' + version
    need(fields['format'] == FORMAT, 'unknown statement format')
    need(fields['version'] == version, 'statement is for another version')
    need(fields['commit'] == commit, 'statement is for another commit')
    need(fields['bundle'] == name + '.tar.gz' and archive.name == name + '.tar.gz', 'statement/archive names another bundle')
    need(fields['workflow'] == WORKFLOW, 'statement was not produced by the allowed Lab workflow on main')
    need(re.fullmatch('[1-9][0-9]{0,18}', fields['run']), 'invalid statement run claim')
    need(all(sha(fields[key]) for key in ('bundle-sha256', 'manifest-sha256', 'sha256sums-sha256')),
         'invalid signed member/archive digest')
    actual = archive_digest(archive, archive_id)
    need(actual == fields['bundle-sha256'], 'bundle differs from the signed statement')
    if pins:
        need(actual == pins[0], 'archive differs from external expected SHA256')
    members, seen = {}, set()
    with tarfile.open(archive, 'r:gz') as source:
        for member in source:
            path = member.name.rstrip('/')
            need(path == name or path.startswith(name + '/'), 'tar member outside expected bundle')
            need(path not in seen and '\\' not in path and ':' not in path
                 and all(part not in ('', '.', '..') for part in path.split('/'))
                 and not any(ord(c) < 32 or ord(c) == 127 for c in path)
                 and (member.isdir() or member.isfile()), 'unsafe or duplicate tar member')
            seen.add(path)
            need(len(seen) <= 100000, 'tar member count bound')
            if path in (name + '/manifest.json', name + '/SHA256SUMS'):
                need(member.isfile() and 0 < member.size <= 16 * 1024 * 1024, 'signed member size/type bound')
                handle = source.extractfile(member)
                need(handle is not None, 'signed member unavailable')
                with handle:
                    members[path] = handle.read(16 * 1024 * 1024 + 1)
                need(len(members[path]) == member.size, 'signed member truncated')
    need(set(members) == {name + '/manifest.json', name + '/SHA256SUMS'}, 'signed members missing')
    need(hashlib.sha256(members[name + '/manifest.json']).hexdigest() == fields['manifest-sha256'],
         'manifest.json differs from the signed statement')
    need(hashlib.sha256(members[name + '/SHA256SUMS']).hexdigest() == fields['sha256sums-sha256'],
         'SHA256SUMS differs from the signed statement')
    manifest = json.loads(members[name + '/manifest.json'].decode('utf-8'),
                          object_pairs_hook=unique, parse_constant=invalid)
    need(type(manifest) is dict and manifest.get('version') == version and manifest.get('gitSha') == commit,
         'actual archive manifest does not match expected version/source')
    for path, expected in ((archive, archive_id), (statement, statement_id),
                           (signers, signers_id), (signature, signature_id)):
        need(identity(path.lstat()) == expected, 'input changed during verification')
    print(json.dumps(dict(action='verified-lab-workflow-identity', version=version, commit=commit,
        bundle_sha256=actual, workflow=WORKFLOW, statement_run_claim=fields['run'],
        scope='legacy-format/namespace compatibility slice only', namespace_migrated=False,
        external_archive_pin_checked=bool(pins), external_signer_file_pin_checked=len(pins) == 2,
        native_workflow_run_verified=False, release_authority=False, installation_authority=False), sort_keys=True))


try:
    verify(sys.argv[1:])
except (ValueError, OSError, UnicodeError, tarfile.TarError, subprocess.SubprocessError):
    # Never print subprocess stderr, keys, signed URLs or arbitrary payload text.
    print('lab_release_statement: identity, signature, external pin or archive verification refused', file=sys.stderr)
    raise SystemExit(1)
PY
