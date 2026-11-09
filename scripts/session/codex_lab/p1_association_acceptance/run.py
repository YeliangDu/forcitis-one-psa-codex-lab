"""Hosted-only test launcher. Actual existing baseline or separately pinned candidate."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT=Path(__file__).resolve().parent
BASELINE='6faec4f779cf74c43b2e05a3dd318862be7c88433c4ffb2345baa6587cc53340'


def load(name,path):
    spec=importlib.util.spec_from_file_location(name,path);module=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module);return module


def inside(args):
    load('association_containment',ROOT/'worker.py').net_check(int(os.environ['ASSOCIATION_PARENT_NETNS']))
    source=(args.target or ROOT/'baseline/resident_app_matcher.py').resolve(strict=True)
    pin=args.target_sha256 if args.target else BASELINE
    if not pin or hashlib.sha256(source.read_bytes()).hexdigest()!=pin:raise ValueError('selected actual source pin mismatch')
    os.environ.update(ASSOCIATION_TARGET=str(source),ASSOCIATION_TARGET_SHA256=pin,
                      ASSOCIATION_MODE='candidate' if args.target else 'baseline')
    # A separate genuinely exhausted fixture proves legacy API execution. It
    # does not alter/erase the bounded-positive raw continuation.
    F=load('association_capability_inputs',ROOT/'fixtures.py')
    with tempfile.TemporaryDirectory(prefix='association-capability-') as temporary:
        target=Path(temporary)/'actual_baseline.py';target.write_bytes((ROOT/'baseline/resident_app_matcher.py').read_bytes())
        result=subprocess.run([sys.executable,'-I','-S','-B',str(ROOT/'worker.py'),temporary,str(target),BASELINE,
            os.environ['ASSOCIATION_PARENT_NETNS'],'baseline'],input=json.dumps(F.case(exhausted=True)),text=True,
            capture_output=True,cwd=temporary,timeout=10)
        if result.returncode or json.loads(result.stdout)['result'].get('action')!='matched':
            raise ValueError('existing actual baseline API/fixture/containment unavailable: '+result.stderr)
    suite=unittest.defaultTestLoader.discover(str(ROOT/'tests'),pattern='test_association.py')
    result=unittest.TextTestRunner(verbosity=2).run(suite)
    expected=json.loads((ROOT/'EXPECTED.json').read_text())['assertions']
    failures={t.id():d for t,d in result.failures}
    red=not args.target and result.testsRun==11 and not result.errors and not result.skipped and set(failures)==set(expected) and all(v in failures[k] for k,v in expected.items())
    green=bool(args.target and result.testsRun==11 and result.wasSuccessful() and not result.skipped)
    classification='expected-existing-behavior-red' if red else 'isolated-candidate-green' if green else 'HOLD-unexpected-result'
    args.report.write_text(json.dumps(dict(kind='p1-bounded-association-result/1',tests=result.testsRun,
        failures=[dict(id=k,detail=d) for k,d in failures.items()],errors=[dict(id=t.id(),detail=d) for t,d in result.errors],
        skipped=[t.id() for t,_ in result.skipped],classification=classification,source_sha256=pin,
        mode='candidate-window-proof' if args.target else 'legacy-compatible-baseline-comparison',
        B='77e872e8ba9661d3294d3a9337c8e5c956b70641',active_C=None,activation_authority=False,
        native_dispatch=False,actual_native_adapter_proved=False,nonroot=True,network_namespace_isolated=True,
        run=os.environ.get('GITHUB_RUN_ID'),attempt=os.environ.get('GITHUB_RUN_ATTEMPT')),indent=2)+'\n')
    return 1 if red else 0 if green else 86


def main():
    p=argparse.ArgumentParser();p.add_argument('--manifest-sha256',required=True);p.add_argument('--target',type=Path)
    p.add_argument('--target-sha256');p.add_argument('--report',type=Path,required=True);p.add_argument('--inside',action='store_true')
    args=p.parse_args();args.report=args.report.absolute()
    if os.environ.get('GITHUB_ACTIONS')!='true' or sys.platform!='linux' or os.geteuid()==0:raise ValueError('non-root standard hosted Linux required')
    raw=(ROOT/'manifest.json').read_bytes()
    if hashlib.sha256(raw).hexdigest()!=args.manifest_sha256:raise ValueError('external frozen manifest mismatch')
    for relative,pin in json.loads(raw)['files_sha256'].items():
        path=ROOT/relative
        if path.resolve(strict=True)!=path or hashlib.sha256(path.read_bytes()).hexdigest()!=pin:raise ValueError('frozen input changed: '+relative)
    if args.inside:return inside(args)
    if args.report.exists():raise ValueError('fresh report path required')
    if not Path('/usr/bin/unshare').is_file():raise ValueError('network containment unavailable')
    env=dict(os.environ,ASSOCIATION_PARENT_NETNS=str(os.stat('/proc/self/ns/net').st_ino),PYTHONDONTWRITEBYTECODE='1')
    command=['/usr/bin/unshare','--user','--map-current-user','--net','--',sys.executable,'-I','-S','-B',str(Path(__file__).resolve()),
        '--inside','--manifest-sha256',args.manifest_sha256,'--report',str(args.report)]
    if args.target:command+=['--target',str(args.target.resolve(strict=True)),'--target-sha256',args.target_sha256 or '']
    result=subprocess.run(command,env=env,cwd=ROOT,text=True,capture_output=True,timeout=180)
    sys.stdout.write(result.stdout);sys.stderr.write(result.stderr)
    if not args.report.exists():raise ValueError('containment/API/fixture capability unavailable: '+result.stderr[:400])
    return result.returncode


if __name__=='__main__':
    try:raise SystemExit(main())
    except (OSError,ValueError,RuntimeError,KeyError,subprocess.SubprocessError) as error:
        print('CAPABILITY_HOLD (not assertion red): '+str(error),file=sys.stderr);raise SystemExit(86)
