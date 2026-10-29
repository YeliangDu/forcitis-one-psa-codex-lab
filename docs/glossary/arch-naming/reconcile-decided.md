# NAM-1017-GLO-05：已裁定用语对账与 proposed 分类

日期：2026-10-17；本地基线 `47ecece9`；正式词表 729 条。只新增本报告、机器明细（二乙 E4 起移出 main，在 archive/2026-10-b2b 上的 `docs/glossary/arch-naming/reconcile-decided.json`，取回：`git show archive/2026-10-b2b:docs/modernization/glossary/arch-naming/reconcile-decided.json`；构建改读同目录 `reconcile-decided-slim.json`）和[只读脚本](../coverage/reconcile_decided.py)。未修改正式词表、proposed、守护或验收测试，未提交、推送、联网或启动容器。

**门禁状态：本卡以开工 HEAD 为基线验证为 4 PASS、0 FAIL、12 SKIP；默认 origin/main 基线仍有 2 FAIL，未达到整分支交付门禁，不能按默认门禁报完成。** 两次结果分列在第 6 节。

明确抽取的已裁定内容为 **976 个去重审计单元、1383 条来源发生项**。发现 **60 个缺少登记的用语/指称单元（65 处发生项）**；另有 **7 个已有词条与裁定不一致（12 处）**。proposed 174 条非错误码分为 **A 66 / B 105 / C 3**；156 条错误码归 R-5。

这里的“单元”按概念 id，或未定 id 时的 bc＋英文/中文去重；包含受控动作原子、状态及明确不登记的治理项，不能当成待新增独立业务对象数。“漏登记”也包括中文指称已定、但只能在定义或草稿里找到的情况；是否作为独立概念、属性、枚举值或指称登记仍需设计。没有据此新增 60 个聚合或实体。

## 1. 证据与覆盖边界

- 扫描指定来源及引用材料 **775 个文件**，逐文件 SHA-256 存于明细 `inputs`；其中用语相关 intent 96 份，全文及候选词存于 `intent_ledger`。页面、草稿、题面和预选不作为提交证据。
- 原始提交台账 1,072 条，逐题文件路径、题号、原始 choice/label/note、日期及已有 submission 编号全部保留；验证时与原文件的键和值逐项相等。第三轮原始 410 条中，408 条有导出决定，ACL-TRANSITION G58/G59 由 `apply_acl.py` 的 HELD 明确暂缓，不能当普通漏登。废弃的第一次第三轮“365 条全同意”不作最终裁定，依据 intent `2026-10-15-161339…先等等…`。
- 架构 JSON 169 个题面出现位置：156 项关联本地提交 intent 及所选项；其余是 `COMPLETENESS_QUESTIONS.json` 的 12 道前置草稿和 `ADRB A-2` 当时未提交项。A-2 的补答另在 ADR5051 A-2 留痕，未替旧页伪造提交。INFOMAP 的多选、暂不定和特殊处理均保留，未用推荐项代填。
- 第一、二轮 24 个方言别名索引由 `build_terms.py` 字面量＋源术语/OA/R0 输入恢复；没有执行生成器，也没有拿当前别名数组的下标猜当年的对象。
- 名称、英文、kind、bc 分开处理。NM2–NM4 中文认可不代表批准聚合边界；N9 的英文与 kind 属设计定稿。已定的 BC 迁移、GL 改名及后续 NM 改名列入 `superseded_fields`。例如旧“月度薪酬核算”由后续“月度薪酬结算单”承接，不能再报一条漏登。
- 同中文名不能直接匹配不同概念。ACL `StaffEmployee`（原中文“人员”，后让名“员工任职快照”）不能命中正式 `Person`；本次抽样发现并修正了这个匹配错误。
- CAPINFO 本地只有带提交编号 `CAPINFO-20261017-111010-cfy` 的交接稿和已登记项，未见原始提交文件。对象交接的当前登记可核实；能力、价值流和备名仍作设计推导待人工，不宣称见到原始批准。

### 对账口径

逐条 `records` 保存原词、裁定动作、原始来源、实际比较字段、匹配概念、后续取代字段及差异。以下分类按来源发生项统计，跨批重提同词会重复出现；第三轮镜像原始答复不再计一次。

| 结果 | 发生项 |
| --- | --- |
| 已登记且一致 | 927 |
| 已登记但不一致 | 12 |
| 漏登记 | 65 |
| 应不登记 | 109 |
| 待人工 | 297 |

待人工 297 处中，27 处属于抽取/批准范围不明确，270 处属于中文或归类已定但登记形态仍待核对；后者仍计入已裁定内容。另有 61 道规则答复完整保留在 `answer_ledger`，尚不据其业务规则文字自动拆出新的用语裁定，主要是缺题面的 FD 41 题，其余 F1 6、FD2 6、D5 5、C6 2、A1 1。它们不是“未提交”。

| 批次 | 一致 | 不一致 | 漏登 | 应不登记 | 待人工 |
| --- | --- | --- | --- | --- | --- |
| 第一轮 | 190 | 3 | 0 | 4 | 0 |
| 第二轮 | 42 | 0 | 0 | 38 | 0 |
| 第三轮 | 245 | 2 | 0 | 43 | 11 |
| 门禁 | 6 | 0 | 1 | 0 | 0 |
| GL2 | 38 | 0 | 2 | 2 | 2 |
| ACL | 41 | 0 | 0 | 9 | 59 |
| NM1 | 0 | 0 | 0 | 0 | 182 |
| NM2 | 21 | 0 | 1 | 0 | 8 |
| NM3 | 26 | 1 | 3 | 1 | 0 |
| NM4 | 21 | 0 | 4 | 0 | 0 |
| NM5 | 6 | 0 | 1 | 0 | 0 |
| CAPINFO | 13 | 0 | 0 | 0 | 21 |
| 其他 | 278 | 6 | 53 | 12 | 14 |

“其他”包含 GL、B1、rulings、FD/F1/D5/A1 的明确用语裁定及架构 intent；NM1–NM5 保留各自题号，未合并成无法追溯的一批。

## 2. 漏登记与原因

### 优先核对的前 20 项

