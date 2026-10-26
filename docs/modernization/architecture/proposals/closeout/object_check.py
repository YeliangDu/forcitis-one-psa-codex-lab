"""Business object x process/command CRUD matrix from the group skeletons, plus six consistency checks (report only).

Usage: python3 -I object_check.py [--local] [--write] [--exemptions PATH] [--matrix PATH] [--states PATH]... [--commands PATH]...   |   --self-test
  skeletons: processes.<group>.yaml from origin/claude/one-arch-<group>-closeout (or closeout/<group>/ with --local)
  objects:   OBJ-NN in the aggregate name cell of ENTERPRISE_AGGREGATES_DRAFT.md section 2.2; catalogue = table rows of ENTERPRISE_INFO_MAP.md
             (rows whose definition says 非对象 / 行号保留 are not objects); an aggregate without OBJ-NN is its own row
  states:    state-machines.yaml of each group (converge_check.py format; --state PATH repeatable, default: each group's file, or closeout/state-machines.yaml)
  commands:  commands.yaml of each group (--commands PATH repeatable); entries whose unused_ok says they are fired by an on_reject / withdraw / 驳回 / 撤回
             edge count as reverse
  exemptions: OBJECT_EXEMPTIONS.yaml (--exemptions PATH; absent = empty, noted on the first report line); entries {check, object, detail?, reason, ruling};
             an issue whose check text equals `check`, whose text names `object` and contains `detail` is exempted: not counted, listed separately;
             an entry without reason or ruling is an error and does not apply; an entry matching nothing is reported as stale
  --write:   write CRUD_MATRIX.md (sections by value stream: VS-NN of the first stage of the process) next to this script
  --objects-yaml: write process-objects.yaml next to this script: PRC -> [{obj, name, aggregates, ops, proposed_only}] from the same matrix
             (obj = the aggregate's first OBJ-NN, or the aggregate id when it has none; ops in CUD?R order; proposed_only = letters only from 〔缺〕)

Command class (per aggregate x command), in this order:
  1 state machine of the aggregate, when it has a transition of that command: from null = C (create); to a terminal state from another state = D (end,
    but not when the same command also creates: that is a successor replacing the old version, counted U); kind non_state = U; kind reverse (or reverse_of
    with a command) = reverse; other transitions out of an initial 草稿/草案 state = C; anything else = U.
  2 otherwise by the verb at the start of the command name (end words, then change words, then create words; UPDATE before CREATE), then by a word
    contained in the name (two characters or more). Reverse words (冲销 红冲 撤销 撤回 作废 回退 更正 释放 取消 驳回 退回 废止) are D and reverse;
    启用 恢复 重新生成 are reverse and U. Prefix 〔缺〕 (proposed command) is stripped first; its cells carry a * in the matrix. Not found: "?".
A step with `read`/`reads` marks R. Step approval.on_reject and step withdraw (target = the aggregate of the gated command), and catalogue commands
whose unused_ok names an on_reject / withdraw edge, count as reverse.
Checks: 1 aggregate or object not in the catalogue (or already a non-object); 2 object lacking creation / change / end in any process (or not touched
at all: missing process, or should be reference data or an attribute); 3 one step changing several aggregates beyond inner/also (list aggregate, `aggregates`,
compound command name unless the name is in the aggregate draft's command column, unit with several aggregates) and aggregate state sets of different units
that do not form one machine (several components after joining states by transitions and by shared command names; components holding a kind: entry
target are sub-lifecycles and ignored) or names differing only by 已/待 prefix with no transition between them, or terminal in one unit and not in another;
4 several BCs writing one object (step.bc or the process owner_bc, against the aggregate's BC; objects of an aggregate marked 一拆多 skipped); 5 object x
command without an A in RESPONSIBILITY_MATRIX.yaml (skipped when absent); 6 lifecycle: no end/archive, no reverse command; 7 machine marked stateless or
perpetual without a reason. Aggregates with stateless: true or perpetual: true (with reason) are exempt from the end checks (2 and 6 end). Exit code 0
(1 for --self-test failure).
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
INFO_MAP = cc.PROPOSALS.parent / "ssot" / "10-objects" / "ENTERPRISE_INFO_MAP.md"
VS_FILE = cc.PROPOSALS.parent / "ssot" / "05-value-streams" / "value-streams.yaml"

REVERSE = ("冲销", "红冲", "撤销", "撤回", "作废", "回退", "更正", "释放", "取消", "驳回", "退回", "废止")
REVERSE_ONLY = ("启用", "恢复", "重新生成")
END = ("关闭", "结束", "终止", "归档", "结项", "结清", "停用", "注销", "完成", "办结", "竣工", "关账", "封账", "核销", "结转", "退还", "没收",
       "减员", "锁定", "归还", "离职") + REVERSE
CREATE = ("登记", "新建", "创建", "建立", "开立", "发起", "计划", "编制", "立项", "生成", "导入", "录入", "起草", "申请", "设立", "开具", "发布", "制单", "入职", "办理",
          "增员", "草拟", "计算")
UPDATE = ("修改", "变更", "更新", "调整", "维护", "批准", "通过", "审批", "核定", "审核", "复核", "提交", "确认", "分配", "占用", "锁定", "认可",
          "评审", "生效", "回签", "过账", "匹配", "补充", "修订", "标记", "置", "扣减", "认领", "内审", "答复", "决议", "转",
          "更名", "补卡", "指定") + REVERSE_ONLY
DRAFT_STATE = re.compile(r"(^|:)(草稿|草案)$")
BRANCH_EDGE = re.compile(r"on_reject|withdraw|驳回|撤回")


def classify_cmd(name):
    n = cc.base_cmd(name)
    for at_start in (True, False):
        for letter, words in (("D", END), ("U", UPDATE), ("C", CREATE)):
            for w in words:
                if (n.startswith(w) if at_start else (len(w) > 1 and w in n)):
                    if w in REVERSE_ONLY:
                        return "U", True
                    return letter, w in REVERSE
    return "?", False


def froms(t):
    f = t.get("from")
    return list(f) if isinstance(f, list) else [f]


def prep_machine(m):
    sts = [s["name"] if isinstance(s, dict) else s for s in m.get("states") or []]
    trs = [t for t in m.get("transitions") or [] if isinstance(t, dict)]
    term = {s["name"] for s in m.get("states") or [] if isinstance(s, dict) and s.get("terminal")}
    if trs:
        out = {f for t in trs for f in froms(t) if f and f != t.get("to")}
        term |= {s for s in sts + [t.get("to") for t in trs] if s and s not in out}
    initial = m.get("initial") or (sts[0] if sts else None)
    by_cmd = collections.defaultdict(list)
    for t in trs:
        if t.get("command"):
            by_cmd[cc.base_cmd(t["command"])].append(t)
    null_to = {t.get("to") for t in trs if None in froms(t) and t.get("kind") != "non_state"}
    draft = {t.get("to") for t in trs if None in froms(t) and DRAFT_STATE.search(str(t.get("to")))}
    if DRAFT_STATE.search(str(initial)):
        draft.add(initial)
    entry_cmd = any(t.get("command") and None in froms(t) and t.get("to") in draft for t in trs)
    implicit = set()   # commandless (event-fired) transitions: into a terminal = D, others = U
    for t in trs:
        if t.get("command") or t.get("kind") == "non_state" or None in froms(t):
            continue
        to = t.get("to")
        for f in froms(t):
            if f != to:
                implicit.add("D" if to in term and f not in term else "U")
    return {"entry_cmd": entry_cmd, "implicit": implicit, "initial": initial, "null_to": null_to, "term": term, "draft": draft, "by_cmd": by_cmd,
            "stateless": bool(m.get("stateless")), "perpetual": bool(m.get("perpetual")), "reason": m.get("reason")}


def machine_class(pm, cmd):
    ts = pm["by_cmd"].get(cmd)
    if not ts:
        return None
    letters, rev = set(), False
    creates = any(None in froms(t) for t in ts if t.get("kind") != "non_state")
    for t in ts:
        k, to, fr = t.get("kind"), t.get("to"), froms(t)
        if fr and all(f == to for f in fr):   # self-loop: state unchanged, a modification only
            if k == "reverse" or t.get("reverse_of"):
                rev = True
            letters.add("U")
            continue
        fr = [f for f in fr if f != to or f is None]
        if k == "reverse" or t.get("reverse_of"):
            rev = True
        if k == "non_state":
            letters.add("U")
        elif None in fr:
            letters.add("C" if to == pm["initial"] or pm["initial"] not in pm["null_to"] else "U")
        elif to in pm["term"] and to not in fr:
            letters.add("U" if creates else "D")
        elif pm["draft"] & set(fr) and to not in pm["draft"] and not pm["entry_cmd"]:
            letters.add("C")
        else:
            letters.add("U")
    return letters, rev


def load_catalog():
    cat = {}
    for line in INFO_MAP.read_text(encoding="utf-8").splitlines():
        m = re.match(r"^\|\s*(OBJ-\d+)\s*\|", line)
        if not m or m.group(1) in cat:
            continue
        r = cc.cells(line)
        cat[m.group(1)] = {"name": r[1], "non": is_non_object(r)}
    return cat


def is_non_object(r):
    """Catalogue row cells -> already a non-object: definition keeps only the row number, or the level cell itself starts with 非对象 (a mention elsewhere does not count)."""
    return len(r) > 3 and ("行号保留" in r[2] or r[3].lstrip("*· ").startswith("非对象"))


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


def analyse(docs, aggs, new_aggs, cat, matrix=None, machines=None, catalog_cmds=None):
    """Pure analysis; returns dict with rows, issues (item -> [(vs, text)])."""
    pms = {a: prep_machine(m) for a, m in (machines or {}).items()}
    draft_names = {a: set(v["commands"]) for a, v in aggs.items()}

    def cmd_class(a, cmd):
        n = cc.base_cmd(cmd)
        r = machine_class(pms[a], n) if a in pms else None
        by_name = classify_cmd(n)
        return (r[0], r[1] or by_name[1]) if r else ({by_name[0]}, by_name[1])

    all_ids = set(aggs) | set(new_aggs)
    key_of = {}
    for a, v in aggs.items():
        key_of[a] = v["objs"][0] if len(v["objs"]) >= 1 else a
    for a in new_aggs:
        key_of.setdefault(a, a)
    touch = collections.defaultdict(lambda: collections.defaultdict(set))   # key -> unit -> letters
    firm = collections.defaultdict(lambda: collections.defaultdict(set))   # same, without proposed (〔缺〕) commands
    touched_aggs = collections.defaultdict(set)
    unit_info = {}
    cmdletters = collections.defaultdict(lambda: collections.defaultdict(set))  # key -> letter -> {cmd}
    rev_cmds = collections.defaultdict(set)
    writers = collections.defaultdict(lambda: collections.defaultdict(set))   # key -> bc -> {units}
    first_vs = {}
    issues = collections.defaultdict(list)
    seen_aggs = set()
    agg_units = collections.defaultdict(set)   # aggregate -> units that change it with a command
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
            if u.get("reads"):   # 读模型等单元级 reads：只读
                steps = steps + [{"reads": [x for x in u["reads"] if isinstance(x, str)], "actor": "x", "where": "in_system"}]
            seen_uc, uc_aggs = set(), set()
            for c in (u.get("commands") or []) + (u.get("transitions") or []):
                cmd = c.get("command") if isinstance(c, dict) else c
                agg = (c.get("aggregate") if isinstance(c, dict) else None) or u.get("aggregate")
                if agg and cmd and (agg, cc.base_cmd(cmd)) not in seen_uc:
                    seen_uc.add((agg, cc.base_cmd(cmd)))
                    uc_aggs.add(agg)
                    st = {"aggregate": agg, "command": cmd, "actor": "x", "where": "in_system", "_unitcmd": True}
                    if isinstance(c, dict) and isinstance(c.get("approval"), dict):
                        st["approval"] = c["approval"]
                    steps = steps + [st]
            if len(uc_aggs) > 1 and not (isinstance(u.get("aggregates"), list) and len(u["aggregates"]) > 1):
                issues["3 单聚合维护含多个聚合"].append((vs, f"{uid}：{sorted(uc_aggs)}"))
            for i, s in enumerate(steps):
                if (isinstance(s.get("approval"), dict) and s["approval"].get("on_reject") is not None) or s.get("withdraw"):
                    tgt = cc.gated_target(steps, i)
                    if tgt and isinstance(tgt[0], str):
                        rev_cmds[key_of.get(tgt[0], tgt[0])].add(f"{uid} 第{i + 1}步 驳回或撤回边")
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
                if (s.get("command") and re.search(r"[、并及]", cc.base_cmd(s["command"])) and kind == "command"
                        and not any(cc.base_cmd(s["command"]) in draft_names.get(x, ()) for x, _c in refs)):
                    issues["3 步骤同时改多个聚合"].append((vs, f"{uid} 第{n}步 命令名含并列：{cc.base_cmd(s['command'])}"))
                for a in list(s.get("reads") or []) + list(s.get("also") or []):
                    if isinstance(a, str):
                        refs.append((a, None))
                for a, cmd in refs:
                    if not isinstance(a, str):
                        continue
                    seen_aggs.add(a)
                    key = key_of.get(a, a)
                    touched_aggs[key].add(a)
                    first_vs.setdefault(key, vs) if vs != "未标阶段" else first_vs.setdefault(key, vs)
                    if cmd is None or kind in ("read", "platform"):
                        if kind != "platform":
                            touch[key][uid].add("R")
                        continue
                    Ls, rv = cmd_class(a, cmd)
                    touch[key][uid] |= Ls
                    if cc.MISSING not in str(cmd):
                        firm[key][uid] |= Ls
                    for L in Ls:
                        cmdletters[key][L].add(cc.base_cmd(cmd))
                    if rv:
                        rev_cmds[key].add(cc.base_cmd(cmd))
                    if cc.MISSING in str(cmd):
                        continue
                    agg_units[a].add(uid)
                    wcmds[a].add(cc.base_cmd(cmd))
                    bc = s.get("bc") or owner
                    home_bc = aggs.get(a, {}).get("bc")
                    via_api = [x for x in s.get("cross_bc") or [] if isinstance(x, dict) and x.get("mode") == "api" and x.get("direction") == "out"]
                    if home_bc and via_api and (any(x.get("bc") == home_bc for x in via_api) or (bc and bc != home_bc)):
                        bc = home_bc   # orchestrator calls the owning BC's command through its api: the write belongs to the owner
                    if bc:
                        writers[key][str(bc)].add(f"{uid}")
    for a, pm in pms.items():
        if pm["implicit"] and a in agg_units:
            for uid in agg_units[a]:
                touch[key_of.get(a, a)][uid] |= pm["implicit"]
                firm[key_of.get(a, a)][uid] |= pm["implicit"]
                for L in pm["implicit"]:
                    cmdletters[key_of.get(a, a)][L].add("（状态机事件触发转换）")
    for a, cmd, why in catalog_cmds or []:
        if a in all_ids and BRANCH_EDGE.search(why) and ("触发" in why or "回调" in why):
            rev_cmds[key_of.get(a, a)].add(f"{cc.base_cmd(cmd)}（目录 unused_ok）")
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
        exempt = all(a in pms and (pms[a]["stateless"] or pms[a]["perpetual"]) and pms[a]["reason"] for a in touched_aggs[k])
        for L, txt in (("C", "无人创建"), ("U", "无人修改"), ("D", "无人结束或归档")):
            if L == "D" and exempt:
                continue
            if L not in letters:
                hint = sorted(draft_cmds[k][L])[:3]
                issues[f"2 对象{txt}"].append((vs_of(k), label + (f"；聚合稿有命令未被流程用：{'、'.join(hint)}" if hint else "")))
        if "D" not in letters and not exempt:
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
    inner_carried = {o: a for a, v in aggs.items() for o in v.get("inner_objs", [])}   # secondary objects held inside an aggregate (内含 column)
    for k, c in sorted(cat.items()):
        if not c["non"] and k not in members and k not in inner_carried:
            issues["2 对象无聚合承载（缺流程，或应为参考数据或属性）"].append(("未触及", f"{k} {c['name']}"))
    # 3b state machines
    src = {a: list(v) for a, v in agg_states.items()}
    for a, m in (machines or {}).items():
        src.setdefault(a, []).append(("state-machines.yaml", [s["name"] if isinstance(s, dict) else s for s in m.get("states") or []], m.get("transitions") or []))
    for a, pm in sorted(pms.items()):
        if (pm["stateless"] or pm["perpetual"]) and not pm["reason"] and a in seen_aggs:
            issues["7 机器标 stateless 或 perpetual 却没写理由"].append((vs_of(key_of.get(a, a)), a))
    for a, lst in sorted(src.items()):
        states, edges, termin, nonterm, by_cmd, entry_to = set(), set(), {}, {}, collections.defaultdict(set), set()
        for uid, sts, trs in lst:
            local_states = {x.get('name') if isinstance(x, dict) else x for x in sts}
            outgoing, outgoing_any = set(), set()
            for t in trs:
                for f in froms(t):
                    if f and f != "*":
                        edges.add((f, t.get("to")))
                        outgoing_any.add(f)
                        if f != t.get("to"):   # a self-loop leaves the state unchanged: not an outgoing transition
                            outgoing.add(f)
                        local_states.add(f)
                        if t.get("command"):
                            by_cmd[cc.base_cmd(t["command"])].add(f)
                local_states.add(t.get("to"))
                if t.get("command"):
                    by_cmd[cc.base_cmd(t["command"])].add(t.get("to"))
                if t.get("kind") == "entry":
                    entry_to.add(t.get("to"))
            states |= {x for x in local_states if x}
            for st_ in local_states:
                if st_ in outgoing:
                    nonterm.setdefault(st_, uid)
                if st_ not in outgoing_any:
                    termin.setdefault(st_, uid)
        parent = {s: s for s in states}

        def find(x):
            while parent[x] != x:
                parent[x] = parent[parent[x]]
                x = parent[x]
            return x
        for f, t in edges:
            if f in parent and t in parent:
                parent[find(f)] = find(t)
        for grp in by_cmd.values():
            grp = [x for x in grp if x in parent]
            for x in grp[1:]:
                parent[find(x)] = find(grp[0])
        comps = collections.defaultdict(list)
        for s_ in states:
            comps[find(s_)].append(s_)
        sub = {find(x) for x in entry_to if x in parent}
        loose = [c for r, c in comps.items() if r not in sub]
        vs = vs_of(key_of.get(a, a))
        if len(loose) > 1:
            issues["3 状态集合合不成一张状态机（无转换相连）"].append((vs, f"{a}：{sorted(sorted(c) for c in loose)}"))
        norm = collections.defaultdict(set)
        for s_ in states:
            norm[re.sub(r"^[已待]", "", s_)].add(s_)
        for nm, grp in norm.items():
            pairs = [(x, y) for x in sorted(grp) for y in sorted(grp) if x < y and (x, y) not in edges and (y, x) not in edges]
            if pairs:
                issues["3 状态名冲突"].append((vs, f"{a}：{sorted({x for p_ in pairs for x in p_})} 疑为同一状态的两种叫法（之间无转换）"))
        for s_ in states:
            if s_ in termin and s_ in nonterm and termin[s_] != nonterm[s_]:
                issues["3 状态名冲突"].append((vs, f"{a}：「{s_}」在 {termin[s_]} 为终点、在 {nonterm[s_]} 有出转换"))
    # 4 writers
    for k, bcs in sorted(writers.items()):
        home = {aggs[a]["bc"] for a in members.get(k, []) if a in aggs}
        bset = set(bcs)
        if any(aggs[a].get("kind") == "一拆多" for a in members.get(k, []) if a in aggs):
            continue
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
    return {"touch": touch, "firm": firm, "names": names, "members": members, "issues": issues, "unit_info": unit_info, "first_vs": first_vs}


def load_exemptions(path):
    if not path.exists():
        return [], False
    return (yaml.safe_load(path.read_text(encoding="utf-8")) or {}).get("exemptions") or [], True


def apply_exemptions(R, entries):
    """Move matched issues out of R["issues"] into R["exempted"] [(check, vs, text, reason, ruling)]; fills R["exempt_errors"], R["stale"]."""
    errors, exempted, valid = [], [], []
    for n, e in enumerate(entries, 1):
        e = e if isinstance(e, dict) else {}
        missing = [k for k in ("check", "object", "reason", "ruling") if not str(e.get(k) or "").strip()]
        if missing:
            errors.append(f"第{n}条豁免缺 {'、'.join(missing)}，不生效：{e}")
        else:
            valid.append(e)
    used = set()
    for check in list(R["issues"]):
        keep = []
        for vs, text in R["issues"][check]:
            hit = next((i for i, e in enumerate(valid) if e["check"] == check
                        and re.search(rf"(?<![\w-]){re.escape(e['object'])}(?!\d)", text) and str(e.get("detail") or "") in text), None)
            if hit is None:
                keep.append((vs, text))
            else:
                used.add(hit)
                exempted.append((check, vs, text, valid[hit]["reason"], valid[hit]["ruling"]))
        if keep:
            R["issues"][check] = keep
        else:
            del R["issues"][check]
    stale = [f"{e['check']} / {e['object']}" + (f" / {e['detail']}" if e.get("detail") else "") for i, e in enumerate(valid) if i not in used]
    R["exempted"], R["exempt_errors"], R["stale"] = exempted, errors, stale
    return R


def render_matrix(R, vsn):
    touch, names, members, ui = R["touch"], R["names"], R["members"], R["unit_info"]
    by_vs = collections.defaultdict(lambda: collections.defaultdict(dict))   # vs -> key -> {unit: letters}
    for k, us in touch.items():
        for uid, L in us.items():
            by_vs[ui[uid][1]][k][uid] = L
    out = ["# 业务对象 × 流程 CRUD 矩阵（object_check.py 自动生成，勿手改）", "",
           "格：C 创建、U 修改、D 结束或归档（含冲销撤回类）、R 只读、? 判不出、* 号表示该字母只来自提议命令（〔缺〕）；先按状态机、再按命令名动词判定，只作线索。对象行取聚合稿 §2.2 的 OBJ-NN，无 OBJ 的聚合自成一行。", ""]
    iss = R["issues"]
    out += ["## 问题汇总（按价值流）", ""]
    ex = R.get("exempted", [])
    vss = sorted({v for lst in iss.values() for v, _ in lst} | {e[1] for e in ex}, key=lambda x: (x == "未标阶段" or x == "未触及", x))
    items = sorted(iss)
    out.append("| 价值流 | " + " | ".join(i.split(" ", 1)[1].split("（")[0] + f"（{i[0]}）" for i in items) + " | 已豁免 |")
    out.append("|---|" + "---|" * (len(items) + 1))
    for v in vss:
        out.append(f"| {v} {vsn.get(v, '')} | " + " | ".join(str(sum(1 for x, _ in iss[i] if x == v)) for i in items) + f" | {sum(1 for e in ex if e[1] == v)} |")
    out.append("")
    for v in vss:
        out.append(f"### {v} {vsn.get(v, '')}")
        for i in items:
            rows = [t for x, t in iss[i] if x == v]
            if rows:
                out.append(f"- **{i}**（{len(rows)}）")
                out += [f"  - {t}" for t in rows]
        out.append("")
    if ex:
        out += [f"## 已豁免（{len(ex)}，不计入问题数）", ""]
        out += [f"- {c} / {v}：{t}（理由：{r}；裁定：{g}）" for c, v, t, r, g in ex]
        out.append("")
    if R.get("stale"):
        out += ["## 失效豁免（未匹配任何问题，请删除）", ""] + [f"- {x}" for x in R["stale"]] + [""]
    if R.get("exempt_errors"):
        out += ["## 豁免条目错误（不生效）", ""] + [f"- {x}" for x in R["exempt_errors"]] + [""]
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
                cells_ = ["".join(c + ("" if c in R["firm"][k][u] or c == "R" else "*") for c in sorted(by_vs[v][k].get(u, ""), key=lambda c: "CUD?R".index(c)))
                          for u in cols]
                if any(cells_):
                    out.append(f"| {k} {names.get(k, '')} | " + " | ".join(cells_) + " |")
        out.append("")
    return "\n".join(out) + "\n"


def summary(R):
    iss = R["issues"]
    lines = [f"对象行 {len(R['touch'])} 个被流程触及；问题 {sum(len(v) for v in iss.values())} 条；已豁免 {len(R.get('exempted', []))} 条"]
    for i in sorted(iss):
        lines.append(f"- {i}：{len(iss[i])}")
    lines += [f"- 失效豁免（请删除）：{x}" for x in R.get("stale", [])] + [f"- 豁免条目错误：{x}" for x in R.get("exempt_errors", [])]
    lines += [f"- 已豁免 {c}：{t}（{r}；{g}）" for c, _v, t, r, g in R.get("exempted", [])]
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


def _run(steps, aggs, machines=None, catalog_cmds=None, units=None, draft_cmds=None):
    doc = {"processes": [{"id": "PRC-1", "stages": ["VS-01.1"], "owner_bc": "B-01", "steps": steps}] + (units or [])}
    full = {a: {"bc": "B-01", "name": a, "objs": [f"OBJ-{a[-2:]}"], "commands": (draft_cmds or {}).get(a, []), "kind": "一对一"} for a in aggs}
    return analyse({"g": doc}, full, {}, {}, None, machines, catalog_cmds)


def _st(a, cmd, **kw):
    return dict({"actor": "SH-E1", "where": "in_system", "aggregate": a, "command": cmd}, **kw)


def self_test_more():
    results = []

    def check(name, cond, info=None):
        results.append(cond)
        print(f"自测 {name}: {'通过' if cond else '失败'}")
        if not cond and info is not None:
            print("   ", info)

    def iss(R, k):
        return sorted(t for _v, t in R["issues"].get(k, []))
    M = lambda a, states, trs, **kw: dict({"aggregate": a, "states": states, "transitions": trs}, **kw)
    # 1 proposed prefix is stripped, classified, and marked in the matrix
    R = _run([_st("AGG-01", "〔缺〕作废甲乙")], ["AGG-01"])
    check("〔缺〕前缀先剥掉再分类", R["touch"]["OBJ-01"]["PRC-1"] == {"D"} and not R["firm"]["OBJ-01"]["PRC-1"] and "无人结束" not in "".join(iss(R, "6 生命周期缺结束或归档")),
          R["touch"])
    check("提议命令在矩阵里标 *", "| OBJ-01 AGG-01 | D* |" in render_matrix(R, {}))
    # 2 state machine first
    m = M("AGG-02", ["草稿", "已提交", {"name": "已完结", "terminal": True}],
          [{"from": None, "to": "草稿", "command": "起甲"}, {"from": "草稿", "to": "已提交", "command": "送乙"}, {"from": "已提交", "to": "已完结", "command": "收丙"},
           {"from": "已提交", "to": "草稿", "command": "打丁", "kind": "reverse"}, {"from": "草稿", "to": "草稿", "command": "微戊", "kind": "non_state"}], initial="草稿")
    got = {c: machine_class(prep_machine(m), c) for c in ("起甲", "送乙", "收丙", "打丁", "微戊")}
    check("机器：from null 创建、入口有命令时草稿出向按修改、指向终态结束、reverse 逆向、non_state 修改",
          got == {"起甲": ({"C"}, False), "送乙": ({"U"}, False), "收丙": ({"D"}, False), "打丁": ({"U"}, True), "微戊": ({"U"}, False)}, got)
    R = _run([_st("AGG-02", c) for c in ("起甲", "收丙", "打丁", "微戊")], ["AGG-02"], {"AGG-02": m})
    check("机器优先：名字无词表词也分得出", R["touch"]["OBJ-02"]["PRC-1"] == {"C", "D", "U"} and not R["issues"].get("6 生命周期缺冲销撤回类逆向"), R["issues"])
    R = _run([_st("AGG-03", "批准用印申请"), _st("AGG-03", "提交申请")], ["AGG-03"])
    check("机器缺失退回按名字（动词在前，UPDATE 先于 CREATE）", R["touch"]["OBJ-03"]["PRC-1"] == {"U"}, R["touch"])
    repl = M("AGG-04", ["生效", {"name": "已结束", "terminal": True}], [{"from": None, "to": "生效", "command": "换版"}, {"from": "生效", "to": "已结束", "command": "换版"}])
    R = _run([_st("AGG-04", "换版")], ["AGG-04"], {"AGG-04": repl})
    check("同一命令既创建又结束旧版本，不算结束", any("OBJ-04" in x for x in iss(R, "2 对象无人结束或归档")), R["issues"])
    # 3 reverse edges
    R = _run([_st("AGG-05", "批准甲", approval={"engine": "platform.approval", "on_reject": 1}), _st("AGG-05", "登记乙")], ["AGG-05"])
    check("approval.on_reject 计入逆向", not any("OBJ-05" in x for x in iss(R, "6 生命周期缺冲销撤回类逆向")), R["issues"])
    R = _run([_st("AGG-06", "提交丙", withdraw={"to": "end"})], ["AGG-06"])
    check("withdraw 边计入逆向", not any("OBJ-06" in x for x in iss(R, "6 生命周期缺冲销撤回类逆向")), R["issues"])
    R = _run([_st("AGG-07", "提交丁")], ["AGG-07"], catalog_cmds=[("AGG-07", "收回丁", "由骨架 withdraw 边触发")])
    R2 = _run([_st("AGG-07", "提交丁")], ["AGG-07"], catalog_cmds=[("AGG-07", "收回丁", "维护用")])
    check("目录 unused_ok 写明由撤回边触发的命令计入逆向",
          not any("OBJ-07" in x for x in iss(R, "6 生命周期缺冲销撤回类逆向")) and any("OBJ-07" in x for x in iss(R2, "6 生命周期缺冲销撤回类逆向")))
    # 5 sub-lifecycles
    two = lambda extra: M("AGG-08", ["a1", "a2", "b1", "b2"], [{"from": None, "to": "a1", "command": "起A"}, {"from": "a1", "to": "a2", "command": "走A"},
                                                             {"from": None, "to": "b1", "command": "起B", **extra}, {"from": "b1", "to": "b2", "command": "走B"}])
    chk = lambda m: iss(_run([_st("AGG-08", "走A")], ["AGG-08"], {"AGG-08": m}), "3 状态集合合不成一张状态机（无转换相连）")
    shared = M("AGG-08", ["a1", "a2", "b1", "b2"], [{"from": "a1", "to": "a2", "command": "共"}, {"from": "b1", "to": "b2", "command": "共"}])
    entry = M("AGG-08", ["a1", "a2", "b1", "b2"], [{"from": None, "to": "a1", "command": "起A", "kind": "entry"}, {"from": "a1", "to": "a2", "command": "走A"},
                                                  {"from": None, "to": "b1", "command": "起B", "kind": "entry"}, {"from": "b1", "to": "b2", "command": "走B"}])
    check("合不成一张：无关联报，同名命令相连或有 entry 入口不报", len(chk(two({}))) == 1 and chk(shared) == [] and chk(entry) == [], (chk(two({})), chk(shared), chk(entry)))
    conflict = lambda trs: iss(_run([_st("AGG-09", "走")], ["AGG-09"], {"AGG-09": M("AGG-09", ["待申报", "已申报"], trs)}), "3 状态名冲突")
    check("状态名冲突只在两状态间无转换时报", conflict([{"from": "待申报", "to": "已申报", "command": "走"}]) == [] and len(conflict([{"from": None, "to": "待申报", "command": "走"}, {"from": None, "to": "已申报", "command": "走"}])) == 1)
    # 6 compound names, one-to-many
    R = _run([_st("AGG-10", "并入扣缴"), _st("AGG-10", "冲销并重过账")], ["AGG-10"], draft_cmds={"AGG-10": ["并入扣缴"]})
    check("并列判断对聚合稿命令列中已有的命令名跳过", iss(R, "3 步骤同时改多个聚合") == ["PRC-1 第2步 命令名含并列：冲销并重过账"], R["issues"])
    docs = {"g": {"processes": [{"id": "PRC-1", "stages": ["VS-01.1"], "owner_bc": "B-02", "steps": [_st("AGG-11", "登记甲")]}]}}
    one = {"AGG-11": {"bc": "B-01", "name": "x", "objs": ["OBJ-11"], "commands": [], "kind": "一拆多"}}
    many = {"AGG-11": dict(one["AGG-11"], kind="一对一")}
    check("一拆多的对象不报多个 BC 写入", not analyse(docs, one, {}, {}, None)["issues"].get("4 多个 BC 写同一对象") and len(analyse(docs, many, {}, {}, None)["issues"]["4 多个 BC 写同一对象"]) == 1)
    # 7 exemptions
    for flag in ("stateless", "perpetual"):
        mm = {"AGG-12": {"aggregate": "AGG-12", flag: True, "reason": "长期存在", "states": [], "transitions": []}}
        R = _run([_st("AGG-12", "登记甲")], ["AGG-12"], mm)
        mm2 = {"AGG-12": {"aggregate": "AGG-12", flag: True, "states": [], "transitions": []}}
        R2 = _run([_st("AGG-12", "登记甲")], ["AGG-12"], mm2)
        check(f"{flag} 且有 reason 豁免结束类检查；缺 reason 不豁免并报告",
              not R["issues"].get("6 生命周期缺结束或归档") and not R["issues"].get("2 对象无人结束或归档") and not R["issues"].get("7 机器标 stateless 或 perpetual 却没写理由")
              and len(R2["issues"]["6 生命周期缺结束或归档"]) == 1 and len(R2["issues"]["7 机器标 stateless 或 perpetual 却没写理由"]) == 1, (R["issues"], R2["issues"]))
    # 8 exemptions
    base = lambda: _run([_st("AGG-13", "登记甲")], ["AGG-13"])
    key = "6 生命周期缺结束或归档"
    ok_e = {"check": key, "object": "OBJ-13", "reason": "设计如此", "ruling": "S-43 第3条"}
    R = apply_exemptions(base(), [ok_e])
    check("豁免匹配生效：不计入问题、单列已豁免", key not in R["issues"] and len(R["exempted"]) == 1 and R["exempted"][0][3:] == ("设计如此", "S-43 第3条")
          and not R["stale"] and "已豁免 1 条" in summary(R), (R["issues"], R["exempted"]))
    check("豁免 object 不误匹配前缀（OBJ-1 不匹配 OBJ-13）", len(apply_exemptions(base(), [dict(ok_e, object="OBJ-1")])["issues"][key]) == 1)
    check("detail 限定到明细子串", len(apply_exemptions(base(), [dict(ok_e, detail="不存在")])["issues"][key]) == 1
          and not apply_exemptions(base(), [dict(ok_e, detail="AGG-13")])["issues"].get(key))
    for miss in ("reason", "ruling"):
        R = apply_exemptions(base(), [dict(ok_e, **{miss: " "})])
        check(f"缺 {miss} 不生效并报错", len(R["issues"][key]) == 1 and not R["exempted"] and len(R["exempt_errors"]) == 1, R["exempt_errors"])
    R = apply_exemptions(base(), [dict(ok_e, object="OBJ-99")])
    check("失效豁免列出", len(R["stale"]) == 1 and len(R["issues"][key]) == 1 and "失效豁免" in summary(R), R["stale"])
    # 单聚合维护：commands[] 与 transitions[] 条目都计入，条目 aggregate 优先，同一命令两处只计一次
    def su(**kw):
        return dict({"id": "PRC-7", "type": "single_aggregate", "stages": ["VS-01.1"], "owner_bc": "B-01", "aggregate": "AGG-01"}, **kw)
    R = _run([], ["AGG-01", "AGG-02"], units=[su(commands=[{"command": "登记甲", "aggregate": "AGG-02"}, "作废乙"],
                                                 transitions=[{"from": "草稿", "to": "终止", "command": "作废丙"}])])
    check("单聚合维护：条目 aggregate 优先、transitions 条目计入",
          R["touch"]["OBJ-02"]["PRC-7"] == {"C"} and R["touch"]["OBJ-01"]["PRC-7"] == {"D"}, dict(R["touch"]))
    R = _run([], ["AGG-01", "AGG-02"], units=[su(commands=[{"command": "登记甲", "aggregate": "AGG-02"}, "作废乙"],
                                                 transitions=[{"from": "草稿", "to": "终止", "command": "作废乙"}])])
    check("单聚合维护：同一命令两处只计一次", R["touch"]["OBJ-01"]["PRC-7"] == {"D"}, dict(R["touch"]))
    check("单聚合维护：条目 aggregate 不同于单元级、实际只一个聚合，不报多聚合",
          not iss(_run([], ["AGG-01", "AGG-02"], units=[su(commands=[{"command": "登记甲", "aggregate": "AGG-02"}])]), "3 单聚合维护含多个聚合"))
    check("单聚合维护：条目实际跨多个聚合才报",
          len(iss(R, "3 单聚合维护含多个聚合")) == 1 and "PRC-7" in iss(R, "3 单聚合维护含多个聚合")[0], R["issues"])
    # 状态机：无命令的事件转换参与分类；草稿入口有命令时，草稿出向转换按修改
    mv = M("AGG-01", ["草稿", "已生效", "已取代"], [{"from": None, "to": "草稿", "command": "编制甲"}, {"from": "草稿", "to": "已生效", "command": "发布甲"},
                                                   {"from": "已生效", "to": "已取代"}])
    R = _run([_st("AGG-01", "编制甲"), _st("AGG-01", "发布甲")], ["AGG-01"], machines={"AGG-01": mv})
    check("草稿入口有命令：创建只认入口，发布按修改；无命令终态转换计结束",
          R["touch"]["OBJ-01"]["PRC-1"] == {"C", "U", "D"}, R["touch"])
    mv2 = M("AGG-01", ["草稿", "已生效", "已取代"], [{"from": "草稿", "to": "已生效", "command": "发布甲"}, {"from": "已生效", "to": "草稿"}])
    R = _run([_st("AGG-01", "发布甲")], ["AGG-01"], machines={"AGG-01": mv2})
    check("草稿入口无命令：出向命令按创建；无命令非终态转换计修改", R["touch"]["OBJ-01"]["PRC-1"] == {"C", "U"}, R["touch"])
    R = _run([], ["AGG-01"], units=[su(transitions=[{"from": "草稿", "to": "已生效", "command": "发布甲", "approval": {"on_reject": "状态保持草稿"}}])])
    check("单聚合维护条目上的审批驳回边计逆向", R["touch"]["OBJ-01"]["PRC-7"] and not iss(R, "6 生命周期缺冲销撤回类逆向"), R["issues"])
    R = _run([], ["AGG-01"], units=[su(transitions=[{"from": "草稿", "to": "已生效", "command": "发布甲"}])])
    check("单聚合维护条目无审批：仍缺逆向", len(iss(R, "6 生命周期缺冲销撤回类逆向")) == 1, R["issues"])
    # B3 self-loops: a modification only, never an end; not an outgoing transition
    loop = M("AGG-01", ["a", "b"], [{"from": None, "to": "a", "command": "起甲"}, {"from": "a", "to": "b", "command": "收乙"}, {"from": "b", "to": "b", "command": "循环丙"},
                                   {"from": ["a", "b"], "to": "b", "command": "收丁"}])
    pml = prep_machine(loop)
    check("自环：只算修改，不算创建或结束；from 列表里的非自环项仍算结束",
          machine_class(pml, "循环丙") == ({"U"}, False) and machine_class(pml, "收丁") == ({"D"}, False) and "b" in pml["term"], (machine_class(pml, "循环丙"), machine_class(pml, "收丁")))
    lp = lambda unit_trs, mach_trs: iss(_run([], ["AGG-01"], {"AGG-01": M("AGG-01", ["a", "b"], mach_trs)},
                                           units=[su(transitions=unit_trs)]), "3 状态名冲突")
    check("自环不算出转换：他处为终点、状态机里只有自环，不报状态名冲突；有真出转换才报",
          lp([{"from": "a", "to": "b"}], [{"from": "a", "to": "b"}, {"from": "b", "to": "b"}]) == []
          and len(lp([{"from": "a", "to": "b"}], [{"from": "a", "to": "b"}, {"from": "b", "to": "a"}])) == 1)
    # B4 orchestrator calling the owning BC through api: the write belongs to the owner
    api = {"mode": "api", "direction": "out", "bc": "B-01", "name": "x"}
    w4 = lambda **kw: iss(_run([_st("AGG-01", "登记甲"), _st("AGG-01", "批准乙", bc="B-02", **kw)], ["AGG-01"]), "4 多个 BC 写同一对象")
    check("经 api 调所属 BC：写入方记所属 BC；无 api 仍报多个 BC", w4(cross_bc=[api]) == [] and len(w4()) == 1, (w4(cross_bc=[api]), w4()))
    check("经 api 调所属 BC（不指向所属 BC 但步骤 bc 与聚合 BC 不同）也记所属 BC；direction in 不算",
          w4(cross_bc=[dict(api, bc="B-09")]) == [] and len(w4(cross_bc=[dict(api, direction="in")])) == 1, w4(cross_bc=[dict(api, direction="in")]))
    # B5 level cell decides non-object
    row = lambda definition, level: ["", "x", definition, level, "", ""]
    check("层级格以非对象开头才是非对象；格里提到「非对象」或定义里写非对象不算；行号保留算",
          not is_non_object(row("定义", "待定（暂列一级：原父对象间接成本池已降为非对象）")) and is_non_object(row("定义", "**非对象**：并入 OBJ-01"))
          and not is_non_object(row("并入时标非对象", "次级")) and is_non_object(row("【行号保留，已非对象】x", "次级")))
    # B6 secondary objects held inside an aggregate are carried by it
    ag = {"AGG-01": {"bc": "B-01", "name": "a", "objs": ["OBJ-01"], "inner_objs": ["OBJ-02"], "commands": [], "kind": "一对一"}}
    ct = {"OBJ-01": {"name": "a", "non": False}, "OBJ-02": {"name": "b", "non": False}, "OBJ-03": {"name": "c", "non": False}}
    docs6 = {"g": {"processes": [{"id": "PRC-1", "stages": ["VS-01.1"], "owner_bc": "B-01", "steps": [_st("AGG-01", "登记甲")]}]}}
    got6 = [t for _v, t in analyse(docs6, ag, {}, ct, None)["issues"].get("2 对象无聚合承载（缺流程，或应为参考数据或属性）", [])]
    check("内含列的次级对象由该聚合承载，不报无聚合承载；不在内含列的仍报", got6 == ["OBJ-03 c"], got6)
    check("矩阵汇总表有已豁免列", "| 已豁免 |" in render_matrix(apply_exemptions(base(), [ok_e]), {}))
    return all(results)


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
    classes = {n: classify_cmd(n) for n in ("登记报销", "提交申请", "批准用印申请", "关闭线索", "冲销报销", "拜访客户", "驳回奖金核定", "参保减员", "启用法人主体",
                                           "〔缺〕撤销离职登记", "参保增员", "草拟奖金核定", "更名法人主体", "补卡", "计算", "锁定", "归还资产", "办理离职")}
    ok_cls = classes == {"登记报销": ("C", False), "提交申请": ("U", False), "批准用印申请": ("U", False), "关闭线索": ("D", False), "冲销报销": ("D", True),
                         "拜访客户": ("?", False), "驳回奖金核定": ("D", True), "参保减员": ("D", False), "启用法人主体": ("U", True),
                         "〔缺〕撤销离职登记": ("D", True), "参保增员": ("C", False), "草拟奖金核定": ("C", False), "更名法人主体": ("U", False),
                         "补卡": ("U", False), "计算": ("C", False), "锁定": ("D", False), "归还资产": ("D", False), "办理离职": ("C", False)}
    print("命令分类:", "通过" if ok_cls else f"失败 {classes}")
    ok = all(exp_ok) and ok_cls and self_test_more()
    print("自测", "通过" if ok else "失败")
    return 0 if ok else 1


def read_group_yaml(group, name, local):
    if local:
        p = HERE / group / name
        return p.read_text(encoding="utf-8") if p.exists() else None
    return cc.git_show(f"claude/one-arch-{group}-closeout", f"docs/modernization/architecture/proposals/closeout/{group}/{name}")


def yaml_items(key, given, name, local):
    texts = [p.read_text(encoding="utf-8") for p in given if p.exists()] if given else [read_group_yaml(g, name, local) for g in cc.GROUPS]
    return [x for t in texts if t for x in (yaml.safe_load(t) or {}).get(key) or []]


def render_objects_yaml(R):
    touch, firm, names, members, ui = R["touch"], R["firm"], R["names"], R["members"], R["unit_info"]
    by_unit = collections.defaultdict(list)
    for k, us in touch.items():
        for uid, L in us.items():
            ops = sorted(L, key="CUD?R".index)
            by_unit[uid].append({"obj": k, "name": names.get(k, k), "aggregates": sorted(members.get(k, [])) or [k], "ops": "".join(ops),
                                 "proposed_only": "".join(c for c in ops if c != "R" and c not in firm[k][uid])})
    num = lambda x: (x[:3], int(re.sub(r"\D", "", x) or 0))
    doc = {"note": "object_check.py --objects-yaml 生成，勿手改；ops：C 创建、U 修改、D 结束或归档（含冲销撤回）、R 只读、? 判不出；proposed_only 为只来自提议命令（〔缺〕）的字母",
           "processes": [{"id": uid, "name": ui[uid][2], "group": ui[uid][0],
                          "objects": [{k: v for k, v in o.items() if v or k == "ops"} for o in sorted(by_unit[uid], key=lambda o: num(o["obj"]))]}
                         for uid in sorted(by_unit, key=num)]}
    return yaml.safe_dump(doc, allow_unicode=True, sort_keys=False, width=200)


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
    flag = lambda f: [Path(argv[i + 1]) for i, x in enumerate(argv) if x == f]
    machines = {m["aggregate"]: m for m in yaml_items("machines", flag("--states"), "state-machines.yaml", local)}
    if not machines and not flag("--states") and (HERE / "state-machines.yaml").exists():
        machines = {m["aggregate"]: m for m in yaml_items("machines", [HERE / "state-machines.yaml"], "", local)}
    catalog_cmds = [(c["aggregate"], c["name"], str(c["unused_ok"])) for c in yaml_items("commands", flag("--commands"), "commands.yaml", local)
                    if c.get("unused_ok") and c.get("aggregate")]
    R = analyse(docs, aggs, new_aggs, cat, matrix, machines, catalog_cmds)
    ep = Path(argv[argv.index("--exemptions") + 1]) if "--exemptions" in argv else HERE / "OBJECT_EXEMPTIONS.yaml"
    entries, present = load_exemptions(ep)
    R = apply_exemptions(R, entries)
    if not present:
        print(f"豁免表 {ep.name} 缺失，视为空表")
    print(f"对象目录 {len(cat)} 行（有效 {sum(1 for c in cat.values() if not c['non'])}）；聚合稿聚合 {len(aggs)}；权责表：{'有' if matrix is not None else '无，第 5 项跳过'}")
    print(summary(R))
    if "--write" in argv:
        (HERE / "CRUD_MATRIX.md").write_text(render_matrix(R, vs_names()), encoding="utf-8")
        print("已写 CRUD_MATRIX.md")
    if "--objects-yaml" in argv:
        (HERE / "process-objects.yaml").write_text(render_objects_yaml(R), encoding="utf-8")
        print("已写 process-objects.yaml")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
