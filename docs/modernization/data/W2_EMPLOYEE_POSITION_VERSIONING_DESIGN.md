# 数据修复批 W2「员工与岗位版本化」数据设计（ADR-0055 决定 2，人事业务生效日）

- 作者：one-data，2026-10-21。依据：ADR-0055（已采纳）决定 2、3、6；用户 2026-10-16「U-12」（intent `2026-10-16-134434-…U-12-白话重问页提交-用人事`：生效依据改为人事业务生效日，可提前登记、到日生效，补登落回真实日期，修改时间由审计留痕）取代「同步观察日」（intent `085331`）；D17 细则与 D6；现行基线 `V20261014131000__organization_workforce_baseline.sql`（main）。
- 状态：**数据设计草稿，待独立复核后签发**（作者不自审）。A 线 PR-B（人事命令实现）依赖本设计；命令契约、命令 ID 空间、待放行离职新载体由 one-architecture 定（切分方案第 3 节第 1、2 条），本页只写数据侧并标出接口。
- 范围：`employees`、`positions` 版本化；`assignment_versions` 生效依据改 DECLARED；`assignments` 当前表去留；`employee_legal_entities`、`org_unit_legal_entities` 的生效依据同步改为 DECLARED（它们已是 DIMENSION_VERSIONED）。组织单元维护命令缺失（切分方案 3.4）不在本页。

## 1. 生效口径（对所有版本表一致）

- **有效期**：`effective_from_date`（含）、`effective_to_date`（不含，空表示开放），都是 **人事业务生效日**（`date`，由人事命令带入，D7）。读取以「业务日期」取当时有效的版本，所以：
  - **提前登记、到日生效**：允许 `effective_from_date` 晚于今天；未来版本存在但在到日前不是「当前」；多个未来版本不重叠。
  - **补登落真实日期**：补登的版本 `effective_from_date` 写真实发生日，不是登记日；只允许落在**没有版本覆盖**的区间（例如给更早的空档补一段，或在开放版本之前补前史，补登行 `effective_to_date` 取下一版本的生效日）。
  - **更正**：沿用 DATA-RULE-01 的同日修订——更正一个已有版本，先关闭该行为空区间（`effective_to_date = effective_from_date`），再以 `revision_number + 1` 追加新行；日期登记错了，同样先把错行关为空区间，再按真实日期追加。不改写旧行（D6、只追加）。
- **记录时间**与**操作者**不进生效日：`recorded_at`（注入时钟）、`recorded_by`（账号 ID，跨 schema 只存 ID）写在版本行；谁何时改了什么的轨迹由审计承担（用户 U-12 的落点）。
- **与授予关闭区分**：授予的 `effective_to_at` 是系统实际收回时刻，不用人事业务日期回填（ADR-0055 决定 3 第 6 点）。

## 2. 表设计

### 2.1 employees（根表：只留身份与个人当前值）

D17 细则 5：自有、可未来生效的主数据，根表不存可版本化的列。

| 列 | 去向 | 理由（D17 细则 3 逐列） |
|---|---|---|
| `id` | 留（根） | UUIDv7 |
| `employee_code` | 留（根） | 员工编号发号后不变（身份标识，同法人代码口径）；**业务唯一待核**：见 3.1 |
| `display_name`、`gender`、`email` | 留（根，当前值） | D17 细则 2 第四类「个人信息最小化只留当前值」；改动由审计留痕。**这是对「都做维度型」的一处收窄，登记为豁免，须在复核中确认** |
| `employment_status` | → `employee_versions` | 随生效日变化 |
| `is_fee_earner` | → `employee_versions` | 随生效日变化 |
| `separated_at` | 删 | 离职日 = 开放的 SEPARATED 版本的 `effective_from_date`，不再另存（E-2 随之关闭） |
| `login_name`、`wecom_user_id` | 删，移 `platform_integration.external_identities`（DA-3、ADR-0033 第 4 项第 5 条、ADR-0055 决定 2「企业微信按外部身份对照绑定」） | 外部身份不属人事记录；`login_name` 为空即离职的旧口径随同步一起退役 |
| `last_sync_run_id` | 删 | 同步退役（ADR-0055 决定 6） |
| `version`、`event_count`、`created_at`、`updated_at` | 留 | 乐观锁、事件序号 |

### 2.2 employee_versions（新，维度型）

