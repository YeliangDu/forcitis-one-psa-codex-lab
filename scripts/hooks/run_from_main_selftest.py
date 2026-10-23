#!/usr/bin/env python3
"""run_from_main.sh 自测（GOV-1023-QUA-06）：落后的工作区里钩子取主检出的版本；定位不看工作目录；主检出不在 main 或 scripts/hooks 有改动时退回并告警；
钩子崩溃按失败处理；告警进日志和（SessionStart）标准输出；settings.json 里每个钩子都经启动器，PreToolUse 只挂 Bash 的 gates/pre_tool_dispatch.py 且带 --strict，三个无关插件关闭；gates/<名字> 取 scripts/gates。"""
import json, os, shlex, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
LAUNCHER = os.path.join(HERE, "run_from_main.sh")
PROBE = '#!/bin/bash\necho "$1 {tag} cwd=$PWD in=$(cat)"\n'


def put(path, text, mode=0o755):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)
    os.chmod(path, mode)


def git(repo, *args):
    return subprocess.run(["git", "-C", repo, "-c", "user.name=t", "-c", "user.email=t@t", *args], check=True, capture_output=True, text=True).stdout


def launch(workspace, *args, cwd=None, project=None, stdin="", flags=()):
    """Runs the launcher copy that lives in `workspace`, the way settings.json does ("$CLAUDE_PROJECT_DIR"/scripts/hooks/run_from_main.sh …)."""
    env = {"PATH": os.environ["PATH"], "LC_ALL": "C"}
    if project is not None:
        env["CLAUDE_PROJECT_DIR"] = project
    return subprocess.run([os.path.join(workspace, "scripts/hooks/run_from_main.sh"), *flags, *args], cwd=cwd or workspace, input=stdin, capture_output=True, text=True, env=env)


