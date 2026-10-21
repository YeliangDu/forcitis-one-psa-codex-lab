#!/usr/bin/env python3
"""Hook (PostToolUse, UserPromptSubmit, Stop): hand a session over before its context fills up (SESSION_PLAYBOOK §2.1 #1, GOV-1021-QUA-01).

Measure: the usage of the last assistant message of the session's own transcript (hook input `transcript_path`, sub-agent messages skipped):
input + cache_read + cache_creation tokens, divided by the model's window from scripts/hooks/context_windows.json.
A model missing from the file is counted against the conservative `default` and says so once per session; an unreadable transcript or config says so once and does nothing else.
Remind (PostToolUse, UserPromptSubmit; reaches the model as additionalContext, so it only takes effect at the next tool call or prompt): at >= 75% "at the batch boundary update the context documents and hand over, report to one-coordinator" (no clear: the user clears or the session compacts, user 2026-10-21);
at >= 85% "hand over now". Each level once per session; state lives in the untracked <root>/.claude/context-handoff-state.json (a cleared session has a new id, so starts clean).
Enforce (Stop): past 75% while none of the profile / context documents (SESSION_PLAYBOOK §2.1 #16, same session-name resolution as session_context.py) was modified since the level was first reached, the stop is blocked once per level with the reason; a document written since lets it through.
Never a loop: `stop_hook_active` lets the stop through, and a level blocks at most once. A crash of this hook never blocks a stop; for the other events it is reported once as additionalContext.
During the pilot `pilot_sessions` in context_windows.json limits the hook to the listed session names (null or absent = every session).
Usage: context_handoff.py < hook-input.json  |  context_handoff.py --self-test
"""
import contextlib
import fcntl
import io
import json
import os
import re
import subprocess
import sys
import tempfile
import time


HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import session_context as sc  # noqa: E402

CONFIG = os.environ.get("CONTEXT_WINDOWS_CONFIG") or os.path.join(HERE, "context_windows.json")
STATE = ".claude/context-handoff-state.json"
LEVELS = (75, 85)
FALLBACK_WINDOW = 200000
KEEP_SESSIONS = 20
NAME_RECHECK_SECONDS = 600
RESET_BELOW = 60
TAIL_BYTES = 4 * 1024 * 1024
SESSIONS = sc.SESSIONS


class NothingYet(ValueError):
    """The transcript exists but holds no measurable assistant message yet (a new or just cleared session): nothing to report."""


def load_windows(path=None):
    """(default window, {model: window}, warning or None)."""
    path = path or CONFIG
    try:
        with open(path, encoding="utf-8") as stream:
            data = json.load(stream)
        default = int(data.get("default", FALLBACK_WINDOW))
        windows = {str(k): int(v) for k, v in (data.get("windows") or {}).items()}
        if default <= 0 or any(v <= 0 for v in windows.values()):
            raise ValueError("窗口必须是正整数")
        return default, windows, None
    except (OSError, ValueError, TypeError, AttributeError) as error:
        return FALLBACK_WINDOW, {}, f"上下文窗口配置 {os.path.basename(path)} 读不了（{error}），一律按保守值 {FALLBACK_WINDOW} 计算"


def pilot_sessions(path=None):
    """Session names the hook is limited to during the pilot, or None for every session (`pilot_sessions` in the configuration)."""
    try:
        with open(path or CONFIG, encoding="utf-8") as stream:
            listed = json.load(stream).get("pilot_sessions")
        return [str(item) for item in listed] if isinstance(listed, list) else None
    except (OSError, ValueError, AttributeError):
        return None


def window_for(model, default, windows):
    """(window, warning or None): exact model, then the longest configured prefix, else the conservative default."""
    if model in windows:
        return windows[model], None
    prefixes = [name for name in windows if model and model.startswith(name)]
    if prefixes:
        return windows[max(prefixes, key=len)], None
    return default, f"模型「{model or '未知'}」不在 scripts/hooks/context_windows.json，按保守值 {default} 计算占比，请补配置"


