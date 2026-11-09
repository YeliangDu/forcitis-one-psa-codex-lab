"""Fixed-branch transport for a broker-owned consumed effect. No entrypoint or retry."""
import hashlib
import json
import os
from pathlib import Path
import re
import selectors
import shlex
import signal
import stat
import subprocess
import time

REPO='YeliangDu/forcitis-one-psa-codex-lab'
RID=1449415666
ORIGIN=f'git@github.com:{REPO}.git'
MAX=256*1024

def need(ok, reason):
    if not ok:
        raise ValueError(reason)

def sha(raw):
    return hashlib.sha256(raw).hexdigest()

def physical(name):
    p=Path(name)
    need(p.is_absolute() and p.resolve(strict=True)==p, 'nonphysical-path')
    return p

def pinned(name, digest):
    p=physical(name)
    fd=os.open(p,os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK)
    with os.fdopen(fd,'rb') as f:
        before=os.fstat(f.fileno())
        need(stat.S_ISREG(before.st_mode) and before.st_uid==os.geteuid()
             and before.st_nlink==1 and not before.st_mode&0o022, 'unsafe-pinned-input')
        raw=f.read(MAX+1)
        after=p.lstat()
        ident=lambda s:(s.st_dev,s.st_ino,s.st_size,s.st_mtime_ns)
        need(len(raw)<=MAX and ident(before)==ident(after) and sha(raw)==digest, 'pinned-input-drift')
    return raw

def run_bounded(argv, *, cwd, env, timeout=35):
    with subprocess.Popen(argv,cwd=cwd,env=env,stdin=subprocess.DEVNULL,
                          stdout=subprocess.PIPE,stderr=subprocess.PIPE,start_new_session=True) as child:
        out={child.stdout:bytearray(),child.stderr:bytearray()}
        until=time.monotonic()+timeout
        try:
            with selectors.DefaultSelector() as sel:
                for f in out:sel.register(f,selectors.EVENT_READ)
                while sel.get_map():
                    left=until-time.monotonic()
                    need(left>0,'transport-deadline-outcome-unknown')
                    for key,_ in sel.select(left):
                        f=key.fileobj;b=os.read(f.fileno(),16384)
                        if not b:sel.unregister(f);continue
                        out[f].extend(b)
                        need(sum(map(len,out.values()))<=MAX,'transport-output-bound-outcome-unknown')
            rc=child.wait(timeout=max(0,until-time.monotonic()))
            return rc,bytes(out[child.stdout]),bytes(out[child.stderr])
        except BaseException:
            # Kill only this operation's process group; the remote may still finish.
            try:os.killpg(child.pid,signal.SIGKILL)
            except ProcessLookupError:pass
            child.wait(timeout=5)
            raise

