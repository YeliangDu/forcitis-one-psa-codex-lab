"""流程归集闭环检查（PROCESS_METHOD_DRAFT §12；intent 2026-10-17-174814、2026-10-16-212218）。

用法: python3 -I anchoring_check.py [--local] [--extra PATH]... [--report] | --self-test
  输入  ssot/09-processes/catalog.yaml、value-streams.v2.yaml、process-objects.yaml（object_check.py --objects-yaml 生成）、
        ENTERPRISE_INFO_MAP_DRAFT.md 的对象行；「支撑本段」与集成按供给挂接取 object_layer_check.analyse 的结果，
        object-layer.yaml 只用于核对它是否落后于目录（落后只告警，本脚本在内存重算）。
  --extra PATH 按 ID 覆盖或追加流程（同 closeout_check），附加流程的对象读写关系在内存中重算。
  --report 写 ANCHORING_REPORT.md。
作用对象另计目录 acts_on（读或经它改动、不作主次对象的业务对象）；type: retired 与 merged 不参与。
检查：C1 无法归集的流程；C2 无流程的对象；C3 对象属价值流但不在其任何阶段（补阶段候选）；
      C4 管理流程未关联被管流程的对象；C5 读模型流程未归价值流。C1 或 C2 非空退出码 1；输入缺失或无效退出码 2。
"""
import collections
import re
import sys
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import object_layer_check as olc  # noqa: E402
import stage_align_check as sac  # noqa: E402

REPORT = HERE / "ANCHORING_REPORT.md"
SKIP_TYPES = {"merged", "retired"}
TITLES = {"C1": "无法归集的流程", "C2": "无流程的对象", "C3": "对象属价值流但不在其任何阶段（补阶段候选）",
          "C4": "管理流程未关联被管流程的对象", "C5": "读模型流程未归价值流"}


class InputError(Exception):
    pass


def load_yaml(path, key):
    if not path.exists():
        raise InputError(f"缺输入文件：{path}")
    try:
        doc = yaml.safe_load(path.read_text(encoding="utf-8"))
    except yaml.YAMLError as error:
        raise InputError(f"{path} 不是有效 YAML：{error}") from error
    if not isinstance(doc, dict) or not isinstance(doc.get(key), list) or not doc[key]:
        raise InputError(f"{path} 缺非空列表 {key}")
    return doc


def info_objects(text):
    out = {}
    for line in text.splitlines():
        m = re.match(r"^\|\s*(OBJ-\d+)\s*\|\s*([^|]+?)\s*\|([^|]*)\|", line)
        if m and m.group(1) not in out:
            out[m.group(1)] = None if "已非对象" in m.group(3) else m.group(2)
    objs = {k: v for k, v in out.items() if v}
    if not objs:
        raise InputError("信息图未解析出任何对象行")
    return objs


def stage_index(vs_doc):
    stage_vs, cond, cats = {}, collections.defaultdict(set), {}
    for vs in vs_doc["value_streams"]:
        if not vs.get("id") or not vs.get("stages"):
            raise InputError(f"价值流缺 id 或 stages：{vs.get('id')}")
        cats[vs["id"]] = vs.get("category")
        pa = vs.get("stage_pattern") or {}
        for s in vs["stages"]:
            stage_vs[s["id"]] = vs["id"]
            leaves = [lf for k in ("entry", "exit") for lf in sac.leaves(sac.expand(s.get(k) or {"all": []}, pa))]
            leaves += list(s.get("in_stage_states") or [])
            cond[vs["id"]] |= {lf["ref"] for lf in leaves if isinstance(lf, dict) and str(lf.get("ref", "")).startswith("OBJ-")}
    return stage_vs, cond, cats


