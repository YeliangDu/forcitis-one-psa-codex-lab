#!/usr/bin/env python3
"""资料笔记格式与锚点引用检查。

新格式笔记（带 YAML 信息栏）逐项校验；旧格式笔记只计数为待迁移，--strict 时也算失败。
锚点定义在四节内表格首列，写作 `编号#概念名`，概念名只用汉字、字母、数字、`-`、`·`；命中 terms.yaml 词条别名或禁用词时须改用词条中文名。
产品资料用证据号（E01 式）代替页码时，证据号须在 notes/_evidence/<编号>.yaml 登记且有路径与定位。
笔记里引用的 VS-、阶段、PRC-、OBJ- 编号须存在于全景模型 panorama.yaml（价值流以 v2 为准）；编号后紧跟的名称若是同类另一节点的名称，判为编号与名称不符。
全仓 docs/modernization 下（剔除代码块、行内代码与 URL 后）形如 `编号#概念名` 的引用：编号属已登记笔记时须能解析到锚点，否则告警。
"""
import argparse
import re
import sys
import unicodedata
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
ID = r"[A-Z]{2,6}[0-9]{0,4}"
ID_RE = re.compile(rf"^{ID}$")
CONCEPT = rf"(?:(?!{ID}#)[一-鿿A-Za-z0-9\-·])+"
CONCEPT_RE = re.compile(rf"^{CONCEPT}$")
REF_RE = re.compile(rf"(?<![A-Za-z0-9])({ID})#({CONCEPT})")
ANCHOR_CELL_RE = re.compile(rf"^({ID})#(.+)$")
PANORAMA = HERE.parent / "panorama" / "panorama.yaml"
INFO_MAP = HERE.parent / "proposals" / "ENTERPRISE_INFO_MAP_DRAFT.md"
SYS_REF_RE = re.compile(r"(?<![A-Za-z0-9-])(VS-\d{2}(?:\.\d+)?|PRC-\d{2,3}|OBJ-\d{2,3})(?![\d.])(?:\s*[「“（(]?\s*)([\u4e00-\u9fff]{2,12})?")
FENCE_RE = re.compile(r"^(```|~~~).*?^\1[^\n]*$", re.M | re.S)
NOISE_RE = re.compile(r"`[^`\n]*`|https?://\S+")


def strip_fences(text):
    return FENCE_RE.sub(lambda m: "\n" * m.group(0).count("\n"), text)


def strip_code(text):
    return NOISE_RE.sub("", strip_fences(text))


def split_front_matter(text):
    """返回 (meta, body)；无信息栏时 meta 为 None；信息栏有误时 meta 含 _error。"""
    if not text.strip():
        return {"_error": "文件为空"}, ""
    if not text.startswith("---\n"):
        return None, text
    end = text.find("\n---\n", 4)
    if end < 0:
        return {"_error": "信息栏缺结束行 ---"}, text
    try:
        meta = yaml.safe_load(text[4:end]) or {}
    except yaml.YAMLError as exc:
        return {"_error": f"信息栏不是合法 YAML（{str(exc).splitlines()[0]}），含冒号的值须加引号"}, text[end + 5:]
    if not isinstance(meta, dict):
        return {"_error": "信息栏须为「键: 值」映射"}, text[end + 5:]
    return meta, text[end + 5:]


def cells(line):
    return [c.strip() for c in line.strip().strip("|").split("|")]


def anchor_rows(body):
    """四节内（附节之前、代码块之外）首列恰为一个 编号#… 的表格行：[(编号, 概念名, 各列, 表序号)]。
    首列含多个锚点（以「、」或空格分隔）的是引用，不是定义；同一锚点可在不同表中各出现一次。"""
    scope = re.split(r"^## 附：", strip_fences(body), maxsplit=1, flags=re.M)[0]
    rows, table, in_table = [], 0, False
    for line in scope.splitlines():
        if not line.lstrip().startswith("|"):
            in_table = False
            continue
        if not in_table:
            table, in_table = table + 1, True
        c = cells(line)
        m = ANCHOR_CELL_RE.match(c[0]) if c else None
        if m and len(REF_RE.findall(c[0])) <= 1 and not re.search(r"[、，\s]", m.group(2)):
            rows.append((m.group(1), m.group(2), c, table))
    return rows


def norm(s):
    return unicodedata.normalize("NFKC", s).casefold().strip()


def load_term_aliases():
    zh_names, alias_to_zh = set(), {}
    for term in yaml.safe_load(TERMS.read_text(encoding="utf-8"))["terms"]:
        zh_names.add(norm(term["zh"]))
        names = [a["name"] for a in term.get("aliases") or [] if isinstance(a, dict) and a.get("name")]
        names += [f for f in term.get("forbidden") or [] if isinstance(f, str)]
        for name in names:
            for part in re.split(r"\s*/\s*", name):
                if part.strip():
                    alias_to_zh.setdefault(norm(part), term["zh"])
    return {a: z for a, z in alias_to_zh.items() if a not in zh_names}


