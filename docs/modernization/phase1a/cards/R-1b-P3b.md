---
卡号: R-1b-P3b
状态: 完成
前置: R-1b-0
---
# 派工卡 R-1b P3b：员工、部门、岗位改为自有 UUIDv7，领域层只见自有 ID

## 1. 卡号与类别
- 卡号：R-1b P3b（R-1b 的第 3b 个子 PR；并入 DA-1、DA-2、DA-5 的员工 / 部门 / 岗位部分，DDL-01、DDL-06 的 workforce 部分，G-A1 的员工 / 部门 / 岗位部分，R-1b-0 C-5 的老系统视图一致性检查）
- 类别：结构性重构
- 批次与整合分支：Phase 1a 下一批 R-1b 线（`phase1a/NEXT_BATCH_PLAN.md` 7.11：R-1b-0a → **P3b** → 组织同步拆分 b → 拆分 a → R-1b-0b → DA-3 → P5）；单独一个 PR，直接合入 main
- 前置：P1、P2（#209）、P3a 已合并；**R-1b-0a 先合并，P3b 在其上 rebase**（两者都改 seeyon 适配器与 `SourcePerson`、`SnapshotReconciler`，R-1b-0 卡第 14 节）

## 2. 依据
- ADR-0033（已采纳）：第 1 项（主键 UUIDv7、版本表与归属期间表代理主键 + 自然键、1:1 扩展表、类型化 ID 包 `UUID`）；第 2 项第 2、3、4、6、9、10 条（分配、按来源 ID 匹配、领域只用自有 ID、部门简称经对照、老待办移入集成层、多态引用拆分列）；第 3 项第 1、3、5、6 条（`employee_id`、「本人」判定按员工侧、签名票据、运维命令）；第 6 项（员工编号不作键、ID 对外为字符串）；第 7 项第 4 条（DA-5）；守护 3、6、7、8、9
- ADR-0039（已采纳）：决定第 1 条（外部概念只在 `platform.integration`，业务模块只收已翻译结果）、第 5 条第 2 款（老待办移入集成层，契约中性化可放生产后）、第 6 条（表的位置）、「需同步修改的守护规则」中 `ExternalConnectionIsolationTest` 一条
- 计划：`phase1a/NEXT_BATCH_PLAN.md` 7.2（P3b 行、C8、C10）与 **7.11（以 7.11 为准）**；负责会话证据 `~/.modernize/psa/handoff/one-1a-next-3/p3b-sequencing.md` 第 2、3 节（表位置与范围）；拆分图 `~/.modernize/psa/handoff/one-1a-next-2/r1b-split-plan.md`（P3b 行、第 3 节验收）
- 卡：`phase1a/cards/R-1b.md`（§3 至 §9 逐子 PR 适用，文末 2026-10-15 修订：G-A1、「本人」判定清单）；`phase1a/cards/R-1b-0.md` 第 11 节 C-5、第 13、14 节
- 规范：SESSION_PLAYBOOK §5（开工前术语门禁）、§6.1、§12.2（基线开放期直接改基线，附预期表结构 diff）、§14、§14.1；CODING_STANDARD CS-1.8、CS-1.10、CS-1.11、CS-2.20、CS-2.21、CS-2.29、CS-5.3–5.9；NAMING_STANDARD §2.4、§2.4a
- 用户原话：「改为自有 UUIDv7 + A8 ID 对照列 (Recommended)」「主键统一」「也加，自有表零例外 (Recommended)」「认可对照表 (Recommended)」（intent 见 ADR-0033 背景）；防腐层边界 U-ACL-0「认可」、U-ACL-A「同步搬进集成层，离职暂停留组织模块」（intent `2026-10-15-164527-one-architecture-防腐层边界决策页-U-ACL-0.md`）
- P2 已合并的写法（#209）可作参照：`TestAccounts` 让员工 ID 与用户 ID 取不同值；`UserIdContractTest`、`UserIdColumnsIT` 的契约与列断言写法；`primary-key-type-baseline.txt` 删行；INFRA_RUNBOOK `--employee-id` 段

## 3. 范围与卡上文件

### 3.1 表与列（改开放基线，不追加迁移；全部 uuid 由应用侧 `IdGenerator` 生成）

`organization_workforce`（`V20261014131000__organization_workforce_baseline.sql`）：

| 表 | 最终形态 |
|---|---|
| `positions` | `id uuid PRIMARY KEY`（行内写法不变，约束名仍为 `positions_pkey`） |
| `org_units` | `id uuid PRIMARY KEY`；`parent_org_unit_id`、`head_employee_id`、`regional_receivables_manager_id` 改 `uuid`；外键与 `DEFERRABLE INITIALLY DEFERRED` 不变；新增 `ix_org_units_regional_receivables_manager_id`（DDL-06） |
| `employees` | `id uuid PRIMARY KEY`；`employee_code` 只是属性（`employees_employee_code_idx` 普通索引保留，不加唯一）；`login_name`、`wecom_user_id` 不动（DA-3） |
| `assignments` | 1:1 扩展表（主岗投影）：`employee_id uuid PRIMARY KEY REFERENCES employees (id)`；`org_unit_id`、`position_id`、`reports_to_employee_id` 改 `uuid`；新增 `ix_assignments_position_id`（DDL-06） |
| `org_unit_versions` | 新增代理主键列 `org_unit_version_id uuid NOT NULL`，`pk_org_unit_versions` 改为只含该列；原主键三列改为 `uk_org_unit_versions_revision UNIQUE (org_unit_id, effective_from_date, revision_number)`（DDL-01，写法同 `uk_legal_entity_versions_revision`）；`org_unit_id`、`parent_org_unit_id`、`head_employee_id`、`regional_receivables_manager_id` 改 `uuid`；排他约束 `ex_org_unit_versions_overlap` 不变 |
| `assignment_versions` | 同上：`assignment_version_id uuid` 代理主键，`uk_assignment_versions_revision UNIQUE (employee_id, effective_from_date, revision_number)`；`employee_id`、`org_unit_id`、`position_id`、`reports_to_employee_id` 改 `uuid`；新增 `ix_assignment_versions_org_unit_id (org_unit_id, effective_from_date)`（DDL-06） |
| `org_unit_legal_entities` | 新增代理主键列 `org_unit_legal_entity_id uuid NOT NULL`（列名待术语门禁，第 11 节），`pk_org_unit_legal_entities` 改为只含该列；原主键改为 `uk_org_unit_legal_entities_effective_from (org_unit_id, effective_from_date)`（名称按 §2.4，实施时以 `DdlNamingTest` 为准）；`org_unit_id` 改 `uuid`；新增 `ix_org_unit_legal_entities_legal_entity_id`（DDL-06）；排他约束不变 |
| `employee_legal_entities` | 同上：`employee_legal_entity_id uuid` 代理主键，`uk_employee_legal_entities_effective_from (employee_id, effective_from_date)`；`employee_id` 改 `uuid`；新增 `ix_employee_legal_entities_legal_entity_id` |
| `legal_entity_attributions` | DA-5：删 `subject_kind`、`subject_id` 与 `ck_legal_entity_attributions_subject_kind`、`uk_legal_entity_attributions_subject_id`；加 `org_unit_id uuid` 与 `employee_id uuid`（可空，列注释写明「为空表示归属对象是另一类」），各带外键（`fk_legal_entity_attributions_org_unit_id` → `org_units`，`fk_…_employee_id` → `employees`）与唯一约束（`uk_legal_entity_attributions_org_unit_id`、`uk_…_employee_id`），加 `ck_legal_entity_attributions_subject CHECK (num_nonnulls(org_unit_id, employee_id) = 1)` |
| `legal_entity_attribution_corrections` | DA-5：同上拆为 `org_unit_id`、`employee_id` 两列 + 外键 + 「恰有一个非空」CHECK（`ck_legal_entity_attribution_corrections_subject`）；索引 `ix_legal_entity_attribution_corrections_subject` 改为两条（`(org_unit_id, period_effective_from_date, correction_number DESC)`、`(employee_id, …)`）；只追加触发器不变 |
| `sync_run_held_separations` | **只改 `employee_id` 为 `uuid`**（外键指向 `employees.id`，无法避开）；主键、表名、其余不动（拆分 b 换表） |

