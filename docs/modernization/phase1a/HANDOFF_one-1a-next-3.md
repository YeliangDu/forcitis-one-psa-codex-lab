# 交接报告：one-1a-next-3 → one-1a-next-4（结构重构主线）

交接时点：2026-10-15，上下文 76%（SESSION_PLAYBOOK TEAM-05、06：75% 准备交接、85% 在批次边界交接）。批次边界：P3b 第二轮修复、FLAKY-SESS 复核在途时。

**会话约定**：全程中文（对话、报告、跨会话消息、子代理最终回复）；派子代理在提示开头写「全程中文，最终回复用中文」并显式指定模型；权限模式与 one-coordinator 一致（bypass）；每开 PR 立即用 `mcp__ccd_pr__set_monitor` 关 Auto-fix（应用常误报「已开启」，以 `get_status` 为准）；完成点报 one-coordinator，PR 由它审核入队，本线不合并。

## 1. 先读

1. 本报告；`phase1a/NEXT_BATCH_PLAN.md` **7.11**（ADR-0039 之后的 R-1b 排序「甲′」，在 R-1b-0 分支上，未合并）、7.8（后续项落点）、文末「Phase 2 前置项」。
2. 卡：`phase1a/cards/R-1b-0.md`（第 14 节 0a / 0b 拆分；R-1b-0 分支）、`phase1a/cards/R-1b-P3b.md`（第 11–14 节新词与裁定；P3b 分支）。
3. 私有证据（不入仓）`~/.modernize/psa/handoff/one-1a-next-3/`：`pending-notes.md`（全部决定、裁定与待办，按时间）、`p3b-sequencing.md`（P3b / 拆分 / 0a·0b 排序论证）、`p2-schema-diff.txt`、`p3b-schema-diff.txt`、`r1b0-card-draft-notes.md`。
4. ADR-0039（防腐层边界）、ADR-0033、ADR-0037。

## 2. 本会话完成的事

| 项 | PR | 状态 |
|---|---|---|
| #119 复审 Low 1–4（build_all / review_revisions） | #184 | 已合并 |
| 阻塞项 101 条件②改为 C-14 从 rc.2 发布包运行只读核实命令 | #185 | 已合并 |
| R-1b P2 用户 ID 底层改 UUID（含 H-1 首次登录登记无操作主体的生产缺陷修复、系统操作主体目录 `system-actors.txt`、`CallerUserIdResolver`、`EmploymentVerdict`、`@NotFromQuery` 放 platform.web、K-3a 的 L-7） | #209 | 已合并 |
| ARCH-D1..D7 核实（D3、D6 与工时审批文档更正，D5 登记为 Phase 2 前置项） | #211 | 已合并 |

## 3. 在途（分支均在 origin，均未开 PR）

### 3.1 R-1b P3b 员工、部门、岗位改 UUID — `claude/one-1a-next-3-r1b-p3b` @453feb26（基于 main a789646f，已强推 rebase 结果）

- **实现与验收已完成**：验收（测试代理 opus，RULING-P3B-1..12）与实现（opus，按模块分提交）均在分支上；rebase 后全量 `backend_verify.sh` surefire 1121 / 0 失败、failsafe 574 / 0 失败，`legacy_it.sh` 7/7，`pnpm verify` 9/9，precheck 14 PASS / 0 FAIL / 3 SKIP（assertion drift 一条警告 = `TaskPolicyTest` 删除，已在集成层 `InboxMergerTest` 重建）。表结构 diff 只含卡第 7 节声明项（`p3b-schema-diff.txt`）。rebase 后首次全量 `TimesheetOwnerAccountsIT` 偶发一次会话 401（重跑通过），疑与 #228 同源（Moments 使测试上下文定时作业全开）。
- **裁定要点**（卡第 13、14 节）：P3B-8 OrgSource 两个登录查人方法合并为 `findStaffByLogin(StaffLookup)`，集成层 api 守在 12；**P3B-9 已改判**——原裁定「不新增 kernel 端口、经 A8 来源查」被推翻（命令要快照里已同步的员工，来源语义错、dev 恒为无人、验收必红、集成层依赖 workforce 会成环；one-architecture 提的方案 3 因 api 上限、取值两套、验收改写、C1 返工不采用），新裁定保留 kernel 端口 `ExternalSubjectEmployees`（`withExternalLoginName(String)` / `withMessagingAccountId(String)`，入参中性，`module-dependencies.txt` 登记，**到期：C1 落地后按身份模型删除或改形，DA-3 时复核**，one-coordinator 同意、告 one-architecture 与用户）；P3B-10 老待办配置键改 `forcitis.platform.integration.legacy.inbox.*`，旧键兼容已删，INFRA_RUNBOOK 与 one.env.template 已改（卡外文档已转 one-ops，排 C-14 后）；只读命令 `find-accounts-by-external-subject`（集成层，`--provider-kind=SEEYON_LOGIN_NAME|WECOM_USER_ID --external-subject=…`，退出码 0 / 1 / 2，只读）已写入 INFRA_RUNBOOK §6 与 C-5 剧本，请 one-ops 核对。
- **在途**：opus 复审已派（结论待回，可能交接后才到——下一会话以 `SendMessage` 无法续接该子代理，请据 PR 状态重派或读其结果）；提议术语已按 one-glossary 核定（`messaging_account` 即时通讯账号、新增配对 `external_login_name` 外部登录名，均 value_object、scope kernel，@453feb26）。
- **之后**：Blocker / High 修复与 opus 复核 → **等 R-1b-0a 合并后 rebase**（解冲突由 P3b 负责，验收断言不得因解冲突改动）、补 R-1b-0 C-5「老系统两视图读取列登记与 SQL 常量一致」检查、重跑全量 + legacy-it → 开 PR → 关 Auto-fix → 报 one-coordinator（合并顺序 0a 先、P3b 后）。
- **PR 正文须单列**：实现代理最终回执列的守护与端口改动、例外清单（新建 `source-type-exceptions.txt` 9 行只减不增、`primary-key-type-baseline.txt` 删 5–12 行）、改动的断言 / 测试（`TaskPolicyTest` 删除与重建、`AssigneeResolverTest` 三处、`OrgUnitSyncRulesTest.smallestHeadIdWins`、`OrgUnitScopeIntegrationTest`、`SeeyonViews*` 收紧、`LegacyInboxAccessIT`、`DatabaseTransportCheckTest`、`SignedTicketTest`、`IdentityIntegrationTest` 替身、`ScalarIdModuleTest` 下限恢复）、C8、只读命令、kernel 端口与 `OrgUnitAccess.Visible` / `AttributionSubjectId` / `StaffLookup`、`relevant_change.py` 一行、RULE-356 靠适配器升序。
- 卡外记录：`legal_entity_attribution_corrections_reject_truncate` 触发器命名 → R-2a；`LegacyShapeTest.APPROVED_AREAS` 两条过期路径 → P5。

