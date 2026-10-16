# 人员组 NOTES（one-arch-people）

按 `../CONVENTIONS.md` §4 固定小节。每节按来源分：B-13/B-14/B-27 表（试点）与各分片（`parts/*.notes.md` 合并，分片文件保留作底稿）。样板见 `SAMPLE.md`。编号如「V-n」「U-n」「PG-n」为各分片内部编号，合成确认页时由 one-architecture 统一。

## B-13 长期目标（用户 P-01，经 one-architecture 转）

- 用户原话：「这个 BC 后期要持续建设直至满足 HRM 的总体功能要求。」B-13 `organization.workforce` 的长期目标是覆盖 HRM 总体功能；本期范围按已定口径（入转调离、招聘、劳动合同、执业证书、绩效评估、职位目录与法人归属），不扩。

## 接缝问题

### B-13、B-14、B-27 表（试点）

1. **B-14 → B-13 的法人引用无登记边**：`work_calendar_years.legal_entity_id`、`standard_work_hour_versions.legal_entity_id` 引用法人（AGG-60，B-13）；聚合稿 §4.1 只登记 B-14 → platform.audit。按 §2.1，下游引用上游应走 api 成边；B-14 与 B-13 同在 L2，加边是否成环或违层，请 one-architecture 定：加 B-14 → B-13（api）边，或法人 ID 按「不校验」存放并在 AGG-108 发布命令里由调用方保证。
2. **法人三口径不一致预警的归属**：劳动合同用人单位（B-13 AGG-67）、数据范围法人（B-13）、个税扣缴义务人（B-15 AGG-93 / 95）要比对。B-15 → B-13 有 api 边，预警由 B-15 读 B-13 做最顺；若放 B-13，会出现 B-13 → B-15 的反向读取（聚合稿 §4.1 无此边）。请定预警归属与方向，本稿不新增表。
3. **考勤记录 AGG-69 的数据入口**：日常打卡来自企业微信，经 platform.integration 进 AGG-69（用户事实 intent 2026-10-16-152603）。B-27 → platform.integration 边已登记；外部打卡记录 ID 只作来源对照。需确认 platform.integration 提供的是拉取适配器（防腐层转换后调用 B-27 api 写入）还是事件，及外部读取的超时与降级（§14.3 I2、I7）。历史打卡（A8，约 79.5 万行）走迁入通道，来源对照要区分来源系统。
4. **B-06 读请假与标准工时**：INV-65 需要 B-27（已批准 + 待批请假时长）与 B-14（标准工时），§4.1 已登记 B-06 → B-14、B-27；仅提醒请假行的敏感度 tbd（病假）会限制 B-06 读取的出参，应只返回时长，不带假期类型与事由。

### 入转调离（PRC-79–82、85、128–130、136、138）

1. **B-27 订阅 Separated 缺事件边**：PRC-128 步 7 向 B-27 发 Separated，让 B-27 生成「离职当月考勤待代确认」（待用户 V-3）；§4.1 只有 B-27 → B-13（api）。方向对（下游订阅上游），请补「事件」方式。调岗对请假审批人与在途单据是否联动（PRC-136）同样需要 B-27 订阅「任职已变更」，待 B-27 组确认。
2. **B-31 订阅 Separated，B-13 不依赖 B-31**：按 NOTES「聚合稿回补」第 1 节的建议处理——B-31 订阅 Separated 生成归还待办（b31 PRC-143 已按此写），PRC-128 只发事件，不读 B-31，不等待归还（工作交接清单是单据不作门，VS-13.6 备注）；B-31 → B-13 现有 api，事件方式随 B-31 新边一并登记。时点上 Separated 在离职日结束才发，归还待办晚于最后一天，建议对外发「离职已登记」让 B-31 提前（待用户 V-15）。
3. **platform.identity、B-15 对 B-13 事件的订阅登记**：聚合稿 AGG-62、63 的订阅方列需补——platform.identity：Separated（已有）、任职已变更、Employed、入职已登记、〔缺〕法人归属已变更；B-15：Separated（已有）、任职已变更、〔缺〕转正已办理、〔缺〕法人归属已变更。边 platform.identity → B-13、B-15 → B-13 已有，方向一致，不新增边（PG-02）。
4. **企业微信停用不再产生 integration → B-13 的反向事件（PG-04 对停用不再适用）**：PRC-138 步 6 由 platform.identity 经 api 同步调用 platform.integration 停用成员，边 platform.identity → platform.integration 已有，B-13 不参与，不再需要「企业微信成员已停用」事件。试点稿 PRC-138 的该事件与 `kernel.ExternalSubjectEmployees` 反向端口处理见 IDENTITY_LIFECYCLE「C1 一并要做的实现项」。
5. **P-31 策略要扩触发**：「标记数据范围待复核并通知区域负责人」现只由组织单元已调整触发（PRC-80 步 5），PRC-136 步 11 需增「任职已变更」触发，生成待调整权限（ADR-0032 第 4 项两类条目）；请 one-architecture 把 P-31 的触发列扩为两个事件，并定该策略归 platform.identity 的编号（目前挂在 PRC-80、136 的步骤里，没有独立 PRC 号）。
6. **PRC-136 向 B-15 的接缝**：任职已变更 → B-15 调整薪酬档案（AGG-93）；跨法人时还要变更参保缴纳主体（AGG-74 〔缺〕命令）。B-15 → B-13 边已有，AGG-63 订阅方列需补 B-15（PG-07）。
7. **ADR-0027 第 7 点的载体变了**：该点按「同步运行」写（HELD），B-13 自为记录系统后无同步运行；PRC-138 步 3–4 改成按离职事件判断：涉及超管 / 安全审计员持有人，或会使可用超管少于 2 名，则删除角色授予暂停、人工放行。ADR-0027 的措辞需 one-architecture 或安全组重述（结构问题 11）。
8. **离职人员的 userid 与工号**：企业微信 userid 取工号（SAMPLE S6）；离职者重新入职（返聘、再入职）是否沿用同一人员 ID 与工号，影响 userid 能否重用与「解绑后保留历史」，请 one-architecture 与 one-data 定（待用户 V-16）。

### VS-13 获得报酬（PRC-113、115–127、134、145）

1. **审批结果回调与取消**：B-15、B-27 → `platform.approval` 在 §4.1 只登记 `api`，而审批通过 / 驳回要回到业务聚合（批准请假、批准加班、批准薪酬等）；平台不能依赖业务模块，只能由业务 BC 订阅平台事件，边需补「事件」方式，事件名〔缺〕「审批已通过」「审批已驳回」。另需平台提供「取消审批实例」（审批中撤销请假，PRC-116 步骤 8）。PRC-116、117、121、124 均受影响。
2. **定时触发的边**：PRC-115（每日）、118（月末与确认期限届满）、120（评估期末）、125（申报期提醒）都靠 `platform.scheduling`，B-27、B-13、B-15 → `platform.scheduling` 在 §4.1 无登记（STEP6A G-04 未处理）；yaml 不写 cross_bc，待统一约定。
3. **代发：员工银行账户与 B-10 收款方**：B-10 的 AGG-53 收款方只引用 AGG-01 往来单位的银行账户（INV-80），应付薪酬（AGG-52）是法人 × 月一笔合计，没有“员工 × 账户 × 金额”的代发明细。代发明细在 B-15（AGG-73 薪酬行），但 B-10 不能依赖 B-15（B-15 → B-10 已有，反向成环）。选项：① B-15 提交应付薪酬时把代发明细作为附件引用随 api 参数交给 B-10，由付款执行时生成银行代发文件；② 代发文件由 B-15 生成，B-10 只付一笔合计给代发银行（往来单位账户，符合 INV-80），员工到账由银行代发；③ 员工作为 AGG-01 的个人往来单位登记银行账户（与薪酬档案账户重复，且个人信息扩散）。员工银行账户（SENSITIVE）放在 AGG-93 还是 B-13 也待定。建议 ②，代价最小，但退票按人处理见结构问题 2。
4. **股息红利个税扣缴额须在支付前确定**：PRC-113 订阅“利润分配已支付”，此时钱已付；个税应在支付时代扣，实付给自然人股东的是税后金额，B-10 在提交应付利润时就要知道扣缴额，但 B-10 不得读 B-15（反向成环）。选项：在 AGG-109 的分配行上由 B-10 按股东类型和法定税率算出代扣额并随事件带给 B-15；或扣缴额由 B-15 在“利润分配已决议”（现为仅分析）时计算，再由 B-10 经 api 读取——后者需 B-10 → B-15 边，不可。建议前者；税率 20% 见个人所得税法第三条〔高〕，是否按股东类型分别处理交财务。
5. **缴款资金与银行流水**：AGG-95 只“登记缴款”，缴款凭证与银行流水（B-22 AGG-88）的对账无引用；B-22 不能引用 B-15。个税与社保公积金多由银行三方协议扣款〔中〕，流水对账归 B-22 的匹配规则，需 one-architecture 定是 B-22 订阅“个税与社保公积金申报已提交”还是 B-15 经 api 读取流水。
6. **企业微信成员到人员的对照位置**：PRC-115 步骤 1 假定来源对照在 `platform.integration`（NOTES 数据接缝 3 已提，D16 外部 ID 只作来源对照，userid 不留在 `employees`），B-27 → `platform.integration` api 可解析；若对照放在 `platform.identity`，B-27 无到它的边。请确认拉取适配器由 integration 调 B-27 写入还是 B-27 调 integration 拉取（本稿按后者，方向合乎 B-27 → integration）。
7. **审计边**：B-15、B-27 在 §4.1 没有 → `platform.audit` 的边，而本稿多处 `audit: true`（薪酬计算、批准、代确认、调薪）。请确认审计是框架级拦截设施不需边，还是补边。
8. **B-14 → B-13 法人引用**：沿用 NOTES 数据接缝问题 1（AGG-108 法人可空引用 AGG-60），本稿 PRC-119 未再处理。
9. **转正与调岗联动薪酬**：沿用 PG-07、PG-10：PRC-85 办理转正、PRC-136 调岗不通知 B-15，PRC-123 的调薪靠人力人工发现；B-15 订阅“任职已变更”的订阅方列待补。

### B-15 薪酬表

1. AGG-76 → AGG-73 的「已发放」：奖金核定状态「已发放（经月度薪酬结算）」由 AGG-73 的「薪酬已发放」同 BC 事件置位；表上不存结算引用，只在薪酬项目行存奖金核定 ID。聚合稿 AGG-76 的引用列无「订阅 AGG-73」，同 BC 无需边，但应补一行事件订阅说明。
2. 个税申报需证件号码，B-13 `employees` 目前不含证件号（B-13 NOTES 待用户定 1 未决）。本稿让 `tax_social_filing_lines` 在生成时快照证件类型与号码（列级 SPI）。若人员档案不收证件号，B-15 无处可取；B-15 → B-13 的 api 读取证件号须待该题定后补。
3. 发薪：AGG-52 应付薪酬是法人 × 月一笔实发合计，收款方是代发银行或代发批次，而 B-10 付款要求收款方为往来单位账户；员工发薪账户在 AGG-93 版本，不进 B-10。银行代发明细文件由谁生成、是否经 platform.integration，聚合稿未写；见待用户定 5。
4. AGG-95 订阅 AGG-109「利润分配已支付」：聚合稿写「沿已有边 B-15 → B-10」。CONVENTIONS §2 规则 2 字面「event 由发布方 → 订阅方」，而 §4.1 的箭头是「依赖方 → 被依赖方」，同一事件方向两种读法。本稿 `cross_bc` 无需写，但请 one-architecture 在 CONVENTIONS 写明事件的箭头语义。
5. AGG-73 读 AGG-97「月度考勤已锁定」：B-15 → B-27 边存在（api、事件），无问题。AGG-95 读 AGG-81 会计期间：B-15 → B-19 存在。均已核对。
6. 扣缴义务人是三口径之一：`payroll_profile_versions.withholding_legal_entity_id` 与 `payroll_settlement_lines.withholding_legal_entity_id` 分别在档案与结算行，结算行是批准时快照；PRC-145 预警读档案版本，不读结算行。无新边。

### VS-09 / VS-11 与 B-18、B-30 表

1. [流程][数据] AGG-79 引用列缺人员：销售预测承诺的目标值按人员 × 期间 × 指标（PRC-89 步 1，信息图 M-8），聚合稿 §2.2 AGG-79 引用列只有 AGG-61 组织单元和 platform.approval。B-18 → B-13（api）边已有，只需把 AGG-62 人员补进引用列，不加边。表里 `plan_target_values` 已按此建。
2. [数据] 预算占用方单据的引用：AGG-80 的占用由 B-11（AGG-55 供方协议）、B-12（AGG-59 费用报销）调用。B-18 不能依赖下游，所以占用行里的占用方单据 ID 只能是「不校验」引用；聚合稿 AGG-80 引用列只写了 AGG-79，「不校验」清单（§4.1）也没有这两项。请 one-architecture 补登记并更新「不校验」计数。表里未放进 `references`。
3. [流程] 审批结果回调方向：PRC-88 步 2 经 platform.approval 审批，结果回调「通过 / 驳回经营计划审批」调 B-18 的命令，而 §4.1 的边是 B-18 → platform.approval（api），回调方向相反。AGG-12 等已用同样写法（intent 114200 F-2），这是各组共性，本稿不改边，请 one-architecture 在审批回调的统一约定里一并说明。
4. [流程] 项目复盘提炼的触发：v1 PRC-107 步 2 写 `on_event: 项目复盘已发布`，好像 B-30 订阅 B-04 的事件。聚合稿 AGG-103 说订阅方是 B-04 自身，由复盘编制人决定后调 B-30 的创建命令；B-04 → B-30（api）边已有，方向一致。所以本稿触发写 `type: call`（调用方 B-04），不写 event，B-30 不订阅任何事件。
5. [数据] 外部知识平台文件夹引用走 platform.integration 来源对照后，B-30 若要读取来源对照以展示外部链接，需要 B-30 → platform.integration（api）边，聚合稿 §4.1 中 B-30 只依赖 B-13。若不展示外部链接（只作迁入来源），则无需加边，请定。加边的层位影响：platform.integration 在 L0，B-30 在 L3，不改层。

### B-31 行政事务（PRC-139、142–144，AGG-113–116）

