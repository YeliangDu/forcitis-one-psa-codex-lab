"""Inert validation of a collector-normalized independent control receipt.

Expected must originate in old trusted control. workflow_source, subject,
producer_job_id and run_attempt linkage are OUTER collector assertions, not
assumed GitHub-native fields. This function cannot prove complete pagination,
artifact transport/provenance, workflow isolation or real execution. Verified
means this supplied snapshot matches; it grants no approval or write authority.
No I/O, candidate imports, callbacks or production gate binding occurs here.
"""
import hashlib
import json
import re


REPOSITORY = "YeliangDu/forcitis-one-psa-codex-lab"
BINDINGS = {"repository_id", "repository", "number", "base", "head", "target",
            "old_control_commit", "old_manifest", "new_manifest", "workflow",
            "run_id", "run_attempt", "event", "subject"}
EXPECTED = BINDINGS | {"aggregation_job", "artifact", "required_checks"}
SUBJECT = {"repository_id", "number", "base", "head", "target"}
WORKFLOW = {"path", "source_commit", "source_blob"}
RUN = {"id", "run_attempt", "event", "head_sha", "repository", "status", "conclusion",
       "path", "workflow_source", "subject"}
JOB = {"id", "name", "run_id", "run_attempt", "head_sha", "conclusion", "runner_group_name", "labels"}
ARTIFACT = {"id", "name", "expired", "workflow_run", "producer_job_id", "content"}
RAW_LIMIT = 256 * 1024


def _require(condition, reason):
    if not condition:
        raise ValueError(reason)


def _shape(value, fields):
    _require(type(value) is dict and set(value) == fields, "missing-or-unknown-evidence-fields")


def _positive(value):
    return type(value) is int and value > 0


def _digest(value, size):
    return type(value) is str and re.fullmatch(r"[0-9a-f]{" + str(size) + r"}", value) is not None


def _text(value):
    return type(value) is str and bool(value) and value == value.strip() and value.isprintable()


def _event(value):
    return type(value) is str and value in ("workflow_dispatch", "workflow_run", "push", "pull_request")


def _workflow(value):
    _shape(value, WORKFLOW)
    path = value["path"]
    _require(_text(path) and path.startswith(".github/workflows/") and path.endswith((".yml", ".yaml"))
             and "\\" not in path and ":" not in path
             and all(p not in ("", ".", "..") for p in path.split("/")), "unsafe-independent-workflow-path")
    _require(_digest(value["source_commit"], 40) and _digest(value["source_blob"], 40), "invalid-workflow-source")


def _subject(value):
    _shape(value, SUBJECT)
    _require(_positive(value["repository_id"]) and _positive(value["number"])
             and _digest(value["base"], 40) and _digest(value["head"], 40)
             and type(value["target"]) is str and value["target"] == "main", "invalid-independent-subject")


def _bindings(value):
    _require(type(value["repository"]) is str and value["repository"] == REPOSITORY
             and all(_positive(value[k]) for k in ("repository_id", "number", "run_id", "run_attempt"))
             and all(_digest(value[k], 40) for k in ("base", "head", "old_control_commit"))
             and all(_digest(value[k], 64) for k in ("old_manifest", "new_manifest"))
             and type(value["target"]) is str and value["target"] == "main" and _event(value["event"]),
             "invalid-frozen-repository-identity-or-manifests")
    _workflow(value["workflow"])
    _subject(value["subject"])
    _require(all(value["subject"][k] == value[k] for k in SUBJECT), "subject-is-not-frozen-pr-identity")
    _require(value["workflow"]["source_commit"] != value["head"], "candidate-head-cannot-be-independent-source")


def _repository(value, expected, private=False):
    fields = {"id", "full_name"}
    _require(type(value) is dict and set(value) in (fields, fields | {"private"}), "invalid-repository-shape")
    _require(_positive(value["id"]) and value["id"] == expected["repository_id"]
             and type(value["full_name"]) is str and value["full_name"] == REPOSITORY,
             "foreign-repository")
    if private or "private" in value:
        _require(value.get("private") is True, "private-lab-proof-required")


