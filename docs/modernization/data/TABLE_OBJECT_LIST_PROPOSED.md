# 三组已提议表的清单与归属（供 one-architecture 双向核对，ARC-1017-ARC-16）

- 作者：one-data，2026-10-17。机械整理自三个分支的表级数据模型片段：`one-arch-finance-closeout` 的 `data-model.finance.yaml`、`one-arch-people-closeout` 的 `data-model.people.yaml`、`one-arch-revenue-closeout` 的 `data-model.revenue.yaml`（共 362 个表项，与 one-architecture 说的数一致）。聚合名与 BC 名取自 `ENTERPRISE_AGGREGATES_DRAFT.md` 与 `PRC_ALLOCATIONS.md`。
- 状态：**全部为提议、未签发**（数据设计门禁：这些表的 DDL 还没有进卡，尚未经我审查）。「主对象」只写聚合所对应的聚合稿名称（括号里的 OBJ 编号是聚合稿自己标的），不是我的新判断；**我没有逐表核过对象归属**。
- 「技术表」栏按表名关键字（staging、outbox、inbox、idempot、import_、cursor、dedup）机械标「是（按名推定）」，其余为「否」。按这组关键字本次**没有命中任何一张**（最初的关键字含 reconcil、migration_ 误中法定账对账与期间迁入占用等业务表，已去掉）；因此 362 个表项全部按业务表对待，是否有应归技术表的请 one-architecture 判；yaml 里的 `role: staging`（迁入暂存）本次一条也没有。sync_* 等不属聚合的技术表在各组 `DATA_MODEL.md`，不在这 362 个表项里。
- 每行：表名（不含 schema，yaml 约定）｜来源｜BC｜聚合｜表角色｜技术表。表角色：root 根、child 次级、version 版本、link 关联。
- 查不到聚合名的聚合编号：见末尾。


## finance 组（105 表）

