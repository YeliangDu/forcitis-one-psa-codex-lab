#!/usr/bin/env python3
"""从 glossary/issues/ 与 glossary/decision-sheets/ 生成 glossary/ISSUES.md（用语问题清单与积压计数；不入仓，与 INTENT.md 同类）。

用法：
  python3 docs/modernization/glossary/build_issues.py              校验片段并生成 ISSUES.md
  python3 docs/modernization/glossary/build_issues.py --self-test  校验规则自测

- 问题片段 issues/<日期>-<HHMMSS>-<会话名>-<短名>.md 由 new_term_issue.py 生成（格式见 TEMPLATE），只增不改，状态恒为「待处理」。
- 处理片段 issues/<问题文件名去 .md>.resolution.md 由 one-glossary 用 resolve_term_issue.py 写（格式见 RESOLUTION），
  记当前状态与处理；问题的状态以它为准（SESSION_PLAYBOOK 附 M6）。
- 决策单片段 decision-sheets/<日期>.md：每出一次用语决策单一份，首行「# 用语决策单 <日期>」。
- 门槛（用户 2026-10-15：「门槛只算业务用语，错误码单列」）：业务用语积压 = backend/glossary/proposed/*.yaml 中 status 为
  proposed 且 kind 不是 error_code 的条目 + 未关闭的问题，达到 THRESHOLD 即出决策单；错误码单列，归结构重构主线。
  距上次决策单超过 SHEET_DAYS 天时标出。
"""
import datetime, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))
ISSUES = os.path.join(HERE, "issues")
SHEETS = os.path.join(HERE, "decision-sheets")
OUT = os.path.join(HERE, "ISSUES.md")
PROPOSED = os.path.join(ROOT, "backend", "glossary", "proposed")
THRESHOLD = 100
SHEET_DAYS = 7
KINDS = {"new": "新词", "dialect": "疑似方言", "conflict": "同名异义", "duplicate": "同义异名", "other": "其他"}
STATES = ("已预判", "待用户", "已关闭")
KEYS = ["词或现象", "出处", "使用范围", "bc", "类型", "登记会话", "状态"]
SCOPES = ("domain", "kernel", "acl")
# B1 之前用旧 new_term_issue.py 登记的片段没有「使用范围」「bc」两行；片段只增不改，按文件名时刻在此之前的照旧格式读
KEYS_V1 = ["词或现象", "出处", "类型", "登记会话", "状态"]
V1_BEFORE = "2026-10-16-000000"
TEMPLATE = "## 用语问题\n\n" + "".join(f"- {k}：{{{i}}}\n" for i, k in enumerate(KEYS))
BCS = os.path.join(HERE, "bcs.yaml")
RESOLUTION = "## 处理\n\n- 状态：{0}\n- 处理：{1}\n"
FILE = re.compile(r"(\d{4}-\d{2}-\d{2})-(\d{6})-.+\.md")
SHEET = re.compile(r"(\d{4}-\d{2}-\d{2})\.md")


class IssueError(ValueError):
    pass


def cell(value, where):
    if re.search(r"(?<!\\)\|", value):
        raise IssueError(f"{where}：不得含未转义的「|」")
    return value


def keyed_lines(text, title, keys, where):
    lines = text.rstrip("\n").split("\n")
    if lines[:2] != [title, ""] or len(lines) != 2 + len(keys):
        raise IssueError(f"{where}：须为「{title}」、空行，再依次 {len(keys)} 行「- 键：值」")
    out = {}
    for number, (key, line) in enumerate(zip(keys, lines[2:]), 3):
        prefix = f"- {key}："
        if not line.startswith(prefix):
            raise IssueError(f"{where}：第 {number} 行须以「{prefix}」开头")
        out[key] = cell(line[len(prefix):].strip(), f"{where} {key}")
    return out


def bc_ids():
    with open(BCS, encoding="utf-8") as f:
        return {i for i, k in re.findall(r"(?m)^- id: (\S+)\n(?:  .*\n)*?  kind: (\S+)$", f.read()) if k != "system"}


def parse_issue(text, file_name):
    m = FILE.fullmatch(file_name)
    if not m or file_name.endswith(".resolution.md"):
        raise IssueError(f"{file_name}：文件名须为 <日期>-<HHMMSS>-<会话名>-<短名>.md（用 new_term_issue.py 生成）")
    if f"{m.group(1)}-{m.group(2)}" < V1_BEFORE and len(text.rstrip("\n").split("\n")) == 2 + len(KEYS_V1):
        item = dict(keyed_lines(text, "## 用语问题", KEYS_V1, file_name), 使用范围="", bc="")
    else:
        item = keyed_lines(text, "## 用语问题", KEYS, file_name)
    if item["使用范围"] and (item["使用范围"] not in SCOPES or item["bc"] not in bc_ids()) or not item["使用范围"] and f"{m.group(1)}-{m.group(2)}" >= V1_BEFORE:
        raise IssueError(f"{file_name}：使用范围须为 {'|'.join(SCOPES)}，bc 须在 glossary/bcs.yaml 中")
    for key in ("词或现象", "出处", "登记会话"):
        if not item[key]:
            raise IssueError(f"{file_name}：「{key}」不能为空")
    if item["类型"] not in KINDS:
        raise IssueError(f"{file_name}：类型须为 {'|'.join(KINDS)}")
    if item["状态"] != "待处理":
        raise IssueError(f"{file_name}：问题片段只增不改，状态须为「待处理」；处理写进 {file_name[:-3]}.resolution.md")
    return dict(item, date=m.group(1), stem=file_name[:-3], 处理="")


