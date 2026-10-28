---
编号: "EIP"
题名: "Enterprise Integration Patterns: Designing, Building, and Deploying Messaging Solutions"
作者: ["Gregor Hohpe", "Bobby Woolf"]
年份: "2004（版权年；第二次印刷 2003）"
版本: "第二次印刷，2003 年 12 月（本地版权页）"
资料类型: "书籍"
主题标签: ["集成", "领域建模"]
状态: "已审定"
派工: "ARC-1018-ARC-02"
---

# Hohpe / Woolf《Enterprise Integration Patterns》要点笔记

本笔记保存与 FORCITIS one 相关的阅读要点，不构成新增架构决定。正文为自行概括；英文模式名是来源术语，中文说明不据此确立正式术语。项目取舍和流程对照均为草拟，现行决定优先。

原笔记审定记录：已审定（one-architecture 2026-10-18，页码已抽查）；记录日期：2026-10-17。

## 核心思想

- **论断：消息传递能减弱应用必须同时在线的要求，但会引入交付时间和运行管理问题。** 依据：EIP#集成方式。含义：对照 ADR-0055 和 PRC-184–189、PRC-192–200，先核对每项集成的时效与运行责任，不因选用消息模式就选定消息中间件。
- **论断：消息翻译处理格式、类型、取值及关系语义，通道适配负责连接应用接口与通道。** 依据：EIP#消息翻译、EIP#通道适配、EIP#规范交换模型。含义：按 ADR-0039、CS-2.29 将外部形状隔离在 platform.integration；独立交换模型不作为共享领域模型或公共业务类库的依据。
- **论断：持久保存待交付消息只能抵御交付故障，不能保证接收方已完成业务处理。** 依据：EIP#持久交付、EIP#消息事务协调。含义：按 ADR-0022、ADR-0054 分别验证可靠交付、领域不变量与外部结果，PRC-194 不因收到技术回复就认定 U8C 已审核记账，也不据此引入跨库分布式事务。
- **论断：可靠通道仍可能重复交付，幂等接收必须使重复到达不重复产生业务效果。** 依据：EIP#幂等接收、EIP#关联号。含义：PRC-194 按 ADR-0022 的确定性外部键核对重复处理；请求回复的关联号不能替代业务标识或去重判据，其他集成的去重范围仍按各自契约确认。
- **论断：命令要求接收方执行行为，文档传递数据，事件通知已经发生的变化。** 依据：EIP#消息语义。含义：PRC-196–200 按外部契约区分要求办理、数据交付与结果通知；PRC-193 仍遵守迁入不发布领域事件的要求，不把历史单据套成消息重放。
- **论断：无法交付消息的处置通道和消息运行记录支持故障发现与观察，不能证明业务完成。** 依据：EIP#无法交付消息处置、EIP#消息运行记录。含义：PRC-189、PRC-194 按 DATA_DESIGN_CHECKLIST §14.3 保留失败处置与告警，技术投递记录不能代替记账、金额核对或业务验收证据。

## 概念及页码

PDF 物理页从 1 开始。下列为选读定位范围，不是复刻原书目录。EIP1 正文所列页的物理页 = 印刷页 + 56；已目视核对 PDF 141、411、584 的印刷页 85、355、528，其余范围按抽取标题、目录及偏移定位，仍待独立抽查。

