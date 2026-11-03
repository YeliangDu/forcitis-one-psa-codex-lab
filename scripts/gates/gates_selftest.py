#!/usr/bin/env python3
"""门的自测，合一（O8，D25；定稿 1.10「自测 1 个文件」）。`gates_selftest.py [段…]` 跑指定的段，不带参数跑全部；各门的 `--self-test` 只跑自己那一段。
- prod_write_gate：命令原文与特征表字面匹配（用户 intent 2026-10-28-202019：命中即拒、不分大小写、无关命令不误拦、拆开或编码的按设计放行、无表失败关闭）、版式。
- pre_tool_dispatch：预筛不藏拒绝、失败关闭、时限。main_checkout_gate（含 git_ceiling_env.sh）：在临时仓库与 worktree 里端到端提交。pre_commit：.pre-commit-config.yaml 只挂两项、会话改名。
- install：install.sh 与 .claude/settings.json 挂载，全部在临时目录里做，HOME 指向临时目录，不碰真实安装目录（安装、切换、并发、自检不过、只读、篡改核对与修复、挂载失败关闭）。
不写任何真实生产样本的字面值，拆段拼接也不行（O2 复审 M3）：真实特征运行时按 target id 从 prod_targets.json 取（install 段只经 install.sh 自检与探针按位置取用）；其余用虚构样本（192.0.2.x 是文档专用地址）。"""
import ast
import contextlib
import errno
import inspect
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import traceback
import types
from pathlib import Path
from unittest import mock

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, HERE)
import codex_gate  # noqa: E402
import pre_tool_dispatch as d  # noqa: E402
import prod_write_gate as g  # noqa: E402
from prod_write_gate import (COMMAND_TOOLS, LAUNCH_TOOL, could_concern, judge, load_table)  # noqa: E402

PYTHON = "/usr/bin/python3" if os.path.exists("/usr/bin/python3") else sys.executable

def env(**extra):  # a git environment without the caller's config and GIT_* variables
    base = {k: v for k, v in os.environ.items() if not k.startswith("GIT_")}
    return {**base, "GIT_CONFIG_GLOBAL": "/dev/null", "GIT_CONFIG_NOSYSTEM": "1", "GIT_AUTHOR_NAME": "t", "GIT_AUTHOR_EMAIL": "t@t", "GIT_COMMITTER_NAME": "t", "GIT_COMMITTER_EMAIL": "t@t", **extra}

def git(cwd, *args, ok=True, **extra):
    r = subprocess.run(["git", *args], cwd=cwd, capture_output=True, text=True, encoding="utf-8", env=env(**extra))
    assert not ok or r.returncode == 0, f"git {args}: {r.stderr}"
    return r

def put(path, text, mode=0o755):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)
    os.chmod(path, mode)

TEST_IP = "192.0.2.15"  # ── prod_write_gate ── RFC 5737 documentation address, not a production host
SAMPLE_IDS = ("legacy-rds-sqlserver", "legacy-ecs", "new-prod-names")  # P, H, D of the dispatcher and install sections: the first pattern of each of these targets

def shipped_firsts():
    """{target id: its first pattern} of the shipped prod_targets.json, read at run time (the guard sources hold no production sample)."""
    return {t["id"]: t["patterns"][0].strip() for t in json.loads(Path(HERE, "prod_targets.json").read_text(encoding="utf-8"))["targets"]}

def real_samples():
    """(P, H, D) under the real table."""
    firsts = shipped_firsts()
    return tuple(firsts[i] for i in SAMPLE_IDS)

MISBLOCKED = (  # no production sample on the line: allowed (卡 #736; the first four were refused in one-ops' own session on 10-28)
    "grep -rnE 'Closes #' .github scripts 2>/dev/null | grep -v selftest | head -20", "grep -rnlE 'mq\\.py|入队' docs/*.md | head",
    "/usr/bin/python3 -I -c 'import json;d=json.load(open(\"s.json\"));print(d[\"hooks\"])'", "grep -n x deploy.sh",
    "cd scripts/gates && python3 gates_selftest.py", "python3 - <<'EOF'\nprint(1)\nEOF", "setopt nullglob; ls *.md", "ls *(.)", "npm --prefix frontend run build", "make -C backend test", "env -C /tmp ls",
    "echo -e 'a\\tb'", "printf 'a\\n'", "f() { ls; }; f", "x=(1 2); echo $x", "eval \"$(printf x)\"", "git -c core.pager=cat log -1", "PAGER=cat git log -1", "cd pkg && make && ./dist/app",
    "cat <<EOF > n.md\nnotes # x\nEOF", "psql -h localhost -c 'select 1'", "curl -s https://api.github.com/repos/x/y", "ssh dev-box uptime", "scp a dev:/tmp/", "curl -X POST http://localhost:8080/api",
    "aliyun --profile dev-ro ecs DescribeRegions", "./deploy.sh", "bash deploy.sh")

def gate_self_test():
    """用户 intent 2026-10-28-202019「不用，你直接简化」：整条命令原文与特征表字面匹配，不分大小写，命中即拒、没命中放行；不解析写法。"""
    patterns, P, H = ["pgm-prod0001", "db.prod.example.com", TEST_IP], "pgm-prod0001", TEST_IP
    verdict = lambda command, table=patterns, problem=None: judge(command, table, problem)  # noqa: E731
    hit = (f"psql -h {P} -c 'select 1'", f"ssh {H} uptime", f"curl https://{P}/health", "wget https://db.prod.example.com/x", f"git commit -m '{P} 续费说明'", f"grep -rn {P} docs",
           "PGM-PROD0001", "Db.Prod.Example.Com", f"echo ok # x\nls {P}", f"bash -c \"psql -h {P}\"", f"x{P}y", f"echo 'unterminated {P}", f"python3 -c \"connect('{P}')\"")
    for command in hit:
        assert verdict(command) == g.REFUSED, ("a pattern in the command's text, any case: refused", command)
    for command in MISBLOCKED + ("ls", "git status", "", "psql -h dev-db.example.invalid -c x", "echo 192.0.2.150"):
        expected = g.REFUSED if TEST_IP in command else None
        assert verdict(command) == expected, ("no pattern in the text: allowed", command, verdict(command))
    hidden = ("pgm-pr\"\"od0001", "pgm-pr\\od0001", "H=pgm-pr; echo ${H}od0001", "echo pgm-prod{0001,0002}", "curl http://192.0.{2,2}.15/", f"echo {P[::-1]} | rev | sh")
    for command in hidden:  # the text is not parsed: a pattern split, built or encoded is not seen (by design, 202019; the user clears production credentials, 202108)
        assert verdict(command) is None, ("not on the text as written: allowed by design", command)
    assert verdict("ls", None, "表坏了") and verdict("ls -la", None, "表坏了"), "no table: refused (fail closed)"
    with tempfile.TemporaryDirectory() as tmp:
        good = os.path.join(tmp, "t.json")
        for text, ok in ((json.dumps({"targets": [{"id": "a", "patterns": ["abcdef123"]}]}), True), ('{"targets": []}', True), ("not json", False), ("{}", False), ('{"targets": [{"id": "a"}]}', False),
                         ('{"targets": [{"id": "a", "patterns": []}]}', False), ('{"targets": [{"id": "a", "patterns": ["ab"]}]}', False), ('{"targets": [{"id": "a", "patterns": [1]}]}', False), ('{"targets": "x"}', False)):
            open(good, "w").write(text)
            assert (load_table(good)[1] is None) == ok, text
        assert load_table(os.path.join(tmp, "missing.json"))[1] is not None
        table, problem, _ = load_table()
        firsts = shipped_firsts()
        assert problem is None and table and firsts and all(v.lower() in table for v in firsts.values()), "the shipped table loads and every target's first pattern is in it"
        assert not could_concern("") and not could_concern("  ") and could_concern("ls")
        # hook process end to end, with the shipped, a missing and an empty table
        script = os.path.join(HERE, "prod_write_gate.py")
        def hook(command, table_path=None, tool="Bash", env=None, raw=None):  # `env` replaces the hook's environment, `raw` its input
            env = env or {"LC_ALL": "C", "PATH": os.environ.get("PATH", ""), **({"PROD_TARGETS_FILE": table_path} if table_path else {})}
            return subprocess.run([sys.executable, "-I", script], input=raw or json.dumps({"tool_name": tool, "tool_input": {"command": command}, "cwd": tmp}), capture_output=True, text=True, env=env)
        shipped = [(t["id"], p.strip()) for t in json.loads(Path(HERE, "prod_targets.json").read_text(encoding="utf-8"))["targets"] for p in t["patterns"]]
        for at, (name, value) in enumerate(shipped):  # every pattern of the real table (hosts, the production profile, databases, accounts), in either case: refused; failures name the target and position, never the value
            for written in (f"grep -rn {value} docs", f"echo {value.upper()}"):
                run = hook(written)
                assert run.returncode == 2 and "底线 4" in run.stderr, (name, at)
        assert hook("ls").returncode == 0 and hook("x", tool="Read").returncode == 0
        gone, remote = hook("python3 clean.py", os.path.join(tmp, "missing.json")), hook("psql -c 'select 1'", os.path.join(tmp, "missing.json"))
        assert gone.returncode == 2 and remote.returncode == 2 and "缺失或不合格" in gone.stderr, (gone, remote)  # no table: everything refused
        Path(tmp, "empty.json").write_text('{"targets": []}', encoding="utf-8")
        no_git = {"LC_ALL": "C", "PATH": tmp, "PROD_TARGETS_FILE": os.path.join(tmp, "empty.json")}
        assert hook("python3 clean.py", env=no_git).returncode == 2 and "为空" in hook("ls", env=no_git).stderr, "an empty table, no git: refused"
        assert [hook(None, env={"LC_ALL": "C"}, raw=raw).returncode for raw in ("not json", "[1]")] == [2, 2], "input that is no JSON object"
    print(f"prod_write_gate self-test: ok（命中即拒 {len(hit)} 例、真实表 {len(firsts)} 个 target 全部 {len(shipped)} 项各 2 例；无关命令 {len(MISBLOCKED) + 5} 例放行；拆开或编码的 {len(hidden)} 例按设计放行；无表、空表、坏输入拒）")

def layout_self_test():
    """门本体的版式与出处：一行一条语句、块体另起一行（O2 复审 M1）；守护源码里没有生产样本，拆段拼接也算（O2 复审 M3）。"""
    source = Path(HERE, "prod_write_gate.py").read_text(encoding="utf-8")
    lines = source.split("\n")
    raw = [line.encode("utf-8") for line in lines]  # col_offset counts bytes
    crowded = [node.lineno for node in ast.walk(ast.parse(source)) if isinstance(node, ast.stmt) and node.col_offset != len(raw[node.lineno - 1]) - len(raw[node.lineno - 1].lstrip(b" "))]
    assert not crowded, f"prod_write_gate.py 有一行多条语句或块体与条件同行：{sorted(set(crowded))[:20]}"
    folded = [re.sub(r"[^0-9a-z]", "", v.lower()) for v in load_table()[0]]
    for path in [os.path.join(HERE, name) for name in sorted(os.listdir(HERE)) if name.endswith(".py")]:
        text = re.sub(r"[^0-9a-z]", "", Path(path).read_text(encoding="utf-8").lower())
        found = sum(1 for v in folded if v in text)
        assert not found, f"{os.path.basename(path)} 含 {found} 项生产特征（拆段拼接也算），改为运行时从 prod_targets.json 按 id 取"
    print("prod_write_gate layout: ok")

