---
卡号: R-1b-P5
状态: 完成
前置: R-1b-P4
---
# 派工卡 R-1b P5：收口——审计对象拆列、事件信封 ID 改 UUID 并删 source，守护收尾

## 1. 卡号与类别
- 卡号：R-1b P5（R-1b 的最后一个子 PR；并入 DA-4 的审计部分、ADR-0033 第 7 项第 1、2、6 条与守护 1、2、8、ADR-0039 第 5 条第 1 款（B3）、G-A1 收尾；**随带改名**：`ApprovalTaskOverdue` → `ApprovalTaskBecameOverdue`、`TaskResulted` → `TaskHandled`（one-glossary-b2，#272 已合并）；**随带修复**：P2 复核 L-1、L-8，P2 复审 P2-L12，P3b 终审 Low-6，`LegacyShapeTest.APPROVED_AREAS` 两条过期路径，见第 3.6 节）
- 类别：结构性重构（含改名）。随带修复按 P4 先例单列（第 12 节 D-1）
- 批次与整合分支：Phase 1a 下一批 R-1b 线（`phase1a/NEXT_BATCH_PLAN.md` 7.11：R-1b-0a → P3b → P4 → **P5** → F-1）；单独一个 PR，直接合入 main
- 前置：P4（分支 `claude/one-1a-next-4-r1b-p4`）合并。本卡按 P4 分支 @ 20ec17f3 盘点（派卡时点名的 08fa91d4 之后，P4 分支已合入 main 含 #272）；P4 合并前开工的只能是测试代理，基线为 P4 分支；实现代理在 P4 合并后的 main 上开工，开工前核对第 3 节清单与 main 一致，不一致先报告
- 之后：F-1（one-1a-par）在 P5 合并后开；K-3b-4（audit 接入）排在 P5 之后，二者文件不相交可并行

## 2. 依据
- ADR-0033（已采纳）：第 1 项第 8 条（豁免只剩 5 张框架表，登记在 `primary-key-exemptions.txt`）、第 9 条（聚合 ID 在构造时生成）、第 10 条（不依赖 ID 次序，游标与归档按「时刻列 + 主键」）；第 6 项第 2 条（ID 对外为字符串）；第 7 项第 1 条（信封 `aggregateId`、`actorId` 与 `AggregateReference` 改 `UUID`，事件目录 `aggregateId` 声明 `format: uuid`）、第 2 条（`audit_entries.subject_key` 拆为 `subject_kind` + `subject_id uuid`，索引 `(subject_kind, subject_id, created_at)`；本人登录记录按 `USER_ACCOUNT` + 本人 `user_id` 查，取代 ADR-0027 第 3 项第 3 条的写法）、第 6 条（审计归档按时刻列、去整数哨兵）、第 7 条（ID 承载位置清单）；「需同步修改的守护规则」第 1、2、8 条
- ADR-0039（已采纳）：第 5 条第 1 款（B3：信封删除 `source`，含 `documentRef`；不改名为 `EventOrigin`，见「被否决的方案」；同步改 `asyncapi.yaml` 信封 schema 与 `EVENT_ANALYTICS_FIELDS`；ADR-0025「信封 `source.*` 落在业务表上」一项随之取消）；第 6 条（信封删 `source` 在 P5、P5 独占 `EventEnvelope`；老待办契约中性化可放生产后）
- 计划：`phase1a/NEXT_BATCH_PLAN.md` 7.2（P5 行；末段「P5 独占 `EventEnvelope` 与全部守护文件」）、7.8（落点 P5 的 L-1、L-8、P2-L12）、**7.11（以 7.11 为准）**：「先保 dev」顺序与 P5 守护收口的三处保留例外；第 8 节（F-1 前置改为 P5 + K-3；K-3b-4 在 P5 之后；第 42 行「新增守护类不改现有守护文件，视为与 P5 不冲突」）
- 卡：`phase1a/cards/R-1b.md`（§3 至 §9 逐子 PR 适用；文末修订 G-A1「P5 收尾」）；范本 `R-1b-P4.md`（第 13 节 RULING-P4-D6：`InboxItemResponse.taskId` 保留到 ADR-0039 第 6 条契约中性化；第 14 节改名边界与 RULING-P4-6）、`R-1b-P3b.md`
- 用语：#272（one-glossary-b2）G40–G42 检查与 `backend/glossary/code-term-baseline.txt`；`terms.yaml` 的 `task_handled`「任务已办理」（R3-AP-60，已批准）、`overdue`「已超时」、`audit_subject`「审计对象」（已批准，定义仍写「键形如 {type}:{id}」）、`numbering_counter`、`numbering_rule`
- 规范：SESSION_PLAYBOOK §5（开工前术语门禁、子代理模型）、§6.1、§12.2（基线开放期直接改基线，附预期表结构 diff）、§14、§14.1（D2、D6、D7、D16、D18）；CODING_STANDARD CS-1.8、CS-1.9、CS-1.10、CS-1.11、CS-2.20、CS-2.21、CS-2.24、CS-5.5、CS-5.6、CS-5.9；NAMING_STANDARD §2.4（`_kind` 为 text + CHECK、`_id` 为 uuid）
- one-coordinator 派卡说明（2026-10-16）：所有 ID 类查询参数非 uuid → 400 ProblemDetail 的用例，至少含 `LegacyNumberController` 的 `documentId`；随带类名改名的具体要求（第 3.5 节）

## 3. 范围与卡上文件

盘点基线：P4 分支 @ 20ec17f3（`git grep`）。数字为文件数，实施时以 P4 合并后的 main 为准。

### 3.1 表与列（改开放基线，不追加迁移）

`platform_audit`（`V20261014134000__platform_audit_baseline.sql`）：

| 表 | 最终形态 |
|---|---|
| `audit_entries` | **删** `subject_key`；**新增** `subject_kind text NOT NULL` + `ck_audit_entries_subject_kind`（取值见第 3.2 节第 4 条与 D-4）、`subject_id uuid`（可空性见 D-5；可空时加 `ck_audit_entries_subject_id`：「`subject_id` 为空」当且仅当类别属于无对象的几类）；索引 `audit_entries_subject_idx` 列改为 `(subject_kind, subject_id, created_at DESC)`，**名称不变**（C10，`ddl-naming-baseline.txt` 第 71 行照留）；只追加触发器不变 |
| `audit_archive_batches` | 按 D-7 定（推荐：新增 `first_entry_created_at timestamptz NOT NULL`、`last_entry_created_at timestamptz NOT NULL`，水位改为「时刻 + ID」） |

列注释：`subject_kind`、`subject_id` 用与 P4 `outbound_deliveries.subject_kind / subject_id` **同一句**：「对象类别 / 对象 ID：被记录或被投递的业务对象的类别与自有 ID」；删去「对象键：{类型}:{id}，如 approval-task:42」。`backend/architecture/archive-columns.txt` 的 `subject_key` 行改为两行；`data-access-registry.yaml` 中 `audit_entries` 若需 `fk_exceptions`（`subject_id` 是多态引用、无外键）随列补上，理由写「指向的对象由 `subject_kind` 决定，跨模块」。

**不改的表**（7.11 保留例外，到期改为拆分；`sync_run_held_separations` 主键按 RULING-P5-D12 本卡改）：`organization_workforce.sync_diffs.subject_key`（拆分 a 按最终形态在集成层新建）、`sync_runs` / `sync_diffs` 的旧约束与索引名（拆分 a）。

### 3.2 Java

1. **信封与草稿**（kernel，P5 独占）：`EventEnvelope.aggregateId`、`actorId`（D-2）与 `AggregateReference.aggregateId`、`EventDraft.aggregateId` 由 `String` 改 `UUID`；**删** `EventEnvelope.source`、`EventDraft.source` 与 `kernel/event/EventSource.java`、`SourceSystem.java`；`ActorType` 的类注释（「Migrated history is told by EventSource.system」）随改。`platform/eventing/infrastructure/ContextualEventEnvelopes.java` 随改（P2 时独占，现归 P5）。
2. **各聚合的 `aggregateId` 改为自己的 UUID**（14 个 `new EventDraft(` 所在文件）：
   - 已是 UUID 文本、只换类型：`Timesheet`、`LegalEntity`、`RegisteredEmployee`、`SyncRun`（含相关聚合 `OrgSnapshot`）、`EmergencyAdmin`、`AuthorizationRequest`、`Role`、`UserAccount`、`SourceReference`（含被取代对照的相关聚合）、`SourceReferenceRefusal`
   - **形状要改**（D-3）：`ApprovalRequest`（现为「单据类型:单据 ID」，改为 P4 的 `ApprovalRequestId`；`aggregateId()` 方法与日志、异常文案中的用法改用 `id()` 或单据引用）、`WorkCalendarYear`（现为 `key.asText()`，改为工作日历年度行 ID）、`LegalEntityAttribution`（现为「类别:对象 ID」，改为 `AttributionId`）、`NumberingRule`（现为单据类型名，改为 `numbering_rule_id`）。这三处的 ID 现由仓储在插入时生成（`JdbcWorkCalendarRepository:61`、`JdbcLegalEntityAttributionRepository`、`JdbcNumberingRuleRepository`），按 ADR-0033 第 1 项第 9 条改为聚合构造时向 `IdGenerator` 取得
   - `SubmitTimesheet.java:85` 的相关聚合 `new AggregateReference("NumberingRule", "TIMESHEET")`：按 D-6 改为真正同事务变化的对象及其 UUID