| 序 | 中文 | 英文（草稿者仅作建议） | bc（未裁定者待设计） | 裁定出处 |
| --- | --- | --- | --- | --- |
| 1 | 线索 | Lead | presales.opportunity | [2026-10-15-205406-one-architecture-售前信息图确认页保存-合同与项目.md](../../decisions/2026-10-15-205406-one-architecture-售前信息图确认页保存-合同与项目.md) `—` |
| 2 | 劳动合同 | EmploymentContract | organization.workforce | [2026-10-16-114200-one-architecture-符合性审查确认页提交-F-1-至.md](../../decisions/2026-10-16-114200-one-architecture-符合性审查确认页提交-F-1-至.md) `—` |
| 3 | 证据 | AcceptanceEvidence | delivery.acceptance | [2026-10-16-114200-one-architecture-符合性审查确认页提交-F-1-至.md](../../decisions/2026-10-16-114200-one-architecture-符合性审查确认页提交-F-1-至.md) `—` |
| 4 | 证据关联记录 | EvidenceLinkRecord | delivery.acceptance | [2026-10-16-114200-one-architecture-符合性审查确认页提交-F-1-至.md](../../decisions/2026-10-16-114200-one-architecture-符合性审查确认页提交-F-1-至.md) `—` |
| 5 | 投标 | Bid | presales.opportunity | [2026-10-15-202939-one-architecture-售前业务对象问题页保存-线索单独.md](../../decisions/2026-10-15-202939-one-architecture-售前业务对象问题页保存-线索单独.md) `—` |
| 6 | 往来单位 | Counterparty | masterdata.counterparty | [2026-10-15-210513-one-architecture-售前聚合与-BC-确认页保存-一.md](../../decisions/2026-10-15-210513-one-architecture-售前聚合与-BC-确认页保存-一.md) `—` |
| 7 | 建设工程 | ConstructionProject | masterdata.counterparty | [2026-10-15-234418-one-architecture-答复复核-R1–R5-提交-已提.md](../../decisions/2026-10-15-234418-one-architecture-答复复核-R1–R5-提交-已提.md) `—` |
| 8 | 协议价格 | AgreementPrice | masterdata.catalog | [2026-10-15-202939-one-architecture-售前业务对象问题页保存-线索单独.md](../../decisions/2026-10-15-202939-one-architecture-售前业务对象问题页保存-线索单独.md) `—` |
| 9 | 协议价目 | AgreementPriceList | masterdata.catalog | [2026-10-15-210513-one-architecture-售前聚合与-BC-确认页保存-一.md](../../decisions/2026-10-15-210513-one-architecture-售前聚合与-BC-确认页保存-一.md) `—` |
| 10 | 分包变更 | SubcontractChange | procurement.subcontracting | [NM3.json](../../decisions/decision-pages/answers/nm3.json) `NM3~NM3-11` |
| 11 | 收入确认行 | RevenueRecognitionLine | receivables.wip | [GL2.json](../../decisions/decision-pages/answers/gl2.json) `GL2~B2-X06` |
| 12 | 角色已废除 | RoleAbolished | platform.identity | [GL.json](../../decisions/decision-pages/answers/gl.json) `GL~B2-B06` |
| 13 | 任务已超时 | ApprovalTaskBecameOverdue | platform.approval | [GL.json](../../decisions/decision-pages/answers/gl.json) `GL~B2-B02` |
| 14 | 工时单已撤回审批 | TimesheetApprovalWithdrawn | cost.timesheet | [GL.json](../../decisions/decision-pages/answers/gl.json) `GL~B2-B07` |
| 15 | 来源对照登记已被拒绝 | SourceReferenceRefused | platform.integration | [GL.json](../../decisions/decision-pages/answers/gl.json) `GL~B2-B05` |
| 16 | 签约前投入 | PrecontractInvestment | cost.costing | [2026-10-15-124316-one-coordinator-财务确认单第-2-版答复的-13.md](../../decisions/2026-10-15-124316-one-coordinator-财务确认单第-2-版答复的-13.md) `—` |
| 17 | 进项发票 | InputInvoice | accounting.tax | [2026-10-16-072440-one-architecture-对象完整性与方法第-4-稿确认页.md](../../decisions/2026-10-16-072440-one-architecture-对象完整性与方法第-4-稿确认页.md) `—` |
| 18 | 经营产值 | 未定 | 待定 | [A1.json](../../decisions/decision-pages/answers/a1.json) `A1~A1-01` |
| 19 | 财务负责人 | 未定 | 待定 | [A1.json](../../decisions/decision-pages/answers/a1.json) `A1~A1-02` |
| 20 | 现金保证金 | 未定 | 待定 | [NM2.json](../../decisions/decision-pages/answers/nm2.json) `NM2~NM2-11` |

这些例子均检查过正式词条首选名、同 bc 的明确英文及别名；没有因为长词中含“线索”等字样就认为该词已登记。无英文/上下文的指称不能靠翻译补齐，也不据“kind 未定”把已定中文退回问用户。

### 静态证据能说明的漏登路径

1. **“已定沿用”落在写回范围之外。** `decisions-nm.yaml/not_registered` 明列证据、证据关联记录、劳动合同、线索、协议价格等“已定沿用、不再出题；本卡无 NM2–NM4 答复，不扩入本批”。N5 的“不重问”没有自动补成 N9 的登记操作；`apply_nm.py` 只应用明确列出的 term 操作。能证实这条静态遗漏路径，不能据此归责个人。
2. **已提交的新名没有进入转正快照。** GL B2-B02/B06/B07 等已要求新事件名，B2-B09 已明确五个事件英文改名；当前 `promoted/GL-2026-10-16.yaml` 和 `rulings.yaml` 未把这些要求完整写成新词/改名。前者产生漏词，后者产生现有词条不一致。不是“答复未收到”。
3. **为防重复而暂留 proposed。** 分包变更的 N9 记录明确写：卡外 proposed 尚在，转正要同批删除旧提议，否则重复 id/中英文名。本卡只做对账，继续保留这项批准与后续处理边界。
4. **只写了定义，缺指称/英文设计。** 现金保证金、职位、三种法定申报，以及采购变更、租赁变更等在 N9 有明确不单列原因：没有独立英文、snake 或上下文设计。相关业务含义和中文已定；需要补足指称及概念关系，不等于必须拆实体。
5. **规则页产生了未纳入用语写回的名称。** FD 明确“另设含税收入”“另设新签净额”、分别定义管理账龄/减值账龄、列迁入事实差异说明等；F1-05 明确要求登记区域。本次从已提交 label/note 抽取，不因 FD 题面文件缺失而忽略这些明确指令；其余不确定内容保留待人工。
6. **后续裁定取代与已转正的旧提议并非新漏登。** 合同/售前 BC 合并、预算 BC 改挂、GL 后的 NM 改名按后续证据对账。CAPINFO 的假期余额账户、人员薪酬档案已登记，旧 `leave_balance`、`employee_compensation` 仍在 proposed，应复用目标并另卡清理；不能再新增同义条目。

以上是当前文件的静态写回路径与明列原因；没有检查远端 PR 或在线数据库，不把“缺一段处理”进一步推断成某次提交、某个人造成。每项的来源及 `writeback_evidence` 均在 JSON。

### 全部 60 个缺口

