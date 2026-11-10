"""Operator-only observational adoption and a separately reserved ref update."""
import json
import os
from pathlib import Path
import re
import sqlite3
import subprocess
import uuid

from pr_body import lines
from public_card import GitHubPort, REPO, RID, ORIGIN, run
from owned_runtime import private,sha
from runtime_broker import physical,private_file
from runtime_store import need,wire
from successor_card import native, validate_preserved_update
import stat

FIELDS=("repo_id","repository","card","number","head_ref","base_ref","old_head","old_base",
        "new_base","scope_sha256","author_session","acceptance_session","acceptance_sha256")
RECORD_KIND="observe-and-adopt-existing-pr"
UPDATE_KIND="adopted-pr-ref-update"


EXACT_F1_UPDATE_PIN = '06c95ac797ad6349fa38a8a7f4a759d8e085e340cdf00ec1228ea6d8c740a40c'


def f1_update_input(cfg, sc):
    approval = cfg.get('approved_update')
    need(type(approval) is dict and sha(wire(approval).encode()) == EXACT_F1_UPDATE_PIN
         and approval['head'] == cfg['candidate'] and approval['base'] == sc['new_base']
         and approval['scope_sha256'] == sc['scope_sha256']
         and approval['original_diff_sha256'] == sc['approval']['contribution_sha256'],
         'exact-f1-update-approval-required')
    for field in ('normalized_contribution', 'normalized_commit_evidence'):
        need(type(cfg.get(field)) is dict
             and sha(wire(cfg[field]).encode()) == approval[field + '_sha256'],
             'exact-f1-contribution-evidence-drift')
    return approval


def verify_f1_update(sc, cfg, port, original, candidate, approval):
    head, base = approval['head'], approval['base']
    need(sha(wire(original).encode()) == approval['original_diff_sha256']
         and sha(wire(candidate).encode()) == approval['candidate_diff_sha256']
         and cfg['files'] == [x['path'] for x in original['files']]
         and cfg['test_path'] == 'backend/src/test/java/com/forcitis/one/acceptance/operations/facts/PostingFactsDmlRoleIT.java'
         and port.git('rev-parse', head + '^{tree}') == approval['tree'],
         'exact-f1-file-inventory-or-tree-drift')
    port.git('merge-base', '--is-ancestor', base, head)
    mapping, evidence = cfg['normalized_contribution'], cfg['normalized_commit_evidence']
    need((mapping['B'],mapping['H'],mapping['original_head']) == (base,head,sc['old_head'])
         and (evidence['base'],evidence['head'],evidence['tree']) == (base,head,approval['tree'])
         and len(mapping['history']) == len(evidence['history']) == 12,
         'exact-f1-history-scope-drift')
    for entry, observed in zip(mapping['history'], evidence['history']):
        need((entry['original'],entry['replayed']) == (observed['original'],observed['replayed']),
             'exact-f1-history-mapping-drift')
        for side in ('original','replayed'):
            record=observed[side + '_metadata']
            actual=port.git('show','-s','--format=%an%x00%ae%x00%aI%x00%B',observed[side]).split(chr(0),3)
            expected=[record[k] for k in ('author','email','author_date','message')]
            need(len(actual)==4 and actual[:3]==expected[:3] and actual[3].rstrip()==expected[3].rstrip(),
                 'exact-f1-history-metadata-drift')
    for test in approval['tests']:
        raw=run(['/usr/bin/git','--no-replace-objects','show',head+':'+test['path']],
                port.cwd,environment=port.git_env)
        need(sha(raw)==test['sha256'],'exact-f1-frozen-test-drift')


EXACT_F1_FOLLOWUP_PIN = '940c0e472e9162a90b18d9ea4e1da640ff1c4a4ad6265e2a7e43084d998f4c3d'
EXACT_F1_FOLLOWUP_EVIDENCE = '2e2b1f0a0f5b986ee69be9c718cefbb1266595c80399ffa0eef8e5c1832295d1'


