"""Explicit one-role probation. Actual native calls only, no manufactured oracle."""
import hashlib
import json
import os
from pathlib import Path
import secrets
import stat
import uuid

from runtime_store import Store, need, wire, digest

TOOL = dict(type='function', name='lab_read_assignment',
            description='Read only the current broker-authorized assignment checkpoint.',
            inputSchema=dict(type='object', properties={}, additionalProperties=False))


def canonical_uuid(value):
    need(type(value) is str and str(uuid.UUID(value)) == value, 'canonical-native-id-required')
    return value


class RoleStore(Store):
    def __init__(self, directory, installation):
        super().__init__(directory, installation)
        with self.tx() as db:
            db.execute('''create table if not exists role_registration(
                id integer primary key check(id=1), effect_id text unique not null,
                phase text not null, generation integer not null, session_id text unique,
                first_turn_id text, readback_sha256 text)''')
            db.execute('''create table if not exists native_dispatch(
                effect_id text primary key, session_id text not null, revision integer not null,
                generation integer not null, epoch integer not null, prompt_sha256 text not null,
                phase text not null, connection_id text not null, turn_id text)''')
            db.execute('''create table if not exists native_tool_operations(
                session_id text not null,turn_id text not null,call_id text not null,
                revision integer not null,generation integer not null,epoch integer not null,
                connection_id text not null,phase text not null,authorization_seq integer,
                execution_seq integer,result text,primary key(session_id,turn_id,call_id))''')

    def allowed_session(self, session_id):
        row = self.db.execute('select session_id from role_registration where id=1').fetchone()
        return row is not None and row['session_id'] == session_id

    def registration(self):
        with self.tx() as db:
            row = db.execute('select * from role_registration where id=1').fetchone()
            return dict(row) if row else None


