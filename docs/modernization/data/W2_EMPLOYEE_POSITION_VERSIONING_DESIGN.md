# 数据修复批 W2「员工与岗位版本化」数据设计（ADR-0055 决定 2、ADR-0068）——第 4 版（2026-10-21 晚补：停薪留职、组织单元与岗位命令表）

- 作者：one-data，2026-10-21。**第 4 版（@下一提交）按用户 2026-10-21 在 #494 评论与收件箱的答复回填：D17 豁免同意及六项边界、工号/岗位编码规矩、迁入重复工号处置、离职日口径、W-05 与 W-01 同批、`org_units` 根表收窄时限；并按用户批 ADR-0068 时的补充改第 0 节——零点作业不是正确性前提，登记时即写带未来生效日的版本，按日期读，作业只管事件与通知**（intent `9a5136bf`、`c28a0601`）。第 1 版 @ae8e4621，第 2 版 @06e4e73f；改后复核 `R2-W2设计改后复核-2026-10-21.md`（N-01…N-07、W-06/08/09、R-01）已在本版处理；首轮独立复核 `R-W2设计复核-2026-10-21.md`（`claude/one-coordinator-two-lanes` @13dd137e，W-01…W-17、R-01…R-04）；本版按复核改完，末尾有逐条处理表。
- 依据：ADR-0055 决定 2、3；ADR-0068（#493，提议）第 1、2、7 条；用户 U-12（intent `2026-10-16-134434-…U-12-…用人事`）；D6、D17 细则；现行基线 `V20261014131000__organization_workforce_baseline.sql`（main）。
- **状态：数据设计草稿 v2，待 sonnet 改后复核后签发。** 属 ADR-0068（待批）与补篇 B 类待批项的部分标「等答复」，不阻塞其余部分。
- 范围：`employees`、`positions` 版本化；`assignment_versions`、`employee_legal_entities`、`org_unit_legal_entities` 生效依据改 DECLARED 并采用统一更正机制；`assignments` 去除；`legal_entities` 代码规则（LV-1(b)）。`org_units` 根表收窄不在本版（第 9 节）。

## 0. 总体结构：登记即写版本、按日期读，作业只管事件与通知

用户批 ADR-0068 时的补充：**零点作业不是正确性前提——在职判断以生效日 ≤ 今天为准，作业只落状态和发事件，停摆时业务判断仍正确**；去除 `assignments` 的理由也是「按版本表加日期读，到日自动翻转，不依赖作业」。第 3 版 §0「版本只在命令执行时才写」与之相反，本版改为：

- **登记即写版本**：人事命令（`personnel_commands`，ADR-0068 第 1 条，员工聚合内，UUIDv7，所有种类一个 ID 空间）在**录入的同一事务里**写出带未来生效日的版本行（`effective_from_date` 可晚于今天），不论生效日是过去、今天还是将来。读取一律**按业务日期取版本**：`superseded_at IS NULL` 且区间含「今天」的行就是当前事实，到日**自动翻转**，不靠作业。
- **命令表的角色**：`PENDING` 表示「生效日未到，副作用（事件、通知、授予收回）尚未落地」；生效日不晚于今天的命令录入时即 `EFFECTIVE`。作业（零点后，自声明 `@Scheduled` + `@SchedulerLock`）只做三件事：把到日的 `PENDING` 命令置 `EFFECTIVE`（写 `applied_at`）、发领域事件、触发通知与授予的**正式收回**；作业停摆时，业务判断（谁在职、在哪个部门）仍然正确，只是事件与通知延后，过点没执行告警（ADR-0068 第 1 条 4）。
- **撤销尚未到日的登记**：不再是只动命令——同一事务里把对应的未来版本行**作废**（第 2 节机制）并置命令 `WITHDRAWN`。
- **副作用**（关闭授予、吊销会话、F-1 复核、缓存失效）由命令生效事件触发，幂等键 = 命令 ID（`personnelCommandId`），作业重放、补跑不会重复处理。

### 0.1 授予收回不依赖作业（W-02 的答案）

- **读时守卫**：权限判定在原有「授予开放」之外，加一条「持有人今天在职」——账号 → 员工 → 当前 EMPLOYED 版本（按今天的业务日期）。生效日到了而作业尚未跑，持有人的所有权限在读取时就已失效，不必等授予被正式关闭；会话校验与权限范围解析沿用同一判定（主体缓存 ≤ 60 秒，ADR-0026）。
- **正式收回**由作业触发：命令生效事件 → identity 关闭全部有效授予（实际收回时刻，不回填人事日期，ADR-0055 决定 3 第 6 点）、吊销会话、停用应急账号。读时守卫保证这段延迟里没有可用权限；作业补跑后授予状态与读时结果一致。
- **边界**：读时守卫只管「在职」，不替代正式收回——审计、授予历史、F-1 复核仍以事件为准。

### 0.2 管理员离职的保护也不依赖作业

- 保护（ADR-0055 决定 3 第 3、4 点：可用超管少于 2 名仍暂停，放行后为 0 名只能走双人运维命令）**在录入时判定**，不在零点作业里判定：管理员离职命令（含生效日在将来的）录入时，在咨询锁内用**待放行员工集合加所有尚未到日的管理员离职命令**一起作为 `SeparationReview` 的入参计算剩余可用超管；需要暂停的命令置 `HELD`，**不写离职版本**（乙）。这样将来日期的管理员离职不会因作业停摆绕过保护，也不会出现「到日自动离职、保护没跑」。
- 生效日作业**再判一次**（one-architecture 方案 A68，数据侧同意，带条件）：再判结果仍需暂停时，同一事务里把命令转 `HELD`、**作废**那条未来（或刚到日的）离职版本、不关授予；该转换必须写审计并告警，因为读时守卫在作业前的这段时间已把该员工当离职处理。条件：①作废与转 `HELD` 同事务；②授予的正式收回在再判之后才做，回到在职时无需恢复授予；③再判只会因「管理员被停用、角色被收回」这类外部变化改变结论（并发的管理员离职已在录入时被串行锁排除）。作业停摆时离职照常按日生效，是收紧方向；0 名超管由双人运维命令兜底（ADR-0055 决定 3 第 4 点）。
- 本节所需的契约改动（`SeparationReview` 入参含未到日的管理员离职）交 one-architecture，见第 10 节 6。

