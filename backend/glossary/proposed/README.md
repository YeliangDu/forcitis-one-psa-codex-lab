# 提议术语清单（第三轮用语确认用）

<!-- 本文件由 backend/glossary/build_proposed_index.py 从同目录 *.yaml 生成，不要手改；改 yaml 后运行 python3 docs/modernization/build_all.py 并提交结果。 -->

`terms` 只放业务术语，纯技术词（动词、框架词、值对象方法名等）放同文件的 `common_words`。每条必填使用范围 `scope`（domain 领域 / kernel 共享内核 / acl 防腐层）与 `bc`（取 `docs/modernization/glossary/bcs.yaml` 中的 id）：kernel 另填 `used_by`（使用它的 bc），acl 另填 `acl_class`（过渡期 / 长期），指标另填 `formula`、`grain`、`as_of`，下游 bc 自用词可填 `upstream_term`；同一 bc 内中英文名唯一，跨 bc 同名在 `homonyms` 互列；`kind` 只取 DDD 分类（aggregate 聚合 / entity 实体 / value_object 值对象 / domain_event 领域事件 / command 命令 / business_rule 业务规则 / read_model 读模型 / metric 指标 / role 角色 / state 状态，错误码另列 error_code），事件名为聚合名加过去式业务动词、命令用祈使动词，Create / Update / Delete / Data / Info / Manager 等技术泛词不作领域名（规则与校验见 `glossary/term_rules.py`）。本表按文件列出除错误码以外的全部提议术语；「是否疑似方言」由条目中类型为方言或待定的别名与禁用写法生成，空表示未登记问题；「备注」为条目 note 原文。

修改任何一个 yaml 后运行 `./mvnw -B verify`（GlossaryTest 会校验字段、重名和未登记词）与 `python3 docs/modernization/build_all.py`（重新生成本文件）。

## organization.workforce（`architecture-acl.yaml`）

ADR-0039（防腐层边界）引入的术语（proposed，待业务评审，ADR-0018）。登记会话：one-architecture，2026-10-15。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `org_change_set` | 组织变更集 | OrgChangeSet | 领域 · organization.workforce | 集成层翻译成新系统用语后交给组织人事的一批组织单元、员工与任职变更；以同步运行 ID 作幂等键，整批在一个事务内应用 |  |  |
| `apply_org_change_set` | 应用组织变更 | ApplyOrgChangeSet | 领域 · organization.workforce | 组织人事 api 中唯一的同步入口：应用一份组织变更集，管理员离职挂为待放行离职，其余变更照常生效；同一变更集重复调用返回上次结果 |  |  |
| `separation_hold` | 待放行离职 | SeparationHold | 领域 · organization.workforce | 会使可用超级管理员少于 2 名、或涉及超级管理员与安全审计员持有人的离职，被单独挂起等待另一名持有人放行的记录（ADR-0027 第 1 项第 7 条、ADR-0028） |  | 权限码 organization.workforce:separation-hold:approve 已用此词（ADR-0028 第 2 条） |

## `code-aggregates-events.yaml`

涉及模块：`cost.timesheet`、`organization.attendance`、`organization.workforce`、`platform.approval`、`platform.identity`、`platform.integration`、`platform.numbering`。

B2 登记：代码里的聚合根与领域事件各自要有词条（G40、G41）；

