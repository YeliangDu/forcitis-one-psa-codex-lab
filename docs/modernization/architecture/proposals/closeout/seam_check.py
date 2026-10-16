"""Cross-group seam check (read-only): do the revenue, finance and people closeout skeletons fit together?

Usage: python3 -I seam_check.py [--local] [--no-write] [--self-test]
  default       read processes / commands / state-machines / events files from origin/claude/one-arch-<group>-closeout (git show; run git fetch first)
  --local       read them from closeout/<group>/ in this working tree instead
  --no-write    print the summary only, do not write SEAM_REPORT.md
  --self-test   run the built-in minimal samples (one per check) and exit

Checks (findings only, exit code is always 0 except --self-test failures):
  1 cross-BC calls: every cross_bc entry (and event trigger) of a skeleton step is an edge in section 4.1 of ENTERPRISE_AGGREGATES_DRAFT.md with the same
    way (api / event); a call that goes from an upstream BC to a downstream BC is listed separately.
  2 event pairing: an out event has an in at the subscriber BC (names equal after normalisation); an in event / declared subscription exists on the publisher
    side (command catalog emits, draft event column, other skeleton out).
  3 cross-group commands: a step that calls a command of an aggregate catalogued by another group: the command exists in that group's commands.yaml,
    the caller is registered (callers / also_used_by / used_by) and state-machines.yaml has a transition for it.
  4 cycles: edges the skeletons really use, added to the section 4.1 edge set (draft edges plus built edges), checked for cycles.
  5 orphan events: draft event column lists a business subscriber but no skeleton step or trigger receives the event.
  6 same-meaning-different-name candidates: two names for one aggregate command or one publisher BC event that are close but not equal.
"""
import collections
import difflib
import importlib.util
import re
import subprocess
import sys
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent
PROPOSALS = HERE.parent
REPO = PROPOSALS.parents[3]
DRAFT = PROPOSALS / "ENTERPRISE_AGGREGATES_DRAFT.md"
TERMS = REPO / "docs/modernization/glossary/terms.yaml"
REPORT = HERE / "SEAM_REPORT.md"
GROUPS = ["revenue", "finance", "people"]
MAX_LINES = 30
SIM = 0.75
BC_RE = re.compile(r"B-\d+")
PLAT_RE = re.compile(r"platform\.[a-z]+")


def load_module(path, name):
    argv = sys.argv
    sys.argv = [argv[0]]
    try:
        spec = importlib.util.spec_from_file_location(name, path)
        mod = importlib.util.module_from_spec(spec)
        sys.modules[name] = mod
        spec.loader.exec_module(mod)
    finally:
        sys.argv = argv
    return mod


CC = load_module(HERE / "closeout_check.py", "closeout_check")
DEP = load_module(PROPOSALS / "aggregates_dep_check.py", "aggregates_dep_check")
base_cmd, units_of, unit_steps = CC.base_cmd, CC.units_of, CC.unit_steps


# ---------------------------------------------------------------- reading
def read_text(group, name, local):
    base = "docs/modernization/architecture/proposals/closeout/" + group
    if local:
        p = HERE / group / name
        return p.read_text(encoding="utf-8") if p.exists() else None
    return CC.git_show(f"claude/one-arch-{group}-closeout", f"{base}/{name}")


def read_yaml(group, name, local):
    t = read_text(group, name, local)
    return yaml.safe_load(t) if t else None


def split_top(text, seps):
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


def parse_draft(text):
    """aggregates: {agg: {bc, name, commands[], events[(name, subs)]}} from section 2.2; plus dep-check owner/deps/modules."""
    owner, deps, counted, modules = DEP.parse(text)
    aggs, bc, idx, section = {}, None, None, None
    for line in text.splitlines():
        if line.startswith("### "):
            section = line[4:].split(" ")[0]
        if section != "2.2":
            continue
        m = re.match(r"^\*\*(B-\d+)\b", line)
        if m:
            bc = m.group(1)
            continue
        if line.startswith("| #"):
            h = DEP.cells(line)
            idx = {k: next((i for i, c in enumerate(h) if c.startswith(p)), None) for k, p in
                   (("name", "聚合"), ("cmd", "命令"), ("evt", "对外事件"))}
            continue
        m = re.match(r"^\|\s*((?:AGG|REF)-\d+)\s*\|", line)
        if m and bc and idx:
            row = DEP.cells(line)
            events = []
            for item in split_top(row[idx["evt"]], "、"):
                if item.startswith("—"):
                    continue
                name, _, paren = item.partition("（")
                subs = BC_RE.findall(paren) + PLAT_RE.findall(paren)
                only = "仅分析" in paren
                names = [n.strip() for n in name.split("/") if n.strip()]
                if len(names) > 1:
                    prefix = names[0].rsplit("已", 1)[0]
                    names = [names[0]] + [prefix + n if n.startswith("已") else n for n in names[1:]]
                for n in names:
                    events.append((n, subs, only))
            cmds = [re.split(r"[（(]", c, 1)[0].strip() for c in split_top(row[idx["cmd"]], "；") if not c.startswith("—")]
            aggs[m.group(1)] = {"bc": bc, "name": re.split(r"[（(]", row[idx["name"]], 1)[0].strip(), "commands": cmds, "events": events}
    return aggs, owner, deps, modules


def load_terms():
    """name (zh, en, alias) -> en, for domain events."""
    out = {}
    try:
        for t in yaml.safe_load(TERMS.read_text(encoding="utf-8"))["terms"]:
            if t.get("kind") != "domain_event":
                continue
            for n in [t["zh"], t["en"]] + [a.get("name") for a in t.get("aliases") or [] if isinstance(a, dict)]:
                if n:
                    out[n] = t["en"]
    except (OSError, KeyError, yaml.YAMLError):
        pass
    return out


