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
- `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql`（只限 `sync_runs`、`sync_diffs`、`org_units`、`assignments`、`assignment_versions`、`org_unit_versions`、`legal_entity_versions` 的约束、索引）
- `backend/architecture/data-access-registry.yaml`（只限 workforce 各表条目：`sync_run_held_separations` 保留期、`legal_entities` 投影登记、快照三表 `business_key` 说明、版本表 `fk_exceptions` 与逐列不进版本的理由）
- Java：`backend/src/main/java/com/forcitis/one/organization/workforce/` 下 `infrastructure/persistence/JdbcSyncRunQueries.java`（WF-08 若需调整查询）、`domain/Assignment.java`（WF-06 若 `position_id` 改 NOT NULL）；其余只读
- 测试：`SyncRunStateMachineTest`（只读，用来核对 CHECK 取值）、`organization/workforce` 下新增的 CHECK 集成测试（类名由测试代理定）

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
| P-1（高）`positions` 版本化 | W2「主数据版本化」（DA-7）；**用户已定做维度型版本表，生效依据为同步观察日（OBSERVED）**（收件箱 2026-10-16-one-data-employee-position-versioning；intent `2026-10-16-085331-one-data-员工与岗位怎样版本化-都做维度型.md`）；positions 是否在 W2 的 25 张表内：待核（OV 未核实），不在则 W2 卡须补入；生产前闸门 |
| E-1（高）`employees` 版本化、E-2（中）`separated_at` | W2 DA-7（`employee_versions`，同上用户已定维度型、OBSERVED）；离职时刻字段按此口径定：改名 `separation_observed_at`、按规则卡每次离职各记一条，首轮快照已离职者为未知、不记部署时刻；W2 前不改现列（OV）。SS §4 第 2 组（`separated_at:72` ⇔ `employment_status:70 = 'SEPARATED'`；`login_name:66` 为空 ⇔ 离职）随 W2 与 DA-3 一并处理，本卡不加；本项不再列待用户决定 |
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