```sql
CREATE TABLE organization_workforce.employee_versions (
    employee_version_id     uuid        NOT NULL,
    employee_id             uuid        NOT NULL,
    effective_from_date     date        NOT NULL,
    effective_to_date       date,
    revision_number         integer     NOT NULL DEFAULT 0,
    employment_status       text        NOT NULL,
    is_fee_earner           boolean     NOT NULL,
    change_origin_kind      text        NOT NULL,
    change_command_id       uuid,
    recorded_at             timestamptz NOT NULL,
    recorded_by             uuid,
    created_at              timestamptz NOT NULL,
    updated_at              timestamptz NOT NULL,
    CONSTRAINT pk_employee_versions PRIMARY KEY (employee_version_id),
    CONSTRAINT fk_employee_versions_employee_id FOREIGN KEY (employee_id) REFERENCES organization_workforce.employees (id) ON DELETE RESTRICT,
    CONSTRAINT uk_employee_versions_revision UNIQUE (employee_id, effective_from_date, revision_number),
    CONSTRAINT ck_employee_versions_range CHECK (effective_to_date IS NULL OR effective_to_date >= effective_from_date),
    CONSTRAINT ck_employee_versions_employment_status CHECK (employment_status IN ('EMPLOYED', 'SEPARATED')),
    CONSTRAINT ck_employee_versions_revision_number CHECK (revision_number >= 0),
    CONSTRAINT ck_employee_versions_origin CHECK (change_origin_kind IN ('COMMAND', 'MIGRATION')),
    CONSTRAINT ck_employee_versions_command CHECK ((change_origin_kind = 'COMMAND') = (change_command_id IS NOT NULL)),
    CONSTRAINT ck_employee_versions_recorded CHECK ((change_origin_kind = 'COMMAND') = (recorded_by IS NOT NULL)),
    CONSTRAINT ex_employee_versions_overlap EXCLUDE USING gist (employee_id WITH =, daterange(effective_from_date, effective_to_date) WITH &&)
);
CREATE INDEX ix_employee_versions_employment_status_effective_from_date ON organization_workforce.employee_versions (effective_from_date) WHERE employment_status = 'SEPARATED';
```
- 形状沿用现有 `org_unit_versions` / `assignment_versions`（`effective_from_date`、`effective_to_date`、`revision_number`、同形 EXCLUDE），触发器与权限回收按现有版本表一份（`refuse_version_rewrite` 的关闭列清单加 `effective_to_date`、`updated_at`），列入 `one_db_roles_schemas.sql` §4b 清单。
- `employment_status` 现只有 EMPLOYED、SEPARATED：停薪留职无对应在职状态（切分方案 3.3，待 one-architecture 裁），转正（试用期）没有已定义的状态集合；按门禁 B7 **不先写进 CHECK**，裁定后无损追加取值。
- `change_origin_kind = MIGRATION` 的版本是一次迁入写的初始版本（命令 ID、记录人为空）；迁入行的 `effective_from_date` 口径要生产证据（见第 5 节）。
- 时间列全部由应用传注入时钟，DDL 无 `now()`；`created_at` 与 `recorded_at` 同值，加 `CONSTRAINT ck_employee_versions_created_at CHECK (created_at = recorded_at)`；`updated_at` 在关闭行时写关闭时刻（版本表触发器放行 `effective_to_date`、`updated_at` 两列，其余只追加）。
- `change_command_id` 引用人事命令的 ID，命令表尚未建，**只存 ID，不建外键**（跨聚合/待建对象，D2）；类型 uuid，是否与离职命令共用 ID 空间由 one-architecture 定。

### 2.3 positions（根表）与 position_versions（新）

- 根表 `positions` 留：`id`、`created_at`、`updated_at`、`version`；`code`、`name`、`sort_order`、`status` 进版本表（岗位是受控目录，代码也可能随目录调整，与 `org_unit_versions` 同法：`code` 在版本表且可空）。
- `position_versions`：`position_version_id`、`position_id`（外键）、`effective_from_date`、`effective_to_date`、`revision_number`、`code`、`name`、`sort_order`、`status`（`ACTIVE`、`DISABLED`、`DELETED`，沿用现有取值）、`change_origin_kind`、`change_command_id`、`recorded_at`、`recorded_by`、`created_at`、`updated_at`；约束同 `employee_versions`，加 `ck_position_versions_name`（长度 ≤ 500 非空白）、`ck_position_versions_code`（空或长度 ≤ 500 非空白）、`status` 枚举 CHECK。
- 岗位编码是否全局唯一（门禁 O3）仍待取证：先不建唯一约束，列入迁入核对（第 5 节）。岗位维护命令（ADR 只列人员类命令）由 one-architecture 补（切分方案 3.4）。

### 2.4 assignments（当前任职表）：去除，版本表为唯一来源

- 现状：`assignments` 是同步写入的当前投影（`employee_id` 主键，一人一行）。自有、可未来生效的任职按 D17 细则 5 不存当前投影——有未来版本时「当前」随日期变，投影要靠到日作业刷新，反而多一个失败点。
- 设计：读取按业务日期从 `assignment_versions` 取（`@>` 日期，EXCLUDE 的 gist 索引已覆盖），`OrgDirectory` 缓存。`assignments` 的列约束（WF-06 的 `position_id` 可空、本人不是自己的直属领导）搬到 `assignment_versions`（见重判表）。
- `assignment_versions` 加 `change_origin_kind`、`change_command_id`、`recorded_at`、`recorded_by`（与 `employee_versions` 同），生效依据 DECLARED；`employee_legal_entities`、`org_unit_legal_entities` 的生效依据在注册表同步改为 DECLARED。
- 这是比「只做员工与岗位」更大的一步；若复核或 one-architecture 认为任职表保留当前投影（附一致性测试）更稳，退路是保留 `assignments` 并加到日刷新作业——请复核在两者间明确选一。我倾向去除。

