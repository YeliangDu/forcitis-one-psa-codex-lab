"""One branch lifecycle entry for CLI/coordinator and injected hosted ports.

No default transport in run_branch_command. Native adapters validate real API
shapes separately; these normalized port contracts never authorize main merge.
"""
import argparse
import copy
import importlib.util
import json
from pathlib import Path
import re


def sibling(name):
    path=Path(__file__).absolute().with_name(name+'.py')
    if path.resolve(strict=True)!=path:raise ValueError('linked-control-module')
    spec=importlib.util.spec_from_file_location('fixed_'+name,path)
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    return module


life=sibling('lab_branch_lifecycle')
require,sha,positive=life.require,life.sha,life.positive


def worktrees(git,cwd):
    text=git(['worktree','list','--porcelain'],cwd=cwd)
    require(type(text) is str and len(text)<=1024*1024,'invalid-worktree-inventory')
    rows=[];row={}
    for line in text.splitlines()+['']:
        if not line:
            if row:require('worktree' in row and ('HEAD' in row or 'bare' in row),'incomplete-worktree');rows.append(row);row={}
            continue
        key,_,value=line.partition(' ')
        require(key not in row,'duplicate-worktree-field');row[key]=value
    require(rows,'missing-worktree-inventory')
    return rows


def remote(git,cwd,ref):
    text=git(['ls-remote','--refs','origin',ref],cwd=cwd)
    require(type(text) is str,'invalid-remote-readback')
    rows=[line.split() for line in text.splitlines() if line.strip()]
    require(len(rows)<=1 and all(len(x)==2 and sha(x[0]) and x[1]==ref for x in rows),'ambiguous-remote-ref')
    return rows[0][0] if rows else None


def pull(row,b,merged=False,exact_head=True):
    require(type(row) is dict and positive(row.get('number')) and row.get('merged') is merged
            and row.get('state')==('closed' if merged else 'open') and type(row.get('draft')) is bool,'invalid-native-PR')
    for side in ('head','base'):
        require(type(row.get(side)) is dict,'missing-PR-branch');life.fixed_repo(row[side].get('repo'))
    require(row['head'].get('ref',b['branch'])==b['branch'] and row['base'].get('ref')=='main'
            and row['base'].get('sha')==b['base'] and (not exact_head or row['head'].get('sha')==b['head']), 'PR-binding-moved')
    if b['pr_number'] is not None:require(row['number']==b['pr_number'],'different-bound-PR')
    return row


def occupancy(github,b,retiring=False):
    view=github.retirement_snapshot(copy.deepcopy(b))
    require(type(view) is dict and view.get('complete') is True and view.get('queued') is False
            and type(view.get('active_ci')) is list and type(view.get('proof_pins')) is list
            and not view['proof_pins'],'queue-or-proof-occupation-unavailable')
    if retiring:
        require(not view['active_ci'] and type(view.get('other_open_pulls')) is list and not view['other_open_pulls']
                and view.get('queue_completed') is True,'live-occupation-or-queue-unfinished')
    return view