| 中文 | 拟复用/候选 id | 原因 |
| --- | --- | --- |
| 线索 | lead | N9 明列不写回：已定沿用，不再出题；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| 劳动合同 | employment_contract | N9 明列不写回：已定沿用，不再出题；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| 证据 | acceptance_evidence | N9 明列不写回：已定沿用，不再出题；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| 证据关联记录 | evidence_link_record | N9 明列不写回：已定沿用，不再出题；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| 投标 | bid | 仅停留在提议/草稿目录 |
| 往来单位 | counterparty | 仅停留在提议/草稿目录 |
| 建设工程 | construction_project | 仅停留在提议/草稿目录 |
| 协议价格 | agreement_price | N9 明列不写回：已定沿用，不再出题；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| 协议价目 | agreement_price_list | 答复未形成登记写回 |
| 分包变更 | subcontract_change | N9 明列不写回：卡外 backend/glossary/proposed/enterprise-aggregates.yaml#subcontract_change 尚在；正式转正必须同批删除提议，否则重复 id/同 bc 中英文名。裁定保留，待负责会话另卡转正。 |
| 收入确认行 | revenue_recognition_line | 仅停留在提议/草稿目录 |
| 角色已废除 | role_abolished | GL 已提交；promoted/GL 快照与 rulings 尚缺对应新词写回（静态核对） |
| 任务已超时 | approval_task_became_overdue | GL 已提交；promoted/GL 快照与 rulings 尚缺对应新词写回（静态核对） |
| 工时单已撤回审批 | timesheet_approval_withdrawn | GL 已提交；promoted/GL 快照与 rulings 尚缺对应新词写回（静态核对） |
| 来源对照登记已被拒绝 | source_reference_refused | GL 已提交；promoted/GL 快照与 rulings 尚缺对应新词写回（静态核对） |
| 签约前投入 | precontract_investment | N9 明列不写回：已定沿用，不再出题；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| 进项发票 | input_invoice | N9 明列不写回：沿用已登记中文名，不上页；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| 经营产值 | 指称/上下文待设计 | 答复未形成登记写回 |
| 财务负责人 | 指称/上下文待设计 | 答复未形成登记写回 |
| 现金保证金 | 指称/上下文待设计 | N9 明列不写回：单独列示已写入 bond_and_deposit / receivable；草稿没有独立英文与 snake，不增设单独词条。 |
| 个人所得税申报 | 指称/上下文待设计 | N9 明列不写回：分别显示已写入 payroll_statutory_filing_record；三种独立英文、snake 不在草稿，不擅自拆实体。 |
| 住房公积金申报 | 指称/上下文待设计 | N9 明列不写回：分别显示已写入 payroll_statutory_filing_record；三种独立英文、snake 不在草稿，不擅自拆实体。 |
| 余额调节表 | 指称/上下文待设计 | 答复未形成登记写回 |
| 净收入 | 指称/上下文待设计 | 答复未形成登记写回 |
| 减值计划 | 指称/上下文待设计 | 答复未形成登记写回 |
| 减值账龄 | 指称/上下文待设计 | 答复未形成登记写回 |
| 区域 | 指称/上下文待设计 | 答复未形成登记写回 |
| 受限 | 指称/上下文待设计 | 答复未形成登记写回 |
| 可随时支用 | 指称/上下文待设计 | 答复未形成登记写回 |
| 含税收入 | 指称/上下文待设计 | 答复未形成登记写回 |
| 实际成本费率 | 指称/上下文待设计 | 答复未形成登记写回 |
| 对以前期间的更正 | 指称/上下文待设计 | 答复未形成登记写回 |
| 已计提 | 指称/上下文待设计 | 答复未形成登记写回 |
| 度量事件 | 指称/上下文待设计 | 答复未形成登记写回 |
| 扩初设计 | 指称/上下文待设计 | 答复未形成登记写回 |
| 新签净额 | 指称/上下文待设计 | 答复未形成登记写回 |
| 权责账簿 | 指称/上下文待设计 | 答复未形成登记写回 |
| 标准成本费率 | 指称/上下文待设计 | 答复未形成登记写回 |
| 社会保险申报 | 指称/上下文待设计 | N9 明列不写回：分别显示已写入 payroll_statutory_filing_record；三种独立英文、snake 不在草稿，不擅自拆实体。 |
| 租赁变更 | 指称/上下文待设计 | N9 明列不写回：按协议类型分别称呼已定；草稿仅有 SubcontractChange，另外两种缺独立英文、snake 与上下文设计，不硬造条目。 |
| 第 13 期 | 指称/上下文待设计 | 答复未形成登记写回 |
| 管理账龄 | 指称/上下文待设计 | 答复未形成登记写回 |
| 职位 | 指称/上下文待设计 | N9 明列不写回：三种含义分开已写入目录与既有岗位/职级定义；草稿没有职位的独立英文和 snake。 |
| 计费费率 | 指称/上下文待设计 | 答复未形成登记写回 |
| 费用所属期间 | 指称/上下文待设计 | 答复未形成登记写回 |
| 迁入事实差异说明 | 指称/上下文待设计 | 答复未形成登记写回 |
| 采购变更 | 指称/上下文待设计 | N9 明列不写回：按协议类型分别称呼已定；草稿仅有 SubcontractChange，另外两种缺独立英文、snake 与上下文设计，不硬造条目。 |
| 兑付状态 | bill_redemption_status | 仅停留在提议/草稿目录 |
| 已逾期 | bill_redemption_status__overdue | 仅停留在提议/草稿目录 |
| 收到 | bill_redemption_status__received | 仅停留在提议/草稿目录 |
| 已兑付 | bill_redemption_status__redeemed | 仅停留在提议/草稿目录 |
| 继续教育学时 | continuing_education_hours | 仅停留在提议/草稿目录 |
| 视为同意 | deemed_acceptance | 仅停留在提议/草稿目录 |
| 施工图审查合格书 | drawing_review_certificate | 仅停留在提议/草稿目录 |
| 施工图审查意见 | drawing_review_opinion | 仅停留在提议/草稿目录 |
| 诉讼 | litigation | 仅停留在提议/草稿目录 |
| 迁入核对 | migration_tie_out | 仅停留在提议/草稿目录 |
| 无异议声明 | no_objection_declaration | 仅停留在提议/草稿目录 |
| 往来款项处置分类 | receivable_disposition_category | 仅停留在提议/草稿目录 |
| 工时单审批已撤销 | 指称/上下文待设计 | GL 已提交；promoted/GL 快照与 rulings 尚缺对应新词写回（静态核对） |

## 3. 已登记但不一致

| 概念 id | 当前 → 裁定 | 出处 |
| --- | --- | --- |
| contract_modification | en: ContractModification → ContractModified；zh: 合同变更（会计口径） → 合同已变更 | [GL.json](../../decisions/decision-pages/answers/gl.json) `GL~B2-B09` |
| ecl_alert | en: EclAlert → EclAlerted | [GL.json](../../decisions/decision-pages/answers/gl.json) `GL~B2-B09` |
| forced_logout | en: ForcedLogout → ForceLogout | [GL.json](../../decisions/decision-pages/answers/gl.json) `GL~B2-B10` |
| retention_money_release_due | en: RetentionMoneyReleaseDue → RetentionMoneyReleaseDueReached | [GL.json](../../decisions/decision-pages/answers/gl.json) `GL~B2-B09` |
| revenue_reduction | en: RevenueReduction → RevenueReduced | [GL.json](../../decisions/decision-pages/answers/gl.json) `GL~B2-B09` |
| rule_execution | en: RuleExecution → RuleExecuted | [GL.json](../../decisions/decision-pages/answers/gl.json) `GL~B2-B09` |
| standard_cost_rate_card | zh: 标准费率 → 标准成本费率表 | [NM3.json](../../decisions/decision-pages/answers/nm3.json) `NM3~NM3-08` |

标准成本费率表还涉及 `cost.costing` 与 `masterdata.catalog` 的复用关系，N9 已明确未定；这里只报告首选中文未写回，不擅自把 BC 搬走。别名/禁用比较有源快照时逐项检查；历史泛称、common_word 和合并后的多目标别名不能靠全局字符串包含判断，相关 `alias_writeback=待核对` 保留，未冒称所有禁用语义已验证。

## 4. proposed 逐条分类

330 条按文件＋id 一一对应原文，无丢项或重复分类：174 条非错误码中，132 条仍是 proposed、42 条是 deprecated。A 66 条里 **22 条是补登记候选，44 条是复用已有正式目标/清理旧兼容条目**。A 不代表旧 id 全部可原样转正；例如 RoleDeleted 的目标是 RoleAbolished。B 105 条给出设计建议；C 3 条才出名称/业务边界问题。

A 的 en/kind/bc 若未被用户逐项审定，仍只属设计定稿建议；中文认可不批准聚合边界。B 依 ADR-0053 决定 3 的已定名词/动作组合、决定 4 的结构/代码面、决定 7.1 的初裁及决定 8 的细则权限；技术词另依。N5 明交设计方的条目优先采用该具体边界。B 不用来批准新业务行为、财务口径或权限。

### A：已有裁定，可补登记或复用目标

