"""Synthetic literal window inputs, not a native collector or normalizer."""
import hashlib
import json

THREAD='00000000-0000-4000-8000-000000006203'
INITIAL='00000000-0000-4000-8000-000000000201'
RESUME='00000000-0000-4000-8000-000000000202'
OLD='00000000-0000-4000-8000-000000000101'
NEW='00000000-0000-4000-8000-000000000102'
OTHER='00000000-0000-4000-8000-000000000103'
C0='{"requestedThreadId":"00000000-0000-4000-8000-000000006203","opaque":"synthetic-newest","scope":{"kind":"turns"}}'
C1='{"requestedThreadId":"00000000-0000-4000-8000-000000006203","opaque":"synthetic-page-two","scope":{"kind":"turns"}}'
C2='{"requestedThreadId":"00000000-0000-4000-8000-000000006203","opaque":"synthetic-older-than-boundary","scope":{"kind":"turns"}}'


def canonical(value):return json.dumps(value,sort_keys=True,separators=(',',':'))


def case(resume=False,exhausted=False):
    prompt=b'Synthetic bounded association assignment; never send.'
    claim=dict(kind='resume' if resume else 'initial',thread_id=THREAD,initial_claim_id=INITIAL,
        dispatch_claim_id=RESUME if resume else INITIAL,generation=5,role='one-test',issue=62003,
        phase='synthetic-association',head='b'*40,cwd='/synthetic/personal-lab/work',
        handoff='.codex-lab/cards/synthetic-association/result.json',prompt_sha256=hashlib.sha256(prompt).hexdigest(),
        pre_send_cursor=C0,preexisting_turn_ids=[OLD])
    if resume:claim['predecessor_turn']=OLD
    ack={k:claim[k] for k in ('initial_claim_id','thread_id','generation','role','issue','phase','head','cwd','handoff','prompt_sha256')}
    ack['claim_id']=claim['dispatch_claim_id']
    if resume:ack['predecessor_turn']=OLD
    text='LAB_DISPATCH_ACK '+canonical(ack)
    legacy=[dict(request_cursor=C0,threadId=THREAD,turns=[dict(id=NEW,status='inProgress',items=[dict(type='agentMessage',text=text)])],nextCursor=C1),
            dict(request_cursor=C1,threadId=THREAD,turns=[dict(id=OLD,status='completed',items=[])],nextCursor=None if exhausted else C2)]
    native=[dict(query_cursor=C0,response=dict(thread=dict(id=THREAD),page=dict(order='newest_first',limit=1,hasMore=True,nextCursor=C1),
                 turns=[dict(id=NEW,status='inProgress',items=[dict(id='fixture-ack-item',type='agentMessage',phase='commentary',text=text)])])),
            dict(query_cursor=C1,response=dict(thread=dict(id=THREAD),page=dict(order='newest_first',limit=1,hasMore=not exhausted,nextCursor=None if exhausted else C2),
                 turns=[dict(id=OLD,status='completed',items=[])]))]
    proof=dict(kind='lab-p1-window-proof/1',native_pages=native,
        pre_send_snapshot=dict(thread_id=THREAD,turn_id=OLD,status='completed',cursor=C0),
        initial_top=dict(thread_id=THREAD,turn_id=NEW),final_snapshot=dict(thread_id=THREAD,turn_id=NEW))
    return dict(claim=claim,prompt_hex=prompt.hex(),legacy_pages=legacy,window_proof=proof)


def audit_index(value):
    # Expected output only. Never used to prepare/repair target input.
    return [dict(query_cursor=p['query_cursor'],next_cursor=p['response']['page']['nextCursor'],
                 has_more=p['response']['page']['hasMore'],order=p['response']['page']['order'],
                 turn_ids=[t['id'] for t in p['response']['turns']],
                 items=[dict(turn_id=t['id'],item_id=i['id'],type=i['type'],phase=i.get('phase'))
                        for t in p['response']['turns'] for i in t['items']])
            for p in value['window_proof']['native_pages']]
