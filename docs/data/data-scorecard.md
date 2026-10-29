# Phase 1a 数据架构逐表检查（OLTP / OLAP 准备 / DAMA）

- 日期：2026-10-14 · 会话：psa-olap-architecture
- 用户原话（经 psa-coordinator 转达）：问「这些表符合olap、oltp、dama的核心思想吗？这么检查对吗？」后选「做，并把能自动化的变成守护 (Recommended)」。
- 范围：1a 分支 `claude/new-session-ff6cac` 提交 `9e89c02`（含 main 上的全部表，以及 `cost_timesheet`、`organization_attendance` 与审批 / 集成新增）。共 26 个迁移、10 个 schema、34 张表、322 列；Flowable `ACT_*` 另见 §5。
  - 派工时给的是 `7cb1ca7`；检查开始时 1a 已前进到 `9e89c02`，以后者为准。
  - main 上较新的 `DdlNamingTest`、`ddl-naming-baseline.txt`、`data-classification.txt` 尚未合入该分支；本检查按 main 上的版本判断命名与分级。
- 方法：
  - 表结构与代码用法：haiku 子代理用脚本按迁移顺序推算最终表结构，并扫描 Java 中的 SQL 字面量；
  - 逐项判断：本会话（opus）；
  - 依据：GUIDING_PRINCIPLES §6–§8（含用户已认可的 1a 表保留期）、NAMING_STANDARD §2.4–§2.5、ADR-0016、0025（提议）、0026、0027、`docs/architecture/events/event-analytics-fields.md` §6、METHODS M3、DATA_DESIGN_CHECKLIST §14。
- 判定：✓ 符合；✗ 不符合（问题编号见 §3）；△ 部分符合或有瑕疵；— 不适用。

## 0. 结论

1. **OLTP 基础扎实**：
   - 状态列全部有 `CHECK`；时间列全部是 `timestamptz`（框架表除外）；可变聚合大多有 `version`；
   - 没有跨 schema 外键，模块之间只按 ID 引用；
   - 审计表只追加，由触发器保证；
   - 1a 尚无金额列。
   - 主要缺口：同一 schema 内有 6 处引用没有外键；1 张可变表无 `version`；2 处把多个值拼成一个字符串（违反第一范式）；工时单没有查询索引。
2. **OLAP 准备是最大缺口**：
   - 维度类数据只有当前值，没有生效区间：法人、组织单元、任职与汇报线全是覆盖式快照，变化历史只留在保留 1 年的同步差异文本里。这与 Q10「按生效期保留历史」、ADR-0026 第 9 条「部门取事实发生时的部门」、ADR-0025 都不一致。
   - ADR-0026 第 6 条要求 1a 交付的「部门 → 法人」「员工 → 法人」两张生效区间表，在该提交中**不存在**。
   - 业务事实缺分析字段：工时单没有「事实发生时的部门」与单据编号；审批任务没有认领时刻与办理时刻，审批时效无从统计。
3. **DAMA**：
   - 34 张表都有表注释，322 列中 312 列有列注释；但多数表注释没写粒度（「每 … 一行」）。
   - 主数据归属与 COLUMN_OWNERSHIP、ADR-0026 / 0027 一致；老单据编号对照表 `legacy_numbers` 是很好的血缘载体。
   - 用户已认可的保留期中，**外发投递、同步记录与差异没有清理作业**，审计的 3 年归档也未登记；`consumed_events` 不在已认可清单里，会无限增长。
   - 敏感分级漏登姓名、登录名、企业微信 ID、收件人等；`architecture/data-access-registry.yaml`（ADR-0026 第 5 条）在仓库中不存在。
4. 结论分两类：
   - **要改 15 项**（K1–K15），进 1a 下一批；
   - **有取舍 5 项**（T1–T5），请用户定。
   - 能自动化的写成 10 条守护规格（§4），以一张扩展的数据访问注册表为中心，由 1a 实现。
