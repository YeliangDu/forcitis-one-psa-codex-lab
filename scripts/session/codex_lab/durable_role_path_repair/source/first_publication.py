"""Exact first-publication effects in the owned Store."""
import json,re,secrets,subprocess
from runtime_store import need,wire

FIELDS={'repo_id','repository','card','revision','branch','base','head','title','body'}
OID=re.compile('[a-f0-9]{40}')

def validate(s):
    need(type(s) is dict and set(s)==FIELDS,'publication-subject-shape')
    need(all(type(s[k]) is int and s[k]>0 for k in ('repo_id','card','revision'))
         and all(type(s[k]) is str for k in FIELDS-{'repo_id','card','revision'}),'publication-subject-types')
    need(re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+',s['repository'])
         and re.fullmatch(r'codex/[a-z0-9][a-z0-9-]{0,90}',s['branch'])
         and OID.fullmatch(s['base']) and OID.fullmatch(s['head']) and s['base']!=s['head']
         and 0<len(s['title'])<=200 and '\n' not in s['title'] and 0<len(s['body'])<=12000
         and '\0' not in s['title']+s['body'],'publication-subject-values')

def bound(store,db,auth,s):
    owner,task=store.binding(db,auth,s['revision'],paused_ok=True)
    body=json.loads(task['body'])
    need(all(body.get(k)==s[k] for k in ('card','revision','B','H') if k in s)
         and body.get('B')==s['base'] and body.get('H')==s['head'],'publication-assignment-drift')
    return owner

def exact_pr(p,s):
    return (type(p) is dict and type(p.get('number')) is int and p['number']>0
            and p.get('state')=='open' and p.get('draft') is True
            and p.get('title')==s['title'] and p.get('body')==s['body']
            and p.get('head')==dict(ref=s['branch'],sha=s['head'])
            and p.get('base')==dict(ref='main',sha=s['base']))

def observe(s,port,kind):
    repo=port.repo()
    need((repo.get('id'),repo.get('full_name'),repo.get('default_branch'))==(s['repo_id'],s['repository'],'main'),'publication-repository-drift')
    ref=port.ref(s['branch'])
    need(ref is None or ref==s['head'],'publication-ref-conflict')
    if kind=='ref':
        return None if ref is None else dict(head=ref)
    need(ref==s['head'],'publication-pr-needs-exact-ref')
    pulls=port.pulls(s['branch'])
    need(pulls.get('complete') is True and type(pulls.get('items')) is list
         and len(pulls['items'])<=1,'publication-pr-list-incomplete-or-ambiguous')
    if not pulls['items']:return None
    row=pulls['items'][0]
    need(exact_pr(row,s) and exact_pr(port.pull(row['number']),s)
         and port.ref(s['branch'])==s['head'],'publication-pr-readback-mismatch')
    return dict(number=row['number'],head=s['head'],base=s['base'])

def advance(store,auth,subject,port,kind):
    try:
        validate(subject)
        need(kind in ('ref','pr'),'publication-effect-kind')
        s=subject
        key=f"first-publication:{s['repo_id']}:{s['branch']}:{kind}"
        target=wire(s)
        with store.mutex:
            def lookup(db):
                owner=bound(store,db,auth,s)
                row=db.execute('select * from effects where operation_key=?',(key,)).fetchone()
                if row:
                    need(row['revision']==s['revision'] and row['generation']==owner['generation']
                         and row['kind']=='first-'+kind and row['target']==target and row['issue_count']==1
                         and row['phase'] in ('unknown','observed'),'publication-effect-subject-mismatch')
                return owner,row
            with store.tx() as db:
                _,row=lookup(db)
            result=observe(s,port,kind)
            if result is None:
                if row:return dict(action='hold',reason='publication-unknown-readback-only',effect_id=row['effect_id'])
                with store.tx() as db:
                    owner,prior=lookup(db)
                    need(prior is None,'publication-consume-race')
                    effect=secrets.token_hex(24)
                    db.execute('insert into effects(effect_id,revision,generation,operation_key,kind,target,phase,issue_count) values(?,?,?,?,?,?,?,1)',
                               (effect,s['revision'],owner['generation'],key,'first-'+kind,target,'unknown'))
                    store.event(db,'publication-consumed-before-send',dict(effect_id=effect,kind=kind,subject=s))
                try:
                    if kind=='ref':port.create_ref(s['branch'],s['head'])
                    else:port.create_pr(s)
                except (OSError,ValueError,subprocess.SubprocessError):
                    return dict(action='hold',reason='publication-send-uncertain-readback-only',effect_id=effect)
                result=observe(s,port,kind)
                need(result is not None,'publication-awaiting-readback-only')
            with store.tx() as db:
                _,row=lookup(db)
                if row:
                    if row['phase']=='unknown':
                        db.execute("update effects set phase='observed',observation=? where effect_id=?",(wire(result),row['effect_id']))
                        store.event(db,'publication-exact-readback',dict(effect_id=row['effect_id'],kind=kind,result=result))
                    else:need(row['observation']==wire(result),'publication-observation-drift')
                return dict(action='confirmed',effect_id=row['effect_id'] if row else None,**result)
    except (OSError,ValueError,KeyError,TypeError,subprocess.SubprocessError) as e:
        return dict(action='hold',reason=str(e) if type(e) is ValueError else 'publication-observation-unavailable')
