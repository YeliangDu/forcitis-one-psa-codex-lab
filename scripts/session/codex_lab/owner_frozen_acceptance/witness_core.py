"""Draft hosted primitives; one-test must freeze case construction/orchestration separately."""
import fcntl,hashlib,importlib.util,json,os,select,signal,sqlite3,stat,subprocess,sys,time
from pathlib import Path

CLOSURE="5094eb8b0aa25e6dae12b3f658a74a5f865c29c14c9c02e427800378a0fa6287"
SCOPE_SHA="ca67e5d8e74408c0c87a7f30dbca8f6b2f26b8eec07deaaab3cc0a20f3df5b4f"
CRASH_SHA="a70cca5aba37acd27a42703339ac318e87e3dab844b0477552d4fe4b769abd28"
class Hold(RuntimeError): pass
def need(ok,why):
    if not ok: raise Hold(why)
def sha(raw): return hashlib.sha256(raw).hexdigest()
def load_fixture(path,pin,name):
    raw=path.read_bytes();need(sha(raw)==pin,"independent fixture bytes differ")
    spec=importlib.util.spec_from_file_location(name,path)
    module=importlib.util.module_from_spec(spec);sys.modules[name]=module
    spec.loader.exec_module(module)
    return module

class Witness:
    def __init__(self,root,source,scope_fixture,crash_fixture,private_secrets):
        need(sys.platform=="linux" and os.geteuid()!=0
             and os.environ.get("RUNNER_ENVIRONMENT")=="github-hosted","nonroot hosted Linux required")
        need(os.environ.get("GITHUB_REPOSITORY")=="YeliangDu/forcitis-one-psa-codex-lab"
             and os.environ.get("GITHUB_REPOSITORY_ID")=="1449415666","wrong Lab")
        need(os.environ.get("HOST_NET") and os.readlink("/proc/self/ns/net")!=os.environ["HOST_NET"]
             and "NoNewPrivs:\t1" in Path("/proc/self/status").read_text(),"private netns/no-new-privileges required")
        namespace=os.readlink("/proc/self/ns/net")
        ip=next((p for p in ("/usr/sbin/ip","/usr/bin/ip") if Path(p).is_file()),None)
        need(ip is not None,"fixed absolute ip missing")
        links=subprocess.check_output([ip,"-o","link","show"],env={"PATH":"/usr/bin:/bin"},text=True,timeout=5)
        need([line.split(":",2)[1].strip().split("@",1)[0] for line in links.splitlines() if line.strip()]==["lo"]
             and os.readlink("/proc/self/ns/net")==namespace,"external interface or changed netns")
        need(not any(os.environ.get(k) for k in ("GH_TOKEN","GITHUB_TOKEN","GH_CONFIG_DIR",
             "SSH_AUTH_SOCK","OPENAI_API_KEY")),"credential environment refused")
        self.root=Path(root);self.source=Path(source)
        need(self.root.resolve(strict=True)==self.root
             and self.root.parent==Path(os.environ["RUNNER_TEMP"]).resolve(strict=True)
             and self.root.name.startswith("lab-owner-"),"fresh test-owned root required")
        need(stat.S_IMODE(self.root.stat().st_mode)==0o700 and self.root.stat().st_uid==os.geteuid(),"private root")
        self.ledger=self.root/"ledger";self.db=self.ledger/"runtime.sqlite3"
        need(self.ledger.resolve(strict=True)==self.ledger and self.db.is_file() and not self.db.is_symlink(),
             "existing seeded scratch ledger required; driver never initializes it")
        raw=(self.source/"SOURCE-CLOSURE.json").read_bytes();need(sha(raw)==CLOSURE,"wrong candidate")
        closure=json.loads(raw)
        for name,pin in closure["files_sha256"].items():
            path=self.source/name
            need(path.resolve(strict=True)==path and sha(path.read_bytes())==pin,"candidate source changed")
        self.F=load_fixture(Path(scope_fixture),SCOPE_SHA,"frozen_owner_scope_fixture")
        self.C=load_fixture(Path(crash_fixture),CRASH_SHA,"frozen_owned_crash_fixture")
        self.secrets=tuple(s.encode() for s in private_secrets)
        self.env=dict(PATH="/usr/bin:/bin",HOME=str(self.root),LANG="C.UTF-8",
                      PYTHONDONTWRITEBYTECODE="1",RUNNER_ENVIRONMENT="github-hosted",
                      RUNNER_TEMP=os.environ["RUNNER_TEMP"],HOST_NET=os.environ["HOST_NET"])
        self.child=None;self.owned=None;self.audit=[]
        self.ident=(self.db.stat().st_dev,self.db.stat().st_ino)
    def private(self,path):
        path=Path(path)
        need(path.resolve(strict=True)==path and path.is_relative_to(self.root)
             and path.is_file() and stat.S_IMODE(path.stat().st_mode)==0o600
             and path.stat().st_uid==os.geteuid(),"private case input required")
        return path
    def argv(self,command,binding):
        binding=self.private(binding)
        b=json.loads(binding.read_text())
        need(b["ledger"]["directory"]==str(self.ledger)
             and b["role"]["physical_cwd"]==str(self.root/"work"),"non-scratch binding target")
        refs=b["evidence_references"]
        need(set(refs)<={"broker-installation","runtime-startup","runtime-actions",
                       "owner-transfer","owner-transfer-renewal"},"native/external handler scope refused")
        for reference in refs.values(): self.private(reference["path"])
        endpoint=b["native"]["endpoint"]
        for name in ("alias","alias_parent","target","target_parent"):
            need(Path(endpoint[name]["path"]).is_relative_to(self.root),"external endpoint")
        for name in ("alias_parent","target","target_parent"):
            p=Path(endpoint[name]["path"]);need(p.resolve(strict=True)==p,"nonphysical endpoint resource")
        need(Path(endpoint["alias"]["path"]).resolve(strict=True)==Path(endpoint["target"]["path"]),
             "endpoint alias leaves scratch")
        need(b["native"]["proxy"]["path"]=="/usr/bin/false","real native proxy refused")
        installation=json.loads(self.private(refs["broker-installation"]["path"]).read_text())
        need(installation["runtime_directory"]==str(self.ledger)
             and installation["physical_cwd"]==str(self.root/"work")
             and Path(installation["native_endpoint"]).resolve(strict=True)==Path(endpoint["target"]["path"])
             and Path(installation["proxy_binary"]).is_relative_to(self.root)
             and Path(installation["proxy_binary"]).resolve(strict=True)==Path("/usr/bin/false")
             and installation["gh_binary"]=="/usr/bin/false","non-synthetic installation refused")
        return self.F.public_args(self.source,command,binding,sha(binding.read_bytes()),CLOSURE)
    def decode(self,result):
        need(len(result.stdout)<=1024*1024 and len(result.stderr)<=1024*1024,"oversize CLI result")
        if any(secret and secret in result.stdout+result.stderr for secret in self.secrets):
            raise AssertionError("CLI_CREDENTIAL_DISCLOSURE")
        value=json.loads(result.stdout)
        self.audit.append(dict(exit=result.returncode,stdout_sha256=sha(result.stdout),
                               stderr_sha256=sha(result.stderr),action=value.get("action")))
        return value
    def call(self,binding,request):
        argv=self.argv("call",binding)+["--request",str(self.private(request))]
        result=subprocess.run(argv,cwd=self.root,env=self.env,capture_output=True,timeout=15)
        return self.decode(result)
    def snapshot(self):
        need((self.db.stat().st_dev,self.db.stat().st_ino)==self.ident,"database inode changed")
        # A real read transaction, not immutable=1 against a concurrently active writer.
        with sqlite3.connect(self.db.as_uri()+"?mode=ro",uri=True) as db:
            db.row_factory=sqlite3.Row;db.execute("BEGIN")
            return {table:[dict(r) for r in db.execute("select * from "+table)]
                    for table in ("owner","tasks","effects","events","native_dispatch",
                                  "native_tool_operations","waits","role_registration",
                                  "native_handoff","orphan_handoff_archive")
                    if db.execute("select 1 from sqlite_schema where type='table' and name=?",(table,)).fetchone()}
    def start(self,binding):
        need(self.child is None or self.child.poll() is not None,"owned broker already live")
        argv=self.argv("serve",binding)
        # Raw private logs must never be uploaded; publish hashes/sanitized facts only.
        out=self.root/("serve-"+str(len(self.audit))+".stdout")
        err=out.with_suffix(".stderr")
        with out.open("xb") as stdout,err.open("xb") as stderr:
            out.chmod(0o600);err.chmod(0o600)
            self.child=subprocess.Popen(argv,cwd=self.root,env=self.env,stdout=stdout,stderr=stderr)
        deadline=time.monotonic()+8
        while self.child.poll() is None and not (self.ledger/"broker.sock").exists() and time.monotonic()<deadline:
            time.sleep(.02)  # Readiness wait, never terminal/ownership proof.
        need(self.child.poll() is None and (self.ledger/"broker.sock").exists(),"public startup capability HOLD")
        self.owned=self.C.OwnedCrashSocket(self.root,self.ledger,self.child,argv)
        self.audit.append(self.owned.observe_owned_startup())
    def read_only_status(self,binding,request):
        before=self.snapshot();value=self.call(binding,request);after=self.snapshot()
        assert before==after,"STATUS_MUTATED_LEDGER"
        assert value.get("credential_returned") is False and value.get("read_only") is True
        return value
    def race(self,binding,requests,barrier_path,barrier_sha):
        need(len(requests)==2 and sha(Path(barrier_path).read_bytes())==barrier_sha,"exact two-client barrier required")
        before=self.snapshot();owner=before["owner"][0];children=[];go_fds=[];ready_fds=[]
        assert max(before["tasks"],key=lambda t:t["revision"])["phase"]=="fenced"
        args=[json.loads(self.private(p).read_text())["arguments"] for p in requests]
        assert args[0]["expected_generation"]==args[1]["expected_generation"]==owner["generation"]
        assert args[0]["client"]!=args[1]["client"]
        try:
            for request in requests:
                go_r,go_w=os.pipe();ready_r,ready_w=os.pipe()
                argv=self.argv("call",binding)+["--request",str(request)]
                child=subprocess.Popen([sys.executable,"-I","-S","-B",str(barrier_path),
                     str(go_r),str(ready_w),"--",*argv],cwd=self.root,env=self.env,
                     pass_fds=(go_r,ready_w),stdout=subprocess.PIPE,stderr=subprocess.PIPE)
                os.close(go_r);os.close(ready_w)
                children.append(child);go_fds.append(go_w);ready_fds.append(ready_r)
            deadline=time.monotonic()+8
            for fd in ready_fds:
                need(select.select([fd],[],[],max(0,deadline-time.monotonic()))[0]
                     and os.read(fd,1)==b"R","two real CLI barrier unavailable")
            for fd in go_fds:os.write(fd,b"G")
            values=[]
            for child in children:
                stdout,stderr=child.communicate(timeout=15)
                values.append(self.decode(subprocess.CompletedProcess(child.args,child.returncode,stdout,stderr)))
            after=self.snapshot();new=after["owner"][0]
            assert sorted(v["action"] for v in values)==["claimed","lost"]
            assert new["generation"]==owner["generation"]+1 and new["paused"]==1
            winner=next(a for a in args if a["client"]==new["client"])
            assert new["token_sha"]==sha(winner["new_owner_token"].encode())
            transfers=[e for e in after["effects"] if e["kind"]=="owner-transfer"]
            assert len(transfers)==1 and transfers[0]["phase"]=="claimed" and transfers[0]["issue_count"]==1
            assert sum(e["kind"]=="one-approved-contender-owner-granted" for e in after["events"])==1
            for table in ("tasks","native_dispatch","native_tool_operations","waits","role_registration",
                          "native_handoff","orphan_handoff_archive"):
                assert before.get(table)==after.get(table),"CLAIM_CHANGED_EXECUTION_OR_OLD_REVISION"
            assert [e for e in before["effects"] if e["kind"]!="owner-transfer"] == [
                e for e in after["effects"] if e["kind"]!="owner-transfer"],"OLD_EFFECT_REWRITTEN"
            return dict(winner=new["client"],generation=new["generation"],paused=True,
                        credential_returned=False,actual_cli_processes=2)
        finally:
            for fd in go_fds+ready_fds:os.close(fd)
            for child in children:
                if child.poll() is None:child.kill()
                child.wait(timeout=5)
    def hard_kill_reconcile(self,fresh_binding):
        need(self.child is not None and self.child.poll() is None and self.owned is not None,"no owned live broker")
        self.child.kill();self.child.wait(timeout=8)
        need(os.path.lexists(self.ledger/"broker.sock"),"hard-crash stale-socket family not reached")
        argv=self.argv("serve",fresh_binding)
        refusal=subprocess.run(argv,cwd=self.root,env=self.env,capture_output=True,timeout=10)
        value=self.decode(refusal)
        need(refusal.returncode==78 and value.get("action")=="hold","actual stale-socket startup refusal required")
        self.audit.append(self.owned.reconcile_killed_owned_child(refusal,argv))
        need(not os.path.lexists(self.ledger/"broker.sock"),"exact socket reconciliation incomplete")
    def controlled_stop(self):
        need(self.child is not None and self.child.poll() is None,"no original live child")
        self.child.send_signal(signal.SIGINT)
        try:self.child.wait(timeout=8)
        except subprocess.TimeoutExpired:
            self.child.kill();self.child.wait(timeout=8)
            raise Hold("controlled stop failed; now hard-crash family, do not remove socket")
        need(not os.path.lexists(self.ledger/"broker.sock"),"controlled cleanup not observed")

if __name__=="__main__":
    print("capability-HOLD: one-test must freeze the scratch case factory and matrix runner")
    raise SystemExit(86)
