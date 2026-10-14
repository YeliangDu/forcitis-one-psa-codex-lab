#!/usr/bin/env python3
"""Extract a dependency/topology map of legacy/psa into topology.json.

Run from the workspace root:  python3 analysis/psa/extract_topology.py
Big estate -> mapped in chunks (backend, pc, mini) written to analysis/psa/map/<chunk>.json,
then merged.  Delete a chunk file to force it to rerun.

Node granularity
  backend : <module>:<layer>/<sub>   e.g. fst:service/manhour, fst:domain/expense, system:service/permission
  pc      : pc:views/<top>/<sub>, pc:api/<top>
  mini    : h5:pages/<a>/<b>, h5:api/<a>
  data    : ds:<table>
Edges
  call     : Java import from one node into another; PC/H5 view -> api module import
  dispatch : front-end api url -> backend controller (via gateway /admin-api|/app-api/<svc>/**), Feign
  read/write : node uses a MyBatis mapper / @Select SQL / DO of a table
"""
import json, os, re, sys
from collections import defaultdict

ROOT = os.path.realpath("legacy/psa")
OUT = "analysis/psa"
MAPDIR = os.path.join(OUT, "map")
os.makedirs(MAPDIR, exist_ok=True)
SKIP = {"node_modules", "dist", "target", ".git", "test", "test-integration"}

# business-domain grouping (from ASSESSMENT.md domain table); key = sub-package / folder name
DOMAIN_OF = {
    # project delivery & planning
    "project": "Project & delivery", "projecttask": "Project & delivery", "plan": "Project & delivery",
    "delivery": "Project & delivery", "wip": "Project & delivery", "projectcost": "Project & delivery",
    "calendar": "Project & delivery", "task": "Project & delivery",
    # time & resources
    "manhour": "Timesheet & resources", "resource": "Timesheet & resources", "user": "Timesheet & resources",
    "operations": "Timesheet & resources",
    # presales / CRM / contract
    "presales": "Presales, CRM & contract", "customer": "Presales, CRM & contract", "contract": "Presales, CRM & contract",
    "opportunity": "Presales, CRM & contract", "contacts": "Presales, CRM & contract", "survey": "Presales, CRM & contract",
    # cost, budget, accounting, receivables
    "accounting": "Finance: cost, accounting & AR", "cost": "Finance: cost, accounting & AR",
    "costsummaryext": "Finance: cost, accounting & AR", "budget": "Finance: cost, accounting & AR",
    "biget": "Finance: cost, accounting & AR", "receivables": "Finance: cost, accounting & AR",
    "procurement": "Finance: cost, accounting & AR", "costopenning": "Finance: cost, accounting & AR",
    "a8": "Finance: cost, accounting & AR",
    # expense & travel
    "expense": "Expense & travel", "travel": "Expense & travel",
    # approval / workflow
    "bpm": "Approval & workflow (BPM)", "workflowform": "Approval & workflow (BPM)",
    "workflowhook": "Approval & workflow (BPM)", "process": "Approval & workflow (BPM)",
    "approvalDemo": "Approval & workflow (BPM)", "message": "Approval & workflow (BPM)",
    # org / identity / platform
    "organization": "Identity, org & platform", "dept": "Identity, org & platform", "platform": "Identity, org & platform",
    "system": "Identity, org & platform", "sync": "Identity, org & platform", "km": "Identity, org & platform",
    "infra": "Infrastructure & framework", "framework": "Infrastructure & framework",
    "dashboard": "Reporting & dashboards", "visualization": "Reporting & dashboards",
    "visualized": "Reporting & dashboards", "weeklyMeetingStatistics": "Reporting & dashboards",
    # second pass: legacy fst sub-packages and PC/H5 folders
    "pmproject": "Project & delivery", "projectplan": "Project & delivery", "ohproject": "Project & delivery",
    "planning": "Project & delivery", "pmList": "Project & delivery", "lists": "Project & delivery",
    "deliverable": "Project & delivery", "forecast": "Project & delivery", "progress": "Project & delivery",
    "myTaskLists": "Project & delivery", "taskLists": "Project & delivery", "plan_modules": "Project & delivery",
    "weeklyMeeting": "Project & delivery", "sends": "Project & delivery", "costTemplate": "Project & delivery",
    "quotation": "Presales, CRM & contract", "crm": "Presales, CRM & contract", "price": "Presales, CRM & contract",
    "revenue": "Finance: cost, accounting & AR", "receive": "Finance: cost, accounting & AR",
    "voucher": "Finance: cost, accounting & AR", "u8c": "Finance: cost, accounting & AR",
    "mdm": "Finance: cost, accounting & AR", "material": "Finance: cost, accounting & AR",
    "timesheet": "Timesheet & resources", "skillTag": "Timesheet & resources",
    "permission": "Identity, org & platform", "oauth2": "Identity, org & platform", "wecom": "Identity, org & platform",
    "wx": "Identity, org & platform", "qyLogin": "Identity, org & platform", "login": "Identity, org & platform",
    "register": "Identity, org & platform", "sso": "Identity, org & platform", "socialLogin": "Identity, org & platform",
    "mine": "Identity, org & platform",
}
FRONTEND_SHELL = "Front-end shell (PC & H5)"
MODULE_DEFAULT_DOMAIN = {"system": "Identity, org & platform", "infra": "Infrastructure & framework",
                         "bpm": "Approval & workflow (BPM)", "gateway": "Infrastructure & framework",
                         "framework": "Infrastructure & framework"}


