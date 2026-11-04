# 已建成表审查补充：全量机械扫描、补审、级别调整

> 冻结（2026-11-04，#1263）：表审查记录，已完成，不再更新。

- 日期：2026-10-16 · 会话：one-data 助手 · 范围：`backend/src/main/resources/db/migration/*/` 全部基线 SQL，**读自 `origin/main`（a27bf877）**，不是本分支（本分支落后：main 已改 workforce、audit、identity 三份基线，门禁清单与 `DATA_CONTEXT.md` 也更新）。因此下文「文件:行」是 main 版行号，与四份分报告里的旧行号可能差几行。
- 扫描对象：10 个 schema 去掉框架表 `event_publication*`、`sessions*`、`scheduler_locks` 后的 44 张表、486 列；框架表不计。门禁清单用 main 的 `DATA_DESIGN_GATE.md`（已核含 B0、B3a、O10a、O15、O16；O16 在第 108 行）。
- 方法：脚本按括号配平解析每个 `CREATE TABLE`（列、表级约束、`COMMENT ON COLUMN`、`ALTER TABLE`），再逐项判定；每条「没有某约束」的结论另用 `grep -n CHECK` 对全部基线 SQL 复核（例：`released_at`、`held_reason`、`failure_reason`、`finished_at`、`revoked_at`、`separated_at`、`last_login_address`、`result_reason`、`record_reason`、`on_behalf_of_id`、`sent_at`、`response_number`、`overdue_at`、`escalated_at`、`issue_kind`、`submitted_date` 没有出现在任何 CHECK 行；`decided_by` 只出现在 `ck_source_references_*` 与 `ck_authorization_requests_two_people`）。拿不准的写「待核」。
- 简称：cts=cost_timesheet、att=organization_attendance、wf=organization_workforce、apv=platform_approval、aud=platform_audit、idm=platform_idempotency、idt=platform_identity、itg=platform_integration、num=platform_numbering；文件名是各目录下唯一的 `V…__<schema>_baseline.sql`。`表.列:行` = 该列定义所在行。
- 条数：§1 87 列；§2 18 项业务默认值；§3 113 列；§4 14 组缺项（另 1 组对照）；§5 89 列；§6 36 处；§7 5 条；补审 B7 4 条；补审 O15 缺项 8 条（另待核 2、旧结论更正 1）；级别调整 38 条。

## 1 无格式约束的 text 列（C4 自由文本全量，不按列名挑）

口径：`text` 与 `text[]` 列，无列内 CHECK、无表级「正则 / char_length / 枚举」CHECK。符号：n=可空，D=已在 `data-classification.txt` 登记，A=`text[]`，`*`=只有非空或非特定值的 CHECK（`legal_entity_attribution_corrections.correction_reason:415` 只有 `btrim <> ''`；`ticket_issuers.allowed_role_codes:157` 只有「不含 super_admin」）。

- 全库 `char_length` 或 `length(` 出现次数：**0**（grep 全部基线 SQL）；即 87 列都没有长度上限。其中 16 列已登记分级（D），71 列未登记（默认 INTERNAL + NONE）。
- 注释声明了长度但库里没有 CHECK：`idm.idempotent_responses.idempotency_key:8`（注释「至多 128 个可见字符」）；`num.numbering_rule_versions.template_text:98`（注释只限占位符 `{seq:N}` 的 N 为 1 至 12，没有总长度）。
- **已知必须出现项：`apv.approval_requests.summary_text:20`（NOT NULL，无任何 CHECK，未登记）已找到。**
- 未登记但内容可能含个人信息、需 C4「允许写入内容清单」的列（待核，是否含 PI 由写入点决定）：`summary_text`、各 `result_reason` / `record_reason` / `change_reason` / `handled_note` / `last_error` / `failure_message` / `detail` / `before_text` / `after_text` / `response_text` / `title` / `description`、`sync_diffs.subject_key`。

