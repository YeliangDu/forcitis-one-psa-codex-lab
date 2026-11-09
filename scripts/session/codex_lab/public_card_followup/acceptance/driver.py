"""Frozen contract suites on the existing isolated GitHub witness path."""
import argparse,hashlib,io,json,os,sys,unittest
from pathlib import Path
p=argparse.ArgumentParser()
for k in ('runner-temp','source-root','evidence-dir','parent-netns','source-closure-sha256','stage'):p.add_argument('--'+k,required=True)
a=p.parse_args();src=Path(a.source_root);out=Path(a.evidence_dir)
assert os.geteuid()!=0 and os.environ['RUNNER_ENVIRONMENT']=='github-hosted' and os.readlink('/proc/self/ns/net')!=a.parent_netns
assert not any(k in os.environ for k in ('GH_TOKEN','GITHUB_TOKEN','OPENAI_API_KEY','SSH_AUTH_SOCK'))
sha=lambda b:hashlib.sha256(b).hexdigest()
raw=(src/'SOURCE-CLOSURE.json').read_bytes()
assert sha(raw)==a.source_closure_sha256
assert all(sha((src/n).read_bytes())==v for n,v in json.loads(raw)['files_sha256'].items())
sys.path[:0]=[str(Path(__file__).resolve().parent),str(src)]
import frozen_first_publication,frozen_card_capsule_acceptance,frozen_followup
suites={}
for name,m in [('followup',frozen_followup)]+([('first_publication',frozen_first_publication),('card_capsule',frozen_card_capsule_acceptance)] if a.stage=='green' else []):
    buf=io.StringIO();r=unittest.TextTestRunner(stream=buf,verbosity=2).run(unittest.TestSuite(unittest.defaultTestLoader.loadTestsFromTestCase(c) for c in vars(m).values() if isinstance(c,type) and issubclass(c,unittest.TestCase) and c.__module__==m.__name__))
    print(buf.getvalue())
    suites[name]=dict(tests=r.testsRun,failures=len(r.failures),errors=len(r.errors),skips=len(r.skipped),
       missing_capability=bool(r.failures) and all('CAPABILITY_MISSING' in t for _,t in r.failures))
counts={k:sum(r[k] for r in suites.values()) for k in ('tests','failures','errors','skips')}
red=a.stage=='red' and all(r['tests'] and r['tests']==r['failures'] and r['missing_capability'] for r in suites.values()) and counts['errors']==counts['skips']==0
green=a.stage=='green' and all(r['tests'] for r in suites.values()) and counts['failures']==counts['errors']==counts['skips']==0
v=dict(suites=suites,classification='expected-capability-red' if red else ('first-publication-and-capsule-passed' if green else 'execution-HOLD'),**counts)
(out/'verdict.json').write_text(json.dumps(v,indent=2)+'\n')
raise SystemExit(1 if red else (0 if green else 86))
