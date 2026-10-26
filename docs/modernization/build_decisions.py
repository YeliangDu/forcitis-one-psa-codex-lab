#!/usr/bin/env python3
"""从 intent/ 片段生成按主题的现行决定页 CURRENT_DECISIONS.md，不超过 10 KB（GOV-1023-FV2 G4，定稿 1.5、H14）。
取代 build_decision_digest.py（速查表）与 INTENT.md；产物不入仓（.gitignore），由 build_all.py 生成。引用条目写文件名前缀，摘句不作依据，引用前打开原文。
只列现行条目，按主题（decision-digest/topics.txt）分组、新的在前；每主题列几条由体量决定（从 8 条往下减到整页 ≤10 KB），其余记条数。
取代：片段「## 取代」段与 decision-digest/relations.txt；含推翻字样却没登记的只告警。沿用原脚本的校验：文件名须为 <日期>-<HHMMSS>-<会话名>-<短名>.md 或旧式 <日期>-<三位序号>-<短名>.md；
格式「## 原话」「## 落点」[「## 取代」][规则、影响的 ADR、单子、题号]，原话落点非空；topics.txt 无重复主题与空白关键词；取代引用不存在的文件、循环取代、新条目早于旧条目都失败。
用法：python3 docs/modernization/build_decisions.py [--check | --self-test]（--check 只校验与量体，不写文件）
"""
import os, re, sys, tempfile

BASE = os.path.dirname(os.path.abspath(__file__))
OUT, LIMIT, PER_MAX, CUT = "CURRENT_DECISIONS.md", 10240, 8, 48
REVERSAL = ("改选", "看错了", "先等等", "不作为最终", "推翻", "已被", "作废")
REF = re.compile(r"[A-Za-z]+-[\dA-Za-z-]*\d[\w-]*|PR\s*#\d+|§[\d.]+|[\w./-]+\.(?:md|py|yaml|json|html|txt|yml)")
SECTION = re.compile(r"## 原话\n\n(.*?)\n\n## 落点\n\n(.*?)(?:\n\n## 取代\n\n(.*?))?\n(?:\n## (?:规则|影响的 ADR|单子|题号)\n\n[^\n]+\n)*", re.S)
NAME = re.compile(r"(\d{4}-\d{2}-\d{2})-(\d{6}|\d{3})-")

def read(path):
    return open(path, encoding="utf-8").read()

def cell(text, cut=None):
    t = re.sub(r"\s+", " ", text).strip().replace("|", "／")
    return t if not cut or len(t) <= cut else t[:cut].rstrip() + "…"

def load_topics(path):
    topics, seen = [], set()
    for n, line in enumerate(read(path).splitlines(), 1):
        if not line.strip() or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) != 2 or not parts[0].strip() or not parts[1].strip():
            raise ValueError(f"{path}:{n}：须为「主题<TAB>关键词|关键词」")
        name, keys = parts[0].strip(), parts[1].split("|")
        if name in seen or name == "未分类" or any(not k or k != k.strip() for k in keys):
            raise ValueError(f"{path}:{n}：主题名「{name}」重复或与保留的「未分类」冲突，或关键词为空、带首尾空白")
        seen.add(name)
        topics.append((name, keys))
    if not topics:
        raise ValueError(f"{path}：没有主题")
    return topics

def load_entries(folder):
    keyed, entries = [], {}
    for f in os.listdir(folder):
        if not f.endswith(".md") or f.startswith("_"):
            continue
        if not (m := NAME.match(f)):
            raise ValueError(f"{f}：文件名须为 <日期>-<HHMMSS>-<会话名>-<短名>.md（用 new_intent.py 生成）")
        keyed.append(((m.group(1), len(m.group(2)) == 6, m.group(2), f), f))
        m = SECTION.fullmatch(read(os.path.join(folder, f)))
        if not m or not m.group(1).strip() or not m.group(2).strip():
            raise ValueError(f"{f}：格式须为「## 原话」「## 落点」[「## 取代」]，原话与落点非空")
        entries[f] = (m.group(1), m.group(2), [s.strip().lstrip("-").strip().strip("`") for s in (m.group(3) or "").splitlines() if s.strip()])
    return entries, [f for _, f in sorted(keyed)]

def load_relations(path, entries, order):
    pos, sup, conflicts = {n: i for i, n in enumerate(order)}, {}, []
    def add_sup(new, old, why, where):
        if any(f not in entries for f in (new, old)):
            raise ValueError(f"{where}：{new} 或 {old} 不在 intent/ 下")
        if new == old or pos[new] < pos[old]:
            raise ValueError(f"{where}：取代方须是更晚的条目（{new} 不晚于 {old}）")
        lst = sup.setdefault(old, [])
        if new not in [n for n, _ in lst]:
            lst.append((new, why))
    for name, (_, _, refs) in entries.items():
        for old in refs:
            add_sup(name, old, "片段「## 取代」", name)
    for n, line in enumerate(read(path).splitlines(), 1):
        parts = line.split("\t")
        if not line.strip() or line.startswith("#"):
            continue
        if len(parts) != 4 or parts[0] not in ("取代", "矛盾"):
            raise ValueError(f"{path}:{n}：须为「取代|矛盾<TAB>文件<TAB>文件<TAB>说明」")
        if parts[0] == "取代":
            add_sup(parts[1], parts[2], parts[3], f"{path}:{n}")
        elif any(f not in entries for f in parts[1:3]) or parts[1] == parts[2]:
            raise ValueError(f"{path}:{n}：矛盾的两个文件须不同且都在 intent/ 下")
        else:
            conflicts.append(tuple(parts[1:4]))
    for name in sup:
        reaches(name, None, sup)
    return sup, conflicts

