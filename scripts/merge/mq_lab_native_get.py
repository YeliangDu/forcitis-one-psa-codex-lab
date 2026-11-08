"""Pinned Lab-only GH GET routes and verified local immutable Git object reads.

No default runner/audit/policy, URL, write method, fetch or writer. The caller
supplies a separately installed scope digest and explicit subprocess runner.
Local Git objects are labelled local evidence, never native GH observations.
"""
import base64
import copy
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

ROOT = '/Users/yeliang/Projects/FORCITIS/forcitis-one-psa-codex-lab'
REPO = 'YeliangDu/forcitis-one-psa-codex-lab'
RID = 1449415666
BASELINE_REF = "git/ref/heads/lab/22-public-old-main-witness"
BASELINE_SOURCE = "60c97c75b8092a64dbf322efcab4f8a3ba166510"
OBJECT_LIMIT = 32 * 1024 * 1024
TOTAL_LIMIT = 256 * 1024 * 1024


def need(ok, reason):
    if not ok:
        raise ValueError(reason)


def sha(value):
    return type(value) is str and re.fullmatch('[0-9a-f]{40}', value) is not None


def unique(pairs):
    result = {}
    for key, value in pairs:
        need(key not in result, 'duplicate-scoped-GET-JSON')
        result[key] = value
    return result


def invalid(value):
    raise ValueError('nonfinite-scoped-GET-JSON')


def decode(raw):
    return json.loads(raw.decode('utf-8'), object_pairs_hook=unique, parse_constant=invalid)


def normalize_object(oid, kind, body):
    """Pure offline raw-object correspondence, also used by audit consumers."""
    need(sha(oid) and kind in ('commit', 'tree', 'blob') and type(body) is bytes
         and len(body) <= OBJECT_LIMIT
         and hashlib.sha1(f'{kind} {len(body)}'.encode() + b'\0' + body).hexdigest() == oid,
         'offline-raw-Git-object-mismatch')
    if kind == 'blob':
        return dict(sha=oid, size=len(body), encoding='base64', content=base64.b64encode(body).decode('ascii'))
    if kind == 'commit':
        headers = body.split(b'\n\n', 1)[0].splitlines()
        trees = [line[5:].decode('ascii') for line in headers if line.startswith(b'tree ')]
        parents = [line[7:].decode('ascii') for line in headers if line.startswith(b'parent ')]
        need(len(trees) == 1 and sha(trees[0]) and all(sha(p) for p in parents), 'offline-commit-headers-invalid')
        return dict(sha=oid, tree=dict(sha=trees[0]), parents=[dict(sha=p) for p in parents])
    rows, cursor, names = [], 0, set()
    while cursor < len(body):
        space, zero = body.find(b' ', cursor), body.find(b'\0', cursor)
        need(cursor < space < zero and zero + 21 <= len(body), 'offline-tree-malformed')
        mode, name = body[cursor:space].decode('ascii'), body[space + 1:zero].decode('utf-8')
        need(mode in ('40000', '100644', '100755', '120000', '160000') and name
             and '/' not in name and name not in ('.', '..') and name not in names, 'offline-tree-entry-invalid')
        names.add(name)
        rows.append(dict(path=name, mode='040000' if mode == '40000' else mode,
                         type='tree' if mode == '40000' else 'commit' if mode == '160000' else 'blob',
                         sha=body[zero + 1:zero + 21].hex()))
        cursor = zero + 21
    return dict(sha=oid, truncated=False, tree=rows)


def matches(value, expected):
    if type(value) is not type(expected):
        return False
    if type(expected) is dict:
        return all(key in value and matches(value[key], item) for key, item in expected.items())
    if type(expected) is list:
        return len(value) == len(expected) and all(matches(a, b) for a, b in zip(value, expected))
    return value == expected


