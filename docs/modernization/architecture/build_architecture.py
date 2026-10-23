#!/usr/bin/env python3
"""Generate the architecture drawings from the architecture SSOT (architecture/DESIGN.md §3).

  python3 architecture/build_architecture.py          regenerate generated/*.mmd (deterministic; build_all.py runs this)
  python3 architecture/build_architecture.py --svg    also render generated/*.svg and generated/index.html with the pinned
                                                      mermaid-cli in ~/.modernize/psa/tools/mermaid-cli (local only, SUPPLY_CHAIN.md)
  python3 architecture/build_architecture.py --check  fail if any reference is broken, a .mmd is stale, or an .svg was rendered from an older .mmd
Run from docs/modernization.
"""
import hashlib, html, os, re, subprocess, sys
import yaml

HERE = os.path.dirname(os.path.abspath(__file__))
GEN = os.path.join(HERE, "generated")
MMDC = os.path.expanduser("~/.modernize/psa/tools/mermaid-cli/node_modules/.bin/mmdc")
REL_LABEL = {None: "依赖", "conformist": "遵奉者", "anticorruption-layer": "防腐层",
             "open-host-service": "开放主机", "published-language": "发布语言", "shared-kernel": "共享内核"}


def load(rel):
    with open(os.path.join(HERE, rel), encoding="utf-8") as f:
        return yaml.safe_load(f)


REL_STATUS = ("built", "planned")   # planned = 本阶段在建（ADR-0041 决定 4）；规划边不登记


def relation_problems(rels, cids):
    problems = []
    for r in rels:
        rid = r.get("id")
        for end in ("upstream", "downstream"):
            if r.get(end) not in cids:
                problems.append(f"context-map.yaml {rid}: unknown {end} {r.get(end)}")
        if r.get("type") not in REL_LABEL or ("type" in r and r["type"] is None):
            problems.append(f"context-map.yaml {rid}: type must be one of {sorted(k for k in REL_LABEL if k)} or omitted, got {r.get('type')!r}")
        if not str(r.get("via") or "").strip():
            problems.append(f"context-map.yaml {rid}: via is required")
        if r.get("status") not in REL_STATUS:
            problems.append(f"context-map.yaml {rid}: status must be one of {REL_STATUS}, got {r.get('status')!r}")
        if rid != f"{r.get('upstream')}>{r.get('downstream')}":
            problems.append(f"context-map.yaml {rid}: id must be <upstream>><downstream>")
    return problems


def module_dependencies():
    """backend/architecture/module-dependencies.txt as a set of (upstream, downstream) edges."""
    edges = set()
    with open(os.path.join(HERE, "..", "..", "..", "backend", "architecture", "module-dependencies.txt"), encoding="utf-8") as f:
        for line in f:
            line = line.split("#", 1)[0].strip()
            if "->" not in line:
                continue
            down, ups = line.split("->", 1)
            edges |= {(u.strip(), down.strip()) for u in ups.split(",") if u.strip()}
    return edges


def a1_problems(rels, deps):
    """CI A1: the built edges in context-map.yaml equal the module dependency graph."""
    built = {(r.get("upstream"), r.get("downstream")) for r in rels if r.get("status") == "built"}
    return ([f"context-map.yaml: missing built edge {u}>{d} (in module-dependencies.txt)" for u, d in sorted(deps - built)]
            + [f"context-map.yaml: built edge {u}>{d} not in module-dependencies.txt" for u, d in sorted(built - deps)])


def self_test():
    cids = {"a", "b"}
    ok = {"id": "a>b", "upstream": "a", "downstream": "b", "via": "api：查询", "status": "built"}
    assert relation_problems([ok], cids) == [], relation_problems([ok], cids)
    assert relation_problems([{**ok, "type": "anticorruption-layer", "status": "planned"}], cids) == []
    for bad in ({"type": "partnership"}, {"type": "customer-supplier"}, {"type": "separate-ways"}, {"type": ""}, {"type": None},
                {"via": ""}, {"via": None}, {"status": "proposed"}, {"status": None}, {"id": "b>a"}, {"upstream": "x", "id": "x>b"}):
        assert relation_problems([{**ok, **bad}], cids), f"should reject {bad}"
    assert relation_problems([{k: v for k, v in ok.items() if k != "via"}], cids), "should reject missing via"
    assert relation_problems([{k: v for k, v in ok.items() if k != "status"}], cids), "should reject missing status"
    assert a1_problems([ok], {("a", "b")}) == []
    assert a1_problems([], {("a", "b")}) and a1_problems([ok], set()), "A1 must catch both directions"
    assert a1_problems([{**ok, "status": "planned"}], set()) == [], "planned edges are outside A1"
    print("self-test: ok")


