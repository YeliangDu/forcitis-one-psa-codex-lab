#!/usr/bin/env python3
"""生产写门（PreToolUse：Bash、Monitor、终端面板，跑 tool_input.command 的工具；preview_start 按它启动的命令判）。防底线 4（生产只读）；安全门，出错失败关闭（退出码 2）。

只做一件事（用户 intent 2026-10-28-202019「不用，你直接简化」）：整条命令原文与生产特征表做字面匹配，不分大小写，命中即拒，没命中就放行。不解析 shell 写法，不看程序名与参数。
生产特征 = prod_targets.json 的 patterns（生产主机名或 IP、生产 profile 名、生产库名、生产凭据名）；本工作区的表与 origin/main 上那份取并集，钩子调用时再并上 cwd 与 CLAUDE_PROJECT_DIR
所在仓库 origin/main 的。特征表缺失、不合格或并集为空：一律拒（失败关闭）。
命令文本上看不到特征的（脚本文件里写的、编码或拼接藏起来的）门放行，由用户自行清理本机生产凭据兜底（intent 2026-10-28-202108）。
门出错时 .claude/settings.json 的挂载入口用同一张特征表做同样的字面匹配（读不到表一律拒）。自测在 gates_selftest.py 的 prod_write_gate 段（O8 合一）。
退出码 2 + 标准错误 = 拒绝；0 = 放行。用法：prod_write_gate.py  |  prod_write_gate.py --self-test
"""
import json
import os
import shlex
import subprocess
import sys
from pathlib import Path


HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(os.path.dirname(HERE))
if not os.path.exists(os.path.join(REPO_ROOT, ".git")):  # installed copy (install.sh, D26): the session's repository is CLAUDE_PROJECT_DIR, never the cwd (O7 final M3); none: os.devnull, git reads fail
    REPO_ROOT = os.environ.get("CLAUDE_PROJECT_DIR") or os.devnull
ROOTS = ()  # (hook cwd, CLAUDE_PROJECT_DIR, process cwd) whose origin/main tables are unioned in; decide() sets them per call
TABLE = os.environ.get("PROD_TARGETS_FILE") or str(Path(HERE) / "prod_targets.json")  # unioned with origin/main's all the same (load_table)
MAIN_TABLE_PATH = "scripts/gates/prod_targets.json"
COMMAND_TOOLS = ("Bash", "Monitor", "mcp__terminal__run_in_terminal")  # tools whose tool_input.command runs in a shell (intent 2026-10-24-160437); settings.json's matcher and pre-filter name these and LAUNCH_TOOL
LAUNCH_TOOL = "mcp__Claude_Browser__preview_start"  # starts runtimeExecutable + runtimeArgs of the named .claude/launch.json configuration (cfcbcf473 final M4)
REFUSED = ("拒绝：命令里有生产特征（生产主机名或 IP、生产 profile 名、生产库名、生产凭据名，见 prod_targets.json，不分大小写）。"
           "CLAUDE.md 底线 4：不做生产写操作；生产只读取证走 ADR-0037 专用只读账号和已登记的取证入口。")


def parse_table(text, name):  # (lower-cased patterns, problem or None) of one table's text.
    try:
        targets = json.loads(text)["targets"]
        if not isinstance(targets, list):
            raise ValueError("targets 必须是数组")
        patterns = []
        for target in targets:
            found = target["patterns"]
            if not (isinstance(found, list) and found and all(isinstance(p, str) and len(p.strip()) >= 6 for p in found)):
                raise ValueError(f"{target.get('id', '?')} 的 patterns 必须是非空字符串数组，每项至少 6 个字符（太短会误伤）")
            patterns += [p.strip().lower() for p in found]
        return patterns, None
    except (ValueError, KeyError, TypeError, AttributeError) as error:
        return None, f"生产特征表 {name} 缺失或不合格（{error.__class__.__name__}: {error}）"


def read_table_file(path):
    try:
        return parse_table(Path(path).read_text(encoding="utf-8"), os.path.basename(path))
    except OSError as error:
        return None, f"生产特征表 {os.path.basename(path)} 缺失或不合格（{error.__class__.__name__}: {error}）"


