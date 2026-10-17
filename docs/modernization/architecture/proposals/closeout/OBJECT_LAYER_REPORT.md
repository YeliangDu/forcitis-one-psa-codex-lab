# 对象层与能力层对齐报告（object_layer_check.py 生成，勿手改）

非支撑流程 143 条：已确认 114、声明无业务对象 23、提议 0、多候选 0、无候选 0、读模型 6。
检查：① 无主对象 0；② 阶段承载对象无主流程 0；③ 主对象多流程 23；④ 能力不在所标段清单 11（另主对象无能力 0）；主对象不是业务对象 0。

提议口径：第 1 档 所标段退出条件精确命中；第 2 档 所标段段内状态精确命中；第 3 档 退出或段内近似命中；第 4 档 所标段退出条件引用；第 5 档 唯一写入对象。只取本流程写入的对象；第 4、5 档把握低，组确认时重点看。

## 主对象（按组）

### finance

| 流程 | 类型 | 所标段 | 主对象 | 状态 | 依据档 | 候选 / 写入对象 | 一级能力 |
|---|---|---|---|---|---|---|---|
| PRC-15 未签约的售前投入转费用 | event_policy | VS-01.2 | — | declared_none | — | AGG-43、AGG-122 | — |
| PRC-25 转入售前投入 | event_policy | VS-02.0、VS-04.1 | — | declared_none | — | AGG-43、AGG-122 | — |
| PRC-35 项目或协议终止、结项后清算成本 | event_policy | VS-02.7 | — | declared_none | 5 | AGG-43 | — |
| PRC-55 结转合同履约成本 | event_policy | VS-04.2 | — | declared_none | 5 | AGG-43 | — |
| PRC-70 起草并生效供方协议 | business | VS-05.2 | OBJ-05 协议 | confirmed | 3 | AGG-58、OBJ-05 协议、OBJ-06 合同范围条目、OBJ-07 付款条件项、OBJ-100 经营计划 | CAP-005 |
| PRC-71 生效分包变更 | business | VS-05.2 | OBJ-09 协议变更 | confirmed | — | AGG-58、OBJ-09 协议变更、OBJ-100 经营计划 | CAP-005 |
| PRC-72 确认供方履约 | business | VS-05.3 | OBJ-87 供方履约确认 | confirmed | 1 | AGG-58、AGG-77、OBJ-87 供方履约确认 | CAP-005 |
| PRC-73 登记并查验进项发票 | business | VS-05.4 | OBJ-83 增值税发票 | confirmed | 1 | OBJ-83 增值税发票 | CAP-103 |
| PRC-74 提交供方应付 | business | VS-05.4 | OBJ-85 往来款项 | confirmed | 1 | OBJ-83 增值税发票、OBJ-85 往来款项 | CAP-103 |
| PRC-75 发起、审批并执行付款 | business | VS-05.5、VS-08.3、VS-13.5 | OBJ-86 资金收付 | confirmed | 1 | AGG-77、OBJ-85 往来款项、OBJ-86 资金收付 | CAP-103 |
| PRC-76 完成并结算供方协议 | business | VS-05.6 | OBJ-05 协议 | confirmed | 1 | OBJ-05 协议、OBJ-06 合同范围条目、OBJ-07 付款条件项 | CAP-005 |
| PRC-77 申报并缴纳增值税、附加税与企业所得税 | business | VS-06.1 | OBJ-92 法定申报 | confirmed | 1 | OBJ-92 法定申报 | CAP-103 |
| PRC-87 提交、核定并确认报销应付 | business | VS-08.2 | OBJ-88 费用报销 | confirmed | 1 | AGG-77、OBJ-83 增值税发票、OBJ-88 费用报销、OBJ-100 经营计划 | CAP-056 |
| PRC-90 出具经营月报 | read_model | VS-09.3、VS-10.2 | — | read_model | — |  | — |
| PRC-92 过账业务事实 | business | VS-10.0 | — | declared_none | 5 | AGG-77 | — |
| PRC-93 生成会计分录 | event_policy | VS-10.0 | OBJ-98 会计分录 | confirmed | 4 | OBJ-98 会计分录 | CAP-103 |
| PRC-94 制单并过账受控手工凭证 | business | VS-10.0 | OBJ-98 会计分录 | confirmed | 4 | OBJ-98 会计分录 | CAP-103 |
| PRC-95 期末结转 | business | VS-10.0 | — | declared_none | — | AGG-77、AGG-99 | — |
| PRC-96 分配人工成本 | business | VS-10.0 | — | declared_none | — | AGG-41、AGG-77 | — |
| PRC-98 分摊间接成本 | business | VS-10.0 | — | declared_none | — | AGG-42、AGG-77 | — |
| PRC-99 计提折旧并处置公司资产 | business | VS-10.0 | OBJ-93 公司资产 | confirmed | 5 | OBJ-93 公司资产 | CAP-093 |
| PRC-101 月结检查并关账 | business | VS-10.1 | OBJ-97 会计期间 | confirmed | 1 | OBJ-97 会计期间 | CAP-103 |
| PRC-102 启用第 13 期并年末关账 | business | VS-10.1 | OBJ-97 会计期间 | confirmed | 1 | AGG-96、OBJ-97 会计期间 | CAP-103 |
| PRC-103 生成期间余额快照 | event_policy | VS-10.1 | — | declared_none | 5 | AGG-100 | — |
| PRC-104 外部法定账对账与签字 | business | VS-10.2 | — | declared_none | — | AGG-101、OBJ-98 会计分录 | — |
| PRC-106 出具三大报表 | read_model | VS-10.2 | — | read_model | — |  | — |
| PRC-111 认缴并实缴出资 | single_aggregate | VS-12.1 | OBJ-91 合伙人出资 | confirmed | 1 | OBJ-91 合伙人出资 | CAP-054 |
| PRC-112 决议并支付利润分配 | business | VS-12.2 | OBJ-112 利润分配 | confirmed | 1 | AGG-77、OBJ-85 往来款项、OBJ-112 利润分配 | CAP-054 |
| PRC-114 退回出资 | business | VS-12.3 | OBJ-91 合伙人出资 | confirmed | 1 | OBJ-85 往来款项、OBJ-91 合伙人出资 | CAP-054 |
| PRC-131 付款成功后核销应付 | event_policy | VS-05.5、VS-08.3、VS-13.5 | OBJ-85 往来款项 | confirmed | 4 | OBJ-85 往来款项 | CAP-103 |
| PRC-132 应付核销后置付款已核销并提交过账候选 | event_policy | VS-05.5、VS-08.3、VS-13.5 | OBJ-86 资金收付 | confirmed | 4 | OBJ-86 资金收付 | CAP-103 |
| PRC-133 应付结清后报销置已付 | event_policy | VS-08.3 | OBJ-88 费用报销 | confirmed | 4 | OBJ-88 费用报销 | CAP-056 |
| PRC-135 处理会计分录待处理队列与推送重投 | single_aggregate | VS-10.0 | OBJ-98 会计分录 | confirmed | 4 | OBJ-98 会计分录 | CAP-103 |
| PRC-158 处置过账拒收记录 | single_aggregate | VS-10.0 | — | declared_none | 5 | AGG-77 | — |
| PRC-159 申报已提交后确认税费应付 | event_policy | VS-06.1 | OBJ-85 往来款项 | confirmed | 5 | OBJ-85 往来款项 | CAP-103 |
| PRC-163 应付结清后登记利润分配支付 | event_policy | VS-12.2 | OBJ-112 利润分配 | confirmed | 4 | OBJ-112 利润分配 | CAP-054 |
| PRC-164 收入确认冲销后冲回已配对成本结转 | event_policy | VS-04.2 | — | declared_none | 5 | AGG-43 | — |
| PRC-169 处置勾稽差异案 | single_aggregate | VS-10.0 | — | declared_none | 5 | AGG-126 | — |
| PRC-170 申请费用事前额度 | business | VS-08.0 | — | declared_none | 5 | AGG-130 | — |
| PRC-171 核对差旅服务商月账单并确认应付 | business | VS-05.4 | OBJ-85 往来款项 | confirmed | 4 | AGG-131、OBJ-85 往来款项 | CAP-103 |
| PRC-172 申请出差并同步差旅服务商 | business | VS-08.0 | — | declared_none | 5 | AGG-132 | — |
| PRC-173 审批差旅超标订单 | business | VS-08.0 | — | declared_none | 5 | AGG-131 | — |
| PRC-175 直接付款申请 | business | VS-05.5 | OBJ-85 往来款项 | confirmed | 4 | OBJ-85 往来款项 | CAP-103 |
| PRC-176 预付款核销 | single_aggregate | VS-05.5 | OBJ-85 往来款项 | confirmed | 1 | OBJ-85 往来款项 | CAP-103 |

