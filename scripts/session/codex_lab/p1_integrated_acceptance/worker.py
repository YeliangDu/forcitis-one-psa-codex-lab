"""Test-only labctl CLI containment/profiling; no task/admission implementation."""
import contextlib
import hashlib
import io
import json
import os
import argparse
import subprocess
import sqlite3
from pathlib import Path
import socket
import sys
import types


def net_check(parent):
    if os.geteuid()==0 or os.stat('/proc/self/ns/net').st_ino==parent:raise RuntimeError('non-root isolated netns required')
    if {n for _,n in socket.if_nameindex()}!={'lo'}:raise RuntimeError('external network interface')
    if any(r.split()[0]!='lo' for r in Path('/proc/net/route').read_text().splitlines()[1:] if r.split()):raise RuntimeError('external route')


def main():
    root,entry,state,closure,parent=Path(sys.argv[1]).resolve(),Path(sys.argv[2]).resolve(),Path(sys.argv[3]).resolve(),Path(sys.argv[4]),int(sys.argv[5])
    net_check(parent);spec=json.loads(closure.read_text());raw=entry.read_bytes();python={Path(sys.prefix).resolve(),Path(sys.base_prefix).resolve()}
    violations=[];calls=[]
    def within(value,base):
        if not isinstance(value,(str,bytes,os.PathLike)):return False
        p=Path(os.fsdecode(value)).resolve();return p==base or base in p.parents
    def audit(event,args):
        deny=event in {'subprocess.Popen','os.system','os.exec','os.fork','os.forkpty','os.posix_spawn','os.chdir','socket.__new__','socket.connect','socket.bind','ctypes.dlopen'}
        if event=='open':
            path,mode,flags=args;writing=any(x in (mode or '') for x in 'wax+') or bool(flags&(os.O_WRONLY|os.O_RDWR|os.O_CREAT|os.O_TRUNC|os.O_APPEND))
            deny|=not (within(path,state) if writing else within(path,root) or any(within(path,p) for p in python))
        if event in {'os.mkdir','os.remove','os.rmdir','os.chmod','os.chown','os.utime','os.truncate'}:deny|=not within(args[0],state)
        if event in {'os.rename','os.link','os.symlink'}:deny|=not (within(args[0],state) and within(args[1],state))
        if event=='sqlite3.connect':deny|=not within(args[0],state)
        if event=='import' and args[0].split('.')[0] in {'ctypes','_ctypes','cffi','socket','_socket','multiprocessing'}:deny=True
        if deny:violations.append(event);raise PermissionError('integrated fixture denied '+event)
    code_root=root/'code'
    def profile(frame,event,arg):
        if event=='call':
            file=Path(frame.f_code.co_filename)
            if file.is_absolute() and code_root in file.parents:
                relative=str(file.relative_to(code_root))
                for slot,path in spec['slots'].items():
                    if relative==path and frame.f_code.co_name in ('recover','match_dispatch_ack','artifacts'):
                        calls.append(dict(slot=slot,path=relative,function=frame.f_code.co_name))
    sys.dont_write_bytecode=True;sys.path.insert(0,str(entry.parent))
    sys.argv=[str(entry),*sys.argv[6:]];module=types.ModuleType('__main__');module.__file__=str(entry);module.__package__=None
    sys.modules['__main__']=module;sys.addaudithook(audit);sys.setprofile(profile)
    output=io.StringIO();status=0
    with contextlib.redirect_stdout(output):
        try:exec(compile(raw,str(entry),'exec'),module.__dict__)
        except SystemExit as result:status=result.code or 0
    sys.setprofile(None)
    if violations:raise RuntimeError('forbidden side effect attempted '+violations[0])
    print(json.dumps(dict(exit=status,stdout=output.getvalue(),leaf_calls=calls)))


if __name__=='__main__':
    try:main()
    except (RuntimeError,PermissionError) as error:
        print('CAPABILITY_HOLD:'+str(error),file=sys.stderr);raise SystemExit(86)