def prod_write_gate_test():
    gate_self_test()
    layout_self_test()

DISPATCH = os.path.join(HERE, "pre_tool_dispatch.py")  # ── pre_tool_dispatch ──
PROD_P, PROD_H, PROD_D = real_samples()
SAMPLES = [f"sqlcmd -S {PROD_P} -Q 'delete from t'", f"ssh {PROD_H} uptime", f"psql -d {PROD_D} -c x", f"echo {PROD_H.upper()}"]  # one per kind, and the case does not matter

def payload(command, **extra):
    return json.dumps({"tool_name": "Bash", "tool_input": {"command": command}, "cwd": extra.pop("cwd", HERE), **extra})

def pre_tool_dispatch_test():
    # 1. the pre-filter never hides a refusal: for every command the dispatcher decides exactly what the gate decides on its own
    corpus = SAMPLES + ["ls", "git status", "echo hi", "cat api.py", "npm test", "", "   ", "python3 x.py", f"git commit -m '{PROD_P} 说明'", f"psql -h {PROD_P} -c 'select 1'", f"aliyun rds DeleteAccount --DBInstanceId {PROD_P}",
               f"aliyun rds DescribeDBInstances --DBInstanceId {PROD_P}", f"python3 -c \"connect('{PROD_P}')\"", f"xargs -I{{}} sqlcmd -S {PROD_P} -Q {{}}", f"sqlcmd -S `echo {PROD_P}` -Q 'delete from t'",
               f"bash -lc \"psql -h {PROD_P} -c 'drop table t'\"", f"$'\\x73qlcmd' -S {PROD_P} -Q 'delete from t'", f"bash <<'EOF'\nsqlcmd -S {PROD_P} -Q 'delete from t'\nEOF", f"env -S 'sqlcmd -S {PROD_P} -Q x'",
               f"curl -XPOST https://{PROD_P}/x", f"al\"\"iyun rds DeleteAccount --DBInstanceId {PROD_P}", "PSQL -c x", "git push --no-verify", "gh pr create --title x"]
    with tempfile.TemporaryDirectory() as elsewhere:
        for command in corpus:
            raw = payload(command, cwd=elsewhere)
            assert d.dispatch(raw) == g.decide(json.loads(raw)), command
        # 1b. a script that names production, run by an interpreter: the gate no longer reads files it runs (卡 #736; known gap, the read-only account holds), dispatcher and gate agree
        for name in ("deploy", "deploy.lua"):
            put(os.path.join(elsewhere, name), f"ssh {PROD_H} rm -rf /srv/x\n", 0o644)
        for command in ("lua deploy.lua", "dash deploy", "ksh deploy", ". deploy", "nohup dash deploy", "lua deploy.lua && echo ok", "cat sshd_conf 'oops", "sudo -u x ksh deploy", "(. deploy)"):
            raw = payload(command, cwd=elsewhere)
            assert g.decide(json.loads(raw))[0] == 0 and d.dispatch(raw) == g.decide(json.loads(raw)), command
    # 2. every production sample is refused through the dispatcher, with the gate's own reason
    for command, tool in ((c, t) for c in SAMPLES for t in COMMAND_TOOLS):  # Monitor and the terminal panel run shell commands as Bash does (intent 160437)
        code, out, err = d.dispatch(payload(command, tool_name=tool))
        assert code == 2 and "底线 4" in err, (command, tool)
    # 3. ordinary commands and other tools start nothing; checks that used to hang off the dispatcher are no longer here
    for command in ("ls", "echo hi", "git status", "npm test", "git push --no-verify", "gh pr create --title x"):
        assert d.dispatch(payload(command)) == (0, "", ""), command
    # preview_start runs the named .claude/launch.json configuration: judged as that command; a url starts nothing; an unreadable configuration is refused
    with tempfile.TemporaryDirectory() as launch, tempfile.TemporaryDirectory() as other, mock.patch.dict(os.environ, CLAUDE_PROJECT_DIR=launch):  # the app reads the session's working directory
        configs = [{"name": "w", "runtimeExecutable": "psql", "runtimeArgs": ["-h", PROD_H, "-c", "delete from t"]}, {"name": "ok", "runtimeExecutable": "npm", "runtimeArgs": ["run", "dev"]},
                   {"name": "args", "runtimeExecutable": "psql", "args": ["-h", PROD_H, "-c", "delete from t"]}, {"name": "prog", "runtimeExecutable": "sh", "runtimeArgs": ["-c"], "program": f"psql -h {PROD_H} -c 'delete from t'"},
                   {"name": "envw", "env": {"PGHOST": PROD_H}, "runtimeExecutable": "psql", "runtimeArgs": ["-c", "delete from t"]},
                   {"name": "twice", "runtimeExecutable": "npm"}, {"name": "TWICE", "runtimeExecutable": "npm"},
                   {"name": "cwdpy", "runtimeExecutable": "/usr/bin/python3", "runtimeArgs": ["run.py", PROD_H], "cwd": "sub"}, {"name": "var", "runtimeExecutable": "npm", "runtimeArgs": ["${env:X}"]}]
        put(os.path.join(launch, ".claude", "launch.json"), json.dumps({"configurations": configs}), 0o644)
        put(os.path.join(other, ".claude", "launch.json"), json.dumps({"configurations": [{"name": "w", "runtimeExecutable": "npm"}]}), 0o644)
        put(os.path.join(other, "unnamed", ".claude", "launch.json"), json.dumps({"configurations": [{"runtimeExecutable": "psql", "runtimeArgs": ["-h", PROD_H]}, {"name": "ok", "runtimeExecutable": "npm", "runtimeArgs": ["run", "dev"]}]}), 0o644)
        for tool_input, where, code in (({"name": "w"}, launch, 2), ({"name": "ok"}, launch, 0), ({"url": "http://localhost:3000"}, launch, 0), ({"name": "missing"}, launch, 2),
                                        ({"name": "args"}, launch, 2), ({"name": "prog"}, launch, 2), ({"name": "w"}, other, 2), ({"name": "ok"}, other, 2), ({"name": "W"}, launch, 2),
                                        ({"name": "envw"}, launch, 2), ({"name": "twice"}, launch, 2), ({"name": "cwdpy"}, launch, 2), ({"name": "var"}, launch, 2)):  # other: a second launch.json disagrees or lacks the name; W: case ignored
            assert d.dispatch(json.dumps({"tool_name": LAUNCH_TOOL, "tool_input": tool_input, "cwd": where}))[0] == code, (tool_input, where)
        assert g.launch_command({"name": "ok"}, os.path.join(other, "unnamed"))[0] is None, "an unnamed entry: the app derives its name, so which one runs cannot be seen (69af6c8da review M2)"
        assert g.launch_command({"name": "cwdpy"}, launch)[0].startswith("cd sub && "), "the configuration's cwd runs first: a relative script there is another file"
    monitor_ws = json.dumps({"tool_name": "Monitor", "tool_input": {"ws": {"url": "wss://x.example.invalid"}, "description": "d", "timeout_ms": 1000}})  # runs no command
    for raw in (monitor_ws, json.dumps({"tool_name": "Agent", "tool_input": {"description": "复审", "prompt": "p"}}), json.dumps({"tool_name": "Read", "tool_input": {"file_path": "x"}}),
                json.dumps({"tool_name": "Bash", "tool_input": {"command": "ls", "description": "跑测试", "run_in_background": True}})):
        assert d.dispatch(raw) == (0, "", ""), raw
    # a Bash call without a string command fails closed (O3 review, suggestion 2; it used to pass)
    for raw in (json.dumps({"tool_name": "Bash", "tool_input": {}}), json.dumps({"tool_name": "Bash", "tool_input": {"command": 1}}), json.dumps({"tool_name": "Bash"}), json.dumps({"tool_input": {"command": None}})):
        code, out, err = d.dispatch(raw)
        assert code == 2 and "按拒绝处理" in err and g.decide(json.loads(raw))[0] == 2, raw
    # 4. unreadable input is refused; a gate that cannot be imported, crashes or hangs exits 3 (settings.json's entry then runs its own pre-filter, C3)
    for raw in ("not json", "[1]", "{"):
        code, out, err = d.dispatch(raw)
        assert code == 2 and "拒绝" in err, raw
    def broken_import():
        raise ImportError("no prod_write_gate")
    def boom(payload):
        raise RuntimeError("boom")
    Crashing, Hanging = (types.SimpleNamespace(command_of=g.command_of, could_concern=lambda text: True, decide=decide)  # the gate's own parsing: decide is reached
                         for decide in (boom, lambda payload: time.sleep(5) or (0, "", "")))  # #47: without command_of main stopped at an AttributeError, the hang went untested
    with mock.patch.object(sys, "stdin"), mock.patch.object(d, "DEADLINE", 1):  # a gate with no answer in time is a failure (O7 final, suggestion 1)
        for loader, why in ((broken_import, "ImportError: no prod_write_gate"), (lambda: Crashing, "RuntimeError: boom"), (lambda: Hanging, "Overdue: 超过 1 秒没有结论")):
            sys.stdin = io.StringIO(payload(f"psql -h {PROD_P} -c 'select 1'"))
            with contextlib.redirect_stderr(io.StringIO()) as err:
                code = d.main(loader)
            assert code == 3 and why in err.getvalue() and "生产写门出错" in err.getvalue() and "保守预筛" in err.getvalue(), (why, code, err.getvalue())
    # 4b. the deadline holds while the gate waits on git (O7 fix final M1): a git that sleeps is cut at DEADLINE, not swallowed by the gate's `except OSError`
    with tempfile.TemporaryDirectory() as fake:
        put(os.path.join(fake, "git"), "#!/bin/bash\nexec sleep 30\n")
        run = "import sys;sys.path.insert(0,sys.argv[1]);import pre_tool_dispatch as d;d.DEADLINE=2;sys.exit(d.main())"
        start = time.monotonic()
        slow = subprocess.run([sys.executable, "-c", run, HERE], input=payload("psql -h localhost -c 'select 1'", cwd=g.REPO_ROOT), capture_output=True, text=True, env={"LC_ALL": "C", "PATH": fake + os.pathsep + os.environ.get("PATH", "")}, timeout=60)
        assert slow.returncode == 3 and "生产写门出错" in slow.stderr and "Overdue" in slow.stderr and time.monotonic() - start < 8, (slow.returncode, slow.stderr, time.monotonic() - start)
    # 4c. the codex gate failing is its own failure: the dispatcher exits 3 (the mount's pre-filter then refuses codex), the production gate alone still answers other commands
    def codex_import():
        raise ImportError("no codex_gate")
    with mock.patch.object(sys, "stdin"):
        for loader, why in ((codex_import, "ImportError: no codex_gate"), (lambda: types.SimpleNamespace(decide=lambda p, c: 1 / 0), "ZeroDivisionError")):
            sys.stdin = io.StringIO(payload("codex exec --sandbox workspace-write 'a b'"))
            with contextlib.redirect_stderr(io.StringIO()) as err:
                code = d.main(d.load_gate, loader)
            assert code == 3 and why in err.getvalue() and "codex 门出错同此处理" in err.getvalue(), (why, code, err.getvalue())
    # 5. end to end as a hook process under the system python, also with -I
    env = {"LC_ALL": "C", "PATH": os.environ.get("PATH", "")}
    for runner in (["/usr/bin/python3"], ["/usr/bin/python3", "-I"]) if os.path.exists("/usr/bin/python3") else ([sys.executable], [sys.executable, "-I"]):
        refused, passed, bad = (subprocess.run([*runner, DISPATCH], input=text, capture_output=True, text=True, env=env) for text in (payload(f"aliyun rds DeleteAccount --DBInstanceId {PROD_P}"), payload("ls"), "not json"))
        assert refused.returncode == 2 and "底线 4" in refused.stderr and passed.returncode == 0 and not passed.stdout and not passed.stderr and bad.returncode == 2 and "拒绝" in bad.stderr, (refused, passed, bad)
    print("pre_tool_dispatch self-test: ok")

