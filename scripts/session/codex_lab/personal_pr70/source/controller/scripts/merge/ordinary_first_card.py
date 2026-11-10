"""Explicit, externally pinned #69 operator entry; no default permit or transport."""
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import re
import stat
from datetime import datetime, timedelta, timezone

FIELDS = ("number", "head", "base", "target")
REPO_ID = 1449415666
LOCKS = ("merge.queue.merge-lock", "merge.queue.lock", "merge.queue.heads-lock")


def require(ok, reason):
    if not ok:
        raise ValueError(reason)


def digest(value, n):
    return type(value) is str and re.fullmatch(f"[0-9a-f]{{{n}}}", value) is not None


def authorize(request, approval):
    """Pure scope check; verification must originate in the pinned file loader."""
    require(type(request) is dict and set(request) == set(FIELDS) | {"command"}
            and request["command"] == "once" and type(request["number"]) is int
            and request["number"] == 69 and request["target"] == "main"
            and all(digest(request[k], 40) for k in ("head", "base")), "first-card-request-scope")
    subject = {k: request[k] for k in FIELDS}
    require(type(approval) is dict and approval.get("kind") == "ordinary-first-card"
            and approval.get("subject") == subject
            and type(approval.get("subject", {}).get("number")) is int, "first-card-approval-scope")
    v = approval.get("verification")
    require(type(v) is dict and set(v) == {"source", "verified"}
            and v.get("source") == "installed-local-authorization" and v.get("verified") is True,
            "first-card-authorization-not-verified")
    return subject


def _read(path, expected, private=False, *, trusted_uid=None):
    path = Path(path)
    owner = os.geteuid() if trusted_uid is None else trusted_uid
    require(path.is_absolute() and path.resolve(strict=True) == path and digest(expected, 64), "first-card-physical-pin-required")
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(fd, "rb") as stream:
        st = os.fstat(stream.fileno())
        require(stat.S_ISREG(st.st_mode) and st.st_nlink == 1 and st.st_uid == owner
                and not st.st_mode & 0o022 and (not private or not st.st_mode & 0o077) and 0 < st.st_size <= 1024*1024,
                "first-card-file-not-trusted")
        data = stream.read(1024*1024+1)
        named = path.lstat()
        require((st.st_dev, st.st_ino) == (named.st_dev, named.st_ino)
                and hashlib.sha256(data).hexdigest() == expected, "first-card-pinned-file-drift")
    return data


def _closure(path, expected, *, trusted_uid=None):
    root = Path(__file__).resolve().parents[3]
    require(Path(path) == root / "SOURCE-CLOSURE.json", "first-card-installation-root-mismatch")
    manifest = json.loads(_read(path, expected, trusted_uid=trusted_uid))
    files = manifest["files_sha256"]
    entry = "controller/scripts/merge/ordinary_first_card.py"
    require(manifest["entry"] == entry and entry in files and root/entry == Path(__file__).resolve(),
            "first-card-entry-unbound")
    require(not any(p.is_symlink() for p in root.rglob("*")), "first-card-closure-symlink")
    require({p.relative_to(root).as_posix() for p in root.rglob("*") if p.is_file()} == set(files)|{"SOURCE-CLOSURE.json"},
            "first-card-closure-inventory-drift")
    if trusted_uid is not None:
        for directory in (*root.parents, root, *(p for p in root.rglob("*") if p.is_dir())):
            st = directory.lstat()
            require(stat.S_ISDIR(st.st_mode) and st.st_uid == trusted_uid and not st.st_mode & 0o022,
                    "first-card-closure-directory-not-trusted")
    for n, h in files.items():
        f = root/n
        require(f.is_relative_to(root) and ".." not in Path(n).parts, "first-card-closure-path")
        _read(f, h, trusted_uid=trusted_uid)
    return root


def _utc_expiry(value):
    require(type(value) is str and re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\.[0-9]{1,6})?(?:Z|\+00:00)",value),
            "first-card-expiry-must-be-rfc3339-utc")
    return datetime.fromisoformat(value)


def send_permit_guard(permit, reread, clock):
    """Recheck identical pinned authority and time at the actual Git boundary."""
    frozen = copy.deepcopy(permit)
    def check():
        try:
            installed = reread()
            current = clock()
            return (type(installed) is dict and installed == frozen and isinstance(current, datetime)
                    and current.utcoffset() == timedelta(0) and current < _utc_expiry(frozen["expires_at"]))
        except Exception:
            return False
    return check