### people

| 流程 | 类型 | 所标段 | 主对象 | 状态 | 依据档 | 候选 / 写入对象 | 一级能力 |
|---|---|---|---|---|---|---|---|
| PRC-79 登记继续教育并延续注册执业证书 | single_aggregate | VS-06.2 | OBJ-64 注册执业证书 | confirmed | 1 | OBJ-64 注册执业证书 | CAP-056 |
| PRC-81 提出并批准招聘需求 | business | VS-07.1 | OBJ-60 招聘需求 | confirmed | 1 | OBJ-60 招聘需求 | CAP-058 |
| PRC-82 登记候选人并作出录用决定 | business | VS-07.2 | OBJ-56 员工 | confirmed | 3 | OBJ-56 员工 | CAP-056 |
| PRC-83 办理入职与任职 | business | VS-07.3 | OBJ-56 员工 | confirmed | 4 | OBJ-05 协议、OBJ-06 合同范围条目、OBJ-07 付款条件项、OBJ-56 员工、OBJ-57 任职 | CAP-056 |
| PRC-84 入职后参保增员与建立薪酬档案 | business | VS-07.3 | OBJ-62 社保与公积金参保 | confirmed | 4 | AGG-93、OBJ-62 社保与公积金参保 | CAP-056 |
| PRC-85 试用期评估与转正 | business | VS-07.3、VS-13.2 | OBJ-56 员工 | confirmed | 1 | OBJ-56 员工、OBJ-109 绩效评估 | CAP-056 |
| PRC-88 编制、批准并基线化经营计划与预算 | business | VS-09.1、VS-09.3 | OBJ-100 经营计划 | confirmed | 1 | OBJ-100 经营计划 | CAP-100 |
| PRC-89 编制滚动预测 | business | VS-09.2 | OBJ-100 经营计划 | confirmed | 1 | OBJ-100 经营计划 | CAP-100 |
| PRC-107 知识条目的起草、评审、发布与修订废止 | business | VS-11.1、VS-11.2、VS-11.3 | OBJ-110 知识条目 | confirmed | 1 | OBJ-110 知识条目 | CAP-110 |
| PRC-113 并入股息红利个税扣缴申报 | event_policy | VS-12.2 | OBJ-92 法定申报 | confirmed | 5 | OBJ-92 法定申报 | CAP-103 |
| PRC-116 申请并审批请假 | business | VS-13.1 | OBJ-68 请假 | confirmed | 1 | AGG-71、OBJ-68 请假 | CAP-056 |
| PRC-117 申请并批准加班 | business | VS-13.1 | OBJ-72 加班 | confirmed | 1 | AGG-71、OBJ-68 请假、OBJ-72 加班 | CAP-056 |
| PRC-118 生成、确认并锁定月度考勤 | business | VS-13.1 | OBJ-111 月度考勤确认 | confirmed | 1 | OBJ-70 考勤记录、OBJ-72 加班、OBJ-111 月度考勤确认 | CAP-056 |
| PRC-120 评估月度绩效 | business | VS-13.2 | OBJ-109 绩效评估 | confirmed | 1 | OBJ-109 绩效评估 | CAP-056 |
| PRC-122 核定奖金 | business | VS-13.2 | OBJ-104 奖金核定 | confirmed | 1 | OBJ-104 奖金核定 | CAP-074 |
| PRC-124 计算、审核并批准月度薪酬 | business | VS-13.3 | OBJ-73 算薪运行 | confirmed | 1 | OBJ-73 算薪运行 | CAP-054 |
| PRC-125 申报个税扣缴与社保公积金 | business | VS-13.4、VS-06.1 | OBJ-92 法定申报 | confirmed | 1 | OBJ-62 社保与公积金参保、OBJ-92 法定申报 | CAP-103 |
| PRC-126 提交应付薪酬 | event_policy | VS-13.5 | OBJ-85 往来款项 | confirmed | 4 | OBJ-85 往来款项 | CAP-103 |
| PRC-127 薪酬发放完成 | event_policy | VS-13.5 | OBJ-73 算薪运行 | confirmed | 4 | OBJ-73 算薪运行 | CAP-054 |
| PRC-128 办理离职 | business | VS-13.6 | OBJ-56 员工 | confirmed | 1 | OBJ-05 协议、OBJ-06 合同范围条目、OBJ-07 付款条件项、OBJ-56 员工、OBJ-57 任职 | CAP-056 |
| PRC-129 离职后吊销会话 | event_policy | VS-13.6 | — | declared_none | — |  | — |
| PRC-130 离职后参保减员 | event_policy | VS-13.6 | OBJ-62 社保与公积金参保 | confirmed | 4 | AGG-93、OBJ-62 社保与公积金参保 | CAP-056 |
| PRC-134 薪酬事实已过账置位 | event_policy | VS-13.5 | OBJ-73 算薪运行 | confirmed | 4 | OBJ-73 算薪运行 | CAP-054 |
| PRC-136 调岗（任职变更，按人事业务生效日，触发数据范围复核） | business | VS-07.3 | OBJ-57 任职 | confirmed | 1 | AGG-93、OBJ-05 协议、OBJ-06 合同范围条目、OBJ-07 付款条件项、OBJ-57 任职、OBJ-62 社保与公积金参保 | CAP-056 |
| PRC-137 入职开通企业微信账号并绑定外部身份 | business | VS-07.3 | — | declared_none | — |  | — |
| PRC-138 离职停用企业微信账号并解绑 | business | VS-13.6 | — | declared_none | — |  | — |
| PRC-145 员工法人三口径不一致预警 | event_policy | VS-07.3 | — | declared_none | — |  | — |
| PRC-160 开立年度假期额度账户 | event_policy | VS-07.3 | — | declared_none | 5 | AGG-71 | — |
| PRC-178 补休到期未休或离职时转计发加班工资 | event_policy | VS-13.6 | — | declared_none | 5 | AGG-93 | — |
| PRC-181 记录经营复盘并跟踪经营动作 | business | VS-09.3 | OBJ-100 经营计划 | confirmed | 4 | OBJ-100 经营计划 | CAP-100 |
| PRC-182 知识条目使用情况 | read_model | VS-11.3 | — | read_model | — |  | — |

