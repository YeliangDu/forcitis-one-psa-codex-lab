"""Future actual integrated labctl candidate contract; no fabricated baseline."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import unittest

ROOT=Path(__file__).resolve().parent


def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def inside(args):
    spec=importlib.util.spec_from_file_location('integrated_containment',ROOT/'worker.py')
    worker=importlib.util.module_from_spec(spec);spec.loader.exec_module(worker)
    worker.net_check(int(os.environ['INTEGRATED_PARENT_NETNS']))
    os.environ.update(LABCTL_CLOSURE=str(args.closure.resolve()),LABCTL_SOURCE_ROOT=str(args.source_root.resolve()),LABCTL_CLOSURE_SHA256=args.closure_sha256)
    suite=unittest.defaultTestLoader.discover(str(ROOT/'tests'),pattern='test_integrated.py')
    result=unittest.TextTestRunner(verbosity=2).run(suite)
    capability_hold=bool(result.errors or result.skipped or result.testsRun!=7
                         or result.expectedFailures or result.unexpectedSuccesses)
    passed=not capability_hold and result.wasSuccessful()
    classification=('HOLD-entry-API-or-capability' if capability_hold
                    else 'hosted-replay-contract-passed' if passed
                    else 'candidate-contract-assertions-failed')
    args.report.write_text(json.dumps(dict(kind='p1-integrated-hosted-replay/1',tests=result.testsRun,
        failures=[dict(id=t.id(),detail=d) for t,d in result.failures],errors=[dict(id=t.id(),detail=d) for t,d in result.errors],
        skipped=[t.id() for t,_ in result.skipped],classification=classification,
        B='77e872e8ba9661d3294d3a9337c8e5c956b70641',candidate_closure_sha256=args.closure_sha256,active_C=None,
        actual_native_authentication_proved=False,real_compact_restart_proved=False,operational_admission_proved=False,
        activation_authority=False,run=os.environ.get('GITHUB_RUN_ID'),attempt=os.environ.get('GITHUB_RUN_ATTEMPT')),indent=2)+'\n')
    return 86 if capability_hold else 0 if passed else 1


def main():
    p=argparse.ArgumentParser();p.add_argument('--manifest-sha256',required=True);p.add_argument('--source-root',type=Path)
    p.add_argument('--closure',type=Path);p.add_argument('--closure-sha256');p.add_argument('--report',type=Path,required=True)
    p.add_argument('--inside',action='store_true');args=p.parse_args();args.report=args.report.absolute()
    if os.environ.get('GITHUB_ACTIONS')!='true' or sys.platform!='linux' or os.geteuid()==0:raise ValueError('non-root standard hosted Linux required')
    if sha(ROOT/'manifest.json')!=args.manifest_sha256:raise ValueError('externally frozen A manifest mismatch')
    for relative,pin in json.loads((ROOT/'manifest.json').read_text())['files_sha256'].items():
        path=ROOT/relative
        if path.resolve(strict=True)!=path or sha(path)!=pin:raise ValueError('frozen A input missing/changed')
    if not args.source_root or not args.closure or not args.closure_sha256:raise ValueError('MISSING_INTEGRATED_LABCTL_SEAM: reviewed entry/source closure and later projection SHA required; no baseline red')
    if sha(args.closure)!=args.closure_sha256:raise ValueError('external source closure pin mismatch')
    closure=json.loads(args.closure.read_text());pins=json.loads((ROOT/'COMPONENT-PINS.json').read_text())
    if closure.get('contract')!='labctl-readonly-p1/1':raise ValueError('actual entry contract unavailable')
    if set(closure['slots'])!={'checkpoint','association','receipt','projection'}:raise ValueError('complete source slots required')
    files=closure['files_sha256'];entry=closure['entry']
    if entry not in files or entry in closure['slots'].values():raise ValueError('actual integrated entry required, not a standalone green leaf')
    for slot in ('checkpoint','association','receipt'):
        if files.get(closure['slots'][slot])!=pins[slot]['sha256']:raise ValueError('component source transition needs independent review: '+slot)
    projection=closure['slots']['projection']
    if projection not in files or files[projection]==pins['association']['sha256']:raise ValueError('later actual native projection pin required; old matcher is not adapter')
    for relative,pin in files.items():
        if Path(relative).is_absolute() or '..' in Path(relative).parts:raise ValueError('unsafe closure path')
        path=args.source_root.resolve()/relative
        if path.resolve(strict=True)!=path or not path.is_file() or sha(path)!=pin:raise ValueError('actual closure missing/changed: '+relative)
    if args.inside:return inside(args)
    if args.report.exists():raise ValueError('fresh report path required')
    if not Path('/usr/bin/unshare').is_file():raise ValueError('actual containment unavailable')
    env=dict(os.environ,INTEGRATED_PARENT_NETNS=str(os.stat('/proc/self/ns/net').st_ino),PYTHONDONTWRITEBYTECODE='1')
    cmd=['/usr/bin/unshare','--user','--map-current-user','--net','--',sys.executable,'-I','-S','-B',str(Path(__file__).resolve()),
         '--inside','--manifest-sha256',args.manifest_sha256,'--source-root',str(args.source_root.resolve()),'--closure',str(args.closure.resolve()),
         '--closure-sha256',args.closure_sha256,'--report',str(args.report)]
    result=subprocess.run(cmd,env=env,cwd=ROOT,capture_output=True,text=True,timeout=240)
    sys.stdout.write(result.stdout);sys.stderr.write(result.stderr)
    if not args.report.exists():raise ValueError('actual entry/API/namespace capability unavailable: '+result.stderr[:300])
    return result.returncode


if __name__=='__main__':
    try:raise SystemExit(main())
    except (OSError,ValueError,KeyError,RuntimeError,subprocess.SubprocessError) as error:
        print('CAPABILITY_HOLD (not assertion red): '+str(error),file=sys.stderr);raise SystemExit(86)
