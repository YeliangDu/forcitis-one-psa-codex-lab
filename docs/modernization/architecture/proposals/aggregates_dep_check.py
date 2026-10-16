"""Derive BC dependencies from ENTERPRISE_AGGREGATES_DRAFT.md and check them for cycles.

Read-only. Usage: python3 -I aggregates_dep_check.py [draft.md] [module-dependencies.txt]
Exit code 0 only when every reference parses, the graph (draft edges plus built edges) is
acyclic, and the layer block in section 4.1 equals the generated layers.
"""
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[3]
DRAFT = Path(sys.argv[1]) if len(sys.argv) > 1 else HERE / "ENTERPRISE_AGGREGATES_DRAFT.md"
BUILT = Path(sys.argv[2]) if len(sys.argv) > 2 else REPO / "backend/architecture/module-dependencies.txt"

MODES = {"api", "事件", "不校验"}
TARGET = re.compile(r"(AGG-\d+|REF-\d+|B-\d+|(?:platform|organization|operations|accounting|cost)\.[a-z]+)")
MODE = re.compile(r"（([^（）]*)）\s*$")
BC_HEADING = re.compile(r"^\*\*(B-\d+)\b")
AGG_ROW = re.compile(r"^\|\s*((?:AGG|REF)-\d+)\s*\|")
BC_ROW = re.compile(r"^\|\s*(B-\d+)\s*\|([^|]*)\|([^|]*)\|")
BEGIN, END = "<!-- dep-layers:begin -->", "<!-- dep-layers:end -->"

errors = []
names = {}


def cells(line):
    return [c.strip() for c in line.strip().strip("|").split("|")]


def parse(text):
    owner, deps, counted, modules = {}, {}, set(), {}
    names.clear()
    section, current = None, None
    for no, line in enumerate(text.splitlines(), 1):
        if line.startswith("### "):
            section = line[4:].split(" ")[0]
        if section == "2.2":
            m = BC_HEADING.match(line)
            if m:
                current = m.group(1)
                continue
            m = AGG_ROW.match(line)
            if m:
                if current is None:
                    errors.append(f"line {no}: {m.group(1)} outside a B-xx heading")
                    continue
                agg, row = m.group(1), cells(line)
                if agg in owner:
                    errors.append(f"line {no}: {agg} listed twice")
                owner[agg] = current
                if agg.startswith("AGG-") and "（不计" not in line:
                    counted.add(agg)
                deps[agg] = (no, row[-1])
        elif section == "3.2":
            m = BC_ROW.match(line)
            if m:
                mod = re.search(r"`([a-z]+\.[a-z]+)`", m.group(3))
                modules[m.group(1)] = mod.group(1) if mod else None
                names[m.group(1)] = m.group(2).strip()
    return owner, deps, counted, modules


def edges_from(owner, deps):
    edges, unchecked = {}, 0
    for agg, (no, cell) in deps.items():
        if cell in ("—", "-", ""):
            continue
        for seg in [s.strip() for s in cell.split("；") if s.strip()]:
            t, mm = TARGET.match(seg), MODE.search(seg)
            if not t or not mm:
                errors.append(f"line {no}: {agg}: cannot parse dependency '{seg}'")
                continue
            modes = {x.strip() for x in mm.group(1).split("、")}
            if not modes <= MODES:
                errors.append(f"line {no}: {agg}: unknown mode in '{seg}'")
                continue
            target = t.group(1)
            if target.startswith(("AGG-", "REF-")):
                if target not in owner:
                    errors.append(f"line {no}: {agg}: unknown target {target}")
                    continue
                target = owner[target]
            if modes == {"不校验"}:
                unchecked += 1
                continue
            src = owner[agg]
            if src != target:
                edges.setdefault((src, target), set()).update(modes - {"不校验"})
    return edges, unchecked


def built_edges(modules):
    by_module = {m: b for b, m in modules.items() if m}
    out, isolated = [], set()
    for line in BUILT.read_text(encoding="utf-8").splitlines():
        line = line.split("#", 1)[0].strip()
        if "->" not in line:
            continue
        left, right = (s.strip() for s in line.split("->", 1))
        isolated.add(by_module.get(left, left))
        for r in [s.strip() for s in right.split(",") if s.strip()]:
            out.append((left, r, by_module.get(left, left), by_module.get(r, r)))
    return out, isolated