### 3.2 R-1b-0a A8 部门类型直读基表、读取列登记合并 — `claude/one-1a-next-3-r1b0` @229588b1

- 已提交：卡（第 11–14 节）、计划 7.11、ADR-0039 排序；验收（测试代理 opus，RULING-R1B0-1..7）：`A8SourceFlagsIT` 11 例（9 红待实现、2 对照绿）；人员标志用例转 `r1b0b-pending/A8MemberFlagsCases.md` 留给 0b。
- **阻塞：实现等用户在用语确认页确认 G01–G03**（读取列登记 / ExternalReadRegistry、读取用途 ReadPurpose 及取值；one-glossary 推荐 G01 改「外部读取登记 / ExternalReadRegister」、G03「对账」改「迁入核对 MIGRATION_TIE_OUT」）。G04 / G05 照常确认；G06 / G07 已撤下，随拆分 a 新词确认。
- 实现要点：`ORG_UNIT.TYPE` 为 nvarchar(50)、排序规则大小写不敏感（one-ops 取证）→ Java 侧精确匹配 Department / Account，大小写不同按非法值整轮 FAILED + ALERT（C-3）；C-2「冻结视图 JOIN 基表不触发改到再迁」依据写在卡 §3.2，opus 复审确认。
- **PR 时一并改**：计划文末「Phase 2 前置项」ARCH-D5 行「老行为先生产取证」→「先对照 AEC 行业项目制专业服务公司的通行做法定审批路线，老系统按行类型 0/1/2 的行为与生产数据只作参考与核对，交 one-rules / 用户定」（用户新原则 intent 2026-10-15-202634 / 203001；one-coordinator 已确认改法）。

### 3.3 FLAKY-SESS CI 偶发 401 — `claude/one-1a-next-3-flaky-sess` @91766d19

- 两层根因（均有 CI run 38772041094 日志证据）：① 会话清理按注入 Clock 判过期而 Spring Session 4.1.1 按系统时钟写会话（无时钟注入点）→ 新增 identity 包私有 `SessionStoreClock`（方案 H，one-coordinator 定；CS-2.26 框架表例外已登记，随 C1 自写会话仓库撤销——C1 卡登记已转 one-architecture）；② Spring Modulith 2.1.1 `MomentsAutoConfiguration` 自带 `@EnableScheduling`，使 `jobs-enabled=false` 在**所有环境**失效 → 生产 `application.properties` 排除该自动配置（项目无 Moments 使用者），新守护 `JobsDisabledInTestContextsIT`。
- 全量 1087 / 545 绿，precheck 11 PASS / 0 FAIL；**opus 复核在途**（重点：Moments 结论、生产影响、守护有效性、上一轮 M-1..M-4 / L-1..L-4 关闭）。**优先于 P3b**（one-coordinator）。复核通过后开 PR，正文须写清：受影响的档案与环境、ShedLock 是此前唯一防线、修正后哪些行为会变；并单列：两层根因、时钟同源修复、**生产配置改动**、守护改动（TimeSourceGuardTest 例外与负向样例、JobsDisabledInTestContextsIT、OperationsProfileSmokeTest 适配）、验收改动（RULING-FS-1 / FS-2）。已请 one-coordinator 告知 one-ops：运维与只读核实档案此前实际会触发定时作业，修正后不会。