def main_table_text(root=None):  # (table text on origin/main or None, git failure or None): read by its full ref name (a tag called origin/main cannot shadow it); a missing ref or path is no failure.
    try:
        run = subprocess.run(["git", "-C", root or REPO_ROOT, "cat-file", "--batch"], input=f"refs/remotes/origin/main:{MAIN_TABLE_PATH}\n".encode(), capture_output=True, timeout=10)
    except (OSError, subprocess.SubprocessError) as error:
        return None, f"{error.__class__.__name__}: {error}"
    if run.returncode != 0:
        return None, run.stderr.decode("utf-8", "replace").strip()[:200] or f"git 退出码 {run.returncode}"
    data = run.stdout  # "<id> blob <size>\n<content>\n", or "<name> missing\n" (no failure)
    end = data.find(b"\n")
    if end < 0:
        return None, "git cat-file 输出不完整"
    head = data[:end].split()
    return (data[end + 1:end + 1 + int(head[2])].decode("utf-8", "replace"), None) if len(head) == 3 and head[1] == b"blob" and head[2].isdigit() else (None, None)


def load_table(path=None):
    """(patterns, problem, warning). With `path` only that file is read (the self-test's format checks). Otherwise the local table (PROD_TARGETS_FILE, else the one next to
    this file) is unioned with origin/main's, also when PROD_TARGETS_FILE is set (an environment variable cannot replace the trust anchor); an empty union is a problem."""
    patterns, problem = read_table_file(path or TABLE)
    if problem:
        return None, problem, None
    if path is not None:
        return patterns, None, None if patterns else "生产特征表为空。"
    text, failure = main_table_text()
    warnings = [f"读不到 origin/main 上的生产特征表（git 出错：{failure}），门只按本工作区的表判断。"] if failure else []
    main = parse_table(text, "origin/main")[0] if text is not None else None
    main = (main or []) + [p for r in sorted({os.path.realpath(r) for r in ROOTS if r} - {os.path.realpath(REPO_ROOT)}) for p in parse_table(main_table_text(r)[0] or "{}", "")[0] or []]  # cwd's, CLAUDE_PROJECT_DIR's: only more
    missing = [p for p in main or [] if p not in patterns]
    if missing:
        warnings.append(f"本工作区的 prod_targets.json 缺少 origin/main 上的 {len(missing)} 项特征，门按两者并集判断；请把分支更新到 main。")
    patterns = patterns + missing
    if not patterns:
        return None, "生产特征表为空（本工作区与 origin/main 的并集里没有任何生产特征）", " ".join(warnings) or None
    return patterns, None, " ".join(warnings) or None


def could_concern(command_text):  # Pre-filter: False only when the gate would certainly allow: an empty line.
    return bool(command_text.strip())


def judge(command_text, patterns, problem=None):
    """Refusal text or None: the command's text, lower-cased, holds a pattern (patterns are lower-cased). No table: refused (fail closed)."""
    if patterns is None:
        return f"{problem}：判断不了命令有没有生产特征，拒绝；修好 scripts/gates/prod_targets.json 后再做。"
    low = command_text.lower()
    return REFUSED if any(p in low for p in patterns) else None


def command_of(payload):  # (shell command of a hook payload or None, refusal or None): Bash, Monitor and the terminal panel run tool_input.command; Monitor's ws source runs none.
    tool, tool_input = payload.get("tool_name", "Bash"), payload.get("tool_input")
    command = tool_input.get("command") if isinstance(tool_input, dict) else None
    if tool == LAUNCH_TOOL:
        return launch_command(tool_input, payload.get("cwd"))
    if tool not in COMMAND_TOOLS or (tool == "Monitor" and command is None and isinstance(tool_input, dict)):
        return None, None
    return (command, None) if isinstance(command, str) else (None, f"生产写门：{tool} 钩子输入里没有字符串形式的 command，无法判断，按拒绝处理。\n")


