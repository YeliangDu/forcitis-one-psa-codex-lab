"""Closeout coverage check (read-only): do the three closeout process drafts line up with the aggregates and objects?

Usage: python3 -I closeout_check.py [--local] [--extra PATH]... [--write] | --self-test
  default       read processes.<group>.yaml, SAMPLE.md and data-model.<group>.yaml from origin/claude/one-arch-<group>-closeout (git show)
  --local       read them from closeout/<group>/ in this working tree instead
  --extra PATH  overlay process units by ID, in argument order; new IDs are appended
  --write       also write COVERAGE_REPORT.md next to this script

Checks per step (a: aggregate exists in section 2.2 of ENTERPRISE_AGGREGATES_DRAFT.md or in PRC_ALLOCATIONS.md new aggregates;
b: command is in the aggregate's command column; c: aggregate -> information-map object correspondence; d: PRC-136-144 and AGG-113-116 present).
Steps with actor platform / platform_task / bc platform.* and read-only steps count as no command. Commands marked 〔缺〕 count as proposed new commands.
Findings only, no judgement of design quality; exit code is always 0.

--qc  quality-checklist items instead (PROCESS_QUALITY_CHECKLIST.md): approval.why_human non-empty; approval.approver is cost_object_owner or the A of
      the object x command in RESPONSIBILITY_MATRIX.yaml (--matrix PATH, default closeout/RESPONSIBILITY_MATRIX.yaml); escalation_rules only guard: approval
      rules of APPROVAL_RULES.yaml; user tasks (actor SH-*, where in_system) have permission and page; every decision exit / timeout / outcome has a scenario
      in scenarios.<group>.yaml (heuristic text match, only if the file exists); task and command names in glossary terms.yaml or in the group's NOTES
      "新用语" sections (report only). Report only, exit code 0; --all lists every finding instead of the first 12 per item.
"""
import argparse
import collections
import difflib
import re
import subprocess
import sys
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parents[1] / "proposals" / "closeout"  # its data stays in proposals/closeout; the module moved to panorama/ (O16, G04)
PROPOSALS = HERE.parent
REPO = PROPOSALS.parents[3]
DRAFT = PROPOSALS / "ENTERPRISE_AGGREGATES_DRAFT.md"
ALLOC = HERE / "PRC_ALLOCATIONS.md"
GROUPS = ["revenue", "finance", "people"]
MISSING = "〔缺〕"
KINDS = ["一对一", "次级在内", "一拆多", "无聚合", "技术聚合"]


def cells(line):
    return [c.strip() for c in line.strip().strip("|").split("|")]


def split_top(text, seps="；"):
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


def parse_commands(cell):
    out = []
    for item in split_top(cell):
        if item.startswith("—"):
            continue
        out.append(re.split(r"[（(]", item, 1)[0].strip())
    return out


def load_aggregates():
    aggs, bc, idx = {}, None, None
    section = None
    for line in DRAFT.read_text(encoding="utf-8").splitlines():
        if line.startswith("### "):
            section = line[4:].split(" ")[0]
        if section != "2.2":
            continue
        m = re.match(r"^\*\*(B-\d+)\b", line)
        if m:
            bc = m.group(1)
            continue
        if line.startswith("| #"):
            h = cells(line)
            idx = {
                "name": next(i for i, c in enumerate(h) if c.startswith("聚合")),
                "inner": next(i for i, c in enumerate(h) if c.startswith("内含")),
                "corr": next(i for i, c in enumerate(h) if "对应" in c),
                "cmd": next(i for i, c in enumerate(h) if c.startswith("命令")),
            }
            continue
        m = re.match(r"^\|\s*((?:AGG|REF)-\d+)\s*\|", line)
        if m and bc and idx:
            row = cells(line)
            name_cell, corr = row[idx["name"]], row[idx["corr"]]
            kinds = [k for k in KINDS if k in corr]
            if not kinds and "配置" in corr:
                kinds = ["配置型"]
            aggs[m.group(1)] = {
                "bc": bc,
                "name": re.split(r"[（(]", name_cell, 1)[0].strip(),
                "objs": re.findall(r"OBJ-\d+", name_cell),
                "inner_objs": re.findall(r"OBJ-\d+", row[idx["inner"]]),
                "kind": kinds[0] if kinds else "未标",
                "corr": corr,
                "config": "配置" in name_cell + corr,
                "commands": parse_commands(row[idx["cmd"]]),
                "source": "聚合稿",
            }
    return aggs


def load_allocations():
    new_aggs, prcs = {}, {}
    mode = None
    for line in ALLOC.read_text(encoding="utf-8").splitlines():
        if line.startswith("## 新增聚合"):
            mode = "agg"
        elif line.startswith("# "):
            mode = "prc"
        m = re.match(r"^\|\s*(AGG-\d+)\s*\|", line)
        if m and mode == "agg":
            r = cells(line)
            new_aggs[m.group(1)] = {"name": r[1], "bc": re.split(r"[；（( ]", r[2], 1)[0], "group": r[3]}
        m = re.match(r"^\|\s*(PRC-\d+)\s*\|", line)
        if m and mode == "prc":
            r = cells(line)
            prcs[m.group(1)] = {"name": r[1], "group": r[2]}
    return new_aggs, prcs


