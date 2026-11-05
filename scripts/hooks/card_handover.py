#!/usr/bin/python3
"""One card, one clear (rules.md ②, #1080, #1453): a session whose pull request merged writes its handoff, ends the turn, and lead clears it. The mechanism, not memory, holds it.

--stop       Stop hook. Two triggers, no skip for a second stop in the same turn (the host's continuation limit bounds it):
             child  A pull request owned by this session (branch `claude/<session>-…`, `Closes #n` or `Refs #n` on its first line; the owner is gapscan.author_session, the map the
                    merge queue uses) merged after the last handoff write blocks until a new handoff is written. The baseline is the mtime of `state/<session>/handoff.md`, so only
                    the merges since then are looked up (a handful, within the budget); without a handoff file it is the transcript's first timestamp.
             lead   Lead is not exempt: it blocks while a child session has a handoff, no merged pull request of its own after that handoff, a context that began before the handoff
                    (its newest transcript file under ~/.claude/projects, found through the git worktrees carrying the child's session marker, started earlier) and has been idle
                    for IDLE seconds: handed over, not cleared. The message names the child and the steps. The clearing stays with lead.
             Cheap first: marker, handoff and merge.log are local; gh is asked only for pull requests that merged after the baseline, once each (cached in the workspace's git dir).
             A session without a marker is never blocked. Fails open: when gh, merge.log, the owners map or a transcript cannot be read, one 【钩子告警】 line (systemMessage), exit 0.
--self-test  Block and pass cases for both triggers, the compaction case, the 171-merge case within budget, fail-open cases, and no gh call when nothing merged since the baseline.
"""
import datetime
import io
import json
import os
import re
import subprocess
import sys
import time

CARD = re.compile(r"(?:Closes|Refs)\s+#(\d+)\b")   # the first line of the pull request description (CLAUDE.md delivery 4)
MERGED = re.compile(r"^(\S+) MERGED pr=#(\d+) ")
LOG_TAIL = 1 << 20
BUDGET = 20   # seconds of gh lookups per stop (the mount cuts the hook at 30 s); what was looked up is cached, the next stop continues
CACHE = "one-session-handover-v2.json"
IDLE = 600   # seconds a child transcript has not grown before lead treats the child as handed over
HANDOFF = "/usr/bin/python3 ~/.modernize/psa/tools/one-session/handoff/handoff.py"
MESSAGE = ("【交卡】本会话的 PR #{pr}（卡 #{card}）已合入，合入时间晚于最近一次交接（没有交接文件时晚于本上下文开始）。一卡一清（rules.md ②）：先用 {handoff} 覆盖写交接（从 stdin 读，"
           "含交接顺序里的下一张卡），然后结束本回合，不要自己清空，也不要开始下一张卡；由 lead 清空本会话并发 接续。交接写入后本钩子即放行。")
LEAD_MESSAGE = ("【待清空】会话 {child} 已交接（交接 {when}），其上下文开始于交接之前，且之后没有它自己合入的 PR。请 lead 依次：clear_session({child})；确认出现新的对话记录文件；"
                "向 {child} 发 接续（SendMessage）。清空由 lead 做，子会话不自清。")
class Unreadable(Exception):
    """An input the hook needs cannot be read: fail open with a warning."""


def git(root, *args):
    p = subprocess.run(["git", "-C", root, *args], capture_output=True, text=True, stdin=subprocess.DEVNULL, timeout=5)
    if p.returncode:
        raise Unreadable(f"git {' '.join(args)} 失败（{root}）")
    return p.stdout.strip()


def parse_time(text):
    return datetime.datetime.fromisoformat(text.replace("Z", "+00:00"))


def context_start(path):
    """The first timestamp in the transcript (entries before it, such as snapshots, carry none)."""
    try:
        with open(path, encoding="utf-8", errors="replace") as f:
            for _, line in zip(range(200), f):
                try:
                    stamp = json.loads(line).get("timestamp")
                except ValueError:
                    continue
                if isinstance(stamp, str):
                    return parse_time(stamp)
    except (OSError, ValueError, AttributeError) as e:
        raise Unreadable(f"读不出对话记录 {path}（{e}）")
    raise Unreadable(f"对话记录 {path} 里没有时间戳")


