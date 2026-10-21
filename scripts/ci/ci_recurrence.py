#!/usr/bin/env python3
"""每晚复发检测（错题本机制，用户 2026-10-17 批准）：把最近 N 小时失败的 ci 运行，按错题本里已登记的「报错特征」归类。已落实的条目再次命中就是「复发」：它的新增检查项没拦住，须修。

特征写在错题本条目「现象」里 `报错特征：` 之后、第一个句号之前（括号里的说明不算）：反引号包起来的字符串；用「加」连接的几组须同时出现（组与组「且」），一组里用顿号或逗号隔开的几个任一出现即可。
短于 13 个字符的记号太泛，不用。条目可另写 `修复提交：` 加反引号包起来的提交号。
归类（每个失败的运行与尝试各一行）：
  复发     状态为已落实，运行晚于条目日期，特征命中；这一条的新增检查项没拦住，须修。
  旧基线   条目登记了修复提交，而该运行的 PR 头不含它：合 main 即自愈，不算复发（被测的合并树未必含修复，需要时人工看）。取不到祖先关系时仍算复发并注明「未能判断」。
  已登记   特征命中，但条目还是待落实，或运行早于条目日期：已知问题仍在发生。
  未登记   没有任何条目命中。
  日志不可得  取不到日志（不当作未登记）。
被合并队列「失败重跑一次」吸收的偶发失败（运行最终成功、尝试次数大于 1）也检查：逐次取前面各次尝试的失败日志。
用法：ci_recurrence.py [--hours 26] [--bank 目录] [--json] [--fail-on-recurrence]  |  ci_recurrence.py --self-test
默认退出码 0（只报告）；加 --fail-on-recurrence 时有复发类（含未能判断修复是否在 PR 头里）退出码 1，其余 0。错题本目录不存在、条目标题像条目却格式不对，直接失败（不静默当作没有）。
"""
import datetime
import json
import os
import re
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
BANK = os.path.join(ROOT, "docs", "modernization", "quality", "defect-bank")
HEADING = re.compile(r"### ([A-Z0-9]+-\d{3})　(.+)")
LOOSE = re.compile(r"#{2,4}\s*[A-Za-z0-9]+-\d+\b.*")
FIELD = re.compile(r"- \*\*([^*]+)\*\*：(.*)")
MIN_TOKEN = 13
RELEVANT = re.compile(r"<<< (FAILURE|ERROR)|\[ERROR\]|Caused by|Exception|Error:|AssertionError|AssertionFailedError|Expecting|expected|Wanted|TooMany|exceptions\.verification|Application run failed|"
                      r"Container startup failed|forked VM|Timeout|timed out|##\[error\]|Process completed with exit code|produced no report|did not run")
NOISE = re.compile(r'^(?:[^\t]*\t){0,2}\S*\s*\{"@timestamp"')   # the application's own JSON log lines: not what failed
LIMIT = 800


def parse_bank(bank=BANK):
    """({条目号: {groups, status, date, fix}}, [没有报错特征的条目号])。目录不存在、像条目的标题格式不对都失败。"""
    if not os.path.isdir(bank):
        raise SystemExit(f"错题本目录不存在：{bank}")
    entries, featureless = {}, []
    for name in sorted(os.listdir(bank)):
        if not name.endswith(".md") or name == "README.md":
            continue
        text = open(os.path.join(bank, name), encoding="utf-8").read().replace("\r\n", "\n")
        loose = [l for l in text.split("\n") if LOOSE.fullmatch(l) and not HEADING.fullmatch(l)]
        if loose:
            raise SystemExit(f"{name}：标题长得像条目却格式不对（须为「### 前缀-NNN　标题」，全角空格）：{loose[0][:40]}")
        ident, field, fields = None, None, {}
        for line in text.split("\n") + ["### X-000　end"]:
            h = HEADING.fullmatch(line)
            if h or re.match(r"#{1,2} ", line):   # a new entry, or a heading that ends the entry (an appendix is no part of it)
                if ident:
                    done = entry_of(fields)
                    if done:
                        entries[ident] = done
                    elif ident != "X-000":
                        featureless.append(ident)
                ident, field, fields = (h.group(1) if h else None), None, {}
                continue
            if ident is None:
                continue
            f = FIELD.fullmatch(line)
            if f:
                field = f.group(1)
                fields[field] = f.group(2)
            elif field and line.strip() and not line.startswith("- **"):
                fields[field] += " " + line.strip()
    return entries, [i for i in featureless if i != "X-000"]