3. **审计订阅事件**：`platform/audit/application/AuditDomainEvents.java` 不再拼 `aggregateType + ":" + aggregateId`，改为 `(类别, aggregateId)`；类别由 `aggregateType` 按固定规则换算（`UserAccount` → `USER_ACCOUNT`），换算不到即失败并 ALERT，不回退为文本、不跳过（CLAUDE.md 第 6 条）。
4. **审计对象**：`platform.audit.api.AuditEntry` 的 `String subjectKey` 改为审计对象值对象（`AuditSubject`，已批准术语；类别枚举名见第 11 节）；`AuditRecord`、`AuditQuery`、`AuditSearch`、`AuditEntryView`、`AuditCsv`（导出表头 `subjectKey` 改为两列）、`JdbcAuditEntryRepository`、`JdbcAuditEntryQueries`（本人登录记录 :75 改按 `USER_ACCOUNT` + 本人 `user_id`）、`AuditArchiveJob.COLUMNS` 随改。生产方（`"user-account:" + …` 等文本拼接，main 中 36 个文件）全部改为构造审计对象：

   | 现文本前缀 | 类别 | 对象 ID |
   |---|---|---|
   | `user-account:`（19 处） | `USER_ACCOUNT` | 用户 ID |
   | `employee:`（13） | `EMPLOYEE` | 员工 ID |
   | `org-unit:`（9） | `ORG_UNIT` | 部门 ID |
   | `emergency-admin:`（4） | `EMERGENCY_ADMIN` | 用户 ID |
   | `role:`（2） | `ROLE` | 角色 ID |
   | `approval-task:`（2） | `APPROVAL_TASK` | 任务 ID |
   | `approval-request:`（2，`Retraction:95`、`StartApproval:72`） | `APPROVAL_REQUEST` | 审批请求 ID |
   | `sync-run:`（`RecordSyncRunRelease:36`） | `SYNC_RUN` | 同步运行 ID |
   | `legal-entity-attribution:<类别>:<ID>`（`CorrectLegalEntityAttribution:51`、`OwnAttributionGuard:57`） | 按 D-4 | 按 D-4 |
   | `request:<方法> <路径>`（`AccessDeniedAudit:86`、`PrivilegedRequestAudit:43`）、`operation:<类.方法>`（`AccessDeniedAudit:83`）、`login:<方式>`（`LoginAdmission:96`，未识别到人时） | 无对象的类别（D-5） | 空 |
   | 经事件（`AuditDomainEvents`）写入的各聚合 | 由 `aggregateType` 换算 | `aggregateId` |

   不属于审计对象、不改的文本键：咨询锁键 `"timesheet-create:"`（`JdbcTimesheetRepository:53`）、`"approval-start:"`（`JdbcApprovalRequestRepository:66`）——只在事务内取锁，不落库、不出接口（写进 ID 承载位置清单，D-9）；`SnapshotReconciler` 的 `"position:"` 等同步差异主体键（`sync_diffs.subject_key`，拆分 a）。
5. **审计接口**（契约先行，CS-1.10）：`AuditEntryResponse.subjectKey` 改为 `subjectKind`（具名枚举）+ `subjectId`（`format: uuid`，可空随 D-5）；`GET /api/v1/platform/audit-entries` 与 `:export` 的查询参数 `subjectKey` 改为 `subjectKind` + `subjectId`（uuid）；旧参数名由 `UnknownQueryParameters` 拒绝（400）。K-3b-4 之后要改的导出上限、过滤参数统一不在本卡。
6. **不依赖 ID 次序**（ADR-0033 第 1 项第 10 条，SQL `ORDER BY` 守护的现存违例）：
   - `AuditArchiveJob:117、134、136`：归档水位与取批按 `id` 排序（类注释已承认跨实例会漏）→ 按 D-7 改为「`created_at`, `id`」
   - `JdbcSourceReferenceQueries:97-99`：游标 `source_reference_id > :after` 与 `order by source_reference_id` → 改为 `(created_at, source_reference_id)` 游标（`cursor` 本是不透明字符串，契约不变）
   - `JdbcEmergencyAdminRepository:54` `order by user_id` → `order by created_at, user_id`（或说明为何不需要次序后去掉排序）
   - `ProcessArchiveJob:241` `act_hi_varinst … order by id_` → 加时刻列（`create_time_`），与同文件 :212、:221、:229 写法一致
7. **G-A1 收尾（后端）**：查询参数中的 ID（`audit-entries` 的 `actorId`、`onBehalfOfId`、新 `subjectId`；`audit-entries:export` 的 `actorId`、`subjectId`；`source-references` 的 `ownId`；`legacy-numbers` 的 `documentId`）与全部路径参数 ID 已是 `UUID` 并经 `CanonicalUuids`，本卡补齐用例（第 4 节第 7、8 条）。`TaskDetailResponse.initiator`（名称不以 Id 结尾、现无 `format`）按 D-10 处理。

### 3.3 契约、事件目录与前端
- OpenAPI：第 3.2 节第 5 条；重新生成 `frontend/packages/api`（`openapi.json`、`schema.ts`、`validators.ts`）
- 事件目录：`docs/modernization/events/build_asyncapi.py` 的信封 schema 删 `source`，`aggregateId`（含 `relatedAggregates[].aggregateId`）加 `format: uuid`，`actorId` 已是 uuid；`events/approval.yaml` 中「aggregateId 是 单据类型:单据标识，如 TIMESHEET:7」两处（:6、:800）改为审批请求 ID；重新生成 `asyncapi.yaml`
- `docs/modernization/proposals/EVENT_ANALYTICS_FIELDS.md`：删 `source` 行与第 5、6、8 条中 `source.*` 的说法（ADR-0039 第 5 条第 1 款点名同步改）；`DATA_SCORECARD.md:76`「即信封的 `source.documentRef`」一句删去括注（D-8）
- 前端（G-A1 收尾、P2-L12）：测试桩与夹具中的 ID 改为 uuid 形态，只改数据、不改断言含义与界面文字。15 个文件：`frontend/packages/core/src/testing/fixtures.ts`、`packages/core/src/approval/{model.test.ts,hooks.test.tsx}`、`packages/api/src/{operations.test.ts,http.test.ts}`、`apps/web/src/app/app-routes.test.tsx`、`apps/web/src/modules/{approval/pages/{inbox-page,task-detail-page}.test.tsx, numbering/pages/numbering-rules-page.test.tsx, session/pages/{login-callback-page,login-pages}.test.tsx, workforce/pages/org-units-page.test.tsx}`、`apps/h5/src/modules/{approval/pages/{inbox-page,task-detail-page}.test.tsx, session/components/session-gate.test.tsx}`；`sync-runs-page.test.tsx` 的 `subjectKey: '1001'` 属同步差异（拆分 a），不改。前端归属见 D-11