def parse_resolution(text, file_name):
    r = keyed_lines(text, "## 处理", ["状态", "处理"], file_name)
    if r["状态"] not in STATES:
        raise IssueError(f"{file_name}：状态须为 {'、'.join(STATES)}")
    if not r["处理"]:
        raise IssueError(f"{file_name}：处理不能为空（预判结论与依据，或去向）")
    return r


def last_sheet(names):
    dates = []
    for f in names:
        m = SHEET.fullmatch(f)
        if not m:
            raise IssueError(f"decision-sheets/{f}：文件名须为 <日期>.md")
        dates.append(m.group(1))
    return max(dates) if dates else None


def check_sheet(text, file_name):
    lines = text.strip().split("\n")
    if lines[0] != f"# 用语决策单 {file_name[:-3]}" or len(lines) < 3:
        raise IssueError(f"decision-sheets/{file_name}：首行须为「# 用语决策单 {file_name[:-3]}」，其后写条数、页面路径与答复状态")


def load_issues(files):
    items, resolutions = {}, {}
    for f, text in files.items():
        if f.endswith(".resolution.md"):
            resolutions[f[: -len(".resolution.md")]] = parse_resolution(text, f)
        else:
            item = parse_issue(text, f)
            items[item["stem"]] = item
    orphans = sorted(set(resolutions) - set(items))
    if orphans:
        raise IssueError(f"处理片段没有对应的问题：{'、'.join(o + '.resolution.md' for o in orphans)}")
    for stem, r in resolutions.items():
        items[stem].update(r)
    return [items[k] for k in sorted(items)]


def proposed_counts():
    sys.path.insert(0, os.path.join(ROOT, "backend", "glossary"))
    from build_proposed_index import parse_yaml
    business = codes = 0
    for f in sorted(os.listdir(PROPOSED)):
        if f.endswith(".yaml"):
            for t in parse_yaml(open(os.path.join(PROPOSED, f), encoding="utf-8").read(), f).get("terms", []):
                if t.get("status") == "proposed":
                    if t.get("kind") == "error_code":
                        codes += 1
                    else:
                        business += 1
    return business, codes


def render(items, business, codes, sheet, today):
    open_items = [i for i in items if i["状态"] != "已关闭"]
    closed = [i for i in items if i["状态"] == "已关闭"]
    backlog = len(open_items) + business
    if sheet:
        days = (today - datetime.date.fromisoformat(sheet)).days
        since = f"距上次决策单（{sheet}）{days} 天" + (f"，**已超过 {SHEET_DAYS} 天**" if days > SHEET_DAYS else "")
    else:
        since = "尚无决策单"
    head = ("# 用语问题清单\n\n<!-- 由 docs/modernization/glossary/build_issues.py 生成，不入仓，不要手改。 -->\n\n"
            "登记：`python3 docs/modernization/glossary/new_term_issue.py --session=<会话名> \"<词或现象>\" \"<出处>\" "
            "[--kind=new|dialect|conflict|duplicate|other]`；只登记、不自行裁定，由 one-glossary 处理（SESSION_PLAYBOOK 附 M6）。\n\n"
            f"**业务用语积压 {backlog} 条**（达到 {THRESHOLD} 条即出决策单，{'已达到' if backlog >= THRESHOLD else '未达到'}）："
            f"业务提议术语 {business} 条、未关闭问题 {len(open_items)} 条；{since}。\n\n"
            f"错误码提议 {codes} 条单列，不计入门槛，归结构重构主线（R-5、CS-2.24），阶段出口前清零。\n")
    cols = "| 日期 | 登记会话 | 类型 | 词或现象 | 使用范围 | bc | 出处 | 状态 | 处理 |\n|---|---|---|---|---|---|---|---|---|\n"

    def rows(group):
        return "".join(f"| {i['date']} | {i['登记会话']} | {KINDS[i['类型']]} | {i['词或现象']} | {i['使用范围']} | {i['bc']} | {i['出处']} | {i['状态']} | {i['处理']} |\n"
                       for i in group)

    out = head + "\n## 未关闭\n\n" + (cols + rows(open_items) if open_items else "无。\n")
    out += f"\n## 已关闭（{len(closed)} 条）\n\n" + (cols + rows(closed) if closed else "无。\n")
    return out