- cts｜timesheets（1）timesheet_number:17；consumed_events（1）consumer_name:49
- wf｜positions（2）code:7[n] name:8；org_units（3）code:27[n] name:28 short_name:36[n]；employees（5）employee_code:64[D] display_name:65[D] login_name:66[nD] email:67[nD] wecom_user_id:69[nD]；sync_runs（2）held_reason:138[n] failure_reason:139[n]；sync_diffs（4）subject_key:187 issue_kind:189[n] detail:190[n] changed_field_codes:193[nA]；legal_entities（1）name:213；org_unit_versions（2）code:312[n] name:313；legal_entity_versions（2）code:383 name:384；legal_entity_attribution_corrections（1）correction_reason:415*
- apv｜approval_requests（3）process_key:7 result_reason:13[n] summary_text:20；approval_tasks（4）node_key:71 approved_hash:78[n] result_reason:86[n] node_name:87；approval_task_records（3）node_key:135 node_name:136 record_reason:141[n]；process_archive_skips（2）object_key:192 failure_message:193
- aud｜audit_entries（6）action:8 detail:13[n] change_reason:18[n] before_text:19[n] after_text:20[n] source_address:21[nD]；audit_archive_batches（2）object_key:74 sha256_hash:75
- idm｜idempotent_responses（5）idempotency_key:8 request_hash:9 response_name:11[n] response_text:12[n] response_header_text:13[n]
- idt｜user_accounts（1）last_login_address:9[nD]；roles（2）code:29 name:30；role_permissions（1）permission_code:58；navigation_items（3）name:93 route_path:94[n] permission_code:95[n]；login_states（2）state_hash:115[D] binding_hash:120；sso_ticket_uses（2）ticket_source:134 ticket_hash:135[D]；ticket_issuers（4）issuer_code:153 signing_key_code:154 key_reference:155[D] allowed_role_codes:157*[A]；emergency_admins（3）account_name:175[D] password_hash:176[D] totp_secret_encrypted:177[D]；browser_identifiers（1）identifier_hash:247[D]；authorization_requests（4）role_codes:274[A] new_role_code:275[n] role_name:276[n] permission_codes:279[A]
- itg｜outbound_deliveries（9）title:41 description:42[n] target_url:43 last_error:47[n] response_code:48[n] handled_note:50[n] handled_result:51[n] recipient_codes:59[DA] skipped_recipient_codes:60[nDA]
- num｜numbering_rules（1）pattern:8[n]；numbering_counters（2）document_type:35 period_key:38；legacy_numbers（2）document_type:60 legacy_number:61；numbering_rule_versions（1）template_text:98
- att｜无（work_calendar_years、work_calendar_days 的 text 列都有枚举 CHECK 或不存在）

## 2 业务默认值（DEFAULT 不是技术初值）

排除项：`DEFAULT now()` 89 列（见 §5）；技术初值 `DEFAULT 0` 共 39 列（`version` 17、`event_count` 14、`revision_number` 3、`round_number`、`sort_order`、`last_totp_step`、`failed_attempts`、`attempt_count` 各 1；其中 `emergency_admins.failed_attempts:179`、`last_totp_step:178` 是计数初值，按 §2.4a-3「`version` 初值」同类处理，待核）。其余 18 项为业务默认值，NAMING_STANDARD §2.4a-3 要求由应用显式写入：

- wf.org_units.is_delivery_unit:35 DEFAULT false；wf.employees.is_fee_earner:71 DEFAULT false；wf.sync_runs.hold_kind_codes:147 DEFAULT '{}'
- wf.sync_runs.released_kind_codes:148 DEFAULT '{}'；idt.user_accounts.status:7 DEFAULT 'ENABLED'；idt.roles.kind:31 DEFAULT 'CUSTOM'
- idt.roles.status:32 DEFAULT 'ENABLED'；idt.roles.access_scope:33 DEFAULT 'SELF'；idt.roles.is_administrative:34 DEFAULT false
- idt.navigation_items.is_enabled:97 DEFAULT true；idt.ticket_issuers.is_enabled:156 DEFAULT false；idt.ticket_issuers.allowed_role_codes:157 DEFAULT '{}'
- idt.emergency_admins.is_enabled:181 DEFAULT true；idt.authorization_requests.role_codes:274 DEFAULT '{}'；idt.authorization_requests.permission_codes:279 DEFAULT '{}'
- itg.outbound_deliveries.kind:54 DEFAULT 'CARD'；num.numbering_rules.is_active:11 DEFAULT true；num.numbering_rules.confirmation_status:15 DEFAULT 'PENDING_CONFIRMATION'

- **已知必须出现项：`idt.user_accounts.status:7 DEFAULT 'ENABLED'` 已找到。** 另 `idt.roles` 三项（:31-33）分报告 R-5 已记；其余 15 项分报告未记（`is_enabled` ×3、`is_administrative`、`is_delivery_unit`、`is_fee_earner`、`'{}'` 数组 ×5、`kind 'CARD'`、`is_active`、`numbering_rules.confirmation_status`）。

## 3 可空列注释缺「为空表示」

可空且非主键的列 134 个：注释含字面「为空表示」21 个；含等价措辞（「为空」「根为空」「留空」等，守护是否接受待核）29 个；**没有任何空值含义说明 84 个**（其中 16 个有「才有」「仅」「清空」类隐含说明，标 ⁱ）。

