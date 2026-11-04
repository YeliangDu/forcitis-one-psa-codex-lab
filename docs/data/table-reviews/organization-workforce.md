# 数据设计审查：organization_workforce（15 张表）

> 冻结（2026-11-04，#1263）：表审查记录，已完成，不再更新。

- 审查：one-data 子代理，2026-10-16；依据 `DATA_DESIGN_GATE.md`（草案）§3、DATA_DESIGN_CHECKLIST §14.1 D1–D19、NAMING_STANDARD §2.4 / §2.4a / §2.5、ADR-0033、ADR-0043、注册表、事件目录、规则卡、信息图。
- 简称：`M` = `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql`；`REG` = `backend/architecture/data-access-registry.yaml`；`J/…` = `backend/src/main/java/com/forcitis/one/organization/workforce/…`。
- 范围说明：只写不通过项；通过项不展开。**整表不适用**：A1–A8（本 schema 无分析事实表，版本表是维度）、O11（无大表，`sync_runs` 约 10.5 万行 / 年、保留 2 年）、O13（本 schema 就是法人 / 期间维度的来源）、T3 / E3（无迁入映射）。B1 无卡对照，按信息图推定：positions=OBJ-58，org_units=OBJ-55，employees=OBJ-56，assignments / assignment_versions=OBJ-57，legal_entities / legal_entity_versions=OBJ-54，两张归属期间表 = OBJ-55 / OBJ-56 的「默认法人」「数据范围法人」关系行，其余为技术表（SyncRun 用例）。
- 条目对应：本工作区的 `DATA_DESIGN_GATE.md` 没有 B3a、O15、附录 C（见「未核实」），B3a 按任务说明理解为「状态 CHECK、事件目录、Java 状态机 / 枚举」三方一致。三方核对结果：`SyncRunStatus`（5 值）、`OrgUnitStatus`、`PositionStatus`、`LegalEntityStatus`、`EmploymentStatus`、`HoldKind`、`SyncTrigger`、`EntityKind`、`ChangeKind`、`Gender`、`RegionCategory` 与 M 的 CHECK 取值逐值一致；`SyncRun.MACHINE`（RUNNING→SUCCEEDED/HELD/FAILED；HELD→HELD/RELEASED）与事件目录 6 个 SyncRun 事件一致；`IssueKind`（16 值）无 CHECK（见 SD-2）；信息图 OBJ-54 状态与 CHECK 不一致（见 LE-1）。

## 逐表结论

| 表 | 结论 | 阻断 / 高 / 中 / 低 |
|---|---|---|
| positions | 需修改 | 0 / 1 / 1 / 1 |
| org_units | 需修改 | 0 / 0 / 2 / 4 |
| employees | 需修改 | 0 / 1 / 1 / 2 |
| assignments | 需修改 | 0 / 0 / 2 / 0 |
| sync_runs | 需修改 | 0 / 0 / 3 / 4 |
| sync_diffs | 需修改 | 0 / 1 / 2 / 1 |
| legal_entities | 需修改 | 0 / 0 / 3 / 0 |
| legal_entity_attributions | 需修改 | 0 / 0 / 1 / 1 |
| org_unit_legal_entities | 通过 | 0 / 0 / 0 / 0 |
| employee_legal_entities | 通过 | 0 / 0 / 0 / 0 |
| org_unit_versions | 需修改 | 0 / 0 / 1 / 0 |
| assignment_versions | 需修改 | 0 / 0 / 1 / 0 |
| legal_entity_versions | 需修改 | 0 / 0 / 1 / 0 |
| legal_entity_attribution_corrections | 需修改 | 0 / 0 / 1 / 1 |
| sync_run_held_separations | 需修改 | 0 / 1 / 2 / 0 |
| 跨表共性（X 系列，不计入各表） | — | 0 / 0 / 7 / 6 |
| **合计** | | **0 / 4 / 28 / 20** |

## 问题清单

### positions

**P-1 [高] O6 / D17 细则 2、3**　岗位属受控目录（主数据 / 参考数据），当前覆盖写，无版本表也无「不需要」豁免。证据：REG L50-55 `retention: CURRENT_STATE`、无数据类别与时态字段；`J/infrastructure/persistence/JdbcOrgSnapshotRepository.java` `save()` 对 `positions` 做 `on conflict (id) do update set code, name, sort_order, status`；`assignment_versions.position_id`（M:L352）只存 ID，岗位名称与状态一改，历史任职上读到的是今天的岗位；`DATA_SCORECARD.md` 第 6 行「覆盖，无历史」仍未关闭。建议：三选一（见「待用户决定」1）：加 `position_versions`（维度型，OBSERVED，列覆盖 code / name / sort_order / status）；或在注册表写「不需要」并选 D17 细则 2 四类理由之一（须用户认可）。依据：D17 细则 1–3、D6。

**P-2 [中] O8**　`code` 无格式 CHECK、`name` 无长度上限（M:L7-8）；删除类状态用语与组织单元不一致：岗位用 `DELETED`，组织单元用 `REVOKED`，含义同为「组织源不再返回」（M:L10 对 M:L31）。建议：统一词表（`terms.yaml` 已有 `revoked`），或在列注释写明两者为何不同。依据：NAMING_STANDARD §2.4a-5、§2.4（枚举词汇）。

