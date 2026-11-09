"""Complete independent owner handoff entry; prepared only, never auto-runs.

Original six cases are reused byte-identically. Two additional executable
branches require actual kernel/commit/lifecycle evidence or return HOLD.
"""
import argparse
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys

PACKAGE=Path(__file__).resolve().parent
BASE_SHA='1e1e205bc59e4fb773f686e698726ccae3818b0fc7fc1963b3ab3824ace5ceec'
CUT_SHA='0a893e0bb8a0479af16900fd2f2cbbc320aa07c4422eb7bc3c0ab4d0b620d21f'
class Hold(RuntimeError):pass

def sha(raw):return hashlib.sha256(raw).hexdigest()
def base():
    p=PACKAGE/'base_owner_selection.py'
    if sha(p.read_bytes())!=BASE_SHA:raise Hold('original six-case code changed')
    spec=importlib.util.spec_from_file_location('_original_six_owner',p);m=importlib.util.module_from_spec(spec);sys.modules[spec.name]=m;spec.loader.exec_module(m);return m

def prepare_released(B,case):
    # Actual public fence/release, no post-setup SQL state manipulation.
    observed=case.call('inspect',auth=case.auth)
    B.require(observed.get('owner',{}).get('generation')==1,'ORIGINAL_AUTH_NOT_REAL')
    before=case.w.snapshot()
    B.require(case.call('owner.transfer-release',dict(grant_id=case.grant),case.auth).get('action')=='hold','UNFENCED_RELEASE_ACCEPTED')
    B.require(before==case.w.snapshot(),'UNFENCED_RELEASE_CHANGED_STATE')
    fence=case.call('assignment.revoke',{},case.auth)
    B.require(fence['revoking_seq']<fence['fenced_seq'],'FENCE_ORDER_WRONG')
    released=case.call('owner.transfer-release',dict(grant_id=case.grant),case.auth)
    if released.get('action')!='released':raise Hold('actual released state not reached')
    state=case.w.snapshot();owner=state['owner'][0]
    B.require(owner['token_sha'] is None and owner['generation']==1 and owner['paused']==1,'RELEASE_NOT_DURABLE')
    return released,state

def hard_kill_case(B,args):
    case=B.Case(args,'hard-kill-released-recovery')
    try:
        released,before=prepare_released(B,case)
        original_pid=case.w.child.pid
        # Fresh released-state permit for the actual stale-socket refusal.
        # Child is idle/committed; SIGKILL does not unlink or change the socket.
        case.startup_binding()
        case.w.hard_kill_reconcile(case.binding)
        cleanup=case.w.audit[-1]
        B.require(cleanup['owned_process']==original_pid and cleanup['owned_wait_exit']<0,'NOT_ORIGINAL_HARD_KILLED_CHILD')
        after_cleanup=case.w.snapshot()
        B.require(before==after_cleanup,'HARD_CRASH_OR_CLEANUP_CHANGED_LOGICAL_LEDGER')
        case.startup_binding();case.w.start(case.binding)
        case.status('contender-a')
        requests=[case.request('owner.transfer-claim',case.claim(client)) for client in ('contender-a','contender-b')]
        winner=case.w.race(case.binding,requests,PACKAGE/'barrier_cli.py',B.PINS['barrier_cli.py'])
        case.status('contender-a');case.status('contender-b')
        return dict(case='hard-kill-released-recovery',actual_SIGKILL_and_owned_wait=True,
                    stale_public_startup_refusal=True,exact_owned_socket_reconciliation=cleanup,
                    logical_ledger_unchanged_by_cleanup=True,fresh_released_state_startup=True,
                    exactly_one_two_CLI_claim=winner,credential_returned=False,
                    App_Server_epoch_proved=False,actual_current_owner_affected=False,audit=case.w.audit)
    finally:case.close()

