// Terminology guard for what users read (JSX text, button / title / notice / placeholder / aria-label strings, message tables).
// Every Chinese text in production source must be made of registered terms (docs/modernization/glossary/terms.yaml, backend/glossary/terms.yaml,
// backend/glossary/proposed/*.yaml) and the allow list of non-business words (scripts/copy-allow-list.yaml); a forbidden dialect fails outright.
// Tests, test helpers and generated code are not user-facing and are skipped.
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { join, relative } from 'node:path';
import ts from 'typescript';
import { parse } from 'yaml';

const CHINESE = /[一-鿿]+/g;
const HAS_CHINESE = /[一-鿿]/;
const SKIPPED_FILE = /(\.test\.tsx?|\.d\.ts)$/;
const SKIPPED_DIRECTORY = new Set(['node_modules', 'dist', 'generated', 'testing', 'test']);

function readYaml(path) {
  if (!existsSync(path)) throw new Error(`copy glossary: ${path} is missing. Without the vocabulary nothing can be checked (G-1).`);
  return parse(readFileSync(path, 'utf8')) ?? {};
}

function chineseChunks(text) {
  return String(text).match(CHINESE) ?? [];
}

/** Terms from the three glossary sources, plus the allow list. `vocabulary` holds every Chinese word a text may be built from. */
export function loadVocabulary({ officialTerms, backendTerms, proposedDirectory, allowList }) {
  const terms = [];
  const problems = [];
  for (const path of [officialTerms, backendTerms]) {
    for (const term of readYaml(path).terms ?? []) terms.push({ ...term, source: relative(process.cwd(), path) });
  }
  if (!existsSync(proposedDirectory)) throw new Error(`copy glossary: ${proposedDirectory} is missing (G-1).`);
  for (const file of readdirSync(proposedDirectory).filter((name) => name.endsWith('.yaml')).sort()) {
    for (const term of readYaml(join(proposedDirectory, file)).terms ?? []) terms.push({ ...term, source: `backend/glossary/proposed/${file}` });
  }

  const vocabulary = new Set();
  for (const term of terms) {
    if (typeof term.zh !== 'string' || term.zh === '') {
      problems.push(`${term.id}: a term without zh`);
      continue;
    }
    chineseChunks(term.zh).forEach((chunk) => vocabulary.add(chunk));
  }

  const allowed = readYaml(allowList).words ?? [];
  const registeredWords = new Set(vocabulary);
  for (const word of allowed) {
    if (typeof word !== 'string' || !/^[一-鿿]+$/.test(word)) problems.push(`allow list: "${word}" must be Chinese characters only`);
    else if (registeredWords.has(word)) problems.push(`allow list: "${word}" is already a registered term; remove it from the allow list`);
    else vocabulary.add(word);
  }

  const termZh = new Set(terms.map((term) => term.zh));
  const forbidden = [];
  for (const term of terms) {
    const phrases = [
      ...(term.forbidden ?? []),
      ...(term.aliases ?? []).filter((alias) => typeof alias === 'object' && alias !== null && alias.type === 'dialect').map((alias) => alias.name),
    ];
    for (const phrase of phrases) {
      if (typeof phrase !== 'string' || !HAS_CHINESE.test(phrase)) continue;
      if (!termZh.has(phrase)) forbidden.push({ phrase, term: term.zh, id: term.id });
    }
  }
  return { vocabulary, forbidden, termZh: [...termZh], problems, longest: Math.max(1, ...[...vocabulary].map((word) => word.length)) };
}

