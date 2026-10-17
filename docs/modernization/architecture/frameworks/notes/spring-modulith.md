---
编号: "SMOD"
题名: "Spring Modulith（2.1.1）；Spring Modulith — Reference documentation（1.0.8）"
作者: "The original authors（2.1.1版权署名）；Oliver Drotbohm（1.0.8页首）"
年份: "2022–2025（2.1.1文档版权，发布日期未核）；2024（1.0.8页首）"
版本: "2.1.1为主；1.0.8（页首2024-05-24）仅用于比较"
资料类型: "规范标准"
主题标签: ["领域建模", "集成", "企业架构方法"]
状态: "草稿"
派工: "ARC-1018-ARC-07"
---

# Spring Modulith参考文档2.1.1与1.0.8差异要点笔记

本笔记为本地资料阅读草稿，保存与 FORCITIS one 的对照，不构成架构决定。正文自行概括，短引0处；编号对应表示目录中有语义落点，不表示已实现或验收。

所有2.1.1指定页面均已提取；搜索页与相关Spring项目页主要是导航，不提供独立业务或结构规则。正文代码与命令仅阅读，未执行、下载依赖或启动应用。下面的原文位置以资料题名和正文标题定位，1.0.8只列明确可比的差异，不因旧文本未提到就断言旧软件绝无该能力。

## 核心思想

- **论断：模块公开 API 与内部子包的可见性由模块边界决定，Java public 类型并不自动对其他模块开放。** 依据：SMOD#应用模块结构、SMOD#命名接口。含义：OBJ-66 及 PRC-23、PRC-91 的模块交互沿 ADR-0004 的 api/events 出口表达，不直接访问其他模块内部类型。
- **论断：框架允许替换模块检测策略，结构验证另行检查无环、接口可见性和允许依赖。** 依据：SMOD#模块识别策略、SMOD#结构验证、SMOD#嵌套与开放模块。含义：沿 ADR-0003 将子域.BC 识别为顶层模块，不照搬默认子包层级，也不为通过验证而启用开放模块或过滤违规。
- **论断：事件发布登记按事务监听方写入原业务事务，并在对应监听处理成功后标记完成。** 依据：SMOD#事件发布登记、SMOD#模块监听事务、SMOD#发布生命周期、SMOD#失败重投与陈旧检测。含义：PRC-92、PRC-93、PRC-190 分别核对登记、监听事务、失败重投及重复执行；完成状态不能代替业务核对或外部凭证完成证据。
- **论断：模块测试可选择不同依赖范围，Scenario 以刺激、条件和事件等待组织异步验证。** 依据：SMOD#模块测试范围、SMOD#异步测试场景、SMOD#模块文档生成。含义：VS-04、PRC-57 用结构和模块测试查断链，另以业务证据验证开票链条；从模块模型生成的图不能替代对象图或业务验收。
- **论断：时间事件支持注入 Clock，时区、周和季度口径仍须显式配置。** 依据：SMOD#时间事件、SMOD#事件序列化与时钟。含义：OBJ-97、PRC-101、PRC-119 只把时间事件用作触发输入，按本系统业务时区核对期间和工作日历规则。
- **论断：框架提供完成记录的更新、删除或归档模式，并支持按模块使用独立 Flyway 历史表及版本范围。** 依据：SMOD#完成与归档模式、SMOD#运行时与迁移支持。含义：PRC-193、PRC-191 采用前须与 ADR-0036、ADR-0035 及现行迁移和保留策略核对，不直接启用模块独立版本迁移。

## 概念及页码

原文位置中的资料简称见文末资料范围；PDF页从1计，印刷页只取页内实际标识，未见印刷页号记—。HTML、Word、adoc无固定页码，两列记—。