等价措辞（29）：
- cts｜timesheets.submitted_date:19
- wf｜org_units.parent_org_unit_id:29 org_units.region_category:34 employees.login_name:66 employees.gender:68 assignments.position_id:102 org_unit_versions.parent_org_unit_id:314
- apv｜approval_tasks.assignee_id:73 approval_tasks.due_at:74 approval_tasks.remind_at:81 approval_tasks.outcome_kind:84 approval_tasks.result_reason:86 approval_task_records.on_behalf_of_id:139 approval_task_records.assignee_id:140 approval_task_records.record_reason:141
- aud｜audit_entries.actor_id:9 audit_entries.source_address:21 audit_entries.outcome_kind:22 audit_entries.login_method_kind:23
- idm｜idempotent_responses.response_number:10
- idt｜navigation_items.parent_item_id:92 navigation_items.route_path:94
- itg｜outbound_deliveries.trigger_event_id:40 outbound_deliveries.response_code:48 outbound_deliveries.skipped_recipient_codes:60 source_references.created_by:143
- num｜numbering_counters.legal_entity_id:37 legacy_numbers.legal_entity_id:63 numbering_rule_versions.author_id:102

无空值说明（84）：
- wf｜positions（2）code:7 sort_order:9；org_units（7）code:27 sort_order:30 head_employee_id:32 regional_receivables_manager_id:33 short_name:36 revoked_at:37 last_sync_run_id:38；employees（4）email:67 wecom_user_id:69 separated_at:72 last_sync_run_id:73；assignments（1）reports_to_employee_id:103ⁱ；sync_runs（18）finished_at:125 source_org_unit_count:126 source_employee_count:127 incoming_active_org_unit_count:128 incoming_employed_count:129 previous_active_org_unit_count:130 previous_employed_count:131 org_unit_change_count:132 employee_change_count:133 position_change_count:134 isolated_row_count:135 issue_count:136 employed_without_wecom_count:137 held_reason:138 failure_reason:139 released_by:140 released_at:141 released_by_run_id:142；sync_diffs（3）issue_kind:189ⁱ detail:190 changed_field_codes:193ⁱ；org_unit_versions（4）code:312 head_employee_id:316 regional_receivables_manager_id:317 region_kind:318；assignment_versions（2）position_id:352 reports_to_employee_id:353；legal_entity_attribution_corrections（1）correction_number:409
- apv｜approval_requests（2）current_instance_id:11 result_reason:13；approval_tasks（5）escalate_at:75ⁱ approved_hash:78ⁱ claimed_at:82ⁱ completed_at:83 effect_kind:85ⁱ
- aud｜audit_entries（7）on_behalf_of_id:10ⁱ detail:13 event_id:16ⁱ correlation_id:17 change_reason:18 before_text:19 after_text:20
- idm｜idempotent_responses（3）response_name:11 response_text:12 response_header_text:13
- idt｜user_accounts（2）last_login_at:8 last_login_address:9；authorization_requests（7）subject_user_id:272ⁱ role_id:273ⁱ new_role_code:275ⁱ role_name:276ⁱ is_enabled:277ⁱ decided_by:281 decided_at:282
- itg｜outbound_deliveries（8）description:42 next_attempt_at:46 last_error:47 sent_at:49 handled_note:50 handled_result:51ⁱ target_delivery_id:55ⁱ correlation_id:57
- num｜numbering_rules（4）pattern:8 reset_policy:9 start_value:10 confirmation_status:15；numbering_rule_versions（4）confirmed_by:105 confirmed_at:106 finance_confirmed_by:107 finance_confirmed_at:108

注：`num.numbering_rules` 的 4 列（pattern、reset_policy、start_value、confirmation_status）注释自写「停用，由 numbering_rule_versions 取代」，是停用列仍在基线（分报告 NR-1）。

## 4 成对可空列 / 状态 ⇒ 原因 缺成对 CHECK（应用保证）

依据：对 44 张表全部 CHECK / EXCLUDE 约束的清单逐表核对（见方法）。每组列出缺的约束；写 CHECK 前须核对 `BASELINE_TABLE_REVIEW.md` §4 的三条警示（`hookDecidesReason` 路径、`finished_at >= started_at` 不加、关闭任务保留处理人）。

