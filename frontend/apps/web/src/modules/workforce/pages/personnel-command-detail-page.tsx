import { describeFailure, formatDateTime } from '@forcitis/core';
import { Button, Dialog, DescriptionList, LoadingState, Notice, PageTitle, StatusBadge, TextAreaField, TextLink } from '@forcitis/ui';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { usePersonnelCommand, useWithdrawPersonnelCommand } from '../hooks/use-workforce';
import { employeePath } from '../model/employee';
import { COMMAND_KIND_LABELS, COMMAND_STATUS_LABELS, HOLD_STATUS_LABELS } from '../model/personnel-command';

export function PersonnelCommandDetailPage() {
  const { personnelCommandId = '' } = useParams();
  const navigate = useNavigate();
  const command = usePersonnelCommand(personnelCommandId);
  const withdraw = useWithdrawPersonnelCommand(personnelCommandId);
  const [asking, setAsking] = useState(false);
  const [reason, setReason] = useState('');

  if (command.isPending) return <LoadingState />;
  if (command.isError) return <Notice tone="error" title="人事命令加载失败">{describeFailure(command.error)}</Notice>;

  const detail = command.data;
  const close = () => {
    setAsking(false);
    setReason('');
    withdraw.reset();
  };
  const confirm = () => withdraw.mutate(reason.trim(), { onSuccess: close });

  return (
    <>
      <PageTitle
        text={`${COMMAND_KIND_LABELS[detail.commandKind] ?? detail.commandKind} · ${detail.displayName}`}
        actions={
          <>
            <StatusBadge tone={detail.status === 'EFFECTIVE' ? 'posted' : 'neutral'}>{COMMAND_STATUS_LABELS[detail.status] ?? detail.status}</StatusBadge>
            {detail.withdrawnAt == null ? <Button variant="danger" onClick={() => setAsking(true)}>撤回</Button> : null}
          </>
        }
      />
      <DescriptionList
        items={[
          { label: '员工', value: <TextLink href={employeePath(detail.employeeId)} onNavigate={() => void navigate(employeePath(detail.employeeId))}>{detail.displayName}</TextLink> },
          { label: '员工编码', value: detail.employeeCode },
          { label: '命令类型', value: COMMAND_KIND_LABELS[detail.commandKind] ?? detail.commandKind },
          { label: '生效日期', value: detail.effectiveDate },
          { label: '最后工作日', value: detail.lastWorkingDay ?? '—' },
          { label: '放行状态', value: detail.holdStatus == null ? '—' : (HOLD_STATUS_LABELS[detail.holdStatus] ?? detail.holdStatus) },
          { label: '创建时间', value: formatDateTime(detail.createdAt) },
          { label: '撤回时间', value: detail.withdrawnAt == null ? '—' : formatDateTime(detail.withdrawnAt) },
          { label: '撤回原因', value: detail.withdrawalReason ?? '—' },
        ]}
      />
      <Dialog open={asking} title="撤回人事命令" confirmText="确认撤回" danger confirmLoading={withdraw.isPending} confirmDisabled={reason.trim() === ''} onConfirm={confirm} onClose={close}>
        <TextAreaField label="撤回原因" value={reason} onChange={setReason} />
        {withdraw.isError ? <Notice tone="error" title="撤回失败">{describeFailure(withdraw.error)}</Notice> : null}
      </Dialog>
    </>
  );
}
