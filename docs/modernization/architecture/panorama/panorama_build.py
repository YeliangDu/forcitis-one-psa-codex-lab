#!/usr/bin/env python3
"""全景追溯模型：按登记网络 v5 的 15 个节点（ADR-0059）从现有源文件生成 panorama.yaml，检查编号 Nxx-n 对应节点，缺口写 GAPS.md。

节点：01 干系人 · 02 关注点 · 03 价值主张 · 04 取舍决定 · 05 业务能力 · 06 价值流与阶段 · 07 业务流程 · 08 约束与认定规则 ·
09 业务对象 · 10 受控业务执行 · 11 业务事实 · 12 维度 · 13 指标与评价 · 14 计算绑定 · 15 决策与行动。
聚合、BC、子域、表作为 09、10 的领域与实现层映射（检查编号 N09-Mn、N10-Mn）。每条链接带出处（源文件与定位）。
源：ssot/05-value-streams/value-streams.yaml、ssot/10-objects/objects.yaml、ssot/10-objects/ENTERPRISE_INFO_MAP.md §2、ssot/09-processes/catalog.yaml、
ssot/c1-rules/rules.yaml、ssot/i03-aggregates/{planned,built}.yaml、ENTERPRISE_AGGREGATES_DRAFT.md §3.2、ssot/d-domain/subdomains.yaml、ssot/13-dimensions、ssot/15-kpis/kpis.yaml、glossary/terms.yaml、
data/PANORAMA_TABLES.yaml（one-data 出，缺失时表层只用 ssot/i03-aggregates/built.yaml 的已建表）。
缺口只报告、不使构建失败：反复跑到收敛；panorama.yaml、GAPS.md 不入仓（只要求能生成）；STAKEHOLDERS.md 仍入仓，由 build_all 的前后快照比对守护。
"""
import json
import re
import subprocess
import sys
from collections import Counter, defaultdict
from pathlib import Path

import yaml
import scan_aggregates
import scan_commands
import scan_org_roles

HERE = Path(__file__).resolve().parent
ARCH = HERE.parent
import anchoring_check as anc  # noqa: E402  流程归集与对象承载的判定与 anchoring_check C1、C2 共用一份代码
import object_layer_check as olc  # noqa: E402
DOCS = ARCH.parent
ROOT = DOCS.parent.parent
JAVA = ROOT / "backend/src/main/java/com/forcitis/one"
SRC = {
    "vs": ARCH / "ssot/05-value-streams/value-streams.yaml",
    "obj": ARCH / "ssot/10-objects/objects.yaml",
    "obj_details": ARCH / "ssot/10-objects/ENTERPRISE_INFO_MAP.md",
    "prc": ARCH / "ssot" / "09-processes" / "catalog.yaml",
    "agg": ARCH / "proposals" / "ENTERPRISE_AGGREGATES_DRAFT.md",
    "planned": ARCH / "ssot/i03-aggregates/planned.yaml",
    "built": ARCH / "ssot/i03-aggregates/built.yaml",
    "agg_map": ARCH / "ssot/i03-aggregates/mapping.yaml",
    "sd": ARCH / "ssot/d-domain/subdomains.yaml",
    "bc": ARCH / "ssot/d-domain/contexts.yaml",
    "terms": DOCS / "glossary" / "terms.yaml",
    "tables": DOCS / "data" / "PANORAMA_TABLES.yaml",
    "decr": HERE / "tradeoffs.yaml",
    "inv": ARCH / "ssot/c1-rules/rules.yaml",
    "rule_cards": ARCH / "ssot/c1-rules/rule_cards.yaml",
    "manual": ARCH / "ssot/c1-rules/inv_execution_points.yaml",
    "rule": DOCS / "rules/RULE_INDEX.md",
    "ep": ARCH / "ssot/i08-execution-points/execution-points.yaml",
    "tc": ARCH / "ssot/i09-acceptance/tests.yaml",
    "api": ROOT / "frontend/packages/api/openapi.json",
    "external": ROOT / "backend/architecture/external-read-registry.yaml",
    "access": ROOT / "backend/architecture/data-access-registry.yaml",
    "modules": ROOT / "backend/architecture/module-dependencies.txt",
    "cmd": ARCH / "ssot/11-commands/commands.yaml",
    "facts": ARCH / "ssot/12-facts/facts.yaml",
    "dims": ARCH / "ssot/13-dimensions/dimensions.yaml",
    "dims_raw": ARCH / "ssot/13-dimensions/dimensions.raw.yaml",
    "kpis": ARCH / "ssot/15-kpis/kpis.yaml",
    "bindings": ARCH / "ssot/14-bindings/bindings.yaml",
    "otr": ARCH / "ssot/10-objects/transitions.yaml",
    "events": DOCS / "events",
    "cmd_map": ARCH / "ssot/11-commands/command-mapping.yaml",
    "permissions": ARCH / "ssot/07-org-roles/permissions.yaml",
    "roles": ARCH / "ssot/07-org-roles/roles.yaml",
    "roles_raw": ARCH / "ssot/07-org-roles/roles.raw.yaml",
}
IN_SYSTEM = ("用户角色", "往来单位加角色", "专门对象", "只作属性或参与者", "不进系统")
OUT_MODEL = HERE / "panorama.yaml"
OUT_GAPS = HERE / "GAPS.md"
OUT_SH = ARCH / "proposals" / "STAKEHOLDERS.md"
SH_BEGIN, SH_END = "<!-- 干系人图：由 panorama_build.py 从 value-streams.v2.yaml 生成，勿手改 -->", "<!-- 干系人图结束 -->"
OBJ_RE = re.compile(r"OBJ-\d{2,3}")
AGG_RE = re.compile(r"AGG-\d{2,3}")
INACTIVE_PRC = {"retired", "merged"}
# 审计按 kernel.event.DomainEvent 与 EventEnvelope 接收全部事件、不导入发布模块（platform/audit/application/AuditDomainEvents.java）
KERNEL_EVENT_SUBSCRIBERS = {"platform.audit"}
EVENT_CATALOGS = ("approval", "attendance", "identity", "integration", "numbering", "timesheet", "workforce")

UNAUTOMATED = {}
SKELETON = ARCH / "proposals" / "closeout"
LAYER_NODE = {"sh": "01", "shcat": "01", "con": "02", "vp": "03", "decr": "04", "cap": "05", "vs": "06", "stage": "06", "prc": "07", "inv": "08",
              "obj": "09", "act": "10", "fact": "11", "dim": "12", "metric": "13", "bind": "14", "dec": "15",
              "perm": "01 映射", "srole": "01 映射", "role": "01 映射", "evt": "10 映射", "cmd": "10", "otr": "09/10 映射", "rule": "08", "ep": "10", "tc": "10", "api": "10 映射", "mod": "10 映射", "steps": "07", "agg": "09/10 映射", "bc": "09/10 映射", "sd": "09/10 映射", "table": "09/10 映射"}
NODES = {"01": "干系人", "02": "关注点", "03": "价值主张", "04": "取舍决定", "05": "业务能力", "06": "价值流与阶段", "07": "业务流程", "08": "约束与认定规则", "09": "业务对象", "10": "受控业务执行", "11": "业务事实", "12": "维度", "13": "指标与评价", "14": "计算绑定", "15": "决策与行动"}
CHECKS = {
    "N01-1": "每个干系人都登记了关注点（02 CON 未登记时全部计为缺口）",
    "N01-3": "每个干系人类别对应一个一级业务对象，类别角色取值在信息图可查；没有对象的登记 04 并写理由",
    "N01-4": "每个具体干系人登记「在系统中的体现」（五选一），并是类别对象（或其从属对象）的一个类型、角色或属性取值；不进系统的登记 04",
    "N01-2": "每个干系人至少触发、接收或参与一条价值流（价值流 triggering_stakeholders、value_receivers，阶段 participants）",
    "N03-1": "每条价值流都有价值主张（暂以价值流的 value 字段作 03 的登记）",
    "N05-1": "每个阶段都有业务能力支撑（阶段 capabilities 字段）",
    "N06-1": "每个阶段都有对象状态作为门（进入或退出条件引用对象状态）",
    "N06-2": "每条价值流的门对象中，至少一个顺依附链（信息图「父对象」列）追到一级业务对象",
    "N06-3": "每个阶段的门对象中，至少一个顺依附链追到一级业务对象（无门对象的阶段也计缺口）",
    "N06-4": "每条价值流都有触发者和价值接收者",
    "N07-1": "每个有效流程都能归集到价值流：所标阶段或支撑本段，且有作用对象（同 anchoring_check C1）",
    "N07-2": "流程主要对象引用的编号存在且是有效对象（主要对象为空不算缺口：作用对象由 N07-1 / anchoring C1 判）",
    "N07-P1": "P-1：流程骨架中作用于聚合的步骤都声明 Command，且该命令在同一聚合下登记于各组 commands.yaml",
    "N01-R1": "权责表引用的每个干系人编号都归入 ssot/07-org-roles/roles.yaml 的某个 ROLE（系统执行者与模块除外；roles.yaml 未建时不查）",
    "N01-R2": "每个 ROLE 写明由谁承担（host_category 非空）",
    "N01-R3": "每个权限码至少一个系统角色持有（迁移种子与 AdministratorMode 代码定义，只报告）",
    "N08-1": "每个不变量都归属存在的聚合",
    "N08-2": "每条不变量至少有一个执行点",
    "N08-3": "每条不变量至少有一个用例",
    "N08-4": "每张有不变量对照的 RULE 至少有一个用例",
    "N09-1": "每个有效对象都有流程承载（同 anchoring_check C2；OBJD 定论的从属、参照、视图按定论排除）",
    "N09-2": "每个有效一级业务对象都有聚合（聚合「对应」列；从属、参照、视图不要求）",
    "N09-3": "每个聚合对应的对象都出现在某个阶段的门或段内状态中",
    "N09-4": "依附链必填：非一级的有效对象「父对象」列填有效 OBJ 编号，一级对象填「—」",
    "N09-M1": "每个聚合都归属一个 BC",
    "N09-M2": "每个聚合都有不变量",
    "N09-M3": "每个聚合至少一张表（ADR-0059 建表规则，不要求每个对象一张）",
    "N09-M4": "每张表都能追到聚合，聚合能追到业务对象",
    "N09-M5": "数据访问登记中 AGGREGATE_ROOT、AGGREGATE_CHILD、BUSINESS_DOCUMENT 表能追到聚合（其他 role 只计数）",
    "N09-M6": "每个已建聚合在 ssot/i03-aggregates/mapping.yaml 对上规划聚合或写明无对应（pending 计缺口）",
    "N10-M2": "每个后端模块在 module-dependencies.txt 登记",
    "N10-M3": "每个 API 路径能追到 BC（同路径的全部方法均须有明确且一致的归属）",
    "N10-1": "每个受控业务执行（命令）都有 owner BC",
    "N10-M1": "每个 BC 都归属一个子域（模块命名空间可在 subdomains.yaml 找到）",
    "N10-2": "每个 CMD 登记的命令有 owner BC（ssot/11-commands，只取 owner_bc；N10-1 查骨架中的受控执行）",
    "N10-3": "每条状态转换有触发命令（含 pending；by_command 为空计缺口）",
    "N10-4": "每个已建命令类在 ssot/11-commands/command-mapping.yaml 登记对照（pending 计缺口）",
    "N10-5": "每个事件有唯一发布者与版本",
    "N10-6": "跨 BC 只走已发布事件或契约（订阅模块对发布模块的依赖须登记；只按 kernel DomainEvent 信封接收的订阅方除外）",
    "N11-1": "每个一级业务对象都声明了产生的业务事实（信息图「产生的事实」列）",
    "N11-2": "每条经营事实有产生它的命令或事件",
    "N11-3": "每条分析事实能追到经营事实",
    "N12-1": "每个维度至少切一条事实",
    "N12-2": "总线矩阵的每个 ✔ 格都对上 DIM×FACT",
    "N13-1": "每个阶段都有指标",
    "N13-2": "每个指标都能追到阶段或流程",
    "N13-3": "度量链：每个指标都有计算绑定（绑定带事实来源）",
    "N14-1": "每个绑定有事实、公式版本与时间基准",
}


