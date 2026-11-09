"""Run the frozen successor decision tests in the existing isolated witness."""
import argparse,hashlib,io,json,os,sys,unittest
from pathlib import Path
p=argparse.ArgumentParser()
for key in ('runner-temp','source-root','evidence-dir','parent-netns','source-closure-sha256','stage'):
    p.add_argument('--'+key,required=True)
a=p.parse_args()
src=Path(a.source_root)
assert os.geteuid()!=0 and os.environ['RUNNER_ENVIRONMENT']=='github-hosted' and os.readlink('/proc/self/ns/net')!=a.parent_netns
assert not any(k in os.environ for k in ('GH_TOKEN','GITHUB_TOKEN','OPENAI_API_KEY','SSH_AUTH_SOCK'))
raw=(src/'SOURCE-CLOSURE.json').read_bytes()
assert hashlib.sha256(raw).hexdigest()==a.source_closure_sha256
m=json.loads(raw)['files_sha256']
assert set(m)|{'SOURCE-CLOSURE.json'}=={p.relative_to(src).as_posix() for p in src.rglob('*') if p.is_file()}
assert all(hashlib.sha256((src/n).read_bytes()).hexdigest()==h for n,h in m.items())
sys.path[:0]=[str(src),str(Path(__file__).resolve().parent)]
import frozen_successor_card
stream=io.StringIO()
r=unittest.TextTestRunner(stream=stream,verbosity=2).run(unittest.defaultTestLoader.loadTestsFromModule(frozen_successor_card))
raw=stream.getvalue()
print(raw)
n,fail,err,skip=r.testsRun,len(r.failures),len(r.errors),len(r.skipped)
red=a.stage=='red' and (n,fail,err,skip)==(4,4,0,0) and all('SUCCESSOR_CARD_CAPABILITY_MISSING' in v for _,v in r.failures)
green=a.stage=='green' and (n,fail,err,skip)==(4,0,0,0)
out=Path(a.evidence_dir)
(out/'case-observations.json').write_text(json.dumps(dict(log=raw)))
(out/'verdict.json').write_text(json.dumps(dict(tests=n,failures=fail,errors=err,skips=skip,classification='expected-capability-red' if red else 'successor-decision-passed' if green else 'execution-HOLD')))
raise SystemExit(1 if red else 0 if green else 86)
