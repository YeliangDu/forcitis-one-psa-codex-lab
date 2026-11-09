"""Owned CLI first-receive entry cut; never reads or fabricates a reply.

Actual Broker is a separate untraced process. Candidate/client code unchanged.
Only disposable synthetic owner/DB/credentials supplied by independent factory.
"""
import argparse
import ctypes
import json
import os
from pathlib import Path
import signal
import sqlite3
import sys
import time

libc=ctypes.CDLL(None,use_errno=True);libc.ptrace.restype=ctypes.c_long
class Hold(RuntimeError):pass
class Regs(ctypes.Structure):
    _fields_=[(n,ctypes.c_ulonglong) for n in ('r15','r14','r13','r12','rbp','rbx','r11','r10','r9','r8','rax','rcx','rdx','rsi','rdi','orig_rax','rip','cs','eflags','rsp','ss','fs_base','gs_base','ds','es','fs','gs')]
def pt(op,pid,a=0,d=0):
    ctypes.set_errno(0);r=libc.ptrace(ctypes.c_ulong(op),ctypes.c_ulong(pid),ctypes.c_void_p(a) if type(a)is int else a,ctypes.c_void_p(d) if type(d)is int else d)
    if r==-1 and ctypes.get_errno():raise Hold('owned child ptrace unavailable')
    return r
def word(pid,a):return pt(2,pid,a,0)&((1<<64)-1)
def string(pid,a):
    raw=bytearray()
    for i in range(0,4096,8):
        part=word(pid,a+i).to_bytes(8,'little')
        if 0 in part:return bytes(raw+part[:part.index(0)]).decode()
        raw.extend(part)
    raise Hold('socket path exceeds bound')
def main():
    p=argparse.ArgumentParser(allow_abbrev=False);p.add_argument('--database',required=True,type=Path);p.add_argument('--socket',required=True);p.add_argument('--effect-id',required=True);p.add_argument('--client',required=True);p.add_argument('--generation',required=True,type=int);p.add_argument('--token-sha256',required=True);p.add_argument('--report',required=True,type=Path);p.add_argument('argv',nargs=argparse.REMAINDER);a=p.parse_args()
    if os.uname().machine!='x86_64' or ctypes.sizeof(ctypes.c_void_p)!=8:raise Hold('native x86_64 required')
    argv=a.argv[1:] if a.argv and a.argv[0]=='--' else a.argv
    child=os.fork()
    if child==0:
        try:pt(0,0);os.execve(argv[0],argv,dict(os.environ))
        except BaseException:os._exit(86)
    report=dict(classification='capability-HOLD',cut_reached=False,actual_client_pid=child,request_bytes_sent=0,
                response_receive_syscalls_completed=0,response_bytes_received_by_client=0,reply_read_by_supervisor=False)
    fd=None;pending=None;pending_count=0
    try:
        _,status=os.waitpid(child,0)
        if not os.WIFSTOPPED(status):raise Hold('real client trace entry unavailable')
        pt(0x4200,child,0,1|0x100000)
        while True:
            pt(24,child)
            _,status=os.waitpid(child,0)
            if os.WIFEXITED(status) or os.WIFSIGNALED(status):raise Hold('client terminated before real receive cut')
            stop=os.WSTOPSIG(status)
            if stop!=(signal.SIGTRAP|0x80):
                if stop in (signal.SIGTRAP,signal.SIGSTOP):continue
                raise Hold('unexpected signal before receive cut')
            info=ctypes.create_string_buffer(128);pt(0x420e,child,128,ctypes.byref(info))
            if int.from_bytes(info.raw[4:8],'little')!=0xc000003e:raise Hold('syscall ABI differs')
            r=Regs();pt(12,child,0,ctypes.byref(r));op=info.raw[0];number=int(r.orig_rax)
            if op==1:
                pending=None
                if number==42 and (word(child,r.rsi)&65535)==1 and string(child,r.rsi+2)==str(a.socket):
                    pending=('connect',int(r.rdi))
                elif fd is not None and int(r.rdi)==fd and number in (44,46,1):pending=('send',fd)
                elif fd is not None and int(r.rdi)==fd and number in (45,47,0):
                    # First socket receive is stopped at ENTRY: zero payload read.
                    if report['request_bytes_sent']<=0:raise Hold('full request dispatch not observed')
                    descriptor=os.readlink('/proc/'+str(child)+'/fd/'+str(fd))
                    if not descriptor.startswith('socket:[') or descriptor!=report.get('client_socket_fd_identity'):
                        raise Hold('original connected socket descriptor changed')
                    deadline=time.monotonic()+8;observed=False
                    while time.monotonic()<deadline:
                        with sqlite3.connect(a.database.as_uri()+'?mode=ro',uri=True) as db:
                            db.execute('BEGIN')
                            owner=db.execute('select client,generation,paused,token_sha from owner where id=1').fetchone()
                            effect=db.execute('select phase,issue_count from effects where effect_id=?',(a.effect_id,)).fetchone()
                        if owner==(a.client,a.generation,1,a.token_sha256) and effect==('claimed',1):observed=True;break
                        time.sleep(.02)
                    if not observed:raise Hold('real claim commit not visible before receive')
                    report.update(cut_reached=True,classification='actual-commit-before-client-receive-cut',
                        cut_syscall=number,cut_phase='syscall-entry-before-kernel-receive',claim_committed=True,
                        owner_generation=a.generation,owner_paused=True,original_effect_claimed_once=True)
                    os.kill(child,signal.SIGKILL);_,terminal=os.waitpid(child,0)
                    if not os.WIFSIGNALED(terminal) or os.WTERMSIG(terminal)!=signal.SIGKILL:raise Hold('owned client termination unavailable')
                    report['actual_client_terminal_signal']=signal.SIGKILL
                    a.report.write_text(json.dumps(report,indent=2)+chr(10));return 0
            elif op==2 and pending is not None:
                result=ctypes.c_longlong(r.rax).value
                if pending[0]=='connect' and result==0:
                    fd=pending[1];report['exact_broker_socket_connected']=True
                    report['client_socket_fd_identity']=os.readlink('/proc/'+str(child)+'/fd/'+str(fd))
                elif pending[0]=='send' and result>0:report['request_bytes_sent']+=result
                pending=None
    except BaseException:
        try:os.kill(child,signal.SIGKILL)
        except ProcessLookupError:pass
        try:os.waitpid(child,0)
        except ChildProcessError:pass
        a.report.write_text(json.dumps(report,indent=2)+chr(10));return 86
if __name__=='__main__':raise SystemExit(main())
