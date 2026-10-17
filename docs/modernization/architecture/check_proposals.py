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
LOOKS_NEW = re.compile(r"｜|负责[:：]|^\s*[>*-]*\s*状态[:：]\s*\**(?:草稿|评审中|已确认|历史记录|已成文|已并入|已取代)")
LINE = re.compile(r"状态：(草稿|评审中|已确认|历史记录|已成文→ADR-\d{4}|已并入→\S+|已取代→\S+)｜(\d{4}-\d{2}-\d{2})｜负责：(\S+)\s*")


def status_of(text):
    """前 8 行内的受控状态行 -> (状态, 日期, 负责) 或 None；有「状态」字样但格式不对 -> "旧写法"；都没有 -> "缺行"。"""
    head = text.split("\n")[:8]
    for line in head:
        m = LINE.fullmatch(line)
        if m:
            return m.groups()
    for line in head:
        if "状态" in line and LOOKS_NEW.search(line):
            return "近似"   # 看着像新格式却不合规：全角｜、状态：、行首无空白、日期 YYYY-MM-DD、负责写一个会话名
    return "旧写法" if any("状态" in l for l in head) else "缺行"


def known_sessions(mod=MOD):
    folder = os.path.join(mod, "sessions")
    names = {f[:-3] for f in os.listdir(folder) if f.endswith(".md") and not f.startswith("_")} if os.path.isdir(folder) else set()
    retired = os.path.join(folder, "_retired.md")
    if os.path.exists(retired):
        names |= set(re.findall(r"(?m)^- (\S+)：", open(retired, encoding="utf-8").read()))
    return names


def inside(path, base):
    return os.path.realpath(path).startswith(os.path.realpath(base) + os.sep)


def problems(name, parsed, mod=MOD, proposals=PROPOSALS, today=None, last_commit=None):
    errors, warns = [], []
    if parsed == "近似":
        return [f"{name}：状态行看着像受控格式却不合规，须为「状态：<词>｜YYYY-MM-DD｜负责：<会话名>」，行首不留空白或符号"], warns
    if isinstance(parsed, str):
        return errors, warns
    status, date, owner = parsed
    try:
        d = datetime.date.fromisoformat(date)
    except ValueError:
        errors.append(f"{name}：日期无效：{date}")
        d = None
    if owner not in known_sessions(mod):
        errors.append(f"{name}：负责 {owner} 不是已知会话（sessions/ 画像或 _retired.md）")
    if status.startswith("已成文→"):
        adr = os.path.join(mod, "adr", "src", status.split("→")[1] + ".md")
        if not os.path.exists(adr):
            errors.append(f"{name}：{status} 所指 ADR 不存在")
        elif not re.search(r"(?m)^- \*\*状态\*\*：(已采纳|已被 ADR-\d{4} 取代)", open(adr, encoding="utf-8").read()):
            errors.append(f"{name}：{status} 所指 ADR 尚未采纳（采纳后才可标已成文）")
    elif status.startswith(("已并入→", "已取代→")):
        target = status.split("→", 1)[1].split("#")[0]
        roots = (proposals, mod, os.path.normpath(os.path.join(mod, "..", "..")))
        if os.path.isabs(target) or not any(os.path.isfile(os.path.join(base, target)) and inside(os.path.join(base, target), roots[2]) for base in roots):
            errors.append(f"{name}：{status} 所指文件不存在（写 proposals/ 内、docs/modernization/ 内或仓库根相对的文件路径，不是目录）")
    if status == "草稿" and last_commit and today and (today - last_commit).days > STALE_DAYS:
        warns.append(f"{name}：草稿已 {(today - last_commit).days} 天无提交，请确认、并入或标历史记录")
    return errors, warns


def last_commit_date(path):
    try:
        if subprocess.run(["git", "rev-parse", "--is-shallow-repository"], capture_output=True, text=True, cwd=os.path.dirname(path)).stdout.strip() != "false":
            return None   # 浅克隆里每个文件的最近提交都是 HEAD，日期无意义；取不到 git 状态也不告警
        r = subprocess.run(["git", "log", "-1", "--format=%cs", "--", path], capture_output=True, text=True, cwd=os.path.dirname(path))
        return datetime.date.fromisoformat(r.stdout.strip())
    except (ValueError, OSError):
        return None


