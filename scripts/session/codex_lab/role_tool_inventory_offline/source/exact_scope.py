"""Explicit typed command grants, distinct from current identity/assignment scope.

No credential/source authentication supplied by this parser. Trusted launcher
pins private authorization bytes; underlying guards/effect truth remain mandatory.
"""
import json
import re
import uuid
from pathlib import Path
from runtime_store import need, wire, digest

ASSIGNMENT = {'card','revision','role','session_id','physical_cwd','task','A','B','H'}


def exact_fields(value, names):
    need(type(value) is dict and set(value) == set(names), 'exact-typed-fields-required')


def positive(value):
    need(type(value) is int and 0 < value <= 2**63-1, 'positive-typed-integer-required')


def sha(value, length):
    need(type(value) is str and re.fullmatch('[0-9a-f]{'+str(length)+'}', value) is not None, 'exact-source-digest-required')


def assignment(value):
    exact_fields(value, ASSIGNMENT)
    positive(value['card']);positive(value['revision'])
    need(type(value['role']) is str and re.fullmatch('one-[a-z][a-z0-9-]*',value['role']), 'exact-role-required')
    session=value['session_id']
    need(type(session) is str and str(uuid.UUID(session))==session,'exact-registered-session-required')
    cwd=value['physical_cwd']
    need(type(cwd) is str and Path(cwd).is_absolute() and str(Path(cwd))==cwd and '..' not in Path(cwd).parts,
         'exact-physical-workspace-path-required')
    need(type(value['task']) is str and 0<len(value['task'].encode())<=2048,'bounded-current-task-required')
    for key in ('A','B','H'):sha(value[key],40)


def revision_args(args, current):
    exact_fields(args, {'revision'})
    positive(args['revision'])
    need(args['revision']==current['revision'],'command-revision-not-current')


def target_for(broker, command, args, current, task, owner):
    """Finite CODE registry. Unknown management strings have no authority."""
    if command=='assignment.revoke':
        exact_fields(args,set())
        need(owner['paused'] and task['phase'] in ('active','fenced'),'paused-exact-current-fence-only')
        return dict(kind='fence-current-assignment-only',assignment=current,capability_epoch=task['capability_epoch'])
    if command=='assignment.publish':
        exact_fields(args,{'assignment'})
        future=args['assignment'];assignment(future)
        need(task['phase']=='fenced' and owner['paused'] and future['revision']>current['revision']
             and future['session_id']==broker.session_id and future['role']==broker.cfg['role']
             and future['physical_cwd']==broker.cfg['physical_cwd'], 'exact-next-paused-assignment-required')
        return dict(kind='next-paused-assignment',assignment=future,broker_paused=True)
    if command=='plan':
        revision_args(args,current)
        need(broker._plan_installation is not None,'independent-plan-installation-missing')
        policy=broker._plan_installation.load()
        need(policy==broker._plan_installation.policy and wire(policy['assignment'])==wire(current), 'plan-installation-not-current')
        sha(policy['controller'],40);positive(policy['number']);sha(policy['approval_sha256'],64)
        return dict(kind='existing-pr-readonly-plan',number=policy['number'],A=current['A'],B=current['B'],H=current['H'],
                    C=policy['controller'],approval_sha256=policy['approval_sha256'],
                    plan_installation_sha256=broker._plan_installation.pin)
    if command in ('role.resume','role.dispatch-current'):
        revision_args(args,current)
        need(task['phase']=='active','fenced-role-target-denied')
        profile=broker.binding.reference('permissions-profile')
        return dict(kind='registered-native-resume' if command=='role.resume' else 'registered-native-turn',
                    session_id=broker.session_id,assignment=current,capability_epoch=task['capability_epoch'],
                    permission_reference_sha256=profile['sha256'])
    if command in ('branch.prepare','branch.issue','branch.observe'):
        if command=='branch.prepare':revision_args(args,current)
        else:
            exact_fields(args,{'revision','effect_id'});positive(args['revision']);sha(args['effect_id'],48)
        need(broker._branch_installation is not None,'independent-branch-installation-missing')
        policy=broker._branch_installation.load()
        need(policy==broker._branch_installation.policy,'branch-installation-moved')
        approved=policy['approved']
        exact_fields(approved,{'subject','transport','session_id','revision','base','acceptance'})
        positive(approved['revision']);sha(approved['base'],40);sha(approved['acceptance'],40)
        need(approved['session_id']==broker.session_id,'branch-role-identity-not-exact')
        subject=approved['subject']
        exact_fields(subject,{'repo_id','branch','expected','head','card'})
        positive(subject['repo_id']);positive(subject['card']);sha(subject['expected'],40);sha(subject['head'],40)
        need(subject['repo_id']==1449415666 and type(subject['branch']) is str
             and re.fullmatch('codex/'+str(subject['card'])+'-[a-z0-9][a-z0-9-]{0,90}',subject['branch'])
             and subject['expected']!=subject['head'], 'exact-nonmain-existing-branch-only')
        if command!='branch.observe':
            need(args['revision']==current['revision']==approved['revision'] and task['phase']=='active'
                 and subject['card']==current['card'] and subject['head']==current['H']
                 and approved['base']==current['B'] and approved['acceptance']==current['A']
                 and approved['session_id']==broker.session_id,'branch-write-not-current-exact-subject')
        target=dict(kind='branch-cas-readonly-observe' if command=='branch.observe' else 'branch-cas',
                    subject=subject,revision=approved['revision'],base=approved['base'],acceptance=approved['acceptance'],
                    branch_installation_sha256=broker._branch_installation.pin)
        if command!='branch.prepare':
            with broker.store.tx() as db:
                row=db.execute('select * from effects where effect_id=?',(args['effect_id'],)).fetchone()
            expected=dict(subject=subject,authorization_sha256=digest(wire(approved)))
            need(row and row['kind']=='git-push-cas' and row['revision']==args['revision']==approved['revision']
                 and row['target']==wire(expected),'exact-existing-branch-effect-target-required')
            target['effect_id']=args['effect_id']
        return target
    raise ValueError('typed-command-not-implemented-default-deny')