def dispatch(request,store,git,github):
    require(type(request) is dict and request.get('repository')==life.REPOSITORY
            and type(request.get('repository_id')) is int and request['repository_id']==life.RID
            and positive(request.get('card')) and request.get('role') in life.ROLES
            and sha(request.get('base')) and request.get('target')=='main','invalid-card-repository-role-or-base')
    path=Path(request['worktree']);require(path.is_absolute() and path.resolve(strict=True)==path,'unverified-worktree-path')
    cwd=str(path);common=git(['rev-parse','--path-format=absolute','--git-common-dir'],cwd=cwd).strip()
    require(Path(common).is_absolute() and Path(common).resolve()==Path(common),'nonphysical-git-common')
    top=git(['rev-parse','--show-toplevel'],cwd=cwd).strip();require(top==cwd,'worktree-root-mismatch')
    inventory=worktrees(git,cwd);require(sum(x['worktree']==cwd for x in inventory)==1,'worktree-not-registered')
    existing=store.data['bindings'].get(str(request['card']))
    if existing:
        b=existing['binding']
        require(existing['phase']!='retired' and all(request[k]==b[k] for k in
                ('repository','repository_id','card','role','worktree','base','target')),'dispatch-rebind-refused')
        if existing['phase']=='bound':return life.result('bound',existing)
    else:
        require(not any(x['phase']!='retired' and x['binding']['worktree']==cwd for x in store.data['bindings'].values()),'worktree-already-bound')
        require(not git(['status','--porcelain','--untracked-files=all'],cwd=cwd).strip(),'dirty-assigned-worktree')
        require(git(['cat-file','-t',request['base']],cwd=cwd).strip()=='commit'
                and git(['rev-parse','refs/heads/main'],cwd=cwd).strip()==request['base'],'base-is-not-verified-local-main')
        require(remote(git,cwd,'refs/heads/main')==request['base'],'base-is-not-remote-main')
        name=request.get('branch','codex/'+str(request['card'])+'-'+request['role'])
        life.branch_name(name)
        require(name.startswith('codex/'+str(request['card'])+'-'),'new-branch-not-card-scoped')
        b=dict(repository=life.REPOSITORY,repository_id=life.RID,card=request['card'],role=request['role'],worktree=cwd,
               base=request['base'],branch=name,head=request['base'],target='main',pr_number=None,common=common)
        found=github.find_pulls(copy.deepcopy(b));require(found.get('complete') is True and type(found.get('items')) is list,'PR-inventory-incomplete')
        require(len(found['items'])<=1,'ambiguous-existing-card-PR')
        if found['items']:
            native=found['items'][0];life.branch_name(native['head']['ref']);b['branch']=native['head']['ref'];b['head']=native['head']['sha']
            b['pr_number']=native['number'];pull(native,b)
        require(not any(x.get('branch')=='refs/heads/'+b['branch'] and x['worktree']!=cwd for x in inventory),'branch-in-another-worktree')
        existing=dict(binding=b,phase='dispatching',operations={})
        store.data['bindings'][str(b['card'])]=existing;store.save()
    b=existing['binding'];current=git(['symbolic-ref','--quiet','--short','HEAD'],cwd=cwd).strip()
    if current!=b['branch']:
        require(not git(['status','--porcelain','--untracked-files=all'],cwd=cwd).strip(),'dirty-assigned-worktree')
        branches=git(['for-each-ref','--format=%(refname)','refs/heads/'+b['branch']],cwd=cwd).splitlines()
        if branches:
            require(branches==['refs/heads/'+b['branch']] and git(['rev-parse','refs/heads/'+b['branch']],cwd=cwd).strip()==b['head'],'existing-local-branch-conflict')
            git(['checkout',b['branch']],cwd=cwd)
        else:git(['checkout','-b',b['branch'],b['head']],cwd=cwd)
    require(git(['rev-parse','HEAD'],cwd=cwd).strip()==b['head'],'dispatch-source-moved')
    existing['phase']='bound';store.save();return life.result('bound',existing)


def publish(request,row,store,git,github):
    b=row['binding'];cwd=b['worktree'];head=request.get('head');require(sha(head),'invalid-published-head')
    require(row['phase']=='bound' and git(['rev-parse','HEAD'],cwd=cwd).strip()==head
            and git(['symbolic-ref','--quiet','--short','HEAD'],cwd=cwd).strip()==b['branch']
            and not git(['status','--porcelain','--untracked-files=all'],cwd=cwd).strip(),'publish-worktree-not-exact-clean-binding')
    occupancy(github,b)
    ref='refs/heads/'+b['branch'];actual=remote(git,cwd,ref);pending=row['operations'].get('publish')
    if pending and pending['status']=='pending':
        require(head==pending['head'] and actual==head,'publication-unknown-readback-only')
    elif actual!=head:
        require(actual in (None,b['head']),'remote-business-head-moved')
        git(['merge-base','--is-ancestor',b['head'],head],cwd=cwd)
        row['operations']['publish']=dict(status='pending',head=head,expected=actual);store.save()
        try:git(['push','--porcelain','--force-with-lease='+ref+':'+(actual or ''),'origin',head+':'+ref],cwd=cwd)
        except Exception:return dict(action='hold',reason='publication-outcome-unknown-readback-only')
        require(remote(git,cwd,ref)==head,'publication-not-read-back')
    b['head']=head;row['operations']['publish']=dict(status='done',head=head);store.save()
    return life.result('published',row,pr_readback_required=b['pr_number'] is not None)


