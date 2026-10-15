#!/usr/bin/env python3
"""从 sessions/ 的会话功能画像生成 SESSIONS.md（一表一屏；不入仓，与 INTENT.md 同类，SESSION_PLAYBOOK §2.1 第 16 条）。

用法：
  python3 docs/modernization/build_sessions.py              校验画像并生成 SESSIONS.md
  python3 docs/modernization/build_sessions.py --self-test  校验规则自测

- sessions/<会话名>.md：一个在用会话一份画像，标题「# <会话名>」，其后按 FIELDS 的顺序各一个二级标题，每节非空；
  会话名为 one-*、与文件名一致、全局唯一；「类型」为「长期」或「工作」；「当前状态」为一行。
- sessions/_retired.md：已交接、一次性会话，每行「- <会话名>：<状态>」，只进汇总表、不写画像。
- 「上下文文档」一节中反引号括起的 .md 路径（仓库根相对）即清空或压缩后钩子注入的文档（scripts/hooks/session_context.py，
  解析函数与钩子共用）：路径必须存在；长期会话须至少有一份本画像以外的上下文文档。
- 独占与不碰靠调度派工、评审时人工核对，不做自动校验（SESSION_PLAYBOOK §2.1 第 16 条）。
不合规时生成失败，不写产物。
"""
import os, re, sys

BASE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(BASE))
SESSIONS = os.path.join(BASE, "sessions")
sys.path.insert(0, os.path.join(ROOT, "scripts", "hooks"))
from session_context import context_docs  # noqa: E402
OUT = os.path.join(BASE, "SESSIONS.md")
FIELDS = ["类型", "职责", "独占", "不碰", "输入", "输出", "触发与节奏", "自动化", "上下文文档", "交接条件", "权限边界", "当前状态"]
KINDS = ("长期", "工作")
NAME = re.compile(r"one-[a-z0-9]+(-[a-z0-9]+)*")
RETIRED_NAME = re.compile(r"(one|psa)-[a-z0-9]+(-[a-z0-9]+)*(-\d+)?")


class ProfileError(ValueError):
    pass


def one_line(text, where):
    if "\n" in text or re.search(r"(?<!\\)\|", text) or not text.strip():
        raise ProfileError(f"{where}：须为非空单行且不含未转义的「|」")
    return text.strip()


def parse_profile(text, file_name):
    stem = file_name[:-3]
    lines = text.rstrip("\n").split("\n")
    if not lines or lines[0] != f"# {stem}":
        raise ProfileError(f"{file_name}：首行须为「# {stem}」（与文件名一致）")
    if not NAME.fullmatch(stem):
        raise ProfileError(f"{file_name}：会话名须为 one-* 小写连字符写法")
    sections, current = {}, None
    for line in lines[1:]:
        if line.startswith("## "):
            current = line[3:].strip()
            if current in sections:
                raise ProfileError(f"{file_name}：字段「{current}」重复")
            sections[current] = []
        elif line.startswith("# "):
            raise ProfileError(f"{file_name}：只能有一个一级标题")
        elif current is None:
            if line.strip():
                raise ProfileError(f"{file_name}：标题与第一个字段之间不得有正文")
        else:
            sections[current].append(line)
    if list(sections) != FIELDS:
        missing = [f for f in FIELDS if f not in sections]
        extra = [f for f in sections if f not in FIELDS]
        raise ProfileError(f"{file_name}：字段须依次为 {'、'.join(FIELDS)}"
                           + (f"；缺 {'、'.join(missing)}" if missing else "") + (f"；多出 {'、'.join(extra)}" if extra else "")
                           + ("" if missing or extra else "；顺序不对"))
    body = {k: "\n".join(v).strip() for k, v in sections.items()}
    for k, v in body.items():
        if not v:
            raise ProfileError(f"{file_name}：字段「{k}」为空")
    if body["类型"] not in KINDS:
        raise ProfileError(f"{file_name}：类型须为「长期」或「工作」")
    one_line(body["当前状态"], f"{file_name} 当前状态")
    return {"name": stem, **body}


def parse_retired(text):
    rows = []
    for number, line in enumerate(text.splitlines(), 1):
        if not line.strip() or line.startswith("#") or line.startswith("<!--"):
            continue
        m = re.fullmatch(r"- (\S+)：(.+)", line)
        if not m or not RETIRED_NAME.fullmatch(m.group(1)):
            raise ProfileError(f"_retired.md:{number}：须为「- <会话名>：<状态>」，会话名为 one-* 或历史 psa-*")
        rows.append((m.group(1), one_line(m.group(2), f"_retired.md:{number}")))
    return rows


def check(profiles, retired, exists):
    names = [p["name"] for p in profiles] + [n for n, _ in retired]
    dup = sorted({n for n in names if names.count(n) > 1})
    if dup:
        raise ProfileError(f"会话名重复：{'、'.join(dup)}")
    for p in profiles:
        own = f"docs/modernization/sessions/{p['name']}.md"
        docs = context_docs(f"## 上下文文档\n{p['上下文文档']}\n")
        for path in docs:
            if not exists(path):
                raise ProfileError(f"sessions/{p['name']}.md：上下文文档 {path} 不存在（写仓库根相对路径）")
        if p["类型"] == "长期" and not [d for d in docs if d != own]:
            raise ProfileError(f"sessions/{p['name']}.md：长期会话须在「上下文文档」列出至少一份本画像以外的文档")


