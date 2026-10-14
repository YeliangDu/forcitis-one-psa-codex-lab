# PREFLIGHT — psa

日期：2026-10-14 · `legacy/psa` → `/Users/yeliang/Projects/FORCITIS/psa`（软链；工作区 `/Users/yeliang/Projects/FORCITIS/psa-modernization`，放在仓外，避免自我递归、避免污染 psa 的 git 状态）

## Answers

1. **范围**：是完整系统，还是更大代码库的一个切片？外部有什么依赖它？
   > 完整系统，外部仅读库/接口
2. **本地构建与测试**：本机能否 restore / build / 跑测试？完整 CI 多久？
   > 能，CI 约 10–20 分钟
3. **定制构建设施**：
   > 不知道
   >
   > **待人工补充。** 从 CI 推断：`scripts/ci/resolve-mvn.sh`（有可用的 mvnd 就用 mvnd，否则回退 mvn），各 runner 隔离 `~/.m2` 和 pnpm 目录，Maven 走阿里云 central 镜像，yudao 代码生成器在 infra 模块。未发现私有 Maven 仓库。
4. **既往尝试**：
   > forcitis-one 就是 PSA 的接班系统
5. **禁区**：
   > 仅分析，不改 psa 仓代码
   >
   > 补充（用户中途追加）：「forcitis-one仓库代码不能参考」—— 只用其 ADR-0001 作栈参照。

## Check 6 — 范围边界

psa 是**独立仓库**（monorepo，三个子项目同仓），**出站依赖**：无（`legacy/psa` 是仓库根，不依赖仓外源码）。

**入站消费者（影响范围）**——用户确认「外部仅读库/接口」，源码中可见：
- `BI-olap`（55 个文件命中 PSA 表名或接口特征）、`BI` / `BI-release`（各 4 个）：直接读 PSA SQL Server 表，例如 `psa_term_work`、`psa_time_sheet_works_end`。
- `forcitis-it-service-portal`（3 个文件）：调用 PSA 接口或引用 PSA 数据。

→ 换库（SQL Server → PostgreSQL）或改表结构，都会打断这些只读消费者。`brief` 必须为每个消费者明确过渡决策：保留只读兼容视图或同步副本、迁移消费者，或者排期中断。

## 检查表

| # | 检查 | 状态 | 发现 / 修复 |
|---|---|---|---|
| 0 | 人工答复 | ⚠️ | 5 题已答；第 3 题「不知道」，已按 CI 推断补充，待人工确认 |
| 1 | 栈识别 | ✅ | Java 5,141 个文件 / 37.4 万行代码；Vue 364 / 9.4 万；JS 528 / 6.9 万；TS 112 / 1.6 万；SQL 340 / 7.3 万。Maven 多模块（gateway、framework 28 个 starter、system、infra、bpm、fst）；pnpm（PC）；Taro 4.2 + React 18（H5）|
| 2 | 分析工具 | ✅ | scc 4.1.0、python3 3.12.13、lizard 1.22.2 |
| 3a | 构建定义 | ✅ | `.github/workflows/pr-gate.yml`：Temurin **Java 8**，mvn 或 mvnd（`scripts/ci/resolve-mvn.sh`），Node 16 + pnpm 7（PC），Node 20 / 18（H5 和工具）；Flyway 迁移在 Testcontainers SQL Server 上真跑；部署 `cd-dev/pre/prod.yml`；PC 另有 `Jenkinsfile` |
| 3b | 老栈冒烟 | ✅ | 用 JDK 11 离线编译 `yudao-framework/yudao-common`（level 2）通过，耗时 2.4 秒。本机另有 **Corretto 8**（`~/.local/opt/corretto8`，不在 `java_home` 列表里），与 CI 的 Java 8 一致；`tools/local-dev` 已能用它启动后端连 dev 库跑 E2E（#2068） |
| 3c | 目标栈实证 | ✅ | 临时工程：Boot 4.1.1 + Java 25 + Modulith 2.1.1 + Data JDBC + Flyway + Testcontainers **PostgreSQL 17.11**；`ApplicationModules.verify()` 和 PG17 连通两个测试全部通过（2/0/0），工程已删 |
| 4 | 源码完整性 | ✅ | DDL：`psa-backend/sql/sqlserver`（编号脚本）+ 622 个 Flyway `db/migration` 脚本（SQL Server 方言）；配置在 Nacos（仓库外，配置项不全）；无源码的二进制产物：无 |
| 5 | 可选上下文 | ⚠️ | git 历史完整（1,051 个提交），可做变更频度分析；没有接 APM 的 MCP，无运行时叠加数据 |
| 7 | 源码防改 | ⚠️ | 项目和用户 settings 里都没有 `Edit` 的 deny 规则。建议在 `psa-modernization/.claude/settings.json` 加：`{ "permissions": { "deny": ["Edit(/legacy/**)", "Edit(//Users/yeliang/Projects/FORCITIS/psa/**)"] } }`。权限规则只管 Claude 的文件工具和它能识别的 shell 写法，真正的保障要靠操作系统（只读挂载或沙箱） |

## 各命令就绪度

| 命令 | 判定 |
|---|---|
| assess / map / extract-rules | **Ready** |
| harden | **Ready**（SAST 工具：未发现专用工具，以 Claude 审计为主） |
| brief | Ready（等上游产物） |
| transform / reimagine | **Ready**：目标栈实证为绿；老栈可在本机用 Corretto 8 运行（连 dev 库），可做双跑对拍。生产使用量数据待取（`runtime_usage_queries.sql`） |
| uplift | Ready-with-gaps：源 JDK 8（Corretto）和目标 JDK 25 都有；OpenRewrite 没装（`mvn org.openrewrite.maven:rewrite-maven-plugin` 可按需拉取）；存在入站消费者（BI） |

**最重要的一步补齐**：取生产运行时使用量（`analysis/psa/runtime_usage_queries.sql`，在生产库只读执行后导出 CSV）。用户指出「运行代码没那么多」，评估范围要以实际使用为准。

下一步：`/code-modernization:modernize-assess psa`
