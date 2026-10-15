# 提议术语清单（第三轮用语确认用）

<!-- 本文件由 backend/glossary/build_proposed_index.py 从同目录 *.yaml 生成，不要手改；改 yaml 后运行 python3 docs/modernization/build_all.py 并提交结果。 -->

`terms` 只放业务术语，纯技术词（动词、框架词、值对象方法名等）放同文件的 `common_words`。本表按文件列出除错误码以外的全部提议术语；「是否疑似方言」由条目中类型为方言或待定的别名与禁用写法生成，空表示未登记问题；「备注」为条目 note 原文。

修改任何一个 yaml 后运行 `./mvnw -B verify`（GlossaryTest 会校验字段、重名和未登记词）与 `python3 docs/modernization/build_all.py`（重新生成本文件）。

## `contract-evolution.yaml`

涉及模块：`platform.integration`、`platform.scheduling`、`platform.web`。

契约演进与部署排空用语（proposed，ADR-0035，待用语确认，ADR-0018）。

契约演进用语（`contract-evolution.yaml`，ADR-0035）：`maintenance_mode` 维护模式、`event_quarantine` 隔离登记、`deprecation_register` 弃用登记。

| id | zh | en | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|
| `maintenance_mode` | 维护模式 | MaintenanceMode | 部署或回滚期间的系统状态：停写请求与业务类定时作业，事件投递与重投照常，用于排空事件发布；状态存于库内标记，由 one-ops 部署剧本开关（ADR-0035 第 6 条） |  |  |
| `event_quarantine` | 隔离登记 | EventQuarantine | 部署或回滚时排不空的事件发布被移入的登记，保留原负载、类型、关联号与部署批次，修复后重新投递或人工处理（ADR-0035 第 6 条） |  | 与组织同步的「被隔离的行」（isolated row）无关 |
| `deprecation_register` | 弃用登记 | DeprecationRegister | 已标为弃用的接口、字段、事件或数据集列的登记（backend/architecture/deprecations.yaml）：对象、弃用日、消费方类别、到期条件、出处；G-C1 据此放行到期删除（ADR-0035 第 7 条） |  | 仓库内文件，不是数据库表 |

## cost.timesheet（`cost-timesheet.yaml`）

cost.timesheet 提议术语（proposed，待第三轮用语确认，ADR-0018）。

| id | zh | en | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|
| `time_entry_category` | 工时类别 | TimeEntryCategory | 工时按用途的分类，取值为项目工时、售前工时、OH 工时；决定工时单走哪些审批分支 |  |  |
| `project_hours` | 项目工时 | ProjectHours | 工时类别的取值：记在项目工作上的工时 |  |  |
| `pre_sales_hours` | 售前工时 | PreSalesHours | 工时类别的取值：记在售前工作上的工时 |  | 尚非正式用语 |
| `overhead_hours` | OH 工时 | OverheadHours | 工时类别的取值：记在间接（管理）工作上的工时 | 「OH工时」待定 | 疑似方言，第三轮用语确认 |

另有错误码 12 个（kind 为 `error_code`）、技术词 23 个（`common_words`），不在表中。

## platform.integration（`data-migration.yaml`）

迁入的其余用语（拒收、核对、历史期间、迁入标记等）随 ADR-0036（另行 PR）登记。

通用词另增 `batch`、`count`（批量命令后缀与计数端点，NAMING_STANDARD §2.6 A-3、A-6）。

| id | zh | en | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|
| `migration_batch` | 迁入批次 | MigrationBatch | 一次历史数据迁入运行；登记来源系统、对象类型、范围、状态、行数与来源 ID 摘要；批次挂在来源对照与原编号对照上，业务表不带批次列 | 「导入批次」已禁用；「import_batch」已禁用；「ImportBatch」已禁用 | 用户 2026-10-15 定批次术语唯一写法；platform.numbering 的 import_batch 已在 R-1b P3a（#139）改为 migration_batch_id，同一提交加入 forbidden 并删去 platform-integration.yaml 中的重复条目 |

另有技术词 2 个（`common_words`），不在表中。

## `fix-b.yaml`

涉及模块：`organization.workforce`、`platform.approval`、`platform.identity`、`platform.integration`、`platform.numbering`、`platform.web`。

Phase 1a 修复批 B 新增术语（proposed，待业务评审，ADR-0018）。

| id | zh | en | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|
| `escalation` | 升级 | Escalation | 审批任务超时未处理时，转给上级处理 |  |  |
| `reassignment` | 改派 | Reassignment | 流程管理员把待办改给另一个人 |  |  |
| `release_of_task` | 释放 | ReleaseOfTask | 认领人放回已认领的任务，让候选人重新认领 |  |  |
| `accounting_voucher_text` | 记账凭证 | AccountingVoucherText | 会计凭证在对外文案中的叫法（审批单据类型的显示名） |  |  |

另有错误码 47 个（kind 为 `error_code`）、技术词 49 个（`common_words`），不在表中。

## `fix-b1-identity.yaml`

涉及模块：`kernel`、`organization.workforce`、`platform.audit`、`platform.identity`、`platform.numbering`。

Phase 1a 修复批 B1（身份与授权）新增术语（proposed，待业务评审，ADR-0018）。ADR-0027 的八项与运维命令用到的词；每个新错误码一条 error__ 条目。