def nid(x):
    return re.sub(r"[^A-Za-z0-9]", "_", x)


def esc(s):
    return str(s).replace('"', "'")


def model():
    subs = load("ssot/d-domain/subdomains.yaml")["subdomains"]
    ctx = load("ssot/d-domain/contexts.yaml")["contexts"]
    rels = load("ssot/d-domain/context-map.yaml")["relations"]
    tas = load("ssot/t-transitions/transitions.yaml")["transitions"]
    aggs = load("ssot/i03-aggregates/built.yaml")["aggregates"]
    events = catalogue()
    problems = []
    sids = {s["id"] for s in subs}
    cids = [c["id"] for c in ctx]
    for dup in {x for x in cids if cids.count(x) > 1}:
        problems.append(f"contexts.yaml: duplicate id {dup}")
    for c in ctx:
        if c.get("subdomain") and c["subdomain"] not in sids:
            problems.append(f"contexts.yaml {c['id']}: unknown subdomain {c['subdomain']}")
        if c["status"] not in ("built", "planned", "retired", "undecided"):
            problems.append(f"contexts.yaml {c['id']}: bad status {c['status']}")
    problems += relation_problems(rels, set(cids))
    problems += a1_problems(rels, module_dependencies())
    tids = {t["id"] for t in tas}
    for t in tas:
        problems += [f"transitions.yaml {t['id']}: unknown BC {b}" for b in t["delivers"] if b not in cids]
        problems += [f"transitions.yaml {t['id']}: unknown predecessor {a}" for a in t["after"] if a not in tids]
    for a in aggs:
        if a["bc"] not in cids:
            problems.append(f"aggregates.yaml {a['id']}: unknown BC {a['bc']}")
        problems += [f"aggregates.yaml {a['id']}: event {e} not in events/*.yaml" for e in a.get("events") or [] if e not in events]
    return subs, ctx, rels, tas, problems, aggs, events


def catalogue():
    """eventType -> (publisher, consumers) from the event catalogue fragments (docs/modernization/events/*.yaml, ADR-0020 SSOT)."""
    out = {}
    base = os.path.join(HERE, "..", "events")
    for f in sorted(os.listdir(base)):
        if not f.endswith(".yaml") or f == "asyncapi.yaml":
            continue
        with open(os.path.join(base, f), encoding="utf-8") as fh:
            d = yaml.safe_load(fh) or {}
        for m in ((d.get("components") or {}).get("messages") or {}).values():
            x = m.get("x-event") or {}
            if x.get("eventType"):
                cs = [c if isinstance(c, str) else c.get("module") for c in x.get("consumers") or []]
                out[x["eventType"]] = (x.get("publisher"), [c for c in cs if c])
    return out