## 1. 生效口径

- **日期**：`effective_from_date`（含）、`effective_to_date`（不含，空表示开放），都是人事业务生效日（`date`）。读取以「业务日期」取当时有效的版本。
- **「今天」与截日**（W-03）：取注入时钟在业务时区（`BusinessZone`，Asia/Shanghai）下的当天日期。离职：人事填写的是**最后工作日**时，SEPARATED 版本的 `effective_from_date` = 最后工作日的次日（业务时区零点）；调岗、入职直接是新状态开始的第一天。授予关闭仍用实际收回时刻（`timestamptz`），不以补登的人事日期回填（ADR-0055 决定 3 第 6 点）。
- **补登落真实日期、更正**：用第 2 节作废机制，**任意区间都可以补登**（包括落在已关闭版本中间、早于最早版本的前史），不再限制「只落在无覆盖区间」。ADR-0068 第 1 条 4 的「生效日早于受影响版本起始日则拒绝」和「跨版本更正延后」，随本设计签发解除（见第 10 节，等 one-architecture 在补篇里改写）。
- **提前登记**：见第 0 节（登记即写带未来生效日的版本，到日自动翻转）。

## 2. 版本表通用结构与更正机制（W-01，高）

所有本批版本表（`employee_versions`、`position_versions`、`assignment_versions`，以及 `employee_legal_entities`、`org_unit_legal_entities`，W-09）统一：

| 列 | 说明 |
|---|---|
| `…_version_id uuid` | UUIDv7 主键 |
| 主体 ID（`employee_id` 等） | 同 schema 外键，`ON DELETE RESTRICT` |
| `effective_from_date date NOT NULL`、`effective_to_date date` | 有效期，左闭右开 |
| `revision_number integer NOT NULL DEFAULT 0` | 同一生效日的修订序号 |
| `change_channel_kind text NOT NULL` | `COMMAND` / `MIGRATION`（与 `role_versions.change_channel_kind` 同名同义，取值 SEED 不适用，W-14） |
| `change_command_id uuid` | 引用 `personnel_commands`（同 schema、同聚合）：**真外键** `ON DELETE RESTRICT`；`COMMAND` 必填，`MIGRATION` 为空。岗位、组织单元版本的命令表待 A4 |
| `version_audit_entry_id uuid` | 写入本行的同一事务里写下的审计条目 ID（跨 schema 只存 ID，S-03）；`COMMAND` 必填，`MIGRATION` 可空 |
| `closed_at timestamptz` | **关闭时刻**（N-01）：写 `effective_to_date` 的同时写入，由空变非空一次；`(effective_to_date IS NULL) = (closed_at IS NULL)` |
| `superseded_at timestamptz`、`superseding_command_id uuid` | **作废标记**：被更正或补登替换的行，只允许由空变非空**一次**；`superseding_command_id` 同外键 |
| `corrected_from_version_id uuid` | **重新追加行的来源**（N-02）：追加行是作废某行后的替换行时，指向被作废的行（同表外键）；其余为空 |
| `created_at`、`updated_at` | `created_at` 即记录时刻，由应用传注入时钟（删除第 1 版的冗余 `recorded_at`，W-14） |

- **只追加 + 作废**：版本行的内容列永不改。可变的只有：关闭列 `effective_to_date`、`closed_at`，作废列 `superseded_at`、`superseding_command_id`——这四列都只能由空变非空**一次**；`updated_at` 可随关闭、作废变化，不受一次性限制。触发器放行列清单按 identity 的 `refuse_history_rewrite(...)` 先例做成参数。已关闭的行**仍可被作废**（W-01 的关键）。
- **防重叠只看未作废行**：`EXCLUDE USING gist (employee_id WITH =, daterange(effective_from_date, effective_to_date) WITH &&) WHERE (superseded_at IS NULL)`。唯一约束 `(employee_id, effective_from_date, revision_number)` 保留（作废行与替换行修订号递增）。
- **补登与更正怎么做**（一个命令一个事务）：①把受影响的行标为作废（`superseded_at` = 注入时钟，`superseding_command_id` = 本命令）；②追加覆盖同一区间（受影响区间的并集）的新行，内容为更正后的事实，边界按真实日期；③被拆开的原事实以新行重新追加（带原内容），且**保留原来源**（N-02）：`change_channel_kind`、`change_command_id`、`version_audit_entry_id` 照抄被作废行，`corrected_from_version_id` 指向被作废行；只有内容真正被更正的新行才取更正命令的 ID（`change_command_id` = 更正命令）。一致性测试：更正前后，对原命令按 `change_command_id` 取到的原事实不变。按命令取前值（F-1）读 `corrected_from_version_id IS NULL` 的行，或沿 `corrected_from_version_id` 追到最早那一行。`MIGRATION` 行重新追加后仍是 `MIGRATION`。
  - 撤销尚未生效的提前登记 = 把对应的未来版本行作废并置命令 `WITHDRAWN`（同一事务，第 0 节）。
  - 日期登记错了：作废错误行，按真实日期追加。