class BranchEffect:
    """Installation comes from the independently pinned private Broker policy."""
    def __init__(self, subject, installation):
        self.i=installation
        need(subject==installation['subject'] and set(subject)=={'repo_id','branch','expected','head','card'}
             and type(subject['card']) is int and subject['card']>0 and subject['repo_id']==RID
             and re.fullmatch(r'codex/'+str(subject['card'])+r'-[a-z0-9][a-z0-9-]{0,90}',subject['branch'])
             and all(re.fullmatch('[a-f0-9]{40}',subject[k]) for k in ('expected','head'))
             and subject['expected']!=subject['head'],'branch-subject-not-approved')
        self.s=dict(subject);self.ref='refs/heads/'+subject['branch']
        self.root=physical(installation['checkout'])
        self.gitdir=physical(installation['common_dir'])
        need(self.gitdir==self.root/'.git' and self.gitdir.is_dir(),'standalone-approved-common-dir-required')
        self.empty_home=physical(installation['empty_home'])
        need(self.empty_home.is_dir() and not any(self.empty_home.iterdir())
             and self.empty_home.stat().st_uid==os.geteuid()
             and stat.S_IMODE(self.empty_home.stat().st_mode)==0o700,'empty-private-home-required')

    def semantic_key(self):
        return ['git-push-cas',RID,self.ref,self.s['expected']]

    def _verify_config(self):
        for name in ('objects/info/alternates','objects/info/http-alternates','commondir','config.worktree','shallow'):
            p=self.gitdir/name
            need(not p.exists() and not p.is_symlink(),'unexpected-local-git-redirection')
        physical(str(self.gitdir/'objects'))
        raw=pinned(str(self.gitdir/'config'),self.i['git_config_sha256'])
        # Fail closed on all configurable sections outside a minimal standalone repo.
        # This intentionally rejects includes, pushurl, receivepack, aliases, extensions,
        # credential helpers, filters, fsmonitor, partial-clone and other remote config.
        env={'PATH':'/usr/bin:/bin','HOME':str(self.empty_home)}
        rc,out,_=run_bounded(['/usr/bin/git','config','--file',str(self.gitdir/'config'),
                             '--no-includes','--null','--list'],cwd=self.root,env=env)
        need(rc==0,'unable-to-read-pinned-git-config')
        permitted={'core.repositoryformatversion':{'0'},'core.filemode':{'true','false'},
                   'core.bare':{'false'},'core.logallrefupdates':{'true','false'},
                   'core.ignorecase':{'true','false'},'core.precomposeunicode':{'true','false'},
                   'remote.origin.url':{ORIGIN},'remote.origin.fetch':{'+refs/heads/*:refs/remotes/origin/*'}}
        seen={}
        for entry in out.decode().strip('\0').split('\0'):
            key,sep,value=entry.partition('\n')
            need(sep and key not in seen and value in permitted.get(key,()),'unapproved-local-git-config')
            seen[key]=value
        need(seen.get('remote.origin.url')==ORIGIN and sha(raw)==self.i['git_config_sha256'],'git-origin-not-pinned')
        pinned(str(self.gitdir/'config'),self.i['git_config_sha256'])

    def _git(self,*args):
        self._verify_config()
        known=physical(self.i['known_hosts'])
        pinned(str(known),self.i['known_hosts_sha256'])
        agent=physical(self.i['ssh_agent_socket']);info=agent.stat()
        need(stat.S_ISSOCK(info.st_mode) and info.st_uid==os.geteuid()
             and [info.st_dev,info.st_ino]==self.i['ssh_agent_dev_inode'],'ssh-agent-identity-drift')
        ssh=['/usr/bin/ssh','-F','/dev/null','-oBatchMode=yes','-oStrictHostKeyChecking=yes',
             '-oUserKnownHostsFile='+str(known),'-oGlobalKnownHostsFile=/dev/null',
             '-oProxyCommand=none','-oProxyJump=none','-oPermitLocalCommand=no']
        env={'PATH':'/usr/bin:/bin','HOME':str(self.empty_home),'SSH_AUTH_SOCK':str(agent),
             'GIT_CONFIG_NOSYSTEM':'1','GIT_CONFIG_GLOBAL':'/dev/null','GIT_TERMINAL_PROMPT':'0',
             'GIT_OPTIONAL_LOCKS':'0','GIT_SSH_COMMAND':shlex.join(ssh)}
        argv=['/usr/bin/git','--no-replace-objects','--git-dir='+str(self.gitdir),'--work-tree='+str(self.root),
              '-c','core.hooksPath=/dev/null','-c','protocol.allow=never','-c','protocol.ssh.allow=always',*args]
        return run_bounded(argv,cwd=self.root,env=env)

    def _read_git(self,*args):
        rc,out,_=self._git(*args);need(rc==0,'git-observation-unavailable')
        return out.decode().strip()

    def _api(self,suffix):
        # Repository metadata and ref are public. This transport needs no GH credential.
        rc,out,_=run_bounded(['/usr/bin/curl','--disable','--silent','--show-error','--fail',
                              '--proto','=https','--max-time','25','https://api.github.com/repos/'+REPO+suffix],
                             cwd=self.root,env={'PATH':'/usr/bin:/bin','HOME':str(self.empty_home)},timeout=28)
        need(rc==0,'public-github-observation-unavailable')
        return json.loads(out)

    def observe(self):
        repo=self._api('')
        need(repo.get('id')==RID and repo.get('full_name')==REPO and repo.get('private') is False,'repo-identity-drift')
        parts=self._read_git('ls-remote','--exit-code','--refs','origin',self.ref).split('\t')
        ref=self._api('/git/ref/heads/'+self.s['branch'])
        need(len(parts)==2 and parts[1]==self.ref and re.fullmatch('[a-f0-9]{40}',parts[0])
             and ref.get('ref')==self.ref and ref.get('object',{}).get('type')=='commit'
             and ref['object'].get('sha')==parts[0],'ref-observers-disagree')
        return dict(ref=self.ref,observed=parts[0],exact_ref_observed=parts[0]==self.s['head'],
                    sender_attribution_proved=False)

    def preflight(self):
        need(self.observe()['observed']==self.s['expected'],'expected-ref-moved')
        need(self._read_git('cat-file','-t',self.s['head'])=='commit'
             and self._git('merge-base','--is-ancestor',self.s['expected'],self.s['head'])[0]==0,
             'not-fast-forward-from-preimage')

    def issue_after_consumed_reservation(self,reservation,record_private):
        need(callable(record_private),'independent-private-recorder-required')
        need(reservation.get('phase')=='unknown' and reservation.get('issue_count')==1
             and reservation.get('effect_id') and reservation.get('semantic_key')==self.semantic_key()
             and reservation.get('subject')==self.s,'consumed-reservation-required')
        self.preflight()
        argv=['push','--porcelain','--no-follow-tags','--recurse-submodules=no',
              '--force-with-lease='+self.ref+':'+self.s['expected'],
              'origin',self.s['head']+':'+self.ref]
        rc,out,err=self._git(*argv)
        status=[line.split('\t') for line in out.decode(errors='replace').splitlines() if '\t' in line]
        matched_range=False
        if len(status)==1 and len(status[0])==3:
            change=re.fullmatch(r'([a-f0-9]{7,40})\.\.([a-f0-9]{7,40})',status[0][2])
            matched_range=bool(change and self.s['expected'].startswith(change[1]) and self.s['head'].startswith(change[2]))
        exact=(rc==0 and len(status)==1 and len(status[0])==3 and status[0][0]==' '
               and status[0][1]==self.s['head']+':'+self.ref and matched_range)
        receipt=record_private(effect_id=reservation['effect_id'],argv=tuple(argv),stdout=out,stderr=err,exit_code=rc)
        need(type(receipt) is str and re.fullmatch('[a-f0-9]{64}',receipt),'accepted-output-not-persisted')
        return dict(effect_id=reservation['effect_id'],exact_accepted_update=exact,exit_code=rc,
                    private_receipt_sha256=receipt,stdout_sha256=sha(out),stderr_sha256=sha(err),confirmed=False)