| 锚点 | 概念 | 原文位置 | 印刷页 | PDF 页 | 要点 |
|---|---|---|---|---|---|
| EIP#书目 | 书名、作者、ISBN、版权与印次 | EIP1，题名页 / 版权页 | 无页码 | 7–8 | 书名、作者、ISBN、版权与印次的证据 |
| EIP#集成方式 | 共享数据库、远程调用、消息传递的取舍 | EIP1，第 2 章 Shared Database / Remote Procedure Invocation / Messaging | 47–54 | 103–110 | 集成形式改变时效、数据和执行耦合 |
| EIP#消息翻译 | 翻译及其不同层次 | EIP1，第 3 章 Message Translator / Levels of Transformation | 85–88 | 141–144 | 翻译涉及格式、类型、取值与关系语义 |
| EIP#通道适配 | 适配应用接口与数据 | EIP1，第 4 章 Channel Adapter | 127–128 | 183–184 | 连接既有应用的接口或数据与通道 |
| EIP#持久交付 | 持久保存与故障后交付 | EIP1，第 4 章 Guaranteed Delivery | 122–123 | 178–179 | 持久保存待交付消息以抵御故障 |
| EIP#无法交付消息处置 | 无法交付的处置去向 | EIP1，第 4 章 Dead Letter Channel | 119–120 | 175–176 | 为不能继续交付的消息保留可处置去向 |
| EIP#消息事务协调 | 消息与其他资源的事务协调 | EIP1，第 10 章 Transactional Client | 484–485 | 540–541 | 消息与数据库等资源的事务协调须单独判断 |
| EIP#幂等接收 | 重复到达、去重与操作语义 | EIP1，第 10 章 Idempotent Receiver | 528–530 | 584–586 | 重复到达不重复产生业务效果 |
| EIP#关联号 | 请求与回复关联 | EIP1，第 5 章 Correlation Identifier | 163–164 | 219–220 | 把回复关联到对应请求，不替代业务标识或去重键 |
| EIP#消息语义 | 命令、数据交付与变化通知的语义 | EIP1，第 5 章 Command Message / Document Message / Event Message | 145–148；151–152 | 201–204；207–208 | 命令、数据交付和已发生变化通知须分开 |
| EIP#规范交换模型 | 独立交换格式及其成本 | EIP1，第 8 章 Canonical Data Model | 355–356 | 411–412 | 独立交换格式有间接成本，不等于统一领域模型 |
| EIP#消息运行记录 | 保存消息相关信息用于观察 | EIP1，第 11 章 Message Store | 555–556 | 611–612 | 运行记录支持观察，不直接证明业务完成 |

## 对本系统的启示

### 集成方式先于消息产品

文件、共享数据库、远程调用和消息传递分别改变时效、数据耦合与执行耦合。远程调用跨越故障和延迟边界，不能因为接口形状像本地方法，就按本地调用理解；消息传递减弱同时在线的要求，也带来交付时间和运行管理问题。选择消息模式不等于已经选择消息中间件。（EIP#集成方式） 本系统对照：ADR-0055、PRC-184–189、PRC-192–200。

### 翻译处理语义差异

Message Translator 处理的不只是字段改名：表示格式、类型、取值、时区、实体关系都可能不同。Channel Adapter 则连接既有应用的接口或数据与消息通道；二者职责相关但不同。集成能读到某个字段，不能证明该字段就是本系统同名概念，更不能证明应由本系统取得写入权。（EIP#消息翻译、EIP#通道适配）

Canonical Data Model 通过独立于具体应用的交换格式减少成对翻译的依赖；参与方很少时，这层间接关系可能不划算。它是交换模型的选择，不是本项目采用一个全企业领域对象模型、共享表或公共业务类库的依据。（EIP#规范交换模型） 本系统对照：ADR-0039、CS-2.29、ADR-0033、ADR-0055。

### 交付、事务和业务结果分别判断

Guaranteed Delivery 讨论持久保存待交付消息以抵御消息系统故障；Transactional Client 进一步讨论消息操作与数据库等资源的事务协调。前者不自动保证业务处理完成，后者讨论的跨资源事务也不能直接覆盖本项目的本地事务边界与发件箱决定。可靠交付、领域不变量和外部结果确认仍须分别验证。（EIP#持久交付、EIP#消息事务协调） 本系统对照：ADR-0022、ADR-0054、DATA_DESIGN_CHECKLIST §14.3。

### 重复与关联是不同问题

