"""Seed one approved permanent slot; device login runs in the user's terminal."""
import hashlib
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import sys
import tempfile

SRC=Path(__file__).resolve().parent
sys.path.insert(0,str(SRC))
from durable_role import validate_root, exclusive, require
from role_native import (BASE_SLOT, POLICY_SHA, CATALOG_SHA, BIN_SHA, APPLE_CA_SHA,
                         expected_config, policy_additions, environment)
from portable_broker import verify_source
from single_role import RoleInstallation

D=SRC.parent
LAB=D.parents[2]
SLOT=LAB/'.codex-lab/role-runtime/slots/one-readonly-lab'
REF_ROOT=D.parent


def sha(b):return hashlib.sha256(b).hexdigest()
def read(path):return json.loads(path.read_bytes())
def file_sha(path):return sha(path.read_bytes())
def write(path,data):
    with path.open('xb') as f:
        f.write(data); f.flush(); os.fsync(f.fileno())
def put(path,x):write(path,(json.dumps(x,indent=2)+'\n').encode())


def approved():
    g=read(D/'green-readback.json')
    r=read(D/'review/installation-approval.json')
    require(sys.platform=='darwin' and os.geteuid()!=0 and g['driver_exit']==0
            and g['verdict']['classification']=='durable-role-passed'
            and g['H']==r['H'] and g['source_sha256']==r['closure'] and r['approved'] is True,
            'reviewed-durable-host-required')
    verify_source(str(SRC/'SOURCE-CLOSURE.json'),g['source_sha256'])
    old=read(D/'old-role-retirement.json')
    require(old['role']=='one-readonly-lab' and old['new_native_id'] is None
            and old['previous_status']=='runtime-state-lost-unrecoverable'
            and old['never_replay_old_tasks'] is True,'old-runtime-retirement-required')
    return old


def prepare():
    origin=subprocess.check_output(['/usr/bin/git','remote','get-url','origin'],cwd=LAB,timeout=10,text=True).strip()
    require(LAB.name=='forcitis-one-psa-codex-lab' and origin=='git@github.com:YeliangDu/forcitis-one-psa-codex-lab.git',
            'wrong-durable-lab')
    ignore=subprocess.run(['/usr/bin/git','check-ignore','-q',str(SLOT)],cwd=LAB,timeout=10)
    require(ignore.returncode==0,'persistent-state-must-be-git-ignored')
    files=REF_ROOT/'runtime-entry-decision'
    binary=Path(read(files/'abort-diagnosis/original/provenance.json')['binary_path'])
    policy_raw=(files/'config-official-preferences-witness/candidate.sb').read_bytes()
    catalog=(REF_ROOT/'single-role-readonly-task/direct-tool-repair/model-catalog.direct.json').read_bytes()
    ca=Path('/private/etc/ssl/cert.pem').read_bytes()
    require(binary.resolve(strict=True)==binary and file_sha(binary)==BIN_SHA and sha(policy_raw)==POLICY_SHA
            and policy_raw.count(BASE_SLOT)==1 and sha(catalog)==CATALOG_SHA and sha(ca)==APPLE_CA_SHA
            and b'"' not in os.fsencode(SLOT) and b'\\' not in os.fsencode(SLOT),
            'native-material-pin-drift')
    for p in (Path('/etc/codex'),Path('/private/etc/codex')):
        require(not p.exists() and not p.is_symlink(),'unexpected-system-config')
    SLOT.parent.mkdir(mode=0o700,parents=True,exist_ok=True)
    SLOT.mkdir(mode=0o700)
    validate_root(LAB,SLOT)
    with exclusive(SLOT):
        for n in ('home','home/.codex','tmp','work','runtime','owner','trace','credentials'):
            (SLOT/n).mkdir(mode=0o700)
        policy=SLOT/'runtime.sb';write(policy,policy_raw.replace(BASE_SLOT,os.fsencode(SLOT))+policy_additions(SLOT))
        model=SLOT/'model-catalog.json';write(model,catalog)
        c=expected_config(SLOT)
        lines=[k+' = '+json.dumps(v) for k,v in c.items() if type(v) is not dict]
        lines+=['','[features]']+[k+' = false' for k in c['features']]
        lines+=['','[agents]','enabled = false','','[tools.update_plan]','enabled = false',
                '','[tools.experimental_request_user_input]','enabled = false','','[analytics]','enabled = false','']
        cfg=SLOT/'home/.codex/config.toml';write(cfg,'\n'.join(lines).encode())
        write(SLOT/'tmp/native-witness-apple-ca.pem',ca)
        db=SLOT/'owner/runtime.sqlite3';pin=sha(os.urandom(32));token=os.urandom(32).hex()
        conn=sqlite3.connect(db)
        try:
            with conn:
                conn.executescript((SRC/'durable_seed_schema.sql').read_text())
                conn.execute('insert into installation values(1,?)',(pin,))
                conn.execute('insert into owner values(1,1,?,?,1)',('durable-role-controller',sha(token.encode())))
        finally:conn.close()
        put(SLOT/'credentials/owner.json',dict(client='durable-role-controller',generation=1,token=token))
        owned=SLOT/'owned-installation.json'
        argv=['/usr/bin/sandbox-exec','-f',str(policy),str(binary)]
        put(owned,dict(schema='lab-owned-runtime-installation/1',kind='official-single-role-stdio-only',
             runtime_directory=str(SLOT/'runtime'),owner_database=str(db),owner_installation_sha256=pin,
             physical_cwd=str(SLOT/'work'),control_socket=str(SLOT/'runtime/owned.sock'),
             child=dict(argv=argv+['app-server','--listen','stdio://','--strict-config','-c','cli_auth_credentials_store="file"'],
                        files_sha256={str(x):file_sha(x) for x in (binary,policy,cfg,model)},environment=environment(SLOT)),
             timeouts=dict(startup_ms=9000,request_ms=9000,stop_ms=9000)))
        role=SLOT/'single-role-installation.json'
        put(role,dict(schema='lab-single-role-readonly-installation/1',kind='official-single-role-readonly',
                      owned_installation_sha256=file_sha(owned),role='one-readonly-lab',
                      assignment_file=str(SLOT/'work/assignment.json'),max_assignment_bytes=4096,
                      dynamic_tool=dict(name='lab_read_assignment',arguments={})))
        RoleInstallation(str(owned),file_sha(owned),str(role),file_sha(role))


