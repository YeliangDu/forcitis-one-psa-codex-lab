# 执行点编号缺口

由 scan_execution_points.py 生成，勿手改。

仅登记源码中的执行点与编号引用，不代表守护测试已运行或业务规则已验证。

## 统计

- 守护：333
- 不变量：167
- 数据约束：52
- 授权：5
- 豁免：44
- enforces 非空率：2/601（0.33%）
- 无名约束数：44（迁移声明累计，不入登记）

## enforces 为空的条目（按来源文件分组）

### backend/architecture/legacy-shape-exceptions.txt（1 条）

- `EP-d1790334d7` — "src/main/java/com/forcitis/one/platform/approval/infrastructure/replay/LegacyVocabulary.java | OLD_FIELD_NAME | The one old-name to new-name table of the TIMESHEET replay (decision H2): exports made from the old system spell the old names, the replay translates them; every other file speaks the glossary names" — `backend/architecture/legacy-shape-exceptions.txt:7`

### backend/architecture/multi-aggregate-exceptions.txt（2 条）

- `EP-05878a8533` — "com.forcitis.one.cost.timesheet.application.SubmitTimesheet | Timesheet+NumberingRule | A document number must come from a gapless counter in the same transaction that inserts the document (DocumentNumbers.next); the TimesheetSubmitted of that transaction names NumberingRule in relatedAggregates" — `backend/architecture/multi-aggregate-exceptions.txt:5`
- `EP-18832823b6` — "com.forcitis.one.organization.workforce.application.ApplySourceSnapshot | SyncRun+OrgSnapshot | 运行结果与快照同生同灭，拆开会留下「已应用但运行未记」或反之的窗口；SyncRunSucceeded 与 OrgSnapshotChanged 在 relatedAggregates 里列出 OrgSnapshot" — `backend/architecture/multi-aggregate-exceptions.txt:6`

### backend/architecture/random-uuid-exceptions.txt（1 条）

- `EP-009b73ea87` — "com.forcitis.one.platform.identity.application.OperationsCommands  # the correlation id of a command run is a random UUID; OPS-CID gives it a Clock and Uuids.timeOrdered" — `backend/architecture/random-uuid-exceptions.txt:3`

### backend/architecture/repository-id-exceptions.txt（1 条）

- `EP-a641cbe37b` — "com.forcitis.one.organization.workforce.infrastructure.persistence.JdbcSyncRunRepository.insertRunning  # SyncRun gets its id in the repository; the synchronisation is rebuilt in split a / b (NEXT_BATCH_PLAN 7.11), which builds it with the id" — `backend/architecture/repository-id-exceptions.txt:3`

### backend/architecture/repository-write-exceptions.txt（22 条）

- `EP-084ecc3807` — "com/forcitis/one/platform/identity/infrastructure/persistence/JdbcOneTimeCredentials.java#consumeLoginState  # the atomic delete is the consumption, so a replay finds nothing" — `backend/architecture/repository-write-exceptions.txt:17`
- `EP-0dd16b04f5` — "com/forcitis/one/platform/identity/infrastructure/persistence/JdbcOneTimeCredentials.java#markTicketUsed  # the unique key is the replay check" — `backend/architecture/repository-write-exceptions.txt:18`
- `EP-169827f851` — "com/forcitis/one/platform/numbering/infrastructure/persistence/JdbcNumberingRuleRepository.java#reserveNext  # the counter is allocated by one atomic upsert; a read-modify-write through an aggregate would let two issuers read the same value" — `backend/architecture/repository-write-exceptions.txt:8`
- `EP-247ba7ddc4` — "com/forcitis/one/platform/identity/infrastructure/persistence/JdbcAccountRepository.java#recordLogin  # last-login time and address are access metadata, not an invariant of the account; an unconditional update that must not bump the account version and so must not fail a concurrent role change" — `backend/architecture/repository-write-exceptions.txt:3`
- `EP-2eb6772b40` — "com/forcitis/one/platform/identity/infrastructure/persistence/JdbcBrowserIdentifiers.java#invalidateAll  # removes every identifier of an account when it is disabled or its person leaves, in the transaction that ends its sessions" — `backend/architecture/repository-write-exceptions.txt:21`
- `EP-301d2f1d92` — "com/forcitis/one/platform/idempotency/infrastructure/JdbcIdempotentResponses.java#release  # drops the claim of a request that failed so that the key can be retried" — `backend/architecture/repository-write-exceptions.txt:15`
- `EP-49d7651086` — "com/forcitis/one/platform/idempotency/infrastructure/JdbcIdempotentResponses.java#finish  # completes the row this request claimed; no other writer holds it" — `backend/architecture/repository-write-exceptions.txt:13`
- `EP-4c825c969f` — "com/forcitis/one/platform/identity/infrastructure/persistence/JdbcOneTimeCredentials.java#purgeExpired  # removes expired one-time credentials; nothing refers to them" — `backend/architecture/repository-write-exceptions.txt:19`
- `EP-509de12bff` — "com/forcitis/one/platform/identity/infrastructure/persistence/JdbcBrowserIdentifiers.java#replace  # a browser identifier is a credential, not an aggregate: the old value is retired, the other accounts of the browser move to the new value and the new value is recorded in one transaction" — `backend/architecture/repository-write-exceptions.txt:20`
- `EP-5440099b67` — "com/forcitis/one/platform/identity/infrastructure/persistence/JdbcOneTimeCredentials.java#storeLoginState  # one-time credential, not an aggregate: stored once, consumed once" — `backend/architecture/repository-write-exceptions.txt:16`
- `EP-5b25c4c479` — "com/forcitis/one/cost/timesheet/infrastructure/persistence/JdbcConsumedEvents.java#firstTime  # the insert is the idempotency check: the unique key tells a first delivery from a repeat" — `backend/architecture/repository-write-exceptions.txt:9`
- `EP-85632e9228` — "com/forcitis/one/organization/workforce/infrastructure/persistence/OrgHistory.java#recordAssignments  # effective-dated history of the snapshot, closed and appended in the snapshot's own transaction under the snapshot writer lock" — `backend/architecture/repository-write-exceptions.txt:11`
- `EP-8c157cdd98` — "com/forcitis/one/organization/workforce/infrastructure/persistence/OrgHistory.java#recordOrgUnits  # effective-dated history of the snapshot, closed and appended in the snapshot's own transaction under the snapshot writer lock" — `backend/architecture/repository-write-exceptions.txt:10`
- `EP-8ec35a3655` — "com/forcitis/one/platform/integration/infrastructure/persistence/JdbcOutboundDeliveryRepository.java#claimDue  # claiming due deliveries is a queue operation: one statement counts the attempt and pushes next_attempt_at out by the lease, with FOR UPDATE SKIP LOCKED so that workers never claim the same row" — `backend/architecture/repository-write-exceptions.txt:7`
- `EP-9492cd7b1a` — "com/forcitis/one/platform/identity/infrastructure/persistence/JdbcEmergencyAdminRepository.java#reserveAttempt  # atomic check-and-increment of the attempt counter and lock-out time; the condition is the lock itself, a version would serialise nothing more" — `backend/architecture/repository-write-exceptions.txt:4`
- `EP-9edefe3fa2` — "com/forcitis/one/platform/identity/infrastructure/persistence/JdbcEmergencyAdminRepository.java#recordSuccess  # resets the failure counter in one statement" — `backend/architecture/repository-write-exceptions.txt:5`
- `EP-b0a19796ea` — "com/forcitis/one/platform/identity/infrastructure/persistence/JdbcEmergencyAdminRepository.java#acceptTotpStep  # one-time use of a TOTP step: the update applies only while the stored step is smaller, so a replayed code changes nothing" — `backend/architecture/repository-write-exceptions.txt:6`
- `EP-c8a7936394` — "com/forcitis/one/platform/idempotency/infrastructure/JdbcIdempotentResponses.java#claim  # the claim is the insert of the unique (user, key) row, or the takeover of an expired one in the same statement; it is not an aggregate" — `backend/architecture/repository-write-exceptions.txt:12`
- `EP-d2fd5c2aea` — "com/forcitis/one/platform/idempotency/infrastructure/JdbcIdempotentResponses.java#holdUntilExpiry  # an answer too long to store keeps its claim until the key expires: updated_at is set to expires_at so that no retry takes the claim over and runs the write again" — `backend/architecture/repository-write-exceptions.txt:14`
- `EP-ed09f391ee` — "com/forcitis/one/platform/idempotency/infrastructure/JdbcIdempotentResponses.java#purgeExpired  # removes answers past their 24 hours; nothing refers to them" — `backend/architecture/repository-write-exceptions.txt:23`
- `EP-f06fcee6d7` — "com/forcitis/one/organization/workforce/infrastructure/persistence/LegalEntityHistory.java#recordLegalEntity  # effective-dated history of the legal entity: closed and appended in the transaction of the save that changed it" — `backend/architecture/repository-write-exceptions.txt:24`
- `EP-fab8cda600` — "com/forcitis/one/platform/identity/infrastructure/persistence/JdbcBrowserIdentifiers.java#purge  # removes identifiers past their lifetime; nothing refers to them" — `backend/architecture/repository-write-exceptions.txt:22`

### backend/architecture/sensitive-response-exceptions.txt（3 条）

- `EP-d9d431a795` — "com.forcitis.one.platform.audit.web.OwnLoginRecordResponse#sourceAddress  # the signed-in user's own login history; the controller only ever returns the caller's own records, and seeing where one's own sessions came from is how a stolen session is noticed" — `backend/architecture/sensitive-response-exceptions.txt:3`
- `EP-e0cf7d0197` — "com.forcitis.one.platform.identity.web.CurrentUserResponse#displayName  # the signed-in user's own name for the page header; it is the caller's own record and never another employee's" — `backend/architecture/sensitive-response-exceptions.txt:4`
- `EP-fb54ca4d08` — "com.forcitis.one.platform.audit.web.AuditEntryResponse#sourceAddress  # the audit trail exists to say who acted from where; it is read only with audit-entry:read, held by the security auditor, and every read is itself audited" — `backend/architecture/sensitive-response-exceptions.txt:5`

### backend/architecture/source-type-exceptions.txt（9 条）

- `EP-042d21df7c` — "src/main/java/com/forcitis/one/organization/workforce/application/RegisterEmployeeOnLoginName.java | 登录名登录建档收来源人员 | 拆分 a" — `backend/architecture/source-type-exceptions.txt:8`
- `EP-06d71b13a2` — "src/main/java/com/forcitis/one/organization/workforce/application/LoginMissRegistration.java | 登录建档收登录查人返回的来源人员并经 SourceTerms 换类型 | 拆分 a" — `backend/architecture/source-type-exceptions.txt:6`
- `EP-0e03887f5b` — "src/main/java/com/forcitis/one/organization/workforce/domain/OrgReconciliation.java | 对账比对来源快照与组织快照 | 拆分 a" — `backend/architecture/source-type-exceptions.txt:11`
- `EP-278ae88b60` — "src/main/java/com/forcitis/one/organization/workforce/application/SyncOrgSnapshot.java | 定时同步读集成层的来源快照 | 拆分 a" — `backend/architecture/source-type-exceptions.txt:9`
- `EP-32e977808d` — "src/main/java/com/forcitis/one/organization/workforce/application/ApplySourceSnapshot.java | 同步用例收集成层交出的来源快照（已是自有 ID） | 拆分 a" — `backend/architecture/source-type-exceptions.txt:5`
- `EP-83fd076ec5` — "src/main/java/com/forcitis/one/organization/workforce/domain/SourceTerms.java | 来源枚举翻译与自有 ID 换类型的薄映射（RULING-P3B-D12） | 拆分 a" — `backend/architecture/source-type-exceptions.txt:13`
- `EP-a1079d05b8` — "src/main/java/com/forcitis/one/organization/workforce/domain/SnapshotReconciler.java | 来源快照与组织快照的差异计算 | 拆分 a" — `backend/architecture/source-type-exceptions.txt:12`
- `EP-ad41e690b9` — "src/main/java/com/forcitis/one/organization/workforce/application/RegisterEmployeeOnLogin.java | 企业微信登录建档收来源人员 | 拆分 a" — `backend/architecture/source-type-exceptions.txt:7`
- `EP-d2ab3b69f6` — "src/main/java/com/forcitis/one/organization/workforce/domain/EmployeeChecks.java | 来源人员的隔离与在职判定 | 拆分 a" — `backend/architecture/source-type-exceptions.txt:10`

### backend/architecture/status-write-exceptions.txt（1 条）

- `EP-a677c9c836` — "com/forcitis/one/platform/approval/infrastructure/replay/ExportedSnapshot.java  # read-only mapping of the exported boolean 'employed' onto the OrgDirectory read contract for the replay tool; it writes no state, and the enum is the contract's own type" — `backend/architecture/status-write-exceptions.txt:3`