| id | zh | en | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|
| `witness` | 见证人 | Witness | 运维命令执行时在场的另一个人，与操作人一起记入审计（双人在场） |  |  |
| `separation` | 离职 | Separation | 员工从在职变为离职这件事；离职时删除全部角色授予、停用应急账号、吊销会话（ADR-0027 第 1 项）。事件、字段与方法里的 separated 是同一概念的过去分词（已离职），登记为技术词，不另立术语 |  |  |
| `finance` | 财务 | Finance | 凭证号模板在业务确认之外另需的财务确认所属的职能（D-1a-157） |  |  |
| `confirmer` | 确认人 | Confirmer | 确认某个编号模板版本的人：业务确认人持有 numbering-rule:approve，财务确认人持有凭证号模板的确认权限 |  |  |
| `author` | 作者 | Author | 保存某个编号模板版本的人；作者不能确认自己保存的版本（四眼） |  |  |
| `authorization_request` | 授权申请 | AuthorizationRequest | 授予敏感权限、或把角色改成含敏感权限时先建的申请，另一人确认后才生效 |  |  |
| `retained` | 留任 | Retained | 放行同步暂停的人必须仍是启用账号、持有 super_admin 或安全审计员、在组织快照中明确在职且不在离职名单上（ADR-0028） |  |  |
| `administrator_mode` | 管理模式 | AdministratorMode | super_admin 持有人经再次验证后在同一会话进入的模式，最长 1 小时、不延长；只有五项平台权限（ADR-0027 第 2 项、ADR-0028），不含业务、敏感读与审计查看 |  |  |
| `forced_logout` | 强制下线 | ForcedLogout | 管理员结束某个人的全部会话：一次一人、须写理由、不能对自己，结束超级管理员或安全审计员的会话会告警（ADR-0027 第 3 项） |  |  |
| `org_data_admin` | 组织数据管理员 | OrgDataAdmin | 系统角色 org_data_admin：维护组织与法人归属、工作日历、编号模板、同步运行；无敏感权限、不能确认模板（ADR-0027 第 2 项） |  |  |
| `business_admin` | 业务管理员 | BusinessAdmin | 系统角色 business_admin：确认各单据编号模板（凭证号模板的财务确认不在 1a 内设角色）（ADR-0027 第 2 项） |  |  |
| `security_auditor` | 安全审计员 | SecurityAuditor | 系统角色 security_auditor：查看与导出审计记录，并在普通会话里放行管理员离职造成的同步暂停（ADR-0027 第 3 项、ADR-0028） |  |  |
| `voucher_numbering_rule` | 凭证号规则 | VoucherNumberingRule | 凭证号模板的编号规则；其确认权限 voucher-numbering-rule:approve 是敏感权限，1a 不设持有角色，待首个过账阶段再定（用户决定） |  |  |
| `install` | 安装 | Install | 把 JVM 默认时区设为 UTC 的动作（UtcJvm.installAsDefault）；整合分支的方法名用到了它，此处补登记 |  |  |

另有错误码 28 个（kind 为 `error_code`）、技术词 18 个（`common_words`），不在表中。

## `fix-b2-data.yaml`

涉及模块：`organization.attendance`、`organization.workforce`、`platform.integration`。

Phase 1a 修复批 B2（数据架构）新增术语（proposed，待业务评审，ADR-0018）。

| id | zh | en | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|
| `archived` | 已归档 | Archived | 已写入归档库并经归档库确认的状态或时间 |  |  |
| `probe` | 探针 | Probe | 不留下数据的写入检查：启动时和每天一次检查归档目标是否可用 |  |  |
| `size` | 大小 | Size | 归档对象的字节数 |  |  |
| `revision_number` | 修订序号 | RevisionNumber | 版本表同一生效日内的修订序号：同日再次变化时关闭当天版本（空区间）并以加 1 的序号追加新行 |  |  |
| `work_calendar_locked_days_changed` | 工作日历锁定日期被修改 | WorkCalendarLockedDaysChanged | 组织数据管理员确认修改了工作日历中早于今天的日期；载荷列出每个被改日期的改前改后类别，进审计 |  |  |
| `keep` | 保留 | Keep | 把数据留在在线库或归档库的期限，到期后删除或转归档；与「质保金」无关 |  |  |
| `worm` | 一次写入多次读取 | Worm | 归档库的合规保留锁：锁定期内对象不能被覆盖或删除 |  |  |

另有错误码 1 个（kind 为 `error_code`），不在表中。

## `fix-b2b-events.yaml`

涉及模块：`kernel`、`organization.attendance`、`organization.workforce`、`platform.approval`、`platform.numbering`、`platform.scheduling`。

Phase 1a 修复批 B2b（事件业务日期、指标）新增术语（proposed，待业务评审，ADR-0018）。

| id | zh | en | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|
| `rule_version` | 规则版本 | RuleVersion | 编号模板的版本号值对象（RuleVersion），从 1 起，每保存一个新模板加 1；版本表的 version_number 列、接口字段 templateVersion 和事件负载 ruleVersion 是同一个东西；与乐观锁 version、事件序号 event_count 无关 |  |  |
| `effective_from_date` | 生效日 | EffectiveFromDate | 某个事实、版本或模板从哪一天起生效（含当天）的日期；事件负载与代码里叫 effectiveFrom，列名带类别词 _date。OrgSnapshotChanged 的 effectiveFrom 语义相同：变化从哪一天起生效，因此与其他地方统一叫 effectiveFrom，不再另设 effectiveDate |  |  |
| `snapshot_date` | 快照日 | SnapshotDate | 读取组织源快照的那个业务日，即同步运行开始当天（Asia/Shanghai）。它是 OrgSnapshotChanged 信封的 businessDate，也是同一次同步写入的版本行的生效日 |  |  |
| `changed_dates` | 被修改的日期 | ChangedDates | 一次工作日历保存新增、删除或改变类型的日期，按日期升序，放在 WorkCalendarYearMaintained 的负载里；原样重存时为空 |  |  |
| `dead_letter_job` | 死信作业 | DeadLetterJob | 流程引擎用尽重试后移入死信表的作业，需要运维处理；数量由指标 dead.letter.jobs 导出 |  |  |
| `job_failure` | 作业失败 | JobFailure | 带 SchedulerLock 的定时作业抛出异常的一次运行；按作业名（锁名）计数，指标 job.failures。作业自己捕获并记录的失败不在其中，由所属模块的指标覆盖 |  |  |

## `fix-c.yaml`

涉及模块：`kernel`、`organization.attendance`、`organization.workforce`、`platform.approval`、`platform.integration`、`platform.numbering`。

前端修复批新增的界面用语（proposed）：状态、列名与提示语中的业务词，守护要求它们作为完整的词登记，不得由单字拼成。

状态、列名与提示语中的业务词。前端用语守护要求单字只作连接词，状态词（已通过、待认领等）必须作为完整的词登记。

