"""Card30 exact adoption: synthetic ports, real public functions and Store.
No network/native/Git transport is invoked by this suite. Hosted execution only.
The approval object is an acceptance fixture, never installed authorization.
"""
import argparse
import copy
import hashlib
import importlib
import json
import os
from pathlib import Path
import sqlite3
import sys
import threading
import unittest
from unittest.mock import patch

PACKAGE = Path(__file__).resolve().parent
BASELINE_PIN = '05c01a3ea6a867b151066d14374e7d4bba8079f2a01f9f9e7c9d8fc5abe887df'
N = None
S = None


def wire(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), allow_nan=False)


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


class Acceptance(unittest.TestCase):
    def scope(self):
        return json.loads((PACKAGE / 'scope-fixture.json').read_text())['scope']

    def database(self):
        db = sqlite3.connect(':memory:', isolation_level=None)
        db.row_factory = sqlite3.Row
        self.addCleanup(db.close)
        db.executescript('''
        CREATE TABLE owner(id INTEGER PRIMARY KEY,generation INTEGER,client TEXT,token_sha TEXT,paused INTEGER);
        CREATE TABLE tasks(revision INTEGER PRIMARY KEY,body TEXT,phase TEXT,capability_epoch INTEGER,fence_seq INTEGER);
        CREATE TABLE turns(turn_id TEXT PRIMARY KEY,revision INTEGER,owner_generation INTEGER,epoch INTEGER,state TEXT);
        CREATE TABLE effects(effect_id TEXT PRIMARY KEY,revision INTEGER,generation INTEGER,operation_key TEXT,kind TEXT,target TEXT,phase TEXT,issue_count INTEGER,observation TEXT,advances INTEGER);
        CREATE TABLE native_dispatch(effect_id TEXT PRIMARY KEY,session_id TEXT,revision INTEGER,generation INTEGER,epoch INTEGER,prompt_sha256 TEXT,phase TEXT,connection_id TEXT,turn_id TEXT);
        CREATE TABLE role_registration(id INTEGER PRIMARY KEY,effect_id TEXT,phase TEXT,generation INTEGER,session_id TEXT,first_turn_id TEXT,readback_sha256 TEXT);
        CREATE TABLE events(seq INTEGER PRIMARY KEY AUTOINCREMENT,kind TEXT,body TEXT);
        ''')
        return db

    def fixture(self):
        sc = self.scope()
        db = self.database()
        store = S.Store.__new__(S.Store)
        store.db, store.mutex = db, threading.RLock()
        sid = '33333333-3333-4333-8333-333333333333'
        auth = dict(client='synthetic-owner', generation=1, token='synthetic-token')
        db.execute('INSERT INTO owner VALUES(1,1,?,?,1)',
                   (auth['client'], sha(auth['token'].encode())))
        candidate = json.loads((PACKAGE / 'candidate-observed.json').read_text())['candidate']
        body = dict(card=30, revision=1, session_id=sid, B=sc['new_base'], H=candidate)
        db.execute("INSERT INTO tasks VALUES(1,?,'active',1,NULL)", (wire(body),))
        db.execute("INSERT INTO turns VALUES('synthetic-turn',1,1,1,'terminal')")
        db.execute("INSERT INTO role_registration VALUES(1,'registration','stable',1,?,'synthetic-turn',NULL)", (sid,))
        db.execute("INSERT INTO effects VALUES('native-effect',1,1,'native-operation','native-turn','{}','observed',1,'{}',0)")
        db.execute("INSERT INTO native_dispatch VALUES('native-effect',?,1,1,1,?,'observed','synthetic-connection','synthetic-turn')", (sid, sha(b'synthetic-prompt')))
        cfg = dict(candidate=candidate, successor_session=sid, revision=1)
        return store, auth, sc, cfg

    def port(self, sc, cfg):
        port = N.AdoptionPort.__new__(N.AdoptionPort)
        port.calls = []
        issue_body = 'Synthetic card30 approved scope fixture.\n'
        pr_body = f"Refs #30\nCodex: {sc['author_session']}\nCodex-Acceptance: {sc['acceptance_session']}\n"
        cfg.update(issue_body_sha256=sha(issue_body.encode()), pr_body_sha256=sha(pr_body.encode()))
        row = dict(number=32, head=dict(ref=sc['head_ref'], sha=sc['old_head']),
                   base=dict(ref='main', sha=sc['old_base']), state='open', draft=False, body=pr_body)
        def repo():
            port.calls.append(('repo',))
            return dict(id=1449415666, full_name=sc['repository'], default_branch='main')
        def api(path):
            port.calls.append(('api', path))
            self.assertEqual(path, '/issues/30')
            return dict(number=30, state='open', body=issue_body)
        def pull(number):
            port.calls.append(('pull', number))
            self.assertEqual(number, 32)
            return copy.deepcopy(row)
        def ref(branch):
            port.calls.append(('ref', branch))
            self.assertEqual(branch, 'lab/30-facts-f1')
            return sc['old_head']
        port.repo, port.api, port.pull, port.ref = repo, api, pull, ref
        port.s = dict(branch=sc['head_ref'], expected=sc['old_head'], head=cfg['candidate'])
        def git(*args):
            port.calls.append(('git', args))
            if args == ('show', '-s', '--format=%P', cfg['candidate']):
                return ' '.join(json.loads((PACKAGE / 'candidate-observed.json').read_text())['parents'])
            if args and args[0] == 'push':
                self.fail('UNAPPROVED_UPDATE_REACHED_SEND')
            self.fail('UNEXPECTED_GIT_VOCABULARY:' + repr(args))
        port.git = git
        return port

    def rows(self, db):
        tables = [r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")]
        return {name: [tuple(r) for r in db.execute('SELECT * FROM "' + name + '" ORDER BY rowid')] for name in tables}

    def test_01_exact_approved_lab_tuple_uses_public_observe_without_send(self):
        store, auth, sc, cfg = self.fixture()
        port = self.port(sc, cfg)
        before = [tuple(r) for r in store.db.execute('SELECT * FROM effects')]
        try:
            result = N.advance(store, auth, 'adoption.observe', sc, cfg, port)
        except ValueError as error:
            self.fail('CARD30_EXACT_APPROVED_LAB_OBSERVE_REJECTED:' + str(error))
        self.assertEqual(result, dict(action='adopted', number=32))
        self.assertEqual(before, [tuple(r) for r in store.db.execute('SELECT * FROM effects')])
        self.assertEqual([r[0] for r in port.calls], ['repo', 'api', 'pull', 'ref'])

    def test_02_exact_approved_registration_is_idempotent(self):
        db, sc = self.database(), self.scope()
        remote = dict(copy.deepcopy(sc), state='open', draft=False)
        try:
            first = N.observe(db, sc, remote)
        except ValueError as error:
            self.fail('CARD30_EXACT_APPROVED_REGISTRATION_REJECTED:' + str(error))
        before = self.rows(db)
        self.assertEqual(N.observe(db, sc, remote), first)
        self.assertEqual(first, dict(action='adopted', number=32))
        self.assertEqual(before, self.rows(db))
        self.assertEqual(db.execute('SELECT count(*) FROM adopted_pr').fetchone()[0], 1)
        self.assertEqual(db.execute('SELECT count(*) FROM effects').fetchone()[0], 0)

    def test_03_no_prefix_authority_or_approval_fallback(self):
        mutations = dict(repo_id=1, repository='other/repo', card=31, number=33,
                         head_ref='lab/31-other', old_head='e'*40, old_base='e'*40,
                         new_base='e'*40, author_session='44444444-4444-4444-8444-444444444444',
                         acceptance_session='55555555-5555-4555-8555-555555555555',
                         acceptance_sha256='e'*64, scope_sha256='e'*64)
        for field, value in mutations.items():
            with self.subTest(field=field):
                sc, db = self.scope(), self.database()
                sc[field] = value
                with self.assertRaises(ValueError):
                    N.observe(db, sc, dict(copy.deepcopy(sc), state='open', draft=False))
        for mutation in ('missing', 'wrong-kind', 'wrong-contribution', 'wrong-tests', 'codex-escape', 'other-lab'):
            with self.subTest(approval=mutation):
                sc, db = self.scope(), self.database()
                if mutation == 'missing':del sc['approval']
                elif mutation == 'wrong-kind':sc['approval']['kind'] = 'prefix-allowance'
                elif mutation == 'wrong-contribution':sc['approval']['contribution_sha256'] = '0'*64
                elif mutation == 'wrong-tests':sc['approval']['acceptance_manifest_sha256'] = '0'*64
                elif mutation == 'codex-escape':sc['head_ref'] = 'codex/30-other'
                else:sc['head_ref'] = 'lab/30-facts-f1-extra'
                with self.assertRaises(ValueError):
                    N.observe(db, sc, dict(copy.deepcopy(sc), state='open', draft=False))

    def test_04_remote_drift_does_not_register(self):
        for field in ('repo_id', 'card', 'number', 'head_ref', 'old_head', 'old_base', 'author_session', 'acceptance_session', 'acceptance_sha256'):
            with self.subTest(field=field):
                sc, db = self.scope(), self.database()
                remote = dict(copy.deepcopy(sc), state='open', draft=False)
                remote[field] = remote[field]+1 if type(remote[field]) is int else 'wrong'
                with self.assertRaises(ValueError):N.observe(db, sc, remote)
                exists = db.execute("SELECT 1 FROM sqlite_master WHERE name='adopted_pr'").fetchone()
                self.assertFalse(exists and db.execute('SELECT count(*) FROM adopted_pr').fetchone()[0])

    def test_05_legacy_codex_observation_remains_compatible(self):
        sc, db = self.scope(), self.database()
        del sc['approval']
        sc['head_ref'] = 'codex/synthetic-legacy'
        self.assertEqual(N.observe(db, sc, dict(sc, state='open', draft=False)), dict(action='adopted', number=32))

    def test_06_real_ref_update_preserves_exact_single_ref_lease(self):
        store, auth, sc, cfg = self.fixture()
        port = self.port(sc, cfg)
        calls = []
        port.git = lambda *args: calls.append(args)
        port.update_ref(sc['head_ref'], sc['old_head'], cfg['candidate'])
        ref = 'refs/heads/lab/30-facts-f1'
        self.assertEqual(calls, [('push', '--porcelain', '--no-follow-tags', '--recurse-submodules=no',
                                  '--force-with-lease='+ref+':'+sc['old_head'], 'origin', cfg['candidate']+':'+ref)])
        for branch, old, new in (('lab/31-other', sc['old_head'], cfg['candidate']),
                                 (sc['head_ref'], 'e'*40, cfg['candidate']),
                                 (sc['head_ref'], sc['old_head'], 'e'*40)):
            with self.subTest(branch=branch, old=old, new=new):
                before = len(calls)
                with self.assertRaises(ValueError):port.update_ref(branch, old, new)
                self.assertEqual(len(calls), before)

    def test_07_actual_unsigned_candidate_delta_cannot_reach_send(self):
        store, auth, sc, cfg = self.fixture()
        # Synthetic persisted adoption receipt; never installed into a real Store.
        store.db.execute('CREATE TABLE adopted_pr(repo_id INTEGER,card INTEGER,number INTEGER,head_ref TEXT,kind TEXT,subject TEXT)')
        store.db.execute('INSERT INTO adopted_pr VALUES(?,?,?,?,?,?)', (sc['repo_id'],30,32,sc['head_ref'],N.RECORD_KIND,wire(sc)))
        port = self.port(sc, cfg)
        original = json.loads((PACKAGE/'original-contribution.json').read_text())['original_diff']
        observed = json.loads((PACKAGE/'candidate-observed.json').read_text())
        def diff(base, head):
            port.calls.append(('diff', base, head))
            if (base,head)==(sc['old_base'],sc['old_head']):return copy.deepcopy(original)
            if (base,head)==(sc['new_base'],cfg['candidate']):return copy.deepcopy(observed['candidate_diff'])
            self.fail('UNEXPECTED_DIFF_SCOPE')
        port.diff = diff
        port.cwd = Path('/synthetic-no-git-execution')
        port.git_env = {}
        test_path = json.loads((PACKAGE/'scope-fixture.json').read_text())['test_path']
        cfg.update(files=[r['path'] for r in original['files']], test_path=test_path)
        # Preserve-test guard is deliberately satisfied to test the later delta
        # guard independently. This is a synthetic transport response, NOT proof
        # that the real candidate contains the old path or frozen bytes.
        def frozen_show(argv, *args, **kwargs):
            self.assertEqual(argv, ['/usr/bin/git','--no-replace-objects','show',cfg['candidate']+':'+test_path])
            return (PACKAGE/'original-tests'/Path(test_path).name).read_bytes()
        before = self.rows(store.db)
        with patch.object(N, 'run', frozen_show):
            with self.assertRaises(ValueError):
                N.advance(store, auth, 'adoption.update', sc, cfg, port)
        self.assertEqual(before, self.rows(store.db))
        self.assertFalse(any(call[0]=='git' and call[1][0]=='push' for call in port.calls))


def main():
    global N, S
    p = argparse.ArgumentParser()
    p.add_argument('--source-root', type=Path, default=PACKAGE/'baseline-source')
    p.add_argument('--source-closure-sha256', default=BASELINE_PIN)
    args = p.parse_args()
    root = args.source_root.resolve(strict=True)
    raw = (root/'SOURCE-CLOSURE.json').read_bytes()
    if sha(raw) != args.source_closure_sha256:raise SystemExit('HOLD: source closure pin mismatch')
    for name, pin in json.loads(raw)['files_sha256'].items():
        source = (root/name).resolve(strict=True)
        source.relative_to(root)
        if sha(source.read_bytes()) != pin:raise SystemExit('HOLD: dependency pin mismatch')
    sys.path.insert(0, str(root))
    N = importlib.import_module('existing_pr_adoption')
    S = importlib.import_module('runtime_store')
    result = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(Acceptance))
    return 86 if result.errors or result.skipped else (0 if result.wasSuccessful() else 1)


if __name__ == '__main__':
    raise SystemExit(main())