# ---------------------------------------------------------------- normalising
def norm_bc(x, modules=None):
    if not x:
        return None
    x = str(x).strip()
    m = BC_RE.match(x)
    if m:
        return m.group(0)
    m = PLAT_RE.match(x)
    if m:
        return m.group(0)
    if modules:
        for b, mod in modules.items():
            if mod and x.startswith(mod):
                return b
    return None


def canon(name, terms):
    n = str(name or "").replace(CC.MISSING, "").replace("`", "")
    n = re.sub(r"^已有", "", n)
    n = re.sub(r"[（(].*?[）)]", "", n)
    n = re.sub(r"\s+", "", n)
    return terms.get(n, n)


def canon_set(name, terms):
    base = re.sub(r"[（(].*?[）)]", "", str(name or "").replace("`", ""))
    return {canon(x, terms) for x in re.split(r"[、/，,]", base) if x.strip()} or {canon(name, terms)}


def similar(a, b):
    if a == b or not a or not b:
        return False
    if len(a) >= 4 and len(b) >= 4 and (a in b or b in a) and abs(len(a) - len(b)) <= 3:
        return True
    return difflib.SequenceMatcher(None, a, b).ratio() >= SIM


# ---------------------------------------------------------------- model
class Ctx:
    def __init__(self, aggs, owner, edges41, built, groups, terms, modules=None):
        self.aggs, self.owner, self.terms, self.modules = aggs, owner, terms, modules or {}
        self.groups = groups
        self.edges41 = {k: set(v) for k, v in edges41.items()}
        self.built = set(built)
        self.adj = collections.defaultdict(set)
        for s, t in list(self.edges41) + list(self.built):
            self.adj[s].add(t)
        self.catalog_group = collections.defaultdict(set)   # agg -> groups that catalogue it
        self.cmd_index = {}                                # (group, agg) -> {base name: command dict}
        self.machine_cmds = {}                             # (group, agg) -> set of transition command names
        self.emits = collections.defaultdict(set)          # bc -> canon event names published
        for g, d in groups.items():
            for c in d.get("commands") or []:
                a = c.get("aggregate")
                self.catalog_group[a].add(g)
                self.cmd_index.setdefault((g, a), {})[base_cmd(c.get("name"))] = c
                for e in c.get("emits") or []:
                    self.add_emit(a, e)
            for m in d.get("sm") or []:
                self.machine_cmds.setdefault((g, m.get("aggregate")), set()).update(
                    base_cmd(t.get("command")) for t in m.get("transitions") or [] if t.get("command"))
            for e in d.get("events") or []:
                if e.get("pub_agg"):
                    self.add_emit(e["pub_agg"], e["name"])
        for a, v in aggs.items():
            for n, _s, _o in v["events"]:
                self.emits[v["bc"]].add(canon(n, terms))
        self.bc_groups = collections.defaultdict(collections.Counter)
        for a, gs in self.catalog_group.items():
            for g in gs:
                if a in owner:
                    self.bc_groups[owner[a]][g] += 1
        for g, d in groups.items():
            for _k, u in units_of(d.get("proc")):
                b = norm_bc(u.get("owner_bc"), self.modules)
                if b:
                    self.bc_groups[b][g] += 1

    def add_emit(self, agg, name):
        b = self.owner.get(agg)
        if b and name:
            self.emits[b].add(canon(name, self.terms))

    def bc_of(self, agg):
        return self.owner.get(agg)

    def group_of_bc(self, bc):
        if not bc:
            return "?"
        if bc.startswith("platform."):
            return "平台"
        c = self.bc_groups.get(bc)
        return c.most_common(1)[0][0] if c else "无骨架"

    def reach(self, src, dst, extra=None):
        adj = self.adj if extra is None else extra
        seen, stack = set(), [src]
        while stack:
            v = stack.pop()
            if v == dst:
                return True
            if v in seen:
                continue
            seen.add(v)
            stack.extend(adj.get(v, ()))
        return False


def local_bc(ctx, step, unit):
    agg = step.get("aggregate")
    if not (isinstance(agg, str) and agg in ctx.owner):
        for i in step.get("inner") or []:
            if isinstance(i, dict) and i.get("aggregate") in ctx.owner:
                agg = i["aggregate"]
                break
    if isinstance(agg, str) and agg in ctx.owner:
        return ctx.owner[agg]
    return norm_bc(step.get("bc"), ctx.modules) or norm_bc(unit.get("owner_bc"), ctx.modules)


def interactions(ctx):
    """All cross-BC interactions of the skeletons: dicts with group, prc, where, local, other, mode, dir, names, raw, src."""
    out = []
    for g, d in ctx.groups.items():
        for _k, u in units_of(d.get("proc")):
            ubc = norm_bc(u.get("owner_bc"), ctx.modules)
            for n, s in enumerate(unit_steps(u), 1):
                lb = local_bc(ctx, s, u)
                for c in s.get("cross_bc") or []:
                    ob = norm_bc(c.get("bc"), ctx.modules)
                    if not lb or not ob or lb == ob:
                        continue
                    out.append({"group": g, "prc": u["id"], "where": f"第{n}步", "local": lb, "other": ob, "mode": c.get("mode"),
                                "dir": c.get("direction"), "raw": str(c.get("name")), "names": canon_set(c.get("name"), ctx.terms), "src": "cross_bc",
                                "agg": s.get("aggregate")})
            for t in u.get("triggers") or []:
                if isinstance(t, dict) and t.get("type") == "event":
                    ob = norm_bc(t.get("bc") or t.get("publisher") or t.get("publisher_bc"), ctx.modules)
                    if not ob:
                        m = BC_RE.search(str(t.get("name")))
                        ob = m.group(0) if m else None
                    names = canon_set(t.get("name"), ctx.terms) | (canon_set(t["event"], ctx.terms) if t.get("event") else set())
                    out.append({"group": g, "prc": u["id"], "where": "触发器", "local": ubc, "other": ob, "mode": "event", "dir": "in",
                                "raw": str(t.get("name")), "names": names, "src": "trigger", "agg": None})
            for e in (u.get("events") or []) + (u.get("subscribes") or []):
                if isinstance(e, dict) and e.get("event"):
                    ob = norm_bc(e.get("bc"), ctx.modules)
                    out.append({"group": g, "prc": u["id"], "where": "事件策略订阅", "local": ubc, "other": ob, "mode": "event", "dir": "in",
                                "raw": str(e["event"]), "names": canon_set(e["event"], ctx.terms), "src": "subscribe", "agg": e.get("aggregate")})
    return [i for i in out if i["local"] and i["local"] != i["other"] or (i["other"] is None and i["local"])]


