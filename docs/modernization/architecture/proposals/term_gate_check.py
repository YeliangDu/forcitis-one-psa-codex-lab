"""架构用语门禁检查：架构产物里的名字逐个对照 terms.yaml（用户决定 2026-10-16：架构产物只用规范后的用语，先规范、用户认可、再登记）。

用法：python3 -I term_gate_check.py [--enforce] [--out 报告.md] [--catalog 流程目录.yaml] [--self-test]
默认只出报告、退出码 0；--enforce 在有违规时退出 1（架构定稿、SSOT 合入 main 前改用）。

检查对象与方式
  精确名（须是词条中文名，或 bcs.yaml 某 bc 的 words）：信息图对象名、能力名去掉结尾「管理」、聚合稿 §2.2 的聚合名、命令名、事件名，信息图「关键状态」拆出的状态名
  短语名（只查方言与外部系统用语）：价值流名、阶段名、流程目录里的流程名
判定（同一名字只报一种，自上而下取第一种）
  外部系统  名字含 bcs.yaml 中 kind: system 的名称（中文或英文），领域对象不得出现外部系统用语
  方言      名字等于（精确名）或含有（短语名，先去掉已登记词条再查）词条 aliases 里 type 为 dialect 的叫法，或词条 forbidden 里的写法
  弃用词    名字是已弃用词条（status: deprecated）
  acl 词    名字只对应 scope 为 acl 的词条（防腐层用语，不进领域）
  待确认    名字等于 type 为 undetermined 的叫法
  未登记    精确名既不是正式词条、也不是 bcs words；若在 backend/glossary/proposed/*.yaml 有提议词条，记为「提议中」（计入通过，另列）
  kind 不符 提示项，不计违规：词条存在，但 kind 与名字的用途不符（命令名对应 command、事件名对应 domain_event、聚合名对应 aggregate）
输入缺失时明确写「未检查」，不静默跳过；--enforce 时输入缺失也算违规。
"""
import argparse
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent
MOD = HERE.parent.parent
REPO = MOD.parent.parent
TERMS = MOD / "glossary" / "terms.yaml"
BCS = MOD / "glossary" / "bcs.yaml"
PROPOSED = REPO / "backend" / "glossary" / "proposed"
IM_FILE = HERE / "ENTERPRISE_INFO_MAP_DRAFT.md"
AG_FILE = HERE / "ENTERPRISE_AGGREGATES_DRAFT.md"
VS_FILE = HERE / "value-streams.v2.yaml"
CAP_FILE = HERE / "capabilities.v2.yaml"
CATALOG = HERE / "closeout" / "process-catalog.yaml"
KIND_FOR = {"command": ("command",), "event": ("domain_event",), "aggregate": ("aggregate",)}
ORDER = ("外部系统", "方言", "弃用词", "acl 词", "待确认", "未登记")
STATE_MAX = 8


def strip_note(s):
    return re.sub(r"[（(].*", "", s).strip()


def split_top(s, seps="；;"):
    """按分隔符切分，括号内的分隔符不切。"""
    out, depth, cur = [], 0, ""
    for ch in s:
        if ch in "（(":
            depth += 1
        elif ch in "）)":
            depth = max(0, depth - 1)
        if ch in seps and depth == 0:
            out.append(cur)
            cur = ""
        else:
            cur += ch
    return out + [cur]