| 原文件#id | 定稿/目标建议 | kind | bc | 依据与处理 |
| --- | --- | --- | --- | --- |
| code-aggregates-events.yaml#timesheet_approval_withdrawn | 工时单已撤回审批 / TimesheetApprovalWithdrawn | domain_event | cost.timesheet | [GL.json](../../decisions/decision-pages/answers/gl.json) `GL~B2-B07`；补登记；英文/kind/bc 是设计定稿建议 |
| code-aggregates-events.yaml#approval_task_became_overdue | 任务已超时 / ApprovalTaskOverdue | domain_event | platform.approval | [GL.json](../../decisions/decision-pages/answers/gl.json) `GL~B2-B02`；补登记；英文/kind/bc 是设计定稿建议 |
| code-aggregates-events.yaml#role_deleted | 角色已废除 / RoleAbolished | domain_event | platform.identity | [GL.json](../../decisions/decision-pages/answers/gl.json) `GL~B2-B06`；补登记；英文/kind/bc 是设计定稿建议 |
| code-aggregates-events.yaml#source_reference_refused | 来源对照登记已被拒绝 / SourceReferenceRegistrationRefused | domain_event | platform.integration | [GL.json](../../decisions/decision-pages/answers/gl.json) `GL~B2-B05`；补登记；英文/kind/bc 是设计定稿建议 |
| data-migration.yaml#migration_tie_out | 迁入核对 / MigrationTieOut | entity | platform.integration | [decisions-gate-R-1b-0.json](../decisions-gate-r-1b-0.json) `GATE-R-1b-0-G03`；补登记；英文/kind/bc 是设计定稿建议 |
| enterprise-aggregates.yaml#counterparty | 往来单位 / Counterparty | aggregate | masterdata.counterparty | `docs/decisions/2026-10-15-210513-one-architecture-售前聚合与-BC-确认页保存-一.md` ；补登记；英文/kind/bc 是设计定稿建议 |
| enterprise-aggregates.yaml#construction_project | 建设工程 / ConstructionProject | aggregate | masterdata.counterparty | `docs/decisions/2026-10-15-234418-one-architecture-答复复核-R1–R5-提交-已提.md` ；补登记；英文/kind/bc 是设计定稿建议 |
| enterprise-aggregates.yaml#revenue_recognition_line | 收入确认行 / RevenueRecognitionLine | entity | receivables.wip | [GL2.json](../../decisions/decision-pages/answers/gl2.json) `GL2~B2-X06`；补登记；英文/kind/bc 是设计定稿建议 |
| enterprise-aggregates.yaml#input_invoice | 进项发票 / InputInvoice | aggregate | accounting.tax | `docs/decisions/2026-10-16-072440-one-architecture-对象完整性与方法第-4-稿确认页.md` ；补登记；英文/kind/bc 是设计定稿建议 |
| enterprise-aggregates.yaml#subcontract_change | 分包变更 / SubcontractChange | aggregate | procurement.subcontracting | [NM3.json](../../decisions/decision-pages/answers/nm3.json) `NM3~NM3-11`；补登记；英文/kind/bc 是设计定稿建议 |
| enterprise-aggregates.yaml#leave_balance | 假期余额账户 / LeaveBalanceAccount | aggregate | organization.leave | [CAPINFO_NEWTERMS.md](capinfo-newterms.md) `OBJ-122`；复用已登记目标，旧兼容条目按改名卡撤除 |
| enterprise-aggregates.yaml#employee_compensation | 人员薪酬档案 / EmployeeCompensationProfile | aggregate | organization.payroll | [CAPINFO_NEWTERMS.md](capinfo-newterms.md) `OBJ-123`；复用已登记目标，旧兼容条目按改名卡撤除 |
| enterprise-aggregates.yaml#no_objection_declaration | 无异议声明 / NoObjectionDeclaration | entity | delivery.acceptance | `docs/decisions/2026-10-16-061232-one-architecture-阶段类型与-BC-划分确认页提交.md` ；补登记；英文/kind/bc 是设计定稿建议 |
| enterprise-aggregates.yaml#deemed_acceptance | 视为同意 / DeemedAcceptance | business_rule | receivables.wip | `docs/decisions/2026-10-16-061232-one-architecture-阶段类型与-BC-划分确认页提交.md` ；补登记；英文/kind/bc 是设计定稿建议 |
| enterprise-aggregates.yaml#bid | 投标 / Bid | aggregate | presales.opportunity | `docs/decisions/2026-10-15-202939-one-architecture-售前业务对象问题页保存-线索单独.md` ；补登记；英文/kind/bc 是设计定稿建议 |
| platform-numbering.yaml#qt | 单据类型代码 / DocumentTypeCode | value_object | platform.numbering | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-NU-18`；复用已登记目标，旧兼容条目按改名卡撤除 |
| platform-numbering.yaml#ts | 单据类型代码 / DocumentTypeCode | value_object | platform.numbering | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-NU-18`；复用已登记目标，旧兼容条目按改名卡撤除 |
| platform-numbering.yaml#jv | 单据类型代码 / DocumentTypeCode | value_object | platform.numbering | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-NU-18`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__act | 办理 / Handle | command | platform.approval | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-AP-04`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__assigned | 任务已分配 / TaskAssigned | domain_event | platform.approval | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-AP-05`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__completed | 审批已完成 / ApprovalCompleted | domain_event | platform.approval | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-AP-07`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__escalated | 已上推 / ReroutedToSuperior | value_object | platform.approval | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-AP-10`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__fallback | 转流程管理员 / AdministratorFallback | value_object | platform.approval | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-AP-11`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__form | 审批表单字段 / ApprovalField | entity | platform.approval | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-AP-12`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__round | 审批轮次 / ApprovalRound | entity | platform.approval | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-AP-15`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__row | 行级任务 / LineTask | entity | platform.approval | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-AP-16`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__deadline | 审批时限 / ApprovalTimeLimit | value_object | platform.approval | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-AP-21`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__effect | 办理效力 / TaskEffect | value_object | platform.approval | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-AP-22`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__release_of_task | 取消认领 / Unclaim | command | platform.approval | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-AP-25`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__ui_business_summary | 审批摘要 / ApprovalSummary | value_object | platform.approval | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-AP-41`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__ui_proxy_handling | 代理办理 / ProxyHandling | role | platform.approval | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-AP-42`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__task_participant | 任务参与人 / TaskParticipant | role | platform.approval | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-AP-53`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__decision | 审批决定 / TaskDecision | entity | platform.approval | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-AP-57`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__resulted | 任务已办理 / TaskHandled | domain_event | platform.approval | [GL.json](../../decisions/decision-pages/answers/gl.json) `GL~B2-B03`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__skipped | 通知已跳过 / NotificationSkipped | state | platform.approval | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-AP-65`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__project_manager_review | 项目经理审批 / ProjectManagerApproval | value_object | platform.approval | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-AP-68`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__regional_head_review | 部门负责人审批 / OrgUnitHeadApproval | value_object | platform.approval | [D5.json](../../decisions/decision-pages/answers/d5.json) `D5~D5-06`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__supervisor_review | 直属上级审批 / SupervisorApproval | value_object | platform.approval | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-AP-70`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__cutoff | 撤回截止点 / WithdrawalCutoff | value_object | platform.approval | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-AP-74`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__administrator_mode | 管理员模式 / AdministratorMode | business_rule | platform.identity | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-ID-04`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__authorization | 授予权限 / GrantPermission | command | platform.identity | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-ID-10`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__account_disabled | 账号停用 / AccountDisabled | state | platform.identity | [GL.json](../../decisions/decision-pages/answers/gl.json) `GL~B2-B01`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__permission_adjustment_kind__sensitive_role_holder | 敏感角色复核 / SensitiveRoleReview | value_object | platform.identity | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-ID-22`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__size | 字节数 / ByteCount | value_object | platform.integration | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-IN-07`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__keep | 数据保留期限 / DataRetentionPeriod | command | platform.integration | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-IN-08`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__worm | 一次写入多次读取 / WriteOnceReadMany | business_rule | platform.integration | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-IN-36`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__voucher_numbering_rule | 记账凭证编号规则 / JournalVoucherNumberingRule | business_rule | platform.numbering | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-NU-08`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__rule_version | 编号规则版本 / NumberingRuleVersion | value_object | platform.numbering | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-NU-09`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__overhead_hours | 间接工时 / IndirectHours | value_object | cost.timesheet | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-TS-04`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__workforce__email | 企业邮箱 / EmailAddress | value_object | organization.workforce | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-WF-06`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__department | 组织单元 / OrgUnit | entity | organization.workforce | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-AP-09`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__ui_task_handler | 操作人 / Actor | role | platform.audit | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-AP-32`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__running | 审批中 / ApprovalRunning | state | platform.approval | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-AP-61`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__ts | 单据类型代码 / DocumentTypeCode | value_object | platform.numbering | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-NU-18`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__qt | 单据类型代码 / DocumentTypeCode | value_object | platform.numbering | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-NU-18`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__jv | 单据类型代码 / DocumentTypeCode | value_object | platform.numbering | [decisions-round3-2026-10-15.json](../decisions-round3-2026-10-15.json) `R3-NU-18`；复用已登记目标，旧兼容条目按改名卡撤除 |
| round3-pending-renames.yaml#round3_pending__journal_voucher | 记账凭证 / JournalVoucher | aggregate | accounting.ledger | [decisions-nm.yaml](../decisions-nm.yaml) `NM2~NM2-10`；复用已登记目标，旧兼容条目按改名卡撤除 |
| step3-objects.yaml#bill_redemption_status | 兑付状态 / BillRedemptionStatus | state | receivables.collection | `docs/decisions/2026-10-16-090004-one-architecture-阶段写实-对象回补与-ADR-0.md` ；补登记；英文/kind/bc 是设计定稿建议 |
| step3-objects.yaml#bill_redemption_status__received | 收到 / Received | value_object | receivables.collection | `docs/decisions/2026-10-16-090004-one-architecture-阶段写实-对象回补与-ADR-0.md` ；补登记；英文/kind/bc 是设计定稿建议 |
| step3-objects.yaml#bill_redemption_status__redeemed | 已兑付 / Redeemed | value_object | receivables.collection | `docs/decisions/2026-10-16-090004-one-architecture-阶段写实-对象回补与-ADR-0.md` ；补登记；英文/kind/bc 是设计定稿建议 |
| step3-objects.yaml#bill_redemption_status__overdue | 已逾期 / BillOverdue | value_object | receivables.collection | `docs/decisions/2026-10-16-090004-one-architecture-阶段写实-对象回补与-ADR-0.md` ；补登记；英文/kind/bc 是设计定稿建议 |
| step3-objects.yaml#receivable_disposition_category | 往来款项处置分类 / ReceivableDispositionCategory | state | receivables.collection | `docs/decisions/2026-10-16-090004-one-architecture-阶段写实-对象回补与-ADR-0.md` ；补登记；英文/kind/bc 是设计定稿建议 |
| step3-objects.yaml#litigation | 诉讼 / Litigation | value_object | receivables.collection | `docs/decisions/2026-10-16-090004-one-architecture-阶段写实-对象回补与-ADR-0.md` ；补登记；英文/kind/bc 是设计定稿建议 |
| step3-objects.yaml#continuing_education_hours | 继续教育学时 / ContinuingEducationHours | value_object | organization.workforce | `docs/decisions/2026-10-16-090004-one-architecture-阶段写实-对象回补与-ADR-0.md` ；补登记；英文/kind/bc 是设计定稿建议 |
| step3-objects.yaml#drawing_review_opinion | 施工图审查意见 / DrawingReviewOpinion | value_object | delivery.acceptance | `docs/decisions/2026-10-16-090004-one-architecture-阶段写实-对象回补与-ADR-0.md` ；补登记；英文/kind/bc 是设计定稿建议 |
| step3-objects.yaml#drawing_review_certificate | 施工图审查合格书 / DrawingReviewCertificate | value_object | delivery.acceptance | `docs/decisions/2026-10-16-090004-one-architecture-阶段写实-对象回补与-ADR-0.md` ；补登记；英文/kind/bc 是设计定稿建议 |

