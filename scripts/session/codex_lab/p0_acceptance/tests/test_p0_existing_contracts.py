"""P0 independent A: existing pinned baselines, no new production behavior.

Only the legacy compact/resume context assertions are expected red. Native
fixture assertions are supplied-witness consistency, not live hooks/activation.
"""
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest

ROOT = Path(__file__).resolve().parents[1]
PINS = {
    'resident_app_matcher.py': '6faec4f779cf74c43b2e05a3dd318862be7c88433c4ffb2345baa6587cc53340',
    'resident_dispatch.py': 'e274dfa2093487006338b5e2946b8d6b460a67d56f9f608bde2b7002777fa281',
    'hook.py': '165654142e3eade8518404715b9116bf76e715fc0abdb8cddf1a61c81a3951c3',
    'lab_queue_release.py': '719a22654b5ec42034550dc18f46305826de036aea29cf74bfc8cca32283ec36',
}
B = '77e872e8ba9661d3294d3a9337c8e5c956b70641'
THREAD = '019a0000-0000-7000-8000-000000000061'
CLAIM = '019a0000-0000-7000-8000-000000000201'
OTHER = '019a0000-0000-7000-8000-000000000202'
OLD = '019a0000-0000-7000-8000-000000000101'
TURN = '019a0000-0000-7000-8000-000000000102'
ORIGIN = 'git@github.com:YeliangDu/forcitis-one-psa-codex-lab.git'


