"""Restricted real stdio child; fixture only, no tasks/login/model/network."""
import argparse,json,os,sys,time,uuid
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--audit',required=True,type=Path);p.add_argument('--control',required=True,type=Path);p.add_argument('--replay',required=True,type=Path);a=p.parse_args()
instance=uuid.uuid4().hex

def audit(event,**fields):
    with a.audit.open('a') as f:f.write(json.dumps(dict(event=event,pid=os.getpid(),instance=instance,**fields))+chr(10));f.flush()
def send(message):
    audit('out',wire=message);print(json.dumps(message),flush=True)
audit('process-start',ppid=os.getppid(),cwd=os.getcwd(),started_ns=time.monotonic_ns())
for line in sys.stdin:
    try:request=json.loads(line)
    except ValueError:raise SystemExit(24)
    audit('in',wire=request)
    method=request.get('method');params=request.get('params',{})
    if method=='initialized' and 'id'not in request:continue
    if method not in ('initialize','config/read','thread/list'):raise SystemExit(25)
    if 'id'not in request or type(request['id'])not in (int,str):raise SystemExit(26)
    if 'auth'in params or 'token'in params or 'task'in params:raise SystemExit(27)
    mode=json.loads(a.control.read_text()).get('mode','normal') if a.control.exists() else 'normal'
    if method=='initialize':result=dict(fixture=True,worker_id=instance,pid=os.getpid())
    elif method=='config/read':result=dict(config=dict(fixture=True,worker_id=instance,pid=os.getpid(),physical_cwd=os.getcwd()))
    else:result=dict(data=[],nextCursor=None,fixture=True,worker_id=instance,pid=os.getpid())
    if method!='initialize':
        if mode=='exit':audit('controlled-exit');raise SystemExit(23)
        if mode=='disconnect':
            audit('controlled-stdout-disconnect');os.close(1)
            for ignored in sys.stdin:pass
            raise SystemExit(0)
        if mode=='replay':send(json.loads(a.replay.read_text()));continue
        if mode=='wrong-id':send(dict(id='unexpected-'+str(request['id']),result=result));continue
        if mode=='unsolicited':send(dict(id='unsolicited-'+str(request['id']),result=result))
    send(dict(id=request['id'],result=result))
audit('stdin-closed')
