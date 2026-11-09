"""Explicit old-owner release then one approved batch CAS. No token retrieval."""
import hashlib
import hmac
import json
import re
import time
from runtime_store import need,wire,digest
from exact_scope import exact_fields,assignment,positive,sha


def _permit(broker,grant_id,mutation):
    ref=broker.binding.reference('owner-transfer')
    need(ref['kind']=='authorization','independent-owner-transfer-installation-required')
    policy=broker.private_authorization('owner-transfer','lab-owner-transfer/1')
    exact_fields(policy,{'kind','grant_id','broker_installation_sha256','source_closure_sha256','role',
        'native_session','physical_cwd','previous_owner','assignment','capability_epoch','candidates','not_before','expires_at'})
    need(type(grant_id) is str and re.fullmatch('[A-Za-z0-9_-]{1,128}',grant_id)
         and policy['grant_id']==grant_id and policy['broker_installation_sha256']==broker.cfg['pin']
         and policy['source_closure_sha256']==broker.binding.selected_source_closure_sha256
         and policy['role']==broker.cfg['role'] and policy['native_session']==broker.session_id
         and policy['physical_cwd']==broker.cfg['physical_cwd'],'exact-installed-transfer-subject-required')
    exact_fields(policy['previous_owner'],{'client','generation'})
    positive(policy['previous_owner']['generation']);positive(policy['capability_epoch']);assignment(policy['assignment'])
    need(type(policy['previous_owner']['client']) is str and bool(policy['previous_owner']['client'])
         and policy['assignment']['role']==broker.cfg['role']
         and policy['assignment']['session_id']==broker.session_id
         and policy['assignment']['physical_cwd']==broker.cfg['physical_cwd'],'installed-owner-assignment-mismatch')
    candidates=policy['candidates']
    need(type(candidates) is dict and 1<=len(candidates)<=2,'bounded-explicit-contender-batch-required')
    seen=set()
    for client,record in candidates.items():
        need(type(client) is str and re.fullmatch('[A-Za-z0-9_-]{1,128}',client)
             and client!=policy['previous_owner']['client'],'exact-new-contender-client-required')
        exact_fields(record,{'capability_sha256','new_owner_token_sha256'})
        for pin in record.values():
            sha(pin,64);need(pin not in seen,'distinct-client-capabilities-and-new-credentials-required');seen.add(pin)
    need(type(policy['not_before']) is int and type(policy['expires_at']) is int
         and 0<policy['expires_at']-policy['not_before']<=900,'bounded-one-time-transfer-window-required')
    if mutation:
        need(policy['not_before']<=time.time()<=policy['expires_at'],'transfer-permit-not-current')
    return policy,ref['sha256']


def _task(broker,db,policy):
    current=broker.store.current(db)
    need(current and current['phase']=='fenced' and current['fence_seq'] is not None
         and current['capability_epoch']==policy['capability_epoch']
         and wire(json.loads(current['body']))==wire(policy['assignment']),'exact-current-fenced-assignment-required')
    role=db.execute('select session_id,phase from role_registration where id=1').fetchone()
    need(role and role['session_id']==broker.session_id and role['phase']=='stable','actual-stable-role-required')
    need(db.execute("select 1 from turns where state!='terminal'").fetchone() is None,'live-native-turn-blocks-transfer')
    need(db.execute("select 1 from waits where phase in ('pending','ready','delivery-unknown')").fetchone() is None,
         'live-or-reply-unknown-wait-blocks-transfer')
    for row in db.execute("select * from effects where phase in ('planned','issuing','inflight','sending') or lower(phase) like '%unknown%'").fetchall():
        # Only already-final-reviewed terminal-only orphan history is non-executable;
        # NEVER mask Git/GitHub/CI/native-resume/Goal unknown by changing owner.
        archived=db.execute('select evidence from orphan_handoff_archive where effect_id=?',(row['effect_id'],)).fetchone()
        handoff=db.execute('select phase from native_handoff where effect_id=?',(row['effect_id'],)).fetchone()
        need(row['kind']=='native-wait-owned-handoff' and archived and handoff and handoff['phase']=='orphan-archived',
             'unresolved-executable-effect-blocks-transfer')
        proof=json.loads(archived['evidence'])
        need(proof.get('terminal_archive_only') is True and proof.get('reply_restored') is False
             and proof.get('handoff_recovered') is False,'orphan-not-permanently-nonexecutable')
    return current


