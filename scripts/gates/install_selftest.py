#!/usr/bin/env python3
"""install.sh 与 .claude/settings.json 挂载的自测（GOV-1023-FV2 O7，D26；接替 run_from_main_selftest 对 settings.json 的断言）。全部在临时目录里做，HOME 指向临时目录，不碰真实安装目录。
- 结构：四个挂载（SessionStart 两个、PreToolUse 只挂 Bash、Stop 不动），前三个直接调用 <policy gates.install_dir>/<install_current_link>/ 下的文件，目标在索引里是 100755；三个插件关闭。
- install.sh：首次安装、装第二个版本并切换、切回旧版本、自检不过（分发器失败开放、入口不可执行、已装版本被改坏）时 current 不变且不留半成品；HOOKS_INSTALL_DIR 改目录。
- 挂载命令按 settings.json 原样用 /bin/sh 与 /bin/bash 执行：生产写门在 current 缺失、链接悬空、入口缺失、解释器缺失、崩溃、被杀时退出 2（门自己的 0 与 2 照传）；SessionStart 两个缺失或出错时退出 0 并输出一行告警。
- 装好的门向会话仓库问 origin/main 的特征表（prod_write_gate 的 REPO_ROOT 退到 CLAUDE_PROJECT_DIR 或工作目录）。
生产样本只用虚构值；真实表只经 install.sh 自检按位置取第一项，本文件不读不写其内容。用法：install_selftest.py"""
import json
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "../.."))
FAKE = "fake-prod-db.example.invalid"  # fictitious production host, put on the temporary repository's origin/main table only
ENTRIES = {"PreToolUse": ["scripts/gates/pre_tool_dispatch.py"], "SessionStart": ["scripts/hooks/git_ceiling_env.sh", "scripts/hooks/start_sheet.py"]}
SHELLS = ("/bin/sh", "/bin/bash")


def git(repo, *args):
    return subprocess.run(["git", "-C", repo, "-c", "user.name=t", "-c", "user.email=t@t", "-c", "core.hooksPath=/dev/null", *args], check=True, capture_output=True, text=True).stdout.strip()


def put(path, text, mode=0o755):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)
    os.chmod(path, mode)


def policy():
    with open(os.path.join(ROOT, "policy.json"), encoding="utf-8") as f:
        return json.load(f)["gates"]


def settings_self_test():
    with open(os.path.join(ROOT, ".claude/settings.json"), encoding="utf-8") as f:
        settings = json.load(f)
    gates = policy()
    assert gates["install_dir"].startswith("~/"), gates["install_dir"]
    prefix = 'H="$HOME"/' + gates["install_dir"][2:] + "/" + gates["install_current_link"] + "/"
    hooks = settings["hooks"]
    stop = [h["command"] for g in hooks.pop("Stop") for h in g["hooks"]]
    assert len(stop) == 1 and "/.modernize/psa/tools/one-session/stop_check/stop_check.py" in stop[0] and stop[0].rstrip().endswith("; exit 0"), stop
    assert sorted(hooks) == ["PreToolUse", "SessionStart"], sorted(hooks)
    assert [g["matcher"] for g in hooks["PreToolUse"]] == ["Bash"], "PreToolUse hangs only the production-write gate, on Bash"
    assert [g["matcher"] for g in hooks["SessionStart"]] == ["startup|clear|compact|resume", "startup|clear|compact"]
    commands = {}
    for event, groups in hooks.items():
        assert all(len(g["hooks"]) == 1 for g in groups), event
        found = []
        for group in groups:
            hook = group["hooks"][0]
            head = hook["command"].split(";")[0]
            assert hook["type"] == "command" and head.startswith(prefix), (event, hook["command"])
            found.append(head[len(prefix):])
            commands[found[-1]] = (event, hook["command"], hook["timeout"])
        assert found == ENTRIES[event], (event, found)
    gate = commands["scripts/gates/pre_tool_dispatch.py"]
    assert gate[2] == 120 and "exit 2; }" in gate[1] and gate[1].endswith("exit 2") and "exit 0" not in gate[1], "the gate mount fails closed and never exits 0 on its own"
    for name in ("scripts/hooks/git_ceiling_env.sh", "scripts/hooks/start_sheet.py"):
        assert commands[name][1].endswith("; exit 0") and "【钩子告警】" in commands[name][1] and "exit 2" not in commands[name][1], name
    assert commands["scripts/hooks/git_ceiling_env.sh"][2] == 10 and commands["scripts/hooks/start_sheet.py"][2] == 15
    # a target without its exec bit never runs once installed (O6 review item 1: start_sheet.py had none)
    for name in commands:
        mode = git(ROOT, "ls-files", "-s", "--", name).split()[:1]
        assert mode == ["100755"], (name, mode)
    for plugin in ("kotlin-lsp", "claude-code-setup", "code-modernization"):
        assert settings["enabledPlugins"][f"{plugin}@claude-plugins-official"] is False, plugin
    with open(os.path.join(HERE, "install.sh"), encoding="utf-8") as f:
        assert sum(1 for _ in f) <= gates["max_lines"]["install"], "install.sh 超过 policy gates.max_lines.install"
    return {name: cmd for name, (_, cmd, _) in commands.items()}


