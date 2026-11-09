"""Isolated P1 replay CLI. No native transport, admission or writer capability.

Witnessed live operation is deliberately a separate unimplemented trust seam.
Neither input Booleans nor a successful replay can cross that seam.
"""
import argparse
import base64
import copy
import fcntl
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import sys
from types import SimpleNamespace

B = '77e872e8ba9661d3294d3a9337c8e5c956b70641'
LEAVES = {
    'checkpoint': 'cd183a60a6658ca0194cd848ece39a9df906761e3dc988b1c605b9480204a9d6',
    'association': '6b8796a22cbc6417194a37836bcb73eafa1fe94507303694ad29696b7e64eadb',
    'receipt': '87bd96b150809e5e9a68ca62fdf03566dbcb07ca043937ab49c7b37fb68bf165',
}
MISSING = ['native-authentication', 'signed-card-authorization', 'native-compact-restart',
           'effective-permissions', 'active-control-provenance',
           'hosted-producer-authentication', 'independent-native-review',
           'existing-readonly-admission-proof']


def require(value, reason):
    if not value:
        raise ValueError(reason)


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), allow_nan=False)


def unique(pairs):
    value = {}
    for key, item in pairs:
        require(key not in value, 'duplicate-json-key')
        value[key] = item
    return value


def invalid_constant(value):
    raise ValueError('nonfinite-json')


def parse(raw):
    return json.loads(raw.decode('utf-8'), object_pairs_hook=unique, parse_constant=invalid_constant)


def hex_sha(value, size=64):
    return type(value) is str and re.fullmatch(r'[0-9a-f]{' + str(size) + '}', value) is not None


def positive(value):
    return type(value) is int and value > 0


def physical(value):
    path = Path(value)
    require(path.is_absolute() and str(path) == str(value)
            and path.resolve(strict=True) == path, 'nonphysical-path')
    require(not any(p.is_symlink() for p in (path, *path.parents)), 'symlink-path')
    return path


def read_bytes(path, limit):
    path = physical(str(path))
    before = path.stat()
    require(stat.S_ISREG(before.st_mode) and 0 < before.st_size <= limit, 'input-file-bound')
    raw = path.read_bytes()
    after = path.stat()
    require((before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns, before.st_ctime_ns) ==
            (after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns, after.st_ctime_ns)
            and len(raw) <= limit, 'input-file-changed')
    return raw


def closure_sources(args):
    raw = read_bytes(Path(args.source_closure), 1024 * 1024)
    require(hex_sha(args.source_closure_sha256) and sha(raw) == args.source_closure_sha256,
            'external-source-closure-pin')
    closure = parse(raw)
    require(type(closure) is dict and closure.get('contract') == 'labctl-readonly-p1/1',
            'wrong-entry-contract')
    files, slots, entry = closure['files_sha256'], closure['slots'], closure['entry']
    require(type(files) is dict and 5 <= len(files) <= 32 and type(slots) is dict
            and set(slots) == {'checkpoint', 'association', 'receipt', 'projection'}
            and len(set(slots.values())) == 4 and entry in files and entry not in slots.values(),
            'incomplete-source-closure')
    relative = PurePosixPath(entry)
    require(not relative.is_absolute() and '..' not in relative.parts, 'unsafe-entry-path')
    own = physical(str(Path(__file__).absolute()))
    root = own
    for _ in relative.parts:
        root = root.parent
    require(root.joinpath(*relative.parts) == own, 'entry-path-identity')
    sources = {}
    for name, pin in files.items():
        path = PurePosixPath(name)
        require(type(name) is str and str(path) == name and not path.is_absolute()
                and '..' not in path.parts and hex_sha(pin), 'unsafe-source-path')
        actual = physical(str(root / name))
        require(root in actual.parents, 'source-outside-closure')
        content = read_bytes(actual, 1024 * 1024)
        require(sha(content) == pin, 'source-byte-pin')
        sources[name] = (actual, content)
    for slot, path in slots.items():
        require(path in sources and (slot not in LEAVES or files[path] == LEAVES[slot]),
                'immutable-leaf-pin')
    modules = {}
    for slot, name in slots.items():
        path, content = sources[name]
        module_name = '_isolated_p1_' + slot
        spec = importlib.util.spec_from_file_location(module_name, path)
        module = importlib.util.module_from_spec(spec)
        # Compile already pinned bytes: imports are stdlib, never path discovery.
        exec(compile(content, str(path), 'exec'), module.__dict__)
        modules[slot] = module
    return modules


