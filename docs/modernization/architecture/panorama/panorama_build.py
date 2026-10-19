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
    "decr": HERE / "tradeoffs.yaml",
}
IN_SYSTEM = ("用户角色", "往来单位加角色", "专门对象", "只作属性或参与者", "不进系统")
OUT_MODEL = HERE / "panorama.yaml"
OUT_GAPS = HERE / "GAPS.md"
OUT_SH = ARCH / "proposals" / "STAKEHOLDERS.md"
SH_BEGIN, SH_END = "<!-- 干系人图：由 panorama_build.py 从 value-streams.v2.yaml 生成，勿手改 -->", "<!-- 干系人图结束 -->"
OBJ_RE = re.compile(r"OBJ-\d{2,3}")
AGG_RE = re.compile(r"AGG-\d{2,3}")
INV_RE = re.compile(r"INV-\d{2,3}")
INACTIVE_PRC = {"retired", "merged"}

UNAUTOMATED = {}
SKELETON = ARCH / "proposals" / "closeout"
LAYER_NODE = {"sh": "01", "shcat": "01", "con": "02", "vp": "03", "decr": "04", "cap": "05", "vs": "06", "stage": "06", "prc": "07", "inv": "08",
              "obj": "09", "act": "10", "fact": "11", "dim": "12", "metric": "13", "bind": "14", "dec": "15",
              "steps": "07", "agg": "09/10 映射", "bc": "09/10 映射", "sd": "09/10 映射", "table": "09/10 映射"}
