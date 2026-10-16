# 数据模型全貌（finance 组）第二版说明

对应 `data-model.finance.v2.yaml`（完整替换稿），按 `FIN/REVIEW.md` 首轮评审修改。共 32 个聚合、81 张表（v1 为 32 个聚合、77 张表：撤 AGG-78 两张，新增 AGG-新-F1 四张、待处理队列、银行流水导入、`journal_entry_pending_items` 等）。AGG-52、59、77、89 沿用样板写法，仅把 `pi` 取值改为 none / pi / spi / tbd，并补 `source`、补个别漏写的引用。

## 接缝问题

无新增跨 BC 边。本稿的跨 BC 引用仍按聚合稿「引用与依赖方式」；同 BC 内跨聚合引用一律写 `to: AGG-xx`，不写表名（R-24），也不带 `cross_bc`。

## 结构问题

交 one-architecture：

| 编号 | 问题 | 建议 |
|---|---|---|
| ST-1 | R-05：期初余额没有聚合。ADR-0022 决定 9、11 要求期初表经财务签字，§14.2 M7 又规定迁入事实不生成分录，G-2、G-3 准入都靠它 | 本稿先以「AGG-新-F1 期初余额」（B-20）占位出表：`opening_balance_sets`（法人 × 账簿，财务签字、内部审计复核引用）、`opening_balance_lines`（科目 × 辅助维度）、`opening_balance_basis_differences`（口径差异归因）、`opening_balance_sign_offs`（财务负责人签字、内部审计复核；无法定审计，G-2）。请给聚合号，并裁定命令与事件（含签字前置） |
| ST-2 | R-13：AGG-78（口径版本、勾稽差异案）聚合稿「不计」，本稿撤掉原两张表。但词表已批准 `policy_version`、`reconciliation_deviation_case` 为 aggregate（operations.policy），ADR-0016、0023 的版本化口径与差异案生命周期必须有表 | 请定：B-17 需要口径版本、勾稽差异案的聚合（定号后表名即 `policy_versions`、`reconciliation_deviation_cases`，不再自拟） |
| ST-3 | R-12、S-18：聚合稿 AGG-52 内含列仍有「员工借款」 | 以 AGG-49 为准，聚合稿 AGG-52 删去员工借款；本稿 `payables` 理由已删 |
| ST-4 | R-02：待处理队列属 B-20 技术表，本稿放在 AGG-84 下，作为无父行的技术表（同 AGG-77 的拒收记录写法），因为队列项处理前没有分录 | 请确认放 AGG-84，还是独立技术聚合；`journal_entries` 因此改为只追加 |
| ST-5 | R-26：缺表清单增补。①新旧科目对照（ADR-0022 决定 3、8：与 U8C 对账用，归属可能在 `platform.integration` 防腐层）；②G1 导入通道要保留的原始文件（随 S-05 过渡 ADR 定形态，不再默认两步走）；③ 上稿已列：口径转换规则版本、期末结转模板版本、现金流量项目、辅助核算维度定义、推送死信与外部凭证对照、关账前置检查结果、应付争议分类取值表 | 一并给归属；①②定前不出表 |
| ST-6 | R-08、R-09：U8C 与银行、税务来源的历史（法定凭证、银行流水、申报与缴款、合伙人出资、利润分配、公司资产卡片）不在老系统与 A8，标 none 后 U-3、U-4「全部历史明细迁入」对这些对象无承载 | 投产日余额由 ST-1 承载；历史明细是否要从 U8C、银行、税务端取回，见「待用户定」DM-11。若要取回，须有 ACL 导入用例，与过渡 ADR 一并定 |
| ST-7 | R-04：名称对照。`journal_entries`（会计分录）与词表 `journal_voucher`（记账凭证，accounting.ledger aggregate，「分录载体」）是否同一概念；AGG-42 名称「分摊运行」与词表 `allocation_result`（分摊结果，含试算与生效）基本重合，本稿表名取 `allocation_results` | 交 one-glossary 用 `new_term_issue.py` 登记；若同一，`journal_entries` 改 `journal_vouchers` |

## 新用语

词表已批准、本稿已改用的 snake：`payable`、`cash_payment`、`ledger`、`account`、`cost_transfer_batch`、`allocation_result`、`business_fact`、`accounting_period`、`expense_claim`；`policy_version`、`reconciliation_deviation_case` 用于 ST-2。以下 snake 与中文名在 `terms.yaml` 中均查无同名（中文名用 `zh` 包含匹配做了查重），建议登记，使用范围「领域」，bc 见括号：

