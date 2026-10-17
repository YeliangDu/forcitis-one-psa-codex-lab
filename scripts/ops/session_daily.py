#!/usr/bin/env python3
"""Daily session report data: one JSON object per one-* session for a time window, from git, gh and the merge queue log (the coordinator adds grade / assessment / action and publishes).

Per session: commits and their themes, pull requests opened and merged, queue returns (CONFLICT, HOLD from the timestamped queue log), CI failures, the review verdict written in the pull request body,
commits pushed after the pull request was queued, defect-bank entries dated inside the window, and loop candidates (the same kind of commit or queue event repeating).
Attribution is by branch name: a session owns the branches `claude/<prefix>*` listed in SESSIONS. Anything else is counted in the notes, not in a session.
Usage: session_daily.py [--date YYYY-MM-DD | --start ISO --end ISO] [--out FILE] [--log merge.log] [--tz Europe/Berlin]   |   session_daily.py --self-test
Default window: 08:00 the day before --date (default today) to 09:00 on --date, local time (--tz, default Europe/Berlin). Run `git fetch origin` first: the data comes from the local git refs.
No queue data for the window (log missing or starting later): returns and pushed_after_queue are null, not 0."""
import collections
import datetime
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
import zoneinfo

TZ = zoneinfo.ZoneInfo("Europe/Berlin")   # --tz overrides: the default window is 08:00..09:00 local time, +02:00 in summer, +01:00 in winter
DEFECT_BANK = "docs/modernization/quality/defect-bank"
MAIN = "origin/main"
# (name, role, branch prefixes under claude/); the first session whose prefix matches owns a branch, so list the specific prefixes first
SESSIONS = [
    ("one-coordinator", "总调度：派工、批准入队、转达决定", ["one-coordinator", "one-1a-next-5-train", "one-merge-train", "one-quality-merge-train"]),
    ("one-merge", "合并执行：组合集、运行队列", ["one-merge", "xin-c2cfec"]),
    ("one-quality", "质量工具：CI、守护、合并队列脚本", ["one-quality", "xin-61164f"]),
    ("one-ops", "运维：部署、取证、云资源", ["one-ops"]),
    ("one-1a-par", "身份与运维命令实现", ["one-1a-par", "elated-mclaren-be9a20"]),
    ("one-1a-next-5", "数据修复批实现", ["one-1a-next-5", "lucid-almeida-1748de"]),
    ("one-data", "数据设计门禁与审查", ["one-data", "magical-franklin-00c1c4"]),
    ("one-rules", "规则卡与决策页", ["one-rules", "xin-c15a36"]),
    ("one-glossary", "用语与命名", ["one-glossary", "modest-torvalds-293503"]),
    ("one-architecture", "架构收尾主笔", ["one-architecture", "xin-4e9c83"]),
    ("one-arch-revenue", "收入组流程设计", ["one-arch-revenue", "practical-hodgkin-3f1b8a"]),
    ("one-arch-finance", "财务组流程设计", ["one-arch-finance", "objective-hertz-0e3c0a"]),
    ("one-arch-people", "人员组流程设计", ["one-arch-people", "zealous-shaw-1e4eba"]),
]
REVIEW_WORDS = re.compile(r"opus|复审|终审|ultrareview", re.IGNORECASE)
FIXED_THEN_MERGE = re.compile("修后可合并|修正后可合并|修后合并")
NO_BLOCKER = re.compile(r"无阻塞|没有阻塞|无阻断|阻塞项?[^，。；;\n]{0,40}已(?:修|采纳|清|解决)|已修[^，。；;\n]{0,10}阻塞")
BLOCKED = re.compile(r"有阻塞|阻塞|阻断|不可合并|不能合并|未通过|不通过")
MERGEABLE = re.compile(r"可合并|已通过|复审通过|结论[：:]\s*通过|无阻塞|没有阻塞")


def line_verdict(line):
    if FIXED_THEN_MERGE.search(line):
        return "修后可合并"
    cleaned = NO_BLOCKER.sub("", line) if NO_BLOCKER.search(line) else line
    if BLOCKED.search(cleaned):
        return "有阻塞"
    if MERGEABLE.search(line):
        return "可合并"
    return None
QUEUE_LINE = re.compile(r"^(\S+)\s+([A-Z][A-Z-]*)\s+pr=#(\d+)\s*(.*)$")
COMMENT = re.compile(r"<!--.*?-->", re.DOTALL)
CODEX_SECTION = re.compile(r"^##\s*Codex\s*实现[^\n]*\n(.*?)(?=^##\s|\Z)", re.MULTILINE | re.DOTALL | re.IGNORECASE)
CODEX_INLINE = re.compile(r"Codex\s*(?:起草|实现)(?:\s*/\s*(?:起草|实现))?\s*[（(][^）)\n]+[）)]", re.IGNORECASE)
REWORK_COMMIT = re.compile(r"返工|复审修正|复审意见|按.{0,12}复[审核](?:修正|意见|修)|address(?:ed)? review|review fix", re.IGNORECASE)
CODEX_LOOKBACK = datetime.timedelta(days=7)   # a pull request opened up to a week ago can still be reworked inside the window


def is_codex_pr(body):
    """The pull request template's section `## Codex 实现（模型、强度）` is filled with something other than 不涉及, or the body names it inline as `Codex 实现（模型，强度）`; the template's own heading and comment do not count."""
    text = COMMENT.sub("", body or "")
    section = CODEX_SECTION.search(text)
    if section:
        content = section.group(1).strip()
        return bool(content) and not content.startswith("不涉及") and "codex" in content.lower()
    return bool(CODEX_INLINE.search(text))
SYNC_COMMIT = re.compile(r"^Merge (branch|remote-tracking branch) '(origin/)?main'")
LOOP_MIN = 4


def session_of(branch):
    """The session that owns `claude/...` branch, or None."""
    name = branch.removeprefix("origin/")
    if not name.startswith("claude/"):
        return None
    tail = name[len("claude/"):]
    for session, _, prefixes in SESSIONS:
        if any(tail == p or tail.startswith(p + "-") for p in prefixes):
            return session
    return None


def session_refs(root):
    """{session: [remote refs]} by session_of, and the refs no session owns."""
    refs = git(root, "for-each-ref", "--format=%(refname:short)", "refs/remotes/origin/claude").split()
    owned, free = {name: [] for name, _, _ in SESSIONS}, []
    for ref in refs:
        (owned[session_of(ref)] if session_of(ref) else free).append(ref)
    return owned, free


