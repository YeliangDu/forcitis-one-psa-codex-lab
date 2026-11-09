"""Public Broker plan -> byte-pinned existing read-only mq.py plan consumer."""
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import subprocess
import sys

from goal_retire import RetireStore, RetireSupport
from branch_broker import private_raw
from runtime_store import need, wire, digest

REPO = 'YeliangDu/forcitis-one-psa-codex-lab'


class PlanStore(RetireStore):
    pass  # Read-only plan needs no new effect/schema or approval store.


class PlanInstallation:
    def __init__(self, path, external_sha256):
        self.path, self.pin = path, external_sha256
        self.policy = self.load()

    def load(self):
        raw = private_raw(self.path)
        need(hashlib.sha256(raw).hexdigest() == self.pin, 'external-plan-installation-pin-mismatch')
        value = json.loads(raw)
        need(type(value) is dict and set(value) == {'kind', 'broker_installation_sha256', 'owner',
            'assignment', 'number', 'author', 'controller', 'root', 'approval_path', 'approval_sha256',
            'consumer_sha256', 'source_files_sha256', 'read_environment'}
            and value['kind'] == 'r2-existing-approved-readonly-plan/1'
            and type(value['root']) is str and Path(value['root']).is_absolute() and type(value['number']) is int and value['number'] > 0,
            'exact-independent-plan-installation-required')
        env = value['read_environment']
        need(type(env) is dict and set(env) <= {'HOME', 'GH_CONFIG_DIR', 'GH_HOST'}
             and 'HOME' in env and all(type(v) is str and bool(v) for v in env.values())
             and env.get('GH_HOST', 'github.com') == 'github.com', 'approved-credential-provider-read-context-required')
        # No tokens, helper commands, proxy/TLS overrides or arbitrary env input.
        return value


