// Self-test of eslint.config.js. Flat config lets a later block silently REPLACE an earlier block's
// no-restricted-imports / no-restricted-syntax (this is how F9 once went dead for modules/*/model).
// Lint one probe line as if it lived in each file category and assert the expected rule fires (or not).
import { ESLint } from 'eslint';

const eslint = new ESLint();
const CATEGORIES = {
  page: 'src/modules/probe/pages/probe-page.tsx',
  hook: 'src/modules/probe/hooks/use-probe.ts',
  model: 'src/modules/probe/model/probe.ts',
  moduleApi: 'src/modules/probe/api/probe-api.ts',
  app: 'src/app/probe.tsx',
  sharedUi: 'src/shared/ui/probe.tsx',
  sharedLib: 'src/shared/lib/probe.ts',
};
const all = Object.keys(CATEGORIES);
const PROBES = [
  { rule: 'F5 axios', code: "import 'axios';", ruleId: 'no-restricted-imports', blocked: all },
  { rule: 'F10 generated client', code: "import '@/api/generated/client';", ruleId: 'no-restricted-imports', blocked: all.filter((c) => c !== 'moduleApi') },
  { rule: 'F9 react in pure code', code: "import 'react';", ruleId: 'no-restricted-imports', blocked: ['model', 'sharedLib'] },
  { rule: 'F9 api in pure code', code: "import '../api/probe-api';", ruleId: 'no-restricted-imports', blocked: ['model', 'sharedLib'] },
  { rule: 'F14 antd', code: "import 'antd';", ruleId: 'no-restricted-imports', blocked: all.filter((c) => c !== 'sharedUi') },
  { rule: 'F14 tokens.css', code: "import '@/shared/design-system/tokens.css';", ruleId: 'no-restricted-imports', blocked: all },
  { rule: 'F14 hex colour', code: "export const c = '#ff0000';", ruleId: 'no-restricted-syntax', blocked: all.filter((c) => c !== 'sharedLib') },
  { rule: 'F14 palette primitive', code: "export const c = 'var(--slate-500)';", ruleId: 'no-restricted-syntax', blocked: all.filter((c) => c !== 'sharedLib') },
  { rule: 'F11 innerHTML', code: "document.body.innerHTML = '';", ruleId: 'no-restricted-syntax', blocked: all },
  { rule: 'F12 export *', code: "export * from './x';", ruleId: 'no-restricted-syntax', blocked: all },
];

let failed = false;
for (const probe of PROBES) {
  for (const [category, filePath] of Object.entries(CATEGORIES)) {
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
