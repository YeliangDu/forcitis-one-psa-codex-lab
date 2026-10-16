"""阶段对齐检查（只读）：流程目录与三组骨架，对照价值流阶段的进出条件。

用法: python3 -I stage_align_check.py [--local] [--no-write] [--self-test]
  默认          骨架读 origin/claude/one-arch-<组>-closeout（沿用 closeout_check.read_group_file）
  --local       骨架读 closeout/<组>/ 本地文件
  --no-write    只打印，不写 STAGE_ALIGN_REPORT.md
  --self-test   最小样例覆盖五项检查

五项：1 未落阶段的流程；2 跳段；3 阶段退出条件能否由本段流程的结局满足；4 阶段进入条件的产生方；5 稀少。
只报发现，不判设计好坏；退出码恒为 0（self-test 失败除外）。匹配口径见报告末尾。
"""
import re
import sys
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
PROPOSALS = HERE.parent
REPO = PROPOSALS.parents[3]
VS_FILE = PROPOSALS / "value-streams.v2.yaml"
CATALOG = HERE / "process-catalog.yaml"
INFO_MAP = PROPOSALS / "ENTERPRISE_INFO_MAP_DRAFT.md"
GLOSSARY = REPO / "docs/modernization/glossary/terms.yaml"
REPORT = HERE / "STAGE_ALIGN_REPORT.md"
GROUPS = ["revenue", "finance", "people"]
SKIP_REFS = ("派生", "缺对象状态")
TYPES = ["business", "event_policy", "single_aggregate", "read_model"]
SMALL_VS = 4


# ---------------------------------------------------------------- 规范化与匹配

class Norm:
    def __init__(self, alias_map=None):
        self.alias = sorted((alias_map or {}).items(), key=lambda kv: -len(kv[0]))

    def keep(self, t):
        """别名换正式用语、去标点，但保留「已」「被」。"""
        t = str(t or "")
        for a, z in self.alias:
            if a in t:
                t = t.replace(a, z)
        return re.sub(r"[\s（()）【】\[\]，,。；;：:、/→\-—_·「」\"']", "", t)

    def __call__(self, t):
        t = self.keep(t)
        stripped = t.replace("已", "").replace("被", "")
        return stripped if len(stripped) >= 2 else t   # 「已付」去掉「已」只剩一字，退回原文整体比对


def variants(state, norm):
    """一个状态原文拆成可比对的说法：整体、按 / → 拆分、括号内外分开。"""
    s = str(state)
    parts = {s}
    for seg in re.split(r"[/→]", s):
        parts.add(seg)
        parts.add(re.sub(r"[（(].*?[）)]", "", seg))
        parts.update(re.findall(r"[（(](.*?)[）)]", seg))
    out = []
    for p in parts:
        n = norm(p)
        if len(n) >= 2 and n not in out:
            out.append(n)
    return out


def bigrams(t):
    return {t[i:i + 2] for i in range(len(t) - 1)} or {t}


def match_atom(oname, state, text, norm):
    """('exact'|'approx'|None)。oname 为对象名，state 为条件里的状态原文，text 为流程结局 / 事件 / 状态原文。"""
    tn, on = norm(text), norm(re.sub(r"[（(].*?[）)]", "", oname or ""))
    if not tn:
        return None
    tk = norm.keep(text)
    best = None
    for v in variants(state, norm):
        # 状态因去「已」「被」后不足两字而保留原文时，结局也用保留形式比对
        tn = tk if re.search("[已被]", v) else norm(text)
        if v == tn or (v in tn and on and on in tn) or (on and tn == on + v):
            return "exact"
        if v in tn or (len(tn) >= 3 and tn in v):
            best = "approx"
        elif len(v) >= 3 and len(bigrams(v) & bigrams(tn)) / len(bigrams(v)) >= 0.6:
            best = "approx"
    return best


# ---------------------------------------------------------------- 条件树

def expand(node, patterns):
    """把 pattern 引用展开；pattern 与同处 all/any 取且。返回 {'all': [...]} / {'any': [...]} / 叶子 dict。"""
    if not isinstance(node, dict):
        return {"all": []}
    parts = []
    if "pattern" in node:
        parts.append(expand(patterns.get(node["pattern"]) or {"all": []}, patterns))
    if "ref" in node:
        parts.append({"leaf": node})
    for k in ("all", "any"):
        if k in node:
            parts.append({k: [expand(x, patterns) for x in node[k] or []]})
    if len(parts) == 1:
        return parts[0]
    return {"all": parts}


def leaves(t):
    if "leaf" in t:
        return [t["leaf"]]
    return [x for k in ("all", "any") for c in t.get(k, []) for x in leaves(c)]


def render_cond(t, onames):
    if "leaf" in t:
        r = t["leaf"]
        return f"{r.get('ref')}{onames.get(r.get('ref'), '')}「{r.get('state')}」"
    for k, sep in (("all", " 且 "), ("any", " 或 ")):
        if k in t:
            items = [render_cond(c, onames) for c in t[k]]
            s = sep.join(items)
            return f"({s})" if len(items) > 1 else s
    return ""


RANK = {"exact": 2, "approx": 1, None: 0}


