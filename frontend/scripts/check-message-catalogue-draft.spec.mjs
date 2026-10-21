// 草案不进门禁；运行：node --test frontend/scripts/check-message-catalogue-draft.spec.mjs；由 one-quality 接门禁。
import assert from 'node:assert/strict';
import test from 'node:test';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { checkCatalogue, parsePattern } from './check-message-catalogue-draft.mjs';

const key = 'cost.timesheet.timesheet.submit.succeeded';
function fixture(text = '{noun:opaque_01}已{verb:V001}') {
  return {
    messages: { [key]: { text, status: 'approved', note: 'N8 测试夹具' } },
    usedKeys: [key],
    concepts: [{ id: 'opaque_01', kind: 'aggregate', status: 'approved', designations: [
      { locale: 'zh-CN', role: 'canonical', name: '工时表', usage_status: 'preferred', approval_status: 'approved' },
      { locale: 'en', role: 'code_name', name: 'Timesheet', usage_status: 'preferred', approval_status: 'approved' },
      { locale: 'zh-CN', role: 'canonical', name: '工单', usage_status: 'forbidden', approval_status: 'approved' },
    ] }],
    verbs: [{ id: 'V001', zh: '提交', status: 'approved' }],
  };
}
const rules = (input) => checkCatalogue(input).problems.map((p) => p.rule);

test('NAM-1017-GLO-09：解析多个、重复占位符与中文片段', () => {
  assert.deepEqual(parsePattern('{noun:opaque_01}已{verb:V001}，{noun:opaque_01}'), [
    { type: 'noun', id: 'opaque_01' }, { type: 'literal', value: '已' },
    { type: 'verb', id: 'V001' }, { type: 'literal', value: '，' }, { type: 'noun', id: 'opaque_01' },
  ]);
  assert.deepEqual(checkCatalogue(fixture()), { problems: [], warnings: [] });
});

test('NAM-1017-GLO-09：破损、未知类型与暂不支持的 ICU 模式失败', () => {
  for (const text of ['', '{noun:}', '{noun:x', 'x}', '{{noun:x}}', '${noun:x}', '{value:x}', '{count, plural, one {一}}']) {
    assert.throws(() => parsePattern(text), Error);
    assert.ok(rules(fixture(text)).includes('pattern'));
  }
});

test('NAM-1017-GLO-09：缺键失败，继承属性不能伪装成登记键', () => {
  const input = fixture();
  input.usedKeys.push('kernel.document.version.conflict', 'toString');
  assert.deepEqual(rules(input), ['missing_key', 'missing_key']);
});

test('NAM-1017-GLO-09：资源 kebab、kernel 前缀与点分小写用途', () => {
  for (const valid of ['kernel.document.version.conflict', 'delivery.execution.work-package.save.failed']) {
    const input = fixture();
    input.messages = { [valid]: { text: '请重试', status: 'approved' } };
    input.usedKeys = [valid];
    assert.deepEqual(rules(input), []);
  }
  for (const invalid of ['cost.timesheet.timesheet.submitSucceeded', 'cost.timesheet.work_package.save', 'kernel.document']) {
    const input = fixture();
    input.messages = { [invalid]: { text: '请重试', status: 'approved' } };
    input.usedKeys = [invalid];
    assert.deepEqual(rules(input), ['key_format']);
  }
});

test('NAM-1017-GLO-09：中文、英文代码名和禁用名不得写成消息字面量', () => {
  for (const name of ['工时表', 'Timesheet', '工单']) {
    assert.deepEqual(rules(fixture(`${name}已提交`)), ['literal_noun']);
  }
  assert.deepEqual(rules(fixture('请{verb:V001}{noun:opaque_01}')), []);
});

test('NAM-1017-GLO-09：未知概念、未知动词及错误 V 编号失败', () => {
  assert.deepEqual(rules(fixture('{noun:missing}')), ['noun_reference']);
  assert.deepEqual(rules(fixture('{verb:V999}')), ['verb_reference']);
  assert.deepEqual(rules(fixture('{verb:submit}')), ['verb_reference']);
});

test('NAM-1017-GLO-09：缺首选、待确认、重复首选和冲突禁止引用', () => {
  for (const mode of ['missing', 'pending', 'duplicate', 'conflict', 'retired']) {
    const input = fixture();
    const concept = input.concepts[0];
    if (mode === 'missing') concept.designations.shift();
    if (mode === 'pending') concept.designations[0].approval_status = 'pending_confirmation';
    if (mode === 'duplicate') concept.designations.push({ ...concept.designations[0] });
    if (mode === 'conflict') concept.designations.push({ ...concept.designations[0], usage_status: 'forbidden' });
    if (mode === 'retired') concept.status = 'retired';
    assert.deepEqual(rules(input), ['noun_reference']);
  }
});

