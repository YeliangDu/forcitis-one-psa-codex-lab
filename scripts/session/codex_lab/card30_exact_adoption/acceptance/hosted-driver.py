"""Runner evidence only; executes independently frozen tests unchanged."""
import argparse,json,os,re,subprocess,sys
from pathlib import Path
p=argparse.ArgumentParser()
for k in ('runner-temp','source-root','evidence-dir','parent-netns','source-closure-sha256','stage'):p.add_argument('--'+k,required=True)
a=p.parse_args()
assert os.geteuid()!=0 and os.environ['RUNNER_ENVIRONMENT']=='github-hosted' and os.readlink('/proc/self/ns/net')!=a.parent_netns
assert not any(k in os.environ for k in ('GH_TOKEN','GITHUB_TOKEN','OPENAI_API_KEY','SSH_AUTH_SOCK'))
here=Path(__file__).resolve().parent
av=[sys.executable,'-I','-B',str(here/'test_acceptance.py'),'--source-root',a.source_root,'--source-closure-sha256',a.source_closure_sha256]
r=subprocess.run(av,capture_output=True,text=True,timeout=75)
log=r.stdout+r.stderr
print(log)
m=re.search(r'^Ran (\d+) tests? in ',log,re.M)
fails=len(re.findall(r'^FAIL: ',log,re.M));errs=len(re.findall(r'^ERROR: ',log,re.M));skip=len(re.findall(r"\.\.\. skipped ",log))
want=('CARD30_EXACT_APPROVED_LAB_OBSERVE_REJECTED:invalid-existing-pr-scope','CARD30_EXACT_APPROVED_REGISTRATION_REJECTED:invalid-existing-pr-scope')
red=a.stage=='red' and r.returncode==1 and m and int(m[1])==7 and (fails,errs,skip)==(2,0,0) and all(x in log for x in want)
if a.stage=='green' and r.returncode==0 and m and int(m[1])==7:
    extra=[str(here/n) for n in ('frozen_existing_pr.py','frozen_adoption_provenance.py','frozen_preserved_update.py') if (here/n).is_file()]
    # Load legacy tests by filename without importing or executing the old driver.
    if extra:
        code='import sys,unittest,runpy; sys.path[:0]=[sys.argv[1]]; s=unittest.TestSuite(); l=unittest.TestLoader(); [s.addTests(l.loadTestsFromTestCase(c)) for f in sys.argv[2:] for c in runpy.run_path(f).values() if isinstance(c,type) and c is not unittest.TestCase and issubclass(c,unittest.TestCase)]; r=unittest.TextTestRunner(verbosity=2).run(s); sys.exit(0 if r.wasSuccessful() and not r.skipped else 1)'
        rr=subprocess.run([sys.executable,'-I','-B','-c',code,a.source_root,*extra],capture_output=True,text=True,timeout=75)
        addition=rr.stdout+rr.stderr; print(addition); log+=addition
        mm=re.search(r'^Ran (\d+) tests? in ',addition,re.M)
        legacy=int(mm[1]) if mm else 0
        green=rr.returncode==0 and legacy>0
    else:legacy=0;green=True
else:legacy=0;green=False
out=Path(a.evidence_dir)
(out/'case-observations.json').write_text(json.dumps(dict(log=log,tests_exit=r.returncode)))
(out/'verdict.json').write_text(json.dumps(dict(tests=(int(m[1])+legacy) if m else 0,failures=fails,errors=errs,skips=skip,legacy=legacy,classification='expected-exact-adoption-red' if red else 'exact-adoption-green' if green else 'execution-HOLD')))
raise SystemExit(1 if red else 0 if green else 86)
