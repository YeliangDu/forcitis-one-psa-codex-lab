// Card #1506: opens the built web client through the REAL nginx config (tools/ops/ecs/files/nginx-one.conf in a local container) and lists every
// Content-Security-Policy violation the browser reports on the login page and one admin page. Usage: node csp_check.mjs <https base url> (run by csp_check.sh)
import { createRequire } from 'node:module';

// Playwright lives in the web app (the working directory), not next to this file
const { chromium } = createRequire(`${process.cwd()}/`)('@playwright/test');

const base = process.argv[2];
const browser = await chromium.launch({ args: ['--host-resolver-rules=MAP one.forcitis.com 127.0.0.1'] });
const context = await browser.newContext({ ignoreHTTPSErrors: true });
const page = await context.newPage();
const violations = [];
// reported to node at once: the local login does a full page load, which would throw away anything kept in the page
await page.exposeFunction('reportCsp', (where, text) => { violations.push(`${where}: ${text}`); });
await context.addInitScript(() => {
  document.addEventListener('securitypolicyviolation', (e) => {
    window.reportCsp(location.pathname, `${e.violatedDirective} blocked ${e.blockedURI || 'inline'} at ${e.sourceFile.split('/').pop()}:${e.lineNumber} ${(e.sample || '').slice(0, 60)}`);
  });
});

const response = await page.goto(`${base}/login`);
const header = await response.headerValue('content-security-policy');
console.log('header:', header ?? 'MISSING');
await page.getByText('本机登录：Synthetic Admin').click();
await page.waitForURL((url) => !url.pathname.startsWith('/login'));
const adminResponse = await page.goto(`${base}/admin/org-units`);
await page.waitForLoadState('networkidle');
console.log('admin page title text present:', (await page.locator('body').innerText()).length > 0, 'url:', page.url().replace(base, ''));
const adminHeader = await adminResponse.headerValue('content-security-policy');
await browser.close();
// a browser reports nothing when no policy arrives, so a missing header is a failure of its own
const missing = header === null || adminHeader === null;
if (missing) console.log('CSP header MISSING on a page response');
console.log(violations.length === 0 ? 'CSP violations: 0' : `CSP violations: ${violations.length}\n${[...new Set(violations)].join('\n')}`);
process.exit(violations.length === 0 && !missing ? 0 : 1);
