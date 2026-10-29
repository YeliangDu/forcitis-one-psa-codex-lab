# ADR-0068 证据附件

状态：历史记录｜2026-10-21｜负责：one-architecture

本附件列出 ADR-0068（ADR-0055 补篇一，提议）各条的证据锚点、B 类代价、DDL 草案、检索记录与守护明细。原第 7 至 14 条的证据见 `ADR-0069-证据附件.md`。

- **基线**：origin/main `d7b805d8`。与 `11fcc11f` 相比，`backend/` 只有 #489 的改名（identity 基线改列名，行号不变；`git diff --stat` 共 4 个文件）。
- **路径缩写**：
  - `M/` = `backend/src/main/java/com/forcitis/one/`
  - `T/` = `backend/src/test/java/com/forcitis/one/`
  - `R/` = `backend/src/main/resources/`
  - `A/` = `backend/architecture/`
  - `D/` = `docs/`
- **基线 SQL**：
  - WF：`R/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql`
  - ID：`R/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql`
  - AU：`R/db/migration/platform_audit/V20261014134000__platform_audit_baseline.sql`
- **#491 评论**：owner 分档为评论 6309671304；B 类裁定稿为评论 6310172649（本地草稿文件名 `B-RULING-POST.md`，不在仓库中；裁定稿的代价与锚点已摘入下文「B 类代价」）。

## 第 1 条　人事命令

- **ADR 依据**：
  - ADR-0055:56：入职、转正、调岗、停薪留职、离职由人事命令维护。
  - ADR-0055:62：人事业务生效日；允许提前登记、到日生效、补登落真实日期。
  - ADR-0055:70：`revocation_separation_command_id`。
  - ADR-0055:77：去重键。
  - ADR-0058 决定 8：之后新建的有状态聚合一律记转换。
- **现状**：
  - `M/organization/workforce/infrastructure/persistence/JdbcOrgSnapshotRepository.java:117-152` 整表写入；
  - `INVENTORY.md` §7：employees 只由同步与登录补建写入。
- **ID 与事件**：`M/kernel/IdGenerator.java:6-8`；`M/kernel/event/EventEnvelope.java`。
- **被取代的事件**：`M/organization/workforce/events/OrgSnapshotChanged.java:10-15`、`EmployeeRegistered.java:7-8`。
- **版本与补登**：
  - WF:319-390 版本表左闭右开（WF:242）；
  - W2 设计第 3 版第 2 节「补登与更正怎么做」（作废加追加，任意区间可补登）；
  - W2 第 10 节第 3、5 项（解除补登限制、请求加 `CORRECTION`）；
  - one-data 跟进意见 1–3。
- **到日作业写法**：`D/CROSS_CUTTING_MATRIX.md:92`（CC-T09，`@SchedulerLock` 用于全部 1a 作业）、ADR-0002:22-23。原稿「2026-10-20 统一写法」没有出处，已改引此处。
- **转正与阶段**：
  - one-data 审查意见 7（转正没有落点）；
  - W2 §3.2 建议另设 `employment_phase`；
  - `D/glossary/terms.yaml` 的 `regularized`（`contract.agreement` 的领域事件「转正」，含义为授权复核关闭）。workforce 的「转正」与它同名异义，须互列。
- **在职状态用语**：terms.yaml `employment_status`（在职状态，取值 `EMPLOYED`、`SEPARATED`）。
- **行业做法**：
  - SAP HCM Personnel Actions、Workday Business Process：雇佣、试用转正、调动、离职、再入职、更正都是同一类动作记录，按种类区分、按生效日执行；
  - 试用期阶段与雇佣状态分列，是常见做法。

## 第 2 条　待放行离职

- **ADR 依据**：
  - ADR-0055:68-69：载体、单条放行、0 名与 1 名、留任排除待放行；
  - ADR-0028 决定 4（文件 :24-27）：放行审计内容（操作人、会话模式、原因、剩余可用超管数），告警其他超管、审计员持有人与运维；
  - ADR-0039:26。
- **现表会被级联删除**：WF:463-480，`ON DELETE CASCADE` 在 WF:471。
- **放行权限**：ID:574-578。
- **判定端口**：
  - 端口：`M/kernel/SeparationReview.java:10-28`；
  - 实现：`M/platform/identity/application/AdministratorSeparationReview.java:30-58`，其中 :56 的 `retained` 与 :66-73 的 `usableHolders` 都不排除待放行中的人（opus M1）；
  - 调用点：`ApplySourceSnapshot.java:50`、`ReleaseSyncRunHolds.java:35`。
- **事务级咨询锁先例**：
  - `M/cost/timesheet/infrastructure/persistence/JdbcTimesheetRepository.java:67`；
  - `M/platform/approval/infrastructure/persistence/JdbcApprovalRequestRepository.java:65`；
  - AU:144；
  - `JdbcOrgSnapshotRepository.java:55`（同步写入锁，随 D2 删除）。
- **双人参数与分区预检**：`M/platform/identity/application/OperationsCommands.java:23、34-35、80-86`。
- **0 名超管的运维命令在 main 上尚不存在**：只有 `ReleaseSyncRunHolds.java:38-39` 的报错文字提到它。
- **只能关闭一次的触发器**：ID:497-501（`refuse_history_rewrite` 参数化放行列）。
- **审计**：`D/audit/MUST_AUDIT_ACTIONS.yaml:47-50`。
- **B、C 之间的空窗**（opus M11）：
  - identity 现在异步订阅（`HandleOrgSnapshotChange.java:30-31、65`）；
  - `sync_runs` 没有拒删触发器（WF:129 起）；
  - 只有测试删过它（`T/.../EmergencyLoginEmploymentIT.java:131、208`）。

