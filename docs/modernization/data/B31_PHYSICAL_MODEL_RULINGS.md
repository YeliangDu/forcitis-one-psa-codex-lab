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

## 10. 更正：先行期资产卡片与法人来源（取代第 3 节第 1、3 条的依赖；2026-10-21，答 one-architecture）

one-architecture 裁定先行期不建 B-31 → `fixedasset` 的依赖边（ADR-0051 决定 9，fixedasset 在 W5）。我同意这个方向，第 3 节「经公司资产读取接口确认、法人由它给」作废，改为：

1. **卡片身份**：根表 `it_asset_usage_records` 的业务键是 `asset_card_number text NOT NULL`（资产卡片号，格式 CHECK、`UNIQUE`），登记为业务键（G-DDL1）。它是公司资产卡片号这一业务编号，不是老系统的内部 ID；`company_asset_id uuid` **先不建**，W5 fixedasset 上线时用追加迁移加 `company_asset_id uuid`（可空）并按卡片号回填，回填完成后再改为必填，那次迁移由 fixedasset 的卡负责。
2. **根的建立**：先行期**不惰性建根**（卡片号无法被外部确认，惰性建根会让手误录入变成新根）。根只有两个来源：迁入入口（按迁入的卡片清单）与行政的「登记资产卡片」命令（B-31 自己的台账登记，权限码由 one-1a-admin 定）。领用申请的卡片号必须已有根，没有就失败。
3. **法人**：先行期 `it_asset_usage_requests.legal_entity_id`、`it_asset_usage_periods.legal_entity_id` 取**领用人在业务日期当天的法人归属**（`organization.workforce` 的员工法人归属读取接口，已有 W2 区间，缺归属则失败，不默认），不取资产的法人。列注释写明先行期口径；W5 后改取资产卡片的法人时，历史行不改，新行换来源，换口径当天登记在 ADR。
4. 迁入的卡片：卡片清单由行政提供，作为迁入规格的输入进评审；卡片号重复、格式不符的拒收。
5. 取证提醒：老系统员工法人归属无来源（DAT-1021-ARC-01、B-1），所以第 3 点在迁入历史行和投产初期依赖人事提供的归属表；缺归属的领用申请在先行期失败并告警，不能默认 1001。

## 11. 合同原件借阅移交（B-31 → contract.agreement）的具体步骤（答 one-architecture ③）

前提：第一版在 `organization_administration` 按 AGG-13 目标形状建表（含 INV-125 部分唯一索引），W3 合同模块上线时移交。移交是一次维护窗口内的迁移，步骤：

