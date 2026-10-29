# R1-F：对象、流程与阶段命名含义对抗验证

工作卡：ARC-1018-ARC-15（F）。立场：优先寻找“我们错了或漏了”的反证。本文是待裁定的审查结果，不是术语决定或规格变更。

基线：本地 HEAD `6d92b141b7a873cac284797dff7e0c047eb0cef9` 的 `value-streams.v2.yaml`、`ENTERPRISE_INFO_MAP_DRAFT.md` §1–4、`closeout/process-catalog.yaml` 与 `glossary/terms.yaml`。以实际词条定义为准，不沿用对象稿“词表无”的旧尾注。笔记中的旧版价值流号只用于检索，全部重新按本版名称、阶段及流程关联定位。

审查范围：15 条价值流、61 个阶段名称与说明，流程目录中非 retired／merged 且 stages 非空的 161 条；总表 125 个保留行号及其 §2、§3 出口。另有 24 条有效支撑流程 stages 为空，不作为本卡阶段流程逐条裁定的对象。对 notes 目录全部 85 个 Markdown 文件做相关语义检索（含一个模板），重点回读成熟产品、合同、会计、项目、人事、APQC、SPI 与 Silverston 第 7 章的命中段；这不等于逐页读完所有资料。

英文审查边界：正式英文只取 `terms.yaml`，没有词条的名称不自行补译。阶段文件与流程目录未提供独立英文名称字段，故不能宣称其所有英文名已通过审查。下表来源英文只用于说明同名异义，不是给本系统定名。“增补”包括缺少定义中的范围、计量维度或概念对应，不据此断言缺表、缺实现或必须新增对象。提议名称只在本文登记，未写入词表；后续交 one-glossary 处理。

独立来源按产品／资料家族计数：Unanet 八份手册只计一个；PMI 的 PMBOK 与 WBS 标准不重复计；同一准则的摘要、指南不重复计。来源数多不自动升级“冲突”；还须反证同一项已定含义。单源相反线索均降为“增补”。

## 1. 逐项反证与关键印证