def git_show(branch, path):
    r = subprocess.run(["git", "-C", str(REPO), "show", f"origin/{branch}:{path}"], capture_output=True, text=True)
    return r.stdout if r.returncode == 0 else None


def read_group_file(group, kind, local):
    base = "docs/modernization/architecture/proposals/closeout/" + group
    name = {"proc": f"processes.{group}.yaml", "dm": f"data-model.{group}.yaml", "sample": "SAMPLE.md", "scen": f"scenarios.{group}.yaml"}[kind]
    if local:
        p = HERE / group / name
        return p.read_text(encoding="utf-8") if p.exists() else None
    return git_show(f"claude/one-arch-{group}-closeout", f"{base}/{name}")


def units_of(doc):
    """Yield (list key, unit dict) for every list of units in a processes yaml."""
    for key, val in (doc or {}).items():
        if isinstance(val, list):
            for u in val:
                if isinstance(u, dict) and "id" in u:
                    yield key, u


def extra_paths(argv):
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--extra", action="append", type=Path, default=[])
    return parser.parse_known_args(argv)[0].extra


def load_extra_units(paths):
    units = {}
    for path in paths:
        try:
            doc = yaml.safe_load(path.read_text(encoding="utf-8"))
        except (OSError, yaml.YAMLError) as error:
            raise ValueError(f"--extra {path}：无法读取骨架：{error}") from error
        if not isinstance(doc, dict) or not list(units_of(doc)):
            raise ValueError(f"--extra {path}：骨架必须是含流程列表的 YAML 映射，且至少有一个流程")
        meta = doc.get("meta", {})
        if not isinstance(meta, dict) or (meta.get("group") is not None and not isinstance(meta["group"], str)):
            raise ValueError(f"--extra {path}：meta 必须是映射，group 必须是组名字符串")
        group = meta.get("group")
        for key, val in doc.items():
            if not isinstance(val, list):
                continue
            for unit in val:
                if not isinstance(unit, dict) or not isinstance(unit.get("id"), str) or not unit["id"].strip() or not isinstance(unit.get("name"), str) or not unit["name"].strip():
                    raise ValueError(f"--extra {path}：{key} 中每个流程必须有非空 id 和 name")
                if unit.get("group") is not None and not isinstance(unit["group"], str):
                    raise ValueError(f"--extra {path}：{unit['id']} 的 group 必须是组名字符串")
                units[unit["id"]] = (key, unit, group)
    return units


def merge_process_docs(docs, extra):
    owners = {u["id"]: g for g, doc in docs.items() for _, u in units_of(doc)}
    out = {g: {k: [u for u in v if not isinstance(u, dict) or u.get("id") not in extra] if isinstance(v, list) else v
               for k, v in doc.items()} for g, doc in docs.items()}
    for pid, (key, unit, group) in extra.items():
        target = owners.get(pid) or unit.get("group") or group or "extra"
        out.setdefault(target, {}).setdefault(key, []).append(unit)
    return out


def load_process_docs(local, extra=None):
    docs = {}
    for g in GROUPS:
        txt = read_group_file(g, "proc", local)
        if txt:
            docs[g] = yaml.safe_load(txt) or {}
        else:
            print(f"警告：读不到 {g} 组骨架（{'本地' if local else 'origin 分支'}）", file=sys.stderr)
    return merge_process_docs(docs, extra or {})


def overlay_catalog(catalog, extra):
    out = {p["id"]: dict(p) for p in catalog}
    types = {"processes": "business", "event_policies": "event_policy", "single_aggregate": "single_aggregate", "read_models": "read_model"}
    for pid, (key, unit, group) in extra.items():
        prior = out.get(pid, {})
        out[pid] = dict(prior, **unit)
        out[pid]["type"] = unit.get("type") or types.get(key, prior.get("type", "business"))
        out[pid].setdefault("group", group or "extra")
    return list(out.values())


def unit_steps(u):
    steps = list(u.get("steps") or [])
    if isinstance(u.get("step"), dict):
        steps.append(u["step"])
    return steps


def transition_approvals(u):
    """Single-aggregate commands / transitions that carry an approval, as self-contained steps (CONVENTIONS section 5)."""
    out, seen = [], set()
    for t in (u.get("commands") or []) + (u.get("transitions") or []):
        if isinstance(t, dict) and t.get("approval") and (t.get("aggregate"), t.get("command")) not in seen:
            seen.add((t.get("aggregate"), t.get("command")))
            out.append({"aggregate": t.get("aggregate") or u.get("aggregate"), "command": t.get("command"), "actor": t.get("actor") or (t.get("by") or [None])[0],
                        "approval": t["approval"], "transition": f"{t.get('from')}→{t.get('to')}"})
    return out


def classify(step, unit):
    if step.get("read") is True:
        return "read"
    if step.get("actor") == "platform" or step.get("platform_task") or str(step.get("bc", "")).startswith("platform."):
        return "platform"
    if str(unit.get("owner_bc", "")).startswith("platform.") and not step.get("aggregate"):
        return "platform"
    cmd = step.get("command")
    if cmd:
        return "proposed" if MISSING in str(cmd) else "command"
    return "nocommand"


