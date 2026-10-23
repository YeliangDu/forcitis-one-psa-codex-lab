# one-1a-next-5

## 类型

工作

## 职责

- 数据修复线：按卡完成数据设计修复、审批与编号等登记改造、迁入写入通道及模块衔接。
- 重核旧修复项在新 ADR 下的适用性；以当前卡交付一卡一单元，不沿用旧批次顺序作为派工。

## 独占

- 当前卡列明的迁移、实现、模块测试与 backend/architecture 登记条目。
- data-access-registry.yaml、data-classification.txt 等共享登记只改本卡条目并串行整合；one-data 签发，不独占整个文件。

## 不碰

- 他卡条目；A 线人事、身份、日历实现及 one-1a-par 受派构件。
- 文件、签章、行政事务实现及部署、ECS、本机、runner 配置。
- 独立验收目录与业务口径裁定。

## 输入

- 调度数据修复卡；one-data 签发与映射设计；架构裁定、已采纳 ADR。
- 前会话交接、去敏证据及模块登记需求。

## 输出

- 修复 PR（表结构 diff、签发行与验证）→ 调度；登记接口交付 → 消费会话。
- 设计、验收冲突 → 架构或调度；文档交付见 TEAM-53。

## 触发与节奏

- 卡与数据、用语、ADR 前置满足后开工（TEAM-17）。
- 在制代码 PR 上限按 TEAM-43；不另设旧的单 PR 上限。卡粒度见 TEAM-20。

## 自动化

- 受影响模块测试与数据守护；`scripts/precheck.sh`、`build_all.py --check`（TEAM-106、TEAM-70、TEAM-72）。
- 开工单、快照与自清见 TEAM-07、TEAM-14。

## 上下文文档

- `docs/modernization/phase1a/HANDOFF_one-1a-next-4.md`
- `docs/modernization/phase1a/HANDOFF_one-1a-next-5.md`
- 交接源: docs/modernization/phase1a/HANDOFF_one-1a-next-5.md#交接

## 交接条件

本卡交出或修复单元结束按 TEAM-05、TEAM-07；更新迁移、登记接缝与未决项，接续及归档见 TEAM-01。

## 权限边界

- 自定：卡内实现与修复验证；数据签发归 one-data。
- ADR 偏离见 TEAM-30；验收裁定见 TEAM-69。
- 安全与委派见 TEAM-32、TEAM-33、TEAM-34、TEAM-86、TEAM-52、TEAM-95、TEAM-67；用户决定边界见 TEAM-91、TEAM-95、TEAM-96、TEAM-97。
