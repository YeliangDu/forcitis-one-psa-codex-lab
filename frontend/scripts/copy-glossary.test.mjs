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
  officialTerms: join(repository, 'docs', 'glossary', 'terms.yaml'),
  backendTerms: join(repository, 'backend', 'glossary', 'terms.yaml'),
  proposedDirectory: join(repository, 'backend', 'glossary', 'proposed'),
  allowList: join(frontend, 'scripts', 'copy-allow-list.yaml'),
  contexts: join(repository, 'docs', 'glossary', 'bcs.yaml'),
});

const kinds = (text) => problemsOfText(text, glossary).map((problem) => `${problem.kind}:${problem.word}`);

test('the glossary and the allow list are well formed', () => {
  assert.deepEqual(glossary.problems, []);
});

test('context register names, bc words and term attributes pass, an unlisted word still fails', () => {
  assert.deepEqual(kinds('企业微信扫码登录'), []);
  assert.deepEqual(kinds('老系统的待办'), []);
  assert.deepEqual(kinds('致远单点登录'), []);
  assert.deepEqual(kinds('请确认你的身份'), []);
  assert.deepEqual(kinds('编号容量'), []);
  assert.deepEqual(kinds('单据'), []);
  assert.deepEqual(kinds('某某杜撰词'), ['unregistered:某某杜撰词']);
});

test('registered terms, proposed terms and allowed plain words pass', () => {
  assert.deepEqual(kinds('工时单'), []);
  assert.deepEqual(kinds('待办'), []);
  assert.deepEqual(kinds('老系统'), []);
  for (const proposed of ['新系统', '组织浏览', '当前节点']) assert.deepEqual(kinds(proposed), [], proposed);
  // a proposed term of a throwaway proposed directory passes too, so this case does not depend on which real terms are still proposed
  const proposedDirectory = mkdtempSync(join(tmpdir(), 'copy-glossary-proposed-'));
  writeFileSync(join(proposedDirectory, 'fixture.yaml'), `terms:
- id: fixture_only_term
  zh: 夹具专用词
  en: FixtureOnlyTerm
  snake: fixture_only_term
  kind: value_object
  bc: platform.integration
  scope: acl
  acl_class: "过渡期"
  definition: 只给本测试用的拟议术语
  standard_ref: []
  confidence: low
  aliases: []
  forbidden: []
  status: proposed
  note: test fixture
`);
  const withFixture = loadVocabulary({
    officialTerms: join(repository, 'docs', 'glossary', 'terms.yaml'),
    backendTerms: join(repository, 'backend', 'glossary', 'terms.yaml'),
    proposedDirectory,
    allowList: join(frontend, 'scripts', 'copy-allow-list.yaml'),
    contexts: join(repository, 'docs', 'glossary', 'bcs.yaml'),
  });
  assert.deepEqual(withFixture.problems, []);
  assert.deepEqual(problemsOfText('夹具专用词', withFixture), []);
  assert.notDeepEqual(problemsOfText('夹具专用词', glossary), [], 'the fixture term is not in the real glossary');
  for (const plain of ['确定', '取消', '加载中']) assert.deepEqual(kinds(plain), [], plain);
});

test('a one-character word is only a connector between two words (M-16)', () => {
  assert.deepEqual(kinds('工时单和审批'), []);
  assert.deepEqual(kinds('工时单的审批'), []);
  assert.deepEqual(kinds('已认领'), [], 'a state is a registered term, not 已 + 认领');
  for (const text of ['的', '的审批', '工时单的', '请重试', '工时单的是审批', '已通知']) assert.notDeepEqual(kinds(text), [], text);
});

test('connectors cannot be chained into a text that no term explains', () => {
  assert.notDeepEqual(kinds('工时单的是审批'), []);
  assert.deepEqual(kinds('不是的'), ['unregistered:的']);
  assert.deepEqual(kinds('的的的').length > 0, true);
});

