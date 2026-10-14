// Proves the terminology guard fails when it should. Run by `pnpm test:scripts` (part of `pnpm verify`).
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { loadVocabulary, problemsOfText, scan, sourceFiles, textsOf } from './copy-glossary.mjs';

const frontend = join(dirname(fileURLToPath(import.meta.url)), '..');
const repository = join(frontend, '..');
const glossary = loadVocabulary({
  officialTerms: join(repository, 'docs', 'modernization', 'glossary', 'terms.yaml'),
  backendTerms: join(repository, 'backend', 'glossary', 'terms.yaml'),
  proposedDirectory: join(repository, 'backend', 'glossary', 'proposed'),
  allowList: join(frontend, 'scripts', 'copy-allow-list.yaml'),
});

const kinds = (text) => problemsOfText(text, glossary).map((problem) => `${problem.kind}:${problem.word}`);

test('the glossary and the allow list are well formed', () => {
  assert.deepEqual(glossary.problems, []);
});

test('registered terms, proposed terms and allowed plain words pass', () => {
  assert.deepEqual(kinds('工时单'), []);
  assert.deepEqual(kinds('待办'), []);
  assert.deepEqual(kinds('老系统'), []);
  for (const proposed of ['新系统', '组织浏览', '当前节点', '触发方式', '被中止']) assert.deepEqual(kinds(proposed), [], proposed);
  for (const plain of ['确定', '取消', '加载中']) assert.deepEqual(kinds(plain), [], plain);
});

test('a business word that is not registered fails, with the word named', () => {
  assert.deepEqual(kinds('锦鲤积分'), ['unregistered:锦鲤积分']);
  assert.deepEqual(kinds('确定后查看锦鲤积分'), ['unregistered:锦鲤积分']);
});

test('a registered term next to an unregistered word still names only the unregistered word', () => {
  assert.deepEqual(kinds('工时单的幸运值'), ['unregistered:幸运值']);
});

test('a dialect of a registered term fails, a registered term that contains the dialect does not', () => {
  assert.deepEqual(kinds('销售合同'), ['dialect:销售合同']);
  assert.deepEqual(kinds('合同'), []);
  assert.deepEqual(kinds('签证'), ['dialect:签证']);
  assert.deepEqual(kinds('现场签证'), []);
});

test('UI text positions are all read: JSX text, button, title, placeholder, aria-label, template, message table', () => {
  const source = `
    export const labels = { empty: '锦鲤一', note: \`锦鲤二\${1}\` };
    export const el = (
      <section title="锦鲤三" aria-label="锦鲤四">
        <button>锦鲤五</button>
        <input placeholder="锦鲤六" />
        锦鲤七
      </section>
    );
  `;
  const words = textsOf(source, 'probe.tsx').map((entry) => entry.text.trim());
  for (const expected of ['锦鲤一', '锦鲤二', '锦鲤三', '锦鲤四', '锦鲤五', '锦鲤六', '锦鲤七']) assert.ok(words.includes(expected), `${expected} not read; got ${words.join(' | ')}`);
});

test('comments and English text are not user-facing copy', () => {
  assert.deepEqual(textsOf('// 锦鲤注释\n/* 锦鲤块 */\nexport const x = "Save";', 'probe.ts'), []);
});

test('injection: a source file with an unregistered word, a dialect and a plain word is reported line by line', () => {
  const root = mkdtempSync(join(tmpdir(), 'copy-glossary-'));
  mkdirSync(join(root, 'modules'));
  const file = join(root, 'modules', 'probe-page.tsx');
  writeFileSync(file, 'export const a = <button>锦鲤积分</button>;\nexport const b = <p aria-label="销售合同">确定</p>;\n');
  const failures = scan(sourceFiles([root]), glossary);
  assert.deepEqual(failures.map((failure) => `${failure.line}:${failure.kind}:${failure.word}`), ['1:unregistered:锦鲤积分', '2:dialect:销售合同']);
});

test('test files and test helpers are not scanned', () => {
  const root = mkdtempSync(join(tmpdir(), 'copy-glossary-'));
  mkdirSync(join(root, 'testing'));
  writeFileSync(join(root, 'page.test.tsx'), 'export const a = "锦鲤积分";');
  writeFileSync(join(root, 'testing', 'fixtures.ts'), 'export const a = "锦鲤积分";');
  assert.deepEqual(sourceFiles([root]), []);
});

test('the command line exits 1 on an unregistered word and 0 on clean copy', () => {
  const root = mkdtempSync(join(tmpdir(), 'copy-glossary-'));
  const run = (name, content) => {
    writeFileSync(join(root, name), content);
    return spawnSync(process.execPath, [join(frontend, 'scripts', 'check-copy-glossary.mjs'), '--root', root], { encoding: 'utf8' });
  };
  const failing = run('probe.tsx', 'export const a = <button>锦鲤积分</button>;');
  assert.equal(failing.status, 1);
  assert.match(failing.stderr, /锦鲤积分/);
  const clean = run('probe.tsx', 'export const a = <button>确定</button>;');
  assert.equal(clean.status, 0, clean.stderr);
});