**P-3 [低] C1**　可空列注释未写「为空表示」：`code`（M:L17）、`sort_order`（M:L19）。依据：§2.4a-3、D10。

### org_units

**OU-1 [中] O4 / B3**　`status = 'REVOKED'` 与 `revoked_at` 成对约束缺失。证据：M:L31、L37；`JdbcOrgSnapshotRepository.upsertOrgUnit` 写 `revoked_at = case when status = 'REVOKED' then … end`，两者恒同时有值或同时为空。建议：`CONSTRAINT ck_org_units_revoked_at CHECK ((status = 'REVOKED') = (revoked_at IS NOT NULL))`。依据：D2、门禁 B3「终态与时间 / 原因成对」。

**OU-2 [低] O8 / D10**　同一属性两个列名且口径漂移：`org_units.region_category`（M:L34）对 `org_unit_versions.region_kind`（M:L318）。`ddl-naming-baseline.txt:6` 已登记 `region_category` 待改名为 `region_kind` 并「加 CHECK」，现 CHECK 为未命名内联（M:L34）。版本表已用目标名，快照表仍是旧名，`SELECT_ORG_UNITS`（`OrgSnapshotRows.java:24`）与 `OrgHistory.java` 靠别名在两个名字间转换。建议：基线开放期内直接改名并具名为 `ck_org_units_region_kind`，清掉基线行。依据：NAMING_STANDARD §2.4（`_kind` 枚举、`ck_<表>_<列>`）、G-D12。

**OU-3 [中] O6 / D17 细则 3、4；D11**　（a）列覆盖：`sort_order`、`short_name` 不在 `org_unit_versions`（M:L307-328），注册表无逐列不进版本的理由（REG L56-64）。（b）一行两个来源：`short_name` 由「每天从老系统只读刷新」（M:L55；`J/application/RefreshShortNames`、`updateShortNames`），其余列来自组织源，同一行被两个来源写；`ShortNameAdapter` 对 ID 的假定见 ADR-0033 第 2 项第 6 条，DA-8（改为自有属性）未落。建议：逐列登记理由；`short_name` 在 DA-8 落地前单列登记为跨来源列，落地后拆表或改自有属性。依据：D17 细则 3、4；ADR-0033 第 2 项第 6 条。

**OU-4 [低] O4**　自引用未排除：无 `CHECK (parent_org_unit_id <> id)`（M:L29、L460-462）；成环只在应用里查（`J/domain/OrgHierarchy`、`findAncestors` 深度上限 64）。成环落不成 CHECK，写入卡即可。依据：D2、B5。

**OU-5 [低] B3 / B7**　信息图 OBJ-55 属性「单元类型：公司 / 事业部 / 区域 / 部门 / 交付中心等」已由用户定（Q37a、Q37b），表里只有 `region_category` 两值与 `is_delivery_unit`，没有单元类型列。当前阶段只同步来源字段，不要求现在补；登记为差距，6e 起补。依据：信息图 OBJ-55；CS-1.8（不提前建）。

**OU-6 [低] C1**　可空列注释缺「为空表示」：`code`（L46）、`sort_order`（L49）、`head_employee_id`（L51）、`regional_receivables_manager_id`（L52）、`revoked_at`（L56）、`last_sync_run_id`（L57）。依据：§2.4a-3。

### employees

**E-1 [高] O6 / D17 细则 2**　员工是主数据，只有当前投影，无版本表也无豁免。证据：REG L65-72（`CURRENT_STATE`）；`employment_status`、`is_fee_earner`、`employee_code`、`display_name` 均直接覆盖（`JdbcOrgSnapshotRepository.upsertStaff` 的 `on conflict … do update`）；任职历史虽在 `assignment_versions`，但在职状态与工号无历史，规则卡 `WORKFORCE_HISTORY_RULES.md`（候选）要求「每次离职各记一条、复职后再离职不覆盖旧值」，现表结构做不到（见 E-2）。`DATA_SCORECARD.md` 第 8 行「生效区间 ✗」。建议：见「待用户决定」1；若选「不需要」，唯一可用的是「个人信息最小化只留当前值」，须用户认可并同时回答 E-2。依据：D17 细则 1–3、D6。

**E-2 [中] O4 / B3 / B8**　`separated_at` 语义与已写规则冲突，且缺成对约束。证据：M:L72、L92 注释「首次同步到离职的时间」；`JdbcOrgSnapshotRepository.java:150-151` 复职时置空、首次部署时已离职的人取部署当天时刻；`WORKFORCE_HISTORY_RULES.md`（候选规则，第 2 节）写「首轮快照里已是离职状态的员工为未知，不记部署时刻」「每次离职各记一条」，并写「出现具体消费方之前不建列」；提议术语 `separation_observed_at`（`backend/glossary/proposed/organization_workforce.yaml:314`），`separation_date` 已禁用。`employment_status = 'SEPARATED'` 与 `separated_at` 无 CHECK（L70、L72）。建议：待用户定列去留（见「待用户决定」2）；若留，改名 `separation_observed_at`，加 `ck_employees_separated_at`（`(employment_status = 'SEPARATED') = (separated_at IS NOT NULL)`，首轮已离职为「未知」则改为单向 `separated_at IS NULL OR employment_status = 'SEPARATED'`）。依据：D2、D10、B8、CS-1.8。

