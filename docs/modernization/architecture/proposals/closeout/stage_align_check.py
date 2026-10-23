"""阶段对齐检查（只读）：流程目录与三组骨架，对照价值流阶段的进出条件。

用法: python3 -I stage_align_check.py [--local] [--extra PATH]... [--no-write] [--self-test]
  默认          骨架读 origin/claude/one-arch-<组>-closeout（沿用 closeout_check.read_group_file）
  --local       骨架读 closeout/<组>/ 本地文件
  --extra PATH  按 ID 覆盖或追加流程，后给的文件优先；归段说明骨架优先、目录兜底
  --no-write    只打印，不写 STAGE_ALIGN_REPORT.md
  --self-test   最小样例覆盖六项检查

六项：1 未落阶段的流程；2 跳段；3 阶段退出条件能否由本段流程的结局满足；4 阶段进入条件的产生方；5 稀少；
6 归段（按用户判据推「应归段」，与目录所标阶段比较；判据原文见报告末尾）。
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
CATALOG = Path(__file__).resolve().parents[1].parent / "ssot" / "09-processes" / "catalog.yaml"   # 流程目录正本
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


def split_outside(s):
    """按 / 和 → 拆分，括号内的不拆（「减员（转移 / 封存）」是一个状态）。"""
    out, depth, cur = [], 0, ""
    for ch in str(s):
        if ch in "（(":
            depth += 1
        elif ch in "）)":
            depth = max(0, depth - 1)
        if ch in "/→" and depth == 0:
            out.append(cur)
            cur = ""
        else:
            cur += ch
    return out + [cur]


def variants(state, norm):
    """一个状态原文拆成可比对的说法：整体、按括号外的 / → 拆分、括号内外分开。"""
    s = str(state)
    parts = {s}
    for seg in split_outside(s):
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


def load_skeletons(local, extra=None):
    import closeout_check as cc
    units = {}
    for doc in cc.load_process_docs(local, extra).values():
        for _, u in cc.units_of(doc):
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


def load_event_map(local, machines, aggs):
    """事件名 → [(聚合, 到达状态, 对象集合)]：各组 commands.yaml 里命令的 emits 找到发出事件的命令，再在状态机里取该命令转换的 to
    （自环与 non_state 不算）。emits 去掉〔缺〕与括号注释。"""
    import closeout_check as cc
    out = {}
    for g in GROUPS:
        if local:
            pth = HERE / g / "commands.yaml"
            txt = pth.read_text(encoding="utf-8") if pth.exists() else None
        else:
            txt = cc.git_show(f"claude/one-arch-{g}-closeout", f"docs/modernization/architecture/proposals/closeout/{g}/commands.yaml")
        if not txt:
            print(f"警告：读不到 {g} 组命令（{'本地' if local else 'origin 分支'}）", file=sys.stderr)
            continue
        for c in (yaml.safe_load(txt) or {}).get("commands") or []:
            em = c.get("emits")
            em = [em] if isinstance(em, str) else (em or [])
            m = machines.get(c.get("aggregate"))
            objs = set((aggs.get(c.get("aggregate")) or {}).get("objs") or [])
            if not m or not objs or m.get("stateless") is True:
                continue
            bc = cc.base_cmd(c.get("name"))
            tos = [str(t["to"]) for t in m.get("transitions") or []
                   if t.get("to") is not None and not is_self_loop(t) and t.get("command") and cc.base_cmd(t["command"]) == bc]
            for e in em:
                e = re.sub(r"[（(].*?[）)]", "", str(e).replace(cc.MISSING, "")).strip()
                if not e or e == "":
                    continue
                for to in tos:
                    v = (c["aggregate"], to, frozenset(objs))
                    if v not in out.setdefault(e, []):
                        out[e].append(v)
    return out


def is_self_loop(tr):
    """不改业务状态的转换：from == to 的自环，或 kind: non_state（追加记录类，如登记继续教育学时）。"""
    if tr.get("kind") == "non_state":
        return True
    f = tr.get("from")
    f = f if isinstance(f, list) else [f]
    return tr.get("to") is not None and len(f) == 1 and f[0] == tr.get("to")


def split_objects(aggs):
    """一拆多对象：{OBJ: {聚合}}。同一信息图对象被 ≥ 2 个聚合承载，且至少一个的「对应」列写了一拆多。"""
    by = {}
    for a, v in aggs.items():
        for o in v.get("objs") or []:
            by.setdefault(o, {})[a] = v
    return {o: set(m) for o, m in by.items() if len(m) >= 2 and any("一拆多" in str(v.get("corr", "")) for v in m.values())}


def leaf_aggs(leaf):
    a = leaf.get("agg")
    return None if not a else ({a} if isinstance(a, str) else set(a))


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
            if is_self_loop(t):
                continue   # 自环（from == to，如提交过账候选）不改业务状态，不算到达
            if t.get("command") and cc.base_cmd(t["command"]) == bc and t.get("to") and (agg, bc, t["to"]) not in seen:
                seen.add((agg, bc, t["to"]))
                out.append((str(t["to"]), objs, f"{bc}→{t['to']}", agg))
    return out


def texts_of(unit, steps, aggs=None, onames=None):
    """流程的结局文本：[(kind, text)]。单聚合维护单元的 states / transitions 不带对象名：按条目 aggregate（缺则单元 aggregate；
    条目聚合是技术聚合、没有对象时同样退回单元聚合）对应的信息图对象名补一条「对象名+状态」文本。"""
    aggs, onames = aggs or {}, onames or {}

    def objnames(*agg_ids):
        for a in agg_ids:
            objs = (aggs.get(a) or {}).get("objs") or []
            if objs:
                return [onames[o] for o in objs if onames.get(o)]
        return []
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
    sts = unit.get("states") or []
    if isinstance(sts, dict):   # 按子对象分组的 {组: [状态]}
        sts = [x for v in sts.values() for x in (v or [])]
    for st in sts:
        name = st.get("name") if isinstance(st, dict) else st
        add("状态", name)
        for on in objnames(unit.get("aggregate")):
            add("状态", f"{on}{name}")
    for tr in unit.get("transitions") or []:
        if isinstance(tr, dict) and not is_self_loop(tr):
            add("状态", tr.get("to"))
            for on in objnames(tr.get("aggregate"), unit.get("aggregate")):
                add("状态", f"{on}{tr.get('to')}")
    return out


# ---------------------------------------------------------------- 分析

def declared_supports(p, unit, stage_pos):
    """supports 声明的「支撑本段」：骨架优先、目录兜底；阶段须存在，且不得同时在 stages 里。"""
    raw = unit[0]["supports"] if unit and "supports" in unit[0] else p.get("supports")
    sup = raw if isinstance(raw, list) else ([] if raw is None else [raw])
    bad = [x for x in sup if x not in stage_pos]
    if bad:
        raise ValueError(f"{p['id']} supports 指向不存在的阶段：{'、'.join(map(str, bad))}")
    both = [x for x in sup if x in (p.get("stages") or []) or (unit and x in (unit[0].get("stages") or []))]
    if both:
        raise ValueError(f"{p['id']} 的阶段同时在 stages 与 supports：{'、'.join(both)}")
    return set(sup)


def analyze(vs_doc, catalog, skeletons, onames, alias_map, machines=None, aggs=None, events=None):
    norm = Norm(alias_map)
    ev_by_norm = {}
    for k, v in (events or {}).items():
        ev_by_norm.setdefault(norm(k), []).extend(v)
    machines, aggs = machines or {}, aggs or {}
    split = split_objects(aggs)
    agg_missing = {}
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
         "skel_diff": [], "no_skeleton_stage": 0, "agg_missing": agg_missing, "split": split}

    cand, arr = {}, {}
    for p in procs:
        u = skeletons.get(p["id"])
        t = texts_of(*u, aggs, onames) if u else []
        cand[p["id"]] = t
        arr[p["id"]] = arrivals_of(u[0], u[1], machines, aggs) if u else []
        if u:
            sk = set(u[0].get("stages") or [])
            if sk and sk != set(p.get("stages") or []):
                R["skel_diff"].append((p["id"], p["name"], sorted(sk), list(p.get("stages") or [])))

    def arr_ok(leaf, stage):
        """一拆多对象的步骤到达只在聚合一致时算：返回允许的聚合集合，None 表示不限，空集表示待人工（不计步骤到达）。"""
        want = leaf_aggs(leaf)
        if want:
            return want
        ref = leaf.get("ref")
        if ref not in split or not stage:
            return None
        on = onames.get(ref, "")
        cands = {a for p in procs if any(stage_vs.get(x) == stage_vs[stage] for x in p.get("stages") or [])
                 for to, objs, _d, a in arr[p["id"]] if ref in objs and a in split[ref] and match_atom(on, leaf.get("state"), to, norm)}
        if len(cands) == 1:
            return cands
        if len(cands) > 1:
            agg_missing.setdefault((stage, ref, str(leaf.get("state"))), set()).update(cands)
        return set() if cands else None

    def find_in(proc_ids, leaf, use_name=True, with_arr=True, stage=None):
        best, ev = None, []
        on = onames.get(leaf.get("ref"), "")
        okagg = arr_ok(leaf, stage) if with_arr else None
        for pid in proc_ids:
            for to, objs, desc, agg in (arr[pid] if with_arr else []):
                if leaf.get("ref") not in objs or (okagg is not None and agg not in okagg):
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
        elif not st and p.get("type") != "integration":   # 集成流程不归段（type: integration，按所供对象支撑）
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
            tier, bad = evaluate(ex, lambda lf: find_in(ps, lf, stage=s["id"]))
            outs = list(dict.fromkeys([(pid, "", t) for pid in ps for k, t in cand[pid] if k != "名称"] +
                                      [(pid, "", "→" + d) for pid in ps for _t, _o, d, _a in arr[pid]]))
            ev_all = _evidence(ex, lambda lf: find_in(ps, lf, stage=s["id"]))
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
                tier, ev = find_in(allp, lf, stage=_s["id"])
                return tier, ev
            etier, ebad = evaluate(en, fe)
            etier0, _ = evaluate(en, lambda lf: find_in(allp, lf, with_arr=False))
            e_by_arr = []
            rel = []
            for lf in leaves(en):
                if str(lf.get("ref")) in SKIP_REFS or not str(lf.get("ref", "")).startswith("OBJ-"):
                    continue
                t2, ev = find_in(allp, lf, stage=s["id"])
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
    # 6 归段
    part = {s["id"]: set(s.get("participants") or []) for vs in vss for s in vs["stages"]}
    sname = {s["id"]: s["name"] for vs in vss for s in vs["stages"]}
    own_exit, own_inner, own_entry = {}, {}, {}
    isobj = lambda lf: str(lf.get("ref", "")).startswith("OBJ-")
    bc_vs = {}
    for q in procs:
        for x in q.get("stages") or []:
            if x in stage_vs and q.get("owner_bc"):
                bc_vs.setdefault(q["owner_bc"], set()).add(stage_vs[x])
    for vs in vss:
        for s in vs["stages"]:
            vpa = vs.get("stage_pattern") or {}   # 展开 exit_common / entry_common
            own_exit[s["id"]] = [lf for lf in leaves(expand(s.get("exit") or {"all": []}, vpa)) if isobj(lf)]
            own_entry[s["id"]] = [lf for lf in leaves(expand(s.get("entry") or {"all": []}, vpa)) if isobj(lf)]
            own_inner[s["id"]] = [lf for lf in s.get("in_stage_states") or [] if isinstance(lf, dict) and isobj(lf)]
    P = {"unsupported": [], "explained": [], "missing": [], "uncertain": [], "role": [], "ok": 0, "ok_approx": 0}
    R["hit_refs"] = {}   # pid -> [(stage, tier, OBJ, 依据)]：归段判据命中的条件对象，供对象层（主对象提议）读
    for p in procs:
        if p.get("support"):
            P["support_skipped"] = P.get("support_skipped", 0) + 1   # 已声明支撑的不归段
            continue
        u = skeletons.get(p["id"])
        cat = [x for x in (p.get("stages") or []) if x in stage_pos]
        sup_decl = declared_supports(p, u, stage_pos)
        exact, approx, why, unres = {}, {}, [], False

        hit_log = R["hit_refs"].setdefault(p["id"], [])

        def put(sid, tier, ev, lf=None, src=""):
            if tier and lf is not None:
                hit_log.append((sid, tier, lf.get("ref"), src))
            d = exact if tier == "exact" else approx
            if tier and sid not in d:
                d[sid] = ev
            if tier == "exact":
                approx.pop(sid, None)
        if not u:
            why.append("无骨架")
            unres = True
        elif p["type"] == "read_model":
            why.append("读模型不造对象状态，按其读取的对象状态人工归段")
            unres = True
        elif p["type"] == "event_policy" or (p["type"] == "business" and u[0].get("triggers") and all(isinstance(t, dict) and t.get("type") == "event" for t in u[0]["triggers"])):
            trig = [t for t in u[0].get("triggers") or [] if isinstance(t, dict) and t.get("type") == "event" and t.get("name")]
            hit_any = False
            own_vs = bc_vs.get(p.get("owner_bc")) or {stage_vs[x] for x in cat}

            def trig_hits(src, name, vs_only=None, mapped=None):
                out = {}
                for sid, lfs in src.items():
                    if vs_only is not None and stage_vs[sid] not in vs_only:
                        continue
                    for lf in lfs:
                        if mapped is not None:   # 事件名 → 发出该事件的转换 → 到达状态与聚合
                            tier = None
                            for agg_, to_, objs_ in mapped:
                                if lf.get("ref") in objs_ and (leaf_aggs(lf) is None or agg_ in leaf_aggs(lf)):
                                    tr_ = match_atom(onames.get(lf.get("ref"), ""), lf.get("state"), to_, norm)
                                    if RANK[tr_] > RANK[tier]:
                                        tier = tr_
                            if tier and RANK[tier] > RANK[(out.get(sid) or (None,))[0]]:
                                out[sid] = (tier, lf)
                            continue
                        tier = match_atom(onames.get(lf.get("ref"), ""), lf.get("state"), name, norm)
                        on_ = norm(re.sub(r"[（(].*?[）)]", "", onames.get(lf.get("ref"), "")))
                        if tier == "exact" and on_ and on_ not in norm(name):
                            tier = "approx"
                        if tier and RANK[tier] > RANK[(out.get(sid) or (None,))[0]]:
                            out[sid] = (tier, lf)
                return out
            for t in trig:
                mp = None
                for cand_ in (t["name"], t.get("event")):   # 触发写事件名，或另给事件标识（如 Separated）
                    if cand_ and not mp:
                        mp = ev_by_norm.get(norm(re.sub(r"[（(].*?[）)]", "", str(cand_).replace("〔缺〕", ""))))
                hits = trig_hits(own_exit, t["name"], mapped=mp) if mp else {}
                ent = trig_hits(own_entry, t["name"], own_vs, mapped=mp) if mp else {}
                if not hits and not ent:   # 映射找不到再按词表别名与近似规则
                    hits = trig_hits(own_exit, t["name"])
                    ent = trig_hits(own_entry, t["name"], own_vs)
                if hits and ent:
                    hits = ent   # 触发状态同时是别段的退出、本价值流某段的进入：归以它为进入的那一段
                hit = bool(hits)
                for sid, (tier, lf) in hits.items():
                    put(sid, tier, [(p["id"], "触发状态", f"{t['name']} ≈ {leaf_txt(lf, onames)}")], lf, "trigger")
                hit_any = hit_any or hit
                if not hit:
                    why.append(f"触发事件「{t['name']}」对不上任何段退出条件里的对象状态")
            if not trig:
                why.append("无事件触发")
            elif not hit_any:
                why.append("全部触发事件都对不上对象状态")
            unres = not hit_any
        else:
            for sid in own_exit:
                for lf in own_exit[sid] + own_inner[sid]:   # 退出条件 + 段内状态；进入条件不作归段依据
                    src = "exit" if any(lf is x for x in own_exit[sid]) else "inner"
                    tier, ev = find_in([p["id"]], lf, use_name=False, stage=sid)
                    if tier == "exact" and ev[0][1] != "步骤到达":
                        on_ = norm(re.sub(r"[（(].*?[）)]", "", onames.get(lf.get("ref"), "")))
                        named = [e for e in ev if e[1] == "步骤到达" or on_ in norm(e[2])]   # 同档证据里任一条带对象名即可
                        if on_ and not named:
                            tier = "approx"   # 结局只写了通用状态词（如「已关闭」）、没写对象名：不算精确
                        elif named:
                            ev = named + [e for e in ev if e not in named]
                    if tier:
                        put(sid, tier, ev, lf, src)
        sup = set(exact) | set(approx)
        row = {"id": p["id"], "name": p["name"], "type": p["type"], "cat": cat, "exact": exact, "approx": approx, "why": why,
               "support": bool(p.get("support"))}
        # 目录标了但判据不支持
        no_sup = [x for x in cat if x not in sup]
        if no_sup and not unres:
            note = str((u[0].get("stages_note") if u and "stages_note" in u[0] else p.get("stages_note")) or "")
            expl = [x for x in no_sup if note and x in note]   # stages_note 点到该段 id：已说明（有理由）
            rest = [x for x in no_sup if x not in expl]
            if expl:
                P["explained"].append(dict(row, stages=expl, note=note))
            if rest:
                P["unsupported"].append(dict(row, stages=rest))
        elif no_sup:
            P["uncertain"].append(dict(row, stages=no_sup, kind="目录所标段无法判定"))
        P["ok"] += sum(1 for x in cat if x in exact)
        P["ok_approx"] += sum(1 for x in cat if x in approx and x not in exact)
        # 判据支持但目录没标
        ex_new = [x for x in exact if x not in cat and x not in sup_decl]
        ap_new = [x for x in approx if x not in cat and x not in exact and x not in sup_decl]
        if ex_new:
            P["missing"].append(dict(row, stages=ex_new))
        if ap_new:
            P["uncertain"].append(dict(row, stages=ap_new, kind="仅近似支持，目录未标"))
        # 参与者
        if u:
            rs = {r.get("id") for r in u[0].get("roles") or [] if isinstance(r, dict) and r.get("as") != "informed" and str(r.get("id", "")).startswith("SH-")}
            rs |= {st.get("actor") for st in u[1] if str(st.get("actor", "")).startswith("SH-")}
            target = [x for x in cat if x in sup or x in cat] + [x for x in exact if x not in cat]
            allowed = set().union(*[part.get(x, set()) for x in target]) if target else set()
            bad = sorted(rs - allowed) if target else []
            if bad:
                P["role"].append({"id": p["id"], "name": p["name"], "type": p["type"], "bad": bad, "stages": target})
    R["place"] = P
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

CRITERION = """- 主判据：流程（或其步骤经状态机到达的状态）造出某段的退出条件（已展开价值流级 exit_common / entry_common）或该段 in_stage_states 所列的段内状态，就归该段；进入条件不作归段依据。
- 事件策略（type event_policy）看触发状态（触发事件对应的对象状态）所在段：触发状态是哪段的退出条件，策略就归那段；若它同时是本价值流（按流程 owner_bc 所属价值流，取不到时按目录已标的价值流）某段的进入条件，归以它为进入条件的那一段。
- 流程参与者（骨架 roles / 步骤 actor 的 SH- 编号）应在该段参与者中（价值流阶段的 participants 字段）。
- 不按部门或时间先后归段。
（用户 2026-10-16，经 one-coordinator，intent 已记，#421）"""


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
    P = R["place"]
    w(f"| 6 归段：目录标了但判据不支持（未说明）/ 其中另有已说明 / 判据支持但目录没标 / 待人工 / 参与者不在该段 | "
      f"{len(P['unsupported'])} / {len(P['explained'])} / {len(P['missing'])} / {len(P['uncertain'])} / {len(P['role'])} |")
    w(f"| 附 骨架阶段与目录阶段不一致 | {len(R['skel_diff'])} |")
    w(f"| 附 一拆多对象缺 agg 限定（待人工） | {len(R['agg_missing'])} |")
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

    w("## 6 归段\n")
    w("按报告末尾的归段判据，对每条流程推出「应归段」，与目录 `stages` 比较。判据支持的证据分精确 / 近似：精确才算「支持」，仅近似的目录所标段不报，目录未标的仅近似段进「待人工」。"
      f"目录所标段有精确支持 {P['ok']} 处，仅近似支持 {P['ok_approx']} 处（后者不报）。已声明支撑的 {P.get('support_skipped', 0)} 条流程不参与归段。结局文本只写通用状态词、没写对象名的，按近似。\n")

    def ev1(d):
        return "；".join(f"{sid}←{e[0][2]}" if e else sid for sid, e in d.items())

    def evtxt(r, sid):
        e = (r["exact"].get(sid) or r["approx"].get(sid) or [None])[0]
        return f"{e[1]}「{e[2]}」" if e else ""
    w(f"### 6a 目录标了但判据不支持（{len(P['unsupported'])}）\n")
    if P["unsupported"]:
        w("| 流程 | 名称 | type | 目录所标 | 不支持的段 | 判据推出的段 |\n|---|---|---|---|---|---|")
        for r in P["unsupported"]:
            w(f"| {r['id']} | {r['name']} | {r['type']} | {'、'.join(r['cat'])} | {'、'.join(r['stages'])} | {'、'.join(list(r['exact']) + [x + '(近似)' for x in r['approx']]) or '无'} |")
    w("")
    w(f"### 6a' 目录标了但判据不支持，stages_note 已点到该段（已说明，有理由，不计入问题数）（{len(P['explained'])}）\n")
    if P["explained"]:
        w("| 流程 | 名称 | type | 目录所标 | 已说明的段 | 判据推出的段 |\n|---|---|---|---|---|---|")
        for r in P["explained"]:
            w(f"| {r['id']} | {r['name']} | {r['type']} | {'、'.join(r['cat'])} | {'、'.join(r['stages'])} | {'、'.join(list(r['exact']) + [x + '(近似)' for x in r['approx']]) or '无'} |")
    w("")
    w(f"### 6b 判据支持但目录没标（{len(P['missing'])}）\n")
    if P["missing"]:
        w("| 流程 | 名称 | type | 目录所标 | 应加的段 | 证据 |\n|---|---|---|---|---|---|")
        for r in P["missing"]:
            w(f"| {r['id']} | {r['name']} | {r['type']} | {'、'.join(r['cat']) or '（未落阶段）'}{'（已声明支撑）' if r['support'] else ''} | "
              f"{'、'.join(r['stages'])} | {'；'.join(evtxt(r, x) for x in r['stages'])} |")
    w("")
    w(f"### 6c 待人工（{len(P['uncertain'])}）\n")
    if P["uncertain"]:
        w("| 流程 | 名称 | type | 目录所标 | 段 | 原因 |\n|---|---|---|---|---|---|")
        for r in P["uncertain"]:
            reason = r["kind"] + ("：" + "；".join(r["why"]) if r["why"] else "")
            w(f"| {r['id']} | {r['name']} | {r['type']} | {'、'.join(r['cat'])} | {'、'.join(r['stages'])} | {reason} |")
    w("")
    w(f"### 6d 参与者不在该段参与者中（{len(P['role'])}）\n")
    w("流程的 SH- 编号（roles 去掉 as: informed，加步骤 actor）不在其所标段与判据支持段的 participants 并集里。\n")
    if P["role"]:
        w("| 流程 | 名称 | type | 对照的段 | 不在其中的参与者 |\n|---|---|---|---|---|")
        for r in P["role"]:
            w(f"| {r['id']} | {r['name']} | {r['type']} | {'、'.join(r['stages'])} | {'、'.join(r['bad'])} |")
    w("")

    w(f"## 附 一拆多对象缺 agg 限定（{len(R['agg_missing'])}）\n")
    w("一个信息图对象由多个聚合承载时，阶段条件叶子应写 `agg: AGG-xx` 限定；没写、且按本价值流推不出唯一聚合的，其「步骤到达」不计入匹配（避免串段），标待人工。"
      f"一拆多对象：{'、'.join(f'{o}（{ ' '.join(sorted(a))}）' for o, a in sorted(R['split'].items()))}。\n")
    if R["agg_missing"]:
        w("| 阶段 | 叶子 | 可能的聚合 |\n|---|---|---|")
        for (sid, ref, st), cands in sorted(R["agg_missing"].items()):
            w(f"| {sid} | {ref}{onames.get(ref, '')}「{st}」 | {'、'.join(sorted(cands))} |")
    else:
        w("无。")
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
- 自环：状态机里 from == to 的转换（如提交过账候选）、`kind: non_state` 的转换与单聚合骨架 `transitions` 的自环不改业务状态，其 to 不算「步骤到达」，不参与第 3、4、6 项。
- 事件归段（第 6 项）：event_policy 与「triggers 全为 type: event」的 business 流程都按事件策略归段。触发事件名先经各组 commands.yaml 的 emits 找到发出该事件的命令，再取状态机里该命令转换的 to 与聚合作为触发状态（自环、non_state 不算），按聚合与状态比对各段退出 / 进入条件；映射找不到再按词表别名与近似规则比对事件名。
- 一拆多：同一 OBJ 被 ≥ 2 个聚合的「聚合」列承载且至少一个「对应」列写一拆多者。阶段条件叶子可带可选 `agg: AGG-xx`（或列表），步骤到达只在到达状态所属聚合在其中时才算；没带 agg 的一拆多叶子，取本价值流内匹配该状态的步骤到达所属聚合，唯一则采用，多个则标待人工并不计步骤到达（列在「缺 agg 限定」清单），没有则无步骤到达可算。结局文本类匹配不受影响。
- 退出条件「满足」= 存在落在本段的某条流程的某个结局匹配；整体 tier 取 any 的最好者、all 的最差者。
- 进入条件的产生方在全部非合并流程里找（含别的价值流），并标注与本段的关系（同价值流前段 / 本段 / 后段 / 别的价值流 / 未落阶段）。
- 状态名与结局用词不同（如「赢单」与「商机已转赢单」）、结局只写在 notes 里、事件用英文名，都会被判为匹配不上；它们提示的是用语对齐问题，不一定是流程缺失。
- 稀少：价值流流程数 ≤ %d 或某段 0 条流程。
- 归段（第 6 项）补充三条（revenue 复跑发现，one-architecture 裁定）：① 各段退出、进入条件展开价值流级 `stage_pattern` 的 exit_common / entry_common（引用处展开，第 3、4、6 项同口径）；② 主判据扩到段内状态：流程结局或步骤到达的状态是某段 `in_stage_states` 所列状态，也算支持归该段，进入条件不算归段依据；③ 事件策略的触发状态若同时是别段的退出、本价值流某段的进入，归本价值流里以它为进入条件的那一段；本价值流取流程 owner_bc 所属价值流（目录里同 owner_bc 流程所标价值流的并集），取不到时按该流程目录已标的价值流。
- 归段（第 6 项）：「造出退出条件」= 流程的结局文本或步骤到达的状态匹配某段退出条件的某个叶子（含引用展开的公共叶子与 in_stage_states；不用流程名称匹配）。事件策略只看骨架 `triggers` 里 type 为 event 的事件名，与各段退出叶子按同一精确 / 近似规则比对，命中的段即触发状态所在段（不再看其结局）；非对象状态的事件（如过账事实）对不上则不参与，全部对不上进待人工。读模型与无骨架流程进待人工。事件对应的状态同时是多段退出条件时，这些段都算。

