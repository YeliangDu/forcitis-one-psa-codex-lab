"""Owned-child ptrace fault barriers for actual R3 candidate/Git processes.

No product method/subprocess mocking, register writes or sender replacement.
Linux native x86_64 only; unavailable tracing/real barrier is capability HOLD.
"""
import argparse
import ctypes
import fcntl
import hashlib
import json
import os
from pathlib import Path
import signal
import sqlite3
import subprocess

libc=ctypes.CDLL(None,use_errno=True);libc.ptrace.restype=ctypes.c_long
class Hold(RuntimeError):pass
class Regs(ctypes.Structure):
    _fields_=[(n,ctypes.c_ulonglong) for n in ('r15','r14','r13','r12','rbp','rbx','r11','r10','r9','r8','rax','rcx','rdx','rsi','rdi','orig_rax','rip','cs','eflags','rsp','ss','fs_base','gs_base','ds','es','fs','gs')]
def ptrace(op,pid,address=0,data=0):
    ctypes.set_errno(0);result=libc.ptrace(ctypes.c_ulong(op),ctypes.c_ulong(pid),ctypes.c_void_p(address) if type(address)is int else address,ctypes.c_void_p(data) if type(data)is int else data)
    if result==-1 and ctypes.get_errno():raise OSError(ctypes.get_errno(),'owned-child tracing unavailable')
    return result
def peek(pid,address):return ptrace(2,pid,address,0)&((1<<64)-1)
def string(pid,address):
    data=bytearray()
    for offset in range(0,4096,8):
        part=peek(pid,address+offset).to_bytes(8,'little')
        if 0 in part:return bytes(data+part[:part.index(0)]).decode()
        data.extend(part)
    raise Hold('syscall pathname/argument too long')
def vector(pid,address):
    rows=[]
    for n in range(32):
        pointer=peek(pid,address+n*8)
        if pointer==0:return rows
        rows.append(string(pid,pointer))
    raise Hold('argv exceeds bounded capture')
def sha(raw):return hashlib.sha256(raw).hexdigest()
def stat_identity(path):
    s=path.stat();return [s.st_dev,s.st_ino,s.st_uid,s.st_mode,s.st_nlink]
