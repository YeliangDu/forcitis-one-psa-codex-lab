# 协议「已签署」「已生效」分立后的订阅方裁定

状态：已确认｜2026-10-22｜负责：one-architecture

依据：用户 GL5-01（intent `2026-10-22-061733`）把协议 AGG-13 的「已签署生效」分为「已签署」「已生效」；one-arch-revenue 已落状态链（`claude/one-arch-revenue-closeout` @803017d1），问 ContractSigned 其余订阅方按签署还是按生效。

## 裁定

| 订阅方 | 用途 | 改为订阅 | 改动方 |
|---|---|---|---|
| B-07 `cost.costing`（AGG-122 售前投入归属登记） | 售前投入转入项目成本 | 协议已生效 | one-arch-finance（已改，`claude/one-arch-finance-closeout` @1f6f913a；命令改名「登记协议已生效」@1a93ba71） |
| B-26 `delivery.inspection`（PRC-41 制定并生效检查计划） | 按受托协议制定周期检查计划 | 协议已生效 | one-arch-revenue（已改，@58af24a1） |
| PRC-16 申请并转正风险交付授权 | 合同签回后转正、并入协议 | 协议已生效 | one-arch-revenue（已改，@58af24a1） |

ContractSigned 保留，表示「已签署」这一事实，供协议自身（归档、借阅）与审计读取；不再作上述三处的触发。

## 理由

1. 附生效条件的合同在条件成就前不生效（民法典第 158 条，本地无原文，未核实），可能永远不生效；收入准则以合同成立且各方承诺履行为前提（企业会计准则第 14 号第五条）。
2. 三处都是对合同投入资源或结清风险：转入成本、启动周期检查、把先行投入并入协议。按签署触发，条件不成就时要冲回；按生效触发不用。
3. 无附条件的合同签署与生效同一事务完成（GL5-01 第 1 点），改订阅不带来延迟。
4. 经验判断：成熟产品以合同生效（或激活）而非签署作为项目、成本与履约计划的启动点。

## 其余待办（one-architecture，待 revenue、finance 两条分支进 main 后做）

- `ssot/10-objects/transitions.yaml` 中 AGG-13 的「已签署生效」各条重新登记。
- 价值流 VS-04.1 退出、VS-04.6 段内的「已签署生效」改为「已生效」。
- 流程目录 PRC-47、48、50、68、179 的 `stages_note`；PRC-41 的 `stages_note`「由 ContractSigned 触发」改为协议已生效。
- 流程目录 PRC-25 的 `stages_note`：「合同已签回（ContractSigned）= OBJ-05 已签署生效」改为「协议已生效 = OBJ-05 已生效」（one-arch-finance 1a93ba71 已把命令改名为「登记协议已生效」）。
- 聚合稿 AGG-13 补「确认生效条件满足」命令与「协议已生效」事件。