### 3.4 守护（P5 独占全部守护文件）
1. **`PrimaryKeyTypeGuardTest`**（ADR-0033 守护 1）：现已对复合与非 uuid 主键报错，但 ① 无主键的表直接放过（`t.primaryKey.isEmpty()`）→ 改为报错；② 框架表取自 `DdlNamingTest.FRAMEWORK_TABLES` → 改为读新文件 `backend/architecture/primary-key-exemptions.txt`（只允许 ADR 第 1 项第 8 条的 5 张表，每行写理由，过期行失败；`DdlNamingTest`、`RepositoryWriteGuardTest`、`TestIsolationGuardTest` 中 `FRAMEWORK_TABLES` 的用处改读同一文件或保留常量并断言两者一致，二选一，PR 写明）；③ 新增：主键与 `_id` 列不得由序列、`GENERATED … AS IDENTITY` 或数据库默认值生成（`_number` 序号列不受限，如 `legal_entity_attribution_corrections.correction_number`）；带 `effective_from_*` / `effective_to_*` 的表须有 uuid 主键与「自然键 + 区间」排他约束；`*_version_id` 列须引用版本行主键。`primary-key-type-baseline.txt` 的去留按 D-12
2. **去哨兵守护**（ADR-0033 守护 8 的 DDL 部分，新增，放 `DdlNamingTest` 或 `DataArchitectureTest`）：唯一约束、唯一索引、排他约束的表达式中不得出现 `COALESCE(…, <常量>)`；不得有存「类型:ID」文本的列（按列名 `subject_key`、`deduplication_key` 与注释含「{类型}:」两条认定，实现定细则）。例外只有 `organization_workforce.sync_diffs.subject_key`，写明理由与到期「拆分 a」，过期即失败
3. **`OpenApiExportTest`**（ADR-0033 守护 8 的 OpenAPI 部分）：`isIdName` 补上以 `By` 结尾的名字（ADR 原文「以 `Id` 结尾或以 `By` 结尾」）；`OTHER_ID_NAMES` **保留** `InboxItemResponse.taskId`（RULING-P4-D6，到期随 ADR-0039 第 6 条契约中性化），是否加 `SyncDiffResponse.subjectKey` 按 D-13；`OWN_ID_FIELDS` 加 `AuditEntryResponse.subjectId`、`parameter subjectId`，删 `AuditEntryResponse.subjectKey` 相关
4. **事件目录守护**（ADR-0033 守护 8 的事件部分，新增或并入 `EventCatalogueTest`）：信封 `aggregateId`、`relatedAggregates[].aggregateId`、`actorId` 为 `format: uuid`；信封不再有 `source`；各事件中 `x-type` 为类型化 ID 的字段为 `format: uuid`
5. **SQL `ORDER BY` 守护**（ADR-0033 守护 2 的 SQL 审查规则，新增）：`src/main/java` 中 SQL 文本的顶层 `ORDER BY` 不得只按主键或只按 `*_id` 列（须带时刻列或显式序号）；聚合函数内的 `ORDER BY`（如 `SyncRunRows:30` 的 `array_agg(… order by employee_id)`）不算；第 3.2 节第 6 条修完后例外清单为空
6. **系统操作主体目录只增不删**（P2 复核 L-1）：新增 `scripts/ci/system_actors_append_only.py`（写法参照 `migrations_immutable.py` 与 `ci_git.py` 的基线取法），以 `git show <基线>:backend/architecture/system-actors.txt` 校验基线中的每一行仍在；接入 `.github/workflows/ci.yml` 与 `scripts/precheck.sh`；带自测（删行失败、新增行通过、改名即「新增一行、旧行仍在」通过）。`backend/src/test/resources/architecture/system-actors.txt` 是测试副本，不在校验范围（实现核对后 PR 写明）
7. **`LegacyShapeTest.APPROVED_AREAS`**：删 `src/main/java/com/forcitis/one/platform/approval/infrastructure/legacy/`、`src/test/java/com/forcitis/one/platform/approval/infrastructure/legacy/` 两条（P3b 已搬到集成层，目录不存在）；加一条断言「`APPROVED_AREAS` 中每个目录都存在」，防再过期
8. **用语基线**（随带改名）：`backend/glossary/code-term-baseline.txt` 删 `G41 platform.approval ApprovalTaskOverdue event name not past tense` 行与第 3 行对应注释，`count 2` → `1`；`docs/modernization/glossary/term-rules-baseline.json` 中 `proposed/code-aggregates-events.yaml#approval_task_overdue:event_name` 一行若因改名消失则删除，`event_name` 的 `count 6` → `5`，`where` 文案去掉「另含代码类 ApprovalTaskOverdue…」一句
9. 按 D-14、D-15 决定是否新增：ArchUnit「聚合根 ID 在构造时赋值（仓储不生成聚合 ID）」、「除 `kernel.Uuids` 外生产代码不调用 `UUID.randomUUID`」

### 3.5 随带类名改名（one-glossary-b2，#272 已合并）
- `backend/src/main/java/com/forcitis/one/platform/approval/events/ApprovalTaskOverdue.java` → `ApprovalTaskBecameOverdue.java`；`TaskResulted.java` → `TaskHandled.java`；事件类型串 `platform.approval.ApprovalTaskOverdue` / `platform.approval.TaskResulted` 同步改
- 生产方与消费方：`platform/approval/domain/ApprovalRequest.java`、`platform/approval/infrastructure/notification/TaskNotifier.java`；测试 `ApprovalDeadlineIT`、`ApprovalIntegrationSupport`、`TaskLifecycleIT`、`ApprovalTaskStateMachineTest`（只改名，不改断言含义）
- 事件目录 `events/approval.yaml`（:179 描述中的 `TaskResulted`、:323–393、:478 起两个条目）与 `asyncapi.yaml`；`docs/modernization/architecture/ddd/aggregates.yaml:200、206` 与其生成图 `architecture/generated/4-tactical.svg`；`docs/modernization/audit/MUST_AUDIT_ACTIONS.yaml:87`（审计动作取自事件类型，随改）
- 用语：`backend/glossary/proposed/code-aggregates-events.yaml` 的 `approval_task_overdue` 条目改 id、en、snake、中文名（候选见第 11 节，one-glossary 定稿），`note` 去掉「是否改为 ApprovalTaskTimedOut 待确认」；删 `task_resulted` 条目（`terms.yaml` 已有 `task_handled`）；`backend/glossary/proposed/README.md` 中这两行随生成或手改同步；基线文件见第 3.4 节第 8 条
- 不改：已采纳 ADR 正文（ADR-0025 等）、`REIMAGINED_ARCHITECTURE.md`、`AI_NATIVE_SPEC.md`、`process/1a/*`、`rules/APPROVAL_DEADLINE_RULES.md`、intent、session-log、复审报告中的旧名——流程与规格文档由 one-rules / one-glossary 随用语写回改（D-16）
- 在途 Modulith 事件登记（`event_publication` 中按旧类名序列化的行）：dev 随库重建，不迁移

### 3.6 随带修复
1. **L-8**：`organization/workforce/application/SnapshotFreshness.cycleOf` 只取 cron 头两次触发的间隔，间隔不均的 cron 算短。改为取一个足够长窗口（如 7 天）内相邻触发的**最大**间隔；cron 在窗口内少于两次触发即启动失败（不回退默认值）。单元测试覆盖均匀、不均匀（如 `0 0 8,20 * * *`）、月度三种
2. **P3b 终审 Low-6**：`backend/src/test/java/com/forcitis/one/platform/integration/infrastructure/legacy/LegacyInboxAccessIT.java:36` 继承审批模块的 `ApprovalIntegrationSupport`（跨模块测试基类）。改为集成层自己的测试支撑（或 `acceptance` 下的共用支撑，若只用账号与会话），不再 import `platform.approval` 的测试类；`TestIsolationGuardTest` 若能顺带拦「测试类继承他模块测试基类」则加规则与负向样例，否则 PR 写明未加的理由
3. **P2-L12**：前端测试桩 `userId: '1001'` 等，见第 3.3 节
4. **`LegacyShapeTest.APPROVED_AREAS`**：见第 3.4 节第 7 条

### 3.7 允许改动的文件
- 开放基线：`backend/src/main/resources/db/migration/platform_audit/V20261014134000__platform_audit_baseline.sql`（只限 `audit_entries` 与 D-7 的 `audit_archive_batches`）
- `backend/src/main/java/com/forcitis/one/`：`kernel/event/**`；`platform/eventing/infrastructure/ContextualEventEnvelopes.java`；`platform/audit/**`；第 3.2 节第 2、4、6 条所列聚合、仓储与审计生产方（约 50 个文件，按 `git grep -E '"[a-z-]+:" *\+|new EventDraft\(|EventSource|aggregateId\(\)'` 为准）；`cost/timesheet/application/SubmitTimesheet.java`（与 D-6 涉及的 `platform/numbering/api` 文件）；`platform/approval/events/**`；`organization/workforce/application/SnapshotFreshness.java`
- 测试：受影响模块的单元与集成测试（非验收目录，约 50 个文件）及第 3.6 节；全部守护测试与 `backend/architecture/*`（守护登记文件）、`backend/glossary/**`、`docs/modernization/glossary/term-rules-baseline.json`
- `scripts/ci/system_actors_append_only.py`、`scripts/precheck.sh`、`.github/workflows/ci.yml`（只加第 3.4 节第 6 条一步）
- `docs/modernization/events/{build_asyncapi.py, approval.yaml, asyncapi.yaml}`、`architecture/ddd/aggregates.yaml` 与生成图、`audit/MUST_AUDIT_ACTIONS.yaml`、`proposals/EVENT_ANALYTICS_FIELDS.md`、`phase1a/DATA_SCORECARD.md`（一句）、ID 承载位置清单（D-9）
- `frontend/packages/api/**` 生成物；第 3.3 节所列前端测试文件
- 越界先停下报告，不先做后报

### 3.8 归属与不在 P5
- 本卡独占：`EventEnvelope`、`EventDraft`、`AggregateReference`、`ActorType`、`ContextualEventEnvelopes`；`platform_audit` 两张表；`platform.audit.api`；全部守护文件（含 `backend/architecture/*` 登记文件、`OpenApiExportTest`、`DdlNamingTest`、`PrimaryKeyTypeGuardTest`、`LegacyShapeTest`、`TestIsolationGuardTest`、CI 守护脚本）
- **不碰**：`OrgUnitId`（F-1 下沉 kernel，P5 不迁不改形状）；`sync_runs`、`sync_diffs` 及同步代码（拆分 a / b；`sync_run_held_separations` 只改主键形状，RULING-P5-D12）；`account_roles`、`role_permissions` 除主键形状外的改动（W2，ADR-0034）；收件人 `List<UserId>`、`WecomUserId`（DA-10）；审计导出上限、去 `count(*)`、删 `AuditQuery.MAX_LIMIT` 与 `QUERY_INVALID`、本人登录记录分页（K-3b-4）；`OperationsCommands` 的 `UUID.randomUUID()`（OPS-CID）；审批列表统一与 `:count`（K-3b-3）；BPMN 计时器守护（FLW-2）；老待办契约中性化（ADR-0039 第 6 条，生产后）；`ddl-naming-baseline.txt` 中除本卡删除的列所带行以外的改名（R-2a）；时钟默认值（R-4）

