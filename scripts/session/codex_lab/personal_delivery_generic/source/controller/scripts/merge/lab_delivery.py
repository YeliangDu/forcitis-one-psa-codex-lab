"""Exact-scope Lab delivery, using the existing release queue and journal."""
import argparse
from contextlib import closing, contextmanager
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import sqlite3
import stat
import subprocess
import sys
import tempfile
import time
from uuid import UUID

if __name__ == "__main__":
    sys.path.insert(0,str(Path(__file__).resolve().parent))
import lab_main_https as port
from ordinary_first_card import _closure, _read, _utc_expiry, digest, require

KIND="personal-delivery-v1"
GRANT_FIELDS={"kind","repo_id","subject","expires_at","closure_sha256","registry_sha256",
              "approval_sha256","metadata_sha256","role"}
HOLD=dict(action="HOLD",allow_reserve=False,allow_send=False)
LOCKS=("merge.queue.merge-lock","merge.queue.lock","merge.queue.heads-lock")
GIT_COMMANDS={"rev-parse","remote","config","merge-tree","commit-tree","show","merge-base","cat-file","status"}


def pin(value):
    return hashlib.sha256(json.dumps(value,sort_keys=True,separators=(",",":"),allow_nan=False).encode()).hexdigest()


def role_valid(role):
    try:
        return (type(role) is dict and set(role)=={"name","session_id"} and
                type(role["name"]) is str and re.fullmatch(r"one-[a-z0-9-]+",role["name"]) and
                type(role["session_id"]) is str and str(UUID(role["session_id"]))==role["session_id"])
    except (ValueError,TypeError,KeyError,AttributeError):
        return False


def authorize(permit, observed, *, recover_only):
    """The closure has no card-specific values; every write has one exact subject."""
    try:
        s=permit["subject"];n=s["number"]
        sha=pin(permit)
        require(type(permit) is type(observed) is dict and type(recover_only) is bool
                and type(n) is int and n>0 and observed["kind"]==permit["kind"]==KIND
                and observed["action"]==permit["action"] and permit["action"] in ("first-send","recover-only")
                and role_valid(permit["role"]) and observed["role"]==permit["role"]
                and observed["paused"] is False and observed["coordinator_exclusive"] is True
                and observed["conflict"] is False and observed["permit_sha256"]==sha
                and observed["metadata"]==dict(head=s["head"],base=s["base"],role=permit["role"],permit_sha256=sha)
                and type(observed["fifo"]) is list and observed["fifo"] in ([],[n])
                and all(type(x) is int for x in observed["fifo"]), "delivery-authorization-mismatch")
        recovered=recover_only or permit["action"]=="recover-only"
        result=port.authorize(permit,observed,recover_only=recovered,number=n)
        if result["allow_send"]:
            health=observed["health"]
            require(observed["fifo"]==[n] and health["action"]=="healthy"
                    and health["head_sha"]==s["base"] and health.get("expired") is not True,
                    "delivery-first-send-not-ready")
        return result
    except (ValueError,TypeError,KeyError,AttributeError,OverflowError):
        return dict(HOLD)


def metadata(pr):
    """Stable PR metadata signed by the approver; live SHA separately checked."""
    return dict(title=pr["title"],body=pr["body"],draft=pr["draft"],
                labels=sorted(x["name"] for x in pr["labels"]),
                refs={k:pr[k]["ref"] for k in ("head","base")})


def _grant(a):
    g=port._json(_read(a.authorization,a.authorization_sha256,private=True))
    s=g["subject"]
    require(set(g)==GRANT_FIELDS and g["kind"]==KIND and type(g["repo_id"]) is int
            and g["repo_id"]==port.SCOPE["repo_id"]
            and type(s) is dict and set(s)==set(port.KEYS)
            and type(s["number"]) is int and s["number"]==a.pr and a.pr>0 and s["target"]=="main"
            and all(digest(s[k],40) for k in ("head","base")) and s["head"]!=s["base"]
            and g["closure_sha256"]==a.closure_sha256 and role_valid(g["role"])
            and all(digest(g[k],64) for k in ("approval_sha256","metadata_sha256","registry_sha256"))
            and datetime.now(timezone.utc)<_utc_expiry(g["expires_at"]),
            "delivery-independent-grant-invalid-or-expired")
    return g


