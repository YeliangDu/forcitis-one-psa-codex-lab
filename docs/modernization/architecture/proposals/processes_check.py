"""Check processes.v1.yaml (METHOD v4 step 6a) against value-streams.yaml and ENTERPRISE_AGGREGATES_DRAFT.md.

Read-only. Usage: python3 -I processes_check.py
Exit code 0 only when there is no violation. Open gaps (the `gaps` list), subscription-coverage gaps and hints are findings, not violations.

The YAML holds four kinds of unit (opus review H2; sonnet re-check added the fourth): `processes` (business processes),
`event_policies` (one event, one step), `single_aggregate` (maintenance of one aggregate's life cycle) and `read_models`
(read only, no command; may cover stages, a stage covered by read models alone is listed in stages_without_process with
coverage 读模型); `merged` records the numbers folded into another process.

Violations
  1. every value-stream stage is covered by a process, or is listed in stages_without_process with its coverage type
     (事件策略 / 单聚合维护 / 无需系统支持); the type must be true
  2. every process has a trigger (event with a published aggregate event, time, human, or call); an event policy has an event trigger
  3. every non-read step has a command and an existing aggregate; the command is in that aggregate's command column
     ("命令（→ 事件；前置 INV）") in section 2.2, also for `also` and `inner`; `emits` must be events the command declares there;
     only a C-class invariant (INV table class C, matched by INV number) allows `also`
  4. dependency direction (section 4.1 edges, downstream calls upstream): every aggregate a unit touches lies in its BC (owner_bc,
     or the step's `bc`) or in an upstream BC; `reporting` only reads and subscribes; platform.* owners follow the built edges;
     an event trigger or `on_event` needs the mode 事件 on the edge (same BC excepted)
  5. internal references (calls, gap ids, merged_into) resolve; unit numbers are unique and complete
  6. human_orchestration true needs a reason and one of: a platform approval task, a timeout (`on_time`, or a branch containing
     超时), a return branch (branch containing 退回 / 驳回 / 撤回 / 退票 / 未通过 / 拒绝); platform_task services must be registered modules
  7. a single_aggregate unit touches only aggregates of its own BC

Findings (printed, not violations)
  - commands in the command column no step uses (the column was merged in from this very file, so this is no proof of completeness; H1)
  - subscription coverage gaps (H3): a BC named as subscriber of an event with no process, event policy or `on_event` step of that BC
    reacting to it; compared with the list in gap G-19
  - stage mapping hints (M4): a unit mapped to a stage touches none of the aggregates of the objects in the stage's entry or exit
    condition (object -> aggregate by the object column of section 2.2)
  - events used as triggers although their subscriber column says 仅分析 (G-05)

Formal checks only (L1): the command-column checks (3) and the stage checks (1) prove that names and references resolve, not that the
list is complete or right; the independent review of the command column is step 4.
"""
import importlib.util
import re
import sys
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent
PROC = HERE / "processes.v1.yaml"
STAGES = HERE.parent / "ssot" / "05-value-streams" / "value-streams.yaml"
DRAFT = HERE / "ENTERPRISE_AGGREGATES_DRAFT.md"
DEPCHECK = HERE / "aggregates_dep_check.py"

TRIGGER_TYPES = {"event", "time", "human", "call"}
GAP_KINDS = {"缺命令", "缺事件", "缺订阅方", "方向冲突", "缺依赖边", "缺聚合", "待定未决", "文字不一致", "规则冲突"}
COVERAGE_TYPES = {"事件策略", "单聚合维护", "读模型", "无需系统支持"}
REPORTING = "reporting"
RETURN_WORDS = ("退回", "驳回", "撤回", "退票", "未通过", "拒绝")
AGG_ROW = re.compile(r"^\|\s*((?:AGG|REF)-\d+)\s*\|")
BC_HEADING = re.compile(r"^\*\*(B-\d+)\b")
INV_ROW = re.compile(r"^\|\s*(INV-\d+[ab]?)\s*\|")

