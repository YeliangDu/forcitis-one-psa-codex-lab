"""Single R1/R2 broker truth. No canonical queue, merge journal or writer API."""
import contextlib
import hashlib
import hmac
import json
import os
from pathlib import Path
import secrets
import sqlite3
import threading


def need(ok, reason):
    if not ok:
        raise ValueError(reason)


def wire(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), allow_nan=False)


def digest(value):
    return hashlib.sha256(value.encode()).hexdigest()


class Store:
    def __init__(self, directory, installation):
        self.directory = Path(directory)
        self.installation = installation
        self.binding = installation['binding']
        self.session_id = self.binding.view()['native']['expected_registered_session']
        self.mutex = threading.RLock()
        from startup_authority import GuardedRuntime
        resource = installation.get('_existing_runtime')
        need(type(resource) is GuardedRuntime and Path(directory) == resource.directory,
             'verified-existing-runtime-resource-required-no-bootstrap')
        self.db = resource.open_existing()
        self.db.row_factory = sqlite3.Row
        self.db.execute('pragma synchronous=full')
        self.db.execute('pragma foreign_keys=on')
        row = self.db.execute('select pin from installation where id=1').fetchone()
        need(row is not None and row['pin'] == installation['pin'],
             'existing-installation-required-no-insert-or-reset')

    @contextlib.contextmanager
    def tx(self):
        with self.mutex:
            self.db.execute('begin immediate')
            try:
                yield self.db
                self.db.execute('commit')
            except BaseException:
                self.db.execute('rollback')
                raise

    def event(self, db, kind, body):
        return db.execute('insert into events(kind,body) values(?,?)', (kind, wire(body))).lastrowid

    def owner(self, db, auth):
        row = db.execute('select * from owner where id=1').fetchone()
        need(type(auth) is dict and type(auth.get('generation')) is int
             and auth['generation'] == row['generation'] and auth.get('client') == row['client']
             and row['token_sha'] is not None and type(auth.get('token')) is str
             and hmac.compare_digest(digest(auth['token']), row['token_sha']), 'stale-or-unauthorized-owner')
        return row

    def current(self, db):
        return db.execute('select * from tasks order by revision desc limit 1').fetchone()

    def binding(self, db, auth, revision, *, paused_ok=False):
        owner = self.owner(db, auth)
        task = self.current(db)
        need(task is not None and type(revision) is int and task['revision'] == revision
             and task['phase'] == 'active', 'stale-or-fenced-assignment')
        need(paused_ok or not owner['paused'], 'durable-pause')
        return owner, task

    def acquire(self, client, expected_generation):
        need(type(client) is str and 0 < len(client) <= 128 and type(expected_generation) is int,
             'invalid-owner-request')
        with self.tx() as db:
            old = db.execute('select * from owner where id=1').fetchone()
            need(old['client'] is None and old['generation'] == expected_generation, 'owner-cas-lost')
            generation = expected_generation + 1
            token = secrets.token_hex(32)
            db.execute('update owner set generation=?,client=?,token_sha=? where id=1',
                       (generation, client, digest(token)))
            self.event(db, 'owner-granted', dict(client=client, generation=generation))
            return dict(client=client, generation=generation, token=token)

    def release(self, auth):
        with self.tx() as db:
            owner = self.owner(db, auth)
            self._fence(db, 'owner-release')
            db.execute('update owner set client=null,token_sha=null,paused=1 where id=1')
            self.event(db, 'owner-released', dict(generation=owner['generation']))
        return dict(action='released', requires_explicit_acquire=True)

    def _fence(self, db, reason):
        task = self.current(db)
        if task is None or task['phase'] != 'active':
            return None
        revision = task['revision']
        revoking = self.event(db, 'assignment-revoking', dict(revision=revision, reason=reason))
        db.execute("update tasks set phase='revoking' where revision=?", (revision,))
        # Every broker operation rechecks this persisted epoch at execution.
        db.execute("update tasks set capability_epoch=capability_epoch+1 where revision=?", (revision,))
        fenced = self.event(db, 'execution-fenced', dict(revision=revision))
        db.execute("update tasks set phase='fenced',fence_seq=? where revision=?", (fenced, revision))
        db.execute("update turns set state='quarantined-unresolved' where revision=? and state!='terminal'", (revision,))
        db.execute("update waits set phase='obsolete' where revision=? and phase!='completed'", (revision,))
        return dict(revoking_seq=revoking, fenced_seq=fenced, revision=revision,
                    old_turn_state='quarantined-unresolved')

    def revoke(self, auth):
        with self.tx() as db:
            self.owner(db, auth)
            result = self._fence(db, 'explicit-revocation')
            db.execute('update owner set paused=1 where id=1')
            return result or dict(action='no-active-assignment')

    def allowed_session(self, session_id):
        return session_id == self.installation['native_session']

    def publish(self, auth, body):
        required = {'card', 'revision', 'role', 'session_id', 'physical_cwd', 'task', 'A', 'B', 'H'}
        need(type(body) is dict and set(body) == required, 'exact-assignment-required')
        need(type(body['card']) is int and body['card'] > 0 and type(body['revision']) is int
             and body['revision'] > 0 and type(body['task']) is str and 0 < len(body['task']) <= 2048,
             'invalid-assignment')
        cfg = self.installation
        need(self.allowed_session(body['session_id']) and body['role'] == cfg['role']
             and body['physical_cwd'] == cfg['physical_cwd'], 'installation-binding-mismatch')
        for key in ('A', 'B', 'H'):
            value = body[key]
            need(type(value) is str and len(value) == 40 and all(c in '0123456789abcdef' for c in value),
                 'exact-source-identity-required')
        need(str(Path(body['physical_cwd']).resolve(strict=True)) == body['physical_cwd'], 'nonphysical-cwd')
        with self.tx() as db:
            self.owner(db, auth)
            old = self.current(db)
            need(old is None or (old['phase'] == 'fenced' and body['revision'] > old['revision']),
                 'revoke-before-publish')
            seq = self.event(db, 'assignment-published', body)
            db.execute('insert into tasks values(?,?,?,1,null)', (body['revision'], wire(body), 'active'))
            # Publish does not clear a user/owner pause.
            return dict(action='published', sequence=seq, paused=True)

    def pause(self, auth, paused):
        need(type(paused) is bool, 'explicit-pause-required')
        with self.tx() as db:
            self.owner(db, auth)
            db.execute('update owner set paused=? where id=1', (int(paused),))
            seq = self.event(db, 'paused' if paused else 'explicit-resume', {})
        return dict(paused=paused, sequence=seq)

    def prepare_effect(self, auth, revision, operation_key, kind, target):
        need(kind == 'ci-rerun' and type(operation_key) is str and 0 < len(operation_key) <= 256,
             'effect-kind-not-allowed')
        need(type(target) is dict and set(target) == {'repository', 'run_id', 'attempt', 'head'}
             and target['repository'] == 'YeliangDu/forcitis-one-psa-codex-lab'
             and type(target['run_id']) is int and target['run_id'] > 0
             and type(target['attempt']) is int and target['attempt'] > 0, 'exact-ci-target-required')
        # Physical rerun identity excludes caller key, owner and assignment.
        # Head is checked against the existing immutable target, not used to
        # mint another rerun of the same physical run/attempt.
        operation_key = digest(wire([kind, target['repository'], target['run_id'], target['attempt']]))
        with self.tx() as db:
            owner, task = self.binding(db, auth, revision)
            body = json.loads(task['body'])
            need(target['head'] == body['H'], 'effect-head-mismatch')
            existing = db.execute('select * from effects where operation_key=?', (operation_key,)).fetchone()
            if existing:
                need(existing['revision'] == revision and existing['kind'] == kind
                     and existing['target'] == wire(target), 'effect-key-rebound')
                return dict(existing)
            effect_id = secrets.token_hex(24)
            db.execute('insert into effects(effect_id,revision,generation,operation_key,kind,target,phase) '
                       'values(?,?,?,?,?,?,?)', (effect_id, revision, owner['generation'], operation_key,
                                               kind, wire(target), 'planned'))
            self.event(db, 'effect-planned', dict(effect_id=effect_id, revision=revision))
            return dict(db.execute('select * from effects where effect_id=?', (effect_id,)).fetchone())

    def begin_issue(self, auth, revision, effect_id):
        """Only internal transport may invoke after installing an approved executor."""
        with self.tx() as db:
            owner, _ = self.binding(db, auth, revision)
            effect = db.execute('select * from effects where effect_id=?', (effect_id,)).fetchone()
            need(effect is not None and effect['revision'] == revision and effect['generation'] == owner['generation']
                 and effect['phase'] == 'planned' and effect['issue_count'] == 0, 'effect-consumed-readback-only')
            db.execute("update effects set phase='unknown',issue_count=1 where effect_id=?", (effect_id,))
            self.event(db, 'effect-consumed-before-send', dict(effect_id=effect_id))
            return dict(effect)

    def register_wait(self, db, connection_id, envelope):
        """Internal native-connection entry. A CLI cannot supply this identity."""
        session, turn_id, call_id = (envelope[key] for key in ('threadId', 'turnId', 'callId'))
        turn = db.execute('select * from turns where turn_id=?', (turn_id,)).fetchone()
        task = self.current(db)
        owner = db.execute('select * from owner where id=1').fetchone()
        need(task is not None and turn is not None and task['phase'] == 'active'
             and session == self.installation['native_session'] and turn['revision'] == task['revision']
             and turn['epoch'] == task['capability_epoch'] and turn['owner_generation'] == owner['generation']
             and turn['state'] == 'active', 'obsolete-native-call')
        effect_id = envelope['arguments']['effect_id']
        effect = db.execute('select * from effects where effect_id=?', (effect_id,)).fetchone()
        need(effect is not None and effect['revision'] == task['revision'], 'wait-effect-mismatch')
        wait_id = digest(wire([session, turn_id, call_id]))
        db.execute('insert into waits values(?,?,?,?,?,?,?,?,?,0) on conflict(wait_id) do nothing',
                   (wait_id, effect_id, task['revision'], owner['generation'], session,
                    turn_id, call_id, connection_id, 'pending'))
        self.event(db, 'native-wait-registered', dict(wait_id=wait_id, effect_id=effect_id))
        return wait_id

    def archive(self, envelope):
        with self.tx() as db:
            return self.event(db, 'late-or-unbound-native-item', envelope)

    def observe_effect(self, effect_id, raw_observation):
        """Internal read-only adapter only; no arbitrary CLI completion Boolean."""
        with self.tx() as db:
            row = db.execute('select * from effects where effect_id=?', (effect_id,)).fetchone()
            need(row is not None, 'unknown-effect-id')
            self.event(db, 'effect-readback-observation', dict(effect_id=effect_id, observation=raw_observation))
            # Run/attempt observation is not authenticated causal attribution.
            db.execute('update effects set observation=? where effect_id=?', (wire(raw_observation), effect_id))
        return dict(action='hold', reason='independent-exact-effect-causality-not-bound', effect_id=effect_id)

    def snapshot(self, auth):
        with self.tx() as db:
            self.owner(db, auth)
            owner = dict(db.execute('select generation,client,paused from owner where id=1').fetchone())
            return dict(owner=owner, tasks=[dict(r) for r in db.execute('select * from tasks')],
                        effects=[dict(r) for r in db.execute('select * from effects')],
                        waits=[dict(r) for r in db.execute('select * from waits')],
                        events=[dict(r) for r in db.execute('select * from events order by seq')],
                        formal_enqueue_allowed=False, writer_allowed=False)

    def stage_notification(self, auth, delivery_id, body):
        """Untrusted hint only; duplicates never complete waits or clear pause."""
        need(type(delivery_id) is str and 0 < len(delivery_id) <= 256 and type(body) is dict,
             'bounded-notification-required')
        with self.tx() as db:
            self.owner(db, auth)
            old = db.execute('select body from inbox where delivery_id=?', (delivery_id,)).fetchone()
            need(old is None or old['body'] == wire(body), 'notification-id-rebound')
            if old is None:
                db.execute('insert into inbox values(?,?,?)', (delivery_id, wire(body), 'untrusted'))
                self.event(db, 'notification-staged', dict(delivery_id=delivery_id))
        return dict(action='staged', completion_authority=False)

    def drain(self, auth, revision):
        with self.tx() as db:
            owner, _ = self.binding(db, auth, revision, paused_ok=True)
            reason = 'durable-pause' if owner['paused'] else 'native-wait-and-hosted-verifier-not-bound'
            self.event(db, 'drain-held', dict(revision=revision, reason=reason))
            return dict(action='hold', reason=reason, advanced=0, completed_waits=0)
