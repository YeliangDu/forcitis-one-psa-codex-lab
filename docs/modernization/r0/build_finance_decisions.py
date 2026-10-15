#!/usr/bin/env python3
"""由 FINANCE_DECISIONS.md（财务口径决策单的唯一数据源）生成离线页面 FINANCE_DECISIONS.html，供用户逐题拍板。
版式沿用财务确认单（finance_confirm.css），交互在 finance_decisions.js：每题预选推荐项，可改选并写说明，底部导出。
校验（不通过则生成失败）：题号唯一且格式为 FD-xx；阶段只取五个卡点；每题至少两个选项且恰好一个推荐；
FINANCE_QUESTIONS.md「去向」表引用的 FD / F 题号都存在；GL_FINANCE_DECISIONS.json 的 resolution 引用的 FD / F 题号都存在。
用法：python3 build_finance_decisions.py"""
import json, pathlib, re, sys

HERE = pathlib.Path(__file__).parent
SRC = HERE / "FINANCE_DECISIONS.md"
QUESTIONS = HERE / "FINANCE_QUESTIONS.md"
GL_JSON = HERE.parent / "rules" / "GL_FINANCE_DECISIONS.json"
OUT = HERE / "FINANCE_DECISIONS.html"
STAGES = ["1a 出口", "1b 前", "Phase 4 前", "G1 前", "G2 前"]
KEYS = {"来源": "src", "背景": "ctx", "核实": "check", "现状": "now"}
MULTI = {"依据": "basis", "实践": "practice"}


def parse(md):
    doc = {"example": {"intro": [], "rows": [], "after": []}, "stages": [], "facts": [], "decided": [], "sources": []}
    section, cur = None, None
    for raw in md.splitlines():
        line = raw.rstrip()
        if line.startswith("## "):
            title = line[3:].strip()
            cur = None
            if title.startswith("卷首示例"):
                section = "example"
            elif title.startswith("阶段："):
                section = "stage"
                doc["stages"].append({"stage": title[len("阶段："):].strip(), "items": []})
            elif title.startswith("需向财务核实的事实"):
                section = "facts"
            elif title.startswith("已定口径"):
                section = "decided"
            elif title.startswith("外部来源"):
                section = "sources"
            else:
                section = None
            continue
        if section == "example":
            if line.startswith("|"):
                cells = [c.strip() for c in line.strip("|").split("|")]
                if not set("".join(cells)) <= set("-: "):
                    doc["example"]["rows"].append(cells)
            elif line.strip():
                (doc["example"]["after"] if doc["example"]["rows"] else doc["example"]["intro"]).append(line.strip())
            continue
        if section == "stage":
            m = re.match(r"^### (FD-\d{2}) · (.+)$", line)
            if m:
                cur = {"id": m.group(1), "title": m.group(2).strip(), "opts": [], "basis": [], "practice": []}
                doc["stages"][-1]["items"].append(cur)
                continue
            if cur is None or not line.strip():
                continue
            if line.startswith("- "):
                body = line[2:]
                rec = body.startswith("推荐｜")
                if rec:
                    body = body[len("推荐｜"):]
                parts = body.split("｜", 1)
                cur["opts"].append({"label": parts[0].strip(), "effect": parts[1].strip() if len(parts) > 1 else "", "rec": rec})
                continue
            k, _, v = line.partition("：")
            if k in KEYS:
                cur[KEYS[k]] = v.strip()
            elif k in MULTI:
                cur[MULTI[k]].append(v.strip())
            else:
                sys.exit(f"{cur['id']}：无法识别的行「{line[:30]}」")
            continue
        if section == "facts":
            m = re.match(r"^### (F-\d{2}) · (.+)$", line)
            if m:
                cur = {"id": m.group(1), "q": m.group(2).strip(), "impact": ""}
                doc["facts"].append(cur)
            elif cur and line.startswith("影响："):
                cur["impact"] = line[len("影响："):].strip()
            continue
        if section == "decided" and line.startswith("- "):
            doc["decided"].append(line[2:].strip())
        if section == "sources" and line.startswith("- "):
            m = re.match(r"^- \[(.+?)\]\((.+?)\)：(.+)$", line)
            if not m:
                sys.exit(f"外部来源格式不对：{line[:40]}")
            doc["sources"].append({"name": m.group(1), "url": m.group(2), "note": m.group(3)})
    return doc


