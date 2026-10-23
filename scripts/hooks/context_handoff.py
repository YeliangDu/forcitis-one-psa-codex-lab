#!/usr/bin/env python3
"""Hook (PostToolUse, UserPromptSubmit, Stop): hand a session over before its context fills up (SESSION_PLAYBOOK §2.1 #1, GOV-1021-QUA-01).

Measure: the usage of the last assistant message of the session's own transcript (hook input `transcript_path`, sub-agent messages skipped):
input + cache_read + cache_creation tokens, divided by the model's window from scripts/hooks/context_windows.json.
A model missing from the file is counted against the conservative `default` and says so once per session; an unreadable transcript or config says so once and does nothing else.
Remind (PostToolUse, UserPromptSubmit; reaches the model as additionalContext, so it only takes effect at the next tool call or prompt): at >= `context_levels.prepare` (shipped 30%, SESSION_PLAYBOOK §2.1 #1) "at the batch boundary update the context documents and hand over, report to one-coordinator" (a session outside the self-clear scope, i.e. the coordinator, is told not to clear_session; one inside it is told to clear itself, see below);
at >= `context_levels.handover` (shipped 40%) "hand over now". Each level once per session; state lives in the untracked <root>/.claude/context-handoff-state.json (a cleared session has a new id, so starts clean).
Enforce (Stop): past the prepare level while none of the profile / context documents (SESSION_PLAYBOOK §2.1 #16, same session-name resolution as session_context.py) was modified since the level was first reached, the stop is blocked once per level with the reason; a document written since lets it through.
Never a loop: `stop_hook_active` lets the stop through, and a level blocks at most once. A crash of this hook never blocks a stop; for the other events it is reported once as additionalContext.
Bulk (PostToolUse of Bash): a single command longer than `bash_input_chars` or a result longer than `bash_output_chars` (context_windows.json; defaults 5000 / 8000 from the length distribution of 1.6k Bash calls: about p96 / p98) is pointed out once per session and reason as additionalContext, never blocking (SESSION_PLAYBOOK §5 context-saving rules).
During the pilot `pilot_sessions` in context_windows.json limits the hook to the listed session names (null or absent = every session).
Self-clear (GOV-1022-COO-13 follow-up, user 2026-10-22): `node_handoff.sessions` (null = all) minus `node_handoff.exclude` is the scope whose sessions write the handover, send one-coordinator「清空接续」and clear_session(self) at a node and from the 30% / 40% reminders; sessions outside it (one-coordinator) are told not to clear_session.
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
LEVELS = (30, 40)
RESET_MARGIN = 15
FALLBACK_WINDOW = 200000
KEEP_SESSIONS = 20
NAME_RECHECK_SECONDS = 600
BASH_INPUT_CHARS = 5000
BASH_OUTPUT_CHARS = 8000
TAIL_BYTES = 4 * 1024 * 1024
SESSIONS = sc.SESSIONS
TASKS = os.path.dirname(SESSIONS) + "/tasks"
THROTTLE_SECONDS = 30
THROTTLE_BYTES = 150000
NODE_COOLDOWN_SECONDS = 600
NODE_MIN_PERCENT = 20


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


def context_levels(path=None):
    """((prepare, handover) percent, warning or None): `context_levels` in the configuration, two integers 1 <= prepare < handover <= 99 (SESSION_PLAYBOOK §2.1 #1: 30 / 40)."""
    try:
        with open(path or CONFIG, encoding="utf-8") as stream:
            data = json.load(stream)
        if "context_levels" not in data:
            return LEVELS, None
        value = data["context_levels"]
        prepare, handover = value["prepare"], value["handover"]
        if any(isinstance(x, bool) or not isinstance(x, int) for x in (prepare, handover)) or not 1 <= prepare < handover <= 99:
            raise ValueError(f"prepare / handover 必须是 1 <= prepare < handover <= 99 的整数，读到 {value!r}")
        return (prepare, handover), None
    except (OSError, ValueError, TypeError, AttributeError, KeyError) as error:
        return LEVELS, f"交接档位配置有问题（{error}），改用默认值 {LEVELS[0]}% / {LEVELS[1]}%"


def node_config(path=None):
    """(settings or None when the node-event handover is off, warning or None): `node_handoff` in the configuration, e.g.
    {"enabled": true, "min_percent": 20, "sessions": ["one-quality"] or null for every session, "exclude": ["one-coordinator"], "coordinator_session_ids": ["local_..."], "coordinator_names": ["one-coordinator"]}.
    A session in scope (listed or `sessions` null, and not in `exclude`) clears itself at a handover node and is told so by the 30% / 40% reminders; the others only hand over and are told not to clear_session.
    The coordinator (`coordinator_names`, `coordinator_session_ids`, and one-coordinator) is always outside the scope."""
    try:
        with open(path or CONFIG, encoding="utf-8") as stream:
            raw = json.load(stream).get("node_handoff")
        if raw is None or raw.get("enabled") is not True:
            return None, None
        percent = raw.get("min_percent", NODE_MIN_PERCENT)
        if isinstance(percent, bool) or not isinstance(percent, int) or not 1 <= percent <= 99:
            raise ValueError(f"min_percent 必须是 1..99 的整数，读到 {percent!r}")
        sessions = raw.get("sessions")
        if sessions is not None and not (isinstance(sessions, list) and all(isinstance(x, str) for x in sessions)):
            raise ValueError("sessions 必须是会话名数组或 null")
        exclude = raw.get("exclude", [])
        if not (isinstance(exclude, list) and all(isinstance(x, str) for x in exclude)):
            raise ValueError("exclude 必须是会话名数组")
        ids = raw.get("coordinator_session_ids", [])
        names = raw.get("coordinator_names", ["one-coordinator"])
        if not (isinstance(ids, list) and isinstance(names, list) and all(isinstance(x, str) for x in ids + names)):
            raise ValueError("coordinator_session_ids / coordinator_names 必须是字符串数组")
        return {"min_percent": percent, "sessions": sessions, "exclude": exclude, "ids": ids, "names": names}, None
    except (OSError, ValueError, TypeError, AttributeError) as error:
        return None, f"节点交接配置有问题（{error}），节点事件交接不生效"


COORDINATOR = "one-coordinator"


def clears_itself(config, name, session_id=None):
    """True when the session `name` is in the node-handover scope: it writes the handover, tells one-coordinator and clears itself. An unrecognised session never is,
    and the coordinator (by name or by session_id, whatever `exclude` says) never is."""
    if not (config and name):
        return False
    if name == COORDINATOR or name in config["names"] or name in config["exclude"] or (session_id and session_id in config["ids"]):
        return False
    return config["sessions"] is None or name in config["sessions"]


PR_CREATE = re.compile(r"(?:^|[;&|(\s/])gh\s+pr\s+(?:create|new)(?![\w-])")
GIT_PUSH = re.compile(r"(?:^|[;&|(\s/])git\s+(?:(?:-[cC]\s+\S+|--[\w-]+(?:=\S+)?)\s+)*push(?![\w-])")
GIT_COMMIT = re.compile(r"(?:^|[;&|(\s/])git\s+(?:(?:-[cC]\s+\S+|--[\w-]+(?:=\S+)?)\s+)*commit(?![\w-])")
MESSAGE_TOOLS = ("send_message", "SendMessage")
QUOTED = re.compile(r"\"[^\"]*\"|'[^']*'")


def task_done_in_head(root):
    """True when the last commit sets a tasks/*.md fragment's 状态 to 完成."""
    result = subprocess.run(["git", "-C", root, "show", "HEAD", "--format=", "-U0", "--", TASKS], capture_output=True, text=True, encoding="utf-8", errors="replace")
    return result.returncode == 0 and re.search(r"^\+状态:\s*完成\s*$", result.stdout, re.M) is not None


def turn_used_tools(path):
    """True when the assistant ran a tool since the last real user message (a user message that is not just tool results)."""
    try:
        size = os.path.getsize(path)
        with open(path, "rb") as stream:
            stream.seek(max(0, size - TAIL_BYTES))
            lines = stream.read().split(b"\n")
    except (OSError, TypeError):
        return False
    used = False
    for raw in reversed(lines):
        try:
            entry = json.loads(raw)
        except ValueError:
            continue
        message = entry.get("message") if isinstance(entry, dict) else None
        if not isinstance(message, dict) or entry.get("isSidechain"):
            continue
        content = message.get("content")
        if entry.get("type") == "assistant" and isinstance(content, list) and any(isinstance(c, dict) and c.get("type") == "tool_use" for c in content):
            used = True
        if entry.get("type") == "user":
            if isinstance(content, str) or (isinstance(content, list) and any(isinstance(c, dict) and c.get("type") != "tool_result" for c in content)):
                return used
    return used


def node_of(payload, event, root, config):
    """The handover node this event is, or None: Stop (a turn ended), a Bash that ran `gh pr create` / `git push`, a commit that set a task to 完成,
    a message to one-coordinator that is not itself the clear notice."""
    if event == "Stop":
        return "一轮结束" if turn_used_tools(payload.get("transcript_path")) else None  # a turn that only answered a question is not a node
    if event != "PostToolUse":
        return None
    tool = str(payload.get("tool_name") or "")
    tool_input = payload.get("tool_input") if isinstance(payload.get("tool_input"), dict) else {}
    if tool == "Bash":
        command = tool_input.get("command")
        if not isinstance(command, str):
            return None
        command = QUOTED.sub(" ", command)  # text inside quotes (a commit message, an echo) is not a command
        if PR_CREATE.search(command):
            return "已开 PR"
        if GIT_PUSH.search(command) and "--dry-run" not in command:
            return "已推送"
        if GIT_COMMIT.search(command) and task_done_in_head(root):
            return "任务置为完成"
        return None
    if tool.endswith(MESSAGE_TOOLS):
        target = tool_input.get("session_id") or tool_input.get("to")
        text = str(tool_input.get("message") or "")
        if "清空接续" in text:
            return None
        if target in config["ids"] or target in config["names"]:
            return "已向 one-coordinator 报告"
    return None


def node_instruction(node, percent, name, profile, docs, goals=""):
    target = "one-merge" if name == "one-coordinator" else "one-coordinator"
    where = "、".join([profile or "sessions/<会话名>.md", *docs])
    extra = "（调度自清：one-merge 收到后用 set_remote_control 重开调度的远程并发『接续』）" if name == "one-coordinator" else ""
    return (f"【交接节点】上下文 {percent:.0f}%，刚到节点：{node}。现在按顺序做，本轮不再做别的："
            f"①更新 {where}（上下文文档与交接，写明下一步和未完成的用户请求）；②提交并推送；"
            f"③给 {target} 发消息「清空接续：{name or '<会话名>'} 交接在 <交接文件路径>」{extra}；"
            f"④调用 clear_session（session_id=self）。清空在本轮结束后才执行：此后不要再发消息或起任务，否则排队消息会让清空被丢弃；"
            f"{target} 收到后须先 get_session 确认本会话已空闲且已清空，再发『接续』。发出「清空接续」后，其他会话找本会话一律经 {target} 转，不直接发给本会话（清空中收到消息会顶掉清空）。"
            + (f"\n{goals}" if goals else ""))


def throttle_limits(path=None):
    """(seconds, bytes) of the PostToolUse throttle: `throttle_seconds` / `throttle_bytes` in the configuration, integers >= 0 (0 seconds = no throttle)."""
    limits = {"throttle_seconds": THROTTLE_SECONDS, "throttle_bytes": THROTTLE_BYTES}
    try:
        with open(path or CONFIG, encoding="utf-8") as stream:
            data = json.load(stream)
        for key in limits:
            if key in data:
                if isinstance(data[key], bool) or not isinstance(data[key], int) or data[key] < 0:
                    return (THROTTLE_SECONDS, THROTTLE_BYTES)
                limits[key] = data[key]
    except (OSError, ValueError, AttributeError):
        pass
    return limits["throttle_seconds"], limits["throttle_bytes"]


def pilot_sessions(path=None):
    """Session names the hook is limited to during the pilot, or None for every session (`pilot_sessions` in the configuration)."""
    try:
        with open(path or CONFIG, encoding="utf-8") as stream:
            listed = json.load(stream).get("pilot_sessions")
        return [str(item) for item in listed] if isinstance(listed, list) else None
    except (OSError, ValueError, AttributeError):
        return None


def bulk_limits(path=None):
    """((input chars, output chars), warning or None) above which a single Bash call is pointed out; `bash_input_chars` / `bash_output_chars` in the configuration, positive integers."""
    limits = {"bash_input_chars": BASH_INPUT_CHARS, "bash_output_chars": BASH_OUTPUT_CHARS}
    try:
        with open(path or CONFIG, encoding="utf-8") as stream:
            data = json.load(stream)
        for key in limits:
            if key in data:
                value = data[key]
                if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
                    raise ValueError(f"{key} 必须是正整数，读到 {value!r}")
                limits[key] = value
        return (limits["bash_input_chars"], limits["bash_output_chars"]), None
    except (OSError, ValueError, TypeError, AttributeError) as error:
        return (BASH_INPUT_CHARS, BASH_OUTPUT_CHARS), f"Bash 超长阈值配置有问题（{error}），改用默认值 {BASH_INPUT_CHARS} / {BASH_OUTPUT_CHARS}"


def bash_output_size(response):
    """Characters of what the model sees of a Bash result: a string as it is, or stdout + stderr of the result object; none for an image or an output Claude Code already stored in a file (it passes only a preview then)."""
    if isinstance(response, str):
        return len(response)
    if not isinstance(response, dict) or response.get("isImage") or response.get("persistedOutputPath"):
        return 0
    return sum(len(response[key]) for key in ("stdout", "stderr") if isinstance(response.get(key), str))


def bulk_reminders(payload, entry):
    """One-time notes (per session and reason) on a single oversized Bash input or result; only PostToolUse of Bash, never blocking."""
    if payload.get("hook_event_name") != "PostToolUse" or payload.get("tool_name") != "Bash":
        return []
    (limit_in, limit_out), problem = bulk_limits()
    notes = []
    if problem and not entry.get("limits_warned"):
        entry["limits_warned"] = True
        notes.append(problem)
    seen = entry.setdefault("bulk", [])
    tool_input = payload.get("tool_input")
    command = tool_input.get("command") if isinstance(tool_input, dict) else None
    size_in = len(command) if isinstance(command, str) else 0
    if size_in > limit_in and "input" not in seen:
        seen.append("input")
        notes.append(f"【上下文节约】本次 Bash 命令输入 {size_in} 字符（阈值 {limit_in}）：改文件用 Edit 改片段，不要用脚本整篇重写；新文件用 Write，长正文先写文件再用 --body-file 或 < 传入（SESSION_PLAYBOOK §5）。")
    size_out = bash_output_size(payload.get("tool_response"))
    if size_out > limit_out and "output" not in seen:
        seen.append("output")
        notes.append(f"【上下文节约】本次 Bash 输出 {size_out} 字符（阈值 {limit_out}）：先统计、筛选再读具体段，长输出存文件按需读；报错与测试失败的输出要完整读，不要用 head/tail 截掉（SESSION_PLAYBOOK §5）。")
    return notes


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
    source = sc.handoff_source(profile)
    if source and source[0] not in docs and not sc.unsafe_reason(root, source[0]):
        docs.append(source[0])  # the handover source counts for the node instruction and the stop gate even when it is not a listed context document
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


def reminder(level, percent, tokens, window, name, handover=LEVELS[1], clears=False):
    where = f"sessions/{name}.md" if name else "sessions/<会话名>.md（本会话未识别，先在 .claude/session-name 写入会话名）"
    head = f"【上下文 {percent:.0f}%（{tokens}/{window}）】"
    if clears:
        steps = (f"更新 {where} 的上下文文档与交接并提交推送，给 one-coordinator 发「清空接续：{name} 交接在 <交接文件路径>」，再调用 clear_session（session_id=self）；"
                 "之后其他会话找本会话一律经 one-coordinator 转")
        return head + ("立即交接：现在就" if level >= handover else "到批次边界：") + steps + "。"
    if level >= handover:
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
    (prepare, handover), level_warning = context_levels()
    levels = (prepare, handover)
    out = None
    warnings = []
    early = [level_warning] if level_warning and not entry.get("level_warned") else []
    if level_warning:
        entry["level_warned"] = True
    if not entry["name"] and event != "Stop" and not entry.get("noname_warned"):
        entry["noname_warned"] = True
        early.append("【会话未识别】没有 .claude/session-name，也无法从工作区目录名或分支名认出会话画像：上下文档位提醒、提交信息的 `Session:` 都用不上；先在 .claude/session-name 写入会话名（SESSION_PLAYBOOK §2.1）。")
    if pilot is None or entry["name"] in pilot:
        percent, tokens, window, warnings = measure(payload, entry)
        warnings += bulk_reminders(dict(payload, hook_event_name=event), entry)
        if percent is not None:
            if percent < max(1, prepare - RESET_MARGIN):
                if "crossed" in entry:
                    entry.pop("bulk", None)
                for key in ("reminded", "blocked", "crossed"):
                    entry.pop(key, None)
            for level in levels:
                if percent >= level:
                    entry.setdefault("crossed", {}).setdefault(str(level), now)
            top = max((level for level in levels if percent >= level), default=0)
            node_out = None
            config, config_problem = node_config()
            if config_problem and not entry.get("node_config_warned"):
                entry["node_config_warned"] = True
                early.append(config_problem)
            clears = clears_itself(config, entry["name"], session)
            if clears and percent >= config["min_percent"] \
                    and now - float(entry.get("node_at", 0)) >= NODE_COOLDOWN_SECONDS and not payload.get("stop_hook_active"):
                node = node_of(payload, event, root, config)
                if node:
                    entry["node_at"] = now
                    resolved = resolve_session(root)
                    try:
                        sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
                        import start_sheet
                        goals = start_sheet.goals_for_node(root, resolved[0] or entry["name"] or "")
                    except Exception as error:  # the goals are a courtesy; the instruction itself must still go out, saying they are missing
                        goals = f"（任务卡的目标与完成标准读取失败：{type(error).__name__}，自己读 docs/modernization/tasks/ 里本会话的在途卡）"
                    text = node_instruction(node, percent, resolved[0] or entry["name"] or None, resolved[1], resolved[2], goals)
                    if event == "Stop":
                        node_out = {"decision": "block", "reason": text}
                    else:
                        warnings.append(text)
            if event == "Stop":
                if node_out:
                    out = node_out
                elif top and not payload.get("stop_hook_active"):
                    out = stop_decision(root, entry, top, percent, now, clears)
            elif top and str(top) not in entry.setdefault("reminded", []):
                entry["reminded"].append(str(top))
                warnings.append(reminder(top, percent, tokens, window, entry["name"] or None, handover, clears))
            if event == "PostToolUse":
                entry["checked"] = now
                entry["checked_size"] = transcript_size(payload)
    try:
        save_state(root, state)
    except OSError as error:
        print(f"context_handoff: 状态文件写不了（{error}），本次提醒照常输出，但同一档位可能重复提醒", file=sys.stderr)
    warnings = early + warnings
    if event == "Stop":
        for text in warnings:
            print(f"context_handoff: {text}", file=sys.stderr)
        return out
    if warnings:
        return {"hookSpecificOutput": {"hookEventName": event, "additionalContext": "\n".join(warnings)}}
    return None


def stop_decision(root, entry, top, percent, now, clears=False):
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
            + ("先更新上下文文档与交接（做到哪、在途、下一步）并提交推送，给 one-coordinator 发「清空接续：" + name + " 交接在 <路径>」，再 clear_session（session_id=self）；清空在本轮结束后执行，之后不再发消息，其他会话找本会话一律经 one-coordinator 转。"
               if clears else "先更新上下文文档与交接（做到哪、在途、下一步），报 one-coordinator；不要 clear_session，写过之后可以直接结束。")}


