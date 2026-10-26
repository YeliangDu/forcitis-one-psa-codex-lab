"""Generate derived tables of STEP1_VALUE_STREAMS.md from value-streams.yaml.

Reads ssot/05-value-streams/value-streams.yaml and capabilities.v2.yaml (same folder), validates the value-stream
data, then rewrites three marked blocks in STEP1_VALUE_STREAMS.md:
  vs-stats   counts, vs-matrix   stakeholder coverage matrix,
  vs-links   shared-object dependency table (object state -> stages that reference it).
Value streams are never linked to each other directly; only through shared object states.

Usage: python3 -I vs_links_gen.py [--check]
--check changes nothing and exits 1 when a block is out of date. Exit code is also 1 on any data error.
"""
import re
import sys
from collections import defaultdict
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent
VS_FILE = HERE.parent / "ssot" / "05-value-streams" / "value-streams.yaml"
CAP_FILE = HERE / "capabilities.v2.yaml"
MD_FILE = HERE / "STEP1_VALUE_STREAMS.md"

OBJ_RE = re.compile(r"^OBJ-\d{2,3}$")
GAP_REF, DERIVED_REF = "缺对象状态", "派生"
ONLY_PARTICIPANTS = {"SH-R1"}
errors = []


def err(msg):
    errors.append(msg)


def split_state(state):
    s = re.sub(r"（[^）]*）", "", str(state)).replace("→", "/")
    return [p.strip() for p in s.split("/") if p.strip()]


def leaves(cond, patterns, seen=()):
    """Yield every leaf (a mapping with 'ref') below a condition node."""
    if not cond:
        return
    if isinstance(cond, list):
        for c in cond:
            yield from leaves(c, patterns, seen)
        return
    if "ref" in cond:
        yield cond
        return
    if "pattern" in cond:
        name = cond["pattern"]
        if name not in patterns:
            err(f"unknown pattern {name}")
        elif name not in seen:
            yield from leaves(patterns[name], patterns, seen + (name,))
    for key in ("all", "any"):
        if key in cond:
            yield from leaves(cond[key], patterns, seen)


def vs_of(label):
    m = re.match(r"VS-\d\d", label)
    return m.group(0) if m else label


def load():
    data = yaml.safe_load(VS_FILE.read_text(encoding="utf-8"))
    caps = yaml.safe_load(CAP_FILE.read_text(encoding="utf-8"))
    return data, caps


def collect(data):
    """Return list of (label, kind, leaf). kind: start end entry exit in act_in act_out."""
    rows = []
    for vs in data["value_streams"]:
        vid = vs["id"]
        patterns = vs.get("stage_pattern", {})
        for key, kind, suffix in (("starts_when", "start", "（起始）"), ("ends_in", "end", "（结果）")):
            for lf in leaves(vs.get(key), patterns):
                rows.append((vid + suffix, kind, lf))
        for act in vs.get("cross_stage_activities") or []:
            if not isinstance(act, dict):
                continue
            label = f"{vid} 活动·{act['name']}"
            for lf in leaves(act.get("inputs"), patterns):
                rows.append((label, "act_in", lf))
            for lf in leaves(act.get("outputs"), patterns):
                rows.append((label, "act_out", lf))
        for st in vs.get("stages", []):
            for key, kind in (("entry", "entry"), ("exit", "exit"), ("in_stage_states", "in")):
                for lf in leaves(st.get(key), patterns):
                    rows.append((st["id"], kind, lf))
    return rows


