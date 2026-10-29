# N9 名词与会计动词写回

日期：2026-10-17。覆盖 N9 写回与 N9R 第 1 轮返工；本轮按当前用户指令在独立 worktree 内修改，未提交、未推送、未联网、未起容器。

## 写回结果与证据

**N9R 状态：返工已写回；本轮沙箱 precheck 为 9 PASS、1 FAIL、10 SKIP，退出 1，未达到本轮 0 FAIL 门禁。** `.codex-inputs/OPUS2.md` 记载负责会话本机 precheck 为 10 PASS、0 FAIL；这是复审提供的结果，本轮没有重跑该主机环境。本轮沙箱唯一 FAIL 为前端整体验证超时，检查原始输出记录在文末，不能把上轮沙箱超时写成所有环境的门禁结论。

正式词表从 664 条增至 719 条：新增 55 条，更新既有 11 条，均为 approved。另在 decisions-nm.yaml 的 verbs 中定稿 8 个会计动词／动作片段（7 道题，其中记账与过账分别列项），没有把无对象片段伪造为 DDD 命令。48 道答复逐题列在下表，原始答案、选项与 intent 的提交编号均由 apply_nm.py 读取校验。

| 批次 | 实际题数 | 提交编号 | 原话记录 |
| --- | --- | --- | --- |
| NM2 | 22 | `NM2-20261017-105418-xsj` | [intent](../../decisions/2026-10-17-105923-one-glossary-NM2-决策页提交-NM2-20.md) |
| NM3 | 15 | `NM3-20261017-105653-1u6` | [intent](../../decisions/2026-10-17-105923-one-glossary-NM3-决策页提交-NM3-20.md) |
| NM4 | 11 | `NM4-20261017-105806-n04` | [intent](../../decisions/2026-10-17-105924-one-glossary-NM4-决策页提交-NM4-20.md) |

以当前 sheets、answers 和本卡用户事实为准：N5_NOUN_JUDGMENT.md 的旧题数 20/12/9 及 n5-nouns-draft.yaml 的 review.page_items 尚未反映拆题后的 22/15/11，不将旧索引当作未作答。defaulted/recommended 仅是页面操作记录，不替代已经提交的 label。用户本次明确会计用语免转财务，已落实，不再请求财务确认。

所有登记项的 en、snake、kind 取 N5 草稿；流水明细用 N5-002 的 prior_draft_names 中 BankStatementLine / bank_statement_line，以及该条 rationale 明确的单行/entity 分类。kind 仅沿用设计候选，不表示中文认可批准了聚合边界、技术对象增减、版本合并、权限或存储。

## N9R 第 1 轮逐条处理

1. **全局禁用范围**：复审列出的 13 个通用短语全部改为 `standard-zh / deprecated: true`，从 `forbidden` 删除；法定申报涉及两个词条。新增检查按字面执行双向子串比对，覆盖 `bcs.yaml` 的 `zh`、`words`、`description`，以及其他正式／提议词条的 `zh`、`definition`；检查本轮新增的全局禁用，不重判上游已存在的禁用。另检出 IT 需求工单及 9 处子串冲突，共更正 24 个别名记录。未冲突的延期申请记录、用印申请记录、请假申请记录、检查表模板版本、服务产品版本、激励方案版本等仍保留 `dialect`。
   - **口径边界**：严格双向检查使“报表行定义版本／行”“分摊规则版本／规则版本”“员工活动申请记录／员工”冲突。本轮优先执行“互为子串时报错”，这 3 个旧名也改成 `standard-zh / deprecated: true`，没有为纯旧名增加绕过检查的例外。已向用户说明此项与“纯旧名保留 dialect”的冲突；尚未收到口径答复，供负责会话复核。
2. **定向更新与确认来源**：11 条 update 各自声明 `fields`，仅写对应答复的 `zh`、`definition`、`aliases`、`forbidden`、`standard_ref` 或追加 `note`；快照中其他字段与上游不一致即失败。列表合并去重，`note`、`confirmed` 保留原串并追加。本批有 9 条更新原先已有 `confirmed`，均逐条核实保留；另 2 条原先无该字段，写入本批确认。上游新增而快照未列出的字段也保留。
3. **别名与循环来源**：删除整表 bank_statement 上的 BankStatementLine 别名，单笔正式词条保留；删除 account、cash_receipt、receivable、wbs_node 的 4 条 `docs/glossary/terms.yaml terms[id=…]` 自引用。
4. **非空与提议词冲突**：`id`、`zh`、`en`、`snake`、`definition` 必须是非空文本（空串、纯空白、null 均失败）；新增词条的 `id`、`snake` 与 `backend/glossary/proposed/*.yaml` 比对，冲突即失败。未修改提议词文件。
5. **申请与岗位含义**：延期申请、员工活动申请、用印申请、请假申请的定义都以“申请”指称；position 恢复“任职与费率适用的岗级体系”，只追加区分说明；job_grade 和职位职级目录同步保留这层既有约束。“职位”缺独立英文与 snake，继续列入文末待补，不擅自登记。
6. **报告时效**：区分复审记载的主机 10 PASS、0 FAIL、上轮沙箱结果与本轮实跑结果；当前操作边界以 N9R 用户指令为准。文末附本轮原始 precheck 输出及每项 SKIP 理由。

本轮词条数 **719 → 719**（新增 0、删除 0，34 条内容更正；717 approved、2 deprecated）。相对 N9 之前仍为 664 → 719，新增 55、更新 11；受控会计动作仍为 8 个。48 道原始答复、8 个 verbs 记录和 not_registered 清单逐项比对未变；没有放宽或删除断言。`build_terms.py` 与本轮开工快照逐字节相同，其既有两行接入不属于 N9R 新修改。

## 答复 → 词条变更

