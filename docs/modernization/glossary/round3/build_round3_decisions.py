#!/usr/bin/env python3
"""由 round3-items.json（第三轮用语确认的唯一数据源）生成离线页面 ROUND3_DECISIONS.html，供用户逐条拍板。
版式沿用财务决策单（r0/finance_confirm.css、finance_decisions.css）加 round3.css，交互在 round3.js：
每条预选推荐项，可改选并写说明，每组可「本组全部按推荐」，进度存在本机浏览器，底部导出。
校验（不通过则生成失败）：编号唯一；每条 2–4 个选项且恰好一个推荐；action 只取 approve / rename / merge / common_word / defer / keep / redefine（redefine 须带 definition）；
并入目标 id 存在于正式词表、提议条目或本单；必填字段不空；组内「影响在途开发」的条目排在前面。
用法：python3 docs/modernization/glossary/round3/build_round3_decisions.py"""
import json, pathlib, re, sys
import yaml

HERE = pathlib.Path(__file__).parent
ROOT = HERE.parents[3]
SRC = HERE / "round3-items.json"
INVENTORY = HERE / "inventory.json"
OUT = HERE / "ROUND3_DECISIONS.html"
R0 = ROOT / "docs" / "modernization" / "r0"
ACTIONS = {"approve", "rename", "merge", "common_word", "defer", "keep", "redefine"}
VERDICTS = {"正式", "方言", "待定", "不涉及"}
REQUIRED = ("key", "code", "zh", "en", "source", "reason", "standard", "verdict")
JUDGE = {
    "levels": [
        "法规与会计准则：中文用《企业会计准则》原文，英文用 IFRS / IAS。例：「合同资产 / ContractAsset」（企业会计准则第 14 号——收入）。",
        "行业与工程标准：中文用国标（GB/T 50326、50358、50500），英文用 PMI PMBOK / AACE、FIDIC。例：「工程变更 / Variation」（GB/T 50500、FIDIC）。平台技术用语按软件工程通行规范（BPMN 2.0、OAuth / OIDC、ISO/IEC 27000 等）。",
        "专业服务 / PSA 行业通行写法：准则与国标都没有时采用。例：「可计费工时 / BillableHours」。",
    ],
    "marks": [
        "方言：公司内部叫法，与正式用语不符，新系统一律不用（不进代码、界面、接口文档、列注释）。例：「产值」是「收入确认」的方言。选方言的条目会登记为禁用写法；正被代码使用的，先由负责会话改名，再加禁用。",
        "待定：判断不了是正式用语还是方言，或要等某个决定后再定。例：「OH 工时」（间接工时的公司叫法，正式名待定）。",
        "选项说明：「批准」按所写中英文名登记为正式用语；「改为」用新名登记，原叫法归为方言；「并入」不另立词条，作为已有词条的别名或删除；「归为通用词」从业务术语移到技术通用词；「暂缓」本轮不定；末组「定义写法」只问定义用哪种写法。",
    ],
}


def known_ids():
    ids = {t["id"] for t in yaml.safe_load((ROOT / "docs/modernization/glossary/terms.yaml").read_text(encoding="utf-8"))["terms"]}
    for path in sorted((ROOT / "backend/glossary/proposed").glob("*.yaml")):
        ids |= {t["id"] for t in (yaml.safe_load(path.read_text(encoding="utf-8")) or {}).get("terms") or []}
    ids |= {r["id"] for r in json.loads(INVENTORY.read_text(encoding="utf-8"))}
    return ids


