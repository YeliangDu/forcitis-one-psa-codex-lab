// Front-end guard rails (React) for humans and coding agents. Rules F1-F14 (NAMING_STANDARD.md); ../frontend-vue mirrors them.
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
const API_MSG = 'Use the OpenAPI-generated client (src/api/generated) via modules/<m>/api.';

// ---- no-restricted-imports building blocks -------------------------------------------------------------------
const BAN = {
  // F5 no hand-written HTTP
  axios: { paths: [{ name: 'axios', message: API_MSG }] },
  // F10 only modules/<m>/api may import the generated client
  generated: { patterns: [{ group: ['@/api/generated', '@/api/generated/*', '**/api/generated', '**/api/generated/*'], message: 'Only modules/<m>/api may import the generated client (one adapter per module).' }] },
  // F14 business code uses shared/ui, never antd directly
  antd: {
    paths: [{ name: 'antd', message: 'Use the design-system components in @/shared/ui (they wrap antd with the FORCITIS one tokens).' }],
    patterns: [{ group: ['antd/*', '@ant-design/*', 'rc-*'], message: 'Use the design-system components in @/shared/ui.' }],
  },
  // F14 tokens.css is read only inside shared/design-system (index.ts loads it, theme.ts maps it to antd)
  tokens: { patterns: [{ group: ['**/design-system/tokens*'], message: 'tokens.css is read only by shared/design-system (index.ts loads it once, theme.ts maps it to antd). Use semantic tokens via var(--...) in CSS or @/shared/ui.' }] },
  // F9 model/ and shared/lib are pure
  impure: { patterns: [{ group: ['react', 'react/*', 'react-*', '@/api/*', '**/api/*', '**/api'], message: 'model/ and shared/lib are pure: no React, no API calls. Put React code in hooks/ or components/, IO in api/.' }] },
};

function importsFor(...parts) {
  return ['error', {
    paths: parts.flatMap((p) => BAN[p].paths ?? []),
    patterns: parts.flatMap((p) => BAN[p].patterns ?? []),
  }];
}

// ---- no-restricted-syntax building blocks --------------------------------------------------------------------
const BASE_SYNTAX = [
  // F5 no hand-written API URLs
  { selector: 'Literal[value=/^\\/(admin-|app-)?api\\//]', message: 'No hand-written API URLs; use the generated client.' },
  { selector: 'TemplateElement[value.raw=/^\\/(admin-|app-)?api\\//]', message: 'No hand-written API URLs; use the generated client.' },
  // F11 no raw HTML (SEC-008)
  { selector: "JSXAttribute[name.name='dangerouslySetInnerHTML']", message: 'Render text, not HTML (SEC-008). Rich text goes through shared/ui/rich-text with the sanitizer.' },
  { selector: "AssignmentExpression > MemberExpression.left[property.name=/^(innerHTML|outerHTML)$/]", message: 'Render text, not HTML (SEC-008).' },
  // F12 explicit public surface
  { selector: 'ExportAllDeclaration', message: 'No `export *`: a module index lists its public surface explicitly.' },
];
const STYLE_SYNTAX = [
  // F14 no inline styles, no hard-coded colours, no palette primitives
  { selector: "JSXAttribute[name.name='style']", message: 'No inline styles: use @/shared/ui components or a CSS file with semantic tokens (var(--surface-*|--text-*|--border-*|--intent-*|--status-*|--ledger-*)).' },
  { selector: 'Literal[value=/^#(?:[0-9a-fA-F]{3,4}){1,2}$|^(rgba?|hsla?|hwb|oklch|oklab|lab|lch|color|color-mix)\\(/]', message: 'No hard-coded colours: use semantic tokens (var(--surface-*|--text-*|--border-*|--intent-*|--status-*|--ledger-*)) via a CSS file or @/shared/ui.' },
  { selector: 'TemplateElement[value.raw=/#(?:[0-9a-fA-F]{3,4}){1,2}\\b|\\b(rgba?|hsla?|oklch|oklab)\\(/]', message: 'No hard-coded colours: use semantic tokens via a CSS file or @/shared/ui.' },
  { selector: `Literal[value=/${PALETTE}/]`, message: 'No palette primitives (--slate-*, --red-* ...): use a semantic token from shared/design-system/tokens.css.' },
  { selector: `TemplateElement[value.raw=/${PALETTE}/]`, message: 'No palette primitives (--slate-*, --red-* ...): use a semantic token from shared/design-system/tokens.css.' },
];

