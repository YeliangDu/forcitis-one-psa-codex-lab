---
编号: SIL2
题名: "The Data Model Resource Book, Revised Edition, Volume 2: A Library of Universal Data Models by Industry Types"
作者: ["Len Silverston"]
年份: "2001"
版本: "修订版，第 2 卷；epub 核读"
资料类型: 书籍
主题标签: [数据建模, 人员与资源, 度量与BI]
状态: 草稿
派工: ARC-1018-ARC-13
---

# Silverston《The Data Model Resource Book》第 2 卷要点笔记

本次完整核读 epub 第 7 章 Professional Services 正文、图 7.1–7.13，并用附录 F 的实体与属性清单补核。正文为自行概括，短引 0 处；英文实体标识和小节名用于检索定位。第 7 章以外的行业模型未纳入本笔记。沿用的 7 个 SIL2 锚点全部保留；旧扫描页位置仅存于「附：旧定位对照」。本次改写及对照为待审建议，不沿用旧版的审定结论。

## 核心思想

- **论断：专业服务中，主体身份、交易角色与双方关系应分别表达。** 依据：SIL2#主体角色关系、SIL2#人员、SIL2#客户、SIL2#付款客户、SIL2#接收服务客户。含义：核对 OBJ-01、OBJ-02、OBJ-03、OBJ-54、OBJ-56 能否区分签约方、付款方、服务接收方及专业人员，不能把外部专业人员一律视为我方员工。
- **论断：服务产品定义可复用的供给，需求及委托条目表达一次具体需要与承诺。** 依据：SIL2#服务内容与裁剪、SIL2#需求、SIL2#专业服务请求、SIL2#报价单、SIL2#委托事项。含义：OBJ-24 不替代 OBJ-18、OBJ-21、OBJ-06；技能目录虽已有 OBJ-56 的参考数据入口，产品所需技能与需求中的年限、等级约束仍须核验。
- **论断：购买指定人员时间与购买服务成果具有不同责任，承诺的拆分也不必等于执行工作的拆分。** 依据：SIL2#专业人员派驻、SIL2#委托工作履行、SIL2#工作投入。含义：核对 OBJ-05、OBJ-06 到 OBJ-29、OBJ-30 的履行关系；不能把 Engagement 直接等同项目或会计履约义务，也不能仅因书中有多对多关系就改变现有 WBS 挂接规则。
- **论断：协议统辖委托，实际服务记录与计费分配另行留证，几层金额不能互相替代。** 依据：SIL2#协议与服务履行、SIL2#服务记录、SIL2#服务记录计费、SIL2#工作投入计费。含义：以 OBJ-05、OBJ-66、OBJ-35、OBJ-88、OBJ-82 分别核对约定、投入、送达、费用与请款；开票和收入确认仍依各自业务事实，不由工时金额自动推出。
- **论断：工时星型模型的维度组合粒度与审批工时的原子粒度不同，成本和已计费工时也各有口径。** 依据：SIL2#工时分析、SIL2#工时事实、SIL2#计费小时、SIL2#专业人员成本。含义：总线矩阵工时行应先声明如何从审批记录筛选、转换并归集到六维组合；不将 hours_billed 当作词表「可计费工时」，不将向人员支付的成本当作标准费率计算的「直接人工」。
- **论断：书中名为 gross_margin 的量是成本除以账单金额，不能作为本系统贡献毛利率。** 依据：SIL2#原文成本账单比。含义：沿用 terms.yaml 的「贡献毛利率」及其同层级净服务收入分母，按 BI_SCOPE 的口径治理处理差异；若需成本账单比，先提议独立指标，不能用同名替换。

## 概念及页码

原文位置均以 epub 章节、小节及图号定位；本次不使用 epub 内的 `page_*` 遗留标记推定页码，印刷页和 PDF 页均为「—」。下表给出主题定位；「对象对照」每行另定义实体锚点，理由末尾标图号，均属于第 7 章。附录 F 仅作补核，不扩展到第 1 卷全部被援引的模型。

| 锚点 | 概念 | 原文位置 | 印刷页 | PDF 页 | 要点 |
|---|---|---|---|---|---|
| SIL2#书目 | 题名、作者、版权、ISBN | 题名页；版权页（01_title.html、02_copyright.html） | — | — | Len Silverston；2001；ISBN 0-471-35348-5；epub 的 creator 错写为编辑 Robert M. Elliott |
| SIL2#服务内容与裁剪 | 产品与通用模式的行业裁剪 | 第 7 章 / Professional Services Products Model；Applicability of Other Product Models；Professional Service Product Associations（图 7.2–7.3） | — | — | 服务按成果或时间材料计价；组合、补充与淘汰关系有别于制造物料结构 |
| SIL2#专业服务请求 | Professional Services Requests | 第 7 章 / Orders / Professional Services Requests and Quotes / Professional Services Requests（图 7.5） | — | — | RFI、RFP、RFQ 可多次请求同一需求、技能、成果或特征 |
| SIL2#委托事项 | Engagements | 第 7 章 / Orders / Engagements；Types of Engagement Items；Placement versus Deliverables-Based Consulting（图 7.7） | — | — | 订单式承诺，条目可为派驻、定制服务、产品或其他项 |
| SIL2#协议与服务履行 | 统辖协议与实际履行的联系 | 第 7 章 / Professional Services Agreements；Delivery / Professional Services Entries；Work Efforts（图 7.8–7.10） | — | — | 上层协议、承诺条目、工作投入和服务记录分开表达 |
| SIL2#服务记录计费 | Service Entry Billing | 第 7 章 / Invoicing（图 7.11） | — | — | 多个服务记录可合并到一条账单明细；关系须保留来源 |
| SIL2#工时分析 | 专业服务工时星型模型 | 第 7 章 / Star Schema for Professional Services / Time Entry Fact；Dimensions（图 7.12） | — | — | 一个事实表、4 个度量、6 个维度；附录 F 补明六维组合主键 |
| SIL2#主体角色关系 | Party、Party Role、Party Relationship | 第 7 章 / People and Organizations in Professional Services / Person Roles；Organization Roles（图 7.1） | — | — | 同一主体可有多种角色；关系有起止日期与类型 |
| SIL2#产品交付技能要求 | Product Delivery Skill Requirement | 第 7 章 / Professional Services Products Model（图 7.2） | — | — | 产品与技能多对多，关联含经验年限、技能等级等条件 |
| SIL2#服务组合 | Marketing Package / Marketing Packaging | 第 7 章 / Professional Service Product Associations（图 7.3） | — | — | 图 7.3 名为 Marketing Packaging，正文与总图名为 Marketing Package；合记一个关系模式 |
| SIL2#需求沟通 | Requirement Communication | 第 7 章 / Professional Services Requirements（图 7.4） | — | — | 沟通事件关联需求，也可关联被考虑的专业人员 |
| SIL2#委托工作履行 | Engagement Work Fulfillment | 第 7 章 / Types of Engagement Items；Work Efforts（图 7.7、7.10） | — | — | 委托条目与工作投入多对多；承诺分解与管理分解可以不同 |
| SIL2#专业人员分派 | Professional Assignment | 第 7 章 / Types of Engagement Items；Professional Services Entries（图 7.7、7.9） | — | — | 人员与委托条目多对多，含生效期间 |
| SIL2#工作投入分派 | Work Effort Assignment | 第 7 章 / Work Efforts（图 7.10） | — | — | 人员也可分派到项目、活动或任务 |
| SIL2#工作投入计费 | Work Effort Billing | 第 7 章 / Invoicing（图 7.11） | — | — | 工作投入与账单明细关联，记录本次计费比例 |
| SIL2#工时事实 | TIME_ENTRY_FACT | 第 7 章 / Star Schema for Professional Services / Time Entry Fact（图 7.12）；附录 F 同名条目 | — | — | 附录列六个维度键为组合主键，不含原始工时 ID；不可宣称逐条审批记录粒度 |
| SIL2#账单金额 | dollars_billed | 第 7 章 / Time Entry Fact（图 7.12） | — | — | 计费费率乘时间量；税、币种、实际开票状态未在该公式声明 |
| SIL2#计费小时 | hours_billed | 第 7 章 / Time Entry Fact（图 7.12） | — | — | 时间量汇总，非小时单位经单位换算；已计费范围须结合正文语境核验 |
| SIL2#专业人员成本 | cost | 第 7 章 / Time Entry Fact（图 7.12） | — | — | 向专业人员支付的成本，区别于本系统标准费率计成本 |
| SIL2#原文成本账单比 | gross_margin | 第 7 章 / Time Entry Fact（图 7.12） | — | — | 原文定义为 cost ÷ dollars_billed；不借其名称认定本系统毛利率 |
| SIL2#专业人员维度 | PROFESSIONALS | 第 7 章 / Dimensions（图 7.12） | — | — | 专业人员标识与姓名 |
| SIL2#客户维度 | CLIENTS | 第 7 章 / Dimensions（图 7.12） | — | — | 来源为委托角色中的客户及其主体名称 |
| SIL2#项目维度 | PROJECTS | 第 7 章 / Dimensions（图 7.12） | — | — | 只取工作投入中的项目子型，含工作投入类型 |
| SIL2#费率类型维度 | RATE_TYPES | 第 7 章 / Dimensions（图 7.12） | — | — | 普通、加班等费率类别；不同于费率版本或工时归集类别 |
| SIL2#按日时间维度 | TIME_BY_DAY | 第 7 章 / Dimensions（图 7.12） | — | — | 日、周、月、季、年层级 |
| SIL2#委托条目类型维度 | ENGAGEMENT_ITEM_TYPES | 第 7 章 / Dimensions（图 7.12） | — | — | 区分派驻、产品订单与定制委托等承诺类型 |