其他 schema：

| 表 | 改动 |
|---|---|
| `platform_identity.user_accounts` | `employee_id` 改 `uuid NOT NULL`，`uk_user_accounts_employee_id` 不变；列注释去掉「R-1b P3b 随员工 ID 改为…」的过渡说明 |
| `cost_timesheet.timesheets` | `employee_id`、`project_manager_id`、`org_unit_id` 改 `uuid`；`ix_timesheets_employee_id`、`ix_timesheets_org_unit_id` 不变 |
| `platform_approval.approval_requests`、`start_rounds` | `initiator_id` 改 `uuid` |

列与表注释：去掉「沿用组织源，有符号 bigint」（workforce 基线 :16、:45、:83、:248）与「人员 ID」写法（统一为「员工 ID」，查 `terms.yaml` 的 employee）；ADR-0033 背景所列法人代码「创建后不改」注释若仍在一并更正。数据访问注册表（`backend/architecture/data-access-registry.yaml`）随列名同步 `references`、`fk_exceptions`。

**约束名（C10）**：凡原有约束、索引，名称一律不变（含行内 `PRIMARY KEY` / `REFERENCES` 产生的默认名，如 `assignments_pkey`、`assignments_employee_id_fkey`——不得改成显式 `CONSTRAINT pk_…` 写法，否则 `ddl-naming-baseline.txt` 对应行变成「已修好却没删」并越进 R-2a）。只有本卡新增的约束与索引按 NAMING_STANDARD §2.4 命名。

### 3.2 Java 与集成层
1. **类型**：kernel `EmployeeId`、workforce `OrgUnitId`（api）、`PositionId`（domain）由 `long` 改为包 `UUID`，校验写法同 `OwnObjectId`（`Uuids.requireKey`）；`AttributionSubject` 的 `long subjectKey` 改为分别的类型化 ID（DA-5，ADR-0033 第 7 项第 4 条）；全部 `rs.getLong` / `Long.parseLong` 的员工、部门、岗位 ID（`OrgSnapshotRows`、`OrgHistory`、`JdbcOrgSnapshotRepository`、`JdbcTimesheetRepository`、`JdbcApprovalRequestRepository`、`FlowableApprovalEngine:306`、`NodeAssignment:50`、`ProcessVariables:47`、`AssigneeResolver:137`、`OperationsCommands:112` 等）改 UUID 解析，格式不对失败并告警，不回退、不跳过。
2. **来源 ID 只在集成层**：删 `SourceUnitId`、`SourcePersonId`、`SourcePositionId`；`SourceOrgSnapshot` 及其中的 `SourceOrgUnit`、`SourcePerson`、`SourcePosition`、`SourceRelationship` 只带 `OwnObjectId`；`SourceTerms` 删 ID 翻译，**暂留一层 `OwnObjectId` → `OrgUnitId` / `EmployeeId` / `PositionId` 的薄映射（只换类型、值不变），拆分 a 交出组织变更集后删除**。枚举翻译不动（拆分 a）。
3. **seeyon 适配器**（`SeeyonOrgSource` 等）：读出本次结果集的全部来源 ID 后，按类别**批量**查已确认的 `(ORG_SOURCE, 类别, source_id)` 对照取自有 ID；只对查不到的对象逐个调 `AllocateOwnId`（经 `SourceReferences`），不对已有对照的对象调分配；关系（上级、负责人、区域应收负责人、汇报人、岗位）用同一张映射翻译（引用到结果集外对象的处理见第 12 节 D-3）。批量查询加在集成层 application（`SourceReferences` 增一个批量方法，方法名见第 11 节），不让适配器直接读表。登录查人（`findStaffByWecomUser`、`findStaffByLoginName`）交出的人员同样带自有 ID（第 12 节 D-4）。
4. **签名票据**：`LoginWithSignedTicket` 的 subject（A8 人员 ID）经 `(ORG_SOURCE, EMPLOYEE)` 对照**只查不分配**解析为员工 ID，查不到即拒绝登录并审计（ADR-0033 第 3 项第 5 条；联合身份细节随 C1，不在本卡）。
5. **老待办移入集成层**（ADR-0033 第 2 项第 9 条、ADR-0039 第 5 条第 2 款）：`JdbcLegacyInbox`、`LegacyInboxConfiguration`、approval 的 `LegacyProperties` 移到 `platform.integration.infrastructure.legacy`；端口与条目类型放 `platform.integration.api`，审批模块只收到不含 `Legacy*Id` 的条目；`LegacyInstanceId` 移出 `platform.approval.domain`；按 `(LEGACY_SYSTEM, EMPLOYEE, own_id)` 对照取当前员工在老系统的人员 ID 再查视图，**查不到对照时该来源返回「不可用」**（合并待办 `legacyAvailable = false` 并告警），不按 0 条处理。名称沿用现名（契约中性化随 ADR-0039 第 6 条「可放生产后」，不在本卡）；搬迁边界见第 12 节 D-2。
6. **部门简称**（ADR-0033 第 2 项第 6 条）：老系统部门 ID → 自有 ID 的翻译移进集成层 `LegacyOrgNames`（经 `(LEGACY_SYSTEM, ORG_UNIT)` 对照），`OrgShortNames` 交出按 `OwnObjectId` 的结果；workforce 的 `ShortNameAdapter` 只剩薄映射（D-1）。查不到对照的部门简称视为「不可用」：不写空、不清除现值，告警。
7. **C8 降级**：P6（老系统人员、部门对照的一次性导入）完成前，老系统待办与部门简称恒为「不可用」。已经 one-coordinator 接受（7.2 C8），**PR 正文单列写明**。
8. **守护**：`ExternalConnectionIsolationTest` 去掉 `APPROVAL_LEGACY`（`approval.infrastructure.legacy`）准入，外部连接只在集成层；`legacy-shape-exceptions.txt` 中老待办读取器的路径随搬迁更新；R-1b-0 C-5 留给本卡的「老系统两个视图的读取列登记与适配器 SQL 常量一致」检查随搬迁加上（登记文件以 R-1b-0a 合并后的为准）；`LegacyShapeTest` 新规则见第 4 节。
9. **「本人」判定**：比较一侧不变（员工侧仍比较员工 ID，账号侧仍比较用户 ID）；只随类型改 UUID。`ensureAccount` 的 4 个入口按 `employee_id uuid` 冲突键（`ON CONFLICT (employee_id)`）不变。
10. **运维命令**：`--employee-id` 改收 uuid 文本（`CanonicalUuids` 规范形态），旧整数形态即用法错误；INFRA_RUNBOOK §6 与 `runbooks/C-5-first-admins.md` 同步（去掉「P2 期间为整数」的说明）。按登录名 / 企业微信 userid 查员工 ID 的只读命令见第 12 节 D-8。
11. **回放工具**（`platform/approval/infrastructure/replay`）：导出文件中的数字 ID 在工具内存中一一映射为 UUID，不经来源对照、不落库（D-6）。
12. **契约**（契约先行，CS-1.10，与代码同一提交）：
    - OpenAPI 路径参数改 `string`/`uuid`：`/organization/employee-legal-entities/{employeeId}`（及 `:correct`、`:end`）、`/organization/org-unit-legal-entities/{orgUnitId}`（及 `:correct`、`:end`）、`/organization/org-units/{orgUnitId}`——**计划写「6 条」，按现 `openapi.json` 实为 7 个路径模板、9 个操作，全部改**（D-7）；
    - 请求 `ReassignTaskRequest.assigneeId`、`TransferTaskRequest.assigneeId` 改为员工 ID 的 uuid；响应中的员工、部门 ID（`OrgUnitResponse.*Id`、`OrgUnitDetailResponse.ancestorOrgUnitIds` / `orgUnitAndBelowIds`、`CurrentUserResponse.orgUnitId`、`LegalEntityAttributionResponse.subjectId`）声明 `format: uuid`；
    - `OpenApiExportTest.OTHER_ID_NAMES` 删 9 个名字：`orgUnitId`、`employeeId`、`headEmployeeId`、`parentOrgUnitId`、`regionalReceivablesManagerId`、`ancestorOrgUnitIds`、`orgUnitAndBelowIds`、`assigneeId`、`subjectId`（只剩 P4 的 `taskId`、`instanceId`、`documentId`）；
    - 事件目录：`events/workforce.yaml`、`timesheet.yaml`、`approval.yaml` 中 `x-type` 为 `EmployeeId` / `OrgUnitId` / `PositionId` 的字段加 `format: uuid`，`LegalEntityAttributed` 等的 `subject` 按 D-5；重新生成 `asyncapi.yaml`；
    - 前端：重新生成 `frontend/packages/api`（`openapi.json`、`validators.ts` 中 `assigneeId` 不再是 `z.number()`）；`apps/web/src/modules/workforce` 组织单元页与模型、审批改派 / 转交选人处把 ID 当数字的地方改为字符串。