def fixture_receipt_request(verification):
    """Untrusted replay seam only. No default network transport exists."""
    def request(path, pages=False, binary=False):
        expected = verification['expected']
        if path == '' and not pages and not binary:
            return copy.deepcopy(verification['run']['repository'])
        if path in (f"actions/runs/{expected['run_id']}",
                    f"actions/runs/{expected['run_id']}/attempts/{expected['attempt']}") and not pages and not binary:
            return copy.deepcopy(verification['run'])
        if path == f"actions/runs/{expected['run_id']}/artifacts?per_page=100" and pages and not binary:
            rows = [] if verification.get('missing') else [copy.deepcopy(verification['metadata'])]
            return [dict(total_count=len(rows), artifacts=rows)]
        if path == f"actions/artifacts/{expected['artifact_id']}/zip" and binary and not pages:
            encoded = verification['zip_base64']
            require(type(encoded) is str and len(encoded) <= 45 * 1024 * 1024, 'replay-archive-bound')
            return base64.b64decode(encoded, validate=True)
        raise ValueError('unprovided-replay-read-route')
    return request


def validate_replay(request, modules, closure_pin):
    require(request.get('kind') == 'labctl-p1-replay-request/1'
            and request.get('action') == 'replay-readonly'
            and request.get('mode') == 'untrusted-hosted-replay', 'not-isolated-replay')
    require(request['candidate_control_closure'] == closure_pin, 'wrong-candidate-closure')
    assignment = request['assignment']
    require(type(assignment) is dict and set(assignment) == {
        'card', 'revision', 'phase', 'role', 'session_id', 'cwd', 'branch', 'base', 'head', 'task'
    } and positive(assignment['card']) and positive(assignment['revision'])
            and assignment['base'] == B and hex_sha(assignment['head'], 40)
            and type(assignment['task']) is str and 0 < len(assignment['task'].encode()) <= 1024,
            'assignment-identity')
    cwd = physical(assignment['cwd'])
    require(os.getcwd() == str(cwd), 'wrong-actual-replay-cwd')
    root = cwd.parent
    scope = request['scope']
    require(scope['card'] == assignment['card'] and type(scope['body']) is str
            and sha(scope['body'].encode()) == scope['sha256']
            and assignment['task'] in scope['body'], 'scope-binding')
    frozen = request['frozen_acceptance']
    require(hex_sha(request['test_A'], 40) and request['test_A'] == frozen['A']
            and hex_sha(frozen['manifest_sha256']), 'frozen-acceptance-binding')
    claim = request['claim']
    require(claim['kind'] == 'initial' and claim['issue'] == assignment['card']
            and claim['generation'] == assignment['revision']
            and claim['role'] == assignment['role'] and claim['thread_id'] == assignment['session_id']
            and claim['phase'] == assignment['phase'] and claim['head'] == assignment['head']
            and claim['cwd'] == assignment['cwd'], 'claim-assignment-binding')
    checkpoint = request['checkpoint']
    cp_path = physical(checkpoint['path'])
    require(root in cp_path.parents, 'checkpoint-outside-replay')
    args = SimpleNamespace(expected_card=assignment['card'], expected_revision=assignment['revision'],
        expected_role=assignment['role'], expected_session=assignment['session_id'],
        expected_cwd=str(cwd), lab_root=str(root), base=B,
        checkpoint=str(cp_path), checkpoint_sha256=checkpoint['sha256'])
    event = dict(hook_event_name='SessionStart', source='resume', cwd=str(cwd),
                 session_id=assignment['session_id'])
    original_stdin = sys.stdin
    try:
        sys.stdin = io.TextIOWrapper(io.BytesIO(canonical(event).encode()), encoding='utf-8')
        restored = json.loads(modules['checkpoint'].recover(args))
    finally:
        sys.stdin = original_stdin
    context = json.loads(restored['hookSpecificOutput']['additionalContext'])
    require(context['task'] == assignment['task'], 'checkpoint-task-binding')
    projection = request['native_projection']
    require(type(projection) is dict and projection['claim'] == claim, 'projection-current-claim')
    prompt = bytes.fromhex(projection['prompt_hex'])
    association = modules['projection'].match_dispatch_ack(claim, prompt, projection['legacy_pages'],
                                                          window_proof=projection['window_proof'])
    require(association['action'] == 'matched' and association['claim_id'] == claim['dispatch_claim_id'],
            'native-replay-association')
    artifact = request['artifact']
    artifact_path = physical(artifact['path'])
    require(root in artifact_path.parents and artifact['revision'] == assignment['revision']
            and sha(read_bytes(artifact_path, 1024 * 1024)) == artifact['sha256'], 'artifact-binding')
    verification = request['verification']
    expected = verification['expected']
    require(expected['head'] == assignment['head'] and expected['base'] == assignment['base']
            and verification['run']['head_branch'] == assignment['branch'], 'verification-assignment-binding')
    reader = modules['receipt'].GithubRead(request=fixture_receipt_request(verification), expected=expected)
    receipts = reader.artifacts(expected['run_id'], expected['attempt'])
    require(len(receipts) == 1, 'missing-verified-replay-receipt')
    reviews = request['reviews']
    require(type(reviews) is list and len(reviews) == 2, 'missing-review-shape')
    reviewers = set()
    for review, kind in zip(reviews, ('first', 'final')):
        require(type(review) is dict and review.get('kind') == kind
                and review.get('head') == assignment['head']
                and type(review.get('reviewer')) is str and bool(review['reviewer'])
                and type(review.get('author')) is str and bool(review['author'])
                and review['reviewer'] != review['author'] and review['reviewer'] not in reviewers,
                'stale-or-shared-review-shape')
        reviewers.add(review['reviewer'])
    require(reviews[0]['author'] == reviews[1]['author'], 'review-author-binding')
    intent = request['intent']
    require(type(intent) is dict and set(intent) == {'id', 'prompt_sha256', 'state', 'actual_send_count'}
            and intent['id'] == claim['dispatch_claim_id'] and intent['prompt_sha256'] == sha(prompt)
            and intent['state'] == 'planned-unissued' and type(intent['actual_send_count']) is int
            and intent['actual_send_count'] == 0, 'unissued-intent-binding')
    # JSON auth/proof Booleans are intentionally excluded from authority decisions.
    binding = {key: request[key] for key in (
        'assignment', 'scope', 'checkpoint', 'claim', 'native_projection', 'artifact',
        'verification', 'reviews', 'test_A', 'frozen_acceptance', 'candidate_control_closure', 'intent')}
    return sha(canonical(binding).encode())