class PlanSupport(RetireSupport):
    def __init__(self, cfg):
        super().__init__(cfg)
        self._plan_installation = None

    def install_plan(self, value):
        need(value is None or type(value) is PlanInstallation, 'trusted-plan-startup-only')
        self._plan_installation = value

    def _plan_selection(self, auth, args):
        need(self._plan_installation is not None, 'no-independent-plan-installation')
        policy = self._plan_installation.load()
        need(policy == self._plan_installation.policy and policy['broker_installation_sha256'] == self.cfg['pin'],
             'plan-installation-drift')
        assignment = policy['assignment']
        need(type(args) is dict and args == {'revision': assignment['revision']}, 'installed-plan-selector-only')
        with self.store.tx() as db:
            owner, task = self.store.binding(db, auth, args['revision'], paused_ok=True)
            need(policy['owner'] == dict(client=owner['client'], generation=owner['generation'])
                 and json.loads(task['body']) == assignment and assignment['session_id'] == self.session_id,
                 'plan-current-owner-or-assignment-mismatch')
        for key in ('A', 'B', 'H'):
            value = assignment[key]
            need(type(value) is str and len(value) == 40 and all(c in '0123456789abcdef' for c in value),
                 'exact-plan-acceptance-base-head-required')
        return policy

    def _environment(self, policy):
        return dict(policy['read_environment'], PATH='/opt/homebrew/bin:/usr/bin:/bin',
                    GIT_OPTIONAL_LOCKS='0', GIT_CONFIG_NOSYSTEM='1', GIT_CONFIG_GLOBAL='/dev/null',
                    PYTHONDONTWRITEBYTECODE='1')

    def _git(self, policy, *args):
        result = subprocess.run(['/usr/bin/git', '--no-replace-objects', '-C', str(Path(policy['root'])), *args],
                                env=self._environment(policy), capture_output=True, timeout=15, check=False)
        need(result.returncode == 0 and len(result.stdout) <= 1024 * 1024, 'isolated-controller-read-unavailable')
        return result.stdout

    def _source_and_approval(self, policy):
        need(all(Path(policy['root']) != root and root not in Path(policy['root']).parents for root in self.protected_roots()), 'plan-root-is-protected')
        need(Path(policy['root']).resolve(strict=True) == Path(policy['root']) and (Path(policy['root']) / '.git').resolve(strict=True) == Path(policy['root']) / '.git'
             and (Path(policy['root']) / '.git').is_dir(), 'exact-isolated-standalone-plan-root-required')
        need(self._git(policy, 'rev-parse', 'HEAD').decode().strip() == policy['controller']
             and self._git(policy, 'rev-parse', '--path-format=absolute', '--git-common-dir').decode().strip() == str(Path(policy['root']) / '.git')
             and self._git(policy, 'status', '--porcelain', '--untracked-files=all').strip() == b'',
             'actual-controller-or-clean-source-mismatch')
        pins = policy['source_files_sha256']
        required = {'scripts/merge/mq.py', 'scripts/merge/mq_lab.py', 'scripts/merge/mq_lab_operator.py',
                    'scripts/merge/mq_lab_proof.py', 'scripts/session/codex_lab/lab_queue_evidence.py', 'policy.json'}
        need(type(pins) is dict and required <= set(pins) and len(pins) <= 256,
             'independently-selected-complete-plan-source-closure-required')
        for relative, pin in pins.items():
            path = PurePosixPath(relative)
            need(not path.is_absolute() and str(path) == relative and '..' not in path.parts,
                 'unsafe-plan-source-path')
            actual = Path(policy['root']) / relative
            need(actual.resolve(strict=True) == actual and actual.is_file(), 'plan-source-not-physical')
            raw = actual.read_bytes()
            need(len(raw) <= 1024 * 1024 and hashlib.sha256(raw).hexdigest() == pin
                 and raw == self._git(policy, 'show', policy['controller'] + ':' + relative), 'plan-source-pin-not-at-C')
        expected = Path(policy['root']) / '.git/merge-queue/approvals' / (str(policy['number']) + '.json')
        need(policy['approval_path'] == str(expected), 'only-existing-isolated-approval-path')
        raw = private_raw(expected, limit=131072)
        need(hashlib.sha256(raw).hexdigest() == policy['approval_sha256'], 'existing-private-plan-approval-drift')
        approval = json.loads(raw)
        assignment = policy['assignment']
        need(all(approval.get(k) == v for k, v in dict(number=policy['number'], head=assignment['H'],
             base=assignment['B'], target='main', repo_id=1449415666, author=policy['author']).items()),
             'existing-plan-approval-subject-or-author-mismatch')
        return raw

    def _actual_pr(self, policy):
        result = subprocess.run(['/opt/homebrew/bin/gh', 'api', '--method', 'GET',
            'repos/' + REPO + '/pulls/' + str(policy['number'])], env=self._environment(policy),
            capture_output=True, timeout=25, check=False)
        need(result.returncode == 0 and len(result.stdout) <= 1024 * 1024, 'actual-pr-read-unavailable')
        pr = json.loads(result.stdout)
        assignment = policy['assignment']
        need(pr.get('number') == policy['number'] and pr.get('state') == 'open' and pr.get('draft') is False
             and pr.get('head', {}).get('sha') == assignment['H']
             and pr.get('base', {}).get('sha') == assignment['B']
             and all(pr.get(side, {}).get('repo', {}).get('id') == 1449415666 for side in ('head', 'base'))
             and type(pr.get('body')) is str and bool(pr['body'].splitlines())
             and pr['body'].splitlines()[0] == 'Refs #' + str(assignment['card']), 'actual-pr-current-scope-mismatch')
        return result.stdout

    def _readonly_plan(self, auth, args):
        policy = self._plan_selection(auth, args)
        approval = self._source_and_approval(policy)
        pr_before = self._actual_pr(policy)
        consumer = Path(__file__).with_name('readonly_plan_consumer.py')
        raw = consumer.read_bytes()
        need(hashlib.sha256(raw).hexdigest() == policy['consumer_sha256'], 'reviewed-readonly-consumer-pin-mismatch')
        assignment = policy['assignment']
        command = [sys.executable, '-I', '-B', str(consumer), '--number', str(policy['number']),
                   '--head', assignment['H'], '--base', assignment['B'], '--controller', policy['controller'],
                   '--approval-sha256', policy['approval_sha256'], '--root', policy['root'],
                   '--protected-roots', self.binding.reference('protected-roots')['path'],
                   '--protected-roots-sha256', self.binding.reference('protected-roots')['sha256']]
        with self.store.tx() as db:
            self.store.event(db, 'public-readonly-plan-consumer-before-execution',
                dict(revision=assignment['revision'], number=policy['number'], A=assignment['A'],
                     B=assignment['B'], H=assignment['H'], C=policy['controller'], command=command,
                     installation_raw_sha256=self._plan_installation.pin))
        result = subprocess.run(command, cwd=Path(policy['root']), env=self._environment(policy), capture_output=True,
                                timeout=65, check=False)
        need(len(result.stdout) <= 1024 * 1024 and len(result.stderr) <= 256 * 1024,
             'plan-consumer-output-bound')
        need(self._plan_selection(auth, args) == policy and self._source_and_approval(policy) == approval,
             'plan-source-or-approval-moved-during-read')
        pr_after = self._actual_pr(policy)
        before, after = json.loads(pr_before), json.loads(pr_after)
        need(all(before[k] == after[k] for k in ('number', 'state', 'draft', 'head', 'base', 'body')),
             'actual-pr-moved-during-plan')
        parsed = json.loads(result.stdout)
        need(type(parsed) is dict, 'readonly-consumer-object-required')
        actual = parsed.get('result')
        need(type(parsed) is dict and parsed.get('kind') == 'v2-generic-readonly-plan/1'
             and parsed.get('controller') == policy['controller'] and parsed.get('approval_sha256') == policy['approval_sha256']
             and parsed.get('isolated_state_unchanged') is True and parsed.get('writer_called') is False
             and type(actual) is dict, 'consumer-did-not-return-verified-readonly-observation')
        if actual.get('action') == 'eligible':
            need(result.returncode == 0 and all(actual.get(k) == v for k, v in dict(number=policy['number'],
                head=assignment['H'], base=assignment['B'], target='main').items()), 'consumer-plan-subject-mismatch')
        with self.store.tx() as db:
            self.store.event(db, 'public-readonly-plan-consumer-observed',
                dict(revision=assignment['revision'], number=policy['number'], C=policy['controller'],
                     stdout_sha256=hashlib.sha256(result.stdout).hexdigest(),
                     stderr_sha256=hashlib.sha256(result.stderr).hexdigest(), exit_code=result.returncode,
                     pr_before_raw_sha256=hashlib.sha256(pr_before).hexdigest(),
                     pr_after_raw_sha256=hashlib.sha256(pr_after).hexdigest(), result=actual))
        return dict(action='eligible' if actual.get('action') == 'eligible' else 'hold', plan=actual,
                    A=assignment['A'], B=assignment['B'], H=assignment['H'], C=policy['controller'],
                    acceptance_scope='existing-readonly-consumer-not-new-card-vertical-pass',
                    formal_enqueue_allowed=False, writer_allowed=False, independent_acceptance=False)

    def handle(self, request):
        if request.get('command') != 'plan':
            return super().handle(request)
        with self.execution_gate:
            return self._readonly_plan(request.get('auth'), request.get('arguments', {}))
