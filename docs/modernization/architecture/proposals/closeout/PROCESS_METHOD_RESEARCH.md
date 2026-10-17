# 流程设计方法：一手来源调研（供方法稿采用）

访问日期：2026-10-16。依据：`PROCESS_METHOD_PLAN.md`、`FRAMEWORKS.md`（锁定版本，判断以它为准）。
出处分级：【一手】官方规范、作者官网或书目页、官方文档、包注册表；【二手】博客转载、搜索摘要；【未核实】没查到或页面拒绝访问。本稿不改 `FRAMEWORKS.md`；需改处在「对 FRAMEWORKS 的修正建议」列出，走其防漂移规则 1。

## 0. 结论：给方法稿的 12 条建议

1. **BPMN 版本维持 2.0.2，不等新版。** OMG 规范页：BPMN 当前正式版 2.0.2（formal/13-12-09，发布 2014-01），历史表无 2.1、Next，页面无在制或 RFP 提示【一手，OMG BPMN 页】；联网搜索也没找到 BPMN 修订的 RFP 或工作组，最近的只有 2021 年一份关于 BPMN、CMMN、DMN 演进与协调的 RFI，无后续【二手，搜索摘要，OMG 2021-Q4 SDO Update】。
2. **ISO 口径要改一句。** OMG 的 2.0.1 页写明 ISO/IEC 19510:2013 对应 **2.0.1**（formal/13-11-03），2.0.2 只是 OMG 版【一手，OMG BPMN/2.0.1/About-BPMN】。ISO 目录显示该标准 2022 年复审确认、仍现行【二手，搜索摘要；iso.org 页面本次抓取为空，未直接核实】。写「遵循 BPMN 2.0.2；ISO 对应版本为 19510:2013（内容为 2.0.1）」，别写「同」。
3. **流程层级与结局状态借 Silver 的 Method and Style，只取描述级规则。** 要点：每层流程的结束事件按「结局状态」命名（形容词短语），只有一个结束事件可不命名；任务后的排他网关出口标该任务的结局；子流程子图的结束事件与父图出口一一对应【二手，Trisotech 转载 Silver 博客，书目页确认第 2 版存在】。
4. **风格规则分「脚本可查」与「评审查」两类**（见第 2 节清单）。脚本部分用 bpmnlint 写自定义规则插件实现（MIT 许可，CLI、Node、浏览器均可，支持插件）【一手，GitHub bpmn-io/bpmnlint】；Silver 自家的校验器内置于 Trisotech 建模器，不可独立脚本化【二手】，不依赖。
5. **事件风暴只借「处理建模」层的积木，不办工作坊。** 本项目已有聚合与事件清单（FRAMEWORKS §1「事件清单推导」已替代工作坊）。可借：策略（policy，「当某事件发生则触发某命令」）、读模型（read model）、外部系统、热点（hotspot，待定题）。四项在 Brandolini 书目录中出现在「Process Modeling Building Blocks」一章【一手，Leanpub 目录；定义正文未取到】。
6. **价值流到流程的交叉映射沿用 BIZBOK 口径，版本记 v15。** BIZBOK Guide 最新为 v15（2026-04-08 发布），v14 为 2025-03；v14、v15 都列「能力与价值映射」为强化项【二手，Guild 新闻稿转载；指南全文仅会员可读，价值流与流程交叉映射的具体做法未核实】。方法稿只写本项目已有的「阶段 ↔ 流程」多对多（目录 `stages` 字段），不引用 BIZBOK 页码。
7. **验收示例由流程路径推导，写进规则卡 Given/When/Then。** 规则：一条路径（开始事件到某个结局状态）至少一条场景；一个判断点的每个出口至少一条；超时与升级分支各一条。方法依据：实例化需求「用示例说明」「协作式细化」两项模式【一手，出版社书目页】；Given/When/Then 的定义与单一 When 的建议见 Fowler 与 Cucumber 文档【一手，martinfowler.com；Cucumber 官方页本次未取到，标二手】。「路径推导示例」是本项目的做法，不是 Adzic 书中原话。
8. **页面用 bpmn-js 只读查看器，固定版本。** 最新稳定 18.31.0（npm，2026-10-01）；建议固定 **18.30.1（2026-09-24）**，已满两周且是上一补丁。构建文件 `dist/bpmn-viewer.production.min.js`（jsDelivr 实测 HTTP 200，约 186 KB）。许可：保留 bpmn.io 水印，不得移除、遮挡【一手，npm、GitHub LICENSE】。
9. **可执行子集只在人工编排流程用，并用「元素白名单」脚本检查。** Flowable 官方构件文档列出的元素是白名单来源（第 7 节）；文档未列出的元素（复杂网关、升级事件、条件事件、链接事件等）一律不得进可执行版，部署测试验证后才放行【一手，Flowable 文档；「未列出即不支持」是推断，须部署验证】。
10. **追溯链以「用户任务」为锚点。** 用户任务带三个属性：候选组（角色，对应权限码）、表单或页面引用、所属命令。Camunda、Flowable 都用用户任务上的扩展属性挂接候选组与表单【一手，Camunda 文档、Flowable 文档】；本项目把候选组改映射到权限码，由脚本核对。
11. **路径覆盖要自己算。** 业界现成工具做的是节点与顺序流覆盖（Camunda 社区的 process test coverage 扩展），Camunda 官方博客建议以流程节点覆盖为底线；全路径覆盖指数增长，工具里没有现成报告【一手，GitHub README、Camunda 博客；「无现成全路径报告」为搜索结论，标未核实】。建议本项目：对描述级图枚举「开始到各结束事件的简单路径（循环只走一次）」，每条路径对应至少一条验收场景。
12. **描述级与可执行版的差异登记，不隐瞒。** 每个可执行版附「与描述级的差异表」（见第 7 节），目录 yaml 的 `human_orchestration` 字段指向两份文件，CI 核对流程 id 一致。