def transcript_size(payload):
    path = payload.get("transcript_path")
    try:
        return os.path.getsize(path) if isinstance(path, str) and path else -1
    except OSError:
        return -1


def can_skip(payload, root, now):
    """Throttle for PostToolUse (SESSION_PLAYBOOK §5, GOV-1022-COO-08): skip the transcript parse and the state write when the session was measured less than
    THROTTLE_SECONDS ago and its transcript grew by less than THROTTLE_BYTES since. Never skipped: Stop / UserPromptSubmit, sub-agent calls (they return at once anyway),
    a session with no measurement yet, an oversized Bash input/result (the bulk note), and every possible handover node (when node events are on)."""
    if payload.get("hook_event_name") != "PostToolUse" or payload.get("agent_id"):
        return False
    state = load_state(root).get(str(payload.get("session_id") or "unknown"))
    if not state or "checked" not in state or not state.get("name") and now - float(state["checked"]) >= NAME_RECHECK_SECONDS:
        return False
    seconds, grown = throttle_limits()
    if now - float(state["checked"]) >= seconds:
        return False
    size = transcript_size(payload)
    if size < 0 or size - int(state.get("checked_size", 0)) >= grown or size < int(state.get("checked_size", 0)):
        return False
    tool_input = payload.get("tool_input") if isinstance(payload.get("tool_input"), dict) else {}
    if payload.get("tool_name") == "Bash":
        (limit_in, limit_out), _ = bulk_limits()
        command = tool_input.get("command")
        if (isinstance(command, str) and len(command) > limit_in) or bash_output_size(payload.get("tool_response")) > limit_out:
            return False
    config, _ = node_config()
    if config and node_of(payload, "PostToolUse", root, config):
        return False
    return True


