"""Real Git HTTPS and git-http-backend in the private hosted namespace."""
import base64,fcntl,hashlib,http.server,json,os,shutil,signal,socket,sqlite3,ssl,subprocess,sys,tempfile,threading,time
from pathlib import Path
from urllib.parse import urlsplit

REPO="YeliangDu/forcitis-one-psa-codex-lab.git"
EXPECTED_AUTH="Basic "+base64.b64encode(b"x-access-token:synthetic-fixture-only").decode()
FIXREF="refs/heads/codex/lab69-apphttps-123456789abc-target"
LIFEREF="refs/heads/codex/lab69-apphttps-123456789abc-control"

def need(x,reason):
    if not x:raise AssertionError(reason)
def cmd(argv,cwd=None,env=None,input=None,pass_fds=()):
    return subprocess.run(argv,cwd=cwd,env=env,input=input,capture_output=True,pass_fds=pass_fds,timeout=20)
def git(cwd,*a):
    r=cmd(["/usr/bin/git","-C",str(cwd),*a])
    need(r.returncode==0,"fixture-Git-setup-failed")
    return r.stdout.decode().strip()
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def tip(repo,ref):return git(repo,"rev-parse",ref)
def blocked(p):
    with p.open("rb") as f:
        try:fcntl.flock(f,fcntl.LOCK_EX|fcntl.LOCK_NB)
        except BlockingIOError:return True
        else:return False
def load(merge):
    sys.path.insert(0,str(merge))
    from mq_queue import Queue
    from mq_lab_runtime import run_iteration
    from mq_lab_journal_v2 import ReleaseJournalV2,V1,ALTER,BINDINGS
    from mq_lab_operator import ProductionPorts
    from lab_https_transport import BoundCPrimeHTTPS,GitHTTPS,URL
    return Queue,run_iteration,ReleaseJournalV2,V1,ALTER,BINDINGS,ProductionPorts,BoundCPrimeHTTPS,GitHTTPS,URL

class TLSHandler(http.server.BaseHTTPRequestHandler):
    def log_message(self,*a):pass
    def do_GET(self):self.run_cgi()
    def do_POST(self):self.run_cgi()
    def run_cgi(self):
        if self.headers.get("Authorization")!=EXPECTED_AUTH:
            self.send_response(401);self.send_header("WWW-Authenticate",'Basic realm="lab-fixture"')
            self.end_headers();return
        self.server.authenticated+=1
        u=urlsplit(self.path)
        need(u.path.startswith("/"+REPO+"/"),"wrong-HTTPS-path")
        n=int(self.headers.get("Content-Length","0"))
        need(0<=n<=1024*1024,"HTTPS-fixture-content-length")
        request=self.rfile.read(n)
        env=dict(PATH="/usr/bin:/bin",GIT_PROJECT_ROOT=str(self.server.project),GIT_HTTP_EXPORT_ALL="1",
            PATH_INFO=u.path,QUERY_STRING=u.query,REQUEST_METHOD=self.command,
            CONTENT_TYPE=self.headers.get("Content-Type",""),CONTENT_LENGTH=str(n),
            REMOTE_USER="x-access-token",GIT_CONFIG_NOSYSTEM="1",GIT_CONFIG_GLOBAL="/dev/null")
        r=cmd(["/usr/bin/git","http-backend"],env=env,input=request)
        need(r.returncode==0,"git-http-backend-failed")
        headers,_,body=r.stdout.partition(b"\r\n\r\n")
        if u.path.endswith("/git-receive-pack"):
            self.server.receives+=1
            if self.server.mode=="drop":
                self.close_connection=True
                return
            if self.server.mode=="hold":
                self.server.entered.set()
                need(self.server.release.wait(10),"HTTPS-fixture-child-not-released")
        self.send_response(200)
        for line in headers.splitlines():
            if line.startswith((b"Content-Type:",b"Cache-Control:",b"Pragma:",b"Expires:")):
                k,v=line.decode().split(":",1);self.send_header(k,v.strip())
        self.send_header("Content-Length",str(len(body)));self.end_headers()
        self.wfile.write(body)

