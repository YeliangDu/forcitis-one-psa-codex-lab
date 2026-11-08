"""Fixed-source, GET-only collector and offline aggregator. No candidate execution."""
import argparse
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import re
import stat
import types
import urllib.error
import urllib.parse
import urllib.request
import zipfile

SLUG = 'YeliangDu/forcitis-one-psa-codex-lab'
RID = 1449415666
API = 'https://api.github.com/repos/' + SLUG
LIMIT = 32 * 1024 * 1024


def need(value, reason):
    if not value:
        raise ValueError(reason)


def unique(pairs):
    result = {}
    for key, value in pairs:
        need(key not in result, 'duplicate JSON key')
        result[key] = value
    return result


def bad_constant(value):
    raise ValueError('nonfinite JSON')


def decode(raw):
    return json.loads(raw.decode('utf-8'), object_pairs_hook=unique, parse_constant=bad_constant)


def encode(value):
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, allow_nan=False) + '\n').encode()


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def next_page(link, path, page):
    matches = re.findall(r'<([^>]+)>; rel="next"', link)
    need(len(matches) == 1, 'ambiguous next page')
    url = urllib.parse.urlparse(matches[0])
    relative = path.split('?', 1)[0]
    expected = urllib.parse.parse_qs(path.split('?', 1)[1], keep_blank_values=True)
    expected['page'] = [str(page)]
    need(url.scheme == 'https' and url.netloc == 'api.github.com'
         and url.path in ('/repos/' + SLUG + '/' + relative, '/repositories/' + str(RID) + '/' + relative)
         and not url.fragment and urllib.parse.parse_qs(url.query, keep_blank_values=True) == expected,
         'foreign/skipped native page')


def read(path, maximum=LIMIT):
    path = Path(path)
    need(path.resolve(strict=True) == path, 'symlink/nonphysical input')
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(fd, 'rb') as stream:
        before = os.fstat(stream.fileno())
        need(stat.S_ISREG(before.st_mode) and before.st_nlink == 1 and before.st_uid == os.geteuid()
             and not before.st_mode & 0o022 and before.st_size <= maximum, 'unsafe/oversize input')
        raw = stream.read(maximum + 1)
        identity = lambda s: (s.st_dev, s.st_ino, s.st_size, s.st_mtime_ns, s.st_ctime_ns)
        need(len(raw) == before.st_size and identity(before) == identity(os.fstat(stream.fileno()))
             == identity(path.lstat()), 'input changed')
    return raw


def write(path, raw):
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, 'wb') as stream:
        stream.write(raw)
        stream.flush()
        os.fsync(stream.fileno())


def repository(value):
    need(type(value) is dict and type(value.get('id')) is int and value['id'] == RID
         and value.get('full_name') == SLUG and value.get('private') is False, 'foreign/nonpublic repo')


def verify_bundle(root, expected, source):
    need(re.fullmatch('[0-9a-f]{40}', source) and source == os.environ.get('GITHUB_SHA')
         and os.environ.get('GITHUB_REPOSITORY') == SLUG
         and os.environ.get('GITHUB_REPOSITORY_ID') == str(RID)
         and os.environ.get('GITHUB_EVENT_NAME') == 'push'
         and re.fullmatch('[0-9a-f]{64}', expected)
         and os.environ.get('GITHUB_REF') == 'refs/heads/lab/22-independent-public-candidate-proof-fdec099-' + expected,
         'wrong independently selected control context')
    raw = read(root / 'bundle.json', 1024 * 1024)
    need(digest(raw) == expected, 'external bundle digest mismatch')
    manifest = decode(raw)
    need(set(manifest) == {'version', 'files'} and manifest['version'] == 1
         and set(manifest['files']) == {'proof_runtime.py', 'expected-inputs.json', 'mq_lab_control_receipt.py', 'workflow.yml'}, 'bundle schema')
    buffers = {name: read(root / name) for name in manifest['files']}
    need(all(digest(raw) == manifest['files'][name] for name, raw in buffers.items()), 'control bundle changed')
    need(digest(read(Path(os.environ['GITHUB_WORKSPACE']) / '.github/workflows/lab-22-independent-public-candidate-proof.yml'))
         == manifest['files']['workflow.yml'], 'executed workflow source mismatch')
    pins = decode(buffers['expected-inputs.json'])
    need(pins['repository'] == SLUG and pins['repository_id'] == RID
         and source != pins['subject']['head'], 'candidate cannot select independent source')
    return pins, buffers, manifest


class Redirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        url = urllib.parse.urlparse(newurl)
        host = (url.hostname or '').lower()
        need(url.scheme == 'https' and not url.username and not url.password
             and url.port in (None, 443)
             and (host.endswith('.blob.core.windows.net') or host.endswith('.githubusercontent.com')),
             'unapproved artifact/log redirect')
        # Never forward the GitHub credential to a signed storage URL.
        return urllib.request.Request(newurl, headers={'User-Agent':'lab22-readonly'}, method='GET')


class Collector:
    def __init__(self, output):
        self.output, self.records, self.total = output, [], 0
        self.opener = urllib.request.build_opener(Redirect())

    def get(self, key, path, raw=False):
        need(re.fullmatch('[A-Za-z0-9_-]+', key) and len(self.records) < 512, 'request bound/key')
        allowed = (r'', r'pulls/43', r'pulls/43/(?:reviews|files)\?per_page=100&page=[1-9][0-9]*',
                   r'git/ref/heads/main', r'actions/runs\?event=pull_request&head_sha=[0-9a-f]{40}&per_page=100&page=[1-9][0-9]*',
                   r'actions/runs/42464006956(?:/attempts/1)?',
                   r'actions/runs/42464006956/attempts/1/jobs\?per_page=100&page=[1-9][0-9]*',
                   r'actions/runs/42464006956/artifacts\?per_page=100&page=[1-9][0-9]*',
                   r'actions/artifacts/[1-9][0-9]*/zip', r'actions/jobs/[1-9][0-9]*/logs',
                   r'git/(?:commits|trees|blobs)/[0-9a-f]{40}')
        need(any(re.fullmatch(pattern, path) for pattern in allowed), 'unapproved GET')
        request = urllib.request.Request(API + ('/' + path if path else ''), method='GET', headers={
            'Authorization':'Bearer ' + os.environ['GH_TOKEN'], 'Accept':'application/vnd.github+json',
            'User-Agent':'lab22-readonly', 'X-GitHub-Api-Version':'2022-11-28'})
        with self.opener.open(request, timeout=45) as response:
            body = response.read(LIMIT + 1)
            need(response.status == 200 and len(body) <= LIMIT, 'native GET incomplete/oversize')
            link = response.headers.get('Link', '')
        self.total += len(body)
        need(self.total <= 256 * 1024 * 1024, 'collection total bound')
        name = f'{len(self.records):04d}.raw'
        write(self.output / name, body)
        self.records.append(dict(key=key, path=path, filename=name, size=len(body), sha256=digest(body), link=link))
        return body if raw else decode(body)

    def pages(self, key, path, field=None):
        rows, expected, seen = [], None, set()
        for page in range(1, 17):
            value = self.get(f'{key}_{page}', path + f'&page={page}')
            entries = value[field] if field else value
            need(type(entries) is list, 'native page shape')
            if field:
                count = value['total_count']
                need(type(count) is int and count >= 0 and (expected is None or count == expected), 'changing page total')
                expected = count
            for item in entries:
                identity = item.get('id', item.get('filename'))
                need(identity is not None and identity not in seen, 'duplicate/missing paged identity')
                seen.add(identity)
                rows.append(item)
            link = self.records[-1]['link']
            if 'rel="next"' not in link:
                need(expected is None or len(rows) == expected, 'incomplete native pages')
                return rows
            next_page(link, path, page + 1)
        raise ValueError('pagination bound')


