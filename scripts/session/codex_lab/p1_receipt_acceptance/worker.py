"""Test containment and invocation only; no receipt validation/repair/admission."""
import copy
import base64
import io
import stat
import subprocess
import zipfile
import hashlib
import inspect
import json
import os
from pathlib import Path
import re
import socket
import sys
import types
import uuid


def net_check(parent):
    if os.geteuid()==0 or os.stat('/proc/self/ns/net').st_ino==parent:
        raise RuntimeError('non-root independent network namespace required')
    if {name for _,name in socket.if_nameindex()}!={'lo'}:
        raise RuntimeError('external network interface')
    if any(row.split()[0]!='lo' for row in Path('/proc/net/route').read_text().splitlines()[1:] if row.split()):
        raise RuntimeError('external network route')


def main():
    root,target,pin,parent,mode=Path(sys.argv[1]).resolve(),Path(sys.argv[2]).resolve(),sys.argv[3],int(sys.argv[4]),sys.argv[5]
    net_check(parent)
    raw=target.read_bytes();data=json.load(sys.stdin);before=copy.deepcopy(data)
    if hashlib.sha256(raw).hexdigest()!=pin or root not in target.parents:raise RuntimeError('source pin/path mismatch')
    python={Path(sys.prefix).resolve(),Path(sys.base_prefix).resolve()};violations=[]
    def allowed(path):
        if not isinstance(path,(str,bytes,os.PathLike)):return False
        p=Path(os.fsdecode(path)).resolve()
        return p==root or root in p.parents or any(p==x or x in p.parents for x in python)
    def audit(event,args):
        deny=event in {'subprocess.Popen','os.system','os.exec','os.posix_spawn','os.fork','os.forkpty','os.chdir',
            'socket.__new__','socket.connect','socket.bind','sqlite3.connect','ctypes.dlopen','os.remove','os.rename',
            'os.mkdir','os.rmdir','os.link','os.symlink','os.chmod','os.chown','os.truncate','os.utime'}
        if event=='import' and args[0].split('.')[0] in {'ctypes','_ctypes','cffi','socket','_socket','multiprocessing'}:deny=True
        if event=='open':
            path,access,flags=args
            deny|=not allowed(path) or any(c in (access or '') for c in 'wax+') or bool(flags&(os.O_WRONLY|os.O_RDWR|os.O_CREAT|os.O_TRUNC|os.O_APPEND))
        if event in ('os.listdir','os.scandir') and args:deny|=not allowed(args[0])
        if deny:violations.append(event);raise PermissionError('receipt fixture denied '+event)
    sys.dont_write_bytecode=True;sys.addaudithook(audit)
    module=types.ModuleType('isolated_actual_reader')
    module.__file__=str(target);module.__package__=None;module.__cached__=None
    sys.modules[module.__name__]=module
    exec(compile(raw,str(target),'exec'),module.__dict__)
    constructor=module.__dict__.get('GithubRead')
    if not callable(constructor):raise RuntimeError('actual GithubRead entry unavailable')
    calls=[]
    def request(path,pages=False,binary=False):
        calls.append(dict(path=path,pages=pages,binary=binary))
        expected=data['expected'];meta=data['metadata']
        if path=='' and not pages and not binary:
            return copy.deepcopy(data['run']['repository'])
        if path==f"actions/runs?event=pull_request&head_sha={expected['head']}&per_page=100" and pages and not binary:
            return [dict(total_count=1,workflow_runs=[copy.deepcopy(data['run'])])]
        if path==f"actions/runs/{expected['run_id']}/artifacts?per_page=100" and pages and not binary:
            rows=[] if data['missing'] else [copy.deepcopy(meta)]
            return [dict(total_count=len(rows),artifacts=rows)]
        if path==f"actions/artifacts/{meta['id']}/zip" and binary and not pages:
            return base64.b64decode(data['zip_base64'],validate=True)
        if path in (f"actions/runs/{expected['run_id']}",f"actions/runs/{expected['run_id']}/attempts/{expected['attempt']}") and not pages and not binary:
            return copy.deepcopy(data['run'])
        raise RuntimeError('CAPABILITY_HOLD: unprovided synthetic GET route '+path)
    kwargs={} if mode=='baseline' else dict(expected=copy.deepcopy(data['expected']))
    try:inspect.signature(constructor).bind(request,**kwargs)
    except TypeError as error:raise RuntimeError('incompatible candidate expected-tuple constructor; HOLD, not red') from error
    try:
        reader=constructor(request,**kwargs)
        result=reader.artifacts(data['expected']['run_id'],data['expected']['attempt'])
        refusal=None
    except ValueError as error:
        result=[];refusal=str(error)
    if violations:raise RuntimeError('candidate attempted forbidden effect '+violations[0])
    print(json.dumps(dict(rows=result,refusal=refusal,get_calls=calls,inputs_unchanged=data==before),sort_keys=True))


if __name__=='__main__':
    try:main()
    except (RuntimeError,PermissionError) as error:
        print('CAPABILITY_HOLD:'+str(error),file=sys.stderr);raise SystemExit(86)
