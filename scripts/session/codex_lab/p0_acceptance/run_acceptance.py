"""Independent hosted test result capture; raw expected red still exits1."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import unittest

B = '77e872e8ba9661d3294d3a9337c8e5c956b70641'
PREFIX = 'scripts/session/codex_lab/p0_acceptance/'
EXPECTED = {
 'test_p0_existing_contracts.P0ExistingContractAcceptance.test_A03_compact_without_current_authority_must_not_inject_old_static_dispatch': 'A03_COMPACT_STALE_FIXTURE_INJECTED',
 'test_p0_existing_contracts.P0ExistingContractAcceptance.test_A03_resume_without_current_authority_must_not_inject_old_static_dispatch': 'A03_RESUME_STALE_FIXTURE_INJECTED',
}


def main():
    p=argparse.ArgumentParser()
    p.add_argument('--source-checkout',type=Path,required=True)
    p.add_argument('--expected-head',required=True)
    p.add_argument('--report',type=Path,required=True)
    p.add_argument('--hook-target',type=Path)
    p.add_argument('--hook-target-sha256')
    args=p.parse_args();root=Path(__file__).resolve().parent
    def git(*argv):
        return subprocess.check_output(['git','--no-replace-objects',*argv],cwd=args.source_checkout,
                                       text=True,stderr=subprocess.PIPE,timeout=20).strip()
    if os.environ.get('GITHUB_ACTIONS')!='true':raise ValueError('actual hosted job required')
    manifest=json.loads((root/'manifest.json').read_text())
    for relative,expected in manifest['files_sha256'].items():
        if hashlib.sha256((root/relative).read_bytes()).hexdigest()!=expected:raise ValueError('frozen file changed: '+relative)
    head=git('rev-parse','HEAD')
    if head!=args.expected_head:raise ValueError('actual A head differs from external selected H')
    changed=git('diff','--name-only','--no-renames',B,head,'--').splitlines()
    allowed=set(manifest['files_sha256'])|{'manifest.json','RESUME.json','RUNNER-HANDOFF.md'}
    if not changed or any(not x.startswith(PREFIX) or x[len(PREFIX):] not in allowed for x in changed):
        raise ValueError('A is not the exact tests-only staging package')
    target=args.hook_target.resolve(strict=True) if args.hook_target else root/'baselines/hook.py'
    target_sha=args.hook_target_sha256 if args.hook_target else manifest['files_sha256']['baselines/hook.py']
    if not target_sha or hashlib.sha256(target.read_bytes()).hexdigest()!=target_sha:
        raise ValueError('missing/changed externally selected actual hook CLI')
    os.environ['P0_SESSIONSTART_TARGET']=str(target)
    os.environ['P0_SESSIONSTART_TARGET_SHA256']=target_sha
    suite=unittest.defaultTestLoader.discover(str(root/'tests'),pattern='test_p0_existing_contracts.py')
    result=unittest.TextTestRunner(verbosity=2).run(suite)
    failures={t.id():detail for t,detail in result.failures}
    expected_red=(result.testsRun==14 and not result.errors and not result.skipped
                  and set(failures)==set(EXPECTED) and all(marker in failures[k] for k,marker in EXPECTED.items()))
    green=(result.wasSuccessful() and result.testsRun==14 and not result.skipped)
    classification=('expected-component-assertion-red' if expected_red else 'component-green' if green and args.hook_target else 'HOLD-unexpected-result')
    verdict=dict(kind='lab-p0-existing-baseline-result/1',card=61,revision=2,
        claim='a2688a482aeaf37269395b258ee39c01',B=B,A=head,H=None,target_file_sha256=target_sha,
        C=None,control_status='HOLD: active control source/trust not bound by this component suite',
        tests=result.testsRun,failures=[dict(id=k,detail=v) for k,v in failures.items()],
        errors=[dict(id=t.id(),detail=d) for t,d in result.errors],skipped=[t.id() for t,_ in result.skipped],
        classification=classification,
        expected_assertions=list(EXPECTED.values()),run=os.environ.get('GITHUB_RUN_ID'),attempt=os.environ.get('GITHUB_RUN_ATTEMPT'),
        native_active_hook_proved=False,native_recovery_proved=False,activation_authority=False)
    args.report.write_text(json.dumps(verdict,indent=2)+'\n')
    if classification.startswith('HOLD'): return 86
    return 0 if green else 1


if __name__=='__main__':
    try: raise SystemExit(main())
    except (OSError,ValueError,KeyError,subprocess.SubprocessError) as error:
        print('CAPABILITY_HOLD (not assertion red): '+str(error))
        raise SystemExit(86)
