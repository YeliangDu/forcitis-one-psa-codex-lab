"""Report drift of stages / supports / stages_note / primary_object(_note) / secondary_objects between ssot/09-processes/catalog.yaml (the SSOT) and the three group skeletons.

Usage: python3 -I sync_catalog_stages.py [--local] [--write-catalog]   |   --self-test
  reads processes.<group>.yaml like closeout_check.py (origin/claude/one-arch-<group>-closeout, or closeout/<group>/ with --local);
  default compares parsed YAML values only (not text format, order of keys, quotes), never writes; exit 1 on drift or a skeleton process missing from the catalog.
  retired / merged catalog processes are skipped. --write-catalog (discouraged) overwrites catalog blocks from the skeletons.
"""
import importlib.util
import re
import sys
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("closeout_check", HERE / "closeout_check.py")
cc = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(cc)
CATALOG = Path(__file__).resolve().parents[1].parent / "ssot" / "09-processes" / "catalog.yaml"   # 流程目录正本
FIELDS = ("support", "supports", "stages_note", "primary_object", "primary_object_note", "secondary_objects")


def skeleton_units(doc):
    out = {}
    for _k, u in cc.units_of(doc):
        if isinstance(u, dict) and str(u.get("id", "")).startswith("PRC-"):
            out.setdefault(u["id"], u)
    return out


def quote(v):
    if isinstance(v, list):
        return "[" + ", ".join(str(x) for x in v) + "]"
    return '"' + str(v).replace('"', "'").replace("\n", " ").strip() + '"'


def patch_block(blk, unit):
    stages = [str(s) for s in unit.get("stages") or []]
    nb = re.sub(r"    stages: \[.*?\]", "    stages: [" + ", ".join(stages) + "]", blk, count=1)
    for f in FIELDS:
        nb = re.sub(r"\n    %s: (\".*?\"|\[.*?\])" % f, "", nb)
    extra = "".join(f"\n    {f}: {quote(unit[f])}" for f in FIELDS if str(unit.get(f) or "").strip())
    return re.sub(r"(    stages: \[.*?\])", lambda m: m.group(1) + extra, nb, count=1)


def norm(v):
    if v is None:
        return []
    if isinstance(v, (list, tuple)):
        return [str(x).strip() for x in v]
    return [str(v).strip()] if str(v).strip() else []


def retired(p):
    return p.get("type") == "merged" or p.get("status") == "retired" or p.get("type") == "retired"


def drift(units_by_group, catalog):
    """Return (drifts, missing): drifts are 'group PRC field：catalog value ≠ skeleton value'."""
    by_id = {p["id"]: p for p in catalog}
    out, missing = [], []
    for g, units in units_by_group.items():
        for pid, u in sorted(units.items()):
            if "stages" not in u and not any(f in u for f in FIELDS):
                continue
            p = by_id.get(pid)
            if p is None:
                missing.append(f"{g} {pid}")
                continue
            if p.get("group") != g or retired(p):
                continue
            for f in ("stages",) + FIELDS:
                if norm(p.get(f)) != norm(u.get(f)):
                    out.append(f"{g} {pid} {f}：{norm(p.get(f))} ≠ {norm(u.get(f))}")
    return out, missing


def sync(text, units_by_group, owner_of):
    changes, missing = [], []
    for g, units in units_by_group.items():
        for pid, u in sorted(units.items()):
            if "stages" not in u and not any(f in u for f in FIELDS):
                continue
            m = re.search(r"  - id: %s\n(.*?)(?=\n  - id: |\Z)" % re.escape(pid), text, re.S)
            if not m:
                missing.append(f"{g} {pid}")
                continue
            if owner_of.get(pid) != g:
                continue
            nb = patch_block(m.group(0), u)
            if nb != m.group(0):
                text = text.replace(m.group(0), nb)
                changes.append(f"{g} {pid}")
    return text, changes, missing


def self_test():
    cat = '  - id: PRC-1\n    name: "甲"\n    stages: [VS-01.1]\n    support: "旧"\n    group: g\n  - id: PRC-2\n    name: "乙"\n    stages: []\n    group: h\n'
    units = {"g": {"PRC-1": {"id": "PRC-1", "stages": ["VS-01.1", "VS-01.3"], "stages_note": "跨段理由"}, "PRC-2": {"id": "PRC-2", "stages": ["VS-09.1"]},
                   "PRC-9": {"id": "PRC-9", "stages": []}}}
    out, ch, miss = sync(cat, units, {"PRC-1": "g", "PRC-2": "h"})
    checks = ["stages: [VS-01.1, VS-01.3]" in out, 'stages_note: "跨段理由"' in out, 'support: "旧"' not in out,
              "stages: []\n    group: h" in out, ch == ["g PRC-1"], miss == ["g PRC-9"]]
    c2 = [{"id": "PRC-1", "group": "g", "stages": ["VS-01.1"], "stages_note": "理由", "supports": ["VS-02.1"]},
          {"id": "PRC-2", "group": "g", "stages": [], "type": "merged"}, {"id": "PRC-3", "group": "g", "status": "retired", "stages": ["VS-1.1"]}]
    same = {"g": {"PRC-1": {"id": "PRC-1", "stages": ["VS-01.1"], "stages_note": " 理由 ", "supports": ["VS-02.1"], "primary_object": None}}}
    d0, m0 = drift(same, c2)
    d1, m1 = drift({"g": {"PRC-1": {"id": "PRC-1", "stages": ["VS-01.1"], "stages_note": "理由", "supports": []}}}, c2)
    d2, _ = drift({"g": {"PRC-2": {"id": "PRC-2", "stages": ["VS-9.1"]}, "PRC-3": {"id": "PRC-3", "stages": []}}}, c2)
    checks += [d0 == [] and m0 == [], len(d1) == 1 and "supports" in d1[0], d2 == []]
    print("自测", "通过" if all(checks) else f"失败 {checks}")
    return 0 if all(checks) else 1


def main(argv):
    if "--self-test" in argv:
        return self_test()
    local = "--local" in argv
    text = CATALOG.read_text(encoding="utf-8")
    owner_of = {p["id"]: p.get("group") for p in (yaml.safe_load(text).get("processes") or [])}
    units = {}
    for g in cc.GROUPS:
        t = cc.read_group_file(g, "proc", local)
        if t is None:
            print(f"取不到 processes.{g}.yaml")
            continue
        units[g] = skeleton_units(yaml.safe_load(t))
    if "--write-catalog" in argv:
        print("警告：--write-catalog 用骨架覆盖目录，与「目录为准」的裁定相反，不建议使用")
        text, changes, missing = sync(text, units, owner_of)
        print(f"同步 {len(changes)} 条：" + "、".join(changes))
        if missing:
            print("骨架有、目录无：" + "、".join(missing))
        CATALOG.write_text(text, encoding="utf-8")
        return 1 if missing else 0
    drifts, missing = drift(units, yaml.safe_load(text).get("processes") or [])
    for line in drifts:
        print(line)
    if missing:
        print("骨架有、目录无：" + "、".join(missing))
    print(f"漂移 {len(drifts)} 条（目录为准）")
    return 1 if drifts or missing else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
