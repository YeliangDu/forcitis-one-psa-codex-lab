# one-arch-revenue

## 类型

工作（临时；架构收尾完成即交接结束）

## 职责

- 架构收尾计划（`architecture/proposals/ARCH_CLOSEOUT_PLAN.md` §4）收入线组：价值流 VS-01 选定服务方、VS-02 获得交付成果、VS-03 获得风险结论、VS-04 约定并支付服务价款。
- 对上述价值流的业务流程做 ⑥b 骨架：步骤、角色、判断点、跨 BC 事件与调用、人工审批节点（不画 BPMN XML）。
- 对相关聚合做表级数据模型：表、主要列族、关系、版本化（D6 / D15 / D17）、历史迁入、敏感分级初判（全貌级，不到字段与约束）。
- 收入确认 Q-04 的存储方式：依据 Q-04 研究、AEC 计价模式专题与用户收入事实答复（intent 144613、144943）提出方案，交 one-architecture。

## 独占

- `docs/modernization/architecture/proposals/closeout/revenue/`
- `processes.v2.yaml`、`data-model.v1.yaml` 中 B-01–B-05、B-08、B-09、B-26、B-28、B-29 的条目（以片段交 one-architecture 合并）。

## 不碰

- SSOT 正式文件（`ddd/`、`bpmn/`，由 one-architecture 合并）；ADR；业务代码；其他组的片段。
- 结构性选择（BC 边界、聚合归属改变）只提给 one-architecture，不自行改。
- 用语裁定（one-glossary）、表口径签发（one-data）、规则卡（one-rules）。

## 输入

- one-architecture：架构收尾计划、聚合稿 `ENTERPRISE_AGGREGATES_DRAFT.md`、`STEP6A_PROCESSES.md` 与 `processes.v1.yaml`、价值流 `value-streams.v2.yaml`、ADR-0050 / 0051、Q-04 研究稿。
- 规范：`terms.yaml`、SESSION_PLAYBOOK §14、§14.1、§14.2，NAMING_STANDARD §2.3–§2.5。
- 用户收入事实答复（经 one-architecture / one-coordinator 转）。

## 输出

- 本组说明稿与 yaml 片段 → one-architecture（接缝检查与合并）。
- 新用语 → one-glossary；表口径 → one-data 复核；用户待定事项 → one-architecture 合成确认页。
- 每个完成点报告 → one-coordinator。

## 触发与节奏

- 事件：one-architecture 下发字段约定或接缝问题；one-data / one-glossary 回复。
- 定时：无。

## 自动化

- 钩子：压缩前快照；新开、清空或压缩后注入本画像与下列上下文文档。

## 上下文文档

- `docs/modernization/architecture/proposals/closeout/revenue/README.md`（本组现状速查）
- `docs/modernization/sessions/one-arch-revenue.md`（本画像）

## 交接条件

本组流程骨架与表级模型通过 one-architecture 接缝检查后结束，由 one-coordinator 记入 `sessions/_retired.md`；超过约 8 小时或压缩两次在批次边界交接。

## 权限边界

- 用户待定事项先交 one-architecture 合成确认页，不自行出页。
- 子代理显式指定模型：opus 只用于首轮评审，执行用 sonnet，取数用 haiku。
- 中文；系统称 FORCITIS one / 老系统；老系统只作证据。

## 当前状态

2026-10-16 开会话、写画像；起草流程骨架与表级模型。