def collect(pins, source, output):
    c = Collector(output)
    c.get('repo', '')
    c.get('main_before', 'git/ref/heads/main')
    c.get('pr_before', 'pulls/43')
    ci = pins['candidate_ci']
    c.get('run', 'actions/runs/42464006956')
    c.get('attempt', 'actions/runs/42464006956/attempts/1')
    c.pages('latest_before', f"actions/runs?event=pull_request&head_sha={pins['subject']['head']}&per_page=100", 'workflow_runs')
    c.pages('files', 'pulls/43/files?per_page=100')
    c.pages('reviews_before', 'pulls/43/reviews?per_page=100')
    c.pages('jobs', 'actions/runs/42464006956/attempts/1/jobs?per_page=100', 'jobs')
    artifacts = c.pages('artifacts', 'actions/runs/42464006956/artifacts?per_page=100', 'artifacts')
    names = [ci['artifact_name'], f"lab-backend-input-{pins['subject']['head']}-1"]
    names += [f"lab-backend-result-{pins['subject']['head']}-1-{s}" for s in ('1','2','3','4','unit')]
    for index, name in enumerate(names):
        matches = [a for a in artifacts if a['name'] == name]
        need(len(matches) == 1 and matches[0]['expired'] is False, 'missing/ambiguous required artifact')
        c.get(f'zip_{index}', f"actions/artifacts/{matches[0]['id']}/zip", raw=True)
    c.get('upload_log', f"actions/jobs/{ci['verification_job_id']}/logs", raw=True)
    c.get('unit_log', f"actions/jobs/{ci['backend_jobs']['unit']}/logs", raw=True)
    # Read modes/blobs/content from fixed Git API objects, never execute H files.
    trees, blobs = {}, set()
    def tree(sha):
        if sha not in trees:
            value = c.get('tree_' + sha, 'git/trees/' + sha)
            need(value.get('truncated') is not True, 'truncated tree')
            rows = value['tree']
            need(len({x['path'] for x in rows}) == len(rows), 'duplicate tree path')
            trees[sha] = {x['path']:x for x in rows}
        return trees[sha]
    roots = {}
    for label in ('base','head'):
        commit = pins['subject'][label]
        roots[label] = c.get('commit_' + label, 'git/commits/' + commit)['tree']['sha']
        paths = {e['path'] for e in pins['manifests']['old' if label == 'base' else 'new']['entries']}
        paths |= {e['path'] for e in pins['skip_source_review']['value']}
        for path in sorted(paths):
            parts = path.split('/')
            sha = roots[label]
            for i, part in enumerate(parts):
                row = tree(sha).get(part)
                need(row is not None and row['mode'] in ('040000','100644','100755'), 'unapproved/missing tree mode')
                if i < len(parts)-1:
                    need(row['type'] == 'tree', 'non-directory source component')
                    sha = row['sha']
                else:
                    need(row['type'] == 'blob', 'nonregular source object')
                    if row['sha'] not in blobs:
                        c.get('blob_' + row['sha'], 'git/blobs/' + row['sha'])
                        blobs.add(row['sha'])
    c.pages('reviews_after', 'pulls/43/reviews?per_page=100')
    c.pages('latest_after', f"actions/runs?event=pull_request&head_sha={pins['subject']['head']}&per_page=100", 'workflow_runs')
    c.get('pr_after', 'pulls/43')
    c.get('main_after', 'git/ref/heads/main')
    c.get('repo_after', '')
    write(output / 'capture-index.json', encode(dict(version=1,control_source=source,
          control_run=os.environ['GITHUB_RUN_ID'],control_attempt=os.environ['GITHUB_RUN_ATTEMPT'],repository=SLUG,repository_id=RID,
          subject=pins['subject'],records=c.records,release_authority=False)))


