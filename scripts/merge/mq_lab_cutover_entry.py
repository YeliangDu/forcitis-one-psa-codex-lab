"""Shared actual old/recovery/successor entry logic; no result-driver algorithm.

Programs consume one bounded private JSON request inside the hosted scratch root.
Old and successor are launched/waited by the independent HostProbe. Recovery's
narrow readback path deliberately never constructs an IPC or send adapter.
"""
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys

from mq_lab_cutover_channel import ScratchChannel, decode
from mq_lab_journal_v2 import ReleaseJournalV2
from mq_lab_control_install import (admit_control_upgrade, read_control_upgrade_once,
                                    recover_control_upgrade_once, run_control_upgrade_once)


def require(ok, reason):
    if not ok:
        raise ValueError(reason)


def request_file(path):
    path = Path(path)
    require(path.is_absolute() and path.resolve(strict=True) == path, 'physical-request-required')
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(fd, 'rb') as stream:
        info = os.fstat(stream.fileno())
        require(stat.S_ISREG(info.st_mode) and info.st_uid == os.geteuid()
                and info.st_nlink == 1 and stat.S_IMODE(info.st_mode) == 0o600
                and info.st_size <= 4 * 1024 * 1024, 'private-bounded-request-required')
        raw = stream.read(4 * 1024 * 1024 + 1)
    require(len(raw) <= 4 * 1024 * 1024, 'oversize-request')
    request = decode(raw)
    require(type(request) is dict and type(request.get('number')) is int and request['number'] > 0,
            'invalid-request-number')
    root, common = Path(request['root']), Path(request['common_dir'])
    require(os.environ.get('GITHUB_ACTIONS') == 'true' and root.is_absolute()
            and root.resolve(strict=True) == root and root.name == 'forcitis-one-psa-codex-lab'
            and common == root / '.git' and common.resolve(strict=True) == common
            and path.is_relative_to(root), 'hosted-physical-scratch-required')
    return request


def journal(request):
    # Constructor is existing-only V2. No fallback/initialization/migration here.
    return ReleaseJournalV2(Path(request['common_dir']) / 'merge-queue/releases.sqlite3')


def loaded_source(request, phase, commit):
    from mq_lab_cutover_live import git
    root = Path(__file__).resolve().parents[2]
    require(git(root, 'rev-parse', '--verify', 'HEAD').decode().strip() == commit,
            'entry-not-loaded-from-pinned-commit')
    paths = request['source_paths'][phase]
    require(type(paths) is list and paths and len(paths) == len(set(paths)), 'missing-fixed-source-closure')
    for relative in paths:
        target = root / relative
        require(type(relative) is str and not relative.startswith('/')
                and all(p not in ('', '.', '..') for p in relative.split('/'))
                and target.resolve(strict=True) == target and target.is_file()
                and target.read_bytes() == git(Path(request['root']), 'show', commit + ':' + relative),
                'loaded-source-differs-from-pinned-object')


class PreparationScope:
    def __init__(self, request, channel):
        self.request, self.channel = request, channel

    def read_preparation_binding(self):
        from mq_lab_cutover_live import read_scope
        record = self.request['reads']['preparation']
        scope = read_scope(self.request, 'preparation')
        require(record['key'] == 'preparation'
                and json.dumps(record['scope'], sort_keys=True, allow_nan=False)
                == json.dumps(scope, sort_keys=True, allow_nan=False), 'preparation-scope-not-exact')
        return decode(self.channel.read('preparation', scope))


def old(request):
    from canonical_storage_transition import prepare_storage
    from mq_lab_cutover_live import ControlLivePorts
    approval = request['expected_approval']
    loaded_source(request, 'old', approval['base'])
    channel = ScratchChannel(request['root'], request['socket'])
    prepared = prepare_storage(request['common_dir'], request['backup_path'],
                               request['reviewed_v1_sha256'], PreparationScope(request, channel))
    if prepared.get('action') != 'prepared':
        return prepared
    storage = journal(request)
    ports = ControlLivePorts(request, channel)
    admitted = admit_control_upgrade(request['number'], request['common_dir'], storage, ports)
    if admitted.get('action') != 'admitted':
        return admitted
    # A successful old send can confirm but cannot adopt: this ports instance
    # refuses read_fresh_successor. New source needs a separately launched PID.
    result = run_control_upgrade_once(request['number'], request['common_dir'], storage, ports)
    if request.get('fault') == 'duplicate-dispatch':
        # Actual dispatcher reentry with the durable pair still present. The
        # installer can only reconcile this record; never reacquire/send authority.
        return run_control_upgrade_once(request['number'], request['common_dir'], storage, ports)
    return result


def recovery(request):
    # No loaded_source subprocesses here: this READBACK observer phase allows
    # exactly one actual ref-read command. Parent independently verifies every
    # source path against the pinned commit before launching this process.
    def read_main():
        result = subprocess.run(['git', '--no-replace-objects', '-C', request['root'],
                                 'rev-parse', '--verify', 'refs/heads/main'],
                                capture_output=True, timeout=10, check=True)
        value = result.stdout.decode().strip()
        require(re.fullmatch('[0-9a-f]{40}', value), 'actual-main-unreadable')
        return value
    return read_control_upgrade_once(request['number'], request['common_dir'], journal(request), read_main)


def successor(request):
    from mq_lab_cutover_live import ControlLivePorts, actual_main
    channel = ScratchChannel(request['root'], request['socket'])
    storage = journal(request)
    record = storage.load_upgrade(request['number'])
    require(record is not None, 'no-upgrade-reservation-for-successor')
    merge = actual_main(Path(request['root']))
    loaded_source(request, 'new', merge)
    ports = ControlLivePorts(request, channel, successor=True)
    result = recover_control_upgrade_once(request['number'], request['common_dir'], storage, ports)
    if result.get('action') != 'adopted':
        return result
    # This fixed module/API is supplied by the ordinary operator owner. There
    # is no direct eligible-result adapter or version-label shortcut here.
    from mq_lab_cutover_ordinary import make_ordinary_ports
    from mq_lab_operator import run_command
    ordinary = request['ordinary']
    require(type(ordinary) is dict and type(ordinary.get('number')) is int
            and ordinary['number'] > 0 and ordinary['number'] != request['number'],
            'separate-ordinary-subject-required')
    normal_ports = make_ordinary_ports(request, channel)
    # Enqueue uses actual ordinary approval/evidence. Once retains the actual
    # operator's V2 reservation/confirmation/readback and canonical queue path.
    admitted = run_command('enqueue', ordinary['number'], request['common_dir'], normal_ports)
    if admitted.get('action') != 'admitted':
        return admitted
    return run_command('once', ordinary['number'], request['common_dir'], normal_ports)


def main(role):
    try:
        require(len(sys.argv) == 2 and role in ('old', 'recovery', 'successor'), 'invalid-entry-argv')
        result = {'old': old, 'recovery': recovery, 'successor': successor}[role](request_file(sys.argv[1]))
    except Exception as error:
        result = dict(action='hold', reason='cutover-entry-held', detail=type(error).__name__ + ':' + str(error)[:240])
    print(json.dumps(result, sort_keys=True, allow_nan=False), flush=True)
