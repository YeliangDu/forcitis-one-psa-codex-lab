#!/usr/bin/env python3
"""业务决策在线页（one-rules）：把待用户定的业务口径做成手机能开的在线页，推荐项预选、按组提交，选择存在在线页数据库（README.md）。
单子：FD = 财务口径决策单（数据源 r0/FINANCE_DECISIONS.md，解析沿用 r0/build_finance_decisions.py）；其余单子为 sheets/<单子>.json（格式见 README.md）；sheets/ 里没有的，再找旧确认页题源 architecture/decision-page/ 一层（同一格式，只读；archive/ 不读），且只认已答页（answers/ 有导出或 answered_pages.txt 已登记），未答的旧页拒绝出页。
决定页生成器只此一套（G2）：出题检查 check_pages.py 在本目录，题型 KINDS 从它导入。
校验不通过即失败：题号唯一、每题至少两个选项且恰好一个推荐、单组不超过 40 题（一屏只画一组，组太大手机上会停在加载中）、页面不加载任何外部资源。
用法：python3 docs/modernization/rules/decide/build_decision_page.py <单子>[,<单子>...] <输出路径>"""
import json, os, pathlib, re, sys

HERE = pathlib.Path(__file__).parent
R0 = HERE.parent.parent / "r0"
sys.path.insert(0, str(R0))
sys.path.insert(0, str(HERE))
import build_finance_decisions as fd  # noqa: E402

FD_GROUPS = {
    "1a 出口": ("fd-1a", "1a 出口前要定：影响现有代码与文档里的写法。"),
    "1b 前": ("fd-1b", "1b 开工前要定：金额舍入、税额、工时成本等平台口径，1b 起所有金额计算都按它走。"),
    "Phase 4 前": ("fd-p4", "Phase 4（收入与两本账）开工前要定：合同、收入、冲销与更正的记账口径。"),
    "G1 前": ("fd-g1", "G1（新系统接管管理账）前要定：月结、与 U8C 的分工、经营分析指标口径。"),
    "G2 前": ("fd-g2", "G2（法定账切换）前要定。"),
}
import check_pages as cp  # noqa: E402  G2 决定页合一：生成器与 check_pages 同在本目录
KINDS = cp.KINDS   # 题型只在出题检查里定义一处；旧页是否已答也用 check_pages 的同一判据（answered_nos）
REPO = pathlib.Path(os.path.abspath(__file__)).parents[4]   # 仓库根（不解析链接；根以上的链接如 /tmp→/private/tmp 允许）
SHEETS_REL = "docs/modernization/rules/decide/sheets"
SHEET_NAME = re.compile(r"[A-Za-z0-9][A-Za-z0-9_-]*")
OLD_RELS = ("docs/modernization/architecture/decision-page",)   # 旧确认页题源（只读，只此一层），按单子名找得到；archive/ 不读：它不在 check_pages 与 light 的范围内
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
            "lede": f"共 {sum(len(s['items']) for s in doc['stages'])} 题，按阶段卡点分组，最急的在前；最后一组是 {len(doc['facts'])} 条需向财务核实的事实。每题已预选推荐项，同意就不用动；要改就点别的选项并写理由。全部选完后到页面最后一个标签「检查答案」，核对后一次提交。",
            "groups": groups, "items": items,
            "info": [{"title": "卷首示例：两本账怎么记", "lines": ex["intro"], "table": ex["rows"], "after": ex["after"]}],
            "side": [{"title": f"已定口径（{len(doc['decided'])} 条，供核对）", "lines": doc["decided"]},
                     {"title": f"同行与监管来源（{len(doc['sources'])} 个）", "lines": [f"{s['name']}：{s['note']}" for s in doc["sources"]]}]}


def symlink_on_path(root, *rel):
    """从 root（不含）往下逐级，返回第一个是符号链接的路径；没有返回 None。"""
    cur = root
    for part in pathlib.PurePosixPath(*rel).parts:
        cur = cur / part
        if cur.is_symlink():
            return cur
    return None


