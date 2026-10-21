# B-31 行政事务（organization.administration）用语差距清单

> 附录说明：本报告成文于 2026-10-21 的 one-architecture、one-data、one-rules 裁定之前，是只读核对的原始记录；裁定已吸收的结果见 `B31_CONTRACT.md` §6，数字与结论以契约文档和裁定文件为准。

基线：origin/main 526a906a5。依据：`B31_CONSTRUCTION_DESIGN.md`（下称「设计」，行号为该文件行号）、`B31_RULES.yaml`、`terms.yaml`、`bcs.yaml`、`backend/glossary/proposed/*.yaml`、NAMING_STANDARD §2.1–2.4、§2.6、§3、§4。
提议文件：`backend/glossary/proposed/organization-administration.yaml`（新建，未改任何已有文件；`proposed/README.md` 是生成产物，需 `python3 docs/modernization/build_all.py` 重新生成，我没有运行）。

## 0. 数量与结论

| 类别 | 数量 | 说明 |
|---|---|---|
| 已登记且与设计一致，可直接用 | 28 个词条（另有标准动作与 4 个 bc 标识） | 见 1.1 |
| 已登记但设计用了旧称、另一英文名或被词表禁用的写法 | 22 项不一致 | 见 1.2，其中 3 项是被词表禁用的中文写法（兜底、已完成、参与人） |
| 查不到，已写入提议文件 | 170 条 + 1 个技术词 | 116 条非错误码（实体 16、值对象 18、聚合 4、角色 7、业务规则 5、读模型 1、事件 7、命令 33、状态 25）、54 个错误码；其中 8 条使用范围为 acl（过渡期） |
| 查不到但属其他 bc，未写入提议文件 | 9 项 | 见 1.4 |
| 交 one-glossary 判定的问题 | 18 个 | 见第 3 节 |

关键结论：
1. 设计里 5 个聚合的名字有 4 个与词表不一致（只有员工活动申请完全一致），设计 §8.2 第 1 条「未找到 bc=organization.administration 的词条」已过期，现有 5 个已批准聚合词条（命名第 3、4 批确认页写回）。
2. 设计 §3 的表名全是草案，按词表应改为 `seal_usage_requests`、`it_service_tickets`、`it_asset_usage_*`，见 2.1。
3. 设计 §11 的「兜底」、§3.6 的「已完成」「参与人」被词表禁用，必须改写或由 one-glossary 裁定。

## 1. 逐项查词结果

### 1.1 已登记且一致（直接用）

| 设计里的概念 | 词条 id | zh / en | kind | bc | 状态 |
|---|---|---|---|---|---|
| 员工活动申请（AGG-116，表 `employee_activity_requests`） | `employee_activity_request` | 员工活动申请 / EmployeeActivityRequest | aggregate | organization.administration | approved |
| 用印法人、法人 | `legal_entity` | 法人主体 / LegalEntity | aggregate | organization.workforce | approved |
| 员工、申请人身份引用 | `employee` | 员工 / Employee | aggregate | organization.workforce | approved |
| 原组织单元 | `org_unit` | 组织单元 / OrgUnit | entity | organization.workforce | approved |
| 岗位资产配置标准的「岗位」 | `position` | 岗位 / Position | value_object | organization.workforce | approved |
| 审批人 | `approver` | 审批人 / Approver | role | platform.approval | approved |
| 审批实例、来源审批实例 | `approval_instance` | 审批实例 / ApprovalInstance | aggregate | platform.approval | approved |
| 审批轮次（`round_number`） | `approval_round` | 审批轮次 / ApprovalRound | entity | platform.approval | approved |
| 消费的审批完成事件 | `approval_completed` | 审批已完成 / ApprovalCompleted | domain_event | platform.approval | approved |
| 季度复核、月度抽检待办 | `todo` | 待办 / Todo | entity | platform.approval | approved |
| 金额（活动预估） | `money` | 金额 / Money | value_object | kernel | approved |
| 业务日期 | `business_date` | 业务日期 / BusinessDate | value_object | kernel | approved |
| 单据编号 | `document_number` | 单据编号 / DocumentNumber | value_object | platform.numbering | approved |
| 单据类型（编号登记） | `document_type` | 单据类型 / DocumentType | state | platform.numbering | approved |
| 老系统编号映射（与老系统审批单号不同） | `legacy_number` | 原编号 / LegacyNumber | entity | platform.numbering | approved |
| 流程标识（G47） | `process_key` | 流程标识 / ProcessKey | state | platform.approval | approved |
| 合同 / 协议（借阅对象） | `contract`、`agreement` | 合同 / 协议 | aggregate / entity | contract.agreement | approved |
| 报价（用印关联） | `quote` | 报价单 / Quote | aggregate | presales.opportunity | approved |
| 状态历史表 `state_transitions` | `state_transition` | 状态转换记录 / StateTransition | entity | platform.eventing | proposed |
| 文件版本、内容哈希、关联 | `stored_file`、`content_hash`、`file_lineage`、`bind_file` | 存储文件、内容哈希、版本与派生、关联文件 | entity / value_object / command | platform.file | proposed |
| 权限动作 | `standard_actions` | read create update delete submit approve reject export import execute | （backend/glossary/terms.yaml） | — | — |
| bc 标识 | `organization.administration`、`platform.signing`、`platform.file`、`platform.approval` | 行政事务、签章服务（SigningService）、文件服务（FileService）、流程与审批 | bc | bcs.yaml | 已登记 |
| 审批平台的通用命令 | `withdraw`、`reject`、`return` | 撤回、驳回、退回 | command | platform.approval | approved（语义与 B-31 的同名状态不同，见 M11、M12） |
| 登记（动词） | `registration` | 登记 / Registration | command | platform.integration | proposed |