## 第 3、4 条　B 类代价（摘自 #491 评论 6310172649）

### 第 3 条　停薪留职

- **与 #491 裁定稿的差异**：裁定稿写的是给在职状态增加取值 `ON_LEAVE`；本篇改为独立状态列（W2 §3.2 建议），行为不变。
- **代价**：
  - 新增状态列与两种命令、两种事件，F-1 新增一种来源；
  - 下列判断在职的地方要逐处判定是否受停薪留职影响：`LoginAdmission.java:56`、`SessionPrincipals.java:55`、`AssigneeResolver.java:198`、`SubmitTimesheet.java:100`、`AdministratorSeparationReview.java:51-58`。
- **派生范围**：`OWN_ORG_UNIT` 等相对范围由任职派生（ADR-0034:272 一节）。推荐期间照常，标「待批」。
- **受影响的 PR**：A、B、C、F-1、审批办理人解析（卡由 one-coordinator 指派）、cost.timesheet、W2。
- **备选**：延后。触发条件为迁入取证发现在册的停薪留职人员，或人事提出需求；负责人 one-1a-par。

### 第 4 条　组织单元与岗位命令

- **锚点**：
  - 上级：WF:31；
  - 负责人两列：WF:34-35；
  - `OrgDirectory.java:19、22`（`findOrgUnitHead`、`findRegionalReceivablesManager`）；
  - 岗位：WF:5-16。
- **代价**：
  - 合并 = 撤销 A，并把 A 的在职员工逐人调岗到 B。合并跨多个员工聚合，事务方案由 one-data 在 B2 开工前签发；
  - 撤销时部门内仍有在职员工的，拒绝并告警；
  - 版本按 `DECLARED`。
- **岗位命令归属**（one-data 意见 8）：与组织单元命令同表 `org_structure_commands`（暂名），`target_kind` 区分；不并入人事命令表，因为岗位不属员工聚合。
- **受影响的 PR**：A、B（可拆出 B2）、F-1、G、W2。

## 第 5 条　F-1

- **F-1 卡**：`D/phase1a/cards/F-1.md`：
  - :134 去重口径；
  - :145 `expiry_held_sync_run_id`；
  - :163 监听器；
  - :462、:478、:485 三列；
  - :520 CHECK；
  - :559 唯一索引；
  - 正文第 23 行来源②。
- **one-data 判定**：
  - 人事命令一侧三列通过，跨 schema 只存 ID；
  - 组织命令一侧的两列暂不签（B7），第 4 条批准后再无损追加（第 4 条已批，2026-10-21 one-data 第三轮确认现可追加，列见「组织结构命令」节）。
- **ADR 依据**：ADR-0055:75-77；ADR-0044 第 2 项（:59-62）；ADR-0044 第 3 项（:72）。

## 第 6 条　OBSERVED

- ADR-0034:272 原句；ADR-0055:62。
- §14.1 D17 第 2 条。
- `A/data-access-registry.yaml:276、286`：`DECLARED` 的写法先例。
- W2 第 3 版第 2 节「读取」：用 `created_at` 与 `superseded_at` 回放「系统当时认为」；第 9 节：`org_units` 收窄的风险登记。

## 第 15 条　W2 接口

W2 设计第 3 版 §0、§2、§3.2–3.4、§3.6、§8、§10。第 10 节五项的对应关系见 ADR-0068 第 15 条第 6 项。

## DDL 草案（供 one-data 签发；名称均为暂名）