确认丢失可能导致发送方重发，可靠通道也可能产生重复。Idempotent Receiver 可以通过去重登记或操作语义，让同一消息重复到达不重复产生业务效果；登记保留范围取决于发送方与接收方的契约。Correlation Identifier 则把回复与相应请求关联；它不能替代业务标识或去重判据。（EIP#幂等接收、EIP#关联号）

Command Message 要求接收方执行行为；Document Message 侧重传递数据并由接收方决定如何使用；Event Message 通知已经发生的变化。三者不能只按传输格式区分。某个 HTTP 请求或一张历史单据，不能仅凭采用本书模式就改称领域事件。（EIP#消息语义） 本系统对照：ADR-0022、ADR-0054、PRC-194、PRC-196–200。

### 失败要能被发现和处置

Dead Letter Channel 为不能或不应继续交付的消息保留可处置去向；Message Store 保存消息相关记录以观察异步系统。技术投递记录只能说明处理链上的某个状态，不直接证明凭证已记账、金额已核对或业务已验收；存哪些字段还需服从本项目的数据分级与日志规则。（EIP#无法交付消息处置、EIP#消息运行记录） 本系统对照：DATA_DESIGN_CHECKLIST §14.3、PRC-189、PRC-194。

### 集成流程草案逐条对照

只读对照 [process-catalog.yaml](../../proposals/closeout/process-catalog.yaml) 中全部 `type: integration` 行，共 15 条：PRC-184–189、PRC-192–200。目录这些行均为 `draft`；PRC-190、PRC-191 仍保留目录行但 type 为 retired，不能算入本次集成集合。下表是**模式借鉴建议，已审定（one-architecture 2026-10-18，页码已抽查）**，不改变流程归属、生命期或现行 ADR。

