"""Hosted disposable portable compatibility: matched identity is NO authority.

No original deployment IDs, private paths, evidence or credentials are embedded.
Only synthetic configuration/registry and a bound non-listening Unix socket.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import socket
import sqlite3
import stat
import subprocess
import sys
import tempfile
import uuid

SOURCE_SHA256 = '60dd413cea142ad972b1c8acdbf6dd0de442c0eaf9c18fd41e7482a5310ea70b'
SOURCE_PINS = {'branch_broker.py': '9f6254fd3b83b6b0f34bde515c56edb9b944d90fc251b551e42e1d7cc3ddb1d5', 'branch_effect_binding.py': '82435b55731b7f78f72993194dbb57fc5d62e0ffa1c0e83d0b5a38ed72d63cbe', 'branch_effect_port.py': 'ae4ba44f12472d1109a01e0ad468e6cabbbf02adbc8d9cbd39c1dfb4aff7c42c', 'broker_plan.py': '638dbf482e5ef12e257fc5f0a8c0297571641703981f8fd1517b16ab02805cc7', 'existing_ci_proof.py': '5393667fcf2e10be2e8223e6380df321b6d72deb56423e290e51b8cfc1217ba4', 'goal_retire.py': 'e4a5157b78d278e64ac2345ab7b35a3c22da132603b2b3b8ce5f2563b55445df', 'handoff_retire.py': '8118be38d49376a0138ef88e2ba2373b18e4a871ee02d4bd6553a983af5933b7', 'native_goal.py': '5856c23ca25734ed7df2ef4f737f472229b1c875f344e8796be9115d935d2c89', 'native_quiesce.py': 'f4e90a3833728ac7e6ed067effd84d112ad5cc74418a2dec133fe432e6f62312', 'native_role.py': 'fe5617f8bdfaa3d170585c84fe3c57c9ec39b1b954da801634dcf51f2c45dc45', 'native_transport.py': '2925546088ac9386f5945185b29806cc95c557bcc9fb4d3a330d7d5122011ac1', 'native_wait.py': '062c69a93055b5bded567311b538865a8a8e5f08ec4b570be8d4dff60cd2e969', 'orphan_terminal.py': '77f8e0adb5b0b2a358d7e3a6e5467143adaf4eb959f9da94e041ddf097fa0177', 'pending_wire.py': '9398c7383a8c424509cd24f7a0578a514f9ddea46d689a26c3ab6e05c6c2cbfc', 'portable_broker.py': '6101afe0e2522988b3c652f91a88b06082506f7cc4f323656ff51b45407449b0', 'portable_config.py': 'ef79262cd962cb580d9eca12f09cd76293315f555476a2dbdb3dc607d226b403', 'private_resources.py': '0b8409f3ea7c4f14eca9bd783dd76db2958be7c826edc08a6aa631abae4bf126', 'readonly_plan_consumer.py': '62aac50be8802351a8ae79e592c0e6e8f04bb943dbc54573221efb1b47f473af', 'reconnect_observe.py': 'bdc7ed4273e1f96fb1b7d0e2e5d464ac50235fa3ee8314c1c4296fa4e0da30dc', 'role_resume.py': 'fd2fe7bb9b3b19685e21c17aad5319122feffaab33b2d74ca66268a8b44c6928', 'runtime_broker.py': 'db842b7d777d07b9ef34f34079c1eb1f514317c19d503f442abb307f8e8c3639', 'runtime_store.py': '3990105d2eff4dafb40e01810734f065c1f81f9c9dec0e7ab49f6d95af8871db', 'warm_handoff.py': 'ec343e6391e15ec13a2be87c41a9bbbb2e8fdf353f55355d238625312c40bf93', 'binding.schema.json': '8e56721a606eaff72289da8279f63f0cbb59b2167dda4a732877ac95ef3fcf64'}
SCENARIO_SHA256 = '7c778e9bc57f56bb187b1355131816c7e30f553af9c5f6c997a6c1cd4e602b48'


class Hold(RuntimeError):
    pass


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def check(ok, reason):
    if not ok:
        raise AssertionError(reason)


def identity(path):
    s = path.lstat()
    return dict(path=str(path), dev=s.st_dev, ino=s.st_ino, uid=s.st_uid,
                mode=stat.S_IMODE(s.st_mode), kind=stat.S_IFMT(s.st_mode))


def ledger_state(directory):
    return {p.name: dict(sha256=sha(p.read_bytes()), size=p.stat().st_size,
                        inode=p.stat().st_ino, dev=p.stat().st_dev,
                        mode=stat.S_IMODE(p.stat().st_mode), mtime_ns=p.stat().st_mtime_ns)
            for p in directory.iterdir() if p.is_file()}


def endpoint_check(args):
    # Trusted test bootstrap imports the actual pinned public data-boundary code.
    sys.path.insert(0, str(args.source_root))
    from portable_config import load_private_binding
    binding = load_private_binding(args.binding, args.binding_sha256, args.source_sha256)
    print(json.dumps(binding.check_endpoint_identity(), sort_keys=True))
    return 0


def collect(source, evidence, parent_netns):
    if SOURCE_SHA256 is None:
        raise Hold('final public candidate source not frozen')
    if sys.platform != 'linux' or os.geteuid() == 0 or os.environ.get('RUNNER_ENVIRONMENT') != 'github-hosted':
        raise Hold('non-root GitHub-hosted Linux required')
    namespace = os.readlink('/proc/self/ns/net')
    if namespace == parent_netns:
        raise Hold('private network namespace required')
    if 'NoNewPrivs:' + chr(9) + '1' not in Path('/proc/self/status').read_text():
        raise Hold('no-new-privileges required')
    if any(os.environ.get(k) for k in ('GH_TOKEN', 'GITHUB_TOKEN', 'GH_CONFIG_DIR', 'SSH_AUTH_SOCK', 'OPENAI_API_KEY')):
        raise Hold('credential-bearing environment refused')
    tracer = next((p for p in ('/usr/bin/strace', '/bin/strace') if Path(p).is_file()), None)
    ip = next((p for p in ('/usr/sbin/ip', '/usr/bin/ip') if Path(p).is_file()), None)
    if tracer is None or ip is None:
        raise Hold('real fixed-path strace/ip missing')
    links = subprocess.check_output([ip, '-o', 'link', 'show'], text=True, timeout=5,
                                    env={'PATH': '/usr/bin:/bin'})
    if [line.split(':', 2)[1].strip().split('@', 1)[0] for line in links.splitlines() if line.strip()] != ['lo']:
        raise Hold('only loopback permitted')
    closure = (source / 'SOURCE-CLOSURE.json').read_bytes()
    if sha(closure) != SOURCE_SHA256:
        raise Hold('final source manifest differs')
    # Public export must not contain deployment-specific UUIDs or host paths.
    for name in ['SOURCE-CLOSURE.json', *SOURCE_PINS]:
        path = source / name
        if path.resolve(strict=True) != path or not path.is_file():
            raise Hold('source physical identity differs')
        raw = path.read_bytes()
        if name != 'SOURCE-CLOSURE.json' and sha(raw) != SOURCE_PINS[name]:
            raise Hold('source byte pin differs')
        if re.search(rb'[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}', raw):
            raise Hold('deployment UUID literal in public source export')
        if any(marker in raw for marker in (b'/Users/', b'/private/tmp/', b'/tmp/lab')):
            raise Hold('deployment host path in public source export')
    results = []
    bootstrap = ('import sys,runpy; root=sys.argv.pop(1); sys.path.insert(0,root); '
                 'runpy.run_path(root+"/portable_broker.py",run_name="__main__")')
    with tempfile.TemporaryDirectory(prefix='pc-', dir='/tmp') as temporary:
        root = Path(temporary).resolve(); root.chmod(0o700)
        env = dict(PATH='/usr/bin:/bin', HOME=str(root), LANG='C.UTF-8', PYTHONDONTWRITEBYTECODE='1')
        for label in ('matching', 'wrong-binding-sha', 'wrong-source-sha', 'wrong-installation-pin', 'wrong-registered-id'):
            case = root / label; case.mkdir(mode=0o700)
            ledger = case / 'ledger'; ledger.mkdir(mode=0o700)
            parent_a = case / 'alias'; parent_a.mkdir(mode=0o700)
            parent_t = case / 'target'; parent_t.mkdir(mode=0o700)
            cwd = case / 'work'; cwd.mkdir(mode=0o700)
            sid = str(uuid.uuid4())
            database = ledger / 'runtime.sqlite3'
            lock = ledger / 'broker.lock'; lock.write_bytes(b''); lock.chmod(0o600)
            target = parent_t / 's'; alias = parent_a / 's'
            with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as fixture_socket:
                fixture_socket.bind(str(target)); target.chmod(0o600)
                # No listen/connect/accept and no App Server protocol is implemented.
                alias.symlink_to(target)
                proxy_target = Path('/usr/bin/false').resolve(strict=True)
                proxy_alias = case / 'proxy'; proxy_alias.symlink_to(proxy_target)
                legacy = dict(native_endpoint=str(alias), native_session=sid, physical_cwd=str(cwd),
                              role='one-lead', proxy_binary=str(proxy_alias), gh_binary='/usr/bin/false',
                              runtime_directory=str(ledger))
                legacy_path = case / 'old-installation.json'
                legacy_path.write_text(json.dumps(legacy)); legacy_path.chmod(0o600)
                installed = sha(legacy_path.read_bytes())
                with sqlite3.connect(database) as db:
                    db.execute('create table installation(id integer primary key,pin text not null)')
                    db.execute('create table role_registration(id integer primary key,session_id text not null,phase text not null)')
                    db.execute('insert into installation values(1,?)',
                               (sha(b'wrong-ledger-pin') if label == 'wrong-installation-pin' else installed,))
                    db.execute('insert into role_registration values(1,?,?)',
                               (str(uuid.uuid4()) if label == 'wrong-registered-id' else sid, 'stable'))
                database.chmod(0o600)
                config = dict(schema='lab-private-runtime-binding/1', deployment_label='hosted-scratch',
                    source_closure_sha256=SOURCE_SHA256,
                    ledger=dict(directory=str(ledger), installation_sha256=installed),
                    role=dict(name='one-lead', physical_cwd=str(cwd)),
                    native=dict(expected_registered_session=sid,
                        endpoint=dict(alias=identity(alias),alias_parent=identity(parent_a),
                                      target=identity(target),target_parent=identity(parent_t),link_text=str(target)),
                        proxy=dict(path=str(proxy_target),sha256=sha(proxy_target.read_bytes())),
                        engine_epoch_reference=None), evidence_references={'broker-installation':
                            dict(kind='authorization',path=str(legacy_path),sha256=installed)})
                config_path = case / 'binding.json'; config_path.write_text(json.dumps(config)); config_path.chmod(0o600)
                config_sha = sha(config_path.read_bytes())
                before = ledger_state(ledger)
                before_names = sorted(p.name for p in ledger.iterdir())
                if label == 'matching':
                    endpoint_command = [sys.executable, '-I', '-S', '-B', str(Path(__file__).resolve()),
                        '--endpoint-check', '--source-root', str(source), '--binding', str(config_path),
                        '--binding-sha256', config_sha, '--source-sha256', SOURCE_SHA256]
                    result = subprocess.run(endpoint_command, env=env, cwd=case, capture_output=True, text=True, timeout=8)
                    check(result.returncode == 0 and not result.stderr, 'ACTUAL_ENDPOINT_IDENTITY_CHECK_FAILED')
                    endpoint = json.loads(result.stdout)
                    check(endpoint == dict(endpoint_identity_matched=True,server_epoch_proved=False,execution_authority=False),
                          'SOCKET_IDENTITY_GRANTED_AUTHORITY')
                source_sha = '0' * 64 if label == 'wrong-source-sha' else SOURCE_SHA256
                private_sha = '0' * 64 if label == 'wrong-binding-sha' else config_sha
                entry_args = [sys.executable, '-I', '-S', '-B', '-c', bootstrap, str(source),
                              'check-compatibility', '--source-closure', str(source / 'SOURCE-CLOSURE.json'),
                              '--source-closure-sha256', source_sha, '--private-binding', str(config_path),
                              '--private-binding-sha256', private_sha]
                trace = case / 'syscalls.log'
                argv = [tracer, '-f', '-qq', '-s', '256', '-e', 'trace=execve,execveat,connect,bind,listen',
                        '-o', str(trace), *entry_args]
                actual = subprocess.run(argv, env=env, cwd=case, capture_output=True, timeout=10)
                if not trace.exists() or trace.stat().st_size > 1024 * 1024:
                    raise Hold('syscall evidence absent/oversize')
                trace_raw = trace.read_bytes(); text = trace_raw.decode(errors='replace')
                execs = [line for line in text.splitlines() if 'execve(' in line or 'execveat(' in line]
                if not execs:
                    raise Hold('strace/ptrace could not execute public entry')
                check(len(execs) == 1 and not any(x in text for x in ('connect(', 'bind(', 'listen(')),
                      'COMPATIBILITY_STARTED_OR_CONNECTED_RUNTIME')
                check(actual.returncode == 78 and actual.stderr == b'', 'COMPATIBILITY_WRONG_EXIT_OR_STDERR')
                response = json.loads(actual.stdout)
                if label == 'matching':
                    check(response == dict(action='hold', ledger_identity='matched-read-only',
                        owner_credentials_read=False,quiescence='existing-broker-lock-held-and-no-sidecars',
                        immutable_snapshot_stable=True,execution_authority=False,schema_migrated=False,broker_started=False),
                        'MATCHED_IDENTITY_BECAME_AUTHORITY')
                else:
                    check(response.get('action') == 'hold' and response.get('execution_authority') is False
                          and response.get('reason') == 'portable-selection-or-capability-unavailable'
                          and 'ledger_identity' not in response, 'MISMATCH_ACCEPTED_AS_COMPATIBLE')
                check(before == ledger_state(ledger) and before_names == sorted(p.name for p in ledger.iterdir()),
                      'COMPATIBILITY_WROTE_LEDGER')
                check(identity(target) == config['native']['endpoint']['target'], 'SOCKET_IDENTITY_CHANGED')
                results.append(dict(label=label, argv=entry_args, exit_code=actual.returncode,
                    response=response, stdout_sha256=sha(actual.stdout), stderr_sha256=sha(actual.stderr),
                    ledger_sha_unchanged=True, ledger_inode_and_mtime_unchanged=True,
                    broker_started=False, exec_syscalls=1, connect_bind_listen_syscalls=0,
                    syscall_trace_sha256=sha(trace_raw)))
                # Only synthetic scratch paths/config IDs could appear in these traces.
                (evidence / (label + '.trace')).write_bytes(trace_raw)
                (evidence / 'observations.json').write_text(json.dumps(results,indent=2)+chr(10))
    check(os.readlink('/proc/self/ns/net') == namespace, 'NAMESPACE_CHANGED')
    return dict(classification='portable-compatibility-no-authority-boundary-only',
                source_closure_sha256=SOURCE_SHA256, scenarios=1, cases=len(results),
                actual_brokers_started=0, App_Servers_started=0, native_calls=0,
                matching_readback_granted_authority=False, original_private_state_included=False,
                deployment_proved=False, overall_vertical_pass=False)


def main():
    parser = argparse.ArgumentParser(allow_abbrev=False)
    parser.add_argument('--source-root',type=Path,required=True)
    parser.add_argument('--evidence-dir',type=Path)
    parser.add_argument('--parent-netns')
    parser.add_argument('--endpoint-check',action='store_true')
    parser.add_argument('--binding')
    parser.add_argument('--binding-sha256')
    parser.add_argument('--source-sha256')
    args = parser.parse_args()
    if args.endpoint_check:
        return endpoint_check(args)
    evidence = args.evidence_dir.resolve(strict=True)
    if any(evidence.iterdir()):
        raise Hold('fresh evidence directory required')
    try:
        report = collect(args.source_root.resolve(strict=True),evidence,args.parent_netns)
    except AssertionError as error:
        (evidence/'verdict.json').write_text(json.dumps(dict(classification='assertion-failed',reason=str(error),overall_vertical_pass=False)))
        return 1
    except Exception as error:
        (evidence/'verdict.json').write_text(json.dumps(dict(classification='capability-HOLD',reason=type(error).__name__+': '+str(error)[:900],overall_vertical_pass=False)))
        return 86
    (evidence/'verdict.json').write_text(json.dumps(report,indent=2)+chr(10));print(json.dumps(report))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