const MODULES_AND_APP = ['src/modules/**/*.{ts,tsx}', 'src/app/**/*.{ts,tsx}'];

export default tseslint.config(
  { ignores: ['node_modules', 'dist', 'scripts', '*.config.{js,ts}'] },
  ...tseslint.configs.strict,
  {
    files: ['src/**/*.{ts,tsx}'],
    plugins: { boundaries, 'react-hooks': reactHooks, unicorn },
    settings: {
      'import/resolver': { typescript: { project: './tsconfig.json' } },
      // Order matters: the most specific pattern first.
      'boundaries/elements': [
        { type: 'generated-api', pattern: 'src/api/generated' },
        { type: 'module', pattern: 'src/modules/*', capture: ['module'] },
        { type: 'shared', pattern: 'src/shared' },
        { type: 'app', pattern: 'src/app' },
      ],
    },
    rules: {
      // F1-F4 deep modules, narrow interfaces, one-way dependencies
      'boundaries/no-unknown-files': 'error',
      'boundaries/element-types': ['error', {
        default: 'disallow',
        message: '${file.type} must not import ${dependency.type}. Layout: app -> modules(index) -> shared; only modules/<m>/api may call api/generated.',
        rules: [
          { from: 'app', allow: ['module', 'shared'] },
          { from: 'module', allow: ['shared', 'module', 'generated-api'] },
          { from: 'shared', allow: ['shared'] },
        ],
      }],
      'boundaries/entry-point': ['error', {
        default: 'disallow',
        message: 'Import another module only through its public index (src/modules/<m>/index.ts). Its internals are private.',
        rules: [
          { target: ['module'], allow: 'index.ts' },
          { target: ['shared', 'generated-api', 'app'], allow: '**' },
        ],
      }],
      // F5 (+F10) default for every src file; narrowed / widened per category below
      'no-restricted-imports': importsFor('axios', 'generated'),
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
    files: ['src/modules/*/hooks/**/*.{ts,tsx}'],
    rules: { '@typescript-eslint/naming-convention': ['error', { selector: 'function', modifiers: ['exported'], format: null, custom: { regex: '^use[A-Z][A-Za-z0-9]*$', match: true } }] },
  },
  // ---- per-category import and syntax bans (each block is complete for its files, see PITFALL above) ----------
  {
    // F5 F10 F14: business modules and app
    files: MODULES_AND_APP,
    rules: {
      'no-restricted-imports': importsFor('axios', 'generated', 'antd', 'tokens'),
      'no-restricted-syntax': ['error', ...BASE_SYNTAX, ...STYLE_SYNTAX],
    },
  },
  {
    // modules/<m>/api is the one adapter allowed to import the generated client; F5 F14 still apply
    files: ['src/modules/*/api/**/*.{ts,tsx}'],
    rules: { 'no-restricted-imports': importsFor('axios', 'antd', 'tokens') },
  },
  {
    // F9 model = pure types and rules: no React, no IO (mirrors backend domain). shared/lib likewise.
    files: ['src/modules/*/model/**/*.{ts,tsx}', 'src/shared/lib/**/*.{ts,tsx}'],
    rules: { 'no-restricted-imports': importsFor('axios', 'generated', 'antd', 'tokens', 'impure') },
  },
  {
    // F14 shared/ui wraps antd (allowed) but references semantic tokens only: no tokens.css, no inline style, no raw colours
    files: ['src/shared/ui/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': importsFor('axios', 'generated', 'tokens'),
      'no-restricted-syntax': ['error', ...BASE_SYNTAX, ...STYLE_SYNTAX],
    },
  },
  {
    // Generated code: the only place allowed to call fetch / spell API URLs; never edited by hand.
    files: ['src/api/generated/**'],
    rules: { 'no-restricted-imports': importsFor('axios'), 'no-restricted-globals': 'off', 'no-restricted-syntax': 'off', 'unicorn/filename-case': 'off' },
  },
);