def needed_edge(i):
    """(source BC = depends, target BC = depended on, way that edge needs, strict way check)."""
    l, o = i["local"], i["other"]
    plat_other = o.startswith("platform.") and not l.startswith("platform.")
    if i["mode"] == "api":
        return (l, o, "api", True) if i["dir"] == "out" or plat_other else (o, l, "api", True)
    if plat_other:
        return (o, l, "事件", True) if i["dir"] == "out" else (l, o, "事件", True)
    return (o, l, "事件", True) if i["dir"] == "out" else (l, o, "事件", True)


# ---------------------------------------------------------------- checks
def check1(ctx, inter):
    res = {"ok": 0, "missing": [], "reversed": [], "mode": [], "callback": [], "skeleton_edges": {}}
    for i in inter:
        if not i["other"]:
            continue
        s, t, way, strict = needed_edge(i)
        i["edge"] = (s, t, way)
        if s.startswith("platform.") and not t.startswith("platform.") and ((t, s) in ctx.edges41 or (t, s) in ctx.built):
            s, t = t, s                      # business publishes, platform subscribes, but the declared edge is business -> platform
            i["edge"] = (s, t, way)
        res["skeleton_edges"].setdefault((s, t), i)
        if i["src"] != "cross_bc" and not (i["src"] == "trigger" and i["other"]):
            continue
        if (s, t) in ctx.edges41:
            if strict and way not in ctx.edges41[(s, t)]:
                (res["callback"] if i["raw"].startswith("审批已") and t == "platform.approval" else res["mode"]).append(i)
            else:
                res["ok"] += 1
        elif (s, t) in ctx.built:
            res["ok"] += 1
        elif ctx.reach(t, s):
            res["reversed"].append(i)
        else:
            res["missing"].append(i)
    return res


def event_inters(inter):
    return [i for i in inter if i["mode"] == "event"]


def check2(ctx, inter):
    ev = event_inters(inter)
    ins = [i for i in ev if i["dir"] == "in"]
    outs = [i for i in ev if i["dir"] == "out" and not i["other"].startswith("platform.")]
    res = {"out_ok": 0, "out_no_in": [], "out_renamed": [], "in_ok": 0, "in_unpublished": [], "in_renamed": [], "in_nopub": 0, "declared": []}
    for o in outs:
        pub, sub = o["local"], o["other"]
        hit = [i for i in ins if i["local"] == sub and i["other"] in (pub, None) and i["names"] & o["names"]]
        if hit:
            res["out_ok"] += 1
            continue
        near = [i for i in ins if i["local"] == sub and i["other"] in (pub, None) and any(similar(a, b) for a in i["names"] for b in o["names"])]
        if near:
            res["out_renamed"].append((o, near[0]))
        else:
            res["out_no_in"].append(o)
    for i in ins:
        if not i["other"] or i["other"].startswith("platform.") or i["local"].startswith("platform."):
            if not i["other"]:
                res["in_nopub"] += 1
            continue
        pub = i["other"]
        known = set(ctx.emits.get(pub, ())) | {n for o in outs if o["local"] == pub for n in o["names"]}
        if i["names"] & known:
            res["in_ok"] += 1
            continue
        near = sorted(n for n in known if any(similar(n, a) for a in i["names"]))
        if near:
            res["in_renamed"].append((i, near[0]))
        else:
            res["in_unpublished"].append(i)
    # declared subscribers in the group catalogs (revenue event_subscribers, finance/people events files)
    received = collections.defaultdict(set)
    for i in ins:
        received[i["local"]] |= i["names"]
    for g, d in ctx.groups.items():
        for e in d.get("events") or []:
            pub = ctx.bc_of(e.get("pub_agg"))
            n = canon(e["name"], ctx.terms)
            for text in e.get("subs") or []:
                for sb in BC_RE.findall(str(text)):
                    if sb == pub:
                        continue
                    if n not in received.get(sb, ()):
                        near = [x for x in received.get(sb, ()) if similar(x, n)]
                        res["declared"].append((g, e["name"], pub, sb, str(text)[:60], near[0] if near else ""))
    return res