### 1.2 已登记但不一致（交 one-glossary 与 one-architecture）

「设计位置」为 `B31_CONSTRUCTION_DESIGN.md` 行号，除非另写。

| 编号 | 设计里的写法 | 词表现状 | 问题 | 建议处理 | 交谁 |
|---|---|---|---|---|---|
| M1 | 「IT 需求工单」（L41、L213）、「IT 需求」（L134、L226、L344）、`it_requests`、`it_request_handlings`（L234–235）、`ItRequest`（b31.notes）、事件「IT 需求已解决」、doc_type「IT需求审批」 | `it_service_ticket` 信息技术服务工单（approved，命名第 4 批 NM4-03），「IT 需求工单」为已废弃旧称 | 全文用旧称与旧英文名 | 全文改称；表 `it_service_tickets`、`it_service_ticket_handlings`；事件 ItServiceTicketResolved；doc_type 见 M18 | one-architecture 改设计，one-glossary 确认事件与 doc_type 中文名 |
| M2 | 「IT 资产使用记录」（L241）、`it_asset_use_ledgers`、`it_asset_use_requests`、`it_asset_use_periods`、`AssetUseLedger` | `it_asset_usage_record` 信息技术资产使用记录；「IT 资产使用记录」是方言且已禁用，「信息技术设备使用记录」已废弃 | 旧称；词干 use 与 usage 不一致；根在设计里叫 ledger（台账），词表聚合叫 record（记录） | 表改 `it_asset_usage_records`（根，每资产一条）、`it_asset_usage_requests`、`it_asset_usage_periods`；子表术语已在提议文件 | one-architecture 确认每资产一根，one-glossary 确认根的名字 |
| M3 | AGG-115 只处理公司资产卡片上的设备（L241–L267） | `it_asset_usage_record` 定义「实物设备和软件许可…区分设备与软件许可」（NM4-08） | 词表聚合范围比设计宽，软件许可无落点 | 二选一：设计补软件许可；或词表定义收窄 | one-architecture + one-glossary |
| M4 | 「IT 资产维修单」（L271） | `it_asset_repair_order`，zh「信息技术设备维修单」，en ItAssetRepairOrder；「IT 资产维修单」为方言已禁用 | 词条自身 zh 用「设备」、en 用 Asset；NM4-08 把使用记录改为「资产」后维修单未跟改 | 旧称改掉；zh 是否改「信息技术资产维修单」请裁定 | one-glossary |
| M5 | `seal_applications`、`seal_application_documents`、`seal_application_source_documents`（L204–206）、`SealApplication`、`WithdrawSealApplication`、状态机名 `seal_application` | `seal_usage_request` 用印申请，「用印申请记录」「SealUsageRequestRecord」已禁用 | 英文名与表名不是词表名 | 表 `seal_usage_requests`、`seal_usage_documents`、`seal_usage_source_documents`；命令 SubmitSealUsageRequest 等 | one-architecture |
| M6 | 实际用印登记表 `seal_usage_records`、`SealUsageRecord`、「用印登记行」 | 查不到 | 词表没有这个对象；设计与 b31.notes 沿原稿提议但未登记 | 已提议 `seal_usage_registration` 用印登记 / SealUsageRegistration，备选 用印记录 / SealUsageRecord | one-glossary 选 |
| M7 | 「资产卡片」「固定资产卡片」`fixed_asset_card_id`（L73、L149、L243、L261、L279） | `company_asset` 公司资产（accounting.fixedasset，聚合稿 AGG-91） | 词表里没有「资产卡片」，被引用对象叫公司资产 | 列名 `company_asset_id`，文字改称公司资产 | one-architecture |
| M8 | 「兜底」「兜底审批人」「兜底保管人」（L706、L723、L730–735，§11 第 2 条） | `administrator_fallback` 转流程管理员，「兜底」被禁用（forbidden） | 禁用写法；前端用语守护会拦 | 已提议 `backup_approver` 备用审批人 / BackupApprover，备选 替补审批人；需统一三个词，见 Q1 | one-glossary |
| M9 | 员工活动状态「已完成」（L290–L291，命令「完成员工活动」） | `approval_completed` 审批已完成，「已完成」被禁用（forbidden），旧词条 `round3_pending__completed` 仍让界面暂时通过 | 状态名与禁用写法冲突，待改名条目删除后界面守护会拦 | 我没有登记「已完成」；请裁定员工活动完成态的中文名 | one-glossary |
| M10 | 「参与人」、`employee_activity_participants`（L299、L382） | `task_participant` 任务参与人，「参与人」被禁用 | 界面不得单写「参与人」 | 已提议全称「员工活动参与人」；界面一律写全称 | one-glossary 确认 |
| M11 | 状态「审批中」「已驳回」「已退回」「已通过」「已撤销」 | 同中文名已在 platform.approval 登记为 `ui_approval_running`、`ui_approval_rejected`、`ui_approval_returned`、`ui_approval_approved`，「已撤销」在 organization.workforce 登记为 `workforce__revoked`（组织单元的状态） | 同名；B-31 的含义不全相同（例：资产领用「已通过」是规则通过，不是审批通过；「已退回」既指审批驳回也指处理人退回） | 因跨 bc 同名须在已批准词条上互列 homonyms，我无权改 terms.yaml，故这 5 个不登记；英文枚举取 IN_APPROVAL、REJECTED、RETURNED、APPROVED、REVOKED，单词都已登记 | one-glossary 判定同概念复用还是同名异义 |
| M12 | 「撤回」得到状态「已撤回」，与「已退回」并存（L186–L192、L372） | `withdraw` 撤回：发起人拉回在途审批，本轮以退回（RETURNED）结束，对应状态「已退回」 | 审批平台里撤回就是退回；B-31 要把撤回与退回分成两个状态 | 设计 §4.3 已提示映射待定；请 one-architecture 在接入契约中定 | one-architecture + one-glossary |
| M13 | 消费「B-13 离职事件」「Separated」（L140，events.yaml） | `separated` 已离职是 value_object（在职状态取值）；`account_separated` 是身份侧事件；没有人事侧「离职生效」事件；「离职日期」被禁用 | 消费的事件没有词条 | 他 bc 缺口（见 1.4），B-31 列名用 `separated_at`（离职生效时点） | one-glossary + 人事负责会话 |
| M14 | IT 工单「处理人」`handler_id`（L234） | `assignee` 处理人（platform.approval，认领后唯一可办理任务的人） | 同中文名，含义不同 | 我没有登记「处理人」；请裁定 | one-glossary |
| M15 | 事件「资产已领用 / 已调拨 / 已归还」（L135）；「用印已办理」英文空缺 | 无；公司资产（company_asset）侧有自己的资产事件 | 去掉「信息技术」会与公司资产事件同义；「用印已办理」由命令「登记用印」发出，英文按命名规范应为 SealUsageRegistered，中文动词却是「办理」 | 已提议 信息技术资产已领用 / ItAssetIssued 等；用印已办理 / SealUsageRegistered（备选 用印已登记） | one-glossary |
| M16 | 「印章类型」（L71、§3.7，ADR-0064 L46–L49）与词条 `seal_usage_request` 定义里的「印章类别」 | 签章服务用语尚未登记（ADR-0064 L138：印章台账、印章类型等 10 个词交 one-glossary） | 同一概念两个词 | 归签章服务，B-31 只引用，见 1.4 | one-glossary |
| M17 | 合同原件借阅：§1.1（L66）写「属于 B-05，不并入 B-31」；§10 Q8 写「先记在行政事务」 | bcs.yaml 对 organization.administration 的说明写「合同借阅与归档归 contract.agreement」；聚合稿 AGG-13 用「协议原件借出」「协议借阅已逾期」 | 设计内部自相矛盾；bc 归属与协议/合同用词都不一致 | 提议文件把它登记在 organization.administration 并在 note 里写明；请裁定 bc 与用词 | one-architecture + one-glossary |
| M18 | 只列两个审批 doc_type「用印审批」「IT需求审批」（L44、L344、L629） | `process_key`/`DocumentType` 中没有行政类取值（L384） | Q9、Q10 的借阅审批实际需要第三个类型；doc_type 中文名带旧称「IT需求」 | 已提议 用印审批、信息技术服务审批、合同原件借阅审批 三个值对象；先例 `timesheet_approval` 放在 platform.approval | one-glossary 定 bc 与中文名 |
| M19 | 设计 §8.2 第 1 条（L603）称无 bc=organization.administration 词条 | 已有 5 个 approved 聚合词条；bcs.yaml 对该 bc 的说明还列了「报废、盘点、采购申请」，设计没有 | 设计过期；bc 说明比设计宽 | 设计 §8.2 改写；bcs.yaml 说明请核对 | one-architecture + one-glossary |
| M20 | 周年通知接收：B31_RULES ZN-06「通知行政」；设计 §10 清单「员工关系负责人」 | 无词条 | 接收角色两种说法 | 待行政确认；不入词表 | one-architecture |
| M21 | 替补（IT-04、§9）、兜底（§11）、委派（AR-12 缺位委派） | — | 三个词表达同一类事 | 见 Q1 | one-glossary |
| M22 | 撤回、撤销、取消、作废、废止 | `withdraw` 撤回、`workforce__revoked` 已撤销；本文提议已取消、已作废 | 5 个动词近义，设计按对象各用一个 | 见 Q14 | one-glossary |

