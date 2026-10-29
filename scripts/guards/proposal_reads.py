#!/usr/bin/env python3
"""拦截代码读 proposals/（TEAM-108；GOV-1023-ARC-01 第②步）。

扫描仓库里 proposals/ 目录以外的已跟踪脚本与测试（py、sh、java、kt、ts、mjs），凡在路径里写到 proposals/（前面是 / 或引号、行首，
后面是 / 或引号），须在 docs/architecture/proposal-reads.yaml 登记。失败条件：未登记；登记了却已不再写到；kind=data 的来源到期，或来源名已不在文件里；
kind 或字段不合法。architecture-guardrails/ 是守护测试的镜像，不扫。
用法：proposal_reads.py | proposal_reads.py --self-test
"""
import datetime, re, subprocess, sys, tempfile
from pathlib import Path

import yaml

REPO = Path(__file__).resolve().parents[2]
REGISTRY = REPO / "docs/architecture/proposal-reads.yaml"
SUFFIXES = (".py", ".sh", ".java", ".kt", ".ts", ".mjs")
SKIP = ("docs/architecture-guardrails/",)
MENTION = re.compile(r"""(?:^|[/"'])proposals(?:/|["'])""", re.M)
KINDS = {"data", "tool", "mention"}


def candidates(repo=REPO):
    files = subprocess.run(["git", "ls-files", "-z"], cwd=repo, capture_output=True, text=True, check=True).stdout.split("\0")
    found = {}
    for f in files:
        if not f.endswith(SUFFIXES) or "/proposals/" in "/" + f or f.startswith(SKIP):
            continue
        text = (repo / f).read_text(encoding="utf-8", errors="replace")
        if MENTION.search(text):
            found[f] = text
    return found


def problems(rows, found, today):
    errors, listed = [], set()
    for row in rows:
        path, kind = row.get("path"), row.get("kind")
        if not path or kind not in KINDS:
            errors.append(f"{path}: 须有 path，kind 取 {sorted(KINDS)}")
            continue
        if path in listed:
            errors.append(f"{path}: 重复登记")
        listed.add(path)
        if path not in found:
            errors.append(f"{path}: 已不再写到 proposals/，删掉这行登记")
            continue
        sources = row.get("sources") or []
        if kind != "data":
            if sources or not row.get("note"):
                errors.append(f"{path}: kind={kind} 不写 sources，须写 note 说明为什么不算读数据")
            continue
        if not sources:
            errors.append(f"{path}: kind=data 须列 sources（来源与到期日）")
        for s in sources:
            name, due = s.get("source"), s.get("due")
            if not name or not isinstance(due, datetime.date):
                errors.append(f"{path}: 来源须写 source 与 due（YYYY-MM-DD）：{s}")
            elif name not in found[path]:
                errors.append(f"{path}: 来源 {name} 已不在文件里，删掉这条")
            elif due < today:
                errors.append(f"{path}: 读 proposals/ 的 {name} 已过期（{due}），升正本后改读正本或删掉读取")
    errors += [f"{f}: 读 proposals/ 未登记，改读正本；确需暂读的在 docs/architecture/proposal-reads.yaml 登记来源与到期日" for f in sorted(set(found) - listed)]
    return errors


def main():
    rows = yaml.safe_load(REGISTRY.read_text(encoding="utf-8")).get("reads") or []
    errors = problems(rows, candidates(), datetime.date.today())
    if errors:
        sys.exit("proposals/ 读取检查失败：\n" + "\n".join(errors))
    n = {k: sum(r["kind"] == k for r in rows) for k in sorted(KINDS)}
    print(f"proposal_reads: 登记 {len(rows)} 个文件（data {n['data']}、tool {n['tool']}、mention {n['mention']}），无未登记或过期")


def self_test():
    today = datetime.date(2026, 11, 1)
    with tempfile.TemporaryDirectory() as d:
        repo = Path(d)
        git = lambda *a: subprocess.run(["git", *a], cwd=repo, check=True, capture_output=True)
        git("init", "-q")
        files = {"a.py": 'SRC = ARCH / "proposals" / "X_DRAFT.md"\n', "b.sh": "echo docs\n", "c.py": "# 见 proposals/ 说明\n",
                 "proposals/tool.py": 'open("proposals/y.yaml")\n', "d.java": 'Path.of("../proposals/F.md");\n', "e.md": "proposals/x\n"}
        for name, text in files.items():
            (repo / name).parent.mkdir(parents=True, exist_ok=True)
            (repo / name).write_text(text, encoding="utf-8")
        git("add", ".")
        found = candidates(repo)
        assert set(found) == {"a.py", "d.java"}, found   # 注释里的「见 proposals/」前无 / 或引号；proposals/ 内部与非代码文件不扫
        ok = [{"path": "a.py", "kind": "data", "sources": [{"source": "X_DRAFT.md", "due": datetime.date(2026, 11, 6)}]},
              {"path": "d.java", "kind": "tool", "note": "接线"}]
        assert problems(ok, found, today) == []
        assert any("未登记" in e for e in problems(ok[:1], found, today))
        assert any("已过期" in e for e in problems(ok, found, datetime.date(2026, 11, 7)))
        assert any("不再写到" in e for e in problems(ok + [{"path": "b.sh", "kind": "tool", "note": "x"}], found, today))
        assert any("已不在文件里" in e for e in problems([dict(ok[0], sources=[{"source": "GONE.md", "due": today}]), ok[1]], found, today))
        assert any("须列 sources" in e for e in problems([{"path": "a.py", "kind": "data"}, ok[1]], found, today))
        assert any("须写 note" in e for e in problems([ok[0], {"path": "d.java", "kind": "tool"}], found, today))
        assert any("kind 取" in e for e in problems([ok[0], {"path": "d.java", "kind": "later"}], found, today))
        assert any("重复登记" in e for e in problems(ok + ok[1:], found, today))
    rows = yaml.safe_load(REGISTRY.read_text(encoding="utf-8")).get("reads") or []
    assert not problems(rows, candidates(), datetime.date(2026, 10, 23)), "真实登记在 2026-10-23 不得失败"
    print("proposal_reads self-test: ok")


if __name__ == "__main__":
    self_test() if sys.argv[1:] == ["--self-test"] else main()