**E-3 [低] O10 / O8**　登录路径查询无索引：`findStaffByLoginName`、`findEmployeeIdsByLoginName` 按 `login_name` 查（`JdbcOrgSnapshotQueries.java:37、43`），无索引（M:L66）；`findStaffByWecomUser`、`findEmployeeIdsByWecomUser`（`:31、49`）不带 `employment_status = 'EMPLOYED'`，用不上部分唯一索引（M:L79-80），离职行走全表扫。`login_name` 注释允许空串代空值（M:L86「null 或空串」），违反 §2.4a-3。以上两列按 ADR-0033 第 4 项第 3、5 条将在首次部署前随 DA-3 删除，故只记低；DA-3 前不补索引，但注册表应写明「随 DA-3 删除」。依据：O10、§2.4a-3、ADR-0033。

**E-4 [低] O10 / O8**　`employees_employee_code_idx`（M:L81）无查询路径使用：全仓 `employee_code` 只出现在 select 列表与 upsert（`OrgSnapshotRows.java:31`、`JdbcOrgSnapshotRepository.java:142-146`），多余索引；`email`、`gender` 的改名仍在 `ddl-naming-baseline.txt:4-5`（`email_address`、`gender_code`）。建议：删索引或补查询路径；基线开放期内把两个改名做掉。依据：§2.4a-2（无冗余）、§2.4。

### assignments（含 assignment_versions 的同名列）

**A-1 [中] B4 / O8**　`position_id` 可空（M:L102；`assignment_versions` M:L352），但注释写「正常数据不为空」（M:L113），代码在写入前就隔离缺岗位的人（`EmployeeChecks.java:32` `ISOLATED_MISSING_POSITION`；`LoginMissRegistration.java:46-55` 同检查），`Assignment` record 却允许空（`domain/Assignment.java`）。能落成 `NOT NULL` 的没落。建议：两表 `position_id` 改 `NOT NULL`，`Assignment` 去掉可空；若确有「无岗位在职」场景，在注释写明并给规则卡出处。依据：B4（基数 1 → NOT NULL）、D2。

**A-2 [中] O4 / B5**　「本人不能是自己的直属领导」只在注释与代码里：M:L114、`IssueKind.SUPERVISOR_IS_SELF`；`assignments.reports_to_employee_id`（M:L103）与 `assignment_versions.reports_to_employee_id`（M:L353）无 `CHECK (reports_to_employee_id <> employee_id)`。「已离职」「成环」只能在应用里保证（跨行），写入卡说明。依据：RULE-372、D2。

### sync_runs

**SR-1 [中] B3 / O4**　状态与原因 / 时间字段无成对 CHECK，门禁 B3 明文要求「原因必填的终态有成对 CHECK」。证据：M:L120-149 除 `status` 枚举外无任何跨列约束；`SyncRun.fail()`（`J/domain/SyncRun.java`）不校验原因非空，`BeginSyncRun` 以固定串关闭遗弃运行。建议（取值须对照 `SyncRunStateMachineTest` 复核，见 opus 项 1）：`ck_sync_runs_failure_reason`：`status <> 'FAILED' OR failure_reason IS NOT NULL`；`ck_sync_runs_held_reason`：`status <> 'HELD' OR (held_reason IS NOT NULL AND cardinality(hold_kind_codes) > 0)`；`ck_sync_runs_released`：`status <> 'RELEASED' OR (released_at IS NOT NULL AND released_by_run_id IS NOT NULL)`；`ck_sync_runs_finished_at`：`(status = 'RUNNING') = (finished_at IS NULL)`。依据：B3、§14.1 D2（成对 / 互斥落约束）。

**SR-2 [中] O4**　其余可落约束：（a）`released_kind_codes <@ hold_kind_codes`（`SyncRun.hold()` 在类型变化时清空已放行类型，`J/domain/SyncRun.java:190-197`，不变量成立）；（b）12 个统计列要么全空要么全有（`SyncRunRows.stats()` 以 `source_org_unit_count` 为空作哨兵，`SyncRunRows.java` 约 L75-78）；（c）计数列 `>= 0`（M:L126-137）；（d）`finished_at >= started_at`；（e）`held_reason`、`failure_reason` 长度（代码按 2000 截断，`JdbcSyncRunRepository.java:93、141`，库里无上限）。依据：D2、§2.4a-5。

**SR-3 [中] O10**　登录路径热点查询无合适索引。证据：`SnapshotFreshness.isCurrent()` 经 `OrgDirectoryService.java:53` 在登录判定时调用，执行 `select max(finished_at) … where status = 'SUCCEEDED'`（`JdbcSyncRunQueries.java:59`）；`sync_runs_status_idx`（M:L151）只含 `status`，SUCCEEDED 占几乎全部行（每 5 分钟一次，约 21 万行 / 2 年），索引选择性为零，需扫描全部 SUCCEEDED 索引项；`SyncMetrics` 的 gauge 也调用同一查询。`findHeld`、`countHeld`、`findAbandoned`、`findHeldExcept` 只关心 HELD / RUNNING，应走部分索引。分页 `order by started_at desc, id desc`（`:42-49`）对应索引 `sync_runs_started_at_idx`（M:L150）缺 `id` 兜底。建议：以 `(finished_at DESC) WHERE status = 'SUCCEEDED'` 与 `(started_at, id) WHERE status IN ('RUNNING', 'HELD')` 两个部分索引取代 `sync_runs_status_idx`；`started_at` 索引改 `(started_at DESC, id DESC)`。依据：O10、D19、§2.4a-2。

