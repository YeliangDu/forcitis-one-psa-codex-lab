# 数据模型全貌（finance 组）说明

对应 `data-model.finance.yaml`。全貌级，不到字段。格式按 one-architecture 2026-10-16 统一约定（表上不写 schema，`versioned` 四取值）。

## 1 汇总

| 聚合 | BC | 表数 | 根表 | 版本化 | 迁入 | 分级 |
|---|---|---|---|---|---|---|
| AGG-40 | B-07 | 2 | allocation_rule_versions | append_only | none | internal |
| AGG-41 | B-07 | 2 | labor_cost_allocation_runs | none | tbd | internal/sensitive |
| AGG-42 | B-07 | 2 | allocation_runs | none | tbd | internal |
| AGG-43 | B-07 | 2 | cost_carryover_batches | none | tbd | internal |
| AGG-52 | B-10 | 5 | payable_items | append_only | full/open_items | internal/sensitive |
| AGG-53 | B-10 | 3 | payments | append_only | full | internal/sensitive |
| AGG-54 | B-10 | 2 | partner_contributions | append_only | full | sensitive |
| AGG-109 | B-10 | 4 | profit_distributions | append_only | full | internal/sensitive |
| AGG-55 | B-11 | 3 | supplier_agreements | none | full | internal |
| AGG-56 | B-11 | 2 | subcontract_changes | none | full | internal |
| AGG-57 | B-11 | 2 | supplier_performance_confirmations | append_only | full | internal |
| AGG-58 | B-11 | 2 | supplier_agreement_quotas | append_only | none | internal |
| AGG-59 | B-12 | 3 | expense_claims | none | full | internal/sensitive |
| AGG-77 | B-16 | 3 | posting_candidates | append_only | full/none | internal/tbd |
| AGG-78 | B-17 | 2 | metric_definition_versions | append_only | none/tbd | internal |
| AGG-81 | B-19 | 2 | accounting_periods | none | full/none | internal |
| AGG-96 | B-19 | 1 | period_adjustment_enablements | append_only | none | internal |
| AGG-82 | B-20 | 2 | chart_of_account_versions | append_only | tbd | internal |
| AGG-83 | B-20 | 1 | ledger_books | none | none | internal |
| AGG-84 | B-20 | 4 | journal_entries | append_only | none/tbd | internal |
| AGG-85 | B-20 | 2 | account_mapping_versions | append_only | none | internal |
| AGG-94 | B-20 | 2 | control_account_authorization_versions | append_only | none | internal |
| AGG-98 | B-20 | 2 | manual_vouchers | none | tbd | internal |
| AGG-99 | B-20 | 2 | period_end_carryover_runs | none | tbd | internal |
| AGG-100 | B-20 | 2 | period_balance_snapshots | snapshot | tbd | internal |
| AGG-101 | B-20 | 3 | statutory_reconciliations | none | none | internal |
| AGG-86 | B-21 | 2 | report_line_definition_versions | append_only | none | internal |
| AGG-87 | B-22 | 2 | fund_accounts | effective_dated | full | internal/sensitive |
| AGG-88 | B-22 | 1 | bank_statement_lines | append_only | full | sensitive |
| AGG-89 | B-23 | 3 | input_invoices | append_only | full | internal |
| AGG-90 | B-23 | 3 | tax_returns | append_only | full | internal |
| AGG-91 | B-24 | 4 | company_assets | append_only/effective_dated | tbd | internal |

合计 32 个聚合（含聚合稿「不计」的 AGG-78、AGG-83）、77 张表。无表使用 ADR-0048 分区主键例外（该例外只属 `platform_audit.audit_entries`，本组审计走 platform.audit）。

## 2 交 one-data 复核的口径点