def _expected(value):
    _shape(value, EXPECTED)
    _bindings(value)
    aggregate, artifact, checks = value["aggregation_job"], value["artifact"], value["required_checks"]
    _shape(aggregate, {"id", "name"})
    _shape(artifact, {"id", "name", "sha256"})
    _require(_positive(aggregate["id"]) and _text(aggregate["name"])
             and _positive(artifact["id"]) and _text(artifact["name"])
             and _digest(artifact["sha256"], 64), "invalid-frozen-job-or-artifact")
    _require(type(checks) is dict and bool(checks), "missing-frozen-check-selection")
    ids = {aggregate["id"]}
    for name, check in checks.items():
        _require(type(name) is str and re.fullmatch(r"[a-z][a-z0-9_-]{0,63}", name)
                 and name != aggregate["name"], "ambiguous-check-name")
        _shape(check, {"job_id", "selection_sha256", "tests"})
        _require(_positive(check["job_id"]) and check["job_id"] not in ids
                 and _digest(check["selection_sha256"], 64) and _positive(check["tests"]), "invalid-check-id-selection-or-count")
        ids.add(check["job_id"])


def _latest(expected, runs):
    _require(type(runs) is list and bool(runs), "missing-independent-runs")
    seen, related, identities = set(), {}, {}
    source = expected["workflow"]["source_commit"]
    for run in runs:
        _shape(run, RUN)
        _repository(run["repository"], expected)
        _workflow(run["workflow_source"])
        _subject(run["subject"])
        _require(_positive(run["id"]) and _positive(run["run_attempt"])
                 and _digest(run["head_sha"], 40) and _event(run["event"])
                 and _text(run["path"]) and _text(run["status"])
                 and (run["conclusion"] is None or _text(run["conclusion"])), "malformed-run-identity-or-state")
        key = (run["id"], run["run_attempt"])
        _require(key not in seen, "duplicate-run-attempt")
        seen.add(key)
        identity = (run["event"], run["head_sha"], run["repository"]["id"], run["path"],
                    run["workflow_source"], run["subject"])
        _require(run["id"] not in identities or identities[run["id"]] == identity,
                 "same-run-id-has-conflicting-source-or-subject")
        identities[run["id"]] = identity
        # A candidate-native run is not independent proof. A row claiming the
        # expected SOURCE in either provenance layer remains relevant and must
        # pass BOTH layers below; do not hide inconsistent newer claims.
        if (run["subject"] == expected["subject"]
                and (run["head_sha"] == source or run["workflow_source"]["source_commit"] == source)):
            related[key] = run
    _require(bool(related), "no-source-and-subject-bound-independent-run")
    key = max(related)
    run = related[key]
    _require(key == (expected["run_id"], expected["run_attempt"]), "newer-related-run-or-attempt-invalidates-frozen-proof")
    _require(run["head_sha"] == source and run["workflow_source"] == expected["workflow"]
             and run["path"] == expected["workflow"]["path"] and run["event"] == expected["event"]
             and run["status"] == "completed" and run["conclusion"] == "success", "independent-run-provenance-or-result-mismatch")


def _jobs(expected, jobs):
    _require(type(jobs) is list and bool(jobs), "missing-hosted-jobs")
    by_id, by_name = {}, {}
    source = expected["workflow"]["source_commit"]
    for job in jobs:
        _require(type(job) is dict and set(job) in (JOB, JOB | {"workflow_source"}), "invalid-job-shape")
        _require(_positive(job["id"]) and _text(job["name"])
                 and job["id"] not in by_id and job["name"] not in by_name, "duplicate-or-ambiguous-job")
        _require(_positive(job["run_id"]) and _positive(job["run_attempt"])
                 and (job["run_id"], job["run_attempt"]) == (expected["run_id"], expected["run_attempt"])
                 and _digest(job["head_sha"], 40) and job["head_sha"] == source, "job-not-bound-to-independent-source-run")
        _require(_text(job["conclusion"]) and _text(job["runner_group_name"])
                 and type(job["labels"]) is list and all(type(label) is str for label in job["labels"]), "malformed-job-result-or-runner")
        if "workflow_source" in job:
            _workflow(job["workflow_source"])
        by_id[job["id"]], by_name[job["name"]] = job, job
    required = [(expected["aggregation_job"]["id"], expected["aggregation_job"]["name"])]
    required += [(check["job_id"], name) for name, check in expected["required_checks"].items()]
    for job_id, name in required:
        job = by_id.get(job_id)
        _require(job is not None and by_name.get(name) is job and job["conclusion"] == "success"
                 and job["runner_group_name"] == "GitHub Actions" and job["labels"] == ["ubuntu-24.04"],
                 "missing-failed-or-unverified-required-hosted-job")
    aggregate = by_id[expected["aggregation_job"]["id"]]
    _require(aggregate.get("workflow_source") == expected["workflow"], "aggregation-not-from-frozen-old-workflow")


