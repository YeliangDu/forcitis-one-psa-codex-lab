"""Personal-Lab native adapters. No merge/ref REST API or credential discovery.

No operation happens at import. Local operator policy and complete proof index
are prerequisites, not candidate/label assertions. Missing bindings HOLD.
"""
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import sqlite3
import stat
import subprocess
import urllib.parse
import urllib.request

LAB=Path('/Users/yeliang/Projects/FORCITIS/forcitis-one-psa-codex-lab')
SLUG='YeliangDu/forcitis-one-psa-codex-lab'
RID=1449415666
ORIGIN='git@github.com:'+SLUG+'.git'
API='https://api.github.com/repos/'+SLUG
ROOT=LAB/'.codex-lab/branch-lifecycle'


def need(value, reason):
    if not value:raise ValueError(reason)


def unique(pairs):
    result={}
    for key,value in pairs:
        need(key not in result,'duplicate-native-input-key');result[key]=value
    return result


def nonfinite(_value):raise ValueError('nonfinite-native-input')


def private_bytes(path, limit=1024*1024, private=True):
    path=Path(path);need(path.is_absolute() and path.resolve(strict=True)==path,'linked-native-state')
    fd=os.open(path,os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK)
    with os.fdopen(fd,'rb') as stream:
        before=os.fstat(stream.fileno())
        need(stat.S_ISREG(before.st_mode) and before.st_uid==os.geteuid() and before.st_nlink==1
             and (stat.S_IMODE(before.st_mode)==0o600 if private else not before.st_mode&0o022)
             and before.st_size<=limit,'unsafe-native-input-mode-owner-size')
        raw=stream.read(limit+1);identity=lambda s:(s.st_dev,s.st_ino,s.st_size,s.st_mtime_ns,s.st_ctime_ns)
        need(len(raw)==before.st_size and identity(before)==identity(os.fstat(stream.fileno()))==identity(path.lstat()),'native-input-moved')
        return raw


def private_json(path):return json.loads(private_bytes(path).decode('utf-8'),object_pairs_hook=unique,parse_constant=nonfinite)


def repo(value, reduced=False):
    need(type(value) is dict and type(value.get('id')) is int and value['id']==RID,'foreign-native-repo-id')
    if not reduced:need(value.get('full_name')==SLUG and value.get('private') is False,'foreign-or-nonpublic-repo')
    if 'full_name' in value:need(value['full_name']==SLUG,'foreign-native-repo-name')
    if 'name' in value:need(value['name']==SLUG.split('/')[1],'foreign-native-repo-name')
    if 'url' in value:need(value['url']==API,'foreign-native-repo-url')
    if 'private' in value:need(value['private'] is False,'nonpublic-native-association')
    if 'visibility' in value:need(value['visibility']=='public','unknown-native-visibility')
    if 'fork' in value:need(value['fork'] is False,'fork-native-repository')


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs):raise ValueError('unexpected-native-API-redirect')


