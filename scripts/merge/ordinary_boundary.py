"""Separate test-owned ordinary CAS; never routes to protected HostProbe.cas."""
import copy
import json
import sqlite3
import subprocess
import sys


def need(value, reason):
    if not value:
        raise AssertionError(reason)


class OrdinaryBoundary:
    def __init__(self, test, exact_intent, merge, independent_normal_readback):
        self.test, self.intent, self.merge = test, copy.deepcopy(exact_intent), merge
        self.normal = independent_normal_readback
        self.attempts = 0

    def cas(self, intent, expected_base):
        need(type(intent) is dict and intent == self.intent and expected_base == intent['base'],
             'ORDINARY_CAS_INTENT_OR_LEASE_MOVED')
        need(type(self.normal) is VerifiedNormalVerifier, 'ORDINARY_REAL_NORMAL_PROOF_BINDING_UNBOUND')
        # Callback is test-owned and must run reviewed normal verifier over raw
        # ledger, returning full exact proof; candidate boolean/eligible flag fails.
        proof = self.normal(copy.deepcopy(intent))
        need(type(proof) is dict and proof.get('action') == 'eligible'
             and all(type(proof.get(k)) is type(v) and proof[k] == v for k, v in intent.items())
             and isinstance(proof.get('release_key'), str) and proof['release_key'],
             'ORDINARY_FAKE_ELIGIBLE_NOT_NORMAL_PROOF')
        with sqlite3.connect(self.test.dbpath) as db:
            need(db.execute('select phase from upgrade_bindings').fetchall() == [('adopted',)],
                 'ORDINARY_BEFORE_EXACT_ADOPTION')
            need(db.execute('select status,mode,base,head,release_key from releases where number=?',
                 (intent['number'],)).fetchone() == ('reserved', 'ordinary', intent['base'], intent['head'], proof['release_key']),
                 'ORDINARY_RESERVATION_NOT_COMMITTED_EXACT_PROOF')
        script = "import fcntl,sys\nf=open(sys.argv[1],'r+b')\ntry: fcntl.flock(f,fcntl.LOCK_EX|fcntl.LOCK_NB)\nexcept BlockingIOError: sys.exit(0)\nsys.exit(8)"
        lock = self.test.dbpath.parent / 'merge.queue.merge-lock'
        check = subprocess.run([sys.executable, '-B', '-c', script, str(lock)], capture_output=True, timeout=10)
        need(check.returncode == 0, 'ORDINARY_CANONICAL_OS_LOCK_NOT_HELD')
        need(self.test.git('show', '-s', '--format=%P', self.merge).split() == [intent['base'], intent['head']],
             'ORDINARY_EXACT_ORDERED_PARENTS_REQUIRED')
        self.attempts += 1
        need(self.attempts == 1, 'ORDINARY_SECOND_CAS_FORBIDDEN')
        self.test.git('update-ref', 'refs/heads/main', self.merge, expected_base)
        return dict(requested=True)

class VerifiedNormalVerifier:
    """Test-owned actual normal verifier loader, not arbitrary eligible callback."""
    def __init__(self, source_root, reviewed_source_sha256, approval, github, source_check):
        import hashlib
        import importlib.util
        from pathlib import Path
        import sys
        root = Path(source_root).resolve(strict=True)
        need('scripts/merge/mq_lab_proof.py' in reviewed_source_sha256, 'NORMAL_SOURCE_INVENTORY_UNBOUND')
        for relative, digest in reviewed_source_sha256.items():
            path = root / relative
            need(path.resolve(strict=True).is_relative_to(root) and not path.is_symlink()
                 and hashlib.sha256(path.read_bytes()).hexdigest() == digest, 'NORMAL_VERIFIER_SOURCE_CHANGED')
        name = root / 'scripts/merge/mq_lab_proof.py'
        before = sys.path[:]
        sys.path.insert(0, str(name.parent))
        try:
            spec = importlib.util.spec_from_file_location('test_owned_exact_normal_proof', name)
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
        finally:
            sys.path[:] = before
        self.verify, self.approval, self.github, self.source_check = module.verify_intent, copy.deepcopy(approval), github, source_check

    def __call__(self, intent):
        return self.verify(intent, copy.deepcopy(self.approval), self.github, self.source_check, None)
