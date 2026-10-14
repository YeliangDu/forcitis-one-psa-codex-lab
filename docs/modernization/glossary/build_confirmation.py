#!/usr/bin/env python3
"""Build glossary/TERM_CONFIRMATION.md from glossary/terms.yaml: everything the business admin must confirm
(user rule 2026-10-14: 不符合正式用语的都归为方言；不确定的登记用语表，由用户确认)."""
import yaml, collections
d = yaml.safe_load(open("glossary/terms.yaml", encoding="utf-8"))
T = d["terms"]
INTERNAL = "FORCITIS 03-通用语言（内部概念，准则 / 国标无对应条目）"
L = ["# 用语确认表", "",
     "规则（用户 2026-10-14）：「所有跟正式用语不符合的，都归为我们公司内部的方言，新系统一律采用正式用语，不把方言带入新系统，不确定哪些是正式用语，哪些是方言可以登记用语表，我来确认。」",
     "", "**怎么确认**：在「确认」列填 `正式`、`方言` 或 `改为：<用语>`，也可以直接回复编号（例如「A3 方言，B12 同意，C 组全部同意」）。确认后由 `glossary/build_terms.py` 更新 `terms.yaml`，状态改为已批准。",
     "", "已确定为方言、无需再确认的（如「产值确认单」「产值」= 收入确认单）不在本表，见 `terms.yaml` 中 `type: dialect` 的别名。", ""]
# A
A = [(t, al) for t in T if t["status"] != "proposed" and not t.get("confirmed") for al in (t.get("aliases") or []) if al.get("type") == "undetermined" and al["name"] != t["zh"]]  # proposed 的在 B 组一并确认
L += [f"## A. 公司叫法：正式用语还是方言？（{len(A)} 项）", "",
      "这些是公司在用的中文叫法，审计没有把握判定。选「正式」则保留并与标准用语并列为同义正式名（新系统只用一个，取右列）；选「方言」则禁止进入新系统。", "",
      "| 编号 | 公司叫法 | 对应正式用语（中 / 英） | 所属模块 | 依据 | 建议 | 确认 |", "|---|---|---|---|---|---|---|"]
for i, (t, al) in enumerate(A, 1):
    ref = "；".join((t.get("standard_ref") or [])[:2])
    sug = "方言" if t["zh"] != al["name"] else "正式"
    L.append(f"| A{i} | {al['name']} | {t['zh']} / `{t['en']}` | `{t['bc']}` | {ref} | {sug} |  |")
# B
B = [t for t in T if t["status"] == "proposed" and not t.get("confirmed")]
fin = {"G-8", "G-9", "G-10", "G-11", "G-12", "G-13", "G-16", "G-17", "G-20", "G-21", "G-27"}
L += ["", f"## B. 改为标准用语的建议（{len(B)} 项）", "",
      "原词表用语不符合准则或行业标准，建议改名；原叫法确认后归为方言。标 💰 的涉及会计口径，建议同时请财务确认。", "",
      "| 编号 | 原用语（将归为方言） | 建议正式用语（中 / 英） | 所属模块 | 依据 | 改名编号 | 确认 |", "|---|---|---|---|---|---|---|"]
for i, t in enumerate(B, 1):
    old = " / ".join(al["name"] for al in (t.get("aliases") or []) if al.get("type") in ("dialect", "undetermined", "old-code")) or "—"
    # 确认 B 组即同时确认原中文叫法归为方言
    ref = "；".join((t.get("standard_ref") or [])[:2])
    g = (t.get("note") or "")
    gid = next((x for x in sorted(fin | {f"G-{n}" for n in range(1, 30)}, key=len, reverse=True) if x in str(t.get("change_ref", "")) ), t.get("change_ref", "") or "")
    mark = " 💰" if gid in fin else ""
    L.append(f"| B{i} | {old} | {t['zh']} / `{t['en']}` | `{t['bc']}` | {ref} | {gid}{mark} |  |")
# C
C = [t for t in T if t["status"] == "approved" and not t.get("confirmed") and set(t.get("standard_ref") or []) <= {INTERNAL, "ADR-0016 权责发生制业财一体主干"}]
byb = collections.defaultdict(list)
for t in C: byb[t["bc"]].append(t)
L += ["", f"## C. 公司自有概念，没有外部标准用语（{len(C)} 项，可整组确认）", "",
      "这些概念在准则、国标、PMBOK、FIDIC 中都没有对应条目，是公司自己的经营管理概念。建议以词表的定义作为正式用语；如果其中有你认为是口头叫法、应另取正式名的，单独标出。", "",
      "| 编号 | 模块 | 用语（中 / 英） | 确认 |", "|---|---|---|---|"]
for j, (bc, ts) in enumerate(sorted(byb.items()), 1):
    names = "、".join(f"{t['zh']}（`{t['en']}`）" for t in ts)
    L.append(f"| C{j} | `{bc}`（{len(ts)} 项） | {names} |  |")
if not (A or B or C):
    L += ["", "## 状态：全部已确认", "", "2026-10-14 业务 Admin 在用语确认页上确认了全部 197 项：A 组 4 项判为方言；B 组 59 项同意改为标准用语（原叫法归为方言）；C 组 134 项公司自有概念确认为正式用语。确认记录见 `glossary/decisions-2026-10-14.json`，已写入 `terms.yaml`（全部 293 条为已批准）。今后新出现的待确认用语会重新出现在本表。"]
open("glossary/TERM_CONFIRMATION.md", "w", encoding="utf-8").write("\n".join(L) + "\n")
print("A", len(A), "B", len(B), "C", len(C), "groups", len(byb))
