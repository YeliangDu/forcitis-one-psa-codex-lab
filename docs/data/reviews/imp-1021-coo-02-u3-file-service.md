# 文件服务表结构草案 DD 审（IMP-1021-COO-02 单元 3）

> 冻结（2026-11-04，#1263）：复审记录，不再更新；表结构见 Flyway 中 `platform_file`。

- 日期：2026-10-21；审者：one-data；对象：`claude/one-platform-file` @89fe88cb，`docs/modernization/phase1a/file-service/DDL_DRAFT.sql`（schema `platform_file` 十张表）。依据：ADR-0060/0061/0070、ADR-0058、ADR-0066 决定 4、ADR-0033、DATA_DESIGN_CHECKLIST §14/§14.1 D1–D19、NAMING_STANDARD、DATA_DESIGN_GATE。
- 范围：只审草案的数据设计；草案未进 `db/migration`，实际 DDL 出来后我再复核实际表结构 diff。

## 结论

**数据设计：需修改。** 底子好：十张表主键 UUIDv7、命名与具名约束到位、内容列不可变由触发器兜底、保存期限表带四眼、封存与例外清单追加、查询路径多有索引。但有 5 项必须改（下表高档），改完再送。Q1–Q10 的答复在末节。

## 必须改（阻断签发）

| # | 位置 | 问题 | 改法 |
|---|---|---|---|
| H1 | 全 schema | 四张表有状态列（`file_status`、`judgment_status`、`request_status`、`confirmation_status`），schema 内无 `state_transitions`（ADR-0058、M3），`stored_files` 触发器也不限状态流转，`DISPOSED` 之后可被改回 `AVAILABLE` | 建 `platform_file.state_transitions`（形状按 ADR-0058 / `STATE_TRANSITIONS_DESIGN.md`），四个状态列的合法流转各登一组；三张可变表的触发器拒绝非法流转（状态是终态后任何改动被拒） |
| H2 | `preservation_category_versions` | 配置型版本表，但：无根表（注册表 `CONFIG_VERSIONED` 须 `versions_table` 挂根，同 `numbering_rules`）；作者与时间列叫 `created_by`/`created_at`、出处叫 `decision_ref_key`，与 D15/M6 列名（作者 `author_id`/`authored_by`、`authored_at`、`confirmed_by`、`confirmed_at`、`decision_ref`、`effective_from_date`）不一致；`PENDING→CONFIRMED` 要改行但没有触发器限定只能改确认列 | 建根表 `preservation_categories`（`category_code` 业务键，G-DDL1，唯一约束；`category_name` 放根表，版本表不再重复）；版本表外键引根；列名对齐 M6；加触发器：只允许 `PENDING→CONFIRMED` 且只改确认三列（形状仿 `numbering_rule_versions`），禁删禁清空；生效日须不早于确认日（版本号 >1 时），同日修订按 `version_number`（D6） |
| H3 | `stored_files.migration_batch_id` | M5/ADR-0033：业务表不加批次列，迁入批次只挂来源对照；该列还不可变、无外键 | 删列；迁入文件的批次与老系统来源走 `platform_integration.source_references`（来源对照指向 `stored_file_id`） |
| H4 | 三个「主体键」列 `uploader_key`/`bound_by_key`/`held_by_key`（Q4） | 用 `kind + text key` 混存用户 uuid、链接 uuid、模块 id，无法外键、无法按主体查询、违背「对象列二选一、不拼文本键」（SD-1 同款要求） | 拆列：`*_user_id uuid`、`*_link_id uuid`、`*_module_code text`（格式 CHECK），具名 CHECK 按 `*_kind` 恰好一个非空；`kind` 保留。跨 schema 不建外键（用户、链接在别的 schema）。见 Q4 |
| H5 | `file_preservations`（Q6） | 「最新一行为当前」靠 `recorded_at DESC`：同一时刻两行或时钟回拨就读错；到期作业按 `expiry_date` 局部索引会扫到被取代的旧行并把它们当作到期（删除依据！）；「一个文件一个类别与起算单据」没有库内保证 | 加 `preservation_seq integer NOT NULL`（每文件从 1 递增，`UNIQUE (stored_file_id, preservation_seq)`），当前行 = 最大序号；建只读视图 `current_file_preservations`（`security_invoker`），到期作业和展示只读视图；加插入触发器：序号必须等于前一最大序号 + 1，且类别编码（经版本取根）与 `anchor_*` 与上一行相同；注册表 APPEND_ONLY，并写明读当前行只许走视图 |