## 对本系统的启示

对照范围为当前检出的[全企业业务对象信息图](../../proposals/ENTERPRISE_INFO_MAP_DRAFT.md) §1、[总线矩阵草案](../../../business/bi-scope.md) §3.2，以及[词表](../../../glossary/terms.yaml)；不以旧笔记中的待核编号作结论。「已覆盖」只指草案已明确同类业务语义，不证明代码、DDL 或运行验收；「部分覆盖」指已有承载对象但范围、属性或基数未对齐；「可能遗漏」指这几份材料未明确该语义，供反证，不能据此认定必须建对象；「不适用」指本次服务业务对照不采用该专门模式。

第 7 章的需求—请求—报价—委托—履行—计费链可用于核对业务追溯，不据此替代本系统的价值流、能力或流程编号。书中的计费事实不改变本系统收入确认规则。尚无词条的中文锚点只是本笔记的检索标签；下述缺口均为提议，正式命名、对象设立与指标登记另交负责会话，不在本卡新增对象或词表项。

### 对象对照

逐行覆盖图 7.1–7.11、7.13 的实体、子型、类型表和关联实体；同一实体跨图重复仅列一次。另列正文点名的通用模式和附录 F 的补充实体；星型事实、维度在下一节逐个列出。每行理由中的图号或小节归属均在第 7 章，实体名的拼写差异与原文矛盾另见附节。