**SR-4 [低] B3**　放行原因与逐类放行人只在事件 / 审计里：`SyncRunReleased.reason`、`SyncRunHoldReleased.releasedBy`（`docs/architecture/events/workforce.yaml`）无对应列，表里 `released_by` 只记最终放行人，被后续成功同步取代时为空（M:L172）。审计在线 3 年、归档至 10 年（GUIDING §6），长于本表 2 年，故判低；若要在表上自洽，加 `release_reason`。依据：B3、D12。

**SR-5 [低] O3　待核**　「同一时刻至多一条 RUNNING」未落库：`BeginSyncRun.java:32-37` 先关闭超 15 分钟的遗弃运行再 `insertRunning`，不检查现有 RUNNING；靠 ShedLock（`OrgSnapshotSyncJob`，`lockAtMostFor PT4M`）与 advisory 写锁。定时与管理员触发（`MANUAL`）能否并发起 RUNNING，我没有测试证据。若业务要求单飞，加 `CREATE UNIQUE INDEX … ON sync_runs ((true)) WHERE status = 'RUNNING'`。依据：O3、O4。

**SR-6 [低] O9**　`org_unit_change_count`、`employee_change_count`、`position_change_count`、`issue_count`、`isolated_row_count`（M:L133-137）可由 `sync_diffs` 数出，属派生值，未登记为投影；因 diffs 1 年清理、runs 2 年保留，它们是长期摘要，保留合理，但须登记「投影 + 可重算期限 + 对账」。依据：D1。

**SR-7 [低] C1 / O8**　注释与代码不符：`hold_kind_codes` 注释「状态为 HELD 时才有值」（M:L179），但 `release()`、`succeed()` 不清空它（`SyncRun.java:176、228`），RELEASED 行保留（对审计有用）；改注释即可。未具名的枚举 CHECK 见 X-7。依据：D10。

### sync_diffs

**SD-1 [高] O9 / O8**　`subject_key` 把对象类别与 ID 拼成字符串：`"employee:" + id`、`"position:" + id`、`"org-unit:" + id`（`SnapshotReconciler.java:58、148、246`；`EmployeeChecks.java:24`），`entity_kind` 已有一列，ID 是自有 UUID；注释举例 `employee:2001`（M:L202）是老系统数值 ID 形状，已不符。建议：`subject_key text` 改 `subject_id uuid NOT NULL`，注释改写。依据：D1（无多值拼字符串）、§2.4（`_id` 一律 uuid）、D16。

**SD-2 [中] B3 / O8 / O4**　`issue_kind` 无 CHECK（M:L189），`IssueKind` 有 16 个取值；`ddl-naming-baseline.txt:8` 仍登记待补。`change_kind = 'ISSUE'` 与 `issue_kind` 非空应成对（`SyncDiff.issue()` / `change()` 工厂保证，M 未约束）。`changed_field_codes` 元素是 Java 属性名（`parentOrgUnitId`、`loginName` 等，`SyncedFields.java`），无取值约束，且含将随 DA-3 删除的字段。建议：补 `ck_sync_diffs_issue_kind`（16 值）、`ck_sync_diffs_issue_pair`（`(change_kind = 'ISSUE') = (issue_kind IS NOT NULL)`）；字段代码改词表名并加 `<@` 约束。依据：D5、G-D12、B3。

**SD-3 [低] O10**　`sync_diffs_sync_run_id_idx`（M:L196）与唯一约束 `uk_sync_diffs_sync_run_id_diff_number`（M:L194）前缀相同，冗余；`ddl-naming-baseline.txt:65` 还在为它登记改名。建议：删除该索引与基线行。依据：§2.4a-2。

**SD-4 [中] O7 / O8**　注册表登记 `APPEND_ONLY`（REG L105-111），库内未禁 UPDATE（版本表有触发器，M:L511-530），`updated_at` 列对永不更新的行无意义；`detail` 无长度上限（代码截 2000，`JdbcSyncRunRepository.java:137`）。清理作业需要 DELETE，故只禁 UPDATE 即可。建议：加禁止 UPDATE 的触发器，`detail` 加长度 CHECK。依据：D8、§2.4a-5。

### legal_entities

**LE-1 [中] B3 / B3a**　信息图 OBJ-54 的关键状态是「筹建 → 存续 → 注销」，CHECK 只有 `ACTIVE`、`INACTIVE`（M:L214），状态机允许 `INACTIVE → ACTIVE` 反向（`LegalEntity.java:28-30`，`LegalEntityStatus`），而「注销」不可逆；列注释写「启用 / 停用」（M:L225），与信息图用语不对应，`aggregates.yaml` 的 states 也是两值。建议：用户 / one-architecture 判：二值是否即信息图状态的当前阶段子集；若是，在卡与注释写映射并说明「筹建、注销」未落库的来由（B7 不适用，信息图未标暂不定）；若否，补取值。依据：B3、门禁 §3.1。

