"""Conformance checks J1-J7 of the seams (METHOD_V4_DRAFT.md section 3) on the architecture proposals; rules are J_CHECKS.md (one-architecture).

Inputs: ssot/05-value-streams/value-streams.yaml (VS) and ssot/10-objects/ENTERPRISE_INFO_MAP.md (IM) (a fixture folder may hold value-streams.yaml / ENTERPRISE_INFO_MAP.md instead), same folder: capabilities.v2.yaml (CAP), ENTERPRISE_AGGREGATES_DRAFT.md (AG), STAGE_TYPES_DRAFT.md (stage names, J3.2),
aggregates_dep_check.py (DEP, J7.1-J7.3) and vs_links_gen.py (state splitting and condition leaves). Read-only.

30 checks: the 28 rules J1.1-J7.5, J2.4r (the derived-value list outside the gates) and J5.2r (combined A classes), of which J3.2, J2.4r and J5.2r only report. Every check prints PASS / FAIL / REPORT with its findings; the exit code is 1 when any check fails. REPORT checks (J3.2) never fail. The counts and the capability x stage table close the output.
J2.3 splits the leaf state and the IM key-state column the way vs_links_gen.py does, and also cuts the IM column at ； ; ⇄ (it mixes states with explanatory clauses).
J4.4 reads the field `unsupported_reason` on a first-level capability (the name agreed with one-architecture).

Usage: python3 -I j_checks.py [proposals-dir] | python3 -I j_checks.py --self-test
"""
import importlib.util
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent
SSOT_VS = HERE.parent / "ssot" / "05-value-streams" / "value-streams.yaml"
SSOT_IM = HERE.parent / "ssot" / "10-objects" / "ENTERPRISE_INFO_MAP.md"
BANNED_CAP_KEYS = {"bc", "module", "subdomain", "current_bc", "strategic_hint"}
BLOCK_KEYS = {"block", "blocking", "gate_mode"}
MODES = {"api", "事件", "不校验"}
MAPPINGS = ("一对一", "次级在内", "一拆多", "无聚合", "技术聚合", "运行单据")
STAGE_WHITELIST = {"AGG-20", "AGG-04"}  # AGG-20 the work process, AGG-04 the configuration version of the stage type catalogue (one-architecture): neither is a stage state machine
REF_RE = re.compile(r"^(OBJ|REF)-\d+$")
STAGE_ID_RE = re.compile(r"^VS-\d{2}\.\d+$")


def load_module(name, path, argv=()):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    saved = sys.argv
    sys.argv = [str(path), *argv]
    try:
        spec.loader.exec_module(module)
    finally:
        sys.argv = saved
    return module


def cells(line):
    return [c.strip() for c in line.strip().strip("|").split("|")]


def parse_im(lines):
    rows = {}
    for no, line in enumerate(lines, 1):
        m = re.match(r"^\|\s*(OBJ-\d+)\s*\|", line)
        if m and m.group(1) not in rows:
            c = cells(line)
            if len(c) >= 6:
                rows[m.group(1)] = {"name": c[1], "level": c[3].lstrip("*"), "states": c[5], "line": no}
    return rows


def parse_ag(lines):
    """INV rows of section 1.2 and aggregate rows of section 2.2 (one entry per row, so a repeated id is visible)."""
    section, bc, invs, aggs, bcs = None, None, {}, [], []
    for no, line in enumerate(lines, 1):
        if line.startswith("### "):
            section = line[4:].split(" ")[0]
        if section == "1.2":
            m = re.match(r"^\|\s*(INV-\d+[a-z]?)\s*\|", line)
            if m:
                c = cells(line)
                invs[m.group(1)] = {"cls": c[2], "agg": c[3], "raw": line, "line": no}
        elif section == "2.2":
            m = re.match(r"^\*\*(B-\d+)\b", line)
            if m:
                bc = m.group(1)
                bcs.append(bc)
            m = re.match(r"^\|\s*((?:AGG|REF)-\d+)\s*\|", line)
            if m:
                c = cells(line)
                aggs.append({"id": m.group(1), "bc": bc, "root": c[1], "inner": c[2], "map": c[3], "inv": c[4], "events": c[5], "ref": c[6] if len(c) > 6 else "", "line": no, "raw": line})
    return invs, aggs, bcs


def expand_ids(prefix, text, problems=None):
    """Every id named in a cell, with the shorthands the drafts use: `AGG-03、04、36`, the ranges `INV-05–09` and the letter suffix `INV-16a、03b`.
    A shorthand number follows its separator without a space and is not a count (`AGG-01 — 2 个`, `INV-12，3 条` read as one id each); a descending range is reported in `problems`."""
    out = set()
    tail = r"(?:[、,，]|[–—~～-])\d+[a-z]?(?!\s*[个条项年次%]|\d)"
    for m in re.finditer(rf"{prefix}-(\d+)([a-z]?)((?:{tail})*)", text):
        width, prev = len(m.group(1)), int(m.group(1))
        out.add(f"{prefix}-{prev:0{width}d}{m.group(2)}")
        for sep, num, suffix in re.findall(r"([、,，]|[–—~～-])(\d+)([a-z]?)", m.group(3)):
            n, w = int(num), max(2, len(num))
            if sep in "–—~～-":
                if n < prev:
                    if problems is not None:
                        problems.append(f"descending range {m.group(0)!r}")
                    continue
                out |= {f"{prefix}-{k:0{w}d}" for k in range(prev, n + 1)}
                out.add(f"{prefix}-{n:0{w}d}{suffix}")
            else:
                out.add(f"{prefix}-{n:0{w}d}{suffix}")
            prev = n
    return out


def strip_parens(text):
    text = str(text)
    while True:
        new = re.sub(r"（[^（）]*）", "", text)
        if new == text:
            return text
        text = new


def keys_of(node):
    if isinstance(node, dict):
        for k, v in node.items():
            yield k
            yield from keys_of(v)
    elif isinstance(node, list):
        for x in node:
            yield from keys_of(x)


