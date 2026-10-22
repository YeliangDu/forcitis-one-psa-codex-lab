#!/usr/bin/env python3
"""资料笔记的格式与断链检查（严格），并生成概念索引 CONCEPT_INDEX.md 与总目录 NOTES_INDEX.md。

概念索引按锚点概念名归并各笔记的叫法与位置，并接 terms.yaml 词条；跨资料差异说明与同义归并手写在 concept_notes.yaml：`概念: {差异: …, 同义: [其他笔记的锚点概念名]}`。
"""
import re
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

import yaml

from notes_check import anchor_rows, cells, split_front_matter

HERE = Path(__file__).resolve().parent
NOTES = HERE / "notes"
TERMS = HERE.parents[1] / "glossary" / "terms.yaml"
DIFF_NOTES = HERE / "concept_notes.yaml"
CONCEPT_OUT = HERE / "CONCEPT_INDEX.md"
INDEX_OUT = HERE / "NOTES_INDEX.md"
VS_RE = re.compile(r"(?<![A-Za-z0-9-])VS-[0-9]{2}(?![0-9])")
SKIP = {"书目"}
HEADER = "<!-- 由 frameworks/notes_build.py 生成，勿手改 -->\n"


def load_notes():
    notes = []
    for path in sorted(NOTES.glob("*.md")):
        if path.name.startswith("_"):
            continue
        meta, body = split_front_matter(path.read_text(encoding="utf-8"))
        anchors, seen = [], set()
        for _, concept, c, _ in anchor_rows(body):
            if concept in seen:
                continue
            seen.add(concept)
            pages = " / ".join(p for p in c[3:5] if p and p != "—") if len(c) >= 6 else ""
            anchors.append({"concept": concept, "name": c[1] if len(c) > 1 else "",
                            "where": c[2] if len(c) > 2 else "", "pages": pages})
        notes.append({"file": path.name, "meta": meta, "anchors": anchors, "vs": sorted(set(VS_RE.findall(body)))})
    return notes


def esc(s):
    return str(s).replace("|", "／").replace("\n", " ")


def link_text(s):
    return esc(s).replace("[", "［").replace("]", "］")


def build_concepts(notes):
    terms = {t["zh"]: t for t in yaml.safe_load(TERMS.read_text(encoding="utf-8"))["terms"]}
    diffs = (yaml.safe_load(DIFF_NOTES.read_text(encoding="utf-8")) or {}) if DIFF_NOTES.exists() else {}
    canon = {s: k for k, v in diffs.items() for s in (v or {}).get("同义", [])}
    groups = defaultdict(list)
    for n in notes:
        for a in n["anchors"]:
            if a["concept"] not in SKIP:
                groups[canon.get(a["concept"], a["concept"])].append((n, a))
    present = {a["concept"] for n in notes for a in n["anchors"]}
    unknown = sorted((set(diffs) | set(canon)) - present)
    shared = sorted(k for k, v in groups.items() if len({n["file"] for n, _ in v}) > 1 or k in terms)
    lines = [HEADER, "# 资料概念索引\n",
             "同一概念在各资料中的叫法与位置。收录跨两份及以上资料出现、或在 terms.yaml 有词条的概念；只在一份资料出现的概念见各笔记。差异说明手写在 `concept_notes.yaml`。\n",
             f"收录 {len(shared)} 个概念（全部锚点 {sum(len(v) for v in groups.values())} 个）。\n"]
    for k in shared:
        term = terms.get(k)
        lines.append(f"## {k}\n")
        if term:
            lines.append(f"词条：`{term['id']}`（{term.get('en', '')}，{term.get('bc', '')}）——{esc(term.get('definition', ''))}\n")
        else:
            lines.append("词条：无（terms.yaml 未收）\n")
        if (diffs.get(k) or {}).get("差异"):
            lines.append(f"差异：{esc(diffs[k]['差异'])}\n")
        lines.append("| 锚点 | 资料 | 原文叫法 | 位置 | 页码 |\n|---|---|---|---|---|")
        for n, a in groups[k]:
            lines.append(f"| {n['meta']['编号']}#{a['concept']} | [{link_text(n['meta']['题名'])}](notes/{n['file']}) | {esc(a['name'])} | {esc(a['where'])} | {esc(a['pages']) or '—'} |")
        lines.append("")
    return "\n".join(lines), unknown


