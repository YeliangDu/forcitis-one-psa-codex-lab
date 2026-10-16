#!/usr/bin/env python3
"""从 intent/ 片段自动汇总「已定决策速查表」DECISION_DIGEST.md（用户 2026-10-16 批准，one-coordinator 转达）。

设计组出题前对照本表并在题中引用条目（条目标识即 intent 文件名）。产物不入仓（.gitignore），由 build_all.py 生成。
- 主题：decision-digest/topics.txt 的关键词得分最高；没命中的归「未分类」并计数，不静默丢弃
- 每条一行：决定要点（落点摘句）、原话摘句、intent 文件名、被哪条取代
- 取代与矛盾：decision-digest/relations.txt 登记；矛盾且都未被取代的单列「待用户裁定」
用法：python3 docs/modernization/build_decision_digest.py [--self-test]
"""
import os, re, sys, tempfile

BASE = os.path.dirname(os.path.abspath(__file__))
POINT_LEN, QUOTE_LEN = 110, 50


def read(path):
    return open(path, encoding="utf-8").read()


def cell(text):
    return re.sub(r"\s+", " ", text).strip().replace("|", "／")


def clip(text, n):
    text = cell(text)
    return text if len(text) <= n else text[:n].rstrip("，、；。 ") + "…"


def load_topics(path):
    topics = []
    for n, line in enumerate(read(path).splitlines(), 1):
        if not line.strip() or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) != 2 or not parts[0].strip() or not parts[1].strip():
            raise ValueError(f"{path}:{n}：须为「主题<TAB>关键词|关键词」")
        topics.append((parts[0].strip(), [k for k in parts[1].split("|") if k]))
    if not topics:
        raise ValueError(f"{path}：没有主题")
    return topics


def load_entries(folder):
    entries = {}
    for name in sorted(os.listdir(folder)):
        if not name.endswith(".md") or name.startswith("_"):
            continue
        m = re.fullmatch(r"## 原话\n\n(.*)\n\n## 落点\n\n(.*)\n", read(os.path.join(folder, name)), re.S)
        if not m:
            raise ValueError(f"{name}：格式须为「## 原话」空行 原话 空行「## 落点」空行 落点")
        entries[name] = (m.group(1), m.group(2))
    return entries


def load_relations(path, entries):
    superseded, conflicts = {}, []
    for n, line in enumerate(read(path).splitlines(), 1):
        if not line.strip() or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) != 4 or parts[0] not in ("取代", "矛盾"):
            raise ValueError(f"{path}:{n}：须为「取代|矛盾<TAB>文件<TAB>文件<TAB>说明」")
        for f in parts[1:3]:
            if f not in entries:
                raise ValueError(f"{path}:{n}：{f} 不在 intent/ 下")
        if parts[1] == parts[2]:
            raise ValueError(f"{path}:{n}：两个文件相同")
        if parts[0] == "取代":
            superseded.setdefault(parts[2], []).append((parts[1], parts[3]))
        else:
            conflicts.append((parts[1], parts[2], parts[3]))
    return superseded, conflicts


def topic_of(topics, point, quote):
    best, best_score = "未分类", 0
    for name, keys in topics:
        score = sum(2 * point.count(k) + quote.count(k) for k in keys)
        if score > best_score:
            best, best_score = name, score
    return best


def build(entries, topics, superseded, conflicts):
    groups = {name: [] for name, _ in topics}
    groups["未分类"] = []
    for name, (quote, point) in entries.items():
        groups[topic_of(topics, point, quote)].append(name)
    out = ["# 已定决策速查表（自动生成，勿手改）\n",
           "每条一行；条目标识是 intent 文件名，出题时引用它。被取代的条目仍列出并标明。分类靠关键词（`decision-digest/topics.txt`），取代与矛盾登记在 `decision-digest/relations.txt`。\n"]
    live = [c for c in conflicts if c[0] not in superseded and c[1] not in superseded]
    out.append("## 待用户裁定（互相矛盾且都未被取代）\n")
    out.append("".join(f"- `{a}` ⟷ `{b}`：{cell(why)}\n" for a, b, why in live) if live else "- 无\n")
    for topic in [t for t, _ in topics] + ["未分类"]:
        names = groups[topic]
        out.append(f"\n## {topic}（{len(names)} 条）\n")
        if not names:
            continue
        out.append("| 决定要点 | 原话摘句 | intent | 被取代 |\n|---|---|---|---|\n")
        for name in names:
            quote, point = entries[name]
            by = "；".join(f"被 `{n}` 取代（{cell(w)}）" for n, w in superseded.get(name, [])) or "—"
            out.append(f"| {clip(point, POINT_LEN)} | {clip(quote, QUOTE_LEN)} | `{name}` | {by} |\n")
    return "".join(out), len(groups["未分类"])


