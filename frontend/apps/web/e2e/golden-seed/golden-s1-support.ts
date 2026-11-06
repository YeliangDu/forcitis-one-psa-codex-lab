import { expect, type Page } from '@playwright/test';

// Pilot slice S1 (card #1479). Each spec expects the stack seeded by `seed_s1.py --stop-before <node>` with the local clock on the node's pack day.
// Expected values are the pack's (NOT_REVIEWED); a mismatch is reported in the gap list, never bent to pass.
export const PERSON = 'Synthetic Golden Person F-P01';

export async function loginAs(page: Page, name: string): Promise<void> {
  await page.goto('/login');
  await page.getByText(`本机登录：${name}`).click();
  await page.waitForURL((url) => !url.pathname.startsWith('/login'));
}

export async function openEmployee(page: Page, name: string): Promise<void> {
  await page.goto('/admin/employees');
  await page.getByRole('link', { name }).click();
  await expect(page.getByRole('heading', { name })).toBeVisible();
}

/** Moves the local clock (profile local only; setting it runs the due jobs once, ruling of card #1486). Needs a signed-in page for the XSRF cookie. */
export async function setClock(page: Page, at: string): Promise<void> {
  await page.request.get('/api/v1/platform/local-accounts'); // refreshes the XSRF cookie after the login (else the POST is 403)
  const token = (await page.context().cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value ?? '';
  const response = await page.request.post('/api/v1/platform/local-clocks:set', { data: { at }, headers: { 'X-XSRF-TOKEN': token } });
  expect(response.status(), 'setting the local clock').toBeLessThan(300);
}

/**
 * Signs in as the administrator-mode account of card #1499 and takes the explicit local step into the administrator mode (the production step without the WeCom proof).
 * The session id is rotated by the step; the browser context keeps the cookie the response sets. After it the clock endpoint refuses any move back before the end of the mode (one hour).
 */
export async function loginAsPlatformAdmin(page: Page): Promise<void> {
  await loginAs(page, 'Synthetic Platform Admin');
  // Park the page first: the app keeps calling the API with the old session cookie, and a 401 caused by the rotation makes it sign the person out.
  await page.goto('about:blank');
  await page.request.get('/api/v1/platform/local-accounts'); // refreshes the XSRF cookie after the login
  const token = (await page.context().cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')?.value ?? '';
  const response = await page.request.post('/api/v1/platform/sessions:enter-local-administrator-mode', { headers: { 'X-XSRF-TOKEN': token } });
  expect(response.status(), 'entering the local administrator mode').toBeLessThan(300);
}