def evaluate(t, find):
    """返回 (tier, 未满足叶子列表)。tier 为 exact / approx / None（不满足）；find(leaf) -> (tier, evidence)。"""
    if "leaf" in t:
        r = t["leaf"]
        if str(r.get("ref")) in SKIP_REFS or not str(r.get("ref", "")).startswith("OBJ-"):
            return "exact", []
        tier, _ = find(r)
        return tier, ([] if tier else [r])
    if "any" in t:
        res = [evaluate(c, find) for c in t["any"]]
        if not res:
            return "exact", []
        ok = [r for r in res if r[0]]
        if ok:
            return max((r[0] for r in ok), key=RANK.get), []
        return None, [x for r in res for x in r[1]]
    res = [evaluate(c, find) for c in t.get("all", [])]
    bad = [x for r in res for x in r[1]]
    if any(r[0] is None for r in res):
        return None, bad
    return (min((r[0] for r in res), key=RANK.get) if res else "exact"), []


# ---------------------------------------------------------------- 加载

def load_objnames(path=INFO_MAP):
    out = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        m = re.match(r"^\|\s*(OBJ-\d+)\s*\|\s*([^|]+?)\s*\|", line)
        if m:
            out[m.group(1)] = m.group(2)
    return out


def load_alias_map(path=GLOSSARY):
    amap = {}
    for t in yaml.safe_load(path.read_text(encoding="utf-8")).get("terms", []):
        zh = t.get("zh")
        for a in t.get("aliases") or []:
            n = a.get("name") if isinstance(a, dict) else None
            if zh and n and n != zh and len(str(n)) >= 3 and re.search(r"[一-鿿]", str(n)):
                amap.setdefault(str(n), str(zh))
    return amap


def load_skeletons(local):
    import closeout_check as cc
    units = {}
    for g in GROUPS:
        txt = cc.read_group_file(g, "proc", local)
        if not txt:
            print(f"警告：读不到 {g} 组骨架（{'本地' if local else 'origin 分支'}）", file=sys.stderr)
            continue
        for _, u in cc.units_of(yaml.safe_load(txt)):
            units[u["id"]] = (u, cc.unit_steps(u))
    return units


def load_machines(local):
    """三组 state-machines.yaml（object_check.read_group_yaml 同口径）：{聚合: 状态机}。"""
    import closeout_check as cc
    out = {}
    for g in GROUPS:
        if local:
            p = HERE / g / "state-machines.yaml"
            txt = p.read_text(encoding="utf-8") if p.exists() else None
        else:
            txt = cc.git_show(f"claude/one-arch-{g}-closeout", f"docs/modernization/architecture/proposals/closeout/{g}/state-machines.yaml")
        if not txt:
            print(f"警告：读不到 {g} 组状态机（{'本地' if local else 'origin 分支'}）", file=sys.stderr)
            continue
        for m in (yaml.safe_load(txt) or {}).get("machines") or []:
            out[m["aggregate"]] = m
    return out


def arrivals_of(unit, steps, machines, aggs):
    """流程改状态步骤经状态机到达的状态：[(to, 对象集合, 说明)]。聚合 + 命令在状态机里查 command 相同的转换取 to。"""
    import closeout_check as cc
    pairs = []
    for st in steps:
        if cc.classify(st, unit) != "command":
            continue
        if st.get("aggregate") and st.get("command"):
            pairs.append((st["aggregate"], st["command"]))
        pairs += [(x.get("aggregate"), x.get("command")) for x in st.get("inner") or [] if x.get("aggregate") and x.get("command")]
    for c in (unit.get("commands") or []) + (unit.get("transitions") or []):
        if isinstance(c, dict) and c.get("command"):
            pairs.append((c.get("aggregate") or unit.get("aggregate"), c["command"]))
    out, seen = [], set()
    for agg, cmd in pairs:
        m = machines.get(agg)
        if not m or m.get("stateless") is True or cc.MISSING in str(cmd):
            continue
        objs = set((aggs.get(agg) or {}).get("objs") or [])
        if not objs:
            continue
        bc = cc.base_cmd(cmd)
        for t in m.get("transitions") or []:
            if t.get("command") and cc.base_cmd(t["command"]) == bc and t.get("to") and (agg, bc, t["to"]) not in seen:
                seen.add((agg, bc, t["to"]))
                out.append((str(t["to"]), objs, f"{bc}→{t['to']}"))
    return out


def texts_of(unit, steps):
    """流程的结局文本：[(kind, text)]。"""
    out = []
    seen = set()

    def add(k, t):
        t = str(t or "").strip()
        if t and (k, t) not in seen:
            seen.add((k, t))
            out.append((k, t))

    for o in unit.get("outcomes") or []:
        add("结局", o if not isinstance(o, dict) else o.get("name") or o.get("outcome"))
    for s in steps:
        add("步骤结局", s.get("outcome"))
        for d in s.get("decision") or []:
            if isinstance(d, dict):
                add("分支结局", d.get("outcome"))
        for e in s.get("emits") or []:
            add("事件", e)
    for st in unit.get("states") or []:
        add("状态", st.get("name") if isinstance(st, dict) else st)
    for tr in unit.get("transitions") or []:
        if isinstance(tr, dict):
            add("状态", tr.get("to"))
    return out


# ---------------------------------------------------------------- 分析