MAIN_GATE = os.path.join(HERE, "main_checkout_gate.py")  # ── main_checkout_gate ──
REFUSED = "拒绝：这是在主检出"
FAILED = "主检出提交保护出错"

def run(*args, cwd, **extra):
    return subprocess.run(args, cwd=cwd, capture_output=True, text=True, env=env(**extra))

def checkout_gate(cwd, **extra):
    r = run(PYTHON, "-I", MAIN_GATE, "--pre-commit", cwd=cwd, **extra)
    return r.returncode, r.stderr

def main_checkout_gate_test():
    with tempfile.TemporaryDirectory() as raw:
        d = os.path.realpath(raw)
        main = os.path.join(d, "repo")
        git(d, "init", "-q", "-b", "main", main)
        git(main, "commit", "-q", "--allow-empty", "-m", "one")
        between = os.path.join(main, ".claude", "worktrees")
        work = os.path.join(between, "w1")
        git(main, "worktree", "add", "-q", work, "-b", "side")
        # 1. the verdicts, asked directly
        code, message = checkout_gate(main)
        assert code == 1 and REFUSED in message and main in message, f"main checkout on main must be refused: {code} {message}"
        code, message = checkout_gate(between)
        assert code == 1 and REFUSED in message, "from .claude/worktrees/ itself git finds the main checkout (the 2026-10-21 incident)"
        assert checkout_gate(os.path.join(main, ".claude"))[0] == 1, "a subdirectory of the main checkout is the main checkout"
        assert checkout_gate(work) == (0, ""), "a commit in a linked worktree goes ahead"
        git(main, "checkout", "-q", "-b", "elsewhere")
        code, message = checkout_gate(main)
        assert code == 1 and REFUSED in message and "refs/heads/elsewhere" in message, "the main checkout on another branch is refused too (intent 225957)"
        w2 = os.path.join(between, "w2")
        git(main, "worktree", "add", "-q", w2, "main")
        assert checkout_gate(w2)[0] == 0, "a linked worktree that has main checked out is not the main checkout"
        git(main, "worktree", "remove", "--force", w2)
        git(main, "checkout", "-q", "--detach", "main")
        assert checkout_gate(main)[0] == 1, "detached HEAD in the main checkout is refused: commit, then update-ref main, moves main in two steps"
        git(main, "checkout", "-q", "main")
        # 2. fails closed: no repository, no git, git that says nonsense or fails
        lonely, empty, fake = (os.makedirs(os.path.join(d, name)) or os.path.join(d, name) for name in ("lonely", "empty-bin", "fake-bin"))
        code, message = checkout_gate(lonely, GIT_CEILING_DIRECTORIES=d)
        assert code == 1 and FAILED in message, f"no repository: {code} {message}"
        code, message = checkout_gate(main, PATH=empty)
        assert code == 1 and FAILED in message and "git 跑不起来" in message, f"git not found: {code} {message}"
        for name, body in (("garbage", "echo only-one-line"), ("broken", "echo boom >&2; exit 128")):
            put(os.path.join(fake, "git"), f"#!/bin/sh\n{body}\n")
            code, message = checkout_gate(main, PATH=fake + os.pathsep + "/bin:/usr/bin")
            assert code == 1 and FAILED in message, f"fake git ({name}) must be refused: {code} {message}"
        # 3. end to end: a real `git commit` through a pre-commit hook (git sets GIT_DIR / GIT_INDEX_FILE for hooks)
        hooks = os.path.join(d, "hooks")
        put(os.path.join(hooks, "pre-commit"), f"#!/bin/sh\nexec python3 -I '{MAIN_GATE}' --pre-commit\n")
        git(main, "config", "core.hooksPath", hooks)
        before = git(main, "rev-parse", "main").stdout
        r = run("git", "commit", "-q", "--allow-empty", "-m", "x", cwd=main)
        assert r.returncode != 0 and REFUSED in r.stderr and git(main, "rev-parse", "main").stdout == before, f"real commit on the main checkout's main must fail: {r.stderr}"
        r = run("git", "commit", "-q", "--allow-empty", "-m", "x", cwd=between)
        assert r.returncode != 0 and REFUSED in r.stderr, "real commit from .claude/worktrees/ must fail"
        r = run("git", "commit", "-q", "--allow-empty", "-m", "x", cwd=work)
        assert r.returncode == 0, f"real commit in the worktree must go ahead: {r.stderr}"
        os.makedirs(os.path.join(work, "sub"))
        r = run("git", "commit", "-q", "--allow-empty", "-m", "x", cwd=os.path.join(work, "sub"))
        assert r.returncode == 0, f"a commit from a worktree subdirectory (git sets GIT_PREFIX) goes ahead: {r.stderr}"
        # the repository is named from the worktree: git hands the hook the main checkout's git-dir (O4 review item 1)
        main_git = os.path.join(main, ".git")
        for argv, extra in ((("git", "commit", "-q", "--allow-empty", "-m", "d"), {"GIT_DIR": main_git, "GIT_WORK_TREE": work}), (("git", "--git-dir=" + main_git, "--work-tree=" + work, "commit", "-q", "--allow-empty", "-m", "e"), {})):
            r = run(*argv, cwd=work, **extra)
            assert r.returncode != 0 and REFUSED in r.stderr and git(main, "rev-parse", "main").stdout == before, (argv, extra, r.returncode, r.stderr)
        # 4. the session-start script: ceiling at <main checkout>/.claude, once however often it runs
        env_file = os.path.join(d, "claude.env")
        script = os.path.join(ROOT, "scripts", "hooks", "git_ceiling_env.sh")
        for _ in range(2):
            r = subprocess.run(["bash", script], cwd=work, capture_output=True, text=True, env=env(CLAUDE_ENV_FILE=env_file, CLAUDE_PROJECT_DIR=work))
            assert r.returncode == 0, r.stderr
        assert open(env_file, encoding="utf-8").read().splitlines() == [f"export GIT_CEILING_DIRECTORIES={main}/.claude"]
        ceiling = env(GIT_CEILING_DIRECTORIES=os.path.join(main, ".claude"))
        assert subprocess.run(["git", "rev-parse", "--show-toplevel"], cwd=between, capture_output=True, env=ceiling).returncode != 0, "with the ceiling, git finds nothing from .claude/worktrees/"
        assert subprocess.run(["git", "rev-parse", "--show-toplevel"], cwd=work, capture_output=True, env=ceiling).returncode == 0, "the worktree itself is found"
        assert subprocess.run(["bash", script], cwd=work, capture_output=True, env=env()).returncode == 0, "without CLAUDE_ENV_FILE it does nothing"
    # 5. wiring
    config = Path(ROOT, ".pre-commit-config.yaml").read_text(encoding="utf-8")
    block = re.search(r"- id: main-checkout-gate\n(?:\s{8,}.*\n)+", config)
    assert block and "entry: python3 -I scripts/gates/main_checkout_gate.py --pre-commit" in block.group(0) and "stages: [pre-commit, pre-merge-commit]" in block.group(0), "pre-commit wiring"
    assert not os.path.exists(os.path.join(ROOT, "scripts", "hooks", "main_checkout_gate.py")), "the old gate is gone"
    print("main_checkout_gate self-test: ok")

def renamed_test():  # ── pre_commit ──
    """#744：会话名随角色（one-lead、one-design、one-test、one-platform、one-dev-<bc>），都在 sessions.config。#856：旧名不再登记（没有 sessions.aliases），
    会话由工作区会话标记认定；合入队列的分支归属（gapscan.load_owners）只认 sessions.config 的键，旧前缀的分支报未知。"""
    sessions, root = policy("sessions"), Path(ROOT)
    assert set(sessions["config"]) == {"one-lead", "one-design", "one-platform", "one-dev-org", "one-dev-identity", "one-test"}, sorted(sessions["config"])
    for name, role in (("one-lead", "lead"), ("one-design", "design"), ("one-platform", "platform"), ("one-dev-org", "dev"), ("one-dev-identity", "dev"), ("one-test", "test")):
        assert sessions["config"][name]["role"] == role, name
    assert "aliases" not in sessions, "no old names are kept (#856)"
    assert not (root / "scripts/merge/session_map.json").exists(), "branch owners come from policy.json only (#754)"
    sys.path.insert(0, str(root / "scripts" / "merge"))
    import gapscan
    owners = gapscan.load_owners()
    assert all(gapscan.author_session(b, owners) == o for b, o in (("claude/one-lead-x", "one-lead"), ("claude/one-dev-org-x", "one-dev-org"), ("claude/one-dev-identity-x", "one-dev-identity"),
                                                               ("claude/one-platform-810-x", "one-platform"), ("claude/one-design-860-x", "one-design"),
                                                               ("claude/one-ops-744-x", "?(claude/one-ops-744-x)"))), "only current names own branches"