### 3.3 允许改动的文件
- 开放基线：`backend/src/main/resources/db/migration/{organization_workforce,platform_identity,cost_timesheet,platform_approval}/*.sql`（只限上表所列）
- `backend/src/main/java/com/forcitis/one/`：`kernel/EmployeeId.java`；`organization/workforce/**`；`platform/integration/**`；`platform/approval/**`（类型、老待办移出、流程变量解析）；`platform/identity/**`（员工 ID 类型、签名票据、运维命令）；`cost/timesheet/**`（类型）；`platform/eventing/**` 只在序列化需要时
- 测试：受影响模块的单元与集成测试（非验收目录）；`OpenApiExportTest`、`LegacyShapeTest`、`ExternalConnectionIsolationTest`、`DataArchitectureTest`
- `backend/architecture/`：`primary-key-type-baseline.txt`（删行）、`legacy-shape-exceptions.txt`（改路径）、新建 `source-type-exceptions.txt`、`data-access-registry.yaml`、`module-dependencies.txt`（仅当搬迁需要，先报告）
- `docs/modernization/events/*.yaml` 与 `asyncapi.yaml`；`frontend/packages/api/**` 生成物；`frontend/apps/web/src/modules/{workforce,approval}/**`
- `docs/modernization/phase1a/infra/INFRA_RUNBOOK.md` §6、`phase1a/infra/runbooks/C-5-first-admins.md`
- 越界先停下报告，不先做后报

### 3.4 归属
- 本卡独占：integration 的 `OrgSource` 与 seeyon 适配器、老系统读取适配器（7.2 子 PR 独占构件）；上表各列的类型；`source-type-exceptions.txt`
- 共享构件（需改时停下报告）：`EventEnvelope`、全部守护的结构性扩展（P5 独占）；kernel 中 `EmployeeId` 以外的类型

### 3.5 不在 P3b（越界即停）
- `sync_runs`、`sync_diffs`：**不动 DDL**，由拆分 a 在 `platform_integration` 按最终形态新建（`subject_key` 拆列在那里做）；P3b 之后 `sync_diffs.subject_key` 的文本内容变为「类别:uuid」，表结构不变，属预期过渡态
- `sync_run_held_separations`：只改 `employee_id` 类型；主键、表名、去向由拆分 b 换为 workforce 的待放行离职表（`primary-key-type-baseline.txt` 第 13 行留给拆分 b 删）
- `org_units.last_sync_run_id`、`employees.last_sync_run_id`：拆分 b 改为引用变更集
- `employees.login_name`、`wecom_user_id` 与 `OrgSource.findStaffBy*` 的删除：DA-3
- 审批主键（`approval_tasks`、`instance_id`、`process_archive_skips`、`DocumentId` 等）：P4
- `audit_entries.subject_key`、事件信封 `aggregateId` / `AggregateReference`、`OTHER_ID_NAMES` 清空：P5
- `SourceTerms` 的枚举翻译与 `SourcePerson` 等进入 workforce domain 的移层：拆分 a（P3b 只登记例外，见第 4 节第 4 条）
- 老待办契约中性化（`InboxSource.LEGACY`、`legacyCount`、`legacyAvailable` 改名）：ADR-0039 第 6 条，生产后
- 改名（R-2a）、时钟默认值（R-4）、`ddl-naming-baseline.txt` 任何一行

