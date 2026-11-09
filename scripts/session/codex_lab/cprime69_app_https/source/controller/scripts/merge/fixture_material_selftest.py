"""Material-layer refusal tests. No test execution during authoring."""
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from fixture_material import verify_locked_material, advance_verified_seed

COMMIT = 'a' * 40


class FixtureMaterialTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name).resolve()
        self.files = {'old/launcher.py': b'old source\n', 'new/launcher.py': b'new source\n',
                      'raw/run.json': b'{"id":700,"run_attempt":2}\n',
                      'raw/proof.zip': b'synthetic raw ZIP envelope placeholder',
                      'raw/reviews.json': b'[{"id":501},{"id":502}]\n'}
        entries = []
        for path, data in self.files.items():
            phase, target = path.split('/', 1)
            entries.append(dict(phase=phase, source_commit=COMMIT, source_path=path,
                target_path=target, mode='100644', sha256=hashlib.sha256(data).hexdigest(),
                blob=hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()))
        self.inventory = {(e['phase'], e['target_path']) for e in entries}
        self.keys = {'run', 'zip', 'reviews'}
        self.scope = dict(repository_id=1449415666, run=700, attempt=2)
        ledger = {key: dict(path=path.split('/', 1)[1], sha256=hashlib.sha256(self.files[path]).hexdigest(),
                           scope=dict(self.scope)) for key, path in
                  [('run', 'raw/run.json'), ('zip', 'raw/proof.zip'), ('reviews', 'raw/reviews.json')]}
        self.spec = dict(version=1, entries=entries, ledger=ledger)

    def read(self, commit, path):
        self.assertEqual(COMMIT, commit)
        return self.files[path]

    def verify(self, spec=None, reader=None):
        raw = json.dumps(self.spec if spec is None else spec, sort_keys=True).encode()
        return verify_locked_material(raw, hashlib.sha256(raw).hexdigest(),
                                      self.inventory, self.keys, reader or self.read)

    def test_exact_material_seed_and_raw_reads_are_test_observable(self):
        material = self.verify()
        self.assertEqual(('launcher.py',), material.seed(self.root, 'old'))
        self.assertEqual(b'old source\n', (self.root / 'launcher.py').read_bytes())
        ledger = material.raw_ledger()
        for key in self.keys:
            self.assertIsInstance(ledger.read(key, self.scope), bytes)
        self.assertEqual(self.keys, set(ledger.require_complete(self.keys)))
        material.check_scratch_commit('old', lambda path: b'old source\n')
        advance_verified_seed(material, self.root)
        self.assertEqual(b'new source\n', (self.root / 'launcher.py').read_bytes())

    def test_wrong_manifest_or_source_sha_refuses_before_seed(self):
        with self.assertRaisesRegex(AssertionError, 'MATERIAL_MANIFEST_SHA_MISMATCH'):
            verify_locked_material(b'{}', '0' * 64, self.inventory, self.keys, self.read)
        with self.assertRaisesRegex(AssertionError, 'MATERIAL_SOURCE_SHA_MISMATCH'):
            self.verify(reader=lambda commit, path: self.files[path] + b'changed')
        self.assertEqual([], list(self.root.iterdir()))

    def test_omitted_inventory_or_raw_collector_record_refuses(self):
        for mutation in ('source', 'raw'):
            with self.subTest(mutation=mutation):
                spec = copy.deepcopy(self.spec)
                if mutation == 'source': spec['entries'].pop(0)
                else: del spec['ledger']['reviews']
                with self.assertRaises(AssertionError):
                    self.verify(spec)
                self.assertEqual([], list(self.root.iterdir()))

    def test_not_in_exact_scratch_commit_or_wrong_attempt_is_not_evidence(self):
        material = self.verify()
        with self.assertRaisesRegex(AssertionError, 'MATERIAL_NOT_IN_EXACT_SCRATCH_COMMIT'):
            material.check_scratch_commit('new', lambda path: b'not committed source')
        ledger = material.raw_ledger()
        with self.assertRaisesRegex(AssertionError, 'RAW_WRONG_SUBJECT_RUN_ATTEMPT'):
            ledger.read('run', dict(self.scope, attempt=1))
        with self.assertRaisesRegex(AssertionError, 'RAW_COLLECTOR_NOT_ACTUALLY_READ'):
            ledger.require_complete(self.keys)


if __name__ == '__main__':
    unittest.main(verbosity=2)
