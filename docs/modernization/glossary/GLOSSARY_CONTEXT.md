# one-glossary 速查（2026-10-15）

规矩：只预判、不裁定，用户答复为准；会计类经财务（ADR-0018）；一行一项，过程放链接（SESSION_PLAYBOOK §2.1 第 13 条）。

bcs.yaml 每个 bc 的 `words`（不另立词条的业务用语）能给界面文字背书，作用同正式词条（B2 #272，用户新定）：往里加词也走用语登记流程，由 one-glossary 把关、用户确认，不当第二个放行清单；规则——纯中文、两字以上，不得是正式词条名或禁用写法，要成为词条先走用语流程，前端脚本会检查；正式登记成词条时同时从 words 删掉。

## 判定顺序（`terms.yaml` meta.naming_priority）

| 级 | 依据 | 例 |
|---|---|---|
| 1 | 法规与会计准则（CAS 原文；IFRS / XBRL） | 合同资产、履约义务 |
| 2 | 国标与工程标准（GB/T 50326 等；PMBOK、FIDIC） | 工程变更、WBS |
| 3 | 专业服务行业通行写法 | Timesheet、Utilization |
| 4 | 其余为方言（禁入代码、界面、接口文档、列注释）；判定不了为待定 | 产值 = 收入确认单 |

推荐依据以准则、国标与 AEC 项目制专业服务行业通行做法为主；老系统叫法与生产数据只作证据，至多作别名，「老系统这么叫」不是保留理由（intent 2026-10-15-202634，CLAUDE.md 第 4 条）。

## 在哪里

| 什么 | 路径 |
|---|---|
| 正式术语（单一来源，生成） | `glossary/terms.yaml` ← `glossary/build_terms.py` + 决定记录 `glossary/decisions-*.json` |
| 通用词与守护规则 | `backend/glossary/terms.yaml`（GlossaryTest G37–G39） |
| 提议术语 | `backend/glossary/proposed/*.yaml`，清单 `proposed/README.md`（生成） |
| 用语问题 | `glossary/issues/`（问题只增不改；处理写同名 `.resolution.md`，用 `resolve_term_issue.py`），清单与积压计数 `glossary/ISSUES.md`（生成，不入仓） |
| 决策单记录 | `glossary/decision-sheets/<日期>.md`（每出一次一份，ISSUES.md 据此算距上次天数） |
| 待确认表 | `glossary/TERM_CONFIRMATION.md`（A 组 19 项待定） |
| 第三轮输入 | `r0/TERMS_ROUND3_INPUT.md`、`glossary/STANDARDS_AUDIT.md`、`glossary/oat/` |
| 前两轮做法 | 确认页 `glossary/build_confirm_page.py`、答复 `glossary/export_decisions_round2.py` |
| 决策单（在线页） | 第三轮 `glossary/round3/`：`build_round3_page.py` + `page-template.html` 生成私有 Artifact，db 存 `decisions/<编号>`，ArtifactData 读答复，`export_decisions.py` 导出；页面不引用 Google 字体、页面级不设 overflow 隐藏 |
| 门禁小决策页 | `glossary/gate/`：数据 `<卡号>.json` → `build_gate_page.py` → 发布 → `export_decisions.py` → `glossary/decisions-gate-<卡号>.json` → `build_terms.py`（`apply_gate.py`）；用法见 `gate/README.md` |

## 流程

开工前术语门禁（每张卡验收阶段交本卡新词 → 当天预判 → 小决策页 → 用户确认 → 写回 → 负责会话才派实现）；存量与遗漏走：登记（各会话 `new_term_issue.py` / 提议术语）→ 去重归并（同义合并、同名改开）→ 预判（级别与依据，写处理片段）→ 决策单（在线页；业务用语积压达到 100 条或距上次超过 7 天；记 `decision-sheets/`）→ 用户答复 → 写回（`decisions-*.json` 新一轮一份 + `build_terms.py` 该轮应用段 → `terms.yaml`；提议状态）→ 改名 PR 先合、`forbidden` 后合 → 代码标识由 G38 拦截，文档方言靠巡检 → 处理片段改「已关闭」并写去向。

## 现状

| 项 | 值 |
|---|---|
| 积压 | 以 `glossary/ISSUES.md` 首段为准（门槛只算业务用语；错误码单列归 R-5，用户 2026-10-15） |
| 已知重复 | 中文名重复 5 组（§5 起因）；同义错误码 4 组（R-5 卡） |
| 在途 | 来单驱动的预判与登记（任务登记在 `tasks/NAM-*-GLO-*.md`，最新到 NAM-1021-GLO-23）；GL3、GL4、GL5 已答复写回；待办清单见 glossary/HANDOFF_20261022.md |
| 下一步 | /clear 后读本文件、glossary/HANDOFF_20261022.md、sessions/one-glossary.md、`tasks/` 本会话片段；先办 one-1a-admin 卡 C 的 T-G1…G5，再 codex-n20/n22/n21 成果写回；规则：模块标识与业务用语不自定，先审题再进收件箱 |
| B2 | B2（分支 claude/one-glossary-b2）：类别统一为 DDD 十种（判定表 `glossary/b2/kind-judgement.json`，subdomain/module/system 移入 bcs.yaml，属性挂 attributes），term_rules 加 kind 与命名规则，GlossaryTest 加 G40–G42（聚合根/领域事件反查词条，基线 `backend/glossary/code-term-baseline.txt`），59 条缺失的聚合与事件登记为提议（`proposed/code-aggregates-events.yaml`）；待用户确认清单在共享笔记 b2-confirm-list.md。已合：#212、#214、#234（B1）、#236 |
| 用语规范 | evidence 只用于「证据」义（fact_evidence 等）；判定结果用 verdict（在职判定 EmploymentVerdict） |
| 定时 | 每周积压检查（会话内定时任务，开会话后设，记在画像） |
