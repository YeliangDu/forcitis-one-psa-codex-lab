#!/usr/bin/env python3
"""从 intent/ 片段生成按主题的现行决定页 CURRENT_DECISIONS.md，上限取自 policy.json 的 decisions.current_page_max_kb（GOV-1023-FV2 G4，定稿 1.5、H14）。
取代 build_decision_digest.py（速查表）与 INTENT.md；产物不入仓（.gitignore），由本脚本在本机生成（new_intent.py 记决定后自动跑）。引用条目写文件名前缀，摘句不作依据，引用前打开原文。
页面只列现行条目：常驻（decision-digest/standing.txt 指定，按小节）、新近（最近 5 条非常驻）、主题索引（按 decision-digest/topics.txt 分类，每主题只记条数）；超限即失败，删减 standing.txt；--topic=<主题> 把该主题全部现行条目打到终端。
取代：片段「## 取代」段与 decision-digest/relations.txt；含推翻字样却没登记的只告警。归档：decision-digest/archived.txt 每行「完整文件名<TAB>原因码」，归档的不上页、不计入主题索引、不进疑似告警，--archived 列出。沿用原脚本的校验：文件名须为 <日期>-<HHMMSS>-<会话名>-<短名>.md 或旧式 <日期>-<三位序号>-<短名>.md；
格式「## 原话」「## 落点」[「## 取代」][规则、影响的 ADR、单子、题号、类别、负责会话、影响路径]，原话落点非空；topics.txt 无重复主题与空白关键词；取代引用不存在的文件、循环取代、新条目早于旧条目都失败。
用法：python3 scripts/session/build_decisions.py [--check | --self-test | --topic=<主题> | --archived]（--check 只校验与量体，不写文件；不收目录参数）
"""
import json, os, re, sys, tempfile

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
BASE = os.path.join(ROOT, "docs", "modernization")
OUT, RECENT, CUT, POLICY = "CURRENT_DECISIONS.md", 5, 48, os.path.join(ROOT, "policy.json")
REVERSAL = ("改选", "看错了", "先等等", "不作为最终", "推翻", "已被", "作废")
REF = re.compile(r"[A-Za-z]+-[\dA-Za-z-]*\d[\w-]*|PR\s*#\d+|§[\d.]+|[\w./-]+\.(?:md|py|yaml|json|html|txt|yml)")
SECTION = re.compile(r"## 原话\n\n(.*?)\n\n## 落点\n\n(.*?)(?:\n\n## 取代\n\n(.*?))?\n(?:\n## (?:规则|影响的 ADR|单子|题号|类别|负责会话|影响路径)\n\n[^\n]+\n)*", re.S)
NAME = re.compile(r"(\d{4}-\d{2}-\d{2})-(\d{6}|\d{3})-")
ARCHIVE_REASONS = ("已入正本", "已完成", "会话退役", "已取消")

def read(path):
    return open(path, encoding="utf-8").read()

def limit_of(path=None):
    try: kb = json.loads(read(path or POLICY))["decisions"]["current_page_max_kb"]
    except (OSError, ValueError, KeyError, TypeError) as e: raise ValueError(f"读不到 decisions.current_page_max_kb：{e!r}")
    if isinstance(kb, bool) or not isinstance(kb, (int, float)) or kb <= 0: raise ValueError(f"decisions.current_page_max_kb 须为正数：{kb!r}")
    return int(kb * 1024)

def cell(text, cut=None):
    t = re.sub(r"\s+", " ", text).strip().replace("|", "／")
    return t if not cut or len(t) <= cut else t[:cut].rstrip() + "…"

def load_topics(path):
    rows = [l.split("\t") for l in read(path).splitlines() if l.strip() and not l.startswith("#")]
    names = [r[0].strip() for r in rows]
    if not rows or any(len(r) != 2 or not r[0].strip() or not r[1].strip() for r in rows):
        raise ValueError(f"{path}：没有主题，或有行不是「主题<TAB>关键词|关键词」")
    if len(set(names)) != len(names) or "未分类" in names or any(not k or k != k.strip() for r in rows for k in r[1].split("|")):
        raise ValueError(f"{path}：主题名重复或与保留的「未分类」冲突，或关键词为空、带首尾空白")
    return [(r[0].strip(), r[1].split("|")) for r in rows]

