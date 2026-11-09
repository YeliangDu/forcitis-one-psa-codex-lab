#!/usr/bin/env python3
"""按卡模板（.github/ISSUE_TEMPLATE/card.md）开一张卡（Issue）：标题与各小节按模板生成，形状和 post_review 的卡检查（scripts/merge/issue_gate.py）一致（#1163）。

用法：python3 scripts/session/new_card.py --type=<类型> --scope=<范围> --summary=<中文摘要> --session=<会话名> [--file=<含小节标题的 md>] [--<小节名>=<内容> ...] [--dry-run]
小节表与类型表现读卡模板，不另存一份：除「类型」外每个小节对应一个同名选项（如 --目标 --完成标准），「类型」由 --type 填。
--file 指向一个 markdown 文件，里面用「## <小节名>」分节；同一小节命令行选项与文件都给了，以命令行为准。
拒绝：缺小节或小节为空（HTML 注释不算内容）、--type 不在模板的类型表或没有 docs/rules/playbooks/<类型>.md、--session 不是 policy.json sessions.config 的键、
--scope 不合标题形状、--summary 不含中文或超过 30 字。标题为「前缀(范围): 摘要」，前缀按 CS-7.8 由类型映射（impl→feat、bugfix→fix、docs/design→docs、ops→ci、acceptance→test、investigation→spike）。
--dry-run 只打印标题、空行、正文，不建卡；否则 gh issue create 带标签「会话:<会话名>」，并把 gh 的输出（卡的网址）打印出来。
"""
import argparse, json, os, re, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "..")
sys.path.insert(0, os.path.join(ROOT, "scripts", "merge"))
import issue_gate  # noqa: E402  卡模板的读法与卡标题、正文的检查都用它的，不另写一份

PREFIX = dict(pair.split(">") for pair in "impl>feat bugfix>fix ops>ci docs>docs acceptance>test design>docs investigation>spike".split())   # CS-7.8
SCOPE = re.compile(r"[\w./-]+")
MAX_SUMMARY = 30


def read_template():
    try:
        template = open(issue_gate.TEMPLATE, encoding="utf-8").read()
    except OSError as e:
        sys.exit(f"读不到卡模板 {issue_gate.TEMPLATE}：{e}")
    names, kinds = list(issue_gate.sections(template)), issue_gate.card_kinds(template)
    if not names or not kinds or "类型" not in names:
        sys.exit("卡模板里没有小节、「类型」小节或读不出类型表")
    return names, kinds


def sessions():
    policy = json.load(open(os.path.join(ROOT, "policy.json"), encoding="utf-8"))
    return policy.get("sessions", {}).get("config", {})


def problems(opts, names, kinds, given):
    """逐项查，返回全部问题（一次列全）。"""
    found = []
    kind = opts.type
    if kind not in kinds:
        found.append(f"--type 须为模板的类型之一（{' / '.join(kinds)}）：{kind!r}")
    elif kind not in PREFIX:
        found.append(f"--type {kind} 没有对应的标题前缀（CS-7.8 映射缺项）")
    elif not os.path.isfile(os.path.join(ROOT, f"docs/rules/playbooks/{kind}.md")):
        found.append(f"--type {kind} 没有剧本 docs/rules/playbooks/{kind}.md")
    if not opts.scope or not SCOPE.fullmatch(opts.scope):
        found.append(f"--scope 须为子域或模块的 slug（字母数字 . / _ -）：{opts.scope!r}")
    summary = opts.summary or ""
    if not issue_gate.CJK.search(summary) or len(summary) > MAX_SUMMARY or "\n" in summary:
        found.append(f"--summary 须含中文、一行、不超过 {MAX_SUMMARY} 字（现 {len(summary)} 字）：{summary!r}")
    if not opts.session or opts.session not in sessions():
        found.append(f"--session 须是 policy.json sessions.config 的键：{opts.session!r}")
    empty = [n for n in names if n != "类型" and not given.get(n)]
    if empty:
        found.append(f"缺小节或小节为空：{'、'.join(empty)}（每节用 --<小节名> 或 --file 给内容）")
    return found


def build(opts, names, given):
    title = f"{PREFIX.get(opts.type, opts.type)}({opts.scope}): {opts.summary}"
    parts = [f"## {n}\n\n{opts.type if n == '类型' else given[n].strip()}\n" for n in names]
    return title, "\n".join(parts)


def main(argv):
    names, kinds = read_template()
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter, add_help=True)
    for flag in ("type", "scope", "summary", "session", "file"):
        ap.add_argument(f"--{flag}")
    ap.add_argument("--dry-run", action="store_true")
    for n in names:
        if n != "类型":
            ap.add_argument(f"--{n}", dest=f"sec_{n}")
    opts = ap.parse_args(argv)
    given = {}
    if opts.file:
        try:
            given = issue_gate.sections(open(opts.file, encoding="utf-8").read())
        except OSError as e:
            sys.exit(f"读不到 --file：{e}")
    given.update({n: v for n in names if (v := getattr(opts, f"sec_{n}", None)) is not None})
    found = problems(opts, names, kinds, given)
    title, body = build(opts, names, given) if not found else (None, None)
    if not found:   # 生成物自查：与 post_review 的卡检查同口径，不一致就是本脚本的错，不建卡
        found += [f"生成的卡{w}" for w in (issue_gate.issue_title_problem(title), issue_gate.issue_body_problem(body)) if w]
    if found:
        sys.exit("拒绝开卡：\n- " + "\n- ".join(found))
    if opts.dry_run:
        print(f"{title}\n\n{body}", end="")
        return
    r = subprocess.run(["gh", "issue", "create", "--title", title, "--body-file", "-", "--label", f"会话:{opts.session}"], input=body, text=True, capture_output=True)
    sys.stdout.write(r.stdout)
    if r.returncode:
        sys.exit(f"gh issue create 失败（{r.returncode}）：{r.stderr.strip()}")


if __name__ == "__main__":
    main(sys.argv[1:])