| 题号 | 已提交所选 label | 实际词条／受控片段 | 变更及边界 |
| --- | --- | --- | --- |
| NM2-01 | 以下中文名全部认可 | `company_asset` | 认可本题所列草稿规范中文名；英文、snake、kind 沿用草稿，原名按本概念的旧指称处理。 |
| NM2-02 | 以下中文名全部认可 | `report_line_definition` | 认可本题所列草稿规范中文名；英文、snake、kind 沿用草稿，原名按本概念的旧指称处理。 |
| NM2-03 | 以下中文名全部认可 | `tax_filing_record` | 认可本题所列草稿规范中文名；英文、snake、kind 沿用草稿，原名按本概念的旧指称处理。 |
| NM2-04 | 以下中文名全部认可 | `allocation_rule` | 认可本题所列草稿规范中文名；英文、snake、kind 沿用草稿，原名按本概念的旧指称处理。 |
| NM2-05 | 以下中文名全部认可 | `profit_distribution_plan`、`partner_capital_contribution_record` | 认可本题所列草稿规范中文名；英文、snake、kind 沿用草稿，原名按本概念的旧指称处理。 |
| NM2-21 | 以下中文名全部认可 | `fund_account` | 认可本题所列草稿规范中文名；英文、snake、kind 沿用草稿，原名按本概念的旧指称处理。 |
| NM2-22 | 以下中文名全部认可 | `account`、`controlled_manual_voucher`、`chart_of_accounts_version` | 认可本题所列草稿规范中文名；英文、snake、kind 沿用草稿，原名按本概念的旧指称处理。 |
| NM2-06 | 以下中文名全部认可 | `bank_statement` | 认可本题所列草稿规范中文名；英文、snake、kind 沿用草稿，原名按本概念的旧指称处理。 |
| NM2-07 | 以下中文名全部认可 | `journal_entry` | 认可本题所列草稿规范中文名；英文、snake、kind 沿用草稿，原名按本概念的旧指称处理。 |
| NM2-08 | 以下中文名全部认可 | `bond_and_deposit` | 认可本题所列草稿规范中文名；英文、snake、kind 沿用草稿，原名按本概念的旧指称处理。 |
| NM2-09 | 整张明细表叫银行流水，单笔叫流水明细 | `bank_statement`、`bank_statement_line` | 整表 bank_statement 与单笔 bank_statement_line（流水明细）分开；单行英文来自 N5-002 的 prior_draft_names。 |
| NM2-10 | 分开叫，凭证承载会计分录 | `journal_entry`、`controlled_manual_voucher`、`journal_voucher` | journal_entry、journal_voucher、controlled_manual_voucher 区分借贷内容与凭证载体。 |
| NM2-11 | 现金保证金单独列示 | `bond_and_deposit`、`receivable` | bond_and_deposit 与 receivable 明写现金保证金单独列示；独立英文缺失项不增设词条。 |
| NM2-12 | 回款单只记已到账款项 | `cash_receipt` | cash_receipt 仅已到账；未兑付商票另按票据跟踪。 |
| NM2-13 | 保留冲销 | `reversing_entry`、`reverse`、`V037` | V037 冲销定稿；reversing_entry、reverse 补定义与本批确认，不改既有身份。 |
| NM2-14 | 这种抵付叫冲抵 | `V073` | V073 冲抵定稿，用于预付款抵付应付款；不臆造无对象命令。 |
| NM2-15 | 这种减少叫冲减 | `revenue_reduction`、`V074` | V074 冲减定稿，revenue_reduction 明确与信用损失分开。 |
| NM2-16 | 这种报表处理叫抵销 | `intercompany_elimination`、`V112` | V112 抵销定稿，intercompany_elimination 补报表处理边界。 |
| NM2-17 | 使用完整动作名 | `V070` | V070 发票更正定为完整名“开具红字发票”；红冲仍禁用；对象英文未定。 |
| NM2-18 | 恢复额度 | `V068` | V068 首选“恢复额度”，旧“回冲”只作禁用检索候选；不表示凭证冲销。 |
| NM2-19 | 区分记账与过账 | `V137`、`V153` | V137 记账、V153 过账分别定稿；未扩展为无对象命令。 |
| NM2-20 | 本批明确免转，由我直接确定 | `V037`、`V073`、`V074`、`V112`、`V070`、`V068`、`V137`、`V153` | 本批直接由用户确定，免转财务；NM1 两种核销仅确认路径沿用，不扩大到本卡之外的名词写回。 |
| NM3-01 | 以下中文名全部认可 | `client_supplied_material`、`customer_satisfaction_survey_record`、`design_change` | 认可本题所列草稿规范中文名；英文、snake、kind 沿用草稿，原名按本概念的旧指称处理。 |
| NM3-02 | 以下中文名全部认可 | `external_test_record`、`inspection_checklist_template`、`inspection_plan`、`inspection_record` | 认可本题所列草稿规范中文名；英文、snake、kind 沿用草稿，原名按本概念的旧指称处理。 |
| NM3-03 | 以下中文名全部认可 | `qualification_certificate` | 认可本题所列草稿规范中文名；英文、snake、kind 沿用草稿，原名按本概念的旧指称处理。 |
| NM3-04 | 以下中文名全部认可 | `customer_activity`、`marketing_campaign`、`independence_check_record` | 认可本题所列草稿规范中文名；英文、snake、kind 沿用草稿，原名按本概念的旧指称处理。 |
| NM3-13 | 以下中文名全部认可 | `wbs_node`、`resource_assignment_record`、`resource_requirement`、`project_team_member`、`project_retrospective_report`、`project_risk_and_issue_record` | 认可本题所列草稿规范中文名；英文、snake、kind 沿用草稿，原名按本概念的旧指称处理。 |
| NM3-14 | 以下中文名全部认可 | `service_product` | 认可本题所列草稿规范中文名；英文、snake、kind 沿用草稿，原名按本概念的旧指称处理。 |
| NM3-05 | 以下中文名全部认可 | `project_team` | 认可本题所列草稿规范中文名；英文、snake、kind 沿用草稿，原名按本概念的旧指称处理。 |
| NM3-07 | 以下中文名全部认可 | `supplier_performance_confirmation_statement` | 认可本题所列草稿规范中文名；英文、snake、kind 沿用草稿，原名按本概念的旧指称处理。 |
| NM3-08 | 以下中文名全部认可 | 无直接词条操作 | 认可标准成本费率表中文；既有 cost.costing 的 StandardCostRateCard 与草稿 masterdata.catalog 的复用关系未定，暂不新增或移 BC。 |
| NM3-15 | 以下中文名全部认可 | `billing_factor_version` | 认可本题所列草稿规范中文名；英文、snake、kind 沿用草稿，原名按本概念的旧指称处理。 |
| NM3-16 | 以下中文名全部认可 | `phase_type_version` | 认可本题所列草稿规范中文名；英文、snake、kind 沿用草稿，原名按本概念的旧指称处理。 |
| NM3-09 | 只有约定单价，没有总额上限 | 无直接词条操作 | 明确不登记 framework_agreement_limit 及派生限额名词，不修改既有独立的授权额度概念。 |
| NM3-10 | 包含全部参与人员，关键人员另作标记 | `project_team`、`project_team_member` | project_team、project_team_member 覆盖全部参与人员，关键人员另标记。 |
| NM3-11 | 按协议类型分别称变更 | 无直接词条操作 | 分包变更只用于分包；采购、租赁分别称采购变更、租赁变更；subcontract_change 尚在卡外 proposed，不重复登记。 |
| NM3-12 | 直接用申请作名称 | `extension_request`、`employee_activity_request`、`seal_usage_request`、`leave_request` | extension_request、employee_activity_request、seal_usage_request、leave_request 直接称申请；旧“申请记录”作禁用旧指称。 |
| NM4-01 | 以下中文名全部认可 | `knowledge_item` | 认可本题所列草稿规范中文名；英文、snake、kind 沿用草稿，原名按本概念的旧指称处理。 |
| NM4-02 | 以下中文名全部认可 | `overtime_record`、`monthly_attendance_confirmation_sheet` | 认可本题所列草稿规范中文名；英文、snake、kind 沿用草稿，原名按本概念的旧指称处理。 |
| NM4-10 | 以下中文名全部认可 | `payroll_statutory_filing_record`、`bonus_determination_sheet`、`monthly_payroll_settlement_statement`、`incentive_plan`、`social_insurance_and_housing_fund_enrollment_record` | 认可本题所列草稿规范中文名；英文、snake、kind 沿用草稿，原名按本概念的旧指称处理。 |
| NM4-11 | 以下中文名全部认可 | `recruitment_requisition`、`registered_practice_certificate`、`performance_appraisal_result` | 认可本题所列草稿规范中文名；英文、snake、kind 沿用草稿，原名按本概念的旧指称处理。 |
| NM4-03 | 以下中文名全部认可 | `it_service_ticket`、`it_asset_usage_record`、`it_asset_repair_order` | 认可本题所列草稿规范中文名；英文、snake、kind 沿用草稿，原名按本概念的旧指称处理。 |
| NM4-04 | 以下中文名全部认可 | `special_additional_deduction_declaration` | 认可本题所列草稿规范中文名；英文、snake、kind 沿用草稿，原名按本概念的旧指称处理。 |
| NM4-05 | 以下中文名全部认可 | `job_and_grade_catalog` | 认可本题所列草稿规范中文名；英文、snake、kind 沿用草稿，原名按本概念的旧指称处理。 |
| NM4-06 | 允许在记录名称中使用 | `monthly_attendance_confirmation_sheet`、`attendance_record` | attendance_record、monthly_attendance_confirmation_sheet 允许含考勤；既有裸“考勤”禁用保留，不将其恢复为模块名。 |
| NM4-07 | 三种含义分别命名 | `job_and_grade_catalog`、`position`、`job_grade` | job_and_grade_catalog 保留认可汇称，同时区分职位/岗位/职级；position 与 job_grade 补边界，职位缺英文未单列。 |
| NM4-08 | 同时涵盖设备和软件许可 | `it_asset_usage_record` | it_asset_usage_record 的 zh 改为“信息技术资产使用记录”，设备和软件许可分开说明；旧设备名禁用。 |
| NM4-09 | 按税费种类分别叫申报 | `payroll_statutory_filing_record` | payroll_statutory_filing_record 保留展示汇称，定义列三种申报；缺英文不擅自拆独立词条。 |

## 正式词条明细

以下 66 个概念是本次 terms.yaml 的全部结构化差异；已有概念 id、BC、英文身份均保持。更新项保留原标准依据、别名、禁用项及原有业务约束，confirmed 精确写本批日期、用户、页批次和提交编号。

