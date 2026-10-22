# 工程层标准适配评审（对抗式，2026-10-15）

状态：历史记录｜2026-10-15｜负责：one-architecture

起因：用户 2026-10-15「关于架构的任何选择都要非常谨慎，必须深入研究后才能决策」（intent `2026-10-15-193135`）。评审：opus 子代理（architecture-critic，只读、未联网），one-architecture 抽查核实（见文末）。外部事实（版本、条款、文号）待联网核实，核实前不作硬依据（FRAMEWORKS 防漂移规则 3）。结论待用户在确认页重新定；在用户决定前，FRAMEWORKS §9 不改。

评审对象：`architecture/FRAMEWORKS.md` §9 的 13 项，盘点 `proposals/ENGINEERING_STANDARDS.md`。

## 汇总

| # | 项 | 结论 | 把握 | 一句理由 |
|---|---|---|---|---|
| 1 | OpenAPI 3.1.x | 保留；写成「以 springdoc 实际输出的 3.1.x 为准，升级随生成工具走 ADR」 | 高 | 已在用：springdoc 3.1.1、CI 契约比对、前端 openapi-typescript 生成客户端 |
| 2 | AsyncAPI 3.0.0 | 降级：只作事件目录文件格式，不追 3.1、不引工具链；补官方 schema 校验或不再声称「符合」 | 中 | 事件在单体进程内流转，无中间件；EventCatalogueTest 只校验与代码一致，未按 AsyncAPI 规范校验；大量自定义 `x-event` |
| 3 | RFC 9457 | 保留 | 高 | `platform/web` ProblemDetail 已实现 |
| 4 | RFC 9562 UUIDv7 | 保留；CODING_STANDARD 加「排序用业务时间列，不用 ID」 | 高 | `kernel/Uuids.java` 已实现；同一毫秒内无定义顺序 |
| 5 | CycloneDX 1.7 | 降级：保留 S5，不锁 1.7，用生成插件默认输出（不低于 1.5），只在 release.yml 生成 | 中 | 无外部使用方，osv-scanner 已覆盖漏洞查询；「1.7 = ECMA-424」存疑 |
| 6 | OpenTelemetry | 去掉；另开 ADR 把 ADR-0002 与 STACK_DECISION 的「OpenTelemetry」改到与 O-1 一致 | 高 | 与 GUIDING_PRINCIPLES §11 O-1「不引入 traceId 与分布式追踪库」冲突；pom 无 OTel |
| 7 | OWASP ASVS 5.0 | 降级：L2、只取适用章节，一次性对照写进 `phase1a/SECURITY_INVARIANTS.md`，阶段出口抽查新增攻击面 | 中 | 总账 + 薪资 + 银行卡，L2 合理；逐条逐阶段全量走不现实 |
| 8 | OWASP Top 10:2025 | 不作独立标准，并入 ASVS 对照（可按 Top 10 分组） | 中高 | Top 10 是认知文档，与 ASVS 重复 |
| 9 | ISO/IEC 25010:2023 | 降级：只作写非功能需求的用语参照，不进阶段出口评审 | 中 | GUIDING_PRINCIPLES §7a、§11 O-5 已有可量化预算 |
| 10 | 等保 2.0（GB/T 22239-2019） | 保留并改时点：现在按 GB/T 22240-2020 拟定第二级、启动定级备案，不等「上生产前」 | 义务高；级别中低 | 网络安全法第二十一条、财会〔2024〕11 号第 39–41 条；Phase 1a 出口即全员生产登录 |
| 11 | 个人信息保护法 + GB/T 35273-2020 | 法律保留并加触发点：银行卡、薪资、证件号首次入库前完成个人信息保护影响评估并经用户签认；35273 降为参照 | 法高；国标降级中 | 自建 HRM、算薪迁入、银行卡进新系统（INTENT H-3、M-5）；35273 多针对面向公众的产品 |
| 12 | SLSA v1.2 | 降级：SUPPLY_CHAIN 写一段「现有发布链对照 Build L2」，不追级别、不加 Source Track、不引生成器 | 中 | release.yml 托管 runner + 独立签名作业 + 签名声明已近 Build L2；真缺口是 S6 自托管 runner，SLSA 管不到 |
| 13 | DORA | 推迟到上生产后；届时只取变更失败率、失败部署恢复时间，从 deploy 运行记录取数 | 中 | 尚无生产部署；团队对标工具，现在的数字无决策用途 |

## 结构性问题