def last_usage(path):
    """(tokens, model) of the last non-sidechain assistant message that carries usage; raises OSError / ValueError when there is none."""
    size = os.path.getsize(path)
    window = TAIL_BYTES
    while True:
        with open(path, "rb") as stream:
            stream.seek(max(0, size - window))
            chunk = stream.read()
        lines = chunk.split(b"\n")
        if size > window:
            lines = lines[1:]
        for raw in reversed(lines):
            try:
                entry = json.loads(raw)
            except ValueError:
                continue
            message = entry.get("message") if isinstance(entry, dict) else None
            if entry.get("type") != "assistant" or entry.get("isSidechain") or not isinstance(message, dict):
                continue
            usage = message.get("usage")
            if isinstance(usage, dict):
                if message.get("model") == "<synthetic>":
                    continue
                tokens = sum(int(usage.get(key) or 0) for key in ("input_tokens", "cache_read_input_tokens", "cache_creation_input_tokens"))
                return tokens, str(message.get("model") or "")
        if size <= window:
            raise NothingYet("transcript 里还没有带 usage 的 assistant 消息")
        window *= 4


def resolve_session(root):
    """(name or None, profile path or None, [context doc paths]) with the same rules as session_context.py."""
    names = set()
    listed = sc.git(root, "ls-tree", "--name-only", f"origin/main:{SESSIONS}")
    local = os.listdir(os.path.join(root, SESSIONS)) if os.path.isdir(os.path.join(root, SESSIONS)) else []
    for file in (listed or "").split() + local:
        if file.endswith(".md") and not file.startswith("_"):
            names.add(file[:-3])
    retired_path = f"{SESSIONS}/_retired.md"
    retired = sc.retired_names(sc.from_main(root, retired_path) or sc.from_tree(root, retired_path)) - names
    marker_path = os.path.join(root, sc.MARKER)
    marker = open(marker_path, encoding="utf-8").read().strip() if os.path.isfile(marker_path) else ""
    branch = (sc.git(root, "rev-parse", "--abbrev-ref", "HEAD") or "").strip().removeprefix("claude/")
    name = sc.match(names | retired, marker, [os.path.basename(root), branch])
    if not name or name in retired:
        return None, None, []
    profile_path = f"{SESSIONS}/{name}.md"
    profile = sc.from_main(root, profile_path) or sc.from_tree(root, profile_path) or ""
    docs = [doc for doc in sc.context_docs(profile) if doc != profile_path and not sc.unsafe_reason(root, doc)]
    return name, profile_path, docs


def load_state(root):
    try:
        with open(os.path.join(root, STATE), encoding="utf-8") as stream:
            data = json.load(stream)
        return {key: value for key, value in data.items() if isinstance(value, dict)} if isinstance(data, dict) else {}
    except (OSError, ValueError):
        return {}


def save_state(root, state):
    path = os.path.join(root, STATE)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    if len(state) > KEEP_SESSIONS:
        for stale in sorted(state, key=lambda key: state[key].get("seen", 0))[:len(state) - KEEP_SESSIONS]:
            del state[stale]
    handle_, temporary = tempfile.mkstemp(dir=os.path.dirname(path), prefix="context-handoff-", suffix=".tmp")
    try:
        with os.fdopen(handle_, "w", encoding="utf-8") as stream:
            json.dump(state, stream, ensure_ascii=False)
        os.replace(temporary, path)
    except BaseException:
        with contextlib.suppress(OSError):
            os.unlink(temporary)
        raise


