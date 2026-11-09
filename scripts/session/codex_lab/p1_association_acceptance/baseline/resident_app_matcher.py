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


def match_dispatch_ack(claim, outbound_prompt_bytes, pages):
    """Return a correlated native envelope ID or HOLD, never perform an action.

    Strict normalized envelope v1: page keys request_cursor/threadId/turns/
    nextCursor (+ optional null error); turn keys id/status/items; item keys
    type/text. Unrecognized metadata requires outer normalization, not guessing.
    Input objects must remain stable during the call; none are mutated.
    """
    try:
        expected, old = _expected(claim, outbound_prompt_bytes)
        _require(type(pages) is list and 1 <= len(pages) <= MAX_PAGES, 'incomplete-or-overlong-native-window')
        cursor = claim['pre_send_cursor']
        seen_cursors, seen_turns, matches = set(), set(), []
        turns_total = items_total = ack_items = 0
        predecessor_terminal = False
        for index, page in enumerate(pages):
            fields = {'request_cursor', 'threadId', 'turns', 'nextCursor'}
            _require(type(page) is dict and set(page) in (fields, fields | {'error'})
                     and page.get('error') is None, 'malformed-or-expired-native-page')
            request = page['request_cursor']
            _require(_text(request, 1024) and request == cursor and request not in seen_cursors
                     and page['threadId'] == claim['thread_id'], 'native-page-thread-or-cursor-mismatch')
            seen_cursors.add(request)
            next_cursor = page['nextCursor']
            if index == len(pages) - 1:
                _require(next_cursor is None, 'truncated-native-page-chain')
            else:
                _require(_text(next_cursor, 1024) and next_cursor not in seen_cursors, 'skipped-or-repeated-native-page')
            cursor = next_cursor
            turns = page['turns']
            _require(type(turns) is list and len(turns) <= MAX_TURNS, 'invalid-native-turn-list')
            turns_total += len(turns)
            _require(turns_total <= MAX_TURNS, 'native-turn-window-too-large')
            for turn in turns:
                _require(type(turn) is dict and set(turn) == {'id', 'status', 'items'}, 'malformed-native-turn')
                _uuid(turn['id'])
                _require(turn['id'] not in seen_turns and type(turn['status']) is str and turn['status'] in STATUSES,
                         'repeated-or-unknown-native-turn')
                seen_turns.add(turn['id'])
                if claim['kind'] == 'resume' and turn['id'] == claim['predecessor_turn']:
                    predecessor_terminal = turn['status'] in TERMINAL
                items = turn['items']
                _require(type(items) is list and len(items) <= 64, 'invalid-native-item-list')
                items_total += len(items)
                _require(items_total <= MAX_ITEMS, 'native-item-window-too-large')
                for item in items:
                    _require(type(item) is dict and set(item) == {'type', 'text'} and _text(item['type'], 128)
                             and type(item['text']) is str and len(item['text']) <= MAX_TEXT
                             and len(item['text'].encode('utf-8')) <= MAX_TEXT, 'malformed-or-oversize-native-item')
                    if turn['id'] in old or item['type'] != 'agentMessage':
                        continue
                    # Whole item equality only; no substring, code fence, quote,
                    # tool text or resident-supplied turn UUID can be authority.
                    if item['text'].startswith(PREFIX):
                        ack_items += 1
                        _require(item['text'] == expected, 'native-acknowledgement-binding-mismatch')
                        matches.append(turn['id'])
        _require(ack_items == 1 and len(matches) == 1, 'missing-or-ambiguous-native-acknowledgement')
        _require(claim['kind'] != 'resume' or predecessor_terminal, 'resume-predecessor-not-observed-terminal')
        return dict(action='matched', reason='exact-claim-prompt-and-complete-native-acknowledgement',
                    turn_id=matches[0], claim_id=claim['dispatch_claim_id'])
    except (ValueError, TypeError, KeyError, UnicodeError, OverflowError, RecursionError):
        # No tentative handle is returned, even after an earlier matching page.
        return dict(action='hold', reason='missing-ambiguous-or-inexact-native-dispatch-readback')