NODES = {"01": "干系人", "02": "关注点", "03": "价值主张", "04": "取舍决定", "05": "业务能力", "06": "价值流与阶段", "07": "业务流程", "08": "约束与认定规则", "09": "业务对象", "10": "受控业务执行", "11": "业务事实", "12": "维度", "13": "指标与评价", "14": "计算绑定", "15": "决策与行动"}
CHECKS = {
    "N01-1": "每个干系人都登记了关注点（02 CON 未登记时全部计为缺口）",
    "N01-3": "每个干系人类别对应一个一级业务对象，类别角色取值在信息图可查；没有对象的登记 04 并写理由",
    "N01-4": "每个具体干系人登记「在系统中的体现」（五选一），并是类别对象（或其从属对象）的一个类型、角色或属性取值；不进系统的登记 04",
    "N01-2": "每个干系人至少触发、接收或参与一条价值流（价值流 triggering_stakeholders、value_receivers，阶段 participants）",
    "N03-1": "每条价值流都有价值主张（暂以价值流的 value 字段作 03 的登记）",
    "N05-1": "每个阶段都有业务能力支撑（阶段 capabilities 字段）",
    "N06-1": "每个阶段都有对象状态作为门（进入或退出条件引用对象状态）",
    "N06-2": "每条价值流的门对象中，至少一个顺依附链（信息图「父对象」列）追到一级业务对象",
    "N06-3": "每个阶段的门对象中，至少一个顺依附链追到一级业务对象（无门对象的阶段也计缺口）",
    "N06-4": "每条价值流都有触发者和价值接收者",
    "N07-1": "每个有效流程都能归集到价值流：所标阶段或支撑本段，且有作用对象（同 anchoring_check C1）",
    "N07-2": "流程主要对象引用的编号存在且是有效对象（主要对象为空不算缺口：作用对象由 N07-1 / anchoring C1 判）",
    "N07-P1": "P-1：流程骨架中作用于聚合的步骤都声明 Command，且该命令在同一聚合下登记于各组 commands.yaml",
    "N08-1": "每个不变量都归属存在的聚合",
    "N09-1": "每个有效对象都有流程承载（同 anchoring_check C2；OBJD 定论的从属、参照、视图按定论排除）",
    "N09-2": "每个有效一级业务对象都有聚合（聚合「对应」列；从属、参照、视图不要求）",
    "N09-3": "每个聚合对应的对象都出现在某个阶段的门或段内状态中",
    "N09-4": "依附链必填：非一级的有效对象「父对象」列填有效 OBJ 编号，一级对象填「—」",
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


def gate_refs(node, patterns, depth=0):
    """门条件里的全部叶子引用：展开价值流级 stage_pattern（entry_common / exit_common）与嵌套 any / all。"""
    if not isinstance(node, dict) or depth > 10:
        return []
    out = [node] if "ref" in node else []
    if node.get("pattern"):
        if node["pattern"] not in patterns:
            raise ValueError(f"门条件引用了未定义的 pattern：{node['pattern']}")
        out += gate_refs(patterns[node["pattern"]], patterns, depth + 1)
    for k in ("any", "all"):
        for x in node.get(k) or []:
            if not isinstance(x, dict):
                raise ValueError(f"门条件 {k} 里有非条件项：{x!r}")
            out += gate_refs(x, patterns, depth + 1)
    return out


def obj_texts(text):
    """每个对象可查取值的文字：总表该行，加上 §2 中提到它（编号或名称）的小节。"""
    rows = {oid: (" ".join(c), c[1].replace("*", "")) for oid, c, _ in md_rows(text, "## 1. 总表", "### 1.1", r"OBJ-\d{2,3}")}
    s2 = text[text.index("## 2."):text.index("## 3.")] if "## 2." in text and "## 3." in text else ""
    secs = re.split(r"\n(?=### )", s2)
    out = {}
    for oid, (row, name) in rows.items():
        out[oid] = row + "\n" + "\n".join(s for s in secs if re.search(rf"{oid}(?!\d)", s) or (len(name) >= 3 and name in s))
    return out


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
        m["sh"][s["id"]] = {"name": s.get("members") if s.get("category") == "—" else (s.get("sub") or s.get("category")), "category": s.get("category"), "type": s.get("type"),
                            "in_system": s.get("in_system"), "obj": s.get("obj"), "attr": s.get("attr"), "value": s.get("value"),
                            "decr": s.get("decr"), "src": f"{rel(SRC['vs'])}#{s['id']}"}
    m["shcat"] = {c["category"]: {**c, "src": f"{rel(SRC['vs'])}#stakeholder_categories"} for c in vsd.get("stakeholder_categories") or []}
    for d in (yaml.safe_load(SRC["decr"].read_text(encoding="utf-8")) or {}).get("decisions") or []:
        m["decr"][d["id"]] = {**d, "src": f"{rel(SRC['decr'])}#{d['id']}"}
    for v in vsd["value_streams"]:
        m["vs"][v["id"]] = {"name": v["name"], "category": v.get("category"), "value": v.get("value"),
                            "triggers": v.get("triggering_stakeholders") or [], "receivers": v.get("value_receivers") or []}
        for kind, key in (("sh-trigger", "triggering_stakeholders"), ("sh-receive", "value_receivers")):
            for sh in v.get(key) or []:
                link(kind, sh, v["id"], f"{rel(SRC['vs'])}#{v['id']}.{key}")
        if v.get("value"):
            m["vp"][f"VP-{v['id'][3:]}"] = {"name": v["value"], "vs": v["id"], "provisional": "取自价值流 value 字段，待 03 正式登记"}
            link("vp-vs", f"VP-{v['id'][3:]}", v["id"], f"{rel(SRC['vs'])}#{v['id']}.value")
        for st in v.get("stages") or []:
            gates = []
            for side in ("entry", "exit"):
                for g in gate_refs(st.get(side), v.get("stage_pattern") or {}):
                    if OBJ_RE.fullmatch(str(g.get("ref", ""))):
                        gates.append({"side": side, "obj": g["ref"], "state": g.get("state")})
            inside = [g["ref"] for g in st.get("in_stage_states") or [] if OBJ_RE.fullmatch(str(g.get("ref", "")))]
            caps = st.get("capabilities") or []
            m["stage"][st["id"]] = {"name": st["name"], "vs": v["id"], "gates": gates, "in_stage": inside, "caps": caps,
                                    "participants": st.get("participants") or []}
            for sh in st.get("participants") or []:
                link("sh-participate", sh, st["id"], f"{rel(SRC['vs'])}#{st['id']}.participants")
            for c in caps:
                m["cap"].setdefault(c, {"stages": []})["stages"].append(st["id"])
                link("cap-stage", c, st["id"], f"{rel(SRC['vs'])}#{st['id']}.capabilities")
            link("vs-stage", v["id"], st["id"], f"{rel(SRC['vs'])}#{st['id']}")
            for g in gates:
                link(f"stage-gate-{g['side']}", st["id"], g["obj"], f"{rel(SRC['vs'])}#{st['id']}.{g['side']}")

    text = SRC["obj"].read_text(encoding="utf-8")
    m["obj_text"] = obj_texts(text)
    valid = anc.info_objects(text)
    disp = olc.object_dispositions(text)
    kinds = {"subordinate": "从属对象", "reference_data": "参照数据", "view": "视图"}
    for oid, c, no in md_rows(text, "## 1. 总表", "### 1.1", r"OBJ-\d{2,3}"):
        if oid not in valid:
            continue
        level = (c[3] if len(c) > 3 else "").replace("*", "")
        d = disp.get(oid)
        parents = c[12] if len(c) > 12 else ""
        m["obj"][oid] = {"name": c[1].replace("*", ""), "active": True, "primary": level.startswith("一级") or "暂列一级" in level,
                         "class": kinds[d["kind"]] if d else "业务对象", "parent": d.get("parent_object") if d else None,
                         "parents": OBJ_RE.findall(parents), "parents_raw": parents,
                         "states": c[5] if len(c) > 5 else "", "facts": c[8].strip() if len(c) > 8 else "",
                         "src": f"{rel(SRC['obj'])}:{no}"}
        for p in m["obj"][oid]["parents"]:
            link("obj-parent", oid, p, f"{rel(SRC['obj'])}:{no}")

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

    for f in sorted(SKELETON.glob("*/commands.yaml")):
        for c in (yaml.safe_load(f.read_text(encoding="utf-8")) or {}).get("commands") or []:
            agg = c.get("aggregate")
            m["act"][f"{agg}/{c['name']}"] = {"command": c["name"], "aggregate": agg, "bc": None,
                                              "state_change": c.get("state_change"), "src": f"{rel(f)}#{c['name']}"}
    m["steps"] = []
    for f in sorted(SKELETON.glob("*/processes*.yaml")) + sorted(SKELETON.glob("*/parts/*processes*.yaml")):
        d = yaml.safe_load(f.read_text(encoding="utf-8")) or {}
        for p in (d.get("processes") if isinstance(d, dict) else d) or []:
            for i, s in enumerate(p.get("steps") or [], 1):
                if s.get("aggregate"):
                    m["steps"].append({"prc": p["id"], "step": i, "aggregate": str(s["aggregate"]), "command": s.get("command"),
                                       "src": f"{rel(f)}#{p['id']}.steps[{i}]"})
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

    top = {k for k in primary if m["obj"][k].get("class", "业务对象") == "业务对象"}

    def chain(o, seen=()):
        """顺依附链上行，返回追到的一级业务对象（可多个，「或」关系任一可达即可）。"""
        if o in top:
            return {o}
        if o in seen or o not in m["obj"]:
            return set()
        return set().union(*[chain(p, seen + (o,)) for p in m["obj"][o].get("parents", [])] or [set()])

    def in_cycle(o):
        """沿父对象上行能回到自身则返回环路，否则 None。"""
        stack = [(o, (o,))]
        while stack:
            cur, path = stack.pop()
            for p in m["obj"].get(cur, {}).get("parents", []):
                if p == o:
                    return path + (o,)
                if p not in path:
                    stack.append((p, path + (p,)))
        return None

    for o in sorted(active_obj, key=lambda x: int(x[4:])):
        ob = m["obj"][o]
        bad = [p for p in ob.get("parents", []) if p not in active_obj]
        cyc = in_cycle(o)
        if o in primary and ob.get("parents"):
            gaps["N09-4"].append(f"{o} {ob['name']}（一级对象不应有父对象：{'、'.join(ob['parents'])}）")
        elif o not in primary and not ob.get("parents"):
            gaps["N09-4"].append(f"{o} {ob['name']}（非一级，父对象未填 OBJ 编号：{ob.get('parents_raw') or '空'}）")
        elif bad:
            gaps["N09-4"].append(f"{o} {ob['name']}（父对象 {'、'.join(bad)} 不是有效对象）")
        elif cyc:
            gaps["N09-4"].append(f"{o} {ob['name']}（依附链成环：{' → '.join(cyc)}）")

    def gate_note(objs):
        return "、".join(f"{o}→{'、'.join(sorted(chain(o))) or '断'}" for o in objs)

    for vid, v in m["vs"].items():
        objs = sorted({g["obj"] for s in m["stage"].values() if s["vs"] == vid for g in s["gates"]})
        if not any(chain(o) for o in objs):
            gaps["N06-2"].append(f"{vid} {v['name']}（门对象 {gate_note(objs) or '无'}）")
        if not v.get("triggers") or not v.get("receivers"):
            gaps["N06-4"].append(f"{vid} {v['name']}（" + "、".join(x for x, ok in (("缺触发者", v.get("triggers")), ("缺价值接收者", v.get("receivers"))) if not ok) + "）")
    for sid, s in m["stage"].items():
        objs = sorted({g["obj"] for g in s["gates"]})
        if not any(chain(o) for o in objs):
            gaps["N06-3"].append(f"{sid} {s['name']}（门对象 {gate_note(objs) or '无'}）")
    involved = {x for v in m["vs"].values() for x in v.get("triggers", []) + v.get("receivers", [])} | {x for s in m["stage"].values() for x in s.get("participants", [])}

    obj_text = m.get("obj_text", {})
    decr_subj = {k: str(d.get("subject", "")) for k, d in m.get("decr", {}).items()}

    def under(o, root):
        """o 是 root 本身或顺依附链在 root 之下。"""
        if o == root:
            return True
        stack, seen = list(m["obj"].get(o, {}).get("parents", [])), set()
        while stack:
            p = stack.pop()
            if p == root:
                return True
            if p not in seen:
                seen.add(p)
                stack += m["obj"].get(p, {}).get("parents", [])
        return False

    def found(o, values):
        txt = obj_text.get(o, "")
        return [v for v in re.split(r"[、，,]", str(values or "")) if v.strip() and v.strip() not in txt]

    for cname, c in m.get("shcat", {}).items():
        o = c.get("obj")
        if not o:
            if not c.get("decr") or c["decr"] not in decr_subj:
                gaps["N01-3"].append(f"{cname}（无对应对象，且未登记 04）")
            continue
        if o not in top:
            gaps["N01-3"].append(f"{cname}（对应 {o} 不是一级业务对象）")
            continue
        holders = [x for x in m["obj"] if under(x, o)]
        miss = [v for v in re.split(r"[、，,]", str(c.get("role") or "")) if v and not any(v in obj_text.get(x, "") for x in holders)]
        if miss:
            gaps["N01-3"].append(f"{cname}（{o} 及其从属对象没有{c.get('role_attr') or '角色'}取值：{'、'.join(miss)}）")
    for sid, s in m["sh"].items():
        if not s.get("in_system"):
            continue
        how, o, cat = s["in_system"], s.get("obj"), m.get("shcat", {}).get(s.get("category"))
        if how not in IN_SYSTEM:
            gaps["N01-4"].append(f"{sid} {s['name']}（体现「{how}」不在五选一内）")
        elif how == "不进系统":
            if not s.get("decr") or sid not in decr_subj.get(s["decr"], ""):
                gaps["N01-4"].append(f"{sid} {s['name']}（不进系统，但 04 未登记）")
        elif not o or o not in active_obj:
            gaps["N01-4"].append(f"{sid} {s['name']}（承载对象 {o or '空'} 不是有效对象）")
        elif cat and cat.get("obj") and not under(o, cat["obj"]):
            gaps["N01-4"].append(f"{sid} {s['name']}（{o} 不在类别「{s['category']}」的对象 {cat['obj']} 之下）")
        elif how != "用户角色" and found(o, s.get("value")):
            gaps["N01-4"].append(f"{sid} {s['name']}（{o}「{s.get('attr')}」没有取值：{'、'.join(found(o, s.get('value')))}）")
    for sid, s in m["sh"].items():
        if s.get("category") not in (None, "—") and s.get("in_system") and s["category"] not in m.get("shcat", {}):
            gaps["N01-3"].append(f"{sid} {s['name']}（类别「{s['category']}」未在 stakeholder_categories 登记）")
        if not s.get("in_system"):
            gaps["N01-4"].append(f"{sid} {s['name']}（未登记在系统中的体现）")

    for sid, s in m["sh"].items():
        if sid not in involved:
            gaps["N01-2"].append(f"{sid} {s['name']}")
        if not any(c.get("sh") == sid for c in m["con"].values()):
            gaps["N01-1"].append(f"{sid} {s['name']}")
    for vid, v in m["vs"].items():
        if not v.get("value"):
            gaps["N03-1"].append(f"{vid} {v['name']}")
    for sid, s in m["stage"].items():
        if not s.get("caps"):
            gaps["N05-1"].append(f"{sid} {s['name']}")
    for aid, a in m["act"].items():
        bc = a.get("bc") or (m["agg"].get(a.get("aggregate") or "", {}) or {}).get("bc")
        if not bc:
            gaps["N10-1"].append(f"{aid}（聚合 {a.get('aggregate')} 未归 BC）")
    strip = lambda c: re.sub(r"^〔缺〕", "", str(c or "")).strip()
    registered = {(a.get("aggregate"), strip(a["command"])) for a in m["act"].values()}
    seen = set()
    for s in m.get("steps", []):
        if (s["prc"], s["step"]) in seen:
            continue
        seen.add((s["prc"], s["step"]))
        aggs = expand("AGG", s["aggregate"]) or [s["aggregate"]]
        if not strip(s["command"]):
            gaps["N07-P1"].append(f"{s['prc']} 第 {s['step']} 步（{s['aggregate']}）未声明 Command")
        elif not any((a, strip(s["command"])) in registered for a in aggs):
            gaps["N07-P1"].append(f"{s['prc']} 第 {s['step']} 步命令「{s['command']}」未在 {s['aggregate']} 的 commands.yaml 登记")
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
    m = {k: v for k, v in m.items() if k != "obj_text"}
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


def stakeholder_table(vsd, text):
    """STAKEHOLDERS.md 的干系人图（四列 + 在系统中的体现），替换标记之间的内容。"""
    if SH_BEGIN not in text or SH_END not in text:
        raise ValueError(f"{rel(OUT_SH)} 缺生成标记")
    cats = vsd.get("stakeholder_categories") or []
    rows = ["### 类别与一级业务对象", "", "| 类型 | 类别 | 对应一级业务对象 | 说明 |", "|---|---|---|---|"]
    rows += [f"| {c['type']} | {c['category']} | {c.get('obj') or '无（' + c.get('decr', '') + '）'}{'，' + c['role_attr'] + '：' + c['role'] if c.get('role') else ''} | {c['desc']} |" for c in cats]
    rows += ["", "### 具体干系人", "", "| 编号 | 类型 | 类别 | 具体干系人 | 说明 | 在系统中的体现 | 承载 | 关系 |", "|---|---|---|---|---|---|---|---|"]
    for s in vsd.get("stakeholders") or []:
        who = s.get("members") if s.get("category") == "—" else f"{s.get('sub')}：{s.get('members')}" if s.get("sub") not in (None, "—") else s.get("members")
        carry = s.get("decr") if s.get("in_system") == "不进系统" else f"{s.get('obj')}「{s.get('attr')}」= {s.get('value')}"
        rows.append(f"| {s['id']} | {s.get('type')} | {s.get('category')} | {who} | {s.get('desc', '')} | {s.get('in_system', '')} | {carry} | {s.get('relation', '参')} |")
    a, b = text.index(SH_BEGIN) + len(SH_BEGIN), text.index(SH_END)
    return text[:a] + "\n\n" + "\n".join(rows) + "\n\n" + text[b:]


def self_test():
    assert cells("| AGG-01 | x |") == ["AGG-01", "x"]
    assert expand("AGG", "AGG-01、02、33；REF-01") == ["AGG-01", "AGG-02", "AGG-33"]
    pat = {"exit_common": {"all": [{"any": [{"ref": "OBJ-35"}, {"all": [{"ref": "OBJ-30"}]}]}]}}
    assert [g["ref"] for g in gate_refs({"pattern": "exit_common", "all": [{"ref": "OBJ-38"}]}, pat)] == ["OBJ-35", "OBJ-30", "OBJ-38"]
    for bad in ({"pattern": "nope"}, {"any": ["OBJ-35"]}):
        try:
            gate_refs(bad, pat)
            raise AssertionError(f"应失败：{bad}")
        except ValueError:
            pass
    assert expand("AGG", "AGG-08–12、18、102") == ["AGG-08", "AGG-09", "AGG-10", "AGG-11", "AGG-12", "AGG-18", "AGG-102"]
    m = {"sh": {}, "con": {}, "act": {}, "vs": {}, "stage": {"VS-01.1": {"name": "s", "vs": "VS-01", "gates": [], "in_stage": []}},
         "obj": {"OBJ-01": {"name": "o", "active": True, "primary": True}}, "prc": {}, "agg": {}, "inv": {},
         "bc": {}, "sd": {}, "table": {}, "metric": {}}
    g = check(m)
    assert g["N06-1"] and g["N09-1"] and g["N09-2"] and g["N13-1"]
    assert g["N06-3"] and not g["N09-4"]
    m["sh"] = {"SH-A": {"name": "甲"}, "SH-B": {"name": "乙"}}
    m["vs"] = {"VS-01": {"name": "v", "triggers": ["SH-A"], "receivers": []}}
    m["stage"]["VS-01.1"]["gates"] = [{"side": "entry", "obj": "OBJ-03"}]
    m["obj"].update({"OBJ-02": {"name": "c", "active": True, "primary": False, "parents": ["OBJ-01"]},
                     "OBJ-03": {"name": "d", "active": True, "primary": False, "parents": ["OBJ-02"]},
                     "OBJ-04": {"name": "e", "active": True, "primary": False, "parents": []},
                     "OBJ-05": {"name": "f", "active": True, "primary": False, "parents": ["OBJ-09"]}})
    g = check(m)
    assert not g["N06-2"] and not g["N06-3"], (g["N06-2"], g["N06-3"])   # 两跳依附链可达一级
    assert [x[:6] for x in g["N09-4"]] == ["OBJ-04", "OBJ-05"], g["N09-4"]
    m["obj"]["OBJ-01"]["parents"] = ["OBJ-02"]
    m["obj"]["OBJ-06"] = {"name": "g", "active": True, "primary": False, "parents": ["OBJ-07"]}
    m["obj"]["OBJ-07"] = {"name": "h", "active": True, "primary": False, "parents": ["OBJ-06"]}
    g = check(m)
    assert [x[:6] for x in g["N09-4"]] == ["OBJ-01", "OBJ-02", "OBJ-04", "OBJ-05", "OBJ-06", "OBJ-07"], g["N09-4"]   # 一级填了父对象（且与 OBJ-02 成环）；OBJ-06、07 互指成环
    assert "一级对象不应有父对象" in g["N09-4"][0] and all("成环" in g["N09-4"][i] for i in (1, 4, 5))
    m["obj"]["OBJ-01"]["parents"] = []
    del m["obj"]["OBJ-06"], m["obj"]["OBJ-07"]
    assert g["N01-2"] == ["SH-B 乙"] and g["N06-4"] == ["VS-01 v（缺价值接收者）"], (g["N01-2"], g["N06-4"])
    m["stage"]["VS-01.1"]["gates"] = [{"side": "entry", "obj": "OBJ-04"}]
    g = check(m)
    assert g["N06-2"] and g["N06-3"]   # 断链：门对象追不到一级
    m["obj_text"] = {"OBJ-01": "往来单位", "OBJ-02": "角色：客户；供应商", "OBJ-03": "决策链角色：发起人"}
    m["decr"] = {"DECR-01": {"subject": "SH-D 公众"}}
    m["shcat"] = {"客户": {"obj": "OBJ-01", "role": "客户"}, "监管": {"obj": "OBJ-01", "role": "主管机关"}, "公众": {"obj": None, "decr": "DECR-01"},
                  "伙伴": {"obj": "OBJ-02"}}
    m["sh"] = {"SH-A": {"name": "甲", "category": "客户", "in_system": "只作属性或参与者", "obj": "OBJ-03", "value": "发起人"},
               "SH-B": {"name": "乙", "category": "客户", "in_system": "往来单位加角色", "obj": "OBJ-02", "attr": "客户类型", "value": "开发商"},
               "SH-C": {"name": "丙", "category": "客户", "in_system": "飞鸽", "obj": "OBJ-02"},
               "SH-D": {"name": "丁", "category": "公众", "in_system": "不进系统", "decr": "DECR-01"},
               "SH-E": {"name": "戊", "category": "公众", "in_system": "不进系统"},
               "SH-F": {"name": "己", "category": "客户", "in_system": "往来单位加角色", "obj": "OBJ-05", "value": "x"},
               "SH-G": {"name": "庚", "category": "客户"}}
    g = check(m)
    assert g["N01-3"] == ["监管（OBJ-01 及其从属对象没有角色取值：主管机关）", "伙伴（对应 OBJ-02 不是一级业务对象）"], g["N01-3"]
    assert [x[:4] for x in g["N01-4"]] == ["SH-B", "SH-C", "SH-E", "SH-F", "SH-G"], g["N01-4"]   # 缺取值、体现不在五选一、未登记 04、对象不在类别之下、未登记体现
    print("self-test ok")


def main():
    if "--self-test" in sys.argv:
        self_test()
        return 0
    m, links, _ = load()
    vsd = yaml.safe_load(SRC["vs"].read_text(encoding="utf-8"))
    OUT_SH.write_text(stakeholder_table(vsd, OUT_SH.read_text(encoding="utf-8")), encoding="utf-8")
    gaps = check(m, anc.compute())
    model, report = render(m, links, gaps)
    OUT_MODEL.write_text(model, encoding="utf-8")
    OUT_GAPS.write_text(report, encoding="utf-8")
    print("panorama: " + "、".join(f"{k} {len(v)}" for k, v in m.items()) + "；缺口 " + "、".join(f"{k} {len(gaps.get(k, []))}" for k in CHECKS))
    return 0


if __name__ == "__main__":
    sys.exit(main())