def measure(payload, state_entry):
    """(percent, tokens, window, [warnings still to be said]); percent is None when nothing could be measured."""
    warnings = []
    default, windows, config_warning = load_windows()
    if config_warning and not state_entry.get("config_warned"):
        state_entry["config_warned"] = True
        warnings.append(config_warning)
    path = payload.get("transcript_path")
    try:
        if not isinstance(path, str) or not path:
            raise ValueError("钩子输入没有 transcript_path")
        tokens, model = last_usage(path)
    except (NothingYet, FileNotFoundError):
        return None, 0, 0, warnings
    except (OSError, ValueError) as error:
        if not state_entry.get("transcript_warned"):
            state_entry["transcript_warned"] = True
            warnings.append(f"读不到本会话 transcript 的用量（{error}），上下文占比未知，交接提醒本轮不生效")
        return None, 0, 0, warnings
    window, model_warning = window_for(model, default, windows)
    if model_warning and state_entry.get("model_warned") != model:
        state_entry["model_warned"] = model
        warnings.append(model_warning)
    return tokens * 100.0 / window, tokens, window, warnings


def reminder(level, percent, tokens, window, name):
    where = f"sessions/{name}.md" if name else "sessions/<会话名>.md（本会话未识别，先在 .claude/session-name 写入会话名）"
    head = f"【上下文 {percent:.0f}%（{tokens}/{window}）】"
    if level >= 85:
        return head + f"立即交接：现在就更新 {where} 的上下文文档与交接，报 one-coordinator；不要 clear_session。"
    return head + f"到批次边界：更新 {where} 的上下文文档与交接，报 one-coordinator；不要 clear_session。"


def handle(event, payload, root, now=None):
    """The hook output (dict) for one event, or None. The state file is read, changed and written under a lock."""
    if payload.get("agent_id"):
        return None
    now = time.time() if now is None else now
    os.makedirs(os.path.join(root, ".claude"), exist_ok=True)
    with open(os.path.join(root, STATE + ".lock"), "w") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        return handle_locked(event, payload, root, now)


def handle_locked(event, payload, root, now):
    session = str(payload.get("session_id") or "unknown")
    state = load_state(root)
    entry = state.setdefault(session, {})
    entry["seen"] = now
    entry.setdefault("name", "")
    if not entry["name"] and ("name_checked" not in entry or now - float(entry["name_checked"]) >= NAME_RECHECK_SECONDS):
        entry["name"] = resolve_session(root)[0] or ""
        entry["name_checked"] = now
    pilot = pilot_sessions()
    out = None
    warnings = []
    if pilot is None or entry["name"] in pilot:
        percent, tokens, window, warnings = measure(payload, entry)
        if percent is not None:
            if percent < RESET_BELOW:
                for key in ("reminded", "blocked", "crossed"):
                    entry.pop(key, None)
            for level in LEVELS:
                if percent >= level:
                    entry.setdefault("crossed", {}).setdefault(str(level), now)
            top = max((level for level in LEVELS if percent >= level), default=0)
            if event == "Stop":
                if top and not payload.get("stop_hook_active"):
                    out = stop_decision(root, entry, top, percent, now)
            elif top and str(top) not in entry.setdefault("reminded", []):
                entry["reminded"].append(str(top))
                warnings.append(reminder(top, percent, tokens, window, entry["name"] or None))
    try:
        save_state(root, state)
    except OSError as error:
        print(f"context_handoff: 状态文件写不了（{error}），本次提醒照常输出，但同一档位可能重复提醒", file=sys.stderr)
    if event == "Stop":
        for text in warnings:
            print(f"context_handoff: {text}", file=sys.stderr)
        return out
    if warnings:
        return {"hookSpecificOutput": {"hookEventName": event, "additionalContext": "\n".join(warnings)}}
    return None


