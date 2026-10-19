#!/usr/bin/env python3
"""全景追溯模型：按登记网络 v5 的 15 个节点（ADR-0059）从现有源文件生成 panorama.yaml，检查编号 Nxx-n 对应节点，缺口写 GAPS.md。

节点：01 干系人 · 02 关注点 · 03 价值主张 · 04 取舍决定 · 05 业务能力 · 06 价值流与阶段 · 07 业务流程 · 08 约束与认定规则 ·
09 业务对象 · 10 受控业务执行 · 11 业务事实 · 12 维度 · 13 指标与评价 · 14 计算绑定 · 15 决策与行动。
聚合、BC、子域、表作为 09、10 的领域与实现层映射（检查编号 N09-Mn、N10-Mn）。每条链接带出处（源文件与定位）。
源：proposals/value-streams.v2.yaml、ENTERPRISE_INFO_MAP_DRAFT.md §1、closeout/process-catalog.yaml、
ENTERPRISE_AGGREGATES_DRAFT.md §1.2 / §2.2 / §3.2、ddd/aggregates.yaml、ddd/subdomains.yaml、glossary/terms.yaml（kind=metric）、
data/PANORAMA_TABLES.yaml（one-data 出，缺失时表层只用 ddd/aggregates.yaml 的已建表）。
缺口只报告、不使构建失败：反复跑到收敛；--check 由 build_all 保证生成物可复现。
"""
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent
ARCH = HERE.parent
sys.path.insert(0, str(ARCH / "proposals" / "closeout"))
import anchoring_check as anc  # noqa: E402  流程归集与对象承载的判定与 anchoring_check C1、C2 共用一份代码
import object_layer_check as olc  # noqa: E402
DOCS = ARCH.parent
SRC = {
    "vs": ARCH / "proposals" / "value-streams.v2.yaml",
    "obj": ARCH / "proposals" / "ENTERPRISE_INFO_MAP_DRAFT.md",
    "prc": ARCH / "proposals" / "closeout" / "process-catalog.yaml",
    "agg": ARCH / "proposals" / "ENTERPRISE_AGGREGATES_DRAFT.md",
    "built": ARCH / "ddd" / "aggregates.yaml",
    "sd": ARCH / "ddd" / "subdomains.yaml",
    "terms": DOCS / "glossary" / "terms.yaml",
    "tables": DOCS / "data" / "PANORAMA_TABLES.yaml",
}
OUT_MODEL = HERE / "panorama.yaml"
OUT_GAPS = HERE / "GAPS.md"
OBJ_RE = re.compile(r"OBJ-\d{2,3}")
AGG_RE = re.compile(r"AGG-\d{2,3}")
INV_RE = re.compile(r"INV-\d{2,3}")
INACTIVE_PRC = {"retired", "merged"}

UNAUTOMATED = {"N07-P1": "三组骨架（命令、状态机）未整合进本分支"}
LAYER_NODE = {"sh": "01", "con": "02", "vp": "03", "decr": "04", "cap": "05", "vs": "06", "stage": "06", "prc": "07", "inv": "08",
              "obj": "09", "act": "10", "fact": "11", "dim": "12", "metric": "13", "bind": "14", "dec": "15",
              "agg": "09/10 映射", "bc": "09/10 映射", "sd": "09/10 映射", "table": "09/10 映射"}
NODES = {"01": "干系人", "02": "关注点", "03": "价值主张", "04": "取舍决定", "05": "业务能力", "06": "价值流与阶段", "07": "业务流程", "08": "约束与认定规则", "09": "业务对象", "10": "受控业务执行", "11": "业务事实", "12": "维度", "13": "指标与评价", "14": "计算绑定", "15": "决策与行动"}
CHECKS = {
    "N01-1": "每个干系人都登记了关注点（02 CON 未登记时全部计为缺口）",
    "N03-1": "每条价值流都有价值主张（暂以价值流的 value 字段作 03 的登记）",
    "N05-1": "每个阶段都有业务能力支撑（阶段 capabilities 字段）",
    "N06-1": "每个阶段都有对象状态作为门（进入或退出条件引用对象状态）",
    "N07-1": "每个有效流程都能归集到价值流：所标阶段或支撑本段，且有作用对象（同 anchoring_check C1）",
    "N07-2": "流程主要对象引用的编号存在且是有效对象（主要对象为空不算缺口：作用对象由 N07-1 / anchoring C1 判）",
    "N07-P1": "P-1：流程中改变对象状态的步骤都声明 Command（三组骨架未整合进本分支，暂未自动化）",
    "N08-1": "每个不变量都归属存在的聚合",
    "N09-1": "每个有效对象都有流程承载（同 anchoring_check C2；OBJD 定论的从属、参照、视图按定论排除）",
    "N09-2": "每个有效一级业务对象都有聚合（聚合「对应」列；从属、参照、视图不要求）",
    "N09-3": "每个聚合对应的对象都出现在某个阶段的门或段内状态中",
    "N09-M1": "每个聚合都归属一个 BC",
    "N09-M2": "每个聚合都有不变量",
    "N09-M3": "每个聚合至少一张表（ADR-0059 建表规则，不要求每个对象一张）",
    "N09-M4": "每张表都能追到聚合，聚合能追到业务对象",
    "N10-1": "每个受控业务执行（命令）都有 owner BC",
    "N10-M1": "每个 BC 都归属一个子域（模块命名空间可在 subdomains.yaml 找到）",
    "N11-1": "每个一级业务对象都声明了产生的业务事实（信息图「产生的事实」列）",
    "N13-1": "每个阶段都有指标",
    "N13-2": "每个指标都能追到阶段或流程",
    "N13-3": "度量链：每个指标都有业务事实来源与计算绑定（12、14 未登记时计为缺口）",
}


