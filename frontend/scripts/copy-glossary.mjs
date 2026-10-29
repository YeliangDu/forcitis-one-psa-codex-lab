// Terminology guard for what users read (JSX text, button / title / notice / placeholder / aria-label strings, message tables).
// Every Chinese text in production source must be made of registered terms (docs/glossary/terms.yaml and the attributes listed under a term,
// backend/glossary/terms.yaml, backend/glossary/proposed/*.yaml), the names in the context register (docs/glossary/bcs.yaml: the zh of every
// bc / subdomain / system and the `words` a bc lists for business words it describes without a term of their own) and the allow list of non-business words (scripts/copy-allow-list.yaml); a forbidden dialect fails outright.
// A single-character allowed word (的, 和, 已 ...) is only a connector: it must sit between two words, never start, end or stand next to another one,
// otherwise any Chinese text could be spelled from connectors and registered fragments. A state such as 已认领 is registered as a term, not assembled.
// Tests, test helpers and generated code are not user-facing and are skipped.
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import ts from 'typescript';
import { parse } from 'yaml';

const CHINESE = /[一-鿿]+/g;
const HAS_CHINESE = /[一-鿿]/;
const SKIPPED_FILE = /(\.test\.tsx?|\.d\.ts)$/;
const SKIPPED_DIRECTORY = new Set(['node_modules', 'dist', 'generated']);
/** Test helper folders, only at the place the layout puts them (relative to a scanned source root); a production folder that happens to be called testing is scanned. */
const TEST_SUPPORT_DIRECTORIES = new Set(['test', 'testing', 'shared/testing']);

function readYaml(path) {
  if (!existsSync(path)) throw new Error(`copy glossary: ${path} is missing. Without the vocabulary nothing can be checked (G-1).`);
  return parse(readFileSync(path, 'utf8')) ?? {};
}

function chineseChunks(text) {
  return String(text).match(CHINESE) ?? [];
}

/** Terms from the three glossary sources, plus the allow list. `vocabulary` holds every Chinese word a text may be built from. */
export function loadVocabulary({ officialTerms, backendTerms, proposedDirectory, allowList, contexts }) {
  if (contexts === undefined) throw new Error('copy glossary: the context register path is required (G-1).');
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
    for (const attribute of term.attributes ?? []) {
      if (typeof attribute?.zh !== 'string' || attribute.zh === '') problems.push(`${term.id}: an attribute without zh`);
      else chineseChunks(attribute.zh).forEach((chunk) => vocabulary.add(chunk));
    }
  }
  // The context register keeps what is not a term: bc, subdomain and system names, and the business words a bc describes.
  const contextWords = [];
  const describedWords = [];
  const contextEntries = readYaml(contexts).bcs;
  if (!Array.isArray(contextEntries) || contextEntries.length === 0) throw new Error(`copy glossary: ${contexts} has no bcs list (G-1).`);
  for (const context of contextEntries) {
    if (typeof context.zh !== 'string' || !HAS_CHINESE.test(context.zh)) problems.push(`${context.id}: a context register entry without a Chinese name`);
    else {
      chineseChunks(context.zh).forEach((chunk) => vocabulary.add(chunk));
      contextWords.push(context.zh);
    }
    if (context.words === undefined) continue;
    if (!Array.isArray(context.words)) {
      problems.push(`${context.id}: words must be a list`);
      continue;
    }
    for (const word of context.words) {
      if (typeof word !== 'string' || !/^[一-鿿]{2,}$/.test(word)) problems.push(`${context.id}: word "${word}" must be two or more Chinese characters`);
      else {
        vocabulary.add(word);
        contextWords.push(word);
        describedWords.push({ word, id: context.id });
      }
    }
  }

  const allowFile = readYaml(allowList);
  const allowed = allowFile.words ?? [];
  const standalone = new Set(allowFile.standalone ?? []);
  const registeredWords = new Set(vocabulary);
  const connectors = new Set();
  for (const word of allowed) {
    if (typeof word !== 'string' || !/^[一-鿿]+$/.test(word)) problems.push(`allow list: "${word}" must be Chinese characters only`);
    else if (registeredWords.has(word)) problems.push(`allow list: "${word}" is already a registered term; remove it from the allow list`);
    else if (word.length === 1) connectors.add(word);
    else vocabulary.add(word);
  }

  const registeredZh = new Set(terms.map((term) => term.zh));
  const termZh = new Set([...registeredZh, ...contextWords]);
  const forbidden = [];
  const dialectPhrases = new Set();
  for (const term of terms) {
    const phrases = [
      ...(term.forbidden ?? []),
      ...(term.aliases ?? []).filter((alias) => typeof alias === 'object' && alias !== null && alias.type === 'dialect').map((alias) => alias.name),
    ];
    for (const phrase of phrases) {
      if (typeof phrase !== 'string' || !HAS_CHINESE.test(phrase)) continue;
      dialectPhrases.add(phrase);
      if (!termZh.has(phrase)) forbidden.push({ phrase, term: term.zh, id: term.id });
    }
  }
  for (const { word, id } of describedWords) {
    if (registeredZh.has(word)) problems.push(`${id}: word "${word}" is already a registered term; remove it from words`);
    if (dialectPhrases.has(word)) problems.push(`${id}: word "${word}" is a forbidden dialect; words must not whitelist it`);
  }
  return { vocabulary, connectors, standalone, forbidden, termZh: [...termZh], problems, longest: Math.max(1, ...[...vocabulary].map((word) => word.length)) };
}