登记会话 one-glossary，2026-10-15，中文名待用户确认。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `legal_entity_attribution` | 法人归属 | LegalEntityAttribution | 领域 · organization.workforce | 一个部门或一名员工在某段时间内归属于哪个法人主体的记录；各段期间互不重叠，历史只能关闭、不能改写，新的归属只能从最近一段的开始日之后起算 |  | 来源：聚合源码说明与事件目录 |
| `registered_employee` | 登记员工 | RegisteredEmployee | 领域 · organization.workforce | 员工首次登录时，在组织来源里找到、但下一次同步尚未把他带入组织快照的人；登记后他即可使用系统 |  | 中文名待确认：与词表「员工」是同一个人的两个阶段，是否应并入「员工」聚合由用户裁定 |
| `approval_request` | 审批请求 | ApprovalRequest | 领域 · platform.approval | 对同一种审批流程中的同一张单据所发起的审批，一张单据在一种流程下只有一个；每一轮审批有自己的流程实例，本轮的审批任务归它管理，只能通过它修改 |  | 来源：聚合源码说明；与词表「审批实例」「审批轮次」的边界待确认 |
| `source_reference_refusal` | 来源对照被拒登记 | SourceReferenceRefusal | ACL（过渡期） · platform.integration | 有人试图人工登记只能由同步或导入写入的来源对照而被拒绝的一次记录；不存储任何对照，事件本身就是记录，使审计能看到是谁试过 |  | 中文名待确认；聚合承载的只是一次被拒事实 |
| `timesheet_approved` | 工时单已批准 | TimesheetApproved | 领域 · cost.timesheet | 工时单审批通过，工时成立；过程里程碑，以工作日期记业务日 |  | 形成成本事实的是后续过账事件，本事件是否使用待阶段二定 |
| `timesheet_cancelled` | 工时单审批已撤销 | TimesheetCancelled | 领域 · cost.timesheet | 填报人撤销审批，工时单回到草稿；不记为驳回；以工作日期记业务日 |  | 类名 Cancelled 与「撤销审批」略不符，类名是否改为 ApprovalWithdrawn 之类待确认 |
| `timesheet_rejected` | 工时单已驳回 | TimesheetRejected | 领域 · cost.timesheet | 工时单被驳回，工时不成立；以工作日期记业务日 |  | 来源：事件目录 |
| `timesheet_returned` | 工时单已退回 | TimesheetReturned | 领域 · cost.timesheet | 审批人退回或填报人撤回，工时单回到可修改状态，下一轮重新提交；以工作日期记业务日 |  | 来源：事件目录 |
| `timesheet_submitted` | 工时单已提交审批 | TimesheetSubmitted | 领域 · cost.timesheet | 填报人把工时单提交审批，工时单进入新一轮审批；以工作日期记业务日，提交日另记；只陈述事实，不过账 |  | 来源：事件目录 |
| `work_calendar_year_maintained` | 工作日历年度已维护 | WorkCalendarYearMaintained | 领域 · organization.attendance | 某一年的工作日历被保存：有日期被加入、去掉或改了日类型，或该年只是被重新核对；记下变动的日期 |  | 来源：事件目录与事件类说明 |
| `employee_registered` | 员工已登记 | EmployeeRegistered | 领域 · organization.workforce | 员工第一次登录时，被登记进组织快照；此后下一次同步会带入完整信息 |  | 聚合 Employee，中文名与「员工」一致 |
| `legal_entity_activated` | 法人主体已启用 | LegalEntityActivated | 领域 · organization.workforce | 一个法人主体被启用，可以开始作为法人使用 |  | 来源：事件目录 |
| `legal_entity_attributed` | 法人归属已确立 | LegalEntityAttributed | 领域 · organization.workforce | 部门或员工从某日起归属于某个法人主体 |  | 来源：事件目录 |
| `legal_entity_attribution_corrected` | 法人归属结束日已更正 | LegalEntityAttributionCorrected | 领域 · organization.workforce | 已关闭的法人归属期间的结束日被更正 |  | 来源：事件目录 |
| `legal_entity_attribution_ended` | 法人归属已结束 | LegalEntityAttributionEnded | 领域 · organization.workforce | 部门或员工对某个法人主体的归属在某日结束 |  | 来源：事件目录 |
| `legal_entity_code_corrected` | 法人主体代码已更正 | LegalEntityCodeCorrected | 领域 · organization.workforce | 法人主体的代码被更正；仅在尚未发过号时允许 |  | 来源：事件目录 |
| `legal_entity_deactivated` | 法人主体已停用 | LegalEntityDeactivated | 领域 · organization.workforce | 一个法人主体被停用，不再作为法人使用 |  | 来源：事件目录 |
| `legal_entity_registered` | 法人主体已登记 | LegalEntityRegistered | 领域 · organization.workforce | 一个新的法人主体被登记进组织人事 |  | 事件目录称「法人新增」，按不用泛词的规则改为「已登记」 |
| `legal_entity_renamed` | 法人主体已更名 | LegalEntityRenamed | 领域 · organization.workforce | 法人主体的名称被改变 |  | 来源：事件目录 |
| `org_snapshot_changed` | 组织快照已变化 | OrgSnapshotChanged | ACL（过渡期） · organization.workforce | 一次组织同步使组织快照发生了变化；离职人员的会话据此被收回；业务日为快照截止日，生效日同截止日 |  | 事件目录所属聚合为 SyncRun，故 scope 与「同步记录」一致取 acl 过渡期；若归入「组织快照」聚合则应为 domain，待确认 |
| `sync_run_failed` | 同步记录已失败 | SyncRunFailed | 领域 · organization.workforce | 一次组织同步运行失败 |  | 来源：事件目录 |
| `sync_run_held` | 同步记录已被暂停 | SyncRunHeld | 领域 · organization.workforce | 一次组织同步因缩水保护而被中止并保持暂停，等待有权的人放行 |  | 来源：事件目录 |
| `sync_run_hold_released` | 同步记录一类暂停已解除 | SyncRunHoldReleased | 领域 · organization.workforce | 被暂停的同步，其中一类暂停被有权的人解除；另一类暂停仍在时，运行保持暂停 |  | 来源：事件目录与事件类说明 |
| `sync_run_released` | 同步记录已放行 | SyncRunReleased | 领域 · organization.workforce | 被中止的同步已由管理员放行，或被后续成功的同步取代；记下接替的运行与管理员给出的原因 |  | 来源：事件目录与事件类说明 |
| `sync_run_succeeded` | 同步记录已成功 | SyncRunSucceeded | 领域 · organization.workforce | 一次组织同步运行成功 |  | 来源：事件目录；该事件按设计不进审计 |
| `approval_round_started` | 审批轮次已开始 | ApprovalRoundStarted | 领域 · platform.approval | 审批开始新一轮：首次发起，或退回、驳回、撤销之后重新发起；过程里程碑 |  | 聚合 ApprovalRequest；词表已有实体「审批轮次」 |
| `approval_task_escalated` | 任务已升级 | ApprovalTaskEscalated | 领域 · platform.approval | 任务超时仍未处理，已升级给审批人的上级；取不到上级则交流程管理员 |  | 聚合 ApprovalRequest |
| `approval_task_overdue` | 任务已超时 | ApprovalTaskOverdue | 领域 · platform.approval | 任务超过审批时限仍未处理；只提醒，不自动通过，计入时效统计 |  | 类名 Overdue 是形容词而非过去式，是否改为 ApprovalTaskTimedOut 待确认 |
| `document_submitted_for_approval` | 单据已请求审批 | DocumentSubmittedForApproval | 领域 · platform.approval | 单据的所有者请审批模块发起一轮审批，并带齐发起所需的全部信息；同一单据同一轮重复收到只发起一次 |  | 类型归审批模块，由申请审批的模块的聚合（现为工时单）发布；中文名与「工时单已提交审批」的区别见事件目录 |
| `task_claimed` | 任务已认领 | TaskClaimed | 领域 · platform.approval | 任务被候选人之一认领，其他候选人的待办随之改为已处理 |  | 聚合 ApprovalRequest |
| `task_closed` | 任务已关闭 | TaskClosed | 领域 · platform.approval | 本轮在任务办结前结束（驳回、退回、撤回、撤销），任务关闭，未办理的候选人失去办理权 |  | 聚合 ApprovalRequest |
| `task_reassigned` | 任务已改派 | TaskReassigned | 领域 · platform.approval | 流程管理员把任务改派给另一个人，填写原因并留痕 |  | 聚合 ApprovalRequest |
| `task_released` | 任务已退回候选组 | TaskReleased | 领域 · platform.approval | 任务的处理人把任务退回候选组，候选人可以再次认领 |  | 聚合 ApprovalRequest |
| `task_resulted` | 任务已出结果 | TaskResulted | 领域 · platform.approval | 任务被办理（通过、驳回、退回），或系统自动通过；加签人的驳回只记意见，不结束本轮 |  | 类名与已确认用语冲突：第三轮 R3-AP-60 已定为「任务已办理 / TaskHandled」，该词条已在词表；代码类名应改为 TaskHandled，改名后本条作废 |
| `task_transferred` | 任务已转交 | TaskTransferred | 领域 · platform.approval | 任务的当前处理人把任务转交给他人 |  | 聚合 ApprovalRequest |
| `account_enabled` | 用户账号已启用 | AccountEnabled | 领域 · platform.identity | 一个用户账号被启用，可以登录使用 |  | 聚合 UserAccount |
| `account_opened` | 用户账号已开立 | AccountOpened | 领域 · platform.identity | 为一个人开立用户账号，包含开立时授予的角色 |  | 聚合 UserAccount |
| `account_roles_replaced` | 用户账号角色已替换 | AccountRolesReplaced | 领域 · platform.identity | 一个用户账号的角色被整体替换为新的一组 |  | 聚合 UserAccount |
| `account_separated` | 用户账号已因离职收回角色 | AccountSeparated | 领域 · platform.identity | 账号的本人离职，账号收回全部角色；挂在账号上的东西（如应急账号）随这一事实处理 |  | 聚合 UserAccount |
| `authorization_confirmed` | 授权申请已确认 | AuthorizationConfirmed | 领域 · platform.identity | 敏感授权申请被另一个人确认 |  | 聚合 AuthorizationRequest |
| `authorization_opened` | 授权申请已开立 | AuthorizationOpened | 领域 · platform.identity | 一个敏感授权申请被提出，等待另一个人确认 |  | 聚合 AuthorizationRequest |
| `authorization_rejected` | 授权申请已拒绝 | AuthorizationRejected | 领域 · platform.identity | 敏感授权申请被拒绝，或被申请人撤回 |  | 聚合 AuthorizationRequest；拒绝与撤回是否分设两个事件待确认 |
| `emergency_admin_credentials_reset` | 应急管理员凭据已重置 | EmergencyAdminCredentialsReset | 领域 · platform.identity | 应急管理员的登录凭据被重置 |  | 聚合 EmergencyAdmin |
| `emergency_admin_disabled` | 应急管理员已停用 | EmergencyAdminDisabled | 领域 · platform.identity | 应急管理员因离职或运维命令而被停用 |  | 聚合 EmergencyAdmin |
| `emergency_admin_enabled` | 应急管理员已启用 | EmergencyAdminEnabled | 领域 · platform.identity | 应急管理员被启用，可作为应急入口使用 |  | 聚合 EmergencyAdmin |
| `emergency_admin_registered` | 应急管理员已登记 | EmergencyAdminRegistered | 领域 · platform.identity | 一名应急管理员被登记 |  | 聚合 EmergencyAdmin |
| `role_created` | 角色已设立 | RoleCreated | 领域 · platform.identity | 新设立一个角色 |  | 事件目录称「角色新增」，避开泛词改为「设立」；类名 Created 是否改为 Established 待确认 |
| `role_deleted` | 角色已废除 | RoleDeleted | 领域 · platform.identity | 一个角色被废除，不再可用 |  | 事件目录称「角色删除」；「删除」属技术泛词，业务说法（废除/撤销）待用户确认 |
| `role_disabled` | 角色已停用 | RoleDisabled | 领域 · platform.identity | 一个角色被停用 |  | 聚合 Role |
| `role_enabled` | 角色已启用 | RoleEnabled | 领域 · platform.identity | 一个角色被启用 |  | 聚合 Role |
| `role_revised` | 角色已修订 | RoleRevised | 领域 · platform.identity | 角色的名称、数据范围或权限被修改 |  | 聚合 Role |
| `source_reference_confirmed` | 来源对照已确认 | SourceReferenceConfirmed | 领域 · platform.integration | 另一个人确认了登记的来源对照，从生效日起用于出站 |  | 聚合 SourceReference |
| `source_reference_registered` | 来源对照已登记 | SourceReferenceRegistered | 领域 · platform.integration | 有人登记了一条来源对照（核算组织编码），等待他人确认 |  | 聚合 SourceReference |
| `source_reference_registration_refused` | 来源对照登记已被拒绝 | SourceReferenceRegistrationRefused | 领域 · platform.integration | 有人试图人工登记只能由同步或导入写入的来源对照，被拒绝，未存储任何对照 |  | 聚合 SourceReferenceRefusal；类名 RegistrationRefused 与聚合名有重复 |
| `source_reference_rejected` | 来源对照已拒绝 | SourceReferenceRejected | 领域 · platform.integration | 另一个人拒绝了登记的来源对照；已生效的对照不变 |  | 聚合 SourceReference |
| `source_reference_superseded` | 来源对照已被取代 | SourceReferenceSuperseded | 领域 · platform.integration | 生效中的来源对照，在后继对照的生效日结束 |  | 聚合 SourceReference |
| `numbering_rule_confirmation_given` | 编号规则已获一次确认 | NumberingRuleConfirmationGiven | 领域 · platform.numbering | 编号规则的模板版本得到业务或财务其中一方的一次确认 |  | 类名 ConfirmationGiven 不是「聚合+过去式动词」形式，是否改为 NumberingRuleConfirmationRecorded 待确认 |
| `numbering_rule_confirmed` | 编号规则已确认 | NumberingRuleConfirmed | 领域 · platform.numbering | 编号规则的模板版本已确认齐全，从此开始发号 |  | 聚合 NumberingRule |
| `numbering_rule_reconfigured` | 编号规则已重新设置 | NumberingRuleReconfigured | 领域 · platform.numbering | 编号规则保存了新的模板版本，等待他人确认 |  | 聚合 NumberingRule |

