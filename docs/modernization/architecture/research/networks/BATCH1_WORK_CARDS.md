# 第一批派工卡：约束规则、执行点、验收用例，接入接口、表、模块（ARC-1021-COO-10 批次 1）

状态：草稿｜2026-10-21｜负责：one-architecture

依据：`proposals/DECISION_IMPACT_NETWORK_PROPOSAL.md`（用户 U1–U6 已定）；批次表 `research/networks/ADR_NODE_FREQUENCY.md`（one-coordinator 已批）。批次 1 选 C1、I08、I09 的理由：C1 被 61% 的已采纳 ADR 改过，I08 43% 且全缺；实现网络 v2 的硬不变式「每条约束 ≥1 执行点、≥1 用例」要这三类同时在。I05、I06、I07 已有登记，只接入连线。

分工按调度要求：能自动生成的交 Codex（gpt-6.1-sol）或 DeepSeek；规则理由、INV 与 RULE 对照这类语义判断由 one-architecture 写。交 one-coordinator 审后派。

## 0. 本批共同约定

- 稳定编号：INV（不变量，沿用 `ENTERPRISE_AGGREGATES_DRAFT.md` §1.2 的 150 条编号）、RULE（规则卡，沿用 `rules/`）、EP（执行点，新前缀）、测试不另立编号（以测试类与方法全名为键）。新前缀与 `ssot/` 目录布局按用户 U9 先交 one-glossary 审用语，再汇页交用户；未批前文件放 `architecture/ssot/` 下、`meta.status: proposal`。
- 生成的登记文件开头写「由 <脚本> 生成，勿手改」；手写部分与生成部分分文件。
- 每卡完成后报：条目数、自动生成条目数、自动化率、`panorama_build.py --check` 缺口数（用户 U2）。
- 基线：origin/main（本分支已于 2026-10-21 合入，d637bb37）。

## 卡 1　ARC-1021-ARC-01　约束规则登记（C1）：INV 为主键、RULE 挂接

- **类别**：功能补做（文档与登记）。**执行者**：one-architecture（语义判断）＋ Codex（机械转换，gpt-6.1-sol，reasoning medium）。
- **做什么**：
  1. one-architecture 定登记格式 `architecture/ssot/c1-rules/rules.yaml`：每条 INV 一项，字段 `id`、`statement`（一句）、`owner_aggregate`（AGG 或 `模块/类`）、`owner_bc`、`category`（不变量 A/B/C、前置检查、数据约束，沿 METHOD J5）、`rule_cards`（RULE 列表）、`exceptions`（审批 / 到期 / 补偿，可空）、`source`（出处文件与行）。
  2. Codex 把 `ENTERPRISE_AGGREGATES_DRAFT.md` §1.2 的 150 条 INV 表逐行转成上述 yaml（不改文字、不合并、不新增），附转换前后条数核对。
  3. one-architecture 逐条判定 INV 与 RULE 的对照（`rule_cards`）：测试引用的是 RULE 编号（69 个测试文件），INV 不对上 RULE，下游的执行点与用例就挂不上。拿不准的标 `mapping: pending` 并列清单。
- **允许改动**：`architecture/ssot/c1-rules/` 新建；`architecture/panorama/panorama_build.py` 读取段（读新文件）。
- **不碰**：`rules/*.md` 规则卡正文；`ENTERPRISE_AGGREGATES_DRAFT.md`（只读，转换后加一行「已迁至 ssot/c1-rules」由本会话做）。
- **验收**：yaml 条数 = 150；每条有 `owner_bc`；`rule_cards` 中的 RULE 都存在；`panorama_build.py --check` 不因本文件报错；抽 15 条与原表逐字比对一致（sonnet 抽查）。
- **禁止**：不改规则语义；不删条目；不联网。

## 卡 2　ARC-1021-ARC-02　执行点扫描（I08）

