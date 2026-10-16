# 三组接缝检查报告（revenue / finance / people）

本文件由 `closeout/seam_check.py` 生成（只读检查，不改任何输入）。口径与不确定处见文末「脚本口径说明」。

## 一屏结论

骨架跨 BC 交互 577 条（其中 cross_bc 516、事件触发器 61、事件策略订阅 0）；§4.1 BC 边 153 条，已建边 21 条。

| 项 | 结论 | 条数 |
|---|---|---|
| 1 跨 BC 调用 | 通过 528；边缺失 4；方向反（上游调下游）0；边在但方式不符 7；审批回调边缺「事件」35 | 46 |
| 2 事件对接 | out 无对应 in 7；out/in 名称不一致 0；in 发布方未登记 2；in 名称近似 1；声明订阅无承接 5 | 15 |
| 3 跨组命令 | 跨组调用 9 个（组, 流程, 命令）；登记齐全 9；聚合无组目录 0；命令不在目录 0；调用方未登记 0；目录登记的调用方无步骤 1；状态机无转换 0（目录无调用方字段 0、聚合无状态机 0 另计，不计入）| 1 |
| 4 成环 | §4.1 自身环 0；加骨架边后环 0；骨架用到而 §4.1 没有的边 3 | 0 |
| 5 孤儿事件 | 聚合稿有业务订阅方的事件 53 条，骨架无承接 13 | 13 |
| 6 同名不同义候选 | 命令 0；事件 1 | 1 |


## 1 跨 BC 调用（骨架 cross_bc 与 §4.1 边）

分组键为「骨架所在组 → 对方 BC 的主归属组」。

#### 1a 方向反（上游调下游）（0 条）

无。

#### 1b §4.1 没有这条边（4 条）

**finance → finance**（2 条）
- finance PRC-74 触发器（B-11，trigger event/in「进项发票已用途确认」）需要边 B-11 → B-23（事件）；§4.1 边方式：无
- finance PRC-77 第4步（B-23，cross_bc api/out「读银行流水匹配（完税凭证金额与时刻）」）需要边 B-23 → B-22（api）；§4.1 边方式：无

**finance → 平台**（2 条）
- finance PRC-99 第4步（B-24，cross_bc api/out「提交审批」）需要边 B-24 → platform.approval（api）；§4.1 边方式：无
- finance PRC-99 第5步（B-24，cross_bc event/in「审批已通过」）需要边 B-24 → platform.approval（事件）；§4.1 边方式：无

#### 1c 边在但方式不符（不含审批回调）（7 条）

**finance → 平台**（1 条）
- finance PRC-64 触发器（B-22，trigger event/in「银行文件已到达」）需要边 B-22 → platform.integration（事件）；§4.1 边方式：api

**people → people**（6 条）
- people PRC-128 第7步（B-13，cross_bc event/out「Separated」）需要边 B-31 → B-13（事件）；§4.1 边方式：api
- people PRC-128 第7步（B-13，cross_bc event/out「Separated」）需要边 B-27 → B-13（事件）；§4.1 边方式：api
- people PRC-128 第10步（B-13，cross_bc event/out「〔缺〕离职登记已撤销」）需要边 B-31 → B-13（事件）；§4.1 边方式：api
- people PRC-156 第1步（B-31，cross_bc event/in「Separated」）需要边 B-31 → B-13（事件）；§4.1 边方式：api
- people PRC-156 触发器（B-31，trigger event/in「员工已离职」）需要边 B-31 → B-13（事件）；§4.1 边方式：api
- people PRC-160 触发器（B-27，trigger event/in「入职生效（年中入职）」）需要边 B-27 → B-13（事件）；§4.1 边方式：api

#### 1d 审批回调（platform.approval 事件 in）而 §4.1 该边未标「事件」，按 BC 汇总（35 条）

**BC B-02（边 B-02 → platform.approval，§4.1 方式：api）**（2 条）
- revenue PRC-13 第2步「审批已驳回」
- revenue PRC-13 第4步「审批已通过」

**BC B-04（边 B-04 → platform.approval，§4.1 方式：api）**（3 条）
- revenue PRC-19 第2步「审批已驳回」
- revenue PRC-19 第4步「审批已通过」
- revenue PRC-36 第6步「审批已通过」

**BC B-06（边 B-06 → platform.approval，§4.1 方式：api）**（4 条）
- revenue PRC-23 第4步「审批已通过」
- revenue PRC-23 第5步「审批已驳回」
- revenue PRC-23 第6步「审批已驳回」
- revenue PRC-23 第7步「审批已驳回」

