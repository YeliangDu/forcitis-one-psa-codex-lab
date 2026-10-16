"""对象层与能力层对齐（只读；BIZBOK §3.4 第二、三层）：流程主对象、阶段承载对象、流程推出的能力与阶段能力清单。

用法: python3 -I object_layer_check.py [--local] [--no-write] [--self-test]
  输入  process-catalog.yaml（primary_object 为三组确认值，经 sync_catalog_stages 从骨架同步）；
        process-objects.yaml（object_check.py --objects-yaml 生成，先跑它）；value-streams.v2.yaml（各段 exit、capabilities）；
        capabilities.v2.yaml（对象 → 一级 / 二级能力）；stage_align_check.analyze 的归段命中（hit_refs）。
  输出  object-layer.yaml（生成，供对齐图读）与 OBJECT_LAYER_REPORT.md；--no-write 只打印汇总。

主对象提议（目录未填 primary_object 时）：只取本流程写入（C/U/D）的对象，按档取第一档非空者——
  1 所标段退出条件精确命中；2 所标段段内状态精确命中；3 退出或段内近似命中；4 所标段退出条件引用到的对象；5 唯一写入对象。
  事件策略的触发命中是触发方对象，不作主对象依据。一档内多于一个 = 多候选，交组选。读模型不改对象状态，不提议、不计入 ①（组可手填其主读对象）。
primary_object: none 为组声明的「无业务对象」（须附理由），不计入 ①③④；secondary_objects 为次要对象（须是本流程写入的对象）。
检查：① 非支撑流程无主对象；② 阶段承载对象（退出条件引用的对象）在该段无流程以它为主对象或次要对象；
      ③ 同一主对象被多条流程使用（列出，判断重复或分工）；④ 流程主对象推出的一级能力不在所标段的能力清单。
"""
import collections
import re
import sys
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import stage_align_check as sac  # noqa: E402

PROPOSALS = HERE.parent
CAPS = PROPOSALS / "capabilities.v2.yaml"
POBJ = HERE / "process-objects.yaml"
OUT_YAML = HERE / "object-layer.yaml"
REPORT = HERE / "OBJECT_LAYER_REPORT.md"
WRITE_OPS = set("CUD")
LEVELS = {1: "所标段退出条件精确命中", 2: "所标段段内状态精确命中", 3: "退出或段内近似命中", 4: "所标段退出条件引用", 5: "唯一写入对象"}


def num(x):
    return (str(x)[:3], int(re.sub(r"\D", "", str(x)) or 0))


def cap_index(caps_doc):
    out = {}
    for c in caps_doc.get("capabilities") or []:
        if c.get("object"):
            out[c["object"]] = (c["id"], c["id"], c["name"])
        for c2 in c.get("l2") or []:
            if c2.get("object"):
                out[c2["object"]] = (c["id"], c2["id"], c2["name"])
    return out


def stage_objects(vs_doc):
    out, caps, sname, order, disp = {}, {}, {}, [], {}
    for vs in vs_doc["value_streams"]:
        pa = vs.get("stage_pattern") or {}
        for i, s in enumerate(sorted(vs["stages"], key=lambda x: x.get("order", 0)), 1):
            disp[s["id"]] = (vs["id"][3:], s.get("order", i))
            refs = [lf.get("ref") for lf in sac.leaves(sac.expand(s.get("exit") or {"all": []}, pa))
                    if str(lf.get("ref", "")).startswith("OBJ-")]
            out[s["id"]] = list(dict.fromkeys(refs))
            caps[s["id"]] = list(s.get("capabilities") or [])
            sname[s["id"]] = s["name"]
            order.append(s["id"])
    return out, caps, sname, order, disp


def display_numbers(rows, catalog, disp):
    """展示编号（只供阅读，PROCESS_METHOD_DRAFT §11）：主展示段 = 所标段中展示序最小者；段内按目录 order、再按永久编号。"""
    cat = {p["id"]: p for p in catalog}
    by_stage = {}
    for r in rows:
        st = [s for s in r["stages"] if s in disp]
        if st:
            home = min(st, key=lambda s: (disp[s][0], disp[s][1]))
            by_stage.setdefault(home, []).append(r)
    for s, rs in by_stage.items():
        rs.sort(key=lambda r: (cat[r["id"]].get("order", 10 ** 6), num(r["id"])))
        for i, r in enumerate(rs, 1):
            r["display"] = f"{disp[s][0]}.{disp[s][1]}.{i:02d}"


def written_objs(pobj_doc, agg_objs):
    out = collections.defaultdict(set)
    for p in pobj_doc.get("processes") or []:
        for o in p.get("objects") or []:
            if WRITE_OPS & set(o.get("ops", "")):
                objs = {x for a in o.get("aggregates") or [] for x in agg_objs.get(a, [])} or {o["obj"]}
                out[p["id"]] |= {x for x in objs if str(x).startswith("OBJ-")} or {o["obj"]}
    return out


