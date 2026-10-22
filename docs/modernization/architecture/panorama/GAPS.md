<!-- 由 architecture/panorama/panorama_build.py 生成，勿手改 -->

# 全景追溯缺口

每轮生成一次，反复跑到收敛。缺口是「缺上层」还是「本身多余」，由 one-architecture 逐条判定，写进层笔记的待决问题。

## N13-3 判据变更

同一输入按改前判据计 101 个缺口，按改后判据计 99 个缺口。改前同时要求指标自带 facts 与 bind，改后按计算绑定判定；绑定的事实、公式版本与时间基准缺失由 N14-1 报告。技术度量均排除，pending 仍计缺口并附原因前 40 字。

## 覆盖率

按起点去重；via 列为每种出处下已覆盖起点数（可重叠），不是执行通过率。表→聚合只统计 data-access-registry 的三类业务表；API→BC 按路径统计，方法数见 api 节点。

| 关系 | 已覆盖 / 总数 | via（已覆盖起点数） |
|---|---|---|
| INV→EP | 3 / 151 | enforces: 0、manual: 3、rule: 0 |
| INV→TC | 1 / 151 | class_covers: 0、covers: 0、rule: 1 |
| RULE→TC（有 INV 对照） | 1 / 38 | class_covers: 0、covers: 1 |
| 表→聚合 | 20 / 32 | built: 20、panorama: 0 |
| 事件→聚合 | 59 / 59 | events: 47、module: 49 |
| 事件→发布 BC | 59 / 59 | publisher: 59 |
| API→BC | 58 / 58 | controller: 58 |

登记表其他 role（只计数）：CONFIG 3、CONFIG_VERSIONED 4、COUNTER 1、DIMENSION_VERSIONED 7、FRAMEWORK 6、IDEMPOTENCY 2、LINEAGE 1、LOG_APPEND_ONLY 8、ONE_TIME 3、QUEUE 1、SNAPSHOT 4。

| 节点 | 层 | 条目数 |
|---|---|---|
| 01 干系人 | sh | 39 |
| 02 关注点 | con | 0 |
| 03 价值主张 | vp | 15 |
| 04 取舍决定 | decr | 2 |
| 05 业务能力 | cap | 24 |
| 06 价值流与阶段 | vs | 15 |
| 06 价值流与阶段 | stage | 61 |
| 07 业务流程 | prc | 202 |
| 08 约束与认定规则 | inv | 151 |
| 09 业务对象 | obj | 104 |
| 10 受控业务执行 | act | 680 |
| 11 业务事实 | fact | 37 |
| 12 维度 | dim | 10 |
| 13 指标与评价 | metric | 112 |
| 14 计算绑定 | bind | 11 |
| 15 决策与行动 | dec | 0 |
| 09/10 映射  | agg | 145 |
| 09/10 映射  | bc | 51 |
| 09/10 映射  | sd | 13 |
| 09/10 映射  | table | 72 |
| 08 约束与认定规则 | rule | 512 |
| 10 受控业务执行 | ep | 601 |
| 10 受控业务执行 | tc | 1132 |
| 10 映射  | api | 68 |
| 10 映射  | mod | 14 |
| 10 受控业务执行 | cmd | 613 |
| 09/10 映射  | otr | 881 |
| 10 映射  | evt | 59 |
| 01 干系人 | shcat | 6 |
| 07 业务流程 | steps | 661 |
| 01 映射  | perm | 27 |
| 01 映射  | srole | 7 |
| 01 映射  | role | 56 |

对象按分类：业务对象 96、从属对象 6、参照数据 1、视图 1。

