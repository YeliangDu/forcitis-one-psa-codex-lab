# B-31（organization.administration）物理承载裁定

- 日期：2026-10-21；作者：one-data；依据：one-1a-admin 的 `B31_ALIGNMENT_AUDIT.md`（claude/one-1a-admin-skeleton @d2f0b69d）DATA-01–11、GLO-09，`B31_CONSTRUCTION_DESIGN.md` §3、§10、§11，`B31_TERM_GAPS.md` §2.1；SESSION_PLAYBOOK §14、§14.1 D1–D19；ADR-0058；DATA_DESIGN_GATE。
- 性质：本文定**物理承载的规则与形状**，不是 DDL 签发。7.5（AGG-114）、7.8（AGG-116）开表前仍须带 DDL 草案送审，我逐表签「数据设计：通过」；7.4、7.6 同理。未出 DDL 之前，下列裁定是 DDL 草案的验收口径。
- schema：`organization_administration`；表名按词表（B31_TERM_GAPS §2.1 M1、M2、M5，已批准的采用批准名；M6 等 one-glossary 未裁的名字，裁后跟随，不阻塞结构）。

## 1. 命名（回答表名问题）

| 设计草案 | 采用 |
|---|---|
| `seal_applications`、`seal_application_documents`、`seal_application_source_documents` | `seal_usage_requests`、`seal_usage_documents`、`seal_usage_source_documents` |
| `seal_usage_records`（实际登记） | 取 one-glossary 对 `seal_usage_registration`/`seal_usage_record` 的裁定；结构不依赖名字 |
| `it_requests`、`it_request_handlings` | `it_service_tickets`、`it_service_ticket_handlings` |
| `it_asset_use_ledgers`、`it_asset_use_requests`、`it_asset_use_periods` | `it_asset_usage_records`（根）、`it_asset_usage_requests`、`it_asset_usage_periods` |
| `fixed_asset_card_id` | `company_asset_id`（M7），跨 schema 只存类型化 ID，不建外键 |
| 「兜底」列 | 用 `backup_approver_role_code`（M8 暂名，跟随 one-glossary 裁定；列名只含词表用语） |
| `decision_ref` | 见第 6 节 |

## 2. DATA-05 配置型版本表：写边界与表名

**裁定：不挂在任何聚合下**，D17 细则 4（一张版本表一个来源、一个负责人）。四类配置加预盖上限、提醒参数共用一对根表与版本表头，各配置的内容行各一张条目表：

- `config_sets`（根，`role: CONFIG_VERSIONED`，`versions_table: …config_set_versions`）：`config_set_code`（业务键，格式 CHECK；取值 `SEAL_APPROVERS`、`IT_SERVICE_CATEGORIES`、`POSITION_ASSET_STANDARDS`、`EMPLOYEE_ACTIVITY_TYPES`、`REMINDER_RULES`）、`owner_role_code`（该配置的负责角色，写边界落这里：行政负责人 / IT 负责人 / 员工关系负责人）、通用审计与乐观锁列。每个配置集一行，由迁移种子写入。
- `config_set_versions`（版本头，K-2 形状，同日历版本表）：`config_set_id`、`config_set_code`（冗余，复合外键保证条目类型）、`version_number`、`effective_from_date`、`effective_to_date`（只能把开放版本关闭一次）、`confirmation_status`（`PENDING`/`CONFIRMED`）、`author_id`、`authored_at`、`confirmed_by`、`confirmed_at`、`decision_ref_key`、`decision_audit_entry_id`；四眼 CHECK（确认人≠作者）、待确认至多一个（部分唯一索引）、`BEFORE INSERT` 只许 `PENDING`、确认触发器只改确认列（形状同 #521 的保存类别版本）；`version_number > 1` 时生效日不早于确认日；已确认版本区间排他约束；`UNIQUE (id, config_set_code)`。
- 条目表（只在版本待确认时可插入，确认后拒绝；追加表触发器；`(config_set_version_id, config_set_code)` 复合外键、`config_set_code` 具名 CHECK 固定为本表对应值）：
  - `seal_approver_rule_items`：用途 × 印章类型 → `first_approver_role_code`、`backup_approver_role_code`（存角色或岗位引用，**不存个人**）、`is_high_risk`、`blank_page_preload_limit_count`（Q3 预盖张数上限，仅投标用途可非空，具名 CHECK）。具名 CHECK：高风险用途（担保、外借、预盖）`backup_approver_role_code` 必为空；非高风险必填（缺兜底的行不能定版，对应确认触发器检查整版）。唯一键 `(version, purpose_code, seal_type_code)`。
  - `it_service_category_items`：`category_code`、名称、`requires_approval`、`first_approver_role_code`、`backup_approver_role_code`、`requires_validity_date`（权限类）；`requires_approval` 为真时审批人列必填（CHECK）。
  - `position_asset_standard_items`：`position_id uuid`（岗位根 ID，跨 schema 无外键）、`asset_category_code`、`standard_quantity_count`（≥1）。
  - `employee_activity_type_items`：`activity_type_code`、名称、`is_enabled`。
  - `reminder_rule_items`：`reminder_code`、`lead_days_count`（七日提醒、归还提醒等参数，类型化列，不用 jsonb）。
