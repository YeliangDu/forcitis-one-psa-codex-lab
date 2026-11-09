"""Deterministic TEST-ONLY native proof bytes. No repository imports or I/O ports."""
import base64
import copy
import hashlib
import io
import json
import zipfile

RID=1449415666
REPO='YeliangDu/forcitis-one-psa-codex-lab'
BASE,HEAD,MERGE,SOURCE=(c*40 for c in 'abce')
LANES=('light','secret','shadow','frontend','backend')
PHASE_PATHS={'old': ['.github/ISSUE_TEMPLATE/card.md', '.github/workflows/lab-pr-ci.yml', '.gitleaks.toml', '.gitleaksignore', 'policy.json', 'scripts/ci/backend_verify.sh', 'scripts/ci/lab_backend_lane.sh', 'scripts/ci/lab_backend_shards.py', 'scripts/ci/legacy_it.sh', 'scripts/ci/light.sh', 'scripts/ci/light_triggers.sh', 'scripts/ci/relevant_change.py', 'scripts/codegen/generate_api.sh', 'scripts/guards/acceptance_reports.py', 'scripts/merge/canonical_storage_transition.py', 'scripts/merge/cutover_e2e_driver.py', 'scripts/merge/issue_gate.py', 'scripts/merge/mq_lab.py', 'scripts/merge/mq_lab_active.py', 'scripts/merge/mq_lab_authorization.py', 'scripts/merge/mq_lab_control_authorization.py', 'scripts/merge/mq_lab_control_install.py', 'scripts/merge/mq_lab_control_manifest.py', 'scripts/merge/mq_lab_control_plan.py', 'scripts/merge/mq_lab_control_receipt.py', 'scripts/merge/mq_lab_cutover_channel.py', 'scripts/merge/mq_lab_cutover_entry.py', 'scripts/merge/mq_lab_cutover_live.py', 'scripts/merge/mq_lab_cutover_native.py', 'scripts/merge/mq_lab_cutover_old.py', 'scripts/merge/mq_lab_cutover_recovery.py', 'scripts/merge/mq_lab_cutover_successor.py', 'scripts/merge/mq_lab_journal.py', 'scripts/merge/mq_lab_journal_v2.py', 'scripts/merge/mq_lab_operator.py', 'scripts/merge/mq_lab_proof.py', 'scripts/merge/mq_lab_runtime.py', 'scripts/merge/mq_lab_trust.py', 'scripts/merge/mq_queue.py', 'scripts/merge/post_review.py', 'scripts/session/codex_lab/install_gitleaks.sh', 'scripts/session/codex_lab/install_hosted_shellcheck.sh', 'scripts/session/codex_lab/lab_main_health.py', 'scripts/session/codex_lab/lab_pr_ci.py', 'scripts/session/codex_lab/lab_queue_evidence.py', 'scripts/session/codex_lab/lab_queue_git_writer.py', 'scripts/session/codex_lab/lab_queue_live.py', 'scripts/session/codex_lab/lab_queue_release.py'], 'new': ['.github/ISSUE_TEMPLATE/card.md', '.github/workflows/lab-pr-ci.yml', '.gitleaks.toml', '.gitleaksignore', 'policy.json', 'scripts/ci/backend_verify.sh', 'scripts/ci/lab_backend_lane.sh', 'scripts/ci/lab_backend_shards.py', 'scripts/ci/legacy_it.sh', 'scripts/ci/light.sh', 'scripts/ci/light_triggers.sh', 'scripts/ci/relevant_change.py', 'scripts/codegen/generate_api.sh', 'scripts/guards/acceptance_reports.py', 'scripts/merge/canonical_storage_transition.py', 'scripts/merge/cutover_e2e_driver.py', 'scripts/merge/issue_gate.py', 'scripts/merge/mq_lab.py', 'scripts/merge/mq_lab_active.py', 'scripts/merge/mq_lab_authorization.py', 'scripts/merge/mq_lab_control_authorization.py', 'scripts/merge/mq_lab_control_install.py', 'scripts/merge/mq_lab_control_manifest.py', 'scripts/merge/mq_lab_control_plan.py', 'scripts/merge/mq_lab_control_receipt.py', 'scripts/merge/mq_lab_cutover_channel.py', 'scripts/merge/mq_lab_cutover_entry.py', 'scripts/merge/mq_lab_cutover_live.py', 'scripts/merge/mq_lab_cutover_native.py', 'scripts/merge/mq_lab_cutover_old.py', 'scripts/merge/mq_lab_cutover_ordinary.py', 'scripts/merge/mq_lab_cutover_recovery.py', 'scripts/merge/mq_lab_cutover_successor.py', 'scripts/merge/mq_lab_journal.py', 'scripts/merge/mq_lab_journal_v2.py', 'scripts/merge/mq_lab_operator.py', 'scripts/merge/mq_lab_proof.py', 'scripts/merge/mq_lab_runtime.py', 'scripts/merge/mq_lab_trust.py', 'scripts/merge/mq_queue.py', 'scripts/merge/post_review.py', 'scripts/session/codex_lab/install_gitleaks.sh', 'scripts/session/codex_lab/install_hosted_shellcheck.sh', 'scripts/session/codex_lab/lab_main_health.py', 'scripts/session/codex_lab/lab_pr_ci.py', 'scripts/session/codex_lab/lab_queue_evidence.py', 'scripts/session/codex_lab/lab_queue_git_writer.py', 'scripts/session/codex_lab/lab_queue_live.py', 'scripts/session/codex_lab/lab_queue_release.py']}
PHASE_MODES={'old': {'.github/ISSUE_TEMPLATE/card.md': '100644', '.github/workflows/lab-pr-ci.yml': '100644', '.gitleaks.toml': '100644', '.gitleaksignore': '100644', 'policy.json': '100644', 'scripts/ci/backend_verify.sh': '100755', 'scripts/ci/lab_backend_lane.sh': '100755', 'scripts/ci/lab_backend_shards.py': '100644', 'scripts/ci/legacy_it.sh': '100755', 'scripts/ci/light.sh': '100755', 'scripts/ci/light_triggers.sh': '100644', 'scripts/ci/relevant_change.py': '100644', 'scripts/codegen/generate_api.sh': '100644', 'scripts/guards/acceptance_reports.py': '100644', 'scripts/merge/canonical_storage_transition.py': '100644', 'scripts/merge/cutover_e2e_driver.py': '100644', 'scripts/merge/issue_gate.py': '100644', 'scripts/merge/mq_lab.py': '100644', 'scripts/merge/mq_lab_active.py': '100644', 'scripts/merge/mq_lab_authorization.py': '100644', 'scripts/merge/mq_lab_control_authorization.py': '100644', 'scripts/merge/mq_lab_control_install.py': '100644', 'scripts/merge/mq_lab_control_manifest.py': '100644', 'scripts/merge/mq_lab_control_plan.py': '100644', 'scripts/merge/mq_lab_control_receipt.py': '100644', 'scripts/merge/mq_lab_cutover_channel.py': '100644', 'scripts/merge/mq_lab_cutover_entry.py': '100644', 'scripts/merge/mq_lab_cutover_live.py': '100644', 'scripts/merge/mq_lab_cutover_old.py': '100644', 'scripts/merge/mq_lab_cutover_recovery.py': '100644', 'scripts/merge/mq_lab_cutover_successor.py': '100644', 'scripts/merge/mq_lab_journal.py': '100644', 'scripts/merge/mq_lab_journal_v2.py': '100644', 'scripts/merge/mq_lab_operator.py': '100644', 'scripts/merge/mq_lab_proof.py': '100644', 'scripts/merge/mq_lab_runtime.py': '100644', 'scripts/merge/mq_lab_trust.py': '100644', 'scripts/merge/mq_queue.py': '100644', 'scripts/merge/post_review.py': '100644', 'scripts/session/codex_lab/install_gitleaks.sh': '100644', 'scripts/session/codex_lab/install_hosted_shellcheck.sh': '100755', 'scripts/session/codex_lab/lab_main_health.py': '100644', 'scripts/session/codex_lab/lab_pr_ci.py': '100644', 'scripts/session/codex_lab/lab_queue_evidence.py': '100644', 'scripts/session/codex_lab/lab_queue_git_writer.py': '100644', 'scripts/session/codex_lab/lab_queue_live.py': '100644', 'scripts/session/codex_lab/lab_queue_release.py': '100644'}, 'new': {'.github/ISSUE_TEMPLATE/card.md': '100644', '.github/workflows/lab-pr-ci.yml': '100644', '.gitleaks.toml': '100644', '.gitleaksignore': '100644', 'policy.json': '100644', 'scripts/ci/backend_verify.sh': '100755', 'scripts/ci/lab_backend_lane.sh': '100755', 'scripts/ci/lab_backend_shards.py': '100644', 'scripts/ci/legacy_it.sh': '100755', 'scripts/ci/light.sh': '100755', 'scripts/ci/light_triggers.sh': '100644', 'scripts/ci/relevant_change.py': '100644', 'scripts/codegen/generate_api.sh': '100644', 'scripts/guards/acceptance_reports.py': '100644', 'scripts/merge/canonical_storage_transition.py': '100644', 'scripts/merge/cutover_e2e_driver.py': '100644', 'scripts/merge/issue_gate.py': '100644', 'scripts/merge/mq_lab.py': '100644', 'scripts/merge/mq_lab_active.py': '100644', 'scripts/merge/mq_lab_authorization.py': '100644', 'scripts/merge/mq_lab_control_authorization.py': '100644', 'scripts/merge/mq_lab_control_install.py': '100644', 'scripts/merge/mq_lab_control_manifest.py': '100644', 'scripts/merge/mq_lab_control_plan.py': '100644', 'scripts/merge/mq_lab_control_receipt.py': '100644', 'scripts/merge/mq_lab_cutover_channel.py': '100644', 'scripts/merge/mq_lab_cutover_entry.py': '100644', 'scripts/merge/mq_lab_cutover_live.py': '100644', 'scripts/merge/mq_lab_cutover_old.py': '100644', 'scripts/merge/mq_lab_cutover_recovery.py': '100644', 'scripts/merge/mq_lab_cutover_successor.py': '100644', 'scripts/merge/mq_lab_journal.py': '100644', 'scripts/merge/mq_lab_journal_v2.py': '100644', 'scripts/merge/mq_lab_operator.py': '100644', 'scripts/merge/mq_lab_proof.py': '100644', 'scripts/merge/mq_lab_runtime.py': '100644', 'scripts/merge/mq_lab_trust.py': '100644', 'scripts/merge/mq_queue.py': '100644', 'scripts/merge/post_review.py': '100644', 'scripts/session/codex_lab/install_gitleaks.sh': '100644', 'scripts/session/codex_lab/install_hosted_shellcheck.sh': '100755', 'scripts/session/codex_lab/lab_main_health.py': '100644', 'scripts/session/codex_lab/lab_pr_ci.py': '100644', 'scripts/session/codex_lab/lab_queue_evidence.py': '100644', 'scripts/session/codex_lab/lab_queue_git_writer.py': '100644', 'scripts/session/codex_lab/lab_queue_live.py': '100644', 'scripts/session/codex_lab/lab_queue_release.py': '100644', 'scripts/merge/mq_lab_cutover_ordinary.py': '100644'}}
CHANGED_PATHS=['.github/workflows/lab-pr-ci.yml', 'scripts/merge/mq_lab.py', 'scripts/merge/mq_lab_authorization.py', 'scripts/merge/mq_lab_operator.py', 'scripts/merge/mq_lab_proof.py', 'scripts/session/codex_lab/lab_main_health.py', 'scripts/session/codex_lab/lab_pr_ci.py', 'scripts/session/codex_lab/lab_queue_evidence.py', 'scripts/session/codex_lab/lab_queue_git_writer.py']