- **读取**：「当前事实」= `superseded_at IS NULL` 的行按业务日期取。「系统当时认为」的回放（ADR-0034 授权可还原，ADR-0068 第 6 条把「以 `applied_at` 为界还原」延后）用三个时刻界定一行在时刻 T 的样子：行存在 = `created_at <= T AND (superseded_at IS NULL OR superseded_at > T)`；它在 T 的结束日 = `effective_to_date` 当且仅当 `closed_at <= T`，否则开放（N-01：原地写结束日不丢当时状态，因为 `closed_at` 单独记了关闭时刻）。
- 这是全库版本表的统一更正机制，建议 K-2 时态基座承接，日后逐步收掉 `legal_entity_attribution_corrections` 这种覆盖表写法（S-02）。

## 3. 表设计

### 3.1 employees（根表：身份与个人当前值）

| 列 | 去向 | 理由（D17 细则 3 逐列） |
|---|---|---|
| `id` | 留（根） | UUIDv7 |
| `employee_code` | 留（根） | 发号后不变，命令拒改并有测试（同法人代码，D17 细则 8）；**全局唯一，复职沿用原工号**（用户 2026-10-21 已定）：`uk_employees_employee_code UNIQUE (employee_code)`，替换现有普通索引 `employees_employee_code_idx` |
| `display_name`、`gender`、`email` | 留（根，当前值） | D17 细则 2 第四类「个人信息最小化只留当前值」；对「都做维度型」（intent `085331`）的一处收窄，**用户 2026-10-21 已同意** |
| `employment_status`、`is_fee_earner` | → `employee_versions` | 随生效日变化 |
| `separated_at` | 删 | 离职日 = 开放的 SEPARATED 版本的 `effective_from_date` |
| `login_name` | 删（D2，ADR-0068 第 7 条 1） | A8 登录名；只剩迁入追溯用途，进**来源对照/迁入暂存**，**不**进 `external_identities`（W-12） |
| `wecom_user_id` | 暂留，随 DA-3 的 `platform_integration.external_identities` 落地同批删（ADR-0068 第 7 条 4：企业微信登录分支保留到外部身份落地） | 企业微信登录路径还靠它。**配套（N-05）**：现有部分唯一索引 `employees_wecom_user_id_employed_key`（`WHERE employment_status = 'EMPLOYED'`）依赖被移走的 `employment_status`，版本表上建不了跨表部分唯一。过渡期改为 `UNIQUE (wecom_user_id) WHERE wecom_user_id IS NOT NULL`（含离职者，更严）；迁入若有同一企业微信 ID 对应多条人员（历史重复）走拒收与处置（M6），不为迁入放宽；DA-3 落地时唯一性随 `external_identities` 的绑定规则重做，本索引删除 |
| `last_sync_run_id` | 删 | 同步退役 |
| `version`、`event_count`、`created_at`、`updated_at` | 留 | 乐观锁、事件序号 |

**豁免已获用户同意**（2026-10-21，#494 评论 6311130069、6311104971，intent `9a5136bf`、`c28a0601`），六项边界（原五个条件加固）：①出处=上述用户答复，仅限 `display_name`、`gender`、`email` 三列，`employee_code` 不在其中且**发号后禁改并有测试**；②任何业务规则不按历史日期读这三列，单据需要「当时的姓名」的自存快照；③命令契约：这三列的修改即时生效，拒绝带未来日期（交 one-architecture）；④每次修改写审计，`before_text`/`after_text` 按 ADR-0043 第 4 项只记 L2 原值，`employee_code`（L3）不记原值；⑤注册表逐列写「个人信息最小化只留当前值」并附出处，`email` 离职满 180 天置空作业登记 `job`；三列登记 INTERNAL + PI（理由以「用途：」开头）；`ddl-naming-baseline.txt` :4-5 的 `email_address`、`gender_code` 改名在本批一起办。

### 3.2 employee_versions（新）

第 2 节通用列，加：`employment_status text NOT NULL`（`EMPLOYED`、`SEPARATED`；`ck_employee_versions_employment_status`）、`is_fee_earner boolean NOT NULL`。

