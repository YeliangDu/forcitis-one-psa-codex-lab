"""Restricted actual native-shaped stdio fixture: one read-only dynamic tool.

Only native thread/turn projection persists here; no owner/task authorization or
parallel Broker state machine. No model, credentials, login or network.
"""
import argparse,json,os,sys,uuid
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--audit',type=Path,required=True);p.add_argument('--control',type=Path,required=True);p.add_argument('--replay',type=Path,required=True);a=p.parse_args()
instance=uuid.uuid4().hex;statepath=a.control.parent/'fixture-native-state.json'
state=json.loads(statepath.read_text()) if statepath.exists() else {'thread':None};pending=None

def save():
    temp=statepath.with_suffix('.pending');temp.write_text(json.dumps(state));os.replace(temp,statepath)
def audit(event,**v):
    with a.audit.open('a') as f:f.write(json.dumps(dict(event=event,pid=os.getpid(),instance=instance,**v))+chr(10));f.flush()
def send(v):audit('out',wire=v);print(json.dumps(v),flush=True)
def reply(req,result):send(dict(id=req['id'],result=result))
audit('process-start',ppid=os.getppid(),cwd=os.getcwd())
for line in sys.stdin:
    req=json.loads(line);audit('in',wire=req)
    if 'method'not in req:
        if not pending or req.get('id')!=pending['rpc_id']:raise SystemExit(31)
        result=req.get('result',{});item=dict(type='dynamicToolCall',id=pending['call_id'],tool='lab_read_assignment',namespace=None,arguments={},status='completed',success=result.get('success'),contentItems=result.get('contentItems'))
        turn=pending['turn'];turn.update(status='completed',items=[item]);state['thread']['turns'].append(turn);save()
        send(dict(method='item/completed',params=dict(threadId=state['thread']['id'],turnId=turn['id'],item=item)))
        send(dict(method='turn/completed',params=dict(threadId=state['thread']['id'],turn=turn)));pending=None;continue
    method=req['method'];params=req.get('params',{})
    if method=='initialized' and 'id'not in req:continue
    if method=='initialize':reply(req,dict(fixture=True,pid=os.getpid(),worker_id=instance));continue
    if method=='config/read':reply(req,dict(config=dict(fixture=True,pid=os.getpid(),worker_id=instance)));continue
    if method=='thread/list':reply(req,dict(data=[] if state['thread']is None else [state['thread']],nextCursor=None));continue
    if method=='thread/start':
        if state['thread']is not None:raise SystemExit(32)
        tools=params.get('dynamicTools',[])
        if len(tools)!=1 or tools[0].get('name')!='lab_read_assignment':raise SystemExit(33)
        state['thread']=dict(id=str(uuid.uuid4()),cwd=params.get('cwd'),turns=[],status=dict(type='idle'));save();reply(req,dict(thread=state['thread'],cwd=state['thread']['cwd']));continue
    if method in ('thread/resume','thread/read'):
        if not state['thread'] or params.get('threadId')!=state['thread']['id']:raise SystemExit(34)
        reply(req,dict(thread=state['thread'],cwd=state['thread']['cwd']));continue
    if method=='turn/start':
        if pending or not state['thread'] or params.get('threadId')!=state['thread']['id']:raise SystemExit(35)
        turn=dict(id=str(uuid.uuid4()),status='inProgress',items=[]);call='call-'+uuid.uuid4().hex;rpc='native-tool-'+uuid.uuid4().hex
        pending=dict(turn=turn,call_id=call,rpc_id=rpc);reply(req,dict(turn=turn))
        send(dict(method='item/started',params=dict(threadId=state['thread']['id'],turnId=turn['id'],item=dict(type='dynamicToolCall',id=call,tool='lab_read_assignment',namespace=None,arguments={},status='inProgress'))))
        send(dict(id=rpc,method='item/tool/call',params=dict(threadId=state['thread']['id'],turnId=turn['id'],callId=call,namespace=None,tool='lab_read_assignment',arguments={})));continue
    raise SystemExit(36)
audit('stdin-closed')
