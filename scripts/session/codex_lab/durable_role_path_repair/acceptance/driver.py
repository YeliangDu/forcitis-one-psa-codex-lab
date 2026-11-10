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
import frozen_durable_role, frozen_persistent_metadata
stream=io.StringIO()
suite=unittest.defaultTestLoader.loadTestsFromModule(frozen_persistent_metadata)
if a.stage=='green':
    suite.addTests(unittest.defaultTestLoader.loadTestsFromModule(frozen_durable_role))
r=unittest.TextTestRunner(stream=stream,verbosity=2).run(suite)
raw=stream.getvalue()
print(raw)
n,fail,err,skip=r.testsRun,len(r.failures),len(r.errors),len(r.skipped)
red=a.stage=='red' and n==3 and fail>0 and err==skip==0 and any('PERSISTENT_ANCESTOR_METADATA_MISSING' in x for _,x in r.failures)
green=a.stage=='green' and (n,fail,err,skip)==(7,0,0,0)
out=Path(a.evidence_dir)
(out/'case-observations.json').write_text(json.dumps(dict(log=raw)))
(out/'verdict.json').write_text(json.dumps(dict(tests=n,failures=fail,errors=err,skips=skip,classification='expected-metadata-red' if red else 'durable-role-passed' if green else 'execution-HOLD')))
raise SystemExit(1 if red else 0 if green else 86)