def merged_since(log_path, start, tail=LOG_TAIL):
    """Numbers of the pull requests merge.log shows MERGED after `start`. Only the last `tail` bytes are read; the first line of a cut is dropped (it may be half a line)."""
    try:
        with open(log_path, "rb") as f:
            f.seek(0, 2)
            offset = max(0, f.tell() - tail)
            f.seek(offset)
            lines = f.read().decode("utf-8", "replace").splitlines()[1 if offset else 0:]
        return [int(m.group(2)) for m in map(MERGED.match, lines) if m and parse_time(m.group(1)) > start]
    except (OSError, ValueError) as e:
        raise Unreadable(f"读不出合入日志 {log_path}（{e}）")


def gh_view(root, n):
    try:
        p = subprocess.run(["gh", "pr", "view", str(n), "--json", "headRefName,body"], cwd=root, capture_output=True, text=True, stdin=subprocess.DEVNULL, timeout=15)
        return json.loads(p.stdout) if p.returncode == 0 else None
    except (OSError, ValueError, subprocess.SubprocessError):
        return None


def closing_pr(session, prs, owners, gapscan, view, cache, save, deadline):
    """(pr, card) of the first of `prs` that `session` owns and that closed its card, else None. `cache` {pr: [owner, card or None]} is filled in and
    `save`d after every lookup; past `deadline` (time.monotonic) the lookups stop with Unreadable and the next stop continues from the cache."""
    for n in prs:
        if str(n) not in cache:
            if time.monotonic() > deadline:
                raise Unreadable(f"PR 太多，{BUDGET} 秒内没查完，已查的存了缓存，下一回合继续")
            info = view(n)
            if not isinstance(info, dict) or "headRefName" not in info:
                raise Unreadable(f"gh 读不出 PR #{n}")
            first = str(info.get("body") or "").strip().splitlines()[:1]
            m = CARD.match(first[0].strip()) if first else None
            cache[str(n)] = [gapscan.author_session(info["headRefName"], owners), int(m.group(1)) if m else None]
            save(cache)
        owner, card = cache[str(n)]
        if owner == session and card:
            return n, card
    return None


def load_cache(path):
    try:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        return data if isinstance(data, dict) else {}
    except (OSError, ValueError):
        return {}


def save_cache(path, cache):
    try:
        with open(path + ".tmp", "w", encoding="utf-8") as f:
            json.dump(cache, f)
        os.replace(path + ".tmp", path)
    except OSError:
        pass


def state_root():
    return os.environ.get("HANDOFF_STATE") or os.path.expanduser("~/.modernize/psa/state")   # the same rule as start_sheet.state_root


def projects_root():
    return os.path.join(os.environ.get("CLAUDE_CONFIG_DIR") or os.path.expanduser("~/.claude"), "projects")


def handoff_time(session, state):
    """mtime of state/<session>/handoff.md as an aware datetime, None when there is none."""
    try:
        return datetime.datetime.fromtimestamp(os.stat(os.path.join(state, session, "handoff.md")).st_mtime, datetime.timezone.utc)
    except OSError:
        return None


def session_transcripts(root, session, projects):
    """Transcript files (path, mtime) of every git worktree whose session marker is `session`; Claude Code keeps them in projects/<path with non-alphanumerics as dashes>/."""
    found = []
    for line in git(root, "worktree", "list", "--porcelain").splitlines():
        if not line.startswith("worktree "):
            continue
        path = line[len("worktree "):]
        try:
            with open(os.path.join(path, ".git"), encoding="utf-8") as f:
                git_dir = f.read().strip().split("gitdir:", 1)[1].strip()
            with open(os.path.join(git_dir, "one-session"), encoding="utf-8") as f:
                if f.read().strip() != session:
                    continue
        except (OSError, IndexError):
            continue   # the main checkout (.git is a folder), or a worktree without a marker
        folder = os.path.join(projects, re.sub(r"[^A-Za-z0-9]", "-", path))
        try:
            found += [(e.path, e.stat().st_mtime) for e in os.scandir(folder) if e.name.endswith(".jsonl")]
        except OSError:
            continue
    return found


