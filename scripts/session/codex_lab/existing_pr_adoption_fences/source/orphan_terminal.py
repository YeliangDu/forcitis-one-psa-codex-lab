"""Exact terminal-only orphan archive, never normal retire/recovery or reply CAS."""
import json
from handoff_retire import RetirementStore, RetirementSupport
from runtime_store import need, wire, digest



class ArchiveStore(RetirementStore):
    def __init__(self, directory, installation):
        super().__init__(directory, installation)
        with self.tx() as db:
            pass  # Existing-only startup: schema was independently verified; no DDL.


class ArchiveSupport(RetirementSupport):
    def _archive_selection(self, db, auth, args):
        scope = self.private_authorization('orphan-archive-scope', expected_kind='orphan-terminal-archive-scope/1')
        need(scope['session_id'] == self.session_id and scope['broker_installation_sha256'] == self.cfg['pin'], 'orphan-private-scope-mismatch')
        need(args == dict(revision=scope['revision'], wait_id=scope['wait_id']), 'only-reviewed-exact-orphan-scope')
        owner = self.store.owner(db,auth)
        need(owner['paused'], 'owner-must-stay-paused')
        audit = db.execute('select * from orphan_handoff_archive where effect_id=?',(scope['effect_id'],)).fetchone()
        if audit:
            return None,None,None,json.loads(audit['evidence'])
        row,wait,original,retired = self._selection(db,auth,args)
        need(retired is None and row['effect_id']==scope['effect_id'] and wait['turn_id']==scope['turn_id'] and wait['call_id']==scope['call_id']
             and wait['session_id']==self.session_id and wait['phase']=='pending' and wait['completions']==0,
             'exact-original-orphan-not-unreplied-pending')
        history=self._history(db,row,original)
        effect=db.execute('select * from effects where effect_id=?',(scope['effect_id'],)).fetchone()
        need(effect and effect['kind']=='native-wait-owned-handoff' and effect['issue_count']==1,
             'only-isolated-handoff-reply-disposition-archiveable')
        # _selection already rejects ALL other unknown/live effects, waits and turns.
        return row,wait,dict(history=history,effect=dict(effect),original=original),None

    def _archive(self,auth,args,commit):
        scope = self.private_authorization('orphan-archive-scope', expected_kind='orphan-terminal-archive-scope/1')
        with self.store.tx() as db:
            row,wait,snapshot,prior=self._archive_selection(db,auth,args)
            if prior:return dict(action='orphan-archived',archive=prior,repeated_read_only=True)
        channel=self._terminal_observer(row)  # Fenced diagnostics only; no resume/start/interrupt.
        channel.check_endpoint()
        loaded_before=self._loaded_snapshot(channel)
        need(self.session_id not in loaded_before['data'],'orphan-thread-must-not-be-loaded')
        goal_before=self._get_goal(channel)
        need(type(goal_before) is dict and goal_before.get('threadId')==self.session_id
             and goal_before.get('status')=='paused','actual-native-goal-must-be-paused')
        result=channel.rpc('thread/read',dict(threadId=self.session_id,includeTurns=True))
        thread=result['thread'];turns=thread.get('turns')
        need(thread.get('id')==self.session_id and thread.get('cwd')==self.cfg['physical_cwd']
             and thread.get('status',{}).get('type')=='notLoaded' and type(turns) is list
             and all(t.get('status') in ('completed','interrupted','failed') for t in turns),
             'exact-no-live-native-thread-readback-required')
        target=[t for t in turns if t.get('id')==scope['turn_id']]
        need(len(target)==1 and target[0].get('status')=='completed','exact-revision10-positive-completed-proof-required')
        goal_after=self._get_goal(channel)
        need(type(goal_after) is dict and goal_after.get('threadId')==self.session_id
             and goal_after.get('status')=='paused'
             and all(goal_before.get(k)==goal_after.get(k) for k in ('objective','tokenBudget','createdAt')),
             'native-paused-goal-moved-during-readback')
        loaded_after=self._loaded_snapshot(channel)
        need(self.session_id not in loaded_after['data'],'orphan-became-loaded-during-readback')
        channel.check_endpoint()
        evidence=dict(effect_id=scope['effect_id'],revision=scope['revision'],wait_id=scope['wait_id'],turn_id=scope['turn_id'],call_id=scope['call_id'],
            original_handoff=snapshot,source_installation_sha256=self.cfg['pin'],
            native_readback_semantic_sha256=digest(wire(result)),
            loaded_before_semantic_sha256=digest(wire(loaded_before)),loaded_after_semantic_sha256=digest(wire(loaded_after)),
            goal_before_semantic_sha256=digest(wire(goal_before)),goal_after_semantic_sha256=digest(wire(goal_after)),
            terminal_archive_only=True,reply_restored=False,handoff_recovered=False,server_epoch_proved=False,
            original_handles_reconstructed=False,wait_completions=0)
        with self.store.tx() as db:
            current,current_wait,current_snapshot,prior=self._archive_selection(db,auth,args)
            need(prior is None and current==row and current_wait==wait and current_snapshot==snapshot,
                 'orphan-ledger-moved-during-native-readback')
            if not commit:
                self.store.event(db,'orphan-terminal-archive-readonly-check',evidence)
                return dict(action='hold',reason='explicit-archive-required',evidence=evidence)
            changed=db.execute("update waits set phase='obsolete' where wait_id=? and phase='pending' and completions=0",(scope['wait_id'],))
            need(changed.rowcount==1,'orphan-wait-cas-failed')
            fence=self.store._fence(db,'explicit-terminal-only-orphan-archive')
            need(fence and fence['revision']==scope['revision'],'must-durably-revoke-exact-original-assignment')
            db.execute("update turns set state='terminal' where turn_id=?",(scope['turn_id'],))
            changed=db.execute("update native_handoff set phase='orphan-archived' where effect_id=? and phase=?",
                               (scope['effect_id'],row['phase']))
            need(changed.rowcount==1,'orphan-handoff-disposition-cas-failed')
            seq=self.store.event(db,'terminal-only-orphan-archived-not-recovered',dict(evidence,fence=fence))
            db.execute('insert into orphan_handoff_archive values(?,?,?)',(scope['effect_id'],wire(dict(evidence,archive_seq=seq,fence=fence)),seq))
            # effects.phase/observation/issue_count are intentionally NOT changed.
        return dict(action='orphan-archived',archive_seq=seq,fence=fence,terminal_archive_only=True,
                    reply_restored=False,handoff_recovered=False,server_epoch_proved=False,
                    next_paused_assignment_only=True,independent_acceptance=False)

    def handle(self,request):
        with self.execution_gate:
            command=request.get('command');auth=request.get('auth');args=request.get('arguments',{})
            if command in ('wait.orphan-terminal-check','wait.orphan-terminal-archive'):
                return self._archive(auth,args,command=='wait.orphan-terminal-archive')
            with self.store.tx() as db:
                archived=db.execute('select 1 from orphan_handoff_archive').fetchone()
            if archived:
                if command=='assignment.publish':
                    with self.store.tx() as db:
                        owner=self.store.owner(db,auth)
                        need(owner['paused'],'archive-only-allows-paused-next-assignment')
                    return self.store.publish(auth,args['assignment'])
                if command in ('inspect','plan'):
                    return super().handle(request)
                return dict(action='hold',reason='orphan-archive-grants-no-execution-reply-or-effect-right')
            return super().handle(request)
