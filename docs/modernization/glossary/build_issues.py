#!/usr/bin/env python3
"""从 glossary/issues/ 生成 glossary/ISSUES.md（用语问题清单与积压计数；不入仓，与 INTENT.md 同类）。

用法：
  python3 docs/modernization/glossary/build_issues.py              校验片段并生成 ISSUES.md
  python3 docs/modernization/glossary/build_issues.py --self-test  校验规则自测

片段由 new_term_issue.py 生成，一条问题一个文件 <日期>-<HHMMSS>-<会话名>-<短名>.md，格式见 TEMPLATE。
登记会话只新增；状态与处理两行由 one-glossary 改（SESSION_PLAYBOOK §5「提议术语按批次清理」）。
积压 = 未关闭的问题 + backend/glossary/proposed/*.yaml 中 status 为 proposed 的条目（含错误码），阈值 THRESHOLD。
"""
import os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))
ISSUES = os.path.join(HERE, "issues")
OUT = os.path.join(HERE, "ISSUES.md")
PROPOSED = os.path.join(ROOT, "backend", "glossary", "proposed")
THRESHOLD = 100
KINDS = {"new": "新词", "dialect": "疑似方言", "conflict": "同名异义", "duplicate": "同义异名", "other": "其他"}
STATES = ("待处理", "已预判", "待用户", "已关闭")
KEYS = ["词或现象", "出处", "类型", "登记会话", "状态", "处理"]
TEMPLATE = "## 用语问题\n\n" + "".join(f"- {k}：{{{i}}}\n" for i, k in enumerate(KEYS))
FILE = re.compile(r"(\d{4}-\d{2}-\d{2})-(\d{6})-.+\.md")


class IssueError(ValueError):
    pass


def parse_issue(text, file_name):
    m = FILE.fullmatch(file_name)
    if not m:
        raise IssueError(f"{file_name}：文件名须为 <日期>-<HHMMSS>-<会话名>-<短名>.md（用 new_term_issue.py 生成）")
    lines = text.rstrip("\n").split("\n")
    if lines[:2] != ["## 用语问题", ""] or len(lines) != 2 + len(KEYS):
        raise IssueError(f"{file_name}：须为「## 用语问题」、空行，再依次 {len(KEYS)} 行「- 键：值」")
    item = {"date": m.group(1), "file": file_name}
    for key, line in zip(KEYS, lines[2:]):
        prefix = f"- {key}："
        if not line.startswith(prefix):
            raise IssueError(f"{file_name}：第 {lines.index(line) + 1} 行须以「{prefix}」开头")
        value = line[len(prefix):].strip()
        if re.search(r"(?<!\\)\|", value):
            raise IssueError(f"{file_name}：「{key}」不得含未转义的「|」")
        item[key] = value
    for key in ("词或现象", "出处", "登记会话"):
        if not item[key]:
            raise IssueError(f"{file_name}：「{key}」不能为空")
    if item["类型"] not in KINDS:
        raise IssueError(f"{file_name}：类型须为 {'|'.join(KINDS)}")
    if item["状态"] not in STATES:
        raise IssueError(f"{file_name}：状态须为 {'、'.join(STATES)}")
    if item["状态"] != "待处理" and not item["处理"]:
        raise IssueError(f"{file_name}：状态为「{item['状态']}」时须写处理（预判结论与依据，或去向）")
    return item


def proposed_counts():
    sys.path.insert(0, os.path.join(ROOT, "backend", "glossary"))
    from build_proposed_index import parse_yaml
    total = codes = 0
    for f in sorted(os.listdir(PROPOSED)):
        if f.endswith(".yaml"):
            for t in parse_yaml(open(os.path.join(PROPOSED, f), encoding="utf-8").read(), f).get("terms", []):
                if t.get("status") == "proposed":
                    total += 1
                    codes += t.get("kind") == "error_code"
    return total, codes