const START = 0;
const GAP = 1;
const WORD = 2;
const CONNECTOR = 3;
const STATES = [START, GAP, WORD, CONNECTOR];

/**
 * Smallest set of uncovered pieces when `run` is cut into vocabulary words and connectors (dynamic programming over position and the kind of the last piece).
 * A connector must have something on both sides: it cannot be the first or last character of a text or sit next to another connector.
 * A neighbour that is itself uncovered counts, because that neighbour is reported anyway.
 */
function uncoveredPieces(run, { vocabulary, connectors, longest }) {
  const unreachable = { missing: Number.POSITIVE_INFINITY };
  const best = Array.from({ length: run.length + 1 }, () => STATES.map(() => unreachable));
  best[0][START] = { missing: 0 };
  const reach = (position, state, missing, from, fromState, covered) => {
    if (missing < best[position][state].missing) best[position][state] = { missing, from, fromState, covered };
  };
  for (let at = 0; at < run.length; at += 1) {
    for (const state of STATES) {
      const here = best[at][state];
      if (here.missing === Number.POSITIVE_INFINITY) continue;
      reach(at + 1, GAP, here.missing + 1, at, state, false);
      for (let end = at + 1; end <= Math.min(run.length, at + longest); end += 1) {
        if (vocabulary.has(run.slice(at, end))) reach(end, WORD, here.missing, at, state, true);
      }
      if ((state === WORD || state === GAP) && connectors.has(run[at])) reach(at + 1, CONNECTOR, here.missing, at, state, true);
    }
  }
  const last = best[run.length];
  let state = [START, GAP, WORD].reduce((winner, candidate) => (last[candidate].missing < last[winner].missing ? candidate : winner));
  const pieces = [];
  let open = '';
  for (let end = run.length; end > 0;) {
    const step = best[end][state];
    const piece = run.slice(step.from, end);
    if (step.covered) {
      if (open !== '') pieces.unshift(open);
      open = '';
    } else {
      open = piece + open;
    }
    end = step.from;
    state = step.fromState;
  }
  if (open !== '') pieces.unshift(open);
  return pieces;
}

/** Problems of one user-visible text: Chinese pieces no registered term or allowed word explains, and forbidden dialects. */
export function problemsOfText(text, glossary) {
  const problems = [];
  if (glossary.standalone.has(text.trim())) return problems;
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
  const walk = (root, directory) => {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      const path = join(directory, entry.name);
      if (entry.isDirectory()) {
        const fromRoot = relative(root, path).split(sep).join('/');
        if (!SKIPPED_DIRECTORY.has(entry.name) && !TEST_SUPPORT_DIRECTORIES.has(fromRoot)) walk(root, path);
      } else if (/\.tsx?$/.test(entry.name) && !SKIPPED_FILE.test(entry.name)) {
        found.push(path);
      }
    }
  };
  roots.filter((root) => existsSync(root)).forEach((root) => walk(root, root));
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