## 3. 业务键、并发与查询路径

### 3.1 业务键（O3）
- 每个员工任一业务日至多一个有效版本：`ex_employee_versions_overlap`（岗位、任职同形）。
- `employee_code`：目前无唯一约束，仅普通索引。是否全局唯一取决于迁入取证（有无重复）；取证前写在卡里，不先建（避免迁入时失败），取证结果为零重复则加 `uk_employees_employee_code`。
- 离职后复职：同一员工开新的 EMPLOYED 版本，不新建员工（账号按员工唯一，ADR-0055 决定 2）。

### 3.2 并发
- 命令写版本时先锁 `employees` 根行（`version` 乐观锁或行锁），保证同一员工的区间调整是串行的；`EXCLUDE` 兜底拒绝重叠。

### 3.3 查询路径（O10）

| 路径 | 过滤/排序 | 预期行数 | 索引 |
|---|---|---|---|
| 某员工在业务日 d 的版本（权限、审批、成本，约 25 个调用点） | `employee_id = :e AND daterange(from, to) @> :d` | 1 | `ex_employee_versions_overlap` 的 gist |
| 某员工全部版本（人事详情、审计取证） | `employee_id` 排序 `effective_from_date, revision_number` | 个位数 | `uk_…_revision` |
| 业务日 d 在职名单（报表、周年） | `employment_status = 'EMPLOYED'` 且区间含 d | ≤ 1,500 | gist 范围；全表小，不建部分索引 |
| 某期间内离职的人（离职清除作业、报表） | `employment_status = 'SEPARATED'` 且 `effective_from_date` 在期间内 | 年约几十 | `ix_…_effective_from_date` 部分索引 |
| 岗位 d 日的名称、状态 | `position_id` + 日期 | 1 | gist 同形 |

## 4. 登记、保留期、分级

- 注册表：`employee_versions`、`position_versions` 为 `DIMENSION_VERSIONED`、`APPEND_ONLY`、生效依据 DECLARED；`employees`、`positions` 为 `AGGREGATE_ROOT`，`data_category: 主数据`；聚合登记 Employee、Position（OBJ-56、58），与 ADR-0058 讨论一致。
- 保留期：沿用现行口径——`employees` 的姓名、工号随引用存续；邮箱、企业微信 ID 等离职满 180 天置空并记审计（`RETENTION_PROPOSAL.md`，用户已定「按 one-data 建议」）；这些列随 `external_identities` 迁出后，清除作业随 DA-3。版本表含员工 ID 与在职状态，属代理键加状态，**不新增个人信息列**；保留期随员工，与 `assignment_versions` 一致（PERMANENT）。
- 分级：`employee_versions` 全列 INTERNAL，无自由文本；`recorded_by` 代理键不登记。

## 5. 迁入（ADR-0036、ADR-0054 一次投产）

- 投产时每名员工写一条 `MIGRATION` 初始版本（`employment_status` 取迁入时的状态，在职者 `EMPLOYED`，已离职者 `SEPARATED` 但无更早的 EMPLOYED 版本，投影里按左截断处理）。
- `effective_from_date` 口径要生产证据：老系统/A8 是否有入职日、离职日字段，取不到时用迁入日并标左截断；由迁入映射卡（E3）和 one-ops 取证给出，**本设计不预设**。
- `employee_code` 重复、岗位编码重复、`login_name` 与企业微信 ID 的迁往 `external_identities` 对照，同属迁入核对项。

## 6. 对其他设计的影响

- **ADR-0058**：`employees`、`positions` 的状态进了版本表，版本表自身即历史（M3 豁免），不再需要 `organization_workforce.state_transitions` 里的这两张；`org_units` 同理（已有 `org_unit_versions.status`，根表退成身份后不再有状态列）。`STATE_TRANSITION_TABLES.md` 里 workforce 一栏随之只剩 `legal_entities`（根表含状态）。
- **F-1 / 变更复核**：ADR-0055 决定 4 的去重键（人，人事变更命令 ID，触发字段）用的就是 `change_command_id`；版本表是「前值按人事版本和业务生效日取」的数据来源。
- **identity**：离职命令的 ID 同时写 `account_role_grants.revocation_separation_command_id`（#489）与 `employee_versions.change_command_id`，一个命令一个 ID。
- **同步退役**：`employees.last_sync_run_id`、`org_units.last_sync_run_id` 随 W2 / 删同步卡一并删除，不在旧位置补外键。

## 7. 需要别人回答的问题

1. **one-architecture**：命令 ID 类型与生成方、是否一个 ID 空间；停薪留职与试用/转正的状态集合；组织单元与岗位的维护命令；待放行离职新载体（切分方案 3.1–3.4）。
2. **复核**：2.4 去除 `assignments` 还是保留当前投影；2.1 个人信息列留根表作为 D17 豁免是否接受。
3. **one-ops/迁入取证**：入职日、离职日来源；`employee_code`、岗位编码重复情况。
