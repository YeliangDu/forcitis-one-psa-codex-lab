"""Business object x process/command CRUD matrix from the group skeletons, plus six consistency checks (report only).

Usage: python3 -I object_check.py [--local] [--write] [--matrix PATH] [--states PATH]   |   --self-test
  skeletons: processes.<group>.yaml from origin/claude/one-arch-<group>-closeout (or closeout/<group>/ with --local)
  objects:   OBJ-NN in the aggregate name cell of ENTERPRISE_AGGREGATES_DRAFT.md section 2.2; catalogue = table rows of ENTERPRISE_INFO_MAP_DRAFT.md
             (rows whose definition says 非对象 / 行号保留 are not objects); an aggregate without OBJ-NN is its own row
  --write:   write CRUD_MATRIX.md (sections by value stream: VS-NN of the first stage of the process) next to this script

Command class by name: D end/archive/reverse words (关闭 结束 完成 取消 作废 冲销 ...), C create words (登记 新建 创建 ...), U change words (提交 批准
确认 变更 ...), otherwise "?". Reverse words (冲销 红冲 撤销 撤回 作废 回退 更正 释放 取消) are also counted as reverse. A step with `read`/`reads` marks R.
Checks: 1 aggregate or object not in the catalogue (or already a non-object); 2 object lacking creation / change / end in any process (or not touched
at all: missing process, or should be reference data or an attribute); 3 one step changing several aggregates beyond inner/also (list aggregate, `aggregates`,
compound command name, unit with several aggregates) and aggregate state sets of different units that do not form one machine (several unconnected
components, or names differing only by 已/待 prefix, or terminal in one unit and not in another); 4 several BCs writing one object (step.bc or the
process owner_bc, against the aggregate's BC); 5 object x command without an A in RESPONSIBILITY_MATRIX.yaml (skipped when absent); 6 lifecycle:
no end/archive, no reverse command. Exit code 0 (1 for --self-test failure).
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
INFO_MAP = cc.PROPOSALS / "ENTERPRISE_INFO_MAP_DRAFT.md"
VS_FILE = cc.PROPOSALS / "value-streams.v2.yaml"

REVERSE = ("冲销", "红冲", "撤销", "撤回", "作废", "回退", "更正", "释放", "取消")
END = ("关闭", "结束", "终止", "归档", "结项", "结清", "停用", "注销", "完成", "办结", "竣工", "关账", "封账", "核销", "结转", "退还", "没收") + REVERSE
CREATE = ("登记", "新建", "创建", "建立", "开立", "发起", "计划", "编制", "立项", "生成", "导入", "录入", "起草", "申请", "设立", "开具", "发布", "制单", "入职", "办理")
UPDATE = ("修改", "变更", "更新", "调整", "维护", "批准", "通过", "审批", "核定", "审核", "复核", "提交", "确认", "分配", "占用", "锁定", "认可",
          "评审", "生效", "回签", "过账", "匹配", "补充", "修订", "标记", "置", "扣减", "认领", "内审", "答复", "决议", "转")


def classify_cmd(name):
    n = cc.base_cmd(name)
    rev = any(w in n for w in REVERSE)
    if any(w in n for w in END):
        return "D", rev
    if any(w in n for w in CREATE):
        return "C", rev
    if any(w in n for w in UPDATE):
        return "U", rev
    return "?", rev


def load_catalog():
    cat = {}
    for line in INFO_MAP.read_text(encoding="utf-8").splitlines():
        m = re.match(r"^\|\s*(OBJ-\d+)\s*\|", line)
        if not m or m.group(1) in cat:
            continue
        r = cc.cells(line)
        cat[m.group(1)] = {"name": r[1], "non": ("非对象" in r[2] or "行号保留" in r[2] or "非对象" in r[3]) if len(r) > 3 else False}
    return cat


def vs_names():
    names = {}

    def walk(x):
        if isinstance(x, dict):
            i = str(x.get("id", ""))
            if re.fullmatch(r"VS-\d+", i) and x.get("name"):
                names[i] = x["name"]
            for y in x.values():
                walk(y)
        elif isinstance(x, list):
            for y in x:
                walk(y)
    walk(yaml.safe_load(VS_FILE.read_text(encoding="utf-8")))
    return names


def unit_vs(u):
    st = u.get("stages") or []
    m = re.match(r"(VS-\d+)", str(st[0])) if st else None
    return m.group(1) if m else "未标阶段"


def analyse(docs, aggs, new_aggs, cat, matrix=None, machines=None):
    """Pure analysis; returns dict with rows, issues (item -> [(vs, text)])."""
    all_ids = set(aggs) | set(new_aggs)
    key_of = {}
    for a, v in aggs.items():
        key_of[a] = v["objs"][0] if len(v["objs"]) >= 1 else a
    for a in new_aggs:
        key_of.setdefault(a, a)
    touch = collections.defaultdict(lambda: collections.defaultdict(set))   # key -> unit -> letters
    unit_info = {}
    cmdletters = collections.defaultdict(lambda: collections.defaultdict(set))  # key -> letter -> {cmd}
    rev_cmds = collections.defaultdict(set)
    writers = collections.defaultdict(lambda: collections.defaultdict(set))   # key -> bc -> {units}
    first_vs = {}
    issues = collections.defaultdict(list)
    seen_aggs = set()
    wcmds = collections.defaultdict(set)   # aggregate -> commands of writing steps
    agg_states = collections.defaultdict(list)   # aggregate -> [(unit, states, transitions)]

    def vs_of(key):
        return first_vs.get(key, "未标阶段")

    for g, doc in docs.items():
        for _k, u in cc.units_of(doc):
            uid, vs, owner = u["id"], unit_vs(u), u.get("owner_bc")
            unit_info[uid] = (g, vs, u.get("name", ""))
            if isinstance(u.get("aggregates"), list) and len(u["aggregates"]) > 1 and u.get("commands"):
                issues["3 单聚合维护含多个聚合"].append((vs, f"{uid}：{u['aggregates']}"))
            if u.get("aggregate") and (u.get("states") or u.get("transitions")):
                agg_states[u["aggregate"]].append((uid, list(u.get("states") or []), list(u.get("transitions") or [])))
            steps = cc.unit_steps(u)
            for c in u.get("commands") or []:
                cmd = c.get("command") if isinstance(c, dict) else c
                if u.get("aggregate") and cmd:
                    steps = steps + [{"aggregate": u["aggregate"], "command": cmd, "actor": "x", "where": "in_system", "_unitcmd": True}]
            for n, s in enumerate(steps, 1):
                kind = cc.classify(s, u)
                refs = []
                for a in ([s["aggregate"]] if isinstance(s.get("aggregate"), str) else (s.get("aggregate") or [])) if s.get("aggregate") else []:
                    refs.append((a, s.get("command")))
                for x in s.get("inner") or []:
                    if x.get("aggregate"):
                        refs.append((x["aggregate"], x.get("command")))
                if isinstance(s.get("aggregate"), list) or s.get("aggregates"):
                    issues["3 步骤同时改多个聚合"].append((vs, f"{uid} 第{n}步 {s.get('command')}：{s.get('aggregate') or s.get('aggregates')}"))
                if s.get("command") and re.search(r"[、并及]", cc.base_cmd(s["command"])) and kind == "command":
                    issues["3 步骤同时改多个聚合"].append((vs, f"{uid} 第{n}步 命令名含并列：{cc.base_cmd(s['command'])}"))
                for a in list(s.get("reads") or []) + list(s.get("also") or []):
                    if isinstance(a, str):
                        refs.append((a, None))
                for a, cmd in refs:
                    if not isinstance(a, str):
                        continue
                    seen_aggs.add(a)
                    key = key_of.get(a, a)
                    first_vs.setdefault(key, vs) if vs != "未标阶段" else first_vs.setdefault(key, vs)
                    if cmd is None or kind in ("read", "platform"):
                        if kind != "platform":
                            touch[key][uid].add("R")
                        continue
                    if cc.MISSING in str(cmd):
                        touch[key][uid].add("?")
                        continue
                    L, rv = classify_cmd(cmd)
                    touch[key][uid].add(L)
                    cmdletters[key][L].add(cc.base_cmd(cmd))
                    if rv:
                        rev_cmds[key].add(cc.base_cmd(cmd))
                    wcmds[a].add(cc.base_cmd(cmd))
                    bc = s.get("bc") or owner
                    if bc:
                        writers[key][str(bc)].add(f"{uid}")
    # 1 catalogue
    for a in sorted(seen_aggs):
        vs = "未标阶段"
        if a not in all_ids:
            issues["1 聚合不在聚合稿"].append((vs_of(key_of.get(a, a)), a))
            continue
        for o in aggs.get(a, {}).get("objs", []):
            if o not in cat:
                issues["1 对象不在对象目录"].append((vs_of(key_of[a]), f"{a} → {o}"))
            elif cat[o]["non"]:
                issues["1 对象已是非对象"].append((vs_of(key_of[a]), f"{a} → {o} {cat[o]['name']}"))
        if a in new_aggs and a not in aggs:
            issues["1 聚合是新增聚合，无对象对应"].append((vs_of(key_of[a]), f"{a} {new_aggs[a]['name']}"))
    # 2, 6
    names = {}
    members = collections.defaultdict(list)
    for a in all_ids:
        members[key_of.get(a, a)].append(a)
    for k, ms in members.items():
        names[k] = cat[k]["name"] if k in cat else (aggs.get(ms[0], {}).get("name") or new_aggs.get(ms[0], {}).get("name") or k)
    draft_cmds = collections.defaultdict(lambda: collections.defaultdict(set))
    for a, v in aggs.items():
        for c in v["commands"]:
            L, rv = classify_cmd(c)
            draft_cmds[key_of[a]][L].add(c)
            if rv:
                draft_cmds[key_of[a]]["rev"].add(c)
    for k in sorted(touch):
        if k not in members:
            continue
        letters = set().union(*touch[k].values())
        label = f"{k} {names.get(k, '')}（{'、'.join(sorted(members.get(k, [])))}）"
        for L, txt in (("C", "无人创建"), ("U", "无人修改"), ("D", "无人结束或归档")):
            if L not in letters:
                hint = sorted(draft_cmds[k][L])[:3]
                issues[f"2 对象{txt}"].append((vs_of(k), label + (f"；聚合稿有命令未被流程用：{'、'.join(hint)}" if hint else "")))
        if "D" not in letters:
            issues["6 生命周期缺结束或归档"].append((vs_of(k), label))
        if not rev_cmds.get(k):
            hint = sorted(draft_cmds[k]["rev"])[:3]
            issues["6 生命周期缺冲销撤回类逆向"].append((vs_of(k), label + (f"；聚合稿有：{'、'.join(hint)}" if hint else "")))
    for k, ms in sorted(members.items()):
        if k in touch:
            continue
        if k.startswith("OBJ-") and k in cat and cat[k]["non"]:
            continue
        issues["2 对象未被任何流程触及（缺流程，或应为参考数据或属性）"].append(("未触及", f"{k} {names.get(k, '')}（{'、'.join(sorted(ms))}）"))
    for k, c in sorted(cat.items()):
        if not c["non"] and k not in members:
            issues["2 对象无聚合承载（缺流程，或应为参考数据或属性）"].append(("未触及", f"{k} {c['name']}"))
    # 3b state machines
    src = {a: list(v) for a, v in agg_states.items()}
    for a, m in (machines or {}).items():
        src.setdefault(a, []).append(("state-machines.yaml", [s["name"] if isinstance(s, dict) else s for s in m.get("states") or []], m.get("transitions") or []))
    for a, lst in sorted(src.items()):
        states, edges, termin, nonterm = set(), set(), {}, {}
        for uid, sts, trs in lst:
            local_states = {x.get('name') if isinstance(x, dict) else x for x in sts}
            outgoing = set()
            for t in trs:
                for f in ([t.get("from")] if not isinstance(t.get("from"), list) else t["from"]):
                    if f:
                        edges.add((f, t.get("to")))
                        outgoing.add(f)
                        local_states.add(f)
                local_states.add(t.get("to"))
            states |= {x for x in local_states if x}
            for st_ in local_states:
                (nonterm if st_ in outgoing else termin).setdefault(st_, uid)
        parent = {s: s for s in states}

        def find(x):
            while parent[x] != x:
                parent[x] = parent[parent[x]]
                x = parent[x]
            return x
        for f, t in edges:
            if f in parent and t in parent:
                parent[find(f)] = find(t)
        comps = collections.defaultdict(list)
        for s_ in states:
            comps[find(s_)].append(s_)
        vs = vs_of(key_of.get(a, a))
        if len(comps) > 1:
            issues["3 状态集合合不成一张状态机（无转换相连）"].append((vs, f"{a}：{sorted(sorted(c) for c in comps.values())}"))
        norm = collections.defaultdict(set)
        for s_ in states:
            norm[re.sub(r"^[已待]", "", s_)].add(s_)
        for nm, grp in norm.items():
            if len(grp) > 1:
                issues["3 状态名冲突"].append((vs, f"{a}：{sorted(grp)} 疑为同一状态的两种叫法"))
        for s_ in states:
            if s_ in termin and s_ in nonterm and termin[s_] != nonterm[s_]:
                issues["3 状态名冲突"].append((vs, f"{a}：「{s_}」在 {termin[s_]} 为终点、在 {nonterm[s_]} 有出转换"))
    # 4 writers
    for k, bcs in sorted(writers.items()):
        home = {aggs[a]["bc"] for a in members.get(k, []) if a in aggs}
        bset = set(bcs)
        if len(bset | home) > 1 and len({b for b in bset | home if not b.startswith("platform")}) > 1:
            issues["4 多个 BC 写同一对象"].append((vs_of(k), f"{k} {names.get(k, '')}：写入方 {', '.join(f'{b}（{len(u)} 流程）' for b, u in sorted(bcs.items()))}；聚合稿 BC {sorted(home)}"))
    # 5 matrix
    if matrix is not None:
        for a, cs in sorted(wcmds.items()):
            for c in sorted(cs):
                e = matrix.get((a, c))
                A = (e or {}).get("raci", {}).get("A")
                if e is None:
                    issues["5 权责表无此对象×命令"].append((vs_of(key_of.get(a, a)), f"{a} {c}"))
                elif not A or A.get("resolve") == "TODO":
                    issues["5 权责表 A 未定"].append((vs_of(key_of.get(a, a)), f"{a} {c}"))
    return {"touch": touch, "names": names, "members": members, "issues": issues, "unit_info": unit_info, "first_vs": first_vs}


def render_matrix(R, vsn):
    touch, names, members, ui = R["touch"], R["names"], R["members"], R["unit_info"]
    by_vs = collections.defaultdict(lambda: collections.defaultdict(dict))   # vs -> key -> {unit: letters}
    for k, us in touch.items():
        for uid, L in us.items():
            by_vs[ui[uid][1]][k][uid] = L
    out = ["# 业务对象 × 流程 CRUD 矩阵（object_check.py 自动生成，勿手改）", "",
           "格：C 创建、U 修改、D 结束或归档（含冲销撤回类）、R 只读、? 命令名判不出；按命令名关键词判定，只作线索。对象行取聚合稿 §2.2 的 OBJ-NN，无 OBJ 的聚合自成一行。", ""]
    iss = R["issues"]
    out += ["## 问题汇总（按价值流）", ""]
    vss = sorted({v for lst in iss.values() for v, _ in lst}, key=lambda x: (x == "未标阶段" or x == "未触及", x))
    items = sorted(iss)
    out.append("| 价值流 | " + " | ".join(i.split(" ", 1)[1].split("（")[0] + f"（{i[0]}）" for i in items) + " |")
    out.append("|---|" + "---|" * len(items))
    for v in vss:
        out.append(f"| {v} {vsn.get(v, '')} | " + " | ".join(str(sum(1 for x, _ in iss[i] if x == v)) for i in items) + " |")
    out.append("")
    for v in vss:
        out.append(f"### {v} {vsn.get(v, '')}")
        for i in items:
            rows = [t for x, t in iss[i] if x == v]
            if rows:
                out.append(f"- **{i}**（{len(rows)}）")
                out += [f"  - {t}" for t in rows]
        out.append("")
    out += ["## 矩阵", ""]
    for v in sorted(by_vs, key=lambda x: (x == "未标阶段", x)):
        units = sorted({u for k in by_vs[v] for u in by_vs[v][k]}, key=lambda x: int(re.sub(r"\D", "", x) or 0))
        out.append(f"### {v} {vsn.get(v, '')}")
        for i in range(0, len(units), 12):
            cols = units[i:i + 12]
            out.append("")
            out.append("| 对象 | " + " | ".join(cols) + " |")
            out.append("|---|" + "---|" * len(cols))
            for k in sorted(by_vs[v], key=lambda x: (x[:3], int(re.sub(r"\D", "", x) or 0))):
                cells_ = ["".join(sorted(by_vs[v][k].get(u, ""), key=lambda c: "CUD?R".index(c))) for u in cols]
                if any(cells_):
                    out.append(f"| {k} {names.get(k, '')} | " + " | ".join(cells_) + " |")
        out.append("")
    return "\n".join(out) + "\n"


def summary(R):
    iss = R["issues"]
    lines = [f"对象行 {len(R['touch'])} 个被流程触及；问题 {sum(len(v) for v in iss.values())} 条"]
    for i in sorted(iss):
        lines.append(f"- {i}：{len(iss[i])}")
    return "\n".join(lines)


SAMPLE_AGGS = {
    "AGG-1": {"bc": "B-01", "name": "报销", "objs": ["OBJ-1"], "commands": ["提交报销", "关闭报销", "冲销报销"]},
    "AGG-2": {"bc": "B-02", "name": "付款", "objs": ["OBJ-2"], "commands": ["发起付款"]},
    "AGG-3": {"bc": "B-03", "name": "孤立", "objs": ["OBJ-9"], "commands": []},
}
SAMPLE_CAT = {"OBJ-1": {"name": "报销单", "non": False}, "OBJ-2": {"name": "付款", "non": False}, "OBJ-5": {"name": "无人用", "non": False},
              "OBJ-6": {"name": "已并入", "non": True}}
SAMPLE_DOCS = {"g": {"processes": [{"id": "PRC-90", "stages": ["VS-08.1"], "owner_bc": "B-01", "steps": [
    {"actor": "SH-E1", "where": "in_system", "aggregate": "AGG-1", "command": "登记报销"},
    {"actor": "SH-E1", "where": "in_system", "aggregate": "AGG-1", "command": "批准报销"},
    {"actor": "SH-E1", "where": "in_system", "aggregate": "AGG-2", "command": "发起付款", "bc": "B-07"},
    {"actor": "SH-E1", "where": "in_system", "aggregate": "AGG-2", "command": "冲销并重过账"},
    {"actor": "SH-E1", "where": "in_system", "aggregate": "AGG-4", "command": "未知聚合命令"}]}],
    "single_aggregate": [
        {"id": "PRC-91", "stages": ["VS-08.1"], "aggregate": "AGG-1", "owner_bc": "B-01", "states": ["草稿", "已提交", "已关闭"],
         "transitions": [{"from": None, "to": "草稿"}, {"from": "草稿", "to": "已提交"}]},
        {"id": "PRC-92", "stages": ["VS-08.1"], "aggregate": "AGG-1", "owner_bc": "B-01", "states": ["提交", "关闭"],
         "transitions": [{"from": None, "to": "提交"}, {"from": "已提交", "to": "关闭"}]}]}}


def self_test():
    R = analyse(SAMPLE_DOCS, SAMPLE_AGGS, {}, SAMPLE_CAT, matrix={("AGG-1", "登记报销"): {"raci": {"A": {"resolve": "instance"}}}})
    iss = {k: sorted(t for _v, t in v) for k, v in R["issues"].items()}
    t = R["touch"]
    exp_ok = [
        t["OBJ-1"]["PRC-90"] == {"C", "U"},
        t["OBJ-2"]["PRC-90"] == {"C", "D"},
        iss.get("1 聚合不在聚合稿") == ["AGG-4"],
        "1 对象不在对象目录" not in iss,
        "4 多个 BC 写同一对象" in iss and "OBJ-2" in iss["4 多个 BC 写同一对象"][0],
        iss.get("3 步骤同时改多个聚合") == ["PRC-90 第4步 命令名含并列：冲销并重过账"],
        any("OBJ-1" in x for x in iss.get("2 对象无人结束或归档", [])),
        not any("OBJ-2" in x for x in iss.get("2 对象无人创建", [])),
        iss.get("2 对象无聚合承载（缺流程，或应为参考数据或属性）") == ["OBJ-5 无人用"],
        any("OBJ-1" in x for x in iss.get("6 生命周期缺冲销撤回类逆向", [])),
        not any("OBJ-2" in x for x in iss.get("6 生命周期缺冲销撤回类逆向", [])),
        len(iss.get("3 状态名冲突", [])) >= 1,
        iss.get("5 权责表无此对象×命令") == ["AGG-1 批准报销", "AGG-2 冲销并重过账", "AGG-2 发起付款", "AGG-4 未知聚合命令"],
    ]
    for i, c in enumerate(exp_ok, 1):
        print(f"自测 {i}: {'通过' if c else '失败'}")
        if not c:
            print("   ", {k: v for k, v in iss.items()})
    classes = {n: classify_cmd(n)[0] for n in ("登记报销", "提交申请", "关闭线索", "冲销报销", "拜访客户")}
    ok_cls = classes == {"登记报销": "C", "提交申请": "C", "关闭线索": "D", "冲销报销": "D", "拜访客户": "?"}
    print("命令分类:", "通过" if ok_cls else f"失败 {classes}")
    ok = all(exp_ok) and ok_cls
    print("自测", "通过" if ok else "失败")
    return 0 if ok else 1


def main(argv):
    if "--self-test" in argv:
        return self_test()
    local = "--local" in argv
    docs = {}
    for g in cc.GROUPS:
        t = cc.read_group_file(g, "proc", local)
        if t:
            docs[g] = yaml.safe_load(t)
    aggs = cc.load_aggregates()
    new_aggs, _p = cc.load_allocations()
    cat = load_catalog()
    mp = Path(argv[argv.index("--matrix") + 1]) if "--matrix" in argv else HERE / "RESPONSIBILITY_MATRIX.yaml"
    matrix = None
    if mp.exists():
        matrix = {(e["object"], cc.base_cmd(e["command"])): e for e in (yaml.safe_load(mp.read_text(encoding="utf-8")) or {}).get("entries", [])}
    machines = None
    sp = Path(argv[argv.index("--states") + 1]) if "--states" in argv else HERE / "state-machines.yaml"
    if sp.exists():
        machines = {m["aggregate"]: m for m in (yaml.safe_load(sp.read_text(encoding="utf-8")) or {}).get("machines", [])}
    R = analyse(docs, aggs, new_aggs, cat, matrix, machines)
    print(f"对象目录 {len(cat)} 行（有效 {sum(1 for c in cat.values() if not c['non'])}）；聚合稿聚合 {len(aggs)}；权责表：{'有' if matrix is not None else '无，第 5 项跳过'}")
    print(summary(R))
    if "--write" in argv:
        (HERE / "CRUD_MATRIX.md").write_text(render_matrix(R, vs_names()), encoding="utf-8")
        print("已写 CRUD_MATRIX.md")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
