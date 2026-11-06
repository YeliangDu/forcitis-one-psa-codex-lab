import { expect, test } from '@playwright/test';
import { PERSON, loginAsPlatformAdmin } from './golden-s1-support';

// HCM-LIFE-031 authorization after the transfer (pack 2026-10-02): "只改变来源授权；历史审批记录仍保留". Role: access administrator (Synthetic Platform Admin, administrator mode of #1512). Client: web.
// Start state: seed.py --stop-before 031 (clock 2026-10-02). Needs the account pages of PR #1531 (card #1520), run on that branch until it is on main.
// Node text (lead): the transfer creates a pending permission adjustment, an administrator handles it.
test('HCM-LIFE-031 the transfer of F-P01 left a pending permission adjustment for the administrator', async ({ page }) => {
  await loginAsPlatformAdmin(page);
  await page.goto('/admin/permission-adjustments');
  await expect(page.getByRole('row', { name: new RegExp(PERSON) })).toBeVisible();
});

// The change itself: the administrator changes the roles on the account page; the earlier grant stays readable (history kept).
test('HCM-LIFE-031 the access administrator changes the roles of F-P01 and the earlier grant stays', async ({ page }) => {
  await loginAsPlatformAdmin(page);
  await page.goto('/admin/accounts');
  await page.getByRole('link', { name: PERSON }).click();
  await page.getByRole('button', { name: '调整角色' }).click();
  await page.getByLabel('添加角色').selectOption({ label: '人事专员' });
  await page.getByRole('dialog').getByRole('button', { name: '提交' }).click();
  await expect(page.getByText('角色已调整')).toBeVisible();
  await expect(page.getByText('员工、人事专员', { exact: true })).toBeVisible();
  // the grant of the hire is still in the table, not ended
  const hireGrant = page.getByRole('row', { name: /^员工 / });
  await expect(hireGrant).toContainText('2026/01/05');
});
