"""#45 additive formal-entry/V2 handoff acceptance. Hosted temporary state only."""
import copy
import hashlib
import importlib.util
import os
from pathlib import Path
import sqlite3
import subprocess
import sys
import unittest

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
CORE_SHA='99122154e3ce796c23e16221e83804df63dcf137ad2c998cfeac4691a40fea8e'


def load(name,path,search=None):
    if name in sys.modules:
        existing=sys.modules[name]
        if Path(existing.__file__).resolve()!=path.resolve():
            raise AssertionError('V2_ACCEPTANCE_FOREIGN_MODULE_CACHE: '+name)
        return existing
    spec=importlib.util.spec_from_file_location(name,path)
    value=importlib.util.module_from_spec(spec);sys.modules[name]=value
    before=sys.path[:]
    if search:sys.path.insert(0,str(search))
    try:spec.loader.exec_module(value)
    finally:sys.path[:]=before
    return value


class BranchHandoffV2AcceptanceTest(unittest.TestCase):
    def setUp(self):
        frozen=HERE/'lab_branch_lifecycle_selftest.py'
        self.assertEqual(CORE_SHA,hashlib.sha256(frozen.read_bytes()).hexdigest(),
                         'FROZEN_EIGHT_MUST_REMAIN_BYTE_EXACT')
        self.core_module=load('lab45_original_fixture',frozen,HERE)
        self.core=self.core_module.BranchLifecycleAcceptanceTest(methodName='runTest')
        self.core.setUp();self.addCleanup(self.core.doCleanups)
        self.binding=self.core.dispatch()
        self.core.commit_publish('synthetic V2 handoff candidate\n')
        self.binding=copy.deepcopy(self.core.binding)
        self.core.raw(['checkout','--detach',self.binding['head']],self.core.w1)
        merge_dir=ROOT/'scripts/merge'
        self.operator=load('mq_lab_operator',merge_dir/'mq_lab_operator.py',merge_dir)
        self.storage=load('mq_lab_journal_v2',merge_dir/'mq_lab_journal_v2.py',merge_dir)
        self.common=self.core.checkout/'.git'
        self.directory=self.common/'merge-queue';self.directory.mkdir(mode=0o700);self.directory.chmod(0o700)
        self.dbpath=self.directory/'releases.sqlite3'
        self.history=[(n,'confirmed',str(n)*40,str(n+1)*40,'main','h'+str(n),str(n+2)*40,'ordinary') for n in range(1,6)]
        with sqlite3.connect(self.dbpath) as db:
            db.execute(self.storage.V1);db.execute(self.storage.ALTER);db.execute(self.storage.BINDINGS)
            db.execute('pragma user_version=2')
            db.executemany('insert into releases values (?,?,?,?,?,?,?,?)',self.history)
        self.dbpath.chmod(0o600)
        (self.directory/'merge.queue').write_text('401\n')
        (self.directory/'merge.queue.heads').write_text('401 '+self.binding['head']+'\n')
        self.intent=dict(number=401,head=self.binding['head'],base=self.binding['base'],target='main')
        self.approval=dict(self.intent,repo_id=1449415666,mode='normal')
        self.sends=0;self.handoffs=[];self.fail_handoff=False;self.merge=None

    def prepare_pr(self):
        result=self.core.command('ensure-pr',dict(binding=self.binding))
        self.assertEqual(401,result['pr_number'])

    def load(self,number):
        self.assertEqual(401,number)
        return copy.deepcopy(self.approval)

    def verify(self,intent,approval):
        return dict(self.intent,action='eligible',reason='synthetic-current-ordinary-proof',release_key='45')

    def writer(self,authorize):
        owner=self
        class Writer:
            def send(self,intent):
                owner.assertIs(authorize(intent),True)
                with sqlite3.connect(owner.dbpath) as db:
                    owner.assertEqual(('reserved','ordinary'),db.execute('select status,mode from releases where number=401').fetchone())
                owner.sends+=1
                owner.assertEqual(1,owner.sends,'V2_HANDOFF_TRIGGERED_SECOND_MERGE')
                owner.core.raw(['merge','--no-ff','-m','Synthetic ordinary merge',owner.binding['head']])
                owner.merge=owner.core.raw(['rev-parse','HEAD'])
                owner.core.raw(['push','origin','HEAD:refs/heads/main'])
                owner.core.github.pulls[0].update(state='closed',merged=True,merge_commit_sha=owner.merge)
            def readback(self,intent):
                if owner.merge is None:return None
                owner.assertEqual([owner.binding['base'],owner.binding['head']],
                                  owner.core.raw(['show','-s','--format=%P',owner.merge]).split())
                owner.assertEqual(owner.merge,owner.core.ref('refs/heads/main'))
                return dict(owner.intent,merged=True,merge_sha=owner.merge,target_contains_merge=True)
        return Writer()

    def post_merge(self,request):
        try:return self.core.command('post-merge',request)
        except Exception as error:
            self.fail('FORMAL_POST_MERGE_NOT_FAIL_CLOSED: '+type(error).__name__)

    def after_confirmed_merge(self,number,common_dir,result):
        """Proposed optional real operator→formal lifecycle handoff port."""
        self.handoffs.append((number,str(common_dir),copy.deepcopy(result)))
        self.assertEqual(401,number);self.assertEqual(self.common,Path(common_dir))
        self.assertEqual('confirmed',result['action'])
        if self.fail_handoff:raise OSError('synthetic lifecycle observation unavailable')
        # Reader-port observations are derived from actual fixture state, not
        # standalone queue_completed/green labels supplied by caller.
        with sqlite3.connect(self.dbpath) as db:
            row=db.execute('select status,head,base,merge_sha,mode from releases where number=401').fetchone()
        self.assertEqual(('confirmed',self.binding['head'],self.binding['base'],self.merge,'ordinary'),row)
        self.assertEqual(b'',(self.directory/'merge.queue').read_bytes())
        self.assertEqual(b'',(self.directory/'merge.queue.heads').read_bytes())
        self.core.github.view.update(queue_completed=True,queued=False,
            merge_log=dict(repository_id=1449415666,number=401,head=row[1],base=row[2],merge_sha=row[3],status=row[0]),
            merged_readback=dict(number=401,merge_sha=self.merge,
                parents=self.core.raw(['show','-s','--format=%P',self.merge]).split(),target_contains_merge=True))
        before=self.core.state.read_bytes()
        candidate=self.post_merge(dict(binding=self.binding,number=number,
            common_dir=str(self.common),merge_result=copy.deepcopy(result)))
        self.assertEqual(before,self.core.state.read_bytes(),'READONLY_CANDIDATE_MUTATED_BRANCH_BINDING')
        return candidate

    def once(self):return self.operator.run_command('once',401,self.common,self)

    def assert_preserved(self):
        self.assertEqual(1,self.sends)
        self.assertEqual([],self.core.deletes,'HANDOFF_MUST_NEVER_DELETE_BRANCH')
        self.assertEqual(self.binding['head'],self.core.ref('refs/heads/'+self.binding['branch']))
        with sqlite3.connect(self.dbpath) as db:
            self.assertEqual(self.history,db.execute('select * from releases where number<=5 order by number').fetchall())
            self.assertEqual(('confirmed',self.merge),db.execute('select status,merge_sha from releases where number=401').fetchone())
        self.assertEqual(b'',(self.directory/'merge.queue').read_bytes())
        self.assertEqual(b'',(self.directory/'merge.queue.heads').read_bytes())

    def test_actual_v2_confirmed_and_cleared_fifo_emits_readonly_branch_candidate(self):
        self.prepare_pr()
        result=self.once()
        self.assertEqual('confirmed',result['action'])
        self.assert_preserved()
        self.assertEqual(1,len(self.handoffs),'V2_CONFIRMED_LIFECYCLE_HANDOFF_MISSING')
        candidate=result.get('branch_retirement')
        self.assertIsInstance(candidate,dict,'V2_READONLY_RETIREMENT_CANDIDATE_MISSING')
        self.assertEqual('retirement-candidate',candidate.get('action'))
        self.assertIs(candidate.get('allows_delete'),False)
        self.assertIs(candidate.get('release_authority'),False)
        self.assertEqual(self.merge,candidate.get('merge_sha'))
        self.assertEqual(self.binding['head'],candidate.get('head'))

    def test_handoff_failure_cannot_downgrade_confirmed_merge_retry_writer_or_delete(self):
        self.prepare_pr();self.fail_handoff=True
        result=self.once()
        self.assertEqual('confirmed',result['action'],'LIFECYCLE_FAILURE_DOWNGRADED_CONFIRMED_MERGE')
        self.assert_preserved()
        self.assertEqual(1,len(self.handoffs),'V2_POST_MERGE_FAILURE_BOUNDARY_NOT_REACHED')
        self.once()
        self.assert_preserved()

    def test_unconfirmed_or_inconsistent_v2_fifo_head_handoff_refuses_without_effect(self):
        self.prepare_pr()
        for mutation in ('missing','reserved','wrong-head','queued','stale-head'):
            with self.subTest(mutation=mutation):
                with sqlite3.connect(self.dbpath) as db:
                    db.execute('delete from releases where number=401')
                    if mutation!='missing':
                        db.execute('insert into releases values (?,?,?,?,?,?,?,?)',
                            (401,'reserved' if mutation=='reserved' else 'confirmed',
                             'f'*40 if mutation=='wrong-head' else self.binding['head'],self.binding['base'],
                             'main','45',None if mutation=='reserved' else 'e'*40,'ordinary'))
                (self.directory/'merge.queue').write_text('401\n' if mutation=='queued' else '')
                (self.directory/'merge.queue.heads').write_text('401 '+'f'*40+'\n' if mutation=='stale-head' else '')
                result=self.post_merge(dict(binding=self.binding,number=401,common_dir=str(self.common),
                    merge_result=dict(action='confirmed',merge_sha='e'*40)))
                self.assertEqual('hold',result['action'],'INEXACT_V2_COMPLETION_BECAME_RETIREMENT_CANDIDATE')
                self.assertEqual(0,self.sends);self.assertEqual([],self.core.deletes)

    def test_duplicate_pr_or_incomplete_native_listing_is_not_creation_permission(self):
        github=self.core.github
        original=github.find_pulls
        github.create_pull(self.binding) # Native fixture preexisting effect, not harness mutation.
        creates=github.creates
        for mutation in ('duplicate','incomplete','foreign','missing-repo'):
            with self.subTest(mutation=mutation):
                rows=copy.deepcopy(github.pulls)
                if mutation=='duplicate':rows.append(dict(copy.deepcopy(rows[0]),number=402))
                elif mutation=='foreign':rows[0]['head']['repo']['id']=123
                elif mutation=='missing-repo':del rows[0]['head']['repo']
                github.find_pulls=lambda binding:dict(complete=mutation!='incomplete',items=copy.deepcopy(rows))
                try:result=self.core.command('ensure-pr',dict(binding=self.binding))
                except Exception as error:self.fail('NATIVE_PR_READBACK_NOT_FAIL_CLOSED: '+type(error).__name__)
                self.assertEqual('hold',result['action'],'UNVERIFIED_NATIVE_PR_LIST_WAS_ACCEPTED')
                self.assertEqual(creates,github.creates)
        github.find_pulls=original
        self.assertEqual([],self.core.deletes)

    def test_lost_rerun_reply_reconciles_same_native_attempt_without_second_request(self):
        self.prepare_pr();github=self.core.github
        github.run=dict(id=700,run_attempt=1,event='pull_request',repository=dict(id=1449415666,full_name=self.core_module.REPO,private=False),
            head_branch=self.binding['branch'],head_sha=self.binding['head'],base_sha=self.binding['base'],
            status='completed',conclusion='success',pull_request=401)
        def request_rerun(run,attempt):
            self.assertEqual(700,run)
            github.reruns+=1
            github.run['run_attempt']+=1
            return dict(requested=True)
        def lost(run,attempt):
            request_rerun(run,attempt)
            raise TimeoutError('synthetic rerun response lost after native attempt advanced')
        github.rerun=lost
        try:self.core.command('rerun-ci',dict(binding=self.binding,run_id=700,run_attempt=1))
        except TimeoutError:pass
        github.rerun=request_rerun
        result=self.core.command('rerun-ci',dict(binding=self.binding,run_id=700,run_attempt=1))
        self.assertEqual(1,github.reruns,'UNKNOWN_NATIVE_RERUN_WAS_RESENT')
        self.assertEqual(2,github.run['run_attempt'])
        self.assertIn(result['action'],('verified-ci','hold'))
        self.assertEqual([],self.core.deletes)

    def test_formal_cli_help_exposes_same_post_merge_readonly_entry_without_ports(self):
        bindir=self.core.root/'blocked-cli';bindir.mkdir()
        for command in ('gh','git'):
            stub=bindir/command;stub.write_text('#!/bin/sh\nexit 79\n');stub.chmod(0o755)
        env=dict(self.core.env,PATH=str(bindir),GH_TOKEN='',GITHUB_TOKEN='')
        process=subprocess.run([sys.executable,'-B',self.core.live.__file__,'--help'],cwd=self.core.checkout,
            env=env,capture_output=True,text=True,timeout=15)
        self.assertEqual(0,process.returncode,'FORMAL_BRANCH_CLI_HELP_MUST_NOT_TOUCH_PORTS')
        self.assertIn('post-merge',process.stdout,'FORMAL_POST_MERGE_ENTRY_NOT_EXPOSED')
        self.assertEqual([],self.core.deletes)


if __name__=='__main__':unittest.main(verbosity=2)