## 4. 验收测试（由测试代理先写，§6.1 第 8 条）
- 测试代理：`code-modernization:test-engineer`，**sonnet**，先于实现提交，提交说明带 `Acceptance-Test-Author: test-engineer`；术语门禁未定稿的名字先按第 11 节候选写，定稿后改名再交实现
- 路径：`backend/src/test/java/com/forcitis/one/acceptance/platform/audit/`、`…/acceptance/platform/eventing/`、`…/acceptance/platform/approval/`；契约类放 `acceptance/` 根（同 `ApprovalIdContractTest`）
- 测试名（`@DisplayName` 以 `R-1b P5` 与依据编号开头）：
  1. `R-1b P5 ADR-0033 第 7 项第 2 条（列断言，写法同 UserIdColumnsIT）`：`audit_entries` 无 `subject_key`；`subject_kind` 为 text 带 CHECK、`subject_id` 为 uuid；索引 `audit_entries_subject_idx` 列为 `(subject_kind, subject_id, created_at)`；列注释为第 3.1 节那一句
  2. `R-1b P5 ADR-0033 第 7 项第 2 条 / ADR-0027 第 3 项第 3 条：员工查本人登录记录只按「用户账号 + 本人用户 ID」取`——本人的登录成功、失败、登出都在；他人的不在；另造一条类别不同但对象 ID 恰为本人用户 ID 的条目，不在结果中；动作与字段白名单不变
  3. `R-1b P5 审计订阅事件：条目的对象类别由事件聚合类型换算、对象 ID 等于信封 aggregateId`（审批、工时、角色、法人归属各一例）；聚合类型无法换算时不写条目、失败并 ALERT（日志断言），不写成文本
  4. `R-1b P5 ADR-0033 第 7 项第 1 条：事件信封的 aggregateId 与 relatedAggregates 的 ID 都是 UUID`——走一遍提交工时 → 审批通过、法人归属维护、工作日历维护、编号规则确认，捕获的每个事件 `aggregateId` 为规范 uuid；审批请求事件的 `aggregateId` 等于审批请求 ID（不含单据类型）；工作日历、法人归属、编号规则事件的 `aggregateId` 等于各自表的主键
  5. `R-1b P5 ADR-0039 第 5 条第 1 款：事件信封不再有 source`——序列化后的信封 JSON 无 `source` 键；`asyncapi.yaml` 信封 schema 无 `source`，`aggregateId` 为 `format: uuid`
  6. `R-1b P5 ADR-0033 第 1 项第 9 条：工作日历年度、法人归属、编号规则的 ID 在构造时取得，仓储不生成`（写法同 RULING-P4-3：ID 出自注入的 `IdGenerator`，取号调用栈不在 `infrastructure.persistence`）
  7. `R-1b P5 G-A1：ID 类查询参数不是规范 uuid 时答 400 ProblemDetail`——`GET /api/v1/platform/legacy-numbers?documentId=`（**必含**）、`audit-entries` 的 `actorId`、`onBehalfOfId`、`subjectId`，`audit-entries:export` 的 `actorId`、`subjectId`，`source-references` 的 `ownId`；取值 `42`、`not-a-uuid`、大写 uuid、nil uuid；断言状态 400、`application/problem+json`、`errorCode` 为 `PLATFORM_WEB_REQUEST_INVALID`，不是 200 空列表、404 或 500
  8. `R-1b P5 G-A1（通用）：OpenAPI 中每个 format: uuid 的路径参数与查询参数，传非 uuid 都答 400`——从导出的 OpenAPI 逐个生成请求（GET 直接发；写操作带合法的 `Idempotency-Key` 与 `If-Match`），以文档驱动，新接口自动纳入
  9. `R-1b P5 G-A1 / ADR-0033 第 6 项第 2 条（契约）`：`AuditEntryResponse` 有 `subjectKind`（枚举）与 `subjectId`（`string/uuid`），无 `subjectKey`；查询参数 `subjectKey` 已不存在（传入答 400）；`OTHER_ID_NAMES` 只含 D-13 定下的项；以 `By` 结尾的属性（`requestedBy`、`releasedBy`、`confirmedBy`、`financeConfirmedBy`）为 `string/uuid`；生成的 `validators.ts` 中这些字段不是数字（RULING-P4-2 口径）
  10. `R-1b P5 ADR-0033 第 1 项第 10 条：审计归档不依赖 ID 次序`（D-7 采纳时）——注入**递减**的 `IdGenerator`、固定 Clock 推进，写入若干条后归档，全部到期条目都进批次、对账无差异
  11. `R-1b P5 ADR-0033 第 1 项第 10 条：来源对照列表翻页不依赖 ID 次序`——递减 ID 写入多条，按页取完无遗漏、无重复，次序为创建时刻
  12. `R-1b P5 随带改名：审批任务超时与任务办理事件以新名发布`——事件目录与发布的事件类型为 `platform.approval.ApprovalTaskBecameOverdue`、`platform.approval.TaskHandled`；旧名不在事件目录、`MUST_AUDIT_ACTIONS.yaml` 与审计动作中；超时提醒卡与工时回写照常发生
  13. ★ `R-1b P5 ADR-0033 第 3 项第 3 条：「本人」闸门在信封与审计改形后不变`——`ApprovalAccountsIT`、`RetractionInitiatorAccountsIT`、`AssigneeInputUuidIT` 与 P2 的反向测试原样重跑，断言不变
- 守护的自测（CS-5.9 注入负向样例与反例）由实现代理写在各守护测试内，不放验收目录：无主键表、复合主键、text 主键、`GENERATED … AS IDENTITY` 的 `_id`、默认值生成的主键、无排他约束的区间表、过期豁免行；`COALESCE(…, 0)` 唯一索引、新的 `*_key` 类型:ID 列；`order by id` 被拦、`order by created_at, id` 与 `array_agg(… order by …)` 放过；系统操作主体目录删行被拦
- **要按 Acceptance-Ruling 改的现有验收物**（测试代理改，实现代理不碰；编号 `RULING-P5-n` 由负责会话裁定）：
  - `acceptance/EmployeeIdContractTest.java:50、139-145`（`OTHER_ID_NAMES_AFTER_P4` 精确等于 `{InboxItemResponse.taskId}`）与 `acceptance/ApprovalIdContractTest.java:125-141`：若 D-13 加入 `SyncDiffResponse.subjectKey`，含义随之放宽一项，须裁定并在 PR 单列
  - 引用 `subjectKey`、`aggregateId`、`EventSource` / `SourceSystem` 的验收：`acceptance/platform/eventing/EventEnvelopeActorIT.java`、`acceptance/platform/identity/{OwnRolesIT, SessionPurgeClockIT, AccountEntriesConcurrencyIT}.java`、`acceptance/platform/integration/EventPayloadScalarIdsIT.java`、`acceptance/platform/numbering/LegacyNumberSourceIT.java`、`acceptance/SourceTypeBoundaryTest.java`（类注释「kernel's SourceSystem … goes with P5」与 :247–255 的反例写的是临时工程里的假类，删真类后只需改注释，断言不变）——只改构造与读取方式，不改断言
- 实现方认为验收测试有误：停下报告，由复审裁定（`Acceptance-Ruling: RULING-P5-n`），不自行修改

## 5. 执行者与模型
- 测试代理：`code-modernization:test-engineer`，**sonnet**
- 实现：子代理，**sonnet**；按模块分提交，建议顺序：① kernel 信封（`aggregateId` / `actorId` 改 UUID、删 `source`）与 14 个聚合、三处构造时取 ID、`SubmitTimesheet` 相关聚合、事件目录与 `asyncapi.yaml`；② audit 拆列（DDL、api、全部生产方、web、CSV、归档列、本人登录记录）与 OpenAPI、前端生成物；③ 随带改名；④ `ORDER BY` 违例修复（含 D-7 归档）；⑤ 守护（第 3.4 节）；⑥ 随带修复与前端测试桩；⑦ ID 承载位置清单（D-9）。中间提交尽量可编译，不能时在提交说明注明；PR 末提交全绿
- 工作区：独立 worktree（`Agent` 的 `isolation: worktree`，计划 7.9），分支 `claude/one-1a-next-4-r1b-p5`（以负责会话命名为准），基线为 **P4 合并后的 main**；开工前确认 P4 已合并，否则停下报告
- 首轮复审：**opus 一次**，推理强度 high（§14、§14.1 全部条目 + 第 9 节专查项）
- 修后复核：**sonnet**（Blocker / High 的修复仍须 opus 复核）
- 不交 Codex：信封与守护是全仓共享构件，判断类改动多

