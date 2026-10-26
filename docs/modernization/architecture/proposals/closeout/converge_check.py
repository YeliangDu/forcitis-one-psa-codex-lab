"""Aggregate convergence check (RESPONSIBILITY_MATRIX_SPEC.md section B): command catalogue and state machines against the group skeletons.

Usage: python3 -I converge_check.py [--local] [--commands PATH] [--states PATH] [--all]   |   --self-test
  skeletons: processes.<group>.yaml from origin/claude/one-arch-<group>-closeout (or closeout/<group>/ with --local)
  defaults:  closeout/commands.yaml, closeout/state-machines.yaml (missing file -> reported, only skeleton statistics are printed)

commands.yaml (minimal structure, proposal):
  commands:
    - name: 提交费用报销          # unique per aggregate; synonyms merged into one: merged_from: [旧名]
      aggregate: AGG-59
      params: [报销单]            # optional
      preconditions: [INV-84]     # optional, rule card or invariant ids
      authority: {A: ...}         # optional, filled from RESPONSIBILITY_MATRIX.yaml
      idempotency_key: [报销单号] # optional
      unused_ok: "仅月度维护用"   # optional; a reason declares a command used by maintenance, not an orphan

state-machines.yaml (minimal structure, proposal):
  machines:
    - aggregate: AGG-59
      initial: 草稿               # optional, default = first state
      states: [草稿, 已提交, {name: 已核定, terminal: true}]   # string or {name, terminal}
      transitions:
        - {from: 草稿, to: 已提交, command: 提交费用报销, guard: [INV-84], reverse_of: ~}
    - {aggregate: AGG-43, stateless: true, reason: "运行单据，写下即不可改"}   # no states/transitions; its steps need no transition
    - {aggregate: AGG-60, perpetual: true, reason: "法人主体长期存在", states: [...], transitions: [...]}   # exempt from terminal-state checks
        # from: a state, a list of states, or "*" (any state); command may be null for event-triggered transitions

Checks: (1) orphan commands: a catalogue command used by no skeleton step (command or inner) and no single-aggregate commands list;
skeleton commands missing from the catalogue; (2) states: transition endpoints declared, every state reachable from initial, every non-terminal
state reaches a terminal state, at least one terminal state, transition commands in the catalogue; (3) every state-changing skeleton step (aggregate +
command, not read / platform / proposed 〔缺〕) matches a transition of that aggregate's machine (aggregates without a machine are listed once).
Report only; exit code 0 (1 for --self-test failure).
"""
import collections
import importlib.util
import sys
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("closeout_check", Path(__file__).resolve().parents[2] / "panorama" / "closeout_check.py")  # moved to panorama/ (G04)
cc = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(cc)


def skeleton_usage(docs):
    """Returns (used {(agg, cmd): [where]}, changing {(agg, cmd): [where]})."""
    used, changing = collections.defaultdict(list), collections.defaultdict(list)
    for g, doc in docs.items():
        for _k, u in cc.units_of(doc):
            for c in (u.get("commands") or []) + (u.get("transitions") or []):
                cmd = c.get("command") if isinstance(c, dict) else c
                agg = (c.get("aggregate") if isinstance(c, dict) else None) or u.get("aggregate")
                if agg and cmd:
                    used[(agg, cc.base_cmd(cmd))].append(f"{g}:{u['id']}")
            steps = cc.unit_steps(u)
            for i, s in enumerate(steps, 1):
                kind = cc.classify(s, u)
                pairs = []
                if s.get("aggregate") and s.get("command"):
                    pairs.append((s["aggregate"], s["command"]))
                pairs += [(x.get("aggregate"), x.get("command")) for x in s.get("inner") or [] if x.get("aggregate") and x.get("command")]
                for a, c in pairs:
                    key = (a, cc.base_cmd(c))
                    used[key].append(f"{g}:{u['id']}#{i}")
                    if kind in ("command",) and cc.MISSING not in str(c):
                        changing[key].append(f"{g}:{u['id']}#{i}")
    return used, changing