## `contract-evolution.yaml`

契约演进与部署排空用语（proposed，ADR-0035，待用语确认，ADR-0018）。

无错误码以外的术语。

## cost.timesheet（`cost-timesheet.yaml`）

cost.timesheet 提议术语（proposed，待第三轮用语确认，ADR-0018）。

无错误码以外的术语。

另有错误码 12 个（kind 为 `error_code`）、技术词 23 个（`common_words`），不在表中。

## `data-migration.yaml`

涉及模块：`accounting.period`、`operations.facts`、`platform.integration`、`platform.migration`。

迁入的其余用语（拒收、核对、历史期间、迁入标记等）随 ADR-0036（另行 PR）登记。

通用词另增 `batch`、`count`（批量命令后缀与计数端点，NAMING_STANDARD §2.6 A-3、A-6）。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `migration_batch` | 迁入批次 | MigrationBatch | ACL（过渡期） · platform.integration | 一次历史数据迁入运行；登记来源系统、对象类型、范围、状态、行数与来源 ID 摘要；批次挂在来源对照与原编号对照上，业务表不带批次列 | 「导入批次」已禁用；「import_batch」已禁用；「ImportBatch」已禁用 | 用户 2026-10-15 定批次术语唯一写法；platform.numbering 的 import_batch 已在 R-1b P3a（#139）改为 migration_batch_id，同一提交加入 forbidden 并删去 platform-integration.yaml 中的重复条目 |
| `migration_reject` | 迁入拒收 | MigrationReject | ACL（过渡期） · platform.integration | 迁入时被拒绝的一条来源行，带批次、来源对象、来源 ID 与原因代码；处置经应用内带权限的操作录入，批次提交前须逐条处置（ADR-0036 第 7 条） |  |  |
| `migration_reject_reason__unmappable_value` | 取值无法映射 | UnmappableValue | ACL（过渡期） · platform.integration | 迁入拒收原因代码：来源取值在映射表中没有对应的自有取值（ADR-0036 第 7 条） |  |  |
| `migration_reject_reason__required_missing` | 必填缺失 | RequiredMissing | ACL（过渡期） · platform.integration | 迁入拒收原因代码：目标必填项在来源中为空（ADR-0036 第 7 条） |  |  |
| `migration_reject_reason__number_conflict` | 编号冲突 | NumberConflict | ACL（过渡期） · platform.integration | 迁入拒收原因代码：原编号与已有编号在（单据类型, 编号）上冲突（ADR-0036 第 7 条） |  |  |
| `migration_reject_reason__original_missing` | 原行缺失 | OriginalMissing | ACL（过渡期） · platform.integration | 迁入拒收原因代码：冲销行的原行不在本批次也不在目标库中（ADR-0036 第 7 条） |  |  |
| `migration_reject_reason__legal_entity_out_of_scope` | 法人不在范围 | LegalEntityOutOfScope | ACL（过渡期） · platform.integration | 迁入拒收原因代码：来源行的法人不在新系统的法人范围内（ADR-0036 第 7 条） |  |  |
| `migration_reject_reason__trial_commit_failed` | 试提交失败 | TrialCommitFailed | ACL（过渡期） · platform.integration | 迁入拒收原因代码：试提交中领域校验或数据库约束不通过（ADR-0036 第 7 条） |  |  |
| `migration_reject_disposition__rerun_after_fix` | 修正后重跑 | RerunAfterFix | ACL（过渡期） · platform.integration | 迁入拒收处置取值：修正映射或来源后由新的暂存重跑（ADR-0036 第 7 条） |  |  |
| `migration_reject_disposition__excluded` | 确认不迁 | Excluded | ACL（过渡期） · platform.integration | 迁入拒收处置取值：确认该行不迁入；权责事实须财务负责人确认，其他由业务管理员确认（ADR-0036 第 7 条） |  |  |
| `migration_tie_out` | 迁入核对 | MigrationTieOut | ACL（过渡期） · platform.integration | 一个迁入批次按期间 × 法人 × 对象类型比对来源侧聚合与暂存的行数、金额合计、来源 ID 摘要与按来源单号的行数金额的结果；零差异或经财务签字后批次才能提交（ADR-0036 第 8 条） |  | 不用 Reconciliation：已批准术语「经营勾稽」Reconciliation 指跨口径对账 |
| `period_status__historical` | 历史期间 | Historical | 领域 · accounting.period | 期间状态的一个取值，终态：FORCITIS one 首个业务期间之前的 (期间, 法人)，永不接受业务过账，只接受迁入过账通道；不逐期关账、不发关账事件（ADR-0036 第 6 条） |  | 会计类用语，待财务确认（ADR-0018；ADR-0036 U-3） |
| `migration_period_reservation` | 迁入占用 | MigrationPeriodReservation | ACL（过渡期） · accounting.period | 迁入批次对 (期间, 法人) 的占用登记，由 accounting.period 自持；有未解除占用的期间不得关账，批次转为提交中时加锁确认期间状态（ADR-0036 第 6 条） |  |  |
| `migration_marker` | 迁入标记 | MigrationMarker | 领域 · operations.facts | 事实行上区分原生与迁入的标记；只在事实行，单据表不带；accounting.ledger 不为迁入标记的事实生成分录（ADR-0036 第 5、6 条） |  | 列名在实现 PR 中按 NAMING_STANDARD 定 |
| `migration_posting_channel` | 迁入过账通道 | MigrationPostingChannel | ACL（过渡期） · operations.facts | operations.facts 中只供迁入用例使用的过账入口：期间可为历史期间或开放期间，不发布事件（ADR-0036 第 6 条） |  |  |
| `migration_staging` | 迁入暂存 | MigrationStaging | ACL（过渡期） · platform.integration | 来源行转换为目标形状后存放的集成层暂存表（Flyway 按对象类型建固定表，带批次列、来源键、摘要与核对所需来源取值）；试提交与核对都针对它，核对通过后才提交目标库（ADR-0036 第 7 条） |  | 暂存只有一份，不另存来源快照 |
| `trial_commit` | 试提交 | TrialCommit | ACL（过渡期） · platform.migration | 用与正式提交同一套代码把整批写入目标库后回滚事务，以跑全部领域校验与数据库约束（ADR-0036 第 7 条） |  |  |
| `migration_batch_status__staging` | 暂存中 | Staging | ACL（过渡期） · platform.integration | 迁入批次状态取值：正在抽取、转换与暂存，可试提交（ADR-0036 第 3 条） |  |  |
| `migration_batch_status__awaiting_tie_out` | 待核对 | AwaitingTieOut | ACL（过渡期） · platform.integration | 迁入批次状态取值：暂存与试提交完成，等待核对通过或签字（ADR-0036 第 3 条） |  |  |
| `migration_batch_status__committing` | 提交中 | Committing | ACL（过渡期） · platform.integration | 迁入批次状态取值：核对已通过，正在分块提交目标库（ADR-0036 第 3 条） |  |  |
| `migration_batch_status__effective` | 已生效 | Effective | 领域 · platform.integration | 迁入批次状态取值：提交后复核一致；可转为已冲销（ADR-0036 第 3 条） |  |  |
| `migration_batch_status__reversed` | 已冲销 | Reversed | ACL（过渡期） · platform.integration | 迁入批次状态取值：提交阶段失败或生效后发现错误，事实行已整批冲销，终态；重跑为新批次（ADR-0036 第 3 条） |  |  |
| `migration_batch_status__abandoned` | 已放弃 | Abandoned | ACL（过渡期） · platform.integration | 迁入批次状态取值：提交前放弃，目标库未受影响，终态（ADR-0036 第 3 条） |  |  |
| `source_reference_status` | 对照状态 | SourceReferenceStatus | ACL（过渡期） · platform.integration | 来源对照行的状态：当前 CURRENT / 已冲销 REVERSED；只有事实类对照可为已冲销，冲销与关闭旧对照同一事务（ADR-0036 第 3、9 条） |  | 与确认状态 confirmation_status 是两列；自然键只约束已确认且当前的行；取值「已冲销」REVERSED 与迁入批次状态的「已冲销」同词同义，不另登记 |
| `source_reference_status__current` | 当前 | Current | ACL（过渡期） · platform.integration | 对照状态取值：该对照仍指向有效的自有行（ADR-0036 第 3 条） |  |  |
| `source_row_digest` | 来源行摘要 | SourceRowDigest | ACL（过渡期） · platform.integration | 来源行规范化后的 SHA-256，存于来源对照与暂存行，用于重跑时判断同一来源行是否变化（ADR-0036 第 3、7 条） |  |  |

