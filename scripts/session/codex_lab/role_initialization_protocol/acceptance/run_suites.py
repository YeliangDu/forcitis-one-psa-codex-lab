"""Orchestrate frozen independent acceptance without changing its expectations."""
import argparse,json,subprocess,sys
from pathlib import Path
p=argparse.ArgumentParser()
for flag in ('runner-temp','source-root','evidence-dir','parent-netns','source-closure-sha256','stage'):
    p.add_argument('--'+flag,required=True)
a=p.parse_args()
root=Path(__file__).resolve().parent
out=Path(a.evidence_dir)
suites=[('initialization','run_first_launch.py',4)]
if a.stage=='green':
    suites += [('replacement','replacement/run_acceptance.py',3),('direct','direct/run_regression.py',5),('readonly','readonly/run_acceptance.py',5)]
reports={}
for name,entry,expected in suites:
    e=out/name;e.mkdir()
    cmd=[sys.executable,'-I','-B',str(root/entry)]
    for key in ('runner-temp','source-root','parent-netns','source-closure-sha256'):
        cmd+=['--'+key,getattr(a,key.replace('-','_'))]
    cmd+=['--evidence-dir',str(e)]
    proc=subprocess.run(cmd,timeout=135)
    path=e/'verdict.json'
    facts=json.loads(path.read_bytes()) if path.is_file() else {'classification':'missing-verdict'}
    reports[name]=dict(exit=proc.returncode,expected=expected,verdict=facts)
hold=any(r['verdict'].get('tests')!=r['expected'] or r['verdict'].get('errors') or r['verdict'].get('skips') for r in reports.values())
expected_red=a.stage=='red' and not hold and reports['initialization']['exit']==1 and reports['initialization']['verdict']['classification']=='expected-capability-assertion-red'
passed=a.stage=='green' and not hold and all(r['exit']==0 and r['verdict']['failures']==0 for r in reports.values())
status='expected-capability-assertion-red' if expected_red else ('initialization-and-affected-regressions-passed' if passed else 'execution-HOLD')
stats={k:sum(r['verdict'].get(k,0) for r in reports.values()) for k in ('tests','failures','errors','skips')}
(out/'case-observations.json').write_text(json.dumps(reports,indent=2)+'\n')
(out/'verdict.json').write_text(json.dumps(dict(classification=status,suites=reports,**stats),indent=2)+'\n')
raise SystemExit(0 if passed else (1 if expected_red else 86))
