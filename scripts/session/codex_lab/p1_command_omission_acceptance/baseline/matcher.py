"""Bounded pure matching of collector-supplied native acknowledgement envelopes.

No I/O, filesystem validation, App/API or dispatch/resume/authority capability.
The outer collector authenticates native pages, role/CWD and exact send ordering.
Matching text is correlation only, never evidence that execution waited for it.
"""
import hashlib
import json
from pathlib import PurePosixPath
import re
import uuid


PREFIX = 'LAB_DISPATCH_ACK '
CLAIM_FIELDS = {'kind', 'thread_id', 'initial_claim_id', 'dispatch_claim_id', 'generation',
                'role', 'issue', 'phase', 'head', 'cwd', 'handoff', 'prompt_sha256',
                'pre_send_cursor', 'preexisting_turn_ids'}
MAX_PAGES, MAX_TURNS, MAX_ITEMS = 4, 64, 256
MAX_TEXT, MAX_PROMPT = 16 * 1024, 64 * 1024
TERMINAL = {'completed', 'failed', 'interrupted'}
STATUSES = TERMINAL | {'inProgress'}


def _require(value, reason):
    if not value:
        raise ValueError(reason)


def _uuid(value):
    _require(type(value) is str and len(value) == 36, 'invalid-native-identity')
    parsed = uuid.UUID(value)
    _require(str(parsed) == value and parsed.variant == uuid.RFC_4122
             and parsed.version in range(1, 9), 'noncanonical-native-identity')


def _text(value, maximum):
    return (type(value) is str and 0 < len(value) <= maximum
            and not any(ord(c) < 32 or 127 <= ord(c) <= 159 for c in value))


def _positive(value):
    return type(value) is int and 0 < value <= 2**63 - 1


def _sha(value, length):
    return type(value) is str and re.fullmatch('[0-9a-f]{' + str(length) + '}', value) is not None


def _path(value, absolute):
    _require(_text(value, 2048) and '\\' not in value, 'invalid-assignment-path')
    path = PurePosixPath(value)
    _require(bool(path.parts) and str(path) == value and path.is_absolute() is absolute
             and '..' not in path.parts, 'noncanonical-assignment-path')
    return path


def _expected(claim, prompt):
    _require(type(claim) is dict and type(claim.get('kind')) is str
             and claim['kind'] in ('initial', 'resume'), 'invalid-dispatch-claim-kind')
    expected_fields = CLAIM_FIELDS | ({'predecessor_turn'} if claim['kind'] == 'resume' else set())
    _require(set(claim) == expected_fields, 'invalid-dispatch-claim-fields')
    for field in ('thread_id', 'initial_claim_id', 'dispatch_claim_id'):
        _uuid(claim[field])
    _require(_positive(claim['generation']) and _positive(claim['issue'])
             and _text(claim['role'], 128)
             and re.fullmatch(r'one-(?:lead|design|platform|test|dev-[a-z][a-z0-9-]*)', claim['role'])
             and _text(claim['phase'], 256) and _sha(claim['head'], 40)
             and _sha(claim['prompt_sha256'], 64) and _text(claim['pre_send_cursor'], 1024),
             'invalid-dispatch-assignment-identity')
    _path(claim['cwd'], True)  # Syntax only; no filesystem/registry authority.
    handoff = _path(claim['handoff'], False)
    _require(handoff.parts[:2] == ('.codex-lab', 'cards') and len(handoff.parts) >= 4,
             'handoff-outside-exact-card-scope')
    previous = claim['preexisting_turn_ids']
    _require(type(previous) is list and len(previous) <= MAX_TURNS, 'invalid-preexisting-native-turns')
    seen = set()
    for turn in previous:
        _uuid(turn)
        _require(turn not in seen, 'duplicate-preexisting-native-turn')
        seen.add(turn)
    if claim['kind'] == 'initial':
        _require(claim['initial_claim_id'] == claim['dispatch_claim_id'], 'wrong-initial-dispatch-claim')
    else:
        _uuid(claim['predecessor_turn'])
        _require(claim['initial_claim_id'] != claim['dispatch_claim_id']
                 and claim['predecessor_turn'] in seen, 'unbound-resume-predecessor-or-claim')
    _require(type(prompt) is bytes and 0 < len(prompt) <= MAX_PROMPT
             and hashlib.sha256(prompt).hexdigest() == claim['prompt_sha256'], 'outbound-prompt-digest-mismatch')
    ack = {key: claim[key] for key in ('initial_claim_id', 'thread_id', 'generation', 'role',
                                      'issue', 'phase', 'head', 'cwd', 'handoff', 'prompt_sha256')}
    ack['claim_id'] = claim['dispatch_claim_id']
    if claim['kind'] == 'resume':
        ack['predecessor_turn'] = claim['predecessor_turn']
    text = PREFIX + json.dumps(ack, sort_keys=True, ensure_ascii=False, separators=(',', ':'), allow_nan=False)
    _require(len(text.encode('utf-8')) <= MAX_TEXT, 'acknowledgement-too-large')
    return text, seen


