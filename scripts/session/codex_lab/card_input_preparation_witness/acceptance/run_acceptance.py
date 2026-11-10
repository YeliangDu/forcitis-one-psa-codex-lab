"""Independent preparation API tests and separate real-history guard replay.
Fixture Git mutations occur only when this entry runs on the disposable runner.
No implementation is supplied; missing prepare_card is capability-HOLD, not red.
"""
import argparse
import copy
import hashlib
import importlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

PACKAGE=Path(__file__).resolve().parent
IMPL='11111111-1111-4111-8111-111111111111'
TESTER='22222222-2222-4222-8222-222222222222'
ACCEPTANCE_PATH='backend/src/test/java/com/forcitis/one/acceptance/FixtureAcceptanceTest.java'
ORDINARY_PATH='backend/src/test/java/com/forcitis/one/organization/FixtureTest.java'
PREPARE=None
GUARD=None
CFG=None


def sha(raw):return hashlib.sha256(raw).hexdigest()

def git(repo,*args,message=None):
    env=dict(PATH='/usr/bin:/bin',HOME=str(repo.parent),GIT_CONFIG_NOSYSTEM='1',
             GIT_CONFIG_GLOBAL='/dev/null',GIT_TERMINAL_PROMPT='0',
             GIT_AUTHOR_DATE='2026-01-01T00:00:00+00:00',GIT_COMMITTER_DATE='2026-01-01T00:00:00+00:00')
    result=subprocess.run(['git','-c','user.name=Synthetic Fixture','-c','user.email=fixture@example.invalid',*args],
                          cwd=repo,env=env,input=message,capture_output=True,check=True,timeout=15)
    return result.stdout.decode().strip()


def put(repo,path,raw):
    target=repo/path;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)


def commit(repo,message):
    git(repo,'add','--all');git(repo,'commit','-q','-F','-',message=message.encode())
    return git(repo,'rev-parse','HEAD')


class Fixtures(unittest.TestCase):
    def workspace(self):
        temp=tempfile.TemporaryDirectory(prefix='prepare-card-',dir=CFG.runner_temp)
        self.addCleanup(temp.cleanup)
        root=Path(temp.name).resolve();repo=root/'slot/work';repo.mkdir(parents=True)
        git(repo,'init','-q','-b','main')
        put(repo,'fixture/logic.py',b'value = 1\n')
        base=commit(repo,'synthetic base\n')
        return root,repo,base

    def fixture(self,paths=None):
        root,repo,base=self.workspace()
        git(repo,'checkout','-q','-b','codex/71-standard-hours-scope-lock')
        paths=[ACCEPTANCE_PATH] if paths is None else paths
        blobs={path:('synthetic acceptance '+str(index)+'\n').encode() for index,path in enumerate(paths)}
        for path,raw in blobs.items():put(repo,path,raw)
        guarded=all(GUARD.ACCEPTANCE.match(path) for path in paths)
        message='synthetic independent fixture\n\n'
        if guarded:message+='Acceptance-Test-Author: test-engineer\n'
        message+='Codex: '+TESTER+'\n'
        acceptance=commit(repo,message)
        output=root/'output';output.mkdir()
        bundle=dict(repo_id=1449415666,repository='YeliangDu/forcitis-one-psa-codex-lab',
                    card=71,revision=5,branch='codex/71-standard-hours-scope-lock',workdir=str(repo),
                    scope=dict(base=base,issue_title='fix(attendance): serialize same-scope standard-hours publication',
                               description='Publish one scope serially; retain stale-tail conflict semantics.',
                               read=['fixture/logic.py',*paths],write=['fixture/logic.py'],
                               implementation_session=IMPL,acceptance_session=TESTER),
                    acceptance=dict(author=TESTER,A=acceptance,B=base,
                                    files={path:sha(raw) for path,raw in blobs.items()}),
                    metadata={},registry=dict(roles=[dict(role='one-dev-fixture',id=IMPL),dict(role='one-test',id=TESTER)]))
        return bundle,repo,output,base

    def prepare(self,bundle,repo,output,observed_base):
        before=copy.deepcopy(bundle)
        repo_before={str(p.relative_to(repo)):sha(p.read_bytes()) for p in repo.rglob('*') if p.is_file()}
        result=PREPARE.prepare(bundle,repo,output,mode='live',observed_base=observed_base)
        self.assertEqual(bundle,before,'INPUT_BUNDLE_MUTATED')
        self.assertEqual(repo_before,{str(p.relative_to(repo)):sha(p.read_bytes()) for p in repo.rglob('*') if p.is_file()},'PREPARER_WROTE_REPO')
        self.assertIsInstance(result,dict)
        self.assertIn(result.get('action'),('prepared','unchanged','hold'))
        self.assertIsInstance(result.get('reason'),str)
        self.assertTrue(result['reason'].strip())
        return result

    def held(self,bundle,repo,output,base):
        self.assertEqual(self.prepare(bundle,repo,output,base)['action'],'hold')
        self.assertEqual(list(output.iterdir()),[],'FAILED_PREPARATION_WROTE_OUTPUT')


