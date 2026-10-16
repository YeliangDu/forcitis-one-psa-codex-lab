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
PATH = re.compile(r"`([^`\s]+)[^`]*`")


def parse_book(name, text):
    """-> (前缀, [条目 dict]) 或 None（不是本格式的册，如表格册）。"""
    if not re.search(r"(?m)^### ", text):
        return None
    prefix = re.search(r"(?m)^前缀：`([A-Z0-9]+)`", text)
    entries, current = [], None
    for line in text.split("\n"):
        h = HEADING.fullmatch(line)
        if h:
            current = dict(prefix=h.group(1), no=h.group(2), title=h.group(3), fields={}, order=[])
            entries.append(current)
        elif current is not None and (m := re.fullmatch(r"- \*\*([^*]+)\*\*：(.*)", line)):
            current["fields"][m.group(1)] = m.group(2).strip()
            current["order"].append(m.group(1))
    return (prefix.group(1) if prefix else None), entries


def path_exists(token, root=ROOT):
    token = token.rstrip(",;。")
    if not ("/" in token or re.search(r"\.(py|md|sh|java|yml|yaml|json|txt|sql|html)$", token)):
        return False
    return any(os.path.exists(os.path.join(root, base, token)) for base in ("", "docs/modernization", "docs/modernization/quality"))


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
    if m.group(1) == "已落实" and not any(path_exists(t.group(1), root) for t in PATH.finditer(f["新增检查项"])):
        soft.append(f"{tag}：已落实，但“新增检查项”里没有存在的路径（脚本、守护、检查项的仓库内位置）")
    if m.group(1) == "待落实":
        if not re.search(r"负责[:：]\s*\S+", f["新增检查项"] + status) or not re.search(r"\d{4}-\d{2}-\d{2}", f["新增检查项"] + status):
            soft.append(f"{tag}：待落实须在“新增检查项”或状态里写“负责：<会话>”和日期")
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
        prefix, entries = parsed
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
        kinds = {"缺类别或类别不合规": sum("类别" in x for x in soft), "已落实无可定位路径": sum("没有存在的路径" in x for x in soft), "待落实缺负责人或日期": sum("待落实须" in x for x in soft)}
        print("报告：软检查未通过（迁移完成后转为失败）：" + "；".join(f"{k} {v} 条" for k, v in kinds.items() if v), file=sys.stderr)
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
        assert run(("t.md", "# x 错题本\n\n| 编号 | 现象 |\n|---|---|\n| RB-01 | y |\n"))[3] == ["t.md"]
    errors, soft, warns, legacy, repeated = scan()
    assert not errors, errors   # 真实目录现状不得失败
    print("defect_bank_check self-test: ok")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    else:
        main(sys.argv[1:])
