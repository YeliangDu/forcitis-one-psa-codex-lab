import { expect, test } from '@playwright/test';
import { PERSON, loginAs, openEmployee } from './golden-s1-support';

// HCM-LIFE-016 probation passed (pack 2026-04-05): "仍为ER-001-A；转正经历保留、主职未改变". Role: personnel specialist. Client: web.
// Start state: seed_s1.py --stop-before 016, clock 2026-04-05.
test('HCM-LIFE-016 the personnel specialist confirms the regularization of F-P01', async ({ page }) => {
  await loginAs(page, 'Synthetic Admin');
  await page.goto('/admin/personnel-commands/new');
  await page.getByLabel('命令类型').selectOption({ label: '转正' });
  await page.getByLabel('员工').selectOption({ label: `${PERSON}（EMP000001）` });
  await page.getByLabel('生效日期').fill('2026-04-05');
  await page.getByRole('button', { name: '提交' }).click();
  await expect(page.getByText('已生效')).toBeVisible();

  await openEmployee(page, PERSON);
  // the primary post is unchanged
  await expect(page.getByText('Synthetic junior engineer')).toBeVisible();
  await expect(page.getByText('Synthetic Nanjing unit')).toBeVisible();
});