### 1.3 查不到、已写入提议文件的词（170 条 + 1 技术词）

只列分组与要点；全文见提议文件，每条带定义、设计出处、备选英文（note）。

| 组 | 条数 | 内容 |
|---|---|---|
| 用印申请子实体与配置 | 11 | 用印文件、用印来源单据、用印登记、用印用途、用印审批人表（聚合）、用印审批人表行、用印特批、高风险用途、预盖空白页、预盖张数上限、用印审批（doc_type） |
| 用印过渡通道（scope: acl，过渡期） | 5 | 用印过渡通道、老系统审批来源、老系统审批单号、过渡通道季度复核、过渡通道月度抽检 |
| 审批人与角色 | 7 | 第一审批人、备用审批人、申请人、提出人、资产使用人、报修人、借阅人 |
| 信息技术服务工单子实体与配置 | 6 | 工单处理记录、服务类别表（聚合）、服务类别、服务优先级、权限有效期、信息技术服务审批（doc_type） |
| 资产使用与维修 | 11 | 使用申请、使用期间、资产交接、归还验收、设备实物状态、岗位资产配置标准（聚合）及其行、超配、离职归还提醒、故障描述、维修结果 |
| 员工活动 | 6 | 活动参与人、活动类型、周年贺卡、周年日期、周年生成计划（读模型）、活动费用预估 |
| 合同原件借阅 | 5 | 借阅记录（聚合）、借阅审批（doc_type）、外借、续借、借阅逾期提醒 |
| 事件 | 7 | 用印申请已批准、用印已办理（SealUsageRegistered）、信息技术服务工单已解决、信息技术资产已领用 / 已调拨 / 已归还、员工活动申请已批准 |
| 命令 | 33 | 设计与命令目录已列 25 个；推出 8 个（重开、放弃剩余用印份数、预盖三个、核对老系统批准页、复核登记、抽检登记，confidence 均为 low） |
| 状态取值 | 25 | 草稿 已批准 已撤回 已用印 已关闭 已提交 处理中 已解决 已领用 已申请 已调拨 已转领用 在用 已结束 已归还 因归还结束 因调拨结束 已报修 已维修登记 已修复 无法修复 已取消 已登记 已收回 已作废；同一 bc 内中英文名唯一，所以五个聚合共用一个词条，M11 所列 5 个同名词不重复登记 |
| 错误码 | 54 | 见提议文件，id 为 `error__organization_administration_*`，中文名与现有 178 个错误码均不重名；confidence 为 low，实施卡可裁剪 |
| 技术词 | 1 | `cancellation`（权限资源 `employee-activity-cancellation` 用） |

