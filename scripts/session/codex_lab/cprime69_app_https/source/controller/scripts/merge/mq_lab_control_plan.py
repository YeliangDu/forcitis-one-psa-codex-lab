"""Readonly upgrade-plan preflight, deliberately unbound to native authority.

Initial failure-only entry. Read callbacks supply untrusted snapshots, not a
capability to verify their own provenance. Even fully matching data yields HOLD
until separately reviewed native-baseline/installation verification is bound.
No normal/bootstrap fallback, filesystem, queue/journal/writer or send callable.
"""
import hashlib
import json
from pathlib import PurePosixPath
import re


REPOSITORY = 'YeliangDu/forcitis-one-psa-codex-lab'
REPOSITORY_ID = 1449415666
BASELINE_FIELDS = {'version', 'kind', 'repository', 'repository_id', 'old_base',
                   'old_control_commit', 'old_manifest', 'workflow', 'run_id',
                   'run_attempt', 'artifact_id', 'receipt_sha256'}
INPUT_FIELDS = {'repository', 'main', 'candidate', 'approval', 'old_control_commit',
                'baseline_sha256', 'old_paths', 'old_entries', 'new_paths', 'new_entries'}


def _require(value, reason):
    if not value:
        raise ValueError(reason)


def _sha(value, length=40):
    return type(value) is str and re.fullmatch('[0-9a-f]{' + str(length) + '}', value) is not None


def _positive(value):
    return type(value) is int and 0 < value <= 2**63 - 1


def _unique(pairs):
    value = {}
    for key, item in pairs:
        _require(key not in value, 'duplicate-baseline-JSON-key')
        value[key] = item
    return value


def _constant(_value):
    raise ValueError('nonfinite-baseline-JSON')


def _public_repository(value):
    _require(type(value) is dict and value.get('full_name') == REPOSITORY
             and type(value.get('id')) is int and value['id'] == REPOSITORY_ID
             and value.get('private') is False
             and ('visibility' not in value or value['visibility'] == 'public')
             and ('fork' not in value or value['fork'] is False), 'wrong-exact-public-upgrade-repository')


def _hold(reason, proposal=None):
    result = dict(action='hold', reason=reason, mode='control-upgrade-plan', allows_new_write=False)
    if proposal is not None:
        result['unverified_proposal'] = proposal
    return result


def _baseline(raw):
    _require(type(raw) is bytes and 0 < len(raw) <= 64 * 1024, 'invalid-independent-old-control-baseline-bytes')
    value = json.loads(raw.decode('utf-8'), object_pairs_hook=_unique, parse_constant=_constant)
    _require(type(value) is dict and set(value) == BASELINE_FIELDS, 'invalid-independent-old-control-baseline-schema')
    _require(type(value['version']) is int and value['version'] == 1
             and value['kind'] == 'independent-public-old-control-baseline', 'old-or-unscoped-independent-baseline')
    _require(value['repository'] == REPOSITORY and type(value['repository_id']) is int
             and value['repository_id'] == REPOSITORY_ID, 'wrong-independent-baseline-repository')
    _require(_sha(value['old_base']) and _sha(value['old_control_commit'])
             and _sha(value['old_manifest'], 64) and _sha(value['receipt_sha256'], 64)
             and all(_positive(value[key]) for key in ('run_id', 'run_attempt', 'artifact_id')),
             'invalid-independent-baseline-identities')
    workflow = value['workflow']
    _require(type(workflow) is dict and set(workflow) == {'source_commit', 'path', 'blob'}
             and _sha(workflow['source_commit']) and _sha(workflow['blob'])
             and type(workflow['path']) is str
             and re.fullmatch(r'\.github/workflows/[A-Za-z0-9_-][A-Za-z0-9._-]*\.ya?ml', workflow['path']),
             'invalid-independent-baseline-workflow')
    return value


