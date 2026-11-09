"""R3 isolated actual native/installer/writer chain over real file Git receiver.

Native-shaped GitHub observations are TEST-ONLY fixtures, never authentication.
All Git mutations and product execution occur only in disposable hosted scratch.
"""
import argparse
import base64
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import sqlite3
import subprocess
import sys
import tempfile
import uuid

MATERIAL_SHA256='339825d0c3a3aa269f2028d7518ebd384b33020d6be8ed78d66a84f4ee37f6a8'
PACKAGE=Path(__file__).resolve().parent
class Hold(RuntimeError):pass
class ActualUnknown(RuntimeError):pass

def sha(raw):return hashlib.sha256(raw).hexdigest()
def canonical(v):return json.dumps(v,sort_keys=True,separators=(',',':'),allow_nan=False)
def check(ok,label):
    if not ok:raise AssertionError(label)
def load(name,path,pin):
    raw=path.read_bytes()
    if sha(raw)!=pin:raise Hold('independent helper byte pin differs')
    spec=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(spec);sys.modules[name]=m
    exec(compile(raw,str(path),'exec'),m.__dict__);return m

def run(args):
    if sys.platform!='linux' or os.geteuid()==0 or os.environ.get('RUNNER_ENVIRONMENT')!='github-hosted':raise Hold('non-root hosted Linux required')
    if os.readlink('/proc/self/ns/net')==args.parent_netns:raise Hold('private network namespace required')
    if 'NoNewPrivs:'+chr(9)+'1' not in Path('/proc/self/status').read_text():raise Hold('no-new-privileges required')
    if any(os.environ.get(k) for k in ('GH_TOKEN','GITHUB_TOKEN','GH_CONFIG_DIR','SSH_AUTH_SOCK','OPENAI_API_KEY')):raise Hold('credential environment refused')
    tracer=next((p for p in ('/usr/bin/strace','/bin/strace') if Path(p).is_file()),None)
    ip=next((p for p in ('/usr/sbin/ip','/usr/bin/ip') if Path(p).is_file()),None)
    if not tracer or not ip:raise Hold('real strace/ip required')
    links=subprocess.check_output([ip,'-o','link','show'],text=True,env={'PATH':'/usr/bin:/bin'},timeout=5)
    if [x.split(':',2)[1].strip().split('@',1)[0] for x in links.splitlines() if x.strip()]!=['lo']:raise Hold('loopback-only namespace required')
    if sha((PACKAGE/'MATERIAL.json').read_bytes())!=MATERIAL_SHA256:raise Hold('material manifest differs')
    meta=json.loads((PACKAGE/'MATERIAL.json').read_text())
    for name,record in meta['files'].items():
        p=PACKAGE/'runtime-source'/name
        if p.resolve(strict=True)!=p or sha(p.read_bytes())!=record['sha256']:raise Hold('runtime source differs')
    if sha((PACKAGE/'entry.py').read_bytes())!=meta['entry_sha256']:raise Hold('actual R3 entry differs')
    builder=load('_independent_r3_builder',PACKAGE/'native_fixture_builder.py',meta['builder_sha256'])
    history=load('_independent_r3_history',PACKAGE/'history_fixture.py',meta['history_fixture_sha256'])
    temporary=args.runner_temp.resolve(strict=True)
    if not temporary.is_dir():raise Hold('owned runner temporary root missing')
    with tempfile.TemporaryDirectory(prefix='lab-r3-',dir=temporary) as t:
        root=Path(t).resolve();root.chmod(0o700);work=root/'forcitis-one-psa-codex-lab';receiver=root/'receiver.git';runtime=root/'runtime';runtime.mkdir(mode=0o700)
        env=dict(PATH='/usr/bin:/bin',HOME=str(root),LANG='C.UTF-8',TZ='UTC',GIT_CONFIG_GLOBAL='/dev/null',GIT_CONFIG_NOSYSTEM='1',GIT_TERMINAL_PROMPT='0',GIT_ALLOW_PROTOCOL='file',GIT_AUTHOR_NAME='R3 Fixture',GIT_AUTHOR_EMAIL='r3@example.invalid',GIT_COMMITTER_NAME='R3 Fixture',GIT_COMMITTER_EMAIL='r3@example.invalid',PYTHONDONTWRITEBYTECODE='1')
        def git(*argv,at=work):return subprocess.check_output(['/usr/bin/git','-c','core.hooksPath=/dev/null',*argv],cwd=at if Path(at).exists() else root,env=env,input=b'',stderr=subprocess.PIPE,timeout=30).decode().strip()
        git('init','--initial-branch=main',str(work),at=root);git('init','--bare',str(receiver),at=root)
        names=sorted(meta['files']);modes={n:v['mode'] for n,v in meta['files'].items()}
        contents={n:(PACKAGE/'runtime-source'/n).read_bytes() for n in names}
        old_contents=dict(contents);old_contents['.github/workflows/lab-pr-ci.yml']=base64.b64decode(meta['old_workflow_base64'])
        def write_tree(values):
            for n,raw in values.items():
                p=work/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(raw);p.chmod(0o755 if modes[n]=='100755' else 0o644)
        write_tree(old_contents);git('add','--all');git('commit','-m','synthetic predecessor base')
        old_base=git('rev-parse','HEAD');git('commit','--allow-empty','-m','synthetic predecessor head');old_head=git('rev-parse','HEAD')
        old_tree=git('rev-parse','HEAD^{tree}');O=git('commit-tree',old_tree,'-p',old_base,'-p',old_head,'-m','Synthetic prior adopted control')
        git('update-ref','refs/heads/main',O);git('checkout','--detach',O)
        write_tree(contents);git('add','--all');git('commit','-m','synthetic independent new protected candidate');H=git('rev-parse','HEAD')
        git('commit','--allow-empty','-m','synthetic independent reviewed source');S=git('rev-parse','HEAD')
        new_tree=git('rev-parse',H+'^{tree}');M0=git('commit-tree',new_tree,'-p',O,'-p',H,'-m','Lab controlled main release #42')
        git('checkout','--detach',O);git('remote','add','origin',receiver.as_uri());git('--git-dir='+str(receiver),'fetch','--no-tags',work.as_uri(),O+':refs/heads/main')
        def actual_trees(commit):
            trees={}
            def visit(oid):
                rows=[]
                for rawrow in subprocess.check_output(['/usr/bin/git','ls-tree','-z',oid],cwd=work,env=env).split(bytes([0])):
                    if not rawrow:continue
                    header,name=rawrow.split(bytes([9]),1);mode,kind,oidraw=header.decode().split();child=oidraw.decode()
                    rows.append(dict(path=name.decode(),mode=mode,type=kind,sha=child))
                    if kind=='tree':visit(child)
                trees[oid]=dict(sha=oid,truncated=False,tree=rows)
            tree=git('rev-parse',commit+'^{tree}');visit(tree);return tree,trees
        source_tree,trees=actual_trees(S)
        actual=dict(source_commit=S,source_tree=source_tree,trees=trees,runtime_paths=meta['runtime_paths'],old_workflow_base64=meta['old_workflow_base64'],files={n:dict(content_base64=base64.b64encode(raw).decode(),sha256=sha(raw),mode=modes[n]) for n,raw in contents.items()})
        builder.BASE=O;builder.HEAD=H;builder.SOURCE=S;builder.PHASE_PATHS={'old':names,'new':names};builder.PHASE_MODES={'old':modes,'new':modes}
        upgrade_id=str(uuid.uuid4())
        def build(M):
            builder.MERGE=M
            fixture,reference=builder.build(actual)
            fixture['approval']['upgrade_id']=upgrade_id
            reference['successor_adoption']['upgrade_id']=upgrade_id
            for policy in fixture['policy_by_stage'].values():policy['expected_approval']=fixture['approval']
            (args.evidence_dir/('synthetic-stages-'+M+'.json')).write_text(json.dumps(fixture,indent=2)+chr(10))
            objects={}
            for label,oid in [('O',O),('H',H),('S',S),('M',M)]:
                raw_commit=subprocess.check_output(['/usr/bin/git','cat-file','commit',oid],cwd=work,env=env)
                check(hashlib.sha1(b'commit '+str(len(raw_commit)).encode()+bytes([0])+raw_commit).hexdigest()==oid,'REAL_COMMIT_OID_MISMATCH')
                objects[label]=dict(oid=oid,raw_base64=base64.b64encode(raw_commit).decode(),raw_sha256=sha(raw_commit),tree=git('rev-parse',oid+'^{tree}'))
            (args.evidence_dir/('real-git-objects-'+M+'.json')).write_text(json.dumps(objects,indent=2)+chr(10))
            # Every Source Git API tree/blob/commit object must exist in real scratch Git.
            checked=0
            for key,row in fixture['raw_get']['before'].items():
                path,pages,binary=json.loads(key)
                if path.startswith('git/blobs/'):
                    oid=path.split('/')[-1];raw=base64.b64decode(row['json']['content']);observed=subprocess.check_output(['/usr/bin/git','cat-file','blob',oid],cwd=work,env=env);check(observed==raw,'SYNTHETIC_BLOB_NOT_REAL_GIT_BYTES');checked+=1
                elif path.startswith('git/trees/'):
                    oid=path.split('/')[-1];check(git('cat-file','-t',oid)=='tree','SYNTHETIC_TREE_NOT_REAL_OBJECT')
                elif path.startswith('git/commits/'):
                    oid=path.split('/')[-1];check(git('rev-parse',oid+'^{tree}')==row['json']['tree']['sha'],'SYNTHETIC_COMMIT_TREE_DIFFERS')
            return fixture,reference,checked
        fixture,reference,blob_count=build(M0);approved=copy.deepcopy(fixture['approval'])
        def install_runtime(commit):
            for p in list(runtime.rglob('*')):
                if p.is_file():p.unlink()
            for n in names:
                data=subprocess.check_output(['/usr/bin/git','show',commit+':'+n],cwd=work,env=env)
                p=runtime/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data);p.chmod(0o755 if modes[n]=='100755' else 0o644)
        install_runtime(S)
        sys.path[:0]=[str(runtime/'scripts/merge'),str(runtime/'scripts/session/codex_lab')]
        from mq_lab_journal import ReleaseJournal
        from mq_lab_journal_v2 import ReleaseJournalV2,migrate_v1
        directory=work/'.git/merge-queue';directory.mkdir(mode=0o700);database=directory/'releases.sqlite3'
        v1=ReleaseJournal(str(database))
        for n in range(1,10):
            row=dict(number=n,status='reserved',head=old_head,base=old_base,target='main',release_key='synthetic-ordinary-'+str(n))
            check(v1.reserve(row) and v1.confirm(dict(row,status='confirmed',merge_sha=O)),'HISTORICAL_ORDINARY_SEED_REFUSED')
        check(migrate_v1(str(database),str(directory/'v1.backup'),sha(database.read_bytes()))['action']=='migrated','HISTORICAL_V2_MIGRATION_REFUSED')
        journal=ReleaseJournalV2(database);prior=history.approval(43,old_base,old_head)
        check(journal.reserve_upgrade(prior)['action']=='newly-reserved','OLD_PAIR_SEED_REFUSED')
        check(journal.confirm_upgrade(prior,history.readback(prior,O))['action']=='confirmed','OLD_PAIR_CONFIRM_REFUSED')
        check(journal.adopt_public_upgrade(prior,history.synthetic_health(prior,O))['action']=='adopted','OLD_PAIR_ADOPT_FIXTURE_REFUSED')
        for name in ['merge.queue','merge.queue.heads','merge.queue.lock','merge.queue.heads-lock','merge.queue.merge-lock','control-upgrade.runner-lock']:
            p=directory/name;p.write_bytes(b'');p.chmod(0o600)
        upgrades=directory/'upgrades';upgrades.mkdir(mode=0o700);approval_path=upgrades/(upgrade_id+'.json');approval_path.write_text(json.dumps(approved));approval_path.chmod(0o600)
        def rows():
            with sqlite3.connect(database.as_uri()+'?mode=ro&immutable=1',uri=True) as db:
                return (db.execute('select * from releases where number!=42 order by number').fetchall(),db.execute('select * from upgrade_bindings where number!=42 order by number').fetchall())
        original_rows=rows();locks={p.name:(p.stat().st_dev,p.stat().st_ino) for p in directory.iterdir() if 'lock'in p.name}
        def preserve():
            check(rows()==original_rows,'OLD_HISTORY_CHANGED')
            check({p.name:(p.stat().st_dev,p.stat().st_ino) for p in directory.iterdir() if 'lock'in p.name}==locks,'ORIGINAL_LOCK_INODE_CHANGED')
        configpins={str(p.relative_to(root)):sha((p/'config').read_bytes()) for p in [work/'.git',receiver]}
        phases=[]
        def invoke(label,phase,stage,changes=None):
            observations=copy.deepcopy(fixture['raw_get'][stage]);policy=copy.deepcopy(fixture['policy_by_stage'][stage])
            if stage!='before':
                actual_tip=git('--git-dir='+str(receiver),'rev-parse','refs/heads/main')
                check(actual_tip==fixture['subject']['merge'],'POST_SEND_SNAPSHOT_NOT_CURRENT_RECEIVER')
                check(git('--git-dir='+str(receiver),'show','-s','--format=%P',actual_tip).split()==[O,H],
                      'POST_SEND_PARENTS_NOT_ACTUAL_RECEIVER')
                check(git('--git-dir='+str(receiver),'show','-s','--format=%T',actual_tip)==new_tree,
                      'POST_SEND_TREE_NOT_ACTUAL_RECEIVER')
            if changes:changes(policy,observations)
            files={str(p.relative_to(runtime)):sha(p.read_bytes()) for p in runtime.rglob('*') if p.is_file()}
            scope=dict(kind='lab-r3-disposable-scope/1',phase=phase,root=str(root),git_sha256=sha(Path('/usr/bin/git').read_bytes()),config_sha256=configpins,runtime_files_sha256=files,policy=policy,observations=observations)
            path=root/(label+'.scope.json');path.write_text(canonical(scope));path.chmod(0o600)
            (args.evidence_dir/(label+'.synthetic-scope.json')).write_bytes(path.read_bytes())
            trace=args.evidence_dir/(label+'.exec.log')
            childenv=dict(env,RUNNER_ENVIRONMENT='github-hosted',RUNNER_TEMP=str(temporary))
            argv=[sys.executable,'-I','-S','-B',str(PACKAGE/'entry.py'),'--scope',str(path),'--scope-sha256',sha(path.read_bytes()),'--phase',phase]
            result=subprocess.run([tracer,'-f','-qq','-s','4096','-e','trace=execve','-o',str(trace),*argv],cwd=work,env=childenv,capture_output=True,timeout=90)
            raw=result.stdout
            try:value=json.loads(raw)
            except ValueError:value=None
            log=trace.read_text(errors='replace') if trace.exists() else ''
            actual_git={kind:sum('execve("/usr/bin/git"' in line and '"'+kind+'"' in line for line in log.splitlines()) for kind in ['push','merge-tree','commit-tree']}
            record=dict(label=label,phase=phase,stage=stage,scope_sha256=sha(path.read_bytes()),argv=argv,exit_code=result.returncode,stdout_sha256=sha(raw),stderr_sha256=sha(result.stderr),raw_stdout=raw.decode(errors='replace'),actual_Git_execs=actual_git,trace_sha256=sha(trace.read_bytes()) if trace.exists() else None)
            phases.append(record);(args.evidence_dir/'phase-observations.json').write_text(json.dumps(phases,indent=2)+chr(10))
            preserve()
            if phase!='once':check(all(count==0 for count in actual_git.values()),'NON_ONCE_PHASE_SENT_OR_CONSTRUCTED_MERGE')
            return result,value,actual_git
        def held(value):return type(value)is dict and value.get('result',{}).get('action')=='hold'
        def wrong_head(policy,observation):
            policy['expected_approval']['head']=O
            for review in policy['expected_approval']['reviews'].values():review['head']=O
        for label,change in [('wrong-head',wrong_head),('missing-new-UUID',lambda p,o:p['expected_approval'].update(upgrade_id=str(uuid.uuid4())))]:
            result,value,_=invoke(label,'admit','before',change)
            if not value or type(value.get('result'))is not dict:raise Hold('negative admit did not reach structured native result')
            check(held(value) and journal.load_upgrade(42)is None and (directory/'merge.queue').read_bytes()==b'' and (directory/'merge.queue.heads').read_bytes()==b'','INVALID_NATIVE_AUTHORITY_RESERVED_OR_ADMITTED')
        result,value,_=invoke('admit','admit','before')
        if not value or value.get('result',{}).get('action')!='admitted':raise Hold('actual NativePorts proof/fixture admission unavailable; not assertion red')
        check(journal.load_upgrade(42)is None and (directory/'merge.queue').read_text().strip()=='42','ADMISSION_NOT_REAL_OR_RESERVED_EARLY')
        result,value,executions=invoke('once-unknown','once','before')
        pair=journal.load_upgrade(42)
        if result.returncode!=0 and pair is not None:
            # Actual reserved/tool/transport failure is not missing-capability red.
            invoke('failed-once-readonly-recovery','recover','before')
            check(journal.load_upgrade(42)['phase']=='reserved','FAILED_UNKNOWN_RESERVATION_LOST')
            raise ActualUnknown('actual once process/tool/transport failed after durable reservation; recovery only, no resend')
        if not value or value.get('push_attempts')!=1:raise Hold('actual leased file push not reached; lock-replacement zero-attempt fault requires its separate selection')
        check(held(value) and type(pair)is dict and pair.get('phase')=='reserved','ACTUAL_SEND_UNKNOWN_NOT_RESERVED')
        M=git('--git-dir='+str(receiver),'rev-parse','refs/heads/main')
        if M==O:
            invoke('attempted-unknown-readonly-recovery','recover','before')
            check(journal.load_upgrade(42)['phase']=='reserved','UNKNOWN_RESERVATION_LOST')
            raise ActualUnknown('actual push attempted but receiver unchanged; reserved/read-only recovery only')
        check(git('--git-dir='+str(receiver),'show','-s','--format=%P',M).split()==[O,H]
              and git('--git-dir='+str(receiver),'show','-s','--format=%T',M)==new_tree,'RECEIVER_EFFECT_NOT_EXACT')
        check(git('--git-dir='+str(receiver),'rev-parse','refs/heads/main')==M,'RECEIVER_MOVED_DURING_READBACK')
        fixture,reference,blob_count=build(M);check(fixture['approval']==approved,'APPROVAL_CHANGED_WHEN_REAL_M_REBOUND')
        git('checkout','--detach',M);install_runtime(M)
        def reversed_parents(policy,observations):
            key=json.dumps(['commits/'+M,False,False],separators=(',',':'));observations[key]['json']['parents']=[dict(sha=H),dict(sha=O)]
        _,value,_=invoke('wrong-parents-readonly','recover','merged-awaiting-health',reversed_parents)
        if not value or type(value.get('result'))is not dict:raise Hold('negative recovery native result unavailable')
        check(held(value) and journal.load_upgrade(42)['phase']=='reserved','WRONG_PARENT_PROOF_CONFIRMED_EFFECT')
        _,value,_=invoke('fresh-process-no-health','recover','merged-awaiting-health')
        check(held(value) and journal.load_upgrade(42)['phase']=='confirmed','NO_HEALTH_MUST_CONFIRM_BUT_NOT_ADOPT')
        def bad_health(policy,observations):
            for key,row in observations.items():
                route,pages,binary=json.loads(key)
                if route in ('actions/runs/701','actions/runs/701/attempts/1'):row['json']['head_sha']=H
        _,value,_=invoke('wrong-health','successor','healthy',bad_health)
        if not value or type(value.get('result'))is not dict:raise Hold('negative health native result unavailable')
        check(held(value) and journal.load_upgrade(42)['phase']=='confirmed','WRONG_HEALTH_ADOPTED')
        _,value,_=invoke('successor','successor','healthy')
        if not value or value.get('result',{}).get('action')!='adopted':raise Hold('actual fresh M/source/health fixture adoption unavailable')
        check(journal.load_upgrade(42)['phase']=='adopted' and (directory/'merge.queue').read_bytes()==b'' and (directory/'merge.queue.heads').read_bytes()==b'','ADOPTION_OR_CLEANUP_NOT_DURABLE')
        for i in range(2):
            _,value,_=invoke('repeat-recover-'+str(i),'recover','healthy')
            check(value.get('result',{}).get('action')=='adopted' and journal.load_upgrade(42)['phase']=='adopted','REPEATED_RECOVERY_CHANGED_ADOPTED_EFFECT')
        preserve()
        (args.evidence_dir/'history-and-lock-preservation.json').write_text(json.dumps(dict(before_rows=original_rows,after_rows=rows(),original_lock_inodes=locks,all_phases_preserved=True),indent=2)+chr(10))
        report=dict(classification='R3-disposable-native-shaped-actual-file-CAS-chain-passed',O=O,H=H,S=S,M=M,
            preexisting_merge_object=M0,new_upgrade_id=upgrade_id,number=42,real_Git_blobs_checked=blob_count,
            phases=len(phases),actual_push_execs=sum(x['actual_Git_execs']['push'] for x in phases),
            old_rows_and_lock_inodes_preserved=True,initial_FIFO_and_heads_empty=True,
            native_authenticated=False,transport='file-only-not-OpenSSH',activation_authority=False,overall_production_acceptance=False)
        check(report['actual_push_execs']==1,'MORE_THAN_ONE_ACTUAL_PUSH')
        (args.evidence_dir/'chain-report.json').write_text(json.dumps(report,indent=2)+chr(10));return report

def main():
    p=argparse.ArgumentParser(allow_abbrev=False);p.add_argument('--runner-temp',required=True,type=Path);p.add_argument('--evidence-dir',required=True,type=Path);p.add_argument('--parent-netns',required=True);args=p.parse_args();args.evidence_dir=args.evidence_dir.resolve(strict=True)
    if any(args.evidence_dir.iterdir()):raise Hold('fresh evidence directory required')
    try:report=run(args);verdict=dict(classification=report['classification'],production_authority=False);code=0
    except ActualUnknown as e:verdict=dict(classification='actual-reserved-send-outcome-failed-or-unknown',reason=str(e),production_authority=False);code=2
    except AssertionError as e:verdict=dict(classification='reached-R3-behavior-assertion-failure',reason=str(e),production_authority=False);code=1
    except Exception as e:verdict=dict(classification='capability-or-proof-fixture-HOLD',reason=type(e).__name__+': '+str(e)[:1000],production_authority=False);code=86
    (args.evidence_dir/'verdict.json').write_text(json.dumps(verdict,indent=2)+chr(10));print(json.dumps(verdict));return code
if __name__=='__main__':raise SystemExit(main())