5. 「这么检查对吗」：方向对。DAMA 还应看「血缘」与「数据质量规则是否落成约束」，OLAP 还应看「业务行上有没有分析必带字段」，本检查已加入。数据架构检查清单并入 DATA_DESIGN_CHECKLIST §14.1，作为每波复审项。

## 1. 逐表评分

表类型：根 = 聚合根；子 = 聚合子表；快照 = A8 同步快照；配置；日志；框架（Spring Session、Spring Modulith、ShedLock，列名由框架固定，豁免命名与注释检查）。

| # | 表 | 类型 | 范式 / 冗余 | 键与约束 | version | 状态 CHECK | 时间 | 生效区间 | 分析字段 | 注释（含粒度） | 保留期作业 | 敏感登记 | 归属 / 血缘 | 问题 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | `cost_timesheet.timesheets` | 根（工时单） | ✓ | △ 无查询索引 | ✓ | ✓ | ✓ | — | ✗ 缺部门、单据编号 | △ 未写粒度 | —（业务单据，随会计档案） | ✓ | 新系统自有；迁入历史经 `legacy_numbers` | K1 K2 K13 K9 |
| 2 | `cost_timesheet.consumed_events` | 幂等记录 | ✓ | ✓ 代理主键 consumed_event_id，（事件标识，消费者）为唯一约束 | —（只插入） | — | ✓ | — | — | ✓ | ✗ 无保留期 | — | 新系统 | T3 |
| 3 | `delivery_execution.work_packages` | 根（占位） | △ `actual_hours` 为派生值 | △ 主键未按 `pk_` 命名 | ✓ | ✓ | ✓ | — | — | △ | — | — | 沿用老系统雪花 ID；1a 无写入 | T4 |
| 4 | `organization_attendance.work_calendar_years` | 根（日历年度） | ✓ | ✓ | ✓ | — | ✓ | —（年份即区间） | — | ✗ 4 列无注释 | 永久 | — | 新系统；老日历待迁入 | K8 T2 |
| 5 | `organization_attendance.work_calendar_days` | 子 | ✓ | ✓ | —（随根） | ✓ | ✓ | — | — | ✗ 3 列无注释 | 永久 | — | 同上 | K8 T2 |
| 6 | `organization_workforce.positions` | 快照 | ✓ | ✓ | ✓ | ✓ | ✓ | ✗ 覆盖，无历史 | — | △ | 当前态 | — | A8（COLUMN_OWNERSHIP） | T1 |
| 7 | `organization_workforce.org_units` | 快照 | ✓ | ✗ 上级、负责人、区域应收负责人 3 处无外键 | ✓ | ✓ | ✓ | ✗ | — | △ | 当前态 | — | A8；`short_name` 每日从老系统刷新 | K6 T1 |
| 8 | `organization_workforce.employees` | 快照 | ✓ | ✓ | ✓ | ✓ | ✓ | ✗ | △ `separated_at` 是首次同步到离职的时间，不是离职日 | △ | 当前态 | ✗ 姓名、登录名、企业微信 ID、工号未登记 | A8 | K10 T1 T5 |
| 9 | `organization_workforce.assignments` | 快照 | ✓ | ✗ 直属领导无外键 | ✓ | — | ✓ | ✗ 汇报线无历史 | — | △ | 当前态 | — | A8 | K6 T1 |
| 10 | `organization_workforce.sync_runs` | 日志（运行记录） | ✓ | ✓ | ✓ | ✓ | ✓ | — | — | ✗ `version` 无注释 | ✗ 认可 2 年，无清理作业 | — | 新系统 | K7 K8 |
| 11 | `organization_workforce.sync_diffs` | 日志（运行明细） | ✗ `changed_fields` 逗号拼接 | ✓ | — | △ `issue_kind` 无 CHECK（已在命名基线） | ✓ | — | — | △ | ✗ 认可 1 年，无清理作业 | △ `detail` 可能含姓名 | 新系统 | K7 K14 |
| 12 | `organization_workforce.legal_entities` | 主数据（新系统自有） | ✓ | △ 唯一约束未命名 | ✓ | ✓ | ✓ | ✗ 无生效区间 | — | ✗ `version` 无注释 | 永久 | — | 新系统（组织数据管理员，ADR-0027） | K4 K8 |
| — | 「部门 → 法人」「员工 → 法人」生效区间表 | 维度（ADR-0026 第 6 条） | — | — | — | — | — | ✗ **缺表** | — | — | — | — | — | K3 |
| 13 | `platform_approval.approval_requests` | 根 | ✓ | △ 唯一约束 `uq_` 命名 | ✓ | ✓ | ✓ | — | ✓ 法人；单据编号由业务单据持有 | ✓ | 业务 | — | 新系统；老审批只读归档 | — |
| 14 | `platform_approval.start_rounds` | 子 | ✓ | ✗ 未以外键指向审批请求 | —（只插入） | ✓ | ✓ | — | — | ✓ | 业务 | — | 新系统 | K6 |
| 15 | `platform_approval.approval_tasks` | 根（任务） | ✓ | ✗ 未以外键指向审批请求 | ✓ | ✓ | ✓ | — | ✗ 缺认领、办理时刻 | ✓ | 业务 | — | 新系统（引擎任务的投影） | K5 K6 |
| 16 | `platform_audit.audit_entries` | 日志（只追加） | ✓ | ✓ | —（触发器禁改） | — | ✓ | — | ✓ 事件号、关联号 | ✓ | △ 3 年后归档，作业未登记 | △ `detail` | 新系统 | K7 |
| 17 | `platform_identity.user_accounts` | 根 | ✓ | ✓ | ✓ | ✓ | ✓ | — | — | △ | 永久 | ✓ | 新系统（ID 与员工 ID 相同） | — |
| 18 | `platform_identity.roles` | 根 | ✓ | ✓ | ✓ | ✓ | ✓ | — | — | △ | 永久 | — | 新系统 | — |
| 19 | `platform_identity.role_permissions` | 子 | ✓ | ✓ | —（整组替换） | — | ✓ | — | — | △ | 永久 | — | 新系统 | — |
| 20 | `platform_identity.account_roles` | 关联 | ✓ | ✓ | —（整组替换） | — | ✓ | — | — | △ | 永久 | — | 新系统 | — |
| 21 | `platform_identity.navigation_items` | 配置（种子） | ✓ | ✓ | —（只读） | — | ✓ | — | — | △ | 永久 | — | 新系统 | — |
| 22 | `platform_identity.login_states` | 一次性凭证 | ✓ | △ 主键未命名；代理主键 login_state_id，state_hash 为唯一约束 | — | ✓ | ✓ | — | — | ✓ | ✓ 到期即删 | ✓ | 新系统 | — |
| 23 | `platform_identity.sso_ticket_uses` | 一次性凭证 | ✓ | ✓ 代理主键 sso_ticket_use_id，（凭证来源，摘要）为唯一约束 | — | — | ✓ | — | — | ✓ | ✓ 到期即删 | ✓ | 新系统 | — |
| 24 | `platform_identity.ticket_issuers` | 配置 | ✓ | ✓ | —（迁移维护） | — | ✓ | — | — | ✓ | 永久 | ✓ | 新系统 | — |
| 25 | `platform_identity.emergency_admins` | 根 | ✓ | ✓ | ✗ 有 UPDATE 无 `version` | — | ✓ | — | — | ✓ | 永久 | △ `account_name` 未登记 | 新系统 | K12 K10 |
| 26 | `platform_identity.sessions` | 框架 | 框架 | ✓ | — | — | 框架（毫秒 bigint） | — | — | ✓ | ✓ | ✓ | 框架 | 豁免 |
| 27 | `platform_identity.sessions_attributes` | 框架 | 框架 | ✓ | — | — | ✓ | — | — | ✓ | ✓ 随会话删除 | ✓ RESTRICTED | 框架 | 豁免 |
| 28 | `platform_integration.event_publication` | 框架 | 框架 | ✓ | — | ✓ | ✓ | — | — | ✓ | ✓ 完成后归档 | — | 框架 | 豁免 |
| 29 | `platform_integration.event_publication_archive` | 框架 | 框架 | ✓ | — | ✓ | ✓ | — | — | ✓ | ✓ 30 天清理 | — | 框架 | 豁免 |
| 30 | `platform_integration.outbound_deliveries` | 投递队列 | ✗ 收件人以竖线拼接 | ✓ | ✓ | ✓ | ✓ | — | ✓ 关联号 | △ | ✗ 认可 90 天 / 1 年，无清理作业 | ✗ 收件人未登记 | 新系统 | K7 K10 K14 |
| 31 | `platform_numbering.numbering_rules` | 配置（编号规则） | ✓ | ✓ | ✓ | ✓ | ✓ | ✗ 无版本历史与生效日 | — | △ | 永久 | — | 新系统 | K11 |
| 32 | `platform_numbering.numbering_counters` | 计数器 | ✓ | ✓ 代理主键 numbering_counter_id；（单据类型，法人，周期）唯一，法人为空表示全局计数 | —（原子递增） | — | ✓ | — | — | ✓ | 永久 | — | 新系统 | — |
| 33 | `platform_numbering.legacy_numbers` | 对照（血缘） | ✓ | △ 唯一约束未按 `uk_` 命名 | —（只插入） | — | ✓ | — | ✓ | ✓ | ✓ 永久 | — | 迁入 | — |
| 34 | `platform_scheduling.scheduler_locks` | 框架 | 框架 | ✓ | — | — | ✓ | — | — | ✓ | — | — | 框架 | 豁免 |
| 35 | `platform_identity.role_versions` | 维度（版本，角色定义） | ✓ | ✓ 代理主键 role_version_id；（角色，版本号）唯一；同角色、同名（不分大小写）生效区间 EXCLUDE；外键均 RESTRICT | —（只追加，版本号即并发序） | ✓ 角色状态、范围类型、变更渠道 | ✓ timestamptz，有限值 CHECK | ✓ `effective_from_at`/`effective_to_at` 半开区间 + EXCLUDE | — | ✓ 含粒度 | 永久（3 年在线、10 年归档，清理作业随审计在线删除，首次生产部署后 30 个月内，owner one-1a-par） | —（无个人信息） | 新系统；ADR-0034 | — |
| 36 | `platform_identity.account_role_grants` | 关联（版本，账号持有角色） | ✓ | ✓ 代理主键；（账号，角色）生效区间 EXCLUDE；外键均 RESTRICT；授予渠道与收回原因的互斥 CHECK | —（只追加，关闭开放行） | ✓ 授予渠道、收回原因 | ✓ timestamptz，有限值 CHECK | ✓ 同上 | — | ✓ 含粒度 | 同 `role_versions`，先于它清理 | — | 新系统；ADR-0034（迁入行 MIGRATION 渠道带批次） | — |