## 4. 验收测试（由测试代理先写，§6.1 第 8 条）
- 测试代理：`code-modernization:test-engineer`（**opus**，含安全闸门），先于实现提交，提交说明带 `Acceptance-Test-Author: test-engineer`
- 路径：`backend/src/test/java/com/forcitis/one/acceptance/organization/workforce/`、`…/acceptance/platform/{integration,identity,approval}/`、`…/acceptance/cost/timesheet/`；契约类放 `acceptance/` 根（同 `UserIdContractTest`）
- 测试名（`@DisplayName` 以 `R-1b P3b` 与依据编号开头）：
  1. `R-1b P3b ADR-0033 守护 6 NegativeSourceIdIT：A8 来源 ID 为负数（含 Long.MIN_VALUE 与 -1）的部门、人员、岗位，经来源对照映射到自有 UUIDv7，完整走通「分配 → 同步建档 → 再次同步匹配到同一自有 ID」，不出现正数校验`
  2. `R-1b P3b ADR-0033 第 2 项第 3 条：同步按来源 ID 匹配，找不到才新建`——第二次同步中姓名、员工编号、登录名都变了的人仍是同一自有 ID；只有新的来源 ID 才新建对照与对象；已有对照的对象不再调分配（以对照行数与分配次数为证）。**「触发一次同步」封进一个辅助方法**（经真实 seeyon 适配器读测试库中的视图替身，参照 `SeeyonViewsOnPostgresTest` / `StalledExternalSourceIT` 的夹具，不用 `OrgSource` 假实现），第 1、2 条共用；拆分 a 时只改这一处
  3. `R-1b P3b ADR-0033 守护 7 EmployeeCodeNotKeyTest`（`DataArchitectureTest` 内）：`employee_code` 不出现在主键、唯一约束、唯一索引、外键、排他约束中；仓储与查询接口无按员工编号取单个对象的方法；另有同步测试：两人同编号、编号为空都正常建档
  4. `R-1b P3b ADR-0033 守护 3 LegacyShapeTest`：
     - 新规则一：除 `platform.integration` 外，各模块 `domain`、`application`、`api`、`events`、`web` 包不得引用名为 `Source*` 的类型（范围见第 12 节 D-12：按 CS-2.29 限为 `platform.integration` 中的 `Source*` 与 workforce 的 `SourceTerms`，kernel 事件来源 `SourceSystem` 不在内）；现有违规逐文件登记在新文件 `backend/architecture/source-type-exceptions.txt`，每行写理由与去向「拆分 a」，**只减不增**（文件头写明；与 origin/main 比较的机械校验同 P2 复核 L-1，落 P5）；过期行失败。按 main @ fcb74ea0 核对，P3b 后预计登记的是 workforce 的 `ApplySourceSnapshot`、`LoginMissRegistration`、`RegisterEmployeeOnLogin`、`RegisterEmployeeOnLoginName`、`SyncOrgSnapshot`、`EmployeeChecks`、`OrgReconciliation`、`SnapshotReconciler`、`SourceTerms` 及其他引用 `SourceTerms` 的文件，以 R-1b-0a 合并后的实际结果为准
     - 新规则二：除 `platform.integration` 外不得有名为 `Source*Id`、`Legacy*Id` 的类型，**无例外清单**
     - 两条规则都带注入负向样例与反例（CS-5.9）
  5. `R-1b P3b ADR-0033 守护 9 OperationsCommandEmployeeIdTest`：`--employee-id` 非 uuid（含旧整数形态与负数）退出码 2 且不写任何数据；员工不存在退出码非 0 且不写任何数据；uuid 有效时取得或建立账号
  6. `R-1b P3b ADR-0033 第 3 项第 3 条：流程变量与表单选人字段中非 uuid 的人员值、部门值使指派失败并告警`（`PLATFORM_APPROVAL_ASSIGNEE_INPUT_INVALID`，现行为是静默跳过），不跳过该值、不写任务
  7. `R-1b P3b DA-5：法人归属与更正记录的 org_unit_id / employee_id 恰有一个非空`——两空、两非空都被 CHECK 拒绝；外键生效；同一部门 / 员工只有一条归属
  8. ★ `R-1b P3b ADR-0033 第 3 项第 3 条：「本人」闸门在员工 ID 为 UUID 时仍按原一侧拦下`——P2 的反向测试原样重跑，员工 ID 与用户 ID 取不同 UUID：`EmergencyLoginEmploymentIT`、`HeldSeparationReleaseIT`、`OwnAttributionIT`、`ApprovalAccountsIT`、`RetractionInitiatorAccountsIT`、`TimesheetOwnerAccountsIT`、`OwnRolesIT`、`AuthorizationConfirmerAccountsIT`、`AccountingCodeConfirmerAccountsIT`、`NumberingRuleConfirmerIT`；断言不变
  9. `R-1b P3b ADR-0033 第 3 项第 1 条：AccountEnsureConcurrencyIT 在 employee_id 为 uuid 时 4 个入口并发只产生一个账号`
  10. `R-1b P3b G-A1 / ADR-0033 第 6 项第 2 条`（新 `EmployeeIdContractTest`，写法同 `UserIdContractTest`）：OpenAPI 中员工、部门、岗位 ID 的属性、路径参数（7 个模板）与 `assigneeId` 为 `type: string, format: uuid`；`OTHER_ID_NAMES` 只剩 `taskId`、`instanceId`、`documentId`；事件目录中 `x-type` 为 `EmployeeId` / `OrgUnitId` / `PositionId` 的字段声明 `format: uuid`；生成的 `validators.ts` 中这些字段不是数字
  11. `R-1b P3b ADR-0033 第 1 项`（新列断言，写法同 `UserIdColumnsIT`）：第 3.1 节各列为 `uuid`；workforce 上述 8 张表主键为单列 uuid；DDL-01 的自然键唯一约束存在；DDL-06 的 5 个外键索引存在；`sync_run_held_separations` 主键仍为原复合主键（拆分 b）
  12. `R-1b P3b ADR-0033 第 2 项第 9 条 / C8：当前员工没有老系统对照时，合并待办的老系统来源为「不可用」并告警，不按 0 条处理`
  13. `R-1b P3b ADR-0033 第 2 项第 6 条 / C8：没有老系统对照的部门简称不写入、不清除现值，并告警`
  14. `R-1b P3b ADR-0033 第 3 项第 5 条：签名票据的 subject 经来源对照解析为员工；没有对照即拒绝登录并审计`
  15. `R-1b P3b ADR-0039 守护：只有集成层的老系统适配器取得老系统连接`（`ExternalConnectionIsolationTest` 准入去掉 `approval.infrastructure.legacy`）与老系统视图读取列登记一致性（R-1b-0 C-5）