## 对 FRAMEWORKS 的修正建议（待 one-architecture 决定，本稿不改）

| 位置 | 现写法 | 建议 |
|---|---|---|
| §2 BPMN 标题与出处 | 「BPMN 2.0.2（OMG，formal/2013-12-09）」 | OMG 页文号写作 formal/13-12-09，发布 2014-01；文号日期按 OMG 页面写 |
| §2 表格 | 「只用可执行的 Process」「登记由 `.bpmn20.xml` 生成」 | 与计划第 0 节两处 ADR-0050 冲突，随过渡 ADR 改 |
| 核实记录 | 无 BPMN 版本核实 | 加一行：2026-10-16 查 OMG 官网，仍为 2.0.2，无 2.1 / Next 公告 |

## 1. BPMN 版本核实

- **要点**：OMG BPMN 规范页显示 Version 2.0.2、formal、发布 2014 年 1 月；正式版历史为 1.0（2007-03）、1.1（2008-01）、1.2（2009-01）、2.0.2；2.0.1（2013-09）已被取代。页面有「In Progress」入口但本次未见 BPMN 条目。2.0.1 页写明其已作为 ISO/IEC 19510 第一版（2013）发布，文号 formal/13-11-03。【一手：omg.org/spec/BPMN/、/2.0.2/About-BPMN、/2.0.1/About-BPMN，2026-10-16】
- **在制版本**：没找到 BPMN 2.1、Next 的 RFP 或修订工作组。检索只得到 2021 年 RFI（BPMN、CMMN、DMN 演进）一条线索，无后续。【二手，搜索摘要；OMG issues.omg.org 未逐条查，标未核实】
- **ISO**：目录显示 19510:2013 为「已确认（90.93）」，2022 年复审；内容等同 2.0.1。【二手，搜索摘要；iso.org 直接抓取失败】
- **本项目怎么用**：锁定 2.0.2；XML 命名空间仍是 `http://www.omg.org/spec/BPMN/20100524/MODEL`（2.0.x 通用，Flowable 页示例同）。
- **代价**：无。
- **不建议**：不写「BPMN 最新版要求」这类说法，最新版就是 2.0.2，2014 年以来未变；用户说的「最新版本的要求」实际指向画法风格（第 2 节），不是规范版本。

## 2. Bruce Silver《BPMN Method and Style》

书：第 2 版含 BPMN Implementer's Guide，Cody-Cassidy Press，含 100 余张图与 XML 示例；另有《BPMN Quick and Easy Using Method and Style》；官网 methodandstyle.com。【书目页为二手；官网本次未取到】

**方法要点**（均为二手转载，Trisotech 刊 Silver 博客）
- 自顶向下：先画顶层流程，每个子流程再展开一层子图；Level 1 用有限图元，Level 2 才用中间事件与异常处理。
- 结局即结束状态：每个结束事件命名为一个结局（形容词短语，如「已受理」「已驳回」）。
- 判断即任务结局：排他网关紧跟产生结局的任务，出口标签用同一组结局词；两个出口时可用问句加「是 / 否」。
- 规范里「可选」的显示元素在风格里强制：任务类型图标、事件触发类型、消息流、黑盒泳池。
- 描述级模型不用只在可执行模型里有意义的元素。
- 规范校验通过不等于风格校验通过。