def check(payload, root, view=None, budget=BUDGET, state=None, projects=None, now=None):
    """The reason to block, or None. Raises Unreadable."""
    sys.path.insert(0, os.path.join(root, "scripts", "merge"))
    try:
        import gapscan
        policy = os.path.join(root, "policy.json")
        owners = gapscan.load_owners(policy)
        with open(policy, encoding="utf-8") as f:
            config = json.load(f)["sessions"]["config"]
    except (ImportError, OSError, ValueError, KeyError, TypeError) as e:
        raise Unreadable(f"读不出会话清单（{root} 的 policy.json 或 scripts/merge/gapscan.py、issue_gate.py：{e}）")
    git_dir = git(root, "rev-parse", "--absolute-git-dir")
    try:
        with open(os.path.join(git_dir, "one-session"), encoding="utf-8") as f:
            session = f.read().strip()
    except OSError:
        return None   # no marker: no session, nothing owned
    if session not in config:
        return None
    state = state or state_root()
    log_path = os.path.join(git(root, "rev-parse", "--path-format=absolute", "--git-common-dir"), "merge-queue", "merge.log")
    cache_path = os.path.join(git_dir, CACHE)
    cache = load_cache(cache_path)
    view = view or (lambda n: gh_view(root, n))
    deadline = time.monotonic() + budget

    def owned_merge(who, since):   # (pr, card) of a pull request of `who` merged after `since`, else None; no network call when nothing merged since
        merged = merged_since(log_path, since)
        return closing_pr(who, merged, owners, gapscan, view, cache, lambda c: save_cache(cache_path, c), deadline) if merged else None

    if config[session].get("role") != "lead":
        path = payload.get("transcript_path")
        since = handoff_time(session, state)
        if since is None:
            if not isinstance(path, str):
                raise Unreadable("钩子输入没有对话记录路径")
            since = context_start(path)
        hit = owned_merge(session, since)
        return MESSAGE.format(pr=hit[0], card=hit[1], handoff=HANDOFF) if hit else None
    now = now or datetime.datetime.now(datetime.timezone.utc)
    for child in sorted(config):
        if config[child].get("role") == "lead":
            continue
        since = handoff_time(child, state)
        files = session_transcripts(root, child, projects or projects_root()) if since else []
        if not files:
            continue
        newest, mtime = max(files, key=lambda f: f[1])
        if now.timestamp() - mtime < IDLE or context_start(newest) >= since:
            continue   # still working, or cleared (its newest context began after the handoff)
        if not owned_merge(child, since):
            return LEAD_MESSAGE.format(child=child, when=since.astimezone().strftime("%m-%d %H:%M"))
    return None


def stop(stdin, env=None, view=None, budget=BUDGET, state=None, projects=None, now=None):
    """(exit code, stdout, stderr)."""
    env = os.environ if env is None else env
    try:
        payload = json.load(stdin)
        root = env.get("CLAUDE_PROJECT_DIR") or payload.get("cwd")
        if not isinstance(root, str):
            raise Unreadable("没有项目目录")
        reason = check(payload, root, view, budget, state, projects, now)
    except Exception as e:  # fail open: a broken check must not hold up a session
        why = str(e) if isinstance(e, Unreadable) else f"{type(e).__name__}: {e}"
        return 0, json.dumps({"systemMessage": f"【钩子告警】card_handover 读不了输入，本次放行：{why}"}, ensure_ascii=False), ""
    return (2, "", reason) if reason else (0, "", "")


