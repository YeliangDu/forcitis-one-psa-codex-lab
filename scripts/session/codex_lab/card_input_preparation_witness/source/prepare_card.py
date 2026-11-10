#!/usr/bin/env python3
"""Prepare local Lab card inputs. No dispatch, publish, commit or installation."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import tempfile
from uuid import UUID

REPO = 'YeliangDu/forcitis-one-psa-codex-lab'
RID = 1449415666
DELIVERY = '8f8017d32bb2cfae3e2442a6272913044f713c734a705bb9934ce985d5f15894'
STAGES = ('首轮', '终审')
LANES = {'backend','frontend','light','secret','shadow'}
GUARDED = re.compile(r'^(backend/src/test/java/com/forcitis/one/acceptance/.+|backend/src/test/resources/acceptance/.+|frontend/(?!.*node_modules/)(.+/)?__acceptance__/.+)$')
TEST = re.compile(r'^(backend/src/test/|frontend/.*(__acceptance__/|\.test\.|\.spec\.)|scripts/.*(selftest|_test)\.py$)')
TITLE = re.compile(r'[a-z]+\([a-z0-9][a-z0-9_-]*\): [^\r\n]+')
BRANCH = re.compile(r'(codex|lab)/[a-z0-9][a-z0-9/_-]*')
MAX_JSON = 4 * 1024 * 1024


class Hold(ValueError):
    pass


def need(condition, reason):
    if not condition:
        raise Hold(reason)


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def wire(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), allow_nan=False).encode()


def digest(value, length=40):
    return type(value) is str and re.fullmatch('[0-9a-f]{'+str(length)+'}', value) is not None


def identity(value):
    try:
        return type(value) is str and str(UUID(value)) == value
    except ValueError:
        return False


def read(path):
    need(path.resolve(strict=True) == path and path.is_file() and path.stat().st_size <= MAX_JSON, 'invalid-input-file')
    raw=path.read_bytes()
    return raw, json.loads(raw,object_pairs_hook=unique)


def unique(pairs):
    result={}
    for k,v in pairs:
        need(k not in result,'duplicate-json-key:'+k)
        result[k]=v
    return result


def git(repo,*args):
    env={k:v for k,v in os.environ.items() if not k.startswith('GIT_')}
    env.update(GIT_CONFIG_NOSYSTEM='1',GIT_CONFIG_GLOBAL='/dev/null',GIT_TERMINAL_PROMPT='0',GIT_OPTIONAL_LOCKS='0')
    result=subprocess.run(['/usr/bin/git','--no-replace-objects',*args],cwd=repo,env=env,
                          stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,timeout=30)
    need(result.returncode==0,'git-read-failed:'+args[0])
    return result.stdout


def parents(repo,commit):
    return git(repo,'show','-s','--format=%P',commit).decode().split()


def diff(repo,base,head):
    return [p.decode() for p in git(repo,'diff','--name-only','--no-renames','-z',base,head).split(b'\0') if p]


def safe_path(path):
    return (type(path) is str and len(path)<=256 and re.fullmatch(r'[a-zA-Z0-9_./-]+',path)
            and all(p not in ('','.','..','.git','.codex-lab') for p in path.split('/')))


def trailer(repo,commit,key):
    return git(repo,'log','-1',f'--format=%(trailers:key={key},valueonly,unfold)',commit).decode().split()


def content(repo,commit,path):
    need(safe_path(path),'unsafe-repo-path')
    row=git(repo,'ls-tree','-z',commit,'--',path).split(b'\0')
    need(len(row)==2 and not row[-1],'scope-file-not-found:'+path)
    header,name=row[0].split(b'\t',1)
    mode,kind,oid=header.split()
    need(name.decode()==path and kind==b'blob' and mode in (b'100644',b'100755'),'scope-file-not-regular:'+path)
    raw=git(repo,'cat-file','blob',oid.decode())
    need(len(raw)<=65536 and b'\0' not in raw,'scope-file-over-limit:'+path)
    return raw,oid.decode()


def roles(reg):
    rows=reg['roles']
    result={x['role']:x['id'] for x in rows}
    need(len(result)==len(rows)==len(set(result.values())) and all(identity(v) for v in result.values()),'invalid-role-registry')
    return result


def metadata(pr):
    return dict(title=pr['title'],body=pr['body'],draft=pr['draft'],
                labels=sorted(x['name'] for x in pr['labels']),
                refs={k:pr[k]['ref'] for k in ('head','base')})


def declarations(body):
    fence=None; lines=[]
    for line in body.splitlines():
        m=re.match(r'^ {0,3}(`{3,}|~{3,})(.*)$',line)
        if m:
            marker=m[1]
            if fence is None:fence=marker
            elif marker[0]==fence[0] and len(marker)>=len(fence) and not m[2].strip():fence=None
            continue
        if fence is None and re.fullmatch(r'(?:Codex|Codex-Acceptance): .+',line):
            lines.append(line)
    return sorted(lines)


def source_pins(b):
    for k in ('issue_body_sha256','signed_scope_sha256'):
        need(digest(b['scope'][k],64),'invalid-scope-evidence:'+k)
    need(digest(b['acceptance']['receipt_sha256'],64),'invalid-acceptance-receipt')


def delivery_inputs(repo,b,role_ids,base,observed_base,mode,meta):
    data=b['delivery'];pr=data['pr'];ci=data['ci'];proof=ci['proof'];contribution=data['contribution']
    n=pr['number'];head=pr['head']['sha'];author=b['scope']['implementation_session']
    need(type(n) is int and n>0 and digest(head) and pr['base']['sha']==base
         and pr['base']['ref']=='main' and pr['head']['ref']==b['branch'],'delivery-pr-subject-drift')
    need(all(x['repo']['id']==RID and x['repo']['full_name']==REPO for x in (pr['base'],pr['head'])),'delivery-pr-repository-drift')
    body=pr['body'];need(pr['title']==meta['title'] and body.splitlines()[0] in ('Refs #'+str(b['card']),'Closes #'+str(b['card']))
         and declarations(body)==declarations(meta['body']),'delivery-pr-provenance-drift')
    need(contribution['H']==head and contribution['author']==author and identity(contribution['turn_id'])
         and digest(contribution['receipt_sha256'],64),'implementation-receipt-drift')
    need(trailer(repo,head,'Codex')==[author] and trailer(repo,head,'Codex-Turn')==[contribution['turn_id']],
         'implementation-commit-author-drift')
    a=b['acceptance']['A']
    git(repo,'merge-base','--is-ancestor',a,head)
    for p,pin in b['acceptance']['files'].items():
        need(sha(content(repo,head,p)[0])==pin,'frozen-test-drift:'+p)
    need(set(diff(repo,a,head))<=set(b['scope']['write']),'implementation-outside-signed-scope')
    need(type(ci['run']) is int and type(ci['attempt']) is int and min(ci['run'],ci['attempt'])>0 and ci['conclusion']=='success'
         and proof['head_sha']==proof['observed_head']==head and proof['base_sha']==base
         and str(proof['run_id'])==str(ci['run']) and str(proof['run_attempt'])==str(ci['attempt']) and proof['ready'] is True,
         'exact-ci-evidence-drift')
    lanes=proof['lanes']
    need(set(lanes)==LANES and all(x['applicability'] in ('required','not-applicable')
        and (x['proof'],x['outcome'])==(('passed','success') if x['applicability']=='required' else ('not-applicable','skipped'))
        for x in lanes.values()),'ci-lanes-not-green')
    reviews=data['reviews'];need(set(reviews)==set(STAGES),'independent-review-stages-missing')
    observed={}
    for stage,r in reviews.items():
        row=r['github']; session=r['session_id']; role=r['role']; text=r['conclusion']
        who='codex-'+stage+'-'+role
        need(role_ids.get(role)==session and session not in (author,b['acceptance']['author'])
             and identity(r['turn_id']) and digest(r['native_sha256'],64)
             and re.match(r'^\**APPROVE\b',text) and head in text and base in text
             and row['commit_id']==head and row['state'] in ('APPROVED','COMMENTED')
             and type(row['id']) is int and row['id']>0
             and ('复审者：'+who) in row['body'].splitlines()
             and ('commit：'+head) in row['body'].splitlines(),'independent-review-drift:'+stage)
        observed[stage]=(session,who,row['id'])
    need(len({v[0] for v in observed.values()})==len({v[2] for v in observed.values()})==2,'reviews-not-independent')
    subject=dict(number=n,head=head,base=base,target='main')
    role_name=next(k for k,v in role_ids.items() if v==author)
    reconstructed=dict(subject,repo_id=RID,mode='normal',author=role_name,required_reviews=list(STAGES),
                      reviewers={k:v[1] for k,v in observed.items()},review_ids={k:v[2] for k,v in observed.items()},
                      run_id=ci['run'],run_attempt=ci['attempt'])
    decision=data['decision']
    need(decision['approval']==reconstructed,'existing-approval-does-not-bind-exact-evidence')
    if mode=='replay':
        merge=data['completed_merge']
        need(pr['state']=='closed' and pr['merged'] is True and pr['merge_commit_sha']==merge and digest(merge)
             and parents(repo,merge)==[base,head],'completed-replay-subject-drift')
        git(repo,'merge-base','--is-ancestor',merge,observed_base)
        return dict(kind='historical-comparison-only',subject=subject,merge=merge,approval_sha256=sha(wire(reconstructed)),
                    authorization_exported=False)
    need(pr['state']=='open' and pr['draft'] is False and pr['merged'] is False,'live-pr-not-ready')
    grant=decision['authorization']
    raw_auth=decision['authorization_sha256']
    need(digest(raw_auth,64),'missing-independent-authorization-file-pin')
    want=dict(kind='personal-delivery-v1',repo_id=RID,subject=subject,
              expires_at=grant['expires_at'],closure_sha256=DELIVERY,registry_sha256=b['registry_sha256'],
              approval_sha256=sha(wire(reconstructed)),metadata_sha256=sha(wire(metadata(pr))),
              role=dict(name=role_name,session_id=author))
    need(grant==want,'existing-authorization-does-not-bind-exact-evidence')
    expiry=datetime.fromisoformat(grant['expires_at'])
    need(expiry.tzinfo is not None and expiry>datetime.now(timezone.utc),'independent-authorization-expired')
    return dict(kind='inputs-for-separate-operator-check',subject=subject,approval=reconstructed,
                authorization_sha256=raw_auth,authorization_exported=False)


def build(b,repo,mode,observed_base):
    need(mode in ('live','replay') and digest(observed_base),'invalid-base-observation')
    need(type(b['repo_id']) is int and b['repo_id']==RID and b['repository']==REPO,'wrong-lab-repository')
    card,revision=b['card'],b['revision']
    need(type(card) is type(revision) is int and min(card,revision)>0,'invalid-card-or-revision')
    branch=b['branch'];need(type(branch) is str and BRANCH.fullmatch(branch) and '..' not in branch
        and all(x and not x.endswith('.lock') for x in branch.split('/')),'invalid-signed-branch')
    work=b['workdir'];need(type(work) is str and Path(work).is_absolute() and Path(work).resolve(strict=True)==Path(work),'invalid-role-workdir')
    scope=b['scope'];a=b['acceptance'];base=scope['base'];A=a['A']
    need(digest(base) and digest(A) and a['B']==base and (mode=='replay' or base==observed_base),'stale-base')
    need(mode!='replay' or 'delivery' in b,'replay-requires-completed-delivery')
    author, tester=scope['implementation_session'],scope['acceptance_session']
    ids=roles(b['registry'])
    need(identity(author) and identity(tester) and author!=tester and ids.get('one-test')==tester
         and author in ids.values() and a['author']==tester,'author-or-registry-mismatch')
    issue_title=scope['issue_title'];description=scope['description']
    need(type(issue_title) is str and TITLE.fullmatch(issue_title) and issue_title==issue_title.strip()
         and not re.search(r'[（(]#\d+[）)]$',issue_title),'invalid-signed-issue-title')
    need(type(description) is str and description.strip() and '\0' not in description and len(description)<=2048,'missing-or-invalid-scope-description')
    title=f'{issue_title}（#{card}）'
    need(b['metadata'].get('title',title)==title,'proposed-title-mismatch')
    meta=dict(title=title,body=f'Refs #{card}\n\nCodex: {author}\nCodex-Acceptance: {tester}\n')
    read_paths,write_paths=scope['read'],scope['write'];tests=a['files']
    need(type(tests) is dict and tests and all(safe_path(p) and TEST.match(p) and digest(h,64) for p,h in tests.items()),'missing-or-invalid-frozen-tests')
    need(type(read_paths) is type(write_paths) is list and all(safe_path(p) for p in read_paths+write_paths)
         and len(set(read_paths))==len(read_paths) and len(set(write_paths))==len(write_paths)
         and 0<len(read_paths)<=16 and set(write_paths)<=set(read_paths) and set(tests)<=set(read_paths)
         and not set(tests)&set(write_paths),'invalid-read-write-scope')
    flags={bool(GUARDED.match(p)) for p in tests}
    need(len(flags)==1,'mixed-acceptance-role-paths')
    guarded=True in flags
    need(parents(repo,A)==[base] and set(diff(repo,base,A))==set(tests),'tests-only-commit-drift')
    need(trailer(repo,A,'Acceptance-Test-Author')==(['test-engineer'] if guarded else []),'acceptance-role-trailer-mismatch')
    hints=trailer(repo,A,'Codex')+trailer(repo,A,'Codex-Acceptance')
    need(hints==[tester] or (mode=='replay' and not hints and digest(a.get('receipt_sha256'),64)),
         'frozen-test-author-trailer-mismatch')
    files=[]; blobs={}
    for p in sorted(read_paths):
        raw,oid=content(repo,A if p in tests else base,p)
        h=sha(raw)
        if p in tests:need(h==tests[p],'frozen-test-blob-drift:'+p)
        files.append(dict(path=p,sha256=h,text=raw.decode('utf-8'),writable=p in write_paths));blobs[p]=oid
    ident=dict(card=card,revision=revision,session_id=author,B=base)
    capsule=dict(ident,files=files)
    capsule_raw=wire(capsule)+b'\n';need(len(capsule_raw)<=196608,'capsule-byte-limit')
    task=dict(ident,role='one-readonly-lab',physical_cwd=work,task=description,A=A,H=A)
    assignment=dict(task,task='card-capsule:'+sha(capsule_raw))
    result=dict(kind='lab-card-preparation-v1',mode=mode,task=task,capsule=capsule,assignment=assignment,
                capsule_sha256=sha(capsule_raw),metadata=meta,
                patch_preconditions={r['path']:r['sha256'] for r in files if r['writable']},
                test_trailers=(['Acceptance-Test-Author: test-engineer'] if guarded else [])+['Codex: '+tester],
                test_blobs={p:blobs[p] for p in tests})
    if 'delivery' in b:
        source_pins(b)
        result['delivery_inputs']=delivery_inputs(repo,b,ids,base,observed_base,mode,meta)
    return result


def write_once(output,result):
    need(output.parent.resolve(strict=True)==output.parent,'output-parent-not-physical')
    try: output.mkdir(mode=0o700)
    except FileExistsError: pass
    need(output.resolve(strict=True)==output and output.is_dir(),'invalid-output-directory')
    raw=wire(result)+b'\n';target=output/'bundle.json'
    def same():
        st=target.lstat()
        need(stat.S_ISREG(st.st_mode) and st.st_size==len(raw) and target.read_bytes()==raw,'conflicting-preparation-output')
        return 'unchanged'
    if os.path.lexists(target):return same()
    fd,tmp=tempfile.mkstemp(prefix='.bundle-',dir=output)
    try:
        with os.fdopen(fd,'wb') as f:f.write(raw);f.flush();os.fsync(f.fileno())
        try:os.link(tmp,target)
        except FileExistsError:return same()
    finally:os.unlink(tmp)
    return 'prepared'


def prepare(bundle,repo,output,*,mode,observed_base):
    try:
        result=build(bundle,Path(repo),mode,observed_base)
        action=write_once(Path(output),result)
        return dict(action=action,reason='exact-input-checked',bundle_sha256=sha(wire(result)+b'\n'))
    except (ValueError,KeyError,TypeError,OSError,AttributeError,subprocess.SubprocessError) as e:
        return dict(action='hold',reason='missing-field:'+str(e) if isinstance(e,KeyError) else str(e))


def gh(endpoint):
    raw=subprocess.check_output(['/opt/homebrew/bin/gh','api','--method','GET','repos/'+REPO+endpoint],timeout=30)
    return json.loads(raw)


def main():
    p=argparse.ArgumentParser(description=__doc__,allow_abbrev=False)
    p.add_argument('--input',required=True,type=Path)
    p.add_argument('--input-sha256',required=True)
    p.add_argument('--output',required=True,type=Path)
    p.add_argument('--mode',required=True,choices=('live','replay'))
    p.add_argument('--authorization',type=Path,help='existing independently issued file; read only')
    args=p.parse_args()
    try:
        root=Path.cwd()
        need(root.resolve()==root and root.name=='forcitis-one-psa-codex-lab'
             and git(root,'rev-parse','--path-format=absolute','--git-common-dir').decode().strip()==str(root/'.git')
             and git(root,'remote','get-url','--all','origin').decode().strip()=='git@github.com:'+REPO+'.git','not-the-personal-lab')
        output=args.output.absolute()
        allowed=root/'.codex-lab/operations-v1/prepared'
        need(allowed.resolve(strict=True)==allowed and output.parent==allowed,'output-must-be-an-isolated-prepare-directory')
        inp=args.input.absolute()
        need(inp.is_relative_to(root/'.codex-lab') and not inp.is_relative_to(allowed),'input-must-be-pinned-local-evidence')
        raw,b=read(inp)
        need(digest(args.input_sha256,64) and sha(raw)==args.input_sha256,'input-pin-mismatch')
        source_pins(b)
        registry_raw,registry=read(root/'.codex-lab/session-copy/runtime/roles.json')
        need(registry==b['registry'],'registry-drift')
        b['registry_sha256']=sha(registry_raw)
        observed=gh('')
        need((observed['id'],observed['full_name'],observed['default_branch'])==(RID,REPO,'main'),'remote-lab-identity-drift')
        base=gh('/git/ref/heads/main')['object']['sha']
        need(args.mode=='replay' or b['scope']['base']==base,'stale-base')
        issue=gh('/issues/'+str(b['card']))
        need(issue['number']==b['card'] and issue['title']==b['scope']['issue_title']
             and sha(issue['body'].encode())==b['scope']['issue_body_sha256']
             and issue['state']==('closed' if args.mode=='replay' else 'open'),'signed-issue-drift')
        need(b['workdir']==str(root/'.codex-lab/role-runtime/slots/one-readonly-lab/work'),'role-workdir-drift')
        if 'delivery' in b:
            d=b['delivery'];pr=gh('/pulls/'+str(d['pr']['number']))
            for key in ('number','head','base','title','body','labels','state','draft','merged','merge_commit_sha'):
                # GitHub nested repository counters can change without moving an exact ref.
                if key in ('head','base'):
                    select=lambda x:(x['ref'],x['sha'],x['repo']['id'],x['repo']['full_name'])
                    need(select(pr[key])==select(d['pr'][key]),'live-pr-drift:'+key)
                else:need(pr[key]==d['pr'][key],'live-pr-drift:'+key)
            run=gh('/actions/runs/'+str(d['ci']['run']))
            need(run['id']==d['ci']['run'] and run['head_sha']==pr['head']['sha'] and run['run_attempt']==d['ci']['attempt']
                 and run['status']=='completed' and run['conclusion']=='success' and run['event']=='pull_request'
                 and run['path']=='.github/workflows/lab-pr-ci.yml','hosted-ci-live-drift')
            for item in d['reviews'].values():
                r=item['github']; latest=gh('/pulls/'+str(pr['number'])+'/reviews/'+str(r['id']))
                need(all(latest[k]==r[k] for k in ('id','commit_id','body','state')),'published-review-drift')
            if args.mode=='live':
                need(args.authorization is not None,'existing-independent-authorization-required')
                auth=args.authorization.absolute()
                need(auth.is_relative_to(root/'.codex-lab') and not auth.is_relative_to(allowed)
                     and stat.S_IMODE(auth.stat().st_mode)==0o600,'authorization-file-not-private')
                a,grant=read(auth)
                need(d['decision']['authorization_sha256']==sha(a),'independent-authorization-pin-mismatch')
                d['decision']['authorization']=grant
        need(args.authorization is None or (args.mode=='live' and 'delivery' in b),'authorization-not-allowed-for-mode')
        value=build(b,root,args.mode,base)
        need(gh('/git/ref/heads/main')['object']['sha']==base,'main-moved-during-preparation')
        action=write_once(output,value)
        result=dict(action=action,reason='exact-input-checked',bundle_sha256=sha(wire(value)+b'\n'))
    except (ValueError,KeyError,TypeError,OSError,AttributeError,subprocess.SubprocessError) as e:
        result=dict(action='hold',reason='missing-field:'+str(e) if isinstance(e,KeyError) else str(e))
    print(json.dumps(result,ensure_ascii=False))
    return 78 if result['action']=='hold' else 0


if __name__=='__main__':
    raise SystemExit(main())
