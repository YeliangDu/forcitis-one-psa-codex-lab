#!/usr/bin/env python3
"""业务决策在线页（one-rules）：把待用户定的业务口径做成手机能开的在线页，推荐项预选、按组提交，选择存在在线页数据库（README.md）。
单子：FD = 财务口径决策单（数据源 r0/FINANCE_DECISIONS.md，解析沿用 r0/build_finance_decisions.py）；其余单子为 sheets/<单子>.json（格式见 README.md）。
校验不通过即失败：题号唯一、每题至少两个选项且恰好一个推荐、单组不超过 40 题（一屏只画一组，组太大手机上会停在加载中）、页面不加载任何外部资源。
用法：python3 docs/modernization/rules/decide/build_decision_page.py <单子>[,<单子>...] <输出路径>"""
import json, pathlib, re, sys

HERE = pathlib.Path(__file__).parent
R0 = HERE.parent.parent / "r0"
sys.path.insert(0, str(R0))
import build_finance_decisions as fd  # noqa: E402

FD_GROUPS = {
    "1a 出口": ("fd-1a", "1a 出口前要定：影响现有代码与文档里的写法。"),
    "1b 前": ("fd-1b", "1b 开工前要定：金额舍入、税额、工时成本等平台口径，1b 起所有金额计算都按它走。"),
    "Phase 4 前": ("fd-p4", "Phase 4（收入与两本账）开工前要定：合同、收入、冲销与更正的记账口径。"),
    "G1 前": ("fd-g1", "G1（新系统接管管理账）前要定：月结、与 U8C 的分工、经营分析指标口径。"),
    "G2 前": ("fd-g2", "G2（法定账切换）前要定。"),
}
KINDS = ("choice", "approve", "selection", "fact", "param")   # 题型，同 architecture/decision-page/check_pages.py
NO_DEFAULT_KINDS = ("fact", "selection")                      # 事实题、选型题没有默认项：页面显示「未答」，挡住整组提交，不能一键认可
FACT_OPTS = [{"label": "请财务核实", "effect": "由 one-rules 汇总发财务，答复后写回规则卡", "rec": True},
             {"label": "我直接答", "effect": "把答案写在说明里，不再问财务", "rec": False}]


def sheet_fd():
    doc = fd.parse(fd.SRC.read_text(encoding="utf-8"))
    fd.validate(doc)
    groups, items = [], []
    for s in doc["stages"]:
        gid, desc = FD_GROUPS[s["stage"]]
        groups.append({"id": gid, "title": s["stage"], "desc": desc})
        for i in s["items"]:
            items.append({"no": i["id"], "grp": gid, "title": i["title"], "src": i.get("src", ""), "ctx": i.get("ctx", ""),
                          "now": i.get("now", ""), "check": i.get("check", ""), "basis": i["basis"], "practice": i["practice"],
                          "opts": [{"label": o["label"], "effect": o["effect"], "rec": o["rec"]} for o in i["opts"]]})
    groups.append({"id": "fd-facts", "title": "需向财务核实的事实",
                   "desc": "研究替代不了、只有财务知道的事实。默认由 one-rules 汇总发财务；你知道答案的，选「我直接答」并写在说明里。"})
    for f in doc["facts"]:
        items.append({"no": f["id"], "grp": "fd-facts", "title": f["q"], "src": "", "ctx": "影响：" + f["impact"], "now": "", "check": "",
                      "basis": [], "practice": [], "opts": FACT_OPTS})
    ex = doc["example"]
    return {"sheet": "FD", "title": "财务口径决策单",
            "lede": f"共 {sum(len(s['items']) for s in doc['stages'])} 题，按阶段卡点分组，最急的在前；最后一组是 {len(doc['facts'])} 条需向财务核实的事实。每题已预选推荐项，同意就不用动；要改就点别的选项并写理由。每组底部「提交本组」，确认后才算数。",
            "groups": groups, "items": items,
            "info": [{"title": "卷首示例：两本账怎么记", "lines": ex["intro"], "table": ex["rows"], "after": ex["after"]}],
            "side": [{"title": f"已定口径（{len(doc['decided'])} 条，供核对）", "lines": doc["decided"]},
                     {"title": f"同行与监管来源（{len(doc['sources'])} 个）", "lines": [f"{s['name']}：{s['note']}" for s in doc["sources"]]}]}


def load(name):
    if name == "FD":
        return sheet_fd()
    path = HERE / "sheets" / f"{name}.json"
    if not path.exists():
        sys.exit(f"找不到单子 {name}（FD 或 sheets/{name}.json）")
    return json.loads(path.read_text(encoding="utf-8"))


