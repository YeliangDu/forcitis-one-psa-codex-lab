"""Frozen exact card30 H1-to-H2 followup acceptance.
Same existing_pr_adoption.advance; original adopted subject is never rewritten.
"""
import argparse
import copy
import hashlib
import importlib
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

PACKAGE=Path(__file__).resolve().parent
PREDECESSOR=PACKAGE/'predecessor'
sys.path.insert(0,str(PREDECESSOR))
import test_update_acceptance as P
import frozen_card30_observation as F

H1='60db4a63b5970334f1ced9a8ad7f14d4691c1b44'
BASE='0c491846d24d194090bda99a1d16a100331cbd2c'
H2='27942544d70f53014052f44342c06487d52c48fb'
BINDING_PIN='fe26d5e0dc69ed661c763637ea79225a6961ce7eb830f6b8835445210528dd84'
SOURCE_PIN='763524c43285e8f9cb46fcf4014a4a4a54b9c95fe662d19c2575db0ae6363eb4'
N=None
BINDING=None
EXPORTS=None


def sha(raw):return hashlib.sha256(raw).hexdigest()
def load(path):return json.loads(path.read_text())


class FollowupAcceptance(P.UpdateAcceptance):
    def fixture(self):
        store,auth,sc,cfg=super().fixture()
        key=f"existing-pr-update:{sc['repo_id']}:{sc['number']}:{sc['old_head']}"
        observation=dict(action='updated',number=32,head=H1,base=BASE)
        old_target=F.wire(dict(scope=sc,head=H1,revision=1))
        store.db.execute('INSERT INTO effects VALUES(?,1,1,?,?,?,?,1,?,0)',
                         ('predecessor-update',key,N.UPDATE_KIND,old_target,'observed',F.wire(observation)))
        task=json.loads(store.db.execute('SELECT body FROM tasks WHERE revision=1').fetchone()[0])
        task.update(revision=2,H=BINDING['head'],B=BASE)
        store.db.execute("UPDATE tasks SET phase='fenced' WHERE revision=1")
        store.db.execute("INSERT INTO tasks VALUES(2,?,'active',1,NULL)",(F.wire(task),))
        sid=cfg['successor_session']
        store.db.execute("INSERT INTO turns VALUES('followup-turn',2,1,1,'terminal')")
        store.db.execute("INSERT INTO effects VALUES('followup-native',2,1,'followup-native-operation','native-turn','{}','observed',1,'{}',0)")
        store.db.execute("INSERT INTO native_dispatch VALUES('followup-native',?,2,1,1,?,'observed','followup-connection','followup-turn')",(sid,sha(b'followup-prompt')))
        cfg.update(candidate=BINDING['head'],revision=2,
                   approved_followup=copy.deepcopy(BINDING['approved_followup']),
                   followup_evidence=copy.deepcopy(BINDING['evidence']))
        return store,auth,sc,cfg

    def port(self,store,sc,cfg):
        port=F.Acceptance.port(self,sc,cfg)
        old_pull=port.pull
        port.remote_head,port.remote_base=H1,BASE
        port.writes=[];port.loss=None;port.proof_mutation=None
        port.cwd=Path('/synthetic-no-git-execution');port.git_env={}
        port.s=dict(branch=sc['head_ref'],expected=H1,head=cfg['candidate'])
        def pull(number):
            row=old_pull(number)
            row['head']['sha'],row['base']['sha']=port.remote_head,port.remote_base
            return row
        def ref(branch):
            port.calls.append(('ref',branch));self.assertEqual(branch,sc['head_ref'])
            return port.remote_head
        def diff(base,head):
            port.calls.append(('diff',base,head))
            proofs=BINDING['evidence']['diffs']
            matches=[r for r in proofs if (r['base'],r['head'])==(base,head)]
            if len(matches)!=1:raise ValueError('outside exact bound diff inventory')
            value=copy.deepcopy(matches[0])
            if port.proof_mutation=='blob':value['files'][0]['after_blob']='e'*40
            return value
        def git(*args):
            port.calls.append(('git',args))
            if args and args[0]=='push':
                refname='refs/heads/lab/30-facts-f1'
                self.assertEqual(args,('push','--porcelain','--no-follow-tags','--recurse-submodules=no',
                                      '--force-with-lease='+refname+':'+H1,'origin',cfg['candidate']+':'+refname))
                key=f"existing-pr-update:{sc['repo_id']}:32:{H1}"
                row=store.db.execute('SELECT phase,issue_count FROM effects WHERE operation_key=?',(key,)).fetchone()
                self.assertIsNotNone(row,'FOLLOWUP_NOT_RESERVED_BEFORE_SEND')
                self.assertEqual(tuple(row),('unknown',1))
                self.assertFalse(store.db.in_transaction)
                port.writes.append(args)
                if port.loss=='before':raise TimeoutError('synthetic unknown before visible update')
                port.remote_head=cfg['candidate']
                if port.loss=='after':raise TimeoutError('synthetic lost followup reply')
                return ''
            if len(args)==4 and args[:2]==('show','-s'):
                records=[r for r in BINDING['evidence']['commits'] if r['commit']==args[3]]
                if len(records)==1:
                    record=records[0]
                    if args[2]=='--format=%an%x00%ae%x00%aI%x00%B':
                        return '\0'.join(record[k] for k in ('author','email','author_date','message'))
                    if args[2]=='--format=%B':return record['message']
            if args==('show','-s','--format=%P',cfg['candidate']):return ' '.join(BINDING['evidence']['parents'])
            if args in (('rev-parse',cfg['candidate']+'^{tree}'),('show','-s','--format=%T',cfg['candidate'])):
                return BINDING['evidence']['tree']
            if len(args)==4 and args[:2]==('merge-base','--is-ancestor'):
                if args[3]!=cfg['candidate'] or args[2] not in BINDING['evidence']['ancestors']:
                    raise ValueError('missing exact ancestor')
                return ''
            raise ValueError('outside exact frozen Git vocabulary')
        def run(argv,*args,**kwargs):
            if argv[:3]!=['/usr/bin/git','--no-replace-objects','show'] or len(argv)!=4:
                raise ValueError('outside exact frozen file-read vocabulary')
            commit,path=argv[3].split(':',1)
            matches=[r for r in BINDING['exports'] if (r['commit'],r['path'])==(commit,path)]
            if len(matches)!=1:raise ValueError('outside exact export allowlist')
            row=matches[0];raw=(EXPORTS/row['export_path']).read_bytes()
            if sha(raw)!=row['sha256']:raise ValueError('export pin mismatch')
            return raw
        port.pull,port.ref,port.diff,port.git= pull,ref,diff,git
        port.fake_run=run
        return port

    def accepted(self,store,auth,sc,cfg,port):
        try:return self.advance(store,auth,sc,cfg,port)
        except ValueError as error:self.fail('CARD30_EXACT_FOLLOWUP_REJECTED:'+str(error))

    def refused(self,store,auth,sc,cfg,port):
        before=self.rows(store.db);writes=copy.deepcopy(port.writes)
        try:
            value=self.advance(store,auth,sc,cfg,port)
        except ValueError:
            pass
        else:
            self.assertEqual(value.get('action'),'hold')
        self.assertEqual(before,self.rows(store.db))
        self.assertEqual(port.writes,writes)

    def test_followup_01_new_key_and_h1_lease_preserve_original_rows(self):
        store,auth,sc,cfg=self.fixture();port=self.port(store,sc,cfg)
        old=tuple(store.db.execute("SELECT * FROM effects WHERE effect_id='predecessor-update'").fetchone())
        adopted=[tuple(r) for r in store.db.execute('SELECT * FROM adopted_pr')]
        self.assertEqual(self.accepted(store,auth,sc,cfg,port),dict(action='updated',number=32,head=cfg['candidate'],base=BASE))
        self.assertEqual(len(port.writes),1)
        key=f"existing-pr-update:{sc['repo_id']}:32:{H1}"
        row=store.db.execute('SELECT phase,issue_count FROM effects WHERE operation_key=?',(key,)).fetchone()
        self.assertEqual(tuple(row),('observed',1))
        self.assertEqual(old,tuple(store.db.execute("SELECT * FROM effects WHERE effect_id='predecessor-update'").fetchone()))
        self.assertEqual(adopted,[tuple(r) for r in store.db.execute('SELECT * FROM adopted_pr')])
        self.assertEqual(self.advance(store,auth,sc,cfg,port)['action'],'updated')
        self.assertEqual(len(port.writes),1)
        self.assertEqual(store.db.execute("SELECT count(*) FROM effects WHERE kind='first-pr'").fetchone()[0],0)

    def test_followup_02_missing_unknown_or_inexact_predecessor_cannot_send(self):
        for mutation in ('missing','unknown','issue-count','observation','wrong-head','wrong-key','target','adopted'):
            with self.subTest(mutation=mutation):
                store,auth,sc,cfg=self.fixture();port=self.port(store,sc,cfg)
                if mutation=='missing':store.db.execute("DELETE FROM effects WHERE effect_id='predecessor-update'")
                elif mutation=='unknown':store.db.execute("UPDATE effects SET phase='unknown' WHERE effect_id='predecessor-update'")
                elif mutation=='issue-count':store.db.execute("UPDATE effects SET issue_count=2 WHERE effect_id='predecessor-update'")
                elif mutation=='observation':store.db.execute("UPDATE effects SET observation='{}' WHERE effect_id='predecessor-update'")
                elif mutation=='wrong-head':store.db.execute("UPDATE effects SET observation=? WHERE effect_id='predecessor-update'",(F.wire(dict(action='updated',number=32,head='e'*40,base=BASE)),))
                elif mutation=='wrong-key':store.db.execute("UPDATE effects SET operation_key='other-predecessor' WHERE effect_id='predecessor-update'")
                elif mutation=='target':store.db.execute("UPDATE effects SET target='{}' WHERE effect_id='predecessor-update'")
                else:store.db.execute('UPDATE adopted_pr SET subject=\'{}\'')
                self.refused(store,auth,sc,cfg,port)

    def test_followup_03_exact_patch_and_scope_authority_required(self):
        for mutation in ('missing','head','base','scope_sha256','patch_diff_sha256','test-sha','author','acceptance','other-pr','other-ref','other-repo','blob'):
            with self.subTest(mutation=mutation):
                store,auth,sc,cfg=self.fixture();port=self.port(store,sc,cfg)
                if mutation=='missing':del cfg['approved_followup']
                elif mutation=='test-sha':cfg['approved_followup']['tests'][0]['sha256']='e'*64
                elif mutation=='author':sc['author_session']='44444444-4444-4444-8444-444444444444'
                elif mutation=='acceptance':sc['acceptance_session']='55555555-5555-4555-8555-555555555555'
                elif mutation=='other-pr':sc['number']=33
                elif mutation=='other-ref':sc['head_ref']='lab/31-other'
                elif mutation=='other-repo':sc['repo_id']=1
                elif mutation=='blob':port.proof_mutation='blob'
                else:cfg['approved_followup'][mutation]='e'*(40 if mutation in ('head','base') else 64)
                self.refused(store,auth,sc,cfg,port)

    def test_followup_04_new_terminal_native_revision_and_owner_required(self):
        for mutation in ('active-turn','native-unknown','assignment','owner','unpaused'):
            with self.subTest(mutation=mutation):
                store,auth,sc,cfg=self.fixture();port=self.port(store,sc,cfg)
                if mutation=='active-turn':store.db.execute("UPDATE turns SET state='active' WHERE revision=2")
                elif mutation=='native-unknown':store.db.execute("UPDATE effects SET phase='unknown' WHERE effect_id='followup-native'")
                elif mutation=='assignment':store.db.execute("UPDATE tasks SET phase='fenced' WHERE revision=2")
                elif mutation=='owner':auth['generation']=0
                else:store.db.execute('UPDATE owner SET paused=0')
                self.refused(store,auth,sc,cfg,port)

    def test_followup_05_uncertain_send_never_reissues(self):
        for loss in ('after','before'):
            with self.subTest(loss=loss):
                store,auth,sc,cfg=self.fixture();port=self.port(store,sc,cfg);port.loss=loss
                self.assertEqual(self.accepted(store,auth,sc,cfg,port)['action'],'hold')
                self.assertEqual(len(port.writes),1)
                port.loss=None
                result=self.advance(store,auth,sc,cfg,port)
                self.assertEqual(result['action'],'updated' if loss=='after' else 'hold')
                self.assertEqual(len(port.writes),1)