def find_cycles(nodes, adj):
    index, low, stack, on, sccs, n = {}, {}, [], set(), [], [0]

    def visit(v):
        index[v] = low[v] = n[0]
        n[0] += 1
        stack.append(v)
        on.add(v)
        for w in sorted(adj.get(v, ())):
            if w not in index:
                visit(w)
                low[v] = min(low[v], low[w])
            elif w in on:
                low[v] = min(low[v], index[w])
        if low[v] == index[v]:
            comp = []
            while True:
                w = stack.pop()
                on.discard(w)
                comp.append(w)
                if w == v:
                    break
            if len(comp) > 1 or v in adj.get(v, ()):
                sccs.append(sorted(comp))

    sys.setrecursionlimit(10000)
    for v in sorted(nodes):
        if v not in index:
            visit(v)
    return sccs


def layers(nodes, adj):
    level = {}

    def depth(v, seen=()):
        if v in level:
            return level[v]
        level[v] = 1 + max((depth(w) for w in adj.get(v, ())), default=-1)
        return level[v]

    for v in nodes:
        depth(v)
    out = {}
    for v, d in level.items():
        out.setdefault(d, []).append(v)
    return [sorted(out[d], key=sort_key) for d in sorted(out)]


def sort_key(v):
    return (0, int(v[2:])) if v.startswith("B-") else (1, v)


def label(v, modules):
    m = modules.get(v) or names.get(v)
    return f"{v} {m}" if m else v


def main():
    text = DRAFT.read_text(encoding="utf-8")
    owner, deps, counted, modules = parse(text)
    edges, unchecked = edges_from(owner, deps)
    built, built_nodes = built_edges(modules)

    adj = {}
    for (s, t) in edges:
        adj.setdefault(s, set()).add(t)
    built_missing_in_draft = []
    for lm, rm, s, t in built:
        adj.setdefault(s, set()).add(t)
        if (s, t) not in edges and s.startswith("B-") and t.startswith("B-"):
            built_missing_in_draft.append(f"{lm} -> {rm}")
    nodes = set(adj) | {t for ts in adj.values() for t in ts} | set(owner.values()) | built_nodes

    per_bc = {}
    for agg in counted:
        per_bc.setdefault(owner[agg], []).append(agg)

    print(f"聚合数（计数）：{len(counted)}；BC 数（§2.2 有标题的）：{len(set(owner.values()))}")
    print("每个 BC 的聚合数：" + "，".join(
        f"{b} {len(per_bc.get(b, []))}" for b in sorted(set(owner.values()), key=sort_key)))
    print(f"引用条目中「不校验」{unchecked} 条（不成边）")
    print(f"由 §2.2 推出的 BC 依赖边：{len(edges)} 条；已建边（module-dependencies.txt）：{len(built)} 条")
    print("已建边在 §2.2 中未出现（业务 BC 之间）：" + ("无" if not built_missing_in_draft else "；".join(built_missing_in_draft)))
    built_pairs = {(s, t) for _, _, s, t in built}
    new_on_built = sorted(f"{label(s, modules)} -> {label(t, modules)}"
                          for (s, t) in edges if (s, t) not in built_pairs and modules.get(s) and modules.get(s) in
                          {lm for lm, _, _, _ in built})
    print("已建模块的新增规划边（ADR-0041 决定 4：到阶段再登记）：" + ("无" if not new_on_built else "；".join(new_on_built)))

    print("各业务 BC 的上游（§2.2 推出；括号内为方式）：")
    for b in sorted({s for s, _ in edges}, key=sort_key):
        ups = sorted((t for s, t in edges if s == b), key=sort_key)
        print(f"  {label(b, modules)} → " + "、".join(
            f"{t}（{'、'.join(sorted(edges[(b, t)]))}）" for t in ups))

    cycles = find_cycles(nodes, adj)
    print("环：" + ("无" if not cycles else " | ".join(" ↔ ".join(c) for c in cycles)))

    generated = None
    if not cycles:
        lines = []
        for i, layer in enumerate(layers(nodes, adj)):
            lines.append(f"L{i}: " + "、".join(label(v, modules) for v in layer))
        generated = "\n".join(lines)
        print("分层（L0 最上游；每个节点只依赖更小层号的节点）：")
        print(generated)

    if BEGIN in text and END in text and generated is not None:
        block = text.split(BEGIN, 1)[1].split(END, 1)[0].strip().strip("`").strip()
        same = block == generated
        print("§4.1 分层块与生成结果：" + ("一致" if same else "不一致"))
        if not same:
            errors.append("§4.1 layer block differs from the generated layers")
    elif generated is not None:
        errors.append("§4.1 layer block markers not found")

    for e in errors:
        print("错误：" + e)
    return 1 if errors or cycles else 0


if __name__ == "__main__":
    sys.exit(main())
