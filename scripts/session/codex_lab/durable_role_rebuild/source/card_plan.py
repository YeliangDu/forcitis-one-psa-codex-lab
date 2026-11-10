"""Exact read-only plan bound to the owned role and independently observed reviews."""
import copy,fcntl,os,re,stat,subprocess
from pathlib import Path,PurePosixPath
from native_role import canonical_uuid
from owned_runtime import decode,fields,private,sha
from runtime_broker import physical,private_file
from runtime_store import need,wire
from readonly_plan_consumer import consume

RID=1449415666

def authorize(assignment,registration,registry,approval):
    need(all(type(x) is dict for x in (assignment,registration,registry,approval)),'plan-input-shape')
    sid=assignment.get('session_id');role=assignment.get('role');items=registry.get('roles')
    need(registration.get('session_id')==sid and registration.get('phase')=='stable'
         and type(items) is list and len(items)>0,'plan-stable-identity-required')
    ids={}
    for item in items:
        need(type(item) is dict and type(item.get('role')) is str and item['role'] not in ids
             and re.fullmatch(r'one-[a-z0-9-]+',item['role']),'plan-registry-shape')
        ids[item['role']]=canonical_uuid(item.get('id'))
    need(len(set(ids.values()))==len(ids) and role in ids and ids[role]==sid and approval.get('author')==role,'plan-author-identity-drift')
    n=assignment.get('pr_number',assignment.get('card'))
    need(type(n) is int and n>0 and type(approval.get('number')) is int and approval['number']==n
         and approval.get('repo_id')==RID and approval.get('target')=='main'
         and all(type(assignment.get(k)) is str and re.fullmatch('[0-9a-f]{40}',assignment[k])
                 and approval.get(field)==assignment[k] for field,k in (('head','H'),('base','B'))),'plan-exact-repo-and-head-required')
    rr=approval.get('required_reviews');r=approval.get('reviewers');ni=approval.get('review_ids')
    need(type(rr) is list and len(rr)==2 and all(type(k) is str for k in rr) and len(set(rr))==2
         and type(r) is dict and type(ni) is dict and set(rr)==set(r)==set(ni)
         and all(type(v) is int and v>0 for v in ni.values()) and len(set(ni.values()))==2,
         'plan-two-distinct-review-records-required')
    reviewers=[]
    for v in r.values():
        need(type(v) is str and len(v.split('-',2))==3,'plan-reviewer-shape')
        resident=v.split('-',2)[2]
        need(resident in ids and resident!=role,'plan-independent-reviewer-unbound')
        reviewers.append(resident)
    need(len(set(reviewers))==2,'plan-two-independent-residents-required')
    return dict(number=n,head=assignment['H'],base=assignment['B'],target='main')

def admission_assignment(assignment,registration,registry):
    need(all(type(x) is dict for x in (assignment,registration,registry)),'plan-admission-input-shape')
    role=assignment.get('role');sid=canonical_uuid(assignment.get('session_id'))
    need(registration.get('phase')=='stable' and registration.get('session_id')==sid,'plan-admission-role-not-stable')
    items=registry.get('roles');need(type(items) is list and items,'plan-admission-registry-required')
    roles={};seen=set()
    for row in items:
        need(type(row) is dict and type(row.get('role')) is str and row['role'] not in roles,'plan-admission-duplicate-role')
        tid=canonical_uuid(row.get('id'));need(tid not in seen,'plan-admission-duplicate-id')
        roles[row['role']]=tid;seen.add(tid)
    need(type(role) is str and (role=='one-readonly-lab'
         or re.fullmatch(r'one-(?:lead|design|platform|test|dev-[a-z][a-z0-9-]*)',role)),
         'plan-admission-unsupported-runtime-role')
    alias='one-dev-readonly-lab' if role=='one-readonly-lab' else role
    need(roles.get(alias)==sid,'plan-admission-alias-unbound')
    result=copy.deepcopy(assignment);result['role']=alias;return result


