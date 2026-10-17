# 架构知识框架（锁定版本）

依据：用户 2026-10-15「你需要有一个稳定的知识框架，不能漂移」（intent `2026-10-15-163920`）；按适配评审收窄（ADR-0041，intent `2026-10-15-195827`）。本文件是 one-architecture 做架构判断时唯一的理论参照：只收经过适配评审、对本系统确有用处的框架与概念，每项锁定权威版本，写明本项目怎么用；概念用官方英文名加中文，释义用我们自己的话写，出处可查。`forcitis-architecture` 目录不是信源（intent `2026-10-15-163920`）。

**防漂移规则**
1. 改动本文件（换版本、增删框架或概念、改释义）须引 ADR 或 intent，提交带 `Architecture-Ref:`。
2. 增加框架、标准或选型前，先做对照本系统的适配评审：它要求什么、我们照做的代价、实际收益、把握程度；决策页写明代价，不预选推荐项（ADR-0041 决定 6，intent `2026-10-15-193135`）。
3. 本文件与 `METHOD.md` 列入 one-architecture 画像的「上下文文档」，压缩或清空后自动注入；做判断前以本文件为准，不凭记忆。
4. 标「未核实」的条目不作硬依据，用到时先核实并更新本文件。
5. 核实记录：2026-10-15，sonnet 子代理查一手来源（版本与出处）；同日 opus 对抗式适配评审与联网核实，见 `proposals/FIT_REVIEW_FRAMEWORKS.md`、`FIT_REVIEW_ENGINEERING.md`、`FIT_REVIEW_VERIFY.md`。

## 1. Domain-Driven Design（主干）

出处：Eric Evans，*Domain-Driven Design*（Addison-Wesley，2003）；**DDD Reference（Evans，2015，CC BY 4.0，以它的模式名为准）** https://www.domainlanguage.com/ddd/reference/ ；Vaughn Vernon，*Implementing Domain-Driven Design*（2013）。

| 概念 | 中文 | 本项目怎么用 |
|---|---|---|
| Ubiquitous Language | 统一语言 | `glossary/terms.yaml`（ADR-0018） |
| Bounded Context | 限界上下文 | 一个 BC = 一个模块 = 一个 schema（ADR-0003）。边界由统一语言的一致范围、不变量与事务一致性决定，不由能力或老系统模块决定。技术模块（`platform.web`、`idempotency`、`eventing`、`scheduling` 等）在 `ddd/contexts.yaml` 标 `kind: technical`，不称 BC |
| Aggregate、Entity、Value Object、Domain Event、Service、Repository | 聚合、实体、值对象、领域事件、领域服务、仓储 | 一个事务只改一个聚合（SESSION_PLAYBOOK §14 第 1 项）；聚合与表的对应以数据访问注册表 `aggregate` 字段为准 |
| Context Map：只用有代码后果的四种关系——Anticorruption Layer、Open-host Service + Published Language、Shared Kernel、Conformist | 上下文映射 | `ddd/context-map.yaml` 只登记已建或本阶段在建的边，记方向、经由（`api` 或 `events` 契约）与上述关系；防腐层 = `platform.integration`（ADR-0039）。Partnership、Customer/Supplier、Separate Ways 是团队间关系，单团队单体中不用 |
| 深建模 / 薄 CRUD | — | 取代核心 / 支撑 / 通用三分类：规则密集或触账的 BC 深建模（聚合、不变量、事件），其余薄 CRUD。标记由用户定 |
| 事件清单推导 | — | 取代事件风暴工作坊：以规则卡与生产取证为输入推导领域事件、命令与聚合，用户裁定（METHOD 第 2 步） |

复审问题（来自 REA 会计本体，McCarthy 1982，不作框架采用）：每个经济事件的对偶事件在哪里；合同承诺如何成为事实。用于 ADR-0016 相关阶段的过账设计复审。

## 2. BPMN 2.0.2（OMG，formal/13-12-09，2014-01 发布）

出处：https://www.omg.org/spec/BPMN/2.0.2/ 。ISO/IEC 19510:2013 对应的是 2.0.1（OMG formal/13-11-03），不与 2.0.2 等同。核实：2026-10-16 查 OMG 官网，当前正式版仍为 2.0.2，无 2.1 或后续版本公告（`proposals/closeout/PROCESS_METHOD_RESEARCH.md` 第 1 节；intent `2026-10-16-155312`）。画法约定提议稿见 `proposals/closeout/BPMN_STYLE.md`，与下表「只用可执行 Process」「登记由 `.bpmn20.xml` 生成」两句的冲突随流程设计方法稿交用户批。

