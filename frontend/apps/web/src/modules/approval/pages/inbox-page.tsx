import { InboxList, LoadingState, Notice, PageTitle, type InboxRow } from '@forcitis/ui';
import { useState } from 'react';
import { useNavigate } from 'react-router';
import { describeFailure } from '@/shared/lib/failure';
import { formatDateTime } from '@/shared/lib/format-date-time';
import { safeExternalUrl } from '@/shared/lib/safe-url';
import { useInbox } from '../hooks/use-inbox';
import { type InboxFilterKey, type InboxItem, filterInbox, inboxRowId, taskPath } from '../model/inbox';

export function InboxPage() {
  const navigate = useNavigate();
  const inbox = useInbox();
  const [filter, setFilter] = useState<InboxFilterKey>('all');

  if (inbox.isPending) return <LoadingState />;
  if (inbox.isError) return <Notice tone="error" title="待办加载失败">{describeFailure(inbox.error)}</Notice>;

  const { items = [], newCount = 0, legacyCount = 0, totalCount = 0, legacyAvailable = true } = inbox.data;

  const toRow = (item: InboxItem): InboxRow => {
    const subtitle = item.nodeName ?? '';
    const timeText = formatDateTime(item.createdAt);
    if (item.source === 'NEW') {
      const path = taskPath(item.taskId ?? '');
      return {
        id: inboxRowId(item),
        title: item.title ?? '',
        subtitle,
        timeText,
        tag: { label: '新系统', tone: 'info' },
        ...(item.claimed === true ? { state: '已认领' } : {}),
        href: path,
        external: false,
        onOpen: () => void navigate(path),
      };
    }
    const target = safeExternalUrl(item.detailUrl);
    return {
      id: inboxRowId(item),
      title: item.title ?? '',
      subtitle,
      timeText,
      tag: { label: '老系统', tone: 'neutral' },
      ...(target === undefined ? {} : { href: target }),
      external: true,
    };
  };

  return (
    <>
      <PageTitle text="待办" />
      <InboxList
        rows={filterInbox(items, filter).map(toRow)}
        filters={[
          { key: 'all', label: '全部', count: totalCount },
          { key: 'new', label: '新系统', count: newCount },
          { key: 'legacy', label: '老系统', count: legacyCount },
        ]}
        activeFilter={filter}
        onFilterChange={(key) => setFilter(key === 'new' || key === 'legacy' ? key : 'all')}
        emptyText="暂无待办"
        {...(legacyAvailable ? {} : { notice: <Notice tone="warning" title="老系统的待办暂时无法读取">当前只显示新系统的待办，请稍后刷新。</Notice> })}
      />
    </>
  );
}
