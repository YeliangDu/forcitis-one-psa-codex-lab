<!-- 由 build_catalog.py 生成，不手改 -->
# 流程目录（生成，提议稿）

由 build_catalog.py 生成，不手改；来源 `process-catalog.yaml`。

共 157 条，编号 PRC-01 至 PRC-157。

### 按类型

| 类型 | 数量 |
|---|---|
| business | 98 |
| event_policy | 25 |
| single_aggregate | 19 |
| read_model | 5 |
| merged | 10 |
| 合计 | 157 |

### 按组（全部类型）

| 组 | 数量 |
|---|---|
| revenue | 77 |
| finance | 38 |
| people | 42 |
| 合计 | 157 |

### 按组（仅业务流程）

| 组 | 数量 |
|---|---|
| revenue | 45 |
| finance | 24 |
| people | 29 |
| 合计 | 98 |

### 按价值流（仅业务流程；跨价值流的流程各计一次，故合计大于业务流程数）

| 价值流 | 数量 |
|---|---|
| VS-01 | 7 |
| VS-02 | 19 |
| VS-03 | 7 |
| VS-04 | 16 |
| VS-05 | 9 |
| VS-06 | 3 |
| VS-07 | 6 |
| VS-08 | 4 |
| VS-09 | 2 |
| VS-10 | 12 |
| VS-11 | 1 |
| VS-12 | 3 |
| VS-13 | 12 |
| 合计 | 101 |

业务流程中尚未填 stages：12 条。

## 总表

