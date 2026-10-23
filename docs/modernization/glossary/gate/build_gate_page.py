#!/usr/bin/env python3
"""开工前术语门禁的小决策页（SESSION_PLAYBOOK 附 M6）：一张卡一份数据 gate/<卡号>.json，生成只含本卡新词的在线确认页。
版式与第三轮用语确认页相同（page-template.html）：每条 同意建议 / 保持原样 / 改为…，选择存在固定在线页数据库的集合 gate-<卡号>/<编号>（发布到 README 写明的固定链接，手机端新链接首次加载可能卡住）。
数据格式见 gate/README.md；校验不通过即失败。
固定页可同时挂多张单子（逗号分隔），每张一组、答复各存 gate-<卡号> 集合；其他会话的决策单（如架构决策）也用同一格式交 one-glossary 一起发布。
用法：python3 docs/modernization/glossary/gate/build_gate_page.py <卡号>[,<卡号>...] <输出路径> [--by-bc]
<卡号>:<编号>+<编号>：只出该卡的这几条（其余留到下一次），如 R-1b-0:G01+G02+G03。
--by-bc：新系统用语按条目的 bc 分组显示，ACL 单（卡号 ACL-*）保持按归类分组（用户 2026-10-15：「我希望用语按照bc进行汇集」「新系统用语按照bc，acl还是按照你现在的规划」），每组各自提交。"""
import json, pathlib, re, sys

HERE = pathlib.Path(__file__).parent
ACTIONS = {"approve", "rename", "merge", "common_word", "defer", "keep", "redefine"}
VERDICTS = {"正式", "方言", "待定"}


def load(card):
    path = next((d / f"{card}.json" for d in (HERE, HERE.parent / "acl", HERE.parent / "round3" / "sheets", HERE.parent / "homonym", HERE.parent / "b1") if (d / f"{card}.json").exists()), None)
    if path is None:
        sys.exit(f"找不到 {card}.json（gate/、acl/、round3/sheets/、homonym/ 或 b1/）")
    doc = json.loads(path.read_text(encoding="utf-8"))
    errs = []
    for k in ("card", "session", "date", "lede", "items"):
        if not doc.get(k):
            errs.append(f"缺 {k}")
    if doc.get("card") != card:
        errs.append(f"card 应为 {card}")
    codes = set()
    for i in doc.get("items", []):
        where = i.get("code", "?")
        for k in ("code", "zh", "en", "kind", "definition", "verdict", "refs", "why", "options"):
            if not i.get(k):
                errs.append(f"{where}：缺 {k}")
        if not re.fullmatch(r"(G|[A-Z]{2}-)\d{2,3}", i.get("code", "")):
            errs.append(f"{where}：编号形如 G01 或 AP-01")
        if where in codes:
            errs.append(f"{where}：编号重复")
        codes.add(where)
        if i.get("verdict") not in VERDICTS:
            errs.append(f"{where}：verdict 只取 {sorted(VERDICTS)}")
        opts = i.get("options") or []
        if sum(bool(o.get("recommended")) for o in opts) != 1:
            errs.append(f"{where}：推荐项须恰好一个")
        for o in opts:
            if o.get("action") not in ACTIONS:
                errs.append(f"{where}：未知 action {o.get('action')}")
            if o.get("action") in ("approve", "rename") and not (o.get("zh") and o.get("en")):
                errs.append(f"{where}：{o.get('action')} 须给出 zh 与 en")
            if o.get("action") == "merge" and not o.get("into") and not o.get("label"):
                errs.append(f"{where}：merge 须给出 into")
    if errs:
        sys.exit(f"{card}.json 校验失败：\n" + "\n".join(errs))
    return doc


def collection(card):
    """每张卡的答复存在固定在线页（GATE_URL）数据库的独立集合，避免与其他卡、第三轮答复混在一起。"""
    return "gate-" + re.sub(r"[^A-Za-z0-9-]", "-", card)


