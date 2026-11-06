import { expect, test } from '@playwright/test';
import { PERSON, loginAs, openEmployee } from './golden-s1-support';

// HCM-LIFE-022 register a future promotion (pack 2026-06-15): "06-30仍旧职旧薪；两项决定分别存储". Role: personnel specialist. Client: web.
// Start state: seed.py --stop-before 022, clock 2026-06-15. Only the post decision (AS-A2) has a command; the salary decision SAL-A-V2 has none (gap list).
test('HCM-LIFE-022 the personnel specialist registers the promotion effective 2026-07-01, the old post stays until then', async ({ page }) => {
  await loginAs(page, 'Synthetic Admin');
  await page.goto('/admin/personnel-commands/new');
  await page.getByLabel('命令类型').selectOption({ label: '调岗' });
  await page.getByLabel('员工').selectOption({ label: `${PERSON}（EMP000001）` });
  await page.getByLabel('生效日期').fill('2026-07-01');
  await page.getByLabel('目标组织单元').selectOption({ label: 'Synthetic Nanjing unit' });
  await page.getByLabel('目标岗位').selectOption({ label: 'Synthetic senior engineer' });
  await page.getByRole('button', { name: '提交' }).click();

  // the command waits for its day
  await expect(page.getByText('待生效')).toBeVisible();
  // the day before the promotion the person still holds the old post
  await openEmployee(page, PERSON);
  await expect(page.getByText('Synthetic junior engineer')).toBeVisible();
});
