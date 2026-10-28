#!/usr/bin/env python3
"""Fill {{BEHAVIOR_CONTRACT}} in MODERNIZATION_BRIEF.template.md from the P0 rules in BUSINESS_RULES.md and
rules/IDENTITY_ORG_RULES.md (RULE-316..376) plus RULE_REVIEWS.json.

Run from the repo's docs/modernization (or pass the directory holding BUSINESS_RULES.md as argv[1]; analysis/psa is still accepted)."""
import re, collections, os, sys
sys.dont_write_bytecode = True
from review_revisions import current, load_reviews, structure_problems
BASE = sys.argv[1] if len(sys.argv) > 1 else ("analysis/psa" if os.path.isdir("analysis/psa") else ".")
rules = open(os.path.join(BASE, "BUSINESS_RULES.md"), encoding="utf-8").read()
_rv = os.path.join(BASE, "RULE_REVIEWS.json")
try:
    raw_reviews = load_reviews(BASE) if os.path.exists(_rv) else {}
except ValueError as e:
    sys.exit(str(e))
_problems = structure_problems(raw_reviews, BASE)
if _problems:
    sys.exit("RULE_REVIEWS.json 不合规：\n" + "\n".join(_problems))
reviews = {rid: current(entry) for rid, entry in raw_reviews.items()}
IDORG = "身份与组织（platform.identity、organization.workforce）"
PHASE = {"售前与报价": "Phase 1b", "审批（platform.approval）": "Phase 1a", "交付、预算与收入": "Phase 2（收入确认部分在 Phase 4）",
         "工时与资源": "Phase 2", "项目成本与核算": "Phase 4", IDORG: "Phase 1a"}
rows = re.findall(r"^\| (RULE-\d+) \| ([^|]+) \| ([^|]+) \| (\w+) \| (P\d) \| `([^`]+)` \| (\w+) \|$", rules, re.M)
body = {m.group(1): m.group(2) for m in re.finditer(r"### (RULE-\d+): .+\n((?:(?!### RULE-).*\n)*)", rules)}
settled = [r for r in rows if r[4] == "P0" and r[6] == "High" and "Suspected defect" not in body[r[0]]]
# identity/org rules live in their own file: the extractor's Review flag decides settled vs blocked
_io = os.path.join(BASE, "rules", "IDENTITY_ORG_RULES.md")
io_src = open(_io, encoding="utf-8").read() if os.path.exists(_io) else ""
io_body = {m.group(1): m.group(2) for m in re.finditer(r"### (RULE-\d+): .+\n((?:(?!### RULE-).*\n)*)", io_src)}
for rid, title, prio, review in re.findall(r"^\| (RULE-\d+) \| (?:identity|org) \| (.+?) \| (P\d) \| (Yes|No) \|$", io_src, re.M):
    b = io_body[rid]
    src = re.search(r"\*\*Source:\*\* `[^`]*?([^/`]+)`", b).group(1)
    r = (rid, IDORG, " " + title, re.search(r"\*\*Category:\*\* (\w+)", b).group(1), prio, src,
         re.search(r"\*\*Confidence:\*\* (\w+)", b).group(1))
    rows.append(r); body[rid] = b
    if prio == "P0" and review == "No":
        settled.append(r)
p0 = [r for r in rows if r[4] == "P0"]
blocked = [r for r in p0 if r not in settled]
by = collections.defaultdict(lambda: [[], []])
for r in p0:
    by[r[1]][0 if r in settled else 1].append(r)
FIX = re.compile(r"^(正确\s*\+\s*修)[。，,.：:；;\s]*(?:（[^）]*）[：:]?)?\s*")
def note_body(n):  # strip the 「正确+修（…）：」 / 「错误（…）：」 lead-in, keep the substance
    return re.sub(r"^(正确\s*\+\s*修|正确|错误)[。，,.：:；;\s]*(?:（[^）]*）)?[：:，,。\s]*", "", n).strip()
def revised_mark(rv):
    return f"（已修订 {rv['revised']['at']}，出处 {'、'.join(rv['revised']['source'])}；原结论见 RULE_REVIEWS.json）" if rv.get("revised") else ""
def contract_line(r, dfx):
    old = f"{r[0]} {r[2].strip()}（`{r[5]}`，置信度 {r[6]}）"
    rv = reviews.get(r[0])
    if not rv:
        return f"- [ ] {old}" + (f" — {dfx[:220]}" if dfx else "")
    note, when, mark = rv.get("note", "").strip(), rv.get("at", "")[:10], revised_mark(rv)
    if rv["verdict"] == "wrong":
        return f"- [x] **不复刻** · 老行为：{old} → 新系统做法：{note_body(note) or '见 RULE_REVIEWS.json'}（不复刻；评审 {when}）{mark}"
    if rv["verdict"] == "discuss":
        return f"- [ ] **待讨论** · {old} — {note or dfx[:220]}{mark}"
    if FIX.match(note) or (note and not note.startswith("正确")):  # a decision other than plain 「正确」 changes behaviour
        return f"- [x] {old} — 修正：{note_body(note)}（已评审 {when}）{mark}"
    return f"- [x] {old}（已评审 {when}" + (f"：{note_body(note)}" if note_body(note) else "") + f"）{mark}"
