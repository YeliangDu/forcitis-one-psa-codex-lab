"""Test-owned setup hooks BEFORE O/H commits; no candidate result trust input."""
import hashlib
import importlib.util
from pathlib import Path

BUILDER_SHA256 = '12ece1397ad005096079bfd9ecc660788b197c29fa68669acb054f6a21a6db13'


def need(ok, reason):
    if not ok:
        raise AssertionError(reason)


def reviewed_builder(path):
    path = Path(path)
    need(hashlib.sha256(path.read_bytes()).hexdigest() == BUILDER_SHA256,
         'E2E_CANONICAL_MANIFEST_BUILDER_CHANGED')
    spec = importlib.util.spec_from_file_location('test_owned_canonical_manifest', path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.build_manifest


def canonical_phase(material, phase, build_manifest):
    selected = [e for e in material.entries if e['phase'] == phase]
    paths = [e['target_path'] for e in selected]
    rows = [dict(path=e['target_path'], mode=e['mode'], blob=e['blob'],
                 content=material.payloads[(phase, e['target_path'])]) for e in selected]
    return build_manifest(paths, rows)


def seed_before_old_commit(test, material, build_manifest):
    # material must ALREADY be verified by test-owned externally fixed inventory
    # + raw SHA/Git-object manifest. No auto-discovery or result-selected paths.
    need(material is not None, 'E2E_LOCKED_SOURCE_INVENTORY_UNBOUND')
    material.seed(test.root, 'old')
    test.git('add', '-A')
    return canonical_phase(material, 'old', build_manifest)


def seed_before_candidate_commit(test, material, build_manifest):
    from fixture_material import advance_verified_seed
    need(material is not None, 'E2E_LOCKED_SOURCE_INVENTORY_UNBOUND')
    advance_verified_seed(material, test.root)
    test.git('add', '-A')
    return canonical_phase(material, 'new', build_manifest)


def verify_after_commit(test, material, phase, commit, expected_manifest, build_manifest):
    import subprocess
    def read(path):
        result = subprocess.run(['git', '--no-replace-objects', '-C', str(test.root),
                                 'show', commit + ':' + path], env=test.env,
                                capture_output=True, timeout=10)
        need(result.returncode == 0, 'E2E_SEEDED_OBJECT_MISSING_FROM_EXACT_COMMIT')
        return result.stdout
    material.check_scratch_commit(phase, read)
    selected = [e for e in material.entries if e['phase'] == phase]
    rows = []
    for entry in selected:
        data = read(entry['target_path'])
        rows.append(dict(path=entry['target_path'], mode=entry['mode'],
                         blob=hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest(), content=data))
    actual = build_manifest([row['path'] for row in rows], rows)
    need(actual == expected_manifest, 'E2E_EXACT_COMMIT_CANONICAL_MANIFEST_CHANGED')
    return actual