1. wf.sync_runs：`released_by:140` / `released_at:141` / `released_by_run_id:142` 同空同非空，状态 RELEASED ⇒ 放行列非空；`held_reason:138` ⇔ HELD；`failure_reason:139` ⇔ FAILED；`finished_at:125` ⇔ 状态 ≠ RUNNING。该表在 :120-149 内没有任何跨列 CHECK。
2. wf.employees：`separated_at:72` ⇔ `employment_status:70 = 'SEPARATED'`；`login_name:66` 为空 ⇔ 离职（注释 :86）。表内无 CHECK。
3. wf.org_units：`revoked_at:37` ⇔ `status:31 = 'REVOKED'`。
4. wf.sync_diffs：`issue_kind:189` ⇔ `change_kind:188 = 'ISSUE'`；`changed_field_codes:193` ⇔ UPDATED（注释 :208「仅 UPDATE 类变更有值」）。
5. apv.approval_requests：`result_reason:13` ⇔ 状态 REJECTED / RETURNED / CANCELLED；`current_instance_id:11` ⇔ `current_round:10 > 0`（注释 :30「0 表示尚未发起」）。
6. apv.approval_tasks：已有 `ck_approval_tasks_deadline`、`_claimed`、`_completed`（:89-94）；缺反向（非 COMPLETED 时 `completed_at:83`、`outcome_kind:84`、`effect_kind:85`、`result_reason:86`、`approved_hash:78` 须空）、`outcome_kind` 与 `effect_kind` 同空同非空、`overdue_at:76` ⇒ `due_at:74`、`escalated_at:77` ⇒ `overdue_at:76`、PENDING ⇒ `assignee_id:73` 与 `claimed_at:82` 空。
7. apv.approval_task_records：`on_behalf_of_id:139` / `assignee_id:140` ⇔ `action_kind:137`（TRANSFERRED、REASSIGNED、代办）；`record_reason:141` 对 REJECTED、RETURNED、TRANSFERRED、REASSIGNED 须非空。
8. aud.audit_entries：`source_address:21` / `outcome_kind:22` / `login_method_kind:23` 同空同非空且对应登录与会话类 `subject_kind:11`；仅 `ck_audit_entries_subject_id:24` 约束了 `subject_id`。
9. idm.idempotent_responses：`response_number:10` / `response_name:11` / `response_text:12` / `response_header_text:13` 同空同非空（处理中为全空）。
10. idt.user_accounts：`last_login_at:8` / `last_login_address:9` 同空同非空。
11. idt.authorization_requests：`decided_by:281` / `decided_at:282` ⇔ `request_status:280 ≠ 'PENDING'`（:288 的 `ck_authorization_requests_two_people` 对「CONFIRMED 且 decided_by 为空」放行，即 AQ-1）；`request_kind:269` ⇔ `subject_user_id:272` / `role_id:273` / `new_role_code:275` / `role_name:276` / `is_enabled:277`（注释「某类型才有」）。
12. itg.outbound_deliveries：`sent_at:49` ⇔ SENT；`next_attempt_at:46` ⇒ PENDING；`last_error:47` ⇒ FAILED / SKIPPED / DEAD_LETTER；`handled_note:50` / `handled_result:51` 同空同非空。已有 :61 的 `kind ⇔ target_delivery_id`。
13. num.numbering_rule_versions：已有 `confirmed_by:105` / `confirmed_at:106` 成对（:118）；缺 `status:104 = 'CONFIRMED'` ⇒ 确认人与时间非空（NV-2），即 INSERT 一条无确认人的 CONFIRMED 可绕过四眼。
14. cts.timesheets：`submitted_date:19` ⇔ `round_number:12 > 0`（注释 :44「空表示从未提交」）；表内 CHECK 只有 :21-23 三条非负约束。
15. 已含（对照）：itg.source_references `ck_source_references_decision`（:157）；num.numbering_counters `scope_kind ⇔ legal_entity_id`（:44）；wf 的 `legal_entity_attributions`、`corrections` 的「恰有一个非空」；`org_unit_legal_entities` 等的区间 CHECK 与排他约束。

## 5 `DEFAULT now()` 与「应用注入时钟」矛盾

依据：NAMING_STANDARD §2.4a 第 3 条「业务值与时间一律由应用显式写入」；`backend/architecture/sql-database-clock-exceptions.txt` 文件头「写入时刻取注入的 Clock」。**44 张表全部有 `DEFAULT now()`，共 89 列**（`created_at`、`updated_at` 各 44，另 `cts.consumed_events.consumed_at:50`）。

- 列注释与默认值直接矛盾（唯一一处）：`aud.audit_entries.created_at:14 DEFAULT now()`，而其注释（aud:39）写「创建时间：即审计事实发生的时间，来自注入的时钟」。
- 种子 INSERT 里直接调用数据库时钟：`num` 基线 :179（`numbering_rule_versions.authored_at` 取 `now()`）。
- 分报告只在 identity 记了此项（X-1）；其余 8 个 schema 未记。逐表行号（created@行,updated@行）：
- cts｜timesheets(created@14,updated@15) consumed_events(consumed@50,created@51,updated@52)
- att｜work_calendar_years(created@9,updated@10) work_calendar_days(created@30,updated@31)
- wf｜positions(created@11,updated@12) org_units(created@39,updated@40) employees(created@74,updated@75) assignments(created@104,updated@105) sync_runs(created@143,updated@144) sync_diffs(created@191,updated@192) legal_entities(created@215,updated@216) legal_entity_attributions(created@241,updated@242) org_unit_legal_entities(created@265,updated@266) employee_legal_entities(created@289,updated@290) org_unit_versions(created@320,updated@321) assignment_versions(created@354,updated@355) legal_entity_versions(created@386,updated@387) legal_entity_attribution_corrections(created@417,updated@418) sync_run_held_separations(created@448,updated@449)
- apv｜approval_requests(created@14,updated@15) start_rounds(created@48,updated@49) approval_tasks(created@79,updated@80) approval_task_records(created@143,updated@144) process_archive_skips(created@195,updated@196)
- aud｜audit_entries(created@14,updated@15) audit_archive_batches(created@77,updated@78)
- idm｜idempotent_responses(created@15,updated@16)
- idt｜user_accounts(created@10,updated@11) roles(created@35,updated@36) role_permissions(created@59,updated@60) account_roles(created@76,updated@77) navigation_items(created@98,updated@99) login_states(created@118,updated@119) sso_ticket_uses(created@137,updated@138) ticket_issuers(created@158,updated@159) emergency_admins(created@182,updated@183) browser_identifiers(created@250,updated@251) authorization_requests(created@285,updated@286)
- itg｜outbound_deliveries(created@52,updated@53) source_references(created@146,updated@147)
- num｜numbering_rules(created@12,updated@13) numbering_counters(created@40,updated@41) legacy_numbers(created@65,updated@66) numbering_rule_versions(created@109,updated@110)