| 编号 | 层 | 价值流与阶段 | 本系统条目（OBJ- / PRC- 或“无”） | 类别 | 论断 | 证据（锚点，加页码或条款） | 独立来源数 | 建议 |
|---|---|---|---|---|---|---|---|---|
| R1-F-01 | 命名含义 | VS-01.1 采集线索；VS-05.1 完成供方准入 | OBJ-01 往来单位；OBJ-02 往来单位角色；PRC-05、PRC-69 | 印证 | 同一主体可以兼有客户、供应商身份；“往来单位”与其“角色”有不同含义，现有共档做法没有把 Customer、Vendor 当作两个人或两家公司。 | [UAE](../../frameworks/notes/unanet-ae.md) `UAE#往来单位身份`：SP 印刷／PDF p6 明确 Firm 可切换客户、供方标记；[SIL2](../../frameworks/notes/silverston-dmrb2.md) `SIL2#主体角色关系`：第7章 People and Organizations in Professional Services，图7.1，EPUB `14_chapter_7.html`。 | 2（Unanet、Silverston） | 保留身份与角色的区别；不因产品菜单分开就改成两个主体。 |
| R1-F-02 | 命名含义 | VS-01.1 采集线索；VS-01.2 赢得委托 | OBJ-17 线索；OBJ-18 商机；OBJ-19 招标项目；PRC-03、PRC-14 | 增补 | 资料将 RFI、RFP、RFQ 单独表达为专业服务请求，同一需求可对应多次请求；“线索／商机／招标项目”三行尚未说明这种对外请求分别由谁承载。不能因都是售前信息就把 RFQ 直接解释为招标项目。此为单源对应缺口，不证明需要新对象。 | [SIL2](../../frameworks/notes/silverston-dmrb2.md) `SIL2#专业服务请求`：第7章 Orders / Professional Services Requests and Quotes / Professional Services Requests，图7.5；`14_chapter_7.html` 正文列 RFI、RFP、RFQ。 | 1（单源：Silverston） | 补“客户服务请求（提议名）”与现有三概念的对应说明；先核现有商机组成部分能否承载。 |
| R1-F-03 | 命名含义 | VS-02.0 开始项目；VS-02.1–VS-02.6 交付阶段 | OBJ-30 WBS 节点；PRC-21 推进阶段与工作包 | 增补 | 词表 `work_package / WorkPackage` 与 `delivery_package / DeliveryPackage` 的定义仍同为“WBS 下的工作单元分组”，但工作包的正式含义是 WBS 最底层。对象稿已把工作包限定为末级，§3 又把旧交付包交给 WBS 节点类型；两个英文词条仍不能据定义区分。不是所有 WBS 分组都叫 WorkPackage。已知 C-19 的含义缺口仍存在。 | [WBS3](../../frameworks/notes/pmi-wbs3.md) `WBS3#工作包`：§2.1、§2.2.5，印刷 p12、29／PDF p29、46；[PMB6](../../frameworks/notes/pmbok6-zh.md) `PMB6#工作分解`：§5.4；原文术语表 Work Package 明确“最低层”。 | 1（单源家族：PMI） | 按已定末级含义补齐词条；交付包继续保留为何种类型或旧名，交既有 C-19 处理，不新增对象号。 |
| R1-F-04 | 命名含义 | VS-02.0–VS-02.7 开始项目至完成收尾 | OBJ-33 完工估算；PRC-30 提出并批准完工估算 | 增补 | OBJ-33 把 ETC 写成“剩余工时”，而词表 `EstimateAtCompletion` 是 AC + ETC。成本 AC 不能直接与小时相加；Unanet 也分别列 ETC Units 与 ETC Amount。因此缺的是 ETC 的数量／工时和金额两个含义的明确对应，不是判定现有计算已经算错。 | [PMB6](../../frameworks/notes/pmbok6-zh.md) `PMB6#挣值`：§7.4.2.2“预测”，EPUB `OEBPS/text00002.html`，EAC＝AC＋自下而上 ETC；[UAE](../../frameworks/notes/unanet-ae.md) `UAE#ETC及完成进度历史`：PM 印刷／PDF p12，分别定义 ETC Units、ETC Amount。 | 2（PMI、Unanet） | 在定义中分清剩余工时、非人工数量、剩余成本及换算依据；EAC 的 ETC 加数明确为金额。不改变收入确认口径。 |
| R1-F-05 | 命名含义 | VS-02.2–VS-02.6 确认方案至完成施工配合 | OBJ-33 完工估算；PRC-151 填报经营产值 | 增补 | PRC-151 的“经营产值”向完工估算追加 progress_estimates，却未定义是完成百分比、已完成工作的预算价值，还是对客服务价值。词表只明确 `EarnedValue` 是已完成工作的预算价值，且将裸“产值”列为收入确认的禁用旧称。Ajera 又用 earned value 表示按费率计的投入价值，说明英文同名也不能直接互换。 | [PMB6](../../frameworks/notes/pmbok6-zh.md) `PMB6#挣值`：§7.4.2.2“挣值分析”；[AJE](../../frameworks/notes/ajera.md) `AJE#产品计价完成值`：实施指南印刷／PDF p65，Using earned value analysis。本系统：PRC-151 的 primary_object_note；词条 `earned_value`、`revenue_recognition`。 | 2（PMI、Ajera） | 为该暂用名补计量单位、价值基准及与 EAC／EV／收入的区别，再交 one-glossary 定名；不把该行当收入确认证据。 |
| R1-F-06 | 命名含义 | VS-02.0–VS-02.6 开始项目至完成施工配合 | OBJ-44 资源需求；OBJ-45 资源分配；PRC-20 | 增补 | 两个对象当前分别是人员工时需求和具名员工计划工时；Unanet 的 Allocation、Resource Schedule 还包括非人工数量、费用与供应商。因此本系统“资源”实际是人力范围，不能用产品的广义资源安排来宣称已经包含外部检测或非人工安排。单源范围提示，不认定这些业务必须纳入。 | [UAE](../../frameworks/notes/unanet-ae.md) `UAE#岗位及费用需求`、`UAE#具名资源安排`、`UAE#非人工资源安排待核`：PM 印刷／PDF p12–13，人工为 Hours／Employee，非人工为 Units／Vendor。 | 1（单源：Unanet） | 补明确的人力范围说明；如确有非人工排程需求，先核供方协议、履约计划的已有承载，不直接扩张两个对象。 |
| R1-F-07 | 命名含义 | VS-02.0–VS-02.6 项目执行；对照 VS-01.1 采集线索 | OBJ-30 WBS 节点；OBJ-105 客户活动；PRC-21、PRC-02 | 增补 | Unanet Activity 是可分派、完成、记时、归档的执行事项；OBJ-105 是一次客户拜访或跟进。两者不能因中文都含“活动”而视作同义。PRC-21 只明确推进工作包，尚未说明小于工作包的事项是否需要这种独立办理语义。 | [UAE](../../frameworks/notes/unanet-ae.md) `UAE#执行事项`、`UAE#工作包下执行事项待核`：PM 印刷／PDF p64–69，Working with Activities，p64 定义 Activity Card、WBS View、Time Entry、Archive。 | 1（单源：Unanet） | 记录“执行事项（提议名）”的适用性线索；明确它与客户活动、工作包的区别，不要求增加 WBS 层级或对象号。 |
| R1-F-08 | 命名含义 | VS-02.2–VS-02.6 确认方案至完成施工配合 | OBJ-41 延期申请；PRC-32 申请并答复工期顺延 | 增补 | OBJ-41 将一般名称“延期申请”限定为发包人原因。FIDIC 的 Extension of Time 还包括变更和 Exceptional Event 等事由，不能把其全部映到仅发包人原因的定义。该单源反例只证明现定义较窄，不能把范本事由或天数直接变成全系统规则。 | [FIDW](../../frameworks/notes/fidic-white-book-2017.md) `FIDW#延期申请`：4.4.1(a)–(d)、4.4.2–4.4.3，印刷 p13／PDF p36；现有 OBJ-41 所据 GF0209 6.3.1 本来就是“因发包人原因”。 | 1（单源反证：FIDIC） | 在定义或限定名中写明只管发包人原因，或补其他适用事由的承载说明；不自动套用任一范本。 |
| R1-F-09 | 命名含义 | VS-03.3 发布不符合项；VS-03.4 跟进整改；VS-02.6 完成施工配合 | OBJ-38 不符合项；PRC-34、PRC-44、PRC-45 | 增补 | 正式词条 `nonconformity / Nonconformity` 已定义为“未满足规定要求的事项”，但 OBJ-38 仍把隐患和技术风险无条件纳入同一名称。上海细则以风险作为报告与处置对象，未将其定义成 Nonconformity；由此提出待核情形：若尚无未满足要求的证据，这类风险是否仍能按正式不符合项定义登记？现有定义没有给出对应判据。不能把“风险未消除”一律读成“已证实不符合”。 | [QINS](../../frameworks/notes/quality-insurance.md) `QINS#最终风险报告`、`QINS#设计风险管控`：上海细则第七条（四）、第八条，印刷／PDF p6–8，分别要求风险报告与风险处置；本系统词条 `nonconformity` 对照 OBJ-38、§2.10。 | 1（单源外部线索：上海细则；内部定义差异已核实） | 补风险、隐患与已确认不符合的含义判据；原 Q32 合并决定不改，记录分类与正式定义的待对齐点。 |
| R1-F-10 | 命名含义 | VS-02.1–VS-02.6 交付阶段；VS-03.5 登记报告认可 | OBJ-35 交付物；OBJ-37 内部评审；PRC-28 | 印证 | “已交付／签收”“内部校审通过”“客户认可”是三个不同事实。OBJ-35 将签发、交付、审查、认可分别表达，未把文件送出或内部核实直接叫作客户验收，含义有依据。 | [GFD](../../frameworks/notes/gf-design-contracts.md) `GFD#交付签收`、`GFD#成果审查`：GF0209 通用7.1–7.3、8.1–8.7，印刷／PDF p20–21；[PMB6](../../frameworks/notes/pmbok6-zh.md) `PMB6#质量核实与验收`：§5.5、§8.3。 | 2（设计合同范本、PMI） | 保留这三个含义；不把产品 review、delivery 或 acceptance 单词逐字互换。 |
| R1-F-11 | 命名含义 | VS-04.6 完成合同履行 | OBJ-09 协议变更；PRC-48 提出并生效协议变更或签证 | 增补 | OBJ-09 定义是“经双方批准的……修改”，生命周期却包括已提出、影响评估、审批。FIDIC 另有范围指令已发、价款尚未谈妥而先行执行的路径。这些候选、指令、已定价修改均称“变更”时，定义缺了批准程度的区分，不能由出现一条变更记录便断言双方已定价。 | [FIDW](../../frameworks/notes/fidic-white-book-2017.md) `FIDW#变更`、`FIDW#变更价款与先行指令`：5.1.3、5.2.1–5.2.4，印刷 p14–15／PDF p37–38；词条 `change_order` 定义为合同正式修改。 | 1（单源：FIDIC） | 补拟议修改、范围指令、定价结果的含义说明；不改已生效基线条件，也不据范本授权先做。 |
| R1-F-12 | 命名含义 | VS-04.1 签署合同；VS-04.2 确认收入；VS-04.3 开具发票 | OBJ-07 付款条件项；OBJ-08 履约义务；PRC-53、PRC-56 | 印证 | 付款节点不等于向客户转让可明确区分服务的承诺。对象稿将二者分开，含义正确；不能因词表旧 `ContractClause` 同时挂收入、成本与开票，就恢复两者同义的解释。C-03、C-04／G-21 的词表映射仍待既有事项处理。 | [CAS14](../../frameworks/notes/cas14-2017.md) 原文第九条，印刷／PDF p4，定义履约义务；[GFD](../../frameworks/notes/gf-design-contracts.md) `GFD#阶段费用与付款`：两本附件6，印刷／PDF p43–44，阶段服务费用和付款安排分列。 | 2（收入准则、设计合同范本） | 保留分义；旧 `contract_clause` 的兼任定义沿已登记问题处理，不按付款比例自动识别履约义务。 |
| R1-F-13 | 命名含义 | VS-04.3 开具发票 | OBJ-82 开票申请；OBJ-83 增值税发票；PRC-57 | 增补 | OBJ-82 同时被定义为“向客户请款”和“要求开具发票的内部账单”，词表 `BillingRequest` 则止于开票并移交应收。Unanet 的商业账单可以生成、过账、送给客户；FIDIC 咨询费账单还有自己的提交及到期含义。税务开票申请通过，不足以表示对客请款已发出；现定义缺这两个用途的明确对应。 | [UAE](../../frameworks/notes/unanet-ae.md) `UAE#商业账单复核`、`UAE#商业账单及销售分录`：BI 印刷／PDF p6、22–26；[FIDW](../../frameworks/notes/fidic-white-book-2017.md) `FIDW#报酬与付款条件`：附件3、7.2.1，印刷 pf、18–19／PDF p16、41–42。 | 2（Unanet、FIDIC） | 说明内部开税票请求与对客请款账单的身份、发送含义及对应关系；先核 OBJ-82、证据与现有单据，不预设新增对象。 |
| R1-F-14 | 命名含义 | VS-01.2 赢得委托；VS-02.0 开始项目；VS-04.1 签署合同 | OBJ-26 标准成本费率表；OBJ-27 计费系数；OBJ-05 协议；PRC-13、PRC-47 | 增补 | 对客履约计费费率不是当然由标准成本费率乘系数得到：Unanet 分列 Pay、Job Cost、Bill Rates；Ajera 的计费表还支持按人、活动、供方及成本加价选价。现有售前派生单价与正式 `BillRateCard` 仍不能按同名视作同一个概念。这是 §2.5、C-20 的已知问题复核，不是新发现费率对象。 | [UAE](../../frameworks/notes/unanet-ae.md) `UAE#费率方案`、`UAE#费率版本及覆盖`：AD 印刷／PDF p41–43；[AJE](../../frameworks/notes/ajera.md) `AJE#计费费率卡`：实施指南印刷／PDF p34。 | 2（Unanet、Ajera） | 补售前估价单价、协议约定收费单价、标准成本单价的适用范围与版本对应；不改变现有售前乘数决策。 |
| R1-F-15 | 命名含义 | VS-08.2 完成报销核定；VS-08.3 完成报销付款 | OBJ-88 费用报销；PRC-87 提交、核定并确认报销应付 | 增补 | OBJ-88 定义限定为“员工为公务垫付后……公司偿付”，却把“公司直付”列为属性取值，且统一列出“应付（员工）”。公司卡消费由公司向发卡机构结算时，并不存在员工垫付这一事实。Ajera 的公司卡例子反证这两类支出不能都解释为“员工垫付报销”。Unanet 这里只印证员工偿付，不能凑作公司直付的第二个反证源；故按单源增补线索处理。问题在定义与类型的含义，不在是否允许共用费用确认记录。 | [UAE](../../frameworks/notes/unanet-ae.md) `UAE#费用申报及报销`：AD 印刷／PDF p28，Expense Sheets 导入 Employee Reimbursables；Ajera 实施指南原文锚点 Phase 3 / Bank register，印刷／PDF p55：费用报告中的公司卡项进入卡账户登记；Phase 2 / Bank accounts，p30 分列公司银行及信用卡账户。资料身份见 [ajera.md](../../frameworks/notes/ajera.md)「附：资料与核对边界」；这是回原文补出的定位，不冒用其他 AJE 概念锚点。 | 1（单源反证：Ajera；Unanet 仅为员工偿付旁证，不另计） | 将定义补为能够区分员工垫付、公司直付的费用确认含义，或明确公司直付另有承载；保留两类实际付款方及债权人含义，不替用户决定对象拆合。 |
| R1-F-16 | 命名含义 | VS-05.3 确认供方履约；VS-05.4 确认应付款项 | OBJ-87 供方履约确认；OBJ-83 增值税发票；OBJ-85 往来款项；PRC-72、PRC-73、PRC-74 | 增补 | Unanet Vendor Invoice 是供应商提出金额、编号及应付的商业账单，不是中国进项增值税发票。现有流程名只有履约确认、查验税票、提交应付，未说明供应商商业请款单是履约确认的单据、应付的依据还是税票。不能仅凭 Invoice 同词将其全映到 OBJ-83。 | [UAE](../../frameworks/notes/unanet-ae.md) `UAE#供方账单及采购分录`：AP 印刷／PDF p10–13，Purchase Journal / Vendor Invoice，p10 的账单标识由应付账户、供应商、账单号组成。 | 1（单源：Unanet） | 补供应商请款单的单据出口与税票区别；优先解释现有组成部分，不据产品账单新建一级对象。 |
| R1-F-17 | 命名含义 | VS-09.1 编制计划；VS-09.2 变更预测；VS-09.3 完成经营复盘 | OBJ-100 经营计划；PRC-88、PRC-89、PRC-181 | 印证 | 现有对象已用承诺、挑战、预测、预算等目标类型区分数据，滚动预测也已有 PRC-89，不能再报“缺预测对象／流程”。SPI 的预计增长、季度签单预测与年度收入目标回答不同问题，支持保留这些类型的不同含义。 | [SPI26](../../frameworks/notes/spi-psmb-2026.md) `SPI26#预期收入增长`、`SPI26#商机管道覆盖倍数`、`SPI26#年度收入目标达成率`：附录E题41、64、143；印刷 p243–244／PDF p250–251；签单管道正文表110–111，印刷 p111／PDF p118。 | 1（SPI） | 保留类型区别；不得用目标基线代替预测版本，也不以共同对象名抹掉二者含义。 |
| R1-F-18 | 命名含义 | VS-04.4 回收款项；VS-05.4 确认应付款项；VS-05.5 完成付款 | OBJ-85 往来款项；PRC-59、PRC-74、PRC-176 | 增补 | OBJ-85 总定义为“无条件收款权或付款义务”，类型却含预收、预付及可能仍附履约条件的质保金。准则上的合同负债与仍附其他条件的合同资产，不能仅凭放入同一开放项对象就叫作无条件收款权。统一往来款项的定义缺少覆盖这些类型的上位含义。 | [CAS14](../../frameworks/notes/cas14-2017.md) 原文第四十一条，印刷／PDF p18–19：合同资产、合同负债、无条件应收分开定义；对象稿 §2.4 自记客户扣留价款“属合同资产或应收，待 F-03”；词条 `retention_money` 注记同样保留条件差别。 | 1（单源：收入准则） | 补开放项中结算跟踪与准则权利分类的区别；不改变一个对象加方向、预收预付或质保金的既定归类。 |
| R1-F-19 | 命名含义 | VS-07.2 完成录用；VS-07.3 完成人员入职与任职 | OBJ-56 人员；PRC-82、PRC-83 | 印证 | `Person` 已正式包含招聘过程中的自然人，`Employee` 保留为人员的一种类型。候选人并入人员生命周期不等于候选人已是员工；目前词表已经给出这个区别，对象稿“改名仍待查重”的尾注不是词表现状。 | [SIL2](../../frameworks/notes/silverston-dmrb2.md) `SIL2#人员`、`SIL2#主体角色关系`：第7章 Person Roles，图7.1；[LAB](../../frameworks/notes/labor-contract.md) `LAB#劳动关系建立`：劳动合同法第七条，PDF p4。本系统正式词条 `person`、`employee`。 | 2（Silverston、劳动合同法） | 按已批准 Person／Employee 分义回读；不新增人员对象，也不把候选人视作用工已开始。 |
| R1-F-20 | 命名含义 | VS-07.1 提出招聘需求；VS-15.2 批准生效 | OBJ-58 岗位；PRC-81、PRC-80 | 增补 | 对象稿岗位是“职责类别，可配职级区间”；词表 `position / Position` 仍定义“任职与费率适用的岗级体系”，另有 `JobGrade` 表示职级。两处对职责与等级的含义未对齐。Unanet 的 Job Title 可以表示职责、级别或两者，因此不能反过来声称业界一律要求 Position 只表示职责。 | [UAE](../../frameworks/notes/unanet-ae.md) `UAE#职称与岗位`：AD 印刷／PDF p33，Job Title 三种用法；SP p10，Default Job Title 与 Project Role 分列；对照词条 `position`、`job_grade` 与对象稿 §2.6。 | 1（单源外部证据：Unanet；内部定义差异已核实） | 按已定“不管编制、只管职责目录”补定义与职级的关系；交 one-glossary 处理，不以产品术语直接替换正式名。 |
| R1-F-21 | 命名含义 | VS-06.1 完成申报缴款；VS-07.3 完成人员入职与任职；VS-13.4 申报缴纳；VS-13.6 离职结清 | OBJ-62 社保与公积金参保；PRC-84、PRC-125、PRC-130 | 增补 | OBJ-62 用“参保地、险种参保、增减员”同时定义社保与公积金；公积金原文是缴存登记及账户设立、转移、封存。公积金账户封存不等于某保险险种终止参保，复合名称的后半部分缺相应定义。未找到该对象正式英文，不擅称已有英文误译。 | [SOC](../../frameworks/notes/social-insurance.md) `SOC#公积金账户唯一`、`SOC#公积金增减员`、`SOC#公积金汇缴期限`：住房公积金管理条例第十三至十五条，印刷／PDF p5–6；第十九条，p7。 | 1（单源：公积金条例） | 补公积金缴存关系及账户状态的含义；社保参保与公积金缴存按类型明确，不要求拆对象。 |
| R1-F-22 | 命名含义 | VS-12.1 确认收到出资；VS-12.3 退还出资 | OBJ-91 合伙人出资；PRC-111、PRC-114 | 增补 | OBJ-91 定义同时覆盖合伙人或股东，但对象名及两个流程名只称“合伙人出资”。Silverston 明确列出 SHAREHOLDER 角色；普通股东出资是否也受“合伙人出资”覆盖，现有中文名称窄于其明示定义。书中组织业务角色 PARTNER 不作为中国法“合伙人”的译名证据。没有正式英文词条，不把草稿译名当成已定名。 | [SIL2](../../frameworks/notes/silverston-dmrb2.md) `SIL2#股东`、`SIL2#主体角色关系`：第7章 Person Roles，图7.1，`14_chapter_7.html`。 | 1（单源：Silverston） | 提议用能覆盖两类主体的“股东／合伙人出资（提议名）”，或在名称对应说明中明确覆盖；不审退资合法性，不改阶段设置。 |
| R1-F-23 | 命名含义 | VS-11.1–VS-11.3 知识编制、发布、修订废除；对照 VS-02.7 完成收尾 | OBJ-110 知识条目；OBJ-107 项目复盘；PRC-107、PRC-38、PRC-182 | 印证 | 项目复盘记录一次项目的总结，知识条目承载可跨项目复用的内容，二者不是同义。PRC-182 已有知识使用查看，不能再据 SPI 关卡把“知识使用”报为全无；使用记录是否充分则是另一项验证问题。 | [PMB6](../../frameworks/notes/pmbok6-zh.md) `PMB6#项目知识`：§4.4，EPUB `OEBPS/text00000.html`；[SPI26](../../frameworks/notes/spi-psmb-2026.md) `SPI26#知识管理有效性`：附录E题130，印刷 p244／PDF p251；笔记关卡明确指出 PRC-182 已存在。 | 2（PMI、SPI） | 保留复盘来源、知识版本与使用情况的分义；不把知识文件数量当作复用效果。 |