- **S-1 §9 漏掉本系统唯一有行业约束力的软件规范。** 财会〔2024〕11 号、12 号在盘点里列为「已在用」却未进 §9；`GL_COMPLIANCE_CHECKLIST.md` 里的《会计档案管理办法》（财政部 国家档案局令第 79 号）、GB/T 24589.1-2024 也未进。对要做法定账的系统，比 #7–#9、#12、#13 加起来更有约束力。
- **S-2 落点不存在。** §9 写的「安全复审清单」「度量方案」仓库中没有对应文件；阶段出口评审也没有 25010 栏。没有落点、负责会话、守护的标准是摆设。
- **S-3 OpenTelemetry 前后不一致。** `STACK_DECISION.md:49`、`adr/src/ADR-0002.md:27` 写「Micrometer + OpenTelemetry → ARMS / SLS」；`GUIDING_PRINCIPLES.md:233`（§11 O-1，用户 2026-10-15「全部按推荐」）写不引入追踪库；`backend/pom.xml` 只有 `micrometer-registry-prometheus`。

建议 §9 重排：本系统的合规义务清单（网络安全法与等保、个人信息保护法、数据安全法、财会〔2024〕11/12 号、会计档案管理办法）在前，可选实践在后，每项写明落点与负责会话。

## 逐项要点

1. **OpenAPI**：`backend/pom.xml:18` springdoc 3.1.1；CI「OpenAPI contract unchanged」；`frontend/package.json:24` openapi-typescript；CS-4.2、CS-1.10 以它为准。代价只有补版本号。
2. **AsyncAPI**：`events/asyncapi.yaml` 由 `build_asyncapi.py` 合成；STACK_DECISION 定「不保留 RocketMQ」，`address` 只是类名；`EventCatalogueTest.java:121-126` 只校验合成一致、版本字段、目录与代码一致。事件目录的价值来自自定义守护而非 AsyncAPI；Phase 3 出站投递时才可能用到 AsyncAPI 语义。真要「锁定」须引官方解析器（新 Node 依赖，走 SUPPLY_CHAIN S1）。
3. **RFC 9457**：`platform/web/package-info.java`、`ProblemDetails.java`、`OpenApiProblems.java`。小决定：`type` 用 `about:blank`、可解析 URI 还是 `urn:` 错误码。
4. **RFC 9562**：`kernel/Uuids.java`、`kernel/TimeOrderedIds.java`；§14.1 D16 用户决定。
5. **CycloneDX**：代价含 cyclonedx-maven-plugin、前端 SBOM 工具（S1 与 7 天冷却）、release.yml 一步、改 `scripts/release/statement.sh` 与 `verify_bundle.sh` 及其自测；强锁 1.7 可能要配置或升级插件。收益「某已部署版本是否含某组件」，对已打标签提交跑 osv-scanner（S3）也能答。
6. **OpenTelemetry**：O-1 定 correlationId 为唯一追踪键；`proposals/PHASE1A_PLATFORM_CONVENTIONS.md:8`「不再承诺 traceId」；指标走 Prometheus（O-4），日志 ECS JSON 到 SLS（CS-6.1）。照 §9 做须推翻 O-1，引入 SDK / agent、导出端、在 Modulith 事件发布登记表传播上下文。进程内用 Micrometer Observation，出站 correlationId 已覆盖。
7. **ASVS**：风险画像重（总账、法定账 G2、HRM 算薪、银行卡 INTENT.md:97、老系统 CWE-306 等 8 项）。很多章节不适用（本系统是 OAuth 客户端而非授权服务器）。与 §11 O-3、CS-6.2、ADR-0009 部分重叠。
8. **Top 10**：每类都能在 ASVS 中找到对应。
9. **25010**：逐特性评审是没有阈值的主观评分；可检查的阈值在 §7a R-P1–R-P6、§11 O-5、brief 各阶段出口。用其术语也要先过 terms.yaml。
10. **等保**：流程为定级（自定 + 专家评审）→ 二级及以上向属地公安备案 → 测评 → 整改。二级控制（身份鉴别、访问控制、安全审计、入侵防范、完整性与保密性、备份恢复）现有设计大多覆盖（ADR-0009 默认拒绝、`platform.audit` 只追加、应急账号 TOTP、§11 O-8 日志 180 天）。三级额外：更严审计保护，且可能触发商用密码应用安全性评估。法定账后财务、薪资、全员个人信息集中，专家评审可能给三级——控制按「二级做足、三级差距可补不重写」设计。测评、备案需外部机构与用户本人出面。
11. **个人信息保护法**：第十三条（含人力资源管理所必需）、第十七条、第十九条、第二十八 / 二十九条（金融账户属敏感个人信息；单独同意与人力资源管理例外的衔接须法务意见）、第五十一条、第五十四条、第五十五 / 五十六条（影响评估，记录保存至少三年）。现有：`backend/architecture/data-classification.txt` 分级、CS-6.2 日志只记 ID、§14.1 D13、D17。产物：一份影响评估（首个敏感处理阶段前），员工告知文本（人事出）。35273 只用去标识化、访问控制、记录留存几节。
12. **SLSA**：`release.yml` GitHub 托管 ubuntu-24.04 每次新虚拟机；签名在独立作业，密钥在 `release` 环境只允许 main；`scripts/release/statement.sh:39-47` 声明含产物摘要、提交、工作流引用、运行号并签名；`ci_passed.py` 证明提交在 main 且 CI 通过。差别主要在格式（in-toto）。
13. **DORA**：部署为手动 workflow_dispatch + 人工确认；拿交付速度当指标会给会话「求快」压力。

