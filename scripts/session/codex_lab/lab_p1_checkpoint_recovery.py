"""Read-only, externally pinned checkpoint recovery candidate; no activation authority."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import sys

CHECKPOINT_FIELDS = {
    'kind', 'card', 'revision', 'role', 'session_id', 'lab_root', 'cwd',
    'base', 'task', 'next_action',
}


class Refusal(ValueError):
    """Input cannot authorize task recovery."""


def require(condition):
    if not condition:
        raise Refusal()


def unique_object(pairs):
    value = {}
    for key, item in pairs:
        require(key not in value)
        value[key] = item
    return value


def decode(raw):
    return json.loads(raw.decode('utf-8'), object_pairs_hook=unique_object,
                      parse_constant=lambda value: refuse_constant())


def refuse_constant():
    raise Refusal()


def physical_path(value):
    require(type(value) is str and bool(value))
    path = Path(value)
    require(path.is_absolute() and str(path) == value)
    require(str(path.resolve(strict=True)) == value)
    # Reject even a symlink whose resolved spelling happens to be unchanged.
    for component in (path, *path.parents):
        require(not stat.S_ISLNK(component.lstat().st_mode))
    return path


def file_identity(info):
    return (info.st_dev, info.st_ino, info.st_uid, info.st_mode,
            info.st_size, info.st_mtime_ns, info.st_ctime_ns)


def read_checkpoint(path):
    before = path.lstat()
    require(stat.S_ISREG(before.st_mode))
    require(before.st_uid == os.geteuid() and before.st_mode & 0o077 == 0)
    require(before.st_nlink == 1 and 0 < before.st_size <= 8192)
    # No creation, writes or symlink following. Fail closed on unsupported hosts.
    require(hasattr(os, 'O_NOFOLLOW') and hasattr(os, 'O_NONBLOCK'))
    descriptor = os.open(str(path), os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    try:
        opened = os.fstat(descriptor)
        require(file_identity(before) == file_identity(opened))
        chunks = []
        remaining = 8193
        while remaining:
            chunk = os.read(descriptor, remaining)
            if not chunk:
                break
            chunks.append(chunk)
            remaining -= len(chunk)
        raw = b''.join(chunks)
        require(0 < len(raw) <= 8192)
        require(file_identity(opened) == file_identity(os.fstat(descriptor)))
        require(file_identity(opened) == file_identity(path.lstat()))
        require(physical_path(str(path)) == path)
        return raw
    finally:
        os.close(descriptor)


def serialize(value):
    return json.dumps(value, ensure_ascii=True, separators=(',', ':'))


def recover(args):
    require(args.expected_card > 0 and args.expected_revision > 0)
    require(re.fullmatch(r'[0-9a-f]{64}', args.checkpoint_sha256) is not None)
    require(re.fullmatch(r'[0-9a-f]{40}', args.base) is not None)
    require(bool(args.expected_role) and bool(args.expected_session))
    root = physical_path(args.lab_root)
    cwd = physical_path(args.expected_cwd)
    require(root.is_dir() and cwd.is_dir() and root in cwd.parents)
    require(os.getcwd() == str(cwd))
    event_raw = sys.stdin.buffer.read(8193)
    require(len(event_raw) <= 8192)
    event = decode(event_raw)
    require(type(event) is dict)
    require(event.get('hook_event_name') == 'SessionStart')
    require(event.get('source') in ('compact', 'resume'))
    require(event.get('session_id') == args.expected_session)
    require(event.get('cwd') == str(cwd))
    path = physical_path(args.checkpoint)
    require(root in path.parents)
    raw = read_checkpoint(path)
    digest = hashlib.sha256(raw).hexdigest()
    require(digest == args.checkpoint_sha256)
    checkpoint = decode(raw)
    require(type(checkpoint) is dict and set(checkpoint) == CHECKPOINT_FIELDS)
    require(checkpoint['kind'] == 'lab-p1-authorized-checkpoint/1')
    require(type(checkpoint['card']) is int and type(checkpoint['revision']) is int)
    expected = dict(card=args.expected_card, revision=args.expected_revision,
                    role=args.expected_role, session_id=args.expected_session,
                    lab_root=str(root), cwd=str(cwd), base=args.base)
    for key, value in expected.items():
        require(checkpoint[key] == value and type(checkpoint[key]) is type(value))
    for key in ('task', 'next_action'):
        require(type(checkpoint[key]) is str and bool(checkpoint[key].strip()))
    context = dict(checkpoint)
    context['kind'] = 'lab-p1-restored-context/1'
    context['checkpoint_sha256'] = digest
    encoded_context = serialize(context)
    require(len(encoded_context.encode('utf-8')) <= 2048)
    output = serialize(dict(hookSpecificOutput=dict(
        hookEventName='SessionStart', additionalContext=encoded_context)))
    require(len((output + '\n').encode('utf-8')) <= 4096)
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    parser.add_argument('--checkpoint', required=True)
    parser.add_argument('--checkpoint-sha256', required=True)
    parser.add_argument('--expected-card', required=True, type=int)
    parser.add_argument('--expected-revision', required=True, type=int)
    parser.add_argument('--expected-role', required=True)
    parser.add_argument('--expected-session', required=True)
    parser.add_argument('--expected-cwd', required=True)
    parser.add_argument('--lab-root', required=True)
    parser.add_argument('--base', required=True)
    args = parser.parse_args()
    try:
        output = recover(args)
    except (ValueError, OSError, RuntimeError, RecursionError):
        # Never echo untrusted input, task text, paths or exception details.
        print('{"systemMessage":"HOLD: current checkpoint authority not established"}')
        return 78
    print(output)
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
