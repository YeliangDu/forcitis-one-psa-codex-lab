# B-31 卡 A 基线 DDL 草案审（organization_administration：schema、state_transitions、config_sets、config_set_versions、employee_activity_type_items）

- 日期：2026-10-22；审者：one-data；对象：`claude/one-1a-admin-docs` @3e42785a3 的 `DDL_DRAFT_CARD_A.sql`（401 行）；依据：`B31_PHYSICAL_MODEL_RULINGS.md` §2、§4、§12–§14，#547 的 WITHDRAWN、事务时间与首版裁定（日历审更正十），ADR-0058、ADR-0072、ADR-0075，DATA_DESIGN_GATE。
- 草案未在数据库上执行；本轮是设计层复核，迁移出来后另复核实际表结构 diff。

## 结论：数据设计：需修改（7 项必改，改完送回即签；W-2…W-13 的裁定见下）

整体结构对：根表 + 版本头 + 条目表的分层、条目表复合外键固定配置集代码、条目只在待确认时插入（FOR SHARE）、版本行是状态持有者且序号取自身 `version`、`transition_sequence_number` 等列名、复合外键与具名约束、种子占位处理都符合裁定。必改项如下。

## 必改

| # | 位置 | 问题 | 改法 |
|---|---|---|---|
| R1 | `config_set_versions`（W-4） | 写错的待确认版本没有出路，独占「至多一个待确认」（与 #547 日历版本同一缺陷） | 同 #547 的裁定：加 `WITHDRAWN`（`PENDING → WITHDRAWN` 终态），列 `withdrawn_by`、`withdrawn_at`、`withdraw_reason_text`，状态联动 CHECK，版本号不回收；`state_transitions` 的 `to_status` 并集与 `status_of_aggregate`（`ConfigSetVersion` 分支）加 `WITHDRAWN`；撤回同事务写转换 `PENDING → WITHDRAWN` 并推进版本行与根 `version`；条目表插入守护已限定只能插入待确认版本，撤回后自然关闭 |
| R2 | `guard_config_set_version_change` | 确认时「生效日不早于确认日」用应用写入的 `confirmed_at`，可倒填绕过 | 同 #547：确认日取 `(transaction_timestamp() AT TIME ZONE 'Asia/Shanghai')::date`，`confirmed_at` 仍由应用注入只作记录 |
| R3 | 关闭规则（W-6） | 关闭已确认版本是应用单独发一条 UPDATE，关闭日可与后继版本不衔接 | 照 #547：**关闭只在后继版本的确认触发器内发生**（`pg_trigger_depth() > 1`，且存在待确认的后继、其生效日等于被写入的关闭日，且被关闭版本是该配置集最大号的已确认版本、仍开放）；应用不再单独关闭，也不能关闭到别的日期；零长区间（同日修订）允许，保持 `>=`；确认时「上一版」取同配置集版本号最大的已确认版本 |
| R4 | 完整性（W-9） | 空版本可以确认 | 确认触发器调用一个按配置集代码分支的函数 `assert_config_version_complete(版本 ID, 配置集代码)`：所有配置集至少一条条目；`SEAL_APPROVERS` 另检查非高风险行必有兜底、高风险行无兜底；各卡以 `CREATE OR REPLACE FUNCTION` 追加自己的分支（后续卡用追加迁移，不改本基线，见 W-12） |
| R5 | `decision_ref_key`、`author_id`（W-5） | (a) 日常维护没有 intent/ADR 可填，列恒空；(b) 种子版本与作者 | (a) **删 `decision_ref_key`**：决定出处 = `decision_audit_entry_id`（确认操作的审计记录），与日历一致；(b) `author_id` 保持 `NOT NULL`，**不做种子版本**——初始版本由负责角色经界面起草、另一人确认，上线出口条件正是「两张表定版」；将来若出现 `MIGRATION` 来源的配置版本，再按 SEC-1021-COO-01 的做法追加 `source_kind` 与属主写入检查 |
| R6 | `config_sets` 权限（W-8） | 运行角色保留 INSERT 则可建新配置集 | 同意回收：`REVOKE INSERT, DELETE, TRUNCATE ON config_sets FROM one_dev_app`（五行由迁移写入），保留 UPDATE（乐观锁）；`config_set_versions` 保留 UPDATE（确认、撤回）撤 DELETE/TRUNCATE；条目表与 `state_transitions` 只留 SELECT、INSERT；脚本同步改 c3/c9 与 `one_db_roles_schemas.sql`（`scripts/ci/db_scripts_aligned.py` 核三处一致） |
| R7 | `organization_administration` 种子（W-7） | 五行种子的 `owner_role_code` 是占位 | 角色代码须在落迁移前由 one-glossary/平台身份定名并写入；草案里的注释块不能带进迁移。DB 只做格式 CHECK，不验角色存在（跨 schema）。归属读法：SEAL_APPROVERS、POSITION_ASSET_STANDARDS、REMINDER_RULES = 行政负责人，IT_SERVICE_CATEGORIES = IT 负责人，EMPLOYEE_ACTIVITY_TYPES = 员工关系负责人，**认可** |

## 其余裁定

