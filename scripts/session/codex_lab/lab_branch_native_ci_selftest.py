"""#45 actual NativeGithub.runs -> shared command, native JSON/ZIP port only."""
import copy
from email.message import Message
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import unittest
from unittest.mock import patch
import urllib.parse
import zipfile

HERE=Path(__file__).resolve().parent
REPO='YeliangDu/forcitis-one-psa-codex-lab';RID=1449415666
API='https://api.github.com/repos/'+REPO
WORKFLOW='.github/workflows/lab-45-branch-lifecycle-ci.yml'
CORE_SHA='99122154e3ce796c23e16221e83804df63dcf137ad2c998cfeac4691a40fea8e'


def module(name,path):
    spec=importlib.util.spec_from_file_location(name,path)
    value=importlib.util.module_from_spec(spec);spec.loader.exec_module(value)
    return value


def repository():return dict(id=RID,full_name=REPO,private=False)


def zip_receipt(value):
    stream=io.BytesIO()
    with zipfile.ZipFile(stream,'w') as output:
        info=zipfile.ZipInfo('native-pr-verdict.json',date_time=(2026,1,1,0,0,0))
        output.writestr(info,json.dumps(value,sort_keys=True,separators=(',',':')).encode())
    return stream.getvalue()


class Response:
    def __init__(self,value,binary=False,link='',url=API):
        self.status=200;self.headers=Message()
        self.headers['Link']=link
        self.headers['Content-Type']='application/zip' if binary else 'application/json'
        self.raw=value if binary else json.dumps(value).encode()
        self.url=url;self.cursor=0
    def read(self,limit=-1):
        end=len(self.raw) if limit<0 else self.cursor+limit
        value=self.raw[self.cursor:end];self.cursor+=len(value);return value
    def geturl(self):return self.url
    def getcode(self):return self.status
    def getheader(self,key,default=None):return self.headers.get(key,default)
    def close(self):return None
    def __enter__(self):return self
    def __exit__(self,*args):return None


class NativeCiHttp:
    def __init__(self,test,binding,pr):
        self.test,self.binding,self.pr=test,binding,pr
        self.calls=[];self.writes=[];self.total=None;self.unreadable=set()
        self.runs=[];self.artifacts=[];self.archives={}

    def reply(self,value,binary=False,link=''):
        return Response(value,binary=binary,link=link,url=self.calls[-1][1])

    def report(self,attempt=1):
        return dict(head=self.binding['head'],base=self.binding['base'],run='700',attempt=str(attempt),tests=6,
                    failures=[],errors=[],skipped=[],expected_failures=[],unexpected_successes=[])

    def artifact(self,attempt=1,report=None):
        identity=97000+attempt;data=zip_receipt(self.report(attempt) if report is None else report)
        value=dict(id=identity,name='lab-45-branch-lifecycle-'+str(attempt),expired=False,
            digest='sha256:'+hashlib.sha256(data).hexdigest(),
            workflow_run=dict(id=700,head_sha=self.binding['head'],head_branch=self.binding['branch'],
                              repository_id=RID,head_repository_id=RID))
        self.archives[identity]=data
        return value

    def run(self,identity=700,attempt=1,head=None):
        reduced=dict(id=RID,name=REPO.split('/')[1],url=API)
        return dict(id=identity,run_attempt=attempt,event='pull_request',path=WORKFLOW,
            head_sha=self.binding['head'] if head is None else head,head_branch=self.binding['branch'],
            repository=repository(),head_repository=repository(),status='completed',conclusion='success',
            pull_requests=[dict(number=self.binding['pr_number'],
                head=dict(ref=self.binding['branch'],sha=self.binding['head'],repo=copy.deepcopy(reduced)),
                base=dict(ref='main',sha=self.binding['base'],repo=copy.deepcopy(reduced)))])

    def open(self,request,timeout):
        method,url=request.get_method(),request.full_url;self.calls.append((method,url))
        if method!='GET':
            self.writes.append((method,url));raise AssertionError('READ_CI_MUST_NOT_WRITE_NATIVE_API')
        self.test.assertTrue(url==API or url.startswith(API+'/'),'CI_HTTP_OUTSIDE_FIXED_REPOSITORY')
        relative=url.removeprefix(API).lstrip('/')
        if relative in self.unreadable:raise OSError('synthetic exact native evidence unreadable')
        if relative=='':return self.reply(repository())
        if relative=='pulls/'+str(self.binding['pr_number']):return self.reply(self.pr)
        path,_,query=relative.partition('?');params=urllib.parse.parse_qs(query)
        if path=='actions/runs' and query:
            self.test.assertEqual([self.binding['branch']],params.get('branch'))
            self.test.assertEqual(['100'],params.get('per_page'));self.test.assertEqual(['1'],params.get('page'))
            return self.reply(dict(total_count=len(self.runs) if self.total is None else self.total,workflow_runs=self.runs))
        if path in ('actions/runs/700','actions/runs/700/attempts/1','actions/runs/700/attempts/2'):
            exact=next(r for r in self.runs if r['id']==700)
            return self.reply(exact)
        if path=='actions/runs/700/artifacts':
            self.test.assertEqual(['100'],params.get('per_page'));self.test.assertEqual(['1'],params.get('page'))
            return self.reply(dict(total_count=len(self.artifacts),artifacts=self.artifacts))
        if path.startswith('actions/artifacts/'):
            parts=path.split('/');identity=int(parts[2])
            if len(parts)==4 and parts[3]=='zip':
                return self.reply(self.archives[identity],binary=True)
            return self.reply(next(a for a in self.artifacts if a['id']==identity))
        if path.startswith('actions/runs/700/attempts/') and path.endswith('/jobs'):
            attempt=int(path.split('/')[4])
            return self.reply(dict(total_count=1,jobs=[dict(id=87000+attempt,name='branch-lifecycle',
                head_sha=self.binding['head'],conclusion='success',runner_group_name='GitHub Actions',labels=['ubuntu-24.04'],
                status='completed',steps=[dict(number=7,name='Run actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02',status='completed',conclusion='success')])]))
        raise AssertionError('UNBOUND_CI_HTTP_ROUTE: '+relative)

    def archive_reads(self,attempt):
        return sum(url==API+'/actions/artifacts/'+str(97000+attempt)+'/zip' for _,url in self.calls)


