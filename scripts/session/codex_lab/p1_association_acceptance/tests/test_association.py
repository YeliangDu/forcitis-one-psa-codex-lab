"""Independent isolated bounded association, not native dispatch authority."""
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
spec=importlib.util.spec_from_file_location('synthetic_association_inputs',ROOT/'fixtures.py')
F=importlib.util.module_from_spec(spec);spec.loader.exec_module(F)
BASELINE='6faec4f779cf74c43b2e05a3dd318862be7c88433c4ffb2345baa6587cc53340'


class BoundedAssociationAcceptance(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(prefix='association-fixture-');self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name).resolve()
        source=Path(os.environ.get('ASSOCIATION_TARGET',str(ROOT/'baseline/resident_app_matcher.py')))
        self.pin=os.environ.get('ASSOCIATION_TARGET_SHA256',BASELINE)
        self.mode=os.environ.get('ASSOCIATION_MODE','baseline')
        self.target=self.root/'selected_matcher.py';shutil.copyfile(source,self.target)
        if hashlib.sha256(self.target.read_bytes()).hexdigest()!=self.pin:raise RuntimeError('CAPABILITY_HOLD: source pin')

    def invoke(self,value):
        before=hashlib.sha256(self.target.read_bytes()).hexdigest()
        env=dict(PATH=str(Path(sys.executable).parent)+':/usr/bin:/bin',PYTHONDONTWRITEBYTECODE='1')
        result=subprocess.run([sys.executable,'-I','-S','-B',str(ROOT/'worker.py'),str(self.root),str(self.target),self.pin,
            os.environ['ASSOCIATION_PARENT_NETNS'],self.mode],input=json.dumps(value),text=True,env=env,cwd=self.root,
            capture_output=True,timeout=10)
        if result.returncode:raise RuntimeError('CAPABILITY_HOLD: actual API/containment failure: '+result.stderr)
        self.assertEqual(before,hashlib.sha256(self.target.read_bytes()).hexdigest())
        self.assertEqual({'selected_matcher.py'},{p.name for p in self.root.iterdir()},'P1_ASSOC_SIDE_EFFECT')
        output=json.loads(result.stdout);self.assertTrue(output['inputs_unchanged'],'P1_ASSOC_INPUT_MUTATED')
        response=output['result'];self.assertIsInstance(response,dict)
        self.assertIn(response.get('action'),('matched','hold'))
        self.assertIsInstance(response.get('reason'),str)
        self.assertTrue(response['reason'])
        self.assertLessEqual(len(response['reason']),512)
        self.assertIsNot(True,response.get('send_allowed'))
        self.assertIsNot(True,response.get('activation_authority'))
        return response

    def matched_bounded(self,value,marker):
        result=self.invoke(value)
        self.assertEqual('matched',result['action'],marker)
        self.assertEqual(F.NEW,result['turn_id'])
        self.assertEqual(value['claim']['dispatch_claim_id'],result['claim_id'])
        window=result['window']
        self.assertEqual('post-send-through-predecessor',window['scope'])
        self.assertEqual(F.OLD,window['predecessor_turn'])
        self.assertIs(False,window['full_history_exhausted'])
        self.assertEqual(F.C2,window['native_continuation'])
        self.assertEqual(F.audit_index(value),window['audit_index'],'P1_ASSOC_RAW_CURSOR_OR_ENVELOPE_REWRITTEN')

    def held(self,value):
        result=self.invoke(value)
        self.assertEqual('hold',result['action'],'P1_ASSOC_UNPROVEN_WINDOW_ASSOCIATED')
        self.assertNotIn('turn_id',result)
        self.assertNotIn('claim_id',result)

    def test_bounded_initial_retains_older_continuation_and_matches_unique_ack(self):
        self.matched_bounded(F.case(),'P1_ASSOC_BOUNDED_INITIAL_REJECTED_AS_TRUNCATED')

    def test_bounded_resume_reaches_terminal_predecessor_without_history_exhaustion(self):
        self.matched_bounded(F.case(resume=True),'P1_ASSOC_BOUNDED_RESUME_REJECTED_AS_TRUNCATED')

    def test_truly_exhausted_control_executes_existing_api(self):
        value=F.case(exhausted=True)
        self.assertIsNone(value['window_proof']['native_pages'][-1]['response']['page']['nextCursor'])
        self.assertIs(False,value['window_proof']['native_pages'][-1]['response']['page']['hasMore'])
        result=self.invoke(value)
        self.assertEqual(('matched',F.NEW),(result['action'],result['turn_id']))

    def test_missing_predecessor_is_not_bounded_completeness(self):
        value=F.case();value['legacy_pages'][1]['turns']=[]
        value['window_proof']['native_pages'][1]['response']['turns']=[]
        self.held(value)

    def test_missing_cursor_continuity_is_hold(self):
        value=F.case();value['legacy_pages'][1]['request_cursor']='{"opaque":"skipped"}'
        value['window_proof']['native_pages'][1]['query_cursor']='{"opaque":"skipped"}'
        self.held(value)

    def test_wrong_thread_is_hold(self):
        value=F.case();value['legacy_pages'][1]['threadId']=F.OTHER
        value['window_proof']['native_pages'][1]['response']['thread']['id']=F.OTHER
        self.held(value)

    def test_wrong_full_claim_is_hold(self):
        value=F.case();item=value['legacy_pages'][0]['turns'][0]['items'][0]
        body=json.loads(item['text'].split(' ',1)[1]);body['claim_id']=F.OTHER
        item['text']='LAB_DISPATCH_ACK '+F.canonical(body)
        value['window_proof']['native_pages'][0]['response']['turns'][0]['items'][0]['text']=item['text']
        self.held(value)

    def test_duplicate_full_ack_is_hold(self):
        value=F.case()
        value['legacy_pages'][0]['turns'][0]['items']*=2
        items=value['window_proof']['native_pages'][0]['response']['turns'][0]['items']
        items.append(dict(copy.deepcopy(items[0]),id='fixture-second-ack-item'))
        self.held(value)

    def test_tool_output_ack_is_not_authority(self):
        value=F.case();value['legacy_pages'][0]['turns'][0]['items'][0]['type']='functionCallOutput'
        raw=value['window_proof']['native_pages'][0]['response']['turns'][0]['items'][0]
        raw['type']='functionCallOutput';raw.pop('phase')
        self.held(value)

    def test_moved_top_after_walk_requires_new_observation(self):
        value=F.case();value['window_proof']['final_snapshot']['turn_id']=F.OTHER
        self.held(value)

    def test_quote_or_partial_ack_is_hold(self):
        for kind in ('quote','suffix'):
            with self.subTest(kind=kind):
                value=F.case();item=value['legacy_pages'][0]['turns'][0]['items'][0]
                item['text']='> '+item['text'] if kind=='quote' else item['text']+'\nnot-the-whole-ack'
                value['window_proof']['native_pages'][0]['response']['turns'][0]['items'][0]['text']=item['text']
                self.held(value)


if __name__=='__main__':unittest.main(verbosity=2)