def check3(ctx):
    res = {"ok": 0, "no_catalog": [], "no_command": [], "not_registered": [], "no_field": 0, "no_transition": [], "no_machine": 0, "ghost": [], "pairs": []}
    seen = set()
    for g, d in ctx.groups.items():
        for _k, u in units_of(d.get("proc")):
            calls = []
            for n, s in enumerate(unit_steps(u), 1):
                if s.get("read") is True or s.get("actor") == "platform" or s.get("platform_task"):
                    continue
                refs = [(s.get("aggregate"), s.get("command"))] + [(x.get("aggregate"), x.get("command")) for x in s.get("inner") or [] if isinstance(x, dict)]
                calls += [(n, a, c) for a, c in refs]
            calls += [("单元", x.get("aggregate") or u.get("aggregate"), x.get("command")) for x in (u.get("commands") or []) + (u.get("transitions") or [])
                      if isinstance(x, dict)]
            for n, a, c in calls:
                if not (isinstance(a, str) and re.fullmatch(r"(AGG|REF)-\d+", a)) or not c:
                    continue
                owners = ctx.catalog_group.get(a, set())
                if g in owners:
                    continue
                cb = base_cmd(c)
                key = (g, u["id"], a, cb)
                if key in seen:
                    continue
                seen.add(key)
                where = f"{g} {u['id']} 第{n}步" if isinstance(n, int) else f"{g} {u['id']}"
                if not owners:
                    res["no_catalog"].append((g, where, a, cb))
                    continue
                og = next((o for o in sorted(owners) if cb in ctx.cmd_index.get((o, a), {})), sorted(owners)[0])
                cmd = ctx.cmd_index.get((og, a), {}).get(cb)
                if cmd is None:
                    res["no_command"].append((g, where, a, cb, og))
                    continue
                fields = [cmd.get(k) for k in ("callers", "also_used_by", "used_by") if cmd.get(k) is not None]
                if not fields:
                    res["no_field"] += 1
                elif not re.search(re.escape(u["id"]) + r"(?!\d)", yaml.safe_dump(fields, allow_unicode=True)):
                    res["not_registered"].append((g, where, a, cb, og))
                else:
                    res["ok"] += 1
                res["pairs"].append((g, where, a, cb, og))
                mc = ctx.machine_cmds.get((og, a))
                if mc is None:
                    res["no_machine"] += 1
                elif cb not in mc:
                    res["no_transition"].append((g, where, a, cb, og))
    units = {(g, u["id"]): u for g, d in ctx.groups.items() for _k, u in units_of(d.get("proc"))}
    for (og, a), cmds in ctx.cmd_index.items():
        for cb, c in cmds.items():
            for field in ("callers", "also_used_by"):
                v = c.get(field)
                for text in ([v] if isinstance(v, str) else v or []):
                    for cg, prc in re.findall(r"(revenue|finance|people):(PRC-\d+)", str(text)):
                        u = units.get((cg, prc))
                        has = u is not None and any(
                            (x.get("aggregate") == a and base_cmd(x.get("command") or "") == cb)
                            for s in unit_steps(u) for x in [s] + [i for i in s.get("inner") or [] if isinstance(i, dict)]) or (
                            u is not None and any(isinstance(x, dict) and (x.get("aggregate") or u.get("aggregate")) == a and base_cmd(x.get("command") or "") == cb
                                                  for x in (u.get("commands") or []) + (u.get("transitions") or [])))
                        if not has:
                            res["ghost"].append((og, f"{cg} {prc}", a, cb, "流程不存在" if u is None else "该流程没有此步"))
    return res


def check4(ctx, c1):
    adj = {k: set(v) for k, v in ctx.adj.items()}
    new = []
    for (s, t), i in c1["skeleton_edges"].items():
        if (s, t) not in ctx.edges41 and (s, t) not in ctx.built:
            new.append(((s, t), i))
        adj.setdefault(s, set()).add(t)
    nodes = set(adj) | {t for ts in adj.values() for t in ts}
    base = DEP.find_cycles(nodes, {k: set(v) for k, v in ctx.adj.items()})
    cycles = DEP.find_cycles(nodes, adj)
    return {"base": base, "cycles": cycles, "new": new}


def check5(ctx, inter):
    ins = [i for i in event_inters(inter) if i["dir"] == "in"]
    received = collections.defaultdict(set)
    for i in ins:
        received[i["local"]] |= i["names"]
    out, total = [], 0
    for a, v in ctx.aggs.items():
        for n, subs, _only in v["events"]:
            for sb in subs:
                if not sb.startswith("B-") or sb == v["bc"]:
                    continue
                total += 1
                c = canon(n, ctx.terms)
                if c in received.get(sb, ()):
                    continue
                near = [x for x in received.get(sb, ()) if similar(x, c)]
                out.append((a, v["bc"], n, sb, near[0] if near else ""))
    return {"total": total, "orphans": out}


def divergent(sx, sy):
    """Two similar names count as a candidate only when one is used by a skeleton only (no catalog / draft entry) or the groups using them are disjoint."""
    sx, sy = {x[0] if isinstance(x, tuple) else x for x in sx}, {x[0] if isinstance(x, tuple) else x for x in sy}
    official = lambda ss: any(s.startswith(("聚合稿", "目录", "事件表")) for s in ss)
    grp = lambda ss: {s.split(":")[1] for s in ss if ":" in s}
    if official(sx) != official(sy):
        return True
    return bool(grp(sx)) and bool(grp(sy)) and not (grp(sx) & grp(sy))