## 2. 框架层疑点

以下只记录，不提出调整建议，也不计入三类条数。

- 资料的时段履约、产出／投入进度计量与本系统 A1／ADR-0016 的时点口径如何对应，仍有已登记边界（对象稿 §2.7、C-44）。CAS14 第十一至十三条与第四十一条可以作为核对材料；本卡没有以外部产品的 earned value、WIP 或账单进度推翻该决定。
- 销售应收只由蓝字发票产生，与准则“无条件收款权”的判定并非同一个命题（CAS14 第四十一条；对象稿 C-12）。该路径属于既定会计／流程决定，本卡未据此要求改路径。R1-F-18 只讨论统一开放项的上位定义。
- 资源安排、执行事项、公司直付等来源语义，是否应成为独立对象、由哪个 BC 承载、是否改变阶段，均超出命名含义层；本文没有从资料菜单或记录数推出对象层级与边界。
- APQC 笔记按旧 `togaf/value-streams.yaml` 对照，其 VS 编号不能直接套用本版；APQC 对企业合规、员工服务和 IT 管理提出的广范围查漏，并不单独证明本版价值流须新增或重排。SPI 成熟度级别也不构成企业当前成熟度的实测。

## 3. 汇总

共 23 条：**印证 6 条、增补 17 条、冲突 0 条**。印证 6 ≤（增补17＋冲突0）÷2。没有形成满足“两独立反证源”门槛的冲突：内部定义有矛盾、单一产品有反例，仍不足以强行列成双源冲突；R1-F-15 已据此降级。

