import { expect, test } from '@playwright/test';
import { PERSON, loginAs, loginAsPlatformAdmin } from './golden-s1-support';

// HCM-LIFE-009 ordinary access by source (pack 2026-01-05): "普通访问可用、可追来源；管理员权限未授予". Roles: F-P01 herself (ordinary access), the administrator-mode account of #1499 (traceable source; Synthetic Admin has no role:read).
// Start state: after node 007 (seed_s1.py --stop-before 009). The pack's "G-A-WORK granted from ER-001-A": the hire opens the account with role employee.
test('HCM-LIFE-009 F-P01 can sign in and use ordinary pages, and has no administrator pages', async ({ page }) => {
  await loginAs(page, PERSON);
  await page.goto('/inbox');
  await expect(page.getByRole('heading', { name: '待办' })).toBeVisible();
  // no administrator access: the employee roster of the personnel specialist is refused
  await page.goto('/admin/employees');
  await expect(page.getByRole('link', { name: PERSON })).toHaveCount(0);
});

// The traceable source, read model (exists on main): the administrator-mode account sees that the hire opened the account with the default role.
test('HCM-LIFE-009 an administrator (account of card #1499) can trace the source of the ordinary role through the grant records', async ({ page }) => {
  await loginAsPlatformAdmin(page);
  const accounts = await (await page.request.get('/api/v1/platform/user-accounts?limit=50')).json();
  const account = accounts.items.find((row: { displayName: string }) => row.displayName === PERSON);
  expect(account, 'the hired person has an account').toBeTruthy();
  const grants = await page.request.get(`/api/v1/platform/account-role-grants?userId=${account.userId}`);
  expect(grants.status()).toBe(200);
  const rows = (await grants.json()).items;
  expect(rows).toHaveLength(1);
  expect(rows[0]).toMatchObject({ roleCode: 'employee', channel: 'DEFAULT_ROLE', effectiveTo: null });
});

// The traceable source on the page (card #1496, page added by PR #1498, open when this was written): the admin sees why the hired person has the ordinary role.
test('HCM-LIFE-009 an administrator (account of card #1499) can trace the source of the ordinary role on the account grants page', async ({ page }) => {
  await loginAsPlatformAdmin(page);
  await page.goto('/admin/account-grants');
  await page.getByLabel('用户账号').selectOption({ label: `${PERSON}（EMP000001）` });
  await expect(page.getByRole('columnheader', { name: '来源' })).toBeVisible();
  await expect(page.getByText('建档默认角色')).toBeVisible();
});
