"""One explicitly authorized #70 merge under the trusted personal operator."""
import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

if __name__ == "__main__":
    sys.path.insert(0, str(Path(__file__).resolve().parent))

import lab_main_https as common_port
from ordinary_first_card import _closure, _read, digest, require

KIND = "personal-pr70-v1"
HOLD = dict(action="HOLD", allow_reserve=False, allow_send=False)


def authorize(permit, observed, *, recover_only):
    """Extend exact authority with exclusive operator and action boundaries."""
    if not (type(permit) is type(observed) is dict and type(recover_only) is bool
            and permit.get("kind") == observed.get("kind") == KIND
            and permit.get("action") in ("first-send", "recover-only")
            and permit["action"] == observed.get("action")
            and observed.get("coordinator_exclusive") is True and observed.get("conflict") is False
            and observed.get("fifo") in ([], [70])):
        return dict(HOLD)
    recovery = recover_only or permit["action"] == "recover-only"
    result = common_port.authorize(permit, observed, recover_only=recovery, number=70)
    if result["allow_send"] and observed["fifo"] != [70]:
        return dict(HOLD)
    return result


def run(a):
    from mq_github import sh
    from mq_lab import _path, _state, checkout_profile
    from mq_lab_active import admit
    from mq_lab_journal_v2 import ReleaseJournalV2
    from mq_lab_operator import ProductionPorts, _first_card_locks, run_command
    from mq_queue import Queue

    pack = _closure(a.closure,a.closure_sha256)
    require(pack/"controller/scripts/merge/lab_personal68.py" == Path(__file__).resolve(),
            "personal-executor-outside-closure")

    def read_permit():
        return common_port._json(_read(a.permit,a.permit_sha256,private=True))

    p = read_permit()
    require(set(p) == set(common_port.AUTHORITY) | {
        "kind","action","checkout","common_identity","expires_at","lock_ids","control_lock",
        "expected_tree","ordinary_approval_sha256","git_config_sha256"}
        and p["kind"] == KIND and p["closure_sha256"] == a.closure_sha256
        and (a.head,a.base)==("091c1e31fd1e33f29904c82d771f1e9d3d02979a","bf853c2c6ce5cb3e72172330d34aecdc75f0a331")
        and p["subject"] == dict(number=70,head=a.head,base=a.base,target="main")
        and all(digest(p[k],64) for k in ("ordinary_approval_sha256","git_config_sha256"))
        and digest(p["expected_tree"],40)
        and set(p["lock_ids"]) == {"merge.queue.merge-lock","merge.queue.lock","merge.queue.heads-lock"}
        and common_port._id(p["common_identity"]) and common_port._id(p["control_lock"]),
        "personal-permit-shape-or-exact-subject")
    require((a.command == "once") == (p["action"] == "first-send") or
            (a.command == "recover-only" and p["action"] == "first-send"), "personal-wrong-permit-action")

    checkout,common = map(Path,(p["checkout"],p["common_dir"]))
    queue = _path(common)
    require(checkout.is_absolute() and checkout.resolve(strict=True) == checkout, "personal-checkout-not-physical")
    registry = common.parent/".codex-lab/session-copy/runtime/roles.json"
    journal,merge,sender,control = (queue.parent/n for n in
        ("releases.sqlite3","merge.queue.merge-lock","mq.runner.lock","control-upgrade.runner-lock"))
    for k in list(os.environ):
        if k.startswith("GIT_"):
            del os.environ[k]
    os.environ.update(common_port.GIT)
    os.environ["PATH"] = "/opt/homebrew/bin:/usr/bin:/bin"

    def command(argv):
        require(argv[:1] == ["git"] and len(argv) > 1 and argv[1] in {
            "rev-parse","remote","config","merge-tree","commit-tree","show","merge-base",
            "cat-file","status"}, "personal-local-git-command-refused")
        return sh(argv,cwd=str(checkout),git_executable="/usr/bin/git")

    def git(*args):
        r = command(["git",*args])
        require(r.returncode == 0,"personal-local-git-failed")
        return r.stdout

    ports = ProductionPorts(common,command)
    subject = p["subject"]

    def check_checkout():
        require(common_port._identity(common.lstat()) == p["common_identity"] and _path(common) == queue
                and git("rev-parse","--show-toplevel").strip() == str(checkout)
                and git("rev-parse","--path-format=absolute","--git-common-dir").strip() == str(common)
                and checkout_profile(common.parent,git("remote").splitlines(),
                    git("remote","get-url","--all","origin").splitlines(),
                    git("remote","get-url","--push","--all","origin").splitlines()) == "lab"
                and hashlib.sha256(git("config","--local","--null","--list").encode()).hexdigest()
                    == p["git_config_sha256"], "personal-checkout-common-or-origin-moved")

    def approval():
        row = ports.load(70)
        pin = hashlib.sha256(json.dumps(row,sort_keys=True,separators=(",",":"),ensure_ascii=True).encode()).hexdigest()
        require(pin == p["ordinary_approval_sha256"] and row["mode"] == "normal"
                and row["author"] == "one-dev-readonly-lab-successor" and (row["run_id"],row["run_attempt"])==(42769408375,1) and set(row["required_reviews"]) == {"首轮","终审"}
                and all(row[k] == v for k,v in subject.items()), "personal-ordinary-approval-moved")
        return row

    def observe(fd,control_fd,record):
        require(read_permit() == p, "personal-permit-moved")
        check_checkout()
        _first_card_locks(queue,p)
        roles = common_port._json(_read(registry,p["registry_sha256"]))["roles"]
        require(type(roles) is list and len({r["role"] for r in roles}) == len({r["id"] for r in roles}) == len(roles)
                and [r["id"] for r in roles if r["role"] == common_port.ROLE] == [common_port.NATIVE]
                and [r["id"] for r in roles if r["role"] == "one-dev-readonly-lab-successor"]
                    == ["01a1c3c5-3f7c-7941-b2df-1f3f8dc56b0c"]
                and [r["id"] for r in roles if r["role"] == "one-platform"]
                    == ["01a1b63e-1bf8-7ca1-beb7-d0dc25604da8"]
                and [r["id"] for r in roles if r["role"] == "one-test"]
                    == ["01a1b614-934c-7250-a715-5cf09e9c02c2"],
                "personal-formal-registry-moved")
        merge_id = common_port._named(merge)
        require([merge_id["device"],merge_id["inode"]] == p["lock_ids"]["merge.queue.merge-lock"]
                and common_port._named(sender) == common_port._identity(os.fstat(fd)) == p["locks"]["sender"]
                and common_port._named(control) == common_port._identity(os.fstat(control_fd)) == p["control_lock"],
                "personal-coordinator-lock-moved")
        repo = ports.github.repository()
        require(repo.get("private") is False and repo.get("fork") is False and repo.get("default_branch") == "main",
                "personal-wrong-repository-profile")
        present,fifo = _state(queue)
        require(present,"personal-canonical-fifo-missing")
        return dict(kind=KIND, action=p["action"], repo_id=repo["id"],repo=repo["full_name"],
                    ref="refs/heads/main",app_id=common_port.SCOPE["app_id"],
                    installation_id=common_port.SCOPE["installation_id"],common_dir=str(common),
                    journal=common_port._named(journal),locks=dict(merge=merge_id,sender=common_port._named(sender)),
                    closure_sha256=a.closure_sha256,registry_sha256=p["registry_sha256"],registry_adopted=True,
                    subject=subject,now=datetime.now(timezone.utc).isoformat(),coordinator_exclusive=True,
                    conflict=False,fifo=fifo,reservation=None if record is None else {k:record[k] for k in common_port.KEYS})

    with common_port._sender_lock(sender,p["locks"]["sender"]) as fd, \
            common_port._sender_lock(control,p["control_lock"]) as control_fd:
        require(common_port._named(journal) == p["journal"],"personal-wrong-journal")
        ledger = ReleaseJournalV2(journal)
        initial = ledger.load(70)
        recovery = a.command == "recover-only" or initial is not None
        admitted = None
        if not recovery:
            before = observe(fd,control_fd,None)
            require(before["fifo"] in ([],[70]) and
                    authorize(p,dict(before,fifo=[70]),recover_only=False)["allow_reserve"],
                    "personal-pre-admission-authority-refused")

            class Only69Queue(Queue):
                @contextmanager
                def merge_lock(self):
                    with super().merge_lock(expected_identity=p["lock_ids"]["merge.queue.merge-lock"]):
                        require(_state(queue) in ((True,[]),(True,[70])) and ledger.load(70) is None,
                                "personal-admission-conflict")
                        yield

            def verify_admission(intent):
                now = observe(fd,control_fd,None)
                require(authorize(p,dict(now,fifo=[70]),recover_only=False)["allow_reserve"] and
                        git("merge-tree","--write-tree",a.base,a.head).strip() == p["expected_tree"],
                        "personal-admission-tree-or-authority-moved")
                return ports.verify(intent,approval())

            admitted = admit(Only69Queue(str(queue)),subject,verify_admission)
            if admitted["action"] == "hold":
                return dict(admitted,stage="admission")
        require(authorize(p,observe(fd,control_fd,initial),recover_only=recovery)["action"] == "READY",
                "personal-post-admission-or-recovery-refused")

        owned = [None]

        def reserve_guard(proposed):
            require(not recovery and owned[0] is None and ledger.load(70) is None
                    and {k:proposed[k] for k in common_port.KEYS} == subject
                    and authorize(p,observe(fd,control_fd,None),recover_only=False)["allow_reserve"],
                    "personal-new-reservation-refused")

        def recover_guard(current):
            require(current is not None and authorize(p,observe(fd,control_fd,current),
                    recover_only=True)["action"] == "READY", "personal-recovery-scope-moved")

        def can_write():
            record = ledger.load(70)
            observed = observe(fd,control_fd,record)
            decision = authorize(p,observed,recover_only=False)
            return (not recovery and observed["fifo"] == [70] and
                    ((record is None and owned[0] is None and decision["allow_send"]) or
                     (record is not None and record == owned[0] and decision["action"] == "READY")))

        binding = None
        if not recovery:
            require(a.key is not None, "personal-app-key-path-required")
            def transport_check():
                _closure(a.closure,a.closure_sha256)
                return owned[0] is not None and can_write()
            binding = common_port.MainHTTPSBinding(p,checkout,merge,fd,transport_check,a.key)
            ports._main_https = binding
        result = run_command("once",70,common,ports,first_card_scope=p,permit_check=can_write,recover_only=recovery,
                             reserve_guard=reserve_guard,reserved=lambda row:owned.__setitem__(0,dict(row)),
                             recover_guard=recover_guard)
        result.update(admission=admitted,reserved_this_invocation=owned[0] is not None,
                      send_port_entered=bool(binding and binding.spent))
        if binding is not None and binding.transport_hold:
            result["transport_status"] = binding.transport_hold
        return result


def main():
    p = argparse.ArgumentParser(allow_abbrev=False)
    p.add_argument("command", choices=["once","recover-only"])
    for name in ("head","base","permit","permit-sha256","closure","closure-sha256"):
        p.add_argument("--"+name,required=True)
    p.add_argument("--key")
    a = p.parse_args()
    os.umask(0o077)
    try:
        result = run(a)
    except BlockingIOError:
        result = dict(action="hold",reason="personal-coordinator-lock-busy")
    except (OSError,ValueError,TypeError,KeyError,subprocess.SubprocessError) as error:
        result = dict(action="hold",reason=str(error) if type(error) is ValueError else "personal-binding-unavailable")
    print(json.dumps(result,sort_keys=True))
    return 78 if result["action"] == "hold" or result.get("transport_status") else 0


if __name__ == "__main__":
    raise SystemExit(main())