def git(root, *args):
    result = subprocess.run(["git", "-C", root, "-c", "core.quotePath=false", *args], capture_output=True, text=True)
    return result.stdout if result.returncode == 0 else ""


class Gh:
    """The few gh calls the report needs, each returning plain data (replaced by a fake in the self-test)."""

    def __init__(self, root):
        self.root = root

    def _json(self, *args):
        result = subprocess.run(["gh", *args], cwd=self.root, capture_output=True, text=True)
        if result.returncode != 0:
            raise RuntimeError(f"gh {' '.join(args[:3])} failed: {result.stderr.strip()[:200]}")
        return json.loads(result.stdout or "null")

    def prs(self):
        return self._json("pr", "list", "--state", "all", "--limit", "1000", "--json", "number,title,headRefName,createdAt,mergedAt,body,mergeCommit")

    def runs(self):
        return self._json("run", "list", "--workflow", "ci", "--limit", "1000", "--json", "databaseId,conclusion,createdAt,headBranch,event")

    def pr_commits(self, number):
        data = self._json("api", f"repos/{{owner}}/{{repo}}/pulls/{number}/commits", "--paginate")
        return [(c["commit"]["committer"]["date"], c["commit"]["message"].splitlines()[0]) for c in data or []]


def when(text):
    return datetime.datetime.fromisoformat(text.replace("Z", "+00:00")).astimezone(TZ)


def aware(text):
    moment = datetime.datetime.fromisoformat(text.replace("Z", "+00:00"))
    if moment.tzinfo is None:
        raise ValueError(f"{text!r} has no UTC offset (write e.g. 2026-10-16T08:00+02:00)")
    return moment.astimezone(TZ)


def window_of(date, start, end):
    if start or end:
        if not (start and end) or date:
            raise ValueError("give --start and --end together, and not with --date")
        window = aware(start), aware(end)
        if window[0] >= window[1]:
            raise ValueError("--start must be before --end")
        return window
    day = datetime.date.fromisoformat(date) if date else datetime.datetime.now(TZ).date()
    return (datetime.datetime.combine(day - datetime.timedelta(days=1), datetime.time(8), TZ), datetime.datetime.combine(day, datetime.time(9), TZ))


def in_window(text, window):
    return window[0] <= when(text) < window[1]


def commit_rows(root, refs, window):
    """[(sha, committed, subject)] without merge commits, committed inside the window, over the given revisions."""
    out = git(root, "log", "--first-parent", "--no-merges", f"--since={window[0].isoformat()}", f"--until={window[1].isoformat()}", "--format=%H%x1f%cI%x1f%s", *refs)
    rows = [line.split("\x1f") for line in out.splitlines() if line]
    return [(sha, committed, subject) for sha, committed, subject in rows if in_window(committed, window)]


def theme_of(subject):
    match = re.match(r"^(\w+)\(([^)]+)\)", subject) or re.match(r"^(\w+):", subject)
    return match.group(2) if match and match.lastindex == 2 else (match.group(1) if match else subject.split("：")[0][:20])


def shape_of(subject):
    """The part of a commit subject that repeats when the same job is done again and again."""
    head = re.split(r"[:：]", subject, maxsplit=1)[0]
    return re.sub(r"[\d#]+|['\"「」]", "", head).strip().lower()[:50]


def themes_of(subjects):
    groups = collections.OrderedDict()
    for subject in subjects:
        groups.setdefault(theme_of(subject), []).append(subject)
    ranked = sorted(groups.items(), key=lambda kv: -len(kv[1]))[:6]
    return [f"{key}（{len(items)}）：{items[0][:70]}" for key, items in ranked]


def queue_log(path):
    """[(time, event, pr, detail)] of every parseable line of the timestamped queue log, oldest first; a missing or unreadable log gives []."""
    if not path or not os.path.exists(path):
        return []
    rows = []
    for line in open(path, encoding="utf-8", errors="replace"):
        match = QUEUE_LINE.match(line.strip())
        if match:
            try:
                rows.append((when(match.group(1)), match.group(2), int(match.group(3)), match.group(4)))
            except ValueError:
                continue
    return sorted(rows, key=lambda r: r[0])


def review_of(body):
    """The verdict of the review lines of a pull request body: any "fixed, then mergeable" line first, then a blocker, then mergeable; review lines without a recognised verdict say so."""
    verdicts, seen = [], False
    for line in (body or "").splitlines():
        if REVIEW_WORDS.search(line):
            seen = True
            verdict = line_verdict(line)
            if verdict:
                verdicts.append(verdict)
    for label in ("修后可合并", "有阻塞", "可合并"):
        if label in verdicts:
            return label
    return "有复审（结论未识别）" if seen else None


def book_texts(root, name, refs):
    """The defect book of a session as on main and as on the latest commit that touched it on the session's own branches (work not merged yet); entries are `###` sections with a date line or table rows with a date cell."""
    path = f"{DEFECT_BANK}/{name}.md"
    texts = [git(root, "show", f"{MAIN}:{path}")]
    latest = git(root, "log", "-1", "--format=%H", *refs, "--", path).strip() if refs else ""
    if latest:
        texts.append(git(root, "show", f"{latest}:{path}"))
    return texts


def defect_entries(root, name, refs, window):
    """(entries dated inside the window as 'id title', whether the book holds no entry at all); a missing book counts as empty."""
    new, headings = [], set()
    for text in book_texts(root, name, refs):
        for heading, section in re.findall(r"^###\s+(.+?)\s*\n(.*?)(?=^###\s|\Z)", text, re.MULTILINE | re.DOTALL):
            heading = " ".join(heading.split())[:90]
            if heading in headings:
                continue
            headings.add(heading)
            date = re.search(r"日期\*{0,2}[：:]\s*\*{0,2}(\d{4}-\d{2}-\d{2})", section)
            if date and datetime.date.fromisoformat(date.group(1)) >= window[0].date():
                new.append(heading)
        for line in text.splitlines():
            cells = [c.strip() for c in line.strip().strip("|").split("|")] if line.strip().startswith("|") else []
            row_date = next((re.fullmatch(r"\d{4}-\d{2}-\d{2}", c) for c in cells if re.fullmatch(r"\d{4}-\d{2}-\d{2}", c)), None)
            if not row_date:
                continue
            label = " ".join(" / ".join(c for c in cells[:3] if c).split())[:90]
            if label in headings:
                continue
            headings.add(label)
            if datetime.date.fromisoformat(row_date.group(0)) >= window[0].date():
                new.append(label)
    return new, not headings


