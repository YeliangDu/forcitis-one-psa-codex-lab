# B-31 施工设计与已采纳 ADR 字面一致性核对（IMP-1021-ADM-01 前置）

基线：origin/main `526a906a5`，分支 `claude/one-1a-admin-skeleton`。只读核对，不裁定、不改设计。
设计文件：`docs/modernization/architecture/proposals/closeout/people/b31-rules/B31_CONSTRUCTION_DESIGN.md`（下称「设计」，行号 L 为该文件行）；规则草案 `B31_RULES.yaml`（下称「规则稿」）。
判定词：一致／字面不一致／设计缺口／代码缺口。裁定方列在每条末尾。

## 0. 先说结论：核对中确认「一致」的部分

| 项 | 设计位置 | 对照 | 结果 |
|---|---|---|---|
| 依据行中的 ADR 编号与决定号 | §1.1 #1（L39）、§1.3（L87）、§2.4（L160）、§3.8（L337）、§6（L484/496） | ADR-0064 决定 3（台账）、决定 9（修订 ADR-0054）、迁移方案 2、决定 4—9；ADR-0058 决定 1—8；ADR-0026 决定 1—3、4—7；ADR-0043；ADR-0057 决定 1、2 | 编号与内容均对得上 |
| 包结构、schema 名 | §2.1（L91-114） | ADR-0003 决定 1、2（`<子域>.<bc>.{api,events,domain,application,infrastructure,web}`，schema `organization_administration`） | 一致 |
| api 总数 ≤12、不为内部命令开公开方法 | §2.2（L116-125） | ADR-0066 决定 6.5（G4）、CS-1.10 | 一致 |
| 业务模块不碰 Flowable，只依赖审批 api | §4（L339-386） | ADR-0007 决定 2、3 | 一致 |
| 台账归签章服务、B-31 只引用条目与版本 | §3.7（L308-324） | ADR-0064 决定 3（法人×印章类型、排他约束、D17 版本化、早于最早版本失败） | 一致 |
| 定时：自声明 `@Scheduled`＋`@SchedulerLock`、不依赖 `platform.scheduling`、幂等键＝聚合 ID＋到期时点、注入 Clock | §5.1–5.2（L390-414） | `closeout/CONVENTIONS.md`「定时触发」；`platform/scheduling/package-info.java`；G46（`NamingSurfaceTest.java:340`） | 一致（偏差见 SCH-02…05） |
| 权限动作限定 read/create/update/submit/execute | §6.1（L440-467） | `backend/glossary/terms.yaml` `standard_actions`、G43 | 一致 |
| 通用 DDL 规则（UUIDv7、`version`、`timestamptz`、`text+CHECK`、应用显式写业务值、不建软删） | §3.1（L168-176） | NAMING_STANDARD §2.3、§2.4a 第 1/3/4/6 条；SESSION_PLAYBOOK D2/D5/D7/D16 | 一致 |
| 列名类别词（`_count`/`_key`/`_text`/`_code`/`_at`/`_number`…） | §3.2–3.6 | `DdlNamingTest.java` ClassWord | 一致（`decision_ref` 除外，见 GLO-09） |
| 代码现状断言：`ProcessKey` 仅工时流程；`ApprovalCompleted` 有四种结果；撤回以 `RETURNED` 结束；`AccessScope` 仍在 identity api、无共享 `CurrentAccess`；`EmployeeProfile` 无入职日期；workforce 无离职事件；B-31 代码目录不存在；命令 25、状态机 7、既有表 13 | §4.3、§4.4、§6.2、§8.3、§8.4 | 本次逐项读代码与 `b31.commands.yaml`、`b31.state-machines.yaml`、`b31.data-model.yaml` | 属实（唯一例外：「DocumentType 仅有…」见 APR-01） |
| 全部边方向 | §2.4 | 见 MOD-05 逐边表 | 方向均被允许，未引入环 |

## 1. ADR 引用与范围（REF）

**REF-01 实际用印核验接口方向反了**
- 设计：§2.2 #1（L120）「按登记 ID 核对报价版本、法人、拟发文件与实际凭据」。
- ADR-0057 决定 2 第 1 条：编排方确定「目标报价版本、法人及拟发文件」，向 B-31 api 核验，「取得用印登记 ID」；登记 ID 是核验的**输出**，之后作为 AGG-11/12 命令入参。聚合稿 B31-16 注同。
- 判定：字面不一致（契约方向）。REF-04 在核验前并无登记 ID，api 须按（报价版本，法人，拟发文件）查。
- 裁定：one-architecture（契约先行 CS-1.10），负责 B-31 的会话落实。

**REF-02 依据行缺失关键 ADR**
- 设计全文依据行不含 ADR-0060/0061/0070（文件服务）、ADR-0066（分层、单据类型登记、准入）、ADR-0067（冻结通道）、ADR-0051（BC 清单、审批依赖）、ADR-0068（人事命令与离职事件）；ADR-0060 仅在 §0.2（L21）顺带出现。
- 判定：设计缺口（后面 DATA-10、APR-01、APR-02、MOD-02、MOD-03、SCH-06 都由此而来）。
- 裁定：负责 B-31 的会话补依据；实质问题见各条。

**REF-03 ADR-0054/0055/0064 的「经通用审批办理」与 B31 已定决定不符**
- 设计：§1.1（L30）「只有用印及受控 IT 类别存在人工审批路径」；领用、调拨、归还、维修、员工活动明确无审批。
- ADR-0054 决定 1 原文与 ADR-0064 决定 9 的新文都写「用印、合同借阅、IT 工单与资产、员工活动经通用审批办理」；ADR-0055 决定 1 写这些「迁入通用审批」。B31 答复（2026-10-17，晚于 ADR-0054 采纳）取消了资产与活动的审批。
- 判定：字面不一致（ADR 措辞未随用户后续决定更新；设计自己没有标注此偏差）。设计 §1.1 引用「ADR-0054：决定1」时也未注明其「唯一例外与保全句」已被 ADR-0064 决定 9 取代（ADR-0054 头部已标）。
- 裁定：one-architecture（是否补一句澄清或登记偏差）。

