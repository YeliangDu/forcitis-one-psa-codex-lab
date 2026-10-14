# INTENT — psa

日期：2026-10-14

## 目标

**先评估、再定路线和栈**（understand first → 由 assess 推荐 uplift / transform / reimagine 之一）。

用户原话：「使用 code modernization 来现代化本仓，目标参照栈 /Users/yeliang/Projects/FORCITIS/forcitis-one/docs/adr/0001-tech-stack.md，你全面评估后再最终确定栈」

## From → To

- **From**：psa 单仓 —— `psa-backend`（Java 8 / Spring Boot 2.7.7 / Spring Cloud Alibaba 微服务，yudao/ruoyi-vue-pro 底座，MyBatis-Plus，SQL Server 2016，Flowable，Nacos/Redis/RocketMQ/XXL-JOB）、`psa-front-desk-pc`（Vue 2.7 + Element UI 2.15）、`psa-mini`（Taro 4.2 + React 18 H5）。
- **To（参照，未定稿）**：forcitis-one ADR-0001 及其修订 —— Java 25 LTS、Spring Boot 4.1.x、Spring Modulith 2.1.1 模块化单体、PostgreSQL 17（一 BC 一 schema）、Spring Data JDBC、Flyway（模块感知）、Spring MVC REST + OpenAPI 3、Modulith JDBC Event Publication Registry（outbox）、流程引擎推迟（规则型审批，引入时 Flowable 8）、Modulith test + ArchUnit + Testcontainers PG17、Maven。前端栈 ADR 未规定，由评估决定。
- **最终栈**：由 `brief` 在评估之后确定，ADR-0001 是参照而非预设结论。

## 必须保持成立（用户原话）

- 老系统迁移期间持续运行
- 顺手修已知 bug
- 先做安全审查
- 功能复刻但是行为要现代化

## 背景答复（用户原话）

- 范围：「完整系统，外部仅读库/接口」
- 与 forcitis-one 的关系：「forcitis-one 就是 PSA 的接班系统」
- 本轮禁区：「仅分析，不改 psa 仓代码」
- 「forcitis-one仓库代码不能参考」—— 只允许参考其 ADR-0001（技术栈）这一文档；不读取、不复用、不引用 forcitis-one 的源码。

## 待定项

- 前端目标栈（PC 端 Vue 2.7 已 EOL；H5 已是 Taro/React）——由 assess/brief 给建议。
- SQL Server → PostgreSQL 的数据迁移与外部只读消费者（BI）切换方式。

## 评估过程中用户追加的要求（原话，按时间顺序）

