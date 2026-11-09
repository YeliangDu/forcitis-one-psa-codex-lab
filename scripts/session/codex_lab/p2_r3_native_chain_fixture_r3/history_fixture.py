"""Hosted-only already-V2 storage/native-recovery subacceptance.

Synthetic policy/GET rows test validation only, never authenticate native facts.
The local CAS is fixture setup, NOT an authorized production-native send.
"""
import copy
import hashlib
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import sys
import tempfile
import unittest
import uuid

REPO = 'YeliangDu/forcitis-one-psa-codex-lab'
RID = 1449415666

def sha(label, width=40):
    return hashlib.sha256(label.encode()).hexdigest()[:width]


def approval(number, base, head):
    return dict(version=1, kind='control-upgrade', upgrade_id=str(uuid.uuid5(uuid.NAMESPACE_URL, 'synthetic-second-upgrade-' + str(number))),
                repository=REPO, repository_id=RID, number=number, base=base, head=head, target='main',
                old_manifest=sha(str(number) + '-old', 64), new_manifest=sha(str(number) + '-new', 64),
                author='one-platform', proof=dict(source_commit=sha(str(number) + '-independent'),
                    workflow_path='.github/workflows/synthetic-independent.yml', workflow_blob=sha('workflow-' + str(number)),
                    run_id=1000 + number, attempt=1, artifact_id=2000 + number, sha256=sha('artifact-' + str(number), 64)),
                reviews={kind: dict(id=3000 + number * 2 + index, reviewer='codex-' + kind + '-' + role,
                                    head=head, verdict='pass')
                         for index, (kind, role) in enumerate([('首轮', 'one-design'), ('终审', 'one-test')])})


def readback(a, merge):
    return dict(repository=REPO, repository_id=RID, number=a['number'], base=a['base'], head=a['head'], target='main',
                merged=True, merge_sha=merge, merge_parents=[a['base'], a['head']], target_tip=merge, target_contains_merge=True)


def synthetic_health(a, merge):
    # Initial fixture state only: this is normalized storage data, not signed/native proof.
    return dict(repository=REPO, repository_id=RID, upgrade_id=a['upgrade_id'], number=a['number'],
                base=a['base'], head=a['head'], target='main', merge_sha=merge, main_before=merge, main_after=merge,
                loaded_commit=merge, manifest_evidence=dict(old_manifest=a['old_manifest'], new_manifest=a['new_manifest'],
                    loaded_manifest=a['new_manifest'], source_commit=a['proof']['source_commit'], workflow_blob=a['proof']['workflow_blob']),
                health=dict(run=dict(id=7001, run_attempt=1, event='push', head_branch='main', head_sha=merge,
                    path='.github/workflows/lab-pr-ci.yml', status='completed', conclusion='success',
                    repository=dict(id=RID, full_name=REPO, private=False)), latest_run_id=7001, latest_attempt=1,
                    job=dict(id=7002, name='Lab main health', head_sha=merge, conclusion='success',
                             runner_group_name='GitHub Actions', labels=['ubuntu-24.04']),
                    artifact=dict(id=7003, name='lab-main-health-' + merge + '-1', expired=False,
                                  workflow_run=dict(id=7001, head_sha=merge, repository_id=RID)),
                    payload=dict(kind='main-health', event='push', head_sha=merge, observed_head=merge,
                                 run_id=7001, run_attempt=1, healthy=True, checks=dict(pin='success', light='success', secret='success'))))