class NativeGit:
    def __init__(self,worktree,branch,actions,archive_policy,card,config_sha256=None):
        self.worktree,self.branch,self.actions,self.archive_policy=Path(worktree),branch,actions,archive_policy
        self.card,self.config_sha256=card,config_sha256

    def __call__(self,args,cwd=None):
        directory=Path(cwd or self.worktree)
        need(directory.resolve(strict=True)==directory and directory in (LAB,self.worktree),'unbound-Git-cwd')
        need(type(args) is list and args and all(type(x) is str and '\0' not in x for x in args)
             and '-C' not in args and not any(x.startswith('--git-dir') for x in args),'unapproved-Git-argv')
        allowed={'rev-parse','cat-file','status','worktree','branch','symbolic-ref','for-each-ref','ls-remote','merge-base','checkout','merge','push'}
        need(args[0] in allowed,'unapproved-Git-command')
        mutation=args[0] in {'checkout','merge','push'}
        if args[0]=='worktree':need(args==['worktree','list','--porcelain'],'worktree-mutation-forbidden')
        if args[0]=='branch':need(args==['branch','--show-current'],'local-branch-delete-or-mutation-forbidden')
        if args[0]=='symbolic-ref':
            need(args==['symbolic-ref','--quiet','--short','HEAD'],'symbolic-ref-write-forbidden')
            return self._run(args,directory,missing=True)
        if mutation:
            need(self.actions,'native-mutation-not-authorized')
            # No hooks bypass: a configured non-Lab hook path cannot be executed.
            config=self._run(['config','--get','core.hooksPath'],directory,missing=True).strip()
            need(not config or (Path(config).is_absolute() and Path(config).resolve()==Path(config)
                               and LAB in Path(config).parents),'unreviewed-Git-hook-path')
        if args[0]=='checkout':
            need('dispatch' in self.actions and (args==['checkout',self.branch] or
                 (len(args)==4 and args[:3]==['checkout','-b',self.branch] and re.fullmatch('[0-9a-f]{40}',args[3]))), 'unbound-branch-checkout')
        if args[0]=='merge':need('update' in self.actions and len(args)==3 and args[:2]==['merge','--no-edit']
                               and re.fullmatch('[0-9a-f]{40}',args[2]),'unbound-local-update')
        if args[0]=='push':
            self.origin(directory)
            need(len(args)==5 and args[:2]==['push','--porcelain'] and args[3]=='origin'
                 and args[2].startswith('--force-with-lease='),'unleased-branch-push')
            need(type(self.config_sha256) is str and re.fullmatch('[0-9a-f]{64}',self.config_sha256)
                 and hashlib.sha256(self._run(['config','--null','--list','--local'],directory).encode()).hexdigest()==self.config_sha256,
                 'current-local-Git-transport-configuration-unbound')
            ref,expected=args[2].split('=',1)[1].rsplit(':',1);spec=args[4]
            business='refs/heads/'+self.branch
            if ref==business and spec==':'+business:
                need('retire' in self.actions and re.fullmatch('[0-9a-f]{40}',expected),'unbound-exact-branch-deletion')
            elif ref==business:
                need('publish' in self.actions and re.fullmatch('[0-9a-f]{40}',spec.split(':',1)[0])
                     and spec.endswith(':'+business) and (not expected or re.fullmatch('[0-9a-f]{40}',expected)), 'unbound-business-publication')
            else:
                prefix='refs/archive/lab/card-'+str(self.card)+'/'
                need('retire' in self.actions and self.archive_policy and self.archive_policy.get('remote_namespace_verified') is True
                     and ref.startswith(prefix) and re.fullmatch('[0-9a-f]{40}',ref[len(prefix):])
                     and spec==ref[len(prefix):]+':'+ref and expected=='','unbound-recovery-namespace')
        return self._run(args,directory)

    def _run(self,args,cwd,missing=False):
        env={k:v for k,v in os.environ.items() if not k.startswith('GIT_')}
        env.update(GIT_CONFIG_NOSYSTEM='1',GIT_CONFIG_GLOBAL='/dev/null',GIT_TERMINAL_PROMPT='0',GIT_NO_REPLACE_OBJECTS='1',GIT_OPTIONAL_LOCKS='0')
        result=subprocess.run(['/usr/bin/git','--no-replace-objects',*args],cwd=cwd,env=env,stdin=subprocess.DEVNULL,
                              stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,timeout=45)
        if missing and result.returncode==1:return ''
        need(result.returncode==0,'native-Git-failed-or-effect-unknown')
        return result.stdout.rstrip('\n') if args[:3]==['config','--null','--list'] else result.stdout.strip()

    def origin(self,cwd):
        need(self._run(['remote'],cwd)=='origin' and self._run(['remote','get-url','--all','origin'],cwd)==ORIGIN
             and self._run(['remote','get-url','--push','--all','origin'],cwd)==ORIGIN,'foreign-or-ambiguous-origin')