def expand(prefix, text):
    """展开简写：「AGG-01、02、33」「AGG-03–07」「INV-10-12」都按前缀补全。"""
    out = []
    for m in re.finditer(rf"{prefix}-(\d{{2,3}})((?:\s*[、，,/]\s*\d{{2,3}}(?![\d-])|\s*[–—~-]\s*\d{{2,3}})*)", text):
        cur = int(m.group(1))
        width = len(m.group(1))
        out.append(cur)
        for sep, num in re.findall(r"\s*([、，,/]|[–—~-])\s*(\d{2,3})", m.group(2)):
            n = int(num)
            if sep in "–—~-" and n > cur:
                out.extend(range(cur + 1, n + 1))
            else:
                out.append(n)
            cur = n
    seen = []
    for n in out:
        k = f"{prefix}-{n:0{width}d}"
        if k not in seen:
            seen.append(k)
    return seen


def gate_refs(node, patterns, depth=0):
    """门条件里的全部叶子引用：展开价值流级 stage_pattern（entry_common / exit_common）与嵌套 any / all。"""
    if not isinstance(node, dict) or depth > 10:
        return []
    out = [node] if "ref" in node else []
    if node.get("pattern"):
        if node["pattern"] not in patterns:
            raise ValueError(f"门条件引用了未定义的 pattern：{node['pattern']}")
        out += gate_refs(patterns[node["pattern"]], patterns, depth + 1)
    for k in ("any", "all"):
        for x in node.get(k) or []:
            if not isinstance(x, dict):
                raise ValueError(f"门条件 {k} 里有非条件项：{x!r}")
            out += gate_refs(x, patterns, depth + 1)
    return out


def obj_texts(objects, text):
    """每个对象可查取值的文字：登记字段，加上 §2 中提到它（编号或名称）的小节。"""
    fields = ("id", "name", "definition", "level", "type", "key_states", "relations", "rules", "facts", "source", "term", "scope", "parent_text")
    rows = {o["id"]: (" ".join(o[k] for k in fields), o["name"]) for o in objects}
    s2 = text[text.index("## 2."):text.index("## 3.")] if "## 2." in text and "## 3." in text else ""
    secs = re.split(r"\n(?=### )", s2)
    out = {}
    for oid, (row, name) in rows.items():
        out[oid] = row + "\n" + "\n".join(s for s in secs if re.search(rf"{oid}(?!\d)", s.split("\n", 1)[0]) or name in s.split("\n", 1)[0])
    return out


def load_objects(m, link):
    objects = read_rows("obj", "objects")
    text = SRC["obj_details"].read_text(encoding="utf-8")
    m["obj_text"] = obj_texts(objects, text)
    disp = olc.object_dispositions(text)
    kinds = {"subordinate": "从属对象", "reference_data": "参照数据", "view": "视图"}
    seen = set()
    for index, row in enumerate(objects):
        oid = row["id"]
        if oid in seen:
            raise ValueError(f"{rel(SRC['obj'])} 编号重复：{oid}")
        seen.add(oid)
        if "已非对象" in row["definition"]:
            continue
        level, d = row["level"], disp.get(oid)
        src = f"{rel(SRC['obj'])}#objects[{index}]/{oid}"
        m["obj"][oid] = {"name": row["name"], "active": True, "primary": level.startswith("一级") or "暂列一级" in level,
                         "class": kinds[d["kind"]] if d else "业务对象", "parent": d.get("parent_object") if d else None,
                         "parents": row["parent"], "parents_raw": row["parent_text"],
                         "states": row["key_states"], "facts": row["facts"], "src": src}
        for parent in row["parent"]:
            link("obj-parent", oid, parent, src)


def rel(p):
    return str(Path(p).relative_to(DOCS)) if Path(p).is_relative_to(DOCS) else str(Path(p).relative_to(ROOT))


def cells(line):
    return [c.strip() for c in line.strip().strip("|").split("|")]


def md_rows(text, start_heading, end_heading, first_re):
    s = text.index(start_heading)
    e = text.index(end_heading, s) if end_heading else len(text)
    rows = []
    for no, line in enumerate(text[:e].splitlines()[text[:s].count("\n"):], text[:s].count("\n") + 1):
        if line.startswith("|"):
            c = cells(line)
            m = re.match(first_re, c[0].replace("*", "")) if c else None
            if m:
                rows.append((m.group(0), c, no))
    return rows


BUSINESS_ROLES = {"AGGREGATE_ROOT", "AGGREGATE_CHILD", "BUSINESS_DOCUMENT"}
HTTP_METHODS = {"get", "put", "post", "delete", "options", "head", "patch", "trace"}


