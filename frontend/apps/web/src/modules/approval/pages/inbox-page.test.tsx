import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { renderWithProviders } from '@/shared/testing/render-with-providers';
import { stubServer } from '@/shared/testing/stub-server';
import { InboxPage } from './inbox-page';

const INBOX = {
  newCount: 2,
  legacyCount: 2,
  totalCount: 4,
  legacyAvailable: true,
  items: [
    { source: 'NEW', taskId: 'n-1', title: '工时单审批 TS202610140001', nodeName: '部门负责人审核', createdAt: '2026-10-14T01:00:00Z', claimed: true, detailUrl: null },
    { source: 'NEW', taskId: 'n-2', title: '工时单审批 TS202610140002', nodeName: '部门负责人审核', createdAt: '2026-10-14T02:00:00Z', claimed: false, detailUrl: null },
    { source: 'LEGACY', taskId: 'l-1', title: '合同评审 HT-2026-001', nodeName: '法务审核', createdAt: '2026-10-13T08:00:00Z', claimed: false, detailUrl: 'https://old.example.com/bpm/task?id=l-1' },
    { source: 'LEGACY', taskId: 'l-2', title: '异常链接的老待办', nodeName: '财务审核', createdAt: '2026-10-13T09:00:00Z', claimed: false, detailUrl: 'javascript:alert(1)' },
  ],
};

describe('合并待办收件箱', () => {
  it('同时显示新系统和老系统的待办，并标出来源与数量', async () => {
    stubServer({ 'GET approval-tasks': { body: INBOX } });
    renderWithProviders(<InboxPage />);

    expect(await screen.findByText('工时单审批 TS202610140001')).toBeInTheDocument();
    expect(screen.getByText('合同评审 HT-2026-001')).toBeInTheDocument();
    expect(screen.getAllByText('新系统').length).toBeGreaterThanOrEqual(2);
    expect(screen.getAllByText('老系统').length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText('全部 4')).toBeInTheDocument();
    expect(screen.getByText('新系统 2')).toBeInTheDocument();
    expect(screen.getByText('老系统 2')).toBeInTheDocument();
    expect(screen.getByText('已认领')).toBeInTheDocument();
  });

  it('老待办是指向老系统的链接，在新窗口打开且不带来源信息', async () => {
    stubServer({ 'GET approval-tasks': { body: INBOX } });
    renderWithProviders(<InboxPage />);

    const link = await screen.findByRole('link', { name: '合同评审 HT-2026-001' });
    expect(link).toHaveAttribute('href', 'https://old.example.com/bpm/task?id=l-1');
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', expect.stringContaining('noopener'));
    expect(link).toHaveAttribute('rel', expect.stringContaining('noreferrer'));
  });

  it('老待办的地址不是 http(s) 时不生成链接', async () => {
    stubServer({ 'GET approval-tasks': { body: INBOX } });
    renderWithProviders(<InboxPage />);

    await screen.findByText('异常链接的老待办');
    expect(screen.queryByRole('link', { name: '异常链接的老待办' })).not.toBeInTheDocument();
  });

  it('新待办在本系统内打开审批任务，不开新窗口', async () => {
    stubServer({ 'GET approval-tasks': { body: INBOX } });
    renderWithProviders(<InboxPage />);

    const link = await screen.findByRole('link', { name: '工时单审批 TS202610140002' });
    expect(link).toHaveAttribute('href', '/approval/tasks/n-2');
    expect(link).not.toHaveAttribute('target');
    await userEvent.click(link);
    expect(screen.getByTestId('location')).toHaveTextContent('/approval/tasks/n-2');
  });

  it('可以按来源筛选', async () => {
    stubServer({ 'GET approval-tasks': { body: INBOX } });
    renderWithProviders(<InboxPage />);
    await screen.findByText('工时单审批 TS202610140001');

    await userEvent.click(screen.getByText('老系统 2'));
    const list = screen.getByRole('list');
    expect(within(list).queryByText('工时单审批 TS202610140001')).not.toBeInTheDocument();
    expect(within(list).getByText('合同评审 HT-2026-001')).toBeInTheDocument();
  });

  it('老库读取失败时提示只显示了新系统的待办，而不是显示成没有待办', async () => {
    stubServer({ 'GET approval-tasks': { body: { ...INBOX, legacyAvailable: false, legacyCount: 0, totalCount: 2, items: INBOX.items.slice(0, 2) } } });
    renderWithProviders(<InboxPage />);

    expect(await screen.findByText('老系统的待办暂时无法读取')).toBeInTheDocument();
    expect(screen.queryByText('暂无待办')).not.toBeInTheDocument();
  });
});
