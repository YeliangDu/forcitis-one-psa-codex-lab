"""One consumed interrupt of a known fenced old turn; terminal proof is readback."""
import json
import secrets

from role_resume import ResumeStore, ResumeSupport
from native_role import canonical_uuid
from runtime_store import need, wire, digest


class QuiesceStore(ResumeStore):
    pass  # Existing effects registry/schema/installation; no new state database.


class QuiesceSupport(ResumeSupport):
    def _quarantined_target(self, db, auth, args):
        need(type(args) is dict and set(args) == {'revision', 'turn_id'}
             and type(args['revision']) is int and args['revision'] > 0,
             'exact-old-revision-and-native-turn-required')
        canonical_uuid(args['turn_id'])  # Empty startup interrupt is forbidden.
        owner = self.store.owner(db, auth)
        task = db.execute('select * from tasks where revision=?', (args['revision'],)).fetchone()
        turn = db.execute('select * from turns where turn_id=?', (args['turn_id'],)).fetchone()
        dispatches = db.execute('select * from native_dispatch where turn_id=?', (args['turn_id'],)).fetchall()
        need(task and task['phase'] == 'fenced' and task['fence_seq'] is not None
             and turn and turn['revision'] == args['revision']
             and turn['state'] in ('quarantined-unresolved', 'terminal') and len(dispatches) == 1,
             'target-not-known-fenced-native-turn')
        dispatch = dispatches[0]
        body = json.loads(task['body'])
        need(body['session_id'] == self.session_id and dispatch['session_id'] == self.session_id
             and dispatch['revision'] == args['revision'] and dispatch['generation'] == owner['generation']
             and turn['owner_generation'] == owner['generation'] and turn['epoch'] == dispatch['epoch']
             and task['capability_epoch'] > dispatch['epoch'], 'original-generation-fence-binding-required')
        registration = db.execute('select * from role_registration where id=1').fetchone()
        need(registration and registration['phase'] == 'stable' and registration['session_id'] == self.session_id,
             'same-stable-registration-required')
        effect = db.execute('select * from effects where effect_id=?', (dispatch['effect_id'],)).fetchone()
        need(effect and effect['issue_count'] == 1 and effect['kind'] == 'native-turn',
             'original-consumed-dispatch-required')
        # Never interrupt to make room once another task may already execute.
        others = db.execute("select * from turns where turn_id!=? and state!='terminal'", (args['turn_id'],)).fetchall()
        need(not others, 'other-native-turn-unresolved-do-not-interrupt')
        newer = db.execute('select * from native_dispatch where revision>?', (args['revision'],)).fetchall()
        need(not newer, 'newer-task-dispatched-do-not-interrupt-old-target')
        return dict(dispatch), dict(turn)

    def _observe_target(self, connection, turn_id):
        result = connection.rpc('thread/read', dict(threadId=self.session_id, includeTurns=True))
        thread = result['thread']
        need(thread.get('id') == self.session_id and thread.get('cwd') == self.cfg['physical_cwd'],
             'native-quiescence-thread-mismatch')
        turns = thread.get('turns')
        need(type(turns) is list, 'native-turn-window-missing')
        matches = [turn for turn in turns if type(turn) is dict and turn.get('id') == turn_id]
        need(len(matches) == 1, 'exact-native-turn-not-in-readback-no-completeness-inference')
        return result, matches[0]

    def _interrupt_old(self, auth, args):
        with self.store.tx() as db:
            dispatch, old_turn = self._quarantined_target(db, auth, args)
            need(old_turn['state'] == 'quarantined-unresolved', 'target-already-terminal-readback-only')
            key = 'native-interrupt:' + self.session_id + ':' + args['turn_id']
            prior = db.execute('select effect_id from effects where operation_key=?', (key,)).fetchone()
            if prior:
                return dict(action='hold', reason='interrupt-consumed-readback-only', effect_id=prior['effect_id'])
        connection = self._role_connection()  # Existing proxy only; never native resume/start.
        observation, target_turn = self._observe_target(connection, args['turn_id'])
        need(target_turn.get('status') == 'inProgress', 'target-not-proved-running-use-terminal-readback')
        # Host validates exact active turn ID again. There is no current-turn or
        # empty-ID fallback if the native active turn changed since this read.
        with self.store.tx() as db:
            dispatch, _ = self._quarantined_target(db, auth, args)
            need(db.execute('select 1 from effects where operation_key=?', (key,)).fetchone() is None,
                 'interrupt-race-already-consumed')
            effect_id = secrets.token_hex(24)
            target = dict(session_id=self.session_id, turn_id=args['turn_id'], revision=args['revision'],
                          original_dispatch_effect=dispatch['effect_id'])
            self.store._index_effect(db, effect_id, args['revision'], dispatch['generation'],
                                     key, 'native-interrupt', target, 'unknown')
            self.store.event(db, 'old-fenced-interrupt-consumed-before-request',
                dict(effect_id=effect_id, target=target, connection_id=connection.connection_id,
                     semantic_preflight_sha256=digest(wire(observation))))
        result = connection.rpc('turn/interrupt', dict(threadId=self.session_id, turnId=args['turn_id']),
                                role_key=self._role_key)
        need(type(result) is dict and result == {}, 'unexpected-interrupt-response')
        with self.store.tx() as db:
            self._quarantined_target(db, auth, args)
            db.execute("update effects set phase='observed',observation=? where effect_id=?", (wire(result), effect_id))
            self.store.event(db, 'old-turn-interrupt-reply-not-terminal-proof',
                             dict(effect_id=effect_id, turn_id=args['turn_id']))
        return dict(action='observed', effect_id=effect_id, terminal_proved=False,
                    next_command='role.readback-quarantined')

    def _readback_quarantined(self, auth, args):
        with self.store.tx() as db:
            self._quarantined_target(db, auth, args)
        connection = self._role_connection()
        observation, target = self._observe_target(connection, args['turn_id'])
        status = target.get('status')
        need(status in ('completed', 'interrupted', 'failed'), 'actual-native-terminal-not-observed')
        with self.store.tx() as db:
            dispatch, _ = self._quarantined_target(db, auth, args)
            # Terminal proves this exact turn is no longer executing; it never
            # restores old epoch, task authority, wait or callback reply rights.
            db.execute("update turns set state='terminal' where turn_id=? and state='quarantined-unresolved'",
                       (args['turn_id'],))
            key = 'native-interrupt:' + self.session_id + ':' + args['turn_id']
            interrupt = db.execute('select effect_id from effects where operation_key=?', (key,)).fetchone()
            if interrupt:
                db.execute("update effects set phase='terminal-observed',observation=? where effect_id=?",
                           (wire(observation), interrupt['effect_id']))
            seq = self.store.event(db, 'quarantined-old-turn-actual-terminal-readback',
                dict(session_id=self.session_id, turn_id=args['turn_id'], revision=args['revision'],
                     native_status=status, original_dispatch_effect=dispatch['effect_id'],
                     semantic_readback_sha256=digest(wire(observation)), old_permissions_restored=False))
        return dict(action='terminal-observed', turn_id=args['turn_id'], revision=args['revision'],
                    native_status=status, sequence=seq, old_permissions_restored=False,
                    independent_acceptance=False)

    def handle(self, request):
        command = request.get('command')
        if command not in ('role.interrupt-quarantined', 'role.readback-quarantined'):
            return super().handle(request)
        with self.execution_gate:
            auth, args = request.get('auth'), request.get('arguments', {})
            if command == 'role.interrupt-quarantined':
                return self._interrupt_old(auth, args)
            return self._readback_quarantined(auth, args)
