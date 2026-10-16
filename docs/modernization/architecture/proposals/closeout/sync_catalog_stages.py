"""Copy stages / support / stages_note from the three group skeletons into process-catalog.yaml (the catalog is the SSOT for stage placement).

Usage: python3 -I sync_catalog_stages.py [--local] [--dry-run]   |   --self-test
  reads processes.<group>.yaml like closeout_check.py (origin/claude/one-arch-<group>-closeout, or closeout/<group>/ with --local);
  only processes owned by that group in the catalog are synced; prints every change; exit 1 if a skeleton process is missing from the catalog.
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
CATALOG = HERE / "process-catalog.yaml"
FIELDS = ("support", "stages_note")


def skeleton_units(doc):
    out = {}
    for _k, u in cc.units_of(doc):
        if isinstance(u, dict) and str(u.get("id", "")).startswith("PRC-"):
            out.setdefault(u["id"], u)
    return out


def quote(v):
    return '"' + str(v).replace('"', "'").replace("\n", " ").strip() + '"'


def patch_block(blk, unit):
    stages = [str(s) for s in unit.get("stages") or []]
    nb = re.sub(r"    stages: \[.*?\]", "    stages: [" + ", ".join(stages) + "]", blk, count=1)
    for f in FIELDS:
        nb = re.sub(r"\n    %s: \".*?\"" % f, "", nb)
    extra = "".join(f"\n    {f}: {quote(unit[f])}" for f in FIELDS if str(unit.get(f) or "").strip())
    return re.sub(r"(    stages: \[.*?\])", lambda m: m.group(1) + extra, nb, count=1)


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
    text, changes, missing = sync(text, units, owner_of)
    print(f"同步 {len(changes)} 条：" + "、".join(changes))
    if missing:
        print("骨架有、目录无：" + "、".join(missing))
    if "--dry-run" not in argv:
        CATALOG.write_text(text, encoding="utf-8")
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
