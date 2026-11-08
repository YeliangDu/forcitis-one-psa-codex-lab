"""P1 candidate-targeted checkpoint contract; entirely synthetic fixture data."""
import copy
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
B = '77e872e8ba9661d3294d3a9337c8e5c956b70641'
BASELINE_SHA = '165654142e3eade8518404715b9116bf76e715fc0abdb8cddf1a61c81a3951c3'
CARD, REVISION = 62001, 7  # Synthetic case identity, not live state.
SESSION = '00000000-0000-4000-8000-000000006201'
TASK = 'CURRENT_SYNTHETIC_CHECKPOINT_TASK'
STALE = 'STALE_SYNTHETIC_OLD_TASK'


def digest(raw): return hashlib.sha256(raw).hexdigest()


class CapabilityHold(RuntimeError): pass


class CheckpointRecoveryAcceptance(unittest.TestCase):
    def setUp(self):
        if os.geteuid() == 0 or not sys.platform.startswith('linux'):
            raise CapabilityHold('non-root isolated hosted Linux required')
        self.parent_inode = int(os.environ['P1_PARENT_NETNS_INODE'])
        self.tmp = tempfile.TemporaryDirectory(prefix='p1-synthetic-')
        self.addCleanup(self.tmp.cleanup)
        self.lab = Path(self.tmp.name).resolve()/'forcitis-one-psa-codex-lab'
        self.lab.mkdir(mode=0o700)
        self.cwd = self.lab/'.codex-lab/fixture-current/work'
        self.cwd.mkdir(parents=True)
        self.other = self.lab/'other-physical-cwd'; self.other.mkdir()
        self.runtime = self.lab/'fixture-checkpoint'; self.runtime.mkdir(mode=0o700)
        self.path = self.runtime/'current.json'
        self.checkpoint = dict(kind='lab-p1-authorized-checkpoint/1',card=CARD,revision=REVISION,role='one-test',
            session_id=SESSION,lab_root=str(self.lab),cwd=str(self.cwd),base=B,task=TASK,next_action='synthetic-read-only-step')
        self.pin = self.write_checkpoint(self.checkpoint)
        self.dispatch = self.cwd.parent/'work-dispatch.json'
        self.dispatch.write_text(json.dumps(dict(role='one-test',role_kind='fixture',task=STALE)))
        self.source = Path(os.environ.get('P1_CLI_TARGET',str(ROOT/'baseline/hook.py'))).resolve(strict=True)
        self.source_pin = os.environ.get('P1_CLI_TARGET_SHA256',BASELINE_SHA)
        if digest(self.source.read_bytes()) != self.source_pin:
            raise CapabilityHold('selected actual target source differs from external pin')
        self.target = self.lab/'scripts/session/codex_lab/hook.py'; self.target.parent.mkdir(parents=True)
        shutil.copyfile(self.source,self.target)
        self.event = dict(hook_event_name='SessionStart',source='compact',cwd=str(self.cwd),session_id=SESSION)

    def write_checkpoint(self, value):
        raw = (json.dumps(value,sort_keys=True,separators=(',',':'))+'\n').encode()
        self.path.write_bytes(raw); self.path.chmod(0o600)
        return digest(raw)

    def snapshot(self):
        return {str(p.relative_to(self.lab)): ('dir' if p.is_dir() else digest(p.read_bytes()) if p.is_file() else 'other')
                for p in self.lab.rglob('*') if not p.is_symlink()} | {
                str(p.relative_to(self.lab)): 'symlink:'+os.readlink(p) for p in self.lab.rglob('*') if p.is_symlink()}

    def invoke(self, *, pin=None, checkpoint_path=None, actual_cwd=None, event=None):
        before = self.snapshot()
        args = ['--checkpoint',str(checkpoint_path or self.path),'--checkpoint-sha256',self.pin if pin is None else pin,
                '--expected-card',str(CARD),'--expected-revision',str(REVISION),'--expected-role','one-test',
                '--expected-session',SESSION,'--expected-cwd',str(self.cwd),'--lab-root',str(self.lab),'--base',B]
        env = dict(PATH=str(Path(sys.executable).parent)+':/usr/bin:/bin',TZ='UTC',PYTHONDONTWRITEBYTECODE='1')
        result = subprocess.run([sys.executable,'-I','-S','-B',str(ROOT/'cli_worker.py'),str(self.lab),str(self.target),
            self.source_pin,str(self.parent_inode),*args], input=json.dumps(event or self.event),text=True,
            env=env,cwd=actual_cwd or self.cwd,capture_output=True,timeout=10)
        if result.returncode == 86:
            raise CapabilityHold(result.stderr)
        self.assertEqual(before,self.snapshot(),'P1_READ_ONLY_FIXTURE_OR_CANONICAL_SIDE_EFFECT')
        self.assertFalse((self.lab/'.git').exists())
        if result.returncode not in (0,78):
            raise CapabilityHold('actual CLI failed before a usable result: '+result.stderr)
        self.assertLessEqual(len(result.stdout.encode('utf-8')),4096,'P1_CLI_OUTPUT_NOT_BOUNDED')
        if not result.stdout.strip(): return ''  # A successful silent CLI cannot satisfy the positive.
        try: output=json.loads(result.stdout)
        except ValueError: self.fail('P1_ACTUAL_CLI_RETURNED_INVALID_HOOK_JSON')
        self.assertIsInstance(output,dict)
        self.last_output=output
        if result.returncode==78:
            self.assertFalse(output.get('hookSpecificOutput',{}).get('additionalContext',''),'P1_HOLD_EXIT_RETURNED_TASK_CONTEXT')
        return output.get('hookSpecificOutput',{}).get('additionalContext','')

    def restored(self, source):
        self.event['source']=source
        context=self.invoke()
        self.assertTrue(context,'P1_POSITIVE_SILENCE_IS_NOT_RECOVERY')
        self.assertIn(TASK,context,'P1_CURRENT_AUTHORIZED_CHECKPOINT_NOT_RESTORED')
        self.assertNotIn(STALE,context,'P1_STALE_TASK_MIXED_WITH_CURRENT')
        self.assertLessEqual(len(context.encode('utf-8')),2048,'P1_CONTEXT_NOT_BOUNDED')
        try: value=json.loads(context)
        except ValueError: self.fail('P1_CURRENT_CONTEXT_NOT_STRUCTURED')
        expected=dict(kind='lab-p1-restored-context/1',card=CARD,revision=REVISION,role='one-test',session_id=SESSION,
                      cwd=str(self.cwd),lab_root=str(self.lab),base=B,checkpoint_sha256=self.pin,task=TASK,next_action='synthetic-read-only-step')
        self.assertEqual(expected,value,'P1_CURRENT_IDENTITY_OR_CHECKPOINT_BINDING_MISMATCH')
        self.assertIs(int,type(value['card']))
        self.assertIs(int,type(value['revision']))
        self.assertNotIn(STALE,json.dumps(self.last_output),'P1_STALE_TASK_PRESENT_OUTSIDE_CURRENT_CONTEXT')

    def rejected(self, marker, **options):
        self.assertFalse(self.invoke(**options),marker)
        for forbidden in (STALE,TASK,'LATE_SYNTHETIC_OLD_OUTPUT'):
            self.assertNotIn(forbidden,json.dumps(getattr(self,'last_output',{})),marker)

    def test_current_authorized_compact_restores_exact_bounded_context(self): self.restored('compact')
    def test_current_authorized_resume_restores_exact_bounded_context(self): self.restored('resume')

    def test_current_checkpoint_restores_without_legacy_dispatch_silence_is_failure(self):
        self.dispatch.unlink()
        self.restored('compact')

    def test_missing_checkpoint_cannot_fall_back_to_old_task(self):
        self.path.unlink()
        self.rejected('P1_MISSING_CHECKPOINT_FELL_BACK_TO_STALE')

    def test_wrong_revision_with_valid_file_digest_is_refused(self):
        self.pin=self.write_checkpoint(dict(self.checkpoint,revision=REVISION-1))
        self.rejected('P1_WRONG_REVISION_CONTEXT_ACCEPTED')

    def test_wrong_external_checkpoint_digest_is_refused(self):
        self.rejected('P1_WRONG_CHECKPOINT_DIGEST_ACCEPTED',pin='0'*64)

    def test_wrong_checkpoint_cwd_is_refused(self):
        self.pin=self.write_checkpoint(dict(self.checkpoint,cwd=str(self.other)))
        self.rejected('P1_WRONG_CHECKPOINT_CWD_ACCEPTED')

    def test_wrong_actual_physical_cwd_is_refused(self):
        self.rejected('P1_WRONG_ACTUAL_CWD_ACCEPTED',actual_cwd=self.other)

    def test_symlink_checkpoint_is_not_physical_authority(self):
        link=self.runtime/'alias.json';link.symlink_to(self.path)
        self.rejected('P1_SYMLINK_CHECKPOINT_ACCEPTED',checkpoint_path=link)

    def test_late_old_checkpoint_overwrite_cannot_reuse_current_pin(self):
        self.write_checkpoint(dict(self.checkpoint,revision=REVISION-1,task='LATE_SYNTHETIC_OLD_OUTPUT'))
        self.rejected('P1_LATE_OUTPUT_ACCEPTED_AS_CURRENT')

    def test_oversized_task_checkpoint_is_not_silently_truncated_or_injected(self):
        self.pin=self.write_checkpoint(dict(self.checkpoint,task=TASK*600))
        self.rejected('P1_OVERSIZED_CHECKPOINT_CONTEXT_ACCEPTED')

    def test_non_session_start_event_does_not_restore_task(self):
        event=dict(self.event,hook_event_name='Stop')
        self.rejected('P1_WRONG_HOOK_EVENT_RESTORED_TASK',event=event)