### revenue

| 流程 | 类型 | 所标段 | 主对象 | 状态 | 依据档 | 候选 / 写入对象 | 一级能力 |
|---|---|---|---|---|---|---|---|
| PRC-01 举办市场活动 | single_aggregate | VS-01.1 | OBJ-16 市场活动 | confirmed | 2 | OBJ-16 市场活动 | CAP-016 |
| PRC-02 计划并完成客户活动 | single_aggregate | VS-01.1 | OBJ-105 客户活动 | confirmed | 2 | OBJ-105 客户活动 | CAP-001 |
| PRC-03 登记并跟进线索，转为商机 | business | VS-01.1、VS-01.2 | OBJ-17 线索 | confirmed | 1 | OBJ-17 线索、OBJ-18 商机 | CAP-017 |
| PRC-05 维护往来单位档案 | single_aggregate | VS-01.1、VS-05.1 | OBJ-01 往来单位 | confirmed | 2 | OBJ-01 往来单位、OBJ-02 往来单位角色、OBJ-03 联系人 | CAP-001 |
| PRC-06 做独立性检查 | single_aggregate | VS-01.2 | OBJ-23 独立性检查 | confirmed | 2 | OBJ-23 独立性检查 | CAP-018 |
| PRC-07 编制并生效报价 | business | VS-01.2 | OBJ-21 报价 | confirmed | 2 | OBJ-21 报价、OBJ-22 报价估算分解 | CAP-018 |
| PRC-08 参加投标，缴纳保证金并登记结果 | business | VS-01.2 | OBJ-18 商机 | confirmed | 1 | OBJ-13 保证金与押金、OBJ-18 商机、OBJ-20 投标、OBJ-86 资金收付 | CAP-018 |
| PRC-10 收付保证金与保函 | single_aggregate | VS-01.2、VS-04.1 | OBJ-13 保证金与押金 | confirmed | 2 | OBJ-13 保证金与押金 | CAP-005 |
| PRC-12 发布服务产品与阶段类型版本 | single_aggregate | VS-01.2 | OBJ-24 服务产品 | confirmed | — | OBJ-24 服务产品、OBJ-25 阶段类型 | CAP-024 |
| PRC-13 批准并生效费率、计费系数与协议价目版本 | business | VS-01.2、VS-02.0 | OBJ-26 标准成本费率表 | confirmed | 2 | OBJ-05 协议、OBJ-06 合同范围条目、OBJ-07 付款条件项、OBJ-26 标准成本费率表、OBJ-27 计费系数 | CAP-026 |
| PRC-14 登记建设工程与招标项目 | single_aggregate | VS-01.2、VS-02.6、VS-03.1 | OBJ-46 建设工程 | confirmed | 2 | OBJ-19 招标项目、OBJ-46 建设工程、OBJ-47 建筑系统与部位 | CAP-046 |
| PRC-16 申请并转正风险交付授权 | business | VS-01.2、VS-02.0、VS-04.1 | OBJ-12 风险交付授权 | confirmed | 1 | OBJ-12 风险交付授权 | CAP-018 |
| PRC-17 建立项目并组建团队 | business | VS-02.0、VS-03.1 | OBJ-29 项目 | confirmed | 1 | OBJ-29 项目、OBJ-30 WBS 节点、OBJ-34 项目团队成员、OBJ-46 建设工程、OBJ-47 建筑系统与部位 | CAP-029 |
| PRC-18 编制并批准交付计划 | business | VS-02.0 | OBJ-31 交付计划 | confirmed | 4 | OBJ-31 交付计划 | CAP-029 |
| PRC-19 编制并锁定项目预算 | business | VS-02.0 | OBJ-32 项目预算 | confirmed | 1 | OBJ-32 项目预算 | CAP-029 |
| PRC-20 提出并满足资源需求 | business | VS-02.0、VS-02.1、VS-02.2、VS-02.3、VS-02.4、VS-02.5、VS-02.6 | OBJ-44 资源需求 | confirmed | — | OBJ-44 资源需求、OBJ-45 资源分配 | CAP-029 |
| PRC-21 推进阶段与工作包 | single_aggregate | VS-02.0、VS-02.1、VS-02.2、VS-02.3、VS-02.4、VS-02.5、VS-02.6 | OBJ-30 WBS 节点 | confirmed | 1 | OBJ-30 WBS 节点 | CAP-029 |
| PRC-22 阶段门预警 | read_model | VS-02.0、VS-02.1、VS-02.2、VS-02.3、VS-02.4、VS-02.5、VS-02.6、VS-02.7 | — | read_model | — |  | — |
| PRC-23 填报并审批工时 | business | VS-01.2、VS-02.0、VS-02.1、VS-02.2、VS-02.3、VS-02.4、VS-02.5、VS-02.6 | OBJ-66 工时单 | confirmed | 5 | OBJ-66 工时单 | CAP-056 |
| PRC-24 暂停、恢复、终止或取消项目 | business | VS-02.0、VS-02.1、VS-02.2、VS-02.3、VS-02.4、VS-02.5、VS-02.6、VS-02.7 | OBJ-29 项目 | confirmed | 1 | OBJ-29 项目 | CAP-029 |
| PRC-26 接收并确认发包人提供资料 | business | VS-02.0、VS-02.1、VS-02.2、VS-02.3、VS-02.4 | OBJ-42 发包人提供资料 | confirmed | 2 | OBJ-42 发包人提供资料 | CAP-029 |
| PRC-27 登记并处置项目风险与问题 | single_aggregate | VS-02.0、VS-02.1、VS-02.2、VS-02.3、VS-02.4、VS-02.5、VS-02.6、VS-02.7 | OBJ-108 项目风险与问题 | confirmed | 5 | OBJ-108 项目风险与问题 | CAP-029 |
| PRC-28 编制、评审并签发交付物，送达并登记客户认可 | business | VS-02.1、VS-02.2、VS-02.3、VS-02.4、VS-02.5、VS-02.6、VS-03.5 | OBJ-35 交付物 | confirmed | 1 | OBJ-35 交付物、OBJ-37 内部评审 | CAP-029 |
| PRC-30 提出并批准完工估算 | business | VS-02.2、VS-02.3、VS-02.4、VS-02.5、VS-02.6 | OBJ-33 完工估算 | confirmed | 2 | OBJ-33 完工估算 | CAP-029 |
| PRC-31 提出并发出设计变更 | business | VS-02.2、VS-02.3、VS-02.4、VS-02.5、VS-02.6 | OBJ-40 设计变更 | confirmed | 5 | OBJ-40 设计变更 | CAP-029 |
| PRC-32 申请并答复延期 | business | VS-02.2、VS-02.3、VS-02.4、VS-02.5、VS-02.6 | OBJ-41 延期申请 | confirmed | 5 | OBJ-41 延期申请 | CAP-029 |
| PRC-33 采集、关联并封存证据 | single_aggregate | VS-02.4、VS-02.7、VS-03.2、VS-04.2 | OBJ-53 证据 | confirmed | 1 | AGG-110、OBJ-53 证据 | CAP-053 |
| PRC-34 处置我方成果不符合项 | business | VS-02.6、VS-02.7 | OBJ-38 不符合项（原质量问题；原发现 OBJ-51 并入，用户 2026-10-15 Q32） | confirmed | 1 | OBJ-38 不符合项（原质量问题；原发现 OBJ-51 并入，用户 2026-10-15 Q32）、OBJ-39 返工认定 | CAP-029 |
| PRC-36 封存成果、结项并归档项目 | business | VS-02.7 | OBJ-29 项目 | confirmed | 1 | OBJ-29 项目、OBJ-35 交付物、OBJ-37 内部评审、OBJ-53 证据 | CAP-029 |
| PRC-37 回访客户满意度 | business | VS-02.7 | OBJ-106 客户满意度调查 | confirmed | 1 | OBJ-106 客户满意度调查、OBJ-108 项目风险与问题 | CAP-029 |
| PRC-38 编制并发布项目复盘 | business | VS-02.7 | OBJ-107 项目复盘 | confirmed | 1 | OBJ-107 项目复盘 | CAP-029 |
| PRC-41 制定检查计划并使其生效 | business | VS-03.1 | OBJ-48 检查计划 | confirmed | 5 | OBJ-48 检查计划 | CAP-005 |
| PRC-42 发布检查表模板版本 | single_aggregate | VS-03.1 | OBJ-50 检查表模板 | confirmed | 2 | OBJ-50 检查表模板 | CAP-024 |
| PRC-43 实施现场检查并锁定 | business | VS-03.1、VS-03.2、VS-02.6 | OBJ-49 检查 | confirmed | 1 | AGG-110、OBJ-49 检查、OBJ-53 证据 | CAP-029 |
| PRC-44 发布客户资产类不符合项并建议外部检测 | business | VS-03.2、VS-03.3 | OBJ-38 不符合项（原质量问题；原发现 OBJ-51 并入，用户 2026-10-15 Q32） | confirmed | 1 | AGG-110、OBJ-38 不符合项（原质量问题；原发现 OBJ-51 并入，用户 2026-10-15 Q32）、OBJ-52 外部检测 | CAP-029 |
| PRC-45 跟踪整改并复查闭合 | business | VS-03.4 | OBJ-38 不符合项（原质量问题；原发现 OBJ-51 并入，用户 2026-10-15 Q32） | confirmed | 1 | AGG-110、OBJ-38 不符合项（原质量问题；原发现 OBJ-51 并入，用户 2026-10-15 Q32）、OBJ-49 检查、OBJ-53 证据 | CAP-029 |
| PRC-46 采信外部检测报告 | business | VS-03.4 | OBJ-52 外部检测 | confirmed | 3 | OBJ-52 外部检测、OBJ-53 证据 | CAP-029 |
| PRC-47 起草、评审并回签协议 | business | VS-04.1 | OBJ-05 协议 | confirmed | 1 | OBJ-05 协议、OBJ-06 合同范围条目、OBJ-07 付款条件项 | CAP-005 |
| PRC-48 提出并生效协议变更或签证 | business | VS-04.6 | OBJ-09 协议变更 | confirmed | 2 | OBJ-05 协议、OBJ-06 合同范围条目、OBJ-07 付款条件项、OBJ-09 协议变更 | CAP-005 |
| PRC-49 提出并认定索赔 | business | VS-04.6、VS-04.5 | OBJ-10 索赔 | confirmed | 2 | OBJ-10 索赔 | CAP-005 |
| PRC-50 占用框架限额并提交任务订单 | business | VS-04.6 | OBJ-05 协议 | confirmed | 3 | AGG-14、OBJ-05 协议、OBJ-06 合同范围条目、OBJ-07 付款条件项 | CAP-005 |
| PRC-51 建立并更新开票与收入额度 | event_policy | VS-04.1、VS-04.2、VS-04.3 | — | declared_none | 5 | AGG-44 | — |
| PRC-53 确认收入 | event_policy | VS-04.2 | OBJ-81 收入确认 | confirmed | 5 | AGG-44 | CAP-005 |
| PRC-54 视为同意期满处理 | business | VS-04.2 | OBJ-81 收入确认 | confirmed | — | AGG-44、OBJ-05 协议、OBJ-06 合同范围条目、OBJ-07 付款条件项 | CAP-005 |
| PRC-56 付款条件项转为可结算 | event_policy | VS-04.3 | OBJ-07 付款条件项 | confirmed | — | OBJ-05 协议、OBJ-06 合同范围条目、OBJ-07 付款条件项 | CAP-005 |
| PRC-57 申请并开具销项发票 | business | VS-04.3 | OBJ-83 增值税发票 | confirmed | 1 | OBJ-82 开票申请、OBJ-83 增值税发票 | CAP-103 |
| PRC-58 红冲销项发票 | business | VS-04.3 | OBJ-83 增值税发票 | confirmed | 4 | AGG-44、OBJ-83 增值税发票 | CAP-103 |
| PRC-59 由销项发票创建应收 | event_policy | VS-04.4 | OBJ-85 往来款项 | confirmed | 4 | OBJ-85 往来款项 | CAP-103 |
| PRC-60 认领收款并核销应收 | business | VS-04.4 | OBJ-85 往来款项 | confirmed | 1 | OBJ-85 往来款项、OBJ-86 资金收付 | CAP-103 |
| PRC-61 检测应收逾期并催收 | business | VS-04.4 | OBJ-85 往来款项 | confirmed | 3 | OBJ-85 往来款项 | CAP-103 |
| PRC-62 质量保证金到期提示 | read_model | VS-04.4 | — | read_model | — |  | — |
| PRC-63 商业汇票兑付与逾期 | business | VS-04.4 | OBJ-85 往来款项 | confirmed | 3 | OBJ-85 往来款项、OBJ-86 资金收付 | CAP-103 |
| PRC-66 发起结算，确认最终价款并结算协议 | business | VS-04.5、VS-04.6 | OBJ-05 协议 | confirmed | 1 | OBJ-05 协议、OBJ-06 合同范围条目、OBJ-07 付款条件项 | CAP-005 |
| PRC-68 终止协议 | single_aggregate | VS-04.5 | OBJ-05 协议 | confirmed | 1 | OBJ-05 协议、OBJ-06 合同范围条目、OBJ-07 付款条件项 | CAP-005 |
| PRC-69 供应商准入与资质核验 | business | VS-05.1 | OBJ-02 往来单位角色 | confirmed | 1 | OBJ-01 往来单位、OBJ-02 往来单位角色、OBJ-03 联系人、OBJ-65 资质证书 | CAP-001 |
| PRC-78 年检、核查与延续企业资质 | business | VS-06.2 | OBJ-65 资质证书 | confirmed | 1 | OBJ-65 资质证书 | CAP-054 |
| PRC-86 发放、归还与催还员工借款 | business | VS-08.1 | OBJ-85 往来款项 | confirmed | 1 | OBJ-85 往来款项、OBJ-86 资金收付 | CAP-103 |
| PRC-91 认定工时有效性 | event_policy | VS-10.1 | OBJ-66 工时单 | confirmed | 5 | OBJ-66 工时单 | CAP-056 |
| PRC-147 费用报销核定后冲抵员工借款并确认应付 | event_policy | VS-08.2 | OBJ-88 费用报销 | confirmed | 4 | OBJ-85 往来款项、OBJ-88 费用报销 | CAP-056 |
| PRC-148 收入确认审批通过后占用转实并提交过账 | event_policy | VS-04.2 | OBJ-81 收入确认 | confirmed | 5 | AGG-44 | CAP-005 |
| PRC-149 冲销收入确认 | business | VS-04.2、VS-04.5 | OBJ-81 收入确认 | confirmed | 5 | AGG-44 | CAP-005 |
| PRC-150 发票红冲后冲减应收 | event_policy | VS-04.4 | OBJ-85 往来款项 | confirmed | 4 | OBJ-85 往来款项 | CAP-103 |
| PRC-151 填报经营产值 | business | VS-02.2、VS-02.3、VS-02.4、VS-02.5、VS-02.6 | OBJ-33 完工估算 | confirmed | 5 | OBJ-33 完工估算 | CAP-029 |
| PRC-152 登记付款条件达成 | business | VS-04.3 | OBJ-07 付款条件项 | confirmed | 2 | OBJ-05 协议、OBJ-06 合同范围条目、OBJ-07 付款条件项 | CAP-005 |
| PRC-153 退还投标保证金 | business | VS-01.2、VS-04.1 | OBJ-13 保证金与押金 | confirmed | 5 | OBJ-13 保证金与押金 | CAP-005 |
| PRC-154 收入三口径对照 | read_model | VS-04.2、VS-04.3 | — | read_model | — |  | — |
| PRC-179 项目开始执行后协议转入履行中 | event_policy | VS-04.6 | OBJ-05 协议 | confirmed | 4 | OBJ-05 协议、OBJ-06 合同范围条目、OBJ-07 付款条件项 | CAP-005 |
| PRC-183 作出参投决策 | business | VS-01.2 | OBJ-18 商机 | confirmed | 1 | OBJ-18 商机 | CAP-018 |