| 锚点 | Silverston 实体 | 本系统对象（OBJ-，查 docs/architecture/proposals/ENTERPRISE_INFO_MAP_DRAFT.md） | 判断 | 理由 |
|---|---|---|---|---|
| SIL2#主体 | PARTY | OBJ-01、OBJ-54、OBJ-56 | 部分覆盖 | 往来单位、我方法人与人员分开承载，未声明统一主体身份；图 7.1。 |
| SIL2#人员 | PERSON | OBJ-56、OBJ-01、OBJ-03 | 部分覆盖 | 我方劳动或劳务人员与个人客户、联系人范围不同；图 7.1。 |
| SIL2#组织 | ORGANIZATION | OBJ-01、OBJ-54、OBJ-55 | 部分覆盖 | 外部单位、法人、组织单元分立，不能仅按同名合并；图 7.1。 |
| SIL2#主体角色 | PARTY ROLE | OBJ-02、OBJ-34、OBJ-57 | 部分覆盖 | 有往来、项目和任职角色，未覆盖全部跨主体角色；图 7.1。 |
| SIL2#人员角色 | PERSON ROLE | OBJ-56、OBJ-03 | 部分覆盖 | 人员用工类型及联系人已有，通用人员角色未统一；图 7.1。 |
| SIL2#组织角色 | ORGANIZATION ROLE | OBJ-02、OBJ-54、OBJ-55 | 部分覆盖 | 业务角色与内部组织结构分开承载；图 7.1。 |
| SIL2#专业人员 | PROFESSIONAL | OBJ-56、OBJ-64、OBJ-02 | 部分覆盖 | 有人员与执业证书，但可服务的外部专业人员不必是我方人员；图 7.1。 |
| SIL2#员工 | EMPLOYEE | OBJ-56、OBJ-05 | 已覆盖 | 人员的用工类型和劳动合同关系明确；图 7.1。 |
| SIL2#承包人员 | CONTRACTOR | OBJ-56、OBJ-02、OBJ-05 | 部分覆盖 | 劳务派遣、人力外包及供应商角色已有，外部个人承包者范围待核；图 7.1。 |
| SIL2#联系人 | CONTACT | OBJ-03 | 已覆盖 | 往来单位联系人及决策链角色明确；图 7.1。 |
| SIL2#股东 | SHAREHOLDER | OBJ-91、OBJ-112 | 部分覆盖 | 有出资与利润分配，未独立说明自然人或组织的持股角色；图 7.1。 |
| SIL2#潜在客户 | PROSPECT | OBJ-17、OBJ-18、OBJ-02 | 部分覆盖 | 线索和商机表达机会，不自动等于主体上的潜在客户角色；图 7.1。 |
| SIL2#客户 | CLIENT | OBJ-01、OBJ-02 | 已覆盖 | 往来单位可为个人或组织，客户作为角色；图 7.1。 |
| SIL2#付款客户 | BILL TO CLIENT | OBJ-03、OBJ-05、OBJ-85 | 部分覆盖 | 有付款方联系人、协议相对方与款项对象，付款客户主体角色未显式区分；图 7.1。 |
| SIL2#接收服务客户 | DELIVERY CLIENT | OBJ-05、OBJ-35 | 部分覆盖 | 有协议相对方与成果送达，接收服务主体未单列角色；图 7.1。 |
| SIL2#专业服务提供方 | PROFESSIONAL SERVICES PROVIDER | OBJ-54、OBJ-02 | 部分覆盖 | 内部法人及外部分包、外包角色可承载，统一服务提供方角色未声明；图 7.1。 |
| SIL2#业务伙伴 | PARTNER | OBJ-02 | 已覆盖 | 合伙人、渠道伙伴、联合体及海外伙伴作为业务角色；图 7.1。 |
| SIL2#供应商 | SUPPLIER | OBJ-02 | 已覆盖 | 供应商角色含分包、外包、采购与派遣；图 7.1。 |
| SIL2#竞争者 | COMPETITOR | OBJ-01、OBJ-18 | 可能遗漏 | 总表未显式声明竞争者角色及其与商机的关系；图 7.1。 |
| SIL2#行业协会 | ASSOCIATION | OBJ-01 | 可能遗漏 | 往来单位类型及角色未明确协会角色；图 7.1。 |
| SIL2#组织单元 | ORGANIZATION UNIT | OBJ-55 | 已覆盖 | 已作为组织图元素供业务引用；图 7.1。 |
| SIL2#母组织 | PARENT ORGANIZATION | OBJ-01、OBJ-54、OBJ-55 | 部分覆盖 | 外部单位上级链及内部组织树已有，法律控制与管理隶属须区别；图 7.1。 |
| SIL2#子组织 | SUBSIDIARY | OBJ-01、OBJ-54、OBJ-55 | 部分覆盖 | 有上下级关系，子公司与部门不能共用一种控制语义；图 7.1。 |
| SIL2#组织单元-部门型 | DEPARTMENT | OBJ-55 | 已覆盖 | 组织单元类型含部门；锚点使用词表中文名作前缀；图 7.1。 |
| SIL2#组织单元-事业部型 | DIVISION | OBJ-55 | 已覆盖 | 组织单元类型含事业部；图 7.1。 |
| SIL2#组织单元-其他型 | OTHER ORGANIZATION UNIT | OBJ-55 | 部分覆盖 | 树可承载，其他类型取值未说明；图 7.1。 |
| SIL2#内部组织 | INTERNAL ORGANIZATION | OBJ-54、OBJ-55 | 已覆盖 | 内部法人及管理单元已区分；图 7.1。 |
| SIL2#主体关系 | PARTY RELATIONSHIP | OBJ-01、OBJ-02、OBJ-05、OBJ-57 | 部分覆盖 | 有多种专门关系，未声明通用双方角色及期间关系；图 7.1。 |
| SIL2#供方关系 | SUPPLIER RELATIONSHIP | OBJ-02、OBJ-05 | 已覆盖 | 供方准入状态与分包、采购协议可追溯；图 7.1。 |
| SIL2#单位联系人关系 | ORGANIZATION CONTACT RELATIONSHIP | OBJ-03、OBJ-01 | 已覆盖 | 联系人依附往来单位；图 7.1。 |
| SIL2#雇佣关系 | EMPLOYMENT | OBJ-56、OBJ-05、OBJ-57 | 已覆盖 | 人员、劳动合同、任职共同承载；图 7.1。 |
| SIL2#分包关系 | SUBCONTRACTOR RELATIONSHIP | OBJ-02、OBJ-05 | 已覆盖 | 分包角色、主协议与分包协议关系明确；图 7.1。 |
| SIL2#客户关系 | CLIENT RELATIONSHIP | OBJ-02、OBJ-05、OBJ-54 | 部分覆盖 | 客户与我方法人可经协议关联，无协议期间的关系未明确；图 7.1。 |
| SIL2#伙伴关系 | PARTNERSHIP | OBJ-02、OBJ-05 | 已覆盖 | 伙伴角色及联合体等协议已有；图 7.1。 |
| SIL2#组织汇总关系 | ORGANIZATION ROLLUP | OBJ-01、OBJ-55 | 已覆盖 | 往来单位上级链与内部组织树已有；图 7.1。 |
| SIL2#角色类型 | ROLE TYPE | OBJ-02、OBJ-34、OBJ-57 | 部分覆盖 | 各类角色目录分散，未声明跨域角色类型；图 7.1。 |
| SIL2#主体角色类型 | PARTY ROLE TYPE | OBJ-02 | 部分覆盖 | 已有往来单位角色取值，人员和内部组织角色仍分立；图 7.1。 |
| SIL2#主体关系类型 | PARTY RELATIONSHIP TYPE | OBJ-01、OBJ-05、OBJ-57 | 部分覆盖 | 有关系类型与业务状态，未明确两端角色的兼容约束；图 7.1。 |
| SIL2#服务产品 | PRODUCT | OBJ-24 | 部分覆盖 | 服务类别与默认阶段已覆盖，书中产品还包括实物；图 7.2。 |
| SIL2#服务 | SERVICE | OBJ-24 | 已覆盖 | 目录的服务类别及版本已有；图 7.2。 |
| SIL2#按成果服务 | DELIVERABLE BASED SERVICE | OBJ-24、OBJ-25、OBJ-35 | 部分覆盖 | 默认成果已有，目录层计价形态未声明；图 7.2。 |
| SIL2#按时间材料服务 | TIME AND MATERIALS SERVICE | OBJ-24、OBJ-05、OBJ-07 | 部分覆盖 | 协议有按量节点，服务目录与计价形态的关联待核；图 7.2。 |
| SIL2#实物商品 | GOOD | OBJ-24、OBJ-93 | 不适用 | 公司自用资产不是对客商品；本卡不提议扩展商品销售目录；图 7.2。 |
| SIL2#产品交付技能要求 | PRODUCT DELIVERY SKILL REQUIREMENT | OBJ-24、OBJ-56、OBJ-44 | 可能遗漏 | 人员技能标签及技能参考目录已有，但产品到技能的年限、等级要求未明确；图 7.2。 |
| SIL2#技能类型 | SKILL TYPE | OBJ-56、OBJ-44 | 已覆盖 | 人员技能目录为参考数据，供资源需求匹配；不能沿用旧笔记认定完全缺失；图 7.2、7.4。 |
| SIL2#产品特征 | PRODUCT FEATURE | OBJ-24 | 部分覆盖 | 目录允许默认阶段和成果，其他可选特征与价格关联未说明；图 7.2。 |
| SIL2#服务特征 | SERVICE FEATURE | OBJ-24、OBJ-25 | 部分覆盖 | 服务范围及阶段可裁剪，选项结构未明确；图 7.2。 |
| SIL2#软件特征 | SOFTWARE FEATURE | OBJ-24 | 不适用 | 对客软件及源码选项非当前服务目录已声明范围；图 7.2。 |
| SIL2#计费特征 | BILLING FEATURE | OBJ-24、OBJ-07 | 部分覆盖 | 付款条件可承载周期，目录上的可选计费特征未说明；图 7.2。 |
| SIL2#其他产品特征 | OTHER FEATURE | OBJ-24 | 部分覆盖 | 只有目录入口，未声明其他特征的分类与适用性；图 7.2。 |
| SIL2#产品特征适用性 | PRODUCT FEATURE APPLICABILITY | OBJ-24 | 可能遗漏 | 服务与特征的多对多、生效期间未在信息图明示；图 7.2。 |
| SIL2#产品类别 | PRODUCT CATEGORY | OBJ-24 | 部分覆盖 | 服务类别已有，多个分类轴未说明；图 7.2。 |
| SIL2#产品行业分类 | PRODUCT INDUSTRY CATEGORIZATION | OBJ-24、OBJ-29 | 部分覆盖 | 业务线与项目类型已有，产品面向客户行业的分类未声明；图 7.2。 |
| SIL2#技术产品分类 | TECHNICAL PRODUCT CLASSIFICATION | OBJ-24 | 部分覆盖 | 服务类别可表达技术方向，独立分类轴未说明；图 7.2。 |
| SIL2#产品类别归属 | PRODUCT CATEGORY CLASSIFICATION | OBJ-24 | 可能遗漏 | 多分类归属、主分类标记及生效区间未声明；图 7.2。 |
| SIL2#产品类别层级 | PRODUCT CATEGORY ROLLUP | OBJ-24 | 可能遗漏 | 信息图未声明类别树及层级生效关系；图 7.2。 |
| SIL2#服务组合 | MARKETING PACKAGE / MARKETING PACKAGING | OBJ-24、OBJ-25 | 可能遗漏 | 默认阶段组合不等于多个服务产品打包；应先核是否需要目录关系；图 7.3。 |
| SIL2#产品互补关系 | PRODUCT COMPLEMENT | OBJ-24 | 可能遗漏 | 未见服务产品间互补推荐关系；图 7.3。 |
| SIL2#产品淘汰替代关系 | PRODUCT OBSOLESCENCE | OBJ-24 | 部分覆盖 | 有停用和版本，未声明旧产品被何产品替代；图 7.3。 |
| SIL2#需求 | REQUIREMENT | OBJ-18、OBJ-44、OBJ-60 | 部分覆盖 | 商机、项目资源需求、招聘需求分立；统一需求分解与生命周期未声明；图 7.4。 |
| SIL2#客户需求 | CLIENT REQUIREMENT | OBJ-18、OBJ-06 | 部分覆盖 | 商机及合同范围可留承诺，售前需求自身状态与分解不明确；图 7.4。 |
| SIL2#内部需求 | INTERNAL REQUIREMENT | OBJ-44、OBJ-60 | 部分覆盖 | 资源及招聘需求已有，内部采购服务需求范围待核；图 7.4。 |
| SIL2#资源需求 | RESOURCE REQUIREMENT | OBJ-44、OBJ-60 | 部分覆盖 | 项目与招聘需要已有，对客人员派驻需求的期限和人数未对齐；图 7.4。 |
| SIL2#项目需求 | PROJECT REQUIREMENT | OBJ-18、OBJ-06、OBJ-35 | 部分覆盖 | 机会、约定和实际成果已有，签约前期望成果的需求表达待核；图 7.4。 |
| SIL2#产品需求 | PRODUCT REQUIREMENT | OBJ-18、OBJ-22、OBJ-24 | 部分覆盖 | 报价可选服务产品，未声明独立需求到产品及所需特征的关系；图 7.4。 |
| SIL2#所需技能 | NEEDED SKILL | OBJ-44、OBJ-56 | 部分覆盖 | 已有技能标签与匹配入口，需求侧经验年限和等级约束未明确；图 7.4。 |
| SIL2#所需交付物 | NEEDED DELIVERABLE | OBJ-18、OBJ-25、OBJ-35 | 部分覆盖 | 默认和实际成果已有，尚未签约的期望成果未显式建模；图 7.4。 |
| SIL2#交付物类型 | DELIVERABLE TYPE | OBJ-25、OBJ-35 | 已覆盖 | 阶段默认成果与实际交付物类型已有；图 7.4。 |
| SIL2#期望特征 | DESIRED FEATURE | OBJ-18、OBJ-22、OBJ-24 | 可能遗漏 | 未见需求到服务可选特征及必选、可选标记；图 7.4。 |
| SIL2#场所 | FACILITY | OBJ-46、OBJ-47、OBJ-93 | 部分覆盖 | 有建设工程和公司资产，但办公室、驻场地点与服务需求的关系未声明；图 7.4。 |
| SIL2#需求角色 | REQUIREMENT ROLE | OBJ-18、OBJ-44、OBJ-60 | 部分覆盖 | 有负责人或申请人，需求方、服务方、招募者、候选人角色链未对齐；图 7.4。 |
| SIL2#需求角色类型 | REQUIREMENT ROLE TYPE | OBJ-18、OBJ-44、OBJ-60 | 可能遗漏 | 未明示需求角色目录及两端约束；图 7.4。 |
| SIL2#需求状态 | REQUIREMENT STATUS | OBJ-18、OBJ-44、OBJ-60 | 部分覆盖 | 各对象有状态，需求状态的逐次历史需核；图 7.4。 |
| SIL2#需求状态类型 | REQUIREMENT STATUS TYPE | OBJ-18、OBJ-44、OBJ-60 | 部分覆盖 | 已有各自状态取值，不要求复制一套统一类型表；图 7.4。 |
| SIL2#沟通事件 | COMMUNICATION EVENT | OBJ-105、OBJ-53 | 部分覆盖 | 客户活动与证据可记录沟通，未覆盖内部需求及候选人的全部沟通；图 7.4。 |
| SIL2#沟通事件目的 | COMMUNICATION EVENT PURPOSE | OBJ-105 | 部分覆盖 | 已有拜访、跟进类型，访谈、简历提交、提案等用途未全部声明；图 7.4。 |
| SIL2#需求活动 | REQUIREMENT ACTIVITY | OBJ-105、OBJ-60 | 部分覆盖 | 书中为沟通目的子型；客户活动与招聘可承载，具体分类待核；图 7.4。 |
| SIL2#需求沟通 | REQUIREMENT COMMUNICATION | OBJ-105、OBJ-18、OBJ-60 | 可能遗漏 | 活动能关联商机，但一事件对多需求并可关联候选专业人员未明确；图 7.4。 |
| SIL2#服务请求 | REQUEST | OBJ-19、OBJ-20 | 部分覆盖 | 招标项目和投标覆盖部分请求，非招标的双向请求未明确；图 7.5。 |
| SIL2#信息征询 | RFI | OBJ-19、OBJ-18 | 可能遗漏 | 未见独立信息征询及多轮响应语义；图 7.5。 |
| SIL2#方案征询 | RFP | OBJ-19、OBJ-20 | 部分覆盖 | 招标与投标可承载，非招标方案征询待核；图 7.5。 |
| SIL2#询价请求 | RFQ | OBJ-19、OBJ-21 | 部分覆盖 | 询价可进入报价流程，发出请求及接收请求的状态未声明；图 7.5。 |
| SIL2#请求条目 | REQUEST ITEM | OBJ-19、OBJ-22 | 可能遗漏 | 标段或报价分解不等于请求中逐项需求、技能与成果；图 7.5。 |
| SIL2#需求请求关联 | REQUIREMENT REQUEST | OBJ-18、OBJ-19 | 可能遗漏 | 同一需求被多次请求、每个请求含多个需求的追溯未声明；图 7.5。 |
| SIL2#请求角色 | REQUEST ROLE | OBJ-19、OBJ-20、OBJ-03 | 部分覆盖 | 发起方与响应方可部分追溯，完整角色链待核；图 7.5。 |
| SIL2#请求角色类型 | REQUEST ROLE TYPE | OBJ-19、OBJ-20 | 可能遗漏 | 请求角色取值未明确；图 7.5。 |
| SIL2#响应主体 | RESPONDING PARTY | OBJ-20、OBJ-01 | 可能遗漏 | 我方投标已有，但其他拟响应主体、发出日期及渠道未明确；图 7.5。 |
| SIL2#联系渠道 | CONTACT MECHANISM | OBJ-01、OBJ-03 | 部分覆盖 | 地址电话等档案属性已有，请求发往哪个渠道未明确；图 7.5。 |
| SIL2#报价单 | QUOTE | OBJ-21 | 已覆盖 | 有版本化报价及价格、成本测算；图 7.6。 |
| SIL2#提案 | PROPOSAL | OBJ-20、OBJ-21、OBJ-53 | 部分覆盖 | 投标、报价与附件证据可承载，书中提案的内容与类型尚未完全对齐；图 7.6。 |
| SIL2#工作说明书 | STATEMENT OF WORK | OBJ-05、OBJ-06、OBJ-21 | 部分覆盖 | 协议范围与报价承诺可承载；正文与图对子型位置不一致，不据此设新对象；图 7.6。 |
| SIL2#产品报价 | PRODUCT QUOTE | OBJ-21、OBJ-22、OBJ-24 | 已覆盖 | 报价分解可引用服务产品；图 7.6。 |
| SIL2#其他报价 | OTHER QUOTE | OBJ-21 | 部分覆盖 | 有报价对象，其他类型未定义；图 7.6。 |
| SIL2#报价条目 | QUOTE ITEM | OBJ-22 | 部分覆盖 | 服务、阶段、工作量与价格已覆盖，按技能或特征报价的关系未明示；图 7.6。 |
| SIL2#报价条件 | QUOTE TERM | OBJ-21、OBJ-05、OBJ-07 | 部分覆盖 | 报价与协议可有条件，条件类型及值到报价条目的挂接待核；图 7.6。 |
| SIL2#条件类型 | TERM TYPE | OBJ-05、OBJ-07 | 部分覆盖 | 有付款条件类型，非付款类条件目录未完整说明；图 7.6。 |
| SIL2#报价角色 | QUOTE ROLE | OBJ-21、OBJ-03 | 部分覆盖 | 报价办理角色和对方联系人已有，报价方、接收方完整关系待核；图 7.6。 |
| SIL2#报价角色类型 | QUOTE ROLE TYPE | OBJ-21 | 可能遗漏 | 未声明报价业务角色类型，不应借审批角色替代；图 7.6。 |
| SIL2#计量单位 | UNIT OF MEASURE | OBJ-66、OBJ-26、OBJ-07 | 部分覆盖 | 工时采用小时、费率有人天，合同按量也有单位；统一换算依据需核；图 7.6–7.9。 |
| SIL2#委托事项 | ENGAGEMENT | OBJ-05、OBJ-29 | 部分覆盖 | 任务订单可表达一次承诺，项目表达组织执行；不作同义替换；图 7.7。 |
| SIL2#委托条目 | ENGAGEMENT ITEM | OBJ-06、OBJ-08、OBJ-22 | 部分覆盖 | 范围条目和履约义务承担不同语义，尚未逐型映射；图 7.7。 |
| SIL2#专业人员派驻 | PROFESSIONAL PLACEMENT | OBJ-05、OBJ-34、OBJ-45 | 部分覆盖 | 有人力外包协议和分派，对客指定人员承诺的责任边界未明确；图 7.7。 |
| SIL2#定制委托条目 | CUSTOM ENGAGEMENT ITEM | OBJ-06、OBJ-29、OBJ-30 | 部分覆盖 | 范围和 WBS 能承载定制工作，约定价到条目的追溯待核；图 7.7。 |
| SIL2#产品订单条目 | PRODUCT ORDER ITEM | OBJ-06、OBJ-24 | 部分覆盖 | 服务范围可引用目录，书中含实物商品的部分不适用；图 7.7。 |
| SIL2#标准服务订单条目 | STANDARD SERVICE ORDER ITEM | OBJ-06、OBJ-24 | 已覆盖 | 合同服务范围与标准服务目录可对接；图 7.7。 |
| SIL2#交付物订单条目 | DELIVERABLE ORDER ITEM | OBJ-06、OBJ-25、OBJ-35 | 部分覆盖 | 承诺成果及实际交付物已有，标准成果产品与固定价格关联待核；图 7.7。 |
| SIL2#实物订单条目 | GOOD ORDER ITEM | OBJ-05、OBJ-24 | 不适用 | 不能把采购协议或自用资产当作对客商品销售条目；图 7.7。 |
| SIL2#其他委托条目 | OTHER ENGAGEMENT ITEM | OBJ-06、OBJ-07 | 部分覆盖 | 范围与付款节点可承载部分其他收费，具体语义未声明；图 7.7。 |
| SIL2#委托费率 | ENGAGEMENT RATE | OBJ-26、OBJ-27、OBJ-05、OBJ-66 | 部分覆盖 | 标准成本费率、计费系数和锁定版本已有；委托条目的实际成本、计费费率及普通加班并行费率待核；图 7.7。 |
| SIL2#费率类型 | RATE TYPE | OBJ-26、OBJ-72 | 可能遗漏 | 普通、加班、周末费率类型不等于职级费率或加班申请；图 7.7、7.9。 |
| SIL2#专业人员分派 | PROFESSIONAL ASSIGNMENT | OBJ-34、OBJ-45 | 部分覆盖 | 项目与工作包分派已有，直接到委托条目的分派及期间需核；图 7.7、7.9。 |
| SIL2#委托工作履行 | ENGAGEMENT WORK FULFILLMENT | OBJ-06、OBJ-11、OBJ-30 | 部分覆盖 | 范围挂 WBS、协议与项目多对多已有；书中条目与工作投入多对多不同于现有每 WBS 0..1 范围条目；图 7.7、7.10。 |
| SIL2#协议 | AGREEMENT | OBJ-05 | 已覆盖 | 统辖约定和任务订单挂框架协议已有；图 7.8。 |
| SIL2#专业服务协议 | PROFESSIONAL SERVICES AGREEMENT | OBJ-05 | 已覆盖 | 项目合同、框架协议、任务订单均为协议类型；图 7.8。 |
| SIL2#客户协议 | CLIENT AGREEMENT | OBJ-05、OBJ-02 | 已覆盖 | 协议与客户相对方可关联；图 7.8。 |
| SIL2#分包协议 | SUBCONTRACTOR AGREEMENT | OBJ-05、OBJ-02 | 已覆盖 | 分包、外包及主协议关联已有；图 7.8。 |
| SIL2#协议条目 | AGREEMENT ITEM | OBJ-06、OBJ-07 | 部分覆盖 | 范围条目与付款条件项已有，通用文本或定价子型仍须映射；图 7.8。 |
| SIL2#协议定价安排 | AGREEMENT PRICING PROGRAM | OBJ-05、OBJ-26、OBJ-27 | 部分覆盖 | 协议价目及费率参数已有，条目级定价规则组合未明确；图 7.8。 |
| SIL2#协议条件 | AGREEMENT TERM | OBJ-05、OBJ-07 | 部分覆盖 | 含付款、保密等条件，不能全译为本系统结算语义的合同条款；图 7.8。 |
| SIL2#价格组成 | PRICE COMPONENT | OBJ-05、OBJ-26、OBJ-27 | 部分覆盖 | 价目、费率和系数可承载部分定价，按条件组合计价待核；图 7.8。 |
| SIL2#服务记录 | SERVICE ENTRY | OBJ-66、OBJ-88、OBJ-35、OBJ-53 | 部分覆盖 | 工时、费用、移交分别有承载，未声明统一服务记录；不要求新增统一父对象；图 7.9。 |
| SIL2#工时 | TIME ENTRY | OBJ-66 | 部分覆盖 | 审批工时和费率版本已有，逐条计费费率及向专业人员支付成本不完全相同；图 7.9。 |
| SIL2#费用记录 | EXPENSE ENTRY | OBJ-88、OBJ-118、OBJ-121 | 部分覆盖 | 费用报销及公司直付、月账单已有，逐项可计费与委托条目的关联待核；图 7.9。 |
| SIL2#材料使用 | MATERIALS USAGE | OBJ-88、OBJ-93、OBJ-87 | 可能遗漏 | 费用、资产和到货确认均不等于材料实际使用及向客户计费；先核 AEC 服务是否需要；图 7.9。 |
| SIL2#成果移交 | DELIVERABLE TURNOVER | OBJ-35、OBJ-53 | 已覆盖 | 交付物的提交、送达、认可证据已有；移交不自动构成收入；图 7.9。 |
| SIL2#服务记录类型 | SERVICE ENTRY TYPE | OBJ-66、OBJ-88、OBJ-35 | 部分覆盖 | 已有各业务类型，未建统一类型目录；不据此提议统一对象；图 7.9。 |
| SIL2#服务记录表头 | SERVICE ENTRY HEADER | OBJ-66、OBJ-88 | 部分覆盖 | 工时单与报销单有各自提交单位，书中跨工时、费用、移交的表头未采用；图 7.9。 |
| SIL2#工作投入 | WORK EFFORT | OBJ-29、OBJ-30 | 已覆盖 | 项目及 WBS 表达执行分解、计划、实际和预算坐标；图 7.10。 |
| SIL2#项目 | PROJECT | OBJ-29 | 已覆盖 | 交付管理单元明确；图 7.10。 |
| SIL2#工作活动 | ACTIVITY | OBJ-30 | 部分覆盖 | WBS 的阶段、子项与工作包可承载，层级不必一一对应；图 7.10。 |
| SIL2#工作任务 | TASK | OBJ-30 | 部分覆盖 | 末级工作包可记工时，但不能等同审批任务；图 7.10。 |
| SIL2#工作投入分派 | WORK EFFORT ASSIGNMENT | OBJ-34、OBJ-45 | 已覆盖 | 项目团队角色与工作包具名计划工时、期间已有；图 7.10。 |
| SIL2#账单 | INVOICE | OBJ-82、OBJ-83 | 部分覆盖 | 商业请款由开票申请承载，税务登记发票另有对象；书中通用账单不能直接译为增值税发票；图 7.11。 |
| SIL2#账单明细 | INVOICE ITEM | OBJ-82、OBJ-83 | 部分覆盖 | 信息图有申请及税票，未明示逐条服务或工作投入到商业账单明细的分配；图 7.11。 |
| SIL2#销售账单明细 | SALES INVOICE ITEM | OBJ-82、OBJ-83 | 部分覆盖 | 销项方向已有，商业账单明细与税票行的对应待核；图 7.11。 |
| SIL2#采购账单明细 | PURCHASE INVOICE ITEM | OBJ-83、OBJ-85、OBJ-87、OBJ-121 | 部分覆盖 | 进项票、应付、供方确认与月账单已有，服务记录到购方账单行的关系待核；图 7.11。 |
| SIL2#账单明细类型 | INVOICE ITEM TYPE | OBJ-82、OBJ-83 | 部分覆盖 | 开票原因与税票类型已有，业务收费行类型未完整声明；图 7.11。 |
| SIL2#服务记录计费 | SERVICE ENTRY BILLING | OBJ-66、OBJ-88、OBJ-35、OBJ-82 | 可能遗漏 | 缺少逐项来源到计费明细的显式关联与防重复计费依据；图 7.11。 |
| SIL2#工作投入计费 | WORK EFFORT BILLING | OBJ-30、OBJ-07、OBJ-82 | 部分覆盖 | 付款节点可以表达进度请款，但每次计费的工作比例与累计限制未明确；图 7.11。 |
| SIL2#交付物 | DELIVERABLE | OBJ-35 | 已覆盖 | 附录 F 列实际成果实体；正文需求与移交均指向成果，实际记录已有。 |
| SIL2#项目群 | PROGRAM | OBJ-29 | 不适用 | 附录 F 列工作投入子型，第 7 章图 7.10 仅示项目、活动、任务；本卡不扩展项目群管理。 |
| SIL2#产品关联 | PRODUCT ASSOCIATION | OBJ-24 | 部分覆盖 | 附录 F 的通用关联父型，具体组合、互补、淘汰分别已列；不再提议父对象。 |
| SIL2#估计产品成本 | ESTIMATED PRODUCT COST | OBJ-22、OBJ-26、OBJ-32 | 部分覆盖 | 报价估算、标准费率和预算已有，按供方产品估计成本的入口待核；Applicability of Other Product Models。 |
| SIL2#供方产品 | SUPPLIER PRODUCT | OBJ-02、OBJ-24、OBJ-05 | 可能遗漏 | 正文建议可记录其他服务提供方可供产品，信息图未明示供方到服务产品目录关系；Applicability of Other Product Models。 |
| SIL2#时间单位换算 | UNIT OF TIME CONVERSION | OBJ-28、OBJ-66 | 部分覆盖 | 标准工时版本及小时记录已有，历史人天到小时转换须保留适用规则；Time Entry Fact 援引第 1 卷。 |
| SIL2#产品替代 | PRODUCT SUBSTITUTE | OBJ-24 | 不适用 | 本章在 Professional Service Product Associations 明确移除该模式。 |
| SIL2#产品不相容关系 | PRODUCT INCOMPATIBILITY | OBJ-24 | 不适用 | 正文认为偏向实物，本次未据服务业务需求提议；Professional Service Product Associations。 |
| SIL2#产品组件 | PRODUCT COMPONENT | OBJ-24 | 不适用 | 正文以服务组合替换制造式组件，勿将其再作遗漏；Professional Service Product Associations。 |

