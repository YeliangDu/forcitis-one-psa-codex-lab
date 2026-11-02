"""Generate docs/adr/current.md: which ADRs are in force per BC, which are superseded, and which clauses were partly superseded by whom.

Usage: python3 -I build_adr_current.py [--self-test]
Reads the ADR headers (状态, 类别, BC, 取代, 被部分取代) and docs/adr/catalog.yaml (bc, category, partially_superseded; it stops at ADR-0051,
later ADRs are read from their headers only). The page is generated, never hand-edited.
"""
import collections
import re
import sys
from pathlib import Path

import yaml

REPO = Path(__file__).resolve().parents[2]
ADR = REPO / "docs/adr"
OUT = ADR / "current.md"
REF = re.compile(r"ADR-(\d{4})")
STATE_WORDS = ("已采纳", "提议", "搁置", "已废弃", "已拒绝", "已撤回")


def header(text):
    """Header fields of one ADR: single values as str, multi-value (indented list) fields as list."""
    head = text.split("\n## ", 1)[0]
    fields, key = {}, None
    for line in head.splitlines():
        m = re.match(r"- \*\*(.+?)\*\*：\s*(.*)", line)
        if m:
            key = m.group(1)
            fields[key] = m.group(2) if m.group(2) else []
        elif key and isinstance(fields[key], list) and re.match(r"\s+- ", line):
            fields[key].append(line.strip()[2:].strip())
    return fields


def clip(s, n=60):
    s = re.sub(r"[（(]部分[）)]", "", s).strip()
    return s if len(s) <= n else s[: n - 1] + "…"


def load(adr_dir=ADR):
    catalog = {e["id"]: e for e in yaml.safe_load((adr_dir / "catalog.yaml").read_text(encoding="utf-8"))["adrs"]}
    adrs = {}
    for f in sorted(adr_dir.glob("ADR-*.md")):
        n = f.stem[4:]
        text = f.read_text(encoding="utf-8")
        h, c = header(text), catalog.get(n, {})
        state = str(h.get("状态", ""))
        word = next((w for w in STATE_WORDS if state.startswith(w)), None)
        whole = re.match(r"已被 ADR-(\d{4}) 取代", state)
        bc = h.get("BC") or c.get("bc") or ["待定"]
        adrs[n] = {
            "title": text.splitlines()[0].split("：", 1)[-1].strip(),
            "state": "superseded" if whole else (word or "other"),
            "by": whole.group(1) if whole else None,
            "bc": bc if isinstance(bc, list) else [bc],
            "category": h.get("类别") or c.get("category") or "待定",
            "supersedes": h.get("取代") or [],
            "partly": (h.get("被部分取代") or []) + (c.get("partially_superseded") or []),
        }
    return adrs


def partial_clauses(adrs):
    """{target: {superseder: [ranges]}} from the newer ADR's 取代 field and the older one's 被部分取代 / catalog entries."""
    out = collections.defaultdict(lambda: collections.defaultdict(list))
    for n, a in adrs.items():
        for item in a["supersedes"]:
            m = REF.match(item)
            rest = item[m.end():].strip() if m else ""
            if m and rest and m.group(1) in adrs and adrs[m.group(1)]["state"] != "superseded":
                out[m.group(1)][n].append(clip(rest))
        for item in a["partly"]:
            m = REF.match(item)
            if m:
                out[n][m.group(1)].append(clip(item[m.end():]))
    for target in out:
        for by, ranges in out[target].items():
            ranges = list(dict.fromkeys(r for r in ranges if r))
            out[target][by] = [r for r in ranges if not any(r != o and r in o for o in ranges)]
    return out


def render(adrs):
    partial = partial_clauses(adrs)
    live = {n: a for n, a in adrs.items() if a["state"] == "已采纳"}
    lines = [
        "# ADR 现行规则索引（生成，勿手改）",
        "",
        "由 `scripts/guards/build_adr_current.py` 从 ADR 头部与 `catalog.yaml` 生成。现行 = 状态「已采纳」；标「部分被取代」的 ADR，以「部分被取代的条款」一节所列取代它的 ADR 为准。",
        "",
    ]
    by_bc = collections.defaultdict(list)
    for n, a in live.items():
        for bc in a["bc"]:
            by_bc[bc].append(n)
    for bc in sorted(by_bc, key=lambda b: (b != "全局", b)):
        lines += [f"## {bc}", ""] + [f"- ADR-{n} {live[n]['title']}{'（部分被取代）' if n in partial else ''}" for n in sorted(by_bc[bc])] + [""]
    lines += ["## 部分被取代的条款", "", "在上面的 ADR 中，下列条款已被后来的 ADR 取代，现行规则看右侧。", ""]
    for n in sorted(partial):
        if n in live:
            lines.append(f"- ADR-{n} ← " + "；".join(f"ADR-{by} {'、'.join(r)}" for by, r in sorted(partial[n].items())))
    lines.append("")
    gone = {n: a for n, a in adrs.items() if a["state"] == "superseded"}
    lines += ["## 整份被取代", ""] + [f"- ADR-{n} {a['title']} → ADR-{a['by']}" for n, a in sorted(gone.items())] + [""]
    other = {n: a for n, a in adrs.items() if a["state"] not in ("已采纳", "superseded")}
    lines += ["## 非现行（提议、搁置等）", ""] + [f"- ADR-{n} {a['title']}（{a['state']}）" for n, a in sorted(other.items())] + [""]
    return "\n".join(lines)


def self_test():
    import tempfile
    with tempfile.TemporaryDirectory() as d:
        d = Path(d)
        (d / "catalog.yaml").write_text(
            "adrs:\n- id: \"0001\"\n  category: 方法\n  bc: [全局]\n  partially_superseded: [\"ADR-0002 决定 1\"]\n", encoding="utf-8")
        for n, extra in (("0001", ""), ("0002", "- **取代**：\n  - ADR-0001 决定 2（部分）\n  - ADR-0003\n"), ("0003", "")):
            state = "已被 ADR-0002 取代" if n == "0003" else "已采纳"
            (d / f"ADR-{n}.md").write_text(
                f"# ADR-{n}：T{n}\n\n- **状态**：{state}\n- **类别**：方法\n- **BC**：\n  - platform.x\n{extra}\n## 背景\n", encoding="utf-8")
        page = render(load(d))
    assert "## platform.x" in page and "ADR-0001 T0001" in page
    assert "ADR-0001 ← ADR-0002 决定 1、决定 2" in page, page
    assert "ADR-0003 T0003 → ADR-0002" in page and "- ADR-0003 T0003" not in page.split("## 整份被取代")[0]
    print("self-test ok")


if __name__ == "__main__":
    if "--self-test" in sys.argv:
        self_test()
    else:
        OUT.write_text(render(load()), encoding="utf-8")
        print(f"wrote {OUT.relative_to(REPO)}")
