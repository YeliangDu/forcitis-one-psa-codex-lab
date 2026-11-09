"""Existing-only control upgrade kernel over independently reviewed ports.

This does not bind mq.py, install source, initialize/migrate a journal, or create
admission. Native proof/authentication and any future real transport binding need
separate review. Recovery may audit/clean an existing effect, never resend it.
"""
from contextlib import contextmanager, nullcontext
import copy
import fcntl
import os
from pathlib import Path
import re
import stat

from mq_queue import LINE, Queue
from mq_lab_journal_v2 import _canonical_approval


NAME = 'forcitis-one-psa-codex-lab'
LIMIT = 1024 * 1024


def _require(condition, reason):
    if not condition:
        raise ValueError(reason)


def _hold(reason):
    return dict(action='hold', reason=reason)


def _regular(path, *, missing=False):
    try:
        info = path.lstat()
    except FileNotFoundError:
        if missing:
            return None
        raise
    _require(stat.S_ISREG(info.st_mode) and info.st_uid == os.geteuid()
             and info.st_nlink == 1 and not stat.S_IMODE(info.st_mode) & 0o022
             and info.st_size <= LIMIT, 'unsafe-canonical-file')
    return info


def _directory(common_dir, journal):
    raw = os.fspath(common_dir)
    _require(type(raw) is str and raw and '..' not in raw.split('/'), 'invalid-common-dir')
    common = Path(raw)
    _require(common.is_absolute() and str(common) == raw and common.name == '.git'
             and common.parent.name == NAME and common.resolve(strict=True) == common
             and common.is_dir(), 'unverified-physical-Lab')
    directory = common / 'merge-queue'
    _require(directory.resolve(strict=True) == directory, 'nonphysical-queue-directory')
    info = directory.stat()
    _require(stat.S_ISDIR(info.st_mode) and info.st_uid == os.geteuid()
             and stat.S_IMODE(info.st_mode) == 0o700, 'queue-directory-not-owned-private')
    _require(journal.storage.path == directory / 'releases.sqlite3', 'journal-not-canonical')
    journal.storage.check()  # Existing exact SQLite only, never initialization.
    for suffix in ('', '.heads', '.lock', '.heads-lock', '.merge-lock'):
        _regular(directory / ('merge.queue' + suffix), missing=suffix not in ('', '.heads'))
    return directory


@contextmanager
def _locked(path):
    _regular(path)
    fd = os.open(path, os.O_RDWR | os.O_NOFOLLOW | os.O_NONBLOCK)
    try:
        info = os.fstat(fd)
        current = _regular(path)
        _require(current is not None and (info.st_dev, info.st_ino) == (current.st_dev, current.st_ino),
                 'lock-identity-moved')
        fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        yield fd
    finally:
        os.close(fd)


def _read(path):
    _regular(path)
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(fd, 'r', encoding='utf-8') as stream:
        info = os.fstat(stream.fileno())
        current = _regular(path)
        _require(current is not None and (info.st_dev, info.st_ino) == (current.st_dev, current.st_ino),
                 'canonical-file-identity-moved')
        text = stream.read(LIMIT + 1)
        _require(len(text.encode('utf-8')) <= LIMIT, 'oversize-canonical-state')
        return text.splitlines()


def _current(queue, number, head=None):
    numbers = []
    for line in _read(Path(queue.path)):
        if not line.strip() or line.lstrip().startswith('#'):
            continue
        match = LINE.fullmatch(line)
        _require(match is not None, 'invalid-canonical-membership')
        value = int(match.group(1))
        _require(value > 0 and value not in numbers, 'ambiguous-canonical-membership')
        numbers.append(value)
    _require(numbers == queue.numbers(), 'canonical-membership-moved')
    if not numbers:
        return None
    _require(numbers[0] == number and not queue.deps(number), 'not-requested-independent-FIFO')
    heads = {}
    for line in _read(Path(queue.path + '.heads')):
        if not line.strip():
            continue
        match = re.fullmatch(r'([1-9][0-9]*) ([0-9a-f]{40})', line)
        _require(match is not None, 'invalid-head-stamp')
        key = int(match.group(1))
        _require(key not in heads, 'ambiguous-head-stamp')
        heads[key] = match.group(2)
    stamp = heads.get(number)
    _require(stamp is not None and queue.head_of(number) == stamp, 'missing-or-moved-head-stamp')
    _require(head is None or head == stamp, 'stale-upgrade-admission')
    return stamp



