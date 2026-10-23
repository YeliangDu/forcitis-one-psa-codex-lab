#!/usr/bin/env python3
"""从 sessions/ 的会话功能画像生成 SESSIONS.md（一表一屏；不入仓，与 INTENT.md 同类，SESSION_PLAYBOOK TEAM-02、03）。

用法：
  python3 docs/modernization/build_sessions.py              校验画像并生成 SESSIONS.md
  python3 docs/modernization/build_sessions.py --self-test  校验规则自测

- sessions/<会话名>.md：一个在用会话一份画像，标题「# <会话名>」，其后按 FIELDS 的顺序各一个二级标题，每节非空；
  会话名为 one-*、与文件名一致、全局唯一；「类型」为「长期」或「工作」。画像不写“当前状态”：在途任务看 tasks/ 里的任务卡，交接看上下文文档的交接节
  （GOV-1022-COO-13，开场只注入一页开工单）。
- sessions/_retired.md：已交接、一次性会话，每行「- <会话名>：<状态>」，只进汇总表、不写画像。
- 「上下文文档」一节只列路径、不注入：反引号括起的 .md 路径（仓库根相对；scripts/hooks/session_context.py，解析与路径判定与钩子共用）须为仓库根相对、
  不含 ..、规范化后仍在仓库根下且存在，不得列调度台账 COORDINATOR_LEDGER.md 与整份批次计划（*BATCH_PLAN*）。
- 「上下文文档」一节内一行 `交接源: <仓库根相对 .md 路径>#<标题片段>`：长期会话必须有，工作会话可选；文件须存在、标题含该片段的节须找得到
  （开工单 C 段、交接节点指令、挡停都从它读；它可以是调度台账等不入「上下文文档」列表的文件）。
- 每份画像生成的开工单（scripts/hooks/start_sheet.py）须在 8 KB 内。
- 独占与不碰靠调度派工、评审时人工核对，不做自动校验（SESSION_PLAYBOOK TEAM-04）。
不合规时生成失败，不写产物。
"""
import datetime, os, re, sys

BASE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(BASE))
SESSIONS = os.path.join(BASE, "sessions")
sys.path.insert(0, os.path.join(ROOT, "scripts", "hooks"))
from session_context import context_docs, handoff_source, unsafe_reason  # noqa: E402
import start_sheet  # noqa: E402
OUT = os.path.join(BASE, "SESSIONS.md")
FIELDS = ["类型", "职责", "独占", "不碰", "输入", "输出", "触发与节奏", "自动化", "上下文文档", "交接条件", "权限边界"]
KINDS = ("长期", "工作")
NOT_CONTEXT = re.compile(r"COORDINATOR_LEDGER\.md$|BATCH_PLAN")   # 开场不再注入，但列了就会被当成“要读的上下文”，整份台账与批次计划动辄几十 KB
HANDOFF_STATES = ("有效", "已接手")
HANDOFF_DIR = "handoffs"
OLD_HANDOFFS = ("phase1a", "architecture/proposals")
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


def check(profiles, retired, exists, has_heading=None):
    names = [p["name"] for p in profiles] + [n for n, _ in retired]
    dup = sorted({n for n in names if names.count(n) > 1})
    if dup:
        raise ProfileError(f"会话名重复：{'、'.join(dup)}")
    for p in profiles:
        own = f"docs/modernization/sessions/{p['name']}.md"
        docs = context_docs(f"## 上下文文档\n{p['上下文文档']}\n")
        source = handoff_source(f"## 上下文文档\n{p['上下文文档']}\n")
        if source is None and p["类型"] == "长期":
            raise ProfileError(f"sessions/{p['name']}.md：长期会话的「上下文文档」一节须有一行「交接源: <路径>#<标题片段>」")
        if source:
            reason = unsafe_reason(ROOT, source[0])
            if reason:
                raise ProfileError(f"sessions/{p['name']}.md：交接源 {source[0]} {reason}（只能写仓库根相对路径）")
            if not exists(source[0]):
                raise ProfileError(f"sessions/{p['name']}.md：交接源 {source[0]} 不存在")
            if has_heading and not has_heading(*source):
                raise ProfileError(f"sessions/{p['name']}.md：交接源 {source[0]} 里没有标题含「{source[1]}」的节")
        for path in docs:
            reason = unsafe_reason(ROOT, path)
            if reason:
                raise ProfileError(f"sessions/{p['name']}.md：上下文文档 {path} {reason}（只能写仓库根相对路径）")
            if not exists(path):
                raise ProfileError(f"sessions/{p['name']}.md：上下文文档 {path} 不存在（写仓库根相对路径）")
            if NOT_CONTEXT.search(path):
                raise ProfileError(f"sessions/{p['name']}.md：上下文文档不得列 {path}（调度台账与整份批次计划太大，需要时按任务卡里的链接读）")


