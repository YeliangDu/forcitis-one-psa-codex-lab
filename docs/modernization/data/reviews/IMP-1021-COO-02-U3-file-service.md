# 文件服务表结构草案 DD 审（IMP-1021-COO-02 单元 3）

- 日期：2026-10-21；审者：one-data；对象：`claude/one-platform-file` @89fe88cb，`docs/modernization/phase1a/file-service/DDL_DRAFT.sql`（schema `platform_file` 十张表）。依据：ADR-0060/0061/0070、ADR-0058、ADR-0066 决定 4、ADR-0033、SESSION_PLAYBOOK §14/§14.1 D1–D19、NAMING_STANDARD、DATA_DESIGN_GATE。
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
