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
| 第三批「其余」条数复核（2026-10-14，psa-coordinator 询问）：「是，44 条都同意」 | RULE_REVIEWS.json（RULE-316..376 中除 323 外的 44 条） |
| Phase 1a reimagine 检查点 2（架构，2026-10-14）：Q-1「1a 删除桥接 (Recommended)」；Q-2「两条都按建议修订 (Recommended)」；Q-3「收窄；通知发候选组全员 (Recommended)」；Q-4「四项都同意 (Recommended)」 | REIMAGINED_ARCHITECTURE.md §7（已批准）；ADR-0009 §6 修订；brief Phase 1a 出口条件两条 Proposed revision；AI_NATIVE_SPEC C1a-06 删除 |
| 「确保权责业务事实全部在新系统」（2026-10-14，对 OA 流程盘点的要求） | GUIDING_PRINCIPLES §9a「权责事实全部在新系统」；OA_PROCESS_INVENTORY 分类与权责事实完整性矩阵；ADR-0021 |
| 总账纳入新系统（2026-10-14，经评估会话转达，psa-coordinator 当面复核「是，4 项都是我的决定」）：「我要财务总账集成到新系统，通过新系统直接可以出权责口径的三大报表」；路线「两步：先管理口径，再年度切换法定账（推荐）」；PSA 以外的凭证来源「先导入 / 手工凭证，逐步自建（推荐）」；现金流量表「直接法为主 + 间接法补充（推荐）」 | ADR-0022（提议）；brief 修订稿（新增总账与报表、法定账切换阶段）；ADR-0016 第 6 条、ADR-0017 第 4 条修订稿 |
| 「可以从我现在U8C 系统蒸馏总账做法」（2026-10-14，经评估会话转达） | U8C_GL_PRACTICE.md（只读蒸馏结构、规则、公式与计数），作为 ADR-0022 依据 |
| 安全补丁第 7 轮后（2026-10-14，psa-sec-hotfix 会话）：「老系统先不管，确保新系统没有这些缺陷」 | 老系统安全止血搁置（STATUS）；phase1a/SECURITY_INVARIANTS.md I-1..I-9；老系统补丁与止血方案保留在 security_remediation.* 备查，不执行 |
| 同上，问「老系统不处理的前提下，新系统如何对待老系统的登录令牌？」：「取消桥接 (Recommended)」（与检查点 2 Q-1 一致） | ADR-0009 §6 补充：老 PSA 后端在免密入口修复前不得作为签名票据签发方；SECURITY_INVARIANTS I-1、I-2 |
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
