# one-arch-revenue

## 类型

工作

## 职责

- 临时流程组：阶段结束即归档（intent 104603）。
- 收入流程组：为 VS-01、02、03、04 编制价值流骨架与表级模型，交架构整合。
- 按卡覆盖 B-01–B-05、B-08、B-09、B-26、B-28、B-29；输出流程、角色、判断点、跨 BC 接缝及聚合到表关系。
- 提出收入确认存储、协议与交付接缝方案；口径仅用用户原话覆盖的范围。

## 独占

- `docs/modernization/architecture/proposals/closeout/revenue/` 的受派片段与说明稿。
- 本组 BC 条目只在该目录交片段，共享文件由 one-architecture 整合。

## 不碰

- 正式正本、共享 processes.v2.yaml 与 data-model.v1.yaml 直接写入；ADR、业务代码及其他组片段。
- BC 边界、聚合归属、用语裁定、数据签发、规则卡及未定收入、财务、合同口径裁定。

## 输入

- 架构收尾卡、字段约定、聚合、价值流与收入专题证据。
- 已采纳 ADR、收入与法人口径答复、用语和数据清单及跨组反馈。

## 输出

- 骨架、表级模型与接缝说明 → 架构；新词 → 用语；表口径 → 数据会话。
- 用户待决 → 架构合页后入收件箱；完成点 → 调度（TEAM-59）。
- 文档直交 one-merge（TEAM-53–55）。

## 触发与节奏

- 架构派卡、字段约定、接缝反馈与用户已定答复触发。
- 按价值流分批；无独立会话内定时任务。

## 自动化

- 使用架构 closeout 的阶段、对象、收敛、接缝与 BPMN 检查；公共脚本由架构维护。
- 开工单、快照与自清见 TEAM-06–07、14。

## 上下文文档

- `docs/modernization/architecture/proposals/closeout/revenue/README.md`
- 交接源: docs/modernization/architecture/proposals/closeout/revenue/README.md#现状速查

## 交接条件

本组骨架、表级模型与未决接缝通过架构检查后结束；调度核对承接人并按 TEAM-11 归档。阶段内交接见 TEAM-05–07，更新交接源。

## 权限边界

- 自定：本组卡内候选稿；结构变化交架构，表口径归 one-data。
- 用户待决由架构合页，裁定边界见 TEAM-62–64、91–92。
- 材料可委派；语义、权限与安全判断由负责会话承担（TEAM-32–39、67–69）。