主键、唯一约束未按 `pk_` / `uk_` 命名的项（第 3、12、13、22、33 行）已在 main 的命名守护基线中，或将被它捕获，不另列问题。

## 2. 汇总

| 维度 | 符合 | 不符合或部分符合 | 说明 |
|---|---|---|---|
| 状态 CHECK | 16 / 16 状态列 | 0 | 另有 `issue_kind` 未加枚举 CHECK（已在基线） |
| timestamptz | 全部业务表 | 0 | 框架会话表用毫秒 bigint，豁免 |
| 可变聚合 version | 15 张有 | 1（`emergency_admins`） | 只插入、整组替换、计数器原子递增的表不适用 |
| 同 schema 外键 | — | 6 处缺失 | 见 K6 |
| 第一范式 | — | 2 处拼接字符串 | 见 K14 |
| 生效区间（维度） | 0 | 5 张快照或主数据表，另缺 2 张表 | 见 K3、K4、T1 |
| 业务行上的分析字段 | 审批请求、`legacy_numbers` | 工时单、审批任务 | 见 K1、K2、K5 |
| 表注释含粒度 | 约 8 张 | 约 22 张（不含框架 4 张） | 见 K9 |
| 列注释 | 312 / 322 | 10 列 | 见 K8 |
| 保留期有作业 | 5 张 | 4 张（含审计归档登记），另有 1 张未定保留期 | 见 K7、T3 |
| 敏感分级 | 10 列已登记 | 约 8 列漏登 | 见 K10 |

