import argparse,hashlib,io,json,os,sys,unittest
from pathlib import Path
p=argparse.ArgumentParser()
for key in ('runner-temp','source-root','evidence-dir','parent-netns','source-closure-sha256','stage'):
    p.add_argument('--'+key,required=True)
a=p.parse_args(); src=Path(a.source_root)
assert os.geteuid()!=0 and os.environ['RUNNER_ENVIRONMENT']=='github-hosted' and os.readlink('/proc/self/ns/net')!=a.parent_netns
assert not any(k in os.environ for k in ('GH_TOKEN','GITHUB_TOKEN','OPENAI_API_KEY','SSH_AUTH_SOCK'))
raw=(src/'SOURCE-CLOSURE.json').read_bytes()
assert hashlib.sha256(raw).hexdigest()==a.source_closure_sha256
m=json.loads(raw)['files_sha256']
assert set(m)|{'SOURCE-CLOSURE.json'}=={p.relative_to(src).as_posix() for p in src.rglob('*') if p.is_file()}
assert all(hashlib.sha256((src/n).read_bytes()).hexdigest()==h for n,h in m.items())
sys.path[:0]=[str(src),str(Path(__file__).resolve().parent)]
import frozen_preserved_update, frozen_existing_pr, frozen_adoption_provenance
modules=(frozen_adoption_provenance,) if a.stage=='red' else (frozen_adoption_provenance,frozen_preserved_update,frozen_existing_pr)
tests=unittest.TestSuite([unittest.defaultTestLoader.loadTestsFromModule(m) for m in modules])
stream=io.StringIO()
r=unittest.TextTestRunner(stream=stream,verbosity=2).run(tests)
raw=stream.getvalue();print(raw)
n,fail,err,skip=r.testsRun,len(r.failures),len(r.errors),len(r.skipped)
red=a.stage=='red' and n>0 and fail>0 and err==skip==0 and any('ValueError not raised' in v for _,v in r.failures)
green=a.stage=='green' and n>8 and fail==err==skip==0
out=Path(a.evidence_dir)
(out/'case-observations.json').write_text(json.dumps(dict(log=raw)))
(out/'verdict.json').write_text(json.dumps(dict(tests=n,failures=fail,errors=err,skips=skip,classification='expected-adoption-red' if red else 'existing-pr-adoption-passed' if green else 'execution-HOLD')))
raise SystemExit(1 if red else 0 if green else 86)