def main(extra=None):
    local = "--local" in sys.argv
    aggs = load_aggregates()
    new_aggs, alloc_prcs = load_allocations()
    all_aggs = dict(aggs)
    for k, v in new_aggs.items():
        if k not in all_aggs:
            all_aggs[k] = {"bc": v["bc"], "name": v["name"], "objs": [], "inner_objs": [], "kind": "新增聚合（无对象对应）",
                           "corr": "", "config": False, "commands": [], "source": "PRC_ALLOCATIONS"}

    all_cmds = collections.defaultdict(set)
    for a, v in aggs.items():
        for c in v["commands"]:
            all_cmds[c].add(a)

    result = {}
    gaps = []
    notes = []
    prc_seen = collections.defaultdict(set)
    dm_aggs = {}

    def run(label, doc, sample=False):
        st = collections.Counter()
        used = collections.defaultdict(set)
        nunits = 0
        for key, u in units_of(doc):
            nunits += 1
            prc_seen[u["id"]].add(label)
            steps = unit_steps(u)
            for n, s in enumerate(steps, 1):
                kind = classify(s, u)
                st["steps"] += 1
                st[kind] += 1
                where = (label, u["id"], f"第{n}步" if len(steps) > 1 or "steps" in u else "单步", str(s.get("command") or s.get("desc") or "")[:30])
                if s.get("gap"):
                    st["gap_marked"] += 1
                if s.get("pending"):
                    st["pending_marked"] += 1
                if kind == "platform" and MISSING in str(s.get("command", "")):
                    st["platform_missing"] += 1
                    gaps.append((*where, "平台步骤的命令标〔缺〕（平台模块无此命令，不计入命令）", str(s.get("command"))))
                if kind == "nocommand":
                    gaps.append((*where, "无命令步骤（外部或线下动作，只有说明，本系统无命令可对应）", str(s.get("desc") or "")[:40]))
                refs = []
                if s.get("aggregate"):
                    refs.append((s["aggregate"], s.get("command"), True))
                for inner in s.get("inner") or []:
                    refs.append((inner.get("aggregate"), inner.get("command"), True))
                for a in s.get("also") or []:
                    refs.append((a, None, False))
                for a in s.get("reads") or []:
                    refs.append((a, None, False))
                for a, cmd, need_cmd in refs:
                    if not isinstance(a, str) or not re.fullmatch(r"(AGG|REF)-\d+", a):
                        gaps.append((*where, "聚合缺失或写法不是 AGG-NN", f"aggregate={a}"))
                        st["agg_bad"] += 1
                        continue
                    used[a].add(u["id"])
                    if a not in all_aggs:
                        gaps.append((*where, "聚合不在聚合稿 §2.2，也不在 PRC_ALLOCATIONS 新增聚合", a))
                        st["agg_missing"] += 1
                        continue
                    if a in new_aggs and a not in aggs:
                        st["agg_new_only"] += 1
                    if not need_cmd or not cmd or classify(s, u) in ("read", "platform"):
                        continue
                    if MISSING in str(cmd):
                        gaps.append((*where, "提议新增命令（〔缺〕）", f"{a} {cmd}"))
                        continue
                    base = re.split(r"[（(]", cmd, 1)[0].strip()
                    st["cmd_checked"] += 1
                    if base in aggs.get(a, {}).get("commands", []):
                        st["cmd_ok"] += 1
                    else:
                        elsewhere = sorted(all_cmds.get(base, []))
                        near = difflib.get_close_matches(base, aggs.get(a, {}).get("commands", []), n=1, cutoff=0.6)
                        reason = f"命令不在 {a}（{all_aggs[a]['name']}）命令列"
                        if a in new_aggs and a not in aggs:
                            reason = f"{a} 是新增聚合，聚合稿无命令列可核对"
                            st["cmd_newagg"] += 1
                        elif elsewhere:
                            reason += f"；该命令名在 {'、'.join(elsewhere)} 下出现"
                        elif near:
                            reason += f"；近似：{near[0]}"
                        gaps.append((*where, reason, f"{a} {cmd}"))
            if key in ("processes",) and "steps" in u and not steps:
                gaps.append((label, u["id"], "全流程", "", "流程没有步骤", ""))
            if not steps and u["id"].startswith("PRC-") and key == "processes":
                pass
            if not steps:
                st["units_without_steps"] += 1
        st["units"] = nunits
        if nunits == 0:
            return
        result[label] = (st, used)

    docs = load_process_docs(local, extra)
    groups = list(dict.fromkeys(GROUPS + list(docs)))
    for g in groups:
        if g not in docs:
            notes.append(f"{g}: 取不到 processes.{g}.yaml")
            continue
        run(g, docs[g])
        if g not in GROUPS:
            continue
        dm = read_group_file(g, "dm", local)
        if dm:
            for e in yaml.safe_load(dm) or []:
                if isinstance(e, dict) and "aggregate" in e:
                    dm_aggs[e["aggregate"]] = g
            dmtext = dm
            dm_aggs.update({a: g for a in set(re.findall(r"\bAGG-\d+\b", dmtext)) if a not in dm_aggs})
        else:
            notes.append(f"{g}: 取不到 data-model.{g}.yaml")

    sample = read_group_file("people", "sample", local)
    sample_blocks = 0
    if sample:
        for blk in re.findall(r"```yaml\n(.*?)```", sample, re.S):
            try:
                d = yaml.safe_load(blk)
            except yaml.YAMLError:
                continue
            if isinstance(d, list):
                d = {"processes": d}
            if isinstance(d, dict):
                sample_blocks += 1
                run(f"people-样板#{sample_blocks}", d, sample=True)
    notes.append(f"people SAMPLE.md 的 yaml 块 {sample_blocks} 个，作为附加行核对，不并入三组合计；revenue 与 finance 的 SAMPLE.md 是表格，样板流程取自各自 processes 文件，已包含在组内")

    # d) PRC-136-144 and AGG-113-116
    prc_status = {}
    for n in range(136, 145):
        pid = f"PRC-{n}"
        prc_status[pid] = (sorted(prc_seen.get(pid, [])), alloc_prcs.get(pid, {}).get("group", "?"), alloc_prcs.get(pid, {}).get("name", ""))
    agg_status = {}
    for n in range(113, 119):
        a = f"AGG-{n}"
        defined = a in aggs
        dmg = dm_aggs.get(a)
        used_in = sorted({g for g, (_s, used) in result.items() if a in used})
        agg_status[a] = (new_aggs.get(a, {}).get("name", ""), new_aggs.get(a, {}).get("bc", ""), defined, dmg, used_in)

    # c) aggregate -> object, over aggregates used by the three groups
    group_used = collections.defaultdict(set)
    for g in groups:
        if g in result:
            for a in result[g][1]:
                group_used[g].add(a)
    all_used = set().union(*group_used.values()) if group_used else set()
    dist = {g: collections.Counter() for g in groups}
    dist["合计（去重）"] = collections.Counter()
    for g in groups:
        for a in group_used[g]:
            v = all_aggs.get(a)
            if v:
                dist[g][("配置型·" if v["config"] else "") + v["kind"]] += 1
    for a in all_used:
        v = all_aggs.get(a)
        if v:
            dist["合计（去重）"][("配置型·" if v["config"] else "") + v["kind"]] += 1
    obj_gaps = []
    for a in sorted(all_used):
        v = all_aggs.get(a)
        if not v:
            continue
        if v["kind"] in ("一对一", "次级在内", "一拆多") and not (v["objs"] or v["inner_objs"]):
            obj_gaps.append((a, v["name"], f"对应方式为{v['kind']}但名称与内含列都没有 OBJ-NN"))
        if v["kind"] == "未标":
            obj_gaps.append((a, v["name"], "「对应」列取值不在五种之内：" + v["corr"][:40]))
        if a in new_aggs and a not in aggs:
            obj_gaps.append((a, v["name"], "新增聚合，聚合稿未收，无信息图对象对应"))

    return dict(result=result, gaps=gaps, notes=notes, prc_status=prc_status, agg_status=agg_status, dist=dist,
                aggs=aggs, all_aggs=all_aggs, group_used=group_used, obj_gaps=obj_gaps, new_aggs=new_aggs, groups=groups)


