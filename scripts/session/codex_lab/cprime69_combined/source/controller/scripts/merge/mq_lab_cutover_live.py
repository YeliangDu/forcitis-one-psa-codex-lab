"""Hosted scratch collector using actual validators and test-owned raw IPC.

Pins, scopes and the complete source inventory are independently frozen fixture
inputs. There is no GitHub client/default URL, receipt-to-authority conversion,
or native installation binding. Explicit private approval remains separate.
"""
import copy
import hashlib
import io
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import zipfile

from mq_lab_control_authorization import load_upgrade_approval
from mq_lab_control_manifest import build_manifest
from mq_lab_control_plan import validate_independent_old_control_baseline
from mq_lab_control_receipt import validate_public_candidate_proof
from mq_lab_cutover_channel import decode
import post_review


def require(ok, reason):
    if not ok:
        raise ValueError(reason)


def git(root, *arguments):
    result = subprocess.run(['git', '--no-replace-objects', '-C', str(root), *arguments],
                            capture_output=True, timeout=10, check=True)
    return result.stdout


def actual_main(root):
    value = git(root, 'rev-parse', '--verify', 'refs/heads/main').decode().strip()
    require(re.fullmatch('[0-9a-f]{40}', value), 'actual-main-unreadable')
    return value


def read_scope(request, name):
    """Exact independently instantiated raw scope; no wildcard/latest fallback."""
    approval = request['expected_approval']
    scope = {key: approval[key] for key in ('repository', 'repository_id', 'number', 'base', 'head')}
    scope['resource'] = name
    if name.startswith('baseline.') or name == 'baseline_witness':
        pins = request['baseline_pins']
        scope.update(source_commit=pins['workflow']['source_commit'], run_id=pins['run_id'],
                     attempt=pins['run_attempt'], job_id=pins['job_id'],
                     upload_step=pins['step_number'], artifact_id=pins['artifact_id'])
    elif name.startswith('candidate.') or name == 'candidate_zip':
        pins = request['candidate_pins']
        scope.update(source_commit=pins['independent_source'], run_id=pins['run_id'],
                     attempt=pins['run_attempt'], job_id=pins['aggregation_job_id'],
                     upload_step=pins['upload_step'], artifact_id=pins['artifact_id'])
    elif name == 'preparation':
        scope.update(common_dir=request['common_dir'], backup_path=request['backup_path'],
                     reviewed_v1_sha256=request['reviewed_v1_sha256'])
    elif name == 'merged':
        scope['merge_sha'] = request['successor_merge']
    elif name == 'successor_health':
        pins = request['successor_health_pins']
        scope.update(merge_sha=request['successor_merge'], source_commit=request['successor_merge'],
                     run_id=pins['run_id'], attempt=pins['attempt'],
                     job_id=pins['job_id'], artifact_id=pins['artifact_id'])
    else:
        require(name in ('reviews', 'registry'), 'unknown-raw-resource')
    return scope


def manifest(root, commit, paths):
    require(type(paths) is list and paths and len(paths) == len(set(paths)),
            'independently-fixed-complete-source-inventory-required')
    listed = {}
    for row in git(root, 'ls-tree', '-r', '-z', commit).split(b'\0'):
        if not row:
            continue
        meta, name = row.split(b'\t', 1)
        mode, kind, blob = meta.decode().split(' ')
        path = name.decode('utf-8')
        require(path not in listed, 'duplicate-source-tree-path')
        listed[path] = mode, kind, blob
    entries = []
    for path in paths:
        require(path in listed, 'source-inventory-file-missing-from-commit')
        mode, kind, blob = listed[path]
        require(kind == 'blob' and mode in ('100644', '100755'), 'nonregular-control-source')
        entries.append(dict(path=path, mode=mode, blob=blob,
                            content=git(root, 'show', commit + ':' + path)))
    return build_manifest(paths, entries)