def expand(prefix, text):
    """展开简写：「AGG-01、02、33」「AGG-03–07」「INV-10-12」都按前缀补全。"""
    out = []
    for m in re.finditer(rf"{prefix}-(\d{{2,3}})((?:\s*[、，,/]\s*\d{{2,3}}(?![\d-])|\s*[–—~-]\s*\d{{2,3}})*)", text):
        cur = int(m.group(1))
        width = len(m.group(1))
        out.append(cur)
        for sep, num in re.findall(r"\s*([、，,/]|[–—~-])\s*(\d{2,3})", m.group(2)):
            n = int(num)
            if sep in "–—~-" and n > cur:
                out.extend(range(cur + 1, n + 1))
            else:
                out.append(n)
            cur = n
    seen = []
    for n in out:
        k = f"{prefix}-{n:0{width}d}"
        if k not in seen:
            seen.append(k)
    return seen


def rel(p):
    return str(Path(p).relative_to(DOCS))


def cells(line):
    return [c.strip() for c in line.strip().strip("|").split("|")]


def md_rows(text, start_heading, end_heading, first_re):
    s = text.index(start_heading)
    e = text.index(end_heading, s) if end_heading else len(text)
    rows = []
    for no, line in enumerate(text[:e].splitlines()[text[:s].count("\n"):], text[:s].count("\n") + 1):
        if line.startswith("|"):
            c = cells(line)
            m = re.match(first_re, c[0].replace("*", "")) if c else None
            if m:
                rows.append((m.group(0), c, no))
    return rows