def _unique_json(pairs):
    value = {}
    for key, item in pairs:
        _require(key not in value, "duplicate-receipt-json-key")
        value[key] = item
    return value


def _invalid_constant(_value):
    raise ValueError("nonfinite-receipt-json")


def _payload(expected, artifacts):
    _require(type(artifacts) is list and bool(artifacts), "missing-downloaded-artifacts")
    seen, matching = set(), []
    for artifact in artifacts:
        _shape(artifact, ARTIFACT)
        _require(_positive(artifact["id"]) and artifact["id"] not in seen and _text(artifact["name"])
                 and type(artifact["expired"]) is bool, "invalid-or-duplicate-artifact")
        _shape(artifact["workflow_run"], {"id", "run_attempt"})
        _require(_positive(artifact["workflow_run"]["id"]) and _positive(artifact["workflow_run"]["run_attempt"])
                 and _positive(artifact["producer_job_id"]) and type(artifact["content"]) is bytes,
                 "malformed-outer-artifact-linkage-or-content")
        seen.add(artifact["id"])
        if artifact["id"] == expected["artifact"]["id"] or artifact["name"] == expected["artifact"]["name"]:
            matching.append(artifact)
    _require(len(matching) == 1, "ambiguous-artifact-identity")
    artifact = matching[0]
    _shape(artifact["workflow_run"], {"id", "run_attempt"})
    _require(artifact["id"] == expected["artifact"]["id"] and artifact["name"] == expected["artifact"]["name"]
             and artifact["expired"] is False and _positive(artifact["producer_job_id"])
             and artifact["producer_job_id"] == expected["aggregation_job"]["id"]
             and _positive(artifact["workflow_run"]["id"]) and _positive(artifact["workflow_run"]["run_attempt"])
             and (artifact["workflow_run"]["id"], artifact["workflow_run"]["run_attempt"])
             == (expected["run_id"], expected["run_attempt"]), "artifact-producer-or-run-linkage-mismatch")
    raw = artifact["content"]
    _require(type(raw) is bytes and 0 < len(raw) <= RAW_LIMIT, "missing-or-oversize-raw-receipt")
    _require(hashlib.sha256(raw).hexdigest() == expected["artifact"]["sha256"], "raw-artifact-digest-mismatch")
    payload = json.loads(raw.decode("utf-8"), object_pairs_hook=_unique_json, parse_constant=_invalid_constant)
    _shape(payload, BINDINGS | {"checks"})
    _bindings(payload)
    _require(all(payload[k] == expected[k] for k in BINDINGS), "receipt-identity-manifest-source-or-subject-mismatch")
    checks = payload["checks"]
    _require(type(checks) is dict and set(checks) == set(expected["required_checks"]), "missing-extra-or-ambiguous-checks")
    for name, frozen in expected["required_checks"].items():
        check = checks[name]
        _shape(check, {"job_id", "selection_sha256", "tests", "conclusion", "failures", "errors", "skipped"})
        _require(_positive(check["job_id"]) and check["job_id"] == frozen["job_id"]
                 and _digest(check["selection_sha256"], 64) and check["selection_sha256"] == frozen["selection_sha256"]
                 and _positive(check["tests"]) and check["tests"] == frozen["tests"]
                 and type(check["conclusion"]) is str and check["conclusion"] == "success"
                 and all(type(check[k]) is int and check[k] == 0 for k in ("failures", "errors", "skipped")),
                 "required-check-selection-count-or-outcome-mismatch")


def validate_control_receipt(expected, observed):
    """Validate a supplied frozen envelope; verified never authorizes a write."""
    try:
        _expected(expected)
        _shape(observed, {"repository", "runs", "jobs", "artifacts"})
        _repository(observed["repository"], expected, private=True)
        _latest(expected, observed["runs"])
        _jobs(expected, observed["jobs"])
        _payload(expected, observed["artifacts"])
        return dict(action="verified", reason="supplied-independent-source-subject-and-raw-receipt-match")
    except ValueError as error:
        return dict(action="hold", reason=str(error) or "malformed-control-receipt")
    except Exception:
        return dict(action="hold", reason="unavailable-or-malformed-normalized-control-evidence")


