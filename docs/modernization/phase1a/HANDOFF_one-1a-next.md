# 交接报告：one-1a-next → one-1a-next-2

交接时点：2026-10-15，R-1a（#125）合并后的批次边界。依据：SESSION_PLAYBOOK TEAM-05、06；用户「one-1a-next 在 R-1a 合并后交接 (Recommended)」（经 one-coordinator）。

**会话约定**：全程中文——与用户对话、报告、跨会话消息、子代理最终回复一律中文；派子代理时在提示开头写「全程中文，最终回复用中文」（用户「始终保持中文会话，新开会话继承这个要求」，CLAUDE.md 第 0 条）。权限模式与 one-coordinator 一致（bypass）；整合、复审用 high。

## 1. 先读

1. `phase1a/NEXT_BATCH_PLAN.md`，**第 6 节为最新执行顺序**（与前文不一致处以第 6 节为准；第 6 节在 #124，若尚未合并请看该 PR）。
2. 本报告第 3 节的在途分支与 PR、第 4 节的待办；派工卡在 `phase1a/cards/`（`R-1b.md`、`D1.md`）。
3. 派工卡模板：`docs/modernization/templates/WORK_CARD.md`（one-coordinator #121 / coding-standard 分支；未合并时按本目录 `cards/` 中的写法）。
4. 本会话私有归档（不入仓）：`~/.modernize/psa/handoff/one-1a-next/`——`inventory.md`（修复批后代码清单）、`r3a-evidence.md`、`r1-evidence.md` 与 `r1-dump/`（R-1a 表结构 diff）、`pending-notes.md`（ADR-0029 待改意见、计划待写项）。审计报告在 `~/.modernize/psa/audit/`（DATA_AUDIT、MASTER_DATA_VERSIONING、DDL_AUDIT、PRINCIPLES_GAP_A / B）。

## 2. 本会话完成的事

| 项 | PR | 状态 |
|---|---|---|
| 下一批派工计划、§12.2 开放期直接改基线 | #86、#91、#99、#104、#124 | 合并 / #124 排队 |
| W0 批次基础设施（基线开放标记、迁移单调性、验收测试守护、deploy_gate） | #97 | 已合并 |
| R-3a 纯合并基线（73 → 10 份，opus 独立复做 diff 为空） | #103 | 已合并 |
| R-1a 不涉及 A8 的自有表 UUIDv7、TimeOrderedIds、规范 UUID、去 nil 哨兵 | #125 | opus「可合并」，排队 |
| ADR-0030 非生产单人运维例外 | #93 | 已采纳、已合并 |
| ADR-0031 业务单据锁定、冲销与修正 | #95 | 已采纳、已合并 |
| ADR-0032 权限授予方式与待调整权限 | #94 | 已采纳、已合并 |
| ADR-0034 授权历史可还原 | #118 | 已采纳、已合并 |
| ADR-0033 自有标识与来源对照 + RULE_REVIEWS 修订记录 | #119 | 用户已批准；见第 3 节 |
| ADR-0029 非敏感权限按岗位授予 | #96 | 提议，暂缓（岗位体系重梳） |
| 离职日期候选规则卡（两次生产只读取证） | #109 | 见 one-coordinator |

## 3. 在途分支与 PR

- **#119**（`claude/one-1a-next-adr-0033`，已推送 `ed2b770d`）：ADR-0033 已改为已采纳（引「两份都批准 (Recommended)」，U-1..U-4 已答），ADR-0011 / 0025 / 0027 状态行已标注，已合入含 #118 的 main；规则评审修订记录的守护修复 `68ad6edf` 已推送（含 RULE-345 修订，0034-Q3）。**未完成**：opus 只复核守护 Blocker（`__pycache__`）与 High（缺基线静默跳过）的修复——本会话交接前已派出；若结论未到，请新会话重新派 opus 复核（只读，可在 scratchpad 临时克隆实测浅克隆、无 origin/main、push 以 before 为基线、`GITHUB_ACTIONS=true` 不带 `--base`），结论贴进 PR 后通知 one-coordinator 合并。
- **#96**（`claude/one-1a-next-adr-0029`）：已推送取证写入提交 `6a119249`。W1 之后按 `pending-notes.md` 改：不依赖现有岗位名称与数量、「公司岗位体系重梳完成」为批准前置、Q10 / Q11 暂不报、H1（删除收紧 ADR-0032 第 1 项第 4 条的条款）、M1–M6、授予渠道改为 account_role_grants 的来源取值。
- **#109**：离职日期规则卡，已写入第二次取证，等 one-coordinator 合并。

