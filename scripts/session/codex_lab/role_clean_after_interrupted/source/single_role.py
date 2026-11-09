"""One explicitly selected read-only role on the owned Broker transport."""
import os
from pathlib import Path
import re
import stat
import subprocess
import time
import uuid

from exact_scope import assignment
from native_role import RoleStore, TOOL, canonical_uuid
from owned_runtime import (Channel, Installation, OwnerStore, OwnedBroker, decode,
                           private, sha, run as run_owned)
from runtime_broker import physical, private_file
from runtime_store import Store, need, wire
from role_network import MODEL, relay
import role_native
import role_initialization
import role_interrupted
from first_launch import FirstLaunch, require_resume, require_stable
from role_replacement import retire_failed_role, retire_and_reserve, bind_replacement

FIXTURE_SHA='86ed5477d7afd5e85bd01d7fafb76c187a7b20b5691563990a835e07f7e44702'
class RoleWireError(RuntimeError):
    pass


ROLE_COMMANDS={'assignment.publish','assignment.revoke','role.register','role.resume',
               'role.dispatch-current','role.readback-current','role.reconcile-failed-current','role.retire-failed','role.replace-retired','role.initialize-unpersisted','role.replace-interrupted'}

FAILED_PROOF_SHA='5327bf6b30b1e2dd70031bd4ccbbc870a449db77a3d945f68a43792bd64290d0'
RECONCILE_FIELDS=('epoch','revision','thread_id','turn_id','failure_sha256')


def failed_declarations(failure):
    groups=failure.get('actual_incremental_declarations') if type(failure) is dict else None
    need(type(groups) is list and bool(groups),'failed-tool-evidence-required')
    return {f"{d['name']}.{m}" for group in groups for d in group['definitions'] for m in d['members']}


def reconcile_failed_tool(store,auth,current_epoch,arguments,readback,failure):
    """Mark only the proved terminal turn and append its failed business verdict."""
    from owned_runtime import fields
    fields(arguments,RECONCILE_FIELDS)
    a=arguments;tid=canonical_uuid(a['thread_id']);turn=canonical_uuid(a['turn_id'])
    need(type(current_epoch) is str and bool(current_epoch) and a['epoch']==current_epoch
         and type(a['revision']) is int and a['revision']>0,'failed-reconcile-epoch-or-revision')
    need(type(failure) is dict and failure.get('thread_id')==tid and failure.get('turn_id')==turn
         and a['failure_sha256']==sha(wire(failure).encode()),'failed-tool-proof-pin')
    denied=failed_declarations(failure)
    need({'functions.exec','functions.wait'}<=denied and any(x.startswith('collaboration.') for x in denied)
         and failure.get('actual_fixed_read_calls')==0 and failure.get('broker_tool_operations')==0,
         'failed-tool-evidence-does-not-match')
    thread=readback.get('thread') if type(readback) is dict else None
    need(type(thread) is dict and thread.get('id')==tid and type(thread.get('turns')) is list,
         'failed-reconcile-thread-mismatch')
    matches=[t for t in thread['turns'] if type(t) is dict and t.get('id')==turn]
    need(len(matches)==1 and matches[0].get('status')=='completed' and not matches[0].get('error'),
         'failed-reconcile-native-turn-not-completed')
    with store.tx() as db:
        # Legacy RoleStore instances keep a binding descriptor under the same name.
        owner,task=Store.binding(store,db,auth,a['revision'],paused_ok=True)
        body=decode(task['body'])
        need(owner['paused'] and body['session_id']==tid and thread.get('cwd')==body['physical_cwd'],
             'failed-reconcile-owner-or-workspace')
        reg=db.execute('select * from role_registration where id=1').fetchone()
        row=db.execute('select * from turns where turn_id=?',(turn,)).fetchone()
        ds=db.execute('select * from native_dispatch where turn_id=?',(turn,)).fetchall()
        need(reg and reg['session_id']==tid and reg['generation']==owner['generation']
             and row and row['state']=='active' and row['revision']==a['revision']
             and row['owner_generation']==owner['generation'] and row['epoch']==task['capability_epoch']
             and len(ds)==1,'failed-reconcile-ledger-turn-mismatch')
        dispatch=ds[0]
        eff=db.execute('select * from effects where effect_id=?',(dispatch['effect_id'],)).fetchone()
        need(dispatch['session_id']==tid and dispatch['phase']=='observed'
             and dispatch['revision']==a['revision'] and dispatch['generation']==owner['generation']
             and dispatch['epoch']==task['capability_epoch'] and bool(dispatch['connection_id'])
             and eff and eff['kind']=='native-turn' and eff['issue_count']==1
             and eff['revision']==a['revision'] and eff['generation']==owner['generation']
             and db.execute('select 1 from native_tool_operations where turn_id=?',(turn,)).fetchone() is None,
             'failed-reconcile-dispatch-or-accepted-read-present')
        db.execute("update turns set state='terminal' where turn_id=? and state='active'",(turn,))
        seq=store.event(db,'single-role-readonly-task-business-failed',dict(thread_id=tid,turn_id=turn,
            revision=a['revision'],native_status='completed',business_accepted=False,epoch=current_epoch,
            failed_tool_proof_sha256=a['failure_sha256'],effect_id=dispatch['effect_id'],
            native_readback_sha256=sha(wire(readback).encode())))
    return dict(action='business-failed',thread_id=tid,turn_id=turn,revision=a['revision'],epoch=current_epoch,
                native_status='completed',business_accepted=False,event_sequence=seq)


