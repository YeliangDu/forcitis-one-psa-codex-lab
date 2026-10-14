# 提议术语清单（第三轮用语确认用）

`terms` 只放业务术语，纯技术词（动词、框架词、值对象方法名等）放同文件的 `common_words`。本表按模块列出业务术语；「疑似方言」取自条目的 note 或别名标注，空表示未发现问题。

修改任何一个 yaml 后运行 `./mvnw -B verify`（GlossaryTest 会校验字段、重名和未登记词）。

## platform.identity（`platform-identity.yaml`）

| id | zh | en | 定义 | 是否疑似方言 |
|---|---|---|---|---|
| `login` | 登录 | Login | 用户以某种凭证建立会话的动作；所有入口过同一道「启用且在职」检查 |  |
| `logout` | 登出 | Logout | 用户主动结束会话；会话在服务端删除 |  |
| `session` | 会话 | Session | 服务端会话（Spring Session JDBC）：空闲 10 小时、绝对上限 24 小时，均可配置 |  |
| `ticket` | 票据 | Ticket | 一次性登录凭据：致远 OA 票据，或外部系统的签名票据 |  |
| `ticket_issuer` | 票据签发方 | TicketIssuer | 被允许签发签名票据的外部系统（KM、组织门户、YD 门户、BI），每方一把密钥，默认关闭 |  |
| `sso` | 单点登录 | Sso | 由致远 OA 发起的单点登录 |  |
| `emergency_admin` | 应急管理员 | EmergencyAdmin | 独立凭据（Argon2id 口令 + TOTP）的 break-glass 管理员，只能从内网登录 |  |
| `administrator` | 管理员 | Administrator | 持有 super_admin 或管理员角色的人；不得通过外部票据进入 |  |
| `password` | 口令 | Password | 应急管理员的登录口令，至少 12 位并有复杂度要求 |  |
| `access_scope` | 数据范围 | AccessScope | 法人 × 部门 × 项目角色的行可见范围；空范围 = 零行 |  |
| `access_grant` | 数据范围授权 | AccessGrant | 数据范围中的一格：法人 × 部门（可含下级）× 项目角色 |  |
| `navigation` | 导航 | Navigation | 用户可见的菜单树 |  |
| `navigation_item` | 导航项 | NavigationItem | 菜单树中的目录或页面 |  |
| `descendant` | 下级单元 | Descendant | 组织树中某单元之下的单元 |  |
| `direct_report` | 直接下属 | DirectReport | 汇报线上直接向某人汇报的人；「本人及下属」只算直接下属 |  |

技术词 104 个，在 `common_words`。

## organization.workforce（`organization_workforce.yaml`）

| id | zh | en | 定义 | 是否疑似方言 |
|---|---|---|---|---|
| `workforce__assignment` | 任职 | Assignment | 员工在某个组织单元、某个岗位上的任职，含直属领导（汇报线）；A8 为源 |  |
| `workforce__reporting_line` | 汇报线 | ReportingLine | 员工到直属领导的上下级关系；同步时校验领导存在、在职、非本人、无环 |  |
| `workforce__sync_diff` | 同步差异 | SyncDiff | 一次同步运行改动的对象与字段，以及发现的数据例外（供数据管理员处理） |  |
| `workforce__shrink_guard` | 缩水保护 | ShrinkGuard | A8 返回的有效部门数或在职人数降幅超过 20 % 时中止同步，等管理员人工放行 |  |
| `workforce__held_run` | 被中止的同步 | HeldRun | 被缩水保护中止、等待管理员放行的同步运行 |  |
| `workforce__isolated_row` | 被隔离的行 | IsolatedRow | 缺部门或缺岗位的人员行：不写入、告警，不中断整批同步 |  |
| `workforce__org_hierarchy` | 组织层级 | OrgHierarchy | 组织单元之间的上下级结构；「本部门及以下」不含已撤销部门 |  |
| `workforce__broken_org_tree` | 断裂的组织树 | BrokenOrgTree | A8 返回的部门缺少上级路径；整次同步中止，什么都不写 |  |
| `workforce__email` | 企业邮箱 | Email | 员工的企业邮箱（A8 人事表） |  |
| `workforce__gender` | 性别 | Gender | 员工性别；A8 未维护或无法识别时为空，不默认写成女 |  |
| `workforce__login_name` | 登录名 | LoginName | 员工在组织源的登录名（手机号）；没有即离职（RULE-363） |  |
| `workforce__short_name` | 部门简称 | ShortName | 组织单元简称；A8 视图里没有，每天从老系统只读刷新 |  |
| `workforce__staff_member` | 人员 | StaffMember | 一名员工连同其任职，是同步比较与写入的单位 |  |
| `workforce__relationship` | 关系 | Relationship | A8 中人员与组织单元之间的关系（部门负责人、区域应收负责人），类型 ID 为配置项 |  |
| `workforce__headquarters` | 总部 | Headquarters | 组织单元的总部 / 区域类别中的「总部」 |  |
| `workforce__revoked` | 已撤销 | Revoked | A8 不再返回的组织单元；与「停用」是两个不同状态（RULE-355） |  |

