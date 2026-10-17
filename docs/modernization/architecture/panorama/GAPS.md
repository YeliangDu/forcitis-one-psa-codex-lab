<!-- 由 architecture/panorama/panorama_build.py 生成，勿手改 -->

# 全景追溯缺口

每轮生成一次，反复跑到收敛。缺口是「缺上层」还是「本身多余」，由 one-architecture 逐条判定，写进层笔记的待决问题。

| 层 | 节点数 |
|---|---|
| vs | 15 |
| stage | 61 |
| obj | 103 |
| prc | 200 |
| agg | 130 |
| inv | 134 |
| bc | 31 |
| sd | 13 |
| table | 25 |
| metric | 76 |

对象按分类：业务对象 95、从属对象 6、参照数据 1、视图 1。

| 检查 | 含义 | 缺口 |
|---|---|---|
| TD-1 | 每个阶段都有对象状态作为门（进入或退出条件引用对象状态） | 1 |
| TD-2 | 每个有效对象都有流程承载（同 anchoring_check C2；OBJD 定论的从属、参照、视图按定论排除） | 0 |
| TD-3 | 每个有效一级业务对象都有聚合（聚合「对应」列；从属、参照、视图不要求） | 1 |
| TD-4 | 每个聚合都归属一个 BC | 10 |
| TD-5 | 每个聚合都有不变量 | 29 |
| TD-6 | 每个聚合都有表 | 130 |
| TD-7 | 每个 BC 都归属一个子域（模块命名空间可在 subdomains.yaml 找到） | 3 |
| TD-8 | 每个阶段都有指标 | 61 |
| BU-1 | 每个有效流程都能归集到价值流：所标阶段或支撑本段，且有作用对象（同 anchoring_check C1） | 0 |
| BU-2 | 流程主要对象引用的编号存在且是有效对象（主要对象为空不算缺口：作用对象由 BU-1 / anchoring C1 判） | 0 |
| BU-3 | 每个不变量都归属存在的聚合 | 0 |
| BU-4 | 每个聚合对应的对象都出现在某个阶段的门或段内状态中 | 8 |
| BU-5 | 每张表都归属聚合，聚合能追到对象 | 25 |
| BU-6 | 每个指标都能追到阶段或流程 | 76 |

## TD-1 每个阶段都有对象状态作为门（进入或退出条件引用对象状态）（1）

- VS-02.5 完成招标采购配合

## TD-3 每个有效一级业务对象都有聚合（聚合「对应」列；从属、参照、视图不要求）（1）

- OBJ-19 招标项目

## TD-4 每个聚合都归属一个 BC（10）

- AGG-110 证据关联
- AGG-47 收入确认
- AGG-109 利润分配
- AGG-73 月度薪酬结算
- AGG-74 社保与公积金参保
- AGG-75 激励方案版本
- AGG-76 奖金核定
- AGG-93 人员薪酬档案
- AGG-95 个税扣缴与社保公积金申报
- AGG-83 账簿

## TD-5 每个聚合都有不变量（29）

- AGG-33 建设工程
- AGG-08 市场活动
- AGG-23 完工估算
- AGG-24 项目团队
- AGG-25 资源需求
- AGG-26 资源分配
- AGG-31 延期申请
- AGG-32 发包人提供资料
- AGG-34 检查计划
- AGG-130 费用事前申请
- AGG-132 出差申请
- AGG-62 人员
- AGG-65 招聘需求
- AGG-68 工作日历年度
- AGG-70 请假
- AGG-72 加班
- AGG-74 社保与公积金参保
- AGG-75 激励方案版本
- AGG-76 奖金核定
- AGG-93 人员薪酬档案
- AGG-95 个税扣缴与社保公积金申报
- AGG-120 专项附加扣除声明
- AGG-80 预算账户
- AGG-83 账簿
- AGG-87 资金账户
- AGG-90 增值税、附加税、企业所得税申报
- AGG-91 公司资产
- AGG-116 员工活动申请
- AGG-121 IT 资产维修单

