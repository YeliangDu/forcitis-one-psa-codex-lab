#!/usr/bin/env python3
"""从 adr/src/ 生成 ADR-*.md 与 README.md（变更治理：SESSION_PLAYBOOK §12，README「ADR 生命周期与变更规则」）。

每份 ADR 一个源文件 src/ADR-NNNN.md，格式与生成的 ADR 相同：
  # ADR-NNNN：标题
  - **状态**：…
  - **日期**：YYYY-MM-DD
  - **取代**：ADR-00xx、ADR-00yy 第 N 条      （可选）
  ## 背景 / ## 决定 / [## 影响面 / ## 迁移方案 / ## 回滚方案 / ## 需同步修改的守护规则] / ## 后果 / ## 证据 / ## 备选方案（未采用）
README 由 src/_readme_intro.md、索引表、src/_readme_lifecycle.md、src/_readme_deviations.md 拼成。
校验（不通过则生成失败，不写任何产物）：
  1. 状态以「提议」「已采纳」「已被 ADR-」「已废弃」之一开头；
  2. 小节只能是上面列出的标题，按上面的顺序，背景 / 决定 / 后果 / 证据 / 备选方案必有；
  3. LEGACY_MAX 之后的新 ADR 必须写全四个必备小节；
  4. 已采纳的 ADR 声明取代 ADR-x 时：整份取代 → ADR-x 状态须以「已被 ADR-本号 取代」开头；
     部分取代 → ADR-x 状态中须含「已被 ADR-本号 取代」（例：已采纳（第 6 条已被 ADR-0024 取代））。
"""
import os, re, sys

os.chdir(os.path.dirname(os.path.abspath(__file__)))   # 产物总是写在 adr/ 下，与从哪个目录运行无关
LEGACY_MAX = "0023"   # 本规则生效前已存在的 ADR，不追补必备小节（缺口清单见 README）
STATES = ("提议", "已采纳", "已被 ADR-", "已废弃")
REQUIRED = ("影响面", "迁移方案", "回滚方案", "需同步修改的守护规则")
ORDER = ("背景", "决定", *REQUIRED, "后果", "证据", "备选方案（未采用）")
MANDATORY = ("背景", "决定", "后果", "证据", "备选方案（未采用）")
HEAD = re.compile(r"# ADR-(\d{4})：([^\n]+)\n\n- \*\*状态\*\*：([^\n]+)\n- \*\*日期\*\*：(\d{4}-\d{2}-\d{2})\n(?:- \*\*取代\*\*：([^\n]+)\n)?\n")


def parse(path):
    text = open(path, encoding="utf-8").read()
    m = HEAD.match(text)
    if not m:
        raise ValueError(f"{path}：头部须为「# ADR-NNNN：标题」、状态、日期（及可选的取代）行")
    no, title, status, date, sup = m.groups()
    if f"src/ADR-{no}.md" != path:
        raise ValueError(f"{path}：文件名与编号 ADR-{no} 不一致")
    chunks = re.split(r"(?m)^## ([^\n]+)\n\n", text[m.end():])
    if chunks[0]:
        raise ValueError(f"{path}：头部之后须直接是二级标题")
    names = chunks[1::2]
    secs = {n: b[:-2] if b.endswith("\n\n") else b[:-1] for n, b in zip(names, chunks[2::2])}
    if len(secs) != len(names) or any(n not in ORDER for n in names) or names != [n for n in ORDER if n in secs]:
        raise ValueError(f"{path}：小节须取自 {'、'.join(ORDER)}，不重复且按此顺序：{names}")
    missing = [n for n in MANDATORY if n not in secs]
    if missing:
        raise ValueError(f"{path}：缺少小节 {'、'.join(missing)}")
    return dict(no=no, title=title, status=status, date=date, supersedes=sup.split("、") if sup else [], secs=secs)


def render(a):
    sup = f"- **取代**：{'、'.join(a['supersedes'])}\n" if a["supersedes"] else ""
    body = "\n\n".join(f"## {n}\n\n{a['secs'][n]}" for n in ORDER if n in a["secs"])
    return f"# ADR-{a['no']}：{a['title']}\n\n- **状态**：{a['status']}\n- **日期**：{a['date']}\n{sup}\n{body}\n"


def validate(adrs):
    status_of = {a["no"]: a["status"] for a in adrs}
    errors = []
    for a in adrs:
        no, status = a["no"], a["status"]
        if not status.startswith(STATES):
            errors.append(f"ADR-{no}: 状态须以 {'/'.join(STATES)} 开头：{status[:30]}")
        if no > LEGACY_MAX:
            errors += [f"ADR-{no}: 缺少必备小节「{n}」" for n in REQUIRED if not a["secs"].get(n, "").strip()]
        if status.startswith("已采纳"):
            for ref in a["supersedes"]:
                m = re.match(r"ADR-(\d{4})(.*)", ref)
                if not m or m.group(1) not in status_of:
                    errors.append(f"ADR-{no}: 取代对象不存在：{ref}"); continue
                old, partial, mark = status_of[m.group(1)], m.group(2).strip(), f"已被 ADR-{no} 取代"
                if (partial and mark not in old) or (not partial and not old.startswith(mark)):
                    errors.append(f"ADR-{no}: 取代 {ref}，但 ADR-{m.group(1)} 的状态未标「{mark}」")
    return errors


def main():
    try:
        adrs = [parse(f"src/{f}") for f in sorted(os.listdir("src")) if re.fullmatch(r"ADR-\d{4}\.md", f)]
    except ValueError as e:
        sys.exit(f"ADR 源文件校验失败：{e}")
    errors = validate(adrs)
    if errors:
        sys.exit("ADR 校验失败：\n" + "\n".join(errors))
    for a in adrs:
        open(f"ADR-{a['no']}.md", "w", encoding="utf-8").write(render(a))
    rows = "".join(f"| [ADR-{a['no']}](ADR-{a['no']}.md) | {a['title']} | {a['status']} |\n" for a in adrs)
    readme = (open("src/_readme_intro.md", encoding="utf-8").read() + "\n| ADR | 标题 | 状态 |\n|---|---|---|\n" + rows + "\n"
              + open("src/_readme_lifecycle.md", encoding="utf-8").read() + "\n" + open("src/_readme_deviations.md", encoding="utf-8").read())
    open("README.md", "w", encoding="utf-8").write(readme)
    print(len(adrs), "ADRs written")


if __name__ == "__main__":
    main()
