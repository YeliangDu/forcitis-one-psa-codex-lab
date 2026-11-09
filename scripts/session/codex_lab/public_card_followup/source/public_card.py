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
        A=self.s.get('expected',self.s['head'])
        need(self.git('rev-list','--parents','-n','1',A)==A+' '+self.s['base']
             and self.git('diff','--name-only',self.s['base'],A).splitlines()==[scope['test_path']]
             and self.git('rev-parse',self.s['head']+':'+scope['test_path'])==scope['test_blob'],
             'publication-frozen-test-drift')
        if 'expected' in self.s:
            need(self.git('rev-list','--parents','-n','1',self.s['head'])==self.s['head']+' '+A
                 and self.git('rev-parse','HEAD')==self.s['head'] and not self.git('status','--porcelain','--untracked-files=all'),
                 'followup-exact-applied-worktree-required')
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

    def update_ref(self,branch,expected,head):
        need((branch,expected,head)==(self.s['branch'],self.s['expected'],self.s['head']),'card-update-ref-scope')
        ref='refs/heads/'+branch
        self.git('push','--porcelain','--no-follow-tags','--recurse-submodules=no','--force-with-lease='+ref+':'+expected,'origin',head+':'+ref)
    def ready_pr(self,number,head,title,body):
        need((number,head,title,body)==(self.s['number'],self.s['head'],self.s['title'],self.s['body']),'card-ready-send-scope')
        raw=self.api('/pulls/'+str(number));row=self.normalize(raw)
        need(self.ref(self.s['branch'])==head and row['head']['sha']==head
             and row['draft'] is True and row['body']==self.s['previous_body'],'card-ready-preimage-moved')
        query='mutation($id:ID!,$t:String!,$b:String!){u:updatePullRequest(input:{pullRequestId:$id,title:$t,body:$b}){pullRequest{id}} r:markPullRequestReadyForReview(input:{pullRequestId:$id}){pullRequest{id}}}'
        args=['/opt/homebrew/bin/gh','api','--hostname','github.com','--method','POST','graphql','--input','-']
        out=decode(run(args,self.cwd,wire(dict(query=query,variables=dict(id=raw['node_id'],t=title,b=body))).encode()))
        need(not out.get('errors') and out['data']['r']['pullRequest']['id']==raw['node_id'],'card-ready-response-unknown')