| 检查 | 含义 | 缺口 |
|---|---|---|
| N01-1 | 每个干系人都登记了关注点（02 CON 未登记时全部计为缺口） | 39 |
| N01-3 | 每个干系人类别对应一个一级业务对象，类别角色取值在信息图可查；没有对象的登记 04 并写理由 | 0 |
| N01-4 | 每个具体干系人登记「在系统中的体现」（五选一），并是类别对象（或其从属对象）的一个类型、角色或属性取值；不进系统的登记 04 | 0 |
| N01-2 | 每个干系人至少触发、接收或参与一条价值流（价值流 triggering_stakeholders、value_receivers，阶段 participants） | 0 |
| N03-1 | 每条价值流都有价值主张（暂以价值流的 value 字段作 03 的登记） | 0 |
| N05-1 | 每个阶段都有业务能力支撑（阶段 capabilities 字段） | 0 |
| N06-1 | 每个阶段都有对象状态作为门（进入或退出条件引用对象状态） | 0 |
| N06-2 | 每条价值流的门对象中，至少一个顺依附链（信息图「父对象」列）追到一级业务对象 | 1 |
| N06-3 | 每个阶段的门对象中，至少一个顺依附链追到一级业务对象（无门对象的阶段也计缺口） | 7 |
| N06-4 | 每条价值流都有触发者和价值接收者 | 0 |
| N07-1 | 每个有效流程都能归集到价值流：所标阶段或支撑本段，且有作用对象（同 anchoring_check C1） | 0 |
| N07-2 | 流程主要对象引用的编号存在且是有效对象（主要对象为空不算缺口：作用对象由 N07-1 / anchoring C1 判） | 0 |
| N07-P1 | P-1：流程骨架中作用于聚合的步骤都声明 Command，且该命令在同一聚合下登记于各组 commands.yaml | 0 |
| N01-R1 | 权责表引用的每个干系人编号都归入 ssot/07-org-roles/roles.yaml 的某个 ROLE（系统执行者与模块除外；roles.yaml 未建时不查） | 1 |
| N01-R2 | 每个 ROLE 写明由谁承担（host_category 非空） | 0 |
| N01-R3 | 每个权限码至少一个系统角色持有（迁移种子与 AdministratorMode 代码定义，只报告） | 4 |
| N08-1 | 每个不变量都归属存在的聚合 | 0 |
| N08-2 | 每条不变量至少有一个执行点 | 148 |
| N08-3 | 每条不变量至少有一个用例 | 150 |
| N08-4 | 每张有不变量对照的 RULE 至少有一个用例 | 37 |
| N09-1 | 每个有效对象都有流程承载（同 anchoring_check C2；OBJD 定论的从属、参照、视图按定论排除） | 1 |
| N09-2 | 每个有效一级业务对象都有聚合（聚合「对应」列；从属、参照、视图不要求） | 2 |
| N09-3 | 每个聚合对应的对象都出现在某个阶段的门或段内状态中 | 8 |
| N09-4 | 依附链必填：非一级的有效对象「父对象」列填有效 OBJ 编号，一级对象填「—」 | 1 |
| N09-M1 | 每个聚合都归属一个 BC | 10 |
| N09-M2 | 每个聚合都有不变量 | 36 |
| N09-M3 | 每个聚合至少一张表（ADR-0059 建表规则，不要求每个对象一张） | 132 |
| N09-M4 | 每张表都能追到聚合，聚合能追到业务对象 | 72 |
| N09-M5 | 数据访问登记中 AGGREGATE_ROOT、AGGREGATE_CHILD、BUSINESS_DOCUMENT 表能追到聚合（其他 role 只计数） | 12 |
| N09-M6 | 每个已建聚合在 ssot/i03-aggregates/mapping.yaml 对上规划聚合或写明无对应（pending 计缺口） | 1 |
| N10-M2 | 每个后端模块在 module-dependencies.txt 登记 | 0 |
| N10-M3 | 每个 API 路径能追到 BC（同路径的全部方法均须有明确且一致的归属） | 0 |
| N10-1 | 每个受控业务执行（命令）都有 owner BC | 42 |
| N10-M1 | 每个 BC 都归属一个子域（模块命名空间可在 subdomains.yaml 找到） | 4 |
| N10-2 | 每个 CMD 登记的命令有 owner BC（ssot/11-commands，只取 owner_bc；N10-1 查骨架中的受控执行） | 3 |
| N10-3 | 每条状态转换有触发命令（含 pending；by_command 为空计缺口） | 47 |
| N10-4 | 每个已建命令类在 ssot/11-commands/command-mapping.yaml 登记对照（pending 计缺口） | 0 |
| N10-5 | 每个事件有唯一发布者与版本 | 0 |
| N10-6 | 跨 BC 只走已发布事件或契约（订阅模块对发布模块的依赖须登记；只按 kernel DomainEvent 信封接收的订阅方除外） | 0 |
| N11-1 | 每个一级业务对象都声明了产生的业务事实（信息图「产生的事实」列） | 12 |
| N11-2 | 每条经营事实有产生它的命令或事件 | 0 |
| N11-3 | 每条分析事实能追到经营事实 | 7 |
| N12-1 | 每个维度至少切一条事实 | 0 |
| N12-2 | 总线矩阵的每个 ✔ 格都对上 DIM×FACT | 7 |
| N13-1 | 每个阶段都有指标 | 61 |
| N13-2 | 每个指标都能追到阶段或流程 | 110 |
| N13-3 | 度量链：每个指标都有计算绑定（绑定带事实来源） | 99 |
| N14-1 | 每个绑定有事实、公式版本与时间基准 | 0 |

## N01-1 每个干系人都登记了关注点（02 CON 未登记时全部计为缺口）（39）

- SH-C1 开发商
- SH-C2 自用业主
- SH-C3 既有建筑业主
- SH-C4 发包方
- SH-C5 资本方
- SH-C6a 客户决策链·发起人
- SH-C6b 客户决策链·决策人
- SH-C6c 客户决策链·经济买方
- SH-C6d 客户决策链·付款方
- SH-C6e 客户决策链·使用者
- SH-P1 交付伙伴
- SH-P1c 交付伙伴
- SH-P2 供应商
- SH-P3 渠道伙伴
- SH-P4 审计机构
- SH-P5 金融机构
- SH-P6 供应商·出租方
- SH-R1 工程审批
- SH-R2 企业监管
- SH-H1 求职者和候选人
- SH-S1 最终用户
- SH-S2 市民
- SH-M1 管理层
- SH-E1 用人经理
- SH-E2 项目经理
- SH-E3 设计师和顾问
- SH-E4 技术专家
- SH-E5 市场和客户经理
- SH-E6 职能人员
- SH-E7 注册执业人员
- SH-O1 —
- SH-X1 主体建筑设计院（不作发包方时）
- SH-X2 施工总包和专业分包
- SH-X3 监理
- SH-X4 造价咨询和招标代理
- SH-X5 保险技术检查机构（TIS）
- SH-X6 持证检测机构
- SH-X7 资金监管主办银行
- SH-X8 物业服务企业（未代业主委托时）

## N06-2 每条价值流的门对象中，至少一个顺依附链（信息图「父对象」列）追到一级业务对象（1）

- VS-10 获得期间报告（门对象 OBJ-125→断、OBJ-97→断、OBJ-98→断）

## N06-3 每个阶段的门对象中，至少一个顺依附链追到一级业务对象（无门对象的阶段也计缺口）（7）

- VS-04.4 回收款项（门对象 OBJ-85→断、OBJ-86→断）
- VS-05.5 完成付款（门对象 OBJ-85→断、OBJ-86→断）
- VS-06.1 完成申报缴款（门对象 OBJ-92→断）
- VS-08.1 完成借款发放与归还（门对象 OBJ-85→断）
- VS-10.0 执行记账结转（门对象 OBJ-97→断、OBJ-98→断）
- VS-10.1 关闭会计期间（门对象 OBJ-97→断、OBJ-98→断）
- VS-10.2 发布报表（门对象 OBJ-125→断、OBJ-97→断、OBJ-98→断）