**本项目怎么用**：作为「BPMN 画法约定」一页的骨架。我们的结局状态与价值流阶段的「门」（出口条件）对齐，结束事件名取自阶段结局词，不另造。

**可写成脚本检查的规则**（bpmnlint 自定义插件，一条规则一个文件；规则文字是本项目归纳，Silver 公开的完整清单本次没取到，不声称等同其官方清单）：

| 规则 | 脚本可查 | 来源 |
|---|---|---|
| 每个流程恰一个开始事件 | 是 | Silver 风格；bpmnlint 内置 `start-event-required`、`single-blank-start-event` |
| 每个流程至少一个结束事件；多个结束事件须命名 | 是 | 风格；内置 `end-event-required`、`label-required` |
| 排他网关分叉的每条出口有标签；网关本身有问句或前一任务结局 | 是 | 风格；内置 `label-required`、`no-implicit-split`、`conditional-flows` |
| 消息流只连不同泳池的元素；不用跨泳池顺序流 | 是 | 规范约束，bpmnlint 有对应规则，可用 `bpmn-moddle` 自写 |
| 任务标注类型（用户 / 服务 / 手工） | 是（`bpmn:UserTask` 等元素类型是否出现，不是通用 `bpmn:Task`） | 风格；自写规则：禁用通用 Task |
| 子流程子图结束事件与父图出口标签一一对应 | 是（需跨文件，约 50 行脚本） | 风格 |
| 流程、任务命名「动词加名词」，结局用形容词 | 否，只能查「有标签」 | 评审 |
| 分层是否合理、子流程粒度 | 否 | 评审 |

- **代价**：自写规则约 0.5 天；规则集随我们的画法约定固定，不追 Silver 后续修订。
- **不建议**：不买 Trisotech、不依赖其校验器；不照搬书里的 Level 2 全部图元（升级、条件、链接事件）到描述级图。

## 3. 事件风暴（Alberto Brandolini）

- **要点**：Brandolini 2013 年提出；书《Introducing EventStorming》在 Leanpub，完成度 70%，最后更新 2021-08-26，仍有空章。【一手，Leanpub 页】目录按三种粒度分部：大图（Big Picture，含「用事件风暴发现限界上下文」）、流程建模（Process Modelling，含「Building Blocks」：策略、读模型、热点、系统等）、软件设计（Design-Level，含聚合建模与「从纸带到可运行代码」）。官网 eventstorming.com 另按目的分四种用法：改进组织、构想生态、探索新服务、设计关键软件。【一手，官网】
- **与 BPMN 的关系**：书与官网未给出两者的官方对应；我们的对应是自定的：事件风暴的流程建模层产出是「事件—命令—策略」时间线，BPMN 是同一流程的泳池与控制流表达。【本项目归纳，非出处原话】
- **本项目怎么用**（已有聚合与事件清单为前提）：

| 事件风暴元素 | 借用方式 |
|---|---|
| 策略（当事件发生则触发命令） | 对应「事件策略」类流程（目录 `event_policy`），无 BPMN 文件，在目录 yaml 写触发事件与目标命令 |
| 读模型 | 对应 `read_model` 类，写清读谁、供哪个页面 |
| 外部系统 | BPMN 里画黑盒泳池，消息流连接 |
| 热点 | 方法稿的「待定题」栏，沿用样板页已有的设计点与待定题 |
| 聚合、命令、事件 | 已有，不重做 |

- **代价**：只借词汇，无工作坊成本。
- **不建议**：不开大图与设计级工作坊（FRAMEWORKS §1 已决定以事件清单推导取代）；不引用书中未完成章节作规则依据。

## 4. BIZBOK Guide（Business Architecture Guild）