```sql
CREATE TABLE organization_workforce.personnel_commands (
    personnel_command_id          uuid        NOT NULL,
    employee_id                   uuid        NOT NULL,
    command_kind                  text        NOT NULL,
    effective_date                date        NOT NULL,
    status                        text        NOT NULL,
    target_org_unit_id            uuid,
    target_position_id            uuid,
    target_reports_to_employee_id uuid,
    target_is_fee_earner          boolean,
    target_employment_phase       text,
    target_is_on_unpaid_leave     boolean,
    org_structure_command_id      uuid,
    applied_at                    timestamptz,
    withdrawn_at                  timestamptz,
    withdrawn_by                  uuid,
    withdrawal_reason             text,
    created_at                    timestamptz NOT NULL,
    created_by                    uuid        NOT NULL,
    updated_at                    timestamptz NOT NULL,
    version                       bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_personnel_commands PRIMARY KEY (personnel_command_id),
    CONSTRAINT uk_personnel_commands_id_employee UNIQUE (personnel_command_id, employee_id),
    CONSTRAINT fk_personnel_commands_employee_id FOREIGN KEY (employee_id)
        REFERENCES organization_workforce.employees (id) ON DELETE RESTRICT,
    CONSTRAINT fk_personnel_commands_target_org_unit_id FOREIGN KEY (target_org_unit_id)
        REFERENCES organization_workforce.org_units (id) ON DELETE RESTRICT,
    CONSTRAINT fk_personnel_commands_target_position_id FOREIGN KEY (target_position_id)
        REFERENCES organization_workforce.positions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_personnel_commands_target_reports_to FOREIGN KEY (target_reports_to_employee_id)
        REFERENCES organization_workforce.employees (id) ON DELETE RESTRICT,
    CONSTRAINT fk_personnel_commands_org_structure_command_id FOREIGN KEY (org_structure_command_id)
        REFERENCES organization_workforce.org_structure_commands (org_structure_command_id) ON DELETE RESTRICT,
    CONSTRAINT ck_personnel_commands_command_kind
        CHECK (command_kind IN ('HIRE', 'REGULARIZE', 'TRANSFER', 'SEPARATE', 'REINSTATE', 'CORRECTION',
                                'UNPAID_LEAVE_START', 'UNPAID_LEAVE_END')),
    CONSTRAINT ck_personnel_commands_status
        CHECK (status IN ('PENDING', 'HELD', 'EFFECTIVE', 'WITHDRAWN')),
    CONSTRAINT ck_personnel_commands_held_kind CHECK (status <> 'HELD' OR command_kind = 'SEPARATE'),
    CONSTRAINT ck_personnel_commands_correction_immediate
        CHECK (command_kind <> 'CORRECTION' OR status = 'EFFECTIVE'),
    CONSTRAINT ck_personnel_commands_applied CHECK ((status = 'EFFECTIVE') = (applied_at IS NOT NULL)),
    CONSTRAINT ck_personnel_commands_withdrawn CHECK (
        (status = 'WITHDRAWN') = (withdrawn_at IS NOT NULL)
        AND (withdrawn_at IS NULL) = (withdrawn_by IS NULL)
        AND (withdrawn_at IS NULL) = (withdrawal_reason IS NULL)),
    CONSTRAINT ck_personnel_commands_withdrawal_reason CHECK (withdrawal_reason IS NULL
        OR (char_length(withdrawal_reason) BETWEEN 1 AND 500 AND btrim(withdrawal_reason) <> '')),
    CONSTRAINT ck_personnel_commands_effective_date_finite CHECK (isfinite(effective_date)),
    CONSTRAINT ck_personnel_commands_employment_phase
        CHECK (target_employment_phase IS NULL OR target_employment_phase IN ('PROBATION', 'REGULAR')),
    CONSTRAINT ck_personnel_commands_reports_to_not_self
        CHECK (target_reports_to_employee_id IS DISTINCT FROM employee_id),
    CONSTRAINT ck_personnel_commands_hire_columns CHECK (command_kind <> 'HIRE' OR (
        target_org_unit_id IS NOT NULL AND target_position_id IS NOT NULL
        AND target_is_fee_earner IS NOT NULL AND target_employment_phase IS NOT NULL)),
    CONSTRAINT ck_personnel_commands_reinstate_columns CHECK (command_kind <> 'REINSTATE' OR (
        target_org_unit_id IS NOT NULL AND target_position_id IS NOT NULL
        AND target_is_fee_earner IS NOT NULL AND target_employment_phase IS NOT NULL)),
    CONSTRAINT ck_personnel_commands_transfer_columns CHECK (command_kind <> 'TRANSFER' OR (
        target_employment_phase IS NULL
        AND num_nonnulls(target_org_unit_id, target_position_id,
                         target_reports_to_employee_id, target_is_fee_earner) >= 1)),
    CONSTRAINT ck_personnel_commands_contentless_columns
        CHECK (command_kind NOT IN ('REGULARIZE', 'SEPARATE') OR num_nonnulls(target_org_unit_id,
            target_position_id, target_reports_to_employee_id, target_is_fee_earner, target_employment_phase) = 0),
    CONSTRAINT ck_personnel_commands_unpaid_leave CHECK (
        (command_kind = 'UNPAID_LEAVE_START') = (target_is_on_unpaid_leave IS TRUE)
        AND (command_kind = 'UNPAID_LEAVE_END') = (target_is_on_unpaid_leave IS FALSE)),
    CONSTRAINT ck_personnel_commands_unpaid_leave_columns CHECK (
        command_kind NOT IN ('UNPAID_LEAVE_START', 'UNPAID_LEAVE_END') OR num_nonnulls(target_org_unit_id,
            target_position_id, target_reports_to_employee_id, target_is_fee_earner, target_employment_phase) = 0),
    CONSTRAINT ck_personnel_commands_org_structure_command
        CHECK (org_structure_command_id IS NULL OR command_kind = 'TRANSFER')
);
```

- **更正的内容**：更正行只记命令本身（原因由审计记录），更正后的事实写进 W2 的版本行，由版本行的 `superseding_command_id` 与 `change_command_id` 引用本表。因此更正行的内容列可空，不另加 CHECK。
- **停薪留职**（第 3 条已批，H3）：开始 `UNPAID_LEAVE_START` 时 `target_is_on_unpaid_leave` 恒为真，结束 `UNPAID_LEAVE_END` 恒为假，其他种类为空；对应 W2 第 4 版 `employee_versions.is_on_unpaid_leave`（非在职版本恒为假，由 W2 的 `ck` 保证）。离职命令生效时若此人仍在停薪留职，离职版本按 W2 写假，不另要结束命令。
- **合并引用**（第 4 条已批，H4）：`org_structure_command_id` 只用于合并产生的逐人调岗；人工调岗为空。