def ensure_pr(row,store,github):
    b=row['binding'];require(row['phase']=='bound','inactive-card')
    if b['pr_number'] is not None:
        native=pull(github.pull(b['pr_number']),b)
    else:
        found=github.find_pulls(copy.deepcopy(b));require(found.get('complete') is True and type(found.get('items')) is list,'incomplete-PR-search')
        require(len(found['items'])<=1,'multiple-card-PRs')
        if not found['items']:
            require('create_pr' not in row['operations'],'PR-create-unknown-readback-only')
            row['operations']['create_pr']=dict(status='pending',head=b['head']);store.save()
            try:github.create_pull(copy.deepcopy(b))
            except Exception:return dict(action='hold',reason='PR-create-unknown-readback-only')
            found=github.find_pulls(copy.deepcopy(b));require(found.get('complete') is True and len(found.get('items',[]))==1,'PR-create-not-read-back')
        native=pull(found['items'][0],b)
        b['pr_number']=native['number'];row['operations']['create_pr']=dict(status='done',head=b['head']);store.save()
    return life.result('bound-pr',row,pr_number=native['number'])


def selected_run(b,github):
    require(positive(b['pr_number']),'PR-not-bound');pull(github.pull(b['pr_number']),b)
    view=github.runs(copy.deepcopy(b));require(view.get('complete') is True and type(view.get('items')) is list,'incomplete-CI-inventory')
    matches=[];seen=set()
    for run in view['items']:
        require(type(run) is dict and positive(run.get('id')) and positive(run.get('run_attempt')),'invalid-run')
        key=run['id'],run['run_attempt'];require(key not in seen,'duplicate-run');seen.add(key)
        if run.get('head_sha')!=b['head'] or run.get('head_branch')!=b['branch'] or run.get('pull_request')!=b['pr_number']:continue
        life.fixed_repo(run.get('repository'))
        require(run.get('event')=='pull_request' and run.get('path','.github/workflows/lab-pr-ci.yml')=='.github/workflows/lab-pr-ci.yml','wrong-workflow')
        matches.append(run)
    require(matches,'no-current-branch-head-CI');run=max(matches,key=lambda x:(x['id'],x['run_attempt']))
    require(run.get('base_sha')==b['base'],'CI-base-moved')
    return run


def ci(action,request,row,store,github):
    b=row['binding'];run=selected_run(b,github);op=row['operations'].get('rerun')
    if action=='rerun-ci':
        rid,attempt=request.get('run_id'),request.get('run_attempt');require(positive(rid) and positive(attempt),'invalid-rerun-identity')
        if op and op['run_id']==rid and op['from_attempt']==attempt and op['head']==b['head']:
            require(run['id']==rid and run['run_attempt']>attempt,'rerun-unknown-readback-only')
        else:
            require(not op or op['status']=='done','previous-rerun-unknown')
            require((run['id'],run['run_attempt'])==(rid,attempt),'stale-rerun-request')
            row['operations']['rerun']=dict(status='pending',run_id=rid,from_attempt=attempt,head=b['head']);store.save()
            try:github.rerun(rid,attempt)
            except Exception:return dict(action='hold',reason='rerun-outcome-unknown-readback-only')
            run=selected_run(b,github);require(run['id']==rid and run['run_attempt']>attempt,'new-native-attempt-not-observed')
        row['operations']['rerun']=dict(status='done',run_id=rid,from_attempt=attempt,head=b['head'],observed_attempt=run['run_attempt']);store.save()
    elif op and op['status']=='pending' and op['head']==b['head']:
        require(run['id']==op['run_id'] and run['run_attempt']>op['from_attempt'],'rerun-unknown-readback-only')
        op.update(status='done',observed_attempt=run['run_attempt']);store.save()
    require(run.get('status')=='completed' and run.get('conclusion')=='success','latest-CI-pending-or-failed')
    return life.result('verified-ci',row,run_id=run['id'],run_attempt=run['run_attempt'],release_authority=False)


