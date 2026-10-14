import { InboxList, Notice } from '@forcitis/ui';

const rows = [
  { id: 'n-1', title: '报价单 QT-FST-202610-0012 待审批', subtitle: '部门负责人审批', timeText: '2026-10-14 09:32', tag: { label: '新系统', tone: 'info' as const }, state: '已认领', href: '#', external: false },
  { id: 'n-2', title: '项目预算修订 PRJ-2026-087', subtitle: '财务复核', timeText: '2026-10-13 17:05', tag: { label: '新系统', tone: 'info' as const }, href: '#', external: false },
  { id: 'l-1', title: '差旅报销单 BX202610-0331', subtitle: '直属上级审批', timeText: '2026-10-13 11:48', tag: { label: '老系统', tone: 'neutral' as const }, href: '#', external: true },
];
const filters = [
  { key: 'all', label: '全部', count: 3 },
  { key: 'new', label: '新系统', count: 2 },
  { key: 'legacy', label: '老系统', count: 1 },
];
const noop = () => {};

export const MergedInbox = () => (
  <InboxList rows={rows} filters={filters} activeFilter="all" onFilterChange={noop} emptyText="暂无待办" />
);

export const LegacyUnavailable = () => (
  <InboxList
    rows={rows.slice(0, 2)}
    filters={[{ key: 'all', label: '全部', count: 2 }, { key: 'new', label: '新系统', count: 2 }, { key: 'legacy', label: '老系统', count: 0 }]}
    activeFilter="new"
    onFilterChange={noop}
    emptyText="暂无待办"
    notice={<Notice tone="warning" title="老系统的待办暂时无法读取">当前只显示新系统的待办，请稍后刷新。</Notice>}
  />
);

export const Empty = () => (
  <InboxList rows={[]} filters={[{ key: 'all', label: '全部', count: 0 }, { key: 'new', label: '新系统', count: 0 }, { key: 'legacy', label: '老系统', count: 0 }]} activeFilter="all" onFilterChange={noop} emptyText="暂无待办" />
);
