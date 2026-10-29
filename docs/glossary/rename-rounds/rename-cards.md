# 第三轮用语确认改名卡

用户 2026-10-15 已定新名；下列旧名仍在代码或界面中使用。负责会话改名合入后告诉 one-glossary，由其删除 `backend/glossary/proposed/round3-pending-renames.yaml` 中对应条目（方言禁用随之生效）。

| 编号 | 旧名 | 新名 | 在用位置 |
|---|---|---|---|
| R3-NU-06 | 工时单据类型代码 / Ts | 并入 document_type_code | DocumentTypeCodeTest 按 snake 读取 platform-numbering.yaml，条目暂留，测试改为读 document_type_code 取值后删除 |
| R3-NU-17 | 报价单据类型代码 / Qt | 并入 document_type_code | DocumentTypeCodeTest 按 snake 读取 platform-numbering.yaml，条目暂留，测试改为读 document_type_code 取值后删除 |
| R3-NU-18 | 记账凭证单据类型代码 / Jv | 并入 document_type_code | DocumentTypeCodeTest 按 snake 读取 platform-numbering.yaml，条目暂留，测试改为读 document_type_code 取值后删除 |
| R3-AP-04 | 办理 / Act | 办理 / Handle（handle） | 代码仍用 Act（claude/one-1a-par-f1-acceptance） |
| R3-AP-05 | 已分配 / Assigned | 任务已分配 / TaskAssigned（task_assigned） | 代码仍用 Assigned（claude/one-1a-next-3-r1b-p2） |
| R3-AP-07 | 已完成 / Completed | 审批已完成 / ApprovalCompleted（approval_completed） | 代码仍用 Completed（main） |
| R3-AP-10 | 已升级 / Escalated | 已上推 / ReroutedToSuperior（rerouted_to_superior） | 代码仍用 Escalated（claude/one-1a-next-3-r1b-p2） |
| R3-AP-11 | 兜底 / Fallback | 转流程管理员 / AdministratorFallback（administrator_fallback） | 代码仍用 Fallback（claude/one-1a-next-3-r1b-p2） |
| R3-AP-12 | 表单 / Form | 审批表单字段 / ApprovalField（approval_field） | 代码仍用 Form（claude/one-1a-next-3-r1b-p2、claude/one-1a-par-f1-acceptance、claude/one-1a-par-k3a） |
| R3-AP-15 | 轮次 / Round | 审批轮次 / ApprovalRound（approval_round） | 代码仍用 Round（claude/one-1a-next-3-r1b-p2、claude/one-1a-par-k3a） |
| R3-AP-16 | 行 / Row | 行级任务 / LineTask（line_task） | 界面文字仍用「行」；代码仍用 Row（claude/one-1a-next-3-r1b-p2、claude/one-1a-par-f1-acceptance、claude/one-1a-par-k3a） |
| R3-AP-21 | 审批时限 / Deadline | 审批时限 / ApprovalTimeLimit（approval_time_limit） | 代码仍用 Deadline（claude/one-1a-par-k3a） |
| R3-AP-22 | 效力 / Effect | 办理效力 / TaskEffect（task_effect） | 代码仍用 Effect（claude/one-1a-par-f1-acceptance、claude/one-1a-par-k3a） |
| R3-AP-25 | 释放 / ReleaseOfTask | 取消认领 / Unclaim（unclaim） | 界面文字仍用「释放」 |
| R3-AP-41 | 业务摘要 / BusinessSummary | 审批摘要 / ApprovalSummary（approval_summary） | 界面文字仍用「业务摘要」 |
| R3-AP-42 | 代理 / ProxyHandling | 代理办理 / ProxyHandling（ui_proxy_handling） | 界面文字仍用「代理」 |
| R3-AP-53 | 参与人 / TaskParticipant | 任务参与人 / TaskParticipant（task_participant） | 界面文字仍用「参与人」 |
| R3-AP-57 | 决定 / Decision | 审批决定 / TaskDecision（task_decision） | 代码仍用 Decision（main） |
| R3-AP-60 | 已出结果 / Resulted | 任务已办理 / TaskHandled（task_handled） | 代码仍用 Resulted（main） |
| R3-AP-65 | 已跳过 / Skipped | 通知已跳过 / NotificationSkipped（notification_skipped） | 代码仍用 Skipped（main） |
| R3-AP-68 | 项目经理审批 / ProjectManagerReview | 项目经理审批 / ProjectManagerApproval（project_manager_approval） | 代码仍用 ProjectManagerReview（main） |
| R3-AP-69 | 区域负责人审批 / RegionalHeadReview | 部门负责人审批 / OrgUnitHeadApproval（org_unit_head_approval；D5-06 取代原定的 RegionalHeadApproval，随 R-1b P4） | 代码仍用 RegionalHeadReview（main） |
| R3-AP-70 | 一级汇报人审批 / SupervisorReview | 直属上级审批 / SupervisorApproval（supervisor_approval） | 代码仍用 SupervisorReview（main） |
| R3-AP-74 | 撤回截止点 / Cutoff | 撤回截止点 / WithdrawalCutoff（withdrawal_cutoff） | 代码仍用 Cutoff（main） |
| R3-ID-04 | 管理模式 / AdministratorMode | 管理员模式 / AdministratorMode（administrator_mode） | 界面文字仍用「管理模式」 |
| R3-ID-10 | 授权 / Authorization | 授予权限 / GrantPermission（grant_permission） | 界面文字仍用「授权」；代码仍用 Authorization（claude/one-1a-next-3-r1b-p2、claude/one-1a-par-f1-acceptance） |
| R3-ID-11 | 停用 / AccountDisabled | 账号停用 / AccountDisabled（account_disabled） | 界面文字仍用「停用」 |
| R3-ID-22 | 敏感角色持有人 / SensitiveRoleHolder | 敏感角色复核 / SensitiveRoleReview（permission_adjustment_kind__sensitive_role_review） | 代码仍用 SensitiveRoleHolder（claude/one-1a-par-f1-acceptance） |
| R3-IN-07 | 大小 / Size | 字节数 / ByteCount（byte_count） | 代码仍用 Size（claude/one-1a-next-3-r1b-p2、claude/one-1a-par-f1-acceptance、claude/one-1a-par-k3a） |
| R3-IN-08 | 保留 / Keep | 数据保留期限 / DataRetentionPeriod（data_retention_period） | 代码仍用 Keep（claude/one-1a-par-f1-acceptance） |
| R3-IN-36 | 一次写入多次读取 / Worm | 一次写入多次读取 / WriteOnceReadMany（write_once_read_many） | 代码仍用 Worm（main） |
| R3-NU-08 | 凭证号规则 / VoucherNumberingRule | 记账凭证编号规则 / JournalVoucherNumberingRule（journal_voucher_numbering_rule） | 代码仍用 VoucherNumberingRule（main） |
| R3-NU-09 | 规则版本 / RuleVersion | 编号规则版本 / NumberingRuleVersion（numbering_rule_version） | 代码仍用 RuleVersion（main） |
| R3-TS-04 | OH 工时 / OverheadHours | 间接工时 / IndirectHours（indirect_hours） | 代码仍用 OverheadHours（main） |
| R3-WF-06 | 企业邮箱 / Email | 企业邮箱 / EmailAddress（workforce__email_address） | 代码仍用 Email（claude/one-1a-par-f1-acceptance） |
| R3-AP-09 | 部门 / Department | 并入 org_unit | 代码仍用 Department（claude/one-1a-next-3-r1b-p2、claude/one-1a-par-f1-acceptance） |
| R3-AP-32 | 办理人 / TaskHandler | 并入 actor | 界面文字仍用「办理人」 |
| R3-AP-61 | 进行中 / Running | 并入 ui_approval_running | 代码仍用 Running（main） |
| R3-NU-06 | 工时单据类型代码 / Ts | 并入 document_type_code | 代码仍用 Ts（claude/one-design-sync-staging） |
| R3-NU-17 | 报价单据类型代码 / Qt | 并入 document_type_code | 代码仍用 Qt（main） |
| R3-NU-18 | 记账凭证单据类型代码 / Jv | 并入 document_type_code | 代码仍用 Jv（main） |
| R3-AP-26 | 会计凭证 / JournalVoucher | 记账凭证 / JournalVoucher（journal_voucher） | 界面文字仍用「会计凭证」 |
