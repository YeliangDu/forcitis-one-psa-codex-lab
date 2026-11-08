"""Minimal native-entry binding freeze. Fake fixed GET/argv only, no real GitHub."""
import copy
import gzip
import base64
import hashlib
import importlib.util
import json
from pathlib import Path
import sqlite3
import sys
import tempfile
import unittest

HERE=Path(__file__).resolve().parent
RID=1449415666
REPO='YeliangDu/forcitis-one-psa-codex-lab'
BASE,HEAD,MERGE,TREE=(c*40 for c in 'abcd')
# Test-owner/lead must sign and pin complete raw native proof before positive.
NATIVE_PROOF_FIXTURE_SHA256='cd6db9189ff9c227872338e3bd90d34568a81ba64f5030a9712857ad95dc5d59'
NATIVE_PROOF_GZIP_SHA256='50b53fe184037c180f80d8b350a7abb2cdd155d379234d6e8a4c615422d1ffe0'
NATIVE_PROOF_MAX_BYTES=16*1024*1024


def read_native_fixture_bytes():
    path=HERE/'native-proof-fixture.json.gz'
    if not path.is_file():raise AssertionError('INDEPENDENT_NATIVE_PROOF_FIXTURE_MISSING')
    with path.open('rb') as stream:
        compressed=stream.read(NATIVE_PROOF_MAX_BYTES+1)
    if len(compressed)>NATIVE_PROOF_MAX_BYTES:
        raise AssertionError('INDEPENDENT_NATIVE_PROOF_COMPRESSED_SIZE_EXCEEDED')
    if hashlib.sha256(compressed).hexdigest()!=NATIVE_PROOF_GZIP_SHA256:
        raise AssertionError('INDEPENDENT_NATIVE_PROOF_GZIP_BYTES_CHANGED')
    import io
    with gzip.GzipFile(fileobj=io.BytesIO(compressed),mode='rb') as stream:
        raw=stream.read(NATIVE_PROOF_MAX_BYTES+1)
    if len(raw)>NATIVE_PROOF_MAX_BYTES:
        raise AssertionError('INDEPENDENT_NATIVE_PROOF_UNCOMPRESSED_SIZE_EXCEEDED')
    if hashlib.sha256(raw).hexdigest()!=NATIVE_PROOF_FIXTURE_SHA256:
        raise AssertionError('INDEPENDENT_NATIVE_PROOF_FIXTURE_BYTES_CHANGED')
    return raw


def module(name,optional=False):
    path=HERE/(name+'.py')
    if not path.is_file() and name in ('lab_queue_git_writer','lab_queue_live','lab_main_health'):
        path=HERE.parent/'session/codex_lab'/(name+'.py')
    if optional and not path.exists():return None
    if not path.is_file():raise AssertionError('NATIVE_BINDING_DEPENDENCY_NOT_PACKAGED: '+name)
    if name in sys.modules:
        existing=sys.modules[name]
        if Path(getattr(existing,'__file__','')).resolve()!=path.resolve():
            raise AssertionError('NATIVE_BINDING_CACHED_SOURCE_NOT_EXACT: '+name)
        return existing
    spec=importlib.util.spec_from_file_location(name,path)
    value=importlib.util.module_from_spec(spec)
    before=sys.path[:];sys.path.insert(0,str(HERE))
    try:
        sys.modules[name]=value
        spec.loader.exec_module(value)
    finally:sys.path[:]=before
    return value