errors = []
COMMANDS = {}


def err(msg):
    errors.append(msg)


def cells(line):
    return [c.strip() for c in line.strip().strip("|").split("|")]


def split_top(text, seps="、"):
    out, depth, cur = [], 0, ""
    for ch in text:
        if ch in "（(":
            depth += 1
        elif ch in "）)":
            depth -= 1
        if depth == 0 and ch in seps:
            out.append(cur)
            cur = ""
        else:
            cur += ch
    out.append(cur)
    return [s.strip() for s in out if s.strip()]


def expand(name):
    """Expand '客户活动已计划 / 已变更' and 'TimesheetSubmitted/Approved' into full event names."""
    if " / " in name:
        parts = [p.strip() for p in name.split(" / ")]
        head = parts[0]
        i = head.find("已")
        prefix = head[:i] if i > 0 else ""
        return [head] + [prefix + p for p in parts[1:]]
    if "/" in name and re.fullmatch(r"[A-Za-z/]+", name):
        parts = name.split("/")
        m = re.match(r"[A-Z][a-z]+", parts[0])
        prefix = m.group(0) if m else ""
        return [parts[0]] + [p if p.startswith(prefix) else prefix + p for p in parts[1:]]
    return [name]


def parse_events(cell, own_bc):
    """Return {event name: {"nosub": bool, "subs": set of BC ids}} from the 对外事件 cell."""
    events = {}
    cleaned = re.sub(r"新增草案：|草案：", "", cell)
    for item in split_top(cleaned.replace("；", "、")):
        item = item.replace("`", "")
        m = re.match(r"([^（(]*)(.*)", item)
        name, rest = m.group(1).strip(), m.group(2)
        if not name or name in ("已有", "—", "-"):
            continue
        name = re.sub(r"^已有\s*", "", name)
        rest = rest.split("沿已有边")[0]
        nosub = "仅分析" in rest
        subs = set()
        if not nosub and "不订阅" not in rest:
            subs = set(re.findall(r"B-\d+|platform\.[a-z]+", rest))
            if "同 BC" in rest:
                subs.add(own_bc)
        for ev in expand(name):
            cur = events.setdefault(ev, {"nosub": False, "subs": set()})
            cur["nosub"] = cur["nosub"] or nosub
            cur["subs"] |= subs
    return events


def parse_commands(cell):
    """Return {command name: (events, invs)} from a cell like '提交检查记录（→（内部）；前置 INV-55）；锁定检查（→ 检查已锁定）'."""
    out = {}
    for item in split_top(cell, "；"):
        if item.startswith("—"):
            continue
        name = re.split(r"[（(]", item, 1)[0].strip()
        m = re.search(r"（→\s*(.*)）\s*$", item)
        body = m.group(1) if m else ""
        head, _, inv = body.partition("；前置")
        evs = [] if head.strip() in ("（内部）", "") else [e.strip() for e in split_top(head.strip(), "、")]
        out[name] = (evs, re.findall(r"INV-\d+[ab]?", inv))
    return out


def load_draft():
    text = DRAFT.read_text(encoding="utf-8")
    owner, events, obj_aggs = {}, {}, {}
    section, current = None, None
    c_invs = set()
    COMMANDS.clear()
    for line in text.splitlines():
        if line.startswith("### "):
            section = line[4:].split(" ")[0]
        if section == "1.2":
            m = INV_ROW.match(line)
            if m and "**C**" in cells(line)[2]:
                c_invs.add(m.group(1))
        if section != "2.2":
            continue
        m = BC_HEADING.match(line)
        if m:
            current = m.group(1)
            continue
        m = AGG_ROW.match(line)
        if m and current:
            row = cells(line)
            agg = m.group(1)
            owner[agg] = current
            events[agg] = parse_events(row[-2], current) if len(row) >= 3 else {}
            COMMANDS[agg] = parse_commands(row[-3])
            for obj in re.findall(r"OBJ-\d+", row[1] + " " + row[2]):
                obj_aggs.setdefault(obj, set()).add(agg)
    return text, owner, events, c_invs, obj_aggs