| 操作 | 概念 id | BC／kind／scope | 规范中文名 | 英文／snake | 来源 |
| --- | --- | --- | --- | --- | --- |
| 新增 | `fund_account` | `accounting.cashbank` / `aggregate` / `domain` | 资金账户 | `FundAccount` / `fund_account` | N5-001 |
| 新增 | `bank_statement` | `accounting.cashbank` / `aggregate` / `domain` | 银行流水 | `BankStatement` / `bank_statement` | N5-002 |
| 新增 | `company_asset` | `accounting.fixedasset` / `aggregate` / `domain` | 公司资产 | `CompanyAsset` / `company_asset` | N5-003 |
| 新增 | `journal_entry` | `accounting.ledger` / `aggregate` / `domain` | 会计分录 | `JournalEntry` / `journal_entry` | N5-005 |
| 更新 | `account` | `accounting.ledger` / `entity` / `domain` | 会计科目 | `Account` / `account` | N5-007 |
| 新增 | `controlled_manual_voucher` | `accounting.ledger` / `aggregate` / `domain` | 受控手工凭证 | `ControlledManualVoucher` / `controlled_manual_voucher` | N5-008 |
| 新增 | `chart_of_accounts_version` | `accounting.ledger` / `aggregate` / `domain` | 科目表版本 | `ChartOfAccountsVersion` / `chart_of_accounts_version` | N5-017 |
| 新增 | `report_line_definition` | `accounting.reporting` / `aggregate` / `domain` | 报表行定义 | `ReportLineDefinition` / `report_line_definition` | N5-019 |
| 新增 | `tax_filing_record` | `accounting.tax` / `aggregate` / `domain` | 税务申报记录 | `TaxFilingRecord` / `tax_filing_record` | N5-020 |
| 新增 | `allocation_rule` | `cost.costing` / `aggregate` / `domain` | 分摊规则 | `AllocationRule` / `allocation_rule` | N5-026 |
| 新增 | `client_supplied_material` | `delivery.acceptance` / `aggregate` / `domain` | 发包人提供资料 | `ClientSuppliedMaterial` / `client_supplied_material` | N5-030 |
| 新增 | `customer_satisfaction_survey_record` | `delivery.acceptance` / `aggregate` / `domain` | 客户满意度调查记录 | `CustomerSatisfactionSurveyRecord` / `customer_satisfaction_survey_record` | N5-031 |
| 新增 | `design_change` | `delivery.acceptance` / `aggregate` / `domain` | 设计变更 | `DesignChange` / `design_change` | N5-032 |
| 更新 | `wbs_node` | `delivery.execution` / `entity` / `domain` | 工作分解结构节点 | `WbsNode` / `wbs_node` | N5-036 |
| 新增 | `extension_request` | `delivery.execution` / `aggregate` / `domain` | 延期申请 | `ExtensionRequest` / `extension_request` | N5-037 |
| 新增 | `resource_assignment_record` | `delivery.execution` / `aggregate` / `domain` | 资源分配记录 | `ResourceAssignmentRecord` / `resource_assignment_record` | N5-038 |
| 新增 | `resource_requirement` | `delivery.execution` / `aggregate` / `domain` | 资源需求 | `ResourceRequirement` / `resource_requirement` | N5-039 |
| 新增 | `project_team` | `delivery.execution` / `aggregate` / `domain` | 项目团队 | `ProjectTeam` / `project_team` | N5-040 |
| 新增 | `project_team_member` | `delivery.execution` / `entity` / `domain` | 项目团队成员 | `ProjectTeamMember` / `project_team_member` | N5-041 |
| 新增 | `project_retrospective_report` | `delivery.execution` / `aggregate` / `domain` | 项目复盘报告 | `ProjectRetrospectiveReport` / `project_retrospective_report` | N5-042 |
| 新增 | `project_risk_and_issue_record` | `delivery.execution` / `aggregate` / `domain` | 项目风险与问题记录 | `ProjectRiskAndIssueRecord` / `project_risk_and_issue_record` | N5-044 |
| 新增 | `external_test_record` | `delivery.inspection` / `aggregate` / `domain` | 外部检测记录 | `ExternalTestRecord` / `external_test_record` | N5-045 |
| 新增 | `inspection_checklist_template` | `delivery.inspection` / `aggregate` / `domain` | 检查表模板 | `InspectionChecklistTemplate` / `inspection_checklist_template` | N5-046 |
| 新增 | `inspection_plan` | `delivery.inspection` / `aggregate` / `domain` | 检查计划 | `InspectionPlan` / `inspection_plan` | N5-047 |
| 新增 | `inspection_record` | `delivery.inspection` / `aggregate` / `domain` | 检查记录 | `InspectionRecord` / `inspection_record` | N5-048 |
| 新增 | `knowledge_item` | `km.knowledge` / `aggregate` / `domain` | 知识条目 | `KnowledgeItem` / `knowledge_item` | N5-054 |
| 新增 | `service_product` | `masterdata.catalog` / `aggregate` / `domain` | 服务产品 | `ServiceProduct` / `service_product` | N5-056 |
| 新增 | `billing_factor_version` | `masterdata.catalog` / `aggregate` / `domain` | 计费系数版本 | `BillingFactorVersion` / `billing_factor_version` | N5-058 |
| 新增 | `phase_type_version` | `masterdata.catalog` / `aggregate` / `domain` | 阶段类型版本 | `PhaseTypeVersion` / `phase_type_version` | N5-059 |
| 新增 | `qualification_certificate` | `masterdata.counterparty` / `aggregate` / `domain` | 资质证书 | `QualificationCertificate` / `qualification_certificate` | N5-060 |
| 新增 | `it_service_ticket` | `organization.administration` / `aggregate` / `domain` | 信息技术服务工单 | `ItServiceTicket` / `it_service_ticket` | N5-064 |
| 新增 | `it_asset_usage_record` | `organization.administration` / `aggregate` / `domain` | 信息技术资产使用记录 | `ItAssetUsageRecord` / `it_asset_usage_record` | N5-065 |
| 新增 | `it_asset_repair_order` | `organization.administration` / `aggregate` / `domain` | 信息技术设备维修单 | `ItAssetRepairOrder` / `it_asset_repair_order` | N5-066 |
| 新增 | `employee_activity_request` | `organization.administration` / `aggregate` / `domain` | 员工活动申请 | `EmployeeActivityRequest` / `employee_activity_request` | N5-067 |
| 新增 | `seal_usage_request` | `organization.administration` / `aggregate` / `domain` | 用印申请 | `SealUsageRequest` / `seal_usage_request` | N5-068 |
| 新增 | `overtime_record` | `organization.leave` / `aggregate` / `domain` | 加班记录 | `OvertimeRecord` / `overtime_record` | N5-072 |
| 新增 | `monthly_attendance_confirmation_sheet` | `organization.leave` / `aggregate` / `domain` | 月度考勤确认表 | `MonthlyAttendanceConfirmationSheet` / `monthly_attendance_confirmation_sheet` | N5-073 |
| 新增 | `attendance_record` | `organization.leave` / `aggregate` / `domain` | 考勤记录 | `AttendanceRecord` / `attendance_record` | N5-074 |
| 新增 | `leave_request` | `organization.leave` / `aggregate` / `domain` | 请假申请 | `LeaveRequest` / `leave_request` | N5-075 |
| 新增 | `special_additional_deduction_declaration` | `organization.payroll` / `aggregate` / `domain` | 专项附加扣除声明 | `SpecialAdditionalDeductionDeclaration` / `special_additional_deduction_declaration` | N5-076 |
| 新增 | `payroll_statutory_filing_record` | `organization.payroll` / `aggregate` / `domain` | 个税及社保公积金申报记录 | `PayrollStatutoryFilingRecord` / `payroll_statutory_filing_record` | N5-077 |
| 新增 | `bonus_determination_sheet` | `organization.payroll` / `aggregate` / `domain` | 奖金核定表 | `BonusDeterminationSheet` / `bonus_determination_sheet` | N5-079 |
| 新增 | `monthly_payroll_settlement_statement` | `organization.payroll` / `aggregate` / `domain` | 月度薪酬结算单 | `MonthlyPayrollSettlementStatement` / `monthly_payroll_settlement_statement` | N5-080 |
| 新增 | `incentive_plan` | `organization.payroll` / `aggregate` / `domain` | 激励方案 | `IncentivePlan` / `incentive_plan` | N5-081 |
| 新增 | `social_insurance_and_housing_fund_enrollment_record` | `organization.payroll` / `aggregate` / `domain` | 社保与公积金参保记录 | `SocialInsuranceAndHousingFundEnrollmentRecord` / `social_insurance_and_housing_fund_enrollment_record` | N5-082 |
| 新增 | `recruitment_requisition` | `organization.workforce` / `aggregate` / `domain` | 招聘需求 | `RecruitmentRequisition` / `recruitment_requisition` | N5-086 |
| 新增 | `registered_practice_certificate` | `organization.workforce` / `aggregate` / `domain` | 注册执业证书 | `RegisteredPracticeCertificate` / `registered_practice_certificate` | N5-087 |
| 新增 | `performance_appraisal_result` | `organization.workforce` / `aggregate` / `domain` | 绩效评估结果 | `PerformanceAppraisalResult` / `performance_appraisal_result` | N5-088 |
| 新增 | `job_and_grade_catalog` | `organization.workforce` / `aggregate` / `domain` | 职位职级目录 | `JobAndGradeCatalog` / `job_and_grade_catalog` | N5-089 |
| 新增 | `profit_distribution_plan` | `payables.payment` / `aggregate` / `domain` | 利润分配方案 | `ProfitDistributionPlan` / `profit_distribution_plan` | N5-091 |
| 新增 | `partner_capital_contribution_record` | `payables.payment` / `aggregate` / `domain` | 合伙人出资记录 | `PartnerCapitalContributionRecord` / `partner_capital_contribution_record` | N5-092 |
| 新增 | `customer_activity` | `presales.opportunity` / `aggregate` / `domain` | 客户活动 | `CustomerActivity` / `customer_activity` | N5-094 |
| 新增 | `marketing_campaign` | `presales.opportunity` / `aggregate` / `domain` | 市场活动 | `MarketingCampaign` / `marketing_campaign` | N5-095 |
| 新增 | `independence_check_record` | `presales.opportunity` / `aggregate` / `domain` | 独立性检查记录 | `IndependenceCheckRecord` / `independence_check_record` | N5-097 |
| 新增 | `supplier_performance_confirmation_statement` | `procurement.subcontracting` / `aggregate` / `domain` | 供方履约确认单 | `SupplierPerformanceConfirmationStatement` / `supplier_performance_confirmation_statement` | N5-099 |
| 新增 | `bond_and_deposit` | `receivables.collection` / `aggregate` / `domain` | 保证金与押金 | `BondAndDeposit` / `bond_and_deposit` | N5-101 |
| 更新 | `cash_receipt` | `receivables.collection` / `aggregate` / `domain` | 回款单 | `CashReceipt` / `cash_receipt` | N5-102 |
| 更新 | `receivable` | `receivables.collection` / `aggregate` / `domain` | 应收款项 | `Receivable` / `receivable` | N5-103 |
| 新增 | `bank_statement_line` | `accounting.cashbank` / `entity` / `domain` | 流水明细 | `BankStatementLine` / `bank_statement_line` | N5-002/单笔 |
| 更新 | `journal_voucher` | `accounting.ledger` / `aggregate` / `domain` | 记账凭证 | `JournalVoucher` / `journal_voucher` | 既有概念 journal_voucher |
| 更新 | `reversing_entry` | `operations.facts` / `entity` / `domain` | 冲销 | `ReversingEntry` / `reversing_entry` | 既有概念 reversing_entry |
| 更新 | `reverse` | `kernel` / `command` / `kernel` | 冲销动作 | `Reverse` / `reverse` | 既有概念 reverse |
| 更新 | `revenue_reduction` | `receivables.wip` / `domain_event` / `domain` | 收入冲减 | `RevenueReduction` / `revenue_reduction` | 既有概念 revenue_reduction |
| 更新 | `intercompany_elimination` | `accounting.ledger` / `command` / `domain` | 内部往来抵销 | `IntercompanyElimination` / `intercompany_elimination` | 既有概念 intercompany_elimination |
| 更新 | `position` | `organization.workforce` / `value_object` / `domain` | 岗位 | `Position` / `position` | 既有概念 position |
| 更新 | `job_grade` | `organization.workforce` / `value_object` / `domain` | 职级 | `JobGrade` / `job_grade` | 既有概念 job_grade |

## 旧名、同名与禁用范围

- 草稿 aliases、previous_designation.zh 和 prior_draft_names.en 的旧写法按概念保留；BankStatementLine 属于单笔正式词条，不再作为整表别名。四类“申请记录”旧中文与旧英文仍可追溯，首选名分别为延期申请、员工活动申请、用印申请、请假申请。
- 未与上下文或其他词条冲突的纯旧中文使用 dialect / deprecated: true；发生同形或双向子串冲突的旧指称使用 standard-zh / deprecated: true 并写 context 和说明，表示本概念不再首选该指称，避免把通用词全局误判为方言。没有另造 alias type。旧英文用 old-code / deprecated: true，未将仍在其他概念中使用的代码词全局禁用。
- 银行流水作为整表首选名；单笔的旧“银行流水”仅作为单笔旧指称保留，不能因此禁用整表首选。IT 旧设备使用记录改为信息技术资产使用记录，范围含实物设备及软件许可。
- “收款”作为回款单的旧对象名标为 deprecated；不全局判为方言，保留已认可的收款行为与收款核销含义。当前 4 张决策页检查为 0 处问题，剩余两条警告是题面中的“计费系数”“附加扣除声明”片段；完整名称已有本批登记，未改题面。
- “考勤”原有禁用没有解除。仅 attendance_record 与 monthly_attendance_confirmation_sheet 的完整记录名可用；前端既有检查先识别完整正式名，后查剩余方言，本卡没有修改检查器。
- 新增词条与前端 copy-allow-list.yaml、bcs.yaml.words 的精确词／中文片段比对无冲突；pnpm check:copy 对 95 个文件通过。
- 同 BC 中英文查重、跨 BC 同名互列由 apply_nm.py 和 term_rules 共同检查。本批没有产生需新增 homonyms 的正式同名组（新增互列 0）；StandardCostRateCard 的复用未定，不为满足互列而制造两个概念。

## 未登记及原因

未登记不表示用户未认可：下列项目或细分概念的中文裁定均已保存在 decisions-nm.yaml，阻碍是词条身份、卡外重复或缺双语指称。

