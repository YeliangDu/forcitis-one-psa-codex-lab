"""Hosted-only boundary collection through exact public production serve/call.

No native connection, gh command, effect issuance or formal state is permitted.
One continuous selection from the existing frozen vertical scenario, not a new
controller or replacement native/CI integration test.
"""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import signal
import sqlite3
import subprocess
import sys
import tempfile
import time

PINS = {'runtime_broker.py': '519a0b87c9e6b4d499630dec3255f525a50922b20d7e3ed8a124082f1756f44e', 'runtime_store.py': '7c259a800d5fa263cb0ff1fa595595864da8f09a1b77f0d48f78561ee5f7639d', 'native_transport.py': '32e4b310ad69d066e35d22ddda9e5263f9dc68a7023163bb92214f0710c3045a'}
SCENARIO_SHA = '7c778e9bc57f56bb187b1355131816c7e30f553af9c5f6c997a6c1cd4e602b48'
SESSION = '01a1be37-1c95-71e0-ae7d-3947f3fb9cac'
B = '77e872e8ba9661d3294d3a9337c8e5c956b70641'
H = '47e263825afb62a1b860915f8293da79572d12f6'


class Hold(RuntimeError):
    pass


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def check(condition, label):
    if not condition:
        raise AssertionError(label)


def wait(predicate, label):
    end = time.monotonic() + 8
    while time.monotonic() < end:
        if predicate():
            return
        time.sleep(.02)
    raise Hold('missing fixture barrier: ' + label)


def redact(value):
    if type(value) is dict:
        return {k: ('<redacted-ephemeral-owner-capability>' if k in ('token', 'token_sha') else redact(v)) for k, v in value.items()}
    if type(value) is list:
        return [redact(v) for v in value]
    return value


