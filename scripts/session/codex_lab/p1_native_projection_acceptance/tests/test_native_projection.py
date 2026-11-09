"""Synthetic observed envelope shape. No live connector/authentication claim."""
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
spec=importlib.util.spec_from_file_location('native_shape_fixture',ROOT/'fixtures.py')
F=importlib.util.module_from_spec(spec);spec.loader.exec_module(F)
PIN='6b8796a22cbc6417194a37836bcb73eafa1fe94507303694ad29696b7e64eadb'


class NativeProjectionAcceptance(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(prefix='native-projection-');self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name).resolve();self.target=self.root/'actual_selected.py'
        source=Path(os.environ.get('PROJECTION_TARGET',str(ROOT/'baseline/matcher.py')))
        self.pin=os.environ.get('PROJECTION_TARGET_SHA256',PIN)
        shutil.copyfile(source,self.target)
        if hashlib.sha256(self.target.read_bytes()).hexdigest()!=self.pin:raise RuntimeError('CAPABILITY_HOLD: actual source pin mismatch')

    def invoke(self,value):
        env=dict(PATH=str(Path(sys.executable).parent)+':/usr/bin:/bin',PYTHONDONTWRITEBYTECODE='1')
        run=subprocess.run([sys.executable,'-I','-S','-B',str(ROOT/'worker.py'),str(self.root),str(self.target),self.pin,
            os.environ['PROJECTION_PARENT_NETNS'],'selected'],input=json.dumps(value),text=True,env=env,cwd=self.root,
            capture_output=True,timeout=10)
        if run.returncode:raise RuntimeError('CAPABILITY_HOLD: actual API/containment failed: '+run.stderr)
        output=json.loads(run.stdout);self.assertTrue(output['inputs_unchanged'],'P1_NATIVE_INPUT_REWRITTEN')
        self.assertEqual({'actual_selected.py'},{p.name for p in self.root.iterdir()})
        self.assertEqual(self.pin,hashlib.sha256(self.target.read_bytes()).hexdigest())
        result=output['result'];self.assertIsInstance(result,dict)
        self.assertIn(result.get('action'),('matched','hold'))
        self.assertTrue(isinstance(result.get('reason'),str) and result['reason'])
        self.assertIsNot(True,result.get('send_allowed'))
        self.assertIsNot(True,result.get('activation_authority'))
        return result

    def matched(self,value,marker):
        first=value['window_proof']['native_pages'][0]
        self.assertNotIn('cursor',first['request'])
        self.assertIsNone(first['query_cursor'])
        self.assertNotEqual(F.WAIT,first['query_cursor'])
        result=self.invoke(value)
        self.assertEqual('matched',result['action'],marker)
        self.assertEqual((F.NEW,F.CLAIM),(result['turn_id'],result['claim_id']))
        window=result['window']
        self.assertEqual(F.VERSION,window['projection_version'])
        self.assertEqual(F.WAIT,window['wait_event_cursor'])
        self.assertEqual('post-send-through-predecessor',window['scope'])
        self.assertEqual(F.OLD,window['predecessor_turn'])
        self.assertIs(False,window['full_history_exhausted'])
        self.assertEqual(F.C2,window['native_continuation'])
        self.assertEqual(F.audit_index(value),window['audit_index'],'P1_NATIVE_RAW_PROVENANCE_OR_PHASE_LOST')

    def held(self,value):
        result=self.invoke(value)
        self.assertEqual('hold',result['action'],'P1_NATIVE_UNPROVEN_PROJECTION_MATCHED')
        self.assertNotIn('turn_id',result)
        self.assertNotIn('claim_id',result)

    def test_observed_shape_fresh_top_nontext_tools_and_metadata_is_bounded_positive(self):
        self.matched(F.case(),'P1_NATIVE_REAL_SHAPE_BOUNDED_POSITIVE_REJECTED')

    def test_real_final_answer_ack_maps_versioned_phase_without_losing_raw_value(self):
        self.matched(F.case(final_ack=True),'P1_NATIVE_FINAL_ANSWER_POSITIVE_REJECTED')

    def test_wait_cursor_or_explicit_null_is_not_a_cursorless_fresh_top_request(self):
        for cursor in (F.WAIT,None):
            with self.subTest(cursor=cursor):
                value=F.case();record=value['window_proof']['native_pages'][0]
                record['request']['cursor']=cursor;record['query_cursor']=cursor
                value['legacy_pages'][0]['request_cursor']=cursor
                self.held(value)

    def test_original_raw_response_digest_is_checked_before_projection(self):
        value=F.case();value['window_proof']['native_pages'][0]['response_sha256']='0'*64
        self.held(value)

    def test_raw_and_supplied_projection_cannot_disagree(self):
        value=F.case();value['legacy_pages'][0]['turns'][0]['id']=F.OTHER
        self.held(value)

    def test_missing_predecessor_or_opaque_cursor_gap_is_hold(self):
        for kind in ('predecessor','cursor'):
            with self.subTest(kind=kind):
                value=F.case();record=value['window_proof']['native_pages'][1]
                if kind=='predecessor':
                    record['response']['turns']=[];value['legacy_pages'][1]['turns']=[];F.seal(value)
                else:
                    wrong='{"opaque":"synthetic-gap"}'
                    record['request']['cursor']=record['query_cursor']=wrong
                    value['legacy_pages'][1]['request_cursor']=wrong
                self.held(value)

    def test_changed_fresh_top_after_walk_is_hold(self):
        value=F.case();value['window_proof']['final_snapshot']['turn_id']=F.OTHER
        self.held(value)

    def test_duplicate_full_agent_ack_is_hold(self):
        value=F.case();turn=value['window_proof']['native_pages'][0]['response']['turns'][0]
        turn['items'].append(dict(copy.deepcopy(turn['items'][3]),id='fixture-duplicate-ack'))
        value['legacy_pages'][0]['turns'][0]['items'].append(copy.deepcopy(value['legacy_pages'][0]['turns'][0]['items'][3]))
        F.seal(value);self.held(value)

    def test_wrong_thread_or_complete_claim_is_hold(self):
        for kind in ('thread','claim'):
            with self.subTest(kind=kind):
                value=F.case();record=value['window_proof']['native_pages'][0]
                if kind=='thread':
                    record['response']['thread']['id']=F.OTHER;value['legacy_pages'][0]['threadId']=F.OTHER
                else:
                    item=record['response']['turns'][0]['items'][3]
                    body=json.loads(item['text'].split(' ',1)[1]);body['claim_id']=F.OTHER
                    item['text']='LAB_DISPATCH_ACK '+F.canonical(body)
                    value['legacy_pages'][0]['turns'][0]['items'][3]['text']=item['text']
                F.seal(value);self.held(value)

    def test_tool_command_reasoning_and_preview_are_not_ack_sources(self):
        value=F.case();record=value['window_proof']['native_pages'][0];items=record['response']['turns'][0]['items']
        fake=items[3]['text'];items[3]['text']='synthetic ordinary agent message'
        items[0]['name']=fake;items[1]['command']=fake;items[2]['summary']=[fake]
        record['response']['thread']['preview']=fake
        value['legacy_pages'][0]['turns'][0]['items'][3]['text']=items[3]['text']
        F.seal(value);self.held(value)

    def test_quoted_or_partial_agent_ack_is_not_whole_message(self):
        for kind in ('quote','suffix'):
            with self.subTest(kind=kind):
                value=F.case();item=value['window_proof']['native_pages'][0]['response']['turns'][0]['items'][3]
                item['text']='> '+item['text'] if kind=='quote' else item['text']+'\nother content'
                value['legacy_pages'][0]['turns'][0]['items'][3]['text']=item['text']
                F.seal(value);self.held(value)

    def test_truncated_or_unknown_completeness_is_hold(self):
        for truncated in (True,None):
            with self.subTest(truncated=truncated):
                value=F.case();value['window_proof']['native_pages'][0]['response_truncated']=truncated
                self.held(value)


if __name__=='__main__':unittest.main(verbosity=2)
