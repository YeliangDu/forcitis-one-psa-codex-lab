# MODERNIZATION BRIEF — 老系统 → FORCITIS one

日期：2026-10-14 · 状态：**待审批**

**输入**（均为 2026-10-14 生成）：INTENT、PREFLIGHT、ASSESSMENT、topology.json、SECURITY_FINDINGS（3 轮补丁复审）、BUSINESS_RULES（315 条）、DATA_OBJECTS、GLOSSARY、STACK_DECISION、NAMING_STANDARD、GUIDING_PRINCIPLES、AGENT_FAILURE_MODES、`adr/`（15 篇）、`runtime/`（生产只读采集）、`architecture-guardrails/`（实测模板）。尚无 `RULE_REVIEWS.json`。

## 1. Objective

把老系统重建为 **FORCITIS one**（one.forcitis.com，ADR-0001）。

- **从**：yudao 微服务底座 + Java 8 / Spring Boot 2.7 + SQL Server 2016 + Flowable 6.8 + Vue 2。
- **到**：Spring Modulith 模块化单体，Java 25 / Spring Boot 4.1、PostgreSQL 18（schema 为 `<子域>_<BC>`）、JdbcClient、Flyway、Flowable 8（BPMN + DMN）、API 版本化、默认拒绝的鉴权、可配置的业务编号，前端为 React + Taro + 设计系统。定栈见 `STACK_DECISION.md` 和 `adr/`。

迁移期间老系统持续运行，按 BC 绞杀式切换。行为上**功能复刻、行为现代化，顺手修复已知缺陷**（用户原话）。

为什么是现在：
- 技术栈全线过保：Boot 2.7、Vue 2 已 EOL，Boot 3.5 的 OSS 支持也已于 2026-06-30 结束。
- 生产存在可被公网利用的免密登录（SEC-001）和入库的明文密钥（SEC-006 / 012）。
- 5 个微服务和全部中间件挤在一台 15G 内存的 ECS 上，只服务 335 个活跃用户。
- 核心规则锁在没有测试的老 service 层超大类里。

## 2. Target Architecture

```mermaid
C4Container
  title FORCITIS one 目标架构（终态）
  Person(emp, "员工 / 项目成员", "企业微信 H5 + PC")
  Person(pm, "售前 / PM / 财务 / 管理层", "PC")
  System_Ext(wecom, "企业微信", "OAuth / 消息 / 回调")
  System_Ext(a8, "致远 A8 OA", "组织主数据、SSO")
  System_Ext(u8c, "用友 U8C", "凭证、客商")
  System_Ext(fbt, "分贝通", "差旅订单 / 对账")
  System_Ext(km, "KM 门户", "SSO 跳转")
  System_Ext(bi, "BI 系统", "只读报表")
  Container_Boundary(one, "FORCITIS one 模块化单体（one.forcitis.com · Java 25 / Spring Boot 4.1 / Spring Modulith 2.1）") {
    Container(web, "PC 前端", "React 19 + TS + Vite + Ant Design 5 + TanStack Query + Zod", "设计令牌；OpenAPI 生成客户端")
    Container(h5, "H5 前端", "Taro 4 + React", "企业微信内嵌；与 PC 共用 OpenAPI 客户端、Zod、查询层、令牌 CSS")
    Container(app, "FORCITIS one 应用", "Spring MVC /api/v1 + springdoc", "模块（子域.BC）：platform.identity / approval / integration / audit / numbering、organization.workforce、presales.customer / quoting、delivery.execution / acceptance、cost.timesheet / costing、operations.facts（单一过账管道）、accounting.period / ledger、receivables.wip、expense.spend、reporting")
    ContainerDb(pg, "PostgreSQL 18（FORCITIS one 专用 RDS）", "schema = <子域>_<BC>", "platform_*（含 Flowable ACT_*、事件发布登记、编号）、presales_*、delivery_*、cost_*、operations_facts（明细事实只追加）、accounting_period、accounting_ledger、receivables_wip、expense_spend、reporting（只读视图）")
  }
  Rel(emp, h5, "HTTPS")
  Rel(pm, web, "HTTPS")
  Rel(web, app, "REST / OpenAPI")
  Rel(h5, app, "REST / OpenAPI")
  Rel(app, pg, "JDBC（JdbcClient）")
  Rel(app, wecom, "防腐层：OAuth、消息、回调验签")
  Rel(app, a8, "防腐层：只读同步组织快照、SSO 验票")
  Rel(app, u8c, "防腐层：凭证 / 客商可靠投递（U8C 仍是法定账）")
  Rel(app, fbt, "防腐层：订单 / 对账可靠投递 + webhook 验签")
  Rel(km, app, "签名一次性票据")
  Rel(bi, pg, "只读 reporting schema")
```

| 老组件（topology.json / 运行时） | 目标组件 |
|---|---|
| `yudao-gateway`（gateway:core）、nginx 直连 48080 / 48088 | 删除；nginx → 单体（切换期按路径分流） |
| `yudao-module-system`（system:*）、fst `service/{sync,dept,user,a8}`、企业微信 / A8 / KM 登录 | `identity` + `org` 模块；`integration-acl`（A8、企业微信、KM） |
| ↳ Proposed revision:（用户 2026-10-14：「两条都同意 (Recommended)」）按已批准架构（REIMAGINED_ARCHITECTURE §2）：A8 只读视图防腐层归 `organization.workforce`，致远验票归 `platform.identity`，`platform.integration` 只含共用企业微信客户端与出站投递；KM 等外部系统走签名票据（`platform.identity`）。 | |
| `yudao-module-infra`（infra:*：文件、代码生成、配置、API 日志） | `base` 模块（文件存 OSS、审计日志）；代码生成和表单设计器不迁移 |
| `yudao-module-bpm`（bpm:*）+ fst workflowhook / workflowform / 审批消费者 + RocketMQ 审批主题 | `platform.approval` 模块（Flowable 8 BPMN + DMN：步骤、条件、行级分路、会签、催办、转交、退回、撤回、截止点）+ 模块内事件 |
| fst `presales/*`、`controller/admin/{quotation,opportunity}`、`service/customer`、成本模板 / 费率 | `presales` 模块（报价、成本估算树、岗位费率读模型） |
| fst `delivery/*`、`service/{project,budget,contract}`、`pmproject` / `projectplan` / `projecttask` | `delivery` 模块（项目台账、WBS 预算与修订、进度 / ETC、交付物与验收） |
| fst `receivables/*`、收入确认 | `receivables` 模块（收入确认、开票、AR） |
| fst `service/{manhour,calendar,resource}`、工时 MQ 处理器、工时任务 | `timesheet` 模块（填报、行级审批、费率锁定、人天、资源负荷） |
| fst `expense/*`（分贝通防腐层 13.8k 行） | `expense` 模块 + `integration-acl/fenbeitong` |
| fst `cost/*`（归集、OH 分摊、结转 / WIP、过账、期初、修正、劳务重分类、利润报表）、`service/{wip,accounting,cost,costsummaryext}` | `cost.costing`（成本计算与分摊，只提交过账候选）+ `operations.facts`（单一过账管道、冲销）+ `accounting.period`（(期间, 法人) 期间闸门）+ `accounting.ledger`（科目映射 DMN、凭证 → U8C 防腐层）（ADR-0016） |
| fst `platform/*`（可靠投递、枚举注册表） | `base`（Modulith 事件发布登记 + 外部投递作业表）；枚举 → 各模块的 `events` / `api` 契约 |
| XXL-JOB 27 个处理器 | 各模块的 `@Scheduled` + ShedLock |
| Redis（缓存 / 锁 / 编号）、Nacos、RocketMQ | 删除：Caffeine、作业表租约、`platform.numbering`、外部化配置 + KMS |
| PC Vue 2.7 / Element（pc:*） | React 19 + Ant Design 5 PC 应用（`shared/ui` 按设计正本封装），按阶段逐页重建 |
| H5 Taro 4 React（h5:*） | 保留 Taro；接口改用 OpenAPI 生成的客户端，按阶段改造 |
| SQL Server `ruoyi-vue-pro` 库 | PostgreSQL 18：各 BC 的 schema + `reporting` 视图；Flowable `ACT_*` 不迁移，只读归档 |

## 3. Phased Sequence

**本方案是假设，Phase 1a 是试点。** 试点暴露出的问题（漏掉的耦合、改变顺序的前置条件、没写下来的环境事实）都在预期之内，会据此修订 brief，试点之后重新生成 brief 是正常路径。

**排序原则**：绞杀模式，依赖少、风险低的先做；行级强耦合的 BC 放在同一阶段切换。

**过渡期通用机制**（每个阶段都适用，ADR-0011）：
- nginx 按路径把已切换的域分流到 FORCITIS one；
- 老 URL 由兼容门面承接，返回老格式；
- 只按**逐列所有权矩阵**反向同步到老表；
- 在途 Flowable 实例在老引擎上走完；
- 每个阶段都能回滚（恢复 nginx 路由后，老系统从反向同步后的状态继续运行）。
- 会话管理（ADR-0020，`SESSION_PLAYBOOK.md`）：每个阶段的每个工作单元在独立会话中完成，按开场 / 收尾协议更新 `STATUS.md` 与 `SESSION_LOG.md`；用户决定以原话入档。
- 统一语言随阶段登记（ADR-0018）：阶段开始时，本阶段涉及的术语在 `glossary/terms.yaml` 中全部为「已批准」，或「提议中」的已列明并排定评审；阶段结束时没有「提议中」的会计类术语。
- 设计正本随阶段完善（ADR-0013 第 7 条）：阶段开始时在 `DS_GAPS.md` 列出本阶段所需而正本缺失的组件，**先补正本、再写页面**；阶段结束时该阶段的差距清零或注明延后原因。

```mermaid
flowchart LR
  S["入口条件：老系统安全止血（ADR-0015）"] --> P1a
  P1a["1a 平台桥接（试点）"] --> P1b["1b 报价"]
  P1b --> P2["2 交付 + 工时"]
  P1a --> P3["3 差旅报销"]
  P2 --> P4["4 成本、收入与核算"]
  P3 --> P4
  P4 --> P5["5 身份全面切换、BI、老系统下线"]
```

#### Phase 1a — 平台桥接（试点）
Command: /code-modernization:modernize-reimagine
Modules: system:service/auth, system:controller/admin/auth, system:service/permission, system:service/dept, system:service/user, fst:service/sync, fst:util/wx, fst:service/message, bpm:service/task, bpm:service/definition, bpm:service/oa, bpm:framework/flowable, bpm:mq/producer, fst:framework/workflowhook, fst:service/workflowform, fst:application/platform, fst:infrastructure/platform, pc:views/bpm/processInstance, pc:views/bpm/task, h5:pages/work/process, pc:layout, pc:router, pc:views/login
Scale: L
Risk: 高。
- 风险一：身份桥接出错会影响所有人登录。缓解：1a 已取消桥接——新系统不读、不写、不校验老令牌表（用户：「1a 删除桥接」），也不信任老系统签发的任何身份；会话失败时退回老系统登录页；登录失败率进入告警。
- 风险二：两套审批引擎并存，待办需要合并。缓解：外壳合并展示新旧待办；新流程只承接 1b 及之后切换的单据；老流程在老 Flowable 上排空。
Entry criteria:
- [x] 本 brief 获批（至少覆盖 Phase 1a 和 1b）——证据：§8 审批块（用户 2026-10-14「批准 Phase 1a 和 1b」）（psa-coordinator 2026-10-14 核对）
- [ ] SEC-001 至 SEC-006、SEC-107 已在老系统止血（`security_remediation.patch` 已部署，或网络层止血已生效：48088 不再对 0.0.0.0/0 开放、`/other-api/admin-api/system/auth/` 返回 403），且 `~/.modernize/psa/SECRETS.local.md` 中列出的凭据已全部轮换
  Proposed revision:（用户 2026-10-14：「老系统先不管，确保新系统没有这些缺陷」；SEC-116「仍然完全不处理」）本条不再阻塞 Phase 1a：新系统不信任老系统签发的任何身份，由 `phase1a/SECURITY_INVARIANTS.md` I-1..I-9 及其验收测试保障；老系统缺陷影响的是老系统自身数据，按用户决定暂不处理。
- [x] 仓库已创建：https://github.com/FORCITIS/forcitis-one-psa（私有，2026-10-14，空仓库）
- [x] 骨架已推入 `forcitis-one-psa`，分支保护、CI（verify + 守护 + gitleaks）已配置——证据：PR #9 骨架与 ci.yml；main 分支保护要求 backend / frontend / gitleaks 三项（用户「只要求 CI 三项通过」）（psa-coordinator 2026-10-14 核对）
- [x] 骨架采用 `architecture-guardrails/backend` 和 `frontend`，在 Mac 本机执行 `./mvnw verify` 和 `pnpm verify` 均全绿——证据：PR #9 起各 PR 的 CI 全绿，1a 会话本机 `./mvnw verify`、`pnpm verify` 报告全绿（psa-coordinator 2026-10-14 核对）
- [ ] FORCITIS one 专用主机和 FORCITIS one 专用 RDS PostgreSQL 实例已开通
- [ ] `shared/design-system/tokens.css` 与设计正本「Forcitis One Design System」的 `tokens/palette.css`、`tokens/semantic.css` 逐项一致（正本有更新时重新同步）
- [ ] 设计正本已完整备份到本地；正本现有组件已取下并作为 `shared/ui`（React）的起点（ADR-0013 第 7 条，代码为源）
- [x] 内核值对象 `Money`、`AccountingPeriod`、`ProjectCoordinate`、`LegalEntityId` 已就位（ADR-0016）——证据：backend kernel 包（PR #21）（psa-coordinator 2026-10-14 核对）
- [x] 已对 identity、org 两个域补抽规则并完成评审（`RULE_REVIEWS.json` 中有对应记录）——61 条见 `rules/IDENTITY_ORG_RULES.md`，标记的 45 条已评审（正确 16、错误 29，2026-10-14，见 RULE_REVIEWS.md 第三批）
- [x] 本阶段涉及的老表（`system_users`、`system_dept`、`bpm_*`、`psa_process_to_be_sent`）已有逐列所有权矩阵——证据：phase1a/COLUMN_OWNERSHIP.md（PR #3，14 表 217 列）（psa-coordinator 2026-10-14 核对）
- [x] 本阶段涉及流程的流程梳理已完成（`process/1a/`，SESSION_PLAYBOOK §13），差异清单逐条有用户决定，并已写入 §5「流程梳理结论」（用户 2026-10-14：「每个阶段实施前增加「流程梳理」步骤……先把这一步写进 SESSION_PLAYBOOK 和 brief 的阶段入口条件」）（Phase 1a 已在实施中，本条为补做；2026-10-14 完成：7 个流程、D-1a-01..181 全部有用户决定，178 条写入 §5，另 3 条为文档更正）
Exit criteria:
- [ ] one.forcitis.com 上线：用企业微信 OAuth 和致远 SSO 登录都能成功；遍历全部接口的匿名访问测试返回 401
  Proposed revision:（检查点 2，用户 2026-10-14：「两条都按建议修订」）匿名访问测试改为「非 `@PublicEndpoint` 的接口一律返回 401，且 `@PublicEndpoint` 集合等于经 CODEOWNERS 评审的白名单快照」；另加无 CSRF 写操作返回 403，上线前从 nginx 外侧黑盒遍历。
- [ ] 组织快照与 A8 的对账查询结果为零差异（人员数、部门数、上级关系）
  Proposed revision:（用户 2026-10-14：「两条都同意 (Recommended)」）追加 §9a 证明项：新系统对 A8 的全部访问 = 视图列白名单（启动时列集合完全相等）+ 致远服务端验票；白名单中没有权责列；不存在写 A8 的路径（DBA 授权只含视图 SELECT，集成测试断言写被拒绝）；`phase1a/A8_ACCESS_REGISTER.md` 中 1a 行状态已更新。
- [ ] 回放从生产导出的 TIMESHEET 流程实例，`platform.approval` 解析出的审批人序列与老系统一致；每条差异都在 `RULE_REVIEWS.json` 中被接受
- [ ] 合并待办中，同一用户在新旧系统中的待办数量与老系统一致
- [ ] 业务编号规则已按统一新格式配置并经管理员确认；老编号作为「原编号」可检索，新旧编号对照可查
  Proposed revision:（检查点 2，用户 2026-10-14：「两条都按建议修订」）1a 交付编号规则与「原编号」检索结构；各单据老编号的导入与新旧对照随该单据的切换阶段完成（1b 报价起）。