reviewed = [r for r in blocked if r[0] in reviews and reviews[r[0]]["verdict"] != "discuss"]
L = [f"行为契约取自 `BUSINESS_RULES.md` 与 `rules/IDENTITY_ORG_RULES.md` 的 **{len(p0)} 条 P0 规则**（全部 {len(rows)} 条）。它们构成回归测试集：每条在其所属阶段上线前，都必须证明等价，或以 `RULE_REVIEWS.json` 中的业务决定为准证明「按决定修正」。", "",
     f"- **已定行为（{len(settled)} 条）**：置信度 High 且无疑似缺陷（身份与组织域以抽取时的 Review=No 为准），按老系统行为复刻。",
     f"- **阻塞项（{len(blocked)} 条，已评审 {len(reviewed)} 条）**：置信度低于 High，或抽取时发现疑似缺陷（老行为看起来有误，或与文档不一致）。按用户要求「顺手修已知 bug、行为现代化」，这些规则**在所属阶段开工前必须经 `/code-modernization:modernize-review psa` 由业务逐条决定**（仅限尚未决定的规则；已决定的规则只追加 `revisions`）：保持原行为、按修正行为实现，或列入讨论。未决定的规则阻塞该阶段的入口条件。已评审的条目按 `RULE_REVIEWS.json` 呈现：**不复刻** = 新系统按「新系统做法」实现，老行为不作为基准；「修正：」= 规则保留、按修正实现；「已评审」= 按老行为复刻。条目带 `revisions` 时以最后一条修订为当前结论，并标注「已修订」（修订日期）与出处，原结论仍保留在 `RULE_REVIEWS.json`；「已评审」的日期是原结论的日期。已决定的条目改决定时只在 `revisions` 末尾追加，不得用插件评审面板或 `/code-modernization:modernize-review` 改写它们（会丢失 `revisions`、改动原字段）。", "",
     "| 域 | 阶段 | 已定（P0） | 阻塞（P0，需评审） | 其中已评审 |", "|---|---|---|---|---|"]
for d, (s, b) in by.items():
    L.append(f"| {d} | {PHASE.get(d,'')} | {len(s)} | {len(b)} | {sum(r in reviewed for r in b)} |")
for d, (s, b) in by.items():
    def settled_item(r):  # a settled rule can still be revised by a later review (e.g. RULE-160 after 145/155)
        rv = reviews.get(r[0])
        head = f"{r[0]} {r[2].strip()}"
        if not rv:
            return head
        n = note_body(rv.get("note", ""))
        if rv["verdict"] == "wrong":
            return head + f"（**不复刻**，新系统做法：{n or '见 RULE_REVIEWS.json'}）" + revised_mark(rv)
        if rv["verdict"] == "discuss":
            return head + f"（待讨论：{n}）" + revised_mark(rv)
        return head + (f"（修订：{n}）" if n else "") + revised_mark(rv)
    L += ["", f"### {d}（{PHASE.get(d,'')}）", "", "**已定：** " + ("、".join(settled_item(r) for r in s) if s else "（无）"), "",
          "**阻塞，需评审：**"]
    for r in b:
        dfx = (re.search(r"\*\*Suspected defect:\*\*\s*(.+)", body[r[0]]) or [None, ""])[1]
        L.append(contract_line(r, dfx))
tpl = open(os.path.join(BASE, "MODERNIZATION_BRIEF.template.md"), encoding="utf-8").read()
PC = ["### 流程梳理结论（按阶段，METHODS M4）", "",
      "每个阶段实施前做端到端流程梳理，差异清单逐条由用户决定；结论来自 `process/<阶段>/contract.md`，生成到这里。"]
for ph in re.findall(r"(?m)^#### Phase (\S+) —", tpl):
    src = os.path.join(BASE, "process", ph, "contract.md")
    PC += ["", f"#### Phase {ph}", "", open(src, encoding="utf-8").read().strip() if os.path.exists(src) else "待流程梳理"]
open(os.path.join(BASE, "MODERNIZATION_BRIEF.md"), "w", encoding="utf-8").write(
    tpl.replace("{{BEHAVIOR_CONTRACT}}", "\n".join(L)).replace("{{PROCESS_CONTRACT}}", "\n".join(PC)))
print("P0", len(p0), "settled", len(settled), "blocked", len(blocked), "reviewed", len(reviewed)); print({d: (len(s), len(b)) for d, (s, b) in by.items()})