def _pc_repo(value):
    _require(type(value) is dict and type(value.get('id')) is int and value['id'] == 1449415666
             and value.get('full_name') == REPOSITORY and value.get('private') is False
             and ('visibility' not in value or value['visibility'] == 'public')
             and ('fork' not in value or value['fork'] is False), 'wrong-exact-public-candidate-repository')


def _pc_id(value):
    if type(value) is str:
        _require(re.fullmatch(r'[1-9][0-9]{0,18}', value), 'invalid-workflow-environment-id')
        value = int(value)
    _require(_positive(value) and value <= 2**63 - 1, 'invalid-native-or-workflow-id')
    return value


def _pc_zip(raw):
    import io
    import stat
    import zipfile

    _require(type(raw) is bytes and 0 < len(raw) <= 32 * 1024 * 1024, 'invalid-candidate-ZIP-size')
    result, total = {}, 0
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        entries = archive.infolist()
        _require(len(entries) == 2, 'unknown-candidate-ZIP-members')
        for entry in entries:
            name = entry.filename
            _require(entry.orig_filename == name and name in {'receipt.json', 'backend-summary.json'}
                     and name not in result and not entry.is_dir() and not entry.flag_bits & 1
                     and stat.S_IFMT(entry.external_attr >> 16) in (0, stat.S_IFREG)
                     and 0 < entry.file_size <= 256 * 1024, 'unsafe-or-duplicate-candidate-ZIP-member')
            total += entry.file_size
            _require(total <= 512 * 1024, 'candidate-ZIP-expansion-too-large')
            data = archive.read(entry)
            _require(len(data) == entry.file_size, 'incomplete-candidate-ZIP-member')
            result[name] = data
        _require(archive.testzip() is None, 'corrupt-candidate-ZIP')
    return result


def _pc_cases(value):
    _require(type(value) is list and len(value) <= 2048, 'missing-or-overlong-exact-skip-cases')
    cases = set()
    for case in value:
        _shape(case, {'class', 'method', 'reason'})
        _require(all(_text(case[key]) and len(case[key]) <= 1024 for key in case), 'invalid-exact-skip-case')
        key = tuple(case[field] for field in ('class', 'method', 'reason'))
        _require(key not in cases, 'duplicate-exact-skip-case')
        cases.add(key)
    return cases


