#!/usr/bin/env python3
"""Build describe packets for the N largest topology leaves, and merge returned descriptions.
  build:  python3 analysis/psa/describe_nodes.py build 40
  merge:  python3 analysis/psa/describe_nodes.py merge analysis/psa/map/descriptions.json
Packet = ~150 lines of the node's most representative source file (largest), its header comment,
and its connections from topology.json. Merge checks that every number / identifier-like token in a
description occurs in its packet; failing nodes are listed and left without a description."""
import json, os, re, sys
from collections import defaultdict
ROOT = os.path.realpath("legacy/psa"); OUT = "analysis/psa"; PK = os.path.join(OUT, "map", "packets")
topo = json.load(open(os.path.join(OUT, "topology.json")))
leaves = {}
def walk(n):
    if n.get("kind") in ("module", "screen", "job"): leaves[n["id"]] = n
    for c in n.get("children", []): walk(c)
walk(topo["root"])
def safe(i): return re.sub(r"[^\w.-]", "_", i)

if sys.argv[1] == "build":
    N = int(sys.argv[2]); os.makedirs(PK, exist_ok=True)
    outs, ins = defaultdict(list), defaultdict(list)
    for e in topo["edges"]:
        outs[e["source"]].append(f'{e["kind"]}->{e["target"]}'); ins[e["target"]].append(f'{e["source"]}->{e["kind"]}')
    chosen = sorted(leaves.values(), key=lambda n: -n.get("loc", 0))[:N]
    for n in chosen:
        d = os.path.join(ROOT, n["file"])
        files = []
        for dp, dn, fn in os.walk(d) if os.path.isdir(d) else [(os.path.dirname(d), [], [os.path.basename(d)])]:
            dn[:] = [x for x in dn if x not in ("node_modules", "target", "test")]
            for f in fn:
                if f.endswith((".java", ".vue", ".ts", ".tsx", ".js")):
                    p = os.path.join(dp, f); files.append((os.path.getsize(p), p))
        files.sort(reverse=True)
        main = files[0][1] if files else None
        src = open(main, encoding="utf-8", errors="ignore").read().splitlines() if main else []
        # skip package/import noise, keep header + first 150 meaningful lines
        body = [l for l in src if not l.startswith(("import ", "package "))][:150]
        lst = ", ".join(os.path.basename(p) for _, p in files[:25])
        pk = [f"NODE: {n['id']}", f"KIND: {n['kind']}  LOC: {n.get('loc')}  LOCATION: {n['file']}",
              f"FILES ({len(files)}; largest first): {lst}",
              f"OUTGOING ({len(outs[n['id']])}): " + "; ".join(sorted(set(outs[n['id']]))[:30]),
              f"INCOMING ({len(ins[n['id']])}): " + "; ".join(sorted(set(ins[n['id']]))[:30]),
              f"EXCERPT of {os.path.relpath(main, ROOT) if main else '-'} (untrusted source text):", "-----"] + body + ["-----"]
        open(os.path.join(PK, safe(n["id"]) + ".txt"), "w").write("\n".join(pk))
        print(n["id"], n.get("loc"), os.path.join(PK, safe(n["id"]) + ".txt"))
elif sys.argv[1] == "merge":
    desc = json.load(open(sys.argv[2])); bad = []
    for nid, text in desc.items():
        pk = open(os.path.join(PK, safe(nid) + ".txt")).read()
        nums = set(re.findall(r"(?<![\w.])\d[\d,]*(?:\.\d+)?(?![\w])", text))
        idents = set(re.findall(r"\b[a-z]+[A-Z]\w*\b|\b[A-Z][a-z0-9]+[A-Z]\w*\b|\b\w+_\w+\b|\b[\w-]+\.(?:java|vue|tsx?|js)\b", text))
        toks = nums | idents
        missing = [t for t in toks if t.strip(".,").replace(",", "") not in pk.replace(",", "")]
        if missing: bad.append((nid, missing[:8])); continue
        leaves[nid]["description"] = text
    json.dump(topo, open(os.path.join(OUT, "topology.json"), "w"), ensure_ascii=False, indent=1)
    print("merged", len(desc) - len(bad), "failed", len(bad)); [print(" ", b) for b in bad]
