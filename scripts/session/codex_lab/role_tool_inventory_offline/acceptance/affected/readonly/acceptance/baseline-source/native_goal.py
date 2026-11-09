"""Bounded Goal on one genuine pending wait. No reconnect or dispatch authority."""
import hashlib
import json
import secrets

from branch_broker import BranchStore, BranchSupport, private_raw
from runtime_store import need, wire, digest
from pending_wire import original_pending_wire


class GoalInstallation:
    def __init__(self, path, external_sha256):
        self.path, self.pin = path, external_sha256
        self.policy = self.load()

    def load(self):
        raw = private_raw(self.path)
        need(hashlib.sha256(raw).hexdigest() == self.pin, 'external-goal-installation-pin-mismatch')
        policy = json.loads(raw)
        need(set(policy) == {'kind', 'broker_installation_sha256', 'role_use', 'session_id',
                             'owner', 'card', 'revision', 'ci_kind', 'ci_target', 'ci_operation_key', 'token_budget'}
             and policy['kind'] == 'r2-exact-controlled-lead-goal/1'
             and policy['role_use'] == 'controlled-lead' and type(policy['session_id']) is str
             and type(policy['token_budget']) is int and 256 <= policy['token_budget'] <= 4096,
             'explicit-bounded-controlled-lead-goal-policy-required')
        target = policy['ci_target']
        need(policy['ci_kind'] in ('ci-observe-existing', 'ci-rerun')
             and type(target) is dict and set(target) == {'repository', 'run_id', 'attempt', 'head'}
             and target['repository'] == 'YeliangDu/forcitis-one-psa-codex-lab'
             and type(target['run_id']) is int and target['run_id'] > 0
             and type(target['attempt']) is int and target['attempt'] > 0
             and policy['ci_operation_key'] == digest(wire([policy['ci_kind'], target['repository'],
                                                          target['run_id'], target['attempt']])),
             'independently-pinned-exact-ci-semantic-key-required')
        return policy


class GoalStore(BranchStore):
    def __init__(self, directory, installation):
        super().__init__(directory, installation)
        with self.tx() as db:
            pass  # Existing-only startup: schema was independently verified; no DDL.


