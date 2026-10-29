# 已建成表数据设计审查（基线迁移）

- 日期：2026-10-16 · 会话：one-data · 起因：用户「目前已经建成的表要 review」（经 one-coordinator 派工）。
- 标尺：数据设计门禁清单 `DATA_DESIGN_GATE.md`（v2，含 B0、B3a、O15、O16 与附录 C；#305，用户已定生效、热点阈值 10%）。其中 OW、PA 两份分报告按旧版清单判，O15 逐条对照未完整重做（见「未核实」）。
- 范围：main 上 10 个 schema、49 张表（基线迁移 `backend/src/main/resources/db/migration/*`）、`backend/architecture/data-access-registry.yaml`、`data-classification.txt`、`docs/data/data-scorecard.md`。
- 方法：4 个 sonnet 子代理分 schema 逐表套清单（分报告在 `review-parts/`）→ 1 个 opus 子代理独立复核全部「阻断与高」、各份的高风险项与 6 条中级抽查（`review-parts/OPUS_VERIFY.md`）→ 本会话汇总。生产库未碰；未起容器、未跑测试，结论来自迁移、注册表、事件目录、Java 源码与 ADR 原文。
- 级别口径：统一按「分级参照表」（`DATA_DESIGN_GATE.md` §4a）；原则：违反已采纳 ADR 或用户决定且未排期为阻断，已排期或已由 P5 解决为高；库内安全不变量可被破坏为高；缺成对 CHECK、缺外键与索引、自由文本无长度上限、保留期或分级缺登记、枚举含未建模块取值为中；内联 CHECK 未具名、业务默认值、「为空表示」注释、`DEFAULT now()` 与注入时钟矛盾、命名与措辞为低。

## 1. 结论与统计

49 张表里，通过 4 张（`org_unit_legal_entities`、`employee_legal_entities`、`event_publication`、`scheduler_locks`），其余 45 张需修改。分报告原计 181 条，opus 复核后的口径如下：

| 级别 | 条数 | 说明 |
|---|---|---|
| 阻断 | 0 | 原 8 条阻断均有归属，按分级表归高（HS-1、AE-1 已被 P5 解决，T-1 并入 TS-UNIQ，R-1 / RP-1 / AR-1 排 W2） |
| 高 | 20 | 其中 HS-1、AE-1 已被 P5 解决；7 条已排 ADR-0034 / W2（R-1、RP-1、AR-1、AQ-2、P-1、E-1、WY-2）；opus 升级 S-1、NV-2 |
| 中 | 91 | |
| 低 | 70 | 内联 CHECK 未具名、业务默认值、注释、命名等 |
| 合计 | 181 | |

统一分级后的数字 = 分报告按分级表重分级（0 / 22 / 89 / 70）再叠加 opus 复核的四处裁定：IR-1 维持高（应答正文复制，opus 确认）、WY-1 降中（用户已追认时点偏离）、AB-1 降中（P5 已解决，「漏档丢数据」夸大）、N-1 降中（表只由迁移维护）；分报告各自的合计表未同步这四处，以本表为准。

复核结果：20 条阻断与高中，**确认 12、降级 8、驳回 0**。降级的原因两类：已在 ADR 或计划里排定首次部署前（R-1、RP-1、AR-1、UA-1），或库内实际风险小（WY-1、AB-1、SR-1、N-1）。

**一句话总评**：OLTP 骨架（主键 UUIDv7、外键索引、乐观锁、`timestamptz`、无 PG `ENUM`、无 `CASCADE` 滥用）基本到位；缺口集中在四处：① ADR-0034 的角色与授予版本化尚在 W2，基线仍违反该 ADR；② 库内成对 CHECK（状态 ⇔ 原因 / 确认人 / 时刻）大面积缺失，只由应用保证；③ 分级与保留期登记不全（会话标识、自由文本、应答副本、个人信息表的保留）；④ 主数据与日历的版本化（D17）排在 W2，现为覆盖写。

## 2. 逐表结论

