// Self-test of eslint.config.js. Flat config lets a later block silently REPLACE an earlier block's
// no-restricted-imports / no-restricted-syntax (this is how F9 once went dead for modules/*/model).
// Lint one probe line as if it lived in each file category and assert the expected rule fires (or not).
import { ESLint } from 'eslint';

const eslint = new ESLint();
const CATEGORIES = {
  page: 'apps/web/src/modules/probe/pages/probe-page.tsx',
  hook: 'apps/web/src/modules/probe/hooks/use-probe.ts',
  model: 'apps/web/src/modules/probe/model/probe.ts',
  moduleApi: 'apps/web/src/modules/probe/api/probe-api.ts',
  app: 'apps/web/src/app/probe.tsx',
  webShared: 'apps/web/src/shared/testing/probe.tsx',
  sharedLib: 'apps/web/src/shared/lib/probe.ts',
  ui: 'packages/ui/src/probe.tsx',
  designSystem: 'packages/design-system/src/probe.ts',
  apiPackage: 'packages/api/src/probe.ts',
};
const all = Object.keys(CATEGORIES);
const without = (...excluded) => all.filter((c) => !excluded.includes(c));
const PROBES = [
  { rule: 'F5 axios', code: "import 'axios';", ruleId: 'no-restricted-imports', blocked: all },
  { rule: 'F5 fetch', code: 'export const call = () => fetch("x");', ruleId: 'no-restricted-globals', blocked: without('apiPackage') },
  { rule: 'F5 API URL', code: "export const url = '/api/v1/platform/x';", ruleId: 'no-restricted-syntax', blocked: all },
  { rule: 'F10 api package', code: "import '@forcitis/api';", ruleId: 'no-restricted-imports', blocked: without('moduleApi', 'apiPackage') },
  { rule: 'F9 react in pure code', code: "import 'react';", ruleId: 'no-restricted-imports', blocked: ['model', 'sharedLib'] },
  { rule: 'F9 api in pure code', code: "import '../api/probe-api';", ruleId: 'no-restricted-imports', blocked: ['model', 'sharedLib'] },
  { rule: 'F14 antd', code: "import 'antd';", ruleId: 'no-restricted-imports', blocked: without('ui', 'designSystem') },
  { rule: 'F14 tokens.css', code: "import '@forcitis/design-system/tokens.css';", ruleId: 'no-restricted-imports', blocked: without('designSystem') },
  { rule: 'F14 inline style', code: 'export const el = <div style={{}} />;', ruleId: 'no-restricted-syntax', blocked: ['page', 'moduleApi', 'app', 'webShared', 'ui'], tsx: true },
  { rule: 'F14 hex colour', code: "export const c = '#ff0000';", ruleId: 'no-restricted-syntax', blocked: without('designSystem') },
  { rule: 'F14 palette primitive', code: "export const c = 'var(--slate-500)';", ruleId: 'no-restricted-syntax', blocked: without('designSystem') },
  { rule: 'F11 innerHTML', code: "document.body.innerHTML = '';", ruleId: 'no-restricted-syntax', blocked: all },
  { rule: 'F12 export *', code: "export * from './x';", ruleId: 'no-restricted-syntax', blocked: all },
  { rule: 'F7 any', code: 'export const x: any = 1;', ruleId: '@typescript-eslint/no-explicit-any', blocked: all },
  { rule: 'F8 hook name', code: 'export function loadThing() { return 1; }', ruleId: '@typescript-eslint/naming-convention', blocked: ['hook'] },
  { rule: 'F6 vague name', code: 'export function DataHelper() { return 1; }', ruleId: '@typescript-eslint/naming-convention', blocked: all },
  { rule: 'F6 non-English identifier', code: 'export const 名称 = 1;', ruleId: 'id-match', blocked: all },
  { rule: 'F2 deep import of another module', code: "import '@/modules/session/pages/login-page';", ruleId: 'boundaries/entry-point', blocked: ['page', 'hook', 'model', 'moduleApi', 'app', 'webShared', 'sharedLib'] },
  { rule: 'F1 packages must not import a web module', code: "import '../../../apps/web/src/modules/session';", ruleId: 'boundaries/element-types', blocked: ['ui', 'designSystem', 'apiPackage'] },
];

let failed = false;
for (const probe of PROBES) {
  for (const [category, filePath] of Object.entries(CATEGORIES)) {
    if (probe.tsx && !filePath.endsWith('.tsx')) continue;
    const [result] = await eslint.lintText(probe.code, { filePath });
    const fired = result.messages.some((m) => m.ruleId === probe.ruleId);
    const expected = probe.blocked.includes(category);
    if (fired !== expected) {
      console.error(`${probe.rule}: expected ${expected ? 'BLOCKED' : 'allowed'} in ${filePath}, got ${fired ? 'blocked' : 'allowed'}. A later block in eslint.config.js probably replaced ${probe.ruleId}.`);
      failed = true;
    }
  }
}
if (!failed) console.log(`lint-config self-test: ${PROBES.length} probes x ${all.length} file categories OK`);
process.exit(failed ? 1 : 0);