def validate(doc):
    errs = []
    ids = [i["id"] for s in doc["stages"] for i in s["items"]]
    if len(set(ids)) != len(ids):
        errs.append("FD 题号重复")
    for s in doc["stages"]:
        if s["stage"] not in STAGES:
            errs.append(f"未知阶段「{s['stage']}」，只允许 {STAGES}")
    order = [STAGES.index(s["stage"]) for s in doc["stages"] if s["stage"] in STAGES]
    if order != sorted(order):
        errs.append("阶段须按 1a 出口 → G2 前排列（最急的在前）")
    for s in doc["stages"]:
        for i in s["items"]:
            if len(i["opts"]) < 2:
                errs.append(f"{i['id']}：选项少于 2 个")
            if sum(o["rec"] for o in i["opts"]) != 1:
                errs.append(f"{i['id']}：推荐项须恰好一个")
            for k in ("src", "basis"):
                if not i.get(k):
                    errs.append(f"{i['id']}：缺「{'来源' if k == 'src' else '依据'}」")
    fids = [f["id"] for f in doc["facts"]]
    if len(set(fids)) != len(fids):
        errs.append("F 题号重复")
    known = set(ids) | set(fids)
    refs = set(re.findall(r"\bFD-\d{2}\b|\bF-\d{2}\b", QUESTIONS.read_text(encoding="utf-8")))
    refs |= set(re.findall(r"\bFD-\d{2}\b|\bF-\d{2}\b", GL_JSON.read_text(encoding="utf-8")))
    refs |= set(re.findall(r"\bFD-\d{2}\b|\bF-\d{2}\b", json.dumps(doc, ensure_ascii=False)))
    for r in sorted(refs - known):
        errs.append(f"引用的题号 {r} 不存在")
    if not doc["example"]["rows"]:
        errs.append("缺卷首示例表")
    if errs:
        sys.exit("财务口径决策单校验失败：\n" + "\n".join(errs))


def page(doc):
    n = sum(len(s["items"]) for s in doc["stages"])
    style = (HERE / "finance_confirm.css").read_text(encoding="utf-8") + (HERE / "finance_decisions.css").read_text(encoding="utf-8")
    js = (HERE / "finance_decisions.js").read_text(encoding="utf-8")
    counts = "、".join(f"{s['stage']} {len(s['items'])} 题" for s in doc["stages"])
    return f"""<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>财务口径决策单</title>
<style>
{style}
</style>
</head>
<body>
<div class="wrap">
<header>
  <p class="ver">2026-10-15 · 取代发给财务的第 4 版问卷：按用户决定，尚未定案的财务口径由用户全盘拍板；财务确认单第 3 版保留为历史。</p>
  <h1>财务口径决策单</h1>
  <p class="lede">共 {n} 题（{counts}），按阶段卡点排列，最急的在前。每题已预选推荐项，同意就不用动；要改就点别的选项，并在说明里写理由。「依据与实践」可展开查看准则条款和同类上市公司的做法。末尾另列 {len(doc['facts'])} 条只能由财务回答的事实，以及已定的口径供核对。</p>
  <div class="howto">
    <div><b>怎么用</b>：用浏览器打开本文件即可，不需要登录，也不联网。改选和说明会保存在这台电脑的这个浏览器里，下次打开接着改。</div>
    <div><b>怎么交回</b>：点页面底部「导出决定」，把下载的文件交给调度会话；浏览器不让下载时，点「复制决定」粘贴发回。</div>
  </div>
  <div class="meta">
    <label class="f" for="who">决定人<input id="who" type="text" placeholder="如：都业梁"></label>
    <label class="f" for="when">日期<input id="when" type="date"></label>
  </div>
</header>
<main id="qs"></main>
</div>
<div class="bar"><div class="in">
  <span class="prog" id="prog">已改 0 题 / 共 {n} 题</span>
  <button type="button" id="copy">复制决定</button>
  <button type="button" class="primary" id="exp">导出决定</button>
  <span id="msg" role="status"></span>
  <textarea id="out" hidden readonly aria-label="决定文本"></textarea>
</div></div>
<script>
const DOC = {json.dumps(doc, ensure_ascii=False)};
{js}
</script>
</body>
</html>
"""


if __name__ == "__main__":
    doc = parse(SRC.read_text(encoding="utf-8"))
    validate(doc)
    OUT.write_text(page(doc), encoding="utf-8")
    print(f"{OUT.name}：{sum(len(s['items']) for s in doc['stages'])} 题；" + "、".join(f"{s['stage']} {len(s['items'])}" for s in doc["stages"]) + f"；须向财务核实 {len(doc['facts'])} 条；外部来源 {len(doc['sources'])} 个")
