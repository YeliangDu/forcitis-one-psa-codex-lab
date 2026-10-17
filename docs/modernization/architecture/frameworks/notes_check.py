#!/usr/bin/env python3
"""资料笔记格式与锚点引用检查。

新格式笔记（带 YAML 信息栏）逐项校验；旧格式笔记只计数为待迁移，--strict 时也算失败。
锚点概念名命中 terms.yaml 词条的别名或禁用词时须改用词条中文名；产品资料的证据号须在 notes/_evidence/<编号>.yaml 登记且有路径与定位。
全仓 docs/modernization 下形如 `编号#概念名` 的引用，编号属已登记笔记时须能解析到锚点。
"""
import argparse
import re
import sys
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent
NOTES = HERE / "notes"
DOCS = HERE.parents[1]
TERMS = DOCS / "glossary" / "terms.yaml"
EVIDENCE = NOTES / "_evidence"
EVID_RE = re.compile(r"(?<![A-Za-z0-9])E[0-9]{2,3}(?![0-9])")
SECTIONS = ["核心思想", "概念及页码", "对本系统的启示", "我们的取舍"]
REQUIRED = ["编号", "题名", "作者", "年份", "版本", "资料类型", "主题标签", "状态", "派工"]
ID_RE = re.compile(r"^[A-Z]{2,6}[0-9]{0,4}$")
REF_RE = re.compile(r"(?<![A-Za-z0-9])([A-Z]{2,6}[0-9]{0,4})#([^\s|，。、；;：:（）()\[\]`'\"]+)")


def split_front_matter(text):
    if not text.startswith("---\n"):
        return None, text
    end = text.find("\n---\n", 4)
    if end < 0:
        return None, text
    try:
        meta = yaml.safe_load(text[4:end]) or {}
    except yaml.YAMLError as exc:
        meta = {"_error": str(exc).splitlines()[0]}
    return meta, text[end + 5:]


def load_term_aliases():
    zh_names, alias_to_zh = set(), {}
    for term in yaml.safe_load(TERMS.read_text(encoding="utf-8"))["terms"]:
        zh_names.add(term["zh"])
        names = [a["name"] for a in term.get("aliases") or [] if isinstance(a, dict)]
        names += [f for f in term.get("forbidden") or [] if isinstance(f, str)]
        for name in names:
            for part in name.split(" / "):
                alias_to_zh.setdefault(part.strip(), term["zh"])
    return {a: z for a, z in alias_to_zh.items() if a not in zh_names}


def check_evidence(name, nid, body, errors):
    reg_path = EVIDENCE / f"{nid}.yaml"
    if not reg_path.exists():
        errors.append(f"{name}: 产品资料须有证据登记 _evidence/{nid}.yaml")
        return
    reg = (yaml.safe_load(reg_path.read_text(encoding="utf-8")) or {}).get("证据") or {}
    for eid, entry in reg.items():
        if not (entry or {}).get("路径") or not (entry or {}).get("定位"):
            errors.append(f"_evidence/{nid}.yaml: {eid} 缺路径或定位")
    for eid in sorted(set(EVID_RE.findall(body))):
        if eid not in reg:
            errors.append(f"{name}: 证据号 {eid} 未在 _evidence/{nid}.yaml 登记")


