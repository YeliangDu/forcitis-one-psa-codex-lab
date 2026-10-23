---
卡号: DATA-REPAIR-workforce
状态: 进行中
负责会话: one-1a-par
前置: R-1b-P5
---
# 派工卡 DATA-REPAIR-workforce：organization_workforce 已建表的数据设计缺陷修复（草稿）

- 草稿（第二版）：one-data 起草助手，2026-10-16；第二版并入用户 2026-10-16 的决定、统一分级（阻断 0）与 `SCAN_SUPPLEMENT.md` 补入；待 one-data 终审、one-coordinator 派工。
- 证据来源：`docs/modernization/data/review-parts/organization_workforce.md`（下称 OW）与 `OPUS_VERIFY.md`（下称 OV）；以 OV 为准。「M:L」指 `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql` 行号（取自 OW，按审查时 main 基线；R-1b P5 会改 `sync_run_held_separations` 等，实施前重核）。OW 按旧版门禁判定（无 B0、B3a、O15、O16、附录 C，OV 误报与修正第 8 条），O15 对照不完整，已由 `SCAN_SUPPLEMENT.md`（下称 SS，行号取 `origin/main` a27bf877，与 OW 旧行号可能差几行）补审。
- 级别以统一分级为准（`DATA_DESIGN_GATE.md` §4a、`BASELINE_TABLE_REVIEW.md` §1、§3）：阻断 0；本 schema 的 4 个高（P-1、E-1、SD-1、HS-1）均有归属、不起条目（见第 6 节）；P-2、SD-4、LE-3、LA-1、CR-2、X-6、X-9 由低升中，已起条目 WF-11 至 WF-14。
- 「待核」表示分报告与复核报告都没有给出证据，实施者先核实再改。

## 1. 卡号与类别
- 卡号：DATA-REPAIR-workforce
- 类别：缺陷修复（SESSION_PLAYBOOK §6.1 第 9 条）
- 批次：已建表数据设计审查修复批（门禁 v2 §1.2 第 6 条）；一个小 PR 直接合入 main
- 建议负责会话：one-1a-next-4
- 排期：本卡排在 dev 部署之后（用户「同意，不挡 dev」，`BASELINE_TABLE_REVIEW.md` §3 末段）；本卡不是闸门，W2 版本化才是生产前闸门（one-ops 登记到 PREDEPLOY，C-15 一组，见 README）
- 与 ADR-0039 的关系：`sync_runs`、`sync_diffs` 将由「拆分 a」迁到 `platform_integration`（生产前，P5 未覆盖，见第 6 节）；WF-01、WF-02、WF-07、WF-08、WF-13、WF-14 的改动随表搬迁，开工时若拆分 a 已合并则落在新位置（待核）
- 并行与顺序：基线文件 `organization_workforce` 同时被 R-1b P5（`sync_run_held_separations` 主键）与 W2 主数据版本化（DA-7）改动；本卡须在 P5 合并后开工，且不触碰 W2 重写的表形状

## 2. 依据
- 门禁 v2：B3、O3、O4、O8、O10、C3、C4
- SESSION_PLAYBOOK §14.1 D2、D12、D17；§12.2；ADR-0038；ADR-0033 第 1 项第 2 条（`sync_run_held_separations` 点名）、第 2 项；ADR-0043 R-2
- 规则卡：`WORKFORCE_HISTORY_RULES.md`（候选）、RULE-372（直属领导不能是自己）
- 用户决定（第 6 节引用）：「都做维度型版本表（以同步观察日为生效依据）」（收件箱 2026-10-16-one-data-employee-position-versioning；intent `2026-10-16-085331-one-data-员工与岗位怎样版本化-都做维度型.md`）
- 复核编号：SS §1（wf 22 列）、§2、§3、§4（第 1 至 4 组）、补审 B7-2 与 O15 缺项 1、3、4、5；OV「SR-1 降级为中，四条 CHECK 逐条核对可直接落」；「高风险项判断 OW 第 1、3、4、5 条」；「HS-2 仍开放」；「LV-1(a) 应加」；误报与修正第 5 条（`finished_at >= started_at` 不加）
- 分报告编号：OW SR-1、SR-2、SR-3、HS-2、LV-1、OU-1、OU-3、A-1、A-2、SD-2、OUV-1、ASV-1、LE-2、X-3；统一分级后新增 P-2、SD-4、LE-3、LA-1、CR-2、X-6、X-9

## 3. 范围与卡上文件

允许改动（越界先停下报告，不先做后报）：
- `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql`（只限 `sync_runs`、`sync_diffs`、`org_units`、`assignments`、`assignment_versions`、`org_unit_versions`、`legal_entity_versions`、`positions`、`employees`、`legal_entities`、`org_unit_legal_entities`、`employee_legal_entities`、`legal_entity_attribution_corrections` 的约束、索引；PR-8 经 one-data 裁 8 补入后五张，只加约束，不改列）
- `backend/architecture/data-access-registry.yaml`（只限 workforce 各表条目：`sync_run_held_separations` 保留期、`legal_entities` 投影登记、快照三表 `business_key` 说明、版本表 `fk_exceptions` 与逐列不进版本的理由）
- Java：`backend/src/main/java/com/forcitis/one/organization/workforce/` 下 `infrastructure/persistence/JdbcSyncRunQueries.java`（WF-08 若需调整查询）、`domain/Assignment.java`（WF-06 若 `position_id` 改 NOT NULL）；其余只读
- 测试：`SyncRunStateMachineTest`（只读，用来核对 CHECK 取值）、PR-8 的 `RefreshShortNames` 单元/集成测试与「根表与开放版本一致」补测、`organization/workforce` 下新增的 CHECK 集成测试（类名由测试代理定）

归属：本卡独占上述 workforce 约束与索引片段；`positions`、`employees` 的版本化（P-1、E-1）、`separated_at`（E-2）归 W2（DA-7），不碰这两张表的列。

不碰：`sync_run_held_separations` 的主键与外键索引（已被 P5 覆盖）；`employees` 与 `positions` 表形状；`events/workforce.yaml`；验收测试目录；他卡文件。

## 4. 条目清单

### WF-01　SR-1　中（OV 降级，原高）　`sync_runs` 状态与原因 / 时间字段缺成对 CHECK（四条）
- 问题：门禁 B3 要求原因必填的终态有成对 CHECK，`sync_runs` 除状态枚举外无跨列约束。
- 证据：OW M:L120-149；`SyncRun.java:37-40`（状态机）、`:171-233`；`ApplySourceSnapshot.java:88-99、121-130`；`FailSyncRun`；`SyncOrgSnapshot.java:51`；`BeginSyncRun.java:33`；OV 逐条核对：四条都安全，不会误拒。
- 改法（取值照 OV 核对结论，写错会让每 5 分钟的生产作业在提交时失败）：
  - `ck_sync_runs_failure_reason`：`status <> 'FAILED' OR failure_reason IS NOT NULL`（两条入口都给非空串）
  - `ck_sync_runs_held_reason`：`status <> 'HELD' OR (held_reason IS NOT NULL AND cardinality(hold_kind_codes) > 0)`；原因由 `joining("; ")` 产生，最坏是空串而非 NULL，**本条不加「非空串」**，若要加须先让 `verdict.reason()` 保证非空
  - `ck_sync_runs_released`：`status <> 'RELEASED' OR (released_at IS NOT NULL AND released_by_run_id IS NOT NULL)`；`released_by` 可空，不入约束
  - `ck_sync_runs_finished_at`：`(status = 'RUNNING') = (finished_at IS NULL)`；除 `release()` 外每个转换都写 `finishedAt`，RELEASED 保留 HELD 时的值
- 预期表结构 diff：`organization_workforce.sync_runs`：新增上述四条 CHECK。
- 验收要点：状态机每条转换（RUNNING→SUCCEEDED / HELD / FAILED，HELD→HELD / RELEASED，遗弃运行关闭）走一遍，提交均成功；直接插入违反行被拒；`SyncRunStateMachineTest` 原样重跑。
- 依据：门禁 B3、B5；D2；OV「高风险项判断 OW 第 1 条」；SS §4 第 1 组（`released_by:140` / `released_at:141` / `released_by_run_id:142` 同空同非空，`held_reason:138`、`failure_reason:139`、`finished_at:125`；该表 :120-149 无跨列 CHECK）。

### WF-02　SR-2（子集）　中　`sync_runs` 其余可落约束
- 改法（只落 OV 核对为安全的三项）：
  - (a) `ck_sync_runs_released_kind_codes`：`released_kind_codes <@ hold_kind_codes`（`hold()` 换类型时清空，`releaseKind` 先校验；`SyncRun.java:190-197`）
  - (c) 计数列 `>= 0`（M:L126-137）
  - (e) `held_reason`、`failure_reason` 长度上限 2000（代码按 2000 截断，`JdbcSyncRunRepository.java:93、141`）
  - (b) 统计列「全空或全有」：**待核** `RunStats` 各字段是否都非空（OV 未核实）；核实前不落
  - (d) **不加** `finished_at >= started_at`：HELD→HELD 刷新由另一实例给早先的运行写 `finished_at`，实例间时钟偏差会让生产作业提交失败（OV 误报与修正第 5 条）
- 预期表结构 diff：`sync_runs`：新增 (a) (c) (e) 对应具名 CHECK（名称按 `ck_sync_runs_<列>`）。
- 验收要点：HELD→HELD 刷新、放行、成功三条路径通过；长度 2001 被拒。
- 依据：D2；NAMING_STANDARD §2.4a-5；OV；SS §1（`held_reason:138`、`failure_reason:139`）、§3（统计列、放行列无空值说明，18 列）。