def actual(root,merge,ref,receiver,gitdir,cert,helper,sock,b,h,scope_file=None):
    Queue,run_iteration,Journal,V1,ALTER,BINDINGS,Ports,Bound,Adapter,URL=load(merge)
    queue_path=root/"forcitis-one-psa-codex-lab/.git/merge-queue/merge.queue"
    lock=Path(str(queue_path)+".merge-lock");st=lock.stat()
    scope=dict(repository=REPO.removesuffix(".git"),url=URL,ref=ref,expected=b,head=h,
                lock=dict(path=str(lock),device=st.st_dev,inode=st.st_ino),
                askpass=dict(path=str(helper),sha256=sha(helper)))
    def runner(argv,*,env,pass_fds):
        e=dict(env,HOME=str(root/"home"),LANG="C",GIT_CONFIG_NOSYSTEM="1",GIT_CONFIG_GLOBAL="/dev/null")
        argv=["/usr/bin/git","-c","credential.helper=","-c","credential.useHttpPath=true",
              "-c","http.followRedirects=false","-c","http.curloptResolve=github.com:443:127.0.0.1",
              "-c","http.sslCAInfo="+str(cert),"-c","core.hooksPath=/dev/null",*argv[1:]]
        r=cmd(argv,cwd=gitdir,env=e,pass_fds=pass_fds)
        if r.returncode and b"authentication" in r.stderr.lower():raise AssertionError("fixture-askpass-auth-denied")
        return dict(returncode=r.returncode)
    return Queue(str(queue_path)),Bound(Adapter(scope,str(sock),runner)),URL

def child(argv):
    root,merge,receiver,gitdir,cert,helper,sock=map(Path,argv[:7]);b,h=argv[7:]
    queue,bound,url=actual(root,merge,LIFEREF,receiver,gitdir,cert,helper,sock,b,h)
    with queue.merge_lock():
        bound(["push","--porcelain","--force-with-lease="+LIFEREF+":"+b,"origin",h+":"+LIFEREF],lambda:True)