### 1.4 查不到、属其他 bc、未写入提议文件

| 缺口 | B-31 用在哪 | 应归 | 建议 |
|---|---|---|---|
| 入职日期（hire date） | 周年生成（§5.3）、列 `hire_date`；设计 §8.3 已说 EmployeeProfile 没有入职日期 | organization.workforce | 登记 `hire_date` 入职日期；我的错误码 HIRE_DATE_UNAVAILABLE 暂时让单词 hire 通过，不能当作入职日期词条 |
| 人事侧离职生效事件 | 离职归还提醒的触发（§5.2） | organization.workforce | 登记领域事件，或确认复用现有事件 |
| 报价版本（QuoteVersion） | 用印登记的报价版本引用、`quote_version_id` | presales.opportunity | 登记 |
| 印章类型、印章台账、台账条目及其版本 | 用印申请引用台账条目（§3.7） | platform.signing | ADR-0064 L138 已交 one-glossary，名字（SealType / SealLedger / 台账条目）待定 |
| 印章保管人（A/B 角） | §10 清单、§11 | platform.signing | 归台账条目的保管部门与保管人引用 |
| 电子用印（命令）、签前件、签后件、签章记录 | 先行期之后（§1.3） | platform.signing | 本期不用，不提议 |
| 岗位级「第二人」「改派」 | 备用审批人改派 | platform.approval | 解析别名 cost_object_owner 的改派语义由审批平台定 |
| 资产类别（信息技术资产类别） | 卡片读取的过滤条件 | accounting.fixedasset | 资产类别与折旧政策版本在聚合稿 AGG-124，未登记 |
| 提醒投递 | 通知失败重试 | platform.integration | 已有投递能力，不新增 |

