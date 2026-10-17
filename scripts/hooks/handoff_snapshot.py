#!/usr/bin/env python3
from collections import deque
from datetime import datetime, timedelta
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import time


class SnapshotError(Exception):
    pass


def safe(text):
    lines = []
    for line in str(text).splitlines():
        sensitive = re.search(
            r"(?i)(password|passwd|secret|token|api[_-]?key|access[_-]?key|authorization|口令|密钥|令牌)\s*[=:：]|"
            r"-----[A-Z ]*PRIVATE KEY-----|(?:https?://)[^/\s]+:[^/\s]+@|"
            r"\b(?:gh[pousr]_|github_pat_|sk-)[A-Za-z0-9_-]+|\beyJ[A-Za-z0-9_-]+\.", line)
        for word in re.findall(r"(?<![\w/.-])[A-Za-z0-9_+=.-]{32,}(?![\w/.-])", line):
            if not re.fullmatch(r"[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}", word):
                sensitive = True
        lines.append("[敏感内容已隐藏]" if sensitive else "".join(c for c in line if c >= " " or c == "\t"))
    return "\n".join(lines)


def command(args, cwd, deadline, timeout=2):
    remaining = min(timeout, deadline - time.monotonic())
    if remaining <= 0:
        raise SnapshotError("跳过：执行超时")
    try:
        result = subprocess.run(args, cwd=cwd, capture_output=True, text=True, encoding="utf-8", errors="replace",
                                timeout=remaining, env={**os.environ, "GIT_TERMINAL_PROMPT": "0", "GIT_OPTIONAL_LOCKS": "0",
                                                        "GH_PROMPT_DISABLED": "1", "GH_NO_UPDATE_NOTIFIER": "1"})
    except subprocess.TimeoutExpired:
        raise SnapshotError("跳过：执行超时") from None
    except OSError:
        raise SnapshotError("跳过：命令未安装或不可用") from None
    if result.returncode:
        raise SnapshotError("跳过：命令执行失败")
    return result.stdout.strip("\n")


def git(cwd, deadline, *args):
    return command(["git", "-c", "core.fsmonitor=false", "-c", "core.quotepath=off", "-C", str(cwd), *args], None, deadline)


def messages(path, deadline):
    if not path or not stat.S_ISREG(Path(path).stat().st_mode):
        raise SnapshotError("无（会话记录缺失或不是普通文件）")
    found = deque(maxlen=10)
    invalid = False
    with open(path, encoding="utf-8") as transcript:
        for line in transcript:
            if time.monotonic() >= deadline:
                raise SnapshotError("无（读取会话记录超时）")
            try:
                entry = json.loads(line)
                message = entry.get("message", entry)
                content = message.get("content", [])
                texts = [content] if isinstance(content, str) else [b.get("text", "") for b in content if isinstance(b, dict) and b.get("type") == "text"]
            except (ValueError, AttributeError, TypeError):
                invalid = True
                continue
            for text in texts:
                for block in re.finditer(r"<cross-session-message\b([^>]*)>(.*?)</cross-session-message>", text, re.S):
                    sender = re.search(r"\bfrom-name\s*=\s*([\"'])(.*?)\1", block.group(1), re.S)
                    title = block.group(2).strip().splitlines()
                    if sender and title:
                        found.append(f"- {sender.group(2)}：{title[0]}")
    result = "\n".join(found) or "无"
    return result + ("\n跳过：会话记录含无效 JSON 条目" if invalid else "")


def write_section(out, title, action):
    out.write(f"\n## {title}\n")
    out.flush()
    try:
        result = action()
    except Exception as error:
        result = str(error) if isinstance(error, SnapshotError) else f"无（读取失败：{type(error).__name__}）"
    out.write(safe(result or "无") + "\n")
    out.flush()