def reaches(a, b, sup):
    seen = [a]
    while a in sup:
        a = sup[a][0][0]
        if a in seen:
            raise ValueError(f"取代关系成环：{' → '.join(seen + [a])}")
        seen.append(a)
        if a == b:
            return True
    return False

def topics_of(topics, point, quote):
    point, quote = REF.sub(" ", point), REF.sub(" ", quote)
    def count(text, k):
        return len(re.findall(rf"(?<![A-Za-z0-9]){re.escape(k)}(?![A-Za-z0-9])", text, re.I)) if k.isascii() else text.count(k)
    sc = sorted(((sum(2 * count(point, k) + count(quote, k) for k in keys), -i, n) for i, (n, keys) in enumerate(topics)), reverse=True)
    if sc[0][0] == 0:
        return ["未分类"]
    return [sc[0][2]] + ([sc[1][2]] if len(sc) > 1 and sc[1][0] >= 3 and sc[1][0] >= 0.7 * sc[0][0] else [])

def short_ids(order):
    ids = {}
    for n in order:
        p = re.match(r"\d{4}-\d{2}-\d{2}-\d{3}(?:\d{3})?", n).group(0)
        while sum(o.startswith(p) for o in order) > 1 and len(p) < len(n):
            p = n[:len(p) + 1]
        ids[n] = p
    return ids

def build(entries, order, topics, sup, conflicts, per):
    groups, ids = {n: [] for n, _ in topics} | {"未分类": []}, short_ids(order)
    for name in order:
        for t in topics_of(topics, entries[name][1], entries[name][0]):
            groups[t].append(name)
    registered = {n for olds in sup.values() for n, _ in olds}
    suspects = [n for n in order if n not in registered and n not in sup and any(w in entries[n][0] + entries[n][1] for w in REVERSAL)]
    live = [c for c in conflicts if not (reaches(c[0], c[1], sup) or reaches(c[1], c[0], sup))]
    out = [f"# 现行决定页（自动生成，勿手改）\n\n共 {len(order)} 条决定，现行 {len(order) - len(sup)} 条，被取代 {len(sup)} 条（不列）。标识是 intent/ 下文件名的前缀（`ls intent/<标识>*`）；摘句不作依据，引用前打开原文；同一条可能出现在两个主题下。",
           f"每个主题列最近 {per} 条现行条目，其余只记条数；分类靠 `decision-digest/topics.txt` 的关键词，取代与矛盾登记在片段「## 取代」段与 `decision-digest/relations.txt`。疑似取代但未登记 {len(suspects)} 条（只告警）。\n",
           "## 待用户裁定（互相矛盾且未被取代）\n"] + ([f"- `{ids[a]}` ⟷ `{ids[b]}`：{cell(why, 80)}" for a, b, why in live] or ["- 无登记的矛盾"])
    for topic in [t for t, _ in topics] + ["未分类"]:
        cur = [n for n in groups[topic] if n not in sup]
        out.append(f"\n## {topic}（现行 {len(cur)}，共 {len(groups[topic])}）\n")
        out += [f"- `{ids[n]}` {cell(entries[n][1], CUT)}" for n in reversed(cur[-per:])] if per else []
        out += [f"- 另 {len(cur) - per} 条见 intent/"] if len(cur) > per else []
    return "\n".join(out) + "\n", len(groups["未分类"]), suspects

def run(folder, topics_path, relations_path, out_path, limit=LIMIT):
    entries, order = load_entries(folder)
    topics = load_topics(topics_path)
    sup, conflicts = load_relations(relations_path, entries, order)
    for per in range(PER_MAX, -1, -1):
        text, unclassified, suspects = build(entries, order, topics, sup, conflicts, per)
        if len(text.encode()) <= limit:
            break
    else:
        raise ValueError(f"现行决定页不列任何条目也有 {len(text.encode())} 字节，超过 {limit}")
    if out_path:
        open(out_path, "w", encoding="utf-8").write(text)
    return len(entries), unclassified, suspects, text