def login():
    require(all(s.isatty() for s in (sys.stdin,sys.stdout,sys.stderr)),'interactive-terminal-required')
    with exclusive(SLOT):
        auth=SLOT/'home/.codex/auth.json'
        require(not os.path.lexists(auth) and not os.path.lexists(str(auth)+'.tmp'),
                'existing-login-will-not-be-overwritten')
        owned,role=(SLOT/n for n in ('owned-installation.json','single-role-installation.json'))
        i=RoleInstallation(str(owned),file_sha(owned),str(role),file_sha(role))
        require(not i.sock.exists() and not i.sock.is_symlink(),'owned-broker-active')
        db=sqlite3.connect(i.database.as_uri()+'?mode=ro',uri=True)
        try:
            require(db.execute('select paused from owner').fetchall()==[(1,)] and
                    all(not db.execute('select 1 from '+n+' limit 1').fetchone()
                        for n in ('tasks','role_registration','effects','native_dispatch','turns')),
                    'device-login-requires-unused-paused-slot')
        finally:db.close()
        from auth_relay_tls import relay
        def emit(stage,**info):
            print(json.dumps(dict(stage=stage,**info)),file=sys.stderr,flush=True)
        argv=i.child['argv'][:4]+['-c','cli_auth_credentials_store="file"','login','--device-auth']
        with relay(emit):
            return subprocess.call(argv,cwd=i.cwd,env=i.child_environment(),close_fds=True)


def main():
    os.umask(0o077)
    require(sys.argv[1:] in (['--prepare'],['--device-login']),'exact-durable-login-mode-required')
    approved()
    if sys.argv[1]=='--prepare':
        prepare()
        print('Prepared one private durable slot; owner paused, no native ID, no login.')
        return 0
    return login()


if __name__=='__main__':
    try:raise SystemExit(main())
    except (OSError,ValueError,KeyError,RuntimeError,sqlite3.Error,subprocess.SubprocessError):
        print('Durable role preflight stopped; no credential details emitted.',file=sys.stderr)
        raise SystemExit(78)
