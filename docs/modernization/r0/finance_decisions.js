// 数据由 build_finance_decisions.py 注入为 DOC（来自 FINANCE_DECISIONS.md）。每题预选推荐项；改选与说明存在本机浏览器。
const STORE = "finance-decisions-2026-10-15";
const $ = s => document.querySelector(s);
const el = (tag, attrs = {}, text) => { const e = document.createElement(tag); Object.assign(e, attrs); if (text != null) e.textContent = text; return e; };
function load() { try { return JSON.parse(localStorage.getItem(STORE) || "{}"); } catch { return {}; } }
function save() { try { localStorage.setItem(STORE, JSON.stringify(state)); } catch {} }
let state = load();
const ITEMS = DOC.stages.flatMap(s => s.items.map(i => Object.assign({stage: s.stage}, i)));
const recOf = i => i.opts.find(o => o.rec).label;
const choiceOf = i => state[i.id]?.choice || recOf(i);

function list(items, cls) { const ul = el("ul", {className: cls || "lst"}); items.forEach(t => ul.append(el("li", {}, t))); return ul; }

function renderExample(root) {
  const ex = DOC.example;
  root.append(el("h2", {}, "卷首示例：两本账怎么记"));
  ex.intro.forEach(t => root.append(el("p", {className: "intro"}, t)));
  const box = el("div", {className: "ex"}), tb = el("table");
  ex.rows.forEach((r, n) => { const tr = el("tr"); r.forEach(c => tr.append(el(n ? "td" : "th", {}, c))); tb.append(tr); });
  box.append(tb); root.append(box);
  ex.after.forEach(t => root.append(el("p", {className: "intro"}, t)));
}

function renderItem(root, i) {
  const card = el("section", {className: "q", id: "card_" + i.id});
  const head = el("div", {className: "qhead"});
  head.append(el("span", {className: "code"}, i.id), el("span", {className: "qtitle"}, i.title));
  card.append(head, el("div", {className: "src"}, "来源：" + i.src));
  if (i.ctx) card.append(el("p", {className: "ctx"}, i.ctx));
  const box = el("div", {className: "dopts", role: "radiogroup"});
  i.opts.forEach((o, n) => {
    const lab = el("label", {className: "dopt"});
    const r = el("input", {type: "radio", name: "o_" + i.id, value: o.label});
    r.checked = choiceOf(i) === o.label;
    r.addEventListener("change", () => upd(i.id, {choice: o.label}));
    const txt = el("div");
    const l = el("div", {className: "lab"}, String.fromCharCode(65 + n) + ". " + o.label);
    if (o.rec) l.append(el("span", {className: "rec"}, "推荐"));
    txt.append(l); if (o.effect) txt.append(el("div", {className: "eff"}, o.effect));
    lab.append(r, txt); box.append(lab);
  });
  card.append(box);
  if (i.basis.length || i.practice.length) {
    const d = el("details", {className: "basis"});
    d.append(el("summary", {}, "依据与实践"));
    d.append(list(i.basis.map(t => "依据：" + t).concat(i.practice.map(t => "实践：" + t))));
    card.append(d);
  }
  if (i.check) card.append(el("div", {className: "check"}, "需核实：" + i.check));
  if (i.now) card.append(el("div", {className: "check"}, "现状：" + i.now));
  const lab = el("label", {className: "f"}, "说明（改选时请写理由）");
  const t = el("textarea", {placeholder: "可选", value: state[i.id]?.note || ""});
  t.addEventListener("input", () => upd(i.id, {note: t.value}));
  lab.append(t); card.append(lab);
  root.append(card);
}

