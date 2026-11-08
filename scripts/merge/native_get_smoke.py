"""Hosted-only real GET smoke; never constructs approval, queue, journal or writer."""
import argparse
import base64
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess

RID=1449415666
REPO='YeliangDu/forcitis-one-psa-codex-lab'
MAX=4*1024*1024


def need(ok,reason):
    if not ok:raise AssertionError(reason)


def full(value,width):
    return type(value)is str and re.fullmatch('[0-9a-f]{'+str(width)+'}',value) is not None


def read_pinned(path,digest):
    need(full(digest,64),'EXTERNAL_PIN_SHA_REQUIRED')
    with Path(path).open('rb') as stream:raw=stream.read(MAX+1)
    need(len(raw)<=MAX and hashlib.sha256(raw).hexdigest()==digest,'EXTERNAL_PIN_BYTES_CHANGED')
    return raw


def smoke(params,scope_raw):
    need(os.environ.get('GITHUB_ACTIONS')=='true' and os.environ.get('GITHUB_REPOSITORY')==REPO
         and os.environ.get('GITHUB_REPOSITORY_ID')==str(RID),'HOSTED_EXACT_LAB_CONTEXT_REQUIRED')
    fields={'version','repository','repository_id','number','checkout_head','base','head','main_tip',
            'independent_ref','independent_source','scope_sha256','module_sha256','trees'}
    need(type(params)is dict and set(params)==fields and type(params['version'])is int and params['version']==1,
         'SMOKE_PARAMS_SCHEMA')
    need(params['repository']==REPO and type(params['repository_id'])is int and params['repository_id']==RID
         and type(params['number'])is int and params['number']==43,'SMOKE_NOT_EXACT_LAB_PR')
    need(all(full(params[key],40) for key in ('checkout_head','base','head','main_tip','independent_source')),
         'FINAL_ACTUAL_H_BASE_SOURCE_PINS_REQUIRED')
    need(full(params['scope_sha256'],64) and full(params['module_sha256'],64),'EXTERNAL_SCOPE_AND_SOURCE_PIN_REQUIRED')
    need(hashlib.sha256(scope_raw).hexdigest()==params['scope_sha256'],'SMOKE_SCOPE_SHA_MISMATCH')
    scope=json.loads(scope_raw)
    for key in ('repository','repository_id','number','base','head','main_tip','independent_ref','independent_source'):
        need(type(scope.get(key))is type(params[key]) and scope[key]==params[key],'SMOKE_SCOPE_SUBJECT_MISMATCH')
    need(scope.get('stage')=='observe' and scope.get('merge_sha') is None,'SMOKE_OBSERVE_ONLY')
    need(set(scope.get('object_roots',[]))=={params['base'],params['head'],params['independent_source']},'EXACT_THREE_ROOTS_REQUIRED')
    workspace=Path(os.environ['GITHUB_WORKSPACE'])
    need(workspace.is_absolute() and workspace.resolve(strict=True)==workspace
         and scope.get('object_root')==str(workspace),'SMOKE_CURRENT_PHYSICAL_CHECKOUT_REQUIRED')
    # Two-commit protocol: immutable source C is checked out, real event D is
    # its immediate single-parent wrapper. Params never choose/override D.
    wrapper_head=os.environ.get('GITHUB_SHA')
    need(full(wrapper_head,40) and wrapper_head!=params['checkout_head'],
         'SMOKE_DISTINCT_NATIVE_WRAPPER_EVENT_REQUIRED')
    need(type(params['trees'])is dict and set(params['trees'])=={'old','new'},'EXACT_TWO_TREE_PINS_REQUIRED')
    for phase in ('old','new'):
        pin=params['trees'][phase]
        need(type(pin)is dict and set(pin)=={'tree','policy_blob','policy_sha256'}
             and full(pin['tree'],40) and full(pin['policy_blob'],40) and full(pin['policy_sha256'],64),
             'EXACT_TREE_AND_POLICY_RAW_PINS_REQUIRED')
    here=Path(__file__).resolve().parent
    source=here/'mq_lab_native_get.py'
    need(source.is_file() and hashlib.sha256(source.read_bytes()).hexdigest()==params['module_sha256'],
         'SCOPED_GET_SOURCE_NOT_INDEPENDENTLY_PINNED')
    spec=importlib.util.spec_from_file_location('test_owned_readonly_native_get',source)
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    commands=[];audit=[]
    native_commands=set()
    for route in scope['routes']:
        path=route['path']
        argv=['gh','api','--hostname','github.com','--method','GET','repos/'+REPO+('/'+path if path else '')]
        if route['pages']:argv+=['--paginate','--slurp']
        native_commands.add(tuple(argv))
    git_prefix=['git','--no-replace-objects','-C',str(workspace)]
    def checked_run(argv,**kwargs):
        need(type(argv)is list and not kwargs.get('shell') and 'env' not in kwargs and 'cwd' not in kwargs,
             'SMOKE_NO_AMBIENT_EXECUTION_OVERRIDE')
        allowed=tuple(argv) in native_commands
        if argv[:len(git_prefix)]==git_prefix:
            tail=argv[len(git_prefix):]
            allowed=(tail==['rev-parse','--verify','HEAD']
                     or tail==['show','-s','--format=%P',wrapper_head]
                     or tail in (['cat-file','--batch'],['cat-file','--batch-check']))
            if tail and tail[0]=='cat-file':
                data=kwargs.get('input')
                need(type(data)is bytes and len(data)<=128*41 and data.endswith(b'\n')
                     and all(full(x.decode('ascii'),40) for x in data.splitlines()),'SMOKE_GIT_OBJECT_BATCH_NOT_FIXED_IDS')
        need(allowed,'SMOKE_WRITE_OR_UNLISTED_COMMAND_REFUSED')
        commands.append(tuple(argv))
        return subprocess.run(argv,**kwargs)
    checkout=checked_run(git_prefix+['rev-parse','--verify','HEAD'],stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=20,check=False)
    need(checkout.returncode==0 and checkout.stdout.decode().strip()==params['checkout_head'],'ACTUAL_CHECKOUT_NOT_EXACT_PIN')
    parents=checked_run(git_prefix+['show','-s','--format=%P',wrapper_head],
        stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=20,check=False)
    need(parents.returncode==0 and parents.stdout.decode().split()==[params['checkout_head']],
         'SMOKE_WRAPPER_NOT_SINGLE_IMMEDIATE_CHILD_OF_PINNED_SOURCE')
    def record(meta,raw):
        # Do not print/store bodies, token or Git blob contents in smoke logs.
        need(type(raw)is bytes,'SMOKE_AUDIT_RAW_BYTES_REQUIRED')
        audit.append(dict(meta))
    get=module.ScopedGithubGet(scope_raw,params['scope_sha256'],checked_run,record)
    get.verify_native_pins();get.prime()
    result={}
    for phase,commit in [('old',params['base']),('new',params['head'])]:
        pin=params['trees'][phase]
        observed=get('git/commits/'+commit)
        need(observed['sha']==commit and observed['tree']['sha']==pin['tree'],'EXACT_NATIVE_SUBJECT_TREE_MOVED')
        tree=get('git/trees/'+pin['tree'])
        selected=[row for row in tree['tree'] if row.get('path')=='policy.json']
        need(len(selected)==1 and selected[0].get('type')=='blob' and selected[0].get('mode')=='100644'
             and selected[0].get('sha')==pin['policy_blob'],'EXACT_POLICY_BLOB_NOT_IN_TREE')
        blob=get('git/blobs/'+pin['policy_blob'])
        content=base64.b64decode(blob['content'],validate=True)
        need(hashlib.sha256(content).hexdigest()==pin['policy_sha256'],'EXACT_POLICY_RAW_SHA_CHANGED')
        result[phase]=dict(commit=commit,tree=pin['tree'],policy_blob=pin['policy_blob'],policy_sha256=pin['policy_sha256'])
    before=len(commands)
    for denied in ('user','pulls/44','git/blobs/'+'0'*40):
        refused=False
        try:get(denied)
        except (ValueError,AssertionError):refused=True
        need(refused,'UNPERMITTED_GET_OR_UNBOUND_OBJECT_ACCEPTED')
    need(len(commands)==before,'DENIED_SCOPE_TOUCHED_SUBPROCESS')
    for denied in (['gh','api','--method','POST','repos/'+REPO+'/merges'],git_prefix+['push','origin','main']):
        refused=False
        try:checked_run(denied)
        except AssertionError:refused=True
        need(refused,'WRITE_ARGV_NOT_REFUSED')
    need(len(commands)==before,'DENIED_WRITE_STARTED_PROCESS')
    # Fresh native pins again: local object success cannot hide moving PR/main/S*.
    get.verify_native_pins()
    return dict(kind='native-scoped-get-smoke',action='observed-readonly',repository=REPO,repository_id=RID,
        number=43,base=params['base'],head=params['head'],independent_source=params['independent_source'],
        source_checkout=params['checkout_head'],native_wrapper_event=wrapper_head,
        authority=False,allows_new_write=False,installation_authority=False,ordinary_main_health=False,
        trees=result,command_count=len(commands),audit=audit)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--params',required=True);parser.add_argument('--params-sha256',required=True)
    parser.add_argument('--scope',required=True);parser.add_argument('--output',required=True)
    args=parser.parse_args()
    params=json.loads(read_pinned(args.params,args.params_sha256))
    scope=read_pinned(args.scope,params.get('scope_sha256'))
    result=smoke(params,scope)
    output=Path(args.output)
    need(output.is_absolute() and str(output).startswith(os.environ.get('RUNNER_TEMP','')+'/'),
         'SMOKE_OUTPUT_ONLY_RUNNER_TEMP')
    output.parent.mkdir(parents=True,exist_ok=True)
    with output.open('x') as stream:json.dump(result,stream,sort_keys=True,indent=2)
    output.chmod(0o600)
    print(json.dumps({k:v for k,v in result.items() if k!='audit'},sort_keys=True))


if __name__=='__main__':main()
