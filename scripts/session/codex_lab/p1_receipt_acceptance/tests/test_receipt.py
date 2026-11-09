"""Synthetic raw-byte provenance comparisons; never queue/admission authority."""
import base64
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
spec=importlib.util.spec_from_file_location('synthetic_receipt_inputs',ROOT/'fixtures.py')
F=importlib.util.module_from_spec(spec);spec.loader.exec_module(F)
BASELINE='543f992be547dda4d0dbe0d237788172440f3c7ee292a64ffabefa368c62dc54'


class ReceiptProvenanceAcceptance(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(prefix='receipt-fixture-');self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name).resolve()
        source=Path(os.environ.get('RECEIPT_TARGET',str(ROOT/'baseline/lab_queue_live.py')))
        self.pin=os.environ.get('RECEIPT_TARGET_SHA256',BASELINE)
        self.mode=os.environ.get('RECEIPT_MODE','baseline')
        self.target=self.root/'selected_reader.py';shutil.copyfile(source,self.target)
        if hashlib.sha256(self.target.read_bytes()).hexdigest()!=self.pin:raise RuntimeError('CAPABILITY_HOLD: source pin mismatch')

    def invoke(self,value):
        env=dict(PATH=str(Path(sys.executable).parent)+':/usr/bin:/bin',PYTHONDONTWRITEBYTECODE='1')
        result=subprocess.run([sys.executable,'-I','-S','-B',str(ROOT/'worker.py'),str(self.root),str(self.target),self.pin,
            os.environ['RECEIPT_PARENT_NETNS'],self.mode],input=json.dumps(value),text=True,env=env,cwd=self.root,
            capture_output=True,timeout=10)
        if result.returncode:raise RuntimeError('CAPABILITY_HOLD: actual reader/API/containment unavailable: '+result.stderr)
        self.assertEqual({'selected_reader.py'},{p.name for p in self.root.iterdir()},'P1_RECEIPT_SIDE_EFFECT')
        self.assertEqual(self.pin,hashlib.sha256(self.target.read_bytes()).hexdigest())
        output=json.loads(result.stdout);self.assertTrue(output['inputs_unchanged'],'P1_RECEIPT_INPUT_MUTATION')
        self.assertTrue(all(call['path']=='' or call['path'].startswith(('actions/runs','actions/artifacts/')) for call in output['get_calls']))
        rows=output['rows'];self.assertIsInstance(rows,list)
        return [row for row in rows if isinstance(row,dict) and isinstance(row.get('receipt'),dict)]

    def refused(self,value,marker):self.assertEqual([],self.invoke(value),marker)

    def test_exact_zip_and_matching_artifact_tuple_is_positive(self):
        value=F.fixture();rows=self.invoke(value)
        self.assertEqual(1,len(rows),'P1_EXACT_RECEIPT_PROVENANCE_NOT_ACCEPTED')
        self.assertEqual(value['receipt'],rows[0]['receipt'])
        self.assertEqual(F.ARTIFACT,rows[0]['id'])
        self.assertEqual(value['metadata']['digest'],rows[0]['digest'])

    def test_declared_raw_zip_digest_mismatch_cannot_expose_receipt(self):
        value=F.fixture();value['metadata']['digest']='sha256:'+'0'*64
        self.refused(value,'P1_RECEIPT_DECLARED_ZIP_DIGEST_MISMATCH_TRUSTED')

    def test_tampered_zip_same_json_is_not_the_approved_immutable_archive(self):
        value=F.fixture();original=base64.b64decode(value['zip_base64'])
        changed=F.zipped(value['receipt'],comment=b'synthetic-raw-byte-tamper')
        self.assertNotEqual(hashlib.sha256(original).hexdigest(),hashlib.sha256(changed).hexdigest())
        value['zip_base64']=base64.b64encode(changed).decode()
        self.refused(value,'P1_RECEIPT_TAMPERED_RAW_ZIP_TRUSTED')

    def test_wrong_independent_raw_zip_pin_is_refused(self):
        value=F.fixture();value['expected']['zip_sha256']='0'*64
        self.refused(value,'P1_RECEIPT_WRONG_EXTERNAL_ZIP_PIN_TRUSTED')

    def test_wrong_receipt_run_with_matching_zip_hash_is_refused(self):
        value=F.fixture();receipt=copy.deepcopy(value['receipt']);receipt['run_id']=str(F.RUN+1)
        F.repack(value,receipt)
        self.refused(value,'P1_RECEIPT_OTHER_BODY_RUN_TRUSTED')

    def test_wrong_repository_artifact_association_is_refused(self):
        value=F.fixture();value['metadata']['workflow_run']['repository_id']=F.RID+1
        self.refused(value,'P1_RECEIPT_FOREIGN_ARTIFACT_REPOSITORY_TRUSTED')

    def test_wrong_actual_run_artifact_association_is_refused(self):
        value=F.fixture();value['metadata']['workflow_run']['id']=F.RUN+1
        self.refused(value,'P1_RECEIPT_OTHER_RUN_ARTIFACT_TRUSTED')

    def test_wrong_selected_artifact_id_is_refused(self):
        value=F.fixture();value['metadata']['id']=F.ARTIFACT+1
        self.refused(value,'P1_RECEIPT_OTHER_ARTIFACT_ID_TRUSTED')

    def test_wrong_metadata_head_association_is_refused(self):
        value=F.fixture();value['metadata']['workflow_run']['head_sha']='e'*40
        self.refused(value,'P1_RECEIPT_OTHER_HEAD_ASSOCIATION_TRUSTED')

    def test_wrong_receipt_base_even_with_correct_zip_hash_is_refused(self):
        value=F.fixture();receipt=copy.deepcopy(value['receipt']);receipt['base_sha']='e'*40
        F.repack(value,receipt)
        self.refused(value,'P1_RECEIPT_WRONG_EXPECTED_BASE_TRUSTED')

    def test_wrong_receipt_head_with_matching_hash_is_refused(self):
        value=F.fixture();receipt=copy.deepcopy(value['receipt'])
        receipt['head_sha']=receipt['observed_head']='e'*40
        F.repack(value,receipt)
        self.refused(value,'P1_RECEIPT_WRONG_EXPECTED_HEAD_TRUSTED')

    def test_missing_declared_zip_digest_is_not_verified_proof(self):
        value=F.fixture();value['metadata'].pop('digest')
        self.refused(value,'P1_RECEIPT_MISSING_NATIVE_DIGEST_TRUSTED')

    def test_actual_run_snapshot_wrong_attempt_is_refused(self):
        value=F.fixture();value['run']['run_attempt']=F.ATTEMPT-1
        self.refused(value,'P1_RECEIPT_STALE_ACTUAL_RUN_ATTEMPT_TRUSTED')

    def test_stale_receipt_attempt_even_with_current_name_and_digest_is_refused(self):
        value=F.fixture();receipt=copy.deepcopy(value['receipt']);receipt['run_attempt']=str(F.ATTEMPT-1)
        F.repack(value,receipt)
        self.refused(value,'P1_RECEIPT_STALE_BODY_ATTEMPT_TRUSTED')

    def test_independent_profile_applicability_cannot_be_redefined_by_receipt(self):
        value=F.fixture();receipt=copy.deepcopy(value['receipt'])
        receipt['lanes']['backend']=dict(applicability='not-applicable',proof='not-applicable',outcome='skipped')
        F.repack(value,receipt)
        self.refused(value,'P1_RECEIPT_PROFILE_SELECTION_REDEFINED')

    def test_missing_expired_or_old_name_has_no_downloaded_proof(self):
        for kind in ('missing','expired','old-name'):
            with self.subTest(kind=kind):
                value=F.fixture()
                if kind=='missing':value['missing']=True
                elif kind=='expired':value['metadata']['expired']=True
                else:value['metadata']['name']=f'lab-pr-{F.PR}-{F.H}-{F.ATTEMPT-1}'
                self.refused(value,'P1_RECEIPT_MISSING_OR_STALE_PROOF_TRUSTED')

    def test_metadata_predecoded_receipt_is_not_a_download_substitute(self):
        value=F.fixture();value['metadata']['receipt']=dict(attacker='synthetic-metadata-only')
        rows=self.invoke(value)
        self.assertEqual(1,len(rows))
        self.assertEqual(value['receipt'],rows[0]['receipt'])


if __name__=='__main__':unittest.main(verbosity=2)
