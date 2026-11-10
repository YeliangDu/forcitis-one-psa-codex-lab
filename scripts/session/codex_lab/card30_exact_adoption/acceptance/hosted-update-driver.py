"""Capture independent #30 update suite on the existing isolated runner."""
import argparse,json,os,re,subprocess,sys
from pathlib import Path
p=argparse.ArgumentParser()
for k in ('runner-temp','source-root','evidence-dir','parent-netns','source-closure-sha256','stage'):p.add_argument('--'+k,required=True)
a=p.parse_args()
assert os.geteuid()!=0 and os.environ['RUNNER_ENVIRONMENT']=='github-hosted' and os.readlink('/proc/self/ns/net')!=a.parent_netns
assert not any(k in os.environ for k in ('GH_TOKEN','GITHUB_TOKEN','OPENAI_API_KEY','SSH_AUTH_SOCK'))
here=Path(__file__).resolve().parent
av=[sys.executable,'-I','-B','-c','import sys,runpy; p=sys.argv.pop(1); sys.path.insert(0,p); runpy.run_path(p+"/test_update_acceptance.py",run_name="__main__")',str(here),'--source-root',a.source_root,'--source-closure-sha256',a.source_closure_sha256]
r=subprocess.run(av,capture_output=True,text=True,timeout=75)
log=r.stdout+r.stderr
print(log)
m=re.search(r'^Ran (\d+) tests? in ',log,re.M)
failure_names=re.findall(r'^FAIL: (test_update_\d+[^ ]*)',log,re.M)
errs=len(re.findall(r'^ERROR: ',log,re.M));skip=len(re.findall(r"\.\.\. skipped ",log))
red=a.stage=='red' and r.returncode==1 and m and int(m[1])==6 and errs==skip==0 and [x.split('_')[2] for x in failure_names]==['01','05','06'] and log.count('AssertionError: CARD30_EXACT_APPROVED_UPDATE_REJECTED:')==3
green=a.stage=='green' and r.returncode==0 and m and int(m[1])==6 and not failure_names and errs==skip==0
legacy=0
if green:
    runner=here/'frozen_card30_observation.py'
    rr=subprocess.run([sys.executable,'-I','-B',str(runner),'--source-root',a.source_root,'--source-closure-sha256',a.source_closure_sha256],capture_output=True,text=True,timeout=75)
    extra=rr.stdout+rr.stderr;print(extra);log+=extra
    if rr.returncode!=0 or not re.search(r'^Ran 7 tests? in ',extra,re.M):green=False
    else:legacy=7
out=Path(a.evidence_dir)
(out/'case-observations.json').write_text(json.dumps(dict(log=log,tests_exit=r.returncode)))
(out/'verdict.json').write_text(json.dumps(dict(tests=(int(m[1])+legacy) if m else 0,failures=len(failure_names),errors=errs,skips=skip,legacy=legacy,classification='expected-exact-update-red' if red else 'exact-update-green' if green else 'execution-HOLD')))
raise SystemExit(1 if red else 0 if green else 86)