### WF-03　HS-2　中（OV 确认仍开放）　`sync_run_held_separations` 注册表保留期与实际不符
- 问题：注册表写 `1y`，实际随 `sync_runs` 经 `ON DELETE CASCADE` 保留 2 年；GUIDING_PRINCIPLES §6 无本表认可记录。
- 证据：OW 注册表 :112-118（P5 注册表 :116 仍写 `1y`）；`SyncPurgeJob.java:69、74` 只删 `sync_diffs` 与 `sync_runs`；M:L450；OV「高风险项判断 OW 第 3 条」。
- 改法（OV 建议，比给作业加单独删除简单）：注册表 `retention` 改写为「随 `sync_runs` 删除（2 年）」，指向作业 `organization.workforce.sync.purge`；GUIDING_PRINCIPLES §6 补一行待用户认可后写入（见待决 1）。
- 预期表结构 diff：无。
- 验收要点：注册表保留期与 `ON DELETE CASCADE` 行为一致，守护通过。
- 依据：门禁 C3；D12。

### WF-04　LV-1(a)　中（OV 确认）　法人版本表 `code` 缺格式 CHECK
- 问题：主表 `legal_entities.code` 有 `^[A-Z]{2,10}$`，`legal_entity_versions.code` 无，版本表可被写入非法代码。
- 证据：OW M:L383、M:L219；Java `LegalEntityCode` 同一正则；OV「LV-1(a) 应加」。
- 改法：新增 `ck_legal_entity_versions_code_format`：`code ~ '^[A-Z]{2,10}$'`。
- 预期表结构 diff：`legal_entity_versions`：新增 `ck_legal_entity_versions_code_format`。
- 验收要点：主表与版本表对同一非法代码一致拒绝；`LegalEntityHistoryIntegrationTest` 原样重跑。
- 依据：门禁 O8、O3；NAMING_STANDARD §2.4a-5；D6。

### WF-05　OU-1　中　`org_units` 状态与 `revoked_at` 缺成对约束
- 证据：OW M:L31、L37；`JdbcOrgSnapshotRepository.upsertOrgUnit` 写 `revoked_at = case when status = 'REVOKED' then … end`（恒同时有值或同时为空）。OV 未单独抽查，实施前对照 `upsertOrgUnit` 与 REVIVED 路径核对。
- 改法：新增 `ck_org_units_revoked_at`：`(status = 'REVOKED') = (revoked_at IS NOT NULL)`。
- 预期表结构 diff：`org_units`：新增 `ck_org_units_revoked_at`。
- 验收要点：撤销、复活（`ChangeKind.REVIVED`）两条同步路径通过；直接插入 `REVOKED` 无时间的行被拒。
- 依据：门禁 B3；D2；SS §4 第 3 组（`revoked_at:37` ⇔ `status:31 = 'REVOKED'`）。

### WF-06　A-1 + A-2　中　任职表的 `position_id` 可空与「本人不是自己的直属领导」
- 问题：A-1 `position_id` 注释「正常数据不为空」却可空，代码在写入前隔离缺岗位的人；A-2 直属领导不得是本人只在注释与代码里。
- 证据：OW M:L102、L113、M:L352（`assignment_versions`）；`EmployeeChecks.java:32`（`ISOLATED_MISSING_POSITION`）、`LoginMissRegistration.java:46-55`、`domain/Assignment.java`；A-2：M:L103、L114、M:L353、`IssueKind.SUPERVISOR_IS_SELF`、RULE-372。
- 改法：A-2：两表加 `ck_assignments_reports_to_not_self`、`ck_assignment_versions_reports_to_not_self`（`reports_to_employee_id IS DISTINCT FROM employee_id`）；A-1：两表 `position_id` 改 `NOT NULL` 并去掉 `Assignment` 的可空，**待核**是否存在「无岗位在职」场景（OW 要求有则写规则卡出处）；若有则本条取消，只改列注释。
- 预期表结构 diff：`assignments`、`assignment_versions`：新增各一条 CHECK；`position_id` 可空性（条件见上）。
- 验收要点：隔离先于写入——缺岗位、直属领导为本人的来源行不进入表；对照 `SnapshotReconciler` 核实隔离确实发生在写入之前（待核）。
- 依据：门禁 B4、O4；D2。

### WF-07　SD-2　中　`sync_diffs.issue_kind` 无 CHECK，`change_kind` 与 `issue_kind` 无成对
- 证据：OW M:L189（`issue_kind` 无 CHECK，`IssueKind` 16 个取值）、`ddl-naming-baseline.txt:8` 仍登记待补；`SyncDiff.issue()` / `change()` 工厂保证成对。
- 改法：新增 `ck_sync_diffs_issue_kind`（16 值，取值与 `IssueKind` 逐值一致）、`ck_sync_diffs_issue_pair`：`(change_kind = 'ISSUE') = (issue_kind IS NOT NULL)`；删 `ddl-naming-baseline.txt:8` 对应行。`changed_field_codes` 取值约束随 DA-3 不含。SS §4 第 4 组另有 `changed_field_codes:193` ⇔ UPDATED（注释 :208「仅 UPDATE 类变更有值」）：待核 `SyncDiff.change()` 工厂是否对 UPDATED 必给值，核实后加 `(change_kind = 'UPDATED') = (changed_field_codes IS NOT NULL)`，核实前不落。
- 预期表结构 diff：`sync_diffs`：新增上述两条 CHECK。
- 验收要点：三方一致测试：CHECK 取值 = `IssueKind` 枚举 = 事件目录（G-D12 同款比对）。
- 依据：门禁 B3、B3a、O8；SS §4 第 4 组、§1（`issue_kind:189`）。

### WF-08　SR-3　中　登录路径热点查询无合适索引
- 问题：`SnapshotFreshness.isCurrent()` 在登录判定时执行 `select max(finished_at) … where status = 'SUCCEEDED'`，`sync_runs_status_idx` 只含 `status`，选择性为零。
- 证据：OW `JdbcSyncRunQueries.java:59`、`OrgDirectoryService.java:53`、M:L150-151；约 21 万行 / 2 年。OV 未单独抽查；必须 EXPLAIN 证据放 PR。
- 改法：以 `(finished_at DESC) WHERE status = 'SUCCEEDED'` 与 `(started_at, id) WHERE status IN ('RUNNING','HELD')` 两个部分索引取代 `sync_runs_status_idx`；`sync_runs_started_at_idx` 改 `(started_at DESC, id DESC)`。索引名按 §2.4。
- 预期表结构 diff：`sync_runs`：删 `sync_runs_status_idx`；新增两个部分索引；`sync_runs_started_at_idx` 列改写。
- 验收要点：EXPLAIN 显示 `max(finished_at)` 走新部分索引；分页游标有 `id` 兜底。
- 依据：门禁 O10；D19；§2.4a-2。

### WF-09　OUV-1 + ASV-1　中　版本表引用无外键（需 one-coordinator 先接受撤销例外）
- 问题：`org_unit_versions` 的 `parent_org_unit_id`、`head_employee_id`、`regional_receivables_manager_id`，`assignment_versions` 的 `position_id`、`reports_to_employee_id` 无外键；注册表 `fk_exceptions` 理由「版本不自带键」，但全仓对快照三表无 DELETE。
- 证据：OW M:L314-317、M:L352-353、注册表 :82-84、:92-94；`JdbcSyncRunRepository.java:107、116`、`SyncPurgeJob.java:69、74`（只删 runs 相关表）；`assignment_versions` 的 `employee_id`、`org_unit_id` 已有外键（M:L359-360）。
- 改法：撤销五条例外，加外键（`DEFERRABLE INITIALLY DEFERRED`，与快照表一致）。**前置**：撤销例外由 one-coordinator 按 §5 接受（OW 待决 8），未接受则本条不做。
- 预期表结构 diff：`org_unit_versions`：新增三个外键；`assignment_versions`：新增两个外键（名称 `fk_<表>_<列>`）。
- 验收要点：同步事务内先写版本后写快照行不失败（延迟外键）；引用不存在 ID 被拒；全量同步用例通过。
- 依据：门禁 B4、O10；D2；NAMING_STANDARD §2.4a-4。

### WF-10　登记类（OU-3(a)、LE-2、X-3）　中　注册表登记缺口
- 改法（只改注册表与列注释，无 DDL）：
  - OU-3(a)：`sort_order`、`short_name` 不进 `org_unit_versions`，逐列写理由；`short_name` 单列登记为「跨来源列（组织源 + 老系统只读刷新）」，DA-8 落地前有效（ADR-0033 第 2 项第 6 条）
  - LE-2：`legal_entities` 登记「当前值投影 + 一致性测试」，不改根表（OV「高风险项判断 OW 第 4 条」）；是否已有「根表与最新版本一致」的测试：**待核**，没有则补
  - X-3：快照三表无本地业务唯一，注册表写明「身份来自 `platform_integration.source_references` 的 `(source_system, source_object_kind, source_id)` 唯一」，并写明 `employee_code`、部门编码、岗位编码只作展示、不作唯一键（来源工号不唯一，OW 取证）；是否在某范围内唯一待用户决定（待决 4）
- 证据：OW 注册表 REG L56-64、`JdbcLegalEntityRepository.insert/save`、`WORKFORCE_HISTORY_RULES.md` 取证；OV X-3：本地不建工号 / 编码唯一是对的，竞态由 `source_references` 唯一与 ADR-0033 测试 5 承担。
- 预期表结构 diff：无。
- 验收要点：注册表条目齐全，守护通过。
- 依据：门禁 O3、O6、C2；D17 细则 3、4、5。

