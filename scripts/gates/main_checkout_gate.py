#!/usr/bin/python3
"""主检出提交保护（git pre-commit / pre-merge-commit 层，D25）：在主检出上提交就拒绝（不论哪个分支），在 linked worktree 里放行。

起因：one-platform-file 2026-10-21 18:35 在自己的工作区根执行 `cd ..` 落到 .claude/worktrees/（那里不是仓库，git 向上找到主检出），
随后 `git reset --soft … && git commit` 改了主检出的 main，钩子因此跑旧版本近 3 小时（one-coordinator 2026-10-21）。
判定：本次提交所用仓库的 git-dir 等于 common-dir（即主检出，不是 linked worktree）就拒绝（退出码 1），不论 HEAD 在 main、其他分支还是分离 HEAD
（用户 2026-10-23 定，intent 225957：没有正当流程在主检出提交；分离 HEAD 提交再 update-ref 两步就能改掉 main）。
放行：linked worktree（含签出了 main 的 worktree）。GIT_DIR / GIT_COMMON_DIR / GIT_WORK_TREE 照 git 给的用：从工作区 `GIT_DIR=<主检出>/.git`
或 `--git-dir=<主检出>/.git --work-tree=…` 提交时，git 交给钩子的就是主检出的 git-dir，照样拒（O4 复审必改 1）。
出错一律拒绝：git 跑不起来、返回非零、输出读不懂，都按拒绝处理（退出码 1，pre-commit 视为失败）。
用法：main_checkout_gate.py --pre-commit  |  main_checkout_gate.py --self-test（自测在 main_checkout_gate_selftest.py）
"""
import os
import subprocess
import sys

MESSAGE = ("拒绝：这是在主检出（{top}，HEAD {head}）上提交。主检出只由 one-merge 经队列快进（merge --ff-only origin/main），"
           "不在其中提交。到自己的工作区（.claude/worktrees/ 下的某个目录，不是 .claude/worktrees/ 本身）里做；"
           "若是 `cd ..` 落到了 .claude/worktrees/，git 会向上找到主检出，先 cd 回自己的工作区。")
FAILED = "主检出提交保护出错（{why}），无法确认这里不是主检出的 main，按拒绝处理；请把报错转 one-ops。"


KEPT = ("GIT_DIR", "GIT_COMMON_DIR", "GIT_WORK_TREE")


def clean_env():
    """Drop the GIT_* variables git sets for hooks (GIT_INDEX_FILE, GIT_PREFIX, ...) but keep the ones naming the repository this commit writes to."""
    return {k: v for k, v in os.environ.items() if not k.startswith("GIT_") or k in KEPT}


def where(directory):
    """(top level, is the main checkout, HEAD ref) for the repository at `directory`; raises RuntimeError when git cannot say."""
    try:
        r = subprocess.run(["git", "-C", directory, "rev-parse", "--path-format=absolute", "--show-toplevel", "--git-dir", "--git-common-dir", "--symbolic-full-name", "HEAD"],
                           capture_output=True, text=True, timeout=5, env=clean_env())
    except (OSError, subprocess.SubprocessError) as error:
        raise RuntimeError(f"git 跑不起来：{error.__class__.__name__}: {error}") from error
    lines = r.stdout.splitlines()
    if r.returncode or len(lines) != 4:
        raise RuntimeError(f"git rev-parse 退出码 {r.returncode}：{r.stderr.strip()[:200] or r.stdout.strip()[:200]}")
    top, git_dir, common_dir, head = lines
    return os.path.realpath(top), os.path.realpath(git_dir) == os.path.realpath(common_dir), head


def pre_commit(directory):
    """0 = commit may go ahead; 1 = refused (with the reason on stderr)."""
    try:
        top, is_main_checkout, head = where(directory)
    except Exception as error:   # fail closed: an unanswered question is a refusal
        print(FAILED.format(why=error), file=sys.stderr)
        return 1
    if is_main_checkout:
        print(MESSAGE.format(top=top, head=head), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    if sys.argv[1:] == ["--pre-commit"]:
        sys.exit(pre_commit(os.getcwd()))
    if sys.argv[1:] == ["--self-test"]:
        sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
        from main_checkout_gate_selftest import self_test
        sys.exit(self_test())
    print("用法：main_checkout_gate.py --pre-commit | --self-test", file=sys.stderr)
    sys.exit(1)