## N01-R1 权责表引用的每个干系人编号都归入 ssot/07-org-roles/roles.yaml 的某个 ROLE（系统执行者与模块除外；roles.yaml 未建时不查）（1）

- 财务负责人（权责表引用，未归任何 ROLE）

## N01-R3 每个权限码至少一个系统角色持有（迁移种子与 AdministratorMode 代码定义，只报告）（4）

- cost.timesheet:timesheet:submit
- cost.timesheet:timesheet:approve
- platform.numbering:voucher-numbering-rule:approve
- platform.numbering:legacy-number:read

## N08-2 每条不变量至少有一个执行点（148）

- INV-01
- INV-02
- INV-03a
- INV-03b
- INV-03c
- INV-04a
- INV-04b
- INV-05
- INV-06
- INV-07
- INV-08
- INV-09
- INV-10
- INV-11
- INV-12
- INV-13
- INV-14
- INV-15
- INV-16a
- INV-16b
- INV-17
- INV-18
- INV-19
- INV-20
- INV-21
- INV-22
- INV-23
- INV-24
- INV-25
- INV-26
- INV-27
- INV-28
- INV-29
- INV-30
- INV-31
- INV-32
- INV-33
- INV-34
- INV-34a
- INV-34b
- INV-35
- INV-36
- INV-36a
- INV-37
- INV-39
- INV-39a
- INV-39b
- INV-40
- INV-41
- INV-42
- INV-43
- INV-44
- INV-46
- INV-47
- INV-48
- INV-49
- INV-50
- INV-51
- INV-52
- INV-53
- INV-54
- INV-55
- INV-56
- INV-57
- INV-58
- INV-59
- INV-60
- INV-63
- INV-65
- INV-66
- INV-67
- INV-68
- INV-69
- INV-70
- INV-105
- INV-71
- INV-72
- INV-73
- INV-74
- INV-75
- INV-75a
- INV-76
- INV-77
- INV-77a
- INV-78
- INV-78a
- INV-79
- INV-80
- INV-81
- INV-82
- INV-83
- INV-84
- INV-85
- INV-86
- INV-88
- INV-89
- INV-90
- INV-91
- INV-91a
- INV-92
- INV-93
- INV-94
- INV-95
- INV-95a
- INV-96
- INV-97
- INV-98
- INV-99
- INV-100
- INV-101
- INV-103
- INV-104
- INV-106
- INV-107
- INV-108
- INV-109
- INV-110
- INV-111
- INV-112
- INV-113
- INV-114
- INV-115
- INV-116
- INV-117
- INV-118
- INV-119
- INV-120
- INV-121
- INV-123
- INV-124
- INV-125
- INV-126
- INV-127
- INV-128
- INV-129
- INV-130
- INV-131
- INV-132
- INV-133
- INV-134
- INV-135
- INV-136
- INV-137
- INV-138
- INV-139
- INV-140
- INV-141
- INV-142

## N08-3 每条不变量至少有一个用例（150）

- INV-01
- INV-02
- INV-03a
- INV-03b
- INV-03c
- INV-04a
- INV-04b
- INV-05
- INV-06
- INV-07
- INV-08
- INV-09
- INV-10
- INV-11
- INV-12
- INV-13
- INV-14
- INV-15
- INV-16a
- INV-16b
- INV-17
- INV-18
- INV-19
- INV-20
- INV-21
- INV-22
- INV-23
- INV-24
- INV-25
- INV-26
- INV-27
- INV-28
- INV-29
- INV-30
- INV-31
- INV-32
- INV-33
- INV-34
- INV-34a
- INV-34b
- INV-35
- INV-36
- INV-36a
- INV-37
- INV-39
- INV-39a
- INV-39b
- INV-40
- INV-41
- INV-42
- INV-43
- INV-44
- INV-46
- INV-47
- INV-48
- INV-49
- INV-50
- INV-51
- INV-52
- INV-53
- INV-54
- INV-55
- INV-56
- INV-57
- INV-58
- INV-59
- INV-60
- INV-62
- INV-63
- INV-64
- INV-65
- INV-66
- INV-67
- INV-68
- INV-69
- INV-70
- INV-105
- INV-71
- INV-72
- INV-73
- INV-74
- INV-75
- INV-75a
- INV-76
- INV-77
- INV-77a
- INV-78
- INV-78a
- INV-79
- INV-80
- INV-81
- INV-82
- INV-83
- INV-84
- INV-85
- INV-86
- INV-88
- INV-89
- INV-90
- INV-91
- INV-91a
- INV-92
- INV-93
- INV-94
- INV-95
- INV-95a
- INV-96
- INV-97
- INV-98
- INV-99
- INV-100
- INV-101
- INV-103
- INV-104
- INV-106
- INV-107
- INV-108
- INV-109
- INV-110
- INV-111
- INV-112
- INV-113
- INV-114
- INV-115
- INV-116
- INV-117
- INV-118
- INV-119
- INV-120
- INV-121
- INV-123
- INV-124
- INV-125
- INV-126
- INV-127
- INV-128
- INV-129
- INV-130
- INV-131
- INV-132
- INV-133
- INV-134
- INV-135
- INV-136
- INV-137
- INV-138
- INV-139
- INV-140
- INV-141
- INV-142

## N08-4 每张有不变量对照的 RULE 至少有一个用例（37）