## ② 阶段承载对象在该段无流程以它为主对象或次要对象

无

## 主对象不是业务对象（技术聚合不承载价值项：改为业务对象，或写 none 并说明）

无

## ③ 同一主对象被多条流程使用（判断重复或合理分工）

- OBJ-05 协议：PRC-47 起草、评审并回签协议（VS-04.1，confirmed）；PRC-50 占用框架限额并提交任务订单（VS-04.6，confirmed）；PRC-66 发起结算，确认最终价款并结算协议（VS-04.5、VS-04.6，confirmed）；PRC-68 终止协议（VS-04.5，confirmed）；PRC-70 起草并生效供方协议（VS-05.2，confirmed）；PRC-76 完成并结算供方协议（VS-05.6，confirmed）；PRC-179 项目开始执行后协议转入履行中（VS-04.6，confirmed）
- OBJ-07 付款条件项：PRC-56 付款条件项转为可结算（VS-04.3，confirmed）；PRC-152 登记付款条件达成（VS-04.3，confirmed）
- OBJ-09 协议变更：PRC-48 提出并生效协议变更或签证（VS-04.6，confirmed）；PRC-71 生效分包变更（VS-05.2，confirmed）
- OBJ-13 保证金与押金：PRC-10 收付保证金与保函（VS-01.2、VS-04.1，confirmed）；PRC-153 退还投标保证金（VS-01.2、VS-04.1，confirmed）
- OBJ-18 商机：PRC-08 参加投标，缴纳保证金并登记结果（VS-01.2，confirmed）；PRC-183 作出参投决策（VS-01.2，confirmed）
- OBJ-29 项目：PRC-17 建立项目并组建团队（VS-02.0、VS-03.1，confirmed）；PRC-24 暂停、恢复、终止或取消项目（VS-02.0、VS-02.1、VS-02.2、VS-02.3、VS-02.4、VS-02.5、VS-02.6、VS-02.7，confirmed）；PRC-36 封存成果、结项并归档项目（VS-02.7，confirmed）
- OBJ-33 完工估算：PRC-30 提出并批准完工估算（VS-02.2、VS-02.3、VS-02.4、VS-02.5、VS-02.6，confirmed）；PRC-151 填报经营产值（VS-02.2、VS-02.3、VS-02.4、VS-02.5、VS-02.6，confirmed）
- OBJ-38 不符合项（原质量问题；原发现 OBJ-51 并入，用户 2026-10-15 Q32）：PRC-34 处置我方成果不符合项（VS-02.6、VS-02.7，confirmed）；PRC-44 发布客户资产类不符合项并建议外部检测（VS-03.2、VS-03.3，confirmed）；PRC-45 跟踪整改并复查闭合（VS-03.4，confirmed）
- OBJ-56 员工：PRC-82 登记候选人并作出录用决定（VS-07.2，confirmed）；PRC-83 办理入职与任职（VS-07.3，confirmed）；PRC-85 试用期评估与转正（VS-07.3、VS-13.2，confirmed）；PRC-128 办理离职（VS-13.6，confirmed）
- OBJ-62 社保与公积金参保：PRC-84 入职后参保增员与建立薪酬档案（VS-07.3，confirmed）；PRC-130 离职后参保减员（VS-13.6，confirmed）
- OBJ-66 工时单：PRC-23 填报并审批工时（VS-01.2、VS-02.0、VS-02.1、VS-02.2、VS-02.3、VS-02.4、VS-02.5、VS-02.6，confirmed）；PRC-91 认定工时有效性（VS-10.1，confirmed）
- OBJ-73 算薪运行：PRC-124 计算、审核并批准月度薪酬（VS-13.3，confirmed）；PRC-127 薪酬发放完成（VS-13.5，confirmed）；PRC-134 薪酬事实已过账置位（VS-13.5，confirmed）
- OBJ-81 收入确认：PRC-53 确认收入（VS-04.2，confirmed）；PRC-54 视为同意期满处理（VS-04.2，confirmed）；PRC-148 收入确认审批通过后占用转实并提交过账（VS-04.2，confirmed）；PRC-149 冲销收入确认（VS-04.2、VS-04.5，confirmed）
- OBJ-83 增值税发票：PRC-57 申请并开具销项发票（VS-04.3，confirmed）；PRC-58 红冲销项发票（VS-04.3，confirmed）；PRC-73 登记并查验进项发票（VS-05.4，confirmed）
- OBJ-85 往来款项：PRC-59 由销项发票创建应收（VS-04.4，confirmed）；PRC-60 认领收款并核销应收（VS-04.4，confirmed）；PRC-61 检测应收逾期并催收（VS-04.4，confirmed）；PRC-63 商业汇票兑付与逾期（VS-04.4，confirmed）；PRC-74 提交供方应付（VS-05.4，confirmed）；PRC-86 发放、归还与催还员工借款（VS-08.1，confirmed）；PRC-126 提交应付薪酬（VS-13.5，confirmed）；PRC-131 付款成功后核销应付（VS-05.5、VS-08.3、VS-13.5，confirmed）；PRC-150 发票红冲后冲减应收（VS-04.4，confirmed）；PRC-159 申报已提交后确认税费应付（VS-06.1，confirmed）；PRC-171 核对差旅服务商月账单并确认应付（VS-05.4，confirmed）；PRC-175 直接付款申请（VS-05.5，confirmed）；PRC-176 预付款核销（VS-05.5，confirmed）
- OBJ-86 资金收付：PRC-75 发起、审批并执行付款（VS-05.5、VS-08.3、VS-13.5，confirmed）；PRC-132 应付核销后置付款已核销并提交过账候选（VS-05.5、VS-08.3、VS-13.5，confirmed）
- OBJ-88 费用报销：PRC-87 提交、核定并确认报销应付（VS-08.2，confirmed）；PRC-133 应付结清后报销置已付（VS-08.3，confirmed）；PRC-147 费用报销核定后冲抵员工借款并确认应付（VS-08.2，confirmed）
- OBJ-91 合伙人出资：PRC-111 认缴并实缴出资（VS-12.1，confirmed）；PRC-114 退回出资（VS-12.3，confirmed）
- OBJ-92 法定申报：PRC-77 申报并缴纳增值税、附加税与企业所得税（VS-06.1，confirmed）；PRC-113 并入股息红利个税扣缴申报（VS-12.2，confirmed）；PRC-125 申报个税扣缴与社保公积金（VS-13.4、VS-06.1，confirmed）
- OBJ-97 会计期间：PRC-101 月结检查并关账（VS-10.1，confirmed）；PRC-102 启用第 13 期并年末关账（VS-10.1，confirmed）
- OBJ-98 会计分录：PRC-93 生成会计分录（VS-10.0，confirmed）；PRC-94 制单并过账受控手工凭证（VS-10.0，confirmed）；PRC-135 处理会计分录待处理队列与推送重投（VS-10.0，confirmed）
- OBJ-100 经营计划：PRC-88 编制、批准并基线化经营计划与预算（VS-09.1、VS-09.3，confirmed）；PRC-89 编制滚动预测（VS-09.2，confirmed）；PRC-181 记录经营复盘并跟踪经营动作（VS-09.3，confirmed）
- OBJ-112 利润分配：PRC-112 决议并支付利润分配（VS-12.2，confirmed）；PRC-163 应付结清后登记利润分配支付（VS-12.2，confirmed）

