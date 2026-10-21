// 草案不进门禁；测试：node --test frontend/scripts/check-message-catalogue-draft.spec.mjs；由 one-quality 接门禁。
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

const KEY = /^(?:[a-z][a-z0-9]*\.[a-z][a-z0-9]*|kernel)\.[a-z][a-z0-9]*(?:-[a-z][a-z0-9]*)*\.[a-z][a-z0-9]*(?:\.[a-z][a-z0-9]*)*$/;
const STATUSES = new Set(['proposed', 'approved', 'deprecated', 'retired']);
const NON_NOUN_KINDS = new Set(['command', 'domain_event']);
const SEGMENTER = new Intl.Segmenter('zh-CN', { granularity: 'word' });

function normalizeLiteral(value) {
  return value.normalize('NFKC').toLowerCase().replace(/\p{Cf}/gu, '')
    .replace(/\s+/gu, ' ').replace(/(?<=\p{Script=Han}) +(?=\p{Script=Han})/gu, '').trim();
}

function containsNoun(text, noun) {
  text = normalizeLiteral(text);
  noun = normalizeLiteral(noun);
  if (!noun) return false;
  const boundaries = new Set([text.length]);
  for (const segment of SEGMENTER.segment(text)) boundaries.add(segment.index);
  for (let at = text.indexOf(noun); at >= 0; at = text.indexOf(noun, at + 1)) {
    const end = at + noun.length;
    if (!boundaries.has(at) || !boundaries.has(end)) continue;
    // 中文分词可能把「表单」拆为「表 / 单」，单字只按完整文字边界接受。
    if (Array.from(noun).length === 1) {
      const before = Array.from(text.slice(0, at)).at(-1) ?? '';
      const after = Array.from(text.slice(end))[0] ?? '';
      if (/[\p{L}\p{N}_]/u.test(before) || /[\p{L}\p{N}_]/u.test(after)) continue;
    }
    return true;
  }
  return false;
}

// 仅实现 N3 的两个类型化占位符；ICU plural/select 等语法必须由后续正式解析器承接。
export function parsePattern(text) {
  if (typeof text !== 'string' || text.trim() === '') throw new Error('消息 text 须为非空字符串');
  const tokens = [];
  let start = 0;
  while (start < text.length) {
    const brace = text.slice(start).search(/[{}]/);
    if (brace < 0) {
      tokens.push({ type: 'literal', value: text.slice(start) });
      break;
    }
    const at = start + brace;
    if (at > start) tokens.push({ type: 'literal', value: text.slice(start, at) });
    const match = /^\{(noun|verb):([A-Za-z0-9_][A-Za-z0-9_.-]*)\}/.exec(text.slice(at));
    if (!match || (at > 0 && text[at - 1] === '$')) throw new Error(`第 ${at + 1} 位：无效或草案尚不支持的占位符`);
    tokens.push({ type: match[1], id: match[2] });
    start = at + match[0].length;
  }
  return tokens;
}

function indexById(rows, name) {
  if (!Array.isArray(rows)) throw new Error(`${name} 须为数组`);
  const index = new Map();
  for (const row of rows) {
    if (!row || typeof row.id !== 'string' || !row.id || index.has(row.id)) throw new Error(`${name} 的 id 缺失或重复`);
    index.set(row.id, row);
  }
  return index;
}

function canonicalName(concept) {
  if (!Array.isArray(concept.designations) || !STATUSES.has(concept.status) || concept.status === 'retired') {
    throw new Error('概念无指称、状态无效或已退役');
  }
  const candidates = concept.designations.filter((d) =>
    d.locale === 'zh-CN' && d.role === 'canonical' && d.usage_status === 'preferred');
  if (candidates.length !== 1) throw new Error('须恰有一个 zh-CN/canonical 首选指称');
  const chosen = candidates[0];
  if (typeof chosen.name !== 'string' || chosen.name.trim() === '' ||
      !['approved', 'proposed'].includes(chosen.approval_status) ||
      concept.designations.some((d) => d.name === chosen.name &&
        (d.usage_status === 'forbidden' || d.approval_status === 'pending_confirmation'))) {
    throw new Error('首选指称无效、禁用或待确认');
  }
  return chosen;
}