### backend/architecture/test-isolation-exceptions.txt（3 条）

- `EP-2853ed07b1` — "acceptance/platform/approval/ApprovalOwnIdColumnsIT.java#platform_integration  # R-1b P4 acceptance: reads outbound_deliveries from the catalogue to prove the final column shapes; no module api shows column types; expires when platform_integration offers a schema-introspection api or the acceptance moves under acceptance/platform/integration/" — `backend/architecture/test-isolation-exceptions.txt:5`
- `EP-3f46953072` — "acceptance/platform/identity/SingleOperatorModeStartupIT.java#platform_audit  # P1-17 黑盒系统级启动验收：按次数核对启动审计条数，只读，限本测试类；调度 one-coordinator 批准的破例（SINGLE-OP P1 M6）" — `backend/architecture/test-isolation-exceptions.txt:7`
- `EP-baad340b16` — "acceptance/platform/identity/SingleOperatorModeEnvironment.java#platform_audit  # P1-14 黑盒系统级启动验收的辅助类：读行数证明被拒的启动没有任何副作用，只读，限本验收目录的启动验收；调度 one-coordinator 批准的破例（SINGLE-OP P1 M6）" — `backend/architecture/test-isolation-exceptions.txt:6`

### backend/architecture/typed-text-key-exceptions.txt（1 条）

- `EP-05d16e5ba1` — "organization_workforce.sync_diffs.subject_key  # the synchronisation names a difference by \"employee:<id>\", \"org-unit:<id>\", \"position:<id>\"; ends with split a, which gives the differences their own kind and id columns (NEXT_BATCH_PLAN 7.11)" — `backend/architecture/typed-text-key-exceptions.txt:5`

### backend/src/main/resources/db/migration/cost_timesheet/V20261014140000__cost_timesheet_baseline.sql（8 条）

- `EP-12dab9e718` — "uk_timesheets_timesheet_number" — `backend/src/main/resources/db/migration/cost_timesheet/V20261014140000__cost_timesheet_baseline.sql:25`
- `EP-336c44e1bf` — "uk_timesheets_employee_id_work_date" — `backend/src/main/resources/db/migration/cost_timesheet/V20261014140000__cost_timesheet_baseline.sql:26`
- `EP-509f37a77d` — "ck_timesheets_round_after_submit" — `backend/src/main/resources/db/migration/cost_timesheet/V20261014140000__cost_timesheet_baseline.sql:28`
- `EP-5dea1873cc` — "ck_timesheets_event_count" — `backend/src/main/resources/db/migration/cost_timesheet/V20261014140000__cost_timesheet_baseline.sql:24`
- `EP-82d67b04ed` — "uk_consumed_events_event_id_consumer_name" — `backend/src/main/resources/db/migration/cost_timesheet/V20261014140000__cost_timesheet_baseline.sql:58`
- `EP-93e7489658` — "ck_timesheets_round" — `backend/src/main/resources/db/migration/cost_timesheet/V20261014140000__cost_timesheet_baseline.sql:23`
- `EP-cf5958af7b` — "ck_timesheets_hours" — `backend/src/main/resources/db/migration/cost_timesheet/V20261014140000__cost_timesheet_baseline.sql:22`
- `EP-d997b0ca78` — "ck_timesheets_round_submitted" — `backend/src/main/resources/db/migration/cost_timesheet/V20261014140000__cost_timesheet_baseline.sql:27`

### backend/src/main/resources/db/migration/organization_attendance/V20261014141000__organization_attendance_baseline.sql（4 条）

- `EP-3c6a124cb6` — "ck_work_calendar_days_make_up_weekend" — `backend/src/main/resources/db/migration/organization_attendance/V20261014141000__organization_attendance_baseline.sql:36`
- `EP-cd21864d87` — "uk_work_calendar_years_legal_entity_id_year_number" — `backend/src/main/resources/db/migration/organization_attendance/V20261014141000__organization_attendance_baseline.sql:13`
- `EP-fded69aafd` — "ck_work_calendar_years_year_range" — `backend/src/main/resources/db/migration/organization_attendance/V20261014141000__organization_attendance_baseline.sql:14`
- `EP-fff01ce96e` — "uk_work_calendar_days_work_calendar_year_id_calendar_date" — `backend/src/main/resources/db/migration/organization_attendance/V20261014141000__organization_attendance_baseline.sql:35`

### backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql（56 条）

- `EP-072c6253c7` — "ex_legal_entity_versions_overlap" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:408`
- `EP-087fc3dc94` — "ck_legal_entity_attributions_subject" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:258`
- `EP-0cf1e9f852` — "uk_legal_entity_attributions_employee_id" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:257`
- `EP-1de753b5b8` — "ck_legal_entity_versions_name" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:406`
- `EP-24f799992c` — "ck_employees_employee_code" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:84`
- `EP-3002e0ab11` — "uk_legal_entity_versions_revision" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:403`
- `EP-330cb35e40` — "ck_org_units_short_name" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:47`
- `EP-3434eca75c` — "ck_legal_entity_attribution_corrections_changes" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:443`
- `EP-4a85875cba` — "tg_employee_legal_entities_append_only_truncate" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:571`
- `EP-4b53f4c0ef` — "ex_org_unit_legal_entities_overlap" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:283`
- `EP-5182b4b30a` — "ck_positions_name" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:14`
- `EP-532698bf43` — "ck_legal_entity_versions_code_format" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:405`
- `EP-53830d8789` — "uk_legal_entity_attribution_corrections_correction_number" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:436`
- `EP-573cd60bf0` — "ck_org_unit_versions_status" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:327`
- `EP-595c42ddd9` — "legal_entities_code_format" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:228`
- `EP-615f819aae` — "ck_org_unit_versions_range" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:340`
- `EP-662974027c` — "legal_entity_attribution_corrections_reject_truncate" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:533`
- `EP-7243826598` — "legal_entity_attribution_corrections_reject_update_delete" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:529`
- `EP-7356e06915` — "ck_org_unit_legal_entities_range" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:282`
- `EP-735c399b9c` — "uk_legal_entity_attributions_org_unit_id" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:256`
- `EP-7d34a3bdea` — "ck_legal_entity_versions_status" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:399`
- `EP-805d93be03` — "tg_legal_entity_versions_append_only" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:561`
- `EP-82c773fef0` — "tg_org_unit_legal_entities_append_only_truncate" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:567`
- `EP-83507541d1` — "ex_org_unit_versions_overlap" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:341`
- `EP-83b0d48571` — "ck_employees_display_name" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:85`
- `EP-856616b7c9` — "tg_org_unit_legal_entities_append_only" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:565`
- `EP-8725d97342` — "ck_org_units_revoked_at" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:44`
- `EP-90f4bdfb04` — "tg_org_unit_versions_append_only" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:553`
- `EP-95bb3eda3f` — "tg_legal_entity_versions_append_only_truncate" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:563`
- `EP-97f8272d6f` — "uk_sync_run_held_separations_sync_run_id_employee_id" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:470`
- `EP-98a5277701` — "ck_legal_entity_attribution_corrections_subject" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:444`
- `EP-99a52e1e70` — "ex_employee_legal_entities_overlap" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:308`
- `EP-9eba46999b` — "ck_legal_entity_attribution_corrections_reason" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:438`
- `EP-a4cea79194` — "ck_org_unit_versions_name" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:338`
- `EP-ac90ced07f` — "tg_assignment_versions_append_only" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:557`
- `EP-b13497133a` — "tg_org_unit_versions_append_only_truncate" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:555`
- `EP-b16c39cabf` — "tg_assignment_versions_append_only_truncate" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:559`
- `EP-b648097887` — "ck_legal_entities_name" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:229`
- `EP-b813895ded` — "ck_org_units_name" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:45`
- `EP-b8b8993b25` — "ck_positions_code" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:15`
- `EP-bd706b3ae7` — "ck_org_unit_versions_code" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:339`
- `EP-c2b42d23f3` — "ex_assignment_versions_overlap" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:376`
- `EP-c945258cca` — "ck_org_units_code" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:46`
- `EP-d434089058` — "employees_wecom_user_id_employed_key" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:88`
- `EP-d741d2db20` — "uk_employee_legal_entities_effective_from" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:303`
- `EP-ddef61c0b4` — "ck_employee_legal_entities_range" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:307`
- `EP-dee278d624` — "ck_employees_email_address" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:86`
- `EP-e08bb24a92` — "ck_legal_entity_versions_range" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:407`
- `EP-e383c6721b` — "tg_employee_legal_entities_append_only" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:569`
- `EP-e69d2799ae` — "uk_org_unit_versions_revision" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:336`
- `EP-ee945e050a` — "ck_legal_entity_attribution_corrections_range" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:437`
- `EP-eeb31f1ce5` — "uk_sync_diffs_sync_run_id_diff_number" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:203`
- `EP-f8405058cd` — "ck_assignment_versions_range" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:375`
- `EP-fb0e17105b` — "ck_org_unit_versions_region_kind" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:330`
- `EP-fb8fb1ca01` — "uk_org_unit_legal_entities_effective_from" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:278`
- `EP-ffe872e99d` — "uk_assignment_versions_revision" — `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql:372`

### backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql（34 条）

- `EP-0224421ecb` — "ck_approval_requests_summary_text" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:27`
- `EP-0958db27f6` — "ck_approval_tasks_deadline" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:109`
- `EP-0b5772a95d` — "ck_approval_requests_document_type" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:17`
- `EP-1d17b8cfdc` — "ck_approval_tasks_approved_hash" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:121`
- `EP-267d36a68c` — "start_rounds_reject_update_delete" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:77`
- `EP-2976ccca19` — "ck_approval_task_records_assignee" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:187`
- `EP-34a6b2699b` — "uk_start_rounds_trigger_event_id" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:57`
- `EP-4312f3d43e` — "ck_approval_tasks_pending_unassigned" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:118`
- `EP-48d3f705ab` — "ck_approval_requests_current_round" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:23`
- `EP-49e31c9335` — "ck_approval_tasks_result_reason" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:126`
- `EP-4b1e5cb7d1` — "uk_approval_requests_current_instance_id" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:29`
- `EP-4d1a171618` — "ck_approval_tasks_escalated" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:123`
- `EP-5ee94946cb` — "approval_task_records_reject_truncate" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:225`
- `EP-5f73815766` — "ck_approval_requests_result_reason" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:24`
- `EP-5f7c5c8e15` — "ck_approval_tasks_completed" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:113`
- `EP-5f8b2d0850` — "uk_approval_tasks_task_id_approval_request_id" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:117`
- `EP-6625811a3d` — "uk_process_archive_skips_process_instance_id" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:239`
- `EP-6a4d4ea4ac` — "ck_approval_tasks_not_completed" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:124`
- `EP-6d15046c54` — "tg_process_archive_skips_append_only_truncate" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:259`
- `EP-6ff9245336` — "ck_approval_task_records_record_reason" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:184`
- `EP-7d0b3348bb` — "ck_approval_requests_event_count" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:22`
- `EP-7f05256ed7` — "uk_approval_task_records_record_number" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:183`
- `EP-8cba93923d` — "ck_approval_tasks_claimed" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:111`
- `EP-9325f8093c` — "ck_approval_tasks_outcome_effect" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:119`
- `EP-97018624cf` — "ck_process_archive_skips_failure_message" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:240`
- `EP-a26e7218cf` — "ck_approval_task_records_on_behalf_of" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:188`
- `EP-b4bb84e3db` — "uq_approval_requests_document" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:21`
- `EP-b58a5beefb` — "uk_start_rounds_instance_id" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:58`
- `EP-b7f61f1724` — "approval_task_records_reject_update_delete" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:221`
- `EP-c80ab5da1b` — "start_rounds_reject_truncate" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:79`
- `EP-cdee326f34` — "ck_approval_tasks_overdue" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:122`
- `EP-d523439f08` — "uq_start_rounds_round" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:56`
- `EP-d971efa8c6` — "ck_approval_tasks_automatic" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:120`
- `EP-efa99beae9` — "tg_process_archive_skips_append_only" — `backend/src/main/resources/db/migration/platform_approval/V20261014132000__platform_approval_baseline.sql:257`

### backend/src/main/resources/db/migration/platform_audit/V20261014134000__platform_audit_baseline.sql（19 条）

