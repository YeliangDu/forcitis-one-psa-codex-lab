"""Generic read-only consumer of the existing production plan entry.

Approval is externally supplied and already stored by its independent owner.
This adapter neither publishes a review nor creates an approval or queue.
"""
import argparse
import hashlib
import json
import os
import re
import stat
import subprocess
import sys
from pathlib import Path


def require(ok, reason):
    if not ok:
        raise ValueError(reason)

def sha(data):
    return hashlib.sha256(data).hexdigest()

def regular(path, private=False):
    require(path.resolve(strict=True)==path, 'path-is-not-physical')
    s=path.lstat()
    require(stat.S_ISREG(s.st_mode) and s.st_uid==os.geteuid() and s.st_nlink==1, 'unsafe-input')
    require(not private or s.st_mode & 0o077==0, 'approval-is-not-private')
    require(s.st_size<=131072, 'input-too-large')
    return path.read_bytes()

def queue_snapshot(queue):
    return {str(p.relative_to(queue)):sha(p.read_bytes()) for p in queue.rglob('*')
            if p.is_file() and not p.is_symlink()}

def consume(number, head, base, controller, approval_sha, root, protected):
    ROOT = Path(root)
    require(ROOT.resolve(strict=True)==ROOT, 'unexpected-plan-broker')
    q=ROOT/'.git/merge-queue'
    require(q.resolve(strict=True)==q and all(q != p and p not in q.parents for p in protected), 'plan-cannot-use-formal-queue')
    require(all(re.fullmatch(r'[0-9a-f]{40}',s) for s in (head,base,controller))
            and re.fullmatch(r'[0-9a-f]{64}',approval_sha) and 0<number<2**31, 'exact-pins-required')
    env=dict(os.environ,PATH='/opt/homebrew/bin:/usr/bin:/bin',GIT_OPTIONAL_LOCKS='0')
    def git(*args):
        return subprocess.check_output(['/usr/bin/git','--no-replace-objects','-C',str(ROOT),*args],
                                       env=env,text=True,timeout=15).strip()
    require(git('rev-parse','HEAD')==controller and git('status','--porcelain','--untracked-files=all')==''
            and git('rev-parse','--path-format=absolute','--git-common-dir')==str(ROOT/'.git'),
            'controller-or-common-directory-mismatch')
    p=q/'approvals'/f'{number}.json'
    raw=regular(p,private=True)
    require(sha(raw)==approval_sha, 'independently-pinned-approval-drift')
    approval=json.loads(raw)
    identity=dict(number=number,head=head,base=base,target='main')
    require(all(approval.get(k)==v for k,v in identity.items()) and approval.get('repo_id')==1449415666,
            'approval-subject-mismatch')
    before=queue_snapshot(q)
    require('releases.sqlite3' not in before, 'unexpected-release-journal-in-readonly-broker')
    cmd=[sys.executable,'-B','scripts/merge/mq.py','plan',str(number)]
    result=subprocess.run(cmd,cwd=ROOT,env=env,capture_output=True,text=True,timeout=55)
    require(before==queue_snapshot(q), 'isolated-readonly-plan-state-drift')
    parsed=json.loads(result.stdout) if result.returncode==0 else None
    if isinstance(parsed,dict) and parsed.get('action')=='eligible':
        require(all(parsed.get(k)==v for k,v in identity.items()), 'plan-result-subject-mismatch')
    return dict(kind='v2-generic-readonly-plan/1',identity=identity,controller=controller,
                approval_sha256=approval_sha,exit_code=result.returncode,result=parsed,
                isolated_state_unchanged=True,writer_called=False)

def main():
    p=argparse.ArgumentParser(allow_abbrev=False)
    p.add_argument('--number',type=int,required=True)
    for k in ('head','base','controller','approval-sha256'):
        p.add_argument('--'+k,required=True)
    p.add_argument('--root',required=True)
    p.add_argument('--protected-roots',required=True)
    p.add_argument('--protected-roots-sha256',required=True)
    a=p.parse_args()
    try:
        from private_resources import load_reference
        protection=load_reference(a.protected_roots,a.protected_roots_sha256)
        require(protection.get('kind')=='lab-protected-roots/1' and protection.get('roots'),'protected-roots-required')
        protected=[Path(n) for n in protection['roots']]
        require(all(p.is_absolute() and '..' not in p.parts for p in protected),'absolute-protected-roots-required')
        result=consume(a.number,a.head,a.base,a.controller,a.approval_sha256,a.root,protected)
    except (OSError,ValueError,subprocess.SubprocessError) as e:
        result=dict(kind='v2-generic-readonly-plan/1',result=dict(action='hold',reason=str(e)[:250]),
                    writer_called=False)
    print(json.dumps(result,ensure_ascii=False,indent=2))
    return 0 if (result.get('result') or {}).get('action')=='eligible' else 86

if __name__=='__main__':
    raise SystemExit(main())