class NativeGithub:
    def __init__(self,policy,contract,execute):
        self.policy,self.contract,self.execute=policy,contract,execute
        self.workflow_path=contract.get('ci_workflow','.github/workflows/lab-pr-ci.yml')
        need(re.fullmatch(r'\.github/workflows/[A-Za-z0-9_-][A-Za-z0-9._-]*\.ya?ml',self.workflow_path),'unbound-CI-workflow-path')
        self.opener=urllib.request.build_opener(urllib.request.ProxyHandler({}),NoRedirect())
        self.binding=None

    def api(self,path,method='GET',body=None):
        need(method=='GET' or (method=='POST' and (path=='pulls' or re.fullmatch(r'actions/runs/[1-9][0-9]*/rerun',path))), 'merge-or-unbound-API-write-forbidden')
        need(path=='' or re.fullmatch(r'(?:pulls(?:/[1-9][0-9]*)?(?:\?state=all&per_page=100&page=[1-9][0-9]*)?|git/ref/heads/main|commits/[0-9a-f]{40}|compare/[0-9a-f]{40}\.\.\.[0-9a-f]{40}|actions/runs(?:/[1-9][0-9]*)?(?:/rerun|\?branch=[A-Za-z0-9%._~-]+&per_page=100&page=[1-9][0-9]*)?)',path),'unapproved-native-endpoint')
        headers={'Accept':'application/vnd.github+json','User-Agent':'personal-lab-branch-lifecycle','X-GitHub-Api-Version':'2022-11-28'}
        credential=os.environ.get('GH_TOKEN')
        if credential:headers['Authorization']='Bearer '+credential
        if method!='GET':need(self.execute and credential,'missing-explicit-native-write-capability')
        raw=None if body is None else json.dumps(body,ensure_ascii=False,allow_nan=False).encode('utf-8')
        if raw is not None:headers['Content-Type']='application/json'
        request=urllib.request.Request(API+('/'+path if path else ''),data=raw,method=method,headers=headers)
        with self.opener.open(request,timeout=30) as response:
            data=response.read(8*1024*1024+1);need(len(data)<=8*1024*1024,'native-response-too-large')
            status,link=response.status,response.headers.get('Link','')
        need(status in ((200,) if method=='GET' else (201,202)),'native-request-unconfirmed')
        value=json.loads(data.decode('utf-8'),object_pairs_hook=unique,parse_constant=nonfinite) if data else None
        return value,link

    def pages(self,path,field=None):
        rows=[];seen=set();total=None
        for page in range(1,17):
            value,link=self.api(path+'&page='+str(page));items=value[field] if field else value
            need(type(items) is list,'native-page-shape')
            if field:
                count=value['total_count'];need(type(count) is int and count>=0 and (total is None or count==total),'changing-native-total');total=count
            for row in items:
                need(type(row) is dict and type(row.get('id')) is int and row['id']>0 and row['id'] not in seen,'duplicate-native-page-item')
                seen.add(row['id']);rows.append(row)
            if 'rel="next"' not in link:
                need(total is None or len(rows)==total,'incomplete-native-pages');return rows
            urls=re.findall(r'<([^>]+)>; rel="next"',link);need(len(urls)==1,'ambiguous-native-next')
            u=urllib.parse.urlparse(urls[0]);wanted=urllib.parse.parse_qs(path.split('?',1)[1]);wanted['page']=[str(page+1)]
            need(u.scheme=='https' and u.netloc=='api.github.com' and u.path in
                 ('/repos/'+SLUG+'/'+path.split('?',1)[0],'/repositories/'+str(RID)+'/'+path.split('?',1)[0])
                 and urllib.parse.parse_qs(u.query)==wanted,'foreign-native-next')
        raise ValueError('native-pagination-bound')

    def all_pulls(self):return self.pages('pulls?state=all&per_page=100')

    def find_pulls(self,binding):
        rows=[]
        for row in self.all_pulls():
            link=re.match(r'^(?:Closes|Refs) #([1-9][0-9]*)\s*(?:\n|$)',row.get('body') or '')
            if row.get('head',{}).get('ref')!=binding['branch'] and not (link and int(link[1])==binding['card']):continue
            repo(row['head']['repo']);repo(row['base']['repo']);rows.append(row)
        return dict(complete=True,items=rows)

    def create_pull(self,binding):
        need('ensure-pr' in self.contract['allowed_actions'],'PR-create-not-authorized')
        return self.api('pulls','POST',dict(title=self.contract['pr_title'],body='Closes #'+str(binding['card'])+'\n\nCodex: '+self.contract['session_id'],
                                          head=binding['branch'],base='main',draft=True))[0]

    def pull(self,number):
        row=self.api('pulls/'+str(number))[0];repo(row['head']['repo']);repo(row['base']['repo']);return row

    def native_runs(self,binding):
        return self.pages('actions/runs?branch='+urllib.parse.quote(binding['branch'],safe='')+'&per_page=100','workflow_runs')

    def runs(self,binding):
        self.binding=copy.deepcopy(binding);normalized=[]
        for row in self.native_runs(binding):
            if row.get('event')!='pull_request' or row.get('path')!=self.workflow_path:continue
            repo(row['repository'])
            associations=[a for a in row['pull_requests'] if a['number']==binding['pr_number']]
            if not associations:continue
            need(len(associations)==1,'ambiguous-native-PR-association');association=associations[0]
            for side in ('head','base'):repo(association[side]['repo'],reduced=True)
            need(association['head']['sha']==row['head_sha'] and association['head']['ref']==row['head_branch']
                 and association['base']['ref']=='main','native-run-association-mismatch')
            # base_sha/pull_request below are explicitly collector-normalized,
            # obtained from actual native association, never the caller's guess.
            normalized.append(dict(id=row['id'],run_attempt=row['run_attempt'],event=row['event'],path=row['path'],
                repository=row['repository'],head_branch=row['head_branch'],head_sha=row['head_sha'],
                base_sha=association['base']['sha'],pull_request=association['number'],status=row['status'],conclusion=row['conclusion']))
        return dict(complete=True,items=normalized)

    def rerun(self,run_id,attempt):
        need('rerun-ci' in self.contract['allowed_actions'] and self.binding is not None,'rerun-not-authorized')
        row=self.api('actions/runs/'+str(run_id))[0];repo(row['repository'])
        need(type(row['run_attempt']) is int and row['run_attempt']==attempt and row['head_sha']==self.binding['head']
             and row['head_branch']==self.binding['branch'] and row['event']=='pull_request'
             and row['path']==self.workflow_path,'rerun-native-identity-moved')
        return self.api('actions/runs/'+str(run_id)+'/rerun','POST',{})[0]

    def local_snapshot(self,binding):
        # Strict existing V2 only; shared readonly reader also serves post-merge.
        queued,heads,record=lifecycle().canonical_snapshot(LAB/'.git',binding['pr_number'])
        fields=('number','status','head','base','target','release_key','merge_sha')
        return queued,heads,{key:record[key] for key in fields} if record else None

    def retirement_snapshot(self,binding):
        queued,heads,record=self.local_snapshot(binding)
        proof_raw=private_bytes(ROOT/'proof-pins.json')
        need(hashlib.sha256(proof_raw).hexdigest()==self.policy.get('proof_index_sha256'),'proof-reference-index-pin-unbound')
        proof=json.loads(proof_raw.decode('utf-8'),object_pairs_hook=unique,parse_constant=nonfinite)
        need(type(proof) is dict and proof.get('version')==1 and proof.get('complete') is True
             and proof.get('repository_id')==RID and type(proof.get('items')) is list,'proof-reference-inventory-unbound')
        pins=[]
        for pin in proof['items']:
            need(type(pin) is dict and set(pin)=={'branch','head','state','reference'},'unknown-proof-reference')
            if pin['branch']==binding['branch'] or pin['head']==binding['head']:
                need(pin['state'] in ('active','released'),'expired-or-unknown-proof-reference')
                if pin['state']=='active':pins.append(pin['reference'])
        pulls=self.all_pulls();other=[]
        for row in pulls:
            if row['state']=='open' and row['head']['ref']==binding['branch'] and row['number']!=binding['pr_number']:other.append(row['number'])
        active=[]
        for run in self.native_runs(binding):
            repo(run['repository'])
            if run['head_branch']==binding['branch']:
                need(run['status'] in ('completed','queued','in_progress','waiting','pending','requested'),'unknown-native-CI-status')
                if run['status']!='completed':active.append(dict(id=run['id'],attempt=run['run_attempt']))
        merged=None;log=None;current=None
        if binding['pr_number'] is not None:
            current=self.pull(binding['pr_number'])
            if current.get('merged') is True:
                merge=current.get('merge_commit_sha');need(re.fullmatch('[0-9a-f]{40}',merge or ''),'missing-merged-SHA')
                commit=self.api('commits/'+merge)[0];tip=self.api('git/ref/heads/main')[0]['object']['sha']
                contains=tip==merge
                if not contains:
                    cmp=self.api('compare/'+merge+'...'+tip)[0];contains=cmp.get('status')=='ahead' and cmp.get('merge_base_commit',{}).get('sha')==merge
                merged=dict(number=current['number'],merge_sha=merge,parents=[x['sha'] for x in commit['parents']],target_contains_merge=contains)
                if record and record['status']=='confirmed' and record['head']==binding['head'] and record['base']==binding['base'] and record['merge_sha']==merge:
                    log=dict(repository_id=RID,number=record['number'],head=record['head'],base=record['base'],merge_sha=merge,status='confirmed')
        # Recheck canonical state after native reads; no Queue constructor/locks.
        again=self.local_snapshot(binding);need((queued,heads,record)==again
             and private_bytes(ROOT/'proof-pins.json')==proof_raw,'queue-or-proof-index-changed')
        return dict(complete=True,queued=binding['pr_number'] in queued or binding['pr_number'] in heads,
                    queue_completed=log is not None and binding['pr_number'] not in queued and binding['pr_number'] not in heads,
                    merge_log=log,merged_readback=merged,active_ci=active,proof_pins=pins,other_open_pulls=other,
                    current_base=current['base']['sha'] if current else binding['base'],
                    current_pr_merged=current.get('merged') is True if current else False)



