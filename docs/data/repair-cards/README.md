# 已建表数据设计审查：修复卡草稿索引（第二版）

- 起草：one-data 起草助手，2026-10-16；第二版并入用户 2026-10-16 的六项决定、统一分级（阻断 0）与 `SCAN_SUPPLEMENT.md` 补入；状态：草稿，待 one-data 终审后交 one-coordinator 派工。
- 输入：`docs/data/table-reviews/` 四份分报告、`OPUS_VERIFY.md`、`SCAN_SUPPLEMENT.md`（下称 SS，全量机械扫描与补审，行号取 `origin/main` a27bf877）、`docs/data/baseline-table-review.md`（§3 高、§7 待用户决定）。级别以统一分级为准（`DATA_DESIGN_GATE.md` §4a、`BASELINE_TABLE_REVIEW.md` §1、§3）：**阻断 0**；被 R-1b P5 解决、并入 TS-UNIQ、归 W2 或 ADR-0039 / ADR-0043 实施 PR 的项不起条目，只在各卡「不含」一节列出归属。
- 通用约定：迁移基线仍在开放期（ADR-0038），直接改基线、不追加迁移，每张卡附预期表结构 diff；所有卡的基线以 R-1b P5 合并后的 main 为准，卡内行号取自审查时的 main，实施前逐条重核；「待核」项由实施者先核实再改。
- 排期：**修复卡排在 dev 部署之后**，本身不是闸门（用户「同意，不挡 dev」，`BASELINE_TABLE_REVIEW.md` §3 末段）。

## 四张卡

| 卡 | 文件 | 覆盖 schema | 建议负责会话 | 条目数（高 / 中 / 低） |
|---|---|---|---|---|
| DATA-REPAIR-identity | `DATA-REPAIR-identity.md` | platform_identity | one-1a-next-4 | 13（ID-01 至 ID-13；高 2：ID-01、ID-03；中 11） |
| DATA-REPAIR-workforce | `DATA-REPAIR-workforce.md` | organization_workforce | one-1a-next-4 | 14（WF-01 至 WF-14；高 0；中 14） |
| DATA-REPAIR-approval | `DATA-REPAIR-approval.md` | platform_approval、platform_integration、platform_numbering | one-1a-next-4 | 18（AP-01 至 AP-18；高 1：AP-09；中 17） |
| DATA-REPAIR-timesheet-audit | `DATA-REPAIR-timesheet-audit.md` | cost_timesheet、organization_attendance；platform_audit、platform_idempotency | A 部分 one-1a-par；B 部分 one-1a-next-4 | 11（A 部分 TA-01、TA-02；B 部分 TB-01 至 TB-09；高 3：TB-01、TB-02、TB-07；中 7；低 1：TB-09） |
| 合计 | | | | 56（上一版 42）：高 6、中 49、低 1 |

各卡末节「机械类低级项」按表列出 SS §1（长度上限，中）、§2（业务默认值）、§3（空值注释）、§5（`DEFAULT now()`）、§6（内联枚举 CHECK 未具名）的范围，不逐条展开，每类一次改完；SS §4（成对可空列，中）已逐组落到各卡条目。

条目数增加来自：SS 补入 B7 取值（AP-14）、`summary_text`（AP-15）、成对缺项（AP-16、TB-08、ID-12）、`DEFAULT now()` 矛盾（TB-09）、统一分级中低升中的项（ID-12、ID-13、WF-11 至 WF-14、AP-17、AP-18）、审计分区按 ADR-0048 落条目（TB-07，批准前不开工）。

## 级别口径（统一分级后）

- 阻断 0；高：ID-01、ID-03（含用户已定级别）、AP-09、TB-01、TB-02、TB-07。
- 本卡内的级别变化：AR-1（AP-01）高→中、AR-4（AP-03）随之为中；N-1（ID-11）降中；WY-1、AB-1 降中（不起条目）；IR-1（TB-02）高；T-1、AE-1、HS-1 阻断→高（均有归属，不起条目）；UA-2、UA-3、N-4、AQ-8、P-2、SD-4、LE-3、LA-1、CR-2、X-6、X-9、T-7、AB-4、IR-4、NC-1、LN-2、OD-6 低升中；X-6（编号）、SD-1、WY-3 升高（不起条目，分别归 TS-UNIQ、ADR-0039 拆分 a、W2）。
- 四份分报告表内仍有旧级别（如 N-1 高、WY-1 高、AB-1 高、IR-1 中），以本表与 `BASELINE_TABLE_REVIEW.md` §1、§3 为准。