- **要按 Acceptance-Ruling 改的现有验收物**（测试代理改，实现代理不碰）：
  - `RULING-P3B-1`：`acceptance/AccountsAcceptanceSupport.java`——`newEmployeeId()` / `newOrgUnitId()` 返回 UUID，`orgUnit`、`employee`、`removeFromSnapshot` 等向 `employees`、`org_units`、`assignments` 写 bigint 的 SQL 改写 uuid，`SignedIn.employeeId`、`signIn`、`employeeOfAccount`、`accountsOfEmployee`、`grantByOperations`、`attributeEmployee` 的 `long` 参数改 UUID；`fixture/doubles/TestAccounts.java`（不在验收目录，但 ★ 测试「两种 ID 取不同值」靠它，同样由测试代理改，PR 单列）中 `EmployeeId(long)`、按数字位拼账号 ID 的写法改为按员工 UUID 确定性派生、且与员工 UUID 不相等的账号 ID
  - `RULING-P3B-2`：用到员工 / 部门数字 ID 的现有验收测试只改 ID 的构造与类型，不改断言：上列 ★ 测试与 `EventEnvelopeActorIT`、`AccountEntriesConcurrencyIT`、`SessionPrincipalIT`；R-1b-0a 合并后的 `A8SourceFlagsIT` 若用到数字员工 ID 同样处理
  - `RULING-P3B-3`：`StalledExternalSourceIT`、`ReadOnlyAccountCheckIT` 中老待办读取器的包路径随搬迁更新；其中 `dbo.*` 替身表的 `bigint` 是外部系统形态，不改
- 实现方认为验收测试有误：停下报告，由复审裁定（`Acceptance-Ruling: RULING-P3B-n`），不自行修改

## 5. 执行者与模型
- 测试代理：`code-modernization:test-engineer`，**opus**
- 执行者：子代理，**opus**（体量大：workforce 约 50 个 main 文件，另有 integration、approval、identity、timesheet，测试约 100 个文件）。**按模块分提交**，建议顺序：① 基线 DDL 与 kernel / workforce 类型；② integration（适配器批量查对照、老待办与部门简称移入、签名票据解析）；③ approval（类型、流程变量解析、老待办端口切换）；④ identity、timesheet；⑤ 契约（OpenAPI、事件 yaml、前端生成物与页面）与守护文件。中间提交尽量可编译，不能时在提交说明注明；PR 末提交全绿
- 工作区：独立 worktree，分支 `claude/one-1a-next-3-r1b-p3b`，基线为 **R-1b-0a 合并后的 main**；开工前确认 R-1b-0a 已合并，否则停下报告
- 复审：opus，推理强度 high（§14、§14.1 全部条目 + 「老系统形状」专查）
- 不交 Codex：本卡含身份与「本人」判定（SESSION_PLAYBOOK §5「Codex 派工」）

## 6. 禁止事项
- 不改卡外文件；不顺手重构、改名或统一风格；不改任何原有约束、索引的名字（C10）
- 不改业务语义：「本人」判定只换 ID 类型，不换比较一侧、不放宽；同步规则（缩水保护、离职暂停、RULE-366 等）不变
- 领域与其他模块不新增任何 `Source*` 引用；`source-type-exceptions.txt` 只减不增
- 任何模块不以员工编号作键或匹配键；同步不按姓名、编号、登录名匹配
- 不依赖 ID 顺序（游标与「最近一条」按时刻列 + 主键，CS-2.21）
- 不放宽、不删除断言；不改验收测试目录
- Testcontainers 临时容器（镜像限 `backend/architecture/test-images.txt`）可直接跑；其他容器、常驻容器、对外调用、生产取证先问；不建账号
- 不打印、不写入密钥；测试密钥运行时生成

## 7. 预期表结构 diff（SESSION_PLAYBOOK §12.2）
- 方法：两个空库分别执行改动前、后的基线，`pg_dump --schema-only`（带 / 不带权限）对比，种子另做 `--data-only` 对比
- 预期差异只能是：
  - 类型 `bigint` → `uuid`：`positions.id`；`org_units.{id, parent_org_unit_id, head_employee_id, regional_receivables_manager_id}`；`employees.id`；`assignments.{employee_id, org_unit_id, position_id, reports_to_employee_id}`；`org_unit_versions.{org_unit_id, parent_org_unit_id, head_employee_id, regional_receivables_manager_id}`；`assignment_versions.{employee_id, org_unit_id, position_id, reports_to_employee_id}`；`org_unit_legal_entities.org_unit_id`；`employee_legal_entities.employee_id`；`sync_run_held_separations.employee_id`；`platform_identity.user_accounts.employee_id`；`cost_timesheet.timesheets.{employee_id, project_manager_id, org_unit_id}`；`platform_approval.approval_requests.initiator_id`、`start_rounds.initiator_id`
  - 新增列：`org_unit_versions.org_unit_version_id`、`assignment_versions.assignment_version_id`、`org_unit_legal_entities.org_unit_legal_entity_id`、`employee_legal_entities.employee_legal_entity_id`（名称以术语门禁结果为准）；`legal_entity_attributions` 与 `legal_entity_attribution_corrections` 各加 `org_unit_id`、`employee_id`
  - 删除列：上两表的 `subject_kind`、`subject_id`
  - 约束：上 4 张表的 `pk_*` 列清单改为单列代理主键（名称不变）；新增 `uk_org_unit_versions_revision`、`uk_assignment_versions_revision`、两张归属期间表的自然键唯一约束；`legal_entity_attributions` 删 `ck_…_subject_kind`、`uk_…_subject_id`，加两条外键、两条唯一、一条「恰有一个非空」CHECK；更正表删 `ck_…_subject_kind`，加两条外键、一条 CHECK
  - 索引：新增 `ix_org_units_regional_receivables_manager_id`、`ix_assignments_position_id`、`ix_org_unit_legal_entities_legal_entity_id`、`ix_employee_legal_entities_legal_entity_id`、`ix_assignment_versions_org_unit_id`；`ix_legal_entity_attribution_corrections_subject` 换为两条按 `org_unit_id` / `employee_id` 的索引
  - 注释：第 3.1 节所列列与表注释
- 不得出现的差异：`sync_runs`、`sync_diffs` 的任何变化；`sync_run_held_separations` 除 `employee_id` 类型外的变化；任何原有约束、索引、触发器的改名或删除（上列明示者除外）；默认值、可空性（新增可空列除外）、CHECK 取值的变化；`login_name`、`wecom_user_id`、`last_sync_run_id` 的变化；identity、timesheet、approval 其余列的变化；种子数据变化