```sql
CREATE TABLE organization_workforce.employee_versions (
    employee_version_id     uuid        NOT NULL,
    employee_id             uuid        NOT NULL,
    effective_from_date     date        NOT NULL,
    effective_to_date       date,
    revision_number         integer     NOT NULL DEFAULT 0,
    employment_status       text        NOT NULL,
    employment_phase        text,
    is_on_unpaid_leave      boolean     NOT NULL,
    is_fee_earner           boolean     NOT NULL,
    change_channel_kind     text        NOT NULL,
    change_command_id       uuid,
    version_audit_entry_id  uuid,
    closed_at               timestamptz,
    superseded_at           timestamptz,
    superseding_command_id  uuid,
    corrected_from_version_id uuid,
    created_at              timestamptz NOT NULL,
    updated_at              timestamptz NOT NULL,
    CONSTRAINT pk_employee_versions PRIMARY KEY (employee_version_id),
    CONSTRAINT fk_employee_versions_employee_id FOREIGN KEY (employee_id) REFERENCES organization_workforce.employees (id) ON DELETE RESTRICT,
    CONSTRAINT fk_employee_versions_change_command_id FOREIGN KEY (change_command_id) REFERENCES organization_workforce.personnel_commands (personnel_command_id) ON DELETE RESTRICT,
    CONSTRAINT fk_employee_versions_superseding_command_id FOREIGN KEY (superseding_command_id) REFERENCES organization_workforce.personnel_commands (personnel_command_id) ON DELETE RESTRICT,
    CONSTRAINT fk_employee_versions_corrected_from_version_id FOREIGN KEY (corrected_from_version_id) REFERENCES organization_workforce.employee_versions (employee_version_id) ON DELETE RESTRICT,
    CONSTRAINT uk_employee_versions_revision UNIQUE (employee_id, effective_from_date, revision_number),
    CONSTRAINT ck_employee_versions_range CHECK (effective_to_date IS NULL OR effective_to_date >= effective_from_date),
    CONSTRAINT ck_employee_versions_finite CHECK (isfinite(effective_from_date) AND (effective_to_date IS NULL OR isfinite(effective_to_date))),
    CONSTRAINT ck_employee_versions_employment_status CHECK (employment_status IN ('EMPLOYED', 'SEPARATED')),
    CONSTRAINT ck_employee_versions_employment_phase CHECK (employment_phase IN ('PROBATION', 'REGULAR')),
    CONSTRAINT ck_employee_versions_phase_status CHECK ((employment_status = 'EMPLOYED') = (employment_phase IS NOT NULL)),
    CONSTRAINT ck_employee_versions_leave_employed CHECK (NOT is_on_unpaid_leave OR employment_status = 'EMPLOYED'),
    CONSTRAINT ck_employee_versions_revision_number CHECK (revision_number >= 0),
    CONSTRAINT ck_employee_versions_change_channel_kind CHECK (change_channel_kind IN ('COMMAND', 'MIGRATION')),
    CONSTRAINT ck_employee_versions_command CHECK ((change_channel_kind = 'COMMAND') = (change_command_id IS NOT NULL)),
    CONSTRAINT ck_employee_versions_audit CHECK (change_channel_kind <> 'COMMAND' OR version_audit_entry_id IS NOT NULL),
    CONSTRAINT ck_employee_versions_closed CHECK ((effective_to_date IS NULL) = (closed_at IS NULL)),
    CONSTRAINT ck_employee_versions_superseded CHECK ((superseded_at IS NULL) = (superseding_command_id IS NULL)),
    CONSTRAINT ex_employee_versions_overlap EXCLUDE USING gist (employee_id WITH =, daterange(effective_from_date, effective_to_date) WITH &&) WHERE (superseded_at IS NULL)
);
CREATE INDEX ix_employee_versions_change_command_id ON organization_workforce.employee_versions (change_command_id) WHERE change_command_id IS NOT NULL;
CREATE INDEX ix_employee_versions_superseding_command_id ON organization_workforce.employee_versions (superseding_command_id) WHERE superseding_command_id IS NOT NULL;
CREATE INDEX ix_employee_versions_separated_effective_from_date ON organization_workforce.employee_versions (effective_from_date) WHERE employment_status = 'SEPARATED' AND superseded_at IS NULL;
```
- 触发器 `tg_employee_versions_append_only`（BEFORE UPDATE OR DELETE）：`effective_to_date`、`closed_at`、`superseded_at`、`superseding_command_id` 四列各自只放行由空变非空一次，`updated_at` 可变，其余列拒；TRUNCATE 语句级触发器拒；`one_db_roles_schemas.sql` §4b 回收 DELETE、TRUNCATE，**清理通道**见第 8 节。
- `employment_phase`（试用 `PROBATION`、转正后 `REGULAR`；ADR-0068 第 1 条 3 已定转正命令与阶段列，只在在职时有值，离职为空）：入职命令写初始阶段，转正命令写 `REGULAR`。**停薪留职标记 `is_on_unpaid_leave`**（H3，用户批 ADR-0068 时定，已获采纳）落在本表，随时间开关，按 D17 版本化，不落 `employees`；它不是雇佣状态，离职版本恒为假（`ck_employee_versions_leave_employed`）；命令种类相应增加停薪留职开始、结束（`personnel_commands` 加 `target_is_on_unpaid_leave`）。`employment_status` 现只有 EMPLOYED、SEPARATED。（第 3 版的数据侧建议：`employment_status` 只表达雇佣关系，停薪留职另设 `leave_status`、试用另设 `employment_phase` 作为独立列（行业通行的「工作状态与请假分开」；identity 现有按 EMPLOYED 判定的逻辑不用跟着变），裁定后基线开放期加列。
- 管理员离职暂停期间（A2）：按 ADR-0068 第 2 条，离职命令转 `HELD`，员工保持在职、**不写 SEPARATED 版本**，放行时按原生效日写版本（经第 2 节补登机制）；因此本表在暂停期间没有 SEPARATED 行。判定在录入时做（第 0.2 节），不依赖作业。

### 3.3 positions 与 position_versions

- 根表 `positions` 留：`id`、`created_at`、`updated_at`、`version`；`code`、`name`、`sort_order`、`status` 进版本表（与 `org_unit_versions` 同法）。
- `position_versions`：`position_version_id`、`position_id`（外键）、第 2 节通用列、`code`（可空，长度 ≤ 500 非空白）、`name`（≤ 500 非空白）、`sort_order`、`status`。
- **岗位状态（W-16）**：取值 `ACTIVE`、`DISABLED`；**不再沿用 `DELETED`**（那是同步「组织源删除或不再返回」的语义，基线 :19）。ADR-0068 第 4 条岗位只做新建与停用，与之对齐；岗位与组织单元命令同表 `org_structure_commands`（`target_kind` 取 `ORG_UNIT` / `POSITION`，ADR-0068 H4，已获采纳；DDL 草案 @23401a91 已过 one-data 审，见 `reviews/ADR-0068.md` 第四轮）：`position_versions`、`org_unit_versions` 用 `change_org_structure_command_id` 指向它（同 schema 真外键，与 `change_command_id` 二者对 `COMMAND` 行恰有一个非空）；`org_unit_versions`、`position_versions` 各加一列 `change_org_structure_command_id uuid`（`COMMAND` 行必填、`MIGRATION` 行为空）；员工、任职、员工法人归属版本表仍用 `change_command_id`，两类列不同表，不需要互斥约束。
- **岗位编码全公司唯一**（用户 2026-10-21 已定）：`position_versions.code` 可空；`uk_position_versions_code` 为部分唯一索引 `UNIQUE (code) WHERE code IS NOT NULL AND superseded_at IS NULL`（作废行不占编码）；**编码永不复用**——岗位撤销时保留编码、状态置 `DISABLED`，不过滤状态，停用岗位仍占编码；更名不换编码；另有守护测试断言「用过的编码不会出现在另一个岗位的任何版本里」（含已作废行，作废行不在索引内，由守护兜底）。

