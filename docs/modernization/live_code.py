#!/usr/bin/env python3
"""Estimate the LIVE backend code: classes reachable from real entry points.
Entry points = controller methods whose URL is called by PC/H5 front ends, @XxlJob handlers,
MQ consumers, RPC (Feign) api impls that are themselves reached, Spring framework config.
Reachability = class-level references (imports + same-package simple-name use), transitive.
Run from workspace root. Writes analysis/psa/live_code.json."""
import os, re, json
from collections import defaultdict
ROOT = os.path.realpath("legacy/psa")
SKIP = {"node_modules", "dist", "target", ".git"}
def walk(b, ext):
    for dp, dn, fn in os.walk(b):
        dn[:] = [d for d in dn if d not in SKIP]
        for f in fn:
            if f.endswith(ext): yield os.path.join(dp, f)
def code_loc(t):
    return sum(1 for l in t.splitlines() if l.strip() and not l.strip().startswith(("//", "*", "/*", "import ", "package ")))

cls = {}  # fqcn -> dict
for f in walk(os.path.join(ROOT, "psa-backend"), ".java"):
    if "/src/main/java/" not in f: continue
    t = open(f, encoding="utf-8", errors="ignore").read()
    m = re.search(r"^package\s+([\w.]+);", t, re.M)
    if not m: continue
    name = os.path.basename(f)[:-5]
    fq = m.group(1) + "." + name
    cls[fq] = {"pkg": m.group(1), "name": name, "file": os.path.relpath(f, ROOT), "t": t, "loc": code_loc(t)}
by_pkg = defaultdict(dict)
for fq, c in cls.items(): by_pkg[c["pkg"]][c["name"]] = fq
WORD = re.compile(r"\b[A-Z]\w+\b")
def refs(c):
    out = set()
    imps = re.findall(r"^import\s+(?:static\s+)?([\w.]+?)(?:\.\*)?\s*;", c["t"], re.M)
    for i in imps:
        if i in cls: out.add(i)
        else:  # static import of member or nested -> owner class
            p = i.rsplit(".", 1)[0]
            if p in cls: out.add(p)
        if c["t"].find(i + ".*") >= 0 or re.search(r"import\s+" + re.escape(i) + r"\.\*", c["t"]):
            for n, fq in by_pkg.get(i, {}).items():
                if re.search(r"\b" + n + r"\b", c["t"]): out.add(fq)
    words = set(WORD.findall(c["t"]))
    for n, fq in by_pkg[c["pkg"]].items():
        if n in words and fq != c["pkg"] + "." + c["name"]: out.add(fq)
    return out
graph = {fq: refs(c) for fq, c in cls.items()}
# reverse: an interface reached => its implementations reached (Spring DI)
impls = defaultdict(set)
for fq, c in cls.items():
    m = re.search(r"\bclass\s+\w+(?:<[^{]*?>)?\s*(?:extends\s+[\w.<>, ?]+?)?\s*implements\s+([^{]+)\{", c["t"])
    if m:
        for i in re.findall(r"\b([A-Z]\w+)", m.group(1)):
            for t in graph[fq]:
                if t.endswith("." + i): impls[t].add(fq)
            if i in by_pkg[c["pkg"]]: impls[by_pkg[c["pkg"]][i]].add(fq)
    m2 = re.search(r"\bclass\s+\w+(?:<[^{]*?>)?\s+extends\s+([A-Z]\w+)", c["t"])
    if m2:
        for t in graph[fq]:
            if t.endswith("." + m2.group(1)): impls[t].add(fq)

# ---- front-end URLs (or, with --prod, URLs seen in the production access log)
import sys
PROD = "--prod" in sys.argv
urls = set()
if PROD:
    for line in open("analysis/psa/runtime/prod_usage.tsv", encoding="utf-8"):
        f = line.rstrip("\n").split("\t")
        if len(f) == 10 and f[2].startswith("/"):
            u = re.sub(r"^/(admin-api|app-api)/", "", f[2].split("?")[0]).strip("/")
            urls.add(u)
for sub in (() if PROD else ("psa-front-desk-pc/src", "psa-mini/src")):
    for ext in (".js", ".ts", ".tsx", ".vue", ".jsx"):
        for f in walk(os.path.join(ROOT, sub), ext):
            t = open(f, encoding="utf-8", errors="ignore").read()
            for u in re.findall(r"""(?:url\s*:\s*|request\.\w+\(\s*|axios\.\w+\(\s*|href\s*=\s*)[`'"]\/?((?:admin-api\/|app-api\/)?[\w\-]+\/[\w\-/${}.]+)""", t):
                u = re.sub(r"^(admin-api|app-api)/", "", u)
                u = re.sub(r"\$\{[^}]*\}.*", "", u).strip("/")
                urls.add(u)
