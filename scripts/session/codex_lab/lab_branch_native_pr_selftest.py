"""#45 real NativeGithub HTTP shape -> shared dispatcher, disposable Git only."""
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import urllib.parse
import unittest
from unittest.mock import patch

HERE=Path(__file__).resolve().parent
CORE_SHA='99122154e3ce796c23e16221e83804df63dcf137ad2c998cfeac4691a40fea8e'
REPO='YeliangDu/forcitis-one-psa-codex-lab'
RID=1449415666
API='https://api.github.com/repos/'+REPO


def module(name,path):
    spec=importlib.util.spec_from_file_location(name,path)
    value=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


class HttpResponse:
    def __init__(self,value,link=''):
        self.status=200;self.headers={'Link':link}
        self.raw=json.dumps(value,ensure_ascii=False).encode()
    def read(self,limit):return self.raw[:limit]
    def __enter__(self):return self
    def __exit__(self,*args):return None


class HttpFixture:
    def __init__(self,test,summary,detail):
        self.test=test;self.rows=[copy.deepcopy(summary)];self.detail=copy.deepcopy(detail)
        self.calls=[];self.mutations=[];self.link='';self.detail_failure=False;self.second_page_failure=False

    def open(self,request,timeout):
        method=request.get_method();url=request.full_url
        self.calls.append((method,url))
        if method!='GET':
            self.mutations.append((method,url))
            raise AssertionError('NATIVE_PR_TEST_FORBIDS_ALL_HTTP_MUTATIONS')
        self.test.assertTrue(url==API or url.startswith(API+'/'),'HTTP_OUTSIDE_FIXED_LAB')
        relative=url.removeprefix(API).lstrip('/')
        if relative=='':return HttpResponse(dict(id=RID,full_name=REPO,private=False))
        if relative.startswith('pulls?'):
            query=urllib.parse.parse_qs(relative.split('?',1)[1])
            self.test.assertEqual(['all'],query.get('state'))
            self.test.assertEqual(['100'],query.get('per_page'))
            page=query.get('page')
            if page==['1']:return HttpResponse(self.rows,self.link)
            if page==['2'] and self.second_page_failure:raise OSError('synthetic native next page unavailable')
            raise AssertionError('UNEXPECTED_NATIVE_PAGE: '+relative)
        if relative=='pulls/46':
            if self.detail_failure:raise OSError('synthetic native PR detail unavailable')
            return HttpResponse(self.detail)
        raise AssertionError('UNEXPECTED_NATIVE_HTTP_READ: '+relative)

    def detail_reads(self):return sum(url==API+'/pulls/46' for _,url in self.calls)