1. **维修无法修复后的报废**：PRC-143 步 12 登记维修结果为「无法修复」时，需通知财务在 B-24 走报废。B-31 → B-24 只有 api 读取边；若 B-31 发事件给 B-24，则 B-24 成为订阅方、依赖 B-31，与回补边方向相反（回补：无反向边）。本稿不画 cross_bc，结果只存工单。建议：财务按 B-24 报废流程自行发起，维修结果以读模型（reporting）的「待处置资产」清单呈现；如要事件，需 one-architecture 加 B-24 → B-31 的订阅边并核层级。
2. **盘点读使用记录**：盘点属 B-24（财务组），要核对「谁在用」需读 B-31 的使用期间，这是 B-24 → B-31 的读取，聚合稿无此边。建议同上用读模型，或盘点页面走报表。请 one-architecture 定。
3. **离职归还待办**：PRC-143 步 9 让 B-31 订阅 B-13 的 `Separated`（回补建议）。方向与 B-31 → B-13 的依赖一致，不违边，但 §4.1 边表与 `Separated` 订阅方清单（`docs/modernization/events/*.yaml`）须补 B-31；PRC-128 的离职交接若要「清单」，由读模型汇集。
4. **报价是否需「已用印」才能发出**：A8 盘点说明写「报价用印登记与老系统报价同步相关」。若新系统的报价发出要读 B-31 用印状态，会形成 B-05 → B-31 的依赖，而回补明确来源业务单据「不校验」、B-31 不被业务 BC 依赖。本稿按不校验处理；如业务要求，请 one-architecture 定边或改由用印申请带「来源单据」被动关联。
5. **审批回调方向**：回补命令列写「批准用印申请…由 platform.approval 回调」，即 platform.approval 调用 B-31 命令。依赖方向是 B-31 → platform.approval，回调实现须按 ADR-0036 / 审批平台已有机制（单据订阅 `DocumentSubmittedForApproval` 与审批结果事件），不得让 platform 依赖业务 BC。请 one-architecture 确认回调走事件或注册的处理器，而不是 platform → B-31 的 api 调用。
6. **回补新边合计**：B-31 → B-13（api，读法人、员工）、B-31 → platform.approval（api）、B-31 → B-24（api，读卡片）；另加订阅 B-13 `Separated`（事件）。无反向边、不成环。

## 结构问题

### B-13、B-14、B-27 表（试点）

1. **B-13 自为人事记录系统的改写后形态（已定事项的落地改写，不是待用户题；用户 2026-10-16 U-1、U-7，one-architecture 裁定）**。请汇入过渡 ADR 与聚合稿修订：
   - 快照来源撤销：AGG-61 / 62 / 64 的「现为同步快照 `OrgSnapshot` / `Position`」改写为新系统自有主数据；注册表 `lineage: ORG_SOURCE`、`role: SNAPSHOT`、`retention: CURRENT_STATE` 对应改为 `NEW`（迁入行经 `migrated_from` + 来源对照）、`AGGREGATE_ROOT`、`PERMANENT`；删除 `last_sync_run_id` 与对应 `fk_exceptions`。
   - 根表只留标识与不随时间变的列；会变的列进版本表；版本表 `OBSERVED` 改 `DECLARED`（U-12）。
   - `sync_runs`、`sync_diffs`、`sync_run_held_separations` 及事件 `sync_run_*`、词表 `sync_run`（ACL 聚合）退役：自成体系后无运行时同步；迁入期的批次、暂存、拒收、核对由 platform.integration 的迁入登记承担（ADR-0036 第 3、7 条）。建议基线开放期内直接移除；若需保留迁入窗口内的差异记录，只留 `sync_diffs`，窗口结束后清除。
   - 组织单元「撤销」「有在职任职不得撤销」（INV-60）从「6e 起」改为首版生效（新系统即组织来源）。
   - `positions`（岗位快照）退役，由职位目录（`jobs` 等）取代；`assignment_versions` 的岗位引用改为职位 + 职级两列。
   - 企业微信 userid 不留在 `employees`，走 platform.integration 来源对照；登录绑定归 platform.identity（Q-03）。
   - 入转调离（`Employed` / `Separated` 等）由本 BC 命令驱动，账号开通与吊销为安全关键事件反应（§1.4 G-2），可靠性要求高于现状。
2. **AGG-64 范围**：聚合稿写作「岗位 一对一」，而职位目录（JOB_CATALOG P-0、P-6）含职位、职位族与子族、职级三类各自版本化的对象。本稿暂都放在 AGG-64 下（6 张表）。建议拆为「职位目录」（职位、职位族、子族）与「职级」两个聚合，或确认一个聚合含 6 张表可接受（D3 子表随根写入；职级被费率表引用，生命周期不同）。
3. **「法人归属」聚合号（已解决）**：已补号 AGG-117（B-13，one-architecture 2026-10-16，PRC_ALLOCATIONS）；本稿 aggregate 与 references 已全文改用 AGG-117。聚合稿 §2.2 补行仍请 one-architecture 办。
4. **`regional_receivables_manager_id`（区域应收账款负责人）**在 `org_unit_versions`：这是应收催收域的属性，不是组织结构。建议移到 B-09 receivables.collection（按组织单元 ID 取区域负责人，带生效区间），B-13 不存。请定归属。
5. **AGG-65 招聘需求**：Q-08 已关闭编制，招聘需求原依据 Q26「超编警」，现已无；是否保留 AGG-65、保留什么范围（仅需求—批准—关闭，不含人数上限）请定。本稿按保留写。
6. **技能标签与技能目录**：信息图 OBJ-56 把「技能标签」（多值）写成人员属性，技能目录为参考数据，供资源需求匹配（M-13），聚合稿 AGG-62 与 B-18 均未列表。请定归属，本稿未建表。
7. **人员档案范围**：人事记录系统要不要装证件号码、出生日期、私人联系方式、学历、紧急联系人等（劳动用工实务常见），聚合稿 AGG-62 只写用工类型、状态。本稿未建，见待用户定 1。
8. **B-27 名称与范围**：BC 名「请假与加班」，实含考勤原始记录（AGG-69）与月度考勤确认（AGG-97）；模块 `organization.leave` 与词表 `attendance`（考勤与休假，归 organization.attendance）存在名称重叠。请定是否改名（如「考勤与休假」）或词表归属。schema 名本稿暂按模块名 `organization_leave`。
9. **假期类型与额度规则（号已补）**：已补号 AGG-118「假期类型与额度规则」（B-27，配置型版本化 D15，one-architecture 2026-10-16）；`leave_types` 与 `leave_entitlement_rule_versions` 归 AGG-118，aggregate 与 references 已改用该号。仍请确认配置型维护流程（谁确认）与 `leave_types` 是否改版本化。
10. **AGG-106 子单据**：信息图 OBJ-109「单据：管理层一对一、成长记录」只列名称，本稿 `performance_review_documents` 为推断，待第 6 步复核。
11. **`employees` 表名**：词表已有 `employee`（员工，聚合）、`person`（人员，实体）、`registered_employee`（登记员工，聚合）；聚合稿 AGG-62 名称是「人员」，含候选人。表名沿用已建 `employees` 与候选阶段的语义不合（候选人不是员工）；备选改 `people`。请与 one-glossary 一并定。
12. **工作日历配置型版本化的形状**：见 DATA_MODEL.md 口径 1，若改形会影响 AGG-68 命令「维护工作日历年度」的写法。

### 入转调离（PRC-79–82、85、128–130、136、138）

1. **AGG-62 离职要两段，与入职对称（PG-12）**：聚合稿「办理离职 → Separated」，但 U-12 允许提前登记、到日生效。本稿改为：办理离职（登记离职日，→ 内部）与〔缺〕离职生效（系统定时，离职日结束时执行，→ Separated）。需要与 PRC-83 的「入职生效」同一个「定时命令」约定（STEP6A G-04 未处理）。离职日（最后在职日）、生效时点见待用户 V-1。
2. **PG-12 的处置选择**：本稿让 Separated 先于终止任职与终止劳动合同发出（PRC-128 步 7→8、9），理由是 RULE-363 安全关键，吊销不得被合同或任职终止的失败拖住；代价是 platform.identity 会先后收到 Separated 与「任职已变更」，必须对已离职人员忽略后者。另一方案是「先终止任职与合同、最后发 Separated」，失败则整体不生效，不采纳（见 PRC-128 步 7 说明）。请 one-architecture 在 AGG-62 / 63 / 67 与 platform.identity 订阅策略上确认。
3. **AGG-62 缺命令（PG-09）**：〔缺〕发起公司终止、〔缺〕驳回离职申请（含员工撤回）、〔缺〕撤销离职登记、〔缺〕离职生效；AGG-67 缺〔缺〕登记劳动合同终止依据（协议或解除 / 终止通知的线下文件，离职与调动都用）。数据组需在 `separation_requests` 增终止依据与撤销状态（本稿不改表）。
4. **PRC-82：录用决定已作出的发出点**：聚合稿把事件挂在「作出录用决定」，但 PRC-83 的触发语是「候选人确认入职日后」。本稿在「作出录用决定」之后加〔缺〕登记候选人答复（线下回执，offline），由它发事件。请 one-architecture 在 AGG-62 把「录用决定已作出」改挂到登记答复，或把事件改名为「录用决定已确认」，二选一；候选人放弃、撤销录用（PG-09）同在此处。
5. **AGG-63 缺调岗申请与原子变更（PG-05）**：本稿新增〔缺〕提交 / 批准 / 驳回调岗申请、〔缺〕变更任职（原子：关旧开新，主任职不重叠）与〔缺〕任职变更生效（定时，在业务生效日发「任职已变更」）。调岗申请放在 AGG-63 次级还是另立聚合，请 one-architecture 定；写入未来版本时 INV-62 的排他约束在提交时校验，与生效日无关。
6. **「任职已变更」的发出时点同样是业务生效日**（U-12）：提前登记的调岗、离职时的终止任职，事件都应到日发出，而不是写版本时发出（PRC-136 步 7–8）。
7. **AGG-106 → 转正**：试用期评估已确认事件须带评估类型（月度 / 试用期），PRC-122 的触发事件同源，避免订阅方混淆；〔缺〕转正已办理事件让 B-15 复核转正后薪酬（PG-10）。试用期届满是定时触发（定时命令约定缺）。
8. **AGG-64 职位目录命令缺**：聚合稿只有「停用岗位」。新建 / 修改职位、职级、职位族的命令缺，PRC-80 只能写停用；Q-08 后「岗位」改称职位目录条目，命令名交 one-glossary。AGG-64 暂不拆（S-10）。
9. **PRC-136 挂 VS-07.3 是借用**：VS-07.3「入职任职」的进入条件是人员「待入职」，调岗发生在在职期间，价值流没有「在职任职变动」阶段；任职（OBJ-57）是版本化对象，没有状态机，无阶段门可挂。建议 one-architecture 在 VS-07 增一个「任职变动」阶段，或认可流程可只挂 VS-07.3。PRC-138 挂 VS-13.6：阶段备注已写「账号停用是平台身份事项，不是业务对象状态」，不作门。
10. **PRC-79 的学时达标不在 §1.2**：「达标是延续注册条件」写在 AGG-66 内含里，没有 INV 编号。延续注册是在注册执业管理机构办理后的登记，系统拒绝会丢真实事实，本稿按预警写（待用户 V-14）；是否升为不变量请定。
11. **ADR-0027 第 1 项第 6 点、第 7 点需重述**：现按「同步把某人标为离职」「同步运行 HELD」措辞，B-13 自为记录系统后改为按 Separated 事件；词表「离职」（bc: platform.identity）的定义已含三项处置，与 B-13 业务词「离职」同名异义，交 one-glossary 登记（见新用语）。
12. **跨法人调动的合同模型**：AGG-67 没有「变更用人单位」命令，本稿用「终止原合同 + 与新法人另订合同」表达（工龄连续）。若法务要求同一合同变更主体而不终止，AGG-67 需增变更主体命令（待用户 V-7）。

### VS-13 获得报酬（PRC-113、115–127、134、145）