def context_map(subs, ctx, rels):
    ctx = [c for c in ctx if c["status"] == "built"]
    by_sub = {}
    for c in ctx:
        by_sub.setdefault(c.get("subdomain") or "", []).append(c)
    L = ["%% 生成文件，勿手改：python3 architecture/build_architecture.py（源：ssot/d-domain/*.yaml）",
         "flowchart LR",
         "  classDef built fill:#dbeafe,stroke:#1d4ed8,color:#0f172a",
         "  classDef planned fill:#f8fafc,stroke:#94a3b8,stroke-dasharray:4 3,color:#475569",
         "  classDef kernel fill:#fef3c7,stroke:#b45309,color:#0f172a"]
    for s in subs + [{"id": "", "zh": "共享内核（所有模块可用，不画边）"}]:
        cs = by_sub.get(s["id"], [])
        if not cs:
            continue
        L.append(f'  subgraph SD_{nid(s["id"] or "kernel")}["{esc(s["zh"])}{" · " + s["id"] if s["id"] else ""}"]')
        L.append("    direction TB")
        for c in cs:
            L.append(f'    {nid(c["id"])}["{esc(c["zh"])}<br/><small>{c["id"]}</small>"]')
        L.append("  end")
    for r in rels:
        if r["status"] == "built":
            L.append(f'  {nid(r["upstream"])} -->|{REL_LABEL[r.get("type")]}| {nid(r["downstream"])}')
    for cls in ("built", "planned"):
        ids = [nid(c["id"]) for c in ctx if c["status"] == cls and c["id"] != "kernel"]
        if ids:
            L.append(f"  class {','.join(ids)} {cls}")
    L.append("  class kernel kernel")
    return "\n".join(L) + "\n"


def chip_style(c):
    key = "built" if c["status"] == "built" else c.get("basis", "legacy-evidence")
    return {"built": ("#dbeafe", "#1d4ed8", ""), "legacy-evidence": ("#f8fafc", "#94a3b8", "4 3"), "adr": ("#ecfdf5", "#059669", "4 3")}.get(key, ("#f8fafc", "#94a3b8", "4 3"))


def rect(x, y, w, h, fill, stroke, dash="", rx=6):
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}" stroke="{stroke}"' + (f' stroke-dasharray="{dash}"' if dash else "") + "/>"


def text(x, y, s, size=13, fill="#0f172a", **kw):
    extra = "".join(f' {k.replace("_", "-")}="{v}"' for k, v in kw.items())
    return f'<text x="{x}" y="{y}" font-size="{size}" fill="{fill}"{extra}>{html.escape(str(s))}</text>'


def svg_doc(w, h, body):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}" '
            f'font-family="-apple-system,PingFang SC,Microsoft YaHei,sans-serif" style="background-color:white">\n' + "\n".join(body) + "\n</svg>\n")


def text_w(s, size=13):
    return sum(size if ord(ch) > 0x2E7F else size * 0.6 for ch in s)


def legend(y=16):
    out, lx = [], 16
    for label, key in [("已建", "built"), ("规划：来自老系统切法，仅作证据", "legacy-evidence"), ("规划：已有 ADR 决定新建", "adr")]:
        fill, stroke, dash = chip_style({"status": "built"} if key == "built" else {"status": "planned", "basis": key})
        out += [rect(lx, y, 26, 18, fill, stroke, dash, 4), text(lx + 34, y + 14, label)]
        lx += 34 + text_w(label) + 28
    return out


def transitions(ctx, tas):
    """Plain SVG swimlanes: one row per transition architecture, its BCs wrap inside the row."""
    by = {c["id"]: c for c in ctx}
    W, lab_w, x0, chip_h, gap = 1260, 330, 16, 28, 8
    body, y = legend(), 52
    mark = {"done": "已完成", "in-progress": "进行中", "not-started": "未开始"}
    scheduled = {b for t in tas for b in t["delivers"]}
    rows = [(t["id"] + " " + t["zh"], mark[t["status"]] + ("；前置 " + "、".join(t["after"]) if t["after"] else ""), t["delivers"], t["status"]) for t in tas]
    rest = [c["id"] for c in ctx if c["id"] not in scheduled and c["id"] != "kernel"]
    if rest:
        rows.append(("未排期", "已登记、尚未排入任何阶段", rest, "none"))
    for title, sub, bcs, st in rows:
        cx, cy, lines = x0 + lab_w + 12, y + 10, []
        for b in bcs:
            label = by[b]["zh"]
            w = text_w(label) + 24
            if cx + w > W - 12:
                cx, cy = x0 + lab_w + 12, cy + chip_h + gap
            fill, stroke, dash = chip_style(by[b])
            lines += [rect(cx, cy, w, chip_h, fill, stroke, dash), text(cx + 12, cy + 19, label)]
            cx += w + gap
        h = max(cy + chip_h + 10 - y, 64)
        lane = {"in-progress": "#eff6ff", "done": "#f0fdf4"}.get(st, "#ffffff")
        body += [rect(x0, y, W - x0, h, lane, "#cbd5e1", rx=10), text(x0 + 12, y + 24, title, 15, font_weight=600), text(x0 + 12, y + 46, sub, 12, "#475569")]
        body += lines
        y += h + 10
    return svg_doc(W + 16, y + 6, body)