**REF-04 「合同借阅属于 B-05」的依据不支持归属**
- 设计：§1.1（L66）「合同借阅…属于 B-05，不并入 B-31」，依据 ADR-0054 决定 1、ADR-0055 决定 1。
- 这两条只说合同借阅在先行范围内；归属 B-05 来自聚合稿 B-31 引言与 `bcs.yaml`（L396 条目）。而 §10 Q8（L713）又把借阅记录建在 B-31，见 DATA-07、DOC-03。
- 判定：字面不一致（引用与结论不对应；且与设计 §10 自相矛盾）。
- 裁定：one-architecture。

## 2. 聚合、表、状态历史、配置版本（DATA）

**DATA-01 AGG-115 一条命令多条转换，与 ADR-0058 的 (aggregate_id, sequence_no) 对不上**
- 设计：§3.8（L335）「**结构待定**…ADR-0058 的 (aggregate_id, sequence_no) 与根版本映射如何完整表达这些转换」。
- ADR-0058 决定 1：「`sequence_no` 取聚合的 `version`，(aggregate_id, sequence_no) 唯一」，每条记录只有一对 from_state/to_state，**没有子实体标识列**。
- 状态机分片：`调拨资产` 一条命令同时产生 3 条转换（调拨单 已申请→已调拨；旧期间 在用→已结束；新期间 空→在用）；`登记领用` 产生 2 条。AGG-115 根是 `it_asset_use_ledgers`（每卡片一根），三台机器的状态都在子表。
- 判定：设计缺口，且根因是 ADR-0058 未覆盖「一聚合多台子状态机」。设计「不擅自把子实体 ID 当聚合 ID、不漏记」的立场与 ADR 一致，但没给方案。
- 裁定：one-architecture（ADR-0058 补充或登记例外）＋one-data。

**DATA-02 AGG-113 同命令两条转换、分次登记无转换**
- 设计：§3.8（L335）「用印登记后自动关闭亦须明确版本推进和转换记录边界」。
- 状态机分片：`已批准→已用印`（登记用印）后若份数到齐，同一命令还有 `已用印→已关闭`；分次登记 `已用印→已用印` 无状态变化。ADR-0058 决定 1「状态每变一次就记一条」。
- 判定：设计缺口（同 DATA-01 的版本号语义；另需说明无状态变化的登记是否推进 `version`，以及 `sequence_no` 允许跳号）。
- 裁定：one-data＋one-architecture。

**DATA-03 INV-138 并发方案可行，但对 INV 原文有三处偏离**
- 设计：§3.2（L211）建议「对来源审批实例建立事务级串行化锁」，「有效占用、释放及更正口径尚须规则定稿」。
- 可行性：仓内已有同类写法（`JdbcApprovalRequestRepository.java:65` 的 `pg_advisory_xact_lock`；ADR-0068 决定 2 明文引用）；只写当前申请，满足 §14 第 1 项与 G-T1，仅对其他申请做读。
- 偏离：(a) INV-138 原文是「累计**用印**份数不超过批准份数」，设计与 §9 第 2 条（L690）、规则稿 YY-06 却按「占用／释放」口径（放弃或关闭即释放占用），含义不同；(b) 聚合稿把 INV-138 分类为「A」（聚合内），实际跨申请，设计判断正确但稿未改；(c) §9 第 3 条（L696）/ Q7 把键改成「老系统单号」且份数由行政手录，INV-138 文字需修订（见 GLO-10）。
- 判定：设计方案一致；INV 文字与设计口径字面不一致；(c) 设计缺口。
- 裁定：one-rules（口径）、one-architecture（INV 文字与分类）、one-data（锁键与约束）。

**DATA-04 AGG-115 各表缺法人列，根表带「当前使用人投影」未按 D1 登记**
- 设计：§3.1（L171）要求「单据具备业务日期、法人、编号」；§3.4（L261-263）三张表均无 `legal_entity_id`（§3.3、§3.5、§3.6 的单据表都有）。
- ADR-0058 决定 1：转换记录带 `legal_entity_id`，「聚合没有法人归属的，在本 BC 的登记里写明理由；来源不确定时失败」；SESSION_PLAYBOOK D7。`it_asset_use_ledgers` 的「当前使用人／期间投影」（L261）属 D1「作为投影存储则登记为投影、可重算并有对账」，设计未提。
- 另：`it_asset_use_requests` 必须引用根（L262 `it_asset_use_ledger_id`，默认 NOT NULL，L173），而 `b31.data-model.yaml` 写根「首次领用或迁入时建立」，申请先于领用时根从何而来未说明。
- 判定：设计缺口。
- 裁定：one-data。

**DATA-05 配置表（D15/D17）只落了一张，且放在聚合下**
- 设计：§3.8（L331）「D15 配置保存有效版本、生效日、确认人、确认时间与决定依据」✓；但只有 `employee_activity_types`（L300）有表。§10 的用印审批人表（Q1）、IT 类别与审批人表（Q4/Q5）、预盖张数上限（Q3）及 `ARCH_VS14` 的岗位资产标准、周年规则、七日提醒配置均无物理模型；设计自认 §8.3（L615）「写边界尚须 one-data 定稿」。
- 对照：D3「子表随根写入」、D17 细则 4「一张版本表一个来源、一个负责人」——配置独立版本维护却作为 AGG-116 子表，与二者字面冲突。
- 判定：设计缺口（设计已自陈一部分）。
- 裁定：one-data。