## 用户已作的决定（已写入各卡）

| 事项 | 用户答复 | 收件箱 / intent | 落在 |
|---|---|---|---|
| 审批被驳回、撤销后能否再发起 | 「驳回、撤销后可原单再发起；加签人驳回也须填原因；删掉免填放行」 | 2026-10-16-one-data-approval-resubmit；`docs/decisions/2026-10-16-085331-one-data-审批被驳回-被撤销后能不能再发起.md` | approval 卡 AP-01（CHECK 覆盖驳回、撤销、退回、加签人驳回；删 `hookDecidesReason` 分支；状态机保留 `REJECTED → RUNNING`、`CANCELLED → RUNNING`） |
| 会话标识级别 | 「RESTRICTED（口令、密钥级，不出库、不进日志与分析层）」 | 2026-10-16-one-data-baseline-classification；`docs/decisions/2026-10-16-085331-one-data-会话标识-session_id.md` | identity 卡 ID-03（`sessions.session_id`、`primary_id` 登记 `RESTRICTED + NONE`）；`binding_hash`「同口径另议」仍待用户（ID-04） |
| 审计表按月分区 | 2026-10-15 已定主键 `(id, created_at)`、去重键 `(event_id, created_at)`；2026-10-16「知道了，ADR 成文后我再确认」 | `docs/decisions/2026-10-15-020820-one-coordinator-审计表按月分区主键须含分区键-与.md`；2026-10-16-one-data-audit-partition；`docs/decisions/2026-10-16-085331-one-data-审计表按月分区-知道了-ADR.md` | timesheet-audit 卡 TB-07，分区键 `created_at`；**依 ADR-0048（已采纳，2026-10-16）实施，基线关闭前做，属生产前闸门** |
| 员工与岗位怎样版本化 | 「都做维度型版本表（以同步观察日为生效依据）」；**生效依据后改为人事业务生效日**（intent 2026-10-16-134434） | 2026-10-16-one-data-employee-position-versioning；`docs/decisions/2026-10-16-085331-one-data-员工与岗位怎样版本化-都做维度型.md` | workforce 卡「不含」（并入 W2 DA-7，离职时刻字段按 OBSERVED 口径定，不再列待用户决定） |
| WY-1 日历范围列时点 | 「追认：W2 随日历版本化完成，基线关闭前必须完成」 | 2026-10-16-one-data-wy1-calendar-scope；`docs/decisions/2026-10-16-085332-one-data-日历-calendar_scop.md` | timesheet-audit 卡「不含」；NEXT_BATCH_PLAN 第 299 行补注出处由 one-architecture 做 |
| 闸门口径 | 「同意，不挡 dev」 | `BASELINE_TABLE_REVIEW.md` §3 末段（intent 文件名待核，本批 `085331` 一组中无此条） | 全部卡与 README：下节 |

## 生产前闸门（不是 dev 部署闸门）

ADR-0034 W2、W2 主数据与日历版本化、审计分区（TB-07）、ADR-0043 实施 PR 是**生产前闸门**，由 one-ops 登记到 PREDEPLOY（C-15 一组）；不挡 dev 部署；本批修复卡排在 dev 部署之后。ADR-0039 的拆分 a/b、DA-3、DA-10（见下表）已在 COORDINATOR_LEDGER 排「生产前」，是否同样并入 PREDEPLOY 的 C-15 一组，请 one-coordinator 与 one-ops 确认（SS 补审把它们记为高）。

## 与 R-1b P5、TS-UNIQ、W2、ADR-0043、ADR-0039 的关系

「P5 分支」指 `origin/claude/one-1a-next-4-r1b-p5`（18f2c807）；下表「已核」指已读该分支的基线 SQL。