def _identity(policy,pin):
    key='owner-transfer:'+policy['grant_id']+':'+str(policy['previous_owner']['generation'])
    target=dict(authorization_sha256=pin,previous_owner=policy['previous_owner'],
                assignment_sha256=digest(wire(policy['assignment'])),capability_epoch=policy['capability_epoch'])
    return key,wire(target)


def _effect(db,policy,pin):
    key,target=_identity(policy,pin)
    row=db.execute('select * from effects where operation_key=?',(key,)).fetchone()
    if row:
        need(row['kind']=='owner-transfer' and row['phase'] in ('released','claimed')
             and row['target']==target and row['issue_count']==1
             and row['generation']==policy['previous_owner']['generation']
             and row['revision']==policy['assignment']['revision'],'transfer-grant-rebound-or-inexact')
    return row


def _candidate(policy,args,with_token):
    fields={'grant_id','client','capability'} | ({'expected_generation','new_owner_token'} if with_token else set())
    exact_fields(args,fields)
    need(type(args['client']) is str and args['client'] in policy['candidates'],'unapproved-contender')
    sha(args['capability'],64)
    allowed=policy['candidates'][args['client']]
    need(hmac.compare_digest(digest(args['capability']),allowed['capability_sha256']),'unapproved-client-capability')
    if with_token:
        positive(args['expected_generation']);sha(args['new_owner_token'],64)
        need(args['expected_generation']==policy['previous_owner']['generation']
             and hmac.compare_digest(digest(args['new_owner_token']),allowed['new_owner_token_sha256']),
             'exact-preprovisioned-new-owner-credential-required')


def _status(db,policy,pin):
    effect=_effect(db,policy,pin)
    if effect is None:return dict(action='hold',reason='old-owner-not-released',owner_granted=False)
    observed=json.loads(effect['observation']) if effect['observation'] else None
    return dict(action='released' if effect['phase']=='released' else 'claimed',effect_id=effect['effect_id'],
                outcome=observed,credential_returned=False,old_revision_reactivated=False,read_only=True)


def route_transfer(broker,request):
    command=request.get('command');args=request.get('arguments',{})
    need(type(args) is dict,'typed-owner-transfer-arguments-required')
    exact_fields(request, {'command','arguments','auth'} if command=='owner.transfer-release' else {'command','arguments'})
    policy,pin=_permit(broker,args.get('grant_id'),mutation=command=='owner.transfer-release')
    if command=='owner.transfer-release':
        exact_fields(args,{'grant_id'})
        with broker.store.tx() as db:
            owner=broker.store.owner(db,request['auth'])
            need(wire(dict(client=owner['client'],generation=owner['generation']))==wire(policy['previous_owner'])
                 and owner['paused'],'actual-old-owner-explicit-release-required')
            _task(broker,db,policy)
            need(_effect(db,policy,pin) is None,'one-time-release-already-consumed')
            key,target=_identity(policy,pin)
            import secrets
            effect_id=secrets.token_hex(24)
            db.execute("insert into effects(effect_id,revision,generation,operation_key,kind,target,phase,issue_count) "
                       "values(?,?,?,?,?,?,'released',1)",(effect_id,policy['assignment']['revision'],owner['generation'],key,'owner-transfer',target))
            changed=db.execute('update owner set token_sha=null,paused=1 '
                               'where id=1 and generation=? and client=?',(owner['generation'],owner['client']))
            need(changed.rowcount==1,'old-owner-release-cas-failed')
            broker.store.event(db,'exact-owner-released-before-contender-claim',
                              dict(effect_id=effect_id,old_generation=owner['generation'],grant_id=policy['grant_id']))
            return dict(action='released',effect_id=effect_id,old_requests_fenced=True,new_owner_granted=False)
    _candidate(policy,args,with_token=command=='owner.transfer-claim')
    with broker.store.tx() as db:
        if command=='owner.transfer-status':return _status(db,policy,pin)
        need(command=='owner.transfer-claim','unsupported-owner-transfer-operation')
        effect=_effect(db,policy,pin)
        need(effect is not None,'old-owner-still-may-act-no-takeover')
        if effect['phase']!='released':
            result=_status(db,policy,pin)
            winner=(result.get('outcome') or {}).get('client')
            result.update(action='already-claimed' if winner==args['client'] else 'lost',owner_granted=False)
            return result
        _claim_window(broker,db,policy,pin,effect)
        _task(broker,db,policy)
        predecessor=policy['previous_owner']['generation']
        row=db.execute('select client,generation,paused,token_sha is null as released from owner where id=1').fetchone()
        need(row and row['client']==policy['previous_owner']['client'] and row['generation']==predecessor and row['paused'] and row['released']==1,
             'actual-old-owner-release-required-before-acquire')
        generation=predecessor+1
        changed=db.execute('update owner set client=?,generation=?,token_sha=?,paused=1 '
                           'where id=1 and client=? and token_sha is null and generation=? and paused=1',
                           (args['client'],generation,digest(args['new_owner_token']),policy['previous_owner']['client'],predecessor))
        need(changed.rowcount==1,'approved-owner-generation-cas-lost')
        winner=dict(client=args['client'],generation=generation,paused=True)
        changed=db.execute("update effects set phase='claimed',observation=? where effect_id=? and phase='released'",
                           (wire(winner),effect['effect_id']))
        need(changed.rowcount==1,'transfer-effect-cas-lost')
        broker.store.event(db,'one-approved-contender-owner-granted',dict(effect_id=effect['effect_id'],winner=winner))
        return dict(action='claimed',effect_id=effect['effect_id'],winner=winner,owner_granted=True,credential_returned=False,
                    assignment_still_fenced=True,execution_authority_granted=False)