def entry_of(fields):
    now = fields.get("现象", "")
    m = re.search(r"报错特征：(.*)", now)
    if not m:
        return None
    segment = re.sub(r"（[^）]*）", "", m.group(1).split("。")[0])
    groups = [[t for t in re.findall(r"`([^`]+)`", part) if len(t) >= MIN_TOKEN] for part in segment.split("加")]
    groups = [g for g in groups if g]
    if not groups:
        return None
    everything = " ".join(fields.values())
    fix = re.search(r"修复提交：`([0-9a-f]{7,40})`", everything)
    status = re.match(r"(已落实|待落实|不适用)", fields.get("状态", ""))
    try:
        date = datetime.date.fromisoformat(fields.get("日期", ""))
    except ValueError:
        date = None
    return {"groups": groups, "status": status.group(1) if status else "", "date": date, "fix": fix.group(1) if fix else None}


def relevant_lines(log):
    return [l for l in log.splitlines() if RELEVANT.search(l) and not NOISE.match(l)]


def classify(lines, entries):
    """{条目号: 命中的第一个记号}：每一组至少一个记号出现，才算命中。"""
    hits = {}
    text = "\n".join(lines)
    for ident, e in entries.items():
        found = [next((t for t in group if t in text), None) for group in e["groups"]]
        if all(found):
            hits[ident] = found[0]
    return hits


class Gh:
    def runs(self, hours):
        """已结束、最近 hours 小时建立、失败或经历过重跑（尝试次数大于 1）的 ci 运行；列表到顶时第二个返回值为 True。"""
        since = (datetime.datetime.now(datetime.timezone.utc) - datetime.timedelta(hours=hours)).strftime("%Y-%m-%dT%H:%M:%SZ")
        r = subprocess.run(["gh", "run", "list", "--workflow", "ci", "--status", "completed", "--created", f">={since}", "--limit", str(LIMIT), "--json", "databaseId,headBranch,event,createdAt,headSha,attempt,conclusion"],
                           capture_output=True, text=True, timeout=180)
        if r.returncode:
            raise SystemExit(f"gh run list failed: {r.stderr.strip()[:200]}")
        rows = json.loads(r.stdout)
        return [x for x in rows if x["conclusion"] == "failure" or x.get("attempt", 1) > 1], len(rows) >= LIMIT

    def log(self, run_id, attempt, last):
        args = ["gh", "run", "view", str(run_id), "--log-failed"] + ([] if last else ["--attempt", str(attempt)])
        try:
            r = subprocess.run(args, capture_output=True, text=True, timeout=300)
        except subprocess.SubprocessError:
            return None
        return r.stdout if r.returncode == 0 and r.stdout.strip() else None

    def has_commit(self, fix, head, created):
        """True 当修复在运行的 PR 头里，False 当不在，None 当判断不了（对象取不到、git 出错）。git 在仓库根执行。
        只看 PR 头：pull_request 事件测的是 PR 头与 base 的合并树，GitHub 的合并引用可能是旧的（实测 38962295103 测的 base 早于修复），所以 base 里有没有修复不可靠。"""
        def git(*a, timeout=120):
            return subprocess.run(["git", *a], cwd=ROOT, capture_output=True, text=True, timeout=timeout)
        try:
            if git("cat-file", "-e", f"{fix}^{{commit}}").returncode:
                return None
            if git("cat-file", "-e", f"{head}^{{commit}}").returncode:
                git("fetch", "-q", "origin", head)
            if git("cat-file", "-e", f"{head}^{{commit}}").returncode:
                return None
            code = git("merge-base", "--is-ancestor", fix, head).returncode
        except (OSError, subprocess.SubprocessError):
            return None
        return {0: True, 1: False}.get(code)


