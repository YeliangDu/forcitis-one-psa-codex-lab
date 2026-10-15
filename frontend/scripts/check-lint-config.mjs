// Self-test of eslint.config.js. Flat config lets a later block silently REPLACE an earlier block's
// no-restricted-imports / no-restricted-syntax (this is how F9 once went dead for modules/*/model).
// Lint one probe line as if it lived in each file category and assert the expected rule fires (or not).
// Every category of apps/web and apps/h5 is probed: the H5 guard rails are exactly as strict as the PC ones.
import { dirname, relative } from 'node:path';
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
  core: 'packages/core/src/probe.ts',
  h5Page: 'apps/h5/src/modules/probe/pages/probe-page.tsx',
  h5Hook: 'apps/h5/src/modules/probe/hooks/use-probe.ts',
  h5Model: 'apps/h5/src/modules/probe/model/probe.ts',
  h5ModuleApi: 'apps/h5/src/modules/probe/api/probe-api.ts',
  h5App: 'apps/h5/src/app/probe.tsx',
  h5Entry: 'apps/h5/src/probe.tsx',
  h5Shared: 'apps/h5/src/shared/ui/probe.tsx',
  h5SharedLib: 'apps/h5/src/shared/lib/probe.ts',
};
const all = Object.keys(CATEGORIES);
const h5 = all.filter((c) => c.startsWith('h5'));
const web = all.filter((c) => !c.startsWith('h5'));
const packages = ['ui', 'designSystem', 'apiPackage', 'core'];
const apps = all.filter((c) => !packages.includes(c));
const without = (...excluded) => all.filter((c) => !excluded.includes(c));
const pair = (...names) => names.flatMap((name) => [name, `h5${name[0].toUpperCase()}${name.slice(1)}`]);
const towards = (target) => (filePath) => {
  const path = relative(dirname(filePath), target);
  return `import '${path.startsWith('.') ? path : `./${path}`}';`;
};

