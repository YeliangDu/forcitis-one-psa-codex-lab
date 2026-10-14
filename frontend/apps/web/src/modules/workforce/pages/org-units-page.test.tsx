import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { renderWithProviders } from '@/shared/testing/render-with-providers';
import { stubServer } from '@/shared/testing/stub-server';
import { buildOrgTree } from '../model/org-unit';
import { OrgUnitsPage } from './org-units-page';

const UNITS = [
  { orgUnitId: '1', name: '总部', code: 'HQ', shortName: '总部', status: 'ACTIVE', deliveryUnit: false, sortOrder: 1 },
  { orgUnitId: '3', name: '交付二部', code: 'D2', shortName: '二部', parentOrgUnitId: '1', status: 'ACTIVE', deliveryUnit: true, sortOrder: 2 },
  { orgUnitId: '2', name: '交付一部', code: 'D1', shortName: '一部', parentOrgUnitId: '1', status: 'ACTIVE', deliveryUnit: true, sortOrder: 1 },
];

describe('组织浏览', () => {
  it('把平铺的组织单元整理成按排序的树', () => {
    const tree = buildOrgTree(UNITS);
    expect(tree.map((node) => node.name)).toEqual(['总部']);
    expect(tree[0]?.children?.map((node) => node.name)).toEqual(['交付一部', '交付二部']);
  });

  it('只读：显示组织树，没有任何编辑入口；可切换显示已撤销', async () => {
    const server = stubServer({ 'GET org-units': { body: UNITS } });
    renderWithProviders(<OrgUnitsPage />);

    expect(await screen.findByText('交付一部')).toBeInTheDocument();
    expect(screen.getAllByText('总部').length).toBeGreaterThan(0);
    expect(screen.queryByRole('button', { name: /编辑|新增|删除/ })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: '显示已撤销' }));
    await screen.findByRole('button', { name: '隐藏已撤销' });
    expect(server.calls.some((call) => call.url.includes('includeRevoked=true'))).toBe(true);
  });
});