- `EP-13e3525c42` — "audit_entries_reject_truncate" — `backend/src/main/resources/db/migration/platform_audit/V20261014134000__platform_audit_baseline.sql:72`
- `EP-1a68b25494` — "ck_audit_entries_action_length" — `backend/src/main/resources/db/migration/platform_audit/V20261014134000__platform_audit_baseline.sql:29`
- `EP-3465b67d27` — "ck_audit_entries_source_address_length" — `backend/src/main/resources/db/migration/platform_audit/V20261014134000__platform_audit_baseline.sql:34`
- `EP-34cc978586` — "ck_audit_entries_before_text_length" — `backend/src/main/resources/db/migration/platform_audit/V20261014134000__platform_audit_baseline.sql:32`
- `EP-3cd00f9b66` — "ck_audit_archive_batches_sha256_hash" — `backend/src/main/resources/db/migration/platform_audit/V20261014134000__platform_audit_baseline.sql:94`
- `EP-43ee36476b` — "ck_audit_entries_subject_kind" — `backend/src/main/resources/db/migration/platform_audit/V20261014134000__platform_audit_baseline.sql:12`
- `EP-5a8bf72196` — "ck_audit_entries_change_reason_length" — `backend/src/main/resources/db/migration/platform_audit/V20261014134000__platform_audit_baseline.sql:31`
- `EP-5dd7ccf747` — "uk_audit_archive_batches_last_entry_id" — `backend/src/main/resources/db/migration/platform_audit/V20261014134000__platform_audit_baseline.sql:92`
- `EP-6256137fa9` — "ck_audit_entries_login_method_outcome" — `backend/src/main/resources/db/migration/platform_audit/V20261014134000__platform_audit_baseline.sql:28`
- `EP-6c1f393662` — "uk_audit_archive_batches_object_key" — `backend/src/main/resources/db/migration/platform_audit/V20261014134000__platform_audit_baseline.sql:93`
- `EP-9147db25b6` — "uk_audit_archive_batches_first_entry_id" — `backend/src/main/resources/db/migration/platform_audit/V20261014134000__platform_audit_baseline.sql:91`
- `EP-9362f4d5d0` — "tg_audit_archive_batches_append_only_truncate" — `backend/src/main/resources/db/migration/platform_audit/V20261014134000__platform_audit_baseline.sql:118`
- `EP-94953c7480` — "uk_audit_entries_event_id_created_at" — `backend/src/main/resources/db/migration/platform_audit/V20261014134000__platform_audit_baseline.sql:26`
- `EP-b6855f7362` — "ck_audit_archive_batches_range" — `backend/src/main/resources/db/migration/platform_audit/V20261014134000__platform_audit_baseline.sql:95`
- `EP-ca95ab5fdd` — "tg_audit_archive_batches_append_only" — `backend/src/main/resources/db/migration/platform_audit/V20261014134000__platform_audit_baseline.sql:116`
- `EP-cd09565a30` — "audit_entries_reject_update_delete" — `backend/src/main/resources/db/migration/platform_audit/V20261014134000__platform_audit_baseline.sql:68`
- `EP-f23161781e` — "ck_audit_entries_after_text_length" — `backend/src/main/resources/db/migration/platform_audit/V20261014134000__platform_audit_baseline.sql:33`
- `EP-f3db0acc2b` — "ck_audit_entries_subject_id" — `backend/src/main/resources/db/migration/platform_audit/V20261014134000__platform_audit_baseline.sql:27`
- `EP-f7d04a2f72` — "ck_audit_entries_detail_length" — `backend/src/main/resources/db/migration/platform_audit/V20261014134000__platform_audit_baseline.sql:30`

### backend/src/main/resources/db/migration/platform_idempotency/V20261014221000__platform_idempotency_baseline.sql（6 条）

- `EP-9f28a6e72a` — "ck_idempotent_responses_request_hash" — `backend/src/main/resources/db/migration/platform_idempotency/V20261014221000__platform_idempotency_baseline.sql:20`
- `EP-a02aff357b` — "uk_idempotent_responses_user_id_idempotency_key" — `backend/src/main/resources/db/migration/platform_idempotency/V20261014221000__platform_idempotency_baseline.sql:18`
- `EP-af0484e188` — "ck_idempotent_responses_in_progress" — `backend/src/main/resources/db/migration/platform_idempotency/V20261014221000__platform_idempotency_baseline.sql:23`
- `EP-c496f6fd9e` — "ck_idempotent_responses_response_text" — `backend/src/main/resources/db/migration/platform_idempotency/V20261014221000__platform_idempotency_baseline.sql:22`
- `EP-de83556b8f` — "ck_idempotent_responses_idempotency_key" — `backend/src/main/resources/db/migration/platform_idempotency/V20261014221000__platform_idempotency_baseline.sql:19`
- `EP-f397fd5970` — "ck_idempotent_responses_response_number" — `backend/src/main/resources/db/migration/platform_idempotency/V20261014221000__platform_idempotency_baseline.sql:21`

### backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql（50 条）

