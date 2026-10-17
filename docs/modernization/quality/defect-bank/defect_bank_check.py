#!/usr/bin/env python3
"""错题本检查（治理研究第 6 章）：各会话的 defect-bank/<会话名>.md 是否合格式、状态是否落到可定位处、同类错是否出现在两个以上会话。

格式见 README.md。每条一个小节 `### <前缀>-NNN　<标题>`（标题前是全角空格），字段按序：日期、类别、现象、根因、拦下于、漏过于、新增检查项、状态。
类别为受控词：转述未核、局部测试、先推后测、授权读宽、发布未验、提交卫生、沟通、其他。
检查：
  错误（失败）：条目字段缺或乱序、日期无效、编号前缀与册声明不一致或全局重复、状态词不合规。
  软检查（STRICT_SOFT=True 时同为错误，之前只报告，等各册迁移完成后打开）：缺类别或类别不在受控词内；状态“已落实”但“新增检查项”里没有存在的路径；状态“待落实”缺负责人或日期。
  告警：“待落实”距条目日期超过 7 天。
  报告：同一类别出现在两个以上会话的册里，提示由 one-quality 出一条机械守护（防错），而不是各册各补一条软约束。
不是这个格式的册（表格册）只统计并列出，等迁移。
用法：defect_bank_check.py | defect_bank_check.py --self-test
"""
import datetime, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", "..", "..", ".."))
CATEGORIES = ("转述未核", "局部测试", "先推后测", "授权读宽", "发布未验", "提交卫生", "沟通", "其他")
FIELDS = ("日期", "类别", "现象", "根因", "拦下于", "漏过于", "新增检查项", "状态")
STRICT_SOFT = False
OVERDUE_DAYS = 7
HEADING = re.compile(r"### ([A-Z0-9]+)-(\d{3})　(.+)")
PATH = re.compile(r"`([^`]+)`")
LOOSE_HEADING = re.compile(r"#{2,4}\s*[A-Za-z0-9]+-\d+\b.*")
BASES = ("", "docs/modernization", "docs/modernization/quality", "scripts", "scripts/ci", "scripts/hooks", "backend")


def parse_book(name, text):
    """-> (前缀, [条目 dict], [格式错误]) 或 None（不是本格式的册，如表格册）。标题长得像条目却不合格式（半角空格、小写前缀、四位编号、写成 ##）算格式错误，不静默丢弃。"""
    text = text.lstrip("\ufeff").replace("\r\n", "\n")
    loose = [l for l in text.split("\n") if LOOSE_HEADING.fullmatch(l) and not HEADING.fullmatch(l)]
    if not re.search(r"(?m)^### ", text) and not loose:
        return None
    prefixes = re.findall(r"(?m)^前缀：`([^`]*)`", text)
    problems = [f"标题格式不对（须为「### 前缀-NNN　标题」，全角空格）：{l[:30]}" for l in loose]
    if len(prefixes) > 1:
        problems.append(f"前缀声明有 {len(prefixes)} 处，只能一处")
    entries, current, last = [], None, None
    for line in text.split("\n"):
        h = HEADING.fullmatch(line)
        if h:
            current = dict(prefix=h.group(1), no=h.group(2), title=h.group(3), fields={}, order=[])
            entries.append(current); last = None
        elif current is not None and (m := re.fullmatch(r"- \*\*([^*]+)\*\*：(.*)", line)):
            current["fields"][m.group(1)] = m.group(2).strip(); current["order"].append(m.group(1)); last = m.group(1)
        elif current is not None and last and re.fullmatch(r"\s{2,}\S.*", line):
            current["fields"][last] += " " + line.strip()
        elif current is not None and line.startswith("- **"):
            problems.append(f"{current['prefix']}-{current['no']}：字段行格式不对（须为「- **名**：值」，全角冒号）：{line[:30]}")
    return (prefixes[0] if prefixes else None), entries, problems