另有技术词 2 个（`common_words`），不在表中。

## `finance-decisions.yaml`

财务口径决策单（docs/modernization/r0/FINANCE_DECISIONS.md，2026-10-15）中出现、词表未收的会计与经营用语。

会计类提议术语须经财务口径确认（ADR-0018）；「产值收入」是方言，登记在正式用语「权责收入」下。

无错误码以外的术语。

## `fix-b.yaml`

涉及模块：`organization.workforce`、`platform.identity`、`platform.integration`、`platform.numbering`、`platform.web`。

Phase 1a 修复批 B 新增术语（proposed，待业务评审，ADR-0018）。

无错误码以外的术语。

另有错误码 47 个（kind 为 `error_code`）、技术词 49 个（`common_words`），不在表中。

## `fix-b1-identity.yaml`

涉及模块：`organization.workforce`、`platform.audit`、`platform.identity`、`platform.numbering`。

Phase 1a 修复批 B1（身份与授权）新增术语（proposed，待业务评审，ADR-0018）。ADR-0027 的八项与运维命令用到的词；每个新错误码一条 error__ 条目。

无错误码以外的术语。

另有错误码 28 个（kind 为 `error_code`）、技术词 18 个（`common_words`），不在表中。

## organization.attendance（`fix-b2-data.yaml`）

Phase 1a 修复批 B2（数据架构）新增术语（proposed，待业务评审，ADR-0018）。

无错误码以外的术语。