def _pc_skip_count(pins, observed):
    if 'exact_skip_reference' not in pins and 'exact_skip_reference' not in observed:
        _require('expected_skip_cases' in pins and 'skip_cases' in observed, 'independent-skip-identities-required')
        expected, actual = _pc_cases(pins['expected_skip_cases']), _pc_cases(observed['skip_cases'])
        _require(expected == actual, 'exact-skip-case-or-reason-mismatch')
        return len(expected)
    _require('exact_skip_reference' in pins and 'exact_skip_reference' in observed
             and 'expected_skip_cases' not in pins and 'skip_cases' not in observed,
             'actual-class-exception-cannot-mix-with-synthetic-method-policy')
    ref = pins['exact_skip_reference']
    _shape(ref, {'witness', 'source_review'})
    _require(json.dumps(ref, sort_keys=True, allow_nan=False) ==
             json.dumps(observed['exact_skip_reference'], sort_keys=True, allow_nan=False), 'changed-exact-skip-reference')
    w = ref['witness']
    scope = pins.get('exact_skip_scope')
    _shape(scope, {'head','run_id','attempt','native_unit_job'})
    _require(scope['head'] == pins['head'] and scope['run_id'] == pins['run_id']
             and scope['attempt'] == pins['run_attempt'] and _positive(scope['native_unit_job']),
             'exact-skip-scope-not-this-independent-control-pin')
    _require(type(w) is dict and type(w.get('repo_id')) is int and w['repo_id'] == 1449415666
             and type(w.get('pr')) is int and w['pr'] == pins['number'] == 43
             and w.get('head') == pins['head'] == scope['head']
             and type(w.get('run')) is int and w['run'] == pins['run_id'] == scope['run_id']
             and type(w.get('attempt')) is int and w['attempt'] == pins['run_attempt'] == scope['attempt']
             and w.get('native_unit_job') == scope['native_unit_job'] and w.get('no_release_authority') is True
             and w.get('verified_only_for_exact_run') is True
             and _digest(w.get('selected_log_sha256'), 64), 'actual-skip-exception-is-not-this-exact-subject')
    expected_classes = [dict(class_name='com.forcitis.one.platform.identity.PendingIdentityRulesTest', tests=4, skips=4),
                        dict(class_name='com.forcitis.one.platform.approval.PendingApprovalRulesTest', tests=15, skips=15)]
    _require(w.get('skip_classes') == expected_classes, 'actual-skip-class-exception-changed')
    slots = w.get('slots')
    _require(type(slots) is dict and set(slots) == {'1', '2', '3', '4', 'unit'}, 'actual-skip-slot-set-changed')
    for slot, count in {'1':768, '2':570, '3':589, '4':555, 'unit':2316}.items():
        row = slots[slot]
        _shape(row, {'receipt_sha256', 'skipped', 'tests'})
        _require(type(row['tests']) is int and row['tests'] == count and type(row['skipped']) is int
                 and row['skipped'] == (19 if slot == 'unit' else 0) and _digest(row['receipt_sha256'], 64),
                 'actual-skip-slot-or-count-changed')
    expected_source = {
        'backend/src/test/java/com/forcitis/one/platform/identity/PendingIdentityRulesTest.java':
            ('312f9c4509f1e311d94bfa5d30c3777971005219f85ce25f6faae58027976398', 4),
        'backend/src/test/java/com/forcitis/one/platform/approval/PendingApprovalRulesTest.java':
            ('c71facb1a502bab8f18b65acde4f6c2d2a35725030f89de0cfcade1decc3b73e', 15)}
    sources, seen = ref['source_review'], set()
    _require(type(sources) is list and len(sources) == 2, 'missing-actual-skip-source-review')
    for row in sources:
        _shape(row, {'path', 'sha256', 'disabled_annotations', 'unchanged_from'})
        _require(row['path'] in expected_source and row['path'] not in seen, 'unknown-actual-skip-source')
        seen.add(row['path'])
        digest, count = expected_source[row['path']]
        _require(row['sha256'] == digest and type(row['disabled_annotations']) is int
                 and row['disabled_annotations'] == count
                 and row['unchanged_from'] == pins['base'] == '15511fad03b8d2ae38e3a337f24556bd9d9071c6',
                 'actual-skip-source-or-old-base-changed')
    return 19