- **版本**：最新 v15（2026-04-08）；v14（2025-03-26）；v13（2024-03）；v12（2023-04）。Guide 对会员免费，全文不公开。【二手，新闻稿转载；Guild 官网抓取被 403】
- **要点**（公开新闻稿可见）：v14 强化「能力与价值映射」，并与精益六西格玛重合；v15 称「澄清措辞、使用场景与 Guild 对齐的交叉映射」，更新产品映射、参考模型、应用组合管理。价值流与业务流程交叉映射的具体矩阵形式，公开材料没有，**未核实**。
- **本项目怎么用**：不直接依赖 BIZBOK 的具体做法。我们已有价值流 v2 与能力图；方法稿只落两个映射：阶段 ↔ 流程（目录 `stages`）与能力 ↔ 阶段（已有），并生成「阶段覆盖」检查：每个阶段至少一个业务流程，每个业务流程至少属于一个阶段。
- **代价**：引用 BIZBOK 需会员读全文；若要写进规范须先由有访问权的人核对。
- **不建议**：不在 `FRAMEWORKS.md` 把 BIZBOK 升为锁定框架（它不在 ADR-0041 的采用清单内），只作术语参照；不引 v15 页码。

## 5. 实例化需求与 BDD / Given-When-Then

- **要点**：Adzic《Specification by Example》（Manning，2011）提七项模式，其中关键过程模式含：从目标推导范围、协作式规格、用示例说明、细化规格；目标是「活文档」。【一手，出版社 O'Reilly 书目页；正文章节细节为二手摘要】Given/When/Then 由 Dan North 与 Chris Matts 提出；Fowler 页说明 Given=前置状态，When=被规定的行为，Then=预期变化。Cucumber 的 Gherkin 建议 Given 不写用户操作，每个场景一个 When。【Fowler 页一手；Cucumber 为二手；North 原文本次未取到】
- **本项目怎么用**：规则卡已是 Given/When/Then，方法稿加「路径到场景」约定：

| 流程元素 | 场景写法 |
|---|---|
| 开始事件 | Given 的初始状态与触发 |
| 用户任务 | When 一个命令（单一 When） |
| 判断点出口 | 每个出口一条场景，Then 写结局状态与领域事件 |
| 边界计时事件 | Given 时钟已过期限，Then 升级结果 |
| 结束事件 | Then 的结局状态名（与 BPMN 结束事件同名，可脚本核对） |

- **代价**：场景数随路径增长；用「每个出口一条加每个结束事件一条」控制。
- **不建议**：不引入 Cucumber 与 Gherkin 文件工具链（本项目测试用 JUnit，CS-5.5 / 5.6）；不要求把全部示例都写成自动化。

## 6. bpmn-js

- **版本**：`latest` = **18.31.0**（2026-10-01 09:57 UTC）；近期 18.30.1（2026-09-24）、18.30.0（2026-09-23）、18.29.1、18.29.0（2026-09-21）。另有 `next` 为 10.0.0-alpha.1（dist-tag 顺序异常，疑为另一线预发布，不用）。【一手，registry.npmjs.org，实测】
- **构建文件**（jsDelivr 目录 API 实测）：`dist/bpmn-viewer.production.min.js`（185,955 字节）、`dist/bpmn-navigated-viewer.production.min.js`（194,602，带拖拽缩放）、`dist/bpmn-modeler.production.min.js`（571,401）；样式在 `dist/assets/`。`https://cdn.jsdelivr.net/npm/bpmn-js@18.30.1/dist/bpmn-viewer.production.min.js` 的 18.31.0 同路径返回 200；18.30.1 同路径同样返回 200（2026-10-16 实测）。【一手】
- **许可**：npm 元数据只写「SEE LICENSE IN LICENSE」；LICENSE 要求水印代码（链到 bpmn.io）不得移除或改动，运行在网站或应用中时水印须完全可见、不被其他元素遮挡；其余按 MIT 类条款。【一手，GitHub LICENSE；MIT 类字样未逐字核】
- **只读最小用法**（按 bpmn.io 公开用法，本稿未在浏览器实跑，发布样板页前须实跑）：
  1. `<script src=".../bpmn-viewer.production.min.js">`，页面放一个有高度的容器。
  2. `const v = new BpmnJS({ container: '#c' });`
  3. `await v.importXML(xmlString);` 然后 `v.get('canvas').zoom('fit-viewport');`
  4. 点击元素用 `v.get('eventBus').on('element.click', e => ...)`，`e.element.id` 对应 BPMN 元素 id，可挂「每一步在哪发生」标注。
- **本项目怎么用**：样板页内联 `.bpmn` XML 或同目录取文件，只读；固定 18.30.1，写 SRI 哈希。页面须带系统字体栈（不引外部字体）。
- **代价**：脚本约 186 KB；必须保留水印；`importXML` 要求 BPMNDI 图形信息齐全，缺则不显示。
- **不建议**：不用 modeler 构建（体积 3 倍，且会引入编辑能力）；不跟 `latest` 标签。

