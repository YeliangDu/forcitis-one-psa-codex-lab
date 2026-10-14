#!/usr/bin/env python3
"""Fill {{BEHAVIOR_CONTRACT}} in MODERNIZATION_BRIEF.template.md from BUSINESS_RULES.md P0 rules."""
import re, collections
rules = open("analysis/psa/BUSINESS_RULES.md", encoding="utf-8").read()
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
L = [f"行为契约取自 `BUSINESS_RULES.md` 的 **{len(p0)} 条 P0 规则**（全部 {len(rows)} 条）。它们构成回归测试集：每条在其所属阶段上线前，都必须证明等价，或以 `RULE_REVIEWS.json` 中的业务决定为准证明「按决定修正」。", "",
     f"- **已定行为（{len(settled)} 条）**：置信度 High 且无疑似缺陷，按老系统行为复刻。",
     f"- **阻塞项（{len(blocked)} 条）**：置信度低于 High，或抽取时发现疑似缺陷（老行为看起来有误，或与文档不一致）。按用户要求「顺手修已知 bug、行为现代化」，这些规则**在所属阶段开工前必须经 `/code-modernization:modernize-review psa` 由业务逐条决定**：保持原行为、按修正行为实现，或列入讨论。未决定的规则阻塞该阶段的入口条件。", "",
     "| 域 | 阶段 | 已定（P0） | 阻塞（P0，需评审） |", "|---|---|---|---|"]
for d, (s, b) in by.items():
    L.append(f"| {d} | {PHASE.get(d,'')} | {len(s)} | {len(b)} |")
for d, (s, b) in by.items():
    L += ["", f"### {d}（{PHASE.get(d,'')}）", "", "**已定：** " + ("、".join(f"{r[0]} {r[2].strip()}" for r in s) if s else "（无）"), "",
          "**阻塞，需评审：**"]
    for r in b:
        dfx = (re.search(r"\*\*Suspected defect:\*\*\s*(.+)", body[r[0]]) or [None, ""])[1]
        L.append(f"- [ ] {r[0]} {r[2].strip()}（`{r[5]}`，置信度 {r[6]}）" + (f" — {dfx[:220]}" if dfx else ""))
tpl = open("analysis/psa/MODERNIZATION_BRIEF.template.md", encoding="utf-8").read()
open("analysis/psa/MODERNIZATION_BRIEF.md", "w", encoding="utf-8").write(tpl.replace("{{BEHAVIOR_CONTRACT}}", "\n".join(L)))
print("P0", len(p0), "settled", len(settled), "blocked", len(blocked)); print({d: (len(s), len(b)) for d, (s, b) in by.items()})