**可能遗漏清单（逐行反证入口，仅提议）：**

- SIL2#竞争者：总表未显式声明竞争者角色及其与商机的关系；图 7.1。
- SIL2#行业协会：往来单位类型及角色未明确协会角色；图 7.1。
- SIL2#产品交付技能要求：人员技能标签及技能参考目录已有，但产品到技能的年限、等级要求未明确；图 7.2。
- SIL2#产品特征适用性：服务与特征的多对多、生效期间未在信息图明示；图 7.2。
- SIL2#产品类别归属：多分类归属、主分类标记及生效区间未声明；图 7.2。
- SIL2#产品类别层级：信息图未声明类别树及层级生效关系；图 7.2。
- SIL2#服务组合：默认阶段组合不等于多个服务产品打包；应先核是否需要目录关系；图 7.3。
- SIL2#产品互补关系：未见服务产品间互补推荐关系；图 7.3。
- SIL2#期望特征：未见需求到服务可选特征及必选、可选标记；图 7.4。
- SIL2#需求角色类型：未明示需求角色目录及两端约束；图 7.4。
- SIL2#需求沟通：活动能关联商机，但一事件对多需求并可关联候选专业人员未明确；图 7.4。
- SIL2#信息征询：未见独立信息征询及多轮响应语义；图 7.5。
- SIL2#请求条目：标段或报价分解不等于请求中逐项需求、技能与成果；图 7.5。
- SIL2#需求请求关联：同一需求被多次请求、每个请求含多个需求的追溯未声明；图 7.5。
- SIL2#请求角色类型：请求角色取值未明确；图 7.5。
- SIL2#响应主体：我方投标已有，但其他拟响应主体、发出日期及渠道未明确；图 7.5。
- SIL2#报价角色类型：未声明报价业务角色类型，不应借审批角色替代；图 7.6。
- SIL2#费率类型：普通、加班、周末费率类型不等于职级费率或加班申请；图 7.7、7.9。
- SIL2#材料使用：费用、资产和到货确认均不等于材料实际使用及向客户计费；先核 AEC 服务是否需要；图 7.9。
- SIL2#服务记录计费：缺少逐项来源到计费明细的显式关联与防重复计费依据；图 7.11。
- SIL2#供方产品：正文建议可记录其他服务提供方可供产品，信息图未明示供方到服务产品目录关系；Applicability of Other Product Models。