test('NAM-1017-GLO-09：代码角色与英文界面角色不能代替中文规范名', () => {
  const input = fixture();
  input.concepts[0].designations = [{ locale: 'en', role: 'ui_label', name: 'User label', usage_status: 'preferred', approval_status: 'approved' }];
  assert.deepEqual(rules(input), ['noun_reference']);
});

test('NAM-1017-GLO-09：消息与动词无效状态失败，提议可用但提醒', () => {
  const input = fixture();
  input.messages[key].status = 'retired';
  assert.deepEqual(rules(input), ['retired']);
  input.messages[key].status = 'pending_confirmation';
  assert.deepEqual(rules(input), ['status']);
  input.messages[key].status = 'proposed';
  input.verbs[0].status = 'proposed';
  assert.deepEqual(rules(input), []);
  assert.equal(checkCatalogue(input).warnings.length, 2);
  input.verbs[0].status = 'retired';
  assert.deepEqual(rules(input), ['verb_reference']);
});

test('NAM-1017-GLO-09：缺输入和重复概念 id 失败', () => {
  assert.throws(() => checkCatalogue({}), Error);
  const input = fixture();
  input.concepts.push(structuredClone(input.concepts[0]));
  assert.throws(() => checkCatalogue(input), /重复/);
});

test('NAM-1017-GLO-09：CLI 正样例退出 0，缺键和无效 JSON 退出 1', () => {
  const directory = mkdtempSync(join(tmpdir(), 'n8-messages-'));
  try {
    const path = join(directory, 'input.json');
    const run = () => spawnSync(process.execPath, [new URL('./check-message-catalogue-draft.mjs', import.meta.url).pathname, path], { encoding: 'utf8' });
    writeFileSync(path, JSON.stringify(fixture()));
    assert.equal(run().status, 0);
    const invalid = fixture();
    invalid.usedKeys.push('kernel.document.missing');
    writeFileSync(path, JSON.stringify(invalid));
    const result = run();
    assert.equal(result.status, 1);
    assert.match(result.stderr, /missing_key/);
    writeFileSync(path, '{');
    assert.equal(run().status, 1);
  } finally {
    rmSync(directory, { recursive: true });
  }
});


test('NAM-1017-GLO-09 M1：草案文件不匹配现行 test:scripts 通配', () => {
  assert.equal(/\.test\.mjs$/.test(new URL(import.meta.url).pathname), false);
});

test('NAM-1017-GLO-09 L1：大小写与零宽字符不能绕过名词检查', () => {
  for (const name of ['TIMESHEET', 'timesheet', 'Time\u200bsheet', '工\u200d时表']) {
    assert.deepEqual(rules(fixture(`${name}已提交`)), ['literal_noun']);
  }
});

test('NAM-1017-GLO-09 L1：短语空白规范化后匹配', () => {
  const input = fixture('WORK\t \nPACKAGE 已提交');
  input.concepts[0].designations.push({ name: 'Work Package' });
  assert.deepEqual(rules(input), ['literal_noun']);
  assert.deepEqual(rules(fixture('工 时\t表已提交')), ['literal_noun']);
});

test('NAM-1017-GLO-09 L1：英文词边界不命中较长词内部', () => {
  for (const text of ['Timesheets', 'OldTimesheet', 'timesheet2', 'timesheet_value']) {
    assert.deepEqual(rules(fixture(text)), []);
  }
  assert.deepEqual(rules(fixture('请提交 (TIMESHEET)。')), ['literal_noun']);
});

test('NAM-1017-GLO-09 L1：单字名词不误报表单，独立出现仍失败', () => {
  const input = fixture('表单已提交');
  input.concepts[0].designations.push({ name: '单' });
  assert.deepEqual(rules(input), []);
  input.messages[key].text = '请提交「单」';
  assert.deepEqual(rules(input), ['literal_noun']);
});

test('NAM-1017-GLO-09 L8：消息键各槽位允许字母后的数字', () => {
  for (const valid of ['platform.h5.document-v2.save.v2', 'kernel.h5.open.v2', 'cost2.timesheet2.timesheet2.submit2']) {
    const input = fixture();
    input.messages = { [valid]: { text: '请重试', status: 'approved' } };
    input.usedKeys = [valid];
    assert.deepEqual(rules(input), []);
  }
  for (const invalid of ['platform.5h.document.save', 'kernel.2document.save', 'kernel.document.2save']) {
    const input = fixture();
    input.messages = { [invalid]: { text: '请重试', status: 'approved' } };
    input.usedKeys = [invalid];
    assert.deepEqual(rules(input), ['key_format']);
  }
});
