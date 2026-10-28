import {
  TASK_RECORD_ACTION_LABELS,
  TASK_RECORD_ACTION_TONES,
  type TaskHistoryEntry,
  availableActions,
  describeFailure,
  documentText,
  formatDateTime,
  handlerText,
  labelOf,
  opinionText,
  processNameText,
  toneOf,
  useTask,
  useTaskActions,
} from '@forcitis/core';
import { DataTable, DescriptionList, LoadingState, Notice, PageTitle, StatusBadge, type DataColumn } from '@forcitis/ui';
import { useNavigate, useParams } from 'react-router';
import { TaskActionBar } from '../components/task-action-bar';

const HISTORY_COLUMNS: DataColumn<TaskHistoryEntry>[] = [
  { key: 'node', title: '节点', render: (record) => record.nodeName },
  { key: 'action', title: '办理结果', render: (record) => <StatusBadge tone={toneOf(TASK_RECORD_ACTION_TONES, record.action)}>{labelOf(TASK_RECORD_ACTION_LABELS, record.action)}</StatusBadge> },
  { key: 'actor', title: '办理人', render: (record) => handlerText(record) },
  { key: 'time', title: '办理时间', render: (record) => formatDateTime(record.decidedAt) },
  { key: 'opinion', title: '意见', render: (record) => opinionText(record.reason) },
];

export function TaskDetailPage() {
  const { taskId = '' } = useParams();
  const navigate = useNavigate();
  const task = useTask(taskId);
  const actions = useTaskActions(taskId, task.data, () => void navigate('/inbox'));

  if (task.isPending) return <LoadingState />;
  if (task.isError) return <Notice tone="error" title="审批任务加载失败">{describeFailure(task.error)}</Notice>;

  const detail = task.data;
  const failed = [actions.claim, actions.approve, actions.reject, actions.sendBack].find((mutation) => mutation.isError);
  const busy = [actions.claim, actions.approve, actions.reject, actions.sendBack].some((mutation) => mutation.isPending);
  const claimed = detail.assigneeName !== undefined;

  return (
    <>
      <PageTitle text="审批任务" actions={<>{detail.overdue ? <StatusBadge tone="rejected">已超时</StatusBadge> : null}<StatusBadge tone={claimed ? 'info' : 'pending'}>{claimed ? '已认领' : '待认领'}</StatusBadge></>} />
      {failed === undefined ? null : <Notice tone="error" title="操作失败">{describeFailure(failed.error)}</Notice>}
      <DescriptionList
        items={[
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
      <PageTitle text="审批记录" />
      <DataTable columns={HISTORY_COLUMNS} rows={detail.history} rowKey={(record) => record.recordId} emptyText="暂无审批记录" />
    </>
  );
}