class Data:
    def __init__(self, folder):
        self.folder = Path(folder)
        local = self.folder / "module-dependencies.txt"
        self.built = local if local.exists() else HERE.parents[3] / "backend" / "architecture" / "module-dependencies.txt"
        self.vs_path = self.folder / "value-streams.yaml" if (self.folder / "value-streams.yaml").exists() else SSOT_VS
        self.im_path = self.folder / "ENTERPRISE_INFO_MAP.md" if (self.folder / "ENTERPRISE_INFO_MAP.md").exists() else SSOT_IM
        missing = [n for n in ("capabilities.v2.yaml", "ENTERPRISE_AGGREGATES_DRAFT.md", "STAGE_TYPES_DRAFT.md", "vs_links_gen.py", "aggregates_dep_check.py") if not (self.folder / n).exists()]
        missing += [str(q) for q in (self.vs_path, self.im_path) if not q.exists()]
        if missing:
            raise SystemExit(f"missing input in {self.folder}: {', '.join(missing)}")
        read = lambda name: (self.folder / name).read_text(encoding="utf-8")
        self.vs = yaml.safe_load(self.vs_path.read_text(encoding="utf-8"))
        self.cap = yaml.safe_load(read("capabilities.v2.yaml"))
        self.im_lines = self.im_path.read_text(encoding="utf-8").splitlines()
        self.ag_text = read("ENTERPRISE_AGGREGATES_DRAFT.md")
        self.ag_lines = self.ag_text.splitlines()
        self.stage_types_text = read("STAGE_TYPES_DRAFT.md")
        self.im = parse_im(self.im_lines)
        self.invs, self.aggs, self.bcs = parse_ag(self.ag_lines)
        self.vl = load_module("vs_links_gen_j", self.folder / "vs_links_gen.py")
        self.vl.errors.clear()
        self.streams = self.vs["value_streams"]
        self.stakeholders = {s["id"]: s for s in self.vs["stakeholders"]}
        self.stages = [(v, s) for v in self.streams for s in v["stages"]]

    def gate_leaves(self, v, stage):
        patterns = v.get("stage_pattern", {})
        for side in ("entry", "exit"):
            for leaf in self.vl.leaves(stage.get(side), patterns):
                yield side, leaf


def check_j1_1(d):
    bad = []
    for v in d.streams:
        trig = v.get("triggering_stakeholders") or []
        if not trig or any(t not in d.stakeholders for t in trig):
            bad.append(f"{v['id']}: triggering_stakeholders {trig}")
    return bad


def check_j1_2(d):
    bad = []
    for v, s in d.stages:
        if "stages" in s or "sub_value_streams" in s:
            bad.append(f"{s['id']}: nested stages")
        if not STAGE_ID_RE.match(s["id"]) or not s["id"].startswith(v["id"] + "."):
            bad.append(f"{s['id']}: id is not {v['id']}.<n>")
    return bad


def stray_nodes(node):
    """Mappings of a condition tree that are neither a leaf (ref), nor any / all, nor a pattern."""
    if isinstance(node, list):
        for x in node:
            yield from stray_nodes(x)
    elif isinstance(node, dict):
        if "ref" in node or "pattern" in node:
            return
        if "any" in node or "all" in node:
            for key in ("any", "all"):
                yield from stray_nodes(node.get(key))
        else:
            yield node


def check_j1_3(d):
    bad = []
    for v, s in d.stages:
        for side in ("entry", "exit"):
            bad += [f"{s['id']} {side}: a node with no ref, any, all or pattern: {str(n)[:50]!r}" for n in stray_nodes(s.get(side))]
    for v, s in d.stages:
        for side, leaf in d.gate_leaves(v, s):
            if not REF_RE.match(str(leaf["ref"])):
                bad.append(f"{s['id']} {side}: ref {leaf['ref']!r} is not OBJ-n or REF-n")
    bad += [f"pattern: {e}" for e in d.vl.errors]
    return bad


def check_j1_4(d):
    used = set()
    for v in d.streams:
        used |= set(v.get("triggering_stakeholders") or []) | set(v.get("value_receivers") or [])
        for s in v["stages"]:
            used |= set(s.get("participants") or [])
    listed = set(re.findall(r"SH-[A-Z0-9]+", yaml.safe_dump(d.vs.get("out_of_scope") or [], allow_unicode=True)))
    return [f"{sid}: in no triggering_stakeholders, value_receivers or participants, and not in out_of_scope" for sid in d.stakeholders if sid not in used and sid not in listed]


def check_j2_1(d):
    bad = []
    for v, s in d.stages:
        for side in ("entry", "exit"):
            if not s.get(side) or not any(True for x in d.vl.leaves(s.get(side), v.get("stage_pattern", {})) if x):
                bad.append(f"{s['id']}: {side} is missing or has no leaf")
    return bad


def check_j2_2(d):
    return [f"{s['id']} {side}: {leaf['ref']} is not an IM object row, or is a non-object" for v, s in d.stages for side, leaf in d.gate_leaves(v, s)
            if leaf["ref"] not in d.im or "非对象" in d.im[leaf["ref"]]["level"]]


def im_states(d, text):
    """The IM key-state column cut the way vs_links_gen.py cuts it (nested parentheses removed first), and also at ； ; ⇄ 。 ： , 、, since the column mixes states with clauses and prefixes."""
    return {part.strip() for part in re.split(r"[/→；;⇄。：:，,、]", strip_parens(text)) if part.strip()}


def check_j2_3(d):
    caveats = {c.get("ref") for c in d.vs.get("state_caveats") or []}
    bad = []
    for v, s in d.stages:
        for side, leaf in d.gate_leaves(v, s):
            if not str(leaf.get("state") or "").strip():
                bad.append(f"{s['id']} {side}: {leaf['ref']} has no state")
                continue
            row = d.im.get(leaf["ref"])
            if not row or leaf["ref"] in caveats:
                continue
            wanted = [p.strip() for p in re.split(r"[/→]", strip_parens(leaf["state"])) if p.strip()]
            missing = [p for p in wanted if p not in im_states(d, row["states"])]
            if missing:
                note = " (the leaf carries a caveat note)" if leaf.get("caveat") else ""
                bad.append(f"{s['id']} {side}: {leaf['ref']} state {leaf.get('state')!r}: {'/'.join(missing)} not in the IM key states, and no state_caveats entry{note}")
    return bad


def derived_list(d):
    """The derived-value list at the end of AG section 2.2 (lines `- OBJ-n｜state / state｜where it is computed`) and the problems met while reading it."""
    out, problems, inside = {}, [], False
    for number, line in enumerate(d.ag_lines, 1):
        if "派生值清单" in line and line.startswith("**"):
            inside = True
            continue
        if inside and (line.startswith("#") or line.startswith("|")):
            inside = False
        if inside and line.strip().startswith("- "):
            m = re.match(r"^-\s*(OBJ-\d+)｜([^｜]+)｜(.+)$", line.strip())
            if not m:
                problems.append(f"line {number}: a derived-value row that is not `- OBJ-n｜state｜where`: {line.strip()[:50]!r}")
                continue
            out.setdefault(m.group(1), set()).update(p.strip() for p in m.group(2).split("/") if p.strip())
    if not out:
        problems.append("the derived-value list (**派生值清单**) was not found or has no row in section 2.2 of the aggregates draft")
    return out, problems