## 3. 问题与修法

### 3.1 要改（进 1a 下一批）

| # | 问题 | 证据 | 修法 |
|---|---|---|---|
| K1 | 工时单没有「事实发生时的部门」。ADR-0026 第 9 条：业务事实的部门取事实发生时的部门；而工时单只有 `employee_id`，按员工当前部门回推，调岗后历史会漂移 | `cost_timesheet/V20261014140000__cost_timesheet_create_timesheets.sql:4`（无 `org_unit_id`） | 加 `org_unit_id bigint NOT NULL`，提交时按 `work_date` 解析写入；加索引；数据访问注册表登记为部门范围列 |
| K2 | 工时单没有单据编号，但编号规则 `TIMESHEET` 已存在（ADR-0010；V20261014135300）。EVENT_ANALYTICS_FIELDS §6 第 8 条要求 `documentNumber` 落在业务行 | 同上文件；`platform_numbering/V20261014135300__…sql:1` | 加 `timesheet_number text NOT NULL`，唯一约束 `uk_timesheets_timesheet_number`，创建时取号 |
| K3 | 「部门 → 法人」「员工 → 法人」两张生效区间表缺失。ADR-0026 第 2 项第 5、6 条把它们列为 1a 交付 | `organization_workforce` 下只有 `legal_entities`（V20261014131500） | 建两张表：生效日、失效日（`date`）、`EXCLUDE USING gist` 防止区间重叠、审计列；按日期解析的查询加索引。若 1a 已在其他分支实现，以本项作核对 |
| K4 | 法人没有生效区间。法人是 L1 / L2 的分区维度，事件 `LegalEntityRegistered / Activated / Deactivated / Renamed` 需要生效日（psa-coordinator 已转 1a 的核对结论） | `V20261014131500__…legal_entities.sql:1` | 加 `effective_from date NOT NULL`、`effective_to date`；更名与停用保留历史：建 `legal_entity_versions`（只追加），或在本表外另建历史表 |
| K5 | 审批任务没有认领时刻与办理时刻：状态改为 `CLAIMED` / `COMPLETED` 时只更新 `updated_at`，审批时效（ADR-0025 第 3 条过程事实）无法统计 | `platform_approval/V20261014132200__…tasks.sql:2` | 加 `claimed_at`、`completed_at`（`timestamptz`）；状态与时刻一致的 CHECK（例如 `status = 'COMPLETED'` 时 `completed_at` 非空） |
| K6 | 同 schema 内有 6 处引用没有外键：`org_units.parent_org_unit_id`、`head_employee_id`、`regional_receivables_manager_id`、`assignments.reports_to_employee_id`；`start_rounds`、`approval_tasks` 与 `approval_requests` 之间（经 `process_key + document_key`） | `V20261014131000…snapshot.sql:25,97`；`V20261014132000…requests.sql:34`；`V20261014132200…tasks.sql:2` | 补外键。快照表因同步顺序可能暂时悬空，用 `DEFERRABLE INITIALLY DEFERRED`；审批两张表外键指向 `approval_requests(process_key, document_key)` 唯一键 |
| K7 | 用户已认可的保留期没有清理作业：`outbound_deliveries`（成功 90 天、失败与人工处理 1 年）、`sync_runs` / `sync_diffs`（2 年 / 1 年，HELD 放行后再 1 年）；`audit_entries` 在线 3 年后归档的作业未登记 | GUIDING_PRINCIPLES §6「Phase 1a 表的保留与归档」；作业清单（清点 §5）中无对应删除 | 增加两个 ShedLock 清理作业（`platform.integration.outbound.purge`、`organization.workforce.sync.purge`），保留期作为配置项；审计归档作业在数据访问注册表登记为「到期前实现」（第 3 年） |
| K8 | 10 列无注释：`work_calendar_years` 的 `id`、`version`、`created_at`、`updated_at`；`work_calendar_days` 的 `id`、`created_at`、`updated_at`；`sync_runs.version`、`legal_entities.version`、`outbound_deliveries.version` | `V20261014141000…work_calendar.sql:5,21`；`V20261014131100:3`；`V20261014131500:2`；`V20261014144000:2` | 补 `COMMENT ON COLUMN`；守护 D3 |
| K9 | 多数表注释没写粒度（「每 … 一行」）。DAMA 的数据定义要求含义、粒度、归属三项 | 例如 `timesheets`「工时单：状态机在聚合内……」，没有写「每张工时单一行」 | 每张表注释按「<含义>；粒度：每 … 一行；<要点>」改写（归属 BC 由 schema 注释给出）；守护 D3 |
| K10 | 敏感分级漏登：`employees.display_name`（姓名）、`login_name`、`wecom_user_id`、`employee_code`；`outbound_deliveries.recipients`、`skipped_recipients`（企业微信用户 ID）；`emergency_admins.account_name`；`sessions.principal_name` | `backend/architecture/data-classification.txt`（main，15 行） | 按 NAMING_STANDARD §2.5 登记（个人信息为 SENSITIVE）；扩展守护的关键词（守护 D5） |
| K11 | 编号规则改了模板就覆盖原行，没有版本历史与生效日。METHODS M3「规则调整三类」规定配置类「新增一个版本，不覆盖旧版本」，并在模块版本表中记录版本号、生效日期、批准人与决定出处 | `V20261014100100…numbering.sql:4`；`V20261014135200:9`（迁移内 UPDATE 覆盖模板） | 建 `numbering_rule_versions`（只追加：模板、重置周期、起始值、生效日、确认人、确认时间）；`numbering_rules` 指向当前版本；已发的编号可追溯到版本（事件负载的 `ruleVersion`） |
| K12 | `emergency_admins` 有多处 UPDATE（失败计数、锁定、计步），没有 `version`；DATA_DESIGN_CHECKLIST §14 第 4 项要求可变聚合带 `version` 或用带前置条件的更新 | `JdbcEmergencyAdminRepository.java:59,72,78` | 先核对这 3 处是否都是带前置条件的原子更新（例如 `last_totp_step < ?`）。是则登记为「条件更新」例外，否则加 `version` |
| K13 | 工时单没有任何索引，但有按员工、状态、日期列表与审批的查询路径（GUIDING_PRINCIPLES §7「列表只用索引覆盖的分页」） | `V20261014140000…timesheets.sql`（索引：无） | 按查询路径加 `ix_timesheets_employee_id_work_date`、`ix_timesheets_status`（部分索引），以及 K1 后的部门索引 |
| K14 | 两处把多个值拼成一个字符串（违反第一范式）：`sync_diffs.changed_fields`（逗号）、`outbound_deliveries.recipients` / `skipped_recipients`（竖线） | `V20261014131100…:56`；`V20261014133100…:1` | 改为 `text[]`，列名 `changed_field_codes`、`recipient_codes`、`skipped_recipient_codes`。命名基线中把它们改成 `_text` 的建议应改为本修法 |
| K15 | 数据访问注册表 `architecture/data-access-registry.yaml` 不存在。ADR-0026 第 5 条要求覆盖所有 schema，ADR-0027 第 4 条第 4 项也引用它 | main 与 1a 分支的 `backend/architecture/` 中都没有该文件 | 由 1a 建立；按 §4 增加数据架构字段，作为守护 D1–D10 的单一登记处 |