def loop_candidates(subjects, queue_counts):
    found = []
    shapes = collections.Counter(shape_of(s) for s in subjects)
    for shape, count in shapes.most_common(3):
        if shape and count >= LOOP_MIN:
            found.append({"repeated": f"提交主题「{shape}」窗口内重复 {count} 次", "automation": ""})
    for event, count in sorted(queue_counts.items(), key=lambda kv: -kv[1]):
        if count >= LOOP_MIN - 1 and event in ("HOLD", "CONFLICT", "RERUN-FAILED", "BEHIND-AFTER-FAIL"):
            found.append({"repeated": f"队列事件 {event} 在其 PR 上共 {count} 次", "automation": ""})
    return found


TASK_TABLE = "docs/modernization/tasks/TASKS.md"


def task_counts(root, window):
    """总表是统计来源；缺失、过期与未知轮次保留缺口，按登记变更时刻落日报窗口。"""
    names = {name for name, _, _ in SESSIONS}
    codex, rework = dict.fromkeys(names, 0), dict.fromkeys(names, 0)
    notes = []
    try:
        with open(os.path.join(root, TASK_TABLE), encoding="utf-8") as stream:
            table = stream.read()
        data = json.loads(table.split("<!-- TASKS-DATA v1 -->\n```json\n", 1)[1].split("\n```", 1)[0])
        if data["version"] != 1 or not isinstance(data["tasks"], list):
            raise ValueError("总表机器数据格式不支持")
        folder = os.path.dirname(os.path.join(root, TASK_TABLE))
        actual = {name for name in os.listdir(folder) if name.endswith(".md") and name != "TASKS.md" and not name.startswith("_")}
        if actual != set(data["片段摘要"]):
            raise ValueError("总表与片段清单不一致")
        for filename, digest in data["片段摘要"].items():
            with open(os.path.join(folder, filename), "rb") as stream:
                if hashlib.sha256(stream.read()).hexdigest() != digest:
                    raise ValueError("总表已过期：" + filename)
        for task in data["tasks"]:
            name = task["会话"]
            if name not in names:
                notes.append(f"任务 {task['任务号']} 的会话 {name} 不在日报会话表，未归属。")
                continue
            if not task["记录"] or task.get("未提交", False):
                codex[name] = rework[name] = None
                notes.append(f"任务 {task['任务号']} 未提交或尚无已提交的登记时间证据，该会话 Codex 两列为 null。")
            for event in task["记录"]:
                if not in_window(event["时间"], window):
                    continue
                owner = event["会话"]
                if owner not in names:
                    notes.append(f"任务 {task['任务号']} 的历史会话 {owner} 未归属。")
                    continue
                executor = event["执行者与模型"]
                if executor == "待核":
                    codex[owner] = rework[owner] = None
                    notes.append(f"任务 {task['任务号']} 的执行者待核，该会话 Codex 两列为 null。")
                    continue
                if not re.search(r"\bCodex\b", executor, re.IGNORECASE):
                    continue
                if event["新增"] and codex[owner] is not None:
                    codex[owner] += 1
                delta = event["返工增量"]
                if delta is None:
                    rework[owner] = None
                    notes.append(f"任务 {task['任务号']} 的返工基数或时间待核，codex_rework_rounds 为 null。")
                elif type(delta) is not int or delta < 0:
                    raise ValueError("总表返工增量须为非负整数或 null")
                elif rework[owner] is not None:
                    rework[owner] += delta
    except (OSError, ValueError, KeyError, IndexError, TypeError) as error:
        return dict.fromkeys(names), dict.fromkeys(names), [f"Codex 两列为 null：任务总表不可用（{error}）；请运行 python3 docs/modernization/build_all.py。"]
    notes.append("codex_cards 读 tasks/TASKS.md：窗口内首次提交登记的 Codex 卡数；codex_rework_rounds 读同表登记的累计轮次变更之差。窗口为左闭右开，不依赖 PR 正文与提交标题。历史回填的首次累计轮次无法分配日期时为 null；登记时间不是原派工时间，待核不按零计。")
    return codex, rework, notes


