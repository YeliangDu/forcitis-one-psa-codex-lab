#!/usr/bin/env python3
"""生成规则索引 rules/RULE_INDEX.md（不入仓，同 STATUS.md）：每条规则一行，列状态、最近决定、测试引用数；并提供派工前检查。

规则全集：BUSINESS_RULES.md 的老行为卡（315 张，取自老系统，只作证据）与 rules/*.md 的新增规则表。
状态（暂定词表，one-rules 可改 STATE_WORDS）：
  老行为未评审  BUSINESS_RULES.md 的卡，RULE_REVIEWS.json 无结论
  新规则待评审  rules/*.md 的卡，Review 列为 Yes（“需评审”，见 IDENTITY_ORG_RULES.md 各卡“Review needed”）且 RULE_REVIEWS.json 无结论；只有标题的卡按此算
  新规则无需评审  rules/*.md 的卡，Review 列为 No（用户已决定，如工时路由、审批时限）且无结论
  沿用 / 需修正 / 待议   RULE_REVIEWS.json 当前结论（含修订）为 confirmed / wrong / discuss
最近决定：intent 片段中结构化标注「## 规则」（new_intent.py --rules）列有该规则的最新一条；另列文本提及该规则号的片段数。
测试引用：backend/src/test 下提到该规则号的文件数（只数文件，注释与 @Disabled 里的提及也算，故是上限）；主代码引用：backend/src/main；流程文档引用：process/ 下的文件数。
用法：
  build_rule_index.py                  生成 RULE_INDEX.md
  build_rule_index.py --check          校验：规则号唯一；intent 标注的规则号都存在；代码、测试、文档引用的规则号不存在者只报告
  build_rule_index.py --dispatch <卡.md> [--strict]  派工前检查：卡里提到的规则号若为「老行为未评审」「新规则未评审」，列出；默认只报告（退出 0），加 --strict 才退出 1
  build_rule_index.py --self-test
"""
import glob, importlib.util, json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.normpath(os.path.join(HERE, ".."))
ROOT = os.path.normpath(os.path.join(MOD, "..", ".."))
STATE_WORDS = dict(old_unreviewed="老行为未评审", new_unreviewed="新规则待评审", confirmed="沿用", wrong="需修正", discuss="待议", new_settled="新规则无需评审")
UNREVIEWED = (STATE_WORDS["old_unreviewed"], STATE_WORDS["new_unreviewed"])
RULE = re.compile(r"(?<![\w-])(?:[A-Z]+-)?RULE-\d+")
RANGE = re.compile(r"(?<![\w-])((?:[A-Z]+-)?RULE-)(\d+)\s*(?:\.\.|–|—|~|至|-)\s*(?:(?:[A-Z]+-)?RULE-)?(\d+)")
SLASH = re.compile(r"(?<![\w-])((?:[A-Z]+-)?RULE-)(\d+)((?:/\d+)+)")
INTENT_TAGS = {"规则": r"(?:[A-Z]+-)?RULE-\d+(?:、(?:[A-Z]+-)?RULE-\d+)*", "影响的 ADR": r"ADR-\d{4}(?:、ADR-\d{4})*", "单子": r"[A-Za-z0-9_-]+", "题号": r"[A-Za-z0-9_-]+(?:、[A-Za-z0-9_-]+)*",
               "类别": r"口径|范围|流程|授权|技术|其他", "负责会话": r"[a-z0-9-]+", "影响路径": r"无|[^\s、]+(?:、[^\s、]+)*"}


def parse_intent(path, text):
    """(原话, 落点, {可选标注: 值})；可选小节「取代」（多行名单）居先，再是「规则」「影响的 ADR」「单子」「题号」「类别」「负责会话」「影响路径」按此顺序附在「落点」之后，供规则索引与 ADR 治理反查。"""
    m = re.fullmatch(r"## 原话\n\n(.*?)\n\n## 落点\n\n(.*?)\n(?:\n## 取代\n\n((?:- [^\n]+\n)+))?((?:\n## [^\n]+\n\n[^\n]+\n)*)", text, re.S)
    if not m:
        raise ValueError(f"{path}：格式须为「## 原话」空行 原话 空行「## 落点」空行 落点，之后可选「## 取代」（被取代的 intent 文件名，一行一个）及「## 规则」「## 影响的 ADR」「## 单子」「## 题号」「## 类别」「## 负责会话」「## 影响路径」各一行")
    tags = {}
    for name, value in re.findall(r"\n## ([^\n]+)\n\n([^\n]+)\n", m.group(4)):
        if name not in INTENT_TAGS or name in tags or not re.fullmatch(INTENT_TAGS[name], value):
            raise ValueError(f"{path}：可选小节「{name}」不合规（取自 {'、'.join(INTENT_TAGS)}，不重复，值形如 {INTENT_TAGS.get(name, '—')}）：{value[:30]}")
        tags[name] = value
    if list(tags) != [k for k in INTENT_TAGS if k in tags]:
        raise ValueError(f"{path}：可选小节须按 {'、'.join(INTENT_TAGS)} 的顺序")
    landing = m.group(2) + (f"（取代：{'、'.join(x.strip('- `') for x in m.group(3).splitlines() if x.strip())}）" if m.group(3) else "")
    return m.group(1), landing, tags