def validate(data, caps, rows):
    sh_ids = {s["id"] for s in data["stakeholders"]}
    mss = {m["id"] for m in data.get("missing_states", [])}
    cond_ms = {m["id"] for m in data.get("conditional_missing_states", [])}
    resolved = {m["id"] for m in data.get("resolved_missing_states", [])}
    scs = {c["id"] for c in data.get("state_caveats", [])}
    if mss & cond_ms or mss & resolved or cond_ms & resolved:
        err("missing-state id appears in more than one list")
    stage_ids = set()
    for vs in data["value_streams"]:
        for k in ("id", "name", "description", "triggering_stakeholders", "value", "stages"):
            if not vs.get(k):
                err(f"{vs.get('id')}: missing {k}")
        for sid in vs.get("triggering_stakeholders", []) + vs.get("value_receivers", []):
            if sid not in sh_ids:
                err(f"{vs['id']}: unknown stakeholder {sid}")
            if sid in ONLY_PARTICIPANTS:
                err(f"{vs['id']}: {sid} may only be a stage participant")
        for st in vs["stages"]:
            stage_ids.add(st["id"])
            for k in ("id", "name", "description", "participants", "entry", "exit", "value_item"):
                if not st.get(k):
                    err(f"{st.get('id')}: missing {k}")
            if not st["id"].startswith(vs["id"] + "."):
                err(f"{st['id']}: id not under {vs['id']}")
            for sid in st.get("participants", []):
                if sid not in sh_ids:
                    err(f"{st['id']}: unknown participant {sid}")
    used_gap = defaultdict(set)
    for label, kind, lf in rows:
        ref = lf["ref"]
        if ref == GAP_REF:
            gap = lf.get("gap")
            if gap not in mss:
                err(f"{label}: gap {gap} not in missing_states")
            used_gap[gap].add(label)
        elif ref == DERIVED_REF:
            if kind != "in":
                err(f"{label}: derived state may only be an in-stage state")
        elif not OBJ_RE.match(str(ref)):
            err(f"{label}: bad ref {ref}")
        elif not lf.get("state"):
            err(f"{label}: {ref} without state")
        if lf.get("caveat") and lf["caveat"] not in scs:
            err(f"{label}: unknown caveat {lf['caveat']}")
    for m in data.get("missing_states", []):
        if set(m.get("used_in", [])) != used_gap.get(m["id"], set()):
            err(f"{m['id']}: used_in {sorted(m.get('used_in', []))} != actual {sorted(used_gap.get(m['id'], []))}")
    for m in data.get("conditional_missing_states", []):
        if m["id"] in used_gap:
            err(f"{m['id']}: conditional state must not be used in a condition")
    # step-3 warning: level-1 objects not referenced by any condition
    referenced = {lf["ref"] for _, _, lf in rows if OBJ_RE.match(str(lf["ref"]))}
    level1 = [c["object"] for c in caps["capabilities"]]
    unref = [o for o in level1 if o not in referenced]
    listed = {w["ref"]: w for w in data.get("step3_warnings", [])}
    for o in unref:
        if o not in listed:
            err(f"level-1 object {o} unreferenced and not in step3_warnings")
    for o, w in listed.items():
        if w["status"] == "已补引用":
            if o not in referenced:
                err(f"{o}: marked 已补引用 but not referenced")
        elif o in referenced:
            err(f"{o}: marked {w['status']} but is referenced")
    return referenced, level1, unref


def link_table(rows):
    deps = defaultdict(lambda: {"produce": set(), "consume": set(), "inside": set()})
    for label, kind, lf in rows:
        if not OBJ_RE.match(str(lf["ref"])):
            continue
        for st in split_state(lf["state"]):
            slot = {"exit": "produce", "end": "produce", "act_out": "produce",
                    "entry": "consume", "start": "consume", "act_in": "consume", "in": "inside"}[kind]
            deps[(lf["ref"], st)][slot].add(label)
    shared = []
    for key in sorted(deps, key=lambda k: (int(k[0][4:]), k[1])):
        d = deps[key]
        vss = {vs_of(x) for s in d.values() for x in s}
        if len(vss) >= 2:
            shared.append((key, d, vss))

    def cell(items):
        return "、".join(sorted(items)) if items else "—"

    out = ["| 对象状态 | 产出（退出、结果、活动输出） | 进入或起始引用 | 段内引用 | 涉及价值流 |", "|---|---|---|---|---|"]
    for (obj, st), d, vss in shared:
        out.append(f"| {obj} {st} | {cell(d['produce'])} | {cell(d['consume'])} | {cell(d['inside'])} | {len(vss)} |")
    return out, len(shared), len(deps)