class RoleInstallation(Installation):
    fixture_kind='fixture-single-role-stdio-only'
    fixture_worker_sha=FIXTURE_SHA

    def __init__(self,path,pin,role_path,role_pin):
        super().__init__(path,pin)
        rp=physical(role_path)
        need(rp==self.root/'single-role-installation.json','wrong-role-installation-path')
        raw=private_file(str(rp))
        need(sha(raw)==role_pin,'role-installation-pin-required')
        row=decode(raw)
        need(type(row) is dict and set(row)=={'schema','kind','owned_installation_sha256',
             'role','assignment_file','max_assignment_bytes','dynamic_tool'},'role-installation-fields')
        need(row['schema']=='lab-single-role-readonly-installation/1'
             and row['owned_installation_sha256']==pin
             and row['dynamic_tool']=={'name':TOOL['name'],'arguments':{}}
             and row['max_assignment_bytes']==4096
             and row['assignment_file']==str(self.cwd/'assignment.json'),'role-installation-mismatch')
        self.role=row['role']
        need(type(self.role) is str and re.fullmatch('one-[a-z][a-z0-9-]{0,48}',self.role),'invalid-owned-role')
        need((self.kind,row['kind']) in ((self.fixture_kind,'fixture-single-role-readonly'),
               ('official-single-role-stdio-only','official-single-role-readonly')),'single-role-kind-mismatch')
        self.is_native=self.kind=='official-single-role-stdio-only'
        if self.is_native:need(self.role=='one-readonly-lab','native-single-role-name-required')

    def check_child(self):
        if self.kind=='official-single-role-stdio-only':
            role_native.check_native(self)
        else:
            super().check_child()

    def child_environment(self):
        env=super().child_environment()
        if not self.is_native:return env
        ca=self.root/'tmp/native-witness-apple-ca.pem'
        private(ca,stat.S_ISREG)
        need(role_native.file_sha(ca)==role_native.APPLE_CA_SHA,'native-tls-root-pin-mismatch')
        return dict(env,SSL_CERT_FILE=str(ca))

    def serve_context(self):
        from contextlib import nullcontext
        return relay(MODEL) if self.is_native else nullcontext()


class TaskStore(OwnerStore):
    allowed_session=RoleStore.allowed_session

    def __init__(self,i):
        super().__init__(i,zero_task=False)
        self.installation=dict(role=i.role,physical_cwd=str(i.cwd))
        with self.tx() as db:
            for t in ('role_registration','native_dispatch','native_tool_operations','turns','effects'):
                db.execute('select 1 from '+t+' limit 1').fetchone()


