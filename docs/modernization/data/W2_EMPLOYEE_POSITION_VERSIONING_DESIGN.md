# 数据修复批 W2「员工与岗位版本化」数据设计（ADR-0055 决定 2、ADR-0068）——第 2 版

- 作者：one-data，2026-10-21。第 1 版 @ae8e4621；独立复核 `R-W2设计复核-2026-10-21.md`（`claude/one-coordinator-two-lanes` @13dd137e，W-01…W-17、R-01…R-04）；本版按复核改完，末尾有逐条处理表。
- 依据：ADR-0055 决定 2、3；ADR-0068（#493，提议）第 1、2、7 条；用户 U-12（intent `2026-10-16-134434-…U-12-…用人事`）；D6、D17 细则；现行基线 `V20261014131000__organization_workforce_baseline.sql`（main）。
- **状态：数据设计草稿 v2，待 sonnet 改后复核后签发。** 属 ADR-0068（待批）与补篇 B 类待批项的部分标「等答复」，不阻塞其余部分。
- 范围：`employees`、`positions` 版本化；`assignment_versions`、`employee_legal_entities`、`org_unit_legal_entities` 生效依据改 DECLARED 并采用统一更正机制；`assignments` 去除；`legal_entities` 代码规则（LV-1(b)）。`org_units` 根表收窄不在本版（第 9 节）。

## 0. 总体结构：命令、到日、版本分三层

- **人事命令**（`personnel_commands`，ADR-0068 第 1 条，员工聚合内，UUIDv7，所有命令种类一个 ID 空间）是**提前登记与到日生效的载体**（W-02）：生效日不晚于今天的命令在录入的同一事务里执行；晚于今天的保持 `PENDING`，由 workforce 自有的定时作业每日在业务时区零点后执行，**执行时才写版本、发事件**（ADR-0068 第 1 条 4、5）。所以**版本表里没有尚未生效的未来行**——提前登记与撤销尚未生效的登记都只动命令（`WITHDRAWN`），不碰版本。
- **版本表**（本设计）记录已生效的事实，按人事业务生效日取值，**允许补登与更正**（第 2 节的作废机制）。
- **副作用**（关闭授予、吊销会话、F-1 复核、缓存失效）由命令执行事件触发，幂等键 = 命令 ID（事件 `personnelCommandId`），到日作业重放、补跑不会重复处理（并入 A1/B 的命令契约）。

## 1. 生效口径

- **日期**：`effective_from_date`（含）、`effective_to_date`（不含，空表示开放），都是人事业务生效日（`date`）。读取以「业务日期」取当时有效的版本。
- **「今天」与截日**（W-03）：取注入时钟在业务时区（`BusinessZone`，Asia/Shanghai）下的当天日期。离职：人事填写的是**最后工作日**时，SEPARATED 版本的 `effective_from_date` = 最后工作日的次日（业务时区零点）；调岗、入职直接是新状态开始的第一天。授予关闭仍用实际收回时刻（`timestamptz`），不以补登的人事日期回填（ADR-0055 决定 3 第 6 点）。
- **补登落真实日期、更正**：用第 2 节作废机制，**任意区间都可以补登**（包括落在已关闭版本中间、早于最早版本的前史），不再限制「只落在无覆盖区间」。ADR-0068 第 1 条 4 的「生效日早于受影响版本起始日则拒绝」和「跨版本更正延后」，随本设计签发解除（见第 10 节，等 one-architecture 在补篇里改写）。
- **提前登记**：见第 0 节（命令层）。

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
| `superseded_at timestamptz`、`superseding_command_id uuid` | **作废标记**：被更正或补登替换的行，只允许由空变非空**一次**；`superseding_command_id` 同外键 |
| `created_at`、`updated_at` | `created_at` 即记录时刻，由应用传注入时钟（删除第 1 版的冗余 `recorded_at`，W-14） |

- **只追加 + 作废**：版本行的内容列永不改；允许改的只有关闭列（`effective_to_date`、`updated_at`）和作废列（`superseded_at`、`superseding_command_id`），都只能由空变非空一次——触发器放行列清单按 identity 的 `refuse_history_rewrite(...)` 先例做成参数。已关闭的行**仍可被作废**（W-01 的关键）。
- **防重叠只看未作废行**：`EXCLUDE USING gist (employee_id WITH =, daterange(effective_from_date, effective_to_date) WITH &&) WHERE (superseded_at IS NULL)`。唯一约束 `(employee_id, effective_from_date, revision_number)` 保留（作废行与替换行修订号递增）。
- **补登与更正怎么做**（一个命令一个事务）：①把受影响的行标为作废（`superseded_at` = 注入时钟，`superseding_command_id` = 本命令）；②追加覆盖同一区间（受影响区间的并集）的新行，内容为更正后的事实，边界按真实日期；③被拆开的原事实以新行重新追加（带原内容）。
  - 撤销尚未生效的提前登记 = 命令 `WITHDRAWN`，不动版本。
  - 日期登记错了：作废错误行，按真实日期追加。