def _window(claim, pages, proof):
    """Validate caller-supplied native evidence; never authenticate or collect it."""
    _require(type(proof) is dict and set(proof) == {
        'kind', 'native_pages', 'pre_send_snapshot', 'initial_top', 'final_snapshot'
    } and proof['kind'] == 'lab-p1-window-proof/1', 'missing-window-proof')
    _require(type(pages) is list and 1 <= len(pages) <= MAX_PAGES, 'invalid-window-size')
    native = proof['native_pages']
    _require(type(native) is list and len(native) == len(pages), 'projection-page-count')
    snapshot = proof['pre_send_snapshot']
    _require(type(snapshot) is dict and set(snapshot) == {'thread_id', 'turn_id', 'status', 'cursor'},
             'invalid-pre-send-snapshot')
    _uuid(snapshot['turn_id'])
    predecessor = snapshot['turn_id']
    _require(snapshot['thread_id'] == claim['thread_id']
             and snapshot['cursor'] == claim['pre_send_cursor']
             and type(snapshot['status']) is str and snapshot['status'] in TERMINAL
             and predecessor in claim['preexisting_turn_ids'], 'unbound-predecessor')
    if claim['kind'] == 'resume':
        _require(predecessor == claim['predecessor_turn'], 'wrong-resume-predecessor')
    for key in ('initial_top', 'final_snapshot'):
        top = proof[key]
        _require(type(top) is dict and set(top) == {'thread_id', 'turn_id'}
                 and top['thread_id'] == claim['thread_id'], 'invalid-top-snapshot')
        _uuid(top['turn_id'])
    _require(proof['initial_top'] == proof['final_snapshot'], 'moved-top')
    cursor = claim['pre_send_cursor']
    seen_cursors, seen_turns, seen_items = set(), set(), set()
    audit, ordered_turns = [], []
    total_items = 0
    for index, (page, raw) in enumerate(zip(pages, native)):
        fields = {'request_cursor', 'threadId', 'turns', 'nextCursor'}
        _require(type(page) is dict and set(page) in (fields, fields | {'error'})
                 and page.get('error') is None, 'invalid-legacy-page')
        _require(type(raw) is dict and set(raw) == {'query_cursor', 'response'}, 'invalid-native-page')
        response = raw['response']
        _require(type(response) is dict and set(response) == {'thread', 'page', 'turns'}, 'invalid-native-response')
        thread = response['thread']
        _require(type(thread) is dict and set(thread) == {'id'}
                 and thread['id'] == page['threadId'] == claim['thread_id'], 'wrong-native-thread')
        _require(_text(cursor, 1024) and raw['query_cursor'] == page['request_cursor'] == cursor
                 and cursor not in seen_cursors, 'cursor-gap-or-cycle')
        seen_cursors.add(cursor)
        meta = response['page']
        _require(type(meta) is dict and set(meta) == {'order', 'limit', 'hasMore', 'nextCursor'}
                 and meta['order'] == 'newest_first' and _positive(meta['limit'])
                 and meta['limit'] <= MAX_TURNS and type(meta['hasMore']) is bool,
                 'invalid-native-pagination')
        continuation = meta['nextCursor']
        _require(continuation == page['nextCursor'], 'projection-continuation-mismatch')
        if meta['hasMore']:
            _require(_text(continuation, 1024) and continuation not in seen_cursors, 'missing-or-repeated-continuation')
        else:
            _require(continuation is None, 'inconsistent-exhaustion')
        if index < len(pages) - 1:
            _require(meta['hasMore'], 'page-after-exhaustion')
        cursor = continuation
        turns = response['turns']
        _require(type(turns) is list and 0 < len(turns) <= meta['limit'], 'invalid-native-turns')
        _require(type(page['turns']) is list and len(page['turns']) == len(turns), 'projection-turn-count')
        projection, item_index = [], []
        for turn in turns:
            _require(type(turn) is dict and set(turn) == {'id', 'status', 'items'}, 'invalid-native-turn')
            _uuid(turn['id'])
            _require(turn['id'] not in seen_turns and type(turn['status']) is str
                     and turn['status'] in STATUSES, 'duplicate-or-invalid-turn')
            seen_turns.add(turn['id'])
            ordered_turns.append(turn)
            _require(len(ordered_turns) <= MAX_TURNS, 'turn-bound-exceeded')
            items = turn['items']
            _require(type(items) is list and len(items) <= 64, 'invalid-native-items')
            total_items += len(items)
            _require(total_items <= MAX_ITEMS, 'item-bound-exceeded')
            projected_items = []
            for item in items:
                fields = {'id', 'type', 'text'}
                _require(type(item) is dict and set(item) in (fields, fields | {'phase'}), 'invalid-native-item')
                _require(_text(item['id'], 256) and item['id'] not in seen_items
                         and _text(item['type'], 128) and type(item['text']) is str
                         and len(item['text'].encode('utf-8')) <= MAX_TEXT, 'invalid-item-identity-or-text')
                seen_items.add(item['id'])
                if 'phase' in item:
                    _require(item['phase'] in ('commentary', 'final') and item['type'] == 'agentMessage',
                             'invalid-agent-phase')
                projected_items.append(dict(type=item['type'], text=item['text']))
                item_index.append(dict(turn_id=turn['id'], item_id=item['id'],
                                       type=item['type'], phase=item.get('phase')))
            projection.append(dict(id=turn['id'], status=turn['status'], items=projected_items))
        _require(projection == page['turns'], 'raw-normalized-correspondence-mismatch')
        audit.append(dict(query_cursor=raw['query_cursor'], next_cursor=continuation,
                          has_more=meta['hasMore'], order=meta['order'],
                          turn_ids=[turn['id'] for turn in turns], items=item_index))
    _require(ordered_turns[0]['id'] == proof['initial_top']['turn_id'], 'unbound-newest-top')
    _require(ordered_turns[-1]['id'] == predecessor
             and ordered_turns[-1]['status'] == snapshot['status'], 'missing-terminal-boundary')
    # No preexisting turn may appear on the new side of the frozen boundary.
    _require(all(turn['id'] not in claim['preexisting_turn_ids'] for turn in ordered_turns[:-1]),
             'old-turn-before-boundary')
    return ordered_turns[:-1], dict(
        scope='post-send-through-predecessor', predecessor_turn=predecessor,
        full_history_exhausted=not native[-1]['response']['page']['hasMore'],
        native_continuation=cursor, audit_index=audit)