| 中文 | 表名（单数 snake） | bc |
|---|---|---|
| 分摊规则版本、分摊规则池 | allocation_rule_version、allocation_rule_pool（与词表 `indirect_cost_pool` 间接成本池不同义） | cost.costing |
| 人工成本分配运行 | labor_cost_allocation_run | cost.costing |
| 分摊结果行、成本结转批次行 | allocation_result_line、cost_transfer_batch_line | cost.costing |
| 应付来源、核销占用、应付核销、应付争议记录 | payable_source、payable_application_hold、payable_application、payable_dispute_record。「核销」与 receivables.collection 的 `cash_application`（核销）及 `ar_written_off`（坏账核销）同名异义，须互列；本稿不用 write_off | payables.payment |
| 付款分配、付款银行匹配 | cash_payment_allocation、cash_payment_bank_match | payables.payment |
| 合伙人出资、出资记录 | partner_contribution、partner_contribution_entry | payables.payment |
| 利润分配、利润分配行、决议、支付 | profit_distribution(_line / _resolution / _payment) | payables.payment |
| 供方协议、协议条款、协议证据 | supplier_agreement(_term / _evidence) | procurement.subcontracting |
| 供方协议履约额度、额度占用 | supplier_agreement_quota(_hold) | procurement.subcontracting |
| 供方履约确认（行） | supplier_performance_confirmation(_line) | procurement.subcontracting |
| 分包变更、变更影响 | subcontract_change(_impact) | procurement.subcontracting |
| 报销发票引用 | expense_claim_invoice_ref | expense.spend |
| 过账拒收记录、过账序号计数 | posting_rejection、post_sequence_counter | operations.facts |
| 迁入占用、第 13 期启用登记 | period_migration_hold、period_adjustment_enablement | accounting.period |
| 科目表版本 | chart_of_account_version | accounting.ledger |
| 会计分录、分录行、待处理分录项、分录推送记录 | journal_entry(_line / _pending_item / _push_record)，见 ST-7 | accounting.ledger |
| 账簿科目余额 | ledger_account_balance | accounting.ledger |
| 收付权责科目映射表版本（行） | account_mapping_version(_line)；与词表 `accounting_mapping_rule_set`（核算映射规则，事件到凭证模板）不同义 | accounting.ledger |
| 控制科目授权表版本（行） | control_account_authorization_version(_line) | accounting.ledger |
| 手工凭证（行） | manual_voucher(_line) | accounting.ledger |
| 期末结转运行（行） | period_end_carryover_run(_line)；与词表 `period_end_closing`（期末结转，command）关联 | accounting.ledger |
| 期间余额快照（行） | period_balance_snapshot(_line) | accounting.ledger |
| 外部法定账对账（差异、已推送未记账） | statutory_reconciliation(_difference / _unbooked_push) | accounting.ledger |
| 期初余额表（行、口径差异、签字） | opening_balance_set(_line)、opening_balance_basis_difference、opening_balance_sign_off | accounting.ledger（待 ST-1 定聚合） |
| 报表行定义版本（映射） | report_line_definition_version、report_line_mapping | accounting.reporting |
| 资金账户（版本） | fund_account(_version) | accounting.cashbank |
| 银行流水导入、银行流水行 | bank_statement_import、bank_statement_line | accounting.cashbank |
| 进项发票、占用、转出 | input_invoice(_hold / _transfer_out) | accounting.tax |
| 申报、缴款、更正 | tax_return、tax_payment、tax_return_correction | accounting.tax |
| 公司资产（版本）、折旧与摊销记录、资产处置 | company_asset(_version)、asset_depreciation_record、asset_disposal | accounting.fixedasset |

跨 bc 同名：「核销」（见上）；「账户」在 accounting.cashbank（资金账户）与词表 `account`（科目，accounting.ledger）用字相近，中文名分别写「资金账户」「科目」，不会同名。中文名只做了词表包含匹配，跨 bc 同名互列请 one-glossary 复核。

## 格式问题