def lost_reply_case(B,args):
    p=PACKAGE/'claim_reply_cut.py'
    if sha(p.read_bytes())!=CUT_SHA:raise Hold('receive-entry fault supervisor changed')
    case=B.Case(args,'lost-claim-reply-status')
    try:
        released,before=prepare_released(B,case)
        client='contender-a';claim=case.claim(client)
        request=case.request('owner.transfer-claim',claim)
        actual_argv=case.w.argv('call',case.binding)+['--request',str(request)]
        cut=case.root/'receive-cut.json'
        command=[sys.executable,'-I','-S','-B',str(p),'--database',str(case.db),
                 '--socket',str(case.ledger/'broker.sock'),'--effect-id',released['effect_id'],
                 '--client',client,'--generation','2','--token-sha256',sha(case.values[client]['token'].encode()),
                 '--report',str(cut),'--',*actual_argv]
        result=subprocess.run(command,cwd=case.root,env=case.w.env,capture_output=True,timeout=25)
        evidence=json.loads(cut.read_text()) if cut.exists() else {}
        if result.returncode!=0 or not evidence.get('cut_reached') or not evidence.get('claim_committed'):
            raise Hold('actual commit-before-receive cut unavailable; no lost-reply coverage')
        B.require(result.stdout==b'','KILLED_CLIENT_OBTAINED_APPLICATION_REPLY')
        B.require(evidence['response_receive_syscalls_completed']==0
                  and evidence['response_bytes_received_by_client']==0
                  and evidence['reply_read_by_supervisor'] is False
                  and evidence['actual_client_terminal_signal']==9,'REPLY_WAS_READ_BEFORE_DROP')
        unknown_state=case.w.snapshot()
        for _ in range(2):
            status=case.status(client)
            B.require(status.get('action')=='claimed' and status.get('outcome',{}).get('client')==client
                      and status['outcome']['generation']==2,'PUBLIC_STATUS_DID_NOT_RECONCILE_UNKNOWN')
        final=case.w.snapshot()
        B.require(final==unknown_state,'UNKNOWN_STATUS_RECONCILIATION_MUTATED_STATE')
        transfers=[e for e in final['effects'] if e['kind']=='owner-transfer']
        B.require(len(transfers)==1 and transfers[0]['phase']=='claimed' and transfers[0]['issue_count']==1,'UNKNOWN_RECONCILIATION_REISSUED_CLAIM')
        B.require(final['owner'][0]['generation']==2 and final['owner'][0]['paused']==1,'UNKNOWN_STATUS_CHANGED_OWNER')
        return dict(case='lost-claim-reply-status',actual_receive_cut=evidence,
                    actual_client_stdout_bytes=0,actual_client_stderr_sha256=sha(result.stderr),
                    claim_requests_after_cut=0,public_reconciliation_operations=['owner.transfer-status','owner.transfer-status'],
                    status_read_only=True,exactly_one_claim_effect=True,credential_returned=False,
                    actual_current_owner_affected=False,audit=case.w.audit)
    finally:case.close()

def main():
    p=argparse.ArgumentParser(allow_abbrev=False)
    p.add_argument('--source-root',type=Path,default=PACKAGE/'candidate-source')
    p.add_argument('--fixture-source-root',type=Path,default=PACKAGE/'fixture-source')
    p.add_argument('--runner-temp',type=Path,required=True);p.add_argument('--evidence-dir',type=Path,required=True)
    p.add_argument('--parent-netns',required=True);p.add_argument('--selection',choices=('base','hard-kill','lost-reply','all'),default='all')
    args=p.parse_args();args.source_root=args.source_root.resolve(strict=True);args.fixture_source_root=args.fixture_source_root.resolve(strict=True);args.runner_temp=args.runner_temp.resolve(strict=True);args.evidence_dir=args.evidence_dir.resolve(strict=True)
    if any(args.evidence_dir.iterdir()):raise Hold('fresh evidence directory required')
    os.environ['RUNNER_TEMP']=str(args.runner_temp);os.environ['HOST_NET']=args.parent_netns
    results=[];active='preflight'
    try:
        B=base()
        for root,pin,files in [(args.source_root,B.SOURCE_SHA,B.SOURCE_FILES),(args.fixture_source_root,B.FIXTURE_SHA,B.FIXTURE_FILES)]:
            if sha((root/'SOURCE-CLOSURE.json').read_bytes())!=pin:raise Hold('candidate/fixture closure differs')
            for name,digest in files.items():
                if sha((root/name).read_bytes())!=digest:raise Hold('candidate/fixture source differs')
        labels=['same-process-race','released-controlled-restart','expiry-renewal','blocked-unresolved-effect','blocked-live-turn','blocked-pending-wait'] if args.selection in ('base','all') else []
        if args.selection in ('hard-kill','all'):labels.append('hard-kill-released-recovery')
        if args.selection in ('lost-reply','all'):labels.append('lost-claim-reply-status')
        for active in labels:
            if active=='hard-kill-released-recovery':fact=hard_kill_case(B,args)
            elif active=='lost-claim-reply-status':fact=lost_reply_case(B,args)
            else:fact=B.exercise(args,active)
            results.append(fact);(args.evidence_dir/'case-observations.json').write_text(json.dumps(results,indent=2)+chr(10))
        verdict=dict(classification='synthetic-owner-transfer-selected-branches-observed',selection=args.selection,
                     completed_cases=len(results),covered_cases=labels,hard_kill_observed='hard-kill-released-recovery'in labels,
                     commit_before_receive_reply_cut_observed='lost-claim-reply-status'in labels,
                     nonempty_orphan_history_covered=False,transaction_internal_crash_covered=False,
                     real_native_owner_or_App_Server=False,overall_vertical_pass=False);code=0
    except AssertionError:
        verdict=dict(classification='reached-owner-behavior-assertion-failure',failed_case=active,completed_cases=len(results),overall_vertical_pass=False);code=1
    except Exception:
        verdict=dict(classification='capability-or-fixture-HOLD',unavailable_case=active,completed_cases=len(results),
                     unavailable_branch_not_counted_as_pass=True,overall_vertical_pass=False);code=86
    (args.evidence_dir/'verdict.json').write_text(json.dumps(verdict,indent=2)+chr(10));print(json.dumps(verdict));return code
if __name__=='__main__':raise SystemExit(main())