def build(root, gh, window, log_path):
    if not git(root, "rev-parse", "--verify", "-q", MAIN).strip():
        raise RuntimeError(f"{MAIN} not found: fetch first (git fetch origin)")
    fetched = git(root, "log", "-1", "--format=%cI", MAIN).strip()
    prs_list = gh.prs()
    prs = {p["number"]: p for p in prs_list}
    owner = {n: session_of(p["headRefName"]) for n, p in prs.items()}
    log_rows = queue_log(log_path)
    covered_from = log_rows[0][0] if log_rows else None
    log_complete = covered_from is not None and covered_from <= window[0] + datetime.timedelta(minutes=5)
    events = [r for r in log_rows if window[0] <= r[0] < window[1]]
    queued_at = {}
    for time, event, number, _ in log_rows:
        if event == "LABEL-QUEUED" and time < window[1]:
            queued_at.setdefault(number, time)
    owned_refs, free_refs = session_refs(root)
    seen, per = set(), {name: {"subjects": []} for name, _, _ in SESSIONS}
    for name, _, _ in SESSIONS:
        for sha, _, subject in (commit_rows(root, owned_refs[name], window) if owned_refs[name] else []):
            if sha not in seen:
                seen.add(sha)
                per[name]["subjects"].append(subject)
    free_commits = collections.Counter()
    for ref in free_refs:
        for sha, _, _ in commit_rows(root, [ref], window):
            if sha not in seen:
                seen.add(sha)
                free_commits[ref.removeprefix("origin/")] += 1
    merged_here = [p for p in prs.values() if p.get("mergedAt") and in_window(p["mergedAt"], window) and (p.get("mergeCommit") or {}).get("oid")]
    unattributed, no_second_parent = 0, []
    for p in merged_here:
        name = owner[p["number"]]
        oid = p["mergeCommit"]["oid"]
        if not git(root, "rev-parse", "--verify", "-q", f"{oid}^2").strip():
            no_second_parent.append(p["number"])
            continue
        for sha, _, subject in commit_rows(root, [f"{oid}^1..{oid}^2"], window):
            if sha in seen:
                continue
            seen.add(sha)
            if name:
                per[name]["subjects"].append(subject)
            else:
                unattributed += 1
    ci, runs = collections.Counter(), gh.runs()
    for run in runs:
        if run.get("conclusion") == "failure" and in_window(run["createdAt"], window):
            ci[session_of(run["headBranch"] if run["headBranch"].startswith("claude/") else "claude/" + run["headBranch"]) or "-"] += 1
    runs_from = min((when(r["createdAt"]) for r in runs), default=None)
    returns = collections.defaultdict(lambda: collections.Counter())
    for _, event, number, _ in events:
        returns[owner.get(number)][event] += 1
    pushed, unreadable = collections.Counter(), []
    for number, queued in queued_at.items():
        if owner.get(number):
            p = prs[number]
            if (p.get("mergedAt") and when(p["mergedAt"]) < window[0]) or when(p["createdAt"]) >= window[1]:
                continue
            try:
                commits = gh.pr_commits(number)
            except RuntimeError:
                unreadable.append(number)
                continue
            pushed[owner[number]] += sum(1 for date, headline in commits if when(date) > queued and window[0] <= when(date) < window[1] and not SYNC_COMMIT.match(headline))
    codex, rework, task_notes = task_counts(root, window)
    sessions = []
    for name, role, _ in SESSIONS:
        opened = sorted((p for p in prs.values() if owner[p["number"]] == name and in_window(p["createdAt"], window)), key=lambda p: p["number"])
        merged = sorted(p["number"] for p in prs.values() if owner[p["number"]] == name and p.get("mergedAt") and in_window(p["mergedAt"], window))
        reviews = [f"#{p['number']}:{v}" for p in opened for v in [review_of(p.get("body"))] if v]
        new_entries, empty = defect_entries(root, name, owned_refs[name], window)
        subjects = per[name]["subjects"]
        counts = returns.get(name, collections.Counter())
        sessions.append({
            "name": name, "commits": len(subjects), "themes": themes_of(subjects),
            "prs_opened": [f"#{p['number']} {p['title'][:60]}" for p in opened], "prs_merged": merged,
            "prs_opened_count": len(opened), "prs_merged_count": len(merged),
            "returns": {"conflict": counts["CONFLICT"] if log_complete else None, "hold": counts["HOLD"] if log_complete else None},
            "ci_failures": ci[name], "reviews": reviews, "pushed_after_queue": pushed[name] if log_complete else None,
            "codex_cards": codex[name], "codex_rework_rounds": rework[name],
            "defect_bank_new": new_entries, "defect_bank_empty": empty,
            "loop_candidates": loop_candidates(subjects, counts if log_complete else {}), "role": role})
    notes = [f"窗口 {window[0].isoformat(timespec='minutes')} ~ {window[1].isoformat(timespec='minutes')}（本机时区 {window[0].tzname()}）。提交按分支名归属会话（SESSIONS 的前缀表，整名或「前缀-」开头），去重；沿分支 first-parent 的非合并提交，committer 时间在窗口内（合入 main 的提交不重复算）；已删除分支的提交取窗口内合入 PR 的第二父链补算。数据取自本机 git 引用（origin/main 最新提交 {fetched or '未知'}），脚本不 fetch，出数前请先 git fetch origin。"]
    if log_complete:
        notes.append(f"队列返回次数读带时间戳的队列日志（CONFLICT、HOLD，日志自 {covered_from.isoformat(timespec='minutes')} 起），按 PR 的分支归属；pushed_after_queue = 该 PR 首次 LABEL-QUEUED 之后、窗口内、非「合并 main」的提交数（提交时间，不是推送时间；返工也计入）。")
    else:
        since = f"日志自 {covered_from.isoformat(timespec='minutes')} 起，晚于窗口开始" if covered_from else "队列日志缺失或无可解析的行"
        notes.append(f"returns 与 pushed_after_queue 为 null（无数据，不是 0）：{since}，这两项无法统计；用 --log 指定覆盖整个窗口的日志。")
    if unreadable:
        notes.append(f"这些 PR 的提交列表读不到，pushed_after_queue 少算（不影响已改读任务总表的 codex_rework_rounds）：{', '.join('#' + str(n) for n in sorted(set(unreadable)))}。")
    if free_commits:
        notes.append("窗口内有提交但分支名不在 SESSIONS 前缀表的分支（未计入任何会话，需要时补表）：" + "、".join(f"{b} {n} 个" for b, n in free_commits.most_common(10)) + "。")
    unmatched_prs = sorted(n for n, p in prs.items() if not owner[n] and (in_window(p["createdAt"], window) or (p.get("mergedAt") and in_window(p["mergedAt"], window))))
    if unmatched_prs or unattributed or ci.get("-", 0):
        notes.append(f"未归属会话：PR {', '.join('#' + str(n) for n in unmatched_prs) or '无'}（窗口内开启或合入）；合入 PR 第二父链上的提交 {unattributed} 个；CI 失败 {ci.get('-', 0)} 次（含 main 的 push 运行与未入表分支）。")
    if no_second_parent:
        notes.append(f"这些 PR 是 squash 或 rebase 合入、没有第二父提交，已删分支的提交无法补算：{', '.join('#' + str(n) for n in no_second_parent)}。")
    if len(prs_list) >= 1000 or (runs_from and runs_from > window[0] - datetime.timedelta(hours=2)) or len(runs) >= 1000:
        notes.append(f"gh 返回已达上限（PR {len(prs_list)} 条、CI 运行 {len(runs)} 条，运行最早 {runs_from.isoformat(timespec='minutes') if runs_from else '无'}）：窗口前段的 PR 或 CI 失败可能被截掉，ci_failures 与 PR 数是下限。")
    notes.append("reviews 只看窗口内新开的 PR 正文里含 opus/复审/终审/ultrareview 的行，按「修后可合并 > 有阻塞 > 可合并」归类，「无阻塞」「阻塞项已修」不算阻塞，未识别写「有复审（结论未识别）」，无复审行不列。ci_failures 只数 conclusion=failure，按分支名归属。")
    notes.append("defect_bank_new 取各会话错题本（origin/main 版，及该会话分支上最近改过它的版本）里日期不早于窗口开始日的条目（`###` 小节的日期行或表格行的日期列）；册不存在或无条目时 defect_bank_empty 为 true；补跑过去日期时窗口之后的条目也会计入。")
    notes.extend(task_notes)
    notes.append("loop_candidates 是机械统计（同类提交主题重复≥4 次、队列事件重复），automation 留空由调度填。")
    return {"date": window[1].date().isoformat(), "window": f"{window[0].isoformat(timespec='minutes')} ~ {window[1].isoformat(timespec='minutes')}",
            "sessions": sessions, "notes": notes, "title": window[1].date().isoformat()}