def publication(installation,installation_pin,request,role_installation,role_pin,scope_path,scope_pin):
    from single_role import RoleInstallation,TaskStore
    i=RoleInstallation(installation,installation_pin,role_installation,role_pin)
    need(i.is_native and i.root==Path('/private/tmp/lab60-owned-single-role-v1'),'card-owned-installation-required')
    scope_file=physical(scope_path)
    need(scope_file.parent==i.root/'owner','card-publication-scope-location')
    raw=private_file(str(scope_file));need(sha(raw)==scope_pin,'card-publication-scope-pin')
    scope=decode(raw)
    fields(scope,('subject','worktree','common','session_id','test_path','test_blob','git_config_sha256'))
    s=scope['subject'];validate({k:s[k] for k in __import__('first_publication').FIELDS})
    need(s['repo_id']==RID and s['repository']==REPO and s['branch'].startswith('codex/'+str(s['card'])+'-')
         and s['body'].splitlines()[0]==f"Refs #{s['card']}",'card-native-publication-scope')
    lock=i.runtime/'owned.lock';info=private(lock,stat.S_ISREG)
    with os.fdopen(os.open(lock,os.O_RDONLY|os.O_NOFOLLOW),'rb') as h:
        fcntl.flock(h,fcntl.LOCK_EX|fcntl.LOCK_NB)
        cur=os.fstat(h.fileno())
        need((cur.st_dev,cur.st_ino)==(info.st_dev,info.st_ino) and not i.sock.exists() and not i.sock.is_symlink(),'card-owned-runtime-active')
        rp=physical(request);need(rp.parent==i.root,'card-private-request-location')
        req=decode(private_file(str(rp)));fields(req,('command','auth','arguments'))
        need(req['command'] in ('publication.ref','publication.pr','followup.ref','followup.pr','card.apply'),'card-publication-command-scope')
        store=TaskStore(i)
        if req['command']=='card.apply':
            try:
                fields(req['arguments'],('patch_sha256',))
                need('expected' not in s,'card-patch-requires-original-publication-scope')
                result=apply_native_patch(store,req['auth'],scope,req['arguments']['patch_sha256'],i)
            finally:store.db.close()
            print(wire(result));return 0
        need(req['arguments']=={'revision':s['revision']},'card-publication-revision-scope')
        try:
            with store.tx() as db:
                owner,task=store.binding(db,req['auth'],s['revision'],paused_ok=True)
                reg=db.execute('select session_id,phase,generation from role_registration').fetchone()
                need(owner['paused']==1 and reg and reg['phase']=='stable'
                     and reg['generation']==owner['generation'] and reg['session_id']==scope['session_id']
                     and decode(task['body'])['session_id']==scope['session_id'],'card-stable-paused-owner-required')
            stage,kind=req['command'].split('.')
            need((stage=='followup')==('expected' in s),'card-publication-stage-scope')
            if stage=='followup':
                from card_followup import advance as follow
                with store.tx() as db:
                    effects=db.execute("select target,observation from effects where kind='first-pr' and phase='observed'").fetchall()
                    match=[e for e in effects if decode(e['target']).get('card')==s['card']
                           and decode(e['target']).get('head')==s['expected'] and decode(e['observation']).get('number')==s['number']]
                    need(len(match)==1,'same-card-first-pr-receipt-required')
                result=follow(store,req['auth'],s,GitHubPort(scope),kind)
            else:result=advance(store,req['auth'],s,GitHubPort(scope),kind)
        finally:store.db.close()
        print(wire(result))
        return 0 if result['action']=='confirmed' else 78


