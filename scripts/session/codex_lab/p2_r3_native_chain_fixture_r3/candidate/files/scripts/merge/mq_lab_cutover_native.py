"""Explicit native GET/argv composition; no default transport or CLI authority.

Policy comes from independently installed old control, never candidate arguments.
The existing installer owns locking, paired reservation and recovery. The only
writer is the existing public GitLabMainWriter; its result is never confirmation.
"""
import base64
import copy
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import re
import sys
import zipfile
import post_review

import mq_lab_control_install as installer
from mq_lab_control_authorization import load_upgrade_approval
from mq_lab_control_manifest import build_manifest
from mq_lab_control_plan import validate_independent_old_control_baseline
from mq_lab_control_receipt import validate_public_candidate_proof
from mq_lab_journal_v2 import ReleaseJournalV2, _canonical_approval

REPOSITORY = 'YeliangDu/forcitis-one-psa-codex-lab'
RID = 1449415666
IDENTITY = ('number', 'base', 'head', 'target')


def need(ok, reason):
    if not ok:
        raise ValueError(reason)


def sha(value, width=40):
    return type(value) is str and re.fullmatch('[0-9a-f]{' + str(width) + '}', value) is not None


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def unique(pairs):
    result = {}
    for key, value in pairs:
        need(key not in result, 'duplicate-native-JSON')
        result[key] = value
    return result


def invalid(value):
    raise ValueError('nonfinite-native-JSON')


def decode(raw):
    need(type(raw) is bytes and 0 < len(raw) <= 32 * 1024 * 1024, 'bounded-native-bytes-required')
    return json.loads(raw.decode('utf-8'), object_pairs_hook=unique, parse_constant=invalid)


def runtime(name):
    """Load the actual reviewed sibling library, with exact cache attribution.

    Hosted acceptance packages flat siblings; installed repository uses its fixed
    scripts/session/codex_lab location. No policy-selected module/path or import
    from the downloaded candidate/source evidence is permitted.
    """
    here = Path(__file__).resolve().parent
    flat = here / (name + '.py')
    path = flat if flat.is_file() else here.parent / 'session/codex_lab' / (name + '.py')
    need(path.resolve(strict=True) == path and path.is_file(), 'native-runtime-source-not-physical')
    if name in sys.modules:
        value = sys.modules[name]
        need(Path(value.__file__).resolve() == path, 'native-runtime-cached-source-not-exact')
        return value
    spec = importlib.util.spec_from_file_location(name, path)
    value = importlib.util.module_from_spec(spec)
    sys.modules[name] = value
    try:
        spec.loader.exec_module(value)
    except BaseException:
        del sys.modules[name]
        raise
    return value


def repository(value):
    need(type(value) is dict and type(value.get('id')) is int and value['id'] == RID
         and value.get('full_name') == REPOSITORY and value.get('private') is False
         and value.get('visibility', 'public') == 'public' and value.get('fork', False) is False,
         'native-repository-not-exact-public-Lab')
    return value


def main_ref(value):
    need(type(value) is dict and value.get('ref') == 'refs/heads/main'
         and type(value.get('object')) is dict and sha(value['object'].get('sha'))
         and value['object'].get('type', 'commit') == 'commit', 'native-main-ref-not-exact')
    return value['object']['sha']