## TD-6 每个聚合都有表（130）

- AGG-01 往来单位
- AGG-02 资质证书
- AGG-33 建设工程
- AGG-03 服务产品版本
- AGG-04 阶段类型版本
- AGG-05 标准成本费率表版本
- AGG-06 计费系数版本
- AGG-07 协议价目
- AGG-08 市场活动
- AGG-09 线索
- AGG-10 商机
- AGG-11 投标
- AGG-12 报价版本
- AGG-18 独立性检查
- AGG-102 客户活动
- AGG-19 项目
- AGG-20 项目 WBS
- AGG-21 交付计划版本
- AGG-22 项目预算版本
- AGG-23 完工估算
- AGG-24 项目团队
- AGG-25 资源需求
- AGG-26 资源分配
- AGG-31 延期申请
- AGG-103 项目复盘
- AGG-104 项目风险与问题
- AGG-27 交付物
- AGG-28 不符合项
- AGG-29 返工认定
- AGG-30 设计变更
- AGG-32 发包人提供资料
- AGG-38 证据
- AGG-110 证据关联
- AGG-105 客户满意度调查
- AGG-34 检查计划
- AGG-35 检查
- AGG-36 检查表模板版本
- AGG-37 外部检测
- AGG-13 协议
- AGG-14 框架协议限额
- AGG-15 协议变更
- AGG-16 索赔
- AGG-17 风险交付授权
- AGG-39 工时单
- AGG-40 分摊规则版本
- AGG-41 人工成本分配运行
- AGG-42 分摊运行
- AGG-43 成本结转批次
- AGG-122 售前投入归属登记
- AGG-123 成本核算参数版本
- AGG-44 开票与收入额度
- AGG-45 开票申请
- AGG-46 销项发票
- AGG-47 收入确认
- AGG-92 可触发确认的证据类型开放配置版本
- AGG-49 应收往来款项
- AGG-50 收款
- AGG-51 保证金与押金
- AGG-52 应付往来款项
- AGG-53 付款
- AGG-54 合伙人出资
- AGG-109 利润分配
- AGG-55 供方协议
- AGG-56 分包变更
- AGG-57 供方履约确认
- AGG-58 供方协议履约额度
- AGG-59 费用报销
- AGG-130 费用事前申请
- AGG-131 差旅订单月账单
- AGG-132 出差申请
- AGG-133 出差补贴标准版本
- AGG-60 法人主体
- AGG-61 组织单元
- AGG-62 人员
- AGG-63 任职
- AGG-64 职位目录与职级
- AGG-65 招聘需求
- AGG-66 注册执业证书
- AGG-67 劳动合同
- AGG-106 绩效评估
- AGG-117 法人归属
- AGG-68 工作日历年度
- AGG-108 标准工时版本
- AGG-69 考勤记录
- AGG-70 请假
- AGG-71 假期余额账户
- AGG-72 加班
- AGG-97 月度考勤确认
- AGG-118 假期类型与额度规则
- AGG-73 月度薪酬结算
- AGG-74 社保与公积金参保
- AGG-75 激励方案版本
- AGG-76 奖金核定
- AGG-93 人员薪酬档案
- AGG-95 个税扣缴与社保公积金申报
- AGG-119 薪酬规则版本
- AGG-120 专项附加扣除声明
- AGG-77 过账
- AGG-78 口径版本
- AGG-126 勾稽差异案
- AGG-79 经营计划版本
- AGG-80 预算账户
- AGG-81 会计期间
- AGG-96 第 13 期启用登记
- AGG-82 科目表版本
- AGG-83 账簿
- AGG-84 会计分录
- AGG-85 收付 ↔ 权责科目映射表版本
- AGG-94 控制科目授权表版本
- AGG-98 受控手工凭证
- AGG-99 期末结转运行
- AGG-100 期间余额快照
- AGG-101 外部法定账对账
- AGG-125 期初余额
- AGG-127 口径转换规则版本
- AGG-128 期末结转模板版本
- AGG-129 现金流量项目表版本
- AGG-86 报表行定义版本
- AGG-87 资金账户
- AGG-88 银行流水
- AGG-89 进项发票
- AGG-90 增值税、附加税、企业所得税申报
- AGG-91 公司资产
- AGG-124 资产类别与折旧政策版本
- AGG-107 知识条目
- AGG-113 用印申请
- AGG-114 IT 需求工单
- AGG-115 IT 资产使用记录
- AGG-116 员工活动申请
- AGG-121 IT 资产维修单