def path_exists(token, root=ROOT):
    token = re.sub(r":\d+(-\d+)?$", "", token.strip().rstrip(",;。）)"))
    if not token or os.path.isabs(token) or ".." in token.split("/"):
        return False
    if not ("/" in token or re.search(r"\.(py|md|sh|java|yml|yaml|json|txt|sql|html)$", token)):
        return False
    for base in BASES:
        candidate = os.path.join(root, base, token)
        if os.path.isfile(candidate) and os.path.realpath(candidate).startswith(os.path.realpath(root) + os.sep):
            return True
    return False


def check_entry(book, prefix, e, today, root=ROOT):
    errors, soft, warns = [], [], []
    tag = f"{book} {e['prefix']}-{e['no']}"
    f = e["fields"]
    required = [x for x in FIELDS if x != "类别"]
    if [x for x in e["order"] if x in FIELDS] != [x for x in FIELDS if x in f] or any(x not in f for x in required) or len(set(e["order"])) != len(e["order"]):
        errors.append(f"{tag}：字段须依次为 {'、'.join(FIELDS)}（类别暂可缺），不重复：{e['order']}")
        return errors, soft, warns
    if prefix and e["prefix"] != prefix:
        errors.append(f"{tag}：编号前缀 {e['prefix']} 与册声明的前缀 {prefix} 不一致")
    try:
        date = datetime.date.fromisoformat(f["日期"])
    except ValueError:
        errors.append(f"{tag}：日期无效：{f['日期']}")
        date = None
    if f.get("类别") is None:
        soft.append(f"{tag}：缺类别")
    elif f["类别"] not in CATEGORIES:
        soft.append(f"{tag}：类别须取自 {'、'.join(CATEGORIES)}：{f['类别']}")
    status = f["状态"]
    m = re.match(r"(已落实|待落实|不适用)", status)
    if not m:
        errors.append(f"{tag}：状态须以 已落实 / 待落实 / 不适用 开头：{status[:20]}")
        return errors, soft, warns
    if m.group(1) == "已落实" and not any(path_exists(tok, root) for t in PATH.finditer(f["新增检查项"]) for tok in t.group(1).split()):
        soft.append(f"{tag}：已落实，但“新增检查项”里没有存在的路径（脚本、守护、检查项的仓库内位置）")
    if m.group(1) == "待落实":
        text = f["新增检查项"] + " " + status
        owner = re.search(r"负责人?[:：]\s*([^\s（(，,；;]+)", text) or re.search(r"由\s*(one-[a-z0-9-]+)\s*负责", text)
        dates = []
        for d in re.findall(r"\d{4}-\d{2}-\d{2}", text):
            try:
                dates.append(datetime.date.fromisoformat(d))
            except ValueError:
                pass
        if not owner or not re.fullmatch(r"one-[a-z0-9-]+|用户", owner.group(1)) or not dates:
            soft.append(f"{tag}：待落实须在“新增检查项”或状态里写“负责：<one-会话名>”和有效日期")
        if date and (today - date).days > OVERDUE_DAYS:
            warns.append(f"{tag}：待落实已 {(today - date).days} 天")
    if m.group(1) == "不适用" and not re.search(r"[（(].+[）)]", status):
        errors.append(f"{tag}：不适用须在括号里写理由")
    return errors, soft, warns


