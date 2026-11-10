"""Hosted evidence capture for the independent exact H1→H2 fixture."""
import argparse,hashlib,json,os,re,subprocess,sys
from pathlib import Path
p=argparse.ArgumentParser()
for k in ('runner-temp','source-root','evidence-dir','parent-netns','source-closure-sha256','stage'):p.add_argument('--'+k,required=True)
a=p.parse_args()
assert os.geteuid()!=0 and os.environ['RUNNER_ENVIRONMENT']=='github-hosted' and os.readlink('/proc/self/ns/net')!=a.parent_netns
assert not any(k in os.environ for k in ('GH_TOKEN','GITHUB_TOKEN','OPENAI_API_KEY','SSH_AUTH_SOCK'))
here=Path(__file__).resolve().parent
m=json.loads((here/'manifest.json').read_bytes())['files_sha256']
b=['binding.json']
test=['test_followup_acceptance.py']
assert len(b)==len(test)==1
binding=here/b[0]
av=[sys.executable,'-I','-B','-c','import sys,runpy; p=sys.argv.pop(1);runpy.run_path(p,run_name="__main__")',str(here/test[0]),'--binding',str(binding),'--binding-sha256',m[b[0]],'--exports',str(here/'exports'),'--source-root',a.source_root,'--source-closure-sha256',a.source_closure_sha256]
r=subprocess.run(av,capture_output=True,text=True,timeout=100)
log=r.stdout+r.stderr;print(log)
n=re.search(r'^Ran (\d+) tests? in ',log,re.M);n=int(n[1]) if n else 0
names=re.findall(r'^FAIL: (test_followup_\d+[^ ]*)',log,re.M)
err=len(re.findall(r'^ERROR: ',log,re.M));skip=len(re.findall(r"\.\.\. skipped ",log))
red=a.stage=='red' and r.returncode==1 and n==5 and len(names)==3 and err==skip==0 and [x.split('_')[2] for x in names]==['01','05','05'] and 'CARD30_EXACT_FOLLOWUP_REJECTED:' in log
green=a.stage=='green' and r.returncode==0 and n==5 and not names and err==skip==0
out=Path(a.evidence_dir)
(out/'case-observations.json').write_text(json.dumps(dict(log=log,tests_exit=r.returncode)))
(out/'verdict.json').write_text(json.dumps(dict(tests=n,failures=len(names),errors=err,skips=skip,classification='expected-exact-followup-red' if red else 'exact-followup-green' if green else 'execution-HOLD')))
raise SystemExit(1 if red else 0 if green else 86)