def make_repo(repo):
    """A repository holding this working tree's scripts/gates, scripts/hooks and policy.json; returns (A, B, fail_open, refuses_all, passes_writes, not_executable, main) commits."""
    names = subprocess.run(["git", "-C", ROOT, "ls-files", "--cached", "--others", "--exclude-standard", "--", "scripts/gates", "scripts/hooks", "policy.json"],
                           check=True, capture_output=True, text=True).stdout.split()
    os.makedirs(repo)
    git(repo, "init", "-q", "-b", "main")
    for name in names:
        if os.path.isfile(os.path.join(ROOT, name)):
            os.makedirs(os.path.dirname(os.path.join(repo, name)) or repo, exist_ok=True)
            shutil.copy2(os.path.join(ROOT, name), os.path.join(repo, name))
    commits = []

    def commit(message):
        git(repo, "add", "-A")
        git(repo, "commit", "-q", "-m", message)
        commits.append(git(repo, "rev-parse", "HEAD"))

    commit("A")
    with open(os.path.join(repo, "scripts/hooks/git_ceiling_env.sh"), "a", encoding="utf-8") as f:
        f.write("# B\n")
    commit("B")
    dispatcher = os.path.join(repo, "scripts/gates/pre_tool_dispatch.py")
    original = open(dispatcher, encoding="utf-8").read()
    put(dispatcher, "#!/bin/bash\ncat >/dev/null\nexit 0\n")  # a gate that lets everything through
    commit("fail open")
    put(dispatcher, "#!/bin/bash\ncat >/dev/null\nexit 2\n")  # a gate that refuses everything: every session's Bash would stop
    commit("refuses all")
    put(dispatcher, "#!/bin/bash\nread -r line\ncase \"$line\" in \"{\"*) exit 0 ;; *) exit 2 ;; esac\n")  # refuses unreadable input, lets production writes through
    commit("passes writes")
    put(dispatcher, original, 0o644)
    commit("not executable")
    git(repo, "checkout", "-q", commits[1])
    table = os.path.join(repo, "scripts/gates/prod_targets.json")
    data = json.load(open(table, encoding="utf-8"))
    data["targets"].append({"id": "fake-only-on-main", "patterns": [FAKE]})
    put(table, json.dumps(data), 0o644)
    commit("origin/main table")
    git(repo, "update-ref", "refs/remotes/origin/main", commits[-1])
    git(repo, "checkout", "-q", commits[0])
    return commits


