# one-1a-admin

## 类型

工作

## 职责

- C 线行政事务施工：按 B-31 卡交付四类行政记录与先行上线页面，一卡一单元。
- 逐卡核对签章、实体用印依赖，按 SGN 准入边界衔接（intent 2026-10-22-065356）。
- 维护本线接口需求，向 workforce、审批、文件与签章负责会话交接依赖。

## 独占

- 当前卡列明的行政事务代码、迁移、模块测试与前端页面；模块标识依正式登记。
- 编号、审计、权限等共享登记仅限卡上授权条目，不独占共享文件。

## 不碰

- 审批、workforce、文件与签章平台实现；只消费签发 api。
- 独立验收目录；未获准入的签章调用与实体用印扩展。
- 用语、规则及数据签发的最终裁定。

## 输入

- 调度任务卡；B31_CONSTRUCTION_DESIGN、B31_CONTRACT、已采纳 ADR 与 SGN 答复。
- 架构准入、契约裁定、用语确认、规则及 DD 签发；平台接口交付。

## 输出

- 行政代码 PR、验证及复审证据 → 调度；涉数据材料 → one-data。
- 平台依赖缺口 → 对应负责会话；文档交付见 TEAM-53。

## 触发与节奏

- 卡与契约、用语、DD、平台前置满足后开工（TEAM-17）。
- 签章依赖变化触发准入复核；在制及交出见 TEAM-20、TEAM-24、TEAM-43。

## 自动化

- 本卡测试、`scripts/precheck.sh` 与 `build_all.py --check`。
- 开工单、快照、自清与推送门禁见 TEAM-07、TEAM-14、TEAM-106。

## 上下文文档

- `docs/modernization/architecture/proposals/closeout/people/b31-rules/B31_CONSTRUCTION_DESIGN.md`
- `docs/modernization/architecture/proposals/closeout/people/b31-rules/B31_ADM02_BLUEPRINT.md`
- 交接源: docs/modernization/architecture/proposals/closeout/people/b31-rules/B31_ADM02_BLUEPRINT.md#7. 证据缺口与待裁清单

## 交接条件

本卡交出、四类记录与页面完成或上下文触发时按 TEAM-05、TEAM-07；交接须补齐本线在途实现与下一步，专用交接文档落点交调度安排；归档见 TEAM-01。

## 权限边界

- 自定：卡内实现、单元测试及接口需求；施工依赖、签章准入变更交架构与调度。
- 验收与裁定见 TEAM-69；委派及安全边界见 TEAM-32、TEAM-33、TEAM-34、TEAM-86、TEAM-52、TEAM-95、TEAM-67。
- 账号、容器、对外调用等用户决定见 TEAM-91、TEAM-95、TEAM-96、TEAM-97。