def f1_followup_input(cfg, sc):
    approval = cfg.get('approved_followup')
    need(exact_f1(sc) and type(approval) is dict and type(cfg.get('followup_evidence')) is dict,
         'exact-f1-followup-scope-required')
    fixed = {k:v for k,v in approval.items() if k not in ('revision','previous_target_sha256')}
    need(sha(wire(fixed).encode()) == EXACT_F1_FOLLOWUP_PIN
         and sha(wire(cfg['followup_evidence']).encode()) == EXACT_F1_FOLLOWUP_EVIDENCE
         and sha(wire(sc).encode()) == approval['original_subject_sha256']
         and approval['head'] == cfg['candidate'] and approval['base'] == sc['new_base']
         and type(approval.get('revision')) is int and approval['revision'] == cfg['revision']
         and hexed(approval.get('previous_target_sha256'),64),
         'exact-f1-followup-approval-drift')
    return approval


def verify_f1_followup(cfg, port, approval):
    head, base, previous = (approval[k] for k in ('head','base','previous_head'))
    evidence = cfg['followup_evidence']
    need(port.git('rev-parse', head + '^{tree}') == approval['tree']
         and sha(wire(port.diff(previous,head)).encode()) == approval['patch_diff_sha256']
         and sha(wire(port.diff(base,head)).encode()) == approval['candidate_diff_sha256']
         and sha(wire(evidence['commits']).encode()) == approval['commits_sha256'],
         'exact-f1-followup-inventory-drift')
    for ancestor in (previous,base):
        port.git('merge-base','--is-ancestor',ancestor,head)
    for record in evidence['commits']:
        actual=port.git('show','-s','--format=%an%x00%ae%x00%aI%x00%B',record['commit']).split(chr(0),3)
        expected=[record[k] for k in ('author','email','author_date','message')]
        need(len(actual)==4 and actual[:3]==expected[:3] and actual[3].rstrip()==expected[3].rstrip(),
             'exact-f1-followup-author-drift')
    for test in approval['tests']+[approval['g44_test']]:
        raw=run(['/usr/bin/git','--no-replace-objects','show',head+':'+test['path']],
                port.cwd,environment=port.git_env)
        need(sha(raw)==test['sha256'],'exact-f1-followup-test-drift')


def f1_observed_predecessor(db,sc,approval,generation):
    row=db.execute("select * from effects where operation_key=?",
                   (approval['previous_effect_key'],)).fetchone()
    need(row is not None and row['kind']==UPDATE_KIND and row['phase']=='observed'
         and row['issue_count']==1 and row['generation']==generation
         and row['revision']+1==approval['revision']
         and sha(row['target'].encode())==approval['previous_target_sha256']
         and sha(row['observation'].encode())==approval['previous_observation_sha256'],
         'exact-f1-predecessor-not-observed')
    need(json.loads(row['target'])==dict(scope=sc,head=approval['previous_head'],revision=row['revision'])
         and json.loads(row['observation'])==dict(action='updated',number=sc['number'],
             head=approval['previous_head'],base=sc['new_base']),
         'exact-f1-predecessor-readback-drift')


def hexed(v,n):
    return type(v) is str and re.fullmatch(f"[0-9a-f]{{{n}}}",v) is not None


