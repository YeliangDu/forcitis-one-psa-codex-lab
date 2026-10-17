import os,re,sys,collections
root=sys.argv[1]
def count(path):
    ext=os.path.splitext(path)[1]
    try: txt=open(path,encoding='utf-8',errors='ignore').read()
    except: return 0
    n=0; inblk=False
    for l in txt.splitlines():
        s=l.strip()
        if not s: continue
        if ext=='.sql':
            if s.startswith('--'): continue
            n+=1; continue
        if inblk:
            if '*/' in s or '-->' in s: inblk=False
            continue
        if s.startswith('//'): continue
        if s.startswith('/*') or s.startswith('<!--'):
            if '*/' not in s and '-->' not in s: inblk=True
            continue
        if s.startswith('*'): continue
        n+=1
    return n
res=collections.Counter()
EXT={'.java','.sql','.ts','.tsx','.vue','.js','.mjs','.scss','.css','.kt','.yaml','.yml','.json','.feature'}
for d,ds,fs in os.walk(root):
    ds[:]=[x for x in ds if x not in('node_modules','dist','.git')]
    for f in fs:
        p=os.path.join(d,f); rel=os.path.relpath(p,root); e=os.path.splitext(f)[1]
        if e not in EXT: continue
        parts=rel.split('/')
        if rel.startswith('backend/src/main/java/com/forcitis/one/'):
            k=('main',)+tuple(parts[7:9]) if len(parts)>9 else ('main',parts[7],'-')
        elif rel.startswith('backend/src/test/java/com/forcitis/one/acceptance/'):
            k=('accept',)+tuple(parts[8:10]) if len(parts)>10 else ('accept',parts[8],'-')
        elif rel.startswith('backend/src/test/java/com/forcitis/one/'):
            k=('test',)+tuple(parts[7:9]) if len(parts)>9 else ('test',parts[7],'-')
        elif rel.startswith('backend/src/main/resources/db/migration/'):
            k=('sql',parts[6],'')
        elif rel.startswith('backend/src/test/resources'):
            k=('testres',parts[4] if len(parts)>5 else '-','')
        elif rel.startswith('backend/src/main/resources'):
            k=('mainres','','')
        elif rel.startswith('frontend/'):
            k=('fe',parts[1],(parts[2] if len(parts)>3 else '-')+':'+e)
        else:
            k=('other',parts[0],parts[1] if len(parts)>1 else '')
        res[k]+=count(p)
for k,v in sorted(res.items()): print('\t'.join(k),v,sep='\t')
