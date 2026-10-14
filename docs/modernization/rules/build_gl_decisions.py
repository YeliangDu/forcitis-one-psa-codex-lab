#!/usr/bin/env python3
"""由 GL_FINANCE_DECISIONS.json 生成 GL_FINANCE_DECISIONS.md（不要手改 md）。"""
import json, pathlib
here = pathlib.Path(__file__).parent
d = json.loads((here / "GL_FINANCE_DECISIONS.json").read_text())
dec = d["decisions"]
V = {"decided": "已定", "partly": "已定，部分待财务", "pending": "待财务"}
n = {k: sum(1 for x in dec.values() if x["verdict"] == k) for k in V}
out = [
    "# 总账财务评审结论（G1 入口前）",
    "",
    f"生成自 `GL_FINANCE_DECISIONS.json`（`python3 build_gl_decisions.py`），不要手改。会话：{d['session']} · 范围：{d['scope']} · 依据：{d['basis']}。",
    "",
    f"共 {len(dec)} 项：已定 {n['decided']}，已定但有待财务问题 {n['partly']}，待财务 {n['pending']}。「作答」为用户在弹窗中的原选项；推荐项未被采纳的另注「推荐」。结论是业务口径决定，不改变 ADR-0022 的状态（提议），ADR 文本的修订提议见 `../proposals/GL_SCOPE_REVISIONS.md` §E。",
    "",
    "## 合并与已决定",
    "",
    "| 原事项 | 去向 |",
    "|---|---|",
]
out += [f"| {k} | {v} |" for k, v in d["merged"].items()]
out += ["", "## 结论", "", "| 编号 | 事项 | 状态 | 作答 | 结论 | 相关规则卡 |", "|---|---|---|---|---|---|"]
for k, x in dec.items():
    ans = x["answer"] + (f"（推荐：{x['recommended']}）" if x.get("recommended") else "")
    out.append(f"| {k} | {x['title']} | {V[x['verdict']]} | {ans} | {x['decision']} | {'、'.join(x.get('rules', []))} |")
out += ["", "## 待财务清单", "", "以下问题由用户转财务答复；答复后在 JSON 中补 `finance_answer` 并重新生成。", ""]
i = 0
for k, x in dec.items():
    for q in x.get("pending_finance", []):
        i += 1
        out.append(f"{i}. **{k}** {q}")
out += ["", f"记录时间：{min(x['at'] for x in dec.values())[:10]}。", ""]
(here / "GL_FINANCE_DECISIONS.md").write_text("\n".join(out))
print(f"{len(dec)} decisions, {i} finance questions")
