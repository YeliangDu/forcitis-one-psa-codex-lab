"""R3 additive actual v4 fault slices over the passed real fixture front.

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
                    header,name=rawrow.split(bytes([9]),1);mode,kind,oidraw=header.decode().split();child=oidraw
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
        upgrades=directory/'upgrades';upgrades.mkdir(mode=0o700)
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
        approval_path=upgrades/(upgrade_id+'.json');approval_path.write_text(json.dumps(approved));approval_path.chmod(0o600)
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
            if phase=='once':
                command=[sys.executable,'-I','-S','-B',str(PACKAGE/'fault_supervisor.py'),
                         '--fault',args.fault,'--work',str(work),'--receiver',str(receiver),
                         '--head',H,'--base',O,'--report',str(trace),'--',*argv]
            else:
                command=[tracer,'-f','-qq','-s','4096','-e','trace=execve','-o',str(trace),*argv]
            result=subprocess.run(command,cwd=work,env=childenv,capture_output=True,timeout=90)
            raw=result.stdout
            try:value=json.loads(raw)
            except ValueError:value=None
            log=trace.read_text(errors='replace') if trace.exists() else ''
            if phase=='once':
                try:actual_git=json.loads(log)['Git_exec_counts']
                except (ValueError,KeyError):raise Hold('actual fault syscall evidence unavailable')
            else:
                actual_git={kind:sum('execve("/usr/bin/git"' in line and '"'+kind+'"' in line for line in log.splitlines()) for kind in ['push','merge-tree','commit-tree']}
            record=dict(label=label,phase=phase,stage=stage,scope_sha256=sha(path.read_bytes()),argv=argv,exit_code=result.returncode,stdout_sha256=sha(raw),stderr_sha256=sha(result.stderr),raw_stdout=raw.decode(errors='replace'),actual_Git_execs=actual_git,trace_sha256=sha(trace.read_bytes()) if trace.exists() else None)
            phases.append(record);(args.evidence_dir/'phase-observations.json').write_text(json.dumps(phases,indent=2)+chr(10))
            preserve()
            if phase!='once':check(all(count==0 for count in actual_git.values()),'NON_ONCE_PHASE_SENT_OR_CONSTRUCTED_MERGE')
            return result,value,actual_git
        def held(value):return type(value)is dict and value.get('result',{}).get('action')=='hold'
        result,value,_=invoke('admit','admit','before')
        if not value or value.get('result',{}).get('action')!='admitted':raise Hold('actual front/admit not reached')
        check(journal.load_upgrade(42)is None and (directory/'merge.queue').read_text().strip()=='42','ADMISSION_NOT_REAL')
        if args.fault=='push-nonzero':
            # Stage foreign actor object under NON-main ref; preimage is still O.
            git('--git-dir='+str(receiver),'fetch','--no-tags',work.as_uri(),H+':refs/fixture/preimage-actor')
            check(git('--git-dir='+str(receiver),'rev-parse','refs/heads/main')==O,'FAULT_PRESTAGING_MOVED_MAIN')
        result,value,executions=invoke('fault-once','once','before')
        syscall_path=args.evidence_dir/'fault-once.exec.log'
        fault=json.loads(syscall_path.read_text())
        if not fault.get('barrier_reached') or fault.get('classification')!='actual-owned-child-fault-observed':
            raise Hold('required actual fault/owned-child barrier not reached')
        if not value or type(value.get('result'))is not dict:raise Hold('actual candidate did not return structured fault outcome')
        check(result.returncode==0 and held(value),'FAULT_NOT_STRUCTURED_HOLD')
        pair=journal.load_upgrade(42)
        check(type(pair)is dict and pair['phase']=='reserved','FAULT_LOST_DURABLE_RESERVATION')
        if args.fault=='lock-name':
            check(value['push_attempts']==0 and executions['push']==0,'NAMED_LOCK_REPLACEMENT_DISPATCHED_PUSH')
            check(fault.get('original_named_lock_restored_after_candidate_exit') is True,'ORIGINAL_LOCK_NOT_RESTORED_AFTER_TERMINAL')
            expected_tip=O
        else:
            check(value['push_attempts']==1 and executions['push']==1,'NONZERO_WAS_NOT_ACTUALLY_ATTEMPTED')
            codes=fault.get('Git_push_exit_codes',[])
            if len(codes)!=1:raise Hold('actual Git push terminal status unavailable')
            check(codes[0]!=0,'CONTROLLED_PREIMAGE_FAULT_DID_NOT_FAIL_REAL_PUSH')
            check(fault['test_only_fault_actor']['exit_code']==0,'FOREIGN_PREIMAGE_CAS_NOT_REAL')
            expected_tip=H
        tip=git('--git-dir='+str(receiver),'rev-parse','refs/heads/main')
        check(tip==expected_tip,'FAULT_RECEIVER_REF_UNEXPECTED')
        receiver_observation=dict(ref=tip,parents=git('--git-dir='+str(receiver),'show','-s','--format=%P',tip).split(),
                                  tree=git('--git-dir='+str(receiver),'show','-s','--format=%T',tip))
        (args.evidence_dir/'actual-receiver-after-fault.json').write_text(json.dumps(receiver_observation,indent=2)+chr(10))
        before_recovery=copy.deepcopy(pair)
        def actual_unmerged_snapshot(policy,observations):
            refkey=json.dumps(['git/ref/heads/main',False,False],separators=(',',':'))
            observations[refkey]['json']['object']['sha']=tip
            prkey=json.dumps(['pulls/42',False,False],separators=(',',':'))
            observations[prkey]['json']['merged']=False
            observations[prkey]['json']['state']='open'
            observations[prkey]['json']['base']['sha']=tip
        for index in range(2):
            _,recovered,io=invoke('readonly-recover-'+str(index),'recover','before',actual_unmerged_snapshot)
            if not recovered or type(recovered.get('result'))is not dict:raise Hold('actual read-only recovery unavailable')
            check(held(recovered) and io==dict(push=0,**{'merge-tree':0,'commit-tree':0}),'FAULT_RECOVERY_RESENT_OR_BUILT_MERGE')
            check(journal.load_upgrade(42)==before_recovery,'RECOVERY_CHANGED_UNKNOWN_PAIR')
            check(git('--git-dir='+str(receiver),'rev-parse','refs/heads/main')==tip,'RECOVERY_CHANGED_RECEIVER')
        preserve()
        (args.evidence_dir/'history-lock-preservation.json').write_text(json.dumps(dict(before=original_rows,after=rows(),locks=locks),indent=2)+chr(10))
        report=dict(classification='R3-actual-fault-reserved-readonly-recovery-observed',fault=args.fault,O=O,H=H,S=S,
                    upgrade_id=upgrade_id,actual_candidate_push_attempts=value['push_attempts'],actual_Git_push_execs=executions['push'],
                    pair_phase='reserved',repeat_recoveries=2,recovery_pushes=0,old_history_and_original_lock_names_preserved=True,
                    actual_receiver=receiver_observation,synthetic_native_observations_only=True,timeout_covered=False,
                    OpenSSH_covered=False,native_authenticated=False,canonical_authority=False,activation_authority=False)
        (args.evidence_dir/'fault-report.json').write_text(json.dumps(report,indent=2)+chr(10));return report

def main():
    p=argparse.ArgumentParser(allow_abbrev=False);p.add_argument('--runner-temp',required=True,type=Path);p.add_argument('--evidence-dir',required=True,type=Path);p.add_argument('--parent-netns',required=True);p.add_argument('--fault',required=True,choices=('lock-name','push-nonzero'));args=p.parse_args();args.evidence_dir=args.evidence_dir.resolve(strict=True)
    if any(args.evidence_dir.iterdir()):raise Hold('fresh evidence directory required')
    try:report=run(args);verdict=dict(classification=report['classification'],production_authority=False);code=0
    except ActualUnknown as e:verdict=dict(classification='actual-reserved-send-outcome-failed-or-unknown',reason=str(e),production_authority=False);code=2
    except AssertionError as e:verdict=dict(classification='reached-R3-behavior-assertion-failure',reason=str(e),production_authority=False);code=1
    except Exception as e:verdict=dict(classification='capability-or-proof-fixture-HOLD',reason=type(e).__name__+': '+str(e)[:1000],production_authority=False);code=86
    (args.evidence_dir/'verdict.json').write_text(json.dumps(verdict,indent=2)+chr(10));print(json.dumps(verdict));return code
if __name__=='__main__':raise SystemExit(main())