class PreparationAcceptance(Fixtures):
    def test_01_complete_input_prepares_capsule_metadata_and_is_idempotent(self):
        bundle,repo,output,base=self.fixture()
        self.assertNotIn('H',bundle);self.assertNotIn('delivery',bundle)
        self.assertEqual(self.prepare(bundle,repo,output,base)['action'],'prepared')
        target=output/'bundle.json'
        self.assertEqual(sorted(p.name for p in output.iterdir()),['bundle.json'])
        value=json.loads(target.read_text())
        for forbidden in ('authorization','approval','grant'):
            self.assertNotIn(forbidden,value,'PREPARATION_MUST_NOT_EMIT_AUTHORIZATION')
        task=value['task'];self.assertEqual(task['card'],71);self.assertEqual(task['revision'],5)
        self.assertEqual(task['session_id'],IMPL);self.assertEqual(task['task'],bundle['scope']['description'])
        self.assertEqual(task['B'],base)
        capsule=value['capsule'];self.assertEqual(capsule['card'],71);self.assertEqual(capsule['revision'],5)
        self.assertEqual(capsule['session_id'],IMPL);self.assertEqual(capsule['B'],base)
        actual={row['path']:row for row in capsule['files']}
        self.assertEqual(set(actual),set(bundle['scope']['read']))
        for path,row in actual.items():
            raw=(repo/path).read_bytes()
            self.assertEqual(row['text'],raw.decode())
            self.assertEqual(row['sha256'],sha(raw))
            self.assertIs(row['writable'],path in bundle['scope']['write'])
        self.assertEqual(value['metadata']['title'],bundle['scope']['issue_title']+'（#71）')
        self.assertEqual(value['metadata']['body'],f'Refs #71\n\nCodex: {IMPL}\nCodex-Acceptance: {TESTER}\n')
        self.assertEqual(value['test_trailers'],['Acceptance-Test-Author: test-engineer','Codex: '+TESTER])
        # A forced old timestamp exposes both in-place rewrite and atomic replace.
        os.utime(target,ns=(1234567890000000000,1234567890000000000))
        before=(target.stat().st_ino,target.stat().st_mtime_ns,target.read_bytes())
        self.assertEqual(self.prepare(bundle,repo,output,base)['action'],'unchanged')
        self.assertEqual(before,(target.stat().st_ino,target.stat().st_mtime_ns,target.read_bytes()))

    def test_02_ordinary_tests_have_identity_without_acceptance_role_marker(self):
        bundle,repo,output,base=self.fixture([ORDINARY_PATH])
        bundle['metadata']['title']=bundle['scope']['issue_title']+'（#71）'
        self.assertEqual(self.prepare(bundle,repo,output,base)['action'],'prepared')
        value=json.loads((output/'bundle.json').read_text())
        self.assertEqual(value['test_trailers'],['Codex: '+TESTER])

    def test_03_wrong_author_title_base_digest_or_missing_input_holds(self):
        for mutation in ('author','same-author','registry','duplicate-registry','title','stale-base','acceptance-base','blob','missing-scope','missing-description','missing-registry','missing-acceptance','missing-title','empty-files','repo-id','repository','workdir','invalid-card','invalid-revision'):
            with self.subTest(mutation=mutation):
                bundle,repo,output,base=self.fixture();observed=base
                if mutation=='author':bundle['acceptance']['author']=IMPL
                elif mutation=='same-author':bundle['scope']['acceptance_session']=IMPL;bundle['acceptance']['author']=IMPL
                elif mutation=='registry':bundle['registry']['roles'][1]['id']='33333333-3333-4333-8333-333333333333'
                elif mutation=='duplicate-registry':bundle['registry']['roles'].append(copy.deepcopy(bundle['registry']['roles'][1]))
                elif mutation=='title':bundle['metadata']['title']='unapproved proposed title'
                elif mutation=='stale-base':observed='e'*40
                elif mutation=='acceptance-base':bundle['acceptance']['B']='e'*40
                elif mutation=='blob':bundle['acceptance']['files'][ACCEPTANCE_PATH]='e'*64
                elif mutation=='missing-description':del bundle['scope']['description']
                elif mutation=='missing-title':del bundle['scope']['issue_title']
                elif mutation=='empty-files':bundle['acceptance']['files']={}
                elif mutation=='repo-id':bundle['repo_id']=1
                elif mutation=='repository':bundle['repository']='other/repo'
                elif mutation=='workdir':bundle['workdir']=str(repo.parent)
                elif mutation=='invalid-card':bundle['card']=True
                elif mutation=='invalid-revision':bundle['revision']=0
                else:del bundle[mutation.removeprefix('missing-')]
                self.held(bundle,repo,output,observed)

    def test_04_existing_different_output_is_never_overwritten(self):
        bundle,repo,output,base=self.fixture()
        self.assertEqual(self.prepare(bundle,repo,output,base)['action'],'prepared')
        target=output/'bundle.json';before=(target.stat().st_ino,target.stat().st_mtime_ns,target.read_bytes())
        changed=copy.deepcopy(bundle);changed['scope']['description']='A distinct signed task input.'
        self.assertEqual(self.prepare(changed,repo,output,base)['action'],'hold')
        self.assertEqual(before,(target.stat().st_ino,target.stat().st_mtime_ns,target.read_bytes()))
        self.assertEqual(sorted(p.name for p in output.iterdir()),['bundle.json'])

    def test_05_mixed_role_paths_and_invalid_read_write_scope_hold(self):
        bundle,repo,output,base=self.fixture([ACCEPTANCE_PATH,ORDINARY_PATH])
        self.held(bundle,repo,output,base)
        for mutation in ('absolute','parent','duplicate-read','duplicate-write','write-outside-read','missing-file'):
            with self.subTest(mutation=mutation):
                bundle,repo,output,base=self.fixture()
                if mutation=='absolute':bundle['scope']['read'].append('/outside/secret')
                elif mutation=='parent':bundle['scope']['read'].append('../outside')
                elif mutation=='duplicate-read':bundle['scope']['read'].append('fixture/logic.py')
                elif mutation=='duplicate-write':bundle['scope']['write'].append('fixture/logic.py')
                elif mutation=='write-outside-read':bundle['scope']['write'].append('fixture/unread.py')
                else:bundle['scope']['read'].append('fixture/missing.py')
                self.held(bundle,repo,output,base)


