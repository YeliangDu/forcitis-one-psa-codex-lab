"""Narrow dependency regression; synthetic omission, never live completeness."""
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
spec=importlib.util.spec_from_file_location('synthetic_command_fixture',ROOT/'fixtures.py')
F=importlib.util.module_from_spec(spec);spec.loader.exec_module(F)
PIN='7863877ee59d27566475d189cef0eae9058352d14317c570fe0ff63179e305e1'


class CommandOmissionAcceptance(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(prefix='command-omission-');self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name).resolve();self.target=self.root/'actual_selected.py'
        source=Path(os.environ.get('PROJECTION_TARGET',str(ROOT/'baseline/matcher.py')))
        self.pin=os.environ.get('PROJECTION_TARGET_SHA256',PIN);shutil.copyfile(source,self.target)
        if hashlib.sha256(self.target.read_bytes()).hexdigest()!=self.pin:raise RuntimeError('CAPABILITY_HOLD: target pin')

    def omission(self):
        value=F.case()
        # A new synthetic variant is constructed before its raw bytes are sealed.
        # No real capture is edited and absence never supplies capture completeness.
        value['window_proof']['native_pages'][0]['response']['turns'][0]['items'][1].pop('commandTruncated')
        F.seal(value)
        return value

    def invoke(self,value):
        env=dict(PATH=str(Path(sys.executable).parent)+':/usr/bin:/bin',PYTHONDONTWRITEBYTECODE='1')
        result=subprocess.run([sys.executable,'-I','-S','-B',str(ROOT/'worker.py'),str(self.root),str(self.target),self.pin,
            os.environ['PROJECTION_PARENT_NETNS'],'selected'],input=json.dumps(value),text=True,env=env,cwd=self.root,
            capture_output=True,timeout=10)
        if result.returncode:raise RuntimeError('CAPABILITY_HOLD: actual function/fixture/containment: '+result.stderr)
        output=json.loads(result.stdout);self.assertTrue(output['inputs_unchanged'],'COMMAND_FLAG_RAW_INPUT_REWRITTEN')
        self.assertEqual({'actual_selected.py'},{p.name for p in self.root.iterdir()})
        self.assertEqual(self.pin,hashlib.sha256(self.target.read_bytes()).hexdigest())
        answer=output['result'];self.assertIn(answer.get('action'),('matched','hold'))
        self.assertIsNot(True,answer.get('send_allowed'));self.assertIsNot(True,answer.get('activation_authority'))
        return answer

    def held(self,value):
        answer=self.invoke(value);self.assertEqual('hold',answer['action'])
        self.assertNotIn('turn_id',answer);self.assertNotIn('claim_id',answer)

    def test_ordinary_nonagent_command_flag_absent_with_independent_fixture_completeness_matches(self):
        value=self.omission();record=value['window_proof']['native_pages'][0]
        self.assertIs(False,record['response_truncated'])  # Explicit independent fixture premise only.
        self.assertNotIn('commandTruncated',record['response']['turns'][0]['items'][1])
        expected_raw=record['response_utf8'];expected_sha=record['response_sha256']
        answer=self.invoke(value)
        self.assertEqual('matched',answer['action'],'P1_NONAGENT_COMMAND_FLAG_OMISSION_REJECTED')
        self.assertEqual((F.NEW,F.CLAIM),(answer['turn_id'],answer['claim_id']))
        window=answer['window'];self.assertIs(False,window['full_history_exhausted'])
        self.assertEqual(F.C2,window['native_continuation']);self.assertEqual(F.audit_index(value),window['audit_index'])
        self.assertEqual(expected_sha,window['audit_index'][0]['raw_response_sha256'])
        self.assertEqual(expected_sha,hashlib.sha256(expected_raw.encode()).hexdigest())
        self.assertEqual([dict(turn_id=F.NEW,item_id='fixture-command',commandTruncated_present=False,
                              commandTruncated_value=None,command_or_cwd_authority=False)],window['nonagent_command_audit'])

    def test_missing_whole_window_completeness_is_hold(self):
        value=self.omission();value['window_proof']['native_pages'][0]['response_truncated']=None
        self.held(value)

    def test_false_whole_window_completeness_is_hold(self):
        value=self.omission();value['window_proof']['native_pages'][0]['response_truncated']=True
        self.held(value)

    def test_nonagent_fake_marker_cannot_replace_exact_agent_ack(self):
        value=self.omission();record=value['window_proof']['native_pages'][0];items=record['response']['turns'][0]['items']
        fake=items[3]['text'];items[3]['text']='synthetic ordinary message';items[1]['command']=fake
        items[0]['name']=fake;items[2]['summary']=[fake]
        value['legacy_pages'][0]['turns'][0]['items'][3]['text']=items[3]['text'];F.seal(value)
        self.held(value)

    def test_explicit_true_truncated_command_policy_is_preserved(self):
        value=F.case();value['window_proof']['native_pages'][0]['response']['turns'][0]['items'][1]['commandTruncated']=True
        F.seal(value);self.held(value)


if __name__=='__main__':unittest.main(verbosity=2)