class Source:
    """Read fixed Git objects over native GET, never execute downloaded code."""
    def __init__(self, get):
        self.get, self.trees = get, {}

    def file(self, commit, path):
        need(sha(commit) and type(path) is str and path and not path.startswith('/')
             and '\\' not in path and ':' not in path
             and all(p not in ('', '.', '..') for p in path.split('/')), 'unsafe-native-source-path')
        root = self.get('git/commits/' + commit)
        need(root.get('sha') == commit and sha(root.get('tree', {}).get('sha')), 'native-source-commit-mismatch')
        tree = root['tree']['sha']
        parts = path.split('/')
        for index, part in enumerate(parts):
            if tree not in self.trees:
                value = self.get('git/trees/' + tree)
                need(value.get('sha') == tree and value.get('truncated', False) is False
                     and type(value.get('tree')) is list, 'native-tree-incomplete')
                names = [row.get('path') for row in value['tree']]
                need(len(set(names)) == len(names), 'native-tree-ambiguous')
                self.trees[tree] = value['tree']
            rows = [row for row in self.trees[tree] if row.get('path') == part]
            need(len(rows) == 1 and sha(rows[0].get('sha')), 'native-source-path-missing')
            row = rows[0]
            if index < len(parts) - 1:
                need(row.get('type') == 'tree' and row.get('mode') == '040000', 'native-source-parent-not-tree')
                tree = row['sha']
            else:
                need(row.get('type') == 'blob' and row.get('mode') in ('100644', '100755'),
                     'native-source-not-regular')
        blob = self.get('git/blobs/' + row['sha'])
        need(blob.get('sha') == row['sha'] and blob.get('encoding') == 'base64'
             and type(blob.get('content')) is str, 'native-blob-not-exact')
        content = base64.b64decode(blob['content'].replace('\n', '').replace('\r', ''), validate=True)
        need(type(blob.get('size')) is int and len(content) == blob['size'] <= 32 * 1024 * 1024
             and hashlib.sha1(b'blob ' + str(len(content)).encode() + b'\0' + content).hexdigest() == row['sha'],
             'native-blob-raw-mismatch')
        return dict(path=path, mode=row['mode'], blob=row['sha'], content=content)

    def manifest(self, commit, paths):
        need(type(paths) is list and paths, 'independent-complete-source-inventory-required')
        entries = [self.file(commit, path) for path in paths]
        return build_manifest(paths, entries), entries