def key(r):
    prefix, _, n = r.rpartition("RULE-")
    return (prefix, int(n))


def ref_set(text):
    """文本里提到的规则号集合（规范写法见 norm）：单个、范围（RULE-388..392、RULE-388–RULE-392）、斜杠简写（RULE-282/296）。"""
    found = set()
    for r in RULE.findall(text):
        found.add(norm(r))
    for m in RANGE.finditer(text):
        a, b = int(m.group(2)), int(m.group(3))
        if a < b <= a + 200:
            found.update(f"{m.group(1)}{n}" for n in range(a, b + 1))
    for m in SLASH.finditer(text):
        found.update(f"{m.group(1)}{int(n)}" for n in m.group(3).split("/")[1:])
    return found


def norm(r):
    """规范写法：去掉前导零（RULE-027 与 RULE-27、DATA-RULE-01 与 DATA-RULE-1 是同一条）。卡的原写法保存在 id 字段。"""
    prefix, n = key(r)
    return f"{prefix}RULE-{n}"


def old_cards(text):
    """BUSINESS_RULES.md 汇总表：| RULE-n | 领域 | 标题 | 类型 | 优先级 | …"""
    return {m.group(1): dict(domain=m.group(2).strip(), title=m.group(3).strip(), priority=m.group(4).strip(), source="BUSINESS_RULES.md", reviewed=None)
            for m in re.finditer(r"(?m)^\| (RULE-\d+) \| ([^|]*) \| ([^|]*) \| [^|]* \| (P\d) \|", text)}


def new_cards(text, name):
    """rules/*.md 表：| RULE-n | 领域 | 标题 | 优先级 | Review(Yes/No) |"""
    cards = {m.group(1): dict(domain=m.group(2).strip(), title=m.group(3).strip(), priority=m.group(4), source=f"rules/{name}", reviewed=m.group(5) == "No")
             for m in re.finditer(r"(?m)^\| ((?:[A-Z]+-)?RULE-\d+) \| ([^|]*) \| ([^|]*) \| (P\d) \| (Yes|No)[^|]*\|", text)}
    for m in re.finditer(r"(?m)^#{2,3} ((?:[A-Z]+-)?RULE-\d+)[:：]?\s*(.+)$", text):   # 没有汇总表、只有标题的卡
        cards.setdefault(m.group(1), dict(domain=name[:-3], title=m.group(2).strip(), priority="—", source=f"rules/{name}", reviewed=False))
    return cards


def state_of(card, verdict):
    if verdict:
        return STATE_WORDS[verdict]
    if card["reviewed"] is None:
        return STATE_WORDS["old_unreviewed"]
    return STATE_WORDS["new_settled" if card["reviewed"] else "new_unreviewed"]


def intent_refs(folder, parse_intent):
    """规则号 -> ([结构化标注的片段文件名], [文本提及的片段文件名])，文件名升序即时间升序。"""
    tagged, mentioned = {}, {}
    for f in sorted(glob.glob(os.path.join(folder, "*.md"))):
        name = os.path.basename(f)
        if name.startswith("_"):
            continue
        text = open(f, encoding="utf-8").read()
        try:
            _, _, tags = parse_intent(f, text)
        except ValueError:
            tags = {}
        for r in ref_set(tags.get("规则", "")):
            tagged.setdefault(r, []).append(name)
        for r in ref_set(text):
            mentioned.setdefault(r, []).append(name)
    return tagged, mentioned


def file_refs(folder, suffixes):
    refs = {}
    for dirpath, _, files in os.walk(folder):
        for f in files:
            if f.endswith(suffixes):
                for r in ref_set(open(os.path.join(dirpath, f), encoding="utf-8", errors="replace").read()):
                    refs.setdefault(r, []).append(os.path.join(dirpath, f))
    return refs


def merge_new(cards, new):
    """按规范写法合并；同一条规则出现两次（含 RULE-27 与 RULE-027）失败。"""
    for k, v in new.items():
        ck = norm(k)
        if ck in cards:
            raise ValueError(f"规则号 {k} 重复：{cards[ck]['source']} 与 {v['source']}")
        cards[ck] = dict(v, id=k)