反证时优先检查既有对象的属性、类型、关系和单据明细，不以“没有同名 OBJ”作为遗漏证据。部分覆盖项仍须核验，尤其委托条目到 WBS 的基数、需求技能等级、外部专业人员范围及账单来源追溯；这些不能因未列入上面清单就视为已通过。

### 度量对照

图 7.12 只有一个星型模型。下表逐一覆盖事实表、4 个度量与 6 个维度；事实、维度在对象清单中不再重复计作业务对象。书中图将维度画到事实，附录 F 的 TIME ENTRY FACT 列专业人员、客户、工作投入、日期、费率类型、委托条目类型六键为组合主键，故这里按该组合解释聚合粒度；正文没有规定本系统的审批准入或增量装载规则。

| 锚点 | Silverston 事实或度量 | 本系统对应 | 判断 | 理由 |
|---|---|---|---|---|
| SIL2#工时事实 | TIME_ENTRY_FACT（事实表） | BI_SCOPE §3.2「工时条目」「成本台账分录」「开票」；OBJ-66 | 部分覆盖 | 工时行粒度是一条审批通过记录，书中六维组合可汇总多条工时；成本、开票分属其他事实，不得直接多对多连接后重复求和。客户、费率类型、委托条目类型的取数路径亦未声明。 |
| SIL2#账单金额 | dollars_billed = billing rate × amount of time | §3.2「开票」「工时条目」；metric `billing_conversion_rate`、`accrual_basis_revenue`、`net_service_revenue` | 部分覆盖 | 现有开票事实有金额，但不等于工时推导的账单金额；后三词条分别是开票转化率、权责发生制收入、净服务收入，均非本度量同义项。缺少逐工时的计费金额、税币口径及与实际开票勾稽的声明。 |
| SIL2#计费小时 | hours_billed（时间量归一为小时后汇总） | §3.2「工时条目」；metric `billable_hours`、`effective_hours`、`time_capture_rate`、`utilization_rate` | 部分覆盖 | 词表「可计费工时」是有效、关联 T&M 合同且在费率表范围内的集合，不能直接当作已计费小时；「有效工时」更宽，工时捕获率与利用率是派生比率。需核可计费到实际已计费的状态及转换规则；词表无独立 hours_billed 指标。 |
| SIL2#专业人员成本 | cost（支付给专业人员的金额） | §3.2「成本台账分录」「人工成本 / 薪酬」「付款」；metric `direct_labor`、`outsourcing_cost`、`actual_cost` | 部分覆盖 | 直接人工按有效工时×标准费率，外包成本另有定义，实际成本范围也更广；人员月度成本或付款不能无分配依据地下沉为单条工时成本。须选择实际支付、权责或标准口径并声明。 |
| SIL2#原文成本账单比 | gross_margin = cost ÷ dollars_billed | metric `contribution_margin`、`contribution_margin_ratio`；§3.2 的收入与成本事实 | 不适用 | 本系统贡献毛利率为贡献毛利÷同层级净服务收入，不是成本占账单额比例。原文公式不迁为同名指标，零分母与加总规则也未声明；若需该成本比例，另提议口径。 |
| SIL2#专业人员维度 | PROFESSIONALS（维度） | §3.2「工时条目」人员列；OBJ-56；metric `direct_labor` 的归属 | 部分覆盖 | 人员维度已有，但书中含内部、外部可提供服务的专业人员；须核人员目录覆盖范围与历史身份，不能用平均在册人数代替。 |
| SIL2#客户维度 | CLIENTS（维度） | §3.2「工时条目」客户列为「—」；合同、开票行有客户；OBJ-01、OBJ-02 | 可能遗漏 | 客户主数据存在，但工时到委托客户的分析路径未声明。应核 WBS—范围—协议—客户是否唯一及生效期，不能凭其他事实有客户列认定已覆盖。 |
| SIL2#项目维度 | PROJECTS（维度） | §3.2「工时条目」项目 / WBS 列；OBJ-29 的项目类型、OBJ-30 | 已覆盖 | 项目坐标和项目类型已有；实现时应区分项目与工作包，并明确非项目工时的适用成员，不把二者混为相同粒度。 |
| SIL2#费率类型维度 | RATE_TYPES（维度） | §3.2 未列该维度；OBJ-26、OBJ-72；metric `direct_labor` | 可能遗漏 | 费率版本可复算价格，不表达普通、加班、周末的分类；工时类别的项目、售前、间接也不是费率类型。需核有无该分类的业务需求与历史键。 |
| SIL2#按日时间维度 | TIME_BY_DAY（维度） | §3.2「期间」列；OBJ-28；词表 `work_date`（非 metric） | 部分覆盖 | 工作日期和日历有来源，但矩阵只声明期间，未明确日、周、月、季、年共用维表及周规则；不能把工作日历直接当分析时间维度。 |
| SIL2#委托条目类型维度 | ENGAGEMENT_ITEM_TYPES（维度） | §3.2 未列该维度；合同 / 条款、服务产品列；OBJ-05、OBJ-06、OBJ-24 | 可能遗漏 | 派驻、定制、标准服务或商品订单的承诺分类不是服务产品，也不是项目类型或固定总价 / T&M 计价模式；现有材料未给工时归类路径。 |

