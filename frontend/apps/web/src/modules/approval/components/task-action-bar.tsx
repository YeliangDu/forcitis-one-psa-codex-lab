import { Button, Dialog, TextAreaField } from '@forcitis/ui';
import { useState } from 'react';
import { type TaskActions } from '../model/task';
import './task-action-bar.css';

type ReasonAction = 'approve' | 'reject' | 'return';

const DIALOGS: Record<ReasonAction, { title: string; confirm: string; required: boolean; danger: boolean }> = {
  approve: { title: '通过', confirm: '确认通过', required: false, danger: false },
  reject: { title: '驳回', confirm: '确认驳回', required: true, danger: true },
  return: { title: '退回给发起人', confirm: '确认退回', required: true, danger: false },
};

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
  const [reason, setReason] = useState('');
  const dialog = open === undefined ? undefined : DIALOGS[open];

  const close = () => {
    setOpen(undefined);
    setReason('');
  };
  const confirm = () => {
    if (open === 'approve') onApprove(reason.trim());
    if (open === 'reject') onReject(reason.trim());
    if (open === 'return') onReturn(reason.trim());
    close();
  };

  return (
    <div className="task-action-bar">
      {actions.claim ? <Button variant="default" loading={busy} onClick={onClaim}>认领</Button> : null}
      {actions.approve ? <Button disabled={busy} onClick={() => setOpen('approve')}>通过</Button> : null}
      {actions.reject ? <Button variant="danger" disabled={busy} onClick={() => setOpen('reject')}>驳回</Button> : null}
      {actions.returnToInitiator ? <Button variant="default" disabled={busy} onClick={() => setOpen('return')}>退回</Button> : null}
      <Dialog
        open={dialog !== undefined}
        title={dialog?.title ?? ''}
        confirmText={dialog?.confirm ?? ''}
        danger={dialog?.danger ?? false}
        confirmDisabled={(dialog?.required ?? false) && reason.trim() === ''}
        onConfirm={confirm}
        onClose={close}
      >
        <TextAreaField label={dialog?.required === true ? '理由（必填）' : '理由（选填）'} value={reason} onChange={setReason} />
      </Dialog>
    </div>
  );
}