def end(payload):
    if payload.get("reason", payload.get("session_end_reason")) != "clear":
        return
    deadline = time.monotonic() + 17
    directory = Path(os.environ["HOME"]) / ".modernize/psa/handoff"
    directory.mkdir(parents=True, exist_ok=True, mode=0o700)
    directory.chmod(0o700)
    session = payload.get("session_id", "")
    valid_session = isinstance(session, str) and re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{0,127}", session)
    session = session if valid_session else "unknown"
    now = datetime.now().astimezone()
    path = directory / f"{now:%Y%m%d-%H%M%S-%f}-{session}-clear.md"
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    os.fchmod(fd, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as out:
        out.write(f"# 清空前快照（clear）\n\n- 时间：{now:%Y-%m-%d %H:%M:%S %z}\n- 会话：{safe(session)}\n")
        out.write(f"- 会话记录：{safe(payload.get('transcript_path') or '未知')}\n")
        if not valid_session:
            out.write("跳过：session_id 缺失或无效，文件名使用 unknown\n")
        try:
            cwd = payload.get("cwd")
            if not isinstance(cwd, str) or not os.path.isabs(cwd):
                raise SnapshotError("cwd 缺失或不是绝对路径")
            root = git(cwd, deadline, "rev-parse", "--show-toplevel")
            out.write(f"- 工作目录：{safe(root)}\n")
            branch = git(root, deadline, "rev-parse", "--abbrev-ref", "HEAD")
            out.write(f"- 分支：{safe(branch)}\n")
        except Exception as error:
            out.write(f"跳过：无法读取工作目录或分支（{str(error) if isinstance(error, SnapshotError) else type(error).__name__}）\n")
            root = branch = None
        if root:
            write_section(out, "未提交改动", lambda: "\n".join(git(root, deadline, "status", "--short").splitlines()[:50]))
            write_section(out, "最近提交", lambda: git(root, deadline, "log", "--oneline", "-10"))
            pr_deadline = min(deadline, time.monotonic() + 10)
            for title, selection in [("本分支在开 PR", ["--head", branch]), ("我开的在开 PR", ["--author", "@me"])]:
                write_section(out, title, lambda selection=selection: command(
                    ["gh", "pr", "list", *selection, "--state", "open", "--json", "number,title,state",
                     "--jq", '.[] | "#\(.number) [\(.state)] \(.title)"'], root, pr_deadline, timeout=10))
        write_section(out, "最近跨会话消息标题", lambda: messages(payload.get("transcript_path"), deadline))


def latest(directory, root, session):
    candidates = sorted(directory.glob("*.md"), key=lambda p: p.name, reverse=True)
    texts = []
    for path in candidates:
        if not re.match(r"^\d{8}-\d{6}-.+\.md$", path.name) or path.is_symlink():
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except (OSError, UnicodeError):
            continue
        texts.append((path, text))
        if f"- 工作目录：{root}" in text.splitlines():
            return path, text
    if isinstance(session, str) and re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{0,127}", session):
        for path, text in texts:
            if path.name.endswith((f"-{session}.md", f"-{session}-clear.md")):
                return path, text
    return None


def start(payload):
    deadline = time.monotonic() + 15
    cwd = payload.get("cwd") or os.getcwd()
    root = git(cwd, deadline, "rev-parse", "--show-toplevel")
    hooks = Path(root) / "scripts/hooks"
    for script, args in [("session_context.py", [root]), ("playbook_digest.py", [str(Path(root) / "docs/modernization/SESSION_PLAYBOOK.md")])]:
        try:
            print(command([sys.executable, "-I", str(hooks / script), *args], root, deadline, timeout=5))
        except SnapshotError:
            print(f"【交接快照】告警：{script} 不可用；请手动读取上下文。")
    snapshot = latest(Path(os.environ["HOME"]) / ".modernize/psa/handoff", root, payload.get("session_id"))
    if not snapshot:
        return
    path, text = snapshot
    timestamp = datetime.strptime(path.name[:15], "%Y%m%d-%H%M%S")
    if datetime.now() - timestamp > timedelta(days=7):
        print("【交接快照】已超过 7 天，不注入；请核对当前实况。")
        return
    print(f"\n【交接快照】{safe(path)}")
    print(safe(text))


def run(mode):
    try:
        payload = json.load(sys.stdin)
        if not isinstance(payload, dict):
            raise ValueError()
        (end if mode == "--end" else start)(payload)
    except Exception as error:
        print(f"【交接快照】告警：处理失败（{type(error).__name__}）；请手动交接。")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    run(sys.argv[1] if len(sys.argv) == 2 else "--start")
