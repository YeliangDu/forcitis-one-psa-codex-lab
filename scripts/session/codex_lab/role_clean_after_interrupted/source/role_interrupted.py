"""Reconcile one already-issued native interruption without opening a transport."""
import fcntl
import os
from pathlib import Path
import re
import stat

from native_role import canonical_uuid
from owned_runtime import decode, fields, private, sha
from runtime_broker import physical, private_file
from runtime_store import need, wire

OBSERVATION='role-native-interrupted-business-failed'
FENCE_REASON='interrupted-native-turn-reconciled'
ARGUMENTS=('revision','thread_id','turn_id','effect_id','rollout_sha256')
TERMINALS={'turn_aborted','task_complete','turn_complete','turn_completed'}


def validate_rollout(a,raw):
    fields(a,ARGUMENTS)
    tid=canonical_uuid(a['thread_id'])
    turn=canonical_uuid(a['turn_id'])
    need(type(a['revision']) is int and a['revision']==3 and type(a['effect_id']) is str
         and re.fullmatch('[0-9a-f]{32,48}',a['effect_id']) and type(a['rollout_sha256']) is str
         and re.fullmatch('[0-9a-f]{64}',a['rollout_sha256']),
         'rev3-exact-evidence-required')
    need(type(raw) is bytes and 0<len(raw)<=16*1024*1024 and sha(raw)==a['rollout_sha256']
         and raw.endswith(b'\n'),'native-rollout-pin-mismatch')
    rows=[decode(line) for line in raw.splitlines()]
    need(all(type(x) is dict and type(x.get('payload')) is dict for x in rows),'invalid-rollout-record')
    metadata=[x['payload'] for x in rows if x.get('type')=='session_meta']
    need(rows and rows[0].get('type')=='session_meta' and len(metadata)==1 and metadata[0].get('id')==tid,
         'native-rollout-thread-mismatch')
    terminals=[(n,x['payload']) for n,x in enumerate(rows,1) if x.get('type')=='event_msg'
               and x['payload'].get('type') in TERMINALS]
    need(all(type(x.get('turn_id')) is str for _,x in terminals),'ambiguous-native-terminal')
    matches=[(n,x) for n,x in terminals if x.get('turn_id')==turn]
    need(len(matches)==1 and matches[0][1].get('type')=='turn_aborted'
         and matches[0][1].get('reason')=='interrupted','unique-native-interruption-required')
    return dict(type='turn_aborted',reason='interrupted',line=matches[0][0])


def binding(store,db,auth,a,proof):
    fields(auth,('client','generation','token'))
    owner=store.owner(db,auth)
    task=store.current(db)
    rev,tid,turn,effect=(a[k] for k in ('revision','thread_id','turn_id','effect_id'))
    need(owner['paused']==1 and task and task['revision']==rev and task['phase'] in ('active','fenced'),
         'paused-current-rev3-required')
    body=decode(task['body'])
    reg=db.execute('select * from role_registration where id=1').fetchone()
    native=db.execute('select * from turns where revision=?',(rev,)).fetchall()
    effects=db.execute('select * from effects where revision=?',(rev,)).fetchall()
    dispatches=db.execute('select * from native_dispatch where revision=?',(rev,)).fetchall()
    need(body.get('revision')==rev and body.get('session_id')==tid and body.get('role')=='one-readonly-lab'
         and reg and reg['session_id']==tid and reg['first_turn_id']==turn and reg['phase']=='provisional'
         and reg['readback_sha256'] is None and reg['generation']==owner['generation']
         and len(native)==len(effects)==len(dispatches)==1,'current-provisional-effect-mismatch')
    t,e,d=native[0],effects[0],dispatches[0]
    original_epoch=task['capability_epoch']-(task['phase']=='fenced')
    need(t['turn_id']==turn and t['owner_generation']==d['generation']==e['generation']==owner['generation']
         and t['epoch']==d['epoch']==original_epoch and d['session_id']==tid and d['turn_id']==turn
         and e['effect_id']==d['effect_id']==effect and e['operation_key']==f'native-turn:{tid}:{rev}'
         and e['kind']=='native-turn' and e['phase']==d['phase']=='observed' and e['issue_count']==1
         and e['observation'] is None and e['advances']==0
         and type(d['connection_id']) is str and bool(d['connection_id'])
         and type(d['prompt_sha256']) is str and re.fullmatch('[0-9a-f]{64}',d['prompt_sha256'])
         and decode(e['target'])==dict(session_id=tid,connection_id=d['connection_id'],prompt_sha256=d['prompt_sha256']),
         'once-observed-native-dispatch-unproved')
    need(not db.execute('select 1 from native_tool_operations where revision=? or turn_id=?',(rev,turn)).fetchone()
         and not db.execute('select 1 from waits where revision=?',(rev,)).fetchone(),
         'native-tool-or-wait-evidence-conflict')
    value=dict(a,native_status='interrupted',business_accepted=False,terminal=proof,
               owner_client=owner['client'],owner_generation=owner['generation'],
               dispatch_connection_id=d['connection_id'],capability_epoch=original_epoch,issue_count=1,broker_tool_completions=0)
    records=[]
    for r in db.execute('select seq,body from events where kind=? order by seq',(OBSERVATION,)):
        if decode(r['body']).get('revision')==rev:records.append(r)
    need(len(records)<=1 and (not records or decode(records[0]['body'])==value),
         'interrupted-observation-evidence-conflict')
    return task,t,value,records[0]['seq'] if records else None


