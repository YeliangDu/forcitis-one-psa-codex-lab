"""Explicit isolated Git HTTPS argv/FD adapter; never a reservation or writer."""
import copy
import fcntl
import hashlib
import os
from pathlib import Path
import re
import stat
import subprocess

REPO = "YeliangDu/forcitis-one-psa-codex-lab"
URL = "https://github.com/" + REPO + ".git"
OID = re.compile(r"[a-f0-9]{40}")
REF = re.compile(r"refs/heads/codex/(?:https-adapter-probe|lab69-apphttps-[a-f0-9]{12}-(?:target|control))")


def require(ok, reason):
    if not ok:
        raise ValueError(reason)


def physical(value, check, mode=0o077):
    require(type(value) is str, "HTTPS_PHYSICAL_PATH_REQUIRED")
    p = Path(value)
    require(p.is_absolute() and p.resolve(strict=True) == p, "HTTPS_PHYSICAL_PATH_REQUIRED")
    st = p.lstat()
    require(check(st.st_mode) and st.st_uid == os.geteuid() and not st.st_mode & mode,
            "HTTPS_PRIVATE_RESOURCE_REQUIRED")
    return p, st


class GitHTTPS:
    def __init__(self, scope, credential_socket, runner):
        require(type(scope) is dict and set(scope) == {"repository","url","ref","expected","head","lock","askpass"},
                "HTTPS_SCOPE_SHAPE")
        self.scope = copy.deepcopy(scope)
        s = self.scope
        require(s["repository"] == REPO and s["url"] == URL and type(s["ref"]) is str and REF.fullmatch(s["ref"])
                and all(type(s[k]) is str and OID.fullmatch(s[k]) for k in ("expected","head"))
                and s["expected"] != s["head"] and callable(runner), "HTTPS_ISOLATED_EXACT_SCOPE_REQUIRED")
        self.sock, self.runner = credential_socket, runner

    def push(self, args, permit_guard, lease_fd):
        return self._push_bound(self.scope, self.sock, self.runner, args, permit_guard, lease_fd)

    @staticmethod
    def _push_bound(s, credential_socket, runner, args, permit_guard, lease_fd):
        ref = s["ref"]
        expected = ["--force-with-lease=" + ref + ":" + s["expected"], URL, s["head"] + ":" + ref]
        require(type(args) is list and args == expected and callable(permit_guard)
                and type(lease_fd) is int and lease_fd >= 3, "HTTPS_EXACT_SINGLE_REFSPEC_REQUIRED")
        def binding():
            l, a = s["lock"], s["askpass"]
            require(type(l) is dict and set(l) == {"path","device","inode"}
                    and all(type(l[k]) is int for k in ("device","inode"))
                    and type(a) is dict and set(a) == {"path","sha256"}
                    and type(a["sha256"]) is str and re.fullmatch(r"[a-f0-9]{64}",a["sha256"]), "HTTPS_LOCK_AND_HELPER_SCOPE_REQUIRED")
            lock, named = physical(l["path"], stat.S_ISREG)
            held = os.fstat(lease_fd)
            want = (l["device"], l["inode"])
            require((named.st_dev,named.st_ino) == (held.st_dev,held.st_ino) == want
                    and named.st_nlink == held.st_nlink == 1, "HTTPS_CANONICAL_LOCK_FD_MOVED")
            probe = os.open(lock, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
            try:
                require((os.fstat(probe).st_dev,os.fstat(probe).st_ino)==want,"HTTPS_LOCK_PROBE_MOVED")
                try: fcntl.flock(probe,fcntl.LOCK_EX|fcntl.LOCK_NB)
                except BlockingIOError: pass
                else: raise ValueError("HTTPS_NO_HELD_EXCLUSION")
            finally: os.close(probe)
            helper, info = physical(a["path"], stat.S_ISREG)
            require(info.st_nlink == 1 and 0 < info.st_size <= 65536 and info.st_mode & 0o100, "HTTPS_ASKPASS_MODE")
            fd=os.open(helper,os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK)
            with os.fdopen(fd,"rb") as f:
                st=os.fstat(f.fileno())
                require((st.st_dev,st.st_ino)==(info.st_dev,info.st_ino) and
                        hashlib.sha256(f.read(65537)).hexdigest()==a["sha256"], "HTTPS_ASKPASS_PIN_MISMATCH")
            sock, _ = physical(credential_socket, stat.S_ISSOCK)
            physical(str(sock.parent),stat.S_ISDIR)
            return str(helper),str(sock)
        try:
            helper,sock=binding()
            require(permit_guard() is True,"HTTPS_FINAL_PERMIT_REFUSED")
            require(binding()==(helper,sock),"HTTPS_BINDING_MOVED_DURING_PERMIT")
        except (RuntimeError,OSError,TypeError,ValueError) as e:
            raise ValueError("HTTPS_PRE_SEND_REFUSED") from e
        env={"PATH":"/usr/bin:/bin","GIT_TERMINAL_PROMPT":"0",
             "GIT_ASKPASS":helper,"LAB_CREDENTIAL_SOCKET":sock}
        result=runner(["git","push",*expected],env=env,pass_fds=(lease_fd,))
        return {"action":"issued" if result.get("returncode")==0 else "unknown"}


class TrustedBareGit:
    """Real isolated runner: verified bare config, no ambient credential/config."""
    def __init__(self, root, config_sha256, empty_home):
        self.root,_=physical(root,stat.S_ISDIR)
        self.home,_=physical(empty_home,stat.S_ISDIR)
        require(not any(self.home.iterdir()),"HTTPS_EMPTY_HOME_REQUIRED")
        self.pin=config_sha256
        self.last=None
        self._config()
    def _config(self):
        cfg,st=physical(str(self.root/"config"),stat.S_ISREG,0o022)
        require(st.st_nlink==1 and 0<st.st_size<4096,"HTTPS_BARE_CONFIG_SHAPE")
        raw=cfg.read_bytes()
        require(hashlib.sha256(raw).hexdigest()==self.pin,"HTTPS_BARE_CONFIG_PIN")
        body=raw.decode("ascii").splitlines()
        require(body and body[0].strip().lower()=="[core]","HTTPS_BARE_CONFIG_CORE_ONLY")
        keys={}
        for line in body[1:]:
            m=re.fullmatch(r"\s*(repositoryformatversion|filemode|bare|logallrefupdates)\s*=\s*(0|true|false)\s*",line.lower())
            require(m is not None and m[1] not in keys,"HTTPS_BARE_CONFIG_UNSAFE_KEY")
            keys[m[1]]=m[2]
        require(keys.get("repositoryformatversion")=="0" and keys.get("bare")=="true","HTTPS_BARE_ONLY")
    def __call__(self,argv,*,env,pass_fds):
        require(type(argv) is list and argv[:2]==["git","push"] and len(argv)==5 and argv[-2]==URL
                and set(env)=={"PATH","GIT_TERMINAL_PROMPT","GIT_ASKPASS","LAB_CREDENTIAL_SOCKET"}
                and type(pass_fds) is tuple and len(pass_fds)==1,"HTTPS_RUNNER_ONLY_CONTROLLED_PUSH")
        lease=argv[2].removeprefix("--force-with-lease=")
        ref,sep,expected=lease.rpartition(":")
        obj,sep2,dst=argv[-1].partition(":")
        require(argv[2].startswith("--force-with-lease=") and sep and sep2 and REF.fullmatch(ref)
                and OID.fullmatch(expected) and dst==ref and (not obj or OID.fullmatch(obj)),
                "HTTPS_RUNNER_REF_AND_LEASE_REQUIRED")
        return self._run_scoped(argv, env=env, pass_fds=pass_fds)

    def _run_scoped(self, argv, *, env, pass_fds):
        self._config()
        context=dict(env,HOME=str(self.home),GIT_CONFIG_NOSYSTEM="1",GIT_CONFIG_GLOBAL="/dev/null",
                     GIT_CONFIG_SYSTEM="/dev/null",GIT_NO_REPLACE_OBJECTS="1",LANG="C")
        flags=["-c","credential.helper=","-c","credential.useHttpPath=true",
               "-c","http.followRedirects=false","-c","http.proxy=","-c","http.sslVerify=true",
               "-c","protocol.allow=never","-c","protocol.https.allow=always",
               "-c","core.hooksPath=/dev/null","-c","push.followTags=false","-c","push.recurseSubmodules=no"]
        try:
            p=subprocess.run(["/usr/bin/git",*flags,*argv[1:]],cwd=self.root,env=context,
                             stdin=subprocess.DEVNULL,capture_output=True,pass_fds=pass_fds,timeout=60)
        except subprocess.TimeoutExpired:
            self.last={"returncode":124,"category":"TIMEOUT_UNKNOWN"};return self.last
        low=p.stderr.lower()
        category=("ACCEPTED_RESPONSE" if p.returncode==0 else "RULE_REJECTED" if b"gh013" in low or b"gh006" in low
                  else "STALE_LEASE" if b"stale info" in low or b"stale info" in p.stdout.lower()
                  else "AUTH_DENIED" if b"authentication failed" in low or b"permission denied" in low or b"returned error: 403" in low
                  else "CAS_OR_OTHER_REJECTION")
        self.last={"returncode":p.returncode,"category":category}
        return self.last


class BoundCPrimeHTTPS:
    """Narrow transport override at the existing C-prime Git port."""
    def __init__(self, adapter):
        require(type(adapter) is GitHTTPS,"HTTPS_ADAPTER_TYPE_REQUIRED")
        self.adapter=adapter

    def __call__(self, args, permit_guard):
        from mq_queue import merge_transport_fds
        s=self.adapter.scope
        ref=s["ref"];lease="--force-with-lease="+ref+":"+s["expected"]
        refspec=s["head"]+":"+ref
        require(args==["push","--porcelain",lease,"origin",refspec],"HTTPS_CPRIME_PUSH_SCOPE_MISMATCH")
        fds=merge_transport_fds(["git","push"])
        require(len(fds)==1,"HTTPS_CPRIME_CANONICAL_LEASE_REQUIRED")
        result=self.adapter.push([lease,URL,refspec],permit_guard,fds[0])
        require(result=={"action":"issued"},"HTTPS_CPRIME_SEND_UNKNOWN_RECOVER_ONLY")
        return ""  # The request's status is never durable confirmation.
