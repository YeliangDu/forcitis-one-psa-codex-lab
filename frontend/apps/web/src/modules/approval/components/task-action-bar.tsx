import { REASON_RULES, type ReasonAction, type TaskActions, opinionText } from '@forcitis/core';
import { Button, ReasonDialog } from '@forcitis/ui';
import { useState } from 'react';
import './task-action-bar.css';

interface TaskActionBarProps {
  actions: TaskActions;
  busy: boolean;
  onClaim: () => void;
  onApprove: (reason: string) => void;
  onReject: (reason: string) => void;
  onReturn: (reason: string) => void;
}

export function TaskActionBar({ actions, busy, onClaim, onApprove, onReject, onReturn }: TaskActionBarProps) {
  const [open, setOpen] = useState<ReasonAction>();
  const rule = open === undefined ? undefined : REASON_RULES[open];

  const close = () => setOpen(undefined);
  const confirm = (reason: string) => {
    if (open === 'approve') onApprove(reason);
    if (open === 'reject') onReject(reason);
    if (open === 'return') onReturn(reason);
    close();
  };

  return (
    <div className="task-action-bar">
      {actions.claim ? <Button variant="default" loading={busy} onClick={onClaim}>认领</Button> : null}
      {actions.approve ? <Button disabled={busy} onClick={() => setOpen('approve')}>通过</Button> : null}
      {actions.reject ? <Button variant="danger" disabled={busy} onClick={() => setOpen('reject')}>驳回</Button> : null}
      {actions.returnToInitiator ? <Button variant="default" disabled={busy} onClick={() => setOpen('return')}>退回</Button> : null}
      <ReasonDialog
        open={rule !== undefined}
        title={rule?.title ?? ''}
        confirmText={rule?.confirm ?? ''}
        label={rule?.required === true ? '理由（必填）' : '理由（选填）'}
        required={rule?.required === true}
        danger={rule?.danger ?? false}
        below={(reason) => (open === 'approve' ? <p>意见：{opinionText(reason)}</p> : null)}
        onConfirm={confirm}
        onClose={close}
      />
    </div>
  );
}