### 3.2 有取舍（请用户定）

| # | 问题 | 选项 | 建议 |
|---|---|---|---|
| T1 | 组织维度没有历史：组织单元、任职（部门、岗位）、汇报线都是覆盖式快照。ADR-0025 让 L1 从事件构建 SCD2，但 L1 要到 R1 才建，事件归档只留 30 天，**1a 上线到 R1 之间的组织变化历史会丢失**；同步差异只是文本，且只保留 1 年 | (a) 交易库现在就保留历史：`org_unit_versions`、`assignment_versions`，只追加、带生效区间，与快照在同一事务写入；(b) 只靠 L1，接受 R1 之前的历史缺失；(c) 延长同步差异保留期，并结构化差异内容，事后补建 | **(a)**：与 K3、K4 一起做，口径一致；「按事实发生时的组织」授权（ADR-0025 第 4 条用户决定）也依赖它 |
| T2 | 工作日历保存时删除当年日期再重建，改动过去的日期会改变已算出的审批时效（审批时限按工作日历计算） | (a) 禁止修改今天之前的日期，例外经组织数据管理员操作并审计；(b) 日历按版本保存，审批时效记下所用的日历版本；(c) 不限制 | **(a)**：简单，符合 METHODS M3「规则调整三类」（配置不追溯）；已算出的截止时刻本来就存在 `approval_tasks.due_at` 上 |
| T3 | `cost_timesheet.consumed_events`（幂等记录）不在用户已认可的保留期清单里，会无限增长 | 保留 30 天 / 90 天 / 永久 | **90 天**：大于事件重投递的窗口与事件归档的 30 天 |
| T4 | `work_packages.actual_hours` 是已审批工时之和，属于派生值（冗余） | (a) 删除，查询时汇总；(b) 保留为投影，由工时审批事件维护，并可从工时单重算，加对账 | Phase 2 定：若工作包列表需要显示实际工时且量大，选 **(b)** 并登记为投影（DATA_DESIGN_CHECKLIST §14 第 9 项）；否则 (a) |
| T5 | `employees.separated_at` 是「首次同步到离职的时间」，不是真实离职日；它是员工维度的生效日之一 | (a) 若 A8 有离职日字段，增加 `separation_date`（`date`），分析与汇报线历史以它为准；(b) 维持现状，注明误差（同步周期 5 分钟，但 A8 补录会滞后） | **(a)**，前提是 A8 能提供该字段（需 1a 核对 A8 基表，读取时登记进外部读取登记 `backend/architecture/external-read-registry.yaml`） |