**DATA-06 §10/§11 的新增数据没有进 §3**
- §3.1（L175）「不先建假定金额字段」、§3.6（L298）「费用预估引用」，而 Q6（L711）「预估金额必填并留底」→ 需要 `Money`；Q3 预盖登记（张数、用途、领用人、日期、编号、收回／作废）、Q5 有效期／复核日、Q2「INV 新增」（L707）、§11.3 用印台账「改错用新记录冲正」（L740，AGG-113 命令表里没有更正命令，冲正行如何参与 INV-119/138 累计未定）均无表、列或命令。
- Q2「INV 新增，随实施卡登记」与 §3.2（L211）「不新增未经批准的聚合编号」冲突；INV 编号登记权在 one-architecture（聚合稿 §1.2）。
- 判定：设计缺口。
- 裁定：one-data（表列）、one-rules（INV 与冲正口径）、one-architecture（INV 编号）。

**DATA-07 合同原件借阅建在 B-31，与 INV-125、AGG-13 归属及数据所有权冲突**
- 设计：§10 Q8（L713）「B-31 新增合同原件借阅记录（目标形状同 AGG-13 借阅）」，「第一版即建」INV-125，W3 移交；对照 §1.1（L66）、`bcs.yaml` L401「合同借阅与归档归 contract.agreement」。
- INV-125 原文前置：「协议已归档」且「须引用已通过的合同借阅审批实例」，所属 AGG-13（W3）；B-31 内无协议可核，只能用「老系统合同编号」代位。表与 `state_transitions` 将来在 BC 间移交，仓内无 ADR 或守护覆盖这一做法（ADR-0058 决定 3「其他 BC 不写别人的表」、D3、D14）；也与 CS-1.8（只实现当前阶段）有张力。
- 判定：设计缺口＋设计内部矛盾。
- 裁定：one-architecture（是否放 B-31、移交方式）＋one-data。

**DATA-08 迁入历史状态与 `text+CHECK`、初始转换**
- 设计：§3.4（L262）「历史审批引用仅供迁入保留」；聚合稿与状态机分片要求历史「审批中／已驳回／已撤回」值全量保留，但这些值不在现行状态机内。
- ADR-0058 决定 8：迁入写一条 `from_state` 为空、`cause` 为迁入的初始转换，且迁入「经领域入口产出」。设计未说明 CHECK 如何容纳仅历史值，也未说明迁入初始转换的 `to_state`。
- 判定：设计缺口。
- 裁定：one-data。

**DATA-09 离职事件一次改多个聚合**
- 设计：§5.2（L405）离职生效后「为仍在用资产记录离职生效时点、原组织单元和固定 `return_reminder_due_at`」。
- 一人可持有多台设备，而 AGG-115 是「每卡片一根」，一次处理写多个聚合，违反 SESSION_PLAYBOOK §14 第 1 项 / G-T1（一个 `@Transactional` 只写一个聚合；例外须登记），设计未提逐聚合事务或登记例外。
- 判定：设计缺口。
- 裁定：one-architecture（逐聚合事务或例外）。

**DATA-10 文件服务与保存期的使用约定没写**
- 设计：§3.2（L205）仅记「不可变文件版本 ID、内容哈希」。
- ADR-0060 决定 2：关联时由所属模块给出访问级别、个人信息标记、保存类别，缺一失败（代码 `BindRequest`，`platform/file/api/BindRequest.java`）；决定 7.1：下载在业务模块资源下用命令 `…/{单据 ID}/files/{文件 ID}:download`（设计 §6.1 权限表无下载权限）；决定 3：封存登记。ADR-0061 影响面：`organization.administration` 写**用印完成日**（`StoredFiles.setPreservationStart`），保存类别「签署与用印文件」；IT、资产、活动附件的类别「未定」，按不处置保存并在注册表登记缺口、由 one-coordinator 进收件箱（ADR-0061 决定 2、4）。ADR-0070：单文件 100 MB、加密文件拒收、含宏下载提示，影响用印文件收件与迁入拒收处置（ADR-0070 迁移方案 3）。
- 判定：设计缺口。
- 裁定：负责 B-31 的会话补设计；类别缺口由 one-data＋one-coordinator。

**DATA-11 审批参与关系（ADR-0026 决定 3）没设计**
- 设计：§6.2（L479）「审批参与关系仅授该单据、附件和审批历史的只读访问」。
- ADR-0026 决定 3.4：业务模块维护 `document_participants`，按 `(task_id, user_id)` 幂等写入，每天与引擎对账；全仓无任何实现（只出现在 ADR-0026/0033）。B-31 是第一个接审批的新业务模块，设计的 13 张表里没有它。
- 判定：设计缺口（也是平台待办）。
- 裁定：one-architecture＋one-security。

## 3. 用语与词表（GLO）

词表基线：`docs/modernization/glossary/terms.yaml` 现有 5 个 `bc=organization.administration` 的已批准词条（2026-10-17 写回，L12818、12840、12867、12888、12914）。设计 §8.2 #1（L603）称「664 条…未找到 bc=organization.administration 的词条」，与基线不符（见 GLO-06）。

**GLO-01 IT 需求工单 → 信息技术服务工单**
- 设计：L41/L213/L226/L134/L344/L534-537，表 `it_requests`、`it_request_handlings`（L234-235），审批 doc_type「IT需求审批」。
- 词条 `it_service_ticket`（信息技术服务工单，`ItServiceTicket`）；别名「IT 需求工单」已标禁用（standard-zh，context=organization.administration）。
- 判定：字面不一致。表名应派生 `it_service_tickets`（NAMING_STANDARD §3「表名由词表派生」）；命令/事件/审批类型里的「IT 需求」是否随词条改名，需裁定。
- 裁定：one-glossary。

