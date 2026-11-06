import { badgeOf, formatDateTime } from '@forcitis/core';
import { Button, DescriptionList, PageTitle, ReasonDialog, StatusBadge } from '@forcitis/ui';
import { MutationNotice, QueryBoundary } from '../../../shared/query-boundary';
import { useState } from 'react';
import { useParams } from 'react-router';
import { RouterLink } from '../../../shared/router-link';
import { usePersonnelCommand, useWithdrawPersonnelCommand } from '../hooks/use-workforce';
import { employeePath } from '../model/employee';
import { COMMAND_KIND_LABELS, COMMAND_STATUS_LABELS, COMMAND_STATUS_TONES, HOLD_STATUS_LABELS } from '../model/personnel-command';

export function PersonnelCommandDetailPage() {
  const { personnelCommandId = '' } = useParams();
  const command = usePersonnelCommand(personnelCommandId);
  const withdraw = useWithdrawPersonnelCommand(personnelCommandId);
  const [asking, setAsking] = useState(false);

  return (
    <QueryBoundary query={command} errorTitle="人事命令加载失败">
      {(detail) => {
        const close = () => {
          setAsking(false);
          withdraw.reset();
        };
        const confirm = (reason: string) => withdraw.mutate(reason, { onSuccess: close });

        return (
          <>
            <PageTitle
              text={`${COMMAND_KIND_LABELS[detail.commandKind] ?? detail.commandKind} · ${detail.displayName}`}
              actions={
                <>
                  <StatusBadge tone={badgeOf(COMMAND_STATUS_LABELS, COMMAND_STATUS_TONES, detail.status).tone}>{badgeOf(COMMAND_STATUS_LABELS, COMMAND_STATUS_TONES, detail.status).label}</StatusBadge>
                  {detail.withdrawnAt == null ? <Button variant="danger" onClick={() => setAsking(true)}>撤回</Button> : null}
                </>
              }
            />
            <DescriptionList
              items={[
                { label: '员工', value: <RouterLink to={employeePath(detail.employeeId)}>{detail.displayName}</RouterLink> },
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
            <ReasonDialog open={asking} title="撤回人事命令" confirmText="确认撤回" label="撤回原因" required danger loading={withdraw.isPending} error={<MutationNotice mutation={withdraw} title="撤回失败" />} onConfirm={confirm} onClose={close} />
          </>
        );
      }}
    </QueryBoundary>
  );
}