class RoleSupport:
    def __init__(self, cfg):
        super().__init__(cfg)
        self._role_key = object()
        self._dispatch_inflight = None

    def _role_connection(self):
        if self.native is None:
            self._native_inspect()  # Existing pilot read only; no old-ID resume/start.
        self.native.role_key = self._role_key
        self.native.observer = self.native_event
        registration = self.store.registration()
        if registration and registration['session_id']:
            self.native.sessions.add(registration['session_id'])
        return self.native

    def _register_role(self, auth, args):
        need(args == {'intent': 'register-one-stable-role-once'}, 'explicit-registration-intent-required')
        with self.store.tx() as db:
            owner = self.store.owner(db, auth)
            existing = db.execute('select * from role_registration where id=1').fetchone()
            if existing:
                return dict(action='hold', reason='registration-already-consumed-readback-only',
                            registration=dict(existing))
        connection = self._role_connection()
        params = dict(cwd=self.cfg['physical_cwd'], approvalPolicy='never', permissions=self.permission_profile_id(),
                      ephemeral=False, dynamicTools=[TOOL],
                      config={'features.shell_tool': False},
                      developerInstructions='Use only lab_read_assignment for the authorized checkpoint. '
                        'Do not execute shell, access other tools, change settings, or perform external actions.')
        effect_id = secrets.token_hex(24)
        with self.store.tx() as db:
            owner = self.store.owner(db, auth)
            need(db.execute('select 1 from role_registration where id=1').fetchone() is None,
                 'registration-race-lost')
            db.execute('insert into role_registration values(1,?,?,?,?,null,null)',
                       (effect_id, 'pending-unknown', owner['generation'], None))
            self.store.event(db, 'role-registration-consumed-before-native-request',
                             dict(effect_id=effect_id, generation=owner['generation'], params=params))
        # If reply is lost, keep pending-unknown. NEVER issue thread/start again.
        result = connection.rpc('thread/start', params, role_key=self._role_key)
        thread = result['thread']
        session = canonical_uuid(thread['id'])
        profile = result.get('activePermissionProfile')
        need(session != self.cfg['native_session']
             and thread.get('cwd') == self.cfg['physical_cwd']
             and result.get('cwd') == self.cfg['physical_cwd']
             and result.get('approvalPolicy') == 'never'
             and type(profile) is dict and profile.get('id') == self.permission_profile_id(),
             'new-registration-identity-or-effective-permission-mismatch')
        with self.store.tx() as db:
            self.store.owner(db, auth)
            db.execute("update role_registration set phase='provisional',session_id=? where id=1", (session,))
            self.store.event(db, 'role-provisional-not-stable', dict(effect_id=effect_id, session_id=session))
        connection.sessions.add(session)
        return dict(action='provisional', session_id=session, stable=False,
                    tools_inventory_proved=False, shell_disabled_requested=True)

    def _dispatch_first(self, auth, args):
        need(set(args) == {'revision'}, 'fixed-first-turn-only')
        registration = self.store.registration()
        need(registration is not None and registration['phase'] == 'provisional',
             'provisional-role-required-no-repeat')
        connection = self._role_connection()
        with self.store.tx() as db:
            owner, task = self.store.binding(db, auth, args['revision'])
            body = json.loads(task['body'])
            need(body['session_id'] == registration['session_id'], 'assignment-registration-mismatch')
            need(db.execute('select 1 from native_dispatch').fetchone() is None,
                 'first-dispatch-already-consumed-readback-only')
            prompt = ('Read the current assignment using lab_read_assignment with empty arguments. '
                      'Report its task and revision and stop. Do not call any other tool. '
                      'Current task: ' + body['task'] + '; revision: ' + str(task['revision']))
            effect_id = secrets.token_hex(24)
            db.execute('insert into native_dispatch values(?,?,?,?,?,?,?,?,null)',
                       (effect_id, body['session_id'], task['revision'], owner['generation'],
                        task['capability_epoch'], digest(prompt), 'unknown', connection.connection_id))
            db.execute("update role_registration set phase='first-turn-pending' where id=1")
            self.store.event(db, 'native-dispatch-consumed-before-request',
                             dict(effect_id=effect_id, prompt_utf8=prompt, prompt_sha256=digest(prompt),
                                  generation=owner['generation'], revision=task['revision']))
        self._dispatch_inflight = effect_id
        try:
            result = connection.rpc('turn/start', dict(threadId=body['session_id'],
                input=[dict(type='text', text=prompt)]), role_key=self._role_key)
            turn = result['turn']
            self._bind_turn(connection, effect_id, canonical_uuid(turn['id']))
            return dict(action='dispatched', effect_id=effect_id, turn_id=turn['id'], stable=False)
        finally:
            self._dispatch_inflight = None

    def _bind_turn(self, connection, effect_id, turn_id):
        with self.store.tx() as db:
            dispatch = db.execute('select * from native_dispatch where effect_id=?', (effect_id,)).fetchone()
            owner = db.execute('select * from owner where id=1').fetchone()
            task = self.store.current(db)
            need(dispatch is not None and dispatch['connection_id'] == connection.connection_id
                 and task is not None and task['phase'] == 'active'
                 and task['revision'] == dispatch['revision'] and task['capability_epoch'] == dispatch['epoch']
                 and owner['generation'] == dispatch['generation'] and not owner['paused'],
                 'dispatch-binding-revoked')
            need(dispatch['turn_id'] in (None, turn_id), 'ambiguous-native-turn')
            db.execute("update native_dispatch set turn_id=?,phase='observed' where effect_id=?", (turn_id, effect_id))
            existing = db.execute('select * from turns where turn_id=?', (turn_id,)).fetchone()
            if existing is None:
                db.execute('insert into turns values(?,?,?,?,?)',
                           (turn_id, dispatch['revision'], dispatch['generation'], dispatch['epoch'], 'active'))
                self.store.event(db, 'actual-native-turn-bound', dict(effect_id=effect_id, turn_id=turn_id))
            db.execute('update role_registration set first_turn_id=? where id=1', (turn_id,))

    def native_event(self, connection, message):
        if connection is not self.native:
            return
        method = message.get('method')
        params = message.get('params', {})
        if method not in ('turn/started', 'turn/completed'):
            return
        registration = self.store.registration()
        if not registration or params.get('threadId') != registration['session_id']:
            self.store.archive(message)
            return
        turn = params.get('turn', {})
        try:
            turn_id = canonical_uuid(turn['id'])
            if method == 'turn/started' and self._dispatch_inflight:
                self._bind_turn(connection, self._dispatch_inflight, turn_id)
            if method == 'turn/completed':
                with self.store.tx() as db:
                    row = db.execute('select state from turns where turn_id=?', (turn_id,)).fetchone()
                    if row and row['state'] == 'active' and turn.get('status') == 'completed' and not turn.get('error'):
                        db.execute("update turns set state='terminal' where turn_id=?", (turn_id,))
                        self.store.event(db, 'actual-native-turn-completed', dict(turn_id=turn_id))
                    else:
                        self.store.event(db, 'unusable-native-terminal-event', message)
        except (ValueError, KeyError, TypeError):
            self.store.archive(message)

    def native_call(self, connection, request):
        with self.execution_gate:
            if connection is not self.native or request.get('method') != 'item/tool/call':
                return super().native_call(connection, request)
            params = request.get('params', {})
            try:
                need(set(params) == {'threadId', 'turnId', 'callId', 'namespace', 'tool', 'arguments'}
                     and params['namespace'] is None and params['tool'] == TOOL['name']
                     and params['arguments'] == {}, 'only-fixed-read-tool')
                session, turn_id, call_id = (params[k] for k in ('threadId', 'turnId', 'callId'))
                canonical_uuid(session)
                canonical_uuid(turn_id)
                need(type(call_id) is str and 0 < len(call_id) <= 256, 'native-call-identity')
                with self.store.tx() as db:
                    owner = db.execute('select * from owner where id=1').fetchone()
                    task = self.store.current(db)
                    registration = db.execute('select * from role_registration where id=1').fetchone()
                    turn = db.execute('select * from turns where turn_id=?', (turn_id,)).fetchone()
                    dispatch = db.execute('select * from native_dispatch where turn_id=?', (turn_id,)).fetchone()
                    need(owner['client'] is not None and not owner['paused'] and task and task['phase'] == 'active'
                         and registration and registration['session_id'] == session and turn and turn['state'] == 'active'
                         and dispatch and dispatch['connection_id'] == connection.connection_id
                         and turn['owner_generation'] == owner['generation'] == dispatch['generation']
                         and turn['revision'] == task['revision'] == dispatch['revision']
                         and turn['epoch'] == task['capability_epoch'] == dispatch['epoch'], 'native-call-fenced')
                    body = json.loads(task['body'])
                    need(body['session_id'] == session and body['physical_cwd'] == self.cfg['physical_cwd'],
                         'tool-physical-assignment-binding')
                    identity = (session, turn_id, call_id)
                    need(db.execute('select 1 from native_tool_operations where turn_id=?',
                                    (turn_id,)).fetchone() is None, 'first-turn-read-already-consumed')
                    need(db.execute('select 1 from native_tool_operations where session_id=? and turn_id=? and call_id=?',
                                    identity).fetchone() is None, 'native-call-already-consumed-no-repeat-reply')
                    seq = self.store.event(db, 'first-tool-authorized-before-execution',
                        dict(session_id=session, turn_id=turn_id, call_id=call_id, rpc_id=request['id'],
                             connection_id=connection.connection_id, generation=owner['generation'],
                             revision=task['revision'], physical_cwd=body['physical_cwd'], tool=TOOL['name']))
                    db.execute('insert into native_tool_operations values(?,?,?,?,?,?,?,?,?,?,?)',
                               (*identity, task['revision'], owner['generation'], task['capability_epoch'],
                                connection.connection_id, 'consumed', seq, None, None))
                # execution_gate prevents revoke committing between auth and read.
                raw = self._read_checkpoint(body)
                need(json.loads(raw) == body, 'checkpoint-not-current-assignment')
                result = dict(contentItems=[dict(type='inputText', text=raw.decode('utf-8'))], success=True)
                with self.store.tx() as db:
                    seq = self.store.event(db, 'first-tool-actual-read-completed',
                        dict(session_id=session, turn_id=turn_id, call_id=call_id,
                             path=str(Path(body['physical_cwd']) / '.codex-lab/r1-current-assignment.json'),
                             raw_sha256=hashlib.sha256(raw).hexdigest()))
                    db.execute("update native_tool_operations set phase='reply-consumed',execution_seq=?,result=? "
                               'where session_id=? and turn_id=? and call_id=?', (seq, wire(result), *identity))
                    self.store.event(db, 'native-tool-reply-consumed-before-send', dict(call_id=call_id))
                return result  # Sent only by the originating actual connection.
            except (ValueError, KeyError, TypeError, OSError, UnicodeError):
                self.store.archive(dict(request=request, disposition='fenced-or-unbound-call-no-reply'))
                return None

    def _read_checkpoint(self, body):
        root = Path(body['physical_cwd'])
        need(root.resolve(strict=True) == root and str(root) == self.cfg['physical_cwd'], 'physical-cwd-moved')
        directory = root / '.codex-lab'
        need(directory.resolve(strict=True) == directory and not directory.is_symlink(), 'unsafe-checkpoint-parent')
        info = directory.stat()
        need(info.st_uid == os.geteuid() and not info.st_mode & 0o022, 'checkpoint-parent-not-owned')
        path = directory / 'r1-current-assignment.json'
        fd = os.open(str(path), os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
        try:
            before = os.fstat(fd)
            need(stat.S_ISREG(before.st_mode) and before.st_uid == os.geteuid()
                 and before.st_nlink == 1 and before.st_mode & 0o077 == 0
                 and 0 < before.st_size <= 4096, 'private-bounded-current-checkpoint-required')
            raw = os.read(fd, 4097)
            after, named = os.fstat(fd), path.lstat()
            def ident(s):
                return (s.st_dev, s.st_ino, s.st_size, s.st_mtime_ns, s.st_ctime_ns)
            need(len(raw) == before.st_size and ident(before) == ident(after) == ident(named),
                 'checkpoint-read-moved')
            return raw
        finally:
            os.close(fd)

    def _stabilize(self, auth):
        connection = self._role_connection()
        registration = self.store.registration()
        need(registration and registration['session_id'] and registration['first_turn_id'],
             'missing-provisional-first-turn-readback-only')
        result = connection.rpc('thread/read', dict(threadId=registration['session_id'], includeTurns=True))
        thread = result['thread']
        need(thread['id'] == registration['session_id'] and thread['cwd'] == self.cfg['physical_cwd'],
             'role-readback-mismatch')
        observed = [t for t in thread.get('turns', []) if t.get('id') == registration['first_turn_id']]
        need(len(observed) == 1 and observed[0].get('status') == 'completed'
             and not observed[0].get('error'), 'durable-first-turn-not-observed')
        items = observed[0].get('items')
        need(type(items) is list and all(type(item) is dict and item.get('type') in
             ('userMessage', 'agentMessage', 'reasoning', 'dynamicToolCall') for item in items),
             'first-turn-includes-unproved-tool-or-unknown-item')
        calls = [item for item in items if item.get('type') == 'dynamicToolCall']
        need(len(calls) == 1 and calls[0].get('tool') == TOOL['name']
             and calls[0].get('status') == 'completed', 'durable-dynamic-read-not-observed')
        with self.store.tx() as db:
            owner = self.store.owner(db, auth)
            task = self.store.current(db)
            turn = db.execute('select * from turns where turn_id=?', (registration['first_turn_id'],)).fetchone()
            operations = db.execute("select * from native_tool_operations where turn_id=? and phase='reply-consumed'",
                                    (registration['first_turn_id'],)).fetchall()
            need(task and task['phase'] == 'active' and not owner['paused'] and turn
                 and turn['state'] in ('active', 'terminal') and turn['revision'] == task['revision']
                 and turn['epoch'] == task['capability_epoch'] and turn['owner_generation'] == owner['generation']
                 and len(operations) == 1 and operations[0]['authorization_seq'] < operations[0]['execution_seq'],
                 'first-authorized-action-not-current-or-not-proved')
            if turn['state'] == 'active':
                db.execute("update turns set state='terminal' where turn_id=?", (registration['first_turn_id'],))
                self.store.event(db, 'terminal-observed-in-durable-native-readback',
                                 dict(turn_id=registration['first_turn_id']))
            raw_pin = digest(wire(result))  # Semantic readback index, not original-wire digest.

            db.execute("update role_registration set phase='stable',readback_sha256=? where id=1", (raw_pin,))
            self.store.event(db, 'stable-native-role-durable-first-turn-readback',
                             dict(session_id=registration['session_id'], first_turn_id=registration['first_turn_id'],
                                  semantic_readback_sha256=raw_pin, tools_inventory_proved=False))
        return dict(action='stable', session_id=registration['session_id'],
                    first_turn_id=registration['first_turn_id'], tools_inventory_proved=False,
                    first_action_witness='candidate-observation-requires-independent-verification')

    def handle(self, request):
        command = request.get('command')
        if not isinstance(command, str) or not command.startswith('role.'):
            return super().handle(request)
        with self.execution_gate:
            auth, args = request.get('auth'), request.get('arguments', {})
            with self.store.tx() as db:
                self.store.owner(db, auth)
            if command == 'role.register':
                return self._register_role(auth, args)
            if command == 'role.dispatch-first':
                return self._dispatch_first(auth, args)
            if command == 'role.poll':
                need(self.native is not None, 'reconnect-readback-before-poll')
                return self.native.poll()
            if command == 'role.stabilize':
                return self._stabilize(auth)
            if command == 'role.inspect':
                return dict(registration=self.store.registration(), authority='observation-only')
            raise ValueError('role-command-denied')