def domain_for(mod, sub):
    if sub in DOMAIN_OF:
        return DOMAIN_OF[sub]
    return MODULE_DEFAULT_DOMAIN.get(mod, "Shared / cross-cutting")


def walk(base, exts):
    for dp, dns, fns in os.walk(base):
        dns[:] = [d for d in dns if d not in SKIP]
        for f in fns:
            if f.endswith(exts):
                yield os.path.join(dp, f)


def rel(p):
    return os.path.relpath(p, ROOT)


def loc(text):
    return sum(1 for l in text.splitlines() if l.strip() and not l.strip().startswith(("//", "*", "/*")))


# ---------------------------------------------------------------- backend
PKG_RE = re.compile(r"^package\s+([\w.]+);", re.M)
IMP_RE = re.compile(r"^import\s+(?:static\s+)?([\w.]+)\s*;", re.M)
CLS_RE = re.compile(r"\b(?:class|interface|enum|record)\s+(\w+)")
TABLE_RE = re.compile(r'@TableName\(\s*(?:value\s*=\s*)?"([^"]+)"')
MAPPER_RE = re.compile(r"interface\s+(\w+)\s+extends\s+\w*Mapper\w*<\s*(\w+)")
REQMAP_RE = re.compile(r'@RequestMapping\(\s*(?:value\s*=\s*|path\s*=\s*)?\{?\s*"([^"]*)"')
FEIGN_RE = re.compile(r'@FeignClient\(\s*(?:name\s*=\s*|value\s*=\s*)?([\w.]+|"[^"]+")')
SQL_TBL_RE = re.compile(r"\b(?:FROM|JOIN|UPDATE|INTO)\s+\[?(?:dbo\]?\.)?\[?([a-zA-Z_][\w]*)\]?", re.I)
WRITE_CALL_RE = re.compile(r"\.\s*(insert\w*|update\w*|delete\w*|save\w*|remove\w*)\s*\(")
SQL_ANN_RE = re.compile(r'@(Select|Update|Insert|Delete)\(\s*(?:\{)?([\s\S]*?)\)\s*\n', re.M)


def backend_node(pkg):
    """cn.iocoder.yudao.module.<mod>.<layer>.<sub>... -> (mod, nodeKey)"""
    p = pkg.split(".")
    if p[:4] == ["cn", "iocoder", "yudao", "module"] and len(p) > 4:
        mod = p[4]
        rest = p[5:]
        if not rest:
            return mod, f"{mod}:root"
        layer = rest[0]
        if layer in ("controller", "dal") and len(rest) > 2:
            return mod, f"{mod}:{layer}/{rest[1]}/{rest[2]}"
        if len(rest) > 1:
            return mod, f"{mod}:{layer}/{rest[1]}"
        return mod, f"{mod}:{layer}"
    if p[:3] == ["cn", "iocoder", "yudao"] and len(p) > 3:
        if p[3] == "framework" and len(p) > 4:
            return "framework", f"framework:{p[4]}"
        if p[3] == "gateway":
            return "gateway", "gateway:core"
    return None, None


def node_sub(nid):
    tail = nid.split(":", 1)[1].split("/")
    return tail[-1] if tail[0] not in ("controller", "dal") else tail[-1]


