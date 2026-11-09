"""Exact installed branch binding; default no authorization or external write."""
import base64
import contextlib
import hashlib
import json
import os
import re
from pathlib import Path
import stat
import subprocess

from native_wait import WaitStore, WaitSupport
from branch_effect_binding import BranchBinding, wire
from branch_effect_port import need


class BranchStore(WaitStore):
    pass  # Same effects table, no new store/schema or canonical journal.


def private_raw(path, limit=4 * 1024 * 1024):
    path = Path(path)
    need(path.is_absolute() and path.resolve(strict=True) == path and not path.is_symlink(), 'private-physical-installation-required')
    parent = path.parent.stat()
    need(parent.st_uid == os.geteuid() and stat.S_IMODE(parent.st_mode) == 0o700, 'private-installation-parent-required')
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    try:
        info = os.fstat(fd)
        need(stat.S_ISREG(info.st_mode) and info.st_uid == os.geteuid() and info.st_nlink == 1
             and stat.S_IMODE(info.st_mode) in (0o400, 0o600) and 0 < info.st_size <= limit, 'private-installation-file-required')
        raw = bytearray()
        while len(raw) <= limit:
            chunk = os.read(fd, min(65536, limit + 1 - len(raw)))
            if not chunk:
                break
            raw.extend(chunk)
        after, named = os.fstat(fd), path.lstat()
        ident = lambda s: (s.st_dev, s.st_ino, s.st_mode, s.st_size, s.st_mtime_ns, s.st_ctime_ns)
        need(len(raw) == info.st_size and ident(info) == ident(after) == ident(named), 'private-installation-moved')
        return bytes(raw)
    finally:
        os.close(fd)


class InstalledBranch:
    """Independent trusted launcher selects these bytes; digest is not approval."""
    def __init__(self, path, external_raw_sha256):
        self.path, self.pin = Path(path), external_raw_sha256
        self.policy = self.load()

    def load(self):
        raw = private_raw(self.path)
        need(hashlib.sha256(raw).hexdigest() == self.pin, 'external-branch-installation-pin-mismatch')
        value = json.loads(raw)
        need(type(value) is dict and set(value) == {'kind', 'mode', 'broker_installation_sha256',
            'owner', 'approved', 'receipt_directory', 'source_files_sha256', 'binary_sha256',
            'ssh_public_fingerprint', 'resource_witness_path', 'resource_witness_sha256'}
            and value['kind'] == 'r2-exact-branch-installation/1' and value['mode'] in ('observe-only', 'send-once'),
            'exact-independent-branch-installation-required')
        return value