## 应改（中档，随同一轮改）

| # | 位置 | 问题 | 改法 |
|---|---|---|---|
| M1 | `stored_files.object_key` | CHECK 只验形状，不验末段 uuid 等于 `stored_file_id`，键可指向别人的对象 | 加 `CHECK (object_key LIKE '%/' \|\| stored_file_id::text)`（触发器的 staging→files 改键同样保持末段不变） |
| M2 | `stored_file_names` | 去标识那次 UPDATE 不限 `original_name` 的新值，可改成任意文本；去标识后无定值 | 加 CHECK：`is_deidentified` 时 `original_name` 为固定占位值（例如 `[已去标识]`）、未去标识时不是；触发器只放行「置位 + 占位值」；去标识作业登记（D12），在注册表写保留策略 |
| M3 | `file_status` 取值 | `DISPOSED` 无对应处置记录表（Q7 推迟），审计链断 | 本轮 CHECK 去掉 `DISPOSED`，与 `file_disposals` 同一迁移加回；`EXPIRED_UNBOUND` 保留 |
| M4 | `scan_exceptions` / `false_positive_judgments` | `content_hash` 与 `stored_files` 无库内一致性；`scan_exceptions` 能指向 `REJECTED`/`PENDING_REVIEW` 的判定；例外清单只追加但病毒误判后无法撤销 | 复合外键：`stored_files` 加 `UNIQUE (stored_file_id, content_hash)`，判定表 `(stored_file_id, content_hash)` 引它；判定表加 `UNIQUE (false_positive_judgment_id, judgment_status)`，例外表加 `judgment_status` 列（CHECK = `'CONFIRMED'`）并以 `(判定 ID, 状态)` 引判定表（复核后状态不再变，由 H1 触发器保证）。撤销例外：加追加表 `scan_exception_revocations`（谁、何时、原因，两人规则）或在例外表加撤销列并受控单次更新——请 one-platform-file 与 one-security 选一，二者其一必须在本轮出现，否则「只追加」等于永久放行 |
| M5 | `quarantine_read_requests` | 批准后没有失效期，批准可以无限期留着等人读 | 加 `approval_expires_at timestamptz`（批准时必填，具名 CHECK 与状态联动），读取时超期拒绝 |
| M6 | 触发器与注释 | `file_bindings`、`file_holds`、`scan_exceptions`、`file_preservations`、`preservation_category_versions` 的只追加触发器、表列注释写作「略」或未写（`file_bindings` 注释「同 numbering.legacy_numbers 写法（略）」） | 实际 DDL 须逐表逐列有中文注释，只追加表各有 `BEFORE UPDATE OR DELETE` 与 `BEFORE TRUNCATE` 触发器（形状同 main 上 `refuse_version_rewrite`）；这条在实际 diff 复核时逐项核 |
| M7 | 注册表、分级、保留（D1、D12、D13、R-2） | 草案没有给出十张表的登记 | 表结构 PR 须带：注册表十行（`stored_files` 用 `CONDITIONAL_UPDATE` + `condition`，根表带 `business_key`）、`data-classification.txt` 十表分级（`original_name` 为 INTERNAL + PI；`uploader_user_id`、`bound_by_user_id`、`held_by_user_id`、`judged_by` 等人员标识同按 ADR-0043 逐列定）、保留策略（`stored_files` 与关联表按 PERMANENT 并注明「人员类标识随 RETENTION_PROPOSAL，清理作业另卡」，同 `source_references` 的写法；去标识作业登记名） |

