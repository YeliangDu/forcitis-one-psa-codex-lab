// Front-end guard rails (React) for humans and coding agents. Rules F1-F14 (NAMING_STANDARD.md); ../frontend-vue mirrors them.
// One configuration for the whole workspace (apps/web, apps/h5, packages/api, packages/core, packages/ui, packages/design-system).
// Every rule message says WHERE code belongs, so a failing lint run tells an agent how to fix it.
// CSS files are guarded by stylelint.config.js (F14 colours / palette primitives).
//
// PITFALL (flat config): when several blocks match a file, the LAST block's value of a rule REPLACES earlier ones;
// option arrays are never merged. So `no-restricted-imports` / `no-restricted-syntax` are assembled per file category
// by importsFor()/syntaxFor() below, each block carries the COMPLETE list for its files, and
// scripts/check-lint-config.mjs proves every category still bans what it should (it runs in `pnpm verify`).
import boundaries from 'eslint-plugin-boundaries';
import reactHooks from 'eslint-plugin-react-hooks';
import unicorn from 'eslint-plugin-unicorn';
import tseslint from 'typescript-eslint';

const VAGUE_NAMES = '(Util|Utils|Helper|Manager|Info|Data|Impl)$';
const PALETTE = '--(slate|red|green|amber|blue|violet|orange|yellow)-\\d';
const API_MSG = 'Use the generated operations of @forcitis/api via modules/<m>/api.';

// ---- no-restricted-imports building blocks -------------------------------------------------------------------
const BAN = {
  // F5 no hand-written HTTP
  axios: { paths: [{ name: 'axios', message: API_MSG }, { name: '@tarojs/taro', importNames: ['request', 'uploadFile', 'downloadFile'], message: API_MSG }] },
  // F10 only apps/web modules/<m>/api may import the API package (one adapter per module)
  apiPackage: { patterns: [{ group: ['@forcitis/api', '@forcitis/api/*', '**/api/generated', '**/api/generated/*'], message: 'Only modules/<m>/api may import @forcitis/api (one adapter per module).' }] },
  // F14 (H5) business code uses shared/ui, never Taro components or Taro APIs directly (shared/platform is the one adapter for Taro APIs)
  taroPrimitives: { patterns: [{ group: ['@tarojs/components', '@tarojs/components/*'], message: 'Use the components of apps/h5/src/shared/ui (they wrap Taro components with the FORCITIS one tokens).' }, { group: ['@tarojs/taro', '@tarojs/taro/*'], message: 'Use apps/h5/src/shared/platform (the one adapter for Taro APIs).' }] },
  // F9 model/ may name the generated types (`import type`), never call the client
  apiTypes: { patterns: [{ group: ['@forcitis/api', '@forcitis/api/*', '**/api/generated', '**/api/generated/*'], allowTypeImports: true, message: 'model/ may only `import type` from @forcitis/api (generated types). Calls go through modules/<m>/api.' }] },
  // F14 business code uses @forcitis/ui, never antd directly
  antd: {
    paths: [{ name: 'antd', message: 'Use the design-system components of @forcitis/ui (they wrap antd with the FORCITIS one tokens).' }],
    patterns: [{ group: ['antd/*', '@ant-design/*', 'rc-*'], message: 'Use the design-system components of @forcitis/ui.' }],
  },
  // F14 tokens.css is read only inside packages/design-system (index.ts loads it, theme.ts maps it to antd)
  tokens: { patterns: [{ group: ['**/design-system/tokens*', '@forcitis/design-system/tokens*'], message: 'tokens.css is read only by packages/design-system (index.ts loads it once, theme.ts maps it to antd). Use semantic tokens via var(--...) in CSS or @forcitis/ui.' }] },
  // F9 model/ and shared/lib are pure
  impure: { patterns: [{ group: ['react', 'react/*', 'react-*'], message: 'model/ and shared/lib are pure: no React. Put React code in hooks/ or components/.' }, { regex: '^(\\.{1,2}|@)/(.*/)?api(/|$)', message: 'model/ and shared/lib are pure: no React, no API calls. Put React code in hooks/ or components/, IO in api/.' }] },
};