def analyse(catalog, stage_vs, vs_cond, vs_cat, touched, supports, objects, dispositions=None):
    """touched: 流程 → 作用对象集合（读写、主、次、feeds）；supports: 流程 → 支撑本段的段列表。"""
    live = [p for p in catalog if p.get("type") not in SKIP_TYPES and p.get("status") != "retired"]
    bad_stage = [(p["id"], s) for p in live for s in p.get("stages") or [] if s not in stage_vs]
    if bad_stage:
        raise InputError("目录引用了不存在的阶段：" + "、".join(f"{a}→{b}" for a, b in bad_stage))
    C = {k: [] for k in TITLES}
    vs_of = {}
    for p in live:
        own = {stage_vs[s] for s in p.get("stages") or []}
        sup = {stage_vs[s] for s in supports.get(p["id"], []) if s in stage_vs}
        vs_of[p["id"]] = (own, sup)
        objs = touched.get(p["id"], set())
        if p.get("type") == "read_model":
            if not own and not sup:
                C["C5"].append((p["id"], p["name"], "无所标段、无支撑段"))
            continue
        why = []
        if not own and not sup:
            why.append("集成流程未按供给挂到任何阶段" if p.get("type") == "integration" else
                       "无阶段、无支撑本段" if p.get("support") else "无阶段")
        if not objs:
            why.append("无作用对象")
        if why:
            C["C1"].append((p["id"], p["name"], "；".join(why)))
    used = {o for ps in touched.values() for o in ps}
    disp = dispositions or {}
    unknown = [o for o in disp if o not in objects]
    if unknown:
        raise InputError("处置定论引用了不在对象清单的对象：" + "、".join(unknown))
    C["C2"], C["excluded"] = [], []
    for o, n in sorted(objects.items(), key=lambda x: olc.num(x[0])):
        if o in used:
            continue
        d = disp.get(o)
        if d and d["kind"] != "subordinate":
            C["excluded"].append((o, n, {"reference_data": "参照数据", "view": "视图"}[d["kind"]] + "，已定论不要求承载流程"))
        elif d and d["parent_object"] in used:
            C["excluded"].append((o, n, f"从属对象，由父对象 {d['parent_object']} 的流程承载"))
        else:
            C["C2"].append((o, n, "无任何流程读写或以它为主、次对象" + (f"（从属对象，父对象 {d['parent_object']} 也无流程）" if d else "")))
    homes = collections.defaultdict(set)
    for p in live:
        if p.get("type") != "read_model":
            for v in vs_of[p["id"]][0]:
                for o in p.get("_anchor_objs", set()) & set(objects):
                    homes[(v, o)].add(p["id"])
    for (v, o), ps in sorted(homes.items(), key=lambda x: (x[0][0], olc.num(x[0][1]))):
        if o not in vs_cond.get(v, set()):
            C["C3"].append((f"{v} {o}", objects[o], "由 " + "、".join(sorted(ps, key=olc.num)) + " 主写入，但不在该价值流任何阶段条件里"))
    mgmt = [p for p in live if p.get("type") != "read_model" and vs_of[p["id"]][0] and all(vs_cat.get(v) == "management" for v in vs_of[p["id"]][0])]
    governed = collections.defaultdict(set)
    for p in live:
        own = vs_of[p["id"]][0]
        if own and any(vs_cat.get(v) != "management" for v in own):
            for o in touched.get(p["id"], set()):
                governed[o].add(p["id"])
    for p in mgmt:
        if not any(governed.get(o) for o in touched.get(p["id"], set())):
            C["C4"].append((p["id"], p["name"], "作用对象均未被任何非管理价值流的流程读写"))
    return C


def anchor_objects(catalog, written):
    """主写入对象：目录主对象、次要对象，否则本流程写入（C/U/D）的对象。"""
    for p in catalog:
        prim = str(p.get("primary_object") or "")
        objs = {prim} if prim.startswith("OBJ-") else set(written.get(p["id"], set()))
        objs |= {str(o) for o in p.get("secondary_objects") or []}
        p["_anchor_objs"] = {o for o in objs if o.startswith("OBJ-")}
    return catalog


