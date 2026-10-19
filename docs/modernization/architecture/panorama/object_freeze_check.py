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
CLASSES = {1: "确认建模", 2: "降为参照或维度", 3: "降为属性", 4: "从属对象", 5: "无指向"}


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
    pointed = any(r["pointers"].values())
    if r["objd_class"] == "从属对象":
        return 4, "OBJD 定论从属对象"
    if r["objd_class"] in ("参照数据", "视图"):
        return 2, f"OBJD 定论{r['objd_class']}"
    if not pointed:
        return 5, "无任何节点指向"
    if not r["states"]:
        return 0, "状态写法不是有限状态集（只追加、版本区间等），待对拍判参照、属性或从属"
    return 1, "有状态且被指向"


def self_test():
    assert states_of("草案 → 审批中 → 已签署生效（回签即生效）") == ["草案", "审批中", "已签署生效"]
    assert states_of("只追加") == []
    assert states_of("—") == []
    r = {"objd_class": "业务对象", "pointers": {}, "states": ["a"], "identity_hint": True}
    assert classify(r)[0] == 5
    r["pointers"] = {"06": ["VS-01.1"]}
    assert classify(r)[0] == 1
    r["states"] = []
    assert classify(r)[0] == 0
    print("self-test ok")


def main():
    if "--self-test" in sys.argv:
        self_test()
        return 0
    nodes = yaml.safe_load(PANORAMA.read_text(encoding="utf-8"))["nodes"]
    rows = facts(nodes, anc.compute()["touched"])
    for r in rows.values():
        c, why = classify(r)
        r["initial"] = CLASSES.get(c, "待判")
        r["why"] = why
    OUT_YAML.write_text("# 由 panorama/object_freeze_check.py 生成，勿手改\n" + yaml.safe_dump(rows, allow_unicode=True, sort_keys=False, width=200), encoding="utf-8")
    cnt = Counter(r["initial"] for r in rows.values())
    lines = ["<!-- 由 panorama/object_freeze_check.py 生成，勿手改 -->", "", "# 业务对象冻结：逐对象事实与初判", "",
             "判据见 ADR-0059 与 intent 2026-10-19-171149。指向列：06 阶段、07 流程、08 约束、10 命令、11 事实。", "",
             "初判：" + "、".join(f"{k} {v}" for k, v in cnt.most_common()) + "。", "",
             "| 对象 | 名称 | 层级 | OBJD 分类 | 状态数 | 身份线索 | 06 | 07 | 08 | 10 | 11 | 初判 | 理由 |",
             "|---|---|---|---|---|---|---|---|---|---|---|---|---|"]
    for oid, r in sorted(rows.items(), key=lambda x: int(x[0][4:])):
        p = r["pointers"]
        lines.append(f"| {oid} | {r['name']} | {r['level']} | {r['objd_class']} | {len(r['states'])} | 有 | "
                     + " | ".join(str(len(p.get(k, []))) for k in ("06", "07", "08", "10", "11"))
                     + f" | {r['initial']} | {r['why']} |")
    OUT_MD.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("object-freeze: " + "、".join(f"{k} {v}" for k, v in cnt.most_common()))
    return 0


if __name__ == "__main__":
    sys.exit(main())
