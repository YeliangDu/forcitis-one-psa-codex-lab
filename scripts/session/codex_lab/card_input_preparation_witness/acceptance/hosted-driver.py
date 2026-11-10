"""Run the exact independently frozen history or candidate suite in isolation."""
import argparse,hashlib,json,os,subprocess,sys
from pathlib import Path
p=argparse.ArgumentParser()
for k in ('runner-temp','source-root','evidence-dir','parent-netns','source-closure-sha256','stage'):p.add_argument('--'+k,required=True)
a=p.parse_args()
assert os.geteuid()!=0 and os.environ['RUNNER_ENVIRONMENT']=='github-hosted' and os.readlink('/proc/self/ns/net')!=a.parent_netns
assert not any(k in os.environ for k in ('GH_TOKEN','GITHUB_TOKEN','OPENAI_API_KEY','SSH_AUTH_SOCK'))
here=Path(__file__).resolve().parent
s=Path(a.source_root);m=s/'SOURCE-CLOSURE.json'; assert hashlib.sha256(m.read_bytes()).hexdigest()==a.source_closure_sha256
for n,h in json.loads(m.read_bytes())['files_sha256'].items():assert hashlib.sha256((s/n).read_bytes()).hexdigest()==h
out=Path(a.evidence_dir)
av=[sys.executable,'-I','-B',str(here/'run_acceptance.py'),'--mode','history' if a.stage=='red' else 'candidate','--runner-temp',str(Path.cwd()),'--evidence-dir',str(out)]
if a.stage=='green':av+=['--source-root',str(s),'--source-sha256',json.loads(m.read_bytes())['files_sha256']['prepare_card.py']]
r=subprocess.run(av,capture_output=True,text=True,timeout=180)
log=r.stdout+r.stderr;print(log)
v=json.loads((out/'verdict.json').read_bytes())
raw=dict(v)
if a.stage=='red' and r.returncode==1 and v.get('classification')=='recorded-history-guard-assertion-red' and (v['tests'],v['failures'],v['errors'],v['skips'])==(4,2,0,0):
    v['classification']='expected-historical-red'
elif a.stage=='green' and r.returncode==0 and v.get('classification')=='passed' and v['tests']>=5 and v['failures']==v['errors']==v['skips']==0:
    v['classification']='candidate-green'
else:v['classification']='execution-HOLD'
(out/'case-observations.json').write_text(json.dumps(dict(log=log,frozen_verdict=raw,exit_code=r.returncode)))
(out/'verdict.json').write_text(json.dumps(v))
raise SystemExit(1 if v['classification']=='expected-historical-red' else 0 if v['classification']=='candidate-green' else 86)
