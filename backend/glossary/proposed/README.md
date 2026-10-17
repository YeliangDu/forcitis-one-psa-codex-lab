# 提议术语清单（第三轮用语确认用）

<!-- 本文件由 backend/glossary/build_proposed_index.py 从同目录 *.yaml 生成，不要手改；改 yaml 后运行 python3 docs/modernization/build_all.py 并提交结果。 -->

`terms` 只放业务术语，纯技术词（动词、框架词、值对象方法名等）放同文件的 `common_words`。每条必填使用范围 `scope`（domain 领域 / kernel 共享内核 / acl 防腐层）与 `bc`（取 `docs/modernization/glossary/bcs.yaml` 中的 id）：kernel 另填 `used_by`（使用它的 bc），acl 另填 `acl_class`（过渡期 / 长期），指标另填 `formula`、`grain`、`as_of`，下游 bc 自用词可填 `upstream_term`；同一 bc 内中英文名唯一，跨 bc 同名在 `homonyms` 互列；`kind` 只取 DDD 分类（aggregate 聚合 / entity 实体 / value_object 值对象 / domain_event 领域事件 / command 命令 / business_rule 业务规则 / read_model 读模型 / metric 指标 / role 角色 / state 状态，错误码另列 error_code），事件名为聚合名加过去式业务动词、命令用祈使动词，Create / Update / Delete / Data / Info / Manager 等技术泛词不作领域名（规则与校验见 `glossary/term_rules.py`）。本表按文件列出除错误码以外的全部提议术语；「是否疑似方言」由条目中类型为方言或待定的别名与禁用写法生成，空表示未登记问题；「备注」为条目 note 原文。

修改任何一个 yaml 后运行 `./mvnw -B verify`（GlossaryTest 会校验字段、重名和未登记词）与 `python3 docs/modernization/build_all.py`（重新生成本文件）。

## `architecture-acl.yaml`

ADR-0039（防腐层边界）引入的术语（proposed，待业务评审，ADR-0018）。登记会话：one-architecture，2026-10-15。

无错误码以外的术语。

## `audit-partition.yaml`

审计条目按月分区（ADR-0048、DATA-REPAIR-timesheet-audit TB-07）用到的技术词：分区只是存储与删除的单位，不是业务术语，只登记为技术词（proposed，ADR-0018）。

登记会话：one-1a-next-5 数据修复 PR-1。使用范围：platform.audit 内部的作业、配置键、指标名与锁名。

无错误码以外的术语。

另有技术词 2 个（`common_words`），不在表中。

## `code-aggregates-events.yaml`

B2 登记：代码里的聚合根与领域事件各自要有词条（G40、G41）；

登记会话 one-glossary，2026-10-15，中文名待用户确认。

无错误码以外的术语。

## `contract-evolution.yaml`

契约演进与部署排空用语（proposed，ADR-0035，待用语确认，ADR-0018）。

无错误码以外的术语。

## cost.timesheet（`cost-timesheet.yaml`）

cost.timesheet 提议术语（proposed，待第三轮用语确认，ADR-0018）。

无错误码以外的术语。

另有错误码 13 个（kind 为 `error_code`）、技术词 23 个（`common_words`），不在表中。

## `data-classification.yaml`

数据分级的个人信息标记（ADR-0043）：个人信息 PI、敏感个人信息 SENSITIVE_PI；盘点 P-029 改名，one-glossary 2026-10-16 登记提议。

无错误码以外的术语。

## `data-migration.yaml`

迁入的其余用语（拒收、核对、历史期间、迁入标记等）随 ADR-0036（另行 PR）登记。

通用词另增 `batch`、`count`（批量命令后缀与计数端点，NAMING_STANDARD §2.6 A-3、A-6）。

无错误码以外的术语。

另有技术词 2 个（`common_words`），不在表中。

## `enterprise-aggregates.yaml`

涉及模块：`delivery.acceptance`、`delivery.execution`、`masterdata.counterparty`、`procurement.subcontracting`、`receivables.wip`。

全企业聚合草案 §8 交 one-glossary 的新用语（预判登记稿，proposed，待业务评审，ADR-0018）。

前提：bcs.yaml 先并入 bcs-additions.yaml 中的 masterdata.*、organization.leave、accounting.tax 等；需要用户裁定的 11 项不在此文件，见 decide-items.json。