def stop_decision(root, entry, top, percent, now):
    blocked = entry.setdefault("blocked", [])
    if str(top) in blocked:
        return None
    crossed = float(entry["crossed"][str(top)])
    name, profile, docs = resolve_session(root)
    if not name:
        print("context_handoff: 本会话未识别（无画像），无法检查上下文文档，不挡停；请在 .claude/session-name 写入会话名", file=sys.stderr)
        return None
    modified = []
    for path in [profile, *docs]:
        full = os.path.join(root, path)
        if os.path.isfile(full):
            modified.append(os.path.getmtime(full))
    if modified and max(modified) >= crossed:
        return None
    blocked.append(str(top))
    todo = "、".join([profile, *docs])
    return {"decision": "block", "reason": f"上下文已用 {percent:.0f}%（过 {top}% 线），但 {todo} 自过线以来没有更新。"
            f"先更新上下文文档与交接（当前状态、在途、下一步），报 one-coordinator；不要 clear_session，写过之后可以直接结束。"}


def root_of(payload):
    # the desktop app starts a worktree session's hooks with CLAUDE_PROJECT_DIR = the main checkout; the session's own worktree is the hook input's cwd
    cwd = payload.get("cwd")
    candidates = [cwd] if isinstance(cwd, str) and cwd else []
    candidates += [os.environ.get("CLAUDE_PROJECT_DIR") or os.getcwd()]
    for start in candidates:
        top = subprocess.run(["git", "-C", start, "rev-parse", "--show-toplevel"], capture_output=True, text=True)
        if top.returncode == 0 and top.stdout.strip():
            return top.stdout.strip()
    return candidates[-1]


def main():
    event = "unknown"
    try:
        payload = json.load(sys.stdin)
        if not isinstance(payload, dict):
            raise ValueError("钩子输入不是对象")
        event = str(payload.get("hook_event_name") or "unknown")
        if event not in ("PostToolUse", "UserPromptSubmit", "Stop"):
            return 0
        result = handle(event, payload, root_of(payload))
        if result:
            print(json.dumps(result, ensure_ascii=False))
        return 0
    except Exception as error:
        message = f"context_handoff 出错（{error.__class__.__name__}: {error}），本次交接提醒不生效"
        if event in ("PostToolUse", "UserPromptSubmit"):
            print(json.dumps({"hookSpecificOutput": {"hookEventName": event, "additionalContext": message}}, ensure_ascii=False))
        else:
            print(message, file=sys.stderr)
        return 0


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as stream:
        stream.write(text)


def transcript(path, tokens, model="claude-test", sidechain_tokens=None):
    lines = [{"type": "user", "message": {"content": "hi"}}]
    lines.append({"type": "assistant", "isSidechain": False, "message": {"model": model, "usage": {"input_tokens": 3, "cache_read_input_tokens": tokens - 103, "cache_creation_input_tokens": 100}}})
    lines.append({"type": "user", "message": {"content": "tool result"}})
    if sidechain_tokens is not None:
        lines.append({"type": "assistant", "isSidechain": True, "message": {"model": model, "usage": {"input_tokens": sidechain_tokens}}})
    write(path, "\n".join(json.dumps(line) for line in lines) + "\n")


def make_repo(tmp, with_docs=True):
    root = os.path.join(tmp, "one-test")
    subprocess.run(["git", "init", "-q", root], check=True, capture_output=True)
    write(os.path.join(root, f"{SESSIONS}/one-test.md"), "# one-test\n\n## 上下文文档\n\n- `docs/ctx.md`\n\n## 当前状态\n\n无\n")
    if with_docs:
        write(os.path.join(root, "docs/ctx.md"), "ctx\n")
    return root