class Snapshot:
    def __init__(self, root, source):
        self.root = root
        index = decode(read(root/'capture-index.json'))
        need(index['control_source'] == source and index['release_authority'] is False, 'raw collection source mismatch')
        need(index['control_run'] == os.environ['GITHUB_RUN_ID']
             and index['control_attempt'] == os.environ['GITHUB_RUN_ATTEMPT'], 'different control run/attempt snapshot')
        records = index['records']
        need(type(records) is list and 0 < len(records) <= 512, 'capture index bound')
        self.records, self.buffers, self.total = {}, {}, 0
        for i, row in enumerate(records):
            need(row['filename'] == f'{i:04d}.raw' and row['key'] not in self.records, 'unsafe/duplicate raw record')
            raw = read(root / row['filename'])
            self.total += len(raw)
            need(self.total <= 256*1024*1024 and len(raw) == row['size'] and digest(raw) == row['sha256'], 'raw digest/size mismatch')
            self.records[row['key']], self.buffers[row['key']] = row, raw
        need({p.name for p in root.iterdir()} == {'capture-index.json'} | {r['filename'] for r in records}, 'extra raw files')

    def raw(self, key):
        return self.buffers[key]

    def value(self, key, path=None):
        if path is not None:
            need(self.records[key]['path'] == path, 'request/response scope mismatch')
        return decode(self.raw(key))

    def pages(self, key, path, field=None):
        rows, total, seen = [], None, set()
        for page in range(1,17):
            k = f'{key}_{page}'
            value = self.value(k, path + f'&page={page}')
            entries = value[field] if field else value
            need(type(entries) is list, 'page rows')
            if field:
                n = value['total_count']
                need(type(n) is int and n >= 0 and (total is None or total == n), 'page total moved')
                total = n
            for row in entries:
                identity = row.get('id',row.get('filename'))
                need(identity is not None and identity not in seen, 'duplicate page row')
                seen.add(identity);rows.append(row)
            link = self.records[k]['link']
            if 'rel="next"' not in link:
                need(total is None or len(rows)==total, 'incomplete captured pages')
                return rows
            next_page(link, path, page + 1)
        raise ValueError('page bound')


def zip_members(raw):
    need(len(raw) <= LIMIT, 'ZIP bound')
    result, total, seen = {}, 0, set()
    with zipfile.ZipFile(io.BytesIO(raw)) as archive:
        need(len(archive.infolist()) <= 128, 'ZIP member bound')
        for member in archive.infolist():
            name = member.filename
            need(member.orig_filename == name and not name.startswith('/') and '\\' not in name
                 and ':' not in name and all(x not in ('','.','..') for x in name.rstrip('/').split('/'))
                 and name not in seen and not member.flag_bits & 1
                 and stat.S_IFMT(member.external_attr >> 16) in
                 ((0,stat.S_IFDIR) if member.is_dir() else (0,stat.S_IFREG)), 'unsafe ZIP')
            seen.add(name)
            total += member.file_size
            need(total <= 64*1024*1024, 'ZIP expansion bound')
            if not member.is_dir():result[name]=archive.read(member)
        need(archive.testzip() is None, 'ZIP CRC')
    return result