def load(name):
    if name == "FD":
        return sheet_fd()
    # 单子名只许单段文件名：带 /、\、..、绝对路径或空名会落到子目录（如 archive/）或仓库外，再借已答页名绕过检查（058873e46 终审必改 1）
    if not isinstance(name, str) or not SHEET_NAME.fullmatch(name):
        sys.exit(f"单子名须是单段文件名（字母、数字、_、-，不带目录与扩展名）：{name!r}")
    found = [(rel, REPO / rel / f"{name}.json") for rel in (SHEETS_REL,) + OLD_RELS if (REPO / rel / f"{name}.json").is_file()]
    if not found:
        sys.exit(f"找不到单子 {name}（FD、sheets/{name}.json，或已答旧页 architecture/decision-page/{name}.json；archive/ 不读）")
    rel, p = found[0]
    # 不跟随符号链接：已答判断只看文件名，链接可指向未过 check_pages 的坏副本（c33b8fa2c 终审必改 1、74f973acd 首轮必改 1）。
    # 第一道：从仓库根到文件的每一级（含允许目录的上级、允许目录本身、文件）都不许是链接
    link = symlink_on_path(REPO, rel, f"{name}.json")
    if link:
        sys.exit(f"单子 {name} 的路径上有符号链接（{link}）：不跟随链接出页，题源须是仓库里的普通文件")
    # 第二道（独立有效）：路径须正好是 仓库根/固定相对路径/<名>.json，且真实路径等于 realpath(仓库根)/固定相对路径/<名>.json
    if p.parent != REPO / rel or os.path.realpath(p) != os.path.join(os.path.realpath(REPO), rel, f"{name}.json"):
        sys.exit(f"单子 {name} 解析到允许目录以外：{p}")
    old = rel != SHEETS_REL
    if old:   # 旧题源只重出已答历史页，不许借它绕过出题检查发新页
        done, whole = cp.answered_nos(str(p))
        if not (whole or done):
            sys.exit(f"{p.name} 是旧题源但不算已答（answers/{name}.json 无导出、answered_pages.txt 未登记）：旧题源只重出已答历史页；新题写进 sheets/{name}.json 并过 check_pages.py")
    sheet = json.loads(p.read_text(encoding="utf-8"))
    if not isinstance(sheet, dict) or not isinstance(sheet.get("items"), list) or not isinstance(sheet.get("groups"), list):
        sys.exit(f"{p.name} 不是单子格式（须是含 groups、items 两个列表的对象）")
    for key, default in (("sheet", name), ("title", None), ("lede", "按组作答，全部选完后到页面最后一个标签「检查答案」，核对后一次提交。")):
        if not isinstance(sheet.get(key), str) or not sheet[key]:
            if not old:   # sheets/ 的新单子不补默认：缺 sheet、title、lede 即失败
                sys.exit(f"sheets/{name}.json 缺 {key}（或为空）：新单子须写全 sheet、title、lede")
            sheet[key] = default or sheet["sheet"]   # 旧页有的没写：单子名、标题退回文件名，导语退回通用说明
    return sheet


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
    for needle in ('"none"', '"answered"', "未答", "recOf(i) < 0 && pick(i).choice === null", "blocked === 0"):
        assert needle in template, f"page-template.html 缺少无默认项题的处理：{needle}"
    global REPO, SHEET_NAME, symlink_on_path
    # 真实常量：仓库根下固定相对路径；旧题源只有 architecture/decision-page 一层，不含 archive/；路径改错或加回 archive/ 即红（终审建议 1）
    assert OLD_RELS == ("docs/modernization/architecture/decision-page",) and (REPO / OLD_RELS[0]).is_dir(), OLD_RELS
    assert SHEETS_REL == "docs/modernization/rules/decide/sheets" and (REPO / SHEETS_REL).is_dir() and REPO / SHEETS_REL == HERE / "sheets", SHEETS_REL
    # 旧题源只重出已答页（2e48b3e6e 首轮必改 1）：未答的拒绝；已答但不合现行校验的，读得进、validate 照样拒绝；
    # archive/ 不再读：即便登记已答也找不到（c04fabda0 之后调度令）
    import tempfile
    saved = REPO, cp.ANSWERS_DIR, cp.ANSWERED_REGISTRY, SHEET_NAME, symlink_on_path
    with tempfile.TemporaryDirectory() as tmp:
        base = pathlib.Path(tmp)
        R, answers = base / "repo", base / "answers"   # 临时仓库根，按真实的固定相对路径建目录
        old, sheets = R / OLD_RELS[0], R / SHEETS_REL
        (old / "archive").mkdir(parents=True)
        (sheets / "sub").mkdir(parents=True)
        answers.mkdir()
        good = sheet("approve", (True, False))[0]
        two_recs = sheet("choice", (True, True))[0]
        (old / "OLDX.json").write_text(json.dumps(good | {"title": None}), encoding="utf-8")
        (old / "OLDA.json").write_text(json.dumps(good), encoding="utf-8")
        (old / "OLDL.json").write_text("[]", encoding="utf-8")
        (old / "OLDBAD.json").write_text(json.dumps(two_recs), encoding="utf-8")    # 未答、不合规
        (old / "OLDREC.json").write_text(json.dumps(two_recs), encoding="utf-8")    # 已登记已答、不合规
        (old / "archive" / "OLDARC.json").write_text(json.dumps(good), encoding="utf-8")    # 已登记已答、合规，但在 archive/
        (sheets / "NEW.json").write_text(json.dumps(good | {"title": "新单", "lede": "说明"}), encoding="utf-8")
        (sheets / "NOTITLE.json").write_text(json.dumps(good | {"lede": "说明"}), encoding="utf-8")   # sheets/ 缺 title：不补默认，须失败
        (sheets / "sub" / "OLDX.json").write_text(json.dumps(two_recs), encoding="utf-8")   # 子目录里借已登记页名
        (answers / "OLDA.json").write_text(json.dumps({"OLDA~1": {"choice": 0}}), encoding="utf-8")
        (base / "outside.json").write_text(json.dumps(good | {"title": "外", "lede": "外"}), encoding="utf-8")
        (old / "LNK.json").symlink_to("archive/OLDARC.json")          # 旧题源里已登记页名做成链接，指向子目录文件
        (sheets / "SLNK.json").symlink_to(old / "OLDBAD.json")         # sheets/ 里的链接
        (sheets / "OUT.json").symlink_to(base / "outside.json")         # 链接到允许目录以外
        (sheets / "NEWLNK.json").symlink_to("NEW.json")   # sheets/ 里链接指向写全、合规的单子：仍拒（链接目标日后可改而 --changed 不列）
        # 链接在上级：L1 的 architecture/ 与 rules/ 是链接（74f973acd 首轮必改 1）；L2 的允许目录本身是链接
        for root, links in ((base / "L1", ("docs/modernization/architecture", "docs/modernization/rules")),
                            (base / "L2", (OLD_RELS[0], SHEETS_REL))):
            for rel in links:
                (root / rel).parent.mkdir(parents=True, exist_ok=True)
                (root / rel).symlink_to(R / rel, target_is_directory=True)
        (base / "answered.txt").write_text("OLDX  # 出处\nOLDL\nOLDREC\nOLDARC\nLNK\nOLDA\n", encoding="utf-8")
        REPO = R
        cp.ANSWERS_DIR, cp.ANSWERED_REGISTRY = str(answers), str(base / "answered.txt")

        def rejected(names, fragment):
            for name in names:
                try:
                    load(name)
                    raise AssertionError(f"{name!r} 应失败：{fragment}（REPO={REPO}）")
                except SystemExit as e:
                    assert fragment in str(e), (name, fragment, str(e))
        try:
            got = load("OLDX")
            assert (got["sheet"], got["title"]) == ("T", "T") and got["lede"], got   # title 为空补默认
            validate([got])
            validate([load("OLDA")])   # answers/ 有导出也算已答
            assert load("NEW")["title"] == "新单"
            single = "单段文件名"   # 名字带目录、绝对路径、..、空名：第一道（名字校验）就拒（058873e46 终审必改 1）
            for name, fragment in [("OLDL", "不是单子格式"), ("NO-SUCH-SHEET", "找不到单子"), ("OLDBAD", "不算已答"), ("OLDARC", "找不到单子"),
                                   ("NOTITLE", "缺 title"), ("archive/OLDARC", single), ("sub/OLDX", single), (str(old / "OLDA"), single),
                                   (str(old / "archive" / "OLDARC"), single), ("../old/OLDA", single), ("OLDA/..", single), ("..", single),
                                   ("a\\OLDA", single), ("", single), (None, single)]:
                rejected([name], fragment)
            link = "符号链接"   # 路径上任一级是链接：逐级判定拒（c33b8fa2c 终审必改 1、74f973acd 首轮必改 1）
            rejected(["LNK", "SLNK", "OUT", "NEWLNK"], link)
            for root in (base / "L1", base / "L2"):   # 旧题源与 sheets/ 的上级或本身是链接，内容是同一批合规已答页
                REPO = root
                rejected(["NEW", "OLDA", "OLDX"], link)
            # 第二道单独核：名字校验与逐级判定都放开，仍须由「路径与真实路径都锚定仓库根」拒绝
            SHEET_NAME, symlink_on_path = re.compile(r".+"), (lambda *a: None)
            REPO = R
            rejected(["archive/OLDARC", "sub/OLDX", str(old / "archive" / "OLDARC"), "../../../architecture/decision-page/OLDA",
                      "LNK", "SLNK", "OUT", "NEWLNK"], "允许目录以外")
            for root in (base / "L1", base / "L2"):
                REPO = root
                rejected(["NEW", "OLDA", "OLDX"], "允许目录以外")
            REPO, SHEET_NAME, symlink_on_path = R, saved[3], saved[4]
            try:
                validate([load("OLDREC")])
                raise AssertionError("已答但两个推荐的旧页应被 validate 拒绝")
            except SystemExit as e:
                assert "恰好一个推荐" in str(e), str(e)
        finally:
            REPO, cp.ANSWERS_DIR, cp.ANSWERED_REGISTRY, SHEET_NAME, symlink_on_path = saved
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
    flags = {sh.get("preselect", True) for sh in sheets}
    if len(flags) > 1:
        sys.exit("同一页上的单子 preselect 须一致（业务口径单默认预选推荐项；框架、标准、选型类单子写 preselect:false）")
    data = {"preselect": flags.pop(), "items": items, "groups": [g for sh in sheets for g in sh["groups"]],
            "info": [b for sh in sheets for b in sh.get("info", [])], "side": [b for sh in sheets for b in sh.get("side", [])]}
    title = sheets[0]["title"] if len(sheets) == 1 else "业务口径决策"
    lede = sheets[0]["lede"] if len(sheets) == 1 else f"共 {len(data['groups'])} 组、{len(items)} 题；每题已预选推荐项，全部选完后到页面最后一个标签「检查答案」，核对后一次提交。"
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