def handoff_problems(base):
    """交接文件头（写于、基线提交、状态、触发原因）与“不写上下文百分比”。handoffs/ 下的新文件违规即错（含命名），旧位置的文件只告警。"""
    errors, warns = [], []
    places = [(HANDOFF_DIR, True)] + [(d, False) for d in OLD_HANDOFFS]
    for folder, strict in places:
        full = os.path.join(base, folder)
        if not os.path.isdir(full):
            continue
        for f in sorted(os.listdir(full)):
            if f.startswith(".") or not f.endswith(".md"):
                continue
            if not f.startswith("HANDOFF"):
                if strict:
                    errors.append(f"{folder}/{f}：文件名须为 HANDOFF_<原会话>_<YYYY-MM-DD>.md")
                continue
            found = []
            if strict and not re.fullmatch(r"HANDOFF_[a-z0-9-]+_\d{4}-\d{2}-\d{2}\.md", f):
                found.append("文件名须为 HANDOFF_<原会话>_<YYYY-MM-DD>.md")
            text = open(os.path.join(full, f), encoding="utf-8").read()
            head = text.split("\n## ")[0]   # 第一个二级标题之前
            for key, pattern in (("写于", r"(?m)^- 写于：(\d{4}-\d{2}-\d{2} \d{2}:\d{2})\s*$"), ("基线提交", r"(?m)^- 基线提交：[0-9a-f]{7,40}\s*$"),
                                 ("状态", rf"(?m)^- 状态：({'|'.join(HANDOFF_STATES)})\s*$"), ("触发原因", r"(?m)^- 触发原因：\S.*$")):
                m = re.search(pattern, head)
                if not m:
                    found.append(f"缺「{key}」或写法不合规")
                elif key == "写于":
                    try:
                        datetime.datetime.strptime(m.group(1), "%Y-%m-%d %H:%M")
                    except ValueError:
                        found.append(f"写于时刻无效：{m.group(1)}")
            if re.search(r"上下文.{0,6}\d+\s*[%％]", head):
                found.append("写了上下文百分比（只写触发原因）")
            if found:
                (errors if strict else warns).append(f"{folder}/{f}：{'；'.join(found)}")
    return errors, warns


def summary(text):
    first = next(l for l in text.splitlines() if l.strip())
    return re.sub(r"^[-*]\s+", "", first.strip())


def render(profiles, retired, now=None):
    head = ("# 会话一览\n\n<!-- 由 docs/modernization/build_sessions.py 从 sessions/*.md 生成，不入仓，不要手改；"
            "改画像后运行 python3 docs/modernization/build_all.py。 -->\n\n"
            "画像规则见 SESSION_PLAYBOOK TEAM-02、03；画像全文在 `sessions/<会话名>.md`。\n\n"
            "| 会话 | 类型 | 职责 | 上下文文档（仅列路径，不注入） |\n|---|---|---|---|\n")
    order = sorted(profiles, key=lambda p: (KINDS.index(p["类型"]), p["name"]))
    rows = []
    for p in order:
        docs = "、".join(f"`{path.removeprefix('docs/modernization/')}`" for path in context_docs(f"## 上下文文档\n{p['上下文文档']}\n")) or "—"
        rows.append(f"| {p['name']} | {p['类型']} | {one_line(summary(p['职责']), p['name'] + ' 职责首行')} | {docs} |\n")
    rows += [f"| {n} | 已结束 | {s} | — |\n" for n, s in retired]
    return head + "".join(rows)


