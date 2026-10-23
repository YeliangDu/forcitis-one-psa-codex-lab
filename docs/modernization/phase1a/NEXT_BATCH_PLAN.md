# Phase 1a 下一批派工计划（准备阶段）

起草：one-1a-next，2026-10-15。状态：**one-coordinator 已认可顺序与归属约定（2026-10-15）；Q1 已有用户决定**。#72 合并、收到前任 psa-1a-platform-bridge-reimagine 的交接报告、one-coordinator 发「开工」之前，不写业务代码，也不派实现代理。

依据：
- one-coordinator 派工单（2026-10-15）；
- `phase1a/NEXT_BATCH_DRAFT.md`、`phase1a/DEFERRED.md`、`phase1a/review/REVIEW_*.md`、`process/1a/contract.md`，均为 #72 分支 `27ac707`；
- `CROSS_CUTTING_MATRIX.md` 与 `CROSS_CUTTING_DECISIONS.md`（#75 分支）；
- SESSION_PLAYBOOK TEAM-29–39、附 M5、附 M2、附 M1、附 M3、DATA_DESIGN_CHECKLIST §14.1；
- `proposals/PHASE1A_PLATFORM_CONVENTIONS.md`。

现状数字由只读清点得出，采集于 #72 分支 `27ac707`。#72 整合复审之后若再有改动，开工前重新清点一次。

## 0. 对草案的更正

#72 已合并（main `3485e880`）。草案与 DEFERRED 已按本表更正（one-1a-next 更正 PR）；`application.properties` 的残留由 R-3a 处理（即 INT-L24）。草案与本计划不一致处以本计划与 one-coordinator 的答复为准。

| 草案原文 | 实际（`27ac707`） | 处理 |
|---|---|---|
| R-2「84 项改名」 | `ddl-naming-baseline.txt` 实有 79 条：列 23、约束 40、索引 16。分布：workforce 25、identity 25、numbering 10、audit 7、integration 7、approval 5 | 按 79 条派 |
| R-4「约 91 处、28 个迁移」 | 数据库时钟默认值 102 处、31 个文件，其中 `DEFAULT` 写法 99 处。另有 3 处在 UPDATE / INSERT 中使用 `now()`：numbering `V20261014135200:11`、`V20261014135300:3`、`V20261015000300:78`。迁移文件目前没有守护 | 按 102 处派，并补迁移文件的守护 |
| R-3「B2 011000 / 011100 改无条件」 | 这两个迁移已改为无条件。剩下的是建库脚本 §4b 中的 `to_regclass` 判断 | 只改 §4b |
| R-3「基线开放标记移除后生效」 | 仓库里**不存在**基线开放标记。`migrations_immutable.py` 也没有读标记、放行的逻辑 | 由 W0 先建 |
| 台账「OneApplication 无 main，build.sh 拒绝打包」 | `OneApplication.java:9-11` 已有 `main`。`pom.xml:55-59` 中 `mainClass` 已设，采用 ZIP 布局 | 从待办删除；开工前用 `build.sh` 实跑确认 |
| DEFERRED「编号规则生效日由 B1 交付」 | B1 已实现作者填写生效日、早于确认当天即拒绝 | DEFERRED 删除此行 |
| `application.properties:15` | `spring.flyway.schemas` 仍列着已删除的 `delivery_execution` | 并入 R-3 |
| 验收测试守护（§6.1 第 8 条） | 没有验收测试目录约定。没有「实现提交改动验收测试即失败」的 CI 守护，只有仅告警的 `assertion_drift.py` | 由 W0 先建，否则测试代理无处落地 |

## 1. 总体顺序与关键路径

> 2026-10-15 起的最新执行顺序与追加卡见第 6 节；本节与第 6 节不一致处以第 6 节为准。

首次部署任何环境之前，以下各项必须完成：
- 阻塞 080：#72 合并；
- 阻塞 090：F-1；
- R-1（用户已定，B3 曾擅自推迟）；
- R-2、R-4：按第 11 条「能一开始做对」，部署前做比部署后走 expand / contract 便宜得多；
- R-3（基线收口）；
- F-2 守护；
- F-3：一次构建、多环境部署，dev 首次部署就要用；
- 本批缺陷卡中涉及安全的几张（含 INT-M4 浏览器标识重放、INT-M5 重新启用敏感角色或账号需第二人、INT-L30 幂等重放前复核授权）；
- F-12：ADR-0030 非生产环境单人运维例外，须经用户批准后实现。否则 dev 上单人无法完成 C-5、编号确认等两人确认操作。

```
#72 合并 + 交接报告 + 「开工」
  │
  W0 批次基础设施（1 个代理，串行）
  │   验收测试目录与 CI 守护、基线开放标记与放行逻辑、迁移版本单调性（B3-R7）
  │
  W1 结构性重构窗口（串行；这段时间不派功能代理，因为几乎每个文件都会碰到）
  │   R-3a 纯合并基线 → R-1 UUIDv7 → R-2 改名 79 条 → R-4 去数据库时钟默认值
  │   （直接改基线文件，每个 PR 附预期表结构 diff；用户决定见第 4 节）
  │
  W2 公共基础（K-1、K-2、K-3 三路，文件互不相交，可并行）→ 冻结
  │   K-1 金额内核（F-7）  K-2 时态基座（F-8a）  K-3 平台共用件（F-8b）
  │
  W3 功能补做（并行，归属见第 2 节）
  │   F-1 数据范围 ★  F-2 守护 ★  F-3 运行时配置 ★  F-4  F-5  F-6  F-10
  │   F-9、F-11、F-12 先写 ADR，W0 起即可并行；F-12 批准后再实现 ★
  │
  W4 基线收口：首次生产部署前删除基线开放标记（第 8 节改，依据 ADR-0038（已采纳，部分取代 ADR-0033 迁移方案第 5 条等）；原为首次部署 dev 前）
       ★ 标注的项、R-1..R-4 与安全类缺陷卡合并之后，才解除 090
```

缺陷修复（第 3 节）单独成批。某张卡涉及的文件若属于 W1–W3 中正在进行的代理，就等该代理合并之后再开。卡与当前在途代理的文件不相交时，可以与 W3 并行。

**为什么 R-1 在 F-1 之前**：
- F-1 要把 `OrgUnitId` 下沉到 kernel，并新建范围授予表与 `CurrentAccess`。
- R-1 会改 ID 的底层类型、事件负载和 OpenAPI。
- 如果先做 F-1，F-1 新写的类型、表、契约都要被 R-1 再改一遍。
- R-1 必须用整库视角一次改完，不能与功能代理并行，否则基线文件和 `openapi.json` 必然冲突。

**为什么 K 在 F 之前**：遵照 §6「公共基础先行」。
- F-10 利润中心标识要用 K-2 的时态基座。
- F-1 的数据范围装配要用 K-3 的分页与脱敏约定。
- F-4、F-5 要用 K-3 的列表分页契约。

## 2. 归属图

### 2.1 适用于每个代理的约定

- **迁移号段**：不预分时间窗。
  - 原因：版本号就是时间戳，B3-R7 的单调性守护要求新版本不低于 main 上已有的最高版本。预分的窗口会因合并先后不同而被拒。
  - 规则：每个代理只在自己模块的 schema 目录里新增迁移。合并前先同步 main，再把**本分支尚未合入 main 的**迁移重新编号为当前时刻。这些迁移还没有在任何环境执行过，所以不违反「只追加」。
  - 基线开放期间（W1–W3），各代理改的是基线文件中**本模块**的那一段，见 Q1。
- **生成物不手工合并**：`frontend/packages/api/openapi.json`（`OpenApiExportTest` 生成）、`src/generated/*`、`events/asyncapi.yaml`、`build_all.py` 的产物。冲突时取任一边，重新生成。
- **共享登记文件只追加本代理的行，不改别人的行**：
  - `Permission.java`：在本模块那一块内追加；
  - `SensitivePermissions.java`；
  - `data-access-registry.yaml`；
  - `events/<模块>.yaml`；
  - `glossary/terms.yaml`：提议术语写进 `backend/glossary/proposed/<主题>.yaml`，按主题命名，不按批次命名（A-N6）；
  - `module-dependencies.txt`；
  - `one_db_roles_schemas.sql`：新表的授权要同步；
  - `application.properties` 的 `spring.flyway.schemas`。
- **越界改动**：改归属图之外的文件，先报 one-1a-next，由整合者统一改（§6.1 第 2 条）。改公共基础（W2 冻结后）也一样。
- **验收测试**：
  - 测试代理 `code-modernization:test-engineer`（sonnet；F-1 用 opus）在每个 F 开工前，把验收测试先提交到验收测试目录。
  - 实现代理只负责让这些测试转绿，不得修改。认为测试有误时停下报告。
  - 单元测试由实现代理写。
- **子代理再派子代理**：一律前台等待（§2.1 第 8 条）。
- **派子代理**：都写明模型。复审用 opus，整合与复审的推理强度用 high。

### 2.2 W0 与 W1（串行）

| 代理 | 类 | 模型 | 写入范围 | 不碰 | 产出与验证 |
|---|---|---|---|---|---|
| W0 批次基础设施 | 功能补做（守护） | sonnet | `scripts/ci/`（`migrations_immutable.py` 读开放标记、单调性检查 B3-R7、验收测试目录守护）、`.github/workflows/ci.yml`、基线开放标记文件、验收测试目录约定（后端 `src/test/java/com/forcitis/one/acceptance/`，前端 `frontend/**/acceptance/`，以开工时的结构为准）、PR 模板一行 | 业务代码、迁移 | 三个脚本都有自测。实现提交改动验收测试目录时 CI 失败；提交说明带裁定编号的放行 |
| R-3a 纯合并基线 | 重构 | sonnet 执行，opus 复核 | 每个 schema 的迁移合并为一个基线文件；`application.properties:15`；建库脚本 §4b；`retired-module-schemas.txt` 与 `delivery_execution` 目录（基线中直接不再出现） | 任何表结构变化 | 两个空库各跑合并前、合并后的迁移，`pg_dump --schema-only` 的 diff 为空，证据写进 PR 正文 |
| R-1 UUIDv7 | 重构 | sonnet 执行，opus 复审 | ID 值对象；`KernelConfiguration` 的默认 `IdGenerator` 改为 `Uuids.timeOrdered`；每个 schema 的主键与外键列；事件负载与 `events/*.yaml`；OpenAPI；`primary-key-type-baseline.txt` 删行；前端生成物 | 外部来源 ID：`SourceUnitId`、`SourcePersonId`、`SourcePositionId`；快照表按平台约定 §1 沿用 A8 ID，列入白名单 | 25 张基线表逐一处理：改为 uuid，或按约定列入快照白名单并写明理由。15 个 long 型 ID 值对象、OpenAPI 中 39 处 int64 逐一给出结论。一并解决 Flowable 顺序数字 ID（b12-M-12：`StrongUuidGenerator`）。全量测试与守护通过 |
| R-2 改名 79 条 | 重构 | sonnet | 基线文件中的列、约束、索引名；引用这些名称的 SQL 与仓储；`ddl-naming-baseline.txt` 删行；`SensitiveColumnExposureGuardTest` 若未完成则补上 | 业务语义 | 基线清零，`DdlNamingTest` 通过 |
| R-4 数据库时钟 | 重构 | **Codex 试点**（用户「用最合适的模型，你试试」；one-coordinator 派 Codex 在独立 worktree 实现，验收测试仍由测试代理先写，opus 复审，PR 正文注明 Codex 实现） | 基线中 99 处 `DEFAULT` 时钟与 3 处 `now()` 去掉；各 INSERT 显式传入注入 Clock 的时刻；迁移文件守护（扩展 `TimeSourceGuardTest`，扫描迁移） | — | 守护对迁移生效，`sql-database-clock-exceptions.txt` 仍为空 |

### 2.3 W2 公共基础（三路，冻结后只由整合者改）