def main():
    p=argparse.ArgumentParser(allow_abbrev=False);p.add_argument('--fault',required=True,choices=('lock-name','push-nonzero'));p.add_argument('--work',required=True,type=Path);p.add_argument('--receiver',required=True,type=Path);p.add_argument('--head',required=True);p.add_argument('--base',required=True);p.add_argument('--report',required=True,type=Path);p.add_argument('argv',nargs=argparse.REMAINDER);args=p.parse_args()
    if os.uname().machine!='x86_64' or ctypes.sizeof(ctypes.c_void_p)!=8:raise Hold('native x86_64 ptrace required')
    argv=args.argv[1:] if args.argv and args.argv[0]=='--' else args.argv
    directory=args.work/'.git/merge-queue';database=directory/'releases.sqlite3'
    named=[directory/'control-upgrade.runner-lock',directory/'merge.queue.merge-lock']
    identities=[stat_identity(x) for x in named];backup=args.work.parent/'fault-original-runner-lock'
    report=dict(classification='capability-HOLD',fault=args.fault,barrier_reached=False,actual_entry_argv=argv,Git_exec_counts={k:0 for k in ('push','merge-tree','commit-tree')},Git_executions=[],lock_original_identities=identities)
    def save():
        temporary=args.report.with_name(args.report.name+'.pending');temporary.write_text(json.dumps(report,indent=2)+chr(10));os.replace(temporary,args.report)
    root=os.fork()
    if root==0:
        try:ptrace(0,0);os.execve(argv[0],argv,dict(os.environ))
        except BaseException:os._exit(86)
    tracked={root};pending={};gitpids={};exits={};rootcode=86;changed=False
    def lease_witness(pid_for_fds):
        with sqlite3.connect(database.as_uri()+'?mode=ro&immutable=1',uri=True) as db:
            row=db.execute('select phase from upgrade_bindings where number=42').fetchone()
        if row!=('reserved',):raise Hold('fault did not reach durable reserve')
        handles={}
        for item in Path('/proc/'+str(pid_for_fds)+'/fd').iterdir():
            try:
                st=item.stat();pair=[st.st_dev,st.st_ino]
                for i,expected in enumerate(identities):
                    if pair==expected[:2]:handles[str(i)]=int(item.name)
            except FileNotFoundError:pass
        if len(handles)!=2:raise Hold('both actual original control FDs not observed')
        probes=[]
        for name in named:
            with name.open('rb') as stream:
                try:fcntl.flock(stream,fcntl.LOCK_EX|fcntl.LOCK_NB)
                except BlockingIOError:probes.append('held-by-other-process')
                else:fcntl.flock(stream,fcntl.LOCK_UN);raise Hold('original control lock not actually held')
        return dict(reservation_phase='reserved',observed_process_pid=pid_for_fds,control_fds=handles,independent_lock_probes=probes)
    try:
        _,status=os.waitpid(root,0)
        if not os.WIFSTOPPED(status):raise Hold('candidate entry did not enter traced exec')
        ptrace(0x4200,root,0,1|2|4|8|16|0x100000)
        ptrace(24,root)
        while tracked:
            pid,status=os.waitpid(-1,0x40000000)
            if os.WIFEXITED(status) or os.WIFSIGNALED(status):
                code=os.WEXITSTATUS(status) if os.WIFEXITED(status) else 128+os.WTERMSIG(status)
                exits[pid]=code;tracked.discard(pid)
                if pid==root:rootcode=code
                if pid in gitpids:
                    gitpids[pid]['exit_code']=code;save()
                continue
            if not os.WIFSTOPPED(status):continue
            stopped=os.WSTOPSIG(status);event=status>>16
            if event in (1,2,3):
                child=ctypes.c_ulonglong();ptrace(0x4201,pid,0,ctypes.byref(child));tracked.add(child.value)
            elif event==4:
                execution=pending.pop(pid,None)
                if execution and execution['path']=='/usr/bin/git':
                    if Path('/proc/'+str(pid)+'/exe').resolve(strict=True)!=Path('/usr/bin/git').resolve(strict=True):raise Hold('actual Git exec identity differs')
                    kind=execution['argv'][1] if len(execution['argv'])>1 else ''
                    row=dict(pid=pid,kind=kind,argv=execution['argv'],exec_succeeded=True)
                    report['Git_executions'].append(row);gitpids[pid]=row
                    if kind in report['Git_exec_counts']:report['Git_exec_counts'][kind]+=1
                    if args.fault=='push-nonzero' and kind=='push' and not changed:
                        report['lease_witness']=lease_witness(pid)
                        actor=['/usr/bin/git','-c','core.hooksPath=/dev/null','--git-dir='+str(args.receiver),'update-ref','refs/heads/main',args.head,args.base]
                        result=subprocess.run(actor,env=dict(os.environ),capture_output=True,timeout=10)
                        if result.returncode:raise Hold('controlled foreign preimage CAS did not apply')
                        report['test_only_fault_actor']=dict(argv=actor,exit_code=0,old=args.base,new=args.head)
                        report['barrier_reached']=True;report['barrier']='actual-Git-push-post-exec-before-userspace';changed=True;save()
            elif stopped==(signal.SIGTRAP|0x80):
                info=ctypes.create_string_buffer(128);ptrace(0x420e,pid,128,ctypes.byref(info))
                if int.from_bytes(info.raw[4:8],'little')!=0xc000003e:raise Hold('traced syscall architecture differs')
                if info.raw[0]==1:
                    regs=Regs();ptrace(12,pid,0,ctypes.byref(regs))
                    if regs.orig_rax in (59,322):
                        path=string(pid,regs.rdi if regs.orig_rax==59 else regs.rsi)
                        if path=='/usr/bin/git':
                            arguments=vector(pid,regs.rsi if regs.orig_rax==59 else regs.rdx)
                            pending[pid]=dict(path=path,argv=arguments)
                            if args.fault=='lock-name' and len(arguments)>1 and arguments[1]=='merge-tree' and not changed:
                                report['lease_witness']=lease_witness(root)
                                report['lock_before_fault']=stat_identity(named[0]);os.rename(named[0],backup)
                                fd=os.open(str(named[0]),os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600);os.close(fd)
                                report['lock_after_fault']=stat_identity(named[0])
                                if report['lock_before_fault'][:2]==report['lock_after_fault'][:2]:raise Hold('named lock fault did not change identity')
                                report['barrier_reached']=True;report['barrier']='actual-writer-merge-tree-exec-entry-after-lease';changed=True;save()
            deliver=0 if stopped in (signal.SIGTRAP,signal.SIGSTOP,signal.SIGTRAP|0x80) or event else stopped
            ptrace(24,pid,0,deliver)
        report['actual_entry_exit']=rootcode
        report['Git_push_exit_codes']=[row['exit_code'] for row in report['Git_executions'] if row['kind']=='push' and 'exit_code'in row]
        if args.fault=='lock-name' and changed:
            # Only after traced candidate AND descendants terminated. Restore
            # original scratch inode so independent recovery uses unchanged locks.
            with backup.open('rb') as stream:
                fcntl.flock(stream,fcntl.LOCK_EX|fcntl.LOCK_NB);fcntl.flock(stream,fcntl.LOCK_UN)
            named[0].unlink();os.rename(backup,named[0]);report['original_named_lock_restored_after_candidate_exit']=stat_identity(named[0])==identities[0]
        report['classification']='actual-owned-child-fault-observed' if changed else 'capability-HOLD';save()
        return rootcode if changed else 86
    except BaseException:
        for pid in tracked:
            try:os.kill(pid,signal.SIGKILL)
            except ProcessLookupError:pass
        while tracked:
            try:pid,status=os.waitpid(-1,0x40000000);tracked.discard(pid)
            except ChildProcessError:break
        # Failure cleanup never grants a retry or claims a successful fault.
        if backup.exists():
            if named[0].exists():named[0].unlink()
            os.rename(backup,named[0])
        save();return 86
if __name__=='__main__':raise SystemExit(main())
