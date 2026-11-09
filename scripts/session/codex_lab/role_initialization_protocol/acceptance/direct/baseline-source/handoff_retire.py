"""Terminal handoff retirement. No PID adoption, reply promotion or automatic recovery."""
import json
import os

from warm_handoff import HandoffStore, HandoffSupport
from native_transport import NativeProxy
from runtime_store import need, wire, digest

TERMINAL = {'completed', 'interrupted', 'failed'}


class RetirementStore(HandoffStore):
    def __init__(self, directory, installation):
        super().__init__(directory, installation)
        with self.tx() as db:
            pass  # Existing-only startup: schema was independently verified; no DDL.


class RetirementSupport(HandoffSupport):
    def __init__(self, cfg):
        super().__init__(cfg)
        self._retirement_observer = None

    def _selection(self, db, auth, args):
        need(type(args) is dict and set(args) == {'revision', 'wait_id'}, 'exact-handoff-retirement-selector')
        owner = self.store.owner(db, auth)
        need(owner['paused'], 'retirement-must-stay-durably-paused')
        row = db.execute('select * from native_handoff where wait_id=?', (args['wait_id'],)).fetchone()
        need(row and row['revision'] == args['revision'] and row['generation'] == owner['generation'],
             'original-owner-and-handoff-required')
        if row['phase'] == 'retired':
            audit = db.execute('select * from handoff_retirement where effect_id=?', (row['effect_id'],)).fetchone()
            need(audit, 'retired-audit-missing')
            return dict(row), None, None, json.loads(audit['evidence'])
        wait = db.execute('select * from waits where wait_id=?', (row['wait_id'],)).fetchone()
        payload = db.execute('select * from checkpoint_wait_payload where wait_id=?', (row['wait_id'],)).fetchone()
        task = self.store.current(db)
        need(task and task['revision'] == row['revision'] and task['phase'] == 'active'
             and task['capability_epoch'] == row['epoch'] and wait and payload
             and wait['session_id'] == self.session_id and wait['revision'] == row['revision']
             and wait['generation'] == owner['generation'] and wait['connection_id'] == row['old_connection']
             and wait['phase'] in ('pending','ready') and wait['completions'] == 0
             and payload['epoch'] == row['epoch']
             and db.execute('select 1 from native_tool_operations where turn_id=?',(wait['turn_id'],)).fetchone() is None,
             'original-unconsumed-unreplied-wait-required')
        need(row['old_exit_seq'] is not None and row['refresh_consumed'] == 1, 'old-owned-exit-and-fresh-subscribe-required')
        effect = db.execute('select * from effects where effect_id=?', (row['effect_id'],)).fetchone()
        need(effect and effect['kind'] == 'native-wait-owned-handoff' and effect['issue_count'] == 1,
             'consumed-original-handoff-effect-required')
        unknown = db.execute("select effect_id from effects where lower(phase) like '%unknown%' and effect_id!=?",
                             (row['effect_id'],)).fetchall()
        need(not unknown, 'another-unknown-effect-blocks-retirement')
        need(db.execute("select 1 from effects where phase in ('planned','issuing','inflight','sending')").fetchone() is None,
             'another-live-effect-blocks-retirement')
        need(db.execute("select 1 from waits where wait_id!=? and phase in ('pending','ready','delivery-unknown')",
                        (row['wait_id'],)).fetchone() is None, 'another-live-wait-blocks-retirement')
        need(db.execute("select 1 from turns where turn_id!=? and state!='terminal'", (wait['turn_id'],)).fetchone() is None,
             'another-nonterminal-turn-blocks-retirement')
        return dict(row), dict(wait), json.loads(payload['raw_request']), None

    def _history(self, db, row, original):
        old_exit = db.execute('select * from events where seq=?', (row['old_exit_seq'],)).fetchone()
        need(old_exit and old_exit['kind'] == 'owned-old-proxy-actual-wait-terminal', 'actual-owned-wait-event-required')
        exit_body = json.loads(old_exit['body'])
        need(exit_body.get('effect_id') == row['effect_id'] and type(exit_body.get('exit_code')) is int,
             'exact-owned-exit-binding-required')
        receipts = db.execute("select seq,body from events where kind='dual-observer-server-request-no-result-or-error' "
                             'order by seq desc limit 8193').fetchall()
        need(len(receipts) <= 8192, 'handoff-history-window-unproved')
        pre, post = [], []
        for record in receipts:
            body = json.loads(record['body'])
            if body.get('effect_id') != row['effect_id'] or body.get('connection_id') != row['new_connection']:
                continue
            need(body.get('response_sent') is False and body.get('reply_lease_moved') is False,
                 'observer-reply-attempt-blocks-retirement')
            if body.get('exact_original_request') and body.get('current_unconsumed_binding'):
                need(body.get('request') == original and type(body.get('raw_sha256')) is str,
                     'exact-original-replay-envelope-required')
                if body.get('stage') == 'pre-close-replay-observation' and record['seq'] < row['old_exit_seq']:
                    pre.append(record['seq'])
                if body.get('stage') == 'post-close-fresh-replay-observation' and record['seq'] > row['old_exit_seq']:
                    need(body.get('old_exit_seq') == row['old_exit_seq'], 'fresh-replay-old-exit-mismatch')
                    post.append(record['seq'])
        need(pre and post, 'actual-pre-and-post-owned-exit-replays-required')
        fence = db.execute('select * from native_reply_fence where connection_id=?', (row['old_connection'],)).fetchone()
        need(fence and fence['effect_id'] == row['effect_id'] and fence['seq'] < min(pre), 'old-reply-fence-required')
        return dict(old_exit_seq=row['old_exit_seq'], old_exit_code=exit_body['exit_code'],
                    pre_replay_sequences=pre, fresh_post_replay_sequences=post, old_fence_seq=fence['seq'])

    def _owned_no_reply_proof(self, row):
        context = self._handoff
        if not context or context['effect_id'] != row['effect_id']:
            return None  # Old handles lost on Broker stop; never reconstruct from PID/event text.
        old, new = context['old'], context.get('new')
        process = context['old_process']
        if not (new and process is old.process and old.creator_broker_pid == os.getpid()
                and new.creator_broker_pid == os.getpid() and old.reply_fenced and new.reply_fenced
                and old.connection_id == row['old_connection'] and new.connection_id == row['new_connection']):
            return None
        if process.poll() is None:
            return None
        code = process.wait(timeout=0)
        return dict(kind='same-broker-owned-channel-fence-observation', broker_pid=os.getpid(),
                    old_proxy_wait_exit=code, old_result_error_fenced=True, new_result_error_fenced=True,
                    old_connection_id=old.connection_id,new_connection_id=new.connection_id)

    def _terminal_observer(self, row):
        if self._handoff and self._handoff.get('new'):
            return self._handoff['new']
        if self._retirement_observer is None:
            connection = NativeProxy(self.binding,self.native_record,self.native_call)
            connection.reply_fenced = True
            connection.sessions.add(self.session_id)
            self._retirement_observer = connection
            connection.connect()  # Existing pinned proxy only, no subscription/resume.
        return self._retirement_observer

    def _inspect_retirement(self, auth, args, commit=False):
        with self.store.tx() as db:
            row, wait, original, retired = self._selection(db,auth,args)
            if retired:
                return dict(action='retired',retirement=retired,repeated_read_only=True)
            history = self._history(db,row,original)
        connection = self._terminal_observer(row)
        connection.check_endpoint()
        loaded_before = self._loaded_snapshot(connection)
        result = connection.rpc('thread/read',dict(threadId=self.session_id,includeTurns=True))
        thread = result['thread']
        need(thread.get('id') == self.session_id and thread.get('cwd') == self.cfg['physical_cwd'], 'exact-original-thread-readback')
        visible = thread.get('turns')
        need(type(visible) is list and all(t.get('status') in TERMINAL for t in visible), 'visible-live-native-turn-blocks-retirement')
        target = [t for t in visible if t.get('id') == wait['turn_id']]
        need(len(target) == 1 and target[0]['status'] in TERMINAL, 'actual-original-turn-terminal-required')
        loaded_after = self._loaded_snapshot(connection)
        status = thread.get('status',{}).get('type')
        need((status == 'idle' and self.session_id in loaded_before['data'] and self.session_id in loaded_after['data'])
             or (status == 'notLoaded' and self.session_id not in loaded_before['data'] and self.session_id not in loaded_after['data']),
             'no-live-thread-or-explicit-notloaded-proof-required')
        connection.check_endpoint()
        channels = self._owned_no_reply_proof(row)
        need(channels is None or channels['old_proxy_wait_exit'] == history['old_exit_code'],
             'owned-live-handle-and-recorded-terminal-disagree')
        evidence = dict(effect_id=row['effect_id'],wait_id=wait['wait_id'],revision=row['revision'],
            session_id=self.session_id,turn_id=wait['turn_id'],native_status=target[0]['status'],
            history=history,channel_proof=channels,thread_readback_semantic_sha256=digest(wire(result)),
            loaded_before_semantic_sha256=digest(wire(loaded_before)),loaded_after_semantic_sha256=digest(wire(loaded_after)),
            server_epoch_independently_proved=False,reply_cas=False,old_reply_sent=False)
        with self.store.tx() as db:
            self._selection(db,auth,args)
            self.store.event(db,'handoff-terminal-readback-retirement-check',evidence)
        if not commit or channels is None:
            return dict(action='hold',evidence=evidence,retired=False,
                        reason='explicit-retire-required' if channels else 'original-owned-channel-proof-unavailable-after-broker-stop')
        with self.store.tx() as db:
            current,current_wait,current_original,_=self._selection(db,auth,args)
            need(current==row and current_wait==wait and current_original==original
                 and self._history(db,row,original)==history and self._owned_no_reply_proof(row)==channels,
                 'retirement-evidence-moved')
            fenced=self.store._fence(db,'explicit-terminal-handoff-retirement')
            need(fenced and fenced['revision']==row['revision'],'retirement-must-fence-original-current-task')
            db.execute("update turns set state='terminal' where turn_id=?",(wait['turn_id'],))
            db.execute("update waits set phase='obsolete' where wait_id=?",(wait['wait_id'],))
            seq=self.store.event(db,'handoff-explicitly-retired-no-reply-and-original-wait-obsolete',evidence)
            db.execute('insert into handoff_retirement values(?,?,?)',(row['effect_id'],wire(dict(evidence,retirement_seq=seq)),seq))
            db.execute("update native_handoff set phase='retired' where effect_id=?",(row['effect_id'],))
            db.execute("update effects set phase='retired-observed',observation=? where effect_id=?",(wire(evidence),row['effect_id']))
        return dict(action='retired',retirement_seq=seq,fence=fenced,wait_completions=0,
                    broker_paused=True,next_paused_assignment_only=True,independent_acceptance=False)

    def close_handoff_observers(self):
        with self.execution_gate:
            if self._retirement_observer:
                self._retirement_observer.close()
            super().close_handoff_observers()

    def handle(self,request):
        command=request.get('command')
        with self.execution_gate:
            if command in ('wait.handoff-retire-readback','wait.handoff-retire'):
                return self._inspect_retirement(request.get('auth'),request.get('arguments',{}),
                                                commit=command=='wait.handoff-retire')
            with self.store.tx() as db:
                retired=db.execute("select 1 from native_handoff where phase='retired'").fetchone()
            if retired and command not in ('inspect','plan','assignment.publish','wait.handoff-status'):
                return dict(action='hold',reason='handoff-retired-only-next-paused-assignment-open')
            return super().handle(request)
