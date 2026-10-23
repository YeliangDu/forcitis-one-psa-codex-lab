# 交接报告：one-1a-next-2 → one-1a-next-3（结构重构主线）与 one-1a-par（只读核实命令）

交接时点：2026-10-15，ADR37-1（#163）复核通过、R-1b P2 开工之前的批次边界。依据：SESSION_PLAYBOOK TEAM-05、06（上下文已压缩接近上限）；one-coordinator 转达的编排调整（用户「三项都采纳」，intent `2026-10-15-124901-one-coordinator-部署前开发编排三项都采纳-加一个.md`）。

**会话约定**：全程中文（与用户对话、报告、跨会话消息、子代理最终回复）；派子代理在提示开头写「全程中文，最终回复用中文」，并显式指定模型。权限模式与 one-coordinator 一致（bypass）；整合、复审用 high；安全闸门卡全程 opus。

## 1. 先读

1. `phase1a/NEXT_BATCH_PLAN.md` **第 7 节**（#149 已合并，最新执行顺序；第 7.2 节 R-1b P0–P6 拆分与 C1–C11 处理表）。
2. 本报告第 3 节在途、第 4 节派工草案、第 5 节裁定登记。
3. 编排调整 intent（上述），以及 `COORDINATOR_LEDGER.md` 的在途会话表。
4. 私有证据（不入仓）：`~/.modernize/psa/handoff/one-1a-next-2/`——`pending-notes.md`（全部待并入事项、用户决定核实记录、裁定）、`r1b-split-plan.md`（R-1b 拆分图，P2 起的范围与验收测试分配）、`pr129-consistency.md`（守护归属草案）、各 PR 复审全文（`p3a-review.md`、`d1-1-review.md`、`p1-review.md`、`adr0037-critic.md`、`adr37-1-review.md`）、表结构 diff 证据（`p3a-*schema-diff.txt`）、`r1b0-a8-flags-evidence.md`（A8 标志列取值计数）。

## 2. 本会话完成的事

| 项 | PR | 状态 |
|---|---|---|
| D1-5 / D1-5b 测试口令运行时生成 | #132 | 已合并 |
| D1-2 / D1-3 / D1-3b 未知异常不落盘、prometheus 管理端口、连接串参数 | #134 | 已合并 |
| R-1b P0 编号计数器计数范围（DDL-03 余项） | #137 | 已合并 |
| 阻塞项 100（只读连接校验失效，ADR-0037 实现前不得部署） | #138 | 已合并 |
| R-1b P3a 来源对照、核算组织编码移出法人、原编号来源系统、同日更正 | #139 | 已合并 |
| 计划第 7 节、派工卡修订 | #149 | 已合并 |
| D1-1 外部连接只经集成层工厂并强制三种超时 | #153 | 已合并 |
| R-1b P1 员工 ID / 用户 ID 语义拆分、事件负载扁平、Error Prone 与 ID 比较字节码守护、SUPPLY_CHAIN S4 条款 | #162 | 已合并 |
| CI-LIT：`-Plegacy-it` 按改动路径与每夜全量 | #165 | 已合并 |
| ADR-0037 新系统读取老系统与 A8 数据的范围（已采纳） | #136 | 见 one-coordinator |
| ADR37-1 只读连接启动校验按平台基线重写（(a)–(g)、运行期重检、凭据列守护、阻塞项 101） | #163 | 见第 3 节 |

## 3. 在途

- **#163（ADR37-1）**：已收尾——安全复审第一轮修复（`bc5548bb`）、RULING-ADR37-11 夹具贴近 RDS（`6df27ff4`）与 (d) 收紧（`17d55880`）、与 main 的 PREDEPLOY_CHECKLIST 冲突合并（`9fa44e5e`）。**opus 安全复核结论：H-1、H-2、H-3 已关闭，可合并**（全文 `adr37-1-recheck.md`），已交 one-coordinator 入队。**待 one-coordinator 裁定并由后续小 PR 落地**：
  - M-1（one-coordinator 裁定：**没有死循环**）：C-14 按 one-ops 剧本（#171）是在 ECS 上从签名发布包解出 jar 运行只读核实命令，不安装、不切换、不启动服务，不需要先部署 dev；101 两个删除条件都保留，C-14 仍是 dev 前置，不移到 `PROD_PREREQUISITES`。**待办（one-1a-next-3，#163 合并后小 PR）**：101 正文条件②改写为「C-14：在 ECS 上从 rc.2 发布包运行只读核实命令完成真实登录反例核实，不需要部署」。
  - M-2（one-coordinator 采纳）：C-14 加一步——用只读核实命令对三个正式运行时账号各跑一次，不改任何授权，预期全部通过；有 public 授权行导致 (g) 拒绝的，停下报 one-coordinator，再定是否收窄 (g)（one-ops 补进 #171）。
  - Low（后续小卡）：L-1 自定义服务器角色（`sys.login_token` 只许 public）；L-2 每日重检判定不了时只在成功时更新上次检查时刻、加状态指标；L-3 `ExternalFailure` 以错误号为判据、SQLState 可空；L-4 `SELECT *` 禁令的注释 / `TOP (?)` / 常量绕过；L-5 README 写明所有者仍受库级 DENY、(g) 防转授与 IMPERSONATE；L-6 C-14 剧本执行前补齐（#171）；L-7 系统库 guest / public 授权写进风险登记。
