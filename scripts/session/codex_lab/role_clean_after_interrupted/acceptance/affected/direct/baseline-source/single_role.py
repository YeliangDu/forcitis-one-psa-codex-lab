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
from runtime_store import need, wire
from role_network import MODEL, relay
import role_native

FIXTURE_SHA='86ed5477d7afd5e85bd01d7fafb76c187a7b20b5691563990a835e07f7e44702'
class RoleWireError(RuntimeError):
    pass


ROLE_COMMANDS={'assignment.publish','assignment.revoke','role.register','role.resume',
               'role.dispatch-current','role.readback-current'}


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

    def new_channel(self):
        if self.i.is_native:
            private(self.i.home/'.codex/auth.json',stat.S_ISREG)  # existence/mode only
        self.attached=None
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
                    need(registration and registration['session_id']==body['session_id']
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
                return self.store.publish(auth,body)
            if command=='assignment.revoke':
                return self.store.revoke(auth)
            ch=self.channel
            fields(args,('epoch',) if command in ('role.register','role.resume') else ('epoch','revision'))
            need(ch and args['epoch']==ch.epoch and self.state=='running'
                 and identity==self.epoch_owner and not ch.closed(),'stale-role-connection')
            try:
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
        _,result=ch.rpc('thread/start',dict(cwd=str(self.i.cwd),approvalPolicy='never',
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
        return dict(action='registered',epoch=ch.epoch,thread_id=tid)

    def resume_role(self,ch,auth,row):
        need(row and row['phase'] in ('provisional','stable') and row['session_id'],'role-id-not-durable')
        with self.store.tx() as db:
            owner=self.store.owner(db,auth)
            need(not owner['paused'] and owner['generation']==row['generation'],'role-resume-not-authorized')
            need(db.execute("select 1 from native_dispatch where phase='unknown'").fetchone() is None,
                 'unknown-dispatch-no-resume')
        tid=row['session_id']
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
            db.execute("update turns set state='terminal' where turn_id=? and state='active'",(turn,))
            db.execute("update role_registration set phase='stable',readback_sha256=? where id=1",
                       (sha(wire(result).encode()),))
            self.store.event(db,'current-task-first-action-native-readback',dict(revision=rev,turn_id=turn,epoch=ch.epoch))
        return dict(action='observed',thread_id=tid,turn_id=turn,epoch=ch.epoch,revision=rev,read=payload)


def run(mode,path,pin,request,role_path,role_pin):
    return run_owned(mode,path,pin,request,
         installation_factory=lambda p,s:RoleInstallation(p,s,role_path,role_pin),
         broker_factory=SingleRoleBroker)