**GLO-02 IT 资产使用记录 → 信息技术资产使用记录**
- 设计：L50、L241（标题）、表 `it_asset_use_ledgers`/`it_asset_use_requests`/`it_asset_use_periods`（L261-263）。
- 词条 `it_asset_usage_record`（`ItAssetUsageRecord`）；别名「IT 资产使用记录」「信息技术设备使用记录」已标禁用。设计的根叫 ledger（「台账」在签章服务与 accounting.ledger 另有含义），`use` 与词条 `usage` 也不同。
- 判定：字面不一致。
- 裁定：one-glossary。

**GLO-03 词条定义含「软件许可」，设计只覆盖资产卡片**
- 词条 `it_asset_usage_record` 定义：登记「实物设备和软件许可」的领用与使用，「区分设备与软件许可」；设计与聚合稿只引用 B-24 资产卡片（L261、`fixed_asset_card_id`），软件许可无承载（规则稿 IT-03/Q5 另有「买新软件需批」）。
- 判定：字面不一致（定义 vs 设计范围）。
- 裁定：one-glossary（改定义）或 one-architecture（补范围）。

**GLO-04 IT 资产维修单 → 信息技术设备维修单**
- 设计：L271（标题）、L279；词条 `it_asset_repair_order`，别名「IT 资产维修单」已标禁用。表名 `it_asset_repair_orders` 与 snake 一致，仅中文名不符；词条里「资产使用记录」与「设备维修单」的资产／设备用词本身也不统一。
- 判定：字面不一致（中文名）。
- 裁定：one-glossary。

**GLO-05 用印申请相关表名**
- 设计：L204-207、L200，表 `seal_applications`、`seal_application_documents`、`seal_application_source_documents`，外键列 `seal_application_id`；子表 `seal_usage_records`。
- 词条 `seal_usage_request`（用印申请，`SealUsageRequest`），旧写「用印申请记录」「SealUsageRequestRecord」已禁用；设计的名字来自已被取代的提议（`SealApplication`，b31.notes.md「新用语」）。`seal_usage_records` 与 `seal_usage_request` 近似，「用印登记行」未登记；「印章类别／印章类型」同名异义（ADR-0064 问题单）设计两种写法并用。
- 判定：字面不一致。
- 裁定：one-glossary。

**GLO-06 §8.2 #1 事实有误**
- 设计 L603：「当前 terms.yaml 664 条中，未找到 bc=organization.administration 的词条」。基线 `terms.yaml` 为 837 条，含上述 5 条；`git log` 显示 `seal_usage_request` 于 2026-10-17（`d55146ba0`）已写回，早于设计日期。
- 判定：字面不一致（设计事实陈述过时）。
- 裁定：负责 B-31 的会话更正；one-glossary 复核。

**GLO-07 设计用语与既有词条的跨 bc 同名异义**
- 「兜底」：L257（INV-121「兜底」＝守卫）、§10/§11（L706、723、730-735，备用审批人）；词条 `administrator_fallback`（转流程管理员）把「兜底」列为禁用。
- 「参与人」：`employee_activity_participants`（L299）、L382、「审批参与关系」（L479）；词条 `task_participant`（任务参与人）禁用「参与人」。
- 「已完成」：AGG-116 状态（L289-292）；词条 `approval_completed` 禁用别名「已完成」。
- 「已退回」：IT 工单状态（L221，驳回→已退回）；审批词条 `ui_approval_returned`（已退回＝审批被退回），审批里「驳回」是 `REJECTED`，两边映射相反（见 APR-03）。
- 「业务编号」：L153「业务编号及历史编号映射」；词条 `document_number`（单据编号）禁用「业务编号」。
- 判定：字面不一致／同名异义，须登记互列并写使用范围与 bc。
- 裁定：one-glossary（用 `new_term_issue.py` 登记，不自行裁定）。

**GLO-08 B-31 的状态、命令、事件词条全部未登记**
- 已批准的仅 5 个聚合名；25 条命令、各状态（已批准、已用印、已关闭、已提交、处理中、已解决、已领用、已调拨、已归还、已报修、已维修登记、已转领用、已取消…）、事件名均无词条（设计 §8.2 #2 已承认一部分）。
- 判定：设计缺口（落地前置）。
- 裁定：one-glossary。

**GLO-09 `decision_ref` 过不了列名守护**
- 设计 L300 `employee_activity_types` 列「decision_ref」（照 D17 细则 2 与 ADR-0061/0064 写法）。`DdlNamingTest.java` 的 ClassWord 与 NAMING_STANDARD §2.4 均无 `_ref` 类别词；现有迁移也无此列。
- 判定：字面不一致（规范内部：D17 用语 vs 列名类别词）。
- 裁定：one-data＋one-glossary（增类别词或改列名）。

**GLO-10 域内出现「老系统」**
- 设计 §9 第 3 条（L696）、§10 Q7（L712）、Q8（L713）、§11 第 3 条：申请「来源＝老系统单据」、「老系统单号」「老系统合同编号」将成为列或枚举值。
- CLAUDE.md 第 1 条「领域模块不出现外部或老系统名称」；ADR-0039 决定 1、CS-2.29；SESSION_PLAYBOOK D16「外部 ID 只作来源对照列…只在防腐层使用」。文档里可写「老系统」（CLAUDE.md 第 10 条），代码标识、列、枚举不行。
- 判定：设计缺口（须中性命名与落点）。
- 裁定：one-glossary（中性用语，范围＝领域/ACL）＋one-architecture（落在 B-31 表还是 `legacy_numbers`/来源对照）。