def self_test():
    def raises(fn):
        try:
            fn()
        except ValueError:
            return True
        return False
    def put(path, text):
        open(path, "w", encoding="utf-8").write(text)
    with tempfile.TemporaryDirectory() as d:
        intent, topics, rel = os.path.join(d, "intent"), os.path.join(d, "topics.txt"), os.path.join(d, "relations.txt")
        os.mkdir(intent)
        frag = lambda name, quote, point, sup="": put(os.path.join(intent, name), f"## 原话\n\n{quote}\n\n## 落点\n\n{point}\n" + (f"\n## 取代\n\n{sup}\n" if sup else ""))
        old, new, a, b = "2026-10-01-000001-a-旧.md", "2026-10-02-000001-a-新.md", "2026-10-03-000001-a-甲.md", "2026-10-03-000002-a-乙.md"
        [frag(*args) for args in [(old, "报销按三级审批", "报销审批三级|带竖线，详见 ADR-0007"), (new, "报销改两级，不再三级", "报销审批两级，仅金额超阈值时加一级" + "很长的尾巴" * 40), (a, "借款可冲抵", "借款冲抵报销"),
                     (b, "借款不冲抵", "借款与报销各自结算"), ("2026-10-04-000001-a-杂.md", "天气很好", "无关内容"), ("2026-10-04-000002-a-词界.md", "x", "PRINCIPLES 与 ADR-0001 ADR-0002 ADR-0003"),
                     ("2026-10-04-001-旧式序号.md", "旧式", "旧式序号文件名也认"), ("2026-10-04-000003-a-疑.md", "改选为只预警", "预算超额只预警")]]
        put(os.path.join(intent, "_header.md"), "忽略")
        put(topics, "审批\t审批\n财务\t借款|报销\n工作\tPR|ADR\n")
        put(rel, f"取代\t{new}\t{old}\t改两级\n矛盾\t{a}\t{b}\t冲抵之争\n")
        n, unclassified, suspects, text = run(intent, topics, rel, None)
        assert n == 8 and unclassified == 4 and len(suspects) == 1, (n, unclassified, suspects)   # 疑：含推翻字样但未登记，只告警
        assert "现行 7 条，被取代 1 条" in text and "报销审批两级" in text and "报销审批三级" not in text and "很长的尾巴" * 20 not in text, "被取代的不列；摘句截断"
        assert "冲抵之争" in text and "`2026-10-04-001`" in text, "矛盾列出；旧式序号的标识"
        put(rel, f"取代\t{b}\t{a}\t裁定\n")
        assert "冲抵之争" not in run(intent, topics, rel, None)[3], "矛盾一方被取代后不再列"
        [frag(f"2026-11-01-{i:06d}-a-量{i}.md", "审批", f"审批第{i}号决定，内容较长较长较长较长较长较长较长较长较长较长") for i in range(300)]
        text = run(intent, topics, rel, None)[3]
        assert len(text.encode()) <= LIMIT and "另 " in text, "条目多时按体量减少列出条数，整页不超限"
        assert raises(lambda: run(intent, topics, rel, None, limit=300)), "连一条都列不下的上限应失败"
        [os.remove(os.path.join(intent, f)) for f in os.listdir(intent) if "量" in f]
        bad = [(rel, f"取代\tno.md\t{old}\tx\n"), (rel, f"矛盾\t{old}\t{old}\tx\n"), (rel, "乱写\n"), (rel, f"取代\t{old}\t{new}\tx\n"), (rel, f"取代\t{new}\t{old}\tx\n取代\t{old}\t{new}\tx\n"),
               (topics, "审批\t审批\n审批\t审批\n"), (topics, "未分类\tx\n"), (topics, "审批\t审批| 会签\n"), (topics, "# 只有注释\n"),
               (os.path.join(intent, "2026-10-07-000001-a-空.md"), "## 原话\n\n\n\n## 落点\n\n\n"), (os.path.join(intent, "2026-10-07-000002-a-坏.md"), "坏格式"), (os.path.join(intent, "乱名.md"), "## 原话\n\nx\n\n## 落点\n\ny\n")]
        for path, body in bad:
            good = read(path) if os.path.exists(path) else None
            put(path, body)
            assert raises(lambda: run(intent, topics, rel, None)), f"应拒绝：{body!r}"
            put(path, good) if good is not None else os.remove(path)
    entries, order = load_entries(os.path.join(BASE, "intent"))
    topics = load_topics(os.path.join(BASE, "decision-digest", "topics.txt"))
    for prefix, topic in [l.split("\t") for l in read(os.path.join(BASE, "decision-digest", "expected.txt")).splitlines() if l.strip() and not l.startswith("#")]:
        hits = [n for n in order if n.startswith(prefix)]
        assert len(hits) == 1, f"expected.txt：{prefix} 命中 {len(hits)} 个片段"
        assert topic in topics_of(topics, entries[hits[0]][1], entries[hits[0]][0]), f"expected.txt：{prefix} 应含主题「{topic}」"
    print("ok")

if __name__ == "__main__":
    if "--self-test" in sys.argv:
        self_test()
    else:
        n, unclassified, suspects, text = run(os.path.join(BASE, "intent"), os.path.join(BASE, "decision-digest/topics.txt"), os.path.join(BASE, "decision-digest/relations.txt"),
                                              None if "--check" in sys.argv else os.path.join(BASE, OUT))
        print(f"{OUT}: {n} 条，{len(text.encode())} 字节（上限 {LIMIT}），未分类 {unclassified}，疑似取代未登记 {len(suspects)}")
