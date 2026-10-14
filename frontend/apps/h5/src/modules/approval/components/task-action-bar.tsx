import { useState } from 'react';
import { ActionBar, ActionButton, BottomSheet, ButtonRow, TextAreaField } from '@/shared/ui/actions';
import { REASON_RULES, type ReasonAction, type TaskActions, canConfirm } from '../model/task';

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

  const close = () => {
    setOpen(undefined);
    setReason('');
  };
  const confirm = () => {
    if (open === undefined) return;
    const trimmed = reason.trim();
    if (open === 'approve') onApprove(trimmed);
    if (open === 'reject') onReject(trimmed);
    if (open === 'return') onReturn(trimmed);
    close();
  };
  const rule = open === undefined ? undefined : REASON_RULES[open];

  return (
    <>
      <ActionBar>
        {actions.claim ? <ActionButton variant="default" loading={busy} onClick={onClaim}>认领</ActionButton> : null}
        {actions.approve ? <ActionButton disabled={busy} onClick={() => setOpen('approve')}>通过</ActionButton> : null}
        {actions.reject ? <ActionButton variant="danger" disabled={busy} onClick={() => setOpen('reject')}>驳回</ActionButton> : null}
        {actions.returnToInitiator ? <ActionButton variant="default" disabled={busy} onClick={() => setOpen('return')}>退回</ActionButton> : null}
      </ActionBar>
      <BottomSheet open={rule !== undefined} title={rule?.title ?? ''} onClose={close}>
        <TextAreaField label={rule?.required === true ? '理由（必填）' : '理由（选填）'} value={reason} onChange={setReason} />
        <ButtonRow>
          <ActionButton variant="default" onClick={close}>取消</ActionButton>
          <ActionButton
            variant={rule?.danger === true ? 'danger' : 'primary'}
            disabled={open === undefined || !canConfirm(open, reason)}
            onClick={confirm}
          >
            {rule?.confirm ?? ''}
          </ActionButton>
        </ButtonRow>
      </BottomSheet>
    </>
  );
}