class GoalSupport(BranchSupport):
    def __init__(self, cfg):
        super().__init__(cfg)
        self._goal_installation = None

    def install_goal(self, installation):
        need(installation is None or type(installation) is GoalInstallation, 'trusted-goal-startup-only')
        self._goal_installation = installation

    def _policy(self, db, auth, args, *, active_required=True):
        need(self._goal_installation is not None, 'no-independent-controlled-lead-goal-installation')
        policy = self._goal_installation.load()
        need(policy == self._goal_installation.policy and policy['broker_installation_sha256'] == self.cfg['pin']
             and policy['session_id'] == self.session_id,
             'goal-policy-or-installation-moved')
        need(type(args) is dict and set(args) == {'revision', 'wait_id'}
             and args['revision'] == policy['revision'] and type(args['wait_id']) is str
             and bool(args['wait_id']), 'exact-current-wait-selector-required')
        owner = self.store.owner(db, auth)
        need(policy['owner'] == dict(client=owner['client'], generation=owner['generation']),
             'exact-controlled-lead-owner-required')
        task = db.execute('select * from tasks where revision=?', (policy['revision'],)).fetchone()
        need(task and json.loads(task['body'])['card'] == policy['card']
             and json.loads(task['body'])['session_id'] == self.session_id, 'goal-task-mismatch')
        if active_required:
            need(self.store.current(db)['revision'] == task['revision'] and task['phase'] == 'active', 'goal-assignment-fenced')
        bound = db.execute('select * from managed_wait_goal where revision=?', (policy['revision'],)).fetchone()
        if bound:
            need(bound['wait_id'] == args['wait_id'] and bound['installation_sha256'] == self._goal_installation.pin,
                 'goal-wait-binding-immutable')
            candidates = db.execute('select * from waits where wait_id=?', (bound['wait_id'],)).fetchall()
        else:
            candidates = db.execute("select w.* from waits w join effects e on e.effect_id=w.effect_id "
                "where w.revision=? and w.generation=? and w.session_id=? and w.phase in ('pending','ready') "
                "and w.completions=0 and e.kind=? and e.operation_key=? and e.target=?",
                (policy['revision'], owner['generation'], policy['ci_kind'],
                 policy['ci_operation_key'], wire(policy['ci_target']))).fetchall()
        need(len(candidates) == 1 and candidates[0]['wait_id'] == args['wait_id'],
             'one-unique-real-current-ci-wait-required')
        selected = candidates[0]
        effect = db.execute('select * from effects where effect_id=?', (selected['effect_id'],)).fetchone()
        need(effect and effect['kind'] == policy['ci_kind']
             and effect['operation_key'] == policy['ci_operation_key']
             and effect['target'] == wire(policy['ci_target']) and selected['revision'] == policy['revision']
             and selected['generation'] == owner['generation'] and selected['session_id'] == self.session_id,
             'wait-not-exact-installed-ci-scope')
        return dict(policy, wait_id=selected['wait_id']), owner, task

    def _pending(self, auth, args):
        connection = self.native
        need(connection is not None, 'existing-origin-connection-required-no-reconnect')
        with self.store.tx() as db:
            policy, owner, task = self._policy(db, auth, args)
            wait = db.execute('select * from waits where wait_id=?', (policy['wait_id'],)).fetchone()
            payload = db.execute('select * from checkpoint_wait_payload where wait_id=?', (policy['wait_id'],)).fetchone()
            need(wait and payload and wait['phase'] in ('pending', 'ready') and wait['completions'] == 0
                 and wait['session_id'] == self.session_id and wait['revision'] == task['revision']
                 and wait['generation'] == owner['generation'] and payload['epoch'] == task['capability_epoch']
                 and wait['connection_id'] == connection.connection_id, 'same-genuine-unconsumed-native-wait-required')
            original = json.loads(payload['raw_request'])
            self._live_binding(db, original['params'], connection)
            need(db.execute('select 1 from native_tool_operations where turn_id=?', (wait['turn_id'],)).fetchone() is None,
                 'wait-tool-or-reply-already-consumed')
            selected = dict(wait)
            wire_proof = original_pending_wire(db, selected, dict(payload), connection.connection_id)
        result = connection.rpc('thread/read', dict(threadId=self.session_id, includeTurns=True))
        thread = result['thread']
        need(thread.get('id') == self.session_id and thread.get('cwd') == self.cfg['physical_cwd'], 'goal-native-thread-mismatch')
        turns = [turn for turn in thread.get('turns', []) if turn.get('id') == selected['turn_id']]
        need(len(turns) == 1 and turns[0].get('status') == 'inProgress', 'goal-must-not-activate-idle')
        # History may omit a pending dynamic call. Original authenticated
        # connection wire and the unconsumed ledger binding are its evidence;
        # live thread/read still independently proves this exact turn runs.
        with self.store.tx() as db:
            _, _, current = self._policy(db, auth, args)
            fresh = db.execute('select * from waits where wait_id=?', (selected['wait_id'],)).fetchone()
            need(fresh and fresh['phase'] in ('pending', 'ready') and fresh['completions'] == 0
                 and fresh['connection_id'] == connection.connection_id
                 and current['capability_epoch'] == payload['epoch'], 'pending-wire-binding-changed')
            self._live_binding(db, original['params'], connection)
            need(db.execute('select 1 from native_tool_operations where turn_id=?',
                            (selected['turn_id'],)).fetchone() is None, 'pending-wire-reply-consumed')
            self.store.event(db, 'goal-original-wire-pending-and-live-turn-observed',
                             dict(wait_id=selected['wait_id'], wire=wire_proof,
                                  turn_readback_semantic_sha256=digest(wire(result))))
        return policy, selected, connection

    def _get_goal(self, connection):
        return connection.rpc('thread/goal/get', dict(threadId=self.session_id)).get('goal')

    def _same_goal(self, actual, row, status):
        need(type(actual) is dict and actual.get('threadId') == self.session_id
             and actual.get('objective') == row['objective'] and actual.get('tokenBudget') == row['budget']
             and actual.get('status') == status, 'goal-readback-not-exact-managed-state')

    def _set_once(self, auth, args, action, params, expected_status):
        with self.store.tx() as db:
            policy, owner, _ = self._policy(db, auth, args, active_required=action != 'pause')
            row = db.execute('select * from managed_wait_goal where revision=?', (policy['revision'],)).fetchone()
            need(row and row['installation_sha256'] == self._goal_installation.pin, 'managed-goal-required')
            key = 'native-goal:' + self.session_id + ':' + str(policy['revision']) + ':' + action
            if action == 'activate':
                pause_key = 'native-goal:' + self.session_id + ':' + str(policy['revision']) + ':pause'
                prior_pause = db.execute("select effect_id from effects where operation_key=? and phase='observed'",
                                         (pause_key,)).fetchone()
                if prior_pause:
                    key += ':after-observed-pause:' + prior_pause['effect_id']
            old = db.execute('select effect_id from effects where operation_key=?', (key,)).fetchone()
            if old:
                return dict(action='hold', reason='goal-mutation-consumed-readback-only', effect_id=old['effect_id'])
            effect_id = secrets.token_hex(24)
            self.store._index_effect(db, effect_id, policy['revision'], owner['generation'], key,
                'native-goal-' + action, dict(session_id=self.session_id, wait_id=policy['wait_id'], params=params), 'unknown')
            db.execute("update managed_wait_goal set phase='pause-incomplete' where revision=?", (policy['revision'],))
            self.store.event(db, 'goal-mutation-consumed-before-native-request',
                             dict(effect_id=effect_id, action=action, params=params))
        connection = self._role_connection()
        result = connection.rpc('thread/goal/set', params, role_key=self._role_key)
        self._same_goal(result.get('goal'), row, expected_status)
        actual = self._get_goal(connection)
        self._same_goal(actual, row, expected_status)
        if expected_status == 'active':
            self._pending(auth, args)  # Race/terminal after set => keep pause/HOLD.
        with self.store.tx() as db:
            self._policy(db, auth, args, active_required=action != 'pause')
            db.execute('update managed_wait_goal set phase=? where revision=?', (expected_status, policy['revision']))
            db.execute("update effects set phase='observed',observation=? where effect_id=?", (wire(actual), effect_id))
            self.store.event(db, 'goal-state-native-readback', dict(effect_id=effect_id, status=expected_status))
        return dict(action='observed', native_goal_status=expected_status, broker_paused=True, effect_id=effect_id)

    def _prepare(self, auth, args):
        self.store.pause(auth, True)  # Commit before any native Goal mutation.
        policy, wait, connection = self._pending(auth, args)
        actual = self._get_goal(connection)
        with self.store.tx() as db:
            self._policy(db, auth, args)
            row = db.execute('select * from managed_wait_goal where revision=?', (policy['revision'],)).fetchone()
            if row:
                return dict(action='hold', reason='goal-preparation-already-recorded-readback-only')
            need(actual is None, 'do-not-overwrite-existing-native-goal')
            objective = ('For current task revision ' + str(policy['revision']) + ' and existing wait '
                         + wait['wait_id'] + ', consume only its independently verified existing CI readback. '
                         'Finish the current read and stop; no new task, tool, dispatch or external effect.')
            db.execute('insert into managed_wait_goal values(?,?,?,?,?,?)',
                       (policy['revision'], wait['wait_id'], self._goal_installation.pin, objective,
                        policy['token_budget'], 'pause-incomplete'))
        return self._set_once(auth, args, 'prepare-paused', dict(threadId=self.session_id,
            objective=objective, tokenBudget=policy['token_budget'], status='paused', origin='automatic'), 'paused')

    def _activate(self, auth, args):
        self.store.pause(auth, True)
        _, _, connection = self._pending(auth, args)
        with self.store.tx() as db:
            policy, _, _ = self._policy(db, auth, args)
            row = db.execute('select * from managed_wait_goal where revision=?', (policy['revision'],)).fetchone()
            need(row and row['phase'] == 'paused', 'native-paused-goal-must-be-established-first')
        self._same_goal(self._get_goal(connection), row, 'paused')
        return self._set_once(auth, args, 'activate', dict(threadId=self.session_id, status='active', origin='automatic'), 'active')

    def _pause(self, auth, args):
        self.store.pause(auth, True)
        # No pending-turn prerequisite for stopping the exact owned Goal after a fence.
        connection = self._role_connection()
        with self.store.tx() as db:
            policy, _, _ = self._policy(db, auth, args, active_required=False)
            row = db.execute('select * from managed_wait_goal where revision=?', (policy['revision'],)).fetchone()
            need(row, 'no-managed-goal-to-pause')
        actual = self._get_goal(connection)
        need(type(actual) is dict and actual.get('objective') == row['objective']
             and actual.get('tokenBudget') == row['budget'] and actual.get('threadId') == self.session_id,
             'cannot-pause-foreign-goal')
        if actual.get('status') == 'paused':
            with self.store.tx() as db:
                db.execute("update managed_wait_goal set phase='paused' where revision=?", (policy['revision'],))
            return dict(action='observed', broker_paused=True, native_goal_status='paused')
        return self._set_once(auth, args, 'pause', dict(threadId=self.session_id, status='paused', origin='automatic'), 'paused')

    def handle(self, request):
        command = request.get('command')
        with self.execution_gate:
            auth, args = request.get('auth'), request.get('arguments', {})
            if command in ('goal.prepare', 'goal.activate', 'goal.pause'):
                if command == 'goal.prepare':
                    return self._prepare(auth, args)
                if command == 'goal.activate':
                    return self._activate(auth, args)
                return self._pause(auth, args)
            with self.store.tx() as db:
                managed = db.execute("select * from managed_wait_goal where phase!='retired' order by revision desc limit 1").fetchone()
            if managed:
                if command == 'pause':
                    return self._pause(auth, dict(revision=managed['revision'], wait_id=managed['wait_id']))
                if command in ('role.dispatch-current', 'effect.prepare', 'effect.observe-existing',
                               'branch.prepare', 'branch.issue', 'assignment.publish'):
                    return dict(action='hold', reason='managed-goal-cannot-create-dispatch-effect-or-new-assignment')
                if command == 'resume':
                    self._pending(auth, dict(revision=managed['revision'], wait_id=managed['wait_id']))
                    need(managed['phase'] == 'active', 'native-goal-active-readback-required-before-explicit-resume')
                    self._same_goal(self._get_goal(self.native), managed, 'active')
                if command == 'wait.drain':
                    self._pending(auth, dict(revision=managed['revision'], wait_id=managed['wait_id']))
            return super().handle(request)