```sql
CREATE UNIQUE INDEX uk_personnel_commands_open_per_day
    ON organization_workforce.personnel_commands (employee_id, effective_date)
    WHERE status IN ('PENDING', 'HELD');
CREATE INDEX ix_personnel_commands_due
    ON organization_workforce.personnel_commands (effective_date) WHERE status = 'PENDING';
CREATE INDEX ix_personnel_commands_employee_id
    ON organization_workforce.personnel_commands (employee_id, effective_date);
CREATE UNIQUE INDEX uk_personnel_commands_merge_employee
    ON organization_workforce.personnel_commands (org_structure_command_id, employee_id)
    WHERE org_structure_command_id IS NOT NULL;  -- 合并分批重跑时同一人不重复调岗
CREATE INDEX ix_personnel_commands_target_org_unit_id
    ON organization_workforce.personnel_commands (target_org_unit_id) WHERE target_org_unit_id IS NOT NULL;
CREATE INDEX ix_personnel_commands_target_position_id
    ON organization_workforce.personnel_commands (target_position_id) WHERE target_position_id IS NOT NULL;
CREATE INDEX ix_personnel_commands_target_reports_to
    ON organization_workforce.personnel_commands (target_reports_to_employee_id)
    WHERE target_reports_to_employee_id IS NOT NULL;
-- org_structure_command_id 由 uk_personnel_commands_merge_employee 前导列覆盖
```

- **触发器**：
  - `tg_personnel_commands_append_only`：BEFORE UPDATE OR DELETE。
    - UPDATE：只放行 `status`（按 PENDING → EFFECTIVE、WITHDRAWN、HELD 以及 HELD → EFFECTIVE、WITHDRAWN 前进）、`applied_at`、`withdrawn_*`（只能由空变非空一次）、`updated_at` 与 `version`，其余列一律拒绝。
    - DELETE：只在 `current_user` 为表属主时放行，即经 `SECURITY DEFINER` 函数 `organization_workforce.purge_employee_history`（W2 §8）执行的清除。
  - `tg_personnel_commands_append_only_truncate`：拒绝 TRUNCATE。
- **权限**：运行角色有 SELECT、INSERT、UPDATE，回收 DELETE、TRUNCATE（`one_db_roles_schemas.sql` §4b）。
- **清除次序**：先删 `separation_holds`、各版本行，再删命令行（被 RESTRICT 外键约束）；组织结构命令在引用它的调岗命令清除后才可清除。
- **注释**（COMMENT ON）：
  - 表：「人事命令：入职、转正、调岗、离职、复职、更正等人事异动的第一手记录，只追加；生效日为人事业务生效日（ADR-0055 决定 2、ADR-0068 第 1 条）。粒度：每条人事命令一行」；
  - 每列各写一句含义。
- **跨 schema 列**：`created_by`、`withdrawn_by` 是账号 ID，只存不建外键。
- **建表次序**：同一迁移内先建 `org_structure_commands`，再建 `personnel_commands`（`org_structure_command_id` 外键）。
- **`withdrawal_reason`**：列入 C4 允许写入清单，标 `PI`（自由文本可能写到人）。
- **登记**：
  - 注册表：`AGGREGATE_ROOT` 之内的实体，归 Employee 聚合，生效依据 `DECLARED`，`APPEND_ONLY`；保留期与员工记录相同，离职满 10 年清除；不含个人信息列。
  - 分级：`INTERNAL`。

```sql
CREATE TABLE organization_workforce.separation_holds (
    separation_hold_id   uuid        NOT NULL,
    personnel_command_id uuid        NOT NULL,
    employee_id          uuid        NOT NULL,
    status               text        NOT NULL,
    released_at          timestamptz,
    released_by          uuid,
    release_reason       text,
    release_channel      text,
    created_at           timestamptz NOT NULL,
    updated_at           timestamptz NOT NULL,
    version              bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_separation_holds PRIMARY KEY (separation_hold_id),
    CONSTRAINT uk_separation_holds_personnel_command_id UNIQUE (personnel_command_id),
    CONSTRAINT fk_separation_holds_command FOREIGN KEY (personnel_command_id, employee_id)
        REFERENCES organization_workforce.personnel_commands (personnel_command_id, employee_id) ON DELETE RESTRICT,
    CONSTRAINT ck_separation_holds_status CHECK (status IN ('HELD', 'RELEASED', 'WITHDRAWN')),
    CONSTRAINT ck_separation_holds_released CHECK ((status = 'RELEASED') = (released_at IS NOT NULL)),
    CONSTRAINT ck_separation_holds_release_columns CHECK (
        (released_at IS NULL) = (release_reason IS NULL) AND (released_at IS NULL) = (release_channel IS NULL)),
    CONSTRAINT ck_separation_holds_release_channel
        CHECK (release_channel IS NULL OR release_channel IN ('INTERFACE', 'OPERATIONS_COMMAND')),
    CONSTRAINT ck_separation_holds_released_by CHECK (
        (release_channel IS NOT DISTINCT FROM 'INTERFACE') = (released_by IS NOT NULL)),
    CONSTRAINT ck_separation_holds_release_reason CHECK (release_reason IS NULL
        OR (char_length(release_reason) BETWEEN 1 AND 500 AND btrim(release_reason) <> ''))
);
CREATE INDEX ix_separation_holds_held ON organization_workforce.separation_holds (employee_id) WHERE status = 'HELD';
```

- **触发器**：
  - 放行列只能由空变非空一次；`status` 只能从 HELD 改为 RELEASED 或 WITHDRAWN；
  - DELETE 与 TRUNCATE 同命令表处理。