def match_dispatch_ack(claim, prompt_bytes, legacy_pages, *, window_proof=None):
    """Pure correlation only: no send, resume, activation or effect authority.

    Raw pages and snapshots must be authenticated and collected by the caller.
    Opaque cursor contents are never parsed; all input objects remain unchanged.
    """
    try:
        expected, _ = _expected(claim, prompt_bytes)
        new_turns, window = _window(claim, legacy_pages, window_proof)
        matches = []
        for turn in new_turns:
            for item in turn['items']:
                if item['type'] != 'agentMessage':
                    continue
                text = item['text']
                if PREFIX in text:
                    _require(text == expected, 'inexact-or-wrong-claim-ack')
                    matches.append(turn['id'])
        _require(len(matches) == 1, 'missing-or-ambiguous-ack')
        return dict(action='matched', reason='exact-ack-in-bracketed-bounded-native-window',
                    turn_id=matches[0], claim_id=claim['dispatch_claim_id'], window=window)
    except (ValueError, TypeError, KeyError, IndexError, AttributeError,
            UnicodeError, OverflowError, RecursionError):
        return dict(action='hold', reason='missing-moved-ambiguous-or-inexact-native-window')


# Preserve the pinned prior-profile implementation and its exact return reasons.
_prior_match_dispatch_ack = match_dispatch_ack
PROJECTION_VERSION = 'read-thread-observed-projection/1'


def _unique_object(pairs):
    result = {}
    for key, value in pairs:
        _require(key not in result, 'duplicate-raw-json-key')
        result[key] = value
    return result


def _bad_constant(value):
    raise ValueError('nonfinite-raw-json')


