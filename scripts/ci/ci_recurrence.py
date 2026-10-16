#!/usr/bin/env python3
"""每晚复发检测（错题本机制，用户 2026-10-17 批准）：把最近 N 小时失败的 ci 运行，按错题本里已登记的「报错特征」归类。命中旧条目就是「复发」：该条的新增检查项没拦住，须修。

特征写在错题本条目的「现象」里：`报错特征：` 后面反引号包起来的字符串（可多个，命中任一个即算）。短于 12 个字符的记号太泛，不用。
条目里可另写 `修复提交：` 加反引号包起来的提交号：该运行的 head 不含这个提交（旧基线上的分支），命中不算复发，只注明「旧基线」，合 main 即自愈；取不到 head 或祖先关系时仍算复发并注明「未能判断」。
用法：ci_recurrence.py [--hours 26] [--bank 目录] [--json]  |  ci_recurrence.py --self-test
输出：每个失败运行一行（运行号、分支、事件、命中的条目与记号，或「未登记」），末尾汇总：复发 N 条（条目号）、未登记 M 次。退出码 0（只报告，由调度会话读后处理）。
日志取 `gh run view <id> --log-failed`，只留与失败有关的行再匹配；gh 或日志取不到的运行标「日志不可得」，不当作未登记。
"""
import datetime
import json
import os
import re
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
BANK = os.path.normpath(os.path.join(HERE, "..", "..", "docs", "modernization", "quality", "defect-bank"))
HEADING = re.compile(r"### ([A-Z0-9]+-\d{3})　(.+)")
MIN_TOKEN = 12
RELEVANT = re.compile(r"<<< (FAILURE|ERROR)|\[ERROR\]|Caused by|Exception|Error:|AssertionError|Expecting|expected|Container startup failed|forked VM|Timeout|timed out|##\[error\]|Process completed with exit code")
NOISE = re.compile(r'^(?:[^\t]*\t){0,2}\S*\s*\{"@timestamp"')   # the application's own JSON log lines: not what failed


def fixes(bank=BANK):
    """{条目号: 修复提交}：条目任一字段里「修复提交：`sha`」。"""
    out = {}
    for name in sorted(os.listdir(bank)) if os.path.isdir(bank) else []:
        if not name.endswith(".md") or name == "README.md":
            continue
        ident = None
        for line in open(os.path.join(bank, name), encoding="utf-8").read().split("\n"):
            h = HEADING.fullmatch(line)
            if h:
                ident = h.group(1)
            elif ident and (m := re.search(r"修复提交：`([0-9a-f]{7,40})`", line)):
                out[ident] = m.group(1)
    return out


def signatures(bank=BANK):
    """{条目号: [记号]}：每册里条目「现象」中「报错特征：」之后的反引号字符串（≥12 字符）。"""
    out = {}
    for name in sorted(os.listdir(bank)) if os.path.isdir(bank) else []:
        if not name.endswith(".md") or name == "README.md":
            continue
        ident, field, fields = None, None, {}
        for line in open(os.path.join(bank, name), encoding="utf-8").read().replace("\r\n", "\n").split("\n") + ["### X-000　end"]:
            h = HEADING.fullmatch(line)
            if h:
                if ident and "现象" in fields:
                    m = re.search(r"报错特征：(.*)", fields["现象"])
                    tokens = [t for t in re.findall(r"`([^`]+)`", m.group(1)) if len(t) >= MIN_TOKEN] if m else []
                    if tokens:
                        out[ident] = tokens
                ident, field, fields = h.group(1), None, {}
                continue
            f = re.fullmatch(r"- \*\*([^*]+)\*\*：(.*)", line)
            if ident and f:
                field = f.group(1)
                fields[field] = f.group(2)
            elif ident and field and re.fullmatch(r"\s{2,}\S.*", line):
                fields[field] += " " + line.strip()
    return out


def relevant_lines(log):
    return [l for l in log.splitlines() if RELEVANT.search(l) and not NOISE.match(l)]


def classify(lines, sigs):
    """{条目号: 命中的第一个记号}。"""
    hits = {}
    text = "\n".join(lines)
    for ident, tokens in sigs.items():
        for t in tokens:
            if t in text:
                hits[ident] = t
                break
    return hits