## 可后改（低档，实际 diff 时顺带）

- `hold_reason`、`judgment_reason`、`review_reason`、`request_reason` 自由文本有长度上限，但可能夹带个人信息：分级写 INTERNAL + PI，文字说明「不填个人信息」；封存原因建议改原因代码 + 可选说明。
- `file_bindings.access_level` / `personal_info_mark` 是「每次关联」的分级，同一文件被两单据关联且分级不同时，下载取较严者，请在 api 契约和列注释写明。
- 同一事务内 `stored_files.object_key` 改键与插入 `file_bindings` 的顺序由领域方法保证，加集成测试。

## Q1–Q10 答复

| Q | 答复 |
|---|---|
| Q1 单据引用形状 | **通过**：`document_type text` + 格式 CHECK + `document_id uuid`、无跨 schema 外键，符合 ADR-0066 决定 4（基础层不依赖 numbering）与 REG-1 在 numbering 之外的形状。注意 DB 不验类型码是否登记，由调用方负责并测试；格式 CHECK 与 REG-1 一致（`^[A-Z][A-Z0-9_]{1,39}$`）。 |
| Q2 `media_type_code` | **通过**：白名单在配置、会变，用 `_code` + 格式 CHECK 符合 G-DDL7。 |
| Q3 `object_key` | **通过并加 M1**：`_key` 合 NAMING；格式 CHECK 写死环境前缀字符集有风险，one-ops 定字符集后若有变化追加迁移改 CHECK（ADR-0038），不放宽成无约束。用语交 one-glossary 登记（使用范围领域，bc `platform_file`）。 |
| Q4 主体标识列 | **拆列**，见 H4。 |
| Q5 `personal_info_mark` 含 SPI | 不留死取值：U-14 定前 CHECK 只列 `NONE`、`PI`；U-14 定后追加迁移加 `SPI`。与 Java 枚举不一致时以「数据库当前允许值」为准，应用拒收 SPI 的测试保留。 |
| Q6 当前行读法 | 见 H5：序号 + 视图 + 触发器，注册表 APPEND_ONLY。 |
| Q7 `file_disposals` | 同意到期前 90 天再建，但 `DISPOSED` 取值与它同批进来（M3）。 |
| Q8 隔离保存期取证延长 | 同意本稿不建延长表；one-ops 定方案后若需要表，走新卡新审。 |
| Q9 加密拒收、不可用不建行 | 同意。 |
| Q10 `stored_files.version` / `updated_at` | 同意保留：`object_key` 改键与 `file_status` 会变；`version` 乐观锁的 `DEFAULT 0` 沿用基线惯例。 |

## 下一步与签发

1. one-platform-file 按 H1–H5、M1–M7 改草案，送回；我复核后签「数据设计：通过（one-data，日期，依据 …）」，之后才出迁移脚本（等 REG-1 合入）。
2. 迁移脚本出来后，我复核实际表结构 diff（含触发器、注册表、分级、保留），再签表结构 PR。
3. 本审不是「有条件通过」：以上高档任一项未改，不签。

## 第二轮（2026-10-21，复核 `claude/one-platform-file` @ef0fdf32 的草案第 2 版）

**结论：数据设计：需修改**（还差 1 项高档、2 项中档，改完即可签；不做有条件通过）。第 1 版的 H2–H5、M1–M7 逐项核过已改对：根表与列名对齐 D15/M6、批次列已删、主体列拆开且按 kind 恰好一个非空、`preservation_seq` + 插入触发器 + 当前行视图、判定/例外/撤销的复合外键与追加撤销表、`approval_expires_at`、只追加表触发器与注释、`personal_info_mark` 只列 NONE/PI。