def analyze(vs_doc, catalog, skeletons, onames, alias_map, machines=None, aggs=None):
    norm = Norm(alias_map)
    machines, aggs = machines or {}, aggs or {}
    patterns = {}
    vss = vs_doc["value_streams"]
    stage_pos = {}
    stage_vs = {}
    for vs in vss:
        for i, s in enumerate(vs["stages"]):
            stage_pos[s["id"]] = i
            stage_vs[s["id"]] = vs["id"]
    procs = [p for p in catalog if p.get("type") != "merged"]
    pby = {p["id"]: p for p in procs}
    R = {"vs": {}, "unstaged": [], "support": [], "gaps": [], "gaps_noted": [], "unknown_stage": [], "exit": [], "entry": [], "sparse": [],
         "skel_diff": [], "no_skeleton_stage": 0}

    cand, arr = {}, {}
    for p in procs:
        u = skeletons.get(p["id"])
        t = texts_of(*u) if u else []
        cand[p["id"]] = t
        arr[p["id"]] = arrivals_of(u[0], u[1], machines, aggs) if u else []
        if u:
            sk = set(u[0].get("stages") or [])
            if sk and sk != set(p.get("stages") or []):
                R["skel_diff"].append((p["id"], p["name"], sorted(sk), list(p.get("stages") or [])))

    def find_in(proc_ids, leaf, use_name=True, with_arr=True):
        best, ev = None, []
        on = onames.get(leaf.get("ref"), "")
        for pid in proc_ids:
            for to, objs, desc in (arr[pid] if with_arr else []):
                if leaf.get("ref") not in objs:
                    continue
                tier = match_atom(on, leaf.get("state"), to, norm)
                if tier and RANK[tier] > RANK[best]:
                    best, ev = tier, [(pid, "步骤到达", desc)]
                elif tier and tier == best and len(ev) < 3:
                    ev.append((pid, "步骤到达", desc))
            for kind, text in cand[pid]:
                tier = match_atom(on, leaf.get("state"), text, norm)
                if tier and RANK[tier] > RANK[best]:
                    best, ev = tier, [(pid, kind, text)]
                elif tier and tier == best and len(ev) < 3:
                    ev.append((pid, kind, text))
            if use_name:
                tier = match_atom(on, leaf.get("state"), pby[pid]["name"], norm)
                if tier and not best:
                    best, ev = "approx", [(pid, "名称", pby[pid]["name"])]
        return best, ev

    # 1 未落阶段 / 支撑
    for p in procs:
        st = p.get("stages") or []
        if p.get("support"):
            reason = str(p["support"]).strip()
            flags = []
            if len(reason) < 6:
                flags.append("理由过短")
            if st:
                flags.append("同时标了阶段")
            if str(p.get("owner_bc", "")) not in reason and p.get("owner_bc"):
                flags.append(f"owner_bc {p.get('owner_bc')} 与理由所指不符")
            R["support"].append((p, reason, flags))
        elif not st:
            R["unstaged"].append(p)
        for s in st:
            if s not in stage_pos:
                R["unknown_stage"].append((p["id"], p["name"], s))

    R["guess"] = {}
    for p in R["unstaged"]:
        nb = bigrams(norm(p["name"]))
        best = (0.0, None)
        for vs in vss:
            for st in vs["stages"]:
                sb = bigrams(norm(st["name"] + str(st.get("description") or "")))
                sc = len(nb & sb) / len(nb)
                if sc > best[0]:
                    best = (sc, st)
        if best[1] and best[0] >= 0.3:
            R["guess"][p["id"]] = f"{best[1]['id']} {best[1]['name']}（{best[0]:.0%}）"

    # 2 跳段
    for p in procs:
        by = {}
        for s in p.get("stages") or []:
            if s in stage_pos:
                by.setdefault(stage_vs[s], []).append(stage_pos[s])
        for v, ix in by.items():
            ix = sorted(set(ix))
            missing = [i for i in range(ix[0], ix[-1] + 1) if i not in ix]
            if missing:
                stages = next(x for x in vss if x["id"] == v)["stages"]
                row = (p["id"], p["name"], v, [stages[i]["id"] for i in ix], [stages[i]["id"] for i in missing])
                note = str(p.get("stages_note") or "").strip()
                if note:
                    R["gaps_noted"].append(row + (note,))
                else:
                    R["gaps"].append(row)

    # 3 / 4 / 5
    for vs in vss:
        pa = vs.get("stage_pattern") or {}
        in_vs = [p for p in procs if any(stage_vs.get(s) == vs["id"] for s in p.get("stages") or [])]
        R["vs"][vs["id"]] = {"name": vs["name"], "n": len(in_vs), "stages": {}}
        for i, s in enumerate(vs["stages"]):
            ps = [p["id"] for p in procs if s["id"] in (p.get("stages") or [])]
            R["vs"][vs["id"]]["stages"][s["id"]] = {"name": s["name"], "n": len(ps)}
            if not ps:
                R["sparse"].append(("empty_stage", vs["id"], s["id"], s["name"]))
            # 退出
            ex = expand(s.get("exit") or {"all": []}, pa)
            tier, bad = evaluate(ex, lambda lf: find_in(ps, lf))
            outs = list(dict.fromkeys([(pid, "", t) for pid in ps for k, t in cand[pid] if k != "名称"] +
                                      [(pid, "", "→" + d) for pid in ps for _t, _o, d in arr[pid]]))
            ev_all = _evidence(ex, lambda lf: find_in(ps, lf))
            ev_out = {id(lf): tr for lf, tr, _ in _evidence(ex, lambda lf: find_in(ps, lf, with_arr=False))}
            by_arr = [(lf, tr, e) for lf, tr, e in ev_all if RANK[tr] > RANK[ev_out.get(id(lf))] and e[1] == "步骤到达"]
            if ex.get("all") == [] and "leaf" not in ex and "any" not in ex:
                tier = "exact"
            R["exit"].append({"vs": vs["id"], "stage": s["id"], "name": s["name"], "tier": tier, "bad": bad,
                              "text": render_cond(ex, onames), "n_proc": len(ps), "outs": outs,
                              "ev": ev_all, "by_arr": by_arr})
            # 进入
            en = expand(s.get("entry") or {"all": []}, pa)
            allp = [p["id"] for p in procs]

            def fe(lf, _s=s):
                tier, ev = find_in(allp, lf)
                return tier, ev
            etier, ebad = evaluate(en, fe)
            etier0, _ = evaluate(en, lambda lf: find_in(allp, lf, with_arr=False))
            e_by_arr = []
            rel = []
            for lf in leaves(en):
                if str(lf.get("ref")) in SKIP_REFS or not str(lf.get("ref", "")).startswith("OBJ-"):
                    continue
                t2, ev = find_in(allp, lf)
                t0, _ = find_in(allp, lf, with_arr=False)
                if t2 and RANK[t2] > RANK[t0]:
                    e_by_arr.append((lf, t2, ev[0]))
                for pid, k, t in ev:
                    ps_st = pby[pid].get("stages") or []
                    if any(x == s["id"] for x in ps_st):
                        r = "本段"
                    elif any(stage_vs.get(x) == vs["id"] and stage_pos[x] < i for x in ps_st):
                        r = "同价值流前段"
                    elif any(stage_vs.get(x) == vs["id"] for x in ps_st):
                        r = "同价值流后段"
                    elif ps_st:
                        r = "别的价值流"
                    else:
                        r = "未落阶段"
                    rel.append((lf, pid, r, t2, k))
            only_late = [lf for lf in leaves(en) if lf in [x[0] for x in rel] and
                         all(x[2] in ("同价值流后段",) for x in rel if x[0] is lf)]
            R["entry"].append({"vs": vs["id"], "stage": s["id"], "name": s["name"], "tier": etier, "bad": ebad,
                               "text": render_cond(en, onames), "rel": rel, "only_late": only_late,
                               "tier_out": etier0, "by_arr": e_by_arr})
        if len(in_vs) <= SMALL_VS:
            R["sparse"].append(("small_vs", vs["id"], vs["name"], len(in_vs)))
    return R