def load():
    m = {k: {} for k in ("sh", "con", "vp", "decr", "cap", "vs", "stage", "prc", "inv", "obj", "act", "fact", "dim",
                         "metric", "bind", "dec", "agg", "bc", "sd", "table")}
    links = []

    def link(kind, a, b, src):
        links.append({"kind": kind, "from": a, "to": b, "src": src})

    vsd = yaml.safe_load(SRC["vs"].read_text(encoding="utf-8"))
    for s in vsd.get("stakeholders") or []:
        m["sh"][s["id"]] = {"name": s.get("sub") or s.get("category"), "category": s.get("category")}
    for v in vsd["value_streams"]:
        m["vs"][v["id"]] = {"name": v["name"], "category": v.get("category"), "value": v.get("value")}
        if v.get("value"):
            m["vp"][f"VP-{v['id'][3:]}"] = {"name": v["value"], "vs": v["id"], "provisional": "取自价值流 value 字段，待 03 正式登记"}
            link("vp-vs", f"VP-{v['id'][3:]}", v["id"], f"{rel(SRC['vs'])}#{v['id']}.value")
        for st in v.get("stages") or []:
            gates = []
            for side in ("entry", "exit"):
                cond = st.get(side) or {}
                for k in ("any", "all"):
                    for g in cond.get(k) or []:
                        if OBJ_RE.fullmatch(str(g.get("ref", ""))):
                            gates.append({"side": side, "obj": g["ref"], "state": g.get("state")})
            inside = [g["ref"] for g in st.get("in_stage_states") or [] if OBJ_RE.fullmatch(str(g.get("ref", "")))]
            caps = st.get("capabilities") or []
            m["stage"][st["id"]] = {"name": st["name"], "vs": v["id"], "gates": gates, "in_stage": inside, "caps": caps}
            for c in caps:
                m["cap"].setdefault(c, {"stages": []})["stages"].append(st["id"])
                link("cap-stage", c, st["id"], f"{rel(SRC['vs'])}#{st['id']}.capabilities")
            link("vs-stage", v["id"], st["id"], f"{rel(SRC['vs'])}#{st['id']}")
            for g in gates:
                link(f"stage-gate-{g['side']}", st["id"], g["obj"], f"{rel(SRC['vs'])}#{st['id']}.{g['side']}")

    text = SRC["obj"].read_text(encoding="utf-8")
    valid = anc.info_objects(text)
    disp = olc.object_dispositions(text)
    kinds = {"subordinate": "从属对象", "reference_data": "参照数据", "view": "视图"}
    for oid, c, no in md_rows(text, "## 1. 总表", "### 1.1", r"OBJ-\d{2,3}"):
        if oid not in valid:
            continue
        level = c[3] if len(c) > 3 else ""
        d = disp.get(oid)
        m["obj"][oid] = {"name": c[1].replace("*", ""), "active": True, "primary": level.startswith("一级"),
                         "class": kinds[d["kind"]] if d else "业务对象", "parent": d.get("parent_object") if d else None,
                         "states": c[5] if len(c) > 5 else "", "facts": c[8].strip() if len(c) > 8 else "",
                         "src": f"{rel(SRC['obj'])}:{no}"}

    for p in yaml.safe_load(SRC["prc"].read_text(encoding="utf-8"))["processes"]:
        po = str(p.get("primary_object") or "")
        m["prc"][p["id"]] = {"name": p["name"], "type": p.get("type"), "stages": p.get("stages") or [],
                             "primary_object": OBJ_RE.findall(po), "bc": p.get("owner_bc"), "group": p.get("group")}
        if p.get("type") in INACTIVE_PRC:
            continue
        for s in p.get("stages") or []:
            link("stage-prc", s, p["id"], f"{rel(SRC['prc'])}#{p['id']}.stages")
        for o in OBJ_RE.findall(po):
            link("obj-prc", o, p["id"], f"{rel(SRC['prc'])}#{p['id']}.primary_object")

    text = SRC["agg"].read_text(encoding="utf-8")
    for iid, c, no in md_rows(text, "### 1.2", "### 1.3", r"INV-\d{2,3}"):
        aggs = expand("AGG", c[3] if len(c) > 3 else "")
        m["inv"][iid] = {"aggs": aggs, "src": f"{rel(SRC['agg'])}:{no}"}
        for a in aggs:
            link("agg-inv", a, iid, f"{rel(SRC['agg'])}:{no}")
    for aid, c, no in md_rows(text, "### 2.2", "### 2.3", r"AGG-\d{2,3}"):
        objs = OBJ_RE.findall(" ".join(c[1:4]))
        m["agg"][aid] = {"name": re.sub(r"（.*", "", c[1]), "objs": sorted(set(objs), key=objs.index),
                         "invs": expand("INV", c[4] if len(c) > 4 else ""), "bc": None, "tables": [],
                         "src": f"{rel(SRC['agg'])}:{no}"}
        for o in m["agg"][aid]["objs"]:
            link("obj-agg", o, aid, f"{rel(SRC['agg'])}:{no}")
    for bid, c, no in md_rows(text, "### 3.2", "### 3.3", r"B-\d{2,3}"):
        mod = re.search(r"`([a-z_]+\.[a-z_]+)`", c[2] if len(c) > 2 else "")
        aggs = expand("AGG", c[4] if len(c) > 4 else "")
        m["bc"][bid] = {"name": c[1], "module": mod.group(1) if mod else None, "aggs": aggs,
                        "src": f"{rel(SRC['agg'])}:{no}"}
        for a in aggs:
            if a in m["agg"]:
                m["agg"][a]["bc"] = bid
            link("bc-agg", bid, a, f"{rel(SRC['agg'])}:{no}")

    for s in yaml.safe_load(SRC["sd"].read_text(encoding="utf-8"))["subdomains"]:
        m["sd"][s["id"]] = {"name": s.get("zh")}
    for bid, b in m["bc"].items():
        ns = (b["module"] or "").split(".")[0]
        b["subdomain"] = ns if ns in m["sd"] else None
        if b["subdomain"]:
            link("sd-bc", ns, bid, f"{rel(SRC['sd'])}#{ns}")

    for a in yaml.safe_load(SRC["built"].read_text(encoding="utf-8"))["aggregates"]:
        for cmd in (a.get("commands") or []) + (a.get("commands_via") or []):
            m["act"][f"{a['bc']}/{cmd}"] = {"command": cmd, "aggregate": a["id"], "bc": a.get("bc"),
                                            "src": f"{rel(SRC['built'])}#{a['id']}"}
    mod_to_bc = {b["module"]: bid for bid, b in m["bc"].items() if b["module"]}
    if SRC["tables"].exists():
        rows = yaml.safe_load(SRC["tables"].read_text(encoding="utf-8")) or []
        tsrc = rel(SRC["tables"])
    else:
        rows, tsrc = [], rel(SRC["built"])
        for a in yaml.safe_load(SRC["built"].read_text(encoding="utf-8"))["aggregates"]:
            for t in a.get("tables") or []:
                rows.append({"table": t, "bc": a["bc"], "aggregate": a["id"], "obj": None})
    for r in rows:
        t = r.get("table") or f"{r.get('schema')}.{r.get('name')}"
        m["table"][t] = {"bc": r.get("bc"), "aggregate": r.get("aggregate"), "obj": r.get("obj"), "kind": r.get("kind")}
        for a in m["agg"].values():
            if r.get("obj") and r["obj"] in a["objs"]:
                a["tables"].append(t)
                link("agg-table", None, t, tsrc)
                break

    for t in yaml.safe_load(SRC["terms"].read_text(encoding="utf-8"))["terms"]:
        if t.get("kind") == "metric":
            m["metric"][t["id"]] = {"zh": t["zh"], "bc": t.get("bc"), "stages": t.get("stages") or [],
                                    "processes": t.get("processes") or []}
    return m, links, mod_to_bc