| 代理 | 模型 | 写入范围（独占） | 共享构件 | 依据 | 验收要点 |
|---|---|---|---|---|---|
| K-1 金额内核（F-7） | opus | `kernel/money/`（`Money` 不再隐式舍入、精确中间运算类型、`Rate`、`Ratio`、按权重拆分原语、舍入口径集中一处、`TaxContext`）；守护：领域代码禁止 `setScale` / `divide` / `RoundingMode`、DDL 金额精度与币种配对、OpenAPI 金额为十进制字符串加币种（G-M5）、前端金额格式化 lint（G-M6） | 现有 `Money`、`Hours` 的调用方只做适配，不改语义 | XD-01、02、07、08；CC-B01；FQ-23..27 未答时按 XD-08 的建议实现 | 拆分守恒、确定性、负数对称、冲销镜像。`TaxContext` 按版本与生效日取税率（配置类，只追加），价税分离按 FQ-27 建议 A，口径集中在一处，以后改口径只改配置 |
| K-2 时态基座（F-8a） | sonnet 执行，opus 复审 | `kernel/time/EffectivePeriod`（开闭区间约定）；按日期取生效版本的仓储支持；防重叠排他约束与只追加的统一写法与守护；workforce 的 `org_unit_versions`、`assignment_versions`、`legal_entity_versions` 与 numbering 的 `numbering_rule_versions` 迁到基座，不改行为 | workforce、numbering 的版本表（只改实现方式） | CC-B02；XD-05、06 | 现有版本表的行为测试全部不改仍为绿；新守护覆盖全部版本表 |
| K-3 平台共用件（F-8b） | sonnet | 统一的事件消费幂等；列表游标分页契约与上限（平台约定：`cursor` + `limit`，默认 50，上限 200）；共用脱敏组件（区分「职级标准费率」与「个人真实薪资 / 日薪」两级）；共用导出控制；日期边界测试夹具；中文按拼音排序（ICU `zh`）约定与守护 G-X2 | `kernel/` 中的 `PageRequest`、`CursorPage`；`platform.web`；前端 `packages/api/paging.ts` | XD-03；用户「按职级标准费率看到个人行」 | 拼音排序在 dev 库（内建 C.UTF-8）上可用，不需要重建库，排序时显式指定 ICU 排序规则。不行时停下报告 |

### 2.4 W3 功能补做

| 代理 | 模型 | 表（新增 / 修改） | 权限码（预计；名称以 terms.yaml 登记为准） | 事件（预计） | 写入范围（独占） | 依赖 |
|---|---|---|---|---|---|---|
| F-1 数据范围 ★ | opus 执行，opus 复审；测试代理 opus | identity 新增角色范围授予明细表；`data-access-registry.yaml` 加 `scope_kind`（SCOPED / GLOBAL）并逐表定值 | 授予时选范围的权限沿用 `account-role:update`；「待调整权限」处理权限给组织数据管理员和安全审计员 | 范围授予变更；待调整权限产生 / 处理 | `platform.identity` 授权部分；kernel 新增 `CurrentAccess`；`OrgUnitId` 下沉到 kernel，`OrgUnitAccess` 改为类型化 ID，去掉 `NOT_USED = new LegalEntityId(1)`；workforce 的部门浏览与人员详情接入范围；`DataAccessRegistryTest`、`onlyRegisteredPersistenceTouchesTheDatabase`、`ScopedRepositoryContract`；敏感权限只授给具体的人（守护）；调岗或离岗生成待调整权限待办 | K-3；ADR-0026「1a 交付范围」、ADR-0027 离职删除授予 |
| F-2 守护 ★ | sonnet | — | — | — | `src/test/java/com/forcitis/one/` 的守护类；`backend/architecture/` 的豁免清单（G-T4 规格到测试对照、豁免清单入仓）；G-T1 核对按仓储判定并读 §14 例外；G-T2 扩展到全部生产代码 | W1 完成后开始（要扫描最终代码形状）；G-T4 在 F-1 / F-4 / F-5 合并后最后跑一次 |
| F-3 运行时配置 ★ | sonnet | — | — | — | PC：`login-config.ts`、`login-page.tsx`；H5：`config/index.ts`、`page-frame.tsx`、`wecom-login.ts`；5 个构建期变量改为运行时读取的配置文件；nginx 模板只写约定文件名与路径，部署模板由 one-ops 改 | 与 one-ops 约定配置文件的路径与发布方式 |
| F-4 前端契约 | sonnet | — | 沿用后端已有权限码 | — | 前端审批与收件箱：D-1a-139 全部待办只读视图、D-1a-75 转交、D-1a-77 批量与代理界面；管理模式 1 小时横幅（D-1a-48，`administratorModeExpiresAt`） | K-3；后端接口已有的先做，缺接口的报告由 F-5 补 |
| F-5 审批契约 | sonnet | approval 按条目需要 | 按条目需要 | 按条目需要 | `platform.approval` | K-3。逐条核对范围：D-1a-181 契约原文写「1b 交付」，不做；D-1a-67 与 D-1a-128 的知会部分「随第一个用到知会的流程交付」（RULE-299 已推迟），1a 的 TIMESHEET 流程如果没有知会节点就不做。其余 D-1a-76、91、130、131 核对后实现 |
| F-6 HELD 按角色通知 | sonnet | — | — | 消费同步暂停与每日汇总 | workforce 通知部分、integration 企业微信发送（按角色取人） | W1；企业微信应用（B-2）就绪前用测试替身验收 |
| F-10 利润中心标识 | sonnet | workforce 新增利润中心标识版本表（只追加，带生效区间）；`COLUMN_OWNERSHIP` 与注册表登记为新系统所有 | 利润中心标识维护（组织数据管理员） | 标识指定 / 结束；失效提醒待办 | `organization.workforce` 中利润中心部分；组织同步不得覆盖（守护） | K-2 |
| F-13 复职重授 employee（INT-M12，应交付未做） | sonnet | — | — | 复用账号授予事件 | `platform.identity` 的 `AccountProvisioning` 与 `HandleOrgSnapshotChange` 复职路径；D-1a-17：只重授基础角色，其余由管理员重新分配；写审计 | W1；W3 首批 |
| F-14 离职者在途待办重新取人（INT-M13，应交付未做） | sonnet 执行，opus 复审 | approval 按需要 | — | 消费离职事件 | `platform.approval` 新增离职监听：D-1a-18 / 108 / 168，重新取人，取不到转流程管理员并告警、审计 | W1；W3 首批 |
| F-15 登录页 ICP 备案号（用户「需要，页脚展示 ICP 备案号。」） | sonnet；测试代理先写验收（页脚出现、链接正确、配置缺失失败） | — | — | — | PC 与 H5 登录页页脚显示 ICP 备案号，链接 `https://beian.miit.gov.cn/`（新窗口）；只显示 ICP 号，不显示公安备案号；号码作为按环境的部署配置，走 F-3 的运行时配置，不写死在组件里；缺失时启动或构建失败并告警；界面文字「ICP 备案号」先查 terms.yaml，查不到登记提议术语。生产号码「苏ICP备12028598号-4」（one-ops 核对官网页脚，主体弗思特建筑科技有限公司，用户已确认备案完成），按环境配置 | 与 F-3 同一代理顺序做（同改登录页与运行时配置），F-3 之后 |
| F-9 ADR-0029 | opus 起草，`architecture-critic` opus 评审 | — | — | — | `adr/src/ADR-0029.md`（提议） | 无；**批准前不实现** |
| F-11 业务单据锁定、红冲、修正 | opus 起草，`architecture-critic` opus 评审 | — | — | — | `adr/src/ADR-0031.md`（提议），与 ADR-0016:23、ADR-0022:32、CC-N05 衔接 | 无；1a 只定设计，Phase 2 实现 |
| F-12 非生产环境单人运维例外 ★ | ADR-0030 已采纳；sonnet 实现、opus 复审 | 不新增表；审计记录标注 single-operator | — | — | `adr/src/ADR-0030.md`（提议）。批准后实现：只在非生产 profile 下由显式配置（暂名 `forcitis.security.single-operator-dev`，以 terms.yaml 为准）开启。开启后：①两人确认可由一人完成，照常写审计；②超管与应急账号下限降为 1；③同步锁死阈值相应调整。**生产 profile 下该配置存在即启动失败**：`ProductionConfigSanityTest` 加守护，部署流水线对 prod 再校验一次。生产部署前清单（PREDEPLOY_CHECKLIST）加一项：关闭单人例外，启用第二名超管与第二个应急账号。**安装器子命令更名**：ADR-0030 第 6 项「安装器（漂移发现）」写的 `remote_install.sh install-pending prod` 已改名 `pull`（PR #111 拉取模式，由定时器运行，环境取自 ECS 本机 `release-source`，不再带参数）；实现漂移检查时落在 `cmd_pull`，ADR 正文不改。 | 用户 2026-10-15「dev 白名单把我也加上……这些安全措施等到生产部署前再真正落实」（one-ops 已记 intent）。该例外放宽的规则散见于已采纳的 ADR-0026 / 0027 / 0028（敏感权限两人确认、编号四眼确认、至少 2 名超管与 2 个应急账号、同步锁死在可用超管少于 2 时 HELD、放行人不得在离职名单中），按 §12.1 须新 ADR 取代对应条款的适用范围。实现时会碰到 F-1（敏感权限授予）与 B1 已交付的确认流程，所以排在 F-1 合并之后，并以同一个配置开关接入 |

F-1、F-6、F-10 都会在 `organization.workforce` 中写代码，所以按包划界：F-1 只改部门浏览与人员详情的查询路径；F-6 只改通知；F-10 新建自己的包。三者都要在 `Permission.java` 中追加，按 2.1 的约定处理。

## 3. 缺陷修复批（按卡手术式，小 PR 直合 main）

按 #72 整合复审的统计（`27ac707`）：
- Blocker：未关闭 0 条。
- High：b12-H7 已由前任在 #72 内手术式收口：3 条移入 `scenario/` 并删除例外；余下 2 条待 B1 修完后同样移走，不保留例外。
- Medium：未关闭 21 条，其中 8 条已在草案第三节，13 条不在。
- Low / Nit：未关闭 37 条，其中 5 条在草案，32 条不在（部分只是「未核实」）。

流程：
1. #72 合并后，一个 sonnet 代理逐条核实「未核实」的条目，已修的关闭。
2. 余下条目写成卡：复审编号、复现步骤、预计改动的文件。
3. 测试代理先写失败测试，再由 sonnet 修复。
4. 一张或几张相关卡开一个小 PR。Medium / Low 补上测试、CI 通过即关闭（§6.1 第 4 条），one-1a-next 抽查。

#72 整合复审 `phase1a/review/REVIEW_integration.md` §7 另列 INT-M1..M16、INT-L6..L32，归类如下（与上文各路复审的卡合并出卡，同一文件的卡并为一个小 PR）：
- 转功能补做：INT-M12 → F-13，INT-M13 → F-14（前任建议优先，排 W3 首批）。
- 并入结构性重构：INT-M9、INT-L12（Flowable `StrongUuidGenerator`）、INT-M10 与 INT-L18（裸 long 改类型化 ID）→ R-1；INT-L23 → R-4；INT-L24 → R-3a。
- 并入功能补做：INT-M16、INT-L6、INT-L19、INT-L22、INT-L26 → F-2；INT-L29（系统角色豁免）→ F-1；INT-L15（敏感清单集合相等）→ ADR-0032 及其实现。
- 文档项：INT-L16 已由 one-coordinator 在 #85 写入 §14，关闭；INT-L32 在本更正 PR 中处理（DEFERRED 删过期行并补「专业产值切分 → Phase 2」，CROSSCHECK 第 4 条改为已解决）；INT-L10 按 one-coordinator 核对以 D-1a-78 为准，contract.md D-1a-89 措辞补全（不改语义），回归测试 `INT-L10 重启起点与契约一致` 按 D-1a-78 断言。
- 其余（INT-M1..M8、M11、M14、M15，INT-L9..L11、L13、L14、L17、L20、L21、L25、L27、L28、L30、L31）为缺陷卡。