def authorize_exact(broker, request, scope):
    exact_fields(request, {'command','arguments','auth'})
    exact_fields(request['auth'], {'client','generation','token'})
    need(type(request['command']) is str and type(request['arguments']) is dict,'exact-command-data-required')
    exact_fields(scope, {'kind','broker_installation_sha256','source_closure_sha256','actor',
                         'assignment','task_phase','capability_epoch','grants'})
    need(scope['kind']=='lab-runtime-exact-actions/2'
         and scope['broker_installation_sha256']==broker.cfg['pin']
         and scope['source_closure_sha256']==broker.binding.selected_source_closure_sha256,
         'independent-exact-scope-source-mismatch')
    with broker.store.tx() as db:
        owner=dict(broker.store.owner(db,request['auth']))
        selected=broker.store.current(db)
        need(selected is not None,'actual-current-assignment-required')
        task=dict(selected);current=json.loads(task['body'])
        registration=db.execute('select session_id,phase from role_registration where id=1').fetchone()
        need(registration and registration['phase']=='stable' and registration['session_id']==broker.session_id,
             'actual-stable-registry-required')
    assignment(current);assignment(scope['assignment'])
    exact_fields(scope['actor'],{'kind','owner','role','native_session'})
    exact_fields(scope['actor']['owner'],{'client','generation'})
    positive(scope['actor']['owner']['generation'])
    need(type(scope['actor']['owner']['client']) is str, 'typed-real-owner-client-required')
    need(wire(scope['actor'])==wire(dict(kind='coordinator-owner',owner=dict(client=owner['client'],generation=owner['generation']),
                             role=broker.cfg['role'],native_session=broker.session_id))
         and current['role']==broker.cfg['role'] and current['session_id']==broker.session_id
         and current['physical_cwd']==broker.cfg['physical_cwd']
         and str(Path(current['physical_cwd']).resolve(strict=True))==current['physical_cwd']
         and wire(scope['assignment'])==wire(current) and scope['task_phase']==task['phase']
         and type(scope['capability_epoch']) is int and scope['capability_epoch']==task['capability_epoch'],
         'actual-actor-owner-role-or-full-assignment-mismatch')
    grants=scope['grants'];need(type(grants) is list and 0<len(grants)<=32,'bounded-exact-grants-required')
    seen=set();matches=[]
    for grant in grants:
        exact_fields(grant,{'grant_id','command','arguments','target'})
        need(type(grant['grant_id']) is str and re.fullmatch('[a-zA-Z0-9_-]{1,128}',grant['grant_id'])
             and grant['grant_id'] not in seen and type(grant['command']) is str
             and '*' not in grant['command'] and type(grant['arguments']) is dict and type(grant['target']) is dict,
             'invalid-or-wildcard-exact-grant')
        seen.add(grant['grant_id'])
        if grant['command']==request['command'] and wire(grant['arguments'])==wire(request['arguments']):
            matches.append(grant)
    need(len(matches)==1,'no-unique-independently-approved-exact-command-and-arguments')
    derived=target_for(broker,request['command'],request['arguments'],current,task,owner)
    need(wire(matches[0]['target'])==wire(derived),'exact-approved-destination-mismatch')
    # Outer execution_gate remains held through this call and underlying guard.
    # This audit event is not an effect/reservation or executable approval.
    with broker.store.tx() as db:
        again=broker.store.current(db);actual_owner=broker.store.owner(db,request['auth'])
        need(again and dict(again)==task and actual_owner['generation']==owner['generation'], 'exact-scope-moved-before-delegate')
        broker.store.event(db,'exact-typed-command-scope-matched-before-existing-guard',
            dict(grant_id=matches[0]['grant_id'],command=request['command'],arguments_sha256=digest(wire(request['arguments'])),
                 target_sha256=digest(wire(derived)),revision=current['revision']))