| 项目 | 依据 | 原因与已完成的处理 |
| --- | --- | --- |
| N5-023：框架协议限额 | NM3-09 | NM3-09 明确只有约定单价、无总额上限，拒绝该候选；不登记其限额、预占或累计消耗派生名。 |
| N5-057：标准成本费率表 | NM3-08 | 英文 StandardCostRateCard 已在 cost.costing（id standard_cost_rate_card），草稿提议 masterdata.catalog；rationale 明确权威配置与成本侧快照不可默认成无关概念，尚缺复用或不同含义裁定，不迁移 BC、不创设同名副本。 |
| N5-100：分包变更 | NM3-11 | 卡外 backend/glossary/proposed/enterprise-aggregates.yaml#subcontract_change 尚在；正式转正必须同批删除提议，否则重复 id/同 bc 中英文名。裁定保留，待负责会话另卡转正。 |
| 答复细分：现金保证金 | NM2-11 | 单独列示已写入 bond_and_deposit / receivable；草稿没有独立英文与 snake，不增设单独词条。 |
| 答复细分：采购变更、租赁变更 | NM3-11 | 按协议类型分别称呼已定；草稿仅有 SubcontractChange，另外两种缺独立英文、snake 与上下文设计，不硬造条目。 |
| 答复细分：职位 | NM4-07 | 三种叫法分开已记录；岗位保留“任职与费率适用的岗级体系”原约束，职位仍未单独登记，缺独立英文和 snake，待负责会话补齐身份后另卡登记。 |
| 答复细分：个人所得税申报、社会保险申报、住房公积金申报 | NM4-09 | 分别显示已写入 payroll_statutory_filing_record；三种独立英文、snake 不在草稿，不擅自拆实体。 |
| 答复细分：Offset、Reduce、Eliminate、Restore、Book、Post 独立动词词条 | NM2-20 | N4 提供的是受控动词片段；当前 DDD 十种 kind 没有 verb，且各业务对象与 BC 槽位未完备。本卡在 verbs 定稿并应用有对应的既有词条，不创设空对象命令。 |
| 答复细分：开具红字发票完整英文动作名 | NM2-17 | N4 只有 Issue/Issued，明确红字发票对象代码词未定；中文已定、旧红冲禁用保留，完整命令等待对象英文。 |

“职位／岗位／职级”分别命名的答复已记录；目录保留用户认可的 JobAndGradeCatalog，既有 position、job_grade 保留原约束并追加区分说明，不从名称重定业务含义或目录存储。职位独立英文与 snake 仍缺失，是未解决的登记项。

其余 39 项不在本轮题面内，按 N5 原路由保留，不擅自将“设计方处理”“已定沿用”或“沿用已登记”当作 NM2–NM4 本批新确认：

| 草稿 | 名称 | 原路由 |
| --- | --- | --- |
| N5-004 | 资产折旧政策版本 | 设计方处理，不上业务页；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-006 | 会计口径转换规则版本 | 设计方处理，不上业务页；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-009 | 外部法定账对账记录 | 设计方处理，不上业务页；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-010 | 控制科目授权表 | 设计方处理，不上业务页；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-011 | 收付权责科目映射表 | 设计方处理，不上业务页；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-012 | 期初余额记录 | 设计方处理，不上业务页；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-013 | 期末结转模板版本 | 设计方处理，不上业务页；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-014 | 期末结转记录 | 设计方处理，不上业务页；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-015 | 期间余额快照 | 设计方处理，不上业务页；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-016 | 现金流量项目表版本 | 设计方处理，不上业务页；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-018 | 第十三期启用记录 | 已定沿用，不再出题；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-021 | 进项发票 | 沿用已登记中文名，不上页；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-022 | 变更 | 沿用已登记中文名，不上页；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-024 | 人工成本分配结果 | 设计方处理，不上业务页；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-025 | 分摊结果 | 设计方处理，不上业务页；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-027 | 签约前投入 | 已定沿用，不再出题；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-028 | 成本核算参数版本 | 设计方处理，不上业务页；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-029 | 不符合项 | 沿用已登记中文名，不上页；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-033 | 证据 | 已定沿用，不再出题；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-034 | 证据关联记录 | 已定沿用，不再出题；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-035 | 交付计划 | 沿用已登记中文名，不上页；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-043 | 项目预算（WBS 预算） | 沿用已登记中文名，不上页；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-050 | 出差补贴标准版本 | 设计方处理，不上业务页；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-052 | 报销单 | 沿用已登记中文名，不上页；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-055 | 协议价格 | 已定沿用，不再出题；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-061 | 过账记录 | 设计方处理，不上业务页；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-062 | 经营计划 | 沿用已登记中文名，不上页；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-063 | 预算账户 | 不在第2至4批范围，待负责会话另排；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-069 | 标准工时版本 | 设计方处理，不上业务页；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-071 | 假期额度规则 | 设计方处理，不上业务页；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-083 | 薪酬规则版本 | 设计方处理，不上业务页；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-084 | 人员 | 沿用已登记中文名，不上页；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-085 | 劳动合同 | 已定沿用，不再出题；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-090 | 付款单 | 沿用已登记中文名，不上页；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-093 | 应付款项 | 沿用已登记中文名，不上页；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-096 | 报价单 | 沿用已登记中文名，不上页；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-098 | 线索 | 已定沿用，不再出题；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-104 | 客户认可证据类型开放配置 | 设计方处理，不上业务页；本卡没有对应 NM2–NM4 答复，不扩入本批。 |
| N5-105 | 销项发票 | 沿用已登记中文名，不上页；本卡没有对应 NM2–NM4 答复，不扩入本批。 |

C5 移交的 N5-049 出差申请、N5-051 差旅订单月账单、N5-053 费用事前申请、N5-070 假期余额、N5-078 人员薪酬档案亦不在本卡注册范围；没有修改 C5、proposed、bcs.yaml 或前端通用词清单。

## N9R 应用与验证

沿用 N9 已有的 build_terms.py 两行接入（本轮不改该文件），在上游裁定及补充字段合入后应用本批答复，最后运行原有 term_rules。decisions-nm.yaml 是决策源，apply_nm.py 的 47 个负向用例与现有正向断言全部保留执行；没有修改验收测试、测试目录、断言门槛或 term-rules-baseline.json。

### 已验证

- `cd docs && python3 glossary/apply_nm.py --self-test`：通过，47 个负向用例，覆盖必填缺失、空串／空白／null、通用词进入 forbidden 或 dialect、双向子串、proposed 的 id／snake、非答复字段漂移、无效 fields、原有重复身份／缺目标／同 BC 重名／身份漂移等。正向检查覆盖同名互列、旧值合并、原 confirmed 与 note 追加保留、上游额外字段保留、更新幂等和 48 道答复来源。
- 在 `/tmp/n9r-review/apply_nm.py` 开工副本上复现旧实现的三项缺陷：通用词进 forbidden 不失败、非答复 kind 被覆盖、原 confirmed 被覆盖；仓库实现对应回归已通过。
- `cd docs && python3 glossary/build_terms.py` 连续两次退出 0，terms.yaml 逐字节一致；两次 SHA-256 均为 `0b0b49e978f988b267c9c9787a198504899d1c653e0041e7229295c1c8a4bc89`。输出 `nm: {'added': 55, 'updated': 11, 'homonyms': 0}`；词条总数 719。term_rules 既有 109 条（command_name 1、event_name 5、metric 75、unique_zh_error_code 10、used_by 18），未新增或放宽基线。
- 结构化核对：相对本轮开工快照，词条集合、meta、common_words 完全相同，仅 34 条词条内容更正；11 条 update 的非答复字段逐项保留；9 条既有 confirmed 均为原串加本批确认；48 道 answers、8 个 verbs 与 not_registered 清单未变。复审列出的 13 个通用短语在全部正式词条中均不再属于 dialect／forbidden。
- `cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./mvnw -o test-compile surefire:test -Dtest=GlossaryTest`：退出 0，5 项通过、0 失败、0 错误、0 跳过。沿用既有词条提示，不把提示当作业务验收证据。
- `cd frontend && pnpm run check:copy`：退出 0，95 个文件通过。
- `cd frontend && pnpm run test:scripts`：退出 0，37 项通过、0 失败、0 跳过。
- 本轮 precheck 中后端四类术语测试、文案与脚本测试、用语／题面检查、发布脚本自测、shellcheck、gitleaks 均通过；gitleaks 8.30.1 扫描 2,802 个提交未发现泄露。全历史扫描不包含未提交文件，原输出已如实注明。
- `pnpm verify` 已完成的 8 步返回成功：check:generated、typecheck、check:lint-config、lint、check:structure、check:copy、test:scripts、test；其中 api 37、design-system 2、core 41、ui 4、h5 48、web 93 项测试通过（以文末原输出为准）。build 未返回，整体验证不能判通过。
- Python 使用本机已有 3.12.13 / PyYAML，Java 使用已有 JDK 25，Maven 全部离线（`-o`）；前端沿用已有依赖。本轮没有安装依赖。`git diff --check` 通过。

### 未运行

- 未执行字面命令 `cd backend && ./mvnw -o test -Dtest=GlossaryTest`：`backend/pom.xml:109–110` 把 export-openapi / OpenApiExportTest 绑定到 process-test-classes，会启动 PostgreSQL 容器并改写卡外 OpenAPI；该执行不受本次 `-Dtest` 切换。遵守当前用户禁容器、限文件指令，采用 scripts/precheck.sh 已有的 test-compile + surefire:test 路径运行实际 GlossaryTest，5 项均执行、无跳过。
- 未执行 build_all.py 的卡外生成产物写回；precheck 按既有规则跳过未提交的 generated docs。本卡 terms.yaml 的指定生成及两次可复现检查已执行。
- 未运行需要容器、数据库或联网的全量集成／验收测试；本卡没有指定独立验收测试目录。未提交、未推送、未发布、未向其他会话或财务发送消息。

### 未解决项

- 沙箱前端 build 步骤 120 秒未返回，导致本轮 precheck 1 FAIL；尚未确定根因，不能据超时推断构建代码故障。本轮没有改卡外构建代码、依赖或检查器。OPUS2 记载的负责会话主机 10 PASS、0 FAIL 与本轮沙箱结果是不同环境的证据，均按来源列明。
- 第 1 条“严格双向子串”与“纯旧名保留 dialect”的口径冲突如上所列，本轮执行严格检查，3 个冲突旧名改为 standard-zh；尚未收到用户口径答复。
- “职位”独立英文与 snake、标准成本费率表的复用身份、分包变更转正时删除卡外提议词等既有登记缺口，继续按“未登记及原因”保留；本卡不扩展身份或修改卡外源文件。

### precheck.sh 完整原始输出