def check_evidence(name, nid, body, errors, warnings):
    used = sorted(set(EVID_RE.findall(strip_code(body))))
    reg_path = EVIDENCE / f"{nid}.yaml"
    if not used and not reg_path.exists():
        return
    if not reg_path.exists():
        errors.append(f"{name}: 用了证据号却没有证据登记 _evidence/{nid}.yaml")
        return
    try:
        data = yaml.safe_load(reg_path.read_text(encoding="utf-8")) or {}
    except yaml.YAMLError as exc:
        errors.append(f"_evidence/{nid}.yaml: 不是合法 YAML（{str(exc).splitlines()[0]}）")
        return
    reg = data.get("证据") if isinstance(data, dict) else None
    if not isinstance(reg, dict):
        errors.append(f"_evidence/{nid}.yaml: 须有映射「证据: {{E01: {{路径: …, 定位: …}}}}」")
        return
    for eid, entry in reg.items():
        if not isinstance(entry, dict) or not entry.get("路径") or not entry.get("定位"):
            errors.append(f"_evidence/{nid}.yaml: {eid} 缺路径或定位")
    for eid in used:
        if eid not in reg:
            errors.append(f"{name}: 证据号 {eid} 未在 _evidence/{nid}.yaml 登记")
    for eid in sorted(set(reg) - set(used)):
        warnings.append(f"_evidence/{nid}.yaml: {eid} 已登记但笔记未用")


THREE_PART_ENFORCED = True


def check_core(name, nid, body, sink):
    """核心思想每条须有「论断」「依据：」（至少一个本笔记锚点）「含义：」三段。"""
    m = re.search(r"^## 核心思想\s*$(.*?)(?=^## )", strip_fences(body), re.M | re.S)
    items = re.findall(r"^- (.+(?:\n(?!- |\n).+)*)", m.group(1), re.M) if m else []
    if not items:
        sink.append(f"{name}: 核心思想没有条目")
    for i, item in enumerate(items, 1):
        missing = [k for k in ("论断", "依据：", "含义：") if k not in item]
        if "依据：" in item and not re.search(rf"{nid}#", item.split("依据：", 1)[1]):
            missing.append("依据中的本笔记锚点")
        if missing:
            sink.append(f"{name}: 核心思想第 {i} 条缺 {'、'.join(missing)}（格式见 _TEMPLATE.md）")


def load_system():
    """全景模型的价值流、阶段、流程、对象（编号 → 名称），以及信息图上所有对象行（含已非对象）。"""
    if not PANORAMA.exists():
        return None
    nodes = yaml.safe_load(PANORAMA.read_text(encoding="utf-8"))["nodes"]
    sysmap = {}
    for kind in ("vs", "stage", "prc", "obj"):
        for k, v in nodes[kind].items():
            sysmap[k] = (kind, v.get("name", ""), v.get("type"))
    all_obj = set(re.findall(r"^\|\s*\**(OBJ-\d{2,3})", INFO_MAP.read_text(encoding="utf-8"), re.M))
    names = {}
    for k, (kind, n, _) in sysmap.items():
        names.setdefault((kind, n[:4]), set()).add(k)
    return sysmap, all_obj, names


def missing_system_error(system):
    """全景模型不入仓：先由 panorama_build.py 生成（build_all 里它排在笔记检查之前）；缺了不能静默跳过编号校验。"""
    if system is None:
        return f"全景模型 {PANORAMA.name} 不存在：先运行 architecture/panorama/panorama_build.py（或 build_all.py），笔记里的 VS/PRC/OBJ 编号无从校验"
    return None


def check_system_refs(name, body, system, errors, warnings):
    sysmap, all_obj, names = system
    for m in SYS_REF_RE.finditer(strip_code(body)):
        ref, follow = m.group(1), m.group(2)
        if ref not in sysmap:
            if ref.startswith("OBJ-") and ref in all_obj:
                warnings.append(f"{name}: {ref} 在信息图上已非对象")
            else:
                errors.append(f"{name}: 引用的 {ref} 不存在（价值流与阶段以 proposals/value-streams.v2.yaml 为准）")
            continue
        kind, real, typ = sysmap[ref]
        if typ in ("retired", "merged"):
            warnings.append(f"{name}: {ref} 已{'退役' if typ == 'retired' else '合并'}")
        bigrams = lambda s: {s[i:i + 2] for i in range(len(s) - 1)}
        if follow and real and not (bigrams(follow) & bigrams(real)) and not (set(follow[:4]) >= set(real[:2])):
            other = names.get((kind, follow[:4]), set()) - {ref}
            if other:
                errors.append(f"{name}: {ref} 后写「{follow}」，实为「{real}」；该名称属于 {'、'.join(sorted(other))}")
            elif kind in ("vs", "stage") and len(follow) >= 4 and not ({follow[i:i + 2] for i in range(len(follow) - 1)} & {real[i:i + 2] for i in range(len(real) - 1)}):
                warnings.append(f"{name}: {ref} 后写「{follow}」，与实名「{real}」无一处相同（疑似旧版编号，价值流以 v2 为准）")


