"""Explicit pinned private JSON resource reads; no credential/code/plugin loader."""
import hashlib
import json
from pathlib import Path
from portable_config import unique, nonfinite, require


def load_reference(path, pin):
    from branch_broker import private_raw
    raw = private_raw(path, limit=128 * 1024)
    require(hashlib.sha256(raw).hexdigest() == pin, 'independently-selected-private-reference-moved')
    return json.loads(raw.decode('utf-8'), object_pairs_hook=unique, parse_constant=nonfinite)


class PrivateResourceAccess:
    def private_authorization(self, name, expected_kind=None):
        reference = self.binding.reference(name)
        require(reference['kind'] == ('observation' if name == 'disconnect-reference' else 'authorization'),
                'private-reference-kind-does-not-grant-this-scope')
        value = load_reference(reference['path'], reference['sha256'])
        require(type(value) is dict and (expected_kind is None or value.get('kind') == expected_kind),
                'independently-installed-private-scope-required')
        return value

    def protected_roots(self):
        reference = self.binding.reference('protected-roots')
        require(reference['kind'] == 'source-manifest', 'independent-protected-root-manifest-required')
        value = load_reference(reference['path'], reference['sha256'])
        require(set(value) == {'kind','roots'} and value['kind'] == 'lab-protected-roots/1'
                and type(value['roots']) is list and bool(value['roots']), 'protected-roots-missing')
        roots = [Path(name) for name in value['roots']]
        require(all(p.is_absolute() and '..' not in p.parts and str(p) == name
                    for p,name in zip(roots,value['roots'])), 'invalid-protected-root-data')
        return roots

    def permission_profile_id(self):
        value = self.private_authorization('permissions-profile', 'native-permission-profile/1')
        require(set(value) == {'kind','id'} and type(value['id']) is str
                and 0 < len(value['id']) <= 128 and not value['id'].startswith(':'),
                'explicit-named-private-permission-profile-required')
        return value['id']