1. **冻结**：窗口开始时 `organization.administration` 关闭借阅写入入口（功能开关），在途借阅审批实例逐个处置：已通过的保持；审批中的等其结束或由业务撤回，**窗口内不留审批中**（比回调改路由简单）；定时作业先停用。
2. **表移动**：`ALTER TABLE organization_administration.<借阅表> SET SCHEMA contract_agreement`（行不动，不洗数据）；表上约束、索引名不含 schema，随表移动；**触发器函数与视图留在原 schema，须在目标 schema 重建同名函数并把触发器重指向**，再删除原函数；`OWNER` 保持迁移属主。
3. **状态转换**：把原 schema 的 `state_transitions` 中 `aggregate_type_code` 属借阅聚合的行**复制**到目标 schema 的 `state_transitions`（`id`、时间、序号原样，只追加表上由属主迁移写入，复制后做行数与逐行摘要核对）；目标 schema 的 `aggregate_type_code` 与状态并集 CHECK 须先在其基线里包含借阅聚合的取值；原 schema 的这些转换行**保留**（只追加表不删），注册表标注「已移交」。
4. **审批回调**：审批单据类型码（如 `CONTRACT_LOAN_APPROVAL`）是身份，**不变**，`platform_approval` 里已有的实例与历史不动；变的是单据类型到处理模块的绑定：窗口内由 `platform.approval` 的回调登记从旧模块切到新模块（登记是配置/代码，随同一部署）；因窗口内不留审批中，没有需要重路由的在途回调。
5. **逾期作业**：旧作业（`organization.administration` 的借阅逾期提醒）停用并删除，ShedLock 记录不迁；新模块自声明新作业；提醒是否已发的状态在表里（逾期提醒时间列、升级标记列）随表移动，新作业靠它幂等，不会重复发。
6. **权限码与路径**：权限码由 `organization.administration:contract-loan:*` 改为 `contract.agreement:agreement-loan:*`（名字以 one-glossary 裁定为准）。`platform_identity` 的角色授权是数据：窗口内由 `platform.identity` 追加迁移加新码并**复制**原授权，验证通过后才删旧码；前端路径与 API 路径变更，旧路径保留一个版本的 301/兼容，随后删。
7. **登记与守护**：数据访问注册表、分级、保留期、`aggregates.yaml`、`module-dependencies.txt`、模块边界测试随移交 PR 改；移交 PR 改两个 schema 的迁移，须带 ADR（one-architecture 起草），并过 `migrations_immutable` 的开放基线规则或走追加迁移。
8. **核对与回滚**：窗口内核对行数与逐行摘要（借阅表、转换行）、INV-125 唯一索引存在、权限副本一致；任一失败，`SET SCHEMA` 反向执行即可恢复（数据未动，转换行复制可删，权限新码可删）；回滚点之前不删任何旧物。
9. 移交 PR 的检查清单：本节 1–8 步逐项有对应迁移或配置变更、有集成测试（SET SCHEMA 后写入仍成立、INV-125 仍拒绝并发借出、复制的转换行可读）。

## 12. 更正：先行期资产法人与卡片号来源（取代第 10 节第 2、3 点；2026-10-21，依 ADR-0077 与 one-architecture 对齐）

1. **法人取财务卡片清单里卡片所属的法人**，不取领用人任职法人（资产属于哪个法人与领用人在哪家任职是两回事；也不再依赖人事法人归属表，W5 前后口径一致）。
   - 根表 `it_asset_usage_records` 加 `legal_entity_id uuid NOT NULL`（卡片所属法人，取自财务清单；跨 schema 不建外键）；第 3 节「根登记无法人归属」作废。
   - 请求表、期间表的 `legal_entity_id` 在事件发生时**复制根上的值**（事实快照，之后根上的值变化不改历史行）；`state_transitions.legal_entity_id` 取同一值。
   - 根上的法人**不被增量导入改写**：财务清单对已有卡片给出不同法人时，该行不导入，进冲突报告交财务处置（卡片跨法人转让属 B-24/W5，不在先行期用导入偷换）。
2. **卡片号只来自财务卡片清单**：取消行政单条「登记资产卡片号」命令，避免形成影子台账。根只有两个来源：迁入入口（按迁入的清单）与「导入资产卡片清单」增量导入命令（幂等：同卡片号同法人跳过；同卡片号不同法人进冲突报告；卡片号格式不符拒收）。命令只能由持有该权限的角色执行（权限码由 one-1a-admin 定），写审计，返回导入、跳过、冲突、拒收的计数与清单，不把冲突明细写进审计 `detail`。
3. 领用申请的卡片号必须已有根，没有就失败；法人缺失（清单没给）的卡片行拒收。
4. **W5 回填**：fixedasset 上线时，由其建造卡追加 `company_asset_id uuid`（可空）并按卡片号回填，回填完成再改必填；**回填完整性是 W5 的出口条件**；未匹配的卡片号列清单，交财务处置，不默认、不删除。此条写进 W5 建造卡的验收。
5. 迁入规格（B-31 历史）里的卡片清单同源（财务清单）；卡片号重复、格式不符、无法人的拒收。

## 13. 数据访问注册表字段 `transferred_aggregate_types`（ADR-0076 第 5 条；形状由我定）

加在**原 schema** 的 `state_transitions` 注册表条目下，列表，每项一个已移交的聚合类型：