def main():
    global N,BINDING,EXPORTS
    p=argparse.ArgumentParser(description='Frozen exact H1-to-H2 acceptance; hosted execution only')
    p.add_argument('--binding',type=Path,default=PACKAGE/'binding.json')
    p.add_argument('--binding-sha256',default=BINDING_PIN)
    p.add_argument('--exports',type=Path,default=PACKAGE/'exports')
    p.add_argument('--source-root',type=Path,default=PACKAGE/'baseline-source')
    p.add_argument('--source-closure-sha256',default=SOURCE_PIN)
    args=p.parse_args();raw=args.binding.read_bytes()
    if sha(raw)!=args.binding_sha256:raise SystemExit('HOLD: binding pin mismatch')
    BINDING=json.loads(raw);EXPORTS=args.exports.resolve(strict=True)
    if BINDING.get('status')!='FROZEN' or BINDING.get('head')!=H2:raise SystemExit('HOLD: actual frozen H2 required')
    if BINDING.get('base')!=BASE:raise SystemExit('HOLD: base mismatch')
    for row in BINDING['exports']:
        path=(EXPORTS/row['export_path']).resolve(strict=True);path.relative_to(EXPORTS)
        if sha(path.read_bytes())!=row['sha256']:raise SystemExit('HOLD: exported file pin mismatch')
    source=args.source_root.resolve(strict=True);raw=(source/'SOURCE-CLOSURE.json').read_bytes()
    if sha(raw)!=args.source_closure_sha256:raise SystemExit('HOLD: source closure mismatch')
    for name,pin in json.loads(raw)['files_sha256'].items():
        path=(source/name).resolve(strict=True);path.relative_to(source)
        if sha(path.read_bytes())!=pin:raise SystemExit('HOLD: source dependency mismatch')
    sys.path.insert(0,str(source));N=importlib.import_module('existing_pr_adoption')
    P.N=F.N=N;F.S=importlib.import_module('runtime_store');F.PACKAGE=P.PACKAGE=PREDECESSOR
    names=sorted(n for n in FollowupAcceptance.__dict__ if n.startswith('test_followup_'))
    result=unittest.TextTestRunner(verbosity=2).run(unittest.TestSuite(FollowupAcceptance(n) for n in names))
    return 86 if result.errors or result.skipped else (0 if result.wasSuccessful() else 1)


if __name__=='__main__':raise SystemExit(main())