**GLO-11 `bcs.yaml` 对 B-31 重复登记且描述冲突**
- `docs/modernization/glossary/bcs.yaml` 内 `organization.administration` 出现两次（L173、L396），`delivery.closeout` 同样重复；L396 的描述含「IT 资产领用调拨报修报废盘点与采购申请」，设计与聚合稿明确报废、盘点、账务归 B-24（L267），采购申请不在 B-31。
- 判定：字面不一致。
- 裁定：one-glossary。

## 4. 审批接入（APR）

**APR-01 「DocumentType 仅有 QUOTE/TIMESHEET/JOURNAL_VOUCHER」已过时，doc_type 中文名的落地没写**
- 设计 L384：「当前…`DocumentType` 仅有 `QUOTE/TIMESHEET/JOURNAL_VOUCHER`；尚无上述行政代码枚举」。
- 代码：`kernel.DocumentType` 枚举已删除，换成值对象 `kernel/DocumentTypeCode.java` 与编号模块的 `platform_numbering.document_types` 登记表（ADR-0066 决定 4；种子仅 3 行，`V20261014100100__platform_numbering_baseline.sql:236`）；业务 BC 在自己的 api 声明类型码（先例 `cost/timesheet/api/TimesheetDocument.java:8`）；审批、编号写入经 `DocumentTypes`/`RegisteredDocumentTypes` 校验。
- 缺口：B-31 五个聚合各有 `document_number`，至少需 5 个类型码；登记行要 `number_code`（2–4 位大写、全局唯一）、`owner_bc_code=organization.administration`（`bcs.yaml` 已有）；种子规则版本为「待确认」，四眼确认前不能发号；这些写在 `platform_numbering` 的迁移里，设计 §7.1/7.4 的文件范围没列。
- 另：「doc_type=用印审批」是 CONVENTIONS 里单据类型的中文暂名，代码里对应两个概念（被批单据的 `DocumentTypeCode`＋流程的 `ProcessKey`），设计把它们混成一个词；NAMING_STANDARD §3（L361）「新单据类型要在 `DocumentType` 中登记」也已过时。
- 判定：字面不一致＋设计缺口。
- 裁定：one-architecture＋one-glossary；编号模块 owner 配合。

**APR-02 平台侧枚举与契约改动要走冻结通道**
- 设计：§7.5（L537）「审批模块经单独批准的类型／流程文件」。
- 代码：`ProcessKey` 是枚举（`platform/approval/api/ProcessKey.java:8`，G47 命名＝`{子域}_{BC}_{名}`），每个常量对应 `processes/{key}.bpmn20.xml`，且仍带 `documentType` 字段，ADR-0066 决定 4.4「去掉 DocumentType 入参、单据类型改为流程定义数据」尚未做；B-31 要新增的 `Permission` 枚举项（identity api）、`AuditSubjectKind`（`platform/audit/api/AuditSubjectKind.java`，仍含 `TIMESHEET`，ADR-0066 决定 4.5 待改）、`DeliveryPurpose`（见 SCH-05）都是平台 api 枚举。
- ADR-0067 决定 4：兼容新增只有三类（新方法带默认实现、可空列、新路径），枚举新增不在其中；冻结点是 W0 结束前的收口窗口，之后公共服务层改动走决定 5（one-architecture 定、one-coordinator 核对、报用户）。
- 判定：设计缺口（没有写明在收口窗口内一次做完或走哪条通道）。
- 裁定：one-architecture＋one-coordinator。

**APR-03 `ApprovalCompleted` 四种结果的映射缺口**
- 设计：§4.3（L366-372）已承认「其他退回来源及取消结果如何映射…须在接入契约明确」。
- 代码事实：`ApprovalCompleted` 含 `outcome`（APPROVED/REJECTED/RETURNED/CANCELLED）、`reason`、`returnSource`（APPROVER/INITIATOR/SYSTEM，仅 RETURNED 有）；`withdraw()`＝RETURNED＋INITIATOR，`cancel()`＝CANCELLED（`ApprovalRequest.java:267-275`）；`RefuseApprovalStart` 以 RETURNED＋SYSTEM 结束；REJECTED/RETURNED/CANCELLED 均可由发起人重启下一轮（`ApprovalRequest.java:63-67`）。
- 设计已映射：REJECTED→用印「已驳回」／IT「已退回」；RETURNED＋INITIATOR→「已撤回」。未映射：RETURNED＋APPROVER、RETURNED＋SYSTEM、CANCELLED。另：用印叫「驳回→已驳回」、IT 叫「驳回→已退回」，同一平台结果两个名字，且与审批词表里「已退回＝RETURNED」相反（GLO-07）。
- 与聚合稿的差异：聚合稿写用印「已驳回、已撤回为提前终态」，状态机分片与设计允许重提开新轮；平台侧确实允许重启，设计采纳分片，聚合稿需同步。
- 判定：设计缺口＋聚合稿与设计字面不一致。
- 裁定：one-architecture（映射表）、one-glossary（已退回）。

