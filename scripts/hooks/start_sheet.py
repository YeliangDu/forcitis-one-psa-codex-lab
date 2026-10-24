#!/usr/bin/env python3
"""The one-page start sheet (開工單, GOV-1022-COO-13) that SessionStart injects instead of whole context documents: at most SHEET_LIMIT bytes, so the host shows all of it.

A identity   the profile's 职责 / 独占 / 不碰 / 输入 / 输出 / 权限边界                       <= 1500 bytes
B tasks      cards in tasks/ whose 会话 is this session and 状态 starts with 已派 / 进行中 / 阻塞:
             number, status, path, and the card's 目标 and 完成标准 sections                    <= 2500 bytes
C handoff    the session's handover file state/<session>/handoff.md, written by the tools library's handoff.py (D17): injected whole up to policy.json
             start.handoff_max_kb (2 KB when missing), cut beyond it with a note. Missing, empty or a link: said out loud, then the fallback below.
             Fallback: the profile's `交接源: <path>#<heading fragment>`: the newest matching section (做到哪 / 下一步 / 别做) + path   <= 1000 bytes
D decisions  intent fragments of the last 7 days that name this session or its bc, one line each, at most 8   <= 1000 bytes
Missing things are said out loud (no card, incomplete card, no handoff source), never skipped; a part that fails says so and the others stay. Context documents are listed as paths only.
Usage: start_sheet.py <repo-root> [session] [--snapshot=<path>]  |  start_sheet.py --check <repo-root>  (every profile fits)  |  start_sheet.py --self-test
       start_sheet.py --hook   SessionStart hook (GOV-1023-FV2 O6, replaces handoff_snapshot.py --start): the repo root is the toplevel of the
                               payload's cwd on stdin; any failure, or HOOK_DEADLINE passing, prints one warning line instead of the sheet and exits 0.
"""
import datetime
import json
import math
import os
import re
import signal
import stat
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import session_context as sc  # noqa: E402

SHEET_LIMIT = 8192
A_LIMIT, B_LIMIT, C_LIMIT, D_LIMIT = 1500, 2500, 1000, 1000
GOAL_LIMIT = 520
INTENT_DAYS, INTENT_MAX = 7, 8
HOOK_DEADLINE = 10  # seconds; the SessionStart mount kills the hook at 15 and a killed hook prints nothing
OPEN_STATES = ("已派", "进行中", "阻塞")
A_SECTIONS = ("职责", "独占", "不碰", "输入", "输出", "权限边界")
TASKS = "docs/modernization/tasks"
# built from the sessions path: intent fragments are read here at session start only; the build-side check (check_all) never reads them, so no build depends on them
INTENTS = sc.SESSIONS.rsplit("/", 1)[0] + "/intent"
MARK = "…（全文见原文）"
STATE = "~/.modernize/psa/state"  # the tools library's handoff.py writes <STATE>/<session>/handoff.md (HANDOFF_STATE overrides, as there)
HANDOFF_KB = 2


def size(text):
    return len(text.encode())


def fit(text, room):
    """text cut to at most room UTF-8 bytes, with a mark when something was dropped."""
    if size(text) <= room:
        return text
    return text.encode()[:max(0, room - size(MARK))].decode(errors="ignore") + MARK


def squash(text):
    return " ".join(text.split())


def sections(text, level="##"):
    """{heading: body} of the sections at the given heading level (first of equal headings wins)."""
    found = {}
    for m in re.finditer(rf"(?ms)^{level} (.+?)\n(.*?)(?=^#{{1,{len(level)}}} |\Z)", text):
        found.setdefault(m.group(1).strip(), m.group(2).strip())
    return found


def front_matter(text):
    m = re.match(r"---\n(.*?)\n---\n", text, re.S)
    fields = {}
    for line in (m.group(1).splitlines() if m else []):
        key, sep, value = line.partition(":")
        if sep:
            fields[key.strip()] = value.strip()
    return fields


def git_lines(root, *args):
    result = subprocess.run(["git", "-C", root, *args], capture_output=True, text=True, timeout=3)
    return result.stdout.splitlines() if result.returncode == 0 else []


def read(root, path):
    """Worktree copy first (this session's own newest edits), else origin/main."""
    full = os.path.join(root, path)
    if os.path.isfile(full):
        with open(full, encoding="utf-8") as stream:
            return stream.read()
    return sc.from_main(root, path)


def split_budget(texts, room):
    """room divided over texts so that short ones keep all they need and the rest share what is left."""
    left, order, out = room, sorted(range(len(texts)), key=lambda i: size(texts[i])), {}
    for rank, i in enumerate(order):
        share = left // (len(order) - rank)
        out[i] = min(size(texts[i]), share)
        left -= out[i]
    return [out[i] for i in range(len(texts))]