## 4. 守护规格（由 1a 实现）

位置：新增 `backend/src/test/java/com/forcitis/one/DataArchitectureTest.java`，复用 `DdlNamingTest` 的「按全部迁移推算最终表结构」解析器；规则与例外集中登记在 K15 的数据访问注册表中。每张表增加以下字段：

```yaml
- table: cost_timesheet.timesheets
  scope_kind: SCOPED                   # ADR-0026 原有字段
  role: BUSINESS_DOCUMENT              # AGGREGATE_ROOT / AGGREGATE_CHILD / BUSINESS_DOCUMENT / DIMENSION_VERSIONED / SNAPSHOT / CONFIG_VERSIONED / LOG_APPEND_ONLY / QUEUE / ONE_TIME / FRAMEWORK
  aggregate: Timesheet                 # 所属聚合；同一聚合的表必须在同一 schema
  mutability: MUTABLE                  # MUTABLE / APPEND_ONLY / CONDITIONAL_UPDATE / REPLACE_SET / FRAMEWORK
  business_date_column: work_date
  document_number_column: timesheet_number
  retention: { policy: BUSINESS_RECORD, job: null }   # 或 { policy: "90d", job: platform.integration.outbound.purge }
  lineage: { source: NEW, migrated_from: [legacy.psa_time_sheet*], via: legacy_numbers }
```