def check_note(path, meta, body, vocab, aliases, errors):
    name = path.name
    if "_error" in meta:
        errors.append(f"{name}: 信息栏不是合法 YAML（{meta['_error']}），含冒号的值须加引号")
        return "", set()
    for key in REQUIRED:
        if meta.get(key) in (None, "", []):
            errors.append(f"{name}: 信息栏缺 {key}")
    nid = str(meta.get("编号", ""))
    if nid and not ID_RE.match(nid):
        errors.append(f"{name}: 编号 {nid} 不符合 大写字母 2-6 位 + 可选数字")
    if meta.get("资料类型") and meta["资料类型"] not in vocab["资料类型"]:
        errors.append(f"{name}: 资料类型 {meta['资料类型']} 不在 _vocab.yaml")
    if meta.get("状态") and meta["状态"] not in vocab["状态"]:
        errors.append(f"{name}: 状态 {meta['状态']} 不在 _vocab.yaml")
    tags = meta.get("主题标签") or []
    if not isinstance(tags, list):
        errors.append(f"{name}: 主题标签须为列表")
        tags = []
    for tag in tags:
        if tag not in vocab["主题标签"]:
            errors.append(f"{name}: 主题标签 {tag} 不在 _vocab.yaml")

    heads = re.findall(r"^## (.+?)\s*$", body, re.M)
    main = [h for h in heads if not h.startswith("附：")]
    if main != SECTIONS:
        errors.append(f"{name}: 二级标题须依次为 {'、'.join(SECTIONS)}（附节以「附：」开头），实为 {'、'.join(main)}")
    if heads and any(h.startswith("附：") for h in heads[: len(main)]):
        errors.append(f"{name}: 附节须在四节之后")

    anchors = []
    table = re.search(r"^## 概念及页码\s*$(.*?)(?=^## |\Z)", body, re.M | re.S)
    if table:
        for row in re.findall(r"^\|\s*([^|]+?)\s*\|", table.group(1), re.M):
            if row in ("锚点",) or set(row) <= set("-: "):
                continue
            if not row.startswith(f"{nid}#") or len(row) == len(nid) + 1:
                errors.append(f"{name}: 锚点 {row} 须以 {nid}# 开头并带概念名")
                continue
            concept = row.split("#", 1)[1]
            if concept in aliases:
                errors.append(f"{name}: 锚点 {row} 是词条别名或禁用词，须用词条中文名 {aliases[concept]}")
            anchors.append(concept)
    if not anchors:
        errors.append(f"{name}: 概念及页码表没有锚点")
    dup = {a for a in anchors if anchors.count(a) > 1}
    for a in sorted(dup):
        errors.append(f"{name}: 锚点 {nid}#{a} 重复")
    if meta.get("资料类型") == "产品资料" and nid:
        check_evidence(name, nid, body, errors)
    return nid, set(anchors)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--strict", action="store_true", help="旧格式笔记也算失败")
    args = ap.parse_args()

    vocab = yaml.safe_load((NOTES / "_vocab.yaml").read_text(encoding="utf-8"))
    aliases = load_term_aliases()
    errors, legacy, index = [], [], {}
    for path in sorted(NOTES.glob("*.md")):
        if path.name.startswith("_"):
            continue
        meta, body = split_front_matter(path.read_text(encoding="utf-8"))
        if meta is None:
            legacy.append(path.name)
            continue
        nid, anchors = check_note(path, meta, body, vocab, aliases, errors)
        if nid in index:
            errors.append(f"{path.name}: 编号 {nid} 与 {index[nid][0]} 重复")
        else:
            index[nid] = (path.name, anchors)

    refs = 0
    for path in sorted(DOCS.rglob("*")):
        if path.suffix not in (".md", ".yaml", ".yml", ".json") or not path.is_file():
            continue
        if path.parent == NOTES and path.name.startswith("_"):
            continue
        if "generated" in path.parts:
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for m in REF_RE.finditer(text):
            nid, concept = m.group(1), m.group(2)
            if nid not in index:
                continue
            refs += 1
            if concept not in index[nid][1]:
                line = text.count("\n", 0, m.start()) + 1
                errors.append(f"{path.relative_to(DOCS)}:{line}: 断链 {nid}#{concept}")

    print(f"新格式笔记 {len(index)} 份，锚点 {sum(len(a) for _, a in index.values())} 个，引用 {refs} 处；旧格式待迁移 {len(legacy)} 份")
    if args.strict and legacy:
        errors.extend(f"{n}: 旧格式，未按 _TEMPLATE.md 迁移" for n in legacy)
    for e in errors:
        print(f"ERROR {e}")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