def check_j2_4(d):
    derived, bad = derived_list(d)
    for v, s in d.stages:
        for side, leaf in d.gate_leaves(v, s):
            if leaf.get("derived") or "派生" in str(leaf.get("state", "")):
                bad.append(f"{s['id']} {side}: {leaf['ref']} gate on a derived value")
            hits = [p for p in re.split(r"[/→]", strip_parens(leaf.get("state", ""))) if p.strip() in derived.get(leaf["ref"], ())]
            if hits:
                bad.append(f"{s['id']} {side}: {leaf['ref']} {'/'.join(h.strip() for h in hits)} is on the derived-value list of the aggregates draft")
    return bad


def check_j2_4r(d):
    derived, _ = derived_list(d)
    found = []
    for v in d.streams:
        patterns = v.get("stage_pattern", {})
        places = [(s["id"] + " in_stage_states", s.get("in_stage_states")) for s in v["stages"]] + [(v["id"] + " activity " + str(a.get("name")), [a.get("inputs"), a.get("outputs")]) for a in v.get("cross_stage_activities") or [] if isinstance(a, dict)]
        for where, cond in places:
            for leaf in d.vl.leaves(cond, patterns):
                if any(p.strip() in derived.get(leaf["ref"], ()) for p in re.split(r"[/→]", strip_parens(leaf.get("state", "")))):
                    found.append(f"{where}: {leaf['ref']} {leaf.get('state')} is on the derived-value list (report only outside entry / exit)")
    return found


def check_j2_5(d):
    bad = []
    for v, s in d.stages:
        found = set(keys_of(s)) & BLOCK_KEYS
        if found:
            bad.append(f"{s['id']}: blocking key {sorted(found)}")
    for v in d.streams:
        found = set(keys_of(v.get("stage_pattern") or {})) & BLOCK_KEYS
        if found:
            bad.append(f"{v['id']} stage_pattern: blocking key {sorted(found)}")
    return bad


def root_name(a):
    return a["root"].split("（")[0].strip()


def check_j3_1(d):
    return [f"{a['id']} (line {a['line']}): root name {root_name(a)!r} names a stage" for a in d.aggs if re.search("阶段|Stage", root_name(a)) and a["id"] not in STAGE_WHITELIST]


def stage_names(d):
    """The stage type catalogue of STAGE_TYPES_DRAFT.md: the 、-separated runs of at least five names on its catalogue line (启动 ... 结算, then 检查策划 ... 报告认可)."""
    for line in d.stage_types_text.splitlines():
        if "启动" in line and "整改跟踪" in line:
            runs = re.findall(r"[\u4e00-\u9fff]{2,6}(?:、[\u4e00-\u9fff]{2,6}){4,}", line)
            names = [n for run in runs for n in run.split("、")]
            return names or None
    return None


def check_j3_2(d):
    names = stage_names(d)
    if not names:
        return ["STAGE_TYPES_DRAFT.md: the line with the stage type catalogue (启动 ... 报告认可) was not found"]
    found = []
    for a in d.aggs:
        for column, text in (("内含", a["inner"]), ("不变量", a["inv"])):
            for fragment in re.findall(r"状态[^；。|]*", text):
                hits = [n for n in names if n in fragment]
                if hits:
                    found.append(f"{a['id']} {column}: stage name {'/'.join(hits)} next to 状态")
    return found


def check_j4_1(d):
    found = set(keys_of(d.cap)) & BANNED_CAP_KEYS
    return [f"capabilities.v2.yaml has the key {k}" for k in sorted(found)]


def level_one(d, obj):
    return obj in d.im and d.im[obj]["level"].startswith("一级")


def check_j4_2(d):
    bad = []
    for c in d.cap["capabilities"]:
        if not level_one(d, c["object"]):
            bad.append(f"{c['id']}: object {c['object']} is not a first-level object")
        for l2 in c.get("l2") or []:
            via = l2.get("via")
            for obj in [l2["object"]] + ([via] if isinstance(via, str) else list(via or [])):
                if obj not in d.im or "非对象" in d.im[obj]["level"]:
                    bad.append(f"{l2['id']}: {obj} is not an IM object")
    return bad


def check_j4_3(d):
    count = {}
    for c in d.cap["capabilities"]:
        count[c["object"]] = count.get(c["object"], 0) + 1
    return [f"{o}: {count.get(o, 0)} first-level capabilities (exactly one expected)" for o, r in d.im.items() if r["level"].startswith("一级") and count.get(o, 0) != 1]


def support_table(d):
    """A capability supports a stage when one of its objects is referenced anywhere in the stage: entry, exit, in_stage_states, or an input / output of a cross-stage activity of the same value stream."""
    stage_refs = {}
    for v, s in d.stages:
        patterns = v.get("stage_pattern", {})
        refs = {leaf["ref"] for _, leaf in d.gate_leaves(v, s)} | {leaf["ref"] for leaf in d.vl.leaves(s.get("in_stage_states"), patterns)}
        for a in v.get("cross_stage_activities") or []:
            if isinstance(a, dict):
                refs |= {leaf["ref"] for leaf in d.vl.leaves([a.get("inputs"), a.get("outputs")], patterns)}
        stage_refs[s["id"]] = refs
    table = {}
    for c in d.cap["capabilities"]:
        objs = {c["object"]} | {l2["object"] for l2 in c.get("l2") or []}
        table[c["id"]] = [sid for sid, refs in stage_refs.items() if objs & refs]
    return table, stage_refs


def check_j4_4(d):
    table, stage_refs = support_table(d)
    bad = []
    for c in d.cap["capabilities"]:
        if not table[c["id"]] and not c.get("unsupported_reason"):
            bad.append(f"{c['id']} {c.get('name', '')}: supports no stage and has no unsupported_reason")
    supported = {sid for stages in table.values() for sid in stages}
    bad += [f"{sid}: supported by no first-level capability" for sid in stage_refs if sid not in supported]
    return bad


def check_j5_1(d):
    return [f"{k} (line {v['line']}): class {v['cls'][:40]!r} does not start with A, B or C" for k, v in d.invs.items() if not re.match(r"^\**\s*[ABC](?![A-Za-z])", v["cls"])]


def check_j5_2(d):
    known = {a["id"] for a in d.aggs}
    bad = []
    for k, v in d.invs.items():
        problems = []
        aggs = expand_ids("AGG", v["agg"], problems)
        bad += [f"{k}: {p}" for p in problems]
        bad += [f"{k}: {a} is not in section 2.2" for a in sorted(aggs) if a not in known]
        if re.fullmatch(r"\**A\**\s*(（[^）]*）)?", v["cls"]) and len(aggs) != 1:
            bad.append(f"{k}: class A names {len(aggs)} aggregates {sorted(aggs)} (exactly one expected)")
    return bad