def machine_check(m):
    """Returns dict of problems for one machine."""
    states = []
    term = set()
    for s in m.get("states") or []:
        n = s["name"] if isinstance(s, dict) else s
        states.append(n)
        if isinstance(s, dict) and s.get("terminal"):
            term.add(n)
    sset = set(states)
    tr = m.get("transitions") or []
    edges = []
    undeclared = set()
    for t in tr:
        frm = t.get("from")
        frms = states if frm == "*" else ([frm] if not isinstance(frm, list) else frm)
        for f in frms:
            if f is not None and f not in sset:
                undeclared.add(f)
            if t.get("to") not in sset:
                undeclared.add(t.get("to"))
            edges.append((f, t.get("to")))
    initial = m.get("initial") or (states[0] if states else None)
    fwd = collections.defaultdict(set)
    rev = collections.defaultdict(set)
    for f, t in edges:
        if f is not None:
            fwd[f].add(t)
            rev[t].add(f)
    seen, stack = ({initial} if initial else set()), ([initial] if initial else [])
    # transitions with from null are creation: their targets are also entry points
    for f, t in edges:
        if f is None and t not in seen:
            seen.add(t)
            stack.append(t)
    while stack:
        for n in fwd[stack.pop()]:
            if n not in seen:
                seen.add(n)
                stack.append(n)
    can_end, stack = set(term), list(term)
    while stack:
        for n in rev[stack.pop()]:
            if n not in can_end:
                can_end.add(n)
                stack.append(n)
    return {
        "undeclared": sorted(x for x in undeclared if x),
        "unreachable": [s for s in states if s not in seen],
        "no_terminal_path": [s for s in states if s not in term and s not in can_end],
        "no_terminal": not term,
    }


def run(cmds, machines, used, changing):
    res = collections.OrderedDict()
    catalog = {(c["aggregate"], cc.base_cmd(c["name"])): c for c in cmds}
    for c in cmds:
        for old in c.get("merged_from") or []:
            catalog.setdefault((c["aggregate"], cc.base_cmd(old)), c)
    res["孤儿命令（目录有、骨架无人用）"] = sorted(f"{a} {n}" for (a, n), c in catalog.items()
                                         if (a, n) not in used and not c.get("unused_ok") and n == cc.base_cmd(c["name"]))
    res["骨架命令未入目录"] = sorted(f"{a} {n}" for (a, n) in used if (a, n) not in catalog) if cmds else []
    mach = {m["aggregate"]: m for m in machines}
    trans_cmds = set()
    stateless = {a for a, m in mach.items() if m.get("stateless") is True}
    for a in sorted(stateless):
        m = mach[a]
        if not str(m.get("reason") or "").strip():
            res.setdefault("无状态未写理由", []).append(a)
        if m.get("states") or m.get("transitions"):
            res.setdefault("标了无状态却写了状态或转换", []).append(a)
    for a, m in mach.items():
        if a in stateless:
            continue
        mc = machine_check(m)
        for t in m.get("transitions") or []:
            if t.get("command"):
                trans_cmds.add((a, cc.base_cmd(t["command"])))
                if cmds and (a, cc.base_cmd(t["command"])) not in catalog:
                    res.setdefault("转换命令不在目录", []).append(f"{a} {t['command']}")
        if mc["undeclared"]:
            res.setdefault("转换引用未声明的状态", []).append(f"{a}: {mc['undeclared']}")
        if mc["unreachable"]:
            res.setdefault("状态不可达", []).append(f"{a}: {mc['unreachable']}")
        perpetual = m.get("perpetual") is True
        if perpetual and not str(m.get("reason") or "").strip():
            res.setdefault("长期配置未写理由", []).append(a)
        if mc["no_terminal"] and not perpetual:
            res.setdefault("无终态", []).append(a)
        if mc["no_terminal_path"] and not mc["no_terminal"] and not perpetual:
            res.setdefault("非终态无法到达终态", []).append(f"{a}: {mc['no_terminal_path']}")
    no_machine = sorted({a for (a, _c) in changing if a not in mach})
    res["改状态步骤无对应转换"] = sorted(f"{a} {c}（{','.join(changing[(a, c)][:2])}）" for (a, c) in changing if a in mach and a not in stateless and (a, c) not in trans_cmds)
    res["改状态步骤所在聚合无状态机"] = no_machine
    return res


def load_docs(local):
    docs = {}
    for g in cc.GROUPS:
        t = cc.read_group_file(g, "proc", local)
        if t:
            docs[g] = yaml.safe_load(t)
    return docs