def pre_commit_test():
    """.pre-commit-config.yaml 只挂 gitleaks 与主检出守护（卡 #736：提交时不按路径拆守护文件；#742：commit_msg_gate 的 Session 追加已删）。"""
    config = Path(ROOT, ".pre-commit-config.yaml").read_text(encoding="utf-8")
    assert re.findall(r"- id: ([\w-]+)", config) == ["gitleaks", "main-checkout-gate"], re.findall(r"- id: ([\w-]+)", config)
    types = re.search(r"^default_install_hook_types: \[([^\]]*)\]", config, re.M)
    assert types and [t.strip() for t in types.group(1).split(",")] == ["pre-commit", "pre-merge-commit"], "no commit-msg hook type is installed any more"
    renamed_test()
    for gone in ("scripts/gates/commit_msg_gate.py", "scripts/hooks/commit_msg_gate.py", "scripts/hooks/task_ids.py", "scripts/hooks/command_words.py"):
        assert not os.path.exists(os.path.join(ROOT, gone)), gone + " is deleted"
    print("pre_commit self-test: ok")

FAKE = "fake-prod-db.example.invalid"  # ── install ── fictitious production host, put on the temporary repository's origin/main table only
ENTRIES = {"PreToolUse": ["scripts/gates/pre_tool_dispatch.py"], "SessionStart": ["scripts/hooks/git_ceiling_env.sh", "scripts/hooks/stop_language.py"], "Stop": ["scripts/hooks/stop_language.py", "scripts/hooks/card_handover.py"]}  # the start sheet hangs on the tool library (O23a)
SHELLS = ("/bin/sh", "/bin/bash")
FIRST_ONLY = """#!/bin/bash
read -r line
first=$(/usr/bin/python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["targets"][0]["patterns"][0])' "$(dirname "$0")/prod_targets.json")
case "$line" in *"$first"*) exit 2 ;; "{"*) exit 0 ;; *) exit 2 ;; esac
"""  # refuses writes to the first target only

def writable(root):
    """Gives the owner write access again under `root` (installed versions are read-only), so they can be edited or removed."""
    for base, dirs, files in os.walk(root):
        os.chmod(base, 0o755)
        for name in files:
            path = os.path.join(base, name)
            if not os.path.islink(path):
                os.chmod(path, os.stat(path).st_mode | 0o200)

def gitc(repo, *args):
    return subprocess.run(["git", "-C", repo, "-c", "user.name=t", "-c", "user.email=t@t", "-c", "core.hooksPath=/dev/null", *args], check=True, capture_output=True, text=True).stdout.strip()

def policy(section="gates"):
    return json.loads(Path(ROOT, "policy.json").read_text(encoding="utf-8"))[section]

def sheet_path():  # the tool library's start sheet as the SessionStart mount spells it (O23a: the repository's start_sheet.py and session_context.py are gone)
    return '"$HOME"/' + policy("tools_lib")["dir"][2:] + "/start_sheet/start_sheet.py"

def settings_self_test():
    raw = Path(ROOT, ".claude/settings.json").read_text(encoding="utf-8")
    settings, gates = json.loads(raw), policy()
    assert gates["install_dir"].startswith("~/"), gates["install_dir"]
    link = '"$HOME"/' + gates["install_dir"][2:] + "/" + gates["install_current_link"]
    prefix = "H=" + link + "/"
    verify = ('V="$CLAUDE_PROJECT_DIR"/scripts/gates/hooks_manifest.sh; if [ -x "$V" ]; then "$V" --check ' + link + "; "
              'else echo "【钩子告警】核对不了已装钩子（$V 缺失或不可执行）"; fi; ')  # runs from the session's repository, not from current (O7 final M2)
    hooks = settings["hooks"]
    assert "node_ready" not in raw and "stop_check" not in raw and "clear_session" not in raw, "#752: no hook points at the retired tools (stop_check, the clear_session gate)"
    assert sorted(hooks) == ["PreToolUse", "SessionStart", "Stop"], sorted(hooks)  # #900/#1080: Stop carries the language rule and the card handover, no retired tool
    assert [g["matcher"] for g in hooks["PreToolUse"]] == ["|".join(COMMAND_TOOLS + (LAUNCH_TOOL,))], "PreToolUse: only the production-write gate on the tools that run commands (#752 retired the clear_session gate)"
    assert json.dumps([*COMMAND_TOOLS, LAUNCH_TOOL]) in hooks["PreToolUse"][0]["hooks"][0]["command"], "the mount's own pre-filter names the same tools"
    assert [g["matcher"] for g in hooks["SessionStart"]] == ["startup|clear|compact|resume", "startup|clear|compact", "compact|resume"]
    sheet = hooks["SessionStart"].pop(1)["hooks"]
    assert len(sheet) == 1 and sheet[0]["type"] == "command" and sheet[0]["timeout"] == 15, sheet
    sheet = sheet[0]["command"]
    assert sheet.startswith(f'H={sheet_path()}; [ -f "$H" ] && /usr/bin/python3 -I "$H" --hook && exit 0; echo "【钩子告警】') and sheet.endswith('"; exit 0'), sheet
    assert "exit 2" not in sheet and "/tools/hooks/" not in sheet and "~/" + policy("tools_lib")["dir"][2:] + "/start_sheet" in sheet, "warns, pointing at the tool library"
    for gone in ("scripts/hooks/start_sheet.py", "scripts/hooks/session_context.py"):
        assert not os.path.exists(os.path.join(ROOT, gone)) and gone not in raw, gone + " is deleted and mounted nowhere (O23a)"
    commands = {}
    for event, groups in hooks.items():
        assert all(len(g["hooks"]) == 1 for g in groups), event
        found = []
        for group in groups:
            hook = group["hooks"][0]
            command = hook["command"][len(verify):] if group is hooks["SessionStart"][0] else hook["command"]
            assert (group is hooks["SessionStart"][0]) == hook["command"].startswith(verify), (event, hook["command"])
            head = command.split(";")[0]
            assert hook["type"] == "command" and head.startswith(prefix), (event, hook["command"])
            found.append(head[len(prefix):])
            commands[found[-1]] = (event, hook["command"], hook["timeout"])
        assert found == ENTRIES[event], (event, found)
    gate = commands["scripts/gates/pre_tool_dispatch.py"]
    assert gate[2] == 120 and gate[1].endswith("exit 2") and "/usr/bin/python3 -I -c" in gate[1], "the gate mount: when the gate fails its own pre-filter decides, refusing by default (H4, C3)"
    tables = ['os.path.expanduser("~/' + gates["install_dir"][2:] + "/" + gates["install_current_link"] + '/scripts/gates/prod_targets.json")', '"scripts/gates/prod_targets.json"']  # #736: the same feature table, installed and the project's
    assert all(t in gate[1] for t in tables) and "far=" not in gate[1] and "ok=set" not in gate[1] and "not t or" in gate[1], "pre-filter reads the feature table, fails closed without one"
    for flag, event, index in (("--stop", "Stop", 0), ("--session-start", "SessionStart", -1)):  # #900: the language rule fails open (exit 0, no output) and is cut at 5 s
        hook = hooks[event][index]["hooks"][0]
        assert hook["command"].endswith(f'/usr/bin/python3 -I "$H" {flag}; exit 0') and "exit 2" not in hook["command"] and hook["timeout"] == 5, (event, hook)
    assert all(g.get("matcher") is None for g in hooks["Stop"]) and len(hooks["Stop"]) == 2
    handover = hooks["Stop"][1]["hooks"][0]  # #1080: card_handover passes only its exit 2 on (the block); a missing or failing hook is exit 0, and its gh calls are cut at 30 s
    assert handover["command"].endswith('/usr/bin/python3 -I "$H" --stop; c=$?; [ "$c" = 2 ] && exit 2; exit 0') and handover["timeout"] == 30, handover
    ceiling = commands["scripts/hooks/git_ceiling_env.sh"]
    assert ceiling[1].endswith("; exit 0") and "【钩子告警】" in ceiling[1] and "exit 2" not in ceiling[1] and ceiling[2] == 10, ceiling
    # an installed target without its exec bit never runs (O6 review item 1); the tool library's start sheet runs through python3, so it needs none
    for name in [*commands, "scripts/gates/hooks_manifest.sh"]:
        mode = gitc(ROOT, "ls-files", "-s", "--", name).split()[:1]
        assert mode == ["100755"], (name, mode)
    deny = settings.get("permissions", {}).get("deny", [])  # #1180 (user decision 2026-11-03): the blanket codex deny is gone, codex_gate allows one shape and refuses the rest; pi stays denied
    assert deny == ["Bash(pi *)", "mcp__terminal__run_in_terminal"], ("permissions.deny 恰为 pi 直接调用与整个终端面板工具（#723）；codex 由 codex_gate 白名单管（#1180）", deny)
    for plugin in ("kotlin-lsp", "claude-code-setup", "code-modernization"):
        assert settings["enabledPlugins"][f"{plugin}@claude-plugins-official"] is False, plugin
    return {**{name: cmd for name, (_, cmd, _) in commands.items()}, "start_sheet": sheet}

def make_repo(repo):
    """A repository holding this working tree's scripts/gates, scripts/hooks and policy.json; returns (A, B, fail_open, refuses_all, passes_writes, first_only, not_executable, main) commits."""
    names = subprocess.run(["git", "-C", ROOT, "ls-files", "--cached", "--others", "--exclude-standard", "--", "scripts/gates", "scripts/hooks", "policy.json"], check=True, capture_output=True, text=True).stdout.split()
    os.makedirs(repo)
    gitc(repo, "init", "-q", "-b", "main")
    for name in names:
        if os.path.isfile(os.path.join(ROOT, name)):
            os.makedirs(os.path.dirname(os.path.join(repo, name)) or repo, exist_ok=True)
            shutil.copy2(os.path.join(ROOT, name), os.path.join(repo, name))
    commits = []
    def commit(message):
        gitc(repo, "add", "-A")
        gitc(repo, "commit", "-q", "-m", message)
        commits.append(gitc(repo, "rev-parse", "HEAD"))
    commit("A")
    with open(os.path.join(repo, "scripts/hooks/git_ceiling_env.sh"), "a", encoding="utf-8") as f:
        f.write("# B\n")
    commit("B")
    dispatcher = os.path.join(repo, "scripts/gates/pre_tool_dispatch.py")
    original = open(dispatcher, encoding="utf-8").read()
    for gate, message, mode in (("#!/bin/bash\ncat >/dev/null\nexit 0\n", "fail open", 0o755),  # a gate that lets everything through
                                ("#!/bin/bash\ncat >/dev/null\nexit 2\n", "refuses all", 0o755),  # a gate that refuses everything: every session's Bash would stop
                                ("#!/bin/bash\nread -r line\ncase \"$line\" in \"{\"*) exit 0 ;; *) exit 2 ;; esac\n", "passes writes", 0o755),  # refuses unreadable input, lets production writes through
                                (FIRST_ONLY, "first target only", 0o755), (original, "not executable", 0o644)):
        put(dispatcher, gate, mode)
        commit(message)
    gitc(repo, "checkout", "-q", commits[1])
    table = os.path.join(repo, "scripts/gates/prod_targets.json")
    data = json.load(open(table, encoding="utf-8"))
    data["targets"].append({"id": "fake-only-on-main", "patterns": [FAKE]})
    put(table, json.dumps(data), 0o644)
    commit("origin/main table")
    gitc(repo, "update-ref", "refs/remotes/origin/main", commits[-1])
    gitc(repo, "checkout", "-q", commits[0])
    return commits