| id | zh | en | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|
| `ui_done_task` | 已办 | DoneTask | 当前用户已经办理过的审批任务，列出办理结果和意见 |  | 待办页的「已办」列表（D-1a-90） |
| `ui_pending_claim` | 待认领 | PendingClaim | 任务有候选审批人但还没有人认领；与「已认领」相对 |  | 界面状态用语，任务详情的处理人一栏也显示它 |
| `ui_approval_record` | 审批记录 | ApprovalRecord | 任务详情中按时间列出的办理记录：节点、办理结果、办理人、办理时间、意见 |  | 对应 D-1a-90 的任务办理记录 |
| `ui_task_outcome` | 办理结果 | TaskOutcome | 一次办理的结论：通过、驳回、退回、转交或自动通过 |  | 审批记录的列名 |
| `ui_task_handler` | 办理人 | TaskHandler | 办理了这条任务的人；代理办理时是被代理人以外实际操作的人 |  | 审批记录的列名；「处理人」指认领后唯一可办理的人，两者不同 |
| `ui_flow_node` | 节点 | FlowNode | 审批流程中的一个环节，每个节点有自己的审批人规则 |  | 审批记录的列名；「当前节点」是它的特指 |
| `ui_approval_running` | 审批中 | ApprovalRunning | 审批已经发起且尚未结束；工时单提交之后即处于此状态 |  | 展示用语；对应审批状态 RUNNING 与工时单状态 SUBMITTED |
| `ui_approval_approved` | 已通过 | ApprovalApproved | 审批以通过结束 |  | 展示用语；对应审批状态 APPROVED |
| `ui_approval_rejected` | 已驳回 | ApprovalRejected | 审批以驳回结束 |  | 展示用语；对应审批状态 REJECTED |
| `ui_approval_returned` | 已退回 | ApprovalReturned | 审批被退回，本轮结束，发起人修改后重提开新一轮 |  | 展示用语；对应审批状态 RETURNED |
| `ui_auto_approved` | 自动通过 | AutoApproved | 系统在同一审批人重复出现等情形下自动给出的通过，办理记录里标注「自动」 |  | D-1a-66；办理结果的取值 |
| `ui_opinion_unfilled` | 未填写意见 | OpinionUnfilled | 通过时没有填写意见；数据里留空，界面显示为「（未填写意见）」，不写入数据 |  | RULE-280 |
| `ui_pending_confirmation_state` | 待确认 | PendingConfirmationState | 编号规则还没有由管理员确认 |  | 展示用语；对应确认状态 PENDING_CONFIRMATION |
| `ui_confirmed_state` | 已确认 | ConfirmedState | 编号规则已由管理员确认 |  | 展示用语；对应确认状态 CONFIRMED |
| `ui_released_state` | 已放行 | ReleasedState | 被中止的同步已由管理员放行并生效 |  | 展示用语；同步记录状态 RELEASED |
| `ui_never_reset` | 不重置 | NeverReset | 流水号永不重置，一直递增 |  | 展示用语；重置周期 NEVER |
| `ui_locked_day` | 已锁定 | LockedDay | 工作日历中已经过去的日期；修改需要组织数据管理员二次确认 |  | 用户决定 T2；展示用语 |
| `ui_cc_notice` | 知会 | CcNotice | 只通知、不阻塞流程的审批知会；在合并待办里单列，打开即已读 |  | D-1a-67、D-1a-128 |
| `ui_business_summary` | 业务摘要 | BusinessSummary | 业务模块在发起审批时随请求提供的单据摘要，待办与通知据此显示，不回查业务单据 |  | D-1a-133 |
| `ui_proxy_handling` | 代理 | ProxyHandling | 流程管理员替被代理人办理任务；审批记录同时显示操作人与被代理人 |  | D-1a-77 |
| `ui_task_deadline` | 截止时间 | TaskDeadline | 审批任务应办理完的时刻；超过则提醒并升级，待办标出已超时 |  | D-1a-84、D-1a-110；任务详情的字段名 |
| `ui_external_system` | 外部系统 | ExternalSystem | 本系统之外、通过防腐层接入的系统，如企业微信、致远、组织来源 |  | 错误提示用语 |
| `ui_maintain` | 维护 | Maintain | 录入和修改工作日历这类基础数据 |  | 界面与提示用语 |
| `ui_calculation` | 计算 | Calculation | 按规则得出日期、时限、编号等的过程 |  | 提示用语 |
| `ui_outcome_result` | 结果 | OutcomeResult | 一次办理或一次计算得出的结论 |  | 提示用语；「办理结果」是它在审批记录里的特指 |
| `ui_rest_day` | 休息日 | RestDay | 周末且不是调休上班日的日期 |  | 工作日历的展示用语，与「工作日」相对 |
| `ui_advisory_note` | 加签建议 | AdvisoryNote | 加签人给出的参考意见，不算正式的办理结果 |  | D-1a-76；办理结果的取值 ADVISED |

## `fix-time-zone.yaml`

Phase 1a 时间与时区修复新增用语（proposed，待业务评审，ADR-0018）。纯技术词放 common_words。

无错误码以外的术语。

另有技术词 2 个（`common_words`），不在表中。

## `frontend-ui.yaml`

涉及模块：`kernel`、`organization.workforce`、`platform.approval`、`platform.identity`、`platform.numbering`。

前端界面用语中尚不在正式词表里的业务术语（proposed，待第三轮用语确认，ADR-0018）。

前端界面用语（`frontend-ui.yaml`）另增：`correlation_id` 关联号 / CorrelationId（归属 `kernel`）——一次请求及其引发的事件与投递共用的追踪编号；出错时界面显示可复制的关联号（NAMING_STANDARD §4a U5）。

