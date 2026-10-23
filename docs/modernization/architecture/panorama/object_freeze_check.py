#!/usr/bin/env python3
"""业务对象冻结：按 ADR-0059 判据对每个有效对象列事实并初判（intent 2026-10-19-171149）。

判据：业务对象 = 有身份标识、有有限状态集，且至少被一个节点指向——06 阶段门或段内状态引用其状态、07 流程作用于它、
10 Command 改变其状态（命令所属聚合对应该对象）、11 业务事实以它为主体（信息图「产生的事实」列）、08 约束作用于它（不变量来源或所属聚合）。
初判五类：1 确认建模；2 降为参照或维度（无状态、只被引用；含 OBJD 定论参照数据、视图）；3 降为属性（脚本不判，交对拍）；
4 从属对象（OBJD 定论）；5 无指向。对象稿登记的对象默认视为有业务标识；状态写法无法解析为有限状态集的（如「只追加」「生效区间版本」）标「待判」，与第 2–5 类一并交对拍。
输出 FREEZE_FACTS.yaml（逐对象事实）与 FREEZE_FACTS.md（汇总）。读 panorama.yaml，先运行 panorama_build.py。
"""
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent
ARCH = HERE.parent
sys.path.insert(0, str(ARCH / "proposals" / "closeout"))
import anchoring_check as anc  # noqa: E402

PANORAMA = HERE / "panorama.yaml"
OUT_YAML = HERE / "FREEZE_FACTS.yaml"
OUT_MD = HERE / "FREEZE_FACTS.md"
OBJ_RE = re.compile(r"OBJ-\d{2,3}")
EMPTY = {"", "—", "-", "无"}
ID_HINT = re.compile(r"编号|号码|代码|唯一|标识|证件|单号|发票号|版本号")
CLASSES = {1: "确认建模", 2: "降为参照或维度", 3: "降为属性", 4: "从属对象", 5: "补 07 或 10（无指向，对象保留）"}
JUDGED = ("06", "07", "08", "10")  # 11 本轮不作判据（10、11 尚不全，冻结后从状态转换推出事实）
FINAL = HERE / "FREEZE_FINAL.yaml"
OUT_IMPACT = HERE / "FREEZE_IMPACT.md"
TERMS = ARCH.parent / "glossary" / "terms.yaml"


def states_of(text):
    text = (text or "").strip()
    if text in EMPTY:
        return []
    body = re.sub(r"（[^）]*）|\([^)]*\)", "", text)
    out = []
    for s in re.split(r"→|/|／|；|;", body):
        s = s.strip(" ：:")
        if s and s not in EMPTY and len(s) <= 12 and not re.search(r"只追加|版本|区间|按|根记录", s):
            out.append(s)
    return list(dict.fromkeys(out))


def facts(nodes, touched):
    pointers = defaultdict(lambda: defaultdict(set))
    for sid, s in nodes["stage"].items():
        for g in s.get("gates") or []:
            pointers[g["obj"]]["06"].add(sid)
        for o in s.get("in_stage") or []:
            pointers[o]["06"].add(sid)
    for pid, objs in touched.items():
        for o in objs:
            pointers[o]["07"].add(pid)
    agg_objs = {a: v.get("objs") or [] for a, v in nodes["agg"].items()}
    for aid, a in nodes["act"].items():
        for o in agg_objs.get(a.get("aggregate") or "", []):
            pointers[o]["10"].add(a["command"])
    for iid, i in nodes["inv"].items():
        for a in i.get("aggs") or []:
            for o in agg_objs.get(a, []):
                pointers[o]["08"].add(iid)
    rows = {}
    for oid, o in nodes["obj"].items():
        st = states_of(o.get("states"))
        p = pointers.get(oid, {})
        if (o.get("facts") or "").strip() not in EMPTY:
            p = dict(p)
            p["11"] = {"信息图「产生的事实」列"}
        text = " ".join(str(o.get(k) or "") for k in ("name", "states", "facts"))
        rows[oid] = {
            "name": o["name"],
            "level": "一级" if o.get("primary") else "次级",
            "objd_class": o.get("class", "业务对象"),
            "parent": o.get("parent"),
            "states": st,
            "identity_hint": True,
            "pointers": {k: sorted(v) for k, v in sorted(p.items())},
            "src": o.get("src"),
        }
    return rows