def retirement_ready(row,git,github):
    b=row['binding'];require(positive(b['pr_number']),'PR-not-bound')
    view=occupancy(github,b,retiring=True);native=pull(github.pull(b['pr_number']),b,merged=True)
    log=view.get('merge_log');proof=view.get('merged_readback')
    require(type(log) is dict and type(log.get('repository_id')) is int and log['repository_id']==life.RID
            and log.get('number')==b['pr_number'] and log.get('head')==b['head'] and log.get('base')==b['base']
            and log.get('status')=='confirmed' and sha(log.get('merge_sha')),'missing-exact-confirmed-merge-log')
    require(type(proof) is dict and proof.get('number')==b['pr_number'] and proof.get('merge_sha')==log['merge_sha']
            and proof.get('parents')==[b['base'],b['head']] and proof.get('target_contains_merge') is True
            and native.get('merge_commit_sha')==log['merge_sha'],'missing-independent-exact-merge-readback')
    require(not any(x.get('branch')=='refs/heads/'+b['branch'] for x in worktrees(git,b['worktree'])),'branch-checked-out')
    require(not git(['status','--porcelain','--untracked-files=all'],cwd=b['worktree']).strip(),'dirty-bound-worktree')
    for key in ('publish','create_pr','rerun','update'):
        op=row['operations'].get(key)
        require(not op or op['status']=='done','unknown-prior-branch-effect')


def retire(row,store,git,github):
    b=row['binding'];cwd=b['worktree'];ref='refs/heads/'+b['branch'];archive='refs/archive/lab/card-'+str(b['card'])+'/'+b['head']
    retirement_ready(row,git,github)
    recovery=remote(git,cwd,archive);actual=remote(git,cwd,ref)
    if row['phase']=='retired':
        require(actual is None and recovery==b['head'],'retired-ref-reappeared-or-archive-moved')
        return life.result('retired',row,archive_ref=archive)
    delete=row['operations'].get('delete')
    if actual is None:
        require(delete is not None and recovery==b['head'],'unexplained-business-ref-absence')
        row['phase']='retired';delete['status']='done';store.save()
        return life.result('retired',row,archive_ref=archive)
    require(actual==b['head'],'business-ref-SHA-moved')
    if recovery is None:
        require('archive' not in row['operations'],'archive-outcome-unknown-readback-only')
        row['operations']['archive']=dict(status='pending',ref=archive,head=b['head']);store.save()
        try:git(['push','--porcelain','--force-with-lease='+archive+':','origin',b['head']+':'+archive],cwd=cwd)
        except Exception:return dict(action='hold',reason='archive-outcome-unknown-readback-only')
        recovery=remote(git,cwd,archive)
    require(recovery==b['head'],'recovery-ref-conflict-or-unconfirmed')
    row['operations']['archive']=dict(status='done',ref=archive,head=b['head']);store.save()
    retirement_ready(row,git,github)
    require(remote(git,cwd,ref)==b['head'],'business-ref-moved-before-delete')
    require(delete is None,'delete-outcome-unknown-readback-only')
    row['operations']['delete']=dict(status='pending',head=b['head'],archive_ref=archive);store.save()
    try:git(['push','--porcelain','--force-with-lease='+ref+':'+b['head'],'origin',':'+ref],cwd=cwd)
    except Exception:return dict(action='hold',reason='delete-outcome-unknown-readback-only')
    require(remote(git,cwd,ref) is None and remote(git,cwd,archive)==b['head'],'delete-not-read-back')
    row['phase']='retired';row['operations']['delete']['status']='done';store.save()
    return life.result('retired',row,archive_ref=archive)