class Vocab:
    def __init__(self, terms, bcs, proposed=()):
        self.formal, self.deprecated, self.acl, self.kinds = set(), set(), set(), defaultdict(set)
        self.dialect, self.undetermined, self.systems, self.proposed = {}, set(), {}, {t["zh"] for t in proposed if t.get("zh")}
        for t in terms:
            zh = t.get("zh")
            if not zh:
                continue
            if t.get("status") == "deprecated":
                self.deprecated.add(zh)
            elif t.get("scope") == "acl":
                self.acl.add(zh)
            else:
                self.formal.add(zh)
                self.kinds[zh].add(t.get("kind"))
            for a in t.get("aliases") or []:
                if isinstance(a, dict) and a.get("type") == "dialect" and a.get("name"):
                    self.dialect[a["name"]] = (zh, t.get("bc"))
                elif isinstance(a, dict) and a.get("type") == "undetermined" and a.get("name"):
                    self.undetermined.add(a["name"])
            for f in t.get("forbidden") or []:
                self.dialect[f] = (zh, t.get("bc"))
        self.words = set()
        for b in bcs:
            if b.get("kind") == "system":
                for k in ("zh", "en"):
                    if b.get(k):
                        self.systems[b[k]] = b["zh"]
            self.words.update(b.get("words") or [])
        for zh in self.formal:
            self.dialect.pop(zh, None)
        self.known = sorted(self.formal | self.words, key=len, reverse=True)

    def bc_note(self, alias):
        return f"（词条属 {self.dialect[alias][1]}，词条按 bc 分立，跨 bc 同名时人工判）"

    def system_hit(self, name):
        for s in sorted(self.systems, key=len, reverse=True):
            if (s.isascii() and re.search(rf"(?<![A-Za-z]){re.escape(s)}(?![A-Za-z])", name, re.I)) or (not s.isascii() and s in name):
                return s
        return None

    def classify(self, name, exact, kind=None):
        """(类别, 说明)；通过返回 (None, None)，提议中返回 ('提议中', ...)。"""
        hit = self.system_hit(name)
        if hit:
            return "外部系统", f"含外部系统用语「{hit}」"
        if exact:
            if name in self.dialect:
                return "方言", f"方言，应作「{self.dialect[name][0]}」{self.bc_note(name)}"
            if name in self.deprecated:
                return "弃用词", "已弃用词条"
            if name in self.undetermined:
                return "待确认", "叫法待确认（undetermined）"
            if name in self.formal:
                if kind in KIND_FOR and not self.kinds[name] & set(KIND_FOR[kind]):
                    return "kind 不符", f"词条 kind 为 {'、'.join(sorted(map(str, self.kinds[name])))}，用作{ {'command': '命令', 'event': '事件', 'aggregate': '聚合'}[kind] }名"
                return None, None
            if name in self.words:
                return None, None
            if name in self.acl:
                return "acl 词", "只有防腐层（acl）词条，不进领域"
            if name in self.proposed:
                return "提议中", "提议术语，待 one-glossary 登记"
            return "未登记", "terms.yaml 与 bcs words 均无此名"
        rest = name
        for w in self.known:
            rest = rest.replace(w, "·")
        for d in sorted(self.dialect, key=len, reverse=True):
            if len(d) >= 2 and d in rest:
                return "方言", f"含方言「{d}」，应作「{self.dialect[d][0]}」{self.bc_note(d)}"
        return None, None


def parse_info_map(text):
    """[(OBJ 编号, 对象名, [状态名])]；级别为「非对象」的行不算。"""
    rows = []
    for line in text.splitlines():
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) < 6 or not re.fullmatch(r"OBJ-\d+", cells[0]) or "非对象" in cells[3]:
            continue
        states = []
        for tok in re.split(r"[→／/；;、，,]", re.sub(r"[（(][^）)]*[）)]", "", cells[5])):
            tok = tok.strip().strip("「」\"'` ")
            if re.fullmatch(r"[一-鿿]{2,%d}" % STATE_MAX, tok) and tok != "无":
                states.append(tok)
        rows.append((cells[0], strip_note(cells[1]), list(dict.fromkeys(states))))
    return rows


def parse_aggregates(text):
    """[(AGG 编号, 聚合名, [命令名], [事件名])]：取 §2.2 聚合表行（编号以 AGG- 开头）。"""
    rows = []
    for line in text.splitlines():
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) < 7 or not re.fullmatch(r"AGG-\d+", cells[0]):
            continue
        cmds, evs = [], []
        for item in split_top(cells[5]):
            item = item.strip()
            if not item or item in ("—", "-"):
                continue
            name = strip_note(item)
            if name:
                cmds.append(name)
            m = re.search(r"[（(]\s*→\s*([^）)；;]*)", item)
            if m:
                for e in re.split(r"[、,，]", m.group(1)):
                    e = e.strip()
                    if e and not e.startswith(("（", "(")):
                        evs.append(e)
        for e in re.split(r"[、]", re.sub(r"[（(][^）)]*[）)]", "", cells[6])):
            if e.strip() and e.strip() not in ("—", "-"):
                evs.append(e.strip())
        rows.append((cells[0], strip_note(cells[1]), list(dict.fromkeys(cmds)), list(dict.fromkeys(evs))))
    return rows