class HistoricalReplayAcceptance(Fixtures):
    def replay(self,label):
        rows=json.loads((PACKAGE/'historical-evidence.json').read_text())['records']
        record=next(row for row in rows if row['label']==label)
        root,repo,base=self.workspace()
        for row in record['files']:
            raw=(PACKAGE/row['export_path']).read_bytes()
            self.assertEqual(sha(raw),row['sha256'])
            put(repo,row['path'],raw)
        head=commit(repo,record['message'])
        return record,GUARD.violations(base,head,str(repo))

    def test_history_01_real_card59_initial_artifact_is_blocked_by_missing_role_trailer(self):
        record,violations=self.replay('card59_missing')
        self.assertNotIn('Acceptance-Test-Author:',record['message'])
        self.assertTrue(all(GUARD.ACCEPTANCE.match(row['path']) for row in record['files']))
        self.assertEqual(violations,[],'HISTORICAL_CARD59_MISSING_ACCEPTANCE_TRAILER')

    def test_history_02_real_card30_marker_with_ordinary_paths_is_blocked(self):
        record,violations=self.replay('card30_wrong_path')
        self.assertIn('Acceptance-Test-Author: test-engineer',record['message'])
        self.assertFalse(any(GUARD.ACCEPTANCE.match(row['path']) for row in record['files']))
        self.assertEqual(violations,[],'HISTORICAL_CARD30_ACCEPTANCE_MARKER_PATH_CONFLICT')

    def test_history_03_real_card59_corrected_role_trailer_passes_existing_guard(self):
        record,violations=self.replay('card59_corrected')
        self.assertIn('Acceptance-Test-Author: test-engineer',record['message'])
        self.assertEqual(violations,[])

    def test_history_04_real_card71_ordinary_path_does_not_need_role_marker(self):
        record,violations=self.replay('card71_ordinary')
        self.assertNotIn('Acceptance-Test-Author:',record['message'])
        self.assertIn('Codex-Acceptance:',record['message'])
        self.assertFalse(any(GUARD.ACCEPTANCE.match(row['path']) for row in record['files']))
        self.assertEqual(violations,[])