def pct(a, b):
    return f"{a}/{b} = {a / b * 100:.1f}%" if b else "—"


def render(R):
    out = []
    P = out.append
    res = R["result"]
    groups = R["groups"]
    tot = collections.Counter()
    for g in groups:
        if g in res:
            tot.update(res[g][0])
    cmd_steps = tot["command"] + tot["proposed"]
    P("# 收尾三组流程初稿对聚合与对象的对应检查（COVERAGE_REPORT）")
    P("")
    P("脚本：`closeout/closeout_check.py`（只读）。输入：三个分支 `claude/one-arch-{revenue,finance,people}-closeout` 的 `processes.<组>.yaml`、`data-model.<组>.yaml` 与 `SAMPLE.md`，对照 `ENTERPRISE_AGGREGATES_DRAFT.md` §2.2 与 `closeout/PRC_ALLOCATIONS.md`。分支是初稿、可能不完整，本页只做对应检查，不评价设计好坏。")
    P("")
    P("## 一屏结论")
    P("")
    P(f"- 三组及附加骨架共 {sum(res[g][0]['units'] for g in groups if g in res)} 个流程单元、{tot['steps']} 步；其中命令步骤 {cmd_steps}（含提议新增 {tot['proposed']}），只读 {tot['read']}，平台 {tot['platform']}，无命令（外部或线下动作，只有说明）{tot['nocommand']}。")
    P(f"- 命令能在聚合稿命令列查到：{pct(tot['cmd_ok'], tot['cmd_checked'])}（已核对 {tot['cmd_checked']} 条有聚合的命令；另有 {tot['cmd_newagg']} 条落在无命令列的新增聚合上，无从核对；提议新增命令 {tot['proposed']} 条另计）。")
    P(f"- 聚合缺失（不在聚合稿也不在新增登记）：{tot['agg_missing'] + tot['agg_bad']} 处。")
    pr = R["prc_status"]
    miss_prc = [k for k, v in pr.items() if not v[0]]
    P(f"- PRC-136–144 共 9 个，已出现在 yaml：{9 - len(miss_prc)} 个；未出现：{'、'.join(miss_prc) or '无'}。")
    ag = R["agg_status"]
    nodef = [a for a in ("AGG-113", "AGG-114", "AGG-115", "AGG-116") if not ag[a][2] and not ag[a][3]]
    P(f"- B-31 的 AGG-113–116：聚合稿或 data-model 里有定义的 {4 - len(nodef)} 个；没有定义：{'、'.join(nodef) or '无'}（PRC_ALLOCATIONS 只登记了号与名称）。")
    d = R["dist"]["合计（去重）"]
    P("- 三组触及的聚合到信息图对象的对应方式：" + "、".join(f"{k} {v}" for k, v in d.most_common()) + f"（共 {sum(d.values())} 个）。")
    P(f"- 平台步骤里命令标〔缺〕（平台模块没有此命令，不计入命令）：{tot['platform_missing']} 处；步骤带 `gap` 标记 {tot['gap_marked']} 处、带 `pending` 标记 {tot['pending_marked']} 处（各组自己登记的未决项，未逐条核对）。")
    P("")
    P("## 一、各组数字")
    P("")
    P("| 组 | 流程单元 | 步骤 | 命令步骤 | 命令可查到（已核对） | 提议新增命令 | 只读 | 平台 | 无命令步骤 | 聚合缺失 |")
    P("|---|---|---|---|---|---|---|---|---|---|")
    for g in list(res):
        s = res[g][0]
        P(f"| {g} | {s['units']} | {s['steps']} | {s['command'] + s['proposed']} | {pct(s['cmd_ok'], s['cmd_checked'])} | {s['proposed']} | {s['read']} | {s['platform']} | {s['nocommand']} | {s['agg_missing'] + s['agg_bad']} |")
    P(f"| 合计 | {sum(res[g][0]['units'] for g in groups if g in res)} | {tot['steps']} | {cmd_steps} | {pct(tot['cmd_ok'], tot['cmd_checked'])} | {tot['proposed']} | {tot['read']} | {tot['platform']} | {tot['nocommand']} | {tot['agg_missing'] + tot['agg_bad']} |")
    P("")
    P("口径：命令步骤 = 有 `command` 且不是只读与平台步骤；步骤里的 `inner`、`also`、`reads` 中的聚合也核对存在性，`inner` 的命令一并核对命令列。事件策略与单聚合维护的单元也计入。finance 的 `event_policies_cross_bc`、`single_aggregate_cross_bc` 只有接缝，没有步骤，只计单元数。")
    for n in R["notes"]:
        P(f"- 说明：{n}")
    P("")
    P("## 二、聚合到信息图对象的对应方式（三组实际触及的聚合）")
    P("")
    kinds = []
    for g in groups:
        kinds += list(R["dist"][g])
    kinds += list(R["dist"]["合计（去重）"])
    kinds = sorted(set(kinds), key=lambda k: (k.replace("配置型·", ""), k))
    P("| 对应方式 | " + " | ".join(groups) + " | 合计（去重） |")
    P("|---|" + "---|" * (len(groups) + 1))
    for k in kinds:
        P(f"| {k} | " + " | ".join(str(R["dist"][g][k]) for g in groups) + f" | {R['dist']['合计（去重）'][k]} |")
    P("")
    P("取自聚合稿 §2.2 表「对应」列；「配置型·」表示聚合名或对应列含「配置」。组间聚合会重叠（同一聚合被多组触及），合计列去重。")
    P("")
    P("## 三、PRC-136–144 与 AGG-113–118")
    P("")
    P("| 流程号 | 名称 | 登记组 | 出现在哪个组的 yaml |")
    P("|---|---|---|---|")
    for k, (seen, grp, name) in pr.items():
        P(f"| {k} | {name} | {grp} | {'、'.join(seen) or '**未出现**'} |")
    P("")
    P("| 聚合号 | 名称 | BC | 聚合稿 §2.2 | 组 data-model | 被流程触及 |")
    P("|---|---|---|---|---|---|")
    for a, (name, bc, defined, dmg, used) in ag.items():
        P(f"| {a} | {name} | {bc} | {'有' if defined else '无'} | {dmg or '无'} | {'、'.join(used) or '无'} |")
    P("")
    P("## 四、缺口清单")
    P("")
    P("每条带组、流程号、步骤、原因。")
    P("")
    gaps = R["gaps"]
    by_reason = collections.defaultdict(list)
    for g in gaps:
        key = "提议新增命令（〔缺〕）" if g[4].startswith("提议新增") else ("平台步骤命令标〔缺〕" if g[4].startswith("平台步骤") else ("无命令步骤" if g[4].startswith("无命令") else None)) or ("命令不在聚合命令列" if g[4].startswith("命令不在") else ("新增聚合无命令列" if "新增聚合，聚合稿无命令列" in g[4] else g[4]))
        by_reason[key].append(g)
    P("分类汇总：" + "；".join(f"{k} {len(v)} 条" for k, v in by_reason.items()))
    P("")
    for key, items in by_reason.items():
        P(f"### {key}（{len(items)}）")
        P("")
        P("| 组 | 流程 | 步骤 | 内容 | 原因 |")
        P("|---|---|---|---|---|")
        for (g, pid, step, _d, reason, detail) in items:
            P(f"| {g} | {pid} | {step} | {detail} | {reason} |")
        P("")
    P("### 聚合到对象的缺口")
    P("")
    P("| 聚合 | 名称 | 原因 |")
    P("|---|---|---|")
    for a, name, why in R["obj_gaps"]:
        P(f"| {a} | {name} | {why} |")
    return "\n".join(out) + "\n"