def collect(im_text, ag_text, vs_doc, cap_doc, catalog_doc):
    """[(来源, 项类, 编号, 名字, 是否精确名, kind)]"""
    items = []
    for oid, name, states in parse_info_map(im_text or ""):
        items.append(("信息图", "对象", oid, name, True, None))
        items += [("信息图", "状态", oid, s, True, None) for s in states]
    for aid, name, cmds, evs in parse_aggregates(ag_text or ""):
        items.append(("聚合稿", "聚合", aid, name, True, "aggregate"))
        items += [("聚合稿", "命令", aid, c, True, "command") for c in cmds]
        items += [("聚合稿", "事件", aid, e, True, "event") for e in evs]
    for c in (cap_doc or {}).get("capabilities") or []:
        for x in [c] + list(c.get("l2") or []):
            n = str(x.get("name", ""))
            items.append(("能力", "能力", x.get("id", ""), n[:-2] if n.endswith("管理") else n, True, None))
    for vs in (vs_doc or {}).get("value_streams") or []:
        items.append(("价值流", "价值流", vs.get("id", ""), str(vs.get("name", "")), False, None))
        for s in vs.get("stages") or []:
            items.append(("价值流", "阶段", s.get("id", ""), str(s.get("name", "")), False, None))
    for p in (catalog_doc or {}).get("processes") or []:
        if p.get("type") == "merged" or p.get("status") == "retired" or p.get("split_into"):
            continue
        items.append(("流程目录", "流程", p.get("id", ""), str(p.get("name", "")), False, None))
    return items


def run(vocab, items):
    found = []
    for src, kind_, ident, name, exact, k in items:
        cat, why = vocab.classify(name, exact, k)
        if cat:
            found.append((cat, src, kind_, ident, name, why))
    return found


def load_yaml(path):
    return yaml.safe_load(path.read_text(encoding="utf-8")) if path.exists() else None


def load_text(path):
    return path.read_text(encoding="utf-8") if path.exists() else None


def render(items, found, missing):
    violations = [f for f in found if f[0] in ORDER]
    lines = ["# 架构用语门禁报告（term_gate_check.py 生成）", ""]
    lines += [f"- **未检查（输入缺失）**：{m}" for m in missing] or []
    total = Counter((i[0], i[1]) for i in items)
    lines += ["", f"检查名字 {len(items)} 个（去重后 {len({(i[3], i[4]) for i in items})} 个），违规 {len(violations)} 条。", "",
              "| 来源 | 项 | 检查数 | " + " | ".join(ORDER) + " | 提议中 | kind 不符 |", "|---|---|---|" + "---|" * (len(ORDER) + 2)]
    by = Counter((f[1], f[2], f[0]) for f in found)
    for (src, kd), n in sorted(total.items()):
        lines.append(f"| {src} | {kd} | {n} | " + " | ".join(str(by.get((src, kd, c), 0)) for c in ORDER + ("提议中", "kind 不符")) + " |")
    for cat in ORDER + ("提议中", "kind 不符"):
        rows = [f for f in found if f[0] == cat]
        if not rows:
            continue
        lines += ["", f"## {cat}（{len(rows)}）" + ("，提示项不计违规" if cat in ("提议中", "kind 不符") else ""), ""]
        uniq = defaultdict(list)
        for _, src, kd, ident, name, why in rows:
            uniq[(src, kd, name, why)].append(ident)
        for (src, kd, name, why), ids in sorted(uniq.items()):
            lines.append(f"- {src}·{kd}「{name}」{why}（{', '.join(ids[:6])}{'…共 %d 处' % len(ids) if len(ids) > 6 else ''}）")
    return "\n".join(lines) + "\n"


