"""Zero-task public Broker lane with one directly owned stdio App Server."""
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import selectors
import socket
import sqlite3
import stat
import subprocess
import sys
import threading
import time
import uuid

from runtime_broker import Handler, Server, message, physical, private_file, send
from runtime_store import Store, need

BIN_SHA='030316ecfbb41f4e7946e584b0063ef333b7a36cd312ba349f2f6869e39b62cc'
POLICY_SHA='5700b59494ba23cae5c06180af05bd06db21efd6e23045a104227b3b3792c4c3'
MARKER_SHA='6832cf9ec03c2a44a18dcc54a4f2477252438629fbad96e9d5674e54a4da6adf'
WORKER_SHA='103e9367e7067ec7bd76a9637b2a17864a33ab5975cecb0f12d3b3e66e78b7a7'
READS={'runtime.config-read':('config/read',{'includeLayers':True}),
       'runtime.thread-list':('thread/list',{'limit':1})}
COMMANDS=set(READS)|{'runtime.start','runtime.status','runtime.pause','runtime.stop'}
LIMIT=1024*1024

def sha(b): return hashlib.sha256(b).hexdigest()
def file_sha(p):
    with p.open('rb') as f: return hashlib.file_digest(f,'sha256').hexdigest()
def fields(value,keys):
    need(type(value) is dict and set(value)==set(keys),'wrong-owned-fields')
def uniq(pairs):
    obj={}
    for k,v in pairs:
        need(k not in obj,'duplicate-owned-json-key')
        obj[k]=v
    return obj
def invalid_number(_): raise ValueError('invalid-owned-number')
def decode(raw): return json.loads(raw,object_pairs_hook=uniq,parse_constant=invalid_number)
def private(path,kind):
    p=physical(str(path))
    info=p.lstat()
    need(kind(info.st_mode) and info.st_uid==os.geteuid() and not info.st_mode&0o077,
         'owned-resource-not-private')
    if kind==stat.S_ISREG: need(info.st_nlink==1,'owned-file-must-not-have-alias')
    return info

