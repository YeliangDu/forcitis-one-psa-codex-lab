"""Test containment only: execute byte-exact selected CLI, no recovery logic."""
import hashlib
import json
import os
from pathlib import Path
import re
import socket
import sys


def net_check(parent_inode):
    if os.geteuid() == 0:
        raise RuntimeError('non-root required')
    if os.stat('/proc/self/ns/net').st_ino == parent_inode:
        raise RuntimeError('independent network namespace required')
    if {name for _,name in socket.if_nameindex()} != {'lo'}:
        raise RuntimeError('non-loopback interface exists')
    rows = Path('/proc/net/route').read_text().splitlines()[1:]
    if any(row.split()[0] != 'lo' for row in rows if row.split()):
        raise RuntimeError('external route exists')


def main():
    root, target, expected, parent = Path(sys.argv[1]).resolve(strict=True), Path(sys.argv[2]).resolve(strict=True), sys.argv[3], int(sys.argv[4])
    net_check(parent)
    raw = target.read_bytes()
    if hashlib.sha256(raw).hexdigest() != expected or root not in target.parents:
        raise RuntimeError('selected CLI pin/path mismatch')
    python_roots = {Path(sys.prefix).resolve(), Path(sys.base_prefix).resolve()}
    def allowed(path):
        if isinstance(path, int): return False
        resolved = Path(os.fsdecode(path)).resolve()
        return resolved == root or root in resolved.parents or any(resolved == p or p in resolved.parents for p in python_roots)
    violations=[]
    def audit(event, args):
        denied = event in {'os.system','os.exec','os.posix_spawn','os.fork','os.forkpty','subprocess.Popen',
            'socket.__new__','socket.connect','socket.bind','sqlite3.connect','ctypes.dlopen',
            'os.chdir','os.remove','os.rename','os.mkdir','os.rmdir','os.link','os.symlink','os.chmod','os.chown','os.truncate','os.utime'}
        if event == 'import' and args[0].split('.')[0] in {'ctypes','_ctypes','cffi','socket','_socket','subprocess','_posixsubprocess','multiprocessing','pty'}:
            denied = True
        if event == 'open':
            path, mode, flags = args
            denied |= not allowed(path) or any(c in (mode or '') for c in 'wax+') or bool(flags & (os.O_WRONLY|os.O_RDWR|os.O_CREAT|os.O_TRUNC|os.O_APPEND))
        if event in ('os.listdir','os.scandir') and args:
            denied |= not allowed(args[0])
        if denied:
            violations.append(event)
            raise PermissionError('P1_CONTAINMENT_DENIED:' + event)
    sys.dont_write_bytecode = True
    sys.argv = [str(target), *sys.argv[5:]]
    sys.addaudithook(audit)
    code=None
    try:
        exec(compile(raw, str(target), 'exec'), dict(__name__='__main__', __file__=str(target), __package__=None, __cached__=None))
    except SystemExit as status:
        code=status.code
    if violations:
        raise RuntimeError('candidate attempted forbidden side effect: '+violations[0])
    if code is not None:
        raise SystemExit(code)


if __name__ == '__main__':
    try: main()
    except (RuntimeError, PermissionError) as error:
        print('CAPABILITY_HOLD:'+str(error), file=sys.stderr)
        raise SystemExit(86)