词表检索覆盖所有 `kind: metric` 条目；上表只列与该星型语义直接相关者。未使用词表里非 metric 的 `project_hours` 冒充已登记度量，也未由本章推导利用率、人数或收入确认的公式。书中同名不能消除成本口径、计费与可计费、账单与税票、账单与收入确认之间的差别。

**可能遗漏清单（度量与维度，供对抗验证）：**

- SIL2#客户维度：工时事实到客户维度的可追溯路径及其基数。
- SIL2#费率类型维度：普通、加班、周末等分类的必要性、来源及历史版本。
- SIL2#委托条目类型维度：承诺类型的分类来源及其在工时事实中的适用性。

此外，账单金额、计费小时、人员成本和按日时间维度均为「部分覆盖」，仍缺同口径声明；若对抗验证证明现有语义无法承载，再提议指标或关系，不能直接新增对象。

## 我们的取舍

- **建议采纳：分别追溯主体角色、服务目录、业务承诺、执行分解、实际记录与计费来源。** 理由：现有 OBJ-01、OBJ-05、OBJ-24、OBJ-29、OBJ-66、OBJ-82 已有分工，先核关系与明细，不以通用父型替代既有对象。（SIL2#主体角色关系、SIL2#委托事项、SIL2#服务记录计费）
- **建议采纳：把对象和度量缺口作为对抗验证问题。** 理由：先找属性、类型、关系、参考数据和单据是否已承载，再提议是否需要新对象；技能目录已见于 OBJ-56，不能重复报“没有技能”。（SIL2#技能类型、SIL2#产品交付技能要求、SIL2#需求请求关联）
- **建议采纳：先明确工时星型粒度、单位和维度来源，再评估补充总线矩阵。** 理由：六维组合与原子工时粒度不同，标准人工成本和实际支付成本也不同；只提议补核，不在本卡改 BI 口径。（SIL2#工时事实、SIL2#专业人员成本）
- **建议不采纳：将 Engagement、Agreement、Project、Performance Obligation 一对一视为同物，或照搬全部子型、类型表。** 理由：书中承诺、统辖协议和执行工作层次不同，OBJ-05、OBJ-08、OBJ-29 已有本系统边界；图中的关联实体也未必是一级业务对象。（SIL2#委托事项、SIL2#协议与服务履行、SIL2#委托工作履行）
- **建议不采纳：复用 gross_margin 名称及公式作为贡献毛利率，或从账单金额自动推导收入确认。** 理由：原文分子分母与词表不同，计费不是会计履行证据；也不自行将公式改写后宣称为书中定义。（SIL2#原文成本账单比、SIL2#账单金额）
- **建议不采纳：把实物商品、库存及制造式产品组件直接作为服务系统缺口。** 理由：本章明确裁剪库存与组件；仅在出现材料使用或供方服务目录的实际业务证据时再提议相关关系。（SIL2#服务内容与裁剪、SIL2#产品组件、SIL2#材料使用）