| id | zh | en | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|
| `correlation_id` | 关联号 | CorrelationId | 一次请求及其引发的事件与投递共用的追踪编号（X-Correlation-Id）；出错时界面显示可复制的关联号，便于对日志（NAMING_STANDARD §4a U5、GUIDING_PRINCIPLES §11 O-1） |  | 界面不另称「错误编号」，与 platform-web 错误响应同一用语 |
| `pending_task` | 待办 | PendingTask | 当前用户待处理的审批任务，新系统任务与老系统待办合并后在待办页展示 |  | 界面页面标题与导航用语；与 platform-approval.yaml 的 todo（待办事项）、inbox（待办收件箱）指同一事物，第三轮用语确认时合并 |
| `candidate_approver` | 候选审批人 | CandidateApprover | 任务尚未认领时，有资格认领并办理该任务的审批人 |  | 任务详情页字段名；正式词表只有「审批人」 |
| `task_claim` | 认领 | TaskClaim | 候选审批人把任务认到自己名下，认领后只有他可以办理 |  | 界面按钮与状态用语（待认领、已认领） |
| `approve` | 通过 | Approve | 审批人对任务给出同意的结论 |  | 界面按钮与结果用语 |
| `reject` | 驳回 | Reject | 审批人对任务给出不同意的结论，是否终止流程取决于所在节点 |  | 界面按钮与结果用语 |
| `legacy_system` | 老系统 | LegacySystem | 被本系统替代的原项目管理系统（待办合并期间只读接入） |  | 疑似方言，第三轮用语确认；界面用语，正式叫法待定 |
| `new_system` | 新系统 | NewSystem | FORCITIS one 本身；待办合并期间与老系统并存，界面用来标出待办的来源 |  | 用户 2026-10-14 指定提议；界面标签、筛选项、提示用语 |
| `org_browse` | 组织浏览 | OrgBrowse | 只读浏览组织单元树的页面 |  | 用户 2026-10-14 指定提议；页面标题与导航用语 |
| `current_node` | 当前节点 | CurrentNode | 审批任务当前所在的流程节点 |  | 用户 2026-10-14 指定提议；任务详情字段名 |
| `trigger_mode` | 触发方式 | TriggerMode | 一次同步是定时触发还是手动触发 |  | 用户 2026-10-14 指定提议；同步记录列名 |
| `held` | 被中止 | Held | 同步记录的状态：被缩水保护中止，等待管理员放行；与「被中止的同步」指同一状态 |  | 用户 2026-10-14 指定提议；同步记录状态值，第三轮用语确认时与 workforce__held_run 合并 |
| `release` | 放行 | Release | 管理员核对同步差异后，让被中止的同步生效的动作 |  | 扫描前端界面文案发现；同步记录页按钮与结果用语 |
| `org_sync` | 同步 | OrgSync | 把组织来源的组织单元与员工变化应用到本系统的一次运行 |  | 扫描前端界面文案发现；与「同步记录」「同步差异」配合使用 |
| `delivery_unit` | 交付单元 | DeliveryUnit | 承担项目交付的组织单元 |  | 扫描前端界面文案发现；组织浏览页列名 |
| `serial_number` | 流水号 | SerialNumber | 编号中随单据递增的序号部分 |  | 扫描前端界面文案发现；编号规则页用语 |
| `start_serial_number` | 起始流水号 | StartSerialNumber | 编号规则中流水号的起始值 |  | 扫描前端界面文案发现；编号规则页字段名 |
| `issue_number` | 发号 | IssueNumber | 按编号规则为一张单据分配编号 |  | 扫描前端界面文案发现；编号规则确认提示用语 |
| `user_account` | 用户账号 | UserAccount | 一名员工在本系统的登录主体，可启用或停用 |  | 扫描前端界面文案发现；登录失败提示用语 |
| `portal` | 门户 | Portal | 公司门户，致远单点登录的入口 |  | 扫描前端界面文案发现；登录页与错误提示用语 |
| `totp_code` | 动态口令 | TotpCode | 应急管理员登录时的一次性动态口令（TOTP） |  | 扫描前端界面文案发现；应急登录页字段名 |
| `authorization` | 授权 | Authorization | 向用户授予权限，或企业微信确认用户身份的授权 |  | 扫描前端界面文案发现；权限不足页与登录失败提示用语 |
| `reason` | 理由 | Reason | 审批人办理任务时填写的说明；驳回和退回必填 |  | 扫描前端界面文案发现；办理弹层字段名 |
| `business_check` | 业务校验 | BusinessCheck | 办理审批前由业务模块执行的校验，不通过则不能办理 |  | 扫描前端界面文案发现；办理失败提示用语 |
| `task_participant` | 参与人 | TaskParticipant | 审批任务的候选审批人或处理人 |  | 扫描前端界面文案发现；任务不存在提示用语 |
| `account_disabled` | 停用 | AccountDisabled | 账号被管理员停用，停用后不能登录，已有会话随即失效 |  | 扫描前端界面文案发现；登录失败提示用语；与「已撤销」（组织单元）是两个不同状态 |
| `opinion` | 意见 | Opinion | 办理人随办理结果留下的文字说明；通过时可以留空，留空如实保存为空，界面显示为「（未填写意见）」（RULE-280） |  | 界面文案；输入框沿用「理由」，与 platform-approval 的 reason 指同一内容，第三轮用语确认时合并 |

## `kernel.yaml`

涉及模块：`contract.changeorder`、`kernel`、`organization.attendance`、`presales.quoting`。

前三条（尾调、舍入差、按权重拆分）由用户 2026-10-15 XD-07「同意 (Recommended)」确认登记（`docs/modernization/CROSS_CUTTING_DECISIONS.md`）。冲销单至冲销动作、修正动作十条是 ADR-0031 第 8 项的提议，待用语确认（U-4）。另有 `migration`、`work_calendar`、`workday` 等提议术语，以及若干技术词在 `common_words`。

