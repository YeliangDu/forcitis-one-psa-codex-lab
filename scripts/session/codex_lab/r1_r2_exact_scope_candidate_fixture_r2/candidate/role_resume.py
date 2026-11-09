"""Existing stable-role continuation. No role creation, reset or new effect truth."""
import json
from pathlib import Path
import secrets
import stat

from native_role import RoleStore, RoleSupport, TOOL, canonical_uuid
from runtime_store import need, wire, digest



class ResumeStore(RoleStore):
    def __init__(self, directory, installation):
        path = Path(directory) / 'runtime.sqlite3'
        need(path.is_file() and not path.is_symlink(), 'existing-role-ledger-required-no-initialization')
        super().__init__(directory, installation)  # Preserves exact installation pin and owner credentials.
        with self.tx() as db:
            registration = db.execute('select * from role_registration where id=1').fetchone()
            need(registration is not None and registration['phase'] == 'stable'
                 and registration['session_id'] == self.session_id and registration['first_turn_id'],
                 'existing-observed-stable-role-required')
            first = db.execute('select * from turns where turn_id=?', (registration['first_turn_id'],)).fetchone()
            operations = db.execute("select * from native_tool_operations where turn_id=? and phase='reply-consumed'",
                                    (registration['first_turn_id'],)).fetchall()
            need(first and first['state'] == 'terminal' and len(operations) == 1
                 and operations[0]['authorization_seq'] < operations[0]['execution_seq'],
                 'existing-first-action-evidence-required')
            db.execute('create table if not exists native_resume_schema(id integer primary key check(id=1), version integer not null)')
            version = db.execute('select version from native_resume_schema where id=1').fetchone()
            need(version is None or version['version'] == 1, 'unknown-native-resume-schema')
            if version is None:
                # Existing native tables remain correlation projections. Effects
                # becomes the common consume-before-send registry, reusing IDs.
                self._index_effect(db, registration['effect_id'], 0, registration['generation'],
                    'native-role-registration:' + installation['role'], 'native-role-registration',
                    dict(session_id=self.session_id), 'observed')
                for row in db.execute('select * from native_dispatch').fetchall():
                    self._index_effect(db, row['effect_id'], row['revision'], row['generation'],
                        'native-turn:' + row['session_id'] + ':' + str(row['revision']), 'native-turn',
                        dict(session_id=row['session_id'], prompt_sha256=row['prompt_sha256']),
                        'observed' if row['turn_id'] else 'unknown')
                db.execute('insert into native_resume_schema values(1,1)')
                self.event(db, 'existing-native-effects-indexed-no-new-send',
                           dict(session_id=self.session_id, schema=1))
            # Every restart rechecks projections against the single effect truth.
            for row in db.execute('select * from native_dispatch').fetchall():
                effect = db.execute('select * from effects where effect_id=?', (row['effect_id'],)).fetchone()
                need(effect and effect['kind'] == 'native-turn' and effect['revision'] == row['revision']
                     and effect['generation'] == row['generation'] and effect['issue_count'] == 1,
                     'native-effect-index-inconsistent')

    def _index_effect(self, db, effect_id, revision, generation, key, kind, target, phase):
        existing = db.execute('select * from effects where effect_id=? or operation_key=?', (effect_id, key)).fetchall()
        if existing:
            need(len(existing) == 1 and existing[0]['effect_id'] == effect_id
                 and existing[0]['revision'] == revision and existing[0]['generation'] == generation
                 and existing[0]['operation_key'] == key and existing[0]['kind'] == kind
                 and existing[0]['target'] == wire(target) and existing[0]['issue_count'] == 1,
                 'existing-effect-identity-conflict')
            return
        db.execute('insert into effects(effect_id,revision,generation,operation_key,kind,target,phase,issue_count) '
                   'values(?,?,?,?,?,?,?,1)', (effect_id, revision, generation, key, kind, wire(target), phase))