| schema | 表 | 结论 | 分报告 |
|---|---|---|---|
| organization_workforce | positions、org_units、employees、assignments、sync_runs、sync_diffs、legal_entities、legal_entity_attributions、org_unit_versions、assignment_versions、legal_entity_versions、legal_entity_attribution_corrections、sync_run_held_separations（13 张） | 需修改 | `review-parts/organization_workforce.md` |
| organization_workforce | org_unit_legal_entities、employee_legal_entities | 通过 | 同上 |
| platform_identity | 13 张全部（user_accounts、roles、role_permissions、account_roles、navigation_items、login_states、sso_ticket_uses、ticket_issuers、emergency_admins、sessions、sessions_attributes、browser_identifiers、authorization_requests） | 需修改 | `review-parts/platform_identity.md` |
| platform_approval | approval_requests、start_rounds、approval_tasks、approval_task_records、process_archive_skips | 需修改 | `review-parts/platform_approval_integration_numbering.md` |
| platform_integration | outbound_deliveries、source_references、event_publication_archive（仅注释） | 需修改 | 同上 |
| platform_integration | event_publication（框架表） | 通过 | 同上 |
| platform_numbering | numbering_rules、numbering_rule_versions、numbering_counters、legacy_numbers | 需修改 | 同上 |
| cost_timesheet | timesheets、consumed_events | 需修改 | `review-parts/timesheet_attendance_audit_idem_sched.md` |
| organization_attendance | work_calendar_years、work_calendar_days | 需修改 | 同上 |
| platform_audit | audit_entries、audit_archive_batches | 需修改 | 同上 |
| platform_idempotency | idempotent_responses | 需修改 | 同上 |
| platform_scheduling | scheduler_locks（框架表，2 条低级提示） | 通过 | 同上 |

## 3. 高（统一分级后，无阻断）

「重叠」一列：**P5** = R-1b P5（分支 `claude/one-1a-next-4-r1b-p5`，已签发数据设计）已解决；**TS-UNIQ** = one-1a-par 另卡；**W2** = ADR-0034 / K-2 主数据与日历版本化卡（NEXT_BATCH_PLAN:269、:299）。