## 漏掉的标准与法规（只列相关的）

| # | 项 | 为什么相关 | 落点建议 |
|---|---|---|---|
| 1 | 财会〔2024〕12 号《会计软件基本功能和服务规范》、11 号《会计信息化工作规范》 | 总账与法定账；不可逆记账、结账检查、操作日志、防重复入账（GL 清单 §5） | 进 §9 首位，落点 GL_COMPLIANCE_CHECKLIST |
| 2 | 个人信息保护合规审计管理办法（国家网信办） | 个人信息保护法第五十四条细则 | 并入第 11 项 |
| 3 | 数据安全法（2021）+ 网络数据安全管理条例 | 数据安全法第二十七条要求在等保基础上履行数据安全义务 | 与等保同一份部署与安全方案 |
| 4 | 会计档案管理办法（令第 79 号） | 凭证账簿保存 30 年；电子会计档案单套保存六项条件；对应 D-1a-173 过账审计 30 年 | 进 §9（GL 清单 §4 已有） |
| 5 | 财会〔2020〕6 号电子会计凭证报销入账归档；DA/T 94-2022 电子会计档案管理规范 | Phase 3 差旅报销电子凭证入账归档 | Phase 3 入口条件、`accounting` 归档方案 |
| 6 | 密码法 + GB/T 39786-2021 | 财会〔2024〕12 号第 37 条；定三级或做电子签名验签时触发 | 等保定级后再定 |
| 7 | 电子签名法 + 财政部电子凭证会计数据标准 | 接收发票、银行回单等电子原始凭证并验签（GL 清单第 6、17、18 条） | G2 前，随 `accounting` |
| 8 | 发票管理办法与数电票 | 开票（Phase 4）、差旅发票（Phase 3） | Phase 3、4 入口规则抽取 |
| 9 | GB/T 24589.1-2024 会计核算软件数据接口 第 1 部分：企业 | 审计与监管取数；GL 清单建议作 G2 前提 | 进 §9（推荐性，G2 前） |
| 10 | 个人所得税法扣缴义务 | 算薪迁入后产出扣缴数据，是否对接扣缴申报未讨论 | HRM 阶段立项时作待决题 |

不建议加入：ISO/IEC 27001（认证型体系，偏重，等保已覆盖）、XBRL（GL 清单排在 G2 之后）、Twelve-Factor、SemVer、Conventional Commits（用户已不纳入，未发现推翻理由）。

## 未核实（待联网查一手来源）

1. 全部 URL 本次未打开。
2. ECMA-424 哪一版对应 CycloneDX 1.7（评审者记忆：第 1 版对应 1.6）。
3. ASVS 5.0.0 章节数、条目数、L1/L2/L3 原文定义。
4. OWASP Top 10:2025 发布日期与类别名。
5. ISO/IEC 25010:2023 九个特性名称与 ISO 页面编号。
6. SLSA v1.2 是否含 Source Track、Build L1–L3 原文。
7. DORA「五项」与「部署返工率」官方表述。
8. 网络安全法 2025 年修正与施行日期；等保二级、三级测评频率出处；三级是否强制商用密码应用安全性评估。
9. GB/T 35273-2020 是否在修订；敏感个人信息相关配套国标编号。
10. 个人信息保护合规审计管理办法令号、施行日期、门槛人数。
11. 网络数据安全管理条例令号与施行日期。
12. DA/T 94-2022、财会〔2020〕6 号日期；发票管理办法修订文号；数电票推广公告号。
13. 拟定「第二级」是基于系统画像的推测，须经 GB/T 22240-2020 定级流程确认。
14. 个人信息保护法第二十九条单独同意与第十三条人力资源管理例外的衔接，需法务意见。

## one-architecture 抽查（2026-10-15）

- `GUIDING_PRINCIPLES.md:233` 原文「关联号 `correlationId` 是唯一的追踪键，不引入 `traceId` 与分布式追踪库」，S-3 属实。
- `STACK_DECISION.md:49` 原文「Micrometer + OpenTelemetry → ARMS / SLS」，冲突属实。
- `backend/pom.xml:36` 只有 `micrometer-registry-prometheus`，无 OTel 依赖，属实。
- `events/asyncapi.yaml` 中 `x-event` 出现 60 处，属实。