def chunk_backend():
    nodes, files_of = {}, defaultdict(list)
    cls_node = {}
    infos = []
    for f in walk(os.path.join(ROOT, "psa-backend"), (".java",)):
        if "/src/main/java/" not in f:
            continue
        t = open(f, encoding="utf-8", errors="ignore").read()
        m = PKG_RE.search(t)
        if not m:
            continue
        pkg = m.group(1)
        mod, nid = backend_node(pkg)
        if not nid:
            continue
        cm = CLS_RE.search(t)
        cname = cm.group(1) if cm else os.path.basename(f)[:-5]
        cls_node[f"{pkg}.{cname}"] = nid
        n = nodes.setdefault(nid, {"id": nid, "name": nid, "kind": "module", "language": "java", "loc": 0,
                                   "file": rel(os.path.dirname(f)), "_mod": mod, "_entry": set()})
        n["loc"] += loc(t)
        # node's location = common directory of its files
        d = rel(os.path.dirname(f))
        n["file"] = os.path.commonpath([n["file"], d]) if n["file"] else d
        infos.append((f, t, pkg, cname, nid))

    tables, mapper_tbl, do_tbl = set(), {}, {}
    for f, t, pkg, cname, nid in infos:
        tm = TABLE_RE.search(t)
        if tm:
            do_tbl[f"{pkg}.{cname}"] = tm.group(1).lower()
    for f, t, pkg, cname, nid in infos:
        mm = MAPPER_RE.search(t)
        if mm:
            do_simple = mm.group(2)
            for imp in IMP_RE.findall(t):
                if imp.endswith("." + do_simple) and imp in do_tbl:
                    mapper_tbl[f"{pkg}.{cname}"] = do_tbl[imp]
            if f"{pkg}.{cname}" not in mapper_tbl and f"{pkg}.{do_simple}" in do_tbl:
                mapper_tbl[f"{pkg}.{cname}"] = do_tbl[f"{pkg}.{do_simple}"]

    edges = set()
    controllers = []  # (prefix, nid)
    feign = []
    for f, t, pkg, cname, nid in infos:
        imps = IMP_RE.findall(t)
        for imp in imps:
            tgt = cls_node.get(imp)
            if tgt and tgt != nid:
                edges.add((nid, tgt, "call"))
            if imp in mapper_tbl:
                tb = mapper_tbl[imp]
                tables.add(tb)
                edges.add((nid, "ds:" + tb, "read"))
                if WRITE_CALL_RE.search(t):
                    edges.add((nid, "ds:" + tb, "write"))
        if f"{pkg}.{cname}" in mapper_tbl:
            tb = mapper_tbl[f"{pkg}.{cname}"]
            tables.add(tb)
            edges.add((nid, "ds:" + tb, "read"))
        for kind, body in SQL_ANN_RE.findall(t):
            for tb in SQL_TBL_RE.findall(body):
                tb = tb.lower()
                if tb in ("select", "dual", "t", "a", "b") or len(tb) < 4:
                    continue
                tables.add(tb)
                edges.add((nid, "ds:" + tb, "read" if kind == "Select" else "write"))
        if "@RestController" in t or "@Controller" in t:
            nodes[nid]["_entry"].add("http")
            rm = REQMAP_RE.search(t)
            if rm:
                controllers.append((rm.group(1).strip("/"), nid))
        if "@XxlJob" in t or "JobHandler" in cname:
            nodes[nid]["_entry"].add("job")
        if re.search(r"(RocketMQListener|StreamMessageListener|@RocketMQMessageListener|MessageListener<)", t):
            nodes[nid]["_entry"].add("mq")
        fm = FEIGN_RE.search(t)
        if fm:
            feign.append((fm.group(1).strip('"'), nid))
    # Feign client -> the module that serves it (name constants like ApiConstants.NAME live in each *-api module)
    for name, nid in feign:
        svc = name.split(".")[0].lower().replace("apiconstants", "")
        owner_mod = nodes[nid]["_mod"]
        # the RPC api package sits in the serving module itself; make a dispatch to its controller/admin roots lazily
        edges.add((nid, f"{owner_mod}:rpc-server", "dispatch")) if False else None
    for tb in sorted(tables):
        nodes["ds:" + tb] = {"id": "ds:" + tb, "name": tb, "kind": "datastore"}
    return {"nodes": nodes, "edges": sorted(edges), "controllers": controllers, "do_tables": len(do_tbl)}


