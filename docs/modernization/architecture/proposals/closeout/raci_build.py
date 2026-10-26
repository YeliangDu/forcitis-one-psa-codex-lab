"""Draft RESPONSIBILITY_MATRIX.yaml from the group process skeletons (structure: RESPONSIBILITY_MATRIX_SPEC.md section A).

Usage: python3 -I raci_build.py [--local] [--write] [--all]
  default  read processes.<group>.yaml from origin/claude/one-arch-<group>-closeout, print a report (first 12 findings per item; --all lists all)
  --local  read closeout/<group>/ in this working tree
  --write  write RESPONSIBILITY_MATRIX.yaml and RACI_MERGE_REPORT.md next to this script (draft; never overwrites without this flag);
           entries already there with A {resolve: type, basis: ...} are hand-registered and kept
  --self-test
Group fragments (people/raci.yaml, revenue/raci.yaml, finance/responsibility.finance.yaml) and command catalogs (<group>/commands.yaml) are read the same way
as the process skeletons (origin branch, or closeout/<group>/ with --local).

Rules: one entry per (aggregate, command) of steps with aggregate and command (also `inner`), platform steps excluded.
  R = actor SH-* of the executing steps; I = SH-* in notify.to (not already R); C = [].
  A = approval.approver of the approval step whose target (closeout_check.gated_target) is that object x command:
      cost_object_owner -> {resolve: instance, field: 费用归属对象.责任人}; any other approver -> {resolve: TODO, role: <approver>};
      no approval -> {resolve: TODO}. Conflicting approvers on one object x command are reported (exactly one A required).
  roles = SH-* seen, with org_needs collected from the skeleton roles (position placeholder until the position system is settled).
Merge: skeleton is the base (R, I, approval-derived A, boundary). A priority: hand-registered type A (a_source hand) > group fragment A
  (a_source group:<g>; fragment sensitive/boundary/note etc. are carried, boundary keys of the fragment win) > skeleton A (skeleton) > TODO (none).
  Conflicts are reported, never resolved: fragment A vs skeleton approver; two groups' fragments disagree; platform.approval type-level A without
  basis/why_human source; role names not SH-* (mapped to SH-* via value-streams.yaml stakeholders only when unique, original kept as role_raw);
  fragment entries absent from skeleton and command catalogs.
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
FRAGMENTS = {"people": "raci.yaml", "finance": "responsibility.finance.yaml", "revenue": "raci.yaml"}
SOURCE_RE = re.compile(r"ADR-\d+|intent|用户|U-\d+")
CARRY = ("sensitive_reason", "note", "why_human", "pending", "draft_gap", "approval", "approver", "source_part")


def new_ent():
    return {"R": [], "I": [], "A": [], "src": [], "esc": set(), "ap": []}


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
                        e = ent.setdefault((agg, cmd), new_ent())
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
                    e = ent.setdefault(tgt, new_ent())
                    ap = s["approval"]
                    if ap.get("engine") == "external":
                        st["approval_external"] += 1
                        continue
                    a = ap.get("approver")
                    if ap.get("engine") == "aggregate" and a != "cost_object_owner":
                        a = "type:" + str(a)
                    if a not in e["A"]:
                        e["A"].append(a)
                    e["ap"].append({"approver": a, "engine": ap.get("engine"), "why_human": ap.get("why_human") or "", "group": g})
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


def read_aux(group, name, local):
    if local:
        p = HERE / group / name
        return p.read_text(encoding="utf-8") if p.exists() else None
    return cc.git_show(f"claude/one-arch-{group}-closeout", f"docs/modernization/architecture/proposals/closeout/{group}/{name}")


def load_fragments(local):
    frags, catalogs, miss = {}, {}, []
    for g in cc.GROUPS:
        if g in FRAGMENTS:
            t = read_aux(g, FRAGMENTS[g], local)
            if t is None:
                miss.append(f"{g}/{FRAGMENTS[g]}")
            else:
                frags[g] = (yaml.safe_load(t) or {}).get("entries") or []
        t = read_aux(g, "commands.yaml", local)
        if t is None:
            miss.append(f"{g}/commands.yaml")
        else:
            catalogs[g] = {(c["aggregate"], cc.base_cmd(c["name"])) for c in (yaml.safe_load(t) or {}).get("commands") or [] if c.get("aggregate") and c.get("name")}
    return frags, catalogs, miss


def load_stakeholders(path=None):
    """Role name -> set of SH-* ids; names are sub, category and each member phrase of value-streams.yaml stakeholders."""
    path = path or HERE.parents[1] / "ssot" / "05-value-streams" / "value-streams.yaml"
    smap = collections.defaultdict(set)
    if not Path(path).exists():
        return smap
    for sh in (yaml.safe_load(Path(path).read_text(encoding="utf-8")) or {}).get("stakeholders") or []:
        names = [sh.get("sub"), sh.get("category")] + re.split(r"[；;、，,]", str(sh.get("members") or ""))
        for n in names:
            n = re.sub(r"[（(].*?[）)]", "", str(n or "")).strip()
            if n and n != "—":
                smap[n].add(sh["id"])
    return smap


def norm_role(name, smap, log):
    """SH-* / system / external: / cost_object_owner pass; other names map to SH-* only when unique, else stay and are logged."""
    if not isinstance(name, str) or re.fullmatch(r"SH-\w+|system.*|external:.*|cost_object_owner|platform\.approval", name):
        return name
    ids = smap.get(name, set())
    if len(ids) == 1:
        log.append((name, "mapped", next(iter(ids))))
        return next(iter(ids))
    log.append((name, "ambiguous" if ids else "unmapped", sorted(ids)))
    return name


def sig_of_skeleton(a):
    if a == "cost_object_owner":
        return ("instance",)
    return ("type", a[5:] if str(a).startswith("type:") else a)


def sig_of_fragment(a):
    return ("instance",) if a.get("resolve") == "instance" else ("type", a.get("role"))


def skeleton_a(As):
    if not As:
        return dict(TODO_A)
    if As[0] == "cost_object_owner":
        return {"resolve": "instance", "field": "费用归属对象.责任人"}
    if str(As[0]).startswith("type:"):
        return {"resolve": "type", "role": As[0][5:]}
    return {"resolve": "TODO", "role": As[0]}


def build_boundary(esc):
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
    return b


def cmd_sort_key(kv):
    return (int(re.sub(r"\D", "", kv[0][0]) or 0), kv[0][1])


def build(ent, roles, typed=None, frags=None, catalogs=None, smap=None):
    typed, frags, catalogs, smap = typed or {}, frags or {}, catalogs or {}, smap or {}
    ent = dict(ent)
    for k in typed:
        ent.setdefault(k, new_ent())
    byk = collections.defaultdict(list)
    for g, es in frags.items():
        for fe in es:
            byk[(fe["object"], cc.base_cmd(fe["command"]))].append((g, fe))
    for k in byk:
        ent.setdefault(k, new_ent())
    inskel = {k for k, e in ent.items() if e["src"]}
    incat = set().union(*catalogs.values()) if catalogs else set()
    issues = {
        "缺 A（无审批节点）": [],
        "A 为固定角色（应改 cost_object_owner 或类型级登记）": [],
        "多个不同 A": [],
        "片段 A 与骨架审批不一致": [],
        "两组片段 A 不一致": [],
        "platform.approval 类型级 A 缺 basis 或 why_human 出处": [],
        "角色写法非 SH- 编号": [],
        "片段独有（骨架与命令目录均无）": [],
    }
    info = {"片段独有但在命令目录": 0, "片段 A 覆盖骨架": 0}
    entries, rolelog = [], []
    for (agg, cmd), e in sorted(ent.items(), key=cmd_sort_key):
        key = (agg, cmd)
        cands = byk.get(key, [])
        skel_groups = {x.split(":")[0] for x in e["src"]}
        cands = sorted(cands, key=lambda c: (c[0] not in skel_groups, (cc.GROUPS + [c[0]]).index(c[0]), c[0]))
        log = []
        norm = []
        for g, fe in cands:
            ra = dict(fe["raci"]["A"])
            if ra.get("role"):
                raw = ra["role"]
                ra["role"] = norm_role(raw, smap, log)
                if ra["role"] != raw:
                    ra["role_raw"] = raw
            norm.append((g, fe, ra))
        for g, fe, _ra in norm:
            for f in ("R", "C", "I"):
                fe["raci"][f] = [norm_role(x, smap, log) for x in fe["raci"].get(f) or []]
        for name, st, ids in log:
            rolelog.append((agg, cmd, name, st, ids))
        sk_sigs = {sig_of_skeleton(a) for a in e["A"]}
        sk_a = skeleton_a(e["A"])
        if len(e["A"]) > 1:
            issues["多个不同 A"].append((agg, cmd, e["A"]))
        if len({tuple(sorted(sig_of_fragment(ra))) for _g, _f, ra in norm}) > 1:
            issues["两组片段 A 不一致"].append((agg, cmd, [(g, ra.get("role") or ra.get("field")) for g, _f, ra in norm]))
        if norm and e["A"]:
            for g, _f, ra in norm:
                if sig_of_fragment(ra) not in sk_sigs:
                    issues["片段 A 与骨架审批不一致"].append((agg, cmd, g, sig_of_fragment(ra), sorted(sk_sigs)))
        if norm and not e["src"]:
            if key in incat:
                info["片段独有但在命令目录"] += 1
            else:
                issues["片段独有（骨架与命令目录均无）"].append((agg, cmd, norm[0][0]))
        if key in typed:
            a, a_source = dict(typed[key]), "hand"
        elif norm:
            a, a_source = dict(norm[0][2]), "group:" + norm[0][0]
            if e["A"]:
                info["片段 A 覆盖骨架"] += 1
        else:
            a = sk_a
            a_source = "skeleton" if e["A"] else "none"
            if e["A"] and a.get("resolve") == "TODO":
                issues["A 为固定角色（应改 cost_object_owner 或类型级登记）"].append((agg, cmd, e["A"][0]))
        if not e["A"] and not norm and key not in typed:
            issues["缺 A（无审批节点）"].append((agg, cmd))
        plat = [x for x in e["ap"] if x["engine"] == "platform.approval"]
        if plat and a.get("resolve") == "type" and not a.get("basis") and not any(SOURCE_RE.search(str(x["why_human"])) for x in plat):
            issues["platform.approval 类型级 A 缺 basis 或 why_human 出处"].append((agg, cmd, a.get("role")))
        R = list(e["R"]) or [x for _g, fe, _ra in norm for x in fe["raci"].get("R") or []]
        C = [x for _g, fe, _ra in norm for x in fe["raci"].get("C") or []]
        I = list(e["I"]) + [x for _g, fe, _ra in norm for x in fe["raci"].get("I") or [] if x not in e["I"]]
        R = list(dict.fromkeys(R))
        C = list(dict.fromkeys(x for x in C if x not in R))
        I = list(dict.fromkeys(x for x in I if x not in R))
        item = {"object": agg, "command": cmd, "raci": {"A": a, "R": R, "C": C, "I": I}}
        b = build_boundary(sorted(e["esc"]))
        for _g, fe, _ra in reversed(norm):
            b.update(fe.get("boundary") or {})
        if b:
            item["boundary"] = b
        item["sensitive"] = any(fe.get("sensitive") for _g, fe, _ra in norm)
        for f in CARRY:
            for _g, fe, _ra in norm:
                if fe.get(f) not in (None, "", []):
                    item[f] = fe[f]
                    break
        item["a_source"] = a_source
        item["sources"] = sorted(set(e["src"]))[:6] + [f"{g}:片段" for g, _f, _r in norm]
        entries.append(item)
    grp = collections.OrderedDict()
    for agg, cmd, name, st, ids in rolelog:
        g = grp.setdefault((name, st, tuple(ids)), [])
        g.append(f"{agg} {cmd}")
    for (name, st, ids), where in sorted(grp.items(), key=lambda kv: -len(kv[1])):
        if st != "mapped":
            issues["角色写法非 SH- 编号"].append((name, {"unmapped": "无对应 SH-", "ambiguous": "多个候选 " + "、".join(ids)}[st], f"{len(where)} 处", "例：" + "；".join(where[:3])))
    rl = {}
    for r, v in sorted(roles.items()):
        rl[r] = {"positions": ["待岗位体系定"], "org_needs": v["org_needs"], "groups": sorted(v["groups"])}
    for it in entries:
        for r in [it["raci"]["A"].get("role")] + it["raci"]["R"] + it["raci"]["C"] + it["raci"]["I"]:
            if isinstance(r, str) and re.fullmatch(r"SH-\w+", r) and r not in rl:
                rl[r] = {"positions": ["待岗位体系定"], "org_needs": [], "groups": sorted({s.split(":")[0] for s in it["sources"]})}
    doc = {"entries": entries, "roles": dict(sorted(rl.items()))}
    return doc, issues, info, {"inskel": inskel, "incat": incat, "frags": frags}


def entry_group(it):
    gs = {s.split(":")[0] for s in it["sources"]}
    for g in cc.GROUPS:
        if g in gs:
            return g
    return "(无)"


def stats(doc, ctx, catalogs):
    ents = doc["entries"]
    per = collections.defaultdict(collections.Counter)
    for it in ents:
        per[entry_group(it)][it["a_source"].split(":")[0] if it["a_source"] != "none" else "none"] += 1
        per[entry_group(it)]["total"] += 1
    missing = collections.defaultdict(list)
    for it in ents:
        if it["a_source"] == "none" or it["raci"]["A"].get("resolve") == "TODO":
            missing[entry_group(it)].append((it["object"], it["command"]))
    keys = {(it["object"], it["command"]) for it in ents}
    catonly = {g: sorted(c - keys, key=lambda k: (int(re.sub(r"\D", "", k[0]) or 0), k[1])) for g, c in catalogs.items()}
    return per, missing, catonly


def report(doc, issues, st, info, ctx, catalogs, miss, show_all, full=False):
    ents = doc["entries"]
    lim = None if (show_all or full) else 12
    per, missing, catonly = stats(doc, ctx, catalogs)
    out = ["# 权责对照表合并报告（raci_build，只报告）", ""]
    if miss:
        out.append("- 取不到：" + "、".join(miss))
    n = len(ents)
    src = collections.Counter(it["a_source"].split(":")[0] if it["a_source"] != "none" else "none" for it in ents)
    byA = collections.Counter(it["raci"]["A"]["resolve"] if it["raci"]["A"].get("resolve") in ("instance", "type") else ("固定角色" if it["raci"]["A"].get("role") else "缺 A") for it in ents)
    out.append(f"- 对象×命令 {n} 条（骨架 {len(ctx['inskel'])}，片段独有 {n - len(ctx['inskel'])}）；片段条数 " + "、".join(f"{g} {len(v)}" for g, v in ctx["frags"].items()) + "".join(f"；{g} 无片段" for g in FRAGMENTS if g not in ctx["frags"]))
    out.append(f"- A 来源：hand {src['hand']}、group {src['group']}、skeleton {src['skeleton']}、无 {src['none']}；片段 A 覆盖了骨架已有 A 的 {info['片段 A 覆盖骨架']} 条")
    out.append(f"- A 形态：责任人实例 {byA['instance']}、类型级 {byA['type']}、固定角色待改 {byA['固定角色']}、缺 A {byA['缺 A']}；R 为空 {sum(1 for e in ents if not e['raci']['R'])}；I 非空 {sum(1 for e in ents if e['raci']['I'])}；sensitive 为真 {sum(1 for e in ents if e['sensitive'])}")
    out.append(f"- 审批节点：无法定位对象×命令 {st['approval_no_target']}，外部（客户）审批不入 A {st['approval_external']}")
    out.append(f"- 角色 {len(doc['roles'])} 个")
    out.append("")
    out += ["## 按组 A 来源", "", "| 组 | 条数 | hand | group | skeleton | 无 | 仍缺 A（含固定角色待改） |", "|---|---|---|---|---|---|---|"]
    for g in cc.GROUPS + ["(无)"]:
        if per[g]["total"]:
            c = per[g]
            out.append(f"| {g} | {c['total']} | {c['hand']} | {c['group']} | {c['skeleton']} | {c['none']} | {len(missing[g])} |")
    out.append("")
    out.append("## 仍缺 A")
    for g in cc.GROUPS + ["(无)"]:
        if missing[g]:
            out.append(f"- {g}（{len(missing[g])}）：" + "；".join(f"{a} {c}" for a, c in missing[g][:lim]) + (" …" if lim and len(missing[g]) > lim else ""))
    out.append("")
    out.append("## 命令目录有、矩阵无（不入矩阵，仅提示）")
    for g, v in catonly.items():
        out.append(f"- {g}（{len(v)}）：" + "；".join(f"{a} {c}" for a, c in v[:lim]) + (" …" if lim and len(v) > lim else ""))
    out.append("")
    out.append("## 冲突与待办（不自动裁决）")
    out.append("- " + "；".join(f"{k} {len(v)}" for k, v in issues.items()) + f"；片段独有但在命令目录 {info['片段独有但在命令目录']}")
    out.append("")
    for k, v in issues.items():
        if k.startswith("缺 A"):
            continue
        out.append(f"### {k}（{len(v)}）")
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
    {"actor": "SH-E3", "where": "in_system", "aggregate": "AGG-2", "command": "确认付款"},
    {"actor": "SH-E3", "where": "in_system", "aggregate": "AGG-3", "command": "盖章"},
    {"actor": "platform", "platform_task": True, "approval": {"engine": "platform.approval", "approver": "cost_object_owner", "why_human": "ADR-0047 财务复核"}},
    {"actor": "SH-E3", "where": "in_system", "aggregate": "AGG-4", "command": "归档"},
    {"actor": "platform", "platform_task": True, "approval": {"engine": "platform.approval", "approver": "SH-M1", "why_human": "金额大"}},
    {"actor": "SH-E3", "where": "in_system", "aggregate": "AGG-5", "command": "作废"}]}]}}

SAMPLE_SH = {"stakeholders": [
    {"id": "SH-M1", "sub": "—", "category": "管理层", "members": "执委会；职能高管"},
    {"id": "SH-E6", "sub": "职能人员", "category": "员工", "members": "财务、人力"},
    {"id": "SH-E3", "sub": "设计师", "category": "员工", "members": "设计师（含驻场）"}]}


def _frag(obj, cmd, A, **kw):
    return dict({"object": obj, "command": cmd, "raci": {"A": A, "R": ["SH-E6"], "C": [], "I": []}}, **kw)


def self_test():
    import tempfile
    ent, roles, st = collect(SAMPLE)
    doc, issues, info, _ = build(ent, roles)
    by = {(e["object"], e["command"]): e for e in doc["entries"]}
    checks = [
        by[("AGG-1", "提交报销")]["raci"]["A"] == {"resolve": "TODO"},
        by[("AGG-1", "提交报销")]["raci"]["R"] == ["SH-E1"] and by[("AGG-1", "提交报销")]["raci"]["I"] == ["SH-E2"],
        by[("AGG-1", "核定报销")]["raci"]["A"] == {"resolve": "instance", "field": "费用归属对象.责任人"},
        by[("AGG-1", "核定报销")]["boundary"] == {"limit_rule": "AR-01"},
        by[("AGG-2", "确认付款")]["raci"]["A"] == {"resolve": "TODO", "role": "SH-M1"},
        by[("AGG-2", "登记付款")]["raci"]["A"] == {"resolve": "TODO"},
        len(issues["缺 A（无审批节点）"]) == 3 and len(issues["多个不同 A"]) == 0,
        doc["roles"]["SH-E1"]["org_needs"] == [{"responsibility": "制单"}],
        by[("AGG-1", "核定报销")]["a_source"] == "skeleton" and by[("AGG-1", "提交报销")]["a_source"] == "none",
    ]
    typed = {("AGG-2", "确认付款"): {"resolve": "type", "role": "SH-M1", "basis": "ADR-x"}}
    doc2, issues2, _i, _c = build(ent, roles, typed)
    by2 = {(e["object"], e["command"]): e for e in doc2["entries"]}
    checks += [
        by2[("AGG-2", "确认付款")]["raci"]["A"] == {"resolve": "type", "role": "SH-M1", "basis": "ADR-x"},
        by2[("AGG-2", "确认付款")]["a_source"] == "hand",
        not any(x[0] == "AGG-2" for x in issues2["A 为固定角色（应改 cost_object_owner 或类型级登记）"]),
    ]
    with tempfile.TemporaryDirectory() as td:
        p = Path(td) / "v.yaml"
        p.write_text(yaml.safe_dump(SAMPLE_SH, allow_unicode=True), encoding="utf-8")
        smap = load_stakeholders(p)
    checks += [dict(smap).get("职能高管") == {"SH-M1"}, "财务负责人" not in smap]
    frags = {
        "g": [_frag("AGG-1", "核定报销", {"resolve": "instance", "field": "申请人.负责人"}, sensitive=True, note="n1", boundary={"segregation": ["提交人 ≠ A"]}),
              _frag("AGG-1", "提交报销", {"resolve": "type", "role": "SH-E6", "function": "出纳"}),
              _frag("AGG-2", "确认付款", {"resolve": "type", "role": "财务负责人"}),
              _frag("AGG-2", "登记付款", {"resolve": "type", "role": "职能高管"}),
              _frag("AGG-9", "孤儿", {"resolve": "type", "role": "SH-E6"}),
              _frag("AGG-9", "仅目录", {"resolve": "type", "role": "SH-E6"})],
        "h": [_frag("AGG-1", "提交报销", {"resolve": "type", "role": "SH-E3"})]}
    cats = {"g": {("AGG-9", "仅目录")}}
    doc3, issues3, info3, ctx3 = build(ent, roles, {}, frags, cats, smap)
    by3 = {(e["object"], e["command"]): e for e in doc3["entries"]}
    sub = by3[("AGG-1", "提交报销")]
    checks += [
        sub["raci"]["A"]["role"] == "SH-E6" and sub["a_source"] == "group:g" and sub["raci"]["R"] == ["SH-E1"],
        len(issues3["两组片段 A 不一致"]) == 1,
        by3[("AGG-1", "核定报销")]["a_source"] == "group:g" and by3[("AGG-1", "核定报销")]["sensitive"] is True,
        by3[("AGG-1", "核定报销")]["boundary"] == {"limit_rule": "AR-01", "segregation": ["提交人 ≠ A"]} and by3[("AGG-1", "核定报销")]["note"] == "n1",
        any(x[:2] == ("AGG-2", "确认付款") for x in issues3["片段 A 与骨架审批不一致"]),
        not any(x[:2] == ("AGG-1", "核定报销") for x in issues3["片段 A 与骨架审批不一致"]),
        by3[("AGG-2", "登记付款")]["raci"]["A"]["role"] == "SH-M1" and by3[("AGG-2", "登记付款")]["raci"]["A"]["role_raw"] == "职能高管",
        any(x[0] == "财务负责人" and x[1] == "无对应 SH-" for x in issues3["角色写法非 SH- 编号"]),
        [x[:2] for x in issues3["片段独有（骨架与命令目录均无）"]] == [("AGG-9", "孤儿")] and info3["片段独有但在命令目录"] == 1,
        any(x[:2] == ("AGG-2", "确认付款") for x in issues3["platform.approval 类型级 A 缺 basis 或 why_human 出处"]),
    ]
    doc4, issues4, _i4, _c4 = build(ent, roles, typed, {"g": [_frag("AGG-2", "确认付款", {"resolve": "type", "role": "SH-E6"})]}, {}, smap)
    by4 = {(e["object"], e["command"]): e for e in doc4["entries"]}
    checks += [
        by4[("AGG-2", "确认付款")]["raci"]["A"]["basis"] == "ADR-x" and by4[("AGG-2", "确认付款")]["a_source"] == "hand",
        not any(x[:2] == ("AGG-2", "确认付款") for x in issues4["platform.approval 类型级 A 缺 basis 或 why_human 出处"]),
        any(x[:2] == ("AGG-2", "确认付款") for x in issues4["片段 A 与骨架审批不一致"]),
    ]
    doc5, issues5, _i5, _c5 = build(ent, roles, {}, {"g": [_frag("AGG-4", "归档", {"resolve": "type", "role": "SH-E6"}), _frag("AGG-5", "作废", {"resolve": "type", "role": "SH-M1"})]}, {}, smap)
    bad = {x[:2] for x in issues5["platform.approval 类型级 A 缺 basis 或 why_human 出处"]}
    doc6, issues6, _i6, _c6 = build(ent, roles, {}, {"g": [_frag("AGG-5", "作废", {"resolve": "type", "role": "SH-M1", "basis": "ADR-1"})]}, {}, smap)
    checks += [("AGG-5", "作废") in bad, ("AGG-4", "归档") not in bad, not issues6["platform.approval 类型级 A 缺 basis 或 why_human 出处"]]
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
    frags, catalogs, miss = load_fragments(local)
    doc, issues, info, ctx = build(ent, roles, load_type_entries(HERE / "RESPONSIBILITY_MATRIX.yaml"), frags, catalogs, load_stakeholders())
    print(report(doc, issues, st, info, ctx, catalogs, miss, "--all" in argv))
    if "--write" in argv:
        head = "# 权责对照表（raci_build.py 从三组骨架与各组权责片段合并生成；a_source 标 A 的来源；A 含 TODO 的待补；结构见 RESPONSIBILITY_MATRIX_SPEC.md A 节）\n"
        (HERE / "RESPONSIBILITY_MATRIX.yaml").write_text(head + yaml.safe_dump(doc, allow_unicode=True, sort_keys=False), encoding="utf-8")
        (HERE / "RACI_MERGE_REPORT.md").write_text(report(doc, issues, st, info, ctx, catalogs, miss, True, full=True), encoding="utf-8")
        print("已写 RESPONSIBILITY_MATRIX.yaml、RACI_MERGE_REPORT.md")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