技术词 63 个，在 `common_words`。

## platform.approval（`platform-approval.yaml`）

| id | zh | en | 定义 | 是否疑似方言 |
|---|---|---|---|---|
| `act` | 办理 | Act | 对任务执行认领、通过、驳回、退回等操作 |  |
| `advisory` | 建议性 | Advisory | 加签人的驳回只记录意见、不终止流程（RULE-291，1b） |  |
| `assigned` | 已分配 | Assigned | 任务已分配给候选组（TaskAssigned） |  |
| `assignee` | 处理人 | Assignee | 认领后唯一可以办理任务的人（RULE-276，I-7） |  |
| `claimed` | 已认领 | Claimed | 任务已被候选人认领 |  |
| `completed` | 已完成 | Completed | 审批已结束（ApprovalCompleted） |  |
| `decide` | 办理决定 | Decide | 通过或驳回任务 |  |
| `decision` | 决定 | Decision | 对一个任务的通过或驳回 |  |
| `department` | 部门 | Department | 组织单元；审批规则按部门取负责人、总监、区域应收 |  |
| `director` | 总监 | Director | BU-002 部门总监（RULE-256） |  |
| `escalated` | 已升级 | Escalated | 候选集已按规则上推（汇报线不完整、防自审） |  |
| `fallback` | 兜底 | Fallback | 取不到人时转流程管理员 |  |
| `form` | 表单 | Form | 业务单据的表单数据 |  |
| `inbox` | 待办收件箱 | Inbox | 合并新旧系统待办的列表（RULE-261） |  |
| `live` | 在途 | Live | 审批仍在进行或退回待重提 |  |
| `resulted` | 已出结果 | Resulted | 一个任务已办理（TaskResulted） |  |
| `return` | 退回 | Return | 退回发起人（RULE-275） |  |
| `round` | 轮次 | Round | 同一单据第几次发起（RULE-278） |  |
| `row` | 行 | Row | 行级任务（RULE-261） |  |
| `running` | 进行中 | Running | 审批正在进行 |  |
| `terminate` | 终止 | Terminate | 终止流程实例（顶层驳回，RULE-271） |  |
| `todo` | 待办事项 | Todo | 待处理任务 |  |
| `resubmit` | 重新提交 | Resubmit | 退回后由发起人重新提交（I-5） |  |
| `notification` | 通知 | Notification | 企业微信通知（模块内接口隔离，架构 3.7） |  |
| `skipped` | 已跳过 | Skipped | 收件人缺企业微信 ID，跳过并告警（I-6） |  |
| `project_manager` | 项目经理 | ProjectManager | 工时所属工作的项目经理；工时审批的第一道审批人（表单字段 projectManagerId，取自业务单据） |  |
| `regional_head` | 区域负责人 | RegionalHead | 区域组织单元的负责人；含售前工时的工时单还要经他审批（表单字段 regionalHeadId） |  |
| `skip` | 跳过 | Skip | 节点显式允许且确实没有审批人时，不创建任务直接放行（RULE-300） |  |

技术词 34 个，在 `common_words`。

## platform.integration（`platform-integration.yaml`）

| id | zh | en | 定义 | 是否疑似方言 |
|---|---|---|---|---|
| `seeyon` | 致远 | Seeyon | 致远；组织主数据与单点登录验票的外部系统，由集成模块的防腐层读取 |  |
| `outbound_delivery` | 外发投递 | OutboundDelivery | 一条待发往外部系统（企业微信）的消息及其发送结果；事务提交后由投递作业发送，失败退避重试，次数用尽进死信 |  |
| `recipient` | 收件人 | Recipient | 消息的接收人，用企业微信用户 ID 精确指定；不允许按部门、标签或全员投递 |  |

技术词 8 个，在 `common_words`。

## platform.numbering（`platform-numbering.yaml`）