def load(name):
    path = ROOT / 'baselines' / (name + '.py')
    if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != PINS[path.name]:
        raise RuntimeError('CAPABILITY_HOLD: missing/changed actual baseline ' + name)
    spec = importlib.util.spec_from_file_location('p0_actual_' + name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def compact(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'))


def wait(path):
    deadline = time.monotonic() + 8
    while not path.exists() and time.monotonic() < deadline:
        time.sleep(.01)
    if not path.exists():
        raise RuntimeError('CAPABILITY_HOLD: process barrier unavailable')


def worker(config, gate, output, claim):
    data = json.loads(Path(config).read_text())
    store = load('resident_dispatch').ResidentDispatchStore(data['path'], data['registry'], data['root'], data['common'])
    Path(output + '.ready').write_text('ready')
    wait(Path(gate))
    try:
        value = store.claim_send(data['generation'], claim, expected_revision=data['revision'])
    except ValueError:
        value = {'action': 'stale-cas'}
    Path(output).write_text(json.dumps(value))


class P0ExistingContractAcceptance(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix='p0-independent-')
        self.addCleanup(self.temporary.cleanup)
        self.scratch = Path(self.temporary.name).resolve()

    def matcher_fixture(self):
        prompt = b'Synthetic normalized legacy #28 unit claim; no native send.'
        claim = dict(kind='initial', thread_id=THREAD, initial_claim_id=CLAIM, dispatch_claim_id=CLAIM,
                     generation=2, role='one-test', issue=61, phase='independent-A', head='b' * 40,
                     cwd='/synthetic/personal-lab/work', handoff='.codex-lab/cards/p0-unit/handoff.json',
                     prompt_sha256=hashlib.sha256(prompt).hexdigest(), pre_send_cursor='unit-cursor-0', preexisting_turn_ids=[OLD])
        ack = {k: claim[k] for k in ('initial_claim_id', 'thread_id', 'generation', 'role', 'issue', 'phase', 'head', 'cwd', 'handoff', 'prompt_sha256')}
        ack['claim_id'] = CLAIM
        text = 'LAB_DISPATCH_ACK ' + compact(ack)
        pages = [dict(request_cursor='unit-cursor-0', threadId=THREAD, nextCursor='unit-cursor-1',
                      turns=[dict(id=TURN, status='inProgress', items=[dict(type='agentMessage', text=text)])]),
                 dict(request_cursor='unit-cursor-1', threadId=THREAD, nextCursor=None,
                      turns=[dict(id=OLD, status='completed', items=[])])]
        return claim, prompt, pages

    def store_fixture(self):
        root = self.scratch / 'forcitis-one-psa-codex-lab'
        root.mkdir(mode=0o700)
        common, cwd, private = root / '.git', root / 'work', root / 'private'
        for p in (common, cwd, private):
            p.mkdir(mode=0o700)
        registry = {'one-test': dict(thread_id=THREAD, cwd=str(cwd), common_dir=str(common), fetch_origin=ORIGIN, push_origin=ORIGIN)}
        assignment = dict(issue=61, role='one-test', thread_id=THREAD, cwd=str(cwd), base=B, head='b' * 40,
                          phase='unit-A', run_id=100, run_attempt=1, handoff='.codex-lab/cards/p0-unit/result.json')
        path = private / 'dispatch.json'
        store = load('resident_dispatch').ResidentDispatchStore(path, registry, root, common)
        prepared = store.prepare(assignment, expected_revision=0)
        self.assertEqual('prepared', prepared['state'])
        return store, prepared, root, common, registry, assignment, path

    def test_native_readonly_witness_is_bounded_r1_not_current_recovery_or_hook_proof(self):
        raw = (ROOT / 'fixtures/native-readonly-r1.json').read_bytes()
        self.assertEqual('8933e4aca31b935bbc8f258920f45dacbfb67de88ca626c507e26aae023be79d', hashlib.sha256(raw).hexdigest())
        data = json.loads(raw)
        witness = data['witness']
        current, previous = data['turns']
        self.assertEqual((witness['native_turn'], witness['prior_turn']), (current['id'], previous['id']))
        self.assertEqual(('completed', 'completed'), (current['status'], previous['status']))
        self.assertTrue(data['page']['hasMore'])  # Older history was NOT evaluated.
        ack = next(i for i in current['items'] if i['id'] == witness['ack_item'])
        artifact = next(i for i in current['items'] if i['id'] == witness['artifact_item'])
        command = next(i for i in current['items'] if i['id'] == witness['command_item'])
        self.assertEqual(('agentMessage', 'commentary'), (ack['type'], ack['phase']))
        parsed = json.loads(ack['text'])  # Full JSON message; no substring/quoted-tool extraction.
        self.assertEqual((witness['claim'], 61, 1), (parsed['claim'], parsed['card'], parsed['revision']))
        self.assertEqual(('agentMessage','final_answer'), (artifact['type'],artifact['phase']))
        self.assertLess(current['items'].index(ack), current['items'].index(command))
        self.assertEqual(witness['artifact'], json.loads(artifact['text']))
        self.assertEqual((0, witness['artifact']['cwd']), (command['exitCode'], command['cwd']))
        lines = command['output']['text'].strip().splitlines()
        self.assertEqual([witness['artifact'][k] for k in ('cwd','git_root','git_common_dir','origin')], lines[:4])
        self.assertEqual(witness['artifact']['sha256'], lines[4].split()[0])
        self.assertEqual(B, witness['artifact']['base'])
        self.assertEqual(witness['native_turn'], data['post_snapshot']['turn']['id'])
        self.assertEqual(witness['post_read_cursor'], data['post_snapshot']['cursor'])
        self.assertEqual(1, witness['send_attempts'])
        self.assertNotEqual(data['thread']['cwd'], command['cwd'])
        self.assertIn('hook trust / execution', witness['permissions_not_proved'])
        self.assertNotEqual('a2688a482aeaf37269395b258ee39c01', witness['claim'])

    def test_historical_matcher_returns_only_unique_authoritative_envelope_and_ignores_user_turn(self):
        claim, prompt, pages = self.matcher_fixture()
        before = copy.deepcopy((claim, pages))
        result = load('resident_app_matcher').match_dispatch_ack(claim, prompt, pages)
        self.assertEqual(('matched', TURN), (result['action'], result['turn_id']))
        self.assertEqual(before, (claim, pages))
        pages[0]['turns'].append(dict(id=OTHER, status='completed', items=[dict(type='userMessage', text=pages[0]['turns'][0]['items'][0]['text'])]))
        self.assertEqual(TURN, load('resident_app_matcher').match_dispatch_ack(claim, prompt, pages)['turn_id'])

    def test_historical_matcher_rejects_tool_quote_partial_and_wrong_revision_full_messages(self):
        for kind in ('tool', 'function', 'quote', 'suffix', 'generation', 'reported-turn'):
            with self.subTest(kind=kind):
                claim, prompt, pages = self.matcher_fixture()
                item = pages[0]['turns'][0]['items'][0]
                if kind in ('tool', 'function'):
                    item['type'] = 'commandExecution' if kind == 'tool' else 'functionCallOutput'
                elif kind == 'quote':
                    item['text'] = '> ' + item['text']
                elif kind == 'suffix':
                    item['text'] += '\nother message'
                else:
                    body = json.loads(item['text'].split(' ', 1)[1])
                    body['generation' if kind == 'generation' else 'turn_id'] = 1 if kind == 'generation' else OTHER
                    item['text'] = 'LAB_DISPATCH_ACK ' + compact(body)
                result = load('resident_app_matcher').match_dispatch_ack(claim, prompt, pages)
                self.assertEqual('hold', result['action'])
                self.assertNotIn('turn_id', result)

    def test_historical_matcher_cursor_and_ambiguity_guards(self):
        for kind in ('first', 'next', 'truncated', 'duplicate', 'prompt'):
            with self.subTest(kind=kind):
                claim, prompt, pages = self.matcher_fixture()
                if kind == 'first': pages[0]['request_cursor'] = 'stale'
                elif kind == 'next': pages[1]['request_cursor'] = 'wrong-next'
                elif kind == 'truncated': pages = pages[:1]
                elif kind == 'duplicate': pages[0]['turns'].append(dict(copy.deepcopy(pages[0]['turns'][0]), id=OTHER))
                else: prompt += b'changed bytes'
                self.assertEqual('hold', load('resident_app_matcher').match_dispatch_ack(claim, prompt, pages)['action'])

    def test_existing_store_claim_is_durable_and_unknown_without_handle_never_reclaims(self):
        store, prepared, *_ = self.store_fixture()
        result = store.claim_send(1, CLAIM, expected_revision=prepared['revision'])
        self.assertEqual('send-claimed', result['action'])
        consumed = store.read()
        self.assertIsNone(consumed['turn_id'])
        self.assertEqual('observe', consumed['next_action'])
        for claim in (CLAIM, OTHER):
            self.assertEqual('observe', store.claim_send(1, claim, expected_revision=consumed['revision'])['action'])
            self.assertEqual(consumed, store.read())
        with self.assertRaises(ValueError):
            store.prepare(dict(consumed['assignment'],phase='replacement-after-unknown'), expected_revision=consumed['revision'])
        self.assertEqual(consumed, store.read())

    def test_existing_store_two_processes_one_claim_winner(self):
        store, prepared, root, common, registry, _, path = self.store_fixture()
        config = self.scratch / 'worker.json'
        config.write_text(json.dumps(dict(path=str(path), root=str(root), common=str(common), registry=registry,
                                         generation=1, revision=prepared['revision'])))
        gate = self.scratch / 'go'
        children, outputs = [], []
        try:
            for index, claim in enumerate((CLAIM, OTHER)):
                output = self.scratch / ('result-' + str(index))
                outputs.append(output)
                children.append(subprocess.Popen([sys.executable, '-B', str(Path(__file__)), '--claim-worker', str(config), str(gate), str(output), claim], cwd=self.scratch, stdout=subprocess.PIPE, stderr=subprocess.PIPE))
            for p in outputs: wait(Path(str(p) + '.ready'))
            gate.touch()
            for p in children:
                stdout, stderr = p.communicate(timeout=10)
                self.assertEqual(0, p.returncode, (stdout + stderr).decode())
            values = [json.loads(p.read_text()) for p in outputs]
            self.assertEqual(1, sum(v['action'] == 'send-claimed' for v in values))
            self.assertEqual('claimed', store.read()['state'])
        finally:
            gate.touch()
            for p in children:
                if p.poll() is None: p.kill(); p.communicate(timeout=3)

    def test_existing_assignment_store_rejects_late_generation_revision_and_wrong_cwd_role(self):
        store, first, root, _, _, assignment, _ = self.store_fixture()
        newer = dict(assignment, phase='current-revision', head='c' * 40)
        current = store.prepare(newer, expected_revision=first['revision'])
        event = dict(kind='turn-terminal', thread_id=THREAD, turn_id=TURN, terminal=True, text='late old done')
        for generation, revision in ((1, current['revision']), (current['generation'], first['revision'])):
            with self.assertRaises(ValueError): store.record(generation, event, expected_revision=revision)
            self.assertEqual(current, store.read())
        other = root / 'other-work'; other.mkdir(mode=0o700)
        for bad in (dict(newer, cwd=str(other)), dict(newer, role='one-platform')):
            with self.assertRaises(ValueError): store.prepare(bad, expected_revision=current['revision'])
            self.assertEqual(current, store.read())


    def test_A01_historical_registry_correspondence_rejects_wrong_origin_common_thread_and_role(self):
        store, before, root, common, registry, assignment, path = self.store_fixture()
        other = root / 'other-common'; other.mkdir(mode=0o700)
        for kind in ('origin', 'common', 'thread', 'role'):
            with self.subTest(kind=kind):
                changed = copy.deepcopy(registry)
                if kind == 'origin': changed['one-test']['fetch_origin'] = 'git@github.com:synthetic/forbidden.git'
                elif kind == 'common': changed['one-test']['common_dir'] = str(other)
                elif kind == 'thread': changed['one-test']['thread_id'] = OTHER
                else: changed['one-platform'] = changed.pop('one-test')
                with self.assertRaises(ValueError):
                    candidate = load('resident_dispatch').ResidentDispatchStore(path, changed, root, common)
                    candidate.prepare(assignment, expected_revision=before['revision'])
                self.assertEqual(before, store.read())

    def test_existing_artifact_bytes_assignment_fields_and_late_outputs_are_exact(self):
        store, prepared, root, _, _, assignment, _ = self.store_fixture()
        store.claim_send(1, CLAIM, expected_revision=prepared['revision'])
        current = store.read()
        store.record(1, dict(kind='send-accepted', thread_id=THREAD, turn_id=TURN, claim_id=CLAIM), expected_revision=current['revision'])
        artifact = root / assignment['handoff']; artifact.parent.mkdir(parents=True)
        artifact.write_bytes(b'synthetic exact artifact\n'); artifact.chmod(0o644)
        witness = dict(assignment, generation=1, sha256=hashlib.sha256(artifact.read_bytes()).hexdigest())
        before = store.read()
        for field in ('head', 'base', 'run_attempt', 'sha256'):
            bad = dict(witness); bad[field] = 2 if field == 'run_attempt' else 'e' * (64 if field == 'sha256' else 40)
            result = store.record(1, dict(kind='verified-handoff', thread_id=THREAD, turn_id=TURN, claim_id=CLAIM, witness=bad), expected_revision=before['revision'])
            self.assertEqual(before, result)
        result = store.record(1, dict(kind='verified-handoff', thread_id=THREAD, turn_id=TURN, claim_id=CLAIM, witness=witness), expected_revision=before['revision'])
        self.assertEqual(('completed', witness['sha256']), (result['state'], result['delivery_sha256']))
        # Library completion trusts its caller; this is NOT P0 delivery/native/hosted authority.

    def release_fixture(self):
        repo = 'YeliangDu/forcitis-one-psa-codex-lab'; h = 'b' * 40
        context = dict(repository=repo, main_sha=B, queue_head=h, required_reviews=['first'])
        pr = dict(number=61, head_repo=repo, base_repo=repo, head_sha=h, base_sha=B, base_ref='main', merged=False, state='open', draft=False)
        run = dict(repo=repo, pr_number=61, head_sha=h, event='pull_request', path='.github/workflows/lab-pr-ci.yml', runner_group='GitHub-hosted', status='completed', conclusion='success', id=100, attempt=1)
        receipt = dict(head_sha=h, observed_head=h, base_sha=B, ready=True, run_id='100', run_attempt='1', lanes={k:dict(applicability='required',proof='passed') for k in ('light','secret','shadow','frontend','backend')})
        reviews = [dict(kind='first', verdict='pass', head_sha=h)]
        return context, pr, run, receipt, reviews

    def test_B_normalized_evidence_positive_is_only_inert_component(self):
        self.assertEqual('eligible', load('lab_queue_release').plan_release(*self.release_fixture())['action'])

    def test_B_evidence_guards_wrong_B_H_repo_attempt_workflow_hosted_and_missing_artifact(self):
        for kind in ('B','H','repo','attempt','workflow','runner','artifact'):
            with self.subTest(kind=kind):
                context, pr, run, receipt, reviews = self.release_fixture()
                if kind == 'B': receipt['base_sha'] = 'e'*40
                elif kind == 'H': receipt['observed_head'] = 'e'*40
                elif kind == 'repo': run['repo'] = 'synthetic/forbidden'
                elif kind == 'attempt': receipt['run_attempt'] = '2'
                elif kind == 'workflow': run['path'] = '.github/workflows/other.yml'
                elif kind == 'runner': run['runner_group'] = 'self-hosted'
                else: receipt = None
                self.assertEqual('hold', load('lab_queue_release').plan_release(context,pr,run,receipt,reviews)['action'])

    def test_B_failed_or_unready_receipt_is_not_green_admission(self):
        for kind in ('failed','unready'):
            context, pr, run, receipt, reviews = self.release_fixture()
            if kind == 'failed': run['conclusion'] = 'failure'
            else: receipt['ready'] = False
            self.assertEqual('hold', load('lab_queue_release').plan_release(context,pr,run,receipt,reviews)['action'])

    def legacy_hook(self, source):
        root = self.scratch / 'forcitis-one-psa-codex-lab'; root.mkdir()
        script = root / 'scripts/session/codex_lab/hook.py'; script.parent.mkdir(parents=True)
        selected = Path(os.environ.get('P0_SESSIONSTART_TARGET', str(ROOT/'baselines/hook.py')))
        expected = os.environ.get('P0_SESSIONSTART_TARGET_SHA256', PINS['hook.py'])
        raw = selected.read_bytes()
        self.assertEqual(expected, hashlib.sha256(raw).hexdigest(), 'CAPABILITY_HOLD: selected actual CLI differs from reviewed pin')
        script.write_bytes(raw)
        area = root / '.codex-lab/old-fixture/work'; area.mkdir(parents=True)
        stale = dict(role='one-test', role_kind='tester', task='STALE_SYNTHETIC_CARD20_REV1: read ../card.md and old fixture')
        (area.parent/'work-dispatch.json').write_text(json.dumps(stale))
        # Actual legacy event fields only. No invented current-assignment API.
        event = dict(hook_event_name='SessionStart', source=source, cwd=str(area), session_id=THREAD)
        result = subprocess.run([sys.executable,'-B',str(script)],input=json.dumps(event),text=True,cwd=root,capture_output=True,timeout=10)
        self.assertEqual(0,result.returncode,'CAPABILITY_HOLD: actual hook failed: '+result.stderr)
        return json.loads(result.stdout).get('hookSpecificOutput',{}).get('additionalContext','')

    def test_A03_compact_without_current_authority_must_not_inject_old_static_dispatch(self):
        context = self.legacy_hook('compact')
        self.assertNotIn('STALE_SYNTHETIC_CARD20_REV1', context, 'A03_COMPACT_STALE_FIXTURE_INJECTED')

    def test_A03_resume_without_current_authority_must_not_inject_old_static_dispatch(self):
        context = self.legacy_hook('resume')
        self.assertNotIn('STALE_SYNTHETIC_CARD20_REV1', context, 'A03_RESUME_STALE_FIXTURE_INJECTED')


if __name__ == '__main__':
    if sys.argv[1:2] == ['--claim-worker']:
        worker(*sys.argv[2:])
    else:
        unittest.main(verbosity=2)