## 8. 交付物与自查
- 交付物：基线 DDL；Java（类型、适配器、移层、解析）；单元与集成测试；守护（`LegacyShapeTest` 两条新规则、`ExternalConnectionIsolationTest`、`OpenApiExportTest`、`DataArchitectureTest` 中 `EmployeeCodeNotKeyTest`）；OpenAPI、事件目录、前端生成物与页面；INFRA_RUNBOOK 与 C-5 剧本
- 守护文件变动（PR 单列）：
  - `primary-key-type-baseline.txt` 删第 5–12 行（`assignments`、`employees`、`org_units`、`positions`、`org_unit_legal_entities`、`employee_legal_entities`、`org_unit_versions`、`assignment_versions`，已按 main @ fcb74ea0 核对）；第 13 行 `sync_run_held_separations` 保留（拆分 b）；文件头注释不改
  - 新建 `source-type-exceptions.txt`（只减不增）；`legacy-shape-exceptions.txt` 改老待办读取器路径（只改路径，不加行）
  - `OpenApiExportTest.OTHER_ID_NAMES` 删 9 个名字
  - `ddl-naming-baseline.txt` **不动**（C10）
- 自查：`scripts/precheck.sh <基线>` 全部 PASS 或有理由的 SKIP，输出贴进报告与 PR「precheck.sh 输出」；`pre-commit install`；报告前 gitleaks 8.30.1 扫全历史
- 受影响模块测试：precheck 给出的命令；交付前全量 `scripts/ci/backend_verify.sh` 与前端 `pnpm verify`
- PR 正文单列：预期表结构 diff；改动的断言 / 测试及理由（含 RULING-P3B-n）；C8 降级；第 12 节各点的落法
- 报告分开写「已验证」与「未运行」

## 9. 复审要求（§6.1 第 4、11 条）
- Blocker / High：opus 复核，合并前修完
- Medium / Low：执行者补测试、CI 通过即关闭，负责会话抽查；不阻塞合并的登记进下一批
- 复审清单：SESSION_PLAYBOOK §14、§14.1（D2 键与约束、D6 生效区间、D14 血缘、D16 自有标识、D18 DDL 原则）；CODING_STANDARD CS-1.11、CS-2.20、CS-2.21、CS-2.29、CS-5.5、CS-5.6、CS-5.9
- 专查：表结构 diff 只含第 7 节声明项；约束名无一改动；适配器只对新对象调分配、批量查对照；领域与其他模块无 `Source*Id` / `Legacy*Id`、`Source*` 引用不多于例外清单；「本人」判定每条仍有反向测试且比较一侧未变；OpenAPI 与事件中员工 / 部门 / 岗位 ID 无数字形态；老待办与部门简称查不到对照时为「不可用」而非 0 条或空

## 10. 报告去向
- 完成、部分完成或阻塞，都向 one-1a-next-3，抄 one-coordinator（SESSION_PLAYBOOK §4 第 7 步）
- PR／卡／交接记录（消息按 TEAM-53 四节点及需动作事件送达，不另报号）：分支、改动文件（按模块）、验证结果（已验证 / 未运行）、precheck 输出、预期表结构 diff、未完成项、需要用户决定的事；合并后通知拆分 b 的执行者在此基础上开工

## 11. 本卡新词（开工前术语门禁，SESSION_PLAYBOOK §5）

查过：`docs/modernization/glossary/terms.yaml`（`employee`、`org_unit`、`position`、`legal_entity`）与 `backend/glossary/proposed/*.yaml`（`workforce__assignment`、`revision_number`、`source_reference`、`source_system`、`source_object_kind`、`separation_hold`、`inbox`、`todo`、`legacy_system`、`error__platform_approval_assignee_input_invalid`、`error__organization_workforce_attribution_period_*`），中英文都查重。

**业务用语：无新增。** 员工、组织单元、岗位、法人归属、待办、来源对照等都已登记；流程变量非法值复用已提议的错误码 `PLATFORM_APPROVAL_ASSIGNEE_INPUT_INVALID`（「取人输入无效」，422），不新增错误码；老待办沿用现有类名搬家，中性化不在本卡。

**技术标识（交 one-glossary 预判；纯技术词由它直接定）：**

| 中文 | 标识 | 说明 | 状态 |
|---|---|---|---|
| 组织单元版本 ID | `org_unit_version_id` | 版本行代理主键，按 ADR-0033 第 1 项第 5 条「`<版本表单数>_id`」，同已有 `legal_entity_version_id` | 待预判 |
| 任职版本 ID | `assignment_version_id` | 同上 | 待预判 |
| 部门法人归属期间 ID | `org_unit_legal_entity_id` | 归属期间行代理主键，按「`<表单数>_id`」；与同表 `legal_entity_id` 字面相近，易读成「部门的法人 ID」——备选 `org_unit_attribution_period_id`（与已提议错误码中的「归属期间」一致）；见第 12 节 D-9 | 待预判 |
| 员工法人归属期间 ID | `employee_legal_entity_id` | 同上，备选 `employee_attribution_period_id` | 待预判 |
| 来源类型例外清单 | `backend/architecture/source-type-exceptions.txt` | `LegacyShapeTest` 新规则一的例外文件（负责会话指定的文件名） | 待预判 |
| （规则名） | `LegacyShapeTest` 的两条新规则名，如 `SOURCE_TYPE`、`SOURCE_OR_LEGACY_ID` | 测试内规则枚举名，测试代理定稿时报一次 | 待预判 |
| （方法名） | `SourceReferences` 的批量查对照方法 | 集成层内部方法名，实现时定，只在集成层 | 待预判 |
| （命令名） | 按登录名 / 企业微信 userid 查员工 ID 的只读运维命令名 | 仅当 D-8 决定纳入本卡 | 待定 |

负责会话把上表交 one-glossary；确认前不派实现代理（测试代理可先按推荐名写，确认后改名再交实现）。

## 12. 需要负责会话决定的点

