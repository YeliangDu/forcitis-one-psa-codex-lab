#!/usr/bin/env python3
"""Fill {{BEHAVIOR_CONTRACT}} in MODERNIZATION_BRIEF.template.md from BUSINESS_RULES.md P0 rules and RULE_REVIEWS.json.

Run from the repo's docs/modernization (or pass the directory holding BUSINESS_RULES.md as argv[1]; analysis/psa is still accepted)."""
import re, collections, json, os, sys
BASE = sys.argv[1] if len(sys.argv) > 1 else ("analysis/psa" if os.path.isdir("analysis/psa") else ".")
rules = open(os.path.join(BASE, "BUSINESS_RULES.md"), encoding="utf-8").read()
_rv = os.path.join(BASE, "RULE_REVIEWS.json")
reviews = json.load(open(_rv, encoding="utf-8"))["reviews"] if os.path.exists(_rv) else {}
PHASE = {"售前与报价": "Phase 1b", "审批（platform.approval）": "Phase 1a", "交付、预算与收入": "Phase 2（收入确认部分在 Phase 4）",
         "工时与资源": "Phase 2", "项目成本与核算": "Phase 4"}
rows = re.findall(r"^\| (RULE-\d+) \| ([^|]+) \| ([^|]+) \| (\w+) \| (P\d) \| `([^`]+)` \| (\w+) \|$", rules, re.M)
body = {m.group(1): m.group(2) for m in re.finditer(r"### (RULE-\d+): .+\n((?:(?!### RULE-).*\n)*)", rules)}
p0 = [r for r in rows if r[4] == "P0"]
settled = [r for r in p0 if r[6] == "High" and "Suspected defect" not in body[r[0]]]
blocked = [r for r in p0 if r not in settled]
by = collections.defaultdict(lambda: [[], []])
for r in p0:
    by[r[1]][0 if r in settled else 1].append(r)
FIX = re.compile(r"^(正确\s*\+\s*修)[。，,.：:；;\s]*(?:（[^）]*）[：:]?)?\s*")
def note_body(n):  # strip the 「正确+修（…）：」 / 「错误（…）：」 lead-in, keep the substance
    return re.sub(r"^(正确\s*\+\s*修|正确|错误)[。，,.：:；;\s]*(?:（[^）]*）)?[：:，,。\s]*", "", n).strip()
def contract_line(r, dfx):
    old = f"{r[0]} {r[2].strip()}（`{r[5]}`，置信度 {r[6]}）"
    rv = reviews.get(r[0])
    if not rv:
        return f"- [ ] {old}" + (f" — {dfx[:220]}" if dfx else "")
    note, when = rv.get("note", "").strip(), rv.get("at", "")[:10]
    if rv["verdict"] == "wrong":
        return f"- [x] **不复刻** · 老行为：{old} → 新系统做法：{note_body(note) or '见 RULE_REVIEWS.json'}（不复刻；评审 {when}）"
    if rv["verdict"] == "discuss":
        return f"- [ ] **待讨论** · {old} — {note or dfx[:220]}"
    if FIX.match(note) or (note and not note.startswith("正确")):  # a decision other than plain 「正确」 changes behaviour
        return f"- [x] {old} — 修正：{note_body(note)}（已评审 {when}）"
    return f"- [x] {old}（已评审 {when}" + (f"：{note_body(note)}" if note_body(note) else "") + "）"
reviewed = [r for r in blocked if r[0] in reviews and reviews[r[0]]["verdict"] != "discuss"]
L = [f"行为契约取自 `BUSINESS_RULES.md` 的 **{len(p0)} 条 P0 规则**（全部 {len(rows)} 条）。它们构成回归测试集：每条在其所属阶段上线前，都必须证明等价，或以 `RULE_REVIEWS.json` 中的业务决定为准证明「按决定修正」。", "",
     f"- **已定行为（{len(settled)} 条）**：置信度 High 且无疑似缺陷，按老系统行为复刻。",
     f"- **阻塞项（{len(blocked)} 条，已评审 {len(reviewed)} 条）**：置信度低于 High，或抽取时发现疑似缺陷（老行为看起来有误，或与文档不一致）。按用户要求「顺手修已知 bug、行为现代化」，这些规则**在所属阶段开工前必须经 `/code-modernization:modernize-review psa` 由业务逐条决定**：保持原行为、按修正行为实现，或列入讨论。未决定的规则阻塞该阶段的入口条件。已评审的条目按 `RULE_REVIEWS.json` 呈现：**不复刻** = 新系统按「新系统做法」实现，老行为不作为基准；「修正：」= 规则保留、按修正实现；「已评审」= 按老行为复刻。", "",
     "| 域 | 阶段 | 已定（P0） | 阻塞（P0，需评审） | 其中已评审 |", "|---|---|---|---|---|"]
for d, (s, b) in by.items():
    L.append(f"| {d} | {PHASE.get(d,'')} | {len(s)} | {len(b)} | {sum(r in reviewed for r in b)} |")
for d, (s, b) in by.items():
    def settled_item(r):  # a settled rule can still be revised by a later review (e.g. RULE-160 after 145/155)
        n = note_body(reviews.get(r[0], {}).get("note", ""))
        return f"{r[0]} {r[2].strip()}" + (f"（修订：{n}）" if n else "")
    L += ["", f"### {d}（{PHASE.get(d,'')}）", "", "**已定：** " + ("、".join(settled_item(r) for r in s) if s else "（无）"), "",
          "**阻塞，需评审：**"]
    for r in b:
        dfx = (re.search(r"\*\*Suspected defect:\*\*\s*(.+)", body[r[0]]) or [None, ""])[1]
        L.append(contract_line(r, dfx))
tpl = open(os.path.join(BASE, "MODERNIZATION_BRIEF.template.md"), encoding="utf-8").read()
open(os.path.join(BASE, "MODERNIZATION_BRIEF.md"), "w", encoding="utf-8").write(tpl.replace("{{BEHAVIOR_CONTRACT}}", "\n".join(L)))
print("P0", len(p0), "settled", len(settled), "blocked", len(blocked), "reviewed", len(reviewed)); print({d: (len(s), len(b)) for d, (s, b) in by.items()})