class ResumeSupport(RoleSupport):
    def __init__(self, cfg):
        super().__init__(cfg)
        self._resumed_binding = None  # Connection capability, never persisted as live authority.

    def _resume_role(self, auth, args):
        need(set(args) == {'revision'}, 'current-revision-required')
        registration = self.store.registration()
        need(registration and registration['phase'] == 'stable'
             and registration['session_id'] == self.session_id, 'existing-stable-role-only')
        connection = self._role_connection()
        revision = args['revision']
        with self.store.tx() as db:
            owner, task = self.store.binding(db, auth, revision, paused_ok=True)
            body = json.loads(task['body'])
            need(body['session_id'] == self.session_id, 'current-assignment-role-mismatch')
            key = 'native-resume:' + self.session_id + ':' + str(owner['generation']) + ':' + str(revision)
            prior = db.execute('select * from effects where operation_key=?', (key,)).fetchone()
            if prior:
                return dict(action='hold', reason='resume-consumed-use-readback-no-repeat', effect_id=prior['effect_id'])
            effect_id = secrets.token_hex(24)
            target = dict(session_id=self.session_id, connection_id=connection.connection_id,
                          revision=revision, epoch=task['capability_epoch'])
            self.store._index_effect(db, effect_id, revision, owner['generation'], key, 'native-resume', target, 'unknown')
            self.store.event(db, 'resume-consumed-before-request', dict(effect_id=effect_id, target=target))
            generation, epoch = owner['generation'], task['capability_epoch']
        result = connection.rpc('thread/resume', dict(threadId=self.session_id, excludeTurns=True,
            cwd=self.cfg['physical_cwd'], permissions=self.permission_profile_id(), approvalPolicy='never',
            config={'features.shell_tool': False}), role_key=self._role_key)
        profile, thread = result.get('activePermissionProfile'), result['thread']
        need(thread['id'] == self.session_id and thread.get('cwd') == self.cfg['physical_cwd']
             and result.get('cwd') == self.cfg['physical_cwd'] and result.get('approvalPolicy') == 'never'
             and thread.get('status', {}).get('type') == 'idle'
             and type(profile) is dict and profile.get('id') == self.permission_profile_id(),
             'resume-effective-binding-unproved')
        with self.store.tx() as db:
            owner, task = self.store.binding(db, auth, revision, paused_ok=True)
            need(owner['generation'] == generation and task['capability_epoch'] == epoch, 'resume-revoked')
            db.execute("update effects set phase='observed',observation=? where effect_id=?",
                       (wire(result), effect_id))
            self.store.event(db, 'same-stable-role-resume-response-observed',
                             dict(effect_id=effect_id, session_id=self.session_id,
                                  semantic_response_sha256=digest(wire(result))))
        self._resumed_binding = (connection.connection_id, generation, revision, epoch)
        return dict(action='resumed', session_id=self.session_id, effect_id=effect_id,
                    tools_inventory_proved=False, new_turn_sent=False)

    def _dispatch_current(self, auth, args):
        need(set(args) == {'revision'}, 'exact-current-dispatch-request')
        connection = self._role_connection()
        revision = args['revision']
        with self.store.tx() as db:
            owner, task = self.store.binding(db, auth, revision)
            body = json.loads(task['body'])
            need(body['session_id'] == self.session_id and self._resumed_binding ==
                 (connection.connection_id, owner['generation'], revision, task['capability_epoch']),
                 'same-connection-current-resume-required')
            need(db.execute("select 1 from turns where state!='terminal'").fetchone() is None,
                 'prior-native-turn-not-terminal-readback-required')
            key = 'native-turn:' + self.session_id + ':' + str(revision)
            prior = db.execute('select * from effects where operation_key=?', (key,)).fetchone()
            if prior:
                return dict(action='hold', reason='dispatch-consumed-readback-only', effect_id=prior['effect_id'])
            prompt = ('Read the current assignment using lab_read_assignment with empty arguments. '
                      'Report task and revision and stop. No other tools. Current task: '
                      + body['task'] + '; revision: ' + str(revision))
            effect_id = secrets.token_hex(24)
            self.store._index_effect(db, effect_id, revision, owner['generation'], key, 'native-turn',
                dict(session_id=self.session_id, prompt_sha256=digest(prompt)), 'unknown')
            db.execute('insert into native_dispatch values(?,?,?,?,?,?,?,?,null)',
                       (effect_id, revision, owner['generation'], task['capability_epoch'],
                        digest(prompt), 'unknown', connection.connection_id))
            self.store.event(db, 'current-dispatch-consumed-before-native-request',
                dict(effect_id=effect_id, revision=revision, generation=owner['generation'],
                     prompt_utf8=prompt, prompt_sha256=digest(prompt)))
        self._dispatch_inflight = effect_id
        try:
            result = connection.rpc('turn/start', dict(threadId=self.session_id,
                input=[dict(type='text', text=prompt)]), role_key=self._role_key)
            turn_id = canonical_uuid(result['turn']['id'])
            self._bind_turn(connection, effect_id, turn_id)
            return dict(action='dispatched', effect_id=effect_id, turn_id=turn_id, revision=revision)
        finally:
            self._dispatch_inflight = None

    def _bind_turn(self, connection, effect_id, turn_id):
        # Preserve immutable original registration.first_turn_id across tasks.
        with self.store.tx() as db:
            dispatch = db.execute('select * from native_dispatch where effect_id=?', (effect_id,)).fetchone()
            effect = db.execute('select * from effects where effect_id=?', (effect_id,)).fetchone()
            owner = db.execute('select * from owner where id=1').fetchone()
            task = self.store.current(db)
            need(dispatch and effect and effect['issue_count'] == 1
                 and dispatch['connection_id'] == connection.connection_id and task and task['phase'] == 'active'
                 and dispatch['revision'] == task['revision'] and dispatch['epoch'] == task['capability_epoch']
                 and dispatch['generation'] == owner['generation'] and not owner['paused']
                 and dispatch['turn_id'] in (None, turn_id), 'current-dispatch-binding-fenced')
            db.execute("update native_dispatch set turn_id=?,phase='observed' where effect_id=?", (turn_id, effect_id))
            db.execute("update effects set phase='observed' where effect_id=?", (effect_id,))
            existing = db.execute('select * from turns where turn_id=?', (turn_id,)).fetchone()
            if existing is None:
                db.execute('insert into turns values(?,?,?,?,?)',
                           (turn_id, dispatch['revision'], dispatch['generation'], dispatch['epoch'], 'active'))
                self.store.event(db, 'current-actual-native-turn-bound', dict(effect_id=effect_id, turn_id=turn_id))
            else:
                need(existing['revision'] == dispatch['revision'] and existing['epoch'] == dispatch['epoch']
                     and existing['owner_generation'] == dispatch['generation'], 'native-turn-id-rebound')

    def _readback_current(self, auth, args):
        need(set(args) == {'revision'}, 'current-readback-revision-required')
        connection = self._role_connection()
        with self.store.tx() as db:
            owner, task = self.store.binding(db, auth, args['revision'], paused_ok=True)
            dispatches = db.execute('select * from native_dispatch where session_id=? and revision=?',
                                   (self.session_id, task['revision'])).fetchall()
            need(len(dispatches) == 1, 'no-unique-current-dispatch')
            dispatch = dict(dispatches[0])
        result = connection.rpc('thread/read', dict(threadId=self.session_id, includeTurns=True))
        thread = result['thread']
        need(thread['id'] == self.session_id and thread.get('cwd') == self.cfg['physical_cwd'], 'same-role-readback-required')
        if not dispatch['turn_id']:
            return dict(action='hold', reason='unknown-dispatch-turn-id-do-not-infer-or-resend', effect_id=dispatch['effect_id'])
        turns = [turn for turn in thread.get('turns', []) if turn.get('id') == dispatch['turn_id']]
        need(len(turns) == 1 and turns[0].get('status') == 'completed' and not turns[0].get('error'),
             'current-turn-not-complete-in-readback')
        items = turns[0].get('items')
        need(type(items) is list and all(type(item) is dict and item.get('type') in
             ('userMessage', 'agentMessage', 'reasoning', 'dynamicToolCall') for item in items),
             'current-turn-has-unproved-tool')
        calls = [item for item in items if item.get('type') == 'dynamicToolCall']
        need(len(calls) == 1 and calls[0].get('tool') == TOOL['name']
             and calls[0].get('status') == 'completed' and calls[0].get('success') is True,
             'current-durable-tool-read-not-proved')
        with self.store.tx() as db:
            owner, task = self.store.binding(db, auth, args['revision'], paused_ok=True)
            operations = db.execute("select * from native_tool_operations where turn_id=? and phase='reply-consumed'",
                                    (dispatch['turn_id'],)).fetchall()
            need(owner['generation'] == dispatch['generation'] and task['capability_epoch'] == dispatch['epoch']
                 and len(operations) == 1 and operations[0]['revision'] == task['revision']
                 and calls[0].get('id') == operations[0]['call_id']
                 and calls[0].get('namespace') is None and calls[0].get('arguments') == {}
                 and operations[0]['authorization_seq'] < operations[0]['execution_seq'], 'old-readback-fenced')
            db.execute("update turns set state='terminal' where turn_id=? and state='active'", (dispatch['turn_id'],))
            self.store.event(db, 'current-task-first-action-native-readback',
                dict(revision=task['revision'], turn_id=dispatch['turn_id'], effect_id=dispatch['effect_id'],
                     authorization_seq=operations[0]['authorization_seq'], execution_seq=operations[0]['execution_seq'],
                     semantic_readback_sha256=digest(wire(result))))
        return dict(action='observed', session_id=self.session_id, revision=args['revision'],
                    effect_id=dispatch['effect_id'], turn_id=dispatch['turn_id'],
                    first_action_observed=True, independent_acceptance=False)

    def handle(self, request):
        command = request.get('command')
        if command not in ('role.register', 'role.resume', 'role.dispatch-current', 'role.readback-current',
                           'role.dispatch-first', 'role.stabilize'):
            return super().handle(request)
        with self.execution_gate:
            auth, args = request.get('auth'), request.get('arguments', {})
            with self.store.tx() as db:
                self.store.owner(db, auth)
            if command == 'role.register':
                return dict(action='hold', reason='existing-stable-role-only-registration-prohibited')
            if command in ('role.dispatch-first', 'role.stabilize'):
                return dict(action='hold', reason='use-current-revision-path-preserve-original-first-turn')
            if command == 'role.resume':
                return self._resume_role(auth, args)
            if command == 'role.dispatch-current':
                return self._dispatch_current(auth, args)
            return self._readback_current(auth, args)