def self_test():
    commands = settings_self_test()
    gate_cmd, ceiling_cmd, sheet_cmd = commands["scripts/gates/pre_tool_dispatch.py"], commands["scripts/hooks/git_ceiling_env.sh"], commands["scripts/hooks/start_sheet.py"]
    with tempfile.TemporaryDirectory() as tmp:
        tmp = os.path.realpath(os.path.join(tmp, "dir with space"))
        home, repo = os.path.join(tmp, "home"), os.path.join(tmp, "repo")
        os.makedirs(home)
        a, b, fail_open, refuses_all, passes_writes, not_exec, _ = make_repo(repo)
        hooks = os.path.join(home, policy()["install_dir"][2:])
        current = os.path.join(hooks, policy()["install_current_link"])
        env = {"PATH": os.environ["PATH"], "HOME": home, "LC_ALL": "C"}

        def install(*args, **extra):
            return subprocess.run([os.path.join(repo, "scripts/gates/install.sh"), *args], capture_output=True, text=True, env={**env, **extra}, cwd=tmp)

        def mount(cmd, stdin, shell="/bin/sh", **extra):
            return subprocess.run([shell, "-c", cmd], input=stdin, capture_output=True, text=True, env={**env, "CLAUDE_PROJECT_DIR": repo, **extra}, cwd=repo, timeout=60)

        def bash(command):
            return json.dumps({"tool_name": "Bash", "tool_input": {"command": command}, "cwd": repo})

        def points_to():
            return os.readlink(current) if os.path.islink(current) else None

        def leftovers():
            return [n for n in os.listdir(hooks) if n.startswith(".")] if os.path.isdir(hooks) else []

        def closed(why, shells=SHELLS):
            for shell in shells:
                r = mount(gate_cmd, bash("ls"), shell)
                assert r.returncode == 2 and why in r.stderr, (shell, why, r.returncode, r.stdout, r.stderr)
                for cmd in (ceiling_cmd, sheet_cmd):
                    r = mount(cmd, json.dumps({"cwd": repo}), shell)
                    assert r.returncode == 0 and r.stdout.splitlines()[-1].startswith("【钩子告警】"), (shell, why, r.returncode, r.stdout, r.stderr)

        # 1. nothing installed: the production-write gate refuses, SessionStart warns and passes
        closed("入口缺失")
        # 2. first install: <install_dir>/<full sha>/, current a relative link to it, nothing half-made left
        r = install()
        assert r.returncode == 0 and points_to() == a and os.path.isdir(os.path.join(hooks, a)) and not leftovers(), (r.stdout, r.stderr, points_to())
        for shell in SHELLS:
            r = mount(gate_cmd, bash("ls -la"), shell)
            assert r.returncode == 0 and not r.stderr, (shell, r.stdout, r.stderr)
            for extra in ({}, {"CLAUDE_PROJECT_DIR": ""}):  # the installed gate asks the session's repository (CLAUDE_PROJECT_DIR, else the working directory) for origin/main's table
                r = mount(gate_cmd, bash(f"psql -h {FAKE} -c 'delete from t'"), shell, **extra)
                assert r.returncode == 2 and r.stderr, ("a pattern only on origin/main is refused by the installed gate", shell, extra, r.returncode, r.stderr)
            r = mount(gate_cmd, "not json", shell)
            assert r.returncode == 2 and "不是 JSON" in r.stderr and "异常退出" not in r.stderr, "the gate's own refusal passes through"
            r = mount(ceiling_cmd, "{}", shell, CLAUDE_ENV_FILE=os.path.join(tmp, "env"))
            assert r.returncode == 0 and "【钩子告警】" not in r.stdout, (r.stdout, r.stderr)
        r = mount(sheet_cmd, json.dumps({"cwd": ROOT}))
        assert r.returncode == 0 and r.stdout.startswith("【开工单】") and "【钩子告警】" not in r.stdout, r.stdout[:300]
        # 3. a second version: installed beside the first, current switched; again: already installed, only switched
        r = install(b)
        assert r.returncode == 0 and points_to() == b and os.path.isdir(os.path.join(hooks, a)) and not leftovers(), (r.stdout, r.stderr)
        assert install("HEAD").returncode == 0 and points_to() == a and install(b).returncode == 0 and points_to() == b
        # 4. back to the old version, by directory name and by revision
        r = install("--switch", a)
        assert r.returncode == 0 and points_to() == a and "→ " + a in r.stdout, (r.stdout, r.stderr)
        assert install("--switch", b).returncode == 0 and points_to() == b
        assert install("--switch", "HEAD").returncode == 0 and points_to() == a, "a revision is resolved by git"
        for bad in (("--switch", "nope"), ("--switch", "../x"), ("--switch", ".tmp"), ("--switch",), ("--switch", a, b), ("not-a-commit",), (a, b)):
            r = install(*bad)
            assert r.returncode == 1 and "current 未改动" in r.stderr and points_to() == a, (bad, r.returncode, r.stderr)
        for absent in ("nope", fail_open):  # not a revision; a commit that was never installed
            r = install("--switch", absent)
            assert r.returncode == 1 and "没有已装的版本" in r.stderr and points_to() == a, (absent, r.stderr)
        # 5. a failing self-check leaves current and the install directory as they were
        for broken in (fail_open, refuses_all, passes_writes, not_exec):
            r = install(broken)
            assert r.returncode == 1 and "自检不过" in r.stderr and points_to() == a and not os.path.exists(os.path.join(hooks, broken)) and not leftovers(), (broken, r.stderr)
        put(os.path.join(hooks, b, "scripts/gates/pre_tool_dispatch.py"), "#!/bin/bash\nexit 1\n")  # an installed version edited afterwards
        r = install("--switch", b)
        assert r.returncode == 1 and "自检不过" in r.stderr and points_to() == a, r.stderr
        # 6. HOOKS_INSTALL_DIR moves the install directory; the policy one stays untouched
        other = os.path.join(tmp, "other")
        r = install(b, HOOKS_INSTALL_DIR=other)
        assert r.returncode == 0 and os.readlink(os.path.join(other, "current")) == b and points_to() == a, (r.stdout, r.stderr)
        # 7. the mount commands as written: fail closed for the gate, pass with a warning for SessionStart
        def version(name, gate, ceiling="#!/bin/bash\nexit 1\n", sheet="#!/bin/bash\nexit 1\n", gate_mode=0o755):
            root = os.path.join(hooks, name)
            if gate is not None:
                put(os.path.join(root, "scripts/gates/pre_tool_dispatch.py"), gate, gate_mode)
            put(os.path.join(root, "scripts/hooks/git_ceiling_env.sh"), ceiling)
            put(os.path.join(root, "scripts/hooks/start_sheet.py"), sheet)
            os.remove(current)
            os.symlink(name, current)

        version("missing", None)
        closed("入口缺失")
        version("not-exec", "#!/bin/bash\nexit 0\n", gate_mode=0o644)
        closed("入口缺失")
        version("crash", "#!/bin/bash\nexit 1\n")
        closed("异常退出（退出码 1")
        version("no-python", "#!/nonexistent/python3\n")
        closed("异常退出（退出码 126")
        version("killed", "#!/bin/bash\nkill -9 $$\n")
        closed("异常退出（退出码 137")
        version("syntax", "#!/usr/bin/python3\ndef (:\n")
        closed("异常退出（退出码 1")
        shutil.rmtree(os.path.join(hooks, "syntax"))
        closed("入口缺失")  # a dangling current link
        version("answers", "#!/bin/bash\ncat >/dev/null\necho no >&2\nexit 2\n", "#!/bin/bash\necho ok\n", "#!/bin/bash\necho 【开工单】x\n")
        for shell in SHELLS:
            r = mount(gate_cmd, bash("ls"), shell)
            assert r.returncode == 2 and r.stderr.strip() == "no", "the gate's own 2 passes through unchanged"
            assert mount(ceiling_cmd, "{}", shell).stdout.strip() == "ok" and mount(sheet_cmd, "{}", shell).stdout.strip() == "【开工单】x"
        version("passes", "#!/bin/bash\ncat >/dev/null\necho '{\"x\":1}'\nexit 0\n")
        r = mount(gate_cmd, bash("ls"))
        assert r.returncode == 0 and r.stdout.strip() == '{"x":1}' and not r.stderr, "the gate's own 0 and its stdout pass through"
        os.remove(current)
        r = install("--switch", a)
        assert r.returncode == 0 and points_to() == a and mount(gate_cmd, bash("ls")).returncode == 0
    print("install self-test: ok")
    return 0


if __name__ == "__main__":
    sys.exit(self_test())