def classify(r):
    pointed = any(r["pointers"].get(k) for k in JUDGED)
    if r["objd_class"] == "从属对象":
        return 4, "OBJD 定论从属对象"
    if r["objd_class"] in ("参照数据", "视图"):
        return 2, f"OBJD 定论{r['objd_class']}"
    if not pointed:
        return 5, "06、07、08、10 均无指向：先查 07 是否应有流程改其状态，有则补 07 或 10，确无业务动作才进 04 或降级"
    if not r["states"]:
        return 0, "状态写法不是有限状态集（只追加、版本区间等），待对拍判参照、属性或从属"
    return 1, "有状态且被指向"


def fingerprint(nodes_doc):
    import hashlib
    srcs = sorted((nodes_doc.get("meta") or {}).get("sources", {}).values())
    h = hashlib.sha256()
    for s in srcs + ["architecture/ssot/09-processes/catalog.yaml"]:
        f = ARCH.parent / s
        if f.exists():
            h.update(s.encode())
            h.update(f.read_bytes())
    for f in sorted((ARCH / "proposals" / "closeout").glob("*/commands.yaml")) + sorted((ARCH / "proposals" / "closeout").glob("*/processes*.yaml")):
        h.update(f.read_bytes())
    return h.hexdigest()[:16]


ADVICE = {
    "从属": ("门条件改引父对象 {o} 的状态", "流程主要对象改为父对象 {o}，本对象作组成部分", "改挂父对象 {o} 所在聚合", "并入父对象 {o} 的聚合，表保留为组成部分表", "词条 kind 改为组成部分，注明父对象"),
    "参照": ("门条件不得引其状态，改引使用方 {o}", "流程只读引用，不再以它为主要对象", "改挂使用方 {o} 所在聚合，或改为参照数据校验", "按 §14.1 D17 参照数据或 12 维度管理（带版本），表保留", "词条注明参照数据或维度"),
    "不承接": ("门条件删去对它的引用", "流程中引用它的步骤改为引用制度版本号或文本（参照），不再作对象", "改挂实际执行规则的聚合，或删除并写明理由", "撤销聚合，不建表", "词条注明不建模、为制度文本"),
    "事实": ("门条件改引主体 {o} 的状态", "流程步骤改为「记录事实」，主要对象改为主体 {o}", "改挂主体 {o} 所在聚合", "聚合保留为只追加事实记录，表保留", "词条 kind 改为业务事实"),
}


def impact(nodes, touched, final):
    agg_of = defaultdict(list)
    for aid, a in nodes["agg"].items():
        for o in a.get("objs") or []:
            agg_of[o].append(aid)
    terms = yaml.safe_load(TERMS.read_text(encoding="utf-8"))["terms"]
    out = ["<!-- 由 panorama/object_freeze_check.py 生成，勿手改 -->", "", "# 业务对象冻结：降级对象的影响清单", "",
           "每个不作业务对象的对象，列出引用它的 06 门条件、07 流程、08 不变量、聚合与表、词条，以及各自怎么改。不变量不得因降级变成孤儿：改挂承接对象所在聚合，或删除并写明理由。", ""]
    dims = []
    for oid in sorted(final, key=lambda x: int(x[4:])):
        e = final[oid]
        cls = e["class"]
        if cls == "确认建模":
            continue
        kind = "从属" if cls.startswith("从属") or cls.startswith("并入") else "事实" if "业务事实" in cls else "不承接" if cls.startswith("不承接") else "参照"
        owner = e.get("owner") or ("（无，04 不承接）" if cls.startswith("不承接") else "（无，视图）")
        name = nodes["obj"].get(oid, {}).get("name", oid)
        adv = [a.format(o=owner) for a in ADVICE[kind]]
        same = bool(set(agg_of.get(oid, [])) & set(agg_of.get(e.get("owner") or "", [])))
        if same:
            adv[2] = f"已与 {owner} 同在聚合内，挂接不变，只把规则的主语改为 {owner}"
            adv[3] = f"已与 {owner} 同在聚合内，聚合不变；表随 {owner} 设计"
        stages = sorted(s for s, st in nodes["stage"].items() if any(g["obj"] == oid for g in st.get("gates") or []) or oid in (st.get("in_stage") or []))
        prcs = sorted((p for p, objs in touched.items() if oid in objs), key=lambda x: int(x[4:]))
        aggs = agg_of.get(oid, [])
        invs = sorted({i for i, v in nodes["inv"].items() if set(v.get("aggs") or []) & set(aggs)}, key=lambda x: (int(x[4:].rstrip("abcdefghijklmnopqrstuvwxyz")), x))
        tables = sorted({t for a in aggs for t in nodes["agg"][a].get("tables") or []})
        cmds = sorted({a["command"] for a in nodes["act"].values() if a.get("aggregate") in aggs})
        tws = [f"{x['id']}（{x['zh']}）" for x in terms if x.get("zh") and (x["zh"] == name.split("（")[0])]
        if kind == "参照":
            dims.append(f"{oid} {name}（使用方 {owner}）")
        out += [f"## {oid} {name}：{cls}（承接：{owner}）", "",
                "| 引用方 | 条目 | 改法 |", "|---|---|---|",
                f"| 06 门条件与段内状态 | {'、'.join(stages) or '无'} | {adv[0] if stages else '—'} |",
                f"| 07 流程 | {'、'.join(prcs) or '无'} | {adv[1] if prcs else '—'} |",
                f"| 10 命令 | {'、'.join(cmds) or '无'} | {('撤销这些命令，骨架中引用它们的步骤改为引用制度版本或删除' if kind == '不承接' else (f'已与 {owner} 同在聚合内，命令不动' if same else f'移入承接对象 {owner} 所在聚合')) if cmds else '—'} |",
                f"| 08 不变量 | {'、'.join(invs) or '无'} | {adv[2] if invs else '—'} |",
                f"| 聚合与表 | {'、'.join(aggs) or '无'}；表 {'、'.join(tables) or '无'} | {adv[3] if aggs else '—'} |",
                f"| 词条 | {'、'.join(tws) or '无'} | {adv[4] if tws else '—'} |", ""]
    out += ["## 12 维度候选（降为参照或维度的对象）", "", *[f"- {d}" for d in dims], ""]
    return "\n".join(out)