- **R-1b-0（A8 视图标志列）**：WIP 提交 `f1a67d33`（分支 `claude/one-1a-next-2-a8-view-flags`，未推送）作废——按 ADR-0037 Q1 (c)，标志列改为直接读基表（`dbo.` 限定名），随读取列登记合并与 `SeeyonSourceScanTest` 调整一起做；STATE 取值证据见 `r1b0-a8-flags-evidence.md`（STATE 1 在职 / 2 离职，IS_DELETED / IS_ENABLE 为 0/1，ORG_UNIT.TYPE 为 Department / Account，其余取值失败并告警）。RULE-363 修订（STATE 替代「登录名为空即离职」）随 DA-3。
- **C-14 真实账号核实**：用户已批准计划（intent `2026-10-15-120940`，`runbooks/C-14-readonly-probe.md`，one-ops 维护，#171 已按 (a)–(g) 与新反例 12–17 更新）；前提是 #163 合并、只读核实命令合并并发出 rc.2。第 16、17 项（msdb / master 系统库）超出原批准范围，one-coordinator 已报用户。

## 4. 派工草案（one-1a-next-3：P2 起）

依据：拆分图 `r1b-split-plan.md` 第 2、3、4、6 节；计划 7.2。每张卡照常「测试代理先写验收 → 实现 → opus 复审 → Blocker / High 由 opus 复核」。子代理一律用 Agent `isolation: worktree`（运行环境不允许子代理写负责会话 worktree 以外的目录），负责会话从自己的 worktree 用 `git push origin <sha>:refs/heads/<分支>` 快进推送。

| 卡 | 范围（摘要） | 前置 | 测试 / 执行 / 复审 |
|---|---|---|---|
| **P2 用户 ID 底层改 UUID** | identity：`user_accounts.user_id` uuid + `employee_id`（先 bigint 唯一，P3b 改 uuid）、`emergency_admins` / `account_roles`（C3：只改列类型）/ `browser_identifiers` / `authorization_requests` 各列、`ticket_issuers.key_id` 改名；其他 schema 凡存用户 ID 的列一次改完（`idempotent_responses.user_id`、`audit_entries.actor_id` / `on_behalf_of_id`、`approval_tasks.assignee_id`、`approval_task_records.*`、`numbering_rule_versions.*`、`sync_runs.released_by`、`legal_entity_attribution_corrections.corrected_by_user_id`、`source_references.created_by` / `decided_by`）；`UserId(UUID)`、`Long.parseLong` 全改 UUID 解析、`ensureAccount` 四入口 `ON CONFLICT (employee_id)`；`{userId}` 路径与查询参数、OpenAPI `OTHER_ID_NAMES` 删 userId / actorId / onBehalfOfId / subjectUserId / authorId；**C5**：信封已有 `actorType`（USER / SYSTEM / MIGRATION）就复用，不新增 actorKind，作业名字段名查 terms.yaml；P1 验收 `EventPayloadScalarIdsIT` 账号用例按 `Acceptance-Ruling` 改为 UUID | P1 已合并 | opus 测试代理（★ 反向集成测试：应急登录、HELD 放行人、自改法人、自批、给自己授角色）/ **opus 执行**（体量最大，可按模块分提交）/ opus 复审 |
| D1-6 告警行去 IP | `LoginAdmission`、`EnterAdministratorMode` 告警行去 IP，改引审计条目 ID（审计 A 4-M1） | P2 | sonnet / sonnet / opus |
| **P3b 员工、部门、岗位改 UUID** | workforce 快照 / 版本 / 归属表 uuid 主键与引用列、DA-5 拆列、`sync_diffs.subject_key` 拆类别 + uuid、DDL-01 / 06；`user_accounts.employee_id`、`timesheets.*`、`approval_requests` / `start_rounds.initiator_id` 改 uuid；`EmployeeId` / `OrgUnitId` / `PositionId` 改 UUID；删 `Source*Id` 与 `SourceTerms` 的 ID 翻译（P1 构造器守护白名单 RULING-P1-2 的 4 处随之删除）；`JdbcLegacyInbox`、`ShortNameAdapter` 移入集成层（`ExternalConnections` api 可撤）；保持原约束名（C10）；C8：到 P6 前老系统待办与部门简称返回「不可用」，PR 写明 | P2、D1-1（已合并）、R-1b-0 | opus 测试代理 / sonnet 或 opus / opus |
| P4 审批 | `approval_tasks` uuid 主键、`instance_id`、DDL-04、子表挂 `approval_request_id`、办理记录序号、`process_archive_skips`、DDL-08、`outbound_deliveries` 去重键拆列；Flowable idGenerator 接 UUIDv7（one-1a-par 的 FLW-1 在 `platform/approval/infrastructure/flowable/` 新增一个只 `setClock` 的 `EngineConfigurationConfigurer`，P4 的 id 生成器配置与之并存、不依赖执行顺序）；`ApprovalTaskId` / `ApprovalInstanceId` / `DocumentId` 改 UUID | P3b | sonnet / sonnet / opus |
| P5 收口 | （注意：one-1a-par 的 FLW-1 在 `ArchitectureRulesTest.generalCodingRules` 把 java.util.Date 与 PermitAll 拆成两条，Date 规则豁免 `platform.approval.infrastructure.flowable.FlowableEngineClock`——Flowable Clock 接口签名就是 Date；P5 改守护时保留）信封 `aggregateId` 与 `AggregateReference` 改 UUID、`audit_entries.subject_key` 拆列、删 `OTHER_ID_NAMES`、`PrimaryKeyTypeGuardTest` 扩到复合 / text 主键 + `primary-key-exemptions.txt`、去哨兵守护、事件目录 ID 守护 | P4 | sonnet / sonnet / opus |
| P6 老系统对照导入 | one-ops 抽取（读取清单已发，Q1 (c) 后直读基表），R-1b P3a 的导入命令写入 dev 来源对照 | P3b 合并并部署 dev | one-ops 执行 |