| 编号 | 表 | 问题 | 依据 | 重叠 / 归属 |
|---|---|---|---|---|
| HS-1 | sync_run_held_separations | 复合主键 | ADR-0033 第 1 项第 2 条；O2 | P5 已解决（HS-2 保留期仍开放） |
| AE-1 | audit_entries | `subject_key` 文本未拆列 | ADR-0033 第 7 项第 2 条；O15 | P5 已解决 |
| T-1 | timesheets | 缺「员工 × 工作日」唯一约束，并发新建可得两单 | 用户决定 intent `2026-10-16-052923`；O3 | TS-UNIQ（无谓词、全状态唯一，已定） |
| R-1 | roles | 角色定义原地覆盖、迁移写系统角色、物理删角色 | ADR-0034 第 6 项；D17；D8 | W2 卡；生产前闸门 |
| RP-1 | role_permissions | 复合主键；表去留随 ADR-0034 | ADR-0033 第 1 项第 2 条 | 主键 P5 已解决；删表并入 W2 |
| AR-1（身份） | account_roles | 授予物理删除、无历史 | ADR-0034 第 2 项 | 主键 P5 已解决；授予历史并入 W2 |
| AQ-1 | authorization_requests | 双人确认 CHECK 在「已确认、确认人为空」时整条为真，可绕过 | ADR-0030；O4 | 修复卡 identity |
| AQ-2 | authorization_requests | 缺 `VOIDED` 与两个基准列 | ADR-0034:215-222 | W2；AQ-1 的 CHECK 同时覆盖 `VOIDED` |
| AQ-6 | authorization_requests | `role_codes` 以业务键数组引用角色 | ADR-0033 第 1 项第 1 条；ADR-0034 第 2 项 | W2 |
| S-1 | sessions | `session_id` 等同 Cookie 凭据，明文入库，未分级 | ADR-0043；D13 | 用户已定 RESTRICTED + NONE（intent `2026-10-16-085331`，收件箱 2026-10-16-one-data-baseline-classification）；修复卡 identity |
| NV-2 | numbering_rule_versions | 「确认态 ⇒ 确认人非空」无约束，INSERT 不受触发器控制 | D15；ADR-0030 同类 | 修复卡 approval |
| X-6（编号） | timesheets 等使用方 | 使用方不记所用编号规则版本 | ADR-0033 第 1 项第 5 条；D7、D17 细则 6 | 并入 TS-UNIQ |
| SD-1 | sync_diffs | `subject_key` 把对象类别与 ID 拼成字符串 | ADR-0033 第 7 项第 2 条；O9、O15 | 拆分 a 按最终形态在集成层新建（P5 卡 §3.1）；拆分前仍开放 |
| WY-3 | work_calendar_years | 日历事件聚合标识是文本 `default:2026` | ADR-0033 第 7 项第 1 条；E1 | main 的 P5 已改；待 P5 合并后核 |
| P-1 | positions | 无版本表也无「不需要」豁免 | D17 | W2；用户已定做维度型版本表；生效依据已改为人事业务生效日（DECLARED，intent 2026-10-16-134434，取代同步观察日） |
| E-1 | employees | 同上；离职时刻字段口径 | D17；DA-7 | W2；同上 |
| WY-2 | work_calendar_years / days | 整年删除再插入，使用方不记版本 | ADR-0033 第 9 项第 1、3 条；D15 | W2 K-2；基线关闭前完成 |
| AE-2 | audit_entries | 10 年只追加大表未分区，分区与单列 UUID 主键、`event_id` 唯一冲突 | NAMING_STANDARD §2.4a-8；O-8 | 用户已定（intent `2026-10-15-020820`：主键 (id, created_at)、去重键 (event_id, created_at)）；ADR-0048 由 one-architecture 起草，成文后用户确认 |
| AE-3 | audit_entries | `LoginAdmission` 仍把 IP、浏览器标识写进 `detail`；U-7b 带到期日例外未登记 | ADR-0043 第 4 项第 5 条、U-7c | 修复卡 timesheet-audit B 部分 |
| IR-1 | idempotent_responses | 对所有放行的 POST 存整份应答，未分级、无长度上限 | C4；ADR-0043 | 修复卡 timesheet-audit B 部分 |

生产前闸门（用户「同意，不挡 dev」）：ADR-0034 W2、W2 版本化、审计分区、ADR-0043 实施 PR，由 one-ops 登记到 PREDEPLOY（C-15 一组）；修复卡排在 dev 部署之后。

## 4. 复核发现的误报与修正

1. **WY-1** 日历范围列：库内唯一性已正确，原「阻断」过重；NEXT_BATCH_PLAN:299 已把 `calendar_scope_kind` 挪到 W2，与 ADR-0033 第 9 项第 2 条原文（并入 R-1b）不一致，需 one-coordinator 补记出处（门禁 §5：偏离 ADR 不由会话自定）。
2. **AB-1** 归档漏档丢数据：需 ID 时钟落后超过 3 年才成立，夸大；P5 已改为「时刻 + ID」。
3. **工时唯一范围**：子代理把「部分唯一索引、排除已驳回」列为待用户决定，而用户已决定（全状态唯一、不设取消终态，intent `2026-10-16-052923`）。
4. **PA-AR-1**：漏看 `hookDecidesReason`，其建议的成对 CHECK 会误拒合法路径。
5. **SR-2(d)** `finished_at >= started_at`：实例间时钟偏差会让每 5 分钟的同步作业提交失败，不要加。
6. **TK-1** 补成对约束时须避开两种合法组合：关闭的任务保留处理人与认领时间；系统自动通过的任务无认领时间、无内容摘要。
7. **N-1**：以老系统规则卡 RULE-347 为依据（只作证据），且表为 `MIGRATION_ONLY`、无应用写入方，降中。
8. **「`primary-key-exemptions.txt` 不存在」**：main 属实，P5 已新建。
9. **本会话自身的前科**：预审曾误报「信息图无请假对象」（实有 OBJ-68），已入守则：子代理的「缺失 / 不存在」类结论由本会话 grep 复核后才对外发。