def aggregate(pins, source, buffers, raw_root):
    s = Snapshot(raw_root, source);subject=pins['subject'];ci=pins['candidate_ci'];head=subject['head'];base=subject['base']
    repo=s.value('repo','');repository(repo)
    final_repo=s.value('repo_after','');repository(final_repo)
    need(all(repo.get(k)==final_repo.get(k) for k in ('id','full_name','private','visibility')),'repository identity moved')
    for key in ('main_before','main_after'):
        main=s.value(key,'git/ref/heads/main')
        need(main['ref']=='refs/heads/main' and main['object']['sha']==base,'main moved')
    before=s.value('pr_before','pulls/43');pr=s.value('pr_after','pulls/43')
    for field in ('number','state','draft','merged','head','base','body','title','labels','changed_files'):
        need(before.get(field)==pr.get(field), 'PR changed during observation')
    need(pr['number']==43 and pr['state']=='open' and pr['merged'] is False
         and type(pr['draft']) is bool and pr['head']['sha']==head and pr['base']['sha']==base
         and pr['base']['ref']=='main', 'wrong current subject')
    repository(pr['head']['repo']);repository(pr['base']['repo'])
    run=s.value('run','actions/runs/42464006956');attempt=s.value('attempt','actions/runs/42464006956/attempts/1')
    for row in (run,attempt):
        repository(row['repository'])
        need(type(row['id']) is int and row['id']==ci['run_id'] and type(row['run_attempt']) is int
             and row['run_attempt']==ci['run_attempt'] and row['head_sha']==head
             and row['path']==ci['path'] and row['event']=='pull_request' and row['status']=='completed'
             and row['conclusion']=='success', 'wrong native candidate run')
    for phase in ('before','after'):
        runs=s.pages('latest_'+phase,f'actions/runs?event=pull_request&head_sha={head}&per_page=100','workflow_runs')
        selected=[]
        for row in runs:
            if row.get('event')!='pull_request' or row.get('path')!=ci['path'] or row.get('head_sha')!=head:continue
            repository(row['repository'])
            need(type(row['id']) is int and type(row['run_attempt']) is int,'untyped native run identity')
            associations=[a for a in row['pull_requests'] if a['number']==43]
            if not associations:continue
            need(len(associations)==1,'ambiguous PR association')
            for side in ('head','base'):
                branch=associations[0][side];need(branch['sha']==subject[side] and branch['repo']['id']==RID,'foreign run association')
            selected.append((row['id'],row['run_attempt']))
        need(selected and len(selected)==len(set(selected)) and max(selected)==(ci['run_id'],ci['run_attempt']), 'newer candidate run/attempt')
    files=s.pages('files','pulls/43/files?per_page=100')
    need(len(files)==pr['changed_files'] and sorted(x['filename'] for x in files)==sorted(pins['manifests']['all_changed_files']), 'full changed-files mismatch')
    reviews=s.pages('reviews_before','pulls/43/reviews?per_page=100');after=s.pages('reviews_after','pulls/43/reviews?per_page=100')
    need(reviews==after,'reviews changed during observation')
    for kind,expected in pins['reviews'].items():
        rows=[r for r in reviews if r['id']==expected['id']];need(len(rows)==1,'missing review')
        row=rows[0];body=row.get('body','');target=next(x for x in pins['native_lane_and_review']['value']['reviews'] if x['id']==row['id'])
        need(row['commit_id']==head and row['state']=='COMMENTED' and digest(body.encode())==target['body_sha256']
             and body.splitlines()[:3]==['复审结论：通过','commit：'+head,'复审者：'+expected['reviewer']], 'review identity/verdict/body mismatch')
        speaking=[r for r in reviews if r.get('commit_id')==head
                  and len((r.get('body') or '').splitlines())>=3
                  and (r.get('body') or '').splitlines()[1:3]==['commit：'+head,'复审者：'+expected['reviewer']]]
        need(speaking and speaking[-1]['id']==row['id'],'newer assigned review')
    jobs=s.pages('jobs','actions/runs/42464006956/attempts/1/jobs?per_page=100','jobs');by_id={j['id']:j for j in jobs}
    def step(binding):
        job=by_id[binding['job']]
        need(job['head_sha']==head and job['conclusion']=='success' and job['runner_group_name']=='GitHub Actions'
             and job['labels']==['ubuntu-24.04'],'unverified hosted job')
        rows=[x for x in job['steps'] if x['number']==binding['step']]
        need(len(rows)==1 and rows[0]['name']==binding['name'] and rows[0]['conclusion']=='success','missing/failed numbered step')
    witness=pins['native_lane_and_review']['value']
    for binding in list(witness['lanes'].values())+list(witness['backend_slots'].values())+[witness['backend_aggregation'],witness['receipt_upload']]:step(binding)
    skipped_job=by_id[ci['main_health_skipped_job_id']]
    need(skipped_job['name']=='Lab main health' and skipped_job['head_sha']==head
         and skipped_job['conclusion']=='skipped','PR main-health skip shape')
    artifacts=s.pages('artifacts','actions/runs/42464006956/artifacts?per_page=100','artifacts')
    names=[ci['artifact_name'],f'lab-backend-input-{head}-1']+[f'lab-backend-result-{head}-1-{x}' for x in ('1','2','3','4','unit')]
    decoded=[];metadata=[]
    for i,name in enumerate(names):
        rows=[x for x in artifacts if x['name']==name];need(len(rows)==1 and rows[0]['expired'] is False,'artifact selection')
        a=rows[0];link=a['workflow_run'];need(link['id']==ci['run_id'] and link['head_sha']==head and link['repository_id']==RID,'artifact native run linkage')
        need(s.records[f'zip_{i}']['path']==f"actions/artifacts/{a['id']}/zip",'download identity')
        z=s.raw(f'zip_{i}')
        if a.get('digest') is not None:need(a['digest']=='sha256:'+digest(z),'native ZIP digest')
        decoded.append(zip_members(z));metadata.append(a)
    need(metadata[0]['id']==ci['artifact_id'] and digest(s.raw('zip_0'))==ci['zip_sha256']
         and digest(decoded[0]['receipt.json'])==ci['receipt_sha256'],'fixed candidate receipt bytes')
    need(set(decoded[0])=={'receipt.json','backend-summary.json'},'unknown final receipt files')
    receipt=decode(decoded[0]['receipt.json'])
    need(receipt['head_sha']==receipt['observed_head']==head and receipt['base_sha']==base
         and str(receipt['run_id'])==str(ci['run_id']) and str(receipt['run_attempt'])=='1'
         and receipt['ready'] is True and set(receipt['lanes'])==set(ci['required_lanes'])
         and all(x==dict(applicability='required',proof='passed',outcome='success') for x in receipt['lanes'].values()),
         'required candidate lanes not actually passed')
    summary=decode(decoded[0]['backend-summary.json']);need(summary==ci['backend_observed'],'backend summary changed')
    need(set(decoded[1])=={'head','run','attempt','inventory.json','api/openapi.json','api/generated/schema.ts',
                         'api/generated/validators.ts','api/generated/operations.ts'},'unknown/missing backend inputs')
    need(decoded[1]['head'].decode().strip()==head and decoded[1]['run'].decode().strip()==str(ci['run_id'])
         and decoded[1]['attempt'].decode().strip()=='1','backend input attempt/source mismatch')
    inventory=decode(decoded[1]['inventory.json']);need(inventory['integration_classes']==373 and len(inventory['shards'])==4,'backend inventory')
    totals={'tests':0,'skipped':0};classes=set()
    skip=pins['exact_skip']['value']
    for i,slot in enumerate(('1','2','3','4','unit'),2):
        need(set(decoded[i])=={'result.json'},'unknown backend result files')
        raw=decoded[i]['result.json'];row=decode(raw);expected=skip['slots'][slot]
        need(digest(raw)==expected['receipt_sha256'] and row['head']==head and str(row['run'])==str(ci['run_id'])
             and str(row['attempt'])=='1' and row['slot']==slot and row['tests']==expected['tests']
             and row['skipped']==expected['skipped'] and row['failures']==row['errors']==0
             and row['missing']==row['extra']==[] and len(row['classes'])==len(set(row['classes']))==row['executed_classes'],'slot report mismatch')
        if slot!='unit':need(set(row['classes'])==set(inventory['shards'][int(slot)-1]) and not classes&set(row['classes']),'shard coverage');classes.update(row['classes'])
        totals['tests']+=row['tests'];totals['skipped']+=row['skipped']
    need(len(classes)==373 and totals=={'tests':4798,'skipped':19},'full backend totals')
    # Exact class-level native skip evidence, never invented method identities.
    unit=re.sub(r'\x1b\[[0-9;]*m','',s.raw('unit_log').decode('utf-8'));observed_skips={}
    for line in unit.splitlines():
        m=re.search(r'Tests run:\s*(\d+),\s*Failures:\s*(\d+),\s*Errors:\s*(\d+),\s*Skipped:\s*(\d+).*?\bin\s+([\w.$]+)',line)
        if m and int(m[4]):
            need(m[5] not in observed_skips,'duplicate skipped class line')
            observed_skips[m[5]]=(int(m[1]),int(m[2]),int(m[3]),int(m[4]))
    need(observed_skips=={x['class_name']:(x['tests'],0,0,x['skips']) for x in skip['skip_classes']},'exact skipped classes')
    trees={k[len('tree_'):]:decode(v) for k,v in s.buffers.items() if k.startswith('tree_')}
    def source_file(label,path):
        sha=s.value('commit_'+label)['tree']['sha'];parts=path.split('/')
        for part in parts:
            rows=[x for x in trees[sha]['tree'] if x['path']==part];need(len(rows)==1,'source tree ambiguity');entry=rows[0];sha=entry['sha']
        blob=s.value('blob_'+sha);need(blob['sha']==sha and blob['encoding']=='base64','source blob')
        content=base64.b64decode(blob['content'],validate=False)
        need(len(content)==blob['size'] and hashlib.sha1(b'blob '+str(len(content)).encode()+b'\0'+content).hexdigest()==sha,'raw Git blob identity')
        return entry,content
    for label,key in (('base','old'),('head','new')):
        manifest=pins['manifests'][key]
        for expected in manifest['entries']:
            row,content=source_file(label,expected['path'])
            need(row['type']=='blob' and row['mode']==expected['mode'] and row['sha']==expected['blob'] and digest(content)==expected['sha256'],'manifest mode/blob/raw mismatch')
        canonical=json.dumps({'version':manifest['version'],'entries':manifest['entries']},sort_keys=True,ensure_ascii=False,separators=(',',':')).encode()
        need(digest(canonical)==manifest['digest'],'canonical manifest digest')
    for row in pins['skip_source_review']['value']:
        _,old=source_file('base',row['path']);_,new=source_file('head',row['path'])
        need(old==new and digest(old)==row['sha256'] and row['unchanged_from']==base
             and len(re.findall(r'@Disabled\b',new.decode()))==row['disabled_annotations'],'skip source changed')
    # Uploaded ZIP origin: unique approved action in reviewed workflow + native
    # numbered step + unique log finalization/id/name/digest, no fabricated fields.
    _,workflow_raw=source_file('head','.github/workflows/lab-pr-ci.yml')
    text=workflow_raw.decode();part=text.split('\n  verify:\n',1)[1].split('\n  main-health:',1)[0]
    need(part.count('uses: actions/upload-artifact@')==1,'ambiguous candidate final upload source')
    log=s.raw('upload_log').decode('utf-8')
    need(log.count('##[group]Run actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02')==1,'ambiguous upload action log')
    hashes=re.findall(r'SHA256 digest of uploaded artifact zip is ([0-9a-f]{64})',log)
    finalized=re.findall(r'Artifact (\S+)\.zip successfully finalized\. Artifact ID ([0-9]+)',log)
    need(hashes==[ci['zip_sha256']] and finalized==[(ci['artifact_name'],str(ci['artifact_id']))],'unique native upload id/digest mismatch')
    # Load only separately pinned CONTROL code bytes, never downloaded evidence.
    module=types.ModuleType('fixed_public_receipt')
    exec(compile(buffers['mq_lab_control_receipt.py'],'<pinned-control-receipt>','exec'),module.__dict__)
    pin=dict(repository=SLUG,repository_id=RID,number=43,base=base,head=head,target='main',old_control_commit=base,
        independent_source=source,old_manifest=pins['manifests']['old']['digest'],new_manifest=pins['manifests']['new']['digest'],
        required_lanes=ci['required_lanes'],workflow=dict(path=ci['path'],source_commit=head,source_blob=next(e['blob'] for e in pins['manifests']['new']['entries'] if e['path']==ci['path'])),
        run_id=ci['run_id'],run_attempt=1,aggregation_job_id=ci['verification_job_id'],upload_step=witness['receipt_upload']['step'],artifact_id=ci['artifact_id'],
        artifact_name=ci['artifact_name'],zip_sha256=ci['zip_sha256'],receipt_sha256=ci['receipt_sha256'],expected_tests=4798,
        exact_skip_reference=dict(witness=pins['exact_skip']['value'],source_review=pins['skip_source_review']['value']))
    observed=dict(repository=repo,current_pr=pr,source_evidence={k:pin[k] for k in ('independent_source','old_control_commit','old_manifest','new_manifest','workflow')},
        run=run,latest=dict(run_id=ci['run_id'],run_attempt=1),jobs=jobs,artifacts=artifacts,
        download=dict(artifact_id=ci['artifact_id'],run_id=ci['run_id'],attempt=1,zip_bytes=s.raw('zip_0')),exact_skip_reference=pin['exact_skip_reference'])
    result=module.validate_public_candidate_proof(pin,observed)
    need(pr['draft'] is True or result['action']=='verified-readonly','pure validator refused nondraft snapshot')
    return dict(action='hold' if pr['draft'] else 'verified-readonly',reason='actual-PR-is-draft' if pr['draft'] else 'native-facts-reconciled',
        facts_checked=True,fact_scope=['native-source','attempt','numbered-lanes','review-body','ZIP','class-slot-source'],
        complete_candidate_proof=not pr['draft'],external_source_pin_verified=False,
        actual_draft=pr['draft'],pure_result=result,subject=subject,control_source=source,
        native_capture_sha256=digest(read(raw_root/'capture-index.json')),allows_new_write=False,release_authority=False,
        ordinary_main_health=False,installation_authority=False)