1. 配置版本（科目表、映射表、授权表、报表行定义、分摊规则、第 13 期启用登记、指标口径）按 D15「每版本一个聚合、部分唯一索引只一个生效」，暂记 `append_only`。
2. 维度型 D6：资金账户（`fund_account_versions`，DECLARED）、公司资产（`company_asset_versions`，DECLARED）判 effective_dated；账号不进版本表（个人信息与凭据最小化）。往来单位、人员、法人在别组。
3. `accounting_periods` 判 D17 豁免「单一状态且审计能完整还原」（ADR-0046 §8.6）；`ledger_books` 同理（参照数据、随法人登记）。
4. 只追加：事实、版本、流水、核销记录、折旧记录、快照；`journal_entries` 暂判只追加，已记账更正靠冲销，待处理状态是否需要另表（队列）请复核。
5. 派生投影：`supplier_agreement_quotas`、`ledger_account_balances`、应付未结余额，登记为投影、可重算并对账（D1）。
6. 分区：本组无按月分区表；`business_facts`、`journal_entries` 体量大，按 §2.4a 第 8 条需在建表时定分区键；若分区主键须含分区键，须另立 ADR（ADR-0048 第 6 条）。
7. 主数据归属 D11：资金账户记录系统为新系统（开户信息来自银行但不接入）；公司资产卡片来源 U8C 固定资产模块或清单，待定。
8. 保留期 D12 须用户定：凭证、账簿（30 年，ADR-0022 第 9 条）、`journal_entries`、`period_balance_snapshots`、`business_facts`、银行流水、申报；本稿未登记。
9. `post_sequence_counters` 是高频更新行（D18 第 10 条），需 one-data 给方案。

## 3 结构问题

聚合稿没有聚合、但 ADR 要求有表的对象，本稿未建表，建议归属（只建议）：

| 对象 | 出处 | 建议归属 |
|---|---|---|
| 口径转换规则版本 | ADR-0022 第 2 条写版本化，又说「按代码类变更管理」 | 若是代码，则分录只存规则 ID 与版本串、不建表；若是数据，需新聚合（B-20）。请 one-architecture 判 |
| 期末结转模板版本 | ADR-0022 第 3 条「模板化、按版本生效」；AGG-99 引用 | 配置版本聚合，B-20，与 AGG-99 并列 |
| 现金流量项目参考表 | ADR-0022 第 5 条（末级项目、标注在分录上） | 参考数据，B-20 或 B-21；与科目表版本同批 |
| 辅助核算维度定义 | ADR-0022 第 3 条（部门、项目、往来单位等） | 参考数据，B-20；或并入 `chart_accounts` 的维度要求列（本稿暂放列族） |
| 推送死信与外部对照 | ADR-0022 第 8 条；AGG-84 | 现暂放 `journal_entry_push_records`；外部凭证对照与死信若归 `platform.integration`（ADR-0033），请裁定边界 |
| 关账前置检查结果 | ADR-0022 第 10 条、ADR-0036；B-17 校验、B-19 关账 | 检查运行记录属 B-17 或 B-19，聚合稿未列；建议 B-17 新增「关账检查结果」 |
| 迁入占用 | ADR-0046 / §14.2 M8 | AGG-81 内含，本稿建 `period_migration_holds`（child） |
| 迁入拒收、暂存、对照 | §14.2 M5、M6 | 固定表，归迁入框架，非本组 |
| `ledger_account_balances` | ADR-0022 第 3 条期间余额；AGG-84 名为「分录与余额」 | 本稿放在 AGG-84 下，是否独立聚合请复核 |
| 应付「争议」分类取值表 | AGG-52 | 参考数据，取值待用户定 |

另：AGG-98 的凭证类别、差异类别代码是枚举，不建表（ADR-0022 第 2 条「枚举」）。

## 4 待定问题