def read_mapping(path):
    data = yaml.safe_load(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError(f"{rel(path)} 应为映射")
    return data


def read_rows(key, field):
    if key == "cmd" and not SRC[key].is_file():
        raise FileNotFoundError(f"{rel(SRC[key])} 是不入仓的命令目录，当前缺失；"
                                "请先运行 python3 docs/modernization/build_all.py 生成，或从 generated-docs 分支读取快照")
    rows = read_mapping(SRC[key]).get(field)
    if not isinstance(rows, list) or any(not isinstance(row, dict) for row in rows):
        raise ValueError(f"{rel(SRC[key])}#{field} 应为条目列表")
    return rows


def rule_id(ref):
    match = re.fullmatch(r"((?:DATA-)?RULE-)(\d+)", ref)
    return f"{match[1]}{int(match[2]):0{2 if match[1] == 'DATA-RULE-' else 3}d}" if match else ref


def add_nodes(m, key, field, id_field):
    for index, row in enumerate(read_rows(key, field)):
        ident = row[id_field]
        if ident in m[key]:
            raise ValueError(f"{rel(SRC[key])} 编号重复：{ident}")
        m[key][ident] = {**row, "src": f"{rel(SRC[key])}#{field}[{index}]/{ident}"}


def manual_entries(m):
    if not SRC["manual"].exists():
        return []
    data = yaml.safe_load(SRC["manual"].read_text(encoding="utf-8"))
    if data is None:
        return []
    if not isinstance(data, dict) or not isinstance(data.get("entries"), list):
        raise ValueError(f"{rel(SRC['manual'])} 应为 entries 列表")
    for index, row in enumerate(data["entries"]):
        src = f"{rel(SRC['manual'])}#entries[{index}]"
        if not isinstance(row, dict) or row.get("inv") not in m["inv"]:
            raise ValueError(f"{src} 引用了不存在的 INV：{row!r}")
        if not isinstance(row.get("execution_points"), list) or not isinstance(row.get("evidence"), str) or not row["evidence"].strip():
            raise ValueError(f"{src} 缺 execution_points 列表或 evidence")
        for ep in row["execution_points"]:
            if ep not in m["ep"]:
                raise ValueError(f"{src} 引用了不存在的 EP：{ep}")
    return data["entries"]


def load_constraints(m, link):
    add_nodes(m, "inv", "rules", "id")
    add_nodes(m, "ep", "execution_points", "id")
    add_nodes(m, "tc", "tests", "key")
    for no, line in enumerate(SRC["rule"].read_text(encoding="utf-8").splitlines(), 1):
        row = cells(line)
        if row and re.fullmatch(r"(?:[A-Z]+-)?RULE-\d+", row[0]):
            m["rule"][row[0]] = {"name": row[2], "src": f"{rel(SRC['rule'])}:{no}", "tcs": [], "eps": []}
    if not m["rule"]:
        raise ValueError(f"{rel(SRC['rule'])} 没有 RULE 节点")
    for iid, inv in m["inv"].items():
        inv.update(aggs=inv["owner_aggregates"], rules=[], eps=[], tcs=[])
        for agg in inv["aggs"]:
            link("inv-agg", iid, agg, inv["src"] + ".owner_aggregates", via="owner_aggregates")
    for index, row in enumerate(read_rows("rule_cards", "entries")):
        iid = row["inv"]
        if iid not in m["inv"]:
            raise ValueError(f"rule_cards.entries[{index}] 引用了不存在的 INV：{iid}")
        for raw in row["rule_cards"]:
            rid = rule_id(raw)
            if rid not in m["rule"]:
                raise ValueError(f"rule_cards.entries[{index}] 引用了不存在的 RULE：{raw}")
            m["inv"][iid]["rules"].append(rid)
            link("inv-rule", iid, rid, f"{rel(SRC['rule_cards'])}#entries[{index}].rule_cards", reference=raw, evidence=row["evidence"])
    link_constraint_refs(m, link)
    for index, row in enumerate(manual_entries(m)):
        for ep in row["execution_points"]:
            m["inv"][row["inv"]]["eps"].append(ep)
            link("inv-ep", row["inv"], ep, f"{rel(SRC['manual'])}#entries[{index}].execution_points", via="manual", evidence=row["evidence"])
    link_execution_tests(m, link)


def constraint_refs(node, key):
    return [(rule_id(ref), field) for field in (("enforces",) if key == "ep" else ("covers", "class_covers")) for ref in node[field]]


def link_constraint_refs(m, link):
    invs_by_rule = defaultdict(list)
    for iid, inv in m["inv"].items():
        for rid in inv["rules"]:
            invs_by_rule[rid].append(iid)
    for key, targets in (("ep", "eps"), ("tc", "tcs")):
        for ident, node in m[key].items():
            for ref, field in constraint_refs(node, key):
                src = node["src"] + "." + field
                if ref.startswith(("INV-", "RULE-", "DATA-RULE-")) and ref not in m["inv"] and ref not in m["rule"]:
                    raise ValueError(f"{src} 引用了不存在的编号：{ref}")
                if ref in m["rule"]:
                    m["rule"][ref][targets].append(ident)
                    link(f"rule-{key}", ref, ident, src, via=field)
                    for iid in invs_by_rule[ref]:
                        m["inv"][iid][targets].append(ident)
                        link(f"inv-{key}", iid, ident, src, via="rule", rule=ref,
                             mapping_src=f"{rel(SRC['rule_cards'])}#entries/{iid}.rule_cards")
                elif ref in m["inv"]:
                    m["inv"][ref][targets].append(ident)
                    link(f"inv-{key}", ref, ident, src, via=field)


def link_execution_tests(m, link):
    tests_by_ref = defaultdict(list)
    for tid, test in m["tc"].items():
        for ref, field in constraint_refs(test, "tc"):
            if ref in m["inv"] or ref in m["rule"]:
                tests_by_ref[ref].append((tid, test["src"] + "." + field))
    for eid, ep in m["ep"].items():
        for ref, field in constraint_refs(ep, "ep"):
            for tid, src in tests_by_ref[ref]:
                link("ep-tc", eid, tid, ep["src"] + "." + field, via="rule" if ref in m["rule"] else "inv", reference=ref, test_src=src)


def attach_table(m, link, table, agg, src, via):
    if agg not in m["agg"]:
        raise ValueError(f"{src} 引用了不存在的聚合：{agg}")
    if agg not in m["table"][table]["aggregates"]:
        m["table"][table]["aggregates"].append(agg)
    if table not in m["agg"][agg]["tables"]:
        m["agg"][agg]["tables"].append(table)
    link("table-agg", table, agg, src, via=via)


def load_tables(m, link, mod_to_bc):
    built = read_rows("built", "aggregates")
    mapped = {e["built"]: e for e in read_rows("agg_map", "entries")}
    unknown = sorted(set(mapped) - {agg["id"] for agg in built})
    if unknown:
        raise ValueError(f"{rel(SRC['agg_map'])} 引用了不存在的已建聚合：{'、'.join(unknown)}")
    for agg in built:
        m["agg"][agg["id"]] = {"name": agg["id"].split("/")[-1], "objs": [],
                                "invs": (mapped.get(agg["id"]) or {}).get("invariants") or [],
                                "planned": (mapped.get(agg["id"]) or {}).get("planned") or [],
                                "relation": (mapped.get(agg["id"]) or {}).get("relation"),
                                "bc": mod_to_bc.get(agg["bc"]), "module": agg["bc"], "tables": [], "built": True,
                                "src": f"{rel(SRC['built'])}#aggregates/{agg['id']}"}
    rows = yaml.safe_load(SRC["tables"].read_text(encoding="utf-8")) if SRC["tables"].exists() else []
    if not isinstance(rows, list):
        raise ValueError(f"{rel(SRC['tables'])} 应为表列表")
    for index, row in enumerate(rows):
        table = row.get("table") or f"{row['schema']}.{row['name']}"
        src = f"{rel(SRC['tables'])}#[{index}]"
        m["table"][table] = {**row, "aggregates": [], "sources": [src]}
        targets = [aid for aid, agg in m["agg"].items() if row.get("obj") and row["obj"] in agg["objs"]]
        if row.get("aggregate") in m["agg"]:
            targets.append(row["aggregate"])
        for aid in sorted(set(targets)):
            attach_table(m, link, table, aid, src, "panorama")
    registry = read_mapping(SRC["access"])["tables"]
    if not isinstance(registry, dict):
        raise ValueError(f"{rel(SRC['access'])}#tables 应为映射")
    for table, row in registry.items():
        src = f"{rel(SRC['access'])}#tables/{table}"
        node = m["table"].setdefault(table, {"aggregates": [], "sources": []})
        node.update(role=row["role"], registered=True, registered_aggregate=row.get("aggregate"))
        node["sources"].append(src)
    for agg in built:
        for table in agg.get("tables") or []:
            src = f"{rel(SRC['built'])}#aggregates/{agg['id']}.tables"
            node = m["table"].setdefault(table, {"aggregates": [], "sources": []})
            node["sources"].append(src)
            attach_table(m, link, table, agg["id"], src, "built")


def load_planned_aggregates(m, link):
    for index, row in enumerate(read_rows("planned", "aggregates")):
        aid = row["id"]
        if not AGG_RE.fullmatch(aid) or aid in m["agg"]:
            raise ValueError(f"{rel(SRC['planned'])} 编号无效或编号重复：{aid}")
        name = row.get("聚合（根）") if "聚合（根）" in row else row["聚合"]
        objs = OBJ_RE.findall(" ".join((name, row["内含"], row["对应"])))
        src = f"{rel(SRC['planned'])}#aggregates[{index}]/{aid}"
        m["agg"][aid] = {"name": re.sub(r"（.*", "", name), "objs": sorted(set(objs), key=objs.index),
                         "invs": expand("INV", row["不变量"]), "bc": None, "tables": [], "src": src}
        for obj in m["agg"][aid]["objs"]:
            link("obj-agg", obj, aid, src)


def load_commands(m, link):
    add_nodes(m, "cmd", "commands", "id")
    for ident, command in m["cmd"].items():
        if not re.fullmatch(r"CMD-[0-9a-f]{8}", ident):
            raise ValueError(f"命令编号无效：{ident}")
        scan_commands.command_key(command, "aggregate", "name")
        if "owner_bc" not in command or not isinstance(command.get("objects"), list):
            raise ValueError(f"{ident} 缺 owner_bc 或 objects 列表")
        scan_commands.string(command["owner_bc"], "owner_bc", nullable=True)
        src = command["src"]
        if command["aggregate"] in m["agg"]:
            link("cmd-agg", ident, command["aggregate"], src + ".aggregate")
        if command["owner_bc"]:
            if command["owner_bc"] not in m["bc"]:
                raise ValueError(f"{ident} 引用了不存在的 BC：{command['owner_bc']}")
            link("cmd-bc", ident, command["owner_bc"], src + ".owner_bc")
        for obj in command["objects"]:
            if not isinstance(obj, str) or not OBJ_RE.fullmatch(obj):
                raise ValueError(f"{ident} 对象编号无效：{obj}")
            link("cmd-obj", ident, obj, src + ".objects")
    for section in ("transitions", "pending"):
        for index, row in enumerate(read_rows("otr", section)):
            ident = row["id"]
            if ident in m["otr"] or "by_command" not in row:
                raise ValueError(f"转换编号重复或缺 by_command：{ident}")
            src = f"{rel(SRC['otr'])}#{section}[{index}]/{ident}"
            m["otr"][ident] = {**row, "section": section, "src": src}
            command = row["by_command"]
            if command is not None:
                target = m["cmd"].get(command)
                if not target or (row["aggregate"], row["command"]) != (target["aggregate"], target["name"]):
                    raise ValueError(f"{ident} 的 by_command 未对上同聚合的命令原文：{command}")
                link("cmd-otr", command, ident, src + ".by_command")


def load_facts(m, link, mod_to_bc):
    retired = {row["id"] for row in read_rows("obj", "objects") if "已非对象" in row["definition"]}
    for index, row in enumerate(read_rows("facts", "facts")):
        ident = row.get("id")
        if not isinstance(ident, str) or not re.fullmatch(r"FACT-\d{2}", ident):
            raise ValueError(f"事实编号无效：{ident}")
        if ident in m["fact"]:
            raise ValueError(f"{rel(SRC['facts'])} 编号重复：{ident}")
        if row.get("kind") not in ("经营事实", "分析事实"):
            raise ValueError(f"{ident} kind 须为经营事实或分析事实")
        m["fact"][ident] = {**row, "src": f"{rel(SRC['facts'])}#facts[{index}]/{ident}"}
    for ident, fact in m["fact"].items():
        owner = fact.get("owner_bc")
        bc = owner if owner in m["bc"] else mod_to_bc.get(owner)
        if not bc:
            raise ValueError(f"{ident} 引用了不存在的 BC：{owner}")
        link("fact-bc", ident, bc, fact["src"] + ".owner_bc")
        for ref in fact.get("produced_by") or []:
            if isinstance(ref, str) and ref.startswith("CMD-"):
                key, target = "cmd", ref
            elif isinstance(ref, str) and ref.startswith("EVT:"):
                key, target = "evt", ref[4:]
            else:
                raise ValueError(f"{ident} produced_by 写法无效：{ref}")
            if target not in m[key]:
                raise ValueError(f"{ident} produced_by 引用了不存在的 {key}：{target}")
            link(f"{key}-fact", target, ident, fact["src"] + ".produced_by")
        for ref in fact.get("derives_from") or []:
            if ref not in m["fact"]:
                raise ValueError(f"{ident} derives_from 引用了不存在的 FACT：{ref}")
            link("fact-fact", ref, ident, fact["src"] + ".derives_from")
        for ref in (fact.get("sources") or {}).get("object") or []:
            if ref in retired:
                continue
            if ref not in m["obj"]:
                raise ValueError(f"{ident} sources.object 引用了不存在的 OBJ：{ref}")
            link("obj-fact", ref, ident, fact["src"] + ".sources.object")


def check_facts(m, gaps):
    for ident, obj in sorted(m["obj"].items()):
        facts = (obj.get("facts") or "").strip()
        if (obj["active"] and obj["primary"] and obj.get("class", "业务对象") == "业务对象"
                and (not facts or facts.startswith(("—", "-")))):
            gaps["N11-1"].append(f"{ident} {obj['name']}")
    for ident, fact in m.get("fact", {}).items():
        if fact["kind"] == "经营事实" and not fact.get("produced_by"):
            gaps["N11-2"].append(f"{ident} {fact['name']}")
        if fact["kind"] == "分析事实" and (not fact.get("derives_from") or any(
                m["fact"][ref]["kind"] != "经营事实" for ref in fact["derives_from"])):
            gaps["N11-3"].append(f"{ident} {fact['name']}（analytic_source：{fact.get('analytic_source', '')[:40]}）")


def load_dimensions(m, link):
    retired = {row["id"] for row in read_rows("obj", "objects") if "已非对象" in row["definition"]}
    for index, row in enumerate(read_rows("dims", "dimensions")):
        ident = row.get("id")
        if not isinstance(ident, str) or not re.fullmatch(r"DIM-\d{2}", ident):
            raise ValueError(f"维度编号无效：{ident}")
        if ident in m["dim"]:
            raise ValueError(f"{rel(SRC['dims'])} 编号重复：{ident}")
        src = f"{rel(SRC['dims'])}#dimensions[{index}]/{ident}"
        m["dim"][ident] = {**row, "src": src}
        for slice_index, item in enumerate(row.get("slices") or []):
            for fact in item.get("facts") or []:
                if fact not in m["fact"]:
                    raise ValueError(f"{ident} slices 引用了不存在的 FACT：{fact}")
                link("dim-fact", ident, fact, f"{src}.slices[{slice_index}].facts", row=item["row"])
        for ref in row.get("backed_by") or []:
            if not ref.startswith("OBJ-") or ref in retired:
                continue
            if ref not in m["obj"]:
                raise ValueError(f"{ident} backed_by 引用了不存在的 OBJ：{ref}")
            link("dim-obj", ident, ref, src + ".backed_by")


def check_dimensions(m, gaps):
    dimensions = m.get("dim", {})
    for ident, dim in dimensions.items():
        if not any(item.get("facts") for item in dim.get("slices") or []):
            gaps["N12-1"].append(f"{ident} {dim['name']}")
    excluded = {column for row in read_rows("dims", "not_dimensions")
                for column in (row.get("sources") or {}).get("bi_column") or []}
    for column in read_rows("dims_raw", "by_bi_bus_matrix"):
        name = column["name"]
        if name in excluded:
            continue
        candidates = [d for d in dimensions.values() if (d.get("sources") or {}).get("bi_column") == name]
        for cell in column.get("rows") or []:
            if cell["cell"] == "✔" and not any(
                    item["row"] == cell["fact"] and item.get("facts")
                    for dim in candidates for item in dim.get("slices") or []):
                gaps["N12-2"].append(f"行「{cell['fact']}」× 列「{name}」未对上 DIM×FACT")


# 技术度量（kernel 与平台运维度量）不计入 N13 度量链：kpis.yaml 定稿时 one-architecture 判定（KPI-90、KPI-91，2026-10-22）
METRIC_KINDS = {"业务指标", "出口条件", "技术度量"}


def load_metrics(m, link):
    terms = {row["id"]: row for row in read_rows("terms", "terms")}
    for index, row in enumerate(read_rows("kpis", "kpis")):
        ident = row.get("id")
        if not isinstance(ident, str) or not re.fullmatch(r"KPI-\d{2,3}", ident):
            raise ValueError(f"指标编号无效：{ident}")
        if ident in m["metric"]:
            raise ValueError(f"{rel(SRC['kpis'])} 编号重复：{ident}")
        src = f"{rel(SRC['kpis'])}#kpis[{index}]/{ident}"
        term = row.get("term")
        if term and term not in terms:
            raise ValueError(f"{ident} 引用了不存在的 term：{term}")
        if row.get("kind") not in METRIC_KINDS:
            raise ValueError(f"{ident} kind 须为 {'、'.join(sorted(METRIC_KINDS))} 之一：{row.get('kind')}")
        m["metric"][ident] = {key: row.get(key) for key in
                                ("kind", "term", "baseline", "vision_kpi", "facts", "dimensions")}
        m["metric"][ident].update(zh=row["name"], src=src,
                                 stages=terms.get(term, {}).get("stages") or [],
                                 processes=terms.get(term, {}).get("processes") or [])
        for field, target, kind in (("facts", m["fact"], "metric-fact"),
                                    ("dimensions", m["dim"], "metric-dim"),
                                    ("term", terms, "metric-term")):
            refs = ([term] if term else []) if field == "term" else row.get(field) or []
            for ref in refs:
                if ref not in target:
                    raise ValueError(f"{ident} {field} 引用了不存在的目标：{ref}")
                link(kind, ident, ref, src + "." + field)


def load_bindings(m, link):
    for metric in m["metric"].values():
        metric["bind"] = []
    if not SRC["bindings"].exists():
        return
    for index, row in enumerate(read_rows("bindings", "bindings")):
        ident = row.get("id")
        if not isinstance(ident, str) or not re.fullmatch(r"BIND-\d{3}", ident):
            raise ValueError(f"计算绑定编号无效：{ident}")
        if ident in m["bind"]:
            raise ValueError(f"{rel(SRC['bindings'])} 编号重复：{ident}")
        kpi = row.get("kpi")
        if not isinstance(kpi, str) or kpi not in m["metric"]:
            raise ValueError(f"{ident} 引用了不存在的 KPI：{kpi}")
        facts = row.get("facts")
        if facts is not None and not isinstance(facts, list):
            raise ValueError(f"{ident} facts 应为列表")
        time_basis = row.get("time_basis")
        if time_basis is not None and (not isinstance(time_basis, str) or
                (time_basis.strip() and time_basis not in ("业务时间", "记录时间", "期间"))):
            raise ValueError(f"{ident} time_basis 须为业务时间、记录时间或期间")
        src = f"{rel(SRC['bindings'])}#bindings[{index}]/{ident}"
        m["bind"][ident] = {**row, "src": src}
        m["metric"][kpi]["bind"].append(ident)
        link("bind-metric", ident, kpi, src + ".kpi")
        for fact in facts or []:
            if not isinstance(fact, str) or fact not in m["fact"]:
                raise ValueError(f"{ident} 引用了不存在的 FACT：{fact}")
            link("bind-fact", ident, fact, src + ".facts")
    pending = read_mapping(SRC["bindings"]).get("pending", [])
    if not isinstance(pending, list) or any(not isinstance(row, dict) for row in pending):
        raise ValueError(f"{rel(SRC['bindings'])}#pending 应为条目列表")
    for index, row in enumerate(pending):
        kpi = row.get("kpi")
        src = f"{rel(SRC['bindings'])}#pending[{index}]"
        if not isinstance(kpi, str) or kpi not in m["metric"]:
            raise ValueError(f"{src} 引用了不存在的 KPI：{kpi}")
        if m["metric"][kpi]["bind"]:
            raise ValueError(f"{src} 同一 KPI 既有绑定又在 pending：{kpi}")
        m["metric"][kpi]["bind_pending"] = {**row, "src": src}


def check_bindings(m, gaps):
    for ident, binding in m.get("bind", {}).items():
        missing = [field for field in ("facts", "formula_version", "time_basis")
                   if not binding.get(field) or (isinstance(binding[field], str) and not binding[field].strip())]
        if missing:
            gaps["N14-1"].append(f"{ident}（缺 {'、'.join(missing)}）")
    for ident, metric in m["metric"].items():
        if metric.get("kind") != "技术度量" and not metric.get("bind"):
            reason = (metric.get("bind_pending") or {}).get("reason") or ""
            gaps["N13-3"].append(f"{ident} {metric['zh']}" + (f"（pending：{reason[:40]}）" if metric.get("bind_pending") else ""))


def load_org_roles(m, link):
    data = read_mapping(SRC["permissions"])
    for key, field in (("perm", "permissions"), ("srole", "system_roles")):
        m[key] = {}
        for row in scan_commands.rows(data, field):
            code = row.get("code")
            scan_commands.string(code, f"{field}.code")
            scan_commands.string(row.get("source"), f"{field}.source")
            if code in m[key]:
                raise ValueError(f"{field} 编号重复")
            m[key][code] = {**row, "src": row["source"]}
    for code, node in m["perm"].items():
        if not re.fullmatch(scan_org_roles.CODE, code) or node.get("module") != code.split(":")[0]:
            raise ValueError("权限码或模块结构不符")
        if node["module"] not in m["mod"]:
            raise ValueError(f"权限码模块未登记：{node['module']}")
        link("perm-mod", code, node["module"], node["src"], via="code")
    for code, node in m["srole"].items():
        grants, sources = node.get("permission_codes"), node.get("permission_sources")
        if not isinstance(grants, list) or not isinstance(sources, dict):
            raise ValueError("系统角色缺 permission_codes / permission_sources")
        for permission in grants:
            if not isinstance(permission, str) or not re.fullmatch(scan_org_roles.CODE, permission):
                raise ValueError("系统角色权限码结构不符")
            origins = sources.get(permission)
            if not isinstance(origins, list) or not origins or any(not isinstance(src, str) or not src for src in origins):
                raise ValueError("系统角色权限码缺来源")
            if permission in m["perm"]:
                for src in origins:
                    link("srole-perm", code, permission, src, via="seed")
    m["role"] = {}
    if not SRC["roles"].exists():
        return
    for index, row in enumerate(read_rows("roles", "roles")):
        ident = row.get("id")
        src = f"{rel(SRC['roles'])}#roles[{index}]"
        if not isinstance(ident, str) or not re.fullmatch(r"ROLE-\d{2,3}", ident) or ident in m["role"]:
            raise ValueError(f"{src} 编号无效或重复：{ident!r}")
        m["role"][ident] = {**row, "src": f"{src}/{ident}"}
        for code in row.get("system_roles") or []:
            if code not in m["srole"]:
                raise ValueError(f"{src} 引用了不存在的系统角色：{code}")
            link("role-srole", ident, code, f"{src}.system_roles")


def check_org_roles(m, gaps):
    held = {code for role in m.get("srole", {}).values() for code in role["permission_codes"]}
    for code in m.get("perm", {}):
        if code not in held:
            gaps["N01-R3"].append(code)
    if not m.get("role"):
        return
    covered = {sh for r in m["role"].values() for sh in (r.get("aliases") or {}).get("responsibility_matrix") or []}
    for sh in sorted(read_mapping(SRC["roles_raw"])["responsibility_matrix"]["command_references"]):
        if sh.split("（")[0] == "system" or sh in m.get("mod", {}):
            continue
        if sh not in covered:
            gaps["N01-R1"].append(f"{sh}（权责表引用，未归任何 ROLE）")
    for ident, r in m["role"].items():
        if not r.get("host_category"):
            gaps["N01-R2"].append(ident)


def check_commands(m, gaps):
    for ident, command in m.get("cmd", {}).items():
        if not command["owner_bc"]:
            gaps["N10-2"].append(f"{ident} {command['name']}（{command['aggregate']} 未归 BC）")
    for ident, transition in m.get("otr", {}).items():
        if transition["by_command"] is None:
            gaps["N10-3"].append(f"{ident}（{transition['section']}，{transition['aggregate']}，命令 {transition['command']}）")


def event_modules(value, src):
    if value is None:
        return []
    values = [value] if isinstance(value, str) else value
    if not isinstance(values, list) or any(not isinstance(v, str) or not re.fullmatch(r"[a-z]+\.[a-z]+", v) for v in values):
        raise ValueError(f"{src} 应为模块名或模块名列表")
    return sorted(set(values))


def load_event_catalog(m, mod_to_bc):
    for name in EVENT_CATALOGS:
        path = SRC["events"] / f"{name}.yaml"
        messages = read_mapping(path)["components"]["messages"]
        if not isinstance(messages, dict):
            raise ValueError(f"{rel(path)}#components.messages 应为映射")
        for key, message in messages.items():
            src = f"{rel(path)}#components.messages.{key}"
            if not isinstance(message, dict) or not isinstance(message.get("x-event"), dict):
                raise ValueError(f"{src} 缺 x-event 映射")
            event = message["x-event"]
            ident = event.get("eventType")
            if not isinstance(ident, str) or not ident.strip():
                raise ValueError(f"{src} 缺 eventType")
            publishers = event_modules(event.get("publisher"), src + ".x-event.publisher")
            if "publisher" not in event:
                publishers = sorted(module for module in mod_to_bc if module.rsplit(".", 1)[-1] == name)
            subscriber_field = "subscribers" if "subscribers" in event else "consumers"
            subscribers = event_modules(event.get(subscriber_field), src + ".x-event." + subscriber_field)
            declaration = {"publishers": publishers, "version": event.get("version"), "subscribers": subscribers, "src": src,
                           "publisher_src": src + ".x-event.publisher" if "publisher" in event else rel(path),
                           "subscriber_src": src + ".x-event." + subscriber_field}
            node = m["evt"].setdefault(ident, {"name": message.get("name"), "title": message.get("title"),
                                             "fact": None, "src": src, "declarations": []})
            node["declarations"].append(declaration)
    for node in m["evt"].values():
        publishers = sorted({module for d in node["declarations"] for module in d["publishers"]})
        node["publisher_module"] = publishers[0] if len(publishers) == 1 else None
        node["publisher_bc"] = mod_to_bc.get(node["publisher_module"])
        versions = [d["version"] for d in node["declarations"]]
        node["version"] = versions[0] if all(v == versions[0] for v in versions) else None
        node["subscribers"] = sorted({module for d in node["declarations"] for module in d["subscribers"]})


def link_event_commands(m, link, aggregate_events, built):
    if not SRC["cmd_map"].exists():
        return
    classes = {(row["aggregate"], row["module"], row["class"]) for row in read_rows("cmd", "built")}
    m["cmd_class"] = {(a, c): None for a, _, c in classes}
    aggregates = {row["id"]: row for row in built}
    for index, row in enumerate(read_rows("cmd_map", "entries")):
        src = f"{rel(SRC['cmd_map'])}#entries[{index}]"
        if not all(isinstance(row.get(k), str) and row[k] for k in ("class", "module", "built_aggregate")) or not isinstance(row.get("commands"), list):
            raise ValueError(f"{src} 缺 class、module、built_aggregate 或 commands 列表")
        agg = aggregates.get(row["built_aggregate"])
        if agg is None:
            raise ValueError(f"{src} 引用了不存在的已建聚合")
        command_classes = (agg.get("commands") or []) + (agg.get("commands_via") or [])
        if row["module"] != agg["bc"] or (agg["id"], row["module"], row["class"]) not in classes or row["class"] not in command_classes:
            raise ValueError(f"{src} 命令类未在同一已建聚合的两份目录登记")
        m["cmd_class"][(agg["id"], row["class"])] = row.get("relation") or "pending"
        mapped = m.get("agg", {}).get(agg["id"], {})
        same = [agg["id"]] + (mapped.get("planned", []) if mapped.get("relation") == "same" else [])
        for command in row["commands"]:
            if not isinstance(command, str) or command not in m["cmd"]:
                raise ValueError(f"{src} 引用了不存在的 CMD：{command!r}")
            target = m["cmd"][command]
            if target["aggregate"] not in same:
                continue
            for event in sorted(aggregate_events[agg["id"]]):
                link("cmd-evt", command, event, src, via="mapping", aggregate=agg["id"],
                     built_src=f"{rel(SRC['built'])}#aggregates/{agg['id']}", command_src=target["src"],
                     aggregate_mapping_src=f"{rel(SRC['agg_map'])}#entries/{agg['id']}" if target["aggregate"] != agg["id"] else None,
                     class_src=f"{rel(SRC['cmd'])}#built/{agg['id']}/{row['class']}")


def load_events(m, link, mod_to_bc):
    load_event_catalog(m, mod_to_bc)
    for ident, node in m["evt"].items():
        for declaration in node["declarations"]:
            for publisher in declaration["publishers"]:
                if publisher in mod_to_bc:
                    link("evt-bc", ident, mod_to_bc[publisher], declaration["publisher_src"], via="publisher", module=publisher)
            for subscriber in declaration["subscribers"]:
                link("evt-mod", ident, subscriber, declaration["subscriber_src"], via="subscriber")
    built = read_rows("built", "aggregates")
    aggregate_events = defaultdict(set)
    for agg in built:
        for field, via in (("events", "events"), ("events_in_module", "module")):
            for event in agg.get(field) or []:
                if event in m["evt"]:
                    aggregate_events[agg["id"]].add(event)
                    link("evt-agg", event, agg["id"], f"{rel(SRC['built'])}#aggregates/{agg['id']}.{field}", via=via)
    link_event_commands(m, link, aggregate_events, built)


def check_events(m, gaps):
    for (aid, cls), relation in sorted(m.get("cmd_class", {}).items()):
        if relation in (None, "pending"):
            gaps["N10-4"].append(f"{aid}#{cls}（{relation or '未登记对照'}）")
    if not m.get("evt"):
        return
    dependencies = {}
    for no, line in enumerate(SRC["modules"].read_text(encoding="utf-8").splitlines(), 1):
        line = line.split("#", 1)[0].strip()
        if not line:
            continue
        match = re.fullmatch(r"([a-z]+\.[a-z]+)\s*->\s*(.*)", line)
        if not match:
            raise ValueError(f"{rel(SRC['modules'])}:{no} 模块依赖登记无效")
        dependencies[match[1]] = {part.strip() for part in match[2].split(",") if part.strip()}
    for ident, node in m["evt"].items():
        reasons = []
        if not node["publisher_module"] or any(not d["publishers"] for d in node["declarations"]):
            reasons.append("发布者缺失或不唯一")
        if node["version"] in (None, ""):
            reasons.append("版本缺失或不唯一")
        if reasons:
            gaps["N10-5"].append(f"{ident}（{'；'.join(reasons)}；{node['src']}）")
        for declaration in node["declarations"]:
            for publisher in declaration["publishers"]:
                for subscriber in declaration["subscribers"]:
                    if subscriber in KERNEL_EVENT_SUBSCRIBERS:
                        continue
                    if subscriber != publisher and publisher not in dependencies.get(subscriber, set()):
                        gaps["N10-6"].append(f"{ident}（{subscriber} → {publisher} 未登记；{declaration['src']}）")


def mapping_paths(args):
    match = re.search(r'(?:\b(?:path|value)\s*=\s*|^\s*)(\{(?:\s*"[^"]*"\s*,?)+\}|"[^"]*")', args)
    if match:
        return re.findall(r'"([^"]*)"', match[1])
    return [""] if not args.strip() or re.match(r"\s*(?:produces|consumes|headers|params)\s*=", args) else []


def controller_routes(java):
    routes = defaultdict(list)
    pattern = re.compile(r"@(Request|Get|Post|Put|Delete|Patch|Head|Options)Mapping\s*(?:\(([^)]*)\))?")
    for path in sorted(java.glob("*/*/web/*Controller.java")):
        text = path.read_text(encoding="utf-8")
        text = re.sub(r'/\*.*?\*/|//[^\n]*', lambda match: "\n" * match[0].count("\n"), text, flags=re.S)
        cls = re.search(r"\bclass\s+\w+", text)
        if not cls or "@RestController" not in text[:cls.start()]:
            continue
        annotations = list(pattern.finditer(text))
        class_mappings = [ann for ann in annotations if ann.start() < cls.start()]
        bases = [base for ann in class_mappings for base in mapping_paths(ann[2] or "")] if class_mappings else [""]
        module = ".".join(path.relative_to(java).parts[:2])
        for ann in annotations:
            if ann.start() < cls.start():
                continue
            methods = [ann[1].lower()] if ann[1] != "Request" else [x.lower() for x in re.findall(r"RequestMethod\.(\w+)", ann[2] or "")]
            for base in bases:
                for suffix in mapping_paths(ann[2] or ""):
                    route = "/" + "/".join(x.strip("/") for x in (base, suffix) if x.strip("/"))
                    for method in methods:
                        routes[(route, method)].append((module, f"{rel(path)}:{text[:ann.start()].count(chr(10)) + 1}"))
    return routes


def require_api():
    if not SRC["api"].is_file():
        raise ValueError(f"{rel(SRC['api'])} 缺失；请在仓库根运行 bash scripts/generate-api.sh（需 Docker 和前端依赖），再运行 build_all.py")


def load_interfaces(m, link, mod_to_bc):
    require_api()
    registered = {}
    for no, line in enumerate(SRC["modules"].read_text(encoding="utf-8").splitlines(), 1):
        line = line.split("#", 1)[0].strip()
        if not line:
            continue
        match = re.fullmatch(r"([a-z]+\.[a-z]+)\s*->\s*(.*)", line)
        if not match:
            raise ValueError(f"{rel(SRC['modules'])}:{no} 模块依赖登记无效")
        registered[match[1]] = f"{rel(SRC['modules'])}:{no}"
    for path in sorted(JAVA.glob("*/*")):
        if not path.is_dir() or path.parent.name == "kernel":
            continue
        module = ".".join(path.relative_to(JAVA).parts)
        bc = mod_to_bc.get(module) if module.split(".")[0] in m["sd"] else None
        m["mod"][module] = {"bc": bc, "registered": module in registered, "src": rel(path), "registration_src": registered.get(module)}
        if bc:
            link("mod-bc", module, bc, rel(path), via="module", bc_src=m["bc"][bc]["src"], subdomain_src=f"{rel(SRC['sd'])}#subdomains/{module.split('.')[0]}")
    routes = controller_routes(JAVA)
    for path, operations in sorted(json.loads(SRC["api"].read_text(encoding="utf-8"))["paths"].items()):
        parts = path.strip("/").split("/")
        sd = parts[2] if len(parts) >= 4 and parts[:2] == ["api", "v1"] and parts[2] in m["sd"] else None
        for method, operation in sorted(operations.items()):
            if method not in HTTP_METHODS:
                continue
            matches = routes.get((path, method), [])
            owners = {module for module, _ in matches}
            module = next(iter(owners)) if len(owners) == 1 else None
            bc = m["mod"].get(module, {}).get("bc") if module and module.split(".")[0] == sd else None
            ident, src = f"{method.upper()} {path}", f"{rel(SRC['api'])}#/paths/{path}/{method}"
            reason = None if bc else "路径子域未登记或格式不符" if not sd else "Controller 未唯一匹配或模块未登记 BC"
            m["api"][ident] = {"path": path, "method": method.upper(), "subdomain": sd, "bc": bc, "module": module,
                                "operation_id": operation.get("operationId"), "gap": reason, "src": src, "controllers": [s for _, s in matches]}
            if bc:
                link("api-bc", ident, bc, src, via="controller", controller_src=[s for _, s in matches], bc_src=m["bc"][bc]["src"])


def api_paths(m):
    paths = defaultdict(list)
    for node in m.get("api", {}).values():
        paths[node["path"]].append(node)
    return paths


def covered_api(nodes):
    return all(n["bc"] for n in nodes) and len({n["bc"] for n in nodes}) == 1


def check_coverage(m, gaps):
    for iid, inv in m["inv"].items():
        if not inv.get("eps"):
            gaps["N08-2"].append(iid)
        if not inv.get("tcs"):
            gaps["N08-3"].append(iid)
    mapped = {r for inv in m["inv"].values() for r in inv.get("rules", [])}
    for rid in sorted(mapped):
        if not m["rule"][rid]["tcs"]:
            gaps["N08-4"].append(rid)
    for table, node in m["table"].items():
        if node.get("registered") and node["role"] in BUSINESS_ROLES and not node["aggregates"]:
            gaps["N09-M5"].append(f"{table}（{node['role']}，聚合登记 {node.get('registered_aggregate') or '无'}）")
    for aid, a in m.get("agg", {}).items():
        if a.get("built") and a.get("relation") in (None, "pending"):
            gaps["N09-M6"].append(f"{aid}（{a.get('relation') or '未登记对照'}）")
    for module, node in m.get("mod", {}).items():
        if not node["registered"]:
            gaps["N10-M2"].append(module)
    for path, nodes in api_paths(m).items():
        if not covered_api(nodes):
            gaps["N10-M3"].append(path + "（" + "；".join(f"{n['method']}: {n.get('gap') or n['bc']}" for n in nodes) + "）")


def coverage_counts(m, links):
    mapped = {r for inv in m["inv"].values() for r in inv.get("rules", [])}
    tables = {t for t, node in m["table"].items() if node.get("registered") and node["role"] in BUSINESS_ROLES}
    coverage = {}
    for label, kind, universe in (("INV→EP", "inv-ep", set(m["inv"])), ("INV→TC", "inv-tc", set(m["inv"])),
                                  ("RULE→TC（有 INV 对照）", "rule-tc", mapped), ("表→聚合", "table-agg", tables),
                                  ("事件→聚合", "evt-agg", set(m.get("evt", {}))),
                                  ("事件→发布 BC", "evt-bc", set(m.get("evt", {})))):
        edges = [edge for edge in links if edge["kind"] == kind and edge["from"] in universe]
        by_via = defaultdict(set)
        expected = {"inv-ep": ("enforces", "rule", "manual"), "inv-tc": ("covers", "class_covers", "rule"),
                    "rule-tc": ("covers", "class_covers"), "table-agg": ("panorama", "built"),
                    "evt-agg": ("events", "module"), "evt-bc": ("publisher",)}
        for via in expected[kind]:
            by_via[via]
        for edge in edges:
            by_via[edge.get("via", "direct")].add(edge["from"])
        coverage[label] = {"covered": len({edge["from"] for edge in edges}), "total": len(universe),
                           "via": {via: len(nodes) for via, nodes in sorted(by_via.items())}}
    paths = api_paths(m)
    covered = sum(covered_api(nodes) for nodes in paths.values())
    coverage["API→BC"] = {"covered": covered, "total": len(paths), "via": {"controller": covered}}
    return coverage


def load():
    m = {k: {} for k in ("sh", "con", "vp", "decr", "cap", "vs", "stage", "prc", "inv", "obj", "act", "fact", "dim",
                         "metric", "bind", "dec", "agg", "bc", "sd", "table", "rule", "ep", "tc", "api", "mod", "cmd", "otr", "evt")}
    links = []

    def link(kind, a, b, src, **evidence):
        links.append({"kind": kind, "from": a, "to": b, "src": src, **evidence})

    vsd = yaml.safe_load(SRC["vs"].read_text(encoding="utf-8"))
    for s in vsd.get("stakeholders") or []:
        m["sh"][s["id"]] = {"name": s.get("members") if s.get("category") == "—" else (s.get("sub") or s.get("category")), "category": s.get("category"), "type": s.get("type"),
                            "in_system": s.get("in_system"), "obj": s.get("obj"), "attr": s.get("attr"), "value": s.get("value"),
                            "decr": s.get("decr"), "src": f"{rel(SRC['vs'])}#{s['id']}"}
    if not vsd.get("stakeholder_categories"):
        raise ValueError(f"{rel(SRC['vs'])} 缺 stakeholder_categories")
    m["shcat"] = {c["category"]: {**c, "src": f"{rel(SRC['vs'])}#stakeholder_categories"} for c in vsd["stakeholder_categories"]}
    decisions = (yaml.safe_load(SRC["decr"].read_text(encoding="utf-8")) or {}).get("decisions")
    if not decisions:
        raise ValueError(f"{rel(SRC['decr'])} 缺 decisions")
    for d in decisions:
        if d["id"] in m["decr"]:
            raise ValueError(f"{rel(SRC['decr'])} 编号重复：{d['id']}")
        m["decr"][d["id"]] = {**d, "src": f"{rel(SRC['decr'])}#{d['id']}"}
    for v in vsd["value_streams"]:
        m["vs"][v["id"]] = {"name": v["name"], "category": v.get("category"), "value": v.get("value"),
                            "triggers": v.get("triggering_stakeholders") or [], "receivers": v.get("value_receivers") or []}
        for kind, key in (("sh-trigger", "triggering_stakeholders"), ("sh-receive", "value_receivers")):
            for sh in v.get(key) or []:
                link(kind, sh, v["id"], f"{rel(SRC['vs'])}#{v['id']}.{key}")
        if v.get("value"):
            m["vp"][f"VP-{v['id'][3:]}"] = {"name": v["value"], "vs": v["id"], "provisional": "取自价值流 value 字段，待 03 正式登记"}
            link("vp-vs", f"VP-{v['id'][3:]}", v["id"], f"{rel(SRC['vs'])}#{v['id']}.value")
        for st in v.get("stages") or []:
            gates = []
            for side in ("entry", "exit"):
                for g in gate_refs(st.get(side), v.get("stage_pattern") or {}):
                    if OBJ_RE.fullmatch(str(g.get("ref", ""))):
                        gates.append({"side": side, "obj": g["ref"], "state": g.get("state")})
            inside = [g["ref"] for g in st.get("in_stage_states") or [] if OBJ_RE.fullmatch(str(g.get("ref", "")))]
            caps = st.get("capabilities") or []
            m["stage"][st["id"]] = {"name": st["name"], "vs": v["id"], "gates": gates, "in_stage": inside, "caps": caps,
                                    "participants": st.get("participants") or []}
            for sh in st.get("participants") or []:
                link("sh-participate", sh, st["id"], f"{rel(SRC['vs'])}#{st['id']}.participants")
            for c in caps:
                m["cap"].setdefault(c, {"stages": []})["stages"].append(st["id"])
                link("cap-stage", c, st["id"], f"{rel(SRC['vs'])}#{st['id']}.capabilities")
            link("vs-stage", v["id"], st["id"], f"{rel(SRC['vs'])}#{st['id']}")
            for g in gates:
                link(f"stage-gate-{g['side']}", st["id"], g["obj"], f"{rel(SRC['vs'])}#{st['id']}.{g['side']}")

    load_objects(m, link)

    for p in yaml.safe_load(SRC["prc"].read_text(encoding="utf-8"))["processes"]:
        po = str(p.get("primary_object") or "")
        m["prc"][p["id"]] = {"name": p["name"], "type": p.get("type"), "stages": p.get("stages") or [],
                             "primary_object": OBJ_RE.findall(po), "bc": p.get("owner_bc"), "group": p.get("group")}
        if p.get("type") in INACTIVE_PRC:
            continue
        for s in p.get("stages") or []:
            link("stage-prc", s, p["id"], f"{rel(SRC['prc'])}#{p['id']}.stages")
        for o in OBJ_RE.findall(po):
            link("obj-prc", o, p["id"], f"{rel(SRC['prc'])}#{p['id']}.primary_object")

    load_planned_aggregates(m, link)
    text = SRC["agg"].read_text(encoding="utf-8")
    for bid, c, no in md_rows(text, "### 3.2", "### 3.3", r"B-\d{2,3}"):
        mod = re.search(r"`([a-z_]+\.[a-z_]+)`", c[2] if len(c) > 2 else "")
        aggs = expand("AGG", c[4] if len(c) > 4 else "")
        m["bc"][bid] = {"name": c[1], "module": mod.group(1) if mod else None, "aggs": aggs,
                        "src": f"{rel(SRC['agg'])}:{no}"}
        for a in aggs:
            if a in m["agg"]:
                m["agg"][a]["bc"] = bid
            link("bc-agg", bid, a, f"{rel(SRC['agg'])}:{no}")

    for s in yaml.safe_load(SRC["sd"].read_text(encoding="utf-8"))["subdomains"]:
        m["sd"][s["id"]] = {"name": s.get("zh")}
    for bid, b in m["bc"].items():
        ns = (b["module"] or "").split(".")[0]
        b["subdomain"] = ns if ns in m["sd"] else None
        if b["subdomain"]:
            link("sd-bc", ns, bid, f"{rel(SRC['sd'])}#{ns}")

    for f in sorted(SKELETON.glob("*/commands.yaml")):
        for c in (yaml.safe_load(f.read_text(encoding="utf-8")) or {}).get("commands") or []:
            agg = c.get("aggregate")
            m["act"][f"{agg}/{c['name']}"] = {"command": c["name"], "aggregate": agg, "bc": None,
                                              "state_change": c.get("state_change"), "src": f"{rel(f)}#{c['name']}"}
    m["steps"] = []
    for f in sorted(SKELETON.glob("*/processes*.yaml")) + sorted(SKELETON.glob("*/parts/*processes*.yaml")):
        d = yaml.safe_load(f.read_text(encoding="utf-8")) or {}
        for p in (d.get("processes") if isinstance(d, dict) else d) or []:
            for i, s in enumerate(p.get("steps") or [], 1):
                if s.get("aggregate"):
                    m["steps"].append({"prc": p["id"], "step": i, "aggregate": str(s["aggregate"]), "command": s.get("command"),
                                       "src": f"{rel(f)}#{p['id']}.steps[{i}]"})
    for a in yaml.safe_load(SRC["built"].read_text(encoding="utf-8"))["aggregates"]:
        for cmd in (a.get("commands") or []) + (a.get("commands_via") or []):
            m["act"][f"{a['bc']}/{cmd}"] = {"command": cmd, "aggregate": a["id"], "bc": a.get("bc"),
                                            "src": f"{rel(SRC['built'])}#{a['id']}"}
    mod_to_bc = {b["module"]: bid for bid, b in m["bc"].items() if b["module"]}
    for context in read_rows("bc", "contexts"):
        module = context["id"]
        if context.get("kind") == "shared-kernel" or context.get("status") == "retired" or module in mod_to_bc:
            continue
        m["bc"][module] = {"name": context["zh"], "module": module, "aggs": [], "subdomain": context.get("subdomain") if context.get("subdomain") in m["sd"] else None,
                           "src": f"{rel(SRC['bc'])}#contexts/{module}"}
        mod_to_bc[module] = module
        if context.get("subdomain") in m["sd"]:
            link("sd-bc", context["subdomain"], module, m["bc"][module]["src"])
    load_tables(m, link, mod_to_bc)
    load_constraints(m, link)
    load_interfaces(m, link, mod_to_bc)
    load_org_roles(m, link)
    load_commands(m, link)
    load_events(m, link, mod_to_bc)
    load_facts(m, link, mod_to_bc)
    load_dimensions(m, link)
    load_metrics(m, link)
    load_bindings(m, link)
    return m, links, mod_to_bc


def check(m, anchoring=None):
    gaps = defaultdict(list)
    if anchoring is not None:
        gaps["N07-1"] = [f"{pid} {name}（{why}）" for pid, name, why in anchoring["C"]["C1"]]
        gaps["N09-1"] = [f"{o} {name}（{why}）" for o, name, why in anchoring["C"]["C2"]]
    active_obj = {k for k, o in m["obj"].items() if o["active"]}
    primary = {k for k in active_obj if m["obj"][k]["primary"]}
    live_prc = {k: p for k, p in m["prc"].items() if p["type"] not in INACTIVE_PRC}
    obj_with_prc = {o for p in live_prc.values() for o in p["primary_object"]}
    obj_with_agg = {o for a in m["agg"].values() for o in a["objs"]}
    staged_obj = {g["obj"] for s in m["stage"].values() for g in s["gates"]} | {o for s in m["stage"].values() for o in s["in_stage"]}
    stage_metric = {s for mt in m["metric"].values() if mt.get("kind") != "技术度量" for s in mt["stages"]}

    top = {k for k in primary if m["obj"][k].get("class", "业务对象") == "业务对象"}

    def chain(o, seen=()):
        """顺依附链上行，返回追到的一级业务对象（可多个，「或」关系任一可达即可）。"""
        if o in top:
            return {o}
        if o in seen or o not in m["obj"]:
            return set()
        return set().union(*[chain(p, seen + (o,)) for p in m["obj"][o].get("parents", [])] or [set()])

    def in_cycle(o):
        """沿父对象上行能回到自身则返回环路，否则 None。"""
        stack = [(o, (o,))]
        while stack:
            cur, path = stack.pop()
            for p in m["obj"].get(cur, {}).get("parents", []):
                if p == o:
                    return path + (o,)
                if p not in path:
                    stack.append((p, path + (p,)))
        return None

    for o in sorted(active_obj, key=lambda x: int(x[4:])):
        ob = m["obj"][o]
        bad = [p for p in ob.get("parents", []) if p not in active_obj]
        cyc = in_cycle(o)
        if o in primary and ob.get("parents"):
            gaps["N09-4"].append(f"{o} {ob['name']}（一级对象不应有父对象：{'、'.join(ob['parents'])}）")
        elif o not in primary and not ob.get("parents"):
            gaps["N09-4"].append(f"{o} {ob['name']}（非一级，父对象未填 OBJ 编号：{ob.get('parents_raw') or '空'}）")
        elif bad:
            gaps["N09-4"].append(f"{o} {ob['name']}（父对象 {'、'.join(bad)} 不是有效对象）")
        elif cyc:
            gaps["N09-4"].append(f"{o} {ob['name']}（依附链成环：{' → '.join(cyc)}）")

    def gate_note(objs):
        return "、".join(f"{o}→{'、'.join(sorted(chain(o))) or '断'}" for o in objs)

    for vid, v in m["vs"].items():
        objs = sorted({g["obj"] for s in m["stage"].values() if s["vs"] == vid for g in s["gates"]})
        if not any(chain(o) for o in objs):
            gaps["N06-2"].append(f"{vid} {v['name']}（门对象 {gate_note(objs) or '无'}）")
        if not v.get("triggers") or not v.get("receivers"):
            gaps["N06-4"].append(f"{vid} {v['name']}（" + "、".join(x for x, ok in (("缺触发者", v.get("triggers")), ("缺价值接收者", v.get("receivers"))) if not ok) + "）")
    for sid, s in m["stage"].items():
        objs = sorted({g["obj"] for g in s["gates"]})
        if not any(chain(o) for o in objs):
            gaps["N06-3"].append(f"{sid} {s['name']}（门对象 {gate_note(objs) or '无'}）")
    involved = {x for v in m["vs"].values() for x in v.get("triggers", []) + v.get("receivers", [])} | {x for s in m["stage"].values() for x in s.get("participants", [])}

    obj_text = m.get("obj_text", {})
    decr_subj = {k: set(re.findall(r"SH-[A-Z]\d+[a-z]?|OBJ-\d+", str(d.get("subject", "")))) for k, d in m.get("decr", {}).items()}

    def under(o, root):
        """o 是 root 本身或顺依附链在 root 之下。"""
        if o == root:
            return True
        stack, seen = list(m["obj"].get(o, {}).get("parents", [])), set()
        while stack:
            p = stack.pop()
            if p == root:
                return True
            if p not in seen:
                seen.add(p)
                stack += m["obj"].get(p, {}).get("parents", [])
        return False

    def found(o, values):
        txt = obj_text.get(o, "")
        vals = [v.strip() for v in re.split(r"[、，,]", str(values or "")) if v.strip()]
        return [v for v in vals if v not in txt] if vals else ["（未填取值）"]

    for cname, c in m.get("shcat", {}).items():
        o = c.get("obj")
        if not o:
            if not c.get("decr") or c["decr"] not in decr_subj:
                gaps["N01-3"].append(f"{cname}（无对应对象，且未登记 04）")
            continue
        if o not in top:
            gaps["N01-3"].append(f"{cname}（对应 {o} 不是一级业务对象）")
            continue
        holders = [x for x in m["obj"] if under(x, o)]
        miss = [v for v in re.split(r"[、，,]", str(c.get("role") or "")) if v and not any(v in obj_text.get(x, "") for x in holders)]
        if miss:
            gaps["N01-3"].append(f"{cname}（{o} 及其从属对象没有{c.get('role_attr') or '角色'}取值：{'、'.join(miss)}）")
    for sid, s in m["sh"].items():
        if not s.get("in_system"):
            continue
        how, o, cat = s["in_system"], s.get("obj"), m.get("shcat", {}).get(s.get("category"))
        if how not in IN_SYSTEM:
            gaps["N01-4"].append(f"{sid} {s['name']}（体现「{how}」不在五选一内）")
        elif how == "不进系统":
            if not s.get("decr") or sid not in decr_subj.get(s["decr"], ""):
                gaps["N01-4"].append(f"{sid} {s['name']}（不进系统，但 04 未登记）")
        elif not o or o not in active_obj:
            gaps["N01-4"].append(f"{sid} {s['name']}（承载对象 {o or '空'} 不是有效对象）")
        elif cat and cat.get("obj") and not under(o, cat["obj"]):
            gaps["N01-4"].append(f"{sid} {s['name']}（{o} 不在类别「{s['category']}」的对象 {cat['obj']} 之下）")
        elif how != "用户角色" and not s.get("attr"):
            gaps["N01-4"].append(f"{sid} {s['name']}（未填承载属性）")
        elif how != "用户角色" and found(o, s.get("value")):
            gaps["N01-4"].append(f"{sid} {s['name']}（{o}「{s.get('attr')}」没有取值：{'、'.join(found(o, s.get('value')))}）")
    for sid, s in m["sh"].items():
        if s.get("category") not in (None, "—") and s.get("in_system") and s["category"] not in m.get("shcat", {}):
            gaps["N01-3"].append(f"{sid} {s['name']}（类别「{s['category']}」未在 stakeholder_categories 登记）")
        if not s.get("in_system"):
            gaps["N01-4"].append(f"{sid} {s['name']}（未登记在系统中的体现）")

    for sid, s in m["sh"].items():
        if sid not in involved:
            gaps["N01-2"].append(f"{sid} {s['name']}")
        if not any(c.get("sh") == sid for c in m["con"].values()):
            gaps["N01-1"].append(f"{sid} {s['name']}")
    for vid, v in m["vs"].items():
        if not v.get("value"):
            gaps["N03-1"].append(f"{vid} {v['name']}")
    for sid, s in m["stage"].items():
        if not s.get("caps"):
            gaps["N05-1"].append(f"{sid} {s['name']}")
    for aid, a in m["act"].items():
        bc = a.get("bc") or (m["agg"].get(a.get("aggregate") or "", {}) or {}).get("bc")
        if not bc:
            gaps["N10-1"].append(f"{aid}（聚合 {a.get('aggregate')} 未归 BC）")
    strip = lambda c: re.sub(r"^〔缺〕", "", str(c or "")).strip()
    registered = {(a.get("aggregate"), strip(a["command"])) for a in m["act"].values()}
    seen = set()
    for s in m.get("steps", []):
        if (s["prc"], s["step"]) in seen:
            continue
        seen.add((s["prc"], s["step"]))
        aggs = expand("AGG", s["aggregate"]) or [s["aggregate"]]
        if not strip(s["command"]):
            gaps["N07-P1"].append(f"{s['prc']} 第 {s['step']} 步（{s['aggregate']}）未声明 Command")
        elif not any((a, strip(s["command"])) in registered for a in aggs):
            gaps["N07-P1"].append(f"{s['prc']} 第 {s['step']} 步命令「{s['command']}」未在 {s['aggregate']} 的 commands.yaml 登记")
    check_facts(m, gaps)
    if "dim" in m:
        check_dimensions(m, gaps)
    check_bindings(m, gaps)

    for sid, s in m["stage"].items():
        if not s["gates"]:
            gaps["N06-1"].append(f"{sid} {s['name']}")
        if sid not in stage_metric:
            gaps["N13-1"].append(f"{sid} {s['name']}")
    for o in sorted(primary):
        if anchoring is None and o not in obj_with_prc:
            gaps["N09-1"].append(f"{o} {m['obj'][o]['name']}")
        if m["obj"][o].get("class", "业务对象") != "业务对象":
            continue
        if o not in obj_with_agg:
            gaps["N09-2"].append(f"{o} {m['obj'][o]['name']}")
    for aid, a in m["agg"].items():
        if not a["bc"]:
            gaps["N09-M1"].append(f"{aid} {a['name']}")
        if not a["invs"] and not any(aid in i["aggs"] for i in m["inv"].values()):
            gaps["N09-M2"].append(f"{aid} {a['name']}")
        if not a["tables"]:
            gaps["N09-M3"].append(f"{aid} {a['name']}")
        for o in a["objs"]:
            if o in active_obj and o not in staged_obj:
                gaps["N09-3"].append(f"{aid} → {o} {m['obj'][o]['name']}")
    for bid, b in m["bc"].items():
        if not b.get("subdomain"):
            gaps["N10-M1"].append(f"{bid} {b['name']}（模块 {b['module']}）")
    for pid, p in live_prc.items():
        bad = [s for s in p["stages"] if s not in m["stage"]]
        if anchoring is None and (not p["stages"] or bad):
            gaps["N07-1"].append(f"{pid} {p['name']}" + (f"（阶段不存在：{'、'.join(bad)}）" if bad else "（未挂阶段）"))
        dangling = [o for o in p["primary_object"] if o not in active_obj]
        if dangling:
            gaps["N07-2"].append(f"{pid} {p['name']}（主要对象 {'、'.join(dangling)} 不是有效对象）")
    for iid, i in m["inv"].items():
        if not i["aggs"] or any(a not in m["agg"] for a in i["aggs"]):
            gaps["N08-1"].append(f"{iid}（聚合 {'、'.join(i['aggs']) or '无'}）")
    for t, r in m["table"].items():
        if not r.get("obj") or r["obj"] not in active_obj:
            gaps["N09-M4"].append(f"{t}（对象 {r.get('obj') or '无'}）")
    for mid, mt in m["metric"].items():
        if mt.get("kind") != "技术度量" and not mt["stages"] and not mt["processes"]:
            gaps["N13-2"].append(f"{mid} {mt['zh']}")
    check_coverage(m, gaps)
    check_commands(m, gaps)
    check_events(m, gaps)
    check_org_roles(m, gaps)
    return gaps


def source_files():
    sources = {k: path for k, path in SRC.items() if path.is_file()}
    sources.update({f"evt_{name}": SRC["events"] / f"{name}.yaml" for name in EVENT_CATALOGS})
    return sources


def render(m, links, gaps):
    coverage = coverage_counts(m, links)
    old_n13_3 = sum(mt.get("kind") != "技术度量" and (not mt.get("facts") or not mt.get("bind"))
                    for mt in m.get("metric", {}).values())
    m = {k: v for k, v in m.items() if k not in ("obj_text", "cmd_class")}
    counts = {k: len(v) for k, v in m.items()}
    model = {"meta": {"generator": "architecture/panorama/panorama_build.py", "sources": {k: rel(v) for k, v in source_files().items()},
                      "counts": counts, "checks": CHECKS, "coverage": coverage,
                      "external_reads": {k: len(v) for k, v in read_mapping(SRC["external"]).items() if isinstance(v, list)}},
             "nodes": m, "links": links}
    lines = ["<!-- 由 architecture/panorama/panorama_build.py 生成，勿手改 -->", "", "# 全景追溯缺口", "",
             "每轮生成一次，反复跑到收敛。缺口是「缺上层」还是「本身多余」，由 one-architecture 逐条判定，写进层笔记的待决问题。", "",
             "## N13-3 判据变更", "",
             f"同一输入按改前判据计 {old_n13_3} 个缺口，按改后判据计 {len(gaps.get('N13-3', []))} 个缺口。改前同时要求指标自带 facts 与 bind，改后按计算绑定判定；绑定的事实、公式版本与时间基准缺失由 N14-1 报告。技术度量均排除，pending 仍计缺口并附原因前 40 字。", "",
             *([f"{rel(SRC['bindings'])} 不存在：节点 14 为空，非技术度量指标全部计入 N13-3。", ""] if not SRC["bindings"].exists() else []),
             "## 覆盖率", "", "按起点去重；via 列为每种出处下已覆盖起点数（可重叠），不是执行通过率。表→聚合只统计 data-access-registry 的三类业务表；API→BC 按路径统计，方法数见 api 节点。", "",
             "| 关系 | 已覆盖 / 总数 | via（已覆盖起点数） |", "|---|---|---|",
             *[f"| {k} | {v['covered']} / {v['total']} | " + "、".join(f"{via}: {n}" for via, n in v['via'].items()) + " |" for k, v in coverage.items()], "",
             "登记表其他 role（只计数）：" + "、".join(f"{role} {n}" for role, n in sorted(Counter(t['role'] for t in m['table'].values() if t.get('registered') and t.get('role') not in BUSINESS_ROLES).items())) + "。", "",
             "| 节点 | 层 | 条目数 |", "|---|---|---|", *[f"| {LAYER_NODE[k]} {NODES.get(LAYER_NODE[k], '')} | {k} | {v} |" for k, v in counts.items()], "",
             "对象按分类：" + "、".join(f"{c} {n}" for c, n in sorted(Counter(o.get("class", "业务对象") for o in m["obj"].values()).items())) + "。", "",
             "| 检查 | 含义 | 缺口 |", "|---|---|---|", *[f"| {k} | {d} | {UNAUTOMATED[k] + '，未自动化' if k in UNAUTOMATED else len(gaps.get(k, []))} |" for k, d in CHECKS.items()], ""]
    for k, d in CHECKS.items():
        if gaps.get(k):
            lines += [f"## {k} {d}（{len(gaps[k])}）", "", *[f"- {g}" for g in gaps[k]], ""]
    if not SRC["cmd_map"].exists():
        lines += ["## CMD→EVT", "", f"未运行：{rel(SRC['cmd_map'])} 不存在，按派工卡跳过命令类到 CMD 的对照与 CMD→EVT 连线。", ""]
    return yaml.safe_dump(model, allow_unicode=True, sort_keys=False, width=200), "\n".join(lines)


def stakeholder_table(vsd, text):
    """STAKEHOLDERS.md 的干系人图（四列 + 在系统中的体现），替换标记之间的内容。"""
    if SH_BEGIN not in text or SH_END not in text or text.index(SH_BEGIN) > text.index(SH_END):
        raise ValueError(f"{rel(OUT_SH)} 缺生成标记")
    cats = vsd.get("stakeholder_categories") or []
    rows = ["### 类别与一级业务对象", "", "| 类型 | 类别 | 对应一级业务对象 | 说明 |", "|---|---|---|---|"]
    rows += [f"| {c['type']} | {c['category']} | {c.get('obj') or '无（' + c.get('decr', '') + '）'}{'，' + c['role_attr'] + '：' + c['role'] if c.get('role') else ''} | {c['desc']} |" for c in cats]
    rows += ["", "### 具体干系人", "", "| 编号 | 类型 | 类别 | 具体干系人 | 说明 | 在系统中的体现 | 承载 | 关系 |", "|---|---|---|---|---|---|---|---|"]
    for s in vsd.get("stakeholders") or []:
        who = s.get("members") if s.get("category") == "—" else f"{s.get('sub')}：{s.get('members')}" if s.get("sub") not in (None, "—") else s.get("members")
        carry = s.get("decr") if s.get("in_system") == "不进系统" else f"{s.get('obj')}「{s.get('attr')}」= {s.get('value')}"
        rows.append(f"| {s['id']} | {s.get('type')} | {s.get('category')} | {who} | {s.get('desc', '')} | {s.get('in_system', '')} | {carry} | {s.get('relation', '参')} |")
    a, b = text.index(SH_BEGIN) + len(SH_BEGIN), text.index(SH_END)
    return text[:a] + "\n\n" + "\n".join(rows) + "\n\n" + text[b:]


def self_test():
    assert cells("| AGG-01 | x |") == ["AGG-01", "x"]
    assert expand("AGG", "AGG-01、02、33；REF-01") == ["AGG-01", "AGG-02", "AGG-33"]
    pat = {"exit_common": {"all": [{"any": [{"ref": "OBJ-35"}, {"all": [{"ref": "OBJ-30"}]}]}]}}
    assert [g["ref"] for g in gate_refs({"pattern": "exit_common", "all": [{"ref": "OBJ-38"}]}, pat)] == ["OBJ-35", "OBJ-30", "OBJ-38"]
    for bad in ({"pattern": "nope"}, {"any": ["OBJ-35"]}):
        try:
            gate_refs(bad, pat)
            raise AssertionError(f"应失败：{bad}")
        except ValueError:
            pass
    assert expand("AGG", "AGG-08–12、18、102") == ["AGG-08", "AGG-09", "AGG-10", "AGG-11", "AGG-12", "AGG-18", "AGG-102"]
    m = {"sh": {}, "con": {}, "act": {}, "vs": {}, "stage": {"VS-01.1": {"name": "s", "vs": "VS-01", "gates": [], "in_stage": []}},
         "obj": {"OBJ-01": {"name": "o", "active": True, "primary": True}}, "prc": {}, "agg": {}, "inv": {},
         "bc": {}, "sd": {}, "table": {}, "metric": {}}
    g = check(m)
    assert g["N06-1"] and g["N09-1"] and g["N09-2"] and g["N13-1"]
    assert g["N06-3"] and not g["N09-4"]
    m["sh"] = {"SH-A": {"name": "甲"}, "SH-B": {"name": "乙"}}
    m["vs"] = {"VS-01": {"name": "v", "triggers": ["SH-A"], "receivers": []}}
    m["stage"]["VS-01.1"]["gates"] = [{"side": "entry", "obj": "OBJ-03"}]
    m["obj"].update({"OBJ-02": {"name": "c", "active": True, "primary": False, "parents": ["OBJ-01"]},
                     "OBJ-03": {"name": "d", "active": True, "primary": False, "parents": ["OBJ-02"]},
                     "OBJ-04": {"name": "e", "active": True, "primary": False, "parents": []},
                     "OBJ-05": {"name": "f", "active": True, "primary": False, "parents": ["OBJ-09"]}})
    g = check(m)
    assert not g["N06-2"] and not g["N06-3"], (g["N06-2"], g["N06-3"])   # 两跳依附链可达一级
    assert [x[:6] for x in g["N09-4"]] == ["OBJ-04", "OBJ-05"], g["N09-4"]
    m["obj"]["OBJ-01"]["parents"] = ["OBJ-02"]
    m["obj"]["OBJ-06"] = {"name": "g", "active": True, "primary": False, "parents": ["OBJ-07"]}
    m["obj"]["OBJ-07"] = {"name": "h", "active": True, "primary": False, "parents": ["OBJ-06"]}
    g = check(m)
    assert [x[:6] for x in g["N09-4"]] == ["OBJ-01", "OBJ-02", "OBJ-04", "OBJ-05", "OBJ-06", "OBJ-07"], g["N09-4"]   # 一级填了父对象（且与 OBJ-02 成环）；OBJ-06、07 互指成环
    assert "一级对象不应有父对象" in g["N09-4"][0] and all("成环" in g["N09-4"][i] for i in (1, 4, 5))
    m["obj"]["OBJ-01"]["parents"] = []
    del m["obj"]["OBJ-06"], m["obj"]["OBJ-07"]
    assert g["N01-2"] == ["SH-B 乙"] and g["N06-4"] == ["VS-01 v（缺价值接收者）"], (g["N01-2"], g["N06-4"])
    m["stage"]["VS-01.1"]["gates"] = [{"side": "entry", "obj": "OBJ-04"}]
    g = check(m)
    assert g["N06-2"] and g["N06-3"]   # 断链：门对象追不到一级
    m["obj_text"] = {"OBJ-01": "往来单位", "OBJ-02": "角色：客户；供应商", "OBJ-03": "决策链角色：发起人"}
    m["decr"] = {"DECR-01": {"subject": "SH-D1 公众"}, "DECR-02": {"subject": "SH-J10 别的"}}
    m["shcat"] = {"客户": {"obj": "OBJ-01", "role": "客户"}, "监管": {"obj": "OBJ-01", "role": "主管机关"}, "公众": {"obj": None, "decr": "DECR-01"},
                  "伙伴": {"obj": "OBJ-02"}}
    m["sh"] = {"SH-A": {"name": "甲", "category": "客户", "in_system": "只作属性或参与者", "obj": "OBJ-03", "attr": "决策链角色", "value": "发起人"},
               "SH-B": {"name": "乙", "category": "客户", "in_system": "往来单位加角色", "obj": "OBJ-02", "attr": "客户类型", "value": "开发商"},
               "SH-C": {"name": "丙", "category": "客户", "in_system": "飞鸽", "obj": "OBJ-02"},
               "SH-D1": {"name": "丁", "category": "公众", "in_system": "不进系统", "decr": "DECR-01"},
               "SH-E": {"name": "戊", "category": "公众", "in_system": "不进系统"},
               "SH-F": {"name": "己", "category": "客户", "in_system": "往来单位加角色", "obj": "OBJ-05", "value": "x"},
               "SH-G": {"name": "庚", "category": "客户"},
               "SH-H": {"name": "辛", "category": "客户", "in_system": "往来单位加角色", "obj": "OBJ-02", "attr": "角色"},
               "SH-I": {"name": "壬", "category": "客户", "in_system": "往来单位加角色", "obj": "OBJ-02", "value": "客户"},
               "SH-J1": {"name": "癸", "category": "公众", "in_system": "不进系统", "decr": "DECR-02"},
               "SH-K": {"name": "子", "category": "未登记", "in_system": "用户角色", "obj": "OBJ-01"},
               "SH-L": {"name": "丑", "category": "客户", "in_system": "用户角色", "obj": "OBJ-01", "value": "随便"}}
    g = check(m)
    assert g["N01-3"] == ["监管（OBJ-01 及其从属对象没有角色取值：主管机关）", "伙伴（对应 OBJ-02 不是一级业务对象）",
                          "SH-K 子（类别「未登记」未在 stakeholder_categories 登记）"], g["N01-3"]
    # 缺取值、体现不在五选一、未登记 04、对象不在类别之下、空取值、未填属性、04 主题只含 SH-J10 不算 SH-J1、未登记体现；用户角色不查取值（SH-L 通过）
    assert [x.split(" ")[0] for x in g["N01-4"]] == ["SH-B", "SH-C", "SH-E", "SH-F", "SH-H", "SH-I", "SH-J1", "SH-G"], g["N01-4"]
    assert "（未填取值）" in g["N01-4"][4] and "未填承载属性" in g["N01-4"][5]
    page = f"头\n{SH_BEGIN}\n旧\n{SH_END}\n尾"
    out = stakeholder_table({"stakeholder_categories": [{"type": "外部", "category": "客户", "obj": "OBJ-01", "desc": "d"}],
                             "stakeholders": [{"id": "SH-A", "type": "外部", "category": "客户", "sub": "甲", "members": "m", "desc": "d",
                                               "in_system": "往来单位加角色", "obj": "OBJ-02", "attr": "角色", "value": "客户"}]}, page)
    assert "旧" not in out and "| SH-A | 外部 | 客户 | 甲：m |" in out and out.endswith("尾")
    for bad in ("头", f"{SH_END}\n{SH_BEGIN}"):
        try:
            stakeholder_table({}, bad)
            raise AssertionError("缺标记或标记倒置应失败")
        except ValueError:
            pass
    txt = "## 1. 总表\n| 编号 | 对象 |\n|---|---|\n| OBJ-01 | 往来单位 | 甲 |\n### 1.1\n## 2. x\n### 2.1 往来单位与角色\n- 项目内身份：物业\n### 2.2 别的\n- 往来单位 监理\n## 3."
    row = dict.fromkeys(("definition", "level", "type", "key_states", "relations", "rules", "facts", "source", "term", "scope", "parent_text"), "")
    ot = obj_texts([{**row, "id": "OBJ-01", "name": "往来单位"}], txt)
    assert "物业" in ot["OBJ-01"] and "监理" not in ot["OBJ-01"], ot   # 只取标题含对象名或编号的小节
    import unittest
    import test_panorama_build
    import test_scan_org_roles
    suite = unittest.TestSuite(unittest.defaultTestLoader.loadTestsFromModule(module)
                               for module in (test_panorama_build, test_scan_org_roles))
    result = unittest.TextTestRunner().run(suite)
    if not result.wasSuccessful():
        raise AssertionError("ARC-1021-ARC-04 自测失败")
    print("self-test ok")


EXTRACTS = [ARCH / "ssot/12-facts/extract_facts.py", ARCH / "ssot/13-dimensions/extract_dimensions.py", ARCH / "ssot/15-kpis/extract_metrics.py"]
UNIT_TESTS = [Path(__file__).parent / "test_scan_aggregates.py", Path(__file__).parent / "test_panorama_build.py",
              *(path.with_name("test_" + path.name) for path in EXTRACTS)]


def run_unit_tests():
    for path in UNIT_TESTS:
        r = subprocess.run([sys.executable, "-m", "unittest", path.stem], cwd=path.parent, capture_output=True, text=True)
        if r.returncode:
            raise AssertionError(f"{rel(path)} 未通过：\n{r.stderr[-2000:]}")


def main():
    if "--self-test" in sys.argv:
        self_test()
        run_unit_tests()
        return 0
    require_api()
    scan_aggregates.generate(ROOT, check="--check" in sys.argv)
    for script in EXTRACTS:
        subprocess.run([sys.executable, str(script), *(["--check"] if "--check" in sys.argv else [])], check=True)
    scan_commands.generate(ROOT, check="--check" in sys.argv)
    scan_org_roles.generate(ROOT, check="--check" in sys.argv)
    m, links, _ = load()
    vsd = yaml.safe_load(SRC["vs"].read_text(encoding="utf-8"))
    old_sh = OUT_SH.read_text(encoding="utf-8")
    new_sh = stakeholder_table(vsd, old_sh)
    gaps = check(m, anc.compute())
    model, report = render(m, links, gaps)
    outputs = {OUT_MODEL: model, OUT_GAPS: report, OUT_SH: new_sh}
    if "--check" in sys.argv:
        different = [rel(path) for path, content in outputs.items()
                     if not path.is_file() or path.read_bytes() != content.encode("utf-8")]
        if different:
            raise ValueError("全景生成物不一致：" + "、".join(different))
    else:
        for path, content in outputs.items():
            if not path.is_file() or path.read_bytes() != content.encode("utf-8"):
                path.write_text(content, encoding="utf-8")
    print("panorama: " + "、".join(f"{k} {len(v)}" for k, v in m.items() if k != "cmd_class") + "；缺口 " + "、".join(f"{k} {len(gaps.get(k, []))}" for k in CHECKS))
    return 0


if __name__ == "__main__":
    sys.exit(main())