def page_item(card, i):
    rec = next(o for o in i["options"] if o["recommended"])
    # 归类类单子（opts: std）：推荐不改名也要给「保持原样」，并显示推荐的归类结论
    as_is = i.get("opts") != "std" and rec["action"] == "approve" and rec["zh"] == i["zh"] and rec["en"] == i["en"]
    label = rec.get("label") or (f"{rec.get('zh')} / {rec.get('en')}" if rec["action"] in ("approve", "rename") else rec["action"])
    values = "；".join(f"{v['zh']} {v['en']}" for v in i.get("values") or [])
    coll = collection(card)
    return dict(key=f"{coll}~{i['code']}", coll=coll, no=i["code"], grp=coll, zh=rec.get("zh") or i["zh"], en=rec.get("en") or i["en"],
                say="" if as_is else f"{i['zh']} / {i['en']}", sayIsAlias=False, to="同意转正式" if as_is else label,
                defn=i["definition"] + (f"（取值：{values}）" if values else ""), why=i["why"], refs=i["refs"],
                fin=bool(i.get("accounting")), block=i.get("block", ""), code=i.get("code_note", ""), change=not as_is, opts="asis" if as_is else "std")


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if len(args) != 2:
        sys.exit("用法：build_gate_page.py <卡号>[:<编号>+...][,<卡号>...] <输出路径> [--by-bc]")
    by_bc = "--by-bc" in sys.argv
    docs = []
    for spec in args[0].split(","):
        card, _, only = spec.partition(":")
        doc = load(card)
        if only:
            want = only.split("+")
            unknown = [c for c in want if c not in {i["code"] for i in doc["items"]}]
            if unknown:
                sys.exit(f"{card} 没有条目 {'、'.join(unknown)}")
            doc["items"] = [i for i in doc["items"] if i["code"] in want]
        docs.append(doc)
    items, groups = [], []
    for doc in docs:
        mine = [page_item(doc["card"], i) for i in doc["items"]]
        keep = doc["card"].startswith("ACL-")   # 用户：「新系统用语按照bc，acl还是按照你现在的规划」——ACL 单保持按归类分组
        if by_bc and not keep:
            for x, i in zip(mine, doc["items"]):
                x["grp"] = "bc-" + re.sub(r"[^A-Za-z0-9-]", "-", i.get("bc") or "none")
                x["bc"] = i.get("bc") or "（未定模块）"
                x["sheet"] = doc.get("title") or doc["card"]
        items += mine
        if not by_bc or keep:
            groups.append({"id": collection(doc["card"]), "title": f"{doc.get('title') or doc['card'] + ' 本卡新词'}（{len(mine)} 项）",
                           "desc": doc["lede"] + " 点「同意建议」即按建议定；有不同意见点「改为…」写上你要的叫法。"})
    if by_bc:
        # 用户 2026-10-15：「我希望用语按照bc进行汇集」——按模块分组，组内各条注明来自哪张单子；每组各自提交
        fixed = [g["id"] for g in groups]
        order = sorted({x["grp"]: x["bc"] for x in items if x["grp"] not in fixed}.items(), key=lambda kv: -sum(1 for x in items if x["grp"] == kv[0]))
        for gid, bc in order:
            mine = [x for x in items if x["grp"] == gid]
            parts = {}
            for x in mine:
                parts[x["sheet"]] = parts.get(x["sheet"], 0) + 1
            groups.append({"id": gid, "title": f"{bc}（{len(mine)} 项）",
                           "desc": "本模块待确认：" + "；".join(f"{k} {v} 条" for k, v in parts.items()) + "。点「同意建议」即按建议定；有不同意见点「改为…」。选完点本组底部「提交本组」。"})
        items.sort(key=lambda x: [g["id"] for g in groups].index(x["grp"]))
    biggest = max(sum(1 for x in items if x["grp"] == g["id"]) for g in groups)
    if biggest > 120:
        sys.exit(f"单组 {biggest} 条，超过 120 条上限：页面默认一屏只画一组，组太大手机上会停在加载中，请拆组")
    lede = docs[0]["lede"] if len(docs) == 1 and not by_bc else f"当前待确认 {len(groups)} 组、共 {len(items)} 条，用「分组」切换，每组底部有「提交本组」和「下一组」。选择先存为草稿，提交并确认后才算数。"
    dump = lambda v: json.dumps(v, ensure_ascii=False, separators=(",", ":")).replace("</", "<\\/")
    tpl = (HERE / "page-template.html").read_text(encoding="utf-8")
    page = (tpl.replace("/*__ITEMS__*/[]", dump(items)).replace("/*__GROUPS__*/[]", dump(groups))
            .replace("本卡用语确认 /*__CARD__*/", docs[0]["card"] + " 本卡用语确认" if len(docs) == 1 else "FORCITIS one 用语确认")
            .replace("<h1>本卡用语确认</h1>", "<h1>本卡用语确认</h1>" if len(docs) == 1 else "<h1>用语确认</h1>").replace("/*__LEDE__*/", lede))
    # 页面脚本包在独立作用域里，不往全局放任何名字：在线页里外壳会注入运行环境脚本，全局重名会让它报错、页面一直停在加载中（2026-10-15 实测）
    if page.count("<script>") != 1:
        sys.exit("模板应只有一段 <script>")
    page = page.replace("<script>", "<script>\n(() => {", 1).replace("</script>", "})();\n</script>", 1)
    if re.search(r"https?://", page):
        sys.exit("页面不得引用任何外部地址（用户：不要外部字体；手机端会卡在加载中）")
    pathlib.Path(args[1]).write_text(page, encoding="utf-8")
    print(f"{args[1]}：{len(groups)} 组；" + "；".join(f"{d['card']} {len(d['items'])} 项" for d in docs))


if __name__ == "__main__":
    main()