class NativeCiAssociationAcceptanceTest(unittest.TestCase):
    def setUp(self):
        frozen=HERE/'lab_branch_lifecycle_selftest.py'
        self.assertEqual(CORE_SHA,hashlib.sha256(frozen.read_bytes()).hexdigest())
        core_module=module('lab45_ci_frozen_fixture',frozen)
        self.core=core_module.BranchLifecycleAcceptanceTest(methodName='runTest')
        self.core.setUp();self.addCleanup(self.core.doCleanups)
        self.core.card['branch']='codex/45-branch-lifecycle'
        self.core.dispatch();self.core.commit_publish('synthetic current native CI head\n')
        answer=self.core.command('ensure-pr',dict(binding=self.core.binding))
        self.binding=copy.deepcopy(answer['binding'])
        pr=dict(id=9401,number=self.binding['pr_number'],state='open',draft=True,merged=False,
            head=dict(ref=self.binding['branch'],sha=self.binding['head'],repo=repository()),
            base=dict(ref='main',sha=self.binding['base'],repo=repository()))
        self.http=NativeCiHttp(self,self.binding,pr)
        native_module=module('lab45_ci_native_adapter',HERE/'lab_branch_native.py')
        credentials=patch.dict(os.environ,{'GH_TOKEN':'','GITHUB_TOKEN':''});credentials.start();self.addCleanup(credentials.stop)
        self.native=native_module.NativeGithub({},dict(allowed_actions=['read-ci','rerun-ci'],ci_workflow=WORKFLOW),False)
        self.native.opener=self.http
        self.rerun_attempts=0;original=self.native.rerun
        def observe_rerun(run,attempt):
            self.rerun_attempts+=1;return original(run,attempt)
        self.native.rerun=observe_rerun
        self.http.runs=[self.http.run()];self.http.artifacts=[self.http.artifact()]
        self.before_git=len(self.core.commands)

    def command(self,action='read-ci',**extra):
        request=dict(binding=copy.deepcopy(self.binding),**extra)
        return self.core.live.run_branch_command(action,request,self.core.state,self.core.git,self.native)

    def no_write(self):
        self.assertEqual([],self.http.writes)
        self.assertEqual(0,self.rerun_attempts)
        self.assertEqual([],self.core.deletes)
        self.assertTrue(all(method=='GET' and url.startswith(API) for method,url in self.http.calls))
        self.assertFalse(any(args[0] in ('push','checkout','merge') for args in self.core.commands[self.before_git:]))

    def test_historical_mutated_association_cannot_poison_current_exact_run_in_either_order(self):
        current=self.http.run();old=self.http.run(identity=699,head=self.binding['base']);old['conclusion']='failure'
        for order in ([old,current],[current,old]):
            with self.subTest(old_first=order[0]['id']==699):
                self.http.runs=copy.deepcopy(order);self.http.calls=[]
                result=self.command()
                self.assertEqual('verified-ci',result['action'],'HISTORICAL_ASSOCIATION_POISONED_EXACT_CURRENT_RUN')
                self.assertEqual((700,1),(result['run_id'],result['run_attempt']))
                self.assertIs(result['release_authority'],False)
                self.assertGreater(self.http.archive_reads(1),0,'EXACT_ATTEMPT_EVENT_BASE_RECEIPT_NOT_READ')
                self.assertFalse(any('/runs/699/' in url for _,url in self.http.calls))
                self.no_write()

    def test_old_run_with_current_looking_association_alone_cannot_satisfy_ci(self):
        self.http.runs=[self.http.run(identity=699,head=self.binding['base'])]
        self.assertEqual('hold',self.command()['action'])
        self.assertEqual(0,self.http.archive_reads(1));self.no_write()

    def test_current_exact_run_still_requires_its_exact_native_association(self):
        for mutation in ('head','head-ref','base','foreign','missing','duplicate'):
            with self.subTest(mutation=mutation):
                row=self.http.run();association=row['pull_requests'][0]
                if mutation=='head':association['head']['sha']=self.binding['base']
                elif mutation=='head-ref':association['head']['ref']='codex/foreign'
                elif mutation=='base':association['base']['sha']=self.binding['head']
                elif mutation=='foreign':association['head']['repo']['id']=123
                elif mutation=='missing':row['pull_requests']=[]
                else:row['pull_requests'].append(copy.deepcopy(association))
                self.http.runs=[row]
                self.assertEqual('hold',self.command()['action']);self.no_write()

    def test_incomplete_native_inventory_is_not_a_verified_current_run(self):
        self.http.total=2
        self.assertEqual('hold',self.command()['action'])
        self.assertEqual(0,self.http.archive_reads(1));self.no_write()

    def test_unchanged_head_current_association_cannot_replace_stale_event_time_base(self):
        stale=self.http.report();stale['base']='e'*40
        self.http.artifacts=[self.http.artifact(report=stale)]
        self.assertEqual('hold',self.command()['action'],'MUTABLE_ASSOCIATION_REPLACED_IMMUTABLE_EVENT_BASE')
        self.no_write()

    def test_missing_unreadable_or_bad_exact_attempt_receipt_holds(self):
        for mutation in ('missing','unreadable','attempt','run','head','expired','failed-report','malformed-zip','foreign-artifact','digest'):
            with self.subTest(mutation=mutation):
                self.http.unreadable=set();report=self.http.report()
                if mutation=='attempt':report['attempt']='2'
                elif mutation=='run':report['run']='699'
                elif mutation=='head':report['head']=self.binding['base']
                elif mutation=='failed-report':report['failures']=[dict(name='synthetic',detail='failed')]
                self.http.artifacts=[self.http.artifact(report=report)]
                if mutation=='missing':self.http.artifacts=[]
                elif mutation=='unreadable':self.http.unreadable={'actions/artifacts/97001/zip'}
                elif mutation=='expired':self.http.artifacts[0]['expired']=True
                elif mutation=='malformed-zip':
                    self.http.archives[97001]=b'not a ZIP archive'
                    self.http.artifacts[0]['digest']='sha256:'+hashlib.sha256(self.http.archives[97001]).hexdigest()
                elif mutation=='foreign-artifact':self.http.artifacts[0]['workflow_run']['repository_id']=123
                elif mutation=='digest':self.http.artifacts[0]['digest']='sha256:'+'0'*64
                self.assertEqual('hold',self.command()['action'],'UNVERIFIED_EVENT_TIME_RECEIPT_WAS_ACCEPTED')
                self.no_write()

    def test_later_attempt_uses_same_run_exact_new_receipt_and_stale_rerun_does_not_send(self):
        self.http.runs=[self.http.run(attempt=2)]
        self.http.artifacts=[self.http.artifact(1),self.http.artifact(2)]
        result=self.command()
        self.assertEqual('verified-ci',result['action'])
        self.assertEqual((700,2),(result['run_id'],result['run_attempt']))
        self.assertGreater(self.http.archive_reads(2),0,'LATEST_ATTEMPT_RECEIPT_NOT_READ')
        self.assertEqual(0,self.http.archive_reads(1),'OLDER_ATTEMPT_ZIP_WAS_REUSED')
        self.assertEqual('hold',self.command('rerun-ci',run_id=700,run_attempt=1)['action'])
        self.no_write()

    def test_later_native_attempt_cannot_borrow_earlier_green_artifact(self):
        self.http.runs=[self.http.run(attempt=2)]
        self.http.artifacts=[self.http.artifact(1)]
        self.assertEqual('hold',self.command()['action'],'LATEST_ATTEMPT_BORROWED_OLD_GREEN_RECEIPT')
        self.no_write()

    def test_pending_later_attempt_cannot_fall_back_to_old_green(self):
        row=self.http.run(attempt=2);row.update(status='in_progress',conclusion=None)
        self.http.runs=[row];self.http.artifacts=[self.http.artifact(1)]
        self.assertEqual('hold',self.command()['action']);self.no_write()


if __name__=='__main__':unittest.main(verbosity=2)