def install_test():
    commands = settings_self_test()
    gate_cmd, ceiling_cmd, sheet_cmd = (commands[k] for k in ("scripts/gates/pre_tool_dispatch.py", "scripts/hooks/git_ceiling_env.sh", "start_sheet"))
    with tempfile.TemporaryDirectory() as top:
        try:
            body(gate_cmd, ceiling_cmd, sheet_cmd, os.path.realpath(os.path.join(top, "dir with space")))
        finally:
            writable(top)
    print("install self-test: ok")

def sheet_test(sheet_cmd, mount, tool, repo):
    """The start-sheet mount: /usr/bin/python3 -I <tool library>/start_sheet.py --hook gets the payload (no exec bit, no planted json.py); missing, failing, killed, unparsable: warn, exit 0."""
    os.makedirs(os.path.dirname(tool))
    put(os.path.join(os.path.dirname(tool), "json.py"), "raise SystemExit(7)\n", 0o644)
    for text, ok in ((None, False), ("import json,sys\nprint('【开工单】', sys.argv[1:], json.load(sys.stdin)['cwd'])\n", True), ("import sys\nsys.exit(1)\n", False),
                     ("import os,signal\nos.kill(os.getpid(), signal.SIGKILL)\n", False), ("def (:\n", False)):
        if text is not None:
            put(tool, text, 0o644)
        for shell in SHELLS:
            r = mount(sheet_cmd, json.dumps({"cwd": repo}), shell)
            last = r.stdout.splitlines()[-1] if r.stdout.strip() else ""
            assert r.returncode == 0 and (last == f"【开工单】 ['--hook'] {repo}" if ok else last.startswith("【钩子告警】") and tool in last), (shell, text, r.stdout, r.stderr)

