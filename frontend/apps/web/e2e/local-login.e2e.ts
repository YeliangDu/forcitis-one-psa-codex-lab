import { expect, test } from '@playwright/test';

// Card #1478: logs in as a synthetic person through the local test login (shown only when the backend runs with profile local) and opens one page.
test('local login as a synthetic employee opens the inbox', async ({ page }) => {
  await page.goto('/login');
  await page.getByText('本机登录：Synthetic Employee').click();
  await page.waitForURL((url) => !url.pathname.startsWith('/login'));

  await page.goto('/inbox');
  await expect(page).toHaveURL(/\/inbox$/);
  await expect(page.getByRole('heading', { name: '待办' })).toBeVisible();
});