# ---------------------------------------------------------------- --qc

GLOSSARY = REPO / "docs/modernization/glossary/terms.yaml"
RULES = HERE / "APPROVAL_RULES.yaml"
MATRIX = HERE / "RESPONSIBILITY_MATRIX.yaml"
SCENARIO_KW = ("超时", "逾期", "到期", "提醒", "升级")


def base_cmd(c):
    return re.split(r"[（(]", str(c).replace(MISSING, ""), 1)[0].strip()


def gated_target(steps, i):
    """(aggregate, command) that step i acts on; approval steps without their own command point at the next command step (CONVENTIONS section 5)."""
    def own(s):
        return s.get("aggregate") and s.get("command") and s.get("actor") != "platform" and not s.get("platform_task")
    if own(steps[i]):
        return steps[i]["aggregate"], base_cmd(steps[i]["command"])
    for j in list(range(i + 1, len(steps))) + list(range(i - 1, -1, -1)):
        if own(steps[j]):
            return steps[j]["aggregate"], base_cmd(steps[j]["command"])
    return None


def read_group_aux(group, local, pred):
    """Text of the group's files whose name satisfies pred (NOTES etc.), local or from the origin branch."""
    base = "docs/modernization/architecture/proposals/closeout/" + group
    texts = []
    if local:
        for f in sorted((HERE / group).rglob("*.md")):
            if pred(f.name):
                texts.append(f.read_text(encoding="utf-8"))
        return texts
    r = subprocess.run(["git", "-C", str(REPO), "ls-tree", "-r", "--name-only", f"origin/claude/one-arch-{group}-closeout", "--", base],
                       capture_output=True, text=True)
    for name in r.stdout.split():
        if name.endswith(".md") and pred(Path(name).name):
            t = git_show(f"claude/one-arch-{group}-closeout", name)
            if t:
                texts.append(t)
    return texts