# The approved legacy ref is a full contribution binding, not a ref namespace.
EXACT_F1_SUBJECT = {'acceptance_session': '01a1b614-934c-7250-a715-5cf09e9c02c2', 'acceptance_sha256': '19c19d234ec0d0fdadba6598a7884c362965c6f09fe9e21a8dcee93857f68705', 'author_session': '01a1b612-91bc-77c0-a824-fe48ce5cb5e0', 'base_ref': 'main', 'card': 30, 'head_ref': 'lab/30-facts-f1', 'new_base': '0c491846d24d194090bda99a1d16a100331cbd2c', 'number': 32, 'old_base': '15511fad03b8d2ae38e3a337f24556bd9d9071c6', 'old_head': '5af1d35ad76e95007a714704b97f3da07b0e0d35', 'repo_id': 1449415666, 'repository': 'YeliangDu/forcitis-one-psa-codex-lab', 'scope_sha256': '46360a7fc10bf867b99477ca13811d01fc9f7ca73f9e99f337b5d48cbc5d2f26'}
EXACT_F1_TEST_MANIFEST = 'ecf963f4f9eac7991b5c21951766f18a98b81168e1a3fba97268d32586ff8d41'


def exact_f1(scope):
    if type(scope) is not dict or 'approval' not in scope:
        return False
    subject = {k: v for k, v in scope.items() if k != 'approval'}
    approval = scope['approval']
    return (type(approval) is dict
            and set(approval) == {'kind', 'subject', 'contribution_sha256', 'acceptance_manifest_sha256'}
            and approval['kind'] == 'exact-existing-pr-adoption-v1'
            and subject == EXACT_F1_SUBJECT
            and type(approval['subject']) is dict and approval['subject'] == subject
            and all(type(subject[k]) is type(v) and type(approval['subject'][k]) is type(v)
                    for k, v in EXACT_F1_SUBJECT.items())
            and approval['contribution_sha256'] == EXACT_F1_SUBJECT['scope_sha256']
            and approval['acceptance_manifest_sha256'] == EXACT_F1_TEST_MANIFEST)


def validate(scope):
    if exact_f1(scope):
        return
    need(type(scope) is dict and set(scope)==set(FIELDS)
         and type(scope["repo_id"]) is int and (scope["repo_id"],scope["repository"])==(RID,REPO)
         and all(type(scope[k]) is int and scope[k]>0 for k in ("card","number"))
         and type(scope["head_ref"]) is str and re.fullmatch(r"codex/[A-Za-z0-9][A-Za-z0-9/_-]*",scope["head_ref"])
         and scope["base_ref"]=="main"
         and all(hexed(scope[k],40) for k in ("old_head","old_base","new_base"))
         and all(hexed(scope[k],64) for k in ("scope_sha256","acceptance_sha256"))
         and native(scope["author_session"]) and native(scope["acceptance_session"])
         and scope["author_session"]!=scope["acceptance_session"],"invalid-existing-pr-scope")


def observe(db,scope,remote):
    validate(scope)
    need(type(remote) is dict and all(k in remote and type(remote[k]) is type(scope[k]) and remote[k]==scope[k] for k in FIELDS)
         and remote.get("state")=="open" and remote.get("draft") is False,
         "existing-pr-observation-mismatch")
    need(db.execute("select 1 from effects where phase != 'observed' limit 1").fetchone() is None,
         "existing-pr-pending-effect-hold")
    db.execute("""create table if not exists adopted_pr(
       repo_id integer not null, card integer not null, number integer not null,
       head_ref text not null, kind text not null, subject text not null,
       primary key(repo_id,card), unique(repo_id,number),unique(repo_id,head_ref))""")
    subject=wire(scope)
    same=db.execute("""select kind,subject from adopted_pr where repo_id=?
      and (card=? or number=? or head_ref=?)""",
        (scope["repo_id"],scope["card"],scope["number"],scope["head_ref"])).fetchall()
    need(not same or [tuple(x) for x in same]==[(RECORD_KIND,subject)],"existing-pr-conflicting-binding")
    if not same:
        try:db.execute("insert into adopted_pr values(?,?,?,?,?,?)",
          (scope["repo_id"],scope["card"],scope["number"],scope["head_ref"],RECORD_KIND,subject))
        except sqlite3.IntegrityError as e:raise ValueError("existing-pr-concurrent-binding") from e
    return dict(action="adopted",number=scope["number"])