**APR-04 来源审批「已批准载荷及份数」没有读取接口，且先行期无来源**
- 设计：§4.4（L384）「来源审批『读取已批准载荷及份数』的公开接口也须补齐，不能直查审批 schema」。
- 代码：`ApprovalProcesses` 只有 `start/isFinished/progress(round,status)/checkWithdraw/withdraw/cancel`；`ApprovalStartRequest.formFields` 进流程后不可读；`ApprovalProgress` 无载荷。属实，为代码缺口。
- 进一步：ADR-0066 决定 3.4「平台不认识业务单据」，通用的名值读取需要各来源 BC 约定字段名；而先行期（W0）新系统里没有报价／合同审批（B-03 在 W2、B-05 在 W3；ADR-0057 影响面：REF-04 届时不存在），「来源直通」在先行期唯一可用的来源是 Q7 过渡通道。设计 §7.4（L531）却把「来源审批载荷查询」列为整卡的阻塞前置，与 CS-1.8（只实现当前阶段）张力。
- 判定：代码缺口＋设计缺口（范围与时点）。
- 裁定：one-architecture（是否纳入先行期）、审批模块 owner（若纳入）。

**APR-05 审批人解析：`cost_object_owner` 只是文档别名，兜底语义与平台实现相反**
- 设计：§4.2（L354-362）、§11 第 2 条（L731-735）「第一审批人不可用时改派兜底并发知会」「高风险用途解析结果不含兜底，缺人即挂起告警」。
- 代码：`AssignmentRule` 只有 Role／DepartmentHead／InitiatorSupervisor／FormFieldUsers（`platform/approval/domain/AssignmentRule.java`），无 `cost_object_owner`；ADR-0007 决定 3 要求业务判定由所属模块算好后以 routingFlags/表单字段传入，即 B-31 须自己按 D15 表解析并用 `FormFieldUsers` 传入。取不到人时 `AssigneeResolver.fallback()`（`AssigneeResolver.java:81-85`）自动转**流程管理员**，只有无管理员才 UNRESOLVED；本人审批则升级到上级（`ESCALATED_SELF_APPROVAL`）。设计「缺人即挂起」「不兜到总经理」与此默认行为冲突，且没说解析在哪个模块、启动前拒绝（`RefuseApprovalStart`）还是挂起。
- 判定：设计缺口（契约未定）。
- 裁定：one-architecture＋审批模块 owner＋one-security（职责分离）。

**APR-06 审批启动路径与内容哈希**
- 设计：§2.3、§4.4 只说「消费 `ApprovalCompleted`」「内容哈希」。
- 代码：有两条启动路径——同步 `ApprovalProcesses.start(ApprovalStartRequest)` 与事件 `DocumentSubmittedForApproval`（`StartApprovalOnSubmission`）；`approval.api.ContentHash`（任意非空串，指纹）与 `file.api.ContentHash`（SHA-256 小写十六进制）同名异类型。设计未选启动路径，也未区分两种哈希。
- 判定：设计缺口（小）。
- 裁定：负责 B-31 的会话；哈希叫法交 one-glossary（ADR-0064 问题单已列「文件指纹／内容哈希／校验和」）。

**APR-07 §10 引入的借阅审批使「两类人审」不成立**
- 设计：§1.1（L30）、§3.8（L330）、§4.1（L341-348）、§8.4（L629）均写两类人审；§10 Q9/Q10（L714-715）要求借出审批（含外借加审）与续借「新审批」。
- 判定：字面不一致（设计内部）；借阅审批需要自己的 ProcessKey、单据类型码、审批人表。
- 裁定：one-architecture。

## 5. 模块标识、登记与依赖边（MOD）

**MOD-01 模块标识已登记，设计仍写「待登记」**
- 设计：§2.4（L151-152）「签章服务：正式模块标识待登记」「文件服务：…以同批已批设计及用语决定为准」。
- 事实：`glossary/bcs.yaml` 已登记 `platform.signing`（签章服务，`SigningService`，L242）与 `platform.file`（文件服务，`FileService`，L248），2026-10-21 用户同意（`1156d593a`）；`architecture/ddd/contexts.yaml` 登记了 `platform.file`（待准入，owner `one-platform-file`，L134）但**没有** `platform.signing`，也**没有** `organization.administration`（设计 L146 对 administration 的陈述属实）；`module-dependencies.txt` 无 administration、无 signing，只有 `platform.file -> platform.integration`（L15-16）。
- 判定：字面不一致（过时）；签章在 contexts.yaml 的缺口属实。
- 裁定：one-architecture（登记）；设计由负责会话更正。

**MOD-02 ADR-0066 决定 7：B-31 开工即触发待准入的到期条件**
- ADR-0066 决定 7：文件服务、签章服务按「待准入」登记，到期条件「W1 结束，或首个使用 BC 开工，取早」，「到期未补齐：首个使用 BC 不得开工，并报用户」；准入条件含用语已登记、owner、守护有落点（决定 6 第 6、7 条）。B-31 是两者的首个使用 BC（审批模块不用文件服务）；IMP-1021-ADM-01 已在进行中。
- 设计：§7.2/7.3 的前置里只有「签章占号」「文件服务」，没有这道门；signing 在 contexts.yaml 尚无 owner。
- 判定：设计缺口。
- 裁定：one-coordinator（核对准入）＋one-architecture。

**MOD-03 B-31 不在 ADR-0051 的 BC 清单与审批依赖清单里**
- ADR-0051 决定 3 列 28 个业务 BC，无 `organization.administration`；决定 1 第 1 条：规范性内容（BC 的合并与新增）不一致时「另开 ADR 取代本 ADR 的相应条款」。决定 7 第 3 条的 11 个含审批聚合不含 AGG-113/114。B-31 来自用户 intent `2026-10-16-152807` 与聚合稿，ADR-0054/0057/0064 都按既有 BC 引用，但没有取代 ADR-0051 的条款。`contexts.yaml` 也仍是旧 BC 清单（`presales.customer` 等，无 `masterdata.*`），ADR-0051 决定 1 第 2 条的 SSOT 重写尚未发生，B-31 将是第一个提前登记的新 BC。
- 判定：字面不一致（规范性清单缺项）。
- 裁定：one-architecture（补充 ADR 还是以用户 intent 为据登记）。

