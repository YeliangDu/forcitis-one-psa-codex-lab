"""Summarise org_needs and functional roles from RESPONSIBILITY_MATRIX.yaml into ORG_NEEDS.md (input to the position-system rework).

Usage: python3 -I org_needs_build.py   (reads RESPONSIBILITY_MATRIX.yaml next to this script, writes ORG_NEEDS.md)
"""
import collections
import sys
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent


def is_external(needs):
    return needs and all("外部" in str(n.get("department_hint", "")) for n in needs)


def main():
    src = HERE / "RESPONSIBILITY_MATRIX.yaml"
    if not src.exists():
        sys.exit("缺 RESPONSIBILITY_MATRIX.yaml，先运行 raci_build.py --write")
    m = yaml.safe_load(src.read_text(encoding="utf-8"))
    roles = m.get("roles") or {}
    a_by_role = collections.Counter()
    functions = collections.defaultdict(collections.Counter)
    for e in m.get("entries") or []:
        a = (e.get("raci") or {}).get("A") or {}
        if a.get("resolve") == "type":
            role = str(a.get("role"))
            a_by_role[role] += 1
            functions[role][a.get("function") or ("—" if role.startswith("SH-") else role)] += 1
    internal = {k: v for k, v in roles.items() if not is_external(v.get("org_needs"))}
    L = ["<!-- 由 org_needs_build.py 从 RESPONSIBILITY_MATRIX.yaml 生成，不手改 -->", "# 组织信息需求汇总（岗位体系重梳的输入）", "",
         f"角色 {len(roles)} 个，其中内部 {len(internal)} 个、外部 {len(roles) - len(internal)} 个；类型级 A 共 {sum(a_by_role.values())} 条。泳道先按角色，岗位体系定后一次性改（CONVENTIONS 第 5 节）。", "",
         "## 一、内部角色要承担的职责与审批权", "", "| 角色 | 组 | 职责（去重） | 审批权（去重） | 部门提示 | 职位提示 |", "|---|---|---|---|---|---|"]
    for rid, v in sorted(internal.items()):
        needs = v.get("org_needs") or []
        def uniq(k):
            return "；".join(sorted({str(n.get(k)) for n in needs if n.get(k) and n.get(k) not in ("—", "无")})) or "—"
        L.append(f"| {rid} | {'、'.join(v.get('groups') or [])} | {uniq('responsibility')} | {uniq('approval_right')} | {uniq('department_hint')} | {uniq('position_hint')} |")
    L += ["", "## 二、类型级 A：按角色与职能", "", "| 角色 | 职能 | 条数 |", "|---|---|---|"]
    for role in sorted(functions):
        for fn, n in functions[role].most_common():
            L.append(f"| {role} | {fn} | {n} |")
    L += ["", "## 三、外部角色（不登录本系统，只作泳道）", ""]
    L += [f"- {rid}：{'、'.join(v.get('groups') or [])}" for rid, v in sorted(roles.items()) if rid not in internal]
    (HERE / "ORG_NEEDS.md").write_text("\n".join(L) + "\n", encoding="utf-8")
    print(f"已写 ORG_NEEDS.md：内部角色 {len(internal)}，类型级 A {sum(a_by_role.values())}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
