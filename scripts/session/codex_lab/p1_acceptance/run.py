"""P1 hosted fixture launcher/result capture; never an operational hook/broker."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT=Path(__file__).resolve().parent
BASELINE='165654142e3eade8518404715b9116bf76e715fc0abdb8cddf1a61c81a3951c3'


def sha(path): return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def inside(args):
    spec=importlib.util.spec_from_file_location('p1_containment_check',ROOT/'cli_worker.py')
    checked=importlib.util.module_from_spec(spec);spec.loader.exec_module(checked)
    checked.net_check(int(os.environ['P1_PARENT_NETNS_INODE']))
    target=(args.target or ROOT/'baseline/hook.py').resolve(strict=True)
    pin=args.target_sha256 if args.target else BASELINE
    if not pin or sha(target)!=pin: raise ValueError('selected candidate external SHA mismatch')
    os.environ['P1_CLI_TARGET']=str(target)
    os.environ['P1_CLI_TARGET_SHA256']=pin
    # Actual baseline capability, not a fake new-interface implementation.
    with tempfile.TemporaryDirectory(prefix='p1-baseline-probe-') as temporary:
        lab=Path(temporary).resolve()/'forcitis-one-psa-codex-lab';lab.mkdir()
        entry=lab/'scripts/session/codex_lab/hook.py';entry.parent.mkdir(parents=True)
        shutil.copyfile(ROOT/'baseline/hook.py',entry)
        area=lab/'.codex-lab/probe/work';area.mkdir(parents=True)
        (area.parent/'work-dispatch.json').write_text(json.dumps(dict(role='fixture',role_kind='fixture',task='LEGACY_EXECUTABLE_CAPABILITY')))
        env=dict(PATH=str(Path(sys.executable).parent)+':/usr/bin:/bin',PYTHONDONTWRITEBYTECODE='1')
        result=subprocess.run([sys.executable,'-I','-S','-B',str(ROOT/'cli_worker.py'),str(lab),str(entry),BASELINE,
            os.environ['P1_PARENT_NETNS_INODE']],input=json.dumps(dict(hook_event_name='SessionStart',source='startup',cwd=str(area))),
            text=True,env=env,cwd=area,capture_output=True,timeout=10)
        if result.returncode or 'LEGACY_EXECUTABLE_CAPABILITY' not in result.stdout:
            raise ValueError('actual baseline executable/fixture containment capability failed: '+result.stderr)
    suite=unittest.defaultTestLoader.discover(str(ROOT/'tests'),pattern='test_p1_checkpoint.py')
    result=unittest.TextTestRunner(verbosity=2).run(suite)
    expected=json.loads((ROOT/'EXPECTED.json').read_text())['assertions']
    failed={test.id():detail for test,detail in result.failures}
    expected_red=(not args.target and result.testsRun==12 and not result.errors and not result.skipped
                  and set(failed)==set(expected) and all(marker in failed[name] for name,marker in expected.items()))
    green=bool(args.target and result.wasSuccessful() and result.testsRun==12 and not result.skipped)
    classification='expected-baseline-assertion-red' if expected_red else 'candidate-component-green' if green else 'HOLD-unexpected-result'
    report=dict(kind='lab-p1-isolated-acceptance/1',B='77e872e8ba9661d3294d3a9337c8e5c956b70641',
        source_sha256=pin,source_mode='candidate' if args.target else 'existing-legacy-baseline',
        active_C=None,activation_authority=False,operational_native_proof=False,
        containment=dict(nonroot=os.geteuid()!=0,network_namespace_isolated=True,nonloopback_interfaces=False,
                         candidate_readonly_audit=True,not_universal_hostile_native_code_sandbox=True),
        tests=result.testsRun,failures=[dict(id=k,detail=v) for k,v in failed.items()],
        errors=[dict(id=t.id(),detail=d) for t,d in result.errors],skipped=[t.id() for t,_ in result.skipped],
        classification=classification,run=os.environ.get('GITHUB_RUN_ID'),attempt=os.environ.get('GITHUB_RUN_ATTEMPT'))
    args.report.write_text(json.dumps(report,indent=2)+'\n')
    return 0 if green else 1 if expected_red else 86


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--manifest-sha256',required=True)
    parser.add_argument('--target',type=Path)
    parser.add_argument('--target-sha256')
    parser.add_argument('--report',type=Path,required=True)
    parser.add_argument('--inside',action='store_true')
    args=parser.parse_args();args.report=args.report.absolute()
    if os.environ.get('GITHUB_ACTIONS')!='true' or os.geteuid()==0 or sys.platform!='linux':
        raise ValueError('standard non-root hosted Linux required')
    raw=(ROOT/'manifest.json').read_bytes()
    if hashlib.sha256(raw).hexdigest()!=args.manifest_sha256:raise ValueError('externally frozen manifest mismatch')
    manifest=json.loads(raw)
    for relative,pin in manifest['files_sha256'].items():
        path=ROOT/relative
        if path.resolve(strict=True)!=path or sha(path)!=pin:raise ValueError('frozen A file missing/changed: '+relative)
    if args.inside:return inside(args)
    if args.report.exists():raise ValueError('fresh per-attempt report path required; no stale report reuse')
    if not Path('/usr/bin/unshare').is_file():raise ValueError('unshare unavailable; no containment fallback')
    parent=os.stat('/proc/self/ns/net').st_ino
    environment=dict(os.environ,P1_PARENT_NETNS_INODE=str(parent),PYTHONDONTWRITEBYTECODE='1')
    command=['/usr/bin/unshare','--user','--map-current-user','--net','--',sys.executable,'-I','-S','-B',str(Path(__file__).resolve()),
             '--inside','--manifest-sha256',args.manifest_sha256,'--report',str(args.report)]
    if args.target:command+=['--target',str(args.target.resolve(strict=True)),'--target-sha256',args.target_sha256 or '']
    result=subprocess.run(command,env=environment,cwd=ROOT,text=True,capture_output=True,timeout=180)
    sys.stdout.write(result.stdout);sys.stderr.write(result.stderr)
    if not args.report.exists():raise ValueError('network/user namespace or baseline capability unavailable; '+result.stderr[:400])
    return result.returncode


if __name__=='__main__':
    try: raise SystemExit(main())
    except (OSError,ValueError,KeyError,RuntimeError,subprocess.SubprocessError) as error:
        print('CAPABILITY_HOLD (not behavioral red): '+str(error),file=sys.stderr)
        raise SystemExit(86)
