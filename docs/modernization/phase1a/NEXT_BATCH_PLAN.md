# Phase 1a 下一批派工计划（准备阶段）

起草：one-1a-next，2026-10-15。状态：**one-coordinator 已认可顺序与归属约定（2026-10-15）；Q1 已有用户决定**。#72 合并、收到前任 psa-1a-platform-bridge-reimagine 的交接报告、one-coordinator 发「开工」之前，不写业务代码，也不派实现代理。

依据：
- one-coordinator 派工单（2026-10-15）；
- `phase1a/NEXT_BATCH_DRAFT.md`、`phase1a/DEFERRED.md`、`phase1a/review/REVIEW_*.md`、`process/1a/contract.md`，均为 #72 分支 `27ac707`；
- `CROSS_CUTTING_MATRIX.md` 与 `CROSS_CUTTING_DECISIONS.md`（#75 分支）；
- SESSION_PLAYBOOK §5、§6、§6.1、§12–§14.1；
- `proposals/PHASE1A_PLATFORM_CONVENTIONS.md`。

现状数字由只读清点得出，采集于 #72 分支 `27ac707`。#72 整合复审之后若再有改动，开工前重新清点一次。

## 0. 对草案的更正

`NEXT_BATCH_DRAFT.md`、`DEFERRED.md` 与 `application.properties` 目前只在 #72 分支上。#72 合并后，由本会话按本表更正。

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

首次部署任何环境之前，以下各项必须完成：
- 阻塞 080：#72 合并；
- 阻塞 090：F-1；
- R-1（用户已定，B3 曾擅自推迟）；
- R-2、R-4：按第 11 条「能一开始做对」，部署前做比部署后走 expand / contract 便宜得多；
- R-3（基线收口）；
- F-2 守护；
- F-3：一次构建、多环境部署，dev 首次部署就要用；
- 本批缺陷卡中涉及安全的几张；
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
  W4 基线收口：首次部署 dev 前删除基线开放标记（C-3 第一步）
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
| R-4 数据库时钟 | 重构 | sonnet | 基线中 99 处 `DEFAULT` 时钟与 3 处 `now()` 去掉；各 INSERT 显式传入注入 Clock 的时刻；迁移文件守护（扩展 `TimeSourceGuardTest`，扫描迁移） | — | 守护对迁移生效，`sql-database-clock-exceptions.txt` 仍为空 |

### 2.3 W2 公共基础（三路，冻结后只由整合者改）

| 代理 | 模型 | 写入范围（独占） | 共享构件 | 依据 | 验收要点 |
|---|---|---|---|---|---|
| K-1 金额内核（F-7） | opus | `kernel/money/`（`Money` 不再隐式舍入、精确中间运算类型、`Rate`、`Ratio`、按权重拆分原语、舍入口径集中一处、`TaxContext`）；守护：领域代码禁止 `setScale` / `divide` / `RoundingMode`、DDL 金额精度与币种配对、OpenAPI 金额为十进制字符串加币种（G-M5）、前端金额格式化 lint（G-M6） | 现有 `Money`、`Hours` 的调用方只做适配，不改语义 | XD-01、02、07、08；CC-B01；FQ-23..27 未答时按 XD-08 的建议实现 | 拆分守恒、确定性、负数对称、冲销镜像。`TaxContext` 按版本与生效日取税率（配置类，只追加），价税分离按 FQ-27 建议 A，口径集中在一处，以后改口径只改配置 |
| K-2 时态基座（F-8a） | sonnet 执行，opus 复审 | `kernel/time/EffectivePeriod`（开闭区间约定）；按日期取生效版本的仓储支持；防重叠排他约束与只追加的统一写法与守护；workforce 的 `org_unit_versions`、`assignment_versions`、`legal_entity_versions` 与 numbering 的 `numbering_rule_versions` 迁到基座，不改行为 | workforce、numbering 的版本表（只改实现方式） | CC-B02；XD-05、06 | 现有版本表的行为测试全部不改仍为绿；新守护覆盖全部版本表 |
| K-3 平台共用件（F-8b） | sonnet | 统一的事件消费幂等；列表游标分页契约与上限（平台约定：`cursor` + `limit`，默认 50，上限 200）；共用脱敏组件（区分「职级标准费率」与「个人真实薪资 / 日薪」两级）；共用导出控制；日期边界测试夹具；中文按拼音排序（ICU `zh`）约定与守护 G-X2 | `kernel/` 中的 `PageRequest`、`CursorPage`；`platform.web`；前端 `packages/api/paging.ts` | XD-03；用户「按职级标准费率看到个人行」 | 拼音排序在 dev 库（内建 C.UTF-8）上可用，不需要重建库，排序时显式指定 ICU 排序规则。不行时停下报告 |