**BC B-07（边 B-07 → platform.approval，§4.1 方式：api）**（3 条）
- finance PRC-96 第7步「审批已通过」
- finance PRC-97 第3步「审批已通过」
- finance PRC-98 第4步「审批已通过」

**BC B-10（边 B-10 → platform.approval，§4.1 方式：api）**（4 条）
- finance PRC-75 第4步「审批已驳回」
- finance PRC-75 第5步「审批已通过」
- finance PRC-175 第3步「审批已通过」
- finance PRC-175 第3步「审批已驳回」

**BC B-11（边 B-11 → platform.approval，§4.1 方式：api）**（6 条）
- finance PRC-70 第3步「审批已驳回」
- finance PRC-70 第5步「审批已通过」
- finance PRC-71 第3步「审批已通过」
- finance PRC-72 第7步「审批已通过」
- finance PRC-76 第3步「审批已通过」
- finance PRC-76 第5步「审批已通过」

**BC B-15（边 B-15 → platform.approval，§4.1 方式：api）**（4 条）
- people PRC-121 第3步「审批已通过」
- people PRC-122 第3步「审批已通过」
- people PRC-122 第4步「审批已驳回」
- people PRC-124 第4步「审批已通过」

**BC B-18（边 B-18 → platform.approval，§4.1 方式：api）**（3 条）
- finance PRC-87 第8步「审批已驳回」
- people PRC-88 第4步「审批已驳回」
- people PRC-88 第5步「审批已通过」

**BC B-20（边 B-20 → platform.approval，§4.1 方式：api）**（3 条）
- finance PRC-94 第1步「审批已驳回」
- finance PRC-94 第4步「审批已通过」
- finance PRC-95 第5步「审批已通过」

**BC B-27（边 B-27 → platform.approval，§4.1 方式：api）**（2 条）
- people PRC-116 第4步「审批已通过」
- people PRC-116 第6步「审批已驳回」

**BC B-29（边 B-29 → platform.approval，§4.1 方式：api）**（1 条）
- revenue PRC-36 第3步「审批已驳回」

## 2 事件对接

#### 2a out 事件在订阅方骨架无对应 in（7 条）

**people → people**（3 条）
- people PRC-85 第3步：B-13 发出「〔缺〕转正已办理」给 B-15，订阅方骨架没有同名 in
- people PRC-128 第7步：B-13 发出「Separated」给 B-27，订阅方骨架没有同名 in
- people PRC-128 第10步：B-13 发出「〔缺〕离职登记已撤销」给 B-31，订阅方骨架没有同名 in

**revenue → finance**（1 条）
- revenue PRC-47 第3步：B-05 发出「合同—项目关联已登记」给 B-07，订阅方骨架没有同名 in

**revenue → revenue**（3 条）
- revenue PRC-08 第9步：B-03 发出「投标未中标」给 B-09，订阅方骨架没有同名 in
- revenue PRC-08 第10步：B-03 发出「投标已废标」给 B-09，订阅方骨架没有同名 in
- revenue PRC-08 第11步：B-03 发出「投标已撤销」给 B-09，订阅方骨架没有同名 in

#### 2b out 与 in 名称不一致（两边原文）（0 条）

无。

#### 2c in 事件在发布方目录与聚合稿事件列都没有（2 条）

**revenue → revenue**（2 条）
- revenue PRC-153 第1步：B-09 订阅 B-03 的「投标保证金待退还」，发布方目录与聚合稿事件列均无此名
- revenue PRC-153 触发器：B-09 订阅 B-03 的「投标保证金待退还（PRC-08 发出的信号事件，拆分后与 PRC-08 以信号衔」，发布方目录与聚合稿事件列均无此名

#### 2d in 事件名称与发布方登记名近似（两边原文）（1 条）

**revenue → finance**（1 条）
- 订阅方 revenue PRC-86 第4步（B-09）原文「借款付款已成功」；发布方 B-10 登记的近似名「付款已成功」

#### 2e 事件表声明的订阅方在其骨架无承接（5 条）

**people 事件表 → people**（4 条）
- 「转正已办理」（发布 B-13）声明订阅方 B-15（B-15 复核转正后薪酬），该 BC 骨架无对应 in
- 「Separated」（发布 B-13）声明订阅方 B-27（B-27 离职当月考勤默认确认），该 BC 骨架无对应 in
- 「Separated」（发布 B-13）声明订阅方 B-27（B-27 离职结算补休余额），该 BC 骨架无对应 in
- 「离职登记已撤销」（发布 B-13）声明订阅方 B-31（B-31 取消归还待办），该 BC 骨架无对应 in