- DATA-RULE-01
- DATA-RULE-03
- DATA-RULE-07
- RULE-021
- RULE-024
- RULE-025
- RULE-030
- RULE-031
- RULE-032
- RULE-037
- RULE-063
- RULE-070
- RULE-072
- RULE-091
- RULE-092
- RULE-099
- RULE-126
- RULE-134
- RULE-168
- RULE-184
- RULE-185
- RULE-205
- RULE-213
- RULE-219
- RULE-398
- RULE-399
- RULE-400
- RULE-402
- RULE-403
- RULE-404
- RULE-405
- RULE-407
- RULE-408
- RULE-409
- RULE-410
- RULE-411
- RULE-413

## N09-1 每个有效对象都有流程承载（同 anchoring_check C2；OBJD 定论的从属、参照、视图按定论排除）（1）

- OBJ-126 股东（无任何流程读写或以它为主、次对象）

## N09-2 每个有效一级业务对象都有聚合（聚合「对应」列；从属、参照、视图不要求）（2）

- OBJ-126 股东
- OBJ-19 招标项目

## N09-3 每个聚合对应的对象都出现在某个阶段的门或段内状态中（8）

- AGG-01 → OBJ-03 联系人
- AGG-12 → OBJ-22 报价估算分解
- AGG-13 → OBJ-06 合同范围条目
- AGG-39 → OBJ-66 工时单
- AGG-47 → OBJ-81 收入确认
- AGG-69 → OBJ-70 考勤记录
- AGG-83 → OBJ-96 账簿
- AGG-88 → OBJ-90 银行流水

## N09-4 依附链必填：非一级的有效对象「父对象」列填有效 OBJ 编号，一级对象填「—」（1）

- OBJ-103 财务（非一级，父对象未填 OBJ 编号：—）

## N09-M1 每个聚合都归属一个 BC（10）

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

## N09-M2 每个聚合都有不变量（36）

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
- organization.attendance/WorkCalendarYear WorkCalendarYear
- organization.workforce/LegalEntity LegalEntity
- organization.workforce/RegisteredEmployee RegisteredEmployee
- platform.identity/AuthorizationRequest AuthorizationRequest
- platform.integration/SourceReference SourceReference
- platform.integration/SourceReferenceRefusal SourceReferenceRefusal
- platform.numbering/NumberingRule NumberingRule

## N09-M3 每个聚合至少一张表（ADR-0059 建表规则，不要求每个对象一张）（132）

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
- organization.workforce/RegisteredEmployee RegisteredEmployee
- platform.integration/SourceReferenceRefusal SourceReferenceRefusal

## N09-M4 每张表都能追到聚合，聚合能追到业务对象（72）

- cost_timesheet.consumed_events（对象 无）
- cost_timesheet.timesheets（对象 无）
- organization_administration.config_sets（对象 无）
- organization_administration.config_set_versions（对象 无）
- organization_administration.employee_activity_type_items（对象 无）
- organization_administration.state_transitions（对象 无）
- organization_attendance.work_calendar_years（对象 无）
- organization_attendance.work_calendar_days（对象 无）
- organization_attendance.work_calendars（对象 无）
- organization_attendance.work_calendar_versions（对象 无）
- organization_attendance.work_calendar_version_days（对象 无）
- organization_workforce.positions（对象 无）
- organization_workforce.org_units（对象 无）
- organization_workforce.employees（对象 无）
- organization_workforce.assignments（对象 无）
- organization_workforce.org_unit_versions（对象 无）
- organization_workforce.assignment_versions（对象 无）
- organization_workforce.sync_runs（对象 无）
- organization_workforce.sync_diffs（对象 无）
- organization_workforce.sync_run_held_separations（对象 无）
- organization_workforce.legal_entities（对象 无）
- organization_workforce.legal_entity_versions（对象 无）
- organization_workforce.legal_entity_attributions（对象 无）
- organization_workforce.org_unit_legal_entities（对象 无）
- organization_workforce.employee_legal_entities（对象 无）
- organization_workforce.legal_entity_attribution_corrections（对象 无）
- platform_approval.approval_requests（对象 无）
- platform_approval.approval_task_records（对象 无）
- platform_approval.start_rounds（对象 无）
- platform_approval.approval_tasks（对象 无）
- platform_approval.process_archive_skips（对象 无）
- platform_approval.act_*（对象 无）
- platform_audit.audit_entries（对象 无）
- platform_audit.audit_archive_batches（对象 无）
- platform_idempotency.idempotent_responses（对象 无）
- platform_identity.user_accounts（对象 无）
- platform_identity.roles（对象 无）
- platform_identity.role_permissions（对象 无）
- platform_identity.account_roles（对象 无）
- platform_identity.role_versions（对象 无）
- platform_identity.account_role_grants（对象 无）
- platform_identity.navigation_items（对象 无）
- platform_identity.login_states（对象 无）
- platform_identity.sso_ticket_uses（对象 无）
- platform_identity.browser_identifiers（对象 无）
- platform_identity.ticket_issuers（对象 无）
- platform_identity.emergency_admins（对象 无）
- platform_identity.authorization_requests（对象 无）
- platform_identity.sessions（对象 无）
- platform_identity.sessions_attributes（对象 无）
- platform_integration.event_publication（对象 无）
- platform_integration.event_publication_archive（对象 无）
- platform_integration.outbound_deliveries（对象 无）
- platform_integration.source_references（对象 无）
- platform_numbering.document_types（对象 无）
- platform_numbering.numbering_rules（对象 无）
- platform_numbering.numbering_rule_versions（对象 无）
- platform_numbering.numbering_counters（对象 无）
- platform_numbering.legacy_numbers（对象 无）
- platform_file.stored_files（对象 无）
- platform_file.stored_file_names（对象 无）
- platform_file.file_bindings（对象 无）
- platform_file.file_holds（对象 无）
- platform_file.file_preservations（对象 无）
- platform_file.preservation_categories（对象 无）
- platform_file.preservation_category_versions（对象 无）
- platform_file.false_positive_judgments（对象 无）
- platform_file.scan_exceptions（对象 无）
- platform_file.scan_exception_revocations（对象 无）
- platform_file.quarantine_read_requests（对象 无）
- platform_file.state_transitions（对象 无）
- platform_scheduling.scheduler_locks（对象 无）

