#!/usr/bin/env python3
"""由 round3-items.json 生成第三轮用语确认的在线页（版式沿用第二轮 glossary/confirm-page/template.html）。
每条三个按钮：同意建议 / 保持原样 / 改为…（原样即批准的条目只有 同意 / 改为…）；未点的算待确认；每组可「全部同意建议」。
选择存在页面数据库 decisions/<编号>（choice: approve | keep | rename，renameTo、note），用语管家会话直接读取。
用法：python3 docs/modernization/glossary/round3/build_round3_page.py <输出路径>"""
import json, pathlib, sys

HERE = pathlib.Path(__file__).parent
DEFAULT_DESC = "多数建议原样转正式；左边标紫色的是建议改名、并入已有正式用语或归为通用词。点「同意建议」即按建议处理。"


def item(g_id, i):
    rec = next(o for o in i["options"] if o["recommended"])
    if i["code"].startswith("DF-"):
        return dict(key=i["code"], no=i["code"], grp=g_id, zh=i["zh"], en=i["en"], say="", to="保持 yaml 现定义",
                    defn=i["definition"], why="原写法：" + rec_alt(i), refs="", fin=False, block="", code="", change=False, opts="def")
    as_is = rec["action"] == "approve" and rec.get("zh") == i["zh"] and rec.get("en") == i["en"]
    zh = rec.get("zh") or i["zh"]
    en = rec.get("en") or i["en"]
    label = rec["label"]
    to = "同意转正式" if as_is else label.split("：", 1)[1] if label.startswith(("批准：", "改为：")) else label
    code = []
    if i.get("changes_approved"):
        code.append("改已批准词条：" + i["changes_approved"])
    if i.get("in_code") and rec["action"] in ("rename", "merge", "common_word"):
        code.append("代码里已出现这个词：改名或并入时先开卡交负责会话，合入后再禁用旧写法")
    alias = i.get("title")
    say = alias.split("→")[0].strip().strip("「」") if alias else ("" if as_is else f"{i['zh']} / {i['en']}")
    return dict(key=i["code"], no=i["code"], grp=g_id, zh=zh, en=en, say=say, sayIsAlias=bool(alias), to=to,
                defn=i["definition"], why=i["reason"], refs=i["standard"], fin=i["accounting"], block=i["blocking"],
                code="；".join(code), change=not as_is, opts="asis" if as_is else "std")


def rec_alt(i):
    return next(o for o in i["options"] if not o["recommended"]).get("definition", "")


def main():
    if len(sys.argv) != 2:
        sys.exit("用法：build_round3_page.py <输出路径>")
    doc = json.loads((HERE / "round3-items.json").read_text(encoding="utf-8"))
    groups, items = [], []
    for n, g in enumerate(doc["groups"], 1):
        gid = f"g{n:02d}"
        groups.append({"id": gid, "title": g["title"], "desc": g.get("intro") or DEFAULT_DESC})
        items += [item(gid, i) for i in g["items"]]
    tpl = (HERE / "page-template.html").read_text(encoding="utf-8")
    dump = lambda v: json.dumps(v, ensure_ascii=False, separators=(",", ":")).replace("</", "<\\/")
    page = tpl.replace("/*__ITEMS__*/[]", dump(items)).replace("/*__GROUPS__*/[]", dump(groups))
    pathlib.Path(sys.argv[1]).write_text(page, encoding="utf-8")
    print(f"{sys.argv[1]}：{len(items)} 条，{len(groups)} 组；原样转正式 {sum(not x['change'] for x in items)} 条")


if __name__ == "__main__":
    main()