def load_edges():
    saved = sys.argv
    sys.argv = [str(DEPCHECK)]
    try:
        spec = importlib.util.spec_from_file_location("aggregates_dep_check", DEPCHECK)
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        owner, deps, _counted, modules = mod.parse(mod.DRAFT.read_text(encoding="utf-8"))
        edges, _ = mod.edges_from(owner, deps)
        built, _isolated = mod.built_edges(modules)
        built_pairs = {(bl, br) for (_l, _r, bl, br) in built}
        services = {x for (l, r, _bl, _br) in built for x in (l, r) if x.startswith("platform.")}
    finally:
        sys.argv = saved
    return {k: set(v) for k, v in edges.items()}, built_pairs, services


def stage_ids(vs):
    return [s["id"] for v in vs["value_streams"] for s in v["stages"]]


def condition_refs(cond, patterns):
    """All OBJ refs in an entry / exit condition, resolving `pattern` names."""
    out = set()
    if isinstance(cond, dict):
        if "ref" in cond and isinstance(cond["ref"], str) and cond["ref"].startswith("OBJ-"):
            out.add(cond["ref"])
        if "pattern" in cond and cond["pattern"] in patterns:
            out |= condition_refs(patterns[cond["pattern"]], patterns)
        for k in ("all", "any"):
            for sub in cond.get(k, []) or []:
                out |= condition_refs(sub, patterns)
    elif isinstance(cond, list):
        for sub in cond:
            out |= condition_refs(sub, patterns)
    return out