def _evidence(t, find):
    out = []
    for lf in leaves(t):
        if str(lf.get("ref")) in SKIP_REFS or not str(lf.get("ref", "")).startswith("OBJ-"):
            continue
        tier, ev = find(lf)
        if tier:
            out.append((lf, tier, ev[0]))
    return out


# ---------------------------------------------------------------- 报告

def leaf_txt(lf, onames):
    return f"{lf.get('ref')}{onames.get(lf.get('ref'), '')}「{lf.get('state')}」"


def render(R, onames, pby, skel_n, total_skel):
    L = []
    w = L.append
    ex_bad = [e for e in R["exit"] if e["tier"] is None]
    ex_apx = [e for e in R["exit"] if e["tier"] == "approx"]
    en_bad = [e for e in R["entry"] if e["tier"] is None]
    en_apx = [e for e in R["entry"] if e["tier"] == "approx"]
    empty = [x for x in R["sparse"] if x[0] == "empty_stage"]
    small = [x for x in R["sparse"] if x[0] == "small_vs"]
    unst = {t: [p for p in R["unstaged"] if p["type"] == t] for t in TYPES}
    w("# 阶段对齐报告（stage_align_check.py 生成，只读检查，不要手改）\n")
    w(f"骨架覆盖：{skel_n} 条流程有骨架（目录非合并流程共 {total_skel} 条）；无骨架的流程只能用名称做近似匹配，退出 / 进入条件的未匹配条数因此偏多。\n")
    w("## 摘要\n")
    w("| 项 | 条数 |\n|---|---|")
    w(f"| 1 未落阶段（business / event_policy / single_aggregate / read_model） | "
      f"{len(unst['business'])} / {len(unst['event_policy'])} / {len(unst['single_aggregate'])} / {len(unst['read_model'])} |")
    w(f"| 1 已声明支撑（理由有疑问） | {len(R['support'])}（{sum(1 for x in R['support'] if x[2])}） |")
    w(f"| 1 阶段引用了价值流里没有的阶段 | {len(R['unknown_stage'])} |")
    w(f"| 2 跳段（另：已说明跨段） | {len(R['gaps'])}（{len(R['gaps_noted'])}） |")
    w(f"| 3 退出条件匹配不上 / 仅近似 / 共 | {len(ex_bad)} / {len(ex_apx)} / {len(R['exit'])} |")
    w(f"| 4 进入条件找不到产生方 / 仅近似 / 共 | {len(en_bad)} / {len(en_apx)} / {len(R['entry'])} |")
    w(f"| 5 没有流程的阶段 / 流程数 ≤ {SMALL_VS} 的价值流 | {len(empty)} / {len(small)} |")
    w(f"| 附 骨架阶段与目录阶段不一致 | {len(R['skel_diff'])} |")
    w("")
    w("## 价值流概览（流程数 = 去重后落在该价值流的非合并流程）\n")
    w("| 价值流 | 流程数 | 各段流程数 | 退出匹配不上 | 进入找不到 |\n|---|---|---|---|---|")
    for vid, v in R["vs"].items():
        segs = " ".join(f"{sid.split('.')[-1]}:{s['n']}" for sid, s in v["stages"].items())
        eb = sum(1 for e in ex_bad if e["vs"] == vid)
        nb = sum(1 for e in en_bad if e["vs"] == vid)
        w(f"| {vid} {v['name']} | {v['n']}{' ⚠' if v['n'] <= SMALL_VS else ''} | {segs} | {eb} | {nb} |")
    w("")

    w("## 1 未落阶段的流程\n")
    w("未落阶段的流程没有价值流归属；「疑似阶段」是流程名与阶段名 / 描述的字面相似度最高者，仅供参考（近似）。\n")
    for t in TYPES:
        w(f"### {t}（{len(unst[t])}）\n")
        if unst[t]:
            w("| 流程 | 名称 | 组 | owner_bc | 疑似阶段（近似） |\n|---|---|---|---|---|")
            for p in unst[t]:
                w(f"| {p['id']} | {p['name']} | {p.get('group')} | {p.get('owner_bc')} | {R['guess'].get(p['id'], '—')} |")
            w("")
    w(f"### 已声明支撑（{len(R['support'])}）\n")
    if R["support"]:
        w("| 流程 | 名称 | type | owner_bc | 理由 | 提示 |\n|---|---|---|---|---|---|")
        for p, reason, fl in R["support"]:
            w(f"| {p['id']} | {p['name']} | {p['type']} | {p.get('owner_bc')} | {reason} | {'；'.join(fl) or '—'} |")
        w("")
    if R["unknown_stage"]:
        w("### 引用了不存在的阶段\n")
        for pid, n, s in R["unknown_stage"]:
            w(f"- {pid} {n}：{s}")
        w("")

    w("## 2 跳段\n")
    if R["gaps"]:
        w("| 流程 | 名称 | 价值流 | 所标阶段 | 缺的中间阶段 |\n|---|---|---|---|---|")
        for pid, n, v, have, miss in R["gaps"]:
            w(f"| {pid} | {n} | {v} | {'、'.join(have)} | {'、'.join(miss)} |")
    else:
        w("无。")
    w("")

    if R["gaps_noted"]:
        w(f"### 已说明跨段（{len(R['gaps_noted'])}，不计问题）\n")
        for pid, n, v, have, miss, note in R["gaps_noted"]:
            w(f"- {pid} {n}（{v}：{'、'.join(have)}，跨过 {'、'.join(miss)}）：{note}")
        w("")

    w("## 3 退出条件能否由本段流程结局满足\n")
    for vid in R["vs"]:
        rows = [e for e in R["exit"] if e["vs"] == vid and e["tier"] in (None, "approx")]
        if not rows:
            continue
        w(f"### {vid} {R['vs'][vid]['name']}\n")
        for e in rows:
            tag = "匹配不上" if e["tier"] is None else "仅近似"
            w(f"- **{e['stage']} {e['name']}**（{tag}；本段流程 {e['n_proc']} 条）")
            w(f"  - 退出条件：{e['text']}")
            if e["tier"] is None:
                w(f"  - 未匹配：{'；'.join(leaf_txt(x, onames) for x in e['bad'])}")
            else:
                for lf, tier, (pid, k, t) in e["ev"]:
                    if tier == "approx":
                        w(f"  - 近似：{leaf_txt(lf, onames)} ≈ {pid}（{k}）「{t}」")
            if e["tier"] is None:
                outs = [f"{pid}:{t}" for pid, k, t in e["outs"]]
                shown = outs[:25]
                w(f"  - 本段流程结局（{len(outs)}）：{'、'.join(shown) if shown else '无（本段无流程或无骨架）'}{' …' if len(outs) > 25 else ''}")
    w("")

    w("### 3a 匹配来源：仅由「步骤到达」匹配上的退出条件\n")
    w("这些条件的结局类文本（outcomes / 步骤结局 / 分支结局 / 事件 / 状态）匹配不上，是流程步骤经状态机到达的状态使其匹配（括号内为 命令→到达状态）。\n")
    ea = [(e, x) for e in R["exit"] for x in e["by_arr"]]
    for e, (lf, tier, (pid, k, t)) in ea:
        w(f"- {e['stage']} {e['name']}：{leaf_txt(lf, onames)} ← {pid}（步骤到达，{tier}）{t}")
    if not ea:
        w("无。")
    w("")

    w("## 4 进入条件的产生方\n")
    for vid in R["vs"]:
        rows = [e for e in R["entry"] if e["vs"] == vid and (e["tier"] is None or e["only_late"])]
        if not rows:
            continue
        w(f"### {vid} {R['vs'][vid]['name']}\n")
        for e in rows:
            w(f"- **{e['stage']} {e['name']}**：{e['text']}")
            if e["tier"] is None:
                w(f"  - 找不到产生方：{'；'.join(leaf_txt(x, onames) for x in e['bad'])}")
            for lf in e["only_late"]:
                w(f"  - 只有后段流程产生：{leaf_txt(lf, onames)}")
    w("")

    w("### 4a 匹配来源：产生方仅由「步骤到达」找到的进入条件叶子\n")
    na = [(e, x) for e in R["entry"] for x in e["by_arr"]]
    for e, (lf, tier, (pid, k, t)) in na:
        w(f"- {e['stage']} {e['name']}：{leaf_txt(lf, onames)} ← {pid}（步骤到达，{tier}）{t}")
    if not na:
        w("无。")
    w("")

    w("## 5 稀少\n")
    if small:
        w("价值流流程数 ≤ %d：" % SMALL_VS)
        for _, vid, n, c in small:
            w(f"- {vid} {n}：{c} 条")
        w("")
    if empty:
        w("没有任何流程的阶段：")
        for _, vid, sid, n in empty:
            w(f"- {sid} {n}")
        w("")

    if R["skel_diff"]:
        w("## 附 骨架阶段与目录阶段不一致（以目录为准）\n")
        for pid, n, a, b in R["skel_diff"]:
            w(f"- {pid} {n}：骨架 {a}，目录 {b}")
        w("")

    w("## 匹配口径\n")
    w("""- 流程范围：目录里 type 不为 merged 的条目；阶段归属以目录 `stages` 为准，骨架的 `stages` 不一致者列在附录。
- 结局文本：骨架的 `outcomes`、步骤的 `outcome`、`decision[].outcome`、`emits`、单聚合的 `states` / `transitions[].to`；没有骨架的流程只用目录里的名称，且名称命中一律标近似。
- 条件原子：`{ref: OBJ-nn, state: …}`；`ref` 为「派生」「缺对象状态」的不作门，视为满足；`pattern` 展开（与同处 all / any 取且）；all / any 按原结构求值。
- 规范化：词表 `terms.yaml` 里的别名（长度 ≥ 3 的中文别名）先换成正式用语；再去掉空白、标点、「已」「被」（去掉后不足两字的保留原文整体比对）；对象名取信息图 ENTERPRISE_INFO_MAP_DRAFT.md 的名称（去括号）。
- 状态说法：状态原文按 `/`、`→` 拆开，括号内外分开，整体与各部分各取一个说法（≥ 2 个字）。
- 精确：说法与结局相等；或说法包含于结局且对象名也在结局里；或结局 = 对象名 + 说法。
- 近似：说法包含于结局（缺对象名）；结局（≥ 3 字）包含于说法；说法的二字组被结局覆盖 ≥ 60%%；流程名称命中。拿不准的都落在近似。
- 匹配来源：「结局」类 = 上一条的结局文本；「步骤到达」= 流程的每个改状态步骤（aggregate + command，含 inner 与单聚合 commands[] / transitions[] 条目，条目 aggregate 优先），在三组 state-machines.yaml 里查该聚合 command 相同的转换，取其 to 状态；仅当条件的对象（OBJ）属于该聚合的对象时参与，状态说法同样按下述精确 / 近似规则比对。步骤到达同时参与退出条件（本段流程）与进入条件产生方（全部流程）；3a / 4a 列出只靠它匹配上的叶子。
- 退出条件「满足」= 存在落在本段的某条流程的某个结局匹配；整体 tier 取 any 的最好者、all 的最差者。
- 进入条件的产生方在全部非合并流程里找（含别的价值流），并标注与本段的关系（同价值流前段 / 本段 / 后段 / 别的价值流 / 未落阶段）。
- 状态名与结局用词不同（如「赢单」与「商机已转赢单」）、结局只写在 notes 里、事件用英文名，都会被判为匹配不上；它们提示的是用语对齐问题，不一定是流程缺失。
- 稀少：价值流流程数 ≤ %d 或某段 0 条流程。""" % SMALL_VS)
    return "\n".join(L) + "\n"