def build_index(notes):
    vocab = yaml.safe_load((NOTES / "_vocab.yaml").read_text(encoding="utf-8"))

    def row(n):
        m = n["meta"]
        return f"| {m['编号']} | [{link_text(m['题名'])}](notes/{n['file']}) | {m['年份']} | {m['资料类型']} | {m['状态']} | {len(n['anchors'])} |"

    head = "| 编号 | 题名 | 年份 | 资料类型 | 状态 | 锚点 |\n|---|---|---|---|---|---|"
    out = [HEADER, "# 资料笔记总目录\n",
           f"共 {len(notes)} 份。格式见 [notes/_TEMPLATE.md](notes/_TEMPLATE.md)，概念对照见 [CONCEPT_INDEX.md](CONCEPT_INDEX.md)，按问题查结论见 [DECISION_GUIDE.md](DECISION_GUIDE.md)。\n",
           "## 全部\n", head, *[row(n) for n in sorted(notes, key=lambda n: str(n["meta"]["编号"]))], "", "## 按主题\n"]
    for tag, definition in vocab["主题标签"].items():
        hit = [n for n in notes if tag in n["meta"]["主题标签"]]
        if hit:
            out += [f"### {tag}（{len(hit)}）\n", f"{definition}\n", head, *[row(n) for n in hit], ""]
    out.append("## 按资料类型\n")
    for kind in vocab["资料类型"]:
        hit = [n for n in notes if n["meta"]["资料类型"] == kind]
        if hit:
            out += [f"### {kind}（{len(hit)}）\n", head, *[row(n) for n in hit], ""]
    out.append("## 按价值流\n\n笔记正文引用到的价值流编号。\n")
    by_vs = defaultdict(list)
    for n in notes:
        for vs in n["vs"]:
            by_vs[vs].append(n)
    for vs in sorted(by_vs):
        out += [f"### {vs}（{len(by_vs[vs])}）\n", head, *[row(n) for n in by_vs[vs]], ""]
    if not by_vs:
        out.append("暂无笔记引用价值流编号。\n")
    return "\n".join(out)


def self_test():
    c = cells("| KIM3#粒度 | Grain | 第 2 章 | 39-40 | 75-76 | 先声明一行含义 |")
    assert c[0] == "KIM3#粒度" and c[3] == "39-40" and len(c) == 6
    assert anchor_rows("## 核心思想\n```\n| KIM3#假 | x |\n```\n| KIM3#真 | y |\n## 附：核对\n| KIM3#附 | z |")[0][1] == "真"
    assert anchor_rows("## 核心思想\n| KIM3#甲、KIM3#乙 | x |\n") == []
    assert link_text("a[b]|c") == "a［b］／c"
    assert VS_RE.findall("见 VS-15、PRC-190、XVS-01") == ["VS-15"]
    import notes_check
    assert "不存在" in notes_check.missing_system_error(None) and notes_check.missing_system_error(({}, set(), {})) is None, "全景模型缺失是错误，不是静默跳过"
    print("self-test ok")


def main():
    if "--self-test" in sys.argv:
        self_test()
        return 0
    r = subprocess.run([sys.executable, str(HERE / "notes_check.py"), "--strict"], capture_output=True, text=True)
    if r.returncode:
        print(r.stdout + r.stderr)
        return 1
    notes = load_notes()
    concepts, unknown = build_concepts(notes)
    if unknown:
        print("concept_notes.yaml 有不存在的锚点概念名：" + "、".join(unknown))
        return 1
    CONCEPT_OUT.write_text(concepts, encoding="utf-8")
    INDEX_OUT.write_text(build_index(notes), encoding="utf-8")
    print(f"笔记 {len(notes)} 份；{r.stdout.strip()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
