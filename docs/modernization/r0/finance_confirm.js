// 数据由 build_finance_confirm.py 注入：ADRS（ADR 整体确认）、GROUPS（问卷，来自 FINANCE_QUESTIONS.md）、ADR_INTRO。
const STORE = "finance-confirm-v" + VERSION;
const $ = s => document.querySelector(s);
const el = (tag, attrs = {}, text) => { const e = document.createElement(tag); Object.assign(e, attrs); if (text != null) e.textContent = text; return e; };
const sid = k => k.replace(/[^A-Za-z0-9]/g, "_");
function load() { try { return JSON.parse(localStorage.getItem(STORE) || "{}"); } catch { return {}; } }
function save() { try { localStorage.setItem(STORE, JSON.stringify(state)); } catch {} }
let state = load();
const ITEMS = [];  // {key, card, parts:[subkey...]}

function controls(card, key, spec) {
  const id = sid(key);
  if (spec.opts && spec.opts.length) {
    const box = el("div", {className: "opts", role: "radiogroup"});
    spec.opts.forEach((o, i) => {
      const lab = el("label", {className: "opt"});
      const r = el("input", {type: "radio", name: "o_" + id, id: "o_" + id + "_" + i, value: o});
      r.checked = state[key]?.choice === o;
      r.addEventListener("change", () => upd(key, {choice: o}));
      lab.append(r, el("span", {}, o)); box.append(lab);
    });
    card.append(box);
  }
  if (spec.num) {
    const lab = el("label", {className: "f", htmlFor: "n_" + id}, spec.num);
    const n = el("input", {type: "number", id: "n_" + id, min: 1, max: 31, value: state[key]?.choice || ""});
    n.addEventListener("input", () => upd(key, {choice: n.value}));
    lab.append(n); card.append(lab);
  }
  const lab = el("label", {className: "f", htmlFor: "t_" + id}, "说明");
  const t = el("textarea", {id: "t_" + id, placeholder: spec.ph || "补充说明（可选）", value: state[key]?.note || ""});
  t.addEventListener("input", () => upd(key, {note: t.value}));
  lab.append(t); card.append(lab);
}

