import { Button, DataTable, Dialog, LoadingState, Notice, PageTitle, StatusBadge, TextAreaField, type DataColumn } from '@forcitis/ui';
import { useState } from 'react';
import { useCurrentUser } from '@/modules/session';
import { describeFailure } from '@/shared/lib/failure';
import { formatDateTime } from '@/shared/lib/format-date-time';
import { useReleaseSyncRun, useSyncRunDetail, useSyncRuns } from '../hooks/use-workforce';
import {
  RELEASE_FAILURE_MESSAGES,
  SYNC_RUN_RELEASE_PERMISSION,
  SYNC_STATUS_LABELS,
  SYNC_TRIGGER_LABELS,
  type SyncDiff,
  type SyncRun,
  canRelease,
  syncTone,
} from '../model/sync-run';

const DIFF_COLUMNS: DataColumn<SyncDiff>[] = [
  { key: 'entity', title: '对象类型', render: (diff) => diff.entityKind ?? '—' },
  { key: 'subject', title: '对象', render: (diff) => diff.subjectKey ?? '—' },
  { key: 'change', title: '变化', render: (diff) => diff.changeKind ?? diff.issueKind ?? '—' },
  { key: 'detail', title: '说明', render: (diff) => diff.detail ?? (diff.changedFields ?? []).join('、') },
];

function releaseFailure(error: unknown): string {
  return describeFailure(error, RELEASE_FAILURE_MESSAGES);
}

export function SyncRunsPage() {
  const session = useCurrentUser();
  const runs = useSyncRuns();
  const release = useReleaseSyncRun();
  const [viewing, setViewing] = useState<string>();
  const [releasing, setReleasing] = useState<SyncRun>();
  const [reason, setReason] = useState('');
  const detail = useSyncRunDetail(viewing);
  const mayRelease = session.can(SYNC_RUN_RELEASE_PERMISSION);

  if (runs.isPending) return <LoadingState />;
  if (runs.isError) return <Notice tone="error" title="同步记录加载失败">{describeFailure(runs.error)}</Notice>;

  const closeRelease = () => {
    setReleasing(undefined);
    setReason('');
    release.reset();
  };

  const columns: DataColumn<SyncRun>[] = [
    { key: 'started', title: '开始时间', render: (run) => formatDateTime(run.startedAt) },
    { key: 'trigger', title: '触发方式', render: (run) => SYNC_TRIGGER_LABELS[run.trigger] ?? run.trigger },
    { key: 'status', title: '状态', render: (run) => <StatusBadge tone={syncTone(run.status)}>{SYNC_STATUS_LABELS[run.status] ?? run.status}</StatusBadge> },
    { key: 'orgUnits', title: '组织单元变化', render: (run) => run.orgUnitChangeCount ?? 0 },
    { key: 'employees', title: '员工变化', render: (run) => run.employeeChangeCount ?? 0 },
    { key: 'issues', title: '异常', render: (run) => run.issueCount ?? 0 },
    { key: 'reason', title: '说明', render: (run) => run.heldReason ?? run.failureReason ?? '—' },
    {
      key: 'actions',
      title: '操作',
      render: (run) => (
        <>
          <Button variant="default" onClick={() => setViewing(run.syncRunId)}>查看同步差异</Button>
          {canRelease(run, mayRelease) ? <Button onClick={() => setReleasing(run)}>放行</Button> : null}
        </>
      ),
    },
  ];

  return (
    <>
      <PageTitle text="同步记录" />
      <DataTable columns={columns} rows={runs.data.pages.flatMap((page) => page.items)} rowKey={(run) => run.syncRunId} emptyText="暂无同步记录" />
      {runs.hasNextPage ? <Button variant="default" onClick={() => void runs.fetchNextPage()} disabled={runs.isFetchingNextPage}>加载更多</Button> : null}
      <Dialog open={viewing !== undefined} title="同步差异" confirmText="关闭" cancelText="取消" onConfirm={() => setViewing(undefined)} onClose={() => setViewing(undefined)}>
        {detail.isPending ? <LoadingState /> : null}
        {detail.isError ? <Notice tone="error" title="同步差异加载失败">{describeFailure(detail.error)}</Notice> : null}
        {detail.isSuccess ? <DataTable columns={DIFF_COLUMNS} rows={detail.data.diffs} rowKey={(diff) => `${diff.entityKind ?? ''}:${diff.subjectKey ?? ''}:${diff.changeKind ?? diff.issueKind ?? ''}`} emptyText="没有同步差异" /> : null}
      </Dialog>
      <Dialog
        open={releasing !== undefined}
        title="放行被中止的同步"
        confirmText="确认放行"
        confirmLoading={release.isPending}
        confirmDisabled={reason.trim() === ''}
        onConfirm={() => releasing !== undefined && release.mutate({ syncRunId: releasing.syncRunId, reason }, { onSuccess: closeRelease })}
        onClose={closeRelease}
      >
        {release.isError ? <Notice tone="error" title="放行失败">{releaseFailure(release.error)}</Notice> : null}
        <p>{releasing?.heldReason ?? '该同步因组织数据大幅减少而被中止。'}</p>
        <p>放行后，本次同步的组织数据会立即生效。请先核对同步差异再放行。</p>
        <TextAreaField label="放行原因" value={reason} onChange={setReason} />
      </Dialog>
    </>
  );
}