## 5. 跨表规则与守护建议（交 one-quality）

1. **双人确认的库内防线**：凡状态含「已确认」类终态且有确认人列的表，CHECK 写「确认态 ⇒ 确认人与时刻非空」「待确认 ⇒ 均空」，两人互异不得以 `IS NULL OR` 放行确认态。适用 `authorization_requests`、`numbering_rule_versions`，`source_references` 待核，W2 的日历与角色版本同。
2. **原因必填终态的单一事实源**：状态机声明「需原因」的转换与表上成对 CHECK 由同一测试比对（扩展 G-D12）；`hookDecidesReason` 这类放行分支须在规则卡登记。
3. **注册表 `mutability` 与实际 SQL 一致**：`APPEND_ONLY` 不分 role 都要有拒改触发器（`start_rounds` 漏检）；`INSERT_DELETE` 的表出现 UPDATE 即失败（`browser_identifiers`、`idempotent_responses`、`consumed_events` 现为反例）。
4. **内联 CHECK 具名**：`DdlNamingTest.java:410-412` 对内联 CHECK 直接跳过，四份报告共列约 40 处未具名枚举 CHECK；G-D12 实现时去掉跳过并一次改基线。
5. **自由文本 / 应答副本列的内容清单（C4）**：`detail`、`before_text`、`after_text`、`change_reason`、`response_text`、`failure_message`、`last_error`、`handled_note` 逐列有允许内容清单，守护静态扫描写入点。
6. **会话与凭据等价列**：名字含 `session`、`token`、`ticket`、`cookie` 的非代理键列必须在 `data-classification.txt` 登记且不低于 `SENSITIVE`。
7. **计划改动 ADR 批次须留出处**：NEXT_BATCH_PLAN 把 ADR 条款挪批次时，同一 PR 在 ADR 影响面或台账记一行（条款、原批次、新批次、裁定人）。
8. **首次部署前清单**：ADR-0034 W2 卡、W2 主数据与日历版本化、审计分区（AE-2）、ADR-0043 实施 PR（R-2 gap、四段分级）并入 dev 部署闸门的人工核对项，避免「已排期」被逐卡默认放过。

## 6. 修复卡草稿

见 `repair-cards/README.md`（四张，按 BC 归并，基线仍在开放期故直接改基线并附预期表结构 diff）：

| 卡 | 范围 | 建议负责会话 |
|---|---|---|
| DATA-REPAIR-identity | platform_identity | one-1a-next-4 |
| DATA-REPAIR-workforce | organization_workforce | one-1a-next-4 |
| DATA-REPAIR-approval | platform_approval、platform_integration、platform_numbering | one-1a-next-4 |
| DATA-REPAIR-timesheet-audit | cost_timesheet、organization_attendance、platform_audit、platform_idempotency | one-1a-par（工时与日历）、one-1a-next-4（审计） |

与在途卡的关系：P5 已解决 HS-1、AE-1、RP-1 / AR-1 的主键部分、AB-1；TS-UNIQ 承接 T-1、X-6（`numbering_rule_version_id`）与状态机；W2 承接 R-1、RP-1 / AR-1 的历史、AQ-2、P-1、E-1、WY-2。排期由 one-coordinator 定。门禁生效后，这些修复卡的预期表结构 diff 在表结构 PR 前由 one-data 复核。

## 7. 待用户决定（已登记收件箱，不由本会话裁定）

分报告共列约 40 项，去重并剔除已决后归为下列几类，在收件箱按事项登记：

