#!/usr/bin/env python3
"""从片段目录生成 INTENT.md、SESSION_LOG.md、STATUS.md（用户 2026-10-14：「一条记录一个文件」）。

会话只新增或修改片段，不直接编辑这三个生成产物（SESSION_PLAYBOOK TEAM-15）。三个产物不入仓（.gitignore）：
本地运行 build_all.py 生成查看，或看 generated-docs 分支（main 更新后由 .github/workflows/generated-docs.yml 发布）。
- intent/：_header.md + 每条用户决定一个文件（## 原话 / ## 落点）
- session-log/：_header.md + 每条会话日志一个文件（以「## 日期 · 会话 · 单元」开头）
  两者的文件名为 <日期>-<HHMMSS>-<会话名>-<短名>.md（new_intent.py / new_log.py 生成，并行会话无需协调），
  或迁移时的旧格式 <日期>-<三位序号>-<短名>.md；同一天内旧格式在前、按序号，新格式在后、按时间，再按文件名。
  旧格式同日序号重复时只警告，不失败。
- status/：_header.md、_position.md；units/ 每个工作单元一个文件（键值行，按「排序」）；blockers/ 每条阻塞一个文件
片段格式不合规时生成失败，不写任何产物。
"""
import os, re, sys

BASE = os.path.dirname(os.path.abspath(__file__))
STATUS_KEYS = ["工作单元", "状态", "负责会话", "入口 / 阻塞", "下一条命令"]


def read(path):
    return open(path, encoding="utf-8").read()


WARNINGS = []


def fragments(folder):
    d = os.path.join(BASE, folder)
    return [os.path.join(d, f) for f in sorted(os.listdir(d)) if f.endswith(".md") and not f.startswith("_")]


def dated_fragments(folder):
    keyed, seen = [], {}
    for p in fragments(folder):
        name = os.path.basename(p)
        if m := re.match(r"(\d{4}-\d{2}-\d{2})-(\d{6})-", name):
            keyed.append(((m.group(1), 1, m.group(2), name), p))
        elif m := re.match(r"(\d{4}-\d{2}-\d{2})-(\d{3})-", name):
            keyed.append(((m.group(1), 0, m.group(2), name), p))
            seen.setdefault(m.group(1, 2), []).append(name)
        else:
            raise ValueError(f"{p}：文件名须为 <日期>-<HHMMSS>-<会话名>-<短名>.md（用 new_intent.py / new_log.py 生成）")
    WARNINGS.extend(f"{folder}/ 序号重复（按文件名排序）：{'、'.join(v)}" for v in seen.values() if len(v) > 1)
    return [p for _, p in sorted(keyed)]


def table_cell(text, where):
    # 表格单元格只能是一行，且不能含未转义的竖线，否则会拆坏表格
    if "\n" in text or re.search(r"(?<!\\)\|", text) or not text.strip():
        raise ValueError(f"{where}：单元格须为非空单行且不含未转义的「|」")
    return text


INTENT_TAGS = {"规则": r"(?:[A-Z]+-)?RULE-\d+(?:、(?:[A-Z]+-)?RULE-\d+)*", "影响的 ADR": r"ADR-\d{4}(?:、ADR-\d{4})*", "单子": r"[A-Za-z0-9_-]+", "题号": r"[A-Za-z0-9_-]+(?:、[A-Za-z0-9_-]+)*"}