def body(gate_cmd, ceiling_cmd, sheet_cmd, tmp):
    home, repo = os.path.join(tmp, "home"), os.path.join(tmp, "repo")
    os.makedirs(home)
    a, b, fail_open, refuses_all, passes_writes, first_only, not_exec, on_main = make_repo(repo)
    hooks = os.path.join(home, policy()["install_dir"][2:])
    current = os.path.join(hooks, policy()["install_current_link"])
    env = {"PATH": os.environ["PATH"], "HOME": home, "LC_ALL": "C"}
    def install(*args, **extra):
        return subprocess.run([os.path.join(repo, "scripts/gates/install.sh"), *args], capture_output=True, text=True, env={**env, **extra}, cwd=tmp)
    def mount(cmd, stdin, shell="/bin/sh", **extra):
        cwd = extra.pop("cwd", repo)
        run_env = {k: v for k, v in {**env, "CLAUDE_PROJECT_DIR": repo, **extra}.items() if v is not None}
        return subprocess.run([shell, "-c", cmd], input=stdin, capture_output=True, text=True, env=run_env, cwd=cwd, timeout=60)
    def bash(command): return json.dumps({"tool_name": "Bash", "tool_input": {"command": command}, "cwd": repo})
    def points_to(): return os.readlink(current) if os.path.islink(current) else None
    def leftovers(): return [n for n in os.listdir(hooks) if n.startswith(".")] if os.path.isdir(hooks) else []
    def closed(why, shells=SHELLS, extra=()):  # the gate failed: settings.json's own pre-filter decides (H4, C3) with the same feature table and the same literal match (用户 intent 2026-10-28-202019); no table refuses all
        for shell in shells:
            for command, code in (("ls", 0), ("ls -la | wc -l", 0), ("git status", 0), ("gh pr list", 0), ("git -c core.pager='sh w.sh' log -1", 0), ("GIT_EXTERNAL_DIFF=./w.sh git diff", 0), ("PATH=./bin:/usr/bin ls", 0),
                                  ("echo x > /dev/tcp/10.0.0.5/6379", 0), ("sort --compress-program=./w.sh x", 0), (f"psql -h {PROD_P} -c 'delete from t'", 2), ("./deploy.sh", 0), ("echo `deploy`", 0), ("rg --pre deploy x", 0),
                                  ("ssh dev uptime", 0), ("curl -s https://x.example.invalid", 0), ("codex exec --sandbox workspace-write 'a b'", 2), ('co""dex exec x', 2), ("$'\\x63odex' exec x", 2),
                                  ("co{dex,} exec x", 2), ("co{d..d}ex exec x", 2), ("co\\\ndex exec x", 2), ("cod?x exec x", 2), ("c\\odex exec x", 2), ("ls src/{a,b}", 2), ("git commit -m 'codex'", 2), ("echo CoDeX", 2),   # #1180: a broken gate refuses codex, even the one allowed shape
                                   (f"grep -rn {PROD_H} docs", 2), (f"s''sh {PROD_H} uptime", 2), (f"echo {PROD_D.upper()}", 2),
                                  (f"c\\url https://{PROD_P[:3]}\\{PROD_P[3:]}/", 0),  # split by a backslash: not on the text as written, allowed by design (202019)
                                  *((c, 0) for c in MISBLOCKED), *((c, 2) for c in extra)):
                r = mount(gate_cmd, bash(command), shell)
                assert r.returncode == code and why in r.stderr and ("放行" in r.stderr) == (code == 0) and os.path.isfile(os.path.join(hooks, "gate_failures.log")) == os.path.isdir(hooks), (shell, why, command, r.returncode, r.stderr)
            r = mount(gate_cmd, bash("ls"), shell, CLAUDE_PROJECT_DIR=tmp)  # no table anywhere (nothing installed, none in the project): every command refused
            assert r.returncode == 2 or os.path.isfile(os.path.join(current, "scripts/gates/prod_targets.json")), ("no feature table: fail closed", shell, r.returncode, r.stderr)
            r = mount(ceiling_cmd, json.dumps({"cwd": repo}), shell)
            assert r.returncode == 0 and r.stdout.splitlines()[-1].startswith("【钩子告警】"), (shell, why, r.returncode, r.stdout, r.stderr)
    # 1. nothing installed: the pre-filter decides (an input it cannot read is refused), SessionStart warns and passes
    closed("入口缺失")
    assert mount(gate_cmd, "not json").returncode == 2 and mount(gate_cmd, json.dumps({"tool_name": "Bash", "tool_input": {}})).returncode == 2
    for tool, command, code in ((t, c, r) for t in COMMAND_TOOLS[1:] for c, r in ((f"psql -h {PROD_P} -c 'delete from t'", 2), ("ls", 0))):  # Monitor, the terminal panel
        assert mount(gate_cmd, bash(command).replace('"Bash"', f'"{tool}"')).returncode == code, (tool, command)
    # 2. first install: <install_dir>/<full sha>/, current a relative link to it, nothing half-made left
    r = install()
    assert r.returncode == 0 and points_to() == a and os.path.isdir(os.path.join(hooks, a)) and not leftovers(), (r.stdout, r.stderr, points_to())
    for shell in SHELLS:
        r = mount(gate_cmd, bash("ls -la"), shell)
        assert r.returncode == 0 and not r.stderr, (shell, r.stdout, r.stderr)
        r = mount(gate_cmd, bash(f"psql -h {FAKE} -c 'delete from t'"), shell)  # the installed gate asks CLAUDE_PROJECT_DIR's repository for origin/main's table
        assert r.returncode == 2 and r.stderr, ("a pattern only on origin/main is refused by the installed gate", shell, r.returncode, r.stderr)
        r = mount(gate_cmd, "not json", shell)
        assert r.returncode == 2 and "不是 JSON" in r.stderr and "异常退出" not in r.stderr, "the gate's own refusal passes through"
        r = mount(ceiling_cmd, "{}", shell, CLAUDE_ENV_FILE=os.path.join(tmp, "env"))
        assert r.returncode == 0 and "【钩子告警】" not in r.stdout, (r.stdout, r.stderr)
    sheet_test(sheet_cmd, mount, os.path.join(home, sheet_path()[len('"$HOME"/'):]), repo)
    # 3. a second version: installed beside the first, current switched; again: already installed, only switched
    r = install(b)
    assert r.returncode == 0 and points_to() == b and os.path.isdir(os.path.join(hooks, a)) and not leftovers(), (r.stdout, r.stderr)
    assert install("HEAD").returncode == 0 and points_to() == a and install(b).returncode == 0 and points_to() == b
    # 4. back to the old version by its directory name; a revision is no installed version
    r = install("--switch", a)
    assert r.returncode == 0 and points_to() == a and "→ " + a in r.stdout, (r.stdout, r.stderr)
    assert install("--switch", b).returncode == 0 and points_to() == b
    r = install("--switch", "HEAD")
    assert r.returncode == 1 and "没有已装的版本" in r.stderr and points_to() == b, ("--switch takes only a 40-hex installed directory, no revision (O7 first review M1)", r.stderr)
    assert install("--switch", a).returncode == 0 and points_to() == a
    os.makedirs(os.path.join(hooks, ".tmp.X"))  # a real half-made directory, a real link named like a sha: neither is an installed version
    os.symlink(a, os.path.join(hooks, on_main))  # on_main passes the self-check: only the link test refuses it
    for bad in (("--switch", "nope"), ("--switch", "../x"), ("--switch", ".tmp.X"), ("--switch", "current"), ("--switch", on_main), ("--switch", a.upper()), ("--switch",), ("--switch", a, b), ("not-a-commit",), (a, b)):
        r = install(*bad)
        assert r.returncode == 1 and "current 未改动" in r.stderr and points_to() == a, (bad, r.returncode, r.stderr)
    os.rmdir(os.path.join(hooks, ".tmp.X"))
    os.remove(os.path.join(hooks, on_main))
    r = install("--switch", "nope", HOOKS_INSTALL_DIR=os.path.join(tmp, "never"))
    assert r.returncode == 1 and not os.path.exists(os.path.join(tmp, "never")), "a refused --switch makes no install directory (O7 first review, suggestion 1)"
    for absent in ("nope", fail_open):  # not a revision; a commit that was never installed
        r = install("--switch", absent)
        assert r.returncode == 1 and "没有已装的版本" in r.stderr and points_to() == a, (absent, r.stderr)
    # 4b. two installs of one commit at once both succeed (the loser finds the directory ready) and leave current right (O7 first review, suggestion 2)
    for _ in range(10):  # one round of four lost the race once on main (run 40975901039); eight at once for ten rounds makes the old install.sh fail most runs
        runs = [subprocess.Popen([os.path.join(repo, "scripts/gates/install.sh"), b], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, env=env, cwd=tmp) for _ in range(8)]
        outs = [(p.wait(timeout=120), p.stderr.read()) for p in runs]
        assert all(code == 0 for code, _ in outs) and points_to() == b and not leftovers(), (outs, points_to(), leftovers())
    # 4b'. git archive writes after tar has stopped reading (the zero padding behind the end-of-archive marker; #1183, nightly 41719387871): tar exits at the marker, git
    #      archive is killed by SIGPIPE (141) and pipefail fails the install, whenever tar is quicker than git archive. A git that pads the archive by 1 MB (more than a pipe holds) forces it.
    shim = os.path.join(tmp, "shim")
    os.makedirs(shim)
    put(os.path.join(shim, "git"), '#!/bin/bash\ncase " $* " in *" archive "*) /usr/bin/git "$@" || exit $?; exec head -c 1048576 /dev/zero ;; esac\nexec /usr/bin/git "$@"\n', 0o755)
    r = install(b, PATH=shim + os.pathsep + env["PATH"])
    assert r.returncode == 0 and points_to() == b and not leftovers(), ("the padding after the end of the archive is read, not left to SIGPIPE", r.returncode, r.stderr)
    # 4c. that fallback, deterministically: the target directory is locked (chflags uchg), so neither rename works. A target matching the fresh unpack counts as
    #     ready; one matching only its own manifest (another commit's files) does not, and current stays (O7 fix first review, suggestion 2). chflags is macOS/BSD.
    target = os.path.join(hooks, b)
    for files, code in ((None, 0), (a, 1)):
        if files:
            writable(target)
            shutil.rmtree(target)
            shutil.copytree(os.path.join(hooks, files), target, symlinks=True)
        if not shutil.which("chflags"):
            print("install self-test: 没有 chflags，跳过 4c")
            break
        subprocess.run(["chflags", "uchg", target], check=True)
        try:
            r = install(b)
        finally:
            subprocess.run(["chflags", "nouchg", target], check=True)
        assert r.returncode == code and points_to() == b and not leftovers() and (code == 0 or "放不进" in r.stderr), (files, r.returncode, r.stderr)
    assert install(b).returncode == 0
    # 4d. the race of 4b, deterministically (#879): when install.sh looks, the target is another commit's files (a rival's half-done replacement), locked so the
    #     rename fails; the rival finishes half a second after the installer's copy is read-only. The installer must wait for that, not give up on the first look. chflags is macOS/BSD.
    if shutil.which("chflags"):
        good = os.path.join(tmp, "good-" + b)
        shutil.copytree(target, good, symlinks=True)
        writable(good)  # a read-only directory cannot be renamed into another parent
        writable(target)
        shutil.rmtree(target)
        shutil.copytree(os.path.join(hooks, a), target, symlinks=True)
        subprocess.run(["chflags", "uchg", target], check=True)
        gone = os.path.join(tmp, "gone-" + b)
        rival_errors = []
        def rival():
            try:
                deadline = time.time() + 60
                while time.time() < deadline and not any(n.startswith(".tmp.") and not os.access(os.path.join(hooks, n), os.W_OK) for n in os.listdir(hooks)):
                    time.sleep(0.01)  # the installer has unpacked and made its copy read-only: its next step is the rename
                time.sleep(0.5)
                subprocess.run(["chflags", "nouchg", target], check=True)
                writable(target)
                os.rename(target, gone)  # one rename takes the old files away, no in-place rmtree for the installer to race
                os.rename(good, target)
            except OSError as e:
                # The installer may slide its own read-only copy into the gap between the two renames (the #879 path): then renaming onto it fails with
                # EACCES on macOS (ENOTEMPTY/EEXIST elsewhere) - the PermissionError of #1071. That is fine only when the target is that read-only copy.
                if e.errno not in (errno.EACCES, errno.ENOTEMPTY, errno.EEXIST) or not os.path.isdir(target) or os.access(target, os.W_OK):
                    rival_errors.append(traceback.format_exc())
            except Exception:  # a thread's exception would otherwise vanish and the test pass on a path it never took
                rival_errors.append(traceback.format_exc())
        t = threading.Thread(target=rival)
        t.start()
        try:
            r = install(b)
        finally:
            t.join()
        assert not rival_errors, rival_errors
        assert r.returncode == 0 and points_to() == b and not leftovers(), ("a target that becomes ready within the wait is taken", r.returncode, r.stderr)
    else:
        print("install self-test: 没有 chflags，跳过 4d")
    assert install("--switch", a).returncode == 0 and points_to() == a
    # 5. a failing self-check leaves current and the install directory as they were
    for broken in (fail_open, refuses_all, passes_writes, not_exec):
        r = install(broken)
        assert r.returncode == 1 and "自检不过" in r.stderr and points_to() == a and not os.path.exists(os.path.join(hooks, broken)) and not leftovers(), (broken, r.stderr)
    r = install(first_only)
    assert r.returncode == 1 and "自检不过" in r.stderr and points_to() == a, "every target is probed, not only the first (O7 final, suggestion 2)"
    # 5b. installed versions are read-only: an edit fails
    gate_file = os.path.join(hooks, a, "scripts/gates/prod_write_gate.py")
    assert not os.access(gate_file, os.W_OK) and not os.access(os.path.dirname(gate_file), os.W_OK), "chmod -R a-w after install"
    with contextlib.suppress(PermissionError):
        open(gate_file, "a").close()
        raise AssertionError("an installed file could be opened for writing")
    # 5c. a tampered installed version (the current one, and another one) is warned about at SessionStart, and install.sh <sha> / --switch rebuild it from git (O7 final M1, M2)
    def tamper(version, gate="#!/bin/bash\ncat >/dev/null\nexit 0\n"):
        writable(os.path.join(hooks, version))
        put(os.path.join(hooks, version, "scripts/gates/pre_tool_dispatch.py"), gate)
    def warning():
        r = mount(ceiling_cmd, "{}", CLAUDE_ENV_FILE=os.path.join(tmp, "env"))
        assert r.returncode == 0, r
        return "\n".join(line for line in r.stdout.splitlines() if line.startswith("【钩子告警】"))
    def follow(text, sha):
        """Runs the install.sh command the warning tells one-ops to run: it must repair (O7 fix first review M1: --switch refuses a link or a missing directory)."""
        command = re.search(r"执行 (scripts/gates/install\.sh(?: [^ （]+)*)（", text)
        assert command, text
        r = install(*command.group(1).split()[1:])
        assert r.returncode == 0 and points_to() == sha and os.path.isdir(current) and not os.path.islink(os.path.join(hooks, sha)) and warning() == "" and not leftovers(), (command.group(1), r.stderr)
    fake_write = bash(f"psql -h {FAKE} -c 'delete from t'")
    assert warning() == "" and mount(gate_cmd, fake_write).returncode == 2
    tamper(a)
    assert mount(gate_cmd, fake_write).returncode == 0, "the tampering took effect"
    assert "与安装时的清单不一致" in warning() and f"install.sh {a}（" in warning(), warning()
    follow(warning(), a)
    assert mount(gate_cmd, fake_write).returncode == 2, "install.sh <sha> repairs"
    tamper(b)
    r = install("--switch", b)
    assert r.returncode == 0 and points_to() == b and mount(gate_cmd, fake_write).returncode == 2 and warning() == "", ("--switch repairs", r.stderr)
    assert install("--switch", a).returncode == 0 and points_to() == a
    writable(os.path.join(hooks, a))
    put(os.path.join(hooks, a, "scripts/hooks/extra.sh"), "#!/bin/bash\n")
    assert "不一致" in warning(), "an added file"
    os.remove(os.path.join(hooks, a, "scripts/hooks/extra.sh"))
    os.remove(os.path.join(hooks, a, "scripts/gates/main_checkout_gate.py"))
    assert "不一致" in warning(), "a removed file"
    os.rename(os.path.join(hooks, a, ".manifest"), os.path.join(tmp, "manifest"))
    assert "没有校验和清单" in warning()
    follow(warning(), a)  # the warning's command repairs a missing manifest too (O7 fix first review, suggestion 2)
    outside = os.path.join(tmp, "outside")
    shutil.copytree(os.path.join(repo, "scripts"), os.path.join(outside, "scripts"))
    for target in (outside, "../outside", "not-a-sha"):
        os.remove(current)
        os.symlink(target, current)
        assert "不是指向安装目录下 40 位 sha 目录" in warning(), target
    os.remove(current)
    assert "不是指向" in warning(), "no current at all"
    os.symlink(on_main, current)  # a sha name with nothing behind it (O7 final, suggestion 1); the warning's command repairs it
    assert "current 指向的目录" in warning() and "不存在" in warning(), warning()
    follow(warning(), on_main)
    writable(os.path.join(hooks, on_main))
    shutil.rmtree(os.path.join(hooks, on_main))
    os.symlink(os.path.join(hooks, a), os.path.join(hooks, on_main))  # the version itself a link: warned about, the session goes on (O7 first review M1), the warning's command repairs it
    r = mount(ceiling_cmd, "{}", CLAUDE_ENV_FILE=os.path.join(tmp, "env"))
    assert r.returncode == 0 and "是符号链接" in r.stdout, (r.returncode, r.stdout)
    follow(r.stdout, on_main)
    writable(os.path.join(hooks, on_main))
    shutil.rmtree(os.path.join(hooks, on_main))
    os.symlink(os.path.join(tmp, "nowhere"), os.path.join(hooks, on_main))  # the version a dangling link: `[ -e ]` alone does not see it (O7 refix first review M1)
    assert "是符号链接" in warning(), warning()
    follow(warning(), on_main)
    os.remove(current)
    os.makedirs(os.path.join(current, "scripts"))  # current a real directory, no link: no sha to name, the warning says install.sh without one (O7 refix final M2)
    assert "不是指向" in warning() and "install.sh（" in warning(), warning()
    follow(warning(), a)
    os.remove(current)
    for project in (tmp, ""):
        r = mount(ceiling_cmd, "{}", CLAUDE_PROJECT_DIR=project)
        assert r.returncode == 0 and "核对不了已装钩子" in r.stdout, ("the checker missing from the session's repository", project, r.stdout)
    assert install(a).returncode == 0 and warning() == ""
    # 6. HOOKS_INSTALL_DIR moves the install directory; the policy one stays untouched
    other = os.path.join(tmp, "other")
    r = install(b, HOOKS_INSTALL_DIR=other)
    assert r.returncode == 0 and os.readlink(os.path.join(other, "current")) == b and points_to() == a, (r.stdout, r.stderr)
    # 7. the mount commands as written: fail closed for the gate, pass with a warning for SessionStart
    def version(name, gate, ceiling="#!/bin/bash\nexit 1\n", gate_mode=0o755):
        root = os.path.join(hooks, name)
        if gate is not None:
            put(os.path.join(root, "scripts/gates/pre_tool_dispatch.py"), gate, gate_mode)
        put(os.path.join(root, "scripts/hooks/git_ceiling_env.sh"), ceiling)
        os.remove(current)
        os.symlink(name, current)
    for name, gate, mode, why in (("missing", None, 0o755, "入口缺失"), ("not-exec", "#!/bin/bash\nexit 0\n", 0o644, "入口缺失"), ("crash", "#!/bin/bash\nexit 1\n", 0o755, "异常退出（退出码 1"),
                                  ("no-python", "#!/nonexistent/python3\n", 0o755, "异常退出（退出码 126"), ("killed", "#!/bin/bash\nkill -9 $$\n", 0o755, "异常退出（退出码 137"),
                                  ("syntax", "#!/usr/bin/python3\ndef (:\n", 0o755, "异常退出（退出码 1")):
        version(name, gate, gate_mode=mode)
        closed(why)
    shutil.rmtree(os.path.join(hooks, "syntax"))
    closed("入口缺失")  # a dangling current link
    version("gate-error", "#!/bin/bash\ncat >/dev/null\nexit 3\n")  # the real dispatcher's answer when the gate cannot be imported, crashes or hangs (section 4)
    closed("异常退出（退出码 3", extra=SAMPLES)
    version("answers", "#!/bin/bash\ncat >/dev/null\necho no >&2\nexit 2\n", "#!/bin/bash\necho ok\n")
    for shell in SHELLS:
        r = mount(gate_cmd, bash("ls"), shell)
        assert r.returncode == 2 and r.stderr.strip() == "no", "the gate's own 2 passes through unchanged"
        assert mount(ceiling_cmd, "{}", shell).stdout.splitlines()[-1] == "ok"
    version("passes", "#!/bin/bash\ncat >/dev/null\necho '{\"x\":1}'\nexit 0\n")
    r = mount(gate_cmd, bash("ls"))
    assert r.returncode == 0 and r.stdout.strip() == '{"x":1}' and not r.stderr, "the gate's own 0 and its stdout pass through"
    os.remove(current)
    r = install("--switch", a)
    assert r.returncode == 0 and points_to() == a and mount(gate_cmd, bash("ls")).returncode == 0