## 4. 排期（NEXT_BATCH_PLAN 7.11，one-coordinator 采纳）

R-1b-0a → P3b → 组织同步拆分 b（workforce「应用组织变更」与待放行离职）→ 拆分 a（集成层组织来源同步；`sync_runs`、`sync_diffs` 在 platform_integration 按最终形态新建，ddl-naming-baseline 同步表行直接删，C10 例外）→ R-1b-0b（人员标志与不一致判定记集成层同步差异；业务判断按新原则先对照行业做法）→ DA-3 → P4 → P5 → R-6、R-2a、R-2b、R-5、R-4。拆分前须 one-architecture 出设计卡（依赖反转成环、登录查人端口、D-1a-03 单事务改写）并勘误 ADR-0039:41 与两处计数。

## 5. 登记给本线的后续

- **R-2a**：#212 改名卡共 42 张（后端代码改名归本线，界面文字归 one-1a-par；按合并后的 `glossary/round3/RENAME_CARDS.md` 全表核对，含 P2 带进 main 的 Assigned、Escalated、Fallback、Form、Round、Row、Size 等）；QT / TS / JV 并入 `document_type_code`——先改 `DocumentTypeCodeTest` 读取值（不再要求 `platform-numbering.yaml` 同名 snake）再删三条提议，测试改动单列；审批迁移 SQL 列注释「释放」「业务摘要」「办理人」改术语中文名开头（G39）；触发器命名统一。每张卡合入后告诉 one-glossary 删待改名条目。生产前完成、排在 P5 之后。
- 计划 7.8：P2 复核 L-1（目录旧行不删守护，P5）、L-8（cycleOf，P5）、P2-L2（account_roles 理由，W2）、P2-L12（前端桩数据，P5）、OPS-CID（`OperationsCommands` 用 `UUID.randomUUID()` 作关联号，小卡）、#184 点检 Low（review_revisions 自测与空 `--base` 提示）。
- 验收目录 6 个文件改用 `TestPostgres.container()` 并删 `test-container-gaps.txt` 对应行（测试代理，K-3a 与 P2 已合并，可做）。
- Phase 2 前置：ARCH-D5 工时审批路线映射（见 3.2 末）。

## 6. 经验与注意

- **zsh**：`git push origin "$H:refs/…"` 中 `$H:r` 会被当修饰符吃掉，一律写 `"${H}:refs/heads/…"`。
- **子代理再派子代理**：结果送到最上层会话（§2.1 第 8 条），需原样转给派它的实现代理，并核对其中的删测试 / 改断言。
- **守护与新词**：凡在集成层以外出现外部系统名（如 provider_kind 取值 SEEYON_LOGIN_NAME）会被 `LegacyShapeTest.EXTERNAL_SYSTEM_NAME` 拦（不可登记例外）——相关代码整体放集成层（RULING-P3B-7）。
- **kernel 端口**：用户已定收紧，新增前先找 api 包 / 事件 / 参数解析器等替代；`KernelPortDetourTest` 拦「已有依赖边却经 kernel 绕行」。
- **复审**：每份平均两轮；派复审写明只读、不起容器、不写对象库（不要 merge-tree --write-tree）。
- **额度**：账户本周全模型用量 93%（3 天 14 小时后重置）；用户「照常，用完再说」（intent 183157）。

## 7. 裁定登记（负责会话，one-coordinator 均已知悉或同意；详见 `~/.modernize/psa/handoff/one-1a-next-3/pending-notes.md`）

- **P2**：RULING-P2-1..11、RULING-P2-C5（opus 裁定，静态系统操作主体目录），见 #209 正文。
- **R-1b-0**：RULING-R1B0-1..7（卡第 11、13、14 节）。
- **P3b**：RULING-P3B-D1..D12、P3B-1..12（卡第 13、14 节；P3B-9 已改判，保留原裁定与推翻理由）。
- **FLAKY-SESS**（缺陷卡，one-coordinator 2026-10-15 开卡并选方案 H）：
  - RULING-FS-1：Spring Session 4.1.1 无时钟注入点，会话过期判定改用与框架同基准的 `SessionStoreClock`（CS-2.26 框架表例外，随 C1 撤销）；`SessionPurgeClockIT` 第 2 条「空闲超期仍被清理」不再靠推进注入时钟，改为把会话行的 creation / last_access 时间改到真实时间 11 小时前、expiry 改到 1 小时前构造，断言含义不变。
  - RULING-FS-2（opus 复审 M-1 / M-4 / L-4）：第 1 条清理前把注入时钟拨 +400 天，使任何日期运行都能区分「清理用注入时钟」的回归；类 Javadoc 与 acceptance README 例外说明更正；第 2 条收紧为恰删 1 条并断言 `platform.identity.session.expired`、`cause=IDLE` 审计。