def check_j5_2r(d):
    """Report only: a class that combines A with another (`A + A（前置）`) and names several aggregates; the rule asks for exactly one only of a plain A."""
    return [f"{k}: class {v['cls'][:30]!r} names {len(expand_ids('AGG', v['agg']))} aggregates" for k, v in d.invs.items()
            if re.match(r"^\**\s*A", v["cls"]) and not re.fullmatch(r"\**A\**\s*(（[^）]*）)?", v["cls"]) and len(expand_ids("AGG", v["agg"])) > 1]


def check_j5_3(d):
    return [f"{k} (line {v['line']}): class C without an ADR reference" for k, v in d.invs.items() if re.match(r"^\**\s*C", v["cls"]) and not re.search(r"ADR-\d{4}", v["raw"])]


def referenced_invs(d, problems=None):
    out = set()
    for a in d.aggs:
        out |= expand_ids("INV", a["inv"], problems)
    return out


def check_j5_4(d):
    problems = []
    ref = referenced_invs(d, problems)
    return problems + [f"{i}: referenced by an aggregate but not in section 1.2" for i in sorted(ref - set(d.invs))] + [f"{i}: in section 1.2 but referenced by no aggregate" for i in sorted(set(d.invs) - ref)]


def check_j6_1(d):
    return [f"{a['id']} (line {a['line']}): mapping {a['map'][:30]!r}" for a in d.aggs if not a["map"].startswith(MAPPINGS)]


def objs_in(text):
    return set(re.findall(r"OBJ-\d+", text))


def check_j6_2(d):
    mentioned = set()
    for a in d.aggs:
        mentioned |= objs_in(a["root"] + a["inner"] + a["map"])
    return [f"{o} {r['name']}: in no aggregate row and not marked 无聚合" for o, r in d.im.items() if "非对象" not in r["level"] and o not in mentioned]


def check_j6_3(d):
    rows_of, root_rows = {}, {}
    for a in d.aggs:
        for o in objs_in(a["root"] + a["inner"]):
            rows_of.setdefault(o, set()).add(a["id"])
        for o in objs_in(a["root"]):
            root_rows.setdefault(o, []).append(a)
    bad = []
    for a in d.aggs:
        if a["map"].startswith("一拆多"):
            for o in objs_in(a["root"]):
                if len(rows_of.get(o, ())) < 2:
                    bad.append(f"{a['id']}: {o} is 一拆多 but appears in {len(rows_of.get(o, ()))} aggregate row")
    for o, rows in root_rows.items():
        if len(rows) > 1:
            for a in rows:
                if not a["map"].startswith("一拆多"):
                    bad.append(f"{a['id']}: {o} is the root of {len(rows)} aggregates but this row says {a['map'][:20]!r}")
    return bad


def check_j6_4(d):
    # "claims an IM object as its root" = the parenthesis right after the name starts with OBJ-n; a note that says an object was dropped does not
    return [f"{a['id']} (line {a['line']}): a technical aggregate names {sorted(objs_in(a['root']))} as its root" for a in d.aggs
            if a["map"].startswith(("技术聚合", "运行单据")) and re.match(r"^[^（]*（\s*OBJ-\d+", a["root"])]


def dep_module(d):
    if not hasattr(d, "_dep"):
        d._dep = load_module("aggregates_dep_check_j", d.folder / "aggregates_dep_check.py", [str(d.folder / "ENTERPRISE_AGGREGATES_DRAFT.md"), str(d.built)])
    return d._dep


def check_j7_1(d):
    run = subprocess.run([sys.executable, "-I", str(d.folder / "aggregates_dep_check.py"), str(d.folder / "ENTERPRISE_AGGREGATES_DRAFT.md"), str(d.built)], capture_output=True, text=True)
    out = run.stdout
    bad = [] if run.returncode == 0 else [f"aggregates_dep_check.py exit {run.returncode}: " + "; ".join(l for l in out.splitlines() if l.startswith("错误") or l.startswith("环")) ]
    if "§4.1 分层块与生成结果：一致" not in out:
        bad.append("section 4.1 layer block does not equal the generated layers")
    return bad


def registered_modules(d, modules):
    names = {m for m in modules.values() if m}
    path = d.built
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            line = line.split("#", 1)[0]
            if "->" in line:
                left, right = line.split("->", 1)
                names |= {left.strip()} | {x.strip() for x in right.split(",") if x.strip()}
    return names


def check_j7_2(d):
    if not d.built.exists():
        return [f"module-dependencies.txt not found ({d.built}): registered modules cannot be known"]
    dep = dep_module(d)
    owner, deps, counted, modules = dep.parse(d.ag_text)
    registered = registered_modules(d, modules)
    bcs = set(owner.values())
    bad = []
    for agg, (no, cell) in deps.items():
        if cell in ("—", "-", ""):
            continue
        for seg in [s.strip() for s in cell.split("；") if s.strip()]:
            t, mm = dep.TARGET.match(seg), dep.MODE.search(seg)
            if not t or not mm:
                bad.append(f"{agg} (line {no}): cannot parse {seg[:40]!r}")
                continue
            modes = {x.strip() for x in mm.group(1).split("、")}
            if not modes <= MODES:
                bad.append(f"{agg} (line {no}): mode {sorted(modes - MODES)} in {seg[:40]!r}")
            target = t.group(1)
            known = (target in owner) if target.startswith(("AGG-", "REF-")) else (target in bcs) if target.startswith("B-") else (target in registered)
            if not known:
                bad.append(f"{agg} (line {no}): target {target} is not an existing aggregate, BC or registered module")
    return bad


def layers_of(text):
    block = text.split("<!-- dep-layers:begin -->", 1)[-1].split("<!-- dep-layers:end -->", 1)[0]
    layer, by_id = {}, {}
    for line in block.splitlines():
        m = re.match(r"^L(\d+):\s*(.*)$", line.strip())
        if m:
            for item in [x.strip() for x in m.group(2).split("、") if x.strip()]:
                key = item.split(" ")[0]
                layer[key] = int(m.group(1))
                for tok in item.split(" ")[1:]:
                    if re.fullmatch(r"[a-z]+\.[a-z]+", tok):
                        layer[tok] = int(m.group(1))
                by_id[key] = item
    return layer


def check_j7_3(d):
    dep = dep_module(d)
    dep.errors.clear()
    owner, deps, counted, modules = dep.parse(d.ag_text)
    edges, _ = dep.edges_from(owner, deps)
    layer = layers_of(d.ag_text)
    module_to_bc = {m: b for b, m in modules.items() if m}
    bad = []
    for (src, tgt), modes in sorted(edges.items()):
        if not modes & {"api", "事件"}:
            continue
        tgt = module_to_bc.get(tgt, tgt)
        if src not in layer or tgt not in layer:
            bad.append(f"{src} -> {tgt}: a node is missing from the section 4.1 layers")
        elif layer[src] <= layer[tgt]:
            bad.append(f"{src} (L{layer[src]}) -> {tgt} (L{layer[tgt]}): a reference runs upstream or sideways, against the 4.1 skeleton")
    return bad


