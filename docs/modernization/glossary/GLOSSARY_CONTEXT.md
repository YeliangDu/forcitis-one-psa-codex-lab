# one-glossary 速查（2026-10-15）

规矩：只预判、不裁定，用户答复为准；会计类经财务（ADR-0018）；一行一项，过程放链接（SESSION_PLAYBOOK §2.1 第 13 条）。

## 判定顺序（`terms.yaml` meta.naming_priority）

| 级 | 依据 | 例 |
|---|---|---|
| 1 | 法规与会计准则（CAS 原文；IFRS / XBRL） | 合同资产、履约义务 |
| 2 | 国标与工程标准（GB/T 50326 等；PMBOK、FIDIC） | 工程变更、WBS |
| 3 | 专业服务行业通行写法 | Timesheet、Utilization |
| 4 | 其余为方言（禁入代码、界面、接口文档、列注释）；判定不了为待定 | 产值 = 收入确认单 |

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
| 在途 | ACL 归类与第三轮重做已全部提交（`glossary/answers/2026-10-15-acl-round3.json`：同意 394、保持原样 16），待写回；固定链接改为 https://claude.ai/artifact/QPwiXzEFWCTqSGVCQRdK6J（用户手机能开，答复在其页面数据库）；原 MwoqwkhAYq9CtomgaedwZ1 停用，不删 |
| 下一步 | /clear 后先读本文件与 sessions/one-glossary.md，`gh pr view 206` 核对已合并再开分支；**开工前把方案（字段、守护、拆分）报 one-coordinator**。三个 PR（A、B 可并行）：**A 写回**——答复 `glossary/answers/2026-10-15-acl-round3.json`（同意 394、保持原样 16）；组织同步 7 条（被中止、放行、同步、同步差异、登录名、关系、离职观察时间）按「以 ADR-0039 为准」归集成层，「待放行离职」留 workforce（intent 2026-10-15-171549-one-coordinator）；其余 9 条「保持原样」作领域用语；可参考已关闭 #196 的 round3/apply_round3.py（分支 claude/one-glossary-round3-writeback）。**B 登记规则与守护**（intent 2026-10-15-171649-one-coordinator：「新用语登记要登记在哪个系统使用是acl还是新系统领域，是领域的要按照bc汇集，同一用语在不同bc含义不同，比如项目。」）——terms.yaml 与 proposed 每条必填「使用范围」（ACL / 新系统领域；ACL 再标四类）与 bc，生成器与 GlossaryTest 校验、缺即失败；查重改为同 bc 内、跨 bc 同名显式互列（`<bc>__` 前缀）；new_term_issue.py 与提议模板加两栏；CLAUDE.md 第 2 条改为「同一 bc 内查重；登记时写明使用范围和 bc」（PR 里单列）；守护改动单列并派 opus 复审；并入 DDD 统一语言 8 条（intent 2026-10-15-171959-one-coordinator「落点」栏，PR #200）：使用范围三值（领域 bc / 共享内核 kernel 列使用 bc / ACL）、发布语言用上游 bc 用语、kind 定为 DDD 战术分类并补齐代码 15 个聚合与事件词条、定义写含义边界与近义区别（指标写口径）、事件过去式命令祈使禁技术泛词、同 bc 一概念一中一英、每 bc 业务确认人（未定前用户兼任）、状态提议/正式/弃用且代码可反查词条 id；方案先报 one-coordinator，第 3、8 条守护派 opus 复审。**C「项目」同名异义样例**——盘点销售、交付、财务等 bc 里的含义，做成在线页（固定链接 QPwiXzEFWCTqSGVCQRdK6J）交用户。另：代码搬迁与 ADR 冲突交 one-architecture；R-1b-0 门禁按 ACL 结论重出 |
| 用语规范 | evidence 只用于「证据」义（fact_evidence 等）；判定结果用 verdict（在职判定 EmploymentVerdict） |
| 定时 | 每周积压检查（会话内定时任务，开会话后设，记在画像） |