命令：`PATH=/tmp/n9r-review/bin:/Users/yeliang/.pyenv/versions/3.12.13/bin:$PATH JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home MAVEN_ARGS=-o CI=true scripts/precheck.sh`。退出码 **1**。

`/tmp/n9r-review/bin/pnpm` 只为参数恰为 `verify` 的原 pnpm 命令设置 120 秒超时：原参数和环境照传，超时终止该命令进程组、返回 124；其他 pnpm 调用直接转给原程序。包装没有跳过步骤或改写通过结果。未修改仓库脚本。precheck 读取本地 origin/main `c5d86918`、HEAD `b8c0083d`，没有 fetch；changed files 包括分支原有差异与开工即存在的输入文件，不代表本轮修改范围。

```text
precheck: base origin/main (c5d86918), HEAD b8c0083d, 41 changed files (13 uncommitted)

SKIP  generated docs (build_all --check)     docs or backend/glossary/proposed has uncommitted changes: commit first, then run again (请先提交再运行)
PASS  legacy system name                     check_legacy_name: 无违规
PASS  decision pages: question rules (self-test) check_pages self-test: ok
PASS  decision pages: question rules         check_pages: 4 张页，0 处问题
SKIP  migrations immutable                   no migration, marker or Flyway setting changed
SKIP  system actors append-only              system-actors.txt and its script did not change
SKIP  acceptance guard                       no acceptance test changed
SKIP  database scripts aligned               no database script or migration changed
SKIP  docs read by builds are registered     no backend, frontend or script changed
SKIP  assertion drift (warning only)         no test file changed
PASS  glossary: frontend check:copy          copy glossary: 95 files, every Chinese text is built from registered terms and the allow l
PASS  glossary: frontend test:scripts        ℹ duration_ms 3322.992666
PASS  glossary: backend term tests             size (term round3_pending__size) -> com.forcitis.one.platform.integration.api.ArchivedOb
SKIP  guards: backend guard tests            no guard test or architecture list changed
PASS  release scripts self-test              deploy_gate.py self-test passed
PASS  shellcheck scripts/release             ok
PASS  gitleaks (full history)                2802 commits scanned; no leaks
      note: uncommitted files are not in history; the pre-commit hook scans them on commit
SKIP  backend tests                          not run by default; no module code changed (shared inputs, resources, build or architecture lists)
      run: (cd backend && ./mvnw -q test-compile surefire:test -Dtest='ArchitectureRulesTest,ModularityTest,ModuleDependencyTest,NamingSurfaceTest,GlossaryTest,DdlNamingTest,LegacyShapeTest,TimeSourceGuardTest,TestIsolationGuardTest' -Dsurefire.failIfNoSpecifiedTests=false)
      before delivery run everything: scripts/ci/backend_verify.sh (Testcontainers: ask the user before starting containers)
FAIL  frontend verify (pnpm verify)          exit 124 — last lines:
      | 
      |  Test Files  6 passed (6)
      |       Tests  48 passed (48)
      |    Start at  12:49:33
      |    Duration  989ms (environment 52%, tests 22%, setup 15%, import 7%, transform 4%)
      | 
      | $ vitest run
      | 
      |  RUN  v5.0.4 /Users/yeliang/Projects/FORCITIS/forcitis-one-psa/.claude/worktrees/codex-n9/frontend/apps/web
      | 
      | 
      |  Test Files  13 passed (13)
      |       Tests  93 passed (93)
      |    Start at  12:49:34
      |    Duration  6.57s (tests 41%, import 31%, environment 20%, setup 6%, transform 1%)
      | 
      | 
      | ::endgroup::
      | [ELIFECYCLE] Command failed.
      | N9R 沙箱验证超时：原 pnpm verify 在 120 秒内未结束，已终止；退出 124，未跳过检查。
SKIP  SQL Server integration tests           relevant_change.py: nothing the *MssqlIT tests exercise changed

SUMMARY: 9 PASS, 1 FAIL, 10 SKIP (base origin/main) — not ready
```

10 项 SKIP 理由：generated docs 有未提交改动且本卡禁止提交／卡外写回；migrations 未改迁移／开放标记／Flyway；system actors 未改；acceptance 未改；database scripts 未改；docs read by builds 无 backend／frontend／scripts 源文件改动；assertion drift 无测试文件改动；guards 无守护测试或架构清单改动；backend tests 默认不跑全量且用户禁容器（四类术语测试已执行）；SQL Server integration 无相关输入变化且本卡禁容器。

### 沙箱前端整体验证完整原始输出

以下为本轮 `/tmp/n9r-review/frontend-verify.raw.log` 原文；包装器的 120 秒超时行已在上方 precheck 输出中保留。verify.mjs 只在子步骤返回后输出其缓冲内容，因此尚未结束的 build 没有对应 group；不据此推断它执行到哪个内部阶段。

```text
$ node scripts/verify.mjs

::group::ok   check:structure (0.2s)
$ node scripts/check-structure.mjs

::endgroup::

::group::ok   check:generated (0.5s)
$ node scripts/generate-api.mjs --check
generated API code matches openapi.json (68 operations)

::endgroup::

::group::ok   check:copy (0.5s)
$ node scripts/check-copy-glossary.mjs
copy glossary: 95 files, every Chinese text is built from registered terms and the allow list

::endgroup::

::group::ok   check:lint-config (1.8s)
$ node scripts/check-lint-config.mjs
lint-config self-test: 49 probes, 816 file-category checks OK (11 PC/package categories, 8 H5 categories)

::endgroup::

::group::ok   lint (2.2s)
$ eslint . --max-warnings 0 && stylelint "{apps,packages}/*/src/**/*.css"

::endgroup::

::group::ok   test:scripts (3.7s)
$ node --test "scripts/*.test.mjs"
✔ one React 18 under /h5/ passes (37.692292ms)
✔ a second React fails the check, whatever its version (146.637375ms)
✔ assets outside the sub-path fail the check (37.467458ms)
✔ the same content in both apps is reported, whatever the file is called (3.156834ms)
✔ reformatting or re-commenting a copy does not hide it (1.295958ms)
✔ a copy of a file of packages/core inside an app is reported too (1.650333ms)
✔ files that merely look alike, a copy inside one app and empty placeholders are not duplicates (2.18175ms)
✔ the repository itself has no code that exists twice (15.778583ms)
✔ the command line fails on a copy and names both files, and passes once the copy is gone (78.173042ms)
✔ the glossary and the allow list are well formed (0.874ms)
✔ context register names, bc words and term attributes pass, an unlisted word still fails (1.076ms)
✔ registered terms, proposed terms and allowed plain words pass (0.689709ms)
✔ a one-character word is only a connector between two words (M-16) (0.578917ms)
✔ connectors cannot be chained into a text that no term explains (0.239666ms)
✔ only the listed values may be a whole text by themselves (0.171541ms)
✔ a business word that is not registered fails, with the word named (0.189666ms)
✔ a registered term next to an unregistered word still names only the unregistered word (0.128084ms)
✔ a dialect of a registered term fails, a registered term that contains the dialect does not (0.347042ms)
✔ UI text positions are all read: JSX text, button, title, placeholder, aria-label, template, message table (4.335125ms)
✔ comments and English text are not user-facing copy (0.512083ms)
✔ injection: a source file with an unregistered word, a dialect and a plain word is reported line by line (1.324ms)
✔ a folder called testing is skipped only where the layout puts test helpers, not anywhere in production code (1.866292ms)
✔ test files and test helpers are not scanned (0.420208ms)
✔ the command line exits 1 on an unregistered word and 0 on clean copy (654.640125ms)
✔ the context register fails loudly when it is missing, empty or malformed (5.006167ms)
✔ a bc word may not whitelist a dialect or repeat a registered term; an attribute needs a Chinese name (3.706208ms)
✔ the committed document passes and carries the traits (336.44625ms)
✔ enum-valued properties are open in the types (a newer server may send more), the enum components stay closed for exhaustive tables (326.223583ms)
✔ a document without ProblemDetail or the ErrorCode enum is refused (960.608ms)
✔ an operation without a ProblemDetail error answer is refused, and so is a foreign error answer (465.183ms)
✔ page-number paging is refused: lists page by cursor and limit (198.147042ms)
✔ a cursor needs the {items, nextCursor} answer and a limit, and nextCursor needs a cursor (589.634791ms)
✔ only If-Match and Idempotency-Key may be header parameters, and If-Match must be required (411.790875ms)
✔ an unknown schema keyword still fails the generation (209.3315ms)
✔ the GET of a resource with a versioned update is found, and a GET of another resource is not (0.773542ms)
✔ a plain query over such a GET is a violation, whichever file it is in; useVersionedQuery is not (0.4775ms)
✔ in this repository every versioned GET is read with useVersionedQuery (14.236583ms)
ℹ tests 37
ℹ suites 0
ℹ pass 37
ℹ fail 0
ℹ cancelled 0
ℹ skipped 0
ℹ todo 0
ℹ duration_ms 3548.811667

::endgroup::

::group::ok   typecheck (5.7s)
$ pnpm -r --workspace-concurrency=1 run typecheck
Scope: 6 of 7 workspace projects
$ tsc --noEmit -p tsconfig.json
$ tsc --noEmit -p tsconfig.json
$ tsc --noEmit -p tsconfig.json
$ tsc --noEmit -p tsconfig.json
$ tsc --noEmit -p tsconfig.json
$ tsc --noEmit -p tsconfig.json

::endgroup::

::group::ok   test (12.0s)
$ pnpm -r --workspace-concurrency=1 run test
Scope: 6 of 7 workspace projects
$ vitest run

 RUN  v5.0.4 /Users/yeliang/Projects/FORCITIS/forcitis-one-psa/.claude/worktrees/codex-n9/frontend/packages/api


 Test Files  3 passed (3)
      Tests  37 passed (37)
   Start at  12:49:29
   Duration  629ms (environment 89%, import 7%, tests 2%, transform 2%, worker 1%)

$ vitest run

 RUN  v5.0.4 /Users/yeliang/Projects/FORCITIS/forcitis-one-psa/.claude/worktrees/codex-n9/frontend/packages/design-system


 Test Files  1 passed (1)
      Tests  2 passed (2)
   Start at  12:49:30
   Duration  82ms (import 40%, transform 37%, worker 11%, tests 11%)

$ vitest run

 RUN  v5.0.4 /Users/yeliang/Projects/FORCITIS/forcitis-one-psa/.claude/worktrees/codex-n9/frontend/packages/core


 Test Files  7 passed (7)
      Tests  41 passed (41)
   Start at  12:49:30
   Duration  941ms (environment 65%, tests 23%, import 9%, transform 2%)

$ vitest run

 RUN  v5.0.4 /Users/yeliang/Projects/FORCITIS/forcitis-one-psa/.claude/worktrees/codex-n9/frontend/packages/ui


 Test Files  1 passed (1)
      Tests  4 passed (4)
   Start at  12:49:31
   Duration  1.38s (import 65%, environment 18%, tests 9%, setup 6%, transform 1%)

$ vitest run

 RUN  v5.0.4 /Users/yeliang/Projects/FORCITIS/forcitis-one-psa/.claude/worktrees/codex-n9/frontend/apps/h5


 Test Files  6 passed (6)
      Tests  48 passed (48)
   Start at  12:49:33
   Duration  989ms (environment 52%, tests 22%, setup 15%, import 7%, transform 4%)

$ vitest run

 RUN  v5.0.4 /Users/yeliang/Projects/FORCITIS/forcitis-one-psa/.claude/worktrees/codex-n9/frontend/apps/web


 Test Files  13 passed (13)
      Tests  93 passed (93)
   Start at  12:49:34
   Duration  6.57s (tests 41%, import 31%, environment 20%, setup 6%, transform 1%)


::endgroup::
[ELIFECYCLE] Command failed.
```

