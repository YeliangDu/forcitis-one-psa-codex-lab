"""Public config boundary: private independently pinned data, never authority/code.

No runtime creation, credential load, module import, RPC, effect or ledger write.
"""
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import uuid
from typing import Any

MAX_CONFIG = 128 * 1024


def require(ok, reason):
    if not ok:
        raise ValueError(reason)


def fields(value, expected):
    require(type(value) is dict and set(value) == set(expected), 'unexpected-private-binding-fields')


def sha(value):
    require(type(value) is str and re.fullmatch('[0-9a-f]{64}', value) is not None, 'exact-sha256-required')


def path(value):
    require(type(value) is str and bool(value) and Path(value).is_absolute()
            and str(Path(value)) == value and '..' not in Path(value).parts, 'absolute-canonical-binding-path-required')


def unique(pairs):
    value = {}
    for key, item in pairs:
        require(key not in value, 'duplicate-private-binding-key')
        value[key] = item
    return value


def nonfinite(value):
    raise ValueError('nonfinite-private-binding-value')


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), allow_nan=False)


def record(value):
    fields(value, ('path', 'dev', 'ino', 'uid', 'mode', 'kind'))
    path(value['path'])
    require(all(type(value[k]) is int and value[k] >= 0 for k in ('dev','ino','uid','mode','kind')),
            'exact-filesystem-identity-required')


@dataclass(frozen=True, repr=False)
class PrivateRuntimeBinding:
    """Immutable canonical data. No verified/approved/live capability Boolean."""
    _canonical: str
    config_sha256: str
    selected_source_closure_sha256: str

    def view(self) -> dict[str, Any]:
        return json.loads(self._canonical)  # A copy; caller cannot mutate selection.

    def reference(self, name):
        value = self.view()['evidence_references']
        require(name in value, 'private-evidence-reference-not-installed')
        # Return selected path/digest only, never load approval/credential content.
        return dict(value[name])

    def assert_ledger_identity(self, installed_pin, registered_session, runtime_directory):
        value = self.view()
        require(installed_pin == value['ledger']['installation_sha256']
                and runtime_directory == value['ledger']['directory']
                and registered_session == value['native']['expected_registered_session'],
                'existing-ledger-binding-differs-no-reset-or-replacement')
        # This check does not open/migrate/initialize storage or approve adoption.

    def check_endpoint_identity(self):
        endpoint = self.view()['native']['endpoint']
        for name in ('alias_parent','target_parent'):
            value = Path(endpoint[name]['path'])
            require(value.resolve(strict=True) == value and not value.is_symlink(), 'nonphysical-endpoint-parent')
        for key in ('alias','alias_parent','target','target_parent'):
            value = endpoint[key]
            info = Path(value['path']).lstat()
            actual = dict(path=value['path'], dev=info.st_dev, ino=info.st_ino, uid=info.st_uid,
                          mode=stat.S_IMODE(info.st_mode), kind=stat.S_IFMT(info.st_mode))
            require(actual == value and info.st_uid == os.geteuid(), 'selected-endpoint-identity-moved')
        require(endpoint['alias_parent']['kind'] == endpoint['target_parent']['kind'] == stat.S_IFDIR
                and endpoint['alias_parent']['mode'] == endpoint['target_parent']['mode'] == 0o700
                and endpoint['alias']['kind'] == stat.S_IFLNK and endpoint['target']['kind'] == stat.S_IFSOCK
                and endpoint['target']['mode'] & 0o077 == 0, 'private-explicit-endpoint-required')
        alias, target = Path(endpoint['alias']['path']), Path(endpoint['target']['path'])
        require(os.readlink(alias) == endpoint['link_text'] == str(target)
                and target.resolve(strict=True) == target and alias.resolve(strict=True) == target,
                'explicit-alias-target-mismatch')
        # Socket identity does not establish App Server process epoch/authentication.
        return dict(endpoint_identity_matched=True, server_epoch_proved=False, execution_authority=False)