def part_a(profile):
    body = sections(profile)
    heads = [name for name in A_SECTIONS if name in body]
    texts = [squash(body[name]) for name in heads]
    overhead = sum(size(f"【{name}】") + 1 for name in heads)
    budgets = split_budget(texts, max(0, A_LIMIT - overhead - 60))
    lines = [f"【{name}】" + fit(text, budget) for name, text, budget in zip(heads, texts, budgets)]
    missing = [name for name in A_SECTIONS if name not in body]
    if missing:
        lines.append("（画像缺：" + "、".join(missing) + "，报调度）")
    return "\n".join(lines)


def open_cards(root, name):
    """[(path, front matter, text)] of the open cards of this session; origin/main and the worktree both count, the worktree copy wins."""
    paths = {}
    for hit in git_lines(root, "grep", "-l", "-E", f"^会话: {re.escape(name)}$", "origin/main", "--", TASKS):
        paths[hit.split(":", 1)[1]] = None
    directory = os.path.join(root, TASKS)
    if os.path.isdir(directory):
        for entry in os.listdir(directory):
            if entry.endswith(".md") and entry != "TASKS.md":
                paths.setdefault(f"{TASKS}/{entry}", None)
    cards = []
    for path in sorted(paths):
        text = read(root, path)
        if not text:
            continue
        fields = front_matter(text)
        if fields.get("会话") == name and fields.get("状态", "").startswith(OPEN_STATES):
            cards.append((path, fields, text))
    return cards


def card_goal(text):
    """(goal, criteria, missing names) of a card; a missing section is named, not silently dropped."""
    body = {**sections(text, "###"), **sections(text, "##")}
    goal, criteria = squash(body.get("目标", "")), squash(body.get("完成标准", ""))
    return goal, criteria, [n for n, v in (("目标", goal), ("完成标准", criteria)) if not v]


def part_b(cards, limit=B_LIMIT, with_paths=True):
    if not cards:
        return "无在途任务，向调度要卡。"
    shown = cards[:5]
    room = (limit - 60) // len(shown)
    lines = []
    for path, fields, text in shown:
        goal, criteria, missing = card_goal(text)
        head = f"- {fields.get('任务号', os.path.basename(path)[:-3])} 状态：{fields.get('状态', '?')}" + (f" 卡：{path}" if with_paths else "")
        if missing:
            lines.append(head + f"\n  卡不完整（缺{'、'.join(missing)}），报调度。" + (f"目标：{fit(goal, 200)}" if goal else ""))
        else:
            part = max(80, (room - size(head) - 30) // 2)
            lines.append(head + f"\n  目标：{fit(goal, part)}\n  完成标准：{fit(criteria, part)}")
    if len(cards) > len(shown):
        lines.append(f"（另有 {len(cards) - len(shown)} 张在途卡，见 {TASKS}/）")
    return "\n".join(lines)


def goals_for_node(root, name):
    """Card numbers and titles of the open cards (one line each, at most 8) for the handover-node instruction; the goals stay in the cards (GOV-1023-QUA-06)."""
    cards = open_cards(root, name)
    if not cards:
        return ""
    lines = [f"- {fields.get('任务号', os.path.basename(path)[:-3])} {fields.get('任务名', '')}".rstrip() for path, fields, _ in cards[:8]]
    if len(cards) > 8:
        lines.append(f"（另有 {len(cards) - 8} 张在途卡）")
    return "在途任务卡（交接要写明做到哪，目标见卡）：\n" + "\n".join(lines)


def card_date(root, path):
    lines = git_lines(root, "log", "-1", "--format=%cs", "origin/main", "--", path)
    return lines[0] if lines else ""


def find_handoff(text, fragment):
    """(line, heading, level, date, body) of the LAST heading of text that contains fragment (the newest section of an append-style document), or None.
    The body runs to the next heading of the same or a higher level; the date is one that stands within the first 20 characters of the heading."""
    heads = [(m.start(), len(m.group(1)), m.group(2).strip()) for m in re.finditer(r"(?m)^(#{1,4}) (.+)$", text)]
    hit = None
    for i, (start, level, title) in enumerate(heads):
        if fragment in title:
            end = next((s for s, lv, _ in heads[i + 1:] if lv <= level), len(text))
            body = text[text.index("\n", start) + 1:end] if "\n" in text[start:end] else ""
            date = (re.search(r"\d{4}-\d{2}-\d{2}", title[:20]) or [""])[0]
            hit = (text[:start].count("\n") + 1, title, level, date, body)
    return hit


def handoff_limit(root):
    """(bytes, problem): policy.json start.handoff_max_kb in bytes (the limit handoff.py writes under). The value is checked the way budget_check's check_limit checks
    every limit (a finite positive number; NaN, Infinity, a huge integer, zero, a negative or a non-number fail) and must leave the handoff inside the sheet
    (at most SHEET_LIMIT bytes); a missing file or key, or a value that fails, gives HANDOFF_KB and says why, so the session start never breaks on it."""
    try:
        value = json.loads(open(os.path.join(root, "policy.json"), encoding="utf-8").read())["start"]["handoff_max_kb"]
    except (OSError, ValueError, KeyError, TypeError):
        return HANDOFF_KB * 1024, ""
    try:
        ok = not isinstance(value, bool) and isinstance(value, (int, float)) and math.isfinite(value) and 0 < value * 1024 <= SHEET_LIMIT
    except OverflowError:
        ok = False
    if not ok:
        return HANDOFF_KB * 1024, f"policy.json start.handoff_max_kb {str(value)[:20]!r} 须是有限正数且不超过 {SHEET_LIMIT // 1024} KB，按 {HANDOFF_KB} KB"
    return int(value * 1024), ""


def open_handoff(path, shown, limit):
    """(at most limit + 1 bytes of the file, its size, refusal or ""): opened without following a link (O_NOFOLLOW), then fstat says it is a regular file
    with one name (a hard link could bring in a file from outside the state directory, a key for instance: bottom line 3) and owned by this user."""
    try:
        fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | getattr(os, "O_NONBLOCK", 0))
    except FileNotFoundError:
        return None, 0, f"没有 {shown}"
    except OSError as error:
        return None, 0, f"{shown} 打不开或是符号链接（{error.__class__.__name__}），不读"
    try:
        info = os.fstat(fd)
        if not stat.S_ISREG(info.st_mode):
            return None, 0, f"{shown} 不是普通文件，不读"
        if info.st_nlink != 1:
            return None, 0, f"{shown} 有 {info.st_nlink} 个硬链接（可能指向 state 外的文件），不读"
        if info.st_uid != os.getuid():
            return None, 0, f"{shown} 的属主不是本人，不读"
        data = b""
        while len(data) <= limit:
            chunk = os.read(fd, limit + 1 - len(data))
            if not chunk:
                break
            data += chunk
        return data, info.st_size, ""
    finally:
        os.close(fd)