def _permit(path, pin, closure_pin, request, *, now=None, recover_only=False):
    permit = json.loads(_read(path, pin, private=True))
    required = {"kind", "repo_id", "subject", "checkout", "common_dir", "closure_sha256", "expires_at",
                "ordinary_approval_sha256", "expected_tree", "lock_ids", "git_config_sha256"}
    require(type(permit) is dict and set(permit) == required and permit["repo_id"] == REPO_ID
            and type(permit["repo_id"]) is int and permit["closure_sha256"] == closure_pin
            and digest(permit["ordinary_approval_sha256"], 64) and digest(permit["git_config_sha256"], 64)
            and digest(permit["expected_tree"], 40), "first-card-permit-shape")
    now = now or datetime.now(timezone.utc)
    expiry = _utc_expiry(permit["expires_at"])
    require(recover_only is True or now < expiry, "first-card-permit-expired")
    intent = authorize(request, dict(kind=permit["kind"], subject=permit["subject"],
                       verification={"source":"installed-local-authorization","verified":True}))
    require(type(permit["lock_ids"]) is dict and set(permit["lock_ids"]) == set(LOCKS)
            and all(type(x) is list and len(x)==2 and all(type(i) is int and i>0 for i in x)
                    for x in permit["lock_ids"].values()), "first-card-lock-pins-required")
    return permit, intent


def main():
    p = argparse.ArgumentParser(allow_abbrev=False)
    p.add_argument("command", choices=["once"])
    p.add_argument("number", type=int)
    p.add_argument("--recover-only", action="store_true", help="Process only an existing exact reservation/confirmation; never send")
    for name in ("head", "base", "permit", "permit-sha256", "closure", "closure-sha256"):
        p.add_argument("--"+name, required=True)
    a=p.parse_args()
    try:
        _closure(a.closure,a.closure_sha256)
        request=dict(command=a.command,number=a.number,head=a.head,base=a.base,target="main")
        def pinned_permit():
            return _permit(a.permit,a.permit_sha256,a.closure_sha256,request,recover_only=a.recover_only)[0]
        permit=pinned_permit()
        intent=permit["subject"]
        guard=send_permit_guard(permit,pinned_permit,lambda: datetime.now(timezone.utc))
        from mq_github import sh
        from mq_lab import ORIGIN, checkout_profile
        from mq_lab_operator import ProductionPorts, run_command
        checkout,common=map(Path,(permit["checkout"],permit["common_dir"]))
        require(checkout.resolve(strict=True)==checkout and common.resolve(strict=True)==common,
                "first-card-checkout-not-physical")
        for key in list(os.environ):
            if key.startswith("GIT_"):
                del os.environ[key]
        os.environ.update(PATH="/opt/homebrew/bin:/usr/bin:/bin",GIT_CONFIG_NOSYSTEM="1",
                          GIT_CONFIG_GLOBAL="/dev/null",GIT_NO_REPLACE_OBJECTS="1",GIT_TERMINAL_PROMPT="0")
        def command(argv):
            return sh(argv,cwd=str(checkout),git_executable="/usr/bin/git" if argv[:1]==["git"] else None)
        def git(*args):
            r=command(["git",*args]);require(r.returncode==0,"first-card-checkout-read-failed");return r.stdout.strip()
        require(git("rev-parse","--show-toplevel")==str(checkout)
                and git("rev-parse","--path-format=absolute","--git-common-dir")==str(common)
                and checkout_profile(common.parent,git("remote").splitlines(),
                    git("remote","get-url","--all","origin").splitlines(),
                    git("remote","get-url","--push","--all","origin").splitlines())=="lab",
                "first-card-checkout-or-origin-moved")
        config=command(["git","config","--local","--null","--list"])
        require(config.returncode==0 and hashlib.sha256(config.stdout.encode()).hexdigest()==permit["git_config_sha256"],
                "first-card-git-configuration-moved")
        result=run_command("once",intent["number"],common,ProductionPorts(common,command),
                           first_card_scope=permit,permit_check=guard,recover_only=a.recover_only)
    except (ValueError,OSError,TypeError,KeyError) as e:
        result={"action":"hold","reason":str(e) if type(e) is ValueError else "first-card-binding-unavailable"}
    print(json.dumps(result,ensure_ascii=False,sort_keys=True))
    return 78 if result["action"]=="hold" else 0


if __name__=="__main__":
    raise SystemExit(main())