def attempts_of(run):
    """[(尝试号, 是最后一次)]：失败的最后一次，加上更早各次（它们的失败被重跑盖掉了）。"""
    n = run.get("attempt", 1)
    return [(k, k == n) for k in range(1, n + 1) if k < n or run["conclusion"] == "failure"]


def report(runs, gh, entries):
    rows = []
    for run in runs:
        created = run.get("createdAt", "")
        for k, last in attempts_of(run):
            row = {"run": run["databaseId"], "attempt": k, "branch": run["headBranch"], "event": run["event"]}
            log = gh.log(run["databaseId"], k, last)
            if log is None:
                rows.append(dict(row, result="unavailable", entries={}))
                continue
            hits, kinds = classify(relevant_lines(log), entries), {}
            day = created[:10]
            for ident, token in hits.items():
                e = entries[ident]
                verdict = gh.has_commit(e["fix"], run.get("headSha", ""), created) if e["fix"] and run.get("headSha") else True
                if e["fix"] and verdict is False:
                    kinds[ident] = ("old base", token)
                elif e["status"] != "已落实" or (e["date"] and day and day < e["date"].isoformat()):
                    kinds[ident] = ("known", token)
                else:
                    kinds[ident] = ("recurrence" + ("?" if e["fix"] and verdict is None else ""), token)
            result = "recurrence" if any(k.startswith("recurrence") for k, _ in kinds.values()) else ("known" if kinds else "unregistered")
            rows.append(dict(row, result=result, entries=kinds))
    return rows


def render(rows, featureless=(), capped=False):
    rec = {}
    for r in rows:
        for ident, (kind, _) in r["entries"].items():
            if kind.startswith("recurrence"):
                rec.setdefault(ident, []).append(f"{r['run']}#{r['attempt']}")
    count = lambda name: sum(1 for r in rows if r["result"] == name)
    lines = [f"ci 失败的运行与尝试 {len(rows)} 次：复发 {len(rec)} 条，已登记 {count('known') + sum(1 for r in rows if r['result'] == 'recurrence' and False)} 次，未登记 {count('unregistered')} 次，日志不可得 {count('unavailable')} 次"]
    names = {"recurrence": "复发", "recurrence?": "复发（未能判断修复是否在 PR 头里）", "old base": "旧基线（PR 头不含修复，合 main 自愈）", "known": "已登记（待落实或早于登记）"}
    for r in rows:
        if r["result"] == "unavailable":
            tail = "日志不可得"
        elif not r["entries"]:
            tail = "未登记"
        else:
            tail = "；".join(f"{names[kind]} {i}（{t[:40]}）" for i, (kind, t) in r["entries"].items())
        lines.append(f"- {r['run']}#{r['attempt']} {r['branch']} {r['event']}：{tail}")
    for ident, ids in sorted(rec.items()):
        lines.append(f"复发 {ident}：运行 {', '.join(ids)}；这一条的新增检查项没拦住，须修")
    if featureless:
        lines.append(f"没有报错特征、不在检测范围的条目 {len(featureless)} 条：{'、'.join(featureless[:30])}{'…' if len(featureless) > 30 else ''}")
    if capped:
        lines.append(f"注意：列表到顶（{LIMIT} 次运行），更早的失败没有检查")
    return "\n".join(lines)