def empty_state(closure_pin):
    return dict(claim_id=None, intent_id=None, intent_count=0, actual_native_send_count=0,
                candidate_closure_sha256=closure_pin, authoritative_native_turn=None)


def output(payload, closure_pin, consistency='not-validated', reason=None):
    result = dict(action='hold', missing_proof=list(MISSING), replay_consistency=consistency,
                  replay_assignment=None, replay_artifact_sha256=None, state=empty_state(closure_pin),
                  native_authenticated=False, operational_admission=False, activation_authority=False,
                  queue_write_authority=False, writer_authority=False)
    if payload:
        result.update(replay_assignment=payload['tasks']['current'],
                      replay_artifact_sha256=payload['artifact_sha256'], state=payload['state'])
    if reason:
        result['reason'] = reason
    return result


def load_state(path, closure_pin):
    if not path.exists():
        return None
    envelope = parse(read_bytes(path, 8 * 1024 * 1024))
    payload = envelope['payload']
    require(sha(canonical(payload).encode()) == envelope['sha256'], 'replay-state-integrity')
    require(payload['state']['candidate_closure_sha256'] == closure_pin
            and payload['state']['actual_native_send_count'] == 0
            and payload['state']['intent_count'] == 1
            and payload['effects'][0]['state'] == 'planned-unissued', 'replay-state-binding')
    return payload