def root_of(payload):
    # the desktop app starts a worktree session's hooks with CLAUDE_PROJECT_DIR = the main checkout; the session's own worktree is the hook input's cwd
    cwd = payload.get("cwd")
    candidates = [cwd] if isinstance(cwd, str) and cwd else []
    candidates += [os.environ.get("CLAUDE_PROJECT_DIR") or os.getcwd()]
    for start in candidates:
        directory = os.path.realpath(start)
        while True:  # the nearest directory with .git (a file in a worktree): what `git rev-parse --show-toplevel` answers, without starting git
            if os.path.exists(os.path.join(directory, ".git")):
                return directory
            parent = os.path.dirname(directory)
            if parent == directory:
                break
            directory = parent
    return candidates[-1]


def status_gate(payload, root):
    """The Stop status-declaration gate (stop_status_gate.py, GOV-1023-QUA-01) as a hook output or None; its own failure only warns, never blocks a stop."""
    try:
        import stop_status_gate
        config, _ = node_config()
        targets = set(config["ids"] + config["names"] + [COORDINATOR]) if config else {COORDINATOR}
        output, warnings = stop_status_gate.gate(payload, root, resolve_session(root)[0], targets, time.time())
        for text in warnings:
            print(text, file=sys.stderr)
        return output
    except Exception as error:
        print(f"stop_status_gate 出错（{error.__class__.__name__}: {error}），状态声明检查本次不生效", file=sys.stderr)
        return None