def codex_gate_test():
    """#1180: codex runs in one shape, in a session worktree under .claude/worktrees/; everything else is refused."""
    with tempfile.TemporaryDirectory() as tmp:
        tmp = os.path.realpath(tmp)
        main, w1, w2, plain = (os.path.join(tmp, n) for n in ("main", "main/.claude/worktrees/w1", "main/.claude/worktrees/w2", "plain"))
        os.makedirs(main)
        os.makedirs(plain)
        git(main, "init", "-q", "-b", "main")
        git(main, "commit", "-q", "--allow-empty", "-m", "base")
        for name, path in (("w1", w1), ("w2", w2)):
            git(main, "worktree", "add", "-q", path, "-b", name)
        os.makedirs(os.path.join(w1, "sub"))
        S, M, E = "codex exec --sandbox workspace-write", "-m gpt-5.5", "-c model_reasoning_effort=high"

        def ask(command, cwd=w1, project=w1, tool="Bash"):
            with mock.patch.dict(os.environ, {"CLAUDE_PROJECT_DIR": project} if project else {}, clear=False):
                if not project:
                    os.environ.pop("CLAUDE_PROJECT_DIR", None)
                return codex_gate.check(command, cwd)
        allowed = [f"{S} 'fix the bug'", f'{S} "fix the bug"', "codex exec -s workspace-write 'fix the bug'", "codex exec --sandbox=workspace-write 'fix the bug'", f"{S} {M} 'fix the bug'",
                   f"{S} --model=gpt-5.5 'fix the bug'", f"{S} {E} 'fix the bug'", f"{S} --config model_reasoning_effort=xhigh 'fix the bug'", f'{S} -c \'model_reasoning_effort="low"\' \'fix the bug\'',
                   f"{S} {M} {E} 'fix the bug'", f"codex exec {M} {E} -s workspace-write 'fix the bug'", f"{S} -- '--odd prompt'", f"{S} -",
                   f"{S} 'fix the bug' < PROMPT.md > out.txt 2>&1", f"{S} 'fix the bug' >> sub/out.txt", f"{S} 'fix the bug' 2>/dev/null", f"{S} 'fix the bug' &> out.txt", f"{S} \"fix $(date) the bug\"",
                   f"{S} 'fix the bug'\n", "git commit -m 'codex pilot'", "gh issue comment 1 --body 'ask codex'", "ls ~/.codex", "which codex", "echo codex", "printf codex | cat",   # data programs and script files that only carry the word as an argument (lead's list, one per program)
                   "cat docs/codex/notes.md", "cat docs/codex", "sed -n '1,5p' docs/codex.md", "rg codex docs", "rg -n 'codex pilot' scripts", "grep -rn codex docs", "head -5 notes-codex.md", "ls docs/codex", "git log --oneline -- docs/codex",
                   "git add docs/codex.md", "gh issue create --title 'ops(gates): codex pilot' --body-file body.md", "gh pr create --title 'x (#1)' --body 'codex'", "printf '%s' codex > note.txt", "date", "basename /x/codex",
                   "python3 scripts/session/new_intent.py --who 'x' 'the user said use codex for drafting'", "python3 scripts/merge/post_review.py --pr 1 --body-file codex-review.md", "python3 -I scripts/merge/mq.py status codex",
                   "node scripts/tool.js codex", "bash scripts/ci/light.sh codex", "sh ./x.sh --note 'codex'", "./scripts/session/tool.sh codex notes", "/usr/bin/python3 -I scripts/x.py 'codex pilot'", "git commit -m 'codex' && ls", "git commit -m 'codex pilot' && git push", "echo CODEX_HOME", "cat <<'EOF'\ncodex exec x\nEOF", "git log --grep=codex"]
        for command in allowed:
            assert ask(command) is None, (command, ask(command))
        assert ask(f"{S} 'fix the bug'", cwd=os.path.join(w1, "sub")) is None, "a subdirectory of the own worktree"
        refused = [  # (command, text the refusal must hold): every form the card names
            ("codex", "exec"), ("codex exec 'fix the bug'", "workspace-write"), ("codex exec -s read-only 'fix the bug'", "workspace-write"), ("codex exec -s danger-full-access 'fix the bug'", "workspace-write"),
            ("codex exec --sandbox 'fix the bug'", "workspace-write"), (f"{S} --dangerously-bypass-approvals-and-sandbox 'fix the bug'", "开关"), (f"{S} --full-auto 'fix the bug'", "开关"),
            (f"{S} --oss 'fix the bug'", "开关"), (f"{S} -p dev 'fix the bug'", "开关"), (f"{S} --profile dev 'fix the bug'", "开关"), (f"{S} -C / 'fix the bug'", "开关"), (f"{S} --cd / 'fix the bug'", "开关"),
            (f"{S} --add-dir /etc 'fix the bug'", "开关"), (f"{S} --enable x 'fix the bug'", "开关"), (f"{S} -c sandbox_workspace_write.network_access=true 'fix the bug'", "model_reasoning_effort"),
            (f"{S} -c model=gpt 'fix the bug'", "model_reasoning_effort"), (f"{S} -c model_reasoning_effort=extreme 'fix the bug'", "model_reasoning_effort"), (f"{S} {E} {E} 'fix the bug'", "-c 只放一个"),
            (f"{S} --config sandbox_mode=danger-full-access 'fix the bug'", "model_reasoning_effort"), (f"{S} -m '-x' 'fix the bug'", "模型名"), (f"{S} -m 'a b' 'fix the bug'", "模型名"),
            (f"{S} {S[11:]} 'fix the bug'", "沙箱开关重复"), (f"CODEX_HOME=/x {S} 'fix the bug'", "codex 只放一种用法"), (f"FOO=1 {S} 'fix the bug'", "codex 只放一种用法"),
            ("codex exec resume --last", "workspace-write"), (f"{S} resume", "单个词"), (f"{S} review", "单个词"), (f"{S} 'one' 'two'", "恰有一个提示词"), (f"{S}", "恰有一个提示词"),
            ("codex login", "exec"), ("codex mcp list", "exec"), ("codex apply", "exec"), ("codex --version", "exec"), ("codex --sandbox workspace-write exec 'fix the bug'", "exec"),
            (f"timeout 600 {S} 'fix the bug'", "codex 只放一种用法"), (f"time {S} 'fix the bug'", "codex 只放一种用法"), (f"nice -n 5 {S} 'fix the bug'", "codex 只放一种用法"), (f"nohup {S} 'fix the bug'", "codex 只放一种用法"),
            (f"stdbuf -oL {S} 'fix the bug'", "codex 只放一种用法"), (f"command {S} 'fix the bug'", "codex 只放一种用法"), (f"builtin {S} 'fix the bug'", "codex 只放一种用法"), (f"noglob {S} 'fix the bug'", "codex 只放一种用法"),
            (f"env {S} 'fix the bug'", "codex 只放一种用法"), (f"env -i CODEX_HOME=/x {S} 'fix the bug'", "codex 只放一种用法"), (f"sudo {S} 'fix the bug'", "codex 只放一种用法"), (f"sudo -u root {S} 'fix the bug'", "codex 只放一种用法"),
            (f"nohup nice timeout 5 {S} 'fix the bug'", "codex 只放一种用法"), (f"xargs {S}", "codex 只放一种用法"), (f"find . -exec {S} 'a b' \\;", "codex 只放一种用法"), ("watch -n1 codex exec x", "codex 只放一种用法"),
            (f"ls && {S} 'fix the bug'", "codex 只放一种用法"), (f"ls; {S} 'fix the bug'", "codex 只放一种用法"), (f"ls | {S} 'fix the bug'", "codex 只放一种用法"), (f"{S} 'fix the bug' | tee out.txt", "codex 只放一种用法"),
            (f"{S} 'fix the bug' && ls", "codex 只放一种用法"), (f"{S} 'fix the bug' &", "codex 只放一种用法"), (f"{S} 'fix the bug'\nls", "codex 只放一种用法"), (f"cd /tmp && {S} 'fix the bug'", "codex 只放一种用法"),
            (f"( {S} 'fix the bug' )", "codex 只放一种用法"), (f"{{ {S} 'fix the bug'; }}", "codex 只放一种用法"), (f"if true; then {S} 'fix the bug'; fi", "codex 只放一种用法"), (f"! {S} 'fix the bug'", "codex 只放一种用法"),
            (f"echo $({S} 'fix the bug')", "codex 只放一种用法"), (f"echo `{S} 'fix the bug'`", "codex 只放一种用法"), (f'echo "$({S} \'fix the bug\')"', "codex 只放一种用法"), (f"cat <({S} 'a b')", "codex 只放一种用法"),
            (f"X=$({S} 'fix the bug')", "codex 只放一种用法"), (f"{S} \"fix $({S} 'x y') the bug\"", "codex 只放一种用法"),
            (f"bash -c \"{S} 'fix the bug'\"", "codex 只放一种用法"), (f"sh -c '{S} \"fix the bug\"'", "codex 只放一种用法"), (f"zsh -c \"{S} 'fix the bug'\"", "codex 只放一种用法"), (f"bash -lc \"{S} 'fix the bug'\"", "codex 只放一种用法"),
            (f"eval {S} 'fix the bug'", "codex 只放一种用法"), (f"eval \"{S} 'fix the bug'\"", "codex 只放一种用法"), ("bash <<EOF\ncodex exec x\nEOF", "codex 只放一种用法"), (f"python3 -c \"import os; os.system('{S} x')\"", "codex 只放一种用法"),
            (f"/usr/local/bin/codex exec -s workspace-write 'a b'", "codex 只放一种用法"), (f"./codex exec -s workspace-write 'a b'", "codex 只放一种用法"), (f"node_modules/.bin/codex exec -s workspace-write 'a b'", "codex 只放一种用法"),
            ("npx codex exec -s workspace-write 'a b'", "codex 只放一种用法"), ("npx -y @openai/codex exec -s workspace-write 'a b'", "codex 只放一种用法"), ("pnpm dlx @openai/codex exec", "codex 只放一种用法"),
            ("c\\odex exec 'a b'", "exec"), ("co''dex", "exec"), ("'codex'", "exec"), ("$'\\x63odex'", "exec"), ("bash -c 'c\"\"odex exec x'", "codex 只放一种用法"), ("bash -c \"c'o'dex exec it's\"", "codex 只放一种用法"),
            ("echo 'unterminated codex", "解析不了"), ('echo "x\nbash -c $\'\\x63odex exec x\'', "解析不了"), ("$'\\x63odex' exec x" + '"$(echo ' * 600, "解析不了"),
            # named in text, run only at run time: the first review's forms (codex is written out, so this is not the accepted "assembled at run time" gap)
            ("echo 'codex exec --dangerously-bypass-approvals-and-sandbox x' | bash", "codex 只放一种用法"), ("bash <<< 'codex exec x'", "codex 只放一种用法"), ('bash -c "$(echo codex exec x)"', "codex 只放一种用法"),
            ("bash <(echo codex exec x)", "codex 只放一种用法"), ("$(which codex) exec --dangerously-bypass-approvals-and-sandbox x", "codex 只放一种用法"), ("`which codex` exec x", "codex 只放一种用法"),
            ("X=codex; $X exec x", "codex 只放一种用法"), ("for c in codex; do $c exec x; done", "codex 只放一种用法"), ("printf 'codex exec x' > r.sh; sh r.sh", "codex 只放一种用法"),
            (f"{S} \"$(bash -c 'echo hi')\" ".replace('"$(bash', '"fix $(bash'), "codex 只放一种用法"),
            # the adversarial review's forms: program name made by a brace or glob, a here-document delimiter that expands, a text nested too deep
            # the final review's forms: a brace range, shell keywords and zsh precommand words, any program naming codex
            ("co{d..d}ex --full-auto", "codex 只放一种用法"), ("c{o..o}dex --dangerously-bypass-approvals-and-sandbox x", "codex 只放一种用法"), ("function f { codex --full-auto; }; f", "codex 只放一种用法"),
            ('trap "codex --full-auto" EXIT', "codex 只放一种用法"), ("repeat 1 codex --full-auto", "codex 只放一种用法"), ("nocorrect codex --full-auto", "codex 只放一种用法"), ("coproc codex --full-auto", "codex 只放一种用法"),
            ("alias c=codex; c", "codex 只放一种用法"), ("- codex --full-auto", "codex 只放一种用法"), ("python3 -c 'print(1)' codex", "codex 只放一种用法"), ("python3 - codex <<EOF\nx\nEOF", "codex 只放一种用法"), ("bash -lc 'ls' codex", "codex 只放一种用法"), ("python3 -m pytest codex", "codex 只放一种用法"), ("env ./run.sh codex", "codex 只放一种用法"), ("co\\\ndex --full-auto", "exec"),
            ("co{dex,} --dangerously-bypass-approvals-and-sandbox", "codex 只放一种用法"), ("/opt/bin/cod?x --dangerously-bypass-approvals-and-sandbox 'a b'", "codex 只放一种用法"),
            ("c[o]dex exec x", "codex 只放一种用法"), ("cod*x exec x", "codex 只放一种用法"), ("env co{dex,} exec x", "codex 只放一种用法"),
            ("cat <<$X\nx\n$X\nbash -c $'\\x63odex exec --dangerously-bypass-approvals-and-sandbox'", "codex 只放一种用法"),
            (f"{S} 'fix the bug' > /etc/passwd", "不在 worktree"), (f"{S} 'fix the bug' > ../x.txt", "不在 worktree"), (f"{S} 'fix the bug' > {tmp}/x.txt", "不在 worktree"), (f"{S} 'fix the bug' < /etc/passwd", "不在 worktree"),
            (f"{S} 'fix the bug' 2> /tmp/x", "不在 worktree"), (f"{S} 'fix the bug' > $HOME/x", "写死的路径"), (f"{S} 'fix the bug' > *.txt", "写死的路径"), (f"{S} 'fix the bug' >&2", "不放行的重定向"),
            (f"{S} 'fix the bug' <<< 'x'", "不放行的重定向"), (f"{S} - <<EOF\nprompt\nEOF", "here-document"), (f"{S} 'fix the bug' 3> x", "不放行的重定向"),
            (f"{S} $PROMPT", "展开"), (f"{S} {M[:3]} $MODEL 'a b'", "展开"), (f"{S} 'fix the bug' *", "展开"), (f"{S} ~/x 'a b'", "展开"), (f"{S} {{a,b}} 'a b'", "展开"),
            (f"{S} -c model_reasoning_effort=$E 'a b'", "展开")]
        for command, needle in refused:
            reason = ask(command)
            assert reason and "拒绝" in reason and needle in reason, (command, needle, reason)
        # only a session worktree under .claude/worktrees/ (CLAUDE_PROJECT_DIR is the main checkout in app-started worktree sessions, so it is not compared)
        for why, cwd in (("the main checkout", main), ("a directory that is not a repository", plain), ("no cwd", None), ("a relative cwd", "w1"), ("cwd gone", os.path.join(tmp, "gone"))):
            reason = ask(f"{S} 'fix the bug'", cwd=cwd, project=main)
            assert reason and any(k in reason for k in ("当前目录", "说不出", "不是 .claude/worktrees/")), (why, reason)
        for project in (main, w1, plain, None):
            assert ask(f"{S} 'fix the bug'", project=project) is None, ("whatever CLAUDE_PROJECT_DIR says", project)
        assert ask(f"{S} 'fix the bug'", cwd=w2, project=main) is None, "another session worktree of the repository"
        # git's top level redirected by core.worktree (adversarial round): a directory whose .git file points at a linked worktree configured onto a session worktree is not inside it
        w3, fake = os.path.join(tmp, "w3"), os.path.join(main, "backend")
        git(main, "worktree", "add", "-q", w3, "-b", "w3")
        git(main, "config", "extensions.worktreeConfig", "true")
        git(w3, "config", "--worktree", "core.worktree", w1)
        os.makedirs(fake)
        put(os.path.join(fake, ".git"), f"gitdir: {os.path.join(main, '.git', 'worktrees', 'w3')}\n", 0o644)
        for cwd in (w3, fake):
            reason = ask(f"{S} 'fix the bug'", cwd=cwd, project=main)
            assert reason and "不在 git 报的顶层" in reason, (cwd, reason)
        # a standalone clone placed under .claude/worktrees/ is a main checkout of its own, not a session worktree (first round)
        clone = os.path.join(tmp, "clonehome/.claude/worktrees/c1")
        os.makedirs(os.path.dirname(clone))
        git(tmp, "clone", "-q", main, clone)
        reason = ask(f"{S} 'fix the bug'", cwd=clone, project=main)
        assert reason and "不是 .claude/worktrees/" in reason, reason
        os.symlink(w2, os.path.join(tmp, "link"))
        assert ask(f"{S} 'fix the bug'", cwd=os.path.join(tmp, "link")) is None and "不在 worktree" in ask(f"{S} 'fix the bug' > ../w1/x.txt", cwd=os.path.join(tmp, "link")), "a symlink resolves to the worktree it points at"
        os.symlink(tmp, os.path.join(w1, "escape"))
        assert "不在 worktree" in ask(f"{S} 'fix the bug' > escape/x.txt"), "a symlink out of the worktree does not make its target inside"
        # a repository whose worktree is not under .claude/worktrees/ is not an own worktree
        other = os.path.join(tmp, "elsewhere")
        git(main, "worktree", "add", "-q", other, "-b", "elsewhere")
        assert "worktrees/" in ask(f"{S} 'fix the bug'", cwd=other, project=other)
        # through the dispatcher: all hook-running tools, the production gate still answers, the launch tool is judged as the command it starts
        with mock.patch.dict(os.environ, {"CLAUDE_PROJECT_DIR": w1}):
            for tool in COMMAND_TOOLS:
                assert d.dispatch(payload(f"{S} 'fix the bug'", cwd=w1, tool_name=tool)) == (0, "", ""), tool
                code, out, err = d.dispatch(payload(f"timeout 5 {S} 'fix the bug'", cwd=w1, tool_name=tool))
                assert code == 2 and "codex 只放" in err, (tool, err)
            assert d.dispatch(payload(f"psql -h {PROD_P} -c x", cwd=w1))[0] == 2 and d.dispatch(payload(f"{S} 'fix the bug' > /etc/x", cwd=w1))[0] == 2
            put(os.path.join(w1, ".claude", "launch.json"), json.dumps({"configurations": [{"name": "c", "runtimeExecutable": "codex", "runtimeArgs": ["exec"]}, {"name": "ok", "runtimeExecutable": "npm", "runtimeArgs": ["run", "dev"]}]}), 0o644)
            assert d.dispatch(json.dumps({"tool_name": LAUNCH_TOOL, "tool_input": {"name": "c"}, "cwd": w1}))[0] == 2
            assert d.dispatch(json.dumps({"tool_name": LAUNCH_TOOL, "tool_input": {"name": "ok"}, "cwd": w1})) == (0, "", "")
        # as the hook process, with its stderr
        r = subprocess.run([PYTHON, "-I", DISPATCH], input=payload(f"env {S} 'a b'", cwd=w1), capture_output=True, text=True, env={"LC_ALL": "C", "PATH": os.environ.get("PATH", ""), "CLAUDE_PROJECT_DIR": w1})
        assert r.returncode == 2 and "codex 只放" in r.stderr, (r.returncode, r.stderr)
    print("codex_gate self-test: ok")

SECTIONS = {"prod_write_gate": prod_write_gate_test, "codex_gate": codex_gate_test, "pre_tool_dispatch": pre_tool_dispatch_test, "main_checkout_gate": main_checkout_gate_test, "pre_commit": pre_commit_test, "install": install_test}

if __name__ == "__main__":
    unknown = [name for name in sys.argv[1:] if name not in SECTIONS]
    if unknown:
        sys.exit(f"用法：gates_selftest.py [{' | '.join(SECTIONS)} …]；不认识：{unknown}")
    for name in sys.argv[1:] or SECTIONS:
        SECTIONS[name]()
