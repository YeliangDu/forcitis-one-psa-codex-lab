<!-- 由 build_catalog.py 生成，不手改 -->
# 流程目录（生成）

由 build_catalog.py 生成，不手改；来源 `catalog.yaml`（业务流程唯一登记）。

共 203 条，编号 PRC-01 至 PRC-203。

### 按类型

| 类型 | 数量 |
|---|---|
| business | 107 |
| event_policy | 31 |
| single_aggregate | 29 |
| read_model | 6 |
| integration | 15 |
| merged | 11 |
| retired | 4 |
| 合计 | 203 |

### 按组（全部类型）

| 组 | 数量 |
|---|---|
| revenue | 80 |
| finance | 54 |
| people | 52 |
| platform | 17 |
| 合计 | 203 |

### 按组（仅业务流程）

| 组 | 数量 |
|---|---|
| revenue | 46 |
| finance | 29 |
| people | 32 |
| 合计 | 107 |

### 按价值流（仅业务流程；跨价值流的流程各计一次，故合计大于业务流程数）

| 价值流 | 数量 |
|---|---|
| VS-01 | 7 |
| VS-02 | 18 |
| VS-03 | 7 |
| VS-04 | 15 |
| VS-05 | 10 |
| VS-06 | 3 |
| VS-07 | 7 |
| VS-08 | 6 |
| VS-09 | 3 |
| VS-10 | 9 |
| VS-11 | 1 |
| VS-12 | 2 |
| VS-13 | 13 |
| VS-14 | 7 |
| VS-15 | 5 |
| 支撑（不在价值流上） | 5 |
| 合计 | 118 |

业务流程中尚未填 stages：0 条。

## 总表