1. **缺“薪酬规则版本”配置（AGG-73 自算的前提）**：薪酬自算后，计算必须有版本化规则：个人所得税税率表与基本减除费用、专项附加扣除标准、社保公积金个人与单位比例及缴费基数上下限（按城市类别）、计薪天数（月计薪天数 21.75 天，劳社部发〔2008〕3 号〔高〕）、法定代扣项目清单（INV-70）、全年一次性奖金计税方式〔中〕。提议新聚合 **薪酬规则版本**（B-15，配置型，D15，每版本一个聚合 + 生效区间不重叠，沿 AGG-108 的形态；编号待 one-architecture 要号，暂 `AGG-待定-薪酬规则版本`），命令〔缺〕发布薪酬规则版本 → 〔缺〕薪酬规则已生效；AGG-73 按薪酬期间读取生效版本，取不到失败并告警（同 INV-116）。同时 **聚合稿“薪酬汇总导入”口径作废**：AGG-73 引用列中“platform.integration 薪酬汇总导入与个税扣缴端（api）”应删除“薪酬汇总导入”，“个税扣缴端”随申报方式 U1 定（方式 a 无需集成）；AGG-73 命令“计算”的 6e 前注释（FD-30）与 §4.3.2 步骤 4 同改。
2. **AGG-73 缺命令与行类型**：① 审核驳回的“退回重算”（PRC-124 步骤 4 on_reject，〔缺〕退回薪酬重算）；② 补发补扣行（锁定后更正行、调薪追溯到已批准月份，PRC-123 的 decision；〔缺〕补发补扣行类型）；③ 离职结算项（未休年假折算、经济补偿）是否进月度薪酬未定；④ 代发中个别员工退票：法人 × 月一笔合计下，部分退票时应付“结清”语义不清（PRC-127），需部分失败的处理；⑤ P-41（有工时无薪酬）的触发在 B-07，不在本稿。
3. **AGG-70 / 71 / 72 缺命令**：驳回请假、驳回加班无命令；额度回冲：撤销审批中请假用“释放余额占用”，撤销已批准请假与销假需要〔缺〕回冲已用额度（AGG-71）；v1 把“占用余额”写在“提交请假”之前，两聚合分事务，占用成功提交失败产生孤儿占用，靠定时释放（§7 第 6 条）；建议改为先提交后占用，由请假已提交事件触发占用，占用失败则请假被拒。
4. **事件载荷不足（订阅方须过滤）**：① “绩效评估已确认”试用期评估（PRC-85）与月度评估（PRC-120）都发，PRC-122 须据**评估类型**过滤，事件须带类型；② “应付已结清”B-15（薪酬）与 B-12（报销）都订阅，须带应付类型与来源单据类型（PRC-127）；③ `BusinessFactAttributed` 须带来源单据类型，B-15 才能只处理薪酬（PRC-134）。三处在 yaml 的 events 项用 `filter` 标出。
5. **AGG-97 缺命令**：〔缺〕重新生成月度考勤（员工提出更正后，确认前重新汇总）、〔缺〕代员工确认（离职当月）、锁定时的“逾期未确认”标记。离职当月：离职生效即吊销登录（RULE-363），员工无法本人确认，但阶段进入条件要求离职当月仍须确认（STEP3 C-17）；本稿让人力代确认并留依据，是否改为“逾期默认确认”见 U6。
6. **“法人当月在职人员全部锁定”无承载**：月度考勤已锁定按人员 × 月发出，PRC-124 的进入条件是法人级汇聚；本稿写成人力发起 + 步骤 1 读取校验，也可在 B-27 提供只读查询“法人当月未锁定名单”。建议后者，不新增事件。
7. **AGG-69 / 考勤类型不全**：老系统有外勤申请、年假延期、节前提前下班考勤调整、特殊假别、销假（OA_PROCESS_INVENTORY 第 48–76 行，外勤约 3.5 千条）；聚合稿只有“导入考勤记录、补卡”。外勤 / 出差是否属考勤记录类型、是否影响月度考勤，见 U12；年假延期与特殊假别归 AGG-118 假期类型与额度规则。
8. **AGG-76 奖金**：缺“草拟”命令（v1 备注草拟 → 已核定）；缺“核定不超出激励方案额度”的不变量；老系统有组织级奖金计提与个人奖金补录（线下导入，OA 盘点第 8、23 行），组织级奖金池无聚合。
9. **AGG-106 评估模板缺**：老系统月度评估表有交付人员、BU 支持人员、总部人员三类模板，AGG-106 只有类型、期间、评估人、结论，没有评分维度与指标；“提交”命令的接收方不明（上级复核、被评人阅知 / 申诉均未写）。
10. **法人三口径与薪酬分组**：AGG-73 是“法人 × 月”，应付薪酬、个税扣缴也按法人；三口径（劳动合同用人单位、数据范围法人、个税扣缴义务人，Q-09）可能不同，AGG-73 按哪个口径分组（建议按 AGG-93 的扣缴义务人，即发薪法人）、社保公积金缴纳主体（AGG-74）与它不同时如何申报，未定（U7）。
11. **PRC-125 触发与 AGG-95**：现仅由“薪酬已批准”触发；股息红利并入（PRC-113）后当月若无薪酬批准则无申报触发，本稿加了 time 与 human 触发；AGG-95 缺“申报文件”与“回执附件”的承载（回执、完税凭证只作附件引用，D16）；“合伙人”分配的所得性质因被投资主体而异（合伙企业合伙人一般为经营所得〔中〕），PRC-113 只处理自然人股东的股息红利，其余交财务。
12. **订阅方列**：“薪酬档案已变更”现为仅分析，PRC-145 订阅它（同 BC，B-15 自己）；“法人归属已变更”（AGG-117）订阅方需补 B-15；“月度考勤已锁定”订阅方 B-15 已有。
13. **事件策略与单聚合的 yaml 写法**：PRC-127 / 134 在“已发放”与“已过账”任一后到者触发，需 PRC-127 在发放时检查过账事实（`decision` goto PRC-134），否则过账先于发放时置位丢失；聚合稿没有此检查命令。

### B-15 薪酬表

1. 「薪酬规则版本」聚合号：个税税率表、起征点与专项附加扣除标准、社保公积金比例与基数上下限、计算口径参数，配置型 D15，每版本一聚合（同 AGG-108 写法），5 张表。依据：薪酬自算后聚合稿 AGG-73 对「薪酬汇总导入」依赖作废，缺规则配置（人员组 NOTES 铺开备忘）。请 one-architecture 给号，并在 AGG-73、AGG-93、AGG-74、AGG-95 的引用列补「薪酬规则版本（api）」；聚合稿 B-15 聚合数 6 → 7。本稿 aggregate 写「AGG-待定-薪酬规则版本」。
2. 规则版本是否一类一表：本稿用一个 `payroll_rule_versions`（带规则类别）+ 四张子表，一版本只含一类，使个税法修订与各地社保年度调整可独立生效。备选：四类各自一个根（四个聚合）。推荐前者；请 one-architecture 与 one-data 确认。
3. 迁入历史结算无规则版本：全部历史迁入但无法复算，历史结算行、项目行只迁结果，所用版本引用留空并标「迁入结果」。D7 要求用到可配置规则时记版本 ID，此处是迁入行的例外，需 one-data 确认不违反守护；备选：给迁入行一条「迁入占位规则版本」。
4. 专项附加扣除拆表：声明（年度）+ 明细两表，放 AGG-93。明细含受益人（含未成年子女）信息，个人信息保护法第 28 条明列未成年人信息为敏感；是否抽到独立表、独立访问级别，交 U-11 与 one-data。
5. AGG-73 的聚合边界：薪酬行与项目行同聚合（单据、次级），一法人一月可达数百至千行，批准前整体修改；若行数使乐观锁冲突频繁，需拆「按人员的计算」子聚合。现按全貌级保持一个聚合，规模待 one-data 评估（NAMING_STANDARD §2.4a 第 8 条）。
6. AGG-73 的发放登记：`payroll_settlements` 内含发放日与银行回单引用；标记薪酬已发放为聚合命令。若银行代发需要批次与退票粒度（退票时付款失败、薪酬保持已批准，聚合稿阶段 6），可能要独立「代发批次」次级表，待待用户定 5。
7. AGG-95 的「更正」：用自引用 `corrects_filing_id` 另立申报，而非原申报加更正行；与聚合稿「申报、缴款、更正」三类一致，请确认。
8. AGG-75 的个人额度：方案版本额度到法人 / 组织单元，不到个人；个人额度即 AGG-76 的核定金额。若方案需提取比例累计（销售提成）跨期结转，需另立累计表，待方案梳理。
9. 引用 AGG-61 组织单元：额度表引用 B-13 AGG-61，B-15 → B-13 边存在，但聚合稿 AGG-75 引用列只写 platform.approval，需补。

### VS-09 / VS-11 与 B-18、B-30 表

1. [流程] B-30 评审按聚合内审批（`engine: aggregate`），不经 platform.approval，因为 B-30 没有到 platform.approval 的边。若日后评审要多级或会签而必须走 platform.approval，补边 B-30 → platform.approval 的影响（按聚合稿 §4.1 层位估算，未跑 `check` 脚本，请 one-architecture 验证）：platform.approval 在 L4，且只依赖 B-13、B-14 与平台（聚合稿第 860 行），所以不成环；但 B-30 由 L3 升到 L5，被它引用的 B-02（L5 → L6）、B-03（L6 → L7）、B-04（L7 → L8）、B-28（L8 → L9）都要升一层，逐级带动其下游，层数由 L0–L14 变为约 L0–L15。BC 数、聚合数不变，边数 +1。本稿建议暂不补边（见待用户定 2）。
2. [流程][数据] 「预算调整」两处口径不一致：`terms.yaml` 的预算调整（budget_adjusted）定义为「产生预算基线新版本的受控变更」，聚合稿 AGG-80 的命令「调整预算」是账户级命令（→ BudgetAdjusted）。若按词表，调整预算就是 AGG-79 的新版本，要走经营计划审批；若按聚合稿，账户额度可由财务直接调。本稿在 PRC-88 步 5 判断点写成「调整改变基线额度或目标值 → 回步 1 出新版本，否则账户级调整」，是折中；请 one-architecture 与 one-glossary 定。
3. [流程] 预测的审批：VS-09.2 的 notes 写「新版本经审批成为新基线后可再次进入本段」，但 PRC-89 的 human_orchestration 为 false，AGG-79 的「编制预测版本」命令只产生内部事件，没有基线化与审批。三者不一致。本稿按 PRC-89 现状（不审批，每次滚动即新版本）写，请定（待用户定 4）。
4. [数据] 经营动作与复盘的归属：OBJ-100 定义含「动作」，VS-09.3 退出写「经营动作写入计划」，但聚合稿 AGG-79 内含列只写目标值。`terms.yaml` 却有经营复盘（business_review，aggregate）、经营动作（action_item，entity）、承诺清单（commitment_list，aggregate）三个 operations.planning 条目，聚合稿没有对应聚合。本稿暂在 AGG-79 下建 `action_items` 表承载动作，复盘和承诺清单未建表。请定：动作并入 AGG-79 版本；或新增聚合「经营复盘」（含动作）；承诺清单是否对应 PRC-89 的每周销售预测承诺（目标类型「承诺」）。
5. [数据] AGG-107 内含列写了「外部知识平台文件夹引用」。D16 要求外部 ID 只作来源对照，CLAUDE.md 规则 1 要求领域模块不出现外部系统名。建议聚合稿删去该内含项，改为「迁入来源对照（platform.integration）」；本稿表里没有这个列。
6. [数据] 词表与 BC 名：`terms.yaml` 里 budget_baseline（预算基线，aggregate）、budget_reserved、budget_adjusted 的 bc 仍是已合并撤销的 `operations.budgeting`；预算基线在聚合稿里是 AGG-79 的类型版本而不是聚合，预算账户（AGG-80）在词表里没有条目。交 one-glossary 用 new_term_issue.py 登记，本稿不自行裁定。
7. [数据] AGG-80 在拦截方案下才是「额度占用」技术聚合；预警方案下它更像普通次级聚合，聚合稿「对应」列届时需改写（待用户定 1）。

### B-31 行政事务（PRC-139、142–144，AGG-113–116）

1. **四个流程不属现有 13 条价值流的阶段**：目录 stages 为空，yaml 写 `[]`。它们是支撑性行政事务（用印、IT 需求、IT 资产使用、员工活动），不产生权责事实（盘点说明）。建议：不硬挂价值流阶段，在 `processes.yaml` 以 `type: support`（或 `stages: []` 即视为支撑类）标注，并在价值流 v2 的「支撑」处列出；若 one-architecture 想挂，最接近的是用印 → 合同签订/报价相关阶段（只作旁路，不建议）。请裁定标注方式。
2. **PRC-143 目录名与范围不符**：目录名含报废、盘点、采购申请，但用户已定（intent 2026-10-16-152926）IT 资产是固定资产，这三项属 B-24 / 采购。骨架只画领用、调拨、归还、维修。建议目录改名为「IT 资产领用、调拨、归还与维修」，报废、盘点、采购申请另编号交财务组（采购申请：盘点第 42 行「IT资产采购申请」已在非项目采购 `procurement`）；`IT资产信息变更`（累计 137）按卡片信息变更归 B-24；`IT资产内购`（累计 3）涉及资产处置，归 B-24 / 采购，由财务组决定；`IT资产报废回收流程`（累计 1）归 B-24。
3. **AGG-115 边界**：回补提出「使用期间作根」的备选。本稿取「每卡片一个根 `it_asset_use_ledgers`」，使用期间、申请单、维修工单为次级（见 `b31.DATA_MODEL.md` §3）。理由：INV-新-P4 须在一个事务边界内判定，卡片是一致性键；规模很小（调拨累计 139、领用累计 1），争用可忽略。请 one-architecture 在聚合稿定稿时二选一。
4. **员工活动类型（参考数据）是否单独成聚合**：`employee_activity_types` 本稿放在 AGG-116 内作次级；先例 AGG-118（B-27 假期类型）是单独技术聚合。若业务要在线增改类型，须改为配置型版本化并单独成聚合。请定。
5. **信息图缺对象**：OBJ-新-P1–P4 须先在信息图补（回补已说明）；本稿表名按这四个对象。
6. **目录漏项：合同借阅申请**：盘点第 114 行「合同借阅申请」（营销管理，累计 281、在途 14，留在 OA）不在 PRC-139/142/143/144，也不在 B-31 的四个聚合内；既然 A8 行政流程已改迁入（C-3、C-5），它需要归属：可作 AGG-113 之外的第五个聚合（合同借阅，属 B-05 合同的档案管理？）或并入 B-31。本稿未画，请 one-architecture 与用户定归属并要流程号、聚合号。
7. **感恩节活动**：A8 感恩节活动（累计 2,106、已停用）归并到 AGG-116 的一个活动类型，见 PRC-144 migration。

- 主会话更正：上文所说「目录漏了合同借阅申请」不成立——借阅为 PRC-140、归档为 PRC-141，已归收入组（closeout/process-catalog.yaml），本组不处理。

## 新用语

### B-13、B-14、B-27 表（试点）

| 中文 | 建议英文 snake | bc | 说明 |
|---|---|---|---|
| 职位 | job | organization.workforce | 职位目录中的一类工作（不含级别）；与词表已有的「岗位 position」关系待定：Q-08 后建议岗位一词停用或限为旧叫法 |
| 职位族、职位子族 | job_family | organization.workforce | 族 → 子族层级 |
| 候选人 | candidate | organization.workforce | 人员的候选阶段状态；与 `person`、`employee` 的关系待定 |
| 面试 | interview | organization.workforce | 候选人面试记录 |
| 录用决定 | hiring_decision | organization.workforce | 聚合稿命令「作出录用决定」；事件「录用决定已作出」 |
| 招聘需求 | recruitment_requisition | organization.workforce | AGG-65 |
| 注册执业证书 | practice_certificate | organization.workforce | AGG-66 |
| 注册期 | registration_period | organization.workforce | 证书的一个注册周期 |
| 继续教育学时记录 | continuing_education_record | organization.workforce | 必修、选修学时 |
| 劳动合同 | labor_contract | organization.workforce | AGG-67（词表仅有文字提及，无条目） |
| 用人单位 | employing_legal_entity | organization.workforce | 劳动合同的用人单位口径的法人；三口径之一 |
| 数据范围法人 | scoping_legal_entity | organization.workforce | ADR-0026 D-1 口径的法人，现用「法人归属」；是否并用待定 |
| 绩效评估 | performance_review | organization.workforce | AGG-106 |
| 用工类型、用工状态 | employment_type、employment_status | organization.workforce | 列 `employment_status` 已建（EMPLOYED / SEPARATED），语义扩为含候选阶段与停薪留职；值域自有枚举 |
| 人事业务生效日 | personnel_effective_date | organization.workforce | U-12 的生效依据；可与词表「生效日」合并，请核 |
| 标准工时版本 | standard_work_hour_version | organization.attendance | AGG-108 |
| 考勤记录 | attendance_record | organization.leave | AGG-69；词表有 `attendance`（考勤与休假）条目，bc 为 organization.attendance，需同名异 bc 互列 |
| 补卡 | attendance_correction | organization.leave | 记录类型 |
| 请假 | leave_request | organization.leave | AGG-70 |
| 假期类型 | leave_type | organization.leave | 参考数据 |
| 假期余额账户 | leave_balance_account | organization.leave | AGG-71，技术聚合 |
| 额度占用 | leave_balance_hold | organization.leave | 额度占用模式的占用行 |
| 假期额度规则 | leave_entitlement_rule | organization.leave | 按城市类别的应享规则版本 |
| 加班 | overtime_request | organization.leave | AGG-72 |
| 月度考勤确认 | monthly_attendance_confirmation | organization.leave | AGG-97 |
| 更正行 | monthly_attendance_adjustment | organization.leave | 锁定后的变动，标原月份 |