def lifecycle():
    path=Path(__file__).absolute().with_name('lab_branch_lifecycle.py')
    need(path.resolve(strict=True)==path,'linked-lifecycle-reader-source')
    spec=importlib.util.spec_from_file_location('native_branch_lifecycle',path)
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    return module


def confirmed_request(number,common_dir,result):
    """Production hook selects existing assigned binding, never caller guesses."""
    need(type(number) is int and number>0 and Path(common_dir)==LAB/'.git'
         and Path(common_dir).resolve(strict=True)==Path(common_dir)
         and type(result) is dict and result.get('action') in ('confirmed','skip')
         and re.fullmatch('[0-9a-f]{40}',result.get('merge_sha','')),'unverified-production-merge-subject')
    with lifecycle().Store(ROOT/'state.json').locked(readonly=True) as store:
        rows=[row for row in store.data['bindings'].values()
              if row['phase']=='bound' and row['binding']['pr_number']==number]
        need(len(rows)==1,'assigned-lifecycle-binding-missing-or-ambiguous')
        binding=copy.deepcopy(rows[0]['binding'])
        need(binding['common']==str(LAB/'.git'),'foreign-branch-binding-common')
    return dict(binding=binding,number=number,common_dir=str(common_dir),merge_result=copy.deepcopy(result))


