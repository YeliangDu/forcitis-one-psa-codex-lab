# 目标技术栈定稿 — FORCITIS one 现代化

日期：2026-10-14 · 产品：**FORCITIS one** · 入口：**one.forcitis.com**（ADR-0001）

**参照**：forcitis-one `docs/adr/0001-tech-stack.md`，只参照 ADR 文本，按用户要求不参考 forcitis-one 代码。

**依据**：
- `ASSESSMENT.md`：基线评估；
- `runtime/`：生产只读数据；
- `BUSINESS_RULES.md`：315 条规则；
- `SECURITY_FINDINGS.md`；
- 架构评审意见（对抗式，下文 §3）；
- Claude Design 设计系统与《PSA 边界蓝图 v2.3》（只读）；
- 用户在评估过程中的决定（逐条标注）；
- `architecture-guardrails/`：前后端模板工程，全部实测。

**每一项决定的完整论证都在 `adr/`**，本文只做汇总。状态说明：
- **已采纳**：用户明确决定；
- **提议**：基于评估提出，待确认。

**定稿状态（2026-10-14）**：用户对剩余 9 项选择「全部定稿」，本表所有技术栈条目均已采纳，技术栈最终确定。

## 1. 最终技术栈

| 层 | 定稿 | ADR | 状态 | 实测 |
|---|---|---|---|---|
| 运行时 | Java 25 LTS | 0002 | **已采纳**（用户 2026-10-14 定稿） | ✔ JDK 25.0.4 |
| 框架 | Spring Boot 4.1.x（4.1.1） | 0002 | **已采纳**（用户 2026-10-14 定稿） | ✔ |
| 架构 | Spring Modulith 2.1.1 模块化单体；模块为 `<子域>.<BC>`，由自定义识别策略识别；依赖图入库并由测试强制执行 | 0003 | 已采纳 | ✔ |
| 架构守护 | 深模块、窄接口；后端 G1–G33、依赖图 D1、DDL D2–D3；前端 F1–F14 | 0004 | 已采纳 | ✔ 注入的错误全部被拦下 |
| 命名 | 词表 `03-通用语言.md` 是唯一词源；英文、行业惯用；GLOSSARY.md 给出模块路径和老名对照 | 0005 | 已采纳 | ✔ |
| 类型 | 强类型：值对象、类型化 ID、sealed 结果、枚举；契约中禁止 Object 和 Map | 0006 | 已采纳 | ✔ |
| 数据库 | **PostgreSQL 18**（FORCITIS one 专用 RDS 实例，杭州已可开通），schema 为 `<子域>_<BC>` | 0002 | **已采纳**（用户：「就用 PG 18」；偏离 ADR-0001 的 17） | ✔ Testcontainers 18.6，52 项全绿 |
| 持久化 | **只用 `JdbcClient`**，手写仓储 + 类型化映射；不用 Spring Data JDBC、JPA、MyBatis | 0002 | **已采纳**（用户 2026-10-14 定稿；偏离 ADR-0001） | ✔ |
| 迁移 / 数据字典 | Flyway，一张历史表、按模块分目录；DDL 命名守护；每列中文注释；数据字典自动生成 | 0011 | 已采纳 | ✔ |
| 流程 | **Flowable 8，本阶段引入**（BPMN + DMN），放在 `platform.approval` / `platform_approval` schema | 0007 | 已采纳（偏离 ADR-0001 的「推迟」） | ✔ |
| HTTP API | Spring MVC + springdoc OpenAPI 3；**版本化** `/api/v{n}/…`（Spring 7 原生） | 0008 | 已采纳 | ✔ v1 返回 200 / v2 返回 400 |
| 鉴权 | 默认拒绝；Spring Security 7 + 服务端会话（Spring Session JDBC）+ CSRF；企业微信 OAuth2 / 致远 SSO / 签名一次性票据；类型化权限 + 数据范围；过渡期只读校验老令牌 | 0009 | 已采纳 | ✔ 匿名 401 / 权限不足 403 |
| 业务编号 | `platform.numbering`：编号规则由管理员配置，不重号、不跳号，按法人和周期计数 | 0010 | 已采纳 | ✔ 含 20 线程并发 |
| 模块间事件 | Modulith 事件发布登记表（JDBC）；不保留 RocketMQ | 0002 | **已采纳**（用户 2026-10-14 定稿） | — |
| 批处理 / 定时 | 作业表租约（唯一约束）；`@Scheduled` + ShedLock + 运维页面；不保留 XXL-JOB、Redis 锁 | 0002 | **已采纳**（用户 2026-10-14 定稿） | 待骨架实测 |
| 缓存 / 配置 / 密钥 | Caffeine；Spring 外部化配置 + 阿里云 KMS；不保留 Redis、Nacos；gitleaks 守护 | 0002 | **已采纳**（用户 2026-10-14 定稿） | — |
| 前端 PC | **React 19 + TypeScript strict + Vite + Ant Design 5 + TanStack Query + Zod + React Router 7**，作为 one.forcitis.com 外壳；与 H5 统一为一个框架（独立评估，见 ADR-0013 比较表） | 0013 | **已采纳**（用户：「前端就用 React」） | ✔ React 版守护 verify 全绿，F1–F14 注入全部拦下 |
| 设计系统 | 正本为「**Forcitis One Design System**」；令牌驱动，组件只从 `shared/ui` 取 | 0013 | **已采纳**（用户：「设计正本就用 Forcitis One Design System，还不完善在现代化过程中不断完善」；先补正本再写页面，差距见 DS_GAPS.md） | ✔ 令牌 CSS 已原样收录，守护实测 |
| 前端 H5 | 保留 Taro 4 + React，与 PC 共用 OpenAPI 客户端、Zod、查询层和令牌 CSS | 0013 | **已采纳** | — |
| 业财主干 | 权责发生制：`operations.facts` 单一过账管道、明细事实只追加（只能冲销）、`accounting.period` 按 (期间, 法人) 设闸、科目映射只在 `accounting.ledger`（DMN）、收入确认为无状态规则层；U8C 本期仍为法定账 | 0016 | **已采纳** | 守护 G34–G36 待骨架实现 |
| 与边界蓝图的关系 | 模块沿用老系统子域划分并登记蓝图 BC 编号；蓝图财务不变量作为硬约束；D11 / D12 / DD-6 不在本 brief 范围 | 0017 | **已采纳** | — |
| 测试 | 以模块级集成测试为主（Testcontainers）；单测只补边界、用最少用例；端到端只覆盖 4 条人物流程 | 0012 | 已采纳 | ✔ 行覆盖 85.9% |
| 可观测性 | Micrometer 指标经 Prometheus 端点由 ARMS 抓取，ECS JSON 日志由 SLS 采集；关联号 correlationId 为唯一追踪键，不用 OpenTelemetry 与追踪探针（GUIDING_PRINCIPLES §11）；对同步延迟、事件发布、作业、登录失败率告警 | 0002、0042 | **已采纳**（用户 2026-10-14 定稿） | — |
| 部署 | 新单体独立主机部署，与老系统分开；nginx 负责 TLS 和限流；去掉网关 | 0002 | **已采纳**（用户 2026-10-14 定稿） | — |
| 数据过渡 | 保留雪花 ID；逐列所有权矩阵；老 URL 兼容门面；BI 读 `reporting` | 0011 | **已采纳**（用户 2026-10-14 定稿） | — |
| 统一语言 | `docs/glossary/terms.yaml` 为唯一来源；用语按会计准则 → 国标 / PMBOK / FIDIC → 行业通行写法的优先级；新词不登记构建即失败（G37–G39）；自动生成词条草稿，财务 / 业务 Admin 批准 | 0018 | **已采纳** | ✔ GlossaryTest 四类注入全部拦下 |
| 脚手架 | 退出 yudao（ruoyi-vue-pro）：不迁移框架、starter 和基类，只按 BC 重建生产在用的能力（认证、权限、字典、文件、审计日志）；多租户、代码生成、支付等不迁移；新代码禁止依赖 `cn.iocoder..`（G40） | 0019 | **已采纳** | 依据生产调用量与表行数 |
| 指导思想 | DDD、DAMA、TOGAF、BPMN、DMN、OLTP、OLAP | 0014 | 已采纳 | GUIDING_PRINCIPLES.md |
| 安全 | 先在老系统止血（harden 补丁，3 轮复审），再推进各阶段 | 0015 | 已采纳 | SECURITY_FINDINGS.md |