### 入转调离（PRC-79–82、85、128–130、136、138）

使用范围 / bc 为提议，英文名由 one-glossary 定稿；未在 `terms.yaml` 逐条查重，建议登记前同 bc 内中英文名都查（尤其「离职」「入职」已有 platform.identity / B-13 两处）。

| 中文 | 建议英文 | 种类 | 使用范围 | bc | 说明 |
|---|---|---|---|---|---|
| 离职生效 | TakeSeparationEffect | 命令（系统定时） | 领域 | organization.workforce | 离职日结束时执行，发 Separated；与词表 platform.identity 的「离职」（separation）同名异义，交 one-glossary |
| 发起公司终止 | InitiateCompanyTermination | 命令 | 领域 | organization.workforce | 公司单方终止劳动关系的发起 |
| 驳回离职申请 | RejectSeparationRequest | 命令 | 领域 | organization.workforce | 含员工撤回 |
| 撤销离职登记、离职登记已撤销 | CancelSeparationRegistration、SeparationRegistrationCancelled | 命令、事件 | 领域 | organization.workforce | 离职日前撤销 |
| 登记劳动合同终止依据 | RecordContractTerminationBasis | 命令 | 领域 | organization.workforce | 离职协议或解除 / 终止通知（线下文件） |
| 登记候选人答复 | RecordCandidateReply | 命令 | 领域 | organization.workforce | 接受 / 放弃录用，发录用决定已作出 |
| 提交调岗申请、批准调岗申请、驳回调岗申请 | SubmitTransferRequest、ApproveTransferRequest、RejectTransferRequest | 命令 | 领域 | organization.workforce | 调岗（任职变更）的申请与批准 |
| 变更任职、任职变更生效 | ChangeAssignment、TakeAssignmentChangeEffect | 命令 | 领域 | organization.workforce | 前者原子关旧开新，后者到业务生效日发任职已变更 |
| 转正已办理 | ProbationConfirmed | 事件 | 领域 | organization.workforce | 供 B-15 复核转正后薪酬 |
| 指定法人归属、法人归属已变更 | AssignLegalEntityAttribution、LegalEntityAttributionChanged | 命令、事件 | 领域 | organization.workforce | AGG-117 已建，名称以代码为准 |
| 重算默认角色与数据范围 | RecalculateDefaultGrants | 命令（事件反应） | 共享内核 | platform.identity | 任职已变更触发 |
| 停用登录身份 | DisableLoginIdentity | 命令 | 共享内核 | platform.identity | 已见 PROCESS_SKELETONS 第 5 节 |
| 删除全部角色授予 | RevokeAllRoleGrants | 命令 | 共享内核 | platform.identity | ADR-0027 第 1 项第 6 点 |
| 停用应急账号 | DisableEmergencyAccount | 命令 | 共享内核 | platform.identity | 词表有「应急账号」，命令未登记 |
| 确认放行离职处置 | ReleaseHeldSeparationDisposal | 命令 | 共享内核 | platform.identity | ADR-0027 第 7 点人工放行 |
| 停用企业微信成员 | DisableWeComMember | 命令（出站） | ACL | platform.integration | 与开通成员对称 |
| 登记企业微信成员手工停用 | RecordManualWeComDisable | 命令 | 共享内核 | platform.identity | 信息部门后台手工处理的登记 |
| 变更参保缴纳主体 | ChangeInsuranceContributor | 命令 | 领域 | organization.payroll | 跨法人调动 |

### VS-13 获得报酬（PRC-113、115–127、134、145）

| 中文 | 建议英文 snake | 使用范围 | bc | 说明 |
|---|---|---|---|---|
| 驳回请假 | reject_leave_request | 领域 | organization.leave | AGG-70 命令 |
| 驳回加班 | reject_overtime_request | 领域 | organization.leave | AGG-72 命令 |
| 回冲已用额度 | refund_used_leave_balance | 领域 | organization.leave | AGG-71 命令 |
| 重新生成月度考勤 | regenerate_monthly_attendance | 领域 | organization.leave | AGG-97 命令，确认前 |
| 代员工确认 | confirm_on_behalf | 领域 | organization.leave | AGG-97 命令，离职人员 |
| 确认期限 | confirmation_deadline | 领域 | organization.leave | 月度考勤员工确认的期限 |
| 审批已通过、审批已驳回 | approval_approved、approval_rejected | 共享内核 | platform.approval | 平台事件，B-15、B-27 订阅 |
| 评估类型 | review_type | 领域 | organization.workforce | 绩效评估已确认的载荷（月度 / 试用期） |
| 草拟奖金核定 | draft_bonus_determination | 领域 | organization.payroll | AGG-76 命令 |
| 薪酬规则版本 | payroll_rule_version | 领域 | organization.payroll | 配置型聚合 |
| 发布薪酬规则版本、薪酬规则已生效 | publish_payroll_rule_version、payroll_rule_version_published | 领域 | organization.payroll | 命令、事件 |
| 退回薪酬重算 | return_payroll_for_recalculation | 领域 | organization.payroll | AGG-73 命令 |
| 补发补扣行 | payroll_adjustment_line | 领域 | organization.payroll | 锁定后更正与追溯调薪 |
| 代发明细 | payroll_disbursement_detail | 领域 | organization.payroll | 员工 × 账户 × 金额；归属待接缝问题 3 |
| 申报文件 | filing_file | 领域 | organization.payroll | 个税与社保公积金申报数据文件，不带外部系统名 |
| 申报回执 | filing_receipt | 领域 | organization.payroll | 附件引用 |
| 累计预扣 | cumulative_withholding | 领域 | organization.payroll | 个税预扣计算口径 |
| 申报端对接 | filing_gateway | ACL | platform.integration | 仅方式 b 启用 |
| 法人归属已变更 | legal_entity_attribution_changed | 领域 | organization.workforce | AGG-117 事件（已在聚合稿回补稿） |

“工资条”“月度考勤”等已见聚合稿；“薪酬法人”未登记，沿用“法人”，不另造。

### B-15 薪酬表

| 中文名 | 建议英文名 | 使用范围 | bc | 说明 |
|---|---|---|---|---|
| 月度薪酬结算 | payroll_settlement | 领域 | organization.payroll | 聚合稿 AGG-73；词表查无 |
| 薪酬行 | payroll_settlement_line | 领域 | organization.payroll | 聚合稿「人员薪酬行（工资条是单据）」；「工资条」是对员工的展示单据，不另登记聚合 |
| 薪酬项目行 | payroll_line_item | 领域 | organization.payroll | 计算明细 |
| 参保 | insurance_enrollment | 领域 | organization.payroll | AGG-74；表名 SAMPLE 已定 |
| 激励方案 / 激励方案版本 | incentive_plan / incentive_plan_version | 领域 | organization.payroll | AGG-75 |
| 奖金核定 | bonus_determination | 领域 | organization.payroll | AGG-76 |
| 人员薪酬档案 | payroll_profile | 领域 | organization.payroll | AGG-93；「薪档」已有词（standard cost rate 定价键，cost.costing），注意区分 |
| 专项附加扣除 | special_additional_deduction | 领域 | organization.payroll | 法定名；声明 `payroll_special_deduction_declaration` |
| 个税扣缴义务人 | withholding_legal_entity（列名）| 领域 | organization.payroll | 三口径之一，已见于 NOTES 与 SAMPLE |
| 个税与社保公积金申报 | tax_social_filing | 领域 | organization.payroll | AGG-95；表名暂用 filing，不叫 return，与 B-18 法定申报 AGG-90 区分，建议 one-glossary 统一「申报」一词 |
| 申报回执 | filing_receipt | 领域 | organization.payroll | 外部权威回执 |
| 缴款登记 | tax_social_payment | 领域 | organization.payroll | |
| 薪酬规则版本 | payroll_rule_version | 领域 | organization.payroll | 提议新聚合 |
| 个税税率表 / 级距 | income_tax_bracket | 领域 | organization.payroll | 法定参数 |
| 扣除标准 | tax_deduction_standard | 领域 | organization.payroll | |
| 缴费比例规则 | contribution_rate_rule | 领域 | organization.payroll | 社保公积金比例与基数上下限 |
| 薪酬计算参数 | payroll_calculation_parameter | 领域 | organization.payroll | 月计薪天数、加班倍数等 |
| 〔缺〕命令 | — | — | — | 聚合稿 AGG-75、76、93、95 命令列已有；规则版本的「编制 / 生效薪酬规则版本」命令与事件「薪酬规则版本已生效」〔缺〕，随聚合号补 |

「同一 bc 内中英文名都查重」：词表中无薪酬、社保、个税相关词（只有「薪档」「标准费率」），B-15 内未发现重名。

### VS-09 / VS-11 与 B-18、B-30 表

均为领域用语，bc 为 operations.planning 或 km.knowledge；中英文名需在同 bc 内查重，跨 bc 同名互列。

| 中文 | 建议英文 snake | 范围 | bc | 说明 |
|---|---|---|---|---|
| 〔缺〕提交经营计划审批 | submit_business_plan_for_approval | 领域 | operations.planning | PRC-88 步 2；命令 |
| 〔缺〕通过经营计划审批 | approve_business_plan | 领域 | operations.planning | 审批平台回调命令，写法同「通过报价评审」 |
| 〔缺〕驳回经营计划审批 | reject_business_plan | 领域 | operations.planning | 同上 |
| 〔缺〕退回知识条目 | return_knowledge_entry | 领域 | km.knowledge | PRC-107 步 4 评审退回起草，v1 注明无独立命令 |
| 预算账户 | budget_account | 领域 | operations.planning | AGG-80；词表无条目 |
| 预算占用行 | budget_reservation | 领域 | operations.planning | 表 `budget_reservations`；与词表事件 budget_reserved（预算占用）关系待 one-glossary 核 |
| 预算调整记录 | budget_account_adjustment | 领域 | operations.planning | 表 `budget_account_adjustments`；与词表 budget_adjusted（预算调整，事件）同源 |
| 经营计划版本 | business_plan_version | 领域 | operations.planning | 词表有经营计划、经营计划基线，无版本；基线是已审批生效的版本 |
| 知识条目 | knowledge_entry | 领域 | km.knowledge | AGG-107 |
| 知识条目版本 | knowledge_entry_version | 领域 | km.knowledge | |
| 知识条目评审记录 | knowledge_entry_review | 领域 | km.knowledge | |
| 适用范围 | applicability_scope | 领域 | km.knowledge | 业务线、项目类型等取值；取值集合待补 |
| 有效期 | validity_period | 领域 | km.knowledge | 词表若已有同名条目需互列 |
| 知识类别 | knowledge_category | 领域 | km.knowledge | 标准做法 / 技术案例 / 规范解读（信息图 K-1 举例，取值待补） |

`action_items` 表名沿用词表的经营动作（action_item），不是新词。

### B-31 行政事务（PRC-139、142–144，AGG-113–116）

聚合稿 §2.2 与回补已有的命令和事件名不重列。下列为本稿新增的〔缺〕命令与需登记的名词，使用范围均为「领域」，bc 为 B-31；词表（terms.yaml）中尚无这些词（已按 `用印`、`IT 需求`、`资产使用`、`员工活动`、`领用` 检索）。同一 bc 内中英文名需查重，登记交 one-glossary。

| 中文名 | 建议英文名 | 范围 | bc | 说明 |
|---|---|---|---|---|
| 用印申请 | SealApplication | 领域 | B-31 | AGG-113 |
| 印章类别 | SealType | 领域 | B-31 | 受控代码 |
| 用印登记（行） | SealUsageRecord | 领域 | B-31 | |
| IT 需求工单 | ItRequest | 领域 | B-31 | AGG-114；「需求工单」是 A8 称呼 |
| IT 资产使用记录 | AssetUseLedger | 领域 | B-31 | AGG-115 根，一卡片一根 |
| 使用期间 | AssetUsePeriod | 领域 | B-31 | |
| 维修工单 | AssetRepairOrder | 领域 | B-31 | 报修、处理、结果 |
| 员工活动申请 | EmployeeActivityRequest | 领域 | B-31 | AGG-116 |
| 活动类型 | EmployeeActivityType | 领域 | B-31 | 参考数据 |
| 〔缺〕驳回领用 | RejectAssetIssue | 领域 | B-31 | 回补只有「批准领用」，审批驳回须落地命令 |
| 〔缺〕申请调拨资产 | RequestAssetTransfer | 领域 | B-31 | 回补只有「调拨资产」；调拨须审批〔推〕 |
| 〔缺〕批准调拨 | ApproveAssetTransfer | 领域 | B-31 | |
| 〔缺〕驳回调拨 | RejectAssetTransfer | 领域 | B-31 | |
| 〔缺〕生成归还待办 | GenerateReturnTasks | 领域 | B-31 | 订阅 Separated 的定时 / 事件命令 |
| 〔缺〕撤回用印申请 | WithdrawSealApplication | 领域 | B-31 | 审批中撤回 |

另：「用印法人」与「法人主体」（AGG-60）的关系，建议术语里用印法人 = 引用法人主体，不另造名；「资产卡片」沿用 B-24 词（AGG-91 公司资产）。

## 格式问题

### B-13、B-14、B-27 表（试点）

1. 个人信息标记（NONE / PI / SPI，ADR-0043）无字段：现写在 `reason` 里。建议 `sensitivity` 之外加 `pi_flag`（NONE / PI / SPI）。
2. `sensitivity` 取值：CONVENTIONS 示例为小写 `internal`，`data-classification.txt` 为大写 `INTERNAL`；本稿用大写，请统一。
3. `references.to` 只能写 AGG-NN；引用 platform.*（审批流程实例、来源对照、身份）没有聚合编号，本稿放在说明稿。建议允许 `{to: platform.approval, by: ..., cross_bc: true}` 形式。
4. 无「聚合编号待定」的写法：本稿用 `AGG-待定-<名称>`，请定占位规则。
5. 无数据类别（交易 / 事实 / 配置 …）、迁入来源与通道（`via`）、状态（已建 / 改形 / 新建）字段，已放说明稿；若要机器核对（D17 守护 G-D11、D14 血缘），建议加 `data_category` 与 `migration_via`。
6. `versioned` 的 `none` 与 D17 豁免类别（四类）无结构化字段，现写 `reason`；`effective_dated` 又分 DECLARED / OBSERVED、配置型 D15 与维度型 D6，建议加 `version_basis`。
7. `role: version` 与 `child` 对「根下的次级版本」界线：本稿 `org_unit_legal_entities` 引用的是聚合根（归属根），归 `version`；请确认。

