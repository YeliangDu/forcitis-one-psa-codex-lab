import { defineConfig } from '@playwright/test';

// Card #1478: one smoke run against the local stack (tools/ops/local/local.sh up). Headless, one worker, no retries: a flaky run must show as red.
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.ts',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  outputDir: '../../node_modules/.cache/playwright-results',
  use: {
    baseURL: process.env.LOCAL_WEB_URL ?? 'http://localhost:5173',
    headless: true,
    trace: 'off',
  },
});
