"""Test-owned Git-object seed and immutable raw-port ledger. No default source."""
import hashlib
import json
from pathlib import Path, PurePosixPath
import re


def need(ok, code):
    if not ok:
        raise AssertionError(code)


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def safe_path(value):
    need(type(value) is str and value and '\\' not in value and '\x00' not in value,
         'MATERIAL_UNSAFE_PATH')
    path = PurePosixPath(value)
    need(not path.is_absolute() and str(path) == value
         and all(p not in ('', '.', '..') for p in value.split('/')), 'MATERIAL_UNSAFE_PATH')
    return value


def unique(pairs):
    out = {}
    for key, value in pairs:
        need(key not in out, 'MATERIAL_DUPLICATE_JSON')
        out[key] = value
    return out


class VerifiedMaterial:
    def __init__(self, entries, payloads, ledger):
        self.entries, self.payloads, self.ledger = entries, payloads, ledger

    def seed(self, destination, phase):
        need(phase in ('old', 'new'), 'MATERIAL_UNKNOWN_PHASE')
        root = Path(destination)
        need(root.is_absolute() and root.resolve(strict=True) == root and root.is_dir(),
             'MATERIAL_DESTINATION_NOT_PHYSICAL')
        selected = [e for e in self.entries if e['phase'] == phase]
        # Preflight every destination before any file publication.
        for entry in selected:
            target = root / entry['target_path']
            need(not target.exists() and not target.is_symlink(), 'MATERIAL_DESTINATION_EXISTS')
            for parent in target.parents:
                if parent == root:
                    break
                need(not parent.is_symlink(), 'MATERIAL_DESTINATION_PARENT_SYMLINK')
        for entry in selected:
            target = root / entry['target_path']
            target.parent.mkdir(parents=True, exist_ok=True)
            with target.open('xb') as stream:
                stream.write(self.payloads[(phase, entry['target_path'])])
            target.chmod(0o755 if entry['mode'] == '100755' else 0o644)
        return tuple(e['target_path'] for e in selected)

    def check_scratch_commit(self, phase, read_scratch_object):
        for entry in self.entries:
            if entry['phase'] == phase:
                raw = read_scratch_object(entry['target_path'])
                need(type(raw) is bytes and digest(raw) == entry['sha256'],
                     'MATERIAL_NOT_IN_EXACT_SCRATCH_COMMIT')
        return True

    def raw_ledger(self):
        return RawLedger(self.ledger, self.payloads)


def verify_locked_material(manifest_raw, manifest_sha256, required_inventory,
                           required_raw_keys, read_locked_object):
    """read_locked_object(commit,path)->bytes; no ambient CWD/default/fetch port.

    Manifest digest, exact source inventory and raw-key set must be independently
    frozen test-owned inputs, never derived from candidate result or manifest.
    """
    need(type(manifest_raw) is bytes and len(manifest_raw) <= 1024 * 1024
         and digest(manifest_raw) == manifest_sha256, 'MATERIAL_MANIFEST_SHA_MISMATCH')
    spec = json.loads(manifest_raw, object_pairs_hook=unique,
                      parse_constant=lambda x: need(False, 'MATERIAL_NONFINITE'))
    need(type(spec) is dict and set(spec) == {'version', 'entries', 'ledger'}
         and type(spec['version']) is int and spec['version'] == 1,
         'MATERIAL_MANIFEST_SCHEMA')
    entries, ledger = spec['entries'], spec['ledger']
    need(type(entries) is list and entries and len(entries) <= 512, 'MATERIAL_EMPTY_INVENTORY')
    need(type(ledger) is dict and set(ledger) == set(required_raw_keys), 'MATERIAL_RAW_COLLECTOR_INCOMPLETE')
    seen, payloads = set(), {}
    fields = {'phase', 'source_commit', 'source_path', 'target_path', 'mode', 'blob', 'sha256'}
    for entry in entries:
        need(type(entry) is dict and set(entry) == fields, 'MATERIAL_ENTRY_SCHEMA')
        phase = entry['phase']
        need(phase in ('old', 'new', 'raw'), 'MATERIAL_UNKNOWN_PHASE')
        source, target = safe_path(entry['source_path']), safe_path(entry['target_path'])
        key = (phase, target)
        need(key not in seen, 'MATERIAL_DUPLICATE_ENTRY')
        seen.add(key)
        need(entry['mode'] in ('100644', '100755') and type(entry['source_commit']) is str
             and re.fullmatch('[0-9a-f]{40}', entry['source_commit']) is not None,
             'MATERIAL_SOURCE_NOT_LOCKED')
        need(type(entry['sha256']) is str and re.fullmatch('[0-9a-f]{64}', entry['sha256']) is not None,
             'MATERIAL_BAD_RAW_DIGEST')
        raw = read_locked_object(entry['source_commit'], source)
        need(type(raw) is bytes and digest(raw) == entry['sha256'], 'MATERIAL_SOURCE_SHA_MISMATCH')
        blob = hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest()
        need(blob == entry['blob'], 'MATERIAL_GIT_BLOB_MISMATCH')
        payloads[key] = raw
    # Exact inventory includes the raw files as well as both code closures.
    need(seen == set(required_inventory), 'MATERIAL_SOURCE_INVENTORY_INCOMPLETE')
    for key, record in ledger.items():
        need(type(record) is dict and set(record) == {'path', 'sha256', 'scope'}, 'MATERIAL_RAW_RECORD_SCHEMA')
        path = safe_path(record['path'])
        need(('raw', path) in seen and digest(payloads[('raw', path)]) == record['sha256'],
             'MATERIAL_RAW_RECORD_NOT_PINNED')
        need(type(record['scope']) is dict and record['scope'], 'MATERIAL_RAW_SCOPE_MISSING')
    return VerifiedMaterial(entries, payloads, ledger)