聚合所在 bc 暂取现有 bc（presales.quoting、contract.baseline 等），B2-X01 定合并后的 BC 标识后整体迁移。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `tender_project` | 招标项目 | TenderProject | 领域 · masterdata.counterparty | 依《招标投标法》公开或邀请招标的项目登记（招标标识、采购方式、标段），外部引用登记，被投标、合同和建设工程共同引用。草案称「招标项目登记」，登记是动作，名词取「招标项目」。与我方项目（project）不是一回事。 |  | 若架构定为不建聚合（REF，无聚合），kind 改 entity |
| `output_invoice` | 销项发票 | OutputInvoice | 领域 · receivables.wip | 我方向客户开具的增值税发票（蓝字、红字），含平台开具结果与红冲确认；与开票事件（invoice_issued，开票）的关系是：销项发票是对象，开票是它被开具这件事。 |  | 英文取 output tax 的通行写法；若偏好 SalesInvoice 可改，中文名不变 |
| `supplier_agreement` | 供方协议 | SupplierAgreement | 领域 · procurement.subcontracting | 我方与供方（分包方、采购供应商、出租方、保险人、居间人、劳务派遣单位）签订的协议，含基线、付款条件、质保金条款以及资质与同意证据；与客户侧收入协议（agreement，协议）相对。「供方」取 GB/T 19000 用语。 |  | 词表 agreement_type__lease_contract 等五个供方侧协议类型现登记在 contract.baseline，随本条迁入采购子域，见 PREJUDGE |
| `design_change_claim_status` | 设计变更主张状态 | DesignChangeClaimStatus | 领域 · delivery.acceptance | 设计变更是否向对方主张：待主张，或不主张（附理由）；是否已主张由合同侧的变更、索赔记录，本 BC 不引用它们。草案称「主张标记」，标记是实现用语，业务上是状态。 |  |  |
| `project_terminated` | 项目已终止 | ProjectTerminated | 领域 · delivery.execution | 项目在完成前被终止（有成本发生）；成本核算侧订阅，用于终止清算。 |  |  |
| `project_cancelled` | 项目已取消 | ProjectCancelled | 领域 · delivery.execution | 项目在开工前被取消（无成本发生）；与终止的区别在是否已有成本。 |  |  |

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

另有错误码 27 个（kind 为 `error_code`）、技术词 18 个（`common_words`），不在表中。

## organization.attendance（`fix-b2-data.yaml`）

Phase 1a 修复批 B2（数据架构）新增术语（proposed，待业务评审，ADR-0018）。

无错误码以外的术语。

另有错误码 1 个（kind 为 `error_code`），不在表中。

## `fix-b2b-events.yaml`

Phase 1a 修复批 B2b（事件业务日期、指标）新增术语（proposed，待业务评审，ADR-0018）。

无错误码以外的术语。

## platform.integration（`fix-c.yaml`）

前端修复批新增的界面用语（proposed）：状态、列名与提示语中的业务词，守护要求它们作为完整的词登记，不得由单字拼成。

状态、列名与提示语中的业务词。前端用语守护要求单字只作连接词，状态词（已通过、待认领等）必须作为完整的词登记。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `ui_external_system` | 外部系统 | ExternalSystem | ACL（长期） · platform.integration | 本系统之外、通过防腐层接入的系统，如企业微信、致远、组织来源 |  | 错误提示用语 |

## `fix-time-zone.yaml`

Phase 1a 时间与时区修复新增用语（proposed，待业务评审，ADR-0018）。纯技术词放 common_words。

无错误码以外的术语。

另有技术词 2 个（`common_words`），不在表中。

## `frontend-ui.yaml`

前端界面用语中尚不在正式词表里的业务术语（proposed，待第三轮用语确认，ADR-0018）。

无错误码以外的术语。

## `kernel.yaml`

`tail_adjustment`（尾调）、`rounding_residual`（舍入差）、`prorate`（按权重拆分）由用户 2026-10-15 XD-07「同意 (Recommended)」确认登记（`docs/modernization/CROSS_CUTTING_DECISIONS.md`）。`reversal_document`、`correction_document`、`document_nature`、`reversal_category`、`reversible_balance`、`derived_reversal`、`original_period`、`change_order_document`、`reverse_action`、`correct_action` 十条是 ADR-0031 第 8 项的提议，待用语确认（U-4）。

无错误码以外的术语。

另有技术词 38 个（`common_words`），不在表中。

## `list-paging.yaml`

K-3a 列表分页公共契约新增的纯技术词（proposed，NAMING_STANDARD A-3、A-4）。

无错误码以外的术语。

另有技术词 6 个（`common_words`），不在表中。

## organization.workforce（`nits1.yaml`）

