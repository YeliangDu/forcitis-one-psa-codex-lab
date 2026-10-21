import {
  type InboxItem,
  type InboxView,
  type TaskRecord,
  TASK_RECORD_ACTION_LABELS,
  TASK_RECORD_ACTION_TONES,
  describeFailure,
  filterInbox,
  formatDateTime,
  inboxRowId,
  inboxStateOf,
  inboxTitle,
  inboxViewOf,
  labelOf,
  opinionText,
  recordTitle,
  toneOf,
  useInbox,
  useTaskRecords,
} from '@forcitis/core';
import { Button, InboxList, LoadingState, Notice, PageTitle, type InboxRow } from '@forcitis/ui';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { taskPath } from '../model/paths';

function recordRow(record: TaskRecord): InboxRow {
  return {
    id: record.recordId,
    title: recordTitle(record),
    subtitle: record.nodeName,
    note: `意见：${opinionText(record.reason)}`,
    timeText: formatDateTime(record.decidedAt),
    tag: { label: labelOf(TASK_RECORD_ACTION_LABELS, record.action), tone: toneOf(TASK_RECORD_ACTION_TONES, record.action) },
    external: false,
  };
}

export function InboxPage() {
  const navigate = useNavigate();
  const [view, setView] = useState<InboxView>('all');
  const pending = useInbox();
  const done = useTaskRecords(view === 'done');

  if (pending.isPending) return <LoadingState />;
  const inbox = pending.inbox;
  if (inbox === undefined) return <Notice tone="error" title="待办加载失败">{describeFailure(pending.error)}</Notice>;

  const toRow = (item: InboxItem): InboxRow => {
    const common = { id: inboxRowId(item), title: inboxTitle(item), subtitle: item.nodeName ?? '', timeText: formatDateTime(item.createdAt) };
    const path = taskPath(item.taskId);
    return {
      ...common,
      tag: { label: item.category === 'NOTICE' ? '知会' : '新系统', tone: 'info' },
      state: inboxStateOf(item),
      href: path,
      external: false,
      onOpen: () => void navigate(path),
    };
  };

  const showingDone = view === 'done';
  const list = showingDone ? done : pending;
  const rows = showingDone ? (done.records ?? []).map(recordRow) : filterInbox(inbox.items, view).map(toRow);

  return (
    <>
      <PageTitle text="待办" />
      {pending.isError ? <Notice tone="error" title="待办加载失败">{describeFailure(pending.error)}</Notice> : null}
      <InboxList
        rows={rows}
        filters={[
          { key: 'all', label: '全部', count: inbox.totalCount },
          { key: 'new', label: '新系统', count: inbox.newCount },
          { key: 'notice', label: '知会', count: inbox.noticeCount },
          { key: 'done', label: '已办' },
        ]}
        activeFilter={view}
        onFilterChange={(key) => setView(inboxViewOf(key))}
        emptyText={showingDone ? '暂无已办' : '暂无待办'}
      />
      {showingDone && done.isError ? <Notice tone="error" title="已办加载失败">{describeFailure(done.error)}</Notice> : null}
      {showingDone && done.isPending ? <LoadingState /> : null}
      {list.hasNextPage ? <Button variant="default" loading={list.isFetchingNextPage} onClick={() => void list.fetchNextPage()}>显示更多</Button> : null}
    </>
  );
}