### 3.4 assignment_versions 与 assignments 去除（复核 2.1，结论：去除）

- 去除 `assignments`：自有、可未来生效的任职不存当前投影（D17 细则 5）。理由（用户认可）：按版本表加日期读，到日自动翻转，不依赖作业；另有少一份投影与一套一致性测试、一个事实来源、规模小（≤ 1,500 人、个位数版本）、D17 细则 5 的明文要求。（第 3 版曾因「版本在执行时才写」撤掉「不依赖作业」这条理由，本版随第 0 节改回。）
- **没有读取窗口**：第 4 版起版本在登记时写入，读取按日期取，到日自动翻转，作业停摆不影响在职与任职判断（第 0 节）。第 3 版因「版本只在执行时写」而写的窗口论述已删除；离职后的权限由读时守卫兜住（第 0.1 节）。
- `assignment_versions` 加第 2 节通用列；约束搬入：`position_id` 对 `COMMAND` 行必填（`CHECK (change_channel_kind <> 'COMMAND' OR position_id IS NOT NULL)`，迁入行允许空，请人事确认）、`reports_to_employee_id` 不得等于本人（`CHECK (reports_to_employee_id IS DISTINCT FROM employee_id)`）。
- **索引（W-04、C-a）**：`(org_unit_id, effective_from_date)` 部分索引 `WHERE superseded_at IS NULL`；`reports_to_employee_id` 部分索引（`IS NOT NULL AND superseded_at IS NULL`）；gist 防重叠索引服务按员工取日期。
- **时点（C-b）**：「当前」取注入时钟按业务时区截日；按时刻检查的授权（`timestamptz`）先换成业务日期再查任职版本。**缓存（C-c）**：以业务日期为键、命令提交后失效；也可不加，1,500 人规模按索引直接查。
- **读取改写代价**列入 PR：`OrgSnapshotRows.SELECT_STAFF`（`employees` 内连接 `assignments`）、`OrgDirectoryService` 的 `findEmployee`、`findSupervisor`、`employmentOf` 改为按业务日期读取，「已有员工版本但当日还没有任职版本」的人明确处理（目录返回「尚未任职」）；测试夹具 `AccountsAcceptanceSupport.java:251、286、300`、`OwnIdColumnsIT.java:48-51` 改写版本表，删除测试条目在 PR 单列理由（CLAUDE.md 第 5 条）。
- **退职时的任职**（W-17）：离职命令执行时，同一事务内关闭该员工开放的任职版本（`effective_to_date` = SEPARATED 版本的 `effective_from_date`）；加跨表一致性集成测试。

### 3.6 两张归属期间表（W-09）

`employee_legal_entities`、`org_unit_legal_entities`（均 DIMENSION_VERSIONED）现状：无修订号，唯一键 `(主体, effective_from_date)`，区间 CHECK 为 `>`，更正走覆盖表 `legal_entity_attribution_corrections`（DATA-RULE-07、RULE-416）。改动（W2 同批）：
- 加第 2 节通用列：`closed_at`、`superseded_at`、`superseding_command_id`、`corrected_from_version_id`、`change_channel_kind`、`change_command_id`、`version_audit_entry_id`。
- 唯一键 `uk_*_effective_from (主体, effective_from_date)` 改为**部分唯一** `… WHERE superseded_at IS NULL`（这两张表没有 `revision_number`，用部分唯一代替）；`ex_*_overlap` 加 `WHERE (superseded_at IS NULL)`；区间 CHECK 仍为 `>`（空区间不再需要，更正走作废）。
- `change_command_id`：`employee_legal_entities` → `personnel_commands`（真外键）；`org_unit_legal_entities` → `change_org_structure_command_id` 指 `org_structure_commands`。**待 one-architecture 答**：法人归属期间（OBJ-55/56 的关系）的维护目前不在两张命令表的命令种类里（人事命令无归属种类，组织结构命令也没有 `ASSIGN_LEGAL_ENTITY`），要么给 `org_structure_commands` / `personnel_commands` 增加归属种类，要么承认归属维护走它自己的聚合与命令（RULE-416 的更正路径）；答复前这两张表的 `COMMAND` 行不可写，只有 `MIGRATION` 行。
- 覆盖表 `legal_entity_attribution_corrections` 暂留作更正记录（原因、操作者），新写入的更正同时指向被作废行；收口由 K-2 承接（S-02）。
- 触发器放行列同第 2 节参数化。

### 3.5 法人代码规则（LV-1(b)，R-03 并入）

- `legal_entity_versions.code` 移到根表 `legal_entities.code`（`NOT NULL`，格式 CHECK，`UNIQUE`）：代码发号后不可改（DATA-RULE-03），版本表只留名称、状态。法人代码跨时间唯一因此由根表唯一约束承担，不需要 `EXCLUDE (code …)`。和 B-13 提议 yaml 的「根表只留代码与标识，名称状态随版本」一致。