- **W-1** 已裁，认可。
- **W-2**（state_transitions 的 CHECK 写法）：以 platform_file 实装为准——`_status` 列有取值 CHECK（`DdlNamingTest` 要求）加 `ck_state_transitions_status_of_aggregate` 按聚合成对限定；STATE_TRANSITIONS_DESIGN 已补各 schema 取值差异说明。每加聚合类型三处 CHECK 走 `DROP + ADD ... NOT VALID + VALIDATE`，同意。
- **W-3**（AGG-116 状态集合，DRAFT 是否落库）：数据侧原则是不留死取值。若"提交即创建为已批准"，则 `DRAFT` 不落库，初始转换只有 `∅ → APPROVED`；两者之间由卡 C 与 one-rules 定，卡 C 的 DDL 再审。本卡 `state_transitions` 并集里的 `DRAFT`、`CONCLUDED`、`CANCELLED` 先保留，卡 C 审定后若有死取值，以追加迁移收窄。
- **W-10** 配置集代码写在具名 CHECK 里：同意，新增配置集追加迁移改 CHECK。
- **W-11** 命名沿用先例：同意；排他约束前缀 `ex_` 日历与 workforce 已用，请 one-architecture 在 NAMING §2.4 补一行 `ex_`。
- **W-12** 基线开放期：本卡建新 schema 基线（尚未合入 main，可自由写）；基线合入 main 后，**后续卡一律追加 V 文件**（日历 #547 同做法），不再改基线文件。
- **W-13** 提议术语：交 one-glossary 登记，注释开头用同名中文，登记后核对；`config_set`、`config_set_version` 若代码里要有聚合类，按 G40 改 aggregate。
- btree_gist：用「只检查存在」的 DO 块（建库脚本预建，最小权限），同意，不用 `CREATE EXTENSION`。
- D19：`ex_config_set_versions_overlap` 的 gist 索引支撑「取某日有效版本」查询，读取的 SQL 须与索引表达式一致（`daterange(effective_from_date, effective_to_date) @> 日期` 且带 `confirmation_status = 'CONFIRMED'`），随实现用 IT 验证走索引。

## 连带提醒（不属本卡）

`platform_file.preservation_category_versions`（#521 已签）有同一个"待确认版本无出路"的缺陷：请 one-platform-file 以追加迁移补 `WITHDRAWN`（含 `state_transitions` 取值与转换），首次部署前落地；我会通知。

## 第二轮（2026-10-22，复核 `claude/one-1a-admin-docs` @0c6237507 的 DDL_DRAFT_CARD_A.sql，481 行）

R1–R7 逐项核过已改对：R1 `WITHDRAWN`（状态、三列联动 CHECK、`state_transitions` 取值与 `status_of_aggregate`、撤回触发器分支）；R2 确认日取事务时间；R3 关闭只在后继确认触发器内（嵌套深度、待确认后继、生效日等于关闭日、被关闭的是最大号已确认版本，零长区间保持 `>=`）；R4 `assert_config_version_complete`（本卡 `EMPLOYEE_ACTIVITY_TYPES` 分支，未装分支的配置集失败而不是放行，后续卡追加）；R5 删 `decision_ref_key`、`author_id` 必填、无种子版本；R6 权限回收清单；R7 角色代码占位仍待定名（见下）。W-3 保留 `DRAFT`/`CONCLUDED`/`CANCELLED` 待卡 C 收窄，同意。

数据设计：通过（one-data，2026-10-22，依据 DATA_DESIGN_GATE 表结构草案项、B31_PHYSICAL_MODEL_RULINGS §2 §4 §14、日历审更正十、ADR-0058/0072/0075；范围：schema、`state_transitions`、`config_sets`、`config_set_versions`、`assert_config_version_complete`、`employee_activity_type_items`）。迁移出来后复核实际表结构 diff；七项随附改动（建库脚本、注册表、分级、聚合登记、术语）在表结构 PR 里逐项核。

R7 的角色代码：不是数据设计问题，由 one-glossary 定名、平台身份侧建角色（`platform_identity` 目前只有 6 个系统角色）。建议名（供 one-glossary 参考）：`administration_manager`（行政负责人）、`it_manager`（信息技术负责人）、`employee_relations_manager`（员工关系负责人）。未定名前"空库配置集五行"验收挂起是正确的，迁移里种子不能写占位。

## 第三轮（2026-10-22，答 one-1a-admin：验收测试抓出 `status_of_aggregate` 缺陷）

1. **缺陷认定**：`ck_state_transitions_status_of_aggregate` 在 `from_status` 为空时，非空分支求值为 NULL，CHECK 放行，`∅ → CONCLUDED`、`∅ → CANCELLED`（员工活动）与 `∅ → CONFIRMED`（配置集版本）被误接受。我签发的草案漏了这一点。
2. **修法复签**：`@2f74de2f6` 的草案用 `coalesce(…, false)` 包住整个表达式，并把 `ConfigSetVersion` 分支改为成对写（`∅ → PENDING`、`PENDING → CONFIRMED | WITHDRAWN`；迁入的配置集版本本卡没有，已去掉）；`EmployeeActivityRequest` 分支保留迁入初始转换。核过：NULL 情形一律落到不通过。**复签通过。**
3. **platform_file 同类**：`platform_file` 基线（#521 已合入）的该 CHECK 不是 NULL 误放行，但写成"目标集合 AND（来源为空或来源集合）"，**不按 (from, to) 配对**，会接受 `∅ → CONFIRMED/REJECTED/APPROVED/READ`、`PENDING → READ`、`APPROVED → REJECTED` 等非法配对。需以追加迁移（`DROP + ADD … NOT VALID + VALIDATE`）改为成对写并 `coalesce`；并入 one-platform-file 单元 7（待确认版本 `WITHDRAWN` 同批）。`STATE_TRANSITIONS_DESIGN.md` 已补写法要求，防止后续 schema 再犯。
4. 实际表结构 diff 待 PR 出来后复核。