| # | 问题 | 建议 |
|---|---|---|
| D-1 | 「`ShortNameAdapter` 移入集成层」若整体搬，集成层要实现 workforce 的端口，产生 integration → workforce 依赖，与现有 workforce → integration 成环（拆分前设计卡要解决的同一问题） | 只把「老系统部门 ID → 自有 ID」的翻译移进集成层 `LegacyOrgNames`（经对照），`OrgShortNames` 交 `OwnObjectId`；workforce 的 `ShortNameAdapter` 只留薄映射，拆分 a 一并收掉 |
| D-2 | 老待办搬迁边界：除 `JdbcLegacyInbox`、配置与 `LegacyInstanceId` 外，`LegacyTodo` 与行任务合并 `InboxMerger.mergeRowTasks`（老系统一行多任务的怪癖）是否一起移；端口名 | 一起移进集成层，端口与条目沿用现名放 `platform.integration.api`；审批只收已合并、不含 `Legacy*Id` 的条目；中性化随 ADR-0039 第 6 条 |
| D-3 | 关系指向本次结果集之外的来源对象（上级、负责人、汇报人不存在） | 同样经对照分配（「首次见到」），自有 ID 不在快照中，由 `SnapshotReconciler` 照现行判为同步问题；翻成空会把「领导不存在」误判为「没有领导」 |
| D-4 | 登录查人（入职先于同步时的登录建档）遇到未见过的人员 | 按首次见到分配，登录建档路径不变；签名票据 subject 只查不分配（第 3.2 节第 4 条） |
| D-5 | 法人归属的对外形状：接口 `LegalEntityAttributionResponse` 的 `subjectKind` + `subjectId`、事件 `LegalEntityAttributed` 等的 `subject` | 接口保留 `subjectKind` + `subjectId`（改 uuid），不改形状；事件 `subject` 扁平为 `subjectKind` + `subjectId`（uuid），与库表分列无冲突（ADR-0033 第 2 项第 10 条只约束存储） |
| D-6 | 回放工具导出文件里的老数字 ID | 工具内存里一一映射为 UUID，不经来源对照、不落库 |
| D-7 | 计划写「6 条路径参数」，现 `openapi.json` 为 7 个路径模板、9 个操作 | 按实际全改 |
| D-8 | ADR-0033 第 3 项第 6 条的只读查询命令（按登录名 / 企业微信 userid 列出员工 ID 与 `user_id`）尚未实现；P3b 后员工 ID 是 uuid，运维无从得知 | 纳入本卡（否则 C-5 无法执行）；命令名进第 11 节；查询依据随 DA-3 改为外部身份 |
| D-9 | 两张归属期间表代理主键列名（第 11 节） | 交 one-glossary 定；倾向按 ADR 规则 `<表单数>_id`，列注释写清 |
| D-10 | 数据访问注册表中四张快照表的血缘是否注明「经来源对照」（§14.1 D14） | 只在注释写明，不新增 `via` 取值（新取值随拆分 a 的集成层登记） |
| D-11 | 执行模型：计划 7.2 的 P3b 行写 sonnet，本卡按负责会话要求为 opus | 以本卡为准，7.2 行由负责会话下次改计划时同步 |
| D-12 | ADR-0033 守护 3 字面是「不得引用名为 `Source*` 的类型」，但 kernel 的事件来源 `kernel.event.SourceSystem` 被十余个领域类（`Timesheet`、`WorkCalendarYear`、`LegalEntity`、`SyncRun`、`ApprovalRequest`、`Role`、`UserAccount`、`NumberingRule` 等）引用，照字面会把它们全部算作违规 | 规则一按 CS-2.29 限为 `platform.integration` 中的 `Source*` 与 workforce 的 `SourceTerms`；`SourceSystem` 随 ADR-0039 第 5 条第 1 款在 P5 删除，不登记例外 |

## 13. 负责会话裁定（one-1a-next-3，2026-10-15；涉及守护与契约形状的条目由 opus 复审确认）

| 编号 | 裁定 |
|---|---|
| RULING-P3B-D1 | `ShortNameAdapter` 不整体搬进集成层（会形成 integration ↔ workforce 依赖环）：只把老系统部门 ID 的翻译移进集成层 `LegacyOrgNames`，workforce 留按自有 `OrgUnitId` 取简称的薄端口；依赖方向保持 workforce → integration |
| RULING-P3B-D2 | 老待办 `LegacyTodo` 与行任务合并 `InboxMerger.mergeRowTasks` 一起移入集成层，沿用现名（中性化按 ADR-0039 第 5.2 条排在生产后） |
| RULING-P3B-D3 | 关系指向本次结果集外的对象时照样分配自有 ID，保持现行「引用不存在 → 同步问题」判定，不改同步语义 |
| RULING-P3B-D4 | 登录查人遇到未见过的人员按首次见到分配自有 ID（与同步同一分配路径、同一来源对照） |
| RULING-P3B-D5 | 法人归属接口沿用现有字段名 `kind`，id 字段为 `subjectId`（uuid 字符串）；事件 `subject` 扁平为 `kind` + `subjectId`；库表按 DA-5 分列（RULING-P3B-5 更正：不改名为 subjectKind） |
| RULING-P3B-D6 | 回放工具中的老数字 ID 在内存中映射为 UUID，不经来源对照、不写库 |
| RULING-P3B-D7 | 路径参数按 `openapi.json` 实际 7 个模板、9 个操作全改（计划「6 条」为计数笔误） |
| RULING-P3B-D8 | 纳入本卡：ADR-0033 第 3 项第 6 条要求的只读运维命令（按 A8 登录名或企业微信 userid 返回匹配的员工 ID 与 `user_id` **列表**，不按员工编号查）；理由：P3b 后员工 ID 为 uuid，无此命令 C-5 剧本无法执行（CS-1.9）。命令名随技术标识交 one-glossary |
| RULING-P3B-D9 | `org_unit_legal_entity_id` / `employee_legal_entity_id` 与备选 `*_attribution_period_id` 交 one-glossary 定名；确认前测试代理按推荐名写 |
| RULING-P3B-D10 | 数据访问注册表血缘只在注释写「经来源对照」，不新增取值 |
| RULING-P3B-D11 | 执行用 opus（体量大），计划 7.2 P3b 行的「sonnet」以本卡为准，随 P3b PR 同步改 |
| RULING-P3B-D12 | `LegacyShapeTest` 新规则「领域与 api / events 不得引用 `Source*` 类型」按 CS-2.29 限定为 `platform.integration.api.Source*` 与 workforce 的 `SourceTerms`，不含 kernel 的 `SourceSystem`（事件来源，P5 随信封 `source` 删除，ADR-0039 第 5.1 条） |
| RULING-P3B-9（改判，2026-10-15，one-coordinator 同意） | 只读运维命令 find-accounts-by-external-subject 留在集成层（RULING-P3B-7），按外部主体查**组织快照**中的员工，经 kernel 端口 `ExternalSubjectEmployees`（入参中性：登录名 / 企业微信 userid 两种查询，不带 provider_kind 取值、不出现外部系统名；集成层问、workforce 实现），账号经 kernel `AccountEmployees`；端口登记在 `module-dependencies.txt` kernel 端口注释区，到期条件：C1（联合身份）落地后按 C1 的身份模型删除或改形（DA-3 时复核是否仍需要；NEXT_BATCH_PLAN 7.8）。不采用 one-architecture 的方案 3（命令放 identity、集成层只给外部主体 → 用户 ID / 员工 ID 的 api）的理由：① 集成层 api 已在 12 个方法上限，新增解析 api 须再减方法或动上限守护；② identity 侧命令参数须另立中性的「外部主体种类」取值并过术语门禁，且与 one-glossary 已定的 provider_kind 取值形成两套；③ 验收 `ExternalSubjectLookupCommandIT` 须整体改写与搬目录；④ C1 会重做身份与外部主体模型，方案 3 的形状届时大概率还要再改。**被推翻的原裁定**：不新增 kernel 端口，命令经 `OrgSource` 登录查人取来源人员、再经 `SourceReferences` 只查不分配翻成员工 ID。**推翻理由**：命令要的是系统里已有的快照员工 ID（只有已同步的人才有员工 ID 与账号），按来源查语义不对；dev 常关组织源连接，命令恒为「无人」；验收 `ExternalSubjectLookupCommandIT` 只有快照、没有组织源，必红；集成层不能依赖 workforce（integration → workforce 成环），只能经 kernel 端口 |