- **读取**：「当前事实」= `superseded_at IS NULL` 的行按业务日期取；「系统当时认为」的回放（ADR-0034 授权可还原）= 用 `created_at` 与 `superseded_at` 界定（`created_at <= T AND (superseded_at IS NULL OR superseded_at > T)`）。
- 这是全库版本表的统一更正机制，建议 K-2 时态基座承接，日后逐步收掉 `legal_entity_attribution_corrections` 这种覆盖表写法（S-02）。

## 3. 表设计

### 3.1 employees（根表：身份与个人当前值）

| 列 | 去向 | 理由（D17 细则 3 逐列） |
|---|---|---|
| `id` | 留（根） | UUIDv7 |
| `employee_code` | 留（根） | 发号后不变，命令拒改并有测试（同法人代码，D17 细则 8）；**业务唯一待用户定**（W-11，第 7 节） |
| `display_name`、`gender`、`email` | 留（根，当前值） | D17 细则 2 第四类「个人信息最小化只留当前值」；**对「都做维度型」（intent `085331`）的一处收窄，须用户确认，复核已建议进收件箱，确认前本项不算定稿** |
| `employment_status`、`is_fee_earner` | → `employee_versions` | 随生效日变化 |
| `separated_at` | 删 | 离职日 = 开放的 SEPARATED 版本的 `effective_from_date` |
| `login_name` | 删（D2，ADR-0068 第 7 条 1） | A8 登录名；只剩迁入追溯用途，进**来源对照/迁入暂存**，**不**进 `external_identities`（W-12） |
| `wecom_user_id` | 暂留，随 DA-3 的 `platform_integration.external_identities` 落地同批删 | 企业微信登录路径还靠它 |
| `last_sync_run_id` | 删 | 同步退役 |
| `version`、`event_count`、`created_at`、`updated_at` | 留 | 乐观锁、事件序号 |