def load_entries(folder):
    keyed, entries = [], {}
    for f in os.listdir(folder):
        if not f.endswith(".md") or f.startswith("_"): continue
        if not (m := NAME.match(f)): raise ValueError(f"{f}：文件名须为 <日期>-<HHMMSS>-<会话名>-<短名>.md（用 new_intent.py 生成）")
        keyed.append(((m.group(1), len(m.group(2)) == 6, m.group(2), f), f))
        m = SECTION.fullmatch(read(os.path.join(folder, f)))
        if not m or not m.group(1).strip() or not m.group(2).strip(): raise ValueError(f"{f}：格式须为「## 原话」「## 落点」[「## 取代」]，原话与落点非空")
        entries[f] = (m.group(1), m.group(2), [s.strip().lstrip("-").strip().strip("`") for s in (m.group(3) or "").splitlines() if s.strip()])
    return entries, [f for _, f in sorted(keyed)]

def load_archived(path, entries):
    """{文件名: 原因码}；文件名不在 intent/ 下、原因码不在 ARCHIVE_REASONS 里、同一文件重复都失败。已被取代的也可归档。"""
    archived = {}
    for n, line in enumerate(read(path).splitlines(), 1):
        if not line.strip() or line.startswith("#"): continue
        parts = line.split("\t")
        if len(parts) != 2 or parts[0] not in entries: raise ValueError(f"{path}:{n}：须为「intent/ 下的完整文件名<TAB>原因码」")
        if parts[1] not in ARCHIVE_REASONS: raise ValueError(f"{path}:{n}：原因码须是 {'、'.join(ARCHIVE_REASONS)} 之一：{parts[1]!r}")
        if parts[0] in archived: raise ValueError(f"{path}:{n}：{parts[0]} 重复归档")
        archived[parts[0]] = parts[1]
    return archived

def load_standing(path, entries, sup, archived):
    """[(文件名, 小节)]，按文件顺序；文件名不在 intent/ 下、已被取代、已归档、重复、小节为空都失败。"""
    rows = []
    for n, line in enumerate(read(path).splitlines(), 1):
        if not line.strip() or line.startswith("#"): continue
        parts = line.split("\t")
        if len(parts) != 2 or parts[0] not in entries: raise ValueError(f"{path}:{n}：须为「intent/ 下的完整文件名<TAB>小节」")
        if not parts[1].strip(): raise ValueError(f"{path}:{n}：小节为空")
        if parts[0] in sup or parts[0] in archived: raise ValueError(f"{path}:{n}：{parts[0]} 已被取代或已归档，不能常驻")
        if parts[0] in [r[0] for r in rows]: raise ValueError(f"{path}:{n}：{parts[0]} 重复常驻")
        rows.append((parts[0], parts[1].strip()))
    return rows

def load_relations(path, entries, order):
    pos, sup, conflicts = {n: i for i, n in enumerate(order)}, {}, []
    def add_sup(new, old, why, where):
        if any(f not in entries for f in (new, old)): raise ValueError(f"{where}：{new} 或 {old} 不在 intent/ 下")
        if new == old or pos[new] < pos[old]: raise ValueError(f"{where}：取代方须是更晚的条目（{new} 不晚于 {old}）")
        lst = sup.setdefault(old, [])
        if new not in [n for n, _ in lst]: lst.append((new, why))
    for name, (_, _, refs) in entries.items():
        for old in refs: add_sup(name, old, "片段「## 取代」", name)
    for n, line in enumerate(read(path).splitlines(), 1):
        parts = line.split("\t")
        if not line.strip() or line.startswith("#"): continue
        if len(parts) != 4 or parts[0] not in ("取代", "矛盾"): raise ValueError(f"{path}:{n}：须为「取代|矛盾<TAB>文件<TAB>文件<TAB>说明」")
        if parts[0] == "取代": add_sup(parts[1], parts[2], parts[3], f"{path}:{n}")
        elif any(f not in entries for f in parts[1:3]) or parts[1] == parts[2]: raise ValueError(f"{path}:{n}：矛盾的两个文件须不同且都在 intent/ 下")
        else: conflicts.append(tuple(parts[1:4]))
    for name in sup: reaches(name, None, sup)
    return sup, conflicts

def reaches(a, b, sup):
    seen = [a]
    while a in sup:
        a = sup[a][0][0]
        if a in seen: raise ValueError(f"取代关系成环：{' → '.join(seen + [a])}")
        seen.append(a)
        if a == b: return True
    return False