def check_j7_4(d):
    dep = dep_module(d)
    bc_of = {a["id"]: a["bc"] for a in d.aggs}
    bad = []
    for a in d.aggs:
        if a["bc"] != "B-28" or "OBJ-53" not in a["root"]:
            continue
        for seg in [x.strip() for x in a["ref"].split("；") if x.strip()]:
            t, mm = dep.TARGET.match(seg), dep.MODE.search(seg)
            if not t or not mm or not ({x.strip() for x in mm.group(1).split("、")} & {"api", "事件"}):
                continue
            target = t.group(1)
            owner_bc = bc_of.get(target, target)
            if owner_bc != a["bc"]:
                bad.append(f"{a['id']} (line {a['line']}): evidence root OBJ-53 references {target} ({owner_bc}) by api / 事件: {seg[:50]!r}")
    return bad


def check_j7_5(d):
    bad = []
    seen = {}
    for a in d.aggs:
        seen.setdefault(a["id"], set()).add(a["bc"])
        if a["bc"] is None:
            bad.append(f"{a['id']}: outside any B-xx heading")
    bad += [f"{i}: listed {len([a for a in d.aggs if a['id'] == i])} times (BCs {sorted(str(b) for b in b_set)})" for i, b_set in seen.items() if len([a for a in d.aggs if a['id'] == i]) > 1]
    counted = len({a["id"] for a in d.aggs if a["id"].startswith("AGG-") and "（不计" not in a["raw"]})
    section = d.ag_text.split("### 0.1", 1)[-1].split("### 0.2", 1)[0]
    stated = re.findall(r"(\d+)\s*个\s*(聚合|BC)", section)
    if not any(what == "聚合" for _, what in stated):
        bad.append("section 0.1 states no aggregate count")
    for number, what in stated:
        actual = counted if what == "聚合" else len({a["bc"] for a in d.aggs if a["bc"]})
        if int(number) != actual:
            bad.append(f"section 0.1 says {number} {what}, section 2.2 has {actual}")
    return bad


CHECKS = [("J1.1", check_j1_1, True), ("J1.2", check_j1_2, True), ("J1.3", check_j1_3, True), ("J1.4", check_j1_4, True),
          ("J2.1", check_j2_1, True), ("J2.2", check_j2_2, True), ("J2.3", check_j2_3, True), ("J2.4", check_j2_4, True), ("J2.4r", check_j2_4r, False), ("J2.5", check_j2_5, True),
          ("J3.1", check_j3_1, True), ("J3.2", check_j3_2, False),
          ("J4.1", check_j4_1, True), ("J4.2", check_j4_2, True), ("J4.3", check_j4_3, True), ("J4.4", check_j4_4, True),
          ("J5.1", check_j5_1, True), ("J5.2", check_j5_2, True), ("J5.2r", check_j5_2r, False), ("J5.3", check_j5_3, True), ("J5.4", check_j5_4, True),
          ("J6.1", check_j6_1, True), ("J6.2", check_j6_2, True), ("J6.3", check_j6_3, True), ("J6.4", check_j6_4, True),
          ("J7.1", check_j7_1, True), ("J7.2", check_j7_2, True), ("J7.3", check_j7_3, True), ("J7.4", check_j7_4, True), ("J7.5", check_j7_5, True)]


def run_all(folder):
    d = Data(folder)
    results = []
    for cid, fn, must in CHECKS:
        try:
            found = fn(d)
        except Exception as error:  # a check that cannot run is a failure, never a pass
            found, must = [f"the check could not run: {error.__class__.__name__}: {error}"], True
        status = ("PASS" if not found else "FAIL") if must else ("PASS" if not found else "REPORT")
        results.append((cid, status, found))
    return d, results


def counts(d):
    stages = len(d.stages)
    return (f"value streams {len(d.streams)}, stages {stages}, IM objects {len(d.im)} (first-level {sum(r['level'].startswith('一级') for r in d.im.values())}), "
            f"first-level capabilities {len(d.cap['capabilities'])}, aggregates {len({a['id'] for a in d.aggs if a['id'].startswith('AGG-')})}, "
            f"BCs {len({a['bc'] for a in d.aggs if a['bc']})}, INV {len(d.invs)}")


def report(folder):
    d, results = run_all(folder)
    for cid, status, found in results:
        print(f"{cid}\t{status}\t{len(found)} finding(s)" if found else f"{cid}\t{status}")
        for line in found:
            print(f"      {line}")
    table, _ = support_table(d)
    print("capability x stage (first-level capability: stages it supports):")
    for cap in d.cap["capabilities"]:
        reason = cap.get("unsupported_reason")
        print(f"  {cap['id']} {cap.get('name', '')}: " + (", ".join(table[cap["id"]]) or ("(none; unsupported_reason: " + str(reason)[:40] + ")" if reason else "(none)")))
    print("counts: " + counts(d))
    failed = [cid for cid, status, _ in results if status == "FAIL"]
    print(f"{len(results) - len(failed)} of {len(results)} checks without failure; failed: {', '.join(failed) or 'none'}")
    return 1 if failed else 0


# ---------------------------------------------------------------------------------------------- self-test

BASE_IM = """| 编号 | 对象 | 定义 | 一级 / 次级 | 类型 | 关键状态 | 主要关系与基数 |
|---|---|---|---|---|---|---|
| OBJ-01 | 往来单位 | d | 一级（不依附） | t | 新建 → 生效（说明（嵌套））；另有备注。销项蓝字：已开具 | r |
| OBJ-02 | 合同 | d | 一级（不依附） | t | 草稿 / 生效 | r |
| OBJ-03 | 联系人 | d | 次级（往来单位） | t | 有效 | r |
| OBJ-04 | 说明 | d | 非对象 | t | — | r |
"""