def landscape(subs, ctx):
    """Plain SVG: every subdomain as a card listing its BCs, coloured by status and basis."""
    groups = [(s["zh"] + " · " + s["id"], [c for c in ctx if c.get("subdomain") == s["id"]]) for s in subs]
    groups.append(("共享内核与未定子域", [c for c in ctx if not c.get("subdomain")]))
    groups = [g for g in groups if g[1]]
    cols, cw, gap, row_h, head_h, pad = 3, 380, 16, 34, 38, 10
    col_y, body = [52] * cols, legend()
    for title, cs in groups:
        h = head_h + len(cs) * row_h + pad
        ci = col_y.index(min(col_y))
        x, y = 16 + ci * (cw + gap), col_y[ci]
        body += [rect(x, y, cw, h, "#ffffff", "#cbd5e1", rx=10), text(x + 12, y + 24, title, 15, font_weight=600)]
        for i, c in enumerate(cs):
            fill, stroke, dash = chip_style(c)
            ry = y + head_h + i * row_h
            body += [rect(x + 10, ry, cw - 20, row_h - 6, fill, stroke, dash), text(x + 20, ry + 19, c["zh"]),
                     text(x + cw - 20, ry + 19, c["id"], 11, "#475569", text_anchor="end", font_family="ui-monospace,Menlo,monospace")]
        col_y[ci] += h + gap
    return svg_doc(16 + cols * (cw + gap), max(col_y) + 8, body)


def event_chain(ctx, aggs, events):
    """③ cross-BC event chain among built BCs: aggregate -> event -> consuming BC; audit fan-in collapsed to one edge per publisher."""
    zh = {c["id"]: c["zh"] for c in ctx}
    agg_of = {e: a for a in aggs for e in a.get("events") or []}
    L = ["%% 生成文件，勿手改：python3 architecture/build_architecture.py（源：ssot/i03-aggregates/built.yaml、events/*.yaml）",
         "flowchart LR",
         "  classDef bc fill:#dbeafe,stroke:#1d4ed8,color:#0f172a",
         "  classDef evt fill:#fff7ed,stroke:#c2410c,color:#0f172a",
         "  classDef audit fill:#f1f5f9,stroke:#64748b,color:#0f172a"]
    used, audit_count, lines = set(), {}, []
    for et, (pub, cons) in sorted(events.items()):
        for c in cons:
            if c == pub:
                continue
            if c == "platform.audit":
                audit_count[pub] = audit_count.get(pub, 0) + 1
                continue
            a = agg_of.get(et)
            src = nid(pub) + ("__" + nid(a["id"].split("/")[1]) if a else "")
            used.update([pub, c])
            lines.append(f'  {src} --> E_{nid(et)}(["{et.split(".")[-1]}"]):::evt --> {nid(c)}')
    for pub, n in sorted(audit_count.items()):
        used.update([pub, "platform.audit"])
        lines.append(f"  {nid(pub)} -.->|{n} 个事件| {nid('platform.audit')}")
    for b in sorted(used):
        mine = sorted({agg_of[et]["id"].split("/")[1] for et, (pub, cons) in events.items()
                       if pub == b and et in agg_of and any(c not in (pub, "platform.audit") for c in cons)})
        if mine:
            L.append(f'  subgraph {nid(b)}["{esc(zh.get(b, b))} · {b}"]')
            L += [f'    {nid(b)}__{nid(x)}["{x}"]:::bc' for x in mine]
            L.append("  end")
        else:
            L.append(f'  {nid(b)}["{esc(zh.get(b, b))}<br/><small>{b}</small>"]:::{"audit" if b == "platform.audit" else "bc"}')
    return "\n".join(L + lines) + "\n"


