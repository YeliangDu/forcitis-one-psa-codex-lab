#!/usr/bin/env python3
"""One PreToolUse entry point for Bash / Agent / Task: runs the gates in one process instead of one hook each (GOV-1022-COO-08).

Gates, cheapest first (a refusal by a cheap gate means the slow precheck never starts): bg_task_name_gate, main_checkout_gate, codex_stdin_gate, prod_write_gate (GOV-1022-QUA-24),
pre_push_gate, precheck_before_pr. Each gate keeps its own file, self-test and exit-code contract; the dispatcher imports a gate only when a
conservative string pre-filter says the command could concern it, then calls the gate's own guarded entry with the original stdin text.
Pre-filters only ever skip a gate whose own first step would have allowed the command: they are tested against the gates run on their own (--self-test).
  main_checkout_gate: "git" in the command; pre_push_gate: "git"; precheck_before_pr: "gh" and "pr"; codex_stdin_gate: "codex" or a `pi` word;
  bg_task_name_gate: always for Agent/Task, for Bash only when run_in_background is true.
Quotes and backslashes are removed and the text lower-cased before the pre-filter looks (`GIT` too; `gi"t" push` still reaches the gates, whose shell tokenisers see `git push`).
Result: any gate exit != 0 refuses (exit 2, every refusing gate's stderr in order); stdout JSON warnings (additionalContext) are joined; stderr of allowing gates is passed on.
Time limit: the entry in .claude/settings.json is 3100 s, above the sum of the two slow gates' own limits (pre_push 1400 + precheck_before_pr 1500), because a command such as `git push && gh pr create` runs both one after the other in this process; a hook killed by its time limit does not block.
An error in the dispatcher itself refuses (exit 2): a crash would exit 1, which Claude Code treats as "do not block".
Usage: pre_tool_dispatch.py  |  pre_tool_dispatch.py --self-test
"""
import contextlib
import importlib
import io
import json
import os
import re
import subprocess
import sys
from pathlib import Path

HERE = str(Path(__file__).resolve().parent)
sys.path.insert(0, HERE)
from command_words import plain  # noqa: E402
from prod_write_gate import could_concern  # noqa: E402

PI_WORD = re.compile(r"(?<![A-Za-z0-9_])pi(?![A-Za-z0-9_])")
# (module, entry, applies(tool_name, command_text, tool_input)), cheapest first
GATES = (
    ("bg_task_name_gate", "guarded_main", lambda tool, text, tool_input: tool in ("Agent", "Task") or (tool == "Bash" and tool_input.get("run_in_background") is True)),
    ("main_checkout_gate", "pre_tool_use_guarded", lambda tool, text, tool_input: tool == "Bash" and "git" in text),
    ("codex_stdin_gate", "guarded_main", lambda tool, text, tool_input: tool == "Bash" and ("codex" in text or PI_WORD.search(text) is not None)),
    ("prod_write_gate", "guarded_main", lambda tool, text, tool_input: tool == "Bash" and could_concern(text)),
    ("pre_push_gate", "guarded_main", lambda tool, text, tool_input: tool == "Bash" and "git" in text),
    ("precheck_before_pr", "guarded_main", lambda tool, text, tool_input: tool == "Bash" and "gh" in text and "pr" in text),
)


def entry_of(module, name):
    if name == "pre_tool_use_guarded":
        return lambda: module.guarded(module.pre_tool_use)
    return getattr(module, name)


def run_gate(module_name, entry_name, raw):
    module = importlib.import_module(module_name)
    out, err = io.StringIO(), io.StringIO()
    real_stdin = sys.stdin
    sys.stdin = io.StringIO(raw)
    try:
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = entry_of(module, entry_name)()
    finally:
        sys.stdin = real_stdin
    return int(code or 0), out.getvalue(), err.getvalue()


def selected(payload):
    """Names of the gates the pre-filters let through for this payload."""
    tool = payload.get("tool_name", "Bash")
    tool_input = payload.get("tool_input") if isinstance(payload.get("tool_input"), dict) else {}
    command = tool_input.get("command")
    text = plain(command)
    return [(name, entry) for name, entry, applies in GATES if applies(tool, text, tool_input)]


