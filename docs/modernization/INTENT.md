# INTENT — FORCITIS one

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
| 第三批「其余」条数复核（2026-10-14，psa-coordinator 询问）：「是，44 条都同意」 | RULE_REVIEWS.json（RULE-316..376 中除 323 外的 44 条） |
| Phase 1a reimagine 检查点 2（架构，2026-10-14）：Q-1「1a 删除桥接 (Recommended)」；Q-2「两条都按建议修订 (Recommended)」；Q-3「收窄；通知发候选组全员 (Recommended)」；Q-4「四项都同意 (Recommended)」 | REIMAGINED_ARCHITECTURE.md §7（已批准）；ADR-0009 §6 修订；brief Phase 1a 出口条件两条 Proposed revision；AI_NATIVE_SPEC C1a-06 删除 |
| 「确保权责业务事实全部在新系统」（2026-10-14，对 OA 流程盘点的要求） | GUIDING_PRINCIPLES §9a「权责事实全部在新系统」；OA_PROCESS_INVENTORY 分类与权责事实完整性矩阵；ADR-0021 |
| 总账纳入新系统（2026-10-14，经评估会话转达，psa-coordinator 当面复核「是，4 项都是我的决定」）：「我要财务总账集成到新系统，通过新系统直接可以出权责口径的三大报表」；路线「两步：先管理口径，再年度切换法定账（推荐）」；PSA 以外的凭证来源「先导入 / 手工凭证，逐步自建（推荐）」；现金流量表「直接法为主 + 间接法补充（推荐）」 | ADR-0022（提议）；brief 修订稿（新增总账与报表、法定账切换阶段）；ADR-0016 第 6 条、ADR-0017 第 4 条修订稿 |
| 「可以从我现在U8C 系统蒸馏总账做法」（2026-10-14，经评估会话转达） | U8C_GL_PRACTICE.md（只读蒸馏结构、规则、公式与计数），作为 ADR-0022 依据 |
| 安全补丁第 7 轮后（2026-10-14，psa-sec-hotfix 会话）：「老系统先不管，确保新系统没有这些缺陷」 | 老系统安全止血搁置（STATUS）；phase1a/SECURITY_INVARIANTS.md I-1..I-9；老系统补丁与止血方案保留在 security_remediation.* 备查，不执行 |
| 同上，问「老系统不处理的前提下，新系统如何对待老系统的登录令牌？」：「取消桥接 (Recommended)」（与检查点 2 Q-1 一致） | ADR-0009 §6 补充：老系统后端在免密入口修复前不得作为签名票据签发方；SECURITY_INVARIANTS I-1、I-2 |
| psa-coordinator 汇总决定（2026-10-14）：H-1 产值确认单「迁入新系统，不设双源期 (Recommended)」；H-3 人工成本「新系统要自建整个hrm，算薪要迁入」；M-5 客商 / 部门映射 / 银行卡「主数据全部进新系统」；1a 修订 M-4 §9a 证明项与 brief §2 integration-acl 行「两条都同意 (Recommended)」 | ADR-0016、brief 中 A8 产值只读接入的表述改为迁入（修订稿）；ADR-0021 薪酬改为迁入并自建 HRM；客商、部门映射、员工银行卡等主数据归新系统（银行卡须按敏感个人信息加密存储与最小授权）；brief Phase 1a 出口与 §2 两条 Proposed revision |
| 老系统 SEC-116（短信验证码固定 9999）经 psa-coordinator 提示生产风险后：「仍然完全不处理」 | 老系统不做任何止血；风险由用户承担；新系统按 SECURITY_INVARIANTS I-1..I-9 隔离 |
| OA 盘点：ADR-0021「采纳；6a–6c 并入 Phase 4 前 (Recommended)」；应付与付款「新建「应付与付款」BC (Recommended)」；非项目采购、员工借还款、合伙人注资退回「都算，都进新系统 (Recommended)」 | ADR-0021 已采纳；6a–6c 为 Phase 4 出口前置；新建应付与付款 BC（蓝图编号由 psa-gl-scope 统一）；三类事实列入权责事实矩阵 |
| 「现代化后，我想完善其他子域和 BC，想把 OA 上的业务过程迁到新系统，应该怎么做？」（2026-10-14，经 psa-coordinator 派给 psa-oa-inventory） | OA_PROCESS_INVENTORY.md；ADR-0021（提议） |
| 「是，同意只读查询」（2026-10-14，psa-coordinator 当面确认，用于 OA 流程盘点的生产只读查询） | runtime/a8_*.tsv、u8c_voucher_sources.tsv（只取元数据与计数）；OA_PROCESS_INVENTORY.md §1 |
| main 分支保护「只要求 CI 三项通过 (Recommended)」；用语确认「开一个会话集中确认 (Recommended)」（2026-10-14，psa-coordinator 询问） | main 已设保护：必须 backend / frontend / gitleaks 三项通过，管理员同样受限，禁止强推与删除，不要求人工批准；新会话 psa-terms-review 集中确认 58 条提议术语与 OA 20 项叫法 |
| 给 psa-gl-scope 补充（2026-10-14）：「U8C 除了蒸馏，还要①导出最近两个已结期间的科目余额表和三大报表作为黄金基线（只放本机）；②把结转模板、报表公式、凭证科目映射整理成规则交财务评审；③检查 U8C 有无二次开发或插件；④统计科目和辅助核算的实际使用面。」 | 基线存 ~/.modernize/psa/u8c-baseline/（仓库外，仓库只记清单与校验和）；GL 规则卡交财务评审；U8C 二开 / 插件清单；科目与辅助核算使用面统计（U8C_GL_PRACTICE.md） |
| 给 psa-gl-scope 补充（二，2026-10-14）：「盘点 U8C 实际启用的模块、功能和近一年使用量，以及角色权限分工；把凭证流转、账簿查询、出纳与银行对账、往来核销、年结与期初、打印与会计档案、审计轨迹、GB/T 24589 数据接口逐项归类（第一步需要 / 第二步前需要 / 不迁移 / 留外部）；按《会计软件基本功能和服务规范》列出合规清单。」 | U8C 模块与使用量、角色权限分工、能力归类表、合规清单（U8C_GL_PRACTICE.md 或拆分文件）；法定账切换阶段入口「按规范逐条验收」的依据 |
| 关于 IT 服务门户的 RDS（2026-10-14）：「这个就是你需要的 RDS，上面的 it 将来纳入相应的 BC，目前是测试开发用。」 | Phase 1a 入口「PSA 专用 RDS PostgreSQL 实例」由 pgm-bp13vak0sew3m003（PG 18 高可用，库 forcitis_one）承担，当前作测试开发环境；IT 服务门户（forcitis-it-service-portal）将来纳入相应 BC（待蓝图归属） |
| U8C 9345 XChange 接口（公网、无认证）日志中有 2026-07 SQL 注入探测记录，psa-coordinator 询问处理方式：「和老 PSA 一样先不管」（2026-10-14） | 不登记 SEC 项、不核查暴露面；仅在 U8C_GL_PRACTICE.md 对接一节记录；新系统对 U8C 的接口须自带认证且不暴露公网（ADR-0022 写入） |
| 「RDS 已经开通自动续费」（2026-10-14） | STATUS 去掉 RDS 到期续费提醒 |
| 用语确认第二轮：「开一个会话集中确认 (Recommended)」（经 psa-coordinator 派给 psa-terms-review）；在用语确认页上 P 组 58 项、N 组 2 项全部「同意建议」，A 组 20 项全部「方言」，并告知「用语已经确认」；A5 项目委外「两种都有，按单据区分」；A11 应收坏账申请「计提」（2026-10-14） | 58 条模块提议术语转正式（55 条入 terms.yaml，budget / progress-recognition / audit_trail 并入已有正式用语）；新 BC 中英文名：考勤与休假 `organization.attendance`（「假勤」归方言）、应付与付款 `payables.payment`；项目委外按单据分别对应分包成本 / 外包成本；应收坏账申请对应坏账准备计提（6b 起草词条）。记录：glossary/round2-proposals.yaml、decisions-round2-2026-10-14.json |
| 阿里云盘点后的建议（2026-10-14）：「安全项你不管，新开专用 ECS」；规格「用 g8i.xlarge，购买 (Recommended)」；因 G 区无库存改 K 区后「确认，按此下单」 | 账号安全项（测试机告警、RDS 公网白名单、临时开放端口、MFA）由用户自行处理，调度不派工；已购 FORCITIS one 专用 ECS i-bp122xlpar8w2uylmal2（订单 2005905008640288，5,448.10 元/年，自动续费）；RDS 新增白名单分组 forcitis_one |
| 新 ECS 云侧加固（2026-10-14）：选「元数据强制加固 + 释放保护」「自动快照策略」「云安全中心客户端与授权」，未选数据盘加密 | 元数据 HttpTokens=required；释放保护不适用包年实例（包年到期前不可释放）；快照策略 sp-bp1b4n9pvexztyhyfrof（每日 03:00，保留 7 天，两块盘）；云安全中心客户端经 OperateAgentClientInstall 安装并绑定授权；数据盘不加密 |
| OLAP 范围（2026-10-14）：「现代化负责 reporting 数据层、KPI 语义层（按 OAT）和 BI 过渡不中断；BI 工具和看板由 BI 项目负责。请派一个会话 psa-reporting-contract：盘点现有 BI 迁移会话正在建的模型和数据源，与 terms.yaml / OAT 对齐，设计 reporting 层的契约和过渡视图，产出 ADR-0023（提议）。」 | 工作单元「reporting 契约」（psa-reporting-contract）；ADR-0023（提议）；GUIDING_PRINCIPLES §8 的边界以此为准 |
| 经营分析（2026-10-14，覆盖上一条 OLAP 边界）：「BI 要重构，不满足经营管理需要。经营分析纳入现代化：经营子域 4 个 BC、reporting 与语义层纳入，BI 重构同步规划。暂停 BI 迁移会话在老数据源上的新建模，现有报表照常运行。请派 psa-bi-scope 做经营分析范围评估，产出 ADR-0023（提议）。」 | 已开会话由 psa-reporting-contract 改名为 psa-bi-scope 并扩大范围；BI 会话暂停由用户自行处理；ADR-0023（提议） |
| 「G46 为什么新系统要放 A8 这类旧系统的名称？」；1a 会话询问防腐层位置时选「改名并移到 integration」（2026-10-14） | 领域模块不出现外部系统名（LegacyShapeTest）；A8 视图读取、致远验票移入 platform.integration（REIMAGINED_ARCHITECTURE §2 修订，修订检查点 2 已批准的「防腐层下沉到使用模块」）；G46 不放宽，去掉 Binder 绕开 |
| Phase 1a M3「退回即结束本轮，重提开新一轮 (Recommended)」；D-1/D-2「同实例分库，生产前再定 (Recommended)」；D-3/D-4/D-5「可以」（部署前再问）；M1 先看剩余 7 种指派类型说明再定（2026-10-14，psa-coordinator 当面询问） | RULE_REVIEWS「实现方式变更」；phase1a/infra（forcitis_one_dev、one_dev_* 角色）；审批模块退回改为按轮次 |
| 「新系统，为什么会采用旧写法？需要禁止 agent 使用旧代码？」→ 禁止照搬老代码三项「三项都加 (Recommended)」（2026-10-14） | SESSION_PLAYBOOK §5「老代码只作证据，不得照搬」；LegacyShapeTest 守护；每波 opus「老系统形状」复审 |
| psa-gl-scope 访问 U8C（2026-10-14，本会话当面）：访问方式「经 PSA 服务器跨库只读（推荐）」；范围「两个都蒸馏（推荐）」 | U8C_GL_PRACTICE.md §0；只执行 SELECT、连接只读、凭据只在服务器进程内使用 |
| U8C 黄金基线（用户经 psa-coordinator 下达：「U8C 除了蒸馏，还要①导出最近两个已结期间的科目余额表和三大报表作为黄金基线（只放本机）；②把结转模板、报表公式、凭证科目映射整理成规则交财务评审；③检查 U8C 有无二次开发或插件；④统计科目和辅助核算的实际使用面。」）；本会话当面确认导出金额：「同意，按上述方式导出（推荐）」（各法人取各自最近两个已结期间；只放本机 `~/.modernize/psa/u8c-baseline/`，权限 700，不进 git；往来辅助核算只留编码） | U8C_GL_PRACTICE.md 黄金基线一节（只记清单、行数、SHA-256）；GL 规则卡；二开与插件清单；使用面统计 |
| U8C 功能盘点（用户经 psa-coordinator 下达：「盘点 U8C 实际启用的模块、功能和近一年使用量，以及角色权限分工；把凭证流转、账簿查询、出纳与银行对账、往来核销、年结与期初、打印与会计档案、审计轨迹、GB/T 24589 数据接口逐项归类（第一步需要 / 第二步前需要 / 不迁移 / 留外部）；按《会计软件基本功能和服务规范》列出合规清单。」） | U8C_CAPABILITY_MAP.md、GL_COMPLIANCE_CHECKLIST.md；ADR-0022 与阶段草案引用 |
| 法定账与收付账套（2026-10-14，psa-gl-scope 当面）：「法定账是收付账套，收付账套保留」 | ADR-0022 第 1、2、8 条；proposals/GL_SCOPE_REVISIONS.md §A、§D 第 1–2 项；rules/GL_FINANCE_REVIEW.md GL-109 |
| 收付账套保留的含义（2026-10-14，psa-gl-scope 当面追问）：「新系统内两种口径都出（推荐）」；U8C 权责账套「G1 起由新系统取代（推荐）」 | ADR-0022 第 2、8、9 条；GL_SCOPE_REVISIONS §A、§C、§D |
| 总账财务评审（2026-10-14，psa-gl-finance-review 弹窗逐项作答，共 28 项）：D17-0、D17-1、D17-2/3、D17-9、D17-4、D17-5、D17-6、D17-8、D17-12、D17-10/11、GL-005、GL-042、GL-057、GL-083、GL-123、GL-004、GL-116、GL-027、GL-044、GL-024、GL-051 及 D17-3 终止清算：「同意建议 (Recommended)」；D17-7：「两账都记合同负债 (Recommended)」；GL-010：「同意：1 月起步 (Recommended)」；GL-030：「全部费用化 (Recommended)」；GL-026：「同意建议，风险点问审计 (Recommended)」；剩余财务项处理方式：「我现在逐项回答」 | rules/GL_FINANCE_DECISIONS.json / .md；受影响规则卡（GL_RULES.md）；GL_FINANCE_REVIEW.md 第一节；GL_SCOPE_REVISIONS §D 第 3、17 项与 §E |
| 科目表方案（2026-10-14，psa-gl-finance-review，未采纳推荐的「共用一张科目表」）：「两账各一张科目表」；追问后：「权责新编一套」；「确认，修订 ADR-0022」 | GL_FINANCE_DECISIONS D3；GL_SCOPE_REVISIONS §E-1（ADR-0022 第 2 条修订提议，ADR 状态不变） |
| 经营分析评估待决（2026-10-14，psa-coordinator 询问）：BI 仓库 10-10 记录的「BI 重构完成并替换现有 BI：2026-12-31」「A8 财务相关部分 2026-12-31 前替代」「先迁 OLAP，再迁 OLTP」→「已被 10-14 的决定取代 (Recommended)」；BI 工具 →「先不定，由 R0 出对比后再定 (Recommended)」；Q3–Q12 →「开会话逐项确认 (Recommended)」 | 以现代化路线为准，日期在统一编排时重估；老 BI 保持运行；R0 产出工具对比；新会话 psa-bi-decisions 逐项确认 Q3–Q12 |
| Phase 1a 第一波复审待决（2026-10-14，psa-coordinator 询问）：M3 退回「退回即结束本轮，重提开新一轮 (Recommended)」；D-1/D-2 环境「同实例分库，生产前再定 (Recommended)」；D-3/D-4/D-5「可以」（部署前再问）；M1 先「展示剩下的 7个规则」，看后「按建议：保留 5 种、推迟 3 种、不建 2 种 (Recommended)」 | 退回按轮次机制；dev 库 forcitis_one_dev 同实例；指派类型保留 ROLE、INITIATOR_LEADER 与 TIMESHEET 用的 3 种，推迟 REGIONAL_RECEIVABLES_MANAGER、DEPARTMENT_DIRECTOR、FORM_FIELD_USER_LEADERS，不建 NAMED_USERS、PROCESS_VARIABLE_USERS |
| 经营分析待决 Q3–Q12（2026-10-14，psa-bi-decisions 当面逐项确认）：Q3 BI-olap「冻结作设计输入 (Recommended)」；Q4「按建议划分，两者都建 (Recommended)」；Q5 OAT「复制进本仓库，policy 治理 (Recommended)」；Q12「需要，R0 列清单 (Recommended)」；Q7「过渡镜像回填 BI 库 (Recommended)」；Q8「同意，随阶段迁入并停用 (Recommended)」；Q9「不例外，先不管 (Recommended)」；Q10「按生效期保留历史 (Recommended)」；Q6-1 收入「不含税为准，含税另列 (Recommended)」；Q6-2 毛利「统一用 CM 及其变体 (Recommended)」；Q6 其余五项（费用两指标、逾期定义、理论工时参数化、人均除数、应收单一口径后老页面数变）「整批同意 (Recommended)」；Q11「同意，放在 R0 做 (Recommended)」 | BI_SCOPE.md §6.1（落实、待财务 F1–F5、待业务 B1–B5）；ADR-0023 证据引用；STATUS |
| 「批准 ADR-0023」（2026-10-14，psa-bi-decisions 当面） | ADR-0023 状态改为已采纳（经 build_adrs.py 生成）；ADR-0023 中原「待用户决定」各处改为引用 Q1–Q3、Q7 的决定；BI_SCOPE.md 头部；STATUS |
| Phase 1a 第二波 kernel + numbering 待决（2026-10-14，psa-coordinator 询问）：法人代码「新系统维护法人小表 (Recommended)」；ProjectCoordinate「先用最小形态，Phase 2 再定 (Recommended)」；原编号检索「单独设 legacy-number:read (Recommended)」 | 法人主数据由新系统管理员维护，{LE} 渲染法人代码；ProjectCoordinate 保持项目 + 可选 WBS；新增权限 legacy-number:read |
| 「把变更治理写进 SESSION_PLAYBOOK.md 和 adr/README.md：1. 已采纳的 ADR 不改正文，改决定就新增 ADR，标明「取代 ADR-00xx」，旧 ADR 改为已被取代、正文保留。2. 新 ADR 必须写影响面、迁移方案、回滚方案，并同步修改对应守护规则。3. 规则调整分三类：配置类（DMN、编号、口径、政策参数）按版本和生效日期生效、不追溯；代码类先改规则评审再改测试和实现，Flyway 只追加；ADR 类走架构评审。4. 已过账事实不改，只能冲销重做。请派一个小的文档会话完成，本地提交后报告。」（2026-10-14，经 psa-coordinator 派给 psa-change-governance） | SESSION_PLAYBOOK §12（含 §1、§4、§11 交叉引用）；adr/README.md「ADR 生命周期与变更规则」；adr/build_adrs.py 状态 / 必备小节 / 取代校验；CONFIG_CHANGELOG.md |
| 变更治理后续（2026-10-14，psa-coordinator 当面询问）：历史偏差「只补必要的，其余承认既成事实 (Recommended)」（用户在本会话中复述：「按建议：补开 ADR-0024，其余维持现状」）；部分取代写法「认可 (Recommended)」；ADR-0022 必备小节「要，补齐后再拿来审批」 | ADR-0024（提议，部分取代 ADR-0016 后果第 4 条）；adr/README.md「历史偏差（规则生效前）」与第 6 条；ADR-0022 补齐由 psa-gl-finance-review 负责 |
| 「给 agent 一个写代码的规则，代码注释能不写就不写。」（2026-10-14） | SESSION_PLAYBOOK §5「代码注释能不写就不写」；仓库根 CLAUDE.md |
| 「批准 ADR-0024」（2026-10-14，psa-coordinator 当面） | ADR-0024 已采纳；ADR-0016 状态标「后果第 4 条已被 ADR-0024 取代」并删除 1cd7219 追加的修订提议段；brief Phase 4 入口相应 Proposed revision 转为正文 |
| R0 业务待决 B6–B11（2026-10-14，psa-r0-metric-baseline 当面询问，用户即业务 Admin）：B6「保留，作为订单前置指标 (Recommended)」；B7「保留五种，其余推导 (Recommended)」；B8「同意两项 (Recommended)」；B9「迁入激励模块并版本化 (Recommended)」；B10「作为外部参考数据集纳入 (Recommended)」；B11「纳入，作为报表包 P8 (Recommended)」 | r0/METRIC_BASELINE_DRAFT.md §6.1：立项金额保留为订单前置指标、赢单率分母为赢单 + 丢单；目标类型为预算 / 承诺 / 挑战 / 预测 / 减值计划，理论与线性由参数推导、区域上报作为承诺或预测的版本、承诺清单到项目 / 发票粒度；8 个报表包，「产工比」归方言、正式名「人工成本产出倍数」；季度激励迁入 organization.incentive 并按版本生效；外部市场数据作为外部参考数据集；交付质量指标纳入 P8，内审完成率与覆盖率合一 |
| ADR-0022 对抗性评审（2026-10-14，psa-gl-finance-review 弹窗作答，architecture-critic 意见 C-1..C-15）：C-1「收付推送前移到 6a (Recommended)」；C-6「共同科目沿用收付编码 (Recommended)」；C-9「解耦 (Recommended)」；C-12「先留 U8C，逐家再定 (Recommended)」；C-5「待财务 (Recommended)」；C-8「红字 (Recommended)」；此前用户：「提交」（在 #23、#24 合并前提交 ADR-0022 补齐） | ADR-0022 决定第 1、2、8、10 条与迁移、回滚、守护小节；proposals/ADR-0022_CRITIC.md；GL_FINANCE_DECISIONS GL-123（C-9 修正适用范围）、D17-6（C-5 待财务） |
| ADR-0022 审批（2026-10-14，psa-coordinator 当面）：「批准」 | ADR-0022 已采纳；同一提交标注 ADR-0016 第 6 条、ADR-0017 第 4 条及对照表 accounting.ledger 行、ADR-0023 L2 对总账的适用已被 ADR-0022 取代 |
| 财务确认（2026-10-14，psa-gl-finance-review 当面）：「做成财务确认页」；「他们没有 claude」；「把前面涉及财务的 ADR 整体让财务确认下」。psa-coordinator 决定：以 r0/FINANCE_QUESTIONS.md 为唯一题源、按 FQ 号回填、按来源分发，补 FQ-21（预付款条款）、FQ-22（U8C 自有凭证）、ADR 清单补 0010 与 0017 | r0/FINANCE_CONFIRM.html（第 2 版，离线 HTML，取代未提交的第 1 版）；r0/FINANCE_ADR_POINTS.md；r0/build_finance_confirm.py、r0/import_finance_answers.py；r0/FINANCE_QUESTIONS.md FQ-21、FQ-22 |
| R0 工具对比后（2026-10-14，经 psa-coordinator 转达）：Q1 BI 工具「C 为主，D 待定为后期可选 (Recommended)」；透视「沿用 AG Grid 企业版」 | BI_SCOPE §6.1 Q1；r0/TOOL_COMPARISON.md §0、§7（R1 入口条件草稿）：语义层与 8 个报表包并入 FORCITIS one（operations.policy 治理目录，reporting 编译执行，守护进骨架 CI）；B 冻结作设计输入；D 为 R6 后可选；AG Grid 企业版许可采购为部署前事项，许可密钥走环境变量 / KMS，不进仓库 |
| 问「现在合并过程是不是有什么问题？」后选择：「一条记录一个文件 + ADR 拆开 (Recommended)」（2026-10-14，psa-coordinator 当面询问） | intent/、session-log/、status/ 片段与 build_records.py；adr/src/ 与 build_adrs.py；build_all.py 与 CI「generated docs match fragments」；SESSION_PLAYBOOK §1、§3、§4、§6、§7、§8、§12；CLAUDE.md |
| Phase 1a TIMESHEET 节点顺序核实（2026-10-14，psa-coordinator 询问）：「授权只读核实生产 BPMN (Recommended)」；projectManagerId / preSalesHours 来源列为 Phase 2 入口待决 | phase1a/TIMESHEET_REPLAY.md「生产核实结果（v15）」（并行包容网关三路、子流程内驳回即退回、补 OH 分支）；brief Phase 2 入口 Proposed revision 草稿 |
| 问「1a 还有哪些设计需要现在考虑？权限系统？」，选「写授权模型 ADR，1a 实现数据权限 (Recommended)」（经 psa-coordinator 转达） | ADR-0026（提议）：数据范围机制、法人维度、审批访问、职责分离、super_admin 边界、授权治理、敏感字段、老角色迁移；1a 实现第 1、3、5 项（待用户批准 ADR） |
| 问「OLTP、OLAP 现在架构清晰吗」，选「现在写，并先定事件必带字段 (Recommended)」（2026-10-14，经 psa-coordinator 转达） | 新会话 psa-olap-architecture：先定领域事件的分析必带字段（交 1a 事件目录定稿），再起草 ADR-0025（提议）「OLAP 物理架构与数据流」 |
| 问「第一批agent写完代码，看看有没有需要总结约束后续agent写代码行为的？」后选「全部采纳，并把能自动化的做成守护 (Recommended)」（2026-10-14，psa-coordinator 当面询问） | SESSION_PLAYBOOK §5「写代码规则第二批」第 1–10 条；CLAUDE.md；.pre-commit-config.yaml（gitleaks 8.30.1）；.github/pull_request_template.md；前端界面文字进词表守护由 1a 会话负责 |
| ADR-0025 待决（2026-10-14，经 psa-coordinator 转达，原话为所选项）：历史迁移「是，权责事实一律进交易库 (Recommended)」；分析权限部门范围「含下级，按事实发生时的组织 (Recommended)」；快照与口径「作废重建并留版本；其余按建议 (Recommended)」；构建方式「同意 (Recommended)」；6e 敏感对象「不镜像 (Recommended)」；老库 mirror_* 只写账号「同意，6a 前执行 (Recommended)」；生产库 D-1「扩容现有实例共用」 | ADR-0025 决定第 2–6 条按此改写：权责历史一律进 OLTP（部分取代 ADR-0011 第 2 条）；授权含下级、按事实发生时的组织，另给管理层「按现组织重归属」视角；例外重开期间 L2 作废重建留版本，数据错误只在开放期间列示，合同资产引用 accounting.reporting、经营口径另设未开票在制；财务事实直读 operations.facts；6e 敏感对象不镜像；mirror_* 账号列为 6a 前事项、执行前再确认；生产不另开 RDS，扩容现有实例共用、按库 / schema / 角色隔离，扩容（购买）执行前再确认 |
| 第二波 opus 复审 9 个取舍（2026-10-14，psa-coordinator 询问）：除第 7 条外全部按建议；第 7 条：「能从一开始就做对的，就不要留在后续阶段，除非后续做更有价值」 | M2 web 层响应 record；M3 写死 /api/v1；M5 未发号法人代码可更正；M6 workforce 直接用 integration.api 类型、member→employee；M10 以生成类型为准；M11 内核值对象保留；TIMESHEET 编号 1a 即用统一新格式；第 8 条规则卡提回评审；第 9 条 DdlNamingTest 公共列豁免 COMMENT |
| 能从一开始就做对的，就不要留在后续阶段，除非后续做更有价值（2026-10-14，经 psa-coordinator 转达） | SESSION_PLAYBOOK §5「写代码规则第二批」第 11 条；CLAUDE.md |
| RULE-280 默认通过意见（2026-10-14，psa-coordinator 询问）：「留空并如实审计 (Recommended)」 | RULE_REVIEWS「实现方式变更」；BUSINESS_RULES RULE-280 Revision；proposals/RULE-280_DEFAULT_REASON.md（已确认） |
| 每个阶段实施前增加「流程梳理」步骤：产出现状流程（代码、OA 盘点、生产计数）、目标流程、差异清单，差异逐条由我决定，结果写入 INTENT 和 brief 的行为契约。先把这一步写进 SESSION_PLAYBOOK 和 brief 的阶段入口条件，再从 Phase 1a 涉及的流程开始做。（2026-10-14，经 psa-coordinator 转达） | SESSION_PLAYBOOK §13；process/README.md；brief 各阶段入口条件（1a 为补做）与 §5「流程梳理结论」（rules/build_brief.py 生成） |
| Phase 1a 流程梳理范围：「按建议 7 个、按此顺序 (Recommended)」（登录与会话；组织与人员同步；审批通用流程；TIMESHEET 审批；合并待办与通知；业务编号；审计） | process/1a/（每个流程一份）；status/units/065-Phase-1a-流程梳理.md |
| 推迟项按新原则重审（2026-10-14，psa-coordinator 询问）：「同意，全部在 1a 做 (Recommended)」 | G-1、G-2 会签、RULE-302、RULE-268/269/285/309、RULE-300 剩余、H5（企业微信静默登录、待办、审批详情）在 1a 完成；仍后做项与理由见 phase1a/DEFERRED.md |
| 「1a 涉及哪些聚合？状态机是怎样的？」→ 状态机收进聚合并加守护：「现在就改，并加守护 (Recommended)」（2026-10-14，psa-coordinator 询问） | 审批请求（含轮次）、同步批次、出站投递、工时单、账号 / 角色 / 法人 / 编号规则的启停与确认：合法转换表进领域对象、领域方法改状态、非法转换抛领域异常、仓储带前置状态条件或乐观锁；每个状态机穷举测试；守护禁止 application / infrastructure 直接写状态与 SQL 状态字面量赋值；状态机图写进架构 §3（Phase 1a 下一轮代理 A / B） |
| 问「过渡期老系统自己的 A8 同步何时停」：「保留到老模块下线 (Recommended)」——老同步继续到老系统不再读老用户表为止（最迟 Phase 5），对账只比新快照与 A8，A8 访问登记改成一致说法 | process/1a/组织与人员同步.md（差异：老系统双同步）；contract.md |
| ADR-0025 审批：「批准」 | ADR-0025 已采纳；同一提交标注 ADR-0011 第 2、4 条与 ADR-0023 第 2 条 L1 采集限制对一次性历史装载的适用已被 ADR-0025 取代 |
| ADR-0026 D-1 员工法人归属：「部门→法人为默认，员工可单独指定并带生效区间 (Recommended)」（经 psa-coordinator 转达） | ADR-0026 第 2 项第 5 条：部门→法人对照（子部门继承）为默认，个别员工单独指定，二者带生效区间、按事实发生日取值；与流程梳理 D-1a-27 员工→法人生效区间表合并为同一模型 |
| 「当前仓库的所有文档里要避免使用 psa 旧名称，FORCITIS one 是新名称」；追问后：「老系统称「老系统」，字面标识保留 (Recommended)」；「文档标题改，仓库名和会话名暂不改 (Recommended)」（2026-10-14，经 psa-coordinator 转达） | 工作单元「文档去除旧名称 PSA」（one-docs-naming）；规则：指代新系统 → FORCITIS one，指代老系统 → 老系统；老路径、表名、接口、代码标识、文件名、仓库名、分支名、会话名、用户原话与已采纳 ADR 正文保留；文档标题改；用语守护禁用以 PSA 指代新系统；新会话名用 one-* 前缀 |
| 问「同步被拦下谁来放行、问题发给谁」：「新设组织数据管理员 (Recommended)」——新增角色「组织数据管理员」（放行、法人维护、问题清单），由业务 Admin 指定人选；放行须填原因；HELD 与每日问题汇总用企业微信发给该角色 | process/1a/组织与人员同步.md（差异：放行人、问题出口）；contract.md；需改代码清单 |
| ADR-0026 其余 11 项（D-2..D-7、D-9..D-12）：「全部按建议，并批准 ADR-0026 (Recommended)」（经 psa-coordinator 转达） | ADR-0026 状态改为已采纳；ADR-0009 状态标注第 4 条部分已被 ADR-0026 取代；REIMAGINED_ARCHITECTURE §3.2、AI_NATIVE_SPEC C1a-08/09 与 §5.3、SECURITY_INVARIANTS I-10..I-12 同步 |
| 「领域事件是在1a 要考虑的吗」→「在 1a 做全 (Recommended)」（2026-10-14，psa-coordinator 询问） | 聚合在状态转换时记录事件、同事务交 Modulith 发布登记表、消费方幂等；打通 ApprovalCompleted → cost.timesheet；补法人、编号规则、账号、角色等对外有意义的事件；AsyncAPI 事件目录与代码一致；事件守护；架构 §3 事件流图（Phase 1a 下一轮） |
| 「1a 还有哪些 DDD 设计需要现在考虑？」→「全部在 1a 做，并建 DDD 检查清单 (Recommended)」（2026-10-14，psa-coordinator 询问） | 法人维度与乐观锁直接实施；DMN 路由、审批与单据编排、跨模块强类型、错误链、读模型、上下文映射、审计与事件原则先出方案报用户；DDD 检查清单进 SESSION_PLAYBOOK 与 opus 复审提示 |
| 问「对账怎么跑、差异怎么处理」：「每日自动+页面，差异须清零 (Recommended)」——每天自动对账并可手动触发；比较项加部门负责人、区域 AR、企业微信 ID；差异进问题清单，由组织数据管理员在 A8 修正后重新同步；出口要求零差异，不接受签字豁免 | process/1a/组织与人员同步.md（差异：对账）；contract.md；需改代码清单 |
| ADR-0026 D-13 季度授权复核由谁负责：「内控 / 财务」（经 psa-coordinator 转达） | ADR-0026 第 6 项第 3 条：季度复核报表由内控 / 财务复核，超期提醒内控 / 财务负责人 |
| 问「直属领导不合格时新系统记什么」：「写空+问题清单 (Recommended)」——不保留过时上级；审批取人按 RULE-250 往上推，推不到转流程管理员并告警；组织数据管理员收到问题后去 A8 修正 | process/1a/组织与人员同步.md（差异：汇报线不合格）；contract.md |
| DDD 第 3 项审批路由 DMN（2026-10-14，psa-coordinator 询问）：「B 决策表管业务判断 (Recommended)」——修订检查点 2「1a 不引入 DMN」 | DMN 只管走哪几路、能否跳过、能否自动通过；取谁仍用类型化指派规则；REIMAGINED_ARCHITECTURE §2.1 注明修订 |
| 问「员工离职后名下未处理的审批任务怎么办」：「按规则重新取人 (Recommended)」——同步发现离职后，该人待办按节点指派规则重新取人（汇报线往上推），取不到转流程管理员并告警；审计记录原处理人与新处理人 | process/1a/组织与人员同步.md、审批通用流程.md（差异：离职者在途待办）；contract.md；需改代码清单 |
| DDD 第 6 项错误链位置：「新建 platform.web (Recommended)」（2026-10-14） | platform.web 技术模块：全局 ProblemDetail 处理器、OpenApiRequiredProperties 等跨模块 web 装配 |
| 问「员工 / 部门归哪个法人、法人代码格式」：「1a 就建归属」——1a 即增加员工→法人生效区间表，由组织数据管理员维护（法人代码格式另问） | process/1a/组织与人员同步.md（差异：法人归属）；contract.md；需改代码清单 |
| DDD 第 9 项审计与领域事件：「B 审计订阅领域事件 (Recommended)」；安全事件仍 recordIndependently（2026-10-14） | platform.audit 订阅领域事件（事件发布登记表保证不漏）；登录失败 / 拒绝访问直接独立事务记录 |
| 问「A8 人员『是否内部』为 NULL 怎么处理」：「先查计数，规则同部门 (Recommended)」——用已授权只读通道查人员侧 NULL 行数（只取计数）；NULL 视为内部，并记入问题清单由组织数据管理员在 A8 补齐 | process/1a/组织与人员同步.md（差异：人员 NULL 标志）；contract.md；需改代码清单；生产计数待 psa-coordinator 确认访问方式 |
| 审批超时：「工时要求T+1 审批完毕，这个是不是要现在考虑？」；T 从「提交审批当天 (Recommended)」起算；+1 按「工作日，需要工作日历 (Recommended)」；超时「提醒审批人」「升级给审批人的上级」「计入审批时效统计」（均 Recommended，不自动通过）（2026-10-14） | 1a 实现审批时限能力（时限由 DMN / 配置给出，TIMESHEET=提交日后第 1 个工作日结束前）、工作日历（节假日与调休）、到期提醒与升级（取不到上级给流程管理员并告警）、事件 ApprovalTaskOverdue / ApprovalTaskEscalated；不自动通过 |
| 组织同步其余建议（逐项勾选）：「每 5 分钟同步,HELD 不设「拒绝」,法人停用规则,其余 5 项按现设计」——即：每 5 分钟全量同步；HELD 只能放行（填原因）或等源修复后自动恢复；法人停用后不能用于新单据编号、历史单据保留原法人；部门简称每天从老库只读视图刷新至新维护页上线；生日推迟到费用阶段；同一企业微信 ID 属多名在职者时都不存并记问题；账号由组织变更事件异步创建、登录未命中当场建档兜底；1a 不加岗位 / 法人变更事件 | process/1a/组织与人员同步.md；contract.md |
| 工作日历（2026-10-14，psa-coordinator 询问）：归属「考勤与休假 organization.attendance (Recommended)」（BC 提前到 1a 建）；范围「一份默认日历，预留法人专属 (Recommended)」；时限口径：「这个旧系统已经有规则，看看能不能读到」 | organization.attendance 在 1a 建：工作日历结构、维护、查询「第 N 个工作日结束时刻」、未录入年份失败告警；TIMESHEET 截止与升级参数待 psa-1a-process-review 查实老系统规则后定，放 DMN / 配置不写死 |
| 问「法人字母代码与 U8C 数字核算组织编码怎么对应」：「两种代码并存 (Recommended)」——法人表增加「U8C 核算组织编码」（数字，唯一）；字母代码继续用于业务编号；对接 U8C 与老系统按数字编码 | process/1a/组织与人员同步.md（差异：法人代码）；contract.md；需改代码清单 |
| 平台约定 6 项（ID 策略、时间与时区、API 规范、可观测性、备份与恢复、配置与密钥）：「全部在 1a 做 (Recommended)」（2026-10-14，psa-coordinator 询问） | 先出方案（proposals/PHASE1A_PLATFORM_CONVENTIONS.md），有取舍的报用户定，再由下一轮代理实施；备份恢复与密钥写入 INFRA_RUNBOOK（执行属部署前事项） |
| 问「外部调用方（YD、A8 门户、customization-login、BI、KM）怎么切换到签名票据」：「逐个切换、逐个批准 (Recommended)」——先盘点调用方清单（含查明 customization-login 的调用方）；每个调用方一份切换步骤、一把密钥；开启签发方由用户逐个批准并做安全复审；老端点到 Phase 5 才下线 | process/1a/登录与会话.md（差异：外部调用方切换）；contract.md |
| 平台约定（2026-10-14，psa-coordinator 询问）：主键「UUIDv7 (Recommended)」；分页「游标分页 (Recommended)」；备份恢复「RPO ≤ 5 分钟、RTO ≤ 1 小时 (Recommended)」；告警「按级别分通道 (Recommended)」；并发版本传递未单独作答，按建议 If-Match / ETag | proposals/PHASE1A_PLATFORM_CONVENTIONS.md 标为已决定；A、B 合并后的下一批实施；INFRA_RUNBOOK 写入备份恢复与告警分级（接收群与值班人部署前定） |
| 问「应急管理员账号怎么定」：「独立账号+运维开通 (Recommended)」——应急账号不绑员工、不依赖 A8；保留 2 个；由运维用受控脚本开通 / 启停 / 重置口令与 TOTP，全部审计；改口令后吊销其其他会话 | process/1a/登录与会话.md（差异：应急账号）；contract.md；需改代码清单 |
| 生产库：「扩容现有实例共用」（不另开 RDS）（2026-10-14，psa-coordinator 转达） | INFRA_RUNBOOK D-1、one_db_roles_schemas.sql：生产沿用现有 PG 18 高可用实例，另建生产库与 one_prod_* 角色，连接数预算与按角色超时；实例扩容为部署前购买操作 |
| 问「企业微信小程序怎么带会话」：「请求头带会话标识 (Recommended)」——同一服务端会话（同样的空闲 / 绝对时长与吊销），小程序用请求头携带会话标识、不用 Cookie，该入口不接受 Cookie 故无需 CSRF；不引入令牌对 | process/1a/登录与会话.md（差异：小程序会话载体）；contract.md；需改代码清单 |
| 问「员工复职后角色怎么办」：「只授基础角色 (Recommended)」——复职时重新授予基础角色（employee），其余角色由管理员重新分配；审计记录 | process/1a/组织与人员同步.md D-1a-17；contract.md；需改代码清单 |
| 问「T+1 工作日到哪一刻算超时」：「T+1 工作日 23:59 (Recommended)」——下一个工作日结束仍未处理即超时，次日早上提醒审批人；再过 1 个工作日升级给直属上级 | process/1a/TIMESHEET审批.md、审批通用流程.md（差异：审批时限）；contract.md；需改代码清单 |
| 问「新日历怎么建、谁维护」：「迁历史+组织数据管理员年度录入 (Recommended)」——2016–2026 共 4,018 天迁入新默认日历（只取工作日、法定假日、调休）；组织数据管理员每年 12 月 1 日前在页面录入下一年，11 月系统提醒；缺年份报错告警；2027 年须在 1a 上线前录入 | process/1a/TIMESHEET审批.md（差异：工作日历）；contract.md；需改代码清单 |
| 问「审批时效耗时怎么算、提醒升级走什么渠道」：「按工作日+企业微信 (Recommended)」——耗时按工作日小时计（非工作日不计），统计按审批人、部门、月份的当期办结率与超时件数；提醒与升级用企业微信卡片，并在合并待办中标红 | process/1a/TIMESHEET审批.md、审批通用流程.md、合并待办与通知.md；contract.md；需改代码清单 |
| 问「COST-R02 次日 9:00 前填完是否纳入」：「留到 Phase 2 流程梳理定 (Recommended)」——1a 只做审批时限；填报截止及提醒 / 锁定在 Phase 2 工时流程梳理时决定 | process/1a/TIMESHEET审批.md（差异：填报截止，转 Phase 2） |
| 问「切换前怎么处理缺企业微信 ID 的在职员工」：「列为 1a 出口条件 (Recommended)」——上线前按「在职但无企业微信 ID」清单由组织数据管理员在 A8 补齐；出口要求该清单为 0（确无企业微信的人逐个登记原因） | process/1a/登录与会话.md（差异：企业微信 ID 覆盖率）；contract.md；brief Phase 1a 出口条件（提议，由 psa-coordinator 处理） |
| 问「登录接口按规格 GET+302 还是代码的落地页+POST」：「按代码（落地页+POST） (Recommended)」——规格改为与代码一致；补上外部票据落地页；一次性凭证不出现在服务端访问日志的 GET 参数里 | process/1a/登录与会话.md（差异：接口形态）；contract.md；需改代码清单（票据落地页）；规格修订 |
| 问「老系统 178,533 行登录日志怎么办」：「不迁，老表只读归档 (Recommended)」——与 ADR-0011「API 日志只读归档」一致；老系统下线时导出归档，保留期按公司审计要求（下线前再定） | process/1a/登录与会话.md（差异：历史登录日志）；contract.md |
| 登录其余建议（逐项勾选）：「同步延迟告警 10 分钟,会话不设状态字段,1a 不加账号停用事件,会话法人推到 1b」——即：距上次成功同步超过 2 个周期（10 分钟）或被拦下即告警；会话不设状态字段，过期 / 吊销 / 登出原因只记审计，停用与角色变更也补写「会话吊销」审计；规格中的 AccountStatusChanged 删去，有订阅方时再加，老系统外部订阅方在 Phase 5 前盘点；登录后按哪个法人生效与数据范围授予表在 1b 流程梳理时定 | process/1a/登录与会话.md；contract.md；需改代码清单（吊销审计） |
| 问「流程管理员是谁、告警怎么发、能做什么」：「角色+企微告警+可改派 (Recommended)」——流程管理员是角色（可多人）；取不到人的任务进该角色候选组，并用企业微信告警；管理员可改派给正确的人（填原因、审计）；生产未配置流程管理员时系统拒绝启动 | process/1a/审批通用流程.md（差异：流程管理员）；contract.md；需改代码清单 |
| 问「代理的边界」：「仅流程管理员、逐次、通知本人 (Recommended)」——只有流程管理员可代理；每次必须选择被代理人并填原因；可代理通过与驳回；审计记录操作人与被代理人，并用企业微信通知被代理人；不设长期委托 | process/1a/审批通用流程.md（差异：代理）；contract.md；需改代码清单 |
| 问「审批结束后再次发起哪些允许、重复调用怎么处理」：「已通过不再发起 (Recommended)」——同一单据+轮次的重试返回原实例；已退回、已驳回、已撤销可开新一轮；已通过不允许再发起（需改动的由业务模块建新版本单据） | process/1a/审批通用流程.md（差异：重新发起与幂等）；contract.md；需改代码清单 |
| 问「在途老单据排空期限与停滞实例」：「按阶段排空+停滞清单 (Recommended)」——每个流程在其业务迁入阶段出口前排空；停滞超 30 天的老实例每周列清单给流程管理员，在老系统里催办、转交或撤销；离职者名下的老任务同样列入清单 | process/1a/审批通用流程.md（差异：在途老单据）；contract.md |
| 审批办理动作（逐项勾选）：「不自动通过本人单据,退回指定节点不结束本轮,发起人撤回=结束本轮,转交规则」——即：审批人=发起人时不自动通过，按反自审升级给直属领导；退回到指定节点在同一轮内重审，状态仍为审批中、不通知业务模块、审计记录，随第一个用到的流程交付；发起人撤回与退回一样结束本轮（记为已退回，来源发起人撤回），单据回到可编辑，重提开新一轮，「能否撤回」显示与执行同一判断；转交只能给在职、非发起人用户，原因必填并审计，候选组任务须先认领再转交，通知被转交人 | process/1a/审批通用流程.md；contract.md；需改代码清单 |
| 审批加签、重提、钩子、时限（逐项勾选）：「加签人限制,发起人离职不代重提,钩子不做弹窗协议,时限按流程配置」——即：只有已认领的当前处理人可加签，加签人须在职、非发起人；只有原发起人能重提，发起人离职时由流程管理员撤销在途审批并通知业务负责人重新发起，管理员不能代发起；BEFORE 钩子失败返回明确校验信息，不延续老系统交互式确认弹窗协议；审批时限在各流程节点上配置（按工作日），未配置不计时，TIMESHEET 配 T+1，提醒 / 升级 / 统计 / 不自动通过的通用做法与 TIMESHEET 一致 | process/1a/审批通用流程.md；contract.md；需改代码清单 |
| 审批知会、历史、投递（逐项勾选）：「知会进收件箱,办理记录表,死信告警+重投入口,退回后单据可编辑」——即：知会在合并待办里单列，发卡片，打开即已读，不阻塞流程，随第一个用到知会的流程交付；新增只追加的任务办理记录（含自动同意、加签建议、转交、代理），已办与逐轮历史从中查询，撤销后原审批人仍可见；投递进死信即告警运维与流程管理员，管理页可重投或放弃（填原因、审计）；退回（含撤回）后业务单据回到可编辑并带「已退回」标记，具体状态名在各业务阶段定 | process/1a/审批通用流程.md；contract.md；需改代码清单 |
| 审批事件、法人、DMN（逐项勾选）：「自动同意也发结果,事件随功能补齐,法人记录不限取人,DMN 与指派类型随阶段定」——即：重复审批人自动同意时也发任务结果事件并标注「自动」，加签建议仍不作正式结果；转交、加签、知会事件随功能交付时补，AFTER 钩子与老任务创建 / 活动消息的去留随第一个使用者（差旅 Phase 3）定，规格补列 TaskClaimed；审批请求记录单据法人（业务模块传入），取人不限同法人，审批期间可见性按参与人规则、不受法人范围限制，1b 落地；出现真正的多维决策表时在该阶段引入 DMN，其余指派类型随各流程迁入时翻译，规格中的 DMN、接口路径与权限点、RETURNED 枚举按代码与评审结论修订 | process/1a/审批通用流程.md；contract.md；需改文档 |
| ADR-0027 Q-1：「明确离职就拒绝 (Recommended)」；Q-2：「1a 暂不列敏感，1b 前复核 (Recommended)」；Q-3：「暂停，转人工确认 (Recommended)」（经 psa-coordinator 转达） | ADR-0027 第 1 项第 3、7 条，第 2 项第 3 条；用户决定表 |
| ADR-0027 Q-4 与审批：「新设「业务管理员」系统角色，由我持有；批准 ADR-0027 (Recommended)」（经 psa-coordinator 转达） | ADR-0027 已采纳；系统业务角色「业务管理员」，首任持有人为用户（种子或运维命令按账号指定）；ADR-0009、0010、0026 状态标注部分取代；SECURITY_INVARIANTS I-2、I-11，AI_NATIVE_SPEC C1a-08 与 §5.3 同步；STATUS 阻塞登记 1a 实现项 |