### 入转调离（PRC-79–82、85、128–130、136、138）

1. **decision 项带 outcome**：`goto: end` 时需要说明是哪个结局词，以便生成器为不同结束事件命名；本稿在 decision 项增 `outcome`（取值在流程 `outcomes` 内）。步骤级 `outcome` 仍表示本步结束流程。
2. **并列入口用 `branch`**：PRC-80 的步 1–4 是互不依赖的并列入口（v1 的 `branch` 字段）；其他骨架若有同类，请生成器按「每个 branch 一条独立路径」画，而不是顺序连接。
3. **同一角色内的职务分离**：公司单方终止、调岗申请的提交与批准都由人力（SH-E6），`segregation.roles` 只能写 `[SH-E6]` 并用 rule 文字说明；SH-E6 不分「人力 / 信息 / 财务」（PG-15）使这类约束无法按角色表达。建议 stakeholders 拆出 SH-E6 的成员子类（人力、信息、行政、财务），或允许 `segregation` 写「同一角色内两个动作」。
4. **员工泛称缺编号**：申请离职的员工暂取 SH-E3（设计师和顾问），实际是任一类员工；建议 stakeholders 增「员工（泛称）」。
5. **等待型分支难以在 decision 里表达**：「离职日前撤销」发生在登记之后、生效之前的任意时刻，本稿写成步 6 的 decision 指向备选路径（步 10），语义是「事件中断」；建议约定 `interrupt: {event/时点, goto}` 或事件子流程。
6. **事件策略的多效果**：PRC-129 单步只登记吊销会话，同源的删除授予、停用应急账号、停用登录身份写在 PRC-138；若 one-architecture 认为它们同属强制事件反应（§1.4 G-2），应并入 PRC-129 并允许 `steps` 列表。
7. **区域负责人**归 SH-M1（区域和事业部负责人）；SH-M1 在 stakeholders 的「职能高管」同一条内，无法区分用于升级的对象，本稿 `escalate_to` 暂取 SH-M1。
8. **`type` / `new` / `basis` / `on_time` / `on_event` / `entry_of`** 等扩展键沿用试点稿与 b31 稿的写法，请生成器忽略不识别的键，或在 CONVENTIONS 登记。

### VS-13 获得报酬（PRC-113、115–127、134、145）

1. `decision` 项加可选 `outcome`：条件分支直接结束流程时需要结局词（如额度不足被拒），否则只能把 `outcome` 挂在步骤上，与“该步不一定结束流程”冲突。
2. **员工确认算不算审批**：PRC-118 员工确认是员工本人对自己月度考勤的聚合内确认，本稿按 `approval{engine: aggregate, approver: SH-E3}` 写以满足“人工编排必有 approval”；它不是上级审批。请裁定：算 approval，或另加 `confirm` 字段而人工编排判定放宽。
3. `events` 项加可选 `filter`（订阅过滤条件，如评估类型为月度）。
4. 步骤加可选 `entry`：分支步骤（撤销、销假、补卡、离职代确认）的进入条件，现只能写在 `branch` / `desc`。
5. `segregation.roles` 同一 SH-E6（人力 / 财务）内部分岗无法区分，薪酬“制单与审核分离”只能写 `[SH-E6, SH-E6]` 加规则文字；建议 SH-E6 加子编号或 roles 项加 `note`（同试点格式问题 1）。
6. 同一步骤涉及多个外部渠道（个税申报端、社保渠道、公积金中心）而 `where` 只能写一个 `external:`：本稿拆成多步；并行（个税与社保同时办）也只能线性画，请定并行写法。
7. `timeout` 缺起算点（如“次月 1 日起算 15 日”），本稿写 `P15D` 加注释；期限未定的写“暂拟”。
8. `multi_level` 未定时无取值，本稿暂写 false 加注释；建议允许 `tbd`。
9. `read: true` 步骤的 `actor`：读取由系统在用户动作中完成，本稿按用户动作者写；建议读取步骤并入下一个命令步骤，不单列。
10. 事件策略 `step`（单步）与流程 `steps` 并存：PRC-127 的两个出口写在 `decision`，请确认事件策略允许 `decision` 与 `goto: PRC-xxx`。
11. 审批回调步骤的 actor 写 `platform`、`where: automatic`（请假批准、薪酬批准）；与试点格式问题 6 同，请确认。

### B-15 薪酬表

1. `versioned` 取值无「配置型（D15）」：规则 / 方案版本用 `effective_dated`，在 reason 注「D15 配置型」，与人员组 AGG-108 同。建议 CONVENTIONS 增 `config_versioned`（或把 D15 并入 effective_dated 并加 `basis` 字段）。
2. 引用未编号聚合：`references.to` 写「AGG-待定-薪酬规则版本」，字符串非 AGG-NN；发号后替换。
3. 次级表随版本生效区间由所属根承担，`versioned` 只能填 none，容易与「根不存可版本化列」的 none 混淆；建议加 `follows_parent`。
4. 同 BC 跨聚合引用不写 `cross_bc`（CONVENTIONS 规则），但同聚合自引用（`reverses_settlement_id`、`corrects_filing_id`）写法未规定，本稿按同聚合外键写。
5. `pi` 字段缺「受限于 U-11」的承载位置：本稿对工资类表按 ADR-0043 第 2 项登记 spi，U-11 ③ 确认为 pi 后统一降级；请 CONVENTIONS 增 `pi_pending`（或沿用 tbd + reason）。

### VS-09 / VS-11 与 B-18、B-30 表

1. [流程] 一个流程有多个入口时，触发与步骤的对应无字段。PRC-89 两个时间触发分别进步 1 与步 2；PRC-107 的触发进步 1、步 2、步 6 与步 7。本稿用步骤上的 `on_time` 加 `triggers` 的名称表示。建议 `triggers` 项加可选 `enters: <步骤序号>`。
2. [数据] 业务单据的版本（经营计划版本、知识条目版本）沿用 D15 形状，但 `versioned` 只有 `append_only`，不能表示「基线 / 发布前行可变、之后不可改」。本稿写 `append_only` 并在 reason 说明。建议加取值，或借用人员组格式问题 6 提议的 `version_basis`。
3. [流程] 发布后等待定时事件（PRC-107 步 5 的修订、废止分支）没有「等待」写法，本稿把定时分支写进 decision，并在步骤上注 `on_time`。建议 decision 项加 `wait: <事件或期限>`。
4. [流程] `scenarios`（验收场景编号）由谁分配未说明，本稿未填。
5. [数据] `sensitivity` 取值：CONVENTIONS 为小写，`data-classification.txt` 为大写，本稿用小写。人员组 NOTES 已提同一问题。
6. [流程] 同一角色在 roles 里重复（SH-M1 既 initiator 又 approver，SH-E4 同）；人员组格式问题 5 已提，请确认允许。

### B-31 行政事务（PRC-139、142–144，AGG-113–116）

1. **无「全体员工」角色编号**：任一员工发起申请时只能借用 SH-E3（设计师和顾问），不准确。建议在 `value-streams.v2.yaml` 加通用角色（如 `SH-E0 全体员工`）或在 CONVENTIONS 允许 `employee`。
2. **多入口流程**：PRC-143 有 5 个 trigger，各入口步不是第 1 步。本稿加了步级扩展键 `entry_of: <trigger 名>`（缺省为第 1 步），其他键未动。请 CONVENTIONS 采纳或另给写法。
3. **SH-E6 多职能**：行政、信息、财务都是 SH-E6，无法区分。沿用试点做法，行尾注释区分；建议拆编号。
4. **triggers 的 event 写法**：CONVENTIONS 只说「写 signal 事件名与发布 BC」，本稿写 `{type: event, name, event, bc}`。
5. **v1 `human_orchestration: ~`**：四个流程均有审批，本稿填 `true`（审批走 platform.approval）。
6. **结局词无阶段出口用语**：stages 为空，outcomes 无法取自阶段出口，本稿自拟（已关闭、已驳回、已领用、已调拨、已归还、已维修登记）。
7. **`timeout.escalate_to: system`**：自动关闭的"升级给谁"填 system，CONVENTIONS 只写 SH 编号；`interrupting: true` 表示到期中断等待并关闭。
8. **回调式驳回步**：审批驳回由 platform.approval 回调聚合命令，本稿画成 `actor: platform`、`where: automatic` 的独立步（驳回用印申请、驳回领用、驳回调拨、驳回员工活动申请）。

## 待用户定

### B-13、B-14、B-27 表（试点）

1. **人员档案范围**：首版人事记录是否装证件号码、出生日期、私人联系方式、学历、紧急联系人等（落入 SPI 或 L3，须单独同意与影响评估，个人信息保护法第二十九、五十五条，ADR-0043）。本稿未建表。
2. **历史月度考勤确认的迁入**：老系统是月 × 一级部门粒度（1,026 单），与新对象人员 × 月不对应；「全部历史迁入」下，是按人员 × 月回算成已锁定历史确认，还是只迁考勤 / 请假 / 加班原始行、历史确认不迁，只留核对。本稿暂记「不迁」。
3. **劳动合同、离职申请、加班、注册执业证书的老系统来源**：是否存在、在哪里，尚未取证；取证不需要用户，但若老系统没有这些记录，「全部历史迁入」对它们是空集，请知悉。
4. **病假等请假事由的个人信息级别**：病假、产假可推断医疗健康、生育信息，按 ADR-0043「拿不准按 SPI」暂记 tbd，交 U-11 法务裁定；若判 SPI，请假表需单独同意与影响评估。

### 入转调离（PRC-79–82、85、128–130、136、138）

每条列选项、依据、推荐；与 PRC-128 的 V-1–V-4、V-15–V-16 同属离职，建议合成一页确认。

**V-1 离职日的语义与吊销时点**（PRC-128、129、138）
- 选项 a：离职日 = 最后在职日，吊销在离职日结束（次日 0 点）；b：离职日 0 点吊销。
- 依据：用户已定离职生效日为人事业务生效日，离职生效即吊销（RULE-363）；最后一天仍需交接、完成考勤确认；老系统 15% 离职者离职后仍登录（IDENTITY_LIFECYCLE）说明延迟的风险在登记不及时而非时点。
- 推荐 a。离职日不晚于登记日（补登、即时终止）则登记时立即生效。

**V-2 高风险离职是否提前停用访问（不改离职日）**
- 选项 a：不提供；b：复用已有的强制下线 / 停用登录身份能力，由信息安全或人力负责人在离职日前手动执行，写审计，不新增流程。
- 依据：ADR-0027 已有强制下线接口；离职日前不应改变人事状态。
- 推荐 b（只登记为 platform.identity 既有能力，不改本稿流程）。

**V-3 离职当月考勤确认由谁代确认**（PRC-118 / B-27 相关，离职员工离职后无法登录确认）
- 选项 a：离职申请批准后提示员工在离职日前确认到离职日为止的当月考勤，离职生效时仍未确认的，由人力代确认并留痕「代确认」，员工异议线下处理；b：用人经理代确认；c：离职生效时系统自动确认并锁定；d：一律人力代确认。
- 依据：AGG-97「员工确认」是确认节点（INV-105）；薪酬需锁定后的考勤（B-15 订阅月度考勤已锁定）；用人经理与考勤结果有利益关联，不宜代员工确认。
- 推荐 a。

**V-4 公司单方终止的发起与批准**
- 选项 a：人力发起、人力负责人单级批准，发起与批准分人（本稿）；b：加职能高管批准（多级，B-13 内批准无法多级，须走 platform.approval 会成环，只能线下批准留证据）。
- 依据：F-3 聚合内单级；终止劳动关系的法律风险较高。
- 推荐 a，批准证据附线下法务意见文件。

**V-5 调岗是否需批准、谁批、谁发起**（PRC-136）
- 选项 a：不批准，人力按已定调动登记；b：单级，人力负责人批准（本稿，聚合内）；c：新旧部门负责人会签 + 人力负责人（多级，须线下会签留证据，或另议审批工具）。
- 依据：调岗改变汇报线、成本归属、授权范围（触发待调整权限）；A8 有「人事异动表」（OA_PROCESS_INVENTORY 第 65 行，仅作证据，审批链未取证）；B-13 聚合内审批只能单级。
- 推荐 b；发起人维持人力登记，用人经理的提议在线下，不进系统。若选 a，PRC-136 的 human_orchestration 改 false，步 2、3 删。

**V-6 调岗是否要求员工书面签认**
- 选项 a：不要求；b：改变合同约定的岗位或薪酬时要求员工书面签认（劳动合同法第 35 条协商变更），登记为线下证据；c：一律要求。
- 依据：岗位调整若实质改变合同约定，需协商一致；证据留存可降低争议。
- 推荐 b，采纳则在步 2 与步 7 之间插入 offline 步（证据：员工签认的调动通知、签认日）。

**V-7 跨法人调动的合同处理**
- 选项 a：终止原合同、与新法人另订合同，工龄连续计算（本稿）；b：三方协议变更用人单位、不终止合同（需 AGG-67 增变更主体命令）。
- 依据：劳动合同法实施条例第 10 条（非因本人原因被安排到新用人单位工作的，工作年限合并计算）；三口径同时变化（用人单位、数据范围法人、扣缴义务人）由 PRC-145 预警。
- 推荐 a；需法务确认。

**V-8 录用是否需上级批准**（PRC-82）：聚合稿无命令。选项 a：用人经理决定，人力在登记答复时把关；b：人力负责人批准（聚合内单级）。依据：录用含薪酬谈判，薪酬在 B-15 不在本流程。推荐 a。

**V-9 招聘需求批准人**（PRC-81）：选项 a：人力负责人单级（本稿）；b：用人经理的上级；c：多级（须移出 B-13，聚合稿 §7 第 12 条）。推荐 a；提出与批准分人（已写 segregation）。

**V-10 不予转正的处理**（PRC-85）：选项 a：转入 PRC-128，作为公司单方终止，由其批准（本稿）；b：转正流程内独立批准。依据：试用期内解除须证明不符合录用条件（劳动合同法第 39 条），决定应在试用期届满前作出，系统应在届满前提醒。推荐 a。

**V-11 转正是否需批准**：聚合稿「办理转正」无批准。选项 a：不批，人力办理，评估由用人经理确认；b：人力负责人批准。推荐 a。