function render() {
  const root = $("#qs");
  renderExample(root);
  DOC.stages.forEach(s => {
    root.append(el("h2", {}, `卡点：${s.stage}（${s.items.length} 题）`));
    s.items.forEach(i => renderItem(root, Object.assign({stage: s.stage}, i)));
  });
  root.append(el("h2", {}, `需向财务核实的事实（${DOC.facts.length} 条）`));
  root.append(el("p", {className: "intro"}, "以下只能由财务回答；每条写明该事实会怎样改变推荐。可在说明里记下已知情况。"));
  DOC.facts.forEach(f => {
    const card = el("section", {className: "q"});
    const head = el("div", {className: "qhead"});
    head.append(el("span", {className: "code"}, f.id), el("span", {className: "qtitle"}, f.q));
    card.append(head, el("p", {className: "ctx"}, "影响：" + f.impact));
    const t = el("textarea", {placeholder: "已知情况（可选）", value: state[f.id]?.note || ""});
    t.addEventListener("input", () => upd(f.id, {note: t.value}));
    card.append(t); root.append(card);
  });
  root.append(el("h2", {}, "已定口径（供核对，不需选择）"));
  root.append(list(DOC.decided));
  root.append(el("h2", {}, "外部来源"));
  const ul = el("ul", {className: "lst"});
  DOC.sources.forEach(s => { const li = el("li"); li.append(el("a", {href: s.url, target: "_blank", rel: "noopener"}, s.name), document.createTextNode("：" + s.note)); ul.append(li); });
  root.append(ul);
  $("#who").value = state._who || "";
  $("#when").value = state._when || new Date().toISOString().slice(0, 10);
  $("#who").addEventListener("input", e => { state._who = e.target.value; save(); });
  $("#when").addEventListener("input", e => { state._when = e.target.value; save(); });
  progress();
}
function upd(key, patch) { state[key] = Object.assign({}, state[key], patch); save(); progress(); }
function progress() {
  let n = 0;
  ITEMS.forEach(i => { const ch = choiceOf(i) !== recOf(i); n += ch; $("#card_" + i.id).classList.toggle("changed", ch); });
  $("#prog").textContent = `已改 ${n} 题 / 共 ${ITEMS.length} 题（其余按推荐）`;
}
function payload() {
  return {
    form: "FINANCE_DECISIONS", date: $("#when").value, decided_by: state._who || "",
    decisions: ITEMS.map(i => ({id: i.id, stage: i.stage, choice: choiceOf(i), recommended: choiceOf(i) === recOf(i), note: (state[i.id]?.note || "").trim()})),
    facts: DOC.facts.map(f => ({id: f.id, note: (state[f.id]?.note || "").trim()})).filter(f => f.note)
  };
}
function asText(p) {
  const L = [`财务口径决策单 · 决定人：${p.decided_by || "（未填）"} · 日期：${p.date}`, ""];
  let stage = "";
  p.decisions.forEach(d => {
    if (d.stage !== stage) { stage = d.stage; L.push(`【${stage}】`); }
    const i = ITEMS.find(x => x.id === d.id);
    L.push(`[${d.id}] ${i.title}`, `   选择：${d.choice}${d.recommended ? "（推荐）" : "（改选，推荐为：" + recOf(i) + "）"}`);
    if (d.note) L.push(`   说明：${d.note}`);
  });
  if (p.facts.length) { L.push("", "【已知事实】"); p.facts.forEach(f => L.push(`[${f.id}] ${f.note}`)); }
  L.push("", "---- 以下为机读数据，请勿修改 ----", JSON.stringify(p));
  return L.join("\n");
}
$("#exp").addEventListener("click", () => {
  const p = payload();
  const a = el("a", {href: URL.createObjectURL(new Blob([asText(p)], {type: "text/plain;charset=utf-8"})), download: `财务口径决定_${p.date}.txt`});
  document.body.append(a); a.click(); a.remove();
  $("#msg").textContent = "已生成决定文件。没有看到下载时，改用「复制决定」。";
});
$("#copy").addEventListener("click", async () => {
  const txt = asText(payload());
  try { await navigator.clipboard.writeText(txt); $("#msg").textContent = "已复制。"; }
  catch { const o = $("#out"); o.hidden = false; o.value = txt; o.focus(); o.select(); $("#msg").textContent = "浏览器不允许自动复制，已选中下方文字，请手动复制。"; }
});
render();