class RoleChannel(Channel):
    def __init__(self,broker):
        self.broker=broker
        self.context=None
        self.pending=None
        super().__init__(broker.i)

    def initialize(self):
        _,result=self.rpc('initialize',{'clientInfo':{'name':'lab-single-role','version':'1'},
             'capabilities':{'experimentalApi':True}},self.i.timeout['startup_ms'])
        need(type(result) is dict,'invalid-role-initialize')
        if self.i.is_native:
            need(result.get('codexHome')==str(self.i.home/'.codex'),'native-role-home-mismatch')
        self.send({'method':'initialized'})

    def event(self,x):
        method=x.get('method')
        if method=='configWarning':
            raise ValueError('role-config-warning')
        if 'id' in x:
            need(method=='item/tool/call' and self.context is not None,'unbound-native-server-request')
            try:result=self.broker.read_tool(self,self.context,x)
            except (ValueError,TypeError,KeyError,OSError) as err:
                with self.broker.store.tx() as db:
                    self.broker.store.event(db,'native-tool-refused',
                                            dict(epoch=self.epoch,reason=type(err).__name__))
                raise RoleWireError('native-read-refused') from err
            self.send({'id':x['id'],'result':result})
        elif method=='turn/started' and self.pending is not None:
            self.broker.bind_started(self,self.pending,x['params'])

    def rpc(self,method,params,timeout):
        self.seq+=1
        rid=f'{self.epoch}:{self.seq}'
        self.send(dict(id=rid,method=method,params=params))
        deadline=time.monotonic()+timeout/1000
        for _ in range(256):
            x=self.read(deadline)
            if 'id' in x and 'method' not in x:
                need(x['id']==rid and 'result' in x and 'error' not in x and self.child.poll() is None,
                     'single-role-wire-response-mismatch')
                return rid,x['result']
            need(type(x.get('method')) is str,'unexpected-role-wire')
            self.event(x)
        raise ValueError('single-role-wire-event-bound')