另有 one-coordinator 2026-10-15 登记的缺陷卡（来自 ADR-0030 起草时的核查）：
- **ADR28-1**：ADR-0028 第 1 条「同步会使可用 `super_admin` 少于 2 名时暂停」未实现——`ApplySourceSnapshot.java:85` 只在离职名单含超管或安全审计员时进入 HELD，人数不足只写进原因文本（`AdministratorSeparationReview.java:47-51`）。回归测试 `ADR28-1 可用超管将少于下限时同步暂停`；下限按 ADR-0030 在单人运维模式下为 1。
- **ADR28-2**：ADR-0028 第 4 条所说「放行后超管将为 0 时只能走运维命令」的运维放行命令不存在（`OperationsCommands` 无 release）。回归测试 `ADR28-2 运维命令放行同步暂停（带见证人，审计）`。
- **FLW-1 Flowable 引擎时间来源**（one-coordinator 2026-10-15 登记，**首次部署前须有结论**）：`act_*` 由引擎自建，不在 R-4 范围；须核实引擎内部时间（任务创建、到期、计时器）是否取注入的 Clock（`ProcessEngineConfiguration` 的 clock / Spring `Clock` bean），以及审批时限、超期提醒与升级（D-1a 时限细则）、F-14 改派、ADR-0032 待办 5 个工作日升级用到的时刻从哪里取。结论写进规则卡与 INFRA_RUNBOOK；若需改动按缺陷卡手术式修复，回归测试 `FLW-1 引擎时间取注入 Clock，固定 Clock 下任务创建与到期时刻确定`。opus 核实（判断类）。

- **R-6 删除 `numbering_rules` 旧列**（R-3a opus 复核 M-1）：基线保留了 `pattern`、`reset_policy`、`start_value`、`confirmation_status` 四个旧列（纯合并须保留，`NumberingVersionMigrationIT` 断言其可空）。基线开放期内核实无读写方后删除（直接改基线，附预期表结构 diff），同步改该断言并在 PR 单列理由；排在 R-2 之后、R-5 之前，可与 R-2 同批（同属表结构清理）。
- **OSS-1 实例元数据加固模式**（one-ops 2026-10-15，经 one-coordinator）：归档 ECS 的实例元数据服务为加固模式（须先取元数据令牌再取实例角色凭据）。归档写入方（审计归档、流程归档）实现时核对所用阿里云 SDK 默认凭证链版本支持该模式，并加启动自检（取不到凭据即失败并告警）或集成测试说明。
- **OSS-2 归档只写不读**（同上）：写入方 RAM 策略显式拒绝 GetObject；`INFRA_RUNBOOK` §5 所写 `oss:GetObjectMetadata` 未授予且是否为有效动作名待核。核对 `OssObjectStore` 探针与写入后校验实际调用的接口：不得写后回读，校验用上传返回的 ETag / CRC；探针若需读元信息，把实际接口名告诉 one-ops 补授权。WORM 在第一个写入方上线前由用户决定再锁。
OSS-1、OSS-2 在首次部署前须有结论（归档作业随 dev 部署启用）；按缺陷卡手术式处理，回归测试 `OSS-1 …`、`OSS-2 归档校验不读对象`。

ADR28-1、ADR28-2 两张卡按 §6.1 第 10 条手术式修复（测试代理先写失败测试、一卡一最小改动、小 PR），排在 W3 与 F-12 同期（都动 `platform.identity` 运维命令，与 F-12 不并行，F-12 之后做）。

首次部署前要先修的几张：
- 安全相关：B3-R3 幂等抢占与防护令牌、B3-R4 吊销会话审计、B3-R5 必记动作守护、B3-R2 仓储写入守护堵口；
- b12-M-8：监听器重投时重复入账审计；
- b12-M-11：`WecomConfiguration` 的 `Clock::systemUTC` 兜底；
- B2b NEW-2：出参的 null 与前端 `.optional()` 不匹配。

**不在草案中的 13 条 Medium**：
- b12：M-4 投递进入死信无事件、M-8、M-9、M-11、M-12（并入 R-1）、M-14、M-16 / C-M10 后端对外文案不在用语守护内；
- A-M5；
- B2-N5：只修文档，DATA-RULE-07 标题的「需负责人确认」已由用户「单独的更正记录表」决定；
- B2-N6：`AuditDetail` 移到 kernel，属结构性重构，并入 W2；
- B2b NEW-2；
- B3-M12：术语逐条核实；
- C-M9。

**按已有用户决定处理，不必再问的复审疑问**：
- **A 第 2 轮「转交人的已办里看不到转交」**：intent 130 的原话是「新增只追加的任务办理记录（含自动同意、加签建议、转交、代理），已办与逐轮历史从中查询」。因此已办应列出转交、加签、代理；「认领」「释放」不在原话列举之内，不列入已办。按缺陷卡处理。
- **C-M3「审批已发起 / 发起失败的可见状态」**：
  - intent 144：取人字段缺失时「发起失败并告警」；
  - intent 130：单据退回后可编辑，「具体状态名在各业务阶段定」；
  - A 已经实现发起被拒时退回给员工。
  - 因此不再另设可见状态，状态名在 Phase 2 定。

## 4. 用户决定

**ADR 与仓库设置**（2026-10-15，经 one-coordinator，intent 见 #98，已在其会话记录中核实）：
- ADR-0030（#93）「批准并接受例外 (Recommended)」，U-2 / U-3 保留，U-4 / U-5 不做，U-6 不放宽 → F-12 可实现（W3，F-1 之后）。
- ADR-0032（#94）「按建议批准 (Recommended)」：0032-Q1 (a) 双人逐人级角色只许 `ALL` / `CUSTOM_ORG_UNITS` / `SELF`；Q2 确认；Q3 5 个工作日；Q4「现在不开，生产前再定 (Recommended)」——不设 CODEOWNERS，改守护文件的 PR 在正文单列、合并前 opus 复审。随 F-1 落地。
- ADR-0031（#95）「按建议批准 (Recommended)」：U-1..U-3 按建议，U-4 进第三轮用语确认；1a 只定设计。
- ADR-0029（#96）：0029-Q2 生产只读计数「授权 (Recommended)」，由 one-ops 执行，结果写入证据后再报批。
- main 分支保护 strict 状态检查「开启 (Recommended)」，已开。
- W1 临时容器「允许，用完即删 (Recommended)」：仅本机临时 postgres:18 空库，不连外部库，用完即删，报告中写明已清理。


**Q1　迁移基线开放期间的写法**

用户决定（2026-10-15，经 one-coordinator 转达，已在其会话记录中核实）：「开放期内直接改基线 (Recommended)」。记录在 intent `2026-10-14-225735-one-1a-next-经-one-coordinato.md`，并已写入 SESSION_PLAYBOOK 附 M3「迁移基线」。

具体做法：
1. R-3a 先做纯合并，证明表结构 diff 为空。
2. 之后 R-1、R-2、R-4 以及 W2、W3 的表结构改动直接改基线文件。每个 PR 附「预期表结构 diff」，由 opus 核对。
3. 首次部署 dev 前删除开放标记，恢复只追加（第 8 节改为首次生产部署前，依据 ADR-0038（已采纳，部分取代 ADR-0033 迁移方案第 5 条等））。
4. W0 的 `migrations_immutable.py` 放行逻辑与此一致。

## 5. 交给 one-coordinator 核对的点（不是用户决定，只需确认）

1. **b12-H7**：已在 #72 关闭。one-coordinator 按 §6.1 第 11 条裁定合并前收口，由前任处理。
2. **R-1 的范围**：员工、组织单元、岗位、任职按平台约定 §1「外部主数据沿用来源 ID……快照表列入白名单」沿用 A8 ID。但 `primary-key-type-baseline.txt` 把这几张表也列为待改 uuid。one-coordinator 已认可按平台约定执行：这几张表进白名单并写明理由。`user_accounts.user_id` 由 R-1 代理核实来源后报 one-coordinator。
3. **D-1a-181 不纳入 F-5**：契约原文为 1b 交付；D-1a-67、D-1a-128 的知会部分随首个有知会节点的流程交付。one-coordinator 已认可。
4. **1a 出口条件**：brief 模板中「平台穿刺」（CC-B11）仍是 `Proposed revision:`，待用户批准；批准后由本会话补验收。
5. **ADR 编号**：#72 已用到 ADR-0028。本批占用 ADR-0029（F-9 按岗位授予）、ADR-0030（F-12 单人例外，按 one-coordinator 指定）、ADR-0031（F-11 红冲模式）。one-coordinator 已认可。三份 ADR 于准备阶段起草，各经 `architecture-critic` 评审后逐份交 one-coordinator；ADR-0030 优先。
6. **会话设置**：
   - 本会话权限模式为 bypass，与派工要求一致。
   - 推理强度从会话内无法自查，也无法自行修改；由 one-coordinator 请用户在应用中确认设为 high。
7. **NEXT_BATCH_DRAFT 的更正（第 0 节）**：#72 合并后由本会话开小 PR 更正，不改 #72 分支（one-coordinator 选 b）。

## 6. 2026-10-15 追加：最新执行顺序与卡

依据：用户 2026-10-15 的决定（经 one-coordinator 转达并在其会话记录中核实；intent 见 #98、#101、#106、#107、#113、#120 与本会话片段）、数据设计审计、主数据版本化盘点、DDL 基线审计、原则核对 A / B（报告在 `~/.modernize/psa/audit/`，未入仓）。

### 6.1 已完成与已决定

- 已合并：W0（#97）、R-3a 纯合并基线（#103）、ADR-0030 / 0031 / 0032（#93 / #95 / #94）。
- 已批准、待合并：ADR-0034 授权历史可还原（#118）、ADR-0033 自有标识与来源对照（#119，含规则评审修订记录 `revisions`）。
- ADR-0029（#96）暂缓：用户「公司要重新梳理岗位体系」，以岗位体系重梳完成为批准前置。
- 主键：用户「改为自有 UUIDv7 + A8 ID 对照列」「主键统一」「也加，自有表零例外」——所有自有表 UUIDv7 主键；豁免只剩框架自建表；唯一例外 `audit_entries`（用户「接受，登记为唯一例外」：按 `created_at` 月分区，主键 `(id, created_at)`，id 仍全局唯一，所有引用只用 id；新增分区表须另行用户批准）。

### 6.2 W1（冻结窗口，串行）

| 卡 | 内容 | 前置 | 执行 / 复审 |
|---|---|---|---|
| R-1a | 不涉及 A8 ID 的自有表 UUIDv7（17 张 + 6 张复合主键表）；`TimeOrderedIds`（实例状态、单进程单调）；ID 输出与规范 UUID 解析守护；OpenAPI 自有 ID = string/uuid | R-3a | sonnet / opus（复核中） |
| R-1b | A8 快照、版本、归属表与全部引用列改自有 UUIDv7；`platform_integration.source_references`（状态 PENDING / CONFIRMED / REJECTED，排他约束只约束已确认行）；`user_accounts.user_id` 自有 + `employee_id`；聚合 ID 在构造时生成；「本人」判定按 ADR-0033 逐条 + 反向集成测试；Flowable idGenerator 接 UUIDv7；审批子表引用 `approval_request_id`、办理记录序号；DA-1/2/4/5/6/9/11、DA-17 哨兵；B-7.1 `legacy_numbers` 多来源与 `migration_batch_id`（用户「迁入批次」）；A8 来源 ID 可为负数；DDL-01/02/03/04/06/08；`OTHER_ID_NAMES` 清空；`ticket_issuers.key_id` 改名；框架管理表清单；G-C5 事件 yaml 一半 | R-1a、ADR-0033 | sonnet / opus；可拆子 PR |
| R-6 | 删除 `numbering_rules` 旧列 | R-1b | sonnet / opus |
| R-2a | 79 处命名改名 + DDL-09/10/12/13/14；枚举 CHECK 一律命名（用户已采纳，NAMING §2.4 修订） | R-1b | sonnet / opus |
| R-2b | DDL-15/16/18/19/20（库内长度 CHECK）/21/23/29 | R-2a | sonnet / opus |
| R-5 | 用语改名：4 组同义错误码并入 kernel 通用；删 `PlatformWebUnauthenticated`；离职事件改名；中文名唯一守护；OpenAPI 与前端文案同步 | R-2b | sonnet / opus |
| R-4 | 去数据库时钟默认值（Codex 试点；种子固定时刻；「例外清单为空」为验收；ShedLock 理由一行注释） | R-5 | Codex / opus |
| DDL-07+24 | 审计表按月分区（见 6.1）、ShedLock 提前 3 个月建分区、按分区成批归档记 sha256 与 ETag / CRC、`platform_audit.drop_archived_audit_partition` SECURITY DEFINER 函数（校验批次已校验、条目数相等、id 覆盖），完成写审计 `platform.audit.partition.dropped`（detail 含分区名、批次 ID、sha256、条目数、校验结果、执行人 `session_user`），函数固定 search_path，登记为只追加守护的例外并写理由（db_scripts_aligned 由 one-ops 核对只有维护角色有 EXECUTE）；审计列表索引；维护角色 `one_<env>_audit_maint` 与授权由 one-ops 在建库脚本写（用户「专用维护角色」） | R-4 | sonnet / opus；**C-3 前必须** |
| DDL-26 / DDL-28 | `migration_safety.py`；`btree_gist` 由建库脚本预建、迁移只检查存在 | R-4 | sonnet / opus；**C-3 前必须** |
| DA-3 → DA-10、DA-8、DA-13 | 外部身份绑定 `external_identities` 与收件人解析移入集成层；部门简称归属；敏感分级登记与守护 | R-1b | 首次部署前 |

