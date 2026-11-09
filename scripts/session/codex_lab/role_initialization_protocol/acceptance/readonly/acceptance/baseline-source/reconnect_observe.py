"""One exact warm reconnect observation. Never CAS rebind or answer pending calls."""
import hashlib
import json
from pathlib import Path
import secrets

from native_goal import GoalStore, GoalSupport
from runtime_store import need, wire, digest


class ObserveStore(GoalStore):
    pass  # Existing effects/events only; no wait/schema/installation reset.


class ObserveSupport(GoalSupport):
    def __init__(self, cfg):
        super().__init__(cfg)
        self._replay_observation = None
        self._latest_observation_wire = None

    def native_record(self, value):
        # Called only by actual NativeProxy; public CLI cannot insert this kind.
        if self._replay_observation and 'raw_utf8' in value:
            raw = value['raw_utf8']
            need(hashlib.sha256(raw.encode('utf-8')).hexdigest() == value['raw_sha256'],
                 'actual-observation-wire-digest-mismatch')
            self._latest_observation_wire = (value['connection_id'], json.loads(raw), value['raw_sha256'])
        return super().native_record(value)

    def native_call(self, connection, request):
        context = self._replay_observation
        if context and connection is self.native and connection.connection_id == context['new_connection_id']:
            # ALL server requests on this new observer channel are no-reply.
            # A matched callback is observation, never a new execution lease.
            latest = self._latest_observation_wire
            actual_wire = (latest is not None and latest[0] == connection.connection_id and latest[1] == request)
            exact = actual_wire and request == context['original_request']
            with self.store.tx() as db:
                owner = db.execute('select * from owner where id=1').fetchone()
                task = self.store.current(db)
                wait = db.execute('select * from waits where wait_id=?', (context['wait_id'],)).fetchone()
                payload = db.execute('select * from checkpoint_wait_payload where wait_id=?', (context['wait_id'],)).fetchone()
                current = bool(owner['client'] is not None and owner['paused']
                    and owner['generation'] == context['generation'] and task and task['phase'] == 'active'
                    and task['revision'] == context['revision'] and task['capability_epoch'] == context['epoch']
                    and wait and wait['phase'] in ('pending', 'ready') and wait['completions'] == 0
                    and wait['connection_id'] == context['old_connection_id'] and payload
                    and payload['epoch'] == context['epoch'])
                self.store.event(db, 'actual-reconnect-server-request-observed-no-reply',
                    dict(effect_id=context['effect_id'], wait_id=context['wait_id'],
                         new_connection_id=connection.connection_id, exact_original_request=exact,
                         current_unconsumed_binding=current, raw_sha256=latest[2] if actual_wire else None,
                         request=request, response_sent=False, rebind=False))
            return None
        return super().native_call(connection, request)

    def _original_wait(self, db, auth, args):
        need(type(args) is dict and set(args) == {'revision', 'wait_id'}, 'exact-reconnect-observation-selection')
        owner, task = self.store.binding(db, auth, args['revision'], paused_ok=True)
        need(owner['paused'], 'reconnect-observation-requires-durable-pause')
        wait = db.execute('select * from waits where wait_id=?', (args['wait_id'],)).fetchone()
        payload = db.execute('select * from checkpoint_wait_payload where wait_id=?', (args['wait_id'],)).fetchone()
        need(wait and payload and wait['session_id'] == self.session_id
             and wait['revision'] == task['revision'] and wait['generation'] == owner['generation']
             and wait['phase'] in ('pending', 'ready') and wait['completions'] == 0
             and payload['epoch'] == task['capability_epoch'], 'old-wait-not-current-unconsumed')
        turn = db.execute('select * from turns where turn_id=?', (wait['turn_id'],)).fetchone()
        dispatch = db.execute('select * from native_dispatch where turn_id=?', (wait['turn_id'],)).fetchone()
        need(turn and turn['state'] == 'active' and turn['revision'] == task['revision']
             and turn['owner_generation'] == owner['generation'] and turn['epoch'] == task['capability_epoch']
             and dispatch and dispatch['connection_id'] == wait['connection_id']
             and db.execute('select 1 from native_tool_operations where turn_id=?', (wait['turn_id'],)).fetchone() is None,
             'old-turn-or-reply-already-consumed')
        original = json.loads(payload['raw_request'])
        params = original.get('params', {})
        need(original.get('method') == 'item/tool/call' and 'id' in original
             and json.loads(payload['rpc_id']) == original['id']
             and params == dict(threadId=self.session_id, turnId=wait['turn_id'], callId=wait['call_id'],
                                namespace=None, tool='lab_read_assignment', arguments={}),
             'exact-original-request-envelope-required')
        return dict(owner), dict(task), dict(wait), dict(payload), original

    def _reconnect_observe(self, auth, args):
        ref = self.private_authorization('disconnect-reference', expected_kind=None)
        with self.store.tx() as db:
            owner, task, wait, payload, original = self._original_wait(db, auth, args)
            need(ref['session_id'] == wait['session_id'] and ref['revision'] == wait['revision']
                 and ref['wait_id'] == wait['wait_id'] and ref['turn_id'] == wait['turn_id']
                 and ref['call_id'] == wait['call_id'] and ref['broker_socket_removed'] is True,
                 'independently-recorded-prior-disconnect-scope-required')
            key = 'native-wait-reconnect-observe:' + wait['wait_id'] + ':' + wait['connection_id']
            prior = db.execute('select effect_id from effects where operation_key=?', (key,)).fetchone()
            if prior:
                return dict(action='hold', reason='reconnect-observation-consumed-no-repeat', effect_id=prior['effect_id'])
        # Do not disconnect a still-owned originating channel. This command is
        # only for a new exclusive Broker-created observer proxy after restart.
        need(self.native is None, 'existing-native-channel-must-not-be-replaced')
        connection = self._role_connection()  # Only existing pinned proxy/host; no native resume yet.
        need(connection.connection_id != wait['connection_id'], 'new-exclusive-observation-channel-required')
        connection.check_endpoint()  # Exact prior alias/target/dev/inode, not process-epoch proof.
        loaded = connection.rpc('thread/loaded/list', {})
        need(type(loaded.get('data')) is list and self.session_id in loaded['data'],
             'warm-loaded-same-thread-required-no-cold-resume')
        result = connection.rpc('thread/read', dict(threadId=self.session_id, includeTurns=True))
        thread = result['thread']
        observed_turns = [t for t in thread.get('turns', []) if t.get('id') == wait['turn_id']]
        need(thread.get('id') == self.session_id and thread.get('cwd') == self.cfg['physical_cwd']
             and len(observed_turns) == 1 and observed_turns[0].get('status') == 'inProgress',
             'exact-warm-pending-turn-required')
        with self.store.tx() as db:
            owner, task, wait, payload, original = self._original_wait(db, auth, args)
            need(db.execute('select 1 from effects where operation_key=?', (key,)).fetchone() is None,
                 'reconnect-already-consumed')
            effect_id = secrets.token_hex(24)
            target = dict(session_id=self.session_id, wait_id=wait['wait_id'], old_connection_id=wait['connection_id'],
                          new_connection_id=connection.connection_id, turn_id=wait['turn_id'],
                          original_request_semantic_sha256=digest(wire(original)))
            self.store._index_effect(db, effect_id, wait['revision'], owner['generation'], key,
                                    'native-wait-reconnect-observe', target, 'unknown')
            self.store.event(db, 'warm-reconnect-observe-consumed-before-subscribe',
                dict(effect_id=effect_id, target=target, prior_broker_stop_reference=ref,
                     same_server_epoch_proved=False, old_proxy_termination_proved=False))
        self._replay_observation = dict(effect_id=effect_id, wait_id=wait['wait_id'], revision=wait['revision'],
            generation=owner['generation'], epoch=task['capability_epoch'], old_connection_id=wait['connection_id'],
            new_connection_id=connection.connection_id, original_request=original)
        # Warm subscription only: no cwd/permissions/config/Goal/history overrides.
        # RPC observer mode is installed BEFORE sending, and persists afterwards,
        # because pending replay follows resume response in aligned source.
        resumed = connection.rpc('thread/resume', dict(threadId=self.session_id, excludeTurns=True),
                                 role_key=self._role_key)
        profile = resumed.get('activePermissionProfile')
        need(resumed['thread']['id'] == self.session_id
             and resumed['thread'].get('cwd') == self.cfg['physical_cwd']
             and resumed.get('cwd') == self.cfg['physical_cwd'] and resumed.get('approvalPolicy') == 'never'
             and type(profile) is dict and profile.get('id') == self.permission_profile_id(), 'warm-subscription-binding-mismatch')
        connection.check_endpoint()
        with self.store.tx() as db:
            self._original_wait(db, auth, args)
            db.execute("update effects set phase='observed',observation=? where effect_id=?", (wire(resumed), effect_id))
            self.store.event(db, 'warm-subscribe-response-awaiting-actual-replay-no-rebind', dict(effect_id=effect_id))
        return dict(action='observing', effect_id=effect_id, rebind=False, tool_replies_allowed=False,
                    next_command='role.poll then wait.reconnect-status')

    def _status(self, auth, args):
        with self.store.tx() as db:
            _, _, wait, _, _ = self._original_wait(db, auth, args)
            key = 'native-wait-reconnect-observe:' + wait['wait_id'] + ':' + wait['connection_id']
            effect = db.execute('select * from effects where operation_key=?', (key,)).fetchone()
            need(effect, 'no-consumed-reconnect-observation')
            rows = db.execute("select seq,body from events where kind='actual-reconnect-server-request-observed-no-reply' "
                              'order by seq desc limit 4097').fetchall()
            need(len(rows) <= 4096, 'replay-observation-window-unproved')
            selected = [(r['seq'], json.loads(r['body'])) for r in rows]
            selected = [(seq, body) for seq, body in selected if body.get('effect_id') == effect['effect_id']]
            exact = [seq for seq, body in selected if body.get('exact_original_request')
                     and body.get('current_unconsumed_binding')]
            return dict(action='hold', effect_id=effect['effect_id'], phase=effect['phase'],
                exact_actual_replay_observations=len(exact), replay_event_sequences=exact,
                rebind=False, tool_replies_allowed=False, same_server_epoch_proved=False,
                old_proxy_termination_proved=False, reason='observe-only-independent-epoch-old-channel-and-rebind-proof-missing')

    def handle(self, request):
        command = request.get('command')
        if command not in ('wait.reconnect-observe', 'wait.reconnect-status'):
            return super().handle(request)
        with self.execution_gate:
            auth, args = request.get('auth'), request.get('arguments', {})
            if command == 'wait.reconnect-observe':
                return self._reconnect_observe(auth, args)
            return self._status(auth, args)