def check_note(path, meta, body, vocab, aliases, errors, warnings):
    name = path.name
    if "_error" in meta:
        errors.append(f"{name}: {meta['_error']}")
        return None, set()
    for key in REQUIRED:
        if meta.get(key) in (None, "", []):
            errors.append(f"{name}: 信息栏缺 {key}")
    nid = str(meta.get("编号") or "")
    if not ID_RE.match(nid):
        errors.append(f"{name}: 编号「{nid}」须为大写字母 2-6 位加可选数字；本笔记其余检查跳过")
        return None, set()
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

    heads = re.findall(r"^## (.+?)\s*$", strip_code(body), re.M)
    main = [h for h in heads if not h.startswith("附：")]
    if main != SECTIONS:
        errors.append(f"{name}: 二级标题须依次为 {'、'.join(SECTIONS)}（附节以「附：」开头），实为 {'、'.join(main)}")
    if any(h.startswith("附：") for h in heads[: len(main)]):
        errors.append(f"{name}: 附节须在四节之后")

    check_core(name, nid, body, warnings if not THREE_PART_ENFORCED else errors)
    anchors, per_table = [], []
    for prefix, concept, _, table in anchor_rows(body):
        row = f"{prefix}#{concept}"
        if prefix != nid:
            errors.append(f"{name}: 锚点 {row} 的前缀与编号 {nid} 不符")
            continue
        if not CONCEPT_RE.match(concept):
            errors.append(f"{name}: 锚点 {row} 的概念名只能用汉字、字母、数字、-、·")
            continue
        if norm(concept) in aliases:
            errors.append(f"{name}: 锚点 {row} 是词条别名或禁用词，须用词条中文名 {aliases[norm(concept)]}")
        anchors.append(concept)
        per_table.append((table, concept))
    if not anchors:
        errors.append(f"{name}: 没有锚点（四节内表格首列 编号#概念名）")
    for table, a in sorted({x for x in per_table if per_table.count(x) > 1}):
        errors.append(f"{name}: 锚点 {nid}#{a} 在同一张表内重复")
    if meta.get("资料类型") == "产品资料":
        check_evidence(name, nid, body, errors, warnings)
    return nid, set(anchors)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--strict", action="store_true", help="旧格式笔记也算失败")
    args = ap.parse_args()

    vocab = yaml.safe_load((NOTES / "_vocab.yaml").read_text(encoding="utf-8"))
    aliases = load_term_aliases()
    system = load_system()
    errors, warnings, legacy, index = [], [], [], {}
    if (problem := missing_system_error(system)):
        errors.append(problem)
    for path in sorted(NOTES.glob("*.md")):
        if path.name.startswith("_"):
            continue
        meta, body = split_front_matter(path.read_text(encoding="utf-8"))
        if meta is None:
            legacy.append(path.name)
            continue
        nid, anchors = check_note(path, meta, body, vocab, aliases, errors, warnings)
        if system:
            check_system_refs(path.name, body, system, errors, warnings)
        if nid is None:
            continue
        if nid in index:
            errors.append(f"{path.name}: 编号 {nid} 与 {index[nid][0]} 重复")
        else:
            index[nid] = (path.name, anchors)

    refs, unknown = 0, {}
    for path in sorted(DOCS.rglob("*")):
        if path.suffix not in (".md", ".yaml", ".yml", ".json") or not path.is_file():
            continue
        if (path.parent == NOTES and path.name.startswith("_")) or "generated" in path.parts:
            continue
        text = strip_code(path.read_text(encoding="utf-8", errors="replace"))
        for m in REF_RE.finditer(text):
            nid, concept = m.group(1), m.group(2)
            if nid not in index:
                unknown.setdefault(nid, f"{path.relative_to(DOCS)}:{text.count(chr(10), 0, m.start()) + 1}")
                continue
            refs += 1
            if concept not in index[nid][1] and not any(concept.startswith(a) for a in index[nid][1]):
                line = text.count("\n", 0, m.start()) + 1
                hint = "（两条引用之间须用「、」或空格分隔）" if re.match(ID + "#", text[m.end():]) else ""
                errors.append(f"{path.relative_to(DOCS)}:{line}: 断链 {nid}#{concept}{hint}")
    for nid, where in sorted(unknown.items()):
        warnings.append(f"{where}: {nid}# 不是已登记笔记编号（拼错或尚未迁移？）")

    print(f"新格式笔记 {len(index)} 份，锚点 {sum(len(a) for _, a in index.values())} 个，引用 {refs} 处；旧格式待迁移 {len(legacy)} 份")
    if args.strict and legacy:
        errors.extend(f"{n}: 旧格式，未按 _TEMPLATE.md 迁移" for n in legacy)
    for w in warnings:
        print(f"WARN {w}")
    for e in errors:
        print(f"ERROR {e}")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
