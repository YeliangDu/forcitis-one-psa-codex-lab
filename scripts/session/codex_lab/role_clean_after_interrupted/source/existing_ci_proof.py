"""Read-only existing-run proof adapter; external launcher selects trusted pins.

Not an independent vertical oracle. Never requests/reruns CI or creates effects.
"""
import hashlib
import io
import json
from pathlib import Path
import stat
import zipfile

from runtime_store import need


class ExistingCIProofVerifier:
    def __init__(self, manifest_path, independently_selected_sha256):
        self.path = Path(manifest_path)
        self.pin = independently_selected_sha256
        need(type(self.pin) is str and len(self.pin) == 64, 'independent-manifest-pin-required')
        self._load()

    def _read(self, path, limit):
        need(path.is_absolute() and path.resolve(strict=True) == path and not path.is_symlink(),
             'physical-evidence-required')
        info = path.stat()
        need(stat.S_ISREG(info.st_mode) and 0 < info.st_size <= limit, 'evidence-bound')
        raw = path.read_bytes()
        need(len(raw) == info.st_size and len(raw) <= limit, 'evidence-size-changed')
        return raw

    def _load(self):
        raw = self._read(self.path, 1024 * 1024)
        need(hashlib.sha256(raw).hexdigest() == self.pin, 'independent-proof-manifest-moved')
        manifest = json.loads(raw)
        names = {'repository.json', 'pr.json', 'run.json', 'jobs.json', 'artifacts.json', 'receipt.zip'}
        need(manifest.get('kind') == 'r2-existing-exact-hosted-ci-receipt/1'
             and set(manifest['raw_sha256']) == names and manifest['original_run_reused'] is True
             and manifest['new_external_effect'] is False, 'existing-run-only-manifest-required')
        files = {}
        for name, pin in manifest['raw_sha256'].items():
            files[name] = self._read(self.path.parent / name, 32 * 1024 * 1024)
            need(hashlib.sha256(files[name]).hexdigest() == pin, 'independently-pinned-raw-evidence-mismatch')
        repo, pr, run, jobs, artifacts = [json.loads(files[name]) for name in
            ('repository.json', 'pr.json', 'run.json', 'jobs.json', 'artifacts.json')]
        need(repo['id'] == manifest['repository_id'] == 1449415666
             and repo['full_name'] == manifest['repository'] == 'YeliangDu/forcitis-one-psa-codex-lab'
             and repo['private'] is False, 'proof-repository-mismatch')
        need(pr['number'] == manifest['pr'] and pr['head']['sha'] == manifest['head']
             and pr['base']['sha'] == manifest['base']
             and all(pr[side]['repo']['id'] == manifest['repository_id'] for side in ('head', 'base')),
             'proof-pr-head-base-mismatch')
        need(run['id'] == manifest['run_id'] and run['run_attempt'] == manifest['attempt']
             and run['head_sha'] == manifest['head'] and run['path'] == manifest['workflow_path']
             and run['event'] == 'pull_request' and run['status'] == 'completed'
             and run['conclusion'] == 'success' and run['repository']['id'] == manifest['repository_id'],
             'proof-native-run-not-exact')
        need(type(jobs['jobs']) is list and jobs['total_count'] == len(jobs['jobs'])
             and len({j['id'] for j in jobs['jobs']}) == len(jobs['jobs']), 'incomplete-proof-jobs')
        for job in jobs['jobs']:
            need(job['head_sha'] == manifest['head'] and job.get('conclusion') in ('success', 'skipped'),
                 'proof-job-not-green-or-explicit-skip')
            if job['conclusion'] == 'success':
                need(job.get('runner_group_name') == 'GitHub Actions'
                     and job.get('labels') == ['ubuntu-24.04'], 'proof-job-not-hosted-source')
        rows = artifacts['artifacts']
        need(type(rows) is list and artifacts['total_count'] == len(rows)
             and len({row['id'] for row in rows}) == len(rows), 'incomplete-proof-artifacts')
        selected = [row for row in rows if row['id'] == manifest['artifact_id']]
        need(len(selected) == 1, 'proof-artifact-missing')
        artifact = selected[0]
        link = artifact['workflow_run']
        need(artifact['expired'] is False and link['id'] == manifest['run_id']
             and link['repository_id'] == manifest['repository_id'] and link['head_sha'] == manifest['head']
             and artifact['digest'] == 'sha256:' + manifest['raw_sha256']['receipt.zip'], 'proof-raw-artifact-linkage')
        with zipfile.ZipFile(io.BytesIO(files['receipt.zip'])) as archive:
            entries = archive.infolist()
            need({e.filename for e in entries} == {'receipt.json', 'backend-summary.json'}
                 and len(entries) == 2 and all(not e.flag_bits & 1 and 0 < e.file_size <= 256 * 1024
                 and stat.S_IFMT(e.external_attr >> 16) in (0, stat.S_IFREG) for e in entries)
                 and archive.testzip() is None, 'unsafe-proof-archive')
            receipt = json.loads(archive.read('receipt.json'))
            summary = json.loads(archive.read('backend-summary.json'))
        need(receipt['head_sha'] == receipt['observed_head'] == manifest['head']
             and receipt['base_sha'] == manifest['base'] and str(receipt['run_id']) == str(manifest['run_id'])
             and str(receipt['run_attempt']) == str(manifest['attempt']) and receipt['ready'] is True,
             'proof-receipt-not-exact')
        need(summary == manifest['backend_summary'] and summary['failures'] == summary['errors'] == 0,
             'proof-inventory-summary-mismatch')
        for lane in receipt['lanes'].values():
            need((lane == dict(applicability='required', proof='passed', outcome='success'))
                 or lane == dict(applicability='not-applicable', proof='not-applicable', outcome='skipped'),
                 'proof-lane-not-passed-or-explicit-not-applicable')
        return manifest

    def verify_ci_result(self, effect, delivery, binding):
        # Delivery is only a hint; the independently pinned complete raw bundle
        # is the source. No model-provided outcome/verified flag is read.
        manifest = self._load()
        need(effect['kind'] == 'ci-observe-existing' and effect['issue_count'] == 0,
             'historical-ci-proof-cannot-complete-rerun-or-prove-send')
        need(binding['assignment']['B'] == manifest['base']
             and binding['assignment']['H'] == manifest['head'], 'historical-ci-not-current-assignment-base-head')
        target = json.loads(effect['target'])
        need(all(target[k] == manifest[k] and type(target[k]) is type(manifest[k])
                 for k in ('repository', 'run_id', 'attempt', 'head')), 'wait-does-not-select-this-existing-run')
        return dict(kind='external-ci-result-proof/1', binding=binding,
                    repository=manifest['repository'], run_id=manifest['run_id'], attempt=manifest['attempt'],
                    head=manifest['head'], outcome='success',
                    source_receipt_sha256=manifest['raw_sha256']['receipt.zip'])
