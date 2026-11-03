import { AppShell, InboxList, PageTitle } from '@forcitis/ui';

const noop = () => {};
const navigation = [
  { key: '/工作台', label: '工作台', children: [{ key: '/工作台/待办', label: '待办', path: '/inbox' }] },
  { key: '/系统管理', label: '系统管理', children: [
    { key: '/系统管理/编号规则', label: '编号规则', path: '/numbering-rules' },
  ] },
];

export const Workspace = () => (
  <div style={{ height: 560 }}>
    <AppShell brand="FORCITIS one" navigation={navigation} activePath="/inbox" onNavigate={noop} userName="张敏" userDetail="业务管理部" logoutText="退出登录" onLogout={noop}>
      <PageTitle text="待办" />
      <InboxList
        rows={[
          { id: 'n-1', title: '报价单 QT-FST-202610-0012 待审批', subtitle: '部门负责人审批', timeText: '2026-10-14 09:32', tag: { label: '新系统', tone: 'info' }, href: '#', external: false },
          { id: 'l-1', title: '差旅报销单 BX202610-0331', subtitle: '直属上级审批', timeText: '2026-10-13 11:48', tag: { label: '老系统', tone: 'neutral' }, href: '#', external: true },
        ]}
        filters={[{ key: 'all', label: '全部', count: 2 }, { key: 'new', label: '新系统', count: 1 }, { key: 'legacy', label: '老系统', count: 1 }]}
        activeFilter="all"
        onFilterChange={noop}
        emptyText="暂无待办"
      />
    </AppShell>
  </div>
);
