"""Disposable hosted lifecycle observer for the independent owner-transfer witness.

This adapter does not start/stop a process or connect to a socket. It can remove
only the exact socket witnessed on a directly owned, terminated fixture Broker.
It is not a runtime cleanup command and has no access to the user's trial.
"""
import fcntl
import json
import os
from pathlib import Path
import stat
import subprocess
import sys

def _need(ok, reason):
    if not ok:
        raise ValueError(reason)

def _type(info, kind, mode, links=None):
    _need(stat.S_IFMT(info.st_mode)==kind and info.st_uid==os.geteuid()
          and stat.S_IMODE(info.st_mode)==mode and (links is None or info.st_nlink==links),
          'unexpected-owned-fixture-resource')
    return info.st_dev,info.st_ino,info.st_uid,stat.S_IMODE(info.st_mode),info.st_nlink

class OwnedCrashSocket:
    def __init__(self, root, directory, child, expected_argv):
        _need(sys.platform=='linux' and os.geteuid()!=0 and os.environ.get('RUNNER_ENVIRONMENT')=='github-hosted',
              'unprivileged-hosted-only')
        host_net=os.environ.get('HOST_NET')
        _need(bool(host_net) and os.readlink('/proc/self/ns/net')!=host_net
              and 'NoNewPrivs:\t1' in Path('/proc/self/status').read_text(),'isolated-runner-only')
        parent=Path(os.environ['RUNNER_TEMP']).resolve(strict=True)
        root=Path(root); directory=Path(directory)
        _need(root.resolve(strict=True)==root and root.parent==parent and root.name.startswith('lab-owner-'),
              'exact-disposable-owner-fixture-root-required')
        _need(directory.resolve(strict=True)==directory and directory.parent==root,
              'exact-disposable-ledger-child-required')
        _type(root.lstat(),stat.S_IFDIR,0o700)
        self.directory=directory
        self.directory_id=_type(directory.lstat(),stat.S_IFDIR,0o700)
        _need(type(child)is subprocess.Popen and child.poll() is None and tuple(child.args)==tuple(expected_argv)
              and len(expected_argv)>1 and 'serve' in expected_argv,'exact-owned-public-serve-child-required')
        self.child=child
        self.socket_id=None
        self.lock_id=None
        self.cleaned=False

    def _directory_fd(self):
        fd=os.open(self.directory,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW)
        try:
            _need(_type(os.fstat(fd),stat.S_IFDIR,0o700)==self.directory_id
                  ==_type(self.directory.lstat(),stat.S_IFDIR,0o700),'ledger-directory-replaced')
            return fd
        except BaseException:
            os.close(fd)
            raise

    @staticmethod
    def _resource(fd,name,kind,mode,links=1):
        return _type(os.stat(name,dir_fd=fd,follow_symlinks=False),kind,mode,links)

    def observe_owned_startup(self):
        _need(self.child.poll() is None and self.socket_id is None,'no-live-first-owned-child')
        d=self._directory_fd()
        try:
            lock=self._resource(d,'broker.lock',stat.S_IFREG,0o600)
            s=self._resource(d,'broker.sock',stat.S_IFSOCK,0o600)
            fd=os.open('broker.lock',os.O_RDONLY|os.O_NOFOLLOW,dir_fd=d)
            try:
                _need(_type(os.fstat(fd),stat.S_IFREG,0o600,1)==lock,'lock-replaced-before-probe')
                try:fcntl.flock(fd,fcntl.LOCK_EX|fcntl.LOCK_NB)
                except BlockingIOError:pass
                else:raise ValueError('first-child-did-not-hold-lock')
            finally:os.close(fd)
            _need(self.child.poll() is None,'child-terminal-during-startup-observation')
            self.lock_id=lock;self.socket_id=s
            return dict(bound_socket_inode=s[1],original_lock_inode=lock[1],owned_process=self.child.pid,
                        competing_lock_excluded=True)
        finally:os.close(d)

    def reconcile_killed_owned_child(self, refusal, expected_refusal_argv):
        _need(not self.cleaned and self.socket_id is not None
              and type(refusal)is subprocess.CompletedProcess and tuple(refusal.args)==tuple(expected_refusal_argv)
              and 'serve' in expected_refusal_argv and refusal.returncode==78
              and json.loads(refusal.stdout).get('action')=='hold',
              'expected-public-stale-socket-refusal-required')
        code=self.child.wait(timeout=8)  # Only the original subprocess handle; no PID reattachment.
        _need(code<0,'actual-signal-termination-required-for-this-fixture')
        d=self._directory_fd()
        try:
            fd=os.open('broker.lock',os.O_RDONLY|os.O_NOFOLLOW,dir_fd=d)
            try:
                fcntl.flock(fd,fcntl.LOCK_EX|fcntl.LOCK_NB)
                _need(_type(os.fstat(fd),stat.S_IFREG,0o600,1)==self.lock_id
                      ==self._resource(d,'broker.lock',stat.S_IFREG,0o600),'original-lock-replaced')
                _need(_type(self.directory.lstat(),stat.S_IFDIR,0o700)==self.directory_id
                      and self._resource(d,'broker.sock',stat.S_IFSOCK,0o600)==self.socket_id,'socket-or-ledger-replaced')
                os.unlink('broker.sock',dir_fd=d)
                os.fsync(d)
                self.cleaned=True
            finally:os.close(fd)
        finally:os.close(d)
        return dict(owned_process=self.child.pid,owned_wait_exit=code,original_lock_inode=self.lock_id[1],
                    removed_only_bound_socket_inode=self.socket_id[1],formal_writer_allowed=False,
                    app_server_epoch_proved=False)