### 6.3 与 W1 并行的首次部署前缺陷批 D1（手术式，小 PR）

| 卡 | 内容 | 时点 |
|---|---|---|
| D1-1（A 3-H1） | A8 / 老系统视图连接只经工厂，强制查询、套接字、锁等待三种超时；WAITFOR 视图集成测试验证登录与待办首页降级 | R-1b 之后（同改集成层） |
| D1-2（A 4-H2） | 未知异常只记类名 + correlationId；集成测试断言邮箱不落盘；连接串 `logServerErrorDetail=false`（ECS `one.env` 由 one-ops 改） | R-1a 合并后即开 |
| D1-3（A 4-H1） | `ProductionConfigSanityTest` 断言模板含 prometheus 与 `logServerErrorDetail` | 同上 |
| D1-4（B H-5.3 / M-5.6） | `http.server.requests` 直方图；Hikari 显式配置 | 同上 |
| D1-5 | `SeparationRevokesRolesIntegrationTest.java:58` 口令字面量改运行时生成 | 同上 |

### 6.4 W2 与 W3 的追加

- **W2（与 K-2 时态基座同期，一次改到位）**：ADR-0034 实现（权限三表、复职重授 F-13 建在其上、DDL-17 角色退役不物理删除，**首次部署前必须**）；主数据版本化 25 张表（§14.1 D17 与守护 G-D11，先以「登记缺口、带负责人」进 CI）；DA-7 `employee_versions`、离职两字段；DA-17 日历版本化（用户「版本化」，新版本由业务管理员四眼确认，dev 走 ADR-0030 单人路径）；法人代码「未发过号前可更正，发号后禁改」（RULE_REVIEWS 先追加修订再改测试）。
- **K-3**：列表分页契约统一游标、上限 200（A 2-H1 / 2-H2）；测试库排序规则（DDL-11）与拼音排序 G-X2。
- **W3**：F-15 登录页 ICP 备案号随 F-3 同代理；F-4 转交界面依赖 R-1b（不走 int64）；F-8 掩码按「费率档位来源待定」写（job_grade 用语缺陷，见 one-coordinator 台账）。
- **用户第 2 批决定（平台约定，随对应卡实现）**：kernel `Reason` 上限 500；同一对象至多一个待确认授权申请；工时单同员工同工作日唯一；迁入单据不发新号；时刻一律 UTC `Z`；不提供 PATCH；范围外单资源 404；用户路径外部调用 3 s / 冷却 60 s；IP 不进应用日志（ALERT 行改引审计条目 ID）；只用 correlationId；SLO 300 / 500 ms 超标 P3。规则条文（NAMING §2.6、GUIDING §7a / §11、DATA_DESIGN_CHECKLIST §14.2 / §14.3、SUPPLY_CHAIN.md、ADR-0035 契约演进、ADR-0036 历史数据迁入）由 one-coordinator 另派起草，出来后核对再排守护。

## 7. 2026-10-15 追加（one-1a-next-2）

> 与第 6 节不一致处以本节为准。本节引到的卡文件见 `cards/R-1b.md`（文末修订）与 `cards/D1.md`（文末追加 D1-3b、D1-6、D1-7）。

依据：one-coordinator 对 R-1b 拆分与 ADR-0037 的答复（2026-10-15，已在其会话记录中核实）、#129 规则条文核对、#139 与 ADR-0037（#136）的 opus 复审、#134 复核。报告在 `~/.modernize/psa/handoff/one-1a-next-2/`（未入仓）。#129 尚未合并，下文引用的 G-* 守护编号与数值以 #129 合并后的条文为准，合并时核对一次。

### 7.1 已完成与在途

| 项 | PR | 状态 |
|---|---|---|
| D1-5 测试口令字面量 | #132 | 已合并 |
| D1-2 异常详情不落盘；D1-3 生产配置守护；D1-3b 管理端口放行 prometheus | #134 | 已合并 |
| R-1b P0 编号计数器 | #137 | 已合并 |
| 阻塞项 100（只读连接启动校验失效，`blocks: deploy`） | #138 | 已合并，ADR37-1 完成后删除 |
| ADR-0037 A8 与老系统库级只读、运行时可读任意表列 | #136 | 已采纳 |
| R-1b P3a 来源对照 | #139 | 在途：复审 1 High（`ConfirmationStatus` 同名，OpenAPI 取值写错）、5 Medium；M2 同日更正按用户「允许同日更正 (Recommended)」处理；M3 一个事务改两个同类聚合实例，作为 §14 例外（one-coordinator 2026-10-15 接受，写入 §14）；M5 不等 #129（登记 `migration_batch` 提议术语，`forbidden` 列已知后续，#137 后 rebase）；RULING-P3A-1（M-1 核算编码只由人工登记，CHECK + 夹具等价调整）进行中 |
| R-1b P1 | 无 | 验收测试已提交（分支 `claude/one-1a-next-2-r1b-p1`，事件负载标量 ID 3 条红、目录红）；账号类用例待 P2 以 `Acceptance-Ruling` 重写，workforce / identity 事件目录 yaml 与实际不一致须对齐；实现在 #139 合并后开始 |
| R-1b-0 A8 视图标志列 | 无 | WIP 提交 `f1a67d33`（分支 `claude/one-1a-next-2-a8-view-flags`，未推送；未登记 A8_ACCESS_REGISTER、未跑测试），按 7.4 作废或改写 |

### 7.2 R-1b 改按 P0–P6 拆分（one-coordinator 同意）

原因：卡上的 ①–④ 与 ADR-0033:210 的三段拆法，合入 main 后都有编译不过或 CI 失败的中间态——`UserId`（kernel 中为 long）同时充当员工 ID 与用户 ID；`openapi.json` 漂移检查（`.github/workflows/ci.yml`）要求 OpenAPI 与前端生成物随每个改了接口类型的子 PR 一起提交，不能留到最后收口。各子 PR 独立合入后全绿，沿用卡的验收与自查要求，每个子 PR 附预期表结构 diff。

| 子 PR | 范围 | 前置 | 执行 / 复审 |
|---|---|---|---|
| P0 | 只改 `numbering_counters`：`counter_scope_kind` + CHECK + 索引（DDL-03）。日历适用范围（`calendar_scope_kind`）并入 W2 日历版本化（DA-17；DA-17 重建日历明细表时须带走 `ck_work_calendar_days_make_up_weekend`，见 `data/repair-cards/README.md`），不改日历 API（时点偏离 ADR-0033 第 9 项第 2 条「这一项并入 R-1b」，终态不变；one-architecture 2026-10-16 判定，用户已追认（intent `2026-10-16-085333-one-architecture-收件箱-calendar-sco.md`）：随 W2 日历版本化完成，基线关闭前必须完成）；无验收测试，以表结构 diff 为证据 | — | 已合并（#137） |
| P1 | 语义拆分，不改库：kernel 新增 `EmployeeId(long)` 作过渡；员工语义的引用（`Employee`、工时单、发起人、`AccessScope.ownerIds`、`AttributionSubject`、`SeparationReview`）全部换成 `EmployeeId`；`CurrentUser.employeeId()`；pom 启用 Error Prone，加 ArchUnit「不比较 `.value()`」；G-C5 Jackson 扁平化，事件 yaml 去掉 `{value}` 一层 | #132（已合并）、#139 | sonnet / opus（high）；测试代理 opus |
| P2 | 用户 ID 底层改 UUID：`user_accounts.user_id` 改 uuid 并加 `employee_id`（过渡为 bigint，带唯一约束）；identity 的 `emergency_admins`、`account_roles`、`browser_identifiers`、`authorization_requests` 三个用户列，`ticket_issuers.key_id` 改名；其他 schema 中存用户 ID 的列一次改完（`idempotent_responses`、`audit_entries.actor_id / on_behalf_of_id`、审批任务与办理记录的人员列、`numbering_rule_versions` 的作者与确认人列、`sync_runs.released_by`、`legal_entity_attribution_corrections.corrected_by_user_id`）；`UserId(UUID)`，13 处 `Long.parseLong` 改 UUID 解析，`ensureAccount` 改 `ON CONFLICT (employee_id)`；路径 `{userId}` 5 条、`requestedBy` / `subjectUserId` | P1 | opus / opus（high）；体量最大，approval、identity 各 60 余个文件，内部按模块分提交；测试代理 opus（含安全闸门） |
| P3a | 来源对照：`source_references`、确认流程、`legacy_numbers` 改造、法人去核算组织编码 | — | 在途（#139） |
| P3b | 员工、部门、岗位改 UUID：workforce 的快照、版本、归属期间表及 `sync_run_held_separations` 加 uuid 主键并改引用列；归属表拆 `org_unit_id` / `employee_id`（DA-5）；`sync_diffs.subject_key` 拆为类别 + uuid；DDL-01 / DDL-06；`user_accounts.employee_id` 改 uuid；工时单与审批发起人的引用列；`EmployeeId` / `OrgUnitId` / `PositionId` 改 UUID；删 `Source*Id` 与 `SourceTerms` 的 ID 翻译；`JdbcLegacyInbox`、`ShortNameAdapter` 移入集成层；运维命令改 `--employee-id`；A8 来源 ID 可为负数；保持原约束名 | P1、P2、P3a；D1-1、R-1b-0（同改集成层适配器，先合并） | sonnet / opus（high） |
| P4 | 审批：`approval_tasks` 任务 ID uuid 主键与 `instance_id`；DDL-04；子表挂 `approval_request_id`；办理记录序号；`process_archive_skips`；DDL-08；`outbound_deliveries` 去重键拆列；Flowable idGenerator 接 UUIDv7；`ApprovalTaskId` / `ApprovalInstanceId` / `DocumentId` 改 UUID | P3b | sonnet / opus（high） |
| P5 | 收口：`audit_entries.subject_key` 拆为类别 + uuid；信封 `aggregateId` 与 `AggregateReference` 改 UUID；`OTHER_ID_NAMES` 清空；去哨兵守护；`PrimaryKeyTypeGuardTest` 扩到复合主键与 text 主键并加 `primary-key-exemptions.txt`；SQL `ORDER BY` 守护；G-A1 收尾 | P4 | sonnet / opus（high） |
| P6 | 老系统人员、部门对照的一次性导入（只写数据）。读取老系统明细须先经用户逐阶段批准（DATA_DESIGN_CHECKLIST §14.2 迁入读取授权，写明读哪些列、数据去向、删除时点），由调度会话请示；PR 只放计数与差异条数，不写 ID 明细 | P3b、用户授权 | 授权后定；opus 复审 |

子 PR 的独占构件：P1 独占 kernel `EmployeeId`、`CurrentUser`、`pom.xml`、扁平化 Jackson 模块；P2 独占 kernel `UserId`、`ContextualEventEnvelopes`；P3a 独占 `source_references`、权限码 `platform.integration:source-reference:update` / `:approve`、`permission-grant-modes.txt`；P3b 独占 integration 的 `OrgSource` 与适配器；P4 独占 `EngineWiring`、`DocumentId`；P5 独占 `EventEnvelope` 与全部守护文件。

**拆分图 C1–C11 的处理**（编号见 one-1a-next-2 的拆分图；C3、C4、C6、C7、C8 经 one-coordinator 同意由负责会话处理）：

