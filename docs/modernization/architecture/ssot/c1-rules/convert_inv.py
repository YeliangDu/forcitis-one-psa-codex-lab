#!/usr/bin/env python3
"""把 ENTERPRISE_AGGREGATES_DRAFT.md §1.2 的不变量表逐行转成 rules.yaml（不改文字、不合并、不新增）。

聚合 → BC 取同稿 §2.2 的分组标题（**B-nn 名称**）与 ADR-0051 决定 3 的 B-nn → BC 标识对照；对照表没有的 B-nn（如后增的 B-31）取分组标题中写明的 BC 标识。
rule_cards 由 one-architecture 人工判定，写在 rule_cards.yaml，不由本脚本生成。
用法：python3 convert_inv.py [--check]
"""
import re, sys, pathlib

ROOT = pathlib.Path(__file__).resolve().parents[5]
SRC = ROOT / "docs/modernization/architecture/proposals/ENTERPRISE_AGGREGATES_DRAFT.md"
ADR51 = ROOT / "docs/modernization/adr/src/ADR-0051.md"
OUT = pathlib.Path(__file__).with_name("rules.yaml")

def cells(line):
    return [c.strip() for c in line.strip().strip("|").split("|")]

def q(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'

def main(check):
    text = SRC.read_text(encoding="utf-8").splitlines()
    b2bc = {}
    for l in ADR51.read_text(encoding="utf-8").splitlines():
        m = re.match(r"\| (B-\d+) \| `([a-z_.]+)` \|", l)
        if m:
            b2bc[m.group(1)] = m.group(2)
    agg2b, group = {}, None
    in_aggs = in_invs = False
    invs, inv_group = [], None
    for i, l in enumerate(text, 1):
        if l.startswith("### 1.2"):
            in_invs = True
            continue
        if in_invs and l.startswith("### "):
            in_invs = False
        if l.startswith("### 2.2"):
            in_aggs = True
            continue
        if in_aggs and l.startswith("## "):
            in_aggs = False
        if in_aggs:
            m = re.match(r"\*\*(B-\d+) ", l)
            if m:
                group = m.group(1)
                inline = re.search(r"`([a-z]+\.[a-z_]+)`", l)
                if inline and group not in b2bc:
                    b2bc[group] = inline.group(1)
            m = re.match(r"\| (AGG-\d+[a-z]?) \|", l)
            if m and group:
                agg2b.setdefault(m.group(1), group)
        if in_invs:
            m = re.match(r"\*\*(.+?)\*\*\s*$", l)
            if m:
                inv_group = m.group(1)
            if l.startswith("| INV-"):
                c = cells(l)
                if len(c) < 5:
                    sys.exit(f"{SRC.name}:{i}: 列数不足 5")
                invs.append(dict(id=c[0], statement=c[1], category=c[2], aggregate=c[3], source=c[4], group=inv_group, line=i))
    if not invs:
        sys.exit("未读到不变量")
    seen = set()
    out = ["# 由 architecture/ssot/c1-rules/convert_inv.py 生成，勿手改；rule_cards 见 rule_cards.yaml",
           "meta:", "  status: proposal", f"  source: {SRC.relative_to(ROOT)}", f"  count: {len(invs)}", "rules:"]
    missing = []
    for v in invs:
        if v["id"] in seen:
            sys.exit(f"重复编号 {v['id']}")
        seen.add(v["id"])
        aggs = re.findall(r"AGG-\d+[a-z]?", v["aggregate"])
        bcs = sorted({b2bc.get(agg2b.get(a, ""), "") for a in aggs} - {""})
        if not bcs:
            missing.append(v["id"])
        out += [f"  - id: {v['id']}", f"    statement: {q(v['statement'])}", f"    category: {q(v['category'])}",
                f"    owner_aggregates: [{', '.join(aggs)}]", f"    aggregate_note: {q(v['aggregate'])}",
                f"    owner_bc: [{', '.join(bcs)}]", f"    group: {q(v['group'] or '')}",
                f"    source: {q(v['source'])}", f"    source_line: {v['line']}"]
    content = "\n".join(out) + "\n"
    if check:
        if not OUT.exists() or OUT.read_text(encoding="utf-8") != content:
            sys.exit("rules.yaml 与来源不一致，重跑 convert_inv.py")
    else:
        OUT.write_text(content, encoding="utf-8")
    print(f"不变量 {len(invs)} 条；无 BC 归属 {len(missing)} 条：{', '.join(missing)}")

if __name__ == "__main__":
    main("--check" in sys.argv)
