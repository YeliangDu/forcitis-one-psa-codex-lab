import { expect, test } from '@playwright/test';
import { PERSON, loginAs, openEmployee, setClock } from './golden-s1-support';

// HCM-LIFE-023 promotion takes effect (pack 2026-07-01): "当前主职1；原主职历史可查；关系仍1". Role: personnel specialist. Client: web.
// Start state: after node 022 (seed.py --stop-before 023). The test moves the local clock to the day; the due job then runs once (ruling of card #1486).
test('HCM-LIFE-023 on 2026-07-01 the promotion is effective and the senior post is the current primary post', async ({ page }) => {
  await loginAs(page, 'Synthetic Admin');
  await setClock(page, '2026-07-01T02:00:00Z');
  // the session of 06-15 has aged out on the new day: sign in again
  await page.context().clearCookies();
  await loginAs(page, 'Synthetic Admin');

  await page.goto('/admin/personnel-commands');
  await page.getByLabel('命令类型').selectOption({ label: '调岗' });
  // the transfer is the only one of its kind: its badge is 已生效 and nothing waits any more
  await expect(page.locator('.status-badge', { hasText: '已生效' })).toHaveCount(1);
  await expect(page.locator('.status-badge', { hasText: '待生效' })).toHaveCount(0);

  await openEmployee(page, PERSON);
  await expect(page.getByText('Synthetic senior engineer').first()).toBeVisible();

  // the earlier primary post is readable (card #1495, section 主岗历史 on the same page): newest first, the new one is 当前, the old one ends on the transfer day
  const history = page.getByRole('table', { name: '主岗历史' });
  const rows = history.getByRole('row');
  await expect(rows.nth(1)).toContainText('Synthetic senior engineer');
  await expect(rows.nth(1)).toContainText('当前');
  await expect(rows.nth(2)).toContainText('Synthetic junior engineer');
  await expect(rows.nth(2)).toContainText('2026-07-01');
  await expect(rows.nth(2)).not.toContainText('当前');
});