def toplevel():
    return subprocess.run(["git", "rev-parse", "--show-toplevel"], capture_output=True, text=True, check=True).stdout.strip()


def default_log(root):
    common = git(root, "rev-parse", "--path-format=absolute", "--git-common-dir").strip()
    return os.path.join(common, "merge-queue", "merge.log")


class FakeGh:
    def __init__(self, prs, runs, commits):
        self._prs, self._runs, self._commits = prs, runs, commits

    def prs(self):
        return self._prs

    def runs(self):
        return self._runs

    def pr_commits(self, number):
        return self._commits.get(number, [])


def self_test():
    with tempfile.TemporaryDirectory() as tmp:
        repo = os.path.join(tmp, "repo")
        os.makedirs(repo)
        env = dict(os.environ, GIT_AUTHOR_NAME="t", GIT_AUTHOR_EMAIL="t@example.invalid", GIT_COMMITTER_NAME="t", GIT_COMMITTER_EMAIL="t@example.invalid")

        def sh(*args, at=None, check=True):
            e = dict(env, GIT_COMMITTER_DATE=at, GIT_AUTHOR_DATE=at) if at else env
            r = subprocess.run(["git", "-C", repo, "-c", "commit.gpgsign=false", *args], capture_output=True, text=True, env=e)
            assert r.returncode == 0 or not check, r.stderr
            return r.stdout.strip()

        def commit(message, at, name="f.txt"):
            with open(os.path.join(repo, name), "a") as f:
                f.write(message + "\n")
            sh("add", "-A")
            sh("commit", "-q", "-m", message, at=at)
            return sh("rev-parse", "HEAD")
        sh("init", "-q", "-b", "main")
        base = commit("base", "2026-10-15T10:00:00+02:00")
        sh("update-ref", "refs/remotes/origin/main", base)
        inside, before, after = "2026-10-16T12:00:00+02:00", "2026-10-16T07:00:00+02:00", "2026-10-17T09:30:00+02:00"
        # one-quality: three in the window, one before it, one after; one commit on a branch of another session
        sh("checkout", "-q", "-b", "q", base)
        commit("fix(ci): a 1", before)
        for i in range(4):
            commit(f"fix(ci): repeated fix {i}", inside)
        commit("docs: late", after)
        sh("update-ref", "refs/remotes/origin/claude/one-quality-x", sh("rev-parse", "HEAD"))
        sh("checkout", "-q", "-b", "o", base)
        commit("feat(ops): ops work", inside, "o.txt")
        sh("update-ref", "refs/remotes/origin/claude/one-ops-y", sh("rev-parse", "HEAD"))
        sh("update-ref", "refs/remotes/origin/claude/one-rules-shared", sh("rev-parse", "HEAD"))  # the same commit on a second session's branch
        # the quality branch merges the ops branch: the ops commit must stay with ops
        sh("checkout", "-q", "q")
        sh("merge", "-q", "--no-ff", "o", "-m", "Merge o into q", at=inside)
        sh("update-ref", "refs/remotes/origin/claude/one-quality-x", sh("rev-parse", "HEAD"))
        # a book that only exists on a session branch
        sh("checkout", "-q", "-b", "qbook", base)
        os.makedirs(os.path.join(repo, DEFECT_BANK))
        with open(os.path.join(repo, DEFECT_BANK, "one-quality.md"), "w") as f:
            f.write("### QUAL-900　on a branch\n\n- **日期**：2026-10-16\n")
        sh("add", "-A")
        sh("commit", "-q", "-m", "docs: book on branch", at=inside)
        sh("update-ref", "refs/remotes/origin/claude/one-quality-book", sh("rev-parse", "HEAD"))
        sh("checkout", "-q", "-b", "edge", base)
        commit("docs: exactly at the start", "2026-10-16T08:00:00+02:00", "e1.txt")
        commit("docs: exactly at the end", "2026-10-17T09:00:00+02:00", "e2.txt")
        sh("update-ref", "refs/remotes/origin/claude/one-glossary-edge", sh("rev-parse", "HEAD"))
        sh("checkout", "-q", "-b", "mystery", base)
        commit("feat(x): unowned", inside, "m.txt")
        sh("update-ref", "refs/remotes/origin/claude/mystery-z", sh("rev-parse", "HEAD"))
        # a merged pull request whose branch is gone: its commit is found through the merge commit's second parent
        sh("checkout", "-q", "-b", "gone", base)
        commit("feat(data): gone branch work", inside, "g.txt")
        sh("checkout", "-q", "-b", "other", base)
        commit("feat(other): other branch work", inside, "x.txt")
        sh("checkout", "-q", "gone")
        sh("merge", "-q", "--no-ff", "other", "-m", "Merge other into gone", at=inside)
        gone = sh("rev-parse", "HEAD")
        sh("checkout", "-q", "main")
        sh("merge", "-q", "--no-ff", gone, "-m", "Merge pull request #7", at="2026-10-16T13:00:00+02:00")
        merge_oid = sh("rev-parse", "HEAD")
        # one-data's defect book on origin/main
        sh("checkout", "-q", "-b", "book", base)
        os.makedirs(os.path.join(repo, DEFECT_BANK))
        with open(os.path.join(repo, DEFECT_BANK, "one-data.md"), "w") as f:
            f.write("# book\n\n### DATA-001　old\n\n- **日期**：2026-10-01\n\n### DATA-002　new one\n\n- **日期**：2026-10-16\n- **类别**：其他\n")
        with open(os.path.join(repo, DEFECT_BANK, "one-rules.md"), "w") as f:
            f.write("| 编号 | 日期 | 现象 |\n|---|---|---|\n| RB-01 | 2026-10-16 | new row |\n| RB-02 | 2026-10-01 | old row |\n")
        sh("add", "-A")
        sh("commit", "-q", "-m", "book", at=before)
        sh("update-ref", "refs/remotes/origin/main", sh("rev-parse", "HEAD"))
        log = os.path.join(tmp, "merge.log")
        with open(log, "w") as f:
            f.write("2026-10-16T08:01:00+02:00 START pr=#4 run=- elapsed=0s lane=code\n"
                    "2026-10-16T10:00:00+02:00 LABEL-QUEUED pr=#5 run=- elapsed=0s\n"
                    "2026-10-16T11:00:00+02:00 CONFLICT pr=#5 run=- elapsed=3s back to the author\n"
                    "2026-10-16T10:30:00+02:00 LABEL-QUEUED pr=#15 run=- elapsed=0s\n"
                    "2026-10-16T11:30:00+02:00 HOLD pr=#6 run=1 elapsed=3s checks red\n"
                    "2026-10-15T11:30:00+02:00 HOLD pr=#6 run=1 elapsed=3s yesterday\n"
                    "garbage line\n")
        prs = [{"number": 5, "title": "quality tool", "headRefName": "claude/one-quality-x", "createdAt": "2026-10-16T09:00:00+02:00", "mergedAt": None,
                "body": "## 评审\nopus 首轮复审：修后可合并\n", "mergeCommit": None},
               {"number": 6, "title": "ops", "headRefName": "claude/one-ops-y", "createdAt": "2026-10-15T09:00:00+02:00", "mergedAt": None, "body": "no review here", "mergeCommit": None},
               {"number": 7, "title": "gone", "headRefName": "claude/one-data-gone", "createdAt": "2026-10-16T09:30:00+02:00", "mergedAt": "2026-10-16T13:00:00+02:00", "body": "复审：有阻塞", "mergeCommit": {"oid": merge_oid}},
               *[{"number": n, "title": f"review {n}", "headRefName": "claude/one-rules-t", "createdAt": "2026-10-16T09:40:00+02:00", "mergedAt": None, "body": body, "mergeCommit": None}
                 for n, body in ((10, "opus 复审：无阻塞，可合并"), (11, "终审：未通过"), (12, "opus 复审：阻塞项 B1 已修，待 sonnet 复核"), (13, "说明\n这是 opus 复审 B1 的修法\n复审结论：修后可合并"), (14, "复审：有阻塞"))],
               {"number": 15, "title": "codex work", "headRefName": "claude/one-ops-y", "createdAt": "2026-10-16T09:50:00+02:00", "mergedAt": None, "body": "## Codex 实现（模型、强度）\n\n<!-- 模板注释 Codex 实现（<模型>，<强度>） -->\n\nCodex 实现（gpt-6.1-sol，xhigh），返工 1 轮\n\n## 绕过推送前钩子\n\n无", "mergeCommit": None},
               {"number": 17, "title": "codex work before the window", "headRefName": "claude/one-ops-y", "createdAt": "2026-10-15T09:50:00+02:00", "mergedAt": None, "body": "Codex 实现（gpt-6.1-sol，xhigh）", "mergeCommit": None},
               {"number": 22, "title": "codex work opened long ago", "headRefName": "claude/one-ops-y", "createdAt": "2026-10-01T09:00:00+02:00", "mergedAt": None, "body": "Codex 实现（gpt-6.1-sol，xhigh）", "mergeCommit": None},
               {"number": 18, "title": "mentions codex without being codex work", "headRefName": "claude/one-ops-y", "createdAt": "2026-10-16T09:52:00+02:00", "mergedAt": None, "body": "## Codex 实现（模型、强度）\n\n<!-- 由 Codex 实现的写「Codex 实现（<模型>，<强度>）」；不是写「不涉及」 -->\n\n不涉及\n", "mergeCommit": None},
               {"number": 19, "title": "no section, inline", "headRefName": "claude/one-rules-t", "createdAt": "2026-10-16T09:53:00+02:00", "mergedAt": None, "body": "说明\n\ncodex 实现（gpt-6.1-sol，high）", "mergeCommit": None},
               {"number": 20, "title": "not done by codex", "headRefName": "claude/one-rules-t", "createdAt": "2026-10-16T09:54:00+02:00", "mergedAt": None, "body": "不由 Codex 实现", "mergeCommit": None},
               {"number": 21, "title": "opened two days ago, reworked inside the window", "headRefName": "claude/one-rules-t", "createdAt": "2026-10-14T09:00:00+02:00", "mergedAt": None, "body": "Codex 实现（gpt-6.1-sol，xhigh）", "mergeCommit": None},
               {"number": 16, "title": "claude work", "headRefName": "claude/one-ops-y", "createdAt": "2026-10-16T09:51:00+02:00", "mergedAt": None, "body": "没有这个词", "mergeCommit": None},
               {"number": 9, "title": "after the window", "headRefName": "claude/one-ops-y", "createdAt": "2026-10-17T10:00:00+02:00", "mergedAt": None, "body": "", "mergeCommit": None},
               {"number": 8, "title": "someone else", "headRefName": "feature/x", "createdAt": "2026-10-16T09:30:00+02:00", "mergedAt": None, "body": "", "mergeCommit": None}]
        runs = [{"databaseId": 1, "conclusion": "failure", "createdAt": "2026-10-16T10:00:00Z", "headBranch": "claude/one-quality-x", "event": "pull_request"},
                {"databaseId": 2, "conclusion": "success", "createdAt": "2026-10-16T10:00:00Z", "headBranch": "claude/one-quality-x", "event": "pull_request"},
                {"databaseId": 3, "conclusion": "failure", "createdAt": "2026-10-10T10:00:00Z", "headBranch": "claude/one-quality-x", "event": "pull_request"},
                {"databaseId": 5, "conclusion": "failure", "createdAt": "2026-10-17T09:30:00Z", "headBranch": "claude/one-quality-x", "event": "pull_request"},
                {"databaseId": 4, "conclusion": "failure", "createdAt": "2026-10-16T10:00:00Z", "headBranch": "dependabot/x", "event": "pull_request"}]
        commits = {15: [("2026-10-16T10:00:00+02:00", "feat: first"), ("2026-10-16T11:00:00+02:00", "fix: 按 opus 复审修正"), ("2026-10-16T12:00:00+02:00", "fix: 返工一轮"), ("2026-10-16T12:30:00+02:00", "Merge branch 'main' into x（返工）")],
                   16: [("2026-10-16T10:00:00+02:00", "fix: 复审修正")], 17: [("2026-10-16T10:00:00+02:00", "fix: 返工")], 22: [("2026-10-16T10:00:00+02:00", "fix: 返工")], 18: [("2026-10-16T10:00:00+02:00", "fix: 返工")],
                   19: [("2026-10-16T10:00:00+02:00", "fix: address review"), ("2026-10-17T10:00:00+02:00", "fix: 返工（窗口外）")], 20: [("2026-10-16T10:00:00+02:00", "fix: 返工")],
                   21: [("2026-10-14T10:00:00+02:00", "fix: 返工（窗口前）"), ("2026-10-16T11:00:00+02:00", "fix: 按 sonnet 复核修正"), ("2026-10-16T12:00:00+02:00", "docs: 记录返工率统计口径")],
                   5: [("2026-10-16T09:30:00+02:00", "before queue"), ("2026-10-16T10:30:00+02:00", "after queue"), ("2026-10-16T10:45:00+02:00", "Merge branch 'main' into claude/one-quality-x"),
                       ("2026-10-16T10:50:00+02:00", "after queue again")]}
        window = window_of("2026-10-17", None, None)
        assert window[0].isoformat() == "2026-10-16T08:00:00+02:00" and window[1].isoformat() == "2026-10-17T09:00:00+02:00"
        table_folder = os.path.join(repo, os.path.dirname(TASK_TABLE))
        os.makedirs(table_folder)
        registered = []
        for name in ("one-ops", "one-rules"):
            registered.append({"任务号": "GOV-1016-COO-" + ("01" if name == "one-ops" else "02"), "会话": name,
                               "记录": [{"时间": inside, "会话": name, "执行者与模型": "Codex gpt-6.1-sol xhigh", "新增": True, "返工增量": 0},
                                        {"时间": inside, "会话": name, "执行者与模型": "Codex gpt-6.1-sol xhigh", "新增": False, "返工增量": 3}]})
        def write_table(rows, digests=None):
            with open(os.path.join(repo, TASK_TABLE), "w", encoding="utf-8") as stream:
                stream.write("<!-- TASKS-DATA v1 -->\n```json\n" + json.dumps({"version": 1, "片段摘要": digests or {}, "tasks": rows}, ensure_ascii=False) + "\n```\n")
        write_table(registered)
        data = build(repo, FakeGh(prs, runs, commits), window, log)
        by = {s["name"]: s for s in data["sessions"]}
        assert len(data["sessions"]) == 13 and data["date"] == "2026-10-17" and data["window"] == "2026-10-16T08:00+02:00 ~ 2026-10-17T09:00+02:00", data["window"]
        assert by["one-quality"]["commits"] == 5, by["one-quality"]["commits"]  # 4 fixes + the book commit; the ops commit it merged is not its own
        assert by["one-rules"]["commits"] == 0, "one commit is counted once, for the first session that owns a branch holding it"
        assert by["one-ops"]["commits"] == 1 and by["one-data"]["commits"] == 1, "the commit of a deleted branch is found through the merge commit"
        assert by["one-quality"]["prs_opened_count"] == 1 and by["one-quality"]["prs_opened"] == ["#5 quality tool"]
        assert by["one-data"]["prs_merged"] == [7] and by["one-data"]["prs_merged_count"] == 1 and by["one-ops"]["prs_opened_count"] == 3
        assert by["one-glossary"]["commits"] == 1 and by["one-glossary"]["themes"][0].endswith("exactly at the start"), "the window is half open: the start is in, the end is out"
        assert by["one-rules"]["reviews"] == ["#10:可合并", "#11:有阻塞", "#12:有复审（结论未识别）", "#13:修后可合并", "#14:有阻塞"], by["one-rules"]["reviews"]
        assert by["one-rules"]["defect_bank_new"] == ["RB-01 / 2026-10-16 / new row"] and by["one-rules"]["defect_bank_empty"] is False, "a table book is read"
        assert any("claude/mystery-z 1 个" in n for n in data["notes"]) and not any("未计入" in n and "claude/one-" in n for n in data["notes"]), data["notes"]
        assert by["one-ops"]["codex_cards"] == 1 and by["one-ops"]["codex_rework_rounds"] == 3, (by["one-ops"]["codex_cards"], by["one-ops"]["codex_rework_rounds"])
        # 登记表为两会话各记一张卡、三轮；PR 里的其他 Codex 描述与返工标题不参与计数。
        assert by["one-rules"]["codex_cards"] == 1 and by["one-rules"]["codex_rework_rounds"] == 3, (by["one-rules"]["codex_cards"], by["one-rules"]["codex_rework_rounds"])
        assert by["one-quality"]["codex_cards"] == 0 and by["one-quality"]["codex_rework_rounds"] == 0
        for body, expected in (("## Codex 实现（模型、强度）\n\n<!-- c -->\n\n不涉及", False), ("## Codex 实现（模型、强度）\n\n<!-- Codex 实现（<模型>，<强度>） -->", False), ("## Codex 实现（模型、强度）\n\nCodex 实现（x，y）", True), ("## Codex 实现（模型、强度）\n\n不涉及", False),
                               ("Codex 起草 / 实现（gpt-6.1-sol，xhigh）", True), ("不由 Codex 实现", False), ("CODEX实现（a，b）", True), ("", False), (None, False)):
            assert is_codex_pr(body) is expected, body
        assert by["one-quality"]["returns"] == {"conflict": 1, "hold": 0} and by["one-ops"]["returns"] == {"conflict": 0, "hold": 1}, (by["one-quality"]["returns"], by["one-ops"]["returns"])
        assert by["one-quality"]["ci_failures"] == 1 and by["one-ops"]["ci_failures"] == 0
        assert by["one-quality"]["reviews"] == ["#5:修后可合并"] and by["one-data"]["reviews"] == ["#7:有阻塞"] and by["one-ops"]["reviews"] == []
        assert by["one-quality"]["pushed_after_queue"] == 2, "two commits after the queue label, the sync merge and the earlier one do not count"
        assert by["one-data"]["defect_bank_new"] == ["DATA-002 new one"] and by["one-data"]["defect_bank_empty"] is False
        assert by["one-quality"]["defect_bank_empty"] is False and by["one-quality"]["defect_bank_new"] == ["QUAL-900 on a branch"], by["one-quality"]["defect_bank_new"]
        assert any("fix(ci)" in c["repeated"] and "4 次" in c["repeated"] for c in by["one-quality"]["loop_candidates"]), by["one-quality"]["loop_candidates"]
        assert by["one-ops"]["loop_candidates"] == [] and by["one-quality"]["themes"][0].startswith("ci（4）")
        assert all(k in by["one-quality"] for k in ("grade", "assessment", "action")) is False, "the coordinator fills grade, assessment and action"
        assert set(by["one-quality"]) == {"name", "commits", "themes", "prs_opened", "prs_merged", "prs_opened_count", "prs_merged_count", "returns", "ci_failures", "reviews", "pushed_after_queue",
                                          "defect_bank_new", "defect_bank_empty", "loop_candidates", "role", "codex_cards", "codex_rework_rounds"}
        json.dumps(data, ensure_ascii=False)
        assert session_of("origin/claude/one-1a-next-5-train-x") == "one-coordinator" and session_of("claude/one-1a-next-5-data-pr1") == "one-1a-next-5" and session_of("feature/x") is None
        empty = build(repo, FakeGh([], [], {}), window, os.path.join(tmp, "no-such.log"))
        assert all(s["returns"] == {"conflict": None, "hold": None} and s["pushed_after_queue"] is None for s in empty["sessions"]), "no queue data is null, not zero"
        assert any("null（无数据，不是 0）" in n for n in empty["notes"])
        late = os.path.join(tmp, "late.log")
        with open(late, "w") as f:
            f.write("2026-10-17T07:21:00+02:00 HOLD pr=#5 run=- elapsed=0s checks red\n")
        assert all(s["returns"]["hold"] is None for s in build(repo, FakeGh([], [], {}), window, late)["sessions"]), "a log that starts after the window says nothing about it"
        assert by["one-quality"]["returns"]["hold"] == 0, "a log that covers the window gives real zeros"
        class Failing(FakeGh):
            def pr_commits(self, number):
                raise RuntimeError("gh api failed")
        broken = build(repo, Failing(prs, runs, commits), window, log)
        assert any("读不到" in n and "#5" in n and "codex_rework_rounds" in n for n in broken["notes"]), "a pull request whose commits cannot be read is named, not counted as zero"
        unreadable_note = next(n for n in broken["notes"] if "读不到" in n)
        assert unreadable_note.count("#15") == 1, "a pull request unreadable in both loops is named once: " + unreadable_note
        independent = build(repo, FakeGh([], [], {}), window, log)
        assert next(s for s in independent["sessions"] if s["name"] == "one-ops")["codex_rework_rounds"] == 3, "无 PR 仍按登记表计真实轮次"
        edge_task = {"任务号": "GOV-1016-COO-03", "会话": "one-quality", "记录": []}
        for time, new, delta in ((window[0].isoformat(), True, 0), (inside, False, 2), (window[1].isoformat(), True, 9)):
            edge_task["记录"].append({"时间": time, "会话": "one-quality", "执行者与模型": "Codex gpt-6.1-sol xhigh", "新增": new, "返工增量": delta})
        write_table([edge_task])
        counts, changes, _ = task_counts(repo, window)
        assert counts["one-quality"] == 1 and changes["one-quality"] == 2, "开始计入、结束不计；轮次不是提交数"
        edge_task["记录"][1]["返工增量"] = None
        write_table([edge_task])
        assert task_counts(repo, window)[1]["one-quality"] is None, "待核不能报零"
        edge_task["记录"] = []
        write_table([edge_task])
        assert task_counts(repo, window)[0]["one-quality"] is None, "未提交登记无时间证据"
        fragment = os.path.join(table_folder, "GOV-1016-COO-03.md")
        with open(fragment, "w") as stream:
            stream.write("changed")
        write_table(registered, {os.path.basename(fragment): hashlib.sha256(b"before").hexdigest()})
        assert all(v is None for v in task_counts(repo, window)[0].values()), "总表过期必须显式缺数据"
        os.unlink(fragment)
        os.unlink(os.path.join(repo, TASK_TABLE))
        missing = task_counts(repo, window)
        assert all(v is None for v in missing[0].values()) and "总表不可用" in missing[2][0]
        write_table(registered)
        # sessions by branch name: whole name or prefix plus a dash
        assert session_of("claude/one-database-x") is None and session_of("claude/one-1a-par2-x") is None and session_of("claude/one-data") == "one-data" and session_of("claude/one-data-x") == "one-data"
        assert session_of("claude/one-1a-next-5-train") == "one-coordinator" and session_of("claude/one-quality-merge-train") == "one-coordinator" and session_of("claude/one-merge-train-1") == "one-coordinator"
        assert session_of("claude/xin-61164f") == "one-quality" and session_of("claude/one-1a-next-4-x") is None
        # the window: summer and winter time, strict arguments
        assert window_of("2026-11-01", None, None)[0].isoformat() == "2026-10-31T08:00:00+01:00"
        for bad in (("2026-10-17", "2026-10-16T08:00+02:00", "2026-10-17T09:00+02:00"), (None, "2026-10-16T08:00+02:00", None), (None, "2026-10-16T08:00", "2026-10-17T09:00+02:00"), (None, "2026-10-17T09:00+02:00", "2026-10-16T08:00+02:00")):
            try:
                window_of(*bad)
            except ValueError:
                continue
            raise AssertionError(f"accepted {bad}")
        assert window_of(None, "2026-10-16T06:00Z", "2026-10-17T07:00Z")[0].isoformat() == "2026-10-16T08:00:00+02:00"
    print("session_daily self-test: ok")


