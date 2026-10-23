#!/usr/bin/env python3
"""install.sh 与 .claude/settings.json 挂载的自测（GOV-1023-FV2 O7，D26；接替 run_from_main_selftest 对 settings.json 的断言）。全部在临时目录里做，HOME 指向临时目录，不碰真实安装目录。
- 结构：四个挂载（SessionStart 两个、PreToolUse 只挂 Bash、Stop 不动），前三个直接调用 <policy gates.install_dir>/<install_current_link>/ 下的文件，目标在索引里是 100755；三个插件关闭。
- install.sh：首次安装、装第二个版本并切换、切回旧版本；自检不过（分发器失败开放、全拒、放行生产写、只拦第一个 target、入口不可执行）时 current 不变且不留半成品；
  装好的目录只读；已装目录被改坏后再跑 install.sh <sha> 或 --switch 都从 git 重新解包修好（O7 终审 M1）；HOOKS_INSTALL_DIR 改目录。
- SessionStart 核对（hooks_manifest.sh --check，O7 终审 M2）：current 指到安装目录外或非 40 位 sha 目录、文件被改、多了或少了文件、没有清单、核对脚本缺失，都输出【钩子告警】并退出 0。
- 挂载命令按 settings.json 原样用 /bin/sh 与 /bin/bash 执行：生产写门在 current 缺失、链接悬空、入口缺失、解释器缺失、崩溃、被杀时退出 2（门自己的 0 与 2 照传）；SessionStart 两个缺失或出错时退出 0 并输出一行告警。
- 装好的门向 CLAUDE_PROJECT_DIR 的仓库问 origin/main 的特征表；没有 CLAUDE_PROJECT_DIR 时不认工作目录里伪造的仓库（O7 终审 M3）。
生产样本只用虚构值；真实表只经 install.sh 自检与「只拦第一个 target」的探针按位置取用，本文件不读不写其内容。用法：install_selftest.py"""
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
    link = '"$HOME"/' + gates["install_dir"][2:] + "/" + gates["install_current_link"]
    prefix = "H=" + link + "/"
    verify = ('V="$CLAUDE_PROJECT_DIR"/scripts/gates/hooks_manifest.sh; if [ -x "$V" ]; then "$V" --check ' + link + "; "
              'else echo "【钩子告警】核对不了已装钩子（$V 缺失或不可执行）"; fi; ')  # runs from the session's repository, not from current (O7 final M2)
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
            command = hook["command"][len(verify):] if group is hooks["SessionStart"][0] else hook["command"]
            assert (group is hooks["SessionStart"][0]) == hook["command"].startswith(verify), (event, hook["command"])
            head = command.split(";")[0]
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
    for name in [*commands, "scripts/gates/hooks_manifest.sh"]:
        mode = git(ROOT, "ls-files", "-s", "--", name).split()[:1]
        assert mode == ["100755"], (name, mode)
    for plugin in ("kotlin-lsp", "claude-code-setup", "code-modernization"):
        assert settings["enabledPlugins"][f"{plugin}@claude-plugins-official"] is False, plugin
    with open(os.path.join(HERE, "install.sh"), encoding="utf-8") as f:
        assert sum(1 for _ in f) <= gates["max_lines"]["install"], "install.sh 超过 policy gates.max_lines.install"
    return {name: cmd for name, (_, cmd, _) in commands.items()}


def make_repo(repo):
    """A repository holding this working tree's scripts/gates, scripts/hooks and policy.json; returns (A, B, fail_open, refuses_all, passes_writes, first_only, not_executable, main) commits."""
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
    put(dispatcher, FIRST_ONLY)
    commit("first target only")
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
    with tempfile.TemporaryDirectory() as top:
        try:
            body(gate_cmd, ceiling_cmd, sheet_cmd, os.path.realpath(os.path.join(top, "dir with space")))
        finally:
            writable(top)
    print("install self-test: ok")
    return 0


