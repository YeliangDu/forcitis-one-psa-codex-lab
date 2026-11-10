"""Concrete test-owned pre-O/pre-H hooks, exact object-backed code closure."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
from fixture_material import verify_locked_material, LockedGitObjects, advance_verified_seed
from exact_setup import reviewed_builder, canonical_phase, verify_after_commit

HERE = Path(__file__).resolve().parent
LOCKED_SHA = "57861849e04941e36fc5bdcd9b23d924aad34fba1009dc644f713f5cb821e58f"
INVENTORY = [('new', '.github/ISSUE_TEMPLATE/card.md'), ('new', '.github/workflows/lab-pr-ci.yml'), ('new', '.gitleaks.toml'), ('new', '.gitleaksignore'), ('new', 'policy.json'), ('new', 'scripts/ci/backend_verify.sh'), ('new', 'scripts/ci/lab_backend_lane.sh'), ('new', 'scripts/ci/lab_backend_shards.py'), ('new', 'scripts/ci/legacy_it.sh'), ('new', 'scripts/ci/light.sh'), ('new', 'scripts/ci/light_triggers.sh'), ('new', 'scripts/ci/relevant_change.py'), ('new', 'scripts/codegen/generate_api.sh'), ('new', 'scripts/guards/acceptance_reports.py'), ('new', 'scripts/merge/canonical_storage_transition.py'), ('new', 'scripts/merge/cutover_e2e_driver.py'), ('new', 'scripts/merge/issue_gate.py'), ('new', 'scripts/merge/mq_lab.py'), ('new', 'scripts/merge/mq_lab_active.py'), ('new', 'scripts/merge/mq_lab_authorization.py'), ('new', 'scripts/merge/mq_lab_control_authorization.py'), ('new', 'scripts/merge/mq_lab_control_install.py'), ('new', 'scripts/merge/mq_lab_control_manifest.py'), ('new', 'scripts/merge/mq_lab_control_plan.py'), ('new', 'scripts/merge/mq_lab_control_receipt.py'), ('new', 'scripts/merge/mq_lab_cutover_channel.py'), ('new', 'scripts/merge/mq_lab_cutover_entry.py'), ('new', 'scripts/merge/mq_lab_cutover_live.py'), ('new', 'scripts/merge/mq_lab_cutover_old.py'), ('new', 'scripts/merge/mq_lab_cutover_ordinary.py'), ('new', 'scripts/merge/mq_lab_cutover_recovery.py'), ('new', 'scripts/merge/mq_lab_cutover_successor.py'), ('new', 'scripts/merge/mq_lab_journal.py'), ('new', 'scripts/merge/mq_lab_journal_v2.py'), ('new', 'scripts/merge/mq_lab_operator.py'), ('new', 'scripts/merge/mq_lab_proof.py'), ('new', 'scripts/merge/mq_lab_runtime.py'), ('new', 'scripts/merge/mq_lab_trust.py'), ('new', 'scripts/merge/mq_queue.py'), ('new', 'scripts/merge/post_review.py'), ('new', 'scripts/session/codex_lab/install_gitleaks.sh'), ('new', 'scripts/session/codex_lab/install_hosted_shellcheck.sh'), ('new', 'scripts/session/codex_lab/lab_main_health.py'), ('new', 'scripts/session/codex_lab/lab_pr_ci.py'), ('new', 'scripts/session/codex_lab/lab_queue_evidence.py'), ('new', 'scripts/session/codex_lab/lab_queue_git_writer.py'), ('new', 'scripts/session/codex_lab/lab_queue_live.py'), ('new', 'scripts/session/codex_lab/lab_queue_release.py'), ('old', '.github/ISSUE_TEMPLATE/card.md'), ('old', '.github/workflows/lab-pr-ci.yml'), ('old', '.gitleaks.toml'), ('old', '.gitleaksignore'), ('old', 'policy.json'), ('old', 'scripts/ci/backend_verify.sh'), ('old', 'scripts/ci/lab_backend_lane.sh'), ('old', 'scripts/ci/lab_backend_shards.py'), ('old', 'scripts/ci/legacy_it.sh'), ('old', 'scripts/ci/light.sh'), ('old', 'scripts/ci/light_triggers.sh'), ('old', 'scripts/ci/relevant_change.py'), ('old', 'scripts/codegen/generate_api.sh'), ('old', 'scripts/guards/acceptance_reports.py'), ('old', 'scripts/merge/canonical_storage_transition.py'), ('old', 'scripts/merge/cutover_e2e_driver.py'), ('old', 'scripts/merge/issue_gate.py'), ('old', 'scripts/merge/mq_lab.py'), ('old', 'scripts/merge/mq_lab_active.py'), ('old', 'scripts/merge/mq_lab_authorization.py'), ('old', 'scripts/merge/mq_lab_control_authorization.py'), ('old', 'scripts/merge/mq_lab_control_install.py'), ('old', 'scripts/merge/mq_lab_control_manifest.py'), ('old', 'scripts/merge/mq_lab_control_plan.py'), ('old', 'scripts/merge/mq_lab_control_receipt.py'), ('old', 'scripts/merge/mq_lab_cutover_channel.py'), ('old', 'scripts/merge/mq_lab_cutover_entry.py'), ('old', 'scripts/merge/mq_lab_cutover_live.py'), ('old', 'scripts/merge/mq_lab_cutover_old.py'), ('old', 'scripts/merge/mq_lab_cutover_recovery.py'), ('old', 'scripts/merge/mq_lab_cutover_successor.py'), ('old', 'scripts/merge/mq_lab_journal.py'), ('old', 'scripts/merge/mq_lab_journal_v2.py'), ('old', 'scripts/merge/mq_lab_operator.py'), ('old', 'scripts/merge/mq_lab_proof.py'), ('old', 'scripts/merge/mq_lab_runtime.py'), ('old', 'scripts/merge/mq_lab_trust.py'), ('old', 'scripts/merge/mq_queue.py'), ('old', 'scripts/merge/post_review.py'), ('old', 'scripts/session/codex_lab/install_gitleaks.sh'), ('old', 'scripts/session/codex_lab/install_hosted_shellcheck.sh'), ('old', 'scripts/session/codex_lab/lab_main_health.py'), ('old', 'scripts/session/codex_lab/lab_pr_ci.py'), ('old', 'scripts/session/codex_lab/lab_queue_evidence.py'), ('old', 'scripts/session/codex_lab/lab_queue_git_writer.py'), ('old', 'scripts/session/codex_lab/lab_queue_live.py'), ('old', 'scripts/session/codex_lab/lab_queue_release.py')]
EXTRA_BYTES = {'.github/workflows/lab-22-public-old-main-witness.yml': b'name: Synthetic witness\non: push\npermissions: {contents: read}\njobs: {}\n', '.github/workflows/lab-22-integrated-cutover.yml': b'name: Synthetic independent proof\non: workflow_dispatch\npermissions: {contents: read}\njobs: {}\n'}
CONTEXTS = {}


def need(ok, reason):
    if not ok:
        raise AssertionError(reason)


def old_before_commit(test):
    need(os.environ.get('GITHUB_ACTIONS') == 'true', 'E2E_HOSTED_ONLY_MATERIALIZATION')
    raw = (HERE / 'LOCKED-PHASES.json').read_bytes()
    need(hashlib.sha256(raw).hexdigest() == LOCKED_SHA, 'E2E_LOCKED_PHASE_MANIFEST_CHANGED')
    spec = json.loads(raw)
    source = HERE.parents[1]  # installed scripts/merge/ -> project root
    need(source.name == 'forcitis-one-psa-codex-lab' and source.resolve(strict=True) == source,
         'E2E_SOURCE_CHECKOUT_NOT_FIXED_LAB')
    reader = LockedGitObjects(source, {(e['source_commit'],e['source_path']) for e in spec['entries']})
    material = verify_locked_material(raw, LOCKED_SHA, set(INVENTORY), set(), reader)
    material.seed(test.root, 'old')
    # Fixed test-owned workflow metadata is included in canonical paths below.
    for relative, data in EXTRA_BYTES.items():
        path = test.root / relative
        need(not path.exists(), 'E2E_EXTRA_FIXTURE_PATH_COLLISION')
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_bytes(data)
    builder = reviewed_builder(test.root / 'scripts/merge/mq_lab_control_manifest.py')
    CONTEXTS[str(test.root)] = dict(test=test,material=material,builder=builder)
    def git_bytes(*args):
        r=subprocess.run(['git','--no-replace-objects','-C',str(test.root),*args],env=test.env,capture_output=True,timeout=20)
        need(r.returncode==0,'E2E_TEST_OWNED_GIT_READ_FAILED')
        return r.stdout
    def git_at(where,*args):
        where=Path(where).resolve(strict=True)
        need(test.root in where.parents,'E2E_SOURCE_READ_OUTSIDE_SCRATCH')
        r=subprocess.run(['git','--no-replace-objects','-C',str(where),*args],env=test.env,capture_output=True,timeout=20)
        need(r.returncode==0,'E2E_TEST_OWNED_PROFILE_GIT_READ_FAILED')
        return r.stdout.decode().strip()
    test.git_bytes=git_bytes;test.git_at=git_at
    test.env.update(GITHUB_ACTIONS='true', PYTHONDONTWRITEBYTECODE='1')
    test.git('add','-A')


def new_before_commit(test):
    context = CONTEXTS.get(str(test.root))
    need(context is not None, 'E2E_PRE_O_MATERIAL_HOOK_MISSING')
    advance_verified_seed(context['material'],test.root)
    test.git('add','-A')


def phase_manifest(context, phase, commit):
    test, material = context['test'], context['material']
    paths = [e['target_path'] for e in material.entries if e['phase']==phase] + list(EXTRA_BYTES)
    rows=[]
    for path in paths:
        result=subprocess.run(['git','--no-replace-objects','-C',str(test.root),'show',commit+':'+path],
                              env=test.env,capture_output=True,timeout=10)
        need(result.returncode==0,'E2E_SEEDED_SOURCE_NOT_IN_EXACT_COMMIT')
        content=result.stdout
        mode=next((e['mode'] for e in material.entries if e['phase']==phase and e['target_path']==path),'100644')
        expected=material.payloads.get((phase,path),EXTRA_BYTES.get(path))
        need(content==expected,'E2E_EXACT_PHASE_BYTES_CHANGED')
        rows.append(dict(path=path,mode=mode,blob=hashlib.sha1(b'blob '+str(len(content)).encode()+b'\0'+content).hexdigest(),content=content))
    return paths,context['builder'](paths,rows)
