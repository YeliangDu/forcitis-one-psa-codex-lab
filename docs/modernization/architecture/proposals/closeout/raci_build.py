"""Draft RESPONSIBILITY_MATRIX.yaml from the group process skeletons (structure: RESPONSIBILITY_MATRIX_SPEC.md section A).

Usage: python3 -I raci_build.py [--local] [--write] [--all]
  default  read processes.<group>.yaml from origin/claude/one-arch-<group>-closeout, print a report (first 12 findings per item; --all lists all)
  --local  read closeout/<group>/ in this working tree
  --write  write RESPONSIBILITY_MATRIX.yaml next to this script (draft; never overwrites without this flag);
           entries already there with A {resolve: type, basis: ...} are hand-registered and kept
  --self-test

Rules: one entry per (aggregate, command) of steps with aggregate and command (also `inner`), platform steps excluded.
  R = actor SH-* of the executing steps; I = SH-* in notify.to (not already R); C = [].
  A = approval.approver of the approval step whose target (closeout_check.gated_target) is that object x command:
      cost_object_owner -> {resolve: instance, field: 费用归属对象.责任人}; any other approver -> {resolve: TODO, role: <approver>};
      no approval -> {resolve: TODO}. Conflicting approvers on one object x command are reported (exactly one A required).
  roles = SH-* seen, with org_needs collected from the skeleton roles (position placeholder until the position system is settled).
Report only; exit code 0 (1 for --self-test failure).
"""
import collections
import importlib.util
import re
import sys
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("closeout_check", HERE / "closeout_check.py")
cc = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(cc)

TODO_A = {"resolve": "TODO"}


def collect(docs):
    """docs: {group: yaml doc}. Returns (entries dict, roles dict, stats)."""
    ent = {}
    roles = collections.defaultdict(lambda: {"org_needs": [], "groups": set()})
    st = collections.Counter()
    for g, doc in docs.items():
        for _k, u in cc.units_of(doc):
            for r in u.get("roles") or []:
                if isinstance(r, dict) and r.get("id"):
                    roles[r["id"]]["groups"].add(g)
                    st["roles_declared"] += 1
                    if r.get("org_needs"):
                        if r["org_needs"] not in roles[r["id"]]["org_needs"]:
                            roles[r["id"]]["org_needs"].append(r["org_needs"])
            steps = cc.unit_steps(u) + cc.transition_approvals(u)
            for i, s in enumerate(steps):
                if s.get("read") or s.get("actor") == "platform" or s.get("platform_task"):
                    pass
                else:
                    pairs = []
                    if s.get("aggregate") and s.get("command"):
                        pairs.append((s["aggregate"], cc.base_cmd(s["command"]), s))
                    for inner in s.get("inner") or []:
                        if inner.get("aggregate") and inner.get("command"):
                            pairs.append((inner["aggregate"], cc.base_cmd(inner["command"]), s))
                    for agg, cmd, src in pairs:
                        e = ent.setdefault((agg, cmd), {"R": [], "I": [], "A": [], "src": [], "esc": set()})
                        a = src.get("actor")
                        if isinstance(a, str) and a.startswith("SH-") and a not in e["R"]:
                            e["R"].append(a)
                        if a in ("system",):
                            e.setdefault("auto", True)
                        for nt in src.get("notify") or []:
                            to = nt.get("to") if isinstance(nt, dict) else None
                            if isinstance(to, str) and re.fullmatch(r"SH-\w+", to) and to not in e["I"]:
                                e["I"].append(to)
                        e["src"].append(f"{g}:{u['id']}#{i + 1}")
                        if a and str(a).startswith("SH-"):
                            roles[a]["groups"].add(g)
                if s.get("approval"):
                    tgt = cc.gated_target(steps, i)
                    if not tgt or not re.fullmatch(r"AGG-\d+|REF-\d+", str(tgt[0])):
                        st["approval_no_target"] += 1
                        continue
                    e = ent.setdefault(tgt, {"R": [], "I": [], "A": [], "src": [], "esc": set()})
                    ap = s["approval"]
                    if ap.get("engine") == "external":
                        st["approval_external"] += 1
                        continue
                    a = ap.get("approver")
                    if ap.get("engine") == "aggregate" and a != "cost_object_owner":
                        a = "type:" + str(a)
                    if a not in e["A"]:
                        e["A"].append(a)
                    e["esc"].update(ap.get("escalation_rules") or [])
                    e["src"].append(f"{g}:{u['id']}#{i + 1}(审批)")
    for e in ent.values():
        e["I"] = [x for x in e["I"] if x not in e["R"]]
    return ent, roles, st