def main():
    with tempfile.TemporaryDirectory() as tmp:
        tmp = os.path.realpath(os.path.join(tmp, "dir with space"))
        main_repo, stale, foreign = (os.path.join(tmp, n) for n in ("main", "wt", "foreign"))
        os.makedirs(main_repo)
        git(main_repo, "init", "-q", "-b", "main")
        launcher_text = open(LAUNCHER, encoding="utf-8").read()
        hooks = os.path.join(main_repo, "scripts/hooks")
        put(os.path.join(hooks, "run_from_main.sh"), launcher_text)
        put(os.path.join(hooks, "probe.sh"), PROBE.replace("{tag}", "MAIN"))
        put(os.path.join(hooks, "crash.sh"), "#!/bin/bash\nexit 1\n")
        put(os.path.join(hooks, "block.sh"), "#!/bin/bash\necho no >&2\nexit 2\n")
        put(os.path.join(hooks, "syntax.py"), "#!/usr/bin/env python3\ndef (:\n")
        gates = os.path.join(main_repo, "scripts/gates")  # GOV-1023-FV2 O3: `gates/<name>` runs scripts/gates/<name>
        put(os.path.join(gates, "gprobe.sh"), PROBE.replace("{tag}", "GMAIN"))
        put(os.path.join(gates, "pre_tool_dispatch.py"), "#!/bin/bash\necho DISPATCH-GATES\n")
        git(main_repo, "add", "-A")
        git(main_repo, "commit", "-q", "-m", "one")
        git(main_repo, "worktree", "add", "-q", "-b", "wt", stale)
        # the stale workspace: an old probe, a probe main does not have, an old launcher copy-equal
        put(os.path.join(stale, "scripts/hooks/probe.sh"), PROBE.replace("{tag}", "STALE"))
        put(os.path.join(stale, "scripts/hooks/only_here.sh"), PROBE.replace("{tag}", "ONLYHERE"))
        put(os.path.join(stale, "scripts/gates/gprobe.sh"), PROBE.replace("{tag}", "GSTALE"))
        # a foreign repository with a hook of the same name
        os.makedirs(foreign)
        git(foreign, "init", "-q", "-b", "main")
        put(os.path.join(foreign, "scripts/hooks/probe.sh"), PROBE.replace("{tag}", "FOREIGN"))
        sub = os.path.join(stale, "docs")
        os.makedirs(sub)
        log = os.path.join(git(stale, "rev-parse", "--absolute-git-dir").strip(), "hooks-fallback.log")

        def reset_log():
            if os.path.exists(log):
                os.remove(log)

        # 1. the main checkout's version, working directory and standard input kept
        r = launch(stale, "probe.sh", "arg", project=stale, stdin="payload")
        assert r.returncode == 0 and r.stdout.strip() == f"arg MAIN cwd={stale} in=payload" and not r.stderr, (r.stdout, r.stderr)
        assert f"MAIN cwd={sub}" in launch(stale, "probe.sh", "a", cwd=sub, project=stale).stdout
        assert "MAIN" in launch(stale, "probe.sh", "a").stdout, "no CLAUDE_PROJECT_DIR: located by the launcher's own place"
        assert not os.path.exists(log), "no fallback, no log"
        # 1b. gates/<name>: scripts/gates of the main checkout; the old dispatcher name is an alias of gates/pre_tool_dispatch.py (settings.json loaded before the move)
        r = launch(stale, "gates/gprobe.sh", "g", project=stale, stdin="p")
        assert r.returncode == 0 and r.stdout.strip() == f"g GMAIN cwd={stale} in=p" and not r.stderr, (r.stdout, r.stderr)
        for name in ("pre_tool_dispatch.py", "gates/pre_tool_dispatch.py"):
            r = launch(stale, name, project=stale, flags=("--strict",))
            assert r.returncode == 0 and r.stdout.strip() == "DISPATCH-GATES" and not r.stderr, (name, r.stdout, r.stderr)
        put(os.path.join(gates, "gprobe.sh"), PROBE.replace("{tag}", "GDIRTY"))
        r = launch(stale, "gates/gprobe.sh", "g", project=stale)
        assert "GSTALE" in r.stdout and "GDIRTY" not in r.stdout and "未提交或未跟踪" in r.stderr, "uncommitted edits in main's scripts/gates are not run"
        git(main_repo, "checkout", "--", "scripts/gates/gprobe.sh")
        reset_log()
        # 2. the working directory takes no part: a session that cd'd into another repository does not run that repository's hook (opus round 1 item 1)
        r = launch(stale, "probe.sh", "a", cwd=foreign, project=stale)
        assert "MAIN" in r.stdout and "FOREIGN" not in r.stdout, r.stdout
        r = launch(stale, "probe.sh", "a", cwd=foreign, project=foreign)
        assert "STALE" in r.stdout and "FOREIGN" not in r.stdout and "不是启动器所在的仓库" in r.stderr, "CLAUDE_PROJECT_DIR in another repository: the launcher's own copy, with a warning"
        reset_log()
        # 3. main checkout not on main, or scripts/hooks changed: back to this workspace, with a warning in stderr, the log, and stdout when asked
        git(main_repo, "checkout", "-q", "-b", "other")
        r = launch(stale, "probe.sh", "a", project=stale, flags=("--stdout-warn",))
        assert "STALE" in r.stdout and "【钩子告警】probe.sh" in r.stdout and "不在 main 分支" in r.stdout and "不在 main 分支" in r.stderr, (r.stdout, r.stderr)
        assert "不在 main 分支" in open(log, encoding="utf-8").read(), "the log line is what the start sheet shows"
        assert "【钩子告警】" not in launch(stale, "probe.sh", "a", project=stale).stdout, "without --stdout-warn the warning stays out of stdout"
        git(main_repo, "checkout", "-q", "main")
        put(os.path.join(hooks, "probe.sh"), PROBE.replace("{tag}", "DIRTY"))
        r = launch(stale, "probe.sh", "a", project=stale)
        assert "STALE" in r.stdout and "DIRTY" not in r.stdout and "未提交或未跟踪" in r.stderr, "uncommitted edits in main's hooks are not run"
        git(main_repo, "checkout", "--", "scripts/hooks/probe.sh")
        put(os.path.join(hooks, "untracked.sh"), "#!/bin/bash\n")
        assert "未提交或未跟踪" in launch(stale, "probe.sh", "a", project=stale).stderr, "an untracked file in scripts/hooks counts"
        os.remove(os.path.join(hooks, "untracked.sh"))
        assert "MAIN" in launch(stale, "probe.sh", "a", project=stale).stdout and not launch(stale, "probe.sh", "a", project=stale).stderr, "clean again: main's version"
        # the blind spots of `git status`: an ignored file and an assume-unchanged edit in scripts/hooks
        put(os.path.join(hooks, "ignored.sh"), "#!/bin/bash\n")
        put(os.path.join(main_repo, ".gitignore"), "ignored.sh\n")
        git(main_repo, "add", ".gitignore"); git(main_repo, "commit", "-q", "-m", "ignore")
        # an ignored file is no change (Finder drops .DS_Store into the directory again and again; counting it shut every session's Bash): main's copy runs, exit 0, no warning
        put(os.path.join(hooks, ".DS_Store"), "x", 0o644)
        for ignored in ("ignored.sh", ".DS_Store"):
            r = launch(stale, "probe.sh", "a", project=stale, flags=("--strict",))
            assert r.returncode == 0 and "MAIN" in r.stdout and not r.stderr, (ignored, r.returncode, r.stdout, r.stderr)
            r = launch(main_repo, "probe.sh", "a", cwd=foreign, project=main_repo, flags=("--strict",))
            assert r.returncode == 0 and "MAIN" in r.stdout and not r.stderr, "launcher in main, no workspace copy needed"
            os.remove(os.path.join(hooks, ignored))
            if ignored == "ignored.sh":
                put(os.path.join(hooks, ".DS_Store"), "x", 0o644)
        assert not os.path.exists(os.path.join(hooks, "ignored.sh")) and not os.path.exists(os.path.join(hooks, ".DS_Store"))
        git(main_repo, "update-index", "--assume-unchanged", "scripts/hooks/probe.sh")
        put(os.path.join(hooks, "probe.sh"), PROBE.replace("{tag}", "HIDDEN"))
        r = launch(stale, "probe.sh", "a", project=stale)
        assert "HIDDEN" not in r.stdout and "STALE" in r.stdout, "an assume-unchanged edit is not run"
        git(main_repo, "update-index", "--no-assume-unchanged", "scripts/hooks/probe.sh")
        git(main_repo, "checkout", "--", "scripts/hooks/probe.sh")
        # 3b. the desktop app points CLAUDE_PROJECT_DIR at the main checkout, so the launcher that runs IS main's copy: when main is unsafe it must not fall back to its own dirty files
        put(os.path.join(hooks, "probe.sh"), PROBE.replace("{tag}", "DIRTY"))
        r = launch(main_repo, "probe.sh", "a", cwd=stale, project=main_repo)
        assert "STALE" in r.stdout and "DIRTY" not in r.stdout and "会话自己的工作区" in r.stderr, "falls back to the session's worktree, not to main's dirty copy"
        # no workspace copy to fall back to (another repository's working directory is no fallback): main's copy runs with a warning, never exit 2 (that shut every session's Bash at once)
        for flags in (("--strict",), ()):
            r = launch(main_repo, "probe.sh", "a", cwd=foreign, project=main_repo, flags=flags)
            assert r.returncode == 0 and "DIRTY" in r.stdout and "FOREIGN" not in r.stdout and "仍运行主检出的副本" in r.stderr, (flags, r.returncode, r.stdout, r.stderr)
        git(main_repo, "checkout", "--", "scripts/hooks/probe.sh")
        git(main_repo, "checkout", "-q", "-b", "other2")
        r = launch(main_repo, "probe.sh", "a", cwd=stale, project=main_repo)
        assert "STALE" in r.stdout and "不在 main 分支" in r.stderr and "HEAD" in r.stderr
        git(main_repo, "checkout", "-q", "main")
        assert "MAIN" in launch(main_repo, "probe.sh", "a", cwd=stale, project=main_repo).stdout
        # 4. missing in main: the workspace copy; missing everywhere: failure
        r = launch(stale, "only_here.sh", "a", project=stale)
        assert "ONLYHERE" in r.stdout and "没有可执行的 scripts/hooks/only_here.sh" in r.stderr
        for flags, code in ((("--strict",), 2), ((), 1)):
            r = launch(stale, "nowhere.sh", project=stale, flags=flags)
            assert r.returncode == code and "不存在或不可执行" in r.stderr, (flags, r.returncode)
        for bad in ("", "../probe.sh", "a/b", ".hidden", "gates/", "gates/../probe.sh", "gates/a/b", "gates/.x", "hooks/probe.sh"):
            assert launch(stale, bad, project=stale).returncode == 1 and launch(stale, bad, project=stale, flags=("--strict",)).returncode == 2, f"bad hook name {bad!r}"
        # 5. a crashing hook is a failure, not a pass: exit 1 means "do not block" to Claude Code (opus round 1 item 2); 0 and 2 are the hook's own answers
        reset_log()
        for hook in ("crash.sh", "syntax.py"):
            strict = launch(stale, hook, project=stale, flags=("--strict",))
            assert strict.returncode == 2 and "异常退出" in strict.stderr, (hook, strict.returncode, strict.stderr)
            soft = launch(stale, hook, project=stale)
            assert soft.returncode == 1 and "异常退出" in soft.stderr, (hook, soft.returncode)
        assert "crash.sh 异常退出" in open(log, encoding="utf-8").read()
        r = launch(stale, "block.sh", project=stale, flags=("--strict",))
        assert r.returncode == 2 and r.stderr.strip() == "no", "the hook's own refusal passes through unchanged"
        assert launch(stale, "probe.sh", "a", project=stale, flags=("--strict",)).returncode == 0
        # 6. settings.json (GOV-1023-FV2 O6): four hooks; all but Stop go through the launcher, the dispatcher is strict, SessionStart hooks warn on stdout,
        #    Stop runs the tool library's stop_check and always exits 0 (a missing file must not block every stop), three plugins are off
        settings = json.load(open(os.path.join(HERE, "../../.claude/settings.json"), encoding="utf-8"))
        seen = {}
        stop = [h["command"] for g in settings["hooks"].pop("Stop") for h in g["hooks"]]
        assert len(stop) == 1 and "/.modernize/psa/tools/one-session/stop_check/stop_check.py" in stop[0] and stop[0].rstrip().endswith("; exit 0"), stop
        assert sorted(settings["hooks"]) == ["PreToolUse", "SessionStart"], sorted(settings["hooks"])
        for event, groups in settings["hooks"].items():
            for group in groups:
                for hook in group["hooks"]:
                    words = shlex.split(hook["command"])
                    assert words[0].endswith("/scripts/hooks/run_from_main.sh"), (event, hook["command"])
                    name = next(w for w in words[1:] if not w.startswith("--"))
                    assert os.path.exists(os.path.join(HERE, "..", name) if name.startswith("gates/") else os.path.join(HERE, name)), (event, name)
                    seen.setdefault(name, set()).add((event, tuple(w for w in words[1:] if w.startswith("--"))))
        assert all("--strict" in flags for _, flags in seen["gates/pre_tool_dispatch.py"]) and all("--strict" not in flags for n, v in seen.items() if n != "gates/pre_tool_dispatch.py" for _, flags in v)
        assert [g["matcher"] for g in settings["hooks"]["PreToolUse"]] == ["Bash"], "PreToolUse hangs only the production-write gate, on Bash"
        assert all("--stdout-warn" in flags for n in ("git_ceiling_env.sh", "start_sheet.py") for _, flags in seen[n]), "SessionStart output reaches the model"
        assert sorted(seen) == ["gates/pre_tool_dispatch.py", "git_ceiling_env.sh", "start_sheet.py"], sorted(seen)
        for plugin in ("kotlin-lsp", "claude-code-setup", "code-modernization"):
            assert settings["enabledPlugins"][f"{plugin}@claude-plugins-official"] is False, plugin
    print("run_from_main self-test: ok")


if __name__ == "__main__":
    sys.exit(main() or 0)