**豁免的五个条件**（复核 2.2）：①出处=用户收件箱确认（限 `display_name`、`gender`、`email` 三列，`employee_code` 不在其中）；②任何业务规则不按历史日期读这三列，单据需要「当时的姓名」的自存快照；③命令契约：这三列的修改即时生效，拒绝带未来日期（交 one-architecture）；④每次修改写审计，`before_text`/`after_text` 按 ADR-0043 第 4 项只记 L2 原值，`employee_code`（L3）不记原值；⑤注册表逐列写「个人信息最小化只留当前值」并附出处，`email` 离职满 180 天置空作业登记 `job`；三列登记 INTERNAL + PI（理由以「用途：」开头）；`ddl-naming-baseline.txt` :4-5 的 `email_address`、`gender_code` 改名在本批一起办。

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
    is_fee_earner           boolean     NOT NULL,
    change_channel_kind     text        NOT NULL,
    change_command_id       uuid,
    version_audit_entry_id  uuid,
    superseded_at           timestamptz,
    superseding_command_id  uuid,
    created_at              timestamptz NOT NULL,
    updated_at              timestamptz NOT NULL,
    CONSTRAINT pk_employee_versions PRIMARY KEY (employee_version_id),
    CONSTRAINT fk_employee_versions_employee_id FOREIGN KEY (employee_id) REFERENCES organization_workforce.employees (id) ON DELETE RESTRICT,
    CONSTRAINT fk_employee_versions_change_command_id FOREIGN KEY (change_command_id) REFERENCES organization_workforce.personnel_commands (personnel_command_id) ON DELETE RESTRICT,
    CONSTRAINT fk_employee_versions_superseding_command_id FOREIGN KEY (superseding_command_id) REFERENCES organization_workforce.personnel_commands (personnel_command_id) ON DELETE RESTRICT,
    CONSTRAINT uk_employee_versions_revision UNIQUE (employee_id, effective_from_date, revision_number),
    CONSTRAINT ck_employee_versions_range CHECK (effective_to_date IS NULL OR effective_to_date >= effective_from_date),
    CONSTRAINT ck_employee_versions_finite CHECK (isfinite(effective_from_date) AND (effective_to_date IS NULL OR isfinite(effective_to_date))),
    CONSTRAINT ck_employee_versions_employment_status CHECK (employment_status IN ('EMPLOYED', 'SEPARATED')),
    CONSTRAINT ck_employee_versions_revision_number CHECK (revision_number >= 0),
    CONSTRAINT ck_employee_versions_change_channel_kind CHECK (change_channel_kind IN ('COMMAND', 'MIGRATION')),
    CONSTRAINT ck_employee_versions_command CHECK ((change_channel_kind = 'COMMAND') = (change_command_id IS NOT NULL)),
    CONSTRAINT ck_employee_versions_audit CHECK (change_channel_kind <> 'COMMAND' OR version_audit_entry_id IS NOT NULL),
    CONSTRAINT ck_employee_versions_superseded CHECK ((superseded_at IS NULL) = (superseding_command_id IS NULL)),
    CONSTRAINT ex_employee_versions_overlap EXCLUDE USING gist (employee_id WITH =, daterange(effective_from_date, effective_to_date) WITH &&) WHERE (superseded_at IS NULL)
);
CREATE INDEX ix_employee_versions_change_command_id ON organization_workforce.employee_versions (change_command_id) WHERE change_command_id IS NOT NULL;
CREATE INDEX ix_employee_versions_superseding_command_id ON organization_workforce.employee_versions (superseding_command_id) WHERE superseding_command_id IS NOT NULL;
CREATE INDEX ix_employee_versions_separated_effective_from_date ON organization_workforce.employee_versions (effective_from_date) WHERE employment_status = 'SEPARATED' AND superseded_at IS NULL;
```
- 触发器 `tg_employee_versions_append_only`（BEFORE UPDATE OR DELETE）：只放行 `effective_to_date`、`updated_at`、`superseded_at`、`superseding_command_id` 四列由空变非空一次（关闭列 `updated_at` 例外可变），其余拒；TRUNCATE 语句级触发器拒；`one_db_roles_schemas.sql` §4b 回收 DELETE、TRUNCATE，**清理通道**见第 8 节。
- `employment_status` 现只有 EMPLOYED、SEPARATED；停薪留职、试用转正按 B7 不先写；**数据侧建议转 one-architecture**：`employment_status` 只表达雇佣关系，停薪留职另设 `leave_status`、试用另设 `employment_phase` 作为独立列（行业通行的「工作状态与请假分开」；identity 现有按 EMPLOYED 判定的逻辑不用跟着变），裁定后基线开放期加列。
- 管理员离职暂停期间（A2）：按 ADR-0068 第 2 条，离职命令转 `HELD`，员工保持在职、**不写 SEPARATED 版本**，放行时按原生效日写版本（经第 2 节补登机制）；因此本表在暂停期间没有 SEPARATED 行。

### 3.3 positions 与 position_versions

- 根表 `positions` 留：`id`、`created_at`、`updated_at`、`version`；`code`、`name`、`sort_order`、`status` 进版本表（与 `org_unit_versions` 同法）。
- `position_versions`：`position_version_id`、`position_id`（外键）、第 2 节通用列、`code`（可空，长度 ≤ 500 非空白）、`name`（≤ 500 非空白）、`sort_order`、`status`。
- **岗位状态（W-16）**：取值 `ACTIVE`、`DISABLED`；**不再沿用 `DELETED`**（那是同步「组织源删除或不再返回」的语义，基线 :19）。ADR-0068 第 4 条岗位只做新建与停用，与之对齐；岗位命令的归属表等 one-architecture（`change_command_id` 在命令表确定前，`COMMAND` 行不可写，只有 `MIGRATION` 行）。
- 岗位编码是否全局唯一：待用户定（第 7 节）。

### 3.4 assignment_versions 与 assignments 去除（复核 2.1，结论：去除）

- 去除 `assignments`：自有、可未来生效的任职不存当前投影（D17 细则 5）；到日作业只触发副作用，不写投影。
- `assignment_versions` 加第 2 节通用列；约束搬入：`position_id` 对 `COMMAND` 行必填（`CHECK (change_channel_kind <> 'COMMAND' OR position_id IS NOT NULL)`，迁入行允许空，请人事确认）、`reports_to_employee_id` 不得等于本人（`CHECK (reports_to_employee_id IS DISTINCT FROM employee_id)`）。
- **索引（W-04、C-a）**：`(org_unit_id, effective_from_date)` 部分索引 `WHERE superseded_at IS NULL`；`reports_to_employee_id` 部分索引（`IS NOT NULL AND superseded_at IS NULL`）；gist 防重叠索引服务按员工取日期。
- **时点（C-b）**：「当前」取注入时钟按业务时区截日；按时刻检查的授权（`timestamptz`）先换成业务日期再查任职版本。**缓存（C-c）**：以业务日期为键、命令提交后失效；也可不加，1,500 人规模按索引直接查。
- **读取改写代价**列入 PR：`OrgSnapshotRows.SELECT_STAFF`（`employees` 内连接 `assignments`）、`OrgDirectoryService` 的 `findEmployee`、`findSupervisor`、`employmentOf` 改为按业务日期读取，「已有员工版本但当日还没有任职版本」的人明确处理（目录返回「尚未任职」）；测试夹具 `AccountsAcceptanceSupport.java:251、286、300`、`OwnIdColumnsIT.java:48-51` 改写版本表，删除测试条目在 PR 单列理由（CLAUDE.md 第 5 条）。
- **退职时的任职**（W-17）：离职命令执行时，同一事务内关闭该员工开放的任职版本（`effective_to_date` = SEPARATED 版本的 `effective_from_date`）；加跨表一致性集成测试。

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
- **保留期（R-2）**：`RETENTION_PROPOSAL.md` 第 14–16 行用户已定：`employees` 姓名、工号随引用存续；邮箱等离职满 180 天置空；**任职版本等个人任职史版本表在员工离职满 10 年后删除该员工的行**。所以 `employee_versions`、`assignment_versions` 登记 `PERMANENT` 并写理由与出处，**并有删除通道**（第 8 节）；`position_versions` 无个人信息，永久。`employees` 根表保留策略从 `CURRENT_STATE` 改为上述口径，清除作业登记 `job`。
- 本批同批登记清单（W-08）：`ddl-naming-baseline.txt` :4-5（改名）、:23-27（`assignments`）、:55-57（`assignments`/`employees` 相关索引）；`data-classification.txt` :20-21（`login_name`/`wecom_user_id` 随列删除）；注册表 :55-86、:91-103（role、aggregate、lineage、`fk_exceptions` :79 的 `last_sync_run_id`）；`aggregates.yaml`；`one_db_roles_schemas.sql` §4b 加三张新版本表；测试夹具（3.4）。

## 6. 迁入（ADR-0036、ADR-0054 一次投产）

- 每名员工写一条 `MIGRATION` 初始版本，`change_command_id` 空；`effective_from_date` 口径要生产证据（A8/老系统有无入职日、离职日，取不到用迁入日并标左截断），由 E3 与 one-ops 取证给出，本设计不预设取值，但提出前置条件（第 4 节末）。
- 重复工号、岗位编码重复、`login_name`（进来源对照）与企业微信 ID（进 `external_identities`）的迁入核对，归 E3；**重复工号走拒收与处置（M6），不为迁入放宽约束**。

## 7. 待用户定（收件箱，经 one-coordinator）

1. `display_name`、`gender`、`email` 只留当前值、改动进审计、不建历史版本（推荐），还是也做历史版本（复核 2.2 已建议进收件箱，限这三列）。
2. 工号全局唯一、复职沿用、发号后禁改（推荐）；岗位编码是否全局唯一（主卡第 5 节待决 4）；迁入的重复工号走拒收与处置。

## 8. 版本表删除通道（R-2 的执行）

- 按员工离职满 10 年删除其版本行（`RETENTION_PROPOSAL.md` 第 16 行）：与 ADR-0058 同法，`SECURITY DEFINER` 函数 `organization_workforce.purge_employee_history(p_cutoff date)`，属主角色执行，触发器放行 `current_user = 属主` 的 DELETE（运行角色无权）；只删整名员工到期的全部版本，同语句删完，复合外键不触发。清理作业另批，期限首次生产部署后 30 个月内，负责人 one-1a-par（同 ADR-0034 的 D-1 写法），registry 先写 `PERMANENT` + 注释。
- 触发器、权限、函数模板与 `state_transitions` 的公共卡共用（若 ADR-0058 实施恢复）。

## 9. 不在本版（带负责人与触发点）

- **`org_units` 根表收窄**：根表仍存当前值，同步撤销后没有写入方；提前改隶会让「按日期取任职、按当前取组织树」算错子树（复核 C-d）。触发点：one-architecture 答复补篇第 4 条（组织单元命令）；负责人：one-data 出收窄设计；在此之前，范围、权限相关读取对组织树仍用当前值，这一已知风险登记在本设计。
- **转正落点、停薪留职状态**：等 A3；**岗位命令表**：等 A4。

## 10. 等 one-architecture 的字面冲突（W-07）

1. ADR-0034:272「依据同步观察日（OBSERVED）」改为 DECLARED（ADR-0068 第 6 条已裁，待批准）。
2. ADR-0058 对版本表的「自身即历史」豁免需逐行登记评审：只有版本行带齐 ADR-0058 第 1 条等价字段才成立——业务日期（`effective_from_date`）、记录时刻（`created_at`）、操作者（审计条目 ID）、命令 ID、原因（更正 = 作废标记）。本设计的版本行已带齐，豁免成立的前提是 ADR-0058 状态转换表为这三张表登记例外。
3. ADR-0068 第 1 条 4 的补登拒绝规则与跨版本更正延后，本设计签发后解除。
4. 管理员离职暂停（A2）：ADR-0068 第 2 条已选「暂停不写版本」（乙），由第 2 节机制支撑。

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
| 2.1 C-d | 第 9 节 |
| 2.2 条件 1–6 | 第 3.1 节与第 7 节 |