def _lock_still_named(path, fd):
    opened, named = os.fstat(fd), _regular(path)
    _require((opened.st_dev, opened.st_ino) == (named.st_dev, named.st_ino),
             'lock-identity-moved-before-effect')


@contextmanager
def _cas_window(directory, approval, runner_fd, merge_fd):
    """Leaf locks only for the final snapshot and ONE transport call.

    Never call a Queue method in this scope: its independently opened flock
    descriptors would block against these locks in this same process.
    Outer runner/merge locks remain held through reconciliation.
    """
    queue_lock = directory / 'merge.queue.lock'
    heads_lock = directory / 'merge.queue.heads-lock'
    # Preparation has published all lock inodes. No replacement/bootstrap here.
    _regular(queue_lock)
    _regular(heads_lock)
    with _locked(queue_lock) as queue_fd:
        with _locked(heads_lock) as heads_fd:
            for path, fd in ((directory / 'control-upgrade.runner-lock', runner_fd),
                             (directory / 'merge.queue.merge-lock', merge_fd),
                             (queue_lock, queue_fd), (heads_lock, heads_fd)):
                _lock_still_named(path, fd)
            members = []
            for line in _read(directory / 'merge.queue'):
                if not line.strip() or line.lstrip().startswith('#'):
                    continue
                match = LINE.fullmatch(line)
                _require(match is not None, 'invalid-locked-membership')
                number = int(match.group(1))
                _require(number > 0 and number not in [n for n, _ in members],
                         'ambiguous-locked-membership')
                deps = match.group(2)
                if deps:
                    values = deps.split(',')
                    _require(all(re.fullmatch('[1-9][0-9]*', v) for v in values)
                             and len(set(values)) == len(values), 'invalid-locked-dependencies')
                members.append((number, deps))
            _require(members and members[0] == (approval['number'], None),
                     'requested-independent-FIFO-moved-before-CAS')
            heads = {}
            for line in _read(directory / 'merge.queue.heads'):
                if not line.strip():
                    continue
                match = re.fullmatch(r'([1-9][0-9]*) ([0-9a-f]{40})', line)
                _require(match is not None and int(match.group(1)) not in heads,
                         'ambiguous-locked-head-stamp')
                heads[int(match.group(1))] = match.group(2)
            _require(heads.get(approval['number']) == approval['head'],
                     'head-stamp-moved-before-CAS')
            yield


def read_control_upgrade_once(number, physical_common_dir, journal, read_main):
    """READBACK phase only: no authority/send/confirm/adopt/cleanup callable.

    An applied ref alone does not prove ordered parents or healthy new source.
    Therefore even an observed changed main is HOLD, retaining the durable pair.
    The observer's narrow read-only Git allowlist can safely bind read_main.
    """
    try:
        _require(type(number) is int and number > 0, 'invalid-requested-PR')
        directory = _directory(physical_common_dir, journal)
        _regular(directory / 'control-upgrade.runner-lock')
        _regular(directory / 'merge.queue.merge-lock')
        with _locked(directory / 'control-upgrade.runner-lock'):
            with _locked(directory / 'merge.queue.merge-lock'):
                record = journal.load_upgrade(number)
                _require(record is not None, 'no-durable-upgrade-to-recover')
                approval, _, _ = _approval(record['approval'], number, record['approval']['head'])
                observed = read_main()
                _require(type(observed) is str and re.fullmatch('[0-9a-f]{40}', observed),
                         'missing-actual-main-ref')
                return dict(action='hold', reason='readback-only-needs-parents-source-and-health',
                            phase=record['phase'], observed_main=observed,
                            expected_base=approval['base'], recorded_merge=record['merge_sha'],
                            release_authority=False, allows_new_write=False)
    except Exception:
        return _hold('upgrade-readback-unavailable-or-inexact')