| id | zh | en | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|
| `migration` | 历史迁入 | Migration | 事件或数据来自老系统 / A8 的一次性迁入（与新系统原生相对） |  |  |
| `work_calendar` | 工作日历 | WorkCalendar | 按年维护的节假日与调休上班日；默认日历适用全部法人，预留法人专属日历（用户 2026-10-14） |  |  |
| `workday` | 工作日 | Workday | 工作日历中需要上班的日期：周一至周五且非节假日，或调休上班日 |  |  |
| `tail_adjustment` | 尾调 | TailAdjustment | 报价整单的人工调整额：最终报价 = 行合计 + 尾调，审批单显示尾调金额及其占行合计的比例（RULE-142）；与舍入产生的舍入差是两个概念 | 「尾差」方言·已禁用 | 用户 2026-10-15 XD-07「同意 (Recommended)」（intent 2026-10-15-214100-one-coordinator-横切决定单-XD-07） |
| `rounding_residual` | 舍入差 | RoundingResidual | 金额按统一口径舍入到分后，各份之和与总额之间的分厘差；由按权重拆分按既定规则分配，使各份之和恰等于总额 | 「尾差」方言·已禁用 | 用户 2026-10-15 XD-07「同意 (Recommended)」；「尾差」的禁用登记在 tail_adjustment，一处即可 |
| `prorate` | 按权重拆分 | Prorate | 把一笔金额按一组非负权重拆成若干份，各份之和恰等于原额，舍入差按最大余数法分配，结果确定、可镜像；是 kernel 的金额原语，不是业务动作「间接成本分摊」（allocate_indirect_cost） |  | 用户 2026-10-15 XD-07「同意 (Recommended)」、XD-08「先按建议建 (Recommended)」 |
| `reversal_document` | 冲销单 | ReversalDocument | 只冲不重做的更正单据，用于退回或撤销，可全额也可部分；与原单同一单据类型、各为单一聚合，引用记在冲销单上，原单不写入 |  | 待用语确认（ADR-0031 第 8 项、U-4）。建议已批准词条「冲销」（reversing_entry）的 bc 由 operations.facts 改为通用，本条不改 terms.yaml；冲销单是单据层，「冲销」是事实层的更正方式，二者关系一并确认 |
| `correction_document` | 修正单 | CorrectionDocument | 完成一次冲销重做的更正单据，自带被更正单据引用，生效时冲尽被更正单据的剩余并按新内容重做；单一聚合，一个编号、一次审批、一个过账批；不得改变法人 |  | 待用语确认（ADR-0031 第 8 项、U-4）。「事实重述」（business_fact_restated）、「成本调整」（cost_adjustment）、「期间调整」（period_adjustment）都描述落当期、引用原事实的更正，在本模式下分别对应修正单或差错类冲销单的事实层效果；合并为别名还是保留，待用语确认，本条不改 terms.yaml |
| `document_nature` | 单据性质 | DocumentNature | 单据在冲销与修正模式中的身份，取原单、冲销单、修正单三者之一；冲销行存正数量与正金额，符号由单据性质决定 |  | 待用语确认（ADR-0031 第 8 项、U-4）；对应共用构件 K2 |
| `reversal_category` | 冲销类别 | ReversalCategory | 冲销的缘由，取差错或业务冲回之一；差错落开放期间并标注原期间，业务冲回如客户退回按其业务日期落账期间判定 |  | 待用语确认（ADR-0031 第 8 项、U-4，会计用语由财务）；两个取值的分别落账待 FQ-29 答复（F-2） |
| `reversible_balance` | 可冲销余额 | ReversibleBalance | 原单某行尚可被冲销的金额，等于原额减去已生效冲销合计；按已通过的冲销单与修正单计算（含待过账），不按已过账事实；冲销单不可再被冲销 |  | 待用语确认（ADR-0031 第 8 项、U-4）；只出现在读模型与校验，不回写原单 |
| `derived_reversal` | 派生冲销 | DerivedReversal | 上游单据被冲销后，由下游引用方按冲销事件自动生成的冲销，如收入确认单被冲销后配对成本的冲回；继承上游，不另审批，期间取上游实际落账期间 |  | 待用语确认（ADR-0031 第 8 项、U-4）；比例与期间待 FQ-28、FQ-29 答复（F-1、F-2） |
| `original_period` | 原期间 | OriginalPeriod | 被冲销或被更正的单据所属的会计期间；冲销或更正落在开放期间时标注，与实际落账期间分开记录 |  | 待用语确认（ADR-0031 第 8 项、U-4）；过账事件字段 originalPeriod 的单据层对应，属共用构件 K3 的组成部分 |
| `change_order_document` | 变更单 | ChangeOrderDocument | 合同变更的单据形态；合同变更是新的业务事实，向前记录而不覆盖原合同，锁定后不就地修改，单据本身有错时用取代版本 |  | 待用语确认（ADR-0031 第 8 项、U-4）。已批准词条「变更」（change_order，en ChangeOrder）是概念，本条是其单据形态；二者是否合并，或把「变更单」并入 change_order 的中文别名，待用语确认，本条不改 terms.yaml |
| `reverse_action` | 冲销动作 | ReverseAction | 权限码与接口中发起冲销的动作 reverse，对应路径 :reverse，授予范围与该单据的提交权限相同 |  | 待用语确认（ADR-0031 第 8 项）。reverse 与 ADR-0022:125 提议在 standard_actions 增加的 post、reverse、close、reopen 合并评审，本条不改 standard_actions；是否属于敏感权限见 ADR-0031 U-1 |
| `correct_action` | 修正动作 | CorrectAction | 权限码与接口中发起修正单的动作 correct，对应路径 :correct，授予范围与该单据的提交权限相同 |  | 待用语确认（ADR-0031 第 8 项）。correct 随 reverse 与 ADR-0022:125 的提议合并评审，本条不改 standard_actions；是否属于敏感权限见 ADR-0031 U-1 |

另有技术词 38 个（`common_words`），不在表中。

## organization.attendance（`organization_attendance.yaml`）

organization.attendance 提议术语（proposed，待业务评审，ADR-0018）。

| id | zh | en | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|
| `attendance` | 考勤 | Attendance | 考勤与休假限界上下文；1a 只含工作日历，请假与打卡后做（用户 2026-10-14：BC 提前到 1a 建） |  |  |
| `holiday` | 节假日 | Holiday | 工作日历中休息的日期，可落在周一至周五 |  |  |
| `make_up_workday` | 调休上班日 | MakeUpWorkday | 工作日历中需要上班的周六或周日 |  |  |
| `work_calendar_year` | 工作日历年度 | WorkCalendarYear | 某个日历的一个已维护年份；没有维护的年份，依赖它的计算失败并告警，不假定全部是工作日 |  |  |
| `calendar_scope` | 日历适用范围 | CalendarScope | 工作日历适用的范围：默认日历或某个法人主体专属日历；默认日历不用空法人加哨兵值表示 |  | ADR-0033（提议）登记，待用语确认 |

