"""Materialize only reviewed R3 fixture runtime files from fixed Git blobs."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys

B='77e872e8ba9661d3294d3a9337c8e5c956b70641'
H='47e263825afb62a1b860915f8293da79572d12f6'
CANDIDATE='b61f0bc20a6bf4034c6dcda21862d28d97e1d7c942099a09d63708b821cf3750'
OVERLAYS={'scripts/merge/mq_lab_cutover_native.py','scripts/merge/mq_lab_control_install.py','scripts/merge/mq_github.py'}
sha=lambda b:hashlib.sha256(b).hexdigest()

def require(p,expected):
    raw=p.read_bytes()
    if sha(raw)!=expected:
        raise ValueError('reviewed-file-digest-mismatch')
    return raw

def main():
    p=argparse.ArgumentParser(allow_abbrev=False)
    for name in ('repo','package','out'):
        p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--material-sha256',required=True)
    a=p.parse_args()
    assert sys.platform=='linux' and os.geteuid()!=0 and os.environ.get('RUNNER_ENVIRONMENT')=='github-hosted'
    repo=a.repo.resolve(strict=True)
    package=a.package.resolve(strict=True)
    out=a.out.resolve(strict=True)
    assert out.is_dir() and not any(out.iterdir()) and out.is_relative_to(Path(os.environ['RUNNER_TEMP']).resolve(strict=True))
    material=json.loads(require(package/'MATERIAL.json',a.material_sha256))
    assert material['base']==B and material['transport_H']==H and material['candidate_freeze_sha256']==CANDIDATE
    freeze=json.loads(require(package/'candidate/FROZEN-v4.json',CANDIDATE))
    files=material['files']
    assert type(files)is dict and 13<=len(files)<=350 and OVERLAYS<=set(files)
    git=lambda *args:subprocess.check_output(['/usr/bin/git','--no-replace-objects','-C',str(repo),*args],timeout=10)
    tracked={}
    for obj in (B,H):
        raw=git('ls-tree','-rz',obj,'--','scripts','.github','policy.json','.gitleaksignore','.gitleaks.toml')
        rows=(x.split(b'\t',1) for x in raw.split(b'\0') if x)
        tracked[obj]={n.decode():v.decode().split() for v,n in rows}
    counts={'base':0,'transport':0,'candidate':0}
    for name,v in files.items():
        path=PurePosixPath(name)
        assert not path.is_absolute() and '..' not in path.parts and str(path)==name
        assert re.fullmatch('[A-Za-z0-9_./-]+',name) and set(v)=={'mode','sha256'} and v['mode'] in ('100644','100755')
        if name in OVERLAYS:
            assert freeze['files_sha256']['files/'+name]==v['sha256']
            content=require(package/'candidate/files'/name,v['sha256'])
            kind='candidate'
            assert tracked[B][name][0:2]==[v['mode'],'blob']
        else:
            obj=H if name=='scripts/merge/mq_queue.py' else B
            mode,typ,oid=tracked[obj][name]
            assert (mode,typ)==(v['mode'],'blob')
            content=git('cat-file','blob',oid)
            assert sha(content)==v['sha256']
            kind='transport' if obj==H else 'base'
        dest=out/name
        dest.parent.mkdir(mode=0o700,parents=True,exist_ok=True)
        with dest.open('xb') as f:f.write(content)
        dest.chmod(0o700 if v['mode']=='100755' else 0o600)
        counts[kind]+=1
    print(json.dumps(dict(materialized=counts,material_sha256=a.material_sha256,
                          formal_writer_allowed=False,authenticated_native_claim=False)))

if __name__=='__main__':main()
