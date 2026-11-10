"""Second independent card30 suite: exact approved update, real public advance.
No candidate, test, Git transport or network is executed during authoring.
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

import frozen_card30_observation as F

PACKAGE = Path(__file__).resolve().parent
N = None


def read(name):
    return json.loads((PACKAGE/name).read_text())


class UpdateAcceptance(F.Acceptance):
    def fixture(self):
        store, auth, sc, cfg = super().fixture()
        cfg.update(approved_update=read('approved-update-fixture.json'),
                   normalized_contribution=read('normalized-contribution.json'),
                   normalized_commit_evidence=read('normalized-commit-evidence.json'),
                   test_path=read('scope-fixture.json')['test_path'],
                   files=[r['path'] for r in read('original-contribution.json')['original_diff']['files']])
        # Isolated pre-existing adopted receipt, not a manufactured first-pr.
        store.db.execute('CREATE TABLE adopted_pr(repo_id INTEGER,card INTEGER,number INTEGER,head_ref TEXT,kind TEXT,subject TEXT)')
        store.db.execute('INSERT INTO adopted_pr VALUES(?,?,?,?,?,?)',
                         (sc['repo_id'],30,32,sc['head_ref'],N.RECORD_KIND,F.wire(sc)))
        return store, auth, sc, cfg

    def port(self, store, sc, cfg):
        port = super().port(sc, cfg)
        old_pull = port.pull
        port.remote_head, port.remote_base = sc['old_head'], sc['old_base']
        port.remote_ref_override = None
        port.loss, port.writes = None, []
        port.proof_mutation = None
        port.cwd, port.git_env = Path('/synthetic-no-git-execution'), {}
        observed = read('candidate-observed.json')
        original = read('original-contribution.json')['original_diff']

        def pull(number):
            row = old_pull(number)
            row['head']['sha'], row['base']['sha'] = port.remote_head, port.remote_base
            return row

        def ref(branch):
            port.calls.append(('ref',branch))
            self.assertEqual(branch,sc['head_ref'])
            return port.remote_head if port.remote_ref_override is None else port.remote_ref_override

        def diff(base, head):
            port.calls.append(('diff',base,head))
            if (base,head)==(sc['old_base'],sc['old_head']):
                result = copy.deepcopy(original)
                if port.proof_mutation=='original-blob':result['files'][0]['after_blob']='e'*40
                return result
            self.assertEqual((base,head),(sc['new_base'],cfg['candidate']))
            result = copy.deepcopy(observed['candidate_diff'])
            if port.proof_mutation=='candidate-blob':result['files'][0]['after_blob']='e'*40
            if port.proof_mutation=='candidate-file':result['files'].append(copy.deepcopy(result['files'][0]))
            return result

        def git(*args):
            port.calls.append(('git',args))
            if args and args[0]=='push':
                ref='refs/heads/lab/30-facts-f1'
                self.assertEqual(args,('push','--porcelain','--no-follow-tags','--recurse-submodules=no',
                                      '--force-with-lease='+ref+':'+sc['old_head'],'origin',cfg['candidate']+':'+ref))
                effect=store.db.execute('SELECT phase,issue_count FROM effects WHERE kind=?',(N.UPDATE_KIND,)).fetchall()
                self.assertEqual([tuple(r) for r in effect],[('unknown',1)],'SEND_BEFORE_DURABLE_CONSUMPTION')
                self.assertFalse(store.db.in_transaction,'SEND_INSIDE_UNCOMMITTED_RESERVATION')
                port.writes.append(args)
                if port.loss=='before':raise TimeoutError('synthetic uncertain send before visible update')
                port.remote_head,port.remote_base=cfg['candidate'],sc['new_base']
                if port.loss=='after':raise TimeoutError('synthetic lost update reply')
                return ''
            history=read('normalized-commit-evidence.json')['history']
            records={}
            for item in history:
                records[item['original']]=copy.deepcopy(item['original_metadata'])
                records[item['replayed']]=copy.deepcopy(item['replayed_metadata'])
            if len(args)==4 and args[:2]==('show','-s') and args[3] in records:
                record=records[args[3]]
                replayed={item['replayed'] for item in history}
                if args[3] in replayed and port.proof_mutation=='history-author':record['author']='forged author'
                if args[3] in replayed and port.proof_mutation=='history-message':record['message']='missing original commit and author declarations'
                if args[2]=='--format=%an%x00%ae%x00%aI%x00%B':
                    return '\0'.join(record[k] for k in ('author','email','author_date','message'))
                if args[2]=='--format=%B':return record['message']
            if args==('show','-s','--format=%P',cfg['candidate']):return ' '.join(observed['parents'])
            if args in (('rev-parse',cfg['candidate']+'^{tree}'),('show','-s','--format=%T',cfg['candidate'])):
                return 'e'*40 if port.proof_mutation=='tree' else observed['tree']
            if len(args)==4 and args[:2]==('merge-base','--is-ancestor'):
                self.assertEqual(args[3],cfg['candidate'])
                self.assertIn(args[2],observed['ancestors'])
                if port.proof_mutation=='ancestry':raise ValueError('synthetic missing signed ancestor')
                return ''
            self.fail('UNEXPECTED_GIT_VOCABULARY:'+repr(args))

        def run(argv,*args,**kwargs):
            port.calls.append(('run',tuple(argv)))
            self.assertEqual(argv[:3],['/usr/bin/git','--no-replace-objects','show'])
            self.assertEqual(len(argv),4)
            commit,relative=argv[3].split(':',1)
            self.assertEqual(commit,cfg['candidate'])
            allowed={r['path'] for r in observed['files']}
            if relative not in allowed:raise ValueError('read-outside-exact-exported-allowlist')
            raw=(PACKAGE/'candidate-files'/relative).read_bytes()
            if port.proof_mutation=='test-bytes' and relative.endswith('PostingFactsDmlRoleIT.java'):
                return raw+b'\n// synthetic unauthorized mutation\n'
            return raw

        port.pull,port.ref,port.diff,port.git=pull,ref,diff,git
        port.fake_run=run
        return port

    def advance(self, store, auth, sc, cfg, port):
        with patch.object(N,'run',port.fake_run):
            return N.advance(store,auth,'adoption.update',sc,cfg,port)

    def accepted_advance(self, store, auth, sc, cfg, port):
        try:return self.advance(store,auth,sc,cfg,port)
        except ValueError as error:
            self.fail('CARD30_EXACT_APPROVED_UPDATE_REJECTED:'+str(error))

    def reject_update(self, store, auth, sc, cfg, port):
        before=self.rows(store.db)
        before_writes=copy.deepcopy(port.writes)
        with self.assertRaises(ValueError):self.advance(store,auth,sc,cfg,port)
        self.assertEqual(before,self.rows(store.db),'REJECTED_UPDATE_CHANGED_LEDGER')
        self.assertEqual(port.writes,before_writes,'REJECTED_UPDATE_SENT')

    def test_update_01_exact_approved_real_candidate_updates_once(self):
        store,auth,sc,cfg=self.fixture();port=self.port(store,sc,cfg)
        original=[tuple(r) for r in store.db.execute("SELECT * FROM effects WHERE effect_id='native-effect'")]
        result=self.accepted_advance(store,auth,sc,cfg,port)
        expected=dict(action='updated',number=32,head=cfg['candidate'],base=sc['new_base'])
        self.assertEqual(result,expected)
        self.assertEqual(len(port.writes),1)
        effect=store.db.execute('SELECT kind,phase,issue_count FROM effects WHERE kind=?',(N.UPDATE_KIND,)).fetchall()
        self.assertEqual([tuple(r) for r in effect],[(N.UPDATE_KIND,'observed',1)])
        self.assertEqual(store.db.execute("SELECT count(*) FROM effects WHERE kind='first-pr'").fetchone()[0],0)
        self.assertEqual(original,[tuple(r) for r in store.db.execute("SELECT * FROM effects WHERE effect_id='native-effect'")])
        self.assertEqual(self.advance(store,auth,sc,cfg,port),expected)
        self.assertEqual(len(port.writes),1)

    def test_update_02_missing_or_changed_approved_update_is_rejected(self):
        for mutation in ('missing','kind','head','base','scope_sha256','original_diff_sha256','candidate_diff_sha256','tree','normalized_contribution_sha256','normalized_commit_evidence_sha256','mapping','missing-mapping','mapping-author','test-path','test-sha','test-missing','test-duplicate'):
            with self.subTest(mutation=mutation):
                store,auth,sc,cfg=self.fixture();port=self.port(store,sc,cfg)
                if mutation=='missing':del cfg['approved_update']
                elif mutation=='kind':cfg['approved_update']['kind']='prefix-allowance'
                elif mutation=='mapping':cfg['normalized_contribution']['history'][0]['replayed']='e'*40
                elif mutation=='missing-mapping':del cfg['normalized_contribution']
                elif mutation=='mapping-author':cfg['normalized_commit_evidence']['history'][0]['replayed_metadata']['author']='forged author'
                elif mutation=='test-path':cfg['approved_update']['tests'][0]['path']='backend/unapproved.java'
                elif mutation=='test-sha':cfg['approved_update']['tests'][0]['sha256']='e'*64
                elif mutation=='test-missing':cfg['approved_update']['tests'].pop()
                elif mutation=='test-duplicate':cfg['approved_update']['tests'].append(copy.deepcopy(cfg['approved_update']['tests'][0]))
                else:cfg['approved_update'][mutation]='e'*(40 if mutation in ('head','base','tree') else 64)
                self.reject_update(store,auth,sc,cfg,port)

    def test_update_03_original_contributors_and_moved_test_binding_are_preserved(self):
        for mutation in ('author','acceptance','acceptance-sha','old-head','old-base','new-base','candidate-head','old-test-path','wrong-test-path','missing-approval'):
            with self.subTest(mutation=mutation):
                store,auth,sc,cfg=self.fixture()
                if mutation=='author':sc['author_session']='44444444-4444-4444-8444-444444444444'
                elif mutation=='acceptance':sc['acceptance_session']='55555555-5555-4555-8555-555555555555'
                elif mutation=='acceptance-sha':sc['acceptance_sha256']='e'*64
                elif mutation=='old-head':sc['old_head']='e'*40
                elif mutation=='old-base':sc['old_base']='e'*40
                elif mutation=='new-base':sc['new_base']='e'*40
                elif mutation=='candidate-head':cfg['candidate']='e'*40
                elif mutation=='old-test-path':cfg['test_path']=cfg['test_path'].replace('/acceptance/operations/','/operations/')
                elif mutation=='wrong-test-path':cfg['test_path']='backend/unapproved.java'
                else:del sc['approval']
                # Simulate a stale/rebound stored row too; independent exact approval
                # must still refuse rather than relying only on row inequality.
                store.db.execute('UPDATE adopted_pr SET subject=?',(F.wire(sc),))
                port=self.port(store,sc,cfg)
                self.reject_update(store,auth,sc,cfg,port)

    def test_update_04_drifted_file_blob_tree_or_missing_ancestor_cannot_send(self):
        for mutation in ('original-blob','candidate-blob','candidate-file','tree','ancestry','test-bytes','history-author','history-message'):
            with self.subTest(mutation=mutation):
                store,auth,sc,cfg=self.fixture();port=self.port(store,sc,cfg)
                port.proof_mutation=mutation
                self.reject_update(store,auth,sc,cfg,port)

    def test_update_05_lost_reply_only_reads_back_and_never_resends(self):
        store,auth,sc,cfg=self.fixture();port=self.port(store,sc,cfg);port.loss='after'
        first=self.accepted_advance(store,auth,sc,cfg,port)
        self.assertEqual(first['action'],'hold')
        self.assertEqual(len(port.writes),1)
        self.assertEqual(store.db.execute('SELECT phase FROM effects WHERE kind=?',(N.UPDATE_KIND,)).fetchone()[0],'unknown')
        badcfg=copy.deepcopy(cfg)
        badcfg['approved_update']['head']='e'*40
        self.reject_update(store,auth,sc,badcfg,port)
        start=len(port.calls)
        result=self.advance(store,auth,sc,cfg,port)
        self.assertEqual(result,dict(action='updated',number=32,head=cfg['candidate'],base=sc['new_base']))
        self.assertEqual(len(port.writes),1)
        self.assertFalse(any(r[0]=='git' and r[1][0]=='push' for r in port.calls[start:]))
        self.assertEqual(store.db.execute('SELECT issue_count FROM effects WHERE kind=?',(N.UPDATE_KIND,)).fetchone()[0],1)

    def test_update_06_unknown_with_old_ref_stays_readonly(self):
        store,auth,sc,cfg=self.fixture();port=self.port(store,sc,cfg);port.loss='before'
        self.assertEqual(self.accepted_advance(store,auth,sc,cfg,port)['action'],'hold')
        self.assertEqual(len(port.writes),1)
        port.loss=None
        self.assertEqual(self.advance(store,auth,sc,cfg,port)['action'],'hold')
        self.assertEqual(len(port.writes),1)
        row=store.db.execute('SELECT phase,issue_count FROM effects WHERE kind=?',(N.UPDATE_KIND,)).fetchone()
        self.assertEqual(tuple(row),('unknown',1))

    def test_update_07_api_and_git_ref_disagreement_is_readonly_hold(self):
        for api_new in (False,True):
            with self.subTest(api_new=api_new):
                store,auth,sc,cfg=self.fixture();port=self.port(store,sc,cfg)
                port.remote_head=cfg['candidate'] if api_new else sc['old_head']
                port.remote_base=sc['new_base'] if api_new else sc['old_base']
                port.remote_ref_override=sc['old_head'] if api_new else cfg['candidate']
                before=self.rows(store.db)
                with self.assertRaisesRegex(ValueError,'^existing-pr-live-pr-drift$'):
                    self.advance(store,auth,sc,cfg,port)
                self.assertEqual(before,self.rows(store.db),'DIVERGENT_READBACK_CHANGED_LEDGER')
                self.assertEqual(port.writes,[],'DIVERGENT_READBACK_SENT')
                self.assertTrue(any(call[0]=='pull' for call in port.calls))
                self.assertTrue(any(call[0]=='ref' for call in port.calls))


def main():
    global N
    p=argparse.ArgumentParser()
    p.add_argument('--source-root',type=Path,default=PACKAGE/'baseline-source')
    p.add_argument('--source-closure-sha256',default=F.BASELINE_PIN)
    args=p.parse_args();root=args.source_root.resolve(strict=True)
    raw=(root/'SOURCE-CLOSURE.json').read_bytes()
    if hashlib.sha256(raw).hexdigest()!=args.source_closure_sha256:raise SystemExit('HOLD: source closure pin mismatch')
    for name,pin in json.loads(raw)['files_sha256'].items():
        path=(root/name).resolve(strict=True);path.relative_to(root)
        if hashlib.sha256(path.read_bytes()).hexdigest()!=pin:raise SystemExit('HOLD: source dependency pin mismatch')
    sys.path.insert(0,str(root));N=importlib.import_module('existing_pr_adoption')
    F.N=N;F.S=importlib.import_module('runtime_store');F.PACKAGE=PACKAGE
    # Explicit selection preserves the first seven frozen tests without rerunning
    # or accidentally discovering inherited methods in this additive suite.
    names=sorted(n for n in UpdateAcceptance.__dict__ if n.startswith('test_update_'))
    result=unittest.TextTestRunner(verbosity=2).run(unittest.TestSuite(UpdateAcceptance(n) for n in names))
    return 86 if result.errors or result.skipped else (0 if result.wasSuccessful() else 1)


if __name__=='__main__':raise SystemExit(main())
