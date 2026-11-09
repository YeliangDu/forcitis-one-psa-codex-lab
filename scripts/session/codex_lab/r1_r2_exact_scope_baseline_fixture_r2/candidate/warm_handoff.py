"""Owned-handle dual-observer handoff. No reply CAS without server epoch proof."""
import json
import os
import secrets
import subprocess

from broker_plan import PlanStore, PlanSupport
from native_transport import NativeProxy
from pending_wire import original_pending_wire
from runtime_store import need, wire, digest


class HandoffStore(PlanStore):
    def __init__(self, directory, installation):
        super().__init__(directory, installation)
        with self.tx() as db:
            db.execute('''create table if not exists native_handoff(
              effect_id text primary key, wait_id text unique not null, revision integer not null,
              generation integer not null, epoch integer not null, old_connection text not null,
              new_connection text, phase text not null, old_exit_seq integer, refresh_consumed integer not null)''')
            db.execute('''create table if not exists native_reply_fence(
              connection_id text primary key, effect_id text not null, seq integer not null)''')


class HandoffSupport(PlanSupport):
    def __init__(self, cfg):
        super().__init__(cfg)
        self._handoff = None  # NEVER reconstruct original owned handles from PIDs.
        self._handoff_wire = {}

    def native_record(self, value):
        if self._handoff and 'raw_utf8' in value:
            import hashlib
            raw = value['raw_utf8']
            need(hashlib.sha256(raw.encode()).hexdigest() == value['raw_sha256'], 'handoff-wire-integrity')
            self._handoff_wire[value['connection_id']] = (json.loads(raw), value['raw_sha256'])
        return super().native_record(value)

    def native_call(self, connection, request):
        context = self._handoff
        if context and connection in (context['old'], context.get('new')):
            latest = self._handoff_wire.get(connection.connection_id)
            exact = bool(latest and latest[0] == request and request == context['original'])
            with self.store.tx() as db:
                row = db.execute('select * from native_handoff where effect_id=?', (context['effect_id'],)).fetchone()
                owner = db.execute('select * from owner where id=1').fetchone()
                task = self.store.current(db)
                wait = db.execute('select * from waits where wait_id=?', (context['wait_id'],)).fetchone()
                current = bool(row and owner['client'] is not None and owner['paused']
                    and owner['generation'] == row['generation'] and task and task['phase'] == 'active'
                    and task['revision'] == row['revision'] and task['capability_epoch'] == row['epoch']
                    and wait and wait['phase'] in ('pending','ready') and wait['completions'] == 0
                    and wait['connection_id'] == row['old_connection'])
                self.store.event(db, 'dual-observer-server-request-no-result-or-error',
                    dict(effect_id=context['effect_id'], connection_id=connection.connection_id,
                         stage=context['stage'], old_exit_seq=context.get('old_exit_seq'),
                         exact_original_request=exact, current_unconsumed_binding=current, raw_sha256=latest[1] if latest else None,
                         request=request, response_sent=False, reply_lease_moved=False))
            return None
        # A persisted unfinished handoff never grants a post-crash new channel
        # permissions just because the original in-memory handles were lost.
        with self.store.tx() as db:
            active = db.execute("select 1 from native_handoff where phase!='retired'").fetchone()
        if active:
            self.store.archive(dict(request=request, disposition='handoff-without-owned-handle-no-reply'))
            return None
        return super().native_call(connection, request)

    def _wait(self, db, auth, args):
        need(type(args) is dict and set(args) == {'revision', 'wait_id'}, 'exact-current-handoff-selector')
        owner, task = self.store.binding(db, auth, args['revision'], paused_ok=True)
        need(owner['paused'], 'handoff-requires-durable-pause')
        wait = db.execute('select * from waits where wait_id=?', (args['wait_id'],)).fetchone()
        payload = db.execute('select * from checkpoint_wait_payload where wait_id=?', (args['wait_id'],)).fetchone()
        need(wait and payload and wait['session_id'] == self.session_id and wait['revision'] == task['revision']
             and wait['generation'] == owner['generation'] and wait['phase'] in ('pending','ready')
             and wait['completions'] == 0 and payload['epoch'] == task['capability_epoch']
             and db.execute('select 1 from native_tool_operations where turn_id=?', (wait['turn_id'],)).fetchone() is None,
             'genuine-current-unconsumed-wait-required')
        original = json.loads(payload['raw_request'])
        need(original.get('method') == 'item/tool/call' and 'id' in original
             and json.loads(payload['rpc_id']) == original['id']
             and original.get('params') == dict(threadId=self.session_id, turnId=wait['turn_id'],
                 callId=wait['call_id'], namespace=None, tool='lab_read_assignment', arguments={}),
             'exact-original-native-call-required')
        return dict(owner), dict(task), dict(wait), dict(payload), original

    def _native_preflight(self, connection, wait):
        connection.check_endpoint()
        loaded = connection.rpc('thread/loaded/list', {})
        need(self.session_id in loaded.get('data', []), 'must-remain-warm-loaded-no-cold-resume')
        goal = connection.rpc('thread/goal/get', dict(threadId=self.session_id)).get('goal')
        need(type(goal) is dict and goal.get('threadId') == self.session_id and goal.get('status') == 'paused',
             'native-goal-must-already-be-paused-no-mutation')
        result = connection.rpc('thread/read', dict(threadId=self.session_id, includeTurns=True))
        thread = result['thread']
        turns = [t for t in thread.get('turns', []) if t.get('id') == wait['turn_id']]
        need(thread.get('id') == self.session_id and thread.get('cwd') == self.cfg['physical_cwd']
             and len(turns) == 1 and turns[0].get('status') == 'inProgress', 'actual-current-turn-not-running')

    def _begin(self, auth, args):
        self.store.pause(auth, True)
        old = self.native
        need(old is not None and old.process is not None and old.creator_broker_pid == os.getpid()
             and old.process.poll() is None, 'current-broker-owned-live-proxy-handle-required')
        with self.store.tx() as db:
            owner, task, wait, payload, original = self._wait(db, auth, args)
            need(old.connection_id == wait['connection_id']
                 and db.execute("select 1 from native_handoff where phase!='retired'").fetchone() is None,
                 'one-originating-owned-handoff-only')
            original_pending_wire(db, wait, payload, old.connection_id)
            effect_id = secrets.token_hex(24)
            key = 'native-wait-owned-handoff:' + wait['wait_id'] + ':' + old.connection_id
            # Live outbox fence is set before any observer connection or query.
            old.reply_fenced = True
            self.store._index_effect(db, effect_id, wait['revision'], owner['generation'], key,
                'native-wait-owned-handoff', dict(wait_id=wait['wait_id'], old_connection_id=old.connection_id), 'unknown')
            seq = self.store.event(db, 'handoff-old-result-and-error-rights-fenced',
                dict(effect_id=effect_id, wait_id=wait['wait_id'], connection_id=old.connection_id,
                     owned_proxy_pid=old.process.pid, creator_broker_pid=os.getpid(),
                     original_request_semantic_sha256=digest(wire(original))))
            db.execute('insert into native_reply_fence values(?,?,?)', (old.connection_id,effect_id,seq))
            db.execute('insert into native_handoff values(?,?,?,?,?,?,null,?,null,0)',
                (effect_id,wait['wait_id'],wait['revision'],owner['generation'],task['capability_epoch'],old.connection_id,'old-fenced'))
        self._handoff = dict(effect_id=effect_id, old=old, old_process=old.process, new=None,
                             original=original, stage='old-fenced', wait_id=wait['wait_id'])
        self._native_preflight(old, wait)
        new = NativeProxy(self.binding, self.native_record, self.native_call)
        new.reply_fenced = True
        new.role_key = self._role_key
        new.sessions.add(self.session_id)
        self._handoff['new'] = new  # Observer guard installed before initialize.
        new.connect()
        self._native_preflight(new, wait)
        with self.store.tx() as db:
            self._wait(db, auth, args)
            db.execute("update native_handoff set new_connection=?,phase='subscribe-consumed' where effect_id=?",
                       (new.connection_id,effect_id))
            self.store.event(db,'handoff-new-warm-subscribe-consumed',dict(effect_id=effect_id,new_connection_id=new.connection_id))
        self._handoff['stage']='pre-close-await-response'
        response = new.rpc('thread/resume',dict(threadId=self.session_id,excludeTurns=True),role_key=self._role_key)
        self._check_resume(response)
        self._handoff['stage']='pre-close-replay-observation'
        with self.store.tx() as db:
            self.store.event(db,'handoff-new-subscriber-response-no-reply-right',dict(effect_id=effect_id))
            db.execute("update native_handoff set phase='dual-observing' where effect_id=?",(effect_id,))
        return dict(action='observing',effect_id=effect_id,old_reply_fenced=True,new_reply_fenced=True,rebind=False)

    def _check_resume(self, response):
        need(response['thread']['id']==self.session_id and response['thread'].get('cwd')==self.cfg['physical_cwd']
             and response.get('cwd')==self.cfg['physical_cwd'] and response.get('approvalPolicy')=='never'
             and (response.get('activePermissionProfile') or {}).get('id')==self.permission_profile_id(),'warm-subscription-binding-mismatch')

    def _context(self, db, auth, args):
        self._wait(db,auth,args)
        context=self._handoff
        need(context and context['old'].creator_broker_pid==os.getpid(),'original-owned-handles-not-recoverable-from-pid')
        row=db.execute('select * from native_handoff where effect_id=?',(context['effect_id'],)).fetchone()
        need(row and row['wait_id']==args['wait_id'],'exact-live-handoff-required')
        return context,dict(row)

    def _close_old(self,auth,args):
        with self.store.tx() as db:
            context,row=self._context(db,auth,args)
            need(row['phase']=='dual-observing','dual-subscription-observation-required')
            evidence=db.execute("select body from events where kind='dual-observer-server-request-no-result-or-error'").fetchall()
            need(any((lambda b:b.get('effect_id')==row['effect_id'] and b.get('connection_id')==row['new_connection']
                 and b.get('stage')=='pre-close-replay-observation' and b.get('exact_original_request')
                 and b.get('current_unconsumed_binding'))(json.loads(e['body']))
                 for e in evidence),'actual-new-connection-replay-required-before-old-close')
            db.execute("update native_handoff set phase='old-close-consumed' where effect_id=?",(row['effect_id'],))
            self.store.event(db,'owned-old-proxy-close-consumed',dict(effect_id=row['effect_id']))
        process=context['old_process']
        need(process is context['old'].process and context['old'].reply_fenced,'same-owned-fenced-handle-required')
        if process.stdin:
            process.stdin.close()
        try:
            code=process.wait(timeout=3)
        except subprocess.TimeoutExpired:
            return dict(action='hold',reason='owned-old-proxy-terminal-unproved-no-kill-or-retry',rebind=False)
        need(process.poll()==code,'owned-proxy-wait-result-inconsistent')
        with self.store.tx() as db:
            self._context(db,auth,args)
            seq=self.store.event(db,'owned-old-proxy-actual-wait-terminal',
                dict(effect_id=row['effect_id'],pid=process.pid,exit_code=code,broker_pid=os.getpid()))
            db.execute("update native_handoff set phase='old-terminal',old_exit_seq=? where effect_id=?",(seq,row['effect_id']))
        context['old_exit_seq']=seq
        context['stage']='old-terminal-await-fresh-subscribe'
        return dict(action='old-proxy-terminal',exit_code=code,old_exit_seq=seq,rebind=False)

    def _read_old_terminal(self, auth, args):
        with self.store.tx() as db:
            context, row = self._context(db, auth, args)
            need(row['phase'] in ('old-close-consumed','old-terminal'), 'old-close-must-already-be-consumed')
        process = context['old_process']
        need(process is context['old'].process and context['old'].reply_fenced, 'actual-owned-proxy-handle-required')
        if process.poll() is None:
            return dict(action='hold', reason='owned-old-proxy-still-running', rebind=False)
        code = process.wait(timeout=0)  # Actual original Popen wait; no close resend.
        with self.store.tx() as db:
            self._context(db, auth, args)
            if row['old_exit_seq'] is None:
                seq = self.store.event(db, 'owned-old-proxy-actual-wait-terminal',
                    dict(effect_id=row['effect_id'], pid=process.pid, exit_code=code, broker_pid=os.getpid()))
                db.execute("update native_handoff set phase='old-terminal',old_exit_seq=? where effect_id=?", (seq,row['effect_id']))
            else:
                seq = row['old_exit_seq']
        context['old_exit_seq'] = seq
        context['stage'] = 'old-terminal-await-fresh-subscribe'
        return dict(action='old-proxy-terminal', exit_code=code, old_exit_seq=seq, rebind=False)

    def _refresh(self,auth,args):
        with self.store.tx() as db:
            context,row=self._context(db,auth,args)
            need(row['phase']=='old-terminal' and row['old_exit_seq'] is not None and row['refresh_consumed']==0,
                 'one-fresh-subscribe-after-actual-old-terminal-only')
            need(context['old_process'].poll() is not None,'old-handle-terminal-required')
            db.execute('update native_handoff set refresh_consumed=1 where effect_id=?',(row['effect_id'],))
            self.store.event(db,'post-old-terminal-subscribe-consumed',dict(effect_id=row['effect_id'],old_exit_seq=row['old_exit_seq']))
        new=context['new'];need(new is not None and new.reply_fenced,'existing-new-observer-required')
        context['stage']='post-close-await-response'
        response=new.rpc('thread/resume',dict(threadId=self.session_id,excludeTurns=True),role_key=self._role_key)
        self._check_resume(response)
        # Aligned warm-resume source sends replay AFTER its response; frames
        # already queued before that response were processed in the prior stage.
        context['stage']='post-close-fresh-replay-observation'
        with self.store.tx() as db:
            self._context(db,auth,args)
            db.execute("update native_handoff set phase='fresh-observing' where effect_id=?",(row['effect_id'],))
            self.store.event(db,'post-old-terminal-resume-response-awaiting-fresh-replay',dict(effect_id=row['effect_id']))
        return dict(action='observing',stage=context['stage'],rebind=False,server_epoch_proved=False)

    def close_handoff_observers(self):
        with self.execution_gate:
            if self._handoff and self._handoff.get('new'):
                self._handoff['new'].close()  # Only owned client/proxy, never host.

    def handle(self,request):
        command=request.get('command')
        with self.execution_gate:
            auth,args=request.get('auth'),request.get('arguments',{})
            if command=='wait.handoff-begin':return self._begin(auth,args)
            with self.store.tx() as db:
                active=db.execute("select * from native_handoff where phase!='retired'").fetchone()
            if active:
                if command=='wait.handoff-status':
                    with self.store.tx() as db:
                        self.store.owner(db,auth)
                        rows=[dict(r) for r in db.execute('select * from native_handoff')]
                    return dict(action='hold',handoffs=rows,rebind=False,server_epoch_proved=False,
                                reason='dual-observation-only-no-reply-cas')
                if command=='wait.handoff-close-old':return self._close_old(auth,args)
                if command=='wait.handoff-refresh':return self._refresh(auth,args)
                if command=='wait.handoff-read-old':return self._read_old_terminal(auth,args)
                if command in ('wait.handoff-poll-old','wait.handoff-poll-new'):
                    with self.store.tx() as db:context,_=self._context(db,auth,args)
                    channel=context['old'] if command.endswith('old') else context['new']
                    need(channel is not None and channel.reply_fenced,'fenced-owned-observer-required')
                    return channel.poll()
                if command not in ('inspect','notification.stage','wait.verify','plan'):
                    return dict(action='hold',reason='handoff-reply-fence-requires-independent-recovery',rebind=False)
            return super().handle(request)