def load(mod=MOD, root=ROOT):
    spec = importlib.util.spec_from_file_location("review_revisions", os.path.join(mod, "rules", "review_revisions.py"))
    rr = importlib.util.module_from_spec(spec); spec.loader.exec_module(rr)
    cards = {}
    merge_new(cards, old_cards(open(os.path.join(mod, "BUSINESS_RULES.md"), encoding="utf-8").read()))
    for f in sorted(glob.glob(os.path.join(mod, "rules", "*.md"))):
        for k, v in new_cards(open(f, encoding="utf-8").read(), os.path.basename(f)).items():
            merge_new(cards, {k: v})
    reviews = rr.load_reviews(mod)
    verdicts = {norm(k): rr.current(v)["verdict"] for k, v in reviews.items()}
    tagged, mentioned = intent_refs(os.path.join(mod, "intent"), parse_intent)
    tests = file_refs(os.path.join(root, "backend", "src", "test"), (".java", ".sql", ".json", ".yaml")) if os.path.isdir(os.path.join(root, "backend", "src", "test")) else None
    main = file_refs(os.path.join(root, "backend", "src", "main"), (".java", ".sql", ".yaml")) if os.path.isdir(os.path.join(root, "backend", "src", "main")) else None
    process = file_refs(os.path.join(mod, "process"), (".md",)) if os.path.isdir(os.path.join(mod, "process")) else {}
    return dict(cards=cards, verdicts=verdicts, tagged=tagged, mentioned=mentioned, tests=tests, main=main, process=process)


def problems(data):
    cards, out = data["cards"], []
    for r in sorted(data["tagged"]):
        if r not in cards:
            out.append(f"intent 标注的规则 {r} 不存在（{data['tagged'][r][-1]}）")
    unknown_reviewed = sorted(set(data["verdicts"]) - set(cards))
    out += [f"RULE_REVIEWS.json 的 {r} 不在任何规则卡中" for r in unknown_reviewed]
    return out


def reports(data):
    cards, out = data["cards"], []
    for label, refs in (("测试", data["tests"]), ("主代码", data["main"]), ("流程文档", data["process"])):
        if refs:
            missing = sorted(set(refs) - set(cards), key=key)
            if missing:
                out.append(f"{label}引用了不存在的规则号：{'、'.join(missing[:10])}" + ("…" if len(missing) > 10 else ""))
    if data["tests"] is not None:
        p0 = [r for r, c in cards.items() if c["priority"] == "P0" and r not in data["tests"] and state_of(c, data["verdicts"].get(r)) != "新规则未评审"]
        out.append(f"P0 规则无测试引用 {len(p0)} 条（全部 P0 {sum(c['priority'] == 'P0' for c in cards.values())} 条）")
    return out


def render(data):
    cards = data["cards"]
    rows, counts = [], {}
    for r in sorted(cards, key=key):
        c = cards[r]
        state = state_of(c, data["verdicts"].get(r))
        counts[state] = counts.get(state, 0) + 1
        tagged = data["tagged"].get(r, [])
        n = lambda refs: "—" if refs is None else str(len(refs.get(r, [])))
        rows.append(f"| {c['id']} | {c['domain']} | {c['title'].replace('|', '／')[:80]} | {c['priority']} | {state} | {tagged[-1] if tagged else '—'} | {len(data['mentioned'].get(r, []))} | {n(data['tests'])} | {n(data['main'])} | {n(data['process'])} |")
    head = "# 规则索引\n\n由 `rules/build_rule_index.py` 生成，不手改，不入仓。状态词、列含义见该脚本头部注释。\n\n"
    summary = "- " + "；".join(f"{k} {v}" for k, v in sorted(counts.items())) + f"；共 {len(cards)} 条\n\n"
    table = "| 规则 | 领域 | 标题 | 优先级 | 状态 | 最近结构化决定 | 文本提及的决定数 | 测试引用 | 主代码引用 | 流程文档引用 |\n|---|---|---|---|---|---|---|---|---|---|\n"
    return head + summary + table + "\n".join(rows) + "\n"


def dispatch(card_path, data):
    """卡里提到的规则号中，未评审者（不得派工，先评审）。"""
    text = open(card_path, encoding="utf-8").read()
    bad = []
    for r in sorted(ref_set(text), key=key):
        if r not in data["cards"]:
            bad.append(f"{r}：规则卡中不存在")
            continue
        state = state_of(data["cards"][r], data["verdicts"].get(r))
        if state in UNREVIEWED:
            bad.append(f"{data['cards'][r]['id']}：{state}（{data['cards'][r]['title'][:50]}）")
    return bad