| 编号 | 问题 | 影响的表 | 出处 |
|---|---|---|---|
| DM-1 | U8C 科目、凭证、余额进新系统的方式 | chart_of_account_*、journal_*、manual_*、period_balance_*、ledger_account_balances、company_* | ADR-0022 迁移方案、U-8 |
| DM-2 | 运行单据历史结果迁入还是重算 | allocation_*、labor_cost_*、cost_carryover_*、period_end_* | U-10、aggs.md |
| DM-3 | 供方履约确认形态与成本结转 | supplier_performance_*、cost_carryover_* | Q20、INV-88 |
| DM-4 | 人工成本分配口径 | labor_cost_allocation_* | FD-09 |
| DM-5 | 争议处置分类取值（只确认诉讼） | payable_dispute_records | AGG-52 |
| DM-6 | 对账签字人与容差；应收销项在收付账簿的时点 | statutory_reconciliations、account_mapping_lines | ADR-0022 |
| DM-7 | 第 13 期启用年度、模板结转与「以前年度损益调整」 | period_adjustment_enablements、period_end_* | P-001、P-002、P-003 |
| DM-8 | 工时与薪酬事实是否逐人存放 | business_facts、labor_cost_allocation_lines | AGG-77 |
| DM-9 | 保留期 | 见 §2 第 8 项 | D12 |
| DM-10 | 对外按开票、权责按客户确认两账差异处理 | journal_entries、mapping | R-9 |

## 5 新用语

先 grep `terms.yaml` 的 snake 字段（单数形）：命中仅 `expense_claim`、`posting_candidate`、`business_fact`、`accounting_period`。以下未命中，建议登记（中文 → 英文表名，使用范围：领域，bc 见 yaml）：

应付往来款项 payable_items；核销占用 payable_write_off_holds；供方协议 supplier_agreements；供方协议履约额度 supplier_agreement_quotas；供方履约确认 supplier_performance_confirmations；分包变更 subcontract_changes；分摊规则版本 allocation_rule_versions；分摊运行 allocation_runs；成本结转批次 cost_carryover_batches；人工成本分配运行 labor_cost_allocation_runs；付款 payments；合伙人出资 partner_contributions；利润分配 profit_distributions；会计分录 journal_entries；账簿 ledger_books（词表已有「账簿」9 处，需核对 snake）；科目表版本 chart_of_account_versions；收付权责科目映射表版本 account_mapping_versions；控制科目授权表版本 control_account_authorization_versions；手工凭证 manual_vouchers；期末结转运行 period_end_carryover_runs；期间余额快照 period_balance_snapshots；外部法定账对账 statutory_reconciliations；报表行定义版本 report_line_definition_versions；资金账户 fund_accounts；银行流水 bank_statement_lines；进项发票 input_invoices；申报 tax_returns；公司资产 company_assets；折旧与摊销记录 asset_depreciation_records；第 13 期启用登记 period_adjustment_enablements；勾稽差异案 reconciliation_variance_cases；指标口径版本 metric_definition_versions。

中文名查重只做了关键词计数（「应付往来款项」「供方协议」等 0 命中），未逐条比对跨 bc 同名，请 one-glossary 复核。

## 6 本稿没把握之处

- 聚合 AGG-109、AGG-54 的 BC 取 B-10（依据 ADR-0022 第 7 条与 aggs 事件说明），未在 aggs.md 行内直接标注。
- 配置类 `history_migration: none` 的前提是「新系统首版重新录入」，如需迁入旧口径版本须改。
- 分级：付款、报销、出资类整表判 sensitive 偏保守；若 one-data 认定金额本身 internal、个人只以 ID 引用，可降级。
- `journal_entries` 是否只追加（待处理态更新）、`ledger_account_balances` 是否独立表，均待复核。
- 表的角色 link / version / staging 的使用按 README 字面理解，`payment_bank_matches` 记为 link。

## 接缝问题

无新增边；引用均按 aggs.md「引用与依赖方式」。AGG-109 引用 AGG-52（B-10 内）与 AGG-100（B-20）为 B-10 → B-20，与聚合稿一致，未核对 §4.1 全表。

## 格式问题

1. 配置类版本（D15）没有对应 `versioned` 取值，暂写 `append_only`，建议新增 `config_version`。
2. `history_migration` 增写 `tbd`（U8C 总账数据迁入方式未定）。
3. 保留了 `data_category`、`notes`、`reason` 字段（`reason` 为约定内），前两者不在约定里，请裁定。
4. `key` 全部 uuid_v7；本组无 framework 表。
5. 引用目标除 AGG 号外，含同聚合内表名与 `platform.integration`。

## 待用户定

见 §4，汇总交 one-architecture 合成确认页。
