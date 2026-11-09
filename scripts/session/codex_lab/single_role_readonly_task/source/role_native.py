"""Pinned native policy shape and sanitized actual inference inventory checks."""
from pathlib import Path
import os
import re
import stat
import sys
import tomllib

from owned_runtime import BIN_SHA,POLICY_SHA,decode,file_sha,private,sha
from runtime_broker import physical,private_file
from runtime_store import need
from role_network import PORT,PROXY

MODEL='gpt-6.1-sol'
BASE_SLOT=b'/private/tmp/lab60-v2-config-official-pref-read'
FLAGS=('shell_tool','view_image','apps','plugins','code_mode','multi_agent','request_permissions_tool',
       'sleep_tool','deferred_executor','token_budget','current_time_reminder','send_message_to_user_async')
CATALOG_SHA='431320608eccbd6eaf11a3daf1d69bc14b93ae95734e3323c281f235c7d6437d'

def policy_additions(root):
    return ('\n(allow network-outbound (remote ip "localhost:'+str(PORT)+'"))\n'
            '(deny file-write* (subpath "'+str(root/'work')+'"))\n'
            '(deny file-read-data (literal "'+str(root/'work/assignment.json')+'"))\n').encode()

def environment(root):
    return dict(PATH='/usr/bin:/bin',HOME=str(root/'home'),TMPDIR=str(root/'tmp'),LANG='en_US.UTF-8',
                HTTPS_PROXY=PROXY,CODEX_ROLLOUT_TRACE_ROOT=str(root/'trace'))

def expected_config(root):
    return dict(model=MODEL,model_catalog_json=str(root/'model-catalog.json'),approval_policy='never',
                default_permissions=':read-only',cli_auth_credentials_store='file',web_search='disabled',
                features={k:False for k in FLAGS},
                tools={'update_plan':{'enabled':False},'experimental_request_user_input':{'enabled':False}},
                analytics={'enabled':False})

def check_native(i):
    argv,pins,env=(i.child[k] for k in ('argv','files_sha256','environment'))
    root=i.root
    need(sys.platform=='darwin' and root==Path('/private/tmp/lab60-owned-single-role-v1'),
         'reviewed-single-role-slot-required')
    need(type(argv) is list and len(argv)==10 and argv[:3]==['/usr/bin/sandbox-exec','-f',str(root/'runtime.sb')]
         and argv[4:]==['app-server','--listen','stdio://','--strict-config','-c',
                         'cli_auth_credentials_store="file"'],'single-role-strict-native-argv')
    bin,profile=physical(argv[3]),physical(argv[2])
    config=root/'home/.codex/config.toml';catalog=root/'model-catalog.json'
    for p in (root/'tmp',root/'trace',config.parent):private(p,stat.S_ISDIR)
    for p in (profile,config,catalog):private(p,stat.S_ISREG)
    raw=profile.read_bytes();suffix=policy_additions(root)
    need(raw.endswith(suffix),'single-role-profile-delta-required')
    base=raw[:-len(suffix)]
    need(base.count(os.fsencode(root))==1 and sha(base.replace(os.fsencode(root),BASE_SLOT))==POLICY_SHA,
         'single-role-base-policy-drift')
    need(file_sha(bin)==BIN_SHA and file_sha(catalog)==CATALOG_SHA
         and tomllib.loads(config.read_text())==expected_config(root)
         and env==environment(root),'single-role-config-or-binary-drift')
    need(pins=={str(p):file_sha(p) for p in (bin,profile,config,catalog)},'single-role-native-pins-differ')
    need(Path('/etc').resolve(strict=True)==Path('/private/etc'),'system-config-alias-drift')
    for path in (Path('/etc/codex'),Path('/private/etc/codex')):
        try:path.lstat()
        except FileNotFoundError:continue
        raise ValueError('unexpected-system-config')


def inventory(i,tid,turn):
    """Verify only the current thread/turn's actual provider request tools."""
    facts=[]
    for bundle in sorted((i.root/'trace').glob('trace-*-'+tid)):
        private(bundle,stat.S_ISDIR)
        p=bundle/'trace.jsonl'
        data=private_file(str(p),2*1024*1024)
        for line in data.splitlines():
            e=decode(line).get('payload',{})
            if e.get('type')!='inference_started' or e.get('thread_id')!=tid or e.get('codex_turn_id')!=turn:
                continue
            rp=e['request_payload']['path']
            need(type(rp) is str and re.fullmatch(r'payloads/[0-9]+\.json',rp),'unsafe-native-trace-path')
            raw=private_file(str(bundle/rp),2*1024*1024)
            tools=decode(raw).get('tools')
            need(type(tools) is list and len(tools)==1 and tools[0].get('type')=='function'
                 and tools[0].get('name')=='lab_read_assignment','native-inference-tool-ceiling-not-proved')
            facts.append(dict(thread_id=tid,turn_id=turn,tools=['lab_read_assignment'],request_sha256=sha(raw)))
    need(facts,'actual-native-inference-inventory-required')
    return facts