def _claim_window(broker,db,policy,pin,effect):
    now=time.time()
    if policy['not_before']<=now<=policy['expires_at']:
        return
    # No public renewal endpoint/body. A trusted independent installer must
    # select this new private reference and a fresh startup snapshot explicitly.
    renewal=broker.private_authorization('owner-transfer-renewal','lab-owner-transfer-renewal/1')
    exact_fields(renewal,{'kind','grant_id','original_authorization_sha256','original_effect_id',
                         'broker_installation_sha256','source_closure_sha256','previous_owner','not_before','expires_at'})
    need(renewal['grant_id']==policy['grant_id'] and renewal['original_authorization_sha256']==pin
         and renewal['original_effect_id']==effect['effect_id'] and effect['phase']=='released'
         and renewal['broker_installation_sha256']==broker.cfg['pin']
         and renewal['source_closure_sha256']==broker.binding.selected_source_closure_sha256
         and wire(renewal['previous_owner'])==wire(policy['previous_owner'])
         and type(renewal['not_before']) is int and type(renewal['expires_at']) is int
         and 0<renewal['expires_at']-renewal['not_before']<=900
         and renewal['not_before']<=now<=renewal['expires_at'],'independent-renewal-for-exact-released-effect-required')
    broker.store.event(db,'independent-released-transfer-renewal-selected',dict(effect_id=effect['effect_id'],grant_id=policy['grant_id']))


def prove_released_owner_for_startup(db,binding,expected_owner):
    """Read-only released-state validation, never owner execution permission."""
    from private_resources import load_reference
    ref=binding.reference('owner-transfer')
    need(ref['kind']=='authorization','released-owner-original-permit-reference-required')
    policy=load_reference(ref['path'],ref['sha256'])
    selected=binding.view()
    need(policy.get('kind')=='lab-owner-transfer/1'
         and policy.get('broker_installation_sha256')==selected['ledger']['installation_sha256']
         and policy.get('source_closure_sha256')==binding.selected_source_closure_sha256
         and policy.get('native_session')==selected['native']['expected_registered_session']
         and policy.get('role')==selected['role']['name'] and policy.get('physical_cwd')==selected['role']['physical_cwd']
         and wire(policy.get('previous_owner'))==wire(dict(client=expected_owner['client'],generation=expected_owner['generation'])),
         'released-owner-startup-identity-mismatch')
    key,target=_identity(policy,ref['sha256'])
    row=db.execute("select effect_id,target,generation,revision,issue_count from effects "
                   "where operation_key=? and kind='owner-transfer' and phase='released'",(key,)).fetchone()
    need(row and row[1]==target and row[2]==expected_owner['generation']
         and row[3]==policy['assignment']['revision'] and row[4]==1,'exact-released-transfer-effect-required-for-startup')
    task=db.execute('select body,phase,capability_epoch from tasks order by revision desc limit 1').fetchone()
    need(task and wire(json.loads(task[0]))==wire(policy['assignment']) and task[1]=='fenced'
         and task[2]==policy['capability_epoch'],'released-owner-startup-task-must-stay-fenced')
    # Expired mutation window does not prevent readonly status/startup under a
    # separate fresh startup authorization. It STILL prevents claim sans renewal.
    return True