def parse_intent(path, text):
    """(原话, 落点, {可选标注: 值})；可选小节「取代」（多行名单）居先，再是「规则」「影响的 ADR」「单子」「题号」按此顺序附在「落点」之后，供规则索引与 ADR 治理反查。"""
    m = re.fullmatch(r"## 原话\n\n(.*?)\n\n## 落点\n\n(.*?)\n(?:\n## 取代\n\n((?:- [^\n]+\n)+))?((?:\n## [^\n]+\n\n[^\n]+\n)*)", text, re.S)
    if not m:
        raise ValueError(f"{path}：格式须为「## 原话」空行 原话 空行「## 落点」空行 落点，之后可选「## 取代」（被取代的 intent 文件名，一行一个）及「## 规则」「## 影响的 ADR」「## 单子」「## 题号」各一行")
    tags = {}
    for name, value in re.findall(r"\n## ([^\n]+)\n\n([^\n]+)\n", m.group(4)):
        if name not in INTENT_TAGS or name in tags or not re.fullmatch(INTENT_TAGS[name], value):
            raise ValueError(f"{path}：可选小节「{name}」不合规（取自 {'、'.join(INTENT_TAGS)}，不重复，值形如 {INTENT_TAGS.get(name, '—')}）：{value[:30]}")
        tags[name] = value
    if list(tags) != [k for k in INTENT_TAGS if k in tags]:
        raise ValueError(f"{path}：可选小节须按 {'、'.join(INTENT_TAGS)} 的顺序")
    landing = m.group(2) + (f"（取代：{'、'.join(x.strip('- `') for x in m.group(3).splitlines() if x.strip())}）" if m.group(3) else "")
    return m.group(1), landing, tags


def build_intent():
    rows = []
    for p in dated_fragments("intent"):
        quote, landing, tags = parse_intent(p, read(p))
        extra = "".join(f"；{k}：{v}" for k, v in tags.items())
        rows.append(f"| {table_cell(quote, p)} | {table_cell(landing + extra, p)} |\n")
    return read(os.path.join(BASE, "intent/_header.md")) + "".join(rows)


def build_session_log():
    entries = []
    for p in dated_fragments("session-log"):
        t = read(p)
        if not re.match(r"## \d{4}-\d{2}-\d{2} · [^\n]+ ?· [^\n]+\n", t) or re.search(r"(?m)^## ", t[3:]):
            raise ValueError(f"{p}：须以「## 日期 · 会话名 · 工作单元」开头，且只含这一个二级标题")
        entries.append(t.rstrip("\n") + "\n")
    return read(os.path.join(BASE, "session-log/_header.md")) + "\n" + "\n".join(entries)


def adr_section():
    import datetime, importlib.util
    spec = importlib.util.spec_from_file_location("build_adrs", os.path.join(BASE, "adr", "build_adrs.py"))
    b = importlib.util.module_from_spec(spec); spec.loader.exec_module(b)
    return b.status_section(b.load_adrs(os.path.join(b.BASE, "src")), dict(today=datetime.date.today()))


def build_status():
    units = []
    for p in fragments("status/units"):
        kv = {}
        for line in read(p).rstrip("\n").split("\n"):
            k, sep, v = line.partition(": ")
            if not sep or k in kv:
                raise ValueError(f"{p}：每行须为「键: 值」且键不重复：{line[:30]}")
            kv[k] = v
        if set(kv) != {"排序", *STATUS_KEYS} or not kv["排序"].isdigit():
            raise ValueError(f"{p}：须恰好包含 排序（数字）与 {'、'.join(STATUS_KEYS)}")
        units.append((int(kv["排序"]), p, "| " + " | ".join(table_cell(kv[k], p) for k in STATUS_KEYS) + " |\n"))
    blockers = []
    for p in fragments("status/blockers"):
        t = read(p)
        if not re.fullmatch(r"- [^\n]+\n", t):
            raise ValueError(f"{p}：须为一行以「- 」开头的条目")
        blockers.append(t)
    head = "| " + " | ".join(STATUS_KEYS) + " |\n|---|---|---|---|---|\n"
    return (read(os.path.join(BASE, "status/_header.md")) + "\n"
            + read(os.path.join(BASE, "status/_position.md")) + "\n"
            + head + "".join(r for _, _, r in sorted(units))
            + "\n## 阻塞与待用户决定\n\n" + "".join(blockers) + adr_section())


def main():
    try:
        out = {"INTENT.md": build_intent(), "SESSION_LOG.md": build_session_log(), "STATUS.md": build_status()}
    except ValueError as e:
        sys.exit(f"片段校验失败：{e}")
    for name, text in out.items():
        open(os.path.join(BASE, name), "w", encoding="utf-8").write(text)
    for w in WARNINGS:
        print("警告：" + w, file=sys.stderr)
    print("records:", ", ".join(out))


if __name__ == "__main__":
    main()