## N09-M5 数据访问登记中 AGGREGATE_ROOT、AGGREGATE_CHILD、BUSINESS_DOCUMENT 表能追到聚合（其他 role 只计数）（12）

- organization_administration.config_set_versions（AGGREGATE_CHILD，聚合登记 ConfigSet）
- organization_administration.employee_activity_type_items（AGGREGATE_CHILD，聚合登记 ConfigSet）
- organization_attendance.work_calendar_versions（AGGREGATE_CHILD，聚合登记 WorkCalendar）
- organization_attendance.work_calendar_version_days（AGGREGATE_CHILD，聚合登记 WorkCalendar）
- platform_file.stored_files（AGGREGATE_ROOT，聚合登记 StoredFile）
- platform_file.stored_file_names（AGGREGATE_CHILD，聚合登记 StoredFile）
- platform_file.file_bindings（AGGREGATE_CHILD，聚合登记 StoredFile）
- platform_file.file_holds（AGGREGATE_CHILD，聚合登记 StoredFile）
- platform_file.file_preservations（AGGREGATE_CHILD，聚合登记 StoredFile）
- platform_file.preservation_category_versions（AGGREGATE_CHILD，聚合登记 PreservationCategory）
- platform_file.false_positive_judgments（AGGREGATE_ROOT，聚合登记 FalsePositiveJudgment）
- platform_file.quarantine_read_requests（AGGREGATE_ROOT，聚合登记 QuarantineReadRequest）

## N09-M6 每个已建聚合在 ssot/i03-aggregates/mapping.yaml 对上规划聚合或写明无对应（pending 计缺口）（1）

- organization.workforce/RegisteredEmployee（pending）

## N10-1 每个受控业务执行（命令）都有 owner BC（42）

- AGG-109/草拟利润分配（聚合 AGG-109 未归 BC）
- AGG-109/决议利润分配（聚合 AGG-109 未归 BC）
- AGG-109/登记利润分配支付（聚合 AGG-109 未归 BC）
- AGG-109/提交过账候选（聚合 AGG-109 未归 BC）
- AGG-109/撤销利润分配决议（聚合 AGG-109 未归 BC）
- AGG-73/计算月度薪酬（聚合 AGG-73 未归 BC）
- AGG-73/提交薪酬审核（聚合 AGG-73 未归 BC）
- AGG-73/批准月度薪酬（聚合 AGG-73 未归 BC）
- AGG-73/退回薪酬重算（聚合 AGG-73 未归 BC）
- AGG-73/撤回薪酬审核（聚合 AGG-73 未归 BC）
- AGG-73/提交过账候选（聚合 AGG-73 未归 BC）
- AGG-73/标记薪酬已发放（聚合 AGG-73 未归 BC）
- AGG-73/登记代发结果（聚合 AGG-73 未归 BC）
- AGG-73/发起薪酬补发（聚合 AGG-73 未归 BC）
- AGG-73/登记薪酬补发顺延（聚合 AGG-73 未归 BC）
- AGG-73/标记薪酬已过账（聚合 AGG-73 未归 BC）
- AGG-74/参保增员（聚合 AGG-74 未归 BC）
- AGG-74/参保减员（聚合 AGG-74 未归 BC）
- AGG-74/变更参保缴纳主体（聚合 AGG-74 未归 BC）
- AGG-74/调整缴费基数（聚合 AGG-74 未归 BC）
- AGG-75/编制激励方案版本（聚合 AGG-75 未归 BC）
- AGG-75/生效激励方案版本（聚合 AGG-75 未归 BC）
- AGG-75/驳回激励方案版本（聚合 AGG-75 未归 BC）
- AGG-75/撤回激励方案版本（聚合 AGG-75 未归 BC）
- AGG-76/草拟奖金核定（聚合 AGG-76 未归 BC）
- AGG-76/核定奖金（聚合 AGG-76 未归 BC）
- AGG-76/驳回奖金核定（聚合 AGG-76 未归 BC）
- AGG-93/建立薪酬档案（聚合 AGG-93 未归 BC）
- AGG-93/调整薪酬档案（聚合 AGG-93 未归 BC）
- AGG-93/登记补休转加班工资（聚合 AGG-93 未归 BC）
- AGG-93/终止薪酬档案（聚合 AGG-93 未归 BC）
- AGG-95/并入扣缴申报（聚合 AGG-95 未归 BC）
- AGG-95/生成申报（聚合 AGG-95 未归 BC）
- AGG-95/提交申报（聚合 AGG-95 未归 BC）
- AGG-95/登记缴款（聚合 AGG-95 未归 BC）
- AGG-95/更正申报（聚合 AGG-95 未归 BC）
- AGG-95/提交过账候选（聚合 AGG-95 未归 BC）
- AGG-110/关联证据（聚合 AGG-110 未归 BC）
- AGG-110/解除证据关联（聚合 AGG-110 未归 BC）
- AGG-136/编制账期规则版本（聚合 AGG-136 未归 BC）
- AGG-136/生效账期规则版本（聚合 AGG-136 未归 BC）
- REF-01/登记招标项目（聚合 REF-01 未归 BC）