def scan(folder=HERE, today=None, root=ROOT):
    today = today or datetime.date.today()
    errors, soft, warns, legacy, categories, seen = [], [], [], [], {}, {}
    for f in sorted(os.listdir(folder)):
        if not f.endswith(".md") or f == "README.md":
            continue
        parsed = parse_book(f, open(os.path.join(folder, f), encoding="utf-8").read())
        if parsed is None:
            legacy.append(f)
            continue
        prefix, entries, problems = parsed
        errors += [f"{f}：{p}" for p in problems]
        if not prefix:
            errors.append(f"{f}：缺“前缀：`XXX`”声明")
        for e in entries:
            ident = f"{e['prefix']}-{e['no']}"
            if ident in seen:
                errors.append(f"{f} {ident}：编号与 {seen[ident]} 重复（编号不重用）")
            seen[ident] = f
            er, so, wa = check_entry(f, prefix, e, today, root)
            errors += er; soft += so; warns += wa
            if e["fields"].get("类别") in CATEGORIES:
                categories.setdefault(e["fields"]["类别"], {}).setdefault(f[:-3], []).append(ident)
    repeated = {c: v for c, v in categories.items() if len(v) >= 2 and c != "其他"}
    return errors, soft, warns, legacy, repeated


def main(argv):
    if argv:
        sys.exit(__doc__)
    errors, soft, warns, legacy, repeated = scan()
    if STRICT_SOFT:
        errors, soft = errors + soft, []
    for w in warns:
        print("警告：" + w, file=sys.stderr)
    if soft:
        kinds = {"缺类别或类别不合规": [x for x in soft if "类别" in x], "已落实无可定位路径": [x for x in soft if "没有存在的路径" in x], "待落实缺负责人或日期": [x for x in soft if "待落实须" in x]}
        for k, v in kinds.items():
            if v:
                print(f"报告：{k} {len(v)} 条（迁移完成后转为失败）：" + "、".join(x.split("：")[0].split()[-1] for x in v[:12]) + ("…" if len(v) > 12 else ""), file=sys.stderr)
    if legacy:
        print(f"报告：表格册（待迁移）{len(legacy)} 个：{'、'.join(legacy)}", file=sys.stderr)
    for c, books in sorted(repeated.items()):
        print(f"报告：类别「{c}」出现在 {len(books)} 个会话（{'、'.join(books)}）：出一条机械守护，不再各册各补软约束", file=sys.stderr)
    if errors:
        sys.exit("错题本检查失败：\n" + "\n".join(errors))
    print(f"defect-bank: {len(os.listdir(HERE))} 个文件，表格册 {len(legacy)} 个")


