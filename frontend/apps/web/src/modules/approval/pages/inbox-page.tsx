import {
  type InboxFilterKey,
  type InboxItem,
  PROCESS_LABELS,
  type TaskRecord,
  TASK_RECORD_ACTION_LABELS,
  TASK_RECORD_ACTION_TONES,
  describeFailure,
  filterInbox,
  formatDateTime,
  inboxRowId,
  inboxTitle,
  opinionText,
  safeExternalUrl,
  useInbox,
  useTaskRecords,
} from '@forcitis/core';
import { Button, InboxList, LoadingState, Notice, PageTitle, type InboxRow } from '@forcitis/ui';
import { useState } from 'react';
import { useNavigate } from 'react-router';

type View = InboxFilterKey | 'done';

function taskPath(taskId: string): string {
  return `/approval/tasks/${encodeURIComponent(taskId)}`;
}

function stateOf(item: InboxItem): string | undefined {
  if (item.overdue) return '已超时';
  return item.claimed ? '已认领' : undefined;
}

function viewOf(key: string): View {
  return key === 'new' || key === 'legacy' || key === 'notice' || key === 'done' ? key : 'all';
}

function recordRow(record: TaskRecord): InboxRow {
  return {
    id: record.recordId,
    title: `${PROCESS_LABELS[record.processKey]} ${record.documentId}`,
    subtitle: record.nodeName,
    note: `意见：${opinionText(record.reason)}`,
    timeText: formatDateTime(record.decidedAt),
    tag: { label: TASK_RECORD_ACTION_LABELS[record.action], tone: TASK_RECORD_ACTION_TONES[record.action] },
    external: false,
  };
}

export function InboxPage() {
  const navigate = useNavigate();
  const [view, setView] = useState<View>('all');
  const pending = useInbox();
  const done = useTaskRecords(view === 'done');

  if (pending.isPending) return <LoadingState />;
  if (pending.isError && !pending.isFetchNextPageError) return <Notice tone="error" title="待办加载失败">{describeFailure(pending.error)}</Notice>;
  const inbox = pending.inbox;
  if (inbox === undefined) return <LoadingState />;

  const toRow = (item: InboxItem): InboxRow => {
    const common = { id: inboxRowId(item), title: inboxTitle(item), subtitle: item.nodeName ?? '', timeText: formatDateTime(item.createdAt) };
    if (item.source === 'NEW') {
      const path = taskPath(item.taskId);
      return {
        ...common,
        tag: { label: '新系统', tone: 'info' },
        state: stateOf(item),
        href: path,
        external: false,
        onOpen: () => void navigate(path),
      };
    }
    const target = safeExternalUrl(item.detailUrl);
    return { ...common, state: stateOf(item), tag: { label: item.category === 'NOTICE' ? '知会' : '老系统', tone: 'neutral' }, ...(target === undefined ? {} : { href: target }), external: true };
  };

  const showingDone = view === 'done';
  const list = showingDone ? done : pending;
  const rows = showingDone ? (done.records ?? []).map(recordRow) : filterInbox(inbox.items, view).map(toRow);

  return (
    <>
      <PageTitle text="待办" />
      <InboxList
        rows={rows}
        filters={[
          { key: 'all', label: '全部', count: inbox.totalCount },
          { key: 'new', label: '新系统', count: inbox.newCount },
          { key: 'legacy', label: '老系统', count: inbox.legacyCount },
          { key: 'notice', label: '知会', count: inbox.noticeCount },
          { key: 'done', label: '已办' },
        ]}
        activeFilter={view}
        onFilterChange={(key) => setView(viewOf(key))}
        emptyText={showingDone ? '暂无已办' : '暂无待办'}
        {...(inbox.legacyAvailable ? {} : { notice: <Notice tone="warning" title="老系统的待办暂时无法读取">当前只显示新系统的待办，请稍后刷新。</Notice> })}
      />
      {!showingDone && pending.isFetchNextPageError ? <Notice tone="error" title="待办加载失败">{describeFailure(pending.error)}</Notice> : null}
      {showingDone && done.isError ? <Notice tone="error" title="已办加载失败">{describeFailure(done.error)}</Notice> : null}
      {showingDone && done.isPending ? <LoadingState /> : null}
      {list.hasNextPage ? <Button variant="default" loading={list.isFetchingNextPage} onClick={() => void list.fetchNextPage()}>显示更多</Button> : null}
    </>
  );
}
