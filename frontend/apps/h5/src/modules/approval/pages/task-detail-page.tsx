import { describeFailure } from '@/shared/lib/failure';
import { formatDateTime } from '@/shared/lib/format-date-time';
import { replacePage, useRouteParam } from '@/shared/platform/navigation';
import { LoadingHint, Notice, Tag } from '@/shared/ui/feedback';
import { FieldList, Screen, ScreenTitle } from '@/shared/ui/screen';
import { useCurrentUser } from '@/modules/session';
import { TaskActionBar } from '../components/task-action-bar';
import { useTask, useTaskActions } from '../hooks/use-task';
import { INBOX_PAGE } from '../model/inbox';
import { APPROVAL_ERROR_MESSAGES, PROCESS_LABELS, TASK_APPROVE_PERMISSION, availableActions } from '../model/task';

export function TaskDetailPage() {
  const taskId = useRouteParam('taskId') ?? '';
  const session = useCurrentUser();
  const task = useTask(taskId);
  const actions = useTaskActions(taskId, () => replacePage(INBOX_PAGE));

  if (taskId === '') return <Screen><Notice tone="error" title="审批任务加载失败">{APPROVAL_ERROR_MESSAGES['PLATFORM_APPROVAL_TASK_NOT_FOUND']}</Notice></Screen>;
  if (task.isPending) return <Screen><LoadingHint label="加载中" /></Screen>;
  if (task.isError) return <Screen><Notice tone="error" title="审批任务加载失败">{describeFailure(task.error, APPROVAL_ERROR_MESSAGES)}</Notice></Screen>;

  const detail = task.data;
  const mutations = [actions.claim, actions.approve, actions.reject, actions.sendBack];
  const failed = mutations.find((mutation) => mutation.isError);
  const busy = mutations.some((mutation) => mutation.isPending);
  const candidates = detail.candidates ?? [];

  return (
    <Screen>
      <ScreenTitle text="审批任务" aside={<Tag tone={detail.assignee === undefined ? 'pending' : 'info'}>{detail.assignee === undefined ? '待认领' : '已认领'}</Tag>} />
      {failed === undefined ? null : <Notice tone="error" title="操作失败">{describeFailure(failed.error, APPROVAL_ERROR_MESSAGES)}</Notice>}
      <FieldList
        rows={[
          { label: '审批流程', value: PROCESS_LABELS[detail.processKey ?? ''] ?? detail.processKey ?? '—' },
          { label: '单据', value: `${detail.documentType} ${detail.documentId}` },
          { label: '当前节点', value: detail.nodeName ?? detail.nodeKey ?? '—' },
          { label: '发起人', value: detail.initiator ?? '—' },
          { label: '处理人', value: detail.assignee ?? '未认领' },
          { label: '候选审批人', value: candidates.length === 0 ? '—' : candidates.join('、') },
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
    </Screen>
  );
}
