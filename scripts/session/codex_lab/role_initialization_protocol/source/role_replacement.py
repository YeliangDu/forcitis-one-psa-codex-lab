"""One-shot transition of the existing single logical role's native identity."""
import uuid

from native_role import canonical_uuid
from owned_runtime import decode
from runtime_store import need

ARCHIVE='role-registration-archived'
RESERVE='role-replacement-reserved'
BOUND='role-replacement-bound'


def event(db,kind):
    rows=db.execute('select seq,body from events where kind=? order by seq',(kind,)).fetchall()
    need(len(rows)<=1,'replacement-event-not-unique')
    return (rows[0]['seq'],decode(rows[0]['body'])) if rows else None


def require_paused(store,db,auth):
    owner=store.owner(db,auth)
    need(owner['paused'],'native-role-replacement-requires-paused-owner')
    return owner


def retire(store,db,owner,old_thread):
    old=canonical_uuid(old_thread)
    need(not event(db,ARCHIVE) and not event(db,RESERVE) and not event(db,BOUND),'native-role-retirement-already-consumed')
    reg=db.execute('select * from role_registration where id=1').fetchone()
    task=store.current(db)
    need(reg and reg['session_id']==old and reg['generation']==owner['generation']
         and reg['phase'] in ('registered','provisional','stable') and task and task['phase']=='active',
         'old-role-not-current')
    body=decode(task['body'])
    need(body.get('session_id')==old and body.get('role')=='one-readonly-lab','old-assignment-role-mismatch')
    failed=[]
    for e in db.execute("select seq,body from events where kind='single-role-readonly-task-business-failed'"):
        v=decode(e['body'])
        if v.get('thread_id')==old and v.get('revision')==task['revision']:
            failed.append((e['seq'],v))
    need(len(failed)==1 and failed[0][1].get('native_status')=='completed'
         and failed[0][1].get('business_accepted') is False,'exact-native-business-failure-required')
    seq,proof=failed[0]
    turn=canonical_uuid(proof['turn_id'])
    t=db.execute('select * from turns where turn_id=?',(turn,)).fetchone()
    ds=db.execute('select * from native_dispatch where turn_id=?',(turn,)).fetchall()
    eff=db.execute('select * from effects where effect_id=?',(proof.get('effect_id'),)).fetchone()
    need(t and t['state']=='terminal' and t['revision']==task['revision']
         and t['owner_generation']==owner['generation'] and t['epoch']==task['capability_epoch']
         and len(ds)==1 and ds[0]['phase']=='observed' and ds[0]['effect_id']==proof.get('effect_id')
         and ds[0]['session_id']==old and ds[0]['revision']==task['revision']
         and ds[0]['generation']==owner['generation'] and ds[0]['epoch']==task['capability_epoch']
         and eff and eff['kind']=='native-turn' and eff['issue_count']==1
         and eff['revision']==task['revision'] and eff['generation']==owner['generation']
         and db.execute("select 1 from turns where state!='terminal'").fetchone() is None
         and db.execute("select 1 from native_dispatch where phase='unknown'").fetchone() is None,
         'old-native-issuance-not-proved-terminal')
    fence=store._fence(db,'retired-failed-native-role')
    need(fence and fence['revision']==task['revision'],'old-assignment-fence-required')
    archive=store.event(db,ARCHIVE,dict(registration=dict(reg),old_thread=old,role=body['role'],
             generation=owner['generation'],owner_client=owner['client'],revision=task['revision'],
             failed_turn=turn,failed_event_sequence=seq,fence=fence))
    db.execute("update role_registration set phase='retired' where id=1")
    return dict(action='role-retired',old_thread_id=old,archive_sequence=archive,fence=fence)


def reserve(store,db,owner,old_thread,epoch):
    old=canonical_uuid(old_thread)
    need(type(epoch) is str and 0<len(epoch)<=128,'replacement-epoch-required')
    archive=event(db,ARCHIVE)
    reg=db.execute('select * from role_registration where id=1').fetchone()
    task=store.current(db)
    need(archive and archive[1]['old_thread']==old and archive[1]['generation']==owner['generation']
         and reg and reg['phase']=='retired' and reg['session_id']==old
         and task and task['phase']=='fenced' and task['revision']==archive[1]['revision']
         and not event(db,RESERVE) and not event(db,BOUND),'replacement-reservation-not-permitted')
    reservation=uuid.uuid4().hex
    seq=store.event(db,RESERVE,dict(old_thread=old,epoch=epoch,reservation=reservation,
                     generation=owner['generation'],archive_sequence=archive[0]))
    db.execute("update role_registration set phase='replacement-pending' where id=1")
    return dict(action='replacement-reserved',reservation_sequence=seq,epoch=epoch)


def retire_failed_role(store,auth,old_thread):
    with store.tx() as db:
        return retire(store,db,require_paused(store,db,auth),old_thread)


def reserve_replacement(store,auth,old_thread,epoch):
    with store.tx() as db:
        return reserve(store,db,require_paused(store,db,auth),old_thread,epoch)


def retire_and_reserve(store,auth,old_thread,epoch):
    """Public replacement cut; no native call until both mutations commit."""
    with store.tx() as db:
        owner=require_paused(store,db,auth)
        if not event(db,ARCHIVE):retire(store,db,owner,old_thread)
        return reserve(store,db,owner,old_thread,epoch)


def bind_replacement(store,auth,old_thread,epoch,new_thread):
    old=canonical_uuid(old_thread);new=canonical_uuid(new_thread)
    need(new!=old,'replacement-native-id-not-new')
    with store.tx() as db:
        owner=require_paused(store,db,auth)
        reg=db.execute('select * from role_registration where id=1').fetchone()
        pending=event(db,RESERVE)
        need(pending and pending[1]['epoch']==epoch and pending[1]['old_thread']==old
             and pending[1]['generation']==owner['generation'] and reg
             and reg['phase']=='replacement-pending' and reg['session_id']==old
             and not event(db,BOUND) and not db.execute('select 1 from native_dispatch where session_id=?',(new,)).fetchone(),
             'replacement-bind-not-current')
        db.execute("""update role_registration set effect_id=?,phase='provisional',
                   session_id=?,first_turn_id=NULL,readback_sha256=NULL where id=1""",
                   (pending[1]['reservation'],new))
        seq=store.event(db,BOUND,dict(old_thread=old,new_thread=new,epoch=epoch,
                        generation=owner['generation'],reservation_sequence=pending[0]))
        return dict(action='replacement-bound',old_thread_id=old,thread_id=new,epoch=epoch,bound_sequence=seq)