| 概念 | 本项目怎么用 |
|---|---|
| Process（Flow Objects：Event、Activity、Gateway；Sequence Flow；Lane） | 只用可执行的 Process（Flowable，ADR-0007），只编排人工步骤、超时与退回，不做业务计算（GUIDING_PRINCIPLES §4）；流程登记由 `.bpmn20.xml` 生成（加所属阶段字段），不手写 |

## 3. 维度建模与 OLTP / OLAP

出处：Kimball & Ross，*The Data Warehouse Toolkit*，3rd Ed.（Wiley，2013）；技术目录 https://www.kimballgroup.com/data-warehouse-business-intelligence-resources/kimball-techniques/dimensional-modeling-techniques/ 。OLTP / OLAP 的表述以 GUIDING_PRINCIPLES §7、§8 为准。

| 概念 | 中文 | 本项目怎么用 |
|---|---|---|
| Grain | 粒度 | 取一条领域事件或过账明细（ADR-0025） |
| Fact table：Transaction、Periodic snapshot、Accumulating snapshot | 事实表类型 | 期间快照对应 Periodic snapshot（ADR-0023） |
| Conformed dimension | 一致性维度 | 各 BC 负责的主数据 |
| Enterprise Data Warehouse Bus Matrix | 总线矩阵 | 行 = 业务过程（度量事件：过账、开票、工时等过账事实类型或领域事件类型），列 = 一致性维度；由 SSOT 生成 |
| Slowly Changing Dimension Type 2 | 缓慢变化维第 2 类 | 主数据版本化（D6、D17） |

## 4. TOGAF Standard, 10th Edition（只用迁移规划部分）

出处：The Open Group，C220（含 Technical Corrigendum 1，2025）https://publications.opengroup.org/standards/togaf/specifications/c220 ；Business Capabilities v2（G211，2022）。

| 概念 | 中文 | 本项目怎么用 |
|---|---|---|
| Baseline / Target / Transition Architecture | 基线 / 目标 / 过渡架构 | 老系统现状 / 目标架构 / 每个阶段结束时可运行、可回滚的中间状态（`togaf/transitions.yaml`） |
| Architecture Roadmap | 架构路线图 | 过渡架构的排序与依赖（图②） |
| Business Capability（G211，只用一级） | 业务能力 | `togaf/capabilities.yaml` 一级 12 项，只作范围漏项核对，不驱动 BC 推导；二级保留为参考 |

不用：ADM 全套阶段与治理机制（Architecture Board、Contract、Enterprise Continuum 等）；ABB / SBB 与 BC 不作对应。

## 5. 数据管理（以本仓库规则为准）

DAMA-DMBOK 第 2 版修订版（2024，https://dama.org/dama-dmbok-revision/ ）只作概念参照：System of Record（记录系统）、Reference and Master Data（主数据）、敏感分级、保留期、血缘、数据质量规则落为约束。本项目的规则与登记以 SESSION_PLAYBOOK §14.1 D1–D19、`backend/architecture/data-access-registry.yaml`（DataArchitectureTest 双向校验）、`backend/architecture/data-classification.txt` 为准；不另建 `dama/data-objects.yaml`。

## 6. C4 model（只用 L1、L2）

出处：Simon Brown，https://c4model.com/ （CC BY 4.0，无版本号）。只用 System Context（系统上下文）与 Container（容器）两层，画系统边界与外部系统（MODERNIZATION_BRIEF §2、REIMAGINED_ARCHITECTURE §1 已有）；组件层不用，聚合图不按 C4 画。

## 7. 推迟

| 框架 | 何时再议 | 已知事实 |
|---|---|---|
| DMN | 出现第一张真实决策表时（如 ADR-0016 决定 3 的科目映射），在 DMN 与 D15 版本化配置表 + 表驱动测试之间选，另开 ADR | Flowable 8.0.0（2026-02-27）DMN 解析器只支持 DMN 1.1、1.2、1.3，版本上限 1.3（FIT_REVIEW_VERIFY 5.1） |

## 8. 不采用（ADR-0041）

ArchiMate、ISO/IEC/IEEE 42010、Wardley 地图、Team Topologies、商业模式画布：在架构 SSOT 中没有使用，或与已有规则重复，评审理由见 `proposals/FIT_REVIEW_FRAMEWORKS.md`。REA 只保留 §1 末尾的两个复审问题。若将来系统扩展为多系统、或重新讨论外购总账，再按防漂移规则 2 评审。

## 9. 工程层标准（ADR-0041 决定 5）

### 9.1 合规义务（法律、行政法规、部门规范）