## 6 内联枚举 CHECK 未具名（不是 `CONSTRAINT ck_… CHECK`）

共 **36 处 / 19 张表**（其中 2 处是数组 `<@ ARRAY[…]` 形式）。另有 12 处枚举 CHECK 已具名（wf `org_unit_versions` ×2、`legal_entity_versions` ×1；aud `subject_kind`；itg `outbound_deliveries` ×2、`source_references` ×3；num `counter_scope_kind`、`legacy_numbers.source_system`、表级 `ck_numbering_rules_document_type`）。内联数值 CHECK 未具名另有 8 处（不计入 36）：`sync_diffs.diff_number:185`、`approval_requests.current_round:10`、`start_rounds.round_number:45`、`approval_task_records.record_number:131`、`round_number:132`、`numbering_rules.start_value:10`、`numbering_rule_versions.version_number:97`、`start_value:100`。

- cts｜timesheets 1（status:11）
- att｜work_calendar_days 1（day_kind:29）
- wf｜positions 1（status:10）；org_units 2（status:31、region_category:34）；employees 2（gender:68、employment_status:70）；sync_runs 4（trigger_kind:122、status:123、hold_kind_codes:147、released_kind_codes:148）；sync_diffs 2（entity_kind:186、change_kind:188）；legal_entities 1（status:214）；小计 12
- apv｜approval_requests 2（status:12、document_type:17）；approval_tasks 3（status:72、outcome_kind:84、effect_kind:85）；approval_task_records 1（action_kind:137）；小计 6
- aud｜audit_entries 2（outcome_kind:22、login_method_kind:23）
- idt｜user_accounts 1（status:7）；roles 3（kind:31、status:32、access_scope:33）；login_states 1（entry:116）；authorization_requests 3（request_kind:269、access_scope_kind:278、request_status:280）；小计 8
- itg｜outbound_deliveries 2（status:44、kind:54）
- num｜numbering_rules 2（reset_policy:9、confirmation_status:15）；numbering_rule_versions 2（reset_policy:99、status:104）；小计 4
- 与分报告核对：wf 的 X-7 写「13 处」，逐项核对实为 12 处；apv 组 X-2 的 12 处、identity 的 8 处、timesheet 组的 4 处与本表一致；`BASELINE_TABLE_REVIEW.md` 说的「约 40 处」实为 36 处。

## 7 B7：枚举 CHECK 含本阶段未建模块 / 对象的取值（按 CS-1.8 判）

判定依据：`transitions.yaml`——TA-1a（进行中）交付 identity、approval、integration、audit、numbering、scheduling、idempotency、eventing、web、workforce、attendance、timesheet；TA-1b `presales.quoting`、TA-4 `accounting.ledger` 等均为 not-started；`module-dependencies.txt` 无这些模块。

1. **`apv.approval_requests.document_type:17`** 的 CHECK 含 `QUOTE`（presales.quoting，TA-1b）、`JOURNAL_VOUCHER`（accounting.ledger，TA-4），均未建；`TIMESHEET`（cost.timesheet）已建。**已知必须出现项已找到。** 现状只有 `ProcessKey.COST_TIMESHEET_APPROVAL` 一个流程（分报告 AR-2）。
2. **`num.numbering_rules:18` 表级 `ck_numbering_rules_document_type`** 同样含 `QUOTE`、`JOURNAL_VOUCHER`；另有种子数据：`numbering_rules` :173、:175，`numbering_rule_versions` 种子 :181（QUOTE、JOURNAL_VOUCHER 各一行，待确认）。基线注释 :170 引 ADR-0010 第 5 项「三种单据都从默认模板起步」，是否视为已决定「现在就建」待 one-architecture 判。
3. `idt.roles.access_scope:33` 与 `idt.authorization_requests.access_scope_kind:278` 含 `CUSTOM_ORG_UNITS`：注释（:48）称范围明细随 1b 的 `data_scope_grants`，该表在基线与 ADR 中不存在（分报告 R-4，待核）。
4. `num.legacy_numbers.source_system:59` 含 `ORG_SOURCE`、`ACCOUNTING_SYSTEM`：组织源不持有单据编号（分报告 LN-2）；`ACCOUNTING_SYSTEM` 在 `itg.source_references` 已被已建代码使用（`SourceReference.java:80`，登记核算组织编码），不判。
5. 已逐个核对、取值全部指向已建模块或已建对象：aud `subject_kind`（19 值）、`login_method_kind`；itg `subject_kind`、`purpose_kind`、`source_object_kind`；wf 全部枚举；apv 其余；cts、att。门禁清单 B7 原文是「信息图标『暂不定』的属性不落库」，与本任务口径（未建模块取值）不同，信息图「暂不定」项未逐个比对，待核。