- 单据保存所用 `config_set_version_id`（同 schema 外键，具名）。
- 确认流程：确认人持有该配置的确认权限（权限码由 one-1a-admin 在 §6.1 定）；上线出口条件「两张表定版」= `seal_approver_rule_items` 与 `it_service_category_items` 各有一个已确认版本，由上线前检查脚本核。
- 注册表：`config_sets` `CONFIG_VERSIONED`（`MUTABLE`，仅乐观锁列变）；版本头 `AGGREGATE_CHILD` `CONDITIONAL_UPDATE`；条目表 `AGGREGATE_CHILD` `APPEND_ONLY`；保留期 `PERMANENT`；扫描脚本 M6 能核（根 + 版本表 + 作者/确认人/出处/生效日列）。
- 读取：按业务日期取已确认且生效区间包含该日的版本；找不到失败告警。

## 3. DATA-04 AGG-115：法人列、投影、根先于领用

1. **法人**：`it_asset_usage_requests`、`it_asset_usage_periods` 各加 `legal_entity_id uuid NOT NULL`（单据法人 = 请求时公司资产所属法人，由公司资产读取接口给；取不到失败）。根表 `it_asset_usage_records` 在注册表登记「无法人归属」，理由写明：卡片可因 B-24 跨法人转让而变，根不绑定法人，法人落在每次请求与期间上。`state_transitions.legal_entity_id` 取该次命令请求的 `legal_entity_id`。
2. **当前使用人投影**：**不建投影列**。当前使用人 = 该根下 `ended_at IS NULL` 的唯一期间，由部分唯一索引 `(it_asset_usage_record_id) WHERE ended_at IS NULL` 保证至多一个；按人查在用设备用部分索引 `(user_id) WHERE ended_at IS NULL`（D19）。省掉一个需要登记、重算与对账的投影。
3. **根先于领用**：根按 `company_asset_id` 惰性建立——请求命令在同一事务内 `INSERT … ON CONFLICT (company_asset_id) DO NOTHING` 后取根；`company_asset_id` 必须经公司资产读取接口确认存在，不存在失败；`UNIQUE (company_asset_id)`。迁入时为已迁入的资产一次性建根（迁入入口）。`it_asset_usage_requests.it_asset_usage_record_id` 保持 `NOT NULL` 具名外键。
4. 使用期间排他约束 `EXCLUDE USING gist (it_asset_usage_record_id WITH =, tstzrange(started_at, ended_at, '[)') WITH &&)`，每根至多一个未结束期间如上；调拨在同一事务内结束旧期间、开启新期间、改变请求状态（同一根，一个事务）。

## 4. DATA-01/02 状态历史（对 one-architecture 的数据侧意见，ADR-0058 补充请其起草）

- 子状态机各自记 `state_transitions`：`aggregate_type_code` 取子实体类型（如 `ItAssetUsageRequest`、`ItAssetUsagePeriod`），`aggregate_id` 取该子行 ID，`transition_sequence_number` 取**该子行自己的 `version`**。前提：这些子表行可变且各自带 `version`（设计 §3.1 已有）。一条命令改 3 台状态机就写 3 条转换，各有各的主键与序号，不漏记，也不把子实体 ID 误当根 ID。事务与不变量仍在根上（一事务一根，G-T1 不变）。
- 同一命令内同一行状态变两次（如 `已批准 → 已用印 → 已关闭`）：`version` 增 2，写两条转换，序号 v+1、v+2；无状态变化的登记（`已用印 → 已用印`）不写转换，`version` 照常推进，序号**允许跳号**（只要求严格递增、唯一）。
- 注册表需声明哪些子表是「历史聚合」（`aggregates.yaml` 补 `ItAssetUsageRequest`、`ItAssetUsagePeriod`）。

## 5. DATA-08 迁入历史状态