## 6. 禁止事项
- 不改卡外文件；不顺手重构、改名或统一风格；保留的约束、索引名字一律不改（C10，含 `audit_entries_subject_idx`）
- 不改业务语义：审计动作白名单、本人登录记录的动作与字段白名单、审批流转与通知、事件发布时机照旧；改名只改类名与事件类型串
- 不碰第 3.8 节所列他卡对象，尤其 `OrgUnitId`、同步表（除 RULING-P5-D12 的主键改法）
- 不依赖 ID 次序（CS-2.21）；生产代码除 `kernel.Uuids` 外不新增 `UUID.randomUUID`
- 守护例外只减不增：除 `sync_diffs.subject_key`（拆分 a）、`InboxItemResponse.taskId`（RULING-P4-D6）与 D-12、D-13 裁定的项外不新增例外行，每条写理由与到期条件
- 不为兼容旧信封、旧审计键、旧事件名留回退分支（dev 随库重建）；审计对象类别换算不到即失败并告警，不写成文本
- 不放宽、不删除断言；不改验收测试目录
- Testcontainers 临时容器（镜像限 `backend/architecture/test-images.txt`）可直接跑；其他容器、常驻容器、对外调用、生产取证先问；不建账号
- 不打印、不写入密钥；测试密钥运行时生成

## 7. 预期表结构 diff（SESSION_PLAYBOOK §12.2）
- 方法：两个空库分别执行改动前（P4 合并后的 main）、后的基线，`pg_dump --schema-only`（带 / 不带权限）对比，种子另做 `--data-only` 对比
- 预期差异只能是：
  - `platform_audit.audit_entries`：删列 `subject_key`；新增列 `subject_kind text NOT NULL`、`subject_id uuid`（可空性按 D-5）；新增约束 `ck_audit_entries_subject_kind`、（D-5 可空时）`ck_audit_entries_subject_id`；索引 `audit_entries_subject_idx` 列改为 `(subject_kind, subject_id, created_at DESC)`，名称不变；`audit_entries_created_idx` 换为 `ix_audit_entries_created_at_id (created_at, id)`（one-data M-1）；列注释
  - （D-7 采纳时）`platform_audit.audit_archive_batches`：新增 `first_entry_created_at timestamptz NOT NULL`、`last_entry_created_at timestamptz NOT NULL` 与列注释；`uk_audit_archive_batches_last_entry_id`、`ck_audit_archive_batches_range` 是否改写由实现按新水位定，PR 逐项列出
- 不得出现的差异：`audit_entries`、`audit_archive_batches` 与 RULING-P5-D12 三张表主键改法以外任何表的变化（含 `sync_runs`、`sync_diffs`）；保留约束、索引、触发器的改名或删除；`audit_entries` 其他列的类型、默认值、可空性变化；种子数据变化

## 8. 交付物与自查
- 交付物：基线 DDL；kernel 信封与 14 个聚合；审计拆列（api、生产方、web、CSV、归档）；`ORDER BY` 违例修复；随带改名；守护（第 3.4 节）与 `primary-key-exemptions.txt`；随带修复；OpenAPI、事件目录、`asyncapi.yaml`、前端生成物与测试桩；`EVENT_ANALYTICS_FIELDS.md`；ID 承载位置清单（D-9）
- **守护文件变动（PR 单列）**：`PrimaryKeyTypeGuardTest`、`primary-key-exemptions.txt`（新）、`primary-key-type-baseline.txt`（删除，RULING-P5-D12）、`DdlNamingTest` / `DataArchitectureTest`（去哨兵）、`OpenApiExportTest`（`isIdName`、`OTHER_ID_NAMES`、`OWN_ID_FIELDS`）、事件目录守护、`ORDER BY` 守护（新）、`system_actors_append_only.py`（新）与 `ci.yml` / `precheck.sh` 接入、`LegacyShapeTest.APPROVED_AREAS`、`TestIsolationGuardTest`（若加）、`archive-columns.txt`、`data-access-registry.yaml`、`code-term-baseline.txt`、`term-rules-baseline.json`
- 自查：`scripts/precheck.sh <基线>` 全部 PASS 或有理由的 SKIP，输出贴进报告与 PR「precheck.sh 输出」；`pre-commit install`；报告前 gitleaks 8.30.1 扫全历史；`python3 docs/modernization/check_legacy_name.py`
- 受影响模块测试：precheck 给出的命令；交付前全量 `scripts/ci/backend_verify.sh` 与前端 `pnpm verify`
- PR 正文单列：预期与实际表结构 diff；改动的断言 / 测试及理由（含 RULING-P5-n、改名带来的测试改动）；守护文件变动与每条例外的理由和到期；第 12 节各点的落法；「流程与规格文档中的旧事件名交 one-rules / one-glossary」；「`audit_subject` 词条定义待 one-glossary 改」
- 报告分开写「已验证」与「未运行」

## 9. 复审要求（§6.1 第 4、11 条）
- 首轮：opus 一次（high）；Blocker / High 修后由 opus 复核，合并前修完
- Medium / Low：执行者补测试、CI 通过即关闭，修后复核用 sonnet，负责会话抽查；不阻塞合并的登记进下一批
- 复审清单：SESSION_PLAYBOOK §14、§14.1（D2 键与约束、D6 引用、D7 版本引用、D16 自有标识、D18 DDL 原则）；CODING_STANDARD CS-1.11、CS-2.20、CS-2.21、CS-2.24、CS-5.5、CS-5.6、CS-5.9
- 专查：表结构 diff 只含第 7 节声明项；每个事件的 `aggregateId` 是该聚合自己的主键（无「类型:ID」文本残留，`git grep '":" *+'` 抽查）；信封与事件目录无 `source`；审计无文本对象键、本人登录记录查询不能经类别不同的同值 ID 越界；守护的例外清单只有第 6 节允许的项且都有到期；新守护各有注入负向样例；`ORDER BY` 不只按主键；`OrgUnitId`、`sync_runs` / `sync_diffs` 未动，D-12 三表只改主键形状；改名后事件目录、`aggregates.yaml`、`MUST_AUDIT_ACTIONS.yaml`、用语基线一致

## 10. 报告去向
- 完成、部分完成或阻塞，都向负责会话 one-1a-next-4，抄 one-coordinator（SESSION_PLAYBOOK §4 第 7 步）
- PR／卡／交接记录（消息按 TEAM-53 四节点及需动作事件送达，不另报号）：分支、改动文件（按模块）、验证结果（已验证 / 未运行）、precheck 输出、预期与实际表结构 diff、未完成项、需要用户决定的事；合并后通知 F-1（one-1a-par）与 K-3b-4 的执行者开工，通知拆分 a / b 的负责会话：三处保留例外的到期由它们删除

## 11. 本卡新词（开工前术语门禁，SESSION_PLAYBOOK §5）

查过：`docs/modernization/glossary/terms.yaml`（`audit_subject`、`task_handled`、`overdue`、`numbering_counter`、`numbering_rule`、`document_number`）与 `backend/glossary/proposed/*.yaml`（`code-aggregates-events.yaml` 的 `approval_task_overdue`、`task_resulted`；`platform-integration.yaml` 的 `delivery_purpose`），中英文都查重；`subject_kind`、`subject_id`、`AuditSubjectKind`、`ApprovalTaskBecameOverdue`、`login_attempt`、`numbering_counter_id` 在两处均未登记。

**业务用语**：

| 中文 | 标识 | 说明 | 状态 |
|---|---|---|---|
| 任务已转为超时（候选；或保留「任务已超时」） | `approval_task_became_overdue` / `ApprovalTaskBecameOverdue`（取代 proposed `approval_task_overdue`） | 领域事件以过去式业务动词结尾（G41）；定义沿用原条目 | **待 one-glossary 定稿**（中文名） |
| 审计对象 | `audit_subject` / `AuditSubject`（已批准，value_object） | 定义「键形如 {type}:{id}」须改为「对象类别 + 对象 ID」 | **交 one-glossary 改定义** |
| 审计对象类别 | `audit_entries.subject_kind`；类型名候选 `AuditSubjectKind`（kind state，bc platform.audit） | 取值候选：`USER_ACCOUNT`、`EMPLOYEE`、`ORG_UNIT`、`EMERGENCY_ADMIN`、`ROLE`、`APPROVAL_TASK`、`APPROVAL_REQUEST`、`SYNC_RUN`、`LEGAL_ENTITY`、`LEGAL_ENTITY_ATTRIBUTION`、`TIMESHEET`、`WORK_CALENDAR_YEAR`、`NUMBERING_RULE`、`SOURCE_REFERENCE`、`SOURCE_REFERENCE_REFUSAL`、`AUTHORIZATION_REQUEST`，及 D-5 的无对象类别；与 P4 `outbound_deliveries.subject_kind` 同名同义（注释同一句） | 待 one-glossary |
| （无对象的审计类别） | 候选 `REQUEST`（受访问控制的请求）、`OPERATION`（受方法级授权的操作）、`LOGIN_ATTEMPT`（未识别到人的登录尝试） | 只在 D-5 选「可空」时需要 | 待 one-glossary |

**技术标识（交 one-glossary 预判；纯技术词由它直接定）**：

