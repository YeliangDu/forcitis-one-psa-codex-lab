"""Run independent unit acceptance and impacted suites in the isolated hosted job."""
import argparse,hashlib,io,json,os,subprocess,sys,unittest
from pathlib import Path
p=argparse.ArgumentParser()
for flag in ('runner-temp','source-root','evidence-dir','parent-netns','source-closure-sha256','stage'):
    p.add_argument('--'+flag,required=True)
a=p.parse_args()
src=Path(a.source_root).resolve(strict=True);root=Path(__file__).resolve().parent;out=Path(a.evidence_dir)
assert os.geteuid()!=0 and os.environ['RUNNER_ENVIRONMENT']=='github-hosted' and os.readlink('/proc/self/ns/net')!=a.parent_netns
assert not any(n in os.environ for n in ('GH_TOKEN','GITHUB_TOKEN','OPENAI_API_KEY','SSH_AUTH_SOCK'))
def sha(b):return hashlib.sha256(b).hexdigest()
raw=(src/'SOURCE-CLOSURE.json').read_bytes();assert sha(raw)==a.source_closure_sha256
assert all(sha((src/n).read_bytes())==h for n,h in json.loads(raw)['files_sha256'].items())
sys.path[:0]=[str(root),str(src)]
import frozen_interrupted
buffer=io.StringIO()
r=unittest.TextTestRunner(stream=buffer,verbosity=2).run(unittest.defaultTestLoader.loadTestsFromModule(frozen_interrupted))
print(buffer.getvalue())
new=dict(tests=r.testsRun,failures=len(r.failures),errors=len(r.errors),skips=len(r.skipped))
verdict=dict(suites={'interrupted':new},**new)
expected='INTERRUPTED_RECONCILIATION_CAPABILITY_MISSING'
is_red=(a.stage=='red' and r.testsRun>0 and r.failures and not r.errors and not r.skipped
        and all(expected in traceback for _,traceback in r.failures))
is_green=False
if a.stage=='green' and r.testsRun and r.wasSuccessful() and not r.skipped:
    e=out/'affected';e.mkdir()
    result=subprocess.run([sys.executable,'-I','-B',str(root/'affected/run_suites.py'),'--runner-temp',a.runner_temp,
       '--source-root',a.source_root,'--evidence-dir',str(e),'--parent-netns',a.parent_netns,
       '--source-closure-sha256',a.source_closure_sha256,'--stage','green'],timeout=300)
    affected=json.loads((e/'verdict.json').read_bytes())
    verdict['suites']['affected']=affected
    for k in new:verdict[k]+=affected[k]
    is_green=result.returncode==0 and affected['tests']==17
verdict['classification']='expected-interrupted-capability-red' if is_red else ('interrupted-reconcile-and-regressions-passed' if is_green else 'execution-HOLD')
(out/'case-observations.json').write_text(json.dumps(dict(test_names=unittest.defaultTestLoader.getTestCaseNames(frozen_interrupted.InterruptedAcceptance),native_or_actual_ledger_used=False),indent=2)+'\n')
(out/'verdict.json').write_text(json.dumps(verdict,indent=2)+'\n')
raise SystemExit(1 if is_red else (0 if is_green else 86))