P5 合并后 one-1a-par 开 F-1（编排调整 ③）。之后主线：R-6 → R-2a → R-2b（并入 Reason 500、待确认授权申请部分唯一、工时单员工 + 工作日唯一）→ R-5 → R-4（Codex 试点）。

## 5. 裁定登记（负责会话，one-coordinator 均已知悉或同意）

- **R-1b**：拆分 P0–P6；C3 P2 只改 `account_roles.user_id` 类型；C4 漏项补反向测试；C5 复用 actorType；C6 法人代码发号后禁改已实现（特征测试在 P3a）；C7 新枚举 `ExternalSource`；C8 P3b–P6 间不可用。P0 只改编号计数器（日历适用范围并入 W2 DA-17）。
- **P3a**：RULING-P3A-1（核算编码只由人工登记 CHECK + 夹具等价调整）；同日更正（用户「允许同日更正 (Recommended)」）与 ADR-0033 第 2 项第 1 条字面一致，不需新 ADR；§14 例外 `ConfirmSourceReference`（one-coordinator 接受）。
- **D1-1**：RULING-D1-1-1..8（ALERT 并入、工厂只在 integration、交互 ≤ 3 s / 后台 ≤ 30 s、隔离测试随改、moduleOf、去 sleep 与镜像单一来源 `test-images.txt`（mssql 按摘要固定）、后台每语句超时 + ShedLock 兜底、`SeeyonViewsMssqlIT` 读清单）；复审后超时顺序 lock < query < socket、交互获取 1 s + 查询 2 s。
- **P1**：RULING-P1-1（Error Prone 对 record 比较失效，字节码守护 + 反向测试兜底，ID 保持 record）；RULING-P1-2（构造器守护白名单放行 4 处来源 ID 翻译，P3b 删除）；用户「接受并补条款（推荐）」（GPL-2.0 + Classpath Exception 仅编译期工具，intent `2026-10-15-112158`）。
- **ADR37-1**：RULING-ADR37-1..11——基线写死、容器复现 RDS；保留 (d) `IS_SRVROLEMEMBER` 全 0；class 4 与拥有角色按 (e)；拒绝信息带 (a)–(g) 标记；凭据列 review 默认拒绝、逐列放行；D1-1 夹具改写；每日重检可注入 Clock；启动时外部库不可达即拒绝启动（已采纳 ADR，降级启动须新 ADR）；VIEW SERVER STATE 反例改 DENY；标记 (g) 新增、登录级授权并入 (d)、系统库只经 guest 并入 (f)；夹具贴近 RDS 后 (d) 收紧为恰好 CONNECT SQL。M-1 用户「采纳建议（推荐）」：确认违规立停、判定不了连续 3 次再停、成功一次清零、启动判定不了仍拒绝（intent `2026-10-15-120940`）。