另有错误码 1 个（kind 为 `error_code`），不在表中。

## organization.workforce（`fix-b2b-events.yaml`）

Phase 1a 修复批 B2b（事件业务日期、指标）新增术语（proposed，待业务评审，ADR-0018）。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `snapshot_date` | 快照日 | SnapshotDate | ACL（过渡期） · organization.workforce | 读取组织源快照的那个业务日，即同步运行开始当天（Asia/Shanghai）。它是 OrgSnapshotChanged 信封的 businessDate，也是同一次同步写入的版本行的生效日 |  |  |

## `fix-c.yaml`

涉及模块：`organization.workforce`、`platform.integration`。

前端修复批新增的界面用语（proposed）：状态、列名与提示语中的业务词，守护要求它们作为完整的词登记，不得由单字拼成。

状态、列名与提示语中的业务词。前端用语守护要求单字只作连接词，状态词（已通过、待认领等）必须作为完整的词登记。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `ui_released_state` | 已放行 | ReleasedState | ACL（过渡期） · organization.workforce | 被中止的同步已由管理员放行并生效 |  | 展示用语；同步记录状态 RELEASED |
| `ui_external_system` | 外部系统 | ExternalSystem | ACL（长期） · platform.integration | 本系统之外、通过防腐层接入的系统，如企业微信、致远、组织来源 |  | 错误提示用语 |

## `fix-time-zone.yaml`

Phase 1a 时间与时区修复新增用语（proposed，待业务评审，ADR-0018）。纯技术词放 common_words。

无错误码以外的术语。

另有技术词 2 个（`common_words`），不在表中。

## `frontend-ui.yaml`

涉及模块：`organization.workforce`、`platform.integration`。

前端界面用语中尚不在正式词表里的业务术语（proposed，待第三轮用语确认，ADR-0018）。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `trigger_mode` | 触发方式 | TriggerMode | ACL（过渡期） · organization.workforce | 一次同步是定时触发还是手动触发 |  | 用户 2026-10-14 指定提议；同步记录列名 |
| `held` | 被中止 | Held | ACL（过渡期） · platform.integration | 同步记录的状态：被缩水保护中止，等待管理员放行；与「被中止的同步」指同一状态 |  | 用户 2026-10-14 指定提议；同步记录状态值，第三轮用语确认时与 workforce__held_run 合并 |
| `release` | 放行 | Release | ACL（过渡期） · platform.integration | 管理员核对同步差异后，让被中止的同步生效的动作 |  | 扫描前端界面文案发现；同步记录页按钮与结果用语 |
| `org_sync` | 同步 | OrgSync | ACL（过渡期） · platform.integration | 把组织来源的组织单元与员工变化应用到本系统的一次运行 |  | 扫描前端界面文案发现；与「同步记录」「同步差异」配合使用 |

## kernel（`kernel.yaml`）

`tail_adjustment`（尾调）、`rounding_residual`（舍入差）、`prorate`（按权重拆分）由用户 2026-10-15 XD-07「同意 (Recommended)」确认登记（`docs/modernization/CROSS_CUTTING_DECISIONS.md`）。`reversal_document`、`correction_document`、`document_nature`、`reversal_category`、`reversible_balance`、`derived_reversal`、`original_period`、`change_order_document`、`reverse_action`、`correct_action` 十条是 ADR-0031 第 8 项的提议，待用语确认（U-4）。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `system_actor` | 系统操作主体 | SystemActor | 共享内核 · kernel | 以 SYSTEM 身份发出事件的作业、监听器或运维入口；名字人为声明在 backend/architecture/system-actors.txt，事件信封 actorId 为该名字在固定命名空间下的 UUIDv5，同一名字恒定、不同于任何用户 ID，任何实例都可由目录反查名字（RULING-P2-C5） |  | RULING-P2-C5 已裁定：声明完整的静态目录，改名新增一行、旧行不删 |

另有技术词 38 个（`common_words`），不在表中。

## `list-paging.yaml`

K-3a 列表分页公共契约新增的纯技术词（proposed，NAMING_STANDARD A-3、A-4）。

无错误码以外的术语。

另有技术词 6 个（`common_words`），不在表中。

## organization.attendance（`organization_attendance.yaml`）

organization.attendance 提议术语（proposed，待业务评审，ADR-0018）。

无错误码以外的术语。

另有错误码 8 个（kind 为 `error_code`）、技术词 8 个（`common_words`），不在表中。

## `organization_workforce.yaml`

涉及模块：`organization.workforce`、`platform.integration`。

Phase 1a organization.workforce 新增术语（proposed，待业务评审，ADR-0018）

新词一律 status: proposed；通用技术词放 common_words，业务概念放 terms。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `employment_verdict` | 在职判定 | EmploymentVerdict | 领域 · organization.workforce | 组织快照对某员工是否在职的判定：在职 EMPLOYED、离职 SEPARATED、快照可用而未列出 NOT_LISTED、快照不可用或过期 SNAPSHOT_UNAVAILABLE（ADR-0033 第 3 项第 7 条，应急登录在职校验用） |  | R-1b P2-H2 |
| `login_lookup` | 登录查人 | LoginLookup | ACL（过渡期） · organization.workforce | 按外部登录标识（企业微信 userid 或外部登录名）在组织快照中找员工；随 DA-3 外部身份移到 identity 后删除 |  | R-1b P2-H2，两个按登录标识查人的方法合一以守住 api 方法上限 |
| `workforce__sync_diff` | 同步差异 | SyncDiff | ACL（过渡期） · platform.integration | 一次同步运行改动的对象与字段，以及发现的数据例外（供数据管理员处理） |  |  |
| `workforce__shrink_guard` | 缩水保护 | ShrinkGuard | ACL（过渡期） · organization.workforce | A8 返回的有效部门数或在职人数降幅超过 20 % 时中止同步，等管理员人工放行 |  |  |
| `workforce__held_run` | 被中止的同步 | HeldRun | ACL（过渡期） · organization.workforce | 被缩水保护中止、等待管理员放行的同步运行 |  |  |
| `workforce__isolated_row` | 被隔离的行 | IsolatedRow | ACL（过渡期） · organization.workforce | 缺部门或缺岗位的人员行：不写入、告警，不中断整批同步 |  |  |
| `workforce__broken_org_tree` | 断裂的组织树 | BrokenOrgTree | ACL（过渡期） · organization.workforce | A8 返回的部门缺少上级路径；整次同步中止，什么都不写 |  |  |
| `workforce__login_name` | 登录名 | LoginName | ACL（过渡期） · platform.integration | 员工在组织源的登录名（手机号）；没有即离职（RULE-363） |  |  |
| `workforce__staff_employee` | 人员 | StaffEmployee | ACL（过渡期） · organization.workforce | 一名员工连同其任职，是同步比较与写入的单位 |  |  |
| `workforce__relationship` | 关系 | Relationship | ACL（过渡期） · platform.integration | A8 中人员与组织单元之间的关系（部门负责人、区域应收负责人），类型 ID 为配置项 |  |  |
| `separation_observed_at` | 离职观察时间 | SeparationObservedAt | ACL（过渡期） · platform.integration | 组织同步第一次把员工判为离职、并写入快照的那一轮同步的提交时刻；只表示新系统这样判定了，不是人事生效日 | 「离职日期」已禁用；「separation_date」已禁用 | 单说离职日期有歧义，须写明预计离职日期或离职观察时间 |

