"""#45 independent hosted acceptance; real disposable Git and fake GitHub only."""
import copy
import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

RID, REPO = 1449415666, 'YeliangDu/forcitis-one-psa-codex-lab'
HERE = Path(__file__).resolve().parent


class GithubFixture:
    def __init__(self):
        self.pulls, self.creates, self.reruns = [], 0, 0
        self.lost_create = False
        self.view = dict(complete=True, queued=False, queue_completed=False, merge_log=None,
                         active_ci=[], proof_pins=[], other_open_pulls=[])
        self.run = None

    def find_pulls(self, binding):
        return dict(complete=True, items=copy.deepcopy(self.pulls))

    def create_pull(self, binding):
        self.creates += 1
        row = dict(number=401, state='open', merged=False, draft=False,
            head=dict(ref=binding['branch'], sha=binding['head'], repo=dict(id=RID, full_name=REPO)),
            base=dict(ref='main', sha=binding['base'], repo=dict(id=RID, full_name=REPO)))
        self.pulls.append(row)
        if self.lost_create:
            self.lost_create = False
            raise TimeoutError('synthetic lost create response AFTER native effect')
        return copy.deepcopy(row)

    def pull(self, number):
        return copy.deepcopy(self.pulls[0])

    def runs(self, binding):
        return dict(complete=True, items=[] if self.run is None else [copy.deepcopy(self.run)])

    def rerun(self, run_id, attempt):
        self.reruns += 1
        assert self.run['id'] == run_id and self.run['run_attempt'] == attempt
        self.run['run_attempt'] += 1
        return dict(requested=True)  # Native rerun must subsequently be read back.

    def retirement_snapshot(self, binding):
        return copy.deepcopy(self.view)