def top_findings(R, onames, n=10):
    items = []
    for x in R["sparse"]:
        if x[0] == "empty_stage":
            items.append((0, f"空阶段：{x[2]} {x[3]} 没有任何流程"))
    for x in R["sparse"]:
        if x[0] == "small_vs":
            items.append((1, f"流程稀少：{x[1]} {x[2]} 只有 {x[3]} 条流程"))
    for pid, nm, v, have, miss in R["gaps"]:
        items.append((2, f"跳段：{pid} {nm} 在 {v} 标了 {'、'.join(have)}，缺 {'、'.join(miss)}"))
    for e in R["exit"]:
        if e["tier"] is None and e["n_proc"] > 0:
            items.append((3, f"退出匹配不上：{e['stage']} {e['name']}（本段 {e['n_proc']} 条流程）{'；'.join(leaf_txt(x, onames) for x in e['bad'][:2])}"))
    for e in R["entry"]:
        if e["tier"] is None:
            items.append((4, f"进入找不到产生方：{e['stage']} {e['name']}：{'；'.join(leaf_txt(x, onames) for x in e['bad'][:2])}"))
    for p in R["unstaged"]:
        if p["type"] == "business":
            items.append((5, f"未落阶段的 business 流程：{p['id']} {p['name']}"))
    items.sort(key=lambda x: x[0])
    out, per = [], {}
    for pri, txt in items:
        if per.get(pri, 0) >= 3:
            continue
        per[pri] = per.get(pri, 0) + 1
        out.append(txt)
        if len(out) >= n:
            break
    return out