def run(folder, topics_path, relations_path, out_path):
    entries = load_entries(folder)
    topics = load_topics(topics_path)
    superseded, conflicts = load_relations(relations_path, entries)
    text, unclassified = build(entries, topics, superseded, conflicts)
    if out_path:
        open(out_path, "w", encoding="utf-8").write(text)
    return len(entries), unclassified, text


def self_test():
    with tempfile.TemporaryDirectory() as d:
        intent = os.path.join(d, "intent"); os.mkdir(intent)
        def frag(name, quote, point):
            open(os.path.join(intent, name), "w", encoding="utf-8").write(f"## 原话\n\n{quote}\n\n## 落点\n\n{point}\n")
        frag("2026-10-01-000001-a-旧.md", "报销按三级审批", "报销审批三级|带竖线")
        frag("2026-10-02-000001-a-新.md", "报销改两级", "报销审批两级")
        frag("2026-10-03-000001-a-甲.md", "借款可冲抵", "借款冲抵报销")
        frag("2026-10-03-000002-a-乙.md", "借款不冲抵", "借款与报销各自结算")
        frag("2026-10-04-000001-a-杂.md", "天气很好", "无关内容")
        open(os.path.join(intent, "_header.md"), "w").write("忽略")
        topics = os.path.join(d, "topics.txt"); open(topics, "w", encoding="utf-8").write("审批\t审批\n财务\t借款\n")
        rel = os.path.join(d, "relations.txt")
        open(rel, "w", encoding="utf-8").write("取代\t2026-10-02-000001-a-新.md\t2026-10-01-000001-a-旧.md\t改两级\n矛盾\t2026-10-03-000001-a-甲.md\t2026-10-03-000002-a-乙.md\t冲抵之争\n")
        n, unclassified, text = run(intent, topics, rel, None)
        assert n == 5 and unclassified == 1, (n, unclassified)
        assert "被 `2026-10-02-000001-a-新.md` 取代（改两级）" in text
        assert "## 待用户裁定" in text and "甲.md` ⟷ `" in text and "冲抵之争" in text
        assert all(l.count("|") == 5 for l in text.splitlines() if l.startswith("| ") and "intent" not in l[:10]), "竖线未转义"
        assert "三级／带竖线" in text
        assert text.count("| 报销审批三级") == 1
        open(rel, "a", encoding="utf-8").write("取代\t2026-10-03-000002-a-乙.md\t2026-10-03-000001-a-甲.md\t裁定\n")
        assert "- 无" in run(intent, topics, rel, None)[2].split("## 财务")[0]
        for bad in ("取代\tno.md\t2026-10-01-000001-a-旧.md\tx\n", "矛盾\t2026-10-01-000001-a-旧.md\t2026-10-01-000001-a-旧.md\tx\n", "乱写\n"):
            open(rel, "w", encoding="utf-8").write(bad)
            try:
                run(intent, topics, rel, None); raise SystemExit("应拒绝：" + bad)
            except ValueError:
                pass
        frag("2026-10-05-000001-a-坏.md", "x", "y"); open(os.path.join(intent, "2026-10-05-000001-a-坏.md"), "w").write("坏格式")
        open(rel, "w").write("")
        try:
            run(intent, topics, rel, None); raise SystemExit("应拒绝坏片段")
        except ValueError:
            pass
    print("ok")


if __name__ == "__main__":
    if "--self-test" in sys.argv:
        self_test()
    else:
        n, unclassified, _ = run(os.path.join(BASE, "intent"), os.path.join(BASE, "decision-digest/topics.txt"),
                                os.path.join(BASE, "decision-digest/relations.txt"), os.path.join(BASE, "DECISION_DIGEST.md"))
        print(f"DECISION_DIGEST.md: {n} 条，未分类 {unclassified} 条")
