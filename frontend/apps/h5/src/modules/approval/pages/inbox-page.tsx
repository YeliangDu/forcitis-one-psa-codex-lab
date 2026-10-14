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
import { useState } from 'react';
import { leaveTo } from '@/shared/platform/browser';
import { openPage } from '@/shared/platform/navigation';
import { ActionButton, Segments } from '@/shared/ui/actions';
import { EmptyHint, LoadingHint, Notice, Tag } from '@/shared/ui/feedback';
import { ListCard } from '@/shared/ui/list-card';
import { Screen, ScreenTitle } from '@/shared/ui/screen';
import { taskPath } from '../model/paths';

type View = InboxFilterKey | 'done';

function viewOf(key: string): View {
  return key === 'new' || key === 'legacy' || key === 'notice' || key === 'done' ? key : 'all';
}

function badgeOf(item: InboxItem): string | undefined {
  if (item.overdue) return '已超时';
  return item.claimed ? '已认领' : undefined;
}

function openerOf(item: InboxItem): (() => void) | undefined {
  if (item.source === 'NEW') {
    const path = taskPath(item.taskId);
    return () => openPage(path);
  }
  const target = safeExternalUrl(item.detailUrl);
  return target === undefined ? undefined : () => leaveTo(target);
}

function RecordCard({ record }: { record: TaskRecord }) {
  return (
    <ListCard
      title={`${PROCESS_LABELS[record.processKey]} ${record.documentId}`}
      subtitle={record.nodeName}
      note={`意见：${opinionText(record.reason)}`}
      footnote={formatDateTime(record.decidedAt)}
      tag={<Tag tone={TASK_RECORD_ACTION_TONES[record.action]}>{TASK_RECORD_ACTION_LABELS[record.action]}</Tag>}
      onOpen={undefined}
    />
  );
}

export function InboxPage() {
  const [view, setView] = useState<View>('all');
  const pending = useInbox();
  const done = useTaskRecords(view === 'done');

  if (pending.isPending) return <Screen><LoadingHint label="加载中" /></Screen>;
  if (pending.isError || pending.inbox === undefined) {
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
      {inbox.legacyAvailable ? null : <Notice tone="warning" title="老系统的待办暂时无法读取">当前只显示新系统的待办，请稍后刷新。</Notice>}
      <Segments
        active={view}
        onChange={(key) => setView(viewOf(key))}
        options={[
          { key: 'all', label: `全部 ${inbox.totalCount}` },
          { key: 'new', label: `新系统 ${inbox.newCount}` },
          { key: 'legacy', label: `老系统 ${inbox.legacyCount}` },
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
            tag={item.source === 'NEW' ? <Tag tone="info">新系统</Tag> : <Tag tone="neutral">{item.category === 'NOTICE' ? '知会' : '老系统'}</Tag>}
            badge={badgeOf(item)}
            onOpen={openerOf(item)}
          />
        ))}
      {list.hasNextPage ? <ActionButton variant="default" loading={list.isFetchingNextPage} onClick={() => void list.fetchNextPage()}>显示更多</ActionButton> : null}
    </Screen>
  );
}