## 2. 命名对照

### 2.1 表（schema `organization_administration`，设计 §3 草案名 → 按词表应为）

| 设计草案名 | 应为 | 依据 |
|---|---|---|
| `seal_applications` | `seal_usage_requests` | M5 |
| `seal_application_documents` | `seal_usage_documents` | 提议词条 |
| `seal_application_source_documents` | `seal_usage_source_documents` | 提议词条 |
| `seal_usage_records` | `seal_usage_registrations`（只追加，注册表 APPEND_ONLY） | M6，待选 |
| `it_requests` | `it_service_tickets` | M1 |
| `it_request_handlings` | `it_service_ticket_handlings` | M1 |
| `it_asset_use_ledgers` | `it_asset_usage_records` | M2 |
| `it_asset_use_requests` | `it_asset_usage_requests` | M2 |
| `it_asset_use_periods` | `it_asset_usage_periods` | M2 |
| `it_asset_repair_orders` | 同名，已一致 | 已登记 |
| `employee_activity_requests` | 同名，已一致 | 已登记 |
| `employee_activity_participants` | 同名（界面写全称） | M10 |
| `employee_activity_types` | 同名 | 提议词条 `employee_activity_type` |
| `state_transitions` | 同名 | 提议词条 `state_transition`（platform.eventing） |
| 配置型版本表 | `seal_usage_approver_tables` + `seal_usage_approver_lines`；`it_service_category_tables` + `it_service_categories`；`position_asset_standards` + `position_asset_standard_lines` | 提议词条 |
| §10–§11 新对象 | `blank_page_pre_stamps`、`legacy_approval_sources`、`transition_channel_reviews`、`transition_channel_spot_checks`、`contract_original_loan_records`、`contract_original_loan_renewals`、`anniversary_generation_plans` | 提议词条 |

配置版本引用的去向设计没有给表名：用印制度配置、领用规则与清单、活动与周年规则、提醒配置、报价核验配置。须 one-data 冻结后再登记（设计 §8.3 配置承载），我没有为它们造词。

### 2.2 关键列