1. 新增字段 `source`（每张 `history_migration: full` 的表写迁入来源系统，R-09），请 one-architecture 裁定并写进 CONVENTIONS 第 3 节；`notes`、`reason` 沿用，`data_category` 已删（R-23），`pending` 不适用于本文件。
2. `history_migration` 只用 full / open_items / none，不再用 tbd；`sensitivity` 不再用 tbd；`pi` 有 tbd 三处（`labor_cost_allocation_lines`、`business_facts`、`supplier_agreements`），均写了理由。
3. 个人信息判定口径（待 one-data 复核）：业务对象的人员引用（报销人、付款对象、责任人、辅助维度人员、签字人）计 `pi`；操作类人员（作者、确认人、制单、复核、批准、执行）作审计列，不单独计；对方账号为自然人账号的计 `spi`。
4. 锚点（`&id001`）已全部展开。
5. 同聚合内引用的目标写同聚合表名，跨聚合一律写 `to: AGG-xx`；同 BC 跨聚合是否建外键（§14.1 D2）与 CONVENTIONS 写法不一致，请裁定。
6. 聚合稿标「不计」的 AGG-78 撤出本文件，聚合总数计 32（含「不计」的 AGG-83、占位的 AGG-新-F1）。
7. 聚合内无父行的技术表（`posting_rejections`、`post_sequence_counters`、`journal_entry_pending_items`）写 `role: child` 且不写本聚合内引用，沿用 AGG-77 样板。
8. 配置类版本草稿存放位置（R-31）：版本行在确认时才写入，草稿另存，存放位置请 one-data 定（审批载荷或同聚合技术表）。

## 待用户定

| 编号 | 问题 | 影响的表 | 出处 |
|---|---|---|---|
| DM-3 | 供方履约确认形态与成本结转 | supplier_performance_*、cost_transfer_batch* | Q20、INV-88 |
| DM-4 | 人工成本分配口径 | labor_cost_allocation_* | FD-09 |
| DM-5 | 争议处置分类取值（只确认诉讼） | payable_dispute_records | AGG-52 |
| DM-6 | 对账签字人与容差；应收销项在收付账簿的时点 | statutory_reconciliations、account_mapping_lines | ADR-0022 |
| DM-7 | 第 13 期启用年度、模板结转与「以前年度损益调整」 | period_adjustment_enablements、period_end_carryover_* | P-001、P-002、P-003 |
| DM-9 | 保留期（凭证、账簿 30 年之外的各类） | 凭证、分录、快照、事实、银行流水、申报 | D12 |
| DM-10 | 对外按开票、权责按客户确认两账差异处理 | journal_entries、account_mapping_lines | R-9 |
| DM-11 | 来源不在老系统与 A8 的历史（法定凭证明细、银行流水、申报与缴款、合伙人出资、利润分配）是否从 U8C、银行、税务端取回迁入；不取回则以期初余额表承载余额，历史查阅回老库只读归档（U-9）或 U8C | journal_*、bank_statement_*、tax_*、partner_contribution*、profit_distribution* | U-3、U-4、R-09 |

v1 的 DM-1（U8C 数据进入方式）改交 one-architecture 的过渡 ADR（S-05）与 ST-1；DM-2 按 R-08 处理，不再问用户；DM-8（事实是否逐人存放）改交 one-data。

## 交 one-data 复核

1. 所有 `source` 取值按 v1 与聚合稿推断，须经 ADR-0033 来源对照与取证核实；尤其是资金账户（A8 付款账户加财务清单补录）、供方协议（老系统分包与 A8 采购、租赁等表单）、进项发票（A8 报销所附与老系统分包发票）、供方履约确认。
2. 公司资产卡片、资金账户、合伙人出资台账的投产日初始化来源（初始化建档不等于历史迁入），与 ST-1、DM-11 合并定。
3. DM-8：`business_facts`、`labor_cost_allocation_lines` 的 pi 判定。
4. 配置版本（科目表、映射表、授权表、报表行定义、分摊规则、第 13 期启用登记）仍暂记 `append_only`，草稿存放见格式问题 8。
5. `ledger_account_balances` 是否独立表；`post_sequence_counters` 高频更新行方案（D18 第 10 条）；`business_facts`、`journal_entries` 分区键（§2.4a 第 8 条）。
6. `accounting_periods`、`ledgers` 的 D17 豁免判定；`payable_application_holds`、`manual_vouchers` 等状态可变的 `versioned: none` 表是否都符合 D17。
7. 个人信息口径（格式问题 3）与 `cash_payments` 的 sensitive 判定。

## 评审意见处理

仅列与数据模型有关的条目；R-01、R-15、R-16、R-17、R-20、R-21、R-22、R-27、R-28（流程部分）、R-29、R-30 属流程稿，本稿不涉及。