def body(gate_cmd, ceiling_cmd, sheet_cmd, tmp):
    home, repo = os.path.join(tmp, "home"), os.path.join(tmp, "repo")
    os.makedirs(home)
    a, b, fail_open, refuses_all, passes_writes, first_only, not_exec, _ = make_repo(repo)
    hooks = os.path.join(home, policy()["install_dir"][2:])
    current = os.path.join(hooks, policy()["install_current_link"])
    env = {"PATH": os.environ["PATH"], "HOME": home, "LC_ALL": "C"}

    def install(*args, **extra):
        return subprocess.run([os.path.join(repo, "scripts/gates/install.sh"), *args], capture_output=True, text=True, env={**env, **extra}, cwd=tmp)

    def mount(cmd, stdin, shell="/bin/sh", **extra):
        cwd = extra.pop("cwd", repo)
        run_env = {k: v for k, v in {**env, "CLAUDE_PROJECT_DIR": repo, **extra}.items() if v is not None}
        return subprocess.run([shell, "-c", cmd], input=stdin, capture_output=True, text=True, env=run_env, cwd=cwd, timeout=60)

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
        r = mount(gate_cmd, bash(f"psql -h {FAKE} -c 'delete from t'"), shell)  # the installed gate asks CLAUDE_PROJECT_DIR's repository for origin/main's table
        assert r.returncode == 2 and r.stderr, ("a pattern only on origin/main is refused by the installed gate", shell, r.returncode, r.stderr)
        r = mount(gate_cmd, "not json", shell)
        assert r.returncode == 2 and "不是 JSON" in r.stderr and "异常退出" not in r.stderr, "the gate's own refusal passes through"
        r = mount(ceiling_cmd, "{}", shell, CLAUDE_ENV_FILE=os.path.join(tmp, "env"))
        assert r.returncode == 0 and "【钩子告警】" not in r.stdout, (r.stdout, r.stderr)
    r = mount(sheet_cmd, json.dumps({"cwd": ROOT}))
    assert r.returncode == 0 and r.stdout.startswith("【开工单】") and "【钩子告警】" not in r.stdout, r.stdout[:300]
    # 2b. no CLAUDE_PROJECT_DIR: the working directory is no repository root for the installed gate, so a forged repository whose origin/main holds a
    #     script cannot make it trusted (O7 final M3); the forged script names a fictitious host that is on a fictitious local table
    forged, table = os.path.join(tmp, "forged"), os.path.join(tmp, "fake_targets.json")
    doc_script = "docs" + "/" + "modernization/new_intent.py"  # one of the gate's DOC_SCRIPTS; built, not spelled (relevant_change.py scans sources for literal docs paths)
    put(table, json.dumps({"targets": [{"id": "fake", "patterns": [FAKE]}]}), 0o644)
    put(os.path.join(forged, doc_script), f"import os\nos.system(\"psql -h {FAKE} -c 'delete from t'\")\n", 0o644)
    git(forged, "init", "-q", "-b", "main")
    git(forged, "add", "-A")
    git(forged, "commit", "-q", "-m", "forged")
    git(forged, "update-ref", "refs/remotes/origin/main", "HEAD")
    for shell in SHELLS:
        for project in (None, ""):
            r = mount(gate_cmd, json.dumps({"tool_name": "Bash", "tool_input": {"command": "python3 " + doc_script}, "cwd": forged}), shell,
                      CLAUDE_PROJECT_DIR=project, PROD_TARGETS_FILE=table, cwd=forged)
            assert r.returncode == 2, ("a forged repository in the working directory makes no script trusted", shell, project, r.returncode, r.stderr)
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
    r = install(first_only)
    assert r.returncode == 1 and "自检不过" in r.stderr and points_to() == a, "every target is probed, not only the first (O7 final, suggestion 2)"
    # 5b. installed versions are read-only: an edit fails
    gate_file = os.path.join(hooks, a, "scripts/gates/prod_write_gate.py")
    assert not os.access(gate_file, os.W_OK) and not os.access(os.path.dirname(gate_file), os.W_OK), "chmod -R a-w after install"
    try:
        open(gate_file, "a").close()
        raise AssertionError("an installed file could be opened for writing")
    except PermissionError:
        pass
    # 5c. a tampered installed version (the current one, and another one) is warned about at SessionStart, and install.sh <sha> / --switch rebuild it from git (O7 final M1, M2)
    def tamper(version, gate="#!/bin/bash\ncat >/dev/null\nexit 0\n"):
        writable(os.path.join(hooks, version))
        put(os.path.join(hooks, version, "scripts/gates/pre_tool_dispatch.py"), gate)

    def warning():
        r = mount(ceiling_cmd, "{}", CLAUDE_ENV_FILE=os.path.join(tmp, "env"))
        assert r.returncode == 0, r
        return "\n".join(line for line in r.stdout.splitlines() if line.startswith("【钩子告警】"))

    fake_write = bash(f"psql -h {FAKE} -c 'delete from t'")
    assert warning() == "" and mount(gate_cmd, fake_write).returncode == 2
    tamper(a)
    assert mount(gate_cmd, fake_write).returncode == 0, "the tampering took effect"
    assert "与安装时的清单不一致" in warning() and f"install.sh --switch {a}" in warning(), warning()
    r = install(a)
    assert r.returncode == 0 and points_to() == a and mount(gate_cmd, fake_write).returncode == 2 and warning() == "" and not leftovers(), ("install.sh <sha> repairs", r.stderr)
    tamper(b)
    r = install("--switch", b)
    assert r.returncode == 0 and points_to() == b and mount(gate_cmd, fake_write).returncode == 2 and warning() == "", ("--switch repairs", r.stderr)
    assert install("--switch", a).returncode == 0 and points_to() == a
    writable(os.path.join(hooks, a))
    put(os.path.join(hooks, a, "scripts/hooks/extra.sh"), "#!/bin/bash\n")
    assert "不一致" in warning(), "an added file"
    os.remove(os.path.join(hooks, a, "scripts/hooks/extra.sh"))
    os.remove(os.path.join(hooks, a, "scripts/hooks/command_words.py"))
    assert "不一致" in warning(), "a removed file"
    os.rename(os.path.join(hooks, a, ".manifest"), os.path.join(tmp, "manifest"))
    assert "没有校验和清单" in warning()
    outside = os.path.join(tmp, "outside")
    shutil.copytree(os.path.join(repo, "scripts"), os.path.join(outside, "scripts"))
    for target in (outside, "../outside", "not-a-sha"):
        os.remove(current)
        os.symlink(target, current)
        assert "不是指向安装目录下 40 位 sha 目录" in warning(), target
    os.remove(current)
    assert "不是指向" in warning(), "no current at all"
    r = mount(ceiling_cmd, "{}", CLAUDE_PROJECT_DIR=tmp)
    assert r.returncode == 0 and "核对不了已装钩子" in r.stdout, "the checker missing from the session's repository"
    assert install(a).returncode == 0 and warning() == ""
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
        assert mount(ceiling_cmd, "{}", shell).stdout.splitlines()[-1] == "ok" and mount(sheet_cmd, "{}", shell).stdout.strip() == "【开工单】x"
    version("passes", "#!/bin/bash\ncat >/dev/null\necho '{\"x\":1}'\nexit 0\n")
    r = mount(gate_cmd, bash("ls"))
    assert r.returncode == 0 and r.stdout.strip() == '{"x":1}' and not r.stderr, "the gate's own 0 and its stdout pass through"
    os.remove(current)
    r = install("--switch", a)
    assert r.returncode == 0 and points_to() == a and mount(gate_cmd, bash("ls")).returncode == 0


if __name__ == "__main__":
    sys.exit(self_test())