def validate(sheets):
    errs, seen, gids = [], set(), set()
    for sh in sheets:
        for g in sh["groups"]:
            if g["id"] in gids:
                errs.append(f"组 id {g['id']} 重复")
            gids.add(g["id"])
        for i in sh["items"]:
            key = f"{sh['sheet']}~{i['no']}"
            if key in seen:
                errs.append(f"{key}：题号重复")
            seen.add(key)
            if i["grp"] not in {g["id"] for g in sh["groups"]}:
                errs.append(f"{key}：组 {i['grp']} 不存在")
            for k in ("basis", "practice"):
                if not isinstance(i.get(k), list):
                    errs.append(f"{key}：{k} 须是数组（可以为空），缺了页面脚本会抛错、整页空白")
            kind = i.get("kind", "choice")
            if kind not in KINDS:
                errs.append(f"{key}：kind 须取自 {'、'.join(KINDS)}：{kind}")
            recs = sum(bool(o.get("rec")) for o in i["opts"])
            if len(i["opts"]) < 2:
                errs.append(f"{key}：须至少两个选项")
            elif kind in NO_DEFAULT_KINDS and recs != 0:
                errs.append(f"{key}：{kind} 题没有默认项（rec 都为假）：事实题、选型题不预选，用户必须逐题选一项或点「暂不定」")
            elif kind not in NO_DEFAULT_KINDS and recs != 1:
                errs.append(f"{key}：须恰好一个推荐")
        for g in sh["groups"]:
            n = sum(1 for i in sh["items"] if i["grp"] == g["id"])
            if n > 40:
                errs.append(f"组 {g['id']} {n} 题，超过 40 题上限，请拆组")
            if n == 0:
                errs.append(f"组 {g['id']} 没有题")
    if errs:
        sys.exit("决策页校验失败：\n" + "\n".join(errs))


def self_test():
    def sheet(kind, recs):
        item = {"no": "1", "grp": "g", "title": "t", "basis": [], "practice": [], "opts": [{"label": "a", "effect": "", "rec": recs[0]}, {"label": "b", "effect": "", "rec": recs[1]}]}
        if kind:
            item["kind"] = kind
        return [{"sheet": "T", "groups": [{"id": "g", "title": "g", "desc": ""}], "items": [item]}]

    def ok(kind, recs):
        validate(sheet(kind, recs))

    def bad(kind, recs, fragment):
        try:
            validate(sheet(kind, recs))
        except SystemExit as e:
            assert fragment in str(e), (fragment, str(e))
            return
        raise AssertionError(f"{kind} {recs} 应失败：{fragment}")

    ok(None, (True, False)); ok("choice", (True, False)); ok("approve", (True, False)); ok("param", (False, True))
    ok("fact", (False, False)); ok("selection", (False, False))                     # 事实题、选型题没有默认项
    bad(None, (False, False), "恰好一个推荐"); bad("choice", (True, True), "恰好一个推荐")
    bad("fact", (True, False), "没有默认项"); bad("selection", (False, True), "没有默认项"); bad("bogus", (True, False), "kind 须取自")
    template = (HERE / "page-template.html").read_text(encoding="utf-8")
    for needle in ('"open"', "未答", "counts(foot.dataset.g).open", "yes.hidden = open > 0", "if (counts(gid).open)"):
        assert needle in template, f"page-template.html 缺少无默认项题的处理：{needle}"
    print("build_decision_page self-test: ok")


def main():
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        return
    if len(sys.argv) != 3:
        sys.exit("用法：build_decision_page.py <单子>[,<单子>...] <输出路径>")
    sheets = [load(n) for n in sys.argv[1].split(",")]
    validate(sheets)
    items = [dict(i, key=f"{sh['sheet']}~{i['no']}") for sh in sheets for i in sh["items"]]
    data = {"items": items, "groups": [g for sh in sheets for g in sh["groups"]],
            "info": [b for sh in sheets for b in sh.get("info", [])], "side": [b for sh in sheets for b in sh.get("side", [])]}
    title = sheets[0]["title"] if len(sheets) == 1 else "业务口径决策"
    lede = sheets[0]["lede"] if len(sheets) == 1 else f"共 {len(data['groups'])} 组、{len(items)} 题；每题已预选推荐项，每组底部「提交本组」，确认后才算数。"
    dump = json.dumps(data, ensure_ascii=False, separators=(",", ":")).replace("</", "<\\/")
    page = (HERE / "page-template.html").read_text(encoding="utf-8")
    page = page.replace("/*__DATA__*/null", dump).replace("/*__TITLE__*/", title).replace("/*__LEDE__*/", lede)
    # 页面脚本包在独立作用域里：外壳会注入运行环境脚本，全局重名会让页面停在加载中（glossary/gate/README.md）
    if page.count("<script>") != 1:
        sys.exit("模板应只有一段 <script>")
    page = page.replace("<script>", "<script>\n(() => {", 1).replace("</script>", "})();\n</script>", 1)
    if re.search(r"(src|href)\s*=\s*[\"']?https?:|@import|url\(\s*[\"']?https?:", page):
        sys.exit("页面不得加载外部资源（用户：不要外部字体；手机端会卡在加载中）")
    pathlib.Path(sys.argv[2]).write_text(page, encoding="utf-8")
    print(f"{sys.argv[2]}：{len(data['groups'])} 组、{len(items)} 题；" + "；".join(f"{g['title']} {sum(1 for i in items if i['grp'] == g['id'])}" for g in data["groups"]))


if __name__ == "__main__":
    main()