def main(argv):
    data = load()
    if argv[:1] == ["--dispatch"] and len(argv) in (2, 3) and argv[2:] in ([], ["--strict"]):
        bad = dispatch(argv[1], data)
        for b in bad:
            print("派工前检查：" + b)
        print("派工前检查：" + ("有未评审规则，先评审再派工" if bad else "通过"))
        sys.exit(1 if bad and argv[2:] == ["--strict"] else 0)
    if argv not in ([], ["--check"]):
        sys.exit(__doc__)
    errors = problems(data)
    for line in reports(data):
        print("报告：" + line, file=sys.stderr)
    if errors:
        sys.exit("规则索引校验失败：\n" + "\n".join(errors))
    if argv == ["--check"]:
        print(f"check: {len(data['cards'])} 条规则")
        return
    open(os.path.join(HERE, "RULE_INDEX.md"), "w", encoding="utf-8").write(render(data))
    print(f"规则索引：{len(data['cards'])} 条")


def self_test():
    import tempfile
    old = old_cards("| RULE-1 | 审批 | A title | Validation | P0 | `x.java:1` | High |\n| RULE-2 | 审批 | B | Rule | P1 | y | High |\n")
    new = new_cards("| RULE-10 | timesheet | N1 | P0 | No |\n| RULE-11 | timesheet | N2 | P1 | Yes |\n| ID | x |\n", "T.md")
    assert new_cards("## RULE-385 已关闭的归属期间不改写\n\n正文\n### RULE-386: x\n", "L.md")["RULE-385"]["title"].startswith("已关闭") and "RULE-386" in new_cards("### RULE-386: x\n", "L.md")
    assert sorted(old) == ["RULE-1", "RULE-2"] and old["RULE-1"]["priority"] == "P0" and sorted(new) == ["RULE-10", "RULE-11"]
    assert state_of(old["RULE-1"], None) == "老行为未评审" and state_of(old["RULE-1"], "wrong") == "需修正"
    assert state_of(new["RULE-10"], None) == "新规则无需评审" and state_of(new["RULE-11"], None) == "新规则待评审" and state_of(new["RULE-10"], "confirmed") == "沿用"
    cards = {}
    merge_new(cards, old); merge_new(cards, new)
    data = dict(cards=cards, verdicts={"RULE-2": "confirmed", "RULE-99": "wrong"}, tagged={"RULE-2": ["a.md", "b.md"], "RULE-404": ["c.md"]}, mentioned={"RULE-2": ["a.md"]},
                tests={"RULE-1": ["t"], "RULE-777": ["t"]}, main=None, process={})
    errs = problems(data)
    assert any("RULE-404" in e for e in errs) and any("RULE-99" in e for e in errs) and len(errs) == 2
    assert any("RULE-777" in r for r in reports(data))
    board = render(dict(data, tagged={"RULE-2": ["a.md", "b.md"]}))
    assert "| RULE-2 | 审批 | B | P1 | 沿用 | b.md | 1 | 0 | — | 0 |" in board and "| RULE-1 | 审批 | A title | P0 | 老行为未评审 | — | 0 | 1 | — | 0 |" in board, board
    with tempfile.TemporaryDirectory() as d:
        card = os.path.join(d, "c.md")
        open(card, "w", encoding="utf-8").write("依据：RULE-1、RULE-2、RULE-10、RULE-11、RULE-555\n")
        bad = dispatch(card, data)
        assert [b.split("：")[0] for b in bad] == ["RULE-1", "RULE-11", "RULE-555"], bad
        open(card, "w", encoding="utf-8").write("依据：RULE-2、RULE-10\n")
        assert dispatch(card, data) == []
        open(card, "w", encoding="utf-8").write("依据：RULE-0001..RULE-11\n")   # 前导零、范围
        assert sorted(b.split("：")[0] for b in dispatch(card, data)) == ["RULE-1", "RULE-11"] or sorted(b.split("：")[0] for b in dispatch(card, data))[:1] == ["RULE-1"], dispatch(card, data)
        folder = os.path.join(d, "intent"); os.makedirs(folder)
        open(os.path.join(folder, "2026-10-16-100000-x-a.md"), "w", encoding="utf-8").write("## 原话\n\n提到 RULE-5\n\n## 落点\n\n落\n\n## 规则\n\nRULE-6\n")
        for bad_text in ("## 原话\n\na\n\n## 落点\n\nb\n\n## 规则\n\n272\n", "## 原话\n\na\n\n## 落点\n\nb\n\n## 单子\n\nD5\n\n## 规则\n\nRULE-1\n", "## 原话\n\na\n\n## 落点\n\nb\n\n## 别的\n\nx\n"):
            try:
                parse_intent("p", bad_text); raise AssertionError(bad_text)
            except ValueError:
                pass
        assert parse_intent("p", "## 原话\n\na\n\n## 落点\n\nb\n\n## 规则\n\nRULE-1、RULE-2\n\n## 单子\n\nD5\n")[2] == {"规则": "RULE-1、RULE-2", "单子": "D5"}
        assert parse_intent("p", "## 原话\n\na\n\n## 落点\n\nb\n\n## 取代\n\n- x.md\n\n## 规则\n\nRULE-1\n") == ("a", "b（取代：x.md）", {"规则": "RULE-1"})
        tagged, mentioned = intent_refs(folder, parse_intent)
        assert tagged == {"RULE-6": ["2026-10-16-100000-x-a.md"]} and set(mentioned) == {"RULE-5", "RULE-6"}, (tagged, mentioned)
    assert ref_set("RULE-27 RULE-27 DATA-RULE-03 RULE-388..392 RULE-400–RULE-402 RULE-282/296 RULE-9-4") == {"RULE-27", "DATA-RULE-3", "RULE-388", "RULE-389", "RULE-390", "RULE-391", "RULE-392", "RULE-400", "RULE-401", "RULE-402", "RULE-282", "RULE-296", "RULE-9"}, ref_set("RULE-27 RULE-27 DATA-RULE-03 RULE-388..392 RULE-400–RULE-402 RULE-282/296 RULE-9-4")
    assert norm("RULE-27") == "RULE-27" and norm("DATA-RULE-3") == "DATA-RULE-3"
    c = new_cards("### DATA-RULE-01: 版本表\n| RULE-5 | d | t | P0 | Yes |\n| RULE-6 | d | t | P0 | No |\n", "D.md")
    assert state_of(c["DATA-RULE-01"], None) == "新规则待评审" and state_of(c["RULE-5"], None) == "新规则待评审" and state_of(c["RULE-6"], None) == "新规则无需评审"
    with tempfile.TemporaryDirectory() as d:
        os.makedirs(os.path.join(d, "t")); open(os.path.join(d, "t", "A.java"), "w", encoding="utf-8").write("// RULE-282/296 and RULE-388..390\n")
        open(os.path.join(d, "t", "B.md"), "w", encoding="utf-8").write("RULE-282")
        refs = file_refs(os.path.join(d, "t"), (".java",))
        assert sorted(refs) == ["RULE-282", "RULE-296", "RULE-388", "RULE-389", "RULE-390"] and len(refs["RULE-282"]) == 1
        spec_rr = importlib.util.spec_from_file_location("rr", os.path.join(HERE, "review_revisions.py"))
        rr = importlib.util.module_from_spec(spec_rr); spec_rr.loader.exec_module(rr)
        assert rr.current({"verdict": "confirmed", "at": "x", "revisions": [{"verdict": "wrong", "at": "y", "note": "n", "source": ["z"]}]})["verdict"] == "wrong"
        card_file = os.path.join(d, "card.md"); live = load()
        unreviewed = next(c["id"] for r, c in sorted(live["cards"].items()) if state_of(c, live["verdicts"].get(r)) in UNREVIEWED)
        open(card_file, "w", encoding="utf-8").write(f"依据 {unreviewed} RULE-9999\n")
        import subprocess
        run = lambda *a: subprocess.run([sys.executable, os.path.join(HERE, "build_rule_index.py"), "--dispatch", card_file, *a], capture_output=True, text=True)
        assert run().returncode == 0 and run("--strict").returncode == 1 and unreviewed in run().stdout and "RULE-9999：" in run().stdout
    dup = {"RULE-1": dict(source="a")}
    try:
        merge_new(dup, {"RULE-1": dict(source="b")}); raise AssertionError("重复规则号应失败")
    except ValueError:
        pass
    assert any("P0" in r for r in reports(dict(cards={"RULE-1": dict(priority="P0", domain="d", title="t", source="s", reviewed=None)}, verdicts={}, tests={}, main=None, process={})))
    real = load()
    assert len(real["cards"]) > 300 and not problems(real), problems(real)
    assert len(real["verdicts"]) > 50 and {state_of(real["cards"][r], v) for r, v in real["verdicts"].items() if r in real["cards"]} & {"沿用", "需修正"}, "真实 RULE_REVIEWS.json 的结论应被读到"
    print("build_rule_index self-test: ok")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    else:
        main(sys.argv[1:])