function importsFor(...parts) {
  return ['error', {
    paths: parts.flatMap((p) => BAN[p].paths ?? []),
    patterns: parts.flatMap((p) => BAN[p].patterns ?? []),
  }];
}

// ---- no-restricted-syntax building blocks --------------------------------------------------------------------
const HAS_TIME_ZONE = ':has(Property:matches([key.name="timeZone"], [key.value="timeZone"]))';
const BASE_SYNTAX = [
  // F5 no hand-written API URLs
  { selector: 'Literal[value=/^\\/(admin-|app-)?api\\//]', message: 'No hand-written API URLs; use the generated operations.' },
  { selector: 'TemplateElement[value.raw=/^\\/(admin-|app-)?api\\//]', message: 'No hand-written API URLs; use the generated operations.' },
  // F11 no raw HTML (SEC-008)
  { selector: "JSXAttribute[name.name='dangerouslySetInnerHTML']", message: 'Render text, not HTML (SEC-008). Rich text goes through packages/ui/rich-text with the sanitizer.' },
  { selector: "AssignmentExpression > MemberExpression.left[property.name=/^(innerHTML|outerHTML)$/]", message: 'Render text, not HTML (SEC-008).' },
  // F12 explicit public surface
  { selector: 'ExportAllDeclaration', message: 'No `export *`: a module index lists its public surface explicitly.' },
  // G-X2 Chinese text sorts by pinyin and every time on screen is Shanghai time, whatever the browser's locale and zone are
  { selector: "CallExpression[callee.property.name='localeCompare']:not([arguments.1.value=/^zh/])", message: "localeCompare takes the locale 'zh' as its second argument (G-X2): a.localeCompare(b, 'zh')." },
  { selector: `NewExpression[callee.object.name='Intl'][callee.property.name='Collator']:not([arguments.0.value=/^zh/])`, message: "Intl.Collator takes the locale 'zh' as its first argument (G-X2)." },
  { selector: `CallExpression[callee.object.name='Intl'][callee.property.name='Collator']:not([arguments.0.value=/^zh/])`, message: "Intl.Collator takes the locale 'zh' as its first argument (G-X2)." },
  { selector: `NewExpression[callee.object.name='Intl'][callee.property.name='DateTimeFormat']:not(${HAS_TIME_ZONE})`, message: "Intl.DateTimeFormat takes a timeZone option (G-X2): the business zone is 'Asia/Shanghai'." },
  { selector: `CallExpression[callee.object.name='Intl'][callee.property.name='DateTimeFormat']:not(${HAS_TIME_ZONE})`, message: "Intl.DateTimeFormat takes a timeZone option (G-X2): the business zone is 'Asia/Shanghai'." },
  { selector: `CallExpression[callee.property.name=/^toLocale(Date|Time)?String$/]:not(${HAS_TIME_ZONE})`, message: "toLocaleString / toLocaleDateString / toLocaleTimeString take a timeZone option (G-X2); format a number with Intl.NumberFormat." },
];
const STYLE_SYNTAX = [
  // F14 no inline styles, no hard-coded colours, no palette primitives
  { selector: "JSXAttribute[name.name='style']", message: 'No inline styles: use @forcitis/ui components or a CSS file with semantic tokens (var(--surface-*|--text-*|--border-*|--intent-*|--status-*|--ledger-*)).' },
  { selector: 'Literal[value=/^#(?:[0-9a-fA-F]{3,4}){1,2}$|^(rgba?|hsla?|hwb|oklch|oklab|lab|lch|color|color-mix)\\(/]', message: 'No hard-coded colours: use semantic tokens (var(--surface-*|--text-*|--border-*|--intent-*|--status-*|--ledger-*)) via a CSS file or @forcitis/ui.' },
  { selector: 'TemplateElement[value.raw=/#(?:[0-9a-fA-F]{3,4}){1,2}\\b|\\b(rgba?|hsla?|oklch|oklab)\\(/]', message: 'No hard-coded colours: use semantic tokens via a CSS file or @forcitis/ui.' },
  { selector: `Literal[value=/${PALETTE}/]`, message: 'No palette primitives (--slate-*, --red-* ...): use a semantic token from packages/design-system/src/tokens.css.' },
  { selector: `TemplateElement[value.raw=/${PALETTE}/]`, message: 'No palette primitives (--slate-*, --red-* ...): use a semantic token from packages/design-system/src/tokens.css.' },
];