def validate_public_candidate_proof(trusted_pins, observed):
    """Check a public candidate snapshot; external old-control pins stay trust.

    This does not establish execution of the independent aggregator, full source
    discovery, API collection completeness, review approval or installation.
    No legacy private receipt semantics or any normal/bootstrap entry changes.
    """
    try:
        required = {'repository', 'repository_id', 'number', 'base', 'head', 'target', 'old_control_commit',
                    'independent_source', 'old_manifest', 'new_manifest', 'required_lanes', 'workflow',
                    'run_id', 'run_attempt', 'aggregation_job_id', 'upload_step', 'artifact_id', 'artifact_name',
                    'zip_sha256', 'receipt_sha256', 'expected_tests'}
        optional = {'expected_skip_cases', 'exact_skip_reference', 'exact_skip_scope'}
        _require(type(trusted_pins) is dict and required <= set(trusted_pins)
                 and set(trusted_pins) <= required | optional, 'missing-or-unknown-old-control-public-pins')
        p = trusted_pins
        _require(p['repository'] == REPOSITORY and type(p['repository_id']) is int and p['repository_id'] == 1449415666
                 and p['target'] == 'main' and all(_digest(p[key],40) for key in ('base','head','old_control_commit','independent_source'))
                 and p['old_control_commit'] == p['base'] and p['independent_source'] != p['head']
                 and all(_digest(p[key],64) for key in ('old_manifest','new_manifest','zip_sha256','receipt_sha256'))
                 and p['old_manifest'] != p['new_manifest'], 'invalid-frozen-public-source-or-subject')
        _require(all(_positive(p[key]) and p[key] <= 2**63-1 for key in
                     ('number','run_id','run_attempt','aggregation_job_id','upload_step','artifact_id','expected_tests')),
                 'invalid-frozen-public-native-identities')
        workflow = p['workflow']
        _shape(workflow, {'path','source_commit','source_blob'})
        _require(workflow['path'] == '.github/workflows/lab-pr-ci.yml' and workflow['source_commit'] == p['head']
                 and _digest(workflow['source_blob'],40), 'candidate-workflow-is-not-exact-reviewed-head')
        lanes = {'light','secret','shadow','frontend','backend'}
        _require(type(p['required_lanes']) is list and len(p['required_lanes']) == 5
                 and all(type(x) is str for x in p['required_lanes']) and set(p['required_lanes']) == lanes,
                 'wrong-frozen-public-required-lanes')
        _require(p['artifact_name'] == f"lab-pr-{p['number']}-{p['head']}-{p['run_attempt']}", 'wrong-frozen-public-artifact-name')
        native_fields = {'repository','current_pr','source_evidence','run','latest','jobs','artifacts','download'}
        _require(type(observed) is dict and native_fields <= set(observed)
                 and set(observed) <= native_fields | {'skip_cases','exact_skip_reference'}, 'unknown-public-native-envelope')
        _pc_repo(observed['repository'])
        pr = observed['current_pr']
        _require(type(pr) is dict and type(pr.get('number')) is int and pr['number'] == p['number']
                 and pr.get('state') == 'open' and pr.get('draft') is False and pr.get('merged') is False,
                 'candidate-is-not-current-open-reviewed-PR')
        for side in ('head','base'):
            row = pr.get(side)
            _require(type(row) is dict and row.get('sha') == p[side], 'moving-public-candidate-head-or-base')
            _pc_repo(row.get('repo'))
        _require(pr['base'].get('ref') == 'main', 'wrong-public-candidate-target')
        source = observed['source_evidence']
        _shape(source, {'independent_source','old_control_commit','old_manifest','new_manifest','workflow'})
        _require(all(source[key] == p[key] for key in source), 'independent-public-source-or-manifest-mismatch')
        run = observed['run']
        _require(type(run) is dict and type(run.get('id')) is int and run['id'] == p['run_id']
                 and type(run.get('run_attempt')) is int and run['run_attempt'] == p['run_attempt']
                 and run.get('event') == 'pull_request' and run.get('path') == workflow['path']
                 and run.get('head_sha') == p['head'] and run.get('status') == 'completed'
                 and run.get('conclusion') == 'success', 'stale-failed-or-foreign-public-candidate-run')
        _pc_repo(run.get('repository'))
        latest = observed['latest']
        _shape(latest, {'run_id','run_attempt'})
        _require(type(latest['run_id']) is int and latest['run_id'] == p['run_id']
                 and type(latest['run_attempt']) is int and latest['run_attempt'] == p['run_attempt'], 'newer-public-run-or-attempt')
        jobs = observed['jobs']
        _require(type(jobs) is list and 0 < len(jobs) <= 64, 'missing-or-overlong-public-jobs')
        ids, selected = set(), []
        for job in jobs:
            _require(type(job) is dict and _positive(job.get('id')) and job['id'] not in ids, 'duplicate-public-job')
            ids.add(job['id'])
            if job['id'] == p['aggregation_job_id']:
                selected.append(job)
        _require(len(selected) == 1, 'missing-public-aggregation-job')
        job = selected[0]
        _require(job.get('name') == 'Lab exact-head verification' and job.get('head_sha') == p['head']
                 and job.get('conclusion') == 'success' and job.get('runner_group_name') == 'GitHub Actions'
                 and job.get('labels') == ['ubuntu-24.04'], 'unverified-public-aggregation-job')
        for key in ('run_id','run_attempt'):
            if key in job:
                _require(type(job[key]) is int and job[key] == p[key], 'wrong-native-public-job-scope')
        steps = job.get('steps')
        _require(type(steps) is list and 0 < len(steps) <= 128, 'missing-public-upload-steps')
        numbers, uploads = set(), []
        for step in steps:
            _require(type(step) is dict and _positive(step.get('number')) and step['number'] not in numbers,
                     'missing-or-duplicate-public-step-number')
            numbers.add(step['number'])
            if step['number'] == p['upload_step']:
                uploads.append(step)
        _require(len(uploads) == 1 and uploads[0].get('conclusion') == 'success'
                 and _text(uploads[0].get('name')), 'public-native-upload-not-successful')
        artifacts = observed['artifacts']
        _require(type(artifacts) is list and 0 < len(artifacts) <= 128, 'missing-public-artifacts')
        ids, matching = set(), []
        for artifact in artifacts:
            _require(type(artifact) is dict and _positive(artifact.get('id')) and artifact['id'] not in ids
                     and _text(artifact.get('name')) and type(artifact.get('expired')) is bool
                     and not {'producer_job_id','job_id','step_number','run_attempt'} & set(artifact), 'duplicate-or-invented-public-artifact')
            ids.add(artifact['id'])
            if artifact['id'] == p['artifact_id'] or artifact['name'] == p['artifact_name']:
                matching.append(artifact)
        _require(len(matching) == 1, 'missing-or-ambiguous-public-receipt-artifact')
        artifact = matching[0]
        _require(artifact['id'] == p['artifact_id'] and artifact['name'] == p['artifact_name']
                 and artifact['expired'] is False, 'wrong-or-expired-public-receipt-artifact')
        association = artifact.get('workflow_run')
        _require(type(association) is dict and type(association.get('id')) is int and association['id'] == p['run_id']
                 and association.get('head_sha') == p['head'] and type(association.get('repository_id')) is int
                 and association['repository_id'] == 1449415666 and 'run_attempt' not in association,
                 'wrong-or-invented-native-artifact-run-linkage')
        download = observed['download']
        _shape(download, {'artifact_id','run_id','attempt','zip_bytes'})
        _require(type(download['artifact_id']) is int and download['artifact_id'] == p['artifact_id']
                 and type(download['run_id']) is int and download['run_id'] == p['run_id']
                 and type(download['attempt']) is int and download['attempt'] == p['run_attempt']
                 and type(download['zip_bytes']) is bytes
                 and hashlib.sha256(download['zip_bytes']).hexdigest() == p['zip_sha256'], 'public-download-scope-or-ZIP-digest-mismatch')
        if 'digest' in artifact:
            _require(artifact['digest'] == 'sha256:' + p['zip_sha256'], 'native-public-ZIP-digest-mismatch')
        files = _pc_zip(download['zip_bytes'])
        _require(hashlib.sha256(files['receipt.json']).hexdigest() == p['receipt_sha256'], 'public-raw-receipt-digest-mismatch')
        receipt = json.loads(files['receipt.json'].decode('utf-8'), object_pairs_hook=_unique_json, parse_constant=_invalid_constant)
        _shape(receipt, {'head_sha','base_sha','run_id','run_attempt','lanes','ready','observed_head'})
        _require(receipt['head_sha'] == receipt['observed_head'] == p['head'] and receipt['base_sha'] == p['base']
                 and _pc_id(receipt['run_id']) == p['run_id'] and _pc_id(receipt['run_attempt']) == p['run_attempt']
                 and receipt['ready'] is True and type(receipt['lanes']) is dict and set(receipt['lanes']) == lanes,
                 'wrong-public-receipt-subject-or-lanes')
        for lane in lanes:
            _shape(receipt['lanes'][lane], {'applicability','proof','outcome'})
            _require(receipt['lanes'][lane] == dict(applicability='required',proof='passed',outcome='success'),
                     'missing-failed-or-skipped-public-required-lane')
        summary = json.loads(files['backend-summary.json'].decode('utf-8'), object_pairs_hook=_unique_json, parse_constant=_invalid_constant)
        _shape(summary, {'head','run','attempt','tests','failures','errors','skipped','integration_classes','slots'})
        skips = _pc_skip_count(p, observed)
        _require(summary['head'] == p['head'] and _pc_id(summary['run']) == p['run_id']
                 and _pc_id(summary['attempt']) == p['run_attempt'] and type(summary['tests']) is int
                 and summary['tests'] == p['expected_tests'] and _positive(summary['integration_classes'])
                 and summary['slots'] == ['1','2','3','4','unit']
                 and all(type(summary[key]) is int and summary[key] == 0 for key in ('failures','errors'))
                 and type(summary['skipped']) is int and summary['skipped'] == skips, 'public-backend-count-or-exact-skip-mismatch')
        return dict(action='verified-readonly',reason='fixed-public-candidate-native-snapshot-matches-old-control-pins',
                    allows_new_write=False,ordinary_main_health=False,release_authority=False,installation_authority=False,
                    independent_execution_verified=False,
                    number=p['number'],head=p['head'],base=p['base'],independent_source=p['independent_source'],
                    run_id=p['run_id'],run_attempt=p['run_attempt'],tests=summary['tests'],skipped=skips,
                    zip_sha256=p['zip_sha256'],receipt_sha256=p['receipt_sha256'])
    except Exception:
        return dict(action='hold',reason='unavailable-ambiguous-or-inexact-public-candidate-evidence',
                    allows_new_write=False,ordinary_main_health=False,release_authority=False,installation_authority=False)
