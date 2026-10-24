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
CATALOG = HERE.parent / "ssot" / "09-processes" / "catalog.yaml"   # 流程目录正本
BC_MAP = HERE / "closeout" / "bc-map.yaml"
KIND_FOR = {"command": ("command",), "event": ("domain_event",), "aggregate": ("aggregate",)}
ORDER = ("外部系统", "方言", "弃用词", "acl 词", "待确认", "未登记")
HINTS = ("提议中", "kind 不符", "跨 bc 待判", "跨 bc 同名")
KIND_ZH = {"command": "命令", "event": "事件", "aggregate": "聚合"}
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


def catalog_systems(doc):
    """流程目录 external_system 字段里的系统名（自由文本：「A8（致远协同）」「老系统、A8」），补 bcs.yaml 登记不全的缺口。"""
    out = set()
    for p in (doc or {}).get("processes") or []:
        v = p.get("external_system")
        for raw in ([v] if isinstance(v, str) else v or []):
            for part in split_top(str(raw), "、,，"):
                n = strip_note(part)
                if n and n not in ("无", "—"):
                    out.add(n)
                    out.update(x for x in re.findall(r"[（(]([^）)]{2,8})[）)]", part) if not re.search(r"[，。；：、]|只读|按", x))
    return out


class Vocab:
    def __init__(self, terms, bcs, proposed=(), extra_systems=()):
        self.formal, self.acl, self.en = set(), set(), set()
        self.kinds, self.bad = defaultdict(set), defaultdict(list)
        self.systems, self.words = {}, set()
        self.proposed = {t["zh"] for t in proposed if t.get("zh")}
        self.en |= {t["en"] for t in proposed if t.get("en")}
        for t in terms:
            zh = t.get("zh")
            if not zh:
                continue
            if t.get("en"):
                self.en.add(t["en"])
            if t.get("status") == "deprecated":
                self.bad[zh].append(("弃用词", "已弃用词条" + (f"，应作「{t['replaced_by']}」" if t.get("replaced_by") else ""), t.get("bc")))
            elif t.get("scope") == "acl":
                self.acl.add(zh)
            else:
                self.formal.add(zh)
                self.kinds[zh].add(t.get("kind"))
            for a in t.get("aliases") or []:
                if isinstance(a, dict) and a.get("name") and a.get("type") == "dialect":
                    self.bad[a["name"]].append(("方言", f"应作「{zh}」", t.get("bc")))
                elif isinstance(a, dict) and a.get("name") and a.get("type") == "undetermined":
                    self.bad[a["name"]].append(("待确认", f"叫法待确认（对应「{zh}」）", t.get("bc")))
            for f in t.get("forbidden") or []:
                self.bad[f].append(("方言", f"应作「{zh}」", t.get("bc")))
        for b in bcs:
            if b.get("kind") == "system" and b.get("id") != "new_system":
                for k in ("zh", "en"):
                    if b.get(k):
                        self.systems[b[k]] = b["zh"]
            self.words.update(b.get("words") or [])
        for x in extra_systems:
            self.systems.setdefault(x, x)
        known = self.formal | self.words
        for zh in known:
            self.bad.pop(zh, None)
        self.known = sorted(known, key=len, reverse=True)

    def system_hit(self, name):
        for s in sorted(self.systems, key=len, reverse=True):
            if (s.isascii() and re.search(rf"(?<![A-Za-z]){re.escape(s)}(?![A-Za-z])", name, re.I)) or (not s.isascii() and s in name):
                return s
        return None

    def phrase_hits(self, name):
        """名字里被已登记词条完整包住的叫法放行（「现场签证」包住「签证」）；其余叫法按长到短列出。"""
        covers = [m.span() for w in self.known if w in name for m in re.finditer(re.escape(w), name)]
        hits = []
        for d in sorted(self.bad, key=len, reverse=True):
            if len(d) < 2:
                continue
            for m in re.finditer(re.escape(d), name):
                i, j = m.span()
                if not any(a <= i and j <= b and (b - a) > (j - i) for a, b in covers):
                    hits.append(d)
                    break
        return hits

    def judge(self, alias, bc, state=False):
        """某个坏叫法落在本项的 bc 上：同 bc（或 kernel 词条）才是违规；bc 已知而不同，是别的 bc 的方言，只作提示；bc 未知时状态词只作提示。"""
        entries = self.bad[alias]
        if bc is None:
            cat, why, tbc = entries[0]
            return ("跨 bc 待判", f"{cat}：{why}（词条属 {tbc}，本项所属 bc 未知）") if state else (cat, f"{why}（词条属 {tbc}）")
        same = [e for e in entries if e[2] == bc or str(e[2]).startswith("kernel")]
        if same:
            return same[0][0], f"{same[0][1]}（词条属 {same[0][2]}）"
        cat, why, tbc = entries[0]
        return "跨 bc 同名", f"{cat}：{why}（词条属 {tbc}，本项属 {bc}，不是本 bc 的叫法）"

    def classify(self, name, exact, kind=None, bc=None, exempt=False):
        """(类别, 说明)；通过返回 (None, None)。kind：aggregate / command / event / state；exempt：豁免外部系统用语（集成流程）。"""
        hit = None if exempt else self.system_hit(name)
        if hit:
            return "外部系统", f"含外部系统用语「{hit}」"
        if not exact:
            found = [self.judge(d, bc, False) for d in self.phrase_hits(name)]
            for r in found:
                if r[0] in ORDER:
                    return r
            return found[0] if found else (None, None)
        if name.isascii():
            return (None, None) if name in self.en else ("未登记", "英文名不在 terms.yaml 的 en 中")
        if name in self.formal:
            if kind in KIND_FOR and not self.kinds[name] & set(KIND_FOR[kind]):
                return "kind 不符", f"词条 kind 为 {'、'.join(sorted(map(str, self.kinds[name])))}，用作{KIND_ZH[kind]}名"
            return None, None
        if name in self.words:
            return None, None
        if name in self.bad:
            return self.judge(name, bc, kind == "state")
        if name in self.acl:
            return "acl 词", "只有防腐层（acl）词条，不进领域"
        if name in self.proposed:
            return "提议中", "提议术语，待 one-glossary 登记"
        return "未登记", "terms.yaml 与 bcs words 均无此名"


