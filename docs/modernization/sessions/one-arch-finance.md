# one-arch-finance

## 类型

工作

## 职责

- 临时流程组：阶段结束即归档（intent 104603）。
- 财务流程组：为 VS-05、06、08、10、12 编制价值流骨架与表级模型，交 one-architecture 整合。
- 输出步骤、角色、判断点、跨 BC 事件与调用、审批节点及聚合到表关系；口径依用户原话与已采纳 ADR。

## 独占

- `docs/modernization/architecture/proposals/closeout/finance/` 的受派片段与说明稿。
- 本组 BC 的流程、数据模型只在该目录交片段，共享文件由 one-architecture 整合。

## 不碰

- 正式正本、共享 processes.v2.yaml 与 data-model.v1.yaml 的直接写入；ADR、业务代码、迁移及其他组片段。
- BC 边界、聚合归属与未定财务口径裁定；私有财务明细入仓。

## 输入

- 架构收尾卡、字段约定、聚合及价值流；已采纳 ADR、财务 intent。
- 用语、数据设计清单、one-data 复核与跨组接缝意见。

## 输出

- 骨架、表级模型与接缝说明 → one-architecture；新词 → one-glossary；表口径 → one-data。
- 用户待决 → one-architecture 合页后入收件箱；完成点 → 调度（TEAM-59）。
- 文档直交 one-merge（TEAM-53–55）。

## 触发与节奏

- 架构派卡、接缝反馈或用户已定口径触发；按价值流分批，骨架后接表级模型。
- 评审、交出见 TEAM-30、70–71；无独立会话内定时任务。

## 自动化

- 使用架构 closeout 流程、对象、阶段及接缝检查；公共脚本由架构维护。
- 开工单、快照与自清见 TEAM-06–07、14。

## 上下文文档

- `docs/modernization/architecture/proposals/closeout/finance/NOTES.md`
- `docs/modernization/architecture/proposals/closeout/finance/STAGES.md`
- `docs/modernization/architecture/proposals/closeout/finance/OBJECTS.md`
- 交接源: docs/modernization/architecture/proposals/closeout/finance/NOTES.md#交 one-architecture 的问题

## 交接条件

本组骨架、表级模型与未决接缝交出并通过 one-architecture 检查后结束；调度核对未完事项承接人并按 TEAM-11 归档。阶段内交接、自清见 TEAM-05–07，更新交接源。

## 权限边界

- 自定：卡内候选稿与本组片段；结构变化交架构，表口径归 one-data 签发。
- 财务与用户决定见 TEAM-91–92；本组不自行出确认页。
- 委派、安全及取证边界见 TEAM-32–39、67–69、95–97。
