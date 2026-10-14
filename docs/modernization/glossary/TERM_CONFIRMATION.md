# 用语确认表

规则（用户 2026-10-14）：「所有跟正式用语不符合的，都归为我们公司内部的方言，新系统一律采用正式用语，不把方言带入新系统，不确定哪些是正式用语，哪些是方言可以登记用语表，我来确认。」

**怎么确认**：在「确认」列填 `正式`、`方言` 或 `改为：<用语>`，也可以直接回复编号（例如「A3 方言，B12 同意，C 组全部同意」）。确认后由 `glossary/build_terms.py` 更新 `terms.yaml`，状态改为已批准。

已确定为方言、无需再确认的（如「产值确认单」「产值」= 收入确认单）不在本表，见 `terms.yaml` 中 `type: dialect` 的别名。

## A. 公司叫法：正式用语还是方言？（20 项）

这些是公司在用的中文叫法，审计没有把握判定。选「正式」则保留并与标准用语并列为同义正式名（新系统只用一个，取右列）；选「方言」则禁止进入新系统。

| 编号 | 公司叫法 | 对应正式用语（中 / 英） | 所属模块 | 依据 | 建议 | 确认 |
|---|---|---|---|---|---|---|
| A1 | 销售合同（来源：A8 模板「销售合同新增 / 归档」、档案「销售合同档案」） | 合同 / `Contract` | `contract.baseline` | 企业会计准则第 14 号——收入（财会〔2017〕22 号）；IFRS 15 Revenue from Contracts with Customers | 方言 |  |
| A2 | 付款条件档案（来源：A8 档案 formmain_1321（PSA 报价条款同步）） | 付款条件项 / `PaymentTermItem` | `contract.baseline` | 中华人民共和国民法典·合同编；IFRS 15 Revenue from Contracts with Customers | 方言 |  |
| A3 | 销售合同变更（来源：A8 模板） | 变更 / `ChangeOrder` | `contract.changeorder` | GB/T 50500《建设工程工程量清单计价标准》（原 GB 50500-2013 规范）；FIDIC Conditions of Contract for Construction 2017（Red Book） | 方言 |  |
| A4 | 项目工时填报单（来源：A8 模板（2026-04-30 起停用）） | 工时单 / `Timesheet` | `cost.timesheet` | PSA 行业通行写法（Kantata / Certinia / Deltek 等） | 方言 |  |
| A5 | 项目委外（来源：A8 模板「项目委外采购申请 / 采购合同 / 交付确认单」） | 分包成本 / `SubcontractCost` | `cost.costing` | 企业产品成本核算制度（试行）（财会〔2013〕17 号）；FIDIC Conditions of Contract for Construction 2017（Red Book） | 方言 |  |
| A6 | BU岗位人天单价（来源：A8 档案 formmain_1176） | 标准费率 / `StandardCostRateCard` | `cost.costing` | 企业产品成本核算制度（试行）（财会〔2013〕17 号）；PSA 行业通行写法（Kantata / Certinia / Deltek 等） | 方言 |  |
| A7 | 发票档案（来源：A8 档案 formmain_0428） | 开票 / `InvoiceIssued` | `receivables.wip` | 企业会计准则第 14 号——收入（财会〔2017〕22 号） | 方言 |  |
| A8 | 项目开票申请（来源：A8 模板） | 开票申请 / `BillingRequest` | `receivables.wip` | PSA 行业通行写法（Kantata / Certinia / Deltek 等） | 方言 |  |
| A9 | 首付款开票申请（来源：A8 模板） | 开票申请 / `BillingRequest` | `receivables.wip` | PSA 行业通行写法（Kantata / Certinia / Deltek 等） | 方言 |  |
| A10 | 回款单档案（来源：A8 档案 formmain_0569） | 回款单 / `CashReceipt` | `receivables.collection` | PSA 行业通行写法（Kantata / Certinia / Deltek 等） | 方言 |  |
| A11 | 应收坏账申请（来源：A8 模板「应收坏账申请流程」；计提还是核销待财务确认） | 坏账核销 / `ArWrittenOff` | `receivables.collection` | 企业会计准则第 22 号——金融工具确认和计量（预期信用损失）；IFRS 9 Financial Instruments（expected credit losses） | 方言 |  |
| A12 | 项目结算（来源：A8 模板「项目结算申请」） | 竣工结算 / `FinalAccount` | `receivables.wip` | GB/T 50500《建设工程工程量清单计价标准》（原 GB 50500-2013 规范）；FIDIC Conditions of Contract for Construction 2017（Red Book） | 方言 |  |
| A13 | 报价档案（来源：A8 档案 formmain_0366） | 报价单 / `Quote` | `presales.quoting` | PSA 行业通行写法（Kantata / Certinia / Deltek 等） | 方言 |  |
| A14 | 项目立项（来源：A8 模板「项目立项申请」） | 项目 / `Project` | `delivery.execution` | PMI PMBOK Guide；GB/T 50326-2017 建设工程项目管理规范 | 方言 |  |
| A15 | 项目档案（来源：A8 模板「项目档案变更」、档案「项目档案底表」） | 项目 / `Project` | `delivery.execution` | PMI PMBOK Guide；GB/T 50326-2017 建设工程项目管理规范 | 方言 |  |
| A16 | 项目预算编制（来源：A8 模板） | 项目预算（WBS 预算） / `ProjectBudget` | `delivery.execution` | PMI PMBOK Guide | 方言 |  |
| A17 | 日常费用报销单（来源：A8 模板） | 报销单 / `ExpenseClaim` | `expense.spend` | PSA 行业通行写法（Kantata / Certinia / Deltek 等） | 方言 |  |
| A18 | 业务招待费用报销单（来源：A8 模板） | 报销单 / `ExpenseClaim` | `expense.spend` | PSA 行业通行写法（Kantata / Certinia / Deltek 等） | 方言 |  |
| A19 | 差旅费用报销单（来源：A8 模板） | 差旅 / `TravelExpense` | `expense.spend` | PSA 行业通行写法（Kantata / Certinia / Deltek 等） | 方言 |  |
| A20 | 预收款单（来源：A8 模板「预收款单新增」） | 合同负债 / `ContractLiability` | `receivables.wip` | 企业会计准则第 14 号——收入（财会〔2017〕22 号）；IFRS 15 Revenue from Contracts with Customers | 方言 |  |

## B. 改为标准用语的建议（0 项）

原词表用语不符合准则或行业标准，建议改名；原叫法确认后归为方言。标 💰 的涉及会计口径，建议同时请财务确认。

| 编号 | 原用语（将归为方言） | 建议正式用语（中 / 英） | 所属模块 | 依据 | 改名编号 | 确认 |
|---|---|---|---|---|---|---|

## C. 公司自有概念，没有外部标准用语（0 项，可整组确认）

这些概念在准则、国标、PMBOK、FIDIC 中都没有对应条目，是公司自己的经营管理概念。建议以词表的定义作为正式用语；如果其中有你认为是口头叫法、应另取正式名的，单独标出。

| 编号 | 模块 | 用语（中 / 英） | 确认 |
|---|---|---|---|