def touched_objects(catalog, pobj_doc, agg_objs):
    touched = olc.written_objs(pobj_doc, agg_objs, olc.WRITE_OPS | {"R", "?"})
    for p in catalog:
        extra = {str(p.get("primary_object") or "")} | {str(o) for o in (p.get("secondary_objects") or []) + (p.get("feeds") or []) + (p.get("acts_on") or [])}
        touched[p["id"]] = set(touched.get(p["id"], set())) | {o for o in extra if o.startswith("OBJ-")}
    by_id = {p["id"]: p for p in catalog}
    for p in catalog:
        if p.get("type") == "integration":
            for q in p.get("feeds") or []:
                if q in by_id:
                    touched[p["id"]] |= {str(by_id[q].get("primary_object"))} - {"None", "none", ""}
    return touched


def render(C, stale):
    out = ["# 流程归集闭环报告（anchoring_check.py 生成，勿手改）", "",
           "依据：intent 2026-10-17-174814（方法 ①–⑥）、2026-10-16-212218（①支撑本段、④集成）；方法见 PROCESS_METHOD_DRAFT.md §12。", "",
           "| 检查 | 条数 | 拦截 |", "|---|---|---|"]
    out += [f"| {k} {t} | {len(C[k])} | {'是' if k in ('C1', 'C2') else '否（提议输入）'} |" for k, t in TITLES.items()]
    if stale:
        out += ["", f"注意：object-layer.yaml 落后于目录（{stale}），本报告在内存重算，重跑 object_layer_check.py 后一致。"]
    for k, t in TITLES.items():
        out += ["", f"## {k} {t}（{len(C[k])}）", ""]
        out += [f"- {a} {b}：{c}" for a, b, c in C[k]] or ["无"]
    out += ["", f"## 已定论排除（{len(C['excluded'])}，不计入 C2；intent 2026-10-17-201810）", ""]
    out += [f"- {a} {b}：{c}" for a, b, c in C["excluded"]] or ["无"]
    return "\n".join(out) + "\n"


def stale_note(catalog, ol_path):
    if not ol_path.exists():
        return "文件不存在"
    doc = yaml.safe_load(ol_path.read_text(encoding="utf-8")) or {}
    have = {p["id"]: p.get("stages") or [] for p in doc.get("processes") or []}
    diff = [p["id"] for p in catalog if p.get("type") not in SKIP_TYPES and have.get(p["id"]) is not None and list(p.get("stages") or []) != have[p["id"]]]
    return f"{len(diff)} 条所标段不同" if diff else ""


def compute(argv=()):
    """C1–C5 的判定结果与输入；全景追溯模型（architecture/panorama）共用这一份口径。"""
    import closeout_check as cc
    extra = cc.load_extra_units(cc.extra_paths(argv))
    vs_doc = load_yaml(sac.VS_FILE, "value_streams")
    catalog = cc.overlay_catalog(load_yaml(sac.CATALOG, "processes")["processes"], extra)
    pobj_doc = load_yaml(olc.POBJ, "processes")
    if not sac.INFO_MAP.exists():
        raise InputError(f"缺输入文件：{sac.INFO_MAP}")
    objects = info_objects(sac.INFO_MAP.read_text(encoding="utf-8"))
    aggs = cc.load_aggregates()
    if extra:
        pobj_doc = olc.overlay_process_objects(pobj_doc, extra, aggs, sac.load_machines("--local" in argv))
    agg_objs = {a: v.get("objs") or [] for a, v in aggs.items()}
    A = olc.analyse(catalog, pobj_doc, agg_objs, vs_doc, yaml.safe_load(olc.CAPS.read_text(encoding="utf-8")), {}, None, olc.load_categories())
    supports = {r["id"]: r["supports"] for r in A["support_rows"]}
    stage_vs, vs_cond, vs_cat = stage_index(vs_doc)
    anchor_objects(catalog, olc.written_objs(pobj_doc, agg_objs))
    touched = touched_objects(catalog, pobj_doc, agg_objs)
    C = analyse(catalog, stage_vs, vs_cond, vs_cat, touched, supports, objects,
                olc.object_dispositions(sac.INFO_MAP.read_text(encoding="utf-8")))
    return {"C": C, "catalog": catalog, "objects": objects, "supports": supports, "stage_vs": stage_vs, "touched": touched}


