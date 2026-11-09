"""One manual reinitialization after a proved unmaterialized, stopped trial."""
import uuid
from native_role import canonical_uuid
from owned_runtime import decode
from role_replacement import BOUND,event,require_paused
from runtime_store import need

ABANDONED='role-initialization-abandoned-provisional'
RESERVED='role-initialization-reserved'
PROVISIONAL='role-initialization-provisional'


def prepare(store,auth,previous,epoch,prove_absence):
    previous=canonical_uuid(previous)
    with store.tx() as db:
        owner=require_paused(store,db,auth)
        prior=event(db,BOUND)
        reg=db.execute('select * from role_registration where id=1').fetchone()
        task=store.current(db)
        need(prior and prior[1]['new_thread']==previous and prior[1]['epoch']!=epoch
             and prior[1]['generation']==owner['generation'] and reg and reg['phase']=='provisional'
             and reg['generation']==owner['generation'] and reg['session_id']==previous
             and reg['first_turn_id'] is None and reg['readback_sha256'] is None
             and not event(db,ABANDONED) and not event(db,RESERVED) and not event(db,PROVISIONAL)
             and task and task['phase']=='active','one-shot-provisional-initialization-not-eligible')
        body=decode(task['body']);revision=task['revision']
        need(body.get('session_id')==previous and body.get('role')==store.installation['role']
             and not db.execute('select 1 from native_dispatch where session_id=? or revision=?',(previous,revision)).fetchone()
             and not db.execute('select 1 from effects where revision=?',(revision,)).fetchone()
             and not db.execute('select 1 from turns where revision=? or state!=\'terminal\'',(revision,)).fetchone(),
             'abandoned-provisional-has-dispatch-or-unfinished-turn')
        proof=prove_absence(previous,prior[1]['epoch'])
        fence=store._fence(db,'unmaterialized-provisional-abandoned')
        need(fence and fence['revision']==revision,'abandoned-provisional-fence-required')
        seq=store.event(db,ABANDONED,dict(registration=dict(reg),role=body['role'],revision=revision,
                  prior_bound_sequence=prior[0],fence=fence,reason='unmaterialized-do-not-resume',proof=proof))
        reservation=uuid.uuid4().hex
        reserved=store.event(db,RESERVED,dict(previous=previous,epoch=epoch,generation=owner['generation'],
                             reservation=reservation,abandoned_sequence=seq))
        db.execute("update role_registration set phase='initialization-pending' where id=1")
        return reserved


def bind(store,auth,previous,epoch,thread_id,receipt_id):
    tid=canonical_uuid(thread_id)
    with store.tx() as db:
        owner=require_paused(store,db,auth)
        row=db.execute('select * from role_registration where id=1').fetchone()
        prior=event(db,BOUND)
        pending=event(db,RESERVED)
        need(prior and pending and tid not in (previous,prior[1]['old_thread'])
             and row and row['phase']=='initialization-pending' and row['session_id']==previous
             and pending[1]['previous']==previous and pending[1]['generation']==owner['generation']
             and pending[1]['epoch']==epoch and not event(db,PROVISIONAL)
             and receipt_id.startswith(epoch+':'),'initialization-native-receipt-not-current')
        db.execute("""update role_registration set effect_id=?,phase='provisional',session_id=?,
                   first_turn_id=null,readback_sha256=null where id=1""",(pending[1]['reservation'],tid))
        seq=store.event(db,PROVISIONAL,dict(previous=previous,thread_id=tid,epoch=epoch,
                    native_reply_id=receipt_id,generation=owner['generation'],reservation_sequence=pending[0]))
        return dict(action='initialized-provisional',thread_id=tid,previous_thread_id=previous,
                    native_reply_id=receipt_id,epoch=epoch,sequence=seq)