def topics_of(topics, point, quote):
    point, quote = REF.sub(" ", point), REF.sub(" ", quote)
    def count(text, k):
        return len(re.findall(rf"(?<![A-Za-z0-9]){re.escape(k)}(?![A-Za-z0-9])", text, re.I)) if k.isascii() else text.count(k)
    sc = sorted(((sum(2 * count(point, k) + count(quote, k) for k in keys), -i, n) for i, (n, keys) in enumerate(topics)), reverse=True)
    if sc[0][0] == 0: return ["未分类"]
    return [sc[0][2]] + ([sc[1][2]] if len(sc) > 1 and sc[1][0] >= 3 and sc[1][0] >= 0.7 * sc[0][0] else [])

def short_ids(order):
    ids = {}
    for n in order:
        p = re.match(r"\d{4}-\d{2}-\d{2}-\d{3}(?:\d{3})?", n).group(0)
        while sum(o.startswith(p) for o in order) > 1 and len(p) < len(n): p = n[:len(p) + 1]
        ids[n] = p
    return ids

def build(entries, order, topics, sup, conflicts, standing, archived=()):
    groups, ids = {n: [] for n, _ in topics} | {"未分类": []}, short_ids(order)
    for name in order:
        for t in topics_of(topics, entries[name][1], entries[name][0]): groups[t].append(name)
    registered = {n for olds in sup.values() for n, _ in olds}
    suspects = [n for n in order if n not in registered and n not in sup and n not in archived and any(w in entries[n][0] + entries[n][1] for w in REVERSAL)]
    live = [c for c in conflicts if not (reaches(c[0], c[1], sup) or reaches(c[1], c[0], sup))]
    resident = [n for n, _ in standing]
    recent = [n for n in reversed(order) if n not in sup and n not in archived and n not in resident][:RECENT]
    line = lambda n: f"- `{ids[n]}` {cell(entries[n][1], CUT)}"
    out = [f"# 现行决定页（自动生成，勿手改）\n\n共 {len(order)} 条决定，现行 {len(order) - len(set(sup) | set(archived))} 条，被取代 {len(sup)} 条、归档 {len(archived)} 条（不列），疑似取代未登记 {len(suspects)} 条。标识是 intent/ 下文件名前缀；摘句不作依据，引用前打开原文。",
           f"常驻见 `decision-digest/standing.txt`；新近为最近 {RECENT} 条非常驻；其余见主题索引，`build_decisions.py --topic=<主题>` 查全。\n",
           "## 待用户裁定（互相矛盾且未被取代）\n"] + ([f"- `{ids[a]}` ⟷ `{ids[b]}`：{cell(why, 80)}" for a, b, why in live] or ["- 无登记的矛盾"])
    out.append("\n## 常驻")
    for section in dict.fromkeys(s for _, s in standing):
        out += [f"\n### {section}\n"] + [line(n) for n, s in standing if s == section]
    out += ["\n## 新近\n"] + ([line(n) for n in recent] or ["- 无"]) + ["\n## 主题索引（现行条数）\n"]
    for topic in [t for t, _ in topics] + ["未分类"]:
        out.append(f"- {topic} {len([n for n in groups[topic] if n not in sup and n not in archived])}")
    return "\n".join(out) + "\n", len(groups["未分类"]), suspects

def listing(folder, topics_path, relations_path, archived_path, topic):
    """topic 为 None 时列归档条目（--archived），否则列该主题的现行条目（不含归档）。"""
    entries, order = load_entries(folder)
    topics, ids, sup = load_topics(topics_path), short_ids(order), load_relations(relations_path, entries, order)[0]
    archived = load_archived(archived_path, entries)
    if topic is None: return [f"{ids[n]} 〔{archived[n]}〕 {cell(entries[n][1], 120)}" for n in order if n in archived]
    if topic not in [t for t, _ in topics] + ["未分类"]: raise ValueError(f"没有主题「{topic}」；已有主题：{'、'.join([t for t, _ in topics] + ['未分类'])}")
    return [f"{ids[n]} {cell(entries[n][1], 120)}" for n in order if n not in sup and n not in archived and topic in topics_of(topics, entries[n][1], entries[n][0])]