## 附：资料范围与版本

- 原文：本机资料清单编号 SIL2 的 epub；第 7 章为 `14_chapter_7.html`，附录 F 为 `23_appendix_f.html`。资料只读，作为不可信文本处理，未执行书中内容，未联网。
- 作者以题名、版权和用户指定的 Len Silverston 为准；版权页列 Robert M. Elliott 为编辑，epub 的 creator 字段误把编辑作为作者。出版者 John Wiley & Sons, Inc.；版权年 2001；ISBN 0-471-35348-5（epub 包元数据另为 9780471353485）。
- 本次源 SHA-256：`b117609ad3da769136d6569c393be8fde8e9f10e71eb506027ac347a74abe0c2`。不记录本机绝对路径。
- 核读包含第 7 章全部正文、13 幅模型图和附录 F 的 8 张清单图；对象表包含关联实体、子型和参考类型，并补入正文点名的产品成本、供方产品、时间换算与被移除模式。仅被表 7.1 引用的第 1 卷会计、人力、运输等完整模型不在本章实体逐项覆盖范围；不是全书各行业的完整性结论。
- 未登记的检索译名仅在本笔记使用；如决定引入产品交付技能要求、需求请求关联、服务记录计费、费率类型等概念，须由负责会话交 one-glossary 查重、登记使用范围和所属 BC，再提议对象或关系。单文件派工不修改词表、信息图或总线矩阵。