class AdoptionPort(GitHubPort):
    def __init__(self,config):
        sc=config["scope"]
        f = f1_followup_input(config,sc) if 'approved_followup' in config else None
        self.s=dict(branch=sc["head_ref"],base=sc["new_base"],
                    expected=f['previous_head'] if f else sc["old_head"],head=config["candidate"])
        self.cwd=physical(config["worktree"]);common=physical(config["common"])
        self.git_env={k:v for k,v in os.environ.items() if not k.startswith("GIT_")}
        self.git_env.update(GIT_CONFIG_NOSYSTEM="1",GIT_CONFIG_GLOBAL="/dev/null",
                            GIT_TERMINAL_PROMPT="0",GIT_NO_REPLACE_OBJECTS="1")
        need(self.git("rev-parse","--show-toplevel")==str(self.cwd)
             and self.git("rev-parse","--path-format=absolute","--git-common-dir")==str(common)
             and common.name==".git" and common.parent.name=="forcitis-one-psa-codex-lab"
             and self.git("remote","get-url","--all","origin")==ORIGIN
             and self.git("remote","get-url","--push","--all","origin")==ORIGIN
             and self.git("rev-parse","HEAD")==config["candidate"]
             and not self.git("status","--porcelain","--untracked-files=all"),"existing-pr-worktree-drift")
        need(sha(run(["/usr/bin/git","config","--local","--null","--list"],self.cwd,environment=self.git_env))
             ==config["git_config_sha256"],"existing-pr-git-config-drift")

    def diff(self,base,head):
        text=run(["/usr/bin/git","--no-replace-objects","diff","--raw","--no-renames","--no-abbrev","-z",base,head],
                 self.cwd,environment=self.git_env).decode().split("\0")
        need(text[-1]=="" and len(text)%2==1,"existing-pr-raw-diff")
        rows=[]
        for item,path in zip(text[:-1:2],text[1:-1:2]):
            a,b,c,d,status=item.removeprefix(":").split()
            need(item.startswith(":") and status in ("M","A","D"),"existing-pr-diff-kind")
            rows.append(dict(path=path,before_blob=None if c=="0"*40 else c,
              after_blob=None if d=="0"*40 else d,before_mode=None if a=="000000" else a,
              after_mode=None if b=="000000" else b))
        return dict(base=base,head=head,files=rows)

    def observe_remote(self,sc,config):
        self.repo() # actual fixed repo, default branch and live main
        issue=self.api("/issues/"+str(sc["card"]))
        need(issue["number"]==sc["card"] and issue["state"]=="open" and "pull_request" not in issue
             and sha(issue["body"].encode())==config["issue_body_sha256"],"existing-pr-card-scope-drift")
        row=self.pull(sc["number"])
        ref=self.ref(sc["head_ref"])
        need(row["number"]==sc["number"] and row["head"]==dict(ref=sc["head_ref"],sha=ref)
             and row["base"]["ref"]==sc["base_ref"] and row["state"]=="open" and row["draft"] is False
             and sha(row["body"].encode())==config["pr_body_sha256"],"existing-pr-live-pr-drift")
        body=row["body"]
        need(next((x.strip() for x in body.splitlines() if x.strip()),"")=="Refs #"+str(sc["card"]) and
             lines(body,"Codex")==[sc["author_session"]] and
             lines(body,"Codex-Acceptance")==[sc["acceptance_session"]],"existing-pr-original-provenance-unbound")
        return row

    def create_pr(self,*a):raise ValueError("adoption-cannot-create-pr")
    def create_ref(self,*a):raise ValueError("adoption-cannot-create-ref")
    def ready_pr(self,*a):raise ValueError("adoption-cannot-edit-pr")