| 表名 | 来源 | BC | 聚合 | 表角色 | 技术表 |
|---|---|---|---|---|---|
| allocation_rule_versions | 提议·finance·未签发 | B-07 成本核算（cost.costing） | AGG-40 分摊规则版本（OBJ-78，归类待定） | root | 否 |
| allocation_rule_pools | 提议·finance·未签发 | B-07 成本核算（cost.costing） | AGG-40 分摊规则版本（OBJ-78，归类待定） | child | 否 |
| labor_cost_allocation_runs | 提议·finance·未签发 | B-07 成本核算（cost.costing） | AGG-41 人工成本分配运行（运行单据；OBJ-76 在信息图已去掉） | root | 否 |
| labor_cost_allocation_lines | 提议·finance·未签发 | B-07 成本核算（cost.costing） | AGG-41 人工成本分配运行（运行单据；OBJ-76 在信息图已去掉） | child | 否 |
| allocation_results | 提议·finance·未签发 | B-07 成本核算（cost.costing） | AGG-42 分摊运行（运行单据；OBJ-79 已去掉） | root | 否 |
| allocation_result_lines | 提议·finance·未签发 | B-07 成本核算（cost.costing） | AGG-42 分摊运行（运行单据；OBJ-79 已去掉） | child | 否 |
| cost_transfer_batches | 提议·finance·未签发 | B-07 成本核算（cost.costing） | AGG-43 成本结转批次（运行单据；OBJ-80 已去掉） | root | 否 |
| cost_transfer_batch_lines | 提议·finance·未签发 | B-07 成本核算（cost.costing） | AGG-43 成本结转批次（运行单据；OBJ-80 已去掉） | child | 否 |
| payables | 提议·finance·未签发 | B-10 应付与付款（payables.payment） | AGG-52 应付往来款项（OBJ-85 应付方向：分包 / 采购 / 报销 / 其他应付、预付、员工借款、我方扣留分包方质保金、应付薪酬（法人 × 月一笔实发合计，明细留在薪酬结算）） | root | 否 |
| payable_sources | 提议·finance·未签发 | B-10 应付与付款（payables.payment） | AGG-52 应付往来款项（OBJ-85 应付方向：分包 / 采购 / 报销 / 其他应付、预付、员工借款、我方扣留分包方质保金、应付薪酬（法人 × 月一笔实发合计，明细留在薪酬结算）） | child | 否 |
| payable_application_holds | 提议·finance·未签发 | B-10 应付与付款（payables.payment） | AGG-52 应付往来款项（OBJ-85 应付方向：分包 / 采购 / 报销 / 其他应付、预付、员工借款、我方扣留分包方质保金、应付薪酬（法人 × 月一笔实发合计，明细留在薪酬结算）） | child | 否 |
| payable_applications | 提议·finance·未签发 | B-10 应付与付款（payables.payment） | AGG-52 应付往来款项（OBJ-85 应付方向：分包 / 采购 / 报销 / 其他应付、预付、员工借款、我方扣留分包方质保金、应付薪酬（法人 × 月一笔实发合计，明细留在薪酬结算）） | child | 否 |
| payable_dispute_records | 提议·finance·未签发 | B-10 应付与付款（payables.payment） | AGG-52 应付往来款项（OBJ-85 应付方向：分包 / 采购 / 报销 / 其他应付、预付、员工借款、我方扣留分包方质保金、应付薪酬（法人 × 月一笔实发合计，明细留在薪酬结算）） | child | 否 |
| cash_payments | 提议·finance·未签发 | B-10 应付与付款（payables.payment） | AGG-53 付款（OBJ-86 付方向） | root | 否 |
| cash_payment_allocations | 提议·finance·未签发 | B-10 应付与付款（payables.payment） | AGG-53 付款（OBJ-86 付方向） | child | 否 |
| cash_payment_bank_matches | 提议·finance·未签发 | B-10 应付与付款（payables.payment） | AGG-53 付款（OBJ-86 付方向） | link | 否 |
| partner_contributions | 提议·finance·未签发 | B-10 应付与付款（payables.payment） | AGG-54 合伙人出资（OBJ-91） | root | 否 |
| partner_contribution_entries | 提议·finance·未签发 | B-10 应付与付款（payables.payment） | AGG-54 合伙人出资（OBJ-91） | child | 否 |
| profit_distributions | 提议·finance·未签发 | B-10 应付与付款（payables.payment） | AGG-109 利润分配（OBJ-112，法人 × 年度；第 ② 步回补，intent 082311） | root | 否 |
| profit_distribution_lines | 提议·finance·未签发 | B-10 应付与付款（payables.payment） | AGG-109 利润分配（OBJ-112，法人 × 年度；第 ② 步回补，intent 082311） | child | 否 |
| profit_distribution_resolutions | 提议·finance·未签发 | B-10 应付与付款（payables.payment） | AGG-109 利润分配（OBJ-112，法人 × 年度；第 ② 步回补，intent 082311） | child | 否 |
| profit_distribution_payments | 提议·finance·未签发 | B-10 应付与付款（payables.payment） | AGG-109 利润分配（OBJ-112，法人 × 年度；第 ② 步回补，intent 082311） | child | 否 |
| supplier_agreements | 提议·finance·未签发 | B-11 分包与采购（procurement.subcontracting） | AGG-55 供方协议（OBJ-05：分包合同、采购合同、租赁合同、职业责任保险单、居间协议、劳务派遣协议） | root | 否 |
| supplier_agreement_terms | 提议·finance·未签发 | B-11 分包与采购（procurement.subcontracting） | AGG-55 供方协议（OBJ-05：分包合同、采购合同、租赁合同、职业责任保险单、居间协议、劳务派遣协议） | child | 否 |
| supplier_agreement_evidences | 提议·finance·未签发 | B-11 分包与采购（procurement.subcontracting） | AGG-55 供方协议（OBJ-05：分包合同、采购合同、租赁合同、职业责任保险单、居间协议、劳务派遣协议） | child | 否 |
| subcontract_changes | 提议·finance·未签发 | B-11 分包与采购（procurement.subcontracting） | AGG-56 分包变更（OBJ-09 的类型「分包变更」） | root | 否 |
| subcontract_change_impacts | 提议·finance·未签发 | B-11 分包与采购（procurement.subcontracting） | AGG-56 分包变更（OBJ-09 的类型「分包变更」） | child | 否 |
| supplier_performance_confirmations | 提议·finance·未签发 | B-11 分包与采购（procurement.subcontracting） | AGG-57 供方履约确认（OBJ-87）（形态**待定** Q20） | root | 否 |
| supplier_performance_confirmation_lines | 提议·finance·未签发 | B-11 分包与采购（procurement.subcontracting） | AGG-57 供方履约确认（OBJ-87）（形态**待定** Q20） | child | 否 |
| supplier_agreement_quotas | 提议·finance·未签发 | B-11 分包与采购（procurement.subcontracting） | AGG-58 供方协议履约额度（技术；每协议一个） | root | 否 |
| supplier_agreement_quota_holds | 提议·finance·未签发 | B-11 分包与采购（procurement.subcontracting） | AGG-58 供方协议履约额度（技术；每协议一个） | child | 否 |
| expense_claims | 提议·finance·未签发 | B-12 费用报销（expense.spend） | AGG-59 费用报销（OBJ-88） | root | 否 |
| expense_claim_lines | 提议·finance·未签发 | B-12 费用报销（expense.spend） | AGG-59 费用报销（OBJ-88） | child | 否 |
| expense_claim_invoice_refs | 提议·finance·未签发 | B-12 费用报销（expense.spend） | AGG-59 费用报销（OBJ-88） | child | 否 |
| business_facts | 提议·finance·未签发 | B-16 过账（operations.facts） | AGG-77 过账（技术；ADR-0016 决定 1；信息图已去掉「经营事实」，见 C-39） | root | 否 |
| posting_rejections | 提议·finance·未签发 | B-16 过账（operations.facts） | AGG-77 过账（技术；ADR-0016 决定 1；信息图已去掉「经营事实」，见 C-39） | child | 否 |
| posting_rejection_dispositions | 提议·finance·未签发 | B-16 过账（operations.facts） | AGG-77 过账（技术；ADR-0016 决定 1；信息图已去掉「经营事实」，见 C-39） | child | 否 |
| post_sequence_counters | 提议·finance·未签发 | B-16 过账（operations.facts） | AGG-77 过账（技术；ADR-0016 决定 1；信息图已去掉「经营事实」，见 C-39） | child | 否 |
| accounting_periods | 提议·finance·未签发 | B-19 会计期间（accounting.period） | AGG-81 会计期间（OBJ-97，法人 × 会计年度 × 期间序号 1–13；第 13 期启用登记见 AGG-96，ADR-0046；迁入占用见 §14.2 M8） | root | 否 |
| period_migration_holds | 提议·finance·未签发 | B-19 会计期间（accounting.period） | AGG-81 会计期间（OBJ-97，法人 × 会计年度 × 期间序号 1–13；第 13 期启用登记见 AGG-96，ADR-0046；迁入占用见 §14.2 M8） | child | 否 |
| period_adjustment_enablements | 提议·finance·未签发 | B-19 会计期间（accounting.period） | AGG-96 第 13 期启用登记（ADR-0046 决定 4；按会计年度全局，配置类版本：带生效日、财务批准人、决定出处，只追加） | root | 否 |
| chart_of_account_versions | 提议·finance·未签发 | B-20 总账（accounting.ledger） | AGG-82 科目表版本（OBJ-95，两张） | root | 否 |
| accounts | 提议·finance·未签发 | B-20 总账（accounting.ledger） | AGG-82 科目表版本（OBJ-95，两张） | child | 否 |
| ledgers | 提议·finance·未签发 | B-20 总账（accounting.ledger） | AGG-83 账簿（OBJ-96）（不计：参照数据，每法人两个，随法人登记，按 §14.1 D17 参照数据管理） | root | 否 |
| journal_entries | 提议·finance·未签发 | B-20 总账（accounting.ledger） | AGG-84 会计分录（OBJ-98） | root | 否 |
| journal_entry_lines | 提议·finance·未签发 | B-20 总账（accounting.ledger） | AGG-84 会计分录（OBJ-98） | child | 否 |
| journal_entry_pending_items | 提议·finance·未签发 | B-20 总账（accounting.ledger） | AGG-84 会计分录（OBJ-98） | child | 否 |
| journal_entry_push_records | 提议·finance·未签发 | B-20 总账（accounting.ledger） | AGG-84 会计分录（OBJ-98） | child | 否 |
| ledger_account_balances | 提议·finance·未签发 | B-20 总账（accounting.ledger） | AGG-84 会计分录（OBJ-98） | child | 否 |
| account_mapping_versions | 提议·finance·未签发 | B-20 总账（accounting.ledger） | AGG-85 收付 ↔ 权责科目映射表版本（ADR-0022 第 2 条，配置类） | root | 否 |
| account_mapping_lines | 提议·finance·未签发 | B-20 总账（accounting.ledger） | AGG-85 收付 ↔ 权责科目映射表版本（ADR-0022 第 2 条，配置类） | child | 否 |
| control_account_authorization_versions | 提议·finance·未签发 | B-20 总账（accounting.ledger） | AGG-94 控制科目授权表版本（ADR-0022 第 3 条，配置类） | root | 否 |
| control_account_authorization_lines | 提议·finance·未签发 | B-20 总账（accounting.ledger） | AGG-94 控制科目授权表版本（ADR-0022 第 3 条，配置类） | child | 否 |
| manual_vouchers | 提议·finance·未签发 | B-20 总账（accounting.ledger） | AGG-98 受控手工凭证（OBJ-98 会计分录按来源通道拆出的受控手工通道；阶段门走查补，§4.3.3） | root | 否 |
| manual_voucher_lines | 提议·finance·未签发 | B-20 总账（accounting.ledger） | AGG-98 受控手工凭证（OBJ-98 会计分录按来源通道拆出的受控手工通道；阶段门走查补，§4.3.3） | child | 否 |
| period_end_carryover_runs | 提议·finance·未签发 | B-20 总账（accounting.ledger） | AGG-99 期末结转运行（运行单据；损益结转、未分配利润等期末凭证，ADR-0022 第 3 条；法人 × 期间 × 模板版本；阶段门走查补，§4.3.3） | root | 否 |
| period_end_carryover_lines | 提议·finance·未签发 | B-20 总账（accounting.ledger） | AGG-99 期末结转运行（运行单据；损益结转、未分配利润等期末凭证，ADR-0022 第 3 条；法人 × 期间 × 模板版本；阶段门走查补，§4.3.3） | child | 否 |
| period_balance_snapshots | 提议·finance·未签发 | B-20 总账（accounting.ledger） | AGG-100 期间余额快照（法人 × 账簿 × 期间；ADR-0022 第 3 条「月结生成快照」；含「上年调整」「以前期间更正」栏，ADR-0046 第 6 项第 1 条；阶段门走查补，§4.3.3） | root | 否 |
| period_balance_snapshot_lines | 提议·finance·未签发 | B-20 总账（accounting.ledger） | AGG-100 期间余额快照（法人 × 账簿 × 期间；ADR-0022 第 3 条「月结生成快照」；含「上年调整」「以前期间更正」栏，ADR-0046 第 6 项第 1 条；阶段门走查补，§4.3.3） | child | 否 |
| statutory_reconciliations | 提议·finance·未签发 | B-20 总账（accounting.ledger） | AGG-101 外部法定账对账（运行单据；法人 × 期间，收付口径账簿与外部法定账余额与发生额核对，ADR-0022 第 8 条；阶段门走查补，§4.3.3） | root | 否 |
| statutory_reconciliation_differences | 提议·finance·未签发 | B-20 总账（accounting.ledger） | AGG-101 外部法定账对账（运行单据；法人 × 期间，收付口径账簿与外部法定账余额与发生额核对，ADR-0022 第 8 条；阶段门走查补，§4.3.3） | child | 否 |
| statutory_reconciliation_unbooked_pushes | 提议·finance·未签发 | B-20 总账（accounting.ledger） | AGG-101 外部法定账对账（运行单据；法人 × 期间，收付口径账簿与外部法定账余额与发生额核对，ADR-0022 第 8 条；阶段门走查补，§4.3.3） | child | 否 |
| opening_balance_sets | 提议·finance·未签发 | B-20 总账（accounting.ledger） | AGG-125 期初余额 | root | 否 |
| opening_balance_lines | 提议·finance·未签发 | B-20 总账（accounting.ledger） | AGG-125 期初余额 | child | 否 |
| opening_balance_project_splits | 提议·finance·未签发 | B-20 总账（accounting.ledger） | AGG-125 期初余额 | child | 否 |
| opening_balance_basis_differences | 提议·finance·未签发 | B-20 总账（accounting.ledger） | AGG-125 期初余额 | child | 否 |
| opening_balance_sign_offs | 提议·finance·未签发 | B-20 总账（accounting.ledger） | AGG-125 期初余额 | child | 否 |
| report_line_definition_versions | 提议·finance·未签发 | B-21 法定报表（accounting.reporting） | AGG-86 报表行定义版本（OBJ-99） | root | 否 |
| report_line_mappings | 提议·finance·未签发 | B-21 法定报表（accounting.reporting） | AGG-86 报表行定义版本（OBJ-99） | child | 否 |
| fund_accounts | 提议·finance·未签发 | B-22 银行与现金（accounting.cashbank） | AGG-87 资金账户（OBJ-89） | root | 否 |
| fund_account_versions | 提议·finance·未签发 | B-22 银行与现金（accounting.cashbank） | AGG-87 资金账户（OBJ-89） | version | 否 |
| bank_statement_imports | 提议·finance·未签发 | B-22 银行与现金（accounting.cashbank） | AGG-88 银行流水（OBJ-90） | root | 否 |
| bank_statement_lines | 提议·finance·未签发 | B-22 银行与现金（accounting.cashbank） | AGG-88 银行流水（OBJ-90） | child | 否 |
| input_invoices | 提议·finance·未签发 | B-23 税费（accounting.tax） | AGG-89 进项发票（OBJ-83 的进项方向，ADR-0022 第 7 条） | root | 否 |
| input_invoice_holds | 提议·finance·未签发 | B-23 税费（accounting.tax） | AGG-89 进项发票（OBJ-83 的进项方向，ADR-0022 第 7 条） | child | 否 |
| input_invoice_transfers_out | 提议·finance·未签发 | B-23 税费（accounting.tax） | AGG-89 进项发票（OBJ-83 的进项方向，ADR-0022 第 7 条） | child | 否 |
| tax_returns | 提议·finance·未签发 | B-23 税费（accounting.tax） | AGG-90 增值税、附加税、企业所得税申报（OBJ-92 的税务部分；个税扣缴与社保公积金申报拆到 AGG-95） | root | 否 |
| tax_payments | 提议·finance·未签发 | B-23 税费（accounting.tax） | AGG-90 增值税、附加税、企业所得税申报（OBJ-92 的税务部分；个税扣缴与社保公积金申报拆到 AGG-95） | child | 否 |
| tax_return_corrections | 提议·finance·未签发 | B-23 税费（accounting.tax） | AGG-90 增值税、附加税、企业所得税申报（OBJ-92 的税务部分；个税扣缴与社保公积金申报拆到 AGG-95） | child | 否 |
| company_assets | 提议·finance·未签发 | B-24 固定资产（accounting.fixedasset） | AGG-91 公司资产（OBJ-93） | root | 否 |
| company_asset_versions | 提议·finance·未签发 | B-24 固定资产（accounting.fixedasset） | AGG-91 公司资产（OBJ-93） | version | 否 |
| asset_depreciation_records | 提议·finance·未签发 | B-24 固定资产（accounting.fixedasset） | AGG-91 公司资产（OBJ-93） | child | 否 |
| asset_disposals | 提议·finance·未签发 | B-24 固定资产（accounting.fixedasset） | AGG-91 公司资产（OBJ-93） | child | 否 |
| policy_versions | 提议·finance·未签发 | B-17 口径与勾稽（operations.policy） | AGG-78 口径版本、勾稽差异案（不计：ADR-0016、0023 规定，信息图无对象，指标定义移到第 ⑥ 步；以 ADR-0016、ADR-0023 为据，不在本稿推导，符合性审查 建-9） | root | 否 |
| metric_definitions | 提议·finance·未签发 | B-17 口径与勾稽（operations.policy） | AGG-78 口径版本、勾稽差异案（不计：ADR-0016、0023 规定，信息图无对象，指标定义移到第 ⑥ 步；以 ADR-0016、ADR-0023 为据，不在本稿推导，符合性审查 建-9） | child | 否 |
| presales_investment_attributions | 提议·finance·未签发 | B-07 成本核算（cost.costing） | AGG-122 售前投入归属登记 | root | 否 |
| presales_investment_receipts | 提议·finance·未签发 | B-07 成本核算（cost.costing） | AGG-122 售前投入归属登记 | child | 否 |
| cost_accounting_parameter_versions | 提议·finance·未签发 | B-07 成本核算（cost.costing） | AGG-123 成本核算参数版本 | root | 否 |
| asset_category_policy_versions | 提议·finance·未签发 | B-24 固定资产（accounting.fixedasset） | AGG-124 资产类别与折旧政策版本 | root | 否 |
| asset_category_policy_lines | 提议·finance·未签发 | B-24 固定资产（accounting.fixedasset） | AGG-124 资产类别与折旧政策版本 | child | 否 |
| reconciliation_deviation_cases | 提议·finance·未签发 | B-17 口径与勾稽（operations.policy） | AGG-126 勾稽差异案 | root | 否 |
| reconciliation_deviation_case_closures | 提议·finance·未签发 | B-17 口径与勾稽（operations.policy） | AGG-126 勾稽差异案 | child | 否 |
| basis_conversion_rule_versions | 提议·finance·未签发 | B-20 总账（accounting.ledger） | AGG-127 口径转换规则版本 | root | 否 |
| period_end_carryover_template_versions | 提议·finance·未签发 | B-20 总账（accounting.ledger） | AGG-128 期末结转模板版本 | root | 否 |
| period_end_carryover_template_lines | 提议·finance·未签发 | B-20 总账（accounting.ledger） | AGG-128 期末结转模板版本 | child | 否 |
| cash_flow_item_table_versions | 提议·finance·未签发 | B-20 总账（accounting.ledger） | AGG-129 现金流量项目表版本 | root | 否 |
| cash_flow_items | 提议·finance·未签发 | B-20 总账（accounting.ledger） | AGG-129 现金流量项目表版本 | child | 否 |
| expense_pre_applications | 提议·finance·未签发 | B-12 费用报销（expense.spend） | AGG-130 费用事前申请 | root | 否 |
| travel_provider_monthly_statements | 提议·finance·未签发 | B-12 费用报销（expense.spend） | AGG-131 差旅订单月账单 | root | 否 |
| travel_provider_statement_orders | 提议·finance·未签发 | B-12 费用报销（expense.spend） | AGG-131 差旅订单月账单 | child | 否 |
| business_trip_over_standard_reviews | 提议·finance·未签发 | B-12 费用报销（expense.spend） | AGG-131 差旅订单月账单 | child | 否 |
| business_trip_applications | 提议·finance·未签发 | B-12 费用报销（expense.spend） | AGG-132 出差申请 | root | 否 |
| business_trip_itinerary_legs | 提议·finance·未签发 | B-12 费用报销（expense.spend） | AGG-132 出差申请 | child | 否 |
| travel_allowance_standard_versions | 提议·finance·未签发 | B-12 费用报销（expense.spend） | AGG-133 出差补贴标准版本 | root | 否 |
| travel_allowance_standard_lines | 提议·finance·未签发 | B-12 费用报销（expense.spend） | AGG-133 出差补贴标准版本 | child | 否 |