- **类别**：功能补做（生成器）。**执行者**：Codex（gpt-6.1-sol，reasoning high：要读 Java 与 SQL 两类来源）。对拍：DeepSeek（`pi -p --model deepseek/deepseek-flash`）对同一来源独立抽 20 个执行点比对。
- **做什么**：写 `architecture/panorama/scan_execution_points.py`，从代码与 DDL 扫出执行点，生成 `architecture/ssot/i08-execution-points/execution-points.yaml`：
  - 来源：`backend/src/test/java/**/architecture/**` 的 ArchUnit 与守护测试方法；Flyway 迁移中的具名 `CHECK`、触发器、唯一约束；`backend/architecture/*-exceptions.txt` 的每行（作为「豁免」执行点，指回被豁免的规则）；
  - 每项：`id`（EP- + 稳定哈希短码，按来源全名生成，重跑不变）、`kind`（授权 / 不变量 / 数据约束 / 守护）、`location`（文件与行）、`name`（约束名或测试方法名）、`enforces`（从名称、注释、`@DisplayName` 中出现的 INV- / RULE- 编号自动抽取，抽不到留空）。
- **允许改动**：上述脚本与生成文件；`architecture/panorama/panorama_build.py` 读取段。
- **不碰**：任何 Java、SQL、测试源码。
- **验收**：脚本重跑两次输出逐字节相同；`enforces` 非空率写进报告；人工抽 20 条（DeepSeek 对拍）位置与名称正确；破坏验证：删一个 CHECK 后重跑，对应 EP 消失且 `--check` 报出该规则少了执行点。
- **禁止**：不起容器、不连数据库（只读文件）；不联网（DeepSeek 对拍除外，按既有授权）。

## 卡 3　ARC-1021-ARC-03　验收用例抽取（I09）

- **类别**：功能补做（生成器）。**执行者**：Codex（gpt-6.1-sol，reasoning medium）。
- **做什么**：写 `architecture/panorama/scan_acceptance_tests.py`，从 `backend/src/test/java/**/acceptance/**` 与 `frontend/**/__acceptance__/**` 抽出每个测试方法，生成 `architecture/ssot/i09-acceptance/tests.yaml`：键为测试类与方法全名；字段 `display_name`、`covers`（`@DisplayName` 与方法名里出现的 RULE- / INV- / CMD- 编号）、`location`。
- **允许改动**：上述脚本与生成文件；`panorama_build.py` 读取段。
- **不碰**：测试源码（不补编号；缺编号的测试列清单交各模块会话）。
- **验收**：重跑一致；`covers` 非空率写进报告；列出「有 RULE 无测试」与「有测试无编号」两份清单。
- **禁止**：不改验收目录（CLAUDE.md 第 5 条）。

## 卡 4　ARC-1021-ARC-04　连线与不变式检查，接入 I05、I06、I07，底座合入 main

- **类别**：功能补做。**执行者**：one-architecture（不变式定义）＋ Codex（实现，gpt-6.1-sol，reasoning medium）。
- **做什么**：
  1. `panorama_build.py` 增节点类 C1、EP、TC，接入已有的 I05（`openapi.json`、`external-read-registry.yaml`）、I06（`data-access-registry.yaml`）、I07（`module-dependencies.txt`）；增连线：INV → EP（`enforces`）、INV → TC（经 RULE）、EP → TC（同规则）、INV → AGG、TBL → AGG、API → BC、MOD → BC。
  2. `--check` 增 v2 不变式：每条 INV ≥1 执行点、≥1 用例；每张表有聚合归属；每个模块在依赖清单登记；输出到 GAPS.md。
  3. 按用户 U8，底座（`panorama/` 与本批生成器、`ssot/` 新文件）随本批合入 main：由本会话开 PR，独占范围 `architecture/panorama/`、`architecture/ssot/c1-rules|i08-execution-points|i09-acceptance/`。
  4. 用户 U10：同一 PR 补 ADR `catalog.yaml` 缺的 0052–0070 共 19 条。
- **验收**：`panorama_build.py --check` 输出 C1→EP、C1→TC 的覆盖率与缺口清单；重跑一致；precheck 全过。
- **禁止**：不改 `backend/`、`frontend/` 源码。

## 本批交付后报告的数

| 层 | 条目数 | 自动生成 | 自动化率 | 缺口 |
|---|---|---|---|---|
| C1 | 150 | 0（转换为机械、对照为人工） | 待交付 | 无 RULE 对照的 INV 条数 |
| I08 | 待扫 | 全部 | 100% | `enforces` 为空的条数 |
| I09 | 待扫 | 全部 | 100% | 无编号的测试数 |
| 连线 | 待生成 | 全部 | 100% | 无执行点或无用例的 INV 条数 |
