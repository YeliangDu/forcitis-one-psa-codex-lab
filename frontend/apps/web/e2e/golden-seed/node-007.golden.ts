import { expect, test } from '@playwright/test';
import { PERSON, loginAs } from './golden-s1-support';

// HCM-LIFE-007 first day at work (pack 2026-01-05): "员工1、关系1、存续1、主职1". Role: personnel specialist (the seeded admin). Client: web.
// Start state: seed.py --stop-before 007, clock 2026-01-05.
test('HCM-LIFE-007 the personnel specialist hires F-P01 in the Nanjing unit', async ({ page }) => {
  await loginAs(page, 'Synthetic Admin');
  await page.goto('/admin/personnel-commands/new');
  await page.getByLabel('命令类型').selectOption({ label: '入职' });
  await page.getByLabel('姓名').fill(PERSON);
  await page.getByLabel('企业邮箱').fill('f-p01@golden.example');
  await page.getByLabel('生效日期').fill('2026-01-05');
  await page.getByLabel('目标组织单元').selectOption({ label: 'Synthetic Nanjing unit' });
  await page.getByLabel('目标岗位').selectOption({ label: 'Synthetic junior engineer' });
  await page.getByLabel('用人单位').selectOption({ label: 'Synthetic employer A' }); // field added by #1491
  await page.getByLabel('是否计费人员').selectOption({ label: '是' });
  await page.getByLabel('任职阶段').selectOption({ label: '试用' });
  await page.getByRole('button', { name: '提交' }).click();

  // the command exists and is effective
  await expect(page.getByText('已生效')).toBeVisible();
  // one employee, in service, one primary post in Nanjing
  await page.goto('/admin/employees');
  await expect(page.getByRole('link', { name: PERSON })).toHaveCount(1);
  await page.getByRole('link', { name: PERSON }).click();
  await expect(page.getByRole('heading', { name: PERSON })).toBeVisible();
  await expect(page.getByText('Synthetic Nanjing unit')).toBeVisible();
  await expect(page.getByText('Synthetic junior engineer')).toBeVisible();
});
