#!/usr/bin/env python3
"""占号并生成新格式 ADR 空壳 adr/src/ADR-NNNN.md（README「ADR 生命周期与变更规则」第 8 节）。

用法：python3 tools/docs/new_adr.py --session=<起草会话> [--tier=正式|轻量] [--date=YYYY-MM-DD] [--no-fetch] [--no-pr-check] "<标题>" "<背景：两到五句，含触发出处>"
号 = max(origin/main 的 adr/src 最大号, 本工作区 adr/src 最大号, 在途 PR 新增的 ADR 最大号) + 1：已撤回、已拒绝的 ADR 文件保留在 src，号不会复用；
空壳单独开 PR，合入 main 即占号。取 main 与在途 PR 要联网（git fetch、gh pr list）；取不到时失败，确认不会撞号后可加 --no-fetch / --no-pr-check。
空壳状态「提议」，类别、BC、阶段写「待定」。标题须是一行、不空、不含「#」；背景不空（#742 起 build_adrs.py 已删，校验在本脚本）。
"""
import datetime, json, os, re, subprocess, sys

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "docs", "modernization", "adr")
SRC_PATH = "docs/modernization/adr/src"
NAME = re.compile(rf"{SRC_PATH}/ADR-(\d{{4}})\.md")


def max_number(paths):
    return max((int(m.group(1)) for p in paths if (m := NAME.fullmatch(p))), default=0)


def main_numbers(root):
    r = subprocess.run(["git", "ls-tree", "-r", "--name-only", "origin/main", SRC_PATH], cwd=root, capture_output=True, text=True)
    if r.returncode:
        raise RuntimeError(f"取不到 origin/main 的 ADR 目录：{r.stderr.strip()}")
    return r.stdout.split()


def pr_numbers(root):
    r = subprocess.run(["gh", "pr", "list", "--state", "open", "--limit", "200", "--json", "number,files"], cwd=root, capture_output=True, text=True)
    if r.returncode:
        raise RuntimeError(f"取不到在途 PR（gh pr list 失败）：{r.stderr.strip()}")
    return [f["path"] for pr in json.loads(r.stdout) for f in pr["files"]]


def shell(no, title, session, tier, background, date):
    return (f"# ADR-{no}：{title}\n\n- **状态**：提议\n- **日期**：{date}\n- **档位**：{tier}\n- **起草**：{session}\n- **类别**：待定\n"
            "- **BC**：\n  - 待定\n- **阶段**：\n  - 待定\n\n" f"## 背景\n\n{background.strip()}\n")


def problems(title, background):
    out = []
    if not title.strip() or "\n" in title or "#" in title:
        out.append("标题须是一行、不空、不含「#」")
    if not background.strip():
        out.append("背景不能为空")
    return out


def main(argv):
    opts = dict(a[2:].split("=", 1) if "=" in a else (a[2:], True) for a in argv if a.startswith("--"))
    values = [a for a in argv if not a.startswith("--")]
    session, tier = opts.get("session"), opts.get("tier", "正式")
    date = opts.get("date", datetime.date.today().isoformat())
    if len(values) != 2 or not session or session is True or tier not in ("正式", "轻量"):
        sys.exit(__doc__)
    root = subprocess.run(["git", "rev-parse", "--show-toplevel"], cwd=BASE, capture_output=True, text=True, check=True).stdout.strip()
    try:
        if not opts.get("no-fetch"):
            subprocess.run(["git", "fetch", "-q", "origin", "main"], cwd=root, check=True, capture_output=True)
        paths = main_numbers(root)
        if not opts.get("no-pr-check"):
            paths += pr_numbers(root)
    except (RuntimeError, subprocess.CalledProcessError) as e:
        sys.exit(f"失败：{e}")
    paths += [os.path.join(SRC_PATH, f) for f in os.listdir(os.path.join(BASE, "src"))]
    no = f"{max_number(paths) + 1:04d}"
    bad = problems(values[0], values[1])
    if bad:
        sys.exit("失败（未留下文件）：" + "；".join(bad))
    path = os.path.join(BASE, "src", f"ADR-{no}.md")
    open(path, "x", encoding="utf-8").write(shell(no, values[0], session, tier, values[1], date))
    print(os.path.relpath(path, root))


def self_test():
    paths = [f"{SRC_PATH}/ADR-0050.md", f"{SRC_PATH}/ADR-0051.md", "docs/other/ADR-0099.md", f"{SRC_PATH}/_readme_intro.md"]
    assert max_number(paths) == 51 and max_number([]) == 0
    assert max_number(paths + [f"{SRC_PATH}/ADR-0053.md"]) == 53   # 在途 PR 比 main 大的号也避让
    for tier in ("正式", "轻量"):
        text = shell("0100", "标题", "one-architecture", tier, "要决定什么。为什么现在。触发出处：intent x。", "2026-10-16")
        assert text.startswith("# ADR-0100：标题\n") and "- **状态**：提议" in text and f"- **档位**：{tier}" in text and "- **类别**：待定" in text and text.endswith("触发出处：intent x。\n")
    assert not problems("标题", "背景") and problems("", "背景") and problems("a\nb", "背景") and problems("# 标题", "背景") and problems("标题", "  ")
    with open(os.path.join(BASE, "src", "ADR-0001.md"), encoding="utf-8") as f:   # 现有正本的头部格式与空壳一致
        head = f.read()
    assert head.startswith("# ADR-0001：") and "- **状态**：" in head and "- **日期**：" in head
    print("new_adr self-test: ok")


if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    else:
        main(sys.argv[1:])
