// Front-end guard rails (Vue 3) for humans and coding agents. Mirrors ../frontend (React) rules F1-F14.
// Every rule message says WHERE code belongs, so a failing lint run tells an agent how to fix it.
// CSS (<style> blocks and .css) is guarded by stylelint.config.js (F14 colours / palette primitives).
import boundaries from 'eslint-plugin-boundaries';
import unicorn from 'eslint-plugin-unicorn';
import pluginVue from 'eslint-plugin-vue';
import tseslint from 'typescript-eslint';

const VAGUE_NAMES = '(Util|Utils|Helper|Manager|Info|Data|Impl)$';
const PALETTE = '--(slate|red|green|amber|blue|violet|orange|yellow)-\\d';

const API_MSG = 'Use the generated client (src/api/generated, Zod-typed) via modules/<m>/api.';
const AXIOS = { name: 'axios', message: API_MSG };
const GENERATED_ONLY_IN_API = {
  group: ['@/api/generated/*', '**/api/generated/*', '@/shared/api/*', '**/shared/api/*'],
  message: 'Only modules/<m>/api may import the generated client or shared/api (one adapter per module).',
};

const BASE_SYNTAX = [
  { selector: 'Literal[value=/^\\/(admin-|app-)?api\\//]', message: 'No hand-written API URLs; use the generated client.' },
  { selector: 'TemplateElement[value.raw=/^\\/(admin-|app-)?api\\//]', message: 'No hand-written API URLs; use the generated client.' },
  { selector: 'ExportAllDeclaration', message: 'No `export *`: a module index lists its public surface explicitly.' },
  { selector: "Property[key.name='innerHTML']", message: 'Render text, not HTML (SEC-008). Rich text goes through shared/ui/RichText with the sanitizer.' },
];
const COLOUR_SYNTAX = [
  { selector: 'Literal[value=/^#(?:[0-9a-fA-F]{3,4}){1,2}$|^(rgba?|hsla?|oklch|oklab|lab|lch|hwb|color-mix)\\(/]', message: 'No hard-coded colours: use semantic tokens (var(--surface-*|--text-*|--border-*|--intent-*|--status-*|--ledger-*)) via shared/ui.' },
  { selector: `Literal[value=/${PALETTE}/]`, message: 'No palette primitives (--slate-*, --red-* ...): use a semantic token from shared/design-system/tokens.css.' },
  { selector: `TemplateElement[value.raw=/${PALETTE}/]`, message: 'No palette primitives (--slate-*, --red-* ...): use a semantic token from shared/design-system/tokens.css.' },
];
const NO_INLINE_STYLE = {
  'vue/no-restricted-static-attribute': ['error',
    { key: 'style', message: 'No inline styles: use shared/ui components or a scoped <style> with semantic tokens.' },
    { key: '/.+/', value: '/^#(?:[0-9a-fA-F]{3,4}){1,2}$|^(rgba?|hsla?|oklch|oklab)\\(/', message: 'No hard-coded colours in template attributes: use semantic tokens.' },
  ],
  'vue/no-restricted-v-bind': ['error',
    { argument: 'style', message: 'No :style bindings: use shared/ui components or a scoped <style> with semantic tokens.' },
    { argument: 'innerHTML', message: 'Render text, not HTML (SEC-008).' },
  ],
  'vue/no-restricted-syntax': ['error', ...COLOUR_SYNTAX],
};