| # | 级别 | 问题 | 改法 |
|---|---|---|---|
| N1 | 高 | H1 只做一半：草案只在注释里说「`state_transitions`（模板）」，没有 `CREATE TABLE platform_file.state_transitions`，也没有写出四个状态列各自的合法流转清单与登记的系统操作者 | 草案里补完整 DDL（形状按 `STATE_TRANSITIONS_DESIGN.md` v2，含 `actor_type`、`cause_kind` 等约束、追加触发器）；补一张流转清单（stored_files：AVAILABLE→EXPIRED_UNBOUND；判定：PENDING_REVIEW→CONFIRMED/REJECTED；读取申请：PENDING→APPROVED/REJECTED、APPROVED→READ；版本：PENDING→CONFIRMED）；系统操作者登记到 `system-actors.txt`（P1） |
| N2 | 中 | `preservation_category_versions` 的生效日不早于确认日只在 `UPDATE`（确认）触发器里查；直接 `INSERT` 一条 `CONFIRMED` 行（`CHECK` 允许）可绕过它，也绕过「先待确认再确认」 | 加 `BEFORE INSERT` 触发器：插入的行只能是 `PENDING`；种子数据（初始版本）也先以 `PENDING` 插入再由人确认，`author_id` 为空只用于迁移脚本写入的待确认初始版本，注释写明 |
| N3 | 中 | 注册表写法与 main 不一致：草案把根表写成 `CONFIG_ROOT`、版本表写成 `CONFIG_VERSIONED`；main 的约定（`numbering_rules` / `numbering_rule_versions`）是**根表**记 `role: CONFIG_VERSIONED` + `versions_table`，版本表记 `AGGREGATE_CHILD`；`CONFIG_ROOT` 不是注册表的合法 role。扫描脚本 M6（#504）按 main 的约定找版本表 | 登记按 main 约定写；版本表 mutability `CONDITIONAL_UPDATE`（仅确认步） |

提醒（不阻断）：视图用 `security_invoker` 需 PostgreSQL 15 及以上，请迁移脚本验证实例版本（INFRA 记录）；种子版本的确认人由谁担任交 one-platform-file 与用户定。

**P1–P3 答复**：P1 可行——一次性链接与平台模块的状态转换按 `actor_type = SYSTEM` + `system-actors.txt` 登记系统操作者（`STATE_TRANSITIONS_DESIGN` 第 83 行同款），上传主体本身另存在 `stored_files` 的主体列，不丢身份。P2 同意，写进 api 契约注释。P3 同意：业务日按 `Asia/Shanghai`，与 ADR-0044 一致；触发器里写死时区，如将来多时区再迁移。

## 第三轮（2026-10-21，复核 `claude/one-platform-file` @d196bd31 的草案第 3 版）

N1–N3 逐项核过：`platform_file.state_transitions` 的 DDL 与 `STATE_TRANSITIONS_DESIGN.md` v2 模板逐行比对一致；四个状态列各有流转清单，终态写明；三个系统操作者（一次性链接持有人、平台模块调用、临时区到期作业）给了 UUIDv5，待写入 `system-actors.txt`；版本表加了 `BEFORE INSERT` 只许 `PENDING`，种子也先待确认；注册表根表 `CONFIG_VERSIONED` + `versions_table`、版本表 `AGGREGATE_CHILD`；PG15+ 已注明。

数据设计：通过（one-data，2026-10-21，依据 DATA_DESIGN_GATE 表结构草案项、ADR-0058、STATE_TRANSITIONS_DESIGN v2、DATA_DESIGN_CHECKLIST §14.1 D1–D19 逐项核对草案第 3 版）。

范围与后续（均为表结构 PR 阶段的实际 diff 复核项，不是对本次签发的条件）：
1. 草案未在数据库上执行，语法与触发器行为以 Testcontainers 结果为准；实际迁移脚本出来后我复核实际表结构 diff。
2. 注册表行：`preservation_category_versions` 要写 `mutability: CONDITIONAL_UPDATE`（仅确认步），扫描脚本 M6（#504）按此判定。
3. `system-actors.txt` 三行与守护测试随表结构 PR 提交；`data-classification.txt` 十二张表分级（见草案第 9 节）。
4. 签发只覆盖本草案的数据设计；单元 6 的集成测试（改键与插关联顺序、触发器非法流转被拒）由实现方补。