def state_handoff(root, name):
    """(text to inject, or None, and what was found) for <state>/<name>/handoff.md: whole up to the limit, cut beyond it with a note."""
    folder = os.environ.get("HANDOFF_STATE") or os.path.expanduser(STATE)
    path = os.path.join(folder, name, "handoff.md")
    shown = path.replace(os.path.expanduser("~"), "~", 1)
    if os.path.islink(path) or os.path.islink(os.path.dirname(path)):
        return None, f"{shown} 是符号链接，不读"
    limit, problem = handoff_limit(root)
    data, total, refused = open_handoff(path, shown, limit)
    if refused:
        return None, refused
    text = data[:limit].decode("utf-8", errors="ignore")   # only limit + 1 bytes were read: a huge file costs no more than a short one
    if not text.strip():
        return None, f"{shown} 是空的"
    head = f"{shown}（handoff.py 写的交接，全文注入）"
    if len(data) > limit:
        head = f"{shown}（{total} 字节，超过 {limit} 字节上限，只注入开头；读原文，并用 handoff.py 按上限重写）"
    head += f"（{problem}）" if problem else ""
    body = data[:max(0, limit - size(MARK))].decode("utf-8", errors="ignore") + MARK if len(data) > limit else text.rstrip("\n")
    return head + "\n" + body, ""


def part_c(root, source, cards, snapshot, name=None):
    """The session's state handoff file when there is one; else the handoff source (path, heading fragment) of the profile. Anything missing is said, never skipped."""
    if name:
        found, why = state_handoff(root, name)
        if found:
            return found
        return f"交接文件：{why}，改读画像的交接源。\n" + fit(part_c(root, source, cards, snapshot), C_LIMIT)
    shot = f"；最新交接快照（机械记录，未内联）：{snapshot}" if snapshot else "；也没有交接快照"
    if source is None:
        return "画像没有交接源，向调度要交接位置" + shot
    path, fragment = source
    text = read(root, path)
    if not text:
        return f"交接源 {path} 读不到，报调度" + shot
    found = find_handoff(text, fragment)
    if found is None:
        return f"交接源 {path} 里没有标题含「{fragment}」的节，报调度" + shot
    line, title, level, date, body = found
    picks = []
    for keys, label in ((("现状", "做到哪", "在途"), "做到哪"), (("下一步",), "下一步"), (("坑", "别做", "不要做"), "别做")):
        for heading, content in sections(body, "#" * (level + 1)).items():
            if any(k in heading for k in keys):
                items = [squash(row).lstrip("-*0123456789. ") for row in content.splitlines() if row.strip()]
                picks.append(f"【{label}】" + fit(" / ".join(items[:3]), 220))
                break
    older = ""
    newest = max((card_date(root, p) for p, _, _ in cards), default="")
    if date and newest and date < newest:
        older = f"（早于任务卡 {newest} 的更新，以任务卡为准）"
    head = f"{path}:{line}「{title}」{older}"
    if not picks:
        rows = [squash(row) for row in body.splitlines() if row.strip()]
        picks = ["【原文开头】" + fit(" / ".join(rows[:6]), 600)] if rows else ["（该节是空的，读原文）"]
    return fit(head + "\n" + "\n".join(picks), C_LIMIT)