def main():
    data = yaml.safe_load(PROC.read_text(encoding="utf-8"))
    vs = yaml.safe_load(STAGES.read_text(encoding="utf-8"))
    draft_text, bc_of, events_of, c_invs, obj_aggs = load_draft()
    edges, built_pairs, services = load_edges()
    bcs = set(bc_of.values())

    procs = data["processes"]
    policies = data.get("event_policies", []) or []
    maint = data.get("single_aggregate", []) or []
    rmodels = data.get("read_models", []) or []
    merged = data.get("merged", []) or []
    gaps = {g["id"]: g for g in data.get("gaps", [])}
    all_stages = stage_ids(vs)
    stage_set = set(all_stages)

    # stage -> aggregates of the objects in its entry / exit condition
    stage_aggs = {}
    for v in vs["value_streams"]:
        patterns = v.get("stage_pattern", {}) or {}
        for s in v["stages"]:
            refs = condition_refs(s.get("entry"), patterns) | condition_refs(s.get("exit"), patterns)
            aggs = set()
            for r in refs:
                aggs |= obj_aggs.get(r, set())
            stage_aggs[s["id"]] = aggs

    # unit numbers
    ids = [u["id"] for u in procs + policies + maint + rmodels] + [m["id"] for m in merged]
    if len(ids) != len(set(ids)):
        err("duplicate unit ids across processes, event_policies, single_aggregate, read_models and merged")
    nums = sorted(int(i[4:]) for i in set(ids) if re.fullmatch(r"PRC-\d+", i))
    missing = [n for n in range(1, (nums[-1] if nums else 0) + 1) if n not in nums]
    if missing:
        err("unit numbers missing: " + "、".join(f"PRC-{n:02d}" for n in missing))
    pid_set = {p["id"] for p in procs}
    for m in merged:
        if m.get("merged_into") not in pid_set:
            err(f"{m.get('id')}: merged_into {m.get('merged_into')} is not a process")
        if not m.get("reason"):
            err(f"{m.get('id')}: merged needs reason")

    for g in gaps.values():
        for k in ("kind", "severity", "title", "detail", "back_to", "change"):
            if not g.get(k):
                err(f"{g['id']}: missing {k}")
        if g.get("kind") not in GAP_KINDS:
            err(f"{g['id']}: unknown kind {g.get('kind')}")
        for f in g.get("flows", []):
            if f not in set(ids):
                err(f"{g['id']}: unknown flow {f}")
    if len(gaps) != len(data.get("gaps", [])):
        err("duplicate gap ids")

    def base_ok(base, tbc, kind, need_event=False):
        if base == tbc:
            return True
        if base == REPORTING:
            return kind in ("read", "event")
        modes = edges.get((base, tbc))
        if modes is None:
            return (base, tbc) in built_pairs
        return (not need_event) or "事件" in modes

    def check_agg(ref, where, base, kind="write"):
        if ref not in bc_of:
            err(f"{where}: unknown aggregate {ref}")
            return
        if base == REPORTING and kind == "write":
            err(f"{where}: reporting is read-only and may not run commands")
            return
        if not base_ok(base, bc_of[ref], kind):
            err(f"{where}: direction {base} -> {bc_of[ref]} ({ref}) is not an edge in section 4.1 or the built edges")

    consumers = {}
    nosub = {}
    touched_by_unit = {}
    stats = {"write": 0, "read": 0, "task": 0}
    used_cmds = set()
    c_exceptions = []
    time_flows = []
    human_n = 0

    def event_ref(where, base, e, as_trigger_of=None):
        a, ev = e.get("aggregate"), e.get("event")
        if a not in events_of:
            err(f"{where}: event aggregate {a} unknown")
            return
        if ev not in events_of[a]:
            err(f"{where}: event '{ev}' is not an event of {a}")
            return
        consumers.setdefault((a, ev), set()).add(base)
        if events_of[a][ev]["nosub"] and base != REPORTING and as_trigger_of:
            nosub.setdefault((a, ev), []).append(as_trigger_of)
        if base != REPORTING and a in bc_of:
            if not base_ok(base, bc_of[a], "event", need_event=True):
                err(f"{where}: event '{ev}' of {a} comes from {bc_of[a]}, which {base} does not subscribe to (edge missing or mode 事件 missing)")

    def check_step(uid, i, s, owner_bc, touched, unit_kind):
        where = f"{uid} step {i}"
        base = s.get("bc", owner_bc)
        if s.get("bc") and s["bc"] not in bcs and s["bc"] not in services and s["bc"] != REPORTING:
            err(f"{where}: unknown bc {s['bc']}")
        if not s.get("actor"):
            err(f"{where}: missing actor")
        if s.get("gap") and s["gap"] not in gaps:
            err(f"{where}: unknown gap {s['gap']}")
        if s.get("calls") and s["calls"] not in pid_set:
            err(f"{where}: calls unknown process {s['calls']}")
        for e in s.get("on_event", []) or []:
            event_ref(where + " on_event", base, e, as_trigger_of=uid)
            touched.add(e.get("aggregate"))
        if s.get("read"):
            stats["read"] += 1
            if not s.get("reads"):
                err(f"{where}: read step needs reads")
            for a in s.get("reads", []) or []:
                check_agg(a, where, base, "read")
                touched.add(a)
            return
        if s.get("platform_task"):
            stats["task"] += 1
            if not s.get("service") or not s.get("command"):
                err(f"{where}: platform_task needs service and command")
            elif s["service"] not in services:
                err(f"{where}: platform_task service {s['service']} is not a registered platform module")
            return
        stats["write"] += 1
        cmd, agg = s.get("command"), s.get("aggregate")
        if not cmd:
            err(f"{where}: non-read step has no command")
        if not agg:
            err(f"{where}: non-read step has no aggregate")
            return
        check_agg(agg, where, base)
        touched.add(agg)
        if agg.startswith("REF-") and not s.get("gap"):
            err(f"{where}: {agg} has no aggregate; the step must reference a gap")
        used_cmds.add((agg, cmd))
        declared = COMMANDS.get(agg, {}).get(cmd)
        if declared is None:
            err(f"{where}: command '{cmd}' is not in the command column of {agg} (section 2.2)")
        if s.get("post"):
            if "提交过账候选" not in COMMANDS.get(agg, {}):
                err(f"{where}: `post` needs the command '提交过账候选' in the command column of {agg}")
            used_cmds.add((agg, "提交过账候选"))
            if s.get("calls") not in (None, "PRC-92"):
                err(f"{where}: `post` calls PRC-92 only")
        for ev in s.get("emits", []) or []:
            if ev not in events_of.get(agg, {}):
                err(f"{where}: event '{ev}' is not an event of {agg}")
            elif declared is not None and ev not in declared[0]:
                err(f"{where}: event '{ev}' is not declared by command '{cmd}' of {agg} in the command column")
        if s.get("also"):
            exc = s.get("c_exception")
            if exc not in c_invs:
                err(f"{where}: `also` needs c_exception naming a C-class invariant ({sorted(c_invs)})")
            else:
                c_exceptions.append((uid, i, exc))
            for a in s["also"]:
                check_agg(a, where, base)
                touched.add(a)
                used_cmds.add((a, cmd))
                if cmd not in COMMANDS.get(a, {}):
                    err(f"{where}: command '{cmd}' is not in the command column of {a} (also)")
        for inn in s.get("inner", []) or []:
            if not inn.get("command"):
                err(f"{where}: inner step needs command")
            used_cmds.add((inn.get("aggregate"), inn.get("command")))
            if inn.get("command") not in COMMANDS.get(inn.get("aggregate"), {}):
                err(f"{where}: inner command '{inn.get('command')}' is not in the command column of {inn.get('aggregate')}")
            check_agg(inn.get("aggregate"), where + " inner", bc_of.get(agg, base), "write")
            touched.add(inn.get("aggregate"))

    def check_trigger(uid, t, owner_bc, touched, need_event):
        tt = (t or {}).get("type")
        if tt not in TRIGGER_TYPES:
            err(f"{uid}: trigger type must be one of {sorted(TRIGGER_TYPES)}")
            return tt
        if need_event and tt != "event":
            err(f"{uid}: an event policy must have an event trigger")
        if tt in ("time", "human", "call") and not t.get("desc"):
            err(f"{uid}: trigger {tt} needs desc")
        if tt == "event":
            if not t.get("events"):
                err(f"{uid}: event trigger needs events")
            for e in t.get("events", []):
                event_ref(f"{uid} trigger", owner_bc, e, as_trigger_of=uid)
                touched.add(e.get("aggregate"))
        return tt

    def check_stages(uid, stages, touched, hints):
        if not stages:
            err(f"{uid}: no stages")
        for s in stages or []:
            if s not in stage_set:
                err(f"{uid}: unknown stage {s}")
                continue
            sa = stage_aggs.get(s, set())
            if sa and not (sa & touched):
                hints.append((uid, s))

    stage_hints = []
    proc_cov, pol_cov, mnt_cov, rm_cov = set(), set(), set(), set()

    # ---- business processes
    for p in procs:
        pid = p["id"]
        for k in ("name", "trigger", "stages", "steps", "owner_bc", "human_orchestration"):
            if k not in p:
                err(f"{pid}: missing {k}")
        owner_bc = p.get("owner_bc", "")
        if owner_bc not in bcs and owner_bc != REPORTING and owner_bc not in services:
            err(f"{pid}: unknown owner_bc {owner_bc}")
        touched = set()
        tt = check_trigger(pid, p.get("trigger"), owner_bc, touched, need_event=False)
        if tt == "time":
            time_flows.append(pid)
        if not p.get("steps"):
            err(f"{pid}: no steps")
        for i, s in enumerate(p.get("steps", []), 1):
            check_step(pid, i, s, owner_bc, touched, "process")
        if not isinstance(p.get("human_orchestration"), bool):
            err(f"{pid}: human_orchestration must be true/false")
        elif p["human_orchestration"]:
            human_n += 1
            if not p.get("human_note"):
                err(f"{pid}: human_orchestration true needs human_note")
            steps = p.get("steps", [])
            evidence = any(s.get("platform_task") for s in steps) \
                or any(s.get("on_time") for s in steps) \
                or any("超时" in str(s.get("branch", "")) or any(w in str(s.get("branch", "")) for w in RETURN_WORDS) for s in steps)
            if not evidence:
                err(f"{pid}: human_orchestration true needs a platform task, a timeout or a return branch among its steps")
        if owner_bc == REPORTING and any(s.get("command") and not s.get("read") for s in p.get("steps", [])):
            err(f"{pid}: reporting is read-only")
        check_stages(pid, p.get("stages"), touched, stage_hints)
        proc_cov |= set(p.get("stages") or [])
        touched_by_unit[pid] = touched

    # ---- event policies
    for p in policies:
        pid = p["id"]
        for k in ("name", "trigger", "stages", "step", "owner_bc"):
            if k not in p:
                err(f"{pid}: missing {k}")
        owner_bc = p.get("owner_bc", "")
        if owner_bc not in bcs and owner_bc not in services:
            err(f"{pid}: unknown owner_bc {owner_bc}")
        touched = set()
        check_trigger(pid, p.get("trigger"), owner_bc, touched, need_event=True)
        for a in p.get("reads", []) or []:
            check_agg(a, f"{pid} reads", owner_bc, "read")
            touched.add(a)
        st = p.get("step") or {}
        if st.get("read") or not (st.get("command") or st.get("platform_task")):
            err(f"{pid}: an event policy has exactly one command step")
        check_step(pid, 1, st, owner_bc, touched, "policy")
        check_stages(pid, p.get("stages"), touched, stage_hints)
        pol_cov |= set(p.get("stages") or [])
        touched_by_unit[pid] = touched

    # ---- single-aggregate maintenance
    for p in maint:
        pid = p["id"]
        for k in ("name", "aggregate", "owner_bc", "stages", "commands"):
            if k not in p:
                err(f"{pid}: missing {k}")
        owner_bc = p.get("owner_bc", "")
        if owner_bc not in bcs:
            err(f"{pid}: unknown owner_bc {owner_bc}")
        touched = set()
        for i, s in enumerate(p.get("commands", []), 1):
            if s.get("read") or s.get("platform_task") or s.get("also") or s.get("bc") or s.get("on_event"):
                err(f"{pid} step {i}: maintenance steps are plain commands")
            s.setdefault("aggregate", p.get("aggregate"))
            check_step(pid, i, s, owner_bc, touched, "maint")
            if s.get("aggregate") in bc_of and bc_of[s["aggregate"]] != owner_bc:
                err(f"{pid} step {i}: single_aggregate touches {s['aggregate']} outside {owner_bc}")
        check_stages(pid, p.get("stages"), touched, stage_hints)
        mnt_cov |= set(p.get("stages") or [])
        touched_by_unit[pid] = touched

    # ---- read models (read only, no command)
    for p in rmodels:
        pid = p["id"]
        for k in ("name", "owner_bc", "stages", "reads", "desc"):
            if not p.get(k):
                err(f"{pid}: read model missing {k}")
        for k in ("steps", "step", "commands", "trigger"):
            if k in p:
                err(f"{pid}: a read model has no {k} (read only, no command)")
        owner_bc = p.get("owner_bc", "")
        if owner_bc not in bcs and owner_bc not in services and owner_bc != REPORTING:
            err(f"{pid}: unknown owner_bc {owner_bc}")
        touched = set()
        for a in p.get("reads", []) or []:
            check_agg(a, f"{pid} reads", owner_bc, "read")
            touched.add(a)
        for e in p.get("events", []) or []:
            event_ref(f"{pid} events", owner_bc, e, as_trigger_of=pid)
            touched.add(e.get("aggregate"))
        check_stages(pid, p.get("stages"), touched, stage_hints)
        rm_cov |= set(p.get("stages") or [])
        touched_by_unit[pid] = touched

    # ---- stage coverage
    swp = {}
    for sw in data.get("stages_without_process", []) or []:
        st = sw.get("stage")
        if st not in stage_set:
            err(f"stages_without_process: unknown stage {st}")
            continue
        if sw.get("coverage") not in COVERAGE_TYPES:
            err(f"stages_without_process {st}: coverage must be one of {sorted(COVERAGE_TYPES)}")
        if not sw.get("reason"):
            err(f"stages_without_process {st}: missing reason")
        if st in proc_cov:
            err(f"stages_without_process {st}: the stage is covered by a business process")
        if sw.get("coverage") == "事件策略" and st not in pol_cov:
            err(f"stages_without_process {st}: no event policy covers it")
        if sw.get("coverage") == "单聚合维护" and st not in mnt_cov:
            err(f"stages_without_process {st}: no single-aggregate maintenance covers it")
        if sw.get("coverage") == "读模型" and st not in rm_cov:
            err(f"stages_without_process {st}: no read model covers it")
        swp[st] = sw
    cov_n = {"业务流程": 0, "事件策略": 0, "单聚合维护": 0, "读模型": 0, "无需系统支持": 0}
    for s in all_stages:
        if s in proc_cov:
            cov_n["业务流程"] += 1
        elif s in swp:
            cov_n[swp[s].get("coverage")] = cov_n.get(swp[s].get("coverage"), 0) + 1
        else:
            err(f"stage {s} is covered by no business process and not listed in stages_without_process")

    # ---- command column
    inv_ids = set(INV_ROW.findall("\n".join(l for l in draft_text.splitlines())))
    inv_ids |= set(re.findall(r"^\|\s*(INV-\d+[ab]?)\s*\|", draft_text, re.M))
    col_total = unused = 0
    unused_list = []
    for agg, cs in COMMANDS.items():
        evs = events_of.get(agg, {})
        for c, (cev, cinv) in cs.items():
            col_total += 1
            if (agg, c) not in used_cmds:
                unused += 1
                unused_list.append(f"{agg} {c}")
            for e in cev:
                if e not in evs:
                    err(f"{agg}: command '{c}' emits '{e}', which is not in the event column of {agg}")
            for i in cinv:
                if i not in inv_ids:
                    err(f"{agg}: command '{c}' cites {i}, which is not in the invariant table")

    # ---- processes failing criterion 2 (hint)
    narrow = []
    for p in procs:
        aggs, roles = set(), set()
        for st in p.get("steps", []):
            if st.get("read"):
                continue
            if st.get("actor") != "系统策略":
                roles.add(st.get("actor"))
            if st.get("platform_task"):
                continue
            aggs.add(st.get("aggregate"))
            aggs |= set(st.get("also", []) or [])
            aggs |= {i.get("aggregate") for i in st.get("inner", []) or []}
        if len(aggs) <= 1 and len(roles) <= 1:
            narrow.append(p["id"])

    # ---- events no command emits (hint)
    orphan_events = []
    for agg, evs in events_of.items():
        emitted = {e for (cev, _inv) in COMMANDS.get(agg, {}).values() for e in cev}
        for ev in evs:
            if ev not in emitted and ev != "待定":
                orphan_events.append((agg, ev))

    # ---- subscription coverage (H3)
    sub_gaps = []
    for agg, evs in events_of.items():
        for ev, info in evs.items():
            for bc in sorted(info["subs"]):
                if bc not in consumers.get((agg, ev), set()):
                    sub_gaps.append((agg, ev, bc))
    declared = {(d["aggregate"], d["event"], d["bc"]) for g in gaps.values() for d in g.get("subscriptions", []) or []}
    undeclared = [x for x in sub_gaps if x not in declared]
    stale = [x for x in sorted(declared) if x not in set(sub_gaps)]

    written = set()
    for u in procs + policies + maint:
        for a in touched_by_unit.get(u["id"], ()):
            written.add(a)
    agg_all = sorted((a for a in bc_of if a.startswith("AGG-")), key=lambda x: int(x[4:]))

    by_kind = {}
    for g in gaps.values():
        by_kind[g["kind"]] = by_kind.get(g["kind"], 0) + 1

    n_units = len(procs) + len(policies) + len(maint)
    survivors = sorted({m["merged_into"] for m in merged})
    print(f"业务流程 {len(procs)}（人工编排 {human_n}；触发：" + "，".join(
        f"{k} {sum(1 for p in procs if p['trigger'].get('type') == k)}" for k in sorted(TRIGGER_TYPES)) + "）；"
          f"事件策略 {len(policies)}；单聚合维护 {len(maint)}；读模型 {len(rmodels)}；合并：{len(merged)} 个编号并入 {len(survivors)} 个流程；编号共 {len(set(ids))}")
    print(f"步骤数：{stats['write'] + stats['read'] + stats['task']}（改状态 {stats['write']}，只读 {stats['read']}，平台任务 {stats['task']}）；"
          f"命令列共 {col_total} 个命令，未被任何步骤使用 {unused}（提示，不判违规：命令列由本清单汇入，待第④步独立复核）")
    print(f"阶段数：{len(all_stages)}；业务流程覆盖 {cov_n['业务流程']}，仅事件策略 {cov_n['事件策略']}，仅单聚合维护 {cov_n['单聚合维护']}，仅读模型 {cov_n['读模型']}，无需系统支持 {cov_n['无需系统支持']}；"
          f"读模型另覆盖（与流程重叠）{len(rm_cov & proc_cov)} 个阶段")
    print(f"gaps 数：{len(gaps)}（" + "，".join(f"{k} {v}" for k, v in sorted(by_kind.items())) + "）")
    print(f"订阅覆盖 gap：{len(sub_gaps)} 条（已在 G-19 登记 {len(sub_gaps) - len(undeclared)}，未登记 {len(undeclared)}；G-19 登记但已不存在 {len(stale)}）")
    for agg, ev, bc in sub_gaps:
        print(f"  订阅覆盖：{agg} {ev} → {bc}" + ("" if (agg, ev, bc) in declared else "（未登记）"))
    for x in stale:
        print(f"  G-19 登记但已有反应：{x}")
    print(f"判据 ② 候选：{len(narrow)} 个流程只写一个聚合且只有一个人工角色（提示，不判违规；H2 的名单之外，待用户裁定是否再移出）：" + "、".join(narrow))
    print(f"无命令发出的事件：{len(orphan_events)} 个（提示：" + "；".join(f"{a} {e}" for a, e in orphan_events) + "）")
    print(f"C 类同事务步骤：{len(c_exceptions)}（" + "，".join(sorted({c for _, _, c in c_exceptions})) + "）")
    print("定时触发的流程：" + str(len(time_flows)))
    print("以订阅方为「仅分析」的事件作触发：" + (
        "；".join(f"{a} {ev}（{'、'.join(sorted(set(v)))}）" for (a, ev), v in sorted(nosub.items())) or "无"))
    print(f"阶段映射提示：{len(stage_hints)} 对（流程触达的聚合不含该阶段进入或退出条件对象的聚合；提示，不判违规）")
    for uid, s in stage_hints:
        print(f"  阶段映射：{uid} → {s}")
    no_write = [a for a in agg_all if a not in written]
    print(f"未被任何流程、事件策略或维护触达的聚合（{len(no_write)} 个）：" + "、".join(no_write))
    for e in errors:
        print("违规：" + e)
    print("结果：" + ("通过" if not errors else f"{len(errors)} 处违规"))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