class SecondNativeUpgradeSubAcceptance(unittest.TestCase):
    def setUp(self):
        root = Path(os.environ['EXACT_B_SOURCE_ROOT']).resolve(strict=True)
        sys.path.insert(0, str(root / 'scripts/merge'))
        import mq_lab_cutover_native as native
        import mq_lab_journal_v2 as storage
        from mq_lab_journal import ReleaseJournal
        self.native, self.storage = native, storage
        self.tmp = tempfile.TemporaryDirectory(prefix='second-native-')
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name).resolve()
        self.env = dict(PATH='/usr/bin:/bin', HOME=str(self.root), LANG='C.UTF-8', GIT_CONFIG_NOSYSTEM='1',
                        GIT_CONFIG_GLOBAL='/dev/null', GIT_TERMINAL_PROMPT='0', GIT_ALLOW_PROTOCOL='file',
                        GIT_AUTHOR_NAME='Fixture', GIT_AUTHOR_EMAIL='fixture@invalid',
                        GIT_COMMITTER_NAME='Fixture', GIT_COMMITTER_EMAIL='fixture@invalid')
        self.repo = self.root / 'forcitis-one-psa-codex-lab'
        self.git('init', str(self.repo))
        self.common = self.repo / '.git'
        self.directory = self.common / 'merge-queue'; self.directory.mkdir(mode=0o700)
        self.path = self.directory / 'releases.sqlite3'
        v1 = ReleaseJournal(str(self.path))
        for n in range(1, 10):
            record = dict(number=n, status='reserved', base=sha('ordinary-base-' + str(n)),
                          head=sha('ordinary-head-' + str(n)), target='main', release_key='synthetic-ordinary-' + str(n))
            self.assertTrue(v1.reserve(record))
            self.assertTrue(v1.confirm(dict(record, status='confirmed', merge_sha=sha('ordinary-merge-' + str(n)))))
        digest = hashlib.sha256(self.path.read_bytes()).hexdigest()
        self.assertEqual(self.storage.migrate_v1(str(self.path), str(self.directory / 'v1.backup'), digest)['action'], 'migrated')
        journal = self.storage.ReleaseJournalV2(self.path)
        self.old = approval(43, sha('old-base'), sha('old-head'))
        old_merge = sha('old-merge')
        self.assertEqual(journal.reserve_upgrade(self.old)['action'], 'newly-reserved')
        self.assertEqual(journal.confirm_upgrade(self.old, readback(self.old, old_merge))['action'], 'confirmed')
        self.assertEqual(journal.adopt_public_upgrade(self.old, synthetic_health(self.old, old_merge))['action'], 'adopted')
        tree = self.git('-C', str(self.repo), 'mktree')
        base = self.git('-C', str(self.repo), 'commit-tree', tree, '-m', 'base')
        head = self.git('-C', str(self.repo), 'commit-tree', tree, '-p', base, '-m', 'head')
        self.merge = self.git('-C', str(self.repo), 'commit-tree', tree, '-p', base, '-p', head, '-m', 'merge')
        self.tree = tree; self.new = approval(56, base, head)
        self.git('-C', str(self.repo), 'update-ref', 'refs/heads/main', base)
        for name, content in [('merge.queue', '56\n'), ('merge.queue.heads', '56 ' + head + '\n'),
                              ('merge.queue.lock', ''), ('merge.queue.merge-lock', ''),
                              ('merge.queue.heads-lock', ''), ('control-upgrade.runner-lock', '')]:
            p = self.directory / name; p.write_text(content); p.chmod(0o600)
        upgrades = self.directory / 'upgrades'; upgrades.mkdir(mode=0o700)
        for a in (self.old, self.new):
            p = upgrades / (a['upgrade_id'] + '.json'); p.write_text(json.dumps(a)); p.chmod(0o600)
        self.inodes = {p.name: (p.stat().st_dev, p.stat().st_ino) for p in self.directory.iterdir() if 'lock' in p.name}
        self.history = self.snapshot()
        self.native_argv = []; self.gets = []; self.cas_count = 0; self.wrong_head = False

    def git(self, *argv):
        return subprocess.check_output(['/usr/bin/git', '-c', 'core.hooksPath=/dev/null', *argv],
                                       env=self.env, input='', text=True, timeout=10).strip()

    def snapshot(self):
        with sqlite3.connect(self.path) as db:
            return (db.execute('select * from releases order by number').fetchall(),
                    db.execute('select * from upgrade_bindings order by number').fetchall())

    def assert_history(self):
        rows, bindings = self.snapshot()
        self.assertEqual([row for row in rows if row[0] != 56], self.history[0])
        self.assertEqual([row for row in bindings if row[1] != 56], self.history[1])
        self.assertEqual({p.name: (p.stat().st_dev, p.stat().st_ino) for p in self.directory.iterdir() if 'lock' in p.name}, self.inodes)
        with sqlite3.connect(self.path) as db:
            self.assertEqual(db.execute('pragma user_version').fetchone(), (2,))

    def policy(self, a):
        return dict(repository=REPO, repository_id=RID, expected_approval=copy.deepcopy(a), installed_source_commit=a['base'],
                    independent_proof_source=a['proof']['source_commit'], independent_proof_workflow=a['proof']['workflow_path'],
                    independent_proof_workflow_blob=a['proof']['workflow_blob'], old_manifest=a['old_manifest'], new_manifest=a['new_manifest'])

    def get(self, path, pages=False, binary=False):
        self.gets.append(path)
        repo = dict(id=RID, full_name=REPO, private=False, visibility='public', fork=False)
        if path == '':
            return repo
        tip = self.git('-C', str(self.repo), 'rev-parse', 'refs/heads/main')
        merged = tip == self.merge
        if path == 'pulls/56':
            row = dict(number=56, state='closed' if merged else 'open', draft=False, merged=merged,
                       head=dict(sha=sha('wrong-head') if self.wrong_head else self.new['head'], repo=repo),
                       base=dict(sha=self.new['base'], ref='main', repo=repo))
            if merged:
                row['merge_commit_sha'] = tip
            return row
        if path == 'git/ref/heads/main':
            return dict(ref='refs/heads/main', object=dict(sha=tip, type='commit'))
        if path == 'commits/' + self.merge:
            parents = self.git('-C', str(self.repo), 'show', '-s', '--format=%P', self.merge).split()
            return dict(sha=self.merge, parents=[dict(sha=p) for p in parents])
        raise ValueError('CAPABILITY_HOLD: no independently authenticated successor health/source fixture: ' + path)

    def no_native_write(self, argv):
        self.native_argv.append(list(argv))
        raise AssertionError('native rejection/recovery must not invoke Git argv transport')

    def native_run(self, policy):
        return self.native.run_native_upgrade(56, self.common, policy, self.get, self.no_native_write)

    def test_native_old_policy_or_wrong_head_cannot_reserve_or_send(self):
        self.assertEqual(self.native_run(self.policy(self.old))['action'], 'hold')
        self.assertEqual(self.snapshot(), self.history)
        self.wrong_head = True
        self.assertEqual(self.native_run(self.policy(self.new))['action'], 'hold')
        self.assertIn('pulls/56', self.gets, 'wrong-head rejection must reach the native subject check')
        self.assertEqual(self.snapshot(), self.history)
        self.assertEqual(self.native_argv, [])
        self.assertEqual(self.cas_count, 0)
        self.assert_history()

    def test_existing_v2_second_pair_exact_local_cas_lost_reply_native_recovery_no_resend(self):
        # Storage subacceptance only: no claim this reserve is native authorization.
        journal = self.storage.ReleaseJournalV2(self.path)
        self.assertEqual(journal.reserve_upgrade(self.new)['action'], 'newly-reserved')
        self.assert_history()
        try:
            self.cas_count += 1
            self.git('-C', str(self.repo), 'update-ref', 'refs/heads/main', self.merge, self.new['base'])
            raise TimeoutError('synthetic lost reply after actual disposable CAS')
        except TimeoutError:
            pass
        self.assertEqual(self.git('-C', str(self.repo), 'show', '-s', '--format=%T', self.merge), self.tree)
        # New entry/journal instances recover existing pair; absent policy cannot authorize send.
        (self.directory / 'upgrades' / (self.new['upgrade_id'] + '.json')).unlink()
        for _ in range(2):
            self.assertEqual(self.native_run({})['action'], 'hold')
            recovered = self.storage.ReleaseJournalV2(self.path).load_upgrade(56)
            self.assertEqual(recovered['phase'], 'confirmed')
            self.assertEqual(recovered['merge_sha'], self.merge)
            self.assert_history()
        self.assertEqual(journal.reserve_upgrade(self.new)['action'], 'recovery-only')
        self.assertIn('commits/' + self.merge, self.gets)
        self.assertEqual(self.native_argv, [])
        self.assertEqual(self.cas_count, 1)
        self.assertEqual(journal.load_upgrade(43)['phase'], 'adopted')
        self.assert_history()

    def test_conflicting_old_uuid_or_changed_new_subject_cannot_replace_history(self):
        journal = self.storage.ReleaseJournalV2(self.path)
        bad = copy.deepcopy(self.new); bad['upgrade_id'] = self.old['upgrade_id']
        self.assertEqual(journal.reserve_upgrade(bad)['action'], 'hold')
        self.assertEqual(self.snapshot(), self.history)
        self.assertEqual(journal.reserve_upgrade(self.new)['action'], 'newly-reserved')
        before = self.snapshot()
        changed = copy.deepcopy(self.new); changed['head'] = sha('changed-new-head')
        for review in changed['reviews'].values():
            review['head'] = changed['head']
        self.assertEqual(journal.reserve_upgrade(changed)['action'], 'hold')
        self.assertEqual(self.snapshot(), before)
        self.assert_history()


if __name__ == '__main__':
    unittest.main(verbosity=2)