| 来源 | 已覆盖的项 | 对修复卡的影响 |
|---|---|---|
| R-1b P5 | `sync_run_held_separations` 主键与外键索引（HS-1、HS-3）；`audit_entries.subject_kind` / `subject_id`（AE-1）；归档批次「时刻 + ID」（AB-1）；`role_permissions`、`account_roles` 主键；`primary-key-exemptions.txt`。**未覆盖（已核）**：`sync_diffs.subject_key`（wf :187）、`sync_runs` / `sync_diffs` 仍在 workforce（:120）、`employees.login_name` / `wecom_user_id`（:66、:69）、`uk_outbound_deliveries_card` 无收件人（itg :64）、`approval_requests.document_type` 含未建模块取值（apv :17）、`numbering_rules` 同（num :18、:173-181）、`summary_text` 无约束（apv :20） | 四卡全部在 P5 合并后开工；workforce 卡不碰 held_separations 主键；audit 部分 TB-05、TB-06 对照 P5 列名；AP-14、AP-15 不被 P5 覆盖，照做 |
| TS-UNIQ（one-1a-par 另卡） | T-1 `uk_timesheets_employee_id_work_date`（无谓词）；T-2 状态集合与事件改名、`REJECTED` 去终态；X-6（高）`numbering_rule_version_id`；删冗余 `ix_timesheets_employee_id` | timesheet-audit 卡 A 部分的 TA-01（T-3）须在 TS-UNIQ 合并后按其状态集合改；approval 卡不含 X-6；SS §6 的 `timesheets.status` 内联枚举随其状态集合改，不单做 |
| W2（生产前闸门） | ADR-0034 卡（R-1、RP-1、AR-1、AQ-2、AQ-6，均高）；DA-7（`employee_versions`、`positions` 版本表，用户已定维度型，生效依据已改为人事业务生效日（DECLARED，intent 2026-10-16-134434）；E-1、E-2、P-1；SS §4 第 2 组）；DA-17 / K-2（日历版本化 WY-2 高、`calendar_scope_kind` WY-1 中且用户已追认「基线关闭前必须完成」、WD-2、WY-3 高）；法人代码规则（LV-1(b)） | 各卡「不含」逐条列出；identity 卡 ID-01 的 CHECK 写成可并入 `VOIDED` 的形态；positions 是否在 W2 的 25 张表内待核；**DA-17 重建日历明细表时须带走 `ck_work_calendar_days_make_up_weekend`（TA-A A1 新增，调休上班日必须是周六或周日），不得随旧表一起丢** |
| ADR-0043 实施 PR（生产前闸门） | 分级文件四段格式、`user_accounts.last_login_address` 清除路径与 R-2 守护、`recipient_codes` 改 `INTERNAL + PI`、`employees` 等分级（SS 缺项 8） | identity 卡 ID-06 只登记带到期日 gap，ID-12 的成对 CHECK 须与清除路径协调；timesheet-audit 卡 TB-01、TB-02 的分级写在卡内表中 |
| ADR-0039 拆分 a/b、DA-3、DA-10（生产前，已排期，**P5 未覆盖**，SS 补审级别高） | `sync_runs`、`sync_diffs` 迁 `platform_integration`；`sync_run_held_separations` 改 workforce 的 `separation_hold`；`employees` 删 `login_name`、`wecom_user_id` 并建 `external_identities`；`sync_diffs.subject_key` 拆类别 + uuid；`uk_outbound_deliveries_card` 加收件人（一收件人一行，须结构设计） | 不起条目，在 workforce、approval 两卡「不含」逐条列出；workforce 卡 WF-01、WF-02、WF-07、WF-08、WF-13、WF-14 改的是将搬迁的表，随表搬迁，开工时核对拆分 a 是否已合并 |
| ADR-0036 / ADR-0039 延后项 | `source_references` 三列、`legacy_numbers` 搬表（无级别，首个迁入阶段前） | approval 卡「不含」列出；AP-18 的改动随 `legacy_numbers` 搬迁 |
| R-2a / G-D12 | 全库改名（`ddl-naming-baseline.txt`）、内联枚举 CHECK 具名（SS §6 实为 36 处，非「约 40 处」） | 各卡不含改名；新增约束一律具名 |
| DA-10 | `outbound_deliveries` 收件人列与去重键 | approval 卡不含 |

## 建议顺序