## 7. Flowable 8 的 BPMN 可执行子集

出处：Flowable 官方文档「BPMN 2.0 Constructs」（flowable.com/open-source/docs/bpmn/ch07b-BPMN-Constructs，2026-10-16 抓取全文检索）；Flowable 8.0.0 于 2026-02-27 发布，改 Spring Boot 4、Spring 7、Jackson 3，移除 JUnit 3 与 4 支持【一手，GitHub 发布说明与论坛公告；BPMN 元素本身无变更说明】。官方文档页自述「Flowable 支持的 BPMN 2.0 构件，以及对标准的自定义扩展」，页面未标版本号。

| 类别 | 文档明列支持 | 备注 |
|---|---|---|
| 任务 | 用户、脚本、Java 服务、Web 服务、业务规则（Drools）、邮件、Http、Camel、手工（直通，引擎视作自动完成）、接收（只实现 Java 语义）、调用活动 | 用户任务有 assignee、候选用户、候选组；多实例适用于任务、子流程、调用活动，不适用网关与事件 |
| 开始事件 | 空、计时、消息、信号、错误（只能触发事件子流程） | 消息开始只支持顶层流程，嵌入子流程不支持；子流程只能空开始 |
| 中间事件 | 捕获：计时、信号、消息；抛出：空、信号、补偿 | |
| 结束事件 | 空、错误、终止、取消（仅事务子流程） | |
| 边界事件 | 计时（中断与非中断）、错误、信号、消息（中断与非中断）、取消、补偿 | 补偿不支持嵌入子流程；边界事件多条出口为已知问题，改用一条出口接并行网关 |
| 网关 | 排他、并行、包容、事件网关 | 事件网关后须接中间捕获事件，不支持接收任务；至少两条出口 |
| 子流程 | 嵌入、调用活动、事件子流程、事务子流程 | 计时开始不得出现在子流程 |
| 数据对象 | 支持，仅基础 XSD 类型（string、boolean、datetime、double 等） | 不能当「聚合」用，只作流程变量声明 |

- **文档未列出（视为不支持，部署验证后才可放行）**：复杂网关、升级事件、条件事件、链接事件、编排（Choreography）与会话（Conversation）图、泳池间消息流的运行语义。【推断，未实跑】
- **描述级转可执行版的常见差异**：
  1. 泳池：描述级多泳池（外部方黑盒）；可执行版只留本系统一个参与者，外部交互改为消息事件或服务任务。
  2. 服务任务：描述级「外部系统步骤」在可执行版变为发送任务或服务任务加回调消息。
  3. 手工任务：描述级线下步骤，可执行版是直通，需要人工确认的改用户任务。
  4. 数据对象与数据存储：描述级表示读写聚合；可执行版仅流程变量，不连数据库。
  5. 复杂网关、升级、条件事件：改写为排他加边界计时。
  6. 命名空间：加 `flowable:` 扩展用于表单、候选组；标准 `bpmn:` 之外的扩展不入描述级文件。
- **本项目怎么用**：元素白名单脚本，输入 `.bpmn20.xml`，输出越界元素；差异表放在可执行版文件头注释外的同目录说明里（不写代码注释，CS-1.1）。
- **代价**：双份文件需保持一致，靠 CI 核对流程 id 与用户任务 id。
- **不建议**：不用业务规则任务（Drools）、不用脚本任务做业务计算（GUIDING_PRINCIPLES §4）；不用 Flowable 的邮件、Camel 任务。

## 8. 流程到下游的追溯做法