def propose(p, written, hits, st_objs):
    stages = p.get("stages") or []
    w = written.get(p["id"], set())
    hs = [h for h in hits.get(p["id"], []) if h[0] in stages and h[3] != "trigger"]
    tiers = [
        {r for s, t, r, src in hs if t == "exact" and src == "exit"},
        {r for s, t, r, src in hs if t == "exact" and src == "inner"},
        {r for s, t, r, src in hs if t != "exact"},
        {r for s in stages for r in st_objs.get(s, [])},
        set(w) if len(w) == 1 else set(),
    ]
    for lv, cand in enumerate(tiers, 1):
        c = sorted(cand & w, key=num)
        if c:
            return lv, c
    return 0, []


def analyse(catalog, pobj_doc, agg_objs, vs_doc, caps_doc, hits):
    st_objs, st_caps, sname, order, disp = stage_objects(vs_doc)
    cidx = cap_index(caps_doc)
    written = written_objs(pobj_doc, agg_objs)
    rows = []
    for p in catalog:
        if p.get("type") == "merged" or p.get("support"):
            continue
        conf = p.get("primary_object")
        lv, cand = propose(p, written, hits, st_objs)
        if isinstance(conf, str) and conf.strip().lower() == "none":
            status, prim = "declared_none", None
        elif conf:
            status, prim = "confirmed", conf
        elif p.get("type") == "read_model":
            status, prim = "read_model", None
        elif len(cand) == 1:
            status, prim = "proposed", cand[0]
        else:
            status, prim = ("candidates" if cand else "none"), None
        cap = cidx.get(prim) if prim else None
        sec = [o for o in p.get("secondary_objects") or []]
        rows.append({"id": p["id"], "name": p["name"], "group": p.get("group"), "type": p.get("type"), "stages": list(p.get("stages") or []),
                     "secondary_objects": sec, "secondary_unwritten": [o for o in sec if o not in written.get(p["id"], set())],
                     "primary_object": prim, "status": status, "level": lv, "candidates": cand,
                     "writes": sorted(written.get(p["id"], set()), key=num),
                     "capability_l1": cap[0] if cap else None, "capability": cap[1] if cap else None})
    display_numbers(rows, catalog, disp)
    by_obj = collections.defaultdict(list)
    for r in rows:
        if r["primary_object"]:
            by_obj[r["primary_object"]].append(r)
    D = {"no_primary": [r for r in rows if not r["primary_object"] and r["status"] not in ("read_model", "declared_none")], "uncarried": [], "shared": [], "cap_off": [], "no_cap": []}
    by_sec = collections.defaultdict(list)
    for r in rows:
        for o in r["secondary_objects"]:
            if o not in r["secondary_unwritten"]:
                by_sec[o].append(r)
    D["sec_bad"] = [r for r in rows if r["secondary_unwritten"]]
    for s in order:
        for o in st_objs[s]:
            if not any(s in r["stages"] for r in by_obj.get(o, []) + by_sec.get(o, [])):
                D["uncarried"].append((s, o))
    D["shared"] = sorted(((o, rs) for o, rs in by_obj.items() if len(rs) > 1), key=lambda x: num(x[0]))
    for r in rows:
        if not r["primary_object"]:
            continue
        if not r["capability_l1"]:
            D["no_cap"].append(r)
        elif r["stages"] and not any(r["capability_l1"] in st_caps.get(s, []) for s in r["stages"]):
            D["cap_off"].append(r)
    return {"rows": rows, "D": D, "st_objs": st_objs, "st_caps": st_caps, "sname": sname, "order": order, "disp": disp}


def render_yaml(A):
    doc = {"note": "object_layer_check.py 生成，勿手改；status：confirmed 三组确认（目录 primary_object）/ proposed 脚本提议 / candidates 多候选 / none 无候选 / read_model 读模型不提议",
           "stages": [{"id": s, "display": "%s.%s" % A["disp"][s], "order": A["disp"][s][1], "name": A["sname"][s], "objects": A["st_objs"][s],
                       "capabilities": A["st_caps"][s]} for s in A["order"]],
           "processes": [{k: r.get(k) for k in ("id", "display", "name", "group", "stages", "primary_object", "secondary_objects", "status", "candidates", "capability_l1", "capability")
                          if r.get(k) not in (None, [])} for r in A["rows"]]}
    return yaml.safe_dump(doc, allow_unicode=True, sort_keys=False, width=200)


