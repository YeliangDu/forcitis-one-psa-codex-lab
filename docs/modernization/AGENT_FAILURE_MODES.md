# Agent 失败模式 — 针对本仓库

日期：2026-10-14

用户问题原话：「agent 还会范哪些错误？针对这个仓库？」

**依据**：老系统大部分代码由 AI 生成（仓库 psa-review 技能的原话是「多为 AI 生成」）。下面每一种失败模式都在本仓库里**实际出现过**：证据来自本次的安全审计（SEC-xxx）、技术债分析、315 条规则卡中的「疑似缺陷」以及生产数据。每一种都给出 FORCITIS one 中的守护措施。「已实现」表示在 `architecture-guardrails/` 模板中实测拦截过；「计划」表示在 Phase 1 骨架中补齐。

| # | 失败模式 | 本仓库证据 | 守护 | 状态 |
|---|---|---|---|---|
| 1 | **临时绕过不回收**：为了调通先放开权限，写上 TODO 后就忘了 | `CostOpeningController` 39 个 `@PermitAll // TODO cost:opening:*`；`CostCorrectionController` 的注释写着「提 PR 前必须还原」，但没有还原（SEC-003） | G28：每个接口必须有 `@PreAuthorize` 或带理由的 `@PublicEndpoint`；G18：禁用 `@PermitAll`；CI 禁止没有 Issue 号的 TODO | 已实现（TODO 检查：计划） |
| 2 | **调试 / 修数代码上了生产** | `Fix*Controller` 约 25 个匿名 GET 接口会改写工时（SEC-004）；`SampleOrderController`、`TestDemoController`、`/a8/test` 会触发 U8C 推送 | G26：主代码禁止 `Fix*`、`Demo*`、`Mock*`、`Test*`、`Sample*`、`*Example*`；修数一律走 Flyway 脚本或运维作业 | 已实现 |
| 3 | **事务自调用失效** | `WipServiceImpl.wipCarryForward` 没有事务，又通过 `this.` 调用 `@Transactional` 方法，导致结转状态卡在「结转中」 | G27：用例类只有一个 public 方法；G11：事务只能出现在 application 层（含类级注解、`TransactionTemplate`、`jakarta.transaction`） | 已实现 |
| 4 | **静默默认值和吞异常** | 65 个空 catch、72 个 catch 后直接返回 null；MQ 生产者发送失败时静默丢弃；模板初版里的 `GetRemainingHours` 查不到就返回 0（被评审抓到） | 查询结果用 `Optional`，业务结果用 sealed 类型并由调用方穷尽处理；G18：禁 `System.out`；Error Prone / Checkstyle 禁空 catch | 已实现（Checkstyle：计划） |
| 5 | **注释和文档与代码漂移** | 规则卡中大量「文档说 X、代码做 Y」：条款合计「≤」与「=」不一致、DLV-R18 收入确认前置条件、工时 09:00 是开放时间还是截止时间、`expense/README` 描述的目录结构与实际不符 | 行为以规则卡加测试为准（测试名带 `RULE_NNN`）；数据字典和 OpenAPI 从代码生成；决策写进 ADR | 已实现（数据字典）；规则测试命名：计划 |
| 6 | **同一常量 / 状态多处定义且数值不一致** | 审批结果码在 BPM 和 fst 中数值互换（2 / 3）；7.5 小时在两个常量里各定义一份；27 个状态值是雪花 ID；项目状态 ID 在前端、SQL、后端各抄一份 | 状态只用枚举，并按名称存库（G16 + DDL 的 `CHECK`）；业务参数只有一个来源；值对象 | 已实现 |
| 7 | **新旧两套实现并存，老的不下线** | 老 `/fst/accounting/wip` 结转路径与新的结转引擎同时对外开放；老 WBS 与新预算并存；fst 里复制了 system 的 producer，却从未被引用 | 生产 180 天零调用的接口清单（34 个 Controller）作为剪枝依据；每个阶段的出口条件要求下线老路径 | 计划（写入 brief 出口条件） |
| 8 | **功能只做了一半**：定义了状态但没有写入方，规则写了却不执行 | `DLV_CONVERTED`、`REVERSED` 没有任何代码写入；`AccountingPeriodStatusEnum.transition()` 从未被调用；业务检测里标为「BLOCK」的检查实际上不阻断任何操作 | 每个状态枚举都要有覆盖其全部转移的测试；「阻断」类检查必须接入用例的前置条件，并有测试证明确实拦得住 | 计划 |
| 9 | **先查再写的竞态** | 发起轮次用 `selectMax` 后再 insert，没有唯一约束；报价保存不带 CAS，可能覆盖已提交的报价；批量提交返回已结束的旧实例 | 唯一约束 + 乐观锁 `version`；编号服务用原子 `INSERT … ON CONFLICT`；作业租约用唯一键 | 已实现（编号服务）；`version` 规则：计划 |
| 10 | **时间取值漂移** | 报价和执行成本按「今天」取费率，没有用业务日期；资源预测窗口从「明天」开始 | G25：业务代码禁止直接调用 `now()`，必须注入 `Clock` | 已实现 |
| 11 | **信任客户端传来的身份** | `creator` 由请求体指定（SEC-005）；各模块信任 `login-user` 请求头（SEC-020）；`paramsId` 越权（SEC-104）；企业微信回调信任 `EventKey`（SEC-006） | 身份只能来自 `CurrentUser`（会话）；G33：请求 record 中不得出现身份字段；只有 identity 模块能配置安全（G33） | 已实现 |
| 12 | **硬编码凭据、IP、个人 ID 兜底** | 企业微信 secret、KM key、TLS 私钥入库（SEC-006 / 012）；审批人缺失时兜底给某位 IT 管理员的个人 ID；生产 CORS 写死裸 IP | gitleaks 扫描；Checkstyle 禁止 15 位以上的数字字面量和 IP 字面量；配置走 KMS | 计划 |
| 13 | **前端手写 URL、渲染 HTML、内联样式** | 69 个 URL 解析不到后端；`v-html` 存储型 XSS（SEC-008）；`costCarryPosting.mock.js` 中有 `USE_MOCK` 开关 | F5 / F10：只能使用生成的客户端；禁 `dangerouslySetInnerHTML`；F14：禁内联样式、硬编码颜色、直接 import antd；禁止 `*.mock.*` 进入 src | 已实现（mock 文件：计划） |
| 14 | **越界读写别人的表** | fst 直接映射 system 和 bpm 的表；bpm 按表名回写业务表；fst 的 Flyway 替 bpm 建表 | 一个 BC 一个 schema；DDL 测试校验「只能在自己的 schema 建表」；SQL 只能访问本模块 schema | 已实现（DDL）；SQL 扫描：计划 |
| 15 | **守护规则太窄，被钻空子** | 老的 ArchUnit 只检查新分层之间的依赖，没有拦住 100 个「新层 import 老层」的文件；agent 满足了规则的字面要求 | 每条守护都有负向测试（故意违规，必须失败）；依赖图和接口预算改动需要评审；评审时检查规则漏洞（已据此补强 G4、G11、G33） | 已实现 |
| 16 | **为了让 CI 通过而排除测试** | bpm 模块的测试因长期失败被排除在 CI 之外 | CI 检查：surefire 不允许配置排除项；`@Disabled` 必须带 Issue 号 | 计划 |
| 17 | **内存分页和全表扫描** | 待办列表在内存中合并分页；修数接口全表扫描；资源负荷按人循环查询 | 列表必须用数据库分页；web 路径禁止 `findAll`；关键查询有性能基线测试 | 计划 |
| 18 | **结果不确定** | 单审批人节点从候选人中随机取一个；从 HashSet 里取「第一个」审批人 | 领域层和应用层禁止使用 `Random`；返回有序集合；审批人解析由 DMN 决策表给出确定结果 | 计划 |
| 19 | **拼写错误和命名混乱** | `costopenning`、`socail`、`biget`、`cuvalue`；工时有三套叫法 | 词表唯一词源；G14 / G15；F6（kebab-case 文件名、禁用空泛后缀）；DDL 禁用词表 | 已实现 |
| 20 | **旁路入口绕过校验** | 成本修正的 Excel 导入可以直接设置 `flag_exempt`，绕过异常工作台的所有校验和审批 | 导入只能调用与界面相同的用例，不能直接写表；导入测试要覆盖规则 | 计划 |

## 使用方式

- 让 agent 开工前，在模块的 `AGENTS.md` 里引用本表，说明「这些是本仓库已经发生过的错误」。
- 每发现一种新的失败模式，就补一行并加一条守护，守护必须附带负向测试。
- code review 时（包括 psa-review 技能）用本表作为检查单。
