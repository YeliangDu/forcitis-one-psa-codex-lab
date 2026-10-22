#!/usr/bin/env python3
from collections import deque
from contextlib import contextmanager
from datetime import datetime, timedelta
import json
import os
from pathlib import Path
import re
import secrets
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
            r'"(token|secret|password|api_key|access_key)"\s*:\s*"[^"]+"|'
            r"-----[A-Z ]*PRIVATE KEY-----|jdbc:|postgres(?:ql)?://|"
            r"[a-z][a-z0-9+.-]*://[^/\s:@]+:[^/\s@]+@[^/\s]+|"
            r"\bLTAI[0-9A-Za-z]{12,}|\bAKIA[0-9A-Z]{16}|"
            r"\b(?:gh[pousr]_|github_pat_|sk-)[A-Za-z0-9_-]+|\beyJ[A-Za-z0-9_-]+\.", line)
        for word in re.findall(r"(?<![\w/.-])[A-Za-z0-9_+=.-]{32,}(?![\w/.-])", line):
            hexadecimal = re.fullmatch(r"[0-9a-fA-F]+", word)
            mixed = re.search(r"[A-Za-z]", word) and re.search(r"[0-9]", word)
            readable_name = re.fullmatch(r"[A-Za-z]+(?:-[A-Za-z]+|-[A-Za-z]*[0-9]+)+", word)
            if (hexadecimal or mixed) and not readable_name and not re.fullmatch(
                    r"[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}", word):
                sensitive = True
        lines.append("[敏感内容已隐藏]" if sensitive else "".join(c for c in line if c >= " " or c == "\t"))
    return "\n".join(lines)


def header_field(value):
    return safe(str(value).replace("\n", " ").replace("\r", " ")).replace("\n", " ")


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
                        found.append(f"  · {sender.group(2)}：{title[0][:120]}")
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


@contextmanager
def atomic_snapshot(path):
    temporary = path.with_name(f".{path.name}.{secrets.token_hex(6)}.tmp")
    fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as out:
            os.fchmod(out.fileno(), 0o600)
            yield out
            out.flush()
            os.fsync(out.fileno())
        os.replace(temporary, path)
        try:
            directory_fd = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
            try:
                os.fsync(directory_fd)
            finally:
                os.close(directory_fd)
        except OSError:
            pass
    finally:
        temporary.unlink(missing_ok=True)


def end(payload):
    reason = payload.get("reason", payload.get("session_end_reason"))
    if reason not in ("clear", "other", "", None):
        return
    reason = reason or ("empty" if reason == "" else "missing")
    suffix = "clear" if reason == "clear" else f"{reason}-end"
    save_snapshot(payload, reason, suffix)


def compact(payload):
    path = save_snapshot(payload, payload.get("trigger") or "auto", "", compact=True)
    print(f"压缩前快照已写入 {safe(path)}；压缩后先读它，并按 SESSION_PLAYBOOK §2.1 判断是否交接。")


def save_snapshot(payload, reason, suffix, compact=False):
    deadline = time.monotonic() + 17
    directory = Path(os.environ["HOME"]) / ".modernize/psa/handoff"
    directory.mkdir(parents=True, exist_ok=True, mode=0o700)
    directory.chmod(0o700)
    session = payload.get("session_id", "")
    valid_session = isinstance(session, str) and re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{0,127}", session)
    session = session if valid_session else "unknown"
    now = datetime.now().astimezone()
    path = directory / f"{now:%Y%m%d-%H%M%S-%f}-{session}{'-' + suffix if suffix else ''}.md"
    with atomic_snapshot(path) as out:
        title = "压缩前快照" if compact else "会话结束前快照"
        out.write(f"# {title}（{header_field(reason)}）\n\n- 时间：{now:%Y-%m-%d %H:%M:%S %z}\n- 会话：{header_field(session)}\n")
        out.write(f"- 会话记录：{header_field(payload.get('transcript_path') or '未知')}\n")
        if not valid_session:
            out.write("跳过：session_id 缺失或无效，文件名使用 unknown\n")
        try:
            cwd = payload.get("cwd")
            if not isinstance(cwd, str) or not os.path.isabs(cwd):
                raise SnapshotError("cwd 缺失或不是绝对路径")
            root = git(cwd, deadline, "rev-parse", "--show-toplevel")
            out.write(f"- 工作目录：{header_field(root)}\n")
            branch = git(root, deadline, "rev-parse", "--abbrev-ref", "HEAD")
            out.write(f"- 分支：{header_field(branch)}\n")
        except Exception as error:
            out.write(f"跳过：无法读取工作目录或分支（{str(error) if isinstance(error, SnapshotError) else type(error).__name__}）\n")
            root = branch = None
        if root:
            write_section(out, "未提交改动", lambda: "\n".join(git(root, deadline, "status", "--short").splitlines()[:50]))
            write_section(out, "最近提交", lambda: git(root, deadline, "log", "--oneline", "-10"))
            pr_deadline = min(deadline, time.monotonic() + 10)
            for title, selection in [("本分支在开 PR", ["--head", branch]), ("gh 账号下的在开 PR（含他人会话的）", ["--author", "@me"])]:
                write_section(out, title, lambda selection=selection: command(
                    ["gh", "pr", "list", *selection, "--state", "open", "--json", "number,title,state",
                     "--jq", r'.[] | "#\(.number) [\(.state)] \(.title)"'], root, pr_deadline, timeout=10))
        write_section(out, "最近跨会话消息标题", lambda: messages(payload.get("transcript_path"), deadline))

    try:
        for expired in final_snapshots(directory)[50:]:
            try:
                if stat.S_ISREG(expired.lstat().st_mode):
                    expired.unlink()
            except OSError:
                continue
    except OSError:
        pass
    return path