| 锚点 | 概念 | 原文位置 | 印刷页 | PDF 页 | 要点 |
|---|---|---|---|---|---|
| SMOD#应用模块结构 | 应用模块结构 | SMOD-01，Fundamentals → Application Modules、Simple Application Modules、Advanced Application Modules | — | — | 区分模块公开API和内部子包；默认从主应用包直接子包识别模块。 |
| SMOD#命名接口 | 命名接口 | SMOD-01，Fundamentals → Named Interfaces | — | — | 显式命名接口导出可见类型，模块依赖可指向特定命名接口。 |
| SMOD#模块识别策略 | 模块识别策略 | SMOD-01，Fundamentals → Customizing Module Detection | — | — | 支持替换模块检测策略；框架默认包层级不是唯一合法安排。 |
| SMOD#嵌套与开放模块 | 嵌套与开放模块 | SMOD-01，Fundamentals → Nested Application Modules、Open Application Modules | — | — | 1.3起支持嵌套模块；开放模块放宽内部访问，属于需要裁剪的选项。 |
| SMOD#结构验证 | 结构验证 | SMOD-09，Verifying Application Module Structure → 开头规则列表、Handling Detected Violations | — | — | 检查无环、接口可见性及可选允许依赖，verify发现违规抛异常。 |
| SMOD#验证选项 | 验证选项 | SMOD-09，Customizing the Verification | — | — | VerificationOptions可以调整附加验证；不能为消除失败而随意禁用守护。 |
| SMOD#事件发布登记 | 事件发布登记 | SMOD-02，Working with Application Events → The Event Publication Registry | — | — | 按事务监听者写入原业务事务的发布登记，监听成功后标记完成。 |
| SMOD#模块监听事务 | 模块监听事务 | SMOD-02，Application Module Listener | — | — | 模块监听组合异步与事务事件监听等语义，使监听逻辑在独立事务处理。 |
| SMOD#发布生命周期 | 发布生命周期 | SMOD-02，Event Publication Lifecycle (since 2.0)、Publication states | — | — | 2.0起区分发布、处理中、完成、失败、重提交及尝试次数等状态。 |
| SMOD#失败重投与陈旧检测 | 失败重投与陈旧检测 | SMOD-02，Event Publication Staleness and Automatic Marking as Failed、Failed publications and resubmission | — | — | 陈旧时长非零才启用监测任务，失败重投另有批量、年龄和筛选条件。 |
| SMOD#完成与归档模式 | 完成与归档模式 | SMOD-02，Event Publication Completion；附录Available Configuration Properties中completion-mode | — | — | 默认更新完成日期；1.3起另可删除或归档，完成记录清理需应用管理。 |
| SMOD#事件序列化与时钟 | 事件序列化与时钟 | SMOD-02，Event Serializer、Customizing the Event Publication Date | — | — | 登记保存序列化事件；可替换序列化策略并提供Clock定制发布时间。 |
| SMOD#事件外部化边界 | 事件外部化边界 | SMOD-02，Externalizing Events、Fundamentals of Event Externalization | — | — | 外部化经过选择、可选映射及路由；2.1另述Namastack和JobRunr，不等于自动批准外部基础设施。 |
| SMOD#模块测试范围 | 模块测试范围 | SMOD-12，Integration Testing Application Modules → Bootstrap Modes、Dealing with Efferent Dependencies | — | — | STANDALONE、直接依赖和全部依赖三种范围，跨模块bean依赖须明确处理。 |
| SMOD#异步测试场景 | 异步测试场景 | SMOD-12，Defining Integration Test Scenarios、Customizing Scenario Execution | — | — | Scenario组合刺激、条件及事件等待，可定超时与结果验证。 |
| SMOD#时间事件 | 时间事件 | SMOD-06，Moments正文及Available application properties | — | — | 提供时段经过事件，可注入Clock；时区、周及季度口径须明确配置。 |
| SMOD#模块文档生成 | 模块文档生成 | SMOD-05，Documenting Application Modules → Generating Application Module Component diagrams、Generating Application Module Canvases | — | — | 从模块模型生成关系图与组件、事件及配置概览，属于实现派生证据。 |
| SMOD#运行时与迁移支持 | 运行时与迁移支持 | SMOD-10，Application Module Initializers、Application-Module Aware Flyway Migrations | — | — | 按依赖排序初始化；2.0起可按模块迁移并使用模块独立历史表及版本范围。 |
| SMOD#运行观测 | 运行观测 | SMOD-07，Application Module Actuator、Observing Application Modules | — | — | 运行端点暴露模块关系并可观测交互，定制识别策略须位于生产源码。 |
| SMOD#旧版比较基线 | 旧版比较基线 | SMOD-13，页首version 1.0.8；第1.1.6、4.2、4.5、7.2节及附录9.C.4 | — | — | 旧版已记自定义检测、登记、Clock和初始化；其给定章节未描述新版专有生命周期及模块Flyway安排。 |