| 设计列 | 建议 | 说明 |
|---|---|---|
| `fixed_asset_card_id` | `company_asset_id` | M7 |
| `seal_application_id`、`it_request_id`、`it_asset_use_ledger_id` | 随表名改 | M1、M2、M5 |
| `receiving_user_id` | 改名（如 `to_user_id`） | 单词 receiving 未登记 |
| `return_reminded_at` | 改名（如 `return_reminder_sent_at`） | 单词 reminded 未登记，sent 已登记 |
| `separated_at` | 保留 | 离职生效时点；不要用 `separation_date`（被禁用） |
| `hire_date` | 待人事登记入职日期 | 1.4 |
| `priority_code` | 值域缺 | 设计未给取值 |
| 其余（`requested_count`、`approved_count`、`used_count`、`handled_at`、`registration_key`、`handling_key`、`cancel_reason`、`cancelled_by`、`cancelled_at`、`generation_due_at`、`return_reminder_due_at` 等） | 单词均已登记 | 已逐词核对 |

### 2.3 状态枚举（`*Status`，存枚举名）

| 枚举 | 取值（代码 / 中文） |
|---|---|
| SealUsageRequestStatus | DRAFT 草稿、IN_APPROVAL 审批中（M11）、APPROVED 已批准、STAMPED 已用印、CLOSED 已关闭、REJECTED 已驳回（M11）、WITHDRAWN 已撤回 |
| ItServiceTicketStatus | DRAFT、IN_APPROVAL、SUBMITTED 已提交、IN_PROGRESS 处理中、RESOLVED 已解决、CLOSED、RETURNED 已退回（M11）、WITHDRAWN |
| 资产领用申请 | DRAFT、已通过（M11，建议代码 APPROVED，含义是规则通过）、ISSUED 已领用 |
| 资产调拨申请 | DRAFT、REQUESTED 已申请、TRANSFERRED 已调拨、CONVERTED_TO_ISSUE 已转领用、REVOKED 已撤销（M11） |
| ItAssetUsagePeriodStatus | IN_USE 在用、ENDED 已结束；结束原因 ENDED_BY_RETURN 因归还结束、ENDED_BY_TRANSFER 因调拨结束；归还结局 RETURNED 已归还 |
| ItAssetRepairOrderStatus | DRAFT、FAULT_REPORTED 已报修、REPAIR_REGISTERED 已维修登记；维修结果 REPAIRED 已修复、UNREPAIRABLE 无法修复 |
| EmployeeActivityRequestStatus | DRAFT、APPROVED（规则通过）、已完成（M9，代码 COMPLETED 的中文名待裁定）、CANCELLED 已取消 |
| 预盖空白页状态 | REGISTERED 已登记、RECLAIMED 已收回、VOIDED 已作废 |
| 申请类型 | ISSUE 领用、TRANSFER 调拨（取值单词均已登记，不另立词条） |

### 2.4 事件与消费

发布 7 个：见 1.3「事件」。消费：`ApprovalCompleted`（已登记，结果 APPROVED / REJECTED / RETURNED / CANCELLED 的映射待定，设计 §4.3）；人事离职事件（1.4）。活动取消不发事件（设计 §2.3）。

### 2.5 审批 doc_type 与流程标识

| 设计 | 建议值对象 | 说明 |
|---|---|---|
| 用印审批 | `seal_usage_approval` 用印审批 / SealUsageApproval | 已提议 |
| IT需求审批 | `it_service_approval` 信息技术服务审批 / ItServiceApproval | 已提议，中文名不再带旧称「IT需求」，请确认 |
| （设计未列）合同原件借阅审批 | `contract_original_loan_approval` | 已提议，M18 |
| 资产、维修、员工活动 | 无审批 doc_type | 设计 §4.1 明确 |
| 流程标识候选（G47，`{子域}_{BC}_{snake}`） | `organization_administration_seal_usage_approval`、`organization_administration_it_service_approval`、`organization_administration_contract_original_loan_approval` | 单词均可由提议词条覆盖 |

### 2.6 权限码（设计 §6.1 的中文占位符 → 资源 kebab 英文名候选 + 标准动作）

格式 `organization.administration:{资源}:{动作}`，动作只取 read create update delete submit approve reject export import execute；不自造 withdraw / transfer / complete / cancel。