## 补审：B7 与 O15（对 apv / itg / num 与 wf 两份旧版清单分报告）

两份分报告按旧版门禁清单判（无 B7 逐项、O15）。下面按 main 的门禁清单补两类条目。级别按「分级参照表」。

### 补审 B7（除 §7 的枚举 CHECK 外的落库项）

- B7-1 `num.numbering_rule_versions.finance_confirmed_by:107` / `finance_confirmed_at:108`、CHECK :114-118 的财务确认部分、列注释 :136：只服务 `JOURNAL_VOUCHER`（`NumberingRule.NEEDING_FINANCE_CONFIRMATION`），该单据所属模块未建。中（同 §7 第 2 条）；ADR-0010、ADR-0027 是否要求现在建，待 one-architecture 判。
- B7-2 `wf.org_units.regional_receivables_manager_id:33`、`org_unit_versions:317`（区域应收负责人，老系统「区域 AR」）、`is_delivery_unit:35`、`employees.is_fee_earner:71`：属组织源同步字段，使用方（receivables.*、delivery.execution、cost.costing）未建；不是枚举 CHECK，不计 §7。待核：这些字段是否在 ADR-0033 / 同步规格里被点名要求同步。
- B7-3 apv 与 itg 其余枚举（`action_kind`、`purpose_kind`、`outcome_kind` 等）取值都在已建模块内，无条目。
- B7-4 wf 的 OU-5（信息图「单元类型」未落库）与 LE-1（法人状态二值对三值）方向相反（缺而非多），不属 B7；旧报告标 B7 的 LN-2 与 §7 第 4 条合并。

### 补审 O15：适用 ADR 条款对预期 diff（已含 / 缺 / 不适用）

条款：①ADR-0033 第 1 项（主键、自然键转唯一、版本表键与引用）；②ADR-0033 第 2 项（来源对照）；③ADR-0033 第 7 项（文本 ID 拆「类别 + uuid」）；④ADR-0036（迁入）；⑤ADR-0039（防腐层落点）；⑥ADR-0043（分级与保留）。「缺」后方括号对应下表证据编号；「–」= 不适用。

| 表 | ① | ② | ③ | ④ | ⑤ | ⑥ |
|---|---|---|---|---|---|---|
| wf.positions | 含 | – | – | – | – | – |
| wf.org_units | 含 | – | – | – | 缺[3] | – |
| wf.employees | 含 | – | – | – | 缺[3][5] | 缺[8] |
| wf.assignments | 含（1:1，主键 employee_id） | – | – | – | – | – |
| wf.sync_runs | 含 | – | – | – | 缺[3] | – |
| wf.sync_diffs | 含 | – | 缺[1] | – | 缺[3] | – |
| wf.legal_entities | 含 | – | – | – | – | – |
| wf.legal_entity_attributions | 含 | – | 含（7.4） | – | – | – |
| wf.org_unit_legal_entities / employee_legal_entities | 含（1.3，排他约束） | – | – | – | – | – |
| wf.org_unit_versions / assignment_versions / legal_entity_versions | 含（1.3） | – | – | – | – | – |
| wf.legal_entity_attribution_corrections | 含（1.12 允许 `_number` 序列）[10] | – | 含（7.4） | – | – | – |
| wf.sync_run_held_separations | 含（P5 已改） | – | – | – | 缺[4] | – |
| apv.approval_requests / start_rounds / approval_tasks / approval_task_records | 含 | – | 含（7.5） | – | – | – |
| apv.process_archive_skips | 含（1.2，唯一 process_instance_id） | – | – | – | – | – |
| itg.outbound_deliveries | 含 | – | 缺[2] | – | 含（位置在集成层） | 缺[8] |
| itg.source_references | 含 | 含 | – | 缺[6]（时机未到） | – | 待核[9] |
| num.numbering_rules / numbering_counters | 含（1.2） | – | – | – | – | – |
| num.numbering_rule_versions | 含（1.3，唯一 rule+版本号）；区间列待核[11] | – | – | – | – | – |
| num.legacy_numbers | 含（2.7 的 source_system 与唯一） | 含 | – | 含（migration_batch_id NOT NULL） | 缺[7]（可延后） | – |