## ④ 主对象推出的一级能力不在所标段的能力清单

- PRC-23 填报并审批工时：OBJ-66 工时单 → CAP-056；所标段 VS-01.2 ['CAP-018', 'CAP-019', 'CAP-024', 'CAP-026']、VS-02.0 ['CAP-029']、VS-02.1 ['CAP-029']、VS-02.2 ['CAP-029']、VS-02.3 ['CAP-029']、VS-02.4 ['CAP-029']、VS-02.5 ['CAP-029']、VS-02.6 ['CAP-029', 'CAP-046']
- PRC-41 制定检查计划并使其生效：OBJ-48 检查计划 → CAP-005；所标段 VS-03.1 ['CAP-029', 'CAP-046']
- PRC-42 发布检查表模板版本：OBJ-50 检查表模板 → CAP-024；所标段 VS-03.1 ['CAP-029', 'CAP-046']
- PRC-53 确认收入：OBJ-81 收入确认 → CAP-005；所标段 VS-04.2 ['CAP-053']
- PRC-54 视为同意期满处理：OBJ-81 收入确认 → CAP-005；所标段 VS-04.2 ['CAP-053']
- PRC-56 付款条件项转为可结算：OBJ-07 付款条件项 → CAP-005；所标段 VS-04.3 ['CAP-103']
- PRC-79 登记继续教育并延续注册执业证书：OBJ-64 注册执业证书 → CAP-056；所标段 VS-06.2 ['CAP-054']
- PRC-91 认定工时有效性：OBJ-66 工时单 → CAP-056；所标段 VS-10.1 ['CAP-103']
- PRC-99 计提折旧并处置公司资产：OBJ-93 公司资产 → CAP-093；所标段 VS-10.0 ['CAP-103']
- PRC-148 收入确认审批通过后占用转实并提交过账：OBJ-81 收入确认 → CAP-005；所标段 VS-04.2 ['CAP-053']
- PRC-152 登记付款条件达成：OBJ-07 付款条件项 → CAP-005；所标段 VS-04.3 ['CAP-103']

