"""Test containment and invocation only; no window validation/repair/normalizer."""
import copy
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
        if event=='import' and args[0].split('.')[0] in {'ctypes','_ctypes','cffi','socket','_socket','subprocess','_posixsubprocess','multiprocessing'}:deny=True
        if event=='open':
            path,access,flags=args
            deny|=not allowed(path) or any(c in (access or '') for c in 'wax+') or bool(flags&(os.O_WRONLY|os.O_RDWR|os.O_CREAT|os.O_TRUNC|os.O_APPEND))
        if event in ('os.listdir','os.scandir') and args:deny|=not allowed(args[0])
        if deny:violations.append(event);raise PermissionError('association fixture denied '+event)
    sys.dont_write_bytecode=True;sys.addaudithook(audit)
    module=types.ModuleType('isolated_actual_matcher')
    module.__file__=str(target);module.__package__=None;module.__cached__=None
    sys.modules[module.__name__]=module
    exec(compile(raw,str(target),'exec'),module.__dict__)
    entry=module.__dict__.get('match_dispatch_ack')
    if not callable(entry):raise RuntimeError('actual matcher entry unavailable')
    argv=(data['claim'],bytes.fromhex(data['prompt_hex']),data['legacy_pages'])
    kwargs=dict(window_proof=data['window_proof'])
    try:inspect.signature(entry).bind(*argv,**kwargs)
    except TypeError as error:raise RuntimeError('incompatible actual window_proof API; HOLD, not assertion red') from error
    result=entry(*argv,**kwargs)
    if violations:raise RuntimeError('candidate attempted forbidden effect '+violations[0])
    print(json.dumps(dict(result=result,inputs_unchanged=data==before),sort_keys=True))


if __name__=='__main__':
    try:main()
    except (RuntimeError,PermissionError) as error:
        print('CAPABILITY_HOLD:'+str(error),file=sys.stderr);raise SystemExit(86)