## TD-7 每个 BC 都归属一个子域（模块命名空间可在 subdomains.yaml 找到）（3）

- B-01 往来单位（含外部工程登记）（模块 masterdata.counterparty）
- B-02 服务与定价目录（模块 masterdata.catalog）
- B-32 报价发出编排（暂名）（模块 None）

## TD-8 每个阶段都有指标（61）

- VS-01.1 采集线索
- VS-01.2 赢得委托
- VS-02.0 开始项目
- VS-02.1 确认概念
- VS-02.2 确认方案
- VS-02.3 完成初步设计
- VS-02.4 完成深化设计
- VS-02.5 完成招标采购配合
- VS-02.6 完成施工配合
- VS-02.7 完成收尾
- VS-03.1 编制检查计划
- VS-03.2 完成现场检查
- VS-03.3 发布不符合项
- VS-03.4 跟进整改
- VS-03.5 登记报告认可
- VS-04.1 签署合同
- VS-04.6 完成合同履行
- VS-04.2 确认收入
- VS-04.3 开具发票
- VS-04.4 回收款项
- VS-04.5 确认最终价款
- VS-05.1 完成供方准入
- VS-05.2 签署供方协议
- VS-05.3 确认供方履约
- VS-05.4 确认应付款项
- VS-05.5 完成付款
- VS-05.6 完成供方结算
- VS-06.1 完成申报缴款
- VS-06.2 完成资质维护
- VS-07.1 提出招聘需求
- VS-07.2 完成录用
- VS-07.3 完成人员入职与任职
- VS-08.0 批准事前申请
- VS-08.1 完成借款发放与归还
- VS-08.2 完成报销核定
- VS-08.3 完成报销付款
- VS-09.1 编制计划
- VS-09.2 变更预测
- VS-09.3 完成经营复盘
- VS-10.0 执行记账结转
- VS-10.1 关闭会计期间
- VS-10.2 发布报表
- VS-11.1 编制知识条目
- VS-11.2 审核并发布知识条目
- VS-11.3 修订并废除知识条目
- VS-12.1 确认收到出资
- VS-12.2 分配利润
- VS-12.3 退还出资
- VS-13.1 确认月度考勤
- VS-13.2 完成绩效评估与奖金核定
- VS-13.3 完成薪酬结算
- VS-13.4 完成个税及社保公积金申报缴纳
- VS-13.5 完成薪酬发放
- VS-13.6 完成离职结清
- VS-14.1 提交申请
- VS-14.2 批准申请
- VS-14.3 完成事项办理
- VS-14.4 确认办结
- VS-15.1 拟订
- VS-15.2 批准生效
- VS-15.3 修订停用

## BU-4 每个聚合对应的对象都出现在某个阶段的门或段内状态中（8）

- AGG-01 → OBJ-03 联系人
- AGG-12 → OBJ-22 报价估算分解
- AGG-13 → OBJ-06 合同范围条目
- AGG-39 → OBJ-66 工时单
- AGG-47 → OBJ-81 收入确认
- AGG-69 → OBJ-70 考勤记录
- AGG-83 → OBJ-96 账簿
- AGG-88 → OBJ-90 银行流水

## BU-5 每张表都归属聚合，聚合能追到对象（25）