## people 组（95 表）

| 表名 | 来源 | BC | 聚合 | 表角色 | 技术表 |
|---|---|---|---|---|---|
| legal_entities | 提议·people·未签发 | B-13 人员与组织（organization.workforce） | AGG-60 法人主体（OBJ-54，已建 `LegalEntity`） | root | 否 |
| legal_entity_versions | 提议·people·未签发 | B-13 人员与组织（organization.workforce） | AGG-60 法人主体（OBJ-54，已建 `LegalEntity`） | version | 否 |
| legal_entity_attributions | 提议·people·未签发 | B-13 人员与组织（organization.workforce） | AGG-117 法人归属（已建 `organization_workforce.legal_entity_attributions`） | root | 否 |
| org_unit_legal_entities | 提议·people·未签发 | B-13 人员与组织（organization.workforce） | AGG-117 法人归属（已建 `organization_workforce.legal_entity_attributions`） | version | 否 |
| employee_legal_entities | 提议·people·未签发 | B-13 人员与组织（organization.workforce） | AGG-117 法人归属（已建 `organization_workforce.legal_entity_attributions`） | version | 否 |
| legal_entity_attribution_corrections | 提议·people·未签发 | B-13 人员与组织（organization.workforce） | AGG-117 法人归属（已建 `organization_workforce.legal_entity_attributions`） | child | 否 |
| org_units | 提议·people·未签发 | B-13 人员与组织（organization.workforce） | AGG-61 组织单元（OBJ-55，现为同步快照 `OrgSnapshot`） | root | 否 |
| org_unit_versions | 提议·people·未签发 | B-13 人员与组织（organization.workforce） | AGG-61 组织单元（OBJ-55，现为同步快照 `OrgSnapshot`） | version | 否 |
| employees | 提议·people·未签发 | B-13 人员与组织（organization.workforce） | AGG-62 人员（OBJ-56；现为快照记录与 `RegisteredEmployee`）（法人三口径**待定** Q27） | root | 否 |
| employment_versions | 提议·people·未签发 | B-13 人员与组织（organization.workforce） | AGG-62 人员（OBJ-56；现为快照记录与 `RegisteredEmployee`）（法人三口径**待定** Q27） | version | 否 |
| interviews | 提议·people·未签发 | B-13 人员与组织（organization.workforce） | AGG-62 人员（OBJ-56；现为快照记录与 `RegisteredEmployee`）（法人三口径**待定** Q27） | child | 否 |
| hiring_decisions | 提议·people·未签发 | B-13 人员与组织（organization.workforce） | AGG-62 人员（OBJ-56；现为快照记录与 `RegisteredEmployee`）（法人三口径**待定** Q27） | child | 否 |
| separation_requests | 提议·people·未签发 | B-13 人员与组织（organization.workforce） | AGG-62 人员（OBJ-56；现为快照记录与 `RegisteredEmployee`）（法人三口径**待定** Q27） | child | 否 |
| assignments | 提议·people·未签发 | B-13 人员与组织（organization.workforce） | AGG-63 任职（OBJ-57） | root | 否 |
| assignment_versions | 提议·people·未签发 | B-13 人员与组织（organization.workforce） | AGG-63 任职（OBJ-57） | version | 否 |
| jobs | 提议·people·未签发 | B-13 人员与组织（organization.workforce） | AGG-64 岗位（OBJ-58，现为快照记录 `Position`）（编制**待定** Q26） | root | 否 |
| job_versions | 提议·people·未签发 | B-13 人员与组织（organization.workforce） | AGG-64 岗位（OBJ-58，现为快照记录 `Position`）（编制**待定** Q26） | version | 否 |
| job_families | 提议·people·未签发 | B-13 人员与组织（organization.workforce） | AGG-64 岗位（OBJ-58，现为快照记录 `Position`）（编制**待定** Q26） | child | 否 |
| job_family_versions | 提议·people·未签发 | B-13 人员与组织（organization.workforce） | AGG-64 岗位（OBJ-58，现为快照记录 `Position`）（编制**待定** Q26） | version | 否 |
| job_grades | 提议·people·未签发 | B-13 人员与组织（organization.workforce） | AGG-64 岗位（OBJ-58，现为快照记录 `Position`）（编制**待定** Q26） | child | 否 |
| job_grade_versions | 提议·people·未签发 | B-13 人员与组织（organization.workforce） | AGG-64 岗位（OBJ-58，现为快照记录 `Position`）（编制**待定** Q26） | version | 否 |
| recruitment_requisitions | 提议·people·未签发 | B-13 人员与组织（organization.workforce） | AGG-65 招聘需求（OBJ-60）（随 Q26） | root | 否 |
| practice_certificates | 提议·people·未签发 | B-13 人员与组织（organization.workforce） | AGG-66 注册执业证书（OBJ-64） | root | 否 |
| certificate_registration_periods | 提议·people·未签发 | B-13 人员与组织（organization.workforce） | AGG-66 注册执业证书（OBJ-64） | child | 否 |
| continuing_education_records | 提议·people·未签发 | B-13 人员与组织（organization.workforce） | AGG-66 注册执业证书（OBJ-64） | child | 否 |
| labor_contracts | 提议·people·未签发 | B-13 人员与组织（organization.workforce） | AGG-67 劳动合同（OBJ-05 的类型「劳动合同」）（用人单位口径随 Q27） | root | 否 |
| performance_reviews | 提议·people·未签发 | B-13 人员与组织（organization.workforce） | AGG-106 绩效评估（OBJ-109） | root | 否 |
| performance_review_documents | 提议·people·未签发 | B-13 人员与组织（organization.workforce） | AGG-106 绩效评估（OBJ-109） | child | 否 |
| performance_review_corrections | 提议·people·未签发 | B-13 人员与组织（organization.workforce） | AGG-106 绩效评估（OBJ-109） | child | 否 |
| work_calendar_years | 提议·people·未签发 | B-14 工作日历（organization.attendance） | AGG-68 工作日历年度（OBJ-28，已建） | root | 否 |
| work_calendar_days | 提议·people·未签发 | B-14 工作日历（organization.attendance） | AGG-68 工作日历年度（OBJ-28，已建） | child | 否 |
| standard_work_hour_versions | 提议·people·未签发 | B-14 工作日历（organization.attendance） | AGG-108 标准工时版本（OBJ-28 工作日历下的版本化配置属性，不是新的一级对象；每版本一个聚合 + 排除约束） | root | 否 |
| standard_work_hour_day_rules | 提议·people·未签发 | B-14 工作日历（organization.attendance） | AGG-108 标准工时版本（OBJ-28 工作日历下的版本化配置属性，不是新的一级对象；每版本一个聚合 + 排除约束） | child | 否 |
| standard_work_hour_date_overrides | 提议·people·未签发 | B-14 工作日历（organization.attendance） | AGG-108 标准工时版本（OBJ-28 工作日历下的版本化配置属性，不是新的一级对象；每版本一个聚合 + 排除约束） | child | 否 |
| attendance_records | 提议·people·未签发 | B-27 请假与加班（organization.leave） | AGG-69 考勤记录（OBJ-70；来源系统待定，R2） | root | 否 |
| leave_requests | 提议·people·未签发 | B-27 请假与加班（organization.leave） | AGG-70 请假（OBJ-68） | root | 否 |
| leave_request_day_splits | 提议·people·未签发 | B-27 请假与加班（organization.leave） | AGG-70 请假（OBJ-68） | child | 否 |
| leave_balance_accounts | 提议·people·未签发 | B-27 请假与加班（organization.leave） | AGG-71 假期余额账户（技术；人员 × 假期类型 × 年度） | root | 否 |
| leave_balance_holds | 提议·people·未签发 | B-27 请假与加班（organization.leave） | AGG-71 假期余额账户（技术；人员 × 假期类型 × 年度） | child | 否 |
| leave_comp_grants | 提议·people·未签发 | B-27 请假与加班（organization.leave） | AGG-71 假期余额账户（技术；人员 × 假期类型 × 年度） | child | 否 |
| overtime_requests | 提议·people·未签发 | B-27 请假与加班（organization.leave） | AGG-72 加班（OBJ-72） | root | 否 |
| monthly_attendance_confirmations | 提议·people·未签发 | B-27 请假与加班（organization.leave） | AGG-97 月度考勤确认（OBJ-111，人员 × 月；阶段门走查补为运行单据，§4.3.2；第 ② 步回补一级对象检验后为信息图次级对象，intent 082311） | root | 否 |
| monthly_attendance_confirmation_lines | 提议·people·未签发 | B-27 请假与加班（organization.leave） | AGG-97 月度考勤确认（OBJ-111，人员 × 月；阶段门走查补为运行单据，§4.3.2；第 ② 步回补一级对象检验后为信息图次级对象，intent 082311） | child | 否 |
| monthly_attendance_adjustments | 提议·people·未签发 | B-27 请假与加班（organization.leave） | AGG-97 月度考勤确认（OBJ-111，人员 × 月；阶段门走查补为运行单据，§4.3.2；第 ② 步回补一级对象检验后为信息图次级对象，intent 082311） | child | 否 |
| leave_types | 提议·people·未签发 | B-27 请假与加班（organization.leave） | AGG-118 假期类型与额度规则（配置型，版本化） | root | 否 |
| leave_entitlement_rule_versions | 提议·people·未签发 | B-27 请假与加班（organization.leave） | AGG-118 假期类型与额度规则（配置型，版本化） | version | 否 |
| payroll_settlements | 提议·people·未签发 | B-15 薪酬（organization.payroll） | AGG-73 月度薪酬结算（OBJ-73，法人 × 月）（扣缴义务人口径随 Q27） | root | 否 |
| payroll_settlement_rule_refs | 提议·people·未签发 | B-15 薪酬（organization.payroll） | AGG-73 月度薪酬结算（OBJ-73，法人 × 月）（扣缴义务人口径随 Q27） | link | 否 |
| payroll_settlement_lines | 提议·people·未签发 | B-15 薪酬（organization.payroll） | AGG-73 月度薪酬结算（OBJ-73，法人 × 月）（扣缴义务人口径随 Q27） | child | 否 |
| payroll_line_items | 提议·people·未签发 | B-15 薪酬（organization.payroll） | AGG-73 月度薪酬结算（OBJ-73，法人 × 月）（扣缴义务人口径随 Q27） | child | 否 |
| insurance_enrollments | 提议·people·未签发 | B-15 薪酬（organization.payroll） | AGG-74 社保与公积金参保（OBJ-62） | root | 否 |
| insurance_enrollment_versions | 提议·people·未签发 | B-15 薪酬（organization.payroll） | AGG-74 社保与公积金参保（OBJ-62） | version | 否 |
| incentive_plans | 提议·people·未签发 | B-15 薪酬（organization.payroll） | AGG-75 激励方案版本（OBJ-74） | root | 否 |
| incentive_plan_versions | 提议·people·未签发 | B-15 薪酬（organization.payroll） | AGG-75 激励方案版本（OBJ-74） | version | 否 |
| incentive_plan_version_quotas | 提议·people·未签发 | B-15 薪酬（organization.payroll） | AGG-75 激励方案版本（OBJ-74） | child | 否 |
| bonus_determinations | 提议·people·未签发 | B-15 薪酬（organization.payroll） | AGG-76 奖金核定（OBJ-104） | root | 否 |
| bonus_determination_evidences | 提议·people·未签发 | B-15 薪酬（organization.payroll） | AGG-76 奖金核定（OBJ-104） | link | 否 |
| payroll_profiles | 提议·people·未签发 | B-15 薪酬（organization.payroll） | AGG-93 人员薪酬档案（从原 AGG-62 移出：薪酬属性版本、专项附加扣除年度属性） | root | 否 |
| payroll_profile_versions | 提议·people·未签发 | B-15 薪酬（organization.payroll） | AGG-93 人员薪酬档案（从原 AGG-62 移出：薪酬属性版本、专项附加扣除年度属性） | version | 否 |
| payroll_special_deduction_declarations | 提议·people·未签发 | B-15 薪酬（organization.payroll） | AGG-120 专项附加扣除声明（从 AGG-93 拆出） | root | 否 |
| payroll_special_deduction_items | 提议·people·未签发 | B-15 薪酬（organization.payroll） | AGG-120 专项附加扣除声明（从 AGG-93 拆出） | child | 否 |
| tax_social_filings | 提议·people·未签发 | B-15 薪酬（organization.payroll） | AGG-95 个税扣缴与社保公积金申报（OBJ-92 的人事部分；个税扣缴义务人口径随 Q27） | root | 否 |
| tax_social_filing_lines | 提议·people·未签发 | B-15 薪酬（organization.payroll） | AGG-95 个税扣缴与社保公积金申报（OBJ-92 的人事部分；个税扣缴义务人口径随 Q27） | child | 否 |
| tax_social_filing_receipts | 提议·people·未签发 | B-15 薪酬（organization.payroll） | AGG-95 个税扣缴与社保公积金申报（OBJ-92 的人事部分；个税扣缴义务人口径随 Q27） | child | 否 |
| tax_social_payments | 提议·people·未签发 | B-15 薪酬（organization.payroll） | AGG-95 个税扣缴与社保公积金申报（OBJ-92 的人事部分；个税扣缴义务人口径随 Q27） | child | 否 |
| payroll_rule_versions | 提议·people·未签发 | B-15 薪酬（organization.payroll） | AGG-119 薪酬规则版本（配置型，版本化：个税税率表、社保公积金比例与基数上下限等） | root | 否 |
| income_tax_brackets | 提议·people·未签发 | B-15 薪酬（organization.payroll） | AGG-119 薪酬规则版本（配置型，版本化：个税税率表、社保公积金比例与基数上下限等） | child | 否 |
| tax_deduction_standards | 提议·people·未签发 | B-15 薪酬（organization.payroll） | AGG-119 薪酬规则版本（配置型，版本化：个税税率表、社保公积金比例与基数上下限等） | child | 否 |
| contribution_rate_rules | 提议·people·未签发 | B-15 薪酬（organization.payroll） | AGG-119 薪酬规则版本（配置型，版本化：个税税率表、社保公积金比例与基数上下限等） | child | 否 |
| payroll_calculation_parameters | 提议·people·未签发 | B-15 薪酬（organization.payroll） | AGG-119 薪酬规则版本（配置型，版本化：个税税率表、社保公积金比例与基数上下限等） | child | 否 |
| business_plans | 提议·people·未签发 | B-18 计划与预算（operations.planning） | AGG-79 经营计划版本（OBJ-100） | root | 否 |
| business_plan_versions | 提议·people·未签发 | B-18 计划与预算（operations.planning） | AGG-79 经营计划版本（OBJ-100） | version | 否 |
| plan_target_values | 提议·people·未签发 | B-18 计划与预算（operations.planning） | AGG-79 经营计划版本（OBJ-100） | child | 否 |
| business_reviews | 提议·people·未签发 | B-18 计划与预算（operations.planning） | AGG-79 经营计划版本（OBJ-100） | child | 否 |
| business_review_variance_explanations | 提议·people·未签发 | B-18 计划与预算（operations.planning） | AGG-79 经营计划版本（OBJ-100） | child | 否 |
| action_items | 提议·people·未签发 | B-18 计划与预算（operations.planning） | AGG-79 经营计划版本（OBJ-100） | child | 否 |
| budget_accounts | 提议·people·未签发 | B-18 计划与预算（operations.planning） | AGG-80 预算账户（OBJ-100 的类型「预算」）（拦截或预警**待定**） | root | 否 |
| budget_account_adjustments | 提议·people·未签发 | B-18 计划与预算（operations.planning） | AGG-80 预算账户（OBJ-100 的类型「预算」）（拦截或预警**待定**） | child | 否 |
| budget_reservations | 提议·people·未签发 | B-18 计划与预算（operations.planning） | AGG-80 预算账户（OBJ-100 的类型「预算」）（拦截或预警**待定**） | child | 否 |
| knowledge_entries | 提议·people·未签发 | B-30 知识（km.knowledge） | AGG-107 知识条目（OBJ-110） | root | 否 |
| knowledge_entry_versions | 提议·people·未签发 | B-30 知识（km.knowledge） | AGG-107 知识条目（OBJ-110） | version | 否 |
| knowledge_entry_reviews | 提议·people·未签发 | B-30 知识（km.knowledge） | AGG-107 知识条目（OBJ-110） | child | 否 |
| seal_applications | 提议·people·未签发 | B-31 行政事务（支撑流程，S-09 已定）（待定（B31 模块命名空间未在聚合稿中）） | AGG-113 用印申请 | root | 否 |
| seal_application_documents | 提议·people·未签发 | B-31 行政事务（支撑流程，S-09 已定）（待定（B31 模块命名空间未在聚合稿中）） | AGG-113 用印申请 | child | 否 |
| seal_application_source_documents | 提议·people·未签发 | B-31 行政事务（支撑流程，S-09 已定）（待定（B31 模块命名空间未在聚合稿中）） | AGG-113 用印申请 | child | 否 |
| seal_usage_records | 提议·people·未签发 | B-31 行政事务（支撑流程，S-09 已定）（待定（B31 模块命名空间未在聚合稿中）） | AGG-113 用印申请 | child | 否 |
| it_requests | 提议·people·未签发 | B-31 行政事务（支撑流程，S-09 已定）（待定（B31 模块命名空间未在聚合稿中）） | AGG-114 IT 需求工单 | root | 否 |
| it_request_handlings | 提议·people·未签发 | B-31 行政事务（支撑流程，S-09 已定）（待定（B31 模块命名空间未在聚合稿中）） | AGG-114 IT 需求工单 | child | 否 |
| it_asset_use_ledgers | 提议·people·未签发 | B-31 行政事务（支撑流程，S-09 已定）（待定（B31 模块命名空间未在聚合稿中）） | AGG-115 IT 资产使用记录（领用 / 调拨 / 归还 / 维修工单） | root | 否 |
| it_asset_use_requests | 提议·people·未签发 | B-31 行政事务（支撑流程，S-09 已定）（待定（B31 模块命名空间未在聚合稿中）） | AGG-115 IT 资产使用记录（领用 / 调拨 / 归还 / 维修工单） | child | 否 |
| it_asset_use_periods | 提议·people·未签发 | B-31 行政事务（支撑流程，S-09 已定）（待定（B31 模块命名空间未在聚合稿中）） | AGG-115 IT 资产使用记录（领用 / 调拨 / 归还 / 维修工单） | child | 否 |
| it_asset_repair_orders | 提议·people·未签发 | B-31 行政事务（支撑流程，S-09 已定）（待定（B31 模块命名空间未在聚合稿中）） | AGG-121 IT 资产维修单（从 AGG-115 拆出） | root | 否 |
| employee_activity_requests | 提议·people·未签发 | B-31 行政事务（支撑流程，S-09 已定）（待定（B31 模块命名空间未在聚合稿中）） | AGG-116 员工活动申请 | root | 否 |
| employee_activity_participants | 提议·people·未签发 | B-31 行政事务（支撑流程，S-09 已定）（待定（B31 模块命名空间未在聚合稿中）） | AGG-116 员工活动申请 | child | 否 |
| employee_activity_types | 提议·people·未签发 | B-31 行政事务（支撑流程，S-09 已定）（待定（B31 模块命名空间未在聚合稿中）） | AGG-116 员工活动申请 | child | 否 |