def _manifest(paths, entries):
    _require(type(paths) is list and 0 < len(paths) <= 256
             and type(entries) is list and len(entries) == len(paths), 'invalid-upgrade-manifest-input')
    total = 0
    for entry in entries:
        _require(type(entry) is dict and type(entry.get('content')) is bytes
                 and len(entry['content']) <= 1024 * 1024, 'invalid-upgrade-control-content')
        total += len(entry['content'])
    _require(total <= 4 * 1024 * 1024, 'upgrade-control-closure-too-large')
    # Existing inert pure component; no discovery/path import or file reads.
    from mq_lab_control_manifest import build_manifest
    return build_manifest(paths, entries)


def preflight_control_upgrade_plan(number, common_dir, ports):
    """Inspect only explicitly supplied readonly data, never reserve/install.

    Proposed snapshot schemas are for the next independent integration slice.
    Neither read method is authenticated merely by existing on a Python object.
    """
    try:
        _require(_positive(number), 'invalid-control-upgrade-number')
        raw_path = str(common_dir)
        path = PurePosixPath(raw_path)
        _require(path.is_absolute() and str(path) == raw_path and '..' not in path.parts
                 and path.name == '.git' and path.parent.name == REPOSITORY.split('/')[1],
                 'invalid-control-upgrade-common-path')
        read_baseline = getattr(ports, 'read_independent_old_control_baseline', None)
        if not callable(read_baseline):
            return _hold('missing-independent-old-control-baseline')
        try:
            raw = read_baseline(number)
        except Exception:
            return _hold('independent-old-control-baseline-unavailable')
        if raw is None:
            return _hold('missing-independent-old-control-baseline')
        baseline = _baseline(raw)
        read_inputs = getattr(ports, 'read_control_upgrade_plan_inputs', None)
        if not callable(read_inputs):
            return _hold('control-upgrade-readonly-input-port-unbound')
        try:
            inputs = read_inputs(number)
        except Exception:
            return _hold('control-upgrade-readonly-input-unavailable')
        _require(type(inputs) is dict and set(inputs) == INPUT_FIELDS, 'invalid-control-upgrade-input-schema')
        _public_repository(inputs['repository'])
        _require(_sha(inputs['baseline_sha256'], 64)
                 and inputs['baseline_sha256'] == hashlib.sha256(raw).hexdigest(), 'independent-baseline-digest-mismatch')
        main = inputs['main']
        _require(type(main) is dict and main.get('ref') == 'refs/heads/main'
                 and type(main.get('object')) is dict and _sha(main['object'].get('sha')), 'invalid-current-upgrade-main')
        base = main['object']['sha']
        _require(baseline['old_base'] == base and baseline['old_control_commit'] == inputs['old_control_commit'] == base,
                 'stale-independent-old-control-baseline')
        candidate = inputs['candidate']
        _require(type(candidate) is dict and type(candidate.get('number')) is int and candidate['number'] == number
                 and candidate.get('state') == 'open' and candidate.get('draft') is False
                 and candidate.get('merged') is False, 'invalid-current-upgrade-candidate')
        for side in ('head', 'base'):
            branch = candidate.get(side)
            _require(type(branch) is dict and _sha(branch.get('sha')), 'invalid-upgrade-branch')
            _public_repository(branch.get('repo'))
        _require(candidate['base'].get('ref') == 'main' and candidate['base']['sha'] == base,
                 'upgrade-candidate-base-is-not-current-main')
        _require(baseline['workflow']['source_commit'] != candidate['head']['sha'], 'candidate-selected-old-control-source')
        approval = inputs['approval']
        # Reuse the complete strict parser; never call its file loader here.
        from mq_lab_control_authorization import _validate
        _validate(approval)
        _require(approval['number'] == number and approval['head'] == candidate['head']['sha']
                 and approval['base'] == base and approval['target'] == 'main', 'upgrade-approval-subject-mismatch')
        old = _manifest(inputs['old_paths'], inputs['old_entries'])
        new = _manifest(inputs['new_paths'], inputs['new_entries'])
        _require(old['digest'] == baseline['old_manifest'] == approval['old_manifest']
                 and new['digest'] == approval['new_manifest'] and old['digest'] != new['digest'],
                 'upgrade-approved-manifest-transition-mismatch')
        approval_raw = json.dumps(approval, ensure_ascii=False, sort_keys=True,
                                  separators=(',', ':'), allow_nan=False).encode('utf-8')
        # Deliberately no verified/planned success. These data matches establish
        # neither an independently authenticated baseline nor installation trust.
        return _hold('independent-old-control-baseline-verifier-unbound', dict(
            number=number, base=base, head=approval['head'], target='main', upgrade_id=approval['upgrade_id'],
            old_manifest=old['digest'], new_manifest=new['digest'], baseline_sha256=inputs['baseline_sha256'],
            approval_sha256=hashlib.sha256(approval_raw).hexdigest(), provenance_verified=False))
    except (ValueError, TypeError, KeyError, UnicodeError) as error:
        return _hold(str(error) if isinstance(error, ValueError) else 'malformed-control-upgrade-readonly-input')
    except ImportError:
        return _hold('control-upgrade-pure-components-unavailable')
    except Exception:
        return _hold('control-upgrade-readonly-input-unavailable')


