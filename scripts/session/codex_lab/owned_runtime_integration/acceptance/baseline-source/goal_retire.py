"""Readback-only managed Goal retirement; retains native Goal and all history."""
import json
import hashlib

from reconnect_observe import ObserveStore, ObserveSupport
from native_role import canonical_uuid
from runtime_store import need, wire, digest

TERMINAL = {'completed', 'interrupted', 'failed'}
LIMIT = 8192


class RetireStore(ObserveStore):
    def __init__(self, directory, installation):
        super().__init__(directory, installation)
        with self.tx() as db:
            pass  # Existing-only startup: schema was independently verified; no DDL.


class RetireSupport(ObserveSupport):
    def _retire_binding(self, db, auth, args):
        need(type(args) is dict and set(args) == {'revision', 'wait_id'}, 'exact-managed-goal-retirement-selector')
        owner = self.store.owner(db, auth)
        need(owner['paused'], 'durable-pause-required-for-retirement')
        row = db.execute('select * from managed_wait_goal where revision=?', (args['revision'],)).fetchone()
        need(row and row['wait_id'] == args['wait_id'], 'exact-managed-goal-history-required')
        if row['phase'] == 'retired':
            audit = db.execute('select * from goal_retirement where revision=?', (args['revision'],)).fetchone()
            need(audit and audit['wait_id'] == args['wait_id'], 'retirement-audit-missing')
            return dict(row), None, None, dict(audit)
        policy, _, task = self._policy(db, auth, args, active_required=False)
        wait = db.execute('select * from waits where wait_id=?', (args['wait_id'],)).fetchone()
        need(wait and wait['session_id'] == self.session_id and wait['revision'] == row['revision']
             and wait['generation'] == owner['generation'] and wait['phase'] == 'completed'
             and wait['completions'] == 1, 'original-wait-must-be-once-consumed-and-completed')
        effect = db.execute('select * from effects where effect_id=?', (wait['effect_id'],)).fetchone()
        turn = db.execute('select * from turns where turn_id=?', (wait['turn_id'],)).fetchone()
        need(effect and effect['advances'] == 1 and turn and turn['state'] == 'terminal',
             'wait-continuation-and-terminal-ledger-required')
        unknown = db.execute("select effect_id from effects where lower(phase) like '%unknown%'").fetchall()
        need(not unknown, 'unknown-effect-blocks-goal-retirement')
        need(db.execute("select 1 from waits where phase in ('pending','ready','delivery-unknown')").fetchone() is None,
             'unresolved-wait-blocks-goal-retirement')
        return dict(row), dict(wait), dict(task), None

    def _observed_turns(self, db, revision, wait_turn):
        key = 'native-goal:' + self.session_id + ':' + str(revision) + ':prepare-paused'
        prepare = db.execute('select effect_id from effects where operation_key=?', (key,)).fetchone()
        need(prepare, 'original-goal-consumed-effect-required')
        mutations = db.execute("select seq,body from events where kind='goal-mutation-consumed-before-native-request' "
                               'order by seq desc limit ?', (LIMIT + 1,)).fetchall()
        need(len(mutations) <= LIMIT, 'goal-origin-window-unproved')
        matching = [r for r in mutations if json.loads(r['body']).get('effect_id') == prepare['effect_id']]
        need(len(matching) == 1, 'unique-original-goal-start-receipt-required')
        start = matching[0]['seq']
        receipts = db.execute("select seq,body from events where kind='actual-native-wire-receipt' and seq>=? "
                              'order by seq limit ?', (start, LIMIT + 1)).fetchall()
        need(len(receipts) <= LIMIT, 'goal-observation-window-incomplete')
        turns = {canonical_uuid(wait_turn)}
        evidence = []
        for row in receipts:
            record = json.loads(row['body'])
            if 'raw_utf8' not in record:
                continue
            raw = record['raw_utf8']
            need(type(raw) is str and hashlib.sha256(raw.encode('utf-8')).hexdigest() == record.get('raw_sha256'),
                 'goal-observation-wire-integrity')
            message = json.loads(raw)
            if message.get('method') != 'turn/started':
                continue
            params = message.get('params', {})
            if params.get('threadId') != self.session_id:
                continue
            turn_id = canonical_uuid(params['turn']['id'])
            turns.add(turn_id)
            evidence.append(dict(seq=row['seq'], turn_id=turn_id, raw_sha256=record['raw_sha256']))
        return turns, evidence

    def _goal_snapshot(self, connection, managed):
        goal = self._get_goal(connection)
        need(type(goal) is dict and goal.get('threadId') == self.session_id
             and goal.get('objective') == managed['objective'] and goal.get('tokenBudget') == managed['budget']
             and goal.get('status') in ('paused', 'complete'), 'native-goal-not-owned-paused-or-complete')
        return goal

    def _loaded_snapshot(self, connection):
        result = connection.rpc('thread/loaded/list', {})
        need(type(result) is dict and 'nextCursor' in result and result['nextCursor'] is None
             and type(result.get('data')) is list and len(result['data']) <= 64
             and all(type(value) is str for value in result['data'])
             and len(set(result['data'])) == len(result['data']), 'complete-loaded-list-required')
        return result

    def _retire(self, auth, args):
        with self.store.tx() as db:
            managed, wait, _, audit = self._retire_binding(db, auth, args)
            if audit:
                return dict(action='retired', revision=managed['revision'], wait_id=managed['wait_id'],
                            retirement_seq=audit['seq'], repeated_read_only=True, native_goal_changed=False)
        connection = self._role_connection()  # Existing pinned proxy; no resume/start/Goal set.
        connection.check_endpoint()
        loaded_before = self._loaded_snapshot(connection)
        before = self._goal_snapshot(connection, managed)
        result = connection.rpc('thread/read', dict(threadId=self.session_id, includeTurns=True))
        thread = result['thread']
        need(thread.get('id') == self.session_id and thread.get('cwd') == self.cfg['physical_cwd']
             and thread.get('status', {}).get('type') in ('idle', 'notLoaded'),
             'native-thread-not-idle-or-readonly-notloaded')
        actual = thread.get('turns')
        need(type(actual) is list and all(type(t) is dict and t.get('status') in TERMINAL for t in actual)
             and len({t['id'] for t in actual}) == len(actual), 'visible-native-turns-not-uniquely-terminal')
        after = self._goal_snapshot(connection, managed)
        loaded_after = self._loaded_snapshot(connection)
        connection.check_endpoint()
        status = thread['status']['type']
        if status == 'notLoaded':
            need(self.session_id not in loaded_before['data'] and self.session_id not in loaded_after['data'],
                 'notloaded-thread-was-loaded-during-retirement')
        else:
            need(self.session_id in loaded_before['data'] and self.session_id in loaded_after['data'],
                 'idle-loaded-binding-moved-during-retirement')
        need(all(before.get(k) == after.get(k) for k in ('threadId', 'objective', 'tokenBudget', 'status', 'createdAt')),
             'native-goal-moved-during-retirement-readback')
        with self.store.tx() as db:
            current, current_wait, _, raced_audit = self._retire_binding(db, auth, args)
            need(raced_audit is None and current == managed and current_wait == wait, 'retirement-binding-moved')
            observed, started = self._observed_turns(db, managed['revision'], wait['turn_id'])
            by_id = {t['id']: t for t in actual}
            need(observed <= set(by_id) and all(by_id[t]['status'] in TERMINAL for t in observed),
                 'observed-goal-successor-terminal-proof-missing')
            evidence = dict(session_id=self.session_id, revision=managed['revision'], wait_id=wait['wait_id'],
                original_wait_turn=wait['turn_id'], observed_turns=sorted(observed), started_wire=started,
                native_goal_status=after['status'], native_thread_status=status,
                same_live_connection_id=connection.connection_id,
                loaded_before_semantic_sha256=digest(wire(loaded_before)),
                loaded_after_semantic_sha256=digest(wire(loaded_after)),
                goal_before_semantic_sha256=digest(wire(before)),
                goal_after_semantic_sha256=digest(wire(after)), thread_readback_semantic_sha256=digest(wire(result)),
                native_goal_changed=False, budget_changed=False, independent_acceptance=False)
            seq = self.store.event(db, 'managed-goal-retired-after-native-terminal-readback', evidence)
            db.execute('insert into goal_retirement values(?,?,?,?)',
                       (managed['revision'], wait['wait_id'], wire(evidence), seq))
            db.execute("update managed_wait_goal set phase='retired' where revision=?", (managed['revision'],))
        return dict(action='retired', revision=managed['revision'], wait_id=wait['wait_id'],
                    retirement_seq=seq, observed_terminal_turns=sorted(observed), native_goal_changed=False,
                    broker_paused=True, next_assignment_published=False, independent_acceptance=False)

    def handle(self, request):
        command = request.get('command')
        with self.execution_gate:
            auth, args = request.get('auth'), request.get('arguments', {})
            if command == 'goal.retire':
                return self._retire(auth, args)
            if command in ('goal.prepare', 'goal.activate', 'goal.pause'):
                with self.store.tx() as db:
                    self.store.owner(db, auth)
                    row = db.execute('select phase from managed_wait_goal where revision=?',
                                     (args.get('revision'),)).fetchone()
                    need(not row or row['phase'] != 'retired', 'retired-goal-cannot-reactivate-or-mutate')
            return super().handle(request)