def _git_env():
    env={k:v for k,v in os.environ.items() if not k.startswith("GIT_")}
    return dict(env,**port.GIT, GIT_OPTIONAL_LOCKS="0")


def _git(checkout,*args,check=True):
    r=subprocess.run(["/usr/bin/git","--no-replace-objects","-c","core.hooksPath=/dev/null",
                      "-C",str(checkout),*args],env=_git_env(),capture_output=True,text=True,timeout=90)
    if check:require(r.returncode==0,"delivery-fixed-git-failed")
    return r


def _profile(common):
    from mq_lab import checkout_profile
    root=common.parent
    require(_git(root,"rev-parse","--path-format=absolute","--git-common-dir").stdout.strip()==str(common)
            and checkout_profile(root,_git(root,"remote").stdout.splitlines(),
                _git(root,"remote","get-url","--all","origin").stdout.splitlines(),
                _git(root,"remote","get-url","--push","--all","origin").stdout.splitlines())=="lab",
            "delivery-canonical-origin-refused")


def _private_children(root,*parts):
    for n in parts:
        root=root/n
        root.mkdir(mode=0o700,exist_ok=True)
        st=root.lstat()
        require(root.resolve(strict=True)==root and stat.S_ISDIR(st.st_mode)
                and st.st_uid==os.geteuid() and not st.st_mode&0o022,"delivery-unsafe-workspace")
    return root


def _checkout(common,s):
    base=s["base"];head=s["head"];root=common.parent
    parent=_private_children(root,".codex-lab","delivery-worktrees",str(s["number"])+"-"+base)
    target=parent/root.name
    require(_git(root,"check-ignore","-q",str(parent),check=False).returncode==0,"delivery-worktree-not-ignored")
    if not os.path.lexists(target):
        for sha in (base,head):
            if _git(root,"cat-file","-e",sha+"^{commit}",check=False).returncode:
                _git(root,"fetch","--no-tags","--no-write-fetch-head","origin",sha)
        _git(root,"worktree","add","--detach",str(target),base)
    require(target.resolve(strict=True)==target and
            _git(target,"rev-parse","--path-format=absolute","--git-common-dir").stdout.strip()==str(common)
            and _git(target,"rev-parse","HEAD").stdout.strip()==base
            and not _git(target,"status","--porcelain","--untracked-files=all").stdout,
            "delivery-checkout-drift")
    return target


def _repo_pr(github,number):
    repo=github.repository()
    require((repo["id"],repo["full_name"],repo["private"],repo["fork"],repo["default_branch"])==
            (port.SCOPE["repo_id"],port.SCOPE["repo"],False,False,"main"),"delivery-wrong-repository")
    pr=github.pull(number)
    require(pr["number"]==number and all(pr[k]["repo"]["id"]==repo["id"] and
            pr[k]["repo"]["full_name"]==repo["full_name"] for k in ("head","base"))
            and pr["base"]["ref"]=="main","delivery-foreign-pr")
    return pr