## 4. 业务键、查询路径、并发（O3、O10、D4）

- 每个员工任一业务日至多一个未作废的有效版本：`ex_*_overlap … WHERE (superseded_at IS NULL)`（员工、岗位、任职同形）。
- 复职：同一员工开新的 EMPLOYED 版本（账号按员工唯一，ADR-0055 决定 2）。
- 并发：命令写版本前先锁 `employees` / `positions` 根行（`version`），区间调整串行，EXCLUDE 兜底；根行乐观锁。
- 查询路径：

| 路径 | 过滤/排序 | 预期行数 | 索引 |
|---|---|---|---|
| 员工在业务日 d 的版本 | `employee_id` + 区间含 d + `superseded_at IS NULL` | 1 | gist（部分） |
| 员工全部版本（人事详情、取证） | `employee_id` 排序 `effective_from_date, revision_number` | 个位数 | `uk_…_revision` |
| 业务日 d 在职名单 | `employment_status='EMPLOYED'` + 区间含 d | ≤ 1,500 | gist 范围，全表小不另建 |
| 某期间内离职 | `employment_status='SEPARATED'` + `effective_from_date` | 年约几十 | `ix_…_separated_effective_from_date` |
| 按命令取版本（F-1 前值） | `change_command_id` | 个位数 | `ix_…_change_command_id` |
| 某组织单元 d 日任职、某人 d 日直属下属 | 第 3.4 节 | ≤ 数百 | 第 3.4 节索引 |
| 岗位 d 日的名称、状态 | `position_id` + 日期 | 1 | gist（部分） |

- D17 细则 5「查询日期早于最早版本时失败告警」（W-05）：分开定义——**目录查询**（谁在职、有哪些人）对「尚未入职」返回明确的「尚未在职」，不告警；**单据/事实的维度查询**早于最早版本仍失败并告警。迁入后左截断与迁入事实的日期早于最早版本，要求**迁入映射卡（E3）保证最早版本覆盖到最早迁入事实的日期，或让迁入事实自带维度（D7）**——这是迁入设计的前置条件，不是「不预设」。

## 5. 登记与分级（W-06、W-08、D14）

- 注册表：`employee_versions`、`position_versions`、`assignment_versions` 为 `DIMENSION_VERSIONED`、`APPEND_ONLY`、生效依据 DECLARED；`employees`、`positions` 为 `AGGREGATE_ROOT`，`data_category: 主数据`；`lineage` 由 `ORG_SOURCE` 改 `NEW`，迁入行以 `change_channel_kind = MIGRATION` 表达，来源追溯经 `source_references`。`aggregates.yaml` 补 Employee（含 `employees`、`employee_versions`、`assignment_versions`、`personnel_commands`）、Position；`employee_legal_entities` 仍属 LegalEntityAttribution 聚合（D3），任职/归属是否同事务由命令契约（A1）定，默认离职只关任职与员工版本，归属期间的关闭另议。
- 分级：`employee_versions` 的 `employment_status`、`is_fee_earner` 与可识别的员工相关，登记 **INTERNAL + PI**（第 1 版「不新增个人信息列」的说法已删，D13）；`assignment_versions` 同属这一缺口，另列条交 ADR-0043 实施。
- **保留期（R-2）**：`RETENTION_PROPOSAL.md` 第 14–16 行，用户答「按 one-data 建议」（intent `2026-10-16-090551-one-coordinator-已建成表的保留期与匿名化-按-o.md`；该文件标题为「建议」，用户认可见此 intent）：`employees` 姓名、工号随引用存续；邮箱等离职满 180 天置空；**任职版本等个人任职史版本表在员工离职满 10 年后删除该员工的行**。所以 `employee_versions`、`assignment_versions` 登记 `PERMANENT` 并写理由与出处，**并有删除通道**（第 8 节）；`position_versions` 无个人信息，永久。`employees` 根表保留策略从 `CURRENT_STATE` 改为上述口径，清除作业登记 `job`。
- 本批同批登记清单（W-08）：`ddl-naming-baseline.txt` :4-5（`email`、`gender` 改名）、:23-26（`assignments` 的三个外键、主键）、:55（`assignments_org_unit_id_idx`）；:27 `employees_pkey` 与 :56-57（`employees_employee_code_idx`、`employees_wecom_user_id_employed_key`）**不删**，:57 按 3.1 的 N-05 改写后更新该行；`data-classification.txt` :20-21（`login_name`/`wecom_user_id`，前者随列删，后者随 DA-3），:19、:22（`display_name`、`employee_code`）随豁免登记原因；`versioned-table-unconditional-writes.txt` :3、:9（`employees` 的写入登记，随版本化改写）；注册表 :55-86、:91-103（role、aggregate、lineage、`fk_exceptions` :79 的 `last_sync_run_id`）；`aggregates.yaml`；`one_db_roles_schemas.sql` §4b 加三张新版本表；测试夹具（3.4）。

## 6. 迁入（ADR-0036、ADR-0054 一次投产）