def final_reread(pins, source, raw_root, output):
    """Explicit final GET check, never a fallback to repair missing snapshots."""
    original=Snapshot(raw_root,source)
    directory=output/'final-native';directory.mkdir(mode=0o700)
    c=Collector(directory)
    repo=c.get('repo','');repository(repo)
    prior=original.value('repo_after')
    need(all(repo.get(k)==prior.get(k) for k in ('id','full_name','private','visibility')),'final repository moved')
    main=c.get('main','git/ref/heads/main')
    need(main['ref']=='refs/heads/main' and main['object']['sha']==pins['subject']['base'],'final main moved')
    current=c.get('pr','pulls/43');previous=original.value('pr_after')
    need(all(current.get(k)==previous.get(k) for k in
             ('number','state','draft','merged','head','base','body','title','labels','changed_files')),'final PR moved')
    reviews=c.pages('reviews','pulls/43/reviews?per_page=100')
    need(reviews==original.pages('reviews_after','pulls/43/reviews?per_page=100'),'final reviews moved')
    path=f"actions/runs?event=pull_request&head_sha={pins['subject']['head']}&per_page=100"
    runs=c.pages('latest',path,'workflow_runs')
    need(runs==original.pages('latest_after',path,'workflow_runs'),'final latest run/attempt moved')
    raw=encode(dict(control_source=source,control_run=os.environ['GITHUB_RUN_ID'],
                    control_attempt=os.environ['GITHUB_RUN_ATTEMPT'],records=c.records,release_authority=False))
    write(directory/'capture-index.json',raw)
    return digest(raw)


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('mode',choices=('collect','aggregate'))
    parser.add_argument('--source',required=True);parser.add_argument('--bundle-sha256',required=True)
    parser.add_argument('--output',required=True);parser.add_argument('--raw')
    args=parser.parse_args();output=Path(args.output).absolute()
    need(not output.exists() and output.parent.resolve()==output.parent,'output must be new/physical')
    output.mkdir(mode=0o700)
    try:
        pins,buffers,_=verify_bundle(Path(__file__).resolve().parent,args.bundle_sha256,args.source)
        if args.mode=='collect':collect(pins,args.source,output)
        else:
            report=aggregate(pins,args.source,buffers,Path(args.raw).absolute())
            report['final_native_sha256']=final_reread(pins,args.source,Path(args.raw).absolute(),output)
            write(output/'observation.json',encode(report))
    except Exception as failure:
        write(output/'HOLD.json',encode(dict(action='hold',reason=type(failure).__name__,allows_new_write=False,
                                           release_authority=False,ordinary_main_health=False,installation_authority=False)))
        raise SystemExit(1)


if __name__=='__main__':main()