def _completed(common,number,expected=None,live_only=False,expected_tree=None):
    """Read-only inspection never constructs a writer or acquires send locks."""
    from mq_lab import _path, _state
    from mq_lab_journal_v2 import _Storage, _schema, ReleaseJournalV2
    from mq_lab_operator import _module
    queue=_path(common)
    p=queue.parent/"releases.sqlite3"
    storage=_Storage(p);storage.check()
    with closing(sqlite3.connect(p.as_uri()+"?mode=ro",uri=True,timeout=2)) as db:
        db.execute("pragma query_only=on")
        db.execute("pragma trusted_schema=off")
        db.execute("begin")
        _schema(db,2);r=ReleaseJournalV2._read(db,number);db.rollback()
    storage.check()
    require(r is not None and r["status"]=="confirmed","delivery-confirmed-record-missing")
    subject={k:r[k] for k in port.KEYS}
    require(expected is None or expected==subject,"delivery-confirmed-subject-moved")
    present,items=_state(queue); require(present and number not in items,"delivery-confirmed-still-in-fifo")
    with os.fdopen(os.open(queue.parent/"merge.queue.heads",os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK),"rb") as f:
        require(stat.S_ISREG(os.fstat(f.fileno()).st_mode),"delivery-invalid-head-stamp-file")
        stamps=f.read(1048577).decode()
    require(len(stamps)<=1048576 and all(re.fullmatch(r"[1-9][0-9]* [a-f0-9]{40}",line)
            for line in stamps.splitlines()) and not any(line.startswith(str(number)+" ") for line in stamps.splitlines()),
            "delivery-confirmed-still-stamped")
    gh=_module("lab_queue_live").GithubRead()
    pr=_repo_pr(gh,number);tip=gh.main_ref()["object"]["sha"]; merge=r["merge_sha"]
    require(digest(tip,40) and pr.get("merged") is True and pr["merge_commit_sha"]==merge
            and pr["head"]["sha"]==r["head"] and (not live_only or tip==merge),"delivery-merged-pr-readback-drift")
    commit=gh._single("commits/"+merge)
    parents=[x["sha"] for x in commit["parents"]];tree=commit["commit"]["tree"]["sha"]
    require(commit["sha"]==merge and parents==[r["base"],r["head"]] and digest(tree,40)
            and (expected_tree is None or tree==expected_tree),"delivery-merge-object-drift")
    if tip!=merge:
        compare=gh._single("compare/"+merge+"..."+tip)
        require(compare["status"]=="ahead" and compare["merge_base_commit"]["sha"]==merge,
                "delivery-confirmed-not-in-current-main")
    # Use the exact hosted proof reader on an explicitly historical commit.
    # This cannot authorize sends or substitute for live current-main health.
    class HealthAtMerge:
        def main_ref(self):return dict(ref="refs/heads/main",object=dict(sha=merge))
        def __getattr__(self,k):return getattr(gh,k)
    health=_module("lab_main_health").check_main_health(gh if tip==merge else HealthAtMerge())
    require(health["action"]=="healthy" and health["head_sha"]==merge
            and gh.main_ref()["object"]["sha"]==tip,"delivery-exact-merge-health-unverified")
    if tip!=merge:health=dict(health,reason="exact-historical-main-hosted-health")
    return dict(action="delivered",subject=subject,journal=r,merge=merge,parents=parents,tree=tree,
                observed_main=tip,health=health,historical=(tip!=merge),
                fifo_absent=True,stamp_absent=True,send_port_entered=False)


def _receipt(common, result):
    folder=_private_children(common.parent,".codex-lab","delivery-receipts")
    r=result["journal"];path=folder/(str(r["number"])+"-"+result["merge"]+".json")
    # An idempotent completion artifact, not an execution ledger or a send permit.
    payload={k:result[k] for k in ("subject","journal","merge","parents","tree")}
    payload.update(kind="lab-personal-delivery-receipt-v1",health=result["health"],fifo_absent=True,stamp_absent=True)
    raw=(json.dumps(payload,sort_keys=True,indent=2)+"\n").encode()
    fd,tmp=tempfile.mkstemp(prefix=".delivery-receipt-",dir=folder)
    try:
        with os.fdopen(fd,"wb") as stream:
            stream.write(raw);stream.flush();os.fsync(stream.fileno())
        try:os.link(tmp,path)
        except FileExistsError:pass
    finally:os.unlink(tmp)
    require(_read(path,hashlib.sha256(raw).hexdigest(),private=True)==raw,"delivery-receipt-conflict")
    fd=os.open(folder,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW)
    try:os.fsync(fd)
    finally:os.close(fd)
    return str(path)


