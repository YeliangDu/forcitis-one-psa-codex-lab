"""Formal first-card authority and the only HTTPS route to main."""
import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import fcntl
import hashlib
import json
import os
from pathlib import Path
import selectors
import stat
import subprocess
import sys
import tempfile

# Python isolated mode omits the script directory. Resolve only this pinned
# package, never a caller-supplied PYTHONPATH.
if __name__ == "__main__":
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    sys.modules["lab_main_https"] = sys.modules[__name__]

from lab_https_transport import GitHTTPS, TrustedBareGit, URL
from ordinary_first_card import _closure, _read, _utc_expiry, digest, require

REPO = "YeliangDu/forcitis-one-psa-codex-lab"
SCOPE = dict(repo_id=1449415666, repo=REPO, ref="refs/heads/main",
             app_id=5684707, installation_id=180252875)
ROLE = "one-dev-readonly-lab"
NATIVE = "01a1c0aa-8c8c-7113-a21c-28f03e8e3541"
KEYS = ("number", "head", "base", "target")
AUTHORITY = (*SCOPE, "common_dir", "journal", "locks", "closure_sha256",
             "registry_sha256", "subject")
KIND = "lab-main-https-first-card-v1"
CONFIG = b"[core]\nrepositoryformatversion = 0\nfilemode = true\nbare = true\n"
GIT = {"PATH":"/usr/bin:/bin", "GIT_CONFIG_NOSYSTEM":"1", "GIT_CONFIG_GLOBAL":"/dev/null",
       "GIT_NO_REPLACE_OBJECTS":"1", "GIT_TERMINAL_PROMPT":"0", "LANG":"C"}


def _id(row):
    return (type(row) is dict and set(row) == {"device", "inode"}
            and all(type(value) is int and value > 0 for value in row.values()))


def authorize(permit, observed, *, recover_only, number=69):
    """Pure decision; observations are gathered by the launcher, never by CLI input."""
    hold = dict(action="HOLD", allow_reserve=False, allow_send=False)
    try:
        subject = permit["subject"]
        require(type(recover_only) is bool and type(permit) is type(observed) is dict
                and all(type(permit[k]) is type(v) and permit[k] == v for k, v in SCOPE.items())
                and all(type(observed[k]) is type(permit[k]) and observed[k] == permit[k] for k in AUTHORITY)
                and type(subject) is dict and set(subject) == set(KEYS)
                and type(number) is int and number in (70,69) and type(subject["number"]) is int
                and subject["number"] == number and subject["target"] == "main"
                and all(digest(subject[k],40) for k in ("head","base"))
                and type(permit["common_dir"]) is str and Path(permit["common_dir"]).is_absolute()
                and _id(permit["journal"]) and _id(observed["journal"])
                and type(permit["locks"]) is dict and set(permit["locks"]) == {"merge","sender"}
                and all(_id(x) for group in (permit, observed) for x in group["locks"].values())
                and type(observed["subject"]["number"]) is int
                and all(digest(permit[k],64) for k in ("closure_sha256","registry_sha256"))
                and observed["registry_adopted"] is True
                and _utc_expiry(observed["now"]) < _utc_expiry(permit["expires_at"]),
                "main-authority-mismatch")
        reserved = observed["reservation"]
        if reserved is not None:
            require(type(reserved) is dict and type(reserved["number"]) is int and reserved == subject,
                    "main-reservation-mismatch")
            return dict(action="READY", allow_reserve=False, allow_send=False)
        if not recover_only:
            return dict(action="READY", allow_reserve=True, allow_send=True)
    except (ValueError, TypeError, KeyError, OverflowError):
        pass
    return hold


def _identity(st):
    return dict(device=st.st_dev, inode=st.st_ino)


def _named(path):
    st = path.lstat()
    require(path.resolve(strict=True) == path and stat.S_ISREG(st.st_mode)
            and st.st_uid == os.geteuid() and st.st_nlink == 1 and not st.st_mode & 0o022,
            "main-storage-identity-untrusted")
    return _identity(st)