def summary(text):
    first = next(l for l in text.splitlines() if l.strip())
    return re.sub(r"^[-*]\s+", "", first.strip())


def render(profiles, retired):
    head = ("# 会话一览\n\n<!-- 由 docs/modernization/build_sessions.py 从 sessions/*.md 生成，不入仓，不要手改；"
            "改画像后运行 python3 docs/modernization/build_all.py。 -->\n\n"
            "画像规则见 SESSION_PLAYBOOK §2.1 第 16 条；画像全文在 `sessions/<会话名>.md`。\n\n"
            "| 会话 | 类型 | 职责 | 上下文文档（钩子注入） | 当前状态 |\n|---|---|---|---|---|\n")
    order = sorted(profiles, key=lambda p: (KINDS.index(p["类型"]), p["name"]))
    rows = []
    for p in order:
        docs = "、".join(f"`{path.removeprefix('docs/modernization/')}`" for path in context_docs(f"## 上下文文档\n{p['上下文文档']}\n")) or "—"
        rows.append(f"| {p['name']} | {p['类型']} | {one_line(summary(p['职责']), p['name'] + ' 职责首行')} | {docs} | {p['当前状态']} |\n")
    rows += [f"| {n} | 已结束 | — | — | {s} |\n" for n, s in retired]
    return head + "".join(rows)


def load():
    profiles = []
    for f in sorted(os.listdir(SESSIONS)):
        if f.endswith(".md") and not f.startswith("_"):
            profiles.append(parse_profile(open(os.path.join(SESSIONS, f), encoding="utf-8").read(), f))
    retired_path = os.path.join(SESSIONS, "_retired.md")
    retired = parse_retired(open(retired_path, encoding="utf-8").read()) if os.path.exists(retired_path) else []
    check(profiles, retired, lambda p: os.path.isfile(os.path.join(ROOT, p)))
    return profiles, retired


def self_test():
    failures = []
    good = "# one-x\n\n" + "".join(f"## {f}\n\n{'工作' if f == '类型' else '内容'}\n\n" for f in FIELDS)

    def rejects(name, fn):
        try:
            fn()
        except ProfileError:
            return
        failures.append(f"{name}：应失败但通过")

    p = parse_profile(good, "one-x.md")
    if p["类型"] != "工作" or p["当前状态"] != "内容":
        failures.append("合规画像解析结果不对")
    rejects("文件名不一致", lambda: parse_profile(good, "one-y.md"))
    rejects("非 one-* 名", lambda: parse_profile(good.replace("# one-x", "# psa-x"), "psa-x.md"))
    rejects("缺字段", lambda: parse_profile(good.replace("## 权限边界\n\n内容\n\n", ""), "one-x.md"))
    rejects("字段为空", lambda: parse_profile(good.replace("## 独占\n\n内容", "## 独占\n\n"), "one-x.md"))
    rejects("类型不合法", lambda: parse_profile(good.replace("## 类型\n\n工作", "## 类型\n\n临时"), "one-x.md"))
    rejects("当前状态多行", lambda: parse_profile(good.rstrip("\n") + "\n第二行\n", "one-x.md"))
    rejects("顺序不对", lambda: parse_profile(good.replace("## 独占", "## TMP").replace("## 不碰", "## 独占").replace("## TMP", "## 不碰"), "one-x.md"))
    own = "docs/modernization/sessions/one-x.md"
    yes = lambda path: True
    p = dict(p, 上下文文档=f"`{own}`")
    check([p], [("one-old-1", "已交接")], yes)
    rejects("重名", lambda: check([p], [("one-x", "已交接")], yes))
    rejects("上下文文档不存在", lambda: check([p], [], lambda path: False))
    long_lived = dict(p, 类型="长期")
    rejects("长期会话缺上下文文档", lambda: check([long_lived], [], yes))
    check([dict(long_lived, 上下文文档=f"- `docs/a.md`\n- `{own}`")], [], yes)
    rejects("退役行格式", lambda: parse_retired("- one-a 已交接\n"))
    if parse_retired("<!-- 注释 -->\n- psa-coordinator-1：已交接\n") != [("psa-coordinator-1", "已交接")]:
        failures.append("退役行解析不对")
    if failures:
        sys.exit("自测失败：\n  " + "\n  ".join(failures))
    print("self-test: ok")


def main():
    try:
        profiles, retired = load()
    except (ProfileError, StopIteration) as e:
        sys.exit(f"会话画像校验失败：{e}")
    open(OUT, "w", encoding="utf-8").write(render(profiles, retired))
    print(f"SESSIONS.md: {len(profiles)} 份画像，{len(retired)} 个已结束会话")


if __name__ == "__main__":
    self_test() if "--self-test" in sys.argv[1:] else main()