class Installation:
    def __init__(self,path,pin):
        path=physical(path)
        private(path,stat.S_ISREG)
        raw=private_file(str(path))
        need(sha(raw)==pin and os.geteuid()!=0,'owned-installation-pin-required')
        x=decode(raw)
        fields(x,('schema','kind','runtime_directory','owner_database','owner_installation_sha256',
                  'physical_cwd','control_socket','child','timeouts'))
        self.root=path.parent
        self.runtime=self.root/'runtime'
        self.database=self.root/'owner/runtime.sqlite3'
        self.cwd=self.root/'work'
        self.home=self.root/'home'
        self.sock=self.runtime/'owned.sock'
        need(path.name=='owned-installation.json' and x['schema']=='lab-owned-runtime-installation/1'
             and (x['runtime_directory'],x['owner_database'],x['physical_cwd'],x['control_socket'])
             ==tuple(map(str,(self.runtime,self.database,self.cwd,self.sock))),'wrong-owned-layout')
        for p in (self.root,self.runtime,self.database.parent,self.cwd,self.home):private(p,stat.S_ISDIR)
        private(self.database,stat.S_ISREG)
        self.pin=x['owner_installation_sha256']
        need(type(self.pin) is str and re.fullmatch('[0-9a-f]{64}',self.pin),'wrong-owner-pin')
        self.kind=x['kind']
        self.child=x['child']
        self.timeout=x['timeouts']
        fields(self.child,('argv','files_sha256','environment'))
        fields(self.timeout,('startup_ms','request_ms','stop_ms'))
        need(all(type(n) is int and 100<=n<=10000 for n in self.timeout.values()),'owned-timeout-bound')
        self.check_child()

    def check_child(self):
        argv,pins,env=(self.child[k] for k in ('argv','files_sha256','environment'))
        need(type(argv) is list and all(type(a) is str for a in argv) and type(pins) is dict
             and type(env) is dict,'owned-child-pins-required')
        if self.kind=='fixture-stdio-only':
            need(sys.platform=='linux' and self.root.name.startswith('owned-test-')
                 and {n for _,n in socket.if_nameindex()}=={'lo'},'fixture-network-namespace-required')
            need(len(argv)==11 and argv[1:4]==['-I','-S','-B']
                 and argv[5:]==['--audit',str(self.root/'child-audit.jsonl'),'--control',
                               str(self.root/'control.json'),'--replay',str(self.root/'old-frame.json')],
                 'wrong-fixture-argv')
            binary,worker=physical(argv[0]),physical(argv[4])
            need(binary==Path(sys.executable).resolve(strict=True) and worker.name=='stdio_fixture.py'
                 and worker.parent.name=='acceptance' and file_sha(worker)==WORKER_SHA
                 and set(pins)=={str(binary),str(worker)},'wrong-fixture-input')
            need(env==dict(PATH='/usr/bin:/bin',HOME=str(self.home),LANG='C.UTF-8',
                           PYTHONDONTWRITEBYTECODE='1'),'wrong-fixture-environment')
        elif self.kind=='official-stdio-only':
            need(sys.platform=='darwin' and self.root.parent==Path('/private/tmp')
                 and self.root.name.startswith('lab60-owned-'),'native-private-slot-required')
            need(len(argv)==10 and argv[:3]==['/usr/bin/sandbox-exec','-f',str(self.root/'runtime.sb')]
                 and argv[4:]==['app-server','--listen','stdio://','--strict-config','-c',
                                'cli_auth_credentials_store="file"'],'wrong-strict-native-argv')
            binary,profile=physical(argv[3]),physical(argv[2])
            private(profile,stat.S_ISREG)
            raw=profile.read_bytes()
            slot=os.fsencode(self.root)
            need(raw.count(slot)==1 and sha(raw.replace(slot,b'/private/tmp/lab60-v2-config-official-pref-read'))==POLICY_SHA
                 and file_sha(binary)==BIN_SHA and set(pins)=={str(binary),str(profile)},'native-reviewed-pins-required')
            tmp,codex=self.root/'tmp',self.home/'.codex'
            private(tmp,stat.S_ISDIR);private(codex,stat.S_ISDIR)
            marker=codex/'config.toml'
            private(marker,stat.S_ISREG)
            need(file_sha(marker)==MARKER_SHA,'native-private-marker-required')
            need(env==dict(PATH='/usr/bin:/bin',HOME=str(self.home),TMPDIR=str(tmp),LANG='en_US.UTF-8'),
                 'wrong-native-environment')
            need(Path('/etc').resolve(strict=True)==Path('/private/etc'),'system-config-alias-drift')
            for p in (Path('/private/etc/codex'),Path('/etc/codex')):
                need(not p.exists() and not p.is_symlink(),'system-config-present')
        else:
            raise ValueError('unknown-owned-installation-kind')
        need(all(type(pin) is str and file_sha(physical(p))==pin for p,pin in pins.items()),'owned-child-pin-mismatch')

class OwnerStore(Store):
    """Reuse owner verification, transaction, and pause without opening legacy transport."""
    def __init__(self,i):
        self.mutex=threading.RLock()
        self.db=sqlite3.connect(i.database.as_uri()+'?mode=rw',uri=True,isolation_level=None,check_same_thread=False)
        self.db.row_factory=sqlite3.Row
        try:
            self.db.execute('pragma synchronous=full')
            row=self.db.execute('select pin from installation where id=1').fetchone()
            need(row is not None and row['pin']==i.pin and self.current(self.db) is None,
                 'owned-empty-ledger-pin-required')
        except BaseException:
            self.db.close()
            raise

