# one-glossary 速查（2026-10-15）

规矩：只预判、不裁定，用户答复为准；会计类经财务（ADR-0018）；一行一项，过程放链接（SESSION_PLAYBOOK §2.1 第 13 条）。

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
| 在途 | 已合：#212（第三轮与 ACL 写回，正式 580 条；改名卡 42 张已派：后端随 R-2a 由 one-1a-next-3、界面由 one-1a-par，QT/TS/JV 3 张随 R-2a）、#214（「项目」样例卡、R-1b-0 撤 G06/G07）。合并队列：#234（B1：scope/bc 必填、同 bc 查重，存量基线 107 条）。R-1b-0 G01–G03 写回在 #236（G03 同 migration_tie_out 不另立）；本 PR：F-1（业务负责人、维持到期时间）与 R-1b-0 G04、G05 写回，正式词条 594 条（答复在会话里按推荐定，确认页提交未落库）。「项目」22 条答复齐（PJ-02、PJ-19 在 B1 会话答，确认页提交未落库）待写回 |
| 下一步 | /clear 后先读本文件与 sessions/one-glossary.md，核对 #234 与本 PR 已合并（`gh pr list --author @me`）。① 「项目」卡写回（答复在 B1 共享笔记 answers-C-PROJECT-R-1b-0-2026-10-15.json；按 B1 规则带 scope、同名互列；沿用已有词条的不另立）② provider_kind / external_subject / external_identity 迁 platform.integration、scope acl 过渡期，provider_kind 取值 WECOM_USER_ID、SEEYON_LOGIN_NAME（RULING-P3B-7、P3B-9）③ B2：kind 定 DDD 战术分类、聚合与事件反查词条（材料 b1-kind-judgement.json；opus 复审）④ 交来的后续：platform_identity_login_name_unknown 定义含外部名；G39 不分 bc 会误报（开卡）⑤ 发确认页后先请用户刷新，收到提交即核对是否落库 |
| 用语规范 | evidence 只用于「证据」义（fact_evidence 等）；判定结果用 verdict（在职判定 EmploymentVerdict） |
| 定时 | 每周积压检查（会话内定时任务，开会话后设，记在画像） |