| id | zh | en | 定义 | 是否疑似方言 |
|---|---|---|---|---|
| `legacy_number` | 原编号 | LegacyNumber | 单据迁移前在老系统中的编号，在新系统只读保留并可检索，与新单据对照（ADR-0010 决定 5） |  |
| `import_batch` | 导入批次 | ImportBatch | 写入一批原编号对照的导入批次标识，便于整批核对与回溯 |  |
| `confirmation_status` | 确认状态 | ConfirmationStatus | 编号规则是否已由管理员确认；默认模板在确认前为待确认（PENDING_CONFIRMATION），保存即确认（CONFIRMED） |  |
| `document_type_code` | 单据类型代码 | DocumentTypeCode | 单据类型在统一编号格式中的短代码（大写字母 2-4 位），在词表中登记，对应模板占位符 {TYPE} |  |
| `qt` | 报价单据类型代码 | Qt | 报价（DocumentType.QUOTE）的单据类型代码 QT，1b 首个使用方 |  |



## cost.timesheet（`cost-timesheet.yaml`）

| id | zh | en | 定义 | 是否疑似方言 |
|---|---|---|---|---|
| `time_entry_category` | 工时类别 | TimeEntryCategory | 工时按用途的分类，取值为项目工时、售前工时、OH 工时；决定工时单走哪些审批分支 |  |
| `project_hours` | 项目工时 | ProjectHours | 工时类别的取值：记在项目工作上的工时 |  |
| `pre_sales_hours` | 售前工时 | PreSalesHours | 工时类别的取值：记在售前工作上的工时 | 待确认（尚非正式用语） |
| `overhead_hours` | OH 工时 | OverheadHours | 工时类别的取值：记在间接（管理）工作上的工时 | 是（疑似方言，第三轮用语确认） |



## kernel（`kernel.yaml`）

无业务术语；7 个技术词在 `common_words`。

## 前端界面用语（`frontend-ui.yaml`）

| id | zh | en | 定义 | 是否疑似方言 |
|---|---|---|---|---|
| `pending_task` | 待办 | PendingTask | 当前用户待处理的审批任务，新系统任务与老系统待办合并后在待办页展示 |  |
| `candidate_approver` | 候选审批人 | CandidateApprover | 任务尚未认领时，有资格认领并办理该任务的审批人 |  |
| `task_claim` | 认领 | TaskClaim | 候选审批人把任务认到自己名下，认领后只有他可以办理 |  |
| `approve` | 通过 | Approve | 审批人对任务给出同意的结论 |  |
| `reject` | 驳回 | Reject | 审批人对任务给出不同意的结论，是否终止流程取决于所在节点 |  |
| `legacy_system` | 老系统 | LegacySystem | 被本系统替代的原项目管理系统（待办合并期间只读接入） | 是（疑似方言，第三轮用语确认；界面用语，正式叫法待定） |
| `new_system` | 新系统 | NewSystem | FORCITIS one 本身；待办合并期间与老系统并存，界面用来标出待办的来源 | 待确认（用户指定提议） |
| `org_browse` | 组织浏览 | OrgBrowse | 只读浏览组织单元树的页面 | 待确认（用户指定提议） |
| `current_node` | 当前节点 | CurrentNode | 审批任务当前所在的流程节点 | 待确认（用户指定提议） |
| `trigger_mode` | 触发方式 | TriggerMode | 一次同步是定时触发还是手动触发 | 待确认（用户指定提议） |
| `held` | 被中止 | Held | 同步记录的状态：被缩水保护中止，等待管理员放行；与「被中止的同步」指同一状态 | 待确认（用户指定提议） |
| `release` | 放行 | Release | 管理员核对同步差异后，让被中止的同步生效的动作 |  |
| `org_sync` | 同步 | OrgSync | 把组织来源的组织单元与员工变化应用到本系统的一次运行 |  |
| `delivery_unit` | 交付单元 | DeliveryUnit | 承担项目交付的组织单元 |  |
| `serial_number` | 流水号 | SerialNumber | 编号中随单据递增的序号部分 |  |
| `start_serial_number` | 起始流水号 | StartSerialNumber | 编号规则中流水号的起始值 |  |
| `issue_number` | 发号 | IssueNumber | 按编号规则为一张单据分配编号 |  |
| `user_account` | 用户账号 | UserAccount | 一名员工在本系统的登录主体，可启用或停用 |  |
| `portal` | 门户 | Portal | 公司门户，致远单点登录的入口 |  |
| `totp_code` | 动态口令 | TotpCode | 应急管理员登录时的一次性动态口令（TOTP） |  |
| `authorization` | 授权 | Authorization | 向用户授予权限，或企业微信确认用户身份的授权 |  |
| `reason` | 理由 | Reason | 审批人办理任务时填写的说明；驳回和退回必填 |  |
| `business_check` | 业务校验 | BusinessCheck | 办理审批前由业务模块执行的校验，不通过则不能办理 |  |
| `task_participant` | 参与人 | TaskParticipant | 审批任务的候选审批人或处理人 |  |
| `account_disabled` | 停用 | AccountDisabled | 账号被管理员停用，停用后不能登录，已有会话随即失效 |  |