**LE-2 [中] O6 / D17 细则 5　待核**　新系统自有主数据，根表仍存可版本化的列：`legal_entities.code / name / status`（M:L211-217）与 `legal_entity_versions` 同列（M:L383-385）并存，`JdbcLegalEntityRepository.insert/save` 同事务写两处（`history.recordLegalEntity`）。D17 细则 5：新系统自有的根表不存可版本化的列，按业务日期取当时版本；外部快照才保留当前投影并须有一致性测试。是否有「根表与最新版本一致」的测试，我没有核实（只看到 `LegalEntityHistoryIntegrationTest` 的追加与禁改断言）。建议：注册表登记「当前值投影 + 一致性测试」或改为根表只留不可版本化的列。依据：D17 细则 5、D1。

**LE-3 [中] O8**　`legal_entities_code_format`、`legal_entities_code_key`、`legal_entities_pkey` 仍是默认 / 旧名（`ddl-naming-baseline.txt:31-33`）；`name` 无长度上限、无业务唯一（是否要求名称唯一属业务口径，见「待用户决定」7）。依据：§2.4。

### legal_entity_versions

**LV-1 [中] O3 / O8**　（a）`code` 无格式 CHECK（M:L383），而主表有 `^[A-Z]{2,10}$`（M:L219），Java `LegalEntityCode` 同一正则，版本表可被写入非法代码。（b）业务唯一只管「实体 × 区间」（M:L392），未管「同一日期一个代码只属一个法人」；代码用于单据编号 `{LE}`（M:L223），历史单据按日期反查代码，重叠即歧义。建议：加 `ck_legal_entity_versions_code_format`；加排他约束 `EXCLUDE USING gist (code WITH =, daterange(effective_from_date, effective_to_date) WITH &&)`（同日再修订产生的空区间不参与）。依据：O3（带区间的用排他约束）、§2.4a-5、D6。

### legal_entity_attributions（含两张期间表的约束说明）

**LA-1 [中] D3 / O4**　期间表（M:L259-297）与更正表（M:L407-426）的子行不指向聚合根 `legal_entity_attributions`（持有 `version`），根行存在与否由 `JdbcLegalEntityAttributionRepository.save` 先写根保证，库里不拦。根的 `org_unit_id`、`employee_id` 各有唯一约束（M:L246-247），可作外键目标。建议：期间表对根加外键（`org_unit_id` / `employee_id` 引用根的同名唯一列），或写明不建的理由。依据：D3（子表随根）、D2。

**LA-2 [低] O4　无法落约束的说明**　更正后的期间结束日取「最新更正的新结束日」（`EffectiveAttributionPeriods.select`），不受期间表排他约束（M:L272、L296）约束；聚合 `correct()` 校验「不得与同主体其他期间相撞」（`LegalEntityAttribution.java:124-125`）。若绕过聚合写入造成相撞，`JdbcLegalEntityResolutionSources.attribution()`（`:37` `.optional()`）会抛多行异常而非静默出错，属于失败可见。请在卡里写明「只能在应用里保证，原因：更正与期间分表，约束跨两表」。依据：O4（写明为何落不成约束）。

### org_unit_versions

**OUV-1 [中] D2 / D17 细则 5**　同 schema 的引用无外键：`parent_org_unit_id`、`head_employee_id`、`regional_receivables_manager_id`（M:L314-317）。注册表 `fk_exceptions` 的理由是「版本不自带键」（REG L82-84），但代码从不物理删除快照行（全仓对 org_units / employees / positions 无 DELETE，只有 sync_runs、sync_diffs、held_separations 有，见 `JdbcSyncRunRepository.java:107、116`、`SyncPurgeJob.java:69、74`），外键不会阻碍任何现有操作，却能拦住错 ID。建议：撤销这三条例外，加外键（`DEFERRABLE INITIALLY DEFERRED` 与快照表一致）。例外由 one-coordinator 接受，见「待用户决定」8。依据：D2、§5。

### assignment_versions

**ASV-1 [中] D2**　同上：`position_id`、`reports_to_employee_id`（M:L352-353）无外键，`fk_exceptions` 见 REG L92-94。`employee_id`、`org_unit_id` 已有外键（M:L359-360），说明同一张表里外键可建。建议同 OUV-1。依据：D2、§5。

### legal_entity_attribution_corrections

**CR-1 [低] O2**　`correction_number bigint GENERATED ALWAYS AS IDENTITY`（M:L409）：由数据库序列赋值，虽然不是主键也不是 `_id` 列，但 ADR-0033 第 1 项第 1 条的精神是「数据库不分配标识」；「序号最大的为准」靠它排序（M:L433、`EffectiveAttributionPeriods.select` 的 `order by correction_number desc`），事务提交顺序与序号顺序不一致时的语义由聚合 `version` 串行同主体写入兜底。建议：改为聚合内序号（应用侧由 `legal_entity_attributions.event_count` 或同期间已有更正数 + 1 赋值，`(subject, period_from, sequence)` 唯一）。依据：O2、ADR-0033 第 1 项。

**CR-2 [中] O4 / O8**　`new_effective_to_date <> previous_effective_to_date` 无 CHECK（空更正）；`correction_reason` 只有非空白检查（M:L422），无长度上限；两个索引（M:L427-430）对另一类主体的行为空列，可加部分索引。依据：D2、§2.4a-2、-5。

### sync_run_held_separations