def valid_path(path):
    need(type(path) is str and not any(ord(c) < 32 or ord(c) == 127 for c in path)
         and not any(x in path for x in (':', '%', '#', '\\', '//'))
         and not path.startswith('/') and all(p not in ('.', '..') for p in path.split('?', 1)[0].split('/')),
         'unsafe-scoped-GET-path')
    patterns = (
        r'', r'pulls/43', r'pulls/43/(?:reviews|files)\?per_page=100',
        r'git/ref/heads/[A-Za-z0-9_./-]+', r'commits/[0-9a-f]{40}',
        r'compare/[0-9a-f]{40}\.\.\.[0-9a-f]{40}',
        r'actions/runs/[1-9][0-9]*(?:/attempts/[1-9][0-9]*)?',
        r'actions/runs/[1-9][0-9]*/attempts/[1-9][0-9]*/jobs\?per_page=100',
        r'actions/runs/[1-9][0-9]*/artifacts\?per_page=100',
        r'actions/artifacts/[1-9][0-9]*(?:/zip)?', r'actions/jobs/[1-9][0-9]*/logs',
        r'actions/runs\?event=(?:pull_request|push|workflow_dispatch)(?:&branch=[A-Za-z0-9_./-]+)?&head_sha=[0-9a-f]{40}&per_page=100',
    )
    need(any(re.fullmatch(pattern, path) is not None for pattern in patterns), 'GET-vocabulary-not-approved')