def main():
    global CFG,PREPARE,GUARD
    p=argparse.ArgumentParser()
    p.add_argument('--mode',choices=('history','candidate'))
    p.add_argument('--stage',choices=('red','green'))
    p.add_argument('--parent-netns',required=True)
    p.add_argument('--source-closure-sha256')
    p.add_argument('--runner-temp',type=Path,required=True)
    p.add_argument('--evidence-dir',type=Path,required=True)
    p.add_argument('--source-root',type=Path)
    p.add_argument('--source-sha256')
    CFG=p.parse_args()
    selected='history' if CFG.stage=='red' else 'candidate' if CFG.stage=='green' else CFG.mode
    if selected is None or (CFG.mode is not None and CFG.mode!=selected):raise SystemExit('Specify consistent --mode or --stage')
    CFG.mode=selected
    CFG.runner_temp=CFG.runner_temp.resolve(strict=True);evidence=CFG.evidence_dir.resolve(strict=True)
    def finish(value,code):
        text=json.dumps(value,indent=2,sort_keys=True)+'\n';(evidence/'verdict.json').write_text(text);print(text,end='');return code
    if any(evidence.iterdir()):raise SystemExit('Fresh evidence directory required')
    try:
        if sys.platform!='linux' or os.environ.get('RUNNER_ENVIRONMENT')!='github-hosted' or os.readlink('/proc/self/ns/net')==CFG.parent_netns:raise ValueError('isolated hosted runner required')
        pins=json.loads((PACKAGE/'DEPENDENCIES.json').read_text())['files_sha256']
        for name,pin in pins.items():
            if sha((PACKAGE/name).read_bytes())!=pin:raise ValueError('acceptance dependency pin mismatch')
        sys.path.insert(0,str(PACKAGE/'guard-source'))
        GUARD=importlib.import_module('acceptance_guard')
        if CFG.source_closure_sha256 is not None:
            if CFG.source_root is None:raise ValueError('source root required for closure pin')
            source=CFG.source_root.resolve(strict=True);raw=(source/'SOURCE-CLOSURE.json').read_bytes()
            if sha(raw)!=CFG.source_closure_sha256:raise ValueError('source closure mismatch')
            closure=json.loads(raw)['files_sha256']
            for name,pin in closure.items():
                path=(source/name).resolve(strict=True);path.relative_to(source)
                if sha(path.read_bytes())!=pin:raise ValueError('source dependency mismatch')
            if CFG.mode=='candidate':
                if 'prepare_card.py' not in closure:raise ValueError('PREPARE_CARD_CAPABILITY_HOLD')
                CFG.source_sha256=closure['prepare_card.py']
        if CFG.mode=='candidate':
            if CFG.source_root is None or CFG.source_sha256 is None:raise ValueError('independently pinned source required')
            source=CFG.source_root.resolve(strict=True);entry=source/'prepare_card.py'
            if not entry.is_file():raise ValueError('PREPARE_CARD_CAPABILITY_HOLD')
            if sha(entry.read_bytes())!=CFG.source_sha256:raise ValueError('prepare source pin mismatch')
            sys.path.insert(0,str(source));PREPARE=importlib.import_module('prepare_card')
            if not callable(getattr(PREPARE,'prepare',None)):raise ValueError('PREPARE_CARD_CAPABILITY_HOLD')
    except Exception as error:
        return finish(dict(classification='capability-or-setup-HOLD',tests=0,reason=str(error)),86)
    cls=HistoricalReplayAcceptance if CFG.mode=='history' else PreparationAcceptance
    result=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(cls))
    expected_history=(CFG.mode=='history' and result.testsRun==4 and len(result.failures)==2 and not result.errors and not result.skipped
                      and all('HISTORICAL_CARD59_MISSING_ACCEPTANCE_TRAILER' in trace or 'HISTORICAL_CARD30_ACCEPTANCE_MARKER_PATH_CONFLICT' in trace for _,trace in result.failures))
    hold=bool(result.errors or result.skipped)
    classification=('execution-HOLD' if hold else 'recorded-history-guard-assertion-red' if expected_history else 'passed' if result.wasSuccessful() else 'assertion-red')
    return finish(dict(classification=classification,mode=CFG.mode,tests=result.testsRun,failures=len(result.failures),errors=len(result.errors),skips=len(result.skipped),historical_replay_is_prepare_api_proof=False,live_delivery_proven=False),86 if hold else 0 if result.wasSuccessful() else 1)


if __name__=='__main__':raise SystemExit(main())