### B：设计方可定稿的技术、结构与组合名称

| 原文件#id | 定稿/目标建议 | kind | bc | 依据与处理 |
| --- | --- | --- | --- | --- |
| architecture-acl.yaml#org_change_set | 组织变更集 / OrgChangeSet | value_object | organization.workforce | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| architecture-acl.yaml#apply_org_change_set | 应用组织变更 / ApplyOrgChangeSet | command | organization.workforce | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| architecture-acl.yaml#separation_hold | 待放行离职 / SeparationHold | aggregate | organization.workforce | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| data-classification.yaml#personal_information | 个人信息 / PersonalInformation | value_object | platform.audit | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| data-classification.yaml#sensitive_personal_information | 敏感个人信息 / SensitivePersonalInformation | value_object | platform.audit | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| data-migration.yaml#migration_batch | 迁入批次 / MigrationBatch | aggregate | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| data-migration.yaml#migration_reject | 迁入拒收 / MigrationReject | entity | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| data-migration.yaml#migration_reject_reason__unmappable_value | 取值无法映射 / UnmappableValue | value_object | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| data-migration.yaml#migration_reject_reason__required_missing | 必填缺失 / RequiredMissing | value_object | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| data-migration.yaml#migration_reject_reason__number_conflict | 编号冲突 / NumberConflict | value_object | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| data-migration.yaml#migration_reject_reason__original_missing | 原行缺失 / OriginalMissing | value_object | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| data-migration.yaml#migration_reject_reason__legal_entity_out_of_scope | 法人不在范围 / LegalEntityOutOfScope | value_object | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| data-migration.yaml#migration_reject_reason__trial_commit_failed | 试提交失败 / TrialCommitFailed | value_object | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| data-migration.yaml#migration_reject_disposition__rerun_after_fix | 修正后重跑 / RerunAfterFix | value_object | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| data-migration.yaml#migration_reject_disposition__excluded | 确认不迁 / Excluded | value_object | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| data-migration.yaml#migration_period_reservation | 迁入占用 / MigrationPeriodReservation | entity | accounting.period | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| data-migration.yaml#migration_marker | 迁入标记 / MigrationMarker | value_object | operations.facts | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| data-migration.yaml#migration_posting_channel | 迁入过账通道 / MigrationPostingChannel | entity | operations.facts | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| data-migration.yaml#migration_staging | 迁入暂存 / MigrationStaging | entity | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| data-migration.yaml#trial_commit | 试提交 / TrialCommit | command | platform.migration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| data-migration.yaml#migration_batch_status__staging | 暂存中 / Staging | value_object | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| data-migration.yaml#migration_batch_status__awaiting_tie_out | 待核对 / AwaitingTieOut | value_object | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| data-migration.yaml#migration_batch_status__committing | 提交中 / Committing | value_object | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| data-migration.yaml#migration_batch_status__effective | 已生效 / Effective | value_object | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| data-migration.yaml#migration_batch_status__reversed | 已冲销 / Reversed | value_object | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| data-migration.yaml#migration_batch_status__abandoned | 已放弃 / Abandoned | value_object | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| data-migration.yaml#source_reference_status | 对照状态 / SourceReferenceStatus | value_object | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| data-migration.yaml#source_reference_status__current | 当前 / Current | value_object | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| data-migration.yaml#source_row_digest | 来源行摘要 / SourceRowDigest | value_object | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| enterprise-aggregates.yaml#own_participation_record | 我方参与记录 / OwnParticipationRecord | entity | masterdata.counterparty | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| enterprise-aggregates.yaml#project_wbs | 项目 WBS / ProjectWbs | aggregate | delivery.execution | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| enterprise-aggregates.yaml#billing_revenue_limit | 开票与收入额度 / BillingRevenueLimit | aggregate | receivables.wip | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| enterprise-aggregates.yaml#acceptance_evidence_type_admission | 客户认可证据类型开放配置 / AcceptanceEvidenceTypeAdmission | aggregate | receivables.wip | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| enterprise-aggregates.yaml#invoice_posting_reservation | 发票入账占用 / InvoicePostingReservation | entity | accounting.tax | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| enterprise-aggregates.yaml#receivables_collection__cash_application_reservation | 核销占用 / CashApplicationReservation | entity | receivables.collection | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| enterprise-aggregates.yaml#payables_payment__cash_application_reservation | 核销占用 / CashApplicationReservation | entity | payables.payment | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| enterprise-aggregates.yaml#supplier_agreement_performance_limit | 供方协议履约额度 / SupplierAgreementPerformanceLimit | aggregate | procurement.subcontracting | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| enterprise-aggregates.yaml#subcontract_change_activated | 分包变更已生效 / SubcontractChangeBecameEffective | domain_event | procurement.subcontracting | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为；生效事件按 N4 §4.2 用 BecameEffective，不沿用启用义 Activated |
| enterprise-aggregates.yaml#labor_cost_allocation_result | 人工成本分配结果 / LaborCostAllocationResult | aggregate | cost.costing | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| enterprise-aggregates.yaml#cash_accrual_account_mapping | 收付权责科目映射表 / CashAccrualAccountMapping | aggregate | accounting.ledger | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| enterprise-aggregates.yaml#control_account_authorization | 控制科目授权表 / ControlAccountAuthorization | aggregate | accounting.ledger | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| enterprise-aggregates.yaml#no_objection_declared | 无异议已声明 / NoObjectionDeclared | domain_event | delivery.acceptance | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| enterprise-aggregates.yaml#design_change_claim_status | 设计变更主张状态 / DesignChangeClaimStatus | state | delivery.acceptance | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| enterprise-aggregates.yaml#acceptance_date | 认可日 / AcceptanceDate | value_object | delivery.acceptance | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| enterprise-aggregates.yaml#review_period | 审查期 / ReviewPeriod | value_object | delivery.acceptance | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| enterprise-aggregates.yaml#expiry_record | 期满记录 / ExpiryRecord | entity | delivery.acceptance | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| enterprise-aggregates.yaml#acceptance_evidence_type | 客户认可证据类型 / AcceptanceEvidenceType | state | delivery.acceptance | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| enterprise-aggregates.yaml#counterparty_bank_account_changed | 往来单位银行账户已变更 / CounterpartyBankAccountChanged | domain_event | masterdata.counterparty | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| enterprise-aggregates.yaml#bid_won | 投标已中标 / BidWon | domain_event | presales.opportunity | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| enterprise-aggregates.yaml#bid_lost | 投标未中标 / BidLost | domain_event | presales.opportunity | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| enterprise-aggregates.yaml#opportunity_lost | 商机已输单 / OpportunityLost | domain_event | presales.opportunity | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| enterprise-aggregates.yaml#opportunity_abandoned | 商机已放弃 / OpportunityAbandoned | domain_event | presales.opportunity | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| enterprise-aggregates.yaml#agreement_terminated | 协议已终止 / AgreementTerminated | domain_event | contract.agreement | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| enterprise-aggregates.yaml#agreement_baseline_activated | 协议新基线已生效 / AgreementBaselineBecameEffective | domain_event | contract.agreement | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为；生效事件按 N4 §4.2 用 BecameEffective，不沿用启用义 Activated |
| enterprise-aggregates.yaml#agreement_project_linked | 协议项目关联已登记 / AgreementProjectLinked | domain_event | contract.agreement | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| enterprise-aggregates.yaml#project_established | 项目已建立 / ProjectEstablished | domain_event | delivery.execution | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| enterprise-aggregates.yaml#project_closed | 项目已结项 / ProjectClosed | domain_event | delivery.execution | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| enterprise-aggregates.yaml#project_terminated | 项目已终止 / ProjectTerminated | domain_event | delivery.execution | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| enterprise-aggregates.yaml#project_cancelled | 项目已取消 / ProjectCancelled | domain_event | delivery.execution | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| fix-b2b-events.yaml#snapshot_date | 快照日 / SnapshotDate | value_object | organization.workforce | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| fix-c.yaml#ui_released_state | 已放行 / ReleasedState | state | organization.workforce | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| fix-c.yaml#ui_external_system | 外部系统 / ExternalSystem | entity | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| frontend-ui.yaml#trigger_mode | 触发方式 / TriggerMode | value_object | organization.workforce | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| frontend-ui.yaml#held | 被中止 / Held | value_object | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| frontend-ui.yaml#release | 放行 / Release | command | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| frontend-ui.yaml#org_sync | 同步 / OrgSync | aggregate | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| kernel.yaml#system_actor | 系统操作主体 / SystemActor | role | kernel | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| kernel.yaml#messaging_account | 即时通讯账号 / MessagingAccount | value_object | kernel | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| kernel.yaml#external_login_name | 外部登录名 / ExternalLoginName | value_object | kernel | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| organization_workforce.yaml#employment_verdict | 在职判定 / EmploymentVerdict | value_object | organization.workforce | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| organization_workforce.yaml#login_lookup | 登录查人 / LoginLookup | value_object | organization.workforce | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| organization_workforce.yaml#workforce__sync_diff | 同步差异 / SyncDiff | entity | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| organization_workforce.yaml#workforce__shrink_guard | 缩水保护 / ShrinkGuard | business_rule | organization.workforce | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| organization_workforce.yaml#workforce__held_run | 被中止的同步 / HeldRun | value_object | organization.workforce | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| organization_workforce.yaml#workforce__isolated_row | 被隔离的行 / IsolatedRow | value_object | organization.workforce | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| organization_workforce.yaml#workforce__broken_org_tree | 断裂的组织树 / BrokenOrgTree | value_object | organization.workforce | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| organization_workforce.yaml#workforce__login_name | 登录名 / LoginName | value_object | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| organization_workforce.yaml#workforce__staff_employee | 员工任职快照 / StaffEmployee | value_object | organization.workforce | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| organization_workforce.yaml#workforce__relationship | 关系 / Relationship | value_object | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| organization_workforce.yaml#separation_observed_at | 离职观察时间 / SeparationObservedAt | value_object | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| organization_workforce.yaml#attribution_subject | 法人归属对象 / AttributionSubject | value_object | organization.workforce | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| platform-identity.yaml#signing_key_code | 签名密钥编码 / SigningKeyCode | value_object | platform.identity | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| platform-identity.yaml#ticket | 票据 / Ticket | entity | platform.identity | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| platform-identity.yaml#ticket_issuer | 票据签发方 / TicketIssuer | aggregate | platform.identity | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| platform-identity.yaml#external_identity | 外部身份 / ExternalIdentity | entity | platform.identity | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| platform-identity.yaml#provider_kind | 身份提供方类别 / ProviderKind | state | platform.identity | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| platform-identity.yaml#external_subject | 外部主体标识 / ExternalSubject | value_object | platform.identity | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| platform-identity.yaml#single_operator_mode | 单人运维模式 / SingleOperatorMode | value_object | platform.identity | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| platform-identity.yaml#single_operator_relaxation | 单人运维放宽项 / SingleOperatorRelaxation | value_object | platform.identity | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| platform-identity.yaml#super_admin | 超级管理员 / SuperAdmin | value_object | platform.identity | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| platform-integration.yaml#outbound_delivery | 外发投递 / OutboundDelivery | aggregate | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| platform-integration.yaml#recipient | 收件人 / Recipient | role | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| platform-integration.yaml#source_reference | 来源对照 / SourceReference | aggregate | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| platform-integration.yaml#source_system | 来源系统 / SourceSystem | state | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| platform-integration.yaml#source_object_kind | 来源对象类别 / SourceObjectKind | state | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| platform-integration.yaml#rejected_state | 已拒绝 / RejectedState | state | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| platform-integration.yaml#registration | 登记 / Registration | command | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| platform-integration.yaml#supersede | 取代 / Supersede | command | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| platform-integration.yaml#evidence | 取证 / Evidence | value_object | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| platform-integration.yaml#read_only_verification | 只读核实 / ReadOnlyVerification | command | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| platform-integration.yaml#read_only_verification_result | 只读核实结果 / ReadOnlyVerificationResult | state | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| platform-integration.yaml#read_only_verification_result__passed | 核实通过 / Passed | value_object | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| platform-integration.yaml#read_only_verification_result__violated | 确认违规 / Violated | value_object | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| platform-integration.yaml#read_only_verification_result__undetermined | 判定不了 / Undetermined | value_object | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |
| platform-integration.yaml#overridden_grant | 被拒绝覆盖的授权 / OverriddenGrant | value_object | platform.integration | `.codex-inputs/ADR-0053.md` 决定 3（已定对象+动作的组合、槽位与模板属细则）；4（结构、代码面）；7.1（one-glossary 起草初裁）；8（细则不需 ADR）。设计对象另见 N5 review.route；不以本条批准新业务含义。；按已定名词/动词组合或结构职责定稿；此处为设计建议，未批准新业务行为 |

