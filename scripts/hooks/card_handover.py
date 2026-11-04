#!/usr/bin/python3
"""One card, one clear (rules.md ②, #1080): a session whose pull request closed its card writes its handoff and clears itself before it does anything else.

--stop       Stop hook. Blocks the end of the turn (exit 2, Chinese message on stderr) when a pull request owned by this session was merged after this context began and its
             first line is `Closes #<card>`. A `Refs` pull request does not count. "This session" is the workspace's session marker (the one start_sheet.identity reads); the owner
             of a pull request is gapscan.author_session of its branch, the map the merge queue uses. "This context began" is the first timestamp of the hook input's transcript,
             so after a clear (a new transcript) the merge is older and the hook passes.
             Cheap first: marker and merge.log are local; gh is asked only for pull requests that merged after the context began, once each (cached in the workspace's git dir).
             Never blocks lead, a session without a marker, or a second stop in the same turn. Fails open: when gh, merge.log, the owners map or the transcript cannot be read,
             one 【钩子告警】 line (systemMessage) and exit 0.
--self-test  Block and pass cases, fail-open cases, and a check that no gh call is made when nothing merged since the context began.
"""
import datetime
import io
import json
import os
import re
import subprocess
import sys
import time

CLOSES = re.compile(r"Closes\s+#(\d+)\b")   # the first line of the pull request description (CLAUDE.md delivery 4)
MERGED = re.compile(r"^(\S+) MERGED pr=#(\d+) ")
LOG_TAIL = 1 << 20
BUDGET = 20   # seconds of gh lookups per stop (the mount cuts the hook at 30 s); what was looked up is cached, the next stop continues
CACHE = "one-session-handover.json"
HANDOFF = "/usr/bin/python3 ~/.modernize/psa/tools/one-session/handoff/handoff.py"
MESSAGE = ("【交卡】本会话的 PR #{pr} 已合入并关闭卡 #{card}，合入时间晚于本上下文的开始。一卡一清（rules.md ②）：先用 {handoff} 覆盖写交接（从 stdin 读，"
           "含交接顺序里的下一张卡），再调用 clear_session 工具清空本会话（目标选 self）。不要在这个上下文里开始下一张卡；清空后新上下文晚于这次合入，本钩子即放行。")


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
            m = CLOSES.match(first[0].strip()) if first else None
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


def check(payload, root, view=None, budget=BUDGET):
    """The reason to block, or None. Raises Unreadable."""
    sys.path.insert(0, os.path.join(root, "scripts", "merge"))
    try:
        import gapscan
        policy = os.path.join(root, "policy.json")
        owners = gapscan.load_owners(policy)
        with open(policy, encoding="utf-8") as f:
            config = json.load(f)["sessions"]["config"]
    except (ImportError, OSError, ValueError, KeyError, TypeError) as e:
        raise Unreadable(f"读不出会话清单（{root} 的 policy.json 或 scripts/merge/gapscan.py：{e}）")
    git_dir = git(root, "rev-parse", "--absolute-git-dir")
    try:
        with open(os.path.join(git_dir, "one-session"), encoding="utf-8") as f:
            session = f.read().strip()
    except OSError:
        return None   # no marker: no session, nothing owned
    if session not in config or config[session].get("role") == "lead":
        return None
    path = payload.get("transcript_path")
    if not isinstance(path, str):
        raise Unreadable("钩子输入没有对话记录路径")
    merged = merged_since(os.path.join(git(root, "rev-parse", "--path-format=absolute", "--git-common-dir"), "merge-queue", "merge.log"), context_start(path))
    if not merged:
        return None   # no network call
    cache_path = os.path.join(git_dir, CACHE)
    cache = load_cache(cache_path)
    hit = closing_pr(session, merged, owners, gapscan, view or (lambda n: gh_view(root, n)), cache, lambda c: save_cache(cache_path, c), time.monotonic() + budget)
    return MESSAGE.format(pr=hit[0], card=hit[1], handoff=HANDOFF) if hit else None


def stop(stdin, env=None, view=None, budget=BUDGET):
    """(exit code, stdout, stderr)."""
    env = os.environ if env is None else env
    try:
        payload = json.load(stdin)
        if payload.get("stop_hook_active"):   # blocked once already this turn: never loop
            return 0, "", ""
        root = env.get("CLAUDE_PROJECT_DIR") or payload.get("cwd")
        if not isinstance(root, str):
            raise Unreadable("没有项目目录")
        reason = check(payload, root, view, budget)
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
        queue = os.path.join(root, ".git", "merge-queue")
        os.makedirs(queue)

        def setup(session="one-platform", policy=True, log=(), marker=True):
            """log: [(time, pr)] or None for no merge.log."""
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
            return stop(io.StringIO(json.dumps(payload)), {"CLAUDE_PROJECT_DIR": root}, view, budget) + (calls,)

        after, before = now - datetime.timedelta(minutes=10), now - datetime.timedelta(hours=2)
        mine = {7: ("claude/one-platform-1080-x", "Closes #1080\n\nbody")}
        passes = (0, "", "")
        setup(log=[(after, 7)])
        code, out, err, calls = run(mine)
        assert code == 2 and "#7" in err and "#1080" in err and "clear_session" in err and "handoff.py" in err and "self" in err and out == "", ("closes after start blocks", code, out, err)
        assert calls == [7]
        assert run(mine, keep_cache=True)[3] == [], "the second look at the same merged pull request reads the cache"
        again = stop(io.StringIO(json.dumps({"transcript_path": "x", "cwd": root, "stop_hook_active": True})), {"CLAUDE_PROJECT_DIR": root})
        assert again == passes, "a second stop in the same turn passes"
        setup(log=[(before, 7)])   # the context began after the merge
        assert run(mine) == passes + ([],), "a merge before the context start passes, without gh"
        setup(log=[(after, 7)])
        assert run({7: ("claude/one-platform-1080-x", "Refs #1080\n\nbody")})[:3] == passes, "Refs passes"
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
        code, out, err = stop(io.StringIO(json.dumps({"transcript_path": os.path.join(d, "none"), "cwd": root})), {"CLAUDE_PROJECT_DIR": root})
        assert code == 0 and "【钩子告警】" in out, "an unreadable transcript fails open"
        code, out, err = stop(io.StringIO("not json"))
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