缺项证据（每条已核 SQL）：
1. ADR-0033 第 7 项第 2 条点名「`sync_diffs.subject_key` 同样拆分」：wf:187 仍是 `subject_key text NOT NULL`；同一条对 `audit_entries` 已落实（aud:11-12 `subject_kind` + `subject_id`）。**高**（R-1b 范围）。旧分报告 SD-1 以 D1 / D16 判中，已在「级别调整」升为高。
2. ADR-0033 第 7 项第 3 条：投递去重改为「对象类别、对象 ID、收件人 `user_id`、用途类别」结构化列。itg:63 `uk_outbound_deliveries_card (subject_kind, subject_id, purpose_kind, trigger_event_id) … WHERE kind = 'CARD'` **不含收件人**；表上无收件人 `user_id` 列，收件人只是 `recipient_codes text[]`（itg:59）。这正是门禁清单 O15 的例子。**高**（R-1b 清单 / DA-10 首次部署前）；改法涉及「一张卡一行」是否变成「一收件人一行」，需结构设计，旧分报告未记。
3. ADR-0039 第 2 条第 1 款、第 6 条：组织来源同步（运行记录、差异）搬进 `platform_integration`，生产前完成。wf:120 `sync_runs`、wf:182 `sync_diffs` 仍在 organization_workforce，`org_units.last_sync_run_id:38` / `employees.last_sync_run_id:73` 仍指向运行记录（ADR 影响面：改引用变更集）。**高**（已排期生产前）。旧分报告未提 ADR-0039。
4. ADR-0039 第 2 条第 2 款：`sync_run_held_separations`（wf:444）改为 workforce 的「待放行离职」`separation_hold` 记录，放行只放这一条离职；现仍以 `sync_run_id` 外键挂在运行记录下并 `ON DELETE CASCADE`（wf:452）随其删除。**高**（同 3）。
5. ADR-0033 第 4 项第 5 条、ADR-0039 第 4 条第 1 款：`employees` 删 `login_name`、`wecom_user_id`（DA-3，首次部署前），`external_identities` 建在 `platform_integration`。wf:66、:69 仍在，两个基线里都没有 `external_identities`（全部基线 SQL grep 无命中）。**高**（已排期首次部署前）。分报告 E-3 只就索引与空串记了低，未记 ADR 缺口。
6. ADR-0036 第 3 条：`source_references` 新增 `migration_batch_id`、`source_row_digest`、`source_reference_status` 三列与成对 CHECK，两个自然键改为只约束「已确认且当前」。itg:134-170 无这三列。首个迁入（NEXT_BATCH_PLAN P6）前补，按 CS-1.8 现在不算缺陷，卡里须列。无级别。
7. ADR-0039 第 4 条第 2 款：`legacy_numbers`（num:57）整表搬到 `platform_integration`，「可放生产后（首个迁入阶段前）」，允许延后。`migration_batch_id uuid NOT NULL`（num:64）已含，但被引用的 `migration_batches` 表尚不存在（无外键可建）。无级别。
8. ADR-0043 第 3 项：`data-classification.txt` 仍把 `employees.email`、`gender`、`wecom_user_id`、`display_name`、`outbound_deliveries.recipient_codes`、`skipped_recipient_codes` 登记为 SENSITIVE（`data-classification.txt` 第 6、7、19、21、23、24 行），ADR 判 INTERNAL + PI（U-1、U-4 已定）；文件仍是三段格式，无第二维。**中**（分级缺登记；旧分报告 X-4、OD-6 已记）。
9. ADR-0043 第 6 项 R-2：`itg.source_references` 注册表 `PERMANENT`，`source_id` 对 EMPLOYEE 类别是来源人员标识，是否 PI 待用户定（旧分报告「待用户决定」第 4 项）。待核。
10. ADR-0033 第 1 项第 12 条：编号计数值、轮次等序号可由数据库序列生成，守护只禁主键与 `_id` 列；`correction_number`（wf:409，IDENTITY）列名以 `_number` 结尾，不违反。旧分报告 CR-1 的依据（「ADR 的精神」）不成立，级别维持低，建议 one-data 复核后改写依据。
11. ADR-0033 第 1 项第 3 条的「版本 / 生效区间表」：`num.numbering_rule_versions` 只有 `effective_from_date`（num:101），没有 `effective_to_date` 与排他约束，是否按该条处理待核（旧分报告 NV-3）。
- 另：ADR-0033 第 1 项第 5 条（使用方记版本行 UUID）的缺口在 cts.timesheets（无 `numbering_rule_version_id`），不在本两组表内，旧分报告 X-6 已记并在「级别调整」升为高，不重复。