## 附：原文差异与待核

- 工作说明书：Professional Service Quotes 小节正文称为 QUOTE ITEM 的子型；图 7.6 则画作 QUOTE 的子型。保留差异，按概念对照，不据此裁定物理表继承层次。
- 服务组合：正文使用 MARKETING PACKAGE、图 7.3 使用 MARKETING PACKAGING；正文说移除制造式数量与装配指令，附录 F 仍列 QUANTITY USED、INSTRUCTION。本文以正文解释服务组合用途，不据附录直接提出字段。
- 客户子型：Organization Roles 首句表述有倒置，后文定义及图 7.1 一致将 CLIENT 分为 BILL TO CLIENT 与 DELIVERY CLIENT；本文按后两处记录，未混成单一付款主体。
- 星型粒度：附录 F 给出六维组合键，正文只描述时间量归集；不能据此断言书中要求一条事实等于一条原始工时，也未见币种、税口径、零分母和历史维度处理的完整规则。
- 关系基数：书中委托条目到工作投入为多对多；当前信息图 OBJ-30 到合同范围条目为 0..1。这里只提议核验 AEC 场景，未更改既定挂接规则。

## 附：旧定位对照

以下原样保存旧扫描本的位置，供已有引用回查；本次未重新打开或验证旧 PDF，不把它作为 epub 页码。旧版记录的状态“已审定（one-architecture 2026-10-18，页码已抽查）”、派工 ARC-1018-ARC-01 是历史记录，不是本次改写获审证据。

| 保留锚点 | 旧原文位置 | 旧印刷页 | 旧 PDF 页 |
|---|---|---|---|
| SIL2#书目 | 题名、版权、ISBN | i–ii | 1–2 |
| SIL2#服务内容与裁剪 | 第 7 章主体角色；服务产品关联、适用性裁剪 | 312–313、320–321 | 166、170 |
| SIL2#专业服务请求 | Professional Services Requests / Figure 7.5 | 328–329 | 174 |
| SIL2#委托事项 | Engagements；Placement versus Deliverables-Based Consulting | 332–333、336–337 | 176、178 |
| SIL2#协议与服务履行 | 协议统辖委托、服务记录、委托与工作投入关联 | 340–341、344–345 | 180、182 |
| SIL2#服务记录计费 | Professional Service Invoicing / Figure 7.11 | 348 | 184 左 |
| SIL2#工时分析 | Star Schema / Time Entry Fact / Dimensions | 348–350 | 184–185 |

旧源为 288 个物理页的双页扫描本；旧笔记记载文字抽取无可用正文，采用选页目视核读。旧文件名、搬移情况及本机位置不再入笔记。

## 附：旧索引对照

- S2-B1 → SIL2#书目
- S2-01 → SIL2#服务内容与裁剪
- S2-02 → SIL2#专业服务请求
- S2-03 → SIL2#委托事项
- S2-04 → SIL2#协议与服务履行
- S2-05 → SIL2#服务记录计费
- S2-06 → SIL2#工时分析