- **`release_reason`**：列入 C4 允许写入清单，按 `uphold_reason` 先例标 `PI`。
- **状态转换**：记入 workforce 的 `state_transitions`（ADR-0058 决定 8），形状随 ADR-0058 模板。

### 组织结构命令（第 4 条，H4；one-data 第三轮要求）

```sql
CREATE TABLE organization_workforce.org_structure_commands (
    org_structure_command_id    uuid        NOT NULL,
    command_kind                text        NOT NULL,
    target_kind                 text        NOT NULL,
    target_org_unit_id          uuid,
    target_position_id          uuid,
    effective_date              date        NOT NULL,
    status                      text        NOT NULL,
    target_code                 text,
    target_name                 text,
    target_short_name           text,
    target_region_kind          text,
    target_is_delivery_unit     boolean,
    target_parent_org_unit_id   uuid,
    target_head_employee_id     uuid,
    target_receivables_manager_employee_id uuid,
    merge_into_org_unit_id      uuid,
    affected_employee_count     integer,
    transferred_employee_count  integer,
    applied_at                  timestamptz,
    withdrawn_at                timestamptz,
    withdrawn_by                uuid,
    withdrawal_reason           text,
    created_at                  timestamptz NOT NULL,
    created_by                  uuid        NOT NULL,
    updated_at                  timestamptz NOT NULL,
    version                     bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_org_structure_commands PRIMARY KEY (org_structure_command_id),
    CONSTRAINT fk_org_structure_commands_target_org_unit_id FOREIGN KEY (target_org_unit_id)
        REFERENCES organization_workforce.org_units (id) ON DELETE RESTRICT,
    CONSTRAINT fk_org_structure_commands_target_position_id FOREIGN KEY (target_position_id)
        REFERENCES organization_workforce.positions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_org_structure_commands_parent FOREIGN KEY (target_parent_org_unit_id)
        REFERENCES organization_workforce.org_units (id) ON DELETE RESTRICT,
    CONSTRAINT fk_org_structure_commands_head FOREIGN KEY (target_head_employee_id)
        REFERENCES organization_workforce.employees (id) ON DELETE RESTRICT,
    CONSTRAINT fk_org_structure_commands_receivables_manager FOREIGN KEY (target_receivables_manager_employee_id)
        REFERENCES organization_workforce.employees (id) ON DELETE RESTRICT,
    CONSTRAINT fk_org_structure_commands_merge_into FOREIGN KEY (merge_into_org_unit_id)
        REFERENCES organization_workforce.org_units (id) ON DELETE RESTRICT,
    CONSTRAINT ck_org_structure_commands_target_kind CHECK (target_kind IN ('ORG_UNIT', 'POSITION')),
    CONSTRAINT ck_org_structure_commands_target CHECK (
        num_nonnulls(target_org_unit_id, target_position_id) = 1
        AND (target_kind = 'ORG_UNIT') = (target_org_unit_id IS NOT NULL)),
    CONSTRAINT ck_org_structure_commands_command_kind CHECK (
        (target_kind = 'ORG_UNIT' AND command_kind IN ('CREATE', 'DISSOLVE', 'RENAME', 'MERGE', 'REPARENT',
                                                       'ASSIGN_HEAD', 'ASSIGN_RECEIVABLES_MANAGER'))
        OR (target_kind = 'POSITION' AND command_kind IN ('CREATE', 'DISABLE'))),
    CONSTRAINT ck_org_structure_commands_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'EFFECTIVE', 'WITHDRAWN')),
    CONSTRAINT ck_org_structure_commands_in_progress_kind CHECK (status <> 'IN_PROGRESS' OR command_kind = 'MERGE'),
    CONSTRAINT ck_org_structure_commands_applied CHECK ((status = 'EFFECTIVE') = (applied_at IS NOT NULL)),
    CONSTRAINT ck_org_structure_commands_withdrawn CHECK (
        (status = 'WITHDRAWN') = (withdrawn_at IS NOT NULL)
        AND (withdrawn_at IS NULL) = (withdrawn_by IS NULL)
        AND (withdrawn_at IS NULL) = (withdrawal_reason IS NULL)),
    CONSTRAINT ck_org_structure_commands_withdrawal_reason CHECK (withdrawal_reason IS NULL
        OR (char_length(withdrawal_reason) BETWEEN 1 AND 500 AND btrim(withdrawal_reason) <> '')),
    CONSTRAINT ck_org_structure_commands_effective_date_finite CHECK (isfinite(effective_date)),
    CONSTRAINT ck_org_structure_commands_name CHECK (
        (target_name IS NULL OR (char_length(target_name) BETWEEN 1 AND 500 AND btrim(target_name) <> ''))
        AND (target_short_name IS NULL
             OR (char_length(target_short_name) BETWEEN 1 AND 50 AND btrim(target_short_name) <> ''))),
    CONSTRAINT ck_org_structure_commands_code
        CHECK (target_code IS NULL OR (char_length(target_code) BETWEEN 1 AND 500 AND btrim(target_code) <> '')),
    CONSTRAINT ck_org_structure_commands_region_kind
        CHECK (target_region_kind IS NULL OR target_region_kind IN ('HEADQUARTERS', 'REGIONAL')),
    CONSTRAINT ck_org_structure_commands_create_only_columns CHECK (command_kind = 'CREATE'
        OR num_nonnulls(target_code, target_region_kind, target_is_delivery_unit) = 0),
    CONSTRAINT ck_org_structure_commands_unit_attributes CHECK (command_kind <> 'CREATE' OR (
        (target_kind = 'ORG_UNIT') = (target_is_delivery_unit IS NOT NULL)
        AND (target_kind = 'ORG_UNIT' OR target_region_kind IS NULL))),
    CONSTRAINT ck_org_structure_commands_create_columns CHECK (command_kind <> 'CREATE'
        OR (target_name IS NOT NULL AND target_head_employee_id IS NULL
            AND target_receivables_manager_employee_id IS NULL AND merge_into_org_unit_id IS NULL
            AND (target_kind = 'ORG_UNIT' OR (target_short_name IS NULL AND target_parent_org_unit_id IS NULL)))),
    CONSTRAINT ck_org_structure_commands_rename_columns CHECK (command_kind <> 'RENAME' OR (
        num_nonnulls(target_name, target_short_name) >= 1
        AND num_nonnulls(target_parent_org_unit_id, target_head_employee_id,
                         target_receivables_manager_employee_id, merge_into_org_unit_id) = 0)),
    CONSTRAINT ck_org_structure_commands_reparent_columns CHECK (command_kind <> 'REPARENT' OR (
        target_parent_org_unit_id IS NOT NULL
        AND num_nonnulls(target_name, target_short_name, target_head_employee_id,
                         target_receivables_manager_employee_id, merge_into_org_unit_id) = 0)),
    CONSTRAINT ck_org_structure_commands_head_columns CHECK (command_kind <> 'ASSIGN_HEAD' OR (
        target_head_employee_id IS NOT NULL
        AND num_nonnulls(target_name, target_short_name, target_parent_org_unit_id,
                         target_receivables_manager_employee_id, merge_into_org_unit_id) = 0)),
    CONSTRAINT ck_org_structure_commands_receivables_columns CHECK (command_kind <> 'ASSIGN_RECEIVABLES_MANAGER' OR (
        target_receivables_manager_employee_id IS NOT NULL
        AND num_nonnulls(target_name, target_short_name, target_parent_org_unit_id,
                         target_head_employee_id, merge_into_org_unit_id) = 0)),
    CONSTRAINT ck_org_structure_commands_contentless_columns CHECK (command_kind NOT IN ('DISSOLVE', 'DISABLE')
        OR num_nonnulls(target_name, target_short_name, target_parent_org_unit_id, target_head_employee_id,
                        target_receivables_manager_employee_id, merge_into_org_unit_id) = 0),
    CONSTRAINT ck_org_structure_commands_merge_columns CHECK ((command_kind = 'MERGE') = (merge_into_org_unit_id IS NOT NULL)
        AND (command_kind <> 'MERGE' OR num_nonnulls(target_name, target_short_name, target_parent_org_unit_id,
                                                     target_head_employee_id, target_receivables_manager_employee_id) = 0)),
    CONSTRAINT ck_org_structure_commands_merge_not_self CHECK (merge_into_org_unit_id IS DISTINCT FROM target_org_unit_id),
    CONSTRAINT ck_org_structure_commands_parent_not_self CHECK (target_parent_org_unit_id IS DISTINCT FROM target_org_unit_id),
    CONSTRAINT ck_org_structure_commands_merge_progress CHECK (
        (command_kind = 'MERGE') = (affected_employee_count IS NOT NULL)
        AND (affected_employee_count IS NULL) = (transferred_employee_count IS NULL)
        AND (affected_employee_count IS NULL
             OR (0 <= transferred_employee_count AND transferred_employee_count <= affected_employee_count))
        AND (status <> 'EFFECTIVE' OR transferred_employee_count IS NOT DISTINCT FROM affected_employee_count))
);
CREATE UNIQUE INDEX uk_org_structure_commands_open_org_unit_per_day
    ON organization_workforce.org_structure_commands (target_org_unit_id, effective_date)
    WHERE status IN ('PENDING', 'IN_PROGRESS') AND target_org_unit_id IS NOT NULL;
CREATE UNIQUE INDEX uk_org_structure_commands_open_position_per_day
    ON organization_workforce.org_structure_commands (target_position_id, effective_date)
    WHERE status = 'PENDING' AND target_position_id IS NOT NULL;
CREATE UNIQUE INDEX uk_org_structure_commands_merge_in_progress
    ON organization_workforce.org_structure_commands (target_org_unit_id) WHERE status = 'IN_PROGRESS';
CREATE INDEX ix_org_structure_commands_due
    ON organization_workforce.org_structure_commands (effective_date) WHERE status = 'PENDING';
CREATE INDEX ix_org_structure_commands_target_org_unit_id
    ON organization_workforce.org_structure_commands (target_org_unit_id) WHERE target_org_unit_id IS NOT NULL;
CREATE INDEX ix_org_structure_commands_target_position_id
    ON organization_workforce.org_structure_commands (target_position_id) WHERE target_position_id IS NOT NULL;
CREATE INDEX ix_org_structure_commands_parent
    ON organization_workforce.org_structure_commands (target_parent_org_unit_id) WHERE target_parent_org_unit_id IS NOT NULL;
CREATE INDEX ix_org_structure_commands_head
    ON organization_workforce.org_structure_commands (target_head_employee_id) WHERE target_head_employee_id IS NOT NULL;
CREATE INDEX ix_org_structure_commands_receivables_manager
    ON organization_workforce.org_structure_commands (target_receivables_manager_employee_id)
    WHERE target_receivables_manager_employee_id IS NOT NULL;
CREATE INDEX ix_org_structure_commands_merge_into
    ON organization_workforce.org_structure_commands (merge_into_org_unit_id) WHERE merge_into_org_unit_id IS NOT NULL;
```

