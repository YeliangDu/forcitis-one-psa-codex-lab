"""Bounded advancement of the same published card and pull request."""
import json,secrets,subprocess
from first_publication import FIELDS,OID,validate,bound
from runtime_store import need,wire

EXTRA={'expected','previous_body','number'}

def inspect(s,port,kind):
    repo=port.repo()
    need((repo.get('id'),repo.get('full_name'),repo.get('default_branch'))==(s['repo_id'],s['repository'],'main'),'followup-repo-drift')
    ref=port.ref(s['branch']);need(ref in (s['expected'],s['head']),'followup-ref-moved')
    if kind=='ref':return dict(head=ref) if ref==s['head'] else None
    need(ref==s['head'],'followup-pr-ref-not-current')
    r=port.pull(s['number'])
    need(r.get('number')==s['number'] and r.get('state')=='open'
         and r.get('head')==dict(ref=s['branch'],sha=s['head'])
         and r.get('base')==dict(ref='main',sha=s['base']) and r.get('title')==s['title'],'followup-pr-identity-drift')
    if r.get('draft') is False and r.get('body')==s['body']:
        return dict(head=ref,number=s['number'],base=s['base'])
    need(r.get('draft') is True and r.get('body')==s['previous_body'],'followup-pr-body-or-draft-drift')
    return None

def advance(store,auth,subject,port,kind):
    try:
        s=subject
        need(type(s) is dict and set(s)==FIELDS|EXTRA and kind in ('ref','pr'),'followup-exact-subject')
        base={k:s[k] for k in FIELDS};validate(base)
        need(type(s['expected']) is str and OID.fullmatch(s['expected']) and s['expected']!=s['head']
             and type(s['number']) is int and s['number']>0 and type(s['previous_body']) is str
             and s['previous_body']!=s['body'],'followup-subject-values')
        key=f"followup:{s['repo_id']}:{s['branch']}:{s['expected']}:{kind}";target=wire(s)
        with store.mutex:
            def lookup(db):
                owner=bound(store,db,auth,base)
                task=store.current(db)
                need(json.loads(task['body']).get('A')==s['expected'],'followup-acceptance-preimage-drift')
                row=db.execute('select * from effects where operation_key=?',(key,)).fetchone()
                if row:
                    need(row['target']==target and row['revision']==s['revision'] and row['generation']==owner['generation']
                         and row['kind']=='followup-'+kind and row['issue_count']==1 and row['phase'] in ('unknown','observed'),'followup-effect-drift')
                return owner,row
            with store.tx() as db:_,row=lookup(db)
            observation=inspect(s,port,kind)
            if observation is None:
                if row:return dict(action='hold',reason='followup-unknown-readback-only',effect_id=row['effect_id'])
                with store.tx() as db:
                    owner,prior=lookup(db);need(prior is None,'followup-effect-race')
                    eid=secrets.token_hex(24)
                    db.execute('insert into effects(effect_id,revision,generation,operation_key,kind,target,phase,issue_count) values(?,?,?,?,?,?,?,1)',
                               (eid,s['revision'],owner['generation'],key,'followup-'+kind,target,'unknown'))
                    store.event(db,'card-followup-consumed-before-send',dict(effect_id=eid,subject=s,kind=kind))
                try:
                    if kind=='ref':port.update_ref(s['branch'],s['expected'],s['head'])
                    else:port.ready_pr(s['number'],s['head'],s['title'],s['body'])
                except (OSError,ValueError,subprocess.SubprocessError):
                    return dict(action='hold',reason='followup-send-uncertain-readback-only',effect_id=eid)
                observation=inspect(s,port,kind);need(observation is not None,'followup-awaiting-readback-only')
            with store.tx() as db:
                _,row=lookup(db)
                if row:
                    if row['phase']=='unknown':
                        db.execute("update effects set phase='observed',observation=? where effect_id=?",(wire(observation),row['effect_id']))
                        store.event(db,'card-followup-exact-readback',dict(effect_id=row['effect_id'],result=observation))
                    else:need(row['observation']==wire(observation),'followup-observation-drift')
                return dict(action='confirmed',effect_id=row['effect_id'] if row else None,**observation)
    except (ValueError,OSError,TypeError,KeyError,subprocess.SubprocessError) as e:
        return dict(action='hold',reason=str(e) if type(e) is ValueError else 'followup-observation-unavailable')