另有错误码 10 个（kind 为 `error_code`）、技术词 86 个（`common_words`），不在表中。

## platform.approval（`platform-approval.yaml`）

platform.approval 提议术语（proposed，待业务评审，ADR-0018）。单词级术语：服务于代码标识、URL、事件键、错误码的命名守护。

无错误码以外的术语。

另有错误码 28 个（kind 为 `error_code`）、技术词 111 个（`common_words`），不在表中。

## platform.identity（`platform-identity.yaml`）

platform.identity 新增术语（proposed，待业务评审，ADR-0018）。登录、会话、票据、角色与数据范围用到的词。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `signing_key_code` | 签名密钥编码 | SigningKeyCode | ACL（长期） · platform.identity | 票据签发方签名密钥的编码（票据头部的 kid），一方一把；原列名 key_id 按 ADR-0033 影响面改为以 _code 结尾（R-1b P2） |  |  |
| `ticket` | 票据 | Ticket | ACL（长期） · platform.identity | 一次性登录凭据：致远 OA 票据，或外部系统的签名票据 |  |  |
| `ticket_issuer` | 票据签发方 | TicketIssuer | ACL（长期） · platform.identity | 被允许签发签名票据的外部系统（KM、组织门户、YD 门户、BI），每方一把密钥，默认关闭 |  |  |
| `external_identity` | 外部身份 | ExternalIdentity | ACL（长期） · platform.identity | 用户账号在某个身份提供方（企业微信、致远登录）中的标识与该账号的绑定，带绑定区间；登录只经此绑定解析用户 |  | ADR-0033（提议）登记，待用语确认 |
| `provider_kind` | 身份提供方类别 | ProviderKind | ACL（长期） · platform.identity | 外部身份所属的身份提供方类别，如企业微信用户 ID、致远登录名 |  | ADR-0033（提议）登记，待用语确认 |
| `external_subject` | 外部主体标识 | ExternalSubject | ACL（长期） · platform.identity | 身份提供方给出的用户标识文本（企业微信 userid、致远登录名），只存于外部身份绑定 |  | ADR-0033（提议）登记，待用语确认 |

另有错误码 3 个（kind 为 `error_code`）、技术词 107 个（`common_words`），不在表中。

## platform.integration（`platform-integration.yaml`）

platform.integration 提议术语（proposed，待业务评审，ADR-0018）。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `outbound_delivery` | 外发投递 | OutboundDelivery | ACL（长期） · platform.integration | 一条待发往外部系统（企业微信）的消息及其发送结果；事务提交后由投递作业发送，失败退避重试，次数用尽进死信 |  |  |
| `recipient` | 收件人 | Recipient | ACL（长期） · platform.integration | 消息的接收人，用企业微信用户 ID 精确指定；不允许按部门、标签或全员投递 |  |  |
| `source_reference` | 来源对照 | SourceReference | ACL（过渡期） · platform.integration | 外部系统或老系统中一个对象的标识与 FORCITIS one 自有 ID 的对应；只在集成层，领域只用自有 ID；同一来源系统、同一类别下来源 ID 与自有 ID 在任一时点各自唯一 |  | ADR-0033 登记；权限码 platform.integration:source-reference:update（授予组织数据管理员）、platform.integration:source-reference:approve（核算组织编码四眼确认，授予业务管理员），授予方式均为 INDIVIDUAL |
| `source_system` | 来源系统 | SourceSystem | ACL（过渡期） · platform.integration | 来源对照所属的外部系统，取值为组织源（ORG_SOURCE）、老系统（LEGACY_SYSTEM）、核算系统（ACCOUNTING_SYSTEM），只在集成层与迁入登记中出现 |  | 数据库列叫 source_system，Java 枚举叫 ExternalSource（platform.integration.api），因为 kernel.event.SourceSystem（ONE / MIGRATION）表示事件出自新系统还是历史迁入，是另一回事，不改名 |
| `source_object_kind` | 来源对象类别 | SourceObjectKind | ACL（过渡期） · platform.integration | 来源对照所指的自有对象类别（员工、组织单元、岗位、法人主体等），决定自有 ID 指向哪类对象 |  | ADR-0033（提议）登记，待用语确认 |
| `rejected_state` | 已拒绝 | RejectedState | ACL（长期） · platform.integration | 来源对照的确认状态之一：登记的对照被另一个人拒绝，不占自然键约束，不用于出站 |  | 展示用语；对应确认状态 REJECTED。与审批的「驳回」（Reject，platform.approval，审批人对任务给出不同意的结论）不是一回事：这里是另一个人不接受一条登记的来源对照，中文写「已拒绝」，英文命令写 Reject 时不得显示成「驳回」。确认状态的写法：编号规则版本为 PENDING_CONFIRMATION / CONFIRMED，来源对照为 PENDING / CONFIRMED / REJECTED，中文都是待确认 / 已确认 / 已拒绝（ADR-0033 第 2 项第 1 条） |
| `registration` | 登记 | Registration | ACL（长期） · platform.integration | 由人把一条来源对照录入系统，先处于待确认，等另一个人确认或拒绝；现在只有核算组织编码可以人工登记 |  |  |
| `supersede` | 取代 | Supersede | ACL（长期） · platform.integration | 已确认的后继对照从生效日起取代生效中的对照：旧对照在后继的生效日结束，两行的来源标识与自有 ID 都不改写 |  |  |
| `evidence` | 取证 | Evidence | 领域 · platform.integration | 只读核对老系统与 A8 生产数据，用于复刻老行为、迁入对账与新旧等价核对；用专用取证账号，只出计数或聚合，或落 ECS 数据盘，每次登记一行 |  | ADR-0037 登记（数据库登录名 one_evidence_ro），待用语确认 |
| `read_only_verification` | 只读核实 | ReadOnlyVerification | ACL（过渡期） · platform.integration | 运维命令：对一条只读外部连接的登录跑与启动校验同一套判定，逐项（ADR-0037 第 4 项 (a)–(g)）输出核实通过、确认违规或判定不了，判定不了时只带 SQL 错误号与 SQLState，按结果给退出码；不输出数据、消息原文与凭据，不监听端口、不迁移、不起作业，口令只从环境读 |  | PAR-2 验收测试登记（档案名 read-only-verification），待用语确认 |
| `read_only_verification_result` | 只读核实结果 | ReadOnlyVerificationResult | ACL（过渡期） · platform.integration | 只读核实对每一项与对整条连接给出的结论，取核实通过、确认违规、判定不了之一；整条连接有任一项确认违规即为确认违规，否则有判定不了即为判定不了 |  | PAR-2 验收测试登记，待用语确认 |
| `read_only_verification_result__passed` | 核实通过 | Passed | ACL（过渡期） · platform.integration | 只读核实结果的取值：该项（或全部各项）与 RDS 平台基线一致 |  | 不写「通过」：已是审批结论 approve 的中文名 |
| `read_only_verification_result__violated` | 确认违规 | Violated | ACL（过渡期） · platform.integration | 只读核实结果的取值：数据库目录确实显示该项超出或缺少基线（与「判定不了」相对） |  | PAR-2 验收测试登记，待用语确认 |
| `read_only_verification_result__undetermined` | 判定不了 | Undetermined | ACL（过渡期） · platform.integration | 只读核实结果的取值：查询出错、超时、连不上或返回为空，该项无法判定；启动时同样拒绝启动（ADR-0037 第 4 项第 2 条） |  | PAR-2 验收测试登记，待用语确认 |
| `overridden_grant` | 被拒绝覆盖的授权 | OverriddenGrant | ACL（过渡期） · platform.integration | 已授予、但被同级或更高层级的 DENY 覆盖、实际不生效的权限；只读核实按有效权限判定时不算违规，只作提示。 |  | one-glossary 术语门禁结论（one-coordinator 2026-10-15 转达）；只读核实命令定位行种类 overridden，by 写被哪条 DENY 覆盖（DATABASE_DENY / SCHEMA_DENY） |