def check(m, anchoring=None):
    gaps = defaultdict(list)
    if anchoring is not None:
        gaps["N07-1"] = [f"{pid} {name}（{why}）" for pid, name, why in anchoring["C"]["C1"]]
        gaps["N09-1"] = [f"{o} {name}（{why}）" for o, name, why in anchoring["C"]["C2"]]
    active_obj = {k for k, o in m["obj"].items() if o["active"]}
    primary = {k for k in active_obj if m["obj"][k]["primary"]}
    live_prc = {k: p for k, p in m["prc"].items() if p["type"] not in INACTIVE_PRC}
    obj_with_prc = {o for p in live_prc.values() for o in p["primary_object"]}
    obj_with_agg = {o for a in m["agg"].values() for o in a["objs"]}
    staged_obj = {g["obj"] for s in m["stage"].values() for g in s["gates"]} | {o for s in m["stage"].values() for o in s["in_stage"]}
    stage_metric = {s for mt in m["metric"].values() for s in mt["stages"]}

    for sid, s in m["sh"].items():
        if not any(c.get("sh") == sid for c in m["con"].values()):
            gaps["N01-1"].append(f"{sid} {s['name']}")
    for vid, v in m["vs"].items():
        if not v.get("value"):
            gaps["N03-1"].append(f"{vid} {v['name']}")
    for sid, s in m["stage"].items():
        if not s.get("caps"):
            gaps["N05-1"].append(f"{sid} {s['name']}")
    for aid, a in m["act"].items():
        if not a.get("bc"):
            gaps["N10-1"].append(aid)
    for o in sorted(primary):
        if m["obj"][o].get("class", "业务对象") == "业务对象" and m["obj"][o].get("facts", "—") in ("", "—", "-"):
            gaps["N11-1"].append(f"{o} {m['obj'][o]['name']}")
    for mid, mt in m["metric"].items():
        if not mt.get("fact") or not mt.get("bind"):
            gaps["N13-3"].append(f"{mid} {mt['zh']}")

    for sid, s in m["stage"].items():
        if not s["gates"]:
            gaps["N06-1"].append(f"{sid} {s['name']}")
        if sid not in stage_metric:
            gaps["N13-1"].append(f"{sid} {s['name']}")
    for o in sorted(primary):
        if anchoring is None and o not in obj_with_prc:
            gaps["N09-1"].append(f"{o} {m['obj'][o]['name']}")
        if m["obj"][o].get("class", "业务对象") != "业务对象":
            continue
        if o not in obj_with_agg:
            gaps["N09-2"].append(f"{o} {m['obj'][o]['name']}")
    for aid, a in m["agg"].items():
        if not a["bc"]:
            gaps["N09-M1"].append(f"{aid} {a['name']}")
        if not a["invs"] and not any(aid in i["aggs"] for i in m["inv"].values()):
            gaps["N09-M2"].append(f"{aid} {a['name']}")
        if not a["tables"]:
            gaps["N09-M3"].append(f"{aid} {a['name']}")
        for o in a["objs"]:
            if o in active_obj and o not in staged_obj:
                gaps["N09-3"].append(f"{aid} → {o} {m['obj'][o]['name']}")
    for bid, b in m["bc"].items():
        if not b.get("subdomain"):
            gaps["N10-M1"].append(f"{bid} {b['name']}（模块 {b['module']}）")
    for pid, p in live_prc.items():
        bad = [s for s in p["stages"] if s not in m["stage"]]
        if anchoring is None and (not p["stages"] or bad):
            gaps["N07-1"].append(f"{pid} {p['name']}" + (f"（阶段不存在：{'、'.join(bad)}）" if bad else "（未挂阶段）"))
        dangling = [o for o in p["primary_object"] if o not in active_obj]
        if dangling:
            gaps["N07-2"].append(f"{pid} {p['name']}（主要对象 {'、'.join(dangling)} 不是有效对象）")
    for iid, i in m["inv"].items():
        if not i["aggs"] or any(a not in m["agg"] for a in i["aggs"]):
            gaps["N08-1"].append(f"{iid}（聚合 {'、'.join(i['aggs']) or '无'}）")
    for t, r in m["table"].items():
        if not r.get("obj") or r["obj"] not in active_obj:
            gaps["N09-M4"].append(f"{t}（对象 {r.get('obj') or '无'}）")
    for mid, mt in m["metric"].items():
        if not mt["stages"] and not mt["processes"]:
            gaps["N13-2"].append(f"{mid} {mt['zh']}")
    return gaps