# ---------------------------------------------------------------- front ends
URL_RE = re.compile(r"""url\s*:\s*[`'"]\/?([\w\-/]+)""")
URL2_RE = re.compile(r"""request\.(?:get|post|put|delete)\w*\(\s*[`'"]\/?([\w\-/]+)""")
IMPORT_JS_RE = re.compile(r"""(?:import[\s\S]*?from\s*|require\()\s*['"]([@./][^'"]+)['"]""")


def chunk_frontend(name, base, view_dir, api_dir, ui_lang):
    nodes, edges, urls = {}, set(), []
    base = os.path.join(ROOT, base)

    def nid_for(path):
        r = os.path.relpath(path, base)
        parts = r.split(os.sep)
        if parts[0] == "src" and len(parts) > 2 and parts[1] == api_dir:
            top = parts[2].split(".")[0]
            return f"{name}:api/{top}", os.path.join(base, "src", api_dir, parts[2])
        if parts[0] == "src" and len(parts) > 2 and parts[1] == view_dir:
            sub = parts[2:4] if len(parts) > 4 else parts[2:3]
            sub = [s.split(".")[0] for s in sub]
            return f"{name}:{view_dir}/" + "/".join(sub), os.path.join(base, "src", view_dir, *parts[2:2 + len(sub)])
        if parts[0] == "src" and len(parts) > 1:
            return f"{name}:{parts[1].split('.')[0]}", os.path.join(base, "src", parts[1])
        return None, None

    for f in walk(os.path.join(base, "src"), (".vue", ".js", ".ts", ".tsx", ".jsx")):
        if "/generated/" in f:
            continue
        nid, d = nid_for(f)
        if not nid:
            continue
        t = open(f, encoding="utf-8", errors="ignore").read()
        n = nodes.setdefault(nid, {"id": nid, "name": nid, "kind": "screen" if f"/{view_dir}/" in f else "module",
                                   "language": ui_lang, "loc": 0, "file": rel(d)})
        n["loc"] += loc(t)
        for imp in IMPORT_JS_RE.findall(t):
            if imp.startswith("@/"):
                tgt_path = os.path.join(base, "src", imp[2:])
            elif imp.startswith("."):
                tgt_path = os.path.normpath(os.path.join(os.path.dirname(f), imp))
            else:
                continue
            tnid, _ = nid_for(tgt_path)
            if tnid and tnid != nid and (tnid.startswith(f"{name}:api/") or tnid.startswith(f"{name}:{view_dir}/")):
                edges.add((nid, tnid, "call"))
        for u in URL_RE.findall(t) + URL2_RE.findall(t):
            urls.append((nid, u.strip("/")))
    return {"nodes": nodes, "edges": sorted(edges), "urls": urls}


# ---------------------------------------------------------------- run chunks
def run_chunk(key, fn):
    p = os.path.join(MAPDIR, key + ".json")
    if os.path.exists(p):
        print(f"chunk {key}: cached")
        return json.load(open(p))
    data = fn()
    for n in data["nodes"].values():
        if "_entry" in n:
            n["_entry"] = sorted(n["_entry"])
    json.dump(data, open(p, "w"), ensure_ascii=False, indent=1)
    print(f"chunk {key}: {len(data['nodes'])} nodes, {len(data['edges'])} edges")
    return data


be = run_chunk("backend", chunk_backend)
pc = run_chunk("pc", lambda: chunk_frontend("pc", "psa-front-desk-pc", "views", "api", "vue"))
h5 = run_chunk("mini", lambda: chunk_frontend("h5", "psa-mini", "pages", "api", "tsx"))

# ---------------------------------------------------------------- merge
nodes = {}
for c in (be, pc, h5):
    nodes.update(c["nodes"])
edges = {tuple(e) for c in (be, pc, h5) for e in c["edges"]}
observations = []

# front-end url -> controller (gateway strips /admin-api|/app-api; controller prefixes look like /fst/project)
ctrl = sorted(be["controllers"], key=lambda x: -len(x[0]))
unresolved = 0
for c in (pc, h5):
    for nid, u in c["urls"]:
        u2 = re.sub(r"^(admin-api|app-api)/", "", u)
        hit = next((cn for pre, cn in ctrl if pre and (u2 == pre or u2.startswith(pre + "/"))), None)
        if not hit:
            # controllers mapped without module prefix: /admin-api/<mod>/<path> -> "<path>"
            u3 = u2.split("/", 1)[1] if "/" in u2 else u2
            hit = next((cn for pre, cn in ctrl if pre and (u3 == pre or u3.startswith(pre + "/"))), None)
        if hit:
            edges.add((nid, hit, "dispatch"))
        else:
            unresolved += 1