## 归段判据（原文）

%s""" % (SMALL_VS, CRITERION))
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


def top_place(R, n=10):
    P = R["place"]
    items = []
    for r in P["missing"]:
        if not r["cat"]:
            items.append((0, f"未落阶段，判据归 {'、'.join(r['stages'])}：{r['id']} {r['name']}（{r['type']}）"))
        else:
            items.append((1, f"目录缺段，判据加 {'、'.join(r['stages'])}：{r['id']} {r['name']}（{r['type']}，目录 {'、'.join(r['cat'])}）"))
    for r in P["unsupported"]:
        pri = 2 if r["exact"] else 3
        tail = f"，判据推出 {'、'.join(r['exact'])}" if r["exact"] else "，判据推不出任何段"
        items.append((pri, f"目录标 {'、'.join(r['stages'])} 判据不支持：{r['id']} {r['name']}（{r['type']}）{tail}"))
    for r in P["role"]:
        items.append((4, f"参与者 {'、'.join(r['bad'])} 不在 {'、'.join(r['stages'])} 参与者中：{r['id']} {r['name']}"))
    items.sort(key=lambda x: x[0])
    out, per = [], {}
    for pri, t in items:
        if per.get(pri, 0) >= 4:
            continue
        per[pri] = per.get(pri, 0) + 1
        out.append(t)
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
         f"6 归段 目录标了但判据不支持（未说明）{len(R['place']['unsupported'])} / 已说明（stages_note 点到该段）{len(R['place']['explained'])} / 判据支持但目录没标 {len(R['place']['missing'])} / 待人工 {len(R['place']['uncertain'])} / 参与者不在该段 {len(R['place']['role'])}（目录所标段精确支持 {R['place']['ok']}，仅近似 {R['place']['ok_approx']}）",
         f"附 一拆多对象缺 agg 限定 {len(R['agg_missing'])}",
         "最值得注意的 10 条："]
    L += [f"  {i}. {t}" for i, t in enumerate(top_findings(R, onames), 1)]
    L.append("第 6 项最值得注意的 10 条：")
    L += [f"  {i}. {t}" for i, t in enumerate(top_place(R), 1)]
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
    # 自环与一拆多 agg 限定
    vs7 = {"value_streams": [{"id": "VS-W", "name": "w", "stages": [
        {"id": "VS-W.1", "name": "甲", "exit": {"all": [{"ref": "OBJ-1", "state": "已生效", "agg": "AGG-A"}]}},
        {"id": "VS-W.2", "name": "乙", "exit": {"all": [{"ref": "OBJ-1", "state": "已生效"}]}},
        {"id": "VS-W.3", "name": "丙", "exit": {"all": [{"ref": "OBJ-1", "state": "已结清"}]}},
        {"id": "VS-W.4", "name": "丁", "exit": {"all": [{"ref": "OBJ-2", "state": "已关闭"}]}}]}]}
    cat7 = [{"id": "W1", "name": "做甲", "type": "business", "stages": ["VS-W.1"], "owner_bc": "B-1"},
            {"id": "W2", "name": "做乙", "type": "business", "stages": ["VS-W.2"], "owner_bc": "B-1"},
            {"id": "W3", "name": "过账", "type": "business", "stages": ["VS-W.3"], "owner_bc": "B-1"},
            {"id": "W4", "name": "关闭", "type": "business", "stages": ["VS-W.4"], "owner_bc": "B-1"}]
    def sk7(a, c):
        return ({"outcomes": ["无关"], "stages": []}, [{"actor": "SH-E1", "where": "in_system", "aggregate": a, "command": c}])
    skw = {"W1": sk7("AGG-A", "生效A"), "W2": sk7("AGG-B", "生效B"), "W3": sk7("AGG-A", "提交过账候选"), "W4": sk7("AGG-C", "关闭C")}
    skw["W1"][0]["stages"] = ["VS-W.1"]; skw["W2"][0]["stages"] = ["VS-W.2"]; skw["W3"][0]["stages"] = ["VS-W.3"]; skw["W4"][0]["stages"] = ["VS-W.4"]
    machw = {"AGG-A": {"aggregate": "AGG-A", "transitions": [{"from": "x", "to": "已生效", "command": "生效A"},
                                                          {"from": "已结清", "to": "已结清", "command": "提交过账候选"}]},
             "AGG-B": {"aggregate": "AGG-B", "transitions": [{"from": "x", "to": "已生效", "command": "生效B"}]},
             "AGG-C": {"aggregate": "AGG-C", "transitions": [{"from": ["a", "b"], "to": "已关闭", "command": "关闭C"}]}}
    aggw = {"AGG-A": {"objs": ["OBJ-1"], "corr": "一拆多"}, "AGG-B": {"objs": ["OBJ-1"], "corr": "一拆多"}, "AGG-C": {"objs": ["OBJ-2"], "corr": ""}}
    Rw = analyze(vs7, cat7, skw, {"OBJ-1": "协议", "OBJ-2": "单"}, {}, machw, aggw)
    xw = {e["stage"]: e for e in Rw["exit"]}
    assert Rw["split"] == {"OBJ-1": {"AGG-A", "AGG-B"}}, Rw["split"]
    assert xw["VS-W.1"]["tier"] == "exact" and xw["VS-W.1"]["by_arr"], xw["VS-W.1"]       # agg 限定匹配
    assert xw["VS-W.2"]["tier"] is None, xw["VS-W.2"]                                     # 缺限定且本价值流有两个聚合：待人工，不计步骤到达
    assert list(Rw["agg_missing"]) == [("VS-W.2", "OBJ-1", "已生效")] and Rw["agg_missing"][("VS-W.2", "OBJ-1", "已生效")] == {"AGG-A", "AGG-B"}
    assert xw["VS-W.3"]["tier"] is None, xw["VS-W.3"]                                     # 自环到达的「已结清」不算
    assert xw["VS-W.4"]["tier"] == "exact", xw["VS-W.4"]                                  # 非一拆多对象不受影响
    assert "缺 agg 限定" in render(Rw, {"OBJ-1": "协议"}, {}, 4, 4)
    n = Norm({"销售合同": "合同"})
    assert n("销售合同已生效") == "合同生效"
    assert match_atom("商机", "已确认（转商机）", "已转商机", n) == "exact"
    assert match_atom("商机", "赢单", "商机已赢单", n) == "exact"
    assert match_atom("商机", "赢单", "完全无关", n) is None
    assert match_atom("费用报销", "已付", "费用报销已付", n) == "exact"
    # 括号内的斜杠不拆
    assert split_outside("减员（转移 / 封存）") == ["减员（转移 / 封存）"]
    assert split_outside("已结项 → 已归档") == ["已结项 ", " 已归档"]
    assert match_atom("参保", "减员（转移 / 封存）", "参保减员", n) == "exact"
    assert not any("转移" == v for v in variants("减员（转移 / 封存）", n)) and "减员" in variants("减员（转移 / 封存）", n)
    # 6 归段
    vs6 = {"value_streams": [{"id": "VS-Z", "name": "z", "stages": [
        {"id": "VS-Z.1", "name": "一", "participants": ["SH-E1"], "exit": {"all": [{"ref": "OBJ-1", "state": "已完成"}]}},
        {"id": "VS-Z.2", "name": "二", "participants": ["SH-E2"], "exit": {"all": [{"ref": "OBJ-2", "state": "已结项 → 已归档"}]}},
        {"id": "VS-Z.3", "name": "三", "participants": ["SH-E2"], "exit": {"all": [{"ref": "OBJ-3", "state": "已发布"}]}}]}]}
    cat6 = [{"id": "A", "name": "做完", "type": "business", "stages": ["VS-Z.1"], "owner_bc": "B-1"},
            {"id": "B", "name": "结项", "type": "business", "stages": ["VS-Z.1"], "owner_bc": "B-1"},
            {"id": "C", "name": "未落结项", "type": "business", "stages": [], "owner_bc": "B-1"},
            {"id": "D", "name": "结项后清算", "type": "event_policy", "stages": ["VS-Z.1"], "owner_bc": "B-1"},
            {"id": "E", "name": "读", "type": "read_model", "stages": ["VS-Z.1"], "owner_bc": "B-1"},
            {"id": "F", "name": "对不上事件", "type": "event_policy", "stages": ["VS-Z.2"], "owner_bc": "B-1"}]
    sk6 = {"A": ({"outcomes": ["甲已完成"], "stages": ["VS-Z.1"], "roles": [{"id": "SH-E1", "as": "performer"}, {"id": "SH-E9", "as": "informed"}]}, []),
           "B": ({"outcomes": ["项目已结项"], "stages": ["VS-Z.1"], "roles": [{"id": "SH-E1", "as": "performer"}, {"id": "SH-E7", "as": "performer"}]}, []),
           "C": ({"outcomes": ["项目已结项"], "stages": [], "roles": [{"id": "SH-E2", "as": "performer"}]}, []),
           "D": ({"outcomes": ["清算成本已过账"], "stages": ["VS-Z.1"], "triggers": [{"type": "event", "name": "项目已结项"}, {"type": "event", "name": "过账事实"}]}, []),
           "E": ({"outcomes": [], "stages": ["VS-Z.1"]}, []),
           "F": ({"outcomes": ["项目已结项"], "stages": ["VS-Z.2"], "triggers": [{"type": "event", "name": "毫无关系的事件"}]}, [])}
    on6 = {"OBJ-1": "甲", "OBJ-2": "项目", "OBJ-3": "报告"}
    P6 = analyze(vs6, cat6, sk6, on6, {})["place"]
    assert P6["explained"] == [], P6["explained"]
    c6 = [dict(x, stages_note="留 VS-Z.1，因为某理由") if x["id"] == "B" else x for x in cat6]
    P6n = analyze(vs6, c6, sk6, on6, {})["place"]
    assert "B" not in [r["id"] for r in P6n["unsupported"]] and [r["id"] for r in P6n["explained"]] == ["B"], (P6n["unsupported"], P6n["explained"])
    c6b = [dict(x, stages_note="留 VS-Z.9") if x["id"] == "B" else x for x in cat6]
    assert "B" in [r["id"] for r in analyze(vs6, c6b, sk6, on6, {})["place"]["unsupported"]]   # 未点到该段照常计入
    sk6n = dict(sk6, B=(dict(sk6["B"][0], stages_note="骨架说明 VS-Z.1"), []))
    noted = analyze(vs6, c6b, sk6n, on6, {})["place"]
    assert [r["id"] for r in noted["explained"]] == ["B"] and noted["explained"][0]["note"] == "骨架说明 VS-Z.1"
    sk6wrong = dict(sk6, B=(dict(sk6["B"][0], stages_note="骨架只说明 VS-Z.9"), []))
    assert "B" in [r["id"] for r in analyze(vs6, c6, sk6wrong, on6, {})["place"]["unsupported"]]
    sk6empty = dict(sk6, B=(dict(sk6["B"][0], stages_note=""), []))
    assert "B" in [r["id"] for r in analyze(vs6, c6, sk6empty, on6, {})["place"]["unsupported"]]
    import closeout_check as cc
    import tempfile
    from unittest.mock import patch
    with tempfile.TemporaryDirectory() as directory:
        path = Path(directory) / "extra.yaml"
        path.write_text(yaml.safe_dump({"processes": [dict(cat6[1], **sk6n["B"][0]), dict(cat6[0], id="NEW", name="新增", outcomes=["甲已完成"])]}), encoding="utf-8")
        extra = cc.load_extra_units(cc.extra_paths(["--extra", str(path), "--extra", str(path)]))
        with patch.object(cc, "read_group_file", return_value=None), patch("sys.stderr"):
            loaded = load_skeletons(True, extra)
        assert set(loaded) == {"B", "NEW"} and loaded["B"][0]["stages_note"] == "骨架说明 VS-Z.1"
        cextra = cc.overlay_catalog(cat6, extra)
        pextra = analyze(vs6, cextra, dict(sk6, **loaded), on6, {})["place"]
        assert "B" in [r["id"] for r in pextra["explained"]]
        assert pextra["ok"] == P6["ok"] + 1, pextra
    assert [r["id"] for r in P6["unsupported"]] == ["B", "D"] and P6["unsupported"][0]["exact"].keys() == {"VS-Z.2"}, P6["unsupported"]
    assert {r["id"]: r["stages"] for r in P6["missing"]} == {"B": ["VS-Z.2"], "C": ["VS-Z.2"], "D": ["VS-Z.2"]}, P6["missing"]
    assert sorted(r["id"] for r in P6["uncertain"]) == ["E", "F"], P6["uncertain"]   # 读模型、触发事件对不上
    assert [r["id"] for r in P6["role"]] == ["B"] and P6["role"][0]["bad"] == ["SH-E7"], P6["role"]   # informed 不计
    assert P6["ok"] == 1
    t6 = render(analyze(vs6, cat6, sk6, on6, {}), on6, {}, 6, 6)
    assert "## 归段判据（原文）" in t6 and "不按部门或时间先后归段" in t6
    # 6 补充三条：展开 exit_common、段内状态、事件策略归进入段
    vsq = {"value_streams": [
        {"id": "VS-Q", "name": "q", "stage_pattern": {"exit_common": {"all": [{"ref": "OBJ-1", "state": "已认可"}]}}, "stages": [
            {"id": "VS-Q.1", "name": "甲", "participants": [], "exit": {"pattern": "exit_common"}},
            {"id": "VS-Q.2", "name": "乙", "participants": [], "exit": {"all": [{"ref": "OBJ-3", "state": "已发布"}]},
             "in_stage_states": [{"ref": "OBJ-2", "state": "已备案"}]}]},
        {"id": "VS-R", "name": "r", "stages": [
            {"id": "VS-R.1", "name": "丙", "participants": [], "entry": {"all": [{"ref": "OBJ-1", "state": "已认可"}]}, "exit": {"all": [{"ref": "OBJ-4", "state": "已确认"}]}},
            {"id": "VS-R.2", "name": "丁", "participants": [], "exit": {"all": [{"ref": "OBJ-5", "state": "已入账"}]}}]}]}
    catq = [{"id": "Q1", "name": "认可", "type": "business", "stages": ["VS-Q.1"], "owner_bc": "B-1"},
            {"id": "Q2", "name": "备案", "type": "business", "stages": ["VS-Q.2"], "owner_bc": "B-1"},
            {"id": "Q3", "name": "确认", "type": "event_policy", "stages": ["VS-R.1"], "owner_bc": "B-2"},
            {"id": "Q4", "name": "占位", "type": "business", "stages": ["VS-R.2"], "owner_bc": "B-2"}]
    skq = {"Q1": ({"outcomes": ["交付物已认可"], "stages": ["VS-Q.1"]}, []),
           "Q2": ({"outcomes": ["项目已备案"], "stages": ["VS-Q.2"]}, []),
           "Q3": ({"outcomes": ["x"], "stages": ["VS-R.1"], "triggers": [{"type": "event", "name": "交付物已认可"}]}, []),
           "Q4": ({"outcomes": ["x"], "stages": ["VS-R.2"]}, [])}
    onq = {"OBJ-1": "交付物", "OBJ-2": "项目", "OBJ-3": "报告", "OBJ-4": "收入", "OBJ-5": "分录"}
    Pq = analyze(vsq, catq, skq, onq, {})["place"]
    unq = {r["id"] for r in Pq["unsupported"]}
    assert "Q1" not in unq and "Q2" not in unq, Pq["unsupported"]   # exit_common 展开；段内状态支持
    assert "Q3" not in unq and not [r for r in Pq["missing"] if r["id"] == "Q3"], (Pq["unsupported"], Pq["missing"])   # 触发状态归以它为进入的 VS-R.1，不归 VS-Q.1
    # non_state 转换不算到达；纯事件触发的 business 归段；事件名经 emits 映射到状态
    vse = {"value_streams": [{"id": "VS-E", "name": "事件流", "stages": [
        {"id": "VS-E.1", "name": "甲", "exit": {"all": [{"ref": "OBJ-1", "state": "试用期 / 正式"}]}},
        {"id": "VS-E.2", "name": "乙", "exit": {"all": [{"ref": "OBJ-1", "state": "已离职"}]}},
        {"id": "VS-E.3", "name": "丙", "exit": {"all": [{"ref": "OBJ-1", "state": "已记学时"}]}}]}]}
    cate = [{"id": "E1", "name": "开通账号", "type": "business", "stages": ["VS-E.1"], "owner_bc": "B-1"},
            {"id": "E2", "name": "停用账号", "type": "business", "stages": ["VS-E.1"], "owner_bc": "B-1"},
            {"id": "E3", "name": "登记学时", "type": "business", "stages": ["VS-E.3"], "owner_bc": "B-1"}]
    ske = {"E1": ({"outcomes": ["账号已开通"], "stages": ["VS-E.1"], "triggers": [{"type": "event", "name": "入职已登记"}]}, []),
           "E2": ({"outcomes": ["账号已停用"], "stages": ["VS-E.1"], "triggers": [{"type": "event", "name": "Separated"}, {"type": "manual", "name": "人工"}]}, []),
           "E3": ({"outcomes": ["x"], "stages": ["VS-E.3"]}, [{"actor": "SH-E1", "where": "in_system", "aggregate": "AGG-E", "command": "登记学时"}])}
    mache = {"AGG-E": {"aggregate": "AGG-E", "transitions": [{"from": None, "to": "试用期", "command": "登记入职", "kind": "forward"},
                                                            {"from": "试用期", "to": "已记学时", "command": "登记学时", "kind": "non_state"}]}}
    agge = {"AGG-E": {"objs": ["OBJ-1"], "corr": ""}}
    evm = {"入职已登记": [("AGG-E", "试用期", frozenset(["OBJ-1"]))]}
    Re = analyze(vse, cate, ske, {"OBJ-1": "人员"}, {}, mache, agge, evm)["place"]
    assert "E1" not in {r["id"] for r in Re["unsupported"]}, Re["unsupported"]        # 纯事件触发的 business 经 emits 映射归 VS-E.1
    assert "E2" in {r["id"] for r in Re["unsupported"]}, Re["unsupported"]            # 含人工入口的不按事件策略；结局不造退出条件
    assert "E3" in {r["id"] for r in Re["unsupported"]}, Re["unsupported"]            # non_state 转换不算到达
    tt = texts_of({"aggregate": "AGG-S", "states": {"g": ["已关联"]}, "transitions": [{"from": "a", "to": "已封存", "aggregate": "AGG-T"}, {"from": "a", "to": "已解除"}]}, [],
                  {"AGG-S": {"objs": ["OBJ-7"]}, "AGG-T": {"objs": []}}, {"OBJ-7": "证据"})
    assert ("状态", "证据已关联") in tt and ("状态", "证据已封存") in tt and ("状态", "证据已解除") in tt, tt   # 单聚合状态带对象名（条目聚合无对象退回单元聚合）
    vsu = {"value_streams": [{"id": "VS-U", "name": "u", "stages": [
        {"id": "VS-U.1", "name": "甲", "exit": {"all": [{"ref": "OBJ-1", "state": "已生效"}]}},
        {"id": "VS-U.2", "name": "乙", "exit": {"all": [{"ref": "OBJ-2", "state": "已结项"}]}}]}]}
    onu = {"OBJ-1": "协议", "OBJ-2": "项目"}
    sku = {"U1": ({"outcomes": ["协议已生效"], "stages": ["VS-U.2"]}, [])}
    catu = [{"id": "U1", "name": "支撑协议", "type": "business", "stages": ["VS-U.2"], "owner_bc": "B-1"}]
    assert [r["id"] for r in analyze(vsu, catu, sku, onu, {})["place"]["missing"]] == ["U1"]   # 未声明照报 6b
    catus = [dict(catu[0], supports=["VS-U.1"])]
    assert not analyze(vsu, catus, sku, onu, {})["place"]["missing"]                          # supports 视为已声明
    sku_s = {"U1": (dict(sku["U1"][0], supports=["VS-U.1"]), [])}
    assert not analyze(vsu, catu, sku_s, onu, {})["place"]["missing"]                         # 骨架声明同样认
    for bad_cat in ([dict(catu[0], supports=["VS-U.9"])], [dict(catu[0], supports=["VS-U.2"])]):
        try:
            analyze(vsu, bad_cat, sku, onu, {})
        except ValueError:
            pass
        else:
            raise AssertionError(f"无效 supports 未拦下：{bad_cat[0]['supports']}")
    assert load_event_map.__doc__
    txt = render(R, on, {}, 2, 6)
    assert "匹配口径" in txt and "VS-X.3" in txt
    print("self-test 通过")


def main():
    if "--self-test" in sys.argv:
        self_test()
        return
    local = "--local" in sys.argv
    import closeout_check as cc
    try:
        extra = cc.load_extra_units(cc.extra_paths(sys.argv[1:]))
    except ValueError as error:
        sys.exit(str(error))
    vs_doc = yaml.safe_load(VS_FILE.read_text(encoding="utf-8"))
    catalog = cc.overlay_catalog(yaml.safe_load(CATALOG.read_text(encoding="utf-8"))["processes"], extra)
    skeletons = load_skeletons(local, extra)
    onames = load_objnames()
    machines, aggs = load_machines(local), cc.load_aggregates()
    try:
        R = analyze(vs_doc, catalog, skeletons, onames, load_alias_map(), machines, aggs, load_event_map(local, machines, aggs))
    except ValueError as error:
        sys.exit(str(error))
    procs = [p for p in catalog if p.get("type") != "merged"]
    skel_n = sum(1 for p in procs if p["id"] in skeletons)
    text = render(R, onames, {p["id"]: p for p in procs}, skel_n, len(procs))
    if "--no-write" not in sys.argv:
        REPORT.write_text(text, encoding="utf-8")
    print(console(R, onames, skel_n, len(procs)))


if __name__ == "__main__":
    main()