- 状态列 CHECK 取「现行状态 ∪ 仅历史状态」的并集，另加 `origin_kind`（`NATIVE`/`MIGRATION`，具名 CHECK）；具名 CHECK：仅历史状态只允许 `origin_kind = 'MIGRATION'` 的行。仅历史状态为终态，触发器拒绝从它流转出去，也拒绝原生行进入它。
- 迁入初始转换（ADR-0058 决定 8）：`from_status` 为空、`cause_kind = 'MIGRATION'`、`to_status` = 该行的历史状态或现行状态；该 schema 的 `state_transitions` 状态并集 CHECK 随之包含仅历史状态。
- 各表的仅历史状态清单由迁入规格（M1）逐表列出，来源是老系统实际出现的状态取值（取证登记后映射为自有取值，老系统取值不进领域）；在途（审批中）的按 ADR-0036 U-4 处理，不在本裁定内。
- 历史审批引用只存迁入对照（`platform_integration.source_references`），业务表不加老系统单号列；Q7 过渡通道需要的「来源 = 老系统单据」与「老系统单号」，业务上必须存时，列名用中性词（如 `external_approval_number`，待 one-glossary 与 GLO-10 一并定），并受「同一外部单号累计用印份数」约束。

## 6. GLO-09 决定出处列名

- 文本出处（intent、ADR 编号、用户答复编号）：`decision_ref_key text`（`_key` 是业务键类别词，守护通过）。
- 确认操作的审计记录：`decision_audit_entry_id uuid`（`_id`），无跨 schema 外键。
- 两者都满足 D15「决定出处」；扫描脚本 M6 认 `decision_ref`、`decision_ref_key`，认 `decision_audit_entry_id` 的补丁随后提。`employee_activity_types` 照第 2 节改为 `employee_activity_type_items`，不再单独带出处列。

## 7. DATA-06 新增数据落点

- **Q6 费用预估**：`employee_activity_requests.estimated_amount numeric(19,4) NOT NULL` + `estimated_currency_code text NOT NULL`（金额币种成对，M1 守护）；留底不查预算。
- **Q5 权限工单有效期**：`it_service_tickets.valid_until_date date`（可空，空表示该类别不要求；`requires_validity_date` 为真的类别提交时必填由用例保证，另加 CHECK `valid_until_date >= business_date`）；到期作业用 `(valid_until_date) WHERE status = 'RESOLVED'` 类部分索引（实际状态集合随状态机定）。
- **Q3 预盖登记**：`seal_blank_page_preloads`（登记：用印申请 ID、用途、张数、领用人、登记日期、编号，只追加）与 `seal_blank_page_preload_dispositions`（收回/作废：登记 ID、`disposition_kind`（`RECOVERED`/`VOIDED`）、张数、日期，只追加）。插入触发器在锁住登记行后校验已处置张数合计 ≤ 登记张数；未处置余量即在用，作废不删除。预盖张数 ≤ 当时已确认版本的 `blank_page_preload_limit_count`（用例保证并留版本引用）。
- **用印台账冲正（§11 第 3 条）**：登记表只追加；冲正是新行：`record_kind`（`REGISTER`/`CORRECTION`）、`used_count_delta integer`、`corrects_record_id`（自引用，复合外键 `(corrects_record_id, seal_usage_request_id)` 保证同一申请）；具名 CHECK：`REGISTER` 时 delta>0 且无 `corrects_record_id`，`CORRECTION` 时有 `corrects_record_id` 且 delta≠0。INV-119/INV-138 的累计 = delta 之和；触发器在锁住申请（INV-138 时来源审批实例锁）后校验累计在 [0, 批准份数]，且一条 `CORRECTION` 之后目标登记的净份数不为负。
- Q2 的新增 INV 编号由 one-architecture 登记（DATA-06 的非表部分）。

## 8. 其余 DATA 项的数据侧意见

- **DATA-03 INV-138 锁键**：事务级咨询锁键 = 来源审批实例 ID 的 64 位哈希（多来源按 ID 升序加锁），锁内读累计、只写当前申请；累计含冲正行。
- **DATA-07 合同原件借阅**：口径与形状由 one-architecture 定归属。数据侧：若第一版建在本 schema，表按 AGG-13 目标形状原样建、`INV-125` 的「同一份原件至多一笔未归还」用部分唯一索引，移交时 `ALTER TABLE … SET SCHEMA`（行不洗），其 `state_transitions` 行按聚合复制到接收 schema 并在注册表登记移交；移交方案须写入 W3 卡。
- **DATA-09、DATA-10、DATA-11**：非数据设计裁定。DATA-10 里「IT、资产、活动附件的保存类别未定」按 ADR-0061 决定 4 登记缺口，由我与 one-coordinator 进收件箱。

## 9. 开表前的签发流程

1. one-1a-admin 出各卡的 DDL 草案（7.4、7.5、7.6、7.7、7.8），按本文验收口径；
2. 我逐卡复核并签「数据设计：通过」，复核记录进 `data/reviews/`；
3. 迁移出来后复核实际表结构 diff，在 PR 留签发评论（写 head 提交号）；
4. 注册表、分级（预估金额、理由文本、审批人角色）、保留期、系统操作者随表结构 PR 提交。