## N10-M1 每个 BC 都归属一个子域（模块命名空间可在 subdomains.yaml 找到）（4）

- B-01 往来单位（含外部工程登记）（模块 masterdata.counterparty）
- B-02 服务与定价目录（模块 masterdata.catalog）
- B-32 报价发出编排（暂名）（模块 None）
- reporting 经营分析（只读）（模块 reporting）

## N10-2 每个 CMD 登记的命令有 owner BC（ssot/11-commands，只取 owner_bc；N10-1 查骨架中的受控执行）（3）

- CMD-1afc1f3f 生效账期规则版本（AGG-136 未归 BC）
- CMD-c401c884 编制账期规则版本（AGG-136 未归 BC）
- CMD-c2d24f95 登记招标项目（REF-01 未归 BC）

## N10-3 每条状态转换有触发命令（含 pending；by_command 为空计缺口）（47）

- OTR-017（transitions，AGG-55，命令 None）
- OTR-019（transitions，AGG-55，命令 None）
- OTR-020（transitions，AGG-55，命令 None）
- OTR-021（transitions，AGG-55，命令 None）
- OTR-025（transitions，AGG-55，命令 None）
- OTR-026（transitions，AGG-55，命令 None）
- OTR-027（transitions，AGG-55，命令 None）
- OTR-087（transitions，AGG-56，命令 None）
- OTR-088（transitions，AGG-56，命令 None）
- OTR-414（transitions，AGG-40，命令 None）
- OTR-415（transitions，AGG-40，命令 None）
- OTR-416（transitions，AGG-40，命令 None）
- OTR-423（transitions，AGG-45，命令 None）
- OTR-511（transitions，AGG-53，命令 None）
- OTR-514（transitions，AGG-53，命令 None）
- OTR-515（transitions，AGG-53，命令 None）
- OTR-536（transitions，AGG-59，命令 None）
- OTR-537（transitions，AGG-59，命令 None）
- OTR-567（transitions，AGG-90，命令 None）
- OTR-584（transitions，AGG-91，命令 None）
- OTR-589（transitions，AGG-82，命令 None）
- OTR-590（transitions，AGG-82，命令 None）
- OTR-612（transitions，AGG-98，命令 None）
- OTR-613（transitions，AGG-98，命令 None）
- OTR-614（transitions，AGG-98，命令 None）
- OTR-619（transitions，AGG-86，命令 None）
- OTR-620（transitions，AGG-86，命令 None）
- OTR-690（transitions，AGG-109，命令 None）
- OTR-720（transitions，AGG-115，命令 None）
- OTR-725（transitions，AGG-115，命令 None）
- OTR-785（transitions，AGG-78，命令 None）
- OTR-786（transitions，AGG-78，命令 None）
- OTR-789（transitions，AGG-123，命令 None）
- OTR-790（transitions，AGG-123，命令 None）
- OTR-793（transitions，AGG-124，命令 None）
- OTR-794（transitions，AGG-124，命令 None）
- OTR-797（transitions，AGG-133，命令 None）
- OTR-798（transitions，AGG-133，命令 None）
- OTR-807（transitions，AGG-101，命令 None）
- OTR-827（transitions，AGG-85，命令 None）
- OTR-828（transitions，AGG-85，命令 None）
- OTR-831（transitions，AGG-94，命令 None）
- OTR-832（transitions，AGG-94，命令 None）
- OTR-839（transitions，AGG-128，命令 None）
- OTR-840（transitions，AGG-128，命令 None）
- OTR-843（transitions，AGG-129，命令 None）
- OTR-844（transitions，AGG-129，命令 None）

## N11-1 每个一级业务对象都声明了产生的业务事实（信息图「产生的事实」列）（12）

- OBJ-01 往来单位
- OBJ-110 知识条目
- OBJ-19 招标项目
- OBJ-24 服务产品
- OBJ-25 阶段类型
- OBJ-26 标准成本费率表
- OBJ-28 工作日历
- OBJ-46 建设工程
- OBJ-53 证据
- OBJ-54 法人主体
- OBJ-58 岗位
- OBJ-78 分摊规则（原「分摊规则版本」）

## N11-3 每条分析事实能追到经营事实（7）

- FACT-30 商机（累积快照）（analytic_source：presales.opportunity 商机状态里程碑；非过账事实，无经营事实）
- FACT-31 报价（analytic_source：报价版本（presales.quoting 读模型）；非过账事实）
- FACT-32 合同与合同条款（累积快照）（analytic_source：contract.agreement 合同基线版本（参照事实，不过账，OA_PR）
- FACT-33 考勤与休假（analytic_source：organization.leave / attendance 考勤记录、请假、）
- FACT-34 计划目标与预测（analytic_source：operations.planning 经营计划版本（计划值，不是已发生事实））
- FACT-36 审批时效（累积快照）（analytic_source：platform.approval 领域事件，不过账（ADR-0025）；工作日）
- FACT-37 线索与投标转化事件（analytic_source：presales.opportunity 线索、投标状态里程碑）

## N12-2 总线矩阵的每个 ✔ 格都对上 DIM×FACT（7）

- 行「预算执行」× 列「期间」未对上 DIM×FACT
- 行「预算执行」× 列「法人」未对上 DIM×FACT
- 行「预算执行」× 列「组织」未对上 DIM×FACT
- 行「预算执行」× 列「项目 / WBS」未对上 DIM×FACT
- 行「预算执行」× 列「人员」未对上 DIM×FACT
- 行「预算执行」× 列「成本类型」未对上 DIM×FACT
- 行「预算执行」× 列「供应商」未对上 DIM×FACT

## N13-1 每个阶段都有指标（61）

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

## N13-2 每个指标都能追到阶段或流程（110）