def load_type_entries(path):
    """Hand-registered type-level A (CONVENTIONS 第 5 节「类型级例外」); kept across --write."""
    if not path.exists():
        return {}
    doc = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    keep = {}
    for e in doc.get("entries") or []:
        a = (e.get("raci") or {}).get("A") or {}
        if a.get("resolve") == "type" and a.get("basis"):
            keep[(e["object"], cc.base_cmd(e["command"]))] = a
    return keep


def build(ent, roles, typed=None):
    typed = typed or {}
    entries = []
    issues = {"缺 A（无审批节点）": [], "A 为固定角色（应改 cost_object_owner 或类型级登记）": [], "多个不同 A": []}
    for (agg, cmd), e in sorted(ent.items(), key=lambda kv: (int(re.sub(r"\D", "", kv[0][0]) or 0), kv[0][1])):
        As = e["A"]
        if (agg, cmd) in typed:
            a = dict(typed[(agg, cmd)])
            if len(As) > 1:
                issues["多个不同 A"].append((agg, cmd, As))
        elif not As:
            a = dict(TODO_A)
            issues["缺 A（无审批节点）"].append((agg, cmd))
        else:
            if As[0] == "cost_object_owner":
                a = {"resolve": "instance", "field": "费用归属对象.责任人"}
            elif str(As[0]).startswith("type:"):
                a = {"resolve": "type", "role": As[0][5:]}
            else:
                a = {"resolve": "TODO", "role": As[0]}
            if As[0] != "cost_object_owner" and not str(As[0]).startswith("type:"):
                issues["A 为固定角色（应改 cost_object_owner 或类型级登记）"].append((agg, cmd, As[0]))
            if len(As) > 1:
                issues["多个不同 A"].append((agg, cmd, As))
        item = {"object": agg, "command": cmd, "raci": {"A": a, "R": e["R"], "C": [], "I": e["I"]}}
        esc = sorted(e["esc"])
        if esc:
            b = {}
            lim = [x for x in esc if x in ("AR-01", "AR-02")]
            if lim:
                b["limit_rule"] = lim[0]
            if "AR-11" in esc:
                b["delegate"] = {"when": "AR-11"}
            if "AR-12" in esc:
                b["escalate"] = {"when": "AR-12"}
            rest = [x for x in esc if x not in ("AR-01", "AR-02", "AR-11", "AR-12")]
            if rest:
                b["other_rules"] = rest
            item["boundary"] = b
        item["sensitive"] = False
        item["sources"] = sorted(set(e["src"]))[:6]
        entries.append(item)
    rl = {}
    for r, v in sorted(roles.items()):
        rl[r] = {"positions": ["待岗位体系定"], "org_needs": v["org_needs"], "groups": sorted(v["groups"])}
    return {"entries": entries, "roles": rl}, issues


def report(doc, issues, st, show_all):
    ents = doc["entries"]
    lim = None if show_all else 12
    out = ["# 权责对照表初稿（raci_build，只报告）", ""]
    n = len(ents)
    byA = collections.Counter(e["raci"]["A"]["resolve"] if e["raci"]["A"].get("resolve") in ("instance", "type") else ("固定角色" if e["raci"]["A"].get("role") else "缺 A") for e in ents)
    out.append(f"- 对象×命令 {n} 条；A：责任人实例 {byA['instance']}、类型级 {byA['type']}、固定角色待改 {byA['固定角色']}、缺 A {byA['缺 A']}；R 为空 {sum(1 for e in ents if not e['raci']['R'])}（系统自动或平台步骤）；I 非空 {sum(1 for e in ents if e['raci']['I'])}")
    out.append(f"- 审批节点：无法定位对象×命令 {st['approval_no_target']}，外部（客户）审批不入 A {st['approval_external']}")
    nr = len(doc["roles"])
    withn = sum(1 for r in doc["roles"].values() if r["org_needs"])
    out.append(f"- 角色 {nr} 个，其中骨架 roles 带 org_needs 的 {withn} 个（骨架尚未填 org_needs 的需铺开时补）")
    out.append("- 「恰一个 A」检查：" + "；".join(f"{k} {len(v)}" for k, v in issues.items()))
    out.append("")
    for k, v in issues.items():
        if k.startswith("缺 A"):
            continue
        out.append(f"## {k}（{len(v)}）")
        out += [f"- {x}" for x in v[:lim]]
        if lim and len(v) > lim:
            out.append(f"- …另 {len(v) - lim} 条")
        out.append("")
    return "\n".join(out) + "\n"