def run_owned(installation,installation_pin,request,role_installation,role_pin,scope_path,scope_pin):
    from single_role import RoleInstallation,TaskStore
    from durable_role import exclusive
    i=RoleInstallation(installation,installation_pin,role_installation,role_pin)
    f=physical(scope_path);rp=physical(request)
    need(i.is_native and f.parent==i.root/"owner" and rp.parent==i.root,"owned-adoption-path")
    raw=private_file(str(f));need(sha(raw)==scope_pin,"owned-adoption-pin")
    config=json.loads(raw); req=json.loads(private_file(str(rp)))
    need(set(req)=={"command","auth","arguments"} and req["arguments"]=={}
         and req["command"] in ("adoption.observe","adoption.update"),"owned-adoption-request")
    sc=config["scope"];validate(sc)
    need(native(config["successor_session"]) and config["successor_session"] not in (sc["author_session"],sc["acceptance_session"])
         and type(config["revision"]) is int and config["revision"]>0 and hexed(config["candidate"],40)
         and all(hexed(config[k],64) for k in ("pr_body_sha256","issue_body_sha256","git_config_sha256")),"owned-adoption-selection")
    with exclusive(i.root):
        need(not os.path.lexists(i.sock),"owned-adoption-broker-must-be-stopped")
        store=TaskStore(i)
        try:out=advance(store,req["auth"],req["command"],sc,config,AdoptionPort(config))
        finally:store.db.close()
    print(wire(out))
    return 0 if out.get("action") in ("adopted","updated") else 78