/** Smallest set of uncovered pieces when `run` is cut into vocabulary words (dynamic programming over positions). */
function uncoveredPieces(run, { vocabulary, longest }) {
  const best = Array.from({ length: run.length + 1 }, () => ({ missing: Number.POSITIVE_INFINITY, from: -1, covered: false }));
  best[0] = { missing: 0, from: -1, covered: true };
  for (let end = 1; end <= run.length; end += 1) {
    best[end] = { missing: best[end - 1].missing + 1, from: end - 1, covered: false };
    for (let start = Math.max(0, end - longest); start < end; start += 1) {
      if (vocabulary.has(run.slice(start, end)) && best[start].missing < best[end].missing) best[end] = { missing: best[start].missing, from: start, covered: true };
    }
  }
  const pieces = [];
  let end = run.length;
  let open = '';
  while (end > 0) {
    const step = best[end];
    const piece = run.slice(step.from, end);
    if (step.covered) {
      if (open !== '') pieces.unshift(open);
      open = '';
    } else {
      open = piece + open;
    }
    end = step.from;
  }
  if (open !== '') pieces.unshift(open);
  return pieces;
}

/** Problems of one user-visible text: Chinese pieces no registered term or allowed word explains, and forbidden dialects. */
export function problemsOfText(text, glossary) {
  const problems = [];
  const termSpans = [];
  for (const zh of glossary.termZh) {
    for (let at = text.indexOf(zh); at >= 0; at = text.indexOf(zh, at + 1)) termSpans.push([at, at + zh.length]);
  }
  let remaining = text;
  const reported = new Set();
  for (const { phrase, term } of glossary.forbidden) {
    for (let at = text.indexOf(phrase); at >= 0; at = text.indexOf(phrase, at + 1)) {
      const covered = termSpans.some(([from, to]) => from <= at && at + phrase.length <= to && to - from > phrase.length);
      if (covered) continue;
      remaining = `${remaining.slice(0, at)}${' '.repeat(phrase.length)}${remaining.slice(at + phrase.length)}`;
      if (!reported.has(phrase)) problems.push({ kind: 'dialect', word: phrase, hint: `"${phrase}" is a dialect of the registered term "${term}"; write "${term}"` });
      reported.add(phrase);
    }
  }
  for (const run of chineseChunks(remaining)) {
    for (const piece of uncoveredPieces(run, glossary)) {
      problems.push({ kind: 'unregistered', word: piece, hint: `"${piece}" is neither a registered term nor on the allow list` });
    }
  }
  return problems;
}

/** Every string literal, template chunk and JSX text of a source file that contains Chinese, with its line. */
export function textsOf(source, fileName) {
  const file = ts.createSourceFile(fileName, source, ts.ScriptTarget.Latest, true, fileName.endsWith('x') ? ts.ScriptKind.TSX : ts.ScriptKind.TS);
  const texts = [];
  const visit = (node) => {
    const isText = ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node) || ts.isTemplateHead(node) || ts.isTemplateMiddle(node) || ts.isTemplateTail(node) || ts.isJsxText(node);
    const inImport = ts.isImportDeclaration(node.parent ?? node) || ts.isExportDeclaration(node.parent ?? node);
    if (isText && !inImport) {
      const text = node.text;
      if (HAS_CHINESE.test(text)) texts.push({ text, line: file.getLineAndCharacterOfPosition(node.getStart()).line + 1 });
    }
    ts.forEachChild(node, visit);
  };
  visit(file);
  return texts;
}

export function sourceFiles(roots) {
  const found = [];
  const walk = (directory) => {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      const path = join(directory, entry.name);
      if (entry.isDirectory()) {
        if (!SKIPPED_DIRECTORY.has(entry.name)) walk(path);
      } else if (/\.tsx?$/.test(entry.name) && !SKIPPED_FILE.test(entry.name)) {
        found.push(path);
      }
    }
  };
  roots.filter((root) => existsSync(root)).forEach(walk);
  return found.sort();
}

export function scan(files, glossary) {
  const failures = [];
  for (const file of files) {
    for (const { text, line } of textsOf(readFileSync(file, 'utf8'), file)) {
      for (const problem of problemsOfText(text, glossary)) failures.push({ file, line, text, ...problem });
    }
  }
  return failures;
}