def bound_ports(action,request,execute=False):
    need(action!='retire','native-retirement-not-enabled')
    readonly=action in ('post-merge','record-confirmed')
    if readonly:execute=False
    need(LAB.resolve(strict=True)==LAB,'foreign-Lab-root')
    request=copy.deepcopy(request)
    policy=private_json(ROOT/'policy.json')
    need(type(policy) is dict and policy.get('version')==1 and policy.get('repository')==SLUG
         and type(policy.get('repository_id')) is int and policy['repository_id']==RID,'operator-branch-policy-unbound')
    supplied=request if action=='dispatch' else request['binding'];card=supplied['card']
    contract=policy['cards'][str(card)];need(contract['role']==supplied['role'] and contract['worktree']==supplied['worktree'],'unassigned-native-role/worktree')
    registry=private_json(LAB/'.codex-lab/session-copy/runtime/roles.json')
    matches=[r for r in registry['roles'] if r['role']==contract['role']]
    need(len(matches)==1 and matches[0]['id']==contract['session_id'],'registered-resident-identity-moved')
    need(type(contract['allowed_actions']) is list and all(type(a) is str for a in contract['allowed_actions']),'invalid-action-contract')
    if action not in ('read-ci','post-merge','record-confirmed'):need(execute and action in contract['allowed_actions'],'explicit-branch-action-not-authorized')
    if action in ('ensure-pr','rerun-ci'):need(bool(os.environ.get('GH_TOKEN')),'missing-explicit-GitHub-credential')
    archive=policy.get('archive_policy')
    if action=='retire':
        need(type(archive) is dict and archive.get('namespace')=='refs/archive/lab/card-{card}/{head}'
             and archive.get('remote_namespace_verified') is True and archive.get('retention')=='retain-until-explicitly-reviewed-GC'
             and re.fullmatch('[0-9a-f]{64}',archive.get('reviewed_contract_sha256','')),'real-archive-namespace-and-retention-contract-unbound')
    branch=contract['branch']
    if action=='dispatch':
        # Main caller supplies this exact branch in its dispatch request.
        need(request.get('branch',branch)==branch,'different-authorized-branch')
        supplied['branch']=branch
        if 'adopt_remote_head' in contract:
            need(re.fullmatch('[0-9a-f]{40}',contract['adopt_remote_head']),'invalid-adopted-remote-preimage')
            supplied['adopt_head']=contract['adopt_remote_head']
    else:need(supplied['branch']==branch,'legacy-branch-adoption-requires-explicit-contract')
    git=NativeGit(contract['worktree'],branch,set(contract['allowed_actions'])-{'retire'} if execute else set(),archive,card,
                  contract.get('git_config_sha256'))
    inventory=git(['worktree','list','--porcelain'],cwd=LAB)
    need(contract['worktree'] in [line[9:] for line in inventory.splitlines() if line.startswith('worktree ')],
         'assigned-worktree-not-in-personal-Lab-inventory')
    need(Path(contract['worktree']).resolve(strict=True)==Path(contract['worktree']),'linked-assigned-worktree')
    need(git(['rev-parse','--path-format=absolute','--git-common-dir'],cwd=contract['worktree'])==str(LAB/'.git'),'foreign-native-Git-common')
    git.origin(Path(contract['worktree']))
    if action in ('publish','retire'):
        expected=contract.get('git_config_sha256')
        need(type(expected) is str and re.fullmatch('[0-9a-f]{64}',expected)
             and hashlib.sha256(git._run(['config','--null','--list','--local'],Path(contract['worktree'])).encode()).hexdigest()==expected,
             'current-local-Git-transport-configuration-unbound')
    github=NativeGithub(policy,contract,execute);repo(github.api('')[0])
    if action=='dispatch':need(github.api('git/ref/heads/main')[0]['object']['sha']==supplied['base'],'native-main-base-moved')
    state=ROOT/'state.json';need(ROOT.resolve(strict=True)==ROOT and ROOT.stat().st_uid==os.geteuid()
                              and stat.S_IMODE(ROOT.stat().st_mode)==0o700,'branch-state-directory-not-private')
    return git,github,state,request