- KPI-01 OA 迁入模板停用率
- KPI-02 切换后 A8 凭证插件新增凭证行数
- KPI-03 权责事实遗留类别数
- KPI-04 经单一过账管道入账的权责事实占比
- KPI-05 月结对账等式差异数
- KPI-06 权责口径三大报表按月产出率
- KPI-07 与 U8C 收付账套对账差异数
- KPI-08 指标目录 OAT 声明完整率
- KPI-09 同一指标的并存算法数
- KPI-10 关账期间快照覆盖率
- KPI-11 口径差异清单逐期勾稽完成率
- KPI-12 项目贡献毛利可得覆盖率
- KPI-13 贡献毛利率
- KPI-14 老系统与 A8 数据通道下线数
- KPI-15 过渡镜像对账通过率
- KPI-16 安全不变量满足数
- KPI-17 商机管道金额
- KPI-18 赢单金额
- KPI-19 赢单率
- KPI-20 项目立项金额
- KPI-21 线索转化率
- KPI-22 新签合同额
- KPI-23 剩余履约义务
- KPI-24 待签合同金额
- KPI-25 开票金额（含税）
- KPI-26 回款（应收结清，全部方式）
- KPI-27 现金回款
- KPI-28 应收账款余额
- KPI-29 逾期应收（过了到期日）
- KPI-30 开票转化率
- KPI-31 回款率
- KPI-32 减值准备（余额、本期计提）
- KPI-33 收入冲减
- KPI-34 信用损失核销
- KPI-35 商业承兑汇票未兑付余额
- KPI-36 承诺开票超期金额
- KPI-37 直接人工
- KPI-38 直接费用
- KPI-39 净服务收入
- KPI-40 贡献毛利
- KPI-41 BU 贡献（层级）
- KPI-42 经营利润
- KPI-43 经营利润率
- KPI-44 费用（权责，按发生期间）
- KPI-45 费用支付（按支付日）
- KPI-46 法定报表数
- KPI-47 经营活动现金流量净额
- KPI-48 平均在册人数
- KPI-49 离职率（主动、被动）
- KPI-50 计划目标值
- KPI-51 目标达成率
- KPI-52 人均产出
- KPI-53 人工成本产出倍数
- KPI-54 预算执行
- KPI-55 可用额度
- KPI-56 预算消耗率
- KPI-57 交付质量指标族
- KPI-58 启动会完成率
- KPI-59 内部评审覆盖率
- KPI-60 经营偏差
- KPI-61 交付偏差
- KPI-62 后期服务工时
- KPI-63 质量损失
- KPI-64 返工率
- KPI-65 质量缺陷率
- KPI-66 返工工时
- KPI-67 产能变现
- KPI-68 闲置产能
- KPI-69 成本预算
- KPI-70 成本预算偏差
- KPI-71 履约进度
- KPI-72 无合同 WIP
- KPI-73 账龄
- KPI-74 坏账风险
- KPI-75 减值风险
- KPI-76 现金转化
- KPI-77 工时捕获率
- KPI-78 法定成本
- KPI-79 内部往来
- KPI-80 合同取得成本
- KPI-81 ETC
- KPI-82 完工估算
- KPI-83 计划价值
- KPI-84 挣值
- KPI-85 实际成本
- KPI-86 成本绩效指数
- KPI-87 进度绩效指数
- KPI-88 进度偏差
- KPI-89 挣值成本偏差
- KPI-92 转付成本
- KPI-93 目标偏差
- KPI-94 新签净额
- KPI-95 可计费利用率
- KPI-96 含商机工时的利用率
- KPI-97 按有效工时算的利用率
- KPI-98 账龄超 N 天应收
- KPI-99 无合同投入总规模
- KPI-100 超授权额度投入
- KPI-101 分包成本
- KPI-102 外包成本
- KPI-103 项目成本（归集）
- KPI-104 营业成本（结转）
- KPI-105 合同资产余额
- KPI-106 未开票在制
- KPI-107 工时结构（投入去向）
- KPI-108 工时结构（产出）
- KPI-109 应收周转天数
- KPI-110 权责发生制收入
- KPI-111 含税收入
- KPI-112 间接成本（按职能）

## N13-3 度量链：每个指标都有计算绑定（绑定带事实来源）（99）