- **列说明**：
  - 新建命令的目标行（`org_units` 或 `positions`）与命令在同一事务内先插入，故目标列总是非空并有外键；新建组织单元的上级可空（根）。
  - 合并：`target_org_unit_id` 为被并入方（合并完成即撤销），`merge_into_org_unit_id` 为承接方。执行开始转 `IN_PROGRESS` 并写 `affected_employee_count`（以执行时点的在职名单为准，预演结果不落库）；每人一个事务生成一条调岗命令（`personnel_commands.org_structure_command_id` 引用本行）并把 `transferred_employee_count` 加一；全部转完后同一事务写撤销版本、转 `EFFECTIVE`。中断后重跑按 `uk_personnel_commands_merge_employee` 跳过已转的人。
  - 撤销（`DISSOLVE`）时目标部门仍有在职员工的，应用层拒绝并告警（第 4 条代价）；停用岗位同理。
  - 组织结构命令不设更正种类：录错的未生效命令撤回重录，已生效的以新命令改回；版本表因此不需要 `superseding_org_structure_command_id`。
  - 编码：新建命令带 `target_code`（岗位编码全公司唯一、可空，组织单元编码可空），发号后不变，其他种类该列为空；组织单元的总部/区域 `target_region_kind`（可空，同 `org_unit_versions.region_kind`）与是否交付单元 `target_is_delivery_unit`（组织单元新建必填）只随新建写入。
  - 跨 schema 列：`created_by`、`withdrawn_by` 是账号 ID，只存不建外键。
  - `withdrawal_reason` 列入 C4 允许写入清单，标 `PI`；进度两列是计数，不含个人信息。