增补与冲突逐条如下：

- R1-F-02 增补：非招标 RFI／RFP／RFQ 与线索、商机、招标项目的对应未说明（单源）。
- R1-F-03 增补：WorkPackage 的末级含义与 DeliveryPackage 的重复定义待对齐（已知 C-19，单源家族）。
- R1-F-04 增补：ETC 工时／数量与 ETC 金额未在定义中明确区分。
- R1-F-05 增补：“经营产值”缺计量单位及与 EAC、EV、收入的分义。
- R1-F-06 增补：“资源”当前为人力范围，不能按广义 Resource Schedule 宣称非人工也覆盖（单源）。
- R1-F-07 增补：执行 Activity 不等于客户活动；小于工作包的事项仅为适用性线索（单源）。
- R1-F-08 增补：“延期申请”现只管发包人原因，不能覆盖所有 Extension of Time（单源）。
- R1-F-09 增补：对象稿技术风险的宽口径与正式 Nonconformity 定义待对齐（单源外部线索）。
- R1-F-11 增补：“协议变更”定义缺拟议、范围指令与定价程度的区别（单源）。
- R1-F-13 增补：开税票请求与对客请款账单的身份、发送含义未分清。
- R1-F-14 增补：售前派生单价与合同履约计费费率的同名异义仍待处理（已知 C-20）。
- R1-F-15 增补：公司直付费用不能同时被定义为员工先垫付、再向员工偿付（单源反证，已降级）。
- R1-F-16 增补：供应商商业请款单不能仅凭 Invoice 同名等同进项增值税发票（单源）。
- R1-F-18 增补：含预收预付与有条件质保金的开放项，上位定义不能只写无条件现金权利义务（单源）。
- R1-F-20 增补：岗位职责目录与 position 的岗级体系定义未对齐（单源外部证据）。
- R1-F-21 增补：社保参保与公积金缴存登记、账户封存含义不同（单源）。
- R1-F-22 增补：“合伙人出资”名称窄于股东或合伙人共同覆盖的定义（单源）。