BASE_AG = """# Aggregates
### 0.1 本稿做哪一步
- 地位：§2.2 的 2 个聚合、2 个 BC
### 0.2 输入
### 1.2 不变量清单
| 编号 | 名称 | 类 | 聚合 | 对象 |
|---|---|---|---|---|
| INV-01 | 往来单位名称唯一 | A | AGG-01 | OBJ-01 |
| INV-01a | 往来单位名称唯一（分表） | A | AGG-01 | OBJ-01 |
| INV-02 | 合同金额非负 | B（排他约束） | AGG-02 | OBJ-02 |
| INV-03 | 跨聚合额度 | C | AGG-01、AGG-02 ADR-0010 | OBJ-01 |
### 2.2 聚合清单
**B-01 往来**

| # | 聚合（根） | 内含 | 对应 | 不变量 | 对外事件（订阅方） | 引用与依赖方式 |
|---|---|---|---|---|---|---|
| AGG-01 | 往来单位（OBJ-01） | 联系人（OBJ-03） | 次级在内 | INV-01、03、01a | 已建 | — |

**B-02 合同**

| # | 聚合（根） | 内含 | 对应 | 不变量 | 对外事件（订阅方） | 引用与依赖方式 |
|---|---|---|---|---|---|---|
| AGG-02 | 合同（OBJ-02） | 条款 | 一对一 | INV-02 | 已签 | AGG-01 往来单位（api） |
**派生值清单**（回溯检查）：
- OBJ-03｜有效 / 失效｜B-01 读模型计算
### 3.2 候选 BC 清单
| BC | 名称 | 模块 |
|---|---|---|
| B-01 | 往来 | 暂无 |
| B-02 | 合同 | 暂无 |
### 4.1 依赖骨架
<!-- dep-layers:begin -->
```
L0: B-01 往来
L1: B-02 合同
```
<!-- dep-layers:end -->
"""

BASE_VS = {
    "stakeholders": [{"id": "SH-C1"}, {"id": "SH-S1"}, {"id": "SH-R9"}],
    "value_streams": [{
        "id": "VS-01", "triggering_stakeholders": ["SH-C1"], "value_receivers": ["SH-R9"],
        "stages": [{"id": "VS-01.1", "participants": ["SH-S1"], "entry": {"any": [{"ref": "OBJ-01", "state": "新建"}]}, "exit": {"all": [{"ref": "OBJ-02", "state": "生效"}]}}],
    }],
    "state_caveats": [], "out_of_scope": [],
}
BASE_CAP = {"capabilities": [{"id": "CAP-001", "object": "OBJ-01", "l2": [{"id": "CAP-001.001", "object": "OBJ-03"}]}, {"id": "CAP-002", "object": "OBJ-02"}]}
STAGE_TYPES = "阶段类型目录：启动、概念、方案、初设、深化、招采、施工、结算、检查策划、现场检查、发现发布、整改跟踪、报告认可\n"


def write_fixture(folder, vs=None, cap=None, im=BASE_IM, ag=BASE_AG):
    Path(folder, "value-streams.yaml").write_text(yaml.safe_dump(vs or BASE_VS, allow_unicode=True), encoding="utf-8")
    Path(folder, "capabilities.v2.yaml").write_text(yaml.safe_dump(cap or BASE_CAP, allow_unicode=True), encoding="utf-8")
    Path(folder, "ENTERPRISE_INFO_MAP.md").write_text(im, encoding="utf-8")
    Path(folder, "ENTERPRISE_AGGREGATES_DRAFT.md").write_text(ag, encoding="utf-8")
    Path(folder, "STAGE_TYPES_DRAFT.md").write_text(STAGE_TYPES, encoding="utf-8")
    Path(folder, "module-dependencies.txt").write_text("", encoding="utf-8")
    for name in ("vs_links_gen.py", "aggregates_dep_check.py"):
        Path(folder, name).write_text((HERE / name).read_text(encoding="utf-8"), encoding="utf-8")


def mutate(fn):
    import copy
    vs, cap = copy.deepcopy(BASE_VS), copy.deepcopy(BASE_CAP)
    im, ag = BASE_IM, BASE_AG
    out = fn(vs, cap, im, ag)
    return out if isinstance(out, tuple) and len(out) == 4 else (vs, cap, im, ag)


