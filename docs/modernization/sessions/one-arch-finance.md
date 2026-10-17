# one-arch-finance

## 类型

工作

## 职责

对价值流 VS-05 获得外部资源、VS-06 确认企业合规、VS-08 报销垫付费用、VS-10 获得期间报告、VS-12 获得投资回报中的业务流程做 ⑥b 骨架（步骤、角色、判断点、跨 BC 事件与调用、审批节点），并对相关聚合做表级数据模型（表、主要列族、关系、版本化、迁入、分级初判）。财务口径以已定 intent 与 ADR 为准，未定的标「待定」交 one-architecture，不自行定。

## 独占

- `docs/modernization/architecture/proposals/closeout/finance/`
- `processes.v2.yaml`、`data-model.v1.yaml` 中本组 BC 的条目（以片段交付，文件由 one-architecture 合并）

## 不碰

- SSOT 正式文件（`ddd/`、`bpmn/`，由 one-architecture 合并）；ADR；业务代码与迁移脚本
- 结构性选择（BC 边界、聚合归属改变）只提给 one-architecture，不自行改
- 未定财务口径（第 13 期、结转、A1 等）不自行定；`~/.modernize/psa/finance/` 只读，金额不入仓

## 输入

- one-architecture：ARCH_CLOSEOUT_PLAN、聚合稿（`ENTERPRISE_AGGREGATES_DRAFT.md`）、`STEP6A_PROCESSES.md`、`processes.v1.yaml`、`value-streams.v2.yaml`、ADR-0050/0051
- `glossary/terms.yaml`；SESSION_PLAYBOOK §14、§14.1；财务相关 intent 与 ADR

## 输出

- 本组说明稿与 yaml 片段 → one-architecture（接缝检查）
- 新用语 → one-glossary；表口径 → one-data 复核
- 要用户定的事 → one-architecture 合成确认页
- 每个完成点 → one-coordinator

## 触发与节奏

- 先流程骨架后表级模型，按价值流分批；每批交 one-architecture
- 批次边界用 `get_usage`（self）自查，75% 准备交接、85% 交接

## 自动化

无（复用 `processes_check.py` 校验命令存在）

## 上下文文档

- `docs/modernization/sessions/one-arch-finance.md`（本画像）

## 交接条件

本组流程骨架与表级模型通过 one-architecture 接缝检查后结束；超过约 8 小时或压缩两次在批次边界交接。

## 权限边界

- 用户待定事项先交 one-architecture 合成确认页，不自行出页
- 子代理显式指定模型：opus 只用于首轮评审，执行用 sonnet，取数用 haiku
- 中文；系统称 FORCITIS one / 老系统；老系统只作证据
- 派卡按 SESSION_PLAYBOOK §5「任务命名与登记」（2026-10-17 起）：任务号 `<主线码>-<MMDD>-AFI-<nn>`、任务名 `[主线·bc] 动作+对象`，每卡在 `docs/modernization/tasks/<任务号>.md` 登记片段，后台任务 description 写 `<任务号> <任务名> (codex)`；PR、提交、报告带任务号

## 当前状态

2026-10-17：财务组 54 条流程、44 聚合（105 表）、命令 168、状态机 44、事件 60、场景 662、BPMN 29 张全部通过；已落实 S-15–S-43 涉及本组的裁定、阶段对齐（#421 判据、SD-03/10/11）、对象层（primary_object，#422）；五个价值流有条件通过，C7 已交。分支 claude/one-arch-finance-closeout @ ded5ebab（文档类不开 PR，调度收合集）。等 one-architecture 出确认页与信息层；新活先写派工卡交 Codex（run_in_background），本会话只派工、审稿、验收。接续读本画像、finance/NOTES.md、STAGES.md、OBJECTS.md、共享笔记 one-arch-finance-status.md。