**revenue 事件表 → revenue**（1 条）
- 「项目已开始执行」（发布 B-04）声明订阅方 B-05（B-05（AGG-13 已签署生效 → 履行中，S-33）），该 BC 骨架无对应 in

## 3 跨组命令

#### 3a 调用的聚合不在任何组命令目录（0 条）

无。

#### 3b 命令不在所属组 commands.yaml（0 条）

无。

#### 3c 调用方未在命令的 callers / also_used_by / used_by 登记（0 条）

无。

#### 3d 所属组状态机无该命令的转换（0 条）

无。

#### 3e 目录登记了跨组调用方（callers / also_used_by），该流程骨架没有对应步骤（1 条）

**finance 目录 → revenue**（1 条）
- AGG-53「发起付款」登记的调用方 revenue PRC-86：该流程没有此步

## 4 成环

§4.1 自身（含已建边）环数：0。加入骨架实际用到的边后环：无。

#### 4a 骨架用到而 §4.1 与已建边都没有的边（3 条）

**finance**（3 条）
- B-11 → B-23（事件）：finance PRC-74 触发器「进项发票已用途确认」
- B-23 → B-22（api）：finance PRC-77 第4步「读银行流水匹配（完税凭证金额与时刻）」
- B-24 → platform.approval（api）：finance PRC-99 第4步「提交审批」

## 5 孤儿事件

#### 5a 聚合稿标了业务订阅方、骨架无承接（13 条）

**finance → finance**（1 条）
- AGG-101（B-20）「对账已完成」→ 订阅方 B-21，骨架无承接

**finance → people**（1 条）
- AGG-77（B-16）「`BusinessFactAttributed`」→ 订阅方 B-18，骨架无承接

**finance → revenue**（1 条）
- AGG-52（B-10）「应付已确认」→ 订阅方 B-09，骨架无承接

**people → finance**（1 条）
- AGG-117（B-13）「法人归属已变更」→ 订阅方 B-16，骨架无承接

**people → people**（1 条）
- AGG-108（B-14）「标准工时版本已生效」→ 订阅方 B-27，骨架无承接

**people → revenue**（1 条）
- AGG-108（B-14）「标准工时版本已生效」→ 订阅方 B-06，骨架无承接

**revenue → finance**（2 条）
- AGG-01（B-01）「银行账户已变更」→ 订阅方 B-10，骨架无承接
- AGG-13（B-05）「合同—项目关联已登记」→ 订阅方 B-07，骨架无承接

**revenue → revenue**（5 条）
- AGG-11（B-03）「投标未中标」→ 订阅方 B-09，骨架无承接
- AGG-11（B-03）「投标已撤销」→ 订阅方 B-09，骨架无承接
- AGG-11（B-03）「投标已废标」→ 订阅方 B-09，骨架无承接
- AGG-19（B-04）「项目已开始执行」→ 订阅方 B-05，骨架无承接
- AGG-21（B-04）「`MilestoneAccepted`」→ 订阅方 B-08，骨架无承接

## 6 同名不同义候选

#### 6a 同一聚合的命令名（0 条）

无。

#### 6b 同一发布 BC 的事件名（1 条）

**finance**（1 条）
- 发布 B-10：「付款已成功」事件表:finance[付款已成功]、目录:finance[付款已成功]、聚合稿[付款已成功]、骨架:revenue[付款已成功] ／「借款付款已成功」骨架:revenue[借款付款已成功]

## 脚本口径说明

- 边方式：api 调用 = 下游 → 上游；事件 = 订阅方 → 发布方。`direction: in` 的 api 视为对方调本 BC；对 `platform.*` 一律要求业务 BC → 平台（惯例），不检查平台调业务的反向。
- 已建边（module-dependencies.txt）不带方式，命中即通过。「方向反」指所需边的反向路径在 §4.1 + 已建边里可达（被调方依赖调用方）。
- 事件名按 terms.yaml 的领域事件中、英文名与别名归一后比较，并去掉〔缺〕与括号；近似判据：相互包含且长度差 ≤3，或 difflib 比值 ≥ 0.75。
- 事件触发器、事件策略订阅（events / subscribes 字段）也当作 in 承接；触发器未写发布方者，只在 2 项发布方核对中跳过。
- 流程的 owner_bc 为 `reporting` 的读模型（PRC-22、154、90 的事件订阅）是分析库订阅，不计入接缝。
- 第 3 项只取步骤的 aggregate / command 与 inner 子项；`calls`（流程调用流程）不展开；跨组 = 聚合不在调用方所在组的 commands.yaml 里。
- 「组」按命令目录与 owner_bc 的出现次数取 BC 的主归属组，只用于分组展示。

