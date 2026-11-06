import { expect, test } from '@playwright/test';
import { PERSON, loginAs, openEmployee, setClock } from './golden-s1-support';

// HCM-LIFE-027 unpaid leave ends (pack 2026-08-17): the same relationship, no new one; the person works again. Role: personnel specialist. Client: web.
// Start state: seed.py --stop-before 027 (leave started on 08-10), which leaves the clock on 2026-08-17; the spec sets the same day again, harmless. The pack counters (relation_count 1, working_relations 1) have no public read: internal, not checked.
test('HCM-LIFE-027 on 2026-08-17 the personnel specialist ends the unpaid leave and the command is effective', async ({ page }) => {
  await loginAs(page, 'Synthetic Admin');
  await setClock(page, '2026-08-17T02:00:00Z');
  // the session of the earlier day has aged out on the new day: sign in again
  await page.context().clearCookies();
  await loginAs(page, 'Synthetic Admin');

  await page.goto('/admin/personnel-commands/new');
  await page.getByLabel('命令类型').selectOption({ label: '停薪留职结束' });
  await page.getByLabel('员工').selectOption({ label: `${PERSON}（EMP000001）` });
  await page.getByLabel('生效日期').fill('2026-08-17');
  await page.getByRole('button', { name: '提交' }).click();
  await expect(page.getByText('已生效')).toBeVisible();

  // back at work: still in service, same primary post, and no sign of the leave any more
  await openEmployee(page, PERSON);
  await expect(page.getByText('在职').first()).toBeVisible();
  await expect(page.getByText('Synthetic senior engineer')).toBeVisible();
  await expect(page.getByText('停薪留职', { exact: false })).toHaveCount(0);
});