def _project_observed(claim, pages, proof):
    import copy
    _require(type(proof) is dict and set(proof) == {
        'kind', 'native_pages', 'pre_send_snapshot', 'initial_top', 'final_snapshot'
    } and proof['kind'] == 'lab-p1-window-proof/1', 'missing-projection-proof')
    records = proof['native_pages']
    _require(type(records) is list and 1 <= len(records) <= MAX_PAGES
             and type(pages) is list and len(pages) == len(records), 'projection-page-count')
    # Separate internal validation domain for the cursorless first read. This
    # marker is never a native cursor and never returned as evidence.
    fresh = 'internal-cursorless-read-top/1'
    adapted_pages, adapted_records, audit = [], [], []
    next_cursor = None
    for index, (record, legacy) in enumerate(zip(records, pages)):
        _require(type(record) is dict and set(record) == {
            'capture_schema', 'request', 'query_cursor', 'response',
            'response_truncated', 'response_utf8', 'response_sha256'
        } and record['capture_schema'] == PROJECTION_VERSION
                 and record['response_truncated'] is False, 'unproved-capture-completeness')
        raw = record['response_utf8']
        _require(type(raw) is str and 0 < len(raw) <= 2 * 1024 * 1024, 'raw-response-bound')
        encoded = raw.encode('utf-8')
        _require(len(encoded) <= 2 * 1024 * 1024 and _sha(record['response_sha256'], 64)
                 and hashlib.sha256(encoded).hexdigest() == record['response_sha256'], 'raw-response-digest')
        response = json.loads(raw, object_pairs_hook=_unique_object, parse_constant=_bad_constant)
        _require(type(response) is dict and response == record['response'] and set(response) == {
            'schemaVersion', 'thread', 'page', 'turns'
        } and type(response['schemaVersion']) is int and response['schemaVersion'] == 1,
                 'raw-response-correspondence-or-version')
        request = record['request']
        request_fields = {'threadId', 'order', 'limit'} | ({'cursor'} if index else set())
        _require(type(request) is dict and set(request) == request_fields
                 and request['threadId'] == claim['thread_id'] and request['order'] == 'newest_first'
                 and _positive(request['limit']) and request['limit'] <= MAX_TURNS,
                 'invalid-native-read-request')
        _require(record['query_cursor'] == next_cursor
                 and (index == 0 or request['cursor'] == next_cursor), 'native-read-cursor-gap')
        _require(type(legacy) is dict and legacy.get('request_cursor') == next_cursor,
                 'legacy-read-cursor-domain')
        thread = response['thread']
        _require(type(thread) is dict and set(thread) == {
            'id', 'hostId', 'cwd', 'kind', 'status', 'title', 'preview', 'createdAt', 'updatedAt'
        } and thread['id'] == claim['thread_id'], 'invalid-native-thread-metadata')
        meta = response['page']
        _require(type(meta) is dict and meta.get('order') == request['order']
                 and type(meta.get('limit')) is int and meta['limit'] == request['limit'],
                 'native-request-response-page-mismatch')
        next_cursor = meta.get('nextCursor')
        _require(next_cursor != fresh and next_cursor != claim['pre_send_cursor'], 'mixed-cursor-domains')
        turns = response['turns']
        _require(type(turns) is list and len(turns) <= MAX_TURNS, 'invalid-native-turn-list')
        projected_turns, normalized_turns, item_audit = [], [], []
        for turn in turns:
            _require(type(turn) is dict and set(turn) == {
                'id', 'status', 'items', 'startedAt', 'completedAt', 'durationMs', 'error'
            } and turn['error'] is None, 'unproved-native-turn')
            _require(type(turn['items']) is list and len(turn['items']) <= 64, 'native-item-bound')
            projected_items, normalized_items = [], []
            for item in turn['items']:
                _require(type(item) is dict and 'id' in item and 'type' in item, 'missing-native-item-identity')
                kind = item['type']
                phase = item.get('phase')
                if kind == 'agentMessage':
                    _require(set(item) == {'id', 'type', 'phase', 'text'}
                             and phase in ('commentary', 'final_answer') and type(item['text']) is str,
                             'unsupported-agent-projection')
                    normalized_phase = 'final' if phase == 'final_answer' else 'commentary'
                    text = item['text']
                    normalized = dict(id=item['id'], type=kind, text=text, phase=normalized_phase)
                else:
                    allowed = {
                        'functionCallOutput': {'id', 'type', 'name', 'namespace'},
                        'commandExecution': {'id', 'type', 'command', 'commandTruncated', 'cwd',
                                             'durationMs', 'exitCode', 'status'},
                        'reasoning': {'id', 'type', 'summary'},
                    }
                    _require(type(kind) is str and kind in allowed and set(item) == allowed[kind],
                             'unsupported-nonagent-projection')
                    _require(kind != 'commandExecution' or item['commandTruncated'] is False,
                             'truncated-command-observation')
                    normalized_phase, text = None, ''
                    normalized = dict(id=item['id'], type=kind, text='')
                projected_items.append(dict(type=kind, text=text))
                normalized_items.append(normalized)
                item_audit.append(dict(turn_id=turn['id'], item_id=item['id'], type=kind,
                                       raw_phase=phase, normalized_phase=normalized_phase,
                                       raw_text_present='text' in item))
            projected_turns.append(dict(id=turn['id'], status=turn['status'], items=projected_items))
            normalized_turns.append(dict(id=turn['id'], status=turn['status'], items=normalized_items))
        legacy_fields = {'request_cursor', 'threadId', 'turns', 'nextCursor'}
        _require(set(legacy) in (legacy_fields, legacy_fields | {'error'}) and legacy.get('error') is None
                 and legacy['threadId'] == thread['id'] and legacy['turns'] == projected_turns
                 and legacy['nextCursor'] == next_cursor, 'raw-legacy-projection-mismatch')
        adapted = copy.deepcopy(legacy)
        adapted['request_cursor'] = fresh if index == 0 else record['query_cursor']
        adapted_pages.append(adapted)
        adapted_records.append(dict(query_cursor=adapted['request_cursor'], response=dict(
            thread=dict(id=thread['id']), page=copy.deepcopy(meta), turns=normalized_turns)))
        audit.append(dict(request=copy.deepcopy(request), query_cursor=record['query_cursor'],
                          raw_response_sha256=record['response_sha256'], next_cursor=next_cursor,
                          has_more=meta.get('hasMore'), order=meta.get('order'),
                          turn_ids=[t['id'] for t in turns], items=item_audit))
    adapted_claim = copy.deepcopy(claim)
    adapted_claim['pre_send_cursor'] = fresh
    adapted_proof = copy.deepcopy(proof)
    _require(type(proof['pre_send_snapshot']) is dict
             and proof['pre_send_snapshot'].get('cursor') == claim['pre_send_cursor'],
             'unbound-wait-event-cursor')
    adapted_proof['pre_send_snapshot']['cursor'] = fresh
    adapted_proof['native_pages'] = adapted_records
    return adapted_claim, adapted_pages, adapted_proof, audit