class BranchLifecycleAcceptanceTest(unittest.TestCase):
    def setUp(self):
        source = HERE / 'lab_branch_live.py'
        self.assertTrue(source.is_file(), 'BRANCH_LIFECYCLE_HARNESS_UNBOUND')
        before = sys.path[:]
        sys.path.insert(0, str(HERE))
        try:
            spec = importlib.util.spec_from_file_location('independent_branch_live', source)
            self.live = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(self.live)
        finally:
            sys.path[:] = before
        self.assertTrue(callable(getattr(self.live, 'run_branch_command', None)),
                        'BRANCH_LIFECYCLE_HARNESS_UNBOUND')
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name).resolve()
        self.remote = self.root / 'scratch.git'
        self.checkout = self.root / 'forcitis-one-psa-codex-lab'
        self.w1, self.w2 = self.root / 'worker-one', self.root / 'worker-two'
        self.env = dict(os.environ, GIT_CONFIG_NOSYSTEM='1', GIT_CONFIG_GLOBAL='/dev/null',
            GIT_AUTHOR_NAME='Synthetic', GIT_AUTHOR_EMAIL='synthetic@example.invalid',
            GIT_COMMITTER_NAME='Synthetic', GIT_COMMITTER_EMAIL='synthetic@example.invalid',
            GIT_TERMINAL_PROMPT='0')
        self.raw(['init', '--bare', '--initial-branch=main', str(self.remote)], self.root)
        self.raw(['clone', str(self.remote), str(self.checkout)], self.root)
        (self.checkout / 'synthetic.txt').write_text('base\n')
        self.raw(['add', 'synthetic.txt'])
        self.raw(['commit', '-m', 'synthetic base'])
        self.raw(['push', 'origin', 'HEAD:refs/heads/main'])
        self.base = self.raw(['rev-parse', 'HEAD'])
        self.raw(['worktree', 'add', '-b', 'synthetic-park-one', str(self.w1)])
        self.raw(['worktree', 'add', '-b', 'synthetic-park-two', str(self.w2)])
        self.state = self.root / 'synthetic-branch-state.json'
        self.github = GithubFixture()
        self.commands, self.deletes = [], []
        self.race_sha = None
        self.lose_delete_reply = False
        self.card = dict(repository=REPO, repository_id=RID, card=45, role='one-platform',
                         worktree=str(self.w1), base=self.base, target='main')

    def raw(self, args, cwd=None):
        result = subprocess.run(['git', '--no-replace-objects', '-C', str(cwd or self.checkout), *args],
            env=self.env, capture_output=True, text=True, timeout=20)
        if result.returncode:
            raise RuntimeError('SCRATCH_GIT_FAILED: ' + repr(args) + '\n' + result.stderr)
        return result.stdout.strip()

    def git(self, args, cwd=None):
        cwd = Path(cwd or self.checkout).resolve()
        self.assertTrue(cwd == self.root or self.root in cwd.parents, 'GIT_OUTSIDE_SCRATCH')
        self.assertIsInstance(args, list)
        self.assertNotIn('-C', args, 'NESTED_GIT_CWD_ESCAPE')
        self.assertFalse(any('github.com' in arg or arg.startswith(('https://', 'http://', 'ssh://', 'git@')) for arg in args), 'REAL_REMOTE_FORBIDDEN')
        self.assertFalse(args[:2] == ['remote', 'set-url'], 'SCRATCH_ORIGIN_MUST_NOT_CHANGE')
        self.commands.append(list(args))
        deletion = args and args[0] == 'push' and any(x.startswith(':refs/heads/') for x in args)
        if deletion:
            archive = 'refs/archive/lab/card-45/' + self.binding['head']
            self.assertEqual(self.binding['head'], self.ref(archive), 'EXACT_RECOVERY_REF_MUST_PRECEDE_DELETE')
            self.assertIn('--force-with-lease=refs/heads/' + self.binding['branch'] + ':' + self.binding['head'], args)
            self.deletes.append(list(args))
            if self.race_sha:
                self.raw(['push', 'origin', self.race_sha + ':refs/heads/' + self.binding['branch']], self.w2)
                self.race_sha = None
        answer = self.raw(args, cwd)
        if deletion and self.lose_delete_reply:
            self.lose_delete_reply = False
            raise TimeoutError('synthetic delete reply lost AFTER actual local push')
        return answer

    def command(self, action, request):
        # EXACT future production CLI dispatcher, injected scratch argv + fake API.
        return self.live.run_branch_command(action, copy.deepcopy(request), self.state, self.git, self.github)

    def dispatch(self):
        result = self.command('dispatch', self.card)
        self.assertEqual('bound', result['action'])
        self.binding = result['binding']
        self.assertTrue(self.binding['branch'].startswith('codex/'))
        return self.binding

    def commit_publish(self, text):
        (self.w1 / 'synthetic.txt').write_text(text)
        self.raw(['add', 'synthetic.txt'], self.w1)
        self.raw(['commit', '-m', 'synthetic update'], self.w1)
        head = self.raw(['rev-parse', 'HEAD'], self.w1)
        result = self.command('publish', dict(binding=self.binding, head=head))
        self.assertEqual('published', result['action'])
        self.binding = result['binding']
        self.assertEqual(head, self.binding['head'])
        if self.github.pulls:
            self.github.pulls[0]['head']['sha'] = head
        return head

    def ref(self, ref):
        result = subprocess.run(['git', '--git-dir=' + str(self.remote), 'rev-parse', '--verify', ref],
            env=self.env, capture_output=True, text=True, timeout=10)
        return result.stdout.strip() if result.returncode == 0 else None

    def retire_ready(self):
        self.dispatch()
        self.commit_publish('final candidate\n')
        self.command('ensure-pr', dict(binding=self.binding))
        self.raw(['merge', '--no-ff', '-m', 'synthetic merge', self.binding['head']])
        merge = self.raw(['rev-parse', 'HEAD'])
        self.raw(['push', 'origin', 'HEAD:refs/heads/main'])
        self.github.pulls[0].update(state='closed', merged=True, merge_commit_sha=merge)
        self.github.view.update(queue_completed=True,
            merge_log=dict(repository_id=RID, number=401, head=self.binding['head'],
                           base=self.base, merge_sha=merge, status='confirmed'))
        self.raw(['checkout', '--detach', self.binding['head']], self.w1)
        self.github.view['merged_readback'] = dict(number=401, merge_sha=merge,
            parents=[self.base, self.binding['head']], target_contains_merge=True)

    def test_card_repeated_dispatch_two_commits_one_ref_pr_and_native_rerun(self):
        binding = self.dispatch()
        again = self.command('dispatch', self.card)
        self.assertEqual(binding, again['binding'])
        first = self.commit_publish('first\n')
        self.command('ensure-pr', dict(binding=self.binding))
        self.command('ensure-pr', dict(binding=self.binding))
        second = self.commit_publish('second\n')
        self.assertNotEqual(first, second)
        self.assertEqual(binding['branch'], self.binding['branch'])
        self.assertEqual(second, self.ref('refs/heads/' + binding['branch']))
        self.assertEqual(1, self.github.creates)
        self.assertEqual(1, len(self.github.pulls))
        self.github.run = dict(id=700, run_attempt=1, event='pull_request',
            repository=dict(id=RID, full_name=REPO), head_branch=binding['branch'], head_sha=second,
            base_sha=self.base, status='completed', conclusion='success', pull_request=401)
        self.command('rerun-ci', dict(binding=self.binding, run_id=700, run_attempt=1))
        result = self.command('read-ci', dict(binding=self.binding))
        self.assertEqual('verified-ci', result['action'])
        self.assertEqual((700, 2), (result['run_id'], result['run_attempt']))
        self.assertIs(result['release_authority'], False)
        self.assertEqual(1, self.github.reruns)

    def test_lost_pr_create_reply_reconciles_unique_native_pr(self):
        self.dispatch()
        self.commit_publish('candidate\n')
        self.github.lost_create = True
        try:
            self.command('ensure-pr', dict(binding=self.binding))
        except TimeoutError:
            pass
        result = self.command('ensure-pr', dict(binding=self.binding))
        self.assertEqual(401, result['pr_number'])
        self.assertEqual(1, self.github.creates)

    def test_old_head_base_or_foreign_branch_ci_never_authorizes(self):
        self.dispatch()
        old = self.commit_publish('old\n')
        head = self.commit_publish('new\n')
        for mutation in ('head', 'base', 'repo', 'branch'):
            with self.subTest(mutation=mutation):
                self.github.run = dict(id=700, run_attempt=2, event='pull_request',
                    repository=dict(id=RID, full_name=REPO), head_branch=self.binding['branch'],
                    head_sha=head, base_sha=self.base, status='completed', conclusion='success', pull_request=401)
                if mutation == 'head': self.github.run['head_sha'] = old
                elif mutation == 'base': self.github.run['base_sha'] = old
                elif mutation == 'repo': self.github.run['repository']['id'] = 123
                else: self.github.run['head_branch'] = 'codex/foreign'
                self.assertEqual('hold', self.command('read-ci', dict(binding=self.binding))['action'])

    def test_exact_merge_and_completed_queue_archive_then_reclaim_only_bound_ref(self):
        self.retire_ready()
        unrelated = self.ref('refs/heads/main')
        result = self.command('retire', dict(binding=self.binding))
        self.assertEqual('retired', result['action'])
        self.assertEqual('refs/archive/lab/card-45/' + self.binding['head'], result['archive_ref'])
        self.assertEqual(self.binding['head'], self.ref(result['archive_ref']))
        self.assertIsNone(self.ref('refs/heads/' + self.binding['branch']))
        self.assertEqual(unrelated, self.ref('refs/heads/main'))
        self.assertEqual(1, len(self.deletes))
        self.command('retire', dict(binding=self.binding))
        self.assertEqual(1, len(self.deletes))

    def test_any_live_occupation_or_incomplete_read_prevents_deletion(self):
        self.retire_ready()
        original = copy.deepcopy(self.github.view)
        for mutation in ('open-pr', 'worktree', 'ci', 'queued', 'proof-pin', 'incomplete', 'queue-unfinished', 'merge-unconfirmed'):
            with self.subTest(mutation=mutation):
                self.github.view = copy.deepcopy(original)
                if mutation == 'open-pr': self.github.view['other_open_pulls'] = [402]
                elif mutation == 'worktree': self.raw(['checkout', self.binding['branch']], self.w1)
                elif mutation == 'ci': self.github.view['active_ci'] = [701]
                elif mutation == 'queued': self.github.view['queued'] = True
                elif mutation == 'proof-pin': self.github.view['proof_pins'] = ['synthetic-active-proof']
                elif mutation == 'incomplete': self.github.view['complete'] = False
                elif mutation == 'queue-unfinished': self.github.view['queue_completed'] = False
                else: self.github.view['merge_log'] = None
                self.assertEqual('hold', self.command('retire', dict(binding=self.binding))['action'])
                self.assertEqual(self.binding['head'], self.ref('refs/heads/' + self.binding['branch']))
                self.assertEqual([], self.deletes, 'FALSE_DELETION_MUST_BE_ZERO')
                if mutation == 'worktree': self.raw(['checkout', '--detach', self.binding['head']], self.w1)

    def test_real_second_worktree_lease_race_preserves_moved_ref(self):
        self.retire_ready()
        (self.w2 / 'race.txt').write_text('concurrent branch movement\n')
        self.raw(['add', 'race.txt'], self.w2)
        self.raw(['commit', '-m', 'synthetic concurrent update'], self.w2)
        race = self.raw(['rev-parse', 'HEAD'], self.w2)
        self.race_sha = race
        # Force-free remote update must be a descendant of expected HEAD.
        self.raw(['reset', '--soft', self.binding['head']], self.w2)
        self.raw(['commit', '-m', 'synthetic descendant race'], self.w2)
        race = self.raw(['rev-parse', 'HEAD'], self.w2)
        self.race_sha = race
        try:
            result = self.command('retire', dict(binding=self.binding))
            self.assertEqual('hold', result['action'])
        except RuntimeError:
            pass  # Actual Git lease rejection is also a safe refusal.
        self.assertEqual(race, self.ref('refs/heads/' + self.binding['branch']))

    def test_lost_delete_response_reconciles_without_repeating_deletion(self):
        self.retire_ready()
        self.lose_delete_reply = True
        try:
            self.command('retire', dict(binding=self.binding))
        except TimeoutError:
            pass
        self.assertIsNone(self.ref('refs/heads/' + self.binding['branch']))
        count = len(self.deletes)
        result = self.command('retire', dict(binding=self.binding))
        self.assertIn(result['action'], ('retired', 'hold'))
        self.assertEqual(count, len(self.deletes))

    def test_missing_or_foreign_identity_dispatch_does_not_create_ref_or_pr(self):
        for mutation in ('repo-id', 'base', 'role'):
            with self.subTest(mutation=mutation):
                card = copy.deepcopy(self.card)
                if mutation == 'repo-id': card['repository_id'] = 123
                elif mutation == 'base': card['base'] = '0' * 40
                else: card['role'] = 'unregistered'
                result = self.command('dispatch', card)
                self.assertEqual('hold', result['action'])
                self.assertEqual(0, self.github.creates)
        self.assertEqual(['refs/heads/main'], self.raw(['for-each-ref', '--format=%(refname)', 'refs/heads'], self.remote).splitlines())


if __name__ == '__main__':
    expected = '--expect-red' in sys.argv
    if expected:
        sys.argv.remove('--expect-red')
        result = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(BranchLifecycleAcceptanceTest))
        ok = (result.testsRun == 8 and len(result.failures) == 8 and not result.errors
              and not result.skipped and all('BRANCH_LIFECYCLE_HARNESS_UNBOUND' in trace for _, trace in result.failures))
        raise SystemExit(0 if ok else 1)
    unittest.main(verbosity=2)