def recover_control_upgrade_once(number, physical_common_dir, journal, read_ports):
    """Full existing-record recovery; cannot reserve or reach a send method."""
    try:
        _require(type(number) is int and number > 0, 'invalid-requested-PR')
        directory = _directory(physical_common_dir, journal)
        _regular(directory / 'control-upgrade.runner-lock')
        _regular(directory / 'merge.queue.merge-lock')
        with _locked(directory / 'control-upgrade.runner-lock'):
            with _locked(directory / 'merge.queue.merge-lock'):
                record = journal.load_upgrade(number)
                _require(record is not None, 'no-durable-upgrade-to-recover')
                stamp = record['approval']['head']
                queue = Queue(str(directory / 'merge.queue'))
                return _recover(number, stamp, queue, journal, read_ports, record)
    except Exception:
        return _hold('existing-upgrade-recovery-unavailable-or-inexact')


def admit_control_upgrade(number, physical_common_dir, journal, trusted_ports):
    """Explicit scratch admission after preparation and full verified authority.

    Never initializes storage or changes an existing reservation/admission.
    Installer rechecks the complete authority after reacquiring these locks.
    """
    try:
        directory = _directory(physical_common_dir, journal)
        with _locked(directory / 'control-upgrade.runner-lock'):
            with _locked(directory / 'merge.queue.merge-lock'):
                if journal.load_upgrade(number) is not None:
                    return _hold('existing-upgrade-is-recovery-only')
                queue = Queue(str(directory / 'merge.queue'))
                _require(_read(Path(queue.path)) == []
                         and _read(Path(queue.path + '.heads')) == [], 'admission-not-empty')
                value = trusted_ports.read_verified_upgrade_authority(number)
                approval, _, _ = _approval(value, number, value['head'])
                queue.set_head(number, approval['head'])
                _require(queue.enqueue(number) == [number], 'admission-not-new')
                _require(_current(queue, number, approval['head']) == approval['head'],
                         'admission-not-durable')
                return dict(action='admitted', reason='exact-control-upgrade-admitted')
    except Exception:
        return _hold('control-upgrade-admission-unavailable-or-inexact')


def _approval(value, number, head):
    approval, text, digest = _canonical_approval(value)
    _require(approval['number'] == number and approval['head'] == head,
             'authority-not-exact-FIFO-subject')
    return approval, text, digest


def _boundary(ports, approval, text, digest, queue):
    _require(_current(queue, approval['number'], approval['head']) == approval['head'], 'FIFO-moved')
    current, current_text, current_digest = _approval(
        ports.reread_write_boundary(copy.deepcopy(approval)), approval['number'], approval['head'])
    _require(current_text == text and current_digest == digest and current == approval,
             'independent-upgrade-authority-moved')
    _require(_current(queue, approval['number'], approval['head']) == approval['head'], 'FIFO-moved-after-boundary')