def new_term_text(texts):
    out = []
    for t in texts:
        cap, lvl = False, 0
        for line in t.splitlines():
            m = re.match(r"^(#+)\s*(.*)", line)
            if m:
                n = len(m.group(1))
                if "新用语" in m.group(2):
                    if not cap or n <= lvl:
                        cap, lvl = True, n
                elif cap and n <= lvl:
                    cap = False
            if cap:
                out.append(line)
    return "\n".join(out)


def load_terms():
    names = set()
    d = yaml.safe_load(GLOSSARY.read_text(encoding="utf-8"))
    for t in d.get("terms", []):
        if t.get("zh"):
            names.add(str(t["zh"]))
        for a in t.get("aliases") or []:
            if isinstance(a, dict) and a.get("name") and not a.get("deprecated"):
                names.add(str(a["name"]))
    return names


def bigrams(t):
    t = re.sub(r"\W", "", t)
    return {t[i:i + 2] for i in range(len(t) - 1)} or {t}


def text_covers(target, texts, need_kw=False, thr=0.4):
    tb = bigrams(target)
    for tx in texts:
        if need_kw and not any(k in tx for k in SCENARIO_KW):
            continue
        if target and target in tx:
            return True
        if tb and len(tb & bigrams(tx)) / len(tb) >= thr:
            return True
    return False


def a_matches(approver, entry):
    a = (entry or {}).get("raci", {}).get("A")
    if a is None:
        return False
    if isinstance(a, str):
        return a == approver
    if a.get("resolve") == "instance":
        return approver == "cost_object_owner"
    return approver in (a.get("role"), a.get("id"))