## 6. 只读核实命令（转 one-1a-par，#163 合并后开）

one-coordinator 定的口径：进签名发布包；默认只读，不启动 Web、不跑迁移、不起调度；口令只从 ECS 的 env 文件读，不接受命令行参数传口令；输出项目标记 (a)–(g) 与错误号，不输出数据或数据库消息原文；退出码区分「通过 / 违规 / 判定不了」；合并后告诉 one-ops 与 one-coordinator 安排 rc.2（C-14 的前提）。

建议实现：复用 #163 的 `platform.integration.infrastructure.connection` 中的 `ReadOnlyBaseline`（判定，含 (a)–(g) 与「确认违规 / 判定不了」两类结果）、`JdbcReadOnlyCatalog`（查询）与 `ExternalConnectionFactory`（连接与超时），不另写一套判定；入口参照现有运维命令（`identity-operations` 档案与 `OperationsCommands` 写法；注意 F-12 预记：该类档案须设 `management.server.port=-1`，避免与运行中的服务抢管理端口）。独占构件：只读核实命令的入口类与档案配置；**不得改** `ReadOnlyBaseline` / `JdbcReadOnlyCatalog` 的判定（要改先找结构重构主线负责会话）。验收：对容器夹具（`ReadOnlyBaselineFixture`）的基线账号退出码为「通过」，对任一反例为「违规」并输出对应标记，对不可达库为「判定不了」；输出不含连接串、口令与消息原文。

## 7. 登记的后续项（下一批小卡，详见 `pending-notes.md`）

- #119 Low 4 条（build_all 说明、浅克隆回退、push 自比、`--base ""`）。
- #134 余项：端口相同时 prometheus 须登录的测试、注释措辞、sanity 断言管理端口 ≠ 主端口、IT 收紧为只接受 401。
- #139 Low-3 / Low-4（列表准入与可登记类别同义；不按状态的列表无索引）。
- #153 Low：Flyway / MyBatis 建连类、超时字面量检查、`jdbc.datasource` 全禁的误伤、最坏 4 s 写明、acquire 下限、旧键静默忽略。
- #162 余项：构造器守护漏抓三元 then 分支与 long 局部变量等（M）、白名单粒度（L）；守护 14 只拦相等比较（偏差，已单列）。
- O-1 可观测性：`refused()` 记业务消息、非 HTTP 路径（定时、监听、Flowable）记完整消息与堆栈、`JdbcLegacyInbox` 失败日志是否 ALERT。
- ADR37-1 M-2 推迟：基表 `dbo.` 限定名（随 R-1b-0）、日志不带 `getMessage()` 扫描（`ExternalFailure` 合并后作守护卡）、用途边界 ArchUnit（依赖读取列登记的「用途」字段）。
- `precheck.sh --run-backend` 的 legacy_it 分支未实跑；守护 `TestIsolationGuardTest` 对验收目录的映射缺陷（P3a 测试以 `set search_path` 回避）。
- A8 同步范围（`MEMBER_SCOPE`）不看删除 / 启用标志，已删除或停用人员进入快照——随 R-1b-0 / DA-3。
- 只追加表 `updated_at` 统一处理（保留并加注释；统一去掉由 one-coordinator 定）。

## 8. 经验与注意

- **子代理工作区**：本会话中途起，运行环境不允许子代理写负责会话 worktree 以外的目录。一律 `isolation: worktree`，子代理 `git checkout -B <本地名> origin/<PR 分支>` 提交，负责会话核对快进后 `git push origin <sha>:refs/heads/<分支>`。
- **跨会话转达的用户原话先核实**：选择题的答复不会以文字出现在会话记录里，搜不到时向 one-coordinator 要问答原文与 intent 文件名，读 intent 片段核对后再执行。
- **验收测试改动**：实现代理不得改验收目录；测试有误由测试代理按 `Acceptance-Ruling: RULING-…` 改（trailer 与 `Co-Authored-By` 放在同一段末尾才被识别）。
- **先红测试推上 PR 会让 CI 变红**：Auto-fix 会报；先确认失败用例正是先红的那批，再等实现，不改测试。
- **合并队列**：strict 串行，排队期间不要往排队分支推新提交；与 main 冲突时自己合并 main 解冲突（非生成文件），报 one-coordinator 重新入队。
- **容器**：Testcontainers 本机临时容器已获用户长期授权（#160，镜像须登记在 `backend/architecture/test-images.txt`）；其他容器、常驻容器、对外调用照旧先问。
- 每份复审平均要两轮；派复审时把「只核对、不起容器」写明，避免复审代理意外起容器。
