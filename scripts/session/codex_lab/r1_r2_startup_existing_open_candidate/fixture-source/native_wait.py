"""Native checkpoint-read wait. External verifier is injected, never self-signed."""
import json
import secrets

from native_quiesce import QuiesceStore, QuiesceSupport
from native_role import TOOL
from runtime_store import need, wire, digest


class WaitStore(QuiesceStore):
    def __init__(self, directory, installation):
        super().__init__(directory, installation)
        with self.tx() as db:
            db.execute('''create table if not exists checkpoint_wait_control(
              revision integer primary key, effect_id text not null, phase text not null)''')
            db.execute('''create table if not exists checkpoint_wait_payload(
              wait_id text primary key, epoch integer not null, rpc_id text not null,
              raw_request text not null, proof text, proof_sha256 text)''')
            self.event(db, 'checkpoint-wait-schema-present', dict(version=1))


    def observe_existing(self, auth, args):
        need(set(args) == {'revision', 'target'}, 'exact-existing-ci-observation-required')
        target = args['target']
        need(type(target) is dict and set(target) == {'repository', 'run_id', 'attempt', 'head'}
             and target['repository'] == 'YeliangDu/forcitis-one-psa-codex-lab'
             and type(target['run_id']) is int and target['run_id'] > 0
             and type(target['attempt']) is int and target['attempt'] > 0, 'exact-ci-observation-target')
        key = digest(wire(['ci-observe-existing', target['repository'], target['run_id'], target['attempt']]))
        with self.tx() as db:
            owner, task = self.binding(db, auth, args['revision'], paused_ok=True)
            need(json.loads(task['body'])['H'] == target['head'], 'observed-ci-not-current-assignment-head')
            prior = db.execute('select * from effects where operation_key=?', (key,)).fetchone()
            if prior:
                need(prior['kind'] == 'ci-observe-existing' and prior['target'] == wire(target)
                     and prior['revision'] == task['revision'] and prior['generation'] == owner['generation'],
                     'existing-observation-cannot-be-rebound')
                return dict(prior)
            effect_id = secrets.token_hex(24)
            db.execute('insert into effects(effect_id,revision,generation,operation_key,kind,target,phase) '
                       'values(?,?,?,?,?,?,?)', (effect_id, task['revision'], owner['generation'], key,
                                               'ci-observe-existing', wire(target), 'observing'))
            self.event(db, 'readonly-existing-ci-observation-no-send', dict(effect_id=effect_id, target=target))
            return dict(db.execute('select * from effects where effect_id=?', (effect_id,)).fetchone())