| 项 | 本系统相关要点 | 落点 / 负责 |
|---|---|---|
| 网络安全法（2025 年修正，主席令第六十一号，2026-01-01 施行）第二十三条；GB/T 22239-2019 等保 2.0；GB/T 22240-2020 定级指南 | 实行等级保护；日志留存不少于六个月。**级别上生产前再定**（用户「保持现在」）。已知：公网安〔2020〕1960 号要求新建网络在规划设计阶段定级；定三级则每年测评、测评通过才能投入运行、同步密评；按 GB/T 22240-2020 初判第二级有依据 | 部署方案；用户组织定级评审与备案 |
| 个人信息保护法（2021）+ GB/T 35273-2020 | 与法律同等采纳（用户「保持现在」）。法定要点：金融账户属敏感个人信息（第二十八条）；处理敏感个人信息前须做个人信息保护影响评估，记录至少保存三年（第五十五、五十六条）；定期合规审计（第五十四条，网信办令第 18 号）。另：GB/T 35273 正在修订（2026-06-17 征求意见稿）；敏感个人信息国标 GB/T 45574-2025 | SESSION_PLAYBOOK §14.1；`data-classification.txt` |
| 数据安全法（2021）第二十七条 + 网络数据安全管理条例（国务院令第 790 号，2025-01-01 施行） | 在等保基础上履行数据安全保护义务 | 与等保同一份部署与安全方案 |
| 财会〔2024〕11 号《会计信息化工作规范》、12 号《会计软件基本功能和服务规范》（2025-01-01 施行） | 11 号第四十条按等保落实保护要求；12 号规定会计软件基本功能（不可逆记账、结账检查、操作日志、防重复入账等），第三十七条用到密码技术时遵守密码主管部门规定 | `GL_COMPLIANCE_CHECKLIST.md`；`accounting` |
| 会计档案管理办法（财政部、国家档案局令第 79 号） | 凭证、账簿保存 30 年；只存电子件须满足第八条五项条件，外部电子凭证见第九条 | GL 清单 §4；过账审计 30 年（D-1a-173） |
| GB/T 24589.1-2024 会计核算软件数据接口 第 1 部分：企业（推荐性，2025-07-01 实施） | 审计与监管取数 | 法定账切换（G2）前 |

阶段相关、届时再落：财会〔2020〕6 号与 DA/T 94-2022（电子凭证入账归档，Phase 3）；发票管理办法（国务院令第 764 号修订）与数电票（国家税务总局公告 2024 年第 11 号，Phase 3、4）；个人所得税扣缴（HRM 立项时）；企业内部控制基本规范（财会〔2008〕7 号，非上市企业为参照）。

### 9.2 工程实践

| 项 | 怎么用 | 落点 / 负责 |
|---|---|---|
| OpenAPI | 以 springdoc 实际输出版本（现为 3.1.0）为准，升级随生成工具走 ADR；最新发布为 3.2.1 | CODING_STANDARD CS-4.2、CS-1.10 |
| AsyncAPI 3.0.0 | 只作事件目录的文件格式，不追新版、不引工具链，不声称完全符合规范；守护以 EventCatalogueTest 为准 | `events/asyncapi.yaml` |
| RFC 9457 | 错误响应结构 | CODING_STANDARD |
| RFC 9562（UUID 第 7 版） | 自有表主键；排序用业务时间列，不用 ID | NAMING_STANDARD、§14.1 D16 |
| CycloneDX | 物料清单，不锁版本（取生成插件默认输出），只在发布流程生成；现行 1.7 即 ECMA-424 第 2 版 | SUPPLY_CHAIN S5；one-ops |
| OWASP ASVS 5.0.0 | L2，只取适用章节，一次性对照写进安全不变量，阶段出口抽查新增攻击面；OWASP Top 10:2025 并入此对照，不单列 | `phase1a/SECURITY_INVARIANTS.md` |
| ISO/IEC 25010:2023 | 只作写非功能需求的用语参照，术语先过 `terms.yaml` | — |
| SLSA v1.2 | 不追级别；SUPPLY_CHAIN 写一段现有发布链对照（仓库脚本生成的签名声明只到 Build L1，L2 要求构建平台生成出处证明） | SUPPLY_CHAIN；one-ops |
| DORA | 推迟到上生产后，只取变更失败率、失败部署恢复时间，从部署运行记录取数 | — |

不采用：OpenTelemetry（与 GUIDING_PRINCIPLES §11 O-1 冲突，ADR-0002 与 STACK_DECISION 另开 ADR 修订）；Twelve-Factor、SemVer、Conventional Commits（用户 2026-10-15 未选）。