### 2.4 W3 功能补做

| 代理 | 模型 | 表（新增 / 修改） | 权限码（预计；名称以 terms.yaml 登记为准） | 事件（预计） | 写入范围（独占） | 依赖 |
|---|---|---|---|---|---|---|
| F-1 数据范围 ★ | opus 执行，opus 复审；测试代理 opus | identity 新增角色范围授予明细表；`data-access-registry.yaml` 加 `scope_kind`（SCOPED / GLOBAL）并逐表定值 | 授予时选范围的权限沿用 `account-role:update`；「待调整权限」处理权限给组织数据管理员和安全审计员 | 范围授予变更；待调整权限产生 / 处理 | `platform.identity` 授权部分；kernel 新增 `CurrentAccess`；`OrgUnitId` 下沉到 kernel，`OrgUnitAccess` 改为类型化 ID，去掉 `NOT_USED = new LegalEntityId(1)`；workforce 的部门浏览与人员详情接入范围；`DataAccessRegistryTest`、`onlyRegisteredPersistenceTouchesTheDatabase`、`ScopedRepositoryContract`；敏感权限只授给具体的人（守护）；调岗或离岗生成待调整权限待办；`test-isolation-exceptions.txt` 中 3 条迁入 `scenario/`（b12-H7 余项） | K-3；ADR-0026「1a 交付范围」、ADR-0027 离职删除授予 |
| F-2 守护 ★ | sonnet | — | — | — | `src/test/java/com/forcitis/one/` 的守护类；`backend/architecture/` 的豁免清单（G-T4 规格到测试对照、豁免清单入仓）；G-T1 核对按仓储判定并读 §14 例外；G-T2 扩展到全部生产代码 | W1 完成后开始（要扫描最终代码形状）；G-T4 在 F-1 / F-4 / F-5 合并后最后跑一次 |
| F-3 运行时配置 ★ | sonnet | — | — | — | PC：`login-config.ts`、`login-page.tsx`；H5：`config/index.ts`、`page-frame.tsx`、`wecom-login.ts`；5 个构建期变量改为运行时读取的配置文件；nginx 模板只写约定文件名与路径，部署模板由 one-ops 改 | 与 one-ops 约定配置文件的路径与发布方式 |
| F-4 前端契约 | sonnet | — | 沿用后端已有权限码 | — | 前端审批与收件箱：D-1a-139 全部待办只读视图、D-1a-75 转交、D-1a-77 批量与代理界面；管理模式 1 小时横幅（D-1a-48，`administratorModeExpiresAt`） | K-3；后端接口已有的先做，缺接口的报告由 F-5 补 |
| F-5 审批契约 | sonnet | approval 按条目需要 | 按条目需要 | 按条目需要 | `platform.approval` | K-3。逐条核对范围：D-1a-181 契约原文写「1b 交付」，不做；D-1a-67 与 D-1a-128 的知会部分「随第一个用到知会的流程交付」（RULE-299 已推迟），1a 的 TIMESHEET 流程如果没有知会节点就不做。其余 D-1a-76、91、130、131 核对后实现 |
| F-6 HELD 按角色通知 | sonnet | — | — | 消费同步暂停与每日汇总 | workforce 通知部分、integration 企业微信发送（按角色取人） | W1；企业微信应用（B-2）就绪前用测试替身验收 |
| F-10 利润中心标识 | sonnet | workforce 新增利润中心标识版本表（只追加，带生效区间）；`COLUMN_OWNERSHIP` 与注册表登记为新系统所有 | 利润中心标识维护（组织数据管理员） | 标识指定 / 结束；失效提醒待办 | `organization.workforce` 中利润中心部分；组织同步不得覆盖（守护） | K-2 |
| F-9 ADR-0029 | opus 起草，`architecture-critic` opus 评审 | — | — | — | `adr/src/ADR-0029.md`（提议） | 无；**批准前不实现** |
| F-11 业务单据锁定、红冲、修正 | opus 起草，`architecture-critic` opus 评审 | — | — | — | `adr/src/ADR-0031.md`（提议），与 ADR-0016:23、ADR-0022:32、CC-N05 衔接 | 无；1a 只定设计，Phase 2 实现 |
| F-12 非生产环境单人运维例外 ★ | opus 起草，`architecture-critic` opus 评审；批准后由 sonnet 实现、opus 复审 | 不新增表；审计记录标注 single-operator | — | — | `adr/src/ADR-0030.md`（提议）。批准后实现：只在非生产 profile 下由显式配置（暂名 `forcitis.security.single-operator-dev`，以 terms.yaml 为准）开启。开启后：①两人确认可由一人完成，照常写审计；②超管与应急账号下限降为 1；③同步锁死阈值相应调整。**生产 profile 下该配置存在即启动失败**：`ProductionConfigSanityTest` 加守护，部署流水线对 prod 再校验一次。生产部署前清单（PREDEPLOY_CHECKLIST）加一项：关闭单人例外，启用第二名超管与第二个应急账号。 | 用户 2026-10-15「dev 白名单把我也加上……这些安全措施等到生产部署前再真正落实」（one-ops 已记 intent）。该例外放宽的规则散见于已采纳的 ADR-0026 / 0027 / 0028（敏感权限两人确认、编号四眼确认、至少 2 名超管与 2 个应急账号、同步锁死在可用超管少于 2 时 HELD、放行人不得在离职名单中），按 §12.1 须新 ADR 取代对应条款的适用范围。实现时会碰到 F-1（敏感权限授予）与 B1 已交付的确认流程，所以排在 F-1 合并之后，并以同一个配置开关接入 |