def render(m, links, gaps):
    counts = {k: len(v) for k, v in m.items()}
    model = {"meta": {"generator": "architecture/panorama/panorama_build.py", "sources": {k: rel(v) for k, v in SRC.items() if v.exists()},
                      "counts": counts, "checks": CHECKS},
             "nodes": m, "links": links}
    lines = ["<!-- 由 architecture/panorama/panorama_build.py 生成，勿手改 -->", "", "# 全景追溯缺口", "",
             "每轮生成一次，反复跑到收敛。缺口是「缺上层」还是「本身多余」，由 one-architecture 逐条判定，写进层笔记的待决问题。", "",
             "| 节点 | 层 | 条目数 |", "|---|---|---|", *[f"| {LAYER_NODE[k]} {NODES.get(LAYER_NODE[k], '')} | {k} | {v} |" for k, v in counts.items()], "",
             "对象按分类：" + "、".join(f"{c} {n}" for c, n in sorted(Counter(o.get("class", "业务对象") for o in m["obj"].values()).items())) + "。", "",
             "| 检查 | 含义 | 缺口 |", "|---|---|---|", *[f"| {k} | {d} | {UNAUTOMATED[k] + '，未自动化' if k in UNAUTOMATED else len(gaps.get(k, []))} |" for k, d in CHECKS.items()], ""]
    for k, d in CHECKS.items():
        if gaps.get(k):
            lines += [f"## {k} {d}（{len(gaps[k])}）", "", *[f"- {g}" for g in gaps[k]], ""]
    return yaml.safe_dump(model, allow_unicode=True, sort_keys=False, width=200), "\n".join(lines)


def self_test():
    assert cells("| AGG-01 | x |") == ["AGG-01", "x"]
    assert expand("AGG", "AGG-01、02、33；REF-01") == ["AGG-01", "AGG-02", "AGG-33"]
    assert expand("AGG", "AGG-08–12、18、102") == ["AGG-08", "AGG-09", "AGG-10", "AGG-11", "AGG-12", "AGG-18", "AGG-102"]
    m = {"sh": {}, "con": {}, "act": {}, "vs": {}, "stage": {"VS-01.1": {"name": "s", "vs": "VS-01", "gates": [], "in_stage": []}},
         "obj": {"OBJ-01": {"name": "o", "active": True, "primary": True}}, "prc": {}, "agg": {}, "inv": {},
         "bc": {}, "sd": {}, "table": {}, "metric": {}}
    g = check(m)
    assert g["N06-1"] and g["N09-1"] and g["N09-2"] and g["N13-1"]
    print("self-test ok")


def main():
    if "--self-test" in sys.argv:
        self_test()
        return 0
    m, links, _ = load()
    gaps = check(m, anc.compute())
    model, report = render(m, links, gaps)
    OUT_MODEL.write_text(model, encoding="utf-8")
    OUT_GAPS.write_text(report, encoding="utf-8")
    print("panorama: " + "、".join(f"{k} {len(v)}" for k, v in m.items()) + "；缺口 " + "、".join(f"{k} {len(gaps.get(k, []))}" for k in CHECKS))
    return 0


if __name__ == "__main__":
    sys.exit(main())