- `EP-058881fbf9` — "tg_role_versions_append_only_truncate" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:409`
- `EP-1103378525` — "ex_role_versions_role_name_period" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:401`
- `EP-1233cee649` — "ck_role_versions_access_scope_kind" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:386`
- `EP-1e5037e15f` — "ck_roles_code" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:50`
- `EP-20f5330822` — "ck_emergency_admins_failed_attempts" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:200`
- `EP-259cea33c0` — "ck_role_versions_period" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:398`
- `EP-28b08e8ca1` — "uq_sessions_session" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:228`
- `EP-29715743a8` — "uq_roles_code" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:52`
- `EP-37e77f5dc1` — "tg_account_role_grants_append_only_truncate" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:502`
- `EP-3c8caf4e16` — "ex_account_role_grants_user_id_role_id_period" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:475`
- `EP-4025acfc61` — "tg_account_role_grants_open_role_version" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:497`
- `EP-40fbdfe50e` — "uk_user_accounts_employee_id" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:22`
- `EP-4698bd3d24` — "ck_role_versions_role_name" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:384`
- `EP-49476a7adc` — "uk_ticket_issuers_issuer_code" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:173`
- `EP-5fc55f410f` — "ck_role_versions_seed" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:390`
- `EP-614be8c06a` — "ck_role_versions_closure" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:394`
- `EP-68b3236b1f` — "ck_role_versions_closure_reason" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:397`
- `EP-6bb7e3763c` — "ck_navigation_items_parent" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:112`
- `EP-6f5bec807c` — "ck_role_versions_change_channel_kind" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:388`
- `EP-71803b1d8e` — "ck_role_versions_finite" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:399`
- `EP-82a034c0a7` — "ex_role_versions_role_id_period" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:400`
- `EP-8a87e1023a` — "ck_account_role_grants_grant_channel_kind" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:460`
- `EP-8b8e96cf8a` — "ck_roles_reserved_code" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:49`
- `EP-8e45ee0fa5` — "uk_sso_ticket_uses_ticket_source_ticket_hash" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:153`
- `EP-99b6112d27` — "ck_role_versions_manual" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:392`
- `EP-9aab71c2a7` — "ck_account_role_grants_period" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:473`
- `EP-9bd3a433c9` — "ck_account_role_grants_finite" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:474`
- `EP-9d5d004720` — "tg_account_role_grants_append_only" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:499`
- `EP-9f6ce666e0` — "uk_role_permissions_role_id_permission_code" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:74`
- `EP-a1d39d066f` — "uk_browser_identifiers_identifier_hash_user_id" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:270`
- `EP-a241e1247a` — "ck_role_versions_version_number" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:383`
- `EP-a897dbc489` — "uk_account_roles_user_id_role_id" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:91`
- `EP-ae621b23ce` — "ck_user_accounts_last_login_address" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:24`
- `EP-b3046cc7f6` — "ck_role_versions_change_reason" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:389`
- `EP-b3e8a30b43` — "ck_emergency_admins_account_name" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:199`
- `EP-b47bc64d72` — "ck_emergency_admins_last_totp_step" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:201`
- `EP-b94c9ae202` — "uq_roles_name" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:53`
- `EP-c8ded169a0` — "ck_ticket_issuers_no_administrator" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:174`
- `EP-c99c99df00` — "ck_role_versions_declaration_revision_number" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:393`
- `EP-d6a9c18a68` — "uk_role_versions_role_id_version_number" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:382`
- `EP-d8b46518c3` — "uk_navigation_items_parent_item_id_name" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:113`
- `EP-dcb3401086` — "uk_login_states_state_hash" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:134`
- `EP-dcbe16b557` — "ck_account_role_grants_grant" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:463`
- `EP-defde582c3` — "ck_role_versions_role_status" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:385`
- `EP-e0ed0412db` — "ck_user_accounts_last_login" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:23`
- `EP-e7cfb91a59` — "ck_account_role_grants_revocation_cause_kind" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:461`
- `EP-f6c2c26f4c` — "ck_authorization_requests_two_people" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:306`
- `EP-f87afbe970` — "ck_role_versions_permission_codes" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:387`
- `EP-fa6c50a96c` — "tg_role_versions_append_only" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:407`
- `EP-fde18d13e4` — "ck_account_role_grants_revocation" — `backend/src/main/resources/db/migration/platform_identity/V20261014130000__platform_identity_baseline.sql:466`

### backend/src/main/resources/db/migration/platform_integration/V20261014133000__platform_integration_baseline.sql（21 条）

- `EP-06f2ca0a27` — "uk_outbound_deliveries_card" — `backend/src/main/resources/db/migration/platform_integration/V20261014133000__platform_integration_baseline.sql:64`
- `EP-0d2325be98` — "uk_source_references_own_id" — `backend/src/main/resources/db/migration/platform_integration/V20261014133000__platform_integration_baseline.sql:168`
- `EP-0e82420eaa` — "uk_outbound_deliveries_target_delivery_id" — `backend/src/main/resources/db/migration/platform_integration/V20261014133000__platform_integration_baseline.sql:65`
- `EP-25cfbf1787` — "ck_source_references_accounting_code_is_manual_registration" — `backend/src/main/resources/db/migration/platform_integration/V20261014133000__platform_integration_baseline.sql:161`
- `EP-58519f1d88` — "tg_source_references_never_deleted_truncate" — `backend/src/main/resources/db/migration/platform_integration/V20261014133000__platform_integration_baseline.sql:222`
- `EP-604772381d` — "ck_source_references_period" — `backend/src/main/resources/db/migration/platform_integration/V20261014133000__platform_integration_baseline.sql:153`
- `EP-6c6fe2a41e` — "ck_source_references_confirmation_status" — `backend/src/main/resources/db/migration/platform_integration/V20261014133000__platform_integration_baseline.sql:140`
- `EP-7851d2dcae` — "ck_source_references_source_object_kind" — `backend/src/main/resources/db/migration/platform_integration/V20261014133000__platform_integration_baseline.sql:137`
- `EP-7f3249cdb1` — "ck_outbound_deliveries_purpose_kind" — `backend/src/main/resources/db/migration/platform_integration/V20261014133000__platform_integration_baseline.sql:39`
- `EP-8c4c7257c3` — "tg_source_references_guard" — `backend/src/main/resources/db/migration/platform_integration/V20261014133000__platform_integration_baseline.sql:210`
- `EP-9c2bb2619c` — "ex_source_references_source_id" — `backend/src/main/resources/db/migration/platform_integration/V20261014133000__platform_integration_baseline.sql:163`
- `EP-9d2eb4176b` — "ck_source_references_decider_is_not_author" — `backend/src/main/resources/db/migration/platform_integration/V20261014133000__platform_integration_baseline.sql:159`
- `EP-ab473a827a` — "tg_source_references_never_deleted" — `backend/src/main/resources/db/migration/platform_integration/V20261014133000__platform_integration_baseline.sql:220`
- `EP-b0e4a37d9c` — "ck_source_references_manual_row_is_decided_by_someone" — `backend/src/main/resources/db/migration/platform_integration/V20261014133000__platform_integration_baseline.sql:160`
- `EP-b248ce1743` — "uk_source_references_source_id" — `backend/src/main/resources/db/migration/platform_integration/V20261014133000__platform_integration_baseline.sql:166`
- `EP-bdf9907a6e` — "ex_source_references_own_id" — `backend/src/main/resources/db/migration/platform_integration/V20261014133000__platform_integration_baseline.sql:164`
- `EP-bfa62a8935` — "ck_source_references_source_system" — `backend/src/main/resources/db/migration/platform_integration/V20261014133000__platform_integration_baseline.sql:136`
- `EP-d416256b5d` — "ck_source_references_decision" — `backend/src/main/resources/db/migration/platform_integration/V20261014133000__platform_integration_baseline.sql:157`
- `EP-d730e26cfa` — "outbound_deliveries_handled_note_target" — `backend/src/main/resources/db/migration/platform_integration/V20261014133000__platform_integration_baseline.sql:61`
- `EP-dcd4d877e5` — "ck_source_references_accounting_code_positive" — `backend/src/main/resources/db/migration/platform_integration/V20261014133000__platform_integration_baseline.sql:151`
- `EP-f9c22af5c1` — "ck_outbound_deliveries_subject_kind" — `backend/src/main/resources/db/migration/platform_integration/V20261014133000__platform_integration_baseline.sql:37`

### backend/src/main/resources/db/migration/platform_numbering/V20261014100100__platform_numbering_baseline.sql（26 条）

- `EP-0e10b8a7f9` — "numbering_rule_versions_guard" — `backend/src/main/resources/db/migration/platform_numbering/V20261014100100__platform_numbering_baseline.sql:210`
- `EP-10571340f8` — "ck_numbering_rule_versions_confirmation_time" — `backend/src/main/resources/db/migration/platform_numbering/V20261014100100__platform_numbering_baseline.sql:162`
- `EP-1227991336` — "uk_numbering_counters_document_type_legal_entity_period" — `backend/src/main/resources/db/migration/platform_numbering/V20261014100100__platform_numbering_baseline.sql:85`
- `EP-1272bc2969` — "ck_numbering_counters_period_key" — `backend/src/main/resources/db/migration/platform_numbering/V20261014100100__platform_numbering_baseline.sql:80`
- `EP-1a95ffc012` — "uk_document_types_code" — `backend/src/main/resources/db/migration/platform_numbering/V20261014100100__platform_numbering_baseline.sql:16`
- `EP-1c39a778e1` — "uk_numbering_rules_document_type_id" — `backend/src/main/resources/db/migration/platform_numbering/V20261014100100__platform_numbering_baseline.sql:64`
- `EP-2e0ef764b3` — "tg_document_types_append_only" — `backend/src/main/resources/db/migration/platform_numbering/V20261014100100__platform_numbering_baseline.sql:52`
- `EP-37e7ad0b16` — "uk_document_types_number_code" — `backend/src/main/resources/db/migration/platform_numbering/V20261014100100__platform_numbering_baseline.sql:17`
- `EP-503bfbd369` — "uk_numbering_rule_versions_numbering_rule_id" — `backend/src/main/resources/db/migration/platform_numbering/V20261014100100__platform_numbering_baseline.sql:167`
- `EP-52be1aca83` — "uk_legacy_numbers_source_system_document_type_id_legacy_number" — `backend/src/main/resources/db/migration/platform_numbering/V20261014100100__platform_numbering_baseline.sql:111`
- `EP-5e6bfce800` — "ck_document_types_retired_after_effective" — `backend/src/main/resources/db/migration/platform_numbering/V20261014100100__platform_numbering_baseline.sql:21`
- `EP-6fccceb24e` — "tg_legacy_numbers_append_only" — `backend/src/main/resources/db/migration/platform_numbering/V20261014100100__platform_numbering_baseline.sql:132`
- `EP-7840cf409d` — "ck_numbering_counters_last_value" — `backend/src/main/resources/db/migration/platform_numbering/V20261014100100__platform_numbering_baseline.sql:81`
- `EP-7998f196de` — "tg_legacy_numbers_append_only_truncate" — `backend/src/main/resources/db/migration/platform_numbering/V20261014100100__platform_numbering_baseline.sql:134`
- `EP-955191400b` — "tg_document_types_append_only_truncate" — `backend/src/main/resources/db/migration/platform_numbering/V20261014100100__platform_numbering_baseline.sql:54`
- `EP-99b6cc7e9f` — "ck_numbering_counters_scope_kind_matches_legal_entity" — `backend/src/main/resources/db/migration/platform_numbering/V20261014100100__platform_numbering_baseline.sql:87`
- `EP-9bb5bc9047` — "ck_document_types_code" — `backend/src/main/resources/db/migration/platform_numbering/V20261014100100__platform_numbering_baseline.sql:18`
- `EP-9e7cdce90a` — "ck_numbering_rule_versions_confirmed" — `backend/src/main/resources/db/migration/platform_numbering/V20261014100100__platform_numbering_baseline.sql:164`
- `EP-a40e0739d3` — "ck_document_types_owner_bc_code" — `backend/src/main/resources/db/migration/platform_numbering/V20261014100100__platform_numbering_baseline.sql:20`
- `EP-b70617f2ee` — "ck_numbering_rule_versions_two_different_confirmers" — `backend/src/main/resources/db/migration/platform_numbering/V20261014100100__platform_numbering_baseline.sql:160`
- `EP-c20116e53c` — "tg_numbering_rule_versions_append_only_truncate" — `backend/src/main/resources/db/migration/platform_numbering/V20261014100100__platform_numbering_baseline.sql:213`
- `EP-cb5f780ce6` — "ck_numbering_counters_counter_scope_kind" — `backend/src/main/resources/db/migration/platform_numbering/V20261014100100__platform_numbering_baseline.sql:78`
- `EP-dbfeb19832` — "ck_legacy_numbers_source_system" — `backend/src/main/resources/db/migration/platform_numbering/V20261014100100__platform_numbering_baseline.sql:102`
- `EP-e0f6f15b9b` — "ck_numbering_rule_versions_confirmer_is_not_author" — `backend/src/main/resources/db/migration/platform_numbering/V20261014100100__platform_numbering_baseline.sql:158`
- `EP-efc4e70e57` — "uk_numbering_rule_versions_numbering_rule_id_version_number" — `backend/src/main/resources/db/migration/platform_numbering/V20261014100100__platform_numbering_baseline.sql:156`
- `EP-f1189d449c` — "ck_document_types_number_code" — `backend/src/main/resources/db/migration/platform_numbering/V20261014100100__platform_numbering_baseline.sql:19`

### backend/src/test/java/com/forcitis/one/ApplicationIntegrationTest.java（13 条）

- `EP-258b7ee98c` — "numbersFollowTheRuleResetYearlyAndAreSeparatePerLegalEntity" — `backend/src/test/java/com/forcitis/one/ApplicationIntegrationTest.java:214`
- `EP-33b659c337` — "issuingOutsideTheCallersTransactionIsRefused" — `backend/src/test/java/com/forcitis/one/ApplicationIntegrationTest.java:252`
- `EP-368543f952` — "everyTableAndColumnIsDocumentedAndTheDictionaryIsGenerated" — `backend/src/test/java/com/forcitis/one/ApplicationIntegrationTest.java:453`
- `EP-42e74b36f7` — "concurrentIssuingNeverDuplicatesOrSkips" — `backend/src/test/java/com/forcitis/one/ApplicationIntegrationTest.java:239`
- `EP-5ea3c3bb7f` — "anonymousCallersAreRefusedExceptOnTheReviewedPublicEndpoints" — `backend/src/test/java/com/forcitis/one/ApplicationIntegrationTest.java:125`
- `EP-6479808c75` — "serializedDomainEventKeysAreCamelCaseGlossaryWords" — `backend/src/test/java/com/forcitis/one/ApplicationIntegrationTest.java:314`
- `EP-97c3f559ce` — "adminCanRedefineRulesButInvalidPatternsAreRejected" — `backend/src/test/java/com/forcitis/one/ApplicationIntegrationTest.java:262`
- `EP-995d982461` — "rolledBackNumbersAreReissuedSoThereAreNoGaps" — `backend/src/test/java/com/forcitis/one/ApplicationIntegrationTest.java:225`
- `EP-9985409cb3` — "flowableLivesInItsOwnSchema" — `backend/src/test/java/com/forcitis/one/ApplicationIntegrationTest.java:163`
- `EP-9ee91b973e` — "writesWithoutACsrfTokenAreForbidden" — `backend/src/test/java/com/forcitis/one/ApplicationIntegrationTest.java:155`
- `EP-b00cab3330` — "bareNumericVersionSegmentIsNotARoute" — `backend/src/test/java/com/forcitis/one/ApplicationIntegrationTest.java:176`
- `EP-c0c9aec28e` — "unsupportedApiVersionIsNotARoute" — `backend/src/test/java/com/forcitis/one/ApplicationIntegrationTest.java:169`
- `EP-faff5187b6` — "openApiSchemaNamesArePascalCaseAndJsonPropertiesAndParametersCamelCaseGlossaryWords" — `backend/src/test/java/com/forcitis/one/ApplicationIntegrationTest.java:283`

### backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java（35 条）

- `EP-04ff8b0383` — "applicationDoesNotTouchWebOrInfrastructure" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:123`
- `EP-0fc2cad737` — "requestsNeverCarryTheCallerIdentity" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:386`
- `EP-10d28a710d` — "useCasesExposeExactlyOnePublicMethod" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:346`
- `EP-12cae4d3e4` — "moneyIsNeverABareDecimal" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:224`
- `EP-144bddd86f` — "eventsAreFactsNotRequests" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:415`
- `EP-1692fb03b3` — "repositoriesFollowPortAdapterNaming" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:169`
- `EP-16f4683162` — "namedInterfacesAreOnlyApiAndEvents" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:433`
- `EP-1d17cede06` — "webOnlyCallsApplication" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:131`
- `EP-2036591f10` — "webAndInfrastructureAreNotPublic" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:105`
- `EP-277f8c5400` — "domainIsFrameworkFree" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:114`
- `EP-28f58ce23b` — "eventsAreRecords" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:58`
- `EP-2a933f00de` — "apiSurfaceStaysNarrow" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:67`
- `EP-2f9f57a1db` — "apiSurfaceNonExceptionModuleFailsAboveGlobalLimit" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:85`
- `EP-311916907a` — "controllersLiveInWebAndEndWithController" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:162`
- `EP-4bc77994f9` — "timeComesFromAnInjectedClock" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:332`
- `EP-5a1cda8df1` — "noBigDecimalEqualsInBusinessCode" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:323`
- `EP-5e59a67e2f` — "identifiersAreAscii" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:191`
- `EP-6e54fc2dd4` — "apiSignaturesDoNotLeakModuleInternals" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:50`
- `EP-701204e3e0` — "onlyIdentityConfiguresSecurity" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:377`
- `EP-73c0d7de14` — "valueObjectsAreRecords" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:275`
- `EP-77253d34ad` — "noDebugOrDataFixCodeInMain" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:337`
- `EP-852e60c8b1` — "apiSurfaceExceptionModuleAllowsExactlyItsLimit" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:76`
- `EP-8a4bd83d18` — "controllersAreVersioned" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:257`
- `EP-8a69e826b3` — "valueObjectsHoldNoMutableState" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:283`
- `EP-8e767d5876` — "hoursAndQuantitiesUseValueObjects" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:313`
- `EP-960409b2a6` — "statusesAreEnums" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:210`
- `EP-9cc7a0ac3a` — "flowableOnlyInsideApprovalInfrastructure" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:249`
- `EP-9f00675d5e` — "generalCodingRules" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:235`
- `EP-a17952e247` — "everyHandlerIsAuthorizedOrExplicitlyPublic" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:362`
- `EP-a99f5955aa` — "jdbcOnlyInInfrastructure" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:139`
- `EP-ae36876e64` — "domainIdsAreTyped" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:298`
- `EP-bc294667f2` — "noVagueOrLegacyNames" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:180`
- `EP-da95c4e1b4` — "transactionsOnlyInApplication" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:147`
- `EP-e94924c2a4` — "apiPackagesContainOnlyInterfacesAndRecords" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:42`
- `EP-ec74345f2b` — "contractsAreStronglyTyped" — `backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java:396`

### backend/src/test/java/com/forcitis/one/AuditDetailGuardTest.java（5 条）

- `EP-11cb74e757` — "breachesAreCaught" — `backend/src/test/java/com/forcitis/one/AuditDetailGuardTest.java:39`
- `EP-9c24fd883b` — "noDirectIdentifierInAuditTexts" — `backend/src/test/java/com/forcitis/one/AuditDetailGuardTest.java:24`
- `EP-9ff7b0bb2c` — "implementationsAreRecognised" — `backend/src/test/java/com/forcitis/one/AuditDetailGuardTest.java:110`
- `EP-ea8938f1a7` — "releaseSyncRunDetailIsScannedToTheEnd" — `backend/src/test/java/com/forcitis/one/AuditDetailGuardTest.java:85`
- `EP-ebbc02c719` — "auditDetailImplementationsAreRegistered" — `backend/src/test/java/com/forcitis/one/AuditDetailGuardTest.java:95`

### backend/src/test/java/com/forcitis/one/AuditWritePointsGuardTest.java（4 条）

- `EP-2d5e8bd7db` — "snapshotIsEnforcedBothWays" — `backend/src/test/java/com/forcitis/one/AuditWritePointsGuardTest.java:123`
- `EP-2f0764bb5f` — "registerLinesAreChecked" — `backend/src/test/java/com/forcitis/one/AuditWritePointsGuardTest.java:151`
- `EP-464644937b` — "writersMatchTheRegister" — `backend/src/test/java/com/forcitis/one/AuditWritePointsGuardTest.java:104`
- `EP-64f52a789a` — "beforeAndAfterTextsStayNull" — `backend/src/test/java/com/forcitis/one/AuditWritePointsGuardTest.java:136`

### backend/src/test/java/com/forcitis/one/AuthenticationTokenGuardTest.java（2 条）

- `EP-101a3f3abc` — "onlyIdentityMakesTheSessionPrincipal" — `backend/src/test/java/com/forcitis/one/AuthenticationTokenGuardTest.java:45`
- `EP-383feaf077` — "aTokenMadeElsewhereIsReported" — `backend/src/test/java/com/forcitis/one/AuthenticationTokenGuardTest.java:58`

### backend/src/test/java/com/forcitis/one/CurrentUserGuardTest.java（2 条）

- `EP-6c73b8f076` — "madeUpAnswersAreCaught" — `backend/src/test/java/com/forcitis/one/CurrentUserGuardTest.java:69`
- `EP-a6d1b8d4b6` — "noMadeUpLegalEntity" — `backend/src/test/java/com/forcitis/one/CurrentUserGuardTest.java:51`

### backend/src/test/java/com/forcitis/one/DataArchitectureTest.java（10 条）

- `EP-109fe306c4` — "injectedEmployeeCodeKeysAreCaught" — `backend/src/test/java/com/forcitis/one/DataArchitectureTest.java:848`
- `EP-13fc684940` — "registryCoversEveryTable" — `backend/src/test/java/com/forcitis/one/DataArchitectureTest.java:543`
- `EP-31f9442eb3` — "injectedMomentVersionTablesAreCaught" — `backend/src/test/java/com/forcitis/one/DataArchitectureTest.java:692`
- `EP-48dbd17840` — "injectedViolationsAreCaught" — `backend/src/test/java/com/forcitis/one/DataArchitectureTest.java:654`
- `EP-6d317c615d` — "theEmployeeCodeIsNoKey" — `backend/src/test/java/com/forcitis/one/DataArchitectureTest.java:835`
- `EP-6e01cb1472` — "accessMatchesTheRegistry" — `backend/src/test/java/com/forcitis/one/DataArchitectureTest.java:580`
- `EP-7ea55db408` — "scorecardRulesHold" — `backend/src/test/java/com/forcitis/one/DataArchitectureTest.java:573`
- `EP-957167cfef` — "databaseScriptRevokesWritesOnVersionTables" — `backend/src/test/java/com/forcitis/one/DataArchitectureTest.java:740`
- `EP-ae947ffe51` — "auditArchiveColumnsAreDeliberate" — `backend/src/test/java/com/forcitis/one/DataArchitectureTest.java:762`
- `EP-cbd4b7e26f` — "writingSqlNamesItsSchema" — `backend/src/test/java/com/forcitis/one/DataArchitectureTest.java:723`

### backend/src/test/java/com/forcitis/one/DdlNamingTest.java（27 条）

- `EP-0191ad8e5d` — "timestampWithoutTimeZoneIsRejected" — `backend/src/test/java/com/forcitis/one/DdlNamingTest.java:931`
- `EP-043e52e7c9` — "allIdentifierKindsIncludeTheirPrefixesInTheByteLimit" — `backend/src/test/java/com/forcitis/one/DdlNamingTest.java:41`
- `EP-0652a47337` — "idsClassWordAndNamedEnumChecks" — `backend/src/test/java/com/forcitis/one/DdlNamingTest.java:444`
- `EP-09adebd340` — "scannerErrorsIdentifyTheFileAndOpeningLine" — `backend/src/test/java/com/forcitis/one/DdlNamingTest.java:174`
- `EP-1fa8ca0497` — "commonColumnsNeedNoComment" — `backend/src/test/java/com/forcitis/one/DdlNamingTest.java:920`
- `EP-335d4af245` — "otherColumnsStillNeedAComment" — `backend/src/test/java/com/forcitis/one/DdlNamingTest.java:925`
- `EP-38d8528e03` — "everyTableDefinitionIsParsed" — `backend/src/test/java/com/forcitis/one/DdlNamingTest.java:320`
- `EP-495f569589` — "noSentinelsAndNoTypedTextKeys" — `backend/src/test/java/com/forcitis/one/DdlNamingTest.java:1000`
- `EP-506e56ee8e` — "resultingSchemaFollowsClassWordsAndConstraintNames" — `backend/src/test/java/com/forcitis/one/DdlNamingTest.java:346`
- `EP-69404787e1` — "unnamedTableChecksUseReferencedColumns" — `backend/src/test/java/com/forcitis/one/DdlNamingTest.java:126`
- `EP-6baf9701fa` — "injectedViolationsAreCaught" — `backend/src/test/java/com/forcitis/one/DdlNamingTest.java:376`
- `EP-777837cd0c` — "sourceLengthCheckCatchesUtf8AndIntermediateNames" — `backend/src/test/java/com/forcitis/one/DdlNamingTest.java:66`
- `EP-829daf7f66` — "auditReferenceGuardCatchesInjectedSamples" — `backend/src/test/java/com/forcitis/one/DdlNamingTest.java:484`
- `EP-8404b2c9c4` — "exclusionConstraintsAreNamedEx" — `backend/src/test/java/com/forcitis/one/DdlNamingTest.java:886`
- `EP-86facb4bfb` — "migrationsFollowTheNamingStandard" — `backend/src/test/java/com/forcitis/one/DdlNamingTest.java:302`
- `EP-8bdccf1bb7` — "supplementaryUnquotedIdentifiersAreScannedByCodePoint" — `backend/src/test/java/com/forcitis/one/DdlNamingTest.java:195`
- `EP-8f24747806` — "laterRenamesAreFollowed" — `backend/src/test/java/com/forcitis/one/DdlNamingTest.java:423`
- `EP-98da9d643c` — "sensitiveColumnsAreClassified" — `backend/src/test/java/com/forcitis/one/DdlNamingTest.java:367`
- `EP-9d9f169807` — "unnamedAlterConstraintsAreModeledAndFailNamingRules" — `backend/src/test/java/com/forcitis/one/DdlNamingTest.java:107`
- `EP-a5095b3e90` — "sentinelGuardCatchesInjectedSamples" — `backend/src/test/java/com/forcitis/one/DdlNamingTest.java:1006`
- `EP-a7a239e0de` — "migrationAndBaselineIdentifiersFit63Bytes" — `backend/src/test/java/com/forcitis/one/DdlNamingTest.java:26`
- `EP-acecda3f13` — "lengthScannerExcludesTextAndRejectsBrokenInput" — `backend/src/test/java/com/forcitis/one/DdlNamingTest.java:81`
- `EP-b306668c8a` — "unnamedIndexesAreModeledAndFailNamingRules" — `backend/src/test/java/com/forcitis/one/DdlNamingTest.java:94`
- `EP-d8ba967364` — "escapeStringPrefixMustBeAStandaloneToken" — `backend/src/test/java/com/forcitis/one/DdlNamingTest.java:184`
- `EP-d9ce7b2892` — "automaticNamesHonorThe63ByteBoundaryBeforeDatabaseTruncation" — `backend/src/test/java/com/forcitis/one/DdlNamingTest.java:136`
- `EP-f23c70cb37` — "theParserGuardCountsWhatTheModelMisses" — `backend/src/test/java/com/forcitis/one/DdlNamingTest.java:328`
- `EP-fb17a0f9c6` — "auditEntriesAreReferencedByIdOnly" — `backend/src/test/java/com/forcitis/one/DdlNamingTest.java:478`

### backend/src/test/java/com/forcitis/one/DocumentReferenceGuardTest.java（2 条）

- `EP-acd1c48747` — "contractsUseDocumentReference" — `backend/src/test/java/com/forcitis/one/DocumentReferenceGuardTest.java:40`
- `EP-d81101cf7d` — "aBareReferenceIsCaught" — `backend/src/test/java/com/forcitis/one/DocumentReferenceGuardTest.java:48`

### backend/src/test/java/com/forcitis/one/DocumentTypeRegistryGuardTest.java（2 条）

- `EP-50c3b7008e` — "platformCodeNamesNoBusinessDocument" — `backend/src/test/java/com/forcitis/one/DocumentTypeRegistryGuardTest.java:29`
- `EP-5aa0cf065f` — "tablesDoNotEnumerateDocumentTypes" — `backend/src/test/java/com/forcitis/one/DocumentTypeRegistryGuardTest.java:50`

### backend/src/test/java/com/forcitis/one/ErrorCodeGlossaryTest.java（2 条）

- `EP-d3221ac5ba` — "missingTermIsFound" — `backend/src/test/java/com/forcitis/one/ErrorCodeGlossaryTest.java:45`
- `EP-f95a01a874` — "everyErrorCodeIsAGlossaryTerm" — `backend/src/test/java/com/forcitis/one/ErrorCodeGlossaryTest.java:35`

### backend/src/test/java/com/forcitis/one/EventBusinessDateGuardTest.java（6 条）

- `EP-0e66819621` — "clockUseInADomainPackageIsCaught" — `backend/src/test/java/com/forcitis/one/EventBusinessDateGuardTest.java:63`
- `EP-31be9f06b7` — "domainClassesDoNotHoldTheClock" — `backend/src/test/java/com/forcitis/one/EventBusinessDateGuardTest.java:51`
- `EP-41553a484f` — "catalogueDeclarationAndEnvelopeAgree" — `backend/src/test/java/com/forcitis/one/EventBusinessDateGuardTest.java:90`
- `EP-ae690ed231` — "domainClassesDoNotCallNow" — `backend/src/test/java/com/forcitis/one/EventBusinessDateGuardTest.java:57`
- `EP-bc9a89b92e` — "otherPackagesAreNotJudged" — `backend/src/test/java/com/forcitis/one/EventBusinessDateGuardTest.java:73`
- `EP-f594db6057` — "realCatalogueIsLoaded" — `backend/src/test/java/com/forcitis/one/EventBusinessDateGuardTest.java:102`

### backend/src/test/java/com/forcitis/one/EventCatalogueCoverageTest.java（1 条）

- `EP-0f34e748b1` — "everyCatalogueEventIsExercised" — `backend/src/test/java/com/forcitis/one/EventCatalogueCoverageTest.java:26`

### backend/src/test/java/com/forcitis/one/EventCataloguePolicyTest.java（7 条）

- `EP-4e10edd41d` — "rightShapePasses" — `backend/src/test/java/com/forcitis/one/EventCataloguePolicyTest.java:54`
- `EP-69e0e50637` — "catalogueConsistency" — `backend/src/test/java/com/forcitis/one/EventCataloguePolicyTest.java:80`
- `EP-7b406bfc89` — "publisherTestRule" — `backend/src/test/java/com/forcitis/one/EventCataloguePolicyTest.java:98`
- `EP-96987bd125` — "wrongShapesFail" — `backend/src/test/java/com/forcitis/one/EventCataloguePolicyTest.java:60`
- `EP-cb34023156` — "consumerRule" — `backend/src/test/java/com/forcitis/one/EventCataloguePolicyTest.java:130`
- `EP-cdcd7512bf` — "locationRule" — `backend/src/test/java/com/forcitis/one/EventCataloguePolicyTest.java:71`
- `EP-f20a8ce6a2` — "auditRule" — `backend/src/test/java/com/forcitis/one/EventCataloguePolicyTest.java:110`

### backend/src/test/java/com/forcitis/one/EventCatalogueTest.java（10 条）

- `EP-1d6a71c39b` — "catalogueMatchesTheCode" — `backend/src/test/java/com/forcitis/one/EventCatalogueTest.java:84`
- `EP-3e3c8cff27` — "everyEventHasAPublisherTest" — `backend/src/test/java/com/forcitis/one/EventCatalogueTest.java:90`
- `EP-3e8f12c90d` — "composedCatalogueIsCurrent" — `backend/src/test/java/com/forcitis/one/EventCatalogueTest.java:124`
- `EP-ae894c1e52` — "everyEventIsAuditedOrSaysWhyNot" — `backend/src/test/java/com/forcitis/one/EventCatalogueTest.java:103`
- `EP-bea3c4a51d` — "catalogueGuardsCatchInjectedSamples" — `backend/src/test/java/com/forcitis/one/EventCatalogueTest.java:245`
- `EP-c4846ff838` — "pendingModulesHaveNoFragmentYet" — `backend/src/test/java/com/forcitis/one/EventCatalogueTest.java:116`
- `EP-c8380441b7` — "shape" — `backend/src/test/java/com/forcitis/one/EventCatalogueTest.java:75`
- `EP-f5ae0571f7` — "ids_and_kinds_of_the_catalogue" — `backend/src/test/java/com/forcitis/one/EventCatalogueTest.java:226`
- `EP-fbc995d8c0` — "consumersAreDeclared" — `backend/src/test/java/com/forcitis/one/EventCatalogueTest.java:110`
- `EP-fc57653fd9` — "eventsOnlyInTheEventsPackage" — `backend/src/test/java/com/forcitis/one/EventCatalogueTest.java:68`

### backend/src/test/java/com/forcitis/one/EventSequenceCoverageTest.java（1 条）

- `EP-fb038a857c` — "everyAggregateRootIsCovered" — `backend/src/test/java/com/forcitis/one/EventSequenceCoverageTest.java:25`

### backend/src/test/java/com/forcitis/one/GlossaryTest.java（5 条）

- `EP-8cd72eade4` — "domainWordsAreRegistered" — `backend/src/test/java/com/forcitis/one/GlossaryTest.java:114`
- `EP-9d321a7c07` — "aggregatesAndEventsAreGlossaryTerms" — `backend/src/test/java/com/forcitis/one/GlossaryTest.java:190`
- `EP-d1702f50e6` — "glossaryIsWellFormed" — `backend/src/test/java/com/forcitis/one/GlossaryTest.java:183`
- `EP-d9367be0bd` — "forbiddenSpellingsAreNotUsed" — `backend/src/test/java/com/forcitis/one/GlossaryTest.java:141`
- `EP-f2faa352fd` — "commentsStartWithTheGlossaryChineseName" — `backend/src/test/java/com/forcitis/one/GlossaryTest.java:162`

### backend/src/test/java/com/forcitis/one/IdMakingGuardTest.java（6 条）

- `EP-1f452f5a9d` — "rootIdsAreFinal" — `backend/src/test/java/com/forcitis/one/IdMakingGuardTest.java:145`
- `EP-21ff5ae997` — "onlyUuidsMakesRandomUuids" — `backend/src/test/java/com/forcitis/one/IdMakingGuardTest.java:151`
- `EP-52babf433c` — "repositoriesDoNotMakeRootIds" — `backend/src/test/java/com/forcitis/one/IdMakingGuardTest.java:139`
- `EP-5c16f8b84e` — "theRootIdGuardNeedsExactlyOneIdPerRoot" — `backend/src/test/java/com/forcitis/one/IdMakingGuardTest.java:245`
- `EP-9a6a26c8ef` — "theRootIdGuardCatchesInjectedSamples" — `backend/src/test/java/com/forcitis/one/IdMakingGuardTest.java:236`
- `EP-fe636c8a01` — "theGuardsCatchInjectedSamples" — `backend/src/test/java/com/forcitis/one/IdMakingGuardTest.java:255`

### backend/src/test/java/com/forcitis/one/IdPrintingGuardTest.java（3 条）

- `EP-487688886a` — "uuidIdsRefuseNilAndNull" — `backend/src/test/java/com/forcitis/one/IdPrintingGuardTest.java:64`
- `EP-61cb705d8b` — "idsPrintTheirValue" — `backend/src/test/java/com/forcitis/one/IdPrintingGuardTest.java:57`
- `EP-62856df399` — "aDefaultToStringIsCaught" — `backend/src/test/java/com/forcitis/one/IdPrintingGuardTest.java:80`

### backend/src/test/java/com/forcitis/one/IdempotentEndpointGuardTest.java（8 条）

- `EP-1ee9f8a474` — "commentsNameTheAnswerTypes" — `backend/src/test/java/com/forcitis/one/IdempotentEndpointGuardTest.java:112`
- `EP-6907503b53` — "typesThatCannotBeOpenedUpFail" — `backend/src/test/java/com/forcitis/one/IdempotentEndpointGuardTest.java:260`
- `EP-877f9eaf58` — "wildcardAnswersAreOpenedUpOrFail" — `backend/src/test/java/com/forcitis/one/IdempotentEndpointGuardTest.java:235`
- `EP-9899d6028b` — "answersCarryNoDirectIdentifier" — `backend/src/test/java/com/forcitis/one/IdempotentEndpointGuardTest.java:95`
- `EP-abb82c8acb` — "forbiddenComponentsAreCaught" — `backend/src/test/java/com/forcitis/one/IdempotentEndpointGuardTest.java:192`
- `EP-bc4122efbb` — "moreFormsAreCaught" — `backend/src/test/java/com/forcitis/one/IdempotentEndpointGuardTest.java:205`
- `EP-bdd6eea184` — "listedEndpointsAreTheEndpointsThatApply" — `backend/src/test/java/com/forcitis/one/IdempotentEndpointGuardTest.java:81`
- `EP-c89bdd1eb4` — "genericClassesFail" — `backend/src/test/java/com/forcitis/one/IdempotentEndpointGuardTest.java:248`

### backend/src/test/java/com/forcitis/one/IdempotentResponseSizeTest.java（4 条）

- `EP-3373d8e7fb` — "tooLargeIsMeasured" — `backend/src/test/java/com/forcitis/one/IdempotentResponseSizeTest.java:79`
- `EP-36dd30796e` — "limitIsTheLimitOfTheTable" — `backend/src/test/java/com/forcitis/one/IdempotentResponseSizeTest.java:41`
- `EP-3a10883194` — "refusalIsFarUnderTheLimit" — `backend/src/test/java/com/forcitis/one/IdempotentResponseSizeTest.java:67`
- `EP-b30ff92b15` — "largestAnswersAreFarUnderTheLimit" — `backend/src/test/java/com/forcitis/one/IdempotentResponseSizeTest.java:49`

### backend/src/test/java/com/forcitis/one/KernelPortDetourTest.java（2 条）

- `EP-1097d7cd40` — "P2_H2_noKernelPortDetoursAnExistingEdge" — `backend/src/test/java/com/forcitis/one/KernelPortDetourTest.java:91`
- `EP-cef03da519` — "theGuardReportsADetourWhenTheEdgeExists" — `backend/src/test/java/com/forcitis/one/KernelPortDetourTest.java:97`

### backend/src/test/java/com/forcitis/one/LegacyShapeTest.java（13 条）

- `EP-20109a74a4` — "everyRuleFailsOnItsSample" — `backend/src/test/java/com/forcitis/one/LegacyShapeTest.java:486`
- `EP-293dd83ca8` — "exceptionsAreNarrow" — `backend/src/test/java/com/forcitis/one/LegacyShapeTest.java:604`
- `EP-2b8c653bd9` — "approvedAreasExist" — `backend/src/test/java/com/forcitis/one/LegacyShapeTest.java:45`
- `EP-5c36a2dcdd` — "exceptionsFileFormat" — `backend/src/test/java/com/forcitis/one/LegacyShapeTest.java:624`
- `EP-75b8a882bf` — "everyRuleLeavesItsCounterExampleAlone" — `backend/src/test/java/com/forcitis/one/LegacyShapeTest.java:508`
- `EP-7ef59fbe83` — "frontEndScope" — `backend/src/test/java/com/forcitis/one/LegacyShapeTest.java:524`
- `EP-884634c326` — "guardTestsAreNotScannedButModuleTestsAre" — `backend/src/test/java/com/forcitis/one/LegacyShapeTest.java:591`
- `EP-88d7397d9b` — "theCodeBaseHasNoOldSystemShape" — `backend/src/test/java/com/forcitis/one/LegacyShapeTest.java:360`
- `EP-8c6ef8e29a` — "aMissingAreaIsCaught" — `backend/src/test/java/com/forcitis/one/LegacyShapeTest.java:51`
- `EP-dc06f55e2b` — "cleanTreeAndAllowances" — `backend/src/test/java/com/forcitis/one/LegacyShapeTest.java:557`
- `EP-dc9c4a6fad` — "exceptionsAreApprovedAndNeeded" — `backend/src/test/java/com/forcitis/one/LegacyShapeTest.java:370`
- `EP-f02e53e49f` — "instanceIdsStayInTheirAreas" — `backend/src/test/java/com/forcitis/one/LegacyShapeTest.java:540`
- `EP-f19858f44e` — "moduleOfReadsAcceptancePackages" — `backend/src/test/java/com/forcitis/one/LegacyShapeTest.java:572`

### backend/src/test/java/com/forcitis/one/ListPagingGuardTest.java（4 条）

- `EP-17cf4e5f79` — "pageSizeIsWrittenOnceAndTheContractSaysSo" — `backend/src/test/java/com/forcitis/one/ListPagingGuardTest.java:116`
- `EP-3fda48d49b` — "collectionsAnswerTheCursorPageShape" — `backend/src/test/java/com/forcitis/one/ListPagingGuardTest.java:83`
- `EP-788a9a780d` — "inMemoryPagingAndConfiguredCapsAreListed" — `backend/src/test/java/com/forcitis/one/ListPagingGuardTest.java:148`
- `EP-f912db02b3` — "everyGapLineIsReadBySomeGuard" — `backend/src/test/java/com/forcitis/one/ListPagingGuardTest.java:182`

### backend/src/test/java/com/forcitis/one/ModularityTest.java（1 条）

- `EP-4716ce4f12` — "modulesRespectBoundaries" — `backend/src/test/java/com/forcitis/one/ModularityTest.java:8`

### backend/src/test/java/com/forcitis/one/ModuleDependencyTest.java（3 条）

- `EP-57534736cb` — "otherUsesOfTheWebModuleAreReported" — `backend/src/test/java/com/forcitis/one/ModuleDependencyTest.java:69`
- `EP-57553416f3` — "dependenciesFollowTheApprovedGraph" — `backend/src/test/java/com/forcitis/one/ModuleDependencyTest.java:27`
- `EP-c80eb6c848` — "identityUsesTheWebModuleOnlyForTheMarker" — `backend/src/test/java/com/forcitis/one/ModuleDependencyTest.java:63`

### backend/src/test/java/com/forcitis/one/MustAuditActionsTest.java（3 条）

- `EP-2151d2b06b` — "literalsAreRead" — `backend/src/test/java/com/forcitis/one/MustAuditActionsTest.java:98`
- `EP-617d1cc62c` — "listAndCodeAgree" — `backend/src/test/java/com/forcitis/one/MustAuditActionsTest.java:77`
- `EP-c3cf2d8591` — "plannedActionsDoNotExistYet" — `backend/src/test/java/com/forcitis/one/MustAuditActionsTest.java:90`

### backend/src/test/java/com/forcitis/one/NamingSurfaceTest.java（12 条）

- `EP-0bda5e292d` — "bpmnProcessAndDmnDecisionIdsAreModuleSnakeAndUserTasksCamelCaseGlossaryWords" — `backend/src/test/java/com/forcitis/one/NamingSurfaceTest.java:511`
- `EP-0cbffc87d0` — "metricNamesAreModuleDottedLowerCaseStringLiterals" — `backend/src/test/java/com/forcitis/one/NamingSurfaceTest.java:359`
- `EP-19b61015c8` — "httpPathsAreApiVersionSubdomainSlugPluralGlossaryResourceOptionalIdAndAction" — `backend/src/test/java/com/forcitis/one/NamingSurfaceTest.java:78`
- `EP-1d99a409e3` — "scheduledJobsAndTheirLockNamesAreModuleDottedLowerCase" — `backend/src/test/java/com/forcitis/one/NamingSurfaceTest.java:335`
- `EP-54244e50f6` — "errorCodesAreEnumConstantsNamedSubdomainBcReasonInUpperSnakeGlossaryWords" — `backend/src/test/java/com/forcitis/one/NamingSurfaceTest.java:573`
- `EP-5487642a62` — "flywayFilesAreTimestampModuleEnglishSnakeCaseInTheModuleFolder" — `backend/src/test/java/com/forcitis/one/NamingSurfaceTest.java:225`
- `EP-600001076b` — "numberingDocumentTypesAreUpperSnakeGlossaryWordsOfARegisteredBcAndEachHasExactlyTheSeededRule" — `backend/src/test/java/com/forcitis/one/NamingSurfaceTest.java:273`
- `EP-682e17462c` — "ownerGuardRefusesASubdomainAndAnUnknownId" — `backend/src/test/java/com/forcitis/one/NamingSurfaceTest.java:300`
- `EP-88c9e5bcdf` — "ownConfigurationKeysStartWithForcitisModulePrefixAndOthersAreKnownFrameworkKeys" — `backend/src/test/java/com/forcitis/one/NamingSurfaceTest.java:396`
- `EP-b476ff6bf8` — "metricNameGuardCatchesAMissingPrefixAnAlienModuleAndTheWrongOwner" — `backend/src/test/java/com/forcitis/one/NamingSurfaceTest.java:385`
- `EP-cfd482785a` — "permissionCodesAreModuleColonGlossaryResourceColonStandardAction" — `backend/src/test/java/com/forcitis/one/NamingSurfaceTest.java:163`
- `EP-d7f3bf4c7f` — "domainEventRecordComponentsAreCamelCaseGlossaryWords" — `backend/src/test/java/com/forcitis/one/NamingSurfaceTest.java:142`

### backend/src/test/java/com/forcitis/one/NullableResponseGuardTest.java（2 条）

- `EP-b7adb26064` — "boxedResponseComponentsAreNullable" — `backend/src/test/java/com/forcitis/one/NullableResponseGuardTest.java:35`
- `EP-f10e5b5d13` — "unmarkedIsFound" — `backend/src/test/java/com/forcitis/one/NullableResponseGuardTest.java:51`

### backend/src/test/java/com/forcitis/one/OpenApiExportTest.java（4 条）

- `EP-4c8559b75e` — "idFieldsOfThisSystemAreStringUuidAndEveryOtherIdFieldIsClassified" — `backend/src/test/java/com/forcitis/one/OpenApiExportTest.java:143`
- `EP-95fb4c7cda` — "componentNamesAreUniquePerJavaType" — `backend/src/test/java/com/forcitis/one/OpenApiExportTest.java:199`
- `EP-e25410d39d` — "exportsTheOpenApiDocumentForTheFrontendClient" — `backend/src/test/java/com/forcitis/one/OpenApiExportTest.java:86`
- `EP-f1aae81b42` — "pathsUseTheVersionSegmentAndResponseComponentsAreRequiredUnlessNullable" — `backend/src/test/java/com/forcitis/one/OpenApiExportTest.java:99`

### backend/src/test/java/com/forcitis/one/OutwardTextGuardTest.java（2 条）

- `EP-30c23bb7d0` — "outwardTextsUseTheGlossary" — `backend/src/test/java/com/forcitis/one/OutwardTextGuardTest.java:98`
- `EP-7eadd89020` — "unknownWordsAreFound" — `backend/src/test/java/com/forcitis/one/OutwardTextGuardTest.java:111`

### backend/src/test/java/com/forcitis/one/PackagedJarIT.java（2 条）

- `EP-2b129f7578` — "replayRunsOfflineFromTheJar" — `backend/src/test/java/com/forcitis/one/PackagedJarIT.java:44`
- `EP-54749a93dd` — "javaJarStartsTheApplication" — `backend/src/test/java/com/forcitis/one/PackagedJarIT.java:32`

### backend/src/test/java/com/forcitis/one/PrimaryKeyTypeGuardTest.java（7 条）

- `EP-3db4fd71ff` — "everyKeyIsOneUuidColumnTheApplicationMakes" — `backend/src/test/java/com/forcitis/one/PrimaryKeyTypeGuardTest.java:244`
- `EP-3ea1a7371f` — "theRegistryReaderRefusesMalformedLines" — `backend/src/test/java/com/forcitis/one/PrimaryKeyTypeGuardTest.java:369`
- `EP-3ffe38e67f` — "theGuardCatchesMisleadingRegistryLines" — `backend/src/test/java/com/forcitis/one/PrimaryKeyTypeGuardTest.java:352`
- `EP-5a13b0001b` — "theGuardReadsForeignKeysOutsideTheColumnLine" — `backend/src/test/java/com/forcitis/one/PrimaryKeyTypeGuardTest.java:336`
- `EP-6e346ee684` — "thePartitionedTableIsRegisteredAndKeepsItsShape" — `backend/src/test/java/com/forcitis/one/PrimaryKeyTypeGuardTest.java:392`
- `EP-7d2d15f477` — "theGuardCatchesInjectedSamples" — `backend/src/test/java/com/forcitis/one/PrimaryKeyTypeGuardTest.java:271`
- `EP-cd65aede73` — "theGuardCatchesCrossSchemaVersionReferenceSamples" — `backend/src/test/java/com/forcitis/one/PrimaryKeyTypeGuardTest.java:305`

### backend/src/test/java/com/forcitis/one/ReadModelGuardTest.java（2 条）

- `EP-a4b00f1f1b` — "aProjectionOnARepositoryIsCaught" — `backend/src/test/java/com/forcitis/one/ReadModelGuardTest.java:54`
- `EP-ab34ef40b9` — "projectionsDoNotUseWriteRepositories" — `backend/src/test/java/com/forcitis/one/ReadModelGuardTest.java:47`

### backend/src/test/java/com/forcitis/one/ReadPathsDoNotLockTest.java（2 条）

- `EP-6ed325000a` — "aLockingReadIsCaught" — `backend/src/test/java/com/forcitis/one/ReadPathsDoNotLockTest.java:97`
- `EP-75dde5ec0f` — "readPathsDoNotLock" — `backend/src/test/java/com/forcitis/one/ReadPathsDoNotLockTest.java:88`

### backend/src/test/java/com/forcitis/one/RepositoryWriteGuardTest.java（5 条）

- `EP-17f12f6cd7` — "insertsListTheStatus" — `backend/src/test/java/com/forcitis/one/RepositoryWriteGuardTest.java:68`
- `EP-50139604d9` — "writesOfVersionedTablesAreConditional" — `backend/src/test/java/com/forcitis/one/RepositoryWriteGuardTest.java:104`
- `EP-9fb57a1d30` — "unversionedTableWritesAreRegistered" — `backend/src/test/java/com/forcitis/one/RepositoryWriteGuardTest.java:131`
- `EP-b102787c13` — "policyFindsWhatItShould" — `backend/src/test/java/com/forcitis/one/RepositoryWriteGuardTest.java:157`
- `EP-bd4f6abd0c` — "repositoryWritesAreTheAggregatesSave" — `backend/src/test/java/com/forcitis/one/RepositoryWriteGuardTest.java:80`

### backend/src/test/java/com/forcitis/one/RoleSeparatedDeploymentIT.java（5 条）

- `EP-16a2fb7b8b` — "theRuntimeAccountHasOnlyWhatItNeeds" — `backend/src/test/java/com/forcitis/one/RoleSeparatedDeploymentIT.java:170`
- `EP-6e463f2881` — "theRuntimeAccountPreCreatesButCannotRemovePartitions" — `backend/src/test/java/com/forcitis/one/RoleSeparatedDeploymentIT.java:184`
- `EP-b4714ad641` — "smokeAsTheRuntimeAccount" — `backend/src/test/java/com/forcitis/one/RoleSeparatedDeploymentIT.java:141`
- `EP-c0b22f5e87` — "theMaintenanceRoleRunsOnlyTheDropFunction" — `backend/src/test/java/com/forcitis/one/RoleSeparatedDeploymentIT.java:209`
- `EP-f552972819` — "theRuntimeAccountCanAskWhetherAPartitionAndAnEmployeeExist" — `backend/src/test/java/com/forcitis/one/RoleSeparatedDeploymentIT.java:159`

### backend/src/test/java/com/forcitis/one/SchemaPlacementIT.java（3 条）

- `EP-59f4d90baa` — "moduleTablesStayInTheirSchemas" — `backend/src/test/java/com/forcitis/one/SchemaPlacementIT.java:62`
- `EP-864eaf6fec` — "historyAndEngineTablesLandInTheirOwnSchemas" — `backend/src/test/java/com/forcitis/one/SchemaPlacementIT.java:51`
- `EP-ab1ec6c07b` — "retiredSchemasAreNeitherConfiguredNorCreated" — `backend/src/test/java/com/forcitis/one/SchemaPlacementIT.java:72`

### backend/src/test/java/com/forcitis/one/SecurityInvariantsTest.java（3 条）

- `EP-1eca3dea7a` — "legacyTokenTablesAreNeverTouched" — `backend/src/test/java/com/forcitis/one/SecurityInvariantsTest.java:25`
- `EP-92d032e5f4` — "wecomMessagesNeverBroadcast" — `backend/src/test/java/com/forcitis/one/SecurityInvariantsTest.java:58`
- `EP-e25f229242` — "ticketIssuersAreReviewedAndNeverTheLegacyBackend" — `backend/src/test/java/com/forcitis/one/SecurityInvariantsTest.java:33`

### backend/src/test/java/com/forcitis/one/SensitiveColumnExposureGuardTest.java（3 条）

- `EP-0a12a65a70` — "scannerFindsClassifiedFields" — `backend/src/test/java/com/forcitis/one/SensitiveColumnExposureGuardTest.java:104`
- `EP-da4ad77811` — "responsesDoNotCarrySensitiveColumns" — `backend/src/test/java/com/forcitis/one/SensitiveColumnExposureGuardTest.java:82`
- `EP-f192c565e6` — "classificationIsRead" — `backend/src/test/java/com/forcitis/one/SensitiveColumnExposureGuardTest.java:113`

### backend/src/test/java/com/forcitis/one/SingleAggregateGuardTest.java（4 条）

- `EP-36d60d9949` — "theAspectCatchesAnUnregisteredPair" — `backend/src/test/java/com/forcitis/one/SingleAggregateGuardTest.java:88`
- `EP-3999882323` — "aRegisteredPairMustBeNamedByTheEvents" — `backend/src/test/java/com/forcitis/one/SingleAggregateGuardTest.java:97`
- `EP-5f4bca75f9` — "theRegisterIsCurrent" — `backend/src/test/java/com/forcitis/one/SingleAggregateGuardTest.java:34`
- `EP-8546ddc41e` — "anUnregisteredSetIsCaught" — `backend/src/test/java/com/forcitis/one/SingleAggregateGuardTest.java:48`

### backend/src/test/java/com/forcitis/one/SqlOrderByGuardTest.java（2 条）

- `EP-a9acd38ac2` — "noOrderByIdsAlone" — `backend/src/test/java/com/forcitis/one/SqlOrderByGuardTest.java:96`
- `EP-f62ee37eda` — "theGuardCatchesInjectedSamples" — `backend/src/test/java/com/forcitis/one/SqlOrderByGuardTest.java:108`

### backend/src/test/java/com/forcitis/one/StateWriteGuardTest.java（3 条）

- `EP-1e6288c2f6` — "sqlWritesAreCaught" — `backend/src/test/java/com/forcitis/one/StateWriteGuardTest.java:77`
- `EP-39439fc98e` — "enumWritesAreCaught" — `backend/src/test/java/com/forcitis/one/StateWriteGuardTest.java:64`
- `EP-b9f715d475` — "noStatusWritesOutsideTheAggregate" — `backend/src/test/java/com/forcitis/one/StateWriteGuardTest.java:41`

### backend/src/test/java/com/forcitis/one/SystemActorCatalogueTest.java（9 条）

- `EP-11f7293053` — "anActorInAVariableIsReported" — `backend/src/test/java/com/forcitis/one/SystemActorCatalogueTest.java:233`
- `EP-2326bbbe83` — "jobsAndListenersAreDeclared" — `backend/src/test/java/com/forcitis/one/SystemActorCatalogueTest.java:103`
- `EP-66956975ea` — "theEventCatalogueListsTheSameActors" — `backend/src/test/java/com/forcitis/one/SystemActorCatalogueTest.java:284`
- `EP-6dc0844a82` — "namesInTheTestsAreDeclared" — `backend/src/test/java/com/forcitis/one/SystemActorCatalogueTest.java:249`
- `EP-7e51818a2b` — "theCatalogueIsConsistent" — `backend/src/test/java/com/forcitis/one/SystemActorCatalogueTest.java:78`
- `EP-93b146ac51` — "actorConstantsComeFromTheAnnotation" — `backend/src/test/java/com/forcitis/one/SystemActorCatalogueTest.java:141`
- `EP-ef81569c81` — "actorsAreNamedPlainly" — `backend/src/test/java/com/forcitis/one/SystemActorCatalogueTest.java:207`
- `EP-fad004ca9a` — "theTestCatalogueCannotPassForProduction" — `backend/src/test/java/com/forcitis/one/SystemActorCatalogueTest.java:259`
- `EP-fd813facf8` — "namesEnteredInCodeAreDeclared" — `backend/src/test/java/com/forcitis/one/SystemActorCatalogueTest.java:133`

### backend/src/test/java/com/forcitis/one/TestContainerGuardTest.java（2 条）

- `EP-8efa908411` — "containersAreStartedThroughTestPostgres" — `backend/src/test/java/com/forcitis/one/TestContainerGuardTest.java:38`
- `EP-dca47d76c5` — "sqlServerContainersAreStartedThroughTestMssql" — `backend/src/test/java/com/forcitis/one/TestContainerGuardTest.java:86`

### backend/src/test/java/com/forcitis/one/TestIsolationGuardTest.java（3 条）

- `EP-2d0a0934ca` — "testsStayInTheirOwnSchema" — `backend/src/test/java/com/forcitis/one/TestIsolationGuardTest.java:57`
- `EP-66ca4a0c3a` — "acceptanceTestsBelongToTheirModule" — `backend/src/test/java/com/forcitis/one/TestIsolationGuardTest.java:88`
- `EP-c011987921` — "foreignSchemaIsCaught" — `backend/src/test/java/com/forcitis/one/TestIsolationGuardTest.java:97`

### backend/src/test/java/com/forcitis/one/TestPostgresNameTest.java（2 条）

- `EP-4139fcf54e` — "namesThatSanitiseAlikeStayApart" — `backend/src/test/java/com/forcitis/one/TestPostgresNameTest.java:21`
- `EP-d51afefbe1` — "databaseNamesAreValidAndShort" — `backend/src/test/java/com/forcitis/one/TestPostgresNameTest.java:11`

### backend/src/test/java/com/forcitis/one/TestPostgresStartupTest.java（4 条）

- `EP-511d2b390f` — "failureCarriesTheEvidence" — `backend/src/test/java/com/forcitis/one/TestPostgresStartupTest.java:38`
- `EP-dd071ad97d` — "startIsRepeated" — `backend/src/test/java/com/forcitis/one/TestPostgresStartupTest.java:15`
- `EP-e8c0cffdc7` — "failedStartIsExplained" — `backend/src/test/java/com/forcitis/one/TestPostgresStartupTest.java:24`
- `EP-e99193fc11` — "shortLog" — `backend/src/test/java/com/forcitis/one/TestPostgresStartupTest.java:53`

### backend/src/test/java/com/forcitis/one/TimeSourceGuardTest.java（8 条）

- `EP-35f2b3a3e5` — "sqlClockScannerFindsOnlyLiterals" — `backend/src/test/java/com/forcitis/one/TimeSourceGuardTest.java:168`
- `EP-616c235c64` — "aStrayUserIsCaught" — `backend/src/test/java/com/forcitis/one/TimeSourceGuardTest.java:110`
- `EP-838bc729a4` — "ambientTimeIsCaught" — `backend/src/test/java/com/forcitis/one/TimeSourceGuardTest.java:62`
- `EP-8c16032201` — "productionCodeNeverAsksTheMachine" — `backend/src/test/java/com/forcitis/one/TimeSourceGuardTest.java:56`
- `EP-9666b3091e` — "onlyTheExpiredSessionsUseTheStoreClock" — `backend/src/test/java/com/forcitis/one/TimeSourceGuardTest.java:104`
- `EP-d00bfd5cce` — "aNamesakeIsNotExempt" — `backend/src/test/java/com/forcitis/one/TimeSourceGuardTest.java:86`
- `EP-f713829012` — "theExemptClassesAreClean" — `backend/src/test/java/com/forcitis/one/TimeSourceGuardTest.java:77`
- `EP-fdebc5548a` — "sqlStringsDoNotReadTheDatabaseClock" — `backend/src/test/java/com/forcitis/one/TimeSourceGuardTest.java:152`

### backend/src/test/java/com/forcitis/one/TypedIdComparisonGuardTest.java（6 条）

- `EP-2dd09b2128` — "theGuardSeesCrossTypeComparisons" — `backend/src/test/java/com/forcitis/one/TypedIdComparisonGuardTest.java:99`
- `EP-43392e150c` — "theGuardSeesCrossTypeConstruction" — `backend/src/test/java/com/forcitis/one/TypedIdComparisonGuardTest.java:115`
- `EP-5661e384cc` — "errorProneChecksStayOn" — `backend/src/test/java/com/forcitis/one/TypedIdComparisonGuardTest.java:125`
- `EP-7ebcc34012` — "conversionsOutOfSightAreOnRecord" — `backend/src/test/java/com/forcitis/one/TypedIdComparisonGuardTest.java:52`
- `EP-90a4128e4d` — "productionCodeComparesTypedIds" — `backend/src/test/java/com/forcitis/one/TypedIdComparisonGuardTest.java:64`
- `EP-ae89e5687a` — "theGuardSeesRawValueComparisons" — `backend/src/test/java/com/forcitis/one/TypedIdComparisonGuardTest.java:89`

### backend/src/test/java/com/forcitis/one/organization/workforce/guard/WorkforceConfigurationTest.java（3 条）

- `EP-a9d3458961` — "safeDefaults" — `backend/src/test/java/com/forcitis/one/organization/workforce/guard/WorkforceConfigurationTest.java:30`
- `EP-c1c229e952` — "permissionCodesMatchTheIdentityCatalogue" — `backend/src/test/java/com/forcitis/one/organization/workforce/guard/WorkforceConfigurationTest.java:57`
- `EP-c23a4cfcbe` — "periods" — `backend/src/test/java/com/forcitis/one/organization/workforce/guard/WorkforceConfigurationTest.java:40`

### backend/src/test/java/com/forcitis/one/organization/workforce/guard/WorkforceSchemaGuardTest.java（4 条）

- `EP-284f8c90b6` — "moduleUsesNoMoney" — `backend/src/test/java/com/forcitis/one/organization/workforce/guard/WorkforceSchemaGuardTest.java:102`
- `EP-35b1d1f093` — "patternCatchesMoney" — `backend/src/test/java/com/forcitis/one/organization/workforce/guard/WorkforceSchemaGuardTest.java:79`
- `EP-a4bdb41c92` — "apiExposesNoMoney" — `backend/src/test/java/com/forcitis/one/organization/workforce/guard/WorkforceSchemaGuardTest.java:91`
- `EP-c8ab3723fe` — "noMoneyColumns" — `backend/src/test/java/com/forcitis/one/organization/workforce/guard/WorkforceSchemaGuardTest.java:33`

### backend/src/test/java/com/forcitis/one/platform/eventing/infrastructure/ScalarIdModuleTest.java（4 条）

- `EP-1c8c589a53` — "idsReadBack" — `backend/src/test/java/com/forcitis/one/platform/eventing/infrastructure/ScalarIdModuleTest.java:60`
- `EP-697daec478` — "idsAreWrittenAsText" — `backend/src/test/java/com/forcitis/one/platform/eventing/infrastructure/ScalarIdModuleTest.java:48`
- `EP-a0dc6d5aa6` — "badIdsAreRefused" — `backend/src/test/java/com/forcitis/one/platform/eventing/infrastructure/ScalarIdModuleTest.java:90`
- `EP-a23a61c007` — "everyTypedIdRoundTrips" — `backend/src/test/java/com/forcitis/one/platform/eventing/infrastructure/ScalarIdModuleTest.java:72`

### backend/src/test/java/com/forcitis/one/platform/file/guard/PlatformFileContentCallersTest.java（3 条）

- `EP-1b4233a87f` — "aBusinessModuleUsingEitherInterfaceIsCaught" — `backend/src/test/java/com/forcitis/one/platform/file/guard/PlatformFileContentCallersTest.java:40`
- `EP-3e54254f54` — "onlyTheMigrationUsesFileImports" — `backend/src/test/java/com/forcitis/one/platform/file/guard/PlatformFileContentCallersTest.java:35`
- `EP-7fd1f706d1` — "onlyPlatformModulesUsePlatformFileContent" — `backend/src/test/java/com/forcitis/one/platform/file/guard/PlatformFileContentCallersTest.java:30`

### backend/src/test/java/com/forcitis/one/platform/integration/guard/CredentialColumnGuardTest.java（6 条）

- `EP-0a3967015d` — "integrationSqlNamesItsColumns" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/CredentialColumnGuardTest.java:177`
- `EP-1c8a2c2c23` — "detectorFindsAnInjectedCredentialColumn" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/CredentialColumnGuardTest.java:231`
- `EP-3aa0aca782` — "readRegistriesListNoCredentialColumn" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/CredentialColumnGuardTest.java:211`
- `EP-a1b959dd77` — "reviewedColumnsAreReleasedOthersStayDenied" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/CredentialColumnGuardTest.java:260`
- `EP-b25faed0ee` — "mainSqlNamesNoCredentialColumn" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/CredentialColumnGuardTest.java:136`
- `EP-f5f2bba9ff` — "starDetectorFindsInjectedStars" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/CredentialColumnGuardTest.java:191`

### backend/src/test/java/com/forcitis/one/platform/integration/guard/ExternalConnectionFactoryGuardTest.java（2 条）

- `EP-83ff5e9b44` — "onlyTheConnectionFactoryBuildsConnections" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/ExternalConnectionFactoryGuardTest.java:64`
- `EP-a2888bd7c4` — "theFactorySetsAllThreeTimeouts" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/ExternalConnectionFactoryGuardTest.java:75`

### backend/src/test/java/com/forcitis/one/platform/integration/guard/ExternalConnectionIsolationTest.java（7 条）

- `EP-146b5c8597` — "connectionsAreNotDataSourceBeans" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/ExternalConnectionIsolationTest.java:115`
- `EP-20d50244a2` — "onlyTheConnectionFactoryBuildsExternalConnections" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/ExternalConnectionIsolationTest.java:40`
- `EP-53f9cd136e` — "qualifiersAreNotUsedElsewhere" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/ExternalConnectionIsolationTest.java:89`
- `EP-851cb9ced2` — "businessLayersDoNotHoldExternalConnections" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/ExternalConnectionIsolationTest.java:81`
- `EP-b3ed5765cf` — "onlyTheApprovedAdaptersObtainExternalConnections" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/ExternalConnectionIsolationTest.java:50`
- `EP-bd930b8541` — "seeyonIsOnlyReachedFromTheSeeyonPackage" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/ExternalConnectionIsolationTest.java:59`
- `EP-e9640350f4` — "legacyAccessIsTwoViews" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/ExternalConnectionIsolationTest.java:136`