- [ ] 同步延迟、未完成的事件发布、作业失败、登录失败率四类告警已接入，并在 dev 上演练触发
- [ ] 回滚演练：撤销 nginx 路由后，老系统登录和审批不受影响
- [ ] 首次 `/design-sync` 完成：正本由 `shared/ui` 同步生成，原有全部组件逐个比对预览无缺失，`_ds_sync.json` 锚点已写入；1a 的差距（合并待办收件箱、登录页、权限不足 / 会话过期页）已补入正本
- [ ] 「在职但无企业微信 ID」清单为 0（确无企业微信的人逐个登记原因）（用户 2026-10-14：「加 (Recommended)」，D-1a-35）

#### Phase 1b — 报价
Command: /code-modernization:modernize-reimagine
Modules: fst:domain/presales, fst:application/presales, fst:infrastructure/presales, fst:controller/admin/quotation, fst:controller/admin/opportunity, fst:service/customer, pc:views/project/quotation, pc:views/project/costTemplate, pc:views/project/opportunity, pc:views/project/price
Scale: M
Risk: 中。
- 风险一：报价是交付预算的基线，交付仍在老系统。缓解：报价批准后，按所有权矩阵把可交付的列写回 `psa_quotation*`。
- 风险二：老页面仍在调用 `fst/opportunity/*` 等接口。缓解：提供兼容门面。
Entry criteria:
- [ ] Phase 1a 的出口条件全部满足，且本 brief 已按试点结果重新生成
- [x] 售前与报价域被标记的 P0 规则已在 `RULE_REVIEWS.json` 中有结论
- [ ] 已列出所有调用 `/admin-api/fst/quotation/**`、`/admin-api/fst/opportunity/**` 的老调用方（PC、H5、外部系统），并为每个调用方注明：经兼容门面测试通过，或本阶段一并迁移
- [ ] `psa_quotation*`、`psa_cost_template*`、`psa_crm_opportunity` 已有逐列所有权矩阵
- [ ] 特征基线已录制：用 dev 库数据回放报价的保存、提交、审批，请求与结果记录在 `analysis/psa/BASELINE.md`
- [ ] 本阶段涉及流程的流程梳理已完成（`process/1b/`，SESSION_PLAYBOOK §13），差异清单逐条有用户决定，并已写入 §5「流程梳理结论」（用户 2026-10-14：「每个阶段实施前增加「流程梳理」步骤……先把这一步写进 SESSION_PLAYBOOK 和 brief 的阶段入口条件」）
Exit criteria:
- [ ] 售前与报价的 P0 规则全部有以规则 ID 命名的测试，且测试通过
- [ ] 同一组输入在新旧系统上的最终报价、毛利、条款拆分、状态完全一致，或差异已在 `RULE_REVIEWS.json` 中被接受
- [ ] 反向同步之后，老系统交付预算读取的报价基线与切换前一致（契约测试）
- [ ] BI 查询清单中涉及报价的查询，在反向同步后的 SQL Server 上结果不变
- [ ] 回滚演练通过

#### Phase 2 — 交付与工时（同一阶段切换）
Command: /code-modernization:modernize-reimagine
Modules: fst:domain/delivery, fst:application/delivery, fst:infrastructure/delivery, fst:controller/admin/pmproject, fst:controller/admin/project, fst:controller/admin/projectplan, fst:controller/admin/projecttask, fst:service/project, fst:service/budget, fst:service/contract, fst:service/manhour, fst:controller/admin/manhour, fst:service/resource, fst:controller/admin/resource, fst:dal/mysql/manhour, fst:dal/dataobject/manhour, pc:views/project/pmList, pc:views/project/lists, pc:views/project/planning, pc:views/project/deliverable, pc:views/project/weeklyMeeting, pc:views/project/budget, pc:views/project/forecast, pc:views/project/taskLists, pc:views/project/myTaskLists, pc:views/project/manhour, pc:views/project/timesheet, h5:pages/work/project, h5:pages/index
Scale: XL
Risk: 很高。
- 风险一：用户量最大的路径（180 天 37.6 万次调用、34,498 个审批实例）。缓解：先做影子计算，新系统用老系统的输入计算并比对结果；在会计期间边界一次性全员切换。
- 风险二：任务包的实际工时由工时审批回写，交付与工时在行级强耦合。缓解：两个 BC 在同一阶段切换，避免出现两个写入方。
Entry criteria:
- [ ] Phase 1b 的出口条件满足
- [ ] 交付、工时、收入确认的 P0 规则中被标记的条目已在 `RULE_REVIEWS.json` 中有结论（例如：撤回后已审批行是否保留、09:00 是开放时间还是截止时间、过账校验用行状态还是表头状态、人天口径）
- [ ] 工时类 XXL-JOB 的耗时基线已从作业日志导出
- [ ] `psa_task_package`、`psa_time_sheet*`、`psa_pm_project`、`psa_budget_*` 已有逐列所有权矩阵
- [ ] `operations.facts` 过账管道和 `accounting.period` 已可用（ADR-0016）：明细事实表拒绝 UPDATE / DELETE 的测试通过；向已关闭的 (期间, 法人) 过账被拒绝的测试通过；工时成本（交易类型 TS）是第一个事实生产者
- [ ] 老过账引擎最近一个已结期间的工时成本过账结果已导出，作为影子比对基线
  Proposed revision（草稿，待用户决定；Phase 1a 会话 2026-10-14）：追加入口条件「工时审批所需的流程字段已有来源：项目经理（`projectManagerId`）、区域负责人（`regionalHeadId`）与工时行类型路由标记（项目 / 售前 / OH）由哪个模块提供已决定（候选：delivery.execution 提供项目经理，organization.workforce 提供区域负责人）」。1a 中 TIMESHEET 只用于回放，不实际发起，取不到人时落流程管理员兜底。
  Proposed revision（草稿，待用户决定；Phase 1a 会话 2026-10-14，依据 R0 登记的 A8_ACCESS_REGISTER #29–#31）：追加入口条件「无效交付工时规则（生产 SQL Agent 作业第 4 步经 BI 视图 T_FST_INVALID_DELIVERY 回写，#31）已抽取为规则卡并评审；项目档案（#29）与项目预算（#30，与 PSA 预算双入口，RULE-182/239）的 A8 → 老 PSA 通道已在规则卡中登记」。
- [ ] 本阶段涉及流程的流程梳理已完成（`process/2/`，SESSION_PLAYBOOK §13），差异清单逐条有用户决定，并已写入 §5「流程梳理结论」（用户 2026-10-14：「每个阶段实施前增加「流程梳理」步骤……先把这一步写进 SESSION_PLAYBOOK 和 brief 的阶段入口条件」）
Exit criteria:
- [ ] 影子计算结果：一个完整会计期间内，新旧系统逐行的审批状态、锁定费率、标准成本金额、计费金额、人天、任务包实际工时完全一致，或差异已被接受
- [ ] 切换发生在会计期间边界；老系统中切换前发起的工时审批实例已全部结束
- [ ] 工时保存和提交的 p95 延迟不高于基线
- [ ] 老成本归集读取反向同步后的 `psa_time_sheet_works_end` 时，结果与切换前一致
- [ ] 回滚演练通过
  Proposed revision（草稿，待用户决定；同上）：追加出口条件「生产 SQL Agent 中对应的 A8 / BI → 老 PSA 作业（A8_ACCESS_REGISTER #29、#30、#31）已停用，并在登记表中改为『已停用 / 已迁入』；切换完成前 BI 视图 T_FST_PROJECT、T_FST_INVALID_DELIVERY 不改为过渡镜像、不废弃」。

#### Phase 3 — 差旅报销（分贝通）
Command: /code-modernization:modernize-reimagine
Modules: fst:domain/expense, fst:application/expense, fst:infrastructure/expense, fst:controller/admin/expense, fst:expense/travel, h5:pages/work/travel, pc:views/accounting/travel
Scale: L
Risk: 中。
- 风险：外部集成状态机复杂（订单、对账、组织同步、可靠投递）。缓解：用分贝通沙箱做契约测试；保留现有防腐层设计。
Entry criteria:
- [ ] Phase 1a 的出口条件满足
- [ ] 已执行 `/code-modernization:modernize-extract-rules psa expense` 补抽差旅规则，并在 `RULE_REVIEWS.json` 中有结论
- [ ] 分贝通沙箱可用，签名密钥存放在 KMS
- [ ] 分贝通回调地址 `/app-api/fst/fenbeitong/events/*` 的切换方式已与分贝通确认：改为新地址，或由兼容门面转发
- [ ] 本阶段涉及流程的流程梳理已完成（`process/3/`，SESSION_PLAYBOOK §13），差异清单逐条有用户决定，并已写入 §5「流程梳理结论」（用户 2026-10-14：「每个阶段实施前增加「流程梳理」步骤……先把这一步写进 SESSION_PLAYBOOK 和 brief 的阶段入口条件」）
Exit criteria:
- [ ] 差旅 P0 规则全部有测试，且测试通过
- [ ] 一个完整月度周期（申请、订票、对账、分摊、月结审批）在新系统跑通，与老系统的对账单逐笔一致
- [ ] 回滚演练通过

#### Phase 4 — 成本、收入与核算
Command: /code-modernization:modernize-reimagine
Modules: fst:domain/cost, fst:application/cost, fst:infrastructure/cost, fst:controller/admin/cost, fst:controller/admin/accounting, fst:service/wip, fst:service/accounting, fst:service/cost, fst:service/costsummaryext, fst:domain/receivables, fst:application/receivables, fst:infrastructure/receivables, pc:views/accounting/costCollection, pc:views/accounting/costCarry, pc:views/accounting/costAllocation, pc:views/accounting/projectProfitL3, pc:views/accounting/revenueRedReversal, pc:views/accounting/voucherManage, pc:views/accounting/accountingPeriod, pc:views/costopenning, pc:views/project/revenue, pc:api/accounting
Scale: XL
Risk: 很高。
- 风险一：直接产生推送 U8C 的凭证，错误不可逆。缓解：至少两个月结期间影子计算；凭证只出草稿、不推送，人工对账一致后再切换推送权。
- 风险二：老结转路径（`/fst/accounting/wip`）与新结转引擎并存。缓解：切换前由业务确认下线老路径。
Entry criteria:
- [ ] Phase 2 和 Phase 3 的出口条件满足
- [ ] 成本域被标记的 P0 规则已有结论（分摊余数归属、负成本进入 WIP 后的余额符号、红冲是部分还是全额、核销按数量配对还是按金额配对）
- [ ] 收入确认按 ACC-R01「产出法 · 客户价值确认」实现，老文档中 EAC / POC 驱动收入的过期表述已更正；财务已确认期间重开政策、质保金条款（ADR-0016）
- [ ] A8 客户产值确认单（方言，= 收入确认单）随 6a 迁入新系统，不设 A8 只读接入的双源期：在会计期间边界停用 A8 模板与 `oa-cuvalue-import` / `oa-cuvalue-sync`，同日起收入确认只在新系统发起；历史 19,869 行与 434 行退回按冲销关系迁入。本条改为「6a 出口条件满足（收入确认单已迁入新系统，A8 不再产生收入事实）；财务已确认预付款条款（PREPAY）无交付即确认收入的口径」。（ADR-0024 已采纳，2026-10-14）
- [ ] 词表中会计口径相关的改名（G-8 至 G-13、G-16、G-17、G-20、G-21、G-27）已于 2026-10-14 由业务 Admin 确认；财务在本阶段开始前复核这些用语在凭证与报表中的口径（ADR-0018）
- [ ] 科目映射 DMN 表已由财务评审，版本号已登记
  Proposed revision:（ADR-0022 评审 C-1，用户 2026-10-14：「收付推送前移到 6a (Recommended)」；草稿，路线整体编排由 psa-coordinator 统一处理）6a 入口增加：`accounting.ledger` 收付口径推送通道就绪——收付科目表与映射表首版、6a 表单对应的口径转换规则（D17）、主数据单向同步、幂等推送、死信与对账，经财务确认；自 6a 切换日起已迁入表单的凭证由新系统推送 U8C 收付账套，A8 制证插件同日停用。
- [ ] 最近两个已结期间的老系统归集、分摊、结转、凭证结果已导出，作为黄金基线
- [ ] 月结批处理的耗时基线已导出
  Proposed revision（草稿，待用户决定；Phase 1a 会话 2026-10-14，依据 R0 登记的 A8_ACCESS_REGISTER #28）：追加入口条件「成本明细 → psa_pm_pay 通道（生产 SQL Agent 作业，#28）承载的成本明细规则已抽取为规则卡并评审，含隐含规则『委外和居间付款申请不结转』」。
- [ ] 本阶段涉及流程的流程梳理已完成（`process/4/`，SESSION_PLAYBOOK §13），差异清单逐条有用户决定，并已写入 §5「流程梳理结论」（用户 2026-10-14：「每个阶段实施前增加「流程梳理」步骤……先把这一步写进 SESSION_PLAYBOOK 和 brief 的阶段入口条件」）
Exit criteria:
- [ ] 两个月结期间，新旧系统的分摊行、WIP 余额、凭证分录逐笔一致，或差异已由财务在 `RULE_REVIEWS.json` 中签字接受
- [ ] 对账等式全部成立：归集守恒、合同资产滚动恒等式（F113）、凭证借贷平衡；老系统的红冲记录已按冲销关系迁移，原行与反向行双向关联
- [ ] 月结批处理在生产规模数据上的耗时不高于基线
- [ ] U8C 推送权已切换到新系统，老成本页面和 `/fst/accounting/wip` 已下线
  Proposed revision:（OA 流程盘点，用户 2026-10-14：ADR-0021「采纳；6a–6c 并入 Phase 4 前」）增加前置：6a 合同与收入、6b 开票与收款、6c 费用、付款与预提的出口条件均已满足，且 A8 凭证插件（`LEXMISCIF_VOUCHER`）与销项开票插件在切换日后无新记录；本阶段权责事实按 ADR-0021 第 4 条完成无遗留检查。阶段草案见 OA_PROCESS_INVENTORY.md §6.3。
- [ ] 回滚演练通过（推送权切回老系统）
  Proposed revision（草稿，待用户决定；同上）：追加出口条件「#28 对应的生产 SQL Agent 作业已停用，并在 A8_ACCESS_REGISTER 中改为『已停用 / 已迁入』；切换完成前 BI 视图 T_FST_PAY 不改为过渡镜像、不废弃」。

#### Phase 5 — 身份全面切换、BI 与老系统下线
Command: /code-modernization:modernize-reimagine
Modules: system:service/oauth2, system:controller/admin/permission, gateway:core, infra:controller/admin/file, pc:views/system/user, pc:views/system/role, pc:views/system/menu, h5:pages/qyLogin, h5:pages/mine
Scale: M
Risk: 中。
- 风险：BI、KM、A8 等外部系统依赖老库和 SSO 端点。缓解：提供与老表同形状的 `reporting` 视图；外部 SSO 调用方迁移到签名票据。
Entry criteria:
- [ ] Phase 4 的出口条件满足
- [ ] 外部调用方清单（BI、KM、A8、IT 服务门户）中，每一方都已有确认的切换步骤
- [ ] 本阶段涉及流程的流程梳理已完成（`process/5/`，SESSION_PLAYBOOK §13），差异清单逐条有用户决定，并已写入 §5「流程梳理结论」（用户 2026-10-14：「每个阶段实施前增加「流程梳理」步骤……先把这一步写进 SESSION_PLAYBOOK 和 brief 的阶段入口条件」）
Exit criteria:
- [ ] BI 查询清单中的每条查询在 PostgreSQL `reporting` 上的结果，与切换前在 SQL Server 上的结果一致
- [ ] 安全组已移除 48080 和 48088 规则，老服务进程已停止
- [ ] SQL Server 库已转为只读归档；Flowable 历史和 API 日志已按保留期归档

## 4. Business Walkthroughs