def run(argv):
    R = compute(argv)
    C, catalog = R["C"], R["catalog"]
    md = render(C, stale_note(catalog, olc.OUT_YAML))
    if "--report" in argv:
        REPORT.write_text(md, encoding="utf-8")
    print("；".join(f"{k} {len(C[k])}" for k in TITLES) + f"；已定论排除 {len(C['excluded'])}")
    return 1 if C["C1"] or C["C2"] else 0


def self_test():
    vs = {"value_streams": [
        {"id": "VS-A", "category": "business", "stages": [{"id": "VS-A.1", "exit": {"all": [{"ref": "OBJ-01", "state": "完"}]}}]},
        {"id": "VS-M", "category": "management", "stages": [{"id": "VS-M.1", "exit": {"all": [{"ref": "OBJ-09", "state": "已生效"}]}}]}]}
    stage_vs, vs_cond, vs_cat = stage_index(vs)
    objects = {"OBJ-01": "甲", "OBJ-02": "乙", "OBJ-09": "规则"}
    clean = [{"id": "P1", "name": "办甲", "type": "business", "stages": ["VS-A.1"], "primary_object": "OBJ-01"},
             {"id": "P2", "name": "用乙", "type": "business", "stages": [], "support": "支撑"},
             {"id": "P3", "name": "供乙", "type": "integration", "stages": [], "feeds": ["P2"]},
             {"id": "P4", "name": "定规则", "type": "business", "stages": ["VS-M.1"], "primary_object": "OBJ-09"},
             {"id": "P5", "name": "看甲", "type": "read_model", "stages": ["VS-A.1"]},
             {"id": "P6", "name": "已并", "type": "merged"}]
    touch = {"P1": {"OBJ-01", "OBJ-09"}, "P2": {"OBJ-02"}, "P3": {"OBJ-02"}, "P4": {"OBJ-09"}, "P5": {"OBJ-01"}}
    sup = {"P2": ["VS-A.1"], "P3": ["VS-A.1"]}

    def go(cat, tch=touch, sp=sup, objs=objects, cond=vs_cond, dispositions=None):
        cat = anchor_objects([dict(p) for p in cat], tch)
        return analyse(cat, stage_vs, cond, vs_cat, tch, sp, objs, dispositions)

    ok = lambda n, c: print(f"自测 {n}: {'通过' if c else '失败'}") or c
    base = go(clean)
    res = [ok("干净夹具五项全空（支撑本段、集成按供给、管理关联、读模型归段均算归集）", all(not v for k, v in base.items() if k != 'excluded'))]
    c1 = go(clean + [{"id": "P7", "name": "孤立", "type": "business", "stages": []}, {"id": "P8", "name": "孤立集成", "type": "integration", "stages": []}],
            dict(touch, P7={"OBJ-01"}), sup)
    res.append(ok("破坏 C1：无阶段业务流程与未挂接集成流程被抓", [x[0] for x in c1["C1"]] == ["P7", "P8"]))
    c1b = go(clean, dict(touch, P1=set()))
    res.append(ok("破坏 C1：无作用对象被抓", [x[0] for x in c1b["C1"]] == ["P1"] and "无作用对象" in c1b["C1"][0][2]))
    c2 = go(clean, objs=dict(objects, **{"OBJ-03": "丙"}))
    res.append(ok("破坏 C2：新增无流程对象被抓", [x[0] for x in c2["C2"]] == ["OBJ-03"]))
    sub = {"OBJ-03": {"kind": "subordinate", "parent_object": "OBJ-01"}}
    d1 = go(clean, objs=dict(objects, **{"OBJ-03": "丙"}), dispositions=sub)
    res.append(ok("定论：从属对象父有流程不报 C2，列入已定论排除", not d1["C2"] and [x[0] for x in d1["excluded"]] == ["OBJ-03"]))
    d2 = go(clean, objs=dict(objects, **{"OBJ-03": "丙"}), dispositions={"OBJ-03": {"kind": "subordinate", "parent_object": "OBJ-77"}})
    res.append(ok("破坏定论：从属对象父无流程仍报 C2", [x[0] for x in d2["C2"]] == ["OBJ-03"] and not d2["excluded"]))
    d3 = go(clean, objs=dict(objects, **{"OBJ-96": "账", "OBJ-103": "财"}), dispositions={"OBJ-96": {"kind": "reference_data"}, "OBJ-103": {"kind": "view"}})
    res.append(ok("定论：参照数据与视图不报 C2", not d3["C2"] and [x[0] for x in d3["excluded"]] == ["OBJ-96", "OBJ-103"]))
    d4 = go(clean, objs=dict(objects, **{"OBJ-96": "账"}), dispositions={})
    res.append(ok("破坏定论：无定论的无流程对象仍报 C2", [x[0] for x in d4["C2"]] == ["OBJ-96"]))
    pobj = {"processes": [{"id": "P1", "objects": [{"obj": "OBJ-01", "aggregates": ["AGG-1"], "ops": "U"}]}]}
    tc = touched_objects([{"id": "P1"}, {"id": "P2", "acts_on": ["OBJ-02"]}], pobj, {"AGG-1": ["OBJ-01"]})
    res.append(ok("作用对象：目录 acts_on 计入", tc["P1"] == {"OBJ-01"} and tc["P2"] == {"OBJ-02"}))
    c1r = go(clean + [{"id": "P10", "name": "已退役", "type": "retired", "stages": [], "note": "移出"}])
    res.append(ok("已退役（type: retired）不参与", not c1r["C1"]))
    c3 = go([dict(p, secondary_objects=["OBJ-02"]) if p["id"] == "P1" else p for p in clean], dict(touch, P1={"OBJ-01", "OBJ-02", "OBJ-09"}))
    res.append(ok("破坏 C3：主写入对象不在价值流阶段条件被抓", [x[0] for x in c3["C3"]] == ["VS-A OBJ-02"]))
    c4 = go(clean, dict(touch, P1={"OBJ-01"}))
    res.append(ok("破坏 C4：管理流程对象无被管流程读写被抓", [x[0] for x in c4["C4"]] == ["P4"]))
    c5 = go([dict(p, stages=[]) if p["id"] == "P5" else p for p in clean])
    res.append(ok("破坏 C5：读模型无段被抓，且不计入 C1", [x[0] for x in c5["C5"]] == ["P5"] and not c5["C1"]))
    try:
        go(clean + [{"id": "P9", "name": "错段", "type": "business", "stages": ["VS-X.9"]}])
        res.append(ok("无效输入：引用不存在的阶段报错", False))
    except InputError:
        res.append(ok("无效输入：引用不存在的阶段报错", True))
    import tempfile
    with tempfile.TemporaryDirectory() as d:
        bad = Path(d) / "bad.yaml"
        bad.write_text("processes: []\n", encoding="utf-8")
        for name, path in (("缺文件", Path(d) / "none.yaml"), ("空列表", bad)):
            try:
                load_yaml(path, "processes")
                res.append(ok(f"无效输入：{name}报错", False))
            except InputError:
                res.append(ok(f"无效输入：{name}报错", True))
    res.append(ok("信息图跳过「已非对象」行", info_objects("| OBJ-01 | 甲 | x |\n| OBJ-02 | 乙 | 【行号保留，已非对象】 |\n") == {"OBJ-01": "甲"}))
    if not all(res):
        return 1
    print("自测 通过")
    return 0


def main(argv):
    if "--self-test" in argv:
        return self_test()
    try:
        return run(argv)
    except (InputError, ValueError) as error:
        print(f"输入无效：{error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