## 2. 相对 ADR-0001 的变化

| ADR-0001 | FORCITIS one 定稿 | 理由 |
|---|---|---|
| 流程引擎：推迟，审批先做规则型 | Flowable 8 本阶段引入 | 用户决定；生产已有 10 个流程，其中 TIMESHEET 实例量很大；自研规则型审批等于自己写一个流程引擎 |
| 持久化：Spring Data JDBC | 只用 JdbcClient | Spring Data JDBC 的注解与「领域层不依赖 Spring」（G7）冲突；子集合删除后重插会破坏行级身份（行级审批、反向同步、BI 关联都依赖它） |
| 模块按 BC 划分 | 模块按「子域.BC」划分，用自定义识别策略加依赖图文件 | 沿用 psa 仓库已定稿的 12 个子域；Modulith 的嵌套模块不允许 BC 之间直接依赖（已实测） |
| 未规定 | API 版本化、鉴权模型、业务编号、DDL 规范、前端与设计系统、测试策略、强类型 | 用户要求 |
| 未规定（蓝图 DD-8 定为 Vue 3） | 前端 React 19 + TS | 与 H5 统一框架；迁移成本两者相同（都要逐页重建）；TSX 类型检查和 lint 无盲区；对 agent 更友好 |
| 未规定（蓝图 DD-1 / 2 / 3） | 单一过账管道、只能冲销、(期间, 法人) 期间闸门 | 权责发生制是 FORCITIS one 的核心；老系统已有过账引擎、红冲和两套 U8C 账 |
| PostgreSQL 17 | PostgreSQL 18 | 新库没有升级成本，上线后再升大版本要停机和回归；支持期多一年（到 2030-11）；时态约束 `WITHOUT OVERLAPS` 适合费率卡和组织快照的生效区间，`RETURNING OLD/NEW` 适合只追加事实与冲销；杭州 RDS 已可开通；守护模板实测全绿 |
| 不变 | Java 25、Boot 4.1、Modulith 2.1.1、一 BC 一 schema、Flyway、OpenAPI、发件箱、ArchUnit + Testcontainers、Money = 金额 + 币种、LegalEntity | — |