- 每名员工写一条 `MIGRATION` 初始版本，`change_command_id` 空；`effective_from_date` 口径要生产证据（A8/老系统有无入职日、离职日，取不到用迁入日并标左截断），由 E3 与 one-ops 取证给出，本设计不预设取值，但提出前置条件（第 4 节末）。
- 重复岗位编码、`login_name`（进来源对照）与企业微信 ID（进 `external_identities`）的迁入核对，归 E3。
- **重复工号的迁入拒收清单**（用户 2026-10-21 已定）：迁入不放宽 `uk_employees_employee_code`，命中已有员工的行拒收并落清单，**业务可查**（界面或报表可读，不是日志文件）。清单每行写：原编码、命中哪个已有员工（`hit_employee_id`，同 schema 外键）、处置三选一——合并（并入该员工）、改号（给迁入这个人发新编号）、拒收不迁；每行**指定处置人与期限**。建议暂名 `organization_workforce.employee_code_conflicts`（迁入核对差异的一部分，建表随 E3/迁入核对卡的数据设计签发）：`conflict_id uuid` 主键、`source_employee_ref uuid`（来源对照 ID，迁入行尚无自有 ID，规则同 SD-1 的「恰好一个非空」写法）、`original_code text`、`hit_employee_id uuid`（外键）、`disposition text`（`PENDING`、`MERGE`、`RENUMBER`、`REJECT`）、`assignee_employee_id uuid`、`due_date date`、`resolved_at timestamptz`、`resolved_by`、`created_at`、`updated_at`；`disposition <> 'PENDING'` 当且仅当 `resolved_at` 非空；逾期未处置告警。保留期随迁入核对（核对通过、切换窗口结束后删除，ADR-0036）。

## 7. 用户已定（2026-10-21，#494 评论 6311130069、6311104971，intent `9a5136bf`、`c28a0601`）

1. **D17 豁免**：同意。仅 `display_name`、`gender`、`email` 只留当前值；六项边界见第 3.1 节（`employee_code` 不在内且发号后禁改并有测试；无规则按日期读三列；三列修改即时生效、命令拒绝未来日；审计只记 L2 原值、工号不记原值；邮箱离职满 180 天置空并登记 job 名）。
2. **工号**：全局唯一，复职沿用（第 3.1 节）。**岗位编码**：全公司唯一，`code` 可空、部分唯一索引 `WHERE code IS NOT NULL`，编码永不复用、撤销保留编码置停用（第 3.3 节）。
3. **迁入重复工号**：拒收清单每行写原编码、命中哪个已有员工、处置三选一（合并/改号/拒收不迁），业务可查，每行指定处置人与期限（第 6 节）。
4. **离职日口径**：SEPARATED 版本生效日 = 最后工作日次日业务时区零点（第 1 节）。
5. **W-05 与 W-01 同批**：目录查询与单据维度查询的区分（第 4 节末）和作废机制（第 2 节）同批实施。
6. **`org_units` 根表收窄设计**：A4 答复后 30 天内由 one-data 出（第 9 节）。

## 8. 版本表删除通道（R-2 的执行）

- 按员工离职满 10 年删除其版本行（`RETENTION_PROPOSAL.md` 第 16 行）：与 ADR-0058 同法，`SECURITY DEFINER` 函数 `organization_workforce.purge_employee_history(p_cutoff date)`，属主角色执行，触发器放行 `current_user = 属主` 的 DELETE（运行角色无权）；只删整名员工到期的全部版本，同语句删完，复合外键不触发。清理作业另批，期限首次生产部署后 30 个月内，负责人 one-1a-par（同 ADR-0034 的 D-1 写法），registry 先写 `PERMANENT` + 注释。
- **命令表与待放行表的保留与清除（N-06）**：`personnel_commands`、`separation_holds`（ADR-0068）含员工 ID 与异动内容，ADR-0068 的触发器拒 DELETE。10 年后与版本表同批清除：`purge_employee_history` 先删该员工的 `separation_holds`、版本行，再删 `personnel_commands` 行（版本、待放行表对命令的 `RESTRICT` 外键决定次序），命令表与待放行表的触发器同样放行 `current_user = 属主` 的 DELETE，运行角色无权。这是对 ADR-0068 第 1、2 条触发器写法的补充要求（跟进意见写入 `reviews/ADR-0068.md`）；命令表不含个人信息列（该文意见 2），所以清除是 R-2 的存量合规，不是个人信息抹除。
- 触发器、权限、函数模板与 `state_transitions` 的公共卡共用（若 ADR-0058 实施恢复）。

## 9. 不在本版（带负责人与触发点）

- **`org_units` 根表收窄**：根表仍存当前值，同步撤销后没有写入方；提前改隶会让「按日期取任职、按当前取组织树」算错子树（复核 C-d）。触发点：one-architecture 答复补篇第 4 条（组织单元命令）；**时限：答复后 30 天内**（用户 2026-10-21）；负责人：one-data 出收窄设计；在此之前，范围、权限相关读取对组织树仍用当前值，这一已知风险登记在本设计。
- **转正落点、停薪留职状态**：等 A3；**岗位命令表**：等 A4。
- **`personnel_commands.org_structure_command_id` 的外键**（W2 PR-B 只建列、不加外键，one-data 2026-10-22 同意，`reviews/W2-PR-B.md`）：触发点 = 建 `org_structure_commands` 的批次；该批次在同一迁移里补 `fk_personnel_commands_org_structure_command_id`（`ON DELETE RESTRICT`），基线开放期直接改基线；one-data 签那批表结构时核。

## 10. 等 one-architecture 的字面冲突与请求（W-07、N-03）