## NAM-1017-GLO-02：NM5 与 OBJ-118–123 登记

日期：2026-10-17。本节为本卡增量，前面的 N9／N9R 结果按历史保留。沿用开工时的独立 worktree 和上一轮回写机制；本卡只改 `decisions-nm.yaml`、`apply_nm.py`、由生成器重建的 `terms.yaml`，以及本报告的追加部分；未提交、未推送、未联网、未起容器。

**本卡尚未全部完成：已新增 8 条、更新 0 条，正式词表 719 → 727；OBJ-122、OBJ-123 的同概念提议源合并转正涉及卡外文件，未登记；本轮 precheck 为 9 PASS、1 FAIL、10 SKIP，退出 1，未达到 0 FAIL 门禁。** 原有 719 条词条、meta、common_words 逐项未变，新增同名互列 0。原有 48 道答复、66 个写回操作、8 个受控动作和旧的未登记清单均保留。本节的新裁定补充原有“C5 五项不在本批范围”的历史说明，不将旧说明当作当前未答。

### 本卡来源与回写机制

- NM5 三题均来自 `docs/decisions/decision-pages/answers/nm5.json`，提交 `NM5-20261017-113346-5l9`；逐项核对 `sheets/NM5.json` 的选项序号、label、noun_refs 和提交号，拒绝未答、带待核说明及空提交。NM5-01 采用用户实际选择，不用页面的推荐或默认值替代它。
- 本地没有 NM5 对应 intent，故在 `decisions-nm.yaml` 的 `meta.submitted_sheets` 显式声明直接核对已提交答复。NM2–NM4 原有 intent 原话及提交号核对继续执行，没有删除或放宽。
- OBJ-117–123 来自 [CAPINFO 对象交接](capinfo-newterms.md)，提交 `CAPINFO-20261017-111010-cfy`。新增对象交接答复类型，逐行校验中文、建议英文、BC、提交号及来源覆盖；新增词条的中文、英文、BC 必须与交接行一致。核对而不新增的 OBJ-117 和待合并的 OBJ-122／123 也各保留来源行，不伪造选择题或 intent。
- `apply_nm.py` 仅为上述新答复类型扩展来源校验与自测；原有定向更新、禁用词双向子串检查、提议源 id／snake 冲突检查、同 BC 查重及跨 BC 同名互列机制继续执行。最终仍由 `term_rules.py` 校验正式与提议词，不改守护或基线。
- `build_terms.py` 未改，沿用上一轮接入；其日志标题仍是“命名第 2–4 批答复”，实际操作集合以 `decisions-nm.yaml` 为准，现包括 NM5 与 CAPINFO。

### 新增词条与范围

所有新增项 `scope: domain`、`status: approved`。BC 和 DDD kind 为本卡按同类词及 `term_rules.py` 作出的设计分类，不表示权限、存储或业务流程实现已验收。

1. `legal_entity_finance_head`：**法人财务负责人 / LegalEntityFinanceHead**，`organization.workforce`，`role`。与部门负责人、区域应收负责人归入同一 BC；名称和定义均注明法人范围。跨法人事项须另写明由哪个法人的财务负责人承担，任职、代理人与权限仍按已定授权规则。
2. `allocation_base`：**分摊基数 / AllocationBase**，`cost.costing`，`value_object`。据以分摊成本的量；每条分摊规则写明量及单位。本次不替业务选择具体基数、公式或数值。
3. `contribution_base`：**缴费基数 / ContributionBase**，`organization.payroll`，`value_object`。社会保险或住房公积金计算缴费额的基数，按项目及期间明确其金额与币种；与分摊基数分别定义。本卡不设缴费政策、上下限或费率，亦不追加“请财务逐项核实”的前置条件。
4. `professional_discipline`：**专业 / ProfessionalDiscipline**，`organization.workforce`，`value_object`。人员擅长的专业类别，项目团队成员引用该含义；类别清单由业务提供。与业务线及服务产品区分，不编造专业目录。
5. `expense_preapproval`：**费用事前申请 / ExpensePreapproval**，`expense.spend`，`aggregate`，对应 OBJ-118。承载独立金额批准及后续费用关联。
6. `travel_request`：**出差申请 / TravelRequest**，`expense.spend`，`aggregate`，对应 OBJ-119。承载独立行程批准、同步及后续费用关联。
7. `travel_order`：**差旅订单 / TravelOrder**，`expense.spend`，`aggregate`，对应 OBJ-120。作为订单行超标审批、退改、归属与结算核对的依据。
8. `travel_monthly_statement`：**差旅订单月账单 / TravelMonthlyStatement**，`expense.spend`，`aggregate`，对应 OBJ-121。承载月账单逐项核对签字、差异处理及应付确认。

OBJ-118–121 的英文全部采用 CAPINFO 建议，无命名规则或同 BC 冲突，不需改名。N5 修订稿的 `transferred_c5` 仅留移交追溯，没有这五项的完整英文定义；本卡未声称从中读到不存在的草稿字段。对象定义复用已有 `objects-draft.yaml` 的范围并以本次 CAPINFO 已确认边界收紧；英文以本次交接为准，不沿用更早的 BusinessTripRequest／ExpensePreApplication／TravelOrderMonthlyStatement 候选。

OBJ-117 已核对：`reconciliation_deviation_case / ReconciliationDeviationCase / operations.policy / domain / aggregate`，首选中文“勾稽差异案”、已批准，原词条逐字节保留，不重登。

### 未登记及合并方案

1. **OBJ-122 假期余额账户 / LeaveBalanceAccount / organization.leave**：同一概念已在 `backend/glossary/proposed/enterprise-aggregates.yaml#leave_balance`，对象键为人员 × 假期类型 × 年度。应保留概念 id `leave_balance`，将首选名改为本次已定名称，核定、使用、结转、折算与手工调整进入定义，同时更正旧提议中否定“账户”叫法的说明。本卡不另造 `leave_balance_account` 副本。
2. **OBJ-123 人员薪酬档案 / EmployeeCompensationProfile / organization.payroll**：同一概念已在上述提议源的 `employee_compensation`，原首选名为“员工薪酬 / EmployeeCompensation”，并把“人员薪酬档案”列作 dialect。应保留 id `employee_compensation` 合并转正，按本次决定只追加生效日期的薪资历史，关联人员、任职、薪酬结算及独立扣除记录；旧指称保留可追溯来源，不能再全局禁用本次已确认名称，也不能继续把专项附加扣除并成薪酬档案属性。

两项阻碍均已从源码核实：`build_terms.py` 在 `apply_nm.apply(terms)` 之后，仍原样读取 `backend/glossary/proposed/*.yaml` 并交给 `term_rules.enforce`。以原 id 直接加入正式词表会重复概念身份，换新 id 只为避过检查会制造同义副本。完成合并至少须另卡允许同步迁出该提议源记录，再由本决策源转正；提议源文件不在本卡允许范围内，遵照卡外停手规则未改。合并目标、首选英文及定义已保存在本卡 `decisions-nm.yaml` 的追加 `not_registered` 中，便于负责会话一次复核与续接。未把“尚未转正”写成“用户尚未答”。

能力名依“对象＋管理”、价值流与阶段名依动宾槽位组合；不单独登记 CAPINFO 的能力、价值流或阶段标签。专业目录值、具体分摊规则数值和缴费标准未提供，保持由业务维护，不虚构词条。

### 设计方自定事项

以下恰为 [覆盖盘点](naming-atoms-coverage.md) 中原序 1、2、4、5、6、8、10 七项；原序 3、7、9 已由 NM5 作答，不混入设计方自行裁定。按 [ADR-0053](../../adr/ADR-0053.md) 记录结论及理由，登记位置和后续守护仍参照 [登记方案](naming-atoms-registration-plans.md)；本卡没有新增登记文件或实施新守护。