另有错误码 7 个（kind 为 `error_code`）、技术词 25 个（`common_words`），不在表中。

## platform.numbering（`platform-numbering.yaml`）

platform.numbering 提议术语（proposed，待业务评审，ADR-0018）。服务于原编号、确认状态、单据类型代码的命名守护。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `qt` | 报价单据类型代码 | Qt | 领域 · platform.numbering | 报价（DocumentType.QUOTE）的单据类型代码 QT，1b 首个使用方 |  | 例 QT-FST-202610-0001 |
| `ts` | 工时单据类型代码 | Ts | 领域 · platform.numbering | 工时（DocumentType.TIMESHEET）的单据类型代码 TS，沿用种子模板中的前缀字母，正式词表尚无对应词 |  | 例 TS-FST-202610-0001；模板待管理员确认 |
| `jv` | 记账凭证单据类型代码 | Jv | 领域 · platform.numbering | 记账凭证（DocumentType.JOURNAL_VOUCHER）的单据类型代码 JV，沿用种子模板中的前缀字母，正式词表尚无对应词 |  | 例 JV-FST-202610-0001；模板待管理员确认 |

另有错误码 4 个（kind 为 `error_code`）、技术词 3 个（`common_words`），不在表中。

## platform.web（`platform-web.yaml`）

无错误码以外的术语。

另有错误码 5 个（kind 为 `error_code`）、技术词 12 个（`common_words`），不在表中。

## `round3-pending-renames.yaml`

涉及模块：`accounting.voucher`、`cost.timesheet`、`organization.workforce`、`platform.approval`、`platform.identity`、`platform.integration`、`platform.numbering`。

第三轮用语确认的待改名登记（one-glossary 生成，round3/writeback.py）：用户已定新名，旧名仍在代码或界面中使用。

