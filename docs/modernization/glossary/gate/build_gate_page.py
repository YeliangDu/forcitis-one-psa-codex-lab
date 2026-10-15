#!/usr/bin/env python3
"""开工前术语门禁的小决策页（SESSION_PLAYBOOK §5）：一张卡一份数据 gate/<卡号>.json，生成只含本卡新词的在线确认页。
版式与第三轮用语确认页相同（page-template.html）：每条 同意建议 / 保持原样 / 改为…，选择存在固定在线页数据库的集合 gate-<卡号>/<编号>（发布到 README 写明的固定链接，手机端新链接首次加载可能卡住）。
数据格式见 gate/README.md；校验不通过即失败。
固定页可同时挂多张单子（逗号分隔），每张一组、答复各存 gate-<卡号> 集合；其他会话的决策单（如架构决策）也用同一格式交 one-glossary 一起发布。
用法：python3 docs/modernization/glossary/gate/build_gate_page.py <卡号>[,<卡号>...] <输出路径>"""
import json, pathlib, re, sys

HERE = pathlib.Path(__file__).parent
ACTIONS = {"approve", "rename", "merge", "common_word", "defer", "keep", "redefine"}
VERDICTS = {"正式", "方言", "待定"}


def load(card):
    path = next((d / f"{card}.json" for d in (HERE, HERE.parent / "acl", HERE.parent / "round3" / "sheets") if (d / f"{card}.json").exists()), None)
    if path is None:
        sys.exit(f"找不到 {card}.json（gate/、acl/ 或 round3/sheets/）")
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
    if len(sys.argv) != 3:
        sys.exit("用法：build_gate_page.py <卡号>[,<卡号>...] <输出路径>")
    docs = [load(card) for card in sys.argv[1].split(",")]
    items, groups = [], []
    for doc in docs:
        mine = [page_item(doc["card"], i) for i in doc["items"]]
        items += mine
        groups.append({"id": collection(doc["card"]), "title": f"{doc.get('title') or doc['card'] + ' 本卡新词'}（{len(mine)} 项）",
                       "desc": doc["lede"] + " 点「同意建议」即按建议定；有不同意见点「改为…」写上你要的叫法。"})
    biggest = max(len(d["items"]) for d in docs)
    if biggest > 120:
        sys.exit(f"单组 {biggest} 条，超过 120 条上限：页面默认一屏只画一组，组太大手机上会停在加载中，请拆组")
    lede = docs[0]["lede"] if len(docs) == 1 else f"当前待确认 {len(docs)} 组、共 {len(items)} 条，用上方「分组」切换，每组底部有「下一组」。选择自动存为草稿，选完点左侧「提交」。"
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
    pathlib.Path(sys.argv[2]).write_text(page, encoding="utf-8")
    print(f"{sys.argv[2]}：" + "；".join(f"{d['card']} {len(d['items'])} 项" for d in docs))


if __name__ == "__main__":
    main()