| 流程 | 可借鉴的来源模式 | 与现行决定和证据的边界 |
|---|---|---|
| PRC-184 同步组织来源数据 | Channel Adapter、Message Translator（EIP#通道适配、EIP#消息翻译） | ADR-0055 决定 5–6 已撤运行同步。模式仅可帮助检查确有需要的一次性迁入适配器，不建议新建定时同步。 |
| PRC-185 核对组织快照与来源 | Message Store 的运行记录思路（EIP#消息运行记录） | 日常来源同步用途已撤；迁入核对按 ADR-0036。技术记录不能代替来源与目标的行数、金额和标识核对。 |
| PRC-186 刷新部门简称 | Message Translator（EIP#消息翻译） | ADR-0055 撤 `LegacyOrgNames` 运行用途；若只作迁入输入，来源匹配仍须取证，不沿用老系统部门 ID 等于 A8 ID的假定。 |
| PRC-187 校验致远单点登录票据 | Channel Adapter 的边界隔离思路（EIP#通道适配） | ADR-0055 决定 6 不再按 A8 接入建设；非 A8 合法调用方去留待身份生命周期与调用方研究。消息模式不证明票据安全或仍有调用方。 |
| PRC-188 识别企业微信登录身份 | Channel Adapter、Message Translator 的职责区分（EIP#通道适配、EIP#消息翻译） | 企业微信所需外部身份工作按 ADR-0039、ADR-0055 保留；OAuth 换取主体的请求不能据此改为异步消息处理，具体登录与开通先后仍待研究。 |
| PRC-189 投递企业微信通知 | Guaranteed Delivery、Idempotent Receiver、Dead Letter Channel（EIP#持久交付、EIP#幂等接收、EIP#无法交付消息处置） | 可检查落盘、重试、重复通知与失败处置。目录关于 worker 的描述是草案中的现状记载，本笔记未运行代码；频控与可接受重复按集成登记和实测定。 |
| PRC-192 读取老系统待办 | Channel Adapter 的边界隔离思路（EIP#通道适配） | ADR-0055 决定 6 已撤老待办与合并字段；本系统审批待办保留，不建议再建旧来源适配器。 |
| PRC-193 编排历史数据迁入 | Channel Adapter、Message Translator、Idempotent Receiver 的问题检查（EIP#通道适配、EIP#消息翻译、EIP#幂等接收） | ADR-0036 的暂存、试提交、核对、提交及无副作用要求优先；ADR-0054 调整统一投产路线。迁入不发布领域事件，不发通知或向外推送，不能套成历史消息重放。 |
| PRC-194 推送凭证到U8C | Guaranteed Delivery、Idempotent Receiver、Correlation Identifier、Dead Letter Channel（EIP#持久交付、EIP#幂等接收、EIP#无法交付消息处置、EIP#关联号） | ADR-0022 的确定性外部键、主数据前置、拒收、告警与对账保留，时点按 ADR-0054。收到技术回复与 U8C 已审核记账须区分；不据 Transactional Client 引入跨库分布式事务。 |
| PRC-195 同步U8C主数据 | Message Translator、Idempotent Receiver（EIP#消息翻译、EIP#幂等接收） | 目录方向是 `out`；ADR-0022 已定主数据单向同步为凭证推送前置。目录仍留范围与是否合并的待定点，本笔记不补造字段、对象全集或反向同步。 |
| PRC-196 对接数电发票平台 | Correlation Identifier、Idempotent Receiver、Message Translator（EIP#消息翻译、EIP#幂等接收、EIP#关联号） | 可检查申请与反馈对应及重复处理；目录双向只是草案方向，接口、回调与外部状态语义须另有契约证据。 |
| PRC-197 同步差旅员工与组织 | Message Translator、Idempotent Receiver（EIP#消息翻译、EIP#幂等接收） | 可检查人员、组织标识翻译与重复发送；不把外部服务商名称或取值传入领域模型，不据目录生命期草案认定长期生产依赖已批准。 |
| PRC-198 接收差旅订单与账单 | Document Message / Event Message 的语义区分、Idempotent Receiver（EIP#幂等接收、EIP#消息语义） | 目录明确推送、拉取、文件形态未取证；仅建议在契约确认后区分数据交付与已发生事实通知，不假定现有 Webhook 或消息通道。 |
| PRC-199 推送出差申请并回写审批 | Command Message / Event Message 的语义区分、Correlation Identifier（EIP#关联号、EIP#消息语义） | 可检查要求办理与告知结果及关联关系；业务流程、审批决定与接口步骤仍分工，不据模式确定外部审批拥有业务事实。 |
| PRC-200 拉取企业微信打卡记录 | Channel Adapter、Message Translator、Idempotent Receiver（EIP#通道适配、EIP#消息翻译、EIP#幂等接收） | 可检查分页或重复拉取后的重复记录问题；目录未证明接口已实现，时间窗口、记录键、补拉与考勤落账规则须由契约和业务规则另定。 |

## 我们的取舍

以下均为**已审定（one-architecture 2026-10-18，页码已抽查）**。现行约束仍以已采纳 ADR 和规范为准：

- **建议借鉴：逐个外部契约分开说明适配、语义翻译、去重、关联与失败处置。** 可用本书模式检查遗漏，不把模式名当作实现完成证据。外部名称、标识和取值只在 `platform.integration`，翻译后才交业务模块，是 ADR-0039 决定 1 与 CS-2.29 的现行要求。
- **建议借鉴：按确认丢失和重复到达检验出站结果。** 本书为为什么需要幂等提供解释；本项目的发件箱、可靠投递、超时、重试、死信与告警按 GUIDING_PRINCIPLES §7、DATA_DESIGN_CHECKLIST §14.3，U8C 按 ADR-0022 决定 8 及 ADR-0054 决定 3.4–3.5；本笔记没有另定键、期限或重投权限。
- **建议借鉴：保留来源与自有标识的区别。** ADR-0033 的自有 ID 原则继续有效，但其同步用途已被 ADR-0055 取代。来源对照投产后全表只读，迁入追溯与 U8C 出站使用已确认对照；U8C 编码变化办法仍待另行审定，不能借翻译模式授权改写。
- **不建议：为了套模式引入消息中间件、企业服务总线、共享数据库或公共业务类库。** 本书描述选择，不产生本项目的采纳决定。Canonical Data Model 也不能据此改写已批准的 BC、`api` / `events` 边界或共享内核范围。
- **不建议：以消息集成恢复收入双源或旧生产桥接。** ADR-0024 的收入唯一来源原则保留；切换时点与恢复边界按 ADR-0054。ADR-0055 已撤运行组织同步、老待办与 A8 票据接入，不能借适配器模式把这些重新列为建设任务。