def self_test():
    import tempfile
    today = datetime.date(2026, 10, 30)

    def entry(**over):
        f = dict(日期="2026-10-16", 类别="转述未核", 现象="x", 根因="y", 拦下于="调度", 漏过于="无", 新增检查项="`scripts/precheck.sh`", 状态="已落实")
        f.update(over)
        return "".join(f"- **{k}**：{v}\n" for k, v in f.items() if v is not None)

    def book(*entries, prefix="TST"):
        body = "".join(f"### {prefix}-{i:03d}　标题\n\n{e}\n" for i, e in enumerate(entries, 1))
        return f"# 错题本：x\n\n前缀：`{prefix}`\n\n{body}"

    with tempfile.TemporaryDirectory() as d:
        os.makedirs(os.path.join(d, "scripts")); open(os.path.join(d, "scripts", "precheck.sh"), "w").write("x")

        def run(*books, **kw):
            folder = os.path.join(d, "bank"); os.makedirs(folder, exist_ok=True)
            for f in os.listdir(folder):
                os.remove(os.path.join(folder, f))
            for name, text in books:
                open(os.path.join(folder, name), "w", encoding="utf-8").write(text)
            return scan(folder, today, d)

        errors, soft, warns, legacy, repeated = run(("a.md", book(entry())))
        assert not (errors or soft or warns or legacy or repeated), (errors, soft, warns)
        assert run(("a.md", book(entry(日期="2026-13-01"))))[0] and run(("a.md", book(entry(状态="做完了"))))[0]
        assert run(("a.md", book(entry(状态="不适用"))))[0] and not run(("a.md", book(entry(状态="不适用（无此风险）"))))[0]
        assert run(("a.md", book(entry(根因=None))))[0] and run(("a.md", "# x\n\n前缀：`TST`\n\n### TST-001　t\n\n- **现象**：x\n- **日期**：2026-10-16\n"))[0]
        assert run(("a.md", book(entry(), prefix="TST").replace("前缀：`TST`", "前缀：`OTH`")))[0]
        assert run(("a.md", book(entry())), ("b.md", book(entry())))[0]   # 编号全局重复
        assert run(("a.md", "# x\n\n### TST-001　t\n\n" + entry()))[0]   # 缺前缀声明
        assert run(("a.md", book(entry(类别=None)) ))[1] and run(("a.md", book(entry(类别="随便"))))[1]
        assert run(("a.md", book(entry(新增检查项="画像守则第 3 条"))))[1] and run(("a.md", book(entry(新增检查项="`scripts/nope.sh`"))))[1]
        assert run(("a.md", book(entry(状态="待落实"))))[1] and not run(("a.md", book(entry(新增检查项="待补，负责：one-quality 2026-10-20", 状态="待落实"))))[1]
        assert run(("a.md", book(entry(新增检查项="待补，负责：one-quality 2026-10-20", 状态="待落实", 日期="2026-10-16"))))[2]
        assert not run(("a.md", book(entry(新增检查项="待补，负责：one-quality 2026-10-29", 状态="待落实", 日期="2026-10-28"))))[2]
        r = run(("a.md", book(entry())), ("b.md", book(entry(), prefix="OTH")))
        assert r[4] == {"转述未核": {"a": ["TST-001"], "b": ["OTH-001"]}}, r[4]
        assert not run(("a.md", book(entry(类别="其他"))), ("b.md", book(entry(类别="其他"), prefix="OTH")))[4]
        for bad_heading in ("### TST-001 半角空格", "### tst-001　小写", "### TST-0001　四位", "## TST-001　两个井号", "###TST-001　无空格"):
            assert run(("a.md", f"# x\n\n前缀：`TST`\n\n{bad_heading}\n\n" + entry()))[0], bad_heading
        assert run(("a.md", book(entry()).replace("- **现象**：", "- **现象**:")))[0]
        assert run(("a.md", book(entry()) + "前缀：`OTH`\n"))[0]
        two = book(entry()).replace("- **类别**：转述未核\n", "").replace("- **日期**：2026-10-16\n", "- **类别**：转述未核\n- **日期**：2026-10-16\n")
        assert run(("a.md", two))[0]
        assert run(("a.md", book(entry()).replace("- **状态**：已落实\n", "- **状态**：已落实\n- **状态**：已落实\n")))[0]
        assert not run(("a.md", book(entry(现象="第一行\n  续行内容"))))[0]
        for cmd, ok_ in (("`python3 scripts/precheck.sh`", True), ("`scripts/precheck.sh:12`", True), ("`precheck.sh`", True), ("`/etc/hosts`", False), ("`../../etc/passwd`", False), ("`scripts/`", False), ("`python3 -m x`", False)):
            assert bool(run(("a.md", book(entry(新增检查项=cmd))))[1]) != ok_, cmd
        for owner_text, ok_ in (("待补，负责：one-quality 2026-10-20", True), ("待补，由 one-quality 负责，2026-10-20", True), ("待补，负责：（待定）2026-10-20", False), ("待补，负责：one-quality 2026-99-99", False), ("待补 2026-10-20", False), ("待补，负责：TODO 2026-10-20", False), ("待补，负责：用户 2026-10-20", True)):
            assert bool(run(("a.md", book(entry(新增检查项=owner_text, 状态="待落实", 日期="2026-10-29"))))[1]) != ok_, owner_text
        assert run(("t.md", "# x 错题本\n\n| 编号 | 现象 |\n|---|---|\n| RB-01 | y |\n"))[3] == ["t.md"]
    errors, soft, warns, legacy, repeated = scan()
    assert not errors, errors   # 真实目录现状不得失败
    print("defect_bank_check self-test: ok")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    else:
        main(sys.argv[1:])
