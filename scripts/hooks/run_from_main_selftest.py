#!/usr/bin/env python3
"""run_from_main.sh 自测（GOV-1023-QUA-06）：落后的工作区里钩子取主检出版本，工作目录与标准输入原样保留，主检出缺钩子时退回并告警，两处都没有时失败。"""
import os, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
LAUNCHER = os.path.join(HERE, "run_from_main.sh")


def put(path, text, mode=0o755):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)
    os.chmod(path, mode)


def run(cwd, *args, stdin="", env=None):
    return subprocess.run([os.path.join(cwd_hooks[cwd], "run_from_main.sh"), *args], cwd=cwd, input=stdin, capture_output=True, text=True, env={"PATH": os.environ["PATH"], "LC_ALL": "C", **(env or {})})


cwd_hooks = {}

def main():
    with tempfile.TemporaryDirectory() as tmp:
        tmp = os.path.realpath(tmp)
        main_repo, stale = os.path.join(tmp, "main"), os.path.join(tmp, "wt")
        git = lambda *a: subprocess.run(["git", "-C", main_repo, "-c", "user.name=t", "-c", "user.email=t@t", *a], check=True, capture_output=True)
        os.makedirs(main_repo)
        git("init", "-q", "-b", "main")
        probe = '#!/bin/bash\necho "$1 {tag} cwd=$PWD in=$(cat)"\n'
        put(os.path.join(main_repo, "scripts/hooks/run_from_main.sh"), open(LAUNCHER, encoding="utf-8").read())
        put(os.path.join(main_repo, "scripts/hooks/probe.sh"), probe.replace("{tag}", "MAIN"))
        git("add", "-A"); git("commit", "-q", "-m", "one")
        git("worktree", "add", "-q", "-b", "wt", stale)
        # the stale workspace holds an old probe and a probe the main checkout does not have
        put(os.path.join(stale, "scripts/hooks/probe.sh"), probe.replace("{tag}", "STALE"))
        put(os.path.join(stale, "scripts/hooks/only_here.sh"), probe.replace("{tag}", "ONLYHERE"))
        cwd_hooks[stale] = os.path.join(stale, "scripts/hooks")
        sub = os.path.join(stale, "docs")
        os.makedirs(sub)
        cwd_hooks[sub] = cwd_hooks[stale]
        r = run(stale, "probe.sh", "arg", stdin="payload")
        assert r.returncode == 0 and r.stdout.strip() == f"arg MAIN cwd={stale} in=payload", (r.stdout, r.stderr)
        r = run(sub, "probe.sh", "a", stdin="p2")
        assert f"MAIN cwd={sub} in=p2" in r.stdout, "the session's working directory is kept, also from a sub directory"
        r = run(stale, "only_here.sh", "a")
        assert "ONLYHERE" in r.stdout and "退回本工作区副本" in r.stderr, "missing in the main checkout: fall back and warn"
        r = run(stale, "nowhere.sh")
        assert r.returncode == 2 and "不存在或不可执行" in r.stderr, "missing everywhere: fail, never pass silently"
        for bad in ("", "../probe.sh", "a/b", ".hidden"):
            assert run(stale, bad).returncode == 2, f"bad hook name {bad!r}"
        # outside any git repository: the copy next to the launcher runs, with a warning
        outside = os.path.join(tmp, "outside")
        os.makedirs(outside)
        cwd_hooks[outside] = cwd_hooks[stale]
        r = run(outside, "probe.sh", "a")
        assert "STALE" in r.stdout and "找不到主检出" in r.stderr, (r.stdout, r.stderr)
        # CLAUDE_PROJECT_DIR is the fallback when the working directory is no repository
        r = run(outside, "probe.sh", "a", env={"CLAUDE_PROJECT_DIR": stale})
        assert "MAIN" in r.stdout, (r.stdout, r.stderr)
        # every registered hook command goes through the launcher and names an existing hook
        import json
        settings = json.load(open(os.path.join(HERE, "../../.claude/settings.json"), encoding="utf-8"))
        for event, groups in settings["hooks"].items():
            for group in groups:
                for hook in group["hooks"]:
                    parts = hook["command"].split()
                    assert parts[0].endswith("scripts/hooks/run_from_main.sh\"") or parts[0].endswith("run_from_main.sh"), (event, hook["command"])
                    assert os.path.exists(os.path.join(HERE, parts[1])), (event, parts[1])
    print("run_from_main self-test: ok")


if __name__ == "__main__":
    sys.exit(main() or 0)