class SingleRoleBroker(OwnedBroker):
    def __init__(self,i):
        super().__init__(i,store_factory=TaskStore,zero_task=False)
        self.attached=None
        self.first_launch=FirstLaunch()
        self.first_assignment=None

    def new_channel(self):
        if self.i.is_native:
            private(self.i.home/'.codex/auth.json',stat.S_ISREG)  # existence/mode only
        self.attached=None
        self.first_launch=FirstLaunch()
        self.first_assignment=None
        return RoleChannel(self)

    def handle(self,request):
        command=request.get('command')
        if command not in ROLE_COMMANDS:
            return super().handle(request)
        from owned_runtime import fields
        fields(request,('command','auth','arguments'))
        auth,args=request['auth'],request['arguments']
        fields(auth,('client','generation','token'))
        with self.execution_gate:
            with self.store.tx() as db:
                owner=self.store.owner(db,auth)
                current=self.store.current(db)
                registration=db.execute('select * from role_registration where id=1').fetchone()
                if command=='assignment.publish':
                    fields(args,('assignment',))
                    body=args['assignment']
                    assignment(body)
                    need(registration and registration['phase'] in ('provisional','stable')
                         and registration['session_id']==body['session_id']
                         and registration['generation']==owner['generation']
                         and body['role']==self.i.role and body['physical_cwd']==str(self.i.cwd)
                         and (not current or (owner['paused'] and current['phase']=='fenced')),
                         'role-assignment-binding-required')
                if command=='assignment.revoke':
                    fields(args,())
                    need(owner['paused'] and current and current['phase']=='active','pause-before-role-fence')
                    need(db.execute("select 1 from native_dispatch where phase='unknown'").fetchone() is None
                         and db.execute("select 1 from turns where state!='terminal'").fetchone() is None,
                         'unfinished-role-turn-cannot-be-fenced')
                identity=(owner['client'],owner['generation'],owner['token_sha'])
            if command=='assignment.publish':
                result=self.store.publish(auth,body)
                if self.i.is_native and self.first_launch.grant is not None and self.first_assignment is None:
                    tid,_,channel,epoch,owner=self.first_launch.grant
                    if channel is self.channel and self.attached==(epoch,tid) and owner==identity and tid==body['session_id']:
                        self.first_assignment=(tid,body['revision'],sha(wire(body).encode()))
                return result
            if command=='assignment.revoke':
                return self.store.revoke(auth)
            ch=self.channel
            if command=='role.reconcile-failed-current':
                fields(args,RECONCILE_FIELDS)
            elif command in ('role.retire-failed','role.replace-retired'):
                fields(args,('epoch','old_thread_id'))
            elif command=='role.replace-interrupted':
                fields(args,('epoch',*role_interrupted.ARGUMENTS))
            elif command=='role.initialize-unpersisted':
                fields(args,('epoch','previous_thread_id'))
            else:
                fields(args,('epoch',) if command in ('role.register','role.resume') else ('epoch','revision'))
            need(ch and args['epoch']==ch.epoch and self.state=='running'
                 and identity==self.epoch_owner and not ch.closed(),'stale-role-connection')
            try:
                if command=='role.replace-interrupted':
                    need(self.i.is_native,'official-native-interrupted-replacement-only')
                    return self.replace_interrupted(ch,auth,identity,{k:args[k] for k in role_interrupted.ARGUMENTS})
                if command=='role.initialize-unpersisted':
                    need(self.i.is_native,'official-native-initialization-only')
                    return self.reinitialize(ch,auth,identity,args['previous_thread_id'])
                if command in ('role.retire-failed','role.replace-retired'):
                    need(self.i.is_native,'one-native-role-replacement-only')
                    if command=='role.retire-failed':
                        result=retire_failed_role(self.store,auth,args['old_thread_id'])
                        self.attached=None
                        ch.context=None
                        return result
                    return self.replace_role(ch,auth,args['old_thread_id'])
                if command=='role.reconcile-failed-current':
                    return self.reconcile(ch,auth,args)
                if command=='role.register':
                    return self.register_role(ch,auth)
                if command=='role.resume':
                    return self.resume_role(ch,auth,registration)
                if command=='role.dispatch-current':
                    return self.dispatch(ch,auth,args['revision'])
                return self.readback(ch,auth,args['revision'])
            except (RoleWireError,EOFError,OSError,subprocess.SubprocessError):
                self.state='faulted'
                ch.stop()
                raise

    def first_receipt(self,ch,identity,tid,receipt):
        need(ch is self.channel and not ch.closed() and receipt==f'{ch.epoch}:{ch.seq}'
             and identity==self.epoch_owner,'unmatched-initialization-receipt')
        self.first_launch.issue(thread_id=tid,receipt_id=receipt,connection=ch,epoch=ch.epoch,owner=identity)

    def replace_interrupted(self,ch,auth,identity,arguments):
        old=canonical_uuid(arguments['thread_id'])
        raw,_=role_native.persisted_declarations(self.i,old)
        role_interrupted.prepare_replacement(self.store,auth,arguments,raw,ch.epoch)
        self.attached=None
        ch.context=None
        ch.pending=None
        receipt,result=ch.rpc('thread/start',dict(cwd=str(self.i.cwd),approvalPolicy='never',
                    permissions=':read-only',ephemeral=False,environments=[],dynamicTools=[TOOL],
                    config={'features.shell_tool':False}),self.i.timeout['startup_ms'])
        tid=self.thread(result)['id']
        need(ch is self.channel and not ch.closed() and receipt==f'{ch.epoch}:{ch.seq}'
             and identity==self.epoch_owner,'unmatched-interrupted-replacement-receipt')
        value=role_interrupted.bind_replacement(self.store,auth,arguments,ch.epoch,tid,receipt)
        self.attached=(ch.epoch,tid)
        self.first_receipt(ch,identity,tid,receipt)
        return dict(value,action='initialized-provisional')

    def reinitialize(self,ch,auth,identity,previous):
        role_initialization.prepare(self.store,auth,previous,ch.epoch,
                                    lambda tid,epoch:role_native.require_unmaterialized(self.i,tid,epoch))
        self.attached=None
        ch.context=None
        ch.pending=None
        receipt,result=ch.rpc('thread/start',dict(cwd=str(self.i.cwd),approvalPolicy='never',
                    permissions=':read-only',ephemeral=False,environments=[],dynamicTools=[TOOL],
                    config={'features.shell_tool':False}),self.i.timeout['startup_ms'])
        tid=self.thread(result)['id']
        value=role_initialization.bind(self.store,auth,previous,ch.epoch,tid,receipt)
        self.attached=(ch.epoch,tid)
        self.first_receipt(ch,identity,tid,receipt)
        return value

    def replace_role(self,ch,auth,old):
        # Existing role and task lose authority in the same committed cut as
        # consuming the only thread/start. No resume/fork or caller history.
        retire_and_reserve(self.store,auth,old,ch.epoch)
        self.attached=None
        ch.context=None
        ch.pending=None
        receipt,result=ch.rpc('thread/start',dict(cwd=str(self.i.cwd),approvalPolicy='never',
                    permissions=':read-only',ephemeral=False,environments=[],dynamicTools=[TOOL],
                    config={'features.shell_tool':False}),self.i.timeout['startup_ms'])
        tid=self.thread(result)['id']
        value=bind_replacement(self.store,auth,old,ch.epoch,tid)
        self.attached=(ch.epoch,tid)
        self.first_receipt(ch,self.epoch_owner,tid,receipt)
        return value

    def reconcile(self,ch,auth,args):
        need(self.i.is_native,'native-reconciliation-only')
        with self.store.tx() as db:
            owner,task=self.store.binding(db,auth,args['revision'],paused_ok=True)
            body=decode(task['body'])
            need(owner['paused'] and body['session_id']==args['thread_id']
                 and db.execute("select 1 from turns where turn_id=? and state='active'",(args['turn_id'],)).fetchone(),
                 'exact-paused-failed-turn-required')
        proof=private_file(str(self.i.root/'runtime/failed-tool-proof.json'),16384)
        need(sha(proof)==FAILED_PROOF_SHA,'reviewed-native-failed-tool-artifact-drift')
        failure=decode(proof)
        need(failure.get('turn_id')==args['turn_id'] and sha(wire(failure).encode())==args['failure_sha256'],
             'exact-failed-artifact-arguments')
        _,items=role_native.persisted_declarations(self.i,args['thread_id'])
        unsafe=False
        for item in items:
            try:role_native.request_tool_inventory(dict(input=[item]))
            except ValueError:unsafe=True
        need(unsafe,'native-persisted-tool-failure-missing')
        _,readback=ch.rpc('thread/read',dict(threadId=args['thread_id'],includeTurns=True),self.i.timeout['request_ms'])
        result=reconcile_failed_tool(self.store,auth,ch.epoch,args,readback,failure)
        return dict(result,same_thread_dispatch_hold='persisted-unsafe-tool-declaration')

    def thread(self,result,thread_id=None):
        row=result['thread'];n=canonical_uuid(row['id'])
        need((thread_id is None or n==thread_id) and row.get('cwd')==str(self.i.cwd)
             and result.get('cwd',str(self.i.cwd))==str(self.i.cwd),'role-native-workspace-mismatch')
        return row

    def register_role(self,ch,auth):
        effect=uuid.uuid4().hex
        with self.store.tx() as db:
            owner=self.store.owner(db,auth)
            need(not owner['paused'] and db.execute('select 1 from role_registration').fetchone() is None,
                 'role-register-not-permitted')
            db.execute('insert into role_registration values(1,?,?,?,?,null,null)',
                       (effect,'pending-unknown',owner['generation'],None))
            self.store.event(db,'role-registration-consumed-before-native-request',dict(effect_id=effect,epoch=ch.epoch))
        receipt,result=ch.rpc('thread/start',dict(cwd=str(self.i.cwd),approvalPolicy='never',
                   permissions=':read-only',ephemeral=False,environments=[],dynamicTools=[TOOL],
                   config={'features.shell_tool':False}),self.i.timeout['startup_ms'])
        tid=self.thread(result)['id']
        with self.store.tx() as db:
            owner=self.store.owner(db,auth)
            need(not owner['paused'],'role-register-revoked')
            db.execute("update role_registration set phase='provisional',session_id=? where id=1 and effect_id=?",
                       (tid,effect))
            self.store.event(db,'role-provisional-not-stable',dict(session_id=tid,epoch=ch.epoch))
        self.attached=(ch.epoch,tid)
        if self.i.is_native:self.first_receipt(ch,self.epoch_owner,tid,receipt)
        return dict(action='registered',epoch=ch.epoch,thread_id=tid)

    def resume_role(self,ch,auth,row):
        need(row and row['phase'] in ('provisional','stable') and row['session_id'],'role-id-not-durable')
        with self.store.tx() as db:
            owner=self.store.owner(db,auth)
            need(not owner['paused'] and owner['generation']==row['generation'],'role-resume-not-authorized')
            need(db.execute("select 1 from native_dispatch where phase='unknown'").fetchone() is None,
                 'unknown-dispatch-no-resume')
        tid=row['session_id']
        if self.i.is_native:
            role_native.require_clean_resume(self.i,tid)
            require_resume(phase=row['phase'],persisted=True)
        if self.attached!=(ch.epoch,tid):
            _,x=ch.rpc('thread/resume',dict(threadId=tid,cwd=str(self.i.cwd),approvalPolicy='never',
                          permissions=':read-only',environments=[],excludeTurns=True,
                          config={'features.shell_tool':False}),self.i.timeout['startup_ms'])
            self.thread(x,tid)
            self.attached=(ch.epoch,tid)
            with self.store.tx() as db:
                self.store.owner(db,auth)
                self.store.event(db,'same-stable-role-resume-response-observed',dict(session_id=tid,epoch=ch.epoch))
        return dict(action='resumed',epoch=ch.epoch,thread_id=tid)

    def current(self,db,auth,revision,ch,paused_ok=False):
        owner,task=self.store.binding(db,auth,revision,paused_ok=paused_ok)
        body=decode(task['body'])
        reg=db.execute('select * from role_registration where id=1').fetchone()
        need(reg and reg['generation']==owner['generation'] and reg['session_id']==body['session_id']
             and self.attached==(ch.epoch,body['session_id']) and body['physical_cwd']==str(self.i.cwd)
             and body['role']==self.i.role,'native-assignment-not-attached')
        return owner,task,body

    def dispatch(self,ch,auth,revision):
        prompt=('Call lab_read_assignment with empty arguments once. '
                'Return only a JSON object with revision (integer) and sha256 (string). Do not call other tools.')
        effect=uuid.uuid4().hex
        with self.store.tx() as db:
            owner,task,body=self.current(db,auth,revision,ch)
            tid=body['session_id']
            key=f'native-turn:{tid}:{revision}'
            need(db.execute('select 1 from effects where operation_key=?',(key,)).fetchone() is None,
                 'dispatch-already-consumed-readback-only')
            need(db.execute("select 1 from native_dispatch where phase='unknown'").fetchone() is None
                 and db.execute("select 1 from turns where state!='terminal'").fetchone() is None,
                 'prior-native-turn-unresolved')
            if self.i.is_native:
                reg=db.execute('select phase from role_registration where id=1').fetchone()
                if reg['phase']=='provisional':
                    need(self.first_assignment==(tid,revision,sha(wire(body).encode()))
                         and ch is self.channel and not ch.closed(),'first-launch-current-assignment-required')
                    owner_id=(owner['client'],owner['generation'],owner['token_sha'])
                    self.first_launch.authorize(thread_id=tid,connection=ch,epoch=ch.epoch,owner=owner_id,
                         revision=revision,prior_dispatch=None,has_persisted=False)
                    self.store.event(db,'role-initialization-first-dispatch-authorized',
                         dict(thread_id=tid,epoch=ch.epoch,revision=revision,native_reply_id=self.first_launch.grant[1]))
                else:
                    need(reg['phase']=='stable','role-stable-or-live-first-receipt-required')
                    role_native.require_clean_resume(self.i,tid)
            target=wire(dict(session_id=tid,connection_id=ch.epoch,prompt_sha256=sha(prompt.encode())))
            db.execute('insert into effects(effect_id,revision,generation,operation_key,kind,target,phase,issue_count) values(?,?,?,?,?,?,?,1)',
                       (effect,revision,owner['generation'],key,'native-turn',target,'unknown'))
            db.execute('insert into native_dispatch(effect_id,session_id,revision,generation,epoch,prompt_sha256,phase,connection_id,turn_id) values(?,?,?,?,?,?,?,?,null)',
                       (effect,tid,revision,owner['generation'],task['capability_epoch'],sha(prompt.encode()),'unknown',ch.epoch))
            self.store.event(db,'current-dispatch-consumed-before-native-request',dict(effect_id=effect,revision=revision,epoch=ch.epoch))
        ctx=(auth,revision,tid,effect)
        ch.pending=ctx;ch.context=ctx
        try:
            _,x=ch.rpc('turn/start',dict(threadId=tid,cwd=str(self.i.cwd),environments=[],
                       input=[dict(type='text',text=prompt)]),self.i.timeout['request_ms'])
            turn=canonical_uuid(x['turn']['id'])
            self.bind_turn(ch,ctx,turn)
        finally:
            ch.pending=None
            ch.context=None
        return dict(action='dispatched',thread_id=tid,turn_id=turn,epoch=ch.epoch,revision=revision)

    def bind_started(self,ch,ctx,params):
        need(params.get('threadId')==ctx[2],'wrong-native-turn-thread')
        self.bind_turn(ch,ctx,canonical_uuid(params['turn']['id']))

    def bind_turn(self,ch,ctx,turn):
        auth,rev,tid,effect=ctx
        with self.store.tx() as db:
            owner,task,_=self.current(db,auth,rev,ch)
            row=db.execute('select * from native_dispatch where effect_id=?',(effect,)).fetchone()
            need(row and row['session_id']==tid and row['connection_id']==ch.epoch
                 and row['generation']==owner['generation'] and row['epoch']==task['capability_epoch']
                 and row['turn_id'] in (None,turn),'native-turn-binding-mismatch')
            if row['turn_id'] is None:
                db.execute('insert into turns values(?,?,?,?,?)',(turn,rev,owner['generation'],row['epoch'],'active'))
                db.execute("update native_dispatch set turn_id=?,phase='observed' where effect_id=?",(turn,effect))
                db.execute("update effects set phase='observed' where effect_id=?",(effect,))
                db.execute('update role_registration set first_turn_id=coalesce(first_turn_id,?) where id=1',(turn,))
                self.store.event(db,'current-actual-native-turn-bound',dict(turn_id=turn,thread_id=tid,epoch=ch.epoch))

    def read_tool(self,ch,ctx,request):
        auth,rev,tid,effect=ctx
        p=request.get('params',{})
        need(type(p) is dict and set(p)=={'threadId','turnId','callId','namespace','tool','arguments'}
             and p['threadId']==tid and p['namespace'] is None and p['tool']==TOOL['name']
             and p['arguments']=={} and type(p['callId']) is str and 0<len(p['callId'])<=256,
             'only-current-fixed-read-tool')
        turn=canonical_uuid(p['turnId']);call=p['callId']
        with self.store.tx() as db:
            owner,task,body=self.current(db,auth,rev,ch)
            dispatch=db.execute('select * from native_dispatch where effect_id=?',(effect,)).fetchone()
            turnrow=db.execute('select * from turns where turn_id=?',(turn,)).fetchone()
            need(dispatch and dispatch['connection_id']==ch.epoch and dispatch['turn_id']==turn
                 and dispatch['epoch']==task['capability_epoch'] and dispatch['generation']==owner['generation']
                 and turnrow and turnrow['state']=='active' and turnrow['revision']==rev
                 and db.execute('select 1 from native_tool_operations where turn_id=?',(turn,)).fetchone() is None,
                 'stale-or-replayed-role-read')
            auth_seq=self.store.event(db,'first-tool-authorized-before-execution',dict(session_id=tid,turn_id=turn,
                    call_id=call,epoch=ch.epoch,revision=rev,physical_cwd=str(self.i.cwd),tool=TOOL['name']))
            db.execute('insert into native_tool_operations values(?,?,?,?,?,?,?,?,?,?,?)',
                       (tid,turn,call,rev,owner['generation'],task['capability_epoch'],ch.epoch,'consumed',auth_seq,None,None))
        path=physical(str(self.i.cwd/'assignment.json'))
        if self.i.is_native:
            facts=role_native.inventory(self.i,tid,turn)
            with self.store.tx() as db:
                self.current(db,auth,rev,ch)
                self.store.event(db,'actual-native-tool-inventory-verified',dict(inferences=facts))
        raw=private_file(str(path),4096)
        read=decode(raw)
        need(read==body,'fixed-file-not-current-assignment')
        payload=dict(revision=rev,sha256=sha(raw),assignment=read)
        model_value={k:payload[k] for k in ('revision','sha256')} if self.i.is_native else payload
        result=dict(success=True,contentItems=[dict(type='inputText',text=wire(model_value))])
        with self.store.tx() as db:
            self.current(db,auth,rev,ch)
            seq=self.store.event(db,'first-tool-actual-read-completed',dict(turn_id=turn,call_id=call,path=str(path),sha256=payload['sha256'],revision=rev))
            db.execute("update native_tool_operations set phase='reply-consumed',execution_seq=?,result=? where session_id=? and turn_id=? and call_id=?",
                       (seq,wire(result),tid,turn,call))
        return result

    def readback(self,ch,auth,rev):
        with self.store.tx() as db:
            _,_,body=self.current(db,auth,rev,ch,paused_ok=True)
            tid=body['session_id']
            rows=db.execute('select * from native_dispatch where session_id=? and revision=?',(tid,rev)).fetchall()
            need(len(rows)==1 and rows[0]['turn_id'],'unique-observed-dispatch-required')
            d=dict(rows[0])
            ctx=(auth,rev,tid,d['effect_id'])
        ch.context=ctx
        try:
            _,result=ch.rpc('thread/read',dict(threadId=tid,includeTurns=True),self.i.timeout['request_ms'])
        finally:
            ch.context=None
        thread=self.thread(result,tid);turn=d['turn_id']
        found=[x for x in thread.get('turns',[]) if x.get('id')==turn and x.get('status')=='completed' and not x.get('error')]
        need(len(found)==1,'native-turn-not-terminal')
        items=found[0].get('items',[])
        need(all(x.get('type') in ('userMessage','agentMessage','reasoning','dynamicToolCall') for x in items),'unexpected-native-tool-observed')
        calls=[x for x in items if x.get('type')=='dynamicToolCall']
        need(len(calls)==1 and calls[0].get('tool')==TOOL['name'] and calls[0].get('status')=='completed'
             and calls[0].get('success') is True and calls[0].get('arguments')=={},'durable-tool-evidence-required')
        with self.store.tx() as db:
            owner,task,_=self.current(db,auth,rev,ch,paused_ok=True)
            op=db.execute("select * from native_tool_operations where turn_id=? and phase='reply-consumed'",(turn,)).fetchone()
            need(op and op['call_id']==calls[0]['id'] and op['generation']==owner['generation']
                 and op['epoch']==task['capability_epoch'] and op['authorization_seq']<op['execution_seq'],
                 'role-readback-fenced')
            payload=decode(decode(op['result'])['contentItems'][0]['text'])
            if self.i.is_native:
                facts=role_native.inventory(self.i,tid,turn)
                need(facts and all(x.get('type')!='dynamicToolCall' or x.get('tool')==TOOL['name'] for x in items),
                     'native-inventory-or-calls-unproved')
                texts=[x.get('text','') for x in items if x.get('type')=='agentMessage']
                parsed=[]
                for text in texts:
                    try:parsed.append(decode(text))
                    except (ValueError,TypeError):continue
                need(any(type(x) is dict and type(x.get('revision')) is int and x==payload for x in parsed),
                     'native-model-output-unproved')
                durable_sha=role_native.clean_durable_turn(self.i,tid,turn)
                require_stable(completed=True,persisted=bool(durable_sha))
                reg=db.execute('select phase,first_turn_id from role_registration where id=1').fetchone()
                if reg['phase']=='provisional':
                    need(reg['first_turn_id']==turn,'first-durable-turn-mismatch')
                    self.store.event(db,'role-stable-after-native-persistence',
                        dict(thread_id=tid,turn_id=turn,revision=rev,rollout_sha256=durable_sha,epoch=ch.epoch))
            db.execute("update turns set state='terminal' where turn_id=? and state='active'",(turn,))
            db.execute("update role_registration set phase='stable',readback_sha256=? where id=1",
                       (sha(wire(result).encode()),))
            self.store.event(db,'current-task-first-action-native-readback',dict(revision=rev,turn_id=turn,epoch=ch.epoch))
        return dict(action='observed',thread_id=tid,turn_id=turn,epoch=ch.epoch,revision=rev,read=payload)


def run(mode,path,pin,request,role_path,role_pin):
    return run_owned(mode,path,pin,request,
         installation_factory=lambda p,s:RoleInstallation(p,s,role_path,role_pin),
         broker_factory=SingleRoleBroker)