def run_branch_command(action, request, state_path, git_argv_port, github_port):
    """Shared real harness entry; effects require explicit injected capability."""
    try:
        require(action in ('dispatch','publish','update','ensure-pr','read-ci','rerun-ci','retire','record-confirmed'), 'unsupported-branch-action')
        require(callable(git_argv_port) and github_port is not None,'branch-ports-unbound')
        with life.Store(state_path).locked() as store:
            if action=='dispatch':return dispatch(request,store,git_argv_port,github_port)
            row=store.find(request.get('binding'))
            if action=='publish':return publish(request,row,store,git_argv_port,github_port)
            if action=='ensure-pr':return ensure_pr(row,store,github_port)
            if action in ('read-ci','rerun-ci'):return ci(action,request,row,store,github_port)
            if action=='retire':return retire(row,store,git_argv_port,github_port)
            if action=='update':
                # A topology update needs the complete native mutation snapshot.
                b=row['binding'];occupancy(github_port,b);base=request.get('base')
                require(sha(base) and remote(git_argv_port,b['worktree'],'refs/heads/main')==base,'unverified-new-base')
                require(not git_argv_port(['status','--porcelain','--untracked-files=all'],cwd=b['worktree']).strip()
                        and git_argv_port(['symbolic-ref','--quiet','--short','HEAD'],cwd=b['worktree']).strip()==b['branch'],'update-worktree-occupied')
                require('update' not in row['operations'] or row['operations']['update']['status']=='done','update-unknown-readback-only')
                row['operations']['update']=dict(status='pending',base=base,head=b['head']);store.save()
                git_argv_port(['merge','--no-edit',base],cwd=b['worktree'])
                b['base']=base;row['operations']['update']['status']='done';store.save()
                return life.result('updated-local',row,publish_required=True,new_local_head=git_argv_port(['rev-parse','HEAD'],cwd=b['worktree']).strip())
            # A merge event is only a cleanup candidate. It grants no retirement.
            record=request.get('candidate');b=row['binding']
            require(type(record) is dict and record.get('repository_id')==life.RID
                    and record.get('number')==b['pr_number'] and record.get('head')==b['head']
                    and record.get('base')==b['base'] and sha(record.get('merge_sha'))
                    and record.get('queue_completed') is True,'inexact-confirmed-cleanup-candidate')
            row['operations']['merge_candidate']=dict(status='done',record=copy.deepcopy(record));store.save()
            return life.result('retirement-candidate',row,release_authority=False)
    except Exception as error:
        return dict(action='hold',reason=str(error) if type(error) is ValueError else 'branch-effect-unknown-or-readback-incomplete',release_authority=False)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action',choices=('dispatch','publish','update','ensure-pr','read-ci','rerun-ci','retire','record-confirmed'))
    parser.add_argument('--request',required=True);parser.add_argument('--execute',action='store_true')
    args=parser.parse_args()
    try:
        native=sibling('lab_branch_native');request=native.private_json(Path(args.request))
        git,github,state=native.bound_ports(args.action,request,execute=args.execute)
        answer=run_branch_command(args.action,request,state,git,github)
    except Exception as error:answer=dict(action='hold',reason=str(error) if type(error) is ValueError else 'native-branch-binding-unavailable')
    print(json.dumps(answer,ensure_ascii=False,sort_keys=True))
    return int(answer['action']=='hold')


if __name__=='__main__':raise SystemExit(main())
