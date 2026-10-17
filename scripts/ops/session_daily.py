#!/usr/bin/env python3
"""Daily session report data: one JSON object per one-* session for a time window, from git, gh and the merge queue log (the coordinator adds grade / assessment / action and publishes).

Per session: commits and their themes, pull requests opened and merged, queue returns (CONFLICT, HOLD from the timestamped queue log), CI failures, the review verdict written in the pull request body,
commits pushed after the pull request was queued, defect-bank entries dated inside the window, and loop candidates (the same kind of commit or queue event repeating).
Attribution is by branch name: a session owns the branches `claude/<prefix>*` listed in SESSIONS. Anything else is counted in the notes, not in a session.
Usage: session_daily.py [--date YYYY-MM-DD] [--start ISO] [--end ISO] [--out FILE] [--log merge.log]   |   session_daily.py --self-test
Default window: 08:00 the day before --date (default today) to 09:00 on --date, +02:00."""
import collections
import datetime
import json
import os
import re
import subprocess
import sys
import tempfile

TZ = datetime.timezone(datetime.timedelta(hours=2))
DEFECT_BANK = "docs/modernization/quality/defect-bank"
MAIN = "origin/main"
# (name, role, branch prefixes under claude/); the first session whose prefix matches owns a branch, so list the specific prefixes first
SESSIONS = [
    ("one-coordinator", "总调度：派工、批准入队、转达决定", ["one-coordinator", "one-1a-next-5-train-"]),
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
VERDICTS = (("修后可合并", re.compile("修后可合并|修正后可合并")), ("有阻塞", re.compile("有阻塞|阻塞")), ("可合并", re.compile("可合并|通过")))
QUEUE_LINE = re.compile(r"^(\S+)\s+([A-Z][A-Z-]*)\s+pr=#(\d+)\s*(.*)$")
SYNC_COMMIT = re.compile(r"^Merge (branch|remote-tracking branch) '(origin/)?main'")
LOOP_MIN = 4


def session_of(branch):
    """The session that owns `claude/...` branch, or None."""
    name = branch.removeprefix("origin/")
    if not name.startswith("claude/"):
        return None
    tail = name[len("claude/"):]
    for session, _, prefixes in SESSIONS:
        if any(tail.startswith(p) for p in prefixes):
            return session
    return None


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


def window_of(date, start, end):
    if start and end:
        return when(start), when(end)
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


def queue_events(path, window):
    """[(time, event, pr, detail)] of the timestamped queue log inside the window; unreadable or missing log gives []."""
    if not path or not os.path.exists(path):
        return []
    rows = []
    for line in open(path, encoding="utf-8", errors="replace"):
        match = QUEUE_LINE.match(line.strip())
        if match:
            try:
                time = when(match.group(1))
            except ValueError:
                continue
            if window[0] <= time < window[1]:
                rows.append((time, match.group(2), int(match.group(3)), match.group(4)))
    return rows


def review_of(body):
    for line in (body or "").splitlines():
        if REVIEW_WORDS.search(line):
            for label, pattern in VERDICTS:
                if pattern.search(line):
                    return label
            return "有复审（结论未识别）"
    return None


def book_texts(root, name, prefixes):
    """The defect book of a session as on main and as on the latest commit that touched it on the session's own branches (work not merged yet)."""
    path = f"{DEFECT_BANK}/{name}.md"
    texts = [git(root, "show", f"{MAIN}:{path}")]
    latest = git(root, "log", "-1", "--format=%H", *[f"--remotes=origin/claude/{p}*" for p in prefixes], "--", path).strip()
    if latest:
        texts.append(git(root, "show", f"{latest}:{path}"))
    return texts


def defect_entries(root, name, prefixes, window):
    """(entries dated inside the window as 'id title', whether the book holds no entry at all); a missing book counts as empty."""
    new, headings = [], set()
    for text in book_texts(root, name, prefixes):
        for heading, section in re.findall(r"^###\s+(.+?)\s*\n(.*?)(?=^###\s|\Z)", text, re.MULTILINE | re.DOTALL):
            heading = " ".join(heading.split())[:90]
            if heading in headings:
                continue
            headings.add(heading)
            date = re.search(r"日期\*{0,2}[：:]\s*\*{0,2}(\d{4}-\d{2}-\d{2})", section)
            if date and datetime.date.fromisoformat(date.group(1)) >= window[0].date():
                new.append(heading)
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


def build(root, gh, window, log_path):
    prs = {p["number"]: p for p in gh.prs()}
    owner = {n: session_of(p["headRefName"]) for n, p in prs.items()}
    events = queue_events(log_path, window)
    queued_at = {}
    for time, event, number, _ in sorted(queue_events(log_path, (window[0] - datetime.timedelta(days=30), window[1]))):
        if event == "LABEL-QUEUED":
            queued_at.setdefault(number, time)
    seen, per = set(), {name: {"subjects": []} for name, _, _ in SESSIONS}
    for name, _, prefixes in SESSIONS:
        refs = [f"--remotes=origin/claude/{p}*" for p in prefixes]
        for sha, _, subject in commit_rows(root, refs, window):
            if sha not in seen:
                seen.add(sha)
                per[name]["subjects"].append(subject)
    merged_here = [p for p in prs.values() if p.get("mergedAt") and in_window(p["mergedAt"], window) and (p.get("mergeCommit") or {}).get("oid")]
    unattributed = 0
    for p in merged_here:
        name = owner[p["number"]]
        oid = p["mergeCommit"]["oid"]
        for sha, _, subject in commit_rows(root, [f"{oid}^1..{oid}^2"], window) if git(root, "rev-parse", "--verify", "-q", f"{oid}^2").strip() else []:
            if sha in seen:
                continue
            seen.add(sha)
            if name:
                per[name]["subjects"].append(subject)
            else:
                unattributed += 1
    ci = collections.Counter()
    for run in gh.runs():
        if run.get("conclusion") == "failure" and in_window(run["createdAt"], window):
            ci[session_of(run["headBranch"] if run["headBranch"].startswith("claude/") else "claude/" + run["headBranch"]) or "-"] += 1
    returns = collections.defaultdict(lambda: collections.Counter())
    for _, event, number, _ in events:
        returns[owner.get(number)][event] += 1
    pushed = collections.Counter()
    for number, queued in queued_at.items():
        if owner.get(number) and (queued < window[1]):
            p = prs[number]
            if (p.get("mergedAt") and when(p["mergedAt"]) < window[0]) or when(p["createdAt"]) >= window[1]:
                continue
            try:
                commits = gh.pr_commits(number)
            except RuntimeError:
                continue
            pushed[owner[number]] += sum(1 for date, headline in commits if when(date) > queued and window[0] <= when(date) < window[1] and not SYNC_COMMIT.match(headline))
    sessions = []
    for name, role, prefixes in SESSIONS:
        opened = sorted((p for p in prs.values() if owner[p["number"]] == name and in_window(p["createdAt"], window)), key=lambda p: p["number"])
        merged = sorted(p["number"] for p in prs.values() if owner[p["number"]] == name and p.get("mergedAt") and in_window(p["mergedAt"], window))
        reviews = [f"#{p['number']}:{v}" for p in opened for v in [review_of(p.get("body"))] if v]
        new_entries, empty = defect_entries(root, name, prefixes, window)
        subjects = per[name]["subjects"]
        counts = returns.get(name, collections.Counter())
        sessions.append({
            "name": name, "commits": len(subjects), "themes": themes_of(subjects),
            "prs_opened": [f"#{p['number']} {p['title'][:60]}" for p in opened], "prs_merged": merged,
            "prs_opened_count": len(opened), "prs_merged_count": len(merged),
            "returns": {"conflict": counts["CONFLICT"], "hold": counts["HOLD"]},
            "ci_failures": ci[name], "reviews": reviews, "pushed_after_queue": pushed[name],
            "defect_bank_new": new_entries, "defect_bank_empty": empty,
            "loop_candidates": loop_candidates(subjects, counts), "role": role})
    notes = [f"窗口 {window[0].isoformat(timespec='minutes')} ~ {window[1].isoformat(timespec='minutes')}。提交按分支名归属会话（SESSIONS 的前缀表），去重；沿分支 first-parent 的非合并提交，committer 时间在窗口内（合入 main 的提交不重复算）；已删除分支的提交取窗口内合入 PR 的第二父链补算。",
             f"未归属任何会话的提交 {unattributed} 个（合入的 PR 分支不在前缀表内）；CI 失败按 gh run list（ci 工作流，最近 1000 条）的 headBranch 归属，未归属 {ci.get('-', 0)} 次。",
             "队列返回次数读带时间戳的队列日志（CONFLICT、HOLD），按 PR 的分支归属；日志缺失或无该窗口的行时为 0。",
             "pushed_after_queue = 该 PR 首次 LABEL-QUEUED（队列日志）之后、窗口内、非「合并 main」的提交数。",
             "reviews 取 PR 正文中第一处含 opus/复审/终审/ultrareview 的行，按「修后可合并 > 有阻塞 > 可合并」关键词归类，无关键词不列。",
             "defect_bank_new 取各会话错题本（origin/main 版，及该会话分支上最近改过它的版本）里日期不早于窗口开始日的条目；册不存在或无条目时 defect_bank_empty 为 true。",
             "loop_candidates 是机械统计（同类提交主题重复≥4 次、队列事件重复），automation 留空由调度填。"]
    return {"date": window[1].date().isoformat(), "window": f"{window[0].strftime('%Y-%m-%dT%H:%M%z')} ~ {window[1].strftime('%Y-%m-%dT%H:%M%z')}".replace("+0200", "+02:00"),
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
        sh("add", "-A")
        sh("commit", "-q", "-m", "book", at=before)
        sh("update-ref", "refs/remotes/origin/main", sh("rev-parse", "HEAD"))
        log = os.path.join(tmp, "merge.log")
        with open(log, "w") as f:
            f.write("2026-10-16T10:00:00+02:00 LABEL-QUEUED pr=#5 run=- elapsed=0s\n"
                    "2026-10-16T11:00:00+02:00 CONFLICT pr=#5 run=- elapsed=3s back to the author\n"
                    "2026-10-16T11:30:00+02:00 HOLD pr=#6 run=1 elapsed=3s checks red\n"
                    "2026-10-15T11:30:00+02:00 HOLD pr=#6 run=1 elapsed=3s yesterday\n"
                    "garbage line\n")
        prs = [{"number": 5, "title": "quality tool", "headRefName": "claude/one-quality-x", "createdAt": "2026-10-16T09:00:00+02:00", "mergedAt": None,
                "body": "## 评审\nopus 首轮复审：修后可合并\n", "mergeCommit": None},
               {"number": 6, "title": "ops", "headRefName": "claude/one-ops-y", "createdAt": "2026-10-15T09:00:00+02:00", "mergedAt": None, "body": "no review here", "mergeCommit": None},
               {"number": 7, "title": "gone", "headRefName": "claude/one-data-gone", "createdAt": "2026-10-16T09:30:00+02:00", "mergedAt": "2026-10-16T13:00:00+02:00", "body": "复审：有阻塞", "mergeCommit": {"oid": merge_oid}},
               {"number": 9, "title": "after the window", "headRefName": "claude/one-ops-y", "createdAt": "2026-10-17T10:00:00+02:00", "mergedAt": None, "body": "", "mergeCommit": None},
               {"number": 8, "title": "someone else", "headRefName": "feature/x", "createdAt": "2026-10-16T09:30:00+02:00", "mergedAt": None, "body": "", "mergeCommit": None}]
        runs = [{"databaseId": 1, "conclusion": "failure", "createdAt": "2026-10-16T10:00:00Z", "headBranch": "claude/one-quality-x", "event": "pull_request"},
                {"databaseId": 2, "conclusion": "success", "createdAt": "2026-10-16T10:00:00Z", "headBranch": "claude/one-quality-x", "event": "pull_request"},
                {"databaseId": 3, "conclusion": "failure", "createdAt": "2026-10-10T10:00:00Z", "headBranch": "claude/one-quality-x", "event": "pull_request"},
                {"databaseId": 5, "conclusion": "failure", "createdAt": "2026-10-17T09:30:00Z", "headBranch": "claude/one-quality-x", "event": "pull_request"},
                {"databaseId": 4, "conclusion": "failure", "createdAt": "2026-10-16T10:00:00Z", "headBranch": "dependabot/x", "event": "pull_request"}]
        commits = {5: [("2026-10-16T09:30:00+02:00", "before queue"), ("2026-10-16T10:30:00+02:00", "after queue"), ("2026-10-16T10:45:00+02:00", "Merge branch 'main' into claude/one-quality-x"),
                       ("2026-10-16T10:50:00+02:00", "after queue again")]}
        window = window_of("2026-10-17", None, None)
        assert window[0].isoformat() == "2026-10-16T08:00:00+02:00" and window[1].isoformat() == "2026-10-17T09:00:00+02:00"
        data = build(repo, FakeGh(prs, runs, commits), window, log)
        by = {s["name"]: s for s in data["sessions"]}
        assert len(data["sessions"]) == 13 and data["date"] == "2026-10-17" and data["window"] == "2026-10-16T08:00+02:00 ~ 2026-10-17T09:00+02:00", data["window"]
        assert by["one-quality"]["commits"] == 5, by["one-quality"]["commits"]  # 4 fixes + the book commit; the ops commit it merged is not its own
        assert by["one-rules"]["commits"] == 0, "one commit is counted once, for the first session that owns a branch holding it"
        assert by["one-ops"]["commits"] == 1 and by["one-data"]["commits"] == 1, "the commit of a deleted branch is found through the merge commit"
        assert by["one-quality"]["prs_opened_count"] == 1 and by["one-quality"]["prs_opened"] == ["#5 quality tool"]
        assert by["one-data"]["prs_merged"] == [7] and by["one-data"]["prs_merged_count"] == 1 and by["one-ops"]["prs_opened_count"] == 0
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
                                          "defect_bank_new", "defect_bank_empty", "loop_candidates", "role"}
        json.dumps(data, ensure_ascii=False)
        assert session_of("origin/claude/one-1a-next-5-train-x") == "one-coordinator" and session_of("claude/one-1a-next-5-data-pr1") == "one-1a-next-5" and session_of("feature/x") is None
        empty = build(repo, FakeGh([], [], {}), window, os.path.join(tmp, "no-such.log"))
        assert all(s["returns"] == {"conflict": 0, "hold": 0} for s in empty["sessions"]), "a missing queue log gives zeros"
    print("session_daily self-test: ok")


def main():
    args = sys.argv[1:]
    if args == ["--self-test"]:
        return self_test()
    opts = {}
    while args:
        key = args.pop(0)
        if not key.startswith("--") or not args:
            print(__doc__, file=sys.stderr)
            return 2
        opts[key[2:]] = args.pop(0)
    root = toplevel()
    window = window_of(opts.get("date"), opts.get("start"), opts.get("end"))
    log = opts.get("log") or default_log(root)
    if not os.path.exists(log):
        print(f"warning: queue log {log} not found; returns are counted as 0", file=sys.stderr)
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