class Channel:
    def __init__(self,i):
        i.check_child()
        self.i=i
        self.epoch=uuid.uuid4().hex
        self.seq=0
        self.buffer=b''
        self.eof=False
        self.terminal=None
        directory=i.runtime/self.epoch
        directory.mkdir(mode=0o700)
        self.trace=directory/'stdio.jsonl'
        with (directory/'stderr').open('xb') as err:
            self.child=subprocess.Popen(i.child['argv'],cwd=i.cwd,env=i.child['environment'],
                stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=err,close_fds=True,start_new_session=True)
        self.record('spawn',dict(pid=self.child.pid))

    def record(self,direction,value):
        with self.trace.open('a',encoding='utf-8') as f:
            f.write(json.dumps(dict(direction=direction,epoch=self.epoch,value=value),ensure_ascii=True)+'\n')

    def send(self,x):
        need(self.child.poll() is None and not self.eof and self.terminal is None,'owned-wire-closed')
        self.record('send',x)
        self.child.stdin.write(json.dumps(x).encode()+b'\n');self.child.stdin.flush()

    def read(self,deadline):
        with selectors.DefaultSelector() as sel:
            sel.register(self.child.stdout,selectors.EVENT_READ)
            while b'\n' not in self.buffer:
                left=deadline-time.monotonic()
                need(left>0 and bool(sel.select(left)),'owned-wire-timeout')
                data=os.read(self.child.stdout.fileno(),65536)
                if not data:
                    self.eof=True
                    raise EOFError('owned-wire-eof')
                self.buffer+=data
                need(len(self.buffer)<=LIMIT,'owned-wire-bound')
        line,self.buffer=self.buffer.split(b'\n',1)
        self.record('receive',line.decode(errors='backslashreplace'))
        x=decode(line);need(type(x) is dict,'owned-wire-object-required')
        return x

    def rpc(self,method,params,timeout):
        self.seq+=1
        rid=f'{self.epoch}:{self.seq}'
        self.send(dict(id=rid,method=method,params=params))
        deadline=time.monotonic()+timeout/1000
        for _ in range(128):
            x=self.read(deadline)
            if 'id' not in x:
                need(type(x.get('method')) is str and x['method']!='configWarning','owned-wire-warning')
                continue
            need(x['id']==rid and 'error' not in x and 'method' not in x and 'result' in x
                 and self.child.poll() is None,'owned-wire-response-not-current')
            return rid,x['result']
        raise ValueError('owned-wire-notification-bound')

    def initialize(self):
        _,x=self.rpc('initialize',{'clientInfo':{'name':'lab-owned-runtime','version':'1'}},self.i.timeout['startup_ms'])
        need(type(x) is dict,'invalid-owned-initialize')
        if self.i.kind=='official-stdio-only':
            need(x.get('codexHome')==str(self.i.home/'.codex'),'owned-private-home-mismatch')
        self.send({'method':'initialized'})

    def closed(self):
        if self.terminal is not None or self.child.poll() is not None or self.eof:return True
        with selectors.DefaultSelector() as sel:
            sel.register(self.child.stdout,selectors.EVENT_READ)
            if sel.select(0):
                chunk=os.read(self.child.stdout.fileno(),65536)
                if not chunk:self.eof=True
                else:self.buffer+=chunk
                need(len(self.buffer)<=LIMIT,'owned-wire-bound')
        return self.eof

    def stop(self):
        if self.terminal is not None:return self.terminal
        live=self.child.poll() is None
        sent=False
        if not self.child.stdin.closed:
            try:
                self.child.stdin.close()
                sent=live
            except OSError:pass
        forced=False
        try:rc=self.child.wait(timeout=self.i.timeout['stop_ms']/1000)
        except subprocess.TimeoutExpired:
            forced=True;self.child.kill();rc=self.child.wait(timeout=3)
        with selectors.DefaultSelector() as sel:
            sel.register(self.child.stdout,selectors.EVENT_READ)
            for _ in range(32):
                if not sel.select(.05):break
                data=os.read(self.child.stdout.fileno(),65536)
                if not data:
                    self.eof=True
                    break
                self.buffer+=data
                if len(self.buffer)>LIMIT:break
        self.child.stdout.close()
        if self.buffer:self.record('retired-unattributed-bytes',self.buffer.decode(errors='backslashreplace'))
        self.terminal=dict(pid=self.child.pid,wait_returncode=rc,stdout_eof_observed=self.eof,
                           stdin_eof_requested_while_live=sent,forced=forced)
        self.record('terminal',self.terminal)
        return self.terminal