## 3. 架构评审意见的处理

| 评审项 | 处理 |
|---|---|
| B1 两个接班系统并存 | 用户已明确：现代化产物就是 FORCITIS one（one.forcitis.com），新代码放在新建仓库 `forcitis-one-psa` |
| B2 试点依赖的平台能力排在 Phase 6 | 采纳：Phase 1 拆成 1a（平台桥接：身份、组织快照、企业微信通知、合并待办、编号）和 1b（报价） |
| B3 老 URL 被路由走后老调用方会坏 | 采纳：每个切换的域都提供老 URL 兼容门面，并作为入口条件 |
| B4 行级写入冲突 | 采纳：每个阶段的入口条件要求逐列所有权矩阵，并有反向同步契约测试 |
| M1 审批引擎只在最简单的流程上验证 | 因用户决定引入 Flowable 而转化为：Phase 1a 回放生产 TIMESHEET 实例的路由结果；在途实例按「排空」处理 |
| M2 Spring Data JDBC 与 G7 冲突 | 采纳：只用 JdbcClient |
| M3 数据迁移细节不足 | 采纳：保留雪花 ID；每个阶段要有数据迁移规格（字段映射、翻译表、延迟、对账查询） |
| M4 BI 被低估 | 采纳：Phase 1 入口要求 BI 用表和用列清单、负责人、BI 查询契约测试 |
| M5 缺回滚 | 采纳：每个阶段的出口条件含回滚演练 |
| M6 按部门灰度并双写行不通 | 采纳：改为影子计算比对，在期间边界一次性切换 |
| M7 共用主机、共用即将到期的实例 | 采纳：独立主机，FORCITIS one 专用 RDS |
| M8 鉴权模型未定义 | 采纳：ADR-0009；并补抽 identity 域的规则 |
| M9 批处理性能和锁 | 采纳：作业表租约；用 XXL-JOB 日志建立耗时基线 |
| M10 可观测性 | 采纳：Phase 1a 出口条件含告警 |
| M11 新旧 PC 并存 | 采纳：one.forcitis.com 的 React 应用作为外壳 |
| M12 守护有漏洞、模板有坏示范 | 采纳并实测：补 G4、G11、G28–G33、依赖图；模板改正（`TimesheetSubmitted`、`Optional`、真实 SQL、权限、请求 / 响应 record） |
| m2 金额币种放到单据头 | **不采纳**：ADR-0001 的验收条件明确禁止业务表出现无 currency 的裸金额列 |
| m3 无空号编号 | 采纳：ADR-0010 |
| m4 Flyway 历史表 | 采纳：一张历史表，按模块分目录 |
| m5 后缀白名单与黑名单 | 采纳：规范改为黑名单 + 推荐后缀表 |
| m1 brief 格式 | 采纳：去掉时长描述；条件写成可检查的形式；安全止血改为入口条件 |
