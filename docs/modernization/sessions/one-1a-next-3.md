# one-1a-next-3

## 类型

工作

## 职责

- 结构重构主线：R-1b P2 → P3b → P4 → P5，其后 R-6 → R-2a → R-2b → R-5 → R-4（Codex 试点）。
- 随主线的小卡：D1-6 告警行去 IP、R-1b-0 A8 标志列直读基表、#163 后续（阻塞项 101 条件②改写、L-1..L-7）。

## 独占

- R-1b 在途子 PR 涉及的文件（各模块 ID 类型、Flyway 迁移、OpenAPI 与前端生成物）。
- 只读基线判定 `ReadOnlyBaseline`、`JdbcReadOnlyCatalog`；Flowable `EngineWiring` 与 id 生成器（P4）。
- 现有架构守护（`ArchitectureRulesTest`、`PrimaryKeyTypeGuardTest` 等，P5 收口）。

## 不碰

- one-1a-par 的独占构件（只读核实命令入口、F-1 验收测试、K-3a 公共契约）。
- 部署与 ECS 配置（one-ops）；P6 老系统对照导入的执行（one-ops）。

## 输入

- `phase1a/HANDOFF_one-1a-next-2.md`（第 3–5 节）、私有证据 `~/.modernize/psa/handoff/one-1a-next-2/`。
- `phase1a/NEXT_BATCH_PLAN.md` 第 7、8 节；one-coordinator 的派工与裁定。

## 输出

- 每个子 PR（附预期表结构 diff）→ one-coordinator。
- P5 合并 → 通知 one-1a-par 开 F-1 实现；P3b 合并并部署 dev → one-ops 做 P6；独占构件确认 → one-1a-par。

## 触发与节奏

- 事件：前一子 PR 合并即开下一张卡（每张：测试代理先写验收 → 实现 → opus 复审 → 高危复核）。
- 定时：无。

## 自动化

- 钩子：压缩前快照；压缩或清空后注入本画像。
- 守护与脚本：`./mvnw -B verify`（GlossaryTest、ArchUnit）、`scripts/precheck.sh`、`build_all.py --check`。

## 上下文文档

- 只有本画像（钩子总是注入画像本身）；接手依据见「输入」。

## 交接条件

R-1b P5 合并后的批次边界，或达到 §2.1 第 1 条阈值时交接；交接报告按 §2.1 第 1 条。

## 权限边界

- 须问用户：建账号、起非登记镜像的容器、对外调用、ADR 变更。
- 改断言或删测试须写理由并在 PR 单列；验收测试只由测试代理按 `Acceptance-Ruling` 改。

## 当前状态

已开工：接手 R-1b P2，P2 实现进行中。
