#!/usr/bin/python3
"""PR-opening gate (PreToolUse, hung on the dispatcher; #1561): `gh pr create` in a session worktree is refused when the branch changes backend runtime code (`backend/src/main/**`) and the body the command gives has no valid `Codex:` line.
The rules are draft_rules.py (shared with the merge queue's enqueue check, scripts/merge/draft_check.py, which is the check that cannot be skipped: it reads the pull request itself).
This gate is the early answer, so it judges only what the command text shows: the body from `--body <text>`, `--body "$(cat <<'EOF' ... EOF)"`, `--body-file <file>` or `--body-file -` with a here-document. A body it cannot read from the text (a variable, another program's output, no body flag) is not judged here, and the enqueue check still holds.
`Codex: skip <reason>` lines that need a lead comment pass here (no pull request, no comment yet) and are judged at enqueue.
Fails open (ops playbook): anything this gate cannot read (git, ~/.codex, a parse) lets the command through with a warning in additionalContext; only a missing or untrue `Codex:` line refuses.
Usage: pr_open_gate.py --self-test (cases in draft_selftest.py)."""
import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import codex_gate as shell   # the tokenizer and the session-worktree test of the codex gate: one copy
import draft_rules

GIT_WAIT = 20


def read_flag(args, i, long_names, short):
    """(Word or None, next index) when args[i] is the flag (`--body x`, `--body=x`, `-b x`, `-bx` is not read); else (None, i)."""
    a = args[i].text
    for name in long_names:
        if a == name:
            return (args[i + 1], i + 2) if i + 1 < len(args) else (None, i + 1)
        if a.startswith(name + "="):
            w = shell.Word()
            w.text, w.expands = a[len(name) + 1:], args[i].expands
            return w, i + 1
    if a == short:
        return (args[i + 1], i + 2) if i + 1 < len(args) else (None, i + 1)
    return None, i


def lone_cat_body(script):
    """The text of a here-document that a substitution `$(cat <<'EOF' ... EOF)` prints, or None for any other script."""
    try:
        toks, nested = shell.tokenize(script)
        cmds, _ = shell.commands(toks)
    except (shell.ParseError, RecursionError):
        return None
    if nested or len(cmds) != 1 or len(cmds[0].bodies) != 1 or [w.text for w in cmds[0].words] != ["cat"]:
        return None
    return cmds[0].bodies[0]


def create_body(command, cwd):
    """(True, body text or None) for a command that runs `gh pr create` (body None: not readable from the text), (False, None) when it does not."""
    try:
        toks, nested = shell.tokenize(command)
        cmds, _ = shell.commands(toks)
    except (shell.ParseError, RecursionError):
        return False, None
    for cmd in cmds:
        prog, nxt = shell.program(cmd)
        if prog is None or os.path.basename(prog.text) != "gh" or prog.expands:
            continue
        args = cmd.words[nxt:]
        if [w.text for w in args[:2]] != ["pr", "create"]:
            continue
        i, body, file_ = 2, None, None
        while i < len(args):
            w, j = read_flag(args, i, ("--body",), "-b")
            if j != i:
                body = (w.text if not w.expands else (lone_cat_body(nested[0]) if w is not None and w.text == "\0" and len(nested) == 1 else None)) if w is not None else None
                i = j
                continue
            w, j = read_flag(args, i, ("--body-file",), "-F")
            if j != i:
                file_ = w
                i = j
                continue
            i += 1
        if body is None and file_ is not None and not file_.expands:
            if file_.text == "-":
                body = cmd.bodies[0] if len(cmd.bodies) == 1 else None
            else:
                try:
                    body = open(os.path.join(cwd, file_.text), encoding="utf-8").read()
                except OSError:
                    body = None
        return True, body
    return False, None


def git(cwd, *args):
    return subprocess.run(["git", "-C", cwd, *args], capture_output=True, text=True, timeout=GIT_WAIT, stdin=subprocess.DEVNULL)


def local_facts(top):
    """(stats, branch cut time) of the branch checked out in the worktree `top`; raises ToolError when git cannot say."""
    try:
        git(top, "fetch", "-q", "origin", "main")   # a stale origin/main would list main's own commits as this branch's; a failed fetch is no reason to stop
        branch = git(top, "symbolic-ref", "--short", "HEAD")
        numstat = git(top, "diff", "--numstat", "--no-renames", "origin/main...HEAD")
        if branch.returncode or numstat.returncode:
            raise draft_rules.ToolError("git cannot say the branch or its diff against origin/main")
        stats = []
        for line in numstat.stdout.splitlines():
            added, deleted, path = line.split("\t", 2)
            stats.append((path, None, int(added) if added.isdigit() else 0, int(deleted) if deleted.isdigit() else 0))
        log = git(top, "reflog", "show", "--format=%cI", f"refs/heads/{branch.stdout.strip()}")
        dates = [x for x in log.stdout.split() if x] if log.returncode == 0 else []
        if dates:
            return stats, draft_rules.parse_time(dates[-1])
        base = git(top, "merge-base", "origin/main", "HEAD")
        when = git(top, "show", "-s", "--format=%cI", base.stdout.strip()) if base.returncode == 0 and base.stdout.strip() else None
        if when is None or when.returncode:
            raise draft_rules.ToolError("git cannot find the merge base with origin/main")
        return stats, draft_rules.parse_time(when.stdout.strip())
    except (OSError, subprocess.SubprocessError, ValueError) as error:
        raise draft_rules.ToolError(f"{error.__class__.__name__}: {error}")


def warning(why):
    text = f"PR 开启前的 Codex 初稿核验没有跑（{why}）；放行。入队时 mq.py enqueue 会再核验。"
    return 0, json.dumps({"hookSpecificOutput": {"hookEventName": "PreToolUse", "additionalContext": text}}, ensure_ascii=False), ""


def decide(payload, command):
    """(exit code, stdout, stderr) for a shell command the production gate has already read out of the hook input."""
    if "pr" not in command or "create" not in command:
        return 0, "", ""
    try:
        is_create, body = create_body(command, payload.get("cwd") or "")
        if not is_create or body is None:
            return 0, "", ""
        top, why = shell.own_worktree(payload.get("cwd"))
        if top is None:
            return 0, "", ""   # the main checkout and other places are the main-checkout gate's business, not this one's
        stats, cut = local_facts(top)
        problem = draft_rules.judge(stats, body, None, None, top, cut, draft_rules.sessions_root())
    except draft_rules.ToolError as error:
        return warning(str(error))
    except Exception as error:   # a gate that cannot read something does not stop the work
        return warning(f"{error.__class__.__name__}: {error}")
    if problem:
        return 2, "", "拒绝：开 PR 前核验后端初稿：" + problem + "\n"
    return 0, "", ""


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        from draft_selftest import pr_open_gate_test as self_test
        sys.exit(self_test())
    print("用法：pr_open_gate.py --self-test", file=sys.stderr)
    sys.exit(1)