另有错误码 8 个（kind 为 `error_code`）、技术词 8 个（`common_words`），不在表中。

## organization.workforce（`organization_workforce.yaml`）

Phase 1a organization.workforce 新增术语（proposed，待业务评审，ADR-0018）

新词一律 status: proposed；通用技术词放 common_words，业务概念放 terms。

| id | zh | en | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|
| `workforce__assignment` | 任职 | Assignment | 员工在某个组织单元、某个岗位上的任职，含直属领导（汇报线）；A8 为源 |  |  |
| `workforce__reporting_line` | 汇报线 | ReportingLine | 员工到直属领导的上下级关系；同步时校验领导存在、在职、非本人、无环 |  |  |
| `workforce__sync_diff` | 同步差异 | SyncDiff | 一次同步运行改动的对象与字段，以及发现的数据例外（供数据管理员处理） |  |  |
| `workforce__shrink_guard` | 缩水保护 | ShrinkGuard | A8 返回的有效部门数或在职人数降幅超过 20 % 时中止同步，等管理员人工放行 |  |  |
| `workforce__held_run` | 被中止的同步 | HeldRun | 被缩水保护中止、等待管理员放行的同步运行 |  |  |
| `workforce__isolated_row` | 被隔离的行 | IsolatedRow | 缺部门或缺岗位的人员行：不写入、告警，不中断整批同步 |  |  |
| `workforce__org_hierarchy` | 组织层级 | OrgHierarchy | 组织单元之间的上下级结构；「本部门及以下」不含已撤销部门 |  |  |
| `workforce__broken_org_tree` | 断裂的组织树 | BrokenOrgTree | A8 返回的部门缺少上级路径；整次同步中止，什么都不写 |  |  |
| `workforce__email` | 企业邮箱 | Email | 员工的企业邮箱（A8 人事表） |  |  |
| `workforce__gender` | 性别 | Gender | 员工性别；A8 未维护或无法识别时为空，不默认写成女 |  |  |
| `workforce__login_name` | 登录名 | LoginName | 员工在组织源的登录名（手机号）；没有即离职（RULE-363） |  |  |
| `workforce__short_name` | 部门简称 | ShortName | 组织单元简称；A8 视图里没有，每天从老系统只读刷新 |  |  |
| `workforce__staff_employee` | 人员 | StaffEmployee | 一名员工连同其任职，是同步比较与写入的单位 |  |  |
| `workforce__relationship` | 关系 | Relationship | A8 中人员与组织单元之间的关系（部门负责人、区域应收负责人），类型 ID 为配置项 |  |  |
| `workforce__headquarters` | 总部 | Headquarters | 组织单元的总部 / 区域类别中的「总部」 |  |  |
| `workforce__revoked` | 已撤销 | Revoked | A8 不再返回的组织单元；与「停用」是两个不同状态（RULE-355） |  |  |

另有错误码 10 个（kind 为 `error_code`）、技术词 87 个（`common_words`），不在表中。

## platform.approval（`platform-approval.yaml`）

platform.approval 提议术语（proposed，待业务评审，ADR-0018）。单词级术语：服务于代码标识、URL、事件键、错误码的命名守护。

| id | zh | en | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|
| `act` | 办理 | Act | 对任务执行认领、通过、驳回、退回等操作 |  |  |
| `advisory` | 建议性 | Advisory | 加签人的驳回只记录意见、不终止流程（RULE-291，1b） |  |  |
| `assigned` | 已分配 | Assigned | 任务已分配给候选组（TaskAssigned） |  |  |
| `assignee` | 处理人 | Assignee | 认领后唯一可以办理任务的人（RULE-276，I-7） |  |  |
| `claimed` | 已认领 | Claimed | 任务已被候选人认领 |  |  |
| `completed` | 已完成 | Completed | 审批已结束（ApprovalCompleted） |  |  |
| `decide` | 办理决定 | Decide | 通过或驳回任务 |  |  |
| `decision` | 决定 | Decision | 对一个任务的通过或驳回 |  |  |
| `department` | 部门 | Department | 组织单元；审批规则按部门取负责人、总监、区域应收 |  |  |
| `director` | 总监 | Director | BU-002 部门总监（RULE-256） |  |  |
| `escalated` | 已升级 | Escalated | 候选集已按规则上推（汇报线不完整、防自审） |  |  |
| `fallback` | 兜底 | Fallback | 取不到人时转流程管理员 |  |  |
| `form` | 表单 | Form | 业务单据的表单数据 |  |  |
| `inbox` | 待办收件箱 | Inbox | 合并新旧系统待办的列表（RULE-261） |  |  |
| `live` | 在途 | Live | 审批仍在进行或退回待重提 |  |  |
| `resulted` | 已出结果 | Resulted | 一个任务已办理（TaskResulted） |  |  |
| `return` | 退回 | Return | 退回发起人（RULE-275） |  |  |
| `round` | 轮次 | Round | 同一单据第几次发起（RULE-278） |  |  |
| `row` | 行 | Row | 行级任务（RULE-261） |  |  |
| `running` | 进行中 | Running | 审批正在进行 |  |  |
| `terminate` | 终止 | Terminate | 终止流程实例（顶层驳回，RULE-271） |  |  |
| `todo` | 待办事项 | Todo | 待处理任务 |  |  |
| `resubmit` | 重新提交 | Resubmit | 退回后由发起人重新提交（I-5） |  |  |
| `notification` | 通知 | Notification | 企业微信通知（模块内接口隔离，架构 3.7） |  |  |
| `skipped` | 已跳过 | Skipped | 收件人缺企业微信 ID，跳过并告警（I-6） |  |  |
| `project_manager` | 项目经理 | ProjectManager | 工时所属工作的项目经理；工时审批的第一道审批人（表单字段 projectManagerId，取自业务单据） |  | 评审 H2：流程变量改用词表术语，老变量名只留在回放转换表 |
| `regional_head` | 区域负责人 | RegionalHead | 区域组织单元的负责人；含售前工时的工时单还要经他审批（表单字段 regionalHeadId） |  | 评审 H2：流程变量改用词表术语，老变量名只留在回放转换表 |
| `skip` | 跳过 | Skip | 节点显式允许且确实没有审批人时，不创建任务直接放行（RULE-300） |  |  |
| `project_manager_review` | 项目经理审批 | ProjectManagerReview | 工时审批中由项目经理办理的节点，对应项目工时 |  | 正式用语取自老系统任务名，术语表无对应词，登记为提议 |
| `regional_head_review` | 区域负责人审批 | RegionalHeadReview | 工时审批中由区域负责人办理的节点，对应售前工时 |  | 同上 |
| `supervisor_review` | 一级汇报人审批 | SupervisorReview | 工时审批中由发起人的一级汇报人办理的节点，对应 OH 工时 |  | 同上 |
| `timesheet_approval` | 工时审批 | TimesheetApproval | 工时单的审批流程；企业微信卡片上展示的流程名 |  | 流程显示名 |
| `countersign` | 会签 | Countersign | 节点上每位不同的审批人各有一个任务，全部通过才算通过；一路里任一人正式驳回即结束本轮（RULE-248/273） |  |  |
| `withdraw` | 撤回 | Withdraw | 发起人把在途审批拉回自己修改：本轮以退回结束，可重新发起下一轮（RULE-269） |  |  |
| `cancel` | 撤销 | Cancel | 发起人结束整个审批并保留历史，单据回到草稿，不记为驳回（RULE-268/279/288） |  |  |
| `transfer` | 转交 | Transfer | 当前处理人把任务交给另一位在职人员，原处理人不再能办理（RULE-285） |  |  |
| `cutoff` | 撤回截止点 | Cutoff | 流程里标了「撤回截止」的节点：其上有已完成任务之后，发起人不能再撤回或撤销（RULE-309） |  |  |
| `overdue` | 已超时 | Overdue | 任务超过审批时限仍未处理，已记录并提醒处理人；只提醒，不自动通过（RULE-377..384） |  |  |
| `deadline` | 审批时限 | Deadline | 提交日起第 n 个工作日结束；n 与升级间隔由节点的决策表给出（RULE-377/378） |  |  |
| `effect` | 效力 | Effect | 任务办理结果对本轮审批的效力：约束性的结束本轮，建议性的（加签人的驳回）只记录意见（RULE-273、RULE-291） |  |  |

