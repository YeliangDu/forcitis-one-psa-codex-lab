"""One consistent synthetic card replay. Never a signed/live/native authority."""
import hashlib
import importlib.util
import json
from pathlib import Path
import uuid

ROOT=Path(__file__).resolve().parent
B='77e872e8ba9661d3294d3a9337c8e5c956b70641'
CARD,PR,REV=62009,62909,7
H='b'*40
SESSION='00000000-0000-4000-8000-000000006209'


def load(name):
    spec=importlib.util.spec_from_file_location('fixture_'+name,ROOT/(name+'.py'))
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);return module


def sha(raw):return hashlib.sha256(raw).hexdigest()


def build(root):
    root=Path(root);inputs=root/'inputs';inputs.mkdir();work=root/'work';work.mkdir()
    task='SYNTHETIC_ONE_CARD_'+uuid.uuid4().hex
    claim_id=str(uuid.uuid4())
    scope='SYNTHETIC SIGNED-SCOPE CLAIM ONLY; no user/native authority. '+task
    checkpoint=dict(kind='lab-p1-authorized-checkpoint/1',card=CARD,revision=REV,role='one-test',session_id=SESSION,
        lab_root=str(root),cwd=str(work),base=B,task=task,next_action='synthetic-read-only-step')
    cp=inputs/'checkpoint.json';cpraw=(json.dumps(checkpoint,sort_keys=True)+'\n').encode();cp.write_bytes(cpraw);cp.chmod(0o600)
    P=load('projection_fixture');projection=P.case()
    projection['claim'].update(thread_id=SESSION,initial_claim_id=claim_id,dispatch_claim_id=claim_id,generation=REV,
        issue=CARD,phase='isolated-P1',cwd=str(work),head=H,handoff='.codex-lab/cards/synthetic-integrated/artifact.patch')
    prompt=('SYNTHETIC ONE CARD '+task).encode()
    projection['prompt_hex']=prompt.hex();projection['claim']['prompt_sha256']=sha(prompt)
    # All fixture envelope identities remain synthetic; update all records and
    # snapshots to the one assignment, then construct the exact whole ACK.
    for record,page in zip(projection['window_proof']['native_pages'],projection['legacy_pages']):
        record['request']['threadId']=SESSION;record['response']['thread']['id']=SESSION;page['threadId']=SESSION
    for key in ('pre_send_snapshot','initial_top','final_snapshot'):projection['window_proof'][key]['thread_id']=SESSION
    claim=projection['claim'];ack={k:claim[k] for k in ('initial_claim_id','thread_id','generation','role','issue','phase','head','cwd','handoff','prompt_sha256')};ack['claim_id']=claim_id
    text='LAB_DISPATCH_ACK '+P.canonical(ack)
    projection['window_proof']['native_pages'][0]['response']['turns'][0]['items'][3]['text']=text
    projection['legacy_pages'][0]['turns'][0]['items'][3]['text']=text;P.seal(projection)
    artifact=inputs/'artifact.patch';artifact.write_text('synthetic artifact '+task+'\n')
    R=load('receipt_fixture');verification=R.fixture();verification['expected']['number']=PR
    verification['metadata']['name']=f"lab-pr-{PR}-{H}-{verification['expected']['attempt']}"
    verification['run']['pull_requests'][0]['number']=PR
    verification['run']['head_branch']='fixture/integrated-card'
    verification['run']['pull_requests'][0]['head']['ref']='fixture/integrated-card'
    verification['metadata']['workflow_run']['head_branch']='fixture/integrated-card'
    reviews=[dict(kind=kind,head=H,reviewer=role,author='fixture-author',native_identity_proved=False)
             for kind,role in [('first','fixture-reviewer-one'),('final','fixture-reviewer-two')]]
    return dict(kind='labctl-p1-replay-request/1',action='replay-readonly',mode='untrusted-hosted-replay',
        scope=dict(card=CARD,body=scope,sha256=sha(scope.encode()),actual_user_signature_proved=False),
        assignment=dict(card=CARD,revision=REV,phase='isolated-P1',role='one-test',session_id=SESSION,cwd=str(work),branch='fixture/integrated-card',base=B,head=H,task=task),
        checkpoint=dict(path=str(cp),sha256=sha(cpraw)),claim=claim,native_projection=projection,
        artifact=dict(path=str(artifact),sha256=sha(artifact.read_bytes()),revision=REV),
        verification=verification,reviews=reviews,test_A='a'*40,frozen_acceptance=dict(A='a'*40,manifest_sha256='c'*64),candidate_control_closure=None,
        authentication=dict(native=False,hosted_producer=False,independent_review=False,permissions=False),
        intent=dict(id=claim_id,prompt_sha256=claim['prompt_sha256'],state='planned-unissued',actual_send_count=0))
