"""Check processes.v1.yaml (METHOD v4 step 6a) against value-streams.v2.yaml and ENTERPRISE_AGGREGATES_DRAFT.md.

Read-only. Usage: python3 -I processes_check.py
Exit code 0 only when there is no violation. Open gaps (the `gaps` list) are findings, not violations.

Checks
  1. every value-stream stage is covered by at least one process, or listed in stages_without_process
  2. every process has a trigger (event with a published aggregate event, time, human, or call)
  3. every non-read step has a command and an existing aggregate; emitted events exist on that aggregate (section 2.2);
     steps marked `named` use a command name that literally appears in the aggregate draft;
     only the C-class exception (`also` + `c_exception`) changes two aggregates in one step
  4. dependency direction: every aggregate a process touches lies in owner_bc or in an upstream BC (section 4.1 edges)
  5. internal references (calls, gap ids) resolve; human_orchestration processes carry a reason
"""
import importlib.util
import re
import sys
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent
PROC = HERE / "processes.v1.yaml"
STAGES = HERE / "value-streams.v2.yaml"
DRAFT = HERE / "ENTERPRISE_AGGREGATES_DRAFT.md"
DEPCHECK = HERE / "aggregates_dep_check.py"

TRIGGER_TYPES = {"event", "time", "human", "call"}
GAP_KINDS = {"缺命令", "缺事件", "缺订阅方", "方向冲突", "缺依赖边", "缺聚合", "待定未决", "文字不一致", "规则冲突"}
NON_BC_OWNERS = {"reporting"}
AGG_ROW = re.compile(r"^\|\s*((?:AGG|REF)-\d+)\s*\|")
BC_HEADING = re.compile(r"^\*\*(B-\d+)\b")

errors = []


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


def parse_events(cell):
    """Return {event name: no_subscriber(bool)}."""
    events = {}
    cleaned = re.sub(r"新增草案：|草案：", "", cell)
    for item in split_top(cleaned.replace("；", "、")):
        item = item.replace("`", "")
        m = re.match(r"([^（(]*)(.*)", item)
        name, rest = m.group(1).strip(), m.group(2)
        if not name or name in ("已有", "—", "-"):
            continue
        name = re.sub(r"^已有\s*", "", name)
        for ev in expand(name):
            events[ev] = "暂无" in rest
    return events


def load_draft():
    text = DRAFT.read_text(encoding="utf-8")
    owner, events, section, current = {}, {}, None, None
    for line in text.splitlines():
        if line.startswith("### "):
            section = line[4:].split(" ")[0]
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
            events[agg] = parse_events(row[-2]) if len(row) >= 3 else {}
    return text, owner, events


def load_edges():
    saved = sys.argv
    sys.argv = [str(DEPCHECK)]
    try:
        spec = importlib.util.spec_from_file_location("aggregates_dep_check", DEPCHECK)
        mod = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        owner, deps, _counted, _modules = mod.parse(mod.DRAFT.read_text(encoding="utf-8"))
        edges, _ = mod.edges_from(owner, deps)
    finally:
        sys.argv = saved
    return {(s, t) for (s, t) in edges}


def stage_ids(vs):
    return [s["id"] for v in vs["value_streams"] for s in v["stages"]]