def main(argv):
    if "--self-test" in argv:
        return self_test()
    local = "--local" in argv
    arg = lambda k, d: Path(argv[argv.index(k) + 1]) if k in argv else d
    cpath, spath = arg("--commands", HERE / "commands.yaml"), arg("--states", HERE / "state-machines.yaml")
    docs = load_docs(local)
    used, changing = skeleton_usage(docs)
    print(f"骨架：{len(docs)} 组；用到的 聚合×命令 {len(used)} 个；改状态步骤涉及 {len(changing)} 个，聚合 {len({a for a, _ in changing})} 个")
    cmds = yaml.safe_load(cpath.read_text(encoding="utf-8")).get("commands", []) if cpath.exists() else []
    machines = yaml.safe_load(spath.read_text(encoding="utf-8")).get("machines", []) if spath.exists() else []
    if not cmds:
        print(f"无 {cpath.name}（或为空）：命令目录检查跳过")
    if not machines:
        print(f"无 {spath.name}（或为空）：状态机检查跳过")
    if cmds or machines:
        lim = None if "--all" in argv else 12
        for k, v in run(cmds, machines, used, changing).items():
            print(f"## {k}（{len(v)}）")
            for x in v[:lim]:
                print(f"- {x}")
    return 0


S_CMDS = [{"name": n, "aggregate": "AGG-1"} for n in ("提交", "批准", "孤儿命令", "冲销")]
S_MACH = [{"aggregate": "AGG-1", "initial": "草稿", "states": ["草稿", "已提交", {"name": "已批准", "terminal": True}, "幽灵", "死胡同"],
           "transitions": [{"from": "草稿", "to": "已提交", "command": "提交"}, {"from": "已提交", "to": "已批准", "command": "批准"},
                           {"from": "已提交", "to": "死胡同"}, {"from": "已批准", "to": "未声明", "command": "冲销"}]},
          {"aggregate": "AGG-3", "stateless": True, "reason": "运行单据"},
          {"aggregate": "AGG-4", "stateless": True, "states": ["甲"]},
          {"aggregate": "AGG-6", "perpetual": True, "reason": "法人主体长期存在", "states": ["启用", "停用"],
           "transitions": [{"from": "启用", "to": "停用", "command": "停用"}, {"from": "停用", "to": "启用", "command": "启用"}]},
          {"aggregate": "AGG-7", "perpetual": True, "states": ["启用"]}]
S_DOCS = {"g": {"processes": [{"id": "PRC-90", "steps": [
    {"actor": "SH-E1", "where": "in_system", "aggregate": "AGG-1", "command": "提交"},
    {"actor": "SH-E1", "where": "in_system", "aggregate": "AGG-1", "command": "批准"},
    {"actor": "SH-E1", "where": "in_system", "aggregate": "AGG-1", "command": "未登记命令"},
    {"actor": "SH-E1", "where": "in_system", "aggregate": "AGG-2", "command": "别的聚合命令"},
    {"actor": "SH-E1", "where": "in_system", "aggregate": "AGG-3", "command": "运行"}]},
    {"id": "PRC-91", "type": "single_aggregate", "aggregate": "AGG-1", "commands": [{"command": "孤儿命令", "aggregate": "AGG-5"}]},
    {"id": "PRC-92", "type": "single_aggregate", "aggregate": "AGG-1", "transitions": [{"from": "已批准", "to": "已冲销", "command": "冲销"}]}]}}


def self_test():
    used, changing = skeleton_usage(S_DOCS)
    r = run(S_CMDS, S_MACH, used, changing)
    exp = {
        "孤儿命令（目录有、骨架无人用）": ["AGG-1 孤儿命令"],
        "骨架命令未入目录": ["AGG-1 未登记命令", "AGG-2 别的聚合命令", "AGG-3 运行", "AGG-5 孤儿命令"],
        "无状态未写理由": ["AGG-4"],
        "长期配置未写理由": ["AGG-7"],
        "转换命令不在目录": ["AGG-6 停用", "AGG-6 启用"],
        "标了无状态却写了状态或转换": ["AGG-4"],
        "转换引用未声明的状态": ["AGG-1: ['未声明']"],
        "状态不可达": ["AGG-1: ['幽灵']"],
        "非终态无法到达终态": ["AGG-1: ['幽灵', '死胡同']"],
        "改状态步骤无对应转换": ["AGG-1 未登记命令（g:PRC-90#3）"],
        "改状态步骤所在聚合无状态机": ["AGG-2"],
    }
    ok = True
    for k, v in exp.items():
        good = r.get(k) == v
        ok &= good
        print(f"{k}: {'通过' if good else '失败'} 期望 {v} 实得 {r.get(k)}")
    extra = set(r) - set(exp)
    ok &= not extra and "无终态" not in r
    print("无多余项:", "通过" if not extra else f"失败 {extra}")
    print("自测", "通过" if ok else "失败")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