## 4. 待办（按顺序，详见计划第 6 节）

1. **R-1b**（派工卡 `cards/R-1b.md`）——ADR-0033 已批，R-1a 合并后开工；可拆子 PR。
2. **D1 缺陷批卡 2–5**（`cards/D1.md`）——与 R-1b 并行；D1-1（视图超时）排 R-1b 之后。
3. W1 余下：R-6 → R-2a → R-2b → R-5 → R-4（Codex，卡给 one-coordinator，按计划 6.2 与已定 8 条：固定种子时刻、DML now() 并入种子、回填复制来源时间、框架表核实、ShedLock 保留并在 TimeSourceGuardTest 旁一行注释、Flowable 不在范围、updated_at 由注入 Clock、卡外文件列入；「例外清单为空」为验收）→ DDL-07+24（审计分区，one-ops 写维护角色，已对齐要点见计划 6.2）→ DDL-26 / 28 → DA-3 → DA-10、DA-8、DA-13。
4. FLW-1、OSS-1、OSS-2、ADR28-1、ADR28-2：首次部署前须有结论（计划第 3 节）。
5. W2：ADR-0034 实现（含复职 F-13、DDL-17）、主数据版本化 25 表与 G-D11、DA-7 / DA-17、法人代码发号后禁改；K-1 / K-2 / K-3。
6. W3 功能补做（F-1 依赖 ADR-0034 授予表）；F-15 随 F-3。

## 4a. DDL-24 分区量级证据（one-ops 2026-10-15，计划第 6 节 DDL-24 行的补充）

老系统 SQL Server 只读、只做按月计数（one-coordinator 批准）：`dbo.system_operate_log` 近 5 个月 4.0–5.7 万条 / 月（2026-05 峰值 56,719），`dbo.system_login_log` 0.9–1.1 万条 / 月；两表合计月峰值约 6.8 万，近 5 个月均值约 5.2 万；接口访问日志 `infra_api_access_log` 约 19 万条 / 月可作上限参照。按审计口径 2–3 倍余量约 10–20 万条 / 月，在线 3 年约 36 个分区、单分区十万级——维持按月 RANGE 分区。实现 DDL-24 卡时把此段写进卡与 PR 证据。

## 5. 待用户 / one-coordinator 决定

- 无本会话发起的未决用户问题（ADR-0033 / 0034 子问题已全部答复）。
- 规则条文（NAMING §2.6、GUIDING §7a / §11、DATA_DESIGN_CHECKLIST §14.2 / §14.3、SUPPLY_CHAIN.md、ADR-0035 契约演进、ADR-0036 历史数据迁入）由 one-coordinator 另派起草，出来后核对并排守护。
- one-ops 的审计分区量级取证已获批准，结果回来后定分区粒度（按月或按周）。

## 6. 经验与注意

- 每份 ADR 平均要 2–3 轮 opus 复核才收敛；派起草代理时把已知约束（已采纳 ADR 原文、用户原话、相邻 ADR 的标识形态）一次给全，能少一轮。
- 子代理不能在 scratchpad 写「报告文件」（会被拒），但可以写仓库交付物；需要长结果时让其写进仓库文件或直接返回。
- 跨会话转达的用户原话，先用会话记录搜索核实再执行（本会话全部核实通过）。
- 起容器前已有用户授权：W1 期间本机临时 postgres:18 空库，用完即删、只碰自己命名的容器，报告里写明已清理。