### C：真正需要补定的名称/边界

| 原文件#id | 白话问题 | 本地证据边界 |
| --- | --- | --- |
| enterprise-aggregates.yaml#tender_project | 外部公开招标的信息是否统一叫“招标项目”，并与本公司的商机分开保留？ | 未找到本卡本地证据能批准此名称及业务含义；法规/ADR 引用或草稿本身不等于用户认可 |
| enterprise-aggregates.yaml#output_invoice | 我方向客户开出的发票，正式名称用“销项发票”是否认可？ | 未找到本卡本地证据能批准此名称及业务含义；法规/ADR 引用或草稿本身不等于用户认可 |
| enterprise-aggregates.yaml#supplier_agreement | 分包、采购、租赁等供方一侧的协议，是否统一称“供方协议”？ | 未找到本卡本地证据能批准此名称及业务含义；法规/ADR 引用或草稿本身不等于用户认可 |

### error_code：156 条单列归 R-5

| 提议文件 | 条数 |
| --- | --- |
| cost-timesheet.yaml | 13 |
| fix-b.yaml | 47 |
| fix-b1-identity.yaml | 28 |
| fix-b2-data.yaml | 1 |
| organization_attendance.yaml | 8 |
| organization_workforce.yaml | 10 |
| platform-approval.yaml | 29 |
| platform-identity.yaml | 3 |
| platform-integration.yaml | 7 |
| platform-numbering.yaml | 5 |
| platform-web.yaml | 5 |

明细 `error_codes` 保留每条 id、原文及 owner=R-5；本卡没有进行 A/B/C 分类、同义归并、通用错误码迁移或错误码守护验收。

## 5. 不确定项与后续处理边界

