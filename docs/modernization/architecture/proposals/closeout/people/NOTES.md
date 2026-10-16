# 人员组 NOTES（one-arch-people）

按 `../CONVENTIONS.md` §4 固定小节。每节分「流程」「数据」两部分（试点：VS-07 流程、B-13/B-14/B-27 表）；样板相关见 `SAMPLE.md` 第 4–6 节。

## 接缝问题

### 流程（VS-07 试点）

1. platform.identity → B-13 事件订阅边不在 §4.1 边表（PRC-80/129 已隐含）；Employed、任职已变更对 platform.identity 与 B-15 的订阅方聚合稿未登记（PG-02）。
2. PRC-137/138 需要 platform.integration 通知 B-13（企业微信成员已创建 / 已停用），与现有 B-13 → platform.integration（api）反向；须在该边补「事件」方式，或改由 platform.identity 直接订阅 integration 事件、B-13 不参与（PG-04）。到期端口 `kernel.ExternalSubjectEmployees` 同样反向，按 C1 设计处理。
3. 接收端：B-15 对 B-13 任职已变更（PRC-136 步 5）；与薪酬组、leave 组（B-27）的接缝：调岗对请假审批人、在途单据是否联动，待 B-27 组确认。

### 数据（B-13、B-14、B-27 试点）

1. **B-14 → B-13 的法人引用无登记边**：`work_calendar_years.legal_entity_id`、`standard_work_hour_versions.legal_entity_id` 引用法人（AGG-60，B-13）；聚合稿 §4.1 只登记 B-14 → platform.audit。按 §2.1，下游引用上游应走 api 成边；B-14 与 B-13 同在 L2，加边是否成环或违层，请 one-architecture 定：加 B-14 → B-13（api）边，或法人 ID 按「不校验」存放并在 AGG-108 发布命令里由调用方保证。
2. **法人三口径不一致预警的归属**：劳动合同用人单位（B-13 AGG-67）、数据范围法人（B-13）、个税扣缴义务人（B-15 AGG-93 / 95）要比对。B-15 → B-13 有 api 边，预警由 B-15 读 B-13 做最顺；若放 B-13，会出现 B-13 → B-15 的反向读取（聚合稿 §4.1 无此边）。请定预警归属与方向，本稿不新增表。
3. **考勤记录 AGG-69 的数据入口**：日常打卡来自企业微信，经 platform.integration 进 AGG-69（用户事实 intent 2026-10-16-152603）。B-27 → platform.integration 边已登记；外部打卡记录 ID 只作来源对照。需确认 platform.integration 提供的是拉取适配器（防腐层转换后调用 B-27 api 写入）还是事件，及外部读取的超时与降级（§14.3 I2、I7）。历史打卡（A8，约 79.5 万行）走迁入通道，来源对照要区分来源系统。
4. **B-06 读请假与标准工时**：INV-65 需要 B-27（已批准 + 待批请假时长）与 B-14（标准工时），§4.1 已登记 B-06 → B-14、B-27；仅提醒请假行的敏感度 tbd（病假）会限制 B-06 读取的出参，应只返回时长，不带假期类型与事由。

## 结构问题

### 流程（VS-07 试点）

1. AGG-61/62/64 现表述为 A8 同步快照，与 U-1、U-7、Q-03 矛盾；需改写（已判落地改写，PG-14）。
2. AGG-64 岗位 → 职位目录条目 + 职级（Q-08）；AGG-65 随 Q26 与 P-33 作废；Q27 → Q-09 写回（PG-08、11）。
3. Employed / Separated 发出时机须为业务生效日（U-12），并新增「入职已登记」，需「定时命令」约定（PG-01、12）。
4. AGG-63 缺原子「变更任职」（PG-05）；AGG-62/67 缺放弃、驳回、撤回、公司单方终止命令（PG-09）。
5. 外部身份绑定归属与 platform.identity 命令模型（PG-03），关联 ADR-0039 的 C1 设计。
6. PRC-129 只登记吊销会话，ADR-0027 第 1 项范围更大（PG-13）。

### 数据（B-13、B-14、B-27 试点）

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

## 新用语

### 流程（VS-07 试点）

见 `PROCESS_SKELETONS.md` 第 5 节：业务生效日、入职已登记、变更任职、登录身份、外部身份、绑定外部身份、解绑外部身份、停用登录身份、企业微信成员已创建、企业微信成员已停用、职位目录条目。

### 数据（B-13、B-14、B-27 试点）

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

## 格式问题

### 流程（VS-07 试点）

1. SH-E6 内部分人力 / 信息 / 财务等无法区分，建议加可选字段 `note`（如 roles 项 `{id: SH-E6, as: performer, note: 信息}`）或把 SH-E6 细分子编号；本片段暂用行尾注释。
2. 泛称「员工」无 SH- 编号（离职申请人、新员工）；暂借 SH-E3，建议加。
3. 步骤无 SH- 的外部系统手工动作（企业微信开通 / 停用，命令缺）写成无 command 的步骤；建议约定字段（如 `external: true`）。
4. 判断点依据无字段，本片段写在注释；建议在 decision 项加可选 `basis`。
5. 同一人员既发起又审批时同一 SH- 在 roles 重复出现（不同 as），请确认允许。
6. 平台自动反应用 `actor: platform`（v1 `platform_task`），纯系统步骤用 `system`，请确认。
7. 聚合内单级批准按 `approval` 写，doc_type 注明「聚合内单级批准命令，不经 platform.approval」。