### WF-11　P-2 + SD-4（detail）+ LE-3（name）+ CR-2（reason）+ X-6　中（统一分级：低→中）　文本列无长度上限、编码列无格式 CHECK
- 问题：全 schema 文本列无 `char_length`，`positions.code`、`org_units.code`、`org_unit_versions.code` 无格式 CHECK（只 `legal_entities.code` 有）。
- 证据：OW P-2（M:L7-8）、X-6、SD-4（`detail` 代码截 2000，`JdbcSyncRunRepository.java:137`）、LE-3、CR-2（`correction_reason` 只有 `btrim <> ''`，M:L422）；SS §1 wf 22 列：positions（`code:7`、`name:8`）；org_units（`code:27`、`name:28`、`short_name:36`）；employees（`employee_code:64`、`display_name:65`、`email:67`）；sync_diffs（`detail:190`、`changed_field_codes:193`）；legal_entities（`name:213`）；org_unit_versions（`code:312`、`name:313`）；legal_entity_versions（`name:384`）；corrections（`correction_reason:415`）；全库 `char_length` 出现次数 0（SS §1）。
- 改法：对上列加具名长度 CHECK（`ck_<表>_<列>`）与禁空串，上限逐列待核（取来源字段上限或代码截断值，须有测试证据，不静默截断）；编码列格式待核（来源编码取证显示工号、部门编码不唯一，格式只收「可见字符、长度」，不借机收窄，OW X-3）。**不含**：`login_name`、`wecom_user_id`（随 DA-3 删）、`subject_key`（拆分 a）、`issue_kind`（WF-07）、`held_reason`、`failure_reason`（WF-02(e)）、`legal_entity_versions.code`（WF-04）。岗位 `DELETED` 与组织单元 `REVOKED` 用语不一致（OW P-2 后半）属词表问题，不自行裁定，由 `new_term_issue.py` 登记给 one-glossary。
- 预期表结构 diff：上列各表新增 `ck_<表>_<列>` 长度（及编码格式）CHECK。
- 验收要点：种子与全量同步用例通过；超长与空串被拒；`employees.email` 等已登记 PI 的列不因加 CHECK 改变分级。
- 依据：门禁 O8；NAMING_STANDARD §2.4a-5；SS §1。

### WF-12　LA-1 + CR-2（空更正）　中（统一分级：低→中）　期间表、更正表的子行不指向聚合根；空更正无约束
- 证据：OW LA-1（期间表 M:L259-297 与更正表 M:L407-426 不指向聚合根 `legal_entity_attributions`；根的 `org_unit_id`、`employee_id` 各有唯一约束 M:L246-247，可作外键目标）、CR-2（`new_effective_to_date <> previous_effective_to_date` 无 CHECK，M:L422 附近）。
- 改法：期间表对根加外键（`org_unit_id` / `employee_id` 引用根的同名唯一列，外键目标是唯一约束，与 DATA-REPAIR-approval AP-04 同问题：先核 `DdlNamingTest` 是否放行 NAMING_STANDARD §2.4a-1 外键目标为主键，不放行则写明不建的理由）；更正表加 `ck_legal_entity_attribution_corrections_changes`：`new_effective_to_date <> previous_effective_to_date`（两列可空时的写法待核，`IS DISTINCT FROM`）。更正表两个索引对另一类主体的行为空列，可加部分索引（OW CR-2，非必须）。
- 预期表结构 diff：期间表（`org_unit_legal_entities`、`employee_legal_entities`）与更正表：新增外键（视待核结论）；更正表新增一条 CHECK。
- 验收要点：先写根后写子行通过；子行无根被拒；空更正被拒。
- 依据：门禁 D2、D3、O4；SS §4 第 15 组对照（`org_unit_legal_entities` 等的区间 CHECK 已含，不动）。

### WF-13　X-9　中（统一分级：低→中）　`sync_runs.released_by_run_id` 无外键；`last_sync_run_id` 指针悬空
- 证据：OW X-9：`org_units.last_sync_run_id`、`employees.last_sync_run_id` 在注册表登记为 `fk_exceptions`（运行 2 年后被删，快照行仍在，REG L56-72）；`sync_runs.released_by_run_id`（M:L142）无外键也未登记，清理作业会先删较早的被放行运行，被引用的放行运行可能在同一轮被删。SS §3：`released_by_run_id:142` 无空值说明；SS 补审 O15 缺项 3：`last_sync_run_id` 随拆分 a 改为引用变更集。
- 改法：`released_by_run_id` 加外键 `ON DELETE SET NULL` 并在注册表登记删除语义，或写明悬空指针的容忍理由（二选一，待 one-data 判）。两个 `last_sync_run_id` **不动**：随 ADR-0039 拆分 a 改引用变更集，本卡不在旧位置补外键。
- 预期表结构 diff：`sync_runs`：新增 `fk_sync_runs_released_by_run_id`（若选外键）。
- 验收要点：放行、清理作业（`SyncPurgeJob`）两条路径通过；被引用的放行运行被清理时 `released_by_run_id` 置空且不报错。
- 依据：门禁 D2、O7；NAMING_STANDARD §2.4a-4。

### WF-14　SD-4　中（统一分级：低→中）　`sync_diffs` 登记 `APPEND_ONLY`，库内未禁 UPDATE
- 证据：OW SD-4（REG L105-111 登记 `APPEND_ONLY`；版本表有触发器 M:L511-530）；清理作业需要 DELETE（`SyncPurgeJob.java:69、74`），故只禁 UPDATE；`detail` 长度已在 WF-11。
- 改法：给 `sync_diffs` 加禁止 UPDATE 的触发器（命名与同 schema 版本表触发器一致）。守护对 `AGGREGATE_CHILD + APPEND_ONLY` 漏检归 one-quality（OV 建议新增规则 3），不在本卡。
- 预期表结构 diff：`sync_diffs`：新增触发器及函数（或复用现有函数，待核）。
- 验收要点：UPDATE 被拒、DELETE 通过；全仓对 `sync_diffs` 无 UPDATE 语句（实施前 grep 核实，待核）。
- 依据：门禁 O7；D8。

低级顺带（不阻塞）：P-3、OU-4、OU-6、E-3、E-4、SD-3、CR-1（SS 补审缺项 10：依据不成立，级别维持低，须 one-data 改写依据）、X-10、X-12。证据见 OW 对应条目。