- NM1 182 处（157 行整表＋25 处单项语义）和 NM2 8 个动作片段：中文已有提交认可，当前正式结构没有独立 verb 层。不能拿某条命令与动词同字就视为受控动词已登记，也不能把无对象的动作伪造成 DDD 命令。NM2-20 的本批免转财务已保留，不重新请示。
- ACL 59 处：包括只有使用范围裁定、没有名称转正证据的 56 处，以及组织源生命周期问题和 G58/G59 两项暂缓。它们保留“归类”这一裁定范围，没有被批量算成漏登。
- CAPINFO 21 个能力/价值流/备名是交接稿的推导，原始题面及答复未在本地找到；提交编号不能替代原始选项。
- 第三轮 11 处只是“保持 YAML 定义”，并非批准从 proposed 转正式；另有 GL2 的“认可/验收”等概念与指称结构、架构模板名、FD 中含禁用“工抵”的复合展示名等列待人工。
- 缺口清单中的无英文名称（例如三种申报、财务负责人、管理账龄）需要设计方补代码名和归属；应先查是否复用既有概念，只补指称或属性取值，不先造独立实体。
- 61 道未机械展开的规则答复完整存档；尤其 FD 原始题面缺失，不能从“我直接答”推回问题。报告中的已裁定总数是本地能够明确抽取部分，不冒称所有语义已穷尽。

## 6. 验证

### 已验证

- 两个全新 Python 进程运行脚本，`cmp` 逐字节相等。输出 SHA-256：`4e354eaaffa3ebd668018127dc59c643ed15934a7996fb388ce40f8ea7aeb1dd`。
- 独立核对 1,072 条提交的完整键值、410 条第三轮提交的去向、330 条 proposed 原文及 174/156 分流、775 个源文件 SHA-256、裁定日期与记录编号唯一性，均通过。
- `--out` 指向 Git 工作区内时返回 2、没有创建输出文件；默认 stdout。脚本仅标准库＋已有 PyYAML 6.0.3，400 行；AST 可解析，没有执行生成器、exec 或 eval。
- 人工逐项回读以下 30 个已裁定样本。核对的是报告是否忠实反映裁定与当前登记；样本中的漏登/不一致是发现，不是把正式词表验成通过。第 13 项修复了跨概念同名误匹配。

| 序 | 来源题号/intent | 样本 | 实况分类 | 人工核对 |
| --- | --- | --- | --- | --- |
| 1 | B-project | 项目 | 已登记且一致 | 已核对原答/原话与词表；范围限制如上 |
| 2 | A-contract_clause-3 | 开票计划 / 开票计划节点 | 应不登记 | 已核对原答/原话与词表；范围限制如上 |
| 3 | C-contract_type | 合同形态 | 已登记且一致 | 已核对原答/原话与词表；范围限制如上 |
| 4 | R2-P-declared-hours | 填报工时 | 已登记且一致 | 已核对原答/原话与词表；范围限制如上 |
| 5 | R2-A-subcontract_cost-0 | 项目委外 | 应不登记 | 已核对原答/原话与词表；范围限制如上 |
| 6 | R2-P-budget | 预算 | 应不登记 | 已核对原答/原话与词表；范围限制如上 |
| 7 | R3-AP-04 | 办理 | 已登记且一致 | 已核对原答/原话与词表；范围限制如上 |
| 8 | R3-AP-09 | 部门 | 应不登记 | 已核对原答/原话与词表；范围限制如上 |
| 9 | R3-ID-32 | 强制下线 | 已登记但不一致 | 已核对原答/原话与词表；范围限制如上 |
| 10 | R3-RN-48 | 收入冲减 | 已登记但不一致 | 已核对原答/原话与词表；范围限制如上 |
| 11 | GATE-F-1-G01 | 业务负责人 | 已登记且一致 | 已核对原答/原话与词表；范围限制如上 |
| 12 | GATE-R-1b-0-G01 | 外部读取登记 | 已登记且一致 | 已核对原答/原话与词表；范围限制如上 |
| 13 | ACL-TRANSITION-G43 | 人员 | 待人工 | 已核对原答/原话与词表；范围限制如上 |
| 14 | R3-NU-01 | 财务 | 应不登记 | 已核对原答/原话与词表；范围限制如上 |
| 15 | GL~B2-B06 | 角色已废除 | 漏登记 | 已核对原答/原话与词表；范围限制如上 |
| 16 | GL~B2-B09 | contract_modification | 已登记但不一致 | 已核对原答/原话与词表；范围限制如上 |
| 17 | GL2~B2-X04 | receivable | 已登记且一致 | 已核对原答/原话与词表；范围限制如上 |
| 18 | GL2~B2-X10 | preliminary_design | 已登记且一致 | 已核对原答/原话与词表；范围限制如上 |
| 19 | NM1~NM1-22 | 登记 | 待人工 | 已核对原答/原话与词表；范围限制如上 |
| 20 | NM2~NM2-09 | bank_statement_line | 已登记且一致 | 已核对原答/原话与词表；范围限制如上 |
| 21 | NM2~NM2-11 | 现金保证金 | 漏登记 | 已核对原答/原话与词表；范围限制如上 |
| 22 | NM3~NM3-09 | 框架协议限额 | 应不登记 | 已核对原答/原话与词表；范围限制如上 |
| 23 | NM3~NM3-11 | subcontract_change | 漏登记 | 已核对原答/原话与词表；范围限制如上 |
| 24 | NM4~NM4-08 | it_asset_usage_record | 已登记且一致 | 已核对原答/原话与词表；范围限制如上 |
| 25 | NM5~NM5-02 | allocation_base | 已登记且一致 | 已核对原答/原话与词表；范围限制如上 |
| 26 | GL2~B2-X06 | revenue_recognition | 已登记且一致 | 已核对原答/原话与词表；范围限制如上 |
| 27 | 2026-10-15-202939 | 线索 | 漏登记 | 已核对原答/原话与词表；范围限制如上 |
| 28 | 2026-10-16-114200 | 劳动合同 | 漏登记 | 已核对原答/原话与词表；范围限制如上 |
| 29 | 2026-10-16-114200 | 证据 | 漏登记 | 已核对原答/原话与词表；范围限制如上 |
| 30 | 2026-10-16-114200 | 证据关联记录 | 漏登记 | 已核对原答/原话与词表；范围限制如上 |

复跑命令（当前系统默认 Python 没有 PyYAML，使用本机已装环境）：

```sh
PYTHONDONTWRITEBYTECODE=1 /Users/yeliang/.pyenv/versions/3.12.13/bin/python3 \
  docs/glossary/coverage/reconcile_decided.py --out /tmp/reconcile-decided.json
```

### 未运行

- 本卡没有指定验收测试；未运行后端/前端业务测试、容器、数据库或真实工作流验收；不能宣称词表缺口已经修复。
- 没有运行会写卡外生成物的 build_all，没有读取在线确认页或远端提交；没有把检查草案接入守护。

### precheck.sh 输出

默认命令：`PATH=/Users/yeliang/.pyenv/versions/3.12.13/bin:$PATH PYTHONDONTWRITEBYTECODE=1 bash scripts/precheck.sh`，退出 **1**。本地 `origin/main=3ae3d466`，开工 HEAD=`47ecece9`；67 个比较项包含本卡前已有的 61 个已提交差异。未 fetch。两项 FAIL：前端依赖未安装；后端词表测试在启动前找不到 Java Runtime，测试未实际运行。只允许新增三个文件的卡不涵盖依赖安装、环境配置或卡外文件修改，因此没有为清空 FAIL 修改门禁或依赖。