def _recover(number, stamp, queue, journal, ports, record):
    approval, _, _ = _approval(record['approval'], number, stamp)
    if record['phase'] == 'reserved':
        proof = ports.read_merged(copy.deepcopy(approval))
        result = journal.confirm_upgrade(approval, proof)
        if result.get('action') not in ('confirmed', 'already-confirmed'):
            return _hold('upgrade-effect-unconfirmed')
        record = journal.load_upgrade(number)
        _require(record is not None and record['approval'] == approval
                 and record['phase'] in ('confirmed', 'adopted'), 'confirmation-not-durable')
    _require(record['phase'] in ('confirmed', 'adopted'), 'unknown-upgrade-phase')
    health = ports.read_fresh_successor(copy.deepcopy(approval), record['merge_sha'])
    result = journal.adopt_public_upgrade(approval, health)
    if result.get('action') not in ('adopted', 'already-adopted'):
        return _hold('fresh-public-successor-not-adoptable')
    final = journal.load_upgrade(number)
    _require(final is not None and final['approval'] == approval and final['phase'] == 'adopted'
             and final['merge_sha'] == record['merge_sha'] == final['adopted_main'], 'adoption-not-durable')
    # A prior process may have died between remove() and drop_head().
    # Validate all remaining lines before idempotently completing cleanup; never
    # remove a different admission or require the upgrade to remain FIFO forever.
    members = _read(Path(queue.path))
    parsed = []
    for line in members:
        if not line.strip() or line.lstrip().startswith('#'):
            continue
        match = LINE.fullmatch(line)
        _require(match is not None, 'malformed-recovery-membership')
        value = int(match.group(1))
        _require(value > 0 and value not in parsed, 'ambiguous-recovery-membership')
        parsed.append(value)
    if number in parsed:
        _require(_current(queue, number, stamp) == stamp, 'adopted-FIFO-changed')
        queue.remove(number)
    remaining_head = queue.head_of(number)
    _require(remaining_head in (None, stamp), 'adopted-head-changed')
    if remaining_head is not None:
        queue.drop_head(number)
    _require(number not in queue.numbers() and queue.head_of(number) is None, 'adopted-cleanup-incomplete')
    return dict(action='adopted', reason='exact-public-successor-audited-and-FIFO-cleared',
                merge_sha=final['merge_sha'])


def run_control_upgrade_once(number, physical_common_dir, journal, trusted_ports, *, transport_lease=None):
    """Caller-supplied fake ports only; runner -> merge locks bound all effects.

    A full independently verified authority RECORD is required, never True or a
    candidate-produced receipt. No default ports, activation, retries or fallback.
    A BaseException interruption propagates with the committed pair retained.
    """
    try:
        _require(type(number) is int and number > 0, 'invalid-requested-PR')
        directory = _directory(physical_common_dir, journal)
        with _locked(directory / 'control-upgrade.runner-lock') as runner_fd:
            with _locked(directory / 'merge.queue.merge-lock') as merge_fd:
                queue = Queue(str(directory / 'merge.queue'))
                record = journal.load_upgrade(number)
                if record is not None:
                    return _recover(number, record['approval']['head'], queue, journal, trusted_ports, record)
                stamp = _current(queue, number)
                if stamp is None:
                    return dict(action='idle', reason='canonical-FIFO-empty')
                approval, text, digest = _approval(
                    trusted_ports.read_verified_upgrade_authority(number), number, stamp)
                _boundary(trusted_ports, approval, text, digest, queue)
                reserved = journal.reserve_upgrade(approval)
                if reserved.get('action') != 'newly-reserved':
                    return _hold('upgrade-reservation-not-new-or-commit-unknown')
                record = journal.load_upgrade(number)
                _require(record is not None and record['approval'] == approval and record['phase'] == 'reserved',
                         'paired-reservation-not-readable')
                _boundary(trusted_ports, approval, text, digest, queue)
                try:
                    with _cas_window(directory, approval, runner_fd, merge_fd):
                        # Optional explicit composition seam; reservation/locks stay owned here.
                        lease = (transport_lease(copy.deepcopy(approval), runner_fd, merge_fd)
                                 if transport_lease is not None else nullcontext())
                        with lease:
                            trusted_ports.send(copy.deepcopy(approval), expected_base=approval['base'])
                except Exception:
                    pass  # Timeout/error is unknown, not permission to retry.
                return _recover(number, stamp, queue, journal, trusted_ports, record)
    except Exception:
        return _hold('isolated-control-install-unavailable-or-inexact')