| 设计占位符 | 资源候选 | 动作 |
|---|---|---|
| 用印申请资源 | `seal-usage-request` | read create update submit |
| 用印撤回资源 | `seal-usage-request-withdrawal` | execute |
| 实际用印登记资源 | `seal-usage-registration` | read create |
| IT需求资源 | `it-service-ticket` | read create update submit |
| IT受理资源 | `it-service-ticket-acceptance` | execute |
| IT解决资源 | `it-service-ticket-resolution` | execute |
| IT退回资源 | `it-service-ticket-return` | execute |
| IT关闭资源 | `it-service-ticket-closure` | execute |
| IT撤回资源 | `it-service-ticket-withdrawal` | execute |
| 资产领用资源 | `it-asset-issue` | read create execute |
| 资产调拨资源 | `it-asset-transfer` | read create execute |
| 调拨撤销资源 | `it-asset-transfer-revocation` | execute |
| 资产归还资源 | `it-asset-return` | execute |
| 资产维修资源 | `it-asset-repair` | read create execute |
| 员工活动资源 | `employee-activity-request` | read create |
| 活动完成资源 | `employee-activity-completion` | execute |
| 活动取消资源 | `employee-activity-cancellation` | execute |

设计未列、§10–§11 带来的资源（候选，随施工卡定）：`seal-usage-approver-table`、`it-service-category-table`、`employee-activity-type`、`position-asset-standard`（均 read create approve，approve 表示定版）；`blank-page-pre-stamp`（read create execute）；`legacy-approval-source`（read execute，核对）；`transition-channel-review`、`transition-channel-spot-check`（read create）；`contract-original-loan-record`（read create execute）。资源词均已登记，仅 `cancellation` 需加入技术词（已写入）。印章台账维护权限归签章服务，不在此列。

### 2.7 定时作业与锁名（G46：`organization.administration.{小写点分}`，单词须登记）

| 作业 | 锁名候选 |
|---|---|
| 离职资产归还提醒 | `organization.administration.return.reminder.send` |
| 周年生成 | `organization.administration.anniversary.generation` |
| 权限工单到期 | `organization.administration.access.validity.expire` |
| 合同借阅逾期提醒（两级） | `organization.administration.loan.overdue.remind` |
| 过渡通道季度复核待办 | `organization.administration.transition.channel.review.todo` |
| 过渡通道月度抽检待办 | `organization.administration.transition.channel.spot.check.todo` |

### 2.8 错误码

54 个已写入提议文件，格式 `ORGANIZATION_ADMINISTRATION_{REASON}`，id 为 `error__organization_administration_{reason}`。按组：用印 24（含预盖 3、过渡通道 5）、信息技术服务 6、资产与维修 11、员工活动 7、合同借阅 6。通用类拒绝（版本冲突、请求非法）不重复定义，沿用 platform.web；「审批回调无法对应单据」「审批结果暂不支持」两条含义通用，是否应放审批模块见 Q15。

## 3. 要交 one-glossary 判定的问题

按影响排序；「推荐」是我的倾向，不是裁定。

1. **兜底 / 替补 / 委派**：设计 §11 用「兜底」（列名、审批人解析），词表禁用「兜底」（`administrator_fallback`）。推荐：「备用审批人」（已提议），并统一 IT-04 的「替补」与 AR-12 的「缺位委派」。
2. **已完成**：员工活动终态在设计里叫「已完成」，词表禁用该中文名（`approval_completed`）。推荐：改用其他中文名（如「活动已办结」），或确认可以复用并取消禁用。
3. **参与人**：`task_participant` 禁用「参与人」。推荐：保留全称「员工活动参与人」，界面不单写。
4. **同名状态**（M11）：审批中、已驳回、已退回、已通过、已撤销。若判同概念，须在 terms.yaml 已批准词条补 homonyms 或直接复用；若判同名异义，须分立并互列。
5. **撤回与退回**（M12）：审批平台撤回 = 本轮退回；B-31 区分「已撤回」与「已退回」。推荐：B-31 用自己的状态词，接入契约明写映射。
6. **AGG-115 的根**（M2、M3）：每资产一根叫「使用记录」还是「使用台账」；软件许可是否纳入。推荐：沿用 `it_asset_usage_record`，表名按词表。
7. **维修单中文名**（M4）：设备还是资产。
8. **审批 doc_type**（M18）：放哪个 bc；「IT需求审批」改为「信息技术服务审批」是否接受；是否确认存在第三个类型（合同原件借阅审批）。
9. **用印登记 vs 用印记录；用印已办理**（M6、M15）：推荐 SealUsageRegistration / SealUsageRegistered，中文名「用印已办理」与「登记」动词不一致，请定中文名。
10. **过渡通道使用范围**：5 个对象与 3 个命令我按 `scope: acl`（过渡期）登记，因领域模块不出现老系统名称；它们实际放在 B-31 的领域包里，是否应改 `scope: domain` 并去掉「老系统」字样（如「既有审批凭证」）？
11. **合同原件借阅的 bc 与用词**（M17）：organization.administration（设计 Q8）还是 contract.agreement（bcs.yaml）；「合同原件」还是「协议原件」。
12. **bc 级角色词**：申请人、提出人、借阅人、资产使用人、报修人是否要登记为 B-31 的 role；「处理人」与 `assignee` 同名（M14）；「经办人」尚无词条。
13. **共用状态词条**：同一 bc 内中文名唯一，所以草稿、已批准等五个聚合共用一个词条，含义按聚合略有差别（如「已关闭」）。推荐：保持共用，定义里写明各聚合含义。
14. **撤销 / 取消 / 撤回 / 作废 / 废止**（M22）：是否需要收敛成更少的动词。
15. **错误码归属**：审批回调无法对应、审批结果不支持、配置无有效版本这类跨模块通用的错误，是否应放 kernel 或审批模块（CS-2.24 要求通用错误码只在 kernel）；54 个错误码是否全部现在登记。
16. **周年通知接收角色**（M20）：行政还是员工关系负责人；不影响词表，影响接收角色配置。
17. **他 bc 缺口**（1.4）：入职日期、人事离职生效事件、报价版本、签章服务一批词，谁登记、何时。
18. **英文选词**：Issued（备选 Assigned）、Stamped（备选 SealUsed）、OverEntitlement（备选 OverAllocation，因 Allocation 已指成本分摊）、PrimaryApprover（备选 FirstApprover）、AssetCondition（备选 DeviceCondition）、EndedByReturn 风格同 ClosedBySeparation。