def self_test():
    global CONFIG
    original_config = CONFIG
    with tempfile.TemporaryDirectory() as tmp, contextlib.redirect_stderr(io.StringIO()) as quiet:
        config = os.path.join(tmp, "windows.json")
        write(config, json.dumps({"default": 200000, "windows": {"claude-test": 1000, "claude-long": 5000}}))
        CONFIG = config
        root = make_repo(tmp)
        trans = os.path.join(tmp, "t.jsonl")
        payload = {"session_id": "s1", "transcript_path": trans, "hook_event_name": "PostToolUse"}

        def fire(event, tokens, now, extra=None, model="claude-test", sidechain=None, session="s1"):
            transcript(trans, tokens, model, sidechain)
            data = dict(payload, hook_event_name=event, session_id=session, **(extra or {}))
            return handle(event, data, root, now)

        def text(output):
            return output["hookSpecificOutput"]["additionalContext"] if output else None

        assert fire("PostToolUse", 700, 100.0) is None, "below the line: silent"
        reminded = text(fire("PostToolUse", 760, 101.0))
        assert reminded and "到批次边界" in reminded and "sessions/one-test.md" in reminded and "76%" in reminded, reminded
        assert fire("PostToolUse", 770, 102.0) is None and fire("UserPromptSubmit", 780, 103.0) is None, "a level is said once"
        urgent = text(fire("UserPromptSubmit", 860, 104.0))
        assert urgent and "立即交接" in urgent and "到批次边界" not in urgent, urgent
        assert fire("PostToolUse", 900, 105.0) is None, "85 is also said once"
        assert fire("PostToolUse", 100, 106.0, session="other") is None, "another session starts clean"
        assert fire("PostToolUse", 760, 107.0, session="other", sidechain=990) is not None and fire("PostToolUse", 100, 108.0, session="third", sidechain=990) is None, "sub-agent messages are not counted"

        # Stop: documents older than the crossing block once, written documents let through
        os.utime(os.path.join(root, "docs/ctx.md"), (1, 1))
        os.utime(os.path.join(root, f"{SESSIONS}/one-test.md"), (1, 1))
        blocked = fire("Stop", 860, 200.0, session="stopper")
        assert blocked and blocked["decision"] == "block" and "docs/ctx.md" in blocked["reason"] and "不要 clear_session" in blocked["reason"], blocked
        assert fire("Stop", 860, 201.0, session="stopper") is None, "one block per level, never a loop"
        assert fire("Stop", 860, 202.0, session="stopper2", extra={"stop_hook_active": True}) is None, "stop_hook_active lets the stop through"
        assert fire("Stop", 700, 203.0, session="low") is None, "below the line does not block"
        os.utime(os.path.join(root, "docs/ctx.md"), (500.0, 500.0))
        assert fire("Stop", 860, 300.0, session="fresh") is None, "a document modified after the crossing lets the stop through"
        
        # pilot: only the listed sessions are measured
        write(config, json.dumps({"default": 200000, "windows": {"claude-test": 1000}, "pilot_sessions": ["one-ops"]}))
        assert fire("PostToolUse", 900, 600.0, session="pilot-out") is None and fire("Stop", 900, 601.0, session="pilot-out") is None, "outside the pilot nothing happens"
        write(config, json.dumps({"default": 200000, "windows": {"claude-test": 1000}, "pilot_sessions": ["one-test"]}))
        assert fire("PostToolUse", 900, 602.0, session="pilot-in") is not None, "inside the pilot it works"
        write(config, json.dumps({"default": 200000, "windows": {"claude-test": 1000, "claude-long": 5000}}))

        # the real flow: crossing seen by PostToolUse, documents written afterwards, then Stop
        fire("PostToolUse", 780, 1000.0, session="flow")
        os.utime(os.path.join(root, "docs/ctx.md"), (2000.0, 2000.0))
        os.utime(os.path.join(root, f"{SESSIONS}/one-test.md"), (1.0, 1.0))
        assert fire("Stop", 790, 1500.0, session="flow") is None, "context document written after the crossing"
        os.utime(os.path.join(root, "docs/ctx.md"), (900.0, 900.0))
        fire("PostToolUse", 780, 1000.0, session="flow2")
        assert fire("Stop", 790, 1500.0, session="flow2") is not None, "context document older than the crossing blocks"

        # missing window configuration and unreadable transcript say so, once
        unknown = text(fire("PostToolUse", 50, 400.0, model="claude-new", session="m1"))
        assert unknown and "claude-new" in unknown and "200000" in unknown, unknown
        assert fire("PostToolUse", 50, 401.0, model="claude-new", session="m1") is None, "said once per session and model"
        assert window_for("claude-long-1", 1, {"claude-long": 5000, "claude-lo": 9})[0] == 5000, "longest prefix wins"
        CONFIG = os.path.join(tmp, "missing.json")
        broken = text(fire("PostToolUse", 50, 402.0, session="m2"))
        assert broken and "读不了" in broken and "保守值" in broken, broken
        write(config, "{not json")
        CONFIG = config
        assert "读不了" in text(fire("PostToolUse", 50, 403.0, session="m3"))
        write(config, json.dumps({"default": 200000, "windows": {"claude-test": 1000}}))
        nope = os.path.join(tmp, "nope.jsonl")
        assert handle("PostToolUse", dict(payload, session_id="m4", transcript_path=nope), root, 404.0) is None, "a transcript that does not exist yet (new session) is silent"
        write(trans, "garbage\n{}\n")
        assert handle("PostToolUse", dict(payload, session_id="m5"), root, 406.0) is None, "no measurable message yet is silent"
        locked_transcript = os.path.join(tmp, "locked.jsonl")
        transcript(locked_transcript, 100)
        os.chmod(locked_transcript, 0)
        try:
            denied = text(handle("PostToolUse", dict(payload, session_id="m7", transcript_path=locked_transcript), root, 408.0))
            assert denied and "读不到" in denied, denied
            assert handle("PostToolUse", dict(payload, session_id="m7", transcript_path=locked_transcript), root, 409.0) is None, "said once"
        finally:
            os.chmod(locked_transcript, 0o600)
        assert "transcript_path" in text(handle("PostToolUse", {"session_id": "m6"}, root, 407.0))

        # sub-agent calls, synthetic messages, compaction, name re-resolution, state trouble
        assert fire("PostToolUse", 900, 600.0, extra={"agent_id": "a1"}, session="sub") is None and "sub" not in load_state(root), "a sub-agent call neither reminds nor marks the level"
        transcript(trans, 800)
        with open(trans, "a", encoding="utf-8") as stream:
            stream.write(json.dumps({"type": "assistant", "isSidechain": False, "message": {"model": "<synthetic>", "usage": {"input_tokens": 0}}}) + "\n")
        synthetic = text(handle("PostToolUse", dict(payload, session_id="syn"), root, 610.0))
        assert synthetic and "到批次边界" in synthetic and "80%" in synthetic, "a synthetic message at the end is skipped"
        assert fire("PostToolUse", 800, 620.0, session="compact") is not None
        assert fire("PostToolUse", 300, 621.0, session="compact") is None
        assert fire("PostToolUse", 800, 622.0, session="compact") is not None, "after a compaction the level is said again"
        os.remove(os.path.join(root, SESSIONS, "one-test.md"))
        late = fire("PostToolUse", 800, 700.0, session="late")
        assert "<会话名>" in text(late), "no profile yet: asked to write the session name"
        write(os.path.join(root, SESSIONS, "one-test.md"), "# one-test\n\n## 上下文文档\n\n- `docs/ctx.md`\n")
        assert fire("PostToolUse", 800, 701.0, session="late") is None
        late_entry = load_state(root)["late"]
        assert late_entry["name"] == "" and late_entry["name_checked"] == 700.0, "the name is looked up again only after a while"
        assert fire("PostToolUse", 100, 700.0 + NAME_RECHECK_SECONDS + 1, session="late") is None and load_state(root)["late"]["name"] == "one-test", "a later profile is found"
        write(os.path.join(root, STATE), json.dumps({"bad": [1, 2], "good": {"seen": 1}}))
        assert fire("PostToolUse", 800, 800.0, session="after-bad") is not None and "bad" not in load_state(root), "a malformed state entry is dropped"
        os.chmod(os.path.join(root, ".claude"), 0o500)
        try:
            quiet.truncate(0)
            quiet.seek(0)
            try:
                trouble = fire("PostToolUse", 800, 900.0, session="no-write")
            except OSError:
                trouble = "crashed"
            assert trouble not in (None, "crashed") and "状态文件写不了" in quiet.getvalue(), (trouble, quiet.getvalue())
        finally:
            os.chmod(os.path.join(root, ".claude"), 0o700)

        # concurrent hooks: every level reminded exactly once, no crash
        transcript(trans, 800)
        script_path = os.path.abspath(__file__)
        environment = {"LC_ALL": "C", "PATH": os.environ.get("PATH", ""), "CONTEXT_WINDOWS_CONFIG": config, "CLAUDE_PROJECT_DIR": root}
        jobs = [subprocess.Popen([sys.executable, "-I", script_path], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, env=environment)
                for _ in range(24)]
        results = [job.communicate(json.dumps(dict(payload, hook_event_name="PostToolUse", session_id=f"race-{n % 3}", cwd=root))) for n, job in enumerate(jobs)]
        assert all(job.returncode == 0 for job in jobs) and not any(err for _, err in results), results
        assert sum(1 for out, _ in results if "到批次边界" in out) == 3, "three sessions, one reminder each"
        assert len([key for key in load_state(root) if key.startswith("race-")]) == 3, "no session lost"

        # the lock itself: a hook waits while another process holds the state lock
        with open(os.path.join(root, STATE + ".lock"), "w") as held:
            fcntl.flock(held, fcntl.LOCK_EX)
            waiting = subprocess.Popen([sys.executable, "-I", script_path], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, env=environment)
            waiting.stdin.write(json.dumps(dict(payload, hook_event_name="PostToolUse", session_id="locked", cwd=root)))
            waiting.stdin.close()
            time.sleep(1.0)
            assert waiting.poll() is None, "the hook must wait for the state lock"
        assert waiting.wait(timeout=20) == 0, "and finish once it is released"
        waiting.stdout.close()
        waiting.stderr.close()

        # the project root is the session's own working directory; CLAUDE_PROJECT_DIR (the main checkout under the desktop app) is only the fallback
        elsewhere = os.path.join(tmp, "elsewhere")
        subprocess.run(["git", "init", "-q", elsewhere], check=True, capture_output=True)
        moved = subprocess.run([sys.executable, "-I", script_path], input=json.dumps(dict(payload, hook_event_name="PostToolUse", session_id="rooted", cwd=elsewhere)),
                               capture_output=True, text=True, env=environment)
        assert moved.returncode == 0 and "rooted" in load_state(elsewhere) and "rooted" not in load_state(root), moved
        main_checkout = os.path.join(tmp, "forcitis-one-psa")
        subprocess.run(["git", "init", "-q", main_checkout], check=True, capture_output=True)
        transcript(trans, 800)
        named = subprocess.run([sys.executable, "-I", script_path], input=json.dumps(dict(payload, hook_event_name="PostToolUse", session_id="named", cwd=root)),
                               capture_output=True, text=True, env=dict(environment, CLAUDE_PROJECT_DIR=main_checkout))
        assert named.returncode == 0 and "sessions/one-test.md" in json.loads(named.stdout)["hookSpecificOutput"]["additionalContext"], named
        assert "named" in load_state(root) and "named" not in load_state(main_checkout), "CLAUDE_PROJECT_DIR pointing at the main checkout must not decide the session"
        plain = os.path.join(tmp, "not-a-repository")
        os.makedirs(plain)
        for label, cwd in (("a directory outside any repository", plain), ("a directory that does not exist", os.path.join(tmp, "gone")), ("a number", 123)):
            outside = subprocess.run([sys.executable, "-I", script_path], input=json.dumps(dict(payload, hook_event_name="PostToolUse", session_id="outside", cwd=cwd)),
                                     capture_output=True, text=True, env=environment)
            assert outside.returncode == 0 and "outside" in load_state(root) and not os.path.exists(os.path.join(plain, STATE)), (label, outside)
            state_path = os.path.join(root, STATE)
            saved = load_state(root)
            saved.pop("outside", None)
            write(state_path, json.dumps(saved))
        fallback = subprocess.run([sys.executable, "-I", script_path], input=json.dumps(dict(payload, hook_event_name="PostToolUse", session_id="fallback")),
                                  capture_output=True, text=True, env=environment)
        assert fallback.returncode == 0 and "fallback" in load_state(root), fallback

        # session name: unrecognised session is reported, never blocks
        other = os.path.join(tmp, "somewhere-else")
        subprocess.run(["git", "init", "-q", other], check=True, capture_output=True)
        transcript(trans, 860)
        assert handle("Stop", dict(payload, session_id="x1"), other, 500.0) is None
        assert "<会话名>" in text(handle("PostToolUse", dict(payload, session_id="x2", hook_event_name="PostToolUse"), other, 501.0))

        # a crash does not block a stop
        original = globals()["measure"]
        globals()["measure"] = lambda *args: (_ for _ in ()).throw(RuntimeError("boom"))
        try:
            real_stdin, sys.stdin = sys.stdin, io.StringIO(json.dumps(dict(payload, hook_event_name="Stop", cwd=root)))
            try:
                assert main() == 0
            finally:
                sys.stdin = real_stdin
        finally:
            globals()["measure"] = original
        assert "boom" in quiet.getvalue()

        # end to end in a minimal environment: silent, reminder, block
        script = os.path.abspath(__file__)

        def run(event, tokens, extra=None, session="e2e"):
            transcript(trans, tokens)
            data = json.dumps(dict(payload, hook_event_name=event, session_id=session, cwd=root, **(extra or {})))
            return subprocess.run([sys.executable, "-I", script], input=data, capture_output=True, text=True,
                                  env={"LC_ALL": "C", "PATH": os.environ.get("PATH", ""), "CONTEXT_WINDOWS_CONFIG": config})

        quiet_run = run("PostToolUse", 100)
        assert quiet_run.returncode == 0 and not quiet_run.stdout, quiet_run
        loud_run = run("PostToolUse", 800)
        assert loud_run.returncode == 0 and json.loads(loud_run.stdout)["hookSpecificOutput"]["hookEventName"] == "PostToolUse", loud_run
        os.utime(os.path.join(root, "docs/ctx.md"), (1, 1))
        os.utime(os.path.join(root, f"{SESSIONS}/one-test.md"), (1, 1))
        stop_run = run("Stop", 800, session="e2e-stop")
        assert stop_run.returncode == 0 and json.loads(stop_run.stdout)["decision"] == "block", stop_run
        garbage = subprocess.run([sys.executable, "-I", script], input="not json", capture_output=True, text=True, env={"LC_ALL": "C"})
        assert garbage.returncode == 0 and "出错" in garbage.stderr + garbage.stdout, garbage
        other_event = subprocess.run([sys.executable, "-I", script], input=json.dumps({"hook_event_name": "SessionStart"}), capture_output=True, text=True, env={"LC_ALL": "C"})
        assert other_event.returncode == 0 and not other_event.stdout
    CONFIG = original_config
    real_default, real_windows, real_warning = load_windows(original_config)
    assert real_warning is None and real_default > 0, "the shipped window configuration must be readable"
    for model in ("claude-opus-5-5", "claude-sonnet-5-5", "claude-fable-5-1"):
        assert window_for(model, real_default, real_windows)[1] is None, f"the shipped configuration must cover {model}"
    print("context_handoff self-test: ok")
    return 0


if __name__ == "__main__":
    sys.exit(self_test() if sys.argv[1:] == ["--self-test"] else main())