class ControlLivePorts:
    def __init__(self, request, channel, *, successor=False):
        self.request, self.channel, self.successor = request, channel, successor
        self.root = Path(request['root'])
        self.expected = copy.deepcopy(request['expected_approval'])
        self.directory = Path(request['common_dir']) / 'merge-queue'

    def raw(self, name):
        # A raw key and its exact scope are selected by the independent fixture,
        # not copied from a candidate-produced trace or RPC response.
        record = self.request['reads'][name]
        require(type(record) is dict and set(record) == {'key', 'scope'}, 'raw-scope-not-fixed')
        scope = read_scope(self.request, name)
        require(record['key'] == name and json.dumps(record['scope'], sort_keys=True, allow_nan=False)
                == json.dumps(scope, sort_keys=True, allow_nan=False), 'raw-scope-not-exact-subject-run')
        return self.channel.read(name, scope)

    def json(self, name):
        return decode(self.raw(name))

    def _reviews(self, approval, current_pr):
        rows, registry = self.json('reviews'), self.json('registry')
        require(type(rows) is list and type(registry) is dict and type(registry.get('roles')) is list,
                'native-reviews-or-resident-registry-missing')
        identities = {}
        for row in registry['roles']:
            require(type(row) is dict and type(row.get('role')) is str and row['role'] not in identities
                    and type(row.get('id')) is str
                    and re.fullmatch('[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}', row['id']),
                    'invalid-resident-registry')
            identities[row['role']] = row['id']
        require(len(set(identities.values())) == len(identities), 'shared-resident-session')
        ids = [row.get('id') for row in rows if type(row) is dict]
        require(len(ids) == len(rows) and all(type(i) is int and i > 0 for i in ids)
                and len(set(ids)) == len(ids), 'duplicate-or-invalid-native-review-id')
        fields = post_review.load_policy(Path(__file__).resolve().parents[2] / 'policy.json')[0]['header_fields']
        owners = {approval['author']}
        observed = []
        require(approval['author'] in identities, 'unregistered-upgrade-author')
        require(type(current_pr.get('body')) is str and re.search(
            r'^Codex:\s*' + re.escape(identities[approval['author']]) + r'\s*$',
            current_pr['body'], re.M), 'missing-current-upgrade-author-session')
        for kind, expected in approval['reviews'].items():
            matching = []
            for row in rows:
                require(type(row) is dict, 'invalid-native-review-row')
                header = post_review.parse_header(row.get('body'), fields)
                if header and header['kind'] == kind and header['reviewer'] == expected['reviewer']:
                    matching.append((row, header))
            require(bool(matching), 'missing-assigned-current-review')
            row, header = max(matching, key=lambda item: item[0]['id'])
            require(type(row.get('id')) is int and row['id'] == expected['id']
                    and row.get('commit_id') == header['commit'] == approval['head']
                    and row.get('state') in ('COMMENTED', 'APPROVED')
                    and header['verdict'] == post_review.PASS,
                    'stale-dismissed-or-failing-native-review')
            owner = expected['reviewer'].split('-', 2)[2]
            require(owner in identities and owner not in owners, 'shared-author-review-session')
            owners.add(owner)
            require(hashlib.sha256(row['body'].encode()).hexdigest()
                    == self.request['review_body_sha256'][kind], 'review-body-not-independently-pinned')
            observed.append(dict(id=row['id'], kind=header['kind'], resident=owner,
                                 head=header['commit'], verdict='pass',
                                 body_sha256=hashlib.sha256(row['body'].encode()).hexdigest()))
        return sorted(observed, key=lambda row: ('首轮', '终审').index(row['kind']))

    def _publish_trace(self, trace):
        path = Path(self.request['proof_trace'])
        require(path.is_absolute() and path.parent == self.root and not path.is_symlink(),
                'proof-trace-not-exact-scratch-file')
        raw = json.dumps(trace, sort_keys=True, allow_nan=False).encode()
        require(len(raw) <= 65536, 'oversize-proof-trace')
        try:
            fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
        except FileExistsError:
            fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
            with os.fdopen(fd, 'rb') as stream:
                info = os.fstat(stream.fileno())
                require(stat.S_ISREG(info.st_mode) and info.st_uid == os.geteuid()
                        and info.st_nlink == 1 and info.st_size <= 65536
                        and stream.read(65537) == raw, 'previous-proof-trace-moved')
        else:
            with os.fdopen(fd, 'wb') as stream:
                stream.write(raw)
                stream.flush()
                os.fsync(stream.fileno())

    def read_verified_upgrade_authority(self, number):
        approval = self.expected
        require(number == approval['number'] and actual_main(self.root) == approval['base'], 'old-base-moved')
        baseline = {key: self.json('baseline.' + key) for key in
                    ('repository', 'current_main', 'source', 'run', 'latest', 'jobs', 'artifacts')}
        bp = self.request['baseline_pins']
        witness = self.raw('baseline_witness')
        # These are collector download-envelope fields, never native API fields.
        baseline['witness_raw'] = witness
        require(type(baseline.get('artifacts')) is list and len(baseline['artifacts']) == 1,
                'ambiguous-baseline-artifact')
        baseline['artifacts'][0]['raw'] = witness
        baseline['artifacts'][0]['raw_sha256'] = hashlib.sha256(witness).hexdigest()
        checked_baseline = validate_independent_old_control_baseline(baseline, bp)
        require(checked_baseline.get('action') == 'proposal', 'independent-baseline-held')
        candidate = {key: self.json('candidate.' + key) for key in
                     ('repository', 'current_pr', 'source_evidence', 'run', 'latest', 'jobs', 'artifacts')}
        cp = self.request['candidate_pins']
        candidate['download'] = dict(artifact_id=cp['artifact_id'], run_id=cp['run_id'],
                                     attempt=cp['run_attempt'])
        for optional in ('skip_cases', 'exact_skip_reference'):
            if ((optional == 'exact_skip_reference' and optional in cp)
                    or (optional == 'skip_cases' and 'expected_skip_cases' in cp)):
                candidate[optional] = self.json('candidate.' + optional)
        candidate['download']['zip_bytes'] = self.raw('candidate_zip')
        checked_candidate = validate_public_candidate_proof(cp, candidate)
        require(checked_candidate.get('action') == 'verified-readonly', 'native-public-candidate-held')
        # Keep both predicates' false authority fields unchanged. A separate
        # installed explicit record, source closure and review checks follow.
        for key in ('repository', 'repository_id', 'number', 'base', 'head', 'target',
                    'old_manifest', 'new_manifest'):
            require(type(cp[key]) is type(approval[key]) and cp[key] == approval[key],
                    'candidate-does-not-match-explicit-upgrade')
        require(bp['old_base'] == cp['base'] and bp['old_manifest'] == cp['old_manifest'],
                'baseline-candidate-binding-mismatch')
        independent = self.request['independent_workflow']
        require(type(independent) is dict and set(independent) == {'path', 'blob'}
                and git(self.root, 'rev-parse', '--verify', cp['independent_source'] + ':'
                        + independent['path']).decode().strip() == independent['blob'],
                'independent-proof-workflow-not-in-pinned-source')
        proof = approval['proof']
        require(proof == dict(source_commit=cp['independent_source'],
                    workflow_path=self.request['independent_workflow']['path'],
                    workflow_blob=self.request['independent_workflow']['blob'],
                    run_id=cp['run_id'], attempt=cp['run_attempt'], artifact_id=cp['artifact_id'],
                    sha256=cp['receipt_sha256']), 'explicit-independent-proof-binding-mismatch')
        computed = {}
        for phase, commit, expected in (('old', approval['base'], approval['old_manifest']),
                                        ('new', approval['head'], approval['new_manifest'])):
            computed[phase] = manifest(self.root, commit, self.request['source_paths'][phase])['digest']
            require(computed[phase] == expected,
                    'actual-Git-source-manifest-mismatch')
        reviews = self._reviews(approval, candidate['current_pr'])
        installed = load_upgrade_approval(self.directory, approval)
        require(installed.get('action') == 'approved', 'explicit-private-approval-missing-or-moved')
        require(actual_main(self.root) == approval['base'], 'main-moved-during-authority-read')
        # Trace fields come from parsed, validated native/raw observations. Never
        # copy the independent expected trace dictionary or retain fake-green
        # data after a failed validator. ZIP was already strictly checked above.
        zipped = candidate['download']['zip_bytes']
        with zipfile.ZipFile(io.BytesIO(zipped)) as archive:
            receipt_raw = archive.read('receipt.json')
        receipt = decode(receipt_raw)
        job = next(row for row in candidate['jobs'] if row['id'] == cp['aggregation_job_id'])
        step = next(row for row in job['steps'] if row['number'] == cp['upload_step'])
        artifact = next(row for row in candidate['artifacts'] if row['id'] == cp['artifact_id'])
        self._publish_trace(dict(repository_id=candidate['repository']['id'],
            base=candidate['current_pr']['base']['sha'], head=candidate['current_pr']['head']['sha'],
            source_commit=candidate['source_evidence']['independent_source'],
            old_manifest=computed['old'], new_manifest=computed['new'],
            run_id=candidate['run']['id'], attempt=candidate['run']['run_attempt'],
            latest_run_id=candidate['latest']['run_id'], latest_attempt=candidate['latest']['run_attempt'],
            job_id=job['id'], upload_step=step['number'], artifact_id=artifact['id'],
            zip_sha256=hashlib.sha256(zipped).hexdigest(),
            receipt_sha256=hashlib.sha256(receipt_raw).hexdigest(),
            lanes={key: value['proof'] for key, value in receipt['lanes'].items()}, reviews=reviews))
        return installed['approval']

    def reread_write_boundary(self, approval):
        require(approval == self.expected, 'boundary-approval-moved')
        return self.read_verified_upgrade_authority(approval['number'])

    def send(self, approval, *, expected_base):
        require(not self.successor and approval == self.expected and expected_base == approval['base'],
                'protected-send-not-exact-old-authority')
        # Parent prepares the scratch merge object, then delegates the ONLY ref
        # CAS to HostProbe/RecoveryProbe while this child holds the four locks.
        # A killed child never receives a success reply. No retry here.
        return self.channel.call('protected-cas', approval=approval, expected_base=expected_base)

    def read_merged(self, approval):
        proof = self.json('merged')
        merge = proof['merge_sha']
        require(re.fullmatch('[0-9a-f]{40}', merge) and merge == self.request['successor_merge'],
                'invalid-readback-merge')
        parents = [line[7:].decode() for line in git(self.root, 'cat-file', '-p', merge).splitlines()
                   if line.startswith(b'parent ')]
        require(parents == [approval['base'], approval['head']]
                and proof['merge_parents'] == parents and actual_main(self.root) == merge
                and proof['target_tip'] == merge, 'actual-parents-or-ref-not-exact')
        return proof

    def read_fresh_successor(self, approval, merge):
        require(self.successor, 'old-process-cannot-adopt-successor')
        source_root = Path(__file__).resolve().parents[2]
        require(git(source_root, 'rev-parse', '--verify', 'HEAD').decode().strip() == merge
                and actual_main(self.root) == merge, 'successor-not-loaded-at-actual-merge')
        require(manifest(self.root, merge, self.request['source_paths']['new'])['digest']
                == approval['new_manifest'], 'successor-manifest-not-approved')
        for path in self.request['source_paths']['new']:
            require((source_root / path).is_file()
                    and (source_root / path).read_bytes() == git(self.root, 'show', merge + ':' + path),
                    'successor-loaded-source-bytes-moved')
        health = self.json('successor_health')
        pins, native = self.request['successor_health_pins'], health['health']
        require(native['run']['id'] == pins['run_id'] and native['run']['run_attempt'] == pins['attempt']
                and native['job']['id'] == pins['job_id'] and native['artifact']['id'] == pins['artifact_id'],
                'successor-health-not-exact-raw-scope')
        require(health['loaded_commit'] == merge and health['main_before'] == health['main_after'] == merge
                and actual_main(self.root) == merge, 'successor-health-ref-moved')
        return health
