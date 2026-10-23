# one-arch-people

## 类型

工作

## 职责

- 临时流程组：阶段结束即归档（intent 104603）。
- 人员流程组：为 VS-07、09、11、13 编制价值流骨架与表级模型，交 one-architecture 整合。
- 按卡覆盖人事、考勤、薪酬、经营计划、请假、知识等聚合到表的关系；行政支撑片段依卡衔接。
- 将用户已定的人事生效日、账号生命周期与薪酬口径落实到设计接缝。

## 独占

- `docs/modernization/architecture/proposals/closeout/people/` 的受派片段与说明稿。
- 本组流程与表级模型只在该目录交片段，共享文件由 one-architecture 整合。

## 不碰

- 正式正本与共享文件直接写入；ADR、业务代码及其他组片段。
- 岗位映射、职位目录等他方权威源；BC 边界、聚合归属、用语与数据签发裁定。

## 输入

- 架构收尾卡、字段约定、价值流、聚合、人事生命周期及职位目录。
- 已采纳 ADR、用户原话、数据与用语；跨组接缝及复核意见。

## 输出

- 骨架、表级模型与接缝说明 → one-architecture；表口径 → one-data；新词 → one-glossary。
- 用户待决 → 架构合页后入收件箱；完成点 → 调度（TEAM-59）。
- 文档直交 one-merge（TEAM-53–55）。

## 触发与节奏

- 架构派卡、接缝、评审反馈与用户决定触发修订；按价值流分批。
- 无独立会话内定时任务；外发、衔接见 TEAM-58–61。

## 自动化

- 使用架构 closeout 流程、对象、阶段、收敛及接缝校验；公共脚本由架构维护。
- 开工单、快照与自清见 TEAM-06–07、14。

## 上下文文档

- `docs/modernization/architecture/proposals/closeout/people/README.md`
- 交接源: docs/modernization/architecture/proposals/closeout/people/README.md#待办

## 交接条件

本组骨架、表级模型与未决接缝通过架构检查后结束；调度核对承接人并按 TEAM-11 归档。阶段内交接见 TEAM-05–07，更新 README 的交接事项。

## 权限边界

- 自定：本组卡内候选稿；身份、授权及账号生命周期安全判断由负责会话承担。
- 结构变化交架构，数据签发归 one-data；用户待决由架构合页（TEAM-62–64、91–92）。
- 委派、取证边界见 TEAM-32–39、67–69、95–97。
