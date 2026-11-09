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
APPLE_CA_SHA='9dae8d76e55cb08991f2b672d58999ea15560d910759c16b544f843bdffbb994'
BASE_SLOT=b'/private/tmp/lab60-v2-config-official-pref-read'
FLAGS=('shell_tool','view_image','apps','plugins','code_mode','multi_agent','request_permissions_tool',
       'sleep_tool','deferred_executor','token_budget','current_time_reminder','send_message_to_user_async')
CATALOG_SHA='d3abd842194800885cfdf099354dd2b7ef0c8d489d35fd90b8272cb190194703'

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
    check_model_catalog(decode(private_file(str(catalog),256*1024)))
    need(file_sha(bin)==BIN_SHA and file_sha(catalog)==CATALOG_SHA
         and tomllib.loads(config.read_text())==expected_config(root)
         and env==environment(root),'single-role-config-or-binary-drift')
    need(pins=={str(p):file_sha(p) for p in (bin,profile,config,catalog)},'single-role-native-pins-differ')
    need(Path('/etc').resolve(strict=True)==Path('/private/etc'),'system-config-alias-drift')
    for path in (Path('/etc/codex'),Path('/private/etc/codex')):
        try:path.lstat()
        except FileNotFoundError:continue
        raise ValueError('unexpected-system-config')


TOOL='lab_read_assignment'


def check_model_catalog(catalog):
    models=catalog.get('models') if type(catalog) is dict else None
    need(type(models) is list and len(models)==1 and type(models[0]) is dict
         and models[0].get('slug')==MODEL and models[0].get('tool_mode')=='direct',
         'single-role-catalog-requires-direct')
    return True


def _declarations(payload):
    need(type(payload) is dict,'invalid-provider-request')
    declared=[]
    if 'tools' in payload:
        need(type(payload['tools']) is list,'invalid-top-level-tools')
        declared.extend(payload['tools'])
    items=payload.get('input',[])
    need(type(items) is list,'invalid-provider-input')
    for item in items:
        need(type(item) is dict and 'additional_tools' not in item,'unsupported-tool-declaration-wrapper')
        if item.get('type')=='additional_tools':
            need(item.get('role')=='developer' and type(item.get('tools')) is list,'invalid-incremental-tools')
            declared.extend(item['tools'])
    return declared


def request_tool_inventory(payload):
    declarations=_declarations(payload)
    need(declarations,'missing-provider-tool-declaration')
    accepted=[]
    def take(tool,namespace):
        need(type(tool) is dict and tool.get('type')=='function' and tool.get('name')==TOOL,
             'provider-tool-outside-single-role-boundary')
        row=dict(namespace=namespace,name=TOOL)
        if row not in accepted:accepted.append(row)
    for item in declarations:
        need(type(item) is dict,'invalid-provider-tool')
        if item.get('type')=='namespace':
            members=item.get('tools')
            need(item.get('name')=='functions' and type(members) is list and len(members)==1,
                 'provider-namespace-outside-single-role-boundary')
            take(members[0],'functions')
        else:
            take(item,None)
    return accepted


def persisted_declarations(i,tid):
    """Conservative: a saved unsafe declaration is a no-model resume boundary."""
    sessions=i.home/'.codex/sessions'
    private(sessions,stat.S_ISDIR)
    matches=list(sessions.rglob('rollout-*'+tid+'.jsonl'))
    need(len(matches)==1,'one-existing-native-rollout-required')
    path=matches[0]
    private(path,stat.S_ISREG)
    need(path.is_relative_to(sessions),'native-rollout-outside-private-session-root')
    raw=private_file(str(path),16*1024*1024)
    rows=[decode(line) for line in raw.splitlines()]
    need(any(x.get('type')=='session_meta' and x.get('payload',{}).get('id')==tid for x in rows),
         'rollout-session-identity-mismatch')
    items=[x['payload'] for x in rows if x.get('type')=='response_item'
           and x.get('payload',{}).get('type')=='additional_tools']
    return raw,items


def require_clean_resume(i,tid):
    _,items=persisted_declarations(i,tid)
    for item in items:
        request_tool_inventory({'input':[item]})


def inventory(i,tid,turn):
    """Check both declared carriers; accept only a causally chained tool-free delta."""
    facts=[]
    for bundle in sorted((i.root/'trace').glob('trace-*-'+tid)):
        private(bundle,stat.S_ISDIR)
        data=private_file(str(bundle/'trace.jsonl'),2*1024*1024)
        calls={};responses={}
        for line in data.splitlines():
            e=decode(line).get('payload',{})
            call=e.get('inference_call_id')
            if e.get('type')=='inference_completed' and call in calls:
                response=e.get('response_id')
                if type(response) is str and response:responses[response]=calls[call]
                continue
            if e.get('type')!='inference_started' or e.get('thread_id')!=tid or e.get('codex_turn_id')!=turn:
                continue
            rp=e['request_payload']['path']
            need(type(rp) is str and re.fullmatch(r'payloads/[0-9]+\.json',rp),'unsafe-native-trace-path')
            raw=private_file(str(bundle/rp),2*1024*1024)
            payload=decode(raw)
            declarations=_declarations(payload)
            if declarations:
                tools=request_tool_inventory(payload)
            else:
                previous=payload.get('previous_response_id')
                need(type(previous) is str and previous in responses,'native-tool-delta-not-linked')
                tools=responses[previous]
            need(type(call) is str and call and call not in calls,'native-inference-call-id-invalid')
            calls[call]=tools
            facts.append(dict(thread_id=tid,turn_id=turn,tools=tools,request_sha256=sha(raw)))
    need(facts,'actual-native-inference-inventory-required')
    return facts
