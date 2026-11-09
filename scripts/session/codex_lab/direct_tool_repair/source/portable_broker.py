"""Portable public entry. Independent identity pins do not grant execution scope."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import sqlite3
from urllib.parse import quote

from portable_config import load_private_binding, require


def verify_source(path, expected):
    file = Path(path)
    require(file.is_absolute() and file.resolve(strict=True) == file, 'physical-source-manifest-required')
    raw = file.read_bytes()
    require(len(raw) <= 128 * 1024 and hashlib.sha256(raw).hexdigest() == expected, 'independent-public-source-pin-mismatch')
    manifest = json.loads(raw)
    require(manifest.get('entry') == 'portable_broker.py' and type(manifest.get('files_sha256')) is dict,
            'portable-source-closure-required')
    require({p.name for p in file.parent.glob('*.py')} ==
            {n for n in manifest['files_sha256'] if n.endswith('.py')}, 'complete-public-python-source-inventory-required')
    for name,pin in manifest['files_sha256'].items():
        relative = PurePosixPath(name)
        require(not relative.is_absolute() and '..' not in relative.parts and str(relative) == name, 'unsafe-source-member')
        member = file.parent / name
        require(member.resolve(strict=True) == member and member.is_file()
                and hashlib.sha256(member.read_bytes()).hexdigest() == pin, 'public-source-file-mismatch')
    require(Path(__file__).resolve() == file.parent / 'portable_broker.py', 'entry-not-selected-source')


def compatibility(binding):
    """Read ONLY existing installation pin and registry; never owner credentials."""
    value = binding.view()
    reference = binding.reference('broker-installation')
    require(reference['kind'] == 'authorization' and reference['sha256'] == value['ledger']['installation_sha256'],
            'independent-old-installation-reference-required')
    import runtime_broker as runtime
    runtime.installation(reference['path'], reference['sha256'], binding)  # Data/file checks only; no Broker construction.
    database = Path(value['ledger']['directory']) / 'runtime.sqlite3'
    require(database.resolve(strict=True) == database and database.is_file() and not database.is_symlink(),
            'existing-ledger-required-no-initialization')
    import os, stat, fcntl
    directory = database.parent
    info = directory.stat()
    require(info.st_uid == os.geteuid() and stat.S_IMODE(info.st_mode) == 0o700, 'private-ledger-directory-required')
    lock_path = directory / 'broker.lock'
    require(lock_path.resolve(strict=True) == lock_path and not lock_path.is_symlink(), 'existing-broker-lock-required')
    lock_fd = os.open(str(lock_path), os.O_RDONLY | os.O_NOFOLLOW)
    try:
        lock_before = os.fstat(lock_fd)
        require(stat.S_ISREG(lock_before.st_mode) and lock_before.st_uid == os.geteuid()
                and lock_before.st_nlink == 1 and lock_before.st_mode & 0o077 == 0, 'private-existing-broker-lock-required')
        fcntl.flock(lock_fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        def no_sidecars():
            for suffix in ('-wal','-shm','-journal'):
                sidecar = database.with_name(database.name + suffix)
                require(not sidecar.exists() and not sidecar.is_symlink(), 'ledger-sidecar-present-quiescence-unproved')
        identity = lambda value: (value.st_dev,value.st_ino,value.st_uid,value.st_mode,
                                  value.st_nlink,value.st_size,value.st_mtime_ns,value.st_ctime_ns)
        no_sidecars()
        before = database.lstat()
        require(stat.S_ISREG(before.st_mode) and before.st_uid == os.geteuid()
                and before.st_nlink == 1 and before.st_mode & 0o077 == 0, 'private-existing-ledger-required')
        uri = 'file:' + quote(str(database), safe='/') + '?mode=ro&immutable=1'
        db = sqlite3.connect(uri, uri=True)
        try:
            db.execute('pragma query_only=on')
            installation = db.execute('select pin from installation where id=1').fetchone()
            role = db.execute('select session_id,phase from role_registration where id=1').fetchone()
            require(installation and role and role[1] == 'stable', 'existing-stable-registry-required')
            binding.assert_ledger_identity(installation[0], role[0], value['ledger']['directory'])
        finally:
            db.close()
        no_sidecars()
        require(identity(before) == identity(database.lstat())
                and identity(lock_before) == identity(os.fstat(lock_fd)) == identity(lock_path.lstat()),
                'ledger-or-original-lock-moved-during-immutable-read')
    finally:
        os.close(lock_fd)
    return dict(action='hold', ledger_identity='matched-read-only', owner_credentials_read=False,
                quiescence='existing-broker-lock-held-and-no-sidecars', immutable_snapshot_stable=True,
                execution_authority=False, schema_migrated=False, broker_started=False)


def build_composition(binding):
    """Fixed bundled imports after source verification, no private module imports."""
    import runtime_broker as runtime
    from private_resources import PrivateResourceAccess, load_reference
    from runtime_store import need

    class PortableGate(PrivateResourceAccess):
        def handle(self, request):
            command = request.get('command')
            with self.execution_gate:
                if command in ('owner.transfer-release','owner.transfer-claim','owner.transfer-status'):
                    from owner_transfer import route_transfer
                    return route_transfer(self,request)
                auth = request.get('auth')
                with self.store.tx() as db:
                    owner = self.store.owner(db, auth)
                    task = self.store.current(db)
                if command == 'portable.compatibility':
                    return compatibility(self.binding)
                if command != 'inspect':
                    from exact_scope import authorize_exact
                    scope = self.private_authorization('runtime-actions', 'lab-runtime-exact-actions/2')
                    authorize_exact(self, request, scope)
                    if command == 'assignment.revoke':
                        return self.store.revoke(auth)
                return super().handle(request)

    class PortableBroker(PortableGate, runtime.RoleSupport, runtime.Broker):
        pass

    selected = binding.view()
    reference = binding.reference('broker-installation')
    need(reference['kind'] == 'authorization' and reference['sha256'] == selected['ledger']['installation_sha256'],
         'same-old-installation-pin-required')
    cfg = runtime.installation(reference['path'], reference['sha256'], binding)
    optional = {}
    constructors = {
        'plan-installation': ('plan_installation', 'broker_plan', 'PlanInstallation'),
        'branch-installation': ('branch_installation', 'branch_broker', 'InstalledBranch'),
        'goal-installation': ('goal_installation', 'native_goal', 'GoalInstallation'),
        'ci-proof-manifest': ('verifier', 'existing_ci_proof', 'ExistingCIProofVerifier'),
    }
    # Names/modules are fixed CODE, not data-selected plugins/ports.
    import importlib
    for name,(argument,module,symbol) in constructors.items():
        if name in selected['evidence_references']:
            ref = binding.reference(name)
            need(ref['kind'] == ('observation' if name == 'ci-proof-manifest' else 'authorization'), 'wrong-private-action-reference-kind')
            constructor = getattr(importlib.import_module(module), symbol)
            optional[argument] = constructor(ref['path'], ref['sha256'])
    return runtime, cfg, PortableBroker, optional


def main():
    parser = argparse.ArgumentParser(allow_abbrev=False)
    parser.add_argument('command', choices=('check-compatibility','serve','call','serve-owned','call-owned'))
    parser.add_argument('--source-closure',required=True)
    parser.add_argument('--source-closure-sha256',required=True)
    parser.add_argument('--private-binding')
    parser.add_argument('--private-binding-sha256')
    parser.add_argument('--owned-installation')
    parser.add_argument('--owned-installation-sha256')
    parser.add_argument('--single-role-installation')
    parser.add_argument('--single-role-installation-sha256')
    parser.add_argument('--request')
    args=parser.parse_args()
    try:
        verify_source(args.source_closure,args.source_closure_sha256)
        if args.command in ('serve-owned','call-owned'):
            require(args.owned_installation and args.owned_installation_sha256
                    and not args.private_binding and not args.private_binding_sha256,'owned-only-selection-required')
            if args.single_role_installation or args.single_role_installation_sha256:
                require(args.single_role_installation and args.single_role_installation_sha256,
                        'single-role-pinned-installation-required')
                from single_role import run
                return run(args.command,args.owned_installation,args.owned_installation_sha256,args.request,
                           args.single_role_installation,args.single_role_installation_sha256)
            from owned_runtime import run
            return run(args.command,args.owned_installation,args.owned_installation_sha256,args.request)
        require(not args.single_role_installation and not args.single_role_installation_sha256,
                'single-role-owned-modes-only')
        require(args.private_binding and args.private_binding_sha256
                and not args.owned_installation and not args.owned_installation_sha256,'portable-binding-required')
        binding=load_private_binding(args.private_binding,args.private_binding_sha256,args.source_closure_sha256)
        if args.command != 'call':
            checked=compatibility(binding)
            if args.command=='check-compatibility':
                print(json.dumps(checked,sort_keys=True));return 78
        if args.command=='serve':
            runtime,cfg,broker_type,optional=build_composition(binding)
            # Explicit startup only; old store/installation binding never reset.
            from startup_authority import select_startup_authority
            startup = select_startup_authority(binding)
            runtime.serve(cfg,broker_type=broker_type,startup_authority=startup,**optional)
            return 0
        import runtime_broker as runtime
        ref = binding.reference('broker-installation')
        runtime.installation(ref['path'], ref['sha256'], binding)
        import socket
        require(args.request is not None,'private-request-file-required')
        endpoint=runtime.physical(str(Path(binding.view()['ledger']['directory'])/'broker.sock'))
        import os,stat
        info=endpoint.stat()
        require(stat.S_ISSOCK(info.st_mode) and info.st_uid==os.geteuid() and info.st_mode&0o077==0,'private-broker-socket-required')
        request=json.loads(runtime.private_file(args.request))
        with socket.socket(socket.AF_UNIX,socket.SOCK_STREAM) as client:
            client.settimeout(30);client.connect(str(endpoint));runtime.send(client,request)
            print(json.dumps(runtime.message(client),sort_keys=True))
        return 0
    except (ValueError,OSError,TypeError,KeyError,UnicodeError,sqlite3.Error,EOFError,RuntimeError):
        print(json.dumps(dict(action='hold',reason='portable-selection-or-capability-unavailable',execution_authority=False)))
        return 78


if __name__=='__main__':
    raise SystemExit(main())