| 原话 | 落点 |
|---|---|
| 「运行代码没那么多」「你直接调用 aliyun cli 去生产服访问」 | 生产只读采集 `runtime/`；按实际使用面界定范围 |
| 「是不是888都做对接下来工作更有利？」 | 节点描述只做最大的 40 个（覆盖 63% 代码），回答见会话 |
| 「可以在现代化过程中规范命名吗？将原代码的命名和编码规范化」 | ADR-0005，NAMING_STANDARD.md |
| 「命名要符合英文、行业、规范的习惯，agent 开发友好。而且要考虑 archunit 和包结构来保证目录结构不会让 agent 犯错。」 | ADR-0003、ADR-0004，architecture-guardrails/ |
| 「一些基本的原则：深模块、窄接口，要给 agent 约束，避免错代码」 | ADR-0004 |
| 「前端也要加同样的目录约束和 lint 守护。」 | ADR-0013，architecture-guardrails/frontend（F1–F14） |
| 「flowable 这个阶段要引入」 | ADR-0007 |
| 「api 要有版本号」 | ADR-0008 |
| 「值对象要有防止 agent 出错的措施」 | ADR-0006 |
| 「agent 还会范哪些错误？针对这个仓库？」 | AGENT_FAILURE_MODES.md |
| 「原系统鉴权存在的问题，这次现代化要一并解决」 | ADR-0009 |
| 「要用强类型」 | ADR-0006 |
| 「单测要最大代码覆盖率的最少测试，更大单元集成测试为主，必要时端到端」 | ADR-0012 |
| 「DDD、DAMA、TOGAF、BPMN、DMN、OLTP、OLAP 的核心思想作为现代化过程中的指导思想」 | ADR-0014，GUIDING_PRINCIPLES.md |
| 「统一领域用语和 ADR」 | GLOSSARY.md，adr/ |
| 「one.forcitis.com 是现代化后的 url。产品名 FORCITIS one」 | ADR-0001 |
| 「前端要参考 claude design 中的相应 ds」（授权只读） | ADR-0013：已读取，提议以「Forcitis One Design System」为正本；其附带的《PSA 边界蓝图 v2.3》见 ADR-0017 |
| 「数据字典的字段名要符合前述命名规范，业务编号系统要我可以自定义」 | ADR-0011（DdlNamingTest），ADR-0010（platform.numbering） |
| 「这是一个 AEC 行业项目为基础的专业服务公司，权责发生制为核心的业务财务一体化系统，这个对现代化有用吗」 | ADR-0016，GUIDING_PRINCIPLES.md §9a，BRIEF Phase 2 / 4 入口条件 |
| 「不要参考设计系统的建议，从本项目现代化最合适的角度选用技术栈。前端到底是 react 更合适还是 vue」 | ADR-0013（独立评估比较表，提议 React），ADR-0017 第 3 条 |
| 「前端就用 React」 | ADR-0013 改为已采纳；STACK_DECISION、BRIEF §7 同步 |
| 「PG 需要现在就用 18 吗」 | 实测后提议改为 PG 18：ADR-0002、STACK_DECISION、BRIEF §7 |
| 「就用 PG 18」 | ADR-0002、STACK_DECISION 改为已采纳；BRIEF §7 勾选 |
| 「设计正本就用 Forcitis One Design System」 | ADR-0013、STACK_DECISION 改为已采纳；BRIEF §7 勾选、Phase 1a 入口条件改写 |
| 「设计正本就用 Forcitis One Design System，还不完善在现代化过程中不断完善」 | ADR-0013 第 7 条；DS_GAPS.md；BRIEF 过渡期通用机制 |
| 「授权你往正本写入设计」 | ADR-0013 第 7 条；DS_GAPS.md。按阶段在阶段开始时经 `/design-sync` 增量写入 |
| `/design-sync`：选「代码为源」 | ADR-0013 第 7 条；DS_GAPS.md；BRIEF Phase 1a 入口 / 出口条件。FORCITIS one 仓库建好后执行，本次未写入正本 |
| 「新建仓库 forcitis-one-psa 放新代码」 | ADR-0001 第 5 条；BRIEF §7 勾选、Phase 1a 入口条件 |
| 「放在 FORCITIS 组织下，现在就建」 | 已创建私有空仓库 https://github.com/FORCITIS/forcitis-one-psa |
| 「改造过程中 DDD 的统一语言会自动登记吗？这里的语言要符合中文英文行业、规范标准的习惯」 | ADR-0018；glossary/terms.yaml、STANDARDS_AUDIT.md；守护 G37–G39 |
| 「采纳 ADR-0018」 | ADR-0018 改为已采纳；STACK_DECISION、BRIEF 过渡期通用机制与 §7 同步 |
| 「采纳 ADR-0016 和 ADR-0017」 | 两份 ADR 改为已采纳；STACK_DECISION、BRIEF §7 勾选 |
| 「我们现代化如何处理 ruoyi 脚手架的？」 | ADR-0019（提议）；守护 G40；BRIEF §7 |
| 「采纳 ADR-0019」 | ADR-0019 改为已采纳；G40；STACK_DECISION；BRIEF §7 |
| 「SEC-111 也加内部令牌保护」 | SECURITY_FINDINGS；第 5 轮补丁 |
| 「fst 这个服务名可以改吗」 | ADR-0019：新系统中 fst 消失并列为禁用写法；老系统不改名；分贝通回调列入 Phase 3 入口条件 |
| 「短信不迁移，审批通知走企业微信」 | ADR-0019 改为决定；BRIEF §7 勾选 |
| 「新系统都哪些地方需要同一命名规则，都统一了吗」 | NAMING_STANDARD §7 命名面清单；补守护（实测中） |
| 「收入确认在旧系统是什么方法？」 | 代码 + 生产核实写入 ADR-0016 第 4 条；BRIEF Phase 4 入口条件 |
| 「产值确认单就是收入确认单，系统用收入确认这个语言，现在的叫法可以归为方言」 | terms.yaml（收入确认单 approved，产值确认单 / 产值 为 dialect 并列入禁用）；ADR-0018 新增「方言」规则；ADR-0016、STANDARDS_AUDIT G-12 |
| 「所有跟正式用语不符合的，都归为我们公司内部的方言，新系统一律采用正式用语，不把方言带入新系统，不确定哪些是正式用语，哪些是方言可以登记用语表，我来确认。」 | ADR-0018 方言规则；terms.yaml（dialect / undetermined）；glossary/TERM_CONFIRMATION.md；BRIEF §7 |
| 「用语确认完毕。」 | 用语确认页 197 项全部写回 terms.yaml（293 条已批准）；ADR-0018；BRIEF §7 勾选 |
| 「整个改造过程会话管理很重要，需要明确的方案。」 | ADR-0020；SESSION_PLAYBOOK.md；STATUS.md；SESSION_LOG.md |
| 定栈剩余 9 项（运行时、框架、JdbcClient、事件登记表、作业租约 + ShedLock、Caffeine + KMS、可观测性、独立部署、数据过渡）：「全部定稿」 | STACK_DECISION 全部已采纳；ADR-0002 已采纳 |
| 「待决问题给我建议选项，批准」 | brief 审批范围：批准 Phase 1a 和 1b；SEC-001 先网络层再补丁；SEC-003 按已有页面权限自动授权；立即删除 `/other-api`；批准现有 BC slug；自建 ETL；独立 ECS + RDS PG 18 高可用 + ARMS/SLS；规则按阶段分批评审；业务编号统一新格式；BI 负责人业务 Admin 都业梁；ADR-0020 采纳并把分析产物移入新仓库 |
| 规则评审第一批（审批 28 条）逐条作答：RULE-248「正确」；RULE-249「错误，改成候选组认领」；RULE-250「正确+修，汇报线不完整时往上推一级；一直找不到就转给流程管理员并告警。不能空指针，也不能静默跳过」；RULE-252「正确+修。行级类型照旧。非行级类型不再取 HashSet里的「第一个」，和节点级用同一种解析方式（候选组）」 | RULE_REVIEWS.json / .md；BRIEF §5 审批域勾选 |
| 「建议已经发到 psa-review-approval 会话的收件箱，你读一下，不要一条一条问了」；对评估会话 23 条建议：「整批同意」；RULE-293：「按建议：改为「已退回」」 | RULE_REVIEWS.json / .md（RULE-253..311）；BRIEF §5 审批域勾选 |
| 规则评审第二批（售前报价 14 条）逐条作答：RULE-139 折扣「不需要 (Recommended)」；RULE-141 汇总「仅参考+审批提示 (Recommended)」；RULE-147 费率日「提交时锁定 (Recommended)」；RULE-155 审批改价「不能，需退回 (Recommended)」；其余 10 条（140、142、144、145、158、159、168、169、172、173）及随之修订 RULE-160 契约：「整批同意 (Recommended)」 | RULE_REVIEWS.json / .md（RULE-139..173、RULE-160）；BRIEF §5 售前报价域与 Phase 1b 入口条件勾选 |
| Phase 1a reimagine 检查点 1（能力 P0 与舍弃，2026-10-14）：「按建议批准」 | AI_NATIVE_SPEC.md §1、§7（C1a-11 与卡片按钮操作为 P1，其余为 P0；D-01..D-09 舍弃） |
| 规则评审第三批（identity/org 45 条，2026-10-14）逐条作答：RULE-323 小程序「保留 (Recommended)」；RULE-331/332 会话时长「空闲10h+上限24h (Recommended)」；RULE-349 下属「只算直接下属 (Recommended)」；其余条目（询问时写作「其余 42 条（上表除 323 外的条目，349 中下属深度以外的部分）」，实际为除 323 外的全部 44 条）：「整批同意 (Recommended)」 | RULE_REVIEWS.json / .md（RULE-316..376 第三批：正确 16、错误 29）；AI_NATIVE_SPEC §5.2、C1a-01；BRIEF Phase 1a 入口条件勾选 |