- **数据设计（PR-8：WF-04、05、09–12，one-1a-next-5 补，待 one-data 签）**（基线：`origin/main` d150fc33；下文行号取该基线；「基线」＝`organization_workforce/V20261014131000__organization_workforce_baseline.sql`；Java 路径省略前缀 `backend/src/main/java/com/forcitis/one/organization/workforce/`，测试路径省略前缀 `backend/src/test/java/com/forcitis/one/`；本节只做文档核实，未在库内执行 DDL，未跑测试）
  - **范围与顺序**：本 PR 只含 WF-04、WF-05、WF-09、WF-10、WF-11、WF-12，加 B7-2 的注册表说明。不含：WF-01、02、03、07、08、13、14（并入拆分 a）、WF-06（并入 DA-7）、所有 `sync_*` 表（因此 WF-11 里 `sync_diffs.detail`、`sync_runs` 两个原因列不在本 PR，随拆分 a 带走）。无新增列、无新术语，只新增具名约束、外键、索引；按 ADR-0038 直接改基线，已部署库重建。一个提交即可（无「先改 Java 再加 CHECK」的次序依赖；WF-11 的 `RefreshShortNames` 规范化与 `short_name` CHECK 同提交）。
  - **用户待定项，本 PR 不落**：工号、部门编码、岗位编码在什么范围内唯一（本卡第 5 节待决 4），故不建任何编码唯一约束，WF-10 只登记「只作展示」的事实；法人名称是否全局唯一（待决 7），故 `legal_entities.name` 只加长度与非空白，不加唯一；法人状态与 OBJ-54 的对应（待决 3）不动 `status` 取值；保留期与匿名化（待决 1）不在本 PR。
  - **① WF-04　法人版本表 `code` 格式**
    - 核实：Java 单一入口 `api/LegalEntityCode.java:10-18`（`[A-Z]{2,10}` 整串匹配）；版本表写入的 `code` 取自 `entity.code().value()`（`infrastructure/persistence/LegalEntityHistory.java:24-46`），无别的写入路径。主表约束在基线 :219，版本表 :383 无。PG 的 `$` 只匹配串尾，与 Java `matches()` 等价；`[A-Z]` 的字符范围依赖测试库排序规则为 C（G-DDL12，与生产一致），PR 里贴 `show lc_collate` 输出。
    - DDL（`legal_entity_versions` 表内，:391 之后）：`CONSTRAINT ck_legal_entity_versions_code_format CHECK (code ~ '^[A-Z]{2,10}$'),`
    - Java：无。测试：新增 `organization/workforce/application/WorkforceRowConstraintsIntegrationTest`（继承 `WorkforceIntegrationSupport`，`@DisplayName` 前缀 `DATA-REPAIR-workforce WF-nn`，不进 `acceptance/`）：先建法人 `FST`，直插版本行 `code` 为 `fst`、`F`、`FSTFSTFSTFS`、`FS1` 均被拒（23514，约束名断言），`FST` 通过；与 `legal/LegalEntityIntegrationTest.java:136-141` 对称。
    - 夹具：无会被拒的夹具；`LegalEntityHistoryIntegrationTest` 原样重跑。
  - **② WF-05　`org_units` 撤销时间成对**
    - 核实（对卡中「实施前对照 `upsertOrgUnit` 与 REVIVED 路径」）：`infrastructure/persistence/JdbcOrgSnapshotRepository.java:113-136`：插入时 `revoked_at = case when :status = 'REVOKED' then :now end`（:119），冲突更新时 `case when excluded.status = 'REVOKED' then coalesce(org_units.revoked_at, excluded.updated_at) end`（:125），两个分支都是「REVOKED ⇔ 有值」。复活：`domain/SnapshotReconciler.java:277` 把状态从 REVOKED 改回 ACTIVE / DISABLED，走同一语句，`revoked_at` 被置空，相容。撤销：`:282-283` 经 `withStatus(REVOKED)` 走同一语句。`updateShortNames`（:185-197）与 `saveRegisteredEmployee` 不碰 `status`。生产代码里写 `org_units.status` 的 SQL 只此一处。
    - DDL（`org_units` 表内）：`CONSTRAINT ck_org_units_revoked_at CHECK ((status = 'REVOKED') = (revoked_at IS NOT NULL)),`；列注释 `revoked_at` 补「为空表示组织单元未被撤销」。
    - Java：无。测试：`WorkforceRowConstraintsIntegrationTest`：直插 `REVOKED` 无时间被拒、`ACTIVE` 带时间被拒；既有撤销、复活用例回归（`application/OrgDirectoryIntegrationTest.java:265-275` 同步撤销，`domain/OrgUnitSyncRulesTest.java:65-76、182` 域内）。
    - 夹具：`scenario/WorkforceHttpIntegrationTest.java:128` 置 `REVOKED` 同时写 `revoked_at = now()`，合法，不改。`acceptance/` 与其他夹具均以 `ACTIVE` 直插，不受影响。
  - **③ WF-09　版本表五条引用补外键（例外已接受，撤销注册表五条 `fk_exceptions`）**
    - 核实：快照三表在生产代码中无 DELETE（`backend/src/main/java` 里 `delete from` / `truncate` 只命中 `sync_*` 相关：`infrastructure/scheduling/SyncPurgeJob.java:69、74`，`infrastructure/persistence/JdbcSyncRunRepository.java:107、116`）。版本表写入值与快照同批同值：`domain/SnapshotReconciler.java:262-264` 的 `known(...)` 把不在快照里的负责人、区域应收负责人置空并记 `HEAD_NOT_FOUND` / `REGIONAL_MANAGER_NOT_FOUND`，`domain/EmployeeChecks.java:22-36` 的 `isolation` 把岗位或部门未知的人整行隔离，所以进入 `infrastructure/persistence/OrgHistory.java:33-114` 的引用都是快照里已有的行；`org_units` 对同样三列已有延迟外键（基线 :464-470），`assignments.reports_to_employee_id` 同（:472-474），版本表外键不会比快照表更严。写入次序（`JdbcOrgSnapshotRepository.save` :69-96）：岗位 → 部门 → 部门版本 → 员工 → 任职版本，部门版本里的负责人此时员工尚未入库，故五条外键均须 `DEFERRABLE INITIALLY DEFERRED`（与快照表一致，卡 WF-09）。
    - DDL（并入基线 :463-474 的 `ALTER TABLE` 区）：
      ```sql
      ALTER TABLE organization_workforce.org_unit_versions
          ADD CONSTRAINT fk_org_unit_versions_parent_org_unit_id FOREIGN KEY (parent_org_unit_id)
              REFERENCES organization_workforce.org_units (id) DEFERRABLE INITIALLY DEFERRED,
          ADD CONSTRAINT fk_org_unit_versions_head_employee_id FOREIGN KEY (head_employee_id)
              REFERENCES organization_workforce.employees (id) DEFERRABLE INITIALLY DEFERRED,
          ADD CONSTRAINT fk_org_unit_versions_regional_receivables_manager_id FOREIGN KEY (regional_receivables_manager_id)
              REFERENCES organization_workforce.employees (id) DEFERRABLE INITIALLY DEFERRED;
      ALTER TABLE organization_workforce.assignment_versions
          ADD CONSTRAINT fk_assignment_versions_position_id FOREIGN KEY (position_id)
              REFERENCES organization_workforce.positions (id) DEFERRABLE INITIALLY DEFERRED,
          ADD CONSTRAINT fk_assignment_versions_reports_to_employee_id FOREIGN KEY (reports_to_employee_id)
              REFERENCES organization_workforce.employees (id) DEFERRABLE INITIALLY DEFERRED;
      CREATE INDEX ix_org_unit_versions_parent_org_unit_id ON organization_workforce.org_unit_versions (parent_org_unit_id);
      CREATE INDEX ix_org_unit_versions_head_employee_id ON organization_workforce.org_unit_versions (head_employee_id);
      CREATE INDEX ix_org_unit_versions_regional_receivables_manager_id ON organization_workforce.org_unit_versions (regional_receivables_manager_id);
      CREATE INDEX ix_assignment_versions_position_id ON organization_workforce.assignment_versions (position_id);
      CREATE INDEX ix_assignment_versions_reports_to_employee_id ON organization_workforce.assignment_versions (reports_to_employee_id);
      ```
      五个索引为 NAMING_STANDARD §2.4a-2「外键列须有以该列开头的索引」（G-DDL2，待实现）所需；快照行不删、键不改，运行时无收益，见裁 4。
    - 注册表（`backend/architecture/data-access-registry.yaml`）：删 `org_unit_versions` 的 `fk_exceptions`（:84-87）与 `assignment_versions` 的 `fk_exceptions`（:94-96）；`org_unit_versions` 补 `references: {regional_receivables_manager_id: organization_workforce.employees}`（与 `org_units` :63 同；该列名守护推不出目标表，不补则 D6 不检查它）。`org_units`、`employees` 的 `last_sync_run_id` 例外（:61-62、:70-71）属 WF-13，不动。
    - Java：无。测试：`WorkforceRowConstraintsIntegrationTest`：对五条外键各直插一行指向不存在 ID 的版本行被拒（23503，约束名断言）；正例走全量同步（`application/OrgDirectoryIntegrationTest.java:53、270` 的 `head(2, 1)`、`manager(2, 2)` 使部门版本先于员工入库，覆盖延迟语义）、`OrgHistoryAppendOnlyIntegrationTest`、`OrgSnapshotSyncIntegrationTest` 回归。
    - 夹具：没有直插版本行的测试（测试里对版本表只有触发器断言与 `truncate`）。清库用 `application/VersionTables.truncate(... cascade)`，新增外键在 `cascade` 范围内，不改。`acceptance/platform/identity/EmergencyLoginEmploymentIT.java:127-129` 的 `delete from employees / org_units` 与 `scenario/OrgUnitScopeIntegrationTest.java:47-49` 删的行没有版本行引用（直插、未经同步），不受影响；删除有版本行的快照行本来就被既有 `employee_id` / `org_unit_id` 外键拒绝，行为不变。
  - **④ WF-10　读卡确认（OU-3(a)、LE-2、X-3、B7-2；无 DDL，只改注册表注释与列注释）**
    - 登记形式：注册表条目键集合没有 `projection`、`business_key`、`note`（文件头语法与 `DataArchitectureTest` 只读 role、mutability、retention、lineage、references、fk_exceptions 等），既有先例是在条目上写 YAML 注释（注册表 :229-231、:260、:292，「DG-0 加字段前以注释登记」），本 PR 同此做法，见裁 6。
    - OU-3(a)：`org_unit_versions` 的列 = 快照列去掉 `sort_order`、`short_name`、`revoked_at`、`last_sync_run_id`、`version`、`created_at`、`updated_at`（基线 :307-328 对 :25-42）。按日期读版本的唯一代码 `infrastructure/persistence/JdbcLegalEntityResolutionSources.java:43-52` 只取任职版本的 `org_unit_id` 与部门版本的 `parent_org_unit_id`（法人归属向上继承），不读显示顺序与简称。注册表逐列写理由：`sort_order` 只决定同级显示顺序，无按日期取值的业务（`domain/OrgHierarchy.java:25`、`domain/SnapshotReconciler.java:295` 取当前值排序）；`short_name` 不是组织源列，由 `application/RefreshShortNames.java` 每天从老系统只读刷新，列入「跨来源列」，DA-8 落地前有效（ADR-0033 第 2 项第 6 条）；`revoked_at` 由 `status = 'REVOKED'` 的版本起点推出，不另存；`last_sync_run_id` 随拆分 a 改引用变更集。
    - LE-2：`legal_entities` 登记「当前值投影：`code`、`name`、`status` 与 `legal_entity_versions` 的开放版本同事务写（`infrastructure/persistence/JdbcLegalEntityRepository.java:59、77` 与 `LegalEntityHistory.java:24-46`），版本表为历史、根表为当前值」，不改根表（OV 判断）。待核结论：**没有**「根表与开放版本一致」的测试——`legal/LegalEntityHistoryIntegrationTest.java:40-65` 只断言版本序列与追加不可改，`LegalEntityIntegrationTest` 不比较两表。补测：`WorkforceRowConstraintsIntegrationTest` 在建、改名、停用、启用、更正代码（`LegalEntityCodeCorrected`）后，逐个法人断言 `legal_entities.(code, name, status)` 等于 `effective_to_date is null` 的版本行，且每个法人恰有一个开放版本。
    - X-3：快照三表登记「业务身份来自 `platform_integration.source_references` 的 `(source_system, source_object_kind, source_id)` 唯一；`positions.code`、`org_units.code`、`employees.employee_code` 只作展示，来源里不唯一（`WORKFORCE_HISTORY_RULES.md` 取证：5 个工号出现在多行，其中 3 个同时在在职与离职记录），本地不建唯一约束」；是否在某范围内唯一待用户（待决 4），登记语句不预设范围。`acceptance/organization/workforce/EmployeeCodeNotKeyIT.java` 已守护「任何 schema 无以 `employee_code` 为列的主键、唯一约束、外键、排他约束」，WF-11 只加 CHECK（`contype = 'c'`），不触碰它。
    - B7-2（one-architecture ⑦ 裁定：保留，不删，不延后）：`org_units.regional_receivables_manager_id`、`org_unit_versions.regional_receivables_manager_id`、`org_units.is_delivery_unit`、`org_unit_versions.is_delivery_unit`、`employees.is_fee_earner` 五列保留；在注册表 `org_units`、`org_unit_versions`、`employees` 条目上写注释「使用方未建，由同步原样入库」，并写明各列现状：区域应收负责人经 `OrgDirectory.findRegionalReceivablesManager`（`application/OrgDirectoryService.java:90-93`）暴露，调用方尚无；`is_delivery_unit` 经 `application/OrgUnitView.java`、`web/OrgUnitResponse.java` 只读暴露；`is_fee_earner` 经 `api/EmployeeProfile.java:7-9` 暴露，使用方（`cost.costing` 等）未建，且不等于可计费。不加使用方，不改取值，不加约束。
    - 测试与验收：注册表由 `DataArchitectureTest` 双向核对（含 D6，见 WF-09）；LE-2 补测如上。
  - **⑤ WF-11　文本列：只落有证据的约束，其余「未核实，不落」**
    - 方法：上限逐列要有证据（代码校验、来源字段长度、规格）；非空白形态的证据是来源适配器的规范化。适配器 `platform/integration/infrastructure/seeyon/SeeyonViewReader.java`：`text()`（:131-134）把空白读成 null 并 `trim`；部门缺名称或路径整行跳过（:27-32）、岗位缺名称跳过（:77-80）、人员缺工号或姓名跳过（:91-96），SQL 另有 `CODE IS NOT NULL AND CODE <> ''`（`SeeyonViewSql.java:46`）；简称由 `platform/integration/infrastructure/legacy/LegacyOrgNames.java:56-57` 同样空白置 null。所以「非空白」对真实来源路径恒成立。长度上限：来源（Seeyon 视图）字段宽度仓库内无记录（`docs/modernization/phase1a/SEEYON_VIEWS.sql` 只有 `NVARCHAR(32)` 的两个枚举 ID 转换）；`phase1a/COLUMN_OWNERSHIP.md` 记的是老系统列宽（部门名 30、姓名 30、工号 10、邮箱 50），按 CLAUDE.md 第 4 条老系统只作参考，且老系统列宽是它自己的存储限制，不是来源上限，把它当上限会让来源里更长的值使每 5 分钟一次的同步在提交时失败，故不采。
    - 逐列结论：
      | 表.列 | 非空白 CHECK | 长度 CHECK | 依据 |
      |---|---|---|---|
      | `legal_entities.name` | `ck_legal_entities_name`（长度与非空白合并成一条） | ≤ 100 | `domain/LegalEntity.java:24、141-145`（`MAX_NAME_LENGTH = 100`，空白与超长抛 `LEGAL_ENTITY_NAME_INVALID`，存入值为 `trim()` 后）。PG `char_length` 按码点计，Java `length()` 按 UTF-16 单元计，库内只会更松，不会误拒 |
      | `legal_entity_versions.name` | `ck_legal_entity_versions_name`（同上） | ≤ 100 | 版本行取自根实体的 `name`（`LegalEntityHistory.java:40-46`） |
      | `positions.name`（NOT NULL）、`positions.code`（可空） | `ck_positions_name`、`ck_positions_code`（`code IS NULL OR btrim(code) <> ''`） | 未核实，不落 | 适配器规范化如上；长度无证据 |
      | `org_units.name`、`.code`、`.short_name` | `ck_org_units_name`、`ck_org_units_code`、`ck_org_units_short_name`（后两者 `IS NULL OR btrim() <> ''`） | 未核实，不落 | 同上；简称适配器已规范化，但 `updateShortNames` 自身不规范化，见 Java |
      | `org_unit_versions.name`、`.code` | `ck_org_unit_versions_name`、`ck_org_unit_versions_code` | 未核实，不落 | 版本行取自同一 `OrgUnit`（`OrgHistory.java:66-74`） |
      | `employees.employee_code`、`.display_name`（NOT NULL） | `ck_employees_employee_code`、`ck_employees_display_name` | 未核实，不落 | `SeeyonViewReader.java:91-96`；`domain/Employee.java` 构造只 `requireNonNull` |
      | `employees.email`（可空） | `ck_employees_email_address`（`email IS NULL OR btrim(email) <> ''`；约束名按 `ddl-naming-baseline.txt:4` 的目标列名起，见裁 5） | 未核实，不落 | `SeeyonViewReader.java:103`；邮箱形态无规格，不收 |
      | `legal_entity_attribution_corrections.correction_reason` | 已有 `ck_..._reason`（`btrim <> ''`，基线 :422），不动 | 未核实，不落 | `Reason`（`kernel/Reason.java`）只禁空白，`web/CorrectLegalEntityAttributionRequest.java` 无 `@Size`，代码与规格都没有上限；是否先在 `application/CorrectLegalEntityAttribution.java` 定上限（可参照同模块 `application/ReleaseSyncRun.java:26` 的 `MAX_REASON_LENGTH = 200`）属新业务口径，见裁 3 |
    - 编码列格式：`positions.code`、`org_units.code`、`org_unit_versions.code`、`employees.employee_code` 只收「非空白」，不收字符集、大小写、长度（来源编码不唯一、形态无规格，卡 WF-11 与 OW X-3 要求不借机收窄）；`_code` 格式 CHECK（G-DDL7，待实现）对这四列留待取证后的后续 PR，现在以「非空白」满足 G-DDL5「文本列有空串 CHECK」。
    - 不含：`sync_diffs.detail`、`sync_runs.held_reason / failure_reason`（拆分 a 带走）；`employees.login_name`、`wecom_user_id`（DA-3 删）；`legal_entity_versions.code`（WF-04）。岗位 `DELETED` 与组织单元 `REVOKED` 的用语不一致属词表问题，不自行裁定，实施者用 `docs/modernization/glossary/new_term_issue.py` 登记给 one-glossary。
    - DDL（表内；每条 CHECK 单行写完，避免 `WorkforceSchemaGuardTest` 按 `,\n` 切列的逻辑误读）：
      ```sql
      CONSTRAINT ck_legal_entities_name CHECK (char_length(name) <= 100 AND btrim(name) <> ''),
      CONSTRAINT ck_legal_entity_versions_name CHECK (char_length(name) <= 100 AND btrim(name) <> ''),
      CONSTRAINT ck_positions_name CHECK (btrim(name) <> ''),
      CONSTRAINT ck_positions_code CHECK (code IS NULL OR btrim(code) <> ''),
      CONSTRAINT ck_org_units_name CHECK (btrim(name) <> ''),
      CONSTRAINT ck_org_units_code CHECK (code IS NULL OR btrim(code) <> ''),
      CONSTRAINT ck_org_units_short_name CHECK (short_name IS NULL OR btrim(short_name) <> ''),
      CONSTRAINT ck_org_unit_versions_name CHECK (btrim(name) <> ''),
      CONSTRAINT ck_org_unit_versions_code CHECK (code IS NULL OR btrim(code) <> ''),
      CONSTRAINT ck_employees_employee_code CHECK (btrim(employee_code) <> ''),
      CONSTRAINT ck_employees_display_name CHECK (btrim(display_name) <> ''),
      CONSTRAINT ck_employees_email_address CHECK (email IS NULL OR btrim(email) <> ''),
      ```
      可空列缺「为空表示」注释属 SS §3 的机械项，不在本 PR 逐列展开，`short_name`、`revoked_at` 顺带写。
    - Java（一处）：`application/RefreshShortNames.java:36-46`：`names` 在交给 `snapshots.updateShortNames` 前把空白值规范为 `null`（与 `LegacyOrgNames.java:56-57` 的适配器约定一致：空白 = 没有简称；`ShortNameSource` 契约补一句），否则 `updateShortNames`（`JdbcOrgSnapshotRepository.java:185-197`）会原样写入空串而被 `ck_org_units_short_name` 拒绝。不改领域记录构造（来源适配器已规范化，库作最后防线；整轮 FAILED 的效果与域内抛异常等价），见裁 2、裁 9。
    - 测试：`WorkforceRowConstraintsIntegrationTest`：十二条 CHECK 各一例（空串、纯空格被拒，带内容的通过，可空列的 NULL 通过）；名称 100 个字符通过、101 个被拒（两张法人表）；`RefreshShortNames` 对 `""` 与 `"  "` 置空而不抛；`legal/LegalEntityIntegrationTest` 既有名称校验用例回归。
    - 改断言与夹具的逐处理由：
      - `application/OrgSnapshotSyncIntegrationTest.java:420-421`：`oldSystem.names = Map.of(unitId(1), "HQ", unitId(2), "")` 后断言 `refreshShortNames.execute()` 为 1。夹具绕过适配器、直接给 `RefreshShortNames` 喂空串，新 CHECK 会拒绝原样写入。采用 Java 规范化后，该行被置为 NULL（原值 `Delivery` 变了，计数仍为 1），**夹具与断言都不改**；若 one-data 选裁 2 的 B，则夹具改为 `null` 值（`Map.of` 不容 null，改 `HashMap`），断言不动，PR 与报告单列。
      - `domain/EmployeeSyncRulesTest.java:59-60` 的空串、纯空格是登录名，不是 WF-11 的列，不受影响。
    - 验收目录受影响：无（`acceptance/` 下直插的快照行名称、工号均非空：`AccountsAcceptanceSupport.java:230-253` 的 `Synthetic unit n`、`ACC…`、`Synthetic person n`；`SourceIdMatchingIT` 经同步写入，值非空）。
  - **⑥ WF-12　期间表、更正表指向聚合根；空更正**
    - 核实：外键目标 `legal_entity_attributions(org_unit_id)`、`(employee_id)` 是基线 :246-247 的单列 uuid 唯一约束，属「仅由 uuid 列组成的唯一约束」，按 one-architecture 2026-10-16 判定（NAMING_STANDARD G-DDL1 行已写明引用本卡 WF-12）放行，不登例外、不改主键。写入次序：`infrastructure/persistence/JdbcLegalEntityAttributionRepository.java:96-123` 先插或更新根（:97-117），再 `insertCorrections`（:122）、再 `writePeriods`（:123），根总在子行之前，故外键为立即检查，不需延迟。更正表 `org_unit_id`、`employee_id` 可空（「恰有一个非空」，基线 :425），外键对 NULL 不检查，天然适配。空更正：`new_effective_to_date`、`previous_effective_to_date` 在基线 :413-414 均为 NOT NULL，用 `<>` 即可（卡上「两列可空时的写法待核」结论：不需要 `IS DISTINCT FROM`）；域内 `domain/LegalEntityAttribution.java:142-143` 对「新结束日等于现结束日」直接返回、不记更正，`previous` 取自已关闭期间的结束日（:134、:151），故不会误拒。保留既有指向 `org_units` / `employees` 的外键（D6 要求直接外键，`DataArchitectureTest.java:340-354`），新外键与其并存，见裁 7。
    - DDL：
      ```sql
      -- org_unit_legal_entities 表内（:271 之后）
      CONSTRAINT fk_org_unit_legal_entities_root_org_unit_id FOREIGN KEY (org_unit_id)
          REFERENCES organization_workforce.legal_entity_attributions (org_unit_id),
      -- employee_legal_entities 表内（:295 之后）
      CONSTRAINT fk_employee_legal_entities_root_employee_id FOREIGN KEY (employee_id)
          REFERENCES organization_workforce.legal_entity_attributions (employee_id),
      -- legal_entity_attribution_corrections 表内（:425 之后）
      CONSTRAINT fk_legal_entity_attribution_corrections_root_org_unit_id FOREIGN KEY (org_unit_id)
          REFERENCES organization_workforce.legal_entity_attributions (org_unit_id),
      CONSTRAINT fk_legal_entity_attribution_corrections_root_employee_id FOREIGN KEY (employee_id)
          REFERENCES organization_workforce.legal_entity_attributions (employee_id),
      CONSTRAINT ck_legal_entity_attribution_corrections_changes CHECK (new_effective_to_date <> previous_effective_to_date),
      ```
      外键名中的 `root` 指聚合根；`..._attribution_org_unit_id` 形式会到 63 字节上限，故不用。外键列的前导索引已有：期间表 `uk_*_effective_from (org_unit_id | employee_id, effective_from_date)`、更正表 `ix_..._org_unit_id`、`ix_..._employee_id`（基线 :427-430）。更正表两个索引对另一类主体多为空列，改部分索引（OW CR-2 非必须）不做，行数小。注册表无改动（子表角色已是 `DIMENSION_VERSIONED` / `LOG_APPEND_ONLY` 且 `aggregate: LegalEntityAttribution`）。
    - Java：无。测试：`WorkforceRowConstraintsIntegrationTest`：期间表、更正表直插无根的行被拒（23503，约束名断言）；先写根后写子行通过；空更正（新旧结束日相同）被拒（23514）；应用路径回归 `scenario/LegalEntityAttributionIntegrationTest`、`legal/OwnLegalEntityAttributionForbiddenIntegrationTest`、`domain/LegalEntityAttributionTest`。
    - 会被新约束拒绝的夹具（**验收目录，须 Acceptance-Ruling**，卡 §9 禁改验收目录）：`acceptance/organization/workforce/AttributionSubjectColumnsIT.java:128-129`（`aCorrectionNamesExactlyOneExistingSubject` 末尾的 `correction(unit, null)`、`correction(null, person)`）：这两行是正例，只建了部门和员工、没有建根归属，外键到根会拒绝。修法只改夹具、不改断言：两行前补 `attribution(unit, null)`、`attribution(null, person)`（同文件 :55-63 已有此 helper，`aSubjectHasOneAttributionAtMost` 已是同样写法）。:125-126 的反例（指向不存在的部门、员工）仍被拒，报错表名仍含 `legal_entity_attribution_corrections`（该测试只断言 SQLState 23503 与表名，两个外键谁先触发都满足），不改；:123-124 的 CHECK 反例先于外键触发，不改。
    - 非验收夹具：`scenario/LegalEntityAttributionIntegrationTest.java:92-94` 先关触发器删更正，再 `truncate legal_entities ... cascade`；cascade 方向本已含期间表与更正表（经 `org_units` / `employees` 外键），`VersionTables.truncate` 已处理更正表的 truncate 触发器，不改。
  - **改断言 / 夹具汇总（供 PR 与报告单列）**：不改任何断言。夹具改动两处：（1）验收目录 `AttributionSubjectColumnsIT.java:128-129` 前补建根（须 Acceptance-Ruling，裁 1）；（2）`OrgSnapshotSyncIntegrationTest.java:420-421` 视裁 2 不改或改为 `null` 值。其余既有测试原样重跑。
  - **验收要点**：（a）`WorkforceRowConstraintsIntegrationTest` 全绿（每条「违反即被拒、合法即通过」，测试名以 `DATA-REPAIR-workforce WF-nn` 开头）；（b）`WorkforceSchemaGuardTest`、`DataArchitectureTest`（D6 对版本表五列转为检查外键）、`DdlNamingTest`、`GlossaryTest` 全绿，不新增例外清单条目，`ddl-naming-baseline.txt` 与 `data-classification.txt` 不变；（c）既有 `OrgSnapshotSyncIntegrationTest`、`OrgDirectoryIntegrationTest`、`OrgHistoryAppendOnlyIntegrationTest`、`LegalEntityHistoryIntegrationTest`、`LegalEntityAttributionIntegrationTest` 原样通过；（d）`scripts/precheck.sh` 输出贴 PR。
  - **预期表结构 diff**（两个空库分别执行改动前后的基线，`pg_dump --schema-only` 对比，差异只能是下列项）：`positions` + `ck_positions_name`、`ck_positions_code`；`org_units` + `ck_org_units_revoked_at`、`ck_org_units_name`、`ck_org_units_code`、`ck_org_units_short_name`；`employees` + `ck_employees_employee_code`、`ck_employees_display_name`、`ck_employees_email_address`；`org_unit_versions` + `ck_org_unit_versions_name`、`ck_org_unit_versions_code`、三个外键、三个索引；`assignment_versions` + 两个外键、两个索引；`legal_entities` + `ck_legal_entities_name`；`legal_entity_versions` + `ck_legal_entity_versions_code_format`、`ck_legal_entity_versions_name`；`org_unit_legal_entities`、`employee_legal_entities` 各 + 一个外键；`legal_entity_attribution_corrections` + 两个外键、`ck_legal_entity_attribution_corrections_changes`。合计 15 条 CHECK、9 个外键、5 个索引。不得出现：`sync_*` 表的任何变化；`positions`、`employees` 的列变化；种子数据变化。
  - **在开 PR 与基线的交集**（`gh pr list --state open` 共 20 个，另查全部远程 `claude/*` 分支相对 `origin/main` 的未合入提交）：改 `organization_workforce` 基线 SQL 的在开 PR / 分支：无；改本卡 `DATA-REPAIR-workforce.md` 的：无；改 `data-access-registry.yaml`、`ddl-naming-baseline.txt`、`data-classification.txt` 的：无。唯一交集是 #341（`claude/one-1a-par-cross-schema-version-ref`）改 `backend/src/test/java/com/forcitis/one/organization/workforce/application/OrgSnapshotSyncIntegrationTest.java` 的 `releaseReasonLosesControlCharacters`（审计断言，约 :278-284）；本 PR 若按裁 2 选 B 才会改该文件 :420-421，同文件不同行，后合者 rebase 即可；选 A 则无交集。R-1b P5、拆分 a（`sync_*` 迁出）、W2（DA-7）尚无远程分支改该基线，实施前仍须核对其是否已合入 main（卡 §1 并行与顺序）。
  - **请 one-data 裁定**：
    - 裁 1　**验收目录夹具**：`AttributionSubjectColumnsIT.java:128-129` 补建根（WF-12），是否发 Acceptance-Ruling（只改夹具、不改断言）。
    - 裁 2　**`short_name` 空白处理**：A（推荐）`RefreshShortNames` 把空白规范为 NULL，`OrgSnapshotSyncIntegrationTest:420-421` 夹具与断言原样；B 不改 Java，夹具改 `null`。两者都不放宽断言。
    - 裁 3　**无证据的长度上限**：除两张法人表的 `name`（100）外，所有来源列与 `correction_reason` 的长度上限均「未核实，不落」。是否同意：由 one-ops 按 ADR-0037 对 Seeyon 视图取 `MAX(LEN())`（明细不进会话与仓库，登记一行）后，另起后续 PR 落长度；`correction_reason` 须先由业务口径定上限再改 Java。
    - 裁 4　**版本表外键索引**：为 WF-09 的五条外键各加一个前导索引（G-DDL2 待实现所需，运行时收益为零），或暂不加、待 G-DDL2 实现时一并处理。推荐加。
    - 裁 5　**`ck_employees_email_address` 的命名**：按 R-2a 目标列名起名；或按现列名起 `ck_employees_email`，R-2a 改列时再改约束名。推荐前者。
    - 裁 6　**WF-10 登记形式**：以 YAML 注释登记（投影、跨来源列、只作展示、使用方未建），不新增注册表键；是否同意，或要求先由 DG-0 加字段。
    - 裁 7　**WF-12 并存外键**：保留指向 `org_units` / `employees` 的既有外键并新增指向根的外键（同一列两条外键），是否同意；备选是删旧外键并登 `fk_exceptions`，与 D6 守护方向相反，不推荐。
    - 裁 8　**卡 §3 的允许文件清单**：§3 只列 `sync_runs`、`sync_diffs`、`org_units`、`assignments`、`assignment_versions`、`org_unit_versions`、`legal_entity_versions`，并写「不碰 `positions`、`employees` 的列」，而 WF-11、WF-12 要动 `positions`、`employees`、`legal_entities`、期间表、更正表的约束。本节只加约束、不改列，是否按此执行并修订 §3；若不接受，WF-11 中 `positions`、`employees` 三行并入 W2（DA-7）。
    - 裁 9　**不进域层守卫**：不在 `Position`、`OrgUnit`、`Employee` 构造里加非空白校验（来源适配器已规范化，库作最后防线）；是否同意。

