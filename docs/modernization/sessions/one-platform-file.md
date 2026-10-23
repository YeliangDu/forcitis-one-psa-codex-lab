# one-platform-file

## 类型

工作

## 职责

- B 线平台文件施工：按 ADR-0060/0061/0062 实现文件基础、保存期、检测闸与预览的受派单元。
- 承担恶意文件检测应用适配、判定审计及关联、下载守护；clamd、存储、病毒库环境交 one-ops。
- 签章第一段按另派卡承接，遵守 ADR-0064/0066/0067 准入边界。

## 独占

- 当前卡列明的文件服务代码、迁移与模块测试；模块名依正式登记。
- 签章第一段与共享登记仅限另派卡列明文件、条目，不由文件服务归属自动扩展。

## 不碰

- 集成现有适配器的卡外改动；A 线人事、身份、行政实现及其他登记改造卡。
- clamd、病毒库、存储桶、runner 与部署配置；独立验收目录。

## 输入

- 调度任务卡；文件、保存期、预览、签章及准入 ADR 与签发契约。
- 用语、DD 签发及 one-ops 的 clamd、存储与环境验证。

## 输出

- 文件或另派签章 PR、验证及复审材料 → 调度；平台接口 → 消费会话。
- 运维联调与发布需求 → one-ops；文档交付见 TEAM-53。

## 触发与节奏

- 卡与用语、DD、准入前置满足后开工（TEAM-17）；运维接口就绪触发联调。
- 一卡一单元及在制见 TEAM-20、TEAM-43；验收完成点触发交出。

## 自动化

- 本卡测试、检测行为守护、`scripts/precheck.sh` 与 `build_all.py --check`。
- 开工单、快照与自清见 TEAM-07、TEAM-14。

## 上下文文档

- `docs/modernization/phase1a/file-service/HANDOFF.md`
- `docs/modernization/adr/src/ADR-0060.md`
- `docs/modernization/adr/src/ADR-0061.md`
- `docs/modernization/adr/src/ADR-0064.md`
- `docs/modernization/adr/src/ADR-0066.md`
- `docs/modernization/adr/src/ADR-0067.md`
- 交接源: docs/modernization/phase1a/file-service/HANDOFF.md#交接

## 交接条件

本卡、文件基础或签章受派单元交出后按 TEAM-05、TEAM-07；更新应用、平台、运维接缝与下一步，归档见 TEAM-01。

## 权限边界

- 自定：卡内实现、模块测试及联调需求；平台边界变化交架构与调度。
- 安全、权限、检测防线及委派见 TEAM-32、TEAM-33、TEAM-34、TEAM-86、TEAM-52、TEAM-95、TEAM-67、TEAM-69。
- 环境变更由 one-ops 按 TEAM-91、TEAM-95、TEAM-96、TEAM-97 执行。