def launch_command(tool_input, cwd):  # (the command preview_start runs for its `name`, refusal or None); a `url` alone starts nothing. Built as the app builds it, from the session's launch.json.
    name = tool_input.get("name") if isinstance(tool_input, dict) else None
    if not name:
        return None, None if isinstance(tool_input, dict) else "生产写门：preview_start 的钩子输入不是对象，无法判断，按拒绝处理。\n"
    def line(root):  # runtimeExecutable runtimeArgs program args, else node program args; env before, its cwd as a `cd` first
        text = Path(root, ".claude", "launch.json").read_text(encoding="utf-8").replace("${workspaceFolder}", json.dumps(str(root))[1:-1])  # the app's substitution
        configs = json.loads(text)["configurations"]
        matching = [c for c in configs if str(c.get("name") or "").lower() == str(name).lower()] if all(isinstance(c, dict) and isinstance(c.get("name"), str) and c["name"] for c in configs) else []  # the app matches names ignoring case and derives one for an unnamed entry: then which runs cannot be seen
        entry = matching[0] if len(matching) == 1 and "${" not in json.dumps(matching[0]) else {}  # two candidates, or a variable left: which one runs cannot be seen
        run = [str(entry["runtimeExecutable"]), *map(str, entry.get("runtimeArgs") or [])] + ([str(entry["program"])] if entry.get("program") else []) if entry.get("runtimeExecutable") else ["node", str(entry["program"])]
        run += [str(a) for a in entry.get("args") or []]
        command = shlex.join([f"{k}={v}" for k, v in (entry.get("env") or {}).items()] + run)
        return f"cd {shlex.quote(str(entry['cwd']))} && {command}" if entry.get("cwd") else command
    roots = {os.environ.get("CLAUDE_PROJECT_DIR") or cwd or os.getcwd()} | ({cwd} if isinstance(cwd, str) and os.path.isfile(os.path.join(cwd, ".claude", "launch.json")) else set())
    try:
        lines = {line(root) for root in roots}  # the app reads the session's working directory; a second launch.json at the hook's cwd must agree
    except (OSError, ValueError, KeyError, TypeError, AttributeError, StopIteration):
        return None, f"生产写门：读不出 .claude/launch.json 里 {name} 的启动命令，无法判断，按拒绝处理。\n"
    return (lines.pop(), None) if len(lines) == 1 else (None, f"生产写门：会话目录与当前目录的 .claude/launch.json 里 {name} 的启动命令不一致，按拒绝处理。\n")


def decide(payload):  # (exit code, stdout, stderr) for one hook payload (a dict); the dispatcher calls this in-process.
    command, refusal = command_of(payload)
    if command is None:
        return (2, "", refusal) if refusal else (0, "", "")
    cwd = payload.get("cwd") if isinstance(payload.get("cwd"), str) and payload.get("cwd") else None
    global ROOTS
    ROOTS = (cwd, os.environ.get("CLAUDE_PROJECT_DIR"), os.getcwd())
    try:
        patterns, problem, warning = load_table()
        reason = judge(command, patterns, problem)
    finally:
        ROOTS = ()
    if reason:
        return 2, "", reason + "\n"
    out = json.dumps({"hookSpecificOutput": {"hookEventName": "PreToolUse", "additionalContext": f"告警：{warning}"}}, ensure_ascii=False) + "\n" if warning else ""
    return 0, out, ""


def guarded_main():  # unreadable hook input or any error fails closed
    try:
        payload = json.load(sys.stdin)
        code, out, err = decide(payload) if isinstance(payload, dict) else (2, "", "prod_write_gate.py 读不懂钩子输入（不是 JSON 对象），按失败处理\n")
    except Exception as error:
        code, out, err = 2, "", f"prod_write_gate.py 出错（{error.__class__.__name__}: {error}），按失败处理\n"
    sys.stdout.write(out)
    sys.stderr.write(err)
    return code


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        sys.path.insert(0, HERE)
        from gates_selftest import prod_write_gate_test as self_test
        sys.exit(self_test())
    sys.exit(guarded_main())