def self_test():
    failures = []

    def rejects(name, fn):
        try:
            fn()
        except IssueError:
            return
        failures.append(f"{name}：应失败但通过")

    name = "2026-10-15-120000-one-ops-OH工时.md"
    good = TEMPLATE.format("OH工时", "cost-timesheet.yaml:40", "domain", "cost.timesheet", "dialect", "one-ops", "待处理")
    old = "## 用语问题\n\n" + "".join(f"- {k}：{v}\n" for k, v in zip(KEYS_V1, ("OH工时", "x.yaml:1", "dialect", "one-ops", "待处理")))
    if load_issues({"2026-10-15-173736-one-architecture-x.md": old})[0]["使用范围"] != "":
        failures.append("截止前的旧格式片段应照常读取")
    rejects("截止后的旧格式片段", lambda: parse_issue(old, "2026-10-16-090000-one-ops-x.md"))
    rejects("使用范围取值", lambda: parse_issue(good.replace("- 使用范围：domain", "- 使用范围：领域"), name))
    rejects("bc 不在 bcs.yaml", lambda: parse_issue(good.replace("- bc：cost.timesheet", "- bc：cost.hours"), name))
    items = load_issues({name: good})
    if items[0]["状态"] != "待处理" or items[0]["处理"] != "":
        failures.append("合规问题片段解析结果不对")
    rejects("文件名", lambda: parse_issue(good, "OH工时.md"))
    rejects("类型", lambda: parse_issue(good.replace("dialect", "slang"), name))
    rejects("新片段状态必须是待处理", lambda: parse_issue(good.replace("待处理", "已关闭"), name))
    rejects("出处为空", lambda: parse_issue(good.replace("cost-timesheet.yaml:40", ""), name))
    rejects("竖线", lambda: parse_issue(good.replace("OH工时", "a|b"), name))
    rejects("少行", lambda: parse_issue(good.replace("- 状态：待处理\n", ""), name))
    res = name[:-3] + ".resolution.md"
    closed = load_issues({name: good, res: RESOLUTION.format("已关闭", "方言，并入 overhead_hours（决策单 2026-10-20）")})
    if closed[0]["状态"] != "已关闭":
        failures.append("处理片段未覆盖状态")
    rejects("处理状态取值", lambda: load_issues({name: good, res: RESOLUTION.format("待处理", "x")}))
    rejects("处理为空", lambda: load_issues({name: good, res: RESOLUTION.format("已预判", "")}))
    rejects("孤立处理片段", lambda: load_issues({res: RESOLUTION.format("已预判", "x")}))
    rejects("决策单文件名", lambda: last_sheet(["第三轮.md"]))
    rejects("决策单首行", lambda: check_sheet("# 决策单\n\n- 条数：3\n", "2026-10-20.md"))
    if last_sheet(["2026-10-01.md", "2026-10-20.md"]) != "2026-10-20":
        failures.append("最近决策单日期不对")
    today = datetime.date(2026, 10, 28)
    out = render(items, 99, 153, "2026-10-20", today)
    for want in ("**业务用语积压 100 条**（达到 100 条即出决策单，已达到）", "距上次决策单（2026-10-20）8 天，**已超过 7 天**",
                 "错误码提议 153 条单列", "| 疑似方言 | OH工时 | domain | cost.timesheet |"):
        if want not in out:
            failures.append(f"渲染缺少：{want}")
    if "未达到" not in render([], 10, 0, None, today) or "尚无决策单" not in render([], 10, 0, None, today):
        failures.append("未达到门槛或无决策单的渲染不对")
    if "已超过" in render([], 0, 0, "2026-10-21", today):
        failures.append("满 7 天不应标出")
    if failures:
        sys.exit("自测失败：\n  " + "\n  ".join(failures))
    print("self-test: ok")


def main():
    try:
        files = {f: open(os.path.join(ISSUES, f), encoding="utf-8").read()
                 for f in sorted(os.listdir(ISSUES)) if f.endswith(".md") and not f.startswith("_")}
        items = load_issues(files)
        sheets = [f for f in sorted(os.listdir(SHEETS)) if f.endswith(".md") and not f.startswith("_")]
        for f in sheets:
            check_sheet(open(os.path.join(SHEETS, f), encoding="utf-8").read(), f)
        sheet = last_sheet(sheets)
    except IssueError as e:
        sys.exit(f"用语问题片段校验失败：{e}")
    business, codes = proposed_counts()
    open(OUT, "w", encoding="utf-8").write(render(items, business, codes, sheet, datetime.date.today()))
    backlog = sum(i["状态"] != "已关闭" for i in items) + business
    print(f"ISSUES.md: 业务用语积压 {backlog} 条（达到 {THRESHOLD} 出决策单）；错误码提议 {codes} 条单列；"
          + (f"上次决策单 {sheet}" if sheet else "尚无决策单"))


if __name__ == "__main__":
    self_test() if "--self-test" in sys.argv[1:] else main()
