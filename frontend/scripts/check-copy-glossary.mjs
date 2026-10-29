// Terminology guard for UI copy (see copy-glossary.mjs). Runs in `pnpm verify`.
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadVocabulary, scan, sourceFiles } from './copy-glossary.mjs';

const frontend = join(dirname(fileURLToPath(import.meta.url)), '..');
const repository = join(frontend, '..');
const DEFAULT_ROOTS = ['apps/web/src', 'apps/h5/src', 'packages/core/src', 'packages/ui/src', 'packages/design-system/src'].map((root) => join(frontend, root));
const given = process.argv.flatMap((argument, index, all) => (argument === '--root' && all[index + 1] !== undefined ? [all[index + 1]] : []));
const ROOTS = given.length > 0 ? given : DEFAULT_ROOTS;

const glossary = loadVocabulary({
  officialTerms: join(repository, 'docs', 'glossary', 'terms.yaml'),
  backendTerms: join(repository, 'backend', 'glossary', 'terms.yaml'),
  proposedDirectory: join(repository, 'backend', 'glossary', 'proposed'),
  allowList: join(frontend, 'scripts', 'copy-allow-list.yaml'),
  contexts: join(repository, 'docs', 'glossary', 'bcs.yaml'),
});

const files = sourceFiles(ROOTS);
const failures = scan(files, glossary);
for (const problem of glossary.problems) console.error(`glossary: ${problem}`);
for (const failure of failures) console.error(`${failure.file.replace(`${frontend}/`, '')}:${failure.line}: ${failure.hint} (in "${failure.text.trim().slice(0, 60)}")`);

if (failures.length > 0 || glossary.problems.length > 0) {
  const unknown = [...new Set(failures.filter((failure) => failure.kind === 'unregistered').map((failure) => failure.word))].sort();
  if (unknown.length > 0) {
    console.error(`\nUnregistered Chinese words (${unknown.length}): ${unknown.join('、')}`);
    console.error('A business word: register it in backend/glossary/proposed/frontend-ui.yaml (status: proposed). A plain word with no business meaning: add it to frontend/scripts/copy-allow-list.yaml.');
  }
  process.exit(1);
}
console.log(`copy glossary: ${files.length} files, every Chinese text is built from registered terms and the allow list`);
