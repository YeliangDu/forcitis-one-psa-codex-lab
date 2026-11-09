"""Operator-only first publication on the owned installation, never a model tool."""
import fcntl,json,os,re,stat,subprocess
from pathlib import Path
from urllib.parse import urlencode
from first_publication import advance,validate,OID
from owned_runtime import decode,fields,private,sha
from runtime_broker import physical,private_file
from runtime_store import need,wire

REPO='YeliangDu/forcitis-one-psa-codex-lab'
RID=1449415666
ORIGIN='git@github.com:'+REPO+'.git'

def run(args,cwd,input=None,environment=None):
    p=subprocess.run(args,cwd=cwd,input=input,env=environment,capture_output=True,timeout=45)
    need(p.returncode==0 and len(p.stdout)<=12*1024*1024,'card-native-port-unavailable')
    return p.stdout

class GitHubPort:
    def __init__(self,scope):
        self.s=scope['subject'];self.cwd=physical(scope['worktree']);common=physical(scope['common'])
        self.git_env={k:v for k,v in os.environ.items() if not k.startswith('GIT_')}
        self.git_env.update(GIT_CONFIG_NOSYSTEM='1',GIT_CONFIG_GLOBAL='/dev/null',GIT_TERMINAL_PROMPT='0',GIT_NO_REPLACE_OBJECTS='1')
        need(self.git('rev-parse','--show-toplevel')==str(self.cwd)
             and self.git('rev-parse','--path-format=absolute','--git-common-dir')==str(common) and common.name=='.git' and common.parent.name=='forcitis-one-psa-codex-lab'
             and self.git('remote','get-url','--all','origin')==ORIGIN
             and self.git('remote','get-url','--push','--all','origin')==ORIGIN,'card-worktree-identity-drift')
        need(sha(run(['/usr/bin/git','config','--local','--null','--list'],self.cwd,environment=self.git_env))
             ==scope['git_config_sha256'],'card-git-transport-drift')
        need(self.git('rev-list','--parents','-n','1',self.s['head'])==self.s['head']+' '+self.s['base'],
             'first-publication-tests-only-parent-required')
        need(self.git('diff','--name-only',self.s['base'],self.s['head']).splitlines()==[scope['test_path']]
             and self.git('rev-parse',self.s['head']+':'+scope['test_path'])==scope['test_blob'],
             'first-publication-frozen-test-drift')
    def git(self,*args):
        return run(['/usr/bin/git','--no-replace-objects',*args],self.cwd,environment=self.git_env).decode().strip()
    def api(self,endpoint,method='GET',body=None,pages=False):
        args=['/opt/homebrew/bin/gh','api','--hostname','github.com','--method',method]
        if pages:args+=['--paginate','--slurp']
        args+=['repos/'+REPO+endpoint]
        if body is not None:args+=['--input','-']
        return decode(run(args,self.cwd,None if body is None else wire(body).encode()))
    def repo(self):
        r=self.api('')
        need(r['id']==RID and r['full_name']==REPO and r['private'] is False and r['fork'] is False
             and self.api('/git/ref/heads/main')['object']['sha']==self.s['base'],'card-remote-base-drift')
        return r
    def ref(self,branch):
        need(branch==self.s['branch'],'card-remote-ref-scope')
        ref='refs/heads/'+branch
        rows=self.api('/git/matching-refs/heads/'+branch)
        matches=[x for x in rows if x.get('ref')==ref]
        remote=self.git('ls-remote','--refs','origin',ref)
        if not matches:
            need(not remote,'card-ref-observers-disagree')
            return None
        need(len(matches)==1 and matches[0]['object']['type']=='commit','card-ref-observation-ambiguous')
        head=matches[0]['object']['sha']
        need(OID.fullmatch(head) and remote==head+'\t'+ref,'card-ref-observers-disagree')
        return head
    def create_ref(self,branch,head):
        need((branch,head)==(self.s['branch'],self.s['head']),'card-ref-send-scope')
        ref='refs/heads/'+branch
        self.git('push','--porcelain','--no-follow-tags','--recurse-submodules=no','--force-with-lease='+ref+':','origin',head+':'+ref)
    def normalize(self,p):
        need(all(p[side]['repo']['id']==RID and p[side]['repo']['full_name']==REPO for side in ('head','base')),
             'card-pr-origin-drift')
        return {**{k:p[k] for k in ('number','title','body','draft','state')},
                **{side:{k:p[side][k] for k in ('ref','sha')} for side in ('head','base')}}
    def pulls(self,branch):
        need(branch==self.s['branch'],'card-pr-list-scope')
        pages=self.api('/pulls?'+urlencode(dict(state='all',head='YeliangDu:'+branch,per_page=100)),pages=True)
        need(type(pages) is list and bool(pages) and all(type(p) is list for p in pages),'card-pr-pagination-unproved')
        rows=[self.normalize(r) for p in pages for r in p]
        need(len({r['number'] for r in rows})==len(rows),'card-pr-pagination-duplicate')
        return dict(complete=True,items=rows)
    def pull(self,n):
        need(type(n) is int and n>0,'card-pr-number-unbound')
        return self.normalize(self.api('/pulls/'+str(n)))
    def create_pr(self,s):
        need(s==self.s,'card-pr-send-scope')
        return self.api('/pulls','POST',dict(title=s['title'],body=s['body'],head=s['branch'],base='main',draft=True))