- **数据设计补（PR-8b：WF-11 长度上限，one-data 2026-10-16 裁定）**（叠在 PR-8 #366 之上；依据 `../reviews/DATA-REPAIR-workforce-PR8.md` 末段「追加：来源列长度取证与长度上限」；基线 SQL 即 PR-8 之后的 `V20261014131000__organization_workforce_baseline.sql`，行号取 PR-8 头 aa82e57f；本节只做文档核实，未执行 DDL，未跑测试）
  - **裁定**：长度上限取来源视图声明列宽（不取实际最大值，不取老系统列宽）；法人名称仍 100（PR-8 已落）；`correction_reason` 取 200，与 `ReleaseSyncRun.MAX_REASON_LENGTH`（`application/ReleaseSyncRun.java:26`）、`ForceUserLogout.MAX_REASON_LENGTH`（`platform/identity/application/ForceUserLogout.java:26`）同口径；先入口校验、再库内 CHECK。
  - **约束写法：合并，不另加**。PR-8 已有的同列非空白 CHECK 就地改写成「长度 AND 非空白」一条，约束名不变，与 PR-8 已落的 `ck_legal_entities_name`、`ck_legal_entity_versions_name`（基线 :229、:406，`char_length(name) <= 100 AND btrim(name) <> ''`）同一形态。不新增约束名，故 `DdlNamingTest` 的命名与长度检查、`ddl-naming-baseline.txt`、注册表不受影响；可空列写成 `col IS NULL OR (char_length(col) <= N AND btrim(col) <> '')`。每条 CHECK 仍单行写完（`WorkforceSchemaGuardTest` 按 `,\n` 切列）。`char_length` 按码点计，来源（SQL Server `nvarchar(n)` 按 UTF-16 单元计）只会更长地计、不会更短，库内只会更松，同步不会因此被拒。
  - **逐列 DDL**（行号为 PR-8 头基线；列均已存在，不改列）：
    | 表.列 | 约束名（不变） | 上限 | 基线行 |
    |---|---|---|---|
    | `positions.name` | `ck_positions_name` | 500 | :14 |
    | `positions.code` | `ck_positions_code` | 500 | :15 |
    | `org_units.name` | `ck_org_units_name` | 500 | :45 |
    | `org_units.code` | `ck_org_units_code` | 500 | :46 |
    | `org_units.short_name` | `ck_org_units_short_name` | 255 | :47 |
    | `org_unit_versions.name` | `ck_org_unit_versions_name` | 500 | :338 |
    | `org_unit_versions.code` | `ck_org_unit_versions_code` | 500 | :339 |
    | `employees.employee_code` | `ck_employees_employee_code` | 500 | :84 |
    | `employees.display_name` | `ck_employees_display_name` | 500 | :85 |
    | `employees.email`（约束名按 R-2a 目标列名） | `ck_employees_email_address` | 100 | :86 |
    | `legal_entity_attribution_corrections.correction_reason` | `ck_legal_entity_attribution_corrections_reason` | 200 | :438 |
    ```sql
    CONSTRAINT ck_positions_name CHECK (char_length(name) <= 500 AND btrim(name) <> ''),
    CONSTRAINT ck_positions_code CHECK (code IS NULL OR (char_length(code) <= 500 AND btrim(code) <> '')),
    CONSTRAINT ck_org_units_name CHECK (char_length(name) <= 500 AND btrim(name) <> ''),
    CONSTRAINT ck_org_units_code CHECK (code IS NULL OR (char_length(code) <= 500 AND btrim(code) <> '')),
    CONSTRAINT ck_org_units_short_name CHECK (short_name IS NULL OR (char_length(short_name) <= 255 AND btrim(short_name) <> '')),
    CONSTRAINT ck_org_unit_versions_name CHECK (char_length(name) <= 500 AND btrim(name) <> ''),
    CONSTRAINT ck_org_unit_versions_code CHECK (code IS NULL OR (char_length(code) <= 500 AND btrim(code) <> '')),
    CONSTRAINT ck_employees_employee_code CHECK (char_length(employee_code) <= 500 AND btrim(employee_code) <> ''),
    CONSTRAINT ck_employees_display_name CHECK (char_length(display_name) <= 500 AND btrim(display_name) <> ''),
    CONSTRAINT ck_employees_email_address CHECK (email IS NULL OR (char_length(email) <= 100 AND btrim(email) <> '')),
    CONSTRAINT ck_legal_entity_attribution_corrections_reason CHECK (char_length(correction_reason) <= 200 AND btrim(correction_reason) <> ''),
    ```
    列注释补长度：`correction_reason` 改为「更正原因：操作人填写，不得为空，最多 200 个字符」（基线 :458）；其余列注释不动。
  - **`correction_reason` 入口校验**：写更正原因的应用入口只有 `application/CorrectLegalEntityAttribution.execute`（`backend/src/main/java/com/forcitis/one/organization/workforce/application/CorrectLegalEntityAttribution.java:47`），HTTP 入口 `web/LegalEntityAttributionController.java:85-91`（部门）与 `:93-99`（员工）都只转交它；`web/CorrectLegalEntityAttributionRequest.java` 无 `@Size`，`kernel/Reason.java` 只禁空白。校验放在该文件 :57 `Reason.ofNullable(reasonText)` 与 :58-61 空白拒绝之后：新增常量 `static final int MAX_REASON_LENGTH = 200`（与 `ReleaseSyncRun` 同名同值），`reason.value().length() > MAX_REASON_LENGTH` 时抛 `DomainException(WorkforceError.ORGANIZATION_WORKFORCE_ATTRIBUTION_CORRECTION_REASON_REQUIRED, ErrorKind.UNPROCESSABLE, "a reason of 1.." + MAX_REASON_LENGTH + " characters is required")`，并照 `ReleaseSyncRun.java:51-56` 记一条 `ALERT organization.workforce` 错误日志（缺失或无效输入失败并告警）。不在检查之前读库、不写更正表、不发事件。
  - **错误码**：复用 `ORGANIZATION_WORKFORCE_ATTRIBUTION_CORRECTION_REASON_REQUIRED`（HTTP 422），不新增。核对先例（`git grep MAX_REASON_LENGTH`）：`ReleaseSyncRun` 超长复用 `ORGANIZATION_WORKFORCE_RELEASE_REASON_REQUIRED`（`ErrorKind.INVALID`），`ForceUserLogout` 超长复用 `PLATFORM_IDENTITY_FORCED_LOGOUT_REASON_REQUIRED`（`ErrorKind.UNPROCESSABLE`）——都是「缺失与超长共用一个 *_REASON_REQUIRED」，本入口的空白原因本就是 422，故超长同 422。
  - **与先例的一处差别**：`ReleaseSyncRun`、`ForceUserLogout` 先 `strip()` 再量；本入口量 `reason.value().length()`（原值）。理由：更正表存入的是 `Reason.value()` 原值（`JdbcLegalEntityAttributionRepository.java:130` 的插入），库内 CHECK 量的也是存入值，入口与库内用同一把尺，不会出现「入口放行、库内拒绝」的 500。前后空白计入长度；若 one-data 要求先 `strip` 再存，另议。
  - **Java（本补丁只此一处）**：上述入口校验。来源列长度不加 Java 校验（来源适配器与同步不会超限；库作最后防线，与 PR-8 裁 9 一致）。
  - **预期表结构 diff**（两个空库分别执行 PR-8 与 PR-8b 的基线，`pg_dump --schema-only` 对比）：只有上表十一条 CHECK 的定义文本变化（新增 `char_length(...) <= N`），无新增、删除、重命名约束，无列变化，无索引变化；`legal_entity_attribution_corrections.correction_reason` 的列注释一行变化。
  - **验收要点**：各列恰为上限的值通过、上限 + 1 被拒（23514 + 约束名）；更正原因 200 字经 `POST /api/v1/organization/employee-legal-entities/{employeeId}:correct` 通过，201 字 422 `ORGANIZATION_WORKFORCE_ATTRIBUTION_CORRECTION_REASON_REQUIRED` 且更正表无新行；PR-8 的 `WorkforceRowConstraintsIT` 空白用例（空串、纯空格、带内容）原样重跑通过，`RULE-385` 既有用例（原因 `r`、`typed the wrong end day`）不受影响。
  - **改断言 / 夹具**：无。

