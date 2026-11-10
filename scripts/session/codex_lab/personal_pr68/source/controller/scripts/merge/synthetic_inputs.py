"""Test-owned synthetic raw native records bound to exact scratch objects."""
import copy
import hashlib
import io
import json
import zipfile

RID=1449415666
REPO='YeliangDu/forcitis-one-psa-codex-lab'
LANES=('light','secret','shadow','frontend','backend')
ROLES={'one-platform':'11111111-1111-4111-8111-111111111111',
       'one-design':'22222222-2222-4222-8222-222222222222',
       'one-test':'33333333-3333-4333-8333-333333333333'}


def raw(value):
    return (json.dumps(value,sort_keys=True,separators=(',',':'),allow_nan=False)+'\n').encode()


def zip_bytes(files):
    data=io.BytesIO()
    with zipfile.ZipFile(data,'w') as output:
        for name,value in files.items():
            info=zipfile.ZipInfo(name,date_time=(2026,1,1,0,0,0))
            output.writestr(info,value if isinstance(value,bytes) else raw(value))
    return data.getvalue()


def repository():
    return dict(id=RID,full_name=REPO,private=False)


def reviews(head):
    return [dict(id=identity,commit_id=head,state='COMMENTED',body=
        f'复审结论：通过\ncommit：{head}\n复审者：codex-{kind}-{role}\n\nSynthetic independent review.')
        for identity,kind,role in [(501,'首轮','one-design'),(502,'终审','one-test')]]


def receipt(head,base,run=700,attempt=2):
    return dict(head_sha=head,base_sha=base,observed_head=head,run_id=str(run),run_attempt=str(attempt),
                ready=True,lanes={lane:dict(applicability='required',proof='passed',outcome='success') for lane in LANES})


def health(merge):
    return dict(kind='main-health',event='push',head_sha=merge,observed_head=merge,
        run_id=701,run_attempt=1,healthy=True,checks=dict(pin='success',light='success',secret='success'))


