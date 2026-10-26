#!/usr/bin/env python3
"""ARC-1021-ARC-07：逐行保留对象总表与两套状态机；用 --check 只比对产物。"""
import argparse
import re
import sys
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[4]
ARCH = HERE.parent.parent
SRC = ARCH / "ssot/10-objects/ENTERPRISE_INFO_MAP.md"
FINAL = ARCH / "panorama/FREEZE_FINAL.yaml"
GROUPS = ("finance", "people", "revenue")
HEADER = ["编号", "对象", "定义", "一级 / 次级", "类型", "关键状态", "主要关系与基数", "规则", "产生的事实", "来源", "词表", "范围与阶段", "父对象"]
FIELDS = ("id", "name", "definition", "level", "type", "key_states", "relations", "rules", "facts", "source", "term", "scope", "parent_text")


def plain(text):
    text = re.sub(r"(`+)(.*?)\1", r"\2", text)
    return re.sub(r"(\*\*|__|~~)(.*?)\1", r"\2", text)


def parse_objects(text):
    lines = text.splitlines()
    start = next((i for i, line in enumerate(lines) if line.startswith("## 1. 总表")), None)
    if start is None:
        raise ValueError("缺少 §1 总表")
    objects, seen, group, groups, header = [], set(), None, 0, False
    for no in range(start + 1, len(lines)):
        line = lines[no]
        if line.startswith(("## ", "### ")):
            break
        if not line.startswith("|"):
            continue
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) != len(HEADER):
            raise ValueError(f"总表:{no + 1}: 列数应为 {len(HEADER)}，实际 {len(cells)}")
        if not header:
            if cells != HEADER:
                raise ValueError(f"总表:{no + 1}: 表头不符")
            header = True
            continue
        if all(re.fullmatch(r":?-+:?", c) for c in cells):
            continue
        raw = cells
        cells = [plain(c) for c in cells]
        if cells[0] and not any(cells[1:]) and raw[0].startswith("**"):
            group, groups = cells[0], groups + 1
            continue
        if not re.fullmatch(r"OBJ-\d{2,3}", cells[0]) or group is None:
            raise ValueError(f"总表:{no + 1}: 非法对象或缺分组：{cells[0]}")
        if cells[0] in seen:
            raise ValueError(f"总表:{no + 1}: OBJ 编号重复：{cells[0]}")
        seen.add(cells[0])
        row = dict(zip(FIELDS, cells))
        row.update(parent=re.findall(r"OBJ-\d{2,3}", cells[-1]), group=group, source_line=no + 1)
        objects.append(row)
    if not header or not objects:
        raise ValueError("总表未读到对象")
    return objects, groups


def aggregate_machines(paths):
    machines, counts = [], {}
    for group, path in paths.items():
        data = yaml.safe_load(path.read_text(encoding="utf-8"))
        rows = data.get("machines") if isinstance(data, dict) else None
        if not isinstance(rows, list) or any(not isinstance(row, dict) for row in rows):
            raise ValueError(f"{path}: machines 应为条目列表")
        counts[group] = len(rows)
        for index, row in enumerate(rows):
            if "group" in row or "source" in row:
                raise ValueError(f"{path}#machines[{index}]: group/source 字段冲突")
            machines.append({**row, "group": group, "source": f"{path.relative_to(ROOT)}#machines[{index}]"})
    return machines, counts


def documents():
    objects, groups = parse_objects(SRC.read_text(encoding="utf-8"))
    freeze = yaml.safe_load(FINAL.read_text(encoding="utf-8"))
    if not isinstance(freeze, dict) or any(not isinstance(value, dict) for value in freeze.values()):
        raise ValueError(f"{FINAL}: 冻结结论应为 OBJ 到结论的映射")
    for row in objects:
        if row["id"] in freeze:
            row["freeze"] = freeze[row["id"]]
    non_objects = sum("已非对象" in row["definition"] for row in objects)
    object_doc = {"meta": {"source": str(SRC.relative_to(ROOT)), "count": len(objects), "group_count": groups,
                           "valid_count": len(objects) - non_objects, "non_object_count": non_objects}, "objects": objects}
    by_object = [{"object": row["id"], "key_states_text": row["key_states"],
                  "source": f"{SRC.relative_to(ROOT)}:{row['source_line']}"} for row in objects if row["key_states"]]
    by_aggregate, counts = aggregate_machines({group: ARCH / f"proposals/closeout/{group}/state-machines.yaml" for group in GROUPS})
    state_doc = {"meta": {"by_object_count": len(by_object), "by_aggregate_count": len(by_aggregate), "group_counts": counts},
                 "by_object": by_object, "by_aggregate": by_aggregate}
    return {"objects.yaml": object_doc, "state-machines.raw.yaml": state_doc}


def render(data):
    return "# 由 convert_objects.py 生成，勿手改\n" + yaml.safe_dump(data, allow_unicode=True, sort_keys=False, width=200)


def generate(check=False):
    contents = {name: render(data).encode("utf-8") for name, data in documents().items()}
    if check:
        mismatches = [name for name, content in contents.items() if not (HERE / name).exists() or (HERE / name).read_bytes() != content]
        if mismatches:
            raise ValueError("产物与来源不一致，重跑 convert_objects.py：" + "、".join(mismatches))
    else:
        for name, content in contents.items():
            (HERE / name).write_bytes(content)
    for name, content in contents.items():
        print(f"{name}: {yaml.safe_load(content)['meta']}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    try:
        generate(args.check)
    except (OSError, ValueError, yaml.YAMLError) as error:
        sys.exit(str(error))


if __name__ == "__main__":
    main()