def main():
    global TZ
    args = sys.argv[1:]
    if args == ["--self-test"]:
        return self_test()
    opts, known = {}, {"date", "start", "end", "out", "log", "tz"}
    while args:
        key = args.pop(0)
        if not key.startswith("--") or key[2:] not in known or not args or key[2:] in opts:
            print(f"session_daily: bad or repeated argument {key!r}\n{__doc__}", file=sys.stderr)
            return 2
        opts[key[2:]] = args.pop(0)
    try:
        if opts.get("tz"):
            TZ = zoneinfo.ZoneInfo(opts["tz"])
        window = window_of(opts.get("date"), opts.get("start"), opts.get("end"))
    except (ValueError, zoneinfo.ZoneInfoNotFoundError) as e:
        print(f"session_daily: {e}", file=sys.stderr)
        return 2
    root = toplevel()
    log = opts.get("log") or default_log(root)
    if not os.path.exists(log):
        print(f"warning: queue log {log} not found; returns and pushed_after_queue are null", file=sys.stderr)
    try:
        data = build(root, Gh(root), window, log)
    except RuntimeError as e:
        print(f"session_daily: {e}", file=sys.stderr)
        return 1
    text = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
    if opts.get("out"):
        with open(opts["out"], "w", encoding="utf-8") as f:
            f.write(text)
    else:
        sys.stdout.write(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