def self_test():
    import shutil
    import tempfile
    here = os.path.dirname(os.path.abspath(__file__))
    now = datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0)
    iso = lambda dt: dt.isoformat()
    config = {"one-lead": {"role": "lead"}, "one-platform": {"role": "platform"}, "one-dev-org": {"role": "dev"}}
    with tempfile.TemporaryDirectory() as d:
        root = os.path.join(d, "repo")
        os.makedirs(os.path.join(root, "scripts", "merge"))
        for name in ("gapscan.py", "issue_gate.py"):
            shutil.copy(os.path.join(here, "..", "merge", name), os.path.join(root, "scripts", "merge", name))
        subprocess.run(["git", "init", "-q", root], check=True)
        subprocess.run(["git", "-C", root, "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "--allow-empty", "-m", "x"], check=True)
        queue = os.path.join(root, ".git", "merge-queue")
        os.makedirs(queue)
        state, projects = os.path.join(d, "state"), os.path.join(d, "projects")

        def handoff(session, when):
            os.makedirs(os.path.join(state, session), exist_ok=True)
            path = os.path.join(state, session, "handoff.md")
            with open(path, "w", encoding="utf-8") as f:
                f.write("h")
            os.utime(path, (when.timestamp(), when.timestamp()))

        def setup(session="one-platform", policy=True, log=(), marker=True):
            """log: [(time, pr)] or None for no merge.log. Also empties the state folder (no handoffs)."""
            shutil.rmtree(state, ignore_errors=True)
            os.makedirs(state)
            with open(os.path.join(root, "policy.json"), "w", encoding="utf-8") as f:
                f.write(json.dumps({"sessions": {"config": config}}) if policy else "not json")
            marker_path = os.path.join(root, ".git", "one-session")
            if marker:
                with open(marker_path, "w", encoding="utf-8") as f:
                    f.write(session + "\n")
            elif os.path.exists(marker_path):
                os.remove(marker_path)
            log_path = os.path.join(queue, "merge.log")
            if log is None:
                if os.path.exists(log_path):
                    os.remove(log_path)
            else:
                with open(log_path, "w", encoding="utf-8") as f:
                    f.write("".join(f"{iso(t)} MERGED pr=#{n} run=1 elapsed=1s lane=code abc\n" for t, n in log))
            cache = os.path.join(root, ".git", CACHE)
            if os.path.exists(cache):
                os.remove(cache)

        def transcript(start):
            path = os.path.join(d, "t.jsonl")
            with open(path, "w", encoding="utf-8") as f:
                f.write(json.dumps({"type": "summary"}) + "\n" + json.dumps({"type": "user", "timestamp": iso(start).replace("+00:00", ".123Z"), "message": {"content": "x"}}) + "\n")
            return path

        def run(prs, start=now - datetime.timedelta(hours=1), keep_cache=False, budget=BUDGET):
            """prs: {number: (branch, body)}. Returns (code, stdout, stderr, gh calls). Every run starts without a cache unless `keep_cache`."""
            calls = []
            cache = os.path.join(root, ".git", CACHE)
            if not keep_cache and os.path.exists(cache):
                os.remove(cache)

            def view(n):
                calls.append(n)
                return {"headRefName": prs[n][0], "body": prs[n][1]} if n in prs else None
            payload = {"transcript_path": transcript(start), "cwd": root}
            return stop(io.StringIO(json.dumps(payload)), {"CLAUDE_PROJECT_DIR": root}, view, budget, state, projects) + (calls,)

        after, before = now - datetime.timedelta(minutes=10), now - datetime.timedelta(hours=2)
        mine = {7: ("claude/one-platform-1080-x", "Closes #1080\n\nbody")}
        passes = (0, "", "")
        setup(log=[(after, 7)])
        code, out, err, calls = run(mine)
        assert code == 2 and "#7" in err and "#1080" in err and "clear_session" not in err and "handoff.py" in err and out == "", ("closes after start blocks", code, out, err)
        assert calls == [7]
        assert run(mine, keep_cache=True)[3] == [], "the second look at the same merged pull request reads the cache"
        again = stop(io.StringIO(json.dumps({"transcript_path": transcript(now - datetime.timedelta(hours=1)), "cwd": root, "stop_hook_active": True})), {"CLAUDE_PROJECT_DIR": root}, lambda n: {"headRefName": mine[n][0], "body": mine[n][1]}, BUDGET, state, projects)
        assert again[0] == 2, "a second stop in the same turn with the handoff still older than the merge blocks again"
        setup(log=[(before, 7)])   # the context began after the merge
        assert run(mine) == passes + ([],), "a merge before the context start passes, without gh"
        setup(log=[(after, 7)])
        assert run({7: ("claude/one-platform-1080-x", "Refs #1080\n\nbody")})[0] == 2, "a Refs pull request counts too"
        assert run({7: ("claude/one-platform-1080-x", "no card line\n\nbody")})[:3] == passes, "a pull request without Closes or Refs on its first line does not"
        assert run({7: ("claude/one-platform-1080-x", "Summary\n\nCloses #1080")})[:3] == passes, "Closes must be on the first line"
        assert run({7: ("claude/one-dev-org-9-x", "Closes #9")})[:3] == passes, "another session's pull request passes"
        assert run({7: ("claude/someone-else-9", "Closes #9")})[:3] == passes, "unknown owner passes"
        setup(log=[(after, 7), (after, 8)])
        assert run({7: ("claude/one-platform-1-x", "Refs #1"), 8: mine[7]})[0] == 2, "a Refs merge does not hide a later Closes merge"
        setup(log=[(after, 7), (after, 8), (after, 9)])   # past the budget: fail open, but what was looked up stays cached and the next stop continues
        three = {n: ("claude/one-dev-org-1-x", "Refs #1") for n in (7, 8, 9)}
        three[9] = mine[7]
        code, out, err, calls = run(three, budget=-1)
        assert code == 0 and "【钩子告警】" in out and calls == [], (code, out, calls)
        cache_file = os.path.join(root, ".git", CACHE)
        assert not os.path.exists(cache_file)
        slow = []

        def tick(n):   # the budget runs out after the first lookup
            slow.append(n)
            time.sleep(0.05)
            return {"headRefName": three[n][0], "body": three[n][1]}
        payload = json.dumps({"transcript_path": transcript(now - datetime.timedelta(hours=1)), "cwd": root})
        code, out, err = stop(io.StringIO(payload), {"CLAUDE_PROJECT_DIR": root}, tick, 0.01)
        assert code == 0 and "【钩子告警】" in out and slow == [7] and sorted(json.load(open(cache_file))) == ["7"], (code, out, slow)
        code, out, err, calls = run(three, keep_cache=True)
        assert calls == [8, 9] and code == 2, ("the next stop continues from the cache", calls)
        for label, kw, prs in (("no merge.log", dict(log=None), mine), ("bad policy", dict(log=[(after, 7)], policy=False), mine), ("gh down", dict(log=[(after, 7)]), {})):
            setup(**kw)
            code, out, err, _ = run(prs)
            assert code == 0 and err == "" and "\n" not in out and json.loads(out)["systemMessage"].startswith("【钩子告警】"), (label, code, out, err)
        setup(log=[(after, 7)])
        code, out, err = stop(io.StringIO(json.dumps({"transcript_path": os.path.join(d, "none"), "cwd": root})), {"CLAUDE_PROJECT_DIR": root}, None, BUDGET, state, projects)
        assert code == 0 and "【钩子告警】" in out, "an unreadable transcript fails open"
        code, out, err = stop(io.StringIO("not json"), {}, None, BUDGET, state, projects)
        assert code == 0 and "【钩子告警】" in out, "unreadable input fails open"
        setup(session="one-lead", log=[(after, 7)])
        assert run({7: ("claude/one-lead-x", "Closes #5")}) == passes + ([],), "lead is never blocked"
        setup(marker=False, log=[(after, 7)])
        assert run(mine) == passes + ([],), "a workspace without a session marker passes"
        setup(session="not-a-session", log=[(after, 7)])
        assert run(mine) == passes + ([],), "a marker outside sessions.config passes"
        setup(log=[(after, 7)])   # a log longer than the tail read: the cut can fall inside a MERGED line, whose time then does not parse
        log_path = os.path.join(queue, "merge.log")
        with open(log_path, encoding="utf-8") as f:
            line = f.read()
        with open(log_path, "w", encoding="utf-8") as f:
            f.write(line * 3)
        assert merged_since(log_path, now - datetime.timedelta(hours=1), tail=2 * len(line) - 10) == [7], "a half line at the cut is dropped, not parsed"
        setup(log=[])
        assert run(mine) == passes + ([],), "nothing merged since the context start: no gh call"
        # #1453 child: the baseline is the handoff file, not the transcript start
        hours = [(now - datetime.timedelta(hours=h), 100 + h) for h in range(1, 172)]   # 171 merges since the (3 days old) transcript start, all before the handoff
        setup(log=hours)
        handoff("one-platform", now - datetime.timedelta(minutes=5))
        three_days = now - datetime.timedelta(days=3)
        assert run(mine, start=three_days) == passes + ([],), "compaction: old transcript start, recent handoff, 171 merges before it: passes without a gh call"
        setup(log=hours + [(now - datetime.timedelta(minutes=2), 7)])
        handoff("one-platform", now - datetime.timedelta(minutes=5))
        code, out, err, calls = run(mine, start=three_days)
        assert code == 2 and calls == [7] and "结束本回合" in err and "不要自己清空" in err, ("a merge after the handoff blocks after one lookup", code, calls, err)
        handoff("one-platform", now)
        assert run(mine, start=three_days) == passes + ([],), "writing the handoff releases the block"
        # #1453 lead: blocks on a child that has a handoff, no merge of its own after it and a context older than it
        subprocess.run(["git", "-C", root, "worktree", "add", "-q", os.path.join(d, "wt-org"), "-b", "child"], check=True)
        wt = os.path.realpath(os.path.join(d, "wt-org"))
        with open(os.path.join(wt, ".git"), encoding="utf-8") as f:
            with open(os.path.join(f.read().strip().split("gitdir:", 1)[1].strip(), "one-session"), "w", encoding="utf-8") as m:
                m.write("one-dev-org\n")

        def child_transcript(name, start, mtime):
            folder = os.path.join(projects, re.sub(r"[^A-Za-z0-9]", "-", wt))
            os.makedirs(folder, exist_ok=True)
            path = os.path.join(folder, name)
            with open(path, "w", encoding="utf-8") as f:
                f.write(json.dumps({"type": "user", "timestamp": iso(start)}) + "\n")
            os.utime(path, (mtime.timestamp(), mtime.timestamp()))
        minutes = lambda n: now - datetime.timedelta(minutes=n)
        setup(session="one-lead", log=[])
        handoff("one-dev-org", minutes(30))
        child_transcript("old.jsonl", three_days, minutes(60))
        code, out, err, calls = run({})
        assert code == 2 and "one-dev-org" in err and "clear_session(one-dev-org)" in err and "接续" in err and calls == [], ("handed over, not cleared", code, out, err)
        child_transcript("new.jsonl", minutes(20), minutes(15))
        assert run({}) == passes + ([],), "cleared: the newest context began after the handoff"
        os.remove(os.path.join(projects, re.sub(r"[^A-Za-z0-9]", "-", wt), "new.jsonl"))
        child_transcript("old.jsonl", three_days, minutes(1))
        assert run({}) == passes + ([],), "a child still writing its transcript is not handed over"
        child_transcript("old.jsonl", three_days, minutes(60))
        setup(session="one-lead", log=[(minutes(10), 7)])
        handoff("one-dev-org", minutes(30))
        assert run({7: ("claude/one-dev-org-9-x", "Closes #9")}) == passes + ([7],), "a merge of its own after the handoff: the child's own hook handles it"
        assert run({7: ("claude/one-design-9-x", "Closes #9")})[0] == 2, "another session's merge does not release the child"
        setup(session="one-lead", log=[])
        assert run({}) == passes + ([],), "no handoff, nothing to clear"
    print("card_handover self-test: ok")


if __name__ == "__main__":
    mode = sys.argv[1] if len(sys.argv) > 1 else ""
    if mode == "--stop":
        code, out, err = stop(sys.stdin)
        sys.stdout.write(out)
        sys.stderr.write(err)
        sys.exit(code)
    elif mode == "--self-test":
        self_test()
    else:
        sys.exit("usage: card_handover.py --stop | --self-test")
