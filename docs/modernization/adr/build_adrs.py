#!/usr/bin/env python3
"""Generate the FORCITIS one modernization ADRs (one file each + README index)."""
ADRS = [
("0001", "产品与目标：以 FORCITIS one 重建 PSA，绞杀式切换", "已采纳（用户决定 2026-10-14）",
"""- PSA 运行在 yudao 微服务底座上（Java 8 / Boot 2.7 / SQL Server 2016 / Flowable 6.8 / Vue 2），技术栈全线 EOL，生产存在可公网利用的免密登录（SEC-001）。
- 用户：「forcitis-one 就是 PSA 的接班系统」「one.forcitis.com 是现代化后的 url。产品名 FORCITIS one」「老系统迁移期间持续运行」「功能复刻但是行为要现代化」「forcitis-one仓库代码不能参考」。""",
"""1. 现代化的产物就是产品 **FORCITIS one**，入口 **one.forcitis.com**；老系统 psa.forcitis.com 在迁移期间继续运行。
2. 采用 Rearchitect（`reimagine`）：在新架构上按 BC 重建，不做同栈升级。
3. 按绞杀模式逐域切换：每个阶段都是一个可运行、可回滚的过渡架构（TOGAF）。
4. 只把 forcitis-one 的 ADR-0001 当作选型参照，不读取、不复用它的代码。
5. **新代码放在新建仓库 `FORCITIS/forcitis-one-psa`**（用户 2026-10-14 决定：「新建仓库 forcitis-one-psa 放新代码」「放在 FORCITIS 组织下，现在就建」；已创建，私有：https://github.com/FORCITIS/forcitis-one-psa）：单仓，含后端单体、PC（React）、H5（Taro）和 `shared/ui`（设计正本的源头，ADR-0013）；骨架取自 `architecture-guardrails/backend` 与 `frontend`。老 psa 仓库在迁移期继续维护，只接收止血补丁和反向同步所需的改动。""",
"""- 迁移期间有两个入口：已迁移的功能在 one.forcitis.com，其余在 psa.forcitis.com。需要身份桥接（ADR-0009）和老 URL 兼容门面（ADR-0011）。
- 企业微信的可信域名和 OAuth 回调要新增 one.forcitis.com，属于运维事项。""",
"INTENT.md、ASSESSMENT.md（推荐模式 Rearchitect）、runtime/README.md（单台 ECS、335 个活跃用户）",
"同栈升级（Boot 3.5 已于 2026-06-30 停止 OSS 支持，且升级解决不了边界问题）；整体重写后一次性切换（风险不可控）"),
("0002", "技术栈基线", "已采纳（用户 2026-10-14：剩余各项选择「全部定稿」）",
"以 forcitis-one ADR-0001 为参照，结合 PSA 的代码与生产数据逐项评估（STACK_DECISION.md），并吸收架构评审意见。",
"""| 项 | 选型 |
|---|---|
| 运行时 / 框架 | Java 25 LTS；Spring Boot 4.1.x（实测 4.1.1） |
| 模块化 | Spring Modulith 2.1.1 单体（模块结构见 ADR-0003） |
| 数据库 | **PostgreSQL 18**（阿里云 RDS，杭州 8 个可用区均可开通基础版 / 高可用版 / 集群版），一 BC 一 schema；守护模板 52 项测试已在 PG 18.6 上全部通过 |
| 持久化 | **只用 `JdbcClient` 手写仓储 + 类型化映射**；不用 Spring Data JDBC（与「领域层不依赖 Spring」冲突；子集合删除后重插会破坏行级身份） |
| 迁移 | Flyway：一张历史表，按模块分目录（`db/migration/<subdomain>_<bc>`） |
| HTTP | Spring MVC + springdoc OpenAPI 3，带版本号（ADR-0008） |
| 流程 | Flowable 8（ADR-0007） |
| 模块间事件 | Modulith 事件发布登记表（JDBC）；不保留 RocketMQ |
| 批处理互斥 | 作业表租约：`(legal_entity_id, period, job_type)` 唯一约束；不用 Redis 锁或 advisory lock |
| 定时任务 | `@Scheduled` + ShedLock（JDBC），并提供运维页面查看与手动触发；不保留 XXL-JOB |
| 缓存 / 编号 | Caffeine；业务编号见 ADR-0010；不保留 Redis |
| 配置 / 密钥 | Spring 外部化配置 + 阿里云 KMS 凭据管家；不保留 Nacos；仓库禁止出现明文密钥（gitleaks） |
| 网关 | 去掉 Spring Cloud Gateway；nginx 负责 TLS 和限流 |
| 可观测性 | Micrometer + OpenTelemetry → 阿里云 ARMS / SLS（待确认）；告警覆盖同步延迟、未完成的事件发布、作业失败、登录失败率 |
| 部署 | 新单体**独立主机**部署，不与老系统共用 ECS；数据库为 PSA 专用 RDS PG，不复用即将到期的 forcitis-one 实例 |""",
"""- 偏离 ADR-0001 的地方：持久化（JdbcClient 而非 Spring Data JDBC）、流程引擎（本阶段引入，而非推迟）。
- 新增依赖实测通过：Flowable 8、Spring Security 7、Flyway、Testcontainers PG 18.6（architecture-guardrails/backend，52 项测试全绿；PG 17.11 上也全绿）。
- 偏离 ADR-0001 的 PG 17：改用 PG 18，**已采纳**（用户 2026-10-14：「就用 PG 18」），理由见 STACK_DECISION.md。
- 待确认：阿里云 DTS 是否支持 SQL Server → PG 18 的迁移；如不支持，迁移改用自建 ETL（本方案的逐列所有权和对账查询本来就要自建）。
- 待在 Boot 4 上实测：springdoc、ShedLock、Spring Session JDBC。""",
"STACK_DECISION.md；架构评审（B/M 项）；architecture-guardrails/backend",
"保留 Redis / RocketMQ / Nacos / XXL-JOB：单体内不需要，每多一个组件就多一份运维和安全面；PG 17 起步（支持期到 2029-11，比 18 早一年结束；上线后再做一次大版本升级要停机和回归，而新库现在就没有升级成本）"),
("0003", "模块结构：子域.BC 包、自定义识别策略、依赖图文件", "已采纳（用户要求「用 ArchUnit 和包结构保证 agent 不犯错」）",
"""- 仓库已定稿 12 个子域 slug（`02-子域划分.md`），并约定包结构为「子域 → BC → 概念群」三级。
- 实测发现：Spring Modulith 2.1 中，BC 包一旦带 `@ApplicationModule` 注解就会变成嵌套模块，跨子域访问会被拒绝。""",
"""1. 包结构为 `com.forcitis.one.<subdomain>.<bc>.{api,events,domain,application,infrastructure,web}`；PG schema 为 `<subdomain>_<bc>`。
2. 用自定义识别策略 `SubdomainModuleDetection`（配置项 `spring.modulith.detection-strategy`）把每个 BC 识别为顶层模块，标识形如 `cost.timesheet`；子域只作为命名空间；`kernel` 是共享内核。
3. 模块之间允许的依赖写在入库文件 **`architecture/module-dependencies.txt`** 中（即上下文映射的代码形态），由 `ModuleDependencyTest` 强制执行，修改需经 CODEOWNERS（IT Admin）评审。BC 层不使用 `@ApplicationModule` 注解。""",
"""- agent 不能通过随手改注解来「修好」依赖违规，只能走评审。
- 平台子域只有一个 BC，按技术能力拆为 `platform.identity`、`platform.approval`、`platform.integration`、`platform.audit`、`platform.numbering`。""",
"architecture-guardrails/backend：模块识别结果 `cost.timesheet, delivery.execution, kernel, platform.approval, platform.identity`；删掉一条依赖后，负向测试精确报出违规",
"子域作为顶层模块、BC 作为嵌套模块（BC 之间不能直接依赖，不符合上下文映射）；BC 名直接作为顶层包（丢失子域分组，与仓库约定不一致）"),
("0004", "深模块、窄接口与架构守护", "已采纳（用户：「深模块、窄接口，要给 agent 约束，避免错代码」）",
"老 PSA 大部分代码由 AI 生成。评估中记录了大量 agent 式错误，见 AGENT_FAILURE_MODES.md。",
"""1. 每个模块只有两个出口：`api`（接口、record、类型化枚举）和 `events`（过去式的 record）。web 和 infrastructure 层的类一律包内可见。
2. 守护规则：后端 G1–G33（ArchUnit + Modulith + 依赖图 + DDL 命名）；前端 F1–F14（ESLint + TypeScript strict + 公开接口数量检查）。详见 NAMING_STANDARD.md §4。违规即 CI 失败，失败信息写明该放在哪里、该叫什么名字。
3. 接口窄度预算：每个模块的 api 方法数默认 ≤ 12，按模块单独声明，修改需评审。""",
"""- 模板工程实测：后端注入 8 类错误、前端注入 13 类错误，全部被拦下。
- 规则需要随评审意见持续补强，例如 `permitAll()` 只能出现在 identity 模块、`TransactionTemplate` 也受事务规则约束（均已补上）。""",
"architecture-guardrails/{backend,frontend}；negative-test.log / negative-lint.log",
"只靠代码评审（老 PSA 的实际结果证明拦不住）"),
("0005", "统一语言与命名", "已采纳（用户：「命名要符合英文、行业、规范的习惯」「统一领域用语和 ADR」）",
"老代码中有 1,234 处中文标识符；「工时」一个概念有 manhour / works / timesheet 三套叫法；27 个状态值是雪花 ID。",
"""1. `docs/domain/00_战略设计/03-通用语言.md` 是**唯一词源**。GLOSSARY.md 给出模块路径和老名 → 新名对照，不另立术语。
2. 标识符只用英文 ASCII，采用行业惯用语（Quote、Timesheet / TimeEntry、WorkPackage、RateCard、JournalVoucher……）。
3. 词表改词建议 G-1 至 G-7（GLOSSARY.md §3）需同批修改 `03-通用语言.md`。其中包括：废止本方案曾使用的「审批与待办 BC」，改称「权限流程与集成平台 BC」的 `platform.approval` 模块。""",
"模板已按词表改名：`TaskPackage` → `WorkPackage`，PG schema 同步改名。",
"GLOSSARY.md；NAMING_STANDARD.md",
"沿用老名（agent 会继续混用）；自创英文名（违反词表规则）"),
("0006", "强类型与值对象", "已采纳（用户：「要用强类型」「值对象要有防止 agent 出错的措施」）",
"老代码的问题：裸 `BigDecimal` 金额、`long` / `String` 类型的 ID、`Map<String,Object>` 流程变量、`boolean` 返回值，以及审批结果码在两套代码里数值互换。",
"""1. 值对象用 record + 紧凑构造器自校验（`Money` 统一 2 位小数且只能同币种运算；`Hours` 非负），并标注 `@ValueObject`。
2. 跨模块使用的 ID 定义在 api 包中（`WorkPackageId`、`TimesheetId`、`ApprovalInstanceId`、`UserId`）。
3. 结果用 sealed interface 表达，调用方用 `switch` 穷尽处理；查询不到时返回 `Optional`，不返回静默默认值。
4. 模块契约中禁止 `Object` 和 `Map`；权限用类型化枚举。前端开启 TS strict，类型由 OpenAPI 生成。""",
"守护：G16、G17、G21–G24、G32；值对象单测和集成测试已覆盖。",
"architecture-guardrails/backend（kernel、各模块 api、ArchitectureRulesTest）",
"弱类型 + 注释约定（老代码的做法）"),
("0007", "流程引擎：Flowable 8 本阶段引入（BPMN + DMN）", "已采纳（用户决定：「flowable 这个阶段要引入」）",
"""- 生产只有 10 个流程，其中 TIMESHEET 占 34,498 个实例；流程里没有服务任务、子流程或并行网关。
- ADR-0001 原本推迟引入流程引擎，用户决定本阶段就引入。""",
"""1. Flowable 8 作为 `platform.approval` 模块的内部实现，引擎表放在 `platform_approval` schema。
2. 业务模块只依赖 `platform.approval::api`（类型化的 `ApprovalRequest`、`ProcessKey`、`ApprovalInstanceId`），禁止 import `org.flowable`（G19）。
3. BPMN 只编排人工步骤、超时、退回；业务判定用 DMN 决策表，或由所属模块算好后以 routingFlags 传入（见 GUIDING_PRINCIPLES.md §4、§5）。
4. 切换时在途实例按「排空」处理：老实例在老引擎上走完，新提交走新引擎。""",
"实测：Boot 4.1.1 + Flowable 8.0.0 + PG 17 / PG 18.6，引擎表 100% 落在指定 schema，发起、审批、结束流程正常。",
"architecture-guardrails/backend ApplicationIntegrationTest；runtime/README.md",
"规则型自研审批（实际上等于自己写一个工作流引擎）；保留 Flowable 6.8 独立服务"),
("0008", "API 版本化", "已采纳（用户：「api 要有版本号」）",
"老系统的 URL 没有版本，前端手写的 URL 有 69 个解析不到后端。",
"""1. 使用 Spring Framework 7 原生 API 版本化：路径 `/api/{version}/<subdomain>/<resource>`，方法上声明 `version = "1"`；不受支持的版本返回 400。
2. 所有控制器都必须带版本前缀（G20）。破坏性变更发布 v2，与 v1 并存直到调用方迁完。
3. 前端只能使用 OpenAPI 生成的客户端，禁止手写 URL（F5、F10）。""",
"老 URL 在切换期由兼容门面承接（ADR-0011），兼容门面不带版本号。",
"ApplicationIntegrationTest：v1 返回 200，v2 返回 400",
"请求头版本（不直观）；不做版本化"),
("0009", "鉴权重建", "已采纳（用户：「原系统鉴权存在的问题，这次现代化要一并解决」）",
"""老系统的鉴权问题：
- SEC-001：免密登录；SEC-002：KM 令牌签发；SEC-005：伪造发起人；
- SEC-009：没有功能级和行级授权；SEC-011：弱口令、无锁定、验证码关闭；
- SEC-015：可逆加密的「记住我」；SEC-019：令牌放在 URL 里；SEC-020：各模块信任 `login-user` 请求头；SEC-021：应用端令牌可访问管理端接口；
- 另有：权限变更需要重启服务、84 个 `@PermitAll`。""",
"""1. **默认拒绝**：所有请求必须认证；每个接口方法必须有 `@PreAuthorize`，或带理由的 `@PublicEndpoint`（G28）。只有 `platform.identity` 能配置 Spring Security（G33）。
2. **会话**：服务端会话（Spring Session JDBC），HttpOnly + SameSite Cookie，开启 CSRF；令牌不进 URL。
3. **登录方式**：企业微信 OAuth2、致远 SSO（服务端验票、一次性使用）。外部系统（BI、KM、A8）使用签名 + 短时效的一次性票据。取消本地口令登录，只保留管理员应急账号（强口令 + MFA）。
4. **授权**：类型化权限 `Permission`（RBAC）；数据范围按「法人 × 部门 × 项目角色」，由 `AccessScope` 显式传入仓储，不用隐式的 SQL 改写拦截器；权限变更即时生效。
5. **身份来源**：调用方身份只能来自 `CurrentUser`（会话），请求体里不得携带 userId、creator 等身份字段（G33）。
6. **过渡期**：新系统以只读方式校验老系统的令牌（身份桥接），不向老系统写令牌。""",
"""- 每个阶段都要跑「遍历全部接口，匿名访问返回 401」的集成测试（已实现）。
- 老系统在过渡期先用 harden 补丁止血（ADR-0015）。""",
"SECURITY_FINDINGS.md；ApplicationIntegrationTest（匿名 401、权限不足 403、权限正确 200）",
"沿用 yudao 的令牌表和数据权限拦截器（就是问题的根源）"),
("0010", "可配置的业务编号", "已采纳（用户：「业务编号系统要我可以自定义」）",
"老系统的编号规则硬编码在代码里：PSAQU + 年份 + 5 位流水（从 103 起）、TS 前缀、CF0-…、HC-…-R{n}、T-{时间戳}。",
"""1. 新增 `platform.numbering` 模块。每种单据（`DocumentType`）一条规则，由管理员通过 `PUT /api/v1/platform/numbering-rules/{type}` 配置，权限为 `platform.numbering:numbering-rule:update`（G43 统一后的权限码格式）。
2. 模板支持的占位符：`{LE}`、`{yyyy}`、`{yy}`、`{MM}`、`{dd}`、`{yyyyMM}`、`{yyyyMMdd}`、`{seq:N}`；重置周期可选 NEVER / YEARLY / MONTHLY / DAILY；可设起始值，用于承接老系统的最大编号。
3. 保存规则时校验：必须恰好一个 `{seq:N}`；模板里的日期部分必须能区分重置周期，否则会撞号。
4. 发号与业务写入在同一个事务里完成（`Propagation.MANDATORY`），用 PG 的 `INSERT … ON CONFLICT … RETURNING` 原子递增：按「单据类型 × 法人 × 周期」计数，不重号、不跳号。
5. **统一新格式**（用户 2026-10-14 决定）：不沿用老编号格式。默认模板 `{单据类型代码}-{LE}-{yyyyMM}-{seq:4}`，按月重置（例如 `QT-FST-202610-0001`）；单据类型代码在词表中登记（G45）。老编号迁移为只读的「原编号」字段，可检索，并提供新旧编号对照。各单据的最终模板由管理员在 Phase 1a 的编号规则页确认。""",
"实测：从 103 续接、跨年重置、不同法人分别计数、回滚后不跳号、20 线程并发不重号、非法规则返回 422、无权限返回 403。",
"architecture-guardrails/backend（platform.numbering、V…__platform_numbering_create_numbering.sql）",
"PG 序列（会跳号）；继续硬编码（不可配置）"),
("0011", "数据：OLTP 设计、数据字典、桥接与 OLAP 分离", "已采纳（命名与数据字典：用户要求；数据过渡（雪花 ID、逐列所有权矩阵、老 URL 兼容门面）：用户 2026-10-14「全部定稿」）",
"""- 用户：「数据字典的字段名要符合前述命名规范」。
- 架构评审：「每个域只有一个写入方」在行级不成立（老工时审批会回写 `psa_task_package.actual_hours`）；老页面仍在调用会被路由走的 URL；BI 直接读老表。""",
"""1. **【已采纳】DDL 规范**：
   - 表名和列名为英文 snake_case，由词表派生，不用 `psa_` 前缀、拼音或老名；
   - 审计列齐全；金额必须带币种；状态列为 text + `CHECK`；布尔列以 `is_` / `has_` 开头；
   - 每张表、每个字段都有中文 COMMENT；数据字典从数据库元数据自动生成；
   - 以上由 `DdlNamingTest` 强制执行。
2. **【提议】迁移**：保留老系统的雪花 bigint ID 作为主键；只迁主数据和未关闭单据；Flowable 历史与 API 日志只读归档。
3. **【提议】桥接**：每个阶段的入口条件要求有一份**逐列所有权矩阵**（谁写、同步方向、延迟、回滚）；反向同步只写新系统拥有的列；老 URL 由兼容门面承接，返回老格式。
4. **【提议】OLAP**：`reporting` schema（或现有 BI-olap）采用星型模型，由事件或 CDC 构建；BI 只读 `reporting`；过渡期视图保持老表的形状。""",
"BI 的数据迁移降为 SQL 方言移植；每个阶段都要有回滚演练。",
"DdlNamingTest 负向测试（报出 13 项违规）；data-dictionary.sample.md；架构评审 B3、B4、M3、M4",
"一次性全量迁移（风险不可控）；BI 直接读新的 OLTP 表（会把交易模型锁死）"),
("0012", "测试策略", "已采纳（用户：「单测要最大代码覆盖率的最少测试，更大单元集成测试为主，必要时端到端」）",
"老 service 层的超大类几乎没有测试；bpm 模块的测试被排除在 CI 之外。",
"""1. 主力是模块级集成测试：真实 HTTP → 鉴权 → 用例 → 跨模块 → Flowable → PostgreSQL（Testcontainers），不 mock 自己的模块。
2. 单元测试只补纯领域逻辑的边界：值对象不变式、计算、DMN 决策表；用等价类和边界值，以最少的用例覆盖最多的分支。
3. 端到端测试只覆盖 4 条人物流程和登录鉴权冒烟（Playwright）。
4. 门槛：domain 和 application 合计行覆盖 ≥ 80%、分支覆盖 ≥ 70%；P0 规则 100% 有以规则 ID 命名的测试。""",
"实测：11 个集成测试 + 5 个单测，行覆盖率 85.9%。",
"architecture-guardrails/backend/coverage.csv",
"金字塔式大量 mock 单测（覆盖率高，但证明不了真实行为）"),
("0013", "前端：React 19 + TypeScript，PC 与 H5 统一一个框架", "已采纳（用户决定 2026-10-14：「前端就用 React」；选型依据为独立评估，不以设计系统或蓝图 DD-8 的建议为依据）；设计正本：已采纳（用户：「设计正本就用 Forcitis One Design System」）",
"""- PC：Vue 2.7 + Element UI，364 个 `.vue` 文件、约 12.5 万行；存在 841 处只适用于 Vue 2 的 API；Element UI 没有 Vue 3 版本。
- H5：Taro 4 + React 18，110 个文件、约 1.9 万行。
- 团队：近半年 PC 和 H5 的主要提交者是同一批人（3 名全栈开发两端都写），团队已经在生产中维护 React 代码。
- 现代化方式是 reimagine：PC 页面按阶段逐页重建，老 Vue 2 代码不会被搬运。
- 用户的要求：强类型、对 agent 友好、目录约束和 lint 守护、设计系统驱动。""",
"""1. **【已采纳】** PC 用 **React 19 + TypeScript strict + Vite + Ant Design 5 + TanStack Query + Zod + React Router 7**；H5 保留 Taro 4 + React。两端共用 OpenAPI 生成的类型和客户端、Zod 校验、查询层、领域格式化工具（金额万 / 亿、工时、期间）和令牌 CSS。
2. 选 React 而不选 Vue 3 的理由（逐项比较见下）：
   - **一个框架**：H5 已经是 React，选 React 后 PC 和 H5 共用组件逻辑、hooks、类型和测试工具；选 Vue 则两个框架长期并存，或者还要再重写一次 H5；
   - **迁移成本相同**：Vue 2.7 + Element UI 到 Vue 3 不是平滑升级（Element UI 没有 Vue 3 版，841 处 Vue 2 专用 API），而且本项目本来就是逐页重建，Vue 的「延续性」只体现在语法熟悉度上；
   - **类型和守护没有盲区**：TSX 就是 TypeScript，`tsc` 和 ESLint 能看到全部代码；Vue 模板需要 vue-tsc 和单独的模板规则，Vue 版守护实测仍有盲区（`h()` 渲染函数中的 style、`.vue` 文件名要靠脚本检查）；
   - **对 agent 更友好**：React + TS 的公开语料和类型化生态最大，agent 生成代码的准确率更高，与「强类型、防 agent 出错」的要求一致；
   - **密集财务界面的组件生态**：Ant Design 5（含 ProTable 一类企业级表格）、AG Grid、TanStack Table 在 React 上最成熟。
3. **【已采纳】** 设计系统驱动：令牌落在 `shared/design-system/tokens.css`，Ant Design 主题由语义令牌映射；业务模块只能使用 `shared/ui` 中的组件，不能直接 import antd，不能内联样式、不能写死颜色、不能引用调色板原值（F14）。设计正本为 Claude Design 中的「**Forcitis One Design System**」（用户 2026-10-14 决定；与弗思特 VI 一致）。「FORCITIS one（Storm）」不作为正本，其业务组件只作交互参考，按正本令牌重新实现。
4. **【已采纳】** 前端目录约束和 lint 守护 F1–F14 与后端对齐（`modules/<m>/index.ts` 是窄接口）。
5. **【已采纳】** 业务判定在后端：工时上下限、可计费率口径、审批路径、撤回策略、成本处理方式等由后端返回，前端只展示与提示。
6. 过渡期由 one.forcitis.com 的 React 应用作为外壳，未迁移的页面跳转回老系统。
7. **【已采纳】设计正本随现代化持续完善**（用户：「还不完善在现代化过程中不断完善」）。规则：
   - **先补正本，再写页面**：某阶段需要而正本没有的组件或范式，先在正本中设计、评审，再按正本实现到 `shared/ui`；不允许在业务模块里自造一次性组件绕过正本；
   - **差距清单**：每个阶段开始时列出本阶段所需、正本缺失的组件，记录在 `analysis/psa/DS_GAPS.md`；阶段结束时清单清零（已补入正本并在 `shared/ui` 实现），或注明延后原因；
   - **代码为源**（用户 2026-10-14 选定）：FORCITIS one 仓库的 `shared/ui`（React）和 `tokens.css` 是正本的源头；正本现有组件先取下来作为 `shared/ui` 的起点，缺的组件在代码里补齐、经守护和评审后，用 `/design-sync` 同步回 Claude Design 正本。设计与生产代码是同一份，不会走样；不允许在 Claude Design 里另改一份与代码不一致的组件；
   - **写入正本已获授权**（用户 2026-10-14：「授权你往正本写入设计」）。正本现有的 `_ds_bundle.js` 是在 Claude Design 内直接制作的，没有同步锚点，首次同步会替换它：同步前先把正本全部文件备份到本地，并确认同步后的 bundle 覆盖正本现有全部组件（逐个比对预览）；之后按 `/design-sync` 的锚点增量同步，每次写入记录在 `DS_GAPS.md` 的状态列。
   - 按正本现状预判的差距（详见 `DS_GAPS.md`）：1a 合并待办收件箱、登录页；1b 报价单文档、成本估算树；2 项目记录页、甘特 / 进度、交付物与验收；4 WIP 与开票、收入确认、对账工作台（正本路线图中已列）；5 用户、角色、权限管理页；通用表单件中的文件上传、时间选择、富文本。""",
"""- 熟悉 Vue 的开发者需要适应 React；由于团队已经在写 H5 React，这个成本有限。
- 与蓝图 DD-8（Vue 3）不一致：DD-8 是 demo 阶段的决定，没有考虑 H5 已是 React 和守护盲区；用户已决定采用 React，本 ADR 覆盖 DD-8 的前端部分。
- `frontend-vue` 守护模板保留作对照，不作为骨架。""",
"""| 维度 | React 19 | Vue 3 |
|---|---|---|
| 与 H5 统一 | ✔ 同一框架 | ✘ 两个框架 |
| 老 PC 代码可复用 | ✘（逐页重建） | ✘（Element UI 无 Vue 3 版，同样重建） |
| 团队现有技能 | ✔ 已维护 H5 React | ✔ 已维护 PC Vue 2 |
| 类型检查覆盖 | ✔ 全部代码由 tsc 检查 | △ 模板依赖 vue-tsc |
| lint 守护盲区（实测） | 见 `frontend/negative-lint.log` | `h()` 中的 style 无法静态守护；文件名要脚本检查 |
| agent 生成准确率 | 高（语料最大） | 中 |
| 财务密集表格生态 | 最成熟 | 可用（Element Plus、AG Grid Vue） |
| 学习曲线 | 中 | 低 |

architecture-guardrails/frontend（React 版：修复了 ESLint 配置块互相覆盖导致 F9、F14 部分失效的问题，新增配置自检脚本（10 种违规 × 7 类目录）；`pnpm verify` 全绿；F1–F14 注入全部拦下：eslint 38 条、stylelint 4 条、结构检查 3 条。盲区：F8 只查 `hooks/` 目录；颜色写在变量、拼接字符串、CSS-in-JS 或 antd 的 `color="red"` 中拦不住；令牌名拼错查不出）；architecture-guardrails/frontend-vue（Vue 版：verify 全绿，F1–F14 注入全部拦下）""",
"Vue 3 + Element Plus 或 reka-ui（团队上手更快，但与 H5 形成两个框架，且模板类型检查与 lint 有盲区）"),
("0014", "指导思想：DDD、DAMA、TOGAF、BPMN、DMN、OLTP、OLAP", "已采纳（用户要求）",
"用户要求以这七个框架的核心思想指导现代化。",
"按 GUIDING_PRINCIPLES.md 执行：每个框架都落到具体产物和守护上（TOGAF 对应 ADM 阶段 ↔ 本项目产物；DDD 对应 BC = 模块 = schema；BPMN / DMN 分别负责流程与决策；DAMA 对应数据所有权和数据字典；OLTP / OLAP 对应交易与分析分离）。",
"新增决策都要说明对应哪个框架的哪条原则。",
"GUIDING_PRINCIPLES.md",
"—"),
("0015", "安全止血先行", "已采纳（用户：「先做安全审查」）",
"生产安全组对公网开放 48088 端口，免密登录端点正被调用（近 180 天约 2 万次）。",
"""1. 在任何新系统的阶段上线之前，先在老系统上应用 harden 补丁（`security_remediation.patch` 和 `.local.patch`，已经过 3 轮复审），或先做网络层止血。
2. 立即轮换所有泄露的凭据，包括仓库文档里的那一份，并清理 git 历史。
3. 这是 brief Phase 1 的入口条件。""",
"需要协调 A8、YD、BI 等调用方改为签名调用。",
"SECURITY_FINDINGS.md；security_remediation.notes.md",
"等新系统上线后再处理（风险敞口太长）"),
("0016", "权责发生制业财一体主干：事实只追加、单一过账管道、期间闸门", "已采纳（用户 2026-10-14：「采纳 ADR-0016 和 ADR-0017」；起因：「这是一个 AEC 行业项目为基础的专业服务公司，权责发生制为核心的业务财务一体化系统，这个对现代化有用吗」）",
"""- 弗思特是以 AEC 项目为基础的专业服务公司，PSA 的本质是权责发生制下的业财一体：把人的时间转化为收入和现金，并在过账、开票、确认收入、关账这些控制点之后受控留痕。
- 证据（老系统与文档）：
  - `03-通用语言.md`：成本归集按权责发生制入账；合同资产受 F113 滚动恒等式约束；封期后更正走当期调整；质保金是独立应收类型；
  - `income2.0/05-收入确认域-状态机.md`：客户验收即确认收入，CONFIRMED 生成不可篡改的 `RevenueFact`，更正只走冲销 / 重做事实链，收入归属期间取客户签字日所在月；
  - `08-FORCITIS Operating Accounting Taxonomy.md`（OAT）：经营会计原语映射到会计准则、XBRL、管理会计，KPI 口径必须回到 OAT；
  - 老系统已有过账引擎、成本结转、红冲（`revenueRedReversal`）、凭证推送 U8C（权责发生制与收付实现制两套账）；
  - AEC 行业特征：签证（变更）、质保金、分包成本、方案 / 深化阶段，成本类别如安评、光评、施工图审查。
- 用户的 PSA 边界蓝图 v2.3 已裁决 DD-1（单一过账管道）、DD-2（只能冲销）、DD-3（期间拦截器，按 (期间, 法人)）。""",
"""1. **主干三件**：
   - `operations.facts`（经营事实与归属 BC）作为**单一过账管道**：触账的 BC（工时成本、费用、WIP / 开票、收入确认、回款、手工分录）只提交类型化的过账候选（sealed `PostingCandidate`），由管道统一校验（期间开放、借贷平衡、幂等键、法人一致）、分配单调序号 `PostSeq`、写入明细事实、发布事件；
   - 新增 **`accounting.period`**（会计期间 BC）：期间状态以 (期间, 法人) 为粒度，只有本 BC 能写；过账管道内置期间校验，关账是一组前置检查加一次状态转换；
   - `operations.policy`（经营口径与勾稽 BC）负责跨域对账等式（归集守恒、合同资产滚动恒等式、凭证借贷平衡）。
2. **事实只追加**：明细事实表在数据库层禁止 UPDATE / DELETE（权限回收 + 触发器）；更正的唯一路径是冲销，即生成反向事实和新的 `PostSeq`，原行保留并双向关联；关账后的更正落在开放期间，并标注原期间。守护：ArchUnit 禁止事实仓储出现更新和删除方法；DDL 测试检查触发器存在。
3. **业务 BC 不认识会计科目**（ADR-0001 硬约束 6）：科目映射和凭证生成只在 `accounting.ledger`，映射规则用 DMN 表管理，按版本生效（ADR-0007）。
4. **收入确认**是无状态规则层，口径按已定稿的业务规则 **ACC-R01（2026-08-14）：统一为「产出法 · 客户价值确认」**——收到客户价值确认单是确认收入的唯一触发点，确认时形成合同资产并同时结转成本，开票时转为应收；对齐 CAS 14 / IFRS 15 的产出法。不提供投入法（POC）选项；老文档中「EAC / POC 驱动收入确认」的表述已过期（规则清单 C7）。DMN 只用于科目映射等可配置决策。
   **老系统实际做法（代码 + 生产只读核实，2026-10-14）**：与 ACC-R01 一致，是「按合同条款、凭客户确认确认收入」的产出法，没有任何 EAC / POC 驱动的收入计算：
   - 收入载体是**收入确认单**（老表 `psa_pm_cuvalue`；业务和 A8 中的叫法「产值确认单」是方言，见 ADR-0018），金额含税，另存不含税额和税额，按 (条款, 客户确认月份) 计入期间；
   - 来源有两条：**致远 A8 OA 的收入确认单（方言「产值确认单」）同步**（`source_system=0`，2009 年至今 19,869 行，2026 年 1–10 月 1,372 行、约 1.04 亿元，仍是主渠道，同步时整批重灌）；**PSA 自己的收入确认单**（`source_system=1`，2026-09-24 上线，至今确认 18 笔、约 136 万元，1 笔审批中、1 笔驳回）；
   - PSA 收入确认单的规则：确认金额 > 0；累计不超过条款金额（提交和审批通过时各校验一次）；必须附客户验收单、邮件确认和客户签字日期；收入期间取客户签字日期所在月；合同须已回签；预付款条款（PREPAY）免交付验收前置；经审批流通过后才写入产值表；
   - 退回与红冲：`type=1` 的负数行以 `origin_code` 指向原产值单（434 行），成本侧据此回冲已配对的结转；
   - 成本配对：月结时把成本按 (条款, 月份) 与已确认产值配对转出，`write_off_flag` 标记已配对（9,158 行已配对、10,751 行未配对）；
   - 表 `psa_revenue_fact` 在生产为 0 行，代码注释说「向不可变事实表追加」与实际（写产值表）不符。
5. **WIP 与开票是同一个一致性边界**：`receivables.wip` 内 WIP 和发票是两个聚合，开票接受和作废在同一事务内改写 WIP 终态，不拆成两个最终一致的 BC（蓝图 BC-10 硬约束）。
6. **法定总账**：本次现代化期间 U8C 仍是法定账（权责发生制、收付实现制两套），`accounting.ledger` 负责生成凭证、可靠投递和对账。蓝图 DD-6（FORCITIS one 自任法定账、U8C 退役）不在本 brief 范围，作为后续单独决策。
7. **分析语义层**：`reporting` 读模型和 KPI 按 OAT 原语声明口径、单位、期间类型和事实归属；分析侧只读，不回写事实域。
8. **共享内核**只放值对象：`Money`（金额 + 币种）、`AccountingPeriod`、`ProjectCoordinate`（项目 / WBS 坐标）、`LegalEntityId` 和类型化 ID。
9. **AEC 特有的 BC** 纳入路线：`contract.changeorder`（签证、变更、索赔）、`procurement.subcontracting`（分包成本，以外部 AP 为准、强契约对账）、质保金作为 `receivables.collection` 中的独立应收类型。""",
"""- 阶段调整：Phase 1a 的内核增加 `AccountingPeriod` 和 `ProjectCoordinate`；Phase 2 的入口条件增加「过账管道和会计期间 BC 可用」，工时成本是第一个事实生产者（交易类型 TS），老过账引擎结果作为影子比对基线；Phase 4 的收入、WIP、凭证都通过同一管道。
- 守护新增：G34 事实仓储只追加；G35 只有 `accounting.ledger` 能依赖科目映射；G36 触账 BC 不能绕过管道直接写事实表。
- 数据迁移：老系统的红冲记录按冲销关系迁移，保持原行和反向行的双向关联；期间状态按 (期间, 法人) 迁移。
- 收入有两条来源，迁移与切换要分别处理：A8 产值同步在新系统中作为防腐层输入（只读、按来源标记，不得覆盖 PSA 自己确认的收入），直到 A8 侧停用；19,869 行历史产值和 434 行退回按冲销关系迁移。
- 需要财务确认：预付款条款（PREPAY）在无交付验收时即可确认收入，是否符合「收入按履约确认、预收款为合同负债」的准则口径；收入金额以含税额配对成本是否只是管理口径（凭证应取不含税额）；期间重开政策、质保金条款（蓝图待裁决清单中的同名项）；收入确认口径以 ACC-R01 为准，词表中「投入法、产出法并存」的旧表述随 ADR-0018 的改名评审一并更正。""",
"`03-通用语言.md`；`income2.0/05-收入确认域-状态机.md`；`08-FORCITIS Operating Accounting Taxonomy.md`；`docs/design/过账引擎取数与规则配置项.md`、`U8C凭证传递接口文档.md`；PSA 边界蓝图 v2.3（Claude Design 只读读取）",
"各 BC 自行写凭证（老系统现状：多条结转路径并存，`/fst/accounting/wip` 与新结转引擎重复）；可更新的事实表加审计日志（无法证明历史未被改写）"),
("0017", "与《PSA 边界蓝图 v2.3》对齐：沿用 PSA 子域划分，蓝图作为约束来源", "已采纳（用户 2026-10-14：「采纳 ADR-0016 和 ADR-0017」）",
"""- Claude Design 正本中附有用户的《PSA 边界蓝图 v2.3》：13 个业务域（D1–D13）+ 7 个平台域、27 个逻辑 BC，DD-1 至 DD-8 已裁决，并写明「新系统另起仓库」。
- PSA 仓库自己也有已定稿的 12 个子域划分（`02-子域划分.md`），本方案的模块结构（ADR-0003）沿用它。
- 两套划分的切法不同，但多数 BC 能一一对应；蓝图的技术栈（DD-8：Kotlin 2.1 / JDK 21 / Boot 3.5 / jOOQ / PG 16 / Keycloak）早于 ADR-0001，用户本次指定以 ADR-0001 为参照。""",
"""1. **模块结构**沿用 PSA 子域划分（已定稿、与现有代码和词表一致），不改成蓝图的 D1–D13；但每个 BC 在 `package-info` 中登记对应的蓝图 BC 编号，便于以后机械比对。对照见下表。
2. 蓝图中与权责发生制相关的**不变量**（DD-1、DD-2、DD-3，BC-10 的同事务边界，D13 只读）作为硬约束采纳，落在 ADR-0016。
3. **技术栈**按本项目的评估确定，不沿用 DD-8：后端以 ADR-0001 为参照（Java 25 / Boot 4.1 / PG 18 / JdbcClient / Spring Security），前端为 React（ADR-0013）。Keycloak 不引入，鉴权见 ADR-0009。
4. 蓝图中超出 PSA 现有功能的范围（D11 跨法人结算、D12 集团合并、DD-6 自任法定账、自建轻量 CRM 的全部能力）不在本 brief 内，列为后续路线。

| PSA BC（模块） | 蓝图 BC |
|---|---|
| `presales.customer` / `presales.quoting` | BC-01 Client & Pursuit（SD-1a、1d） |
| `contract.baseline` | BC-02 Contract、BC-03 Rate & Pricing Policy |
| `contract.changeorder` | BC-02（ChangeOrder） |
| `contract.riskdelivery` | BC-02 监控（无合同交付、风险敞口为 PSA 特有） |
| `delivery.execution` | BC-04 Project & WBS、BC-05 Planning & Resource |
| `delivery.acceptance` | 无直接对应：PSA「验收即确认收入」，向 BC-11 提供验收事实 |
| `cost.timesheet` / `expense.spend` | BC-06 Work Capture（SD-5a、5b） |
| `cost.costing` / `procurement.subcontracting` | BC-07 Costing（含 SD-6c 分包） |
| `operations.facts` | BC-08 Posting（执行 + 冲销） |
| `operations.policy` | BC-09 Reconciliation |
| `receivables.wip` | BC-10 Billing & WIP、BC-11 Revenue Recognition & Contract Asset |
| `receivables.collection` | BC-12 AR & Cash |
| `accounting.period`（新增） | BC-13 Period Control |
| `accounting.ledger` | BC-14 GL & R2R（本期不自任法定账） |
| `reporting` | BC-15 Analytics（只读） |
| `organization.workforce` | BC-P8 Workforce |
| `platform.identity` / `approval` / `integration` / `audit` | BC-P1 / P2 / P5 / P6 |
| `operations.planning` / `budgeting`、`organization.payroll` / `incentive`、`km.knowledge` | 蓝图未覆盖（PSA 特有或蓝图排除） |""",
"""- 以后如果决定按蓝图重组，可以借助登记的编号机械迁移，不需要重新分析。
- 蓝图的「待裁决 18 项」中与本 brief 相关的（收入方法与场景映射、期间重开政策、质保金、C4 就绪前提）并入 `RULE_REVIEWS.json` 的评审范围。""",
"PSA 边界蓝图 v2.3（Claude Design「Forcitis One Design System」uploads，只读）；`02-子域划分.md`；GLOSSARY.md §1",
"按蓝图 D1–D13 重组模块（与现有代码、词表和 12 个子域不一致，迁移成本高且没有行为收益）；忽略蓝图（会丢掉已裁决的财务不变量）"),
("0018", "统一语言：强制登记、自动起草、对齐准则与行业标准", "已采纳（用户 2026-10-14：「采纳 ADR-0018」；起因：「改造过程中 DDD 的统一语言会自动登记吗？这里的语言要符合中文英文行业、规范标准的习惯」）",
"""- 现状：词源是老仓库的 `03-通用语言.md`，GLOSSARY.md 由脚本生成并补上模块路径和老名对照；命名守护只禁止拼音、老名和含糊词，**不检查新代码里的领域词是否已登记**，也不检查用语是否符合准则和行业标准。
- 审计中发现的例子：「合同资产」的代码名是 `WipAccount`，而准则用语是合同资产（IFRS 15：contract asset），WIP 是管理口径的未开票价值，两者不是一回事；还有 `PlLedger`、`CarryBatch`、`CostWip` 这类自造写法。""",
"""1. **单一来源**：新仓库 `forcitis-one-psa` 的 `glossary/terms.yaml` 是统一语言的唯一来源（初版由老词表加标准审计生成）。每条术语包括：中文、英文代码名、snake_case、类别（实体 / 值对象 / 事件 / 命令 / 状态 / 指标）、所属模块、中文定义、标准依据、可信度、别名（含老名，标注废弃）、禁用写法、状态（已批准 / 提议中）。GLOSSARY.md、数据字典、OpenAPI 的术语说明、前端界面文案键都由它生成，老仓库的 `03-通用语言.md` 冻结为历史版本。
2. **用语标准的优先级**：
   1. 法规与会计准则：中文用《企业会计准则》原文（如第 14 号收入：履约义务、交易价格、合同资产、合同负债、合同履约成本、履约进度）；英文用 IFRS 15 与 IFRS XBRL 分类标准的用语；
   2. 工程与项目管理标准：中文用国标（GB/T 50326、GB/T 50358、GB 50500 中的变更、签证、索赔、质量保证金、竣工结算）；英文用 PMI PMBOK / AACE（WBS、WorkPackage、EstimateAtCompletion、EarnedValue）与 FIDIC（Variation、Claim、RetentionMoney）；
   3. 专业服务行业的通行写法（Timesheet、TimeEntry、Utilization、RateCard、WIP），用于前两类没有覆盖的概念；
   4. 公司内部用语只作别名，不作代码名；禁止拼音和自造缩写。
   - **方言**（用户 2026-10-14 裁定：「所有跟正式用语不符合的，都归为我们公司内部的方言，新系统一律采用正式用语，不把方言带入新系统，不确定哪些是正式用语，哪些是方言可以登记用语表，我来确认」）：
     - 凡与正式用语不符的叫法（业务口头、旧系统、A8 等外部系统里的叫法）都是方言，登记为别名类型 `dialect` 并列入禁用写法，只用于检索和沟通，不进入代码、界面文案、接口文档、列注释；
     - 判定不了的标为 `undetermined`，汇总在 `glossary/TERM_CONFIRMATION.md`，由业务 Admin 确认；确认前不得在新代码中使用；
     - 没有外部标准对应的公司自有概念（如经营口径、无合同风险敞口），以词表定义为正式用语，同样经确认；
     - 已裁定：「产值确认单」「产值」是「收入确认单」的方言。
     - 2026-10-14 业务 Admin 在用语确认页上确认了全部 197 项（A 组 4 项为方言；B 组 G-8 至 G-29 共 59 项同意改为标准用语，原叫法归为方言；C 组 134 项公司自有概念确认为正式用语），`terms.yaml` 293 条全部已批准，方言别名 33 个全部列入禁用写法。确认记录：`glossary/decisions-2026-10-14.json`。
3. **强制登记**：构建时的守护测试扫描领域、接口、事件、Web 层的类型名、字段、枚举、方法，以及迁移脚本中的表名和列名，拆成单词后必须能在词表或通用词白名单中找到；出现禁用写法即失败；列注释的中文必须与词表中文一致（G37–G39，前端界面文案与 OpenAPI 模式名随后接入）。
4. **自动起草，人工批准**：守护失败时自动生成可直接粘贴的词条草稿（`target/glossary-draft.yaml`）。agent 或开发在同一个 PR 中补全中文、定义和标准依据，状态为「提议中」；会计类术语由财务确认，业务术语由业务 Admin 确认，确认后改为「已批准」。「提议中」的术语可以先用，但每次构建都会列出提醒。
5. 现有改词建议 G-1 至 G-7 之后，标准审计提出的改名为 G-8 至 G-29（共 22 条），见 `glossary/STANDARDS_AUDIT.md`，与 G-1 至 G-7 一起评审；其中 G-8 至 G-13、G-16、G-17、G-20、G-21、G-27 涉及会计口径，须财务确认。要点：合同资产 `WipAccount` → `ContractAsset`，管理口径的未开票 WIP → `UnbilledWip`；成本侧 `CostWip` → `ContractFulfilmentCost`（合同履约成本）；补「合同负债 `ContractLiability`」；`ProgressRecognized` → `RevenueRecognition`；「完工百分比 `Poc`」→「履约进度 `MeasureOfProgress`」；质保金 → 「质量保证金 `RetentionMoney`」并补「缺陷责任期」。""",
"""- 统一语言不会再静默漂移：新词不登记，构建就不通过；登记的同时就要说明标准依据。
- 词表评审成为每个阶段的入口条件之一：本阶段涉及的术语全部为「已批准」，或「提议中」的已经列明并有评审日期。
- 改名会影响老表映射和反向同步，改名表要同步更新 NAMING_STANDARD.md §6 的老名对照。""",
"GLOSSARY.md；`glossary/terms.yaml`（293 条：已批准 233、提议中 60；通用词白名单 158 个；生成器 `glossary/build_terms.py`）、`glossary/STANDARDS_AUDIT.md`（G-8 至 G-29）。缺口：OAT 正本 `08-财务原语.md` 及其映射 CSV 不在本机，未能与已有原语映射比对，拿到后补一轮；architecture-guardrails/backend 的 `GlossaryTest`：`mvn verify` 56 项全绿（新增 4 项）；注入 `SiteVisaRecord`（未登记词）、`ManHourEntry`（禁用写法）、列注释「预计工时」与词表「计划工时」不一致、未登记新表 `site_visas`，四种全部被拦下，失败信息附可粘贴的词条草稿（negative-test.log）。盲区：前端与 OpenAPI 未接入；方法参数、常量、私有成员只受禁用写法检查；不识别 RENAME；`common_words` 白名单须人工评审，防止塞入领域词绕过",
"只靠人工维护词表（会漂移，agent 不会主动登记）；全自动登记、不经人工批准（会把自造词合法化）"),
("0019", "退出 yudao（ruoyi-vue-pro）脚手架：只迁移在用能力，不迁移框架", "已采纳（用户 2026-10-14：「采纳 ADR-0019」；起因：「我们现代化如何处理 ruoyi 脚手架的？」）",
"""- PSA 后端建在 yudao（ruoyi-vue-pro）微服务脚手架上：gateway、system、infra、bpm 四个脚手架服务，加 27 个 `yudao-spring-boot-starter-*`；业务代码在 fst。
- 生产只读数据（近 180 天）显示脚手架能力的实际使用面很窄：
  - 调用量：`system/auth` 42.2 万次、字典 21.7 万次、文件 9.0 万次、用户 4.3 万次、部门 1.5 万次；代码生成、租户、通知公告、OAuth2 客户端、社交登录、敏感词等合计不到 30 次；
  - 表：菜单 489、角色 47、角色授权 2,155、字典 119 类 465 项；操作日志 80.5 万行、API 访问日志 490 万行、登录日志 17.9 万行、短信日志 23.3 万行；邮件日志 0、代码生成表 0；
  - 多租户开启，但 fst 中没有任何租户引用；`@DataPermission` 252 处关闭、41 处开启；
  - 144 个 Controller 中 34 个从未被调用，主要是脚手架自带功能；fst 中有 54 处 `TODO 芋艿` 模板残留。""",
"""**不迁移脚手架本身**：新仓库不引入 yudao 的任何模块、starter 或基类；只把生产在用的能力按 BC 重建，其余随老系统下线。

| 脚手架能力 | 去向 |
|---|---|
| gateway、四个微服务、28 个 Feign 客户端 | 合并为模块化单体，跨模块调用走 `api` 包（ADR-0003）；nginx 负责 TLS 和限流 |
| OAuth2 令牌（`system_oauth2_*`） | Spring Security 7 + 服务端会话（ADR-0009）；过渡期只读校验老令牌 |
| 菜单 / 角色 / 按钮权限 | 类型化 `Permission` 权限码 + 角色；菜单由前端路由按权限码生成；老角色授权按映射表迁移 |
| 数据权限 `@DataPermission`（自动改写 SQL） | 显式数据范围，在应用服务和查询中声明（ADR-0009），不再隐式改写 SQL |
| 多租户 | 去掉；法人维度由 `LegalEntity` 承担 |
| 字典 | 状态、类型这类决定业务行为的字典改为代码枚举 + 数据库 `CHECK`（与老仓库 `enums.registry` 方向一致）；只有展示用、可由管理员维护的选项保留为平台配置 |
| 错误码 | sealed 结果类型 + 类型化错误码 |
| 文件 | 阿里云 OSS，平台文件服务 |
| 操作日志、登录日志 | `platform.audit` |
| API 访问日志、错误日志 | 不写业务库，改为结构化日志进 SLS |
| 短信（日志 23.3 万行） | 生产只读核实：几乎全部是审批通知（流程被通过 12.4 万、任务被分配 10.9 万、流程被驳回 463，近 30 天仍新增约 1.2 万条），但 **发送状态全部是「初始化」，没有一条发送成功或失败**，即短信从未真正发出。**不迁移短信**，审批通知统一走企业微信（`platform.integration`）（用户 2026-10-14 决定：「短信不迁移，审批通知走企业微信」） |
| Excel 导入导出 | 需要的模块各自实现，共用一个小工具 |
| MyBatis-Plus、Redis、RocketMQ、XXL-JOB、Nacos | 已由 ADR-0002 替换 |
| Flowable 封装 | Flowable 8（ADR-0007） |
| 代码生成、表单设计器、支付、邮件、站内信、社交登录（企业微信除外）、Redis 监控、示例代码 | 不迁移 |
| 前端 yudao-ui-admin-vue2 框架（布局、路由、权限指令） | 由 React 外壳重建（ADR-0013） |

数据：`system_users`、`system_dept`、`system_role*` 的数据按映射迁移到 `platform.identity` 和 `organization.workforce`；`system_*`、`infra_*` 的表结构不迁移；日志类表按保留期只读归档。""",
"""- 服务名 `fst` 随之消失：新系统没有「服务」，只有按子域命名的模块（`com.forcitis.one.<子域>.<BC>`），接口为 `/api/v1/<子域>/...`，schema 为 `<子域>_<BC>`；`fst` 列入词表禁用写法（G38）。老系统内不改名（3,657 个 Java 文件的包名、Nacos 服务名、网关路由、Feign 常量、XXL-JOB 执行器名、PC 28 个和 H5 22 个文件中的 `/fst/` 路径、CI/CD、分贝通配置的回调地址都依赖它，改名没有业务收益且风险高）。过渡期老 URL `/admin-api/fst/**` 由兼容门面承接（ADR-0011）；分贝通回调 `/app-api/fst/fenbeitong/events/*` 在 Phase 3 切换时与分贝通协调改为新地址，或由门面转发。
- 新代码里不会出现 `cn.iocoder.*`、`BaseDO`、`CommonResult`、`TenantBaseDO` 等脚手架类型；守护：ArchUnit 禁止依赖 `cn.iocoder..`（G40），词表守护把 `DO`、`TODO 芋艿` 等列为禁用写法。
- 一些脚手架「免费」提供的能力需要自己写：权限管理页、字典维护页、文件服务。按 DS_GAPS.md 列入 Phase 5 和通用组件。
- 老系统迁移期仍依赖脚手架，安全补丁照常打在老系统上（ADR-0015）。""",
"runtime/prod_usage.tsv（接口调用量与表行数，只读采集）；ASSESSMENT.md（34 个未调用的 Controller、模板残留）",
"同栈升级脚手架到新版 yudao（Boot 3 / JDK 17）：保留了微服务和全部脚手架负担，与模块化单体方向相反；把脚手架整体搬进单体（继承隐式 SQL 改写、多租户等没人用的复杂度）"),
("0020", "会话管理：状态在文件里、一会话一工作单元、固定开场与收尾协议", "已采纳（用户 2026-10-14 选择「采纳并移入新仓库」；起因：「整个改造过程会话管理很重要，需要明确的方案。」）",
"""- 现代化要跨越多个阶段、数月、多人并行；单个会话会被自动压缩、会结束，聊天记录不能作为记录。
- 本次评估会话已经压缩过一次，靠 INTENT、ADR、brief 等文件接上；工作区 `psa-modernization/` 目前不是 git 仓库，状态文件没有历史。""",
"""按 `SESSION_PLAYBOOK.md` 执行，要点：
1. **状态在文件里**：`STATUS.md`（现在在哪、谁在做哪个单元、下一条命令）、`SESSION_LOG.md`（每个会话一条，只追加）、`INTENT.md`（用户决定原话）、`adr/`、brief 勾选、各专题文件；跨会话记忆只放长期偏好与授权；凭据只在 `~/.modernize/psa/`。
2. **一个会话一个工作单元**（一个阶段的一个步骤或一个 BC 切片），会话命名 `psa-<阶段>-<单元>-<动作>`，可用 `claude --resume` 找回。
3. **固定开场协议**：`modernize-status` → 读 `STATUS.md` 与最后一条日志 → 认领单元 → 核对入口条件 → 一句话确认目标（必要时 `/goal`）。
4. **固定收尾协议**：用户决定写入 INTENT / ADR → 勾选 brief → 追加日志（含下一条命令）→ 更新 `STATUS.md` → 重新生成 brief 与报告 → 清理临时文件、核对无凭据外泄。
5. **并行**只在互不相交的单元上进行，写代码的会话各用独立 worktree 和分支；会话间用 `SendMessage` 协调。
6. **必须由用户决定的事**（审批、口径、用语、推送与部署、生产变更、设计正本写入、凭据轮换）一律停下等待，用户原话入档。
7. **版本化**：`analysis/psa/` 已移入 `FORCITIS/forcitis-one-psa` 的 `docs/modernization/`（2026-10-14），每个会话收尾时提交，gitleaks 一并守护。
8. **总调度会话 `psa-coordinator`**（用户 2026-10-14：「新开 psa-coordinator 会话负责调度」）：只做调度，派工、核对、衔接、把需要用户决定的事汇总给用户；建议与用户决定分开标注，不替用户批准（`SESSION_PLAYBOOK.md` §11）。""",
"""- 任何会话都能接上任何上一次会话，不依赖某个会话的记忆。
- 多一点收尾成本（每个会话几分钟），换来可追溯与可并行。
- 需要用户确认：是否把分析产物移入新仓库并按会话提交。""",
"SESSION_PLAYBOOK.md；STATUS.md；SESSION_LOG.md（首条为本评估会话）",
"一个长会话做到底（上下文压缩后细节丢失、不可并行、出错难回溯）；只靠跨会话记忆（记忆只适合长期偏好，不适合项目状态）"),
]
import os
idx = ["# FORCITIS one 现代化 ADR", "", "本目录记录 PSA → FORCITIS one 现代化中的架构决策。STACK_DECISION.md、NAMING_STANDARD.md、GUIDING_PRINCIPLES.md、MODERNIZATION_BRIEF.md 只引用这里的决定，不另立结论。参照文档：forcitis-one `docs/adr/0001-tech-stack.md`（只参照文本，不参照代码）。", "", "| ADR | 标题 | 状态 |", "|---|---|---|"]
for no, title, status, ctx, dec, cons, ev, alt in ADRS:
    fn = f"ADR-{no}.md"
    open(fn, "w", encoding="utf-8").write(f"# ADR-{no}：{title}\n\n- **状态**：{status}\n- **日期**：2026-10-14\n\n## 背景\n\n{ctx}\n\n## 决定\n\n{dec}\n\n## 后果\n\n{cons}\n\n## 证据\n\n{ev}\n\n## 备选方案（未采用）\n\n{alt}\n")
    idx.append(f"| [ADR-{no}](ADR-{no}.md) | {title} | {status} |")
open("README.md", "w", encoding="utf-8").write("\n".join(idx) + "\n")
print(len(ADRS), "ADRs written")