def exercise(source, evidence, parent_netns):
    for name, pin in PINS.items():
        path = source / name
        if path.resolve(strict=True) != path or digest(path.read_bytes()) != pin:
            raise Hold('source pin/physical file differs: ' + name)
    if sys.platform != 'linux' or os.geteuid() == 0:
        raise Hold('unprivileged GitHub-hosted Linux required')
    if os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted':
        raise Hold('external GitHub-hosted launcher required')
    namespace = os.readlink('/proc/self/ns/net')
    if namespace == parent_netns:
        raise Hold('private network namespace required')
    ip = next((name for name in ('/usr/sbin/ip', '/usr/bin/ip') if Path(name).is_file()), None)
    if ip is None:
        raise Hold('fixed absolute ip executable missing')
    links = subprocess.check_output([ip, '-o', 'link', 'show'], text=True, timeout=5,
                                    env=dict(PATH='/usr/bin:/bin', LANG='C.UTF-8'))
    names = [line.split(':', 2)[1].strip().split('@', 1)[0] for line in links.splitlines() if line.strip()]
    if names != ['lo'] or os.readlink('/proc/self/ns/net') != namespace:
        raise Hold('only loopback in unchanged private network namespace required')
    if any(os.environ.get(name) for name in ('GH_TOKEN', 'GITHUB_TOKEN', 'SSH_AUTH_SOCK', 'AWS_ACCESS_KEY_ID', 'OPENAI_API_KEY')):
        raise Hold('credential-bearing environment refused')
    # Collector credential environment is never inherited by broker/clients.
    env = dict(PATH='/usr/bin:/bin', LANG='C.UTF-8', PYTHONDONTWRITEBYTECODE='1')
    transcript = []; snapshots = []
    server = None
    with tempfile.TemporaryDirectory(prefix='r1-r2-hosted-') as temporary:
        root = Path(temporary).resolve(); root.chmod(0o700)
        env['HOME'] = str(root)
        cwd = root / 'forcitis-one-psa-codex-lab'; cwd.mkdir(mode=0o700)
        runtime = root / 'r1-r2-runtime'; runtime.mkdir(mode=0o700)
        sock = runtime / 'broker.sock'
        # Dormant literal required by exact candidate installation validator only.
        # No endpoint pin is read/copied and native.inspect is never invoked.
        config = dict(native_endpoint='/tmp/lab60-pilot.cnCGuJHo/server.sock', native_session=SESSION,
                      physical_cwd=str(cwd), role='one-lead', proxy_binary='/usr/bin/false',
                      gh_binary='/usr/bin/false', runtime_directory=str(runtime))
        installation = root / 'installation.json'; installation.write_text(json.dumps(config)); installation.chmod(0o600)
        cfg_pin = digest(installation.read_bytes())
        entry = source / 'runtime_broker.py'
        counter = 0
        def request(command, arguments=None, auth=None):
            nonlocal counter
            counter += 1
            body = dict(command=command, arguments=arguments or {})
            if auth is not None:
                body['auth'] = auth
            path = root / ('request-' + str(counter) + '.json')
            path.write_text(json.dumps(body)); path.chmod(0o600)
            return body, path
        def argv(path):
            return [sys.executable, '-B', str(entry), 'call', '--socket', str(sock), '--request', str(path)]
        def record(body, completed, pid=None):
            if completed.returncode != 0:
                raise Hold('public CLI failed: ' + completed.stderr[:1200])
            try:
                value = json.loads(completed.stdout)
            except ValueError as error:
                raise Hold('CLI did not produce raw JSON') from error
            transcript.append(dict(index=len(transcript), command=body['command'], request=redact(body),
                                   response=redact(value), raw_response_sha256=digest(completed.stdout.encode()),
                                   process_pid=pid, returncode=completed.returncode))
            (evidence / 'cli-transcript.json').write_text(json.dumps(transcript, indent=2) + '\n')
            return value
        def call(command, arguments=None, auth=None):
            body, path = request(command, arguments, auth)
            completed = subprocess.run(argv(path), cwd=cwd, env=env, capture_output=True, text=True, timeout=10)
            return record(body, completed)
        def launch():
            nonlocal server
            server = subprocess.Popen([sys.executable, '-B', str(entry), 'serve', '--installation', str(installation),
                                       '--installation-sha256', cfg_pin], cwd=cwd, env=env,
                                      stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)
            wait(lambda: sock.exists() or server.poll() is not None, 'actual public serve socket')
            if server.poll() is not None:
                raise Hold('public serve exited before socket barrier')
        def stop(restart=False):
            nonlocal server
            if server is not None and server.poll() is None:
                server.terminate()
                try:
                    server.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    os.killpg(server.pid, signal.SIGKILL); server.wait(timeout=5)
                    raise Hold('scratch broker did not terminate normally')
            if restart and sock.exists():
                # Exact known-owned scratch process is dead. Prove lock released
                # before operator removal of its stale scratch socket, not recovery code.
                before = sock.lstat()
                with (runtime / 'broker.lock').open('r+') as lock:
                    fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                    now = sock.lstat()
                    check((before.st_dev, before.st_ino) == (now.st_dev, now.st_ino), 'SCRATCH_SOCKET_IDENTITY_MOVED')
                    sock.unlink()
                    fcntl.flock(lock, fcntl.LOCK_UN)
        def snapshot(auth):
            value = call('inspect', auth=auth)
            check('owner' in value and 'effects' in value, 'INSPECT_NOT_CURRENT_OWNER')
            snapshots.append(value)
            return value
        def prepare(auth, revision, key):
            return call('effect.prepare', dict(revision=revision, operation_key=key, kind='ci-rerun',
                        target=dict(repository='YeliangDu/forcitis-one-psa-codex-lab', run_id=42561999740,
                                    attempt=1, head=H)), auth)
        def assignment(revision):
            return dict(card=60, revision=revision, role='one-lead', session_id=SESSION,
                        physical_cwd=str(cwd), task='same frozen R1/R2 hosted boundary selection',
                        A='5bc6987f64187dc47431c0bd73c414a0464db4e2', B=B, H=H)
        try:
            launch()
            # Two independent OS processes rendezvous then exec the unchanged CLI.
            processes = []; pending = []
            for name in ('client-a', 'client-b'):
                body, path = request('owner.acquire', dict(client=name, expected_generation=0))
                gate = [sys.executable, '-B', str(Path(__file__).resolve()), '--client-gate', *argv(path)]
                process = subprocess.Popen(gate, cwd=cwd, env=env, stdin=subprocess.PIPE,
                                           stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
                processes.append(process); pending.append(body)
            for process in processes:
                process.stdin.write('G'); process.stdin.flush(); process.stdin.close(); process.stdin = None
            results = []
            for body, process in zip(pending, processes):
                output, errors = process.communicate(timeout=10)
                results.append(record(body, subprocess.CompletedProcess(process.args, process.returncode, output, errors), process.pid))
            winners = [v for v in results if v.get('action') == 'granted']
            check(len(winners) == 1 and len([v for v in results if v.get('action') == 'hold']) == 1,
                  'TWO_CLIENTS_GRANTED_OWNER')
            auth = winners[0]['auth']
            check(auth['generation'] == 1, 'OWNER_GENERATION_NOT_PERSISTENT')
            first = call('assignment.publish', dict(assignment=assignment(1)), auth)
            check(first.get('action') == 'published', 'INITIAL_ASSIGNMENT_NOT_PUBLISHED')
            check(call('assignment.publish', dict(assignment=assignment(2)), auth).get('action') == 'hold',
                  'REVISION_PUBLISHED_BEFORE_EXECUTION_FENCE')
            check(prepare(auth, 1, 'paused-key').get('action') == 'hold', 'DURABLE_PAUSE_BYPASSED')
            call('resume', auth=auth)
            effect = prepare(auth, 1, 'key-one')
            check(effect.get('phase') == 'planned', 'PERMITTED_EFFECT_NOT_CREATED')
            repeated = prepare(auth, 1, 'different-key')
            check(repeated.get('effect_id') == effect['effect_id'], 'PHYSICAL_RERUN_REPLANNED_BY_KEY')
            check(call('effect.issue', dict(effect_id=effect['effect_id'], revision=1), auth).get('action') == 'hold',
                  'UNAUTHORIZED_EXTERNAL_ISSUE')
            call('pause', auth=auth)
            for _ in range(2):
                call('notification.stage', dict(delivery_id='duplicate-hint', body=dict(run_id=42561999740, attempt=1)), auth)
            drained = call('wait.drain', dict(revision=1), auth)
            check(drained.get('advanced') == 0 and drained.get('completed_waits') == 0, 'PAUSE_ADVANCED')
            before = snapshot(auth)
            check(before['owner']['paused'] == 1 and len(before['effects']) == 1, 'BACKGROUND_EVENT_CLEARED_PAUSE')
            lock_identity = ((runtime / 'broker.lock').stat().st_dev, (runtime / 'broker.lock').stat().st_ino)
            stop(restart=True); launch()
            persisted = snapshot(auth)
            check(persisted['owner']['paused'] == 1 and persisted['effects'] == before['effects'], 'PAUSE_OR_EFFECT_LOST_ON_RESTART')
            check(((runtime / 'broker.lock').stat().st_dev, (runtime / 'broker.lock').stat().st_ino) == lock_identity,
                  'BROKER_LOCK_INODE_REPLACED')
            revoked = call('assignment.revoke', auth=auth)
            published = call('assignment.publish', dict(assignment=assignment(2)), auth)
            check(revoked['revoking_seq'] < revoked['fenced_seq'] < published['sequence'], 'REVISION_PUBLISHED_BEFORE_EXECUTION_FENCE')
            check(snapshot(auth)['owner']['paused'] == 1, 'PUBLISH_CLEARED_PAUSE')
            call('resume', auth=auth)
            check(prepare(auth, 2, 'new-revision-key').get('action') == 'hold', 'PHYSICAL_RERUN_REPLANNED_BY_REVISION')
            call('owner.release', auth=auth)
            fresh = call('owner.acquire', dict(client='client-successor', expected_generation=1))['auth']
            check(fresh['generation'] == 2, 'SUCCESSOR_GENERATION_NOT_MONOTONIC')
            check(call('inspect', auth=auth).get('action') == 'hold', 'STALE_OWNER_NOT_FENCED')
            check(prepare(auth, 2, 'stale-owner-key').get('action') == 'hold', 'STALE_OWNER_ACCEPTED_EFFECT')
            check(call('assignment.publish', dict(assignment=assignment(3)), fresh).get('action') == 'published', 'SUCCESSOR_ASSIGNMENT_HELD')
            call('resume', auth=fresh)
            check(prepare(fresh, 3, 'new-owner-key').get('action') == 'hold', 'PHYSICAL_RERUN_REPLANNED_BY_GENERATION')
            for denied in ('enqueue', 'writer'):
                check(call(denied, auth=fresh).get('action') == 'hold', 'FORMAL_COMMAND_NOT_DENIED')
            check(call('plan', auth=fresh).get('action') == 'hold', 'UNBOUND_NATIVE_PLAN_CLAIMED_PASS')
            final = snapshot(fresh)
            check(len(final['effects']) == 1 and final['effects'][0]['issue_count'] == 0,
                  'HOSTED_BOUNDARY_SENT_OR_DUPLICATED_EFFECT')
            # Independent read-only SQLite observation; never seed internal turns/waits/effects.
            with sqlite3.connect('file:' + str(runtime / 'runtime.sqlite3') + '?mode=ro', uri=True) as db:
                counts = {table: db.execute('select count(*) from ' + table).fetchone()[0]
                          for table in ('effects', 'turns', 'waits', 'inbox')}
                events = [dict(seq=row[0], kind=row[1], body=json.loads(row[2]))
                          for row in db.execute('select seq,kind,body from events order by seq')]
            check(counts == dict(effects=1, turns=0, waits=0, inbox=1), 'UNREACHED_NATIVE_STAGE_FABRICATED')
            check(os.readlink('/proc/self/ns/net') == namespace, 'NETWORK_NAMESPACE_CHANGED_DURING_SELECTION')
            report = dict(classification='hosted-cli-boundary-selection-passed-overall-HOLD',
                          network_namespace=namespace, link_probe='fixed-absolute-ip -o link show',
                          scenario_sha256=SCENARIO_SHA, source_sha256=PINS,
                          real_client_pids=[p.pid for p in processes], broker_restarted=True,
                          reached=['owner', 'revoke-publish-persisted-state', 'pause-inbox', 'rerun-plan-dedup'],
                          counts=counts, snapshot=final, events=events,
                          native_first_action='HOLD-not-called', goal_ci_race='HOLD-no-native-wait',
                          external_acceptance='HOLD-no-executor', readonly_plan='HOLD-unbound',
                          actual_external_sends=0, overall_vertical_pass=False)
            (evidence / 'boundary-report.json').write_text(json.dumps(report, indent=2) + '\n')
            (evidence / 'cli-transcript.json').write_text(json.dumps(transcript, indent=2) + '\n')
            return report
        finally:
            for process in locals().get('processes', []):
                if process.poll() is None:
                    process.kill(); process.wait(timeout=5)
            stop()


def main():
    if len(sys.argv) > 1 and sys.argv[1] == '--client-gate':
        if sys.stdin.read(1) != 'G':
            raise Hold('client launch barrier absent')
        os.execv(sys.argv[2], sys.argv[2:])
    parser = argparse.ArgumentParser(allow_abbrev=False)
    parser.add_argument('--source-root', required=True, type=Path)
    parser.add_argument('--evidence-dir', required=True, type=Path)
    parser.add_argument('--parent-netns', required=True)
    args = parser.parse_args()
    evidence = args.evidence_dir.resolve(strict=True)
    if not evidence.is_dir() or any(evidence.iterdir()):
        raise Hold('fresh empty evidence directory required')
    try:
        report = exercise(args.source_root.resolve(strict=True), evidence, args.parent_netns)
    except AssertionError as error:
        (evidence / 'verdict.json').write_text(json.dumps(dict(selection='assertion-failed', reason=str(error), overall_vertical_pass=False)))
        return 1
    except Exception as error:
        (evidence / 'verdict.json').write_text(json.dumps(dict(selection='capability-HOLD', reason=type(error).__name__ + ': ' + str(error)[:1500], overall_vertical_pass=False)))
        return 86
    print(json.dumps(dict(selection=report['classification'], overall_vertical_pass=False)))
    return 0  # Only this reachable hosted boundary selection; never original oracle PASS.


if __name__ == '__main__':
    raise SystemExit(main())