def dispatch(raw):
    """(exit code, stdout, stderr) for the hook input text."""
    try:
        payload = json.loads(raw)
    except ValueError:
        payload = None
    if not isinstance(payload, dict):
        # let every gate say what it says about unreadable input (most refuse, precheck_before_pr allows)
        names = [(name, entry) for name, entry, _ in GATES]
    else:
        names = selected(payload)
    refusals, warnings, notes = [], [], []
    for name, entry in names:
        code, out, err = run_gate(name, entry, raw)
        if code != 0:
            refusals.append(err.rstrip("\n"))
            break  # cheapest-first: a refusal means the slower gates need not run
        if err.strip():
            notes.append(err.rstrip("\n"))
        if out.strip():
            warnings.append(out)
    if refusals:
        return 2, "", "\n".join(refusals + notes) + "\n"
    stdout = ""
    if warnings:
        contexts = []
        for text in warnings:
            try:
                contexts.append(json.loads(text)["hookSpecificOutput"]["additionalContext"])
            except (ValueError, KeyError, TypeError):
                contexts.append(text.strip())
        stdout = json.dumps({"hookSpecificOutput": {"hookEventName": "PreToolUse", "additionalContext": "\n".join(contexts)}}, ensure_ascii=False) + "\n"
    return 0, stdout, ("\n".join(notes) + "\n") if notes else ""


def main():
    try:
        code, out, err = dispatch(sys.stdin.read())
    except Exception as error:
        print(f"pre_tool_dispatch.py 出错（{error.__class__.__name__}: {error}），按失败处理", file=sys.stderr)
        return 2
    sys.stdout.write(out)
    sys.stderr.write(err)
    return code


def alone(script, raw, cwd=None):
    result = subprocess.run([sys.executable, "-I", str(Path(HERE) / script)], input=raw, capture_output=True, text=True, encoding="utf-8", timeout=120, cwd=cwd,
                            env={"LC_ALL": "C", "PATH": os.environ.get("PATH", "")})
    return result.returncode, result.stdout, result.stderr