def main():
    event = "unknown"
    try:
        payload = json.load(sys.stdin)
        if not isinstance(payload, dict):
            raise ValueError("钩子输入不是对象")
        event = str(payload.get("hook_event_name") or "unknown")
        if event not in ("PostToolUse", "UserPromptSubmit", "Stop"):
            return 0
        root = root_of(payload)
        if event == "Stop":
            blocked = status_gate(payload, root)
            if blocked:
                print(json.dumps(blocked, ensure_ascii=False))
                return 0
        if can_skip(dict(payload, hook_event_name=event), root, time.time()):
            return 0
        result = handle(event, payload, root)
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


def transcript(path, tokens, model="claude-test", sidechain_tokens=None, final_text="好了\n状态：继续"):
    lines = [{"type": "user", "message": {"content": "hi"}}]
    lines.append({"type": "assistant", "isSidechain": False, "message": {"model": model, "content": [{"type": "text", "text": final_text}],
                                                                           "usage": {"input_tokens": 3, "cache_read_input_tokens": tokens - 103, "cache_creation_input_tokens": 100}}})
    lines.append({"type": "user", "message": {"content": [{"type": "tool_result", "content": "tool result"}]}})
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
    global CONFIG, LEVELS
    original_config, original_levels = CONFIG, LEVELS
    LEVELS = (75, 85)  # the scenarios below are written against 75 / 85; the configured 30 / 40 are exercised in their own block
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

        # oversized Bash input / result: pointed out once per session and reason, only for Bash, never blocking
        write(config, json.dumps({"default": 200000, "windows": {"claude-test": 1000, "claude-long": 5000}, "bash_input_chars": 100, "bash_output_chars": 200}))
        small = fire("PostToolUse", 100, 1100.0, extra={"tool_name": "Bash", "tool_input": {"command": "x" * 100}, "tool_response": {"stdout": "y" * 150, "stderr": "z" * 50}}, session="bulk")
        assert small is None, "at the limit is not over it"
        big_out = text(fire("PostToolUse", 100, 1101.0, extra={"tool_name": "Bash", "tool_input": {"command": "ls"}, "tool_response": {"stdout": "y" * 150, "stderr": "z" * 51}}, session="bulk"))
        assert big_out and "Bash 输出 201 字符" in big_out and "存文件按需读" in big_out and "完整读" in big_out, big_out
        assert fire("PostToolUse", 100, 1102.0, extra={"tool_name": "Bash", "tool_input": {"command": "ls"}, "tool_response": "y" * 900}, session="bulk") is None, "the same reason is not repeated"
        big_in = text(fire("PostToolUse", 100, 1103.0, extra={"tool_name": "Bash", "tool_input": {"command": "x" * 101}, "tool_response": "ok"}, session="bulk"))
        assert big_in and "Bash 命令输入 101 字符" in big_in and "Edit" in big_in and "输出" not in big_in, big_in
        assert fire("PostToolUse", 100, 1103.5, extra={"tool_name": "Bash", "tool_input": {"command": "x" * 900}, "tool_response": "ok"}, session="bulk") is None, "input is said once too"
        both = text(fire("PostToolUse", 100, 1104.0, extra={"tool_name": "Bash", "tool_input": {"command": "x" * 500}, "tool_response": "y" * 300}, session="bulk2"))
        assert both and "命令输入 500" in both and "输出 300" in both, "a list response is measured and both reasons come together"
        for label, shape in (("a diff the model never sees", {"stdout": "", "stderr": "", "bashEditDiff": "d" * 5000}),
                             ("an image", {"stdout": "b" * 5000, "stderr": "", "isImage": True}),
                             ("an output stored in a file", {"stdout": "o" * 5000, "stderr": "", "persistedOutputPath": "/tmp/x"}),
                             ("a non-text response", 12345)):
            assert fire("PostToolUse", 100, 1104.5, extra={"tool_name": "Bash", "tool_input": {"command": "ls"}, "tool_response": shape}, session="shape-" + label) is None, label
        assert fire("UserPromptSubmit", 100, 1104.6, extra={"tool_name": "Bash", "tool_input": {"command": "x" * 900}, "tool_response": "y" * 900}, session="bulk9") is None, "only PostToolUse is pointed out"
        write(config, json.dumps({"default": 200000, "windows": {"claude-test": 1000}, "bash_input_chars": 0, "bash_output_chars": "many"}))
        invalid = text(fire("PostToolUse", 100, 1104.7, extra={"tool_name": "Bash", "tool_input": {"command": "ls"}, "tool_response": "ok"}, session="badlimits"))
        assert invalid and "阈值配置有问题" in invalid and "默认值" in invalid, invalid
        assert fire("PostToolUse", 100, 1104.8, extra={"tool_name": "Bash", "tool_input": {"command": "ls"}, "tool_response": "ok"}, session="badlimits") is None, "said once"
        write(config, json.dumps({"default": 200000, "windows": {"claude-test": 1000, "claude-long": 5000}, "bash_input_chars": 100, "bash_output_chars": 200}))
        loud = {"tool_name": "Bash", "tool_input": {"command": "ls"}, "tool_response": "y" * 300}
        assert fire("PostToolUse", 100, 1104.9, extra=loud, session="again") is not None and fire("PostToolUse", 100, 1104.95, extra=loud, session="again") is None
        fire("PostToolUse", 100, 1104.96, session="again")
        assert fire("PostToolUse", 100, 1104.97, extra=loud, session="again") is None, "below 60% without a crossing does not reset the bulk notes"
        fire("PostToolUse", 800, 1104.98, session="again")
        fire("PostToolUse", 100, 1104.99, session="again")
        assert fire("PostToolUse", 100, 1105.0, extra=loud, session="again") is not None, "after a crossing and a compaction the bulk note is said again"
        assert fire("PostToolUse", 100, 1105.1, extra={"tool_name": "Read", "tool_input": {"file_path": "x" * 900}, "tool_response": "y" * 900}, session="bulk3") is None, "other tools are not pointed out"
        assert fire("PostToolUse", 100, 1106.0, extra={"tool_name": "Bash", "tool_input": {"command": "x" * 900}, "tool_response": "y" * 900, "agent_id": "a"}, session="bulk4") is None, "sub-agent calls are not pointed out"
        assert fire("Stop", 100, 1107.0, extra={"tool_name": "Bash", "tool_input": {"command": "x" * 900}, "tool_response": "y" * 900}, session="bulk5") is None
        assert fire("PostToolUse", 100, 1108.0, extra={"tool_name": "Bash", "tool_input": None, "tool_response": None}, session="bulk6") is None, "missing input / response are not an error"
        write(config, json.dumps({"default": 200000, "windows": {"claude-test": 1000, "claude-long": 5000}, "bash_input_chars": 100, "bash_output_chars": 200, "pilot_sessions": ["one-ops"]}))
        assert fire("PostToolUse", 100, 1109.0, extra={"tool_name": "Bash", "tool_input": {"command": "x" * 900}, "tool_response": "y" * 900}, session="bulk7") is None, "the pilot limit applies to the bulk notes too"
        write(config, json.dumps({"default": 200000, "windows": {"claude-test": 1000, "claude-long": 5000}}))
        defaults = text(fire("PostToolUse", 100, 1110.0, extra={"tool_name": "Bash", "tool_input": {"command": "x" * (BASH_INPUT_CHARS + 1)}, "tool_response": "ok"}, session="bulk8"))
        assert defaults and f"阈值 {BASH_INPUT_CHARS}" in defaults, "without configured limits the defaults apply"

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
        write(config, json.dumps({"default": 200000, "windows": {"claude-test": 1000}, "context_levels": {"prepare": 75, "handover": 85}, "throttle_seconds": 0}))
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
        unnamed = text(handle("PostToolUse", dict(payload, session_id="x2", hook_event_name="PostToolUse"), other, 501.0))
        assert "<会话名>" in unnamed and "【会话未识别】" in unnamed, "an unidentified session is told so once, with the reminder"
        assert "【会话未识别】" not in (text(handle("PostToolUse", dict(payload, session_id="x2", hook_event_name="PostToolUse"), other, 502.0)) or ""), "said once per session"

        # a crash does not block a stop
        transcript(trans, 100)
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
        # the status gate (GOV-1023-QUA-01) comes first on Stop, end to end through the one hook entry
        psa = os.path.join(tmp, "psa")
        gate_env = {"LC_ALL": "C", "PATH": os.environ.get("PATH", ""), "CONTEXT_WINDOWS_CONFIG": config, "MODERNIZE_PSA_HOME": psa}

        def gate_run(final_text, session="gate-e2e", extra=None):
            transcript(trans, 100, final_text=final_text)
            data = json.dumps(dict(payload, hook_event_name="Stop", session_id=session, cwd=root, **(extra or {})))
            return subprocess.run([sys.executable, "-I", script], input=data, capture_output=True, text=True, env=gate_env)

        undeclared = gate_run("好了")
        assert undeclared.returncode == 0 and json.loads(undeclared.stdout)["decision"] == "block" and "状态：继续" in json.loads(undeclared.stdout)["reason"], undeclared
        unsent = gate_run("报告\n状态：等调度")
        assert json.loads(unsent.stdout)["decision"] == "block" and not os.path.exists(os.path.join(psa, "mailbox")), unsent
        assert not gate_run("好了\n状态：继续").stdout, "a declared 继续 passes"
        fallback = gate_run("报告\n状态：等调度", session="gate-e2e-2", extra={"stop_hook_active": True})
        assert not fallback.stdout and "信箱兜底" in fallback.stderr and len(os.listdir(os.path.join(psa, "mailbox"))) == 1, fallback
        garbage = subprocess.run([sys.executable, "-I", script], input="not json", capture_output=True, text=True, env={"LC_ALL": "C"})
        assert garbage.returncode == 0 and "出错" in garbage.stderr + garbage.stdout, garbage
        other_event = subprocess.run([sys.executable, "-I", script], input=json.dumps({"hook_event_name": "SessionStart"}), capture_output=True, text=True, env={"LC_ALL": "C"})
        assert other_event.returncode == 0 and not other_event.stdout
        # configured levels (shipped: 30 / 40): read from the configuration, validated, the reset margin follows the lower one
        LEVELS = original_levels
        write(config, json.dumps({"default": 200000, "windows": {"claude-test": 1000}, "context_levels": {"prepare": 30, "handover": 40}}))
        assert context_levels(config) == ((30, 40), None)
        assert fire("PostToolUse", 250, 2000.0, session="lv") is None, "below 30% is silent"
        low = text(fire("PostToolUse", 310, 2001.0, session="lv"))
        assert low and "到批次边界" in low and "31%" in low, low
        high = text(fire("PostToolUse", 410, 2002.0, session="lv"))
        assert high and "立即交接" in high, high
        fire("PostToolUse", 100, 2003.0, session="lv")
        assert text(fire("PostToolUse", 320, 2004.0, session="lv")) is not None, "after a compaction below the reset margin the level is said again"
        write(config, json.dumps({"default": 200000, "windows": {"claude-test": 1000}}))
        assert context_levels(config) == (original_levels, None), "without a configured value the built-in levels apply"
        for bad in ({"prepare": 40, "handover": 30}, {"prepare": 30}, {"prepare": "30", "handover": 40}, {"prepare": True, "handover": 40}, {"prepare": 0, "handover": 40}, {"prepare": 30, "handover": 100}, [30, 40]):
            write(config, json.dumps({"default": 200000, "windows": {"claude-test": 1000}, "context_levels": bad}))
            levels_read, warning = context_levels(config)
            assert levels_read == original_levels and warning and "交接档位配置有问题" in warning, (bad, warning)
        write(config, json.dumps({"default": 200000, "windows": {"claude-test": 1000}, "context_levels": {"prepare": 40, "handover": 30}}))
        assert "交接档位配置有问题" in text(fire("PostToolUse", 100, 2005.0, session="lvbad")), "a broken level configuration is said once"
        assert fire("PostToolUse", 100, 2006.0, session="lvbad") is None
        # node events (GOV-1022-COO-09): handover instruction at a node when the context is >= min_percent, once per cooldown
        LEVELS = (75, 85)
        node_cfg = {"enabled": True, "min_percent": 20, "sessions": ["one-test"], "coordinator_session_ids": ["local_coord"]}
        write(config, json.dumps({"default": 200000, "windows": {"claude-test": 1000}, "node_handoff": node_cfg}))
        assert node_config(config)[0] == {"min_percent": 20, "sessions": ["one-test"], "exclude": [], "ids": ["local_coord"], "names": ["one-coordinator"]}
        assert node_config(os.path.join(tmp, "absent.json"))[0] is None, "an unreadable configuration turns node events off"
        for bad_cfg in ({**node_cfg, "min_percent": 0}, {**node_cfg, "min_percent": "20"}, {**node_cfg, "sessions": "one-test"}, {**node_cfg, "coordinator_session_ids": [1]}):
            write(config, json.dumps({"windows": {}, "node_handoff": bad_cfg}))
            assert node_config(config)[0] is None and "节点交接配置有问题" in node_config(config)[1], bad_cfg
        scope = {**node_cfg, "sessions": None, "exclude": ["one-coordinator"]}
        write(config, json.dumps({"default": 200000, "windows": {"claude-test": 1000}, "node_handoff": scope}))
        scoped = node_config(config)[0]
        assert scoped["exclude"] == ["one-coordinator"] and scoped["sessions"] is None
        assert clears_itself(scoped, "one-ops") and clears_itself(scoped, "one-newcomer"), "every session but the excluded ones clears itself"
        assert not clears_itself(scoped, "one-coordinator") and not clears_itself(scoped, "") and not clears_itself(None, "one-ops"), "excluded, unrecognised or switched-off: no self-clear"
        assert not clears_itself({**scoped, "sessions": ["one-a"]}, "one-ops") and clears_itself({**scoped, "sessions": ["one-ops"]}, "one-ops")
        assert node_config(config)[0] is not None
        assert not clears_itself({**scoped, "exclude": [], "names": []}, "one-coordinator"), "the coordinator is excluded even when the configuration forgets it"
        write(config, json.dumps({"default": 200000, "windows": {"claude-test": 1000}, "node_handoff": {**scope, "exclude": "one-coordinator"}}))
        assert node_config(config)[0] is None and "exclude" in node_config(config)[1]
        low = reminder(30, 31, 310, 1000, "one-ops", 40, clears=True)
        assert "清空接续：one-ops 交接在" in low and "clear_session（session_id=self）" in low and "经 one-coordinator 转" in low and "不要 clear_session" not in low, low
        assert "立即交接" in reminder(40, 41, 410, 1000, "one-ops", 40, clears=True) and "不要 clear_session" in reminder(40, 41, 410, 1000, "one-coordinator", 40)
        assert "经 one-coordinator 转" in node_instruction("已开 PR", 25, "one-ops", "p.md", ["d.md"]), "the protocol rule rides with the clear instruction"
        write(config, json.dumps({"default": 200000, "windows": {"claude-test": 1000}, "node_handoff": {**node_cfg, "enabled": False}}))
        assert node_config(config) == (None, None), "off unless enabled is true"
        write(config, json.dumps({"default": 200000, "windows": {"claude-test": 1000}, "node_handoff": node_cfg}))
        bash = lambda command: {"tool_name": "Bash", "tool_input": {"command": command}, "tool_response": "ok"}
        assert fire("PostToolUse", 150, 3000.0, session="nd1", extra=bash("gh pr create --title x")) is None, "below min_percent: no node instruction"
        said = text(fire("PostToolUse", 250, 3001.0, session="nd1", extra=bash("cd w && gh pr create --title x")))
        assert said and "【交接节点】" in said and "已开 PR" in said and "clear_session" in said and "one-coordinator" in said and "get_session" in said and "25%" in said, said
        assert node_instruction("已开 PR", 25, "one-x", "p.md", ["d.md"], "目标：GOALS").endswith("\n目标：GOALS") and "GOALS" not in node_instruction("已开 PR", 25, "one-x", "p.md", ["d.md"]), "task goals ride along only when there are some"
        assert fire("PostToolUse", 250, 3002.0, session="nd1", extra=bash("git push origin b")) is None, "cooldown: said once per 10 minutes"
        again = text(fire("PostToolUse", 250, 3001.0 + NODE_COOLDOWN_SECONDS + 1, session="nd1", extra=bash("git -C /w push -u origin b")))
        assert again and "已推送" in again, again
        for command in ("git commit -m \"修 git push 守护\"", "echo 'x gh pr create'", "git push --dry-run", "git pushd", "git status", "ls", "echo 'gh pr create'", "gh pr view 3", "gh pr checks 3"):
            assert fire("PostToolUse", 250, 4000.0, session="nd-" + command, extra=bash(command)) is None, command
        sent = lambda target, message="状态": {"tool_name": "mcp__ccd_session_mgmt__send_message", "tool_input": {"session_id": target, "message": message}}
        assert "已向 one-coordinator 报告" in text(fire("PostToolUse", 250, 4001.0, session="nd2", extra=sent("local_coord")))
        assert fire("PostToolUse", 250, 4002.0, session="nd3", extra=sent("local_coord", "清空接续：one-test 交接在 x")) is None, "the clear notice itself is not a node"
        assert fire("PostToolUse", 250, 4003.0, session="nd4", extra=sent("local_other")) is None, "a message to another session is not a node"
        assert "已向 one-coordinator 报告" in text(fire("PostToolUse", 250, 4004.0, session="nd5", extra={"tool_name": "SendMessage", "tool_input": {"to": "one-coordinator", "message": "x"}}))
        def turn_file(tokens, tool_use):
            usage = {"input_tokens": tokens, "cache_read_input_tokens": 0, "cache_creation_input_tokens": 0}
            lines = [{"type": "user", "message": {"role": "user", "content": "请做点事"}}]
            if tool_use:
                lines += [{"type": "assistant", "message": {"model": "claude-test", "usage": usage, "content": [{"type": "tool_use", "name": "Bash"}]}},
                          {"type": "user", "message": {"role": "user", "content": [{"type": "tool_result", "content": "ok"}]}}]
            lines.append({"type": "assistant", "message": {"model": "claude-test", "usage": usage, "content": [{"type": "text", "text": "好"}]}})
            path = os.path.join(tmp, f"turn-{tokens}-{int(tool_use)}.jsonl")
            write(path, "\n".join(json.dumps(x) for x in lines) + "\n")
            return path

        def stop_at(tokens, tool_use, session, now, **extra):
            return handle("Stop", dict(payload, hook_event_name="Stop", session_id=session, transcript_path=turn_file(tokens, tool_use), **extra), root, now)

        card = os.path.join(root, "docs/modernization/tasks/T-1.md")
        write(card, "---\n任务号: T-1\n会话: one-test\n状态: 进行中\n---\n## 目标\n\n做 A 目标。\n\n## 完成标准\n\n测试过。\n")
        stop_node = stop_at(250, True, "nd6", 4005.0)
        assert stop_node and stop_node["decision"] == "block" and "一轮结束" in stop_node["reason"] and "【交接节点】" in stop_node["reason"], stop_node
        assert "做 A 目标。" in stop_node["reason"] and "测试过。" in stop_node["reason"], "the open card's goal rides with the node instruction through the hook"
        os.remove(card)
        assert "读取失败" not in stop_node["reason"]
        # the handover source of the profile counts: it is named by the node instruction and the stop gate waits for it
        write(os.path.join(root, SESSIONS, "one-test.md"), "# one-test\n\n## 上下文文档\n\n- `docs/ctx.md`\n- 交接源: docs/hand.md#交接\n")
        write(os.path.join(root, "docs/hand.md"), "## 交接\n\nx\n")
        assert resolve_session(root)[2] == ["docs/ctx.md", "docs/hand.md"], resolve_session(root)
        os.utime(os.path.join(root, "docs/ctx.md"), (1, 1))
        os.utime(os.path.join(root, f"{SESSIONS}/one-test.md"), (1, 1))
        os.utime(os.path.join(root, "docs/hand.md"), (9e9, 9e9))
        stop_gate = lambda session, now: handle("Stop", dict(payload, hook_event_name="Stop", session_id=session, transcript_path=turn_file(760, False)), root, now)
        assert stop_gate("src-gate1", 90000.0) is None, "writing the handover source satisfies the stop gate"
        os.utime(os.path.join(root, "docs/hand.md"), (1, 1))
        gate = stop_gate("src-gate2", 91000.0)
        assert gate and gate["decision"] == "block" and "docs/hand.md" in gate["reason"], "a stale handover source is named by the stop gate"
        write(os.path.join(root, SESSIONS, "one-test.md"), "# one-test\n\n## 上下文文档\n\n- `docs/ctx.md`\n")
        os.remove(os.path.join(root, "docs/hand.md"))
        os.utime(os.path.join(root, "docs/ctx.md"), (1, 1))
        assert stop_at(250, False, "nd6b", 4005.5) is None, "a turn that only answered a question (no tool ran) is not a node"
        assert stop_at(250, True, "nd7", 4006.0, stop_hook_active=True) is None, "a continued stop is let through"
        assert stop_at(150, True, "nd8", 4007.0) is None, "below min_percent a turn end is not a node"
        assert turn_used_tools(turn_file(250, True)) is True and turn_used_tools(turn_file(250, False)) is False and turn_used_tools(os.path.join(tmp, "none.jsonl")) is False and turn_used_tools(None) is False
        # self-clear scope end to end (the session is named one-test): node, reminder and Stop fallback follow it
        def scope_case(extra_cfg, session, base):
            write(config, json.dumps({"default": 200000, "windows": {"claude-test": 1000}, "node_handoff": {**node_cfg, "sessions": None, **extra_cfg}}))
            step = NODE_COOLDOWN_SECONDS + 1
            node = stop_at(250, True, session, base)
            pushed = fire("PostToolUse", 250, base + step, session=session, extra=bash("git push origin b"))
            nudged = text(fire("PostToolUse", 760, base + 2 * step, session=session))
            os.utime(os.path.join(root, "docs/ctx.md"), (1, 1))
            os.utime(os.path.join(root, f"{SESSIONS}/one-test.md"), (1, 1))
            fallback = stop_at(760, False, session, base + 3 * step)
            assert fallback and fallback["decision"] == "block", fallback
            return node, text(pushed), nudged, fallback["reason"]

        node, pushed, nudged, fallback = scope_case({"exclude": []}, "sc-in", 10000.0)
        assert node and node["decision"] == "block" and "【交接节点】" in node["reason"] and "clear_session" in node["reason"], node
        assert pushed and "已推送" in pushed and "clear_session" in pushed, pushed
        assert nudged and "清空接续：one-test" in nudged and "clear_session（session_id=self）" in nudged and "不要 clear_session" not in nudged, nudged
        assert "clear_session（session_id=self）" in fallback and "不要 clear_session" not in fallback and "之后不再发消息" in fallback, fallback
        for label, extra_cfg, session in (("exclude", {"exclude": ["one-test"]}, "sc-ex"), ("coordinator_names", {"exclude": [], "coordinator_names": ["one-test"]}, "sc-names"),
                                          ("coordinator_session_ids", {"exclude": [], "coordinator_session_ids": ["local_coord"]}, "local_coord")):
            node, pushed, nudged, fallback = scope_case(extra_cfg, session, 20000.0 + 4000 * len(session))
            assert node is None and pushed is None, (label, node, pushed)
            assert nudged and "不要 clear_session" in nudged and "清空接续" not in nudged, (label, nudged)
            assert "不要 clear_session" in fallback and "清空接续" not in fallback, (label, fallback)
        write(config, json.dumps({"default": 200000, "windows": {"claude-test": 1000}, "node_handoff": {**node_cfg, "sessions": ["one-ops"]}}))
        assert stop_at(250, True, "nd9", 4008.0) is None and fire("PostToolUse", 250, 4009.0, session="nd9", extra=bash("git push")) is None, "only the listed (pilot) sessions"
        write(config, json.dumps({"default": 200000, "windows": {"claude-test": 1000}, "node_handoff": {**node_cfg, "sessions": None}}))
        assert text(fire("PostToolUse", 250, 4010.0, session="nd10", extra=bash("git push"))), "sessions null = every session"
        write(config, json.dumps({"default": 200000, "windows": {"claude-test": 1000}}))
        assert fire("PostToolUse", 250, 4011.0, session="nd11", extra=bash("git push")) is None, "off without the configuration"
        with tempfile.TemporaryDirectory() as repo:
            def g(*a):
                return subprocess.run(["git", "-C", repo, *a], capture_output=True, text=True, env={**os.environ, "GIT_CONFIG_GLOBAL": "/dev/null", "GIT_CONFIG_NOSYSTEM": "1", "GIT_AUTHOR_NAME": "t", "GIT_AUTHOR_EMAIL": "t@t", "GIT_COMMITTER_NAME": "t", "GIT_COMMITTER_EMAIL": "t@t"})
            g("init", "-q"); os.makedirs(os.path.join(repo, TASKS))
            fragment = os.path.join(repo, TASKS, "X-1022-QUA-01.md")
            write(fragment, "---\n状态: 进行中\n---\n"); g("add", "-A"); g("commit", "-q", "-m", "a")
            assert task_done_in_head(repo) is False
            write(fragment, "---\n状态: 完成\n---\n"); g("add", "-A"); g("commit", "-q", "-m", "b")
            assert task_done_in_head(repo) is True
            write(os.path.join(repo, "other.md"), "状态: 完成\n"); g("add", "-A"); g("commit", "-q", "-m", "c")
            assert task_done_in_head(repo) is False, "only task fragments count"
            os.makedirs(os.path.join(repo, "sub", "deep")); nested = {"cwd": os.path.join(repo, "sub", "deep")}
            assert root_of(nested) == os.path.realpath(repo), "the repository root is found without starting git"
            write(os.path.join(repo, "sub", ".git"), "gitdir: /elsewhere\n")
            assert root_of(nested) == os.path.realpath(os.path.join(repo, "sub")), "a worktree's .git file marks its root"

        # throttle: PostToolUse is skipped for a session measured moments ago whose transcript barely grew
        write(config, json.dumps({"default": 200000, "windows": {"claude-test": 1000}}))
        probe = lambda **extra: can_skip(dict(payload, hook_event_name=extra.pop("event", "PostToolUse"), session_id=extra.pop("session", "th"), **extra), root, extra_now[0])
        extra_now = [5000.0]
        transcript(trans, 100)
        assert probe() is False, "a session never measured is not skipped"
        fire("PostToolUse", 100, 5000.0, session="th")
        extra_now[0] = 5010.0
        assert probe() is True, "measured 10 s ago, transcript unchanged: skipped"
        extra_now[0] = 5000.0 + THROTTLE_SECONDS + 1
        assert probe() is False, "after the interval it is measured again"
        extra_now[0] = 5010.0
        assert probe(event="Stop") is False and probe(event="UserPromptSubmit") is False, "Stop and UserPromptSubmit are never throttled"
        assert probe(agent_id="a") is False
        assert probe(tool_name="Bash", tool_input={"command": "x" * (BASH_INPUT_CHARS + 1)}) is False, "an oversized Bash input is never skipped"
        assert probe(tool_name="Bash", tool_input={"command": "ls"}, tool_response="y" * (BASH_OUTPUT_CHARS + 1)) is False, "an oversized Bash result is never skipped"
        with open(trans, "a", encoding="utf-8") as grown:
            grown.write("x" * (THROTTLE_BYTES + 1))
        assert probe() is False, "a transcript that grew past the limit is measured"
        transcript(trans, 100)
        fire("PostToolUse", 100, 5000.0, session="th2")
        extra_now[0] = 5010.0
        write(config, json.dumps({"default": 200000, "windows": {"claude-test": 1000}, "node_handoff": node_cfg}))
        assert probe(session="th2", tool_name="Bash", tool_input={"command": "git push"}) is False, "a possible node is never skipped"
        assert probe(session="th2", tool_name="Bash", tool_input={"command": "ls"}) is True
        write(config, json.dumps({"default": 200000, "windows": {"claude-test": 1000}, "throttle_seconds": 0}))
        assert probe(session="th2") is False and throttle_limits(config) == (0, THROTTLE_BYTES), "throttle_seconds 0 turns it off"
        write(config, json.dumps({"default": 200000, "windows": {"claude-test": 1000}, "throttle_seconds": -1, "throttle_bytes": "x"}))
        assert throttle_limits(config) == (THROTTLE_SECONDS, THROTTLE_BYTES), "invalid values fall back"
        LEVELS = original_levels
    CONFIG = original_config
    LEVELS = original_levels
    real_levels, real_levels_warning = context_levels(original_config)
    assert real_levels_warning is None and real_levels == (30, 40), "the shipped configuration sets 30 / 40 (SESSION_PLAYBOOK §2.1 #1)"
    real_default, real_windows, real_warning = load_windows(original_config)
    assert real_warning is None and real_default > 0, "the shipped window configuration must be readable"
    for model in ("claude-opus-5-5", "claude-sonnet-5-5", "claude-fable-5-1"):
        assert window_for(model, real_default, real_windows)[1] is None, f"the shipped configuration must cover {model}"
    print("context_handoff self-test: ok")
    return 0


if __name__ == "__main__":
    sys.exit(self_test() if sys.argv[1:] == ["--self-test"] else main())