def load_private_binding(config_path, independent_config_sha256, independent_source_closure_sha256):
    """Only trusted launcher supplies pins; a model JSON argument is not trust."""
    sha(independent_config_sha256)
    sha(independent_source_closure_sha256)
    value = Path(config_path)
    path(str(value))
    require(value.resolve(strict=True) == value and not value.is_symlink(), 'physical-private-config-required')
    parent = value.parent.stat()
    require(parent.st_uid == os.geteuid() and stat.S_IMODE(parent.st_mode) == 0o700, 'private-config-parent-required')
    fd = os.open(str(value), os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    try:
        before = os.fstat(fd)
        require(stat.S_ISREG(before.st_mode) and before.st_uid == os.geteuid()
                and before.st_nlink == 1 and stat.S_IMODE(before.st_mode) in (0o400,0o600)
                and 0 < before.st_size <= MAX_CONFIG, 'bounded-owned-private-config-required')
        raw = bytearray()
        while len(raw) <= MAX_CONFIG:
            chunk = os.read(fd, min(16384, MAX_CONFIG + 1 - len(raw)))
            if not chunk:
                break
            raw.extend(chunk)
        after, named = os.fstat(fd), value.lstat()
        identity = lambda s: (s.st_dev,s.st_ino,s.st_uid,s.st_mode,s.st_nlink,s.st_size,s.st_mtime_ns,s.st_ctime_ns)
        require(identity(before) == identity(after) == identity(named) and len(raw) == before.st_size,
                'private-config-moved-during-read')
    finally:
        os.close(fd)
    require(hashlib.sha256(raw).hexdigest() == independent_config_sha256, 'independent-private-config-pin-mismatch')
    data = json.loads(bytes(raw).decode('utf-8'), object_pairs_hook=unique, parse_constant=nonfinite)
    fields(data, ('schema','deployment_label','source_closure_sha256','ledger','role','native','evidence_references'))
    require(data['schema'] == 'lab-private-runtime-binding/1', 'unsupported-private-binding-schema')
    require(type(data['deployment_label']) is str and re.fullmatch('[a-z][a-z0-9-]{0,63}', data['deployment_label']),
            'bounded-deployment-label-required')
    require(data['source_closure_sha256'] == independent_source_closure_sha256, 'independent-source-selection-mismatch')
    fields(data['ledger'], ('directory','installation_sha256'))
    path(data['ledger']['directory']); sha(data['ledger']['installation_sha256'])
    fields(data['role'], ('name','physical_cwd'))
    require(type(data['role']['name']) is str and re.fullmatch('one-[a-z][a-z0-9-]*', data['role']['name']),
            'explicit-role-name-required')
    path(data['role']['physical_cwd'])
    fields(data['native'], ('expected_registered_session','endpoint','proxy','engine_epoch_reference'))
    session = data['native']['expected_registered_session']
    require(type(session) is str and str(uuid.UUID(session)) == session, 'canonical-private-session-required')
    fields(data['native']['proxy'], ('path','sha256'))
    path(data['native']['proxy']['path']); sha(data['native']['proxy']['sha256'])
    endpoint = data['native']['endpoint']
    fields(endpoint, ('alias','alias_parent','target','target_parent','link_text'))
    for key in ('alias','alias_parent','target','target_parent'):
        record(endpoint[key])
    require(str(Path(endpoint['alias']['path']).parent) == endpoint['alias_parent']['path']
            and str(Path(endpoint['target']['path']).parent) == endpoint['target_parent']['path']
            and endpoint['link_text'] == endpoint['target']['path'], 'explicit-endpoint-parent-binding-required')
    refs = data['evidence_references']
    require(type(refs) is dict and len(refs) <= 64, 'bounded-private-reference-map-required')
    for key, ref in refs.items():
        require(type(key) is str and re.fullmatch('[a-z][a-z0-9-]{0,63}',key), 'bounded-reference-name-required')
        fields(ref, ('kind','path','sha256'))
        require(ref['kind'] in ('observation','authorization','source-manifest','lifecycle-receipt'),
                'not-a-credential-or-code-reference')
        path(ref['path']); sha(ref['sha256'])
    epoch = data['native']['engine_epoch_reference']
    require(epoch is None or (type(epoch) is str and epoch in refs and refs[epoch]['kind'] == 'lifecycle-receipt'),
            'explicit-lifecycle-reference-or-unknown-required')
    return PrivateRuntimeBinding(canonical(data), independent_config_sha256, independent_source_closure_sha256)
