"""Generate CATALOG.md from process-catalog.yaml; --check validates the catalog.

Usage: python3 -I build_catalog.py [--check]
Exit code 1 on errors, else 0. Warnings (business without bpmn or stages) do not fail.
"""
import collections
import re
import sys
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent
PROPOSALS = HERE.parent
CATALOG = HERE / "process-catalog.yaml"
OUT = HERE / "CATALOG.md"
TYPES = ["business", "event_policy", "single_aggregate", "read_model", "merged", "retired"]
STATUSES = ["draft", "sample", "reviewed", "user_confirmed"]
GROUPS = ["revenue", "finance", "people"]
ID_RE = re.compile(r"^PRC-(\d{2,3})$")
SPECIAL_OWNERS = {"reporting"}


def load():
    return yaml.safe_load(CATALOG.read_text(encoding="utf-8"))["processes"]


def valid_stages():
    vs = yaml.safe_load((PROPOSALS / "value-streams.v2.yaml").read_text(encoding="utf-8"))["value_streams"]
    return {s["id"] for v in vs for s in v["stages"]}


def valid_owners():
    text = (PROPOSALS / "ENTERPRISE_AGGREGATES_DRAFT.md").read_text(encoding="utf-8")
    sec = text.split("### 3.2 ", 1)[1].split("\n### ", 1)[0]
    owners = set(re.findall(r"^\|\s*(B-\d{2})\s*\|", sec, re.M))
    items = (HERE / "STRUCTURAL_ITEMS.md").read_text(encoding="utf-8")
    if "B-31" in items:
        owners.add("B-31")
    return owners


def check(rows):
    errors, warns = [], []
    stages, owners = valid_stages(), valid_owners()
    by_id = {}
    for r in rows:
        i = r.get("id", "")
        if not ID_RE.match(str(i)):
            errors.append(f"{i}: 编号格式应为 PRC-NN")
            continue
        if i in by_id:
            errors.append(f"{i}: 编号重复")
        by_id[i] = r
    nums = sorted(int(i[4:]) for i in by_id)
    if nums:
        for n in range(1, nums[-1] + 1):
            if n not in nums:
                errors.append(f"PRC-{n:02d}: 空号未登记为 retired")
    for i, r in by_id.items():
        t = r.get("type")
        if t not in TYPES:
            errors.append(f"{i}: type {t!r} 不合法")
        if r.get("status") not in STATUSES:
            errors.append(f"{i}: status {r.get('status')!r} 不合法")
        if r.get("group") not in GROUPS:
            errors.append(f"{i}: group {r.get('group')!r} 不合法")
        if not r.get("name"):
            errors.append(f"{i}: 缺 name")
        if t == "merged":
            tgt = by_id.get(r.get("merged_into"))
            if tgt is None:
                errors.append(f"{i}: merged_into {r.get('merged_into')!r} 不存在")
            elif tgt.get("type") == "merged":
                errors.append(f"{i}: merged_into {r['merged_into']} 自身也是 merged")
        elif t == "retired":
            if not r.get("note"):
                errors.append(f"{i}: retired 须写 note")
        else:
            o = r.get("owner_bc")
            if not (o in owners or o in SPECIAL_OWNERS or (isinstance(o, str) and o.startswith("platform."))):
                errors.append(f"{i}: owner_bc {o!r} 未登记")
        for s in r.get("stages") or []:
            if s not in stages:
                errors.append(f"{i}: stage {s} 不在 value-streams.v2.yaml")
        if t == "business":
            if r.get("support") and r.get("stages"):
                errors.append(f"{i}: 标了 support 却填了 stages")
            elif not r.get("stages") and not str(r.get("support") or "").strip():
                warns.append(f"{i}: business 未填 stages（不在价值流上的写 support: <理由>）")
            if not r.get("bpmn"):
                warns.append(f"{i}: business 未登记 bpmn（铺开后改为失败）")
    return errors, warns


def table(title, counter, keys, label):
    lines = [f"### {title}", "", f"| {label} | 数量 |", "|---|---|"]
    for k in keys:
        if counter.get(k):
            lines.append(f"| {k} | {counter[k]} |")
    lines.append(f"| 合计 | {sum(counter.values())} |")
    return lines + [""]


def render(rows):
    by_type = collections.Counter(r["type"] for r in rows)
    by_group = collections.Counter(r["group"] for r in rows)
    biz_group = collections.Counter(r["group"] for r in rows if r["type"] == "business")
    by_vs = collections.Counter()
    for r in rows:
        if r["type"] != "business":
            continue
        for v in sorted({s.split(".")[0] for s in r.get("stages") or []}):
            by_vs[v] += 1
    no_stage = sum(1 for r in rows if r["type"] == "business" and not r.get("stages") and not r.get("support"))
    by_vs["支撑（不在价值流上）"] = sum(1 for r in rows if r["type"] == "business" and r.get("support"))
    L = ["<!-- 由 build_catalog.py 生成，不手改 -->", "# 流程目录（生成，提议稿）", "",
         "由 build_catalog.py 生成，不手改；来源 `process-catalog.yaml`。", "",
         f"共 {len(rows)} 条，编号 PRC-01 至 PRC-{max(int(r['id'][4:]) for r in rows)}。", ""]
    L += table("按类型", by_type, TYPES, "类型")
    L += table("按组（全部类型）", by_group, GROUPS, "组")
    L += table("按组（仅业务流程）", biz_group, GROUPS, "组")
    L += table("按价值流（仅业务流程；跨价值流的流程各计一次，故合计大于业务流程数）", by_vs, sorted(by_vs), "价值流")
    L.append(f"业务流程中尚未填 stages：{no_stage} 条。\n")
    L += ["## 总表", "", "| 编号 | 名称 | 类型 | 并入 | 阶段 | owner_bc | 组 | 状态 | 人工编排 | bpmn |",
          "|---|---|---|---|---|---|---|---|---|---|"]
    for r in rows:
        ho = {True: "是", False: "否", None: "待定"}[r.get("human_orchestration")]
        L.append("| " + " | ".join([r["id"], str(r["name"]).replace("|", "/"), r["type"], r.get("merged_into") or "",
                                    ", ".join(r.get("stages") or []), r.get("owner_bc") or "", r["group"], r["status"],
                                    ho, r.get("bpmn") or ""]) + " |")
    return "\n".join(L) + "\n"


def main():
    rows = load()
    rows.sort(key=lambda r: int(str(r["id"])[4:]) if ID_RE.match(str(r["id"])) else 10**6)
    if "--check" in sys.argv:
        errors, warns = check(rows)
        for w in warns[:10]:
            print("警告", w)
        if len(warns) > 10:
            print(f"警告 另有 {len(warns) - 10} 条同类")
        for e in errors:
            print("错误", e)
        print(f"错误 {len(errors)}，警告 {len(warns)}")
        sys.exit(1 if errors else 0)
    OUT.write_text(render(rows), encoding="utf-8")
    print(f"已生成 {OUT.name}")


if __name__ == "__main__":
    main()
