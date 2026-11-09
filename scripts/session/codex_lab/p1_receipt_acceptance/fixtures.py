"""Synthetic recorded GET fixtures; no actual hosted/native authority claims."""
import base64
import hashlib
import io
import json
import zipfile

B='77e872e8ba9661d3294d3a9337c8e5c956b70641'
H='b'*40
REPO='YeliangDu/forcitis-one-psa-codex-lab'
RID=1449415666
RUN,ATTEMPT,ARTIFACT,PR=700005,3,900005,62005


def zipped(receipt,comment=b''):
    output=io.BytesIO()
    with zipfile.ZipFile(output,'w') as archive:
        info=zipfile.ZipInfo('receipt.json',date_time=(2026,1,1,0,0,0));info.external_attr=0o100644<<16
        archive.writestr(info,json.dumps(receipt,sort_keys=True,separators=(',',':')).encode())
        archive.comment=comment
    return output.getvalue()


def fixture():
    receipt=dict(head_sha=H,observed_head=H,base_sha=B,run_id=str(RUN),run_attempt=str(ATTEMPT),ready=True,
        lanes={k:dict(applicability='required',proof='passed',outcome='success') for k in ('light','secret','shadow','frontend','backend')})
    raw=zipped(receipt);digest=hashlib.sha256(raw).hexdigest()
    repo=dict(id=RID,full_name=REPO,private=False)
    run=dict(id=RUN,run_attempt=ATTEMPT,repository=repo,event='pull_request',path='.github/workflows/lab-pr-ci.yml',
        head_sha=H,status='completed',conclusion='success',head_branch='fixture/receipt-branch',
        pull_requests=[dict(number=PR,head=dict(sha=H,ref='fixture/receipt-branch',repo=repo),base=dict(sha=B,ref='main',repo=repo))])
    metadata=dict(id=ARTIFACT,name=f'lab-pr-{PR}-{H}-{ATTEMPT}',expired=False,digest='sha256:'+digest,
        workflow_run=dict(id=RUN,repository_id=RID,head_repository_id=RID,head_sha=H,head_branch='fixture/receipt-branch'))
    expected=dict(repository=REPO,repository_id=RID,number=PR,run_id=RUN,attempt=ATTEMPT,artifact_id=ARTIFACT,
        head=H,base=B,zip_sha256=digest,profile=dict(name='synthetic-exact-checks/1',workflow='.github/workflows/lab-pr-ci.yml',
        applicability={k:'required' for k in receipt['lanes']}))
    return dict(expected=expected,metadata=metadata,receipt=receipt,run=run,zip_base64=base64.b64encode(raw).decode(),missing=False)


def repack(value,receipt,comment=b'',pin_new_bytes=True):
    raw=zipped(receipt,comment)
    value['zip_base64']=base64.b64encode(raw).decode()
    value['receipt']=receipt
    value['metadata']['digest']='sha256:'+hashlib.sha256(raw).hexdigest()
    if pin_new_bytes:value['expected']['zip_sha256']=hashlib.sha256(raw).hexdigest()