def self_test():
    with tempfile.TemporaryDirectory() as bank:
        open(os.path.join(bank, "one-x.md"), "w", encoding="utf-8").write(
            "# 错题本\n\n前缀：`TST`\n\n"
            "### TST-001　一\n\n- **日期**：2026-10-16\n- **现象**：失败 5 次。报错特征：`Expecting empty but was: [[\"{\"instanceId\"` 加 `ProcessArchiveJobIT.archivesThenDeletes`（断言 `vault.batches` 为空）。其他说明 `不是特征的记号在句号之后`\n- **根因**：x\n- **新增检查项**：修复提交：`c4cced3c`\n- **状态**：已落实\n\n"
            "### TST-002　二\n\n- **现象**：没有特征词\n- **根因**：x\n\n"
            "### TST-003　三\n\n- **日期**：2026-10-16\n- **现象**：第一行\n续行没有缩进。报错特征：`Container startup failed for image postgres:18`、`PSQLException: An error occurred while setting up the SSL connection` 加 `EOFException`\n- **根因**：y\n- **状态**：待落实（负责：one-quality 2026-10-24）\n\n"
            "### TST-004　四\n\n- **日期**：2026-10-16\n- **现象**：报错特征：`TooManyActualInvocations`\n- **状态**：已落实\n\n"
            "## 附录\n\n- **现象**：报错特征：`this belongs to the appendix, not to TST-004`\n")
        open(os.path.join(bank, "README.md"), "w").write("### ZZZ-001　不该读\n- **现象**：报错特征：`this must be ignored here`\n")
        entries, featureless = parse_bank(bank)
        assert sorted(entries) == ["TST-001", "TST-003", "TST-004"] and featureless == ["TST-002"], (sorted(entries), featureless)
        assert entries["TST-001"]["groups"] == [['Expecting empty but was: [["{"instanceId"'], ["ProcessArchiveJobIT.archivesThenDeletes"]], "括号里的说明与句号之后的记号不算；加 = 且"
        assert entries["TST-003"]["groups"] == [["Container startup failed for image postgres:18", "PSQLException: An error occurred while setting up the SSL connection"]], "EOFException（12 字符）太泛，且这一组随之丢掉"
        assert entries["TST-004"]["groups"] == [["TooManyActualInvocations"]], "附录标题结束上一条，附录里的字段不并进去"
        assert entries["TST-001"]["fix"] == "c4cced3c" and entries["TST-001"]["status"] == "已落实" and entries["TST-001"]["date"] == datetime.date(2026, 10, 16) and entries["TST-003"]["status"] == "待落实"
        for bad, why in (("### TST-009 半角空格\n", "半角空格"), ("### tst-009　小写\n", "小写前缀"), ("## TST-009　两个井号\n", "二级标题")):
            with tempfile.TemporaryDirectory() as b2:
                open(os.path.join(b2, "x.md"), "w", encoding="utf-8").write(bad)
                try:
                    parse_bank(b2)
                    raise AssertionError(why)
                except SystemExit as e:
                    assert "格式不对" in str(e), why
        try:
            parse_bank(os.path.join(bank, "no-such-dir"))
            raise AssertionError("missing dir")
        except SystemExit as e:
            assert "不存在" in str(e)
        noisy = '{"@timestamp":"x","message":"Container startup failed for image postgres:18 in a json log line"}'
        mock = "org.mockito.exceptions.verification.TooManyActualInvocations: \nWanted 1 time:"
        lines = relevant_lines("INFO fine\nexpected 1 but was 2\n2026-10-16T17:00:00.0Z [ERROR] Container startup failed for image postgres:18\n" + "backend verify\tUNKNOWN STEP\t2026-10-16T17:00:00.0Z " + noisy + "\n" + mock + "\nRunning com.x.TooManyActualInvocationsHelper\n")
        assert any("TooManyActualInvocations:" in l for l in lines) and not any(l.startswith("backend verify") for l in lines), lines
        assert classify(lines, entries) == {"TST-003": "Container startup failed for image postgres:18", "TST-004": "TooManyActualInvocations"}
        assert classify(["[ERROR] Expecting empty but was: [[\"{\"instanceId\":\"1\""], entries) == {}, "加 = 且：只有一组出现不算"
        assert classify(["[ERROR] Expecting empty but was: [[\"{\"instanceId\":\"1\"", "[ERROR] ProcessArchiveJobIT.archivesThenDeletes -- FAILURE"], entries) == {"TST-001": 'Expecting empty but was: [["{"instanceId"'}
        assert classify(["nothing here"], entries) == {}

        class FakeGh:
            logs = {(1, 1): "x [ERROR] Container startup failed for image postgres:18\n", (2, 1): "[ERROR] something never seen before\n", (3, 1): None,
                    (4, 1): '[ERROR] Expecting empty but was: [["{"instanceId":"01a1"\n[ERROR] ProcessArchiveJobIT.archivesThenDeletes FAILURE\n', (5, 1): "[ERROR] TooManyActualInvocations: x\n", (6, 1): "[ERROR] Expecting empty but was: [[\"{\"instanceId\"\n[ERROR] ProcessArchiveJobIT.archivesThenDeletes FAILURE\n",
                    (7, 1): "[ERROR] Expecting empty but was: [[\"{\"instanceId\"\n[ERROR] ProcessArchiveJobIT.archivesThenDeletes FAILURE\n"}
            fixes = {"old": False, "new": True, "unknown": None}

            def log(self, run_id, attempt, last):
                return self.logs[(run_id, attempt)]

            def has_commit(self, fix, head, created):
                return self.fixes[head]
        run = lambda i, **kw: dict({"databaseId": i, "headBranch": f"b{i}", "event": "pull_request", "createdAt": "2026-10-17T01:00:00Z", "headSha": "new", "attempt": 1, "conclusion": "failure"}, **kw)
        rows = report([run(1), run(2), run(3), run(4, headSha="new"), run(5, attempt=2, conclusion="success"), run(6, headSha="old"), run(7, headSha="unknown")], FakeGh(), entries)
        got = [(r["run"], r["attempt"], r["result"]) for r in rows]
        assert got == [(1, 1, "known"), (2, 1, "unregistered"), (3, 1, "unavailable"), (4, 1, "recurrence"), (5, 1, "recurrence"), (6, 1, "known"), (7, 1, "recurrence")], got   # run 5 ended green after a rerun: its first attempt is checked
        assert rows[3]["entries"]["TST-001"][0] == "recurrence" and rows[5]["entries"]["TST-001"][0] == "old base" and rows[6]["entries"]["TST-001"][0] == "recurrence?", "旧基线不算复发；判断不了仍算并注明"
        assert rows[4]["entries"]["TST-004"][0] == "recurrence" and rows[0]["entries"]["TST-003"][0] == "known", "待落实的条目命中是已知问题，不是复发"
        early = report([run(8, createdAt="2026-10-15T23:00:00Z", headSha="new")], type("G", (FakeGh,), {"logs": {(8, 1): FakeGh.logs[(4, 1)]}})(), entries)
        assert early[0]["entries"]["TST-001"][0] == "known", "早于条目日期的运行不算复发"
        assert attempts_of(run(9, attempt=3, conclusion="success")) == [(1, False), (2, False)] and attempts_of(run(9, attempt=3)) == [(1, False), (2, False), (3, True)] and attempts_of(run(9)) == [(1, True)]
        text = render(rows, featureless, capped=True)
        assert text.splitlines()[0].startswith("ci 失败的运行与尝试 7 次：复发 2 条") and "日志不可得 1 次" in text and "复发 TST-004：运行 5#1" in text and "没有报错特征、不在检测范围的条目 1 条：TST-002" in text and "列表到顶" in text and "旧基线" in text, text
    # the git and gh layers, against a real temporary repository and a fake `gh` on PATH
    global ROOT
    env = {**os.environ, "GIT_AUTHOR_NAME": "t", "GIT_AUTHOR_EMAIL": "t@t", "GIT_COMMITTER_NAME": "t", "GIT_COMMITTER_EMAIL": "t@t", "GIT_CONFIG_GLOBAL": "/dev/null", "GIT_CONFIG_SYSTEM": "/dev/null"}
    with tempfile.TemporaryDirectory() as repo:
        def g(*a):
            return subprocess.run(["git", *a], cwd=repo, capture_output=True, text=True, check=True, env=env).stdout.strip()
        g("init", "-q", "-b", "main")
        for name in ("base", "fix", "after"):
            open(os.path.join(repo, name), "w").write(name)
            g("add", "-A"); g("-c", "commit.gpgsign=false", "commit", "-q", "-m", name)
        fix, after = g("rev-parse", "HEAD~1"), g("rev-parse", "HEAD")
        base = g("rev-parse", "HEAD~2")
        old_root, ROOT = ROOT, repo
        try:
            assert Gh().has_commit(fix, after, "2026-10-17T00:00:00Z") is True and Gh().has_commit(fix, base, "2026-10-17T00:00:00Z") is False
            assert Gh().has_commit(fix, "0" * 40, "2026-10-17T00:00:00Z") is None and Gh().has_commit("f" * 40, after, "2026-10-17T00:00:00Z") is None, "an object that is not there is no answer, not 'old base'"
        finally:
            ROOT = old_root
    with tempfile.TemporaryDirectory() as d:
        rows = [{"databaseId": 1, "headBranch": "a", "event": "push", "createdAt": "x", "headSha": "s", "attempt": 1, "conclusion": "failure"}, {"databaseId": 2, "headBranch": "b", "event": "push", "createdAt": "x", "headSha": "s", "attempt": 1, "conclusion": "success"},
                {"databaseId": 3, "headBranch": "c", "event": "push", "createdAt": "x", "headSha": "s", "attempt": 2, "conclusion": "success"}]
        open(os.path.join(d, "gh"), "w").write("#!/usr/bin/env python3\nimport json, os, sys\nopen(os.environ['FAKE_LOG'], 'a').write(json.dumps(sys.argv[1:]) + '\\n')\n"
                                              f"if sys.argv[1:3] == ['run', 'list']:\n    print(json.dumps({json.dumps(rows)}))\nelif sys.argv[1:3] == ['run', 'view'] and '--attempt' in sys.argv:\n    print('attempt log')\nelif sys.argv[1:3] == ['run', 'view'] and 'FAKE_FAILURE' in os.environ:\n    print(os.environ['FAKE_FAILURE'])\nelse:\n    sys.exit(1)\n")
        os.chmod(os.path.join(d, "gh"), 0o755)
        old = {k: os.environ.get(k) for k in ("PATH", "FAKE_LOG")}
        os.environ["PATH"], os.environ["FAKE_LOG"] = d + os.pathsep + os.environ["PATH"], os.path.join(d, "log")
        try:
            got, capped = Gh().runs(26)
            assert [r["databaseId"] for r in got] == [1, 3] and capped is False, "failed runs and runs that needed a rerun"
            assert (Gh().log(7, 2, False) or "").strip() == "attempt log" and Gh().log(7, 2, True) is None, "the last attempt is asked without --attempt (the fake has no such answer)"
            calls = [json.loads(l) for l in open(os.path.join(d, "log")).read().splitlines()]
            assert calls[0][:8] == ["run", "list", "--workflow", "ci", "--status", "completed", "--created", calls[0][7]] and "--limit" in calls[0] and calls[0][calls[0].index("--limit") + 1] == str(LIMIT)
            open(os.path.join(d, "one-x.md"), "w", encoding="utf-8").write(
                "### TST-001　一\n- **现象**：报错特征：`TooManyActualInvocations`\n- **状态**：已落实\n"
                "### TST-002　二\n- **现象**：报错特征：`Container startup failed`\n- **状态**：待落实\n"
                "### TST-003　三\n- **现象**：报错特征：`AssertionFailedError`\n- **状态**：已落实\n- **新增检查项**：修复提交：`ffffffffffffffffffffffffffffffffffffffff`\n")
            for log, options, code, expected in (
                ("TooManyActualInvocations", ["--fail-on-recurrence"], 1, "复发 TST-001"),
                ("TooManyActualInvocations", [], 0, "复发 TST-001"),
                ("Container startup failed", ["--fail-on-recurrence"], 0, "已登记（待落实或早于登记）"),
                ("AssertionFailedError", ["--fail-on-recurrence"], 1, "复发（未能判断修复是否在 PR 头里）"),
                ("an unregistered failure", ["--fail-on-recurrence"], 0, "未登记"),
                ("TooManyActualInvocations", ["--fail-on-recurrence", "--json"], 1, '"result": "recurrence"'),
                ("", ["--fail-on-recurrence"], 0, "日志不可得 1 次"),
            ):
                result = subprocess.run([sys.executable, os.path.abspath(__file__), "--hours", "26", "--bank", d, *options],
                                        capture_output=True, text=True, timeout=30, env={**os.environ, **({"FAKE_FAILURE": "[ERROR] " + log} if log else {})})
                assert result.returncode == code and expected in result.stdout, (options, log, result.returncode, result.stdout, result.stderr)
            empty = os.path.join(d, "empty-bank")
            os.makedirs(empty)
            open(os.path.join(empty, "one-y.md"), "w", encoding="utf-8").write("### TST-009　无特征\n- **现象**：没写特征\n- **状态**：已落实\n")
            nothing = os.path.join(d, "no-entries")
            os.makedirs(nothing)
            open(os.path.join(nothing, "README.md"), "w", encoding="utf-8").write("# no book here\n")
            for bank_dir in (nothing, empty):
                result = subprocess.run([sys.executable, os.path.abspath(__file__), "--hours", "26", "--bank", bank_dir, "--fail-on-recurrence"], capture_output=True, text=True, timeout=30, env={**os.environ, "FAKE_FAILURE": "[ERROR] x"})
                assert result.returncode == 1 and "复发检测是空的" in result.stdout, (bank_dir, result.returncode, result.stdout, result.stderr)
            for options, code in ((["--fail-on-recurrence"], 1), ([], 0)):
                result = subprocess.run([sys.executable, os.path.abspath(__file__), "--hours", "26", "--bank", empty, *options], capture_output=True, text=True, timeout=30, env={**os.environ, "FAKE_FAILURE": "[ERROR] x"})
                assert result.returncode == code and (code == 0 or "复发检测是空的" in result.stdout), (options, result.returncode, result.stdout, result.stderr)
        finally:
            for k, v in old.items():
                os.environ.pop(k, None) if v is None else os.environ.__setitem__(k, v)
    assert isinstance(parse_bank(), tuple)
    print("ci_recurrence self-test: ok")


def main():
    args = sys.argv[1:]
    if args == ["--self-test"]:
        self_test()
        return
    hours, bank, as_json, fail_on_recurrence = 26, BANK, False, False
    while args:
        a = args.pop(0)
        if a == "--hours" and args and args[0].isdigit():
            hours = int(args.pop(0))
        elif a == "--bank" and args:
            bank = args.pop(0)
        elif a == "--json":
            as_json = True
        elif a == "--fail-on-recurrence":
            fail_on_recurrence = True
        else:
            sys.exit(__doc__)
    entries, featureless = parse_bank(bank)
    if fail_on_recurrence and not entries:
        print(f"复发检测是空的：错题本里没有任何带报错特征的条目（{len(featureless)} 条不在检测范围），不能当作没有复发。")
        return 1
    gh = Gh()
    runs, capped = gh.runs(hours)
    rows = report(runs, gh, entries)
    if as_json:
        print(json.dumps({"rows": rows, "featureless": featureless, "capped": capped}, ensure_ascii=False, default=str))
    else:
        print(render(rows, featureless, capped))
    return int(fail_on_recurrence and any(r["result"] == "recurrence" for r in rows))


if __name__ == "__main__":
    sys.exit(main())
