"""Broker-internal branch effect binding for its existing store and execution gate.

No public route, no separate ledger, and no default authorization to send.
The installer supplies one independently approved policy and the existing
Broker owns the caller, source and tool binding before invoking this module.
"""
import base64
import hashlib
import json
import os
from pathlib import Path
import secrets
import re
import stat

from branch_effect_port import BranchEffect, need

KIND='git-push-cas'
def wire(o):
    return json.dumps(o,sort_keys=True,separators=(',',':'),allow_nan=False)

def sha(raw):
    return hashlib.sha256(raw).hexdigest()

class BranchBinding:
    def __init__(self,broker,approved,approved_sha256,receipt_dir):
        need(sha(wire(approved).encode())==approved_sha256,'independent-policy-pin-required')
        self.broker=broker
        self.store=broker.store
        self.approved=approved
        self.pin=approved_sha256
        self.port=BranchEffect(approved['subject'],approved['transport'])
        self.receipts=Path(receipt_dir)
        i=self.receipts.lstat()
        need(self.receipts.resolve(strict=True)==self.receipts and stat.S_ISDIR(i.st_mode)
             and i.st_uid==os.geteuid() and stat.S_IMODE(i.st_mode)==0o700,'private-receipt-directory-required')

    def _authorized(self,db,auth,revision,paused_ok=False):
        owner,task=self.store.binding(db,auth,revision,paused_ok=paused_ok)
        current=json.loads(task['body']); s=self.port.s
        need(current['card']==s['card'] and current['H']==s['head']
             and current['session_id']==self.approved['session_id']
             and revision==self.approved['revision'] and current['B']==self.approved['base']
             and current['A']==self.approved['acceptance'], 'branch-effect-assignment-drift')
        return owner,task

    def prepare(self,auth,revision):
        key=wire(self.port.semantic_key())
        target=wire(dict(subject=self.port.s,authorization_sha256=self.pin))
        with self.broker.execution_gate,self.store.tx() as db:
            owner,task=self._authorized(db,auth,revision)
            prior=db.execute('select * from effects where operation_key=?',(key,)).fetchone()
            if prior:
                need(prior['kind']==KIND and prior['target']==target,'branch-preimage-already-bound')
                return dict(effect_id=prior['effect_id'],phase=prior['phase'],reused=True)
            effect_id=secrets.token_hex(24)
            db.execute("insert into effects(effect_id,revision,generation,operation_key,kind,target,phase) values(?,?,?,?,?,?,'planned')",
                       (effect_id,task['revision'],owner['generation'],key,KIND,target))
            self.store.event(db,'branch-effect-planned',dict(effect_id=effect_id,revision=revision,policy_sha256=self.pin))
            return dict(effect_id=effect_id,phase='planned',reused=False)

    def _read(self,db,effect_id):
        row=db.execute('select * from effects where effect_id=?',(effect_id,)).fetchone()
        need(row and row['kind']==KIND and row['operation_key']==wire(self.port.semantic_key())
             and json.loads(row['target'])==dict(subject=self.port.s,authorization_sha256=self.pin),
             'inexact-branch-effect')
        return dict(row)

    def _record(self,record):
        effect_id=record['effect_id']
        need(re.fullmatch('[a-f0-9]{48}',effect_id),'invalid-receipt-effect-id')
        raw=(wire(dict(effect_id=effect_id,owner_generation=record['generation'],revision=record['revision'],
                       subject=self.port.s,policy_sha256=self.pin,argv=record['argv'],exit_code=record['exit_code'],
                       stdout_base64=base64.b64encode(record['stdout']).decode(),
                       stderr_base64=base64.b64encode(record['stderr']).decode()))+'\n').encode()
        path=self.receipts/(effect_id+'.json')
        fd=os.open(path,os.O_CREAT|os.O_EXCL|os.O_WRONLY|os.O_NOFOLLOW,0o600)
        with os.fdopen(fd,'wb') as stream:
            stream.write(raw);stream.flush();os.fsync(stream.fileno())
        fd=os.open(self.receipts,os.O_RDONLY|os.O_DIRECTORY)
        try:os.fsync(fd)
        finally:os.close(fd)
        return sha(raw)

    def issue_internal(self,auth,revision,effect_id):
        # Broker invokes only after independently proving actual tool/caller
        # scope and approving this exact real effect. Nothing here grants that.
        with self.broker.execution_gate:
            with self.store.tx() as db:
                owner,task=self._authorized(db,auth,revision)
                row=self._read(db,effect_id)
                need(row['revision']==revision and row['generation']==owner['generation']
                     and row['phase']=='planned' and row['issue_count']==0,'already-consumed-readback-only')
                self.port.preflight()
                changed=db.execute("update effects set phase='unknown',issue_count=1 where effect_id=? and phase='planned' and issue_count=0",
                                   (effect_id,))
                need(changed.rowcount==1,'branch-consumption-race')
                self.store.event(db,'branch-effect-consumed-before-spawn',dict(effect_id=effect_id,revision=revision,
                                generation=owner['generation'],policy_sha256=self.pin))
            # Observe committed state; never turn a pre-update dict into authority.
            with self.store.tx() as db:
                committed=self._read(db,effect_id)
                need(committed['phase']=='unknown' and committed['issue_count']==1,'consumption-not-durable')
            reservation=dict(effect_id=effect_id,phase=committed['phase'],issue_count=committed['issue_count'],
                             semantic_key=self.port.semantic_key(),subject=self.port.s)
            def record_private(**raw):
                return self._record(dict(raw,generation=committed['generation'],revision=committed['revision']))
            result=self.port.issue_after_consumed_reservation(reservation,record_private)
            with self.store.tx() as db:
                self._read(db,effect_id)
                db.execute('update effects set observation=? where effect_id=?',(wire(result),effect_id))
                self.store.event(db,'branch-transport-receipt-recorded',dict(effect_id=effect_id,receipt=result))
            return dict(effect_id=effect_id,phase='unknown',private_receipt_sha256=result['private_receipt_sha256'])

    def observe_readonly(self,auth,revision,effect_id):
        with self.broker.execution_gate:
            with self.store.tx() as db:
                self.store.owner(db,auth)
                row=self._read(db,effect_id)
                need(row['revision']==revision==self.approved['revision'],'inexact-recovery-revision')
            actual=self.port.observe()
            # Neither a ref match nor this candidate's digest is independent
            # attribution. The independent verifier owns the final transition.
            return dict(effect_id=effect_id,phase=row['phase'],actual=actual,requires_independent_attribution=True)