def check6(ctx, inter):
    res = {"cmd": [], "evt": []}
    names = collections.defaultdict(lambda: collections.defaultdict(set))   # agg -> name -> sources
    for g, d in ctx.groups.items():
        for c in d.get("commands") or []:
            names[c["aggregate"]][base_cmd(c["name"])].add(f"目录:{g}")
        for _k, u in units_of(d.get("proc")):
            for s in unit_steps(u):
                if s.get("read") is True or s.get("actor") == "platform" or s.get("platform_task") or not s.get("command"):
                    continue
                for a, c in [(s.get("aggregate"), s.get("command"))] + [(x.get("aggregate"), x.get("command")) for x in s.get("inner") or []]:
                    if isinstance(a, str) and c:
                        names[a][base_cmd(c)].add(f"骨架:{g}")
    for a, v in ctx.aggs.items():
        for c in v["commands"]:
            names[a][c].add("聚合稿")
    for a, nm in names.items():
        ks = sorted(nm)
        for x in range(len(ks)):
            for y in range(x + 1, len(ks)):
                if similar(ks[x], ks[y]) and divergent(nm[ks[x]], nm[ks[y]]):
                    res["cmd"].append((a, ctx.bc_of(a), ks[x], sorted(nm[ks[x]]), ks[y], sorted(nm[ks[y]])))
    evs = collections.defaultdict(lambda: collections.defaultdict(set))   # bc -> canon -> {(source, raw)}
    for a, v in ctx.aggs.items():
        for n, _s, _o in v["events"]:
            evs[v["bc"]][canon(n, ctx.terms)].add(("聚合稿", n))
    for g, d in ctx.groups.items():
        for c in d.get("commands") or []:
            for e in c.get("emits") or []:
                if ctx.bc_of(c["aggregate"]):
                    evs[ctx.bc_of(c["aggregate"])][canon(e, ctx.terms)].add((f"目录:{g}", str(e)))
        for e in d.get("events") or []:
            if ctx.bc_of(e.get("pub_agg")):
                evs[ctx.bc_of(e["pub_agg"])][canon(e["name"], ctx.terms)].add((f"事件表:{g}", e["name"]))
    for i in event_inters(inter):
        if i["other"] and i["other"] in ctx.groups and False:
            pass
        pub = i["local"] if i["dir"] == "out" else i["other"]
        if pub and i["src"] != "subscribe":
            for n in i["names"]:
                evs[pub][n].add((f"骨架:{i['group']}", i["raw"]))
    for bc, nm in evs.items():
        ks = sorted(nm)
        for x in range(len(ks)):
            for y in range(x + 1, len(ks)):
                if similar(ks[x], ks[y]) and divergent(nm[ks[x]], nm[ks[y]]):
                    res["evt"].append((bc, ks[x], sorted(nm[ks[x]]), ks[y], sorted(nm[ks[y]])))
    return res


def run_checks(ctx):
    inter = interactions(ctx)
    c1 = check1(ctx, inter)
    return {"inter": inter, "c1": c1, "c2": check2(ctx, inter), "c3": check3(ctx), "c4": check4(ctx, c1), "c5": check5(ctx, inter),
            "c6": check6(ctx, inter)}


# ---------------------------------------------------------------- build from repo
def build_ctx(local):
    text = DRAFT.read_text(encoding="utf-8")
    aggs, owner, deps, modules = parse_draft(text)
    edges, _unchecked = DEP.edges_from(owner, deps)
    built, _iso = DEP.built_edges(modules)
    groups, notes = {}, []
    for g in GROUPS:
        d = {"proc": read_yaml(g, f"processes.{g}.yaml", local)}
        cm = read_yaml(g, "commands.yaml", local)
        d["commands"] = (cm or {}).get("commands") or []
        sm = read_yaml(g, "state-machines.yaml", local)
        d["sm"] = (sm or {}).get("machines") or []
        ev = []
        for c in d["commands"]:
            for e, subs in (c.get("event_subscribers") or {}).items():
                ev.append({"name": e, "pub_agg": c["aggregate"], "subs": subs, "src": "commands.yaml event_subscribers"})
        fe = read_yaml(g, f"events.{g}.yaml", local) or {}
        for e in fe.get("events") or []:
            ev.append({"name": e["name"], "pub_agg": e.get("publisher"), "subs": [str(x) for x in e.get("subscribers") or []], "src": f"events.{g}.yaml"})
        pe = read_yaml(g, "events.yaml", local) or {}
        for part in (pe.get("by_part") or {}).values():
            for e in part.get("published") or []:
                ev.append({"name": e["name"], "pub_agg": e.get("published_by"),
                           "subs": [str(s.get("who")) for s in e.get("subscribers") or [] if isinstance(s, dict) and s.get("who")], "src": "events.yaml"})
        d["events"] = ev
        for k in ("proc", "commands", "sm"):
            if not d[k]:
                notes.append(f"{g}: 取不到 {k}")
        groups[g] = d
    ctx = Ctx(aggs, owner, edges, {(s, t) for _l, _r, s, t in built}, groups, load_terms(), modules)
    return ctx, notes


# ---------------------------------------------------------------- report
def lim(lines, n=MAX_LINES):
    return lines[:n] + ([f"- …其余 {len(lines) - n} 条略"] if len(lines) > n else [])


def section(title, items):
    """items: {pair: [lines]}; at most MAX_LINES per pair."""
    out = [f"#### {title}（{sum(len(v) for v in items.values())} 条）", ""]
    if not items:
        return out + ["无。", ""]
    for pair in sorted(items):
        out.append(f"**{pair}**（{len(items[pair])} 条）")
        out += lim(items[pair])
        out.append("")
    return out


def pairs(ctx, rows, key_group, key_bc):
    d = collections.defaultdict(list)
    for r in rows:
        d[f"{key_group(r)} → {ctx.group_of_bc(key_bc(r))}"].append(r)
    return d