- KPI-01 OA 迁入模板停用率（pending：出口条件，非经营度量，不绑定经营事实）
- KPI-02 切换后 A8 凭证插件新增凭证行数（pending：出口条件，非经营度量，不绑定经营事实）
- KPI-03 权责事实遗留类别数（pending：出口条件，非经营度量，不绑定经营事实）
- KPI-04 经单一过账管道入账的权责事实占比（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-05 月结对账等式差异数（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-06 权责口径三大报表按月产出率（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-07 与 U8C 收付账套对账差异数（pending：出口条件，非经营度量，不绑定经营事实）
- KPI-08 指标目录 OAT 声明完整率（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-09 同一指标的并存算法数（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-10 关账期间快照覆盖率（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-11 口径差异清单逐期勾稽完成率（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-12 项目贡献毛利可得覆盖率（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-13 贡献毛利率（pending：分子贡献毛利同 KPI-40，所需成本汇集事实 FACT-27 在 W5；分母已）
- KPI-14 老系统与 A8 数据通道下线数（pending：出口条件，非经营度量，不绑定经营事实）
- KPI-15 过渡镜像对账通过率（pending：出口条件，非经营度量，不绑定经营事实）
- KPI-16 安全不变量满足数（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-17 商机管道金额（pending：所需事实非 W1：FACT-30 W2）
- KPI-18 赢单金额（pending：所需事实非 W1：FACT-31 W2）
- KPI-19 赢单率（pending：所需事实非 W1：FACT-37 W2、FACT-31 W2）
- KPI-20 项目立项金额（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-21 线索转化率（pending：所需事实非 W1：FACT-37 W2）
- KPI-22 新签合同额（pending：所需事实非 W1：FACT-32 W3）
- KPI-23 剩余履约义务（pending：所需事实非 W1：FACT-29 W5、FACT-32 W3）
- KPI-24 待签合同金额（pending：所需事实非 W1：FACT-32 W3）
- KPI-26 回款（应收结清，全部方式）（pending：所需事实非 W1：FACT-28 W4）
- KPI-27 现金回款（pending：所需事实非 W1：FACT-28 W4）
- KPI-28 应收账款余额（pending：所需事实非 W1：FACT-29 W5）
- KPI-29 逾期应收（过了到期日）（pending：所需事实非 W1：FACT-29 W5）
- KPI-32 减值准备（余额、本期计提）（pending：所需事实非 W1：FACT-29 W5）
- KPI-33 收入冲减（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-34 信用损失核销（pending：公式无出处：基线 M-21 只给期间 D、加总 A，核销金额口径与「收入冲减」的）
- KPI-35 商业承兑汇票未兑付余额（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-36 承诺开票超期金额（pending：所需事实非 W1：FACT-34 W2）
- KPI-38 直接费用（pending：所需事实含 W5（成本台账分录 FACT-27））
- KPI-40 贡献毛利（pending：所需事实含 W5（成本台账分录 FACT-27）；公式 FD-41 已定，待 F）
- KPI-41 BU 贡献（层级）（pending：所需事实非 W1：FACT-38 W5）
- KPI-42 经营利润（pending：所需事实非 W1：FACT-38 W5、FACT-27 W5）
- KPI-43 经营利润率（pending：所需事实非 W1：FACT-38 W5、FACT-27 W5）
- KPI-46 法定报表数（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-47 经营活动现金流量净额（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-48 平均在册人数（pending：所需事实非 W1：FACT-29 W5）
- KPI-49 离职率（主动、被动）（pending：所需事实非 W1：FACT-29 W5）
- KPI-50 计划目标值（pending：所需事实非 W1：FACT-34 W2）
- KPI-51 目标达成率（pending：所需事实非 W1：FACT-34 W2）
- KPI-52 人均产出（pending：所需事实非 W1：FACT-29 W5）
- KPI-53 人工成本产出倍数（pending：公式出处有，但人工成本范围（是否含奖金、社保）未定（基线 M-45 待决），且所）
- KPI-54 预算执行（pending：所需事实非 W1：FACT-29 W5）
- KPI-55 可用额度（pending：所需事实非 W1：FACT-29 W5）
- KPI-56 预算消耗率（pending：所需事实非 W1：FACT-29 W5）
- KPI-57 交付质量指标族（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-58 启动会完成率（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-59 内部评审覆盖率（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-60 经营偏差（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-61 交付偏差（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-62 后期服务工时（pending：所需事实非 W1：FACT-26 W3）
- KPI-63 质量损失（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-64 返工率（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-65 质量缺陷率（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-66 返工工时（pending：所需事实非 W1：FACT-26 W3）
- KPI-67 产能变现（pending：所需事实非 W1：FACT-26 W3、FACT-33 W2）
- KPI-68 闲置产能（pending：所需事实非 W1：FACT-26 W3、FACT-33 W2）
- KPI-69 成本预算（pending：所需事实非 W1：FACT-29 W5）
- KPI-70 成本预算偏差（pending：所需事实非 W1：FACT-29 W5）
- KPI-71 履约进度（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-72 无合同 WIP（pending：所需事实非 W1：FACT-29 W5）
- KPI-73 账龄（pending：所需事实非 W1：FACT-29 W5）
- KPI-74 坏账风险（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-75 减值风险（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-76 现金转化（pending：无出处：基线与财务决策均无现金转化的公式）
- KPI-77 工时捕获率（pending：所需事实非 W1：FACT-26 W3）
- KPI-78 法定成本（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-79 内部往来（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-80 合同取得成本（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-81 ETC（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-82 完工估算（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-83 计划价值（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-84 挣值（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-85 实际成本（pending：所需事实非 W1：FACT-27 W5）
- KPI-86 成本绩效指数（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-87 进度绩效指数（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-88 进度偏差（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-89 挣值成本偏差（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-93 目标偏差（pending：所需事实非 W1：FACT-34 W2）
- KPI-95 可计费利用率（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-96 含商机工时的利用率（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-97 按有效工时算的利用率（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-98 账龄超 N 天应收（pending：所需事实 W5（期间快照 FACT-29）；起算日参数已答复 KPI-Q04，待）
- KPI-99 无合同投入总规模（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-100 超授权额度投入（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-101 分包成本（pending：无出处：词表仅有定义无公式；事实 FACT-18 在 W1 但分包与外包的分拆取）
- KPI-102 外包成本（pending：无出处：词表仅有定义无公式；事实 FACT-18 在 W1 但分包与外包的分拆取）
- KPI-103 项目成本（归集）（pending：所需事实 W5（成本台账分录 FACT-27）；归集 DC_BIL = DL +）
- KPI-104 营业成本（结转）（pending：所需事实 W5（成本台账分录 FACT-27）；结转口径随收入确认）
- KPI-105 合同资产余额（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-106 未开票在制（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-107 工时结构（投入去向）（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-108 工时结构（产出）（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
- KPI-109 应收周转天数（pending：所需事实 W5（期间快照 FACT-29）；公式 FD-39 已定，待事实就绪后）
- KPI-112 间接成本（按职能）（pending：无出处：事实未登记且公式在 ADR、r0 口径、财务决策与 KPI 页答复中均无）