def self_test():
    assert states_of("草案 → 审批中 → 已签署生效（回签即生效）") == ["草案", "审批中", "已签署生效"]
    assert states_of("只追加") == []
    assert states_of("—") == []
    r = {"objd_class": "业务对象", "pointers": {"11": ["x"]}, "states": ["a"], "identity_hint": True}
    assert classify(r)[0] == 5, "11 不作判据"
    r["pointers"] = {"06": ["VS-01.1"]}
    assert classify(r)[0] == 1
    r["states"] = []
    assert classify(r)[0] == 0
    print("self-test ok")


def main():
    if "--self-test" in sys.argv:
        self_test()
        return 0
    doc = yaml.safe_load(PANORAMA.read_text(encoding="utf-8"))
    nodes = doc["nodes"]
    touched = anc.compute()["touched"]
    rows = facts(nodes, touched)
    base = fingerprint(doc)
    for r in rows.values():
        c, why = classify(r)
        r["initial"] = CLASSES.get(c, "待判")
        r["why"] = why
    OUT_YAML.write_text("# 由 panorama/object_freeze_check.py 生成，勿手改\n" + yaml.safe_dump(rows, allow_unicode=True, sort_keys=False, width=200), encoding="utf-8")
    cnt = Counter(r["initial"] for r in rows.values())
    lines = ["<!-- 由 panorama/object_freeze_check.py 生成，勿手改 -->", "", "# 业务对象冻结：逐对象事实与初判", "",
             f"判据见 ADR-0059 决定 8。基线：06、07、08、10 的输入文件指纹 `{base}`（三组骨架合并后；同一指纹即同一基线）。判据只用 06、07、08、10；11 本轮只列不判。", "",
             "初判：" + "、".join(f"{k} {v}" for k, v in cnt.most_common()) + "。", "",
             "| 对象 | 名称 | 层级 | OBJD 分类 | 状态数 | 身份线索 | 06 | 07 | 08 | 10 | 11 | 初判 | 理由 |",
             "|---|---|---|---|---|---|---|---|---|---|---|---|---|"]
    for oid, r in sorted(rows.items(), key=lambda x: int(x[0][4:])):
        p = r["pointers"]
        lines.append(f"| {oid} | {r['name']} | {r['level']} | {r['objd_class']} | {len(r['states'])} | 有 | "
                     + " | ".join(str(len(p.get(k, []))) for k in ("06", "07", "08", "10", "11"))
                     + f" | {r['initial']} | {r['why']} |")
    OUT_MD.write_text("\n".join(lines) + "\n", encoding="utf-8")
    if FINAL.exists():
        OUT_IMPACT.write_text(impact(nodes, touched, yaml.safe_load(FINAL.read_text(encoding="utf-8"))) + "\n", encoding="utf-8")
    print("object-freeze: " + "、".join(f"{k} {v}" for k, v in cnt.most_common()))
    return 0


if __name__ == "__main__":
    sys.exit(main())