def self_test():
    cwd = str(Path(HERE).parents[1])

    def payload(command, **extra):
        return json.dumps({"tool_name": "Bash", "tool_input": {"command": command}, "cwd": cwd, **extra})

    # 1. differential test: for every command, the dispatcher decides exactly what the gates decide when each runs alone.
    #    Commands that would start precheck.sh (slow) are left to the gates' own self-tests; here a gate that would run it is replaced by a stub.
    import precheck_before_pr as pr
    import pre_push_gate as pp
    from codex_stdin_gate import hook_cases
    commands = [command for command, _, _ in hook_cases()]
    commands += ["ls", "git status", "git log --oneline -3", "echo hi", "gi\"t\" push --no-verify", "git pu\\sh --no-verify", "g\"h\" pr create", "c'o'dex exec p", "git commit -m x", "cd /tmp && git push --no-verify", "psql -h rm-bp1690ep53t9778dp -c \"delete from t\"", "psql -h localhost -c \"select 1\"", "ssh h 'ls'", "p=rm-bp1690ep; q=53t9778dp; psql -h $p$q -c \"drop table t\"",
                 "git push --no-verify", "git push --dry-run", "git push origin --delete old", "pi -p x", "p\"i\" -p x", "PUSH_BYPASS_REASON= git push --no-verify", "python3 -c 'print(1)'", "pip install x",
                 "cat api.py", "git reset --soft HEAD~1", "git checkout -b x", "bash -c 'git push --no-verify'", "echo $(git push --no-verify)", "gh pr view 3", "gh pr checks 3", "", "   ", "git push", "git push origin HEAD", "gh pr create --title x", "cd /tmp && gh pr create"]
    stubbed = 0
    for command in commands:
        raw = payload(command)
        names = [name for name, _ in selected(json.loads(raw))]
        if "precheck_before_pr" in names and pr.is_pr_create(command):
            stubbed += 1
            continue
        if "pre_push_gate" in names and pp.decide(command, cwd)[0] == "run":
            stubbed += 1
            continue
        expected_refusals = []
        expected_warn = False
        for script in ("bg_task_name_gate.py", "main_checkout_gate.py", "codex_stdin_gate.py", "prod_write_gate.py", "pre_push_gate.py", "precheck_before_pr.py"):
            code, out, err = alone(script, raw)
            if code != 0:
                expected_refusals.append(script)
            expected_warn = expected_warn or bool(out.strip())
        code, out, err = dispatch(raw)
        assert (code != 0) == bool(expected_refusals), (command, code, err, expected_refusals)
        if expected_refusals:
            first = alone(expected_refusals[0], raw)
            assert err.startswith(first[2].rstrip("\n")), (command, err, first[2])
        assert code in (0, 2), (command, code)
        assert bool(out.strip()) == (expected_warn and not expected_refusals), (command, out, expected_warn)
    assert stubbed >= 3, "the corpus must include commands that start precheck.sh (covered by the gates' own self-tests)"

    # 2. pre-filters: commands no gate cares about start no gate at all
    for command in ("ls", "echo hi", "cat api.py", "pip install x", "npm test", "mvn -q test"):
        assert selected(json.loads(payload(command))) == [], command
    assert "prod_write_gate" in [n for n, _ in selected(json.loads(payload("python3 x.py")))], "an interpreter may run a script that names production"
    assert [n for n, _ in selected(json.loads(payload("git status")))] == ["main_checkout_gate", "pre_push_gate"]
    assert "precheck_before_pr" in [n for n, _ in selected(json.loads(payload("gh pr create")))]
    assert "codex_stdin_gate" in [n for n, _ in selected(json.loads(payload("pi -p x")))] and "codex_stdin_gate" not in [n for n, _ in selected(json.loads(payload("pip install x")))]
    assert "pre_push_gate" in [n for n, _ in selected(json.loads(payload("g\"it\" push")))], "quotes do not hide git from the pre-filter"

    # 2b. the production-write gate is reached through the dispatcher, also behind quotes
    assert dispatch(payload("aliyun rds DeleteAccount --DBInstanceId rm-bp1690ep53t9778dp"))[0] == 2 and dispatch(payload("aliyun rds DescribeDBInstances --DBInstanceId rm-bp1690ep53t9778dp"))[0] == 0
    for hostile in ("python3 -c \"connect('rm-bp1690ep53t9778dp')\"", "xargs -I{} sqlcmd -S rm-bp1690ep53t9778dp -Q {}", "sqlcmd -S `echo rm-bp1690ep53t9778dp` -Q 'delete from t'", "bash -lc \"psql -h rm-bp1690ep53t9778dp -c 'drop table t'\"",
                    "$'\\x73qlcmd' -S rm-bp1690ep53t9778dp -Q 'delete from t'", "bash <<'EOF'\nsqlcmd -S rm-bp1690ep53t9778dp -Q 'delete from t'\nEOF", "env -S 'sqlcmd -S rm-bp1690ep53t9778dp -Q x'", "curl -XPOST https://rm-bp1690ep53t9778dp/x"):
        assert dispatch(payload(hostile))[0] == 2, hostile
    assert dispatch(payload("git commit -m 'rm-bp1690ep53t9778dp 说明'"))[0] == 0 and dispatch(payload("psql -h rm-bp1690ep53t9778dp -U one_evidence_ro -c 'select 1'"))[0] == 0
    assert dispatch(payload("al\"\"iyun rds DeleteAccount --DBInstanceId rm-bp1690ep53t9778dp"))[0] == 2 and "prod_write_gate" in [n for n, _ in selected(json.loads(payload("PSQL -c x")))]

    # 3. background task names (Bash with run_in_background, Agent, Task) reach their gate through the dispatcher
    bad = json.dumps({"tool_name": "Bash", "tool_input": {"command": "ls", "description": "跑测试", "run_in_background": True}})
    assert dispatch(bad)[0] == 2 and "description" in dispatch(bad)[2]
    good = json.dumps({"tool_name": "Bash", "tool_input": {"command": "ls", "description": "#606 复审 (sonnet)", "run_in_background": True}})
    assert dispatch(good) == (0, "", "")
    assert dispatch(json.dumps({"tool_name": "Agent", "tool_input": {"description": "复审", "prompt": "p"}}))[0] == 2
    assert dispatch(json.dumps({"tool_name": "Agent", "tool_input": {"description": "#606 复审 (opus)", "prompt": "p"}})) == (0, "", "")
    assert dispatch(json.dumps({"tool_name": "Read", "tool_input": {"file_path": "x"}})) == (0, "", ""), "other tools start nothing"

    # 3b. letter case and quote-split names reach the gates and are judged like the plain spelling (GOV-1022-COO-10)
    for plain_text, spelled in (("git push --no-verify", "GIT push --no-verify"), ("git push --no-verify", "gi\"\"t push --no-verify"), ("git push --no-verify", "/usr/bin/GIT push --no-verify"),
                                ("git push --no-verify", "Env Git push --no-verify"), ("codex exec p", "CODEX exec p"), ("codex exec p", "co\"\"dex exec p"), ("pi -p x", "PI -p x"),
                                ("pi -p x", "p\"\"i -p x"), ("timeout 9 pi -p x", "TIMEOUT 9 pi -p x"), ("git push --no-verify", "$'git' push --no-verify"), ("git push --no-verify", "eval 'GIT push --no-verify'"),
                                ("git push --no-verify", "echo $(GIT push --no-verify)"), ("codex exec p", "$'codex' exec p"), ("pi -p x", "$'pi' -p x"), ("codex exec p", "echo $(CODEX exec p)")):
        assert dispatch(payload(spelled))[0] == dispatch(payload(plain_text))[0] == 2, spelled
    assert pp.decide("GIT push --no-verify", cwd)[0] == "deny" and pp.decide("gi\"\"t push --no-verify", cwd)[0] == "deny"
    assert pp.decide("CD /tmp && git push --no-verify", cwd)[2] == cwd, "CD is not the cd builtin"
    assert pp.decide("cd /tmp && git push --no-verify", cwd)[2] == "/tmp"
    assert pr.is_pr_create("$'gh' pr create") and pr.is_pr_create("GH PR create") and pr.is_pr_create("g\"\"h pr create") and not pr.is_pr_create("GH PR view 3")
    for spelled in ("GH PR create", "GIT status", "CODEX exec p", "PI -p x"):
        assert selected(json.loads(payload(spelled))), spelled
    assert selected(json.loads(payload("PIP install x"))) == []

    # 4. unreadable input is judged by the gates themselves
    for raw in ("not json", "[1]", "{"):
        code, out, err = dispatch(raw)
        assert code == 2 and err, (raw, code, err)

    # 5. several refusals: the cheap ones come first, and a refusal stops the slow gates
    code, out, err = dispatch(payload("codex exec p; git push --no-verify"))
    assert code == 2 and "标准输入" in err, err

    # 6. warnings are joined into one additionalContext
    code, out, err = dispatch(payload("codex exec -m x -o o.md p < /dev/null"))
    assert code == 0 and json.loads(out)["hookSpecificOutput"]["hookEventName"] == "PreToolUse" and "禁网" in json.loads(out)["hookSpecificOutput"]["additionalContext"], (code, out)

    # 7. end to end as a hook process, also under -I
    for runner in ([sys.executable], [sys.executable, "-I"]):
        result = subprocess.run([*runner, os.path.abspath(__file__)], input=payload("codex exec p"), capture_output=True, text=True, env={"LC_ALL": "C", "PATH": os.environ.get("PATH", "")})
        assert result.returncode == 2 and "标准输入" in result.stderr, result
        result = subprocess.run([*runner, os.path.abspath(__file__)], input=payload("ls"), capture_output=True, text=True, env={"LC_ALL": "C", "PATH": os.environ.get("PATH", "")})
        assert result.returncode == 0 and not result.stdout and not result.stderr, result
    print("pre_tool_dispatch self-test: ok")
    return 0


if __name__ == "__main__":
    sys.exit(self_test() if sys.argv[1:] == ["--self-test"] else main())