def plan_owned(installation,installation_pin,request,role_installation,role_pin,scope_path,scope_pin):
    from single_role import RoleInstallation,TaskStore
    i=RoleInstallation(installation,installation_pin,role_installation,role_pin)
    need(i.is_native,'plan-owned-native-required')
    f=physical(scope_path);need(f.parent==i.root/'owner','plan-private-selection-required')
    raw=private_file(str(f));need(sha(raw)==scope_pin,'plan-selection-pin')
    p=decode(raw);fields(p,('root','controller','approval_sha256','registry_sha256','review_evidence_sha256','protected_queue','source_files_sha256'))
    root=physical(p['root']);protect=physical(p['protected_queue']);common=physical(str(root/'.git'))
    need(root.name=='forcitis-one-psa-codex-lab' and common.is_dir() and root.is_relative_to('/private/tmp')
         and not root.is_relative_to(i.root) and not protect.is_relative_to(root)
         and not (root/'.git/merge-queue').is_relative_to(protect),'plan-isolated-controller-required')
    req_path=physical(request);need(req_path.parent==i.root,'plan-private-request-location')
    req=decode(private_file(str(req_path)));fields(req,('command','auth','arguments'))
    need(req['command']=='card.plan','plan-readonly-command-only');fields(req['arguments'],('revision',))
    from durable_role import exclusive
    with exclusive(i.root):
        need(not i.sock.exists() and not i.sock.is_symlink(),'plan-active-owned-channel')
        store=TaskStore(i)
        try:
            with store.tx() as db:
                owner,task=store.binding(db,req['auth'],req['arguments']['revision'],paused_ok=True)
                reg=dict(db.execute('select session_id,phase,generation from role_registration').fetchone())
                body=decode(task['body'])
                need(owner['paused']==1 and reg['generation']==owner['generation'],'plan-paused-owner-required')
                seen=[]
                for row in db.execute("select revision,observation,target from effects where kind='followup-pr' and phase='observed' and issue_count=1"):
                    subject=decode(row['target']);o=decode(row['observation'])
                    if row['revision']==task['revision'] and subject['card']==body['card'] and (o['head'],o['base'])==(body['H'],body['B']):seen.append(o)
                need(len(seen)==1,'plan-same-card-pr-effect-required')
                n=seen[0]['number']
            registry_path=root/'.codex-lab/session-copy/runtime/roles.json'
            approval_path=common/'merge-queue/approvals'/f'{n}.json'
            reviews_path=common/'merge-queue/approvals'/f'{n}.native.json'
            rr,ar,nr=(private_file(str(x),131072) for x in (registry_path,approval_path,reviews_path))
            need((sha(rr),sha(ar),sha(nr))==(p['registry_sha256'],p['approval_sha256'],p['review_evidence_sha256']),
                 'plan-approval-or-provenance-pin-drift')
            registry,approval,evidence=map(decode,(rr,ar,nr))
            intent=authorize(admission_assignment(dict(body,pr_number=n),reg,registry),reg,registry,approval)
            need(set(approval['required_reviews'])=={'首轮','终审'} and evidence.get('head')==intent['head']
                 and evidence.get('base')==intent['base'] and evidence.get('number')==n,'plan-native-review-subject-drift')
            mapped={x['role']:x['id'] for x in registry['roles']}
            actual=evidence.get('reviews')
            need(type(actual) is dict and set(actual)==set(approval['required_reviews']),'plan-native-review-receipts-required')
            turn_ids=set()
            for k,x in actual.items():
                review_role=approval['reviewers'][k].split('-',2)[2]
                need(x.get('review_id')==approval['review_ids'][k] and x.get('thread_id')==mapped[review_role]
                     and type(x.get('native_receipt_sha256')) is str and re.fullmatch('[0-9a-f]{64}',x['native_receipt_sha256'])
                     and canonical_uuid(x['turn_id']) not in turn_ids,'plan-native-review-provenance-mismatch')
                turn_ids.add(x['turn_id'])
            pins=p['source_files_sha256']
            need(type(pins) is dict and {'scripts/merge/mq.py','scripts/merge/mq_lab_operator.py','scripts/merge/mq_lab_proof.py','policy.json'}<=set(pins)
                 and len(pins)<=256 and p['controller']==body['B'],'plan-controller-closure-required')
            for path,digest in pins.items():
                rel=PurePosixPath(path)
                need(not rel.is_absolute() and str(rel)==path and '..' not in rel.parts,'plan-controller-path')
                source=physical(str(root/path))
                old=subprocess.check_output(['/usr/bin/git','--no-replace-objects','show',p['controller']+':'+path],cwd=root,timeout=10)
                need(source.read_bytes()==old and sha(old)==digest,'plan-controller-file-drift')
            result=consume(n,intent['head'],intent['base'],p['controller'],p['approval_sha256'],str(root),[protect,i.root])
        finally:store.db.close()
        print(wire(result));return 0 if (result.get('result') or {}).get('action')=='eligible' else 78