def _run(a,common):
    from mq_lab import _path, _state
    from mq_lab_active import admit
    from mq_lab_journal_v2 import ReleaseJournalV2
    from mq_lab_operator import ProductionPorts, run_command, _module, _first_card_locks
    from mq_queue import Queue
    _profile(common)
    q=_path(common); folder=q.parent
    sender=folder/"mq.runner.lock"; control=folder/"control-upgrade.runner-lock"
    g=_grant(a);s=g["subject"];n=s["number"]; path=folder/"releases.sqlite3"
    registry=common.parent/".codex-lab/session-copy/runtime/roles.json"
    gh=_module("lab_queue_live").GithubRead()
    # Snapshot existing names before opening them; no initializer or substitute locks.
    identities={k:port._named(folder/k) for k in LOCKS}
    sl,cl=map(port._named,(sender,control))
    journal_id=port._named(path)
    common_id=port._identity(common.lstat())

    def live_role_and_metadata():
        require(_grant(a)==g and not os.path.lexists(folder/"mq.paused"),"delivery-paused-or-grant-moved")
        roles=port._json(_read(registry,g["registry_sha256"]))["roles"]
        require(type(roles) is list and len({x["role"] for x in roles})==len({x["id"] for x in roles})==len(roles)
                and [x["id"] for x in roles if x["role"]==g["role"]["name"]]==[g["role"]["session_id"]],
                "delivery-registry-drift")
        pr=_repo_pr(gh,n)
        require(pr["head"]["sha"]==s["head"] and pin(metadata(pr))==g["metadata_sha256"],
                "delivery-live-pr-metadata-drift")
        return pr

    with port._sender_lock(sender,sl) as fd, port._sender_lock(control,cl) as control_fd:
        require(port._named(path)==journal_id,"delivery-journal-moved")
        ledger=ReleaseJournalV2(path); initial=ledger.load(n)
        recovering=a.command=="recover" or initial is not None
        if a.command=="recover":require(initial is not None,"delivery-recover-without-reservation")
        if initial is not None:
            require({k:initial[k] for k in port.KEYS}==s,"delivery-other-reservation")
        present,items=_state(q);require(present and items in ([],[n]),"delivery-fifo-conflict")
        pr=live_role_and_metadata()
        if not recovering:
            require(a.key is not None and pr["state"]=="open" and pr["merged"] is False
                    and pr["base"]["sha"]==s["base"] and gh.main_ref()["object"]["sha"]==s["base"],
                    "delivery-no-exact-open-pr-or-key")
        checkout=_checkout(common,s)
        def command(argv):
            require(argv[:1]==["git"] and len(argv)>1 and argv[1] in GIT_COMMANDS,"delivery-git-command-refused")
            return _git(checkout,*argv[1:],check=False)
        ports=ProductionPorts(common,command)
        tree=_git(checkout,"merge-tree","--write-tree",s["base"],s["head"]).stdout.strip()
        require(digest(tree,40),"delivery-merge-tree-conflict")
        permit=dict(port.SCOPE,kind=KIND,action="first-send",subject=s,common_dir=str(common),
                    common_identity=common_id,journal=journal_id,locks=dict(merge=identities[LOCKS[0]],sender=sl),
                    lock_ids={name:[value["device"],value["inode"]] for name,value in identities.items()},
                    checkout=str(checkout),closure_sha256=a.closure_sha256,role=g["role"],
                    registry_sha256=g["registry_sha256"],expires_at=g["expires_at"],control_lock=cl,
                    ordinary_approval_sha256=g["approval_sha256"],expected_tree=tree,
                    git_config_sha256=hashlib.sha256(_git(checkout,"config","--local","--null","--list").stdout.encode()).hexdigest())
        approved_plan=False
        own=[None]

        def approval():
            row=ports.load(n)
            require(type(row) is dict and pin(row)==g["approval_sha256"] and row["mode"]=="normal"
                    and row["author"]==g["role"]["name"] and set(row["required_reviews"])=={"首轮","终审"}
                    and all(row[k]==v for k,v in s.items()),"delivery-installed-approval-drift")
            return row

        def observed(record):
            fresh=live_role_and_metadata()
            require(_path(common)==q and port._identity(common.lstat())==common_id and port._named(path)==journal_id
                    and port._named(sender)==port._identity(os.fstat(fd))==sl
                    and port._named(control)==port._identity(os.fstat(control_fd))==cl
                    and _git(checkout,"rev-parse","HEAD").stdout.strip()==s["base"]
                    and not _git(checkout,"status","--porcelain","--untracked-files=all").stdout
                    and hashlib.sha256(_git(checkout,"config","--local","--null","--list").stdout.encode()).hexdigest()==permit["git_config_sha256"],
                    "delivery-existing-lease-or-checkout-moved")
            _first_card_locks(q,permit)
            _profile(common)
            present,fifo=_state(q)
            require(present,"delivery-queue-missing")
            if record is None:
                require(fresh["state"]=="open" and fresh["merged"] is False and
                        fresh["base"]["sha"]==s["base"] and gh.main_ref()["object"]["sha"]==s["base"],
                        "delivery-preimage-drift")
            sha=pin(permit)
            return dict(permit,paused=os.path.lexists(folder/"mq.paused"),coordinator_exclusive=True,conflict=False,
                        fifo=fifo,registry_adopted=True,permit_sha256=sha,
                        metadata=dict(head=s["head"],base=s["base"],role=g["role"],permit_sha256=sha),
                        now=datetime.now(timezone.utc).isoformat(),
                        reservation=None if record is None else {k:record[k] for k in port.KEYS},
                        health=dict(action="healthy" if approved_plan else "hold",head_sha=s["base"]))

        if not recovering:
            approval()
            plan=run_command("plan",n,common,ports)
            require(plan["action"]=="eligible" and all(plan[k]==v for k,v in s.items()),
                    "delivery-formal-plan-not-eligible")
            approved_plan=True
            before=observed(None)
            require(before["fifo"] in ([],[n]) and authorize(permit,dict(before,fifo=[n]),recover_only=False)["allow_reserve"],
                    "delivery-pre-admission-refused")
            class OneCardQueue(Queue):
                @contextmanager
                def merge_lock(self):
                    with super().merge_lock(expected_identity=permit["lock_ids"][LOCKS[0]]):
                        require(_state(q) in ((True,[]),(True,[n])) and ledger.load(n) is None
                                and not os.path.lexists(folder/"mq.paused"),"delivery-admission-moved")
                        yield
            def verify_admission(intent):
                o=observed(None)
                require(o["fifo"] in ([],[n]) and authorize(permit,dict(o,fifo=[n]),recover_only=False)["allow_reserve"]
                        and _git(checkout,"merge-tree","--write-tree",s["base"],s["head"]).stdout.strip()==tree,
                        "delivery-admission-proof-moved")
                return ports.verify(intent,approval())
            admitted=admit(OneCardQueue(str(q)),s,verify_admission)
            if admitted["action"]=="hold":return dict(admitted,stage="admission",send_port_entered=False)
        else:
            admitted=None

        def recover_guard(current):
            require(current is not None and authorize(permit,observed(current),recover_only=True)["action"]=="READY",
                    "delivery-recovery-scope-moved")

        def reserve_guard(proposed):
            require(not recovering and own[0] is None and ledger.load(n) is None
                    and {k:proposed[k] for k in port.KEYS}==s
                    and authorize(permit,observed(None),recover_only=False)["allow_reserve"],"delivery-reservation-refused")

        def can_write():
            record=ledger.load(n)
            o=observed(record)
            decision=authorize(permit,o,recover_only=False)
            return (not recovering and o["fifo"]==[n] and
                    ((record is None and own[0] is None and decision["allow_send"])
                     or (record is not None and record==own[0] and decision["action"]=="READY")))

        def step(read_only):
            current=ledger.load(n)
            if read_only:recover_guard(current)
            else:require(authorize(permit,observed(current),recover_only=False)["allow_send"],"delivery-send-scope-refused")
            return run_command("once",n,common,ports,first_card_scope=permit,permit_check=can_write,recover_only=read_only,
                     reserve_guard=reserve_guard,reserved=lambda row:own.__setitem__(0,dict(row)),recover_guard=recover_guard)

        binding=None
        if not recovering:
            def final_check():
                _closure(a.closure,a.closure_sha256)
                return own[0] is not None and can_write()
            binding=port.MainHTTPSBinding(permit,checkout,folder/LOCKS[0],fd,final_check,a.key)
            ports._main_https=binding
        result=step(recovering)
        sent=bool(binding and binding.spent)
        # Never call the send-capable path a second time, even with an unknown reply.
        ports._main_https=None
        end=time.monotonic()+a.wait_seconds
        waiting={"exact-new-main-health-required","reserved-outcome-unconfirmed-or-inexact"}
        while result.get("reason") in waiting and ledger.load(n) is not None and time.monotonic()<end:
            time.sleep(min(10,max(0,end-time.monotonic())))
            if time.monotonic()>=end:break
            result=step(True)
        if result.get("action") in ("confirmed","skip"):
            done=_completed(common,n,expected=s,live_only=True,expected_tree=tree)
            done["receipt_path"]=_receipt(common,done)
            result=done
        return dict(result,admission=admitted,send_port_entered=sent,first_reservation=own[0] is not None)


