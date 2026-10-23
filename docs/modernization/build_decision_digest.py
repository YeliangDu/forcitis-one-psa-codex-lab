#!/usr/bin/env python3
"""从 intent/ 片段自动汇总「已定决策速查表」DECISION_DIGEST.md（用户 2026-10-16 批准，one-coordinator 转达）。

设计组出题前对照本表并在题中引用条目（条目标识即 intent 文件名，SESSION_PLAYBOOK TEAM-62）。产物不入仓（.gitignore），由 build_all.py 生成。
摘句不作依据：引用前打开 intent 原文（SESSION_PLAYBOOK「转述前先核对原文」）。
- 主题：decision-digest/topics.txt 的关键词记分（先剔除编号引用；ASCII 词按词边界；次高分且不低于 70% 也列出），都为 0 分归「未分类」
- 取代：片段里的「## 取代」段（new_intent.py --supersedes=）与 decision-digest/relations.txt 的「取代」行；矛盾登记在 relations.txt
  循环取代、新条目早于旧条目、引用不存在的文件都失败；取代链显示最终有效条目
- 「疑似取代未登记」：文字含推翻字样但没有登记取代关系的条目，只告警不失败，由发现的会话补登记
- 生成时会校验：片段格式、原话与落点非空、topics.txt 无重复主题、关键词无空白
用法：python3 docs/modernization/build_decision_digest.py [--self-test]
"""
import os, re, sys, tempfile

BASE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, BASE)
import build_records

QUOTE_CAP = 220
SECOND_RATIO, SECOND_MIN = 0.7, 3
REVERSAL = ("改选", "看错了", "先等等", "不作为最终", "推翻", "已被", "作废")
REF = re.compile(r"[A-Za-z]+-[\dA-Za-z-]*\d[\w-]*|PR\s*#\d+|§[\d.]+|[\w./-]+\.(?:md|py|yaml|json|html|txt|yml)")
SECTION = re.compile(r"## 原话\n\n(.*?)\n\n## 落点\n\n(.*?)(?:\n\n## 取代\n\n(.*?))?\n(?:\n## (?:规则|影响的 ADR|单子|题号)\n\n[^\n]+\n)*", re.S)


def read(path):
    return open(path, encoding="utf-8").read()


def cell(text):
    return re.sub(r"\s+", " ", text).strip().replace("\\|", "／").replace("|", "／")


def quote_cell(text):
    t = cell(text)
    return t if len(t) <= QUOTE_CAP else t[:QUOTE_CAP // 2].rstrip() + " …… " + t[-QUOTE_CAP // 2:].lstrip()


def load_topics(path):
    topics, seen = [], set()
    for n, line in enumerate(read(path).splitlines(), 1):
        if not line.strip() or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) != 2 or not parts[0].strip() or not parts[1].strip():
            raise ValueError(f"{path}:{n}：须为「主题<TAB>关键词|关键词」")
        name, keys = parts[0].strip(), parts[1].split("|")
        if name in seen or name == "未分类":
            raise ValueError(f"{path}:{n}：主题名「{name}」重复或与保留的「未分类」冲突")
        if any(not k or k != k.strip() for k in keys):
            raise ValueError(f"{path}:{n}：关键词不能为空或带首尾空白")
        seen.add(name)
        topics.append((name, keys))
    if not topics:
        raise ValueError(f"{path}：没有主题")
    return topics


def load_entries(folder):
    entries, order = {}, []
    for path in build_records.dated_fragments(folder):
        name = os.path.basename(path)
        m = SECTION.fullmatch(read(path))
        if not m or not m.group(1).strip() or not m.group(2).strip():
            raise ValueError(f"{name}：格式须为「## 原话」「## 落点」[「## 取代」]，原话与落点非空")
        sup = [s.strip().lstrip("-").strip().strip("`") for s in (m.group(3) or "").splitlines() if s.strip()]
        entries[name] = (m.group(1), m.group(2), sup)
        order.append(name)
    return entries, order