def advance(store,auth,cmd,sc,cfg,port):
    validate(sc)
    follow = f1_followup_input(cfg,sc) if 'approved_followup' in cfg else None
    need(follow is None or cmd=='adoption.update','exact-f1-followup-update-only')
    f1 = f1_update_input(cfg, sc) if cmd=='adoption.update' and exact_f1(sc) and not follow else None
    previous = follow['previous_head'] if follow else sc['old_head']
    previous_base = sc['new_base'] if follow else sc['old_base']
    subject=wire(sc)
    key=f'existing-pr-update:{sc["repo_id"]}:{sc["number"]}:{previous}'
    target=wire(dict(scope=sc,head=cfg["candidate"],revision=cfg["revision"]))
    sid=cfg["successor_session"]; new=cfg["candidate"]
    def owner(db):
        o=store.owner(db,auth)
        role=db.execute("select session_id,phase,generation from role_registration where id=1").fetchone()
        need(o["paused"]==1 and role and tuple(role)==(sid,"stable",o["generation"]),"existing-pr-stable-paused-owner")
        return o
    def binding(db):
        o=owner(db)
        adopted=db.execute("select kind,subject from adopted_pr where repo_id=? and card=?",
                          (sc["repo_id"],sc["card"])).fetchone()
        need(adopted is not None and tuple(adopted)==(RECORD_KIND,subject),"existing-pr-adoption-required")
        if follow:f1_observed_predecessor(db,sc,follow,o["generation"])
        task=store.current(db)
        body=json.loads(task["body"])
        need(task["phase"]=="active" and task["revision"]==cfg["revision"]
             and (body["card"],body["B"],body["H"],body["session_id"])==(sc["card"],sc["new_base"],new,sid),
             "existing-pr-current-dispatch-assignment-mismatch")
        row=db.execute("""select t.state,n.phase,e.phase,e.issue_count from native_dispatch n
          join turns t on t.turn_id=n.turn_id join effects e on e.effect_id=n.effect_id
          where n.session_id=? and n.revision=?""",(sid,cfg["revision"])).fetchall()
        need([tuple(x) for x in row]==[("terminal","observed","observed",1)],"existing-pr-native-readback-required")
        effect=db.execute("select * from effects where operation_key=?",(key,)).fetchone()
        need(effect is None or (effect["target"]==target and effect["kind"]==UPDATE_KIND
             and effect["generation"]==o["generation"] and effect["revision"]==cfg["revision"]
             and effect["issue_count"]==1 and effect["phase"] in ("unknown","observed")),"existing-pr-update-effect-drift")
        pending=db.execute("select operation_key from effects where phase!='observed'").fetchall()
        need(not pending or (effect and [tuple(x) for x in pending]==[(key,)]),"existing-pr-unrelated-effect-unknown")
        return o,effect
    if cmd=="adoption.observe":
        row=port.observe_remote(sc,cfg)
        remote=dict(sc,old_head=row["head"]["sha"],old_base=row["base"]["sha"],state=row["state"],draft=row["draft"])
        with store.tx() as db:
            owner(db)
            return observe(db,sc,remote)

    with store.tx() as db: _,effect=binding(db)
    row=port.observe_remote(sc,cfg)
    base=row["base"]["sha"]
    head=row["head"]["sha"]
    need(head in (previous,new),"existing-pr-unexpected-remote-head")
    if head==previous:
        need(base==previous_base,"existing-pr-before-update-base-drift")
        if effect is not None:
            return dict(action="hold",reason="existing-pr-update-readback-only",effect_id=effect["effect_id"])
        if follow:
            verify_f1_followup(cfg,port,follow)
        else:
            core=dict(repository=sc["repository"],number=sc["number"],ref="refs/heads/"+sc["head_ref"],
              author_session=sc["author_session"],successor_session=sid,acceptance_sha256=sc["acceptance_sha256"],
              old_B=sc["old_base"],old_H=sc["old_head"],new_B=sc["new_base"])
            original=port.diff(sc["old_base"],sc["old_head"])
            candidate=port.diff(sc["new_base"],new)
            if f1 is not None:
                verify_f1_update(sc,cfg,port,original,candidate,f1)
            else:
                paths=[x["path"] for x in original["files"]]
                need(paths==cfg["files"] and cfg["test_path"] in paths,"existing-pr-signed-file-scope-drift")
                test=run(["/usr/bin/git","--no-replace-objects","show",new+":"+cfg["test_path"]],port.cwd,environment=port.git_env)
                need(sha(test)==sc["acceptance_sha256"],"existing-pr-frozen-test-drift")
                validate_preserved_update(core,dict(core,candidate=dict(head=new,parents=port.git("show","-s","--format=%P",new).split()),
                     original_diff=original,candidate_diff=candidate,pending_effects=[],
                     role_continuity=dict(phase="stable",previous_session_id=sid,current_session_id=sid,observed=True)))
        with store.tx() as db:
            o,again=binding(db);need(again is None,"existing-pr-update-already-reserved")
            eid=uuid.uuid4().hex
            db.execute("insert into effects(effect_id,revision,generation,operation_key,kind,target,phase,issue_count) values(?,?,?,?,?,?,?,1)",
                       (eid,cfg["revision"],o["generation"],key,UPDATE_KIND,target,"unknown"))
            store.event(db,"existing-pr-update-consumed-before-send",dict(effect_id=eid,head=new))
        try:port.update_ref(sc["head_ref"],previous,new)
        except (OSError,ValueError,subprocess.SubprocessError):
            return dict(action="hold",reason="existing-pr-update-send-unknown",effect_id=eid)
        row=port.observe_remote(sc,cfg)
        head,base=row["head"]["sha"],row["base"]["sha"]
    else:
        need(effect is not None,"existing-pr-ref-moved-without-reservation")
    if (head,base)!=(new,sc["new_base"]):
        return dict(action="hold",reason="existing-pr-update-awaiting-exact-readback")
    result=dict(action="updated",number=sc["number"],head=head,base=base)
    with store.tx() as db:
        _,effect=binding(db)
        need(effect is not None,"existing-pr-update-reservation-missing")
        if effect["phase"]=="unknown":
            db.execute("update effects set phase='observed',observation=? where effect_id=?",(wire(result),effect["effect_id"]))
            store.event(db,"existing-pr-update-exact-readback",result)
        else:need(effect["observation"]==wire(result),"existing-pr-update-observation-drift")
    return result