def render_md(A, onames):
    rows, D = A["rows"], A["D"]
    on = lambda o: f"{o} {onames.get(o, '')}".strip()
    cnt = collections.Counter(r["status"] for r in rows)
    out = ["# 对象层与能力层对齐报告（object_layer_check.py 生成，勿手改）", "",
           f"非支撑流程 {len(rows)} 条：已确认 {cnt['confirmed']}、声明无业务对象 {cnt['declared_none']}、提议 {cnt['proposed']}、多候选 {cnt['candidates']}、无候选 {cnt['none']}、读模型 {cnt['read_model']}。",
           f"检查：① 无主对象 {len(D['no_primary'])}；② 阶段承载对象无主流程 {len(D['uncarried'])}；③ 主对象多流程 {len(D['shared'])}；"
           f"④ 能力不在所标段清单 {len(D['cap_off'])}（另主对象无能力 {len(D['no_cap'])}）。", "",
           "提议口径：" + "；".join(f"第 {k} 档 {v}" for k, v in LEVELS.items()) + "。只取本流程写入的对象；第 4、5 档把握低，组确认时重点看。", ""]
    out += ["## 主对象（按组）", ""]
    for g in sorted({r["group"] for r in rows}, key=str):
        out += [f"### {g}", "", "| 流程 | 类型 | 所标段 | 主对象 | 状态 | 依据档 | 候选 / 写入对象 | 一级能力 |", "|---|---|---|---|---|---|---|---|"]
        for r in [x for x in rows if x["group"] == g]:
            extra = "、".join(on(o) for o in (r["candidates"] if r["status"] == "candidates" else r["writes"]))
            out.append(f"| {r['id']} {r['name']} | {r['type']} | {'、'.join(r['stages'])} | {on(r['primary_object']) if r['primary_object'] else '—'} | "
                       f"{r['status']} | {r['level'] or '—'} | {extra} | {r['capability_l1'] or '—'} |")
        out.append("")
    out += ["## ② 阶段承载对象在该段无流程以它为主对象或次要对象", ""]
    out += [f"- {s} {A['sname'][s]}：{on(o)}" for s, o in D["uncarried"]] or ["无"]
    if D["sec_bad"]:
        out += ["", "### 次要对象不是本流程写入的对象（不计承载）", ""] + [f"- {r['id']} {r['name']}：{'、'.join(on(o) for o in r['secondary_unwritten'])}" for r in D["sec_bad"]]
    out += ["", "## ③ 同一主对象被多条流程使用（判断重复或合理分工）", ""]
    out += [f"- {on(o)}：" + "；".join(f"{r['id']} {r['name']}（{'、'.join(r['stages'])}，{r['status']}）" for r in rs) for o, rs in D["shared"]] or ["无"]
    out += ["", "## ④ 主对象推出的一级能力不在所标段的能力清单", ""]
    out += [f"- {r['id']} {r['name']}：{on(r['primary_object'])} → {r['capability_l1']}；所标段 "
            + "、".join(f"{s} {A['st_caps'].get(s)}" for s in r["stages"]) for r in D["cap_off"]] or ["无"]
    out += ["", "### 主对象无对应能力（无 OBJ 的聚合，或对象未入能力图）", ""]
    out += [f"- {r['id']} {r['name']}：{on(r['primary_object'])}" for r in D["no_cap"]] or ["无"]
    out += ["", "## E 需用户判断（候选，经组与本会话筛过再交）", "",
            "- ④ 中属阶段能力清单漏映射的，改 value-streams.v2.yaml 的 capabilities 需用户确认（第 ③ 步对账为用户已审口径）；",
            "- 主对象是无 OBJ 的聚合（AGG-xx）的：该聚合要不要在信息图有对象、归哪个能力，属信息层，随信息层对齐交用户；\n"
            "- ③ 中判为重复的流程合并、② 中缺流程的承载对象补流程，涉及流程增删的交用户；其余由组与本会话定。", ""]
    return "\n".join(out) + "\n"