- cost_timesheet.timesheets（对象 无）
- organization_attendance.work_calendar_years（对象 无）
- organization_attendance.work_calendar_days（对象 无）
- organization_workforce.legal_entities（对象 无）
- organization_workforce.sync_runs（对象 无）
- organization_workforce.sync_diffs（对象 无）
- organization_workforce.sync_run_held_separations（对象 无）
- organization_workforce.legal_entity_attributions（对象 无）
- organization_workforce.org_unit_legal_entities（对象 无）
- organization_workforce.employee_legal_entities（对象 无）
- organization_workforce.legal_entity_attribution_corrections（对象 无）
- platform_approval.approval_requests（对象 无）
- platform_approval.approval_task_records（对象 无）
- platform_approval.start_rounds（对象 无）
- platform_approval.approval_tasks（对象 无）
- platform_identity.user_accounts（对象 无）
- platform_identity.account_roles（对象 无）
- platform_identity.roles（对象 无）
- platform_identity.role_permissions（对象 无）
- platform_identity.emergency_admins（对象 无）
- platform_identity.authorization_requests（对象 无）
- platform_integration.source_references（对象 无）
- platform_integration.outbound_deliveries（对象 无）
- platform_numbering.numbering_rules（对象 无）
- platform_numbering.numbering_rule_versions（对象 无）

## BU-6 每个指标都能追到阶段或流程（76）

- business_variance 经营偏差
- uncontracted_exposure 无合同风险敞口
- estimate_to_complete ETC
- delivery_variance 交付偏差
- post_completion_service_hours 后期服务工时
- quality_loss 质量损失
- rework_rate 返工率
- defect_rate 质量缺陷率
- effective_hours 有效工时
- ineffective_hours 无效工时
- rework_hours 返工工时
- billable_hours 可计费工时
- capacity_realization 产能变现
- idle_capacity 闲置产能
- project_cost 项目成本
- direct_labor 直接人工
- direct_expense 直接费用
- outsourcing_cost 外包成本
- subcontract_cost 分包成本
- cost_budget 成本预算
- cost_budget_variance 成本预算偏差
- unbilled_wip 未开票在制（WIP）
- measure_of_progress 履约进度
- uncontracted_wip 无合同 WIP
- accounts_receivable 应收账款
- aging 账龄
- bad_debt_risk 坏账风险
- impairment_risk 减值风险
- cash_conversion 现金转化
- time_capture_rate 工时捕获率
- statutory_cost 法定成本
- intercompany_balance 内部往来
- costs_to_obtain_contract 合同取得成本
- cost_of_sales 营业成本
- remaining_performance_obligation 剩余履约义务
- estimate_at_completion 完工估算
- planned_value 计划价值
- earned_value 挣值
- actual_cost 实际成本
- cost_performance_index 成本绩效指数
- schedule_performance_index 进度绩效指数
- schedule_variance 进度偏差
- earned_value_cost_variance 挣值成本偏差
- utilization_rate 利用率
- accrual_basis_revenue 权责发生制收入
- reversible_balance 可冲销余额
- job_failure 作业失败
- contribution_margin 贡献毛利
- contribution_margin_ratio 贡献毛利率
- net_service_revenue 净服务收入
- management_operating_profit 经营利润
- management_operating_profit_margin 经营利润率
- indirect_cost 间接成本
- pass_through_cost 转付成本
- labor_cost_multiplier 人工成本产出倍数
- target_variance 目标偏差
- available_budget 可用额度
- plan_target_value 计划目标值
- target_attainment_rate 目标达成率
- budget_consumption_rate 预算消耗率
- new_contract_value 新签合同额
- project_initiation_value 项目立项金额
- aged_receivable 账龄超 N 天应收
- days_sales_outstanding 应收周转天数
- billing_conversion_rate 开票转化率
- collection_rate 回款率
- overdue_billing_commitment 承诺开票超期
- allowance_for_bad_debts 坏账准备
- average_headcount 平均在册人数
- attrition_rate 离职率
- pipeline_value 商机管道金额
- win_rate 赢单率
- lead_conversion_rate 线索转化率
- expense_payment 费用支付
- kickoff_completion_rate 启动会完成率
- internal_review_coverage 内部评审覆盖率
