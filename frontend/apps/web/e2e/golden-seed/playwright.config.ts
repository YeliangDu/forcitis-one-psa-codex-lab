import { defineConfig } from '@playwright/test';

// Card #1479: the node checks of the golden mainline run by hand against a seeded local stack, one node at a time
// (npx playwright test -c e2e/golden-seed/playwright.config.ts e2e/golden-seed/node-007.golden.ts). They are not part of the plain `playwright test` run (suffix .golden.ts):
// each expects the state that seed_s1.py leaves before its node. Headless, one worker, no retries: a flaky run must show as red.
export default defineConfig({
  testDir: '.',
  testMatch: '**/*.golden.ts',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  expect: { timeout: 15_000 },
  reporter: [['list']],
  outputDir: '../../../../node_modules/.cache/playwright-results',
  use: { baseURL: process.env.LOCAL_WEB_URL ?? 'http://localhost:5173', headless: true, trace: 'off' },
});