const PROBES = [
  { rule: 'F5 axios', code: "import 'axios';", ruleId: 'no-restricted-imports', blocked: all },
  { rule: 'F5 Taro.request', code: "import { request } from '@tarojs/taro'; export const call = request;", ruleId: 'no-restricted-imports', blocked: all },
  { rule: 'F5 fetch', code: 'export const call = () => fetch("x");', ruleId: 'no-restricted-globals', blocked: without('apiPackage') },
  { rule: 'F5 API URL', code: "export const url = '/api/v1/platform/x';", ruleId: 'no-restricted-syntax', blocked: all },
  { rule: 'F10 api package', code: "import '@forcitis/api';", ruleId: 'no-restricted-imports', blocked: without('moduleApi', 'apiPackage', 'h5ModuleApi', 'core') },
  { rule: 'F9 react in pure code', code: "import 'react';", ruleId: 'no-restricted-imports', blocked: ['model', 'sharedLib', 'h5Model', 'h5SharedLib'] },
  { rule: 'F9 api in pure code', code: "import '../api/probe-api';", ruleId: 'no-restricted-imports', blocked: ['model', 'sharedLib', 'h5Model', 'h5SharedLib'] },
  { rule: 'F9 model may only import types of the api package', code: "import type { Schema } from '@forcitis/api'; export type X = Schema<'X'>;", ruleId: 'no-restricted-imports', blocked: without('moduleApi', 'apiPackage', 'model', 'h5ModuleApi', 'h5Model', 'core') },
  { rule: 'F14 antd', code: "import 'antd';", ruleId: 'no-restricted-imports', blocked: without('ui', 'designSystem') },
  { rule: 'F14 tokens.css', code: "import '@forcitis/design-system/tokens.css';", ruleId: 'no-restricted-imports', blocked: without('designSystem') },
  { rule: 'F14 inline style', code: 'export const el = <div style={{}} />;', ruleId: 'no-restricted-syntax', blocked: [...pair('page', 'moduleApi', 'app'), 'webShared', 'ui', 'h5Entry', 'h5Shared'], tsx: true },
  { rule: 'F14 hex colour', code: "export const c = '#ff0000';", ruleId: 'no-restricted-syntax', blocked: without('designSystem') },
  { rule: 'F14 palette primitive', code: "export const c = 'var(--slate-500)';", ruleId: 'no-restricted-syntax', blocked: without('designSystem') },
  { rule: 'F14 Taro components outside apps/h5 shared', code: "import { View } from '@tarojs/components'; export const el = View;", ruleId: 'no-restricted-imports', blocked: without('h5Shared') },
  { rule: 'F14 Taro APIs outside apps/h5 shared', code: "import Taro from '@tarojs/taro'; export const go = Taro;", ruleId: 'no-restricted-imports', blocked: without('h5Shared') },
  { rule: 'F11 innerHTML', code: "document.body.innerHTML = '';", ruleId: 'no-restricted-syntax', blocked: all },
  { rule: 'G-X2 localeCompare without a locale', code: 'export const order = (a: string, b: string) => a.localeCompare(b);', ruleId: 'no-restricted-syntax', blocked: all },
  { rule: 'G-X2 localeCompare with a non-Chinese locale', code: "export const order = (a: string, b: string) => a.localeCompare(b, 'en');", ruleId: 'no-restricted-syntax', blocked: all },
  { rule: 'G-X2 localeCompare with zh is fine', code: "export const order = (a: string, b: string) => a.localeCompare(b, 'zh');", ruleId: 'no-restricted-syntax', blocked: [] },
  { rule: 'G-X2 Intl.DateTimeFormat without timeZone', code: "export const f = new Intl.DateTimeFormat('zh-CN', { year: 'numeric' });", ruleId: 'no-restricted-syntax', blocked: all },
  { rule: 'G-X2 Intl.DateTimeFormat without options', code: "export const f = new Intl.DateTimeFormat('zh-CN');", ruleId: 'no-restricted-syntax', blocked: all },
  { rule: 'G-X2 Intl.DateTimeFormat with timeZone is fine', code: "export const f = new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric' });", ruleId: 'no-restricted-syntax', blocked: [] },
  { rule: 'F12 export *', code: "export * from './x';", ruleId: 'no-restricted-syntax', blocked: all },
  { rule: 'M-6 React 19 hook `use` in shared or H5 code', code: "import { use } from 'react'; export const x = use;", ruleId: '@typescript-eslint/no-restricted-imports', blocked: ['core', ...h5] },
  { rule: 'M-6 React 19 hooks useActionState / useOptimistic in shared or H5 code', code: "import { useActionState, useOptimistic } from 'react'; export const x = [useActionState, useOptimistic];", ruleId: '@typescript-eslint/no-restricted-imports', blocked: ['core', ...h5] },
  { rule: 'M-6 React 19 useFormStatus in shared or H5 code', code: "import { useFormStatus } from 'react-dom'; export const x = useFormStatus;", ruleId: '@typescript-eslint/no-restricted-imports', blocked: ['core', ...h5] },
  { rule: 'F7 any', code: 'export const x: any = 1;', ruleId: '@typescript-eslint/no-explicit-any', blocked: all },
  { rule: 'F8 hook name', code: 'export function loadThing() { return 1; }', ruleId: '@typescript-eslint/naming-convention', blocked: ['hook', 'h5Hook'] },
  { rule: 'F6 vague name', code: 'export function DataHelper() { return 1; }', ruleId: '@typescript-eslint/naming-convention', blocked: all },
  { rule: 'F6 non-English identifier', code: 'export const 名称 = 1;', ruleId: 'id-match', blocked: all },
  { rule: 'F2 deep import of another module', code: "import '@/modules/session/pages/login-page';", ruleId: 'boundaries/entry-point', blocked: ['page', 'hook', 'model', 'moduleApi', 'app', 'webShared', 'sharedLib'], only: web },
  { rule: 'F2 deep import of another module (H5)', code: "import '@/modules/approval/pages/inbox-page';", ruleId: 'boundaries/entry-point', blocked: h5, only: h5 },
  { rule: 'F1 packages must not import a web module', code: "import '../../../apps/web/src/modules/session';", ruleId: 'boundaries/element-types', blocked: packages, only: packages },
  { rule: 'F1 packages must not import an H5 module', code: towards('apps/h5/src/modules/approval/index.ts'), ruleId: 'boundaries/element-types', blocked: packages, only: packages },
  { rule: 'F1 only the apps use @forcitis/core; the other packages never do', code: towards('packages/core/src/index.ts'), ruleId: 'boundaries/element-types', blocked: ['ui', 'designSystem', 'apiPackage'], only: ['ui', 'designSystem', 'apiPackage'] },
  { rule: 'F1 both apps may import @forcitis/core', code: towards('packages/core/src/index.ts'), ruleId: 'boundaries/element-types', blocked: [], only: apps.filter((c) => c !== 'h5Entry') },
  { rule: 'F2 an app never imports the internals of @forcitis/core', code: towards('packages/core/src/failure.ts'), ruleId: 'boundaries/entry-point', blocked: apps, only: apps },
  { rule: 'F1 apps never import each other (PC -> H5)', code: towards('apps/h5/src/modules/approval/index.ts'), ruleId: 'boundaries/element-types', blocked: web, only: web },
  { rule: 'F1 apps never import each other (H5 -> PC)', code: towards('apps/web/src/modules/session/index.ts'), ruleId: 'boundaries/element-types', blocked: h5, only: h5 },
  { rule: 'F1 H5 never uses the antd based @forcitis/ui', code: towards('packages/ui/src/index.ts'), ruleId: 'boundaries/element-types', blocked: h5, only: h5 },
];

let failed = false;
let checked = 0;
for (const probe of PROBES) {
  for (const [category, filePath] of Object.entries(CATEGORIES)) {
    if (probe.tsx && !filePath.endsWith('.tsx')) continue;
    if (probe.only && !probe.only.includes(category)) continue;
    const code = typeof probe.code === 'function' ? probe.code(filePath) : probe.code;
    const [result] = await eslint.lintText(code, { filePath });
    const fired = result.messages.some((m) => m.ruleId === probe.ruleId);
    const expected = probe.blocked.includes(category);
    checked += 1;
    if (fired !== expected) {
      console.error(`${probe.rule}: expected ${expected ? 'BLOCKED' : 'allowed'} in ${filePath}, got ${fired ? 'blocked' : 'allowed'}. A later block in eslint.config.js probably replaced ${probe.ruleId}.`);
      failed = true;
    }
  }
}
if (!failed) console.log(`lint-config self-test: ${PROBES.length} probes, ${checked} file-category checks OK (${web.length} PC/package categories, ${h5.length} H5 categories)`);
process.exit(failed ? 1 : 0);
