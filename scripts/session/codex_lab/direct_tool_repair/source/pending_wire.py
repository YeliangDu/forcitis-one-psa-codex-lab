"""Read only original Broker-native wire receipts; no history-item fabrication."""
import hashlib
import json
from runtime_store import need

MAX_RECEIPTS = 4096


def original_pending_wire(db, wait, payload, connection_id):
    need(wait['connection_id'] == connection_id and wait['completions'] == 0
         and wait['phase'] in ('pending', 'ready'), 'original-unconsumed-connection-required')
    original = json.loads(payload['raw_request'])
    need(original.get('method') == 'item/tool/call' and 'id' in original
         and json.loads(payload['rpc_id']) == original['id'], 'persisted-original-native-request-required')
    params = original['params']
    need(params.get('threadId') == wait['session_id'] and params.get('turnId') == wait['turn_id']
         and params.get('callId') == wait['call_id'] and params.get('namespace') is None
         and params.get('tool') == 'lab_read_assignment' and params.get('arguments') == {},
         'original-call-tuple-mismatch')
    # This event can only be written by the actual native-call handler, not a
    # public notification/oracle endpoint. External verifier still authenticates
    # the selected Broker/source/host; these local checks don't self-sign it.
    bindings = db.execute("select seq,body from events where kind='actual-native-checkpoint-read-waiting-no-reply' "
                          'order by seq desc limit ?', (MAX_RECEIPTS + 1,)).fetchall()
    need(len(bindings) <= MAX_RECEIPTS, 'native-wait-receipt-window-unproved')
    bound = [(row, json.loads(row['body'])) for row in bindings]
    bound = [(row, body) for row, body in bound if body.get('wait_id') == wait['wait_id']]
    need(len(bound) == 1, 'unique-original-wait-registration-receipt-required')
    binding_row, body = bound[0]
    need(all(body.get(k) == wait[k] for k in ('effect_id', 'revision', 'session_id', 'turn_id', 'call_id', 'connection_id')),
         'original-wait-registration-binding-mismatch')
    receipts = db.execute("select seq,body from events where kind='actual-native-wire-receipt' and seq<? "
                          'order by seq desc limit ?', (binding_row['seq'], MAX_RECEIPTS + 1)).fetchall()
    need(len(receipts) <= MAX_RECEIPTS, 'original-wire-window-too-large-unproved')
    started, calls = [], []
    for row in receipts:
        record = json.loads(row['body'])
        if record.get('connection_id') != connection_id or 'raw_utf8' not in record:
            continue
        raw = record['raw_utf8']
        need(type(raw) is str and len(raw.encode()) <= 2 * 1024 * 1024
             and hashlib.sha256(raw.encode('utf-8')).hexdigest() == record.get('raw_sha256'),
             'original-native-wire-digest-mismatch')
        message = json.loads(raw)
        if message == original:
            calls.append((row['seq'], record['raw_sha256']))
        if message.get('method') == 'item/started':
            event = message.get('params', {})
            item = event.get('item', {})
            if (event.get('threadId') == wait['session_id'] and event.get('turnId') == wait['turn_id']
                    and item.get('id') == wait['call_id'] and item.get('type') == 'dynamicToolCall'):
                need(item.get('tool') == 'lab_read_assignment' and item.get('namespace') is None
                     and ('arguments' not in item or item['arguments'] == {}), 'started-tool-tuple-mismatch')
                started.append((row['seq'], record['raw_sha256']))
    need(len(started) == len(calls) == 1
         and started[0][0] < calls[0][0] < binding_row['seq'], 'original-start-call-wait-order-unproved')
    return dict(started_seq=started[0][0], started_raw_sha256=started[0][1],
                call_seq=calls[0][0], call_raw_sha256=calls[0][1],
                wait_registered_seq=binding_row['seq'], original_connection_id=connection_id,
                historical_item_presence_required=False, independent_authentication_claim=False)