def load_relations(path, entries, order):
    pos = {n: i for i, n in enumerate(order)}
    sup, conflicts = {}, []

    def add_sup(new, old, why, where):
        for f in (new, old):
            if f not in entries:
                raise ValueError(f"{where}：{f} 不在 intent/ 下")
        if new == old or pos[new] < pos[old]:
            raise ValueError(f"{where}：取代方须是更晚的条目（{new} 不晚于 {old}）")
        if (new, why) not in sup.setdefault(old, []) and new not in [n for n, _ in sup[old]]:
            sup[old].append((new, why))

    for name, (_, _, refs) in entries.items():
        for old in refs:
            add_sup(name, old, "片段「## 取代」", name)
    for n, line in enumerate(read(path).splitlines(), 1):
        if not line.strip() or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) != 4 or parts[0] not in ("取代", "矛盾"):
            raise ValueError(f"{path}:{n}：须为「取代|矛盾<TAB>文件<TAB>文件<TAB>说明」")
        if parts[0] == "取代":
            add_sup(parts[1], parts[2], parts[3], f"{path}:{n}")
        else:
            for f in parts[1:3]:
                if f not in entries:
                    raise ValueError(f"{path}:{n}：{f} 不在 intent/ 下")
            if parts[1] == parts[2]:
                raise ValueError(f"{path}:{n}：两个文件相同")
            conflicts.append((parts[1], parts[2], parts[3]))
    return sup, conflicts


def final_of(name, sup):
    seen = []
    while name in sup:
        if name in seen:
            raise ValueError(f"取代关系成环：{' → '.join(seen + [name])}")
        seen.append(name)
        name = sup[name][0][0]
    return name


def reaches(a, b, sup):
    while a in sup:
        a = sup[a][0][0]
        if a == b:
            return True
    return False


def scores(topics, point, quote):
    point, quote = REF.sub(" ", point), REF.sub(" ", quote)

    def count(text, k):
        if k.isascii():
            return len(re.findall(rf"(?<![A-Za-z0-9]){re.escape(k)}(?![A-Za-z0-9])", text, re.I))
        return text.count(k)
    return {name: sum(2 * count(point, k) + count(quote, k) for k in keys) for name, keys in topics}


def topics_of(topics, point, quote):
    sc = sorted(((v, -i, n) for i, (n, v) in enumerate((n, s) for n, s in scores(topics, point, quote).items())), reverse=True)
    if not sc or sc[0][0] == 0:
        return ["未分类"]
    out = [sc[0][2]]
    if len(sc) > 1 and sc[1][0] >= SECOND_MIN and sc[1][0] >= SECOND_RATIO * sc[0][0]:
        out.append(sc[1][2])
    return out


def build(entries, order, topics, sup, conflicts):
    groups = {n: [] for n, _ in topics}
    groups["未分类"] = []
    for name in order:
        quote, point, _ = entries[name]
        for t in topics_of(topics, point, quote):
            groups[t].append(name)
    registered = {n for olds in sup.values() for n, _ in olds}
    suspects = [n for n in order if n not in registered and n not in sup and any(w in entries[n][0] + entries[n][1] for w in REVERSAL)]
    live = [c for c in conflicts if not (reaches(c[0], c[1], sup) or reaches(c[1], c[0], sup))]
    out = ["# 已定决策速查表（自动生成，勿手改）\n\n",
           "每条一行；条目标识是 intent 文件名，出题时引用它。**摘句不作依据，引用前打开原文。** 被取代的条目仍列出（删除线）。同一条可能出现在两个主题下。\n\n",
           f"取代关系已登记 {sum(len(v) for v in sup.values())} 条；疑似取代但未登记 {len(suspects)} 条（见文末）。「—」表示没有登记过取代关系，不表示核对过没有。分类靠关键词（`decision-digest/topics.txt`），取代与矛盾登记在片段「## 取代」段与 `decision-digest/relations.txt`。\n\n"]
    out.append("## 待用户裁定（互相矛盾且未被取代）\n\n")
    out.append("".join(f"- `{a}` ⟷ `{b}`：{cell(why)}\n" for a, b, why in live) if live else "- 无登记的矛盾\n")
    for topic in [t for t, _ in topics] + ["未分类"]:
        names = groups[topic]
        out.append(f"\n## {topic}（{len(names)} 条）\n\n")
        if not names:
            continue
        out.append("| 落点摘句 | 原话摘句 | intent | 取代关系 |\n|---|---|---|---|\n")
        for name in names:
            quote, point, olds = entries[name]
            notes = []
            if name in sup:
                notes.append("被 " + "；".join(f"`{n}`（{cell(w)}）" for n, w in sup[name]) + f" 取代，最终有效 `{final_of(name, sup)}`")
            if olds or any(n == name for n, _ in registered_pairs(sup)):
                notes.append("取代 " + "、".join(f"`{o}`" for o in sorted({o for o, v in sup.items() for n, _ in v if n == name})))
            text = cell(point)
            out.append(f"| {'~~' + text + '~~' if name in sup else text} | {quote_cell(quote)} | `{name}` | {'；'.join(notes) or '—'} |\n")
    out.append(f"\n## 疑似取代未登记（{len(suspects)} 条，只告警）\n\n")
    out.append("".join(f"- `{n}`\n" for n in suspects) if suspects else "- 无\n")
    return "".join(out), len(groups["未分类"]), len(suspects)