def validate_independent_old_control_baseline(native, trusted_pins):
    """Pure consistency check against externally selected old-control pins.

    Pins and scoped native collection must be authenticated outside this helper.
    A proposal is not ordinary health, upload-origin authentication, a bound
    preflight, release authority or an installation/reservation/send capability.
    """
    try:
        fields = {'repository', 'repository_id', 'old_base', 'old_control_commit', 'old_manifest',
                  'workflow', 'run_id', 'run_attempt', 'job_id', 'step_number', 'artifact_id',
                  'artifact_name', 'branch', 'required_checks'}
        optional = {'witness_sha256', 'job_name', 'step_name'}
        _require(type(trusted_pins) is dict and fields <= set(trusted_pins)
                 and set(trusted_pins) <= fields | optional, 'missing-or-unknown-independent-baseline-pins')
        pins = trusted_pins
        _require(pins['repository'] == REPOSITORY and type(pins['repository_id']) is int
                 and pins['repository_id'] == REPOSITORY_ID, 'wrong-pinned-baseline-repository')
        _require(_sha(pins['old_base']) and pins['old_control_commit'] == pins['old_base']
                 and _sha(pins['old_manifest'], 64), 'invalid-pinned-old-control-identity')
        workflow = pins['workflow']
        _require(type(workflow) is dict and set(workflow) == {'source_commit', 'path', 'blob'}
                 and _sha(workflow['source_commit']) and _sha(workflow['blob'])
                 and workflow['path'] == '.github/workflows/lab-22-public-old-main-witness.yml'
                 and pins['branch'] == 'lab/22-public-old-main-witness', 'wrong-pinned-baseline-workflow')
        _require(all(_positive(pins[key]) for key in ('run_id', 'run_attempt', 'job_id', 'step_number', 'artifact_id')),
                 'invalid-pinned-native-baseline-identities')
        _require(type(pins['artifact_name']) is str and 0 < len(pins['artifact_name']) <= 256,
                 'invalid-pinned-baseline-artifact-name')
        checks = pins['required_checks']
        _require(type(checks) is list and len(checks) == 3
                 and all(type(check) is str for check in checks)
                 and set(checks) == {'pin', 'light', 'secret'}, 'wrong-independent-baseline-required-checks')
        for key in ('job_name', 'step_name'):
            if key in pins:
                _require(type(pins[key]) is str and 0 < len(pins[key]) <= 256, 'invalid-pinned-native-name')
        if 'witness_sha256' in pins:
            _require(_sha(pins['witness_sha256'], 64), 'invalid-pinned-witness-digest')
        _require(type(native) is dict and set(native) == {
            'repository', 'current_main', 'source', 'run', 'latest', 'jobs', 'artifacts', 'witness_raw'},
            'missing-or-unknown-native-baseline-envelope')
        _public_repository(native['repository'])
        main = native['current_main']
        _require(type(main) is dict and main.get('ref') == 'refs/heads/main'
                 and type(main.get('object')) is dict and main['object'].get('sha') == pins['old_base'],
                 'old-main-moved-from-independent-pins')
        source = native['source']
        _require(type(source) is dict and set(source) == {'commit', 'path', 'blob', 'independent'}
                 and source['independent'] is True and source['commit'] == workflow['source_commit']
                 and source['path'] == workflow['path'] and source['blob'] == workflow['blob'],
                 'old-control-source-not-independently-pinned')
        run = native['run']
        _require(type(run) is dict and type(run.get('id')) is int and run['id'] == pins['run_id']
                 and type(run.get('run_attempt')) is int and run['run_attempt'] == pins['run_attempt']
                 and run.get('event') == 'push' and run.get('head_branch') == pins['branch']
                 and run.get('path') == workflow['path'] and run.get('head_sha') == workflow['source_commit']
                 and run.get('status') == 'completed' and run.get('conclusion') == 'success',
                 'wrong-failed-or-pending-independent-baseline-run')
        _public_repository(run.get('repository'))
        latest = native['latest']
        _require(type(latest) is dict and set(latest) == {'run_id', 'run_attempt'}
                 and type(latest['run_id']) is int and latest['run_id'] == pins['run_id']
                 and type(latest['run_attempt']) is int and latest['run_attempt'] == pins['run_attempt'],
                 'stale-independent-baseline-run-or-attempt')
        jobs = native['jobs']
        _require(type(jobs) is list and len(jobs) == 1, 'missing-or-ambiguous-independent-baseline-job')
        job = jobs[0]
        _require(type(job) is dict and type(job.get('id')) is int and job['id'] == pins['job_id']
                 and job.get('head_sha') == workflow['source_commit'] and job.get('conclusion') == 'success'
                 and ('status' not in job or job['status'] == 'completed')
                 and job.get('runner_group_name') == 'GitHub Actions' and job.get('labels') == ['ubuntu-24.04']
                 and type(job.get('name')) is str and bool(job['name']), 'unverified-independent-baseline-job')
        # Exact job ID is independently pinned to the scoped run/attempt. Some
        # collectors/API projections omit these fields: never manufacture them.
        for key in ('run_id', 'run_attempt'):
            if key in job:
                _require(type(job[key]) is int and job[key] == pins[key], 'foreign-baseline-job-scope')
        if 'job_name' in pins:
            _require(job['name'] == pins['job_name'], 'wrong-independent-baseline-job-name')
        steps = job.get('steps')
        _require(type(steps) is list and 0 < len(steps) <= 128, 'missing-or-overlong-baseline-job-steps')
        numbers, selected = set(), []
        for step in steps:
            _require(type(step) is dict and _positive(step.get('number'))
                     and step['number'] not in numbers, 'missing-or-duplicate-native-step-number')
            numbers.add(step['number'])
            if step['number'] == pins['step_number']:
                selected.append(step)
        _require(len(selected) == 1 and selected[0].get('conclusion') == 'success'
                 and ('status' not in selected[0] or selected[0]['status'] == 'completed')
                 and type(selected[0].get('name')) is str and bool(selected[0]['name']),
                 'independent-baseline-upload-step-not-successful')
        if 'step_name' in pins:
            _require(selected[0]['name'] == pins['step_name'], 'wrong-independent-baseline-upload-step-name')
        artifacts = native['artifacts']
        _require(type(artifacts) is list and len(artifacts) == 1, 'missing-or-ambiguous-independent-baseline-artifact')
        artifact = artifacts[0]
        _require(type(artifact) is dict and type(artifact.get('id')) is int and artifact['id'] == pins['artifact_id']
                 and artifact.get('name') == pins['artifact_name'] and artifact.get('expired') is False,
                 'wrong-or-expired-independent-baseline-artifact')
        _require(not {'job_id', 'step_number', 'run_attempt'} & set(artifact),
                 'invented-native-artifact-linkage-fields')
        # raw/raw_sha256 are explicit collector DOWNLOAD envelope fields, not
        # native artifact job_id/step_number/run_attempt or ZIP digest assertions.
        raw = native['witness_raw']
        _require(type(raw) is bytes and 0 < len(raw) <= 64 * 1024
                 and type(artifact.get('raw')) is bytes and artifact['raw'] == raw,
                 'independent-witness-raw-bytes-mismatch')
        digest = hashlib.sha256(raw).hexdigest()
        _require(_sha(artifact.get('raw_sha256'), 64) and artifact['raw_sha256'] == digest,
                 'independent-witness-download-digest-mismatch')
        if 'witness_sha256' in pins:
            _require(pins['witness_sha256'] == digest, 'independently-pinned-witness-digest-mismatch')
        association = artifact.get('workflow_run')
        if 'workflow_run' in artifact:
            _require(type(association) is dict and type(association.get('id')) is int
                     and association['id'] == pins['run_id'] and association.get('head_sha') == workflow['source_commit']
                     and type(association.get('repository_id')) is int and association['repository_id'] == REPOSITORY_ID,
                     'foreign-native-baseline-artifact-run')
            for key, expected in (('head_repository_id', REPOSITORY_ID), ('head_branch', pins['branch'])):
                if key in association:
                    _require(type(association[key]) is type(expected) and association[key] == expected,
                             'foreign-native-baseline-artifact-source')
        witness = json.loads(raw.decode('utf-8'), object_pairs_hook=_unique, parse_constant=_constant)
        _require(type(witness) is dict and witness.get('kind') == 'public-old-main-observation'
                 and witness.get('event') == 'isolated-cutover', 'ordinary-or-unknown-health-is-not-independent-baseline')
        # Explicitly support the actual bounded witness and the independently
        # frozen expanded v1 schema. Do not fill missing native receipt fields.
        actual_fields = {'kind', 'event', 'head_sha', 'observed_head', 'run_id', 'run_attempt',
                         'checks', 'baseline_ok', 'release_authority'}
        expanded_fields = {'version', 'kind', 'event', 'repository', 'repository_id', 'old_base',
                           'old_control_commit', 'old_manifest', 'workflow', 'run_id', 'run_attempt', 'checks'}
        if set(witness) == actual_fields:
            _require(witness['head_sha'] == pins['old_base'] and witness['observed_head'] == pins['old_base']
                     and witness['baseline_ok'] is True and witness['release_authority'] is False,
                     'actual-witness-base-or-readonly-scope-mismatch')
        elif set(witness) == expanded_fields:
            _require(type(witness['version']) is int and witness['version'] == 1
                     and witness['repository'] == REPOSITORY and type(witness['repository_id']) is int
                     and witness['repository_id'] == REPOSITORY_ID and witness['old_base'] == pins['old_base']
                     and witness['old_control_commit'] == pins['old_control_commit']
                     and witness['old_manifest'] == pins['old_manifest'] and witness['workflow'] == workflow,
                     'expanded-witness-independent-bindings-mismatch')
        else:
            raise ValueError('unknown-independent-witness-schema')
        for key in ('run_id', 'run_attempt'):
            value = witness[key]
            # Actual workflow environment values are decimal strings; native
            # run/job IDs above remain strictly integers, never bool/string IDs.
            if type(value) is str:
                _require(re.fullmatch(r'[1-9][0-9]{0,18}', value), 'invalid-witness-environment-id')
                value = int(value)
            _require(type(value) is int and value == pins[key], 'witness-run-or-attempt-mismatch')
        _require(type(witness['checks']) is dict and set(witness['checks']) == set(checks)
                 and all(witness['checks'][key] == 'success' for key in checks), 'incomplete-or-failed-independent-baseline-checks')
        return dict(action='proposal', reason='independently-pinned-native-baseline-consistency-only',
                    kind='public-old-main-observation', ordinary_main_health=False,
                    allows_new_write=False, release_authority=False, repository=REPOSITORY,
                    repository_id=REPOSITORY_ID, old_base=pins['old_base'], old_control_commit=pins['old_control_commit'],
                    old_manifest=pins['old_manifest'], workflow=dict(workflow), run_id=pins['run_id'],
                    run_attempt=pins['run_attempt'], job_id=pins['job_id'], artifact_id=pins['artifact_id'],
                    witness_sha256=digest)
    except (ValueError, TypeError, KeyError, UnicodeError, OverflowError, RecursionError) as error:
        result = _hold(str(error) if isinstance(error, ValueError) else 'malformed-native-independent-baseline')
        result['ordinary_main_health'] = False
        result['release_authority'] = False
        return result