def tactical(ctx, aggs):
    """④ plain SVG: one card per aggregate, commands on the left, events on the right, states underneath."""
    zh = {c["id"]: c["zh"] for c in ctx}
    W, cw, gap, line = 1260, 600, 20, 20
    cols, col_y, body = 2, [52, 52], []
    body.append(text(16, 30, "每张卡：左列命令（写这个聚合的用例），右列它产生的事件，底部为状态取值；来源 ssot/i03-aggregates/built.yaml 与事件目录", 13, "#475569"))
    for a in sorted(aggs, key=lambda a: a["id"]):
        cmds = list(a.get("commands") or []) + [c + "（经端口）" for c in a.get("commands_via") or []]
        evs = [e.split(".")[-1] for e in a.get("events") or []]
        states = a.get("states") or []
        rows = max(len(cmds), len(evs), 1)
        h = 56 + rows * line + (28 if states else 8)
        ci = col_y.index(min(col_y))
        x, y = 16 + ci * (cw + gap), col_y[ci]
        body += [rect(x, y, cw, h, "#ffffff", "#cbd5e1", rx=10),
                 text(x + 12, y + 22, a["id"].split("/")[1], 15, font_weight=600),
                 text(x + cw - 12, y + 22, zh.get(a["bc"], a["bc"]) + " · " + a["bc"], 11, "#475569", text_anchor="end")]
        for i, c in enumerate(cmds):
            body.append(text(x + 12, y + 48 + i * line, "▸ " + c, 12, "#1d4ed8"))
        for i, e in enumerate(evs):
            body.append(text(x + cw / 2 + 6, y + 48 + i * line, "◆ " + e, 12, "#c2410c"))
        if not evs:
            body.append(text(x + cw / 2 + 6, y + 48, "（无事件）", 12, "#94a3b8"))
        if states:
            body.append(text(x + 12, y + h - 12, "状态：" + " / ".join(states), 11, "#475569"))
        col_y[ci] += h + gap
    return svg_doc(W + 16, max(col_y) + 8, body)


DRAWINGS = [  # (file stem, title, kind, builder); kind "mmd" is rendered by mermaid-cli, "svg" is written directly
    ("1a-landscape", "①a 上下文全景：子域与 BC", "svg", lambda m: landscape(m[0], m[1])),
    ("1b-context-map", "①b 上下文映射：已建 BC 的依赖与关系类型", "mmd", lambda m: context_map(m[0], m[1], m[2])),
    ("2-transitions", "② 过渡架构与建设阶段", "svg", lambda m: transitions(m[1], m[3])),
    ("3-event-chain", "③ 跨 BC 事件链路（已建 BC；价值流层待 METHOD 第 1 步登记后叠加）", "mmd", lambda m: event_chain(m[1], m[5], m[6])),
    ("4-tactical", "④ 战术聚合与契约（已建 BC）", "svg", lambda m: tactical(m[1], m[5])),
]


def sha(text):
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def page(svgs):
    parts = []
    for stem, title, _, _ in DRAWINGS:
        svg = svgs[stem]
        svg = re.sub(r"<\?xml[^>]*>", "", svg)
        parts.append(f'<section><h2>{html.escape(title)}</h2><div class="fig">{svg}</div></section>')
    return f"""<title>FORCITIS one 架构施工图</title>
<style>
/* Layout: one column of drawing sheets; each sheet scrolls sideways inside itself, the page never does. Drawings sit on white paper in both themes. */
:root{{--bg:#f3f5f8;--ink:#0f172a;--muted:#475569;--sheet:#ffffff;--rule:#d6dde6;--accent:#1d4ed8;
  --f-body:-apple-system,"PingFang SC","Hiragino Sans GB","Microsoft YaHei",system-ui,sans-serif}}
@media (prefers-color-scheme: dark){{:root:not([data-theme="light"]){{--bg:#0d1420;--ink:#e5eaf1;--muted:#9aa7b8;--sheet:#ffffff;--rule:#243246;--accent:#8fb0ec;color-scheme:dark}}}}
:root[data-theme="dark"]{{--bg:#0d1420;--ink:#e5eaf1;--muted:#9aa7b8;--sheet:#ffffff;--rule:#243246;--accent:#8fb0ec;color-scheme:dark}}
body{{background:var(--bg);color:var(--ink);font:15px/1.6 var(--f-body)}}
main{{max-width:1280px;margin:0 auto;padding-inline:16px;padding-block:20px 48px;display:grid;gap:28px}}
header{{display:grid;gap:6px}}
h1{{font-size:22px;margin:0;text-wrap:balance}} p.lede{{color:var(--muted);margin:0;max-width:70ch}}
section{{display:grid;gap:10px;min-width:0}} h2{{font-size:17px;margin:0;text-wrap:balance}}
.fig{{background:var(--sheet);border:1px solid var(--rule);border-radius:10px;padding:12px;overflow-x:auto;min-width:0}}
.fig svg{{max-width:none;height:auto;display:block}}
</style>
<main>
<header><h1>架构施工图</h1>
<p class="lede">由架构 SSOT（docs/modernization/architecture/）生成。实线框为已建，虚线框为规划；规划项来自老系统切法或已有 ADR，仅作起点，进入对应阶段前按能力地图与价值流重新推导。图较宽时在框内横向滑动。</p></header>
{''.join(parts)}
</main>
"""