def qc(local, matrix_path, show_all, extra=None):
    rules = {r["id"]: r for r in yaml.safe_load(RULES.read_text(encoding="utf-8"))["rules"]}
    matrix = {}
    if matrix_path and Path(matrix_path).exists():
        for e in (yaml.safe_load(Path(matrix_path).read_text(encoding="utf-8")) or {}).get("entries", []):
            matrix[(e["object"], base_cmd(e["command"]))] = e
    terms = load_terms()
    lim = None if show_all else 12
    out = [f"# 流程质量检查（脚本项，只报告）  权责对照表：{'有 ' + str(len(matrix)) + ' 条' if matrix else '无（只认 cost_object_owner）'}", ""]
    summary = []
    detail = collections.defaultdict(list)

    def hit(item, g, pid, n, msg):
        detail[item].append(f"{g} {pid} 第{n}步：{msg}")
        cnt[item] += 1

    docs = load_process_docs(local, extra)
    for g in dict.fromkeys(GROUPS + list(docs)):
        if g not in docs:
            out.append(f"- {g}: 取不到 processes.{g}.yaml")
            continue
        doc = docs[g]
        sc_text = read_group_file(g, "scen", local)
        scen = yaml.safe_load(sc_text) if sc_text else None
        notes = new_term_text(read_group_aux(g, local, lambda n: "notes" in n.lower()))
        cnt = collections.Counter()
        tot = collections.Counter()
        termstat = collections.Counter()
        missing_terms = set()
        for key, u in units_of(doc):
            steps = unit_steps(u) + transition_approvals(u)
            uscen = [json_text(x) for x in (scen or {}).get(u["id"], [])] if isinstance(scen, dict) else None
            for n, s in enumerate(steps, 1):
                ap = s.get("approval")
                if ap:
                    tot["approval"] += 1
                    if not str(ap.get("why_human") or "").strip():
                        hit("1 why_human 为空", g, u["id"], n, f"{ap.get('doc_type')}")
                    appr = ap.get("approver")
                    tgt = gated_target(steps, n - 1)
                    eng = ap.get("engine")
                    if eng == "external":
                        pass
                    elif eng == "aggregate" and isinstance(appr, str) and appr.startswith("SH-") and not (tgt and (tgt[0], tgt[1]) in matrix):
                        pass
                    elif appr != "cost_object_owner":
                        entry = matrix.get((tgt[0], tgt[1])) if tgt else None
                        if not a_matches(appr, entry):
                            why = "权责对照表无此对象×命令" if entry is None else "不等于表中的 A"
                            hit("2 approver 不是 cost_object_owner 也不等于 A 表", g, u["id"], n,
                                f"{ap.get('doc_type')} approver={appr}；对象×命令={tgt}；{why}")
                    for rid in ap.get("escalation_rules") or []:
                        r = rules.get(rid)
                        if r is None:
                            hit("3 escalation_rules 引用无效", g, u["id"], n, f"{rid} 不在 APPROVAL_RULES")
                        elif r.get("guard") != "approval":
                            hit("3 escalation_rules 引用无效", g, u["id"], n, f"{rid} guard={r.get('guard')}，应交规则卡而非审批")
                    if "escalation_rules" not in ap and "escalation" not in ap:
                        tot["no_escalation_field"] += 1
                if str(s.get("actor", "")).startswith("SH-") and s.get("where") == "in_system" and not s.get("read"):
                    tot["user_task"] += 1
                    for f in ("permission", "page"):
                        if not str(s.get(f) or "").strip():
                            hit(f"4 用户任务缺 {f}", g, u["id"], n, str(s.get("command") or s.get("desc") or "")[:30])
                if uscen is not None:
                    for d in s.get("decision") or []:
                        w = str(d.get("when"))
                        if w == "otherwise" and not d.get("outcome"):
                            continue
                        tot["exit"] += 1
                        if not text_covers(w if w != "otherwise" else str(d.get("outcome")), uscen):
                            hit("5 判断出口无场景", g, u["id"], n, w[:40])
                    if s.get("timeout"):
                        tot["timeout"] += 1
                        tt = str(s.get("command") or s.get("desc") or "")
                        if not text_covers(tt, uscen, need_kw=True, thr=0.3):
                            hit("5 超时无场景", g, u["id"], n, tt[:40])
                for nm in {base_cmd(s["command"]) if s.get("command") else None, (ap or {}).get("doc_type")} - {None, ""}:
                    tot["term"] += 1
                    if nm in terms:
                        termstat["词表命中"] += 1
                    elif nm in notes:
                        termstat["NOTES 新用语"] += 1
                    elif any(len(t) >= 3 and t in nm for t in terms):
                        termstat["含词表词"] += 1
                    else:
                        termstat["无"] += 1
                        missing_terms.add(nm)
            if uscen is not None:
                for o in u.get("outcomes") or []:
                    tot["outcome"] += 1
                    if not text_covers(str(o), uscen, thr=0.99):
                        hit("5 结局无场景", g, u["id"], 0, str(o))
        sc_note = "有场景文件" if scen is not None else f"无 scenarios.{g}.yaml，场景项跳过"
        summary.append((g, tot, cnt, termstat, sc_note))
        detail[("term", g)] = sorted(missing_terms)
    out += ["| 组 | 人工审批 | 无 why_human | approver 不符 | 加审引用无效 | 用户任务 | 缺 permission/page | 出口（无场景） | 超时（无场景） | 结局（无场景） | 场景 |",
            "|---|---|---|---|---|---|---|---|---|---|---|"]
    for g, tot, cnt, ts, note in summary:
        out.append(f"| {g} | {tot['approval']} | {cnt['1 why_human 为空']} | {cnt['2 approver 不是 cost_object_owner 也不等于 A 表']} | {cnt['3 escalation_rules 引用无效']} | "
                   f"{tot['user_task']} | {cnt['4 用户任务缺 permission'] + cnt['4 用户任务缺 page']} | {tot['exit']}（{cnt['5 判断出口无场景']}） | {tot['timeout']}（{cnt['5 超时无场景']}） | {tot['outcome']}（{cnt['5 结局无场景']}） | {note} |")
    out += ["", "用语（任务名=命令名与审批单据类型名）：", "", "| 组 | 总数 | 词表命中 | NOTES 新用语 | 仅含词表词 | 无 |", "|---|---|---|---|---|---|"]
    for g, tot, cnt, ts, note in summary:
        out.append(f"| {g} | {tot['term']} | {ts['词表命中']} | {ts['NOTES 新用语']} | {ts['含词表词']} | {ts['无']} |")
    out.append("")
    for item in sorted(k for k in detail if isinstance(k, str)):
        rows = detail[item]
        out.append(f"## {item}（{len(rows)}）")
        for r in rows[:lim]:
            out.append(f"- {r}")
        if lim and len(rows) > lim:
            out.append(f"- …另 {len(rows) - lim} 条（--all 全列）")
        out.append("")
    for g, *_ in summary:
        rows = detail[("term", g)]
        out.append(f"## 6 用语无着落：{g}（{len(rows)} 个，词表无命中也不在 NOTES）")
        out.append("、".join(rows[:lim or None]) + (" …" if lim and len(rows) > lim else ""))
        out.append("")
    out.append("说明：场景匹配为文本启发（条件文本与场景 path/covers/expect/outcome 的二字组重合度，或直接包含），只作线索；缺失字段 approval.why_human、escalation_rules 在初稿分支里尚未铺开，属预期缺口。")
    return "\n".join(out) + "\n"


