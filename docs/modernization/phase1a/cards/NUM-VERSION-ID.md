---
卡号: NUM-VERSION-ID
状态: 完成
---
# 派工卡 NUM-VERSION-ID：取号返回所用规则版本的 ID

## 1. 卡号与类别
- 卡号：NUM-VERSION-ID（TS-UNIQ 实现 PR 的前置，调度指派：one-coordinator 2026-10-16 派 one-1a-par）
- 类别：功能补做（应交付未做）。ADR-0033 第 1 项第 5 条要求使用方引用版本行的 UUIDv7，`platform.numbering` 的对外类型至今不带它
- 批次与整合分支：一个小 PR 直接合入 main；one-1a-next-4 的数据修复 PR-7（numbering）以本卡改动为基线 rebase

## 2. 依据
- ADR-0033 第 1 项第 5 条：使用方引用版本行的 UUIDv7 主键，列名 `numbering_rule_version_id`（需求出处与列定义见 `phase1a/cards/TS-UNIQ.md` §2.3 第 5 行、§3.1 第 3 条）
- 数据设计签发：one-data 2026-10-16 裁定「numbering 的 API 扩展先开小卡，是 TS-UNIQ 实现 PR 的前置」（`data/reviews/TS-UNIQ.md`）
- 现状（main 71195efe）：表 `platform_numbering.numbering_rule_versions` 已有主键 `numbering_rule_version_id uuid`；`JdbcNumberingRuleRepository.insert` 用 `ids.next()` 生成并写入，但不读回；`TemplateVersion`（domain）不带该 ID；`IssuedNumber(number, counterId)` 不带；`IssueDocumentNumber.next` 只用 `template.pattern()`、`startValue()`

## 3. 范围与卡上文件
- 不涉及 DDL：表与列已存在，基线不改（数据设计：涉数据——否，§1.1 五项均无新增表、列、状态、关系、迁入；只读出既有主键并随取号返回）
- 允许改动：
  - 新增 `platform/numbering/api/NumberingRuleVersionId.java`（`@ValueObject` 记录，包一个 `UUID`，非空；与 `NumberingRuleId`、`NumberingCounterId` 同写法）
  - `platform/numbering/api/IssuedNumber.java`：增第三个分量 `NumberingRuleVersionId ruleVersionId`，非空
  - `platform/numbering/domain/TemplateVersion.java`：增 `NumberingRuleVersionId id` 分量，非空；`NumberingRule` 里所有 `new TemplateVersion(...)` 保持该 ID 不变地传下去（改状态、确认时复制同一个 ID）
  - 新版本的 ID 在创建版本处生成并传入（应用层或聚合的创建入口按现有 `IdGenerator` 惯例），`JdbcNumberingRuleRepository.insert` 改为写入版本自带的 ID，不再自己 `ids.next()`；`versionsOf` 读出 `numbering_rule_version_id`
  - `IssueDocumentNumber.next` 把所取模板版本的 ID 放进返回的 `IssuedNumber`
  - 因签名变化而必须跟着改的测试构造点（只改构造参数，不改断言含义）：`TimesheetOwnerGateTest.java:150`、`ApprovalIntegrationSupport.java:210` 的 `new IssuedNumber(...)`；`NumberingPatternTest`、`NumberingRuleStateMachineTest`、`EventSequenceContinuityTest:147` 的 `new TemplateVersion(...)`
- 归属：本卡独占 `IssuedNumber`、`TemplateVersion` 的签名与 `NumberingRuleVersionId`
- 不碰：任何迁移文件与基线；`cost.timesheet`（TS-UNIQ 的实现 PR 才消费这个 ID）；`platform.numbering` 的 web 层与 OpenAPI（本卡不新增对外接口字段；若 OpenAPI 导出因类型变化有差异，停下报告）；验收测试目录；他卡文件

## 4. 验收测试（测试代理先写，§6.1 第 8 条）
- 测试代理：`code-modernization:test-engineer`（sonnet），先于实现提交，trailer `Acceptance-Test-Author: test-engineer`
- 路径：`backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/`
- 测试名：
  - `NV-01 取号返回的 ruleVersionId 等于所用已确认模板版本在库里的主键`（集成，Testcontainers 临时容器，沿用 `NumberingRulesIT` 的搭建）
  - `NV-02 规则新增版本后，新版本取号返回的是新版本的 ID，旧日期仍返回旧版本的 ID`（生效日分界）
  - `NV-03 版本被确认、被取代时 ID 不变`（同一版本行在状态流转前后 ID 相同）
  - `NV-04 同一版本的多次取号返回同一个 ID`
  - `NV-05 IssuedNumber 与 TemplateVersion 的 ID 为空时构造失败`（单元）
- 实现方认为验收测试有误：停下报告，由复审裁定

## 5. 执行者与模型
- 执行者：one-1a-par 派 sonnet 子代理实现；工作区：分支 `claude/one-1a-par-numbering-version`，基线 main 71195efe
- 复审：opus 首轮（high），sonnet 复核，最多两轮（SESSION_PLAYBOOK §11）

## 6. 禁止事项
- 不改卡外文件；不顺手重构、改名；不放宽、不删除断言；不改验收测试目录
- 不改迁移与基线；不让 `IssuedNumber`、`TemplateVersion` 的新分量可空
- 不联网、不起容器（Testcontainers 临时容器按 CLAUDE.md 第 9 条）、不建账号
- 不打印、不写入密钥

## 7. 预期表结构 diff
无（本卡不改 DDL）。守护：两个空库分别执行改动前、后的基线，`pg_dump --schema-only` 无差异。

## 8. 交付物与自查
- 交付物：`NumberingRuleVersionId`、`IssuedNumber` 与 `TemplateVersion` 新分量、仓储读写、`IssueDocumentNumber` 返回、测试构造点同步
- 自查：`scripts/precheck.sh` 输出贴进 PR；受影响模块 `platform.numbering`、`cost.timesheet`、`platform.approval` 测试；交付前 `scripts/ci/backend_verify.sh`（起容器前先问）
- 报告分开写「已验证」与「未运行」

## 9. 复审要求
- opus 核对：ID 在版本生命周期内不变、不会在 `update` 路径被重新生成；`insert` 写入的 ID 与返回的相同；`changedVersions()` 路径无遗漏；`IssuedNumber` 构造点无遗漏
- Blocker / High 合并前修完并复核；Medium / Low 登记进下一批

## 10. 报告去向
- 向 one-coordinator 报告，抄 one-data（TS-UNIQ 前置已满足）与 one-1a-next-4（PR-7 以本卡为基线）；PR／卡／交接记录（消息按 TEAM-53 四节点及需动作事件送达，不另报号）含改动文件、已验证与未运行、precheck 输出

## 11. 本卡新词（开工前术语门禁）
`NumberingRuleVersionId`（编号规则版本 ID，值对象，领域，`platform.numbering`）由已定词条 `numbering_rule_version`（第三轮用语确认 R3-NU-09，`backend/glossary/proposed/round3-pending-renames.yaml`）加 `id` 组成，列名 `numbering_rule_version_id` 已在基线；实现者开工前仍须用 `GlossaryTest` 确认类名通过，查不到则当场登记提议术语。