## revenue 组（162 表）

| 表名 | 来源 | BC | 聚合 | 表角色 | 技术表 |
|---|---|---|---|---|---|
| counterparties | 提议·revenue·未签发 | B-01 往来单位（含外部工程登记）（masterdata.counterparty） | AGG-01 往来单位（OBJ-01） | root | 否 |
| counterparty_versions | 提议·revenue·未签发 | B-01 往来单位（含外部工程登记）（masterdata.counterparty） | AGG-01 往来单位（OBJ-01） | version | 否 |
| counterparty_parent_links | 提议·revenue·未签发 | B-01 往来单位（含外部工程登记）（masterdata.counterparty） | AGG-01 往来单位（OBJ-01） | link | 否 |
| counterparty_roles | 提议·revenue·未签发 | B-01 往来单位（含外部工程登记）（masterdata.counterparty） | AGG-01 往来单位（OBJ-01） | child | 否 |
| counterparty_role_admission_changes | 提议·revenue·未签发 | B-01 往来单位（含外部工程登记）（masterdata.counterparty） | AGG-01 往来单位（OBJ-01） | child | 否 |
| counterparty_customer_tier_assessments | 提议·revenue·未签发 | B-01 往来单位（含外部工程登记）（masterdata.counterparty） | AGG-01 往来单位（OBJ-01） | child | 否 |
| counterparty_contacts | 提议·revenue·未签发 | B-01 往来单位（含外部工程登记）（masterdata.counterparty） | AGG-01 往来单位（OBJ-01） | child | 否 |
| counterparty_bank_accounts | 提议·revenue·未签发 | B-01 往来单位（含外部工程登记）（masterdata.counterparty） | AGG-01 往来单位（OBJ-01） | child | 否 |
| qualification_certificates | 提议·revenue·未签发 | B-01 往来单位（含外部工程登记）（masterdata.counterparty） | AGG-02 资质证书（OBJ-65） | root | 否 |
| qualification_certificate_versions | 提议·revenue·未签发 | B-01 往来单位（含外部工程登记）（masterdata.counterparty） | AGG-02 资质证书（OBJ-65） | version | 否 |
| qualification_verifications | 提议·revenue·未签发 | B-01 往来单位（含外部工程登记）（masterdata.counterparty） | AGG-02 资质证书（OBJ-65） | child | 否 |
| construction_projects | 提议·revenue·未签发 | B-01 往来单位（含外部工程登记）（masterdata.counterparty） | AGG-33 建设工程（OBJ-46，外部引用登记，外部工程登记部分；R5：竣工在用后即受检对象） | root | 否 |
| construction_project_versions | 提议·revenue·未签发 | B-01 往来单位（含外部工程登记）（masterdata.counterparty） | AGG-33 建设工程（OBJ-46，外部引用登记，外部工程登记部分；R5：竣工在用后即受检对象） | version | 否 |
| construction_project_parts | 提议·revenue·未签发 | B-01 往来单位（含外部工程登记）（masterdata.counterparty） | AGG-33 建设工程（OBJ-46，外部引用登记，外部工程登记部分；R5：竣工在用后即受检对象） | child | 否 |
| construction_project_party_roles | 提议·revenue·未签发 | B-01 往来单位（含外部工程登记）（masterdata.counterparty） | AGG-33 建设工程（OBJ-46，外部引用登记，外部工程登记部分；R5：竣工在用后即受检对象） | link | 否 |
| construction_project_participations | 提议·revenue·未签发 | B-01 往来单位（含外部工程登记）（masterdata.counterparty） | AGG-33 建设工程（OBJ-46，外部引用登记，外部工程登记部分；R5：竣工在用后即受检对象） | link | 否 |
| tender_registrations | 提议·revenue·未签发 | B-01 往来单位（含外部工程登记）（masterdata.counterparty） | REF-01  | root | 否 |
| tender_registration_versions | 提议·revenue·未签发 | B-01 往来单位（含外部工程登记）（masterdata.counterparty） | REF-01  | version | 否 |
| tender_lots | 提议·revenue·未签发 | B-01 往来单位（含外部工程登记）（masterdata.counterparty） | REF-01  | child | 否 |
| service_product_versions | 提议·revenue·未签发 | B-02 服务与定价目录（masterdata.catalog） | AGG-03 服务产品版本（OBJ-24） | root | 否 |
| service_product_version_phases | 提议·revenue·未签发 | B-02 服务与定价目录（masterdata.catalog） | AGG-03 服务产品版本（OBJ-24） | child | 否 |
| phase_type_versions | 提议·revenue·未签发 | B-02 服务与定价目录（masterdata.catalog） | AGG-04 阶段类型版本（OBJ-25） | root | 否 |
| phase_type_version_deliverables | 提议·revenue·未签发 | B-02 服务与定价目录（masterdata.catalog） | AGG-04 阶段类型版本（OBJ-25） | child | 否 |
| phase_type_version_review_requirements | 提议·revenue·未签发 | B-02 服务与定价目录（masterdata.catalog） | AGG-04 阶段类型版本（OBJ-25） | child | 否 |
| standard_cost_rate_card_versions | 提议·revenue·未签发 | B-02 服务与定价目录（masterdata.catalog） | AGG-05 标准成本费率表版本（OBJ-26） | root | 否 |
| standard_cost_rate_card_entries | 提议·revenue·未签发 | B-02 服务与定价目录（masterdata.catalog） | AGG-05 标准成本费率表版本（OBJ-26） | child | 否 |
| billing_coefficient_versions | 提议·revenue·未签发 | B-02 服务与定价目录（masterdata.catalog） | AGG-06 计费系数版本（OBJ-27）（**待定** Q18：粒度） | root | 否 |
| billing_coefficient_entries | 提议·revenue·未签发 | B-02 服务与定价目录（masterdata.catalog） | AGG-06 计费系数版本（OBJ-27）（**待定** Q18：粒度） | child | 否 |
| agreement_price_list_versions | 提议·revenue·未签发 | B-02 服务与定价目录（masterdata.catalog） | AGG-07 协议价目（OBJ-05 的类型「协议价目」） | root | 否 |
| agreement_price_list_items | 提议·revenue·未签发 | B-02 服务与定价目录（masterdata.catalog） | AGG-07 协议价目（OBJ-05 的类型「协议价目」） | child | 否 |
| marketing_campaigns | 提议·revenue·未签发 | B-03 售前（presales.opportunity） | AGG-08 市场活动（OBJ-16） | root | 否 |
| leads | 提议·revenue·未签发 | B-03 售前（presales.opportunity） | AGG-09 线索（OBJ-17） | root | 否 |
| lead_contacts | 提议·revenue·未签发 | B-03 售前（presales.opportunity） | AGG-09 线索（OBJ-17） | child | 否 |
| lead_status_changes | 提议·revenue·未签发 | B-03 售前（presales.opportunity） | AGG-09 线索（OBJ-17） | child | 否 |
| opportunities | 提议·revenue·未签发 | B-03 售前（presales.opportunity） | AGG-10 商机（OBJ-18） | root | 否 |
| opportunity_team_members | 提议·revenue·未签发 | B-03 售前（presales.opportunity） | AGG-10 商机（OBJ-18） | child | 否 |
| opportunity_entrustment_evidences | 提议·revenue·未签发 | B-03 售前（presales.opportunity） | AGG-10 商机（OBJ-18） | child | 否 |
| opportunity_bid_decisions | 提议·revenue·未签发 | B-03 售前（presales.opportunity） | AGG-10 商机（OBJ-18） | child | 否 |
| opportunity_loss_records | 提议·revenue·未签发 | B-03 售前（presales.opportunity） | AGG-10 商机（OBJ-18） | child | 否 |
| bids | 提议·revenue·未签发 | B-03 售前（presales.opportunity） | AGG-11 投标（OBJ-20） | root | 否 |
| bid_consortium_agreements | 提议·revenue·未签发 | B-03 售前（presales.opportunity） | AGG-11 投标（OBJ-20） | child | 否 |
| bid_consortium_members | 提议·revenue·未签发 | B-03 售前（presales.opportunity） | AGG-11 投标（OBJ-20） | child | 否 |
| bid_prequalification_results | 提议·revenue·未签发 | B-03 售前（presales.opportunity） | AGG-11 投标（OBJ-20） | child | 否 |
| bid_commitment_snapshots | 提议·revenue·未签发 | B-03 售前（presales.opportunity） | AGG-11 投标（OBJ-20） | child | 否 |
| bid_award_notices | 提议·revenue·未签发 | B-03 售前（presales.opportunity） | AGG-11 投标（OBJ-20） | child | 否 |
| bid_status_changes | 提议·revenue·未签发 | B-03 售前（presales.opportunity） | AGG-11 投标（OBJ-20） | child | 否 |
| quote_versions | 提议·revenue·未签发 | B-03 售前（presales.opportunity） | AGG-12 报价版本（OBJ-21） | root | 否 |
| quote_estimate_items | 提议·revenue·未签发 | B-03 售前（presales.opportunity） | AGG-12 报价版本（OBJ-21） | child | 否 |
| quote_applied_versions | 提议·revenue·未签发 | B-03 售前（presales.opportunity） | AGG-12 报价版本（OBJ-21） | link | 否 |
| quote_reference_links | 提议·revenue·未签发 | B-03 售前（presales.opportunity） | AGG-12 报价版本（OBJ-21） | link | 否 |
| independence_checks | 提议·revenue·未签发 | B-03 售前（presales.opportunity） | AGG-18 独立性检查（OBJ-23）（D-4 用户已定 b：售前拦截，intent 061232） | root | 否 |
| independence_check_consents | 提议·revenue·未签发 | B-03 售前（presales.opportunity） | AGG-18 独立性检查（OBJ-23）（D-4 用户已定 b：售前拦截，intent 061232） | child | 否 |
| customer_activities | 提议·revenue·未签发 | B-03 售前（presales.opportunity） | AGG-102 客户活动（OBJ-105） | root | 否 |
| customer_activity_changes | 提议·revenue·未签发 | B-03 售前（presales.opportunity） | AGG-102 客户活动（OBJ-105） | child | 否 |
| agreements | 提议·revenue·未签发 | B-05 合同（contract.agreement） | AGG-13 协议（OBJ-05：项目合同、框架协议、任务订单） | root | 否 |
| agreement_baselines | 提议·revenue·未签发 | B-05 合同（contract.agreement） | AGG-13 协议（OBJ-05：项目合同、框架协议、任务订单） | version | 否 |
| agreement_parties | 提议·revenue·未签发 | B-05 合同（contract.agreement） | AGG-13 协议（OBJ-05：项目合同、框架协议、任务订单） | child | 否 |
| agreement_scope_items | 提议·revenue·未签发 | B-05 合同（contract.agreement） | AGG-13 协议（OBJ-05：项目合同、框架协议、任务订单） | child | 否 |
| agreement_payment_term_items | 提议·revenue·未签发 | B-05 合同（contract.agreement） | AGG-13 协议（OBJ-05：项目合同、框架协议、任务订单） | child | 否 |
| agreement_performance_obligations | 提议·revenue·未签发 | B-05 合同（contract.agreement） | AGG-13 协议（OBJ-05：项目合同、框架协议、任务订单） | child | 否 |
| agreement_project_links | 提议·revenue·未签发 | B-05 合同（contract.agreement） | AGG-13 协议（OBJ-05：项目合同、框架协议、任务订单） | link | 否 |
| agreement_commitment_comparisons | 提议·revenue·未签发 | B-05 合同（contract.agreement） | AGG-13 协议（OBJ-05：项目合同、框架协议、任务订单） | child | 否 |
| agreement_deemed_consent_terms | 提议·revenue·未签发 | B-05 合同（contract.agreement） | AGG-13 协议（OBJ-05：项目合同、框架协议、任务订单） | child | 否 |
| agreement_owner_representatives | 提议·revenue·未签发 | B-05 合同（contract.agreement） | AGG-13 协议（OBJ-05：项目合同、框架协议、任务订单） | child | 否 |
| agreement_settlement_statements | 提议·revenue·未签发 | B-05 合同（contract.agreement） | AGG-13 协议（OBJ-05：项目合同、框架协议、任务订单） | child | 否 |
| agreement_status_changes | 提议·revenue·未签发 | B-05 合同（contract.agreement） | AGG-13 协议（OBJ-05：项目合同、框架协议、任务订单） | child | 否 |
| agreement_archive_records | 提议·revenue·未签发 | B-05 合同（contract.agreement） | AGG-13 协议（OBJ-05：项目合同、框架协议、任务订单） | child | 否 |
| payment_term_fulfillments | 提议·revenue·未签发 | B-05 合同（contract.agreement） | AGG-13 协议（OBJ-05：项目合同、框架协议、任务订单） | child | 否 |
| framework_capacities | 提议·revenue·未签发 | B-05 合同（contract.agreement） | AGG-14 框架协议限额（框架限额占用，词表 `framework_capacity_reservation`） | root | 否 |
| framework_capacity_reservations | 提议·revenue·未签发 | B-05 合同（contract.agreement） | AGG-14 框架协议限额（框架限额占用，词表 `framework_capacity_reservation`） | child | 否 |
| agreement_changes | 提议·revenue·未签发 | B-05 合同（contract.agreement） | AGG-15 协议变更（OBJ-09：合同变更、签证） | root | 否 |
| agreement_change_claimed_items | 提议·revenue·未签发 | B-05 合同（contract.agreement） | AGG-15 协议变更（OBJ-09：合同变更、签证） | link | 否 |
| claims | 提议·revenue·未签发 | B-05 合同（contract.agreement） | AGG-16 索赔（OBJ-10） | root | 否 |
| claim_determinations | 提议·revenue·未签发 | B-05 合同（contract.agreement） | AGG-16 索赔（OBJ-10） | child | 否 |
| risk_delivery_authorizations | 提议·revenue·未签发 | B-05 合同（contract.agreement） | AGG-17 风险交付授权（OBJ-12）（口径与转正时点待流程梳理，C-56） | root | 否 |
| risk_delivery_authorization_evidence_links | 提议·revenue·未签发 | B-05 合同（contract.agreement） | AGG-17 风险交付授权（OBJ-12）（口径与转正时点待流程梳理，C-56） | link | 否 |
| projects | 提议·revenue·未签发 | B-04 交付执行（delivery.execution） | AGG-19 项目（OBJ-29）（**待定** Q09 状态机、Q11 承担法人） | root | 否 |
| project_attribute_versions | 提议·revenue·未签发 | B-04 交付执行（delivery.execution） | AGG-19 项目（OBJ-29）（**待定** Q09 状态机、Q11 承担法人） | version | 否 |
| project_source_opportunities | 提议·revenue·未签发 | B-04 交付执行（delivery.execution） | AGG-19 项目（OBJ-29）（**待定** Q09 状态机、Q11 承担法人） | link | 否 |
| project_status_events | 提议·revenue·未签发 | B-04 交付执行（delivery.execution） | AGG-19 项目（OBJ-29）（**待定** Q09 状态机、Q11 承担法人） | child | 否 |
| wbs_trees | 提议·revenue·未签发 | B-04 交付执行（delivery.execution） | AGG-20 项目 WBS（OBJ-30，每项目一棵） | root | 否 |
| wbs_nodes | 提议·revenue·未签发 | B-04 交付执行（delivery.execution） | AGG-20 项目 WBS（OBJ-30，每项目一棵） | child | 否 |
| wbs_node_versions | 提议·revenue·未签发 | B-04 交付执行（delivery.execution） | AGG-20 项目 WBS（OBJ-30，每项目一棵） | version | 否 |
| delivery_plan_versions | 提议·revenue·未签发 | B-04 交付执行（delivery.execution） | AGG-21 交付计划版本（OBJ-31） | root | 否 |
| milestones | 提议·revenue·未签发 | B-04 交付执行（delivery.execution） | AGG-21 交付计划版本（OBJ-31） | child | 否 |
| project_budget_versions | 提议·revenue·未签发 | B-04 交付执行（delivery.execution） | AGG-22 项目预算版本（OBJ-32） | root | 否 |
| project_budget_lines | 提议·revenue·未签发 | B-04 交付执行（delivery.execution） | AGG-22 项目预算版本（OBJ-32） | child | 否 |
| completion_estimates | 提议·revenue·未签发 | B-04 交付执行（delivery.execution） | AGG-23 完工估算（OBJ-33） | root | 否 |
| progress_estimates | 提议·revenue·未签发 | B-04 交付执行（delivery.execution） | AGG-23 完工估算（OBJ-33） | child | 否 |
| project_teams | 提议·revenue·未签发 | B-04 交付执行（delivery.execution） | AGG-24 项目团队（OBJ-34）（**待定** Q13：放项目内还是独立聚合，以及是否决定工时范围与审批人） | root | 否 |
| project_team_members | 提议·revenue·未签发 | B-04 交付执行（delivery.execution） | AGG-24 项目团队（OBJ-34）（**待定** Q13：放项目内还是独立聚合，以及是否决定工时范围与审批人） | child | 否 |
| resource_requests | 提议·revenue·未签发 | B-04 交付执行（delivery.execution） | AGG-25 资源需求（OBJ-44） | root | 否 |
| resource_assignments | 提议·revenue·未签发 | B-04 交付执行（delivery.execution） | AGG-26 资源分配（OBJ-45） | root | 否 |
| extension_requests | 提议·revenue·未签发 | B-04 交付执行（delivery.execution） | AGG-31 延期申请（OBJ-41） | root | 否 |
| extension_request_events | 提议·revenue·未签发 | B-04 交付执行（delivery.execution） | AGG-31 延期申请（OBJ-41） | child | 否 |
| project_retrospectives | 提议·revenue·未签发 | B-04 交付执行（delivery.execution） | AGG-103 项目复盘（OBJ-107） | root | 否 |
| retrospective_reviews | 提议·revenue·未签发 | B-04 交付执行（delivery.execution） | AGG-103 项目复盘（OBJ-107） | child | 否 |
| project_risk_issues | 提议·revenue·未签发 | B-04 交付执行（delivery.execution） | AGG-104 项目风险与问题（OBJ-108） | root | 否 |
| risk_issue_responses | 提议·revenue·未签发 | B-04 交付执行（delivery.execution） | AGG-104 项目风险与问题（OBJ-108） | child | 否 |
| timesheets | 提议·revenue·未签发 | B-06 工时（cost.timesheet） | AGG-39 工时单（OBJ-66，已建 `Timesheet`） | root | 否 |
| time_entries | 提议·revenue·未签发 | B-06 工时（cost.timesheet） | AGG-39 工时单（OBJ-66，已建 `Timesheet`） | child | 否 |
| time_entry_validations | 提议·revenue·未签发 | B-06 工时（cost.timesheet） | AGG-39 工时单（OBJ-66，已建 `Timesheet`） | child | 否 |
| deliverables | 提议·revenue·未签发 | B-28 质量与认可（delivery.acceptance） | AGG-27 交付物（OBJ-35） | root | 否 |
| deliverable_file_versions | 提议·revenue·未签发 | B-28 质量与认可（delivery.acceptance） | AGG-27 交付物（OBJ-35） | child | 否 |
| deliverable_internal_reviews | 提议·revenue·未签发 | B-28 质量与认可（delivery.acceptance） | AGG-27 交付物（OBJ-35） | child | 否 |
| deliverable_issuances | 提议·revenue·未签发 | B-28 质量与认可（delivery.acceptance） | AGG-27 交付物（OBJ-35） | child | 否 |
| submission_rounds | 提议·revenue·未签发 | B-28 质量与认可（delivery.acceptance） | AGG-27 交付物（OBJ-35） | child | 否 |
| delivery_records | 提议·revenue·未签发 | B-28 质量与认可（delivery.acceptance） | AGG-27 交付物（OBJ-35） | child | 否 |
| customer_acceptance_records | 提议·revenue·未签发 | B-28 质量与认可（delivery.acceptance） | AGG-27 交付物（OBJ-35） | child | 否 |
| no_objection_declarations | 提议·revenue·未签发 | B-28 质量与认可（delivery.acceptance） | AGG-27 交付物（OBJ-35） | child | 否 |
| deliverable_seals | 提议·revenue·未签发 | B-28 质量与认可（delivery.acceptance） | AGG-27 交付物（OBJ-35） | child | 否 |
| nonconformities | 提议·revenue·未签发 | B-28 质量与认可（delivery.acceptance） | AGG-28 不符合项（OBJ-38，类型：我方成果 / 客户资产） | root | 否 |
| nonconformity_events | 提议·revenue·未签发 | B-28 质量与认可（delivery.acceptance） | AGG-28 不符合项（OBJ-38，类型：我方成果 / 客户资产） | child | 否 |
| nonconformity_evidence_references | 提议·revenue·未签发 | B-28 质量与认可（delivery.acceptance） | AGG-28 不符合项（OBJ-38，类型：我方成果 / 客户资产） | link | 否 |
| hazard_notifications | 提议·revenue·未签发 | B-28 质量与认可（delivery.acceptance） | AGG-28 不符合项（OBJ-38，类型：我方成果 / 客户资产） | child | 否 |
| rework_recognitions | 提议·revenue·未签发 | B-28 质量与认可（delivery.acceptance） | AGG-29 返工认定（OBJ-39） | root | 否 |
| design_changes | 提议·revenue·未签发 | B-28 质量与认可（delivery.acceptance） | AGG-30 设计变更（OBJ-40） | root | 否 |
| design_change_deliverables | 提议·revenue·未签发 | B-28 质量与认可（delivery.acceptance） | AGG-30 设计变更（OBJ-40） | link | 否 |
| owner_provided_materials | 提议·revenue·未签发 | B-28 质量与认可（delivery.acceptance） | AGG-32 发包人提供资料（OBJ-42） | root | 否 |
| evidence_items | 提议·revenue·未签发 | B-28 质量与认可（delivery.acceptance） | AGG-38 证据（OBJ-53）（**待定** Q29：窄口径为本 BC 聚合；宽口径为一级，位置另定但须满足证据规则；保存期随 Q34） | root | 否 |
| evidence_files | 提议·revenue·未签发 | B-28 质量与认可（delivery.acceptance） | AGG-38 证据（OBJ-53）（**待定** Q29：窄口径为本 BC 聚合；宽口径为一级，位置另定但须满足证据规则；保存期随 Q34） | child | 否 |
| evidence_seals | 提议·revenue·未签发 | B-28 质量与认可（delivery.acceptance） | AGG-38 证据（OBJ-53）（**待定** Q29：窄口径为本 BC 聚合；宽口径为一级，位置另定但须满足证据规则；保存期随 Q34） | child | 否 |
| evidence_integrity_checks | 提议·revenue·未签发 | B-28 质量与认可（delivery.acceptance） | AGG-38 证据（OBJ-53）（**待定** Q29：窄口径为本 BC 聚合；宽口径为一级，位置另定但须满足证据规则；保存期随 Q34） | child | 否 |
| evidence_preservation_holds | 提议·revenue·未签发 | B-28 质量与认可（delivery.acceptance） | AGG-38 证据（OBJ-53）（**待定** Q29：窄口径为本 BC 聚合；宽口径为一级，位置另定但须满足证据规则；保存期随 Q34） | child | 否 |
| evidence_voidings | 提议·revenue·未签发 | B-28 质量与认可（delivery.acceptance） | AGG-38 证据（OBJ-53）（**待定** Q29：窄口径为本 BC 聚合；宽口径为一级，位置另定但须满足证据规则；保存期随 Q34） | child | 否 |
| evidence_links | 提议·revenue·未签发 | B-28 质量与认可（delivery.acceptance） | AGG-110 证据关联（技术聚合，intent 114200 F-1；记「证据 ID → 交付物 / WBS 阶段节点」挂接，证据本身与采集人不在此处） | root | 否 |
| satisfaction_surveys | 提议·revenue·未签发 | B-28 质量与认可（delivery.acceptance） | AGG-105 客户满意度调查（OBJ-106） | root | 否 |
| satisfaction_scores | 提议·revenue·未签发 | B-28 质量与认可（delivery.acceptance） | AGG-105 客户满意度调查（OBJ-106） | child | 否 |
| inspection_schedules | 提议·revenue·未签发 | B-26 检查（delivery.inspection） | AGG-34 检查计划（OBJ-48，仅 IDI） | root | 否 |
| inspections | 提议·revenue·未签发 | B-26 检查（delivery.inspection） | AGG-35 检查（OBJ-49） | root | 否 |
| inspection_items | 提议·revenue·未签发 | B-26 检查（delivery.inspection） | AGG-35 检查（OBJ-49） | child | 否 |
| inspection_item_corrections | 提议·revenue·未签发 | B-26 检查（delivery.inspection） | AGG-35 检查（OBJ-49） | child | 否 |
| inspection_assignments | 提议·revenue·未签发 | B-26 检查（delivery.inspection） | AGG-35 检查（OBJ-49） | link | 否 |
| inspection_report_links | 提议·revenue·未签发 | B-26 检查（delivery.inspection） | AGG-35 检查（OBJ-49） | link | 否 |
| inspection_checklist_template_versions | 提议·revenue·未签发 | B-26 检查（delivery.inspection） | AGG-36 检查表模板版本（OBJ-50） | root | 否 |
| inspection_checklist_items | 提议·revenue·未签发 | B-26 检查（delivery.inspection） | AGG-36 检查表模板版本（OBJ-50） | child | 否 |
| external_testings | 提议·revenue·未签发 | B-26 检查（delivery.inspection） | AGG-37 外部检测（OBJ-52，仅 IDI） | root | 否 |
| external_testing_reports | 提议·revenue·未签发 | B-26 检查（delivery.inspection） | AGG-37 外部检测（OBJ-52，仅 IDI） | child | 否 |
| billing_revenue_capacities | 提议·revenue·未签发 | B-08 开票与收入（receivables.wip） | AGG-44 开票与收入额度（技术；每协议一个：付款条件项可开票额度与累计开票、履约义务分摊价格与累计确认、收入确认行；原 AGG-48 履约义务收入台账并入）（承担法人随 Q11） | root | 否 |
| payment_term_capacities | 提议·revenue·未签发 | B-08 开票与收入（receivables.wip） | AGG-44 开票与收入额度（技术；每协议一个：付款条件项可开票额度与累计开票、履约义务分摊价格与累计确认、收入确认行；原 AGG-48 履约义务收入台账并入）（承担法人随 Q11） | child | 否 |
| performance_obligation_capacities | 提议·revenue·未签发 | B-08 开票与收入（receivables.wip） | AGG-44 开票与收入额度（技术；每协议一个：付款条件项可开票额度与累计开票、履约义务分摊价格与累计确认、收入确认行；原 AGG-48 履约义务收入台账并入）（承担法人随 Q11） | child | 否 |
| billing_reservations | 提议·revenue·未签发 | B-08 开票与收入（receivables.wip） | AGG-44 开票与收入额度（技术；每协议一个：付款条件项可开票额度与累计开票、履约义务分摊价格与累计确认、收入确认行；原 AGG-48 履约义务收入台账并入）（承担法人随 Q11） | child | 否 |
| revenue_recognition_reservations | 提议·revenue·未签发 | B-08 开票与收入（receivables.wip） | AGG-44 开票与收入额度（技术；每协议一个：付款条件项可开票额度与累计开票、履约义务分摊价格与累计确认、收入确认行；原 AGG-48 履约义务收入台账并入）（承担法人随 Q11） | child | 否 |
| deemed_acceptance_determinations | 提议·revenue·未签发 | B-08 开票与收入（receivables.wip） | AGG-44 开票与收入额度（技术；每协议一个：付款条件项可开票额度与累计开票、履约义务分摊价格与累计确认、收入确认行；原 AGG-48 履约义务收入台账并入）（承担法人随 Q11） | child | 否 |
| revenue_recognitions | 提议·revenue·未签发 | B-08 开票与收入（receivables.wip） | AGG-44 开票与收入额度（技术；每协议一个：付款条件项可开票额度与累计开票、履约义务分摊价格与累计确认、收入确认行；原 AGG-48 履约义务收入台账并入）（承担法人随 Q11） | child | 否 |
| revenue_recognition_evidences | 提议·revenue·未签发 | B-08 开票与收入（receivables.wip） | AGG-44 开票与收入额度（技术；每协议一个：付款条件项可开票额度与累计开票、履约义务分摊价格与累计确认、收入确认行；原 AGG-48 履约义务收入台账并入）（承担法人随 Q11） | link | 否 |
| payment_term_capacity_changes | 提议·revenue·未签发 | B-08 开票与收入（receivables.wip） | AGG-44 开票与收入额度（技术；每协议一个：付款条件项可开票额度与累计开票、履约义务分摊价格与累计确认、收入确认行；原 AGG-48 履约义务收入台账并入）（承担法人随 Q11） | child | 否 |
| performance_obligation_capacity_changes | 提议·revenue·未签发 | B-08 开票与收入（receivables.wip） | AGG-44 开票与收入额度（技术；每协议一个：付款条件项可开票额度与累计开票、履约义务分摊价格与累计确认、收入确认行；原 AGG-48 履约义务收入台账并入）（承担法人随 Q11） | child | 否 |
| billing_requests | 提议·revenue·未签发 | B-08 开票与收入（receivables.wip） | AGG-45 开票申请（OBJ-82） | root | 否 |
| billing_request_checks | 提议·revenue·未签发 | B-08 开票与收入（receivables.wip） | AGG-45 开票申请（OBJ-82） | child | 否 |
| sales_invoices | 提议·revenue·未签发 | B-08 开票与收入（receivables.wip） | AGG-46 销项发票（OBJ-83 的销项方向） | root | 否 |
| sales_invoice_lines | 提议·revenue·未签发 | B-08 开票与收入（receivables.wip） | AGG-46 销项发票（OBJ-83 的销项方向） | child | 否 |
| red_letter_confirmations | 提议·revenue·未签发 | B-08 开票与收入（receivables.wip） | AGG-46 销项发票（OBJ-83 的销项方向） | child | 否 |
| recognition_evidence_type_versions | 提议·revenue·未签发 | B-08 开票与收入（receivables.wip） | AGG-92 可触发确认的证据类型开放配置版本（技术；ADR-0047 第 4 条第 4 点） | version | 否 |
| receivables | 提议·revenue·未签发 | B-09 应收与收款（receivables.collection） | AGG-49 应收往来款项（OBJ-85 应收方向：销售应收、其他应收、客户扣留质保金、我方交存的现金保证金） | root | 否 |
| receivable_applications | 提议·revenue·未签发 | B-09 应收与收款（receivables.collection） | AGG-49 应收往来款项（OBJ-85 应收方向：销售应收、其他应收、客户扣留质保金、我方交存的现金保证金） | child | 否 |
| receivable_disputes | 提议·revenue·未签发 | B-09 应收与收款（receivables.collection） | AGG-49 应收往来款项（OBJ-85 应收方向：销售应收、其他应收、客户扣留质保金、我方交存的现金保证金） | child | 否 |
| cash_receipts | 提议·revenue·未签发 | B-09 应收与收款（receivables.collection） | AGG-50 收款（OBJ-86 收方向） | root | 否 |
| receipt_allocations | 提议·revenue·未签发 | B-09 应收与收款（receivables.collection） | AGG-50 收款（OBJ-86 收方向） | child | 否 |
| receipt_bank_matches | 提议·revenue·未签发 | B-09 应收与收款（receivables.collection） | AGG-50 收款（OBJ-86 收方向） | link | 否 |
| security_deposits | 提议·revenue·未签发 | B-09 应收与收款（receivables.collection） | AGG-51 保证金与押金（OBJ-13；用户已定在应收里管，i210513） | root | 否 |
| security_deposit_movements | 提议·revenue·未签发 | B-09 应收与收款（receivables.collection） | AGG-51 保证金与押金（OBJ-13；用户已定在应收里管，i210513） | child | 否 |

## 查不到聚合名的聚合编号

REF-01


## 提示

- 每个聚合下的表归属一个聚合，聚合与 OBJ 的对应以聚合稿为准；「每个业务对象至少一张表」的反向核对请用聚合稿的 OBJ 列对本清单的聚合列做集合比较。
- 表角色 `link` 是关联表，通常不是独立业务对象，归属其两端聚合之一。