function checkToken(token, key, { nouns, actions, literals }, problems, warnings) {
  if (token.type === 'literal') {
    for (const noun of literals) {
      if (containsNoun(token.value, noun)) problems.push({ key, rule: 'literal_noun', detail: `业务名词「${noun}」须改用 {noun:<id>}` });
    }
  } else if (token.type === 'noun') {
    try {
      const concept = nouns.get(token.id);
      if (!concept || NON_NOUN_KINDS.has(concept.kind)) throw new Error('名词概念未登记或类别不符');
      const designation = canonicalName(concept);
      if (concept.status !== 'approved' || designation.approval_status === 'proposed') {
        warnings.push({ key, rule: 'noun_status', detail: `${token.id} 为提议或弃用` });
      }
    } catch (error) {
      problems.push({ key, rule: 'noun_reference', detail: `${token.id}：${error.message}` });
    }
  } else {
    const verb = actions.get(token.id);
    if (!/^V\d{3}$/.test(token.id) || !verb || !['approved', 'proposed', 'deprecated'].includes(verb.status) ||
        typeof verb.zh !== 'string' || verb.zh.trim() === '') {
      problems.push({ key, rule: 'verb_reference', detail: `${token.id}：动词缺失、无中文或不可用` });
    } else if (verb.status !== 'approved') {
      warnings.push({ key, rule: 'verb_status', detail: `${token.id} 为提议或弃用` });
    }
  }
}

export function checkCatalogue({ messages, concepts, verbs, usedKeys }) {
  if (!messages || typeof messages !== 'object' || Array.isArray(messages)) throw new Error('messages 须为键到消息的映射');
  if (!Array.isArray(usedKeys) || usedKeys.some((key) => typeof key !== 'string')) throw new Error('使用点键清单须为字符串数组');
  const nouns = indexById(concepts, 'concepts');
  const actions = indexById(verbs, 'verbs');
  const problems = [];
  const warnings = [];
  const literals = new Set();
  for (const concept of nouns.values()) {
    if (!Array.isArray(concept.designations)) throw new Error(`${concept.id} 缺少指称列表`);
    if (NON_NOUN_KINDS.has(concept.kind)) continue;
    for (const designation of concept.designations) {
      if (typeof designation.name === 'string' && designation.name !== '') literals.add(designation.name);
    }
  }
  for (const key of usedKeys) {
    if (!Object.hasOwn(messages, key)) problems.push({ key, rule: 'missing_key', detail: '使用的消息键未登记' });
  }
  for (const [key, message] of Object.entries(messages)) {
    if (!KEY.test(key)) problems.push({ key, rule: 'key_format', detail: '资源用单数 kebab，用途用点分小写' });
    if (!message || !STATUSES.has(message.status)) {
      problems.push({ key, rule: 'status', detail: '消息状态缺失或无效' });
      continue;
    }
    if (usedKeys.includes(key) && message.status === 'retired') {
      problems.push({ key, rule: 'retired', detail: '已退役消息禁止引用' });
    } else if (usedKeys.includes(key) && message.status !== 'approved') {
      warnings.push({ key, rule: message.status, detail: '提议或弃用消息的构建提醒' });
    }
    let tokens;
    try {
      tokens = parsePattern(message.text);
    } catch (error) {
      problems.push({ key, rule: 'pattern', detail: error.message });
      continue;
    }
    for (const token of tokens) checkToken(token, key, { nouns, actions, literals }, problems, warnings);
  }
  return { problems, warnings };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    if (process.argv.length !== 3) throw new Error('用法：node scripts/check-message-catalogue-draft.mjs <JSON 夹具文件>');
    const result = checkCatalogue(JSON.parse(readFileSync(process.argv[2], 'utf8')));
    for (const problem of result.problems) console.error(`${problem.key} ${problem.rule}：${problem.detail}`);
    for (const warning of result.warnings) console.warn(`${warning.key}：${warning.detail}`);
    process.exitCode = result.problems.length ? 1 : 0;
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