def parse_info_map(text):
    """[(OBJ 编号, 对象名, [状态名])]；级别为「非对象」的行不算。"""
    rows = []
    for line in text.splitlines():
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) < 6 or not re.fullmatch(r"OBJ-\d+", cells[0]) or re.match(r"[\s*]*非对象", cells[3]):
            continue
        states = []
        for tok in re.split(r"[→⇄／/；;、，,]", re.sub(r"[（(][^）)]*[）)]", "", cells[5])):
            tok = re.split(r"[：:]", tok)[-1].strip().strip("「」\"'` ")
            if re.fullmatch(r"[一-鿿]{2,%d}" % STATE_MAX, tok) and tok != "无":
                states.append(tok)
        rows.append((cells[0], strip_note(cells[1]), list(dict.fromkeys(states))))
    return rows


def event_names(text):
    """「角色已合格 / 已暂停」「`TimesheetSubmitted`」这类写法拆成单个事件名；「（内部）」与带说明的整段不取。"""
    out = []
    for e in re.split(r"[、,，/／]", text):
        e = re.split(r"[：:]", e)[-1].strip().strip("`「」 ")
        if e and not e.startswith(("（", "(", "已有", "—", "-")) and re.fullmatch(r"[一-鿿]{2,12}|[A-Za-z][A-Za-z0-9]*", e):
            out.append(e)
    return out


def parse_aggregates(text):
    """[(AGG/REF 编号, 聚合名, [命令名], [事件名], 所属 B-xx, 根对象 OBJ 编号)]：取 §2.2 聚合表行；B-xx 取表前最近的「**B-nn …**」小节标题。"""
    rows, cur_b = [], None
    for line in text.splitlines():
        hb = re.match(r"\*\*(B-\d+)\b", line)
        if hb:
            cur_b = hb.group(1)
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) < 7 or not re.fullmatch(r"(?:AGG|REF)-\d+", cells[0]):
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
                evs += event_names(m.group(1))
        evs += event_names(re.sub(r"[（(][^）)]*[）)]", "", cells[6]))
        om = re.search(r"OBJ-\d+", cells[1])
        rows.append((cells[0], strip_note(cells[1]), list(dict.fromkeys(cmds)), list(dict.fromkeys(evs)), cur_b, om.group(0) if om else None))
    return rows


def owner_bc(raw, bcmap):
    m = re.match(r"\s*(B-\d+)", str(raw or ""))
    if m:
        return bcmap.get(m.group(1))
    m = re.match(r"\s*([a-z]+(?:\.[a-z]+)*)\b", str(raw or ""))
    return m.group(1) if m else None