**HS-1 [高] O2**　复合主键 `(sync_run_id, employee_id)`（M:L449）。ADR-0033 第 1 项第 2 条逐表点名：`sync_run_held_separations（sync_run_id, employee_id）` 须改 UUIDv7 主键 + 命名唯一约束；`primary-key-type-baseline.txt:5` 登记为 R-1b 待改欠账，仍是已采纳 ADR 的明确违反。建议：加 `sync_run_held_separation_id uuid PRIMARY KEY`（应用侧 UUIDv7），原复合键改 `uk_sync_run_held_separations_sync_run_id_employee_id`；同时处理 HS-3。依据：ADR-0033 第 1 项第 1、2 条；O2；D16。

**HS-2 [中] C3**　注册表保留期与实际不符：REG L112-118 登记 `1y`、作业 `organization.workforce.sync.purge`，但 `SyncPurgeJob` 只删 `sync_diffs` 与 `sync_runs`（`SyncPurgeJob.java:69、74`），本表只在 runs 被删时经 `ON DELETE CASCADE`（M:L450）连带删除，实际保留 = 运行保留期（2 年），不是 1 年；GUIDING_PRINCIPLES §6（L115）只列 `sync_runs` / `sync_diffs`，本表没有认可记录。表内容是「待离职名单」，规则卡认定未公开的离职信息敏感（`WORKFORCE_HISTORY_RULES.md`「敏感性与最小列」）。建议：见「待用户决定」3；无论取几年，作业要真删本表（或注册表改写为随运行删除）。依据：D12、ADR-0043 R-2（待核是否适用，列均为代理键）。

**HS-3 [中] O10**　外键 `employee_id`（M:L451）没有以它开头的索引（主键以 `sync_run_id` 开头，M:L449），删除员工行（不会发生）或按人查「在哪些暂停运行里待离职」都要扫全表。随 HS-1 改键后补 `ix_sync_run_held_separations_employee_id`。依据：§2.4a-2（外键列必须有索引）。

### 其余表（positions 外的版本 / 归属表）

`org_unit_legal_entities`、`employee_legal_entities`：O2、O3（`uk … effective_from` 与排他约束）、O7（触发器）、O10（`legal_entity_id` 索引见 M:L474-475）、C1 均通过；初始数据来源（迁入 / 手工）见「未核实」。

## 跨表共性问题

**X-1 [中] B2 / D3**　聚合归属未登记且文档不一致。REG 中 `positions`、`org_units`、`employees`、`assignments`、`org_unit_versions`、`assignment_versions` 无 `aggregate` 字段（REG L50-96）；事件与 Java 却引用 `OrgSnapshot`（`multi-aggregate-exceptions.txt:6`、`SyncRun.draftWithSnapshot`）与 `Employee`（`docs/architecture/events/workforce.yaml` EmployeeRegistered 的 `aggregate: Employee`），而 `architecture/ddd/aggregates.yaml` 里没有 `OrgSnapshot`，`RegisteredEmployee` 聚合 `tables: []`，`LegalEntity` 的表清单漏了 `legal_entity_versions`（注册表标其属 LegalEntity）。建议：补 `OrgSnapshot`（或明确快照是「读模型 / 外部镜像」不属聚合）并给六张表登记；`Employee` 与 `RegisteredEmployee` 二选一。依据：B2、D3。

**X-2 [中] E1**　事件目录缺 `relatedAggregates`：`SyncRunSucceeded`、`OrgSnapshotChanged` 在 Java 里带 `AggregateReference("OrgSnapshot")`（`J/domain/SyncRun.java` `draftWithSnapshot`），`multi-aggregate-exceptions.txt:6` 声称「在 relatedAggregates 里列出 OrgSnapshot」，但 `docs/architecture/events/workforce.yaml` 这两个事件的 `x-event` 无该字段（grep 无命中）。依据：E1、§14 第 3、8 项。

**X-3 [中] O3**　快照三表（`positions.code`、`org_units.code`、`employees.employee_code`）无本地业务唯一约束，业务身份依赖 `platform_integration.source_references`（来源系统 + 类别 + 来源 ID，ADR-0033 第 2 项），注册表也没有 `business_key`（DG-0）。门禁要求「业务上 X 在 Y 范围内唯一」写进卡或约束：现状（a）`employee_code` 在来源里并不唯一（`WORKFORCE_HISTORY_RULES.md` 取证：5 个 CODE 出现在多行，其中 3 个同时在在职与离职记录）；（b）`IssueKind` 有 `DUPLICATE_UNIT`、`DUPLICATE_EMPLOYEE`、`DUPLICATE_WECOM_USER_ID`，说明来源重复是常态，同步里已有隔离逻辑；（c）`wecom_user_id` 在职内唯一已落（M:L79-80）。建议：先把「身份来自来源对照」写进注册表；工号、部门编码、岗位编码是否在某范围内唯一（例如在职员工内工号唯一）由数据管理员定，定了再建部分唯一索引；不定则写明「只作展示用，不作唯一键」。见「待用户决定」6。依据：O3、NAMING_STANDARD §2.4a-1。