| 编号 | 名称 | 类型 | 并入 | 阶段 | owner_bc | 组 | 状态 | 人工编排 | bpmn | 可执行 BPMN |
|---|---|---|---|---|---|---|---|---|---|---|
| PRC-01 | 举办市场活动 | single_aggregate |  | VS-01.1 | B-03 | revenue | draft | 否 |  |  |
| PRC-02 | 计划并完成客户活动 | single_aggregate |  | VS-01.1 | B-03 | revenue | draft | 否 |  |  |
| PRC-03 | 跟进线索并转商机 | business |  | VS-01.1, VS-01.2 | B-03 | revenue | draft | 否 |  |  |
| PRC-04 | 线索到商机：线索已确认后的下一步就是新建商机 | merged | PRC-03 |  |  | revenue | draft | 待定 |  |  |
| PRC-05 | 维护往来单位档案 | single_aggregate |  | VS-01.1, VS-05.1 | B-01 | revenue | draft | 否 |  |  |
| PRC-06 | 做独立性检查 | single_aggregate |  | VS-01.2 | B-03 | revenue | draft | 否 |  |  |
| PRC-07 | 编制并生效报价 | business |  | VS-01.2 | B-03 | revenue | draft | 是 |  |  |
| PRC-08 | 参加投标，缴纳保证金并登记结果 | business |  | VS-01.2 | B-03 | revenue | draft | 是 |  |  |
| PRC-09 | 投标、结果、保证金：中标赢单与未中标丢单是投标结果的事件策略，委托赢单与放弃是同一商机上的人工分支 | merged | PRC-08 |  |  | revenue | draft | 待定 |  |  |
| PRC-10 | 收付保证金与保函 | single_aggregate |  | VS-01.2, VS-04.1 | B-09 | revenue | draft | 否 |  |  |
| PRC-11 | 投标、结果、保证金：未中标后退还保证金是下游 BC 订阅投标未中标的反应 | merged | PRC-08 |  |  | revenue | draft | 待定 |  |  |
| PRC-12 | 发布服务产品与阶段类型版本 | single_aggregate |  | VS-01.2 | B-02 | revenue | draft | 否 |  |  |
| PRC-13 | 批准并生效费率、计费系数与协议价目版本 | business |  | VS-01.2, VS-02.0 | B-02 | revenue | draft | 否 |  |  |
| PRC-14 | 登记建设工程与招标项目 | single_aggregate |  | VS-01.2, VS-02.6, VS-03.1 | B-01 | revenue | draft | 否 |  |  |
| PRC-15 | 未签约的售前投入转费用 | event_policy |  | VS-01.2 | B-07 | finance | draft | 否 |  |  |
| PRC-16 | 申请并转正风险交付授权 | business |  | VS-01.2, VS-02.0, VS-04.1 | B-05 | revenue | draft | 是 |  |  |
| PRC-17 | 建立项目并组建团队 | business |  | VS-02.0, VS-03.1 | B-04 | revenue | draft | 否 |  |  |
| PRC-18 | 编制并批准交付计划 | business |  | VS-02.0 | B-04 | revenue | draft | 否 |  |  |
| PRC-19 | 编制并锁定项目预算 | business |  | VS-02.0 | B-04 | revenue | draft | 是 |  |  |
| PRC-20 | 提出并满足资源需求 | business |  | VS-02.0, VS-02.1, VS-02.2, VS-02.3, VS-02.4, VS-02.5, VS-02.6 | B-04 | revenue | draft | 否 |  |  |
| PRC-21 | 推进阶段与工作包 | single_aggregate |  | VS-02.0, VS-02.1, VS-02.2, VS-02.3, VS-02.4, VS-02.5, VS-02.6 | B-04 | revenue | draft | 否 |  |  |
| PRC-22 | 查看阶段门预警 | read_model |  | VS-02.0, VS-02.1, VS-02.2, VS-02.3, VS-02.4, VS-02.5, VS-02.6, VS-02.7 | reporting | revenue | draft | 否 |  |  |
| PRC-23 | 填报并审批工时单 | business |  |  | B-06 | revenue | draft | 是 |  | cost_timesheet_approval.bpmn20.xml |
| PRC-24 | 变更项目状态 | business |  | VS-02.0, VS-02.1, VS-02.2, VS-02.3, VS-02.4, VS-02.5, VS-02.6, VS-02.7 | B-04 | revenue | draft | 否 |  |  |
| PRC-25 | 转入售前投入 | event_policy |  | VS-02.0, VS-04.1 | B-07 | finance | draft | 否 |  |  |
| PRC-26 | 接收并确认发包人提供资料 | business |  | VS-02.0, VS-02.1, VS-02.2, VS-02.3, VS-02.4 | B-28 | revenue | draft | 是 |  |  |
| PRC-27 | 登记并处置项目风险与问题 | single_aggregate |  | VS-02.0, VS-02.1, VS-02.2, VS-02.3, VS-02.4, VS-02.5, VS-02.6, VS-02.7 | B-04 | revenue | draft | 否 |  |  |
| PRC-28 | 编制、评审并签发交付物，送达并登记客户认可 | business |  | VS-02.1, VS-02.2, VS-02.3, VS-02.4, VS-02.5, VS-02.6, VS-03.5 | B-28 | revenue | user_confirmed | 是 |  |  |
| PRC-29 | 签发到认可：交付物签发后送达与登记客户认可是同一交付物的连续步骤 | merged | PRC-28 |  |  | revenue | draft | 待定 |  |  |
| PRC-30 | 提出并批准完工估算 | business |  | VS-02.0, VS-02.1, VS-02.2, VS-02.3, VS-02.4, VS-02.5, VS-02.6, VS-02.7 | B-04 | revenue | draft | 否 |  |  |
| PRC-31 | 提出并发出设计变更 | business |  | VS-02.2, VS-02.3, VS-02.4, VS-02.5, VS-02.6 | B-28 | revenue | draft | 否 |  |  |
| PRC-32 | 申请并答复工期顺延 | business |  | VS-02.2, VS-02.3, VS-02.4, VS-02.5, VS-02.6 | B-04 | revenue | draft | 是 |  |  |
| PRC-33 | 采集并封存证据 | single_aggregate |  | VS-02.4, VS-02.7, VS-03.2, VS-04.2 | B-28 | revenue | draft | 否 |  |  |
| PRC-34 | 处置我方成果不符合项 | business |  | VS-02.2, VS-02.3, VS-02.4, VS-02.6, VS-02.7 | B-28 | revenue | draft | 否 |  |  |
| PRC-35 | 清算项目成本 | event_policy |  | VS-02.7 | B-07 | finance | draft | 否 |  |  |
| PRC-36 | 结项并归档项目 | business |  | VS-02.7 | B-29 | revenue | draft | 是 |  |  |
| PRC-37 | 开展客户满意度调查 | business |  | VS-02.7 | B-28 | revenue | draft | 否 |  |  |
| PRC-38 | 编制并发布项目复盘 | business |  | VS-02.7 | B-04 | revenue | draft | 否 |  |  |
| PRC-39 | 结项含封存与归档：封存成果、结项检查与审批、结项 | merged | PRC-36 |  |  | revenue | draft | 待定 |  |  |
| PRC-40 | 结项含封存与归档：项目已结项后归档 | merged | PRC-36 |  |  | revenue | draft | 待定 |  |  |
| PRC-41 | 制定并生效检查计划 | business |  | VS-03.1 | B-26 | revenue | draft | 否 |  |  |
| PRC-42 | 发布检查表模板版本 | single_aggregate |  | VS-15.1, VS-15.2, VS-15.3 | B-26 | revenue | draft | 否 |  |  |
| PRC-43 | 实施并锁定检查记录 | business |  | VS-03.1, VS-03.2, VS-02.6 | B-26 | revenue | draft | 否 |  |  |
| PRC-44 | 发布客户资产类不符合项并建议外部检测 | business |  | VS-03.2, VS-03.3 | B-26 | revenue | draft | 否 |  |  |
| PRC-45 | 跟踪整改并复查闭合 | business |  | VS-03.4 | B-26 | revenue | draft | 是 |  |  |
| PRC-46 | 采信外部检测报告 | business |  | VS-03.4 | B-26 | revenue | draft | 否 |  |  |
| PRC-47 | 起草并回签协议 | business |  | VS-04.1 | B-05 | revenue | draft | 是 |  |  |
| PRC-48 | 提出并生效协议变更或签证 | business |  | VS-04.6 | B-05 | revenue | draft | 是 |  |  |
| PRC-49 | 提出并认定索赔 | business |  | VS-04.6, VS-04.5 | B-05 | revenue | draft | 是 |  |  |
| PRC-50 | 占用框架限额并提交任务订单 | business |  | VS-04.6 | B-05 | revenue | draft | 是 |  |  |
| PRC-51 | 建立并更新开票与收入额度 | event_policy |  | VS-04.1, VS-04.2, VS-04.3, VS-04.6 | B-08 | revenue | draft | 否 |  |  |
| PRC-52 | 开放收入确认证据类型 | single_aggregate |  | VS-15.1, VS-15.2, VS-15.3 | B-08 | revenue | draft | 否 |  |  |
| PRC-53 | 确认收入 | event_policy |  | VS-04.2 | B-08 | revenue | draft | 否 |  |  |
| PRC-54 | 视为同意期满处理 | business |  | VS-04.2 | B-08 | revenue | draft | 是 |  |  |
| PRC-55 | 结转合同履约成本 | event_policy |  | VS-04.2 | B-07 | finance | draft | 否 |  |  |
| PRC-56 | 付款条件项转为可结算 | event_policy |  | VS-04.3 | B-05 | revenue | draft | 否 |  |  |
| PRC-57 | 申请并开具销项增值税发票 | business |  | VS-04.3 | B-08 | revenue | draft | 是 |  |  |
| PRC-58 | 红冲销项发票 | business |  | VS-04.3 | B-08 | revenue | draft | 否 |  |  |
| PRC-59 | 创建应收款项 | event_policy |  | VS-04.4 | B-09 | revenue | draft | 否 |  |  |
| PRC-60 | 认领收款并核销应收 | business |  | VS-04.4 | B-09 | revenue | draft | 否 |  |  |
| PRC-61 | 检测并催收逾期应收款项 | business |  | VS-04.4 | B-09 | revenue | draft | 否 |  |  |
| PRC-62 | 查看质量保证金到期提示 | read_model |  | VS-04.4 | B-09 | revenue | draft | 否 |  |  |
| PRC-63 | 商业汇票兑付与逾期 | business |  | VS-04.4 | B-09 | revenue | draft | 否 |  |  |
| PRC-64 | 导入银行流水 | business |  |  | B-22 | finance | draft | 否 |  |  |
| PRC-65 | 维护资金账户 | single_aggregate |  | VS-15.2, VS-15.3 | B-22 | finance | draft | 否 |  |  |
| PRC-66 | 确认最终价款并结算协议 | business |  | VS-04.5, VS-04.6 | B-08 | revenue | draft | 否 |  |  |
| PRC-67 | 履约完成到结算：协议已完成后编制结算书、确认最终价款、结算 | merged | PRC-66 |  |  | revenue | draft | 待定 |  |  |
| PRC-68 | 终止协议 | single_aggregate |  | VS-04.5 | B-05 | revenue | draft | 否 |  |  |
| PRC-69 | 申请并核验供方角色 | business |  | VS-05.1 | B-01 | revenue | draft | 否 |  |  |
| PRC-70 | 起草并生效供方协议 | business |  | VS-05.2 | B-11 | finance | draft | 是 |  |  |
| PRC-71 | 生效分包或采购变更（含增减量、部分取消） | business |  | VS-05.2 | B-11 | finance | draft | 否 |  |  |
| PRC-72 | 确认供方履约 | business |  | VS-05.3 | B-11 | finance | draft | 否 |  |  |
| PRC-73 | 登记并查验进项增值税发票 | business |  | VS-05.4 | B-23 | finance | draft | 否 |  |  |
| PRC-74 | 提交供方应付款项 | business |  | VS-05.4 | B-11 | finance | draft | 否 |  |  |
| PRC-75 | 发起、审批并执行付款 | business |  | VS-05.5, VS-08.3, VS-13.5 | B-10 | finance | draft | 是 |  |  |
| PRC-76 | 完成并结算供方协议 | business |  | VS-05.6 | B-11 | finance | draft | 否 |  |  |
| PRC-77 | 申报并缴纳税款 | business |  | VS-06.1 | B-23 | finance | draft | 否 |  |  |
| PRC-78 | 年检、核查与延续企业资质 | business |  | VS-06.2 | B-01 | revenue | draft | 是 |  |  |
| PRC-79 | 登记继续教育并延续注册执业证书 | single_aggregate |  | VS-06.2 | B-13 | people | draft | 否 |  |  |
| PRC-80 | 维护法人、组织与岗位 | business |  | VS-15.2, VS-15.3 | B-13 | people | draft | 否 |  |  |
| PRC-81 | 提出并批准招聘需求 | business |  | VS-07.1 | B-13 | people | draft | 是 |  |  |
| PRC-82 | 登记候选人并作出录用决定 | business |  | VS-07.2 | B-13 | people | draft | 否 |  |  |
| PRC-83 | 办理员工入职与任职 | business |  | VS-07.3 | B-13 | people | user_confirmed | 否 |  |  |
| PRC-84 | 增员参保并建立薪酬档案 | business |  | VS-07.3 | B-15 | people | user_confirmed | 否 |  |  |
| PRC-85 | 评估并转正试用期员工 | business |  | VS-07.3, VS-13.2 | B-13 | people | draft | 否 |  |  |
| PRC-86 | 发放、归还与催还员工借款 | business |  | VS-08.1 | B-09 | revenue | draft | 否 |  |  |
| PRC-87 | 提交、核定并确认报销应付 | business |  | VS-08.2 | B-12 | finance | user_confirmed | 是 |  |  |
| PRC-88 | 编制、批准并基线化经营计划与预算 | business |  | VS-09.1, VS-09.3 | B-18 | people | draft | 是 |  |  |
| PRC-89 | 编制滚动预测 | business |  | VS-09.2 | B-18 | people | draft | 否 |  |  |
| PRC-90 | 出具经营月报 | read_model |  | VS-09.3, VS-10.2 | reporting | finance | draft | 否 |  |  |
| PRC-91 | 认定工时单有效性 | event_policy |  |  | B-06 | revenue | draft | 否 |  |  |
| PRC-92 | 过账业务事实 | business |  | VS-10.0 | B-16 | finance | draft | 否 |  |  |
| PRC-93 | 生成会计分录 | event_policy |  | VS-10.0 | B-20 | finance | draft | 否 |  |  |
| PRC-94 | 制单并过账受控手工凭证 | business |  | VS-10.0 | B-20 | finance | draft | 是 |  |  |
| PRC-95 | 执行期末结转 | business |  | VS-10.0 | B-20 | finance | draft | 否 |  |  |
| PRC-96 | 分配人工成本 | business |  | VS-10.0 | B-07 | finance | draft | 否 |  |  |
| PRC-97 | 批准并生效分摊规则版本 | business |  | VS-15.1, VS-15.2, VS-15.3 | B-07 | finance | draft | 否 |  |  |
| PRC-98 | 分摊间接成本 | business |  | VS-10.0 | B-07 | finance | draft | 否 |  |  |
| PRC-99 | 计提折旧并处置公司资产 | business |  | VS-10.0 | B-24 | finance | draft | 否 |  |  |
| PRC-100 | 发布总账配置版本 | business |  | VS-15.2, VS-15.3 | B-20 | finance | draft | 否 |  |  |
| PRC-101 | 逐账户核对银行账单、处理未达并留存结论，检查并关闭会计期间 | business |  | VS-10.1 | B-17 | finance | draft | 是 |  |  |
| PRC-102 | 启用并关闭第 13 期 | business |  | VS-10.1 | B-17 | finance | draft | 否 |  |  |
| PRC-103 | 生成期间余额快照 | event_policy |  | VS-10.1 | B-20 | finance | draft | 否 |  |  |
| PRC-104 | 对账外部法定账并签字 | business |  | VS-10.2 | B-20 | finance | draft | 否 |  |  |
| PRC-105 | 评审并生效报表行定义版本 | business |  | VS-15.1, VS-15.2, VS-15.3 | B-21 | finance | draft | 否 |  |  |
| PRC-106 | 出具三大报表 | read_model |  | VS-10.2 | B-21 | finance | draft | 否 |  |  |
| PRC-107 | 知识条目的起草、评审、发布与修订废止 | business |  | VS-11.1, VS-11.2, VS-11.3 | B-30 | people | draft | 是 |  |  |
| PRC-108 | 知识条目生命周期：从复盘提炼是创建知识条目的另一入口 | merged | PRC-107 |  |  | people | draft | 待定 |  |  |
| PRC-109 | 知识条目生命周期：评审并发布 | merged | PRC-107 |  |  | people | draft | 待定 |  |  |
| PRC-110 | 知识条目生命周期：修订或废止 | merged | PRC-107 |  |  | people | draft | 待定 |  |  |
| PRC-111 | 认缴并实缴合伙人出资 | single_aggregate |  | VS-12.1 | B-10 | finance | draft | 否 |  |  |
| PRC-112 | 决议并支付利润分配 | business |  | VS-12.2 | B-10 | finance | draft | 否 |  |  |
| PRC-113 | 并入股息红利个税扣缴申报 | event_policy |  | VS-12.2 | B-15 | people | draft | 否 |  |  |
| PRC-114 | 退回合伙人出资 | business |  | VS-12.3 | B-10 | finance | draft | 否 |  |  |
| PRC-115 | 导入考勤记录并补卡 | business |  |  | B-27 | people | draft | 否 |  |  |
| PRC-116 | 申请并审批请假 | business |  | VS-13.1 | B-27 | people | draft | 是 |  |  |
| PRC-117 | 申请并批准加班 | business |  | VS-13.1 | B-27 | people | draft | 是 |  |  |
| PRC-118 | 生成、确认并锁定月度考勤 | business |  | VS-13.1 | B-27 | people | draft | 是 |  |  |
| PRC-119 | 维护工作日历与标准工时版本 | single_aggregate |  | VS-15.2 | B-14 | people | draft | 否 |  |  |
| PRC-120 | 评估月度绩效 | business |  | VS-13.2 | B-13 | people | draft | 否 |  |  |
| PRC-121 | 制定激励方案 | business |  | VS-15.1, VS-15.2, VS-15.3 | B-15 | people | draft | 是 |  |  |
| PRC-122 | 核定奖金 | business |  | VS-13.2 | B-15 | people | draft | 否 |  |  |
| PRC-123 | 调整薪酬档案与专项附加扣除 | single_aggregate |  |  | B-15 | people | draft | 否 |  |  |
| PRC-124 | 计算、审核并批准月度薪酬 | business |  | VS-13.3 | B-15 | people | draft | 是 |  |  |
| PRC-125 | 准备申报，按实付扣缴事实申报个税并缴库登记回执，办理社保公积金申报缴款 | business |  | VS-13.4, VS-06.1 | B-15 | people | draft | 否 |  |  |
| PRC-126 | 提交应付薪酬 | event_policy |  | VS-13.5 | B-15 | people | draft | 否 |  |  |
| PRC-127 | 按逐人实付结果更新薪酬行并标记整期已发放 | event_policy |  | VS-13.5 | B-15 | people | draft | 否 |  |  |
| PRC-128 | 办理员工离职，出具并交付证明，办理档案及社保转移并登记回执 | business |  | VS-13.6 | B-13 | people | draft | 否 |  |  |
| PRC-129 | 离职后吊销会话 | retired |  |  | platform.identity | people | draft | 否 |  |  |
| PRC-130 | 办理社保减员及公积金转移或封存，登记申请与经办回执 | event_policy |  | VS-13.6 | B-15 | people | draft | 否 |  |  |
| PRC-131 | 核销应付款项 | event_policy |  | VS-05.5, VS-08.3, VS-13.5 | B-10 | finance | draft | 否 |  |  |
| PRC-132 | 应付核销后置付款已核销并提交过账候选 | event_policy |  | VS-05.5, VS-08.3, VS-13.5 | B-10 | finance | draft | 否 |  |  |
| PRC-133 | 标记报销已付 | event_policy |  | VS-08.3 | B-12 | finance | draft | 否 |  |  |
| PRC-134 | 标记薪酬已过账 | event_policy |  | VS-13.5 | B-15 | people | draft | 否 |  |  |
| PRC-135 | 处理并重投会计分录 | single_aggregate |  | VS-10.0 | B-20 | finance | draft | 否 |  |  |
| PRC-136 | 变更任职 | business |  | VS-07.3 | B-13 | people | draft | 待定 |  |  |
| PRC-137 | 入职开通企业微信账号并绑定外部身份 | business |  | VS-07.3 | platform.identity | people | user_confirmed | 待定 |  |  |
| PRC-138 | 离职停用企业微信账号并解绑 | business |  | VS-13.6 | platform.identity | people | draft | 待定 |  |  |
| PRC-139 | 用印申请与用印登记 | business |  | VS-14.1, VS-14.2, VS-14.3, VS-14.4 | B-31 | people | draft | 待定 |  |  |
| PRC-140 | 借阅协议原件 | business |  |  | B-05 | revenue | draft | 待定 |  |  |
| PRC-141 | 归档协议 | business |  |  | B-05 | revenue | draft | 待定 |  |  |
| PRC-142 | IT 需求工单 | business |  | VS-14.1, VS-14.2, VS-14.3, VS-14.4 | B-31 | people | draft | 待定 |  |  |
| PRC-143 | IT 资产领用 | business |  | VS-14.1, VS-14.3 | B-31 | people | draft | 待定 |  |  |
| PRC-144 | 申请员工活动 | business |  | VS-14.1, VS-14.4 | B-31 | people | draft | 待定 |  |  |
| PRC-145 | 员工法人三口径不一致预警 | event_policy |  | VS-07.3 | B-15 | people | draft | 否 |  |  |
| PRC-146 | 催还逾期协议原件 | event_policy |  |  | B-05 | revenue | draft | 否 |  |  |
| PRC-147 | 冲抵借款并确认报销应付 | event_policy |  | VS-08.2 | B-09 | revenue | draft | 否 |  |  |
| PRC-148 | 收入确认审批通过后占用转实并提交过账 | event_policy |  | VS-04.2 | B-08 | revenue | draft | 否 |  |  |
| PRC-149 | 冲销收入确认 | business |  | VS-04.2, VS-04.5 | B-08 | revenue | draft | 是 |  |  |
| PRC-150 | 冲减应收款项 | event_policy |  | VS-04.4 | B-09 | revenue | draft | 否 |  |  |
| PRC-151 | 填报经营产值 | business |  | VS-02.2, VS-02.3, VS-02.4, VS-02.5, VS-02.6 | B-04 | revenue | draft | 否 |  |  |
| PRC-152 | 登记付款条件项达成 | business |  | VS-04.3 | B-05 | revenue | draft | 否 |  |  |
| PRC-153 | 退还投标保证金 | business |  | VS-01.2, VS-04.1 | B-09 | revenue | draft | 否 |  |  |
| PRC-154 | 查看收入三口径对照 | read_model |  | VS-04.2, VS-04.3 | reporting | revenue | draft | 否 |  |  |
| PRC-155 | IT 资产调拨 | business |  | VS-14.3 | B-31 | people | draft | 否 |  |  |
| PRC-156 | IT 资产归还 | business |  | VS-14.4 | B-31 | people | draft | 否 |  |  |
| PRC-157 | IT 资产报修与维修登记 | business |  | VS-14.3, VS-14.4 | B-31 | people | draft | 否 |  |  |
| PRC-158 | 处置过账拒收记录 | single_aggregate |  | VS-10.0 | B-16 | finance | draft | 否 |  |  |
| PRC-159 | 确认税费应付 | event_policy |  | VS-06.1 | B-10 | finance | draft | 否 |  |  |
| PRC-160 | 开立年度假期额度账户 | event_policy |  | VS-07.3 | B-27 | people | draft | 否 |  |  |
| PRC-161 | 关闭预算账户 | event_policy |  |  | B-18 | people | draft | 否 |  |  |
| PRC-162 | 发布假期规则版本 | single_aggregate |  | VS-15.1, VS-15.2, VS-15.3 | B-27 | people | draft | 否 |  |  |
| PRC-163 | 登记利润分配支付 | event_policy |  | VS-12.2 | B-10 | finance | draft | 否 |  |  |
| PRC-164 | 收入确认冲销后冲回已配对成本结转 | event_policy |  | VS-04.2 | B-07 | finance | draft | 否 |  |  |
| PRC-165 | 发布成本核算参数版本 | single_aggregate |  | VS-15.1, VS-15.2, VS-15.3 | B-07 | finance | draft | 否 |  |  |
| PRC-166 | 发布资产类别与折旧政策版本 | single_aggregate |  | VS-15.1, VS-15.2, VS-15.3 | B-24 | finance | draft | 否 |  |  |
| PRC-167 | 编制并签字确认期初余额 | retired |  |  | B-20 | finance | draft | 否 |  |  |
| PRC-168 | 发布口径版本 | single_aggregate |  | VS-15.1, VS-15.2, VS-15.3 | B-17 | finance | draft | 否 |  |  |
| PRC-169 | 处置勾稽差异案 | single_aggregate |  | VS-10.0 | B-17 | finance | draft | 否 |  |  |
| PRC-170 | 申请费用事前额度 | business |  | VS-08.0 | B-12 | finance | draft | 是 |  |  |
| PRC-171 | 核对差旅账单并确认应付 | business |  | VS-05.4 | B-12 | finance | draft | 是 |  |  |
| PRC-172 | 申请出差并同步差旅服务商 | business |  | VS-08.0 | B-12 | finance | draft | 是 |  |  |
| PRC-173 | 审批差旅超标订单 | business |  | VS-08.0 | B-12 | finance | draft | 是 |  |  |
| PRC-174 | 发布出差补贴标准版本 | single_aggregate |  | VS-15.1, VS-15.2, VS-15.3 | B-12 | finance | draft | 否 |  |  |
| PRC-175 | 申请直接付款 | business |  | VS-05.5 | B-10 | finance | draft | 是 |  |  |
| PRC-176 | 核销预付款 | single_aggregate |  | VS-05.5 | B-10 | finance | draft | 否 |  |  |
| PRC-177 | 向差旅服务商同步员工与组织 | merged | PRC-197 |  | B-13 | people | draft | 否 |  |  |
| PRC-178 | 转计发加班工资 | event_policy |  | VS-13.6 | B-15 | people | draft | 否 |  |  |
| PRC-179 | 项目开始执行后协议转入履行中 | event_policy |  | VS-04.6 | B-05 | revenue | draft | 否 |  |  |
| PRC-180 | 发布薪酬规则版本 | single_aggregate |  | VS-15.1, VS-15.2, VS-15.3 | B-15 | people | draft | 否 |  |  |
| PRC-181 | 记录经营复盘并跟踪动作 | business |  | VS-09.3 | B-18 | people | draft | 是 |  |  |
| PRC-182 | 查看知识条目使用情况 | read_model |  | VS-11.3 | reporting | people | draft | 否 |  |  |
| PRC-183 | 作出商机参投决策 | business |  | VS-01.2 | B-03 | revenue | draft | 是 |  |  |
| PRC-184 | 同步组织来源数据 | integration |  |  | platform.integration | platform | draft | 否 |  |  |
| PRC-185 | 核对组织快照与来源 | integration |  |  | platform.integration | platform | draft | 否 |  |  |
| PRC-186 | 刷新部门简称 | integration |  |  | platform.integration | platform | draft | 否 |  |  |
| PRC-187 | 校验致远单点登录票据 | integration |  |  | platform.integration | platform | draft | 否 |  |  |
| PRC-188 | 识别企业微信登录身份 | integration |  |  | platform.integration | platform | draft | 否 |  |  |
| PRC-189 | 投递企业微信通知 | integration |  |  | platform.integration | platform | draft | 否 |  |  |
| PRC-190 | 重投领域事件 | retired |  |  | platform.integration | platform | draft | 否 |  |  |
| PRC-191 | 归档数据到对象存储 | retired |  |  | platform.integration | platform | draft | 否 |  |  |
| PRC-192 | 读取老系统待办 | integration |  |  | platform.integration | platform | draft | 否 |  |  |
| PRC-193 | 编排历史数据迁入 | integration |  |  | platform.migration（暂定名，ADR-0036 提议；main 中未见该模块） | platform | draft | 否 |  |  |
| PRC-194 | 推送凭证到U8C | integration |  |  | platform.integration（接口）与 accounting.ledger（待处理队列、死信、重投） | platform | draft | 否 |  |  |
| PRC-195 | 同步U8C主数据 | integration |  |  | platform.integration | platform | draft | 否 |  |  |
| PRC-196 | 对接数电发票平台 | integration |  |  | platform.integration | platform | draft | 否 |  |  |
| PRC-197 | 同步差旅员工与组织 | integration |  |  | platform.integration | platform | draft | 否 |  |  |
| PRC-198 | 接收差旅订单与账单 | integration |  |  | platform.integration | platform | draft | 否 |  |  |
| PRC-199 | 推送出差申请并回写审批 | integration |  |  | platform.integration | platform | draft | 否 |  |  |
| PRC-200 | 拉取企业微信打卡记录 | integration |  |  | platform.integration（接口）与 B-27 organization.attendance（落考勤记录） | platform | draft | 否 |  |  |
| PRC-201 | 登记代发结果并补发薪酬 | business |  | VS-13.5 | B-15 | people | draft | 是 |  |  |
| PRC-202 | 维护应收默认账期规则 | single_aggregate |  | VS-15.1, VS-15.2, VS-15.3 | B-09 | revenue | draft | 否 |  |  |
| PRC-203 | 按月计提奖金 | business |  | VS-13.2 | B-15 | people | draft | 否 |  |  |