## 第四轮（2026-10-21，复核草案第 4 版 `claude/one-platform-file` @01119018）

与已签 v3 的差异逐行比对，只有一处设计变化（对象键建行即写定、触发器禁改，对齐 ADR-0060 决定 3、ADR-0070 决定 2），其余行不变：
- `ck_stored_files_object_key` 去掉 staging 键形，月份收紧为 `01–12`，末段仍等于本行 ID，隔离键与 `file_status` 的联动 CHECK 不变；
- `guard_stored_file_change` 的可改列只剩 `file_status`（按流转清单）、`updated_at`、`version`，`object_key` 在不可变之列，去掉 staging→files 分支；
- `ix_stored_files_staging`（依赖 staging 键形）换成 `ix_stored_files_available_stored_at`（`WHERE file_status = 'AVAILABLE'`），临时区过期作业按「可用且无关联」用 `file_bindings` 的 `stored_file_id` 前导唯一索引做反连接，路径有索引覆盖（D19）；
- 注册表 condition 改为「只有 file_status 按流转清单可改」。

数据设计：通过（one-data，2026-10-21，依据 v3 签发范围不变，第 4 版变更逐行核对；ADR-0060 决定 3、ADR-0070 决定 2）。表结构 PR 阶段仍复核实际 diff；「正式区孤儿只读核对作业」属单元 6，实现时我复核其只告警、不删。

## 第五轮（2026-10-21，单元 3 opus 首轮后的三处偏差）

1. **例外清单可挂错哈希**：同意并谢谢补上我签发版的缺口。判定表加 `UNIQUE (判定 ID, 判定状态, 内容哈希)`，例外表用三列复合外键引它（状态列限 `CONFIRMED`）。判定复核后状态不再变（触发器保证），所以复合外键不会被事后改状态绕开。**通过**。
2. **`state_transitions` 的 `from_status`/`to_status` 由正则改为取值并集 CHECK，另加按聚合成对限定的 CHECK（`ck_state_transitions_status_of_aggregate`），`aggregate_type_code` 限四值**：同意——DdlNamingTest 要求 `_status` 列有取值 CHECK，这是模板在各 schema 的合法差异（各 schema 取值不同，形状不变）；成对限定比并集更严，保留。模板文档 `STATE_TRANSITIONS_DESIGN.md` 的「列名更正」节补一句：各 schema 的状态取值并集与聚合类型取值由该 schema 的流转清单决定，其余列与约束逐字一致。**通过**。
3. **人员 ID 列不登记 PI**：按 ADR-0043 第 50 行「代理键列（kernel 强类型 ID / UUID）不登记，出口与分析层按 NONE 处理」，`*_user_id`、`judged_by`、`reviewed_by`、`requested_by`、`decided_by`、`author_id`、`confirmed_by`、`revoked_by` 都不登记（法律属性仍是个人信息，ADR-0043 第 97 行，但不改登记）。我在第 1 轮 M7 写的「人员标识按 ADR-0043 逐列定」，答案就是不登记，该处更正；`IdempotentEndpointGuardTest` 红灯的根因是登记会把编号规则端点的 `authorId`/`confirmedBy` 也判为直接标识，不登记就不会误报，**不要为此改守护**。仍须登记的：`stored_file_names.original_name` 和自由文本列（`hold_reason`、`judgment_reason`、`review_reason`、`request_reason`、`revocation_reason`）。
4. 其余补充（判定与读取申请插入只许初始态、待确认版本不得写到期日、`FOR NO KEY UPDATE`、`read_at ≥ decided_at`、视图 REVOKE 写权限）：同意，属于收紧。

