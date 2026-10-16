#!/usr/bin/env python3
"""提议稿目录 architecture/proposals/ 的状态行检查（治理研究第 5 章）。

每份 .md（README.md 除外）的前 8 行内应有一行受控状态：
  状态：<草稿|评审中|已确认|历史记录|已成文→ADR-NNNN|已并入→文件|已取代→文件>｜YYYY-MM-DD｜负责：<会话名>
检查（对写成这个格式的行，不通过则失败）：日期有效；负责是会话名；「已成文→ADR-NNNN」所指 ADR 存在且已采纳；「已并入」「已取代」所指文件存在（相对 proposals/ 或 docs/modernization/）。
对没写成这个格式的文件（旧写法，约半数）和没有状态行的文件只统计、列名单，不失败——清理由 one-architecture 做，之后把 REQUIRE_STATUS_LINE 改为 True，缺行与旧写法即失败。
告警：状态为「草稿」且最近一次提交距今超过 14 天。
用法：check_proposals.py | check_proposals.py --list（列出旧写法与缺行的文件）| check_proposals.py --self-test
"""
import datetime, os, re, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.normpath(os.path.join(HERE, ".."))
PROPOSALS = os.path.join(HERE, "proposals")
REQUIRE_STATUS_LINE = False
STALE_DAYS = 14
LINE = re.compile(r"状态：(草稿|评审中|已确认|历史记录|已成文→ADR-\d{4}|已并入→\S+|已取代→\S+)｜(\d{4}-\d{2}-\d{2})｜负责：(\S+)\s*")


def status_of(text):
    """前 8 行内的受控状态行 -> (状态, 日期, 负责) 或 None；有「状态」字样但格式不对 -> "旧写法"；都没有 -> "缺行"。"""
    head = text.split("\n")[:8]
    for line in head:
        m = LINE.fullmatch(line)
        if m:
            return m.groups()
    return "旧写法" if any("状态" in l for l in head) else "缺行"


def problems(name, parsed, mod=MOD, proposals=PROPOSALS, today=None, last_commit=None):
    errors, warns = [], []
    if isinstance(parsed, str):
        return errors, warns
    status, date, owner = parsed
    try:
        d = datetime.date.fromisoformat(date)
    except ValueError:
        errors.append(f"{name}：日期无效：{date}")
        d = None
    if not re.fullmatch(r"[A-Za-z0-9._-]+", owner):
        errors.append(f"{name}：负责须为会话名：{owner}")
    if status.startswith("已成文→"):
        adr = os.path.join(mod, "adr", "src", status.split("→")[1] + ".md")
        if not os.path.exists(adr):
            errors.append(f"{name}：{status} 所指 ADR 不存在")
        elif not re.search(r"(?m)^- \*\*状态\*\*：已采纳", open(adr, encoding="utf-8").read()):
            errors.append(f"{name}：{status} 所指 ADR 尚未采纳（采纳后才可标已成文）")
    elif status.startswith(("已并入→", "已取代→")):
        target = status.split("→", 1)[1]
        if not any(os.path.exists(os.path.join(base, target)) for base in (proposals, mod)):
            errors.append(f"{name}：{status} 所指文件不存在")
    if status == "草稿" and last_commit and today and (today - last_commit).days > STALE_DAYS:
        warns.append(f"{name}：草稿已 {(today - last_commit).days} 天无提交，请确认、并入或标历史记录")
    return errors, warns


def last_commit_date(path):
    r = subprocess.run(["git", "log", "-1", "--format=%cs", "--", path], capture_output=True, text=True, cwd=os.path.dirname(path))
    try:
        return datetime.date.fromisoformat(r.stdout.strip())
    except ValueError:
        return None


def scan(proposals=PROPOSALS, mod=MOD, today=None, commits=True):
    errors, warns, counts, lists = [], [], {"合规": 0, "旧写法": 0, "缺行": 0}, {"旧写法": [], "缺行": []}
    for f in sorted(os.listdir(proposals)):
        if not f.endswith(".md") or f == "README.md":
            continue
        parsed = status_of(open(os.path.join(proposals, f), encoding="utf-8").read())
        if isinstance(parsed, str):
            counts[parsed] += 1; lists[parsed].append(f)
        else:
            counts["合规"] += 1
        last = last_commit_date(os.path.join(proposals, f)) if commits and parsed != "缺行" and not isinstance(parsed, str) and parsed[0] == "草稿" else None
        e, w = problems(f, parsed, mod, proposals, today or datetime.date.today(), last)
        errors += e; warns += w
    if REQUIRE_STATUS_LINE:
        errors += [f"{f}：{k}，须写受控状态行" for k in ("旧写法", "缺行") for f in lists[k]]
    return errors, warns, counts, lists