def render(ctx, R, notes):
    c1, c2, c3, c4, c5, c6 = (R[k] for k in ("c1", "c2", "c3", "c4", "c5", "c6"))
    inter = R["inter"]
    G = ctx.group_of_bc

    def l1(i):
        s, t, way = i["edge"]
        return (f"- {i['group']} {i['prc']} {i['where']}（{i['local']}，{i['src']} {i['mode']}/{i['dir']}「{i['raw'][:40]}」）需要边 {s} → {t}（{way}）；"
                f"§4.1 边方式：{'、'.join(sorted(ctx.edges41.get((s, t), []))) or '无'}")

    def by_pair(rows, fmt, kb=lambda r: r["other"]):
        d = collections.defaultdict(list)
        for r in rows:
            d[f"{r['group']} → {G(kb(r))}"].append(fmt(r))
        return d

    miss = by_pair(c1["missing"], l1)
    rev = by_pair(c1["reversed"], l1)
    mode = by_pair(c1["mode"], l1)
    cbk = collections.defaultdict(list)
    for i in c1["callback"]:
        cbk[i["edge"][0]].append(i)
    cbk_lines = {f"BC {b}（边 {b} → platform.approval，§4.1 方式：{'、'.join(sorted(ctx.edges41.get((b, 'platform.approval'), []))) or '无'}）":
                 [f"- {i['group']} {i['prc']} {i['where']}「{i['raw']}」" for i in v] for b, v in sorted(cbk.items())}

    out2a = by_pair(c2["out_no_in"], lambda o: f"- {o['group']} {o['prc']} {o['where']}：{o['local']} 发出「{o['raw'][:40]}」给 {o['other']}，订阅方骨架没有同名 in")
    out2b = by_pair([x[0] for x in c2["out_renamed"]], lambda o: "")
    ren = collections.defaultdict(list)
    for o, i in c2["out_renamed"]:
        ren[f"{o['group']} → {i['group']}"].append(
            f"- 发布方 {o['group']} {o['prc']} {o['where']}（{o['local']}）原文「{o['raw'][:40]}」；订阅方 {i['group']} {i['prc']} {i['where']}（{i['local']}）原文「{i['raw'][:40]}」")
    unpub = by_pair(c2["in_unpublished"], lambda i: f"- {i['group']} {i['prc']} {i['where']}：{i['local']} 订阅 {i['other']} 的「{i['raw'][:40]}」，发布方目录与聚合稿事件列均无此名")
    inren = collections.defaultdict(list)
    for i, near in c2["in_renamed"]:
        inren[f"{i['group']} → {G(i['other'])}"].append(
            f"- 订阅方 {i['group']} {i['prc']} {i['where']}（{i['local']}）原文「{i['raw'][:40]}」；发布方 {i['other']} 登记的近似名「{near}」")
    decl = collections.defaultdict(list)
    for g, name, pub, sb, text, near in c2["declared"]:
        decl[f"{g} 事件表 → {G(sb)}"].append(f"- 「{name}」（发布 {pub}）声明订阅方 {sb}（{text}），该 BC 骨架无对应 in" + (f"；近似名「{near}」" if near else ""))

    n3 = {"no_catalog": c3["no_catalog"], "no_command": c3["no_command"], "not_registered": c3["not_registered"], "no_transition": c3["no_transition"]}
    d3 = {}
    for k, rows in n3.items():
        d3[k] = collections.defaultdict(list)
        for r in rows:
            og = r[4] if len(r) > 4 else G(ctx.bc_of(r[2]))
            d3[k][f"{r[0]} → {og}"].append(f"- {r[1]}：{r[2]}「{r[3]}」")

    ghost = collections.defaultdict(list)
    for og, who, a, cb, why in c3["ghost"]:
        ghost[f"{og} 目录 → {who.split()[0]}"].append(f"- {a}「{cb}」登记的调用方 {who}：{why}")
    cyc = c4["cycles"]
    new_edges = collections.defaultdict(list)
    for (s, t), i in c4["new"]:
        new_edges[f"{i['group']}"].append(f"- {s} → {t}（{i['edge'][2]}）：{i['group']} {i['prc']} {i['where']}「{i['raw'][:30]}」")

    o5 = collections.defaultdict(list)
    for a, pb, n, sb, near in c5["orphans"]:
        o5[f"{G(pb)} → {G(sb)}"].append(f"- {a}（{pb}）「{n}」→ 订阅方 {sb}，骨架无承接" + (f"；该 BC 有近似名「{near}」" if near else ""))

    o6c = collections.defaultdict(list)
    for a, bc, x, sx, y, sy in c6["cmd"]:
        o6c[G(bc)].append(f"- {a}（{bc}）「{x}」{'、'.join(sx)} ／「{y}」{'、'.join(sy)}")
    o6e = collections.defaultdict(list)
    for bc, x, sx, y, sy in c6["evt"]:
        o6e[G(bc)].append(f"- 发布 {bc}：「{x}」{'、'.join(f'{s}[{r[:20]}]' for s, r in sx)} ／「{y}」{'、'.join(f'{s}[{r[:20]}]' for s, r in sy)}")

    cnt = lambda d: sum(len(v) for v in d.values())
    n1 = len(c1["missing"]) + len(c1["reversed"]) + len(c1["mode"]) + len(c1["callback"])
    S = [
        "# 三组接缝检查报告（revenue / finance / people）", "",
        "本文件由 `closeout/seam_check.py` 生成（只读检查，不改任何输入）。口径与不确定处见文末「脚本口径说明」。", "",
        "## 一屏结论", "",
        f"骨架跨 BC 交互 {len(inter)} 条（其中 cross_bc {sum(1 for i in inter if i['src'] == 'cross_bc')}、事件触发器 {sum(1 for i in inter if i['src'] == 'trigger')}、"
        f"事件策略订阅 {sum(1 for i in inter if i['src'] == 'subscribe')}）；§4.1 BC 边 {len(ctx.edges41)} 条，已建边 {len(ctx.built)} 条。", "",
        "| 项 | 结论 | 条数 |", "|---|---|---|",
        f"| 1 跨 BC 调用 | 通过 {c1['ok']}；边缺失 {len(c1['missing'])}；方向反（上游调下游）{len(c1['reversed'])}；边在但方式不符 {len(c1['mode'])}；审批回调边缺「事件」{len(c1['callback'])} | {n1} |",
        f"| 2 事件对接 | out 无对应 in {len(c2['out_no_in'])}；out/in 名称不一致 {len(c2['out_renamed'])}；in 发布方未登记 {len(c2['in_unpublished'])}；"
        f"in 名称近似 {len(c2['in_renamed'])}；声明订阅无承接 {len(c2['declared'])} | "
        f"{len(c2['out_no_in']) + len(c2['out_renamed']) + len(c2['in_unpublished']) + len(c2['in_renamed']) + len(c2['declared'])} |",
        f"| 3 跨组命令 | 跨组调用 {len(c3['pairs'])} 个（组, 流程, 命令）；登记齐全 {c3['ok']}；聚合无组目录 {len(c3['no_catalog'])}；命令不在目录 {len(c3['no_command'])}；调用方未登记 {len(c3['not_registered'])}；"
        f"目录登记的调用方无步骤 {len(c3['ghost'])}；状态机无转换 {len(c3['no_transition'])}（目录无调用方字段 {c3['no_field']}、聚合无状态机 {c3['no_machine']} 另计，不计入）| "
        f"{len(c3['no_catalog']) + len(c3['no_command']) + len(c3['not_registered']) + len(c3['no_transition']) + len(c3['ghost'])} |",
        f"| 4 成环 | §4.1 自身环 {len(c4['base'])}；加骨架边后环 {len(cyc)}；骨架用到而 §4.1 没有的边 {len(c4['new'])} | {len(cyc)} |",
        f"| 5 孤儿事件 | 聚合稿有业务订阅方的事件 {c5['total']} 条，骨架无承接 {len(c5['orphans'])} | {len(c5['orphans'])} |",
        f"| 6 同名不同义候选 | 命令 {len(c6['cmd'])}；事件 {len(c6['evt'])} | {len(c6['cmd']) + len(c6['evt'])} |", "",
    ]
    for n in notes:
        S.append(f"> 注：{n}")
    S += ["", "## 1 跨 BC 调用（骨架 cross_bc 与 §4.1 边）", "", "分组键为「骨架所在组 → 对方 BC 的主归属组」。", ""]
    S += section("1a 方向反（上游调下游）", rev) + section("1b §4.1 没有这条边", miss) + section("1c 边在但方式不符（不含审批回调）", mode)
    S += section("1d 审批回调（platform.approval 事件 in）而 §4.1 该边未标「事件」，按 BC 汇总", cbk_lines)
    S += ["## 2 事件对接", ""]
    S += section("2a out 事件在订阅方骨架无对应 in", out2a) + section("2b out 与 in 名称不一致（两边原文）", ren)
    S += section("2c in 事件在发布方目录与聚合稿事件列都没有", unpub) + section("2d in 事件名称与发布方登记名近似（两边原文）", inren)
    S += section("2e 事件表声明的订阅方在其骨架无承接", decl)
    S += ["## 3 跨组命令", ""]
    S += section("3a 调用的聚合不在任何组命令目录", d3["no_catalog"]) + section("3b 命令不在所属组 commands.yaml", d3["no_command"])
    S += section("3c 调用方未在命令的 callers / also_used_by / used_by 登记", d3["not_registered"])
    S += section("3d 所属组状态机无该命令的转换", d3["no_transition"])
    S += section("3e 目录登记了跨组调用方（callers / also_used_by），该流程骨架没有对应步骤", ghost)
    S += ["## 4 成环", "", f"§4.1 自身（含已建边）环数：{len(c4['base'])}。加入骨架实际用到的边后环：" +
          ("无。" if not cyc else " | ".join(" ↔ ".join(c) for c in cyc)), ""]
    S += section("4a 骨架用到而 §4.1 与已建边都没有的边", new_edges)
    S += ["## 5 孤儿事件", ""] + section("5a 聚合稿标了业务订阅方、骨架无承接", o5)
    S += ["## 6 同名不同义候选", ""] + section("6a 同一聚合的命令名", o6c) + section("6b 同一发布 BC 的事件名", o6e)
    S += ["## 脚本口径说明", "",
          "- 边方式：api 调用 = 下游 → 上游；事件 = 订阅方 → 发布方。`direction: in` 的 api 视为对方调本 BC；对 `platform.*` 一律要求业务 BC → 平台（惯例），不检查平台调业务的反向。",
          "- 已建边（module-dependencies.txt）不带方式，命中即通过。「方向反」指所需边的反向路径在 §4.1 + 已建边里可达（被调方依赖调用方）。",
          "- 事件名按 terms.yaml 的领域事件中、英文名与别名归一后比较，并去掉〔缺〕与括号；近似判据：相互包含且长度差 ≤3，或 difflib 比值 ≥ 0.75。",
          "- 事件触发器、事件策略订阅（events / subscribes 字段）也当作 in 承接；触发器未写发布方者，只在 2 项发布方核对中跳过。",
          "- 流程的 owner_bc 为 `reporting` 的读模型（PRC-22、154、90 的事件订阅）是分析库订阅，不计入接缝。",
          "- 第 3 项只取步骤的 aggregate / command 与 inner 子项；`calls`（流程调用流程）不展开；跨组 = 聚合不在调用方所在组的 commands.yaml 里。",
          "- 「组」按命令目录与 owner_bc 的出现次数取 BC 的主归属组，只用于分组展示。", ""]
    return "\n".join(S)


