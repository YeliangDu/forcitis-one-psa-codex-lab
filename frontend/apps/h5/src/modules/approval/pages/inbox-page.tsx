import { useState } from 'react';
import { describeFailure } from '@/shared/lib/failure';
import { formatDateTime } from '@/shared/lib/format-date-time';
import { safeExternalUrl } from '@/shared/lib/safe-url';
import { leaveTo } from '@/shared/platform/browser';
import { openPage } from '@/shared/platform/navigation';
import { ActionButton, Segments } from '@/shared/ui/actions';
import { EmptyHint, LoadingHint, Notice, Tag } from '@/shared/ui/feedback';
import { ListCard } from '@/shared/ui/list-card';
import { Screen, ScreenTitle } from '@/shared/ui/screen';
import { useInbox } from '../hooks/use-inbox';
import { type InboxFilterKey, type InboxItem, filterInbox, inboxRowId, taskPath } from '../model/inbox';

function filterKeyOf(key: string): InboxFilterKey {
  return key === 'new' || key === 'legacy' ? key : 'all';
}

function openerOf(item: InboxItem): (() => void) | undefined {
  if (item.source === 'NEW') {
    const path = taskPath(item.taskId ?? '');
    return () => openPage(path);
  }
  const target = safeExternalUrl(item.detailUrl);
  return target === undefined ? undefined : () => leaveTo(target);
}

export function InboxPage() {
  const inbox = useInbox();
  const [filter, setFilter] = useState<InboxFilterKey>('all');

  if (inbox.isPending) return <Screen><LoadingHint label="加载中" /></Screen>;
  if (inbox.isError) {
    return (
      <Screen>
        <Notice tone="error" title="待办加载失败">{describeFailure(inbox.error)}</Notice>
        <ActionButton onClick={() => void inbox.refetch()}>刷新</ActionButton>
      </Screen>
    );
  }

  const { items = [], newCount = 0, legacyCount = 0, totalCount = 0, legacyAvailable = true } = inbox.data;
  const shown = filterInbox(items, filter);

  return (
    <Screen>
      <ScreenTitle text="待办" />
      {legacyAvailable ? null : <Notice tone="warning" title="老系统的待办暂时无法读取">当前只显示新系统的待办，请稍后刷新。</Notice>}
      <Segments
        active={filter}
        onChange={(key) => setFilter(filterKeyOf(key))}
        options={[
          { key: 'all', label: `全部 ${totalCount}` },
          { key: 'new', label: `新系统 ${newCount}` },
          { key: 'legacy', label: `老系统 ${legacyCount}` },
        ]}
      />
      {shown.length === 0 ? <EmptyHint text="暂无待办" /> : null}
      {shown.map((item) => (
        <ListCard
          key={inboxRowId(item)}
          title={item.title ?? ''}
          subtitle={item.nodeName ?? ''}
          footnote={formatDateTime(item.createdAt)}
          tag={item.source === 'NEW' ? <Tag tone="info">新系统</Tag> : <Tag tone="neutral">老系统</Tag>}
          {...(item.claimed === true ? { badge: '已认领' } : {})}
          onOpen={openerOf(item)}
        />
      ))}
    </Screen>
  );
}