| 人物 | 业务过程 | 老系统实现模块 | 由哪个阶段替换 |
|---|---|---|---|
| 项目成员 / 部门经理 | 每天在手机或电脑上把当天工时分配到工作包，提交后由项目经理或直属领导逐行审批，通过后锁定费率、计入项目人工成本 | h5:pages/work/project、pc:views/project/manhour → fst:service/manhour → bpm:service/task → fst:mq/consumer → fst:trigger/task → fst:application/cost | 填报和审批：Phase 2；审批引擎：Phase 1a；计入成本：Phase 4 |
| 售前经理 / 项目经理 | 售前为商机编制成本估算树和报价并送审（低于 50% 毛利需说明理由）；赢单后 PM 以报价为基线编制 WBS 预算，毛利低于报价时需 CEO 终审 | pc:views/project/quotation → fst:application/presales → fst:domain/presales → bpm:service/task；fst:controller/admin/pmproject → fst:domain/delivery | 报价：Phase 1b；预算：Phase 2 |
| 出差员工 / 财务 | 员工在手机上提交出差申请，审批后在分贝通订票；月底财务把账单和发票分摊到项目或商机，并审批月度对账单 | h5:pages/work/travel → fst:application/expense → fst:infrastructure/expense（分贝通）→ pc:views/accounting/travel | Phase 3 |
| 成本会计 | 月末归集直接和间接成本、按人工成本占比分摊 OH、结转 WIP、核销并生成凭证推送用友 U8C | pc:views/accounting/costCollection、costCarry → fst:application/cost → fst:domain/cost → fst:infrastructure/cost → U8C | Phase 4 |

## 5. Behavior Contract

行为契约取自 `BUSINESS_RULES.md` 与 `rules/IDENTITY_ORG_RULES.md` 的 **226 条 P0 规则**（全部 376 条）。它们构成回归测试集：每条在其所属阶段上线前，都必须证明等价，或以 `RULE_REVIEWS.json` 中的业务决定为准证明「按决定修正」。

- **已定行为（103 条）**：置信度 High 且无疑似缺陷（身份与组织域以抽取时的 Review=No 为准），按老系统行为复刻。
- **阻塞项（123 条，已评审 73 条）**：置信度低于 High，或抽取时发现疑似缺陷（老行为看起来有误，或与文档不一致）。按用户要求「顺手修已知 bug、行为现代化」，这些规则**在所属阶段开工前必须经 `/code-modernization:modernize-review psa` 由业务逐条决定**：保持原行为、按修正行为实现，或列入讨论。未决定的规则阻塞该阶段的入口条件。已评审的条目按 `RULE_REVIEWS.json` 呈现：**不复刻** = 新系统按「新系统做法」实现，老行为不作为基准；「修正：」= 规则保留、按修正实现；「已评审」= 按老行为复刻。

| 域 | 阶段 | 已定（P0） | 阻塞（P0，需评审） | 其中已评审 |
|---|---|---|---|---|
| 工时与资源 | Phase 2 | 21 | 9 | 0 |
| 项目成本与核算 | Phase 4 | 29 | 23 | 0 |
| 售前与报价 | Phase 1b | 8 | 14 | 14 |
| 交付、预算与收入 | Phase 2（收入确认部分在 Phase 4） | 19 | 18 | 0 |
| 审批（platform.approval） | Phase 1a | 20 | 28 | 28 |
| 身份与组织（platform.identity、organization.workforce） | Phase 1a | 6 | 31 | 31 |

### 工时与资源（Phase 2）

**已定：** RULE-001 Task package actual hours = total approved hours (recalculated in full)、RULE-003 Approval writes the cost and bill amounts (approved hours × locked rate)、RULE-005 Rates, company code and cost centre are fixed at submission、RULE-007 Rate lookup by employee grade, salary band and city on the work date、RULE-020 Approval status transition rules; approvers act only on their own lines、RULE-021 Conditions for a timesheet line to be postable to cost、RULE-024 On a workday, booked hours plus leave must be at least 7.5h、RULE-025 One timesheet per employee per calendar day、RULE-026 The remaining-hours (ETC) gate on project-task packages、RULE-028 Fields required to send, by work-package type (drafts exempt)、RULE-029 Every line must have an approver before it can be sent、RULE-031 Every line must match exactly one standard-cost rate and one billing rate、RULE-032 Which project-task packages are bookable、RULE-044 End-of-process result decides the header outcome、RULE-045 Pending becomes In-approval only after BPM has actually started or resumed the process、RULE-046 An approver's personal decision only touches that approver's own rows、RULE-047 How row statuses follow a header change、RULE-049 Who may re-initiate (edit and resubmit) a timesheet、RULE-050 "Save" keeps a draft; "Send" queues the approval process、RULE-051 Row approver is assigned at submit by work-category type、RULE-058 Cost and bill rates are locked at submit, not at approval

**阻塞，需评审：**
- [ ] RULE-002 Which daily wage is used (before 2020 vs from 2020 on)（`HourlyRateServiceImpl.java:329-348`，置信度 Medium） — `Integer.parseInt(postCode.substring(1))` throws if the post code has a non-numeric suffix.
- [ ] RULE-004 Human-days split of a person's approved day across projects（`ManHourEndServiceImpl.java:57-109`，置信度 Medium） — (1) If a null `approval_hours` row follows a non-null row on the same day, `hoursValue.add(null)` throws an NPE. That aborts the run for every remaining user, because the job catches the exception and only logs it. (2) T
- [ ] RULE-006 Project labour cost = daily wage × human days, excluding admin/support posts（`ManHourServiceImpl.java:2090-2128`，置信度 Medium） — `contains("A")`/`contains("S")` matches the letter anywhere in the post code, not just as a prefix. Rest-day human days for A/S posts are still added to non-chargeable days, because the rest-day check runs first. RULE-24
- [ ] RULE-022 Who can see whose timesheets（`ManHourServiceImpl.java:216-222`，置信度 High） — Several read endpoints have no ownership or permission check:
- [ ] RULE-023 Only the owner can edit or resubmit, and only when rejected, cancelled or returned（`ManHourServiceImpl.java:800-818`，置信度 High） — Doc TS-R-003/008 says approved sheets can be reopened by configuration. No reopen path exists in this code.
- [ ] RULE-027 The three org fields are mandatory on every line（`ManHourServiceImpl.java:1327-1351`，置信度 High） — The values come from the client and are not compared with the server's work-package list. The intended source rules are: project or project task → the project's delivery dept; opportunity or opportunity task → the opport
- [ ] RULE-030 Timesheets can only be booked to a work package the employee is allowed to use right now（`ManHourServiceImpl.java:1434-1469`，置信度 High） — Only type and id are compared. The three org fields (costOrgId/projectOrgId/profitCenterId), termNumber, stageId and managerId sent by the client are not checked against the server's list, so a crafted payload can book a
- [ ] RULE-043 Row decisions from BPM task-completion messages（`TimesheetApprovalResultHandler.java:54-139`，置信度 High） — The legacy fallback path (no rowIds, lines 122-135) rejects every id in the opinion JSON, even ids with a blank opinion and ids belonging to other approvers. That contradicts the "blank means agree" rule of the node path
- [ ] RULE-048 Timesheet header approval-status state machine (allowed transitions)（`ManHourApproveStatusService.java:163-183`，置信度 High） — The state-machine doc (05-工时域-状态机.md §5.3, §3.8) lists a `通过 → 取消` edge and an "admin revoke to DRAFT". The global rule list COST-R09 says approved sheets can be reopened back to 审批中. In code, Approved has no outgoing ed

### 项目成本与核算（Phase 4）

**已定：** RULE-067 Three-tier project profit and rates、RULE-068 OH tier routing and carry-forward when the labour base is zero、RULE-071 WIP stage on entry and stage advance (POC exactly 100 and contract returned)、RULE-073 Year-end bonus is split to project terms by the person's annual hours (cent-exact largest remainder)、RULE-074 Cost routing chain and how expense nature is set、RULE-075 Timesheet three-element (salary/social/fund) cent rounding anchored to the rounded total、RULE-076 Labour reclass – U8C credit vs legacy-system landed cost, by department, with tolerance、RULE-077 Labour reclass voucher – reverse U8C credit in full, debit legacy-system buckets, absorb small differences、RULE-078 Vouchers are aggregated per company × voucher word × department、RULE-081 Write-off voucher amounts and credit account derivation、RULE-091 Period open/close preconditions (sequential opening, month-end done, reconciled)、RULE-092 Full OH allocation: one effective full run per period, never concurrent、RULE-093 Carry draft gate order, and the red-reversal exception、RULE-094 Carry confirm re-checks the gates and dry-runs the vouchers、RULE-098 Labor reclass confirm and deliver must be fresh and follow period order、RULE-100 Landing conservation must balance exactly (zero tolerance)、RULE-101 Collection confirmation gate、RULE-103 Project fees without a contract term never go to the OH pool、RULE-105 Labor reclass differences over ¥1.00 block the voucher、RULE-106 Re-running collection reverses a prior confirmation; it is blocked once allocation is confirmed、RULE-107 Collection cannot start until payroll is complete for everyone who filed timesheets、RULE-116 Full allocation takes effect atomically with pool write-back and step advance、RULE-117 A batch becomes POSTED only when U8C has confirmed every voucher; then the step advances、RULE-118 Creating a carry draft (asynchronous)、RULE-119 Posting phases and the completion condition、RULE-123 WIP stage assignment and advancement、RULE-125 Carry steps are strictly serial: occupy on draft, advance on POSTED, release on cancel、RULE-126 WIP write-off flips the entry from in-WIP to relieved and books a P&L line、RULE-134 A cost dated in a closed period lands in the earliest later open period