class BranchSupport(WaitSupport):
    def __init__(self, cfg):
        super().__init__(cfg)
        self._branch_installation = None
        self._branch_binding = None
        self._resource_guard = None

    def install_branch(self, installation, resource_guard):
        # No runtime command sets this. Port presence is never authorization.
        need(installation is None or type(installation) is InstalledBranch, 'trusted-startup-installation-required')
        need(resource_guard is None or (type(resource_guard) not in (dict, bool, str)
             and callable(getattr(resource_guard, 'acquire_exact', None))), 'reviewed-resource-guard-required')
        self._branch_installation = installation
        self._resource_guard = resource_guard

    def _branch(self, auth, *, sending=False):
        need(self._branch_installation is not None, 'no-independent-branch-installation')
        policy = self._branch_installation.load()
        need(policy == self._branch_installation.policy and policy['broker_installation_sha256'] == self.cfg['pin'],
             'branch-installation-or-broker-pin-moved')
        approved = policy['approved']
        need(type(approved) is dict and set(approved) == {'subject', 'transport', 'session_id', 'revision', 'base', 'acceptance'}
             and approved['session_id'] == self.session_id, 'exact-stable-role-branch-policy-required')
        source_pins = policy['source_files_sha256']
        need(type(source_pins) is dict and set(source_pins) == {'branch_effect_binding.py', 'branch_effect_port.py'},
             'reviewed-branch-source-pins-required')
        for name, expected in source_pins.items():
            need(hashlib.sha256(Path(__file__).with_name(name).read_bytes()).hexdigest() == expected,
                 'reviewed-branch-source-moved')
        with self.store.tx() as db:
            owner = self.store.owner(db, auth)
            registration = db.execute('select * from role_registration where id=1').fetchone()
            need(registration and registration['phase'] == 'stable' and registration['session_id'] == self.session_id,
                 'observed-stable-role-required')
            if sending:
                need(policy['mode'] == 'send-once' and policy['owner'] ==
                     dict(client=owner['client'], generation=owner['generation']), 'independent-exact-owner-send-installation-required')
        checkout = Path(approved['transport']['checkout'])
        need(checkout.resolve(strict=True) == checkout
             and all(checkout != root and root not in checkout.parents for root in self.protected_roots())
             and str(checkout) != self.cfg['physical_cwd'], 'isolated-branch-checkout-required')
        for directory in (checkout, checkout / '.git', checkout / '.git/objects'):
            info = directory.stat()
            need(directory.resolve(strict=True) == directory and stat.S_ISDIR(info.st_mode)
                 and info.st_uid == os.geteuid() and stat.S_IMODE(info.st_mode) == 0o700,
                 'private-owned-branch-resources-required')
        if self._branch_binding is None:
            canonical_pin = hashlib.sha256(wire(approved).encode()).hexdigest()
            self._branch_binding = BranchBinding(self, approved, canonical_pin, policy['receipt_directory'])
        return policy, self._branch_binding

    def _binary_and_identity(self, policy):
        paths = {'git': '/usr/bin/git', 'ssh': '/usr/bin/ssh', 'curl': '/usr/bin/curl', 'ssh_add': '/usr/bin/ssh-add'}
        need(set(policy['binary_sha256']) == set(paths), 'exact-transport-binary-pins-required')
        for name, path in paths.items():
            info = Path(path).stat()
            need(stat.S_ISREG(info.st_mode) and info.st_size <= 128 * 1024 * 1024
                 and hashlib.sha256(Path(path).read_bytes()).hexdigest() == policy['binary_sha256'][name],
                 'transport-binary-mismatch')
        transport = policy['approved']['transport']
        agent = Path(transport['ssh_agent_socket'])
        info = agent.stat()
        need(stat.S_ISSOCK(info.st_mode) and info.st_uid == os.geteuid()
             and [info.st_dev, info.st_ino] == transport['ssh_agent_dev_inode'], 'installed-agent-moved')
        result = subprocess.run(['/usr/bin/ssh-add', '-l', '-E', 'sha256'],
            env={'PATH': '/usr/bin:/bin', 'HOME': transport['empty_home'], 'SSH_AUTH_SOCK': str(agent)},
            stdin=subprocess.DEVNULL, capture_output=True, timeout=5, check=False)
        need(result.returncode == 0 and len(result.stdout) <= 4096 and len(result.stderr) <= 4096,
             'single-public-ssh-identity-unavailable')
        lines = result.stdout.decode().splitlines()
        need(len(lines) == 1 and len(lines[0].split()) >= 2
             and lines[0].split()[1] == policy['ssh_public_fingerprint'], 'not-exact-single-approved-ssh-identity')
        witness = private_raw(policy['resource_witness_path'])
        need(hashlib.sha256(witness).hexdigest() == policy['resource_witness_sha256'],
             'independently-selected-resource-witness-moved')

    def _receipt_readback(self, binding, effect_id):
        need(type(effect_id) is str and re.fullmatch('[a-f0-9]{48}', effect_id), 'exact-stored-effect-id-required')
        with self.store.tx() as db:
            record = binding._read(db, effect_id)
        path = binding.receipts / (effect_id + '.json')
        if not path.exists():
            return dict(private_receipt_present=False, attribution_confirmed=False)
        raw = private_raw(path)
        receipt = json.loads(raw)
        need(receipt['effect_id'] == effect_id and receipt['owner_generation'] == record['generation']
             and receipt['revision'] == record['revision'] and receipt['subject'] == binding.port.s
             and receipt['policy_sha256'] == binding.pin, 'private-receipt-record-binding-mismatch')
        # Do not publish raw stdout/stderr or infer attribution from a ref match.
        stdout = base64.b64decode(receipt['stdout_base64'], validate=True)
        stderr = base64.b64decode(receipt['stderr_base64'], validate=True)
        return dict(private_receipt_present=True, private_receipt_sha256=hashlib.sha256(raw).hexdigest(),
                    stdout_sha256=hashlib.sha256(stdout).hexdigest(), stderr_sha256=hashlib.sha256(stderr).hexdigest(),
                    attribution_confirmed=False, requires_independent_accepted_receipt_and_actor_verification=True)

    def handle(self, request):
        command = request.get('command')
        if command not in ('branch.prepare', 'branch.issue', 'branch.observe'):
            return super().handle(request)
        with self.execution_gate:
            auth, args = request.get('auth'), request.get('arguments', {})
            need(type(args) is dict, 'exact-branch-command-arguments')
            policy, binding = self._branch(auth, sending=command in ('branch.prepare', 'branch.issue'))
            if command == 'branch.prepare':
                need(set(args) == {'revision'}, 'branch-prepare-data-only')
                return binding.prepare(auth, args['revision'])
            need(set(args) == {'revision', 'effect_id'}, 'branch-effect-id-and-revision-only')
            if command == 'branch.issue':
                # No default guard implementation: a file/hash/port cannot prove
                # exclusive role-denied resources during an actual spawn window.
                need(self._resource_guard is not None, 'resource-exclusivity-not-installed-no-consume')
                self._binary_and_identity(policy)
                context = self._resource_guard.acquire_exact(self._branch_installation.pin,
                                                             policy['resource_witness_sha256'])
                need(hasattr(context, '__enter__') and hasattr(context, '__exit__'), 'exact-resource-window-required')
                with context:
                    # Re-read independent immutable bytes and identity IN window.
                    again, _ = self._branch(auth, sending=True)
                    need(again == policy, 'installation-moved-before-send')
                    self._binary_and_identity(policy)
                    return binding.issue_internal(auth, args['revision'], args['effect_id'])
            # Current stable owner can recover old fenced task WITHOUT regranting
            # that task any execution rights or changing generation/revision.
            self._binary_and_identity(policy)
            observation = binding.observe_readonly(auth, args['revision'], args['effect_id'])
            receipt = self._receipt_readback(binding, args['effect_id'])
            with self.store.tx() as db:
                self.store.event(db, 'branch-old-or-current-readonly-observation',
                                 dict(effect_id=args['effect_id'], observation=observation, receipt=receipt))
            return dict(action='hold', reason='independent-actor-and-accepted-receipt-attribution-required',
                        observation=observation, receipt=receipt, resent=False, confirmed=False)