def load():
    profiles = []
    for f in sorted(os.listdir(SESSIONS)):
        if f.endswith(".md") and not f.startswith("_"):
            profiles.append(parse_profile(open(os.path.join(SESSIONS, f), encoding="utf-8").read(), f))
    retired_path = os.path.join(SESSIONS, "_retired.md")
    retired = parse_retired(open(retired_path, encoding="utf-8").read()) if os.path.exists(retired_path) else []
    check(profiles, retired, lambda p: os.path.isfile(os.path.join(ROOT, p)),
          lambda path, fragment: start_sheet.find_handoff(open(os.path.join(ROOT, path), encoding="utf-8").read(), fragment) is not None)
    oversize = start_sheet.check_all(ROOT)
    if oversize:
        raise ProfileError("；".join(oversize))
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
    if p["类型"] != "工作" or p["权限边界"] != "内容":
        failures.append("合规画像解析结果不对")
    rejects("文件名不一致", lambda: parse_profile(good, "one-y.md"))
    rejects("非 one-* 名", lambda: parse_profile(good.replace("# one-x", "# psa-x"), "psa-x.md"))
    rejects("缺字段", lambda: parse_profile(good.replace("## 权限边界\n\n内容\n\n", ""), "one-x.md"))
    rejects("字段为空", lambda: parse_profile(good.replace("## 独占\n\n内容", "## 独占\n\n"), "one-x.md"))
    rejects("类型不合法", lambda: parse_profile(good.replace("## 类型\n\n工作", "## 类型\n\n临时"), "one-x.md"))
    rejects("画像仍有当前状态节", lambda: parse_profile(good + "## 当前状态\n\n在岗\n", "one-x.md"))
    rejects("顺序不对", lambda: parse_profile(good.replace("## 独占", "## TMP").replace("## 不碰", "## 独占").replace("## TMP", "## 不碰"), "one-x.md"))
    own = "docs/modernization/sessions/one-x.md"
    yes = lambda path: True
    p = dict(p, 上下文文档=f"`{own}`")
    check([p], [("one-old-1", "已交接")], yes)
    rejects("重名", lambda: check([p], [("one-x", "已交接")], yes))
    rejects("上下文文档不存在", lambda: check([p], [], lambda path: False))
    rejects("上下文文档为绝对路径", lambda: check([dict(p, 上下文文档="`/etc/x.md`")], [], yes))
    rejects("上下文文档含 ..", lambda: check([dict(p, 上下文文档="`docs/../../x.md`")], [], yes))
    check([dict(p, 类型="长期", 上下文文档=f"- `docs/a.md`\n- `{own}`\n- 交接源: docs/a.md#交接")], [], yes)
    rejects("上下文文档列了调度台账", lambda: check([dict(p, 上下文文档="`docs/modernization/COORDINATOR_LEDGER.md`")], [], yes))
    src = lambda line: f"`{own}`\n- 交接源: {line}"
    check([dict(p, 类型="长期", 上下文文档=src("docs/h.md#交接"))], [], yes, lambda path, fragment: True)
    rejects("长期会话没有交接源", lambda: check([dict(p, 类型="长期")], [], yes))
    check([dict(p, 类型="工作")], [], yes)
    rejects("交接源文件不存在", lambda: check([dict(p, 上下文文档=src("docs/h.md#交接"))], [], lambda path: path == own))
    rejects("交接源找不到标题", lambda: check([dict(p, 上下文文档=src("docs/h.md#交接"))], [], yes, lambda path, fragment: False))
    rejects("交接源是绝对路径", lambda: check([dict(p, 上下文文档=src("/etc/h.md#交接"))], [], yes, lambda path, fragment: True))
    rejects("上下文文档列了批次计划", lambda: check([dict(p, 上下文文档="`docs/modernization/phase1a/NEXT_BATCH_PLAN.md`")], [], yes))
    import tempfile
    with tempfile.TemporaryDirectory() as d:
        os.makedirs(os.path.join(d, HANDOFF_DIR)); os.makedirs(os.path.join(d, "phase1a"))
        ok_head = "# 交接报告：a → b\n\n- 写于：2026-10-16 12:00\n- 基线提交：abc1234\n- 状态：有效\n- 触发原因：批次边界\n"
        open(os.path.join(d, HANDOFF_DIR, "HANDOFF_a_2026-10-16.md"), "w", encoding="utf-8").write(ok_head)
        open(os.path.join(d, "phase1a", "HANDOFF_old.md"), "w", encoding="utf-8").write("# 交接报告\n\n上下文约 75%\n")
        errors, warns = handoff_problems(d)
        if errors or len(warns) != 1 or "百分比" not in warns[0]:
            failures.append(f"交接文件检查：旧位置应只告警一条：{errors} {warns}")
        open(os.path.join(d, HANDOFF_DIR, "HANDOFF_bad.md"), "w", encoding="utf-8").write(ok_head.replace("- 状态：有效", "- 状态：未知") + "上下文约 80%\n")
        errors, _ = handoff_problems(d)
        if len(errors) != 1 or "缺「状态」" not in errors[0] or "百分比" not in errors[0]:
            failures.append(f"交接文件检查：新目录违规应报错：{errors}")
        os.remove(os.path.join(d, HANDOFF_DIR, "HANDOFF_bad.md"))
        for name, text in (("HANDOFF_c_2026-10-16.md", ok_head.replace("基线提交：abc1234", "基线提交：abc1234zz")), ("HANDOFF_d_2026-10-16.md", ok_head.replace("状态：有效", "状态：有效中")),
                           ("HANDOFF_e_2026-10-16.md", ok_head.replace("2026-10-16 12:00", "2026-13-40 99:99")), ("HANDOFF_f_2026-10-16.md", ok_head.replace("- 基线提交：abc1234\n", "")),
                           ("HANDOFF_g_2026-10-16.md", ok_head + "\n上下文用了 75％\n"), ("HANDOFF_h.md", ok_head), ("notes.md", ok_head),
                           ("HANDOFF_i_2026-10-16.md", ok_head.replace("触发原因：批次边界", "触发原因：批次边界，上下文约 80%"))):
            open(os.path.join(d, HANDOFF_DIR, name), "w", encoding="utf-8").write(text)
        errors, _ = handoff_problems(d)
        flagged = sorted(e.split("：")[0].split("/")[1] for e in errors)
        if flagged != ["HANDOFF_c_2026-10-16.md", "HANDOFF_d_2026-10-16.md", "HANDOFF_e_2026-10-16.md", "HANDOFF_f_2026-10-16.md", "HANDOFF_g_2026-10-16.md", "HANDOFF_h.md", "HANDOFF_i_2026-10-16.md", "notes.md"]:
            failures.append(f"交接文件检查：违规文件应逐个报出：{flagged}")
        open(os.path.join(d, HANDOFF_DIR, "HANDOFF_g_2026-10-16.md"), "w", encoding="utf-8").write(ok_head + "\n## 1. 先读\n\n上下文用了 75%\n")   # 正文里提到百分比不算头部违规
        if any("HANDOFF_g" in e for e in handoff_problems(d)[0]):
            failures.append("交接文件检查：只查头部（第一个二级标题之前）的百分比")
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
    now = datetime.datetime.now()
    errors, warns = handoff_problems(BASE)
    if warns:
        print(f"警告：{len(warns)} 份旧位置的交接文件不合模板（templates/HANDOFF.md）：" + "；".join(w.split('：')[0] for w in warns), file=sys.stderr)
    if errors:
        sys.exit("交接文件校验失败：\n" + "\n".join(errors))
    open(OUT, "w", encoding="utf-8").write(render(profiles, retired, now))
    print(f"SESSIONS.md: {len(profiles)} 份画像，{len(retired)} 个已结束会话")


if __name__ == "__main__":
    self_test() if "--self-test" in sys.argv[1:] else main()