## 5. 待用户决定
1. 保留期：`sync_run_held_separations` 随运行 2 年是否认可并补 GUIDING §6 一行；「待离职名单」整表的敏感级别（OW 待决 3、4；规则卡认定未公开离职信息敏感）；离职人员在 `employees` 保留多久（X-5，属 W2）。
2. SR-4：是否在 `sync_runs` 增 `release_reason`，还是以审计为唯一记录（OW 待决 9）。
3. 法人状态：信息图 OBJ-54「筹建 / 存续 / 注销」与 `ACTIVE` / `INACTIVE`（可反向）的对应（LE-1，OW 待决 5）；法人名称是否全局唯一（LE-3，待决 7）。
4. 工号、部门编码、岗位编码在什么范围内唯一，或明确只作展示（OW 待决 6）。
5. WF-09 的例外撤销是否由 one-coordinator 接受（OW 待决 8）。

## 6. 不含（已有归属，本卡不起条目）
| 项 | 归属 |
|---|---|
| HS-1（高；原阻断）`sync_run_held_separations` 复合主键 | 已被 R-1b P5 覆盖（`sync_run_held_separation_id`、`uk_…_sync_run_id_employee_id`；已核 P5 分支基线 wf :444-455） |
| HS-3 外键索引 | 同上，P5 `ix_…_employee_id` |
| P-1（高）`positions` 版本化 | W2「主数据版本化」（DA-7）；**用户已定做维度型版本表，生效依据已改为人事业务生效日（DECLARED，intent 2026-10-16-134434，取代同步观察日）**（收件箱 2026-10-16-one-data-employee-position-versioning；intent `2026-10-16-085331-one-data-员工与岗位怎样版本化-都做维度型.md`）；positions 是否在 W2 的 25 张表内：待核（OV 未核实），不在则 W2 卡须补入；生产前闸门 |
| E-1（高）`employees` 版本化、E-2（中）`separated_at` | W2 DA-7（`employee_versions`，同上用户已定维度型，生效依据 DECLARED（人事业务生效日））；离职时刻字段按此口径定（原拟改名 `separation_observed_at`，生效依据改为人事业务生效日后，名称与口径待 W2 重定：离职日由人事登记，不再是同步观察时刻）：原拟改名 `separation_observed_at`、按规则卡每次离职各记一条，首轮快照已离职者为未知、不记部署时刻；W2 前不改现列（OV）。SS §4 第 2 组（`separated_at:72` ⇔ `employment_status:70 = 'SEPARATED'`；`login_name:66` 为空 ⇔ 离职）随 W2 与 DA-3 一并处理，本卡不加；本项不再列待用户决定 |
| SD-1（高；O15，ADR-0033 第 7 项第 2 条）`sync_diffs.subject_key` → 类别 + `subject_id uuid` | **P5 未覆盖**（已核 P5 分支 wf :187 仍为 `subject_key text NOT NULL`）；归 ADR-0039 拆分 a：按最终形态在集成层新建 `sync_diffs`（P5 卡 §3.1），不在旧位置改；生产前 |
| O15 缺项 3（高，SS 补审）`sync_runs`、`sync_diffs` 迁 `platform_integration`（ADR-0039 第 2 条第 1 款、第 6 条） | P5 未覆盖（已核 P5 分支 wf :120 `sync_runs` 仍在 organization_workforce）；已排「拆分 a/b」生产前（COORDINATOR_LEDGER:92）；归 ADR-0039 实施 PR |
| O15 缺项 4（高）`sync_run_held_separations` 改 workforce 的 `separation_hold` 记录，放行只放这一条离职 | P5 未覆盖：P5 只改主键与外键索引，`sync_run_id` 外键与 `ON DELETE CASCADE` 仍在（已核 P5 分支 wf :452）；归 ADR-0039 拆分 a/b；本卡 WF-03 只改注册表保留期，随该表改造失效时重写 |
| O15 缺项 5（高）`employees` 删 `login_name`、`wecom_user_id`，`external_identities` 建在 `platform_integration`（ADR-0033 第 4 项第 5 条、ADR-0039 第 4 条第 1 款，DA-3） | P5 未覆盖（已核 P5 分支 wf :66、:69 仍在，P5 分支无 `external_identities`）；归 DA-3，生产前；所以本卡不对这两列加长度、成对约束（WF-11 已排除） |
| 补审 B7-2（中，待核）`org_units.regional_receivables_manager_id:33`、`org_unit_versions:317`、`is_delivery_unit:35`、`employees.is_fee_earner:71` 使用方（receivables.*、delivery.execution、cost.costing）未建 | 待 one-architecture 判是否被 ADR-0033 或同步规格点名要求同步；判前不动（SS 补审 B7-2） |
| O15 缺项 8（中）`employees.email`、`gender`、`wecom_user_id`、`display_name` 分级仍 SENSITIVE | ADR-0043 实施 PR（SS 补审缺项 8） |
| LV-1(b) 代码跨时间排他约束 | 中，随 W2 法人代码规则（OV） |
| OU-2 `region_category` → `region_kind` 改名 | R-2a 命名卡（`ddl-naming-baseline.txt:6`）；待核该卡是否已排 |
| LE-3（中）默认约束名、X-7 内联枚举 CHECK 具名（SS §6 实为 12 处，非 OW 写的 13 处） | 约束名随 R-2a；内联枚举 CHECK 随 G-D12 一次改基线（OV 建议新增规则 4）；LE-3 的 `name` 长度在 WF-11 |
| X-4 分级（四段格式）、X-5 个人信息保留 | ADR-0043 实施 PR；保留期待用户 |
| X-1、X-2、X-11、X-13 | one-architecture / 流程项，待核 |