def part_d(root, name, bcs, today):
    names = set(git_lines(root, "ls-tree", "--name-only", "origin/main", f"{INTENTS}/"))
    directory = os.path.join(root, INTENTS)
    if os.path.isdir(directory):
        names |= {f"{INTENTS}/{entry}" for entry in os.listdir(directory)}
    oldest = (today - datetime.timedelta(days=INTENT_DAYS)).isoformat()
    recent = {p for p in names if p.endswith(".md") and os.path.basename(p)[:10] >= oldest}
    terms = [name, *bcs]
    related = {p for p in recent if f"-{name}-" in os.path.basename(p)}
    for hit in git_lines(root, "grep", "-l", "-F", *[a for t in terms for a in ("-e", t)], "origin/main", "--", INTENTS):
        related.add(hit.split(":", 1)[1])
    related &= recent
    rows = sorted(related, key=lambda p: os.path.basename(p), reverse=True)[:INTENT_MAX]
    if not rows:
        return f"近 {INTENT_DAYS} 天没有与 {name} 或本 bc 相关的决定。"
    return fit("\n".join("- " + os.path.basename(p)[:-3] for p in rows), D_LIMIT)


def guarded(part, make, fallback=None):
    """make() for one part of the sheet; when it fails the part says so (stderr too) and the other parts stay."""
    try:
        return make()
    except (OSError, subprocess.SubprocessError, ValueError, KeyError, IndexError, TypeError) as error:
        print(f"start_sheet: {part} 段生成失败（{type(error).__name__}: {error}）", file=sys.stderr)
        return fallback if fallback is not None else f"（本段生成失败：{type(error).__name__}，读原文）"


def build(root, name=None, today=None, snapshot=None, intents=True):
    """The start sheet text for the session `name` (identified from the checkout when None)."""
    today = today or datetime.date.today()
    if name is None:
        name, retired, marker, branch = sc.identify(root)
        if not name or name in retired:
            return f"【开工单】未识别本会话（目录 {os.path.basename(root)}、分支 {branch}）：手动读 {sc.SESSIONS}/<会话名>.md，并向调度要卡。"
    profile_path = f"{sc.SESSIONS}/{name}.md"
    profile = sc.from_main(root, profile_path) or sc.from_tree(root, profile_path)
    if not profile:
        return f"【开工单】告警：{profile_path} 读不到，没有画像；向调度确认身份与任务。"
    docs = [d for d in sc.context_docs(profile) if d != profile_path and not sc.unsafe_reason(root, d)]
    source = sc.handoff_source(profile)
    if source and sc.unsafe_reason(root, source[0]):
        source = None
    cards = guarded("B", lambda: open_cards(root, name), f"（本段生成失败，读 {TASKS}/ 里本会话的在途卡）")
    cards_failed = cards if isinstance(cards, str) else None
    cards = [] if cards_failed else cards
    bcs = sorted({f["bc"] for _, f, _ in cards if f.get("bc") and f["bc"] != "跨域"})
    out = [f"【开工单】会话 {name}｜画像 {profile_path}（长输出宿主只显示开头，本页在 {SHEET_LIMIT // 1024} KB 内）",
           "\n## A 身份", guarded("A", lambda: part_a(profile)),
           "\n## B 当前任务", cards_failed or guarded("B", lambda: part_b(cards)),
           "\n## C 交接", guarded("C", lambda: part_c(root, source, cards, snapshot, name)) if intents else "x" * (handoff_limit(root)[0] + 200),
           f"\n## D 近 {INTENT_DAYS} 天相关决定（intent/）", guarded("D", lambda: part_d(root, name, bcs, today)) if intents else "x" * D_LIMIT,
           "\n## 按需读（不注入）：" + ("、".join(docs) if docs else "无上下文文档") + "；规则细则 CLAUDE.md、docs/modernization/RULES.md"]
    text = "\n".join(out)
    return fit(text, SHEET_LIMIT) if size(text) > SHEET_LIMIT else text


