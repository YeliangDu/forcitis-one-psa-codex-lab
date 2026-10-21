# ARCH-CAPINFO-WB 新词交接

状态：交接｜2026-10-17｜负责：one-architecture（登记归 one-glossary）

依据：CAPINFO 提交 CAPINFO-20261017-111010-cfy 第 CAPINFO-I02、CAPINFO-I05、CAPINFO-I07、CAPINFO-I08、CAPINFO-I09、CAPINFO-C13 题。业务记录取舍已答；下列新英文均为建议，尚未经 one-glossary 定名。terms.yaml 本卡未改。

## 对象

| 编号 | 中文名 | 建议英文名 | bc | 使用范围 |
|---|---|---|---|---|
| OBJ-117 | 勾稽差异案 | ReconciliationDeviationCase | operations.policy | 领域 domain；已登记，复用词表；仅补信息图对应 |
| OBJ-118 | 费用事前申请 | ExpensePreapproval | expense.spend | 领域 domain；独立金额批准申请及后续费用关联 |
| OBJ-119 | 出差申请 | TravelRequest | expense.spend | 领域 domain；独立行程批准申请及同步、后续费用关联 |
| OBJ-120 | 差旅订单 | TravelOrder | expense.spend | 领域 domain；订单行的超标审批、退改、归属与结算核对依据 |
| OBJ-121 | 差旅订单月账单 | TravelMonthlyStatement | expense.spend | 领域 domain；月账单逐项核对签字、差异处理与应付确认 |
| OBJ-122 | 假期余额账户 | LeaveBalanceAccount | organization.leave | 领域 domain；人员 × 假期类型 × 年度账户及核定、使用、结转、折算、手工调整依据 |
| OBJ-123 | 人员薪酬档案 | EmployeeCompensationProfile | organization.payroll | 领域 domain；按生效日期只追加薪资历史；关联人员、任职、薪酬结算与独立扣除记录 |

## 能力（已按对象链补入地图）

| 编号 | 中文名 | 建议英文名 | bc | 使用范围 |
|---|---|---|---|---|
| CAP-054.113 | 用印申请管理 | Seal Use Request Management | organization.administration | 领域 domain；业务能力图名称，非代码聚合类型；I02 的对象术语复用已登记词 |
| CAP-056.114 | IT 需求工单管理 | IT Service Request Management | organization.administration | 领域 domain；业务能力图名称，非代码聚合类型；I02 的对象术语复用已登记词 |
| CAP-056.116 | 员工活动申请管理 | Employee Activity Request Management | organization.administration | 领域 domain；业务能力图名称，非代码聚合类型；I02 的对象术语复用已登记词 |
| CAP-056.122 | 假期余额账户管理 | Leave Balance Account Management | organization.leave | 领域 domain；业务能力图名称，非代码聚合类型；I02 的对象术语复用已登记词 |
| CAP-056.123 | 人员薪酬档案管理 | Employee Compensation Profile Management | organization.payroll | 领域 domain；业务能力图名称，非代码聚合类型；I02 的对象术语复用已登记词 |
| CAP-093.115 | IT 资产使用记录管理 | IT Asset Usage Record Management | organization.administration | 领域 domain；业务能力图名称，非代码聚合类型；I02 的对象术语复用已登记词 |
| CAP-103.117 | 勾稽差异案管理 | Reconciliation Deviation Case Management | operations.policy | 领域 domain；业务能力图名称，非代码聚合类型；I02 的对象术语复用已登记词 |
| CAP-103.121 | 差旅订单月账单管理 | Travel Monthly Statement Management | expense.spend | 领域 domain；业务能力图名称，非代码聚合类型；I02 的对象术语复用已登记词 |
| CAP-118 | 费用事前申请管理 | Expense Preapproval Management | expense.spend | 领域 domain；业务能力图名称，非代码聚合类型；I02 的对象术语复用已登记词 |
| CAP-119 | 出差申请管理 | Travel Request Management | expense.spend | 领域 domain；业务能力图名称，非代码聚合类型；I02 的对象术语复用已登记词 |
| CAP-119.120 | 差旅订单管理 | Travel Order Management | expense.spend | 领域 domain；业务能力图名称，非代码聚合类型；I02 的对象术语复用已登记词 |

## 行政类一级能力草稿（待确认拆分与名称）

这四个编号仅预留草稿，不计入现行 23 个一级能力；当前阶段仍按对象现有父链使用 CAP-054、056、093。是否将对象升一级须随拆分一起核对交确认。

| 草稿编号 | 中文名 | 建议英文名 | bc | 使用范围 |
|---|---|---|---|---|
| CAP-113 | 用印申请管理 | Seal Use Request Management | organization.administration | 领域 domain；待用户确认的一级业务能力草稿 |
| CAP-114 | IT 需求工单管理 | IT Service Request Management | organization.administration | 领域 domain；待用户确认的一级业务能力草稿 |
| CAP-115 | IT 资产使用记录管理 | IT Asset Usage Record Management | organization.administration | 领域 domain；待用户确认的一级业务能力草稿 |
| CAP-116 | 员工活动申请管理 | Employee Activity Request Management | organization.administration | 领域 domain；待用户确认的一级业务能力草稿 |

## 价值流与阶段

| 编号 | 中文名 | 建议英文名 | bc | 使用范围 |
|---|---|---|---|---|
| VS-14 | 获得内部服务 | Obtain Internal Services | organization.administration | 领域 domain；跨事务价值流名，暂名已采用 |
| VS-14 备名 | 获得行政支持 | Obtain Administrative Support | organization.administration | 领域 domain；仅保留备名，不另立概念 |
| VS-14.1 | 提交申请 | Submit Request | organization.administration | 领域 domain；阶段标签，同名异义由管家查重 |
| VS-14.2 | 批准申请 | Approve Request | organization.administration | 领域 domain；阶段标签 |
| VS-14.3 | 办理事项 | Fulfil Service Request | organization.administration | 领域 domain；阶段标签 |
| VS-14.4 | 确认办结 | Confirm Service Completion | organization.administration | 领域 domain；阶段标签 |

OBJ-113–116 为既有对象，本卡未新增编号；其名称及状态词若仍未登记，随 one-glossary 原提议批次处理。上述能力的 bc 仅用于词表归集，不据能力图重划 BC，也不将 bc 字段加入能力 SSOT。