```yaml
platform_x.state_transitions:   # 实际写原 schema 的名字
  transferred_aggregate_types:
    - aggregate_type_code: ContractLoan   # 该 schema 的 state_transitions 里 aggregate_type_code 的取值
      transferred_on: 2027-03-01          # 移交日（业务日，Asia/Shanghai）
      to_schema: contract_agreement       # 接收 schema
```

语义与守护：
1. 原 schema 里该类型的转换行**保留**（只追加表不删），但分析投影与按聚合类型的读取一律**排除**这些行，改读接收 schema 的副本（副本在移交日一次复制，核对行数与逐行摘要，见第 11 节）；
2. 字段每项三个键齐全，`aggregate_type_code` 必须出现在原 schema 的 `ck_state_transitions_aggregate_type_code` 取值里（历史行还在）且**也**出现在接收 schema 同名 CHECK 里，`to_schema` 必须是已登记的 schema，`transferred_on` 不得晚于当天；这几点由 `DataArchitectureTest` 校验（随移交 PR 加）；
3. 同一 `aggregate_type_code` 只能移交一次，不得出现在两个 schema 都标「现行」；
4. 本字段只做登记与投影排除，不改表结构。

## 14. AGG-116 员工活动申请（7.8）三处裁定（答 one-1a-admin G-4、G-5、G-11；2026-10-22）

1. **法人口径（G-4）**：`employee_activity_requests.legal_entity_id uuid NOT NULL` = **申请人在业务日期当天的任职法人归属**（`organization.workforce` 的员工法人归属读取接口，按生效日取；缺归属失败告警，不默认——ADR-0058「来源不确定时失败」）。周年自动生成的申请，申请人栏是周年员工，取该员工**在周年日期当天**的法人归属。费用归申请人的法人；参与人可属其他法人，先行期不做跨法人费用分摊（CS-1.8），参与人不带法人列。状态转换行的 `legal_entity_id` 取同一值。前置：人事提供五家法人员工归属表（`MIGRATION_QUALITY_CHECKS.md` 第 7 节 B-1），缺归属的申请先行期失败。
2. **金额（G-5）**：`estimated_amount numeric(19,2) NOT NULL` + `estimated_currency text NOT NULL`。理由：`kernel.Money` 按 2 位小数 HALF_UP 归一，库内小数位必须与领域值一致；列名按 NAMING_STANDARD §2.4 的 `<名>_amount` + `<名>_currency` 成对写法（币种 ISO 4217，`text` + 具名格式 CHECK `^[A-Z]{3}$`），不用 `_currency_code`。具名 CHECK `estimated_amount >= 0`。本卡是金额列首批，我随后让扫描脚本 M1 同时认 `currency` 与 `currency_code` 两种配对（规格里我写窄了，以 NAMING 为准）。
3. **审计列与版本列（G-11）**：申请表（聚合根）带 `version bigint NOT NULL DEFAULT 0`（乐观锁）与 `event_count bigint NOT NULL DEFAULT 0`（事件序号，与 `version` 分开），与已有 AggregateRoot 表同款；**不建通用 `created_by`/`updated_by`**——设计 §3.1 那一条取消：谁创建、谁改状态已有业务语义列（`applicant_id`、`cancelled_by` 等）和 `state_transitions.actor_id`，通用 `_by` 与安全审计重复且增加人员标识列。`created_at`、`updated_at` 照常（命名守护要求），时间由应用注入。
   - 说明：NAMING_STANDARD §2.3 把 `created_by`、`updated_by` 列在审计列里，但 main 上的聚合表实际只有 `platform_integration` 一份基线带 `created_by`，其余都没有、命名守护也不要求；本裁定沿用现有基线实践。若 one-architecture 认为 NAMING 字面要求所有聚合表带这两列，请其判定并通知我，届时改 NAMING 或改本条，不在各表自行取舍。
4. 前置卡 A（`config_sets`、`config_set_versions`、`employee_activity_type_items`、schema 基线、`state_transitions`）的拆法：同意；`state_transitions` 的聚合类型与状态取值并集随基线按 AGG-116 登记，模板列名用 `transition_sequence_number` 等已更正名。