class NativeEntryAcceptanceTest(unittest.TestCase):
    def setUp(self):
        self.native=module('mq_lab_cutover_native',optional=True)
        self.assertTrue(callable(getattr(self.native,'run_native_upgrade',None)),
                        'EXACT_NATIVE_UPGRADE_BINDING_UNBOUND')
        self.storage=module('mq_lab_journal_v2')
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.common=Path(self.tmp.name).resolve()/'forcitis-one-psa-codex-lab/.git'
        self.directory=self.common/'merge-queue';self.directory.mkdir(parents=True,mode=0o700);self.directory.chmod(0o700)
        self.path=self.directory/'releases.sqlite3'
        with sqlite3.connect(self.path) as db:
            db.execute(self.storage.V1);db.execute(self.storage.ALTER);db.execute(self.storage.BINDINGS)
            db.execute('pragma user_version=2')
        self.path.chmod(0o600)
        for name,raw in [('merge.queue',b'42\n'),('merge.queue.heads',('42 '+HEAD+'\n').encode()),
                         ('merge.queue.merge-lock',b''),('merge.queue.lock',b''),
                         ('merge.queue.heads-lock',b''),('control-upgrade.runner-lock',b'')]:
            path=self.directory/name;path.write_bytes(raw);path.chmod(0o600)
        frozen_raw=read_native_fixture_bytes()
        self.assertEqual(NATIVE_PROOF_FIXTURE_SHA256,hashlib.sha256(frozen_raw).hexdigest(),
                         'INDEPENDENT_NATIVE_PROOF_FIXTURE_BYTES_CHANGED')
        frozen=json.loads(frozen_raw)
        self.approval=copy.deepcopy(frozen['approval'])
        folder=self.directory/'upgrades';folder.mkdir(mode=0o700)
        self.approval_path=folder/(self.approval['upgrade_id']+'.json')
        self.approval_path.write_text(json.dumps(self.approval));self.approval_path.chmod(0o600)
        self.policy=copy.deepcopy(frozen['policy_by_stage']['before'])
        self.current=BASE;self.pushes=[];self.reads=[];self.argv=[];self.unknown=False
        repo=dict(id=RID,full_name=REPO,private=False)
        self.pr=dict(number=42,state='open',draft=False,merged=False,
            head=dict(sha=HEAD,ref='codex/synthetic-cutover',repo=copy.deepcopy(repo)),
            base=dict(sha=BASE,ref='main',repo=copy.deepcopy(repo)))
        self.responses={'':repo,'pulls/42':self.pr,
            f'commits/{MERGE}':dict(sha=MERGE,parents=[dict(sha=BASE),dict(sha=HEAD)])}
        self.fresh=None
        self.signed_fixture=None
        self.native_stage='before'

    def get(self,path,pages=False,binary=False):
        self.reads.append(path)
        if self.signed_fixture is not None:
            key=json.dumps([path,pages,binary],separators=(',',':'))
            records=self.signed_fixture['raw_get'][self.native_stage]
            self.assertIn(key,records,'SIGNED_NATIVE_GET_RECORD_MISSING: '+key)
            item=records[key]
            if binary:
                self.assertEqual({'base64'},set(item))
                return base64.b64decode(item['base64'],validate=True)
            self.assertEqual({'json'},set(item))
            return copy.deepcopy(item['json'])
        if path=='git/ref/heads/main':return dict(ref='refs/heads/main',object=dict(sha=self.current,type='commit'))
        if path==f'actions/runs?event=push&branch=main&head_sha={MERGE}&per_page=100':
            rows=[] if self.fresh is None else [copy.deepcopy(self.fresh)]
            value=dict(total_count=len(rows),workflow_runs=rows)
            return [value] if pages else value
        if path in self.responses:return copy.deepcopy(self.responses[path])
        raise AssertionError('NATIVE_TEST_RAW_RECORD_NOT_SUPPLIED: '+path)

    def git(self,args):
        self.argv.append(list(args))
        if args==['remote','-v']:
            return f'origin\tgit@github.com:{REPO}.git (fetch)\norigin\tgit@github.com:{REPO}.git (push)\n'
        if args==['merge-tree','--write-tree',BASE,HEAD]:return TREE
        if args[:1]==['commit-tree']:return MERGE
        if args==['show','-s','--format=%P',MERGE]:return BASE+' '+HEAD
        if args==['merge-base','--is-ancestor',BASE,MERGE]:return ''
        if args[:1]==['push']:
            self.assertEqual(['push','--porcelain','--force-with-lease=refs/heads/main:'+BASE,'origin',MERGE+':refs/heads/main'],args)
            with sqlite3.connect(self.path) as db:
                self.assertEqual([('reserved','control-upgrade')],db.execute('select status,mode from releases').fetchall())
            self.pushes.append(list(args));self.current=MERGE
            if self.signed_fixture is not None:self.native_stage='merged-awaiting-health'
            self.pr.update(state='closed',merged=True,merge_commit_sha=MERGE)
            if self.unknown:raise TimeoutError('synthetic lost actual writer reply')
            return 'synthetic request accepted'
        if self.signed_fixture is not None:
            key=json.dumps(args,separators=(',',':'))
            records=self.signed_fixture['git_reads'][self.native_stage]
            self.assertIn(key,records,'SIGNED_NATIVE_GIT_READ_MISSING: '+key)
            self.assertNotIn(args[0],('push','update-ref','commit','merge','fetch','reset'),
                             'UNAPPROVED_NATIVE_WRITE_VOCABULARY')
            return records[key]
        raise AssertionError('NATIVE_TEST_UNEXPECTED_ARGV: '+repr(args))

    def run_native(self):
        # Proposed real binding; must delegate existing installer/V2/writer.
        return self.native.run_native_upgrade(42,self.common,copy.deepcopy(self.policy),self.get,self.git)

    def reserve_fixture(self):
        self.assertEqual('newly-reserved',self.storage.ReleaseJournalV2(self.path).reserve_upgrade(self.approval)['action'])
        self.current=MERGE;self.pr.update(state='closed',merged=True,merge_commit_sha=MERGE)

    def test_missing_foreign_stale_or_draft_native_subject_has_zero_send(self):
        for mutation in ('missing-approval','foreign','stale-base','draft'):
            with self.subTest(mutation=mutation):
                saved=self.approval_path.read_bytes()
                try:
                    if mutation=='missing-approval':self.approval_path.unlink()
                    elif mutation=='foreign':self.responses['']['id']=123
                    elif mutation=='stale-base':self.current='0'*40
                    else:self.pr['draft']=True
                    self.assertEqual('hold',self.run_native()['action'])
                    self.assertEqual([],self.pushes)
                    with sqlite3.connect(self.path) as db:self.assertEqual([],db.execute('select * from releases').fetchall())
                finally:
                    self.approval_path.write_bytes(saved);self.approval_path.chmod(0o600)
                    self.responses['']['id']=RID;self.current=BASE;self.pr['draft']=False

    def test_existing_unknown_record_reopens_native_readback_without_any_send(self):
        self.reserve_fixture()
        self.approval_path.unlink() # Revocation cannot authorize resend or block readback.
        self.assertEqual('hold',self.run_native()['action']) # No fresh successor health yet.
        self.assertEqual([],self.pushes)
        self.assertIn('pulls/42',self.reads)
        self.assertIn('git/ref/heads/main',self.reads)
        self.assertFalse(any(a[0] in ('push','commit-tree','merge-tree') for a in self.argv))
        self.assertEqual('hold',self.run_native()['action'])
        self.assertEqual([],self.pushes)

    def test_no_fresh_or_wrong_head_health_cannot_adopt_existing_effect(self):
        self.reserve_fixture()
        self.assertEqual('hold',self.run_native()['action'])
        self.fresh=dict(id=701,run_attempt=1,event='push',head_branch='main',head_sha=BASE,
            path='.github/workflows/lab-pr-ci.yml',status='completed',conclusion='success',
            repository=dict(id=RID,full_name=REPO,private=False))
        self.assertEqual('hold',self.run_native()['action'])
        with sqlite3.connect(self.path) as db:
            self.assertNotIn(('adopted',),db.execute('select phase from upgrade_bindings').fetchall())
        self.assertEqual([],self.pushes)

    def install_signed_native_fixture(self):
        path=HERE/'native-proof-fixture.json.gz'
        self.assertTrue(type(NATIVE_PROOF_FIXTURE_SHA256) is str and len(NATIVE_PROOF_FIXTURE_SHA256)==64,
                        'INDEPENDENT_NATIVE_PROOF_FIXTURE_UNBOUND')
        self.assertTrue(path.is_file(),'INDEPENDENT_NATIVE_PROOF_FIXTURE_MISSING')
        raw=read_native_fixture_bytes()
        self.assertEqual(NATIVE_PROOF_FIXTURE_SHA256,hashlib.sha256(raw).hexdigest(),
                         'INDEPENDENT_NATIVE_PROOF_FIXTURE_BYTES_CHANGED')
        value=json.loads(raw)
        self.assertEqual({'version','subject','approval','policy_by_stage','raw_get','git_reads'},set(value))
        self.assertEqual(dict(repository_id=RID,repository=REPO,number=42,base=BASE,head=HEAD,target='main',merge=MERGE),
                         value['subject'])
        self.assertEqual(self.approval,value['approval'])
        for key in ('policy_by_stage','raw_get','git_reads'):
            self.assertEqual({'before','merged-awaiting-health','healthy'},set(value[key]))
        self.signed_fixture=value
        self.policy=copy.deepcopy(value['policy_by_stage']['before'])

    def test_signed_exact_native_proof_uses_real_writer_one_lease_unknown_reopens_get_only(self):
        self.install_signed_native_fixture()
        writer_module=module('lab_queue_git_writer')
        original=writer_module.GitLabMainWriter.send
        calls=[]
        def observed_send(writer,intent):
            calls.append(copy.deepcopy(intent))
            return original(writer,intent) # Actual writer, never substitute send.
        writer_module.GitLabMainWriter.send=observed_send
        self.addCleanup(setattr,writer_module.GitLabMainWriter,'send',original)
        self.unknown=True
        self.assertEqual('hold',self.run_native()['action'])
        self.assertEqual(1,len(calls),'NATIVE_ENTRY_DID_NOT_USE_REAL_GIT_MAIN_WRITER')
        self.assertEqual(1,len(self.pushes),'NATIVE_EXACT_LEASE_NOT_ONCE')
        self.approval_path.unlink()
        self.policy=copy.deepcopy(self.signed_fixture['policy_by_stage']['merged-awaiting-health'])
        for _ in range(2):self.assertEqual('hold',self.run_native()['action'])
        self.assertEqual(1,len(self.pushes),'UNKNOWN_NATIVE_EFFECT_WAS_RESENT')
        self.assertEqual(1,len(calls))
        with sqlite3.connect(self.path) as db:
            self.assertEqual([('confirmed',)],db.execute('select phase from upgrade_bindings').fetchall())

    def test_only_exact_new_native_main_health_adopts_confirmed_effect_without_send(self):
        self.install_signed_native_fixture()
        self.reserve_fixture()
        self.native_stage='merged-awaiting-health'
        self.policy=copy.deepcopy(self.signed_fixture['policy_by_stage'][self.native_stage])
        self.assertEqual('hold',self.run_native()['action'])
        self.assertEqual([],self.pushes)
        self.native_stage='healthy'
        self.policy=copy.deepcopy(self.signed_fixture['policy_by_stage'][self.native_stage])
        self.assertEqual('adopted',self.run_native()['action'],'EXACT_NATIVE_SUCCESSOR_HEALTH_NOT_ADOPTED')
        with sqlite3.connect(self.path) as db:
            self.assertEqual([('adopted',MERGE)],db.execute('select phase,adopted_main from upgrade_bindings').fetchall())
        self.assertEqual([],self.pushes)


if __name__=='__main__':
    expected='--expect-red' in sys.argv
    if expected:
        sys.argv.remove('--expect-red')
        result=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(NativeEntryAcceptanceTest))
        ok=(result.testsRun==5 and len(result.failures)==5 and not result.errors and not result.skipped
            and all('EXACT_NATIVE_UPGRADE_BINDING_UNBOUND' in trace for _,trace in result.failures))
        raise SystemExit(0 if ok else 1)
    unittest.main(verbosity=2)