NITS-1 项5 新增的标识符（proposed）。登记会话：one-1a-next-5，2026-10-17。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `employee_existence` | 员工存在判定 | EmployeeExistence | 领域 · organization.workforce | 员工存在 = 组织快照的员工表里有该 ID 的行；不要求有任职，也不要求在职（one-data 裁定 DR-TB01-FU1-1）；与需要任职的员工查询分开，只答有或无 |  | NITS-1 项5，运维命令的操作人与见证人检查用 |

## organization.attendance（`organization_attendance.yaml`）

organization.attendance 提议术语（proposed，待业务评审，ADR-0018）。

无错误码以外的术语。

另有错误码 8 个（kind 为 `error_code`）、技术词 8 个（`common_words`），不在表中。

## organization.workforce（`organization_workforce.yaml`）

Phase 1a organization.workforce 新增术语（proposed，待业务评审，ADR-0018）

新词一律 status: proposed；通用技术词放 common_words，业务概念放 terms。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `login_lookup` | 登录查人 | LoginLookup | ACL（过渡期） · organization.workforce | 按外部登录标识（企业微信 userid 或外部登录名）在组织快照中找员工；随 DA-3 外部身份移到 identity 后删除 |  | R-1b P2-H2，两个按登录标识查人的方法合一以守住 api 方法上限 |

另有错误码 10 个（kind 为 `error_code`）、技术词 86 个（`common_words`），不在表中。

## platform.approval（`platform-approval.yaml`）

platform.approval 提议术语（proposed，待业务评审，ADR-0018）。单词级术语：服务于代码标识、URL、事件键、错误码的命名守护。

无错误码以外的术语。

另有错误码 29 个（kind 为 `error_code`）、技术词 111 个（`common_words`），不在表中。

## platform.audit（`platform-audit-paging.yaml`）

K-3b-4 审计列表、导出、本人登录记录接入分页公共契约新增的标识符（proposed）。登记会话：one-1a-next-5，2026-10-16。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `audit_filter` | 审计筛选条件 | AuditFilter | 领域 · platform.audit | 审计条目列表与导出共用的十个筛选条件（操作人、代办人、动作、动作前缀、对象种类、对象、来源地址、结果、起止时间）；构造时校验对象与种类一致、起早于止 |  | K-3b-4，取代原 AuditQuery 中的筛选部分 |
| `audit_page_mark` | 审计页标记 | AuditPageMark | 领域 · platform.audit | 审计条目键集分页的位置：上一页最后一条的发生时间与条目标识；只供生成下一页游标 | 「AuditPosition」已禁用 | K-3b-4 复审 M1；原名 AuditPosition 与岗位 Position（organization.workforce）同名异义，故改名 |
| `own_login_record_page_view` | 本人登录记录分页视图 | OwnLoginRecordPageView | 领域 · platform.audit | 本人登录记录一页的应用层读模型：记录列表加下一页游标；不含条目标识 |  | K-3b-4，构词沿用审计条目分页视图 |

## platform.identity（`platform-identity.yaml`）

platform.identity 新增术语（proposed，待业务评审，ADR-0018）。登录、会话、票据、角色与数据范围用到的词。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `role_version` | 角色版本 | RoleVersion | 领域 · platform.identity | 角色定义（名称、状态、数据范围类型、完整权限集合）的每次变化一行，只追加；当前版本是没有失效时刻的那一行，每个角色至多一个；删除角色是关闭最后一个版本（ADR-0034 第 1 项） |  | 登记会话 one-1a-par（IMP-1017-PAR-02，派工卡 ADR-0034-IMPL 第 11 节）；查重：terms.yaml 与 proposed 中同一 bc 无同名、同中文名 |
| `account_role_grant` | 角色授予 | AccountRoleGrant | 领域 · platform.identity | 某账号在一段时间内持有某角色，只追加；结束一律称收回，收回是关闭开放行，再次获得新增一行（ADR-0034 第 2 项） |  | 登记会话 one-1a-par（IMP-1017-PAR-02，派工卡 ADR-0034-IMPL 第 11 节）；查重：terms.yaml 与 proposed 中同一 bc 无同名、同中文名 |
| `revocation_cause` | 收回原因 | RevocationCause | 领域 · platform.identity | 角色授予被收回的原因：账号授权、离职、运维命令、迁入批次回退（ADR-0034 第 2 项）；列名 revocation_cause_kind |  | 登记会话 one-1a-par（IMP-1017-PAR-02，派工卡 ADR-0034-IMPL 第 11 节）；查重：terms.yaml 与 proposed 中同一 bc 无同名、同中文名 |
| `role_version_closure` | 角色版本关闭 | RoleVersionClosure | 领域 · platform.identity | 删除角色时给最后一个角色版本写下失效时刻、关闭人、关闭理由与审计条目，不再开新版本；被新版本接替的关闭不记这三项（ADR-0034 第 1 项） |  | 登记会话 one-1a-par（IMP-1017-PAR-02）；查重：terms.yaml 与 proposed 中同一 bc 无同名、同中文名 |