| 中文 | 标识 | 说明 | 状态 |
|---|---|---|---|
| 审计对象 ID | `audit_entries.subject_id` | 与 P4 同名同义 | 待预判（预计复用） |
| 归档批次首条 / 末条时刻 | `audit_archive_batches.first_entry_created_at`、`last_entry_created_at` | 只在 D-7 采纳时 | 待预判 |
| 编号计数器 ID（类型名） | 候选 `NumberingCounterId`，或 numbering api 交出号码时一并给出的记录类型名 | 只在 D-6 选计数器时需要；`numbering_counter` 已批准 | 待预判 |
| 主键豁免清单 | `backend/architecture/primary-key-exemptions.txt` | ADR-0033 已定名 | 无需登记 |

负责会话把上表交 one-glossary；**定稿前不派实现代理**（测试代理可先按候选名写，定稿后改名再交实现）。`task_resulted` 删除、`approval_task_overdue` 改名属 #272 已交的事项，按第 3.5 节执行，不另走门禁。

## 12. 需要负责会话决定的点

| # | 问题 | 建议 |
|---|---|---|
| D-1 | 本卡是结构性重构，第 3.6 节 L-8 等属缺陷修复，§6.1 第 9 条要求三类分批 | 按 P4 第 3.6 节先例并入、PR 单列（P5 独占全部守护文件，另开小卡会与 P5 抢文件）；L-8 若负责会话认为应等「首次改同步 cron 的卡」则移出（7.8 允许两处落点） |
| D-2 | ADR-0033 第 7 项第 1 条要求 `actorId` 也改 `UUID`；派卡说明只列 `aggregateId` 与 `AggregateReference` | 一并改（P2 起 `actorId` 已全是 uuid 文本，系统主体为 UUIDv5；只换类型，`AuditDomainEvents` 少一次解析）；不改则在 PR 写明并登记余项 |
| D-3 | `ApprovalRequest`、`WorkCalendarYear`、`LegalEntityAttribution`、`NumberingRule` 的 `aggregateId` 不是自己的主键，后三者的 ID 由仓储插入时生成 | 一律改为聚合自己的 UUID，后三者改为构造时向 `IdGenerator` 取（ADR-0033 第 1 项第 9 条）；`SyncRun` 的 ID 也由仓储生成，但同步在拆分 a / b 重做，本卡不改，登记为 D-14 守护的到期例外 |
| D-4 | 法人归属的审计对象：访问拒绝时法人归属记录可能还不存在，事件写入时有 `AttributionId` | 访问拒绝（`CorrectLegalEntityAttribution:51`、`OwnAttributionGuard:57`）记被归属的对象（`ORG_UNIT` / `EMPLOYEE` + 其 ID）；经事件写入的记 `LEGAL_ENTITY_ATTRIBUTION` + `AttributionId`。备选：都记被归属的对象（查询更直观，但事件条目要另取对象，`AuditDomainEvents` 不再通用） |
| D-5 | 访问拒绝、管理员模式请求、未识别到人的登录失败没有业务对象，现存「request:GET /…」「operation:类.方法」「login:方式」 | `subject_id` 可空，加 CHECK「为空当且仅当类别是 `REQUEST` / `OPERATION` / `LOGIN_ATTEMPT`」；路径与方法名写进 `detail`（如「access refused: GET /api/v1/…」），不另加列。备选：新增结构化列（如 `resource_text`），要新词与 `archive-columns.txt` 行，查询更好用；安全排查若需按路径检索选备选 |
| D-6 | `SubmitTimesheet` 把编号规则记为相关聚合，ID 写的是单据类型名；同事务真正变化的是编号计数器行 | numbering api 的 `next` 交出号码时一并给出计数器 ID，相关聚合记 `("NumberingCounter", numbering_counter_id)`；api 形状变化先在 PR 写明（numbering 的 api 方法数不增）。备选：记 `("NumberingRule", numbering_rule_id)`，api 同样要给出规则 ID；或删去这条相关聚合（信封注释要求列出同事务变化的聚合，不推荐） |
| D-7 | `AuditArchiveJob` 按 `id` 取批、记水位，类注释已承认跨实例会漏归档（ADR-0033 第 7 项第 6 条点名） | 本卡改：取批按 `(created_at, id)`，批次加首末条时刻列，水位为「时刻 + ID」，对账按同一谓词计数；`SQL ORDER BY` 守护无此例外。备选：登记为守护例外，另开卡（会让新守护一出生就带例外） |
| D-8 | `source` 删除后，`EVENT_ANALYTICS_FIELDS.md`、`DATA_SCORECARD.md:76`、`ACL_BOUNDARY.md` 仍有说法 | 本卡只改 ADR-0039 点名的 `EVENT_ANALYTICS_FIELDS.md` 与 `DATA_SCORECARD.md` 一句括注；`architecture/proposals/ACL_BOUNDARY.md` 属 one-architecture，经 one-coordinator 转；已采纳 ADR 不改 |
| D-9 | ADR-0033 第 7 项第 7 条要求 R-1b 交「ID 承载位置清单」（主键、引用列、文本键、事件字段、OpenAPI 字段、会话、日志字段，每项改法或「不是标识」的结论），仓库中尚无 | 由本卡交付 `docs/modernization/phase1a/ID_CARRIERS.md`，能由守护生成的部分写明出处；咨询锁键、`sync_diffs.subject_key`、`InboxItemResponse.taskId`、`source_references.source_id` 等写结论与到期。备选：交 one-architecture 另做 |
| D-10 | `TaskDetailResponse.initiator` 是员工 ID，名字不以 Id / By 结尾，现无 `format: uuid`，守护按名字识别不到 | 只补 `format: uuid` 并在 `OpenApiExportTest` 按全名列入 `OWN_ID_FIELDS`（`TaskDetailResponse.initiator`、`TaskDetailResponse.assignee`、`TaskOverviewResponse.assignee`），不改名（改名是契约变更，随 K-3b-3 或 R3 改名卡） |
| D-11 | 第 3.3 节 15 个前端测试文件跨 approval、session、numbering、workforce 模块，前端日常归 one-1a-par | 只改测试数据中的 ID 形态，不改界面文字与断言，P5 直接改并告知 one-1a-par；若 one-1a-par 在途改同一批文件，改由其随手完成，P5 只改 `fixtures.ts` 与 `packages/api` 两处 |
| D-12 | ADR-0033 守护 1 写「`primary-key-type-baseline.txt` 在最后一个子 PR 删除」，但 P5 后仍有 `sync_run_held_separations`（拆分 b）与 `account_roles`、`role_permissions`（ADR-0034，W2）三行 | 保留该文件，只放这三行，文件头改为「R-1b 之后的到期例外」并每行写到期卡；框架表移到新的 `primary-key-exemptions.txt`。两文件语义分开：前者到期必删，后者长期。须 one-coordinator 认可此对 ADR 字面的偏离（拆分 b 顺延是 7.11 已接受的） |
| D-13 | 7.11 要求把 `sync_diffs.subject_key` 登记为 `OTHER_ID_NAMES` 例外，但 OpenAPI 守护现只认以 Id / Ids 结尾的名字，`SyncDiffResponse.subjectKey` 根本不被检查 | 守护的识别名单加上「类型:ID」文本键（`subjectKey`），`OTHER_ID_NAMES` 变为 `InboxItemResponse.taskId`（RULING-P4-D6）+ `SyncDiffResponse.subjectKey`（拆分 a），各写理由与到期。这会使 `EmployeeIdContractTest:50` 的精确断言失败，须 RULING-P5 由测试代理改。备选：不扩识别、只在 DDL 去哨兵守护登记例外（7.11 字面「OTHER_ID_NAMES / 去哨兵」二者其一即可满足） |
| D-14 | ADR-0033 守护 2 的 ArchUnit「聚合根 ID 在构造时赋值」尚无；P4 只做了审批部分 | 本卡加规则（仓储不得为聚合根生成 ID：`infrastructure.persistence` 中对 `IdGenerator.next()` 的调用只用于非聚合根的子行、版本行、日志行），`SyncRun`（拆分 a / b）登记为到期例外；工作量超出时登记余项，不阻塞 P5 |
| D-15 | ADR-0033 守护 2 的 ArchUnit「除 `kernel.Uuids` 外不调 `UUID.randomUUID`」尚无；`OperationsCommands:51` 由 OPS-CID 修，`ObjectStoreArchiveVault:61`、`OssObjectStore:85` 用于临时文件名与探针键（不是标识） | 本卡加规则；两处对象存储用法改经 `kernel.Uuids` 或登记为「不是标识」的例外；`OperationsCommands` 若 OPS-CID 未先合并，登记为到期例外（OPS-CID） |
| D-16 | 改名范围：除代码、事件目录、`aggregates.yaml`、`MUST_AUDIT_ACTIONS.yaml`、用语基线外，`REIMAGINED_ARCHITECTURE.md`、`AI_NATIVE_SPEC.md`、`process/1a/` 三份流程文档、`rules/APPROVAL_DEADLINE_RULES.md` 也有旧名 | 本卡不改这些文档，交 one-rules / one-glossary 随用语写回；已采纳 ADR、intent、session-log、复审报告是历史记录，不改 |
| D-17 | 审计对象类别从 `aggregateType` 机械换算，新增聚合时容易漏枚举值 | 加一条守护：事件目录中每个 `aggregate` 都能换算到审计对象类别枚举、CHECK 取值与枚举一致（放事件目录守护内） |

