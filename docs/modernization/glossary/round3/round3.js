// 数据由 build_round3_decisions.py 注入为 DOC（来自 round3-items.json）。每条预选推荐项；改选与说明存在本机浏览器。
const STORE = "glossary-round3-" + DOC.version;
const $ = s => document.querySelector(s);
const el = (tag, attrs = {}, text) => {
  const e = document.createElement(tag);
  Object.entries(attrs).forEach(([k, v]) => { if (k.includes("-")) e.setAttribute(k, v); else e[k] = v; });
  if (text != null) e.textContent = text;
  return e;
};
function load() { try { return JSON.parse(localStorage.getItem(STORE) || "{}"); } catch { return {}; } }
function save() { try { localStorage.setItem(STORE, JSON.stringify(state)); } catch {} }
let state = load();
const ITEMS = DOC.groups.flatMap(g => g.items.map(i => Object.assign({group: g.title}, i)));
const recOf = i => i.options.findIndex(o => o.recommended);
const choiceOf = i => (state[i.code] && state[i.code].choice != null) ? state[i.code].choice : recOf(i);

function renderJudge(root) {
  const box = el("section", {className: "judge"});
  box.append(el("b", {}, "怎么判"));
  const ol = el("ol");
  DOC.judge.levels.forEach(t => ol.append(el("li", {}, t)));
  box.append(ol);
  const ul = el("ul");
  DOC.judge.marks.forEach(t => ul.append(el("li", {}, t)));
  box.append(ul);
  root.append(box);
}

function renderItem(root, i) {
  const card = el("section", {className: "q", id: "card_" + i.code});
  card.dataset.block = i.blocking ? "1" : "";
  const head = el("div", {className: "qhead"});
  head.append(el("span", {className: "code"}, i.code), el("span", {className: "qtitle"}, i.title || `${i.zh} / ${i.en}`));
  card.append(head);
  const tags = el("div", {className: "tags"});
  if (i.blocking) tags.append(el("span", {className: "tag block", title: i.blocking}, "影响在途开发"));
  if (i.changes_approved) tags.append(el("span", {className: "tag block"}, "改已批准词条"));
  if (i.accounting) tags.append(el("span", {className: "tag acct"}, "会计类"));
  if (i.verdict !== "不涉及") tags.append(el("span", {className: "tag"}, i.verdict === "正式" ? "预判：正式用语" : i.verdict === "方言" ? "预判：方言" : "预判：待定"));
  if (i.in_code) tags.append(el("span", {className: "tag", title: "main 上的代码标识符里已出现这个词"}, "代码中已出现"));
  card.append(tags);
  if (i.definition) card.append(el("p", {className: "def"}, i.definition));
  card.append(el("div", {className: "src"}, "出处：" + i.source));
  if (i.blocking) card.append(el("div", {className: "src"}, "影响：" + i.blocking));
  if (i.changes_approved) card.append(el("div", {className: "issue"}, "改已批准词条，影响面：" + i.changes_approved));
  const why = el("p", {className: "why"});
  why.append(el("b", {}, "推荐理由："), document.createTextNode(i.reason));
  card.append(why);
  if (i.naming_issues) card.append(el("div", {className: "issue"}, "命名检查：" + i.naming_issues));
  const box = el("div", {className: "dopts", role: "radiogroup", "aria-label": i.zh});
  i.options.forEach((o, n) => {
    const lab = el("label", {className: "dopt"});
    const r = el("input", {type: "radio", name: "o_" + i.code, value: String(n)});
    r.checked = choiceOf(i) === n;
    r.addEventListener("change", () => upd(i.code, {choice: n}));
    const l = el("div", {className: "lab"}, String.fromCharCode(65 + n) + ". " + o.label);
    if (o.recommended) l.append(el("span", {className: "rec"}, "推荐"));
    lab.append(r, l); box.append(lab);
  });
  card.append(box);
  const d = el("details", {className: "basis"});
  d.append(el("summary", {}, "依据"));
  d.append(el("p", {className: "ctx"}, i.standard));
  card.append(d);
  const lab = el("label", {className: "f"}, "说明（改选时请写理由）");
  const t = el("textarea", {placeholder: "可选", value: (state[i.code] && state[i.code].note) || ""});
  t.addEventListener("input", () => upd(i.code, {note: t.value}));
  lab.append(t); card.append(lab);
  root.append(card);
}