负责会话按 glossary/round3/RENAME_CARDS.md 改名并合入后，one-glossary 删除对应条目，方言禁用随之生效。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `round3_pending__act` | 办理 | Act | 领域 · platform.approval | 第三轮用语确认（R3-AP-04）已定为：办理 / Handle（handle）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Act（claude/one-1a-par-f1-acceptance） |
| `round3_pending__assigned` | 已分配 | Assigned | 领域 · platform.approval | 第三轮用语确认（R3-AP-05）已定为：任务已分配 / TaskAssigned（task_assigned）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Assigned（claude/one-1a-next-3-r1b-p2） |
| `round3_pending__completed` | 已完成 | Completed | 领域 · platform.approval | 第三轮用语确认（R3-AP-07）已定为：审批已完成 / ApprovalCompleted（approval_completed）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Completed（main） |
| `round3_pending__escalated` | 已升级 | Escalated | 领域 · platform.approval | 第三轮用语确认（R3-AP-10）已定为：已上推 / ReroutedToSuperior（rerouted_to_superior）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Escalated（claude/one-1a-next-3-r1b-p2） |
| `round3_pending__fallback` | 兜底 | Fallback | 领域 · platform.approval | 第三轮用语确认（R3-AP-11）已定为：转流程管理员 / AdministratorFallback（administrator_fallback）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Fallback（claude/one-1a-next-3-r1b-p2） |
| `round3_pending__form` | 表单 | Form | 领域 · platform.approval | 第三轮用语确认（R3-AP-12）已定为：审批表单字段 / ApprovalField（approval_field）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Form（claude/one-1a-next-3-r1b-p2、claude/one-1a-par-f1-acceptance、claude/one-1a-par-k3a） |
| `round3_pending__round` | 轮次 | Round | 领域 · platform.approval | 第三轮用语确认（R3-AP-15）已定为：审批轮次 / ApprovalRound（approval_round）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Round（claude/one-1a-next-3-r1b-p2、claude/one-1a-par-k3a） |
| `round3_pending__row` | 行 | Row | 领域 · platform.approval | 第三轮用语确认（R3-AP-16）已定为：行级任务 / LineTask（line_task）。代码与界面改名合入前保留旧写法。 |  | 界面文字仍用「行」；代码仍用 Row（claude/one-1a-next-3-r1b-p2、claude/one-1a-par-f1-acceptance、claude/one-1a-par-k3a） |
| `round3_pending__deadline` | 审批时限 | Deadline | 领域 · platform.approval | 第三轮用语确认（R3-AP-21）已定为：审批时限 / ApprovalTimeLimit（approval_time_limit）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Deadline（claude/one-1a-par-k3a） |
| `round3_pending__effect` | 效力 | Effect | 领域 · platform.approval | 第三轮用语确认（R3-AP-22）已定为：办理效力 / TaskEffect（task_effect）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Effect（claude/one-1a-par-f1-acceptance、claude/one-1a-par-k3a） |
| `round3_pending__release_of_task` | 释放 | ReleaseOfTask | 领域 · platform.approval | 第三轮用语确认（R3-AP-25）已定为：取消认领 / Unclaim（unclaim）。代码与界面改名合入前保留旧写法。 |  | 界面文字仍用「释放」 |
| `round3_pending__ui_business_summary` | 业务摘要 | BusinessSummary | 领域 · platform.approval | 第三轮用语确认（R3-AP-41）已定为：审批摘要 / ApprovalSummary（approval_summary）。代码与界面改名合入前保留旧写法。 |  | 界面文字仍用「业务摘要」 |
| `round3_pending__ui_proxy_handling` | 代理 | ProxyHandling | 领域 · platform.approval | 第三轮用语确认（R3-AP-42）已定为：代理办理 / ProxyHandling（ui_proxy_handling）。代码与界面改名合入前保留旧写法。 |  | 界面文字仍用「代理」 |
| `round3_pending__task_participant` | 参与人 | TaskParticipant | 领域 · platform.approval | 第三轮用语确认（R3-AP-53）已定为：任务参与人 / TaskParticipant（task_participant）。代码与界面改名合入前保留旧写法。 |  | 界面文字仍用「参与人」 |
| `round3_pending__decision` | 决定 | Decision | 领域 · platform.approval | 第三轮用语确认（R3-AP-57）已定为：审批决定 / TaskDecision（task_decision）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Decision（main） |
| `round3_pending__resulted` | 已出结果 | Resulted | 领域 · platform.approval | 第三轮用语确认（R3-AP-60）已定为：任务已办理 / TaskHandled（task_handled）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Resulted（main） |
| `round3_pending__skipped` | 已跳过 | Skipped | 领域 · platform.approval | 第三轮用语确认（R3-AP-65）已定为：通知已跳过 / NotificationSkipped（notification_skipped）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Skipped（main） |
| `round3_pending__project_manager_review` | 项目经理审批 | ProjectManagerReview | 领域 · platform.approval | 第三轮用语确认（R3-AP-68）已定为：项目经理审批 / ProjectManagerApproval（project_manager_approval）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 ProjectManagerReview（main） |
| `round3_pending__regional_head_review` | 区域负责人审批 | RegionalHeadReview | 领域 · platform.approval | 第三轮用语确认（R3-AP-69）已定为：区域负责人审批 / RegionalHeadApproval（regional_head_approval）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 RegionalHeadReview（main） |
| `round3_pending__supervisor_review` | 一级汇报人审批 | SupervisorReview | 领域 · platform.approval | 第三轮用语确认（R3-AP-70）已定为：直属上级审批 / SupervisorApproval（supervisor_approval）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 SupervisorReview（main） |
| `round3_pending__cutoff` | 撤回截止点 | Cutoff | 领域 · platform.approval | 第三轮用语确认（R3-AP-74）已定为：撤回截止点 / WithdrawalCutoff（withdrawal_cutoff）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Cutoff（main） |
| `round3_pending__administrator_mode` | 管理模式 | AdministratorMode | 领域 · platform.identity | 第三轮用语确认（R3-ID-04）已定为：管理员模式 / AdministratorMode（administrator_mode）。代码与界面改名合入前保留旧写法。 |  | 界面文字仍用「管理模式」 |
| `round3_pending__authorization` | 授权 | Authorization | 领域 · platform.identity | 第三轮用语确认（R3-ID-10）已定为：授予权限 / GrantPermission（grant_permission）。代码与界面改名合入前保留旧写法。 |  | 界面文字仍用「授权」；代码仍用 Authorization（claude/one-1a-next-3-r1b-p2、claude/one-1a-par-f1-acceptance） |
| `round3_pending__account_disabled` | 停用 | AccountDisabled | 领域 · platform.identity | 第三轮用语确认（R3-ID-11）已定为：账号停用 / AccountDisabled（account_disabled）。代码与界面改名合入前保留旧写法。 |  | 界面文字仍用「停用」 |
| `round3_pending__permission_adjustment_kind__sensitive_role_holder` | 敏感角色持有人 | SensitiveRoleHolder | 领域 · platform.identity | 第三轮用语确认（R3-ID-22）已定为：敏感角色复核 / SensitiveRoleReview（permission_adjustment_kind__sensitive_role_review）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 SensitiveRoleHolder（claude/one-1a-par-f1-acceptance） |
| `round3_pending__size` | 大小 | Size | 领域 · platform.integration | 第三轮用语确认（R3-IN-07）已定为：字节数 / ByteCount（byte_count）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Size（claude/one-1a-next-3-r1b-p2、claude/one-1a-par-f1-acceptance、claude/one-1a-par-k3a） |
| `round3_pending__keep` | 保留 | Keep | 领域 · platform.integration | 第三轮用语确认（R3-IN-08）已定为：数据保留期限 / DataRetentionPeriod（data_retention_period）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Keep（claude/one-1a-par-f1-acceptance） |
| `round3_pending__worm` | 一次写入多次读取 | Worm | 领域 · platform.integration | 第三轮用语确认（R3-IN-36）已定为：一次写入多次读取 / WriteOnceReadMany（write_once_read_many）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Worm（main） |
| `round3_pending__voucher_numbering_rule` | 凭证号规则 | VoucherNumberingRule | 领域 · platform.numbering | 第三轮用语确认（R3-NU-08）已定为：记账凭证编号规则 / JournalVoucherNumberingRule（journal_voucher_numbering_rule）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 VoucherNumberingRule（main） |
| `round3_pending__rule_version` | 规则版本 | RuleVersion | 领域 · platform.numbering | 第三轮用语确认（R3-NU-09）已定为：编号规则版本 / NumberingRuleVersion（numbering_rule_version）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 RuleVersion（main） |
| `round3_pending__overhead_hours` | OH 工时 | OverheadHours | 领域 · cost.timesheet | 第三轮用语确认（R3-TS-04）已定为：间接工时 / IndirectHours（indirect_hours）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 OverheadHours（main） |
| `round3_pending__workforce__email` | 企业邮箱 | Email | 领域 · organization.workforce | 第三轮用语确认（R3-WF-06）已定为：企业邮箱 / EmailAddress（workforce__email_address）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Email（claude/one-1a-par-f1-acceptance） |
| `round3_pending__department` | 部门 | Department | 领域 · platform.approval | 第三轮用语确认（R3-AP-09）已定为：并入 org_unit。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Department（claude/one-1a-next-3-r1b-p2、claude/one-1a-par-f1-acceptance） |
| `round3_pending__ui_task_handler` | 办理人 | TaskHandler | 领域 · platform.approval | 第三轮用语确认（R3-AP-32）已定为：并入 actor。代码与界面改名合入前保留旧写法。 |  | 界面文字仍用「办理人」 |
| `round3_pending__running` | 进行中 | Running | 领域 · platform.approval | 第三轮用语确认（R3-AP-61）已定为：并入 ui_approval_running。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Running（main） |
| `round3_pending__ts` | 工时单据类型代码 | Ts | 领域 · platform.numbering | 第三轮用语确认（R3-NU-06）已定为：并入 document_type_code。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Ts（claude/one-design-sync-staging） |
| `round3_pending__qt` | 报价单据类型代码 | Qt | 领域 · platform.numbering | 第三轮用语确认（R3-NU-17）已定为：并入 document_type_code。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Qt（main） |
| `round3_pending__jv` | 记账凭证单据类型代码 | Jv | 领域 · platform.numbering | 第三轮用语确认（R3-NU-18）已定为：并入 document_type_code。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Jv（main） |
| `round3_pending__journal_voucher` | 会计凭证 | JournalVoucher | 领域 · accounting.voucher | 第三轮用语确认（R3-AP-26）已定为：记账凭证 / JournalVoucher（journal_voucher）。代码与界面改名合入前保留旧写法。 |  | 界面文字仍用「会计凭证」 |