```text
precheck: base origin/main (3ae3d466), HEAD 47ecece9, 67 changed files (6 uncommitted)

SKIP  generated docs (build_all --check)     docs or backend/glossary/proposed has uncommitted changes: commit first, then run again (请先提交再运行)
PASS  legacy system name                     check_legacy_name: 无违规
PASS  decision pages: question rules (self-test) check_pages self-test: ok
PASS  decision pages: question rules         check_pages: 5 张页，0 处问题
SKIP  migrations immutable                   no migration, marker or Flyway setting changed
SKIP  system actors append-only              system-actors.txt and its script did not change
SKIP  acceptance guard                       no acceptance test changed
SKIP  database scripts aligned               no database script or migration changed
SKIP  docs read by builds are registered     no backend, frontend or script changed
SKIP  assertion drift (warning only)         no test file changed
FAIL  glossary: frontend check:copy, test:scripts 前端依赖未安装：先在 frontend/ 运行 pnpm install --frozen-lockfile --offline（只用本机缓存、不下载）；缓存缺包而失败时，再问用户是否联网安装
FAIL  glossary: backend term tests           exit 1 — last lines:
      | The operation couldn’t be completed. Unable to locate a Java Runtime.
      | Please visit http://www.java.com for information on installing Java.
      | 
SKIP  guards: backend guard tests            no guard test or architecture list changed
PASS  release scripts self-test              deploy_gate.py self-test passed
PASS  shellcheck scripts/release             ok
PASS  gitleaks (full history)                2922 commits scanned; no leaks
      note: uncommitted files are not in history; the pre-commit hook scans them on commit
SKIP  backend tests                          not run by default; no module code changed (shared inputs, resources, build or architecture lists)
      run: (cd backend && ./mvnw -q test-compile surefire:test -Dtest='ArchitectureRulesTest,ModularityTest,ModuleDependencyTest,NamingSurfaceTest,GlossaryTest,DdlNamingTest,LegacyShapeTest,TimeSourceGuardTest,TestIsolationGuardTest' -Dsurefire.failIfNoSpecifiedTests=false)
      before delivery run everything: scripts/ci/backend_verify.sh (Testcontainers: ask the user before starting containers)
SKIP  frontend verify                        前端依赖未安装：先在 frontend/ 运行 pnpm install --frozen-lockfile --offline（只用本机缓存、不下载）；缓存缺包而失败时，再问用户是否联网安装（本次未改 frontend/，CI 仍会跑）
SKIP  SQL Server integration tests           relevant_change.py: nothing the *MssqlIT tests exercise changed

SUMMARY: 6 PASS, 2 FAIL, 11 SKIP (base origin/main) — not ready
```

补充命令：`PATH=/Users/yeliang/.pyenv/versions/3.12.13/bin:$PATH PYTHONDONTWRITEBYTECODE=1 bash scripts/precheck.sh HEAD`，退出 **0**。HEAD 基线只验证本卡三个新增文件和开工已存在的 `.codex-inputs/` 三个输入；后者未修改。这个结果证明本卡增量的门禁，不能替代上面的默认整分支检查。

```text
precheck: base HEAD (47ecece9), HEAD 47ecece9, 6 changed files (6 uncommitted)

SKIP  generated docs (build_all --check)     docs or backend/glossary/proposed has uncommitted changes: commit first, then run again (请先提交再运行)
PASS  legacy system name                     check_legacy_name: 无违规
SKIP  migrations immutable                   no migration, marker or Flyway setting changed
SKIP  system actors append-only              system-actors.txt and its script did not change
SKIP  acceptance guard                       no acceptance test changed
SKIP  database scripts aligned               no database script or migration changed
SKIP  docs read by builds are registered     no backend, frontend or script changed
SKIP  assertion drift (warning only)         no test file changed
SKIP  glossary: check:copy, test:scripts, term tests terms.yaml, bcs.yaml and backend/glossary unchanged
SKIP  guards: backend guard tests            no guard test or architecture list changed
PASS  release scripts self-test              deploy_gate.py self-test passed
PASS  shellcheck scripts/release             ok
PASS  gitleaks (full history)                2922 commits scanned; no leaks
      note: uncommitted files are not in history; the pre-commit hook scans them on commit
SKIP  backend tests                          not run by default; no module code changed (shared inputs, resources, build or architecture lists)
      run: (cd backend && ./mvnw -q test-compile surefire:test -Dtest='ArchitectureRulesTest,ModularityTest,ModuleDependencyTest,NamingSurfaceTest,GlossaryTest,DdlNamingTest,LegacyShapeTest,TimeSourceGuardTest,TestIsolationGuardTest' -Dsurefire.failIfNoSpecifiedTests=false)
      before delivery run everything: scripts/ci/backend_verify.sh (Testcontainers: ask the user before starting containers)
SKIP  frontend verify                        前端依赖未安装：先在 frontend/ 运行 pnpm install --frozen-lockfile --offline（只用本机缓存、不下载）；缓存缺包而失败时，再问用户是否联网安装（本次未改 frontend/，CI 仍会跑）
SKIP  SQL Server integration tests           relevant_change.py: nothing the *MssqlIT tests exercise changed

SUMMARY: 4 PASS, 0 FAIL, 12 SKIP (base HEAD) — ready
```

SKIP 理由逐项对应（默认 11 项；HEAD 增加“词表检查”一项，共 12 项）：

1. 生成文档：未提交的报告/脚本触发既有跳过条件；遵守本卡不提交要求，未运行会写卡外生成物的生成器。
2. 迁移不可变：未改迁移、开放标记或 Flyway 配置。
3. 系统主体追加检查：未改 system-actors 或相关脚本。
4. 验收保护：未改验收测试。
5. 数据库脚本对齐：未改数据库脚本或迁移。
6. 构建输入登记：本卡未改 backend、frontend 或 scripts。
7. 断言漂移：未改测试或断言。
8. 词表检查（仅 HEAD）：本卡未改 terms.yaml、bcs.yaml 或 backend/glossary；默认分支差异触发的检查有环境 FAIL，未把它改为 SKIP。
9. 后端守护：未改守护测试或架构清单。
10. 后端业务测试：默认不运行，本卡未改模块代码，且禁止容器。
11. 前端整体验证：依赖未安装；本卡未改前端且不安装依赖。
12. SQL Server 集成测试：输入与代码未触及其覆盖范围。



## 7. 确认页提交后未写回就告警：检查草案（未实现）

**检查对象。** 以“已提交答复 → 语义操作 → 登记投影”三段做闭环，不能只检查词表里有没有某个中文子串。每个已提交题必须有操作清单，允许的操作为：新增/更新概念与指称、改名并保存旧指称、并入目标、归类、只改定义、common_word/界面词、明确不登记、暂缓（写理由、负责会话、重启条件）。名称认可、业务含义、英文/kind/BC 设计分别记录；不把 defaulted 当未答，也不把页面预选当已提交。

**读取来源。** 读取 `glossary/decisions-*.json`、第三轮原始 `answers/`、gate/round3/ACL/B1 题面、`rules/decide/answers` 与 `sheets`、架构 decision-page 题面及本地提交片段、相关 intent、`rulings.yaml`、`decisions-nm.yaml`、promoted、正式 `terms.yaml`、`bcs.yaml`、属性/受控动词指称及 proposed。提交片段必须保存原始题面版本/hash、submission id、每题选项与时间；CAPINFO 这类只有交接转述的记录先报“原始答复缺失”，不自动标已完成。

**比较方法。** 给每个提交题保存稳定的题号＋概念 id/目标 id；字段层追踪“批准值、后续取代来源、当前投影”，检查首选 zh/en、kind、bc/scope、别名状态、禁用及目标关系。并入/不登记必须验证目标或出口，而非要求源词再新增；保留定义和归类不能被当作转正。明确批准中文却只有定义内提及，没有相应指称，应告警；尚缺英文/结构者报设计待补，不能重新包装成待用户业务题。只允许显式改名关系或同概念的指称匹配，禁止靠同中文名串接不同概念。

**何时跑。** 保存提交答复时先生成操作清单与未写回告警；one-glossary 写回卡开始/结束、相关数据 PR 的本地 precheck/CI、调度合并前各跑一次；另每日离线巡检已有积压。首次上线先报告模式清点存量；新增已提交却无处置记录，以及声称已写回但投影不符的项，应使本次用语写回验收失败。存量暂缓与无题面记录须列理由、负责会话与可核实的待补证据，不设静默豁免。

失败/告警信息应包含可操作的位置，例如：

```text
DECIDED_NOT_REGISTERED: submission=… question=F-1
  concept=acceptance_evidence zh=证据
  source=intent/2026-10-16-114200…；当前 terms.yaml 无对应概念/指称
  action=补登记或提交明确的并入/不登记处置；不得仅写“已定沿用”

DECIDED_PROJECTION_MISMATCH: question=B2-B09 concept=contract_modification
  en expected=ContractModified actual=ContractModification
  source=rules/decide/answers/GL.json

DECISION_SOURCE_MISSING: submission=CAPINFO-20261017-111010-cfy
  found=交接稿；missing=原始题面版本和逐题答复；不得自动标为已写回
```

本节只定义检查与失败信息，没有新增守护、阈值、基线豁免或自动修改词表行为。