**X-4 [中] C4**　分级登记不全，且未按 ADR-0043 写第二维。（a）已登记 6 列（`data-classification.txt:6-7、19-22`）仍是三段格式，第二维无处写；`employee_code`、`login_name` 的级别 U-2 暂不定。（b）未登记但属个人信息的非代理键列：`employees.employment_status`、`is_fee_earner`、`separated_at`（人事属性，可识别到人）。（c）`sync_run_held_separations` 全表是「待离职名单」，列都是代理键所以按 ADR-0043 第 2 项不登记，但整表含义敏感，需要表级决定。建议：本批给出各列拟定值（PI / INTERNAL 起，`is_fee_earner`、`separated_at` 建议 SENSITIVE 备选），交用户定；理由以「用途：」开头。依据：C4、ADR-0043 第 1–3、6 项（R-1）、NAMING_STANDARD §2.5。

**X-5 [中] C3**　个人信息保留期缺口：`employees` 保留 `CURRENT_STATE` 且从不删除（含离职人员姓名、邮箱、登录名），无清理作业，GUIDING §6 无快照与版本表的认可记录；`assignment_versions`、`employee_legal_entities`、`legal_entity_attribution_corrections` 登记 `PERMANENT`，含代理键但仍是个人信息（ADR-0043 第 4 项第 2 条）。守护按「含 PI 列的表」判，故这些表按字母不违 R-2，实质是个人任职史永久保存。依据：D12、ADR-0043 R-2；见「待用户决定」3。

**X-6 [中] O8**　全 schema 无文本长度上限（`name`、`display_name`、`email`、`code` 系列、各类原因与 `detail`），`_code` 格式 CHECK 只有 `legal_entities.code`（M:L219）；`positions.code`、`org_units.code`、`org_unit_versions.code`、`legal_entity_versions.code`、`employee_code` 均无。依据：§2.4a-5。

**X-7 [低] O8**　枚举 CHECK 未具名（`ck_<表>_<列>`），涉及 13 处：positions.status（M:L10）、org_units.status / region_category（L31、L34）、employees.gender / employment_status（L68、L70）、sync_runs.trigger_kind / status / hold_kind_codes / released_kind_codes（L122-123、L147-148）、sync_diffs.entity_kind / change_kind（L186、L188）、legal_entities.status（L214）；三张版本表已具名（L315、L318、L385）。`ddl-naming-baseline.txt` 里只有主键、外键、唯一的改名行，没有这些 CHECK，G-D12（R-2a，首次部署前）未覆盖到。依据：NAMING_STANDARD §2.4。

**X-8 [低] C1**　可空列注释缺「为空表示」：见 P-3、OU-6，另有 `employees.email`、`wecom_user_id`、`separated_at`、`last_sync_run_id`（M:L87、L89、L92、L93），`sync_runs` 的 `finished_at`、各计数列、`held_reason`、`failure_reason`、`released_*`（M:L157-174），`sync_diffs.detail`、`org_unit_versions.head_employee_id` 等。依据：§2.4a-3。

**X-9 [中] D2 / O7**　同 schema 引用无外键：`org_units.last_sync_run_id`、`employees.last_sync_run_id`（登记为 `fk_exceptions`：运行 2 年后被删，快照行仍在，REG L56-72，指针之后悬空）；`sync_runs.released_by_run_id`（M:L142）无外键也未登记为 `references` 或例外，清理作业会先删较早的被放行运行，被引用的放行运行可能在同一轮被删。建议：外键 + `ON DELETE SET NULL`（须作为新增的删除语义登记），或写明悬空指针的容忍理由。依据：D2、§2.4a-4。

**X-10 [低] O12**　`CREATE EXTENSION IF NOT EXISTS btree_gist`（M:L233）写在业务 BC 基线里，`platform_integration` 基线的做法是「建库脚本预建，迁移只检查存在」（`platform_integration/V20261014133000…sql:125-129`）；建库脚本 `one_db_roles_schemas.sql:27-29` 也已预建，且承认迁移角色可能无权建扩展。建议：改成同样的检查式写法。依据：ADR-0038、D2（只建本 schema 的对象）。

**X-11 [低] 流程**　`DATA_SCORECARD.md` 只有第 6-12 行（旧 7 张表），新增的 8 张表没有打分行，旧行中的 ✗（K6 外键、K10 分级、K7 清理作业）已部分关闭，需重打；门禁 §1.4 第 4 项要求落表。

**X-12 [低] B8 / CLAUDE.md 第 1 条**　`sync_runs.employed_without_wecom_count`（M:L137）把外部产品名带进领域表列名；`wecom_user_id` 在 DA-3 删除，该统计列不在 DA-3 范围内。建议：改为不含供应商名的指标名（如「无外部身份绑定的在职人数」，名称走 `terms.yaml` 登记）。待核：`terms.yaml` 里有无对应术语。

**X-13 [低] E1 / B6　待核**　`OrgSnapshotChanged` 只带 `changedEmployees`、`separatedEmployees`、`changedOrgUnits`（`docs/architecture/events/workforce.yaml`），不区分复职（`ChangeKind.REHIRED`）与撤销 / 复活（`REVOKED`、`REVIVED`），也无岗位变更；消费方 `platform.identity`、`platform.audit` 是否需要区分，我没有核实（RULE-364 复职需恢复账号）。

## 待用户决定