**V-12 试用期评估的提醒提前量与期限**：本稿暂写届满前 N 日生成待办、7 日内确认。推荐 N = 30。

**V-13 企业微信停用的告警时限**（PRC-138）：选项 a：1 个工作日（同入职 T-2）；b：当日。推荐 a。

**V-14 继续教育学时未达标时延续注册的处理**（PRC-79）：选项 a：预警不拒绝（本稿，因延续已在系统外发生）；b：拒绝登记。推荐 a。

**V-15 B-31 归还待办的触发时点**：选项 a：订阅 Separated（b31 稿现状，离职日结束才生成）；b：B-13 对外发「离职已登记」「离职登记已撤销」，B-31 提前生成，撤销时取消。依据：资产在最后一天归还才能与交接衔接；b 在 B-13 多发两个事件，方向仍是 B-31 订阅 B-13。推荐 b，同时 b31 稿的触发改订阅「离职已登记」。

**V-16 再入职沿用人员 ID 与工号**：选项 a：沿用同一人员 ID，工号新发；b：沿用工号。依据：userid 取工号且解绑后保留历史（PRC-138 步 7）；历史迁入需区分两段任职。推荐 a。交 one-data 一并考虑。

**V-17 ADR-0027 第 7 点是否保留**：超管 / 安全审计员持有人离职转人工放行（防止全员锁死）。选项 a：保留（PRC-138 步 3–4）；b：取消，由运维命令兜底。推荐 a，会话吊销与登录身份停用不受限。

### VS-13 获得报酬（PRC-113、115–127、134、145）

**U1 个税扣缴申报方式**（intent 2026-10-16-152603 只定“个税公司代扣代缴”）。依据：个人所得税法第九条（扣缴义务人依法预扣代扣税款）、第十条（全员全额扣缴申报，次月十五日内〔中，核对原文〕）；《个人所得税扣缴申报管理办法（试行）》（国家税务总局）。

| 选项 | 做法 | 代价 | 风险 | 把握度 |
|---|---|---|---|---|
| a | 系统按扣缴客户端的导入格式生成申报数据，人工导入并申报，回填回执（offline + 证据：申报回执、缴款凭证） | 每月一次人工操作；格式变化须跟进 | 低：不依赖接口资质；申报责任在公司 | 高（法律依据）；导入格式〔中〕，开发前取样确认 |
| b | 经税务接口直连申报 | 须有面向企业或第三方软件的公开接口及资质 | 未能核实公开的、面向企业自建系统的申报接口及其资质条件，不编造接口名 | 〔低〕：须由财务向主管税务机关或扣缴端服务方确认 |
| c | 委托代理（代理记账、人力外包）申报 | 外包费用；明细个人信息外送，须个人信息保护法第二十一条委托处理协议与 ADR-0043 评估 | 中：扣缴义务仍由公司承担 | 中 |

推荐 a：不依赖未核实的接口，合规责任清晰，与骨架一致；b 在核实后再评估。

**U2 社保公积金申报方式**：同理 a）系统生成申报明细，人工在经办渠道办理并回填回执；b）直连（社保费由税务部门征收，公积金归各地管理中心，渠道因地而异〔中〕，接口可得性〔低〕）；c）委托代理。依据：社会保险法第五十八条（入职 30 日内申报，P-32）；住房公积金管理条例。推荐 a。

**U3 请假**：审批人（直属上级 / 部门负责人 / 更高）、是否按天数分级（如超过若干天加一级）、销假与已批准请假的撤销是否需再审批、审批超期升级。依据：老系统有销假申请表（只作证据）；通行做法为按天数分级。推荐：直属上级单级，超过阈值加一级，销假知会不再审批，阈值请定。

**U4 补卡**：是否需上级确认、每月次数上限、时限（超期不可补）。推荐：需直属上级确认（成为人工编排流程），次数与时限请定；骨架 v1 无审批，先不加。

**U5 加班**：事前申请还是允许事后补；补偿方式（加班费 / 调休）由谁定；超 36 小时预警知会谁（劳动法第四十一条每月不超过 36 小时〔高〕）。推荐：事前申请，事后补须说明；预警知会用人经理与人力。

**U6 月度考勤**：员工确认期限与起算点（骨架暂拟 3 天）；逾期未确认默认按生成结果锁定（骨架）还是升级人力追办；用人经理是否复核；离职当月代确认依据与是否允许“逾期默认确认”替代。推荐：次月第 3 个工作日前确认，逾期锁定并标记，用人经理不复核，离职当月人力代确认并留依据。

**U7 薪酬**：① 月计薪天数 21.75 天与入离职、事假折算口径；② 财务主管审核是否加分管领导二级；③ 薪酬规则版本由谁维护确认（人力出、财务确认）；④ AGG-73 按三口径中哪个分组（建议发薪法人 = 个税扣缴义务人）；⑤ 全年一次性奖金计税方式〔中〕与经济补偿等离职结算项是否进月度薪酬；⑥ 薪酬发放日（工资支付暂行规定第七条，至少每月支付一次〔高〕）。
**U8 代发方式**：银企直连自动代发（external:银行）还是网银批量上传（offline）；员工银行账户存放位置（接缝问题 3）。推荐网银批量上传先行，银企直连随付款通道一并评估。
**U9 薪酬档案**：调薪是否需审批（老系统有调薪申请单，只作证据；通行做法为审批，若需审批 PRC-123 改为人工编排业务流程）；专项附加扣除来源：员工自助填报 / 人力代录入 / 从扣缴端下载员工已报信息〔中〕。推荐：调薪走 platform.approval；专项附加扣除以员工自助为主。
**U10 激励与奖金**：方案审批是否多级；奖金核定人（管理层单级）与是否需人力复核；组织级奖金池是否建模。
**U11 绩效评估**：评估模板与指标（三类模板是否保留）、提交的接收方、被评人是否可见 / 申诉。
**U12 外勤 / 出差考勤**：是否作为考勤记录类型并参与月度考勤；外勤申请是否需审批。推荐纳入，类型随 AGG-69 扩展。

### B-15 薪酬表

1. 个税与社保公积金申报方式（最小形态已按「系统生成申报数据、回执与缴款登记回写」建表）：
   a. 系统生成申报数据，人工到个税扣缴端 / 社保公积金端申报，回执与缴款回写（现表）；
   b. 经 platform.integration 对接扣缴端接口，系统直接申报；
   c. 委托第三方代理申报，系统收数据与回执。
   推荐 a 起步（CS-1.8 只实现当前阶段），表结构 a/b 通用，b 只多一个外部回执来源；依据 intent 2026-10-16-152603、聚合稿 §4.3.2 阶段 5 的 `platform.integration`（申报端）边。
2. 迁入历史结算是否回补历史规则版本：选项 (a) 只迁结果、不回补（推荐，依据：历史月结已发放，复算无业务用途，且老系统规则未取证）；(b) 回补 2019 年以来个税与各地社保规则版本，使历史可复算。
3. 老系统薪酬数据的取证（交 one-data，ADR-0037 取证登记一行）：是否有月度薪酬、参保、薪酬档案、奖金、个税申报的结构化数据，粒度（人员 × 月 / 法人 × 月 / 汇总）、起止年份、与总账薪酬科目的对账口径。本稿全部写「待取证」，取证前不能确定 `payroll_settlement_lines` 是否能全量迁入。
4. 专项附加扣除的受益人信息（含未成年子女）：选项 (a) 作为 AGG-93 同表列、SPI（现状）；(b) 只存扣除类别与月额，不存受益人姓名与证件，受益人信息留在个税端（推荐，最小化，个人信息保护法第 6 条）；(c) 独立表独立访问级别。依据：个人信息保护法第 28 条、ADR-0043 U-11。
5. 银行代发：选项 (a) 系统导出代发文件，财务到网银上传，回单登记发放；(b) 经 platform.integration 接入银行；(c) 员工银行账户不入库，由财务网银维护。账户已按 SAMPLE 放在 `payroll_profile_versions`（用户已定样板）；此问只问代发动作。推荐 (a)。
6. 薪酬计算口径参数（月计薪天数、加班费倍数、请假扣款口径、试用期、病假工资比例等）：需人力与财务给出现行制度；缺此，规则版本表只有骨架。
7. 规则版本变更是否四眼确认（同 D17 细则 8 的工作日历）：推荐是。
8. 个税申报用证件号码来自何处：与 B-13 NOTES 待用户定 1（人员档案范围）联动。选项：(a) B-13 `employees` 或独立档案表收证件号，B-15 申报时读取（api）；(b) B-15 自存（本稿现状：申报行快照）。推荐 (a)，申报行只留一份快照用于更正追溯。
9. 保留期（D12、ADR-0043 R-2）：薪酬、参保、申报明细按劳动法与税法的保存年限登记，不得 PERMANENT；交 one-data 与法务（U-11），本稿不定年限。

### VS-09 / VS-11 与 B-18、B-30 表

1. **预算账户超可用额度时拦截还是预警**（INV-102，〔支研〕问题 11，信息图 OBJ-101 一直未上确认页；影响 B-11 供方协议、B-12 费用报销的命令前置、AGG-80 是否为额度占用技术聚合、B-18 的深浅）：
   - A 拦截：占用超可用额度就拒绝，采购或报销单据提交失败；需孤儿占用释放与对账兜底。
   - B 预警：占用照记，超额放行并预警留痕给财务与管理层；不阻断业务。
   - C 超额须特批：超额占用不直接拒绝，转 platform.approval 特批，批准后放行（B-18 → platform.approval 边已有）。
   - 推荐 B。依据：一次投产，首期没有预算执行的历史习惯可对照；拦截会在投产首期阻断采购与报销，并带来占用补偿与对账的实现成本（CS-1.8 只实现当前阶段）；命令返回「是否超额」标志，之后升级为 A 或 C 不破契约（CS-1.10）。财政部《管理会计应用指引第 200 号》与内控基本规范对预算控制有刚性与柔性之分，信息图所引条文我没有逐条核对，不作为推荐的依据。老系统预算超额时的现行行为未取证，取证不需要用户，可在你定之前补。
2. **知识条目的评审人与层级**：A 技术委员会（SH-E4）单级，聚合内审批，不补边（推荐，贴合聚合稿 K-1「审批人」一项）；B 管理层（SH-M1）单级；C 多级或会签，须补边 B-30 → platform.approval，影响见结构问题 1。
3. **经营计划审批的层级与审批人**：谁审批（管理层 SH-M1 的哪一级，例如执委会）、是否多级、编制人与审批人是否必须分离（不相容职务，可加 `segregation`）；预算账户级调整（预算调整）是否要审批、金额阈值多少。推荐：计划与预算基线单级由执委会批准，编制人不得审批自己的计划；账户级调整由财务提交，超阈值走审批，阈值由你定。
4. **滚动预测**：新版本是否要审批成基线（结构问题 3；推荐不审批，预测是预期不是承诺）；逾期未报的升级对象与期限（本稿暂写 7 天升级给管理层，未经确认）；项目经理（SH-E2）是否参与预测（VS-09.2 参与者含 SH-E2，但骨架无其动作）。
5. **知识条目历史迁入**：库内没有知识条目实体，「全部历史迁入」对条目是空集；A8 文档中心有 567 份文档（近 24 个月新增 39，使用已衰减，内容未读），项目档案有 203 条知识平台文件夹引用。选项：a 不转条目，只把 203 条文件夹引用迁为来源对照、挂项目，条目从零建（推荐，文档没有经过评审，不是知识条目的历史）；b 由技术专家筛选部分文档，以「起草」状态导入后走评审；c 567 份全部以草稿导入（噪音大）。
6. **案例类知识条目的客户信息**：技术案例可能带客户、项目、人员信息。选项：a 发布前评审项含脱敏确认（推荐）；b 按项目类型或业务线限制可见范围；c 不限制。结果决定 `knowledge_entry_versions` 的 sensitivity 与 pi 取值（现为 tbd）。

### B-31 行政事务（PRC-139、142–144，AGG-113–116）

1. **用印审批是否按印章类别分级**。依据：通行做法公章、法人章、财务章常需更高一级审批；A8 无节点证据。A：单级行政负责人（简单，现稿）。B：合同章单级、公章 / 法人章 / 财务章加分管领导（两级）。推荐 B，由用户按公司制度定。
2. **A8 的 IT 资产历史怎么对应到 B-24 卡片**。依据：全部历史迁入（U-1–U-4）；规则 6 不静默跳过。A：A8 表单的资产编号映射到 U8C / B-24 卡片，对应不上的列清单交用户人工补（推荐；需先取证 A8 表单有无资产编号字段）。B：对应不上的以「未关联卡片」迁入只读历史（需聚合允许卡片 ID 为空，破坏 INV-新-P4 判定对象）。C：只迁对应得上的（与全部迁入矛盾）。
3. **IT 需求时限**：受理 2 个工作日、解决后 5 日不确认自动关闭，均〔推〕。选项：沿用、改数字、不设。推荐沿用，上线后看数据调整。
4. **审批人**：领用 / 调拨由资产管理员负责人批准（现稿）还是提出人部门负责人批准；员工活动由部门负责人（现稿）还是行政 / 人力批准；IT 需求仅哪些类别需审批。推荐：IT 资产由信息负责人批准，活动按费用预估分级（金额阈值待财务），需审批的 IT 类别由信息部门提清单。
5. **感恩节活动在途 75 单（已停用，最近使用 2024-11-11）**：选项 A 按 U-4 清空（审结或撤回）后只迁历史（推荐）；B 全部以草稿迁入。
6. **周年贺卡**：A8 里是员工申请（累计 219）。是否改为系统按入职周年自动生成并通知行政？选项 A 保留申请（现稿）；B 自动生成（需在 B-13 加定时事件）。推荐 A，先对齐行为。
7. **合同借阅申请归属**：见结构问题 6，需用户定归 B-31 还是合同（B-05）。

## 聚合稿回补（交 one-architecture 并入 `ENTERPRISE_AGGREGATES_DRAFT.md` §2.2）

写法同聚合稿 §2.1：列依次为聚合、内含、对应、不变量、命令（→ 事件；前置 INV）、对外事件（订阅方）、引用与依赖方式。新不变量暂编 INV-新-P1…，新对象暂编 OBJ-新-P1…，由 one-architecture 正式编号。命令与事件名为中文草案，交 one-glossary。

#### 1. B-31 行政事务（`organization.administration`，S-09，intent 2026-10-16-152807；证据：`OA_PROCESS_INVENTORY.md` 第 111–122 行，A8 模板只作证据）