def render(items, proposed, codes):
    open_items = [i for i in items if i["状态"] != "已关闭"]
    closed = [i for i in items if i["状态"] == "已关闭"]
    backlog = len(open_items) + proposed
    head = ("# 用语问题清单\n\n<!-- 由 docs/modernization/glossary/build_issues.py 从 glossary/issues/ 生成，不入仓，不要手改。 -->\n\n"
            "登记：`python3 docs/modernization/glossary/new_term_issue.py --session=<会话名> \"<词或现象>\" \"<出处>\" "
            "[--kind=new|dialect|conflict|duplicate|other]`；只登记、不自行裁定，由 one-glossary 处理（SESSION_PLAYBOOK §5）。\n\n"
            f"**积压 {backlog} 条**（阈值 {THRESHOLD}，{'已超' if backlog >= THRESHOLD else '未超'}）：未关闭问题 {len(open_items)} 条，"
            f"提议术语 {proposed} 条（其中错误码 {codes} 条）。\n")
    cols = "| 日期 | 登记会话 | 类型 | 词或现象 | 出处 | 状态 | 处理 |\n|---|---|---|---|---|---|---|\n"

    def rows(group):
        return "".join(f"| {i['date']} | {i['登记会话']} | {KINDS[i['类型']]} | {i['词或现象']} | {i['出处']} | {i['状态']} | {i['处理']} |\n"
                       for i in group)

    out = head + "\n## 未关闭\n\n" + (cols + rows(open_items) if open_items else "无。\n")
    out += f"\n## 已关闭（{len(closed)} 条）\n\n" + (cols + rows(closed) if closed else "无。\n")
    return out


def self_test():
    failures = []
    good = TEMPLATE.format("OH工时", "cost-timesheet.yaml:40", "dialect", "one-ops", "待处理", "")
    item = parse_issue(good, "2026-10-15-120000-one-ops-OH工时.md")
    if item["类型"] != "dialect" or item["处理"] != "":
        failures.append("合规片段解析结果不对")
    for name, text, file_name in (
            ("文件名", good, "OH工时.md"),
            ("类型", good.replace("dialect", "slang"), "2026-10-15-120000-a.md"),
            ("状态", good.replace("待处理", "完成"), "2026-10-15-120000-a.md"),
            ("关闭须写处理", good.replace("待处理", "已关闭"), "2026-10-15-120000-a.md"),
            ("出处为空", good.replace("cost-timesheet.yaml:40", ""), "2026-10-15-120000-a.md"),
            ("竖线", good.replace("OH工时", "a|b"), "2026-10-15-120000-a.md"),
            ("少行", good.replace("- 处理：\n", ""), "2026-10-15-120000-a.md")):
        try:
            parse_issue(text, file_name)
            failures.append(f"{name}：应失败但通过")
        except IssueError:
            pass
    out = render([item], 99, 3)
    if "**积压 100 条**（阈值 100，已超）" not in out or "| 疑似方言 | OH工时 |" not in out:
        failures.append("渲染结果不对")
    if failures:
        sys.exit("自测失败：\n  " + "\n  ".join(failures))
    print("self-test: ok")


def main():
    try:
        items = [parse_issue(open(os.path.join(ISSUES, f), encoding="utf-8").read(), f)
                 for f in sorted(os.listdir(ISSUES)) if f.endswith(".md") and not f.startswith("_")]
    except IssueError as e:
        sys.exit(f"用语问题片段校验失败：{e}")
    proposed, codes = proposed_counts()
    open(OUT, "w", encoding="utf-8").write(render(items, proposed, codes))
    backlog = sum(i["状态"] != "已关闭" for i in items) + proposed
    print(f"ISSUES.md: 用语积压 {backlog} 条（阈值 {THRESHOLD}）：未关闭问题 {backlog - proposed}，提议术语 {proposed}")


if __name__ == "__main__":
    self_test() if "--self-test" in sys.argv[1:] else main()