### backend/src/test/java/com/forcitis/one/platform/integration/guard/IntegrationConfigurationTest.java（3 条）

- `EP-7c866a3e80` — "safeDefaults" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/IntegrationConfigurationTest.java:41`
- `EP-b13cd2c47d` — "credentialsAreEnvironmentPlaceholders" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/IntegrationConfigurationTest.java:29`
- `EP-cf593311d8` — "eventRegistryUsesTheIntegrationSchema" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/IntegrationConfigurationTest.java:67`

### backend/src/test/java/com/forcitis/one/platform/integration/guard/QualifiedBaseTableGuardTest.java（3 条）

- `EP-989a0714e0` — "otherSchemasInFrontOfABaseTableAreFound" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/QualifiedBaseTableGuardTest.java:84`
- `EP-d103a82831` — "injectedUnqualifiedNamesAreFound" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/QualifiedBaseTableGuardTest.java:65`
- `EP-e047b5be91` — "integrationSqlQualifiesA8BaseTables" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/QualifiedBaseTableGuardTest.java:52`

### backend/src/test/java/com/forcitis/one/platform/integration/guard/ReadOnlyCheckSwitchGuardTest.java（5 条）

- `EP-497d22982a` — "deploymentTemplatesHaveNoSwitch" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/ReadOnlyCheckSwitchGuardTest.java:86`
- `EP-636ae00f8d` — "detectorRecognisesEverySpelling" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/ReadOnlyCheckSwitchGuardTest.java:111`
- `EP-8da3802b7a` — "testsDoNotSetTheRemovedKey" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/ReadOnlyCheckSwitchGuardTest.java:98`
- `EP-a150c0628e` — "noCheckIsConditionalOnAProperty" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/ReadOnlyCheckSwitchGuardTest.java:72`
- `EP-ea8546b53d` — "mainHasNoValidateOnStartupSwitch" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/ReadOnlyCheckSwitchGuardTest.java:61`

### backend/src/test/java/com/forcitis/one/platform/integration/guard/RegisterRowCredentialGuardTest.java（1 条）

- `EP-d9accfe1e4` — "aCredentialRowOfTheCurrentFormatIsFound" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/RegisterRowCredentialGuardTest.java:22`