### 数据（B-13、B-14、B-27 试点）

1. 个人信息标记（NONE / PI / SPI，ADR-0043）无字段：现写在 `reason` 里。建议 `sensitivity` 之外加 `pi_flag`（NONE / PI / SPI）。
2. `sensitivity` 取值：CONVENTIONS 示例为小写 `internal`，`data-classification.txt` 为大写 `INTERNAL`；本稿用大写，请统一。
3. `references.to` 只能写 AGG-NN；引用 platform.*（审批流程实例、来源对照、身份）没有聚合编号，本稿放在说明稿。建议允许 `{to: platform.approval, by: ..., cross_bc: true}` 形式。
4. 无「聚合编号待定」的写法：本稿用 `AGG-待定-<名称>`，请定占位规则。
5. 无数据类别（交易 / 事实 / 配置 …）、迁入来源与通道（`via`）、状态（已建 / 改形 / 新建）字段，已放说明稿；若要机器核对（D17 守护 G-D11、D14 血缘），建议加 `data_category` 与 `migration_via`。
6. `versioned` 的 `none` 与 D17 豁免类别（四类）无结构化字段，现写 `reason`；`effective_dated` 又分 DECLARED / OBSERVED、配置型 D15 与维度型 D6，建议加 `version_basis`。
7. `role: version` 与 `child` 对「根下的次级版本」界线：本稿 `org_unit_legal_entities` 引用的是聚合根（归属根），归 `version`；请确认。

## 待用户定

### 流程（VS-07 试点）

1. 批准人：招聘需求、劳动合同、离职申请的批准人是人力（负责人）还是用人经理的上级；是否要多级（多级则须移出 B-13，聚合稿 §7 第 12 条）。
2. 录用决定是否需批准；调岗是否需批准、谁发起；不予转正的处理与依据。
3. Employed / 入职已登记的发出时机，企业微信开通相对入职生效日的提前量与责任时限（Q-03 无兜底，逾期告警阈值）。
4. 企业微信成员匹配到人员的键（工号、手机号等）。
5. 离职生效的时点（当日末还是次日零点）与企业微信账号停用时限、逾期告警阈值。
6. 哪些用工类型无需参保；调岗跨法人时劳动合同、薪酬档案、参保、个税口径如何联动（Q-09）。

### 数据（B-13、B-14、B-27 试点）

1. **人员档案范围**：首版人事记录是否装证件号码、出生日期、私人联系方式、学历、紧急联系人等（落入 SPI 或 L3，须单独同意与影响评估，个人信息保护法第二十九、五十五条，ADR-0043）。本稿未建表。
2. **历史月度考勤确认的迁入**：老系统是月 × 一级部门粒度（1,026 单），与新对象人员 × 月不对应；「全部历史迁入」下，是按人员 × 月回算成已锁定历史确认，还是只迁考勤 / 请假 / 加班原始行、历史确认不迁，只留核对。本稿暂记「不迁」。
3. **劳动合同、离职申请、加班、注册执业证书的老系统来源**：是否存在、在哪里，尚未取证；取证不需要用户，但若老系统没有这些记录，「全部历史迁入」对它们是空集，请知悉。
4. **病假等请假事由的个人信息级别**：病假、产假可推断医疗健康、生育信息，按 ADR-0043「拿不准按 SPI」暂记 tbd，交 U-11 法务裁定；若判 SPI，请假表需单独同意与影响评估。

## 铺开备忘（样板意见回来后用）

- 流程：B-30 无到 platform.approval 的边，PRC-107 评审按聚合内审批写并提示补边；B-15、B-18、B-27 可走 platform.approval。PRC-118「员工确认」是否算 approval 节点（格式问题）。PRC-122 触发事件「绩效评估已确认」也由试用期评估发出，事件须带评估类型。请假驳回、撤销、销假的额度回冲无命令。离职当月考勤由谁代确认。薪酬自算后 AGG-73 对「薪酬汇总导入」依赖作废，缺个税税率表、社保公积金比例等规则配置。个税与社保公积金申报方式列 a/b/c 选项入待用户定（intent 2026-10-16-152603）。
- 数据：薪酬、专项附加扣除、银行账户一律 SENSITIVE，未成年子女信息拿不准归 SPI 交 U-11；个税申报需证件号码，牵出人员档案范围待用户定；提议新聚合「薪酬规则版本」（配置型 D15）；代发明细的员工银行账户与 B-10 付款要求收款方为往来单位账户的接缝；外部知识平台文件夹引用与 D16 的关系；AGG-115 使用期间排他约束跨申请单的边界；AGG-64 暂不拆（S-10），注明可能拆分。