function render() {
  const root = $("#qs");
  renderJudge(root);
  const toc = el("nav", {className: "toc", "aria-label": "分组"});
  DOC.groups.forEach((g, n) => toc.append(el("a", {href: "#g" + n}, `${g.title}（${g.items.length}）`)));
  root.append(el("h2", {}, "分组"), toc);
  DOC.groups.forEach((g, n) => {
    const gh = el("div", {className: "ghead", id: "g" + n});
    const blocking = g.items.filter(i => i.blocking).length;
    gh.append(el("h2", {}, `${g.title}（${g.items.length} 条${blocking ? "，影响在途开发 " + blocking + " 条" : ""}）`));
    const b = el("button", {type: "button"}, "本组全部按推荐");
    b.addEventListener("click", () => {
      g.items.forEach(i => {
        const had = state[i.code] && state[i.code].choice != null;
        if (state[i.code]) delete state[i.code].choice;
        const r = document.querySelector(`input[name="o_${i.code}"][value="${recOf(i)}"]`); if (r) r.checked = true;
        if (had) push(i.code);
      });
      save(); progress();
    });
    gh.append(b); root.append(gh);
    if (g.intro) root.append(el("p", {className: "intro"}, g.intro));
    g.items.forEach(i => renderItem(root, i));
  });
  $("#who").value = state._who || "";
  $("#when").value = state._when || new Date().toISOString().slice(0, 10);
  $("#who").addEventListener("input", e => { state._who = e.target.value; save(); state._meta = {who: state._who, when: state._when}; push("_meta"); });
  $("#when").addEventListener("input", e => { state._when = e.target.value; save(); state._meta = {who: state._who, when: state._when}; push("_meta"); });
  $("#onlyblock").addEventListener("change", e => document.querySelectorAll(".q").forEach(c => { c.hidden = e.target.checked && !c.dataset.block; }));
  progress();
}
// 在线版（claude.ai 页面）把每条决定写到页面数据库 decisions/<编号>，用语管家会话直接读取；离线打开时 db 为 null，只存本机。
let db = null;
const chains = {}, timers = {};
function status(t) { const s = $("#sync"); if (s) s.textContent = t; }
function push(code) {
  if (!db) return;
  clearTimeout(timers[code]);
  timers[code] = setTimeout(() => {
    const s = state[code] || {};
    const i = ITEMS.find(x => x.code === code);
    const body = code === "_meta" ? {who: s.who || "", when: s.when || "", at: new Date().toISOString()}
      : {choice: s.choice == null ? null : s.choice, recommended: s.choice == null || s.choice === recOf(i), note: s.note || "", key: i.key, at: new Date().toISOString()};
    status("保存中…");
    chains[code] = (chains[code] || Promise.resolve()).then(() => db.doc((code === "_meta" ? "meta/form" : "decisions/" + code)).set(body))
      .then(() => status("已自动保存，用语管家可直接读取"), e => status("保存失败（" + (e && e.code || "未知") + "），请稍后再改一次或用底部按钮导出"));
  }, 600);
}
function upd(key, patch) { state[key] = Object.assign({}, state[key], patch); save(); progress(); push(key); }
async function connect() {
  if (!window.claude || !window.claude.use) return;
  db = await window.claude.use("db");
  if (!db) { status("本机保存（未连接云端）"); return; }
  try {
    const snap = await db.collection("decisions").limit(1000).get();
    snap.docs.forEach(d => { const v = d.data(); if (v && ITEMS.some(i => i.code === d.id)) state[d.id] = {choice: v.choice == null ? undefined : v.choice, note: v.note || ""}; });
    const meta = await db.doc("meta/form").get();
    if (meta.exists) { const m = meta.data(); state._who = m.who || state._who; state._when = m.when || state._when; }
    ITEMS.forEach(i => {
      const r = document.querySelector(`input[name="o_${i.code}"][value="${choiceOf(i)}"]`); if (r) r.checked = true;
      const t = document.querySelector(`#card_${i.code} textarea`); if (t) t.value = (state[i.code] && state[i.code].note) || "";
    });
    $("#who").value = state._who || ""; if (state._when) $("#when").value = state._when;
    save(); progress();
    status(snap.size ? `已从云端恢复 ${snap.size} 条，改动自动保存` : "改动自动保存，用语管家可直接读取");
  } catch (e) { status("读取云端失败（" + (e && e.code || "未知") + "），改动仍会尝试保存"); }
}
function progress() {
  let n = 0, notes = 0;
  ITEMS.forEach(i => { const ch = choiceOf(i) !== recOf(i); n += ch; notes += !!(state[i.code] && (state[i.code].note || "").trim()); $("#card_" + i.code).classList.toggle("changed", ch); });
  $("#prog").textContent = `已改选 ${n} 条、写说明 ${notes} 条 / 共 ${ITEMS.length} 条（其余按推荐）`;
}
function payload() {
  return {
    form: "GLOSSARY_ROUND3", version: DOC.version, date: $("#when").value, decided_by: state._who || "",
    decisions: ITEMS.map(i => {
      const c = choiceOf(i), o = i.options[c];
      return {code: i.code, key: i.key, choice: c, action: o.action, zh: o.zh, en: o.en, into: o.into || "", definition: o.definition || "",
              label: o.label, recommended: c === recOf(i), note: ((state[i.code] && state[i.code].note) || "").trim()};
    })
  };
}
function asText(p) {
  const L = [`用语决策单（第三轮）· 决定人：${p.decided_by || "（未填）"} · 日期：${p.date}`, ""];
  let group = "";
  p.decisions.forEach(d => {
    const i = ITEMS.find(x => x.code === d.code);
    if (i.group !== group) { group = i.group; L.push(`【${group}】`); }
    if (d.recommended && !d.note) return;
    L.push(`[${d.code}] ${i.title || i.zh + " / " + i.en}`, `   选择：${d.label}${d.recommended ? "（推荐）" : "（改选，推荐为：" + i.options[recOf(i)].label + "）"}`);
    if (d.note) L.push(`   说明：${d.note}`);
  });
  L.push("", "（未列出的条目均按推荐）", "", "---- 以下为机读数据，请勿修改 ----", JSON.stringify(p));
  return L.join("\n");
}
if ($("#exp")) $("#exp").addEventListener("click", () => {
  const p = payload();
  const a = el("a", {href: URL.createObjectURL(new Blob([asText(p)], {type: "text/plain;charset=utf-8"})), download: `用语决定_第三轮_${p.date}.txt`});
  document.body.append(a); a.click(); a.remove();
  $("#msg").textContent = "已生成决定文件。没有看到下载时，改用「复制决定」。";
});
$("#copy").addEventListener("click", async () => {
  const txt = asText(payload());
  try { await navigator.clipboard.writeText(txt); $("#msg").textContent = "已复制。"; }
  catch { const o = $("#out"); o.hidden = false; o.value = txt; o.focus(); o.select(); $("#msg").textContent = "浏览器不允许自动复制，已选中下方文字，请手动复制。"; }
});
render();
connect();