F-1、F-6、F-10 都会在 `organization.workforce` 中写代码，所以按包划界：F-1 只改部门浏览与人员详情的查询路径；F-6 只改通知；F-10 新建自己的包。三者都要在 `Permission.java` 中追加，按 2.1 的约定处理。

## 3. 缺陷修复批（按卡手术式，小 PR 直合 main）

按 #72 整合复审的统计（`27ac707`）：
- Blocker：未关闭 0 条。
- High：未关闭 1 条，b12-H7。它的余项是 3 条测试隔离例外，已并入 F-1。**按 §6.1 第 11 条，这条是否须在 #72 合并前收口，由 one-coordinator 定。**
- Medium：未关闭 21 条，其中 8 条已在草案第三节，13 条不在。
- Low / Nit：未关闭 37 条，其中 5 条在草案，32 条不在（部分只是「未核实」）。

流程：
1. #72 合并后，一个 sonnet 代理逐条核实「未核实」的条目，已修的关闭。
2. 余下条目写成卡：复审编号、复现步骤、预计改动的文件。
3. 测试代理先写失败测试，再由 sonnet 修复。
4. 一张或几张相关卡开一个小 PR。Medium / Low 补上测试、CI 通过即关闭（§6.1 第 4 条），one-1a-next 抽查。

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

**Q1　迁移基线开放期间的写法**

用户决定（2026-10-15，经 one-coordinator 转达，已在其会话记录中核实）：「开放期内直接改基线 (Recommended)」。记录在 intent `2026-10-14-225735-one-1a-next-经-one-coordinato.md`，并已写入 SESSION_PLAYBOOK §12.2。

具体做法：
1. R-3a 先做纯合并，证明表结构 diff 为空。
2. 之后 R-1、R-2、R-4 以及 W2、W3 的表结构改动直接改基线文件。每个 PR 附「预期表结构 diff」，由 opus 核对。
3. 首次部署 dev 前删除开放标记，恢复只追加。
4. W0 的 `migrations_immutable.py` 放行逻辑与此一致。

## 5. 交给 one-coordinator 核对的点（不是用户决定，只需确认）

1. **b12-H7**：one-coordinator 裁定按 §6.1 第 11 条在 #72 合并前收口，由前任会话处理。前任若决定并入 F-1，须在报告中给出理由，由 one-coordinator 裁定。本计划暂按「并入 F-1」占位。
2. **R-1 的范围**：员工、组织单元、岗位、任职按平台约定 §1「外部主数据沿用来源 ID……快照表列入白名单」沿用 A8 ID。但 `primary-key-type-baseline.txt` 把这几张表也列为待改 uuid。one-coordinator 已认可按平台约定执行：这几张表进白名单并写明理由。`user_accounts.user_id` 由 R-1 代理核实来源后报 one-coordinator。
3. **D-1a-181 不纳入 F-5**：契约原文为 1b 交付；D-1a-67、D-1a-128 的知会部分随首个有知会节点的流程交付。one-coordinator 已认可。
4. **1a 出口条件**：brief 模板中「平台穿刺」（CC-B11）仍是 `Proposed revision:`，待用户批准；批准后由本会话补验收。
5. **ADR 编号**：#72 已用到 ADR-0028。本批占用 ADR-0029（F-9 按岗位授予）、ADR-0030（F-12 单人例外，按 one-coordinator 指定）、ADR-0031（F-11 红冲模式）。one-coordinator 已认可。三份 ADR 于准备阶段起草，各经 `architecture-critic` 评审后逐份交 one-coordinator；ADR-0030 优先。
6. **会话设置**：
   - 本会话权限模式为 bypass，与派工要求一致。
   - 推理强度从会话内无法自查，也无法自行修改；由 one-coordinator 请用户在应用中确认设为 high。
7. **NEXT_BATCH_DRAFT 的更正（第 0 节）**：#72 合并后由本会话在 main 上更正，不改 #72 分支。