def match_dispatch_ack(claim, prompt_bytes, legacy_pages, *, window_proof=None):
    """Versioned pure projection; no collector authentication or effect grant."""
    # Legacy inputs are evaluated byte-for-byte by the pinned implementation.
    records = window_proof.get('native_pages') if type(window_proof) is dict else None
    observed = type(records) is list and any(
        type(record) is dict and any(key in record for key in (
            'capture_schema', 'request', 'response_utf8', 'response_sha256', 'response_truncated'
        )) for record in records)
    if not observed:
        return _prior_match_dispatch_ack(claim, prompt_bytes, legacy_pages, window_proof=window_proof)
    try:
        expected, _ = _expected(claim, prompt_bytes)
        adapted_claim, pages, proof, audit = _project_observed(claim, legacy_pages, window_proof)
        turns, window = _window(adapted_claim, pages, proof)
        matches = []
        for turn in turns:
            for item in turn['items']:
                if item['type'] == 'agentMessage' and PREFIX in item['text']:
                    _require(item['text'] == expected, 'inexact-current-ack')
                    matches.append(turn['id'])
        _require(len(matches) == 1, 'missing-or-ambiguous-current-ack')
        window['projection_version'] = PROJECTION_VERSION
        window['wait_event_cursor'] = claim['pre_send_cursor']
        window['audit_index'] = audit
        return dict(action='matched', reason='exact-ack-in-bracketed-bounded-native-window',
                    turn_id=matches[0], claim_id=claim['dispatch_claim_id'], window=window)
    except (ValueError, TypeError, KeyError, IndexError, AttributeError,
            UnicodeError, OverflowError, RecursionError):
        return dict(action='hold', reason='missing-moved-ambiguous-or-inexact-native-projection')