- **版本表（交 W2 第 4 版落列）**：`org_unit_versions`、`position_versions` 加 `change_org_structure_command_id uuid`（真外键，`ON DELETE RESTRICT`）；员工、任职、员工法人归属版本表仍用 `change_command_id`。凡同时带两列的版本表，`change_channel_kind = 'COMMAND'` 的行 `num_nonnulls(change_command_id, change_org_structure_command_id) = 1`，非 `COMMAND` 行两列皆空；组织单元、岗位版本表的 `COMMAND` 行只许前者为空、后者非空。
- **触发器**：
  - `tg_org_structure_commands_append_only`：BEFORE UPDATE OR DELETE。UPDATE 只放行 `status`（PENDING → IN_PROGRESS、EFFECTIVE、WITHDRAWN；IN_PROGRESS → EFFECTIVE）、`applied_at` 与 `withdrawn_*`（由空变非空一次）、`affected_employee_count`（由空变非空一次，与转 IN_PROGRESS 同一语句）、`transferred_employee_count`（只增不减）、`updated_at`、`version`；其余列一律拒绝。IN_PROGRESS 不可撤回：开始转人后只能完成。
  - DELETE 只在 `current_user` 为表属主时放行，即经 `SECURITY DEFINER` 清除函数（暂名 `organization_workforce.purge_org_structure_history`，随组织记录保留期，由 W2 第 8 节一并定）；`tg_org_structure_commands_append_only_truncate` 拒绝 TRUNCATE。
- **权限**：运行角色有 SELECT、INSERT、UPDATE，回收 DELETE、TRUNCATE（同 `personnel_commands`）。
- **状态转换**：记入 workforce 的 `state_transitions`（ADR-0058 决定 8）。
- **注释**（COMMENT ON）：表「组织结构命令：组织单元新建、撤销、改名、合并、调整上级、指定负责人与区域应收负责人，以及岗位新建、停用的第一手记录，只追加（ADR-0068 第 4 条）。粒度：每条命令一行」；每列各写一句含义。
- **登记**：注册表为组织单元聚合内的实体（岗位命令同表，按 `target_kind` 归属），生效依据 `DECLARED`，`APPEND_ONLY`；保留期随组织记录；无个人信息列（负责人列是员工 ID，不含姓名）。分级 `INTERNAL`。
- **F-1**：组织命令一侧 `trigger_org_structure_command_id`、`closure_org_structure_command_id` 指本表（跨 schema 只存 ID），第 4 条已批，现在可追加。
- **one-data 第四轮签发**（`claude/one-data-handoff-1`@cb485497 `reviews/ADR-0068.md`）：`version` 改 `bigint`（三张表）、新建补编码、名称上限 500、撤回原因标 `PI`、进度列无个人信息、外键部分索引写出，均已改入上文。

#### one-data 第四轮三问的答复