def json_text(x):
    return " ".join(str(v) for v in x.values()) if isinstance(x, dict) else str(x)


def self_test():
    import contextlib
    import io
    import tempfile
    from unittest.mock import patch

    base = {"finance": {"read_models": [{"id": "P1", "name": "重复旧流程"}]},
            "people": {"processes": [{"id": "P1", "name": "旧", "steps": [{"command": "旧命令"}]},
                                      {"id": "P2", "name": "保留"}]}}
    with tempfile.TemporaryDirectory() as directory:
        first, last = Path(directory) / "first.yaml", Path(directory) / "last.yaml"
        first.write_text(yaml.safe_dump({"meta": {"group": "pilot"}, "processes": [
            {"id": "P1", "name": "首稿", "steps": [{"command": "首稿命令"}]}, {"id": "P3", "name": "新增"}]}), encoding="utf-8")
        last.write_text(yaml.safe_dump({"processes": [{"id": "P1", "name": "末稿"}]}), encoding="utf-8")
        paths = extra_paths(["--local", "--extra", str(first), f"--extra={last}", "--qc"])
        assert paths == [first, last]
        extra = load_extra_units(paths)
        merged = merge_process_docs(base, extra)
        all_units = {u["id"]: u for d in merged.values() for _, u in units_of(d)}
        assert set(all_units) == {"P1", "P2", "P3"}
        assert all_units["P1"] == {"id": "P1", "name": "末稿"}, all_units
        assert sum(u["id"] == "P1" for d in merged.values() for _, u in units_of(d)) == 1
        assert list(units_of(merged["pilot"]))[0][1]["id"] == "P3"
        assert base["people"]["processes"][0]["name"] == "旧"
        assert merge_process_docs(base, {}) == base
        cat = overlay_catalog([{"id": "P1", "name": "旧", "type": "business", "group": "people"}], extra)
        assert [p["id"] for p in cat] == ["P1", "P3"] and cat[0]["group"] == "people"
        with patch(__name__ + ".read_group_file", side_effect=lambda g, *_: yaml.safe_dump(base[g]) if g in base else None):
            with contextlib.redirect_stderr(io.StringIO()):
                assert load_process_docs(True, extra) == merged
        for bad in ("", "[]", "processes: []", "processes: [{name: 缺编号}]", "processes: [{id: P1}]", "processes: [1]", "processes: [", "meta: []\nprocesses: [{id: P1, name: 甲}]"):
            last.write_text(bad, encoding="utf-8")
            try:
                load_extra_units([last])
            except ValueError:
                pass
            else:
                raise AssertionError(f"无效骨架未拦下：{bad}")
        try:
            load_extra_units([Path(directory) / "missing.yaml"])
        except ValueError:
            pass
        else:
            raise AssertionError("缺失 --extra 文件未拦下")
        with contextlib.redirect_stderr(io.StringIO()):
            try:
                extra_paths(["--extra"])
            except SystemExit as error:
                assert error.code == 2
            else:
                raise AssertionError("缺少 --extra 参数值未拦下")
    print("self-test 通过：--extra 重复覆盖、追加、跨组去重、原输入不变、无效输入失败")


if __name__ == "__main__":
    if "--self-test" in sys.argv:
        self_test()
        sys.exit(0)
    try:
        extra = load_extra_units(extra_paths(sys.argv[1:]))
    except ValueError as error:
        sys.exit(str(error))
    if "--qc" in sys.argv:
        mp = sys.argv[sys.argv.index("--matrix") + 1] if "--matrix" in sys.argv else MATRIX
        print(qc("--local" in sys.argv, mp, "--all" in sys.argv, extra))
        sys.exit(0)
    R = main(extra)
    text = render(R)
    print(text)
    if "--write" in sys.argv:
        (HERE / "COVERAGE_REPORT.md").write_text(text, encoding="utf-8")