1. **employees、positions 的 D17 选择**（E-1、P-1）：维度型版本表（OBSERVED），还是「不需要」并选四类理由之一（唯一贴近的是「个人信息最小化只留当前值」）。两者对应的保留期、分级、规则卡「每次离职各记一条」完全不同。
2. **`employees.separated_at` 去留与口径**（E-2）：规则卡候选稿写「无消费方前不建列」「首轮已离职为未知」；现列复职时清空、首轮取部署时刻。留则改名 `separation_observed_at` 并按规则卡改写入逻辑；不留则删列与 `separated_at` 写入。
3. **保留期**（X-5、HS-2、SR-4）：离职人员的姓名、邮箱等在 `employees` 里留多久、是否匿名化；`assignment_versions` 等 `PERMANENT` 的个人任职史是否认可永久；`sync_run_held_separations` 是 1 年（注册表）还是随运行 2 年，并补 GUIDING §6 一行。
4. **分级**（X-4）：`employment_status`、`is_fee_earner`、`separated_at` 的访问级别与个人信息标记；「待离职名单」整表的级别；U-2（工号、登录名）未决。
5. **法人状态**（LE-1）：信息图 OBJ-54「筹建 / 存续 / 注销」与现有 ACTIVE / INACTIVE（可反向）的对应，「注销」是否不可逆、是否现在落库。
6. **唯一性口径**（X-3）：工号、部门编码、岗位编码在什么范围内唯一（例如在职内），或明确只作展示。
7. **名称唯一**（LE-3）：法人名称是否要求全局唯一。
8. **例外撤销**（OUV-1、ASV-1）：注册表 `fk_exceptions` 的 5 条版本表外键例外，建议撤销（由 one-coordinator 按 §5 接受或撤销）。
9. **放行原因入表**（SR-4）：是否在 `sync_runs` 增 `release_reason`，还是以审计为唯一记录。

## 需 opus 复核的高风险项

1. **SR-1 / SR-2（sync_runs 成对 CHECK）**　建议的 CHECK 取值必须逐条对照状态机转换与全部调用点（`hold()`、`release()`、`fail()`、`BeginSyncRun` 的遗弃关闭、HELD→HELD 刷新、RELEASED 保留 `finished_at` 与 `hold_kind_codes`）；写错会让正常同步在提交时失败，而失败发生在每 5 分钟一次的生产作业里。
2. **E-1 / E-2 / P-1（D17 版本化与 `separated_at`）**　决定采用「版本表」还是「个人信息最小化」会改变员工快照的写入路径、规则卡与保留期，且 `separated_at` 的现行写入与候选规则冲突；需要结构判断与法务口径一起看。
3. **HS-1 + HS-2 + HS-3（held_separations 改键连带）**　改 UUIDv7 主键会同时影响 `REPLACE_SET` 写法（delete + insert）、外键索引、清理作业与分级；ADR-0033 已定方向，但 `SyncRunRows` 的聚合读取与清理的联动需复核。
4. **LE-2 / LV-1（法人根表与版本表的关系、代码跨时间唯一）**　`code` 可「发号前更正」（`LegalEntityCodeCorrected`）与历史单据按日期反查代码的组合，决定是否需要排他约束与根表是否保留可版本化列；涉及编号模块的读取语义。
5. **X-3（快照业务键落在 `platform_integration.source_references`）**　业务身份在另一个 schema，本地无唯一约束，来源重复是常态；是否影响同步幂等与并发建档（`RegisterEmployeeOnLogin` 与同步的竞态）需要结构复核。

## 未核实

- 本工作区 `DATA_DESIGN_GATE.md` 没有 B3a、O15、附录 C（`grep` 无命中，`origin/main` 同），任务说明中的这些新要求我按说明文字理解（B3a = 状态三方一致），O15 与附录 C 的已知缺陷类型未能对照，可能漏项。
- B8（用语）只抽查了 `terms.yaml` 与 `backend/glossary/proposed/organization_workforce.yaml`：`is_fee_earner`（`fee_earner`）、`regional_receivables_manager`、`separation_observed_at` 有词条；`region_kind`、`revoked_at`、`hold_kind_codes`、`trigger_kind`、`event_count`、`revision_number`（同 bc 的中英文重名）未逐一核。
- `LegalEntityHistoryIntegrationTest` 是否覆盖「根表与最新版本一致」（LE-2）、`OrgHistoryAppendOnlyIntegrationTest` 的覆盖范围，未读全。
- `org_unit_legal_entities`、`employee_legal_entities` 的初始数据来源（ADR-0026 D-1 的初始映射是手工录入还是迁入）与是否需要 `migrated_from`（C5）。
- SR-5：定时同步与 `MANUAL` 触发能否并发产生两条 RUNNING，无测试证据。
- X-13：`platform.identity` 对复职、撤销的消费需求。
- 组织单元「有在职任职的单元不得撤销」（信息图 OBJ-55 的拒类规则）与岗位「被在职任职引用的岗位不得停用」（OBJ-58）：来源驱动的快照无法拒绝，也没有 `IssueKind` 对应，6e 自建 HRM 前不要求落库；本审查不判，记于此。
- 规则卡 RULE-354 至 RULE-374 只读了 354-358、363-367、372 及 `WORKFORCE_HISTORY_RULES.md`；RULE-359 至 362、368 至 371、373 未读（多为老系统手工维护或用户密码，与快照无关）。
- 未运行任何测试、未连库，所有索引与执行计划判断来自 DDL 与查询文本。