| 编号 | 处理 |
|---|---|
| C1、C2 | 卡 §1 的 ①–④ 与 ADR-0033:210 的三段拆法作废，改用 P0–P6；OpenAPI 与前端生成物随各子 PR 提交 |
| C3 | P2 只改 `account_roles.user_id` 的类型（它是外键，须随 `user_accounts`）；角色授予的其余结构留给 W2（ADR-0034）。卡 §8「`primary-key-type-baseline.txt` 只剩 ADR-0034 两行」不变——P2 不改这两张表的主键 |
| C4 | 「本人」判定清单补入：工时单本人检查（提交、读取、取消、撤回四处）、`SessionCurrentUser` 用用户 ID 查员工法人、撤回时发起人的比较；P1、P2 各加反向集成测试（员工 ID 与用户 ID 取不同值）。补入卡的验收清单，PR 中单列 |
| C5 | 系统操作的 `actorId` 存的是作业名，而 ADR 要求 `actorId` 为 UUID：复用信封已有的 `actorType`（`USER` / `SYSTEM` / `MIGRATION`），不新增 `actorKind`；P2 做，先查 `proposals/EVENT_ANALYTICS_FIELDS.md` 与信封现有字段，作业名字段名查 `terms.yaml`，新词当场登记；方案由 opus 复审裁定 |
| C6 | 法人代码「发号后禁改」已实现（`UpdateLegalEntity`）：从 6.4 的 W2 删去，由 P3a 的 `LegalEntityCodeCorrectionIT` 作特征测试覆盖 |
| C7 | `kernel/event/SourceSystem`（取值 `ONE` / `MIGRATION`，表示事件来源）与新的「来源系统」撞名：新词用 `ExternalSource`，理由写在 `source_system` 词条（P3a 已登记） |
| C8 | P3b 起到 P6 完成前，老系统待办与部门简称返回「不可用」，接受这段降级 |
| C9 | Error Prone 与 JDK 25 兼容性未知：P1 先验证；许可证按 SUPPLY_CHAIN S4 核（Apache-2.0 可）；不兼容时由 ArchUnit 与反向测试兜底，并报调度会话 |
| C10 | P3b 重建约束时保持原约束名，否则 `ddl-naming-baseline.txt` 中对应行变成「已修好却没删」而越进 R-2a 的范围 |
| C11 | P2 体量最大：派 opus 执行，内部按模块分提交 |

**#129 带来的 R-1b 验收点落点**：

| 验收点 | 落点 |
|---|---|
| G-A1：路径与查询参数里的 ID 也是字符串；前端生成的 `validators.ts` 不再把 ID 定义为数字 | P2、P3b、P4 各自处理，P5 收尾 |
| G-C5：事件与出站负载扁平，不出现 `{value: …}` | P1 |
| G-MG1 / G-MG6：`legacy_numbers` 只追加、具名唯一约束、来源系统列 CHECK、`migration_batch_id uuid NOT NULL` 不建外键（跨 schema，列注释引 ADR-0036）；删除提议术语 `import_batch` 并在 `migration_batch` 的 `forbidden` 列出旧写法 | P3a（依赖 `migration_batch` 术语与 ADR-0036 合并，见 #139 M5） |
| 来源系统取值按 ADR-0033:99（组织源、老系统、核算系统）定名并登记术语 | P3a |
| 契约如实修正（201、`If-Match`、`ETag`、具名枚举、`info`）不属于 R-1b：ADR-0035 迁移方案第 1 条相应改措辞，归新卡 H-1（7.5） | — |

### 7.3 D1 的变化

| 卡 | 变化 |
|---|---|
| D1-3b | 管理端口安全链放行 prometheus，已在 #134 完成（见 `cards/D1.md`） |
| D1-6 | 新卡（A 4-M1）：`LoginAdmission`、`EnterAdministratorMode` 的告警行去掉客户端 IP，改引审计条目 ID；排在 R-1b P2 之后（同改 identity 文件） |
| D1-7 | 新卡（B R-P4）：交互事务 `SET LOCAL statement_timeout`；首次部署前 |
| D1-4 | 前置：#129 定下直方图桶（补 300 ms，建议 200 / 300 / 500 ms / 1 s / 2 s）与 Hikari 全部取值（含 `minimum-idle`）；dev profile 开 `leak-detection-threshold`。`pg_stat_statements` 与 `log_min_duration_statement` 是 RDS 参数，交 one-ops |
| D1-3 | 在 D1-4 之后扩到 G-P5：生产配置里连接池、`statement_timeout`、直方图桶、慢查询阈值都存在且取值正确；部署后的指标抓取检查交 one-ops |
| D1-1 | 提为 ADR37-1 的前置（7.4）：A8 与老系统连接只经工厂，强制查询、套接字、锁等待三种超时；同时承接 G-P4、G-I1、G-I3、G-I4、G-I2（四个现有集成入册）、G-I5。不再等 R-1b；P3b 在其后 |

### 7.4 ADR-0037 实现卡（ADR37-1）

ADR-0037 已采纳（Q1 选 (c)：现有 6 个视图冻结不动，新读取与 R-1b-0 的标志列直接读基表，适配器改到时再迁，最后一个读者迁走后删视图）。风险处理用户已定「接受并登记风险，加校验与轮换 (Recommended)」（经 one-coordinator）：阿里云 RDS 不允许服务器级 DENY；SQL Server 2016 库级不能 DENY `ALTER ANY DATABASE EVENT SESSION` / `KILL DATABASE CONNECTION`（错误 4631），其来源是服务器角色 `public` 被授予 `ALTER ANY EVENT SESSION`、`ALTER ANY CONNECTION` 等。

**排序**：D1-1 → ADR37-1（合并后删除阻塞项 100）→ 凭据列清单（one-ops，#143）→ R-1b-0 → P3b。

**ADR37-1 范围**：重写 A8 `ReadOnlyGuard` 与老系统 `SingleObjectGuard`、`LegacyAccessGuard` 为同一套校验，调整白名单守护。判定口径（来自 one-coordinator 与 one-ops 用三个运行时账号与取证账号真实登录导出的基线；四个账号基线相同）：

- 服务器级有效权限（`fn_my_permissions(NULL,'SERVER')`）**等于**平台基线，逐项写死：`ALTER ANY CONNECTION`、`ALTER ANY EVENT SESSION`、`ALTER ANY LINKED SERVER`、`ALTER ANY LOGIN`、`ALTER SERVER STATE`、`ALTER TRACE`、`CONNECT SQL`、`VIEW ANY DATABASE`、`VIEW SERVER STATE`；多出即拒绝启动。
- 库级有效权限**等于**基线，逐项写死：`CONNECT`、`KILL DATABASE CONNECTION`、`SELECT`、`SHOWPLAN`、`VIEW ANY COLUMN ENCRYPTION KEY DEFINITION`、`VIEW ANY COLUMN MASTER KEY DEFINITION`、`VIEW DATABASE STATE`；A8 无单列 `VIEW DEFINITION`（不加）。
- 库级（class=0）`DENY` 正向存在且恰含 11 项：INSERT、UPDATE、DELETE、ALTER、EXECUTE、REFERENCES、CREATE TABLE、CREATE VIEW、CREATE PROCEDURE、CREATE FUNCTION、CREATE SCHEMA；库级 `GRANT` 恰好 CONNECT、SELECT。
- `sys.user_token` 只有 `public`；账号不拥有任何 schema、对象、角色。
- `HAS_DBACCESS` 为 1 的库恰好是目标业务库加 master、msdb、rdscore、tempdb（guest）。
- 无法判定（查询失败、结果为空）即拒绝启动；不提供关闭开关（`*_VALIDATE_ON_STARTUP` 不再可关，`ProductionConfigSanityTest` 断言）；`LegacyInboxConfiguration` 不再吞掉数据库异常继续启动。
- 每次同步任务开始前重跑同一校验，失败即停掉该适配器并告警。
- 凭据列拒绝清单守护：以 `credential_columns.csv`（one-ops，#143）为准——credential 92 列（A8 75、老系统 17）；review 25 列（均为 A8），实现 PR 逐列判定是否凭据。守护断言 `src/main` 的 SQL 与外部读取登记都不引用清单里的凭据列。清单只含表名与列名，不含值。
- 适配器出错只记错误号与 SQLState，不记异常消息（SQL Server 的类型转换错误消息会带出字段值）。
- MSSQL 集成测试（SQL Server）进 CI：改到 `platform.integration..` 或 `platform.approval.infrastructure.legacy..` 时按路径过滤运行，另加每夜一次全量；并按 architecture-critic B-2 逐项构造越权反例，范围以 ADR-0037 守护节为准。起 SQL Server 测试容器前先向 one-coordinator 确认在 Testcontainers 授权范围内。
- 实现 PR 注明 SQL Server 2016 版差异、RDS 平台限制与已登记风险，ADR 正文不改。
- 执行与复审：测试代理 opus 先写；执行 opus（含安全闸门）；复审 opus（high）。对外库的真实登录核实按 CLAUDE.md 先请示，未获准前以容器内集成测试为证据。

**R-1b-0**：A8 标志列（`TYPE`、`IS_DELETED`、`IS_ENABLE`、`STATE`）直接读基表，WIP 提交 `f1a67d33`（视图加列、白名单 37 列）作废或改写；基表一律写 `dbo.` 限定名；外部读取登记只留一份并加「用途」列，启动时只检查运行时用途的行。one-ops 取证（只出分组计数，未入仓）显示同步范围 1291 条中有 18 条标记已删除；现行同步范围（`MEMBER_SCOPE`）不看删除与启用标志，已删除或停用人员进入快照，处理见 7.8。

**完成后**：删除 `status/blockers/100-ADR-0037-readonly-guard-before-deploy.md`。

### 7.5 新卡（来自 #129 核对与复审）

执行者与模型在派工时写进卡，复审用 opus；未标「首次部署前」的卡在 Phase 1b 前完成。

| 卡 | 内容 | 前置 | 时点 |
|---|---|---|---|
| H-1 契约如实 | OpenAPI 如实：创建返回 201 + `Location`、修改带 `If-Match` / `ETag`、具名枚举、`info`（ADR-0035 迁移方案第 1 条的“如实修正”部分）；G-A3、G-A4、G-C4、G-A5、G-A9 | R-5 之后（OpenAPI 稳定后）；G-A9 在 D1-2 之后 | 首次部署前（G-A3、G-A4、G-C4） |
| C-1 契约演进守护 | G-C1、G-C2、G-C3、G-C6；G-C3 接入部署剧本交 one-ops；`migration_safety.py` 与 G-C1 共用「读基线开放标记」逻辑 | R-1b、R-5 之后 | 首次部署前，W4 删除基线开放标记之前 |
| O-1 可观测性 | G-O1、G-O2、G-O3、G-O5、G-O6；ALERT 前缀与 correlationId；并入 #134 复核 M3：非 HTTP 路径记完整消息与堆栈（`ScheduledJobMetrics`、`SyncOrgSnapshot`、`JdbcLegacyInbox`、`LegacyInboxConfiguration`、`MigrateOnlyListener`、`AuditArchiveJob`、`ProcessArchiveJob`、Spring 定时 / 异步默认处理器、Flowable 作业异常入库），测试：定时任务或监听器抛带标记值异常，日志不含标记；`GlobalExceptionHandler.refused()` 以 info 记 `refusal.getMessage()`，法人编码重复时消息含编码值落盘（非个人信息，D1-2 测试代理发现），统一裁定业务拒绝日志是否只记错误码 | D1-2（已合并）共用日志捕获；4-M1 由 D1-6 承接 | 首次部署前 |
| P-1 性能守护 | G-P2、G-P3（查询路径登记注册表，EXPLAIN 走索引） | K-3、注册表登记查询路径 | Phase 1b 前 |
| S-1 CI 供应链 | G-S1 至 G-S4；G-S5 交发布链 / one-ops（生产前）；G-S6 待用户决定（用户暂未选给 runner 建专用用户）；G-S4 只改测试基类，避开 R-1b | 与 W1 文件基本不相交 | 首次部署前（G-S1 至 G-S4） |
| FE-1 设计系统 | F15、F16、F17、F18、F20、F21、F22；F15 / F16 在 D-8 首次 design-sync 前，其余 Phase 1b 前 | — | 见左 |