## 级别调整（任务 C）

规则：按分级参照表逐条核对四份分报告；违反已采纳 ADR 且已排期或已由 R-1b / P5 修复的记高，未排期记阻断；成对可空列缺 CHECK、缺外键、缺外键索引、自由文本无长度上限、保留期缺登记、分级缺登记、B7 取值记中；内联 CHECK 未具名、业务默认值、缺「为空表示」、`DEFAULT now()`、命名、注释记低。**一条含多个子项的，按其中最高的表内档计**；表里没有的类别（如索引、D17、注册表登记）不动。

新旧总数（四份分报告合计 181 条，条数不变）：

| 报告 | 旧 阻断/高/中/低 | 新 阻断/高/中/低 |
|---|---|---|
| platform_approval_integration_numbering | 0 / 1 / 20 / 20 | 0 / 2 / 22 / 17 |
| organization_workforce | 1 / 3 / 22 / 26 | 0 / 4 / 28 / 20 |
| platform_identity | 3 / 4 / 21 / 22 | 0 / 8 / 22 / 20 |
| timesheet_attendance_audit_idem_sched | 4 / 4 / 14 / 16 | 0 / 8 / 17 / 13 |
| **合计** | **8 / 12 / 77 / 84** | **0 / 22 / 89 / 70** |

改动清单（编号、原级别 → 新级别、依据）：
- apv：AR-1 高→中（成对 CHECK，应用已保证）；NV-2 中→高（确认态无确认人，双人确认可绕过）；X-6 中→高（ADR-0033 第 1 项第 5 条，已并入 TS-UNIQ）；OD-6 低→中（分级）；NC-1 低→中（含缺外键）；LN-2 低→中（B7 取值）。
- wf：HS-1 阻断→高（R-1b 已排期，main 的 P5 已修复）；SR-1 高→中（成对 CHECK）；SD-1 中→高（O15：ADR-0033 第 7 项第 2 条点名）；OU-2 中→低（命名）；P-2 低→中（含 name 无长度上限）；SD-4 低→中（detail 无长度上限）；LE-3 低→中（name 无长度上限）；LA-1 低→中（缺外键）；CR-2 低→中（reason 无长度上限）；X-6 低→中（文本无长度上限）；X-9 低→中（缺外键）。
- idt：R-1、RP-1、AR-1 阻断→高（ADR-0034 / ADR-0033 已排期 W2、dev 前）；S-1 中→高（会话标识明文入库，等同凭据）；AQ-6 中→高（ADR-0033 第 1 项第 1 条、ADR-0034 第 2 项第 5 条，随 W2）；UA-1 高→中（保留期缺登记）；UA-2 低→中（成对）；UA-3 低→中（无长度上限）；N-4 低→中（无长度上限）；AQ-8 低→中（含无长度上限）；X-1 中→低（`DEFAULT now()`）；X-2 中→低（内联 CHECK 未具名）。
- cts / att / aud / idm：T-1 阻断→高（用户决定，TS-UNIQ 卡已排）；WY-1、AE-1、AB-1 阻断→高（ADR-0033，R-1b / P5 / W2 已排）；WY-3 中→高（ADR-0033 第 7 项第 1 条，main 的 P5 已改 `EventDraft`，日历聚合已不再用 `asText`）；T-7 低→中（保留期缺登记）；AB-4 低→中（保留期登记不一致）；IR-1 高→中（分级缺登记 + 无长度上限）；IR-4 低→中（成对）。
- 共 38 条改动：apv 6、wf 11、idt 12、cts 组 9。

**未改但与 `BASELINE_TABLE_REVIEW.md` / `OPUS_VERIFY.md` 现行口径不同的项**（表里没有对应类别，或存在反向判断，请 one-data 定）：
- 改后与 BASELINE 现行级别不同的：WY-1、AB-1（BASELINE 中，本表高）；AE-1、HS-1、T-1（BASELINE 阻断，本表高）；AR-1（BASELINE 高，本表中）；IR-1（BASELINE 高，本表中）；SD-1、X-6（BASELINE 中，本表高）。其余已改项与 BASELINE 一致（NV-2、S-1 高；SR-1、UA-1 中）。
- 表内无对应类别、保持分报告原级别：identity N-1（高；BASELINE 降中，因该表只由迁移维护）、R-2、AQ-4；audit AE-2、AE-3（高，已排期或待用户定）。
- IR-1 是争议最大的一条：它复制任意接口的应答正文，若按「安全不变量可被破坏」判则仍为高；本表按其可核的子项（分级缺登记、无长度上限）记中，请复核。
- `BASELINE_TABLE_REVIEW.md` §1 统计表与 §3 表、`OPUS_VERIFY.md` 未改（任务范围外）；按新口径合计应为 阻断 0、高 22、中 89、低 70，须 one-data 另行同步。