class Gh:
    def has_commit(self, fix, head):
        """True 当 head 含 fix，False 当不含，None 当取不到（对象不在本地）。"""
        if subprocess.run(["git", "cat-file", "-e", f"{head}^{{commit}}"], capture_output=True).returncode:
            subprocess.run(["git", "fetch", "-q", "origin", head], capture_output=True, timeout=120)
        if subprocess.run(["git", "cat-file", "-e", f"{head}^{{commit}}"], capture_output=True).returncode or subprocess.run(["git", "cat-file", "-e", f"{fix}^{{commit}}"], capture_output=True).returncode:
            return None
        return subprocess.run(["git", "merge-base", "--is-ancestor", fix, head], capture_output=True).returncode == 0

    def failed_runs(self, hours):
        since = (datetime.datetime.now(datetime.timezone.utc) - datetime.timedelta(hours=hours)).strftime("%Y-%m-%dT%H:%M:%SZ")
        r = subprocess.run(["gh", "run", "list", "--workflow", "ci", "--status", "failure", "--created", f">={since}", "--limit", "100", "--json", "databaseId,headBranch,event,createdAt,headSha"],
                           capture_output=True, text=True, timeout=120)
        if r.returncode:
            raise SystemExit(f"gh run list failed: {r.stderr.strip()[:200]}")
        return json.loads(r.stdout)

    def log(self, run_id):
        try:
            r = subprocess.run(["gh", "run", "view", str(run_id), "--log-failed"], capture_output=True, text=True, timeout=300)
        except subprocess.SubprocessError:
            return None
        return r.stdout if r.returncode == 0 and r.stdout.strip() else None


def report(runs, gh, sigs, fix=None):
    fix = fix or {}
    rows, recurred, unknown = [], {}, 0
    for run in runs:
        log = gh.log(run["databaseId"])
        if log is None:
            rows.append({"run": run["databaseId"], "branch": run["headBranch"], "event": run["event"], "result": "log unavailable"})
            continue
        hits, old = classify(relevant_lines(log), sigs), {}
        for ident in list(hits):
            if ident in fix and run.get("headSha") and gh.has_commit(fix[ident], run["headSha"]) is False:
                old[ident] = hits.pop(ident)   # the run's head lacks the fix: an old base, not a recurrence
        if hits:
            for ident in hits:
                recurred.setdefault(ident, []).append(run["databaseId"])
        elif not old:
            unknown += 1
        rows.append({"run": run["databaseId"], "branch": run["headBranch"], "event": run["event"], "result": "recurrence" if hits else ("old base" if old else "unregistered"), "entries": hits, "old": old})
    return rows, recurred, unknown


def render(rows, recurred, unknown):
    lines = [f"ci 失败 {len(rows)} 次：复发 {len(recurred)} 条，未登记 {unknown} 次"]
    for r in rows:
        tail = "、".join(f"{i}（{t[:40]}）" for i, t in r.get("entries", {}).items()) if r.get("entries") else ("未登记" if r["result"] == "unregistered" else "日志不可得")
        if r["result"] == "old base":
            tail = "旧基线（head 不含修复，合 main 自愈）：" + "、".join(r["old"])
        lines.append(f"- {r['run']} {r['branch']} {r['event']}：{('复发 ' + tail) if r['result'] == 'recurrence' else tail}")
    for ident, ids in sorted(recurred.items()):
        lines.append(f"复发 {ident}：运行 {', '.join(map(str, ids))}；这一条的新增检查项没拦住，须修")
    return "\n".join(lines)