## 7. 验证办法
- 守护：`DdlNamingTest`、`DataArchitectureTest`、`GlossaryTest` 全绿；新增 CHECK 全部具名；不新增例外清单条目（WF-07 删 `ddl-naming-baseline.txt:8` 一行、WF-09 撤销 `fk_exceptions` 为减项）。
- 测试：`SyncRunStateMachineTest` 原样重跑；WF-01 至 WF-07、WF-09、WF-11 至 WF-14 各一条「违反即被拒、合法即通过」集成测试；WF-01 额外跑一次完整同步（成功、暂停、放行、失败、遗弃关闭）确认无提交期失败。测试名以 `DATA-REPAIR-workforce WF-nn` 开头。
- 表结构 diff：两个空库分别执行改动前（P5 合并后的 main）、改动后的基线，`pg_dump --schema-only` 对比；预期差异只能是第 4 节所列。不得出现：`positions`、`employees` 的列变化；`sync_run_held_separations` 的任何变化；种子数据变化。
- `scripts/precheck.sh <基线>` 全部 PASS 或有理由的 SKIP，输出贴进 PR。

## 8. 执行者与复审
- 执行者：sonnet 子代理；WF-01 首轮复审用 opus（状态机逐条对调用点），其余 sonnet 复核。
- 工作区：独立 worktree，分支 `claude/one-1a-next-4-data-repair-workforce`，基线为 P5 合并后的 main。
- 报告：向 one-1a-next-4 并抄 one-coordinator，分开写「已验证」与「未运行」。