| 编号 | 名称 | 类型 | 并入 | 阶段 | owner_bc | 组 | 状态 | 人工编排 | bpmn |
|---|---|---|---|---|---|---|---|---|---|
| PRC-01 | 举办市场活动 | single_aggregate |  | VS-01.1 | B-03 | revenue | draft | 否 |  |
| PRC-02 | 计划并完成客户活动 | single_aggregate |  | VS-01.1 | B-03 | revenue | draft | 否 |  |
| PRC-03 | 登记并跟进线索，转为商机 | business |  | VS-01.1, VS-01.2 | B-03 | revenue | draft | 否 |  |
| PRC-04 | 线索到商机：线索已确认后的下一步就是新建商机 | merged | PRC-03 |  |  | revenue | draft | 待定 |  |
| PRC-05 | 维护往来单位档案 | single_aggregate |  | VS-01.1, VS-05.1 | B-01 | revenue | draft | 否 |  |
| PRC-06 | 做独立性检查 | single_aggregate |  | VS-01.2 | B-03 | revenue | draft | 否 |  |
| PRC-07 | 编制并生效报价 | business |  | VS-01.2 | B-03 | revenue | draft | 是 |  |
| PRC-08 | 参加投标，缴纳保证金并登记结果 | business |  | VS-01.2 | B-03 | revenue | draft | 是 |  |
| PRC-09 | 投标、结果、保证金：中标赢单与未中标丢单是投标结果的事件策略，委托赢单与放弃是同一商机上的人工分支 | merged | PRC-08 |  |  | revenue | draft | 待定 |  |
| PRC-10 | 收付保证金与保函 | single_aggregate |  | VS-01.2, VS-04.1 | B-09 | revenue | draft | 否 |  |
| PRC-11 | 投标、结果、保证金：未中标后退还保证金是下游 BC 订阅投标未中标的反应 | merged | PRC-08 |  |  | revenue | draft | 待定 |  |
| PRC-12 | 发布服务产品与阶段类型版本 | single_aggregate |  | VS-01.2 | B-02 | revenue | draft | 否 |  |
| PRC-13 | 批准并生效费率、计费系数与协议价目版本 | business |  | VS-01.2, VS-02.0 | B-02 | revenue | draft | 否 |  |
| PRC-14 | 登记建设工程与招标项目 | single_aggregate |  | VS-01.2, VS-02.6, VS-03.1 | B-01 | revenue | draft | 否 |  |
| PRC-15 | 未签约的售前投入转费用 | event_policy |  | VS-01.2 | B-07 | finance | draft | 否 |  |
| PRC-16 | 申请并转正风险交付授权 | business |  | VS-01.2, VS-02.0, VS-04.1 | B-05 | revenue | draft | 是 |  |
| PRC-17 | 建立项目并组建团队 | business |  | VS-02.0, VS-03.1 | B-04 | revenue | draft | 否 |  |
| PRC-18 | 编制并批准交付计划 | business |  | VS-02.0 | B-04 | revenue | draft | 否 |  |
| PRC-19 | 编制并锁定项目预算 | business |  | VS-02.0 | B-04 | revenue | draft | 是 |  |
| PRC-20 | 提出并满足资源需求 | business |  | VS-02.0, VS-02.2, VS-02.3, VS-02.4, VS-02.5, VS-02.6 | B-04 | revenue | draft | 否 |  |
| PRC-21 | 推进阶段与工作包 | single_aggregate |  | VS-02.0, VS-02.1, VS-02.2, VS-02.3, VS-02.4, VS-02.5, VS-02.6 | B-04 | revenue | draft | 否 |  |
| PRC-22 | 阶段门预警 | read_model |  | VS-02.0, VS-02.1, VS-02.2, VS-02.3, VS-02.4, VS-02.5, VS-02.6, VS-02.7 | reporting | revenue | draft | 否 |  |
| PRC-23 | 填报并审批工时 | business |  | VS-01.2, VS-02.0, VS-02.1, VS-02.2, VS-02.3, VS-02.4, VS-02.5, VS-02.6 | B-06 | revenue | draft | 是 |  |
| PRC-24 | 暂停、恢复、终止或取消项目 | business |  | VS-02.0, VS-02.1, VS-02.2, VS-02.3, VS-02.4, VS-02.5, VS-02.6, VS-02.7 | B-04 | revenue | draft | 否 |  |
| PRC-25 | 转入售前投入 | event_policy |  | VS-02.0, VS-04.1 | B-07 | finance | draft | 否 |  |
| PRC-26 | 接收并确认发包人提供资料 | business |  | VS-02.0, VS-02.2, VS-02.3, VS-02.4 | B-28 | revenue | draft | 是 |  |
| PRC-27 | 登记并处置项目风险与问题 | single_aggregate |  | VS-02.0, VS-02.1, VS-02.2, VS-02.3, VS-02.4, VS-02.5, VS-02.6, VS-02.7 | B-04 | revenue | draft | 否 |  |
| PRC-28 | 编制、评审并签发交付物，送达并登记客户认可 | business |  | VS-02.1, VS-02.2, VS-02.3, VS-02.4, VS-02.5, VS-02.6, VS-03.5 | B-28 | revenue | user_confirmed | 是 |  |
| PRC-29 | 签发到认可：交付物签发后送达与登记客户认可是同一交付物的连续步骤 | merged | PRC-28 |  |  | revenue | draft | 待定 |  |
| PRC-30 | 提出并批准完工估算 | business |  | VS-02.2, VS-02.3, VS-02.4, VS-02.5, VS-02.6 | B-04 | revenue | draft | 否 |  |
| PRC-31 | 提出并发出设计变更 | business |  | VS-02.2, VS-02.3, VS-02.4, VS-02.5, VS-02.6 | B-28 | revenue | draft | 否 |  |
| PRC-32 | 申请并答复延期 | business |  | VS-02.2, VS-02.3, VS-02.4, VS-02.5, VS-02.6 | B-04 | revenue | draft | 是 |  |
| PRC-33 | 采集、关联并封存证据 | single_aggregate |  | VS-02.4, VS-02.7, VS-03.2, VS-04.2 | B-28 | revenue | draft | 否 |  |
| PRC-34 | 处置我方成果不符合项 | business |  | VS-02.6, VS-02.7 | B-28 | revenue | draft | 否 |  |
| PRC-35 | 项目或协议终止、结项后清算成本 | event_policy |  | VS-02.7, VS-04.5 | B-07 | finance | draft | 否 |  |
| PRC-36 | 封存成果、结项并归档项目 | business |  | VS-02.7 | B-29 | revenue | draft | 是 |  |
| PRC-37 | 回访客户满意度 | business |  | VS-02.7 | B-28 | revenue | draft | 否 |  |
| PRC-38 | 编制并发布项目复盘 | business |  | VS-02.7 | B-04 | revenue | draft | 否 |  |
| PRC-39 | 结项含封存与归档：封存成果、结项检查与审批、结项 | merged | PRC-36 |  |  | revenue | draft | 待定 |  |
| PRC-40 | 结项含封存与归档：项目已结项后归档 | merged | PRC-36 |  |  | revenue | draft | 待定 |  |
| PRC-41 | 制定检查计划并使其生效 | business |  | VS-03.1 | B-26 | revenue | draft | 否 |  |
| PRC-42 | 发布检查表模板版本 | single_aggregate |  | VS-03.1 | B-26 | revenue | draft | 否 |  |
| PRC-43 | 实施现场检查并锁定 | business |  | VS-03.1, VS-03.2, VS-03.5, VS-02.6 | B-26 | revenue | draft | 否 |  |
| PRC-44 | 发布客户资产类不符合项并建议外部检测 | business |  | VS-03.2, VS-03.3 | B-26 | revenue | draft | 否 |  |
| PRC-45 | 跟踪整改并复查闭合 | business |  | VS-03.4 | B-26 | revenue | draft | 是 |  |
| PRC-46 | 采信外部检测报告 | business |  | VS-03.4 | B-26 | revenue | draft | 否 |  |
| PRC-47 | 起草、评审并回签协议 | business |  | VS-04.1 | B-05 | revenue | draft | 是 |  |
| PRC-48 | 提出并生效协议变更或签证 | business |  | VS-04.1 | B-05 | revenue | draft | 是 |  |
| PRC-49 | 提出并认定索赔 | business |  | VS-04.1, VS-04.5 | B-05 | revenue | draft | 是 |  |
| PRC-50 | 占用框架限额并提交任务订单 | business |  | VS-04.1 | B-05 | revenue | draft | 是 |  |
| PRC-51 | 建立并更新开票与收入额度 | event_policy |  | VS-04.1, VS-04.3 | B-08 | revenue | draft | 否 |  |
| PRC-52 | 开放可触发收入确认的证据类型 | single_aggregate |  | VS-04.2 | B-08 | revenue | draft | 否 |  |
| PRC-53 | 确认收入 | event_policy |  | VS-04.2 | B-08 | revenue | draft | 否 |  |
| PRC-54 | 视为同意期满处理 | business |  | VS-04.2 | B-08 | revenue | draft | 是 |  |
| PRC-55 | 结转合同履约成本 | event_policy |  | VS-04.2 | B-07 | finance | draft | 否 |  |
| PRC-56 | 付款条件项转为可结算 | event_policy |  | VS-04.3 | B-05 | revenue | draft | 否 |  |
| PRC-57 | 申请并开具销项发票 | business |  | VS-04.3 | B-08 | revenue | draft | 是 |  |
| PRC-58 | 红冲销项发票 | business |  | VS-04.3 | B-08 | revenue | draft | 否 |  |
| PRC-59 | 由销项发票创建应收 | event_policy |  | VS-04.4 | B-09 | revenue | draft | 否 |  |
| PRC-60 | 认领收款并核销应收 | business |  | VS-04.4 | B-09 | revenue | draft | 否 |  |
| PRC-61 | 检测应收逾期并催收 | business |  | VS-04.4 | B-09 | revenue | draft | 否 |  |
| PRC-62 | 质量保证金到期提示 | read_model |  | VS-04.4 | B-09 | revenue | draft | 否 |  |
| PRC-63 | 商业汇票兑付与逾期 | business |  | VS-04.4 | B-09 | revenue | draft | 否 |  |
| PRC-64 | 导入银行流水 | business |  | VS-04.4, VS-05.5 | B-22 | finance | draft | 否 |  |
| PRC-65 | 维护资金账户 | single_aggregate |  | VS-04.4, VS-05.5 | B-22 | finance | draft | 否 |  |
| PRC-66 | 发起结算，确认最终价款并结算协议 | business |  | VS-04.5 | B-08 | revenue | draft | 否 |  |
| PRC-67 | 履约完成到结算：协议已完成后编制结算书、确认最终价款、结算 | merged | PRC-66 |  |  | revenue | draft | 待定 |  |
| PRC-68 | 终止协议 | single_aggregate |  | VS-04.5 | B-05 | revenue | draft | 否 |  |
| PRC-69 | 供应商准入与资质核验 | business |  | VS-05.1 | B-01 | revenue | draft | 否 |  |
| PRC-70 | 起草并生效供方协议 | business |  | VS-05.2 | B-11 | finance | draft | 是 |  |
| PRC-71 | 生效分包变更 | business |  | VS-05.2 | B-11 | finance | draft | 否 |  |
| PRC-72 | 确认供方履约 | business |  | VS-05.3 | B-11 | finance | draft | 否 |  |
| PRC-73 | 登记并查验进项发票 | business |  | VS-05.4, VS-08.2 | B-23 | finance | draft | 否 |  |
| PRC-74 | 提交供方应付 | business |  | VS-05.4 | B-11 | finance | draft | 否 |  |
| PRC-75 | 发起、审批并执行付款 | business |  | VS-05.5, VS-08.1, VS-08.3, VS-12.2, VS-12.3, VS-13.5 | B-10 | finance | draft | 是 |  |
| PRC-76 | 完成并结算供方协议 | business |  | VS-05.6 | B-11 | finance | draft | 否 |  |
| PRC-77 | 申报并缴纳增值税、附加税与企业所得税 | business |  | VS-06.1 | B-23 | finance | draft | 否 |  |
| PRC-78 | 年检、核查与延续企业资质 | business |  | VS-06.2 | B-01 | revenue | draft | 是 |  |
| PRC-79 | 登记继续教育并延续注册执业证书 | single_aggregate |  | VS-06.2 | B-13 | people | draft | 否 |  |
| PRC-80 | 维护法人、组织与岗位 | business |  | VS-07.1, VS-07.3 | B-13 | people | draft | 否 |  |
| PRC-81 | 提出并批准招聘需求 | business |  | VS-07.1 | B-13 | people | draft | 是 |  |
| PRC-82 | 登记候选人并作出录用决定 | business |  | VS-07.2 | B-13 | people | draft | 否 |  |
| PRC-83 | 办理入职与任职 | business |  | VS-07.3 | B-13 | people | user_confirmed | 否 |  |
| PRC-84 | 入职后参保增员与建立薪酬档案 | business |  | VS-07.3 | B-15 | people | user_confirmed | 否 |  |
| PRC-85 | 试用期评估与转正 | business |  | VS-07.3, VS-13.2 | B-13 | people | draft | 否 |  |
| PRC-86 | 发放、归还与催还员工借款 | business |  | VS-08.1 | B-09 | revenue | draft | 否 |  |
| PRC-87 | 提交、核定并确认报销应付 | business |  | VS-08.2 | B-12 | finance | user_confirmed | 是 |  |
| PRC-88 | 编制、批准并基线化经营计划与预算 | business |  | VS-09.1, VS-09.3 | B-18 | people | draft | 是 |  |
| PRC-89 | 编制滚动预测 | business |  | VS-09.2 | B-18 | people | draft | 否 |  |
| PRC-90 | 出具经营月报并复盘 | read_model |  | VS-09.3, VS-10.2 | reporting | finance | draft | 否 |  |
| PRC-91 | 认定工时有效性 | event_policy |  | VS-10.1 | B-06 | revenue | draft | 否 |  |
| PRC-92 | 过账业务事实 | business |  | VS-10.1 | B-16 | finance | draft | 否 |  |
| PRC-93 | 生成会计分录 | event_policy |  | VS-10.1 | B-20 | finance | draft | 否 |  |
| PRC-94 | 制单并过账受控手工凭证 | business |  | VS-10.1 | B-20 | finance | draft | 是 |  |
| PRC-95 | 期末结转 | business |  | VS-10.1 | B-20 | finance | draft | 否 |  |
| PRC-96 | 分配人工成本 | business |  | VS-10.1 | B-07 | finance | draft | 否 |  |
| PRC-97 | 批准并生效分摊规则版本 | business |  | VS-10.1 | B-07 | finance | draft | 否 |  |
| PRC-98 | 分摊间接成本 | business |  | VS-10.1 | B-07 | finance | draft | 否 |  |
| PRC-99 | 计提折旧并处置公司资产 | business |  | VS-10.1 | B-24 | finance | draft | 否 |  |
| PRC-100 | 发布总账配置版本 | business |  | VS-10.1 | B-20 | finance | draft | 否 |  |
| PRC-101 | 月结检查并关账 | business |  | VS-10.1 | B-17 | finance | draft | 是 |  |
| PRC-102 | 启用第 13 期并年末关账 | business |  | VS-10.1 | B-17 | finance | draft | 否 |  |
| PRC-103 | 生成期间余额快照 | event_policy |  | VS-10.2 | B-20 | finance | draft | 否 |  |
| PRC-104 | 外部法定账对账与签字 | business |  | VS-10.2 | B-20 | finance | draft | 否 |  |
| PRC-105 | 评审并生效报表行定义版本 | business |  | VS-10.2 | B-21 | finance | draft | 否 |  |
| PRC-106 | 出具三大报表 | read_model |  | VS-10.2 | B-21 | finance | draft | 否 |  |
| PRC-107 | 知识条目的起草、评审、发布与修订废止 | business |  | VS-11.1, VS-11.2, VS-11.3 | B-30 | people | draft | 是 |  |
| PRC-108 | 知识条目生命周期：从复盘提炼是创建知识条目的另一入口 | merged | PRC-107 |  |  | people | draft | 待定 |  |
| PRC-109 | 知识条目生命周期：评审并发布 | merged | PRC-107 |  |  | people | draft | 待定 |  |
| PRC-110 | 知识条目生命周期：修订或废止 | merged | PRC-107 |  |  | people | draft | 待定 |  |
| PRC-111 | 认缴并实缴出资 | single_aggregate |  | VS-12.1 | B-10 | finance | draft | 否 |  |
| PRC-112 | 决议并支付利润分配 | business |  | VS-12.2 | B-10 | finance | draft | 否 |  |
| PRC-113 | 并入股息红利个税扣缴申报 | event_policy |  | VS-12.2, VS-13.4 | B-15 | people | draft | 否 |  |
| PRC-114 | 退回出资 | business |  | VS-12.3 | B-10 | finance | draft | 否 |  |
| PRC-115 | 导入考勤记录并补卡 | business |  | VS-13.1 | B-27 | people | draft | 否 |  |
| PRC-116 | 申请并审批请假 | business |  | VS-13.1 | B-27 | people | draft | 是 |  |
| PRC-117 | 申请并批准加班 | business |  | VS-13.1 | B-27 | people | draft | 是 |  |
| PRC-118 | 生成、确认并锁定月度考勤 | business |  | VS-13.1 | B-27 | people | draft | 是 |  |
| PRC-119 | 维护工作日历与标准工时版本 | single_aggregate |  | VS-13.1 | B-14 | people | draft | 否 |  |
| PRC-120 | 评估月度绩效 | business |  | VS-13.2 | B-13 | people | draft | 否 |  |
| PRC-121 | 制定激励方案 | business |  | VS-13.2 | B-15 | people | draft | 是 |  |
| PRC-122 | 核定奖金 | business |  | VS-13.2 | B-15 | people | draft | 否 |  |
| PRC-123 | 调整薪酬档案与专项附加扣除 | single_aggregate |  | VS-13.3, VS-13.4 | B-15 | people | draft | 否 |  |
| PRC-124 | 计算、审核并批准月度薪酬 | business |  | VS-13.3 | B-15 | people | draft | 是 |  |
| PRC-125 | 申报个税扣缴与社保公积金 | business |  | VS-13.4, VS-06.1 | B-15 | people | draft | 否 |  |
| PRC-126 | 提交应付薪酬 | event_policy |  | VS-13.5 | B-15 | people | draft | 否 |  |
| PRC-127 | 薪酬发放完成 | event_policy |  | VS-13.5 | B-15 | people | draft | 否 |  |
| PRC-128 | 办理离职 | business |  | VS-13.6 | B-13 | people | draft | 否 |  |
| PRC-129 | 离职后吊销会话 | event_policy |  | VS-13.6 | platform.identity | people | draft | 否 |  |
| PRC-130 | 离职后参保减员 | event_policy |  | VS-13.6 | B-15 | people | draft | 否 |  |
| PRC-131 | 付款成功后核销应付 | event_policy |  | VS-05.5, VS-08.1, VS-08.3, VS-12.2, VS-12.3, VS-13.5 | B-10 | finance | draft | 否 |  |
| PRC-132 | 应付核销后置付款已核销并提交过账候选 | event_policy |  | VS-05.5, VS-08.1, VS-08.3, VS-12.2, VS-12.3, VS-13.5 | B-10 | finance | draft | 否 |  |
| PRC-133 | 应付结清后报销置已付 | event_policy |  | VS-08.3 | B-12 | finance | draft | 否 |  |
| PRC-134 | 薪酬事实已过账置位 | event_policy |  | VS-13.3 | B-15 | people | draft | 否 |  |
| PRC-135 | 处理会计分录待处理队列与推送重投 | single_aggregate |  | VS-10.1 | B-20 | finance | draft | 否 |  |
| PRC-136 | 调岗（任职变更，按人事业务生效日，触发数据范围复核） | business |  |  | B-13 | people | draft | 待定 |  |
| PRC-137 | 入职开通企业微信账号并绑定外部身份 | business |  |  | platform.identity | people | user_confirmed | 待定 |  |
| PRC-138 | 离职停用企业微信账号并解绑 | business |  |  | platform.identity | people | draft | 待定 |  |
| PRC-139 | 用印申请与用印登记 | business |  |  | B-31 | people | draft | 待定 |  |
| PRC-140 | 合同借阅 | business |  |  | B-05 | revenue | draft | 待定 |  |
| PRC-141 | 销售合同归档 | business |  |  | B-05 | revenue | draft | 待定 |  |
| PRC-142 | IT 需求工单 | business |  |  | B-31 | people | draft | 待定 |  |
| PRC-143 | IT 资产领用 | business |  |  | B-31 | people | draft | 待定 |  |
| PRC-144 | 员工活动类申请（周年贺卡、节日活动、创新项目发布） | business |  |  | B-31 | people | draft | 待定 |  |
| PRC-145 | 员工法人三口径不一致预警 | event_policy |  | VS-07.3 | B-15 | people | draft | 否 |  |
| PRC-146 | 协议借阅逾期催还 | event_policy |  |  | B-05 | revenue | draft | 否 |  |
| PRC-147 | 费用报销核定后冲抵员工借款并确认应付 | event_policy |  | VS-08.2 | B-09 | revenue | draft | 否 |  |
| PRC-148 | 收入确认审批通过后占用转实并提交过账 | event_policy |  | VS-04.2 | B-08 | revenue | draft | 否 |  |
| PRC-149 | 冲销收入确认 | business |  | VS-04.2, VS-04.5 | B-08 | revenue | draft | 是 |  |
| PRC-150 | 发票红冲后冲减应收 | event_policy |  | VS-04.4 | B-09 | revenue | draft | 否 |  |
| PRC-151 | 填报经营产值 | business |  | VS-02.2, VS-02.3, VS-02.4, VS-02.5, VS-02.6 | B-04 | revenue | draft | 否 |  |
| PRC-152 | 登记付款条件达成 | business |  | VS-04.3, VS-04.4 | B-05 | revenue | draft | 否 |  |
| PRC-153 | 退还投标保证金 | business |  | VS-01.2, VS-04.1 | B-09 | revenue | draft | 否 |  |
| PRC-154 | 收入三口径对照 | read_model |  | VS-04.2, VS-04.3 | reporting | revenue | draft | 否 |  |
| PRC-155 | IT 资产调拨 | business |  |  | B-31 | people | draft | 否 |  |
| PRC-156 | IT 资产归还 | business |  |  | B-31 | people | draft | 否 |  |
| PRC-157 | IT 资产报修与维修登记 | business |  |  | B-31 | people | draft | 否 |  |