def raw(value):return json.dumps(value,ensure_ascii=False,sort_keys=True,separators=(',',':'),allow_nan=False).encode()
def sha(value):return hashlib.sha256(value).hexdigest()
def blob(value):return hashlib.sha1(b'blob '+str(len(value)).encode()+b'\0'+value).hexdigest()
def repo():return dict(id=RID,full_name=REPO,private=False)

def archive(files):
    stream=io.BytesIO()
    with zipfile.ZipFile(stream,'w',compression=zipfile.ZIP_STORED) as zipped:
        for name,value in files.items():
            info=zipfile.ZipInfo(name,date_time=(2026,1,1,0,0,0))
            zipped.writestr(info,value if isinstance(value,bytes) else raw(value))
    return stream.getvalue()


def manifest(contents,modes):
    entries=[dict(path=path,mode=modes.get(path,'100644'),blob=blob(content),sha256=sha(content)) for path,content in sorted(contents.items())]
    value=dict(version=1,entries=entries)
    return dict(value,digest=sha(raw(value)))


def build(actual):
    global SOURCE
    SOURCE=actual['source_commit']
    workflow='.github/workflows/lab-pr-ci.yml'
    independent='.github/workflows/lab-22-integrated-cutover.yml'
    witness_path='.github/workflows/lab-22-public-old-main-witness.yml'
    old_contents={workflow:b'name: Synthetic old policy\non: pull_request\npermissions: {contents: read}\njobs: {}\n',
                  independent:b'name: Synthetic independently pinned verifier\non: workflow_dispatch\npermissions: {contents: read}\njobs: {}\n',
                  witness_path:b'name: Synthetic old-main witness\non: push\npermissions: {contents: read}\njobs: {}\n'}
    new_contents=dict(old_contents);new_contents[workflow]=b'name: Synthetic new public policy\non: pull_request\npermissions: {contents: read}\njobs: {}\n'
    # Runtime/module/resource bytes are exact staged Git objects, never dummy
    # text masquerading as loaded code. Only logical subject IDs stay synthetic.
    old_contents={path:base64.b64decode(actual['files'][path]['content_base64']) for path in PHASE_PATHS['old']}
    new_contents={path:base64.b64decode(actual['files'][path]['content_base64']) for path in PHASE_PATHS['new']}
    old_contents[workflow]=base64.b64decode(actual['old_workflow_base64'])
    source_contents={path:base64.b64decode(entry['content_base64']) for path,entry in actual['files'].items()}
    old_manifest,new_manifest=manifest(old_contents,PHASE_MODES['old']),manifest(new_contents,PHASE_MODES['new'])
    roles={'one-platform':'11111111-1111-4111-8111-111111111111',
           'one-design':'22222222-2222-4222-8222-222222222222','one-test':'33333333-3333-4333-8333-333333333333'}
    review_rows=[dict(id=i,commit_id=HEAD,state='COMMENTED',body=f'复审结论：通过\ncommit：{HEAD}\n复审者：codex-{kind}-{role}\n\nSynthetic exact native review.',
                     user=dict(id=i+1000,login='synthetic-'+role))
                 for i,kind,role in [(501,'首轮','one-design'),(502,'终审','one-test')]]
    receipt=dict(head_sha=HEAD,base_sha=BASE,observed_head=HEAD,run_id='700',run_attempt='2',ready=True,
        lanes={name:dict(applicability='required',proof='passed',outcome='success') for name in LANES})
    summary=dict(head=HEAD,run='700',attempt='2',tests=1,failures=0,errors=0,skipped=0,integration_classes=1,slots=['1','2','3','4','unit'])
    candidate_zip=archive({'receipt.json':receipt,'backend-summary.json':summary})
    witness=dict(kind='public-old-main-observation',event='isolated-cutover',head_sha=BASE,observed_head=BASE,
                 run_id='600',run_attempt='1',checks=dict(pin='success',light='success',secret='success'),baseline_ok=True,release_authority=False)
    baseline_zip=archive({'health.json':witness})
    baseline_pins=dict(repository=REPO,repository_id=RID,old_base=BASE,old_control_commit=BASE,
        old_manifest=old_manifest['digest'],workflow=dict(source_commit=SOURCE,path=witness_path,blob=blob(source_contents[witness_path])),
        run_id=600,run_attempt=1,job_id=601,step_number=4,artifact_id=602,artifact_name='native-test-old-main',
        branch='lab/22-public-old-main-witness',required_checks=['pin','light','secret'],witness_sha256=sha(raw(witness)))
    candidate_pins=dict(repository=REPO,repository_id=RID,number=42,base=BASE,head=HEAD,target='main',old_control_commit=BASE,
        independent_source=SOURCE,old_manifest=old_manifest['digest'],new_manifest=new_manifest['digest'],required_lanes=list(LANES),
        workflow=dict(path=workflow,source_commit=HEAD,source_blob=blob(new_contents[workflow])),run_id=700,run_attempt=2,
        aggregation_job_id=800,upload_step=4,artifact_id=900,artifact_name=f'lab-pr-42-{HEAD}-2',
        zip_sha256=sha(candidate_zip),receipt_sha256=sha(raw(receipt)),expected_tests=1,expected_skip_cases=[])
    subject=dict(repository_id=RID,number=42,base=BASE,head=HEAD,target='main')
    independent_workflow=dict(path=independent,source_commit=SOURCE,source_blob=blob(source_contents[independent]))
    bindings=dict(repository_id=RID,repository=REPO,number=42,base=BASE,head=HEAD,target='main',old_control_commit=BASE,
        old_manifest=old_manifest['digest'],new_manifest=new_manifest['digest'],workflow=independent_workflow,
        run_id=500,run_attempt=1,event='push',subject=subject)
    checks={name:dict(job_id=851+i,selection_sha256=sha(('synthetic-'+name).encode()),tests=1,
                     conclusion='success',failures=0,errors=0,skipped=0) for i,name in enumerate(LANES)}
    aggregate=dict(bindings,checks=checks)
    observation=dict(facts_checked=True,control_source=SOURCE,
        subject={k:subject[k] for k in ('number','base','head','target')},actual_draft=False,
        complete_candidate_proof=True,allows_new_write=False,release_authority=False,installation_authority=False)
    aggregate_zip=archive({'observation.json':observation})
    independent_expected=dict(bindings,aggregation_job=dict(id=850,name='Independent control aggregation'),
        artifact=dict(id=950,name='native-test-independent-proof',sha256=sha(raw(aggregate))),
        required_checks={name:{k:value[k] for k in ('job_id','selection_sha256','tests')} for name,value in checks.items()})
    approval=dict(version=1,kind='control-upgrade',upgrade_id='11111111-1111-4111-8111-111111111111',repository=REPO,
        repository_id=RID,number=42,base=BASE,head=HEAD,target='main',old_manifest=old_manifest['digest'],new_manifest=new_manifest['digest'],author='one-platform',
        proof=dict(source_commit=SOURCE,workflow_path=independent,workflow_blob=blob(source_contents[independent]),run_id=500,attempt=1,artifact_id=950,sha256=sha(aggregate_zip)),
        reviews={kind:dict(id=row['id'],reviewer='codex-'+kind+'-'+role,head=HEAD,verdict='pass')
                 for kind,role,row in zip(['首轮','终审'],['one-design','one-test'],review_rows)})
    pr=dict(number=42,state='open',merged=False,draft=False,title='ci(lab): 合成受保护 native 入口（#22）',
        body='Closes #22\n\nCodex: '+roles['one-platform'],labels=[],changed_files=1,
        head=dict(sha=HEAD,ref='codex/synthetic-cutover',repo=repo()),base=dict(sha=BASE,ref='main',repo=repo()))
    association=dict(number=42,head=copy.deepcopy(pr['head']),base=copy.deepcopy(pr['base']))
    def run(i,attempt,event,head,path,branch):
        return dict(id=i,run_attempt=attempt,event=event,head_sha=head,path=path,head_branch=branch,
                    status='completed',conclusion='success',repository=repo(),created_at='2026-01-01T00:00:00Z',
                    pull_requests=[copy.deepcopy(association)] if event=='pull_request' else [])
    runs={500:run(500,1,'push',SOURCE,independent,'codex/22-control-cutover'),
          600:run(600,1,'push',SOURCE,witness_path,'lab/22-public-old-main-witness'),
          700:run(700,2,'pull_request',HEAD,workflow,'codex/synthetic-cutover'),701:run(701,1,'push',MERGE,workflow,'main')}
    def job(i,name,head,upload=False):
        value=dict(id=i,name=name,head_sha=head,status='completed',conclusion='success',runner_group_name='GitHub Actions',labels=['ubuntu-24.04'])
        if upload:value['steps']=[dict(number=4,name='Run actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02',status='completed',conclusion='success')]
        return value
    jobs={500:[job(850,'integrated',SOURCE,True)]+[job(851+i,name,SOURCE) for i,name in enumerate(LANES)],
          600:[job(601,'Independent old-main witness',SOURCE,True)],700:[job(800,'Lab exact-head verification',HEAD,True)],
          701:[job(801,'Lab main health',MERGE,True)]}
    zipped={950:aggregate_zip,602:baseline_zip,900:candidate_zip}
    native_health=dict(kind='main-health',event='push',head_sha=MERGE,observed_head=MERGE,run_id=701,run_attempt=1,
                       healthy=True,checks=dict(pin='success',light='success',secret='success'))
    zipped[901]=archive({'health.json':native_health})
    artifacts={}
    for runid,identity,name in [(500,950,'native-test-independent-proof'),(600,602,'native-test-old-main'),
                               (700,900,f'lab-pr-42-{HEAD}-2'),(701,901,f'lab-main-health-{MERGE}-1')]:
        artifacts[runid]=[dict(id=identity,name=name,expired=False,digest='sha256:'+sha(zipped[identity]),
                              workflow_run=dict(id=runid,head_sha=runs[runid]['head_sha'],repository_id=RID))]
    baseline_native=dict(repository=repo(),current_main=dict(ref='refs/heads/main',object=dict(sha=BASE)),
        source=dict(commit=SOURCE,path=witness_path,blob=blob(source_contents[witness_path]),independent=True),run=runs[600],
        latest=dict(run_id=600,run_attempt=1),jobs=jobs[600],artifacts=artifacts[600],witness_raw_base64=base64.b64encode(raw(witness)).decode())
    candidate_native=dict(repository=repo(),current_pr=pr,source_evidence={k:candidate_pins[k] for k in
        ('independent_source','old_control_commit','old_manifest','new_manifest','workflow')},run=runs[700],
        latest=dict(run_id=700,run_attempt=2),jobs=jobs[700],artifacts=artifacts[700],skip_cases=[])
    adoption=dict(repository=REPO,repository_id=RID,upgrade_id=approval['upgrade_id'],number=42,base=BASE,head=HEAD,target='main',
        merge_sha=MERGE,main_before=MERGE,main_after=MERGE,loaded_commit=MERGE,
        manifest_evidence=dict(old_manifest=old_manifest['digest'],new_manifest=new_manifest['digest'],loaded_manifest=new_manifest['digest'],
            source_commit=SOURCE,workflow_blob=blob(source_contents[independent])),
        health=dict(run={k:runs[701][k] for k in ('id','run_attempt','event','head_branch','head_sha','path','status','conclusion','repository')},
            latest_run_id=701,latest_attempt=1,job={k:jobs[701][0][k] for k in ('id','name','head_sha','conclusion','runner_group_name','labels')},
            artifact={k:artifacts[701][0][k] for k in ('id','name','expired','workflow_run')},payload=native_health))
    source_material={commit:{path:dict(mode=PHASE_MODES['old' if commit in (BASE,SOURCE) else 'new'].get(path,'100644'),blob=blob(content),sha256=sha(content),content_base64=base64.b64encode(content).decode())
                             for path,content in contents.items()} for commit,contents in [(BASE,old_contents),(HEAD,new_contents),(SOURCE,source_contents),(MERGE,new_contents)]}
    control_runtime={name:dict(path=path,sha256=actual['files'][path]['sha256']) for name,path in actual['runtime_paths'].items()}
    control_resources={'policy.json':dict(path='policy.json',sha256=actual['files']['policy.json']['sha256'])}
    independent_execution=dict(branch='codex/22-control-cutover',job_id=850,job_name='integrated',job_key='integrated',
        upload_step=4,upload_name='Run actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02',
        upload_action='actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02',artifact_name='native-test-independent-proof')
    review_pins={kind:dict(native_user_id=row['user']['id'],body_sha256=sha(row['body'].encode())) for kind,row in zip(['首轮','终审'],review_rows)}
    native_proof=dict(source_paths=dict(old=sorted(old_contents),new=sorted(new_contents)),baseline_pins=baseline_pins,
        candidate_pins=candidate_pins,review_pins=review_pins,independent_execution=independent_execution,
        control_runtime=control_runtime,control_resources=control_resources)
    policies={};responses={};git_reads={}
    for stage in ('before','merged-awaiting-health','healthy'):
        loaded=BASE if stage=='before' else MERGE
        policies[stage]=dict(repository=REPO,repository_id=RID,expected_approval=approval,installed_source_commit=loaded,
            independent_proof_source=SOURCE,independent_proof_workflow=independent,
            independent_proof_workflow_blob=blob(source_contents[independent]),old_manifest=old_manifest['digest'],
            new_manifest=new_manifest['digest'],native_proof=native_proof,skip_cases=[])
        table={}
        def put(path,value,pages=False,binary=False):
            key=json.dumps([path,pages,binary],separators=(',',':'))
            table[key]={'base64':base64.b64encode(value).decode()} if binary else {'json':value}
        current=BASE if stage=='before' else MERGE
        observed_pr=copy.deepcopy(pr)
        if stage!='before':observed_pr.update(state='closed',merged=True,merge_commit_sha=MERGE)
        put('',repo());put('pulls/42',observed_pr);put('git/ref/heads/main',dict(ref='refs/heads/main',object=dict(sha=current,type='commit')))
        put('commits/'+MERGE,dict(sha=MERGE,parents=[dict(sha=BASE),dict(sha=HEAD)]))
        put('pulls/42/reviews?per_page=100',[review_rows],True)
        put('pulls/42/files?per_page=100',[[dict(filename=workflow,status='modified')]],True)
        for runid,row in runs.items():
            attempt=row['run_attempt']
            put(f'actions/runs/{runid}',row);put(f'actions/runs/{runid}/attempts/{attempt}',row)
            jobvalue=dict(total_count=len(jobs[runid]),jobs=jobs[runid])
            put(f'actions/runs/{runid}/attempts/{attempt}/jobs?per_page=100',[jobvalue],True)
            put(f'actions/runs/{runid}/artifacts?per_page=100',[dict(total_count=1,artifacts=artifacts[runid])],True)
        lists=[(f'actions/runs?event=pull_request&head_sha={HEAD}&per_page=100',[runs[700]]),
               (f'actions/runs?event=push&branch=codex/22-control-cutover&head_sha={SOURCE}&per_page=100',[runs[500]]),
               (f'actions/runs?event=push&branch=lab/22-public-old-main-witness&head_sha={SOURCE}&per_page=100',[runs[600]]),
               (f'actions/runs?event=push&branch=main&head_sha={MERGE}&per_page=100',[runs[701]] if stage=='healthy' else [])]
        for path,rows in lists:put(path,[dict(total_count=len(rows),workflow_runs=rows)],True)
        for identity,content in zipped.items():put(f'actions/artifacts/{identity}/zip',content,binary=True)
        for records in artifacts.values():
            for item in records:put('actions/artifacts/'+str(item['id']),item)
        upload_log=('SHA256 digest of uploaded artifact zip is '+sha(aggregate_zip)+'\n'
            +'Artifact native-test-independent-proof.zip successfully finalized. Artifact ID 950\n').encode()
        put('actions/jobs/850/logs',upload_log,binary=True)
        # Emit nonrecursive Git API trees exactly as Source.file traverses them.
        def tree_api(entries):
            nodes={}
            for path,entry in entries.items():
                node=nodes
                parts=path.split('/')
                for part in parts[:-1]:node=node.setdefault(part,{})
                node[parts[-1]]=entry
            def make(node):
                rows=[];serialized=b''
                for name,value in sorted(node.items(),key=lambda pair:pair[0]+('/' if 'blob' not in pair[1] else '')):
                    if 'blob' in value:
                        mode=value['mode'];oid=value['blob'];kind='blob'
                    else:mode='040000';oid=make(value);kind='tree'
                    rows.append(dict(path=name,mode=mode,type=kind,sha=oid))
                    serialized+=(('40000' if kind=='tree' else mode)+' '+name).encode()+b'\0'+bytes.fromhex(oid)
                oid=hashlib.sha1(b'tree '+str(len(serialized)).encode()+b'\0'+serialized).hexdigest()
                put('git/trees/'+oid,dict(sha=oid,truncated=False,tree=rows))
                return oid
            return make(nodes)
        for commit,entries in source_material.items():
            for path,entry in entries.items():
                content=base64.b64decode(entry['content_base64'])
                put('git/blobs/'+entry['blob'],dict(sha=entry['blob'],encoding='base64',content=entry['content_base64'],size=len(content)))
            root=tree_api(entries)
            if commit==SOURCE:
                root=actual['source_tree']
                for oid,value in actual['trees'].items():put('git/trees/'+oid,value)
            put('git/commits/'+commit,dict(sha=commit,tree=dict(sha=root)))
        responses[stage]=table
        reads={json.dumps(['rev-parse','HEAD'],separators=(',',':')):loaded,
               json.dumps(['status','--porcelain','--untracked-files=all'],separators=(',',':')):'',
               json.dumps(['status','--porcelain'],separators=(',',':')):''}
        for commit,entries in source_material.items():
            for path,entry in entries.items():
                reads[json.dumps(['rev-parse',commit+':'+path],separators=(',',':'))]=entry['blob']
                reads[json.dumps(['rev-parse','--verify',commit+':'+path],separators=(',',':'))]=entry['blob']
                reads[json.dumps(['show',commit+':'+path],separators=(',',':'))]=base64.b64decode(entry['content_base64']).decode()
        reads[json.dumps(['rev-parse','--verify','refs/heads/main'],separators=(',',':'))]=current
        git_reads[stage]=reads
    fixture=dict(version=1,subject=dict(repository_id=RID,repository=REPO,number=42,base=BASE,head=HEAD,target='main',merge=MERGE),
        approval=approval,policy_by_stage=policies,raw_get=responses,git_reads=git_reads)
    reference=dict(old_manifest=old_manifest,new_manifest=new_manifest,baseline_native=baseline_native,
        candidate_native=candidate_native,independent_receipt_raw_base64=base64.b64encode(raw(observation)).decode(),
        candidate_zip_sha256=sha(candidate_zip),baseline_witness_sha256=sha(raw(witness)),successor_adoption=adoption,
        review_body_sha256={kind:pin['body_sha256'] for kind,pin in review_pins.items()})
    return fixture,reference