@contextmanager
def _sender_lock(path, expected):
    fd = os.open(path, os.O_RDWR | os.O_NOFOLLOW | os.O_NONBLOCK)
    try:
        require(_identity(os.fstat(fd)) == _named(path) == expected, "main-sender-lock-moved")
        fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        yield fd
    finally:
        # No explicit unlock: a still-live sender descendant retains the lock.
        os.close(fd)


def _json(raw):
    def pairs(rows):
        require(len({k for k, _ in rows}) == len(rows), "main-duplicate-json-key")
        return dict(rows)
    return json.loads(raw, object_pairs_hook=pairs)


def _rules(github, permit, admin_snapshot):
    # Exact two rulesets. Missing data, extra bypass actors/rules, or scope drift holds.
    pair = permit["rulesets"]
    wanted = {"update": {"update"}, "immutable": {"deletion","non_fast_forward"}}
    require(type(admin_snapshot) is dict and set(admin_snapshot) == set(wanted),
            "main-administrator-rule-receipt-unavailable")
    for name, types in wanted.items():
        row = admin_snapshot[name]
        live = github._single("rulesets/" + str(pair[name]))
        # GitHub redacts bypass_actors from non-writers. The independent
        # root pin attests the administrator's exact response; the live GET
        # must show the same public identity, revision and effective rules.
        public = ("id","node_id","name","source_type","source","target",
                  "enforcement","conditions","rules","updated_at")
        require(all(k in row and k in live and row[k]==live[k] for k in public)
                and type(row["node_id"]) is str and bool(row["node_id"])
                and isinstance(_utc_expiry(row["updated_at"]),datetime)
                and ("bypass_actors" not in live or live["bypass_actors"]==row.get("bypass_actors")),
                "main-admin-ruleset-snapshot-stale")
        actors = ([{"actor_id":SCOPE["app_id"],"actor_type":"Integration","bypass_mode":"always"}]
                  if name == "update" else [])
        rules = row.get("rules")
        require(row.get("id") == pair[name] and row.get("source_type") == "Repository"
                and row.get("source") == REPO and row.get("target") == "branch"
                and row.get("enforcement") == "active" and row.get("bypass_actors") == actors
                and row.get("conditions") == {"ref_name":{"include":[SCOPE["ref"]],"exclude":[]}}
                and type(rules) is list and len(rules) == len(types)
                and {rule.get("type") for rule in rules} == types
                and all(rule.get("parameters") in (None, {}, {"update_allows_fetch_and_merge":False})
                        and rule.get("parameters",{}).get("update_allows_fetch_and_merge",False) is False
                        for rule in rules if rule.get("parameters") is not None),
                "main-ruleset-mismatch")
    rows = github.main_rules()
    require(type(rows) is list and len(rows) == 3
            and all(r.get("ruleset_source_type") == "Repository" and r.get("ruleset_source") == REPO for r in rows)
            and {(r.get("ruleset_id"),r.get("type")) for r in rows} ==
                {(pair[n], t) for n, ts in wanted.items() for t in ts},
            "main-effective-rules-mismatch")


def _local_git(root, home, *args):
    p = subprocess.run(["/usr/bin/git", "-c", "core.hooksPath=/dev/null", *args],
                       cwd=root, env=dict(GIT, HOME=str(home)), stdin=subprocess.DEVNULL,
                       capture_output=True, timeout=30)
    require(p.returncode == 0, "main-local-object-command-failed")
    return p.stdout