def publication(installation,installation_pin,request,role_installation,role_pin,scope_path,scope_pin):
    from single_role import RoleInstallation,TaskStore
    i=RoleInstallation(installation,installation_pin,role_installation,role_pin)
    need(i.is_native and i.root==Path('/private/tmp/lab60-owned-single-role-v1'),'card-owned-installation-required')
    scope_file=physical(scope_path)
    need(scope_file.parent==i.root/'owner','card-publication-scope-location')
    raw=private_file(str(scope_file));need(sha(raw)==scope_pin,'card-publication-scope-pin')
    scope=decode(raw)
    fields(scope,('subject','worktree','common','session_id','test_path','test_blob','git_config_sha256'))
    s=scope['subject'];validate(s)
    need(s['repo_id']==RID and s['repository']==REPO and s['branch'].startswith('codex/'+str(s['card'])+'-')
         and s['body'].splitlines()[0]==f"Refs #{s['card']}",'card-native-publication-scope')
    lock=i.runtime/'owned.lock';info=private(lock,stat.S_ISREG)
    with os.fdopen(os.open(lock,os.O_RDONLY|os.O_NOFOLLOW),'rb') as h:
        fcntl.flock(h,fcntl.LOCK_EX|fcntl.LOCK_NB)
        cur=os.fstat(h.fileno())
        need((cur.st_dev,cur.st_ino)==(info.st_dev,info.st_ino) and not i.sock.exists() and not i.sock.is_symlink(),'card-owned-runtime-active')
        rp=physical(request);need(rp.parent==i.root,'card-private-request-location')
        req=decode(private_file(str(rp)));fields(req,('command','auth','arguments'))
        need(req['command'] in ('publication.ref','publication.pr') and req['arguments']=={'revision':s['revision']},'card-publication-command-scope')
        store=TaskStore(i)
        try:
            with store.tx() as db:
                owner,task=store.binding(db,req['auth'],s['revision'],paused_ok=True)
                reg=db.execute('select session_id,phase,generation from role_registration').fetchone()
                need(owner['paused']==1 and reg and reg['phase']=='stable'
                     and reg['generation']==owner['generation'] and reg['session_id']==scope['session_id']
                     and decode(task['body'])['session_id']==scope['session_id'],'card-stable-paused-owner-required')
            result=advance(store,req['auth'],s,GitHubPort(scope),req['command'].split('.')[-1])
        finally:store.db.close()
        print(wire(result))
        return 0 if result['action']=='confirmed' else 78