## 13. 负责会话裁定（one-1a-next-4，2026-10-16）
- RULING-P5-D1..D11、D13..D17：按第 12 节建议采纳。其中：D-2 `actorId` 一并改 UUID；D-5 `subject_id` 可空（CHECK：为空当且仅当类别为 `REQUEST` / `OPERATION` / `LOGIN_ATTEMPT`），路径与方法名写进 `detail`；D-7 审计归档本卡改为按 `(created_at, id)` 取批与记水位；D-9 本卡交付 `phase1a/ID_CARRIERS.md`；D-11 前端测试数据先问 one-1a-par 是否在途改同批文件，无冲突则 P5 直接改；D-13 守护识别名单加「类型:ID」文本键，`OTHER_ID_NAMES` = `InboxItemResponse.taskId`（RULING-P4-D6）+ `SyncDiffResponse.subjectKey`（拆分 a），`EmployeeIdContractTest` 精确断言由测试代理按 RULING-P5-1 改。
- RULING-P5-D12（2026-10-16 改判：one-architecture 判定原建议偏离 ADR-0033，one-coordinator 撤回认可）：
  - `account_roles`、`role_permissions` 在 P5 改为 UUIDv7 单列主键，原主键改为命名唯一约束（ADR-0033 第 1 项第 2 条「R-1b 合并时若两张旧表仍在，同样按本条改，不登记豁免」；CS-1.9 当期做对）。
  - `sync_run_held_separations` 在 P5 照改：加 UUIDv7 单列主键，原复合主键 `(sync_run_id, employee_id)` 改为命名唯一约束。拆分 b 后该表改造为 workforce 待放行离职表时再改一次（用户「先保 dev」已接受同步表可能改两次，intent `2026-10-15-214511`），不构成阻碍，不推迟。
  - `primary-key-type-baseline.txt` 按 ADR-0033 守护 1 在 P5 删除（自有表无基线行）；框架长期豁免（ADR-0033 第 1 项第 8 条的 5 张表）放进 `primary-key-exemptions.txt`，每行写理由，文件头写落地记录，只减不增、新增须报 one-coordinator。
  - 落地记录写在本卡与 `ID_CARRIERS.md`，ADR-0033 正文不动。
  - 第 3、7 节中与原 D-12 建议相关的表结构项（三张表主键改法）以本条为准；预期表结构 diff 随之增加三张表的代理主键列与唯一约束（名称以 `DdlNamingTest` 与术语门禁为准）。
- 开工条件：第 11 节新词经 one-glossary 定稿；D-12 已认可。体量若超出 sonnet 能力（实现中途），停下报告，可按「信封与审计」「守护与 G-A1 收尾」拆两个 PR，不缩小范围。
- 新词定稿（one-glossary，2026-10-16）：`ApprovalTaskBecameOverdue` 中文名保留「任务已超时」，id / snake `approval_task_became_overdue`；`audit_subject` 定义已由 one-glossary 改（「被审计的业务对象，由对象类别与对象 ID 共同标识；没有对象的审计类别对象 ID 为空」）；**审计对象类别 `AuditSubjectKind`（kind state，bc platform.audit，领域）进用户确认单——确认前不派实现**，取值 16 个对象类别 + `REQUEST`、`OPERATION`、`LOGIN_ATTEMPT`，与 `DeliverySubjectKind` 各自独立（列名同名同义、注释同一句「对象类别：被记录或被投递的业务对象的类别」）；`subject_id` 列注释写明「为空表示这条记录没有对象」；`first_entry_created_at`、`last_entry_created_at`、`NumberingCounterId` 同意。

## 14. 验收阶段裁定（one-1a-next-4，2026-10-16）
- RULING-P5-1 范围含 `ApprovalIdContractTest`：D-13 后 `OTHER_ID_NAMES` = {`InboxItemResponse.taskId`, `SyncDiffResponse.subjectKey`}，两个契约验收同口径改。
- RULING-P5-2：列注释口径——`subject_kind`「对象类别：被记录或被投递的业务对象的类别」；`audit_entries.subject_id`「对象 ID：被记录或被投递的业务对象的自有 ID；为空表示这条记录没有对象」（P4 `outbound_deliveries.subject_id` 无空值，不加后半句）。验收只断言共同片段与「为空」。
- RULING-P5-3：既有验收物随 P5 机械适配，由测试代理改、断言含义不变——`AccountsAcceptanceSupport.grantRole` 写 `account_roles` 带新主键（D-12）；卡第 4 节点名引用 `subjectKey`、`EventSource` 的旧验收用例改为新列与新信封。
- 采纳测试代理对第 3（超时事件 `@NotAudited`，只验事件登记表与提醒卡）、4、5、6、7、9 项的弱断言处理：卡未定的文案与取值不锁死。
- 改名卡并入（用户已在确认页确认，one-coordinator 派；卡见 `docs/modernization/glossary/b2/GL_RENAME_CARDS.md`，#280）：B2-B02 `ApprovalTaskOverdue` → `ApprovalTaskBecameOverdue`；B2-B03 `TaskResulted` → `TaskHandled`；B2-B05 `SourceReferenceRegistrationRefused` → `SourceReferenceRefused`；B2-B07 `TimesheetCancelled` → `TimesheetApprovalWithdrawn`。同步事件目录、消费方、`aggregates.yaml`、`MUST_AUDIT_ACTIONS.yaml`；同一批删 `code-term-baseline.txt` 与 `term-rules-baseline.json` 对应行并调低 count（按 #280 合入后 main 上的实际行）。B05、B07 的验收由测试代理按 RULING-P5-4 补（同第 4 节第 12 项写法）。
- RULING-P5-4：B2-B05、B2-B07 改名验收（`SourceReferenceRefusedRenameIT`、`TimesheetApprovalWithdrawnRenameIT`），写法同第 4 节第 12 项。
- 用户 2026-10-16 在 one-1a-next-4 会话中确认「审计对象类别」AuditSubjectKind（答「同意 (Recommended)」，intent 已记）；第 11 节新词全部定稿，实现可派。
- RULING-P5-5：`AuditIdContractTest` 按仓库约定（`ModelResolver.enumsAsRef = true`，枚举一律 `$ref`）解析 `subjectKind` 的 `$ref`，validators 正则容许 getter 写法；不为验收改全局约定。
- RULING-P5-6：`EventEnvelopeAggregateIdIT.everyAggregateIdIsThePrimaryKeyOfItsAggregate` 只取本用例产生的事件（同类共用容器，其他用例的审批请求会混入），断言含义不变。
- RULING-P5-7：P3b 旧验收 `HeldSeparationReleaseIT`（直接 insert）与 `OwnIdColumnsIT.theHeldSeparationsKeepTheirKeyUntilSplitB`（断言复合主键）随 RULING-P5-D12 适配：insert 带新主键；后者改为断言单列 uuid 主键 + `(sync_run_id, employee_id)` 命名唯一约束（原断言所依的「留到拆分 b」已被 D12 改判取代，PR 单列）。
- 实现中的卡外落法（知悉）：`LegalEntity`、`Role` 的 ID 原由仓储生成，D-14 守护拦下，一并改为调用方取 ID；`subjectId` 查询参数须与 `subjectKind` 同给，否则 400。`ApprovalTaskBecameOverdue` 仍被 G41 / `term_rules.py` 判为非过去式，基线行改为新名保留、count 不降，口径交 one-glossary。
- 终审后修复与未修 Low 理由（one-1a-next-4，2026-10-16）：M-A（管理员模式读授权数据的审计附路径中的规范 uuid）、M-B（Flowable 业务键改单据 uuid 文本，删 `businessKey` 变量）、M-C（根 ID 守护正向识别）已修。未修 Low：L-2 `ArchitectureRulesTest` 类型化 ID 约束——回退正确，不加；L-4 api 方法数已满足，挪动涉及跨模块，不动；L-9 内联主键默认名由 ddl-naming-baseline 统一处理（R-2a 改名）；L-11 守护盲区（`SqlOrderByGuardTest`、`OpenApiExportTest`、`LegacyShapeTest`）登记余项，交 F-2 守护卡（NEXT_BATCH_PLAN 2.4 F-2 行，G-T4 在 F-1 / F-4 / F-5 合并后最后跑一次时一并补）；L-13 卡外文件在 PR 中写明。单列的断言改动：`OrgDataAdminRoleIntegrationTest` 期望随 M-A 改为「模板 + `userId=<uuid>`」；`TestIsolationGuardTest.schemaOf` 对 `acceptance/<area>/X.java` 返回哨兵 `acceptance`；`AuditIdContractTest:114` 已按 RULING-P5-8 收紧为精确匹配 `get subjectKind()`。
- RULING-P5-8：终审 Low——`AuditIdContractTest:114` 的轻微放宽收紧回原强度；本分支新增验收行中的全限定类名改 import（`P5Support`、`HeldSeparationReleaseIT`、`AuditArchiveOrderIT`、`OpenApiUuidParametersIT`、`EventEnvelopeContractTest`、`AccountsAcceptanceSupport` 等），断言不变。