def self_test():
    terms = [{"zh": "往来单位", "kind": "aggregate", "scope": "domain", "status": "approved", "aliases": [{"name": "客商", "type": "dialect"}, {"name": "待定叫法", "type": "undetermined"}], "forbidden": ["客户供应商"]},
             {"zh": "登记往来单位", "kind": "command", "scope": "domain", "status": "approved"},
             {"zh": "往来单位已登记", "kind": "domain_event", "scope": "domain", "status": "approved"},
             {"zh": "旧词", "kind": "entity", "scope": "domain", "status": "deprecated"},
             {"zh": "致远流程", "kind": "entity", "scope": "acl", "status": "approved"},
             {"zh": "合同变更", "kind": "entity", "scope": "domain", "status": "approved"},
             {"zh": "变更单", "kind": "entity", "scope": "domain", "status": "approved", "aliases": [{"name": "变更", "type": "dialect"}]}]
    bcs = [{"id": "x", "zh": "x", "kind": "bc", "words": ["通用用语"]}, {"id": "seeyon", "zh": "致远", "en": "Seeyon", "kind": "system"}]
    v = Vocab(terms, bcs, [{"zh": "提议词"}])
    cases = [("往来单位", True, None, None), ("客商", True, None, "方言"), ("客户供应商", True, None, "方言"), ("旧词", True, None, "弃用词"), ("待定叫法", True, None, "待确认"),
             ("没登记的名", True, None, "未登记"), ("提议词", True, None, "提议中"), ("通用用语", True, None, None), ("致远流程", True, None, "外部系统"),
             ("往来单位", True, "command", "kind 不符"), ("登记往来单位", True, "command", None), ("往来单位已登记", True, "event", None),
             ("往来单位已登记", True, "command", "kind 不符"), ("核对客商档案", False, None, "方言"), ("核对往来单位档案", False, None, None), ("提交合同变更", False, None, None), ("提交变更", False, None, "方言"),
             ("同步 Seeyon 组织", False, None, "外部系统")]
    for name, exact, kind, want in cases:
        got = v.classify(name, exact, kind)[0]
        assert got == want, (name, got, want)
    assert Vocab([], [], []).classify("任意名", True)[0] == "未登记"
    im = "| OBJ-01 | 往来单位（含上级） | 定义 | 一级 | 类型 | 待核实 → 有效（档案已核实）→ 已合并 / 停用；档案按 D17 版本化 | 关系 | 规则 | 事实 | 来源 | 词表 | 范围 |\n| OBJ-02 | 备注 | 定义 | 非对象 | 类型 | 有效 | 关系 | 规则 | 事实 | 来源 | 词表 | 范围 |"
    assert parse_info_map(im) == [("OBJ-01", "往来单位", ["待核实", "有效", "已合并", "停用"])], parse_info_map(im)
    assert parse_info_map(im.replace("版本化", "版本化（D-8、PR #3）"))[0][2] == ["待核实", "有效", "已合并", "停用"]
    ag = "| AGG-01 | 往来单位（OBJ-01） | 内含 | 次级在内 | INV-01 | 登记往来单位（→ 往来单位已登记）；停用往来单位（→ （内部））；核准（→ 甲已核准、乙已核准；前置 INV-02） | 往来单位已登记（仅分析）、甲已核准（订阅方） | 依赖 |"
    assert parse_aggregates(ag) == [("AGG-01", "往来单位", ["登记往来单位", "停用往来单位", "核准"], ["往来单位已登记", "甲已核准", "乙已核准"])], parse_aggregates(ag)
    items = collect(im, ag, {"value_streams": [{"id": "VS-01", "name": "获得客商", "stages": [{"id": "VS-01.1", "name": "启动"}]}]},
                    {"capabilities": [{"id": "CAP-001", "name": "往来单位管理"}]}, {"processes": [{"id": "PRC-01", "name": "举办", "type": "merged"}, {"id": "PRC-02", "name": "核对客商"}]})
    found = {(f[0], f[2], f[4]) for f in run(v, items)}
    assert ("方言", "价值流", "获得客商") in found and ("方言", "流程", "核对客商") in found and ("未登记", "命令", "核准") in found
    assert ("未登记", "状态", "待核实") in found and not any(f[4] == "举办" for f in run(v, items)) and not any(f[4] == "往来单位" and f[0] == "未登记" for f in run(v, items))
    assert "违规 " in render(items, run(v, items), ["x"]) and "未检查" in render(items, [], ["x"])
    print("self-test ok")


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--enforce", action="store_true")
    ap.add_argument("--out")
    ap.add_argument("--catalog", default=str(CATALOG))
    ap.add_argument("--self-test", action="store_true")
    args = ap.parse_args()
    if args.self_test:
        return self_test()
    proposed = []
    for f in sorted(PROPOSED.glob("*.yaml")):
        proposed += (load_yaml(f) or {}).get("terms") or []
    terms, bcs = load_yaml(TERMS), load_yaml(BCS)
    if not terms or not bcs:
        sys.exit(f"词表缺失：{TERMS}、{BCS} 必须存在")
    vocab = Vocab(terms["terms"], bcs["bcs"], proposed)
    catalog = Path(args.catalog)
    inputs = {"信息图": load_text(IM_FILE), "聚合稿": load_text(AG_FILE), "价值流": load_yaml(VS_FILE), "能力": load_yaml(CAP_FILE), "流程目录": load_yaml(catalog)}
    missing = [f"{k}（{p}）" for k, p in (("信息图", IM_FILE), ("聚合稿", AG_FILE), ("价值流", VS_FILE), ("能力", CAP_FILE), ("流程目录", catalog)) if inputs[k] is None]
    items = collect(inputs["信息图"], inputs["聚合稿"], inputs["价值流"], inputs["能力"], inputs["流程目录"])
    found = run(vocab, items)
    report = render(items, found, missing)
    if args.out:
        Path(args.out).write_text(report, encoding="utf-8")
    else:
        sys.stdout.write(report)
    if args.enforce and (missing or any(f[0] in ORDER for f in found)):
        sys.exit(1)


if __name__ == "__main__":
    main()