**MOD-04 B-31 → accounting.fixedasset：方向合规，时点与先行范围冲突**
- 方向：ADR-0057 决定 1 明文保留「B-31 → B-24（api）资产卡片引用」；无环。
- 时点：ADR-0054 把固定资产放 W5；ADR-0067 决定 3 第 4 款列出先行上线所用模块（approval、identity、audit、文件服务、签章服务一段、`organization.administration`）不含固定资产；ADR-0051 决定 9 第 1 条「任一 BC 不早于其全部 api 或事件上游可用」。`accounting.fixedasset` 在 contexts.yaml 仍是 `planned`，代码不存在。设计 §8.3（L617）只写「必须明确最小卡片身份及读取能力」，未点出与上述三处的冲突，也没给选项。
- 判定：设计缺口（涉及 ADR 冲突，须裁定）。
- 裁定：one-architecture。

**MOD-05 设计 §2.4 依赖边逐条核对（均不成环）**

| 边 | ADR／代码依据 | 判定 |
|---|---|---|
| B-31 → `organization.workforce`（api、events） | ADR-0051 决定 7（L2 上游）；离职事件由 ADR-0064 决定 5.4、影响面要求 workforce 补发，ADR-0068 决定 1.9 定义（`EmployeeSeparated`/`EmployeeHired` 暂名，业务日期＝版本生效日）；代码目前无此事件，`EmployeeProfile` 无入职日期 | 一致；设计未引 ADR-0068（REF-02） |
| → `accounting.fixedasset`（api） | 见 MOD-04 | 方向一致，时点缺口 |
| → `platform.approval`（api、events） | ADR-0007 决定 2；审批出边为 identity、workforce、integration、audit、attendance、numbering，不含 B-31 | 一致 |
| → 签章服务（api） | ADR-0064 决定 1.3：用印申请调用签章服务，签章服务不依赖 B-31；守护禁止边 签章服务 ↛ administration、`platform.approval` ↛ 签章服务 | 一致；设计 §7.2「审批平台不反向依赖 B-31」措辞宽于 ADR 守护但不矛盾 |
| → 文件服务（api） | ADR-0060 影响面列 `organization.administration`（W0）；`platform.file` 不依赖业务 | 一致 |
| → `platform.numbering`（api） | ADR-0066 决定 4；`approval -> numbering` 已有（REG-1，依赖表 L8） | 一致 |
| → `platform.audit`（api） | 审计 `AuditSubjectKind` 仍枚举业务对象，B-31 需新增主体种类（APR-02） | 方向一致，平台改动缺口 |
| → `platform.integration`（api，条件登记） | `DeliveryPurpose`/`DeliverySubjectKind` 只有审批类；`Wecom*` 待中性化（ADR-0067 决定 2） | 方向一致，B-31 提醒无目的枚举（SCH-05） |
| REF-04 → B-31（api） | ADR-0057 决定 1 | 一致（接口方向见 REF-01） |
| 不加 → `platform.scheduling` | CONVENTIONS「定时触发」 | 一致；聚合稿 §4.1 L850 与 `b31.processes.yaml` L520/L656 仍留此边，待 SSOT 重建去掉 |
| 不加 → `platform.identity` | workforce 先例（依赖表注释 R-1b P2）；缺共享 `CurrentAccess`（ADR-0026 决定 1.1，代码未落，`kernel` 仅有 `OrgUnitAccess`） | 一致；`Permission` 枚举在 identity api，新增权限码要改平台文件 |

- 漏边：没发现 ADR 要求而设计漏掉的边；但 ADR-0066 决定 4 要求审批、编号写入时校验单据类型码，B-31 经 `numbering` 边满足。
- 新边落依赖表的前置：`module-dependencies.txt` 需同批登记；`ModuleDependencyTest` 需加禁止边（ADR-0064 守护 1、ADR-0057 守护）。

## 6. 定时（SCH）

一致部分见第 0 节。偏离与缺口：

**SCH-01（一致）** 锁名 `organization.administration.<名>` 满足 G46（`checkDottedName(…, moduleOf(type))`，`NamingSurfaceTest.java:340-355`）。

**SCH-02 周年待生成投影不在 CONVENTIONS 覆盖内**
- 设计：§5.3（L418）建议增加「B-31 自有、可重建的待生成投影」，因首次生成前尚无 AGG-116。
- CONVENTIONS：到期扫描对象是「聚合上的到期时点列」。投影表不在 13 张既有表、不在聚合清单；D1 要求投影登记、可重算、有对账；系统作业身份须在 `system-actors.txt` 登记（ADR-0058 决定 1 `actor_id`）。可选替代是消费 workforce 入职事件（ADR-0068 决定 1.9）而不建投影，设计未比较。
- 判定：设计缺口（CONVENTIONS 未覆盖，须裁定）。
- 裁定：one-architecture＋one-data。

**SCH-03 定时入口位置**
- 设计：§2.1（L111）把「本 BC 定时入口」放在 `application`。现有作业都是 `infrastructure/scheduling` 下的薄类（如 `organization/workforce/infrastructure/scheduling/ShortNameRefreshJob.java`、`platform/identity/.../SessionPurgeJob.java`），再调 application 用例；NAMING_STANDARD §3 速查表对此无明文。
- 判定：字面不一致（与先例）；不违反 G46。
- 裁定：one-architecture（统一 §2.1 与 §5.1）。