## 4. 设计里与命名相关、但不属词表的待定点（给 one-architecture）

- §1.1 与 §10 Q8 对合同借阅归属说法不同（M17）。
- 命令目录写「关闭用印申请」为系统自动、不设人工关闭，§9 第 2 条写「行政可关闭」「申请人可放弃未用份数」；放弃是否独立命令（已提议为低把握命令）。
- 「重开」在 §1.1 是须交付能力，命令目录并入「受理」；是否独立命令（已提议为低把握命令）。
- 权限工单的有效期与复核日是一个字段还是两个，到期是自动收回（要调用身份模块）还是提醒。
- IT 优先级（`priority_code`）值域缺。
- INV-138 来源份数占用的概念（有效占用、释放、更正）规则未定稿，我没有为「用印份数占用」造词（先例：`cash_application_reservation`、`invoice_posting_reservation`）。
- 用印、IT 的审批轮次补充表名称待 one-data 冻结；配置型版本引用的去向见 2.1。

## 5. 校验与未运行

已做（只读，未启动容器，未运行 mvn，未运行 build_all.py）：
- 用 `docs/modernization/glossary/term_rules.py` 的 `check` 与 `judge` 对「terms.yaml 全部词条 + 全部 proposed/*.yaml（含新文件）」共 1357 条做内存校验，对照 `term-rules-baseline.json`：新增问题 0 条（存量问题 102 条不变）。含 kind、scope / acl_class、事件过去式、命令祈使、泛词、同 bc 中英文重名、跨 bc 同名互列、错误码中文名全局唯一。
- 新文件用 `backend/glossary/build_proposed_index.py` 的受限 YAML 解析器解析成功（与 PyYAML 结果一致，可渲染 116 行表格），`--self-test` 通过。
- 按 `GlossaryTest` 的 snake 与字段规则检查：12 个必填字段齐全，snake 为 en 的 snake_case，id 无重复。
- 按 `Glossary.java` 的拆词规则核对设计里的表名、关键列、权限资源、锁名、枚举：除 `receiving`、`reminded`（建议改列名）外全部可由词表或提议词条覆盖；英文禁用写法（forbidden）无命中。
- 中文禁用写法：我的 zh 名里「用印审批人表行」「岗位资产配置标准行」「员工活动参与人」「离职归还提醒」含被他词条禁用的片段，登记后各自成为词条全称，界面守护按「更长的已登记词」放行；单写「行」「参与人」仍被拦。

未做：`./mvnw verify`（GlossaryTest、ErrorCodeGlossaryTest）、`build_all.py --check`（需重新生成 `proposed/README.md`）、前端 `check-copy-glossary` 对真实页面的扫描。
