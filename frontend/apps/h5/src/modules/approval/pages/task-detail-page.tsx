import {
  ERROR_MESSAGES,
  TASK_RECORD_ACTION_LABELS,
  availableActions,
  describeFailure,
  documentText,
  formatDateTime,
  handlerText,
  labelOf,
  opinionText,
  processNameText,
  useTask,
  useTaskActions,
} from '@forcitis/core';
import { replacePage, useRouteParam } from '@/shared/platform/navigation';
import { EmptyHint, LoadingHint, Notice, Tag } from '@/shared/ui/feedback';
import { Card, FieldList, Screen, ScreenTitle } from '@/shared/ui/screen';
import { TaskActionBar } from '../components/task-action-bar';
import { INBOX_PAGE } from '../model/paths';

export function TaskDetailPage() {
  const taskId = useRouteParam('taskId') ?? '';
  const task = useTask(taskId);
  const actions = useTaskActions(taskId, task.data, () => replacePage(INBOX_PAGE));

  if (taskId === '') return <Screen><Notice tone="error" title="审批任务加载失败">{ERROR_MESSAGES.PLATFORM_APPROVAL_TASK_NOT_FOUND}</Notice></Screen>;
  if (task.isPending) return <Screen><LoadingHint label="加载中" /></Screen>;
  if (task.isError) return <Screen><Notice tone="error" title="审批任务加载失败">{describeFailure(task.error)}</Notice></Screen>;

  const detail = task.data;
  const mutations = [actions.claim, actions.approve, actions.reject, actions.sendBack];
  const failed = mutations.find((mutation) => mutation.isError);
  const busy = mutations.some((mutation) => mutation.isPending);
  const claimed = detail.assigneeName !== undefined;

  return (
    <Screen>
      <ScreenTitle text="审批任务" aside={<>{detail.overdue ? <Tag tone="rejected">已超时</Tag> : null}<Tag tone={claimed ? 'info' : 'pending'}>{claimed ? '已认领' : '待认领'}</Tag></>} />
      {failed === undefined ? null : <Notice tone="error" title="操作失败">{describeFailure(failed.error)}</Notice>}
      <FieldList
        rows={[
          { label: '审批流程', value: processNameText(detail.processName) },
          { label: '单据', value: documentText(detail) },
          { label: '当前节点', value: detail.nodeName ?? '—' },
          { label: '发起人', value: detail.initiatorName },
          ...(detail.initiatorOrgUnitName === undefined ? [] : [{ label: '发起人部门', value: detail.initiatorOrgUnitName }]),
          { label: '处理人', value: detail.assigneeName ?? '—' },
          { label: '候选审批人', value: detail.candidateNames.length === 0 ? '—' : detail.candidateNames.join('、') },
          { label: '创建时间', value: formatDateTime(detail.createdAt) },
          ...(detail.deadlineAt === undefined ? [] : [{ label: '截止时间', value: formatDateTime(detail.deadlineAt) }]),
          ...(detail.summary === undefined ? [] : [{ label: '业务摘要', value: detail.summary }]),
        ]}
      />
      <TaskActionBar
        actions={availableActions(detail)}
        busy={busy}
        onClaim={() => actions.claim.mutate()}
        onApprove={(reason) => actions.approve.mutate(reason)}
        onReject={(reason) => actions.reject.mutate(reason)}
        onReturn={(reason) => actions.sendBack.mutate(reason)}
      />
      <ScreenTitle text="审批记录" />
      {detail.history.length === 0 ? <EmptyHint text="暂无审批记录" /> : null}
      {detail.history.map((record) => (
        <Card key={record.recordId}>
          <FieldList
            rows={[
              { label: '节点', value: record.nodeName },
              { label: '办理结果', value: labelOf(TASK_RECORD_ACTION_LABELS, record.action) },
              { label: '办理人', value: handlerText(record) },
              { label: '办理时间', value: formatDateTime(record.decidedAt) },
              { label: '意见', value: opinionText(record.reason) },
            ]}
          />
        </Card>
      ))}
    </Screen>
  );
}