def main():
    os.chdir(HERE)
    m = model()
    if m[4]:
        print("\n".join(m[4]))
        sys.exit(1)
    os.makedirs(GEN, exist_ok=True)
    want = {stem: build(m) for stem, _, _, build in DRAWINGS}
    kind = {stem: k for stem, _, k, _ in DRAWINGS}
    hashes_path = os.path.join(GEN, "svg-sources.sha256")
    if "--check" in sys.argv:
        bad = []
        for stem, text in want.items():
            p = os.path.join(GEN, f"{stem}.{kind[stem]}")
            if not os.path.exists(p) or open(p, encoding="utf-8").read() != text:
                bad.append(f"generated/{stem}.{kind[stem]} is stale: run python3 architecture/build_architecture.py --svg")
        recorded = dict(l.split()[::-1] for l in open(hashes_path)) if os.path.exists(hashes_path) else {}
        bad += [f"generated/{stem}.svg was rendered from an older .mmd: run --svg" for stem, text in want.items()
                if kind[stem] == "mmd" and recorded.get(stem) != sha(text)]
        if bad:
            print("\n".join(bad))
            sys.exit(1)
        return
    for stem, text in want.items():
        with open(os.path.join(GEN, f"{stem}.{kind[stem]}"), "w", encoding="utf-8") as f:
            f.write(text)
    if "--svg" in sys.argv:
        if not os.path.exists(MMDC):
            sys.exit(f"mermaid-cli not found at {MMDC} (SUPPLY_CHAIN.md: local drawing tool)")
        svgs = {}
        for stem in want:
            out = os.path.join(GEN, stem + ".svg")
            if kind[stem] == "svg":
                svgs[stem] = want[stem]
                continue
            src = os.path.join(GEN, stem + ".mmd")
            subprocess.run([MMDC, "-q", "-i", src, "-o", out, "-b", "white", "-c", os.path.join(HERE, "mermaid-config.json")], check=True)
            svgs[stem] = open(out, encoding="utf-8").read()
        with open(os.path.join(GEN, "index.html"), "w", encoding="utf-8") as f:
            f.write(page(svgs))
        with open(hashes_path, "w") as f:
            f.writelines(f"{sha(text)}  {stem}\n" for stem, text in sorted(want.items()) if kind[stem] == "mmd")
    elif os.path.exists(hashes_path):   # without --svg a changed .mmd leaves its .svg stale: fail so build_all.py --check catches it
        recorded = dict(l.split()[::-1] for l in open(hashes_path))
        stale = [stem for stem, text in want.items() if kind[stem] == "mmd" and recorded.get(stem) != sha(text)]
        if stale:
            sys.exit(f"generated/{', '.join(stale)}.svg rendered from an older .mmd: run python3 architecture/build_architecture.py --svg")
    print("drawings:", ", ".join(want))


if __name__ == "__main__":
    self_test() if "--self-test" in sys.argv[1:] else main()