test('only the listed values may be a whole text by themselves', () => {
  assert.deepEqual(kinds('是'), []);
  assert.deepEqual(kinds('否'), []);
  assert.deepEqual(kinds(' 是 '), []);
  assert.notDeepEqual(kinds('是的'), []);
  assert.notDeepEqual(kinds('在'), []);
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

test('a folder called testing is skipped only where the layout puts test helpers, not anywhere in production code', () => {
  const root = mkdtempSync(join(tmpdir(), 'copy-glossary-'));
  mkdirSync(join(root, 'shared', 'testing'), { recursive: true });
  mkdirSync(join(root, 'test'));
  mkdirSync(join(root, 'modules', 'orders', 'testing'), { recursive: true });
  writeFileSync(join(root, 'shared', 'testing', 'fixtures.ts'), 'export const a = "锦鲤积分";');
  writeFileSync(join(root, 'test', 'setup.ts'), 'export const a = "锦鲤积分";');
  writeFileSync(join(root, 'modules', 'orders', 'testing', 'order-page.tsx'), 'export const a = <p>锦鲤积分</p>;');
  assert.deepEqual(sourceFiles([root]).map((file) => file.slice(root.length + 1)), ['modules/orders/testing/order-page.tsx']);
  assert.deepEqual(scan(sourceFiles([root]), glossary).map((failure) => failure.word), ['锦鲤积分']);
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

function vocabularyFrom(contextsYaml, termsYaml = 'terms:\n  - { id: a, zh: 工时单, forbidden: [签证单] }\n') {
  const dir = mkdtempSync(join(tmpdir(), 'copy-contexts-'));
  mkdirSync(join(dir, 'proposed'));
  writeFileSync(join(dir, 'terms.yaml'), termsYaml);
  writeFileSync(join(dir, 'backend.yaml'), 'terms: []\n');
  writeFileSync(join(dir, 'allow.yaml'), 'words: []\n');
  if (contextsYaml !== null) writeFileSync(join(dir, 'contexts.yaml'), contextsYaml);
  return () => loadVocabulary({
    officialTerms: join(dir, 'terms.yaml'),
    backendTerms: join(dir, 'backend.yaml'),
    proposedDirectory: join(dir, 'proposed'),
    allowList: join(dir, 'allow.yaml'),
    contexts: join(dir, 'contexts.yaml'),
  });
}

test('the context register fails loudly when it is missing, empty or malformed', () => {
  assert.throws(vocabularyFrom(null), /is missing/);
  assert.throws(vocabularyFrom('bcs: []\n'), /no bcs list/);
  assert.throws(() => loadVocabulary({ officialTerms: 'x', backendTerms: 'x', proposedDirectory: 'x', allowList: 'x' }), /context register path is required/);
  assert.match(vocabularyFrom('bcs:\n  - { id: a, kind: bc }\n')().problems.join('\n'), /without a Chinese name/);
  assert.match(vocabularyFrom('bcs:\n  - { id: a, zh: 身份, words: 单据 }\n')().problems.join('\n'), /words must be a list/);
  assert.match(vocabularyFrom('bcs:\n  - { id: a, zh: 身份, words: [单] }\n')().problems.join('\n'), /two or more Chinese characters/);
});

test('a bc word may not whitelist a dialect or repeat a registered term; an attribute needs a Chinese name', () => {
  assert.match(vocabularyFrom('bcs:\n  - { id: a, zh: 身份, words: [签证单] }\n')().problems.join('\n'), /forbidden dialect/);
  assert.match(vocabularyFrom('bcs:\n  - { id: a, zh: 身份, words: [工时单] }\n')().problems.join('\n'), /already a registered term/);
  assert.match(vocabularyFrom('bcs:\n  - { id: a, zh: 身份 }\n', 'terms:\n  - { id: a, zh: 工时单, attributes: [{ en: X }] }\n')().problems.join('\n'), /attribute without zh/);
  const ok = vocabularyFrom('bcs:\n  - { id: a, zh: 身份, words: [单据] }\n')();
  assert.deepEqual(ok.problems, []);
  assert.ok(ok.vocabulary.has('单据') && ok.vocabulary.has('身份'));
});
