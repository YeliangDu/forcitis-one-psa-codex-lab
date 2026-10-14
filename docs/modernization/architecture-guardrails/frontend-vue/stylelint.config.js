// F14 (CSS half): colours come only from semantic tokens. tokens.css is the single place palette values live.
const SEMANTIC_ONLY = '/var\\(\\s*--(?!(surface|text|border|intent|status|ledger)-)/';
const PALETTE = '/var\\(\\s*--(slate|red|green|amber|blue|violet|orange|yellow)-\\d/';

export default {
  ignoreFiles: ['src/shared/design-system/tokens.css', 'dist/**', 'node_modules/**'],
  overrides: [{ files: ['**/*.vue'], customSyntax: 'postcss-html' }],
  rules: {
    'color-no-hex': [true, { message: 'No hard-coded colours: use a semantic token, e.g. var(--text-primary).' }],
    'color-named': ['never', { message: 'No named colours: use a semantic token, e.g. var(--text-primary).' }],
    'function-disallowed-list': [['rgb', 'rgba', 'hsl', 'hsla', 'hwb', 'lab', 'lch', 'oklab', 'oklch', 'color', 'color-mix'],
      { message: 'No colour functions outside shared/design-system/tokens.css: use a semantic token.' }],
    'declaration-property-value-disallowed-list': [{ '/.*/': [PALETTE, SEMANTIC_ONLY] },
      { message: 'Only semantic tokens (--surface-*, --text-*, --border-*, --intent-*, --status-*, --ledger-*); palette primitives (--slate-*, --red-* ...) are read only by tokens.css.' }],
  },
};