def registered_pairs(sup):
    return [(n, o) for o, v in sup.items() for n, _ in v]


def run(folder, topics_path, relations_path, out_path):
    entries, order = load_entries(folder)
    topics = load_topics(topics_path)
    sup, conflicts = load_relations(relations_path, entries, order)
    for name in sup:
        final_of(name, sup)
    text, unclassified, suspects = build(entries, order, topics, sup, conflicts)
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
    with tempfile.TemporaryDirectory() as d:
        intent = os.path.join(d, "intent"); os.mkdir(intent)

        def frag(name, quote, point, sup=None):
            body = f"## 原话\n\n{quote}\n\n## 落点\n\n{point}\n" + (f"\n## 取代\n\n{sup}\n" if sup else "")
            open(os.path.join(intent, name), "w", encoding="utf-8").write(body)
        old, new = "2026-10-01-000001-a-旧.md", "2026-10-02-000001-a-新.md"
        a, b = "2026-10-03-000001-a-甲.md", "2026-10-03-000002-a-乙.md"
        frag(old, "报销按三级审批", "报销审批三级|带竖线，详见 ADR-0007 与 PR #12")
        frag(new, "报销改两级，不再三级", "报销审批两级，仅金额超阈值时加一级，" + "很长的尾巴" * 40)
        frag(a, "借款可冲抵", "借款冲抵报销")
        frag(b, "借款不冲抵", "借款与报销各自结算")
        frag("2026-10-04-000001-a-杂.md", "天气很好", "无关内容")
        frag("2026-10-04-000002-a-词界.md", "x", "PRINCIPLES 与 ADR-0001 ADR-0002 ADR-0003")
        open(os.path.join(intent, "_header.md"), "w", encoding="utf-8").write("忽略")
        topics = os.path.join(d, "topics.txt")
        open(topics, "w", encoding="utf-8").write("审批\t审批\n财务\t借款|报销\n工作\tPR|ADR\n")
        rel = os.path.join(d, "relations.txt")
        open(rel, "w", encoding="utf-8").write(f"取代\t{new}\t{old}\t改两级\n矛盾\t{a}\t{b}\t冲抵之争\n")
        n, unclassified, suspects, text = run(intent, topics, rel, None)
        assert n == 6 and unclassified == 2, (n, unclassified)        # 杂；词界（编号引用被剔除、PRINCIPLES 不命中 PR）
        assert f"被 `{new}`（改两级） 取代，最终有效 `{new}`" in text
        assert "~~报销审批三级／带竖线" in text and "ADR-0007" in text, "删除线、竖线转义、编号引用保留在摘句里"
        assert "很长的尾巴" * 40 in text, "落点不截断"
        assert "## 待用户裁定" in text and "冲抵之争" in text
        assert all(l.count("|") == 5 for l in text.splitlines() if l.startswith("| ") and not l.startswith("| 落点摘句")), "竖线未转义"
        assert suspects == 0, suspects
        frag("2026-10-04-000003-a-疑.md", "改选为只预警", "预算超额只预警")
        assert run(intent, topics, rel, None)[2] == 1, "含推翻字样但未登记应告警"
        os.remove(os.path.join(intent, "2026-10-04-000003-a-疑.md"))
        # 两个主题：次高分列出
        frag("2026-10-05-000001-a-双.md", "x", "借款审批 借款审批 借款审批")
        assert run(intent, topics, rel, None)[3].count("2026-10-05-000001-a-双.md") == 2
        os.remove(os.path.join(intent, "2026-10-05-000001-a-双.md"))
        # 矛盾一方被另一方取代后消失
        open(rel, "a", encoding="utf-8").write(f"取代\t{b}\t{a}\t裁定\n")
        assert "冲抵之争" not in run(intent, topics, rel, None)[3]
        # 取代链显示最终有效
        c = "2026-10-06-000001-a-丙.md"; frag(c, "再改", "报销审批一级", sup=f"- {new}")
        t = run(intent, topics, rel, None)[3]
        assert f"最终有效 `{c}`" in t and f"取代 `{new}`" in t
        # 失败路径
        bad = {
            "引用不存在": f"取代\tno.md\t{old}\tx\n",
            "两文件相同": f"矛盾\t{old}\t{old}\tx\n",
            "格式乱": "乱写\n",
            "新早于旧": f"取代\t{old}\t{new}\tx\n",
            "成环": f"取代\t{new}\t{old}\tx\n取代\t{old}\t{new}\tx\n",
        }
        for why, body in bad.items():
            open(rel, "w", encoding="utf-8").write(body)
            assert raises(lambda: run(intent, topics, rel, None)), f"应拒绝：{why}"
        open(rel, "w").write("")
        for why, body in {"重复主题": "审批\t审批\n审批\t审批\n", "保留名": "未分类\tx\n", "空白关键词": "审批\t审批| 会签\n", "无主题": "# 只有注释\n"}.items():
            open(topics, "w", encoding="utf-8").write(body)
            assert raises(lambda: run(intent, topics, rel, None)), f"应拒绝：{why}"
        open(topics, "w", encoding="utf-8").write("审批\t审批\n")
        open(os.path.join(intent, "2026-10-07-000001-a-空.md"), "w", encoding="utf-8").write("## 原话\n\n\n\n## 落点\n\n\n")
        assert raises(lambda: run(intent, topics, rel, None)), "应拒绝空片段"
        os.remove(os.path.join(intent, "2026-10-07-000001-a-空.md"))
        open(os.path.join(intent, "2026-10-07-000002-a-坏.md"), "w", encoding="utf-8").write("坏格式")
        assert raises(lambda: run(intent, topics, rel, None)), "应拒绝坏片段"
    expected = os.path.join(BASE, "decision-digest", "expected.txt")
    if os.path.isdir(os.path.join(BASE, "intent")) and os.path.exists(expected):
        entries, order = load_entries(os.path.join(BASE, "intent"))
        topics = load_topics(os.path.join(BASE, "decision-digest", "topics.txt"))
        for line in read(expected).splitlines():
            if not line.strip() or line.startswith("#"):
                continue
            prefix, topic = line.split("\t")
            hits = [n for n in order if n.startswith(prefix)]
            assert len(hits) == 1, f"expected.txt：{prefix} 命中 {len(hits)} 个片段"
            got = topics_of(topics, entries[hits[0]][1], entries[hits[0]][0])
            assert topic in got, f"expected.txt：{prefix} 应含主题「{topic}」，实际 {got}"
    print("ok")


if __name__ == "__main__":
    if "--self-test" in sys.argv:
        self_test()
    else:
        n, unclassified, suspects, _ = run(os.path.join(BASE, "intent"), os.path.join(BASE, "decision-digest/topics.txt"),
                                          os.path.join(BASE, "decision-digest/relations.txt"), os.path.join(BASE, "DECISION_DIGEST.md"))
        print(f"DECISION_DIGEST.md: {n} 条，未分类 {unclassified}，疑似取代未登记 {suspects}")