def matrix(data):
    vss = data["value_streams"]
    out = ["| 干系人 | " + " | ".join(v["id"][3:] for v in vss) + " | 结论 |", "|---|" + "---|" * (len(vss) + 1)]
    pair = defaultdict(int)
    for s in data["stakeholders"]:
        pair[(s.get("category"), s.get("sub"))] += 1
    uncovered = []
    for s in data["stakeholders"]:
        sid = s["id"]
        if s["type"] == "参与者":
            label = f"{sid} {s['members']}"
        else:
            sub = s.get("sub")
            label = f"{sid} {s['category']}" + (f"·{sub}" if sub and sub != "—" else "")
            if pair[(s.get("category"), sub)] > 1:
                label += f"（{s['members'].split('；')[0]}）"
        cells, role = [], set()
        for v in vss:
            trig, recv = sid in v["triggering_stakeholders"], sid in v.get("value_receivers", [])
            part = any(sid in st["participants"] for st in v["stages"])
            c = ("触" if trig else "") + ("受" if recv else "")
            if not c and part:
                c = "参"
            role |= set(c)
            cells.append(c)
        if role & {"触", "受"}:
            concl = "覆盖"
        elif role:
            concl = "只作阶段参与者"
        else:
            concl = "未出现"
            uncovered.append(sid)
        out.append(f"| {label} | " + " | ".join(cells) + f" | {concl} |")
    return out, uncovered


def stats(data, rows, nshared, ndeps, referenced, level1, unref):
    vss = data["value_streams"]
    nst = sum(len(v["stages"]) for v in vss)
    objs = {lf["ref"] for _, _, lf in rows if OBJ_RE.match(str(lf["ref"]))}
    nobj_refs = sum(1 for _, _, lf in rows if OBJ_RE.match(str(lf["ref"])))
    ngap = sum(1 for _, _, lf in rows if lf["ref"] == GAP_REF)
    gaps_used = sorted({lf["gap"] for _, _, lf in rows if lf["ref"] == GAP_REF})
    lines = [
        f"- 价值流 {len(vss)} 条（{'、'.join(v['id'] + ' ' + v['name'] for v in vss)}），阶段 {nst} 个。",
        f"- 条件引用对象 {len(objs)} 个、{nobj_refs} 处（含起始、结果、段内、活动输入输出）；引用「缺对象状态」" + (f"{ngap} 处，涉及 {len(gaps_used)} 项（{'、'.join(gaps_used)}）。" if gaps_used else "无。"),
        f"- 对象状态 {ndeps} 个被引用，其中 {nshared} 个被两条及以上价值流引用（见共享依赖表）。",
        f"- 一级对象 {len(level1)} 个，被条件引用 {len(level1) - len(unref)} 个，未被引用 {len(unref)} 个（{'、'.join(unref)}），处置见第 4.3 节。",
        f"- 对象状态冲突或待定 {len(data.get('state_caveats', []))} 处。",
    ]
    return lines, {"vs": len(vss), "stages": nst, "objs": len(objs), "refs": nobj_refs, "gaps": gaps_used}


def replace_block(text, name, lines):
    begin, end = f"<!-- {name}:begin -->", f"<!-- {name}:end -->"
    if begin not in text or end not in text:
        err(f"marker {name} missing in {MD_FILE.name}")
        return text
    head, rest = text.split(begin, 1)
    _, tail = rest.split(end, 1)
    return head + begin + "\n" + "\n".join(lines) + "\n" + end + tail


def main():
    check = "--check" in sys.argv[1:]
    data, caps = load()
    rows = collect(data)
    referenced, level1, unref = validate(data, caps, rows)
    link_lines, nshared, ndeps = link_table(rows)
    matrix_lines, uncovered = matrix(data)
    if uncovered:
        err(f"stakeholders in no value stream: {uncovered}")
    stat_lines, summary = stats(data, rows, nshared, ndeps, referenced, level1, unref)
    old = MD_FILE.read_text(encoding="utf-8")
    new = old
    for name, lines in (("vs-stats", stat_lines), ("vs-matrix", matrix_lines), ("vs-links", link_lines)):
        new = replace_block(new, name, lines)
    if errors:
        print("ERRORS:")
        for e in errors:
            print(" -", e)
        return 1
    print("\n".join(stat_lines))
    if check:
        if new != old:
            print("STEP1_VALUE_STREAMS.md derived blocks are out of date")
            return 1
        print("derived blocks up to date")
        return 0
    if new != old:
        MD_FILE.write_text(new, encoding="utf-8")
        print("updated derived blocks in", MD_FILE.name)
    else:
        print("derived blocks already up to date")
    return 0


if __name__ == "__main__":
    sys.exit(main())
