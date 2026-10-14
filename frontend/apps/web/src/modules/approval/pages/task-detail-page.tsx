import { DescriptionList, LoadingState, Notice, PageTitle, StatusBadge } from '@forcitis/ui';
import { useNavigate, useParams } from 'react-router';
import { useCurrentUser } from '@/modules/session';
import { describeFailure } from '@/shared/lib/failure';
import { formatDateTime } from '@/shared/lib/format-date-time';
import { TaskActionBar } from '../components/task-action-bar';
import { useTask, useTaskActions } from '../hooks/use-task';
import { APPROVAL_ERROR_MESSAGES, PROCESS_LABELS, TASK_APPROVE_PERMISSION, availableActions } from '../model/task';

export function TaskDetailPage() {
  const { taskId = '' } = useParams();
  const navigate = useNavigate();
  const session = useCurrentUser();
  const task = useTask(taskId);
  const actions = useTaskActions(taskId, () => void navigate('/inbox'));

  if (task.isPending) return <LoadingState />;
  if (task.isError) return <Notice tone="error" title="审批任务加载失败">{describeFailure(task.error, APPROVAL_ERROR_MESSAGES)}</Notice>;

  const detail = task.data;
  const failed = [actions.claim, actions.approve, actions.reject, actions.sendBack].find((mutation) => mutation.isError);
  const busy = [actions.claim, actions.approve, actions.reject, actions.sendBack].some((mutation) => mutation.isPending);

  return (
    <>
      <PageTitle text="审批任务" actions={<StatusBadge tone={detail.assignee ? 'info' : 'pending'}>{detail.assignee ? '已认领' : '待认领'}</StatusBadge>} />
      {failed === undefined ? null : <Notice tone="error" title="操作失败">{describeFailure(failed.error, APPROVAL_ERROR_MESSAGES)}</Notice>}
      <DescriptionList
        items={[
          { label: '审批流程', value: PROCESS_LABELS[detail.processKey ?? ''] ?? detail.processKey ?? '—' },
          { label: '单据', value: `${detail.documentType} ${detail.documentId}` },
          { label: '当前节点', value: detail.nodeName ?? detail.nodeKey ?? '—' },
          { label: '发起人', value: detail.initiator ?? '—' },
          { label: '处理人', value: detail.assignee ?? '未认领' },
          { label: '候选审批人', value: (detail.candidates ?? []).length === 0 ? '—' : (detail.candidates ?? []).join('、') },
          { label: '创建时间', value: formatDateTime(detail.createdAt) },
        ]}
      />
      <TaskActionBar
        actions={availableActions(detail, session.can(TASK_APPROVE_PERMISSION))}
        busy={busy}
        onClaim={() => actions.claim.mutate()}
        onApprove={(reason) => actions.approve.mutate(reason)}
        onReject={(reason) => actions.reject.mutate(reason)}
        onReturn={(reason) => actions.sendBack.mutate(reason)}
      />
    </>
  );
}