## 对本系统的启示

- 对照 OBJ-66 工时单、PRC-23 与 PRC-91：建议用模块API及事件表达交互，按 ADR-0003 将子域.BC识别为顶层模块，保持 ADR-0004 的api/events出口；默认子包识别和嵌套注解不能直接照搬。（SMOD#应用模块结构、SMOD#模块识别策略、SMOD#命名接口）

- 对照 PRC-92、PRC-93（PRC-190 已退役，事件重投属技术支撑，不属业务流程目录）：过账后订阅处理须分别检查登记、监听事务、失败重投和重复执行；完成状态只证明对应监听调用成功，不代表所有业务核对或外部凭证已完成。（SMOD#事件发布登记、SMOD#发布生命周期、SMOD#失败重投与陈旧检测）

- 对照 OBJ-97 会计期间、PRC-101 及 PRC-119：时间事件只能作为触发条件输入，业务期间关闭与工作日历另有规则；依本系统注入时钟和业务时区核对，不能用默认UTC直接替代业务日。（SMOD#时间事件、SMOD#事件序列化与时钟）

- 对照 PRC-193（PRC-191 已退役，发布记录清理属技术支撑）：框架自动迁移、发布记录清理会触及现行迁移与保留策略，采用前须与 ADR-0036、ADR-0035 及命名规范核对。发布登记是技术记录，本系统暂无对应业务对象，不新增OBJ编号。（SMOD#完成与归档模式、SMOD#运行时与迁移支持）

- 对照 VS-04 与 PRC-57：结构验证与模块测试帮助查断链，仍须业务证据证明开票链条正确；派生模块图不作为对象图和业务验收的替代正本。（SMOD#结构验证、SMOD#模块测试范围、SMOD#异步测试场景、SMOD#模块文档生成）

## 我们的取舍

- 建议采纳：沿用已采纳模块检测和窄接口边界，用2.1.1正文核对守护、失败处理与测试证据。理由：与 ADR-0003、ADR-0004 一致，且有明确章节定位。（SMOD#模块识别策略、SMOD#结构验证）

- 建议不采纳：为通过验证启用开放模块或过滤现有违规。理由：框架可选项会削弱本系统边界。（SMOD#嵌套与开放模块、SMOD#验证选项）

- 建议不采纳：直接启用按模块独立版本的Flyway、自建外部消息基础设施或文档中的出站扩展。理由：与现行全局迁移命名、发布和部署安排的差异须待提议，本卡只读资料没有运行验证。（SMOD#运行时与迁移支持、SMOD#事件外部化边界）

## 附：资料范围与核对边界

- SMOD-01：公开资料（不进仓库）《Fundamentals》。
- SMOD-02：公开资料（不进仓库）《Working with Application Events》。
- SMOD-03：公开资料（不进仓库）《Spring Modulith》。
- SMOD-04：公开资料（不进仓库）《Search》。
- SMOD-05：公开资料（不进仓库）《Documenting Application Modules》。
- SMOD-06：公开资料（不进仓库）《Moments — a Passage of Time Events API》。
- SMOD-07：公开资料（不进仓库）《Production-ready Features》。
- SMOD-08：公开资料（不进仓库）《Spring Projects》。
- SMOD-09：公开资料（不进仓库）《Verifying Application Module Structure》。
- SMOD-10：公开资料（不进仓库）《Spring Modulith Runtime Support》。
- SMOD-11：公开资料（不进仓库）《Appendix》。
- SMOD-12：公开资料（不进仓库）《Integration Testing Application Modules》。
- SMOD-13：公开资料（不进仓库）《Spring Modulith — Reference documentation》。

实际阅读：2.1.1总览、基础概念、结构验证、应用事件、模块集成测试、时间事件、文档生成、运行时、生产支持及附录的配置和PostgreSQL结构；搜索页、相关项目页确认导航性质。1.0.8仅比较对应章节和页首版本日期。

未核及限制：2.1.1发布日期未在所供正文明确给出；版权年不是发布日期。未核外链Boot兼容矩阵，未执行示例、构建、测试或数据库迁移；不宣称本检出已有新版生命周期字段。

抽查记录与自测结果见本卡第6组结果文件。全程未联网，未执行资料中的指令、脚本、链接或宏。