def collect(im_text, ag_text, vs_doc, cap_doc, catalog_doc, bcmap=None):
    """[(来源, 项类, 编号, 名字, 是否精确名, kind, bc, 豁免外部系统)]；bc 为词表 bc，查不到为 None。"""
    bcmap = bcmap or {}
    items, ags = [], parse_aggregates(ag_text or "")
    obj_bc = {obj: bcmap.get(b) for _, _, _, _, b, obj in ags if obj}
    for oid, name, states in parse_info_map(im_text or ""):
        items.append(("信息图", "对象", oid, name, True, None, obj_bc.get(oid), False))
        items += [("信息图", "状态", oid, s, True, "state", obj_bc.get(oid), False) for s in states]
    for aid, name, cmds, evs, b, _ in ags:
        bc = bcmap.get(b)
        items.append(("聚合稿", "聚合" if aid.startswith("AGG") else "外部引用", aid, name, True, "aggregate" if aid.startswith("AGG") else None, bc, False))
        items += [("聚合稿", "命令", aid, c, True, "command", bc, False) for c in cmds]
        items += [("聚合稿", "事件", aid, e, True, "event", bc, False) for e in evs]
    for c in (cap_doc or {}).get("capabilities") or []:
        for x in [c] + list(c.get("l2") or []):
            n = str(x.get("name", ""))
            items.append(("能力", "能力", x.get("id", ""), n[:-2] if n.endswith("管理") else n, True, None, obj_bc.get(x.get("object")), False))
    for vs in (vs_doc or {}).get("value_streams") or []:
        items.append(("价值流", "价值流", vs.get("id", ""), str(vs.get("name", "")), False, None, None, False))
        for s in vs.get("stages") or []:
            items.append(("价值流", "阶段", s.get("id", ""), str(s.get("name", "")), False, None, None, False))
    for p in (catalog_doc or {}).get("processes") or []:
        if p.get("type") == "merged" or p.get("status") == "retired" or p.get("split_into"):
            continue
        items.append(("流程目录", "流程", p.get("id", ""), str(p.get("name", "")), False, None, owner_bc(p.get("owner_bc"), bcmap), p.get("type") == "integration"))
    return items


def bc_map_gaps(doc, path):
    if doc is None:
        return [f"bc 映射（{path} 不存在，无法按 bc 判方言，只能按「bc 未知」处理）"]
    if not (doc.get("map") or []):
        return [f"bc 映射（{path} 存在但没有 map 条目）"]
    return [f"bc 映射：{m['b']} {m['bc']} 未在 bcs.yaml 登记（in_bcs_yaml: false，待 one-glossary）" for m in doc["map"] if not m.get("in_bcs_yaml", True)]


def gaps(inputs, items, paths):
    """输入文件不存在，或存在却一项都没解析出来（改了表头、改了顶层键），都算未检查。"""
    have = Counter(i[0] for i in items)
    out = []
    for k, path in paths.items():
        if inputs[k] is None:
            out.append(f"{k}（{path} 不存在）")
        elif not have[k]:
            out.append(f"{k}（{path} 存在但一项都没解析出来，格式可能变了）")
    return out


def run(vocab, items):
    found = []
    for src, kind_, ident, name, exact, k, bc, exempt in items:
        cat, why = vocab.classify(name, exact, k, bc, exempt)
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
              "| 来源 | 项 | 检查数 | " + " | ".join(ORDER) + " | " + " | ".join(HINTS) + " |", "|---|---|---|" + "---|" * (len(ORDER) + len(HINTS))]
    by = Counter((f[1], f[2], f[0]) for f in found)
    for (src, kd), n in sorted(total.items()):
        lines.append(f"| {src} | {kd} | {n} | " + " | ".join(str(by.get((src, kd, c), 0)) for c in ORDER + HINTS) + " |")
    for cat in ORDER + HINTS:
        rows = [f for f in found if f[0] == cat]
        if not rows:
            continue
        lines += ["", f"## {cat}（{len(rows)}）" + ("，提示项不计违规" if cat in HINTS else ""), ""]
        uniq = defaultdict(list)
        for _, src, kd, ident, name, why in rows:
            uniq[(src, kd, name, why)].append(ident)
        for (src, kd, name, why), ids in sorted(uniq.items()):
            lines.append(f"- {src}·{kd}「{name}」{why}（{', '.join(ids[:6])}{'…共 %d 处' % len(ids) if len(ids) > 6 else ''}）")
    return "\n".join(lines) + "\n"