## 9. 禁止事项
- 不放宽、不删除断言；不改验收测试目录；不改卡外文件；不顺手重构、改名或统一风格。
- 不加 `finished_at >= started_at`；不给 `held_reason` 加「非空串」约束而不先保证 `verdict.reason()` 非空。
- 不追加迁移（ADR-0038）；不改 W2 重写的表形状。
- 不联网、不起容器（Testcontainers 白名单镜像除外）、不建账号；不打印、不写入密钥。
- 缺失或无效输入失败并告警，不静默跳过。

## 10. 机械类低级项（按表列出，不逐条展开；列清单与行号见 `review-parts/SCAN_SUPPLEMENT.md`）

SS 行号取 `origin/main`。同一类改法一致，由执行者按表一次改完，每类一条验收即可。无长度上限为中，其余为低；§1 虽为中，因机械且同一改法，一并列此。

| SS 节 | 类 | 级别 | 属本卡 schema 的范围 |
|---|---|---|---|
| §1 | text 列无长度与格式约束 | 中 | wf 共 22 列：positions 2、org_units 3、employees 5、sync_runs 2、sync_diffs 4、legal_entities 1、org_unit_versions 2、legal_entity_versions 2、legal_entity_attribution_corrections 1（`correction_reason:415` 只有 `btrim <> ''`）；已由 WF-02、WF-04、WF-07、WF-11 覆盖大部分，`login_name`、`wecom_user_id`、`subject_key` 不加（见 WF-11） |
| §2 | 业务默认值（NAMING_STANDARD §2.4a-3 要求应用显式写入） | 低 | wf 共 4 项：`org_units.is_delivery_unit:35`、`employees.is_fee_earner:71`、`sync_runs.hold_kind_codes:147`、`released_kind_codes:148`（均 SS 新记，分报告未记；后两项的 `'{}'` 与 WF-02(a) 的 `<@` 约束同表，一并改） |
| §3 | 可空列缺「为空表示」注释 | 低 | 「无空值说明」共 42 列：positions 2、org_units 7、employees 4、assignments 1（`reports_to_employee_id:103`）、sync_runs 18、sync_diffs 3、org_unit_versions 4、assignment_versions 2、legal_entity_attribution_corrections 1（`correction_number:409`）；「等价措辞」另 6 列待 one-quality 判守护是否接受 |
| §4 | 成对可空列缺 CHECK（中） | 中 | 第 1 组 → WF-01、WF-02；第 2 组 → W2（DA-7）与 DA-3；第 3 组 → WF-05；第 4 组 → WF-07；第 15 组「已含」对照（`legal_entity_attributions`、`corrections` 的「恰有一个非空」，区间 CHECK 与排他约束）无须动 |
| §5 | `DEFAULT now()` 与「应用注入时钟」矛盾 | 低 | wf 15 表 30 列（`created_at`、`updated_at`，行号见 SS §5 wf 行）；全库口径另卡（PI 待决 10），本卡不单改 |
| §6 | 内联枚举 CHECK 未具名 | 低 | wf 12 处：positions.status:10；org_units.status:31、region_category:34；employees.gender:68、employment_status:70；sync_runs.trigger_kind:122、status:123、hold_kind_codes:147、released_kind_codes:148（后两处为数组 `<@ ARRAY[…]` 形式）；sync_diffs.entity_kind:186、change_kind:188；legal_entities.status:214；另内联数值 CHECK `sync_diffs.diff_number:185`；随 G-D12 一次改基线 |

### 保留期与匿名化（用户已定「按 one-data 建议」，见 `../RETENTION_PROPOSAL.md`）

- `employees` 的姓名、工号随引用存续；登录名、邮箱、企业微信 ID 离职满 180 天置空并记审计（离职清除作业随 W2 DA-7 设计）。
- `assignment_versions` 等个人任职史版本表：员工离职满 10 年后删除该员工的任职版本行。
- `sync_run_held_separations`：注册表改为随所属同步运行（运行 2 年，被拦截的放行后再 1 年），不再单列 1 年。