# domains
tree = defaultdict(list)
for nid, n in nodes.items():
    if n.get("kind") == "datastore":
        tree["Data stores"].append(nid)
        continue
    mod, tail = nid.split(":", 1)
    parts = tail.split("/")
    sub = parts[-1] if parts[0] in ("controller", "dal", "views", "pages", "api") else (parts[1] if len(parts) > 1 else parts[0])
    if parts[0] in ("views", "pages", "api"):
        sub = parts[1] if len(parts) > 1 else parts[0]
        if sub == "work" and len(parts) > 2:
            sub = parts[2]
        if sub == "project" and mod in ("pc",) and len(parts) > 2:
            sub = parts[2]
    dmod = {"pc": "fst", "h5": "fst"}.get(mod, mod)
    dom = domain_for(dmod, sub)
    if mod in ("pc", "h5") and dom == "Shared / cross-cutting":
        dom = FRONTEND_SHELL
    tree[dom].append(nid)

entry = sorted(nid for nid, n in nodes.items() if n.get("_entry") or n.get("kind") == "screen")
inbound = defaultdict(int)
for s, t, k in edges:
    inbound[t] += 1
dynamic_targets = sum(1 for nid, n in nodes.items() if n.get("kind") == "module" and nid.split(":")[1].startswith(("api", "framework", "service/workflowform")))
dead = sorted(nid for nid, n in nodes.items()
              if n.get("kind") == "module" and not inbound[nid] and nid not in entry
              and re.search(r":(service|application|controller/admin)/", nid)
              and n.get("language") == "java")

clean_nodes = []
for dom, ids in sorted(tree.items()):
    kids = []
    for i in sorted(ids):
        n = {k: v for k, v in nodes[i].items() if not k.startswith("_")}
        kids.append(n)
    clean_nodes.append({"id": "dom:" + re.sub(r"\W+", "-", dom.lower()).strip("-"), "name": dom, "kind": "domain", "children": kids})

ids = set(nodes)
edge_list = [{"source": s, "target": t, "kind": k} for s, t, k in sorted(edges) if s in ids and t in ids]

# keep previously merged descriptions / flows / observations (written by the describe step)
prev = {}
if os.path.exists(os.path.join(OUT, "topology.json")):
    prev = json.load(open(os.path.join(OUT, "topology.json")))
prev_desc = {}
def collect(n):
    if n.get("description"):
        prev_desc[n["id"]] = n["description"]
    for c in n.get("children", []):
        collect(c)
if prev:
    collect(prev["root"])
for d in clean_nodes:
    for c in d["children"]:
        if c["id"] in prev_desc:
            c["description"] = prev_desc[c["id"]]

topo = {
    "system": "老系统（FORCITIS Professional Services Automation）",
    "root": {"id": "sys", "name": "老系统", "kind": "system", "children": clean_nodes},
    "edges": edge_list,
    "entryPoints": entry,
    "deadEnds": dead,
    "observations": prev.get("observations", []),
    "flows": prev.get("flows", []),
}
json.dump(topo, open(os.path.join(OUT, "topology.json"), "w"), ensure_ascii=False, indent=1)

# ---------------------------------------------------------------- summary
from collections import Counter
print(f"\nnodes: {len(nodes)}  edges: {len(edge_list)}  entry points: {len(entry)}  dead-end candidates: {len(dead)}")
print("edge kinds:", dict(Counter(e['kind'] for e in edge_list)))
print(f"DO classes with @TableName: {be['do_tables']}; datastores referenced: {sum(1 for n in nodes.values() if n.get('kind')=='datastore')}")
print(f"front-end urls: {len(pc['urls'])+len(h5['urls'])}, unresolved to a controller: {unresolved}")
print("\nper domain (nodes / loc):")
for d in clean_nodes:
    print(f"  {d['name']:<40} {len(d['children']):>4}  {sum(c.get('loc',0) for c in d['children']):>8}")
fan_in = Counter(e["target"] for e in edge_list if e["kind"] == "call")
fan_out = Counter(e["source"] for e in edge_list if e["kind"] == "call")
print("\ntop fan-in (call):", fan_in.most_common(12))
print("\ntop fan-out (call):", fan_out.most_common(12))
writers = Counter(e["target"] for e in edge_list if e["kind"] == "write")
print("\ntables with most writer nodes:", writers.most_common(12))
print("\ndead-end candidates:", dead[:40])
