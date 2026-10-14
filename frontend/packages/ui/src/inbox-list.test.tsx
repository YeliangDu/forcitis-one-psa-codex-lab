import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { InboxList, type InboxRow } from './inbox-list';

const ROWS: InboxRow[] = [
  { id: 'n', title: '新待办', subtitle: '部门审核', timeText: '2026/10/14 09:00', tag: { label: '新系统', tone: 'info' }, href: '/tasks/n', external: false, onOpen: vi.fn() },
  { id: 'l', title: '老待办', subtitle: '法务审核', timeText: '2026/10/13 16:00', tag: { label: '老系统', tone: 'neutral' }, href: 'https://old.example.com/t/l', external: true },
  { id: 'x', title: '没有链接的老待办', subtitle: '', timeText: '', tag: { label: '老系统', tone: 'neutral' }, external: true },
];

const FILTERS = [{ key: 'all', label: '全部', count: 3 }, { key: 'new', label: '新系统', count: 1 }];

describe('InboxList', () => {
  it('老系统的行在新窗口打开，新系统的行由路由处理', async () => {
    const open = vi.fn();
    const rows = ROWS.map((row) => (row.id === 'n' ? { ...row, onOpen: open } : row));
    render(<InboxList rows={rows} filters={FILTERS} activeFilter="all" onFilterChange={() => undefined} emptyText="暂无待办" />);

    const external = screen.getByRole('link', { name: '老待办' });
    expect(external).toHaveAttribute('target', '_blank');
    expect(external).toHaveAttribute('rel', 'noopener noreferrer');

    const internal = screen.getByRole('link', { name: '新待办' });
    expect(internal).not.toHaveAttribute('target');
    await userEvent.click(internal);
    expect(open).toHaveBeenCalledTimes(1);
  });

  it('没有地址的行只显示文字', () => {
    render(<InboxList rows={ROWS} filters={FILTERS} activeFilter="all" onFilterChange={() => undefined} emptyText="暂无待办" />);
    expect(screen.getByText('没有链接的老待办')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '没有链接的老待办' })).not.toBeInTheDocument();
  });

  it('没有行时显示空状态文字，行操作区由调用方提供', () => {
    const { rerender } = render(<InboxList rows={[]} filters={FILTERS} activeFilter="all" onFilterChange={() => undefined} emptyText="暂无待办" />);
    expect(screen.getByText('暂无待办')).toBeInTheDocument();
    rerender(<InboxList rows={ROWS.slice(0, 1)} filters={FILTERS} activeFilter="all" onFilterChange={() => undefined} emptyText="暂无待办" rowActions={(row) => <button type="button">办理 {row.id}</button>} />);
    expect(screen.getByRole('button', { name: '办理 n' })).toBeInTheDocument();
  });

  it('切换筛选时通知调用方', async () => {
    const change = vi.fn();
    render(<InboxList rows={ROWS} filters={FILTERS} activeFilter="all" onFilterChange={change} emptyText="暂无待办" />);
    await userEvent.click(screen.getByText('新系统 1'));
    expect(change).toHaveBeenCalledWith('new');
  });
});