**阻塞，需评审：**
- [ ] RULE-066 OH basis is timesheet (source_type 0) not-tax cost; period 13 uses the full-year base（`AllocationOrchestrator.java:108-146`，置信度 High） — Docs COST-R39/R40 still say "wpt0/1 work-package hours". Code uses source_type = 0 because work_package_type is never populated (#875).
- [ ] RULE-069 OH allocation – share each overhead pot by direct-labour cost share, with rounding remainder on the largest line（`OhAllocationCalculator.java:196-352`，置信度 High） — Docs (09 rules list COST-R45) call this the "largest remainder method". The code does not do that: it puts the whole remainder on the largest-amount line.
- [ ] RULE-070 Reversing an OH accrual mirrors the original allocation in proportion（`ReversalMirrorCalculator.java:31-90`，置信度 High） — Docs COST-R44 say "分摊无独立红冲入口" (allocation has no separate reversal). The code does have this reversal-mirror path.
- [ ] RULE-072 WIP write-off selection – one revenue confirmation releases one whole WIP entry; leftovers go cost-only or wait（`CarrySelectorImpl.java:126-237`，置信度 Medium） — Docs COST-R60 (write-off cutoff by business_date ≤ latest confirmation date) are not implemented. Pairing is by count, so one confirmation of any size releases a whole entry and pulls every other entry on the term into c
- [ ] RULE-079 Posting rule matching and voucher-line rendering（`PostingRuleEngine.java:185-286`，置信度 High） — Docs COST-R54 describe 3-level matching on (carry_type, source_type, company). The code matches on carry_type → trigger condition → company. Docs also say "同一 carry_type 应唯一" (one rule per carry_type, design doc line 205
- [ ] RULE-080 Revenue-return red reversal – reverse the whole original cost, re-render the voucher and negate it（`PostingVoucherDrafterImpl.java:125-209`，置信度 Medium） — - The return's own amount is never used: a partial return reverses the entire cost.
- [ ] RULE-082 WIP period balance and report increase/decrease use the absolute movement amount（`WipPeriodSnapshotMapper.java:53-113`，置信度 Medium） — ABS() turns negative WIP entries into positive balances. WipReportMapper.java:52-61 (increase/decrease) does the same, so WIP balance and the report overstate WIP by twice any negative cost entered.
- [ ] RULE-095 Red reversal confirm requires a valid reason and a successful red-voucher preview（`RevenueRedReversalCommandUseCase.java:42-55`，置信度 Medium） — The seed spec requires the "财务主管" (finance supervisor) role. The controller only checks the generic permission `cost:carry-batch:confirm` (RevenueRedReversalController:149), which is the same one used for normal carry co
- [ ] RULE-096 Collection start preconditions and order of side effects（`ExecuteCollectionUseCase.java:82-128`，置信度 Medium）
- [ ] RULE-097 Exempting a collection error needs a reason and only works on OPEN errors（`ExemptEntryErrorUseCase.java:28-40`，置信度 Medium） — There is no check on issue type, so a technical SYSTEM_PROCESS_FAILED error can be exempted too, and that unblocks the confirmation gate. The spec (`cost-exception-workbench` "技术异常必阻断确认、业务异常…可豁免") says only business erro
- [ ] RULE-099 Unreconciled expense lines block carry and period close（`ReconciledClosingGate.java:20-32`，置信度 High） — Period close reuses the carry-specific error code COST_CARRY_HAS_UNRECONCILED, so the close screen will show a carry message. A non-zero threshold would let close/carry proceed with known unreconciled rows; the spec has 
- [ ] RULE-102 Ingest pre-routing guards: duplicates, exempt flag, department bonus（`CostRoutingService.java:101-125`，置信度 High） — Finance can set flag_exempt in bulk through the cost-correction Excel import ("是 / 否 / 豁免", CostCorrectionImportService.java:425-441). That bypasses every validation and exception-workbench control with no reason and no 
- [ ] RULE-104 Mandatory dimensions depend on where the cost will land（`CostValidationService.java:76-146`，置信度 High） — The read-only posting pre-check (RULE-20) checks the OH mapping only for cost_org_id, not for the cost centre. A row can pass the pre-check and still fail ingest with DEPT_NOT_MAPPED on its cost centre.
- [ ] RULE-108 Month-end cost processing only runs while the period is Pre-closed or Reopened（`AccountingPeriodServiceImpl.java:43-48`，置信度 High） — The 重开 branch looks unreachable through the normal UI. `AccountingPeriodManagementService.transition` rejects the REOPEN action outright, and 已关账 has no available actions (`AccountingPeriodStatusEnum` CLOSED_BOOKS = empt
- [ ] RULE-120 Allocation batch lifecycle（`CostAllocationBatch.java:69-134`，置信度 High） — `DeleteTrialAllocationUseCase:23-30` deletes a trial batch without checking its status, so a QUEUED or RUNNING trial can be deleted while the worker is still writing it.
- [ ] RULE-121 Collection batch status and phase lifecycle（`CostCollectionBatch.java:122-191`，置信度 High） — `fail()` has no guard. Its comment says "non-terminal only", but it would overwrite a COMPLETED batch if called.
- [ ] RULE-122 Carry batch status and phase state machine（`CarryBatch.java:98-283`，置信度 High） — REVERSED (4) is never assigned by any code; only OpeningEventCarryBridge:275 reads it. Batch-level reversal is not implemented, and red reversal works line by line through carry_type=3.
- [ ] RULE-124 Period month-end step gate: collection lock, confirm, and re-collection rollback（`PeriodProgress.java:71-117`，置信度 High） — Nothing reverses an effective full allocation, so a collection error found after step 2 cannot be fixed through re-collection.
- [ ] RULE-127 Red reversal returns a relieved WIP entry to in-WIP and books one negative P&L line per positive line（`WipLedgerPosterImpl.java:435-502`，置信度 High） — Seed doc 09 rule PLT-R02 says "红冲无人工入口" (no manual entry for red reversal). In code, red reversal is a manual draft-and-confirm flow (RULE-21).
- [ ] RULE-128 Legacy carry-forward lifecycle (待结转 → 结转中 → 已结转), still exposed by controllers（`CostCarryForwardServiceImpl.java:109-157`，置信度 Medium） — 1. In `F/service/wip/CarryForwardServiceImpl.java:219-222` the U8C call is commented out. updateResult is hard-coded SUCCESS, and the 2nd company processed always gets the fake error "U8C错误信息:XXXXXXXXXX", so no real vouc
- [ ] RULE-132 U8C voucher delivery and the opening-year local bypass（`U8cVoucherPostHandler.java:88-140`，置信度 High） — The voucher status only ever moves DRAFT(0) → CONFIRMED(2). SENT(1) and FAILED(3) are never written, even though VoucherWriterImpl:69 says "回写 SENT/CONFIRMED", so delivery state only exists on the delivery task.
- [ ] RULE-133 Opening-period collection confirm can bulk-exempt all open errors（`OpeningMonthExecuteUseCase.java:115-129`，置信度 Medium） — This bypasses the normal-flow rule that technical errors "must be resolved by IT" (CostCollectionBatch:381-383). The exemptions are permanent.
- [ ] RULE-135 Month-end cost processing only runs while the period is pre-closed or reopened（`AccountingPeriodServiceImpl.java:43-56`，置信度 High） — The status checks use `FSTConstant` integer constants, not the `AccountingPeriodStatusEnum` single source of truth (CLAUDE.md forbids new FSTConstant status usage). Seed spec cost-revenue-red-reversal says red reversal n

### 售前与报价（Phase 1b）

**已定：** RULE-143 BU estimated cost = labour cost (whole tree) + the 8 fixed other-cost categories、RULE-146 Line amount: a hand-entered discounted total overrides quantity × unit price、RULE-156 Contract clauses are locked during approval、RULE-157 Server master data is the only source of position rates; strict check at submit、RULE-160 Approval-time adoption recalculates final amount and margin; quotation must stay PENDING（修订：审批中只能采纳成本（成本树、其他成本），采纳后用与保存相同的计算重算 BU 成本与毛利；不再重算最终报价，尾差、税率不可采纳（随 145、155 修订）。）、RULE-161 Server-side checks on monetary inputs、RULE-166 Approve/reject decisions are idempotent and ignore stale messages、RULE-167 Submission order: refresh costs, check all gates, compare-and-set, then start OPP_QUOTATION as one transaction

**阻塞，需评审：**
- [x] RULE-139 Server rates always overwrite client rates; at submission every work package must have a fully priced role（`QuotationCostRateResolver.java:38-103`，置信度 High） — 修正：服务端主数据费率为准；删掉永不触发的查重，改为确定性选方案（见 147）；不做标价折扣（问题1「不需要」），折扣用 RULE-146 行折后总价与尾差表达；工时仅成本编辑权限（BU）可改。（已评审 2026-10-14）
- [x] RULE-140 At submit, cost and margin are recalculated from the current rates before approval starts（`SubmitQuotationUseCase.java:51-81`，置信度 High） — 修正：存在未定价工作包时毛利为空并标「成本未完整」，不输出虚高毛利；成本必填节点由 BU 补全。（已评审 2026-10-14）
- [x] RULE-141 Cost-tree work package value = hours × billing rate; labour cost = hours × cost rate（`CostEstimateNode.java:74-102`，置信度 Medium） — 修正：工作包值、人工成本四舍五入到分；汇总由服务端计算；汇总标价仅参考（问题2「仅参考+审批提示」），审批单显示汇总标价、最终报价与偏差率，不约束最终报价。（已评审 2026-10-14）
- [x] RULE-142 Final quote equals the sum of line amounts plus the tail adjustment（`Quotation.java:237-246`，置信度 High） — 修正：公式保留，合计取整到分；审批单显示尾差金额及其占行合计比例，不设硬上限。（已评审 2026-10-14）
- [x] **不复刻** · 老行为：RULE-144 Gross margin is calculated on the ex-tax final quote using the header tax rate, to 4 decimals（`Quotation.java:252-259`，置信度 High） → 新系统做法：不含税额 = Σ行不含税金额 + 尾差 ÷（1+表头税率），毛利按此计算；不用固定 1.06。Phase 2 的 RULE-182/239 预算毛利须用同一口径。（不复刻；评审 2026-10-14）
- [x] **不复刻** · 老行为：RULE-145 Approval-time edits recalculate the final quote from the stored line total plus the new tail adjustment（`Quotation.java:367-410`，置信度 High） → 新系统做法：审批时重算与保存使用同一计算函数——重新加载行、按税率重拆税额；人工成本为空的老数据从成本树重算。审批中不再重算最终报价（见 155、160）。（不复刻；评审 2026-10-14）
- [x] RULE-147 Role rates come from the master-data schemes in effect today, by role and city, newest scheme first（`PositionRateQueryImpl.java:50-92`，置信度 High） — 修正：费率在提交时锁定（问题3「提交时锁定」）——草稿按当天生效费率；提交时写入费率快照，审批采纳使用该快照；驳回后重新提交按当天费率刷新。同一生效开始日的方案以唯一约束禁止。（已评审 2026-10-14）
- [x] RULE-155 During approval clauses are locked, so the tail adjustment is effectively frozen, and BU cost must be > 0 when required（`ApplyQuotationApprovalUseCase.java:65-101`，置信度 High） — 修正：审批人不能调整最终报价（问题4「不能，需退回」）——去掉尾差、税率的采纳入口；需改价时退回发起人，由销售重拆条款后再提交。条款锁定保留；成本必填节点 BU 成本须 > 0 保留。（已评审 2026-10-14）
- [x] RULE-158 Conditions for submitting a quotation for approval（`Quotation.java:265-279`，置信度 High）（已评审 2026-10-14：原样保留；流程实例在启动时绑定（与 RULE-167 一致），过期注释不带入。）
- [x] RULE-159 Clause amounts must each be greater than 0 and add up exactly to the final quote（`Quotation.java:334-356`，置信度 High） — 修正：前后端同一等式校验（少分配同样提示）；「不要求条款」的豁免仅限带老数据标记的迁移在途单。（已评审 2026-10-14）
- [x] RULE-168 Only DRAFT or REJECTED quotations can be edited, submitted or deleted（`Quotation.java:532-540`，置信度 High） — 修正：保存也做比较并更新（状态 + 版本号），与提交、删除、审批采纳一致，杜绝并发覆盖已提交单据。（已评审 2026-10-14）
- [x] **不复刻** · 老行为：RULE-169 How BPM final results map to quotation outcomes（`QuotationProcessResultHandler.java:34-52`，置信度 Medium） → 新系统做法：与 RULE-279 一致——发起人撤销/取消后单据回到草稿，不记驳回原因；仅「不通过」记为已驳回；结果用枚举。（不复刻；评审 2026-10-14）
- [x] RULE-172 Only the applicant can save, submit or delete a quotation（`QuotationAccessGuard.java:66-71`，置信度 High） — 修正：仅申请人可保存/提交/删除，超管不豁免；删除需独立删除权限，且须为申请人、单据为草稿或已驳回。（已评审 2026-10-14）
- [x] RULE-173 Users without cost-edit permission cannot change cost inputs; work-package cost fields are kept from the database（`SaveQuotationUseCase.java:51-164`，置信度 Medium） — 修正：销售新建的条款（含模板）只带结构，角色、工时、费率一律清空，由 BU 填写。（已评审 2026-10-14）

### 交付、预算与收入（Phase 2（收入确认部分在 Phase 4））

**已定：** RULE-184 A clause's revenue is capped at the clause amount; pending requests reserve quota、RULE-200 A revision cannot be submitted if the archive clauses changed after the revision started、RULE-201 Execution-side structure is frozen during budget approval, and planned hours are frozen after lock、RULE-203 Only one active submission per deliverable、RULE-204 At final approval the quota is re-checked; an over-cap request is auto-rejected、RULE-206 Once hours are logged, the assignee is fixed and the task cannot be deleted、RULE-207 Submitting a deliverable requires all its tasks to be finished, then locks them、RULE-208 Rules for registering customer acceptance、RULE-211 Normal cost items are edited during budgeting; sensitive items need a permission、RULE-219 On budget approval, role rates are frozen and cost snapshots are written、RULE-220 ETC approval outcome — approved promotes the pending value; rejected or cancelled unfreezes and keeps the old value、RULE-221 A budget revision can only start from a locked budget; it snapshots, unlocks and re-baselines the clause layer、RULE-228 Budget editing is blocked while the budget is locked or awaiting approval、RULE-229 On revision, clauses are re-matched to the archive; vanished clauses with history become obsolete, others are removed、RULE-239 Budget margin vs quotation margin decides whether CEO approval is needed、RULE-240 The quota is checked again at final approval, and an over-quota approval is turned into a rejection、RULE-242 Raising ETC needs PM_ETC approval only if it pushes project forecast labour cost above budget labour cost、RULE-243 Archive reconciliation keeps clauses that have data and removes those that do not、RULE-244 Budget approval routing — the CEO step is skipped only if budget gross margin ≥ quote gross margin

**阻塞，需评审：**
- [ ] RULE-181 WBS budget cost is task hours × role cost rate, summed up the tree, plus non-labour cost categories（`ProjectBudget.java:733-767`，置信度 High） — The task-level product (`TaskPackage.budgetCost`) is never rounded to cents, but the progress page rounds each task to 2 dp (`ExecutionCostBook.money`). So the budget page total and the sum of progress-tree rows can diff
- [ ] RULE-182 Budget gross margin uses ex-tax contract amount at the source quotation's tax rate (default 6%)（`ProjectBudget.java:769-792`，置信度 High） — The legacy performance margins (RULE-20) hard-code 1.06 no matter what the quotation's tax rate is, so the two screens can disagree for 13%-tax contracts.
- [ ] RULE-183 Task ETC, EAC and POC（`TaskPackage.java:264-311`，置信度 High） — Rule DLV-R08 (09-业务规则清单) says "ETC empty ⇒ EAC = AC (treated as complete)". The code instead defaults an empty ETC to remaining budget hours.
- [ ] RULE-185 Revenue confirmation needs a positive amount and complete evidence; the period is the sign-date month（`RevenueConfirmation.java:64-125`，置信度 High） — The docs disagree with the code on three points:
- [ ] RULE-202 Only the project's PM can change execution-side structure（`OperateProgressTaskUseCase.java:504-510`，置信度 High） — `delete()` (lines 440-453) does not call `requireProjectManager`. Any holder of `fst:pm-project:progress` can delete another project's task packages, as long as no hours are logged. Also, `/task/update`, `/task/create` a
- [ ] RULE-205 Revenue confirmation submit gates — clause exists, clause not obsolete, contract signed back（`SubmitRevenueConfirmationUseCase.java:44-90`，置信度 High） — Seed doc DLV-R18 lists three preconditions: work package POC 100%, internal review passed, and customer confirmation. The code dropped the deliverable gate (#1524), and `allDeliverablesAccepted` / `allDeliverablesInterna
- [ ] RULE-209 Every task package must be complete before a budget can be submitted（`ProjectBudget.java:326-377`，置信度 High） — The class comment says the role "must be in the static rate-table domain", but `validateTask` (lines 802-808) only checks that hours are not negative. The role value domain is never checked on the server. The `TASK_ROLE_
- [ ] RULE-210 Budget can only be edited while it is unlocked and not awaiting approval（`ProjectBudget.java:438-446`，置信度 High） — The budget write endpoints (`PmProjectBudgetController` `/import`, `/task/*`, `/lock`, `/deliverable*`, `/cost-items`) check only the permission `fst:pm-project:budget` and turn off `@DataPermission`. No use case checks 
- [ ] RULE-212 Rules for an approver changing the budget tree during approval（`ProjectBudget.java:639-723`，置信度 High） — This path does not run the RULE-02 rate-missing check. An approver can add a task package with hours but no matching rate, and it is locked into the baseline at cost 0.
- [ ] RULE-213 Total confirmed revenue cannot exceed the clause amount（`RevenueConfirmation.java:64-87`，置信度 High） — Unlike deliverable acceptance (RULE-21), the sign date is not checked against today. A future date is accepted and gives a future revenue period.
- [ ] RULE-222 Approved revenue writes an append-only revenue fact, after a second quota check（`DecideRevenueConfirmationUseCase.java:36-68`，置信度 High） — When the auto-reject happens, BPM shows the instance as approved while the legacy system shows it rejected. No notification or BPM correction is visible in the code.
- [ ] RULE-223 Task package execution states — DOING/REWORK → DONE, DONE → DOING (reopen), LOCKED → REWORK（`DeliverableProgress.java:47-84`，置信度 High） — `FSTConstant.TaskStatus` (0 进行中 / in progress, 1 已保存 / saved, 2 已完成 / completed, 3 已逾期 / overdue, with the comment "TODO 其他两个状态需要完善" / "the other two states need completing") is a separate legacy integer vocabulary. It i
- [ ] RULE-224 Submitting a deliverable requires all its task packages to be done, and locks them（`DeliverableProgress.java:403-423`，置信度 High） — If the submission is rejected (DRAFT), the packages stay LOCKED. Nothing unlocks them. Resubmission still works because LOCKED is accepted.
- [ ] RULE-225 Deliverable approval outcome — approved → awaiting customer acceptance; rejected or cancelled → DRAFT（`DeliverableSubmission.java:588-613`，置信度 Medium） — The `StageReviewProcessResultHandler.java:16-17` comment says that when a meeting fails, the director-node handler already set the submission to DRAFT, so a later "approved" end is a no-op. But `recordDirectorReview` (li
- [ ] RULE-226 Deliverable submission states, and which states hold the deliverable's single "submission slot"（`DeliverableSubmissionStatus.java:43-64`，置信度 High） — `DLV_CONVERTED` has no writer anywhere in the codebase, although it is listed as a state.
- [ ] RULE-227 A budget can only be locked through an approved PM_BUDGET approval, and only when it is complete（`ProjectBudget.java:326-377`，置信度 High） — `requireAllRolesSelected` does NOT skip obsolete packages, unlike `requireAllTasksComplete`. An obsolete package with no role is read-only, so it would block every future submit. Seed doc OPS-R01 says "预算保存即锁定" (saving t
- [ ] RULE-241 An ETC increase needs PM_ETC approval only if it pushes forecast labour cost over budget labour cost（`DeliverableProgress.java:110-136`，置信度 High） — Seed DLV-R08 says "ETC 为空时 EAC = AC → 视同完工" (empty ETC means EAC = actual, treated as complete). The code treats empty ETC as max(0, planned − actual) (`TaskPackage.java:276-286`).
- [ ] RULE-245 Which role cost rate applies: project city, then quotation city, then unfiltered; latest active scheme wins（`ProjectBudgetRepositoryImpl.java:150-222`，置信度 Medium） — `PositionRateQueryImpl.build` turns a missing cost rate into ¥0 (not null) when the role has only a bill rate. Such tasks pass the "rate missing" gate (RULE-05) and silently cost ¥0.

### 审批（platform.approval）（Phase 1a）

**已定：** RULE-251 Row reachability: a row reaches a node if its routing value activates a path to it、RULE-262 Each node must have exactly one assignment rule、RULE-263 A row-approval node requires row units, and the row set is narrowed by routing、RULE-264 Idempotent process start by idempotency key、RULE-268 Only the initiator may cancel, before any cutoff node, with a reason、RULE-271 A reject inside a subprocess returns to the initiator; a top-level reject terminates the process、RULE-272 Validation for the unified "decide" action、RULE-273 A reject from an added countersigner is advisory only、RULE-274 Row-by-row approval must cover every pending row, and any rejected row rejects the node、RULE-275 Return to initiator requires a reason and passes the node's BEFORE hook、RULE-277 BEFORE-hook commit gate、RULE-287 Resubmission chooses between continuing the same instance, blocking, and starting a new instance、RULE-290 A top-level rejection ends the whole process; a rejection inside a sub-process sends it back to the initiator、RULE-294 Task result lifecycle (bpm_task_ext)、RULE-301 Auto-complete cascade order after each task creation、RULE-305 Task-result messages (topic-bpm-approval-task-result)、RULE-306 Which process endings reach business modules、RULE-309 Withdraw/cancel cutoff = any marked node with a completed task、RULE-310 Document detail is visible to holders of the function permission or to process participants (fail-closed)、RULE-312 The process-end consumer runs the hook and then the handler, and must be idempotent

**阻塞，需评审：**
- [x] RULE-248 Countersign/or-sign nodes create one task per distinct approver（`BpmParallelMultiInstanceBehavior.java:45-93`，置信度 Medium）（已评审 2026-10-14）
- [x] **不复刻** · 老行为：RULE-249 A single-approver node picks one candidate at random（`BpmUserTaskActivityBehavior.java:73-100`，置信度 High） → 新系统做法：改成候选组认领（不复刻；评审 2026-10-14）
- [x] RULE-250 Initiator-leader scripts pick the 1st or 2nd reporting-line leader（`BpmTaskAssignLeaderAbstractScript.java:39-73`，置信度 High） — 修正：汇报线不完整时往上推一级；一直找不到就转给流程管理员并告警。不能空指针，也不能静默跳过（已评审 2026-10-14）
- [x] RULE-252 Per-row approver resolution（`BpmTaskAssignRuleServiceImpl.java:343-426`，置信度 High） — 修正：行级类型照旧。非行级类型不再取 HashSet里的「第一个」，和节点级用同一种解析方式（候选组）（已评审 2026-10-14）
- [x] RULE-253 Approvers are resolved from the node's assignment-rule type（`BpmTaskAssignRuleServiceImpl.java:476-616`，置信度 High） — 修正：排除停用/离职人员；部门无负责人时取上级部门负责人；候选集永不含空值。（已评审 2026-10-14）
- [x] **不复刻** · 老行为：RULE-254 "Custom data" approvers come from form fields, with a hard-coded fallback person（`BpmTaskAssignRuleServiceImpl.java:665-689`，置信度 High） → 新系统做法：去掉写死的个人兜底，取不到人转流程管理员并告警；删 System.out。（不复刻；评审 2026-10-14）
- [x] **不复刻** · 老行为：RULE-265 The initiator of a customized OA process is the caller-supplied `creator`, otherwise the logged-in user（`CustomizedBpmOAServiceImpl.java:105-110`，置信度 High） → 新系统做法：发起人只能是当前登录用户（ADR-0009、G33）；老系统已由 SEC-005 补丁修复。（不复刻；评审 2026-10-14）
- [x] RULE-266 Duplicate-start guard: one live process per business document, reused on resubmission（`CustomizedBpmOAServiceImpl.java:135-198`，置信度 High） — 修正：唯一键改为（流程类型，单据 ID），数据库唯一约束保证。（已评审 2026-10-14）
- [x] RULE-267 A resubmission after a return must belong to the current return round（`CustomizedBpmOAServiceImpl.java:229-283`，置信度 High）（已评审 2026-10-14：所有续跑入口都执行该检查。）
- [x] RULE-269 Withdraw to initiator: initiator only, before cutoff, single-instance current node, not already at initiator（`BpmProcessInstanceServiceImpl.java:360-420`，置信度 High） — 修正：「能否撤回」的显示与实际执行用同一判断。（已评审 2026-10-14）
- [x] **不复刻** · 老行为：RULE-270 Batch approve acts on every user's pending tasks in each assignee's name（`BpmTaskServiceImpl.java:430-466`，置信度 High） → 新系统做法：只能批量处理本人待办；管理员代办须显式「代理」，审计记录操作人与被代理人。（不复刻；评审 2026-10-14）
- [x] RULE-276 Only the assigned approver may act on a task（`BpmTaskServiceImpl.java:1303-1312`，置信度 High）（已评审 2026-10-14：与 249 配套，先认领再处理，各入口口径统一。）
- [x] RULE-278 Approval start rounds: a monotonic round number per (processKey, businessId) defines the idempotency key（`WorkflowStartRoundService.java:20-55`，置信度 Medium） — 修正：加唯一约束（流程类型，单据 ID，轮次）消除并发重号。（已评审 2026-10-14）
- [x] RULE-279 Process-result handling is idempotent per instance and status (reference: quotation)（`DecideQuotationUseCase.java:32-75`，置信度 Medium） — 修正：幂等保留；发起人撤销后单据回到草稿，不记为驳回。（已评审 2026-10-14）
- [x] RULE-280 Unified approve: ownership gate before any field write, fail-closed field permission, default reasons（`WorkflowApproveServiceImpl.java:52-174`，置信度 High） — 修正：字段写入与任务完成同一事务（单体+单库可做到）。（已评审 2026-10-14）
- [x] **不复刻** · 老行为：RULE-288 Initiator cancels the whole process（`BpmProcessInstanceServiceImpl.java:237-356`，置信度 High） → 新系统做法：撤销不删历史，原审批人仍可见单据与审批记录。（不复刻；评审 2026-10-14）
- [x] RULE-289 Process-instance status and result lifecycle（`BpmProcessInstanceServiceImpl.java:555-650`，置信度 High） — 修正：生命周期保留，结果用枚举（已通过/已驳回/已撤销），不靠文案。（已评审 2026-10-14）
- [x] RULE-291 A rejection by an added co-signer is advisory only（`BpmTaskServiceImpl.java:982-1006`，置信度 High） — 修正：补偿任务不得把建议当正式驳回发出。（已评审 2026-10-14）
- [x] RULE-292 Row approval: any rejected row rejects the whole submission, but all rows still finish（`BpmTaskServiceImpl.java:1068-1098`，置信度 Medium） — 修正：加签人在行节点上的驳回同样只算建议（与 291 一致）。（已评审 2026-10-14）
- [x] RULE-293 Return to initiator using the `rejected` flag（`BpmTaskServiceImpl.java:1107-1152`，置信度 Medium） — 修正：按建议：改为「已退回」——退回发起人后其余未审行统一改为「已退回」，重提时整单重新审批（用户亲自决定）。（已评审 2026-10-14）
- [x] **不复刻** · 老行为：RULE-295 Reliable delivery task lifecycle (psa_reliable_delivery_task)（`ReliableDeliveryDispatcher.java:127-251`，置信度 High） → 新系统做法：新系统用 Modulith 事件发布登记表，重试有默认次数与退避。（不复刻；评审 2026-10-14）
- [x] RULE-300 "Skip when empty" bypasses a node that has no approver（`BpmUserTaskActivityBehavior.java:40-60`，置信度 High） — 修正：仅在节点显式配置「允许跳过」且确实无人时跳过；配置错误或脚本异常一律报错告警，不静默放行。（已评审 2026-10-14）
- [x] RULE-302 Repeat approver on the same node is auto-approved, except on rework or "reapprove on return"（`BpmTaskEventListener.java:334-409`，置信度 High） — 修正：行内容在上次审批后有变化（按内容比对）则必须重审。（已评审 2026-10-14）
- [x] RULE-303 Outbox redelivery, backoff and dead-letter（`BpmApprovalMqReliableSender.java:355-398`，置信度 High）（已评审 2026-10-14：新系统由事件发布登记表承担，消费方必须幂等。）
- [x] **不复刻** · 老行为：RULE-304 Reconcile job re-sends missed results from the bpm records（`BpmApprovalMqReliableSender.java:402-485`，置信度 Medium） → 新系统做法：投递由事件发布登记表保证，不再从引擎记录反推（老做法会把自动跳过、加签建议当结果发出）。（不复刻；评审 2026-10-14）
- [x] RULE-307 Anti-self-approval escalates to the initiator's direct leader（`BpmTaskAssignRuleServiceImpl.java:504-550`，置信度 High） — 修正：只有在没有其他非发起人候选时才升级。（已评审 2026-10-14）
- [x] **不复刻** · 老行为：RULE-308 End-of-process write-back of approve_status to the business table（`CustomizedBpmOAServiceImpl.java:303-337`，置信度 Medium） → 新系统做法：各业务模块通过类型化结果事件自行处理，去掉按表名拼 SQL 的通用回写。（不复刻；评审 2026-10-14）
- [x] **不复刻** · 老行为：RULE-311 The task-result consumer routes by business key, and a hook replaces the handler（`ApprovalTaskResultConsumer.java:41-75`，置信度 Medium） → 新系统做法：统一为钩子与业务处理都执行、顺序固定，与流程结果处理一致。（不复刻；评审 2026-10-14）

### 身份与组织（platform.identity、organization.workforce）（Phase 1a）

**已定：** RULE-320 Seeyon (A8 portal) SSO: server-side ticket check, one use per ticket, enabled users only、RULE-340 Role code `super_admin` bypasses every permission and role check、RULE-341 A function permission is granted when an enabled role links a menu with that permission string、RULE-343 Only enabled roles count; a user with no roles has no function permissions、RULE-354 A8 dept tree: prefix scope, parent from PATH, fail on a broken tree、RULE-358 Dept sync aborts if A8 returns < 80 % of active depts

**阻塞，需评审：**
- [x] RULE-316 Password login: same error for unknown user and wrong password; disabled users refused（`AdminAuthServiceImpl.java:114-151`，置信度 High） — 修正：只保留管理员应急账号（强口令 + MFA + 锁定，C1a-04）；未知账号与错误口令仍报同一错误；员工不再口令登录（D-02）。（已评审 2026-10-14）
- [x] **不复刻** · 老行为：RULE-318 Passwordless "customization" login issues real tokens for any username or user id（`AuthController.java:88-102`，置信度 High） → 新系统做法：免密端点不迁移（D-04），外部调用方改用签名、短时效、一次性票据并服务端校验，拒绝停用用户与超管（C1a-03、SEC-001）。（不复刻；评审 2026-10-14）
- [x] **不复刻** · 老行为：RULE-319 A8 login trusts the memberId in the request and redirects with tokens in the URL（`AuthController.java:104-117`，置信度 High） → 新系统做法：A8 入口统一走致远服务端验票（RULE-320），不信任请求中的 memberId，令牌不进 URL。（不复刻；评审 2026-10-14）
- [x] RULE-322 WeCom H5 OAuth login maps the WeCom userid to `we_com_id` exactly（`AdminAuthServiceImpl.java:543-626`，置信度 High） — 修正：按 we_com_id 精确映射保留；映射后必须「启用且在职」；删 local 环境固定用户后门；密钥由配置注入并轮换（SEC-006）。（已评审 2026-10-14）
- [x] **不复刻** · 老行为：RULE-324 PC WeCom web login guesses the user from the email local part or an "f-stripped" id（`SocialUserServiceImpl.java:169-194`，置信度 High） → 新系统做法：不按邮箱前缀或去掉 f 猜人，只按 we_com_id 精确映射（与 H5 同一键），未映射拒绝；令牌不进 URL。（不复刻；评审 2026-10-14）
- [x] **不复刻** · 老行为：RULE-326 Only password and Seeyon logins check the disabled status（`AdminAuthServiceImpl.java:153-216`，置信度 High） → 新系统做法：所有登录入口在建会话前过同一道检查：启用且未删除（identity BC 一处实现）。（不复刻；评审 2026-10-14）
- [x] **不复刻** · 老行为：RULE-327 Resigned (soft-deleted) users are kept out only by the lookup filter（`AdminAuthServiceImpl.java:153-164`，置信度 Medium） → 新系统做法：显式检查「已离职」并返回明确业务错误，不依赖 ORM 逻辑删除过滤，不抛空指针或越界。（不复刻；评审 2026-10-14）
- [x] **不复刻** · 老行为：RULE-329 KM token: anonymous caller, memberId used as a mobile number or a legacy-system id（`AdminAuthServiceImpl.java:253-281`，置信度 High） → 新系统做法：跳转 KM 须已登录，KM 身份取自会话；签名票据、HTTPS；集成密钥移到配置并轮换（SEC-002）。（不复刻；评审 2026-10-14）
- [x] **不复刻** · 老行为：RULE-331 Every login creates a refresh token and an access token for client "default"（`OAuth2TokenServiceImpl.java:54-62`，置信度 Medium） → 新系统做法：不再签发令牌对，改服务端会话（ADR-0009 §2，Spring Session JDBC + HttpOnly/SameSite Cookie）；时长为配置项（见 332）。（不复刻；评审 2026-10-14）
- [x] **不复刻** · 老行为：RULE-332 Access-token expiry slides to now + 10 h on every check（`OAuth2TokenServiceImpl.java:95-137`，置信度 High） → 新系统做法：会话时长「空闲 10h + 上限 24h」——10 小时无活动过期，登录 24 小时后无论是否活跃都须重新登录；均为配置项；令牌不打印到日志。（不复刻；评审 2026-10-14）
- [x] **不复刻** · 老行为：RULE-333 Refresh revokes sibling access tokens but never rotates the refresh token（`OAuth2TokenServiceImpl.java:64-93`，置信度 High） → 新系统做法：新系统无刷新令牌；登录时轮换会话 ID；新系统不读、不写、不校验老令牌表（C1a-06 已删除，用户『取消桥接』）。（不复刻；评审 2026-10-14）
- [x] **不复刻** · 老行为：RULE-335 Four SSO paths put access and refresh tokens in the redirect URL（`AuthController.java:104-132`，置信度 High） → 新系统做法：令牌不进 URL；SSO 回跳用一次性 code，在服务端换取 HttpOnly 会话 Cookie。（不复刻；评审 2026-10-14）
- [x] **不复刻** · 老行为：RULE-336 Services trust the `login-user` header, treat a bad token as anonymous, and ignore user type（`TokenAuthenticationFilter.java:40-135`，置信度 High） → 新系统做法：身份只来自服务端会话（CurrentUser），不信任任何外部身份请求头；无效会话返回 401 而非匿名；按接口族校验用户类型；mock 登录只限本地。（不复刻；评审 2026-10-14）
- [x] **不复刻** · 老行为：RULE-337 The gateway does no authentication（`TokenAuthenticationFilter.java:86-128`，置信度 High） → 新系统做法：默认拒绝集中在 platform.identity 一处（G28/G33）；匿名遍历全部接口返回 401（C1a-07）。（不复刻；评审 2026-10-14）
- [x] **不复刻** · 老行为：RULE-338 Disabling, deleting or a resignation via sync does not revoke live tokens（`AdminUserServiceImpl.java:338-412`，置信度 High） → 新系统做法：停用、删除、离职（含同步）、角色变更时即时吊销该用户全部会话；解析会话时校验用户状态。（不复刻；评审 2026-10-14）
- [x] **不复刻** · 老行为：RULE-344 Every user without a role silently gets role 2 whenever the user cache reloads（`PermissionServiceImpl.java:512-534`，置信度 Medium） → 新系统做法：不再在缓存刷新时隐式补授角色 2（C1a-08）；A8 新建在职员工时显式授予一次基础角色并审计；管理员移除后不补回；离职或已删除员工不授。（不复刻；评审 2026-10-14）
- [x] RULE-345 Role maintenance: reserved code, unique name and code, system roles frozen, default scope ALL（`RoleServiceImpl.java:90-173`，置信度 High） — 修正：保留 super_admin 预留码、名称与编码唯一、系统角色冻结、删角色连带删关联；新角色默认数据范围 SELF（最小权限），不再默认 ALL。（已评审 2026-10-14）
- [x] RULE-349 Business data scope = union over roles; ALL anywhere means unrestricted（`RoleServiceImpl.java:289-345`，置信度 High） — 修正：各角色并集保留；只计启用角色；自定义部门只在 DEPT_CUSTOM 下生效；排除已离职用户；「本人及下属」只算直接下属（问题「只算直接下属」，与老系统一致）；以显式 AccessScope（法人 × 部门 × 项目角色）表达。（已评审 2026-10-14）
- [x] **不复刻** · 老行为：RULE-350 Row filtering applies only to opt-in methods with `@DataOwner` fields（`DataPermissionDatabaseInterceptor.java:68-195`，置信度 High） → 新系统做法：不用注解开启的隐式 SQL 改写；AccessScope 显式传入仓储，异常即拒绝（fail-closed）（ADR-0009 §4、ADR-0019）。（不复刻；评审 2026-10-14）
- [x] **不复刻** · 老行为：RULE-351 An empty data scope fails open（`DataPermissionDatabaseInterceptor.java:108-112`，置信度 High） → 新系统做法：空数据范围返回零行，不放行。（不复刻；评审 2026-10-14）
- [x] RULE-355 A8 dept "disabled" test and how it maps to legacy-system status and deleted（`A8DeptMapper.java:35-48`，置信度 High） — 修正：停用判定保留；NULL 的处理写成显式规则（仍视为有效，同步报告列出 NULL 行，与 A8 负责人确认）；「停用」与「撤销」分为两个状态，不再混同。（已评审 2026-10-14）
- [x] RULE-356 A8 is authoritative for dept leader, regional AR, category and delivery flag（`SyncServiceImpl.java:184-221`，置信度 High） — 修正：A8 为部门负责人、区域 AR、类别、交付标识的权威来源；关系类型 ID 等改为配置并在启动时校验；多名负责人时取 ID 最小者并告警给数据管理员（确定性、不阻塞）。（已评审 2026-10-14）
- [x] RULE-357 Dept sync: insert, update on change, soft-delete what A8 no longer returns, revive（`DeptServiceImpl.java:200-305`，置信度 High） — 修正：A8 部门随 A8 新增、更新、撤销、恢复；FORCITIS one 不再有手工部门（D-06）；FORCITIS one 自有列不被同步覆盖（按所有权矩阵，ADR-0011）。（已评审 2026-10-14）
- [x] **不复刻** · 老行为：RULE-361 A8 user sync scope uses a substring match, unlike the dept prefix rule（`A8UserMapper.java:24-40`，置信度 High） → 新系统做法：人员同步范围改用与部门同步相同的前缀规则（PATH 以 00000001 开头），人员不指向未同步部门。（不复刻；评审 2026-10-14）
- [x] RULE-362 A new A8 person becomes a legacy-system user with username = phone and a default password（`SyncServiceImpl.java:299-340`，置信度 High） — 修正：A8 新人照旧建用户（ID 沿用 A8 ID）；不设本地口令（无共享默认口令）；创建时即写入 we_com_id。（已评审 2026-10-14）
- [x] RULE-363 Resignation = A8 login name is null → legacy-system user soft-deleted（`SyncServiceImpl.java:359-369`，置信度 High） — 修正：统一「有登录名」判定（null 与空串都视为无）；离职时吊销会话、收回角色；离职后 HR 字段继续更新以保留历史。（已评审 2026-10-14）
- [x] RULE-364 Rehire and phone change restore and re-enable the legacy-system user（`SyncServiceImpl.java:380-398`，置信度 High） — 修正：复职（A8 重新有登录名）时恢复；换手机号不覆盖 FORCITIS one 的手工停用（启用状态由 FORCITIS one 拥有）；用户名跟随 A8 登录名同步。（已评审 2026-10-14）
- [x] **不复刻** · 老行为：RULE-365 Per-field update semantics in user sync are asymmetric（`SyncServiceImpl.java:342-441`，置信度 High） → 新系统做法：A8 权威列按值同步并显式写 NULL（汇报线清空也同步）；单行缺部门或岗位时隔离该行并告警，不中断整批。（不复刻；评审 2026-10-14）
- [x] **不复刻** · 老行为：RULE-366 Users that drop out of the A8 result set are never deactivated（`SyncServiceImpl.java:283-441`，置信度 Medium） → 新系统做法：不在 A8 结果中的人员视为离职候选，经与部门同步相同的 80% 缩水保护后停用并吊销会话。（不复刻；评审 2026-10-14）
- [x] RULE-372 Two separate leader concepts: person leader and dept leader（`SyncServiceImpl.java:324-405`，置信度 High） — 修正：「直属领导」（人员 leader）与「部门负责人」两个概念保留，每个流程写明用哪一个；同步时校验存在、在职、非本人、无环，不合格的报告数据管理员；审批取人按 RULE-250 往上推一级。（已评审 2026-10-14）
- [x] **不复刻** · 老行为：RULE-376 WeCom card callback takes the acting user from the button key, not from FromUserName（`WeComCallbackController.java:65-135`，置信度 High） → 新系统做法：操作人按 FromUserName → we_com_id → 用户确定，且必须是该任务处理人；回调 token 与 AES key 移到配置并轮换（SEC-006）。（不复刻；评审 2026-10-14）

### 流程梳理结论（按阶段，SESSION_PLAYBOOK §13）

每个阶段实施前做端到端流程梳理，差异清单逐条由用户决定；结论来自 `process/<阶段>/contract.md`，生成到这里。

#### Phase 1a

# Phase 1a 流程梳理结论（行为契约）

只收录已有用户决定的差异，每条一句可测试的行为；来源与证据见同目录各流程文档。由 `build_all.py` 生成到 brief §5「流程梳理结论」。

## 登录与会话

- D-1a-29：不迁移；外部调用方改用签名、≤60 s、一次性票据，拒绝管理员角色（用户决定：RULE-318/319 评审；检查点 1「D-01..D-09 全部舍弃」）
- D-1a-30：先盘点调用方（含查明 customization-login 的调用方）；每个调用方一份切换步骤、一把密钥；签发方由用户逐个批准开启并做安全复审；老端点到 Phase 5 下线（用户决定：intent/2026-10-14-112-1a流程-登录-外部调用方切换.md）
- D-1a-31：须已登录，KM 身份取自会话，签名票据、HTTPS，密钥进配置并轮换；与入站票据分开（出站方向）（用户决定：RULE-329 评审）
- D-1a-32：新系统不读、不写、不校验老令牌表；老系统 后端不得作为票据签发方；相关文档按此统一（用户决定：「取消桥接 (Recommended)」、检查点 2 Q-1（INTENT））
- D-1a-33：新系统不识别老令牌；从合并待办点老待办时，PC 未登录老系统则回老登录页，H5 在企业微信内静默登录老系统（用户决定：Q-1「1a 删除桥接」；REIMAGINED_ARCHITECTURE「1a 可接受」）
- D-1a-34：三个入口统一按 `we_com_id` 精确映射，未映射拒绝（用户决定：RULE-324 评审）
- D-1a-35：上线前按「在职但无企业微信 ID」清单由组织数据管理员在 A8 补齐；1a 出口要求该清单为 0（确无企业微信者逐个登记原因）（用户决定：intent/2026-10-14-120-1a流程-登录-企业微信ID覆盖.md；intent/2026-10-14-136-经-psa-coordinato.md）
- D-1a-36：小程序保留，与 H5 共用映射与「启用且在职」（用户决定：RULE-323「保留 (Recommended)」）
- D-1a-37：同一服务端会话；小程序用请求头携带会话标识、不用 Cookie，该入口不接受 Cookie 故无需 CSRF；不引入令牌对（用户决定：intent/2026-10-14-114-1a流程-登录-小程序会话.md）
- D-1a-38：Taro 4 H5 企业微信静默登录与待办（用户决定：检查点 1 批准 C1a-19 为 P0）
- D-1a-39：前端落地页接收回跳，再 POST 到 `sessions:*-login`，返回 JSON；规格改为与代码一致；补外部票据落地页；一次性凭证不出现在服务端访问日志的 GET 参数中（用户决定：intent/2026-10-14-121-1a流程-登录-接口形态.md）
- D-1a-40：同样做法，新回跳地址由 A8 管理员登记；失败页给「回到老系统登录」（用户决定：RULE-320/321 按老行为复刻（未标记评审））
- D-1a-41：服务端会话，空闲 10 小时、绝对 24 小时，均为配置项；登录时更换会话 ID（用户决定：「空闲10h+上限24h (Recommended)」（RULE-331/332））
- D-1a-42：无刷新令牌（用户决定：RULE-333 评审）
- D-1a-43：停用、离职（含同步）、角色变更即时吊销全部会话；每次请求校验账号与在职状态（缓存 ≤ 60 秒）（用户决定：RULE-338/363/366 评审）
- D-1a-44：同步每 5 分钟；距上次成功超过 2 个周期（10 分钟）或被拦下即告警；离职者最长约 6 分钟失效（用户决定：intent/2026-10-14-123-1a流程-登录-其余建议.md）
- D-1a-45：停用、离职、角色变更都写「会话吊销」审计；会话本身不设状态字段，过期 / 吊销 / 登出原因只记审计（用户决定：intent/2026-10-14-123-1a流程-登录-其余建议.md）
- D-1a-46：登出失效会话并审计；无会话时也返回成功（用户决定：RULE-334 按老行为复刻（未标记评审））
- D-1a-47：员工无口令；应急账号强口令（≥ 12 位 + 复杂度）+ TOTP + 5 次失败锁 15 分钟，仅内网；改口令后吊销其其他会话（用户决定：RULE-316/317/370 评审；C1a-04）
- D-1a-48：应急账号须对应一名超管持有人（运维命令、双人在场登记、审计），登录后只进管理模式 1 小时并告警；应急登录不要求「在职」快照（A8 或同步故障时仍可用），持有人离职时由运维注销；保留 2 个（用户决定：intent/2026-10-14-113-1a流程-登录-应急账号.md；intent/2026-10-14-156-问-应急账号以今天的决定还是-A.md）
- D-1a-49：切换前盘点非 A8 账号，去留由用户逐个决定（用户决定：Q-4「四项都同意」（待执行））
- D-1a-50：均不迁移（用户决定：「短信不迁移，审批通知走企业微信」；D-01..D-03；RULE-325/328 评审）
- D-1a-51：默认拒绝；身份只来自服务端会话；无效会话 401；匿名遍历全部接口返回 401（用户决定：RULE-336/337 评审；C1a-07）
- D-1a-52：登录未命中即时到 A8 查一人建档；`employee` 仅在建档时授予一次（用户决定：RULE-344 评审；见 D-1a-14、D-1a-24）
- D-1a-53：不迁移，老表只读归档；老系统下线时导出，保留期按公司审计要求（下线前再定）（用户决定：intent/2026-10-14-122-1a流程-登录-历史登录日志.md）
- D-1a-54：账号 ENABLED / DISABLED（identity 拥有）与员工 EMPLOYED / SEPARATED（workforce 拥有）分开（用户决定：RULE-363/364 评审；见 D-1a-19）
- D-1a-55：致远票据、签名票据 nonce、OAuth state 均为数据库唯一键原子一次性；state 10 分钟（用户决定：I-9；RULE-320）
- D-1a-56：签发方默认关闭；由用户逐个批准后以受控变更开启（见 D-1a-30）（用户决定：intent/2026-10-14-112-1a流程-登录-外部调用方切换.md）
- D-1a-57：规格中的 `AccountStatusChanged` 删去，停用时同步吊销会话；有订阅方时再加；老系统外部订阅方在 Phase 5 前盘点（用户决定：intent/2026-10-14-123-1a流程-登录-其余建议.md）
- D-1a-58：登录后按哪个法人生效、数据范围授予表由谁维护，在 1b 流程梳理时定（用户决定：intent/2026-10-14-123-1a流程-登录-其余建议.md）

## 组织与人员同步

- D-1a-01：只读 4 个 A8 视图，列白名单启动校验，A8 账号无写权限（用户决定：「确保权责业务事实全部在新系统」「改名并移到 integration」（`INTENT.md`，REIMAGINED_ARCHITECTURE §integration））
- D-1a-02：新系统独立读 A8，不读不写老表；老同步保留到老系统不再读老用户表为止（最迟 Phase 5）；对账只比新快照与 A8（用户决定：intent/2026-10-14-103-1a-涉及哪些聚合-状态机是怎样.md）
- D-1a-03：每 5 分钟全量同步、单事务；生产以环境变量开启（用户决定：intent/2026-10-14-110-1a流程-组织-其余建议.md）
- D-1a-04：前缀 `00000001%`，与部门一致（用户决定：RULE-361 评审（RULE_REVIEWS 第三批））
- D-1a-05：ACTIVE / DISABLED / REVOKED 三态；NULL 视为有效并记问题清单（生产 NULL 0 行，`phase1a/A8_DEPT_NULL_FLAGS.md`）（用户决定：RULE-355 评审；NULL 确认人见 D-1a-08）
- D-1a-06：NULL 视为内部，并记入问题清单由组织数据管理员在 A8 补齐；生产计数待查（用户决定：intent/2026-10-14-109-1a流程-组织-人员NULL标志.md）
- D-1a-07：有效部门数与在职人数各自下降超 20% 即 HELD（用户决定：RULE-358 保留、RULE-366「经与部门同步相同的 80% 缩水保护」）
- D-1a-08：新增角色「组织数据管理员」（放行、法人维护、问题清单），由业务 Admin 指定人选；放行须填原因；HELD 与每日问题汇总经企业微信发给该角色（用户决定：intent/2026-10-14-104-1a流程-组织-放行与告警.md；intent/2026-10-14-157-问-组织数据管理员的职责与-AD.md）
- D-1a-09：同步批次 RUNNING / SUCCEEDED / HELD / RELEASED / FAILED，不设「拒绝」；HELD 只能放行或等源修复后自动恢复（用户决定：intent/2026-10-14-110-1a流程-组织-其余建议.md）
- D-1a-10：每天自动对账并可手动触发；比较人员、部门、上级关系，并加部门负责人、区域 AR、企业微信 ID；差异进问题清单，由组织数据管理员在 A8 修正后重新同步；1a 出口要求零差异，不接受签字豁免（用户决定：intent/2026-10-14-105-1a-还有哪些-DDD-设计需要.md）
- D-1a-11：全部舍弃，组织数据只读，改动回 A8（用户决定：D-06「按建议批准」（检查点 1）；RULE-359/368/369 评审）
- D-1a-12：A8 权威列按值同步含 NULL；生日推迟到费用阶段（用户决定：intent/2026-10-14-110-1a流程-组织-其余建议.md）
- D-1a-13：建档即写入；同一 ID 属多名在职者时都不存并记问题；登录未命中按 A8 单人查询当场建档（用户决定：intent/2026-10-14-110-1a流程-组织-其余建议.md）
- D-1a-14：新建账号时显式授予一次 `employee`，管理员移除后不补回（用户决定：RULE-344 评审）
- D-1a-15：领导不存在、已离职、是本人或成环时写空并记问题；审批按 RULE-250 往上推，推不到转流程管理员并告警（用户决定：intent/2026-10-14-106-1a流程-组织-汇报线不合格.md）
- D-1a-16：离职（登录名为空、A8 不再返回）即吊销全部会话并收回角色；HR 字段继续更新（用户决定：RULE-338、RULE-363 评审）
- D-1a-17：复职时重新授予基础角色（employee），其余角色由管理员重新分配；审计记录（用户决定：intent/2026-10-14-115-1a流程-复职角色.md）
- D-1a-18：同步发现离职后，其待办按节点指派规则重新取人（汇报线往上推），取不到转流程管理员并告警；审计记录原、新处理人（用户决定：intent/2026-10-14-107-1a流程-组织-离职者在途待办.md）
- D-1a-19：员工 EMPLOYED / SEPARATED（workforce）与账号 ENABLED / DISABLED（identity）分开；同步不动账号启用状态；A8 缺席即 SEPARATED（用户决定：RULE-364、366 评审）
- D-1a-20：ACTIVE / DISABLED / DELETED 同步（用户决定：RULE-367 评审）
- D-1a-21：一轮单事务写入，缺失行隔离并记问题（用户决定：RULE-365 评审）
- D-1a-22：递归保留，权限范围排除已撤销部门，历史报表可含（用户决定：RULE-360 评审）
- D-1a-23：`OrgSnapshotChanged` 至少一次投递；字段清单与同步字段同一份；已撤销不重复发（用户决定：RULE-373 评审）
- D-1a-24：账号由组织变更事件异步创建，登录未命中当场建档兜底；1a 不加岗位、法人变更事件（用户决定：intent/2026-10-14-110-1a流程-组织-其余建议.md）
- D-1a-25：新维护页上线前，每天从老库只读视图刷新（用户决定：intent/2026-10-14-110-1a流程-组织-其余建议.md）
- D-1a-26：法人小表由新系统组织数据管理员维护；停用后不能用于新单据编号，历史单据保留原法人（用户决定：intent/2026-10-14-104-1a流程-组织-放行与告警.md；intent/2026-10-14-110-1a流程-组织-其余建议.md；intent/2026-10-14-157-问-组织数据管理员的职责与-AD.md）
- D-1a-27：法人归属采用组合模型：「部门 → 法人」对照为默认（子部门继承，可在子树上覆盖），员工可单独指定（优先于部门对照），两者都带生效区间、区间不重叠，由组织数据管理员维护；业务事实按事实发生日解析法人，当前会话按当天解析（取代写死的 1）；表 `org_unit_legal_entities`、`employee_legal_entities`（用户决定：intent/2026-10-14-108-1a流程-组织-法人归属.md；intent/2026-10-14-137-经-psa-coordinato.md）
- D-1a-28：法人表增加「U8C 核算组织编码」（数字、唯一）；字母代码继续用于业务编号；对接 U8C 与老系统按数字编码（用户决定：intent/2026-10-14-111-1a流程-组织-法人代码格式.md）

## 审批通用流程

- D-1a-59：候选组，先认领后处理；通知发给候选组全员（用户决定：RULE-249「错误，改成候选组认领」；Q-3「通知发候选组全员 (Recommended)」）
- D-1a-60：汇报线不完整往上推一级，一直找不到转流程管理员并告警（用户决定：RULE-250（用户原话见 RULE_REVIEWS））
- D-1a-61：流程管理员是角色（可多人）；取不到人的任务进该角色候选组并用企业微信告警；管理员可改派（填原因、审计）；生产未配置时系统拒绝启动（用户决定：intent/2026-10-14-124-1a流程-审批-流程管理员.md）
- D-1a-62：仅显式配置且确实无人时跳过；配置错误或脚本异常报错告警（用户决定：RULE-300 评审）
- D-1a-63：排除停用（账号）与离职（员工）；部门无负责人取上级部门负责人；候选集无空值（用户决定：RULE-253 评审）
- D-1a-64：只有没有其他非发起人候选时才升级到直属领导（用户决定：RULE-307 评审）
- D-1a-65：不自动通过，按反自审升级给直属领导（用户决定：intent/2026-10-14-128-1a流程-审批-办理动作.md）
- D-1a-66：保留自动同意；行内容在上次审批后有变化（按内容比对）则必须重审；自动同意也发任务结果事件并标注「自动」（用户决定：intent/2026-10-14-131-1a流程-审批-事件法人DMN.md）
- D-1a-67：知会在合并待办里单列，发卡片，打开即已读，不阻塞流程；随第一个用到知会的流程交付（用户决定：intent/2026-10-14-130-1a流程-审批-知会历史投递.md）
- D-1a-68：会签 / 或签与行级审批随第一个使用者交付（用户决定：Q-3「收窄」；RULE-248）
- D-1a-69：退回即以「已退回」结束本轮；重提开新一轮，历史按轮次保留（用户决定：RULE-275/287「退回即结束本轮，重提开新一轮 (Recommended)」；RULE-293）
- D-1a-70：同（用户决定：RULE-271/290 已定）
- D-1a-71：同一轮内回退重审，状态仍为审批中，不通知业务模块，审计记录；随第一个用到的流程交付（用户决定：intent/2026-10-14-128-1a流程-审批-办理动作.md）
- D-1a-72：撤回与退回一样结束本轮（记为已退回，来源发起人撤回），单据回到可编辑，重提开新一轮；显示与执行同一判断（用户决定：intent/2026-10-14-128-1a流程-审批-办理动作.md）
- D-1a-73：撤销不删历史，原审批人仍可见单据与审批记录；单据回草稿、不记驳回（用户决定：RULE-288、RULE-279 评审）
- D-1a-74：同（用户决定：RULE-268/309 已定）
- D-1a-75：只能转给在职、非发起人用户；原因必填并审计；候选组任务须先认领再转交；通知被转交人（用户决定：intent/2026-10-14-128-1a流程-审批-办理动作.md）
- D-1a-76：只有已认领的当前处理人可加签；加签人须在职、非发起人；加签意见只作参考（行节点同样）（用户决定：intent/2026-10-14-129-1a流程-审批-加签重提钩子时限.md）
- D-1a-77：批量只处理本人待办；只有流程管理员可代理，每次选被代理人并填原因，可代理通过与驳回，审计记录操作人与被代理人，企业微信通知被代理人，不设长期委托（用户决定：intent/2026-10-14-125-1a流程-审批-代理边界.md）
- D-1a-78：唯一键（流程类型，单据 ID，轮次）；同一单据 + 轮次的重试返回原实例；已退回、已驳回、已撤销可开新一轮；已通过不允许再发起（用户决定：intent/2026-10-14-126-1a流程-审批-重新发起.md）
- D-1a-79：发起人只能是当前登录用户；所有续跑入口检查轮次；只有原发起人能重提；发起人离职时由流程管理员撤销在途审批并通知业务负责人重新发起；管理员不能代发起（用户决定：intent/2026-10-14-129-1a流程-审批-加签重提钩子时限.md）
- D-1a-80：钩子保留，字段写入与任务完成同一事务；失败返回明确校验信息，不延续弹窗协议（用户决定：intent/2026-10-14-129-1a流程-审批-加签重提钩子时限.md）
- D-1a-81：退回后其余未审行统一改为「已退回」，重提整单重新审批（用户决定：RULE-293（用户亲自决定）、RULE-292）
- D-1a-82：类型化事件经事件发布登记表，至少一次，消费方幂等；不从引擎记录反推；钩子与业务处理都执行、顺序固定（用户决定：RULE-295/304/308「错误」、RULE-303「正确」、RULE-311）
- D-1a-83：待办 / 完成 / 知会卡片；候选组每人一张，认领后更新；发起人收到结果通知（用户决定：C1a-15、Q-3、ADR-0019）
- D-1a-84：时限按流程节点配置（工作日），未配置不计时；到期提醒、再过 1 个工作日升级直属上级（取不到给流程管理员并告警）；不自动通过；按工作日小时统计时效；企业微信卡片 + 合并待办标红（用户决定：intent/2026-10-14-129-1a流程-审批-加签重提钩子时限.md；intent/2026-10-14-118-1a流程-审批时效统计.md；intent/2026-10-14-133-问-超时提醒与升级的具体做法-加.md）
- D-1a-85：老实例在老引擎走完；每个流程在其业务迁入阶段出口前排空；停滞超 30 天的老实例每周列清单给流程管理员处理；离职者名下老任务同样列入（用户决定：intent/2026-10-14-127-1a流程-审批-在途老单据.md）
- D-1a-86：新任务（含候选组）+ 老库只读视图，行任务合并，计数一致（用户决定：RULE-261；C1a-14）
- D-1a-87：规格的接口路径、权限点（`decide` / `approve`）与 `rows[]` 按代码修订（用户决定：intent/2026-10-14-131-1a流程-审批-事件法人DMN.md）
- D-1a-88：结果枚举含 RETURNED（退回结束本轮）；规格同步（用户决定：intent/2026-10-14-131-1a流程-审批-事件法人DMN.md）
- D-1a-89：单一枚举 RUNNING / RETURNED / APPROVED / REJECTED / CANCELLED；退回 RUNNING → RETURNED，重提 RETURNED → RUNNING（新一轮）（用户决定：RULE-289；M3；RULE-293）
- D-1a-90：新增只追加的任务办理记录（含自动同意、加签建议、转交、代理），已办与逐轮历史从中查询；撤销后原审批人仍可见（用户决定：intent/2026-10-14-130-1a流程-审批-知会历史投递.md）
- D-1a-91：事件登记表 + 出站投递 PENDING / SENT / SKIPPED / FAILED / DEAD_LETTER（8 次后死信）；进死信即告警运维与流程管理员；管理页可重投或放弃（填原因、审计）（用户决定：intent/2026-10-14-130-1a流程-审批-知会历史投递.md）
- D-1a-92：退回（含撤回）后业务单据回到可编辑并带「已退回」标记，具体状态名在各业务阶段定（用户决定：intent/2026-10-14-130-1a流程-审批-知会历史投递.md）
- D-1a-93：1a 只有 4 种类型；其余随各流程迁入时翻译（用户决定：intent/2026-10-14-131-1a流程-审批-事件法人DMN.md）
- D-1a-95：任务结果含 APPROVED / REJECTED / RETURNED；自动同意发结果并标「自动」；加签建议不作正式结果；结果事件带行 ID（用户决定：intent/2026-10-14-131-1a流程-审批-事件法人DMN.md）
- D-1a-96：去掉通用回写，业务模块监听类型化结果事件（用户决定：RULE-308）
- D-1a-97：转交、加签、知会事件随功能交付时补；AFTER 钩子与老任务创建 / 活动消息的去留随第一个使用者（差旅 Phase 3）定；规格补列 TaskClaimed（用户决定：intent/2026-10-14-131-1a流程-审批-事件法人DMN.md）
- D-1a-98：登记表默认重试；企业微信出站 8 次后死信；消费方必须幂等（用户决定：RULE-303）
- D-1a-99：审批请求记录单据法人（业务模块传入）；取人不限同法人；审批期间可见性按参与人规则、不受法人范围限制；1b 落地（用户决定：intent/2026-10-14-131-1a流程-审批-事件法人DMN.md）
- D-1a-100：1a 不用 DMN（BPMN 扩展属性 + `AssigneeResolver`）；出现真正的多维决策表时在该阶段引入 DMN（用户决定：intent/2026-10-14-131-1a流程-审批-事件法人DMN.md）
- D-1a-101：审批请求拥有审批状态，业务模块拥有单据状态，经事件同步（用户决定：RULE-308、RULE-279、RULE-289）
- D-1a-179：办理只要求是处理人或候选人（含流程管理员），不另需权限码；待办列表登录即可；代码现要求「处理人 + `task:approve`」（用户决定：intent/2026-10-14-159-问-办理审批是否另需权限码-主管.md）
- D-1a-180：主管 / PMO 不因身份看审批件，按业务单据的数据范围（ADR-0026）看单据，审批记录跟随单据显示；流程管理员另有只读「全部待办」（D-1a-139）（用户决定：intent/2026-10-14-159-问-办理审批是否另需权限码-主管.md）
- D-1a-181：候选人、办理人、升级上级、流程管理员、加签人、被转交人、知会人可只读查看单据与附件；已办理者、发起人、知会对象办后保留只读，未办理的候选人在任务关闭时收回；不含另行授权的敏感字段；1b 交付（用户决定：intent/2026-10-14-143-问-审批期间谁能看审批中的单据.md）

## TIMESHEET审批

- D-1a-102：任一驳回 / 退回撤销全部任务，以 RETURNED 结束本轮，重提开新一轮（用户决定：M3「退回即结束本轮，重提开新一轮 (Recommended)」（RULE-275/287））
- D-1a-103：无发起人节点（用户决定：M3（`phase1a/TIMESHEET_REPLAY.md`））
- D-1a-104：候选组，任一人认领办理即完成；回放时把老「全部通过」节点按「任一」比较，差异归为已知变更（用户决定：intent/2026-10-14-140-问-工时审批多审批人节点按会签还.md）
- D-1a-105：取人所需的表单字段未传入视为配置错误，发起失败并告警；仅显式允许且确实无人才跳过（用户决定：intent/2026-10-14-144-审批时限计时细则-逐项勾选-计时.md）
- D-1a-106：类型化事件经事件发布登记表，至少一次，消费方幂等（用户决定：RULE-295/303/304/308）
- D-1a-107：1a 不对外暴露工时提交；TIMESHEET 只用于离线回放；入口、归属校验、结果处理、提交权限随 Phase 2 交付（用户决定：intent/2026-10-14-141-问-1a-已有的工时单提交接口怎.md）
- D-1a-108：按规则重新取人，取不到转流程管理员并告警（用户决定：intent/2026-10-14-107-1a流程-组织-离职者在途待办.md）
- D-1a-109：候选组全员各一张卡，认领后其余卡片置已处理（用户决定：Q-3「通知发候选组全员 (Recommended)」）
- D-1a-110：截止为 T+1 工作日 23:59；不自动通过；提醒、升级、统计见下列各条（用户决定：intent/2026-10-14-116-1a流程-工时审批-截止时刻.md）
- D-1a-111：每个任务从创建时起算，重提新一轮重新计时；T = 创建所在工作日，非工作日创建顺延到下一工作日；截止 = T 之后第 1 个工作日 23:59；并行分支各任务各自计时；转流程管理员的任务同样计时（用户决定：intent/2026-10-14-144-审批时限计时细则-逐项勾选-计时.md）
- D-1a-112：截止后下一个工作日 09:00 提醒（候选组发全体候选人）；截止后第 2 个工作日 09:00 仍未办理则升级（用户决定：intent/2026-10-14-144-审批时限计时细则-逐项勾选-计时.md；intent/2026-10-14-133-问-超时提醒与升级的具体做法-加.md）
- D-1a-113：已认领：升级给认领人的直属上级（加入候选、原审批人保留，原卡片改为「已超时，已升级给某人」）；未认领的候选组：升级给流程管理员；取不到上级转流程管理员并告警；只升一次（用户决定：intent/2026-10-14-142-问-升级给谁的直属上级-候选组未.md；intent/2026-10-14-133-问-超时提醒与升级的具体做法-加.md）
- D-1a-114：一律 Asia/Shanghai；一份默认日历，预留法人专属日历但 1a 不启用；2016–2026 共 4,018 天迁入；缺年份报错告警；2027 年须在 1a 上线前录入（用户决定：intent/2026-10-14-117-1a流程-工作日历.md；intent/2026-10-14-144-审批时限计时细则-逐项勾选-计时.md）
- D-1a-115：组织数据管理员每年 12 月 1 日前录入下一年，11 月系统提醒；在普通会话中操作并审计（用户决定：intent/2026-10-14-104-1a流程-组织-放行与告警.md）
- D-1a-116：耗时 = 落在工作日内的自然小时；部门取办理时审批人所在部门；当期办结率 = 当月到期任务中按时办结的比例；驳回与退回也算办结；历史不回算；审批请求带法人后（1b）增加法人维度（用户决定：intent/2026-10-14-118-1a流程-审批时效统计.md；intent/2026-10-14-145-审批时效统计与管理员-逐项勾选.md；intent/2026-10-14-139-合并待办投递与链接-逐项勾选-每.md）
- D-1a-117：只对新系统任务标红与统计，老待办不标红；提醒与升级用企业微信卡片并在合并待办中标红（用户决定：intent/2026-10-14-134-问-老系统待办是否也按工作日算超.md；intent/2026-10-14-118-1a流程-审批时效统计.md）
- D-1a-118：不自动通过（用户决定：intent/2026-10-14-116-1a流程-工时审批-截止时刻.md）
- D-1a-119：候选人、办理人、升级上级、流程管理员、加签人、被转交人、知会人可只读查看单据与附件；已办理者、发起人、知会对象办后保留只读，未办理的候选人在任务关闭时收回；业务模块经审批的「是否参与人」端口校验；查看不写审计（用户决定：intent/2026-10-14-143-问-审批期间谁能看审批中的单据.md）
- D-1a-120：任务升级或转给流程管理员后，管理员作为候选人以本人名义办理（不算代理）；替别人办理时才走代理（用户决定：intent/2026-10-14-145-审批时效统计与管理员-逐项勾选.md）
- D-1a-121：审批人看自己的时效；部门负责人看本部门；流程管理员与管理层看全部；不公开个人排名（用户决定：intent/2026-10-14-145-审批时效统计与管理员-逐项勾选.md）
- D-1a-122：1a 不用 DMN（BPMN 扩展属性 + `AssigneeResolver`）（用户决定：intent/2026-10-14-131-1a流程-审批-事件法人DMN.md）
- D-1a-123：汇报线不完整往上推，推不到转流程管理员并告警，不阻断提交（用户决定：intent/2026-10-14-106-1a流程-组织-汇报线不合格.md）
- D-1a-124：只有没有其他非发起人候选时才升级（用户决定：RULE-307）
- D-1a-125：法人表另存 U8C 核算组织编码（数字、唯一），老 `company_code` 迁入历史工时单时按此映射（用户决定：intent/2026-10-14-111-1a流程-组织-法人代码格式.md）
- D-1a-126：法人按 ADR-0026 组合模型（部门 → 法人默认 + 员工单独指定，均带生效区间）按事实发生日取值；工时的取值点在 Phase 2 定（用户决定：intent/2026-10-14-137-经-psa-coordinato.md）
- D-1a-127：留到 Phase 2 流程梳理，由用户逐条决定（用户决定：intent/2026-10-14-145-审批时效统计与管理员-逐项勾选.md；intent/2026-10-14-119-1a流程-填报截止.md）

## 合并待办与通知

- D-1a-128：「待办数量一致」以老审批待办接口为准：老视图加入未读知会（单列「知会」，点击跳老系统），只对行审批节点合并；工时可编辑提醒与资源任务不在 1a 收件箱，不计入比较（用户决定：intent/2026-10-14-132-问-合并待办-待办数量一致-按什.md）
- D-1a-129：认领 / 办理后同一张卡改为「已由某人处理」，其他候选人卡片一起更新（用户决定：Q-3）
- D-1a-130：收件箱窗口重新获得焦点即刷新，并每 60 秒轮询；不做服务端推送（用户决定：intent/2026-10-14-138-合并待办卡片与展示-逐项勾选-办.md）
- D-1a-131：候选组卡片每人一条投递（每人一个更新凭证）；修正「已处理」与首次发送的竞态（发送成功后再检查并补更新）（用户决定：intent/2026-10-14-139-合并待办投递与链接-逐项勾选-每.md）
- D-1a-132：8 次、30 秒起退避；进死信即告警运维与流程管理员，管理页可重投或放弃（用户决定：intent/2026-10-14-130-1a流程-审批-知会历史投递.md）
- D-1a-133：卡片显示发起人、部门、发起时间、截止时间；业务摘要由业务模块在发起时随请求提供，通知不回查业务单据（用户决定：intent/2026-10-14-138-合并待办卡片与展示-逐项勾选-办.md）
- D-1a-134：卡片落地到 H5 待办（Taro）；1a 现落到 PC 站点（用户决定：C1a-19 P0）
- D-1a-135：所有流程办结（通过 / 驳回 / 退回 / 撤销）给发起人发「完成」卡片；知会卡片随知会节点发（用户决定：intent/2026-10-14-138-合并待办卡片与展示-逐项勾选-办.md）
- D-1a-137：提醒与升级的卡片与收件箱标红按 D-1a-112、113、117；超时与升级属于审批任务，发事件供业务模块选用；投递键区分待办、提醒、升级（用户决定：intent/2026-10-14-133-问-超时提醒与升级的具体做法-加.md）
- D-1a-138：新系统卡片按钮回调推迟（P1）；实现时以 FromUserName → we_com_id → 用户定人并校验处理人；新系统用新的企业微信自建应用（新 agentId、新密钥、独立回调），老应用随老系统下线，老回调密钥按「老系统先不管」不在 1a 处理（用户决定：intent/2026-10-14-135-问-新系统卡片与按钮回调用哪个企.md）
- D-1a-139：流程管理员的只读「全部待办」视图（可按超时筛选），查看写审计；其余人不保留；批量只处理本人待办（用户决定：intent/2026-10-14-138-合并待办卡片与展示-逐项勾选-办.md）
- D-1a-140：老待办视图补上模块与业务 ID，按老 PC 详情路径拼链接，上线前用测试账号实测（用户决定：intent/2026-10-14-139-合并待办投递与链接-逐项勾选-每.md）
- D-1a-141：发送时跳过已离职候选人并记 SKIPPED；全部候选人缺企业微信 ID 时也写入投递记录并告警（I-6）；「在职但无企业微信 ID」清单为 0 是 1a 出口条件（用户决定：intent/2026-10-14-139-合并待办投递与链接-逐项勾选-每.md；intent/2026-10-14-136-经-psa-coordinato.md）
- D-1a-142：测试收件人白名单配置，非白名单标 SKIPPED（用户决定：RULE-375）
- D-1a-143：收件箱不按法人筛选；审批请求带法人后（1b）时效统计增加法人维度（用户决定：intent/2026-10-14-139-合并待办投递与链接-逐项勾选-每.md）

## 业务编号

- D-1a-144：主单据（报价、工时单、差旅、收入确认单、结转批次、凭证）进统一编号，随各自迁入阶段加入；项目号、合同号继续用 A8 的号；条款、修订等子级序号保留「父号-序号」，不进规则模型（用户决定：intent/2026-10-14-150-业务编号范围与状态-逐项勾选-统.md）
- D-1a-145：统一新格式 `{TYPE}-{LE}-{yyyyMM}-{seq:4}`，各单据模板由管理员确认；TIMESHEET 1a 即用统一新格式（intent 100）；凭证号模板须财务确认（ADR-0027）（用户决定：「业务编号统一新格式」（INTENT）；ADR-0010 决定 5）
- D-1a-147：未确认或已停用的规则不发号，取号失败并告警；修改已确认的模板生成新版本，须重新确认后按生效日期启用（用户决定：intent/2026-10-14-146-问-未确认的编号规则能不能发号.md）
- D-1a-148：规则状态为待确认 / 已确认 / 停用，不设草稿；停用与启用有入口并审计；停用后该类单据取号失败并告警（用户决定：intent/2026-10-14-150-业务编号范围与状态-逐项勾选-统.md）
- D-1a-149：法人停用后不能用于新单据编号，历史单据保留原法人（用户决定：intent/2026-10-14-110-1a流程-组织-其余建议.md）
- D-1a-150：新单据一律按 ADR-0026 取法人后再取号；模板不含法人段时计数不分法人（用户决定：intent/2026-10-14-150-业务编号范围与状态-逐项勾选-统.md；intent/2026-10-14-137-经-psa-coordinato.md）
- D-1a-151：法人表另存 U8C 核算组织编码，字母代码用于编号（用户决定：intent/2026-10-14-111-1a流程-组织-法人代码格式.md）
- D-1a-152：原编号导入时：工时按单上核算组织编码、其余按 ADR-0026 在单据日期推算；推不出的法人留空（对照表法人列改为可空），原编号仍可检索（用户决定：intent/2026-10-14-149-问-老报价-差旅单没有法人-导入.md）
- D-1a-153：草稿无正式号（界面显示「草稿」），首次提交审批时在同一事务内取号；退回重提沿用原号；不走审批的单据在生效时取号（用户决定：intent/2026-10-14-147-问-新系统统一在什么时点取号-首.md）
- D-1a-154：作废单据保留号码并可查；「不跳号」指计数器不跳，不承诺有效单据号码连续（用户决定：intent/2026-10-14-150-业务编号范围与状态-逐项勾选-统.md）
- D-1a-155：编号日期段与计数周期取单据业务日期；各单据业务日期字段在其迁入阶段定（用户决定：intent/2026-10-14-151-业务编号日期-审计与下游-逐项勾.md）
- D-1a-156：模板修改、确认、停用、启用都写审计（前后值与操作人）并登记 CONFIG_CHANGELOG（用户决定：intent/2026-10-14-151-业务编号日期-审计与下游-逐项勾.md）
- D-1a-157：组织数据管理员维护模板，在普通会话中操作并审计；确认为独立权限，由业务 Admin 确认（与修改人不同）；涉及凭证号的模板另须财务确认（用户决定：intent/2026-10-14-148-问-谁维护与确认编号模板-组织数.md；intent/2026-10-14-157-问-组织数据管理员的职责与-AD.md）
- D-1a-158：PG 原子递增，行锁至事务结束；重置周期可配；起始值可配（用户决定：ADR-0010 决定 3、4；ADR-0002）
- D-1a-159：序号用到 90% 时企业微信预警组织数据管理员；确认模板时位数按历史峰值 2 倍以上设定；超限取号失败（用户决定：intent/2026-10-14-151-业务编号日期-审计与下游-逐项勾.md）
- D-1a-160：OA 字段长度、合同引用报价号、检索等下游适配在各单据迁入阶段核对；原编号检索增加前缀模糊匹配（用户决定：intent/2026-10-14-151-业务编号日期-审计与下游-逐项勾.md）
- D-1a-161：老编号作「原编号」可检索、新旧对照可查；导入随各单据切换阶段（用户决定：检查点 2「两条都按建议修订」（Q-2））

## 审计

- D-1a-162：停用、角色变更、离职都写「会话吊销」审计；会话清理作业对每个过期会话写一条审计（空闲过期或到达上限）（用户决定：intent/2026-10-14-123-1a流程-登录-其余建议.md；intent/2026-10-14-154-审计记哪些动作-逐项勾选-必记清.md）
- D-1a-163：强制下线列为管理模式的平台权限：吊销某用户全部会话，填原因并审计（用户决定：intent/2026-10-14-154-审计记哪些动作-逐项勾选-必记清.md；intent/2026-10-14-158-问-审计查看-强制下线与-ADR.md）
- D-1a-164：放行须填原因，原因进审计（用户决定：intent/2026-10-14-104-1a流程-组织-放行与告警.md）
- D-1a-165：转交、改派、退回指定节点、代理都写审计（含原因；代理记操作人与被代理人并通知被代理人）（用户决定：intent/2026-10-14-124-1a流程-审批-流程管理员.md）
- D-1a-166：只追加的任务办理记录（含自动同意、加签建议、转交、代理）；审计条目不替代它（用户决定：intent/2026-10-14-130-1a流程-审批-知会历史投递.md）
- D-1a-167：审批发起、撤回、撤销、办结写审计（办理明细另在任务办理记录）（用户决定：intent/2026-10-14-154-审计记哪些动作-逐项勾选-必记清.md）
- D-1a-168：离职者在途待办重新取人、复职重新授予基础角色、应急账号登记 / 启停 / 重置都写审计（用户决定：intent/2026-10-14-107-1a流程-组织-离职者在途待办.md）
- D-1a-169：编号规则修改、确认、停用、启用写审计（前后值）并登记 CONFIG_CHANGELOG（用户决定：intent/2026-10-14-151-业务编号日期-审计与下游-逐项勾.md）
- D-1a-170：审计条目增加追踪号、来源 IP、变更前后值、原因四列；详情不得含密钥与个人敏感信息并加守护（L3 / L4 不进审计详情，ADR-0026 §7）（用户决定：intent/2026-10-14-155-审计字段与可靠性-逐项勾选-增加.md）
- D-1a-171：可按操作人、被代理人、来源 IP、结果、动作前缀、对象、时间查（用户决定：intent/2026-10-14-155-审计字段与可靠性-逐项勾选-增加.md）
- D-1a-172：审计查询与导出为两个权限，归「安全审计员」角色（授予公司审计人员；业务 Admin 如需另按敏感权限双人确认授予）；员工可查自己的登录记录；查询与导出本身写审计；1a 提供审计查询页（用户决定：intent/2026-10-14-152-问-审计谁能查与导出-独立权限+.md；intent/2026-10-14-158-问-审计查看-强制下线与-ADR.md）
- D-1a-173：新审计至少保留 5 年，1a 不做到期删除；到期归档方式与最终保留期在老系统下线前与老日志一并按公司审计要求定（用户决定：intent/2026-10-14-153-问-新审计保留多久-至少-5-年.md；intent/2026-10-14-122-1a流程-登录-历史登录日志.md）
- D-1a-174：规格列出必须写审计的动作清单（登录、会话、权限与角色、代理、放行、法人、编号规则、审批改派 / 转交 / 撤销、应急账号、配置变更等），并加架构守护防止漏记（用户决定：intent/2026-10-14-154-审计记哪些动作-逐项勾选-必记清.md）
- D-1a-175：审计与动作同事务或独立事务同步写；事件监听中写的审计与对应动作在同一事务（离职吊销、放行后重跑同步）（用户决定：intent/2026-10-14-155-审计字段与可靠性-逐项勾选-增加.md）
- D-1a-176：审计表的只追加写入守护清单（架构守护 + 数据库触发器），更正对 G34 的引用（用户决定：intent/2026-10-14-155-审计字段与可靠性-逐项勾选-增加.md）
- D-1a-177：不写业务库，改为结构化日志进 SLS（含追踪号）（用户决定：ADR-0019）
- D-1a-178：审计不设法人列；法人生效与数据范围授予在 1b 定（用户决定：intent/2026-10-14-123-1a流程-登录-其余建议.md）

#### Phase 1b

待流程梳理

#### Phase 2

待流程梳理

#### Phase 3

待流程梳理

#### Phase 4

待流程梳理

#### Phase 5

待流程梳理

## 6. Validation Strategy

| 阶段 | 特征测试（老系统录制） | 契约测试 | 双跑比对 | 属性测试 | 人工 UAT |
|---|---|---|---|---|---|
| 1a 平台桥接 | ✔ 回放生产 TIMESHEET 实例的审批人序列 | ✔ 新系统不访问老令牌表（守护测试）；组织快照与 A8 对账 | ✔ 待办数量比对 | — | ✔ 登录冒烟 |
| 1b 报价 | ✔ 用 dev 库回放保存、提交、审批 | ✔ 反向同步写入老表的结构；OpenAPI 契约 | ✔ 金额、毛利、条款拆分 | ✔ 条款合计 = 最终报价；税率边界 | ✔ 业务 Admin |
| 2 交付 + 工时 | ✔ 预算、修订、ETC、交付物、收入 | ✔ 任务包同步、收入事实 `events` | ✔ 预算毛利、CEO 路由、收入额度 | ✔ 收入额度不超过条款金额 | ✔ PM 抽样 |
| 2（工时部分） | ✔ 填报和行级审批状态机 | ✔ 工时事实同步；过账候选结构 | ✔ 影子计算，逐行比对一个完整期间，期间边界一次性切换 | ✔ 每日 7.5 / 15 小时边界、人天合计；事实只追加 | ✔ PM 与部门抽样 |
| 3 差旅 | ✔ 申请到月结 | ✔ 分贝通沙箱契约 | ✔ 一个完整月度周期 | — | ✔ 财务 |
| 4 成本、收入与核算 | ✔ 最近两个已结期间的黄金基线 | ✔ U8C 凭证结构 | ✔ 两个月结期间并行计算，凭证只出草稿 | ✔ 分摊守恒（各池合计 = 池额）、凭证借贷平衡 | ✔ 财务签字 |
| 5 下线 | — | ✔ reporting 视图与 BI 查询 | ✔ 报表对数 | — | ✔ BI 使用方 |

通用要求：
- 每个阶段的 Modulith `ApplicationModules.verify()` 和 ArchUnit 守护 G1–G36、依赖图 D1、DDL D2–D3（深模块、窄接口、分层、命名、禁止项、事实只追加，见 `NAMING_STANDARD.md` §4 与 ADR-0016）；前端 F1–F14进入 CI，违规即失败。
- Playwright E2E 套件（仓库 `e2e/`）作为端到端回归。

## 7. Open Questions

Phase 1a 开始前需要决定的事项（逐项勾选）：

- [x] **代码仓库**：新建 `forcitis-one-psa`（用户 2026-10-14 决定）
- [x] **SEC-001 的止血方式**：先网络层止血（收窄 48088 安全组、nginx 拒绝免密端点），签名补丁在 dev 实测通过并与 A8、YD、BI 协调后再部署（用户 2026-10-14 决定）
- [x] **SEC-003 的角色授权**：按已有页面权限自动授权（迁移脚本已实现）（用户 2026-10-14 决定）
- [x] **前端栈**：PC 采用 React 19 + TS + Vite + Ant Design 5 + TanStack Query + Zod，与 H5 统一为 React（ADR-0013，用户 2026-10-14 决定：「前端就用 React」）
- [x] **设计正本**：「Forcitis One Design System」（用户 2026-10-14 决定）；「FORCITIS one（Storm）」只作交互参考
- [x] **权责发生制主干**：已采纳 ADR-0016（用户 2026-10-14）（单一过账管道、事实只追加、(期间, 法人) 期间闸门、新增 `accounting.period`、U8C 本期仍为法定账）
- [x] **与边界蓝图的关系**：已采纳 ADR-0017（用户 2026-10-14）（模块沿用老系统子域划分并登记蓝图 BC 编号；蓝图的财务不变量作为硬约束；技术栈按本项目评估确定、不沿用 DD-8；D11 / D12 / DD-6 不在本 brief 范围）
- [x] **SEC-107**：立即删除生产 nginx 的 `/other-api` location（由运维执行后 reload）（用户 2026-10-14 决定）
- [x] **用语确认**：2026-10-14 业务 Admin 在用语确认页确认全部 197 项（A 组 4 项为方言、B 组 59 项同意改为标准用语、C 组 134 项确认为正式用语），`terms.yaml` 293 条全部已批准（ADR-0018）
- [x] **BC slug**：批准 GLOSSARY §1 的现有 slug，含新增 `accounting.period`（用户 2026-10-14 决定）
- [x] **数据库版本**：PostgreSQL 18（用户 2026-10-14 决定：「就用 PG 18」）
- [x] **数据迁移工具**：自建 ETL（JdbcClient 编写、可重跑、可对账，承载逐列所有权映射、翻译表与冲销关系重建），不依赖 DTS（用户 2026-10-14 决定）
- [x] **脚手架退出**：已采纳 ADR-0019（用户 2026-10-14）
- [x] **短信**：不迁移，审批通知统一走企业微信（用户 2026-10-14 决定；生产核实短信从未真正发出）
- [x] **会话管理**：采纳 ADR-0020；`analysis/psa/`（不含任何凭据）移入 `forcitis-one-psa/docs/modernization/`，每个会话收尾时提交；首次提交与推送前列出范围待用户确认（用户 2026-10-14 决定）
- [x] **基础设施**：杭州独立 ECS（4 核 16G）部署新单体；RDS PostgreSQL 18 高可用版（2 核 8G 起）；可观测性接 ARMS 与 SLS（用户 2026-10-14 决定）；开通由运维执行
- [x] **规则评审**：按阶段分批——Phase 1a 前评审审批 28 条，1b 前评审售前报价 14 条，其余在对应阶段前评审，每批一个会话（用户 2026-10-14 决定）
- [x] **业务编号**：**统一新格式**（用户 2026-10-14 决定）。默认模板提议 `{单据类型代码}-{法人代码}-{yyyyMM}-{流水 4 位}`（例如 `QT-FST-202610-0001`），按法人与月份计数、不重号不跳号；老编号作为「原编号」只读保留并可检索。具体模板在 Phase 1a 由管理员在编号规则页确认
- [x] **BI 负责人**：业务 Admin 都业梁（用户 2026-10-14 决定）

## 8. Approval Block

```
Approved by: 用户（业务侧，会话内明确批准：「待决问题给我建议选项，批准」，审批范围选「批准 Phase 1a 和 1b」）  Date: 2026-10-14
Approval covers: Phase 1 only（Phase 1a 与 1b；试点后按结果重新生成 brief，再审批后续阶段）
```

审批人通过编辑本文件来调控执行：被修改的入口 / 出口条件会被执行命令遵守，聊天消息不会。执行命令只会勾选条件或追加 `Proposed revision:` 行，不会改写条件。