**使用边界：** 本书提供消息集成的模式语言，不证明本企业的事实归属、外部接口能力、接口调用方、合法读取范围或运行可靠性。「对本系统的启示」中的集成流程对照以基线文档为据，没有复核生产、接口或实现；目录与 ADR 不一致之处只作提示，未修改目录。原书的商业中间件、JMS、示例共享模型和跨资源事务均未因本笔记被采纳。

## 附：书目与阅读范围

- 书名：Enterprise Integration Patterns: Designing, Building, and Deploying Messaging Solutions。
- 作者：Gregor Hohpe、Bobby Woolf；出版品牌：Addison-Wesley，版权方 Pearson Education, Inc.。
- 版权年：2004；本地版本版权页明确为第二次印刷，December 2003；未据文件名另定版次。
- ISBN：0-321-20068-3，以版权页为准。本地文件名另带一个 ISBN-13，不作为本笔记书目依据。
- 本地源 EIP1：用户提供的本地 PDF，不进仓库，共 744 个物理页；书名与作者见 PDF 7，版权与印次见 PDF 8（EIP#书目）。
- 按问题选读集成方式、翻译、适配、持久化交付、重复处理、请求关联和运行记录；不表示通读全书或复核全部示例。未运行示例、打开链接或核实书中产品的现行能力。短引：0 处。

## 附：核对记录

原记录：页码抽查（ARC-1018-ARC-04）。

| 抽查项 | 笔记所记位置 | 实际位置 | 结果 |
|---|---|---|---|
| EIP#集成方式 | PDF 103–110，印刷 47–54 | 103 为 Shared Database，107 Remote Procedure Invocation，109 Messaging（印刷 53） | 一致 |
| EIP#消息翻译 | PDF 141–144 | 141 起 Message Translator，含 Levels of Transformation | 一致 |
| EIP#通道适配 | PDF 183–184 | 183 Channel Adapter | 一致 |
| EIP#持久交付 | PDF 178–179 | 178 Guaranteed Delivery | 一致 |
| EIP#无法交付消息处置 | PDF 175–176 | 175 Dead Letter Channel | 一致 |
| EIP#消息事务协调 | PDF 540–541，印刷 484 | 540 页眉印刷 484，Transactional Client | 一致 |
| EIP#幂等接收 | PDF 584–586 | 584 Idempotent Receiver | 一致 |
| EIP#关联号 | PDF 219–220 | 219 Correlation Identifier | 一致 |
| EIP#消息语义 | PDF 201–204；207–208 | 201 Command Message，207 Event Message | 一致 |
| EIP#规范交换模型 | PDF 411–412 | 411 Canonical Data Model | 一致 |
| EIP#消息运行记录 | PDF 611–612 | 611 Message Store | 一致 |

抽查 11 条，偏差 0 条。

## 附：旧索引对照

- EIP-B1 → EIP#书目
- EIP-01 → EIP#集成方式
- EIP-02 → EIP#消息翻译
- EIP-03 → EIP#通道适配
- EIP-04 → EIP#持久交付
- EIP-05 → EIP#无法交付消息处置
- EIP-06 → EIP#消息事务协调
- EIP-07 → EIP#幂等接收
- EIP-08 → EIP#关联号
- EIP-09 → EIP#消息语义
- EIP-10 → EIP#规范交换模型
- EIP-11 → EIP#消息运行记录
