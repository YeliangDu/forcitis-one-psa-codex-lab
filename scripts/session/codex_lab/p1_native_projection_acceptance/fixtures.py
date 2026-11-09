"""Synthetic observed-shape records only; no real/private capture or normalizer."""
import copy
import hashlib
import json

THREAD='00000000-0000-4000-8000-000000006207'
CLAIM='00000000-0000-4000-8000-000000000207'
OLD='00000000-0000-4000-8000-000000000107'
NEW='00000000-0000-4000-8000-000000000108'
OTHER='00000000-0000-4000-8000-000000000109'
WAIT='fixture-wait-event-domain:before-send'
C1='{"requestedThreadId":"00000000-0000-4000-8000-000000006207","opaque":"synthetic-next-page","scope":{"kind":"turns"}}'
C2='{"requestedThreadId":"00000000-0000-4000-8000-000000006207","opaque":"synthetic-older-history","scope":{"kind":"turns"}}'
VERSION='read-thread-observed-projection/1'


def canonical(value):return json.dumps(value,sort_keys=True,separators=(',',':'))


def seal(value):
    # Fixture serialization after deliberate mutation, never candidate validation.
    for page in value['window_proof']['native_pages']:
        page['response_utf8']=json.dumps(page['response'],ensure_ascii=False,indent=2)+'\n'
        page['response_sha256']=hashlib.sha256(page['response_utf8'].encode()).hexdigest()


def case(final_ack=False):
    prompt=b'Synthetic native-shape projection assignment. No send.'
    claim=dict(kind='initial',thread_id=THREAD,initial_claim_id=CLAIM,dispatch_claim_id=CLAIM,generation=9,
        role='one-test',issue=62007,phase='synthetic-native-projection',head='b'*40,cwd='/synthetic/authorized-work',
        handoff='.codex-lab/cards/synthetic-projection/result.json',prompt_sha256=hashlib.sha256(prompt).hexdigest(),
        pre_send_cursor=WAIT,preexisting_turn_ids=[OLD])
    ack={k:claim[k] for k in ('initial_claim_id','thread_id','generation','role','issue','phase','head','cwd','handoff','prompt_sha256')};ack['claim_id']=CLAIM
    text='LAB_DISPATCH_ACK '+canonical(ack)
    items=[dict(type='functionCallOutput',id='fixture-tool',name='fixture_tool',namespace='fixture'),
        dict(type='commandExecution',id='fixture-command',command='synthetic command, not executed',commandTruncated=False,
             cwd='/synthetic/command-cwd',durationMs=1,exitCode=0,status='completed'),
        dict(type='reasoning',id='fixture-reasoning',summary=[]),
        dict(type='agentMessage',id='fixture-commentary',phase='commentary',text='synthetic commentary' if final_ack else text),
        dict(type='agentMessage',id='fixture-final',phase='final_answer',text=text if final_ack else 'synthetic final note')]
    turns=[dict(id=NEW,status='completed',items=items,startedAt=200,completedAt=201,durationMs=1000,error=None),
           dict(id=OLD,status='completed',items=[],startedAt=100,completedAt=101,durationMs=1000,error=None)]
    thread=dict(id=THREAD,hostId='fixture-host',cwd='/synthetic/thread-metadata-cwd',kind='thread',status='idle',
                title='synthetic title',preview='synthetic preview',createdAt=100,updatedAt=201)
    records=[];legacy=[]
    for index,turn in enumerate(turns):
        cursor=None if index==0 else C1;next_cursor=C1 if index==0 else C2
        request=dict(threadId=THREAD,order='newest_first',limit=1)
        if index:request['cursor']=C1
        response=dict(schemaVersion=1,thread=copy.deepcopy(thread),page=dict(order='newest_first',limit=1,hasMore=True,nextCursor=next_cursor),turns=[turn])
        records.append(dict(capture_schema=VERSION,request=request,query_cursor=cursor,response=response,
                            response_truncated=False,response_utf8='',response_sha256=''))
        projected=[dict(type=item['type'],text=item['text'] if item['type']=='agentMessage' else '') for item in turn['items']]
        legacy.append(dict(request_cursor=cursor,threadId=THREAD,nextCursor=next_cursor,
                           turns=[dict(id=turn['id'],status=turn['status'],items=projected)]))
    value=dict(claim=claim,prompt_hex=prompt.hex(),legacy_pages=legacy,window_proof=dict(kind='lab-p1-window-proof/1',
        native_pages=records,pre_send_snapshot=dict(thread_id=THREAD,turn_id=OLD,status='completed',cursor=WAIT),
        initial_top=dict(thread_id=THREAD,turn_id=NEW),final_snapshot=dict(thread_id=THREAD,turn_id=NEW)))
    seal(value);return value


def audit_index(value):
    # Output oracle only, not supplied instead of raw facts or used by worker.
    return [dict(request=copy.deepcopy(p['request']),query_cursor=p['query_cursor'],
        raw_response_sha256=p['response_sha256'],next_cursor=p['response']['page']['nextCursor'],
        has_more=p['response']['page']['hasMore'],order=p['response']['page']['order'],
        turn_ids=[t['id'] for t in p['response']['turns']],
        items=[dict(turn_id=t['id'],item_id=i['id'],type=i['type'],raw_phase=i.get('phase'),
                    normalized_phase=('final' if i.get('phase')=='final_answer' else i.get('phase')),
                    raw_text_present='text' in i)
               for t in p['response']['turns'] for i in t['items']]) for p in value['window_proof']['native_pages']]