class RawLedger:
    """Read-only fake GET/download port. Consumption recorded by test, not driver."""
    def __init__(self, ledger, payloads):
        self.ledger, self.payloads, self.reads = ledger, payloads, []

    def read(self, key, exact_scope):
        need(key in self.ledger, 'RAW_UNPINNED_ENDPOINT')
        record = self.ledger[key]
        need(type(exact_scope) is dict and json.dumps(exact_scope, sort_keys=True) ==
             json.dumps(record['scope'], sort_keys=True), 'RAW_WRONG_SUBJECT_RUN_ATTEMPT')
        value = self.payloads[('raw', record['path'])]
        need(digest(value) == record['sha256'], 'RAW_BYTES_CHANGED')
        self.reads.append((key, digest(value)))
        return value

    def require_complete(self, required_keys):
        need({key for key, _ in self.reads} == set(required_keys), 'RAW_COLLECTOR_NOT_ACTUALLY_READ')
        return {key: value for key, value in self.reads}

class LockedGitObjects:
    """Hosted-only fixed existing checkout/object reader; no fetch or other repo."""
    def __init__(self, checkout, allowed_objects):
        self.checkout = Path(checkout)
        need(self.checkout.is_absolute() and self.checkout.resolve(strict=True) == self.checkout,
             'MATERIAL_SOURCE_CHECKOUT_NOT_PHYSICAL')
        self.allowed = frozenset(allowed_objects)

    def __call__(self, commit, path):
        import subprocess
        need((commit, path) in self.allowed, 'MATERIAL_OBJECT_NOT_ALLOWLISTED')
        result = subprocess.run(['git', '--no-replace-objects', '-C', str(self.checkout),
            'show', commit + ':' + safe_path(path)], capture_output=True, timeout=30)
        need(result.returncode == 0, 'MATERIAL_LOCKED_OBJECT_UNAVAILABLE_NO_FETCH')
        return result.stdout


def advance_verified_seed(material, destination):
    """Test-owned old→new scratch tree edit BEFORE H commit, never live install."""
    root = Path(destination)
    need(root.is_absolute() and root.resolve(strict=True) == root, 'MATERIAL_DESTINATION_NOT_PHYSICAL')
    old = {e['target_path']: e for e in material.entries if e['phase'] == 'old'}
    new = {e['target_path']: e for e in material.entries if e['phase'] == 'new'}
    for path, entry in old.items():
        target = root / path
        need(not target.is_symlink() and target.is_file()
             and digest(target.read_bytes()) == entry['sha256'], 'MATERIAL_OLD_SEED_MOVED')
    for path in new:
        target = root / path
        need(path in old or not target.exists(), 'MATERIAL_UNREVIEWED_EXISTING_TARGET')
        for parent in target.parents:
            if parent == root:
                break
            need(not parent.is_symlink(), 'MATERIAL_DESTINATION_PARENT_SYMLINK')
    # Exact reviewed old/new inventories define removals; no inferred live cleanup.
    for path in old.keys() - new.keys():
        (root / path).unlink()
    for path, entry in new.items():
        target = root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(material.payloads[('new', path)])
        target.chmod(0o755 if entry['mode'] == '100755' else 0o644)
    return tuple(new)