@contextmanager
def _broker(root, slot, key):
    sock = slot / "auth.sock"
    with subprocess.Popen([sys.executable,"-I","-B",str(root/"lab_app_broker.py"),
                           "--app-id",str(SCOPE["app_id"]),"--key",key,"--socket",str(sock)],
                          env=dict(GIT,HOME=str(slot)), stdin=subprocess.DEVNULL,
                          stdout=subprocess.PIPE, stderr=subprocess.DEVNULL) as p:
        ready = False
        try:
            with selectors.DefaultSelector() as selector:
                selector.register(p.stdout, selectors.EVENT_READ)
                require(bool(selector.select(timeout=100)), "main-app-startup-timeout")
                row = _json(p.stdout.readline(8193))
            ready = row.get("stage") == "ready"
            require(ready and p.poll() is None and
                    all(type(row.get(k)) is int and row[k] == SCOPE[k] for k in ("app_id","installation_id","repo_id"))
                    and row.get("selection") == "selected" and row.get("installed_repositories") == 1
                    and row.get("permissions") == {"contents":"write","metadata":"read"}
                    and datetime.now(timezone.utc) < _utc_expiry(row["token_expires_at"]),
                    "main-app-installation-mismatch")
            yield str(sock), p
        finally:
            if p.poll() is None:
                p.terminate()
            try:
                rest, _ = p.communicate(timeout=25)
            except subprocess.TimeoutExpired:
                p.kill()
                p.communicate()
                raise ValueError("main-token-revocation-unverified") from None
            if ready:
                require(p.returncode == 0 and b'{"stage":"token-revoked"}' in rest.splitlines(),
                        "main-token-revocation-unverified")


class MainHTTPSBinding:
    """The existing writer owns the one reservation; this port only transmits it."""
    def __init__(self, permit, checkout, merge_lock, sender_fd, check, key):
        self.permit, self.checkout, self.merge_lock = permit, checkout, merge_lock
        self.sender_fd, self.check, self.key = sender_fd, check, key
        self.spent, self.transport_hold = False, None

    def __call__(self, args, permit_guard):
        from mq_queue import merge_transport_fds
        s = self.permit["subject"]
        base, head, ref = s["base"], s["head"], SCOPE["ref"]
        lease = "--force-with-lease=" + ref + ":" + base
        obj = args[-1].removesuffix(":" + ref)
        require(not self.spent and digest(obj,40) and obj != base
                and args == ["push","--porcelain",lease,"origin",obj+":"+ref]
                and permit_guard() is True, "main-https-request-mismatch")
        self.spent = True
        fds = merge_transport_fds(["git","push"])
        require(len(fds) == 1 and self.sender_fd != fds[0], "main-https-live-lease-missing")
        module = Path(__file__).resolve().parent
        try:
            with tempfile.TemporaryDirectory(prefix="lab-main-", dir="/private/tmp") as directory:
                slot = Path(directory)
                bare, home, creds = (slot/n for n in ("bare","home","creds"))
                for path in (bare, home, creds):
                    path.mkdir(mode=0o700)
                _local_git(bare, home, "init","--bare","--template=")
                (bare/"config").write_bytes(CONFIG)
                _local_git(bare, home, "-c","protocol.allow=never","-c","protocol.file.allow=always",
                           "fetch","--no-tags","--no-write-fetch-head",str(self.checkout),obj)
                check = _local_git(bare, home, "show","-s","--format=%P%n%T",obj).decode().splitlines()
                require(check == [base+" "+head,self.permit["expected_tree"]], "main-https-merge-object-mismatch")
                helper = creds/"askpass"
                helper.write_bytes((module/"lab_app_askpass.py").read_bytes())
                helper.chmod(0o700)
                helper_sha = hashlib.sha256(helper.read_bytes()).hexdigest()
                require(self.check() is True and permit_guard() is True, "main-permit-moved-before-app")
                runner = TrustedBareGit(str(bare),hashlib.sha256(CONFIG).hexdigest(),str(home))
                lock = dict(self.permit["locks"]["merge"], path=str(self.merge_lock))
                with _broker(module, creds, self.key) as (sock, process):
                    def run(argv, *, env, pass_fds):
                        require(argv == ["git","push",lease,URL,obj+":"+ref] and pass_fds == fds,
                                "main-https-private-argv-mismatch")
                        return runner._run_scoped(argv,env=env,pass_fds=(*pass_fds,self.sender_fd))
                    # Do not instantiate/relax the isolated ref constructor.
                    result = GitHTTPS._push_bound(
                        dict(ref=ref,expected=base,head=obj,lock=lock,
                             askpass=dict(path=str(helper),sha256=helper_sha)),sock,run,
                        [lease,URL,obj+":"+ref],
                        lambda: self.check() is True and permit_guard() is True and process.poll() is None,
                        fds[0])
                require(result == {"action":"issued"}, "main-send-unknown-recover-only")
        except (ValueError, OSError, subprocess.SubprocessError):
            self.transport_hold = "main-transport-or-token-finalization-needs-readback"
            raise
        return ""