数据设计：通过（one-data，2026-10-21，依据第 5 轮三处偏差逐项核对；ADR-0043 第 50 行、ADR-0058、DATA_DESIGN_GATE）。实际表结构 diff 出来仍复核，命名改名（`access_level_code`、`personal_information_*`、`preservation_sequence_number`、`*_version_code`、`decision_ref_key`）一并看。

## 第六轮（2026-10-21，复核表结构 PR #521 @5bb81155 的实际表结构 diff）

对象：`V20261021120000__platform_file_baseline.sql`（716 行，十二张表加一个视图）、`data-access-registry.yaml` 十三行、`data-classification.txt` 六行、`system-actors.txt` 三行、授权脚本、`FileServiceSchemaIT`。逐表对照第 4 版签发稿与第 5 轮偏差：

**对得上的**：十二张表的列、具名约束、复合外键（判定 → 存储文件三列、例外 → 判定三列、撤销 → 例外）、主体拆列且按 kind 恰好一个非空、`object_key` 建行写定且触发器禁改、去标识固定占位值、版本表根表 + `BEFORE INSERT` 只许 `PENDING` + 确认触发器、`preservation_sequence_number` + 插入触发器 + `current_file_preservations`（security_invoker）、`approval_expires_at`、`read_at ≥ decided_at`、`FOR NO KEY UPDATE`、待确认版本不得写到期日、只追加表各有 `UPDATE/DELETE` 与 `TRUNCATE` 两个触发器、授权脚本对只追加表 REVOKE 改删、视图 REVOKE 写。人员 ID 列未登记 PI，仅 `original_name` 与五个理由列登记，符合第 5 轮。

**必须改（一项）**：

| # | 位置 | 问题 | 改法 |
|---|---|---|---|
| F1 | `platform_file.state_transitions` | 序号列仍叫 `sequence_number`；第 5 轮之后 one-glossary 指出它与词条「流水号」（`sequence_number`，`platform.numbering`）同名，G39 会要求列注释以「流水号」开头，已定名 `transition_sequence_number`（见 `STATE_TRANSITIONS_DESIGN.md`「再更正」） | 列、`uk_state_transitions_aggregate_id_transition_sequence_number`、`ck_state_transitions_transition_sequence_number`、`ck_state_transitions_first`、测试、注册表注释里的旧名全部改；`preservation_sequence_number` 不受影响（已带限定词） |

**提醒（不阻断，由 one-coordinator 的复审关注）**：
1. `DataArchitectureTest` 被改：TRUNCATE 被外键拦下（`0A000`）时改用 `CASCADE` 重试，并新增「表自己的语句级 TRUNCATE 触发器必须存在」。方向是收紧，不是放宽，但属于守护代码变更，PR 说明里需单列。
2. `preservation_categories`（根表）的 `category_name` 可被运行角色 UPDATE，名称不在版本化范围内；这是我在第 1 轮建议把名称放根表时留下的取舍，保留，但名称变更不进版本历史，若业务要求名称变更可追溯，另卡加版本列。
3. 注册表 `state_transitions` 的保留期 `PERMANENT` 带「用户批准保留期后再定」的注释，同其他 schema 做法。

结论：**数据设计（表结构 PR）：未通过，仅差 F1**。F1 改完推送后，我只复核该改动的 diff，通过即签「表结构 PR：通过」；不另做有条件通过。

## 第七轮（2026-10-21，复核 F1 改动 @9421b798）

只看 F1 的 diff：`state_transitions` 序号列、`uk_…_transition_sequence_number`、`ck_…_transition_sequence_number`、`ck_state_transitions_first` 与列注释（「转换序号：」）都已改，迁移里没有残留旧名，`preservation_sequence_number` 未动；术语提议同 PR 登记。

表结构 PR（#521）：通过（one-data，2026-10-21，依据 DATA_DESIGN_GATE 表结构项、实际 diff 第六轮与 F1 复核）。