另有错误码 3 个（kind 为 `error_code`）、技术词 110 个（`common_words`），不在表中。

## platform.integration（`platform-integration.yaml`）

platform.integration 提议术语（proposed，待业务评审，ADR-0018）。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `registration` | 登记 | Registration | ACL（长期） · platform.integration | 由人把一条来源对照录入系统，先处于待确认，等另一个人确认或拒绝；现在只有核算组织编码可以人工登记 |  |  |
| `evidence` | 取证 | Evidence | 领域 · platform.integration | 只读核对老系统与 A8 生产数据，用于复刻老行为、迁入对账与新旧等价核对；用专用取证账号，只出计数或聚合，或落 ECS 数据盘，每次登记一行 |  | ADR-0037 登记（数据库登录名 one_evidence_ro），待用语确认 |

另有错误码 7 个（kind 为 `error_code`）、技术词 25 个（`common_words`），不在表中。

## platform.numbering（`platform-numbering.yaml`）

platform.numbering 提议术语（proposed，待业务评审，ADR-0018）。服务于原编号、确认状态、单据类型代码的命名守护。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `qt` | 报价单据类型代码 | Qt | 领域 · platform.numbering | 报价（DocumentType.QUOTE）的单据类型代码 QT，1b 首个使用方 |  | 例 QT-FST-202610-0001 |
| `ts` | 工时单据类型代码 | Ts | 领域 · platform.numbering | 工时（DocumentType.TIMESHEET）的单据类型代码 TS，沿用种子模板中的前缀字母，正式词表尚无对应词 |  | 例 TS-FST-202610-0001；模板待管理员确认 |
| `jv` | 记账凭证单据类型代码 | Jv | 领域 · platform.numbering | 记账凭证（DocumentType.JOURNAL_VOUCHER）的单据类型代码 JV，沿用种子模板中的前缀字母，正式词表尚无对应词 |  | 例 JV-FST-202610-0001；模板待管理员确认 |

另有错误码 5 个（kind 为 `error_code`）、技术词 3 个（`common_words`），不在表中。

## platform.web（`platform-web.yaml`）

无错误码以外的术语。

另有错误码 5 个（kind 为 `error_code`）、技术词 13 个（`common_words`），不在表中。

## platform.identity（`recon-pending-renames.yaml`）

已裁定漏登补登（NAM-1017-GLO-06）里，新名已入正式词表而代码仍用旧名的待改名登记。

负责会话改名并合入后，one-glossary 删除对应条目，旧名禁用随之生效。

| id | zh | en | 使用范围 | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|---|
| `recon_pending__role_deleted` | 角色已删除 | RoleDeleted | 领域 · platform.identity | 用户裁定（GL 确认单 B2-B06）已定为「角色已废除 / RoleAbolished」；代码事件类改名合入前保留旧写法 RoleDeleted。 |  | 代码仍用 RoleDeleted |

## `round3-pending-renames.yaml`

涉及模块：`accounting.ledger`、`cost.timesheet`、`organization.workforce`、`platform.approval`、`platform.identity`、`platform.integration`、`platform.numbering`。

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
| `round3_pending__regional_head_review` | 区域负责人审批 | RegionalHeadReview | 领域 · platform.approval | 第三轮用语确认（R3-AP-69）曾定为区域负责人审批 / RegionalHeadApproval，后由 D5-06 取代，改为：部门负责人审批 / OrgUnitHeadApproval（org_unit_head_approval）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 RegionalHeadReview（main）；D5-06 把区域负责人审批改为部门负责人审批 |
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
| `round3_pending__journal_voucher` | 会计凭证 | JournalVoucher | 领域 · accounting.ledger | 第三轮用语确认（R3-AP-26）已定为：记账凭证 / JournalVoucher（journal_voucher）。代码与界面改名合入前保留旧写法。 |  | 界面文字仍用「会计凭证」 |

## `step3-objects.yaml`

信息图 ADR-0048 确认页（用户 2026-10-16 选推荐）带来的新用语：one-architecture 交，one-glossary 2026-10-16 查重后登记；争议状态与争议应收是否同义待用户裁定（待决定收件箱）。

无错误码以外的术语。