1. **客户同名与组合引用**：引用现行 `customer_role`，保留已弃用的 `customer` id 及 `replaced_by` 桥接；不把这两个 id 计作两个现行含义，也不凭同名合并其他概念。理由：概念 id 永久不变，改名改变指称，概念关系与弃用桥接保留历史；依据 ADR-0053 决定1第1条、决定2第1条、决定5第3–4条。
2. **上位名词与具体对象粒度**：被独立引用的对象先核定义再登记，具体对象以概念引用进入槽位；上位词、任意长短语或反解残片不自动成为独立对象。证据及证据关联沿已有决定。理由：唯一来源约束具体概念与指称，组合依槽位，名称反解只作迁移工具；依据决定1第3条、决定2第1条、决定3。
3. **状态取值、集合与能力限制**：概念分类与状态取值分开；集合引用值域，能力限制为正交条件；不把每个事件强制变成状态。“已／待／中”按模板渲染，“草稿”等词汇化取值逐条登记，沿用已定可结算与审批进度分离。理由：种类与关系在概念层，状态等组合由结构化槽位生成；依据决定2第1条、决定3。
4. **限定词与计划对象**：限定词受控并声明修饰的对象；计划对象、Plan 动作及计划量修饰分开，不从代码片段自行扩充业务含义。理由：槽位与格式是可演进细则，由命名面入口及负责会话维护，不能让自由字符串改变业务定义；依据决定3、决定4、决定7第2条。
5. **期间、年度、业务日、时刻与生效区间**：业务日用 date，时刻用 UTC；期间、年度与生效区间分别建模，时间单位不代替日期概念。会计年度及期间沿既有定义补引用，不重定会计政策。理由：概念需有明确种类与关系，组合不能抹平量纲和时间语义，地区显示与概念定义分开；依据决定2第1条、决定3、决定6第5条；格式沿 NAMING_STANDARD §2.4、§2.6 A-7。
6. **结果、原因、否定与模态结构**：结果、原因及能力与生命周期分开；“未发生”与“已否定”分别定义，同形异义未定时保留待确认，不默认合并，也不增万能失败动词。理由：定义及关系须消歧，组合有明确槽位，待确认指称禁止使用；依据决定2第1条、决定3、决定5第2条。
7. **语法、技术词、命名空间与固定文案归属**：业务概念及指称归 glossary 并入源，BC 与系统名沿 bcs 既定源；语法、技术格式及连接片段归模板；完整文字归消息目录，业务名词由占位符引用指称，代码名使用英文 ASCII。理由：保持唯一来源，格式是命名面的投影细则，代码角色与界面文字分离；依据决定1第3条、决定3、决定4、决定6第3–4条、决定7第2条。

### 本卡已验证

- 在 `docs` 下使用本机已有 Python 3.12.13／PyYAML 6.0.3，两次运行 `python3 glossary/build_terms.py` 均退出 0，`cmp` 逐字节一致；SHA-256 均为 `a32577c8f78d63629de6107e11f03cb47270937ec68b002c601d40f3e506c21c`。该目录默认 `/usr/bin/python3` 3.9.6 缺 PyYAML，改用已有解释器，没有安装依赖。
- `python3 glossary/apply_nm.py --self-test` 退出 0：59 个负向用例，原有 47 个全部保留；新增已提交来源校验、选项序号／名称一致性、空提交／未答／说明、交接英文及 BC 漂移反例。正向仍校验同名互列、定向更新幂等及旧 confirmed／note 保留；实际来源为 51 道答复及 7 项对象交接。
- `term_rules.py` 既有 109 条基线问题未变：command_name 1、event_name 5、metric 75、unique_zh_error_code 10、used_by 18；未加基线、未放宽守护。所有既有正式词逐项不变，同 BC 中英文无新增重名，跨 BC 无新增同名组。
- `cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./mvnw -o test-compile surefire:test -Dtest=GlossaryTest` 退出 0，5 项通过、0 失败、0 错误、0 跳过。
- `cd frontend && pnpm run check:copy` 退出 0，95 个文件通过；`pnpm run test:scripts` 退出 0，37 项通过、0 失败、0 跳过。沿用本机已有 JDK 25、Node 24 和依赖。
- 开工快照核对：原有词条和决策操作均保留；`build_terms.py`、`N4_VERBS_STATES.md` 及全部 26 个提议 YAML 与开工时 SHA-256 相同。`git diff --check` 通过。自测新增在获准的 `apply_nm.py` 中，未改验收测试或其他测试目录。

### 本卡未运行

- 未运行字面 `./mvnw -o test -Dtest=GlossaryTest`：已核对 pom.xml，process-test-classes 绑定的 export-openapi 会单独运行 OpenApiExportTest，启动容器并改写卡外 OpenAPI，不受此处 `-Dtest` 切换。采用仓库 `precheck.sh` 已有无容器路径，实际 GlossaryTest 全部执行。
- 未运行 build_all.py 的卡外产物生成或容器／数据库／联网集成验收；本卡未指定独立业务验收用例。未发布页面或向其他会话、财务发送消息。
- 未完成 OBJ-122／OBJ-123 合并转正，未实现 N8 登记方案的新结构、目录与守护，也未据专业名词登记宣称业务类别清单已经齐全。

### 本卡 precheck 结果与输出

命令：`PATH=/tmp/nam-1017-glo-02/bin:/Users/yeliang/.pyenv/versions/3.12.13/bin:$PATH JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home MAVEN_ARGS=-o CI=true COREPACK_ENABLE_NETWORK=0 npm_config_offline=true scripts/precheck.sh`。退出码 **1**，SUMMARY 为 **9 PASS、1 FAIL、10 SKIP**，不算完成。

本轮仍沿用上一轮的超时观测方式：临时 `/tmp/nam-1017-glo-02/bin/pnpm` 仅在参数恰为 `verify` 时执行原 pnpm 并设 120 秒总上限；其他调用原样转发。原命令未结束时终止该进程组并返回 124，没有跳过任何子步骤、改变检查参数或伪造通过；仓库脚本未改。前端原始缓冲输出保存在 `/tmp/nam-1017-glo-02/frontend-verify.log`。verify 的 check:generated、typecheck、check:lint-config、lint、check:structure、check:copy、test:scripts、test 八步都返回成功；build 未返回，不能据此判定代码构建故障或整个 verify 通过。未改卡外构建或依赖。

precheck 后端四类术语测试 XML 已回读：GlossaryTest 5、ErrorCodeGlossaryTest 2、NamingSurfaceTest 11、DocumentTypeCodeTest 3，均零失败、零错误、零跳过。全历史 gitleaks 8.30.1 实跑扫描 2,811 个提交未发现泄露；另对本卡四个交付文件的副本运行 `gitleaks dir --redact`，未发现泄露，补足未提交文件不在历史扫描内的边界。

每项 SKIP 理由如下，均保留原脚本判定：

1. generated docs：存在未提交改动，用户禁止提交，且不改卡外生成产物；本卡 terms 的两次生成另已验证。
2. migrations immutable：未改迁移、开放标记或 Flyway 配置。
3. system actors append-only：未改系统执行者清单或其脚本。
4. acceptance guard：未改验收测试目录。
5. database scripts aligned：未改数据库脚本或迁移。
6. docs read by builds are registered：未改 backend／frontend／scripts 源文件。
7. assertion drift：未改测试目录中的文件；本卡只在获准 apply_nm.py 中追加自测，原断言保留。
8. guards：未改后端守护测试或架构清单；四类术语测试已实跑。
9. backend tests：默认不跑后端全量，本卡禁容器；指定 GlossaryTest 与 precheck 的术语测试已执行。
10. SQL Server integration tests：无对应受测输入变化，且本卡禁容器。

基线为本轮本地 `origin/main 23bf4216`、`HEAD b8c0083d`，没有 fetch。原始 changed-file 数含分支原有差异与已有输入，并非本卡修改了 50 个文件。完整原始输出如下：

```text
precheck: base origin/main (23bf4216), HEAD b8c0083d, 50 changed files (22 uncommitted)

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
PASS  glossary: frontend check:copy          copy glossary: 95 files, every Chinese text is built from registered terms and the allow l
PASS  glossary: frontend test:scripts        ℹ duration_ms 2980.439833
PASS  glossary: backend term tests             size (term round3_pending__size) -> com.forcitis.one.platform.integration.api.ArchivedOb
SKIP  guards: backend guard tests            no guard test or architecture list changed
PASS  release scripts self-test              deploy_gate.py self-test passed
PASS  shellcheck scripts/release             ok
PASS  gitleaks (full history)                2811 commits scanned; no leaks
      note: uncommitted files are not in history; the pre-commit hook scans them on commit
SKIP  backend tests                          not run by default; no module code changed (shared inputs, resources, build or architecture lists)
      run: (cd backend && ./mvnw -q test-compile surefire:test -Dtest='ArchitectureRulesTest,ModularityTest,ModuleDependencyTest,NamingSurfaceTest,GlossaryTest,DdlNamingTest,LegacyShapeTest,TimeSourceGuardTest,TestIsolationGuardTest' -Dsurefire.failIfNoSpecifiedTests=false)
      before delivery run everything: scripts/ci/backend_verify.sh (Testcontainers: ask the user before starting containers)
FAIL  frontend verify (pnpm verify)          exit 124 — last lines:
      | 
      |  Test Files  6 passed (6)
      |       Tests  48 passed (48)
      |    Start at  13:01:21
      |    Duration  1.07s (environment 54%, tests 21%, setup 14%, import 6%, transform 4%)
      | 
      | $ vitest run
      | 
      |  RUN  v5.0.4 /Users/yeliang/Projects/FORCITIS/forcitis-one-psa/.claude/worktrees/codex-n9/frontend/apps/web
      | 
      | 
      |  Test Files  13 passed (13)
      |       Tests  93 passed (93)
      |    Start at  13:01:22
      |    Duration  6.76s (tests 39%, import 37%, environment 18%, setup 5%, transform 1%)
      | 
      | 
      | ::endgroup::
      | [ELIFECYCLE] Command failed.
      | NAM-1017-GLO-02：原 pnpm verify 120 秒未结束，已终止，退出 124；未跳过检查。
SKIP  SQL Server integration tests           relevant_change.py: nothing the *MssqlIT tests exercise changed

SUMMARY: 9 PASS, 1 FAIL, 10 SKIP (base origin/main) — not ready
```

本卡验证产物在 `/tmp/nam-1017-glo-02/`：`baseline.json`、两次 build 日志、`terms-once.yaml`、`verification.json`、后端与前端日志、`precheck.log` 及脱敏密钥扫描结果。临时目录仅供本次本机续接；可持续查阅的词条、合并方案、七项结论及 precheck 原文均已写入本卡获准文件。

## NAM-1017-GLO-02：返工第 1 轮（OBJ-122、OBJ-123）

日期：2026-10-17。依据本轮工作卡及 CAPINFO 提交 `CAPINFO-20261017-111010-cfy`，两项对象登记已完成。本轮新增正式词条 **2 条、更新既有词条 0 条**，正式词表 **727 → 729**；连同本卡上轮为 **新增 10 条、更新 0 条，719 → 729**。原有 727 条词条、meta、common_words 逐项未变。本轮仅修改 `decisions-nm.yaml`、由 `build_terms.py` 生成的 `terms.yaml` 与本报告追加部分；`apply_nm.py` 不变，未提交、未推送、未联网、未起容器。

### 合并来源与最终名称