def save_state(path, payload):
    temporary = path.with_name('state.pending.json')
    require(not temporary.exists() and not temporary.is_symlink(), 'pending-state-requires-inspection')
    raw = canonical(dict(payload=payload, sha256=sha(canonical(payload).encode()))).encode()
    descriptor = os.open(str(temporary), os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    try:
        with os.fdopen(descriptor, 'wb') as stream:
            stream.write(raw)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(str(temporary), str(path))
        directory = os.open(str(path.parent), os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    except BaseException:
        # A leftover pending record is an explicit HOLD, never a resend trigger.
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__, allow_abbrev=False)
    parser.add_argument('command', choices=('readonly-p1', 'inspect-p1'))
    parser.add_argument('--request', required=True)
    parser.add_argument('--state-dir', required=True)
    parser.add_argument('--source-closure', required=True)
    parser.add_argument('--source-closure-sha256', required=True)
    args = parser.parse_args()
    payload = None
    try:
        modules = closure_sources(args)
        state_dir = physical(args.state_dir)
        # This candidate has no configurable canonical backend.
        require(state_dir.is_dir() and state_dir.name == 'isolated-state'
                and state_dir.parent == Path(os.getcwd()).parent
                and not (state_dir.parent / '.git').exists(), 'isolated-state-boundary')
        mode = state_dir.stat()
        require(mode.st_uid == os.geteuid() and mode.st_mode & 0o077 == 0, 'private-state-required')
        request = parse(read_bytes(Path(args.request), 48 * 1024 * 1024))
        require(type(request) is dict, 'request-object-required')
        lock_path = state_dir / 'replay.lock'
        require(not lock_path.is_symlink(), 'unsafe-replay-lock')
        descriptor = os.open(str(lock_path), os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600)
        try:
            fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
            require(not (state_dir / 'state.pending.json').exists(), 'pending-state-requires-inspection')
            state_path = state_dir / 'state.json'
            payload = load_state(state_path, args.source_closure_sha256)
            if args.command == 'inspect-p1':
                require(request.get('kind') == 'labctl-p1-inspect/1' and payload is not None
                        and request.get('card') == payload['tasks']['current']['card']
                        and request.get('revision') == payload['tasks']['current']['revision'], 'inspect-current-binding')
                result = output(payload, args.source_closure_sha256, 'validated', 'persisted-untrusted-replay-only')
            else:
                binding = validate_replay(request, modules, args.source_closure_sha256)
                if payload is not None:
                    require(payload['binding_sha256'] == binding, 'late-or-different-replay-refused')
                else:
                    claim_id = request['claim']['dispatch_claim_id']
                    state = dict(claim_id=claim_id, intent_id=request['intent']['id'], intent_count=1,
                                 actual_native_send_count=0, candidate_closure_sha256=args.source_closure_sha256,
                                 authoritative_native_turn=None)
                    payload = dict(kind='isolated-p1-replay-state/1', binding_sha256=binding,
                                   tasks=dict(current=copy.deepcopy(request['assignment'])),
                                   current_claim=copy.deepcopy(request['claim']),
                                   effects=[copy.deepcopy(request['intent'])],
                                   events=[dict(kind='replay-validated', claim_id=claim_id,
                                                actual_native_send=False)], state=state,
                                   artifact_sha256=request['artifact']['sha256'])
                    save_state(state_path, payload)
                result = output(payload, args.source_closure_sha256, 'validated', 'replay-only-live-proof-missing')
        finally:
            os.close(descriptor)
    except (ValueError, KeyError, TypeError, AttributeError, IndexError, OSError,
            RuntimeError, UnicodeError, OverflowError, RecursionError):
        result = output(payload, args.source_closure_sha256, reason='missing-mismatched-or-untrusted-proof')
    print(canonical(result))
    return 78


if __name__ == '__main__':
    raise SystemExit(main())