### 主对象无对应能力（无 OBJ 的聚合，或对象未入能力图）

无

## 支撑与集成流程「支撑本段」（47 条，有支撑段 37；自动得出，不手写）

| 流程 | 主对象 | 状态 | 类别 | 支撑段（条件 / 使用） |
|---|---|---|---|---|
| PRC-52 开放可触发收入确认的证据类型 | — | support_declared_none | — | VS-04.2（使用） |
| PRC-64 导入银行流水 | OBJ-90 银行流水 | support | support | VS-01.2（使用）、VS-04.1（使用）、VS-04.4（使用）、VS-05.5（使用）、VS-08.3（使用）、VS-13.5（使用） |
| PRC-65 维护资金账户 | OBJ-89 资金账户 | support | support | VS-04.4（使用）、VS-05.5（使用）、VS-08.3（使用）、VS-13.5（使用） |
| PRC-80 维护法人、组织与岗位 | OBJ-55 组织单元 | support | — | VS-05.5（使用）、VS-06.2（使用）、VS-07.1（条件） |
| PRC-97 批准并生效分摊规则版本 | OBJ-78 间接成本池 → 分摊规则版本 | support | — | VS-10.0（使用） |
| PRC-100 发布总账配置版本 | OBJ-95 会计科目 | support | support | VS-10.0（使用） |
| PRC-105 评审并生效报表行定义版本 | OBJ-99 报表行定义 | support | support | VS-10.2（使用） |
| PRC-115 导入考勤记录并补卡 | OBJ-70 考勤记录 | support | support | VS-13.1（使用） |
| PRC-119 维护工作日历与标准工时版本 | OBJ-28 工作日历 | support | support | VS-01.2（使用）、VS-02.0（使用）、VS-02.1（使用）、VS-02.2（使用）、VS-02.3（使用）、VS-02.4（使用）、VS-02.5（使用）、VS-02.6（使用）、VS-13.1（条件） |
| PRC-121 制定激励方案 | OBJ-74 激励方案 | support | management | VS-13.2（条件） |
| PRC-123 调整薪酬档案与专项附加扣除 | — | support_declared_none | — | VS-07.3（使用）、VS-13.3（使用）、VS-13.6（使用） |
| PRC-139 用印申请与用印登记 | OBJ-113 用印申请 | support | — | 无：所触对象不在任何段条件里、也不被段内流程使用（配置类须在读取它的流程步骤补 reads） |
| PRC-140 合同借阅 | OBJ-05 协议 | support | business | VS-01.2（使用）、VS-02.0（条件）、VS-02.7（条件）、VS-03.1（使用）、VS-04.1（条件）、VS-04.6（条件）、VS-04.2（条件）、VS-04.3（使用）、VS-04.5（条件）、VS-05.2（条件）、VS-05.3（条件）、VS-05.6（条件）、VS-07.3（条件）、VS-10.0（使用）、VS-13.6（条件） |
| PRC-141 销售合同归档 | OBJ-05 协议 | support | business | VS-01.2（使用）、VS-02.0（条件）、VS-02.7（条件）、VS-03.1（使用）、VS-04.1（条件）、VS-04.6（条件）、VS-04.2（条件）、VS-04.3（使用）、VS-04.5（条件）、VS-05.2（条件）、VS-05.3（条件）、VS-05.6（条件）、VS-07.3（条件）、VS-10.0（使用）、VS-13.6（条件） |
| PRC-142 IT 需求工单 | OBJ-114 IT 需求工单 | support | — | 无：所触对象不在任何段条件里、也不被段内流程使用（配置类须在读取它的流程步骤补 reads） |
| PRC-143 IT 资产领用 | OBJ-115 IT 资产使用记录 | support | — | VS-10.0（使用） |
| PRC-144 员工活动类申请（周年贺卡、节日活动、创新项目发布） | OBJ-116 员工活动申请 | support | — | 无：所触对象不在任何段条件里、也不被段内流程使用（配置类须在读取它的流程步骤补 reads） |
| PRC-146 协议借阅逾期催还 | OBJ-05 协议 | support | business | VS-01.2（使用）、VS-02.0（条件）、VS-02.7（条件）、VS-03.1（使用）、VS-04.1（条件）、VS-04.6（条件）、VS-04.2（条件）、VS-04.3（使用）、VS-04.5（条件）、VS-05.2（条件）、VS-05.3（条件）、VS-05.6（条件）、VS-07.3（条件）、VS-10.0（使用）、VS-13.6（条件） |
| PRC-155 IT 资产调拨 | OBJ-115 IT 资产使用记录 | support | — | VS-10.0（使用） |
| PRC-156 IT 资产归还 | OBJ-115 IT 资产使用记录 | support | — | VS-10.0（使用） |
| PRC-157 IT 资产报修与维修登记 | OBJ-115 IT 资产使用记录 | support | — | VS-10.0（使用） |
| PRC-161 年末关闭预算账户 | OBJ-100 经营计划 | support | management | VS-05.2（条件）、VS-08.2（使用）、VS-09.1（条件）、VS-09.2（条件）、VS-09.3（条件）、VS-10.2（使用） |
| PRC-162 发布假期规则版本 | — | support_declared_none | — | VS-13.1（使用） |
| PRC-165 发布成本核算参数版本 | — | support_declared_none | — | VS-01.2（使用） |
| PRC-166 发布资产类别与折旧政策版本 | — | support_declared_none | — | VS-10.0（使用） |
| PRC-167 编制并签字确认期初余额 | — | support_declared_none | — | VS-10.1（使用） |
| PRC-168 发布口径版本 | — | support_declared_none | — | VS-10.0（使用）、VS-10.1（使用） |
| PRC-174 发布出差补贴标准版本 | — | support_declared_none | — | VS-08.2（使用） |
| PRC-177 向差旅服务商同步员工与组织 | — | support_declared_none | — | 无：所触对象不在任何段条件里、也不被段内流程使用（配置类须在读取它的流程步骤补 reads） |
| PRC-180 发布薪酬规则版本 | — | support_declared_none | — | VS-13.3（使用） |
| PRC-184 同步组织来源数据 | OBJ-55 组织单元 | integration_proposed | — | VS-01.1（使用）、VS-01.2（使用）、VS-02.0（使用）、VS-02.1（使用）、VS-02.2（使用）、VS-02.3（使用）、VS-02.4（使用）、VS-02.5（使用）、VS-02.6（使用）、VS-03.1（使用）、VS-03.2（使用）、VS-05.5（使用）、VS-07.1（使用）、VS-07.2（条件）、VS-07.3（条件）、VS-08.0（使用）、VS-08.1（使用）、VS-08.2（使用）、VS-08.3（使用）、VS-13.1（条件）、VS-13.2（使用）、VS-13.3（使用）、VS-13.5（使用）、VS-13.6（条件） |
| PRC-185 核对组织快照与来源 | OBJ-55 组织单元 | integration_proposed | — | VS-01.1（使用）、VS-01.2（使用）、VS-02.0（使用）、VS-02.1（使用）、VS-02.2（使用）、VS-02.3（使用）、VS-02.4（使用）、VS-02.5（使用）、VS-02.6（使用）、VS-03.1（使用）、VS-03.2（使用）、VS-05.5（使用）、VS-07.1（使用）、VS-07.2（条件）、VS-07.3（条件）、VS-08.0（使用）、VS-08.1（使用）、VS-08.2（使用）、VS-08.3（使用）、VS-13.1（条件）、VS-13.2（使用）、VS-13.3（使用）、VS-13.5（使用）、VS-13.6（条件） |
| PRC-186 刷新部门简称 | OBJ-55 组织单元 | integration_proposed | — | VS-05.5（使用）、VS-07.1（使用） |
| PRC-187 校验致远单点登录票据 | — | integration_none | — | 无：所触对象不在任何段条件里、也不被段内流程使用（配置类须在读取它的流程步骤补 reads） |
| PRC-188 识别企业微信登录身份 | OBJ-56 员工 | integration_proposed | support | VS-01.1（使用）、VS-01.2（使用）、VS-02.0（使用）、VS-02.1（使用）、VS-02.2（使用）、VS-02.3（使用）、VS-02.4（使用）、VS-02.5（使用）、VS-02.6（使用）、VS-03.1（使用）、VS-03.2（使用）、VS-05.5（使用）、VS-07.2（条件）、VS-07.3（条件）、VS-08.0（使用）、VS-08.1（使用）、VS-08.2（使用）、VS-08.3（使用）、VS-13.1（条件）、VS-13.2（使用）、VS-13.3（使用）、VS-13.5（使用）、VS-13.6（条件） |
| PRC-189 投递企业微信通知 | OBJ-98 会计分录 | integration_proposed | support | VS-06.1（使用）、VS-10.0（条件）、VS-10.1（条件）、VS-10.2（条件） |
| PRC-190 重投领域事件 | — | integration_none | — | 无：所触对象不在任何段条件里、也不被段内流程使用（配置类须在读取它的流程步骤补 reads） |
| PRC-191 归档数据到对象存储 | — | integration_none | — | 无：所触对象不在任何段条件里、也不被段内流程使用（配置类须在读取它的流程步骤补 reads） |
| PRC-192 读取老系统待办 | — | integration_none | — | 无：所触对象不在任何段条件里、也不被段内流程使用（配置类须在读取它的流程步骤补 reads） |
| PRC-193 编排历史数据迁入 | OBJ-56 员工 | integration_proposed | support | VS-01.1（使用）、VS-01.2（使用）、VS-02.0（使用）、VS-02.1（使用）、VS-02.2（使用）、VS-02.3（使用）、VS-02.4（使用）、VS-02.5（使用）、VS-02.6（使用）、VS-03.1（使用）、VS-03.2（使用）、VS-04.2（使用）、VS-04.3（条件）、VS-05.4（条件）、VS-05.5（使用）、VS-06.1（使用）、VS-07.2（条件）、VS-07.3（条件）、VS-08.0（使用）、VS-08.1（使用）、VS-08.2（条件）、VS-08.3（使用）、VS-10.0（条件）、VS-10.1（条件）、VS-10.2（条件）、VS-13.1（条件）、VS-13.2（使用）、VS-13.3（使用）、VS-13.5（使用）、VS-13.6（条件） |
| PRC-194 推送凭证到U8C | OBJ-98 会计分录 | integration_proposed | support | VS-06.1（使用）、VS-10.0（条件）、VS-10.1（条件）、VS-10.2（条件） |
| PRC-195 同步U8C主数据 | OBJ-98 会计分录 | integration_proposed | support | VS-06.1（使用）、VS-10.0（条件）、VS-10.1（条件）、VS-10.2（条件） |
| PRC-196 对接数电发票平台 | OBJ-83 增值税发票 | integration_proposed | support | VS-04.2（使用）、VS-04.3（条件）、VS-05.4（条件）、VS-06.1（使用）、VS-08.2（条件） |
| PRC-197 同步差旅员工与组织 | — | integration_none | — | 无：所触对象不在任何段条件里、也不被段内流程使用（配置类须在读取它的流程步骤补 reads） |
| PRC-198 接收差旅订单与账单 | OBJ-85 往来款项 | integration_proposed | support | VS-04.4（条件）、VS-05.4（条件）、VS-05.5（条件）、VS-06.1（使用）、VS-08.1（条件）、VS-08.2（使用）、VS-08.3（条件）、VS-12.2（使用）、VS-12.3（使用）、VS-13.5（条件） |
| PRC-199 推送出差申请并回写审批 | — | integration_none | — | 无：所触对象不在任何段条件里、也不被段内流程使用（配置类须在读取它的流程步骤补 reads） |
| PRC-200 拉取企业微信打卡记录 | OBJ-70 考勤记录 | integration_proposed | support | VS-13.1（使用） |