const WEB = 'apps/web/src';
const H5 = 'apps/h5/src';
const ALL_SOURCES = [`${WEB}/**/*.{ts,tsx}`, `${H5}/**/*.{ts,tsx}`, 'packages/*/src/**/*.{ts,tsx}'];
const BUSINESS = [`${WEB}/modules/**/*.{ts,tsx}`, `${WEB}/app/**/*.{ts,tsx}`, `${WEB}/shared/**/*.{ts,tsx}`, `${H5}/modules/**/*.{ts,tsx}`, `${H5}/app/**/*.{ts,tsx}`, `${H5}/*.{ts,tsx}`];
const H5_SHARED = [`${H5}/shared/**/*.{ts,tsx}`];
const MODULES_APP_SHARED = [...BUSINESS, ...H5_SHARED];

export default tseslint.config(
  { ignores: ['**/node_modules', '**/dist', 'scripts', '**/*.config.{js,ts,mts}', 'apps/h5/config'] },
  ...tseslint.configs.strict,
  {
    files: ALL_SOURCES,
    plugins: { boundaries, 'react-hooks': reactHooks, unicorn },
    settings: {
      'import/resolver': { typescript: { project: ['apps/*/tsconfig.json', 'packages/*/tsconfig.json'], noWarnOnMultipleProjects: true } },
      // Order matters: the most specific pattern first.
      'boundaries/elements': [
        { type: 'api-package', pattern: 'packages/api/src' },
        { type: 'core', pattern: 'packages/core/src' },
        { type: 'ui', pattern: 'packages/ui/src' },
        { type: 'design-system', pattern: 'packages/design-system/src' },
        { type: 'module', pattern: `${WEB}/modules/*`, capture: ['module'] },
        { type: 'web-shared', pattern: `${WEB}/shared` },
        { type: 'app', pattern: `${WEB}/app` },
        { type: 'web-test', pattern: `${WEB}/test` },
        { type: 'h5-module', pattern: `${H5}/modules/*`, capture: ['module'] },
        { type: 'h5-shared', pattern: `${H5}/shared` },
        { type: 'h5-app', pattern: `${H5}/app` },
        { type: 'h5-test', pattern: `${H5}/test` },
        { type: 'h5-entry', pattern: `${H5}/*.{ts,tsx}`, mode: 'file' },
      ],
    },
    rules: {
      // F1-F4 deep modules, narrow interfaces, one-way dependencies
      'boundaries/no-unknown-files': 'error',
      'boundaries/element-types': ['error', {
        default: 'disallow',
        message: '${file.type} must not import ${dependency.type}. Layout: app -> modules(index) -> shared / @forcitis/core / @forcitis/ui (web only) / @forcitis/design-system; only modules/<m>/api and @forcitis/core may call @forcitis/api; apps never import each other. Code both apps need belongs in @forcitis/core, never copied.',
        rules: [
          { from: 'app', allow: ['module', 'web-shared', 'core', 'ui', 'design-system'] },
          { from: 'module', allow: ['web-shared', 'module', 'core', 'ui', 'design-system', 'api-package'] },
          { from: 'web-shared', allow: ['web-shared', 'core', 'ui', 'design-system'] },
          { from: 'web-test', allow: ['web-test', 'core'] },
          { from: 'h5-entry', allow: ['h5-app'] },
          { from: 'h5-app', allow: ['h5-module', 'h5-shared', 'core', 'design-system'] },
          { from: 'h5-module', allow: ['h5-shared', 'h5-module', 'core', 'design-system', 'api-package'] },
          { from: 'h5-shared', allow: ['h5-shared', 'core', 'api-package'] },
          { from: 'h5-test', allow: ['h5-test', 'h5-shared', 'core'] },
          { from: 'core', allow: ['core', 'api-package'] },
          { from: 'ui', allow: ['ui', 'design-system'] },
          { from: 'design-system', allow: ['design-system'] },
          { from: 'api-package', allow: ['api-package'] },
        ],
      }],
      'boundaries/entry-point': ['error', {
        default: 'disallow',
        message: 'Import another module only through its public index (apps/<app>/src/modules/<m>/index.ts). Its internals are private.',
        rules: [
          { target: ['module'], allow: 'index.ts' },
          { target: ['h5-module'], allow: 'index.ts' },
          { target: ['core'], allow: ['index.ts', 'testing/index.ts'] },
          { target: ['web-shared', 'ui', 'design-system', 'api-package', 'app', 'web-test', 'h5-shared', 'h5-app', 'h5-test', 'h5-entry'], allow: '**' },
        ],
      }],
      // F5 (+F10) default for every source file; narrowed / widened per category below
      'no-restricted-imports': importsFor('axios', 'apiPackage', 'taroPrimitives'),
      'no-restricted-globals': ['error', { name: 'fetch', message: API_MSG }, { name: 'XMLHttpRequest', message: API_MSG }],
      // F5 / F11 / F12
      'no-restricted-syntax': ['error', ...BASE_SYNTAX],
      // F6 naming: English, industry glossary, predictable (vague file names: scripts/check-structure.mjs)
      'id-match': ['error', '^[\\x00-\\x7F]+$', { properties: true, onlyDeclarations: false }],
      'unicorn/filename-case': ['error', { case: 'kebabCase' }],
      '@typescript-eslint/naming-convention': ['error',
        { selector: 'typeLike', format: ['PascalCase'], custom: { regex: VAGUE_NAMES, match: false } },
        { selector: 'function', format: ['camelCase', 'PascalCase'], custom: { regex: VAGUE_NAMES, match: false } },
        { selector: 'variable', format: ['camelCase', 'PascalCase', 'UPPER_CASE'] },
      ],
      'no-restricted-properties': ['error', { object: 'console', property: 'log', message: 'Use the shared logger.' }],
      // F7 type safety and React correctness
      '@typescript-eslint/no-explicit-any': 'error',
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'error',
    },
  },
  {
    // F8 hooks are named useXxx and live in hooks/
    files: [`${WEB}/modules/*/hooks/**/*.{ts,tsx}`, `${H5}/modules/*/hooks/**/*.{ts,tsx}`],
    rules: { '@typescript-eslint/naming-convention': ['error', { selector: 'function', modifiers: ['exported'], format: null, custom: { regex: '^use[A-Z][A-Za-z0-9]*$', match: true } }] },
  },
  // ---- per-category import and syntax bans (each block is complete for its files, see PITFALL above) ----------
  {
    // F5 F10 F14: business modules, app and shared (H5 business code also stays off Taro primitives)
    files: MODULES_APP_SHARED,
    rules: {
      'no-restricted-imports': importsFor('axios', 'apiPackage', 'antd', 'tokens', 'taroPrimitives'),
      'no-restricted-syntax': ['error', ...BASE_SYNTAX, ...STYLE_SYNTAX],
    },
  },
  {
    // apps/h5/src/shared/{ui,platform,testing} is where Taro components and APIs are wrapped, so they may import them
    files: H5_SHARED,
    rules: { 'no-restricted-imports': importsFor('axios', 'apiPackage', 'antd', 'tokens') },
  },
  {
    // modules/<m>/api is the one adapter allowed to import @forcitis/api; F5 F14 still apply
    files: [`${WEB}/modules/*/api/**/*.{ts,tsx}`, `${H5}/modules/*/api/**/*.{ts,tsx}`],
    rules: { 'no-restricted-imports': importsFor('axios', 'antd', 'tokens', 'taroPrimitives') },
  },
  {
    // F9 model = pure types and rules: no React, no IO (mirrors backend domain). It may name the generated types with `import type`.
    files: [`${WEB}/modules/*/model/**/*.{ts,tsx}`, `${H5}/modules/*/model/**/*.{ts,tsx}`],
    rules: { 'no-restricted-imports': importsFor('axios', 'apiTypes', 'antd', 'tokens', 'impure', 'taroPrimitives') },
  },
  {
    // shared/lib likewise, without even the type import
    files: [`${WEB}/shared/lib/**/*.{ts,tsx}`, `${H5}/shared/lib/**/*.{ts,tsx}`],
    rules: { 'no-restricted-imports': importsFor('axios', 'apiPackage', 'antd', 'tokens', 'impure', 'taroPrimitives') },
  },
  {
    // F14 packages/ui wraps antd (allowed) but references semantic tokens only: no tokens.css, no inline style, no raw colours
    files: ['packages/ui/src/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': importsFor('axios', 'apiPackage', 'tokens', 'taroPrimitives'),
      'no-restricted-syntax': ['error', ...BASE_SYNTAX, ...STYLE_SYNTAX],
    },
  },
  {
    // The one place the code of both apps is shared: it calls the API through the generated operations and renders nothing platform specific.
    files: ['packages/core/src/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': importsFor('axios', 'antd', 'tokens', 'taroPrimitives'),
      'no-restricted-syntax': ['error', ...BASE_SYNTAX, ...STYLE_SYNTAX],
    },
  },
  {
    // The HTTP boundary: the only package allowed to call fetch. Generated code below also spells API URLs; never edited by hand.
    files: ['packages/api/src/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': importsFor('axios', 'antd', 'tokens', 'taroPrimitives'),
      'no-restricted-globals': 'off',
      'no-restricted-syntax': ['error', ...BASE_SYNTAX, ...STYLE_SYNTAX],
    },
  },
  {
    // M-6: packages/core runs under React 19 (apps/web) and React 18 (apps/h5, Taro). H5 code and the shared package may only use what both have.
    // A separate rule id, because a later block would replace the no-restricted-imports lists above.
    files: [`${H5}/**/*.{ts,tsx}`, 'packages/core/src/**/*.{ts,tsx}'],
    rules: {
      '@typescript-eslint/no-restricted-imports': ['error', {
        paths: [
          { name: 'react', importNames: ['use', 'useActionState', 'useOptimistic'], message: 'React 19 only; apps/h5 runs React 18 and packages/core is shared with it.' },
          { name: 'react-dom', importNames: ['useFormStatus', 'useFormState'], message: 'React 19 only; apps/h5 runs React 18 and packages/core is shared with it.' },
        ],
      }],
    },
  },
  {
    // Card #1478: the two paths of the local test login exist only in the backend's profile local, so they are not in openapi.json and no generated operation can name them. This one file spells them; nothing else may.
    files: ['packages/api/src/local-login.ts'],
    rules: { 'no-restricted-syntax': ['error', ...BASE_SYNTAX.slice(2), ...STYLE_SYNTAX] }, // BASE_SYNTAX minus its two URL entries
  },
  {
    files: ['packages/api/src/generated/**'],
    rules: { 'no-restricted-syntax': 'off', 'unicorn/filename-case': 'off', '@typescript-eslint/naming-convention': 'off' },
  },
);