信息图没有这四个对象（行政流程原定「留在 OA」，C-3、C-5、C-6、C-7 后改迁入，intent 2026-10-16-144943），须先在信息图补对象，故对应列写「一对一（OBJ-新）」。

| # | 聚合 | 内含 | 对应 | 不变量 | 命令（→ 事件；前置 INV） | 对外事件（订阅方） | 引用与依赖方式 |
|---|---|---|---|---|---|---|---|
| AGG-113 | 用印申请（OBJ-新-P1 用印申请） | 印章类别、用印法人、用印事由与文件、份数、来源业务单据引用（报价、合同等，只记录）、用印登记行（实际用印时间、份数、经办人）；状态：草稿 → 审批中 → 已批准 / 已驳回 → 已用印 → 已关闭 | 一对一（OBJ-新） | INV-新-P1 用印须基于已批准的申请；INV-新-P2 登记的用印份数不超过批准份数 | 提交用印申请（→ （内部））；批准用印申请（→ 用印申请已批准；由 platform.approval 回调）；驳回用印申请（→ （内部））；登记用印（→ 已用印；前置 INV-新-P1、P2）；关闭用印申请（→ （内部）） | 用印申请已批准、已用印（仅分析） | AGG-60 用印法人（api）；AGG-62 申请人与经办人（api）；platform.approval 审批（api）；B-03 / B-05 来源业务单据（不校验，只作回溯线索，避免 B-31 落到收入链下游） |
| AGG-114 | IT 需求工单（OBJ-新-P2 IT 需求工单） | 需求类别、描述、优先级、处理人、处理记录、处理结论；状态：已提交 → 审批中（需审批的类别）→ 处理中 → 已解决 → 已关闭 / 已退回 | 一对一（OBJ-新） | INV-新-P3 关闭须有处理结论 | 提交 IT 需求（→ （内部））；批准 IT 需求（→ （内部））；受理 IT 需求（→ （内部））；解决 IT 需求（→ IT 需求已解决）；关闭 IT 需求（→ （内部）；前置 INV-新-P3）；退回 IT 需求（→ （内部）） | IT 需求已解决（仅分析） | AGG-62 提出人与处理人（api）；platform.approval 审批（api，仅需审批的类别） |
| AGG-115 | IT 资产使用记录（OBJ-新-P3 IT 资产使用记录；会计对象仍是 OBJ-93 公司资产） | 资产卡片引用、使用人、使用期间、领用 / 调拨 / 归还单据、维修工单（报修、处理、结果）；不含报废与盘点（账务归 B-24，intent 2026-10-16-152926） | 一对一（OBJ-新） | INV-新-P4 同一资产的使用期间不重叠（同一时刻只有一个使用人） | 申请领用资产（→ （内部））；批准领用（→ （内部））；登记领用（→ 资产已领用；前置 INV-新-P4）；调拨资产（→ 资产已调拨；前置 INV-新-P4）；归还资产（→ 资产已归还）；报修资产（→ （内部））；登记维修结果（→ （内部）） | 资产已领用 / 已调拨 / 已归还（仅分析；离职交接可读） | AGG-91 资产卡片（api，B-31 → B-24，intent 152926）；AGG-62 使用人（api）；platform.approval 审批（api） |
| AGG-116 | 员工活动申请（OBJ-新-P4 员工活动申请） | 活动类型（周年贺卡、节日活动、创新项目发布等，参考数据）、申请人、参与人、费用预估引用（只记录）；状态：草稿 → 审批中 → 已批准 / 已驳回 → 已完成 | 一对一（OBJ-新） | — | 提交员工活动申请（→ （内部））；批准员工活动申请（→ 员工活动申请已批准）；驳回员工活动申请（→ （内部））；完成员工活动（→ （内部）） | 员工活动申请已批准（仅分析） | AGG-62 申请人与参与人（api）；platform.approval 审批（api）；B-12 费用报销（不校验，活动费用若报销由 B-12 引用本申请） |

- AGG-115 的排他约束跨多张申请单（使用期间是资产维度的事实），边界提给 one-architecture：可选「使用期间」作根、领用 / 调拨 / 归还单据作次级。
- 新边：B-31 → B-13（api）、B-31 → platform.approval（api）、B-31 → B-24（api）；无反向边，B-31 不被业务 BC 依赖，不成环。
- 离职交接（PRC-128 / 138）需读「未归还资产」：PRC-128 在 B-13，B-13 不能依赖 B-31（B-31 → B-13 已有）；建议离职交接清单作读模型（reporting）或由 B-31 订阅 Separated 生成归还待办（B-31 → B-13 事件，方向一致）。

#### 2. AGG-117、AGG-118 回写聚合稿的行

| # | 聚合 | 内含 | 对应 | 不变量 | 命令（→ 事件；前置 INV） | 对外事件（订阅方） | 引用与依赖方式 |
|---|---|---|---|---|---|---|---|
| AGG-117 | 法人归属（已建 `LegalEntityAttribution`，表 `organization_workforce.legal_entity_attributions`；Q-09 三口径中的「数据范围法人」） | 部门默认法人期间、员工单独指定法人期间、更正记录（只追加） | 一拆多（OBJ-55、OBJ-56 的法人属性版本） | INV-新-P5 同一部门 / 员工的归属期间不重叠（已建 `EXCLUDE` 约束）；INV-59（读） | 指定法人归属（→ 法人归属已变更）；更正法人归属（→ 法人归属已更正）（命令名以已建代码为准，交 one-glossary 对词表） | 法人归属已变更（platform.identity：数据范围；B-16 等按日期经 api 读取，不订阅） | AGG-60 法人主体（api）；AGG-61 组织单元（api）；AGG-62 人员（api） |
| AGG-118 | 假期类型与额度规则（配置型版本，每版本一个聚合 + 部分唯一索引） | 假期类型（参考数据：年假、病假、事假、婚假、产假等）、按城市与司龄的应享规则、结转规则、生效日、确认人、依据引用 | 技术聚合（配置；OBJ-68 的「假期类型」属性与 OBJ-69 描述中的「额度政策」，信息图无独立对象） | INV-新-P6 同一假期类型同一适用范围只有一个生效版本（D15） | 发布假期规则版本（→ 假期规则已生效） | 假期规则已生效（同 BC：AGG-71 年度开账按规则版本计算应享，按 ID 与日期读取，不订阅） | platform.audit 审计（api） |

#### 3. AGG-93 补 OBJ 对应

AGG-93 人员薪酬档案的「对应」列改为：**一拆多（OBJ-61 薪酬档案，信息图已定为「员工 / 任职的属性版本、调薪为事件」，非独立对象；聚合承载 OBJ-56 的薪酬属性版本与专项附加扣除年度属性）**。依据：`ENTERPRISE_INFO_MAP_DRAFT.md` OBJ-61 行（第 120、459 行）；调薪申请（第 434 行）是薪酬档案新版本的单据。

#### 4. 「入职生效」命令与「入职已登记」事件（样板 S3）

AGG-62 人员命令列补：登记入职（→ **入职已登记**，原为 → Employed）；**入职生效**（系统定时：入职日到达时执行；补登时立即执行 → Employed）。对外事件列补：入职已登记（platform.identity：PRC-137 建立待开通登录身份）；Employed 订阅方增 platform.identity（放行登录）。状态补「待入职」（已登记、未到入职日）。依据：U-12（intent 2026-10-16-134434）、Q-03（intent 2026-10-16-134134）、`SAMPLE.md` S2–S4；边 `platform.identity → organization.workforce` 已建，不新增。

同理补 AGG-67「驳回劳动合同（→ （内部））」（样板第 5 步退回分支）；PRC-83 第 7 步「员工法人三口径比对预警」已移出，改为 B-15 的事件策略（S-20 已定）：订阅 B-13「劳动合同已签订」「法人归属已变更」与本 BC「薪酬档案已变更」，读 B-13 另两口径，不一致预警人力；不作命令。编号 PRC-145（登记于 closeout/process-catalog.yaml）。

另见各分片「新用语」中的〔缺〕命令与事件（如 B-31 的驳回领用、驳回调拨、撤回用印申请等，修正了上文第 1 节）。

## 附：每步在哪里完成（`where`，判据同 SAMPLE §2a）

### 入转调离（PRC-79–82、85、128–130、136、138）

判据：业务决定与登记在本系统（in_system）；规则能定的由本系统自动做（automatic）；外部系统有接口、本系统能发起并拿到回执的走集成（external:<系统名>）；只能在系统外完成、没有可用接口的线下办（offline），本系统只登记结果与证据。

| 流程 | 步 | 在哪 | 本系统留下的证据 | 为什么这样放 |
|---|---|---|---|---|
| PRC-80 | 1 登记法人主体 | in_system | — | 法人主数据在本系统 |
| PRC-80 | 2 调整组织单元 | in_system | — | B-13 自为记录系统（U-1） |
| PRC-80 | 3 指定法人归属 | in_system | — | 三口径之一，规则在本系统 |
| PRC-80 | 4 停用岗位 | in_system | — | 职位目录在本系统 |
| PRC-80 | 5 标记数据范围待复核 | automatic | — | P-31 事件反应 |
| PRC-81 | 1 读职位与组织单元 | in_system | — | 读本 BC |
| PRC-81 | 2 提交招聘需求 | in_system | — | 业务单据在本系统 |
| PRC-81 | 3 批准招聘需求 | in_system | — | 聚合内单级批准（F-3） |
| PRC-81 | 4 关闭招聘需求 | in_system | — | 同上 |
| PRC-82 | 1 登记候选人 | in_system | — | 候选人记录在本系统（T-5，个人信息保护） |
| PRC-82 | 2 记录面试 | in_system | — | 面试本身在系统外进行，本系统登记评价与结论 |
| PRC-82 | 3 作出录用决定 | in_system | — | 业务决定登记在本系统 |
| PRC-82 | 4 登记候选人答复 | offline | 候选人回执（邮件或签回的录用通知）、确认日、确认的入职日 | 录用通知和候选人答复在系统外完成 |
| PRC-82 | 5 标记未录用 | in_system | — | 结论登记在本系统，通知候选人线下 |
| PRC-85 | 1 编制试用期绩效评估 | in_system | — | 评估单据在本系统 |
| PRC-85 | 2 确认绩效评估 | in_system | — | 同上 |
| PRC-85 | 3 办理转正 | in_system | — | 人事记录在本系统 |
| PRC-128 | 1 申请离职 | in_system | — | 员工在系统内申请（线下提出的由人力代录入） |
| PRC-128 | 2 发起公司终止 | in_system | — | 同上 |
| PRC-128 | 3 批准离职申请 | in_system | — | 聚合内单级批准（F-3） |
| PRC-128 | 4 驳回离职申请 | in_system | — | 同上 |
| PRC-128 | 5 登记劳动合同终止依据 | offline | 离职协议或解除 / 终止通知扫描件、签署或送达日、离职日 | 协议和通知的签署送达在系统外 |
| PRC-128 | 6 办理离职（登记） | in_system | — | 人事登记在本系统 |
| PRC-128 | 7 离职生效 | automatic | — | 到日生效是规则（U-12），发 Separated 即吊销（RULE-363） |
| PRC-128 | 8 终止任职 | automatic | — | 随离职生效自动 |
| PRC-128 | 9 终止劳动合同 | automatic | — | 同上，失败告警不回滚 |
| PRC-128 | 10 撤销离职登记 | in_system | — | 离职日前撤销 |
| PRC-129 | 吊销会话 | automatic | — | 安全关键事件反应（§1.4 G-2） |
| PRC-130 | 参保减员 | offline | 社保减员回执、公积金封存或转移回执、办理日 | 人社与公积金网办没有面向本系统的接口，办理后回填（有接口再改 external，待核） |
| PRC-136 | 1 提交调岗申请 | in_system | — | 人事登记在本系统 |
| PRC-136 | 2 批准调岗申请 | in_system | — | 聚合内单级批准 |
| PRC-136 | 3 驳回调岗申请 | in_system | — | 同上 |
| PRC-136 | 4 提交劳动合同（跨法人） | in_system | — | 合同要素在本系统起草 |
| PRC-136 | 5 批准劳动合同（跨法人） | in_system | — | 聚合内单级批准 |
| PRC-136 | 6 签订劳动合同（跨法人） | offline | 三方变更协议或新劳动合同扫描件、签署日 | 目前纸面签署 |
| PRC-136 | 7 变更任职 | automatic | — | 规则能定，原子写入未来版本 |
| PRC-136 | 8 任职变更生效 | automatic | — | 到业务生效日发事件（U-12） |
| PRC-136 | 9 终止劳动合同（原） | automatic | — | 随生效日自动 |
| PRC-136 | 10 重算默认角色与数据范围 | automatic | — | 事件反应（S10） |
| PRC-136 | 11 标记数据范围待复核 | automatic | — | 事件反应，只提醒不收回 |
| PRC-136 | 12 调整薪酬档案 | in_system | — | 薪酬自算，档案是计算依据（B-15） |
| PRC-136 | 13 变更参保缴纳主体 | offline | 社保增减员回执、公积金缴存单位变更回执、办理日 | 网办平台无面向本系统的接口 |
| PRC-138 | 1 吊销会话 | automatic | — | 调用 PRC-129 |
| PRC-138 | 2 停用登录身份 | automatic | — | 以在职为准（IDENTITY_LIFECYCLE） |
| PRC-138 | 3 删除全部角色授予 | automatic | — | ADR-0027 第 1 项第 6 点 |
| PRC-138 | 4 确认放行离职处置 | in_system | — | 防全员锁死，另一名超管人工确认 |
| PRC-138 | 5 停用应急账号 | automatic | — | 同 ADR-0027 |
| PRC-138 | 6 停用企业微信成员 | external:企业微信 | 企业微信返回的停用结果、停用时间 | 经 platform.integration 适配器自动停用，与开通对称（T-1） |
| PRC-138 | 7 解绑外部身份 | automatic | — | 按 userid 精确解绑，保留历史 |
| PRC-138 | 8 逾期告警 | automatic | — | 不静默 |
| PRC-138 | 9 登记企业微信成员手工停用 | external:企业微信 | 企业微信管理端停用记录、停用时间、经办人 | 自动停用失败时，信息部门在企业微信后台手工处理，本系统只登记并重试 |
| PRC-79 | 登记继续教育学时 | in_system | — | 注册执业人员录入，学时证明作附件 |
| PRC-79 | 延续注册执业证书 | offline | 延续注册回执或新证书扫描件、新有效期止 | 在注册执业管理机构办理，无接口 |
| PRC-79 | 标记执业证书已到期 | automatic | — | 有效期届满是规则 |

### VS-13 获得报酬（PRC-113、115–127、134、145）