1. **`region_kind` 与 `is_delivery_unit`**：新建组织单元的命令带这两列（`is_delivery_unit` 必填），属 ADR-0068 第 4 条「新建」的内容，已加入上文。建成后修改这两列需要新命令种类（暂名「调整属性」`SET_ATTRIBUTES`），超出第 4 条已批种类，列为后续卡甲（编号由调度分配）（报用户批准种类）；批准前这两列建成后不可改，成本归集与交付单元判定沿用新建时的值。不采用「迁入后冻结」：组织调整后无更新入口，判定会悄悄过时。
2. **法人归属期间**（`employee_legal_entities`、`org_unit_legal_entities`）：ADR-0068 未定归属的维护命令，超出字面。推荐给两张命令表各加归属种类（员工侧「变更法人归属」进人事命令，组织单元侧「指定法人」进组织结构命令），走同一套待生效、撤回、版本写入与审计，不另起聚合；更正仍按 RULE-416 走作废。列为后续卡乙（编号由调度分配）（报用户批准种类），须在 W2 投产前定；答复前两表只有 `MIGRATION` 行，同 W2 第 3.6 节。
3. **`DISSOLVE` 与状态**：`DISSOLVE` 生效写 `REVOKED`。组织单元不设停用命令（第 4 条只有撤销），推荐 `org_unit_versions.status` 去掉 `DISABLED`，老系统中停用未撤销的单元在迁入规格中映射为 `REVOKED` 并列入迁入质量差异清单由人事确认；由 one-data 取证老数据中是否存在此类单元后在 W2 定（后续卡丙（编号由调度分配））。岗位保留 `DISABLE` → `DISABLED`。

## 检索记录

命令（在 `docs/adr/` 执行，main `d7b805d8`）：

```
rg -n 'OrgSnapshotChanged|EmployeeRegistered|separation_hold|sync_run_held_separations|OBSERVED|停薪留职|转正|Regularized'
```

| 命中 | 结论 |
|---|---|
| ADR-0034:238、335（同步运行 ID 由 `OrgSnapshotChanged` 提供） | 所在决定第 3 项已被 ADR-0055 部分取代，相容 |
| ADR-0034:272（OBSERVED） | 本篇第 6 条取代 |
| ADR-0034:505（证据锚点） | 无关 |
| ADR-0036:89（identity 订阅清单） | 描述性清单；迁入写 `MIGRATION` 行，不产生新事件；相容，事件名随 A 卡更新 |
| ADR-0039:11、26、48 | 所在小节已被 ADR-0055 部分取代，相容 |
| ADR-0055:56、88 | 本篇细化，相容 |

另检索 `rg -n 'separation_hold|人事命令|停薪|转正|employment_phase' D/glossary backend/glossary`：

- `separation_hold` 提议中；
- `regularized`（`contract.agreement`）同名异义；
- 其余未命中，列为待登记。

## 守护规则明细

1. **人事命令**：
   - 重放幂等；到日作业的执行与过点告警；
   - 前史补登与已关闭区间中间补登，均以作废加追加处理、不改写已有行；
   - 同一生效日只有一条待生效命令；离职之后只允许复职，复职须离职已生效；按生效日顺序执行；
   - 撤回待放行时，暂停记录同事务置为已撤回；
   - 只追加触发器；属主经清除函数可删，运行角色不可删；
   - 更正命令改变当日状态才发 `EmployeeCorrected`。
2. **待放行离职**：
   - 两条管理员离职并发时，第二条在锁内看到第一条的结果；
   - 放行人自己在待放行中时不能放行；
   - 剩余超管为 2、1、0 名时各自的行为；
   - 运维命令缺见证人时拒绝；无 CASCADE；放行只能一次；
   - B 未注册命令端点与到日作业；C 的预检；D2 的断言。
3. **F-1**：两套键各自去重；列与 `origin_kind` 的绑定 CHECK。
4. **登记**：`DataArchitectureTest`、`DdlNamingTest`、事件目录（含「转换无事件」登记）、MUST_AUDIT、system-actors、组织接口清单随卡更新。
5. D1 删除 `EmergencyLoginIntegrationTest:209` 的断言，须在 PR 中单列理由；其余断言不放宽。

## 影响面明细

- **organization.workforce**：
  - 新增 `personnel_commands`、`separation_holds`、`state_transitions`、运维命令 `release-separation-hold`；
  - 待批：`org_structure_commands`；
  - 删除同步与补建；版本按 `DECLARED`。
- **platform.identity**：
  - 订阅新事件；
  - `AdministratorSeparationReview` 排除待放行集合；
  - 列改名（R）。
- **kernel**：`SeparationReview` 增加入参与锁键常量。
- **卡**：
  - 改写 F-1、ADR-0034-IMPL PR-3、A-10、G4-01；
  - 撤销 WF-REINSTATE；
  - W2 由 one-data 签发第 3 版。

## 事件契约（ADR-0068 第 1 条第 9 项原文）

9. **事件**：命令生效时才发布，录入与撤回只写审计。这是有意不发事件，在事件注册表中登记。
   - **信封**：`aggregateType = Employee`，`aggregateId` 为员工 ID，`businessDate` 为生效日，`occurredAt` 为 `applied_at`。
   - **负载**：`personnelCommandId`、`employeeId`、`effectiveDate`。调岗另带触发字段和前后部门（ADR-0032 第 4 项、F-1 用）。
   - **事件名**（暂名，待登记）：`EmployeeHired`、`EmployeeRegularized`、`EmployeeTransferred`、`EmployeeSeparated`、`EmployeeReinstated`、`SeparationHeld`、`SeparationHoldReleased`。
   - **`EmployeeCorrected`**：更正改变了截至今天的状态（部门、岗位、在职状态、直属领导）时才发，负载为 `personnelCommandId`、`employeeId` 和受影响区间；只改历史时只写审计。
   - 停薪留职的事件随第 3 条定名。
   - 取代 `OrgSnapshotChanged`（`M/organization/workforce/events/OrgSnapshotChanged.java:14`）与 `EmployeeRegistered`；ADR-0034-IMPL 的 A-10 改从负载取 `personnelCommandId`。