### backend/src/test/java/com/forcitis/one/platform/integration/guard/SeeyonSourceScanTest.java（9 条）

- `EP-09dacac439` — "onlyRegisteredViewsAreExempt" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/SeeyonSourceScanTest.java:175`
- `EP-27fb507e73` — "integrationSqlNamesOnlyRegisteredObjects" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/SeeyonSourceScanTest.java:196`
- `EP-56b4385b7b` — "registeredBaseObjectsStayInTheIntegrationLayer" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/SeeyonSourceScanTest.java:187`
- `EP-7ef7cb3ccf` — "onlyTheMsdbQualifiedJobsViewIsExemptFromTheRegister" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/SeeyonSourceScanTest.java:227`
- `EP-b1a76cbc6f` — "theScannerIsNotVacuous" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/SeeyonSourceScanTest.java:162`
- `EP-be0b9f896d` — "mainSourcesDoNotNameForbiddenA8Objects" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/SeeyonSourceScanTest.java:149`
- `EP-c22830e28f` — "migrationsDoNotNameForbiddenA8Objects" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/SeeyonSourceScanTest.java:155`
- `EP-dc5c6dcbc8` — "injectedReferencesAreFound" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/SeeyonSourceScanTest.java:210`
- `EP-fa39105b34` — "noForeignDataDdl" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/SeeyonSourceScanTest.java:240`

### backend/src/test/java/com/forcitis/one/platform/integration/guard/SourceReferenceAllocationGuardTest.java（5 条）

- `EP-0883ecc5d1` — "allocationElsewhereIsCaught" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/SourceReferenceAllocationGuardTest.java:77`
- `EP-3c08e603cc` — "onlySourceReferencesUsesAllocateOwnId" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/SourceReferenceAllocationGuardTest.java:38`
- `EP-c547273fb2` — "sourceReferencesPasses" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/SourceReferenceAllocationGuardTest.java:55`
- `EP-d6118d7bd4` — "onlyTheOrgSourceAdapterAllocates" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/SourceReferenceAllocationGuardTest.java:63`
- `EP-e338ce6773` — "anotherUserOfAllocateOwnIdIsCaught" — `backend/src/test/java/com/forcitis/one/platform/integration/guard/SourceReferenceAllocationGuardTest.java:46`