1. **保留期与匿名化**：离职人员姓名、邮箱等在 `employees` 里留多久；`assignment_versions` 等个人任职史是否永久；审批四表、`process_archive_skips`、`outbound_deliveries`、`authorization_requests`、`consumed_events`、`idempotent_responses`、`audit_archive_batches`、`sync_run_held_separations`（注册表 1 年，实际随运行保留 2 年）的保留口径；`source_references` 含来源人员标识且 `PERMANENT`，按 ADR-0043 R-2 需用户决定出处。
2. **分级**：`sessions.session_id`、`primary_id`（建议 `RESTRICTED + NONE`，待确认）、`login_states.binding_hash`、各自由文本列与 `idempotent_responses.response_text`、`employment_status` 等；U-2、U-7b 仍未决。
3. **审计分区方案（AE-2）**：是否修订 ADR-0033，写明「按时间分区的只追加表，主键为（UUIDv7 ID，分区键）」，事件幂等唯一改为（事件 ID，发生时刻）；基线开放期内做。
4. **主数据版本化方式（D17）**：`employees`、`positions` 走维度型版本表（opus 建议），还是「不需要」并选四类理由之一；`separated_at` 去留与口径。
5. **审批流转**：被驳回、撤销后可否再发起（`REJECTED → RUNNING`、`CANCELLED → RUNNING` 现在代码允许）；加签人驳回是否也须填原因；`hookDecidesReason` 删除（与 RULE-280 冲突，无钩子实现）。
6. **其余小项**：法人状态与 OBJ-54 的对应、工号 / 部门编码 / 岗位编码的唯一口径、`legacy_numbers` 唯一范围（需 one-ops 按 ADR-0037 取证是否有跨法人重复原编号）、日历版本化确认人、工时单的部门口径（工作日当天 / 提交当时）、`sso_ticket_uses` 保留口径、全库时间默认值是否统一由应用传时钟。

## 8. 未核实

- 四份报告的 O15（ADR 逐条对照）未完整重做；其余 71 条「中」与 84 条「低」未逐条复核（opus 只抽查了 6 条中级）。
- 未起数据库，未跑守护测试与 `pg_dump`；表结构全按基线 SQL 文本推算。
- positions 是否在 W2「主数据版本化 25 张表」清单内；测试夹具是否直接插入「已确认、无确认人」的授权申请或编号版本（加 CHECK 前须 grep `backend/src/test`）；`RunStats` 各字段是否全非空；Spring Session JDBC 能否只存会话标识摘要；生产 PostgreSQL 版本是否 ≥ 15（`NULLS NOT DISTINCT`）；TS-UNIQ 卡正文。

## 9. 更正（2026-10-16，用户「一次投产、老系统加 A8 不接、全量历史迁入」，intent 2026-10-16-133348；one-architecture 判定）

以下条目随「生产不再同步」改变口径；正式改动已采纳 ADR-0039 / 0021 / 0033 等须待 one-architecture 的过渡 ADR（施工方案定稿后起草），**在此之前已建的表与代码不删，只停新增工作**：

| 条目 | 原结论 | 更正 |
|---|---|---|
| `sync_runs`、`sync_diffs`、`sync_run_held_separations`（HS-1、HS-2、HS-3、SD-1、O15 缺项 3、4；ADR-0039 拆分 a / b） | 迁 `platform_integration` / 改离职记录 | **取消方向**：不再开新工作；已建表等过渡 ADR 一并退役；dev 测试数据改用一次性导入 |
| `employees.login_name`（O15 缺项 5） | DA-3 删 | 取消（A8 登录名不再用）；`wecom_user_id` 与 `external_identities` 保留（企业微信仍是外部服务，Q-03：新员工以企业微信登录） |
| `source_references` 的 `ORG_SOURCE` 类别 | 持续同步对照 | 仅作一次性迁入的来源对照（追溯老编号），不再用于持续同步；人员类保留期仍按 `RETENTION_PROPOSAL.md` |
| `employees` / `positions` / 组织主数据 | A8 同步快照 | 成为本系统人事登记主数据；版本表生效依据为人事业务生效日（DECLARED，intent 2026-10-16-134434）；`separated_at` 改为人事登记的离职日（业务日期，与 U-12 一致），E-2 按此重定 |
| 迁入与对账方案 | 分阶段迁入 | 一次性全量迁入（含全部历史明细，U-4）＋对账，另加投产前两个已结期间新系统重算与老结果比对（U-10）；one-data 仍是映射与对账负责方 |