**§4 待实现守护的归属**（★ = 首次部署前必须；依据 #129 核对第三节）：

| 守护 | 归属 | 前置与理由 |
|---|---|---|
| G-A1 ★、G-C5 ★、G-MG1 ★、G-MG6 ★ | R-1b | 改的都是 R-1b 那一批 ID、负载与基线（G-C5 在 P1，G-MG* 在 P3a） |
| G-D12 ★ | R-2a | 枚举 CHECK 命名 `ck_<表>_<列>`，取值集合与 Java 枚举一致 |
| G-A8 ★ | R-5 | 与 G48 同一实现 |
| G-A2 ★、G-P1 | K-3 | 分页契约与上限常量本来就归 K-3 |
| G-A6 | K-1（金额，与 G-M5 合并）+ H-1（时刻） | Phase 1b 前 |
| G-A7 ★ | DA-13 | 与 G-X6 互补；前置 R-1b |
| G-A3 ★、G-A4 ★、G-C4 ★、G-A5、G-A9 | H-1 | R-5 之后；G-A9 在 D1-2 之后 |
| G-C1 ★、G-C2 ★、G-C6 ★、G-C3 ★ | C-1 | R-1b、R-5 之后，W4 删基线开放标记之前 |
| G-C7 | R1 | — |
| G-P4 ★、G-I1 ★、G-I3 ★、G-I4、G-I2 ★、G-I5 ★ | D1-1 | 同一个连接工厂与适配器；四个现有集成入册 |
| G-P5 ★ | D1-3 | 前置 D1-4 |
| G-P2、G-P3 | P-1 | 前置 K-3 与注册表登记查询路径 |
| G-P6 | Phase 2 入口 | 安装 k6 先请示用户 |
| G-O1 ★、G-O2 ★、G-O3 ★、G-O5 ★、G-O6 | O-1 | 前置 D1-2；4-M1 并入 D1-6 |
| G-O4 ★ | D1-3（已含） | 部署后抓取检查归 one-ops |
| G-S1 ★ 至 G-S4 ★、G-S5 | S-1 | G-S5 生产前，交发布链 / one-ops |
| G-S6 | 待用户决定 | — |
| G-MG2 至 G-MG5、G-MG7 | 首个迁入阶段 | ADR-0036 |
| F19 ★ | F-3 或 F-4 | 应用根部配置 |
| F15、F16、F17、F18、F20、F21、F22 | FE-1 | 见上 |

首次部署前必须的守护共 28 项：G-A1 / A2 / A3 / A4 / A7 / A8、G-C1 至 C6、G-P4 / P5、G-S1 至 S4、G-O1 至 O5、G-I1 / I2 / I3 / I5、G-MG1 / MG6、G-D12、F19。另有两项首次部署前必须、但不在表里的：DDL-26 与 DDL-27（7.6）。

### 7.6 既有卡的变化

- **R-2a**：G-D12 枚举 CHECK 命名（用户已采纳）。
- **R-2b**（并入）：kernel `Reason` 上限 500（含 OpenAPI `maxLength`，DDL-20 的 kernel 部分）；同一对象至多一个待确认授权申请（部分唯一索引）；工时单同员工同工作日唯一。后两项先在规则卡登记（引 intent `2026-10-15-020346-one-coordinator-DDL-审计-原则核对-A-B.md`），再改基线与测试。
- **R-5**：G-A8；并发冲突一律 412，409 只表示「当前状态不允许」（A 2-H4）。
- **DDL-26 / DDL-28**：DDL-27 并入——Flyway `lock_timeout` 5 s、`statement_timeout` 15 分钟；`migration_safety.py` 与 G-C1 共用「读基线开放标记」逻辑。首次部署前必须。
- **DDL-07+24**：审计列表用 `{items, nextCursor}`，上限 200；导出不超过 10,000 行、10 s，否则改异步（R-P2 / G-X5），去掉导出前的全表 `count(*)`；审计列表查询路径登记注册表并过 EXPLAIN 走索引检查；分区作业失败按 P3 告警。
- **DA-3 / DA-10 / DA-8 / DA-13**：DA-3 企业微信 userid 与登录名不进日志（O-3），RULE-363 修订（STATE 替代「登录名为空即离职」）随 DA-3，source 引 intent `2026-10-15-020346` 与 DA-3；DA-10 适配器满足 I1 / I4 / I5 / I11，企业微信入集成登记册（G-I2）；DA-13 A-11 掩码与 G-A7（`AuthorizationRequestView` 改成 `*Response`）；DA-8 不受影响。
- **K-3**：审批列表上限从 100 统一到 200；去掉内存分页（`PageRequest`）；排序与过滤用白名单，未知参数返回 400，提供 `:count`；不分页的小表硬上限 1,000 行，超出失败并告警；批量上限 100 作为 kernel 常量，导出 10,000 行、导入 5,000 行上限。
- **W2**：新接口创建返回 201 带 `Location`、`ETag`，修改必带 `If-Match`；版本表查询路径登记；K-1 负责 G-M5 与 G-A6 的金额部分，比率等十进制量用字符串。6.4 中法人代码的条目按 C6 删去。
- **W3**：F-1 范围外单资源 404、列表按范围过滤、范围不作请求参数（A-10）；F-4 的 ID 用字符串，界面显示可复制的关联号、antd 中文（F19）、H5 读屏角色三项首次部署前；F-5 批量用 `:动作-batch`，≤100 项、逐项结果、部分成功返回 200；F-6 满足 I4 / I5 / I11 与 ALERT 前缀。

### 7.7 #129 核对中仍需上游处理的冲突（不在本会话范围）

供 one-coordinator 在 #129 合并前处理，本节只记结论：D1-4 桶与 Hikari 取值（见 7.3）；ADR-0036 的编号约束改为「编号非空，或迁入批次非空，或状态为草稿」（以 ADR-0031 为准）；ADR-0036:32 的来源系统建议名删去、只引用 ADR-0033:99；GUIDING_PRINCIPLES:121 未完成事件发布告警阈值由 1 天改 10 分钟，需用户确认；用户第 2 批三项决定的落点见 7.6；`migration_reconciliation` 的 Reconciliation 与已批准术语「经营勾稽」撞词、`period_status__migrating` 须走用语确认；`migration_batch` 的 `forbidden` 补 `import_batch`、`ImportBatch`、「导入批次」；路径用的 `batch`、`count` 加入通用词表；ADR-0035、ADR-0036 尚缺 architecture-critic 评审。

### 7.8 小余项（下一批小卡，同文件者合为一个小 PR）

| 来源 | 内容 |
|---|---|
| #119 Low 4 条 | `build_all` 说明过时；浅克隆回退告警；push 自比；`--base ""` 与 `GITHUB_ACTIONS=false` |
| #134 复核余项 | 端口相同时 prometheus 须登录的测试（M）；注释措辞（L）；`ProductionConfigSanityTest` 加管理端口≠主端口断言，并按 import 顺序取值（L4）；`ErrorLogDetailIT` 改用测试端点（L3）、第 2 / 3 条只接受 401；F-12：`identity-operations` 档案设 `management.server.port=-1` |
| #139 Low-3、Low-4 | 列表准入借用 `isRegistrationAllowed`，PR 中说明两者同义；不按状态的列表无索引（行数少），规则卡写「为何不建」 |
| `TestIsolationGuardTest` | 把 `acceptance/<子域>/…` 当作 `acceptance_<子域>` schema，验收测试带 schema 前缀的 SQL 被判跨模块（P3a 的测试代理以 `set search_path` 回避）。须修映射：验收目录按其下模块包映射 schema；负责方由 one-coordinator 定 |
| 只追加表的 `updated_at` | 保留并加注释；统一去掉另开 PR，由 one-coordinator 定 |
| A8 同步范围 | `MEMBER_SCOPE` 不看删除 / 启用标志，已删除或停用人员进入快照；R-1b-0 之后的同步卡或 DA-3 处理 |
| RULE-363 | 修订随 DA-3（7.6） |
| R-1b P2 复核 L-1（one-1a-next-3） | 系统操作主体目录 `system-actors.txt`「改名新增一行、旧行不删」加守护：CI 以 `git show origin/main:backend/architecture/system-actors.txt` 校验旧行仍在；落点 P5（P5 独占全部守护文件） |
| R-1b P2 复核 L-8 | `SnapshotFreshness.cycleOf` 只取 cron 第一个间隔，间隔不均匀的 cron 会算错（现行每 5 分钟，无影响）；落点 P5 或首次改同步 cron 的卡 |
| R-1b P2 复审 P2-L2 | `primary-key-type-baseline.txt` 中 `account_roles` 理由「the user id is the employee id」已过时；落点 W2（ADR-0034 实现卡） |
| R-1b P2 复审 P2-L12 | 前端测试桩数据 `userId: '1001'` 仍为数字形态；落点 P5（G-A1 收尾） |
| R-1b P2 卡外缺陷 | `OperationsCommands` 用 `UUID.randomUUID()` 作关联号，违反 ADR-0033 第 1 项第 11 条；手术式小卡 OPS-CID，P2 合并后 |
| R-1b P3b RULING-P3B-9 | C1 落地后按身份模型删除或改形 kernel 端口 `ExternalSubjectEmployees`（集成层只读运维命令按外部主体查快照员工，workforce 实现）及其在 `module-dependencies.txt` 的登记行（DA-3 时复核是否仍需要） |
| #184 opus 点检 Low | `review_revisions.py` 的 `GITHUB_ACTIONS == "true"` 补自测（true 且无基线失败、false 只告警）；空 `--base` 被拒时先输出一行针对性错误；与 #119 余项同文件，下一张文档工具小卡 |

### 7.9 子代理工作区约定（运行环境限制，2026-10-15 起）

运行环境不允许子代理写负责会话 worktree 以外的目录。子代理一律用隔离 worktree（`Agent` 的 `isolation: worktree`），在其中 `checkout -B <本地名> origin/<PR 分支>` 并提交；负责会话先确认快进，再从自己的 worktree `git push origin <sha>:refs/heads/<分支>`。早先建的 `one-1a-next-2-*` worktree 用完清理。

### 7.10 更新后的执行顺序

```
R-1b 线：#139 修订并合并 → P1 → P2 → D1-6 → P3b → P4 → P5 → P6（需用户授权）
ADR-0037 线：D1-1 → ADR37-1（删阻塞项 100）→ 凭据列清单（one-ops，#143）→ R-1b-0 → （接 P3b）
D1 线：D1-4（待 #129 定桶与 Hikari）→ D1-3 扩 G-P5；D1-7
其后：R-6、R-2a、R-2b（并 Reason 500 等）、R-5、R-4、DDL-07+24、DDL-26/28（含 DDL-27）；H-1、C-1、O-1、S-1 与 6.4 的 W2、W3 衔接
```

两条线文件基本不相交，可并行；P3b 在 D1-1、R-1b-0 之后（同改集成层）。

### 7.11 ADR-0039 之后的 R-1b 排序（one-1a-next-3；one-coordinator 2026-10-15 采纳「甲′」）

> 与 7.2、7.4、7.10 不一致处以本节为准。论证（文件交集、每张表只改一次）见负责会话私有证据 `~/.modernize/psa/handoff/one-1a-next-3/p3b-sequencing.md`。

顺序（用户 2026-10-15「先保 dev（推荐）」，intent `2026-10-15-214511-one-coordinator-先保-dev-推荐.md`，取代原「甲′」顺序）：**R-1b-0a → P3b → P4 → P5 → F-1**；组织同步拆分 b → 拆分 a → R-1b-0b → DA-3 移到 F-1 之后、首次生产部署前。原顺序为 R-1b-0a → P3b → 拆分 b → 拆分 a → R-1b-0b → DA-3 → P5。

理由：先把 dev 部署所需的 R-1b 收口与 F-1 做出来，拆分及其依赖项（依赖反转成环的设计卡、新词门禁）不挡关键路径；代价是组织同步几张表可能改两次，用户已接受。其后用户又定阻塞项 090 不挡 dev 首次部署、改为白名单外开放前与生产前完成（intent `2026-10-15-214650`），F-1 与 P3b–P5 已不在 dev 关键路径上，按质量节奏推进；F-1 仍按用户原定在 P5 合并后开（intent `2026-10-15-124901`），执行者 one-1a-par。