def main():
    from mq_lab import _path
    p=argparse.ArgumentParser(allow_abbrev=False)
    p.add_argument("command",choices=["deliver","recover","inspect-completed"])
    p.add_argument("--pr",type=int,required=True)
    p.add_argument("--common",type=Path,required=True)
    p.add_argument("--closure",required=True)
    p.add_argument("--closure-sha256",required=True)
    p.add_argument("--authorization")
    p.add_argument("--authorization-sha256")
    p.add_argument("--key")
    p.add_argument("--wait-seconds",type=int,default=120)
    a=p.parse_args()
    os.umask(0o077)
    try:
        require(a.pr>0 and 0<=a.wait_seconds<=900,"delivery-invalid-command")
        root=_closure(a.closure,a.closure_sha256)
        require(root/"controller/scripts/merge/lab_delivery.py"==Path(__file__).resolve(),"delivery-entry-outside-closure")
        _path(a.common)
        os.environ["PATH"]="/opt/homebrew/bin:/usr/bin:/bin"
        if a.command=="inspect-completed":
            require(a.authorization is None and a.authorization_sha256 is None and a.key is None,"delivery-inspection-has-write-input")
            result=_completed(a.common,a.pr)
        else:
            require(a.authorization and a.authorization_sha256 and (a.command=="deliver" or a.key is None),
                    "delivery-explicit-authorization-required")
            result=_run(a,a.common)
    except BlockingIOError:
        result=dict(action="hold",reason="delivery-existing-coordinator-busy")
    except (OSError,ValueError,TypeError,AttributeError,KeyError,sqlite3.Error,subprocess.SubprocessError) as error:
        result=dict(action="hold",reason=str(error) if type(error) is ValueError else "delivery-protected-operation-held")
    print(json.dumps(result,sort_keys=True))
    return 0 if result.get("action")=="delivered" else 78


if __name__=="__main__":
    raise SystemExit(main())