def reconcile_interrupted(store,auth,arguments,rollout_raw):
    proof=validate_rollout(arguments,rollout_raw)
    with store.tx() as db:
        task,turn,body,sequence=binding(store,db,auth,arguments,proof)
        if sequence is None:
            need(task['phase']=='active' and turn['state']=='active','unobserved-interrupted-turn-not-active')
            n=db.execute("update turns set state='terminal' where turn_id=? and state='active'",(turn['turn_id'],))
            need(n.rowcount==1,'interrupted-turn-transition-lost')
            sequence=store.event(db,OBSERVATION,body)
        else:
            need(turn['state']=='terminal','interrupted-observation-turn-not-terminal')
        return dict(action='interrupted-business-failed',observation_seq=sequence,
                    receipt=sha(wire(body).encode()),native_status='interrupted',business_accepted=False)


def fence_interrupted(store,auth,arguments,rollout_raw):
    fields(arguments,(*ARGUMENTS,'observation_seq'))
    a={k:arguments[k] for k in ARGUMENTS}
    proof=validate_rollout(a,rollout_raw)
    with store.tx() as db:
        task,turn,_,sequence=binding(store,db,auth,a,proof)
        need(sequence is not None and type(arguments['observation_seq']) is int
             and sequence==arguments['observation_seq'] and turn['state']=='terminal',
             'interrupted-fence-observation-required')
        if task['phase']=='active':
            receipt=store._fence(db,FENCE_REASON)
            need(receipt and receipt['revision']==a['revision'],'interrupted-fence-mismatch')
            revoking,fenced=receipt['revoking_seq'],receipt['fenced_seq']
        else:
            fenced=task['fence_seq']
            need(type(fenced) is int and fenced>sequence+1,'interrupted-fence-sequence-mismatch')
            revoking=fenced-1
            rows=db.execute('select kind,body from events where seq in (?,?) order by seq',(revoking,fenced)).fetchall()
            need([(r['kind'],decode(r['body'])) for r in rows]==[
                ('assignment-revoking',dict(revision=a['revision'],reason=FENCE_REASON)),
                ('execution-fenced',dict(revision=a['revision']))],'interrupted-fence-replay-mismatch')
        return dict(action='interrupted-assignment-fenced',revision=a['revision'],observation_seq=sequence,
                    revoking_seq=revoking,fenced_seq=fenced)


def run_owned_interrupted(installation,installation_pin,request,role_installation,role_pin):
    from single_role import RoleInstallation,TaskStore
    from role_native import persisted_declarations
    i=RoleInstallation(installation,installation_pin,role_installation,role_pin)
    need(i.is_native and i.root==Path('/private/tmp/lab60-owned-single-role-v1') and request is not None,
         'interrupted-reviewed-private-slot-only')
    lock=i.runtime/'owned.lock'
    initial=private(lock,stat.S_ISREG)
    with os.fdopen(os.open(lock,os.O_RDONLY|os.O_NOFOLLOW),'rb') as handle:
        fcntl.flock(handle,fcntl.LOCK_EX|fcntl.LOCK_NB)
        held=os.fstat(handle.fileno());current=lock.lstat()
        need((held.st_dev,held.st_ino)==(current.st_dev,current.st_ino)==(initial.st_dev,initial.st_ino)
             and not i.sock.exists() and not i.sock.is_symlink(),'active-or-changed-owned-runtime')
        path=physical(request)
        need(path.parent==i.root,'private-interrupted-request-required')
        message=decode(private_file(str(path)))
        fields(message,('command','auth','arguments'))
        ops={'role.reconcile-interrupted':reconcile_interrupted,'assignment.fence-interrupted':fence_interrupted}
        need(type(message['command']) is str and message['command'] in ops,'interrupted-command-not-allowed')
        a=message['arguments'];fields(a,ARGUMENTS if message['command']=='role.reconcile-interrupted' else (*ARGUMENTS,'observation_seq'))
        tid=canonical_uuid(a['thread_id'])
        raw,_=persisted_declarations(i,tid)
        store=TaskStore(i)
        try:result=ops[message['command']](store,message['auth'],a,raw)
        finally:store.db.close()
        print(wire(result))
    return 0