def scan(proposals=PROPOSALS, mod=MOD, today=None, commits=True):
    errors, warns, counts, lists = [], [], {"合规": 0, "旧写法": 0, "缺行": 0, "近似": 0}, {"旧写法": [], "缺行": [], "近似": []}
    for f in sorted(os.listdir(proposals)):
        if not f.endswith(".md") or f == "README.md":
            continue
        parsed = status_of(open(os.path.join(proposals, f), encoding="utf-8").read())
        if isinstance(parsed, str):
            counts[parsed] += 1; lists[parsed].append(f)
        else:
            counts["合规"] += 1
        last = last_commit_date(os.path.join(proposals, f)) if commits and not isinstance(parsed, str) and parsed[0] == "草稿" else None
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
    assert status_of("状态：做着｜2026-10-16｜负责：x\n") == "近似"
    with tempfile.TemporaryDirectory() as d:
        mod = os.path.join(d, "mod"); prop = os.path.join(mod, "architecture", "proposals"); os.makedirs(prop); os.makedirs(os.path.join(mod, "adr", "src")); os.makedirs(os.path.join(mod, "sessions"))
        for n in ("one-a.md", "one-architecture.md"):
            open(os.path.join(mod, "sessions", n), "w", encoding="utf-8").write("# x\n")
        open(os.path.join(mod, "sessions", "_retired.md"), "w", encoding="utf-8").write("- one-old：已交接\n")
        open(os.path.join(mod, "adr", "src", "ADR-0001.md"), "w", encoding="utf-8").write("# ADR-0001：x\n\n- **状态**：已采纳（用户）\n")
        open(os.path.join(mod, "adr", "src", "ADR-0002.md"), "w", encoding="utf-8").write("# ADR-0002：x\n\n- **状态**：提议\n")
        open(os.path.join(prop, "other.md"), "w", encoding="utf-8").write(ok)

        def errs(line, **kw):
            return problems("f.md", status_of(line + "\n"), mod, prop, **kw)[0]
        assert not errs("状态：已成文→ADR-0001｜2026-10-16｜负责：one-a")
        assert any("尚未采纳" in e for e in errs("状态：已成文→ADR-0002｜2026-10-16｜负责：one-a"))
        assert any("不存在" in e for e in errs("状态：已成文→ADR-0099｜2026-10-16｜负责：one-a"))
        assert not errs("状态：已并入→other.md｜2026-10-16｜负责：one-a") and not errs("状态：已取代→other.md｜2026-10-16｜负责：one-a")
        assert not errs("状态：已并入→other.md#锚点｜2026-10-16｜负责：one-old") and any("不是已知会话" in e for e in errs("状态：草稿｜2026-10-16｜负责：TODO"))
        for bad_target in ("/etc/hosts", "adr", "../../../etc/hosts"):
            assert any("所指文件不存在" in e for e in errs(f"状态：已取代→{bad_target}｜2026-10-16｜负责：one-a")), bad_target
        open(os.path.join(mod, "adr", "src", "ADR-0003.md"), "w", encoding="utf-8").write("# ADR-0003：x\n\n- **状态**：已被 ADR-0004 取代\n")
        assert not errs("状态：已成文→ADR-0003｜2026-10-16｜负责：one-a")
        for near in ("状态:草稿|2026-10-16|负责:one-a", " 状态：草稿｜2026-10-16｜负责：one-a", "状态：草稿｜2026/10/16｜负责：one-a", "状态：草稿｜2026-10-16｜负责：one-a one-b", "> 状态：草稿｜2026-10-16｜负责：one-a"):
            assert status_of(near + "\n") == "近似" and problems("f.md", "近似")[0], near
        assert any("所指文件不存在" in e for e in errs("状态：已取代→nope.md｜2026-10-16｜负责：one-a"))
        assert any("日期无效" in e for e in errs("状态：草稿｜2026-13-45｜负责：one-a"))
        today = datetime.date(2026, 10, 30)
        assert problems("f.md", status_of(ok), mod, prop, today, datetime.date(2026, 10, 1))[1] and not problems("f.md", status_of(ok), mod, prop, today, datetime.date(2026, 10, 20))[1]
        assert not problems("f.md", status_of("状态：评审中｜2026-10-16｜负责：one-a\n"), mod, prop, today, datetime.date(2026, 1, 1))[1]
        # 草稿过期告警接线：真实 git 仓库里一份 2026-01-01 提交的草稿
        repo = os.path.join(d, "repo"); rprop = os.path.join(repo, "architecture", "proposals"); os.makedirs(rprop)
        env = dict(os.environ, GIT_AUTHOR_DATE="2026-01-01T00:00:00", GIT_COMMITTER_DATE="2026-01-01T00:00:00", GIT_AUTHOR_NAME="t", GIT_AUTHOR_EMAIL="t@t", GIT_COMMITTER_NAME="t", GIT_COMMITTER_EMAIL="t@t")
        git = lambda *a: subprocess.run(["git", *a], cwd=repo, env=env, check=True, capture_output=True)
        git("init", "-q"); open(os.path.join(rprop, "draft.md"), "w", encoding="utf-8").write(ok); git("add", "."); git("commit", "-qm", "x")
        assert last_commit_date(os.path.join(rprop, "draft.md")) == datetime.date(2026, 1, 1)
        shallow = os.path.join(d, "shallow")
        subprocess.run(["git", "clone", "-q", "--depth", "1", "file://" + repo, shallow], check=True, capture_output=True, env=env)
        assert last_commit_date(os.path.join(shallow, "architecture", "proposals", "draft.md")) is None   # 浅克隆：不判断
        open(os.path.join(rprop, "fresh.md"), "w", encoding="utf-8").write(ok)   # 未提交：无日期，不告警
        assert last_commit_date(os.path.join(rprop, "fresh.md")) is None
        import shutil
        shutil.copytree(os.path.join(mod, "sessions"), os.path.join(repo, "sessions")); shutil.copytree(os.path.join(mod, "adr"), os.path.join(repo, "adr"))
        _, warns, _, _ = scan(rprop, repo, datetime.date(2026, 10, 30), commits=True)
        assert len(warns) == 1 and "draft.md" in warns[0], warns
        open(os.path.join(prop, "old.md"), "w", encoding="utf-8").write("状态：草案\n")
        open(os.path.join(prop, "none.md"), "w", encoding="utf-8").write("# t\n")
        errors, _, counts, lists = scan(prop, mod, today, commits=False)
        assert not errors and counts == {"合规": 1, "旧写法": 1, "缺行": 1, "近似": 0} and lists == {"旧写法": ["old.md"], "缺行": ["none.md"], "近似": []}, (errors, counts)
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