判据同 SAMPLE 2a：in_system 用户在本系统操作；automatic 本系统规则自动；external:<系统> 经集成由外部完成；offline 线下完成，本系统登记结果与证据。

| 流程 | 步 | 在哪 | 本系统留下的证据 | 为什么这样放 |
|---|---|---|---|---|
| PRC-115 | 1 导入考勤记录 | external:企业微信 | 导入批次号、拉取日期、记录条数、校验和 | 企业微信是打卡来源（intent 152603），有接口，经 platform.integration 每日拉取 |
| PRC-115 | 2 告警人力与信息，不静默； | automatic | — | 规则能定：失败即告警，不静默（CLAUDE.md 第 6 条） |
| PRC-115 | 3 补卡 | in_system | — | 补卡是员工在本系统登记的事实，原记录不改 |
| PRC-116 | 1 读工作日历与标准工时版本 | in_system | — | 读工作日历与标准工时是规则 |
| PRC-116 | 2 占用余额 | in_system | — | 额度占用是员工提交的一部分 |
| PRC-116 | 3 提交请假 | in_system | — | 申请在本系统提交 |
| PRC-116 | 4 请假审批 | in_system | — | 通用审批在 platform.approval（C-3–C-7） |
| PRC-116 | 5 〔缺〕驳回请假 | automatic | — | 审批驳回的回调，规则 |
| PRC-116 | 6 批准请假 | automatic | — | 审批通过的回调，规则 |
| PRC-116 | 7 批准转已用 | automatic | — | 同 BC 事件反应，规则 |
| PRC-116 | 8 撤销请假 | in_system | — | 员工自己的操作 |
| PRC-116 | 9 销假 | in_system | — | 员工自己的操作 |
| PRC-117 | 1 加班申请 | in_system | — | 员工或用人经理在本系统提交 |
| PRC-117 | 2 加班审批 | in_system | — | 通用审批 |
| PRC-117 | 3 批准加班 | automatic | — | 审批回调，规则 |
| PRC-117 | 4 〔缺〕驳回加班 | automatic | — | 审批回调，规则 |
| PRC-118 | 1 汇总考勤、请假、加班；月 | automatic | — | 汇总是规则；缺导入批次则失败告警 |
| PRC-118 | 2 生成月度考勤 | automatic | — | 月末到期自动生成 |
| PRC-118 | 3 员工确认 | in_system | — | 员工本人在本系统确认（企业微信入口登录） |
| PRC-118 | 4 补卡 | in_system | — | 员工在本系统补卡或另提请假 |
| PRC-118 | 5 〔缺〕重新生成月度考勤 | automatic | — | 更正后自动重新汇总 |
| PRC-118 | 6 标记加班已补偿 | automatic | — | 事件反应，规则 |
| PRC-118 | 7 〔缺〕代员工确认 | in_system | — | 离职人员账号已吊销，只能人力代确认 |
| PRC-118 | 8 锁定 | in_system | — | 人力在本系统锁定（确认期限届满） |
| PRC-120 | 1 编制绩效评估 | in_system | — | 用人经理在本系统评估 |
| PRC-120 | 2 提交绩效评估 | in_system | — | 同上 |
| PRC-120 | 3 确认绩效评估 | in_system | — | 同上 |
| PRC-121 | 1 编制激励方案版本 | in_system | — | 人力在本系统编制 |
| PRC-121 | 2 激励方案审批 | in_system | — | 通用审批 |
| PRC-121 | 3 生效激励方案版本 | in_system | — | 人力在本系统生效 |
| PRC-122 | 1 读激励方案与核定依据的绩 | in_system | — | 读取依据，人力在本系统 |
| PRC-122 | 2 〔缺〕草拟奖金核定 | in_system | — | 人力在本系统草拟 |
| PRC-122 | 3 核定奖金 | in_system | — | 管理层在本系统核定 |
| PRC-124 | 1 读月度考勤已锁定（含锁定 | in_system | — | 读取上游，缺失即告警 |
| PRC-124 | 2 计算 | in_system | — | 薪酬自算（intent 152603），人力触发，规则版本计算 |
| PRC-124 | 3 提交审核 | in_system | — | 人力在本系统提交 |
| PRC-124 | 4 薪酬审核 | in_system | — | 通用审批（财务主管） |
| PRC-124 | 5 批准 | automatic | — | 审核回调，规则 |
| PRC-124 | 6 提交过账候选 | automatic | — | 批准即提交一次过账候选 |
| PRC-125 | 1 读当月参保增减员、薪酬结 | in_system | — | 读取是规则 |
| PRC-125 | 2 生成申报 | in_system | — | 系统生成申报数据（方式 a，待用户 U1） |
| PRC-125 | 3 提交申报 | offline | 个税申报回执、申报日、申报明细文件 | 税务申报端没有已核实的面向本系统的接口，人工申报回填回执；若定方式 b 改 external:税务（U1） |
| PRC-125 | 4 提交申报 | offline | 社保费申报回执、公积金汇缴回执、申报日 | 社保公积金经办渠道各地不同、无已核实接口，人工办理回填回执（U2） |
| PRC-125 | 5 登记缴款 | offline | 个税缴款凭证、社保费与公积金缴款凭证、缴款日 | 缴款在银行或税务端完成，本系统登记凭证 |
| PRC-125 | 6 更正申报 | offline | 更正申报回执、更正原因 | 更正在申报端完成，本系统登记回执 |
| PRC-125 | 7 提交过账候选 | automatic | — | 过账候选是规则 |
| PRC-113 | 1 并入扣缴申报 | automatic | — | 规则：自然人股东并入扣缴申报 |
| PRC-126 | 1 提交应付薪酬 | automatic | — | 规则：批准即提交应付 |
| PRC-127 | 1 标记薪酬已发放 | automatic | — | 规则：应付结清即置已发放 |
| PRC-134 | 1 标记已过账 | automatic | — | 规则：过账完成即置位 |
| PRC-145 | 1 三口径比对并预警 | automatic | — | 规则：比对三口径只预警 |
| PRC-119 | 1 维护工作日历年度 | in_system | — | 人力在本系统维护 |
| PRC-119 | 2 发布标准工时版本 | in_system | — | 人力在本系统发布 |
| PRC-123 | 1 调整薪酬档案 | in_system | — | 人力在本系统调整 |
| PRC-123 | 2 更新专项附加扣除 | in_system | — | 人力在本系统更新（来源见 U9） |

### VS-09 / VS-11 与 B-18、B-30 表

本组三个流程没有线下（offline）或外部系统（external）步骤，没有必填证据项。

| 流程 | 步 | 命令 | 谁 | 在哪里 | 页面或备注 |
|---|---|---|---|---|---|
| PRC-88 | 1 | 编制经营计划版本 | SH-M1 管理层 | in_system | 经营计划编辑页 |
| PRC-88 | 2 | 经营计划审批 | SH-M1 管理层 | in_system | platform.approval 审批待办；企业微信待办通知 |
| PRC-88 | 3 | 基线化经营计划 | SH-M1 管理层 | in_system | 经营计划详情页 |
| PRC-88 | 4 | 开立预算账户 | SH-E6 财务 | in_system | 预算账户管理页 |
| PRC-88 | 5 | 调整预算 | SH-E6 财务 | in_system | 预算账户调整页 |
| PRC-89 | 1 | 编制预测版本 | SH-E5 市场和客户经理 | in_system | 销售预测填报页；每周 |
| PRC-89 | 2 | 编制现金流预测 | SH-E6 财务 | in_system | 现金流预测编辑页；按期 |
| PRC-107 | 1 | 起草知识条目 | SH-E4 技术专家 | in_system | 知识条目编辑页 |
| PRC-107 | 2 | 创建知识条目 | SH-E2 项目经理 | in_system | 页面在 B-04 项目复盘详情页，命令调 B-30 |
| PRC-107 | 3 | 提交知识条目评审 | SH-E4 技术专家 | in_system | 知识条目详情页 |
| PRC-107 | 4 | 评审知识条目 | SH-E4 技术专家 | in_system | 知识条目评审页；聚合内审批 |
| PRC-107 | 5 | 发布知识条目 | SH-E4 技术专家 | in_system | 知识条目详情页 |
| PRC-107 | 6 | 修订知识条目 | SH-E4 技术专家 | in_system | 有效期届满或适用范围变化时；定时提醒后人工操作 |
| PRC-107 | 7 | 废止知识条目 | SH-E4 技术专家 | in_system | 有效期届满时；定时提醒后人工操作 |

PRC-107 步 6、7 的「定时」指系统到期提醒（时间触发），修订与废止本身由人操作，所以 `where` 是 in_system，不是 automatic。

### B-31 行政事务（PRC-139、142–144，AGG-113–116）

| 流程 | 步 | 在哪 | 本系统留下的证据 | 为什么这样放 |
|---|---|---|---|---|
| PRC-139 | 1 提交用印申请 | in_system | — | 申请与审批要留痕，来源单据只记 ID |
| PRC-139 | 2 批准用印申请 | in_system（platform.approval） | — | C-3–C-7 行政流程走通用审批 |
| PRC-139 | 3 驳回用印申请 | automatic | — | 审批平台回调，规则决定 |
| PRC-139 | 4 登记用印 | offline | 实际用印时间、份数、经办人、盖章文件扫描件〔推〕 | 实物印章只能系统外盖，本系统登记并卡份数（INV-新-P2） |
| PRC-139 | 5 关闭用印申请 | in_system | — | 行政确认完成或放弃余量 |
| PRC-142 | 1 提交 IT 需求 | in_system | — | 工单起点 |
| PRC-142 | 2 批准 IT 需求 | in_system（platform.approval） | — | 仅需审批的类别 |
| PRC-142 | 3 受理 IT 需求 | in_system | — | 指派处理人，计时 |
| PRC-142 | 4 解决 IT 需求 | in_system | — | 实际排查在系统外，本系统记过程与结论（INV-新-P3 需结论） |
| PRC-142 | 5 关闭 IT 需求 | in_system | — | 提出人确认；长期不确认自动关闭〔推〕 |
| PRC-142 | 6 退回 IT 需求 | in_system | — | 驳回或不成立，写原因 |
| PRC-143 | 1 申请领用资产 | in_system | — | 引用 B-24 卡片 ID |
| PRC-143 | 2 批准领用 | in_system（platform.approval） | — | 通用审批 |
| PRC-143 | 3 驳回领用 | automatic | — | 审批平台回调 |
| PRC-143 | 4 登记领用 | offline | 领用人签收、交付日期、设备状态〔推〕 | 实物交付在系统外，本系统开使用期间（INV-新-P4） |
| PRC-143 | 5 申请调拨资产 | in_system | — | 调拨须审批〔推〕 |
| PRC-143 | 6 批准调拨 | in_system（platform.approval） | — | 通用审批 |
| PRC-143 | 7 驳回调拨 | automatic | — | 审批平台回调 |
| PRC-143 | 8 调拨资产 | offline | 调出 / 调入方交接确认、交接日期 | 实物交接在系统外，本系统同事务换使用人 |
| PRC-143 | 9 生成归还待办 | automatic | — | 订阅 Separated，规则决定 |
| PRC-143 | 10 归还资产 | offline | 归还验收人、归还日期、设备状态 | 实物验收在系统外 |
| PRC-143 | 11 报修资产 | in_system | — | 使用人填故障，生成维修工单 |
| PRC-143 | 12 登记维修结果 | external:维修服务商 | 维修单或发票引用、维修结果、完成日期 | 维修在系统外（含厂商），无接口，只登记结果；无法修复的报废归 B-24 |
| PRC-144 | 1 提交员工活动申请 | in_system | — | 申请与审批要留痕 |
| PRC-144 | 2 批准员工活动申请 | in_system（platform.approval） | — | 通用审批 |
| PRC-144 | 3 驳回员工活动申请 | automatic | — | 审批平台回调 |
| PRC-144 | 4 完成员工活动 | offline | 活动完成确认、实际参与人〔推〕 | 活动在系统外举办，本系统登记完成 |

## 附：验收场景（入转调离）

| 编号 | 场景 |
|---|---|
| PRC-80-S01 | 撤销仍有在职任职的组织单元被拒绝（INV-60） |
| PRC-80-S02 | 调整组织单元后区域负责人收到数据范围待复核提醒，授予不被收回（P-31） |
| PRC-80-S03 | 同一员工法人归属期间重叠被拒绝 |
| PRC-81-S01 | 用人经理提交、人力负责人批准；提出人与批准人为同一人被拒绝 |
| PRC-81-S02 | 驳回后关闭，状态为已关闭 |
| PRC-82-S01 | 候选人面试通过、录用、登记接受，发出录用决定已作出，PRC-83 启动 |
| PRC-82-S02 | 候选人放弃，流程以候选人已放弃结束，候选人个人信息仍受访问限制 |
| PRC-85-S01 | 试用期届满前生成评估待办，确认评估后办理转正，B-15 收到转正已办理 |
| PRC-85-S02 | 不予转正转入 PRC-128 |
| PRC-128-S01 | 员工申请、批准、离职日在未来：登记时不发 Separated，离职日结束时发出并吊销会话 |
| PRC-128-S02 | 离职日不晚于登记日：登记即刻生效并吊销 |
| PRC-128-S03 | 离职日前撤销，状态回在职，B-31 与 B-27 待办取消 |
| PRC-128-S04 | 公司单方终止，发起人不能批准自己发起的终止 |
| PRC-129-S01 | 收到 Separated 即吊销全部会话；吊销失败重试并告警 |
| PRC-130-S01 | 离职后 15 日内回填减员回执；逾期升级 |
| PRC-130-S02 | 退休返聘或实习离职，无需减员 |
| PRC-136-S01 | 新职位已停用，调岗被拒绝 |
| PRC-136-S02 | 提前登记的调岗，写入未来版本，业务生效日才发任职已变更；platform.identity 重算默认授予并生成待调整权限 |
| PRC-136-S03 | 主任职区间冲突被拒绝（INV-62） |
| PRC-136-S04 | 跨法人调动：新合同签订、原合同终止、薪酬扣缴义务人与参保缴纳主体变更；三口径不一致时 PRC-145 预警 |
| PRC-138-S01 | 离职后企业微信成员自动停用并解绑 |
| PRC-138-S02 | 企业微信停用失败，告警；信息部门手工停用并登记后重试成功 |
| PRC-138-S03 | 超管持有人离职：会话吊销与登录身份停用立即执行，角色授予删除等待另一名超管放行 |
| PRC-79-S01 | 登记继续教育学时并延续注册，回填新有效期 |
| PRC-79-S02 | 证书有效期届满，系统标记已到期并提醒 |
