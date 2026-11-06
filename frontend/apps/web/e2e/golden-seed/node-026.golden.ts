import { expect, test } from '@playwright/test';
import { PERSON, loginAs, openEmployee } from './golden-s1-support';

// HCM-LIFE-026 unpaid leave starts (pack 2026-08-10): the relationship is kept, the person is on leave. Role: personnel specialist. Client: web.
// Start state: seed.py --stop-before 026, clock 2026-08-10. The pack counters (active_relations 1, working_relations 0) have no public read: internal, not checked.
// What a user must see is the state after the step: the command is effective, and the employee page says the person is on unpaid leave.
test('HCM-LIFE-026 the personnel specialist starts the unpaid leave of F-P01 and the command is effective', async ({ page }) => {
  await loginAs(page, 'Synthetic Admin');
  await page.goto('/admin/personnel-commands/new');
  await page.getByLabel('命令类型').selectOption({ label: '停薪留职开始' });
  await page.getByLabel('员工').selectOption({ label: `${PERSON}（EMP000001）` });
  await page.getByLabel('生效日期').fill('2026-08-10');
  await page.getByRole('button', { name: '提交' }).click();
  await expect(page.getByText('已生效')).toBeVisible();
  // the primary post is unchanged by the leave
  await openEmployee(page, PERSON);
  await expect(page.getByText('Synthetic senior engineer')).toBeVisible();
});

// The state the user needs: nowhere but the command itself says the person is on leave.
test('HCM-LIFE-026 the employee page shows that F-P01 is on unpaid leave', async ({ page }) => {
  await loginAs(page, 'Synthetic Admin');
  await openEmployee(page, PERSON);
  await expect(page.getByText('停薪留职', { exact: false }).first()).toBeVisible();
});