def validate(doc):
    errs, codes, ids = [], set(), known_ids()
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}[a-z0-9-]*", doc.get("version", "")):
        errs.append("version 须为日期开头")
    if not doc.get("groups"):
        errs.append("没有分组")
    own = {i.get("id") for g in doc.get("groups", []) for i in g.get("items", []) if i.get("id")}
    for g in doc.get("groups", []):
        if not g.get("title") or not g.get("items"):
            errs.append(f"分组缺标题或条目：{g.get('title')}")
            continue
        seen_plain = False
        for i in g["items"]:
            where = i.get("code") or i.get("key") or "?"
            for k in REQUIRED:
                if not i.get(k):
                    errs.append(f"{where}：缺 {k}")
            if i.get("code") in codes:
                errs.append(f"{where}：编号重复")
            codes.add(i.get("code"))
            if i.get("verdict") not in VERDICTS:
                errs.append(f"{where}：verdict 只取 {sorted(VERDICTS)}")
            if i.get("blocking") and seen_plain:
                errs.append(f"{where}：「影响在途开发」的条目须排在本组前面")
            seen_plain = seen_plain or not i.get("blocking")
            opts = i.get("options") or []
            if not 2 <= len(opts) <= 4:
                errs.append(f"{where}：选项须 2–4 个")
            if sum(bool(o.get("recommended")) for o in opts) != 1:
                errs.append(f"{where}：推荐项须恰好一个")
            for o in opts:
                if o.get("action") not in ACTIONS:
                    errs.append(f"{where}：未知 action {o.get('action')}")
                if not o.get("label"):
                    errs.append(f"{where}：选项缺 label")
                if o.get("action") == "merge" and o.get("into") not in ids | own:
                    errs.append(f"{where}：并入目标 {o.get('into')} 不存在")
                if o.get("action") == "redefine" and not o.get("definition"):
                    errs.append(f"{where}：redefine 须给出 definition")
                if o.get("action") in ("approve", "rename") and not (o.get("zh") and o.get("en")):
                    errs.append(f"{where}：{o.get('action')} 须给出 zh 与 en")
    if errs:
        sys.exit("用语决策单校验失败：\n" + "\n".join(errs))


def page(doc):
    n = sum(len(g["items"]) for g in doc["groups"])
    blocking = sum(1 for g in doc["groups"] for i in g["items"] if i.get("blocking"))
    style = "".join((R0 / f).read_text(encoding="utf-8") for f in ("finance_confirm.css", "finance_decisions.css")) + (HERE / "round3.css").read_text(encoding="utf-8")
    js = (HERE / "round3.js").read_text(encoding="utf-8")
    data = json.dumps(dict(doc, judge=JUDGE), ensure_ascii=False).replace("</", "<\\/")
    return f"""<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>用语决策单（第三轮）</title>
<style>
{style}
</style>
</head>
<body>
<div class="wrap">
<header>
  <p class="ver">{doc["version"]} · 第三轮用语确认（ADR-0018）：1a 第二波各模块的提议用语，加 R0 报表分析的待确认叫法与新增词条。</p>
  <h1>用语决策单（第三轮）</h1>
  <p class="lede">共 {n} 条，按模块分组；其中 {blocking} 条标「影响在途开发」，排在各组前面，请先定。每条已预选推荐项，同意就不用动；要改就点别的选项，并在说明里写理由。每组可点「本组全部按推荐」。错误码不在本单（归结构重构主线另行处理）。</p>
  <div class="howto">
    <div><b>怎么用</b>：用浏览器打开本文件即可，不需要登录，也不联网。改选和说明保存在这台电脑的这个浏览器里，可以分几次填完。</div>
    <div><b>怎么交回</b>：点页面底部「导出决定」，把下载的文件交给用语管家会话 one-glossary；浏览器不让下载时，点「复制决定」粘贴发回。</div>
  </div>
  <div class="meta">
    <label class="f" for="who">决定人<input id="who" type="text" placeholder="如：都业梁"></label>
    <label class="f" for="when">日期<input id="when" type="date"></label>
  </div>
  <label class="filter"><input type="checkbox" id="onlyblock">只看「影响在途开发」</label>
</header>
<main id="qs"></main>
</div>
<div class="bar"><div class="in">
  <span class="prog" id="prog">已改选 0 条 / 共 {n} 条</span>
  <button type="button" id="copy">复制决定</button>
  <button type="button" class="primary" id="exp">导出决定</button>
  <span id="msg" role="status"></span>
  <textarea id="out" hidden readonly aria-label="决定文本"></textarea>
</div></div>
<script>
const DOC = {data};
{js}
</script>
</body>
</html>
"""


if __name__ == "__main__":
    doc = json.loads(SRC.read_text(encoding="utf-8"))
    validate(doc)
    OUT.write_text(page(doc), encoding="utf-8")
    print(f"{OUT.name}：{sum(len(g['items']) for g in doc['groups'])} 条，{len(doc['groups'])} 组；影响在途开发 "
          f"{sum(1 for g in doc['groups'] for i in g['items'] if i.get('blocking'))} 条")