## 无用途类别（29）：归段跨不同类别价值流且主对象无能力，或支撑流程主对象缺 / 是技术聚合

- PRC-90 出具经营月报（read_model，主对象 —）
- PRC-52 开放可触发收入确认的证据类型（support_declared_none，主对象 —）
- PRC-80 维护法人、组织与岗位（support，主对象 OBJ-55）
- PRC-97 批准并生效分摊规则版本（support，主对象 OBJ-78）
- PRC-123 调整薪酬档案与专项附加扣除（support_declared_none，主对象 —）
- PRC-139 用印申请与用印登记（support，主对象 OBJ-113）
- PRC-142 IT 需求工单（support，主对象 OBJ-114）
- PRC-143 IT 资产领用（support，主对象 OBJ-115）
- PRC-144 员工活动类申请（周年贺卡、节日活动、创新项目发布）（support，主对象 OBJ-116）
- PRC-155 IT 资产调拨（support，主对象 OBJ-115）
- PRC-156 IT 资产归还（support，主对象 OBJ-115）
- PRC-157 IT 资产报修与维修登记（support，主对象 OBJ-115）
- PRC-162 发布假期规则版本（support_declared_none，主对象 —）
- PRC-165 发布成本核算参数版本（support_declared_none，主对象 —）
- PRC-166 发布资产类别与折旧政策版本（support_declared_none，主对象 —）
- PRC-167 编制并签字确认期初余额（support_declared_none，主对象 —）
- PRC-168 发布口径版本（support_declared_none，主对象 —）
- PRC-174 发布出差补贴标准版本（support_declared_none，主对象 —）
- PRC-177 向差旅服务商同步员工与组织（support_declared_none，主对象 —）
- PRC-180 发布薪酬规则版本（support_declared_none，主对象 —）
- PRC-184 同步组织来源数据（integration_proposed，主对象 OBJ-55）
- PRC-185 核对组织快照与来源（integration_proposed，主对象 OBJ-55）
- PRC-186 刷新部门简称（integration_proposed，主对象 OBJ-55）
- PRC-187 校验致远单点登录票据（integration_none，主对象 —）
- PRC-190 重投领域事件（integration_none，主对象 —）
- PRC-191 归档数据到对象存储（integration_none，主对象 —）
- PRC-192 读取老系统待办（integration_none，主对象 —）
- PRC-197 同步差旅员工与组织（integration_none，主对象 —）
- PRC-199 推送出差申请并回写审批（integration_none，主对象 —）

## E 需用户判断（候选，经组与本会话筛过再交）

- ④ 中属阶段能力清单漏映射的，改 value-streams.v2.yaml 的 capabilities 需用户确认（第 ③ 步对账为用户已审口径）；
- 主对象是无 OBJ 的聚合（AGG-xx）的：该聚合要不要在信息图有对象、归哪个能力，属信息层，随信息层对齐交用户；
- ③ 中判为重复的流程合并、② 中缺流程的承载对象补流程，涉及流程增删的交用户；其余由组与本会话定。