1. **OBJ-122**：`leave_balance_account`，**假期余额账户 / LeaveBalanceAccount**，`organization.leave`，`scope: domain`、`kind: aggregate`、`status: approved`。合并 `backend/glossary/proposed/enterprise-aggregates.yaml` 的 `id: leave_balance`：保留人员 × 假期类型 × 年度、应享／已用／占用三项数量及“已用加占用不超过应享”的边界，保留 AGG-71 与《职工带薪年休假条例》依据；按 CAPINFO 补入核定、使用、结转、折算与手工调整。原英文 **LeaveBalance** 作为 `old-code / deprecated: true` 别名；原中文 **假期余额** 作为 `standard-zh / deprecated: true` 别名，不全局禁用。正式定义区分假期额度账户与会计账户，不保留旧提议否定“账户”叫法的说明。
2. **OBJ-123**：`employee_compensation_profile`，**人员薪酬档案 / EmployeeCompensationProfile**，`organization.payroll`，`scope: domain`、`kind: aggregate`、`status: approved`。合并同文件 `id: employee_compensation`：保留薪酬属性版本（职级、薪档）、按月结算输入、敏感个人信息与 ADR-0043 访问分级，保留 AGG-93 与企业会计准则第 9 号依据；按 CAPINFO 明确关联人员、任职，按生效日期只追加薪资历史，并关联独立扣除记录，不再将专项附加扣除并成档案属性。原英文 **EmployeeCompensation** 作为 `old-code / deprecated: true` 别名；原中文 **员工薪酬** 作为 `standard-zh / deprecated: true` 别名。正式词条不复制旧提议将当前首选名“人员薪酬档案”标为 dialect 的裁定。

两个旧英文与本次 CAPINFO 首选名不同，故保留为可追溯旧英文别名，各别名均写明原文件、提议 id 与更名原因；不增加 `forbidden`。业务内容按本次已确认对象范围合并，旧提议中与新决定冲突的命名或对象边界不照搬。原标准依据仅从本机提议源承接，本轮没有联网重新核验标准。

### 查重与身份处理（纠正上轮阻碍结论）

已读源码：`term_rules.check` 将正式与提议条目全部纳入现行词条检查，仅排除 `status: deprecated`，按同 BC 的 `zh`、`en` 首选名查重；没有“正式词存在即忽略同 id 提议”的规则，也不把别名当作第二个首选名。Java `Glossary.load` 另将正式与提议合读并检查全局 id 唯一。因此，保留原提议时直接复用 `leave_balance`、`employee_compensation` 两个 id 会造成 Java 的 `duplicate id`。

本轮按用户“正式登记、保留提议源并交所属会话删除”的明确要求，以 CAPINFO 正式名称对应的 `leave_balance_account`、`employee_compensation_profile` 登记；旧提议 id 的合并映射及来源写入 `standard_ref`、别名 `source` 与 `note`。这两条是原提议内容的正式落点，不将未删的原提议当作另外两项获批业务对象。已有正式概念 id 未改。上轮“必须同批修改卡外提议源才能转正”及其保留原提议 id 的合并方案由本节取代；`not_registered` 的 OBJ-122、OBJ-123 已迁出，答复行分别指向已落地正式 id。

两条沿用现有 `op: add` 和来源核对，无需新增答复类型或改 `apply_nm.py`。同 BC 首选中英文、正式与提议间 id／snake 均不冲突；没有为通过守护增加例外、改基线、放宽断言或修改测试。旧提议的中文名与本次正式名不同；其旧英文只在正式项别名中保留，故不会触发首选名重复。前端已有规则会识别正式首选名，不将“人员薪酬档案”按旧提议 dialect 拦截。

### 通知项（交负责会话转达）

**正式词条已存在，请提议源所属会话删除原提议条目。**

- 文件：`backend/glossary/proposed/enterprise-aggregates.yaml`；待删除 **id: `leave_balance`**；内容已合并至正式 **id: `leave_balance_account`**（OBJ-122）。
- 文件：`backend/glossary/proposed/enterprise-aggregates.yaml`；待删除 **id: `employee_compensation`**；内容已合并至正式 **id: `employee_compensation_profile`**（OBJ-123），同步消除该旧提议将“人员薪酬档案”标为 dialect 的过期裁定。

提议源原条目本轮未改。通知已写入本报告交接；本会话未直接向其他会话发送消息。删除源条目前，所属会话须按上面的旧提议 id → 正式 id 映射核对并迁移相关引用，不再另建概念。

### 本轮已验证

- `build_terms.py` 在 `docs` 下以本机已有 Python 3.12.13 连续运行两次，均退出 0；`cmp` 逐字节一致，SHA-256 均为 `916ed800244b17886c2a75f66fb9ca92c4ba50acfba5b37aa70fc17d9defb8db`。
- `apply_nm.py --self-test` 退出 0：59 个负向用例、同名互列、原确认与说明保留、更新幂等、51 道答复及 7 项对象交接来源核对全部通过；脚本未修改。
- 生成阶段原有 `term_rules.enforce` 通过：存量仍为 109 条，分布为 command_name 1、event_name 5、metric 75、unique_zh_error_code 10、used_by 18；新问题 0。既有 727 条正式词逐项未改。
- `cd backend && JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home MAVEN_ARGS=-o ./mvnw -o test-compile surefire:test -Dtest=GlossaryTest` 退出 0：5 项通过，0 失败、0 错误、0 跳过。
- `cd frontend && pnpm run check:copy` 退出 0：95 个文件通过；`pnpm run test:scripts` 退出 0：37 项通过，0 失败、0 跳过。均禁用包管理器联网，未安装依赖。

### 本轮未运行

- 未运行字面 `./mvnw -o test -Dtest=GlossaryTest`：pom.xml 在 process-test-classes 绑定 export-openapi，会启动容器并改写卡外 OpenAPI；按仓库 precheck 的既有无容器路径实际执行了 GlossaryTest。
- 未运行容器／数据库／外网集成验收及 `build_all.py` 的卡外生成。未发布页面，未改提议源或验收测试。

### 本轮 precheck 结果与完整输出

**本轮两项词条登记已完成；整卡门禁仍未通过。** `scripts/precheck.sh` 最终为 **9 PASS、1 FAIL、10 SKIP**，没有达到 `0 FAIL`。唯一 FAIL 为原始 `pnpm verify` 在 180 秒内未结束，超时退出 124。其日志已返回通过的组为 check:structure、check:generated、check:copy、check:lint-config、lint、test:scripts、typecheck、test 共 8 项；`build` 没有返回完成组，未取得整体验证成功结论。未据此认定业务代码失败，也未声称已核实超时根因；前端源码与构建配置均不在本卡改动范围。

命令：

```sh
PATH=/tmp/nam-1017-glo-02-r1/bin:/Users/yeliang/.pyenv/versions/3.12.13/bin:/Users/yeliang/.nvm/versions/node/v24.18.0/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
MAVEN_ARGS=-o COREPACK_ENABLE_NETWORK=0 npm_config_offline=true CI=true scripts/precheck.sh
```

临时 `bin/pnpm` 仅对原始 `pnpm verify` 施加 180 秒超时，其他参数原样交给已有 pnpm；没有跳过任何步骤，也没有修改检查脚本或伪造成功退出码。日志按脚本原样附下，每个 SKIP 的理由均保留：

```text
precheck: base origin/main (23bf4216), HEAD b8c0083d, 53 changed files (25 uncommitted)

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
PASS  glossary: frontend check:copy          copy glossary: 95 files, every Chinese text is built from registered terms and the allow l
PASS  glossary: frontend test:scripts        ℹ duration_ms 2934.864833
PASS  glossary: backend term tests             size (term round3_pending__size) -> com.forcitis.one.platform.integration.api.ArchivedOb
SKIP  guards: backend guard tests            no guard test or architecture list changed
PASS  release scripts self-test              deploy_gate.py self-test passed
PASS  shellcheck scripts/release             ok
PASS  gitleaks (full history)                2818 commits scanned; no leaks
      note: uncommitted files are not in history; the pre-commit hook scans them on commit
SKIP  backend tests                          not run by default; no module code changed (shared inputs, resources, build or architecture lists)
      run: (cd backend && ./mvnw -q test-compile surefire:test -Dtest='ArchitectureRulesTest,ModularityTest,ModuleDependencyTest,NamingSurfaceTest,GlossaryTest,DdlNamingTest,LegacyShapeTest,TimeSourceGuardTest,TestIsolationGuardTest' -Dsurefire.failIfNoSpecifiedTests=false)
      before delivery run everything: scripts/ci/backend_verify.sh (Testcontainers: ask the user before starting containers)
FAIL  frontend verify (pnpm verify)          exit 124 — last lines:
      | 
      |  Test Files  6 passed (6)
      |       Tests  48 passed (48)
      |    Start at  13:08:49
      |    Duration  951ms (environment 51%, tests 23%, setup 15%, import 7%, transform 3%)
      | 
      | $ vitest run
      | 
      |  RUN  v5.0.4 /Users/yeliang/Projects/FORCITIS/forcitis-one-psa/.claude/worktrees/codex-n9/frontend/apps/web
      | 
      | 
      |  Test Files  13 passed (13)
      |       Tests  93 passed (93)
      |    Start at  13:08:50
      |    Duration  7.00s (tests 46%, import 30%, environment 18%, setup 5%, transform 1%)
      | 
      | 
      | ::endgroup::
      | [ELIFECYCLE] Command failed.
      | NAM-1017-GLO-02 返工第1轮：原 pnpm verify 180 秒未结束，已终止，退出 124；未跳过检查。
SKIP  SQL Server integration tests           relevant_change.py: nothing the *MssqlIT tests exercise changed

SUMMARY: 9 PASS, 1 FAIL, 10 SKIP (base origin/main) — not ready
```

本轮 precheck 的后端专项另读回 Surefire XML：GlossaryTest 5、ErrorCodeGlossaryTest 2、NamingSurfaceTest 11、DocumentTypeCodeTest 3，共 21 项，均为 0 失败、0 错误、0 跳过。gitleaks 8.30.1 扫描全历史 2818 个提交，未发现泄漏。

最终文件核对：开工快照的卡外文件全部未变，26 个提议 YAML 哈希一致；`apply_nm.py`、`build_terms.py`、N4 文档均未改；本报告原有 625 行逐字节保留，只追加本节。`git diff --check` 通过。`/tmp/nam-1017-glo-02-r1/` 保留开工哈希、原决策与词表快照、两次生成日志、逐字节比对副本、自测／前后端日志、precheck 原文、完整 frontend-verify 日志及 verification.json。临时路径仅供本机复核，关键结果与通知项已写入本报告。