1. ADR-0034:272「依据同步观察日（OBSERVED）」改为 DECLARED（ADR-0068 第 6 条已裁，待批准）。
2. ADR-0058 对版本表的「自身即历史」豁免需逐行登记评审：只有版本行带齐 ADR-0058 第 1 条等价字段才成立——业务日期（`effective_from_date`）、记录时刻（`created_at`）、操作者（审计条目 ID）、命令 ID、原因（更正 = 作废标记）。本设计的版本行已带齐，豁免成立的前提是 ADR-0058 状态转换表为这三张表登记例外。
3. ADR-0068 第 1 条 4 的补登拒绝规则与跨版本更正延后，本设计签发后解除。
4. 管理员离职暂停（A2）：ADR-0068 第 2 条已选「暂停不写版本」（乙），由第 2 节机制支撑；one-architecture 已在 #493 第 15 条确认：OBSERVED 改 DECLARED、待放行期间不写离职版本、放行时补写（W2 须先支持补登，本设计第 2 节即是）、停薪留职改独立状态列。
5. **请 ADR-0068 第 1 条 3 增加命令种类「更正」`CORRECTION`（N-03）**：更正与补登靠 `superseding_command_id` 引用 `personnel_commands`，现有种类（入职、转正、调岗、停薪留职、离职、复职）无处挂一个纯更正（例如把历史版本的 `is_fee_earner` 改对，或改正登记错的日期）。`CORRECTION` 不受「同一员工同一生效日只能一条待生效」限制（更正即时执行，不经 `PENDING`）。**事件**：更正使「截至今天」的状态（部门、岗位、在职状态、直属领导）发生变化时发布 `EmployeeCorrected`（负载 `personnelCommandId`、`employeeId`、受影响区间），identity 订阅后重算范围复核与授予；只改历史、不改今天的状态则不发事件，只写审计。事件名与载荷请 one-architecture 定，本设计只提需求。
6. **管理员离职保护在录入时判定，且把未到日的管理员离职命令计入 `SeparationReview` 的入参**（第 0.2 节）；到日作业不得重新评估并改写版本；零点作业不是正确性前提（用户批 ADR-0068 时的补充），ADR-0068 第 1 条 4 的措辞「过点没执行的告警，延迟执行也是安全空窗」改为「延迟的是事件与通知，业务判断仍按日期正确」。

## 11. 复核意见处理表

| 编号 | 处理 |
|---|---|
| W-01 | 第 2 节：作废标记（`superseded_at`、`superseding_command_id`，空变非空一次）、EXCLUDE 只看未作废行、补登与更正算法、回放口径；`assignment_versions` 与两张归属期间表同批 |
| W-02 | 第 0 节：命令表 + 到日作业为载体，执行时才写版本与发事件，幂等键为命令 ID；撤销提前登记只动命令 |
| W-03 | 第 1 节：业务时区截日；离职取最后工作日次日 |
| W-04 | 第 3.4、4 节：任职路径与索引；`change_command_id` 索引 |
| W-05 | 第 4 节末：目录查询与单据维度查询分开；迁入前置条件 |
| W-06 | 第 5 节：INTERNAL + PI；保留期引用户已定（离职满 10 年删除），`employees` 保留策略改写 |
| W-07 | 第 10 节：交 one-architecture 的字面冲突清单 |
| W-08 | 第 5 节：本批同批登记清单 |
| W-09 | 第 2 节：两张归属期间表采用同一机制，`legal_entity_attribution_corrections` 暂留作更正记录，逐步收（S-02） |
| W-10 | 第 3.2 节：采 ADR-0068 的乙（暂停不写版本），W-01 支撑 |
| W-11 | 第 7 节：进收件箱 |
| W-12 | 第 3.1 节：`login_name` 进来源对照/迁入暂存，只有企业微信 ID 进 `external_identities` |
| W-13 | 第 2 节：命令表同 schema 同聚合，**真外键**；`personnel_commands` 须与版本表同一迁移或先于版本表建（ADR-0068 的 B） |
| W-14 | 第 2 节：删 `recorded_at`；`change_channel_kind` 沿用 `role_versions` 同名 |
| W-15 | 第 3.2 节：索引更名、`isfinite`、触发器参数化 |
| W-16 | 第 3.3 节：岗位状态 `ACTIVE`/`DISABLED` |
| W-17 | 第 3.4 节：离职同事务关闭任职版本 + 一致性测试 |
| S-01 | 第 3.2 节：转给 one-architecture 的状态建模建议 |
| S-02 | 第 2 节末：建议 K-2 承接 |
| S-03 | 第 2 节：`version_audit_entry_id` |
| N-01 | 第 2 节：加 `closed_at`（空变非空一次）；回放用 `closed_at <= T` 判结束日 |
| N-02 | 第 2 节：重新追加行保留原来源，加 `corrected_from_version_id`；按命令取前值读原事实行；一致性测试 |
| N-03 | 第 10 节 5：请求增列 `CORRECTION` 种类及 `EmployeeCorrected` 事件，转 one-architecture |
| N-04 | 第 3.4 节：删「不依赖作业」理由，写窗口界限与判定口径归命令契约 |
| N-05 | 第 3.1 节：`wecom_user_id` 过渡期全唯一，DA-3 随 `external_identities` 重做 |
| N-06 | 第 8 节：命令表、待放行表随版本表同批清除，触发器放行属主 DELETE |
| W-06 出处 | 第 5 节：intent `2026-10-16-090551…` |
| W-08 行号 | 第 5 节：已更正（:23-26、:55；:27、:56-57 不删，:57 改写）；补 `versioned-table-unconditional-writes.txt:3、:9`、`data-classification.txt:19、:22` |
| W-09 | 第 3.6 节：两张归属期间表的列与约束改动、命令表指向 |
| 2.1 C-d | 第 9 节 |
| 2.2 条件 1–6 | 第 3.1 节与第 7 节（用户已同意） |
| 用户 ③ 登记即写版本、按日期读 | 第 0 节整节重写；0.1 读时守卫答 W-02 授予收回；0.2 管理员离职保护在录入时判定；第 3.4 节窗口论述删除 |
| 用户 ② 回填 | 第 3.1 工号唯一；第 3.3 岗位编码；第 6 迁入拒收清单；第 7 用户已定；第 9 org_units 30 天 |
