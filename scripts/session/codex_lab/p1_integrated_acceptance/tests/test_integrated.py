"""Future actual labctl contract. Replay never proves real authentication."""
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('integrated_fixture',ROOT/'fixtures.py')
F=importlib.util.module_from_spec(spec);spec.loader.exec_module(F)


class IntegratedReadOnlyAcceptance(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(prefix='integrated-p1-');self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name).resolve()/'forcitis-one-psa-codex-lab';self.root.mkdir(mode=0o700)
        self.state=self.root/'isolated-state';self.state.mkdir(mode=0o700)
        self.closure=json.loads(Path(os.environ['LABCTL_CLOSURE']).read_text())
        source=Path(os.environ['LABCTL_SOURCE_ROOT']).resolve();self.code=self.root/'code';self.code.mkdir()
        for name,pin in self.closure['files_sha256'].items():
            src=source/name
            if src.resolve(strict=True)!=src or hashlib.sha256(src.read_bytes()).hexdigest()!=pin:raise RuntimeError('CAPABILITY_HOLD: actual closure differs')
            target=self.code/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(src,target)
        self.entry=self.code/self.closure['entry'];self.closure_path=self.root/'closure.json';self.closure_path.write_text(json.dumps(self.closure))
        self.request=F.build(self.root);self.request['candidate_control_closure']=os.environ['LABCTL_CLOSURE_SHA256']
        self.file=self.root/'inputs/request.json'

    def invoke(self,request,inspect=False):
        self.file.write_text(json.dumps(request))
        before={str(p.relative_to(self.root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in self.root.rglob('*') if p.is_file() and self.state not in p.parents}
        env=dict(PATH=str(Path(sys.executable).parent)+':/usr/bin:/bin',PYTHONDONTWRITEBYTECODE='1')
        result=subprocess.run([sys.executable,'-I','-S','-B',str(ROOT/'worker.py'),str(self.root),str(self.entry),str(self.state),str(self.closure_path),
            os.environ['INTEGRATED_PARENT_NETNS'],'inspect-p1' if inspect else 'readonly-p1','--request',str(self.file),'--state-dir',str(self.state),'--source-closure',str(self.closure_path),'--source-closure-sha256',os.environ['LABCTL_CLOSURE_SHA256']],
            cwd=self.root/'work',env=env,capture_output=True,text=True,timeout=15)
        if result.returncode:raise RuntimeError('CAPABILITY_HOLD: actual entry/closure/isolation failure: '+result.stderr)
        wrapper=json.loads(result.stdout);self.assertIn(wrapper['exit'],(0,78))
        value=json.loads(wrapper['stdout']);self.assertIsInstance(value,dict)
        after={str(p.relative_to(self.root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in self.root.rglob('*') if p.is_file() and self.state not in p.parents}
        self.assertEqual(before,after,'INTEGRATED_WRITE_OUTSIDE_ISOLATED_STATE')
        self.assertFalse((self.root/'.git').exists())
        self.assertIs(False,value['activation_authority']);self.assertIs(False,value['queue_write_authority']);self.assertIs(False,value['writer_authority'])
        self.assertIs(False,value['native_authenticated']);self.assertIs(False,value['operational_admission'])
        self.assertEqual(0,value['state']['actual_native_send_count'])
        return value,wrapper['leaf_calls']

    def test_one_entry_replay_wiring_and_durable_restart_without_auth_green(self):
        value,calls=self.invoke(self.request)
        self.assertEqual('hold',value['action'])
        self.assertIn('native-authentication',value['missing_proof'])
        self.assertEqual('validated',value['replay_consistency'],'INTEGRATED_REPLAY_NEVER_CONSUMED')
        self.assertEqual(self.request['assignment'],value['replay_assignment'])
        self.assertEqual(self.request['artifact']['sha256'],value['replay_artifact_sha256'])
        self.assertEqual(os.environ['LABCTL_CLOSURE_SHA256'],value['state']['candidate_closure_sha256'])
        self.assertIsNone(value['state'].get('authoritative_native_turn'))
        self.assertTrue({'checkpoint','projection','receipt'}<={c['slot'] for c in calls},'STANDALONE_GREEN_SUBSTITUTED_FOR_ENTRY_WIRING')
        self.assertTrue(any(p.is_file() for p in self.state.rglob('*')),'ASSIGNMENT_NOT_DURABLE')
        inspect=dict(kind='labctl-p1-inspect/1',card=F.CARD,revision=F.REV)
        reopened,_=self.invoke(inspect,inspect=True)
        self.assertEqual(value['state']['claim_id'],reopened['state']['claim_id'])
        self.assertEqual(self.request['assignment']['task'],reopened['replay_assignment']['task'])
        again,_=self.invoke(self.request)
        self.assertEqual(value['state']['intent_id'],again['state']['intent_id'])
        self.assertEqual(1,again['state']['intent_count'])

    def rejected(self,request):
        value,_=self.invoke(request)
        self.assertEqual('hold',value['action'])
        self.assertNotEqual('validated',value.get('replay_consistency'),'MISMATCHED_REPLAY_TREATED_AS_COMPLETE')

    def test_forged_json_authentication_flag_never_grants_native_authority(self):
        request=copy.deepcopy(self.request);request['authentication']={key:True for key in request['authentication']}
        value,_=self.invoke(request)
        self.assertEqual('hold',value['action']);self.assertIn('native-authentication',value['missing_proof'])

    def test_late_predecessor_cannot_replace_persisted_current_assignment(self):
        first,_=self.invoke(self.request)
        late=copy.deepcopy(self.request);late['assignment']['revision']-=1;late['artifact']['revision']-=1
        self.rejected(late)
        current,_=self.invoke(dict(kind='labctl-p1-inspect/1',card=F.CARD,revision=F.REV),inspect=True)
        self.assertEqual(first['state']['claim_id'],current['state']['claim_id'])
        self.assertEqual(self.request['assignment'],current['replay_assignment'])

    def test_wrong_artifact_or_native_projection_digest_is_hold(self):
        for kind in ('artifact','native'):
            with self.subTest(kind=kind):
                request=copy.deepcopy(self.request)
                if kind=='artifact':request['artifact']['sha256']='0'*64
                else:request['native_projection']['window_proof']['native_pages'][0]['response_sha256']='0'*64
                self.rejected(request)

    def test_wrong_A_B_H_PR_profile_run_attempt_or_artifact_identity_is_hold(self):
        for field in ('A','C','base','head','number','profile','run_id','attempt','artifact_id'):
            with self.subTest(field=field):
                request=copy.deepcopy(self.request);expected=request['verification']['expected']
                if field=='A':request['test_A']='e'*40
                elif field=='C':request['candidate_control_closure']='0'*64
                elif field=='profile':expected['profile']['applicability']['backend']='not-applicable'
                else:expected[field]='e'*40 if field in ('base','head') else expected[field]+1
                self.rejected(request)

    def test_stale_or_nonindependent_review_shape_is_not_same_H_review(self):
        request=copy.deepcopy(self.request);request['reviews'][1]['head']='e'*40
        self.rejected(request)
        request=copy.deepcopy(self.request);request['reviews'][1]['reviewer']=request['reviews'][0]['reviewer']
        self.rejected(request)

    def test_missing_proof_and_misleading_standalone_greens_are_hold(self):
        request=copy.deepcopy(self.request);request['native_projection']=None
        request['standalone_green_counts']=[12,11,17]
        self.rejected(request)


if __name__=='__main__':unittest.main(verbosity=2)