class ScopedGithubGet:
    def __init__(self, scope_raw, scope_sha256, run, audit):
        need(type(scope_raw) is bytes and len(scope_raw) <= 4 * 1024 * 1024
             and hashlib.sha256(scope_raw).hexdigest() == scope_sha256
             and callable(run) and callable(audit), 'explicit-pinned-scope-runner-audit-required')
        self.scope = decode(scope_raw)
        need(type(self.scope) is dict and set(self.scope) == {
            'version', 'repository', 'repository_id', 'number', 'base', 'head',
            'stage', 'main_tip', 'merge_sha', 'independent_ref', 'independent_source',
            'baseline_ref', 'baseline_source', 'object_root', 'object_roots', 'routes'},
            'scoped-GET-policy-shape')
        s = self.scope
        need(type(s['version']) is int and s['version'] == 1 and s['repository'] == REPO
             and type(s['repository_id']) is int and s['repository_id'] == RID
             and type(s['number']) is int and s['number'] == 43 and sha(s['base']) and sha(s['head'])
             and s['base'] != s['head']
             and s['stage'] in ('observe', 'send', 'recovery', 'health') and sha(s['main_tip'])
             and (s['merge_sha'] is None or sha(s['merge_sha'])), 'scope-not-exact-Lab-subject')
        need(type(s['object_roots']) is list and len(s['object_roots']) <= 4
             and len(set(s['object_roots'])) == len(s['object_roots'])
             and all(sha(value) for value in s['object_roots']), 'invalid-exact-object-roots')
        need((s['baseline_ref'] is None and s['baseline_source'] is None)
             or (s['baseline_ref'] == BASELINE_REF and s['baseline_source'] == BASELINE_SOURCE),
             'baseline-ref-source-pair-not-exact-reviewed-witness')
        allowed_roots = {s['base'], s['head'], s['independent_source']}
        if s['stage'] in ('observe', 'send'):
            allowed_roots.add(s['baseline_source'])
        else:
            allowed_roots.add(s['merge_sha'])
        need(set(s['object_roots']) <= allowed_roots, 'unapproved-object-root')
        if s['stage'] == 'send':
            need(s['baseline_ref'] == BASELINE_REF and len(s['object_roots']) == 4
                 and set(s['object_roots']) == {s['base'], s['head'], s['independent_source'], BASELINE_SOURCE}
                 and s['merge_sha'] is None, 'send-requires-exact-four-root-witness-scope')
        if s['independent_ref'] is not None:
            valid_path(s['independent_ref'])
            need(s['independent_ref'].startswith('git/ref/heads/lab/22-independent-public-candidate-proof-')
                 and sha(s['independent_source']) and s['independent_source'] not in (s['head'], s['base']),
                 'independent-source-ref-not-external-pinned-control')
        self.run, self.audit, self.scope_sha256 = run, audit, scope_sha256
        self.routes, self.total = {}, 0
        need(type(s['routes']) is list and len(s['routes']) <= 256, 'scoped-route-bound')
        for record in s['routes']:
            need(type(record) is dict and set(record) == {'path', 'pages', 'binary', 'field', 'match', 'sha256'},
                 'exact-GET-route-shape')
            valid_path(record['path'])
            need(type(record['pages']) is bool and type(record['binary']) is bool
                 and type(record['match']) is dict and record['path'] not in self.routes
                 and record['field'] in (None, 'workflow_runs', 'jobs', 'artifacts')
                 and record['pages'] == ('per_page=100' in record['path'])
                 and record['binary'] == bool(re.fullmatch(r'actions/(?:artifacts/[1-9][0-9]*/zip|jobs/[1-9][0-9]*/logs)', record['path']))
                 and (record['sha256'] is None or type(record['sha256']) is str
                      and re.fullmatch('[0-9a-f]{64}', record['sha256'])),
                 'invalid-GET-mode-or-duplicate-route')
            self.routes[record['path']] = copy.deepcopy(record)
        need(all(p in self.routes for p in ('', 'pulls/43', 'git/ref/heads/main')), 'native-pin-routes-missing')
        need(s['baseline_ref'] is None or s['baseline_ref'] in self.routes, 'baseline-native-ref-route-missing')
        self.verified = False
        self.raw_objects, self.trees, self.allowed, self.object_total = {}, {}, {}, 0
        self.primed = False

    def __call__(self, path, pages=False, binary=False):
        object_route = re.fullmatch(r'git/(commits|trees|blobs)/([0-9a-f]{40})', path) if type(path) is str else None
        if object_route:
            need(pages is False and binary is False, 'local-object-GET-mode')
            return self.object_value(object_route[1], object_route[2])
        return self.remote(path, pages, binary)

    def remote(self, path, pages=False, binary=False):
        need(path in self.routes and type(pages) is bool and type(binary) is bool, 'unlisted-native-GET')
        route = self.routes[path]
        need(route['pages'] == pages and route['binary'] == binary, 'wrong-native-GET-mode')
        if path in ('', 'pulls/43', 'git/ref/heads/main', self.scope['independent_ref'], self.scope['baseline_ref']):
            self.verified = False
        command = ['gh', 'api', '--hostname', 'github.com', '--method', 'GET',
                   'repos/' + REPO + ('/' + path if path else '')]
        if pages:
            command += ['--paginate', '--slurp']
        result = self.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=120, check=False)
        need(result.returncode == 0 and type(result.stdout) is bytes
             and len(result.stdout) <= OBJECT_LIMIT, 'native-GET-failed-or-oversize')
        raw = result.stdout
        self.total += len(raw)
        need(self.total <= TOTAL_LIMIT, 'native-GET-total-bound')
        actual = hashlib.sha256(raw).hexdigest()
        need(route['sha256'] is None or route['sha256'] == actual, 'native-raw-evidence-changed')
        value = raw if binary else decode(raw)
        if not binary:
            if pages:
                need(type(value) is list and 0 < len(value) <= 16, 'native-page-bound')
                rows, counts, seen = [], [], set()
                for page in value:
                    current = page[route['field']] if route['field'] else page
                    need(type(current) is list, 'native-page-shape')
                    if route['field']:
                        count = page.get('total_count')
                        need(type(count) is int and count >= 0, 'native-page-total-missing')
                        counts.append(count)
                    for row in current:
                        key = row.get('filename') if '/files?' in path else row.get('id')
                        need(key is not None and key not in seen and matches(row, route['match']),
                             'native-page-duplicate-or-scope-mismatch')
                        seen.add(key)
                    rows += current
                need(not counts or len(set(counts)) == 1 and len(rows) == counts[0], 'native-pages-incomplete-or-moving')
            else:
                need(matches(value, route['match']), 'native-object-scope-mismatch')
        self.audit(dict(transport='native-gh-GET', scope_sha256=self.scope_sha256, path=path,
                        pages=pages, binary=binary, sha256=actual, size=len(raw)), raw)
        return value

    def verify_native_pins(self):
        s = self.scope
        need(s['independent_ref'] is not None and s['independent_ref'] in self.routes
             and sha(s['independent_source']), 'independent-native-source-pin-unbound')
        repo, pr = self.remote(''), self.remote('pulls/43')
        main, source = self.remote('git/ref/heads/main'), self.remote(s['independent_ref'])
        need(matches(repo, dict(id=RID, full_name=REPO, private=False)), 'foreign-native-repository')
        need(pr.get('number') == 43 and pr.get('head', {}).get('sha') == s['head']
             and pr.get('base', {}).get('sha') == s['base'] and pr['base'].get('ref') == 'main'
             and all(matches(pr[side].get('repo'), dict(id=RID, full_name=REPO, private=False))
                     for side in ('head', 'base')), 'actual-native-PR-pins-moved')
        need(matches(main, dict(ref='refs/heads/main', object=dict(sha=s['main_tip'], type='commit')))
             and matches(source, dict(ref='refs/heads/' + s['independent_ref'].removeprefix('git/ref/heads/'),
                                      object=dict(sha=s['independent_source'], type='commit'))),
             'actual-main-or-independent-SOURCE-moved')
        if s['baseline_ref'] is not None:
            witness = self.remote(s['baseline_ref'])
            need(matches(witness, dict(ref='refs/heads/lab/22-public-old-main-witness',
                                       object=dict(sha=s['baseline_source'], type='commit'))),
                 'actual-baseline-witness-native-ref-moved')
        if s['stage'] in ('recovery', 'health'):
            need(s['merge_sha'] is not None and pr.get('merged') is True
                 and pr.get('merge_commit_sha') == s['merge_sha'], 'native-recovery-merge-pin-missing')
        else:
            need(s['main_tip'] == s['base'] and pr.get('merged') is False, 'native-before-effect-main-moved')
        root = Path(s['object_root'])
        need(root.is_absolute() and root.resolve(strict=True) == root and root.name == REPO.split('/')[1]
             and (root / '.git').is_dir() and (root / '.git').resolve(strict=True) == root / '.git',
             'local-Git-root-not-physical-Lab-clone')
        if str(root) != ROOT:
            need(os.environ.get('GITHUB_ACTIONS') == 'true' and os.environ.get('GITHUB_REPOSITORY') == REPO
                 and os.environ.get('GITHUB_REPOSITORY_ID') == str(RID)
                 and str(root) == os.environ.get('GITHUB_WORKSPACE'), 'object-root-outside-fixed-Lab-or-hosted-checkout')
        self.verified = True

    def batch(self, args, stdin):
        command = ['git', '--no-replace-objects', '-C', self.scope['object_root'], 'cat-file', *args]
        result = self.run(command, input=stdin, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=120, check=False)
        need(result.returncode == 0 and type(result.stdout) is bytes, 'exact-local-Git-object-unavailable-no-fetch')
        return result.stdout

    def read_objects(self, ids):
        ids = sorted(set(ids) - set(self.raw_objects))
        need(all(sha(value) for value in ids), 'invalid-local-Git-object-ID')
        for offset in range(0, len(ids), 128):
            group = ids[offset:offset + 128]
            stdin = ('\n'.join(group) + '\n').encode()
            lines = self.batch(['--batch-check'], stdin).splitlines()
            need(len(lines) == len(group), 'incomplete-local-object-check')
            expected = []
            for oid, line in zip(group, lines):
                fields = line.decode('ascii').split()
                need(len(fields) == 3 and fields[0] == oid and fields[1] in ('commit', 'tree', 'blob')
                     and re.fullmatch('[0-9]+', fields[2]), 'missing-or-invalid-local-object')
                size = int(fields[2])
                need(size <= OBJECT_LIMIT, 'local-object-size-bound')
                expected.append((oid, fields[1], size))
            need(self.object_total + sum(item[2] for item in expected) <= TOTAL_LIMIT, 'local-object-total-bound')
            output, cursor = self.batch(['--batch'], stdin), 0
            for oid, kind, size in expected:
                end = output.find(b'\n', cursor)
                need(end >= cursor and output[cursor:end] == f'{oid} {kind} {size}'.encode(), 'local-batch-header-mismatch')
                body = output[end + 1:end + 1 + size]
                need(len(body) == size and output[end + 1 + size:end + 2 + size] == b'\n'
                     and hashlib.sha1(f'{kind} {size}'.encode() + b'\0' + body).hexdigest() == oid,
                     'local-Git-raw-object-hash-mismatch')
                self.raw_objects[oid] = kind, body
                self.object_total += size
                cursor = end + 2 + size
            need(cursor == len(output), 'extra-local-batch-output')

    def prime(self):
        roots = self.scope['object_roots']
        need(roots and {self.scope['base'], self.scope['head'], self.scope['independent_source']} <= set(roots), 'complete-pinned-object-roots-required')
        self.read_objects(roots)
        todo, blobs = set(), set()
        for oid in roots:
            kind, body = self.raw_objects[oid]
            need(kind == 'commit', 'pinned-root-not-commit')
            headers = body.split(b'\n\n', 1)[0].splitlines()
            trees = [line[5:].decode('ascii') for line in headers if line.startswith(b'tree ')]
            parents = [line[7:].decode('ascii') for line in headers if line.startswith(b'parent ')]
            need(len(trees) == 1 and sha(trees[0]) and all(sha(p) for p in parents), 'invalid-local-commit-headers')
            self.allowed[oid] = 'commit'
            todo.add(trees[0])
        while todo:
            self.read_objects(todo)
            following = set()
            for oid in todo:
                kind, body = self.raw_objects[oid]
                need(kind == 'tree', 'reachable-Git-object-not-tree')
                rows, cursor, names = [], 0, set()
                while cursor < len(body):
                    space, zero = body.find(b' ', cursor), body.find(b'\0', cursor)
                    need(cursor < space < zero and zero + 21 <= len(body), 'malformed-raw-Git-tree')
                    mode, name = body[cursor:space].decode('ascii'), body[space + 1:zero].decode('utf-8')
                    child = body[zero + 1:zero + 21].hex()
                    need(name and '/' not in name and name not in ('.', '..') and name not in names,
                         'ambiguous-raw-Git-tree-path')
                    names.add(name)
                    need(mode in ('40000', '100644', '100755', '120000', '160000'), 'unknown-Git-tree-mode')
                    child_kind = 'tree' if mode == '40000' else 'commit' if mode == '160000' else 'blob'
                    rows.append(dict(path=name, mode='040000' if mode == '40000' else mode, type=child_kind, sha=child))
                    if child_kind == 'tree' and child not in self.trees:
                        following.add(child)
                    elif child_kind == 'blob':
                        blobs.add(child)
                    # Gitlinks never authorize traversal into another repository.
                    cursor = zero + 21
                self.trees[oid] = rows
                self.allowed[oid] = 'tree'
            todo = following - set(self.trees)
        self.read_objects(blobs)
        for oid in blobs:
            need(self.raw_objects[oid][0] == 'blob', 'reachable-Git-object-not-blob')
            self.allowed[oid] = 'blob'
        self.primed = True

    def object_value(self, plural, oid):
        if not self.verified:
            self.verify_native_pins()
        if not self.primed:
            self.prime()
        kind = {'commits': 'commit', 'trees': 'tree', 'blobs': 'blob'}[plural]
        need(self.allowed.get(oid) == kind, 'object-not-in-exact-pinned-commit-closure')
        _, body = self.raw_objects[oid]
        value = normalize_object(oid, kind, body)
        self.audit(dict(transport='local-readonly-Git-object', scope_sha256=self.scope_sha256,
                        path='git/' + plural + '/' + oid, oid=oid, git_type=kind,
                        raw_sha256=hashlib.sha256(body).hexdigest(), size=len(body)), body)
        return value