ANCHOR = Path("/Library/Application Support/ForcitisLab/Main69/authority.json")


def _trust_anchor():
    for path in (*reversed(ANCHOR.parents), ANCHOR):
        row = path.lstat()
        require(path.resolve(strict=True) == path and row.st_uid == 0 and not row.st_mode & 0o022
                and (stat.S_ISREG(row.st_mode) and row.st_nlink == 1 if path == ANCHOR
                     else stat.S_ISDIR(row.st_mode)), "main-independent-anchor-untrusted")
    fd = os.open(ANCHOR, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(fd, "rb") as stream:
        before = os.fstat(stream.fileno())
        raw = stream.read(16385)
        after = ANCHOR.lstat()
        require(0 < len(raw) == before.st_size <= 16384 and
                all(getattr(before,k)==getattr(after,k) for k in
                    ("st_uid","st_mode","st_nlink","st_dev","st_ino","st_size","st_mtime_ns","st_ctime_ns")),
                "main-anchor-moved")
    value = _json(raw)
    require(set(value) == {"kind","enabled","service_uid","permit_path","permit_sha256","closure_path",
                            "closure_sha256","key_path","registry_adoption","old_sender_exclusion",
                            "ruleset_proof_path","ruleset_proof_sha256"}
            and value["kind"] == KIND and value["enabled"] is True
            and type(value["service_uid"]) is int and value["service_uid"] == os.geteuid() > 0
            and all(digest(value[k],64) for k in ("permit_sha256","closure_sha256",
                                                   "registry_adoption","old_sender_exclusion","ruleset_proof_sha256")),
            "main-independent-anchor-unavailable")
    return value


def _pins(permit):
    extra = {"kind","expires_at","checkout","common_identity","rulesets","lock_ids",
             "git_config_sha256","ordinary_approval_sha256","expected_tree","registry_adoption"}
    require(set(permit) == set(AUTHORITY) | extra and permit["kind"] == KIND
            and _id(permit["common_identity"])
            and digest(permit["expected_tree"],40)
            and all(digest(permit[k],64) for k in ("git_config_sha256","ordinary_approval_sha256","registry_adoption"))
            and set(permit["rulesets"]) == {"update","immutable"}
            and len(set(permit["rulesets"].values())) == 2
            and all(type(v) is int and v > 0 for v in permit["rulesets"].values())
            and set(permit["lock_ids"]) == {"merge.queue.merge-lock","merge.queue.lock","merge.queue.heads-lock"}
            and all(type(x) is list and len(x)==2 and all(type(i) is int and i>0 for i in x)
                    for x in permit["lock_ids"].values()), "main-permit-shape")


def _run(args):
    from mq_github import sh
    from mq_lab import _path, _state, checkout_profile
    from mq_lab_journal_v2 import ReleaseJournalV2
    from mq_lab_operator import ProductionPorts, _first_card_locks, run_command

    anchor = _trust_anchor()
    permit_path, pin = anchor["permit_path"], anchor["permit_sha256"]
    permit = _json(_read(permit_path, pin, private=True))
    _pins(permit)
    require(permit["closure_sha256"] == anchor["closure_sha256"] and
            permit["registry_adoption"] == anchor["registry_adoption"], "main-anchor-binding-mismatch")
    require(args.number == 69 and permit["subject"] == dict(number=args.number, head=args.head,
                                                             base=args.base, target="main"), "main-request-scope")
    package = _closure(anchor["closure_path"],anchor["closure_sha256"],trusted_uid=0)
    require(package/"controller/scripts/merge/lab_main_https.py" == Path(__file__).resolve(), "main-entry-unbound")
    checkout, common = map(Path, (permit["checkout"],permit["common_dir"]))
    require(checkout == common.parent and checkout.resolve(strict=True) == checkout,
            "main-checkout-not-physical-canonical")
    queue = _path(common)
    journal, merge, sender = (queue.parent/n for n in
                               ("releases.sqlite3","merge.queue.merge-lock","mq.runner.lock"))
    registry = checkout/".codex-lab/session-copy/runtime/roles.json"
    for key in list(os.environ):
        if key.startswith("GIT_"):
            del os.environ[key]
    os.environ.update(GIT)
    # Fixed root-administered readback executable; never resolve gh in the
    # old developer's writable Homebrew tree or inherit an API write token.
    cli = ANCHOR.parent/"bin"/"gh"
    for f in (cli.parent,cli):
        info = f.lstat()
        require(f.resolve(strict=True)==f and info.st_uid==0 and not info.st_mode&0o022
                and (stat.S_ISREG(info.st_mode) and info.st_nlink==1 and bool(info.st_mode&0o111)
                     if f==cli else stat.S_ISDIR(info.st_mode)), "main-readback-cli-untrusted")
    for key in ("GH_TOKEN","GITHUB_TOKEN","GH_ENTERPRISE_TOKEN","GITHUB_ENTERPRISE_TOKEN",
                "GH_CONFIG_DIR","GH_HOST","GH_REPO","GH_DEBUG"):
        os.environ.pop(key,None)
    os.environ["PATH"] = str(cli.parent) + ":/usr/bin:/bin"

    def command(argv):
        require(argv[:1] == ["git"] and len(argv) > 1 and argv[1] in {
            "rev-parse","remote","config","merge-tree","commit-tree","show","merge-base","cat-file",
            "diff","diff-tree","ls-tree","log","status"}, "main-local-git-command-outside-scope")
        # The sole network write is the typed HTTPS port, never this command.
        return sh(argv,cwd=str(checkout),git_executable="/usr/bin/git")

    def git(*argv):
        r = command(["git",*argv])
        require(r.returncode == 0,"main-canonical-git-read-failed")
        return r.stdout

    def check_checkout():
        require(_identity(common.lstat()) == permit["common_identity"] and _path(common) == queue
                and git("rev-parse","--show-toplevel").strip() == str(checkout)
                and git("rev-parse","--path-format=absolute","--git-common-dir").strip() == str(common)
                and checkout_profile(common.parent,git("remote").splitlines(),
                                     git("remote","get-url","--all","origin").splitlines(),
                                     git("remote","get-url","--push","--all","origin").splitlines()) == "lab"
                and hashlib.sha256(git("config","--local","--null","--list").encode()).hexdigest() ==
                     permit["git_config_sha256"], "main-canonical-checkout-or-config-moved")

    check_checkout()
    ports = ProductionPorts(common,command)
    subject = permit["subject"]

    def read_rules():
        raw = _read(anchor["ruleset_proof_path"],anchor["ruleset_proof_sha256"],trusted_uid=0)
        _rules(ports.github,permit,_json(raw))

    def observe(fd, record):
        require(_trust_anchor() == anchor and _json(_read(permit_path,pin,private=True)) == permit,
                "main-anchored-authorization-moved")
        check_checkout()
        _first_card_locks(queue,permit)
        roles = _json(_read(registry,permit["registry_sha256"]))["roles"]
        require(type(roles) is list and all(type(r) is dict and type(r.get("role")) is str
                                            and type(r.get("id")) is str for r in roles)
                and len({r["role"] for r in roles}) == len({r["id"] for r in roles}) == len(roles)
                and [r["id"] for r in roles if r["role"] == ROLE] == [NATIVE],
                "main-formal-role-mapping-missing")
        merge_id = _named(merge)
        require(_named(sender) == _identity(os.fstat(fd))
                and [merge_id["device"],merge_id["inode"]] == permit["lock_ids"]["merge.queue.merge-lock"],
                "main-queue-lease-identity-moved")
        repo = ports.github.repository()
        require(repo.get("private") is False and repo.get("fork") is False and repo.get("default_branch") == "main",
                "main-repository-profile-mismatch")
        return dict(repo_id=repo["id"],repo=repo["full_name"],ref="refs/heads/main",
                    app_id=SCOPE["app_id"],installation_id=SCOPE["installation_id"],common_dir=str(common),
                    journal=_named(journal),locks=dict(merge=merge_id,sender=_named(sender)),
                    closure_sha256=anchor["closure_sha256"],registry_sha256=permit["registry_sha256"],
                    registry_adopted=True,subject=subject,now=datetime.now(timezone.utc).isoformat(),
                    reservation=None if record is None else {k:record[k] for k in KEYS})

    with _sender_lock(sender,permit["locks"]["sender"]) as fd:
        require(_named(journal) == permit["journal"], "main-journal-identity-mismatch")
        ledger = ReleaseJournalV2(journal)
        record = ledger.load(args.number)
        verdict = authorize(permit,observe(fd,record),recover_only=args.recover_only)
        require(verdict["action"] == "READY", "main-authority-or-reservation-refused")
        # Existing records can only enter the recovery branch even if invoked with once.
        recovery = not verdict["allow_send"]
        owned = [None]

        def reserve_guard(proposed):
            current = ledger.load(args.number)
            present, items = _state(queue)
            require(not recovery and present and items == [69] and current is None
                    and {k:proposed[k] for k in KEYS} == subject and owned[0] is None
                    and authorize(permit,observe(fd,None),recover_only=False)["allow_reserve"],
                    "main-new-reservation-refused")
            read_rules()

        def mark_reserved(proposed):
            owned[0] = dict(proposed)

        def recover_guard(current):
            require(current is not None and
                    authorize(permit,observe(fd,current),recover_only=True)["action"] == "READY",
                    "main-recovery-binding-refused")

        def can_write():
            current = ledger.load(args.number)
            return (not recovery and _state(queue) == (True,[69])
                    and ((current is None and owned[0] is None and
                          authorize(permit,observe(fd,None),recover_only=False)["allow_send"])
                         or (owned[0] is not None and current == owned[0] and
                             authorize(permit,observe(fd,current),recover_only=True)["action"] == "READY")))

        binding = None
        if not recovery:
            def transport_check():
                _closure(anchor["closure_path"],anchor["closure_sha256"],trusted_uid=0)
                read_rules()
                return can_write() is True and owned[0] is not None
            binding = MainHTTPSBinding(permit,checkout,merge,fd,transport_check,anchor["key_path"])
            ports._main_https = binding
        result = run_command("once",args.number,common,ports,first_card_scope=permit,
                             permit_check=can_write,recover_only=recovery,
                             reserve_guard=reserve_guard,reserved=mark_reserved,recover_guard=recover_guard)
        if binding is not None and binding.transport_hold:
            result["transport_status"] = binding.transport_hold
        return result


def main():
    p = argparse.ArgumentParser(allow_abbrev=False)
    p.add_argument("command",choices=["once","recover-only"])
    p.add_argument("number",type=int)
    p.add_argument("--head",required=True)
    p.add_argument("--base",required=True)
    a = p.parse_args()
    a.recover_only = a.command == "recover-only"
    os.umask(0o077)
    try:
        result = _run(a)
    except (ValueError,OSError,TypeError,KeyError,subprocess.SubprocessError) as e:
        result = {"action":"hold","reason":str(e) if type(e) is ValueError else "main-binding-unavailable"}
    print(json.dumps(result,sort_keys=True))
    return 78 if result["action"] == "hold" or result.get("transport_status") else 0


if __name__ == "__main__":
    raise SystemExit(main())