# ---------------------------------------------------------------- self-test
def selftest():
    aggs = {
        "AGG-01": {"bc": "B-01", "name": "甲", "commands": ["建甲"], "events": [("甲已建", ["B-02"], False)]},
        "AGG-02": {"bc": "B-02", "name": "乙", "commands": ["建乙", "改乙"], "events": [("乙已建", ["B-03"], False)]},
        "AGG-03": {"bc": "B-03", "name": "丙", "commands": ["建丙"], "events": [("丙已建", [], True)]},
        "AGG-04": {"bc": "B-01", "name": "丁", "commands": ["建丁"], "events": [("丁已建", ["B-02"], False)]},
    }
    owner = {k: v["bc"] for k, v in aggs.items()}
    edges = {("B-02", "B-01"): {"事件"}, ("B-03", "B-02"): {"api"}}
    step = lambda **k: dict({"actor": "system", "where": "automatic"}, **k)
    cb = lambda mode, d, bc, name: {"mode": mode, "direction": d, "bc": bc, "name": name}
    groups = {
        "revenue": {  # B-02, B-01 skeleton
            "proc": {"processes": [
                {"id": "PRC-1", "owner_bc": "B-02", "steps": [
                    step(aggregate="AGG-02", command="建乙", cross_bc=[cb("api", "out", "B-01", "读甲")]),                     # 1 api downstream->upstream, edge only 事件: mode
                    step(aggregate="AGG-02", command="改乙", cross_bc=[cb("api", "out", "B-03", "读丙")]),                     # 1 reversed (B-03 -> B-02 exists)
                    step(aggregate="AGG-02", command="建乙", cross_bc=[cb("event", "in", "B-01", "甲已创建")]),                  # 2 renamed vs 甲已建
                    step(aggregate="AGG-02", command="建乙", cross_bc=[cb("event", "out", "B-03", "乙已建")]),                 # 2 out without in
                    step(aggregate="AGG-02", command="建乙", cross_bc=[cb("event", "in", "B-01", "丁已建"), cb("api", "out", "platform.approval", "发起")]),
                    step(aggregate="AGG-02", command="建乙", cross_bc=[cb("event", "in", "B-01", "戊已建")]),                   # 2 unpublished
                    step(aggregate="AGG-02", command="建乙", cross_bc=[cb("api", "out", "B-04", "读己")]),                     # 1 missing
                ]},
            ]},
            "commands": [{"name": "建乙", "aggregate": "AGG-02", "emits": ["乙已建"]}, {"name": "改乙", "aggregate": "AGG-02", "callers": ["finance:PRC-77"]}],   # 3 ghost caller
            "sm": [{"aggregate": "AGG-02", "transitions": [{"command": "建乙"}]}], "events": []},
        "finance": {
            "proc": {"processes": [{"id": "PRC-9", "owner_bc": "B-03", "steps": [
                step(aggregate="AGG-02", command="改乙"),                                                                       # 3: not registered, no transition
                step(aggregate="AGG-02", command="没有此令"),                                                                  # 3: no command
                step(aggregate="AGG-01", command="建甲")]}]},                                                                  # 3: no catalog
            "commands": [{"name": "建丙", "aggregate": "AGG-03", "callers": ["x"]}], "sm": [], "events": []},
        "people": {"proc": {"processes": []}, "commands": [{"name": "建乙新", "aggregate": "AGG-02"}], "sm": [], "events": []},   # 6 近似命令名
    }
    ctx = Ctx(aggs, owner, edges, set(), groups, {})
    R = run_checks(ctx)
    c1, c2, c3, c4, c5, c6 = (R[k] for k in ("c1", "c2", "c3", "c4", "c5", "c6"))
    ok = True

    def expect(label, cond):
        nonlocal ok
        print(("通过 " if cond else "失败 ") + label)
        ok = ok and cond
    expect("1 方式不符", any(i["raw"] == "读甲" for i in c1["mode"]))
    expect("1 方向反", any(i["raw"] == "读丙" for i in c1["reversed"]))
    expect("1 边缺失", any(i["raw"] == "读己" for i in c1["missing"]))
    expect("1 平台边需要业务 → 平台", any(e == ("B-02", "platform.approval") for e in c1["skeleton_edges"]))
    expect("2 out 无 in", any(o["raw"] == "乙已建" for o in c2["out_no_in"]))
    expect("2 名称近似", any(x[0]["raw"] == "甲已创建" and x[1] == "甲已建" for x in c2["in_renamed"]))
    expect("2 in 未登记", any(i["raw"] == "戊已建" for i in c2["in_unpublished"]))
    expect("3 聚合无目录", any(r[2] == "AGG-01" for r in c3["no_catalog"]))
    expect("3 命令不在目录", any(r[3] == "没有此令" for r in c3["no_command"]))
    expect("3 调用方未登记", any(r[3] == "改乙" for r in c3["not_registered"]))
    expect("3 目录登记的调用方无步骤", any(r[3] == "改乙" for r in c3["ghost"]))
    expect("4 骨架新边", any(e == ("B-02", "B-04") for e, _ in c4["new"]))
    expect("4 加边成环", bool(c4["cycles"]))
    expect("5 孤儿事件", any(o[2] == "乙已建" and o[3] == "B-03" for o in c5["orphans"]))
    expect("6 近似命令名", any({x[2], x[4]} == {"建乙", "建乙新"} for x in c6["cmd"]))
    # state machine gap: add a cross-group call of a command with a machine that lacks it
    groups["finance"]["proc"]["processes"][0]["steps"].append(step(aggregate="AGG-02", command="改乙", cross_bc=[]))
    expect("3 状态机无转换", any(r[3] == "改乙" for r in run_checks(Ctx(aggs, owner, edges, set(), groups, {}))["c3"]["no_transition"]))
    # a clean cycle-free case
    ctx2 = Ctx({"AGG-01": aggs["AGG-01"]}, {"AGG-01": "B-01"}, {}, set(), {"revenue": {"proc": {"processes": []}}}, {})
    expect("4 无骨架边时无环", not run_checks(ctx2)["c4"]["cycles"])
    expect("5 无订阅承接时报孤儿（B-02 订阅甲已建）", len(run_checks(ctx2)["c5"]["orphans"]) == 1)
    expect("报告可渲染", "一屏结论" in render(ctx, R, []))
    return 0 if ok else 1


def main():
    if "--self-test" in sys.argv:
        sys.exit(selftest())
    local = "--local" in sys.argv
    ctx, notes = build_ctx(local)
    R = run_checks(ctx)
    text = render(ctx, R, notes)
    head = text.split("## 1 跨 BC 调用")[0]
    print(head)
    if "--no-write" not in sys.argv:
        REPORT.write_text(text + "\n", encoding="utf-8")
        print(f"已写 {REPORT}")


if __name__ == "__main__":
    main()