class OwnedBroker:
    def __init__(self,i):
        self.i=i;self.store=OwnerStore(i);self.execution_gate=threading.RLock()
        self.channel=None;self.state='stopped';self.epoch_owner=None;self.seen=set()

    def handle(self,request):
        fields(request,('command','auth','arguments'))
        c,a,x=(request[k] for k in ('command','auth','arguments'))
        need(type(c) is str and c in COMMANDS,'owned-command-not-allowlisted')
        fields(a,('client','generation','token'))
        with self.execution_gate:
            if c=='runtime.pause':
                fields(x,('paused',))
                return dict(self.store.pause(a,x['paused']),action='paused')
            with self.store.tx() as db:
                row=self.store.owner(db,a)
                need(self.store.current(db) is None,'owned-zero-task-required')
                owner=(row['client'],row['generation'],row['token_sha'])
                return self.operation(c,x,owner,bool(row['paused']))

    def operation(self,c,x,owner,paused):
        ch=self.channel
        if c=='runtime.status':
            fields(x,())
            if ch and self.state=='running' and (owner!=self.epoch_owner or ch.closed()):
                self.state='disconnected';ch.stop()
            return dict(action='status',state=self.state,paused=paused,epoch=ch.epoch if ch else None,
                        terminal=ch.terminal if ch else None,execution_authority=False)
        if c=='runtime.start':
            fields(x,())
            need(not paused and (ch is None or ch.terminal is not None),'owned-paused-or-running')
            self.channel=ch=Channel(self.i);self.epoch_owner=owner;self.seen.clear()
            try:ch.initialize()
            except (ValueError,TypeError,OSError,EOFError,subprocess.SubprocessError):
                self.state='faulted';ch.stop();raise
            self.state='running'
            return dict(action='started',epoch=ch.epoch,pid=ch.child.pid,execution_authority=False)
        fields(x,('epoch','request_id'))
        rid=x['request_id']
        need(type(rid) is str and 0<len(rid)<=128,'invalid-owned-request-id')
        need(ch is not None and x['epoch']==ch.epoch and owner==self.epoch_owner,'stale-owned-epoch')
        if c=='runtime.stop':
            terminal=ch.stop();self.state='stopped'
            return dict(action='stopped',epoch=ch.epoch,request_id=rid,native_id=None,terminal=terminal)
        need(not paused and self.state=='running' and rid not in self.seen and len(self.seen)<4096,
             'owned-paused-disconnected-or-replayed')
        self.seen.add(rid)
        try:
            need(not ch.closed(),'owned-channel-disconnected')
            method,params=READS[c]
            native_id,result=ch.rpc(method,params,self.i.timeout['request_ms'])
            return dict(action='ok',epoch=ch.epoch,request_id=rid,native_id=native_id,result=result)
        except (ValueError,TypeError,OSError,EOFError,subprocess.SubprocessError):
            self.state='faulted';ch.stop();raise

    def close(self):
        try:
            if self.channel:self.channel.stop()
        finally:self.store.db.close()

def run(mode,path,pin,request):
    i=Installation(path,pin)
    if mode=='call-owned':
        need(request is not None and physical(request).parent==i.root,'owned-request-required')
        private(i.sock,stat.S_ISSOCK)
        value=decode(private_file(request))
        with socket.socket(socket.AF_UNIX,socket.SOCK_STREAM) as client:
            client.settimeout(15);client.connect(str(i.sock));send(client,value)
            print(json.dumps(message(client),sort_keys=True))
        return 0
    need(mode=='serve-owned' and request is None,'owned-serve-arguments')
    os.umask(0o077)
    lock=i.runtime/'owned.lock'
    fd=os.open(lock,os.O_RDWR|os.O_CREAT|os.O_NOFOLLOW,0o600)
    try:
        private(lock,stat.S_ISREG)
        fcntl.flock(fd,fcntl.LOCK_EX|fcntl.LOCK_NB)
        need(not i.sock.exists() and not i.sock.is_symlink(),'stale-owned-socket')
        broker=OwnedBroker(i)
        try:
            with Server(str(i.sock),Handler) as server:
                os.chmod(i.sock,0o600);before=private(i.sock,stat.S_ISSOCK)
                server.broker=broker
                try:server.serve_forever(poll_interval=.1)
                except KeyboardInterrupt:pass
                finally:
                    current=i.sock.lstat()
                    if (current.st_dev,current.st_ino)==(before.st_dev,before.st_ino):i.sock.unlink()
        finally:broker.close()
        return 0
    finally:os.close(fd)