function render() {
  const root = $("#qs");
  if (ADRS.length) {
    root.append(el("h2", {}, "一、涉及财务的决定（ADR）整体确认"));
    if (ADR_INTRO) root.append(el("p", {className: "intro"}, ADR_INTRO));
    ADRS.forEach(a => {
      const card = el("section", {className: "q", id: "card_" + sid(a.adr)});
      const head = el("div", {className: "qhead"});
      head.append(el("span", {className: "code"}, a.adr), el("span", {className: "qtitle"}, a.title), el("span", {className: "status"}, a.status));
      card.append(head);
      if (a.one) card.append(el("p", {className: "ctx"}, a.one));
      const ul = el("ol", {className: "pts"}); a.points.forEach(p => ul.append(el("li", {}, p))); card.append(ul);
      if (a.watch.length) { const w = el("div", {className: "watch"}); a.watch.forEach(x => w.append(el("div", {}, "留意：" + x))); card.append(w); }
      controls(card, a.adr, {opts: ["同意", "有意见（请在说明中写明第几条）", "需讨论"], ph: "有意见时请写明第几条、哪里不对、应该怎么做"});
      root.append(card); ITEMS.push({key: a.adr, card, parts: []});
    });
    root.append(el("h2", {}, "二、需要财务答复的问题"));
  }
  GROUPS.forEach(([name, qs]) => {
    root.append(el("h3", {className: "grp"}, name));
    qs.forEach(q => {
      const card = el("section", {className: "q" + (q.deferred ? " deferred" : ""), id: "card_" + sid(q.fq)});
      const head = el("div", {className: "qhead"});
      head.append(el("span", {className: "code"}, q.fq), el("span", {className: "qtitle"}, q.q));
      card.append(head);
      if (q.deferred) { card.append(el("p", {className: "ctx"}, q.deferred)); root.append(card); return; }
      if (q.ctx) card.append(el("p", {className: "ctx"}, q.ctx));
      const item = {key: q.fq, card, parts: []};
      if (q.parts) {
        q.parts.forEach((p, i) => {
          const k = q.fq + "." + (i + 1), box = el("div", {className: "part"});
          box.append(el("div", {className: "plabel"}, `（${i + 1}）${p.label}`));
          controls(box, k, p); card.append(box); item.parts.push(k);
        });
      } else controls(card, q.fq, q);
      root.append(card); ITEMS.push(item);
    });
  });
  $("#who").value = state._who || "";
  $("#when").value = state._when || new Date().toISOString().slice(0, 10);
  $("#who").addEventListener("input", e => { state._who = e.target.value; save(); });
  $("#when").addEventListener("input", e => { state._when = e.target.value; save(); });
  progress();
}
const has = k => { const a = state[k] || {}; return !!((a.choice && String(a.choice).trim()) || (a.note && a.note.trim())); };
const done = it => it.parts.length ? it.parts.every(has) : has(it.key);
function upd(key, patch) { state[key] = Object.assign({}, state[key], patch); save(); progress(); }
function progress() {
  let n = 0; ITEMS.forEach(it => { const ok = done(it); n += ok; it.card.classList.toggle("done", ok); });
  $("#prog").textContent = `已答 ${n} / ${ITEMS.length}`;
}
const pick = k => ({choice: state[k]?.choice || "", note: (state[k]?.note || "").trim()});
function payload() {
  return {
    form: "FINANCE_CONFIRM", version: VERSION, respondent_role: state._who || "", date: $("#when").value,
    adrs: ADRS.map(a => Object.assign({adr: a.adr}, pick(a.adr))),
    answers: GROUPS.flatMap(g => g[1]).filter(q => !q.deferred).map(q => q.parts
      ? {fq: q.fq, parts: q.parts.map((p, i) => Object.assign({label: p.label}, pick(q.fq + "." + (i + 1))))}
      : Object.assign({fq: q.fq}, pick(q.fq)))
  };
}
function asText(p) {
  const L = [`财务确认单（第 ${p.version} 版） · 答复人岗位：${p.respondent_role || "（未填）"} · 日期：${p.date}`, ""];
  const line = (a, pad) => L.push(`${pad}选择：${a.choice || "（未选）"}`, `${pad}说明：${a.note || "（无）"}`);
  if (p.adrs.length) { L.push("一、决定（ADR）整体确认"); p.adrs.forEach(a => { L.push(`[${a.adr}] ${ADRS.find(x => x.adr === a.adr).title}`); line(a, "   "); }); L.push(""); }
  L.push("二、问题答复");
  p.answers.forEach(a => {
    const q = GROUPS.flatMap(g => g[1]).find(x => x.fq === a.fq);
    L.push(`[${a.fq}] ${q.q}`);
    if (a.parts) a.parts.forEach((pp, i) => { L.push(`   （${i + 1}）${pp.label}`); line(pp, "      "); }); else line(a, "   ");
  });
  L.push("", "---- 以下为机读数据，请勿修改 ----", JSON.stringify(p));
  return L.join("\n");
}
$("#exp").addEventListener("click", () => {
  const p = payload();
  const a = el("a", {href: URL.createObjectURL(new Blob([asText(p)], {type: "text/plain;charset=utf-8"})), download: `财务确认单答复_v${p.version}_${p.date}.txt`});
  document.body.append(a); a.click(); a.remove();
  $("#msg").textContent = "已生成答复文件，请把下载的文件发回项目负责人。没有看到下载时，改用「复制答复」。";
});
$("#copy").addEventListener("click", async () => {
  const txt = asText(payload());
  try { await navigator.clipboard.writeText(txt); $("#msg").textContent = "已复制，请粘贴到邮件或微信发回。"; }
  catch { const o = $("#out"); o.hidden = false; o.value = txt; o.focus(); o.select(); $("#msg").textContent = "浏览器不允许自动复制，已选中下方文字，请按 Ctrl+C（Mac 为 ⌘C）复制。"; }
});
render();