def self_test():
    with tempfile.TemporaryDirectory() as folder:
        write_fixture(folder)
        _, results = run_all(folder)
        assert all(status in ("PASS",) for _, status, _ in results), [(c, f) for c, s, f in results if s != "PASS"]
    cases = {
        "J1.1": lambda vs, cap, im, ag: vs["value_streams"][0].update(triggering_stakeholders=["SH-ZZ"]),
        "J1.2": lambda vs, cap, im, ag: vs["value_streams"][0]["stages"][0].update(id="VS-02.1"),
        "J1.3": lambda vs, cap, im, ag: vs["value_streams"][0]["stages"][0].update(exit={"all": [{"ref": "VS-01.1", "state": "生效"}]}),
        "J1.4": lambda vs, cap, im, ag: vs["stakeholders"].append({"id": "SH-X9"}),
        "J2.1": lambda vs, cap, im, ag: vs["value_streams"][0]["stages"][0].update(entry={"any": []}),
        "J2.2": lambda vs, cap, im, ag: vs["value_streams"][0]["stages"][0].update(entry={"any": [{"ref": "OBJ-04", "state": "新建"}]}),
        "J2.3": lambda vs, cap, im, ag: vs["value_streams"][0]["stages"][0].update(entry={"any": [{"ref": "OBJ-01", "state": "不存在的状态"}]}),
        "J2.4": lambda vs, cap, im, ag: vs["value_streams"][0]["stages"][0].update(entry={"any": [{"ref": "OBJ-01", "state": "新建", "derived": True}]}),
        "J2.5": lambda vs, cap, im, ag: vs["value_streams"][0]["stages"][0].update(gate_mode="block"),
        "J3.1": lambda vs, cap, im, ag: (vs, cap, im, ag.replace("| AGG-02 | 合同（OBJ-02）", "| AGG-02 | 合同阶段（OBJ-02）")),
        "J4.1": lambda vs, cap, im, ag: cap["capabilities"][0].update(bc="B-01"),
        "J4.2": lambda vs, cap, im, ag: cap["capabilities"][0]["l2"][0].update(object="OBJ-99"),
        "J4.3": lambda vs, cap, im, ag: cap["capabilities"].append({"id": "CAP-003", "object": "OBJ-01"}),
        "J4.4": lambda vs, cap, im, ag: cap["capabilities"].append({"id": "CAP-004", "object": "OBJ-03"}),
        "J5.1": lambda vs, cap, im, ag: (vs, cap, im, ag.replace("| 往来单位名称唯一 | A |", "| 往来单位名称唯一 | 前置检查 |")),
        "J5.2": lambda vs, cap, im, ag: (vs, cap, im, ag.replace("| INV-01 | 往来单位名称唯一 | A | AGG-01 |", "| INV-01 | 往来单位名称唯一 | A | AGG-01、AGG-02 |")),
        "J5.3": lambda vs, cap, im, ag: (vs, cap, im, ag.replace(" ADR-0010", "")),
        "J5.4": lambda vs, cap, im, ag: (vs, cap, im, ag.replace("| 次级在内 | INV-01、03、01a |", "| 次级在内 | INV-01、01a |")),
        "J6.1": lambda vs, cap, im, ag: (vs, cap, im, ag.replace("| 一对一 |", "| 待定 |")),
        "J6.2": lambda vs, cap, im, ag: (vs, cap, im + "| OBJ-05 | 孤立 | d | 一级（不依附） | t | 有 | r |\n", ag),
        "J6.3": lambda vs, cap, im, ag: (vs, cap, im, ag.replace("| 一对一 |", "| 一拆多 |")),
        "J6.4": lambda vs, cap, im, ag: (vs, cap, im, ag.replace("| 合同（OBJ-02） | 条款 | 一对一 |", "| 合同（OBJ-02） | 条款 | 技术聚合 |")),
        "J7.1": lambda vs, cap, im, ag: (vs, cap, im, ag.replace("L0: B-01 往来\nL1: B-02 合同", "L0: B-02 合同\nL1: B-01 往来")),
        "J7.2": lambda vs, cap, im, ag: (vs, cap, im, ag.replace("AGG-01 往来单位（api）", "AGG-77 往来单位（api）")),
        "J7.3": lambda vs, cap, im, ag: (vs, cap, im, ag.replace("| AGG-01 | 往来单位（OBJ-01） | 联系人（OBJ-03） | 次级在内 | INV-01、03、01a | 已建 | — |", "| AGG-01 | 往来单位（OBJ-01） | 联系人（OBJ-03） | 次级在内 | INV-01、03、01a | 已建 | AGG-02 合同（事件） |")),
        "J7.4": lambda vs, cap, im, ag: (vs, cap, im, ag.replace("**B-02 合同**", "**B-28 合同**").replace("| 合同（OBJ-02） | 条款 | 一对一 | INV-02 | 已签 | AGG-01 往来单位（api） |", "| 证据（OBJ-53） | 条款 | 一对一 | INV-02 | 已签 | AGG-01 往来单位（api） |")),
        "J7.5": lambda vs, cap, im, ag: (vs, cap, im, ag.replace("§2.2 的 2 个聚合", "§2.2 的 9 个聚合")),
    }
    extra_failures = {
        "J2.4": lambda vs, cap, im, ag: vs["value_streams"][0]["stages"][0].update(exit={"all": [{"ref": "OBJ-03", "state": "有效"}]}),
        "J1.3": lambda vs, cap, im, ag: vs["value_streams"][0]["stages"][0].update(exit={"all": [{"stage": "VS-01.1"}]}),
        "J1.4": lambda vs, cap, im, ag: (vs["stakeholders"].append({"id": "SH-S10"}), vs["value_streams"][0]["stages"][0].update(participants=[]), vs.update(out_of_scope=[{"item": "x", "reason": "SH-S10 outside"}])),
        "J2.3": lambda vs, cap, im, ag: vs["value_streams"][0]["stages"][0].update(entry={"any": [{"ref": "OBJ-01"}]}),
        "J2.5": lambda vs, cap, im, ag: (vs["value_streams"][0].update(stage_pattern={"p": [{"ref": "OBJ-01", "state": "新建", "blocking": True}]}), vs["value_streams"][0]["stages"][0].update(entry={"pattern": "p"})),
        "J5.2": lambda vs, cap, im, ag: (vs, cap, im, ag.replace("| INV-01 | 往来单位名称唯一 | A | AGG-01 |", "| INV-01 | 往来单位名称唯一 | A | AGG-01、02 |")),
        "J5.4": lambda vs, cap, im, ag: (vs, cap, im, ag.replace("INV-01、03、01a", "INV-01、03")),
        "J7.5": lambda vs, cap, im, ag: (vs, cap, im, ag.replace("- 地位：§2.2 的 2 个聚合、2 个 BC", "- 地位：本稿不写数")),
    }
    for cid, fn in extra_failures.items():
        vs, cap, im, ag = mutate(fn)
        with tempfile.TemporaryDirectory() as folder:
            write_fixture(folder, vs, cap, im, ag)
            _, results = run_all(folder)
        assert {c: s for c, s, _ in results}[cid] == "FAIL", f"{cid}: the gap found in review must fail"
    problems = []
    assert expand_ids("AGG", "AGG-01 — 2 个") == {"AGG-01"} and expand_ids("INV", "INV-12，3 条") == {"INV-12"}, "a counted number after an id is no id"
    assert expand_ids("AGG", "AGG-110、12") == {"AGG-110", "AGG-12"} and expand_ids("INV", "INV-95–98") == {f"INV-{n}" for n in range(95, 99)}, "own digits, ranges"
    assert expand_ids("INV", "INV-16a、03b") == {"INV-16a", "INV-03b"} and expand_ids("INV", "INV-05–09、12") >= {"INV-07", "INV-12"}, "letter suffix, range then list"
    expand_ids("INV", "INV-09–05", problems)
    assert problems, "a descending range is reported"
    for cid, fn in {
        "J2.4": lambda vs, cap, im, ag: (vs, cap, im, ag.replace("- OBJ-03｜有效 / 失效｜B-01 读模型计算", "- OBJ-03 | 有效 / 失效 | B-01 读模型计算")),
        "J4.4": lambda vs, cap, im, ag: (vs["value_streams"][0]["stages"].append({"id": "VS-01.2", "entry": {"any": [{"ref": "OBJ-05", "state": "可用"}]}, "exit": {"any": [{"ref": "OBJ-05", "state": "可用"}]}}), (vs, cap, im + "| OBJ-05 | 附件 | d | 次级（合同） | t | 可用 | r |\n", ag.replace("条款 | 一对一", "条款、附件（OBJ-05） | 一对一")))[1],
    }.items():
        vs, cap, im, ag = mutate(fn)
        with tempfile.TemporaryDirectory() as folder:
            write_fixture(folder, vs, cap, im, ag)
            _, results = run_all(folder)
        assert {c: s for c, s, _ in results}[cid] == "FAIL", f"{cid}: gap found in the final review must fail"
    with tempfile.TemporaryDirectory() as folder:
        write_fixture(folder, ag=BASE_AG.replace("**派生值清单**（回溯检查）：\n- OBJ-03｜有效 / 失效｜B-01 读模型计算\n", ""))
        _, results = run_all(folder)
        assert {c: s for c, s, _ in results}["J2.4"] == "FAIL", "no derived-value list at all is a failure, not a pass"
    with tempfile.TemporaryDirectory() as folder:
        try:
            run_all(folder)
        except SystemExit as stop:
            assert "missing input" in str(stop)
        else:
            raise AssertionError("a missing input must stop the run with a clear message")
    with tempfile.TemporaryDirectory() as folder:
        write_fixture(folder, ag=BASE_AG.replace("| INV-03 | 跨聚合额度 | C | AGG-01、AGG-02 ADR-0010 |", "| INV-03 | 跨聚合额度 | A + A（前置） | AGG-01、AGG-02 ADR-0010 |"))
        _, results = run_all(folder)
        assert {c: s for c, s, _ in results}["J5.2r"] == "REPORT", "a combined A class naming two aggregates is reported"
    with tempfile.TemporaryDirectory() as folder:
        vs = __import__("copy").deepcopy(BASE_VS)
        vs["value_streams"][0]["stages"][0]["in_stage_states"] = [{"ref": "OBJ-03", "state": "有效"}]
        write_fixture(folder, vs=vs)
        _, results = run_all(folder)
        assert {c: s for c, s, _ in results}["J2.4r"] == "REPORT", "a derived value used inside a stage is reported, not failed"
    with tempfile.TemporaryDirectory() as folder:
        ag = BASE_AG.replace("AGG-02", "AGG-04").replace("| 合同（OBJ-02） |", "| 阶段类型版本（OBJ-02） |")
        write_fixture(folder, ag=ag)
        _, results = run_all(folder)
        assert {c: s for c, s, _ in results}["J3.1"] == "PASS", "AGG-04 (stage type catalogue version) is on the J3.1 whitelist"
    with tempfile.TemporaryDirectory() as folder:
        ag = BASE_AG.replace("| 合同（OBJ-02） | 条款 | 一对一 | INV-02 |", "| 合同（OBJ-02） | 条款 | 一对一 | — |").replace("INV-01、03、01a", "INV-01–03、01a")
        write_fixture(folder, ag=ag)
        _, results = run_all(folder)
        assert {c: s for c, s, _ in results}["J5.4"] == "PASS", "INV-02 is named only by the range INV-01–03"
    for note, fn, ids in (
            ("a capability supported only by an in-stage state or a cross-stage activity", lambda vs, cap, im, ag: (cap["capabilities"].append({"id": "CAP-005", "object": "OBJ-03"}), vs["value_streams"][0]["stages"][0].update(in_stage_states=[{"ref": "OBJ-03", "state": "有效"}])), ("J4.4",)),
            ("an activity input in the stream", lambda vs, cap, im, ag: (cap["capabilities"].append({"id": "CAP-005", "object": "OBJ-03"}), vs["value_streams"][0].update(cross_stage_activities=[{"name": "a", "inputs": [{"ref": "OBJ-03", "state": "有效"}], "outputs": []}])), ("J4.4",)),("range and shorthand in the INV column", lambda vs, cap, im, ag: (vs, cap, im, ag.replace("INV-01、03、01a", "INV-01–03、01a")), ("J5.4",)),
                          ("an IM state behind a prefix, 。 and nested parentheses", lambda vs, cap, im, ag: vs["value_streams"][0]["stages"][0].update(entry={"any": [{"ref": "OBJ-01", "state": "已开具"}]}), ("J2.3",))):
        vs, cap, im, ag = mutate(fn)
        with tempfile.TemporaryDirectory() as folder:
            write_fixture(folder, vs, cap, im, ag)
            _, results = run_all(folder)
        status = {c: s for c, s, _ in results}
        assert all(status[c] == "PASS" for c in ids), f"{note}: must pass: {[(c, status[c]) for c in ids]}"
    for cid, fn in cases.items():
        vs, cap, im, ag = mutate(fn)
        with tempfile.TemporaryDirectory() as folder:
            write_fixture(folder, vs, cap, im, ag)
            _, results = run_all(folder)
        status = {c: s for c, s, _ in results}
        assert status[cid] == "FAIL", f"{cid} must fail on its broken input; got {status[cid]}"
    with tempfile.TemporaryDirectory() as folder:
        write_fixture(folder, ag=BASE_AG.replace("| AGG-02 | 合同（OBJ-02） | 条款 | 一对一 | INV-02 | 已签 |", "| AGG-02 | 合同（OBJ-02） | 状态：启动、概念 | 一对一 | INV-02 | 已签 |"))
        _, results = run_all(folder)
        assert {c: s for c, s, _ in results}["J3.2"] == "REPORT", "J3.2 reports a stage name next to 状态 and never fails"
        assert all(s != "FAIL" for c, s, _ in results if c == "J3.2")
    with tempfile.TemporaryDirectory() as folder:
        write_fixture(folder, ag=BASE_AG.replace("| AGG-02 | 合同（OBJ-02） |", "| AGG-02 | 合同（OBJ-02；阶段门走查补充） |").replace("| 条款 | 一对一 |", "| 条款 | 技术聚合 |").replace("合同（OBJ-02；阶段门走查补充）", "运行（运行单据；OBJ-02 已去掉；阶段门走查）"))
        _, results = run_all(folder)
        status = {c: s for c, s, _ in results}
        assert status["J3.1"] == "PASS" and status["J6.4"] == "PASS", "a stage word or a dropped-object note in the root note is neither J3.1 nor J6.4"
    with tempfile.TemporaryDirectory() as folder:
        write_fixture(folder, ag=BASE_AG.replace("**B-02 合同**", "**B-28 合同**").replace("| 合同（OBJ-02） | 条款 | 一对一 | INV-02 | 已签 | AGG-01 往来单位（api） |", "| 证据（OBJ-53） | 条款 | 一对一 | INV-02 | 已签 | AGG-01 往来单位（不校验） |"))
        _, results = run_all(folder)
        assert {c: s for c, s, _ in results}["J7.4"] == "PASS", "an evidence root may reference another BC with 不校验"
    with tempfile.TemporaryDirectory() as folder:
        vs = __import__("copy").deepcopy(BASE_VS)
        vs["value_streams"][0]["stages"][0]["entry"] = {"any": [{"ref": "OBJ-01", "state": "不存在的状态"}]}
        vs["state_caveats"] = [{"id": "SC-01", "ref": "OBJ-01", "issue": "x", "fix": "y"}]
        write_fixture(folder, vs=vs)
        _, results = run_all(folder)
        assert {c: s for c, s, _ in results}["J2.3"] == "PASS", "a state_caveats entry for the ref excuses J2.3"
        cap = __import__("copy").deepcopy(BASE_CAP)
        cap["capabilities"].append({"id": "CAP-004", "object": "OBJ-03", "unsupported_reason": "no stage uses it yet"})
        write_fixture(folder, cap=cap)
        _, results = run_all(folder)
        assert {c: s for c, s, _ in results}["J4.4"] == "PASS", "unsupported_reason excuses a capability without a stage"
    print("j_checks self-test: ok")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        sys.exit(0)
    sys.exit(report(sys.argv[1] if len(sys.argv) > 1 else HERE))