随之调整（one-coordinator 2026-10-15 认可）：

| 项 | 调整 |
|---|---|
| P5 守护收口 | 两处保留例外、到期改为拆分（`sync_run_held_separations` 复合主键改为 P5 照改，one-architecture 判定不得登记基线，P5 卡 RULING-P5-D12）：`sync_diffs.subject_key` 混装主体键登记为 `OTHER_ID_NAMES` / 去哨兵守护的例外（拆分 a）；`ddl-naming-baseline.txt` 中 `sync_runs`、`sync_diffs` 的约束与索引改名行（拆分 a）。P5 只拆 `audit_entries.subject_key` |
| K-3b-2 | 只做部门与法人列表；`HELD` 同步列表的上限随拆分 b（该表在拆分 b 改造） |
| F-1 | 「调岗或离岗生成待调整权限待办」只依赖领域事件、不依赖同步内部类，避免拆分 b 后搬监听点（写进 F-1 卡） |
| P4 | 不依赖拆分或 DA-3 |

下表为 ADR-0039 之后「甲′」的各项决定，除顺序外仍有效。

| 项 | 决定 |
|---|---|
| ADR-0039:41「P3b 直接写到新 schema」 | 解释为：同步三表移出 P3b，由拆分按最终形态新建；P3b 只改 `sync_run_held_separations.employee_id` 的类型（外键指向 `employees.id`，无法避开）。决定本身不变（表只改一次、直接在最终位置）；该句与「3 个权限码」「5 个事件」计数由 one-architecture 在 ADR-0039 中加勘误说明 |
| P3b 不等拆分 | P3b 本就要经来源对照分配自有 ID（ADR-0033 第 2 项第 3、4 条），不依赖拆分；拆分须先解决依赖反转成环（integration → workforce 与 audit → integration）、两事务一致性与新词门禁，前置会拖住 P3b → P4 → P5 → F-1 |
| 表的位置 | P3b：workforce 快照、版本、归属期间与法人归属（DA-5 分列）、`user_accounts.employee_id`、`timesheets` 三列、审批 `initiator_id`；拆分 b：`sync_run_held_separations` 改为 workforce 待放行离职表、`last_sync_run_id` 改为引用变更集；拆分 a：`sync_runs`、`sync_diffs` 在 `platform_integration` 按最终形态新建（`subject_key` 拆列），`ddl-naming-baseline.txt` 同步表行直接删、新表合规命名（C10 例外）；DA-3：`employees` 删登录名与企业微信列；P5：`audit_entries.subject_key` |
| 拆分内部顺序 | b 先 a 后：a 先则离职暂停仍挂在运行上，集成层须知道「谁是管理员」（被否决的 A2） |
| R-1b-0 拆为 0a / 0b（重开 C-10） | 0a（外部读取登记合并、`dbo.` 限定、启动时基表子集检查、`TYPE` 校验、三项守护 = D-1a-01 全部）现在做、P3b 前；0b（`STATE` / `IS_DELETED` / `IS_ENABLE` 读取、两项不一致判定记在集成层同步差异、RULING-R1B0-5）排在拆分 a 后。理由：按 ADR-0039 不一致判定的输入都是 A8 概念，只能在集成层，而同步差异要到拆分 a 才在集成层；不拆则 D-1a-01 要晚到拆分 a 之后 |
| 新词 G06 / G07 | 撤下，随拆分 a 与集成层新词一并确认；G01–G05 照常确认（0a 只用 G01–G03） |
| 拆分前设计卡 | one-architecture：依赖反转成环、登录查人改为 workforce 定义端口 / 集成层实现、D-1a-03「单事务」条款改写（不再问用户：用户选 A1 时已批两事务与组织同步整体搬） |

## 8. 2026-10-15 追加：部署前开发编排调整（one-1a-par）

> 与第 1、6、7 节不一致处以本节为准。

依据：用户 2026-10-15「部署前开发编排三项都采纳：加一个并行工程会话；dev 部署与结构重构解耦（基线 dev 期间保持开放、生产前关闭）；F-1 提前到 R-1b 收口（P5）后就开」（经 one-coordinator，intent `2026-10-15-124901-one-coordinator-部署前开发编排三项都采纳-加一个.md`）。

| 项 | 原安排 | 现安排 |
|---|---|---|
| 工程会话 | 部署前开发全在 one-1a-next-2 一条串行链上 | 加并行工程会话 one-1a-par，承担与 R-1b 在途子 PR 文件不相交的卡（下表）；one-1a-next-2（及其接任者）继续独占结构重构主线（R-1b P2→P3b→P4→P5，其后 R-6、R-2、R-5、R-4）与 #163（ADR37-1） |
| W4 基线收口 | 首次部署 dev 前删除基线开放标记（C-3 第一步） | 改为**首次生产部署前**删除（PREDEPLOY_CHECKLIST C-15；依据 ADR-0038（已采纳，部分取代 ADR-0033 迁移方案第 5 条等））；dev 期间基线保持开放，基线文件改动后 dev 库与服务器发布状态一起重置（one-ops 补 dev 库重建剧本 `runbooks/dev-db-rebuild.md`，PREDEPLOY_CHECKLIST C-16）。`deploy_gate.py` 对 prod 拦截（main 与发布包所在提交都查）、对 dev 放行并告警；SESSION_PLAYBOOK 附 M3「迁移基线」与「已应用」的定义随之修订 |
| F-1 数据范围（阻塞 090） | W3，前置 R-1..R-4 与 K-3 | 前置改为 **R-1b P5 合并 + K-3**；验收测试先由测试代理写（one-1a-par 卡 4），实现在 P5 合并后开，执行者届时由 one-coordinator 定 |
| C-1 契约演进守护 | 「W4 删除基线开放标记之前」 | 改为首次生产部署前、C-15 之前 |

**one-1a-par 的卡**（按序；每张卡开工前向结构重构主线的负责会话确认 7.2 末的独占构件）：

| 卡 | 内容 | 前置 |
|---|---|---|
| PAR-1 部署守护解耦 | `deploy_gate.py` 基线开放标记只拦 prod（含发布包所在提交），dev 告警；`--self-test`、RELEASE.md §3、PREDEPLOY_CHECKLIST C-15 / C-16、C-3 剧本第 0 步、DEV_DEPLOY_REMAINING 0-3、SESSION_PLAYBOOK 附 M3「迁移基线」、CS-3.5、本节；守护文件改动，opus 复审。安装器 `remote_install.sh` 对「两版之间迁移文件被改」仍拒绝安装，dev 解耦在安装层靠重建剧本重置发布状态（one-ops 定改法） | ADR-0038 采纳后合并 |
| PAR-2 ADR-0037 只读核实命令 | 对指定连接跑与启动校验同一套判定（`ReadOnlyBaseline`），逐项输出 (a)–(g) 标记与错误号，不输出数据或消息原文；进签名发布包；默认只读、不启动 Web、不跑迁移、不起调度；口令只从 ECS env 文件读；退出码区分通过 / 违规 / 判定不了；合并后通知 one-ops、one-coordinator 出 rc.2（C-14 前提） | #163 合并 |
| PAR-3 FLW-1 | Flowable 引擎时间来源核实（第 3 节，首次部署前须有结论），opus 核实，结论写规则卡与 INFRA_RUNBOOK；需改动按缺陷卡手术式修 | — |
| PAR-4 F-1 验收测试 | 测试代理（opus）按 ADR-0026「1a 交付范围」与 2.4 F-1 行先写验收测试 | — |

**K-3 拆分与裁定**（one-coordinator 2026-10-15 裁定；现状梳理：15 个列表接口中 6 个 SQL 键集、6 个全取后内存切页、2 个裸数组无上限、收件箱合并后内存分页；无未知参数 400、排序白名单、`:count`、OpenAPI `maximum`）：

| 卡 | 内容 | 前置 / 时点 |
|---|---|---|
| K-3a 公共契约与守护 | kernel 常量（`limit` 默认 50 / 上限 200、批量 100、导出 10,000、导入 5,000、不分页小表 1,000）；游标编解码；`sort` 白名单解析；小表上限断言（超出失败并 ALERT）；`PageRequest.slice` 标弃用；platform.web 未知参数 400 拦截器（排除 springdoc、actuator、非 `/api`）与 OpenAPI `limit` 的 `minimum: 1` / `maximum: 200`；新增守护类 G-A2（按响应形状判 `{items, nextCursor}`）、G-P1（kernel 外无上限常量、OpenAPI 声明 200），现存缺口登记于 `backend/architecture/list-paging-gaps.txt`（每行：路径、负责卡、期限）；DDL-11 测试容器 initdb 排序统一为 C.UTF-8（与 dev 库一致，one-ops C-3；与 S-1 G-S4 协调）；G-X2 前端 `localeCompare` 带 `'zh'` 守护与后端约定；工作日历年份列表试点 | 现在；测试代理先写验收；新增守护类开工前征得 one-1a-next-3 同意，PR 单列守护文件改动，opus 复审 |
| K-3b-1 identity、numbering、integration 接入 | 授权申请、编号规则、老编号 `documentId` 分支改 SQL 键集或登记小表 | R-1b P2 之后 |
| K-3b-2 workforce 接入 | 部门列表、`HELD` 同步列表套小表上限；`status` 具名枚举与白名单；法人列表键集 | R-1b P3b 之后、F-1 实现之前 |
| K-3b-3 approval 接入 | 三个列表响应统一、收件箱计数改 `:count`、删 `PLATFORM_APPROVAL_PAGE_INVALID`（上限 100→200 已在 K-3a #190 提前做，one-coordinator 裁定复审 H-1 选 (a)） | R-1b P4 之后 |
| K-3b-4 audit 接入 | 本人登录记录分页；导出与列表同一组过滤参数（A-4）；导出上限 50,000→10,000、去掉导出前 `count(*)`（dev 前，运行正确性）；删 `AuditQuery.MAX_LIMIT` 与 `QUERY_INVALID` | R-1b P5 之后 |
| K-3c 其余共用件 | 事件消费幂等、共用导出控制、日期边界测试夹具（另卡）；共用脱敏并入 DA-13（A-11 掩码） | 另排；F-1 只依赖分页约定 |

裁定要点：
1. 游标**不签名**：游标只决定从哪里接着取，数据范围在 SQL 中强制，伪造游标拿不到范围外数据；签名要引入密钥管理，进程内生成的密钥每次重启都会让游标失效，收益不抵成本（CS-1.8）。游标保持不透明、带格式版本，严格校验结构与类型，不合格 400。
2. `:count` 返回 `{count}`，到 10,000 时 `{count, capped: true}`；按需提供，不每个列表都开。
3. 未知参数 400 复用 `PLATFORM_WEB_REQUEST_INVALID`，detail 带参数名。
4. 收件箱新旧待办合并登记为例外，上限 200，P6 后再议。
5. DDL-11 测试库用 C.UTF-8（生产若不同再报）；G-X2 先只做前端加后端约定，不做 SQL `COLLATE` 守护。
6. 审计导出上限与去 `count(*)` 归 K-3b-4（dev 前）。
7. 拆出 K-3c（见上表）。
8. 缺口登记文件为 `backend/architecture/list-paging-gaps.txt`；新增守护类不改现有守护文件，视为与 P5 不冲突。

顺序：K-3a → K-3b 各块跟随 R-1b 子 PR → P5 后 F-1 实现与 K-3b-4 可并行（文件不相交）→ P-1（G-P2 / G-P3）→ DDL-07+24。

**后续项**：

