"""Hosted scratch transport adapter for the actual ordinary ProductionPorts.

No synthetic eligibility, default IPC/network, approval installation or ref
update. The independently owned receiver alone performs ordinary-cas, distinct
from protected-cas. Missing raw ledger/receiver contracts fail closed.
"""
import copy
import os
from pathlib import Path
import re
from types import SimpleNamespace

from mq_lab_operator import ProductionPorts, ROOT, _module
from mq_lab_trust import CONTROL_PATHS
from mq_lab_cutover_channel import ScratchChannel, decode
from mq_lab_cutover_live import git, actual_main


REPOSITORY = 'YeliangDu/forcitis-one-psa-codex-lab'
REPOSITORY_ID = 1449415666


def _require(condition, reason):
    if not condition:
        raise ValueError(reason)


def _positive(value):
    return type(value) is int and value > 0


def _sha(value):
    return type(value) is str and re.fullmatch('[0-9a-f]{40}', value) is not None


def _physical(value):
    _require(type(value) is str, 'explicit-physical-path-required')
    path = Path(value)
    _require(path.is_absolute() and str(path) == value and path.resolve(strict=True) == path,
             'physical-path-alias-or-missing')
    return path


class _OrdinaryPorts(ProductionPorts):
    """Inherit verify/_issue_run/_provenance/_review_ids without replacement."""
    def __init__(self, request, channel):
        _require(os.environ.get('GITHUB_ACTIONS') == 'true', 'hosted-scratch-only')
        self.root = _physical(request['root'])
        _require(isinstance(channel, ScratchChannel) and channel.root == self.root, 'explicit-same-scratch-channel-required')
        common = _physical(request['common_dir'])
        _require(self.root.name == 'forcitis-one-psa-codex-lab' and common == self.root / '.git',
                 'fixed-scratch-common-dir-required')
        ordinary = copy.deepcopy(request['ordinary'])
        _require(type(ordinary) is dict and set(ordinary) ==
                 {'number', 'card_number', 'expected_approval', 'source_root', 'github_read'},
                 'ordinary-raw-policy-contract-missing')
        _require(_positive(ordinary['number']) and _positive(ordinary['card_number'])
                 and ordinary['number'] != request['number'], 'ordinary-subject-not-separate')
        self.loaded_root = _physical(ordinary['source_root'])
        _require(self.loaded_root == ROOT and self.loaded_root.is_relative_to(self.root)
                 and (self.loaded_root / '.git').exists(), 'ordinary-source-root-not-actual-loaded-checkout')
        expected = ordinary['expected_approval']
        _require(type(expected) is dict and type(expected.get('number')) is int
                 and expected['number'] == ordinary['number'] and expected.get('mode') == 'normal'
                 and expected.get('target') == 'main' and type(expected.get('repo_id')) is int
                 and expected['repo_id'] == REPOSITORY_ID
                 and _sha(expected.get('head')) and _sha(expected.get('base')), 'missing-normal-independent-approval-binding')
        self.expected = expected
        self.number, self.card_number = ordinary['number'], ordinary['card_number']
        read = ordinary['github_read']
        _require(type(read) is dict and set(read) == {'key', 'scope'}
                 and type(read['key']) is str and bool(read['key']) and type(read['scope']) is dict,
                 'ordinary-scoped-raw-ledger-missing')
        self.read_policy, self.channel = read, channel
        self._built_tree, self._built_merge = None, None
        self.directory = common / 'merge-queue'
        # Do not call the base constructor: it would install default gh transport.
        # The inherited verifier still calls the real helpers/actual local files.
        self.sh = self._proof_sh
        self.github = _module('lab_queue_live').GithubRead(request=self._native)
        self.writer_type = _module('lab_queue_git_writer').GitLabMainWriter

    def load(self, number):
        _require(type(number) is int and number == self.number, 'wrong-normal-approval-number')
        record = super().load(number)  # Strict existing 0600 independently installed file; no IPC substitution.
        if record is None:
            return None
        _require(record == self.expected, 'installed-normal-approval-differs-from-pins')
        return record

    def _proof_sh(self, command):
        _require(type(command) is list and command and command[0] == 'git', 'normal-proof-not-fixed-git')
        args = command[1:]
        allowed = args in (['status', '--porcelain', '--untracked-files=all'], ['rev-parse', 'HEAD'])
        if len(args) == 2 and args[0] == 'rev-parse':
            allowed |= args[1] in {commit + ':' + path
                                  for commit in (self.expected['head'], self.expected['base']) for path in CONTROL_PATHS}
        _require(allowed, 'ordinary-proof-git-command-not-allowlisted')
        return SimpleNamespace(returncode=0, stdout=git(self.loaded_root, *args).decode('utf-8'))

    def _native(self, path, pages=False, binary=False):
        _require(type(path) is str and type(pages) is bool and type(binary) is bool,
                 'ordinary-native-mode-not-explicit')
        patterns = (
            '', 'git/ref/heads/main', f'pulls/{self.number}', f'issues/{self.card_number}',
            f'pulls/{self.number}/closing-issues',
            rf'pulls/{self.number}/(?:files|reviews)\?per_page=100',
            r'actions/runs\?event=pull_request&head_sha=' + self.expected['head'] + r'&per_page=100',
            r'actions/runs\?event=push&branch=main&head_sha=' + self.expected['base'] + r'&per_page=100',
            r'actions/runs/[1-9][0-9]*/attempts/[1-9][0-9]*/jobs\?per_page=100',
            r'actions/runs/[1-9][0-9]*/artifacts\?per_page=100',
            r'commits/[0-9a-f]{40}', r'compare/[0-9a-f]{40}\.\.\.[0-9a-f]{40}',
            r'actions/artifacts/[1-9][0-9]*/zip',
        )
        _require(any(re.fullmatch(pattern, path) for pattern in patterns), 'ordinary-native-path-not-allowlisted')
        _require(pages == ('per_page=100' in path) and binary == path.endswith('/zip'), 'ordinary-native-mode-mismatch')
        scope = dict(binding=copy.deepcopy(self.read_policy['scope']), repository=REPOSITORY,
                     repository_id=REPOSITORY_ID, number=self.number, head=self.expected['head'],
                     base=self.expected['base'], target='main', path=path, pages=pages, binary=binary)
        raw = self.channel.read(self.read_policy['key'], scope)
        _require(type(raw) is bytes, 'ordinary-native-raw-bytes-required')
        if binary:
            return raw  # Existing GithubRead validates the real ZIP/receipt.
        value = decode(raw)
        if path == 'git/ref/heads/main':
            _require(type(value) is dict and value.get('ref') == 'refs/heads/main'
                     and type(value.get('object')) is dict
                     and value['object'].get('sha') == actual_main(self.root), 'native-main-not-actual-scratch-ref')
        if path.startswith('commits/'):
            sha = path.split('/')[1]
            actual_parents = git(self.root, 'show', '-s', '--format=%P', sha).decode().split()
            _require(type(value) is dict and value.get('sha') == sha and type(value.get('parents')) is list
                     and all(type(parent) is dict for parent in value['parents'])
                     and [parent.get('sha') for parent in value['parents']] == actual_parents,
                     'native-commit-parents-not-actual-scratch-object')
        if path.startswith('compare/'):
            merge, tip = path.split('/')[1].split('...')
            _require(actual_main(self.root) == tip, 'native-comparison-tip-not-actual-main')
            git(self.root, 'merge-base', '--is-ancestor', merge, tip)
        return value

    def _writer_git(self, args):
        base, head = self.expected['base'], self.expected['head']
        _require(type(args) is list and args, 'ordinary-writer-command-missing')
        if args == ['remote', '-v']:
            # Frozen scratch has NO configured remotes. Expose the expected
            # identity only to this one Git invocation; never edit config or
            # pass a network-capable push through this process-local setting.
            _require(git(self.root, 'remote').strip() == b''
                     and git(self.root, 'remote', '-v').strip() == b'', 'scratch-must-have-no-remotes')
            return git(self.root, '-c', 'remote.origin.url=git@github.com:' + REPOSITORY + '.git',
                       'remote', '-v').decode('utf-8')
        if args[0] == 'push':
            _require(len(args) == 5 and args[:4] ==
                     ['push', '--porcelain', '--force-with-lease=refs/heads/main:' + base, 'origin'],
                     'ordinary-CAS-not-exact-expected-base-lease')
            match = re.fullmatch(r'([0-9a-f]{40}):refs/heads/main', args[4])
            _require(match is not None, 'ordinary-CAS-target-not-main')
            merge = match.group(1)
            _require(merge == self._built_merge and _sha(self._built_tree), 'ordinary-CAS-not-this-commit-tree-result')
            _require(git(self.root, 'show', '-s', '--format=%T', merge).decode().strip() == self._built_tree,
                     'ordinary-CAS-tree-not-this-merge-tree-result')
            parents = git(self.root, 'show', '-s', '--format=%P', merge).decode().split()
            _require(parents == [base, head] and actual_main(self.root) == base, 'ordinary-CAS-parents-or-base-moved')
            git(self.root, 'merge-base', '--is-ancestor', base, merge)
            # NEVER invoke Git push or protected-cas. Receiver owns the one
            # actual scratch-only atomic ref CAS and checks committed V2 state.
            result = self.channel.call('ordinary-cas',
                       intent=dict(number=self.number, base=base, head=head, target='main'),
                       expected_base=base, merge_sha=merge)
            _require(type(result) is dict and set(result) == {'requested'} and result['requested'] is True,
                     'ordinary-CAS-reply-unavailable')
            return ''  # A request result is not confirmation; real reader follows.
        allowed = args == ['merge-tree', '--write-tree', base, head]
        if len(args) == 8 and args[0] == 'commit-tree':
            allowed |= (_sha(args[1]) and args[1] == self._built_tree and args[2:6] == ['-p', base, '-p', head]
                        and args[6:] == ['-m', f'Lab controlled main release #{self.number}'])
        if len(args) == 4 and args[:3] == ['show', '-s', '--format=%P']:
            allowed |= _sha(args[3])
        if len(args) == 4 and args[:3] == ['merge-base', '--is-ancestor', base]:
            allowed |= _sha(args[3])
        _require(allowed, 'ordinary-writer-git-command-not-allowlisted')
        output = git(self.root, *args).decode('utf-8')
        if args[0] == 'merge-tree':
            _require(_sha(output.strip()), 'ordinary-merge-tree-output-not-exact')
            self._built_tree, self._built_merge = output.strip(), None
        if args[0] == 'commit-tree':
            _require(_sha(output.strip()), 'ordinary-commit-tree-output-not-exact')
            self._built_merge = output.strip()
        return output

    def writer(self, authorize):
        return self.writer_type(self._writer_git, self.github._single, authorize)


def make_ordinary_ports(request, channel):
    return _OrdinaryPorts(request, channel)
