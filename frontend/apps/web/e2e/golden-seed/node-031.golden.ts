import { expect, test } from '@playwright/test';
import { PERSON, loginAs, loginAsPlatformAdmin } from './golden-s1-support';

// HCM-LIFE-031 authorization after the transfer (pack 2026-10-02): "只改变来源授权；历史审批记录仍保留". Clients: web.
// Start state: seed.py --stop-before 031 (clock 2026-10-02). Before node 030 the seed saved the 2026 work calendar and gave F-P01 the role "Synthetic unit reader"
// (scope: the Nanjing unit), so the transfer opened a scope review (ADR-0032 decision 4). The new unit has no head, so the item has no recipient and is handled by a holder
// of permission-adjustment:update: Synthetic Admin (role org_data_admin). Changing roles is the access administrator's work (Synthetic Platform Admin, administrator mode of #1512).
// Needs the account and adjustment pages of PR #1531 (card #1520), run on that branch until it is on main. Run once per seeded stack: the second test changes the roles.
test.describe.configure({ mode: 'serial' });

test('HCM-LIFE-031 the transfer of F-P01 left a pending permission adjustment for the handler', async ({ page }) => {
  await loginAs(page, 'Synthetic Admin');
  await page.goto('/admin/permission-adjustments');
  // the only open item is the scope review after the transfer; it opens with the actions of the handler
  const row = page.getByRole('row', { name: /范围复核 调岗 待处理/ });
  await expect(row).toBeVisible();
  await row.getByRole('link').first().click();
  await expect(page.getByRole('button', { name: '标记已调整' })).toBeVisible();
});

// The access administrator takes the role of the old unit away; the grant of the hire stays in the table (history kept).
test('HCM-LIFE-031 the access administrator takes the unit role off F-P01 and the earlier grant stays', async ({ page }) => {
  await loginAsPlatformAdmin(page);
  await page.goto('/admin/accounts');
  await page.getByRole('link', { name: PERSON }).click();
  await expect(page.getByText('员工、Synthetic unit reader', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '调整角色' }).click();
  await page.getByRole('button', { name: '移除 Synthetic unit reader' }).click();
  await page.getByRole('dialog').getByRole('button', { name: '提交' }).click();
  await expect(page.getByText('角色已调整')).toBeVisible();
  await expect(page.getByText('员工、Synthetic unit reader', { exact: true })).toHaveCount(0);
  await expect(page.getByRole('row', { name: /^员工 / })).toContainText('2026/01/05');
});

// Red: the list and the item show the person by user id although the API answers the name (userName). Fails until the pages show the name.
test('HCM-LIFE-031 the adjustment names the person, not the user id', async ({ page }) => {
  await loginAs(page, 'Synthetic Admin');
  await page.goto('/admin/permission-adjustments');
  await expect(page.getByRole('row', { name: new RegExp(PERSON) })).toBeVisible();
});