| # | 守护 | 规则 | 注入测试（应失败的样例） |
|---|---|---|---|
| D1 | 可变表必须有并发控制 | `mutability: MUTABLE` 的表必须有 `version bigint NOT NULL`；`CONDITIONAL_UPDATE` 须在注册表写明条件列 | 新增可变表不带 `version` |
| D2 | 维度表必须有生效区间 | `role: DIMENSION_VERSIONED` 的表必须有 `effective_from date NOT NULL`、`effective_to date`，以及防重叠的 `EXCLUDE USING gist` | 维度表缺 `effective_to`；缺排他约束 |
| D3 | 注释完整 | 每张非框架表的注释含粒度短语（匹配「每…一行」「每…一条」）；每列有注释 | 表注释不含粒度；新增列无注释 |
| D4 | 保留期有作业 | `retention.policy` 不是 `PERMANENT` / `BUSINESS_RECORD` 时，`job` 必须对应代码中某个 `@SchedulerLock(name=…)`；GUIDING_PRINCIPLES §6 表中列出的每张表都在注册表中有一致的保留期 | 登记 90 天保留期却没有作业；锁名拼错 |
| D5 | 敏感分级 | 在 main 现有关键词之外，增加姓名、`display_name`、`login_name`、`wecom`、`recipient`、`account_name`、`principal`；命中即须登记 | 新增 `contact_name` 列未登记 |
| D6 | 同 schema 外键 | `<实体>_id`、`parent_<实体>_id`、`<角色>_employee_id` 等列，若被引用的表在同一 schema，必须有外键；例外登记理由 | 新增 `reviewer_employee_id` 不建外键 |
| D7 | 业务行的分析字段 | `role: BUSINESS_DOCUMENT` 的表必须有 `legal_entity_id NOT NULL`、`business_date_column` 所指的 `date` 列；有编号规则的单据类型必须有 `document_number_column` 且唯一 | 新单据表缺法人列；有编号规则却无编号列 |
| D8 | 血缘登记 | 每张表都有 `lineage`；`migrated_from` 非空的表必须声明经 `legacy_numbers` 或另行登记的对照方式 | 新表无 `lineage` |
| D9 | 第一范式 | 名称或注释表明「逗号 / 竖线分隔」的 `text` 列失败，应改为数组或子表；`_codes` 必须是 `text[]` | 新增「以逗号分隔」的 text 列 |
| D10 | 配置类必须版本化 | `role: CONFIG_VERSIONED` 的表必须有对应的 `*_versions` 只追加表（含 `effective_from`、确认人、确认时间），且禁止 UPDATE 版本表 | 编号规则版本表缺 `effective_from` |

已有守护（不重复建设）：金额与币种成对、`_at` 必须是 `timestamptz`、状态列必须有 `CHECK`、约束命名（`DdlNamingTest`）；事实表只追加（G34）；审计表触发器测试。

## 5. Flowable `ACT_*` 引擎表

- 归属：`platform_approval` schema；由引擎创建与升级，不走 Flyway。生产 `flowable.database-schema-update=false`，升级按专门手册启动一次。
- 检查口径：不按本表的命名、注释、范式检查（框架表），只登记在数据访问注册表（ADR-0026 第 5 条已要求）。
- 保留：老审批只读归档（ADR-0011）；新系统的引擎历史表（`ACT_HI_*`）**尚无保留期**，建议在审批数据量上来之前定。审批结论已落在 `approval_requests` / `approval_tasks` 与审计中，引擎历史可按 1–3 年清理。这一项列入下一轮保留期确认。
- 分析：审批时效从 `approval_tasks`（K5 补齐后）取，不读 `ACT_*`。

## 6. 数据架构检查清单

已并入 DATA_DESIGN_CHECKLIST §14.1，作为每波复审项；可自动化的对应 §4 的守护。