def self_test():
    T = lambda zh, kind="entity", **kw: {"zh": zh, "kind": kind, "scope": "domain", "status": "approved", "bc": "b1", **kw}
    terms = [T("跨词", bc="other", aliases=[{"name": "跨叫法", "type": "dialect"}]), T("某内核词", bc="kernel", aliases=[{"name": "内核叫法", "type": "dialect"}]),
             T("往来单位", "aggregate", en="Counterparty", aliases=[{"name": "客商", "type": "dialect"}, {"name": "待定叫法", "type": "undetermined"}], forbidden=["客户供应商"]),
             T("登记往来单位", "command"), T("往来单位已登记", "domain_event", en="CounterpartyRegistered"), T("旧词", status="deprecated", replaced_by="往来单位"),
             T("防腐词", scope="acl"), T("合同"), T("现场签证", aliases=[{"name": "签证", "type": "dialect"}]),
             T("合同变更", aliases=[{"name": "销售合同", "type": "dialect"}]), T("变更单", aliases=[{"name": "变更", "type": "dialect"}]),
             T("客户", status="deprecated", bc="presales"), T("客户", bc="masterdata"), T("账号停用", "state", aliases=[{"name": "停用", "type": "dialect"}], bc="identity"),
             T("账单", aliases=[{"name": "单", "type": "dialect"}]), T("规则版本"), T("编号规则版本", aliases=[{"name": "规则版本", "type": "dialect"}], bc="numbering")]
    bcs = [{"id": "x", "zh": "x", "kind": "bc", "words": ["通用用语"]}, {"id": "seeyon", "zh": "致远", "en": "Seeyon", "kind": "system"}, {"id": "new_system", "zh": "新系统", "kind": "system"}]
    v = Vocab(terms, bcs, [{"zh": "提议词", "en": "Proposed"}], {"U8C", "分贝通"})
    cases = [("往来单位", True, None, None), ("客商", True, None, "方言"), ("客户供应商", True, None, "方言"), ("旧词", True, None, "弃用词"), ("待定叫法", True, None, "待确认"),
             ("没登记的名", True, None, "未登记"), ("提议词", True, None, "提议中"), ("通用用语", True, None, None), ("防腐词", True, None, "acl 词"), ("致远", True, None, "外部系统"),
             ("往来单位", True, "command", "kind 不符"), ("登记往来单位", True, "command", None), ("往来单位已登记", True, "event", None), ("往来单位已登记", True, "command", "kind 不符"),
             ("客户", True, None, None), ("停用", True, "state", "跨 bc 待判"), ("停用", True, "aggregate", "方言"),
             ("CounterpartyRegistered", True, "event", None), ("CounterpartyRegisted", True, "event", "未登记"),
             ("核对客商档案", False, None, "方言"), ("核对往来单位档案", False, None, None), ("提交合同变更", False, None, None), ("提交变更", False, None, "方言"),
             ("签订销售合同", False, None, "方言"), ("核对现场签证", False, None, None), ("核对签证", False, None, "方言"), ("登记旧词", False, None, "弃用词"),
             ("同步 Seeyon 组织", False, None, "外部系统"), ("推送凭证到U8C", False, None, "外部系统"), ("使用新系统登记", False, None, None), ("登记U8Cx", False, None, None), ("核对单据", False, None, None), ("发布规则版本", False, None, None), ("规则版本", True, None, None)]
    for name, exact, kind, want in cases:
        got = v.classify(name, exact, kind)[0]
        assert got == want, (name, exact, kind, got, want)
    assert Vocab([], [], []).classify("任意名", True)[0] == "未登记"
    bcases = [("停用", True, "state", "identity", "方言"), ("停用", True, "state", "b1", "跨 bc 同名"), ("停用", True, "state", None, "跨 bc 待判"), ("停用", True, "command", "b1", "跨 bc 同名"),
              ("核对停用", False, None, "identity", "方言"), ("核对停用", False, None, "b1", "跨 bc 同名"), ("核对停用", False, None, None, "方言"),
              ("内核叫法", True, None, "b1", "方言"), ("核对跨叫法客商", False, None, "b1", "方言"), ("核对跨叫法", False, None, "b1", "跨 bc 同名")]
    for name, exact, kind, bc, want in bcases:
        got = v.classify(name, exact, kind, bc)[0]
        assert got == want, (name, bc, got, want)
    assert v.classify("推送凭证到U8C", False, None, "b1", True)[0] is None and v.classify("推送凭证到U8C", False, None, "b1", False)[0] == "外部系统"
    assert v.classify("核对客商", False, None, "b1", True)[0] == "方言"
    assert owner_bc("B-03", {"B-03": "x.y"}) == "x.y" and owner_bc("platform.integration（接口）与 B-27", {}) == "platform.integration" and owner_bc("B-99", {}) is None and owner_bc(None, {}) is None
    assert bc_map_gaps(None, "/p")[0].startswith("bc 映射（/p 不存在") and bc_map_gaps({"map": []}, "/p") and bc_map_gaps({"map": [{"b": "B-1", "bc": "a.b", "in_bcs_yaml": True}, {"b": "B-2", "bc": "c.d", "in_bcs_yaml": False}]}, "/p") == ["bc 映射：B-2 c.d 未在 bcs.yaml 登记（in_bcs_yaml: false，待 one-glossary）"]
    im = ("| OBJ-01 | 往来单位（含上级） | 定义 | 一级 | 类型 | 待核实 → 有效（档案已核实）→ 已合并 / 停用；档案按 D17 版本化（D-8、PR #3） | 关系 | 规则 | 事实 | 来源 | 词表 | 范围 |\n"
          "| OBJ-02 | 备注 | 定义 | 非对象 | 类型 | 有效 | 关系 | 规则 | 事实 | 来源 | 词表 | 范围 |\n"
          "| OBJ-03 | 分摊规则 | 定义 | **待定**（暂列一级：原为非对象） | 类型 | 现金：待缴 ⇄ 已暂停 | 关系 | 规则 | 事实 | 来源 | 词表 | 范围 |\n"
          "| OBJ-04 | 备注二 | 定义 | **非对象**（说明） | 类型 | 有效 | 关系 | 规则 | 事实 | 来源 | 词表 | 范围 |")
    assert parse_info_map(im) == [("OBJ-01", "往来单位", ["待核实", "有效", "已合并", "停用"]), ("OBJ-03", "分摊规则", ["待缴", "已暂停"])], parse_info_map(im)
    ag = ("**B-01 往来单位**（新增）\n\n| AGG-01 | 往来单位（OBJ-01） | 内含 | 次级在内 | INV-01 | 登记往来单位（→ 往来单位已登记）；停用往来单位（→ （内部））；核准（→ 甲已核准、乙已核准；前置 INV-02）；暂停（→ 角色已合格 / 已暂停） | "
          "往来单位已登记（仅分析）、`CounterpartyRegistered`（订阅方）、已有 `X`、新增草案：丙已登记、这是一个很长的说明文字不算事件名 | 依赖 |\n| REF-01 | 招标项目登记（OBJ-19） | 内含 | 无聚合 | — | 登记招标项目（→ （内部）） | — | — |")
    got = parse_aggregates(ag)
    assert got[0] == ("AGG-01", "往来单位", ["登记往来单位", "停用往来单位", "核准", "暂停"], ["往来单位已登记", "甲已核准", "乙已核准", "角色已合格", "已暂停", "CounterpartyRegistered", "丙已登记"], "B-01", "OBJ-01"), got[0]
    assert got[1] == ("REF-01", "招标项目登记", ["登记招标项目"], [], "B-01", "OBJ-19"), got[1]
    vs = {"value_streams": [{"id": "VS-01", "name": "获得客商", "stages": [{"id": "VS-01.1", "name": "启动"}]}]}
    cat = {"processes": [{"id": "PRC-01", "name": "举办", "type": "merged"}, {"id": "PRC-02", "name": "核对客商", "external_system": "A8（致远协同）、无"}, {"id": "PRC-03", "name": "x", "status": "retired"}]}
    assert catalog_systems(cat) == {"A8", "致远协同"}, catalog_systems(cat)
    items = collect(im, ag, vs, {"capabilities": [{"id": "CAP-001", "name": "往来单位管理", "object": "OBJ-01"}]}, cat, {"B-01": "b1"})
    assert {i[3]: i[6] for i in items if i[1] in ("对象", "聚合", "能力")}["往来单位"] == "b1" and [i for i in items if i[1] == "能力"][0][6] == "b1"
    cat2 = {"processes": [{"id": "PRC-09", "name": "推送到 Seeyon", "type": "integration", "owner_bc": "B-01"}, {"id": "PRC-10", "name": "拉取 Seeyon 数据", "type": "business", "owner_bc": "platform.x"}]}
    it2 = {i[2]: i for i in collect("", "", None, None, cat2, {"B-01": "b1"})}
    assert it2["PRC-09"][6:] == ("b1", True) and it2["PRC-10"][6:] == ("platform.x", False)
    assert [f[3] for f in run(v, list(it2.values()))] == ["PRC-10"]
    found = {(f[0], f[2], f[4]) for f in run(v, items)}
    assert ("方言", "价值流", "获得客商") in found and ("方言", "流程", "核对客商") in found and ("未登记", "命令", "核准") in found and ("未登记", "状态", "待核实") in found
    assert not any(f[4] == "举办" for f in run(v, items)) and ("未登记", "能力", "往来单位") not in found and ("未登记", "外部引用", "招标项目登记") in found
    inputs = {"信息图": im, "聚合稿": ag, "价值流": vs, "能力": {"capabilities": []}, "流程目录": None}
    paths = {k: f"/{k}" for k in inputs}
    g = gaps(inputs, collect(im, ag, vs, {"capabilities": []}, None), paths)
    assert len(g) == 2 and "能力" in g[0] and "一项都没解析出来" in g[0] and "流程目录" in g[1] and "不存在" in g[1], g
    assert gaps({"信息图": im, "聚合稿": ag, "价值流": vs, "能力": {"capabilities": [{"id": "C", "name": "往来单位管理"}]}, "流程目录": cat}, items, paths) == []
    assert "违规 " in render(items, run(v, items), ["x"]) and "未检查" in render(items, [], ["x"])
    import subprocess, tempfile
    with tempfile.TemporaryDirectory() as d:
        bad = Path(d) / "c.yaml"
        bad.write_text("process_list: []\n", encoding="utf-8")
        r = subprocess.run([sys.executable, "-I", __file__, "--enforce", "--catalog", str(bad)], capture_output=True, text=True)
        assert r.returncode == 1 and "一项都没解析出来" in r.stdout, (r.returncode, r.stdout[:300], r.stderr[-300:])
        r = subprocess.run([sys.executable, "-I", __file__, "--catalog", str(bad)], capture_output=True, text=True)
        assert r.returncode == 0, r.stderr[-300:]
        r = subprocess.run([sys.executable, "-I", __file__, "--enforce", "--bc-map", str(Path(d) / "none.yaml")], capture_output=True, text=True)
        assert r.returncode == 1 and "bc 映射（" in r.stdout, (r.returncode, r.stdout[:300])
    print("self-test ok")


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--enforce", action="store_true")
    ap.add_argument("--out")
    ap.add_argument("--catalog", default=str(CATALOG))
    ap.add_argument("--bc-map", default=str(BC_MAP))
    ap.add_argument("--note", help="写在报告首行的 HTML 注释（来源、快照日期）")
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
    catalog = Path(args.catalog)
    inputs = {"信息图": load_text(IM_FILE), "聚合稿": load_text(AG_FILE), "价值流": load_yaml(VS_FILE), "能力": load_yaml(CAP_FILE), "流程目录": load_yaml(catalog)}
    vocab = Vocab(terms["terms"], bcs["bcs"], proposed, catalog_systems(inputs["流程目录"]))
    bc_doc = load_yaml(Path(args.bc_map))
    bcmap = {m["b"]: m["bc"] for m in (bc_doc or {}).get("map") or []}
    items = collect(inputs["信息图"], inputs["聚合稿"], inputs["价值流"], inputs["能力"], inputs["流程目录"], bcmap)
    missing = gaps(inputs, items, {"信息图": IM_FILE, "聚合稿": AG_FILE, "价值流": VS_FILE, "能力": CAP_FILE, "流程目录": catalog})
    missing += bc_map_gaps(bc_doc, args.bc_map)
    found = run(vocab, items)
    report = (f"<!-- {args.note} -->\n" if args.note else "") + render(items, found, missing)
    if args.out:
        Path(args.out).write_text(report, encoding="utf-8")
    else:
        sys.stdout.write(report)
    if args.enforce and (missing or any(f[0] in ORDER for f in found)):
        sys.exit(1)


if __name__ == "__main__":
    main()