| 卡 | 内容 | 前置 | 来源 |
|---|---|---|---|
| FLW-2 BPMN 不用引擎计时器与到期 | 守护：流程定义（`processes/**.bpmn20.xml`）不得含计时器事件与 `flowable:dueDate`，时限只由 `approval_tasks` 与定时作业判定（RULE-386）；执行者到时定，opus 复审 | R-1b P4 之后（P4 独占 `EngineWiring`） | FLW-1 复审 L-6；one-coordinator 2026-10-15 排卡 |
| OPS-PORT 运维命令档案不起 Web（结构性） | `identity-operations` 档案真正不起 Web：单独装配不依赖 Web 应用类型的 JDBC 会话仓库，控制器、安全链与 `CallerUserIdResolver` 等 Web 配置不进该档案上下文；`OperationsProfileSmokeTest` B1-R3 改为断言没有 WebServer（更严，PR 单列）；回归测试 `OperationsProfileListensOnNoPortTest`（分支 `claude/one-1a-par-ops-port`，先红已提交）。起因：one.env 的 `SERVER_PORT` / `MANAGEMENT_SERVER_PORT` 环境变量优先于档案文件；保留 servlet 设 `port=-1` 时 Tomcat 仍开随机端口（B1-R3 断言名不副实，已先改说明）。dev 前兜底：C-5 运行写法加 `UnsetEnvironment` 两端口变量（one-ops），`SERVER_ADDRESS=127.0.0.1` 使随机端口只绑回环；开工前向 one-1a-next-3 确认独占构件、按术语门禁列新词 | R-1b P2 合并后 | one-1a-par 发现；one-coordinator 2026-10-15 裁定方向 A、标结构性 |
| 数据范围后续维度与能力（F-1 规则对照登记） | 利润中心作为数据范围维度（ADR 登记，与法人同构，随首个带利润中心列的财务事实）；项目角色访问（项目线与职能线取并集，至迟与首个项目事实同期）；带到期日的临时授权；外部顾问 / 分包人员只许项目范围且有到期日；长期休假暂停访问（依赖人事状态）；定期复核上线前，生产前每月出授权历史报表 | 各自阶段，交 one-rules 起草、用户排期 | `phase1a/cards/F-1-aec-check.md`；one-coordinator 2026-10-15 |

**F-1 规格裁定**（one-coordinator 2026-10-15 裁定，验收测试据此写，分支 `claude/one-1a-par-f1-acceptance`，与实现同一 PR 合并）：
- 自定义部门明细挂在角色版本上，归 `role:update`（已采纳 ADR-0034 第 5 项）；2.4 F-1 行「授予时选范围的权限沿用 `account-role:update`」写于 ADR-0034 采纳前，以 ADR 为准；contract D-1a-58 在 1a 范围内由 ADR-0034 解决。
- 自定义部门逐部门选「含下级」或「仅本部门」，组织调整新增的下级部门对「含下级」自动纳入（用户 2026-10-15，经 one-coordinator）。
- 待调整权限的「保留」不复用已有术语 keep（数据保留期限），改用提议术语「维持授权」uphold（动作 `:uphold`、状态 `UPHELD`），进第三轮用语确认；ADR-0032 影响面的审计动作 `permission-adjustment.kept` 在实现中为 `upheld`，**F-1 实现 PR 须按 `adr/src/_readme_deviations.md` 登记这条用语偏差**（已采纳 ADR 正文不改）。
- 本人不能处理关于本人的待调整权限条目，接口层拒绝（职责分离，与 ADR-0032 收件人排除本人一致）。
- 人员详情用新权限码 `organization.workforce:employee:read`，按敏感权限分级登记。
- 验收未覆盖、实现卡必做并补测试：祖先部门导航（RULE-353）、超期 5 个工作日升级与无收件人告警、HELD 期间不生成、按时刻还原授权历史、注册表与 ArchUnit 守护。
| PAR-5 K-3 | 列表分页契约统一（游标、上限 200，A 2-H1 / 2-H2）与 DDL-11、G-X2；与 R-1b 在途文件冲突则排后 | 与 R-1b 在途文件不相交 |
- 用语：第三轮用语确认（用户全部同意建议）已批准 F-1 验收分支的 12 条提议术语（uphold、include_descendants 等），由 one-glossary 写回正式词表；**F-1 实现 PR 合并前 rebase 到写回之后的 main，与 one-glossary 对名单后从 `backend/glossary/proposed/f1-data-scope.yaml` 删去这些条目**。改名卡 ID-22（`glossary/round3/RENAME_CARDS.md`）随 F-1 实现一起改。
- 开工前术语门禁（SESSION_PLAYBOOK 附 M6，#195）对 F-1 已满足（12 条已确认）；其后新卡在测试代理写验收时列「本卡新词」交 one-glossary，经用户确认后再派实现。

**依据 ADR**：ADR-0033 迁移方案第 5 条等以「首次部署 dev」为界的时点，由 ADR-0038（已采纳，部分取代 ADR-0033 迁移方案第 5 条等） 改为生产前（取代范围见 ADR-0038 标题行与第 4、5 项）；本节的 W4 时点与 PAR-1 以其为依据。

**「首次部署前必须」各项的重新划分**（one-coordinator 口径，2026-10-15：dev 前仍须完成的只有安全与正确性类；标识形态与命名类随基线移到生产前，即 C-15 之前）：

| 项 | 原时点 | 现时点 | 说明 |
|---|---|---|---|
| 阻塞 090：F-1 数据范围 | 首次部署前 | **dev 前** | 前置 R-1b P5 + K-3；验收测试先写（PAR-4） |
| R-1b P1–P5 | W1 | **dev 前**（作为 F-1 前置） | 本身是标识形态类，因 F-1 依赖 P5 而实际在 dev 前完成 |
| K-3（G-A2、G-P1） | W2 | **dev 前**（作为 F-1 前置） | 同上 |
| 阻塞 100：ADR37-1（#163）与 C-14 | 首次部署前 | **dev 前** | 只读账号启动校验与核实 |
| F-12 单人运维例外 | ★ | **dev 前** | dev 上单人才能完成 C-5、编号确认等两人操作 |
| FLW-1 结论 | 首次部署前须有结论 | **dev 前**（结论已有） | 结论：不阻塞；Low 缺陷卡（PAR-3）随后合入 |
| DA-13 敏感分级登记与守护（G-A7） | 首次部署前 | **dev 前** | 安全类；前置 R-1b |
| R-1b P6 老系统人员、部门对照导入 | W1 | **生产前** | 需用户授权；不阻塞 F-1 |
| R-6 删 `numbering_rules` 旧列 | W1 | **生产前** | 改基线 |
| R-2a（含 G-D12）、R-2b | W1 | **生产前** | 命名、DDL 规范，改基线 |
| R-5（含 G-A8） | W1 | **生产前** | 用语改名 |
| R-4 去数据库时钟默认值 | W1 | **生产前** | 改基线 |
| DA-3 → DA-10、DA-8 | 首次部署前 | **生产前** | ADR-0038 第 4 项第 3 条 |
| G-DDL1–12（NAMING_STANDARD） | 首次部署前 | **生产前** | 命名类 |
| DDL-07+24 审计分区与归档 | W1 | **生产前** | 改基线 |
| DDL-26 / DDL-28（含 DDL-27） | C-3 前 / 首次部署前 | **生产前** | 迁移安全；与 C-1 一起在 C-15 之前 |
| C-1 契约演进守护（G-C1 / C2 / C3 / C6） | 删除标记之前 | **生产前、C-15 之前** | ADR-0038 第 1 项第 3 条第 1 步 |
| R-1b ★ 守护 G-A1、G-C5、G-MG1、G-MG6 | 首次部署前 | **生产前** | 只关标识形态；实际随 P1–P5 完成 |

**原待定九项的结论**（one-coordinator 2026-10-15 裁定；原则：dev 经 A8 只读视图接触真实生产数据，安全、隐私与运行正确性类放 dev 前，契约形态与命名类放生产前）：

| 项 | 原时点 | 现时点 | 说明 |
|---|---|---|---|
| H-1 契约如实（G-A3 / A4 / C4） | 首次部署前，R-5 之后 | **生产前** | 契约形状类；前置 R-5 已移到生产前 |
| O-1 可观测性（G-O1–O3、O5） | 首次部署前 | **dev 前** | 日志不落个人信息、告警可用 |
| S-1 CI 供应链（G-S1–S4） | 首次部署前 | **dev 前** | dev 包也由 CI 构建，运行时接触真实数据 |
| D1-1（G-P4、G-I1 / I2 / I3 / I5）、D1-3（G-P5、G-O4）、D1-4、D1-7 | 首次部署前 | **dev 前** | 外部连接超时、连接池与语句超时 |
| ADR-0034 实现（W2 独立卡，含 DDL-17） | 首次部署前必须 | **dev 前** | ADR-0034 正文第 290 行（实现节第 1 条）明写「属安全项，首次部署任何环境前完成」，已采纳、ADR-0038 未取代，不另起 ADR。DDL-17（角色退役不物理删除）正文未写期限，随同一张卡 dev 前完成；若拆出单做则按原则归生产前 |
| F-2 守护、F-3 运行时配置 | ★ | **dev 前** | F-3 是 dev 首次部署的前提 |
| F-4：antd 中文（F19）、H5 读屏角色 | 首次部署前 | **dev 前** | 界面正确性 |
| F-4：ID 字符串显示 | 首次部署前 | **随 R-1b** | 依赖 R-1b P2–P5 |
| OSS-1、OSS-2 结论 | 首次部署前须有结论 | **dev 前**；dev 不启用归档写入时，改为启用归档写入之前 | 归档写入方上线即需要 |
| 安全缺陷卡（B3-R2..R5、b12-M-8、b12-M-11、B2b NEW-2、INT-M4 / M5、INT-L30） | 首次部署前 | **dev 前** | 安全与正确性类 |

**R-1b-0 推迟的两项守护**（one-1a-next-3，one-coordinator 2026-10-15 同意 C-8；卡见 `cards/R-1b-0.md`）：

| 守护（ADR-0037 守护第 3 组） | 推迟到 | 为何后做更有价值（CS-1.9） |
|---|---|---|
| 集成层日志与向外异常不带 SQL 异常消息（扫描 `getMessage()` 等） | O-1 | 集成层有非 SQL 路径的合理用法（`DeliverWecomCards`、`ResolveWecomUserId`、`MigrateOnlyListener`），扫描范围须与 O-1 的日志口径一起定；seeyon 适配器现已统一用 `ExternalFailure` |
| 迁入、对账端口的用途边界 ArchUnit | ADR-0036 第一个迁入端口 | 现在没有任何迁入、对账端口，守护无守护对象；随第一个端口一起写才能带真实负向样例 |

### Phase 2 前置项（流程梳理须定，one-1a-next-3 登记）

| 项 | 内容 | 验收条件 | 现状 |
|---|---|---|---|
| ARCH-D5 工时审批路线旗标映射 | 工时行类型、混合行到审批旗标（`projectHours` / `preSalesHours` / `overheadHours`）的映射，以及区域负责人的来源，交用户逐条决定（D-1a-127，`process/1a/TIMESHEET审批.md`:194）；先对照 AEC 行业项目制专业服务公司的通行做法定审批路线，老系统按行类型 0/1/2 的行为与生产数据只作参考与核对（取证已登记 `LEGACY_READ_REGISTER.md`），交 one-rules / 用户定——方案页 https://claude.ai/artifact/GGz4AuMS63ooyL85itojya（`rules/decide/sheets/D5.json`）；用户 2026-10-15 六题全部选定，见 RULE-388～392（`rules/TIMESHEET_ROUTING_RULES.md`）；「区域负责人」节点与用语改为部门负责人，经 one-glossary 与改名卡 | 映射卡实现时一并加「提交时一个旗标都没置即失败并告警」（现在不加：旗标写死 `PROJECT_HOURS`，检查必然通过，CS-1.8；one-coordinator 2026-10-15） | 1a 只有项目经理一路（D-1a-107 已关闭提交入口） |

### 数据范围后续维度（one-rules 登记，2026-10-15）

| 项 | 内容 | 依据 | 排期 |
|---|---|---|---|
| 区域范围维度 | 「区域」登记为后续数据范围维度：区域是介于项目和公司之间的一层组织，变动频率较高，现有区域与事业部两种（用户说明）。须定：区域取自组织树的哪一层或单独维护、区域与事业部是否同一维度、组织调整后范围怎么复核（参照 RULE-387）、与部门、法人、项目角色维度的关系 | 用户 F1-05「现在就登记「区域」为后续范围维度并排期」（intent 2026-10-15，one-rules）；`rules/decide/answers/F1.json` | 待 one-architecture 排期 |