# ---- controllers & endpoints
ep_total, ep_used, entries = 0, 0, set()
used_eps, unused_eps = [], []
per_ctrl = {}
MAP = re.compile(r'@(Get|Post|Put|Delete|Request|Patch)Mapping\(\s*(?:value\s*=\s*|path\s*=\s*)?\{?\s*"([^"]*)"')
for fq, c in cls.items():
    t = c["t"]
    if "@RestController" not in t and "@Controller" not in t:
        continue
    head = t.split("class " + c["name"], 1)
    pm = MAP.search(head[0]) if len(head) > 1 else None
    prefix = pm.group(2).strip("/") if pm else ""
    body = head[1] if len(head) > 1 else t
    mod = re.search(r"yudao\.module\.(\w+)", c["pkg"])
    mod = mod.group(1) if mod else ""
    hit_any = False
    per_ctrl.setdefault(c["file"], [0, 0, c["loc"]])
    for kind, path in MAP.findall(body):
        ep_total += 1
        full = "/".join(x for x in (prefix, path.strip("/")) if x)
        full_nomod = full
        cands = {full, f"{mod}/{full}"}
        # path variables -> prefix match
        rx = [re.compile("^" + re.sub(r"\\\{[^}]+\\\}", "[^/]+", re.escape(x)) + "$") for x in cands]
        hit = any(r.match(u) for r in rx for u in urls) if PROD else \
              any(u == x or u.startswith(x + "/") for x in cands for u in urls) or \
              any(r.match(u) for r in rx for u in urls)
        per_ctrl[c["file"]][0] += 1
        if hit:
            per_ctrl[c["file"]][1] += 1
            ep_used += 1; hit_any = True; used_eps.append(full)
        else:
            unused_eps.append(f"{c['file']}: {full}")
    if hit_any: entries.add(fq)
# jobs, mq, config, framework autoconfig, application main
for fq, c in cls.items():
    t = c["t"]
    if re.search(r"@XxlJob|@Scheduled|implements\s+(?:\w+\.)?(?:JobHandler|RocketMQListener)|@RocketMQMessageListener|StreamMessageListener|@EventListener|@TransactionalEventListener|ApplicationListener<|@SpringBootApplication|@Configuration|@AutoConfiguration|@Aspect|implements\s+(?:Filter|HandlerInterceptor|.*BeanPostProcessor)|extends\s+\w*(Filter|Listener|Handler)\b", t):
        entries.add(fq)
# reach
seen, stack = set(), list(entries)
while stack:
    x = stack.pop()
    if x in seen: continue
    seen.add(x)
    stack.extend(graph.get(x, ())); stack.extend(impls.get(x, ()))
def area(c):
    m = re.search(r"yudao\.(module\.\w+|framework\.\w+|gateway)", c["pkg"])
    return m.group(1) if m else "other"
tot, live = defaultdict(int), defaultdict(int)
for fq, c in cls.items():
    a = area(c); tot[a] += c["loc"]
    if fq in seen: live[a] += c["loc"]
res = {"endpoints_total": ep_total, "endpoints_called_by_frontend": ep_used, "frontend_urls": len(urls),
       "classes_total": len(cls), "classes_live": len(seen),
       "loc_total": sum(tot.values()), "loc_live": sum(live.values()),
       "by_area": {a: {"loc": tot[a], "live": live[a]} for a in sorted(tot, key=lambda a: -tot[a])},
       "largest_dead_classes": sorted(((c["loc"], c["file"]) for fq, c in cls.items() if fq not in seen), reverse=True)[:40],
       "unused_endpoint_sample": unused_eps[:60],
       "per_controller": per_ctrl, "unused_endpoints": unused_eps}
json.dump(res, open("analysis/psa/live_code%s.json" % ("_prod" if PROD else ""), "w"), ensure_ascii=False, indent=1)
print(json.dumps({k: v for k, v in res.items() if k not in ("largest_dead_classes", "unused_endpoint_sample")}, indent=1))
print("largest unreached classes:"); [print(" ", l, f) for l, f in res["largest_dead_classes"][:25]]