def build(runtime):
    t=runtime.test;O=t.old;H=t.candidate;M=runtime.merge
    old=runtime.manifests['old']['digest'];new=runtime.manifests['new']['digest']
    independent='.github/workflows/lab-22-integrated-cutover.yml'
    witness_path='.github/workflows/lab-22-public-old-main-witness.yml'
    blob=lambda commit,path:t.git('rev-parse',commit+':'+path)
    rows=reviews(H)
    cp=dict(repository=REPO,repository_id=RID,number=42,base=O,head=H,target='main',
        old_control_commit=O,independent_source=O,old_manifest=old,new_manifest=new,required_lanes=list(LANES),
        workflow=dict(path='.github/workflows/lab-pr-ci.yml',source_commit=H,source_blob=blob(H,'.github/workflows/lab-pr-ci.yml')),
        run_id=700,run_attempt=2,aggregation_job_id=800,upload_step=4,artifact_id=900,
        artifact_name=f'lab-pr-42-{H}-2',expected_tests=1,expected_skip_cases=[])
    rec=receipt(H,O)
    summary=dict(head=H,run='700',attempt='2',tests=1,failures=0,errors=0,skipped=0,integration_classes=1,
                 slots=['1','2','3','4','unit'])
    zipped=zip_bytes({'receipt.json':rec,'backend-summary.json':summary})
    cp.update(zip_sha256=hashlib.sha256(zipped).hexdigest(),receipt_sha256=hashlib.sha256(raw(rec)).hexdigest())
    approval=dict(version=1,kind='control-upgrade',upgrade_id='44444444-4444-4444-8444-444444444444',
        repository=REPO,repository_id=RID,number=42,base=O,head=H,target='main',old_manifest=old,new_manifest=new,
        author='one-platform',proof=dict(source_commit=O,workflow_path=independent,workflow_blob=blob(O,independent),
            run_id=700,attempt=2,artifact_id=900,sha256=cp['receipt_sha256']),
        reviews={kind:dict(id=row['id'],reviewer='codex-'+kind+'-'+role,head=H,verdict='pass')
            for row,kind,role in zip(rows,['首轮','终审'],['one-design','one-test'])})
    bp=dict(repository=REPO,repository_id=RID,old_base=O,old_control_commit=O,old_manifest=old,
        workflow=dict(source_commit=O,path=witness_path,blob=blob(O,witness_path)),run_id=600,run_attempt=1,
        job_id=601,step_number=4,artifact_id=602,artifact_name='synthetic-old-main',
        branch='lab/22-public-old-main-witness',required_checks=['pin','light','secret'])
    witness=dict(kind='public-old-main-observation',event='isolated-cutover',head_sha=O,observed_head=O,
        run_id='600',run_attempt='1',checks=dict(pin='success',light='success',secret='success'),
        baseline_ok=True,release_authority=False)
    run=lambda identity,attempt,event,head,branch:dict(id=identity,run_attempt=attempt,event=event,head_sha=head,
        head_branch=branch,path='.github/workflows/lab-pr-ci.yml',status='completed',conclusion='success',repository=repository())
    baseline_run=run(600,1,'push',O,bp['branch']);baseline_run['path']=witness_path
    pr=dict(number=42,state='open',draft=False,merged=False,title='ci(lab): 合成受保护切换（#22）',
        body='Closes #22\n\nCodex: '+ROLES['one-platform'],head=dict(sha=H,ref='synthetic-protected',repo=repository()),
        base=dict(sha=O,ref='main',repo=repository()))
    baseline=dict(repository=repository(),current_main=dict(ref='refs/heads/main',object=dict(sha=O)),
        source=dict(commit=O,path=witness_path,blob=bp['workflow']['blob'],independent=True),run=baseline_run,
        latest=dict(run_id=600,run_attempt=1),jobs=[dict(id=601,name='Synthetic old witness',head_sha=O,
        conclusion='success',runner_group_name='GitHub Actions',labels=['ubuntu-24.04'],
        steps=[dict(number=4,name='Upload pinned witness',conclusion='success')])],
        artifacts=[dict(id=602,name='synthetic-old-main',expired=False)])
    candidate=dict(repository=repository(),current_pr=pr,source_evidence={k:cp[k] for k in
        ('independent_source','old_control_commit','old_manifest','new_manifest','workflow')},
        run=run(700,2,'pull_request',H,'synthetic-protected'),latest=dict(run_id=700,run_attempt=2),
        jobs=[dict(id=800,name='Lab exact-head verification',head_sha=H,conclusion='success',
        runner_group_name='GitHub Actions',labels=['ubuntu-24.04'],steps=[dict(number=4,name='Upload exact-head evidence',conclusion='success')])],
        artifacts=[dict(id=900,name=cp['artifact_name'],expired=False,workflow_run=dict(id=700,head_sha=H,repository_id=RID))],
        skip_cases=[])
    fault=runtime.fault
    if fault=='old-head':candidate['current_pr']['head']['sha']=O
    if fault=='old-base':candidate['current_pr']['base']['sha']=H
    if fault=='fake-green-ci':candidate['jobs'][0]['steps'][0]['conclusion']='failure'
    registry=dict(roles=[dict(role=k,id=v) for k,v in ROLES.items()])
    if fault=='shared-review-resident':registry['roles'][2]['id']=registry['roles'][1]['id']
    return dict(approval=approval,baseline_pins=bp,candidate_pins=cp,baseline=baseline,candidate=candidate,
        witness=raw(witness),zip=zipped,rows=rows,registry=registry,
        independent_workflow=dict(path=independent,blob=blob(O,independent)),
        review_hashes={kind:hashlib.sha256(row['body'].encode()).hexdigest() for kind,row in zip(['首轮','终审'],rows)})


def adoption(runtime,approval):
    M=runtime.merge
    proof=dict(repository=REPO,repository_id=RID,upgrade_id=approval['upgrade_id'],number=42,
        base=approval['base'],head=approval['head'],target='main',merge_sha=M,main_before=M,main_after=M,loaded_commit=M,
        manifest_evidence=dict(old_manifest=approval['old_manifest'],new_manifest=approval['new_manifest'],
            loaded_manifest=approval['new_manifest'],source_commit=approval['proof']['source_commit'],workflow_blob=approval['proof']['workflow_blob']),
        health=dict(run=dict(id=701,run_attempt=1,event='push',head_branch='main',head_sha=M,
            path='.github/workflows/lab-pr-ci.yml',status='completed',conclusion='success',repository=repository()),
            latest_run_id=701,latest_attempt=1,
            job=dict(id=801,name='Lab main health',head_sha=M,conclusion='success',runner_group_name='GitHub Actions',labels=['ubuntu-24.04']),
            artifact=dict(id=901,name=f'lab-main-health-{M}-1',expired=False,
                workflow_run=dict(id=701,head_sha=M,repository_id=RID)),payload=health(M)))
    if runtime.fault=='successor-health-failed':proof['health']['run']['conclusion']='failure'
    return proof
