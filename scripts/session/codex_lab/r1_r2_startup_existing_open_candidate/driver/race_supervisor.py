"""Linux x86_64 owned-child read-only ptrace observation and scratch path fault.

No product patch, register modification or authorization substitution. The child
runs its actual public CLI; fault occurs at exact writable database-open entry.
"""
import argparse
import ctypes
import errno
import hashlib
import json
import os
from pathlib import Path
import signal
import sys
import time

class Hold(RuntimeError):pass
libc=ctypes.CDLL(None,use_errno=True)
libc.ptrace.restype=ctypes.c_long
class Registers(ctypes.Structure):
    _fields_=[(name,ctypes.c_ulonglong) for name in ('r15','r14','r13','r12','rbp','rbx','r11','r10','r9','r8','rax','rcx','rdx','rsi','rdi','orig_rax','rip','cs','eflags','rsp','ss','fs_base','gs_base','ds','es','fs','gs')]
def ptrace(request,pid,addr=0,data=0):
    ctypes.set_errno(0)
    result=libc.ptrace(ctypes.c_ulong(request),ctypes.c_ulong(pid),ctypes.c_void_p(addr) if type(addr)is int else addr,ctypes.c_void_p(data) if type(data)is int else data)
    if result==-1 and ctypes.get_errno():raise OSError(ctypes.get_errno(),'owned-child ptrace unavailable')
    return result
def cstring(pid,address):
    data=bytearray()
    for offset in range(0,4096,8):
        value=ptrace(2,pid,address+offset,0)
        part=(value & ((1<<64)-1)).to_bytes(8,'little')
        if 0 in part:return bytes(data+part[:part.index(0)]).decode()
        data.extend(part)
    raise Hold('open pathname exceeds bound')
def sha(raw):return hashlib.sha256(raw).hexdigest()
def main():
    parser=argparse.ArgumentParser(allow_abbrev=False);parser.add_argument('--database',required=True,type=Path);parser.add_argument('--fault',required=True,choices=('missing','replace'));parser.add_argument('--report',required=True,type=Path);parser.add_argument('argv',nargs=argparse.REMAINDER);args=parser.parse_args()
    if os.uname().machine!='x86_64' or ctypes.sizeof(ctypes.c_void_p)!=8:raise Hold('native x86_64 syscall ABI required')
    argv=args.argv[1:] if args.argv and args.argv[0]=='--' else args.argv
    if not argv:raise Hold('actual child argv missing')
    database=args.database.resolve(strict=True);before=database.read_bytes();before_stat=database.stat();backup=database.parent.parent/'pre-race-original.sqlite3'
    pid=os.fork()
    if pid==0:
        try:
            ptrace(0,0,0,0)
            os.execve(argv[0],argv,dict(os.environ))
        except BaseException:os._exit(86)
    report=dict(classification='capability-HOLD',fault=args.fault,actual_child_argv=argv,barrier_reached=False,database_before_sha256=sha(before),read_only_opens_before_fault=0,writable_opens=[],open_exit_results=[])
    def checkpoint():
        temporary=args.report.with_name(args.report.name+'.pending')
        temporary.write_text(json.dumps(report,indent=2)+chr(10));os.replace(temporary,args.report)
    try:
        _,status=os.waitpid(pid,0)
        if not os.WIFSTOPPED(status):raise Hold('owned child not trace-stopped')
        # TRACESYSGOOD | EXITKILL: supervisor failure terminates only its child.
        ptrace(0x4200,pid,0,0x1|0x100000)
        pending_open=None
        while True:
            ptrace(24,pid,0,0)
            _,status=os.waitpid(pid,0)
            if os.WIFEXITED(status):
                code=os.WEXITSTATUS(status);break
            if os.WIFSIGNALED(status):
                code=128+os.WTERMSIG(status);break
            if not os.WIFSTOPPED(status):continue
            stop=os.WSTOPSIG(status)
            if stop!=signal.SIGTRAP|0x80:
                if stop not in (signal.SIGTRAP,signal.SIGSTOP):raise Hold('unexpected signal before bounded syscall result')
                continue
            info=ctypes.create_string_buffer(128)
            ptrace(0x420e,pid,128,ctypes.byref(info))
            if int.from_bytes(info.raw[4:8],'little')!=0xc000003e:raise Hold('unexpected traced syscall architecture')
            operation=info.raw[0]
            regs=Registers();ptrace(12,pid,0,ctypes.byref(regs))
            if operation==1 and regs.orig_rax in (2,257,437):
                address=regs.rdi if regs.orig_rax==2 else regs.rsi
                pathname=cstring(pid,address)
                if pathname!=str(database):continue
                flags=regs.rsi if regs.orig_rax==2 else regs.rdx
                if regs.orig_rax==437:flags=ptrace(2,pid,regs.rdx,0)&((1<<64)-1)
                if flags&3 not in (1,2):
                    report['read_only_opens_before_fault']+=1;continue
                row=dict(syscall=int(regs.orig_rax),flags=int(flags),O_CREAT=bool(flags&os.O_CREAT),O_RDWR=bool(flags&os.O_RDWR),before_kernel_entry=True)
                report['writable_opens'].append(row);pending_open=row
                if not report['barrier_reached']:
                    if report['read_only_opens_before_fault']<2:raise Hold('writable open reached without two preflight read-only opens')
                    # Child is stopped at ENTRY before kernel resolves the path.
                    os.rename(database,backup)
                    if args.fault=='replace':
                        fd=os.open(str(database),os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
                        try:
                            view=memoryview(before)
                            while view:
                                written=os.write(fd,view);view=view[written:]
                            os.fsync(fd)
                        finally:os.close(fd)
                    report['barrier_reached']=True
                    report['original_inode']=before_stat.st_ino
                    report['replacement_inode']=database.stat().st_ino if database.exists() else None
                    report['original_preserved_sha256']=sha(backup.read_bytes())
                    checkpoint()
            elif operation==2 and pending_open is not None:
                result=ctypes.c_longlong(regs.rax).value
                report['open_exit_results'].append(result);pending_open=None;checkpoint()
        report['actual_child_exit']=code
        report['database_exists_after']=database.exists()
        report['database_after_sha256']=sha(database.read_bytes()) if database.exists() else None
        report['original_preserved_sha256']=sha(backup.read_bytes()) if backup.exists() else None
        report['broker_socket_exists']=(database.parent/'broker.sock').exists()
        report['classification']='observed-real-open-entry-fault' if report['barrier_reached'] else 'capability-HOLD'
        args.report.write_text(json.dumps(report,indent=2)+chr(10))
        return code if report['barrier_reached'] else 86
    except BaseException:
        try:os.kill(pid,signal.SIGKILL)
        except ProcessLookupError:pass
        try:os.waitpid(pid,0)
        except ChildProcessError:pass
        args.report.write_text(json.dumps(report,indent=2)+chr(10))
        return 86
if __name__=='__main__':raise SystemExit(main())