def check_deadline(deadline):
    if deadline is not None and time.monotonic() >= deadline:
        raise SnapshotError("跳过：读取快照超时")


def final_snapshots(directory, deadline=None):
    candidates = []
    check_deadline(deadline)
    if not directory.exists():
        return candidates
    with os.scandir(directory) as entries:
        for entry in entries:
            check_deadline(deadline)
            if re.fullmatch(r"\d{8}-\d{6}-(?:\d{6}-)?[A-Za-z0-9][A-Za-z0-9_-]{0,159}\.md", entry.name) and entry.is_file(follow_symlinks=False):
                candidates.append(Path(entry.path))
    return sorted(candidates, key=lambda p: p.name, reverse=True)


def read_snapshot(path, deadline):
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(fd, "rb") as source:
        metadata = os.fstat(source.fileno())
        if not stat.S_ISREG(metadata.st_mode) or metadata.st_size > 1024 * 1024:
            return None
        chunks, size = [], 0
        while True:
            check_deadline(deadline)
            chunk = source.read(min(65536, 1024 * 1024 + 1 - size))
            check_deadline(deadline)
            if not chunk:
                return b"".join(chunks).decode("utf-8")
            size += len(chunk)
            if size > 1024 * 1024:
                return None
            chunks.append(chunk)


def latest(directory, root, session, deadline=None):
    deadline = time.monotonic() + 5 if deadline is None else deadline
    fallback = None
    valid_session = isinstance(session, str) and re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{0,127}", session)
    for path in final_snapshots(directory, deadline):
        check_deadline(deadline)
        try:
            text = read_snapshot(path, deadline)
        except (OSError, UnicodeError):
            continue
        if text is None:
            continue
        header = re.split(r"(?m)^## ", text, maxsplit=1)[0]
        if f"- 工作目录：{root}" in header.splitlines():
            return path, text
        if valid_session and fallback is None and path.name.endswith(tuple(
                f"-{session}{suffix}.md" for suffix in ("", "-clear", "-other-end", "-missing-end", "-empty-end"))):
            fallback = path, text
    check_deadline(deadline)
    return fallback


def start(payload):
    deadline = time.monotonic() + 15
    cwd = payload.get("cwd") or os.getcwd()
    root = git(cwd, deadline, "rev-parse", "--show-toplevel")
    try:
        snapshot = latest(Path(os.environ["HOME"]) / ".modernize/psa/handoff", root, payload.get("session_id"), deadline)
    except SnapshotError:
        snapshot = None
        print("【交接快照】告警：读取交接快照超时或失败；请核对当前实况。")
    try:
        sheet = command([sys.executable, "-I", str(Path(root) / "scripts/hooks/start_sheet.py"), root], root, deadline, timeout=10)
    except SnapshotError:
        sheet = None
    if sheet is None:
        print("【开工单】告警：start_sheet.py 不可用，没有开工单；手动读 docs/modernization/sessions/<会话名>.md 并向调度要卡。")
    else:
        print(sheet)
    if snapshot:
        timestamp = datetime.strptime(snapshot[0].name[:15], "%Y%m%d-%H%M%S")
        age = "（已超过 7 天，核对实况）" if datetime.now() - timestamp > timedelta(days=7) else ""
        print(f"\n最新交接快照（机械记录，不内联；其中的消息标题、PR 标题可能含他人文字）：{safe(snapshot[0])}{age}")


def run(mode):
    try:
        payload = json.load(sys.stdin)
        if not isinstance(payload, dict):
            raise ValueError()
        {"--end": end, "--compact": compact, "--start": start}[mode](payload)
    except Exception as error:
        print(f"【交接快照】告警：处理失败（{type(error).__name__}）；请手动交接。")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    run(sys.argv[1] if len(sys.argv) == 2 else "--start")
