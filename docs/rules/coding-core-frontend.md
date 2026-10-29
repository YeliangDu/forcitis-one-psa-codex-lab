---
paths:
  - "frontend/**"
---

# Coding core, frontend — FORCITIS one

Loaded for `frontend/**` via the `.claude/rules` symlink (#860).
CS-x.y is in `docs/rules/coding-standard.md`, which wins on conflict. ESLint, stylelint, `pnpm typecheck` and the `pnpm check:*` scripts catch the rest of CS-4; a failure names the fix.

## Rules no lint catches — follow them

1. Errors reach the user only through `describeFailure` (`@forcitis/core`); never show a server `detail`, an exception `message` or English; never swallow a failure, show an actionable message (CS-4.6, CS-1.7).
2. No non-null assertion `!` to get past nullability; narrow the type instead (CS-4.1).
3. External links go through `safeExternalUrl`, internal redirects through `safeInternalPath` (`packages/core/src/safe-url.ts`; CS-4.8).
4. Instants are shown only through `formatDateTime`, the business day comes only from `businessToday` (`@forcitis/core`); money formatting uses the shared function K-1 delivers (CS-4.10).
5. H5 (`apps/h5`) follows every PC rule even where its lint is not wired yet (CS-4.11).

## Look up by id

Types CS-4.1 · generated client only CS-4.2 · module boundaries CS-4.3 · naming CS-4.4 · UI text from the glossary CS-4.5 · design system CS-4.7 · logger CS-4.9 · `localeCompare('zh')`, `timeZone` CS-4.12 · UI rules (brand, contrast, keyboard and ARIA, language, three states, no optimistic updates, budgets) `docs/rules/naming-standard.md` §4a U1–U8.