export default tseslint.config(
  { ignores: ['node_modules', 'dist', 'scripts', '*.config.{js,ts}'] },
  ...tseslint.configs.strict,
  ...pluginVue.configs['flat/essential'],
  {
    files: ['**/*.vue'],
    languageOptions: { parserOptions: { parser: tseslint.parser, extraFileExtensions: ['.vue'], sourceType: 'module' } },
  },
  {
    files: ['src/**/*.{ts,vue}'],
    plugins: { boundaries, unicorn },
    settings: {
      'import/resolver': { typescript: { project: './tsconfig.json', extensions: ['.ts', '.vue'] } },
      'import/extensions': ['.ts', '.vue'],
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
          { from: 'generated-api', allow: ['shared'] },
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
      // F5 no hand-written HTTP: go through the generated client in modules/<m>/api
      'no-restricted-imports': ['error', { paths: [AXIOS] }],
      'no-restricted-globals': ['error', { name: 'fetch', message: API_MSG }, { name: 'XMLHttpRequest', message: API_MSG }],
      'no-restricted-syntax': ['error', ...BASE_SYNTAX],
      // F6 naming: English, industry glossary, predictable (.ts kebab-case, .vue PascalCase - see next block)
      'id-match': ['error', '^[\\x00-\\x7F]+$', { properties: true, onlyDeclarations: false }],
      'unicorn/filename-case': ['error', { case: 'kebabCase' }],
      '@typescript-eslint/naming-convention': ['error',
        { selector: 'typeLike', format: ['PascalCase'], custom: { regex: VAGUE_NAMES, match: false } },
        { selector: 'function', format: ['camelCase', 'PascalCase'], custom: { regex: VAGUE_NAMES, match: false } },
        { selector: 'variable', format: ['camelCase', 'PascalCase', 'UPPER_CASE'] },
      ],
      'no-restricted-properties': ['error', { object: 'console', property: 'log', message: 'Use the shared logger.' }],
      // F7 type safety and Vue correctness (the Vue analogue of React's rules-of-hooks)
      '@typescript-eslint/no-explicit-any': 'error',
      'vue/block-lang': ['error', { script: { lang: 'ts' } }],
      'vue/component-api-style': ['error', ['script-setup']],
      'vue/define-props-declaration': ['error', 'type-based'],
      'vue/define-emits-declaration': ['error', 'type-based'],
      'vue/no-setup-props-reactivity-loss': 'error',
      'vue/no-ref-as-operand': 'error',
      'vue/no-mutating-props': 'error',
      'vue/enforce-style-attribute': ['error', { allow: ['scoped'] }],
      // F11 no raw HTML (SEC-008)
      'vue/no-v-html': 'error',
      'vue/no-restricted-v-bind': ['error', { argument: 'innerHTML', message: 'Render text, not HTML (SEC-008).' }],
    },
  },
  {
    // F6 SFC file names are the component names: PascalCase, multi-word, no vague suffixes (vague suffix: scripts/check-structure.mjs)
    files: ['src/**/*.vue'],
    rules: { 'unicorn/filename-case': ['error', { case: 'pascalCase' }], 'vue/multi-word-component-names': 'error' },
  },
  {
    // F8 composables are named useXxx and live in composables/; Pinia stores are useXxxStore in stores/
    files: ['src/modules/*/composables/**/*.ts'],
    rules: { '@typescript-eslint/naming-convention': ['error', { selector: 'function', modifiers: ['exported'], format: null, custom: { regex: '^use[A-Z][A-Za-z0-9]*$', match: true } }] },
  },
  {
    files: ['src/modules/*/stores/**/*.ts'],
    rules: { '@typescript-eslint/naming-convention': ['error', { selector: 'variable', modifiers: ['exported'], format: null, custom: { regex: '^use[A-Z][A-Za-z0-9]*Store$', match: true } }] },
  },
  {
    // F10 only modules/<m>/api may import the generated client / shared/api (one adapter per module)
    files: ['src/**/*.{ts,vue}'],
    ignores: ['src/modules/*/api/**', 'src/api/generated/**', 'src/shared/api/**'],
    rules: { 'no-restricted-imports': ['error', { paths: [AXIOS], patterns: [GENERATED_ONLY_IN_API] }] },
  },
  {
    // F14 design system: business modules style only through shared/ui components and semantic tokens
    files: ['src/modules/**/*.{ts,vue}', 'src/app/**/*.{ts,vue}'],
    ignores: ['src/modules/*/api/**'],
    rules: {
      'no-restricted-imports': ['error', {
        paths: [AXIOS, { name: 'reka-ui', message: 'Use the design-system components in @/shared/ui (they wrap reka-ui with the FORCITIS one tokens).' }],
        patterns: [
          GENERATED_ONLY_IN_API,
          { group: ['reka-ui/*', 'radix-vue', 'radix-vue/*'], message: 'Use the design-system components in @/shared/ui.' },
          { group: ['**/design-system/tokens.css'], message: 'tokens.css is loaded once by shared/design-system/index.ts (imported from app/main.ts).' },
        ],
      }],
      'no-restricted-syntax': ['error', ...BASE_SYNTAX, ...COLOUR_SYNTAX],
      ...NO_INLINE_STYLE,
    },
  },
  {
    // F14 shared/ui references semantic tokens only (it may import reka-ui, it may not use palette primitives or raw colours)
    files: ['src/shared/ui/**/*.{ts,vue}'],
    rules: { 'no-restricted-syntax': ['error', ...BASE_SYNTAX, ...COLOUR_SYNTAX], ...NO_INLINE_STYLE },
  },
  {
    // F9 model = pure types and rules: no Vue, no Pinia, no Query, no IO (mirrors backend domain).
    // Placed AFTER the F14 block on purpose: a later block's no-restricted-imports replaces an earlier one, so it repeats F14's bans.
    files: ['src/modules/*/model/**/*.ts', 'src/shared/lib/**/*.ts'],
    rules: { 'no-restricted-imports': ['error', { paths: [AXIOS], patterns: [GENERATED_ONLY_IN_API, { group: ['vue', 'pinia', '@tanstack/*', 'reka-ui', '@/api/*', '**/api/*'], message: 'model/ and shared/lib are pure: no Vue, Pinia, Query or API calls. Put reactive code in composables/.' }, { group: ['**/design-system/tokens.css'], message: 'tokens.css is loaded once by shared/design-system/index.ts.' }] }] },
  },
  {
    // Generated code and the HTTP adapter: the only places allowed to call fetch / spell API URLs; generated is never edited by hand.
    files: ['src/api/generated/**', 'src/shared/api/**'],
    rules: { 'no-restricted-globals': 'off', 'no-restricted-syntax': 'off' },
  },
);