覆盖回读：VS-01 对应01、02、14；VS-02 对应03–10、14、23；VS-03 对应09、10；VS-04 对应11–14、18；VS-05 对应01、16、18；VS-06 对应21；VS-07 对应19–21；VS-08 对应15；VS-09 对应17；VS-10 已核阶段“记账结转／关闭会计期间／发布报表”、会计分录与记账凭证的载体说明及外部法定账对账定义，未形成新的充分命名反证；VS-11 对应23；VS-12 对应22；VS-13 对应21，另核工资、考勤、薪酬结算与发放未混为同一状态；VS-14 已核用印、IT 工单、资产使用及活动的对象与流程定义，维修办理进度和修复结果已分列，未形成新的充分命名反证；VS-15 已核拟订、批准生效、修订停用及规则／参数的明示范围，未因其共用上位对象就认定同义错误。未形成反证不代表所有内容已被外部资料穷尽证明。

## 4. 自测与交付边界

### 已验证

- **PASS：编号与引用。** 对本文全部引用去重后，32 个对象号在对象总表的现存行、41 个流程号在流程目录、50 个价值流／阶段号在 v2 定义均能检索到，缺失 0。41 个所引流程均非 retired／merged，且 stages 非空。用 `rg -n` 回查编号，并以 YAML 定义及对象表行首作精确校验，未将历史叙述中的编号命中当作定义存在。
+- **PASS：表格与计数。** 23 行、每行9列；编号连续 R1-F-01 至 R1-F-23；类别只取三种规定值；6印证／17增补／0冲突；印证比例符合限制。未新增任何对象编号。
+- **PASS：来源门槛。** 冲突数为0；未把不足双源的线索升级。单源反例已注明“单源”。文内既有笔记锚点和相对文件链接均能找到；R1-F-15 的公司卡资料另给原文真实小节与页码，没有伪造笔记概念锚点。
+- **PASS：随机抽5条回原文。** 用 `sorted(random.Random(101815).sample(range(1, 24), 5))` 得到 **02、04、06、08、19**，抽取后逐条回读，结果如下；这5条是条目抽样，不把同一页回读算成两条独立资料。
+  - **02：** 直接读 SIL2 EPUB 的 `14_chapter_7.html`，Professional Services Requests and Quotes / Professional Services Requests，图7.5上下文。确认 RFI／RFP／RFQ 是请求类别；原文不证明本企业一定开展全部请求类型，故只保留对应待核。
+  - **04：** 直接读 PMB6 EPUB `OEBPS/text00002.html` §7.4.2.2“预测”的 EAC＝AC＋自下而上 ETC 段，并用 `pdftotext -f 12 -l 13 -layout` 回读 Unanet PM。确认成本公式与 ETC Units／ETC Amount 的区分；未据此断言代码计算错误。
+  - **06：** 回读同一 Unanet PM PDF p12–13 的 Allocations／Resource Schedule Pane。确认人工与非人工分别用 Hours／Units、Employee／Vendor；本企业非人工排程的适用性仍未核，保持单源线索。
+  - **08：** 直接渲染并目视核对 FIDIC 白皮书 PDF p36（页脚13），4.4.1(a)–(d)、4.4.2–4.4.3。确认服务变更、客户原因、Exceptional Event 及其他依协议享有延期权的事由分列；没有将其统一改称发包人原因。
+  - **19：** 直接读 SIL2 第7章 Person Roles／Organization Roles 的人员角色段，并以 `pdftotext -f 4 -l 4 -layout` 回读劳动合同法第七条。确认人员角色区别及用工起始日；再回读正式词条 `person`、`employee`，没有沿用对象稿“英文未定”的旧尾注。
+- **PASS：委派结论回读。** 只读研究助手提供的人事、岗位、公积金、出资线索已由本会话回读词表及对应 Unanet、SIL2、劳动合同法、公积金条例原文；未采用未批准草稿中的英文名。不是仅凭助手转述形成报告。
+- **PASS：未提交结果文件扫描。** gitleaks 8.30.1 另用 `gitleaks stdin --no-banner --redact < docs/architecture/proposals/adversarial/r1-f.md` 扫描本文件，退出码0，未发现密钥；不以全历史扫描替代未提交文件检查。
- **PASS：改动边界。** 本卡写入路径仅为本文件。工作区其余未跟踪报告属于并行工作，未编辑、删除或覆盖；本卡不通过提交来解除检查跳过。
+
+执行命令：`PYTHONDONTWRITEBYTECODE=1 bash scripts/precheck.sh`；退出码 **0**。基线 `origin/main` 为本机已有引用，未 fetch。该脚本统计整个分支与现有工作区，因此“288 changed files／6 uncommitted”不是本卡改动文件数。完整输出如下，每项 SKIP 的原因均由脚本列明：
+
+```text
+precheck: base origin/main (3c926c2f), HEAD 6d92b141, 288 changed files (6 uncommitted)
+
+SKIP  generated docs (build_all --check)     docs or backend/glossary/proposed has uncommitted changes: commit first, then run again (请先提交再运行)
+PASS  legacy system name                     check_legacy_name: 无违规
+PASS  decision pages: question rules (self-test) check_pages self-test: ok
+PASS  decision pages: question rules         check_pages: 13 张页，0 处问题
+SKIP  migrations immutable                   no migration, marker or Flyway setting changed
+SKIP  system actors append-only              system-actors.txt and its script did not change
+SKIP  acceptance guard                       no acceptance test changed
+SKIP  database scripts aligned               no database script or migration changed
+SKIP  docs read by builds are registered     no backend, frontend or script changed
+SKIP  assertion drift (warning only)         no test file changed
+SKIP  glossary: check:copy, test:scripts, term tests terms.yaml, bcs.yaml and backend/glossary unchanged
+SKIP  guards: backend guard tests            no guard test or architecture list changed
+SKIP  release scripts self-test, shellcheck  relevant_change.py: nothing the release scripts or the builds read changed
+PASS  gitleaks (full history)                3099 commits scanned; no leaks
+      note: uncommitted files are not in history; the pre-commit hook scans them on commit
+SKIP  backend tests                          relevant_change.py: nothing the builds read changed
+SKIP  frontend verify                        relevant_change.py: nothing the builds read changed
+SKIP  SQL Server integration tests           relevant_change.py: nothing the *MssqlIT tests exercise changed
+
+SUMMARY: 4 PASS, 0 FAIL, 13 SKIP (base origin/main) — ready
+```
+
+生成文档一致性未运行的原因是存在未提交文档，而本卡明确禁止提交且只准写一个文件；未为解除该 SKIP 修改其他文档或生成产物。业务验收测试未指定，本结果不声称业务验收转绿。

### 未运行

- 未联网、未登录或实际执行业务产品流程；产品原文只证明该版资料记述或静态结构，不证明运行和业务验收。
- 未运行代码、验收测试、容器、构建或数据库检查；本卡没有这些文件的改动，也未指定业务验收测试。
- 未对全部原始资料逐页通读；除抽查和必要细节回读外，其余来源按笔记的可定位锚点使用。
- 没有正式英文对应的对象、流程和阶段未完成英文定名验收；没有从草稿翻译推断英文已经批准。
- 未更改术语表、框架、ADR、对象稿、价值流、流程目录；未提交、未推送。本文建议与拿不准的适用性只保存在本文件。