| 方向 | 业界做法（有出处的） | 本项目采用 |
|---|---|---|
| 任务到角色到权限 | BPMN 规范中泳道表示角色或组织单元（规范语义）；Flowable、Camunda 在用户任务上挂 assignee、候选用户、候选组【一手，Flowable 文档、Camunda 文档】。Camunda 文档提醒候选组本身不控制可见性，需另配授权【一手】 | 候选组名取自权限码或角色码，脚本核对：每个用户任务有候选组，候选组在权限清单中存在；可见性仍由权限中间件裁决，不靠候选组 |
| 用户任务到表单或页面 | Camunda 在用户任务上用 `zeebe:formDefinition`（`formId` 引用表单、`externalReference` 引用自定义表单或路由）【一手】；Flowable 用 `flowable:formKey` | 用户任务上挂「页面编号」属性（我们自己的扩展或命名约定，不进描述级文件，写入目录 yaml 的任务清单）；每个用户任务对应一页或一个操作 |
| 路径到测试 | Camunda 社区 process test coverage 扩展：节点与顺序流覆盖、HTML 报告；Camunda 博客建议以流程节点覆盖为底线【一手】。全路径覆盖在文献中被认为组合爆炸，常用折中是质数路径覆盖【二手，arXiv 论文摘要】 | 见建议 11：简单路径枚举加场景；脚本核对「每个结束事件至少一条场景」 |
| 数据对象到表 | BPMN 数据对象与数据存储用于表示流程读写的数据（规范语义）；没找到「数据对象自动导出表」的成熟方法论。【无出处，未核实】 | 流程不导出表。任务旁的数据对象写聚合名，脚本核对聚合存在于数据访问注册表；表由聚合与 DDL 规则定（§14.1），不由流程推导 |
| 反向（每个命令至少被一个流程使用） | 没找到成熟标准做法。【无出处】 | 本项目自建：`closeout_check.py` 扩展，命令出现在至少一个流程步骤或事件策略 |

- **结论**：任务到角色与表单有产品级先例，路径到测试有节点覆盖先例，数据到表没有先例；下游追溯主要靠本项目目录 yaml 加脚本，不指望外部工具成套提供。
- **代价**：目录 yaml 的任务清单要手写（页面编号、权限码、命令）；这是追溯链的主要维护量，估计每个业务流程多 10–20 行。
- **不建议**：不从 BPMN 自动生成页面或表；不引入追溯管理工具（Jama、Polarion 等，未调研，不在范围）。

## 9. 未核实与待办

| 项 | 状态 | 谁补 |
|---|---|---|
| ISO 页面状态与 OMG in-progress 页 | iso.org 与 omg.org/schedule 抓取为空，结论取自 OMG 版本页与搜索摘要 | 浏览器人工看一眼 |
| Silver 完整风格规则清单、methodandstyle.com | 官网未取到 | 采购书或查官网后补第 2 节表 |
| BIZBOK v15 价值流与流程交叉映射章节 | 会员可读 | 有会员账号者核对 |
| Brandolini 书中 policy、read model 定义原文 | 目录可见，正文未取 | 取 Leanpub 样章 |
| bpmn-js 只读用法实跑；18.30.1 构建文件 200 | 本稿未在浏览器实跑 | 样板页开发时验证 |
| Flowable 对复杂网关等的实际行为 | 文档未列，未部署验证 | 可执行样板部署测试 |
| 调研所用的搜索工具仅限美国地区检索，部分中文与欧洲来源可能漏查 | — | — |

## 10. 出处清单

- OMG BPMN：https://www.omg.org/spec/BPMN/ 、https://www.omg.org/spec/BPMN/2.0.2/About-BPMN 、https://www.omg.org/spec/BPMN/2.0.1/About-BPMN （2026-10-16）
- ISO/IEC 19510:2013：https://www.iso.org/standard/62652.html （抓取为空，状态取自搜索摘要，未直接核实）
- Silver：Trisotech 转载博客 https://www.trisotech.com/bpmn-style-rules/ 、https://www.trisotech.com/standardizing-bpmn-labels/ 、https://www.trisotech.com/what-is-method-and-style/ ；官网 methodandstyle.com（未取到）
- Brandolini：https://leanpub.com/introducing_eventstorming 、https://www.eventstorming.com/
- BIZBOK v15 新闻稿（2026-04-08，转载）：digital-release.kxan.com/business/press-releases/cision/20260408SF29398/business-architecture-guild-releases-bizbok-guide-v15
- Adzic：https://oreilly.com/library/view/specification-by-example/9781617290084 ；Fowler：https://martinfowler.com/bliki/GivenWhenThen.html
- bpmn-js：https://registry.npmjs.org/bpmn-js ；https://github.com/bpmn-io/bpmn-js （LICENSE）；https://data.jsdelivr.com/v1/packages/npm/bpmn-js@18.31.0
- bpmnlint：https://github.com/bpmn-io/bpmnlint
- Flowable：https://www.flowable.com/open-source/docs/bpmn/ch07b-BPMN-Constructs ；https://github.com/flowable/flowable-engine/releases ；https://forum.flowable.org/t/flowable-8-0-0-release/12548
- Camunda：https://docs.camunda.io/docs/components/modeler/bpmn/user-tasks/ ；https://github.com/camunda-community-hub/camunda-bpm-process-test-coverage ；https://camunda.com/blog/2015/08/process-test-coverage-report/
