"""Existing hosted isolation witness; candidate never receives a live transport."""
import argparse,hashlib,io,json,os,shutil,subprocess,sys,unittest
from pathlib import Path

p=argparse.ArgumentParser()
for k in ('runner-temp','source-root','evidence-dir','parent-netns','source-closure-sha256','stage'):
    p.add_argument('--'+k,required=True)
a=p.parse_args(); src=Path(a.source_root); out=Path(a.evidence_dir)
assert os.geteuid()!=0 and os.environ['RUNNER_ENVIRONMENT']=='github-hosted' and os.readlink('/proc/self/ns/net')!=a.parent_netns
assert not any(k in os.environ for k in ('GH_TOKEN','GITHUB_TOKEN','OPENAI_API_KEY','SSH_AUTH_SOCK'))
sha=lambda b:hashlib.sha256(b).hexdigest()
raw=(src/'SOURCE-CLOSURE.json').read_bytes();assert sha(raw)==a.source_closure_sha256
pins=json.loads(raw)['files_sha256']
assert {p.relative_to(src).as_posix() for p in src.rglob('*') if p.is_file()}==set(pins)|{'SOURCE-CLOSURE.json'}
assert all(sha((src/n).read_bytes())==v for n,v in pins.items())
acc=Path(__file__).resolve().parent
sys.path[:0]=[str(acc),str(src/'controller/scripts/merge')]
import frozen_cprime_ordinary,frozen_expiring_permit,frozen_https_adapter,frozen_main_only,frozen_personal69,frozen_personal68
def suite(module):
    stream=io.StringIO()
    result=unittest.TextTestRunner(stream=stream,verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(module.Personal68Acceptance) if module is frozen_personal68 else unittest.defaultTestLoader.loadTestsFromModule(module))
    text=stream.getvalue();print(text)
    return dict(tests=result.testsRun,failures=len(result.failures),errors=len(result.errors),skips=len(result.skipped),
                missing=bool(result.failures) and all('CAPABILITY_MISSING' in t for _,t in result.failures))
new=suite(frozen_personal68)
suites={'personal_authority':new}
observations={'closure_files':len(pins),'separate_health_fixture_not_GitHub_authority':True}
red=a.stage=='red' and new['tests']>0 and new['tests']==new['failures'] and new['errors']==new['skips']==0 and new['missing']
if a.stage=='green':
    suites['original_personal_authority']=suite(frozen_personal69)
    suites['main_authority']=suite(frozen_main_only)
    suites['https_boundary']=suite(frozen_https_adapter)
    suites['permit_final_boundary']=suite(frozen_expiring_permit)
    suites['new_combination']=suite(frozen_cprime_ordinary)
    disposable=Path.cwd()/'disposable-controller';shutil.copytree(src/'controller',disposable)
    m=disposable/'scripts/merge'
    for n in ('mq_lab_cleanup_recovery_selftest.py','lab55_cleanup_fixture_frozen.py','ordinary_v2_selftest.py'):
        shutil.copyfile(acc/n,m/n)
    shutil.copyfile(acc/'fixture_health_adapter.py',m/'lab55_cleanup_fixture.py')
    # Explicit test-only health port for the old ordinary schema contract;
    # none of the original test assertions are edited.
    runner="""import sys,unittest,json,os
sys.path.insert(0,os.getcwd())
import ordinary_v2_selftest as old
old.OrdinaryV2AcceptanceTest.release_health=lambda self,merge:dict(action='healthy' if merge==old.MERGE else 'hold',head_sha=merge)
import mq_lab_cleanup_recovery_selftest as cleanup
for module in (old,cleanup):
 r=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromModule(module))
 print('SUITE_JSON '+json.dumps(dict(name=module.__name__,tests=r.testsRun,failures=len(r.failures),errors=len(r.errors),skips=len(r.skipped))),flush=True)
"""
    q=subprocess.run([sys.executable,'-I','-B','-c',runner],cwd=m,capture_output=True,text=True,timeout=400)
    observations.update(legacy_exit=q.returncode,legacy_stdout_sha256=sha(q.stdout.encode()),legacy_stderr_sha256=sha(q.stderr.encode()))
    for line in q.stdout.splitlines():
        if line.startswith('SUITE_JSON '):
            v=json.loads(line[11:]);suites[v.pop('name')]=v
    if len(suites)!=8 or q.returncode!=0 or any(x['errors'] or x['failures'] for x in suites.values()):
        print(q.stdout[-7500:]);print(q.stderr[-12000:])
        observations['legacy_error_tail']=q.stderr[-12000:]
green=a.stage=='green' and len(suites)==8 and all(v['tests']>0 and not(v['failures'] or v['errors'] or v['skips']) for v in suites.values())
counts={k:sum(x[k] for x in suites.values()) for k in ('tests','failures','errors','skips')}
(out/'case-observations.json').write_text(json.dumps(observations,indent=2)+'\n')
(out/'verdict.json').write_text(json.dumps(dict(suites=suites,**counts,
    classification='expected-capability-red' if red else ('cprime-ordinary-combination-passed' if green else 'execution-HOLD')),indent=2)+'\n')
raise SystemExit(1 if red else (0 if green else 86))