class NativePorts:
    def __init__(self, common, policy, get, git, journal):
        need(type(policy) is dict and callable(get) and callable(git), 'explicit-native-ports-required')
        self.common, self.policy, self.get, self.git, self.journal = common, copy.deepcopy(policy), get, git, journal
        self.writer_module = runtime('lab_queue_git_writer')
        self.github = runtime('lab_queue_live').GithubRead(get)
        self.health_module = runtime('lab_main_health')
        self.writer = self.writer_module.GitLabMainWriter(git, self.github._single, self.authorize)
        self.sending = None

    def subject(self, approval, *, merged=False):
        repository(self.github.repository())
        pr = self.github.pull(approval['number'])
        self.writer_module._pr(pr, {k: approval[k] for k in IDENTITY}, RID, merged)
        labels = pr.get('labels', [])
        need(type(labels) is list and all(type(row) is dict and type(row.get('name')) is str for row in labels)
             and not {'hold', '挂起'} & {row['name'] for row in labels}, 'native-subject-held')
        return pr

    def policy_approval(self, number):
        p = self.policy
        approval, _, _ = _canonical_approval(p['expected_approval'])
        need(p.get('repository') == REPOSITORY and type(p.get('repository_id')) is int
             and p['repository_id'] == RID and approval['number'] == number
             and p.get('installed_source_commit') == approval['base']
             and p.get('independent_proof_source') == approval['proof']['source_commit']
             and p.get('independent_proof_workflow') == approval['proof']['workflow_path']
             and p.get('independent_proof_workflow_blob') == approval['proof']['workflow_blob']
             and p.get('old_manifest') == approval['old_manifest']
             and p.get('new_manifest') == approval['new_manifest'], 'independently-installed-policy-mismatch')
        return approval

    def reviews(self, approval, pins):
        rows = self.github.reviews(approval['number'])
        need(type(pins) is dict and set(pins) == {'首轮', '终审'}, 'independent-review-pins-missing')
        ids = [row.get('id') for row in rows]
        need(all(type(i) is int and i > 0 for i in ids) and len(set(ids)) == len(ids), 'native-review-ID-ambiguous')
        for kind, assigned in approval['reviews'].items():
            pin = pins[kind]
            need(type(pin) is dict and set(pin) == {'native_user_id', 'body_sha256'}
                 and type(pin['native_user_id']) is int and pin['native_user_id'] > 0
                 and sha(pin['body_sha256'], 64), 'native-review-policy-not-exact')
            fields = post_review.load_policy()[0]['header_fields']
            speaking = []
            for row in rows:
                parsed = post_review.parse_header(row.get('body'), fields)
                if parsed and parsed['reviewer'] == assigned['reviewer'] and parsed['kind'] == kind:
                    speaking.append((row, parsed))
            need(speaking, 'assigned-native-review-missing')
            row, parsed = max(speaking, key=lambda value: value[0]['id'])
            need(row['id'] == assigned['id'] and row.get('commit_id') == approval['head']
                 and parsed['commit'] == approval['head'] and parsed['verdict'] == post_review.PASS
                 and row.get('state') in ('COMMENTED', 'APPROVED')
                 and type(row.get('user', {}).get('id')) is int
                 and row['user']['id'] == pin['native_user_id']
                 and digest(row['body'].encode()) == pin['body_sha256'], 'native-review-moved-or-refused')

    def independent_execution(self, approval, proof, source):
        """Bind actual native run/source/job/upload/download to explicit old pins."""
        pins = proof['independent_execution']
        expected = approval['proof']
        run = self.github._single(f"actions/runs/{expected['run_id']}/attempts/{expected['attempt']}")
        repository(run.get('repository'))
        need(run.get('id') == expected['run_id'] and run.get('run_attempt') == expected['attempt']
             and run.get('head_sha') == expected['source_commit'] and run.get('path') == expected['workflow_path']
             and run.get('event') == 'push' and run.get('status') == 'completed'
             and run.get('conclusion') == 'success', 'independent-native-execution-not-exact')
        need(run.get('head_branch') == pins['branch']
             and self.latest(expected['workflow_path'], expected['source_commit'], 'push', pins['branch'])
             == dict(run_id=expected['run_id'], run_attempt=expected['attempt']),
             'independent-native-execution-not-current')
        workflow = source.file(expected['source_commit'], expected['workflow_path'])
        need(workflow['blob'] == expected['workflow_blob'], 'independent-native-workflow-source-mismatch')
        jobs = self.github.jobs(expected['run_id'], expected['attempt'])
        selected = [j for j in jobs if j.get('id') == pins['job_id']]
        need(len(selected) == 1, 'independent-native-job-missing')
        job = selected[0]
        need(job.get('name') == pins['job_name'] and job.get('head_sha') == expected['source_commit']
             and job.get('conclusion') == 'success' and job.get('runner_group_name') == 'GitHub Actions'
             and job.get('labels') == ['ubuntu-24.04'], 'independent-job-not-reviewed-hosted-source')
        steps = job.get('steps')
        need(type(steps) is list and len({s.get('number') for s in steps}) == len(steps), 'independent-step-ambiguous')
        selected = [s for s in steps if s.get('number') == pins['upload_step']]
        need(len(selected) == 1 and selected[0].get('name') == pins['upload_name']
             and selected[0].get('conclusion') == 'success', 'independent-upload-step-unverified')
        # Reviewed source pins choose one aggregation job and one upload action.
        need(re.fullmatch('[A-Za-z0-9_-]+', pins['job_key'])
             and re.fullmatch('actions/upload-artifact@[0-9a-f]{40}', pins['upload_action']),
             'independent-upload-source-pins-invalid')
        text = workflow['content'].decode()
        sections = re.split(r'(?m)^  ([A-Za-z0-9_-]+):\s*$', text)
        blocks = {sections[i]: sections[i + 1] for i in range(1, len(sections), 2)}
        need(len(blocks) == (len(sections) - 1) // 2, 'duplicate-independent-source-job')
        block = blocks.get(pins['job_key'], '')
        need(block.count('uses: actions/upload-artifact@') == 1 and ('uses: ' + pins['upload_action']) in block,
             'independent-upload-source-not-unique')
        artifact = self.github._single('actions/artifacts/' + str(expected['artifact_id']))
        link = artifact.get('workflow_run', {})
        need(artifact.get('id') == expected['artifact_id'] and artifact.get('name') == pins['artifact_name']
             and artifact.get('expired') is False and link.get('id') == expected['run_id']
             and link.get('head_sha') == expected['source_commit'] and link.get('repository_id') == RID,
             'independent-artifact-native-linkage-mismatch')
        raw = self.get(f"actions/artifacts/{expected['artifact_id']}/zip", binary=True)
        need(type(raw) is bytes and digest(raw) == expected['sha256']
             and artifact.get('digest', 'sha256:' + digest(raw)) == 'sha256:' + digest(raw),
             'independent-proof-raw-digest-mismatch')
        log = self.get(f"actions/jobs/{pins['job_id']}/logs", binary=True).decode('utf-8')
        hashes = re.findall(r'SHA256 digest of uploaded artifact zip is ([0-9a-f]{64})', log)
        finals = re.findall(r'Artifact (\S+)\.zip successfully finalized\. Artifact ID ([0-9]+)', log)
        need(hashes == [digest(raw)] and finals == [(pins['artifact_name'], str(expected['artifact_id']))],
             'independent-native-upload-log-mismatch')
        members = runtime('lab_queue_live')._decode_archive
        # Existing strict ZIP safety validator is reused without creating a
        # native producer_job_id/run_attempt field on artifact metadata.
        observation = members(raw, 'observation.json', {'observation.json'})
        need(type(observation) is dict and observation.get('facts_checked') is True
             and observation.get('control_source') == expected['source_commit']
             and observation.get('subject') == {k: approval[k] for k in ('number', 'base', 'head', 'target')}
             and observation.get('actual_draft') is False and observation.get('complete_candidate_proof') is True
             and observation.get('allows_new_write') is False and observation.get('release_authority') is False
             and observation.get('installation_authority') is False,
             'independent-output-not-exact-readonly-observation')

    def read_verified_upgrade_authority(self, number):
        approval = self.policy_approval(number)
        before = self.subject(approval)
        need(main_ref(self.github.main_ref()) == approval['base'], 'actual-native-base-moved')
        installed = load_upgrade_approval(self.common / 'merge-queue', approval)
        need(installed.get('action') == 'approved', 'installed-private-approval-missing-or-moved')
        proof = self.policy['native_proof']  # Missing complete policy never reserves.
        need(type(proof) is dict and set(proof) == {
            'source_paths', 'baseline_pins', 'candidate_pins', 'review_pins', 'independent_execution',
            'control_runtime', 'control_resources'},
            'complete-native-proof-policy-required')
        need(self.git(['rev-parse', 'HEAD']).strip() == approval['base']
             and self.git(['status', '--porcelain']).strip() == '', 'old-installed-source-not-exact-clean')
        source = Source(self.get)
        self.verify_control_runtime(approval, proof, source)
        for phase, commit in (('old', approval['base']), ('new', approval['head'])):
            manifest, _ = source.manifest(commit, proof['source_paths'][phase])
            need(manifest['digest'] == approval[phase + '_manifest'], 'native-source-manifest-mismatch')
        self.independent_execution(approval, proof, source)
        self.verify_components(approval, proof, before)
        self.reviews(approval, proof['review_pins'])
        need(self.subject(approval) == before and main_ref(self.github.main_ref()) == approval['base'],
             'native-subject-or-main-moved-on-final-read')
        need(load_upgrade_approval(self.common / 'merge-queue', approval).get('approval') == approval,
             'private-approval-moved-on-final-read')
        return approval

    def verify_control_runtime(self, approval, proof, source):
        """Actual loaded control bytes match independently fixed S* Git objects."""
        pins = proof['control_runtime']
        modules = self.loaded_modules()
        need(type(pins) is dict and set(pins) == set(modules), 'complete-installed-control-runtime-pins-required')
        for name, module in modules.items():
            pin = pins[name]
            need(type(pin) is dict and set(pin) == {'path', 'sha256'} and sha(pin['sha256'], 64),
                 'invalid-installed-control-runtime-pin')
            entry = source.file(approval['proof']['source_commit'], pin['path'])
            need(Path(module.__file__).read_bytes() == entry['content']
                 and digest(entry['content']) == pin['sha256'], 'loaded-control-runtime-differs-from-independent-source')
        resources = proof['control_resources']
        need(type(resources) is dict and set(resources) == {'policy.json'}, 'installed-control-resources-not-exact')
        pin = resources['policy.json']
        need(type(pin) is dict and set(pin) == {'path', 'sha256'} and sha(pin['sha256'], 64),
             'invalid-control-resource-pin')
        entry = source.file(approval['proof']['source_commit'], pin['path'])
        need(Path(post_review.POLICY).read_bytes() == entry['content'] and digest(entry['content']) == pin['sha256'],
             'loaded-control-policy-resource-mismatch')

    def loaded_modules(self):
        return {'mq_lab_cutover_native': sys.modules[__name__],
                   'mq_lab_control_install': installer,
                   'mq_lab_journal_v2': sys.modules[ReleaseJournalV2.__module__],
                   'mq_lab_control_authorization': sys.modules[load_upgrade_approval.__module__],
                   'mq_lab_control_manifest': sys.modules[build_manifest.__module__],
                   'mq_lab_control_plan': sys.modules[validate_independent_old_control_baseline.__module__],
                   'mq_lab_control_receipt': sys.modules[validate_public_candidate_proof.__module__],
                   'mq_queue': sys.modules['mq_queue'], 'issue_gate': sys.modules['issue_gate'],
                   'post_review': post_review, 'lab_queue_git_writer': self.writer_module,
                   'lab_queue_live': runtime('lab_queue_live'), 'lab_main_health': self.health_module}

    def verify_components(self, approval, proof, pr):
        bp, cp = proof['baseline_pins'], proof['candidate_pins']
        for key in ('repository', 'repository_id', 'number', 'base', 'head', 'target', 'old_manifest', 'new_manifest'):
            need(type(cp[key]) is type(approval[key]) and cp[key] == approval[key], 'candidate-policy-not-exact-approval')
        need(bp['old_base'] == approval['base'] and bp['old_manifest'] == approval['old_manifest']
             and cp['independent_source'] == approval['proof']['source_commit'], 'independent-component-policy-mismatch')
        run = self.github._single(f"actions/runs/{bp['run_id']}/attempts/{bp['run_attempt']}")
        artifacts = self.github._pages(f"actions/runs/{bp['run_id']}/artifacts?per_page=100", 'artifacts')
        selected = [r for r in artifacts if r.get('id') == bp['artifact_id']]
        need(len(selected) == 1, 'independent-baseline-artifact-missing')
        raw = self.get(f"actions/artifacts/{bp['artifact_id']}/zip", binary=True)
        runtime('lab_queue_live').decode_health(raw)
        # Baseline witness is raw evidence: rebuilding JSON would change a pin.
        # Read its actual member bytes after strict existing archive validation.
        with zipfile.ZipFile(io.BytesIO(raw)) as archive:
            witness_raw = archive.read('health.json')
        artifact = dict(selected[0], raw=witness_raw, raw_sha256=digest(witness_raw))
        native = dict(repository=self.github.repository(), current_main=self.github.main_ref(),
            source=dict(commit=bp['workflow']['source_commit'], path=bp['workflow']['path'],
                        blob=Source(self.get).file(bp['workflow']['source_commit'], bp['workflow']['path'])['blob'],
                        independent=bp['workflow']['source_commit'] != approval['head']),
            run=run, latest=self.latest(bp['workflow']['path'], run['head_sha'], 'push', bp.get('branch')),
            jobs=self.github.jobs(bp['run_id'], bp['run_attempt']), artifacts=[artifact], witness_raw=witness_raw)
        need(validate_independent_old_control_baseline(native, bp).get('action') == 'proposal', 'native-baseline-held')
        observed = dict(repository=self.github.repository(), current_pr=pr,
            source_evidence={k: cp[k] for k in ('independent_source', 'old_control_commit', 'old_manifest', 'new_manifest', 'workflow')},
            run=self.github._single('actions/runs/' + str(cp['run_id'])),
            latest=self.latest(cp['workflow']['path'], approval['head'], 'pull_request'),
            jobs=self.github.jobs(cp['run_id'], cp['run_attempt']),
            artifacts=self.github._pages(f"actions/runs/{cp['run_id']}/artifacts?per_page=100", 'artifacts'),
            download=dict(artifact_id=cp['artifact_id'], run_id=cp['run_id'], attempt=cp['run_attempt'],
                          zip_bytes=self.get(f"actions/artifacts/{cp['artifact_id']}/zip", binary=True)))
        need(Source(self.get).file(approval['head'], cp['workflow']['path'])['blob']
             == cp['workflow']['source_blob'], 'actual-candidate-workflow-blob-mismatch')
        associations = observed['run'].get('pull_requests')
        need(type(associations) is list, 'native-candidate-PR-association-missing')
        associations = [r for r in associations if type(r.get('number')) is int and r['number'] == approval['number']]
        need(len(associations) == 1 and all(
            associations[0].get(side, {}).get('sha') == approval[side]
            and type(associations[0].get(side, {}).get('repo', {}).get('id')) is int
            and associations[0][side]['repo']['id'] == RID for side in ('head', 'base')),
            'native-candidate-run-not-exact-PR-base-head')
        for key in ('skip_cases', 'exact_skip_reference'):
            if key in self.policy:
                observed[key] = copy.deepcopy(self.policy[key])
        need(validate_public_candidate_proof(cp, observed).get('action') == 'verified-readonly', 'native-candidate-held')

    def latest(self, workflow, head, event, branch=None):
        path = f'actions/runs?event={event}'
        if branch:
            path += '&branch=' + branch
        path += '&head_sha=' + head + '&per_page=100'
        runs = self.github._pages(path, 'workflow_runs')
        selected = []
        for run in runs:
            if run.get('event') == event and run.get('path') == workflow and run.get('head_sha') == head:
                repository(run.get('repository'))
                need(type(run.get('id')) is int and run['id'] > 0
                     and type(run.get('run_attempt')) is int and run['run_attempt'] > 0, 'invalid-native-run-identity')
                selected.append((run['id'], run['run_attempt']))
        need(selected and len(set(selected)) == len(selected), 'missing-or-ambiguous-latest-native-run')
        run, attempt = max(selected)
        return dict(run_id=run, run_attempt=attempt)

    def reread_write_boundary(self, approval):
        need(self.read_verified_upgrade_authority(approval['number']) == approval, 'native-boundary-approval-moved')
        return copy.deepcopy(approval)

    def authorize(self, intent):
        # No Queue public methods or lock acquisition: installer holds leaf FDs.
        approval = self.sending
        if approval is None or intent != {k: approval[k] for k in IDENTITY}:
            return False
        current = self.read_verified_upgrade_authority(approval['number'])
        record = self.journal.load_upgrade(approval['number'])
        return current == approval and record is not None and record['approval'] == approval and record['phase'] == 'reserved'

    def send(self, approval, *, expected_base):
        need(expected_base == approval['base'] and self.sending is None, 'native-send-not-exact-or-reentered')
        self.sending = copy.deepcopy(approval)
        try:
            return self.writer.send({k: approval[k] for k in IDENTITY})
        finally:
            self.sending = None

    def read_merged(self, approval):
        intent = {k: approval[k] for k in IDENTITY}
        result = self.writer.readback(intent)
        need(type(result) is dict and result.get('merged') is True, 'native-effect-not-confirmed')
        repository(self.github.repository())
        self.subject(approval, merged=True)
        commit = self.github._single('commits/' + result['merge_sha'])
        parents = [p.get('sha') for p in commit.get('parents', [])]
        need(commit.get('sha') == result['merge_sha'] and parents == [approval['base'], approval['head']],
             'native-ordered-parents-moved')
        tip = main_ref(self.github.main_ref())
        if tip != result['merge_sha']:
            compared = self.github._single(f"compare/{result['merge_sha']}...{tip}")
            need(compared.get('status') == 'ahead'
                 and compared.get('merge_base_commit', {}).get('sha') == result['merge_sha'], 'native-main-no-longer-contains-merge')
        return dict(intent, repository=REPOSITORY, repository_id=RID, merged=True,
                    merge_sha=result['merge_sha'], merge_parents=parents, target_tip=tip, target_contains_merge=True)

    def read_fresh_successor(self, approval, merge):
        healthy = self.health_module.check_main_health(self.github)
        need(healthy.get('action') == 'healthy' and healthy.get('head_sha') == merge,
             'exact-new-native-main-health-unavailable')
        need(self.policy.get('installed_source_commit') == merge
             and self.git(['rev-parse', 'HEAD']).strip() == merge
             and self.git(['status', '--porcelain']).strip() == '', 'successor-not-actually-loaded-at-clean-merge')
        source = Source(self.get)
        paths = self.policy['native_proof']['source_paths']['new']
        manifest, entries = source.manifest(merge, paths)
        need(manifest['digest'] == approval['new_manifest'], 'actual-successor-source-manifest-mismatch')
        # Actual loaded runtime bytes must equal pinned new Git objects, not a
        # caller's loaded_commit flag. Flat hosted packaging preserves basenames.
        for name, module in self.loaded_modules().items():
            matches = [e for e in entries if Path(e['path']).name == name + '.py']
            need(len(matches) == 1 and Path(module.__file__).read_bytes() == matches[0]['content'],
                 'actual-loaded-successor-module-not-in-approved-manifest')
        policies = [entry for entry in entries if entry['path'] == 'policy.json']
        need(len(policies) == 1 and Path(post_review.POLICY).read_bytes() == policies[0]['content'],
             'actual-successor-policy-resource-not-in-approved-manifest')
        latest = self.latest('.github/workflows/lab-pr-ci.yml', merge, 'push', 'main')
        run = self.github._single('actions/runs/' + str(latest['run_id']))
        jobs = self.github.jobs(latest['run_id'], latest['run_attempt'])
        jobs = [j for j in jobs if j.get('name') == 'Lab main health']
        need(len(jobs) == 1, 'native-main-health-job-not-unique')
        artifacts = self.github.main_artifacts(latest['run_id'], latest['run_attempt'], merge)
        artifacts = [a for a in artifacts if a.get('name') == f"lab-main-health-{merge}-{latest['run_attempt']}"]
        need(len(artifacts) == 1, 'native-main-health-artifact-not-unique')
        artifact = artifacts[0]
        raw = self.get(f"actions/artifacts/{artifact['id']}/zip", binary=True)
        need(type(raw) is bytes and artifact.get('digest', 'sha256:' + digest(raw)) == 'sha256:' + digest(raw),
             'native-health-artifact-raw-digest-mismatch')
        payload = runtime('lab_queue_live').decode_health(raw)
        need(payload == artifact['health'], 'native-health-download-moved')
        payload = copy.deepcopy(payload)
        # Actual workflow environment identifiers are decimal strings. Only this
        # lossless normalization is allowed; native API identifiers stay integers.
        for key in ('run_id', 'run_attempt'):
            value = payload[key]
            if type(value) is str:
                need(re.fullmatch('[1-9][0-9]{0,18}', value), 'invalid-health-environment-ID')
                payload[key] = int(value)
        # Native response projections match the exact existing V2 adoption schema.
        run_keys = ('id', 'run_attempt', 'event', 'head_branch', 'head_sha', 'path', 'status', 'conclusion')
        job_keys = ('id', 'name', 'head_sha', 'conclusion', 'runner_group_name', 'labels')
        health = dict(run={k: run[k] for k in run_keys}, latest_run_id=latest['run_id'],
                      latest_attempt=latest['run_attempt'], job={k: jobs[0][k] for k in job_keys},
                      artifact={k: artifact[k] for k in ('id', 'name', 'expired')}, payload=payload)
        health['run']['repository'] = {k: repository(run['repository'])[k] for k in ('id', 'full_name', 'private')}
        health['artifact']['workflow_run'] = {k: artifact['workflow_run'][k] for k in ('id', 'head_sha', 'repository_id')}
        need(self.latest('.github/workflows/lab-pr-ci.yml', merge, 'push', 'main') == latest
             and main_ref(self.github.main_ref()) == merge, 'latest-health-or-main-moved-before-adoption')
        return dict(repository=REPOSITORY, repository_id=RID, upgrade_id=approval['upgrade_id'],
            number=approval['number'], base=approval['base'], head=approval['head'], target='main',
            merge_sha=merge, main_before=merge, main_after=merge, loaded_commit=merge,
            manifest_evidence=dict(old_manifest=approval['old_manifest'], new_manifest=approval['new_manifest'],
                                  loaded_manifest=manifest['digest'], source_commit=approval['proof']['source_commit'],
                                  workflow_blob=approval['proof']['workflow_blob']), health=health)


def run_native_upgrade(number, common_dir, independent_policy, get, git, *, transport_lease=None):
    """Explicit actual-native seam; existing durable pair always recovers first.

    No preparation/migration/admission or default API/Git implementation. The
    caller installs trusted policy/approval/source separately. Unknown send is
    reconciled by the existing installer and never grants another writer call.
    """
    try:
        need(type(number) is int and number > 0, 'invalid-native-upgrade-number')
        common = Path(common_dir)
        journal = ReleaseJournalV2(common / 'merge-queue/releases.sqlite3')
        ports = NativePorts(common, independent_policy, get, git, journal)
        return installer.run_control_upgrade_once(number, common, journal, ports, transport_lease=transport_lease)
    except Exception:
        return dict(action='hold', reason='native-upgrade-binding-unavailable-or-inexact')