def apply_native_patch(store,auth,scope,patch_sha256,i):
    import secrets,tempfile
    import card_capsule
    need(type(patch_sha256) is str and re.fullmatch('[a-f0-9]{64}',patch_sha256),'card-patch-pin-required')
    port=GitHubPort(scope);s=scope['subject'];key=f"card-apply:{s['repo_id']}:{s['card']}:{s['head']}"
    with store.tx() as db:
        owner=store.owner(db,auth);task=store.current(db);body=decode(task['body'])
        need(owner['paused']==1 and task['phase']=='active' and body['card']==s['card'] and body['A']==body['H']==s['head']
             and body['B']==s['base'] and body['session_id']==scope['session_id'],'card-patch-current-identity')
        matches=[decode(x['body']) for x in db.execute("select body from events where kind='native-card-patch-proposed'")]
        matches=[x for x in matches if sha(wire(x).encode())==patch_sha256]
        need(len(matches)==1,'card-native-patch-not-proven')
        patch=matches[0]
        need(patch['revision']==task['revision'] and patch['generation']==owner['generation']
             and patch['thread_id']==body['session_id'] and body['task']=='card-capsule:'+patch['scope_sha256'],
             'card-patch-historical-or-unbound')
        row=db.execute('select * from native_dispatch where effect_id=?',(patch['dispatch_effect_id'],)).fetchone()
        turn=db.execute('select state from turns where turn_id=?',(patch['turn_id'],)).fetchone()
        need(row and row['turn_id']==patch['turn_id'] and row['revision']==task['revision'] and turn and turn['state']=='terminal',
             'card-patch-native-turn-unproved')
        prior=db.execute('select * from effects where operation_key=?',(key,)).fetchone()
        if prior:
            record=decode(prior['target'])
            need(record['patch_sha256']==patch_sha256 and prior['issue_count']==1,'card-patch-replay-conflict')
    if prior:
        need(port.git('rev-parse','HEAD')==record['H'] and not port.git('status','--porcelain','--untracked-files=all'),
             'card-patch-apply-unknown-readback-only')
        with store.tx() as db:
            if prior['phase']=='unknown':
                db.execute("update effects set phase='observed',observation=? where effect_id=?",(wire(record),prior['effect_id']))
                store.event(db,'card-patch-apply-readback',dict(effect_id=prior['effect_id'],**record))
        return dict(action='applied',effect_id=prior['effect_id'],**record)
    raw=private_file(str(i.root/'owner/card-capsule.json'),196608)
    result=card_capsule.prepare_edits(body,raw,patch['scope_sha256'],patch['proposal'])
    files=card_capsule.parse(body,raw,patch['scope_sha256'])
    need(port.git('rev-parse','HEAD')==s['base'] and port.git('status','--porcelain','--untracked-files=all').splitlines()
         ==['?? '+scope['test_path']],'card-patch-worktree-not-exact-B')
    test=physical(str(port.cwd/scope['test_path']))
    need(port.git('hash-object',str(test))==scope['test_blob'],'card-frozen-worktree-test-drift')
    with tempfile.TemporaryDirectory(prefix='card-patch-index-') as td:
        env=dict(port.git_env,GIT_INDEX_FILE=str(Path(td)/'index'))
        def obj(*a,data=None):return run(['/usr/bin/git','--no-replace-objects',*a],port.cwd,data,env).decode().strip()
        obj('read-tree',s['head'])
        for p,text in result.items():
            f=physical(str(port.cwd/p));old=files[p]['text'].encode()
            need(f.read_bytes()==old and run(['/usr/bin/git','show',s['head']+':'+p],port.cwd,environment=port.git_env)==old
                 and port.git('ls-tree',s['head'],'--',p).startswith('100644 blob '),'card-patch-exact-preimage-missing')
            blob=obj('hash-object','-w','--stdin',data=text.encode())
            obj('update-index','--add','--cacheinfo','100644,'+blob+','+p)
        tree=obj('write-tree')
        msg=(s['title']+'\n\nRefs #'+str(s['card'])+'\n\nCodex: '+patch['thread_id']+'\nCodex-Turn: '+patch['turn_id']+'\n')
        head=obj('commit-tree',tree,'-p',s['head'],data=msg.encode())
    need(port.git('diff','--name-only',s['head'],head).splitlines()==sorted(result),'card-generated-diff-scope-mismatch')
    record=dict(A=s['head'],B=s['base'],H=head,patch_sha256=patch_sha256,thread_id=patch['thread_id'],turn_id=patch['turn_id'],paths=sorted(result))
    eid=secrets.token_hex(24)
    with store.tx() as db:
        own,t=store.binding(db,auth,patch['revision'],paused_ok=True)
        need(own['paused']==1 and t['body']==task['body'],'card-patch-lease-moved')
        db.execute('insert into effects(effect_id,revision,generation,operation_key,kind,target,phase,issue_count) values(?,?,?,?,?,?,?,1)',
                   (eid,patch['revision'],own['generation'],key,'card-patch-apply',wire(record),'unknown'))
        store.event(db,'card-patch-apply-consumed',dict(effect_id=eid,**record))
    for p,text in result.items():
        f=physical(str(port.cwd/p));need(f.read_bytes()==files[p]['text'].encode(),'card-patch-preimage-moved')
        fd=os.open(str(f.with_name('.lab-card-next-'+f.name)),os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o644)
        with os.fdopen(fd,'wb') as handle:handle.write(text.encode());handle.flush();os.fsync(handle.fileno())
        os.replace(f.with_name('.lab-card-next-'+f.name),f)
    port.git('reset','--mixed',head)
    need(port.git('rev-parse','HEAD')==head and not port.git('status','--porcelain','--untracked-files=all'),'card-patch-postcondition-unproved')
    with store.tx() as db:
        db.execute("update effects set phase='observed',observation=? where effect_id=?",(wire(record),eid))
        store.event(db,'card-patch-apply-readback',dict(effect_id=eid,**record))
    return dict(action='applied',effect_id=eid,**record)