def console(R, onames, skel_n, total_skel):
    ex_bad = [e for e in R["exit"] if e["tier"] is None]
    en_bad = [e for e in R["entry"] if e["tier"] is None]
    unst = {t: sum(1 for p in R["unstaged"] if p["type"] == t) for t in TYPES}
    L = [f"骨架覆盖 {skel_n}/{total_skel}",
         f"1 未落阶段 business {unst['business']} / event_policy {unst['event_policy']} / single_aggregate {unst['single_aggregate']} / read_model {unst['read_model']}；已声明支撑 {len(R['support'])}",
         f"2 跳段 {len(R['gaps'])}（已说明跨段 {len(R['gaps_noted'])}）",
         f"3 退出匹配不上 {len(ex_bad)} / 近似 {sum(1 for e in R['exit'] if e['tier'] == 'approx')} / 共 {len(R['exit'])}",
         f"4 进入找不到产生方 {len(en_bad)} / 近似 {sum(1 for e in R['entry'] if e['tier'] == 'approx')} / 共 {len(R['entry'])}",
         f"5 空阶段 {sum(1 for x in R['sparse'] if x[0] == 'empty_stage')}；流程数 ≤ {SMALL_VS} 的价值流 {sum(1 for x in R['sparse'] if x[0] == 'small_vs')}",
         "最值得注意的 10 条："]
    L += [f"  {i}. {t}" for i, t in enumerate(top_findings(R, onames), 1)]
    return "\n".join(L)


