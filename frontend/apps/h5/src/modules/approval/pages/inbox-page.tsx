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
import { useState } from 'react';
import { openPage } from '@/shared/platform/navigation';
import { ActionButton, Segments } from '@/shared/ui/actions';
import { EmptyHint, LoadingHint, Notice, Tag } from '@/shared/ui/feedback';
import { ListCard } from '@/shared/ui/list-card';
import { Screen, ScreenTitle } from '@/shared/ui/screen';
import { taskPath } from '../model/paths';

function openerOf(item: InboxItem): () => void {
  const path = taskPath(item.taskId);
  return () => openPage(path);
}

function RecordCard({ record }: { record: TaskRecord }) {
  return (
    <ListCard
      title={recordTitle(record)}
      subtitle={record.nodeName}
      note={`意见：${opinionText(record.reason)}`}
      footnote={formatDateTime(record.decidedAt)}
      tag={<Tag tone={toneOf(TASK_RECORD_ACTION_TONES, record.action)}>{labelOf(TASK_RECORD_ACTION_LABELS, record.action)}</Tag>}
      onOpen={undefined}
    />
  );
}

export function InboxPage() {
  const [view, setView] = useState<InboxView>('all');
  const pending = useInbox();
  const done = useTaskRecords(view === 'done');

  if (pending.isPending) return <Screen><LoadingHint label="加载中" /></Screen>;
  if (pending.inbox === undefined) {
    return (
      <Screen>
        <Notice tone="error" title="待办加载失败">{describeFailure(pending.error)}</Notice>
        <ActionButton onClick={() => void pending.refetch()}>刷新</ActionButton>
      </Screen>
    );
  }

  const inbox = pending.inbox;
  const showingDone = view === 'done';
  const list = showingDone ? done : pending;
  const shown = filterInbox(inbox.items, view === 'done' ? 'all' : view);
  const records = done.records ?? [];

  return (
    <Screen>
      <ScreenTitle text="待办" />
      {pending.isError ? (
        <>
          <Notice tone="error" title="待办加载失败">{describeFailure(pending.error)}</Notice>
          <ActionButton variant="default" onClick={() => void pending.refetch()}>刷新</ActionButton>
        </>
      ) : null}
      <Segments
        active={view}
        onChange={(key) => setView(inboxViewOf(key))}
        options={[
          { key: 'all', label: `全部 ${inbox.totalCount}` },
          { key: 'new', label: `新系统 ${inbox.newCount}` },
          { key: 'notice', label: `知会 ${inbox.noticeCount}` },
          { key: 'done', label: '已办' },
        ]}
      />
      {showingDone && done.isError ? <Notice tone="error" title="已办加载失败">{describeFailure(done.error)}</Notice> : null}
      {showingDone && done.isPending ? <LoadingHint label="加载中" /> : null}
      {showingDone && done.isSuccess && records.length === 0 ? <EmptyHint text="暂无已办" /> : null}
      {!showingDone && shown.length === 0 ? <EmptyHint text="暂无待办" /> : null}
      {showingDone ? records.map((record) => <RecordCard key={record.recordId} record={record} />) : null}
      {showingDone
        ? null
        : shown.map((item) => (
          <ListCard
            key={inboxRowId(item)}
            title={inboxTitle(item)}
            subtitle={item.nodeName ?? ''}
            footnote={formatDateTime(item.createdAt)}
            tag={<Tag tone="info">{item.category === 'NOTICE' ? '知会' : '新系统'}</Tag>}
            badge={inboxStateOf(item)}
            onOpen={openerOf(item)}
          />
        ))}
      {list.hasNextPage ? <ActionButton variant="default" loading={list.isFetchingNextPage} onClick={() => void list.fetchNextPage()}>显示更多</ActionButton> : null}
    </Screen>
  );
}
