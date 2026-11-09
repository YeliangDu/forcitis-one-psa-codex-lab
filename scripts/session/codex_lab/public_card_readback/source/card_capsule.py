"""Validate a bounded exact input capsule and a model's structured edit proposal."""
import hashlib,re
from pathlib import PurePosixPath
from owned_runtime import decode
from runtime_store import need

IDENTITY=('card','revision','session_id','B')
def sha(b):return hashlib.sha256(b).hexdigest()
def path(p):
    need(type(p) is str and 0<len(p)<=256 and re.fullmatch(r'[a-zA-Z0-9_./-]+',p)
         and not PurePosixPath(p).is_absolute() and all(x not in ('','.','..','.git') for x in p.split('/')),
         'capsule-path-refused')
    return p
def parse(assignment,raw,pin):
    need(type(raw) is bytes and 0<len(raw)<=196608 and sha(raw)==pin,'capsule-pin-mismatch')
    s=decode(raw)
    need(type(s) is dict and set(s)=={*IDENTITY,'files'} and type(assignment) is dict
         and all(k in assignment and type(s[k]) is type(assignment[k]) and s[k]==assignment[k] for k in IDENTITY)
         and type(s['files']) is list and 0<len(s['files'])<=16,'capsule-identity-mismatch')
    files={}
    for x in s['files']:
        need(type(x) is dict and set(x)=={'path','sha256','text','writable'},'capsule-file-shape')
        p=path(x['path']);t=x['text']
        need(p not in files and type(t) is str and '\0' not in t and len(t.encode())<=65536
             and sha(t.encode())==x['sha256'] and type(x['writable']) is bool,'capsule-file-not-exact')
        files[p]=x
    return files
def read_scope(assignment,scope_raw,approved_sha256):
    return {p:x['text'] for p,x in parse(assignment,scope_raw,approved_sha256).items()}
def prepare_edits(assignment,scope_raw,approved_sha256,patch_result):
    files=parse(assignment,scope_raw,approved_sha256)
    x=patch_result
    need(type(x) is dict and set(x)=={'revision','scope_sha256','edits'}
         and type(x['revision']) is int and x['revision']==assignment['revision'] and x['scope_sha256']==approved_sha256
         and type(x['edits']) is list and 0<len(x['edits'])<=len(files),'patch-subject-mismatch')
    changes={}
    for row in x['edits']:
        need(type(row) is dict and set(row)=={'path','before_sha256','old','new'},'patch-edit-shape')
        p=path(row['path']);o,n=row['old'],row['new']
        need(p in files and files[p]['writable'] and p not in changes and row['before_sha256']==files[p]['sha256']
             and type(o) is str and type(n) is str and o and o!=n and len(n.encode())<=24000 and '\0' not in n,'patch-edit-refused')
        t=files[p]['text'];i=t.find(o)
        need(i>=0 and t.find(o,i+1)==-1,'patch-context-not-unique')
        changes[p]=t[:i]+n+t[i+len(o):]
    return changes