def self_test():
    vs = {"value_streams": [{"id": "VS-X", "stages": [
        {"id": "VS-X.1", "name": "一", "capabilities": ["CAP-001"], "exit": {"all": [{"ref": "OBJ-01", "state": "完"}]}},
        {"id": "VS-X.2", "name": "二", "capabilities": ["CAP-001"], "exit": {"all": [{"ref": "OBJ-03", "state": "完"}]}}]}]}
    caps = {"capabilities": [{"id": "CAP-001", "name": "甲", "object": "OBJ-01", "l2": [{"id": "CAP-001.002", "name": "乙", "object": "OBJ-02"}]},
                             {"id": "CAP-009", "name": "丙", "object": "OBJ-09"}]}
    cat = [{"id": "P1", "name": "a", "type": "business", "stages": ["VS-X.1"]},
           {"id": "P2", "name": "b", "type": "business", "stages": ["VS-X.1"]},
           {"id": "P3", "name": "c", "type": "event_policy", "stages": ["VS-X.1"]},
           {"id": "P4", "name": "d", "type": "business", "stages": ["VS-X.1"], "primary_object": "OBJ-09"},
           {"id": "P5", "name": "e", "type": "business", "stages": [], "support": "支撑"},
           {"id": "P6", "name": "f", "type": "business", "stages": ["VS-X.2"], "primary_object": "none", "secondary_objects": ["OBJ-03"]},
           {"id": "P7", "name": "g", "type": "business", "stages": ["VS-X.2"], "secondary_objects": ["OBJ-01"]}]
    pobj = {"processes": [{"id": "P1", "objects": [{"obj": "OBJ-01", "ops": "CU", "aggregates": ["AGG-1"]}, {"obj": "OBJ-02", "ops": "U", "aggregates": ["AGG-2"]}]},
                          {"id": "P2", "objects": [{"obj": "OBJ-02", "ops": "C", "aggregates": ["AGG-2"]}, {"obj": "OBJ-05", "ops": "U", "aggregates": ["AGG-5"]}]},
                          {"id": "P6", "objects": [{"obj": "OBJ-03", "ops": "U", "aggregates": ["AGG-3"]}]},
                          {"id": "P3", "objects": [{"obj": "OBJ-02", "ops": "U", "aggregates": ["AGG-2"]}, {"obj": "OBJ-01", "ops": "R", "aggregates": ["AGG-1"]}]}]}
    aggo = {"AGG-1": ["OBJ-01"], "AGG-2": ["OBJ-02"], "AGG-3": ["OBJ-03"], "AGG-5": ["OBJ-05"]}
    hits = {"P1": [("VS-X.1", "exact", "OBJ-01", "exit")], "P3": [("VS-X.1", "exact", "OBJ-01", "trigger")]}
    A = analyse(cat, pobj, aggo, vs, caps, hits)
    r = {x["id"]: x for x in A["rows"]}
    ok = lambda n, c: print(f"自测 {n}: {'通过' if c else '失败'}") or c
    res = [ok("退出精确命中取第 1 档", r["P1"]["primary_object"] == "OBJ-01" and r["P1"]["level"] == 1),
           ok("无命中、多写入 = 无候选", r["P2"]["status"] == "none"),
           ok("触发命中不作依据，唯一写入取第 5 档", r["P3"]["primary_object"] == "OBJ-02" and r["P3"]["level"] == 5),
           ok("目录确认值优先", r["P4"]["status"] == "confirmed" and r["P4"]["capability_l1"] == "CAP-009"),
           ok("支撑流程不计", "P5" not in r),
           ok("展示编号按段内顺序", r["P1"]["display"] == "X.1.01" and r["P4"]["display"] == "X.1.04"),
           ok("① 无主对象（none 声明不计）", [x["id"] for x in A["D"]["no_primary"]] == ["P2", "P7"] and r["P6"]["status"] == "declared_none"),
           ok("② 次要对象算承载", ("VS-X.2", "OBJ-03") not in A["D"]["uncarried"] and ("VS-X.1", "OBJ-01") not in A["D"]["uncarried"]),
           ok("次要对象须本流程写入", [x["id"] for x in A["D"]["sec_bad"]] == ["P7"]),
           ok("④ 二级能力上溯一级后核对", any(x["id"] == "P4" for x in A["D"]["cap_off"]) and not any(x["id"] == "P3" for x in A["D"]["cap_off"]))]
    if not all(res):
        sys.exit(1)
    print("自测 通过")


def main(argv):
    if "--self-test" in argv:
        return self_test()
    import closeout_check as cc
    local = "--local" in argv
    if not POBJ.exists():
        sys.exit("缺 process-objects.yaml：先跑 object_check.py --objects-yaml")
    vs_doc = yaml.safe_load(sac.VS_FILE.read_text(encoding="utf-8"))
    catalog = yaml.safe_load(sac.CATALOG.read_text(encoding="utf-8"))["processes"]
    onames = sac.load_objnames()
    machines, aggs = sac.load_machines(local), cc.load_aggregates()
    R = sac.analyze(vs_doc, catalog, sac.load_skeletons(local), onames, sac.load_alias_map(), machines, aggs,
                    sac.load_event_map(local, machines, aggs))
    A = analyse(catalog, yaml.safe_load(POBJ.read_text(encoding="utf-8")), {a: v.get("objs") or [] for a, v in aggs.items()},
                vs_doc, yaml.safe_load(CAPS.read_text(encoding="utf-8")), R["hit_refs"])
    md = render_md(A, onames)
    if "--no-write" not in argv:
        OUT_YAML.write_text(render_yaml(A), encoding="utf-8")
        REPORT.write_text(md, encoding="utf-8")
    print("\n".join(md.splitlines()[2:4]))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