1. **前置**：R-1b P5 合并（四卡共同基线）；TS-UNIQ 合并（仅 timesheet-audit 卡 A 部分依赖）；dev 部署（本批卡排其后）。
2. **第一批（互不依赖，可并行）**：DATA-REPAIR-identity（含高：ID-01、ID-03）、DATA-REPAIR-approval（含高：AP-09；先删 `hookDecidesReason` 再加 CHECK；AP-14 的种子部分等 one-architecture 对 ADR-0010 第 5 项的判定）、DATA-REPAIR-timesheet-audit B 部分（含高：TB-01、TB-02；TB-07 分区部分除外）。
3. **第二批**：DATA-REPAIR-workforce（全部为中；WF-09 须先由 one-coordinator 接受撤销外键例外；WF-01 等改动的表若已被拆分 a 迁走则改落新位置）；timesheet-audit A 部分（TS-UNIQ 之后）。
4. **TB-07 审计分区**：ADR-0048 已采纳（用户 2026-10-16 批准成文），已回答「事件审计的 `created_at` 取信封发生时刻」（决定 2）；基线关闭前做，属生产前闸门；迟到事件处理是必答项。
5. **并行事项（不属本批卡，须由 one-coordinator 排期）**：ADR-0034 W2 卡、W2 主数据与日历版本化、ADR-0043 实施 PR、ADR-0039 拆分 a/b 与 DA-3、DA-10；前四项作为生产前闸门由 one-ops 登记 PREDEPLOY。

## 须用户或 one-coordinator 先决定的事（已剔除已决项；详见各卡「待用户决定」）

- 用户：`login_states.binding_hash` 访问级别（ID-04；`session_id` 已定 RESTRICTED，此项「同口径另议」）；`last_login_address` 保留期（U-12 已定「有期限」，期限待提）；各表保留期（审批四表、`process_archive_skips`、`outbound_deliveries` 含「人工处理过的」口径、`authorization_requests`、`browser_identifiers`、`sso_ticket_uses`、`timesheets`、`consumed_events`、`idempotent_responses`、`audit_archive_batches`、`sync_run_held_separations`）；`source_references` 个人信息判定与永久保留；`decision_ref` 填法与 CONFIRMED 是否必填（AP-10）；编号规则版本生效日语义（NV-3）；U-7b 例外与到期日；工时单部门口径；各自由文本列的分级与允许写入内容清单（含 `summary_text`、`detail`、`response_text`）；工号、部门编码、岗位编码唯一范围；法人状态与 OBJ-54 对应；日历版本化确认人；分区 ADR-0048 成文后的确认（用户已答「ADR 成文后我再确认」）。
- one-coordinator：workforce 版本表 `fk_exceptions` 撤销（WF-09）；复合外键 `(task_id, approval_request_id)` 及 AP-17、WF-12 的外键目标为唯一约束，是否放行 NAMING_STANDARD §2.4a-1「外键目标为主键」（AP-04）；ADR-0039 的拆分 a/b、DA-3、DA-10 是否并入 PREDEPLOY 的 C-15 一组。
- one-architecture：ADR-0048 合入 main；对 ADR-0010 第 5 项「三种单据都从默认模板起步」是否已决定现在就建判定（AP-14）；补记 NEXT_BATCH_PLAN 第 299 行出处（用户已追认，intent 落点指定）；补审 B7-2（区域应收负责人、`is_delivery_unit`、`is_fee_earner`）是否被 ADR 或同步规格点名；ADR-0033 第 9 项第 2 条与 NEXT_BATCH_PLAN:299 不一致的出处补记。
- one-ops：把生产前闸门四项登记到 PREDEPLOY（C-15 一组）；`legacy_numbers` 唯一范围需按 ADR-0037 取证是否有跨法人重复原编号（不在卡内）。

## 保留期与匿名化（用户 2026-10-16 答「按 one-data 建议」，经 one-coordinator 转达，intent 随 #319 合入）

逐表建议在 `../RETENTION_PROPOSAL.md`；各卡落点：identity 卡（`authorization_requests` 10 年、`last_login_address`、`browser_identifiers`、`sso_ticket_uses`）、workforce 卡（`employees` 的登录名 / 邮箱 / 企业微信 ID 离职满 180 天置空、`assignment_versions` 离职满 10 年、`sync_run_held_separations` 随运行）、approval 卡（审批四表 10 年、`process_archive_skips`、`outbound_deliveries` 两档、`source_references` 人员类 10 年）、timesheet-audit 卡（`timesheets` 10 年、`idempotent_responses` 24 小时、`audit_archive_batches` 与归档同寿命）。注册表 `retention` 与清理作业随各卡落；涉 GUIDING_PRINCIPLES §6 补行与 ADR-0043 R-2 出处的，交 one-architecture 与 one-coordinator。