def profile_names(root):
    names = set(git_lines(root, "ls-tree", "--name-only", "origin/main", f"{sc.SESSIONS}/"))
    directory = os.path.join(root, sc.SESSIONS)
    if os.path.isdir(directory):
        names |= {f"{sc.SESSIONS}/{e}" for e in os.listdir(directory)}
    return sorted(os.path.basename(p)[:-3] for p in names if p.endswith(".md") and not os.path.basename(p).startswith("_"))


def check_all(root):
    """Problems (empty when every profile's sheet fits without being cut)."""
    problems = []
    for name in profile_names(root):
        text = build(root, name, intents=False)   # D stands in at its full size
        if size(text) >= SHEET_LIMIT or MARK in text.splitlines()[-1]:
            problems.append(f"{name}：开工单 {size(text)} 字节，超过 {SHEET_LIMIT}（把画像的职责等节写短，或任务卡的目标与完成标准写短）")
    return problems


def self_test():
    global SHEET_LIMIT
    assert fit("短", 100) == "短" and size(fit("字" * 500, 100)) <= 100 and fit("字" * 500, 100).endswith(MARK)
    assert split_budget(["a" * 10, "b" * 1000, "c" * 1000], 300) == [10, 145, 145]
    assert sections("## 目标\nx\n## 完成标准\ny\n") == {"目标": "x", "完成标准": "y"}
    assert sections("### 目标\nx\n### 下一步\ny\n## 别的\nz", "###") == {"目标": "x", "下一步": "y"}
    assert front_matter("---\n任务号: T-1\n会话: one-x\n---\n# 标题") == {"任务号": "T-1", "会话": "one-x"}
    real_state = os.environ.get("HANDOFF_STATE")
    with tempfile.TemporaryDirectory() as tmp:
        os.environ["HANDOFF_STATE"] = state = os.path.join(tmp, "state")  # never the real ~/.modernize/psa/state
        root = os.path.join(tmp, "one-x-r1")
        os.makedirs(os.path.join(root, sc.SESSIONS))
        os.makedirs(os.path.join(root, TASKS))
        os.makedirs(os.path.join(root, INTENTS))
        today = datetime.date(2026, 10, 22)

        def write(path, text):
            with open(os.path.join(root, path), "w", encoding="utf-8") as stream:
                stream.write(text)

        sections_text = "".join(f"## {name}\n\n{name}的内容" + "很长" * 400 + "\n\n" for name in A_SECTIONS)
        write(f"{sc.SESSIONS}/one-x.md", "# one-x\n\n## 类型\n\n长期\n\n" + sections_text + "## 上下文文档\n\n- `docs/ctx.md`\n- 交接源: docs/ctx.md#交接\n")
        write("docs/ctx.md", "# 上下文\n\n## 交接（2026-10-20 晚）\n\n### 现状\n- 旧现状\n\n### 下一步\n1. 旧下一步\n\n## 交接（2026-10-21 晚）\n\n### 现状\n- 做到 X\n- 做到 Y\n\n### 下一步\n1. 先 A\n2. 再 B\n\n### 坑\n- 别碰 Z\n")
        # 1. no card, handoff found, nothing relevant in intent
        text = build(root, "one-x", today)
        assert "无在途任务，向调度要卡" in text and "做到 X / 做到 Y" in text and "先 A / 再 B" in text and "别碰 Z" in text and "旧下一步" not in text, text
        assert "docs/ctx.md:" in text and "近 7 天没有" in text and "docs/ctx.md" in text.split("## 按需读")[1]
        assert size(text) <= SHEET_LIMIT and size(part_a(open(os.path.join(root, f"{sc.SESSIONS}/one-x.md")).read())) <= A_LIMIT
        # 2. cards: complete, incomplete, closed, someone else's
        write(f"{TASKS}/T-1.md", "---\n任务号: T-1\n会话: one-x\n状态: 进行中\nbc: 行政\n---\n# T-1\n\n## 目标\n\n做 A。\n\n## 完成标准\n\n测试 AT-1 过。\n")
        write(f"{TASKS}/T-2.md", "---\n任务号: T-2\n会话: one-x\n状态: 阻塞（等 ADR）\nbc: 跨域\n---\n# T-2\n\n## 目标\n\n只有目标。\n")
        write(f"{TASKS}/T-3.md", "---\n任务号: T-3\n会话: one-x\n状态: 已合并\n---\n## 目标\n\n不该出现\n")
        write(f"{TASKS}/T-4.md", "---\n任务号: T-4\n会话: one-y\n状态: 进行中\n---\n## 目标\n\n别人的\n")
        text = build(root, "one-x", today)
        assert "T-1 状态：进行中" in text and "目标：做 A。" in text and "完成标准：测试 AT-1 过。" in text
        assert "T-2" in text and "卡不完整（缺完成标准），报调度" in text and "不该出现" not in text and "别人的" not in text
        assert "无在途任务" not in text
        assert "钩子告警" not in text
        subprocess.run(["git", "init", "-q", root], check=True, capture_output=True)
        node = goals_for_node(root, "one-x")
        assert "T-1" in node and "做 A。" not in node and "tasks/" not in node and size(node) <= 300, node
        assert goals_for_node(root, "one-nobody") == ""
        # 3. intents: this session by name, by bc keyword, too old, unrelated; at most 8 lines
        write(f"{INTENTS}/2026-10-21-100000-one-x-新决定.md", "x")
        write(f"{INTENTS}/2026-10-20-100000-one-coordinator-行政.md", "## 落点\n\n行政 bc 的卡\n")
        write(f"{INTENTS}/2026-10-01-100000-one-x-太旧.md", "x")
        write(f"{INTENTS}/2026-10-21-110000-one-y-无关.md", "无关内容")
        text = build(root, "one-x", today)
        assert "新决定" in text and "太旧" not in text and "无关" not in text
        for i in range(12):
            write(f"{INTENTS}/2026-10-22-0{i:02}000-one-x-多{i}.md", "x")
        assert build(root, "one-x", today).split("## D")[1].split("## 按需读")[0].count("\n- ") == INTENT_MAX
        # 4. no handoff, unknown session, missing profile
        os.remove(os.path.join(root, "docs/ctx.md"))
        gone = build(root, "one-x", today)
        assert "交接源 docs/ctx.md 读不到" in gone and "也没有交接快照" in gone and "/h/s.md" not in gone, gone
        gone = build(root, "one-x", today, snapshot="/h/s.md")
        assert "交接源 docs/ctx.md 读不到" in gone and "最新交接快照（机械记录，未内联）：/h/s.md" in gone and "也没有交接快照" not in gone, gone
        write("docs/ctx.md", "# 上下文\n\n## 交接（2026-10-21 晚）\n\n### 下一步\n1. 先 A\n")
        withdoc = build(root, "one-x", today, snapshot="/h/s.md")
        assert "先 A" in withdoc and "/h/s.md" not in withdoc and "也没有交接快照" not in withdoc, "a found handoff does not claim there is none"
        write("docs/ctx.md", "# 上下文\n\n## 别的\n")
        assert "没有标题含「交接」的节" in build(root, "one-x", today)
        # other real layouts: a level-1 template with ## parts, an undated "现状" section without parts, the newest of several dated sections
        write("docs/ctx.md", "# 交接报告：one-x\n\n## 1. 先读\n- 先读 R\n\n## 2. 在途（PR、分支）\n- 做到 Q\n\n## 4. 下一步命令\n1. 跑 T\n\n## 5. 不要做的事\n- 别碰 Z\n")
        level1 = build(root, "one-x", today)
        assert "做到 Q" in level1 and "跑 T" in level1 and "别碰 Z" in level1 and "先读 R" not in level1, level1
        write(f"{sc.SESSIONS}/one-x.md", open(os.path.join(root, f"{sc.SESSIONS}/one-x.md"), encoding="utf-8").read().replace("#交接", "#现状"))
        write("docs/ctx.md", "# 速查\n\n## 现状（2026-10-22 16:55，清空前）\n\n- 在做 U\n- 等 V\n\n## 别的\n\nW\n")
        flat = build(root, "one-x", today)
        assert "【原文开头】" in flat and "在做 U" in flat and "等 V" in flat and "W" not in flat.split("## D")[0].split("## C")[1], flat
        write("docs/ctx.md", "# 速查\n\n## 2026-10-21 交接（旧）\n\n### 下一步\n1. 旧\n\n## 2026-10-22 交接（新）\n\n### 下一步\n1. 新\n")
        write(f"{sc.SESSIONS}/one-x.md", open(os.path.join(root, f"{sc.SESSIONS}/one-x.md"), encoding="utf-8").read().replace("#现状", "#交接"))
        assert "新" in build(root, "one-x", today) and "旧" not in build(root, "one-x", today).split("## C")[1].split("## D")[0]
        write(f"{sc.SESSIONS}/one-x.md", open(os.path.join(root, f"{sc.SESSIONS}/one-x.md"), encoding="utf-8").read().replace("- 交接源: docs/ctx.md#交接\n", ""))
        assert "画像没有交接源" in build(root, "one-x", today)
        assert "告警" in build(root, "one-nobody", today)
        # an unsafe handover source is not read
        write(f"{sc.SESSIONS}/one-x.md", open(os.path.join(root, f"{sc.SESSIONS}/one-x.md"), encoding="utf-8").read().replace("## 上下文文档\n", "## 上下文文档\n\n- 交接源: /etc/hosts.md#交接\n"))
        assert "画像没有交接源" in build(root, "one-x", today)
        # a failing card read is said, never reported as "no card"
        real_open_cards = open_cards
        globals()["open_cards"] = lambda *a, **k: (_ for _ in ()).throw(OSError("boom"))
        try:
            nocards = build(root, "one-x", today)
        finally:
            globals()["open_cards"] = real_open_cards
        assert "## B 当前任务\n（本段生成失败" in nocards and "无在途任务" not in nocards and "## C 交接" in nocards, nocards
        # a failing part says so and the others stay
        real_part_c = part_c
        part_c_error = lambda *a, **k: (_ for _ in ()).throw(OSError("boom"))
        globals()["part_c"] = part_c_error
        try:
            broken = build(root, "one-x", today)
        finally:
            globals()["part_c"] = real_part_c
        assert "## C 交接\n（本段生成失败：OSError" in broken and "## A 身份" in broken and "## D" in broken, broken
        # 4b. the state handoff file (GOV-1023-FV2 O10, D17): a 2 KB file goes in whole; missing, empty or a link falls back to the profile's source; too long is cut, with a note
        handoff = os.path.join(state, "one-x", "handoff.md")
        missing = build(root, "one-x", today)
        assert "交接文件：没有 " + handoff in missing and "改读画像的交接源。\n画像没有交接源" in missing, "a missing file is said, then the old source is read as before"
        os.makedirs(os.path.dirname(handoff))
        open(handoff, "w", encoding="utf-8").write(" \n")
        assert "是空的，改读画像的交接源" in build(root, "one-x", today)
        head, tail = "# 交接\n## 做到哪\n- 做到 Q\n## 下一步\n", "\n- 末行 END\n"
        whole = head + "甲" * ((2048 - size(head + tail)) // 3) + "a" * ((2048 - size(head + tail)) % 3) + tail
        assert size(whole) == 2048 and handoff_limit(root) == (2048, "")
        open(handoff, "w", encoding="utf-8").write(whole)
        sheet = build(root, "one-x", today)
        part = sheet.split("\n## C 交接\n")[1].split("\n## D ")[0]
        assert whole.rstrip("\n") in part and "全文注入" in part and MARK not in part and "改读画像的交接源" not in part and size(sheet) <= SHEET_LIMIT, part[-300:]
        open(handoff, "w", encoding="utf-8").write(whole + "超出的一行 TAIL\n")
        cut = build(root, "one-x", today)
        part = cut.split("\n## C 交接\n")[1].split("\n## D ")[0]
        assert "超过 2048 字节上限，只注入开头" in part and part.rstrip().endswith(MARK) and "TAIL" not in part and "- 做到 Q" in part and size(cut) <= SHEET_LIMIT, part[-300:]
        write("policy.json", json.dumps({"start": {"handoff_max_kb": 1}}))
        assert handoff_limit(root) == (1024, "") and "超过 1024 字节上限" in build(root, "one-x", today), "the limit is policy.json start.handoff_max_kb"
        os.remove(os.path.join(root, "policy.json"))
        os.rename(handoff, handoff + ".real")
        os.symlink(handoff + ".real", handoff)
        assert "是符号链接，不读" in build(root, "one-x", today) and "END" not in build(root, "one-x", today)
        os.remove(handoff)
        # 4c. a hard link brings in a file from outside the state directory (a fictitious key here): refused, as are another owner's file and a non-regular one (84be6ded7 final, 1)
        secret = os.path.join(tmp, "outside", "fake_key.txt")
        os.makedirs(os.path.dirname(secret))
        open(secret, "w", encoding="utf-8").write("FAKE-KEY-0000 not a real secret\n")
        os.link(secret, handoff)
        linked = build(root, "one-x", today)
        assert "有 2 个硬链接" in linked and "FAKE-KEY" not in linked and "改读画像的交接源" in linked, "a hard-linked file is not read"
        os.remove(handoff)
        os.symlink(secret, handoff)  # a link that appears between the islink check and the open: O_NOFOLLOW refuses it there too
        assert open_handoff(handoff, "x", 2048)[0] is None and "符号链接" in open_handoff(handoff, "x", 2048)[2]
        os.remove(handoff)
        os.mkfifo(handoff)
        assert "不是普通文件，不读" in build(root, "one-x", today), "a FIFO is refused, without blocking"
        os.remove(handoff)
        open(handoff, "w", encoding="utf-8").write(whole)
        real_uid = os.getuid
        os.getuid = lambda: real_uid() + 1
        try:
            assert "属主不是本人，不读" in build(root, "one-x", today) and "END" not in build(root, "one-x", today)
        finally:
            os.getuid = real_uid
        # 4d. handoff_max_kb is checked as budget_check's check_limit checks a limit, and must fit in the sheet: a bad value gives 2 KB and says so (84be6ded7 final, 4)
        for bad in ("Infinity", "NaN", "1e400", "1e9", "0", "-1", '"2"', "true", "10"):
            write("policy.json", '{"start": {"handoff_max_kb": %s}}' % bad)
            limit, problem = handoff_limit(root)
            assert limit == 2048 and "须是有限正数" in problem, (bad, limit, problem)
            assert "须是有限正数" in build(root, "one-x", today) and check_all(root) == [], bad
        os.remove(os.path.join(root, "policy.json"))
        # 4e. a huge file: only limit + 1 bytes are read (84be6ded7 final, 5)
        open(handoff, "w", encoding="utf-8").write(whole + "乙" * (20 * 1024 * 1024))
        real_read, taken = os.read, []
        os.read = lambda fd, n: taken.append(len(real_read(fd, 0)) + n) or real_read(fd, n)
        try:
            data, total, refused = open_handoff(handoff, "x", 2048)
        finally:
            os.read = real_read
        assert len(data) == 2049 and sum(taken) <= 2049 and total == size(whole) + 3 * 20 * 1024 * 1024 and not refused, (len(data), sum(taken), total)
        huge = build(root, "one-x", today)
        assert f"{total} 字节，超过 2048 字节上限" in huge and huge.split("\n## C 交接\n")[1].split("\n## D ")[0].rstrip().endswith(MARK), huge[-300:]
        open(handoff, "w", encoding="utf-8").write(whole)
        # 5. the hard limit: huge sections and many long cards still fit, with a mark
        for i in range(7):
            write(f"{TASKS}/B-{i}.md", f"---\n任务号: B-{i}\n会话: one-x\n状态: 进行中\n---\n## 目标\n\n{'目标' * 900}\n\n## 完成标准\n\n{'标准' * 900}\n")
        text = build(root, "one-x", today)
        assert size(text) <= SHEET_LIMIT and "另有" in text, size(text)
        assert check_all(root) == [], "a sheet that fits passes the check"
        real, SHEET_LIMIT = SHEET_LIMIT, 1200
        try:
            assert check_all(root) and "one-x" in check_all(root)[0], "a sheet over the limit fails the check"
        finally:
            SHEET_LIMIT = real
        hook = lambda stdin: subprocess.run([sys.executable, "-I", os.path.abspath(__file__), "--hook"], input=stdin, capture_output=True, text=True, timeout=60)
        r = hook(json.dumps({"cwd": os.path.join(root, "docs")}))
        assert r.returncode == 0 and r.stdout.startswith("【开工单】") and "告警" not in r.stdout.splitlines()[0], ("--hook builds the sheet of the payload cwd's repo", r.stdout[:200], r.stderr)
        assert whole.rstrip("\n") in r.stdout and "- 末行 END" in r.stdout, "--hook injects the 2 KB handoff file whole, next to seven long cards"
        for bad in ("{bad", json.dumps({"cwd": tmp})):
            r = hook(bad)
            assert r.returncode == 0 and r.stdout.startswith("【开工单】告警"), ("--hook never fails the session start", bad, r.stdout, r.stderr)
    if real_state is None:
        os.environ.pop("HANDOFF_STATE", None)
    else:
        os.environ["HANDOFF_STATE"] = real_state
    print("start_sheet self-test: ok")


if __name__ == "__main__":
    args = sys.argv[1:]
    if args == ["--self-test"]:
        self_test()
    elif args == ["--hook"]:
        def overdue(*_):
            raise TimeoutError

        signal.signal(signal.SIGALRM, overdue)
        signal.alarm(HOOK_DEADLINE)
        try:
            cwd = json.load(sys.stdin).get("cwd") or os.getcwd()
            root = subprocess.run(["git", "-C", cwd, "rev-parse", "--show-toplevel"], capture_output=True, text=True, timeout=5, check=True).stdout.strip()
            print(build(root))
        except Exception as error:
            print(f"【开工单】告警：start_sheet.py 出错（{error.__class__.__name__}），没有开工单；手动读 docs/modernization/sessions/<会话名>.md 并向调度要卡。")
    elif len(args) == 2 and args[0] == "--check":
        found = check_all(args[1])
        print("\n".join(found) if found else f"start_sheet: {len(profile_names(args[1]))} 份画像的开工单都在 {SHEET_LIMIT} 字节内")
        sys.exit(1 if found else 0)
    elif args and not args[0].startswith("-"):
        shots = [a[len("--snapshot="):] for a in args if a.startswith("--snapshot=")]
        rest = [a for a in args if not a.startswith("--snapshot=")]
        if len(rest) > 2:
            sys.exit(__doc__)
        print(build(rest[0], rest[1] if len(rest) == 2 else None, snapshot=shots[0] if shots else None))
    else:
        sys.exit(__doc__)