class NativePrShapeAcceptanceTest(unittest.TestCase):
    def setUp(self):
        core_source=HERE/'lab_branch_lifecycle_selftest.py'
        self.assertEqual(CORE_SHA,hashlib.sha256(core_source.read_bytes()).hexdigest(),'ORIGINAL_EIGHT_BYTES_CHANGED')
        core_module=module('lab45_native_pr_frozen_fixture',core_source)
        self.core=core_module.BranchLifecycleAcceptanceTest(methodName='runTest')
        self.core.setUp();self.addCleanup(self.core.doCleanups)
        self.core.dispatch();self.core.commit_publish('synthetic native-list candidate\n')
        self.binding=copy.deepcopy(self.core.binding)
        self.detail=dict(id=96046,number=46,state='open',draft=True,merged=False,merged_at=None,
            body='Closes #45\n\nSynthetic native-list fixture.',
            head=dict(ref=self.binding['branch'],sha=self.binding['head'],repo=dict(id=RID,full_name=REPO,private=False)),
            base=dict(ref='main',sha=self.binding['base'],repo=dict(id=RID,full_name=REPO,private=False)))
        self.summary=copy.deepcopy(self.detail);del self.summary['merged']
        # Real GitHub pulls-list shape has merged_at but DOES NOT claim merged.
        self.assertIn('merged_at',self.summary);self.assertNotIn('merged',self.summary)
        native_module=module('lab45_native_pr_adapter',HERE/'lab_branch_native.py')
        credential_guard=patch.dict(os.environ,{'GH_TOKEN':'','GITHUB_TOKEN':''})
        credential_guard.start();self.addCleanup(credential_guard.stop)
        self.native=native_module.NativeGithub({},dict(allowed_actions=['ensure-pr'],
            pr_title='ci(lab): 合成唯一原生 PR（#45）',session_id='11111111-1111-4111-8111-111111111111'),False)
        self.http=HttpFixture(self,self.summary,self.detail)
        self.native.opener=self.http # Only transport replacement; api/pages/find/pull stay REAL.
        self.create_attempts=0
        original=self.native.create_pull
        def count_create(binding):
            self.create_attempts+=1
            return original(binding)
        self.native.create_pull=count_create
        self.before_git=len(self.core.commands)

    def ensure(self):
        return self.core.live.run_branch_command('ensure-pr',dict(binding=copy.deepcopy(self.binding)),
                                                self.core.state,self.core.git,self.native)

    def no_effects(self):
        self.assertEqual(0,self.create_attempts,'UNVERIFIED_NATIVE_SEARCH_ATTEMPTED_PR_CREATE')
        self.assertEqual([],self.http.mutations)
        self.assertEqual([],self.core.deletes)
        self.assertEqual(self.binding['head'],self.core.ref('refs/heads/'+self.binding['branch']))
        self.assertTrue(all(method=='GET' and (url==API or url.startswith(API+'/')) for method,url in self.http.calls),
                        'NATIVE_HTTP_FOREIGN_OR_WRITE_ATTEMPT')
        self.assertFalse(any(args[0] in ('push','checkout','merge') or
                             (args[0]=='branch' and args!=['branch','--show-current'])
                             for args in self.core.commands[self.before_git:]),
                         'PR_LOOKUP_MUTATED_GIT')

    def test_unique_native_list_without_merged_reads_detail_and_reuses_existing_draft_pr(self):
        result=self.ensure()
        self.assertEqual('bound-pr',result['action'],'NATIVE_LIST_REQUIRES_DETAIL_BEFORE_STRICT_PR_REUSE')
        self.assertEqual(46,result['pr_number'])
        self.assertEqual(1,self.http.detail_reads(),'UNIQUE_NATIVE_CANDIDATE_DETAIL_NOT_READ')
        self.assertNotIn('merged',self.http.rows[0],'LIST_MERGED_MUST_NOT_BE_SYNTHESIZED')
        again=self.ensure()
        self.assertEqual(46,again['pr_number'])
        self.assertEqual(2,self.http.detail_reads())
        self.assertEqual(1,sum('/pulls?' in url for _,url in self.http.calls))
        self.no_effects()

    def test_detail_without_merged_cannot_be_defaulted_from_list_merged_at(self):
        del self.http.detail['merged']
        result=self.ensure()
        self.assertEqual('hold',result['action'],'DETAIL_MISSING_MERGED_WAS_DEFAULTED')
        self.assertEqual(1,self.http.detail_reads(),'UNIQUE_NATIVE_CANDIDATE_DETAIL_NOT_READ')
        self.no_effects()

    def test_incomplete_duplicate_or_foreign_page_link_holds_before_any_detail_or_create(self):
        original=copy.deepcopy(self.http.rows)
        for mutation in ('incomplete','duplicate','foreign-next'):
            with self.subTest(mutation=mutation):
                self.http.rows=copy.deepcopy(original);self.http.calls=[];self.http.link='';self.http.second_page_failure=False
                if mutation=='duplicate':self.http.rows.append(dict(copy.deepcopy(original[0]),id=96047,number=47))
                elif mutation=='incomplete':
                    self.http.link='<'+API+'/pulls?state=all&per_page=100&page=2>; rel="next"'
                    self.http.second_page_failure=True
                else:self.http.link='<https://foreign.invalid/repos/other/pulls?state=all&per_page=100&page=2>; rel="next"'
                self.assertEqual('hold',self.ensure()['action'],'NONUNIQUE_OR_INCOMPLETE_NATIVE_SEARCH_WAS_ACCEPTED')
                self.assertEqual(0,self.http.detail_reads(),'AMBIGUOUS_SEARCH_MUST_NOT_CHOOSE_A_DETAIL')
                self.no_effects()

    def test_moved_or_foreign_detail_cannot_bind_stale_list_identity(self):
        for mutation in ('repo','branch','head','base','number'):
            with self.subTest(mutation=mutation):
                self.http.detail=copy.deepcopy(self.detail);self.http.calls=[]
                if mutation=='repo':self.http.detail['head']['repo']['id']=123
                elif mutation=='branch':self.http.detail['head']['ref']='codex/foreign-branch'
                elif mutation=='head':self.http.detail['head']['sha']=self.binding['base']
                elif mutation=='base':self.http.detail['base']['sha']=self.binding['head']
                else:self.http.detail['number']=47
                self.assertEqual('hold',self.ensure()['action'],'NATIVE_DETAIL_IDENTITY_MOVED_BUT_BOUND')
                self.assertEqual(1,self.http.detail_reads(),'UNIQUE_NATIVE_CANDIDATE_DETAIL_NOT_READ')
                self.no_effects()

    def test_unreadable_unique_detail_is_hold_not_create_or_implicit_unmerged(self):
        self.http.detail_failure=True
        result=self.ensure()
        self.assertEqual('hold',result['action'])
        self.assertEqual(1,self.http.detail_reads(),'UNIQUE_NATIVE_CANDIDATE_DETAIL_NOT_READ')
        self.no_effects()

    def test_foreign_or_missing_list_repository_is_not_lookup_or_create_permission(self):
        for mutation in ('foreign-id','missing-repo'):
            with self.subTest(mutation=mutation):
                self.http.rows=[copy.deepcopy(self.summary)];self.http.calls=[]
                if mutation=='foreign-id':self.http.rows[0]['head']['repo']['id']=123
                else:del self.http.rows[0]['head']['repo']
                self.assertEqual('hold',self.ensure()['action'])
                self.assertEqual(0,self.http.detail_reads())
                self.no_effects()


if __name__=='__main__':unittest.main(verbosity=2)