## 15. 数据设计补审闭合（one-data，2026-10-16）
补审报告：`one-data-review-r1b-p5`（无阻断、无高；M-1..M-3 与 L-1..L-6）。M-1、M-2 已改基线，M-3 与 L 项落在本节。

### 15.1 不变量 → 约束
| 不变量 | 约束 / 守护 | 保证者 |
|---|---|---|
| 审计对象类别只取枚举内的值 | `ck_audit_entries_subject_kind`（取值 = `AuditSubjectKind`） | `EventCatalogueTest`（D-17：CHECK 取值与枚举逐项一致，且事件目录每个聚合都换算得到类别，新增聚合漏改 CHECK 即失败）；`AuditSubjectColumnsIT` |
| 无对象类别（`REQUEST`、`OPERATION`、`LOGIN_ATTEMPT`）的 `subject_id` 为空，其余类别必填 | `ck_audit_entries_subject_id`：`(subject_id IS NULL) = (subject_kind IN (…))`；括号内三个值与枚举 `hasObject=false` 的几项一致 | `AuditSubjectColumnsIT`；`AuditLogIT`（无对象类别的条目写入） |
| 审计条目只追加 | 触发器 `audit_entries_reject_update_delete`、`audit_entries_reject_truncate` | `DataArchitectureTest`（只追加表登记）；`unversioned-table-writes.txt` 登记 |
| 同一领域事件至多一条审计条目 | `uk_audit_entries_event_id` | `AuditDomainEventsIT` |
| 审计条目主键是 UUIDv7 | `audit_entries.id uuid` 主键 | `PrimaryKeyTypeGuardTest`；`AuditIdContractTest` |
| 审计归档按「时刻 + ID」取批、批次区间有序且不为空 | `ck_audit_archive_batches_range`、`uk_audit_archive_batches_last_entry_id`；取批与对账走 `ix_audit_entries_created_at_id` | `AuditArchiveOrderIT`；`AuditArchiveJobIT`；`SqlOrderByGuardTest`（不只按主键排序） |
| 事件信封 `aggregateId` 是该聚合自己的主键，且为 UUID；信封无 `source` | 事件目录 `format: uuid`（无数据库约束） | `EventCatalogueTest`（`envelopeProblems`）；`EventEnvelopeContractTest`；`EventEnvelopeAggregateIdIT` |
| 三张子表单列 UUIDv7 主键，业务唯一性仍保证 | `pk_role_permissions` + `uk_role_permissions_role_id_permission_code`；`pk_account_roles` + `uk_account_roles_user_id_role_id`；`pk_sync_run_held_separations` + `uk_sync_run_held_separations_sync_run_id_employee_id` | `PrimaryKeyTypeGuardTest`；`SingleColumnPrimaryKeysIT`；`OwnIdColumnsIT` |
| 被暂停运行的离职名单随运行删除，员工必须存在 | `fk_sync_run_held_separations_sync_run_id`（`ON DELETE CASCADE`）、`fk_sync_run_held_separations_employee_id`；外键列有索引 `ix_sync_run_held_separations_employee_id` | `HeldSeparationReleaseIT`；`DdlNamingTest` |
| 框架表之外的表都有单列 uuid 主键 | `primary-key-exemptions.txt` 只剩 ADR-0033 第 1 项第 8 条的 5 张表，过期行失败 | `PrimaryKeyTypeGuardTest` |

### 15.2 `subject_kind` ↔ 聚合类型
换算规则：聚合类型驼峰转大写下划线（`UserAccount` → `USER_ACCOUNT`），`AuditDomainEvents` 换算不到即失败并 ALERT（`AuditSubjectKind.ofAggregateType`）。守护：`EventCatalogueTest`（D-17）逐个检查事件目录里的聚合类型都有类别、`ck_audit_entries_subject_kind` 的取值集合等于枚举；新增聚合必须先加枚举与 CHECK。

| 取值 | 对象 | 来源 |
|---|---|---|
| `USER_ACCOUNT` | 用户账号 `UserAccount`（用户 ID） | 事件 + 直接审计 |
| `EMPLOYEE` | 员工 `Employee`（员工 ID） | 事件 + 直接审计 |
| `ORG_UNIT` | 部门（部门 ID） | 直接审计（读取、访问拒绝）；法人归属访问拒绝时记被归属的部门（D-4） |
| `EMERGENCY_ADMIN` | `EmergencyAdmin`（用户 ID） | 事件 + 直接审计 |
| `ROLE` | `Role`（角色 ID） | 事件 + 直接审计 |
| `APPROVAL_TASK` | 审批任务（任务 ID） | 直接审计（重新指派、任务闸门） |
| `APPROVAL_REQUEST` | `ApprovalRequest`（审批请求 ID） | 事件 + 直接审计 |
| `SYNC_RUN` | `SyncRun`（同步运行 ID） | 事件 + 直接审计（放行记录） |
| `LEGAL_ENTITY` | `LegalEntity`（法人主体 ID） | 事件 |
| `LEGAL_ENTITY_ATTRIBUTION` | `LegalEntityAttribution`（`AttributionId`） | 事件 |
| `TIMESHEET` | `Timesheet`（工时单 ID） | 事件 |
| `WORK_CALENDAR_YEAR` | `WorkCalendarYear`（年度行 ID） | 事件 |
| `NUMBERING_RULE` | `NumberingRule`（`numbering_rule_id`） | 事件 |
| `SOURCE_REFERENCE` | `SourceReference`（来源对照 ID） | 事件 |
| `SOURCE_REFERENCE_REFUSAL` | `SourceReferenceRefusal` | 事件 |
| `AUTHORIZATION_REQUEST` | `AuthorizationRequest` | 事件 |
| `REQUEST` | 无对象：越权或特权请求（`subject_id` 空） | 直接审计（`AccessDeniedAudit`、`PrivilegedRequestAudit`） |
| `OPERATION` | 无对象：被拒绝的操作（`subject_id` 空） | 直接审计（`AccessDeniedAudit`） |
| `LOGIN_ATTEMPT` | 无对象：未识别到人的登录尝试（`subject_id` 空） | 直接审计（`LoginAdmission`） |

### 15.3 其余补审项
- L-1（逐表出处）：本卡三张 D-12 表与 `audit_entries` 的出处——`audit_entries` 依 ADR-0033 第 7 项第 2 条、第 6 条；`account_roles`、`role_permissions`、`sync_run_held_separations` 依 ADR-0033 第 1 项第 8 条与 RULING-P5-D12。血缘 `NEW`，写入者见 `data-access-registry.yaml`。
- L-2（已修）：`role_permission_id`、`account_role_id` 改为 `CONSTRAINT pk_… PRIMARY KEY`，删 `ddl-naming-baseline.txt` 中两条 `*_pkey` 改名行。
- L-3（PR 补）：三张 D-12 表的预期 diff 逐列——`account_roles`：新增 `account_role_id uuid NOT NULL`（主键 `pk_account_roles`），原主键 `(user_id, role_id)` 改为 `uk_account_roles_user_id_role_id`；`role_permissions`：新增 `role_permission_id uuid`（`pk_role_permissions`），原主键 `(role_id, permission_code)` 改为 `uk_role_permissions_role_id_permission_code`；`sync_run_held_separations`：新增 `sync_run_held_separation_id uuid`（`pk_sync_run_held_separations`），原主键改为 `uk_sync_run_held_separations_sync_run_id_employee_id`，新增 `ix_sync_run_held_separations_employee_id`；三表各加主键列注释。PR 描述照此列出并贴实际 `pg_dump` 对比。
- L-4（不改代码）：`audit_entries.detail` 记请求路径时，路径只含模板与规范 uuid（M-A），uuid 是系统自有 ID，不是个人信息；查询串、请求体、令牌不进 `detail`；列注释已写「不含密钥和个人敏感信息」。
- L-5（已修）：`sync_diffs.subject_key` 列注释示例改为 uuid 形式。该列拆分 a 才重做，卡不扩范围。
- L-6：基线改动后 dev 库按 `phase1a/infra/runbooks/dev-db-rebuild.md` 重建（本批改了已有基线文件，触发条件成立；用户批准前 dev 不重建，见该剧本首段）。
- M-1 落法：`audit_entries_created_idx (created_at DESC)` 删除，换为 `ix_audit_entries_created_at_id (created_at, id)`。依据：归档取批（`created_at, id` 升序）、对账（行值比较）与四处倒序查询（`created_at desc, id desc`）都能由它前向或反向扫描满足，旧索引是它的前缀，不留冗余（§2.4a 第 2 条、G-DDL3）；`ddl-naming-baseline.txt` 对应改名行同删。
- M-2 落法：`ix_sync_run_held_separations_employee_id`；数据访问注册表不登记索引，无需同步。