# ---------------------------------------------------------------- 自检

def self_test():
    vs = {"value_streams": [{
        "id": "VS-X", "name": "测试流", "stage_pattern": {"ec": {"all": [{"ref": "OBJ-2", "state": "进行中"}]}},
        "stages": [
            {"id": "VS-X.1", "name": "一段", "entry": {"all": [{"ref": "OBJ-9", "state": "不存在状态"}]},
             "exit": {"any": [{"ref": "OBJ-1", "state": "赢单"}, {"ref": "OBJ-1", "state": "丢单 / 放弃"}]}},
            {"id": "VS-X.2", "name": "二段", "entry": {"pattern": "ec"},
             "exit": {"all": [{"ref": "OBJ-1", "state": "已签署生效"}, {"ref": "派生", "state": "x"}]}},
            {"id": "VS-X.3", "name": "三段", "entry": {"all": [{"ref": "OBJ-1", "state": "已签署生效"}]},
             "exit": {"all": [{"ref": "OBJ-2", "state": "彻底完全不相干的状态"}]}},
            {"id": "VS-X.4", "name": "空段", "exit": {"all": [{"ref": "OBJ-1", "state": "赢单"}]}},
        ]}]}
    cat = [
        {"id": "P1", "name": "做甲事", "type": "business", "stages": ["VS-X.1"], "owner_bc": "B-1"},
        {"id": "P2", "name": "做乙事", "type": "business", "stages": ["VS-X.1", "VS-X.3"], "owner_bc": "B-1"},
        {"id": "P3", "name": "未落", "type": "event_policy", "stages": [], "owner_bc": "B-1"},
        {"id": "P4", "name": "支撑", "type": "business", "stages": [], "support": "行政支撑：按 B-31", "owner_bc": "B-31"},
        {"id": "P5", "name": "支撑短", "type": "business", "stages": [], "support": "x", "owner_bc": "B-31"},
        {"id": "P6", "name": "合并", "type": "merged", "stages": [], "merged_into": "P1"},
        {"id": "P8", "name": "说明跨段", "type": "business", "stages": ["VS-X.1", "VS-X.3"], "stages_note": "理由", "owner_bc": "B-1"},
        {"id": "P9", "name": "空串跨段", "type": "business", "stages": ["VS-X.1", "VS-X.3"], "stages_note": "", "owner_bc": "B-1"},
        {"id": "P7", "name": "签署合同", "type": "single_aggregate", "stages": ["VS-X.2"], "owner_bc": "B-1"},
    ]
    sk = {"P1": ({"outcomes": ["商机已赢单", "商机已丢单"], "stages": ["VS-X.1"]}, []),
          "P2": ({"outcomes": ["别的结局"], "stages": ["VS-X.1", "VS-X.3"]}, [{"emits": ["协议已签署生效"]}])}
    on = {"OBJ-1": "商机", "OBJ-2": "项目", "OBJ-9": "杂项"}
    R = analyze(vs, cat, sk, on, {})
    assert [p["id"] for p in R["unstaged"]] == ["P3"], R["unstaged"]
    assert [x[0]["id"] for x in R["support"]] == ["P4", "P5"]
    assert R["support"][1][2] and not R["support"][0][2]
    assert [g[0] for g in R["gaps_noted"]] == ["P8"]
    assert [g[0] for g in R["gaps"]] == ["P2", "P9"] and R["gaps"][0][4] == ["VS-X.2"], R["gaps"]
    ex = {e["stage"]: e for e in R["exit"]}
    assert ex["VS-X.1"]["tier"] == "exact", ex["VS-X.1"]
    assert ex["VS-X.2"]["tier"] is None and ex["VS-X.2"]["bad"][0]["ref"] == "OBJ-1", ex["VS-X.2"]  # 无骨架且名称对不上；「派生」不作门
    assert ex["VS-X.3"]["tier"] is None and ex["VS-X.3"]["bad"], ex["VS-X.3"]
    assert ex["VS-X.4"]["tier"] is None
    en = {e["stage"]: e for e in R["entry"]}
    assert en["VS-X.1"]["tier"] is None
    assert en["VS-X.2"]["tier"] is None  # pattern 展开：项目「进行中」无人产生
    assert en["VS-X.3"]["tier"] in ("exact", "approx")
    assert any(r[2] == "同价值流前段" for r in en["VS-X.3"]["rel"]) or any(r[2] == "本段" for r in en["VS-X.3"]["rel"]), en["VS-X.3"]["rel"]
    assert ("empty_stage", "VS-X", "VS-X.4", "空段") in R["sparse"]
    assert not any(x[0] == "small_vs" for x in R["sparse"])  # 5 条流程
    R2 = analyze(vs, cat[:1], sk, on, {})
    assert any(x[0] == "small_vs" for x in R2["sparse"])
    # 步骤到达：P2 的步骤命令「丢弃乙」经状态机到达「已丢单」，应满足 VS-X.4 退出 OBJ-1「赢单」之外的叶子；这里用 VS-X.3 的 OBJ-2 状态验证
    vs3 = {"value_streams": [{"id": "VS-Y", "name": "y", "stages": [
        {"id": "VS-Y.1", "name": "甲段", "entry": {"all": [{"ref": "OBJ-1", "state": "已冻结"}]}, "exit": {"all": [{"ref": "OBJ-1", "state": "已关闭"}]}},
        {"id": "VS-Y.2", "name": "乙段", "entry": {"all": [{"ref": "OBJ-1", "state": "已关闭"}]}, "exit": {"all": [{"ref": "OBJ-1", "state": "已冻结"}]}}]}]}
    cat3 = [{"id": "Q1", "name": "操作", "type": "business", "stages": ["VS-Y.1"], "owner_bc": "B-1"},
            {"id": "Q2", "name": "别的", "type": "business", "stages": ["VS-Y.2"], "owner_bc": "B-1"}]
    sk3 = {"Q1": ({"outcomes": ["无关"], "stages": ["VS-Y.1"]}, [{"actor": "SH-E1", "where": "in_system", "aggregate": "AGG-1", "command": "关闭甲"}]),
           "Q2": ({"outcomes": ["无关"], "stages": ["VS-Y.2"]}, [{"actor": "SH-E1", "where": "in_system", "aggregate": "AGG-1", "command": "冻结甲"},
                                                              {"actor": "SH-E1", "where": "in_system", "aggregate": "AGG-9", "command": "关闭甲"}])}
    mach = {"AGG-1": {"aggregate": "AGG-1", "states": ["x", "已关闭", "已冻结"], "transitions": [
        {"from": "x", "to": "已关闭", "command": "关闭甲"}, {"from": "x", "to": "已冻结", "command": "冻结甲"}]},
            "AGG-9": {"aggregate": "AGG-9", "stateless": True}}
    aggs3 = {"AGG-1": {"objs": ["OBJ-1"]}, "AGG-9": {"objs": ["OBJ-1"]}}
    R3 = analyze(vs3, cat3, sk3, on, {}, mach, aggs3)
    x3 = {e["stage"]: e for e in R3["exit"]}
    assert x3["VS-Y.1"]["tier"] == "exact" and x3["VS-Y.1"]["by_arr"], x3["VS-Y.1"]
    assert x3["VS-Y.2"]["tier"] == "exact", x3["VS-Y.2"]
    e3 = {e["stage"]: e for e in R3["entry"]}
    assert e3["VS-Y.1"]["tier"] == "exact" and e3["VS-Y.2"]["tier"] == "exact" and e3["VS-Y.2"]["by_arr"], e3
    R3b = analyze(vs3, cat3, sk3, on, {}, {}, {})   # 无状态机：不参与，退出不匹配
    assert {e["stage"]: e for e in R3b["exit"]}["VS-Y.1"]["tier"] is None
    assert "步骤到达" in render(R3, on, {}, 2, 2)
    n = Norm({"销售合同": "合同"})
    assert n("销售合同已生效") == "合同生效"
    assert match_atom("商机", "已确认（转商机）", "已转商机", n) == "exact"
    assert match_atom("商机", "赢单", "商机已赢单", n) == "exact"
    assert match_atom("商机", "赢单", "完全无关", n) is None
    assert match_atom("费用报销", "已付", "费用报销已付", n) == "exact"
    txt = render(R, on, {}, 2, 6)
    assert "匹配口径" in txt and "VS-X.3" in txt
    print("self-test 通过")


def main():
    if "--self-test" in sys.argv:
        self_test()
        return
    local = "--local" in sys.argv
    vs_doc = yaml.safe_load(VS_FILE.read_text(encoding="utf-8"))
    catalog = yaml.safe_load(CATALOG.read_text(encoding="utf-8"))["processes"]
    skeletons = load_skeletons(local)
    onames = load_objnames()
    import closeout_check as cc
    R = analyze(vs_doc, catalog, skeletons, onames, load_alias_map(), load_machines(local), cc.load_aggregates())
    procs = [p for p in catalog if p.get("type") != "merged"]
    skel_n = sum(1 for p in procs if p["id"] in skeletons)
    text = render(R, onames, {p["id"]: p for p in procs}, skel_n, len(procs))
    if "--no-write" not in sys.argv:
        REPORT.write_text(text, encoding="utf-8")
    print(console(R, onames, skel_n, len(procs)))


if __name__ == "__main__":
    main()