class WaitSupport(QuiesceSupport):
    def __init__(self, cfg):
        super().__init__(cfg)
        self._verifier = None

    def install_verifier(self, port):
        # Called only by the independently pinned, trusted startup composition.
        # No module-path import, CLI setter, candidate JSON flag or default oracle.
        need(port is None or (type(port) not in (dict, bool, str)
             and callable(getattr(port, 'verify_ci_result', None))), 'external-verifier-port-required')
        self._verifier = port

    def _arm(self, auth, args):
        need(set(args) == {'revision', 'effect_id'}, 'exact-wait-arm-required')
        with self.store.tx() as db:
            owner, task = self.store.binding(db, auth, args['revision'], paused_ok=True)
            effect = db.execute('select * from effects where effect_id=?', (args['effect_id'],)).fetchone()
            body = json.loads(task['body'])
            need(body['session_id'] == self.session_id and effect and effect['revision'] == task['revision']
                 and effect['generation'] == owner['generation'] and effect['kind'] in ('ci-rerun', 'ci-observe-existing'),
                 'current-ci-effect-wait-required')
            old = db.execute('select * from checkpoint_wait_control where revision=?', (task['revision'],)).fetchone()
            if old:
                need(old['effect_id'] == effect['effect_id'], 'wait-cannot-rebind-current-read')
            else:
                need(db.execute('select 1 from native_tool_operations where revision=?',
                                (task['revision'],)).fetchone() is None, 'read-already-executed')
                db.execute('insert into checkpoint_wait_control values(?,?,?)',
                           (task['revision'], effect['effect_id'], 'armed'))
                self.store.event(db, 'current-checkpoint-read-explicitly-awaits-verification',
                                 dict(revision=task['revision'], effect_id=effect['effect_id']))
        return dict(action='armed', revision=args['revision'], no_external_effect_issued=True)

    def _live_binding(self, db, params, connection, *, paused_ok=True):
        owner = db.execute('select * from owner where id=1').fetchone()
        task = self.store.current(db)
        turn = db.execute('select * from turns where turn_id=?', (params.get('turnId'),)).fetchone()
        dispatch = db.execute('select * from native_dispatch where turn_id=?', (params.get('turnId'),)).fetchone()
        need(owner['client'] is not None and (paused_ok or not owner['paused'])
             and params.get('threadId') == self.session_id and params.get('tool') == TOOL['name']
             and params.get('namespace') is None and params.get('arguments') == {}
             and type(params.get('callId')) is str and 0 < len(params['callId']) <= 256
             and task and task['phase'] == 'active' and turn and turn['state'] == 'active' and dispatch
             and dispatch['connection_id'] == connection.connection_id
             and owner['generation'] == turn['owner_generation'] == dispatch['generation']
             and task['revision'] == turn['revision'] == dispatch['revision']
             and task['capability_epoch'] == turn['epoch'] == dispatch['epoch'], 'obsolete-wait-native-binding')
        return owner, task

    def native_call(self, connection, request):
        with self.execution_gate:
            if connection is not self.native or request.get('method') != 'item/tool/call':
                return super().native_call(connection, request)
            params = request.get('params', {})
            try:
                with self.store.tx() as db:
                    owner, task = self._live_binding(db, params, connection)
                    control = db.execute('select * from checkpoint_wait_control where revision=?',
                                         (task['revision'],)).fetchone()
                    if control:
                        wait_id = digest(wire([self.session_id, params['turnId'], params['callId']]))
                        old = db.execute('select * from waits where wait_id=?', (wait_id,)).fetchone()
                        if old:
                            # Re-subscription/repeated native call never gets a second reply.
                            self.store.event(db, 'duplicate-native-wait-call-no-reply', dict(wait_id=wait_id))
                            return None
                        need(db.execute('select 1 from waits where revision=?', (task['revision'],)).fetchone() is None,
                             'one-checkpoint-wait-per-revision')
                        db.execute('insert into waits values(?,?,?,?,?,?,?,?,?,0)',
                            (wait_id, control['effect_id'], task['revision'], owner['generation'],
                             params['turnId'], params['callId'], connection.connection_id, 'pending'))
                        db.execute('insert into checkpoint_wait_payload values(?,?,?,?,null,null)',
                                   (wait_id, task['capability_epoch'], wire(request['id']), wire(request)))
                        db.execute("update checkpoint_wait_control set phase='waiting' where revision=?", (task['revision'],))
                        self.store.event(db, 'actual-native-checkpoint-read-waiting-no-reply',
                            dict(wait_id=wait_id, revision=task['revision'], effect_id=control['effect_id'],
                                 session_id=self.session_id, turn_id=params['turnId'], call_id=params['callId'],
                                 connection_id=connection.connection_id))
                        return None
                return super().native_call(connection, request)
            except (ValueError, KeyError, TypeError):
                self.store.archive(dict(request=request, disposition='obsolete-wait-no-reply'))
                return None

    def native_event(self, connection, message):
        if connection is self.native and message.get('method') == 'thread/goal/updated':
            params = message.get('params', {})
            if params.get('threadId') == self.session_id:
                with self.store.tx() as db:
                    self.store.event(db, 'actual-goal-event-observation-no-transition', message)
                return  # Never clear pause, create effects, publish or reply here.
        return super().native_event(connection, message)

    def _verify(self, auth, args):
        need(set(args) == {'revision', 'wait_id', 'delivery_id'}, 'exact-wait-proof-selection-required')
        with self.store.tx() as db:
            owner, task = self.store.binding(db, auth, args['revision'], paused_ok=True)
            wait = db.execute('select * from waits where wait_id=?', (args['wait_id'],)).fetchone()
            delivery = db.execute('select * from inbox where delivery_id=?', (args['delivery_id'],)).fetchone()
            need(wait and wait['revision'] == task['revision'] and wait['generation'] == owner['generation']
                 and wait['phase'] in ('pending', 'ready') and delivery, 'current-wait-and-staged-delivery-required')
            effect = db.execute('select * from effects where effect_id=?', (wait['effect_id'],)).fetchone()
            binding = dict(wait_id=wait['wait_id'], effect_id=effect['effect_id'], revision=wait['revision'],
                generation=wait['generation'], session_id=wait['session_id'], turn_id=wait['turn_id'], call_id=wait['call_id'],
                assignment=json.loads(task['body']))
            selected_effect, selected_delivery = dict(effect), dict(delivery)
        if self._verifier is None:
            return dict(action='hold', reason='independent-hosted-verifier-not-installed', completed=0)
        # External port must authenticate source/coverage and exact A/H/B/profile,
        # run/attempt/outcome. Input delivery is an UNTRUSTED notification hint.
        proof = self._verifier.verify_ci_result(selected_effect, selected_delivery, binding)
        target = json.loads(effect['target'])
        need(type(proof) is dict and set(proof) == {'kind', 'binding', 'repository', 'run_id', 'attempt',
             'head', 'outcome', 'source_receipt_sha256'} and proof['kind'] == 'external-ci-result-proof/1'
             and proof['binding'] == binding and proof['outcome'] in ('success', 'failure')
             and all(proof[k] == target[k] and type(proof[k]) is type(target[k])
                     for k in ('repository', 'run_id', 'attempt', 'head'))
             and type(proof['source_receipt_sha256']) is str and len(proof['source_receipt_sha256']) == 64
             and all(c in '0123456789abcdef' for c in proof['source_receipt_sha256']),
             'external-proof-does-not-bind-exact-wait')
        with self.store.tx() as db:
            owner, task = self.store.binding(db, auth, args['revision'], paused_ok=True)
            wait = db.execute('select * from waits where wait_id=?', (args['wait_id'],)).fetchone()
            payload = db.execute('select * from checkpoint_wait_payload where wait_id=?', (args['wait_id'],)).fetchone()
            need(wait and wait['generation'] == owner['generation'] and wait['phase'] in ('pending', 'ready')
                 and payload['epoch'] == task['capability_epoch'], 'wait-revoked-during-verification')
            encoded = wire(proof)
            need(payload['proof'] is None or payload['proof'] == encoded, 'conflicting-verified-result')
            if payload['proof'] is None:
                db.execute('update checkpoint_wait_payload set proof=?,proof_sha256=? where wait_id=?',
                           (encoded, digest(encoded), wait['wait_id']))
                db.execute("update waits set phase='ready' where wait_id=?", (wait['wait_id'],))
                self.store.event(db, 'external-proof-staged-not-completed', dict(wait_id=wait['wait_id'], proof_sha256=digest(encoded)))
        return dict(action='ready', wait_id=args['wait_id'], completed=0, pause_cleared=False)

    def _drain(self, auth, args):
        need(set(args) == {'revision', 'wait_id'}, 'exact-wait-drain-required')
        with self.store.tx() as db:
            owner, task = self.store.binding(db, auth, args['revision'], paused_ok=True)
            if owner['paused']:
                return dict(action='hold', reason='durable-pause', completed=0, advanced=0)
            wait = db.execute('select * from waits where wait_id=?', (args['wait_id'],)).fetchone()
            payload = db.execute('select * from checkpoint_wait_payload where wait_id=?', (args['wait_id'],)).fetchone()
            need(wait and payload and wait['phase'] == 'ready' and wait['completions'] == 0
                 and wait['revision'] == task['revision'] and wait['generation'] == owner['generation']
                 and payload['epoch'] == task['capability_epoch'] and payload['proof'] is not None
                 and digest(payload['proof']) == payload['proof_sha256'], 'wait-not-current-ready-or-already-consumed')
            connection = self.native
            need(connection is not None and connection.connection_id == wait['connection_id'],
                 'originating-native-connection-lost-no-reply')
            request = json.loads(payload['raw_request'])
            self._live_binding(db, request['params'], connection, paused_ok=False)
            effect = db.execute('select advances from effects where effect_id=?', (wait['effect_id'],)).fetchone()
            need(effect and effect['advances'] == 0, 'effect-continuation-already-consumed')
            db.execute("update waits set phase='delivery-unknown',completions=1 where wait_id=?", (wait['wait_id'],))
            db.execute('update effects set advances=1 where effect_id=?', (wait['effect_id'],))
            self.store.event(db, 'single-wait-continuation-consumed-before-read-and-reply', dict(wait_id=wait['wait_id']))
        # Bypass only this subclass's delay; inherited tool authorization/read/
        # reply-consumption checks still apply under the SAME execution_gate.
        answer = super().native_call(connection, request)
        if answer is None:
            return dict(action='hold', reason='consumed-wait-read-unavailable-no-retry', completed=0, advanced=0)
        connection._send(dict(id=request['id'], result=answer))
        with self.store.tx() as db:
            db.execute("update waits set phase='completed' where wait_id=?", (wait['wait_id'],))
            self.store.event(db, 'same-native-checkpoint-read-wait-reply-written', dict(wait_id=wait['wait_id']))
        return dict(action='reply-written', wait_id=wait['wait_id'], completed=1,
                    advanced=1, assignment_revision_changed=False, independent_acceptance=False)

    def handle(self, request):
        command = request.get('command')
        if command not in ('effect.observe-existing', 'wait.arm', 'wait.verify', 'wait.drain', 'goal.inspect'):
            return super().handle(request)
        with self.execution_gate:
            auth, args = request.get('auth'), request.get('arguments', {})
            with self.store.tx() as db:
                self.store.owner(db, auth)
            if command == 'effect.observe-existing':
                return self.store.observe_existing(auth, args)
            if command == 'wait.arm':
                return self._arm(auth, args)
            if command == 'wait.verify':
                return self._verify(auth, args)
            if command == 'wait.drain':
                return self._drain(auth, args)
            need(args == {}, 'goal-read-only-no-parameters')
            connection = self._role_connection()
            result = connection.rpc('thread/goal/get', dict(threadId=self.session_id))
            return dict(action='observed', goal=result, continuation_authority=False)