## 14. 验收阶段补充裁定（2026-10-15）
- 卡第 4 节第 3 条中「编号为空也正常建档」子用例删去：RULE-361 取数范围排除空编号、`employees.employee_code` NOT NULL，以规则卡为准。
- RULING-P3B-4：`OwnRolesIT` 以员工 uuid 作账号路径改断言为 404、不授出任何角色、不写授予审计（强度不降）。
- RULING-P3B-6：签名票据 subject 接受完整 64 位有符号范围（A8 人员 ID 可为负、可达 19 位），补验收。
- 老待办配置键搬迁时按 G46 改集成层前缀，键名由实现定后再出裁定改测试。

## 15. 复审修复裁定（one-1a-next-4，2026-10-15；依据 opus 复审 @2d4e68b0：修后可合并，1 High、4 Medium、12 Low）
- RULING-P3B-13（M1，RULE-356）：`SourceOrgSnapshot` / `OrgSource` 契约写明 relationships 按来源人员 ID 有符号升序；补跨层测试（真实来源适配器 + 对账：同部门两负责人先插 805 再插 -5，断言负责人为 -5 对应员工且有 MULTIPLE_HEADS）。`OrgUnitSyncRulesTest` 输入改序在 PR 单列。
- RULING-P3B-14（M2，C8 告警频率）：新增计数器 `forcitis.platform.integration.legacy.inbox.unmapped`；同一员工每个自然日（注入时钟）只发一次 ALERT，其余降为 WARN。测试：同员工连调 3 次，均不可用、计数 3、ALERT 1 次。
- RULING-P3B-15（M3，集成层运维档案）：档案加 `spring.flyway.enabled=false`；注释改为如实描述（启动时只读连库）；补测试：未知或缺 `--provider-kind` → 2、空白 `--external-subject` → 2、查无此人 → 1、`audit_entries` 不变、档案冒烟（无 Flyway 迁移、无调度任务）。验收目录内的由测试代理以 Acceptance-Test-Author 提交。依赖 #228（已合并）。
- RULING-P3B-16（M4，旧配置键）：配置中出现任何 `forcitis.platform.approval.legacy.*` 键即拒绝启动，消息给出对应新键名（CLAUDE.md 第 6 条）；`DatabaseTransportCheckTest`「旧键不读」用例改为断言启动失败，PR 单列；one-ops 迁移 one.env 键名经 one-coordinator 转。
- RULING-P3B-17（H1，只读核实命令连接，one-1a-par 构件，已获同意）：等 #235 合并后在 P3b 上 rebase 再改。枚举改 `INTEGRATION_LEGACY_INBOX("integration-legacy-inbox", …INTEGRATION_LEGACY_INBOX_URL/_USERNAME/_PASSWORD)`；`--connection=legacy-approval` 退出码 2 并提示新名；同步档案、INFRA_RUNBOOK §4.1、`ReadOnlyVerificationCommandIT`（测试代理）；单元测试 `verificationConnectionsAreTheRuntimeOnes` 从运行时连接定义推导（不另写字面量）。指标标签改名、告警规则、C-14 剧本、one.env 键名经 one-coordinator 转 one-ops，与 rc 发布顺序对齐。
- Low：L1 改用类引用；L2 发起人流程变量解析移进 try；L3 票据 subject 规范形校验；L4 同级次序变化 PR 单列（不改）；L5 回放表单值 "0" 映射为空白；L6 删死代码；L7 完整类名改 import；L8 `SourceReferences.allocate` 限定组织来源；L9 `LegacyInboxProperties` 重写 toString 遮蔽口令；L10 INFRA_RUNBOOK 写明首次全量同步较慢；L11 核对术语与事件目录；L12 不改（生成器局限）。
- L8 落法偏离（one-coordinator 同意）：未在 `SourceReferences.allocate` 运行时限定组织来源，改为 ArchUnit 守护 `SourceReferenceAllocationGuardTest`（主代码只许组织来源适配器调用，带负向样例），因 `LegacyReferencesIT`、`StalledExternalSourceIT`、`ReadOnlyAccountCheckIT` 经同一方法分配 LEGACY_SYSTEM 对照。后续：测试代理把这些验收改走测试专用种子路径后，再评估是否加运行时限定（不急）。
- rebase 适配：#228 新增的 `SessionPurgeClockIT` 两个局部变量随 ID 改 UUID（机械改动，沿用 RULING-P3B-2）。
- RULING-P3B-18（rebase 到含 R-1b-0a #250 的 main 后的验收适配，机械改动，断言含义不变）：`A8SourceFlagsIT.unitName` 原直查 `platform_integration.source_references` 取自有 ID，P3b 后被 `TestIsolationGuardTest` 拦，改经 `SourceReferences.findOwnId`（同样只取已确认对照）；其老待办关闭开关从退役旧键改为 `forcitis.platform.integration.legacy.inbox.url`（RULING-P3B-10 / 16）。
- RULING-P3B-19（合并 main 含 #251 TestMssql，one-coordinator 要求 merge 不 rebase）：`StalledExternalSourceIT`、`ReadOnlyVerificationCommandIT`、`SeeyonViewsMssqlIT` 冲突机械合并（P3b 新键与连接名 + main 的共用容器与按类库名），断言不变。
- RULING-P3B-20：P3b 新增的验收支撑 `OrgSourceWorldSupport` 自起 SQL Server 容器，被 #251 的 `TestContainerGuardTest.sqlServerContainersAreStartedThroughTestMssql` 拦下；由测试代理改用 `TestMssql.shared()` / `database()` / `login()`（按类生成库名与登录名），断言不变。同时把其底表 `org_unit_rows` 改名为 `dbo.ORG_UNIT` 并加 `TYPE` 列（根单元 `Account`、其余 `Department`）：#250 后启动检查要求基表 `dbo.ORG_UNIT.TYPE`，三个使用者（`NegativeSourceIdIT`、`LegacyReferencesIT`、`SourceIdMatchingIT`）合 0a 后原本即启动失败，夹具补齐后视图与断言不变。
