"""Test-owned fixed ordinary native response fixture; real validators consume it."""
import copy
import hashlib
import json
import re
import os
import subprocess
from synthetic_inputs import repository,reviews,receipt,health,zip_bytes,ROLES,REPO,RID,raw


def build(runtime):
    t=runtime.test;M=runtime.merge
    # Make a real next business commit WITHOUT touching approved control bytes.
    index=runtime.root/'.ordinary-fixture-index'
    env=dict(t.env,GIT_INDEX_FILE=str(index))
    def git(*args,data=None):
        r=subprocess.run(['git','--no-replace-objects','-C',str(runtime.root),*args],env=env,
                         input=data,capture_output=True,timeout=10)
        if r.returncode:raise AssertionError('E2E_ORDINARY_OBJECT_SETUP_FAILED: '+r.stderr.decode())
        return r.stdout.decode().strip()
    git('read-tree',M)
    blob=git('hash-object','-w','--stdin',data=b'synthetic actual ordinary business change\n')
    git('update-index','--add','--cacheinfo','100644',blob,'business.txt')
    tree=git('write-tree')
    N=git('commit-tree',tree,'-p',M,'-m','Synthetic next ordinary card')
    index.unlink()
    runtime.ordinary_head=N
    rows=reviews(N)
    approval=dict(number=61,head=N,base=M,target='main',repo_id=RID,mode='normal',
        required_reviews=['首轮','终审'],reviewers={'首轮':'codex-首轮-one-design','终审':'codex-终审-one-test'},
        review_ids={'首轮':501,'终审':502},run_id=710,run_attempt=1,author='one-platform')
    pr=dict(number=61,state='open',draft=False,merged=False,title='impl(lab): 合成普通业务卡（#62）',
        body='Closes #62\n\nCodex: '+ROLES['one-platform'],labels=[],changed_files=1,
        head=dict(sha=N,ref='synthetic-next',repo=repository()),base=dict(sha=M,ref='main',repo=repository()))
    association=dict(number=61,head=copy.deepcopy(pr['head']),base=copy.deepcopy(pr['base']))
    run=dict(id=710,run_attempt=1,event='pull_request',head_branch='synthetic-next',head_sha=N,
        path='.github/workflows/lab-pr-ci.yml',status='completed',conclusion='success',repository=repository(),
        created_at='2026-01-01T00:00:00Z',pull_requests=[association])
    main_run=dict(id=701,run_attempt=1,event='push',head_branch='main',head_sha=M,
        path='.github/workflows/lab-pr-ci.yml',status='completed',conclusion='success',repository=repository())
    normal_zip=zip_bytes({'receipt.json':receipt(N,M,710,1)})
    main_zip=zip_bytes({'health.json':health(M)})
    template=runtime.material.payloads[('new','.github/ISSUE_TEMPLATE/card.md')].decode()
    sections=re.findall(r'^## (.+?)\s*$',template,re.M)
    kinds=re.search(r'选一：([^。]*)。',template)
    kind=kinds.group(1).split('/')[0].strip().split()[0]
    issue_body='\n\n'.join('## '+section+'\n'+(kind if section=='类型' else '合成普通验收内容') for section in sections)
    issue=dict(number=62,state='open',title='impl(lab): 合成普通业务卡',body=issue_body,labels=[dict(name='会话:one-platform')])
    static={'':repository(), 'issues/62':issue,
        'pulls/61/closing-issues':dict(closingIssuesReferences=[dict(number=62,repository=dict(name=REPO.split('/')[1],owner=dict(login='YeliangDu')))]),
        'pulls/61/files?per_page=100':[[dict(filename='business.txt',status='modified')]],
        'pulls/61/reviews?per_page=100':[rows],
        f'actions/runs?event=pull_request&head_sha={N}&per_page=100':[dict(total_count=1,workflow_runs=[run])],
        f'actions/runs?event=push&branch=main&head_sha={M}&per_page=100':[dict(total_count=1,workflow_runs=[main_run])],
        'actions/runs/710/attempts/1/jobs?per_page=100':[dict(total_count=1,jobs=[dict(id=810,name='Lab exact-head verification',
            head_sha=N,conclusion='success',runner_group_name='GitHub Actions',labels=['ubuntu-24.04'])])],
        'actions/runs/701/attempts/1/jobs?per_page=100':[dict(total_count=1,jobs=[dict(id=801,name='Lab main health',
            head_sha=M,conclusion='success',runner_group_name='GitHub Actions',labels=['ubuntu-24.04'])])],
        'actions/runs/710/artifacts?per_page=100':[dict(total_count=1,artifacts=[dict(id=910,name=f'lab-pr-61-{N}-1',expired=False)])],
        'actions/runs/701/artifacts?per_page=100':[dict(total_count=1,artifacts=[dict(id=901,name=f'lab-main-health-{M}-1',expired=False)])],
        'actions/artifacts/910/zip':normal_zip,'actions/artifacts/901/zip':main_zip}
    return approval,pr,static


def native_response(runtime,scope):
    binding=runtime.request['ordinary']['github_read']['scope']
    expected=dict(binding=binding,repository=REPO,repository_id=RID,number=61,head=runtime.ordinary_head,
                  base=runtime.merge,target='main',path=scope['path'],pages=scope['pages'],binary=scope['binary'])
    if scope!=expected:raise AssertionError('E2E_ORDINARY_RAW_SCOPE_MOVED')
    path=scope['path'];current=runtime.actual_main()
    pr=copy.deepcopy(runtime.ordinary_pr)
    if path=='git/ref/heads/main':value=dict(ref='refs/heads/main',object=dict(sha=current,type='commit'))
    elif path=='pulls/61':
        if runtime.ordinary_merge and current==runtime.ordinary_merge:
            pr.update(state='closed',merged=True,merge_commit_sha=current)
        value=pr
    elif runtime.ordinary_merge and path=='commits/'+runtime.ordinary_merge:
        value=dict(sha=runtime.ordinary_merge,parents=[dict(sha=runtime.merge),dict(sha=runtime.ordinary_head)])
    elif path in runtime.ordinary_static:value=runtime.ordinary_static[path]
    else:raise AssertionError('E2E_ORDINARY_NATIVE_ENDPOINT_NOT_FROZEN: '+path)
    return value if isinstance(value,bytes) else raw(value)