def main():
    data = yaml.safe_load(PROC.read_text(encoding="utf-8"))
    vs = yaml.safe_load(STAGES.read_text(encoding="utf-8"))
    draft_text, bc_of, events_of = load_draft()
    edges = load_edges()
    bcs = set(bc_of.values())

    procs = data["processes"]
    gaps = {g["id"]: g for g in data.get("gaps", [])}
    all_stages = stage_ids(vs)
    stage_set = set(all_stages)
    pids = [p["id"] for p in procs]
    if len(pids) != len(set(pids)):
        err("duplicate process ids")
    pid_set = set(pids)

    for g in gaps.values():
        for k in ("kind", "severity", "title", "detail", "back_to", "change"):
            if not g.get(k):
                err(f"{g['id']}: missing {k}")
        if g.get("kind") not in GAP_KINDS:
            err(f"{g['id']}: unknown kind {g.get('kind')}")
        for f in g.get("flows", []):
            if f not in pid_set:
                err(f"{g['id']}: unknown flow {f}")
    if len(gaps) != len(data.get("gaps", [])):
        err("duplicate gap ids")

    def check_agg(ref, where, owner_bc, relative_to=None):
        if ref not in bc_of:
            err(f"{where}: unknown aggregate {ref}")
            return
        base = relative_to or owner_bc
        if base in NON_BC_OWNERS or base.startswith("platform."):
            return
        b = bc_of[ref]
        if b != base and (base, b) not in edges:
            err(f"{where}: direction {base} -> {b} ({ref}) is not an edge in section 4.1")

    covered, touched = set(), set()
    named_total = derived_total = 0
    nosub, time_flows = {}, []
    human_n = 0
    step_n = write_n = read_n = task_n = 0
    c_exceptions = []

    for p in procs:
        pid = p["id"]
        for k in ("name", "trigger", "stages", "steps", "owner_bc", "human_orchestration"):
            if k not in p:
                err(f"{pid}: missing {k}")
        if not isinstance(p.get("human_orchestration"), bool):
            err(f"{pid}: human_orchestration must be true/false")
        elif p["human_orchestration"]:
            human_n += 1
            if not p.get("human_note"):
                err(f"{pid}: human_orchestration true needs human_note")
        owner_bc = p.get("owner_bc", "")
        if owner_bc not in bcs and owner_bc not in NON_BC_OWNERS and not owner_bc.startswith("platform."):
            err(f"{pid}: unknown owner_bc {owner_bc}")

        for s in p.get("stages", []):
            if s not in stage_set:
                err(f"{pid}: unknown stage {s}")
            covered.add(s)
        if not p.get("stages"):
            err(f"{pid}: no stages")

        t = p.get("trigger") or {}
        tt = t.get("type")
        if tt not in TRIGGER_TYPES:
            err(f"{pid}: trigger type must be one of {sorted(TRIGGER_TYPES)}")
        elif tt in ("time", "human", "call") and not t.get("desc"):
            err(f"{pid}: trigger {tt} needs desc")
        if tt == "time":
            time_flows.append(pid)
        if tt == "event":
            if not t.get("events"):
                err(f"{pid}: event trigger needs events")
            for e in t.get("events", []):
                a, ev = e.get("aggregate"), e.get("event")
                if a not in events_of:
                    err(f"{pid}: trigger aggregate {a} unknown")
                    continue
                if ev not in events_of[a]:
                    err(f"{pid}: trigger event '{ev}' is not an event of {a}")
                    continue
                if events_of[a][ev] and owner_bc not in NON_BC_OWNERS:
                    nosub.setdefault((a, ev), []).append(pid)
                if owner_bc not in NON_BC_OWNERS and not owner_bc.startswith("platform.") and a in bc_of:
                    b = bc_of[a]
                    if b != owner_bc and (owner_bc, b) not in edges:
                        err(f"{pid}: trigger event of {a} comes from {b}, not upstream of {owner_bc}")

        if not p.get("steps"):
            err(f"{pid}: no steps")
        for i, s in enumerate(p.get("steps", []), 1):
            where = f"{pid} step {i}"
            step_n += 1
            if not s.get("actor"):
                err(f"{where}: missing actor")
            for gref in ([s["gap"]] if s.get("gap") else []):
                if gref not in gaps:
                    err(f"{where}: unknown gap {gref}")
            if s.get("calls") and s["calls"] not in pid_set:
                err(f"{where}: calls unknown process {s['calls']}")
            if s.get("read"):
                read_n += 1
                for a in s.get("reads", []):
                    check_agg(a, where, owner_bc)
                    touched.add(("read", a))
                if not s.get("reads"):
                    err(f"{where}: read step needs reads")
                continue
            if s.get("platform_task"):
                task_n += 1
                if not s.get("service") or not s.get("command"):
                    err(f"{where}: platform_task needs service and command")
                continue
            write_n += 1
            cmd, agg = s.get("command"), s.get("aggregate")
            if not cmd:
                err(f"{where}: non-read step has no command")
            if not agg:
                err(f"{where}: non-read step has no aggregate")
                continue
            check_agg(agg, where, owner_bc)
            touched.add(("write", agg))
            if agg.startswith("REF-") and not s.get("gap"):
                err(f"{where}: {agg} has no aggregate; the step must reference a gap")
            if s.get("named"):
                named_total += 1
                if cmd and cmd not in draft_text:
                    err(f"{where}: command '{cmd}' is marked named but is not in the aggregate draft")
            else:
                derived_total += 1
            for ev in s.get("emits", []) or []:
                if ev not in events_of.get(agg, {}):
                    err(f"{where}: event '{ev}' is not an event of {agg}")
            if s.get("also"):
                if not s.get("c_exception") or s["c_exception"] not in draft_text:
                    err(f"{where}: `also` needs a c_exception that exists in the draft")
                else:
                    c_exceptions.append((pid, i, s["c_exception"]))
                for a in s["also"]:
                    check_agg(a, where, owner_bc)
                    touched.add(("write", a))
            for inn in s.get("inner", []) or []:
                if not inn.get("command"):
                    err(f"{where}: inner step needs command")
                check_agg(inn.get("aggregate"), where + " inner", owner_bc, relative_to=bc_of.get(agg))
                touched.add(("write", inn.get("aggregate")))

    for sw in data.get("stages_without_process", []) or []:
        st = sw.get("stage")
        if st not in stage_set:
            err(f"stages_without_process: unknown stage {st}")
        if not sw.get("reason"):
            err(f"stages_without_process {st}: missing reason")
        covered.add(st)
    uncovered = [s for s in all_stages if s not in covered]
    for s in uncovered:
        err(f"stage {s} is covered by no process and not listed in stages_without_process")

    written = {a for k, a in touched if k == "write"}
    agg_all = sorted((a for a in bc_of if a.startswith("AGG-")), key=lambda x: int(x[4:]))
    no_command = [a for a in agg_all if a not in written]
    gap_use = {}
    for p in procs:
        for s in p.get("steps", []):
            if s.get("gap"):
                gap_use.setdefault(s["gap"], 0)
                gap_use[s["gap"]] += 1

    by_kind = {}
    for g in gaps.values():
        by_kind[g["kind"]] = by_kind.get(g["kind"], 0) + 1

    print(f"流程数：{len(procs)}；人工编排流程：{human_n}；触发类型：" + "，".join(
        f"{k} {sum(1 for p in procs if p['trigger'].get('type') == k)}" for k in sorted(TRIGGER_TYPES)))
    print(f"步骤数：{step_n}（改状态 {write_n}，只读 {read_n}，平台任务 {task_n}）；"
          f"命令名字面见于聚合稿 {named_total}，反推 {derived_total}")
    print(f"阶段数：{len(all_stages)}；已覆盖：{len(covered & stage_set)}；无需系统支持：{len(data.get('stages_without_process') or [])}")
    print(f"gaps 数：{len(gaps)}（" + "，".join(f"{k} {v}" for k, v in sorted(by_kind.items())) + "）")
    print(f"C 类同事务步骤：{len(c_exceptions)}（" + "，".join(sorted({c for _, _, c in c_exceptions})) + "）")
    print("定时触发的流程：" + (str(len(time_flows))))
    print("以订阅方为「暂无」的事件作触发：" + (
        "；".join(f"{a} {ev}（{'、'.join(v)}）" for (a, ev), v in sorted(nosub.items())) or "无"))
    print(f"未被任何流程写入的聚合（{len(no_command)} 个）：" + "、".join(no_command))
    for e in errors:
        print("违规：" + e)
    print("结果：" + ("通过" if not errors else f"{len(errors)} 处违规"))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
