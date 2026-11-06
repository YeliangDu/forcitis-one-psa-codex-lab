import { expect, test } from '@playwright/test';
import { PERSON, loginAs, openEmployee } from './golden-s1-support';

// HCM-LIFE-030 transfer to the Shanghai unit (pack 2026-10-01): lighting support post, same employer, relationship unchanged. Role: personnel specialist. Client: web.
// Start state: seed.py --stop-before 030, clock 2026-10-01. The pack counter (relation_count 1, ER-001-A unchanged) has no public read: internal, not checked.
test('HCM-LIFE-030 the personnel specialist transfers F-P01 to the Shanghai unit and it is the current primary post', async ({ page }) => {
  await loginAs(page, 'Synthetic Admin');
  await page.goto('/admin/personnel-commands/new');
  await page.getByLabel('命令类型').selectOption({ label: '调岗' });
  await page.getByLabel('员工').selectOption({ label: `${PERSON}（EMP000001）` });
  await page.getByLabel('生效日期').fill('2026-10-01');
  await page.getByLabel('目标组织单元').selectOption({ label: 'Synthetic Shanghai unit' });
  await page.getByLabel('目标岗位').selectOption({ label: 'Synthetic lighting support post' });
  await page.getByRole('button', { name: '提交' }).click();
  await expect(page.getByText('已生效')).toBeVisible();

  await openEmployee(page, PERSON);
  await expect(page.getByText('Synthetic Shanghai unit').first()).toBeVisible();
  await expect(page.getByText('Synthetic lighting support post').first()).toBeVisible();
});

// The earlier primary post stays readable (card #1495, section 主岗历史): red until PR #1500 is on main.
test('HCM-LIFE-030 the primary post history shows the Nanjing post ended on 2026-10-01', async ({ page }) => {
  await loginAs(page, 'Synthetic Admin');
  await openEmployee(page, PERSON);
  const rows = page.getByRole('table', { name: '主岗历史' }).getByRole('row');
  await expect(rows.nth(1)).toContainText('Synthetic lighting support post');
  await expect(rows.nth(1)).toContainText('当前');
  await expect(rows.nth(2)).toContainText('Synthetic senior engineer');
  await expect(rows.nth(2)).toContainText('2026-10-01');
});