def run(folder, topics_path, relations_path, archived_path, standing_path, out_path, limit=None, policy=None):
    limit = limit or limit_of(policy)
    entries, order = load_entries(folder)
    topics = load_topics(topics_path)
    sup, conflicts = load_relations(relations_path, entries, order)
    archived = load_archived(archived_path, entries)
    standing = load_standing(standing_path, entries, sup, archived)
    text, unclassified, suspects = build(entries, order, topics, sup, conflicts, standing, archived)
    if len(text.encode()) > limit: raise ValueError(f"现行决定页 {len(text.encode())} 字节，超过上限 {limit}：删减 decision-digest/standing.txt（trim standing.txt）")
    if out_path: open(out_path, "w", encoding="utf-8").write(text)
    return len(entries), unclassified, suspects, text, len(archived), len(standing)

def self_test():
    def raises(fn):
        try: fn()
        except ValueError: return True
        return False
    def put(path, text):
        open(path, "w", encoding="utf-8").write(text)
    with tempfile.TemporaryDirectory() as d:
        intent, topics, rel, pol, arc = os.path.join(d, "intent"), os.path.join(d, "topics.txt"), os.path.join(d, "relations.txt"), os.path.join(d, "policy.json"), os.path.join(d, "archived.txt")
        std = os.path.join(d, "standing.txt")
        os.mkdir(intent)
        frag = lambda name, quote, point, sup="": put(os.path.join(intent, name), f"## 原话\n\n{quote}\n\n## 落点\n\n{point}\n" + (f"\n## 取代\n\n{sup}\n" if sup else ""))
        old, new, a, b = "2026-10-01-000001-a-旧.md", "2026-10-02-000001-a-新.md", "2026-10-03-000001-a-甲.md", "2026-10-03-000002-a-乙.md"
        [frag(*args) for args in [(old, "报销按三级审批", "报销审批三级|带竖线，详见 ADR-0007"), (new, "报销改两级，不再三级", "报销审批两级，仅金额超阈值时加一级" + "很长的尾巴" * 40), (a, "借款可冲抵", "借款冲抵报销"),
                     (b, "借款不冲抵", "借款与报销各自结算"), ("2026-10-04-000001-a-杂.md", "天气很好", "无关内容"), ("2026-10-04-000002-a-词界.md", "x", "PRINCIPLES 与 ADR-0001 ADR-0002 ADR-0003"),
                     ("2026-10-04-001-旧式序号.md", "旧式", "旧式序号文件名也认"), ("2026-10-04-000003-a-疑.md", "改选为只预警", "预算超额只预警")]]
        put(os.path.join(intent, "_header.md"), "忽略")
        put(topics, "审批\t审批\n财务\t借款|报销\n工作\tPR|ADR\n")
        put(rel, f"取代\t{new}\t{old}\t改两级\n矛盾\t{a}\t{b}\t冲抵之争\n")
        put(arc, "# 归档为空时旧用例不变\n")
        put(std, f"# 注释\n{new}\t财务规则\n")
        n, unclassified, suspects, text, _, _ = run(intent, topics, rel, arc, std, None, limit=10240)
        assert n == 8 and unclassified == 4 and len(suspects) == 1, (n, unclassified, suspects)   # 疑：含推翻字样但未登记，只告警
        assert "现行 7 条，被取代 1 条、归档 0 条" in text and "报销审批两级" in text and "报销审批三级" not in text and "很长的尾巴" * 20 not in text, "被取代的不列；常驻条目上页；摘句截断"
        assert "## 常驻\n\n### 财务规则\n" in text and "借款冲抵报销" not in text and "\n- 财务 3\n" in text and "--topic=<主题>" in text, "常驻按小节列；主题索引只记条数、不列条目（甲既非常驻也不在最近 5 条）"
        recent = lambda text: [l.split("`")[1] for l in text.split("## 新近\n")[1].split("\n## ")[0].split("\n") if l.startswith("- ")]
        assert recent(text) == ["2026-10-04-000003", "2026-10-04-000002", "2026-10-04-000001", "2026-10-04-001", "2026-10-03-000002"], f"新近列最近 5 条非常驻现行条目，新的在前：{recent(text)}"
        put(std, f"{new}\t财务规则\n2026-10-04-000003-a-疑.md\t预算\n")
        put(rel, read(rel) + "取代\t2026-10-04-000002-a-词界.md\t2026-10-04-000001-a-杂.md\t并入\n")
        assert recent(run(intent, topics, rel, arc, std, None, limit=10240)[3]) == ["2026-10-04-000002", "2026-10-04-001", "2026-10-03-000002", "2026-10-03-000001"], "新近跳过常驻与被取代的条目"
        put(rel, f"取代\t{new}\t{old}\t改两级\n矛盾\t{a}\t{b}\t冲抵之争\n")
        put(std, f"{new}\t财务规则\n")
        for body in (f"{old}\t财务规则\n", "2026-10-31-000001-a-无.md\t财务规则\n", f"{new}\t财务规则\n{new}\t别的\n", f"{new}\t \n", f"{new}\n"):
            put(std, body)
            assert raises(lambda: run(intent, topics, rel, arc, std, None, limit=10240)), f"常驻清单应拒绝（已被取代、不存在、重复、小节为空、缺小节）：{body!r}"
        put(std, f"2026-10-04-000003-a-疑.md\t预算\n")
        put(arc, "2026-10-04-000003-a-疑.md\t已完成\n")
        assert raises(lambda: run(intent, topics, rel, arc, std, None, limit=10240)), "已归档的不能常驻"
        put(std, f"{new}\t财务规则\n")
        assert "冲抵之争" in text and "`2026-10-04-001`" in text, "矛盾列出；旧式序号的标识"
        put(arc, f"# 注释\n2026-10-04-000003-a-疑.md\t已完成\n{old}\t已入正本\n")
        n, unclassified, suspects, text, m, _ = run(intent, topics, rel, arc, std, None, limit=10240)
        assert m == 2 and suspects == [] and "预算超额只预警" not in text and "现行 6 条，被取代 1 条、归档 2 条" in text, "归档的不上页、不进疑似告警；已被取代的也可归档，现行按并集算"
        assert listing(intent, topics, rel, arc, None) == ["2026-10-01-000001 〔已入正本〕 报销审批三级／带竖线，详见 ADR-0007", "2026-10-04-000003 〔已完成〕 预算超额只预警"], "--archived 列出归档条目"
        assert not any("预算超额只预警" in line for t in [t for t, _ in load_topics(topics)] + ["未分类"] for line in listing(intent, topics, rel, arc, t)), "按主题列时不列归档条目"
        for body in ("2026-10-31-000001-a-无.md\t已完成\n", "2026-10-04-000003-a-疑.md\t不想要了\n", "2026-10-04-000003-a-疑.md\t已完成\n2026-10-04-000003-a-疑.md\t已取消\n", "2026-10-04-000003-a-疑\t已完成\n"):
            put(arc, body)
            assert raises(lambda: run(intent, topics, rel, arc, std, None, limit=10240)), f"归档清单应拒绝（文件名不存在、原因码非法、重复、非完整文件名）：{body!r}"
        put(arc, "")
        put(rel, read(rel) + f"取代\t{b}\t{a}\t裁定\n")
        assert "冲抵之争" not in run(intent, topics, rel, arc, std, None, limit=10240)[3], "矛盾一方被取代后不再列（矛盾行仍在 relations.txt 里）"
        frag("2026-10-04-000009-a-重.md", "审批", "审批甲"), frag("2026-10-04-000009-b-重.md", "审批", "审批乙")
        ids = short_ids(load_entries(intent)[1])
        assert len(set(ids.values())) == len(ids) and ids["2026-10-04-000009-a-重.md"] != "2026-10-04-000009", "同一秒的两条标识须不同"
        cfg = '{"decisions": {"current_page_max_kb": %s}}'
        for body, want in ((cfg % "1", 1024), (cfg % "2.5", 2560), ("{}", 0), ("坏", 0), (cfg % "0", 0), (cfg % "-1", 0), (cfg % '"10"', 0), (cfg % "true", 0)):
            put(pol, body)
            assert (limit_of(pol) == want) if want else raises(lambda: limit_of(pol)), f"上限取自该键，缺失或非正数失败关闭：{body}"
        assert raises(lambda: limit_of(os.path.join(d, "无此文件"))), "policy.json 读不到应失败"
        put(pol, cfg % "0.3")
        assert raises(lambda: run(intent, topics, rel, arc, std, None, policy=pol)), "run 默认按 policy 的键判定体量"
        [frag(f"2026-11-01-{i:06d}-a-量{i}.md", "审批", f"审批第{i}号决定，内容较长较长较长较长较长较长较长较长较长较长") for i in range(300)]
        text = run(intent, topics, rel, arc, std, None, limit=10240)[3]
        assert len(text.encode()) <= 10240 and "--topic=<主题>" in text and "\n- 审批 " in text and sum("审批第" in l for l in text.splitlines()) == RECENT, "条目再多页面也只列常驻与新近，整页不超限，并给出查全写法"
        assert any("审批第0号" in l for l in listing(intent, topics, rel, arc, "审批")) and "审批第0号" not in text and not any("审批第" in l for l in listing(intent, topics, rel, arc, "财务")), "--topic 只列该主题、含页面省掉的现行条目"
        assert not any("报销审批三级" in l for l in listing(intent, topics, rel, arc, "审批")), "--topic 不列被取代的条目"
        assert all(raises(lambda t=t: listing(intent, topics, rel, arc, t)) for t in ("无此主题", "")), "--topic 未知或空主题名应报错"
        try: run(intent, topics, rel, arc, std, None, limit=300); assert False, "超限应失败"
        except ValueError as e: assert "standing.txt" in str(e), f"超限提示删减 standing.txt：{e}"
        [os.remove(os.path.join(intent, f)) for f in os.listdir(intent) if "量" in f]
        bad = [(rel, f"取代\tno.md\t{old}\tx\n"), (rel, f"矛盾\t{old}\t{old}\tx\n"), (rel, "乱写\n"), (rel, f"取代\t{old}\t{new}\tx\n"), (rel, f"取代\t{new}\t{old}\tx\n取代\t{old}\t{new}\tx\n"),
               (topics, "审批\t审批\n审批\t审批\n"), (topics, "未分类\tx\n"), (topics, "审批\t审批| 会签\n"), (topics, "# 只有注释\n"),
               (os.path.join(intent, "2026-10-07-000001-a-空.md"), "## 原话\n\n\n\n## 落点\n\n\n"), (os.path.join(intent, "2026-10-07-000002-a-坏.md"), "坏格式"), (os.path.join(intent, "2026-10-08-000001-a-尾.md"), "## 原话\n\nx\n\n## 落点\n\ny\n尾巴"),
               (os.path.join(intent, "乱名.md"), "## 原话\n\nx\n\n## 落点\n\ny\n")]
        for path, body in bad:
            good = read(path) if os.path.exists(path) else None
            put(path, body)
            assert raises(lambda: run(intent, topics, rel, arc, std, None, limit=10240)), f"应拒绝：{body!r}"
            put(path, good) if good is not None else os.remove(path)
    entries, order = load_entries(os.path.join(BASE, "intent"))
    topics = load_topics(os.path.join(BASE, "decision-digest", "topics.txt"))
    for prefix, topic in [l.split("\t") for l in read(os.path.join(BASE, "decision-digest", "expected.txt")).splitlines() if l.strip() and not l.startswith("#")]:
        hits = [n for n in order if n.startswith(prefix)]
        assert len(hits) == 1, f"expected.txt：{prefix} 命中 {len(hits)} 个片段"
        assert topic in topics_of(topics, entries[hits[0]][1], entries[hits[0]][0]), f"expected.txt：{prefix} 应含主题「{topic}」"
    print("ok")

if __name__ == "__main__":
    src = (os.path.join(BASE, "intent"), os.path.join(BASE, "decision-digest/topics.txt"), os.path.join(BASE, "decision-digest/relations.txt"), os.path.join(BASE, "decision-digest/archived.txt"))
    standing = os.path.join(BASE, "decision-digest/standing.txt")
    if "--self-test" in sys.argv: self_test()
    elif sys.argv[1:2] and (sys.argv[1].startswith("--topic=") or sys.argv[1] == "--archived"):
        try: print("\n".join(listing(*src, sys.argv[1][8:] if sys.argv[1].startswith("--topic=") else None)))
        except ValueError as e: sys.exit(str(e))
    else:
        n, unclassified, suspects, text, archived, resident = run(*src, standing, None if "--check" in sys.argv else os.path.join(BASE, OUT))
        print(f"{OUT}: {n} 条，归档 {archived}，常驻 {resident}，{len(text.encode())} 字节（上限 {limit_of()}），未分类 {unclassified}，疑似取代未登记 {len(suspects)}")