**SCH-04 G46 其余子句未覆盖**
- G46 还要求指标名为字面量且按模块前缀、`job_type` 受检、配置键以 `forcitis.organization.administration.` 开头；设计 §5.1 把周期等留给实施卡，可接受，但未登记这三项。
- 判定：设计缺口（小）。
- 裁定：负责 B-31 的会话。

**SCH-05 §10/§11 新增作业与待办没有承载**
- 设计 §5.4（L432-436）「不设置的作业」、§7.9（L566）仍只含离职归还与周年；§10/§11 新增：权限工单到期「自动收回或提醒」（Q5）、借阅逾期两级提醒（Q10）、季度复核与月度抽检「生成待办」（§11.3）。
- 问题：(a) 「自动收回」是权限授予的收回，归 identity（ADR-0026 决定 6、ADR-0034），设计也无对应边；(b) 「待办」落在哪里没有——ADR-0066 决定 9.3「提醒与待办：未准入，先定 scheduling 的定位」，审批收件箱只服务审批任务；(c) 提醒走 `WecomGateway`，但 `DeliveryPurpose` 只有 6 个审批用途（`platform/integration/api/DeliveryPurpose.java`），归还提醒、周年通知、逾期提醒无用途，需改平台 api（APR-02 同理）；(d) Q10 逾期升级的「7 天」是另一个固定到期时点，须按 CONVENTIONS 写幂等键。
- 判定：设计缺口。
- 裁定：one-architecture（待办载体、是否仅提醒）、integration owner。

**SCH-06 离职起算点要钉到版本生效日**
- 设计：§5.2（L405-410）「人事层离职已生效后」「晚到、重复事件不改变起算点」✓。
- ADR-0068 决定 1.5、1.9：离职版本生效日＝最后工作日次日业务时区零点；事件在命令生效时由到日作业发布，信封 `businessDate`＝生效日。设计应写明起算点＝`businessDate`，而非事件发布时刻。
- 判定：设计缺口（小）。
- 裁定：负责 B-31 的会话；one-rules 在规则卡里固定。

## 7. 设计内部陈旧与规则稿（DOC / RUL）

**DOC-01** L16「读取到的签章稿状态行仍为“提议”」与 §0.2（L21）「签章草案即 ADR-0064（采纳，PR #488）」自相矛盾；§8.2 #4（L608）「签章正式编号及获批决定须入仓」也已完成（ADR-0064 已入 `adr/src/`，状态已采纳）。
**DOC-02** §4.2（L359-360）「加审范围…待 B31P；AR-16 候选不能直接启用」与 §10 Q2（L707）已给出加批规则不一致；§5.3（L425）、§8.1（L588-599）仍写 B31P「未发现 answers/B31P.json」「全部待 B31P」，与 §0.1（L20）已答矛盾（§0.1 要求按其读，但正文未改）。
**DOC-03** §1.1（L66）借阅属 B-05、不并入 B-31，与 §10 Q8（L713）矛盾（DATA-07）。
**DOC-04** §3.1 的「13 张既有设计业务表」未随 §10/§11 更新（DATA-05、DATA-06），§7 施工卡文件范围也未加 §10 末（L725）所列新增三张卡的细目。

规则稿 `B31_RULES.yaml`（头部状态仍为「待用户逐条确认的草案」）：

| 编号 | 规则稿 | 与 §10/§11 或 ADR 的差异 | 裁定 |
|---|---|---|---|
| RUL-01 | BJ-02「内容未定的页面不得预先盖章」；open_points「未定前按 BJ-02 拒绝」 | §11 第 1 条：投标可预盖空白页 | one-rules |
| RUL-02 | JS-01「按用途和法人」配置负责人 | Q1：按用途定审批人；第一审批人＋兜底（岗位引用） | one-rules |
| RUL-03 | JS-02「合同章或法人章…用印法人的授权负责人」 | Q2：合同章、法定代表人名章、合同专用章，由「合同签约主体所属法人」的负责人批（用印法人是否等同未写明） | one-rules |
| RUL-04 | JS-03 专项事项加审人 | Q2：担保类必须附决议或授权文件，不附不予盖章 | one-rules |
| RUL-05 | YY-02「按用印法人和用途引用台账条目」 | ADR-0064 决定 3.1：台账条目＝法人×印章类型，不含用途；Q1 表按用途 → 缺用途→印章类型对应规则 | one-rules＋one-architecture |
| RUL-06 | HD-03「有花费的先行期只填预估金额」 | Q6：登记时预估金额必填（字面范围不同） | one-rules |
| RUL-07 | IT-01/IT-02 把类别清单写进规则文本 | Q4：类别为可增删改的 D15 数据，不写死 | one-rules |
| RUL-08 | 无条目 | Q5 有效期、Q7 过渡通道（含季度复核、月度抽检、只追加冲正）、Q8–Q10 借阅、§11.2 兜底四条规矩、B31R 后新增的 INV | one-rules |

## 8. 其他观察（不在 7 项要求内，供裁定方参考）

- 全部 5 张既有 `data-model` 表均写 `versioned: none`、理由「状态由审计还原」（D17 第四类，待 one-data 复核），与 ADR-0058 决定 7（审计不是历史来源）冲突；设计 §3.8 已用 `state_transitions` 修正，但分片理由未改。
- `contexts.yaml` 仍登记 `platform.migration`，与 ADR-0066 决定 8（不设该平台模块）不符；非 B-31 范围，仅提示。
- ADR-0064 自身：决定 1.3 列签章服务依赖 `platform.idempotency`，决定 4.4（X1 订正）又说不依赖；B-31 无关，但签章边落依赖表时会碰到。
- ADR-0060 迁入入口写「仅 `platform.migration`」，ADR-0066 决定 8 已改为迁入编排流程模块；B-31 迁入附件时按后者。