| 编号 | 处理 |
|---|---|
| R-02 | 已改。`journal_entries` 去掉「状态」列，只写已记账分录；新建 `journal_entry_pending_items`（B-20 内技术表，`versioned: none`）承载 INV-98 待处理队列；推送状态仍由 `journal_entry_push_records` 逐投递追加。AGG-77 候选表样板已撤（`business_facts` 根表）。归属问题见 ST-4 |
| R-03 | 样板已改（AGG-77）：`business_facts` 为根，`posting_rejections`、`post_sequence_counters` 为技术表。本稿沿用，仅补 `source` |
| R-04 | 已改。`payables`、`cash_payments`（`cash_payment_id`）、`ledgers`（`ledger_id`）、`cost_transfer_batches`、`allocation_results`、`accounts`（词表 `account`）；核销类表不用 write_off，改 `payable_applications`；`reconciliation_deviation_case`、`policy_version` 见 ST-2；`journal_entries` 与 `journal_voucher` 见 ST-7；新用语逐表重查，见「新用语」 |
| R-05 | 已改（占位）。AGG-新-F1 四张表，见 ST-1；`ledger_account_balances`、`journal_entries`、`period_balance_snapshots` 等的理由改为指向这张表 |
| R-06 | 已改。`fund_accounts`、`company_assets` 根表 `versioned: none`，去掉当前状态投影与 `version` 列，只留身份列；状态与可变属性在 `*_versions`（effective_dated）；公司资产类别写明含 IT 资产（intent 2026-10-16-152926） |
| R-07 | 已改。落账期间、原期间统一写「（会计年度、期间序号）」，去掉对 AGG-81 的 UUID 引用（`accounting_period_id`）；`reconciliation` 差异案随 ST-2 撤出；补齐：银行流水行（法人、业务日期、单据编号）、申报（业务日期）、利润分配（业务日期、落账期间）、期末结转运行（业务日期、单据编号）、折旧记录与处置（法人、业务日期、单据编号）、各运行单据与分包变更（法人、业务日期）；进项发票样板已有单据编号 |
| R-08 | 已改。没有 tbd。运行单据（AGG-41、42、43、99）写 none，理由为「结果由迁入的权责事实承载（U-3），投产前两期按 U-10 重算」；分摊规则 `allocation_rule_versions` 写明至少录入覆盖 U-10 两个期间的版本；U8C 来源（科目表、凭证、快照、手工凭证、公司资产等）写 none 并指向 AGG-新-F1 与 DM-1 |
| R-09 | 已改。每张 full 表写 `source`；来源不在老系统与 A8 的（银行流水、申报、合伙人出资、利润分配）改 none 加理由，是否取回进 DM-11；资金账户保留 full 并写补录来源，交 one-data 核实 |
| R-10 | 已改。新建根表 `bank_statement_imports`（校验和唯一、原始文件引用、资金账户、导入时刻），`bank_statement_lines` 为只追加子表 |
| R-11 | 样板已改（AGG-59、89），本稿沿用 |
| R-12 | 已改：理由不含员工借款；聚合稿修订见 ST-3 |
| R-13 | 已改：撤 AGG-78 两张表，见 ST-2；未保留，故表名不再另拟 |
| R-14 | 已改。每表加 `pi`（none / pi / spi / tbd）；银行信息、薪酬相关判 sensitive；带人员 ID 判 pi（`journal_entry_lines`、`payables`、`expense_claims`、`cash_payments`、公司资产版本等）；银行流水行对方账号判 spi。口径见格式问题 3 |
| R-18 | 已按其处理本稿的「待用户定」：交架构与交 one-data 事项已分表；DM-2 改按 R-08 处理，不再问用户 |
| R-19 | 已改。不再有汇总表；根表版本化取值以 yaml 为准，AGG-52、53、54、58、89、90、109 的根表为 none |
| R-23 | 已改。锚点已展开；`data_category` 已删；`source`、`notes` 在格式问题 1 列出 |
| R-24 | 已改。跨聚合引用一律 `to: AGG-xx`，同聚合内引用写表名；同 BC 外键问题进格式问题 5 |
| R-25 | 已改。「上年调整栏」「以前期间更正栏」移到 `period_balance_snapshot_lines` |
| R-26 | 已补进结构问题 ST-5 |
| R-31 | 已改。配置版本表去掉「状态」列，生效由生效日推出，版本行在确认时才写入，草稿另存（格式问题 8） |
| R-32 | 已改。`supplier_agreement_evidences` 改 append_only；争议状态只在 `payable_dispute_records`（样板已去根表状态）；补了 `payable_dispute_records`、`input_invoice_transfers_out` 缺的父引用 |