另有错误码 28 个（kind 为 `error_code`）、技术词 111 个（`common_words`），不在表中。

## platform.identity（`platform-identity.yaml`）

platform.identity 新增术语（proposed，待业务评审，ADR-0018）。登录、会话、票据、角色与数据范围用到的词。

| id | zh | en | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|
| `login` | 登录 | Login | 用户以某种凭证建立会话的动作；所有入口过同一道「启用且在职」检查 |  |  |
| `logout` | 登出 | Logout | 用户主动结束会话；会话在服务端删除 |  |  |
| `session` | 会话 | Session | 服务端会话（Spring Session JDBC）：空闲 10 小时、绝对上限 24 小时，均可配置 |  |  |
| `ticket` | 票据 | Ticket | 一次性登录凭据：致远 OA 票据，或外部系统的签名票据 |  |  |
| `ticket_issuer` | 票据签发方 | TicketIssuer | 被允许签发签名票据的外部系统（KM、组织门户、YD 门户、BI），每方一把密钥，默认关闭 |  |  |
| `sso` | 单点登录 | Sso | 由致远 OA 发起的单点登录 |  |  |
| `emergency_admin` | 应急管理员 | EmergencyAdmin | 独立凭据（Argon2id 口令 + TOTP）的 break-glass 管理员，只能从内网登录 |  |  |
| `administrator` | 管理员 | Administrator | 持有 super_admin 或管理员角色的人；不得通过外部票据进入 |  |  |
| `password` | 口令 | Password | 应急管理员的登录口令，至少 12 位并有复杂度要求 |  |  |
| `access_scope` | 数据范围 | AccessScope | 法人 × 部门 × 项目角色的行可见范围；空范围 = 零行 |  |  |
| `access_grant` | 数据范围授权 | AccessGrant | 数据范围中的一格：法人 × 部门（可含下级）× 项目角色 |  |  |
| `navigation` | 导航 | Navigation | 用户可见的菜单树 |  |  |
| `navigation_item` | 导航项 | NavigationItem | 菜单树中的目录或页面 |  |  |
| `descendant` | 下级单元 | Descendant | 组织树中某单元之下的单元 |  |  |
| `direct_report` | 直接下属 | DirectReport | 汇报线上直接向某人汇报的人；「本人及下属」只算直接下属 |  |  |
| `external_identity` | 外部身份 | ExternalIdentity | 用户账号在某个身份提供方（企业微信、致远登录）中的标识与该账号的绑定，带绑定区间；登录只经此绑定解析用户 |  | ADR-0033（提议）登记，待用语确认 |
| `provider_kind` | 身份提供方类别 | ProviderKind | 外部身份所属的身份提供方类别，如企业微信用户 ID、致远登录名 |  | ADR-0033（提议）登记，待用语确认 |
| `external_subject` | 外部主体标识 | ExternalSubject | 身份提供方给出的用户标识文本（企业微信 userid、致远登录名），只存于外部身份绑定 |  | ADR-0033（提议）登记，待用语确认 |

另有错误码 3 个（kind 为 `error_code`）、技术词 107 个（`common_words`），不在表中。

## platform.integration（`platform-integration.yaml`）

platform.integration 提议术语（proposed，待业务评审，ADR-0018）。