SAMPLE = {"g": {"processes": [{"id": "PRC-90", "roles": [{"id": "SH-E1", "as": "initiator", "org_needs": {"responsibility": "制单"}}], "steps": [
    {"actor": "SH-E1", "where": "in_system", "aggregate": "AGG-1", "command": "提交报销", "notify": [{"to": "SH-E2", "how": "x"}]},
    {"actor": "platform", "platform_task": True, "approval": {"engine": "platform.approval", "approver": "cost_object_owner", "escalation_rules": ["AR-01"]}},
    {"actor": "SH-E3", "where": "in_system", "aggregate": "AGG-1", "command": "核定报销"},
    {"actor": "SH-E3", "where": "in_system", "aggregate": "AGG-2", "command": "登记付款"},
    {"actor": "platform", "platform_task": True, "approval": {"engine": "platform.approval", "approver": "SH-M1"}},
    {"actor": "SH-E3", "where": "in_system", "aggregate": "AGG-2", "command": "确认付款"}]}]}}


def self_test():
    ent, roles, st = collect(SAMPLE)
    doc, issues = build(ent, roles)
    by = {(e["object"], e["command"]): e for e in doc["entries"]}
    checks = [
        by[("AGG-1", "提交报销")]["raci"]["A"] == {"resolve": "TODO"},
        by[("AGG-1", "提交报销")]["raci"]["R"] == ["SH-E1"] and by[("AGG-1", "提交报销")]["raci"]["I"] == ["SH-E2"],
        by[("AGG-1", "核定报销")]["raci"]["A"] == {"resolve": "instance", "field": "费用归属对象.责任人"},
        by[("AGG-1", "核定报销")]["boundary"] == {"limit_rule": "AR-01"},
        by[("AGG-2", "确认付款")]["raci"]["A"] == {"resolve": "TODO", "role": "SH-M1"},
        by[("AGG-2", "登记付款")]["raci"]["A"] == {"resolve": "TODO"},
        len(issues["缺 A（无审批节点）"]) == 2 and len(issues["多个不同 A"]) == 0,
        doc["roles"]["SH-E1"]["org_needs"] == [{"responsibility": "制单"}],
    ]
    doc2, issues2 = build(ent, roles, {("AGG-2", "确认付款"): {"resolve": "type", "role": "SH-M1", "basis": "ADR-x"}})
    by2 = {(e["object"], e["command"]): e for e in doc2["entries"]}
    checks += [
        by2[("AGG-2", "确认付款")]["raci"]["A"] == {"resolve": "type", "role": "SH-M1", "basis": "ADR-x"},
        not any(x[0] == "AGG-2" for x in issues2["A 为固定角色（应改 cost_object_owner 或类型级登记）"]),
    ]
    for i, c in enumerate(checks, 1):
        print(f"自测 {i}: {'通过' if c else '失败'}")
    print("自测", "通过" if all(checks) else "失败")
    return 0 if all(checks) else 1


def main(argv):
    if "--self-test" in argv:
        return self_test()
    local = "--local" in argv
    docs = {}
    for g in cc.GROUPS:
        t = cc.read_group_file(g, "proc", local)
        if t is None:
            print(f"取不到 processes.{g}.yaml")
            continue
        docs[g] = yaml.safe_load(t)
    ent, roles, st = collect(docs)
    doc, issues = build(ent, roles, load_type_entries(HERE / "RESPONSIBILITY_MATRIX.yaml"))
    print(report(doc, issues, st, "--all" in argv))
    if "--write" in argv:
        head = "# 权责对照表初稿（raci_build.py 从三组骨架生成；A 含 TODO 的待补；结构见 RESPONSIBILITY_MATRIX_SPEC.md A 节）\n"
        (HERE / "RESPONSIBILITY_MATRIX.yaml").write_text(head + yaml.safe_dump(doc, allow_unicode=True, sort_keys=False), encoding="utf-8")
        print("已写 RESPONSIBILITY_MATRIX.yaml")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