def self_test():
    with tempfile.TemporaryDirectory() as bank:
        open(os.path.join(bank, "one-x.md"), "w", encoding="utf-8").write(
            "# 错题本\n\n前缀：`TST`\n\n### TST-001　一\n\n- **日期**：2026-10-16\n- **现象**：失败 5 次。报错特征：`Expecting empty but was: [[\"{\"instanceId\"` 加 `short`。\n- **根因**：x\n\n"
            "### TST-002　二\n\n- **现象**：没有特征词\n- **根因**：x\n\n"
            "### TST-003　三\n\n- **现象**：第一行\n  续行。报错特征：`Container startup failed for image postgres:18`、`PSQLException: An error occurred while setting up the SSL connection`\n- **根因**：y\n")
        open(os.path.join(bank, "README.md"), "w").write("### ZZZ-001　不该读\n- **现象**：报错特征：`this must be ignored here`\n")
        sigs = signatures(bank)
        assert sorted(sigs) == ["TST-001", "TST-003"], sigs
        assert sigs["TST-001"] == ['Expecting empty but was: [["{"instanceId"'], "short 记号（<12 字符）不用"
        assert len(sigs["TST-003"]) == 2
        noisy = '{"@timestamp":"x","message":"Container startup failed for image postgres:18 in a json log line"}'
        lines = relevant_lines("INFO fine\nexpected 1 but was 2\n2026-10-16T17:00:00.0Z [ERROR] Container startup failed for image postgres:18\n" + "backend verify\tUNKNOWN STEP\t2026-10-16T17:00:00.0Z " + noisy + "\n")
        assert len(lines) == 2 and not any(l.startswith("backend verify") for l in lines), lines
        assert classify(lines, sigs) == {"TST-003": "Container startup failed for image postgres:18"}
        assert classify(["nothing here"], sigs) == {}
        with open(os.path.join(bank, "one-x.md"), "a", encoding="utf-8") as f:
            f.write("\n### TST-004　四\n\n- **现象**：报错特征：`ProcessArchiveJobIT.archivesThenDeletes`\n- **新增检查项**：修复提交：`c4cced3c` 已合入\n")
        assert fixes(bank) == {"TST-004": "c4cced3c"}

        class FakeGh:
            logs = {1: "x [ERROR] Container startup failed for image postgres:18\n", 2: "[ERROR] something never seen before\n", 3: None,
                    4: '[ERROR] ProcessArchiveJobIT Expecting empty but was: [["{"instanceId":"01a1"\n[ERROR] Container startup failed for image postgres:18\n'}

            def log(self, run_id):
                return self.logs[run_id]

            def has_commit(self, fix, head):
                return {"old": False, "new": True, "unknown": None}[head]
        runs = [{"databaseId": i, "headBranch": f"b{i}", "event": "pull_request"} for i in (1, 2, 3, 4)]
        rows, recurred, unknown = report(runs, FakeGh(), sigs)
        assert recurred == {"TST-003": [1, 4], "TST-001": [4]} and unknown == 1
        assert [r["result"] for r in rows] == ["recurrence", "unregistered", "log unavailable", "recurrence"]
        text = render(rows, recurred, unknown)
        # a hit whose run head lacks the registered fix commit is an old base, not a recurrence; with the fix, or when that cannot be told, it is a recurrence
        sigs2 = dict(sigs, **{"TST-004": ["ProcessArchiveJobIT.archivesThenDeletes"]})
        FakeGh.logs.update({5: "[ERROR] ProcessArchiveJobIT.archivesThenDeletes -- FAILURE\n", 6: "[ERROR] ProcessArchiveJobIT.archivesThenDeletes -- FAILURE\n", 7: "[ERROR] ProcessArchiveJobIT.archivesThenDeletes -- FAILURE\n"})
        runs2 = [{"databaseId": 5, "headBranch": "o", "event": "pull_request", "headSha": "old"}, {"databaseId": 6, "headBranch": "n", "event": "pull_request", "headSha": "new"},
                 {"databaseId": 7, "headBranch": "u", "event": "pull_request", "headSha": "unknown"}]
        rows2, rec2, unk2 = report(runs2, FakeGh(), sigs2, {"TST-004": "c4cced3c"})
        assert [r["result"] for r in rows2] == ["old base", "recurrence", "recurrence"] and rec2 == {"TST-004": [6, 7]} and unk2 == 0, (rows2, rec2)
        assert "旧基线" in render(rows2, rec2, unk2)
        assert text.splitlines()[0] == "ci 失败 4 次：复发 2 条，未登记 1 次" and "日志不可得" in text and "复发 TST-003：运行 1, 4" in text and "未登记" in text, text
    real = signatures()
    assert isinstance(real, dict)
    print("ci_recurrence self-test: ok")


def main():
    args = sys.argv[1:]
    if args == ["--self-test"]:
        self_test()
        return
    hours, bank, as_json = 26, BANK, False
    while args:
        a = args.pop(0)
        if a == "--hours" and args and args[0].isdigit():
            hours = int(args.pop(0))
        elif a == "--bank" and args:
            bank = args.pop(0)
        elif a == "--json":
            as_json = True
        else:
            sys.exit(__doc__)
    sigs = signatures(bank)
    rows, recurred, unknown = report(Gh().failed_runs(hours), Gh(), sigs, fixes(bank))
    print(json.dumps({"rows": rows, "recurred": recurred, "unregistered": unknown}, ensure_ascii=False) if as_json else render(rows, recurred, unknown))


main()