def run(merge):
    Queue,run_iteration,Journal,V1,ALTER,BINDINGS,Ports,Bound,Adapter,URL=load(merge)
    with tempfile.TemporaryDirectory(prefix="lh-",dir="/tmp") as td:
        root=Path(td).resolve();(root/"home").mkdir()
        src=root/"src";src.mkdir()
        git(src,"init","--initial-branch=main");git(src,"config","user.name","fixture");git(src,"config","user.email","fixture@example.invalid")
        (src/"file").write_text("old");git(src,"add","file");git(src,"commit","-m","old");b=git(src,"rev-parse","HEAD")
        (src/"file").write_text("new");git(src,"commit","-am","new");h=git(src,"rev-parse","HEAD")
        project=root/"project";receiver=project/REPO;receiver.mkdir(parents=True)
        git(receiver,"init","--bare");git(receiver,"config","http.receivepack","true")
        for ref in (FIXREF,LIFEREF):git(src,"push",str(receiver),b+":"+ref)
        sender=root/"sender";sender.mkdir();git(sender,"init","--bare");git(sender,"fetch",str(src),h)
        cert,key=root/"cert.pem",root/"tls-key.pem"
        p=cmd(["/usr/bin/openssl","req","-new","-newkey","rsa:2048","-nodes","-x509","-days","1",
          "-subj","/CN=github.com","-addext","subjectAltName=DNS:github.com","-keyout",str(key),"-out",str(cert)])
        need(p.returncode==0,"fixture-TLS-certificate")
        helper=root/"askpass";shutil.copyfile(merge/"lab_app_askpass.py",helper);helper.chmod(0o700)
        sock=root/"credentials.sock";auth=socket.socket(socket.AF_UNIX,socket.SOCK_STREAM)
        auth.bind(str(sock));sock.chmod(0o600);auth.listen(3);auth.settimeout(.2)
        stop=threading.Event()
        def credential():
            while not stop.is_set():
                try:c,_=auth.accept()
                except TimeoutError:continue
                with c:
                    if c.recv(80)==b"lab-https-installation-password\n":c.sendall(b"synthetic-fixture-only\n")
        th=threading.Thread(target=credential,daemon=True);th.start()
        server=http.server.ThreadingHTTPServer(("127.0.0.1",443),TLSHandler)
        ctx=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);ctx.load_cert_chain(cert,key)
        server.socket=ctx.wrap_socket(server.socket,server_side=True)
        server.project=project;server.authenticated=server.receives=0;server.mode="drop"
        server.entered=threading.Event();server.release=threading.Event()
        threading.Thread(target=server.serve_forever,daemon=True).start()
        proc=None
        try:
            qdir=root/"forcitis-one-psa-codex-lab/.git/merge-queue";qdir.mkdir(parents=True)
            path=qdir/"merge.queue";path.touch();Path(str(path)+".merge-lock").touch(mode=0o600)
            db=qdir/"releases.sqlite3"
            with sqlite3.connect(db) as c:
                c.execute(V1);c.execute(ALTER);c.execute(BINDINGS);c.execute("pragma user_version=2")
            db.chmod(0o600)
            q,bound,_=actual(root,merge,FIXREF,receiver,sender,cert,helper,sock,b,h)
            j=Journal(db);q.enqueue(69);q.set_head(69,h)
            ports=Ports.__new__(Ports);ports._isolated_https=bound;ports._send_permit_check=lambda:True
            sends=[]
            class Writer:
                def send(self,intent):
                    need(j.load(69)["status"]=="reserved","HTTPS-send-before-reservation")
                    sends.append(intent)
                    ports.git(["push","--porcelain","--force-with-lease="+FIXREF+":"+b,"origin",h+":"+FIXREF])
                def readback(self,intent):return None
            intent=dict(number=69,head=h,base=b,target="main")
            initial=run_iteration(q,j,lambda n,h:dict(intent,action="eligible",release_key="fixture"),Writer(),allow_write=True)
            need(initial["action"]=="hold" and j.load(69)["status"]=="reserved" and tip(receiver,FIXREF)==h
                 and server.receives==1 and len(sends)==1,"HTTPS-accepted-but-reply-lost-not-proved")
            class Recovery:
                def send(self,_):raise AssertionError("HTTPS-RECOVERY-RESENT")
                def readback(self,x):return dict(x,merged=True,target_contains_merge=True,merge_sha=tip(receiver,FIXREF))
            again=run_iteration(q,j,lambda *_:None,Recovery(),allow_write=False,
                    healthy=lambda merge:dict(action="healthy",head_sha=tip(receiver,FIXREF)))
            need(again["action"]=="confirmed" and server.receives==1 and not q.numbers(),"HTTPS-recovery-not-exact")
            server.mode="hold"
            proc=subprocess.Popen([sys.executable,"-I","-B",__file__,"--child",*[str(p) for p in (root,merge,receiver,sender,cert,helper,sock)],b,h],
                                  stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,start_new_session=True)
            need(server.entered.wait(14) and tip(receiver,LIFEREF)==h,"HTTPS-real-child-not-reached")
            lock=Path(str(path)+".merge-lock")
            need(blocked(lock),"HTTPS-lock-not-held")
            # Kill the controlling process while the actual Git and HTTP child
            # remain waiting on the server; inherited descriptors must fence.
            proc.kill();proc.wait(timeout=5)
            need(blocked(lock),"HTTPS-descendant-dropped-lock-after-owner-death")
            server.release.set()
            end=time.monotonic()+9
            while time.monotonic()<end and blocked(lock):time.sleep(.04)
            need(not blocked(lock) and server.receives==2,"HTTPS-lock-not-released-after-child")
            return dict(actual_https=True,validated_tls=True,git_http_backend=True,synthetic_credentials_only=True,
                        receive_requests=server.receives,authenticated_requests=server.authenticated,
                        accepted_lost_reply=True,recovery_send_count=0,owner_death_descendant_lock=True,
                        descendant_terminal_lock_released=True)
        finally:
            server.release.set()
            if proc is not None and proc.poll() is None:proc.kill();proc.wait(timeout=4)
            server.shutdown();server.server_close();stop.set();th.join(timeout=2);auth.close()

if __name__=="__main__" and sys.argv[1:2]==["--child"]:child(sys.argv[2:])