def main(argv):
    if argv not in ([], ["--list"]):
        sys.exit(__doc__)
    errors, warns, counts, lists = scan()
    for w in warns:
        print("警告：" + w, file=sys.stderr)
    if argv == ["--list"]:
        for k, v in lists.items():
            print(f"{k}（{len(v)}）：" + "、".join(v))
    if errors:
        sys.exit("提议稿状态检查失败：\n" + "\n".join(errors))
    print(f"proposals: 合规 {counts['合规']}，旧写法 {counts['旧写法']}，缺行 {counts['缺行']}" + ("" if REQUIRE_STATUS_LINE else "（旧写法与缺行暂只统计）"))


def self_test():
    import tempfile
    ok = "状态：草稿｜2026-10-16｜负责：one-architecture\n# 标题\n"
    assert status_of(ok) == ("草稿", "2026-10-16", "one-architecture")
    assert status_of("# t\n状态：已成文→ADR-0044｜2026-10-16｜负责：x\n")[0] == "已成文→ADR-0044"
    assert status_of("状态：研究提议，待用户裁定 · 日期：2026-10-15\n") == "旧写法" and status_of("- **状态**：提议\n") == "旧写法"
    assert status_of("# 标题\n正文\n") == "缺行" and status_of("\n" * 9 + "状态：草稿｜2026-10-16｜负责：x\n") == "缺行"
    assert status_of("状态：做着｜2026-10-16｜负责：x\n") == "旧写法"
    with tempfile.TemporaryDirectory() as d:
        mod = os.path.join(d, "mod"); prop = os.path.join(mod, "architecture", "proposals"); os.makedirs(prop); os.makedirs(os.path.join(mod, "adr", "src"))
        open(os.path.join(mod, "adr", "src", "ADR-0001.md"), "w", encoding="utf-8").write("# ADR-0001：x\n\n- **状态**：已采纳（用户）\n")
        open(os.path.join(mod, "adr", "src", "ADR-0002.md"), "w", encoding="utf-8").write("# ADR-0002：x\n\n- **状态**：提议\n")
        open(os.path.join(prop, "other.md"), "w", encoding="utf-8").write(ok)

        def errs(line, **kw):
            return problems("f.md", status_of(line + "\n"), mod, prop, **kw)[0]
        assert not errs("状态：已成文→ADR-0001｜2026-10-16｜负责：a")
        assert any("尚未采纳" in e for e in errs("状态：已成文→ADR-0002｜2026-10-16｜负责：a"))
        assert any("不存在" in e for e in errs("状态：已成文→ADR-0099｜2026-10-16｜负责：a"))
        assert not errs("状态：已并入→other.md｜2026-10-16｜负责：a") and not errs("状态：已取代→other.md｜2026-10-16｜负责：a")
        assert any("所指文件不存在" in e for e in errs("状态：已取代→nope.md｜2026-10-16｜负责：a"))
        assert any("日期无效" in e for e in errs("状态：草稿｜2026-13-45｜负责：a"))
        today = datetime.date(2026, 10, 30)
        assert problems("f.md", status_of(ok), mod, prop, today, datetime.date(2026, 10, 1))[1] and not problems("f.md", status_of(ok), mod, prop, today, datetime.date(2026, 10, 20))[1]
        assert not problems("f.md", status_of("状态：评审中｜2026-10-16｜负责：a\n"), mod, prop, today, datetime.date(2026, 1, 1))[1]
        open(os.path.join(prop, "old.md"), "w", encoding="utf-8").write("状态：草案\n")
        open(os.path.join(prop, "none.md"), "w", encoding="utf-8").write("# t\n")
        errors, _, counts, lists = scan(prop, mod, today, commits=False)
        assert not errors and counts == {"合规": 1, "旧写法": 1, "缺行": 1} and lists == {"旧写法": ["old.md"], "缺行": ["none.md"]}, (errors, counts)
        global REQUIRE_STATUS_LINE
        REQUIRE_STATUS_LINE = True
        try:
            assert len(scan(prop, mod, today, commits=False)[0]) == 2
        finally:
            REQUIRE_STATUS_LINE = False
    errors, _, counts, _ = scan(commits=False)
    assert not errors, errors   # 真实目录现状不得失败
    print("check_proposals self-test: ok")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    else:
        main(sys.argv[1:])