| id | zh | en | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|
| `seeyon` | 致远 | Seeyon | 致远；组织主数据与单点登录验票的外部系统，由集成模块的防腐层读取 |  |  |
| `outbound_delivery` | 外发投递 | OutboundDelivery | 一条待发往外部系统（企业微信）的消息及其发送结果；事务提交后由投递作业发送，失败退避重试，次数用尽进死信 |  |  |
| `recipient` | 收件人 | Recipient | 消息的接收人，用企业微信用户 ID 精确指定；不允许按部门、标签或全员投递 |  |  |
| `source_reference` | 来源对照 | SourceReference | 外部系统或老系统中一个对象的标识与 FORCITIS one 自有 ID 的对应；只在集成层，领域只用自有 ID；同一来源系统、同一类别下来源 ID 与自有 ID 在任一时点各自唯一 |  | ADR-0033 登记；权限码 platform.integration:source-reference:update（授予组织数据管理员）、platform.integration:source-reference:approve（核算组织编码四眼确认，授予业务管理员），授予方式均为 INDIVIDUAL |
| `source_system` | 来源系统 | SourceSystem | 来源对照所属的外部系统，取值为组织源（ORG_SOURCE）、老系统（LEGACY_SYSTEM）、核算系统（ACCOUNTING_SYSTEM），只在集成层与迁入登记中出现 |  | 数据库列叫 source_system，Java 枚举叫 ExternalSource（platform.integration.api），因为 kernel.event.SourceSystem（ONE / MIGRATION）表示事件出自新系统还是历史迁入，是另一回事，不改名 |
| `source_object_kind` | 来源对象类别 | SourceObjectKind | 来源对照所指的自有对象类别（员工、组织单元、岗位、法人主体等），决定自有 ID 指向哪类对象 |  | ADR-0033（提议）登记，待用语确认 |
| `org_source` | 组织源 | OrgSource | 组织与人员的来源系统，来源系统取值 ORG_SOURCE；其员工、组织单元、岗位的标识登记在来源对照里 |  |  |
| `accounting_system` | 核算系统 | AccountingSystem | 接收凭证的核算系统，来源系统取值 ACCOUNTING_SYSTEM；法人在其中的数字编码（核算组织编码）登记在来源对照里 |  |  |
| `rejected_state` | 已拒绝 | RejectedState | 来源对照的确认状态之一：登记的对照被另一个人拒绝，不占自然键约束，不用于出站 |  | 展示用语；对应确认状态 REJECTED。与审批的「驳回」（Reject，platform.approval，审批人对任务给出不同意的结论）不是一回事：这里是另一个人不接受一条登记的来源对照，中文写「已拒绝」，英文命令写 Reject 时不得显示成「驳回」。确认状态的写法：编号规则版本为 PENDING_CONFIRMATION / CONFIRMED，来源对照为 PENDING / CONFIRMED / REJECTED，中文都是待确认 / 已确认 / 已拒绝（ADR-0033 第 2 项第 1 条） |
| `registration` | 登记 | Registration | 由人把一条来源对照录入系统，先处于待确认，等另一个人确认或拒绝；现在只有核算组织编码可以人工登记 |  |  |
| `supersede` | 取代 | Supersede | 已确认的后继对照从生效日起取代生效中的对照：旧对照在后继的生效日结束，两行的来源标识与自有 ID 都不改写 |  |  |
| `evidence` | 取证 | Evidence | 只读核对老系统与 A8 生产数据，用于复刻老行为、迁入对账与新旧等价核对；用专用取证账号，只出计数或聚合，或落 ECS 数据盘，每次登记一行 |  | ADR-0037 登记（数据库登录名 one_evidence_ro），待用语确认 |

另有错误码 7 个（kind 为 `error_code`）、技术词 20 个（`common_words`），不在表中。

## platform.numbering（`platform-numbering.yaml`）

platform.numbering 提议术语（proposed，待业务评审，ADR-0018）。服务于原编号、确认状态、单据类型代码的命名守护。

| id | zh | en | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|
| `legacy_number` | 原编号 | LegacyNumber | 单据迁移前在老系统中的编号，在新系统只读保留并可检索，与新单据对照（ADR-0010 决定 5） |  | 1a 只交付结构与检索接口；各单据的原编号随其切换阶段导入 |
| `confirmation_status` | 确认状态 | ConfirmationStatus | 编号规则是否已由管理员确认；默认模板在确认前为待确认（PENDING_CONFIRMATION），保存即确认（CONFIRMED）。来源对照（platform.integration）沿用同一个词，取值写作 PENDING / CONFIRMED / REJECTED（待确认 / 已确认 / 已拒绝），中文一致、枚举名各随其表（ADR-0033 第 2 项第 1 条） |  |  |
| `document_type_code` | 单据类型代码 | DocumentTypeCode | 单据类型在统一编号格式中的短代码（大写字母 2-4 位），在词表中登记，对应模板占位符 {TYPE} |  | 1a 先登记报价 QT；其余单据随各自阶段登记 |
| `qt` | 报价单据类型代码 | Qt | 报价（DocumentType.QUOTE）的单据类型代码 QT，1b 首个使用方 |  | 例 QT-FST-202610-0001 |
| `ts` | 工时单据类型代码 | Ts | 工时（DocumentType.TIMESHEET）的单据类型代码 TS，沿用种子模板中的前缀字母，正式词表尚无对应词 |  | 例 TS-FST-202610-0001；模板待管理员确认 |
| `jv` | 记账凭证单据类型代码 | Jv | 记账凭证（DocumentType.JOURNAL_VOUCHER）的单据类型代码 JV，沿用种子模板中的前缀字母，正式词表尚无对应词 |  | 例 JV-FST-202610-0001；模板待管理员确认 |
| `counter_scope` | 计数范围 | CounterScope | 编号计数器计数的范围：全局（各法人共用一个计数）或按法人（每个法人各计各的）；范围类别与法人主体成对，全局时无法人，按法人时必有法人 |  | 模板不含 {LE} 时为全局 |
| `counter_scope_kind` | 计数范围类别 | CounterScopeKind | 计数范围的类别：GLOBAL 全局 / LEGAL_ENTITY 按法人 |  |  |

另有错误码 4 个（kind 为 `error_code`）、技术词 3 个（`common_words`），不在表中。

## platform.web（`platform-web.yaml`）

无错误码以外的术语。

另有错误码 5 个（kind 为 `error_code`）、技术词 12 个（`common_words`），不在表中。
