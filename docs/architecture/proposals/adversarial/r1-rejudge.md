# ARC-1018-ARC-19：对抗第 1 轮受旧编号影响条目重判

状态：编号影响复核结果；仅本文件，不改原裁决或任何正本。

## 口径与输入

逐条读取 [R1-A](r1-a.md)、[R1-B](r1-b.md)、[R1-C](r1-c.md)、[R1-D](r1-d.md)、[R1-E](r1-e.md)、[R1-F](r1-f.md) 的论断、证据、价值流与阶段、本系统条目和建议；再核对 [受影响清单](r1-affected.tsv) 所列 24 个笔记编号的相关锚点句、[修正日志](VS-FIX-LOG.md) 的前后写法。49 份是 ARC-18 修改笔记总数，本清单实际涉及其中 24 份，不把未在清单中的其他 R1 条目补入本轮。

阶段编号、名称、进入／退出与贯穿活动以 [价值流 v2](../value-streams.v2.yaml) 为准；对象定义、类型及状态以 [信息图](../ENTERPRISE_INFO_MAP_DRAFT.md) 为准；流程有效性、对象与归段以 [流程目录](../closeout/process-catalog.yaml) 为准。原裁决去向逐字保留清单第二列，并对照 [R1 裁决](r1-ruling.md)。笔记和原报告使用旧正本路径的历史说明不作为本轮归段依据。

“重判”仅取 **不受影响／需改挂接／结论改变**。“新价值流与阶段”的“沿用”表示原条目已按 v2 定位，范围写法沿原文，端点之间按 v2 的现行阶段理解；相邻、对照、来源、观察场景不等于给流程另加归段。“新类别”仅在结论改变时填印证／增补／冲突／驳回，其余填“—（不变）”，不把原去向的“用户定”“转规则层”等另造为类别。

“不受影响”只表示未发现该论断及其本系统条目依赖错挂的价值流或阶段，**不表示重新批准原增补，也不表示确认原驳回已完成原文实质复审**。例如 A 组 §2 已说明自行重定位，E 组也说明旧号不进入本轮映射；需检查具体行而不能按笔记文件名整批翻案。原有“单源”“适用性待证”“业务口径待用户定”继续保留。

## 逐条重判

| 编号 | 原裁决去向 | 重判 | 新价值流与阶段 | 新类别 | 理由 |
|---|---|---|---|---|---|
| [R1-A-04](r1-a.md) | 转 one-rules 或实现层，不在本轮内容层处理 | 不受影响 | 沿用：VS-01.2 赢得委托 | —（不变） | UAW#赢单与推进概率、资金概率区分三类判断；笔记改的是资源、成本、收入落点，概率仍在 VS-01.2。OBJ-18 与 PRC-183 原已挂赢得委托，转规则层的去向不因编号变化而改变。（所引笔记：[UAW](../../frameworks/notes/unanet-whitepapers.md)。） |
| [R1-A-05](r1-a.md) | 驳回（反方为关键词复核，第 2 轮抽 20% 回原文复核） | 不受影响 | 沿用：VS-01.2 赢得委托 | —（不变） | UAW#概率复评要求用新信息与实际结果复评，概率段未改号。原文按 PRC-183、PRC-03 判断是否已有复评，不以旧资源阶段作依据；原驳回及后续原文复核安排保留。（所引笔记：[UAW](../../frameworks/notes/unanet-whitepapers.md)。） |
| [R1-A-06](r1-a.md) | 记录 | 不受影响 | 沿用：VS-01.2 赢得委托 | —（不变） | UAW#结构化决策评分、概率协作评估对应参投判断；PRC-183 当前即归 VS-01.2。修正资源、成本及收入段不影响“已有参投决策”的印证。（所引笔记：[UAW](../../frameworks/notes/unanet-whitepapers.md)。） |
| [R1-A-07](r1-a.md) | 转 one-rules 或实现层，不在本轮内容层处理 | 不受影响 | 沿用：VS-01.2 赢得委托；VS-02.0 开始项目至后续指示、成果认可 | —（不变） | GFD#合同文件顺序的签约落点已改 VS-04.1，但本条补核的是代表授权范围和历史，检查 OBJ-03、OBJ-34 及指示／认可记录；没有把售前赢单当作合同回签。协议签署段改号不推翻授权细节转规则层的去向。（所引笔记：[GFD](../../frameworks/notes/gf-design-contracts.md)。） |
| [R1-A-08](r1-a.md) | 驳回（反方为关键词复核，第 2 轮抽 20% 回原文复核） | 不受影响 | 沿用：VS-01.2 赢得委托；VS-02.0 开始项目 | —（不变） | UAW#确定与潜在储备、资源规划闭环的资源视图已由旧单段改为 VS-02 贯穿活动；原文已区分 VS-01.2 商机预期和 VS-02.0 建项目。OBJ-44、OBJ-45 的项目依附范围及原“已覆盖”裁决不因笔记改号改变。（所引笔记：[UAW](../../frameworks/notes/unanet-whitepapers.md)。） |
| [R1-A-10](r1-a.md) | 驳回（反方为关键词复核，第 2 轮抽 20% 回原文复核） | 不受影响 | 沿用：VS-02.0 开始项目；VS-02 各执行段的人员更换 | —（不变） | GFD#设计分包责任所在原文补核人员替换，GFQ#造价团队授权明确通知／同意。当前笔记仍落 OBJ-34 与 PRC-17，原文使用启动及执行各段，没有沿旧签约或变更号推定团队更换属于别流。（所引笔记：[GFD](../../frameworks/notes/gf-design-contracts.md)、[GFQ](../../frameworks/notes/gf-consulting-contracts.md)。） |
| [R1-A-11](r1-a.md) | 转 one-rules 或实现层，不在本轮内容层处理 | 不受影响 | 沿用：VS-02.0 开始项目；VS-02.1–VS-02.6 执行 | —（不变） | PMGB#范围与工作单元的计划预算落点改为 VS-02.0；原文已用 VS-02.0 和执行各段，OBJ-30、OBJ-31、OBJ-32 未换对象。工作单元质量、责任和接口细节仍按原去向处理。（所引笔记：[PMGB](../../frameworks/notes/gbt50326-2017.md)。） |
| [R1-A-15](r1-a.md) | 用户定（汇入确认页） | 不受影响 | 沿用：VS-02.2 确认方案至VS-02.6 完成施工配合 | —（不变） | GFD#设计变更及索赔、GFQ#全咨变更程序现区分 VS-02 技术变更与 VS-04.6 协议变更；本条只问 OBJ-40／PRC-31 的书面指示来源，原挂 VS-02.2 至 VS-02.6，与目录一致，不把价款变更塞回设计段。（所引笔记：[GFD](../../frameworks/notes/gf-design-contracts.md)、[GFQ](../../frameworks/notes/gf-consulting-contracts.md)。） |
| [R1-A-16](r1-a.md) | 转 one-rules 或实现层，不在本轮内容层处理 | 不受影响 | 沿用：VS-02.2–VS-02.6 变更执行 | —（不变） | GFQ#全咨变更影响现保留“获指令而费用未商定”，技术执行沿 VS-02；原文的 OBJ-40、OBJ-12、PRC-31、PRC-16 正是在交付侧保留授权、耗时与未定价依据。没有用旧 VS-02.5 作为变更专段。（所引笔记：[GFQ](../../frameworks/notes/gf-consulting-contracts.md)。） |
| [R1-A-17](r1-a.md) | 转 one-rules 或实现层，不在本轮内容层处理 | 不受影响 | 沿用：VS-02.2–VS-02.6 延期办理 | —（不变） | GFD#设计变更及索赔所在条款补核延期事由和约定期限；OBJ-41／PRC-32 当前仍归 VS-02.2 至 VS-02.6。改将协议变更另列 VS-04.6，不改变本条延期申请范围和规则层去向。（所引笔记：[GFD](../../frameworks/notes/gf-design-contracts.md)。） |
| [R1-A-18](r1-a.md) | 驳回（反方为关键词复核，第 2 轮抽 20% 回原文复核） | 不受影响 | 沿用：VS-02.0–VS-02.7 暂停、恢复 | —（不变） | GFD#施工配合边界所在暂停条款补核全部／部分服务，PRC-24 当前覆盖 VS-02.0 至 VS-02.7。笔记签约、收入和技术变更改号都不决定项目暂停通知的内容，原驳回不据此改判。（所引笔记：[GFD](../../frameworks/notes/gf-design-contracts.md)。） |
| [R1-A-19](r1-a.md) | 驳回（反方为关键词复核，第 2 轮抽 20% 回原文复核） | 不受影响 | 沿用：VS-02.1 确认概念至VS-02.5 完成招标采购配合 | —（不变） | GFD#成果审查涉及我方成果；原文直接比对 PRC-34 仅挂 VS-02.6、VS-02.7 与 VS-02 的贯穿活动，未借用 QINS 的客户资产整改旧挂接。早期处置关联问题及原驳回须按原后续复核处理。（所引笔记：[GFD](../../frameworks/notes/gf-design-contracts.md)。） |
| [R1-A-20](r1-a.md) | 驳回（反方为关键词复核，第 2 轮抽 20% 回原文复核） | 不受影响 | 沿用：VS-02.1–VS-02.6 成果送达、退回重报 | —（不变） | GFD#交付签收、成果审查及 GFQ#全咨成果审查的相关句仍要求分存送达、意见和认可日期并关联新轮次；原文用 OBJ-35、OBJ-53、PRC-28 的交付各段，没有依赖旧收入确认号。原裁决不因换号改变。（所引笔记：[GFD](../../frameworks/notes/gf-design-contracts.md)、[GFQ](../../frameworks/notes/gf-consulting-contracts.md)。） |
| [R1-A-21](r1-a.md) | 驳回（反方为关键词复核，第 2 轮抽 20% 回原文复核） | 不受影响 | 沿用：VS-02.3 完成初步设计；VS-02.4 完成深化设计 | —（不变） | GFD#成果审查、GFQ#全咨成果审查说明外部意见及重新报审；原文直接检查 VS-02.3、VS-02.4 和 OBJ-35／OBJ-53 的事实区分，不将旧“成果验收”段作为政府审查段。编号修正不裁定该细节是否已覆盖。（所引笔记：[GFD](../../frameworks/notes/gf-design-contracts.md)、[GFQ](../../frameworks/notes/gf-consulting-contracts.md)。） |
| [R1-A-23](r1-a.md) | 记录 | 不受影响 | 沿用：VS-02.1–VS-02.6；VS-03.5 登记报告认可 | —（不变） | GFD#交付签收、成果审查相关句仍区分送达与认可；PRC-28 目录现覆盖 VS-02.1 至 VS-02.6 和 VS-03.5，正是原文范围。内部评审、签收、客户认可分立的印证保留。（所引笔记：[GFD](../../frameworks/notes/gf-design-contracts.md)。） |
| [R1-A-24](r1-a.md) | 驳回（反方为关键词复核，第 2 轮抽 20% 回原文复核） | 不受影响 | 沿用：VS-03.1 编制检查计划 | —（不变） | QINS#风险管理委托的原条款是保险公司交底、初步报告与计划；笔记把签约分为客户／供方两段，不改变本条检查策划。原文已挂 OBJ-48／PRC-41、VS-03.1，没有照搬旧签约号。（所引笔记：[QINS](../../frameworks/notes/quality-insurance.md)。） |
| [R1-A-25](r1-a.md) | 驳回（反方为关键词复核，第 2 轮抽 20% 回原文复核） | 不受影响 | 沿用：VS-03.2 完成现场检查至VS-03.4 跟进整改 | —（不变） | QINS#设计风险管控所在句的整改从旧施工配合改到 VS-03.4；原文已用 VS-03.2 至 VS-03.4，并直接指出 OBJ-52 委托方定义。委托角色／费用范围问题不依赖旧号，原单源限制及驳回安排保留。（所引笔记：[QINS](../../frameworks/notes/quality-insurance.md)。） |
| [R1-A-26](r1-a.md) | 转 one-rules 或实现层，不在本轮内容层处理 | 不受影响 | 沿用：VS-03.3 发布不符合项；VS-03.4 跟进整改 | —（不变） | QINS#设计整改证据仍指特定严重风险整改节点和异议处置；原文已用 VS-03.3、VS-03.4 及 PRC-44、PRC-45，未把独立风控整改当作 VS-02.6 我方成果返工。转规则层的去向不变。（所引笔记：[QINS](../../frameworks/notes/quality-insurance.md)。） |
| [R1-A-27](r1-a.md) | 驳回（反方为关键词复核，第 2 轮抽 20% 回原文复核） | 不受影响 | 沿用：VS-03.2 完成现场检查；VS-03.4 跟进整改 | —（不变） | QINS#检测通知明确计划确认及合格／不合格通知时间；PRC-46 当前在 VS-03.4，原文已同时列 VS-03.2、VS-03.4。通知证据细节与笔记旧签约、报告落点无关。（所引笔记：[QINS](../../frameworks/notes/quality-insurance.md)。） |
| [R1-A-28](r1-a.md) | 驳回（反方为关键词复核，第 2 轮抽 20% 回原文复核） | 不受影响 | 沿用：VS-03.4 跟进整改 | —（不变） | QINS#设计整改证据、整改验收现已将独立检查整改指向 VS-03.4；原文同样用 OBJ-38、OBJ-49、OBJ-53、PRC-45 核对前后材料及监理验收。没有因旧施工段形成错误对象归属。（所引笔记：[QINS](../../frameworks/notes/quality-insurance.md)。） |
| [R1-A-29](r1-a.md) | 驳回（反方为关键词复核，第 2 轮抽 20% 回原文复核） | 不受影响 | 沿用：VS-03.5 登记报告认可 | —（不变） | QINS#最终风险报告的落点由旧收尾改为 VS-03.5；原文已经在 VS-03.5／PRC-28 区分技术签发、保险公司审核盖章和送达。原驳回不因笔记追上此编号而自动反转。（所引笔记：[QINS](../../frameworks/notes/quality-insurance.md)。） |
| [R1-A-30](r1-a.md) | 记录 | 不受影响 | 沿用：VS-03.4 跟进整改；VS-03.5 登记报告认可 | —（不变） | QINS#最终风险报告仍要求严重风险整改，OBJ-38 的规则明确严重风险未闭合不得签发最终报告；原文已挂 VS-03.4、VS-03.5。印证的是签发约束，没有依赖旧成果验收段。（所引笔记：[QINS](../../frameworks/notes/quality-insurance.md)。） |
| [R1-B-01](r1-b.md) | 记录 | 不受影响 | 沿用：VS-04.1 签署合同；VS-04.2 确认收入 | —（不变） | GFD#阶段费用与付款仍分列阶段费用和付款安排；笔记收入段改成 VS-04.2，原文已用 VS-04.1／VS-04.2，且 OBJ-07 与 OBJ-08 分立。概念区分的印证不变。（所引笔记：[GFD](../../frameworks/notes/gf-design-contracts.md)。） |
| [R1-B-05](r1-b.md) | 用户定（汇入确认页） | 不受影响 | 沿用：VS-04.6 完成合同履行 | —（不变） | GFQ#全咨变更影响未改“先指令、后定价”的含义；PRC-48 当前归 VS-04.6，原文正是在 OBJ-09 检查这一中间情形，未将旧技术变更阶段当合同生效段。（所引笔记：[GFQ](../../frameworks/notes/gf-consulting-contracts.md)。） |
| [R1-B-07](r1-b.md) | 驳回（反方为关键词复核，第 2 轮抽 20% 回原文复核） | 不受影响 | 沿用：VS-04.4 回收款项；VS-05.5 完成付款 | —（不变） | GFQ#全咨争议款项的句子区分争议数额与无争议余款；原文按 OBJ-85、PRC-61、PRC-75 分列客户回收与对供方付款，已经是 VS-04.4／VS-05.5。旧结算号修正不影响原裁决。（所引笔记：[GFQ](../../frameworks/notes/gf-consulting-contracts.md)。） |
| [R1-B-12](r1-b.md) | 驳回（反方为关键词复核，第 2 轮抽 20% 回原文复核） | 不受影响 | 沿用：VS-04.5 确认最终价款；VS-05.6 完成供方结算 | —（不变） | GFQ#全咨争议款项相邻终止支付条款及 FIDIC 的权利存续用于核对终止后清算；笔记结算已改 VS-04.5，原文亦用该段和 VS-05.6。没有把旧 VS-03.6 当现行报告阶段。（所引笔记：[GFQ](../../frameworks/notes/gf-consulting-contracts.md)。） |
| [R1-B-21](r1-b.md) | 驳回（反方为关键词复核，第 2 轮抽 20% 回原文复核） | 不受影响 | 沿用：VS-08.2 完成报销核定 | —（不变） | AJE#费用复制限制相关句已由旧供方付款改到 VS-08.2；原文就是 PRC-87／OBJ-88 的复制后重验，且限定存在复制入口时适用。产品是否构成增补仍沿原驳回处理。（所引笔记：[AJE](../../frameworks/notes/ajera.md)。） |
| [R1-B-22](r1-b.md) | 记录 | 不受影响 | 沿用：VS-08.2 完成报销核定；VS-08.3 完成报销付款 | —（不变） | AJE#移动费用状态相关句改为 VS-08.2；原文已分 VS-08.2 核定和 VS-08.3 付款，PRC-87、PRC-133 分别处理。批准不等于已付的印证不依赖旧供方段。（所引笔记：[AJE](../../frameworks/notes/ajera.md)。） |
| [R1-B-23](r1-b.md) | 驳回（反方为关键词复核，第 2 轮抽 20% 回原文复核） | 不受影响 | 沿用：VS-13.1 确认月度考勤；VS-13.5 完成薪酬发放 | —（不变） | LEAV#加班工资分类的核对由旧企业合规改到 VS-13；原文已定位 VS-13.1 的 OBJ-72 同步补偿说明与 VS-13.5 支付。考勤锁定不等于补偿的单源质疑未由旧号产生，保留原驳回及复核边界。（所引笔记：[LEAV](../../frameworks/notes/leave-wage.md)。） |
| [R1-B-24](r1-b.md) | 直接补（one-architecture 写回对象、流程、价值流或词表） | 不受影响 | 沿用：VS-13.5 完成薪酬发放 | —（不变） | LEAV#工资支付记录对应 OBJ-73 人员行、OBJ-86 实付结果，原文在 VS-13.5／PRC-127 核对逐人成功、退票与清单。笔记旧企业合规号没有参与该阶段判断。（所引笔记：[LEAV](../../frameworks/notes/leave-wage.md)。） |
| [R1-B-25](r1-b.md) | 转 one-rules 或实现层，不在本轮内容层处理 | 不受影响 | 沿用：VS-13.3 完成薪酬结算；VS-13.5 完成薪酬发放 | —（不变） | LEAV#工资支付日期相关句仍关联约定日、提前支付日和银行结果；原文正确分列 VS-13.3 结算与 VS-13.5 发放，PRC-124、PRC-127 不变，发薪日细节仍转规则层。（所引笔记：[LEAV](../../frameworks/notes/leave-wage.md)。） |
| [R1-B-26](r1-b.md) | 驳回（反方为关键词复核，第 2 轮抽 20% 回原文复核） | 不受影响 | 沿用：VS-13.6 完成离职结清 | —（不变） | LEAV#离职工资结清仍对应解除终止时结清；原文引用 VS-13.6 的月度结算说明和 PRC-128，不是旧入职到离职流。编号修正不证明既有提前支付路径是否充分，原驳回保留。（所引笔记：[LEAV](../../frameworks/notes/leave-wage.md)。） |
| [R1-B-27](r1-b.md) | 转 one-rules 或实现层，不在本轮内容层处理 | 不受影响 | 沿用：VS-13.4 完成个税及社保公积金申报缴纳 | —（不变） | SOC#社保申报与告知的相关句已改沿 VS-13 核对申报、实缴和本人告知；原文已在 VS-13.4／PRC-125 查告知细节，没有把企业增值税申报代作个人明细。（所引笔记：[SOC](../../frameworks/notes/social-insurance.md)。） |
| [R1-B-28](r1-b.md) | 直接补（one-architecture 写回对象、流程、价值流或词表） | 不受影响 | 沿用：VS-13.4 完成个税及社保公积金申报缴纳；VS-13.5 完成薪酬发放 | —（不变） | IIT#扣缴义务人、扣缴缴库期限、全员全额报送内容的相关句改沿 VS-13；原文已列 VS-13.4 与 VS-13.5，直接问实付／扣缴所属期与申报准备的区分，原增补及直接补去向不变。（所引笔记：[IIT](../../frameworks/notes/iit-law.md)。） |
| [R1-B-29](r1-b.md) | 用户定（汇入确认页） | 不受影响 | 沿用：VS-13.6 完成离职结清 | —（不变） | LAB#经济补偿、代通知金基数、补偿月工资口径、离职证明与保管仍分别说明解除原因、基数、支付条件；原文的 OBJ-73／OBJ-123、PRC-128 已在 VS-13.6。入职段改号不改变这项业务口径待定。（所引笔记：[LAB](../../frameworks/notes/labor-contract.md)。） |
| [R1-B-30](r1-b.md) | 驳回（反方为关键词复核，第 2 轮抽 20% 回原文复核） | 不受影响 | 沿用：VS-13.6 完成离职结清 | —（不变） | LEAV#离职年假折算、年假日工资基数、未休报酬构成仍要求折算及不重复付薪；原文只在 VS-13.6 区分 PRC-178 补休转计发与年假清算。旧合规号不构成原驳回的依据。（所引笔记：[LEAV](../../frameworks/notes/leave-wage.md)。） |
| [R1-B-31](r1-b.md) | 直接补（one-architecture 写回对象、流程、价值流或词表） | 不受影响 | 沿用：VS-13.6 完成离职结清 | —（不变） | LAB#离职证明与保管、解除证明内容及 SOC#公积金增减员的相关句分别保留证明与外部办理日期；PRC-128、PRC-130 当前归 VS-13.6，与原文一致。原直接补去向保留。（所引笔记：[LAB](../../frameworks/notes/labor-contract.md)、[SOC](../../frameworks/notes/social-insurance.md)。） |
| [R1-C-01](r1-c.md) | 转 one-rules 或实现层，不在本轮内容层处理 | 不受影响 | 沿用：VS-06 完成企业合规事项／VS-06.1 完成申报缴款 | —（不变） | CIT#预缴与汇算仍要求关联预缴、年度申报和最终补退税；笔记变更的是集团报告号，原文对象为 OBJ-92，流程为 VS-06.1 的 PRC-77／PRC-159。税务办理没有错挂到人员或期间报告流。（所引笔记：[CIT](../../frameworks/notes/cit-law.md)。） |
| [R1-C-02](r1-c.md) | 转 one-rules 或实现层，不在本轮内容层处理 | 不受影响 | 沿用：VS-06／VS-06.1 完成申报缴款 | —（不变） | CIT#税会差异、长周期劳务收入、教育经费与结转、宣传费与结转的相关句仍要求独立税会调整和跨年依据；原文按 PRC-77／OBJ-92 的 VS-06.1 办理，不因报告改为 VS-10 就移出企业税申报。（所引笔记：[CIT](../../frameworks/notes/cit-law.md)。） |
| [R1-C-03](r1-c.md) | 转 one-rules 或实现层，不在本轮内容层处理 | 不受影响 | 沿用：VS-06／VS-06.1 完成申报缴款 | —（不变） | VAT#进项分摊与转出相关句仍在 PRC-73、PRC-77 核对用途、分摊及申报期间；此次收入开票改号与进项分摊不是同一论断，原文 VS-06.1 正确。（所引笔记：[VAT](../../frameworks/notes/vat-law.md)。） |
| [R1-C-04](r1-c.md) | 转 one-rules 或实现层，不在本轮内容层处理 | 不受影响 | 沿用：VS-07 完成人员任用／VS-07.3 完成人员入职与任职 | —（不变） | LAB#劳动关系建立、书面劳动合同的入职核对改到 VS-07.3；SOC#个人社保登记按用工日起算。原文已用该段和 PRC-83／PRC-84，三种日期区分不依赖旧 VS-06。（所引笔记：[LAB](../../frameworks/notes/labor-contract.md)、[SOC](../../frameworks/notes/social-insurance.md)。） |
| [R1-C-06](r1-c.md) | 驳回（反方为关键词复核，第 2 轮抽 20% 回原文复核） | 不受影响 | 沿用：VS-07／VS-07.2 完成录用 | —（不变） | PIPL#保存期限、个人信息删除、标准最短存储现指人员任用与报酬两流；本条限定未录用资料，在 OBJ-56／PRC-82、VS-07.2 检查处置。无需因一般留存句涉及 VS-13 就给候选人追加报酬阶段，原驳回保留。（所引笔记：[PIPL](../../frameworks/notes/pipl.md)。） |
| [R1-C-07](r1-c.md) | 记录 | 不受影响 | 沿用：VS-07／VS-07.3 完成人员入职与任职 | —（不变） | LAB#劳动关系建立、劳动合同必备条款的相关句已指 VS-07.3；原文同段区分 OBJ-56 人员、OBJ-57 任职与 OBJ-05 协议，印证不变。（所引笔记：[LAB](../../frameworks/notes/labor-contract.md)。） |
| [R1-C-08](r1-c.md) | 用户定（汇入确认页） | 不受影响 | 沿用：VS-09 完成经营目标／VS-09.1 编制计划、VS-09.3 完成经营复盘 | —（不变） | BARR#结果与项目、业务实验的相关句始终针对 OBJ-100／PRC-181 的结果证据；笔记改的是客户调查段名称。原文已在 VS-09.1、VS-09.3 区分动作完成与效果观察，原业务口径待用户定不变。（所引笔记：[BARR](../../frameworks/notes/barr-prove-it.md)。） |
| [R1-C-09](r1-c.md) | 记录 | 不受影响 | 沿用：VS-09／VS-09.1 编制计划、VS-09.2 变更预测 | —（不变） | PMB7#基线组合相关句分开计划、预算与预测，改交付／收入关联号不改变版本含义；原文已在 VS-09.1／VS-09.2、OBJ-100 与 PRC-88／PRC-89 核对，不把项目基线直接当企业预测规则。（所引笔记：[PMB7](../../frameworks/notes/pmbok7-zh.md)。） |
| [R1-C-10](r1-c.md) | 直接补（one-architecture 写回对象、流程、价值流或词表） | 不受影响 | 沿用：VS-10 获得期间报告／VS-10.0 执行记账结转、VS-10.1 关闭会计期间 | —（不变） | ACCB#账证账实核对相关句仍要求银行与往来余额核对；原文明确 PRC-104 是外部法定账对账而非银行对账，并已挂 VS-10.0／VS-10.1。笔记旧期间核算号未传入原文。（所引笔记：[ACCB](../../frameworks/notes/acc-basics.md)。） |
| [R1-C-11](r1-c.md) | 转 one-rules 或实现层，不在本轮内容层处理 | 不受影响 | 沿用：VS-10／VS-10.0 执行记账结转 | —（不变） | ACCB#折旧估计复核所在句已将折旧送入 VS-10.0；原文正核 OBJ-93、PRC-99 与 PRC-166 的估计／政策区分，没有误用人员入职阶段。（所引笔记：[ACCB](../../frameworks/notes/acc-basics.md)。） |
| [R1-C-12](r1-c.md) | 转 one-rules 或实现层，不在本轮内容层处理 | 不受影响 | 沿用：VS-10／VS-10.2 发布报表 | —（不变） | ACCB#财务报告勾稽及补核的附注签报、更正条款，核对的是 PRC-106／OBJ-99；原文已使用 VS-10.2。笔记旧期间流号改为 VS-10 不改变报表责任细节的规则层去向。（所引笔记：[ACCB](../../frameworks/notes/acc-basics.md)。） |
| [R1-C-13](r1-c.md) | 记录 | 不受影响 | 沿用：VS-10／VS-10.0 执行记账结转、VS-10.1 关闭会计期间 | —（不变） | ACCB#结账完整性、账证账实核对的期间流从旧号改为 VS-10；原文已挂 VS-10.0、VS-10.1，并以 OBJ-97、OBJ-98 与清理／差异处理流程为依据。保留完整性检查的印证。（所引笔记：[ACCB](../../frameworks/notes/acc-basics.md)。） |
| [R1-C-16](r1-c.md) | 记录 | 不受影响 | 沿用：VS-11／VS-11.1 编制知识条目、VS-11.2 审核并发布知识条目 | —（不变） | MAI#经验复用与成长的相关句关联 OBJ-110、OBJ-107 与 PRC-38；笔记更正经营复盘号不把项目复盘变成经营计划。原文 VS-11.1、VS-11.2 的知识提炼含义不变。（所引笔记：[MAI](../../frameworks/notes/maister-mpsf.md)。） |
| [R1-C-17](r1-c.md) | 转 one-rules 或实现层，不在本轮内容层处理 | 不受影响 | 沿用：VS-12 获得投资回报／VS-12.1 确认收到出资 | —（不变） | ACCB#固定资产初始成本及原文补核的投入资产计价用于非货币出资；笔记更正折旧和关账段，并未规定出资也属于 VS-10。原文以 OBJ-91／PRC-111 的 VS-12.1 提条件增补，范围待核仍保留。（所引笔记：[ACCB](../../frameworks/notes/acc-basics.md)。） |
| [R1-C-19](r1-c.md) | 转 one-rules 或实现层，不在本轮内容层处理 | 不受影响 | 沿用：VS-14 获得内部服务／VS-14.3 完成事项办理、VS-14.4 确认办结；关联 VS-10.0 执行记账结转 | —（不变） | ACCB#固定资产组成部分、后续支出、终止确认仍区分实物维修与会计确认；折旧成本改为 VS-10.0 后，原文 VS-14.3／VS-14.4 的 PRC-157 与 VS-10.0 的 PRC-99 关联已经正确。（所引笔记：[ACCB](../../frameworks/notes/acc-basics.md)。） |
| [R1-C-22](r1-c.md) | 转 one-rules 或实现层，不在本轮内容层处理 | 不受影响 | 沿用：VS-15／VS-15.1 拟订、VS-15.2 批准生效；关联 VS-06.1 完成申报缴款 | —（不变） | VAT#外币折合口径没有因收入流改号改变折合政策含义；原文在 OBJ-124／VS-15.1、VS-15.2 核对政策维护，并关联 VS-06.1 税申报，不把增值税口径当通用期末折算。（所引笔记：[VAT](../../frameworks/notes/vat-law.md)。） |
| [R1-D-02](r1-d.md) | 驳回（反方为关键词复核，第 2 轮抽 20% 回原文复核） | 不受影响 | 沿用：VS-02；VS-02.1–VS-02.7 | —（不变） | GFQ#全咨服务清单、AIA15#服务阶段组合与建成后服务仍列可组合成果；笔记的签约／预算段改号不为可研或建成后服务新增阶段。原文已直接比对 VS-02 的设计七段，是否需补段的适用性与原驳回不因换号改变。（所引笔记：[AIA15](../../frameworks/notes/aia-handbook-15.md)、[GFQ](../../frameworks/notes/gf-consulting-contracts.md)。） |
| [R1-D-03](r1-d.md) | 驳回（反方为关键词复核，第 2 轮抽 20% 回原文复核） | 不受影响 | 沿用：VS-02.1–VS-02.6；尤其已退出后退回修改 | —（不变） | GFD#成果审查相关句仍要求新版本关联重起审查期；原文直接比对 OBJ-30 和 VS-02 的 entry_common／exit_common，未沿旧号把已完成节点当成收尾或收入。原驳回仍待原定复核。（所引笔记：[GFD](../../frameworks/notes/gf-design-contracts.md)。） |
| [R1-D-04](r1-d.md) | 直接补（one-architecture 写回对象、流程、价值流或词表） | 不受影响 | 沿用：VS-02.2、VS-02.3、VS-02.4 | —（不变） | GFD#成果审查针对我方成果，QINS#设计风险管控／设计整改证据针对客户资产风险，不能互换。原文的缺口由 PRC-34 仅列 VS-02.6／VS-02.7 与 OBJ-38 我方成果来源直接比较得出；早期阶段映射的直接补去向不靠 QINS 的旧施工段成立。（所引笔记：[GFD](../../frameworks/notes/gf-design-contracts.md)、[QINS](../../frameworks/notes/quality-insurance.md)。） |
| [R1-D-05](r1-d.md) | 驳回（反方为关键词复核，第 2 轮抽 20% 回原文复核） | 不受影响 | 沿用：VS-03.2→VS-03.3／VS-03.4→VS-03.5 | —（不变） | QINS#最终风险报告相关句已从旧收尾落到 VS-03.5；原文已在 VS-03.2 至 VS-03.5 对照零发现时的进入条件，并说明这是本系统设计反例而非法规规定的阶段画法，原驳回不因改号改变。（所引笔记：[QINS](../../frameworks/notes/quality-insurance.md)。） |
| [R1-D-06](r1-d.md) | 转 one-rules 或实现层，不在本轮内容层处理 | 不受影响 | 沿用：VS-03.5 登记报告认可；涉及 VS-02.6 的 IDI 检查 | —（不变） | QINS#最终风险报告现归 VS-03.5，原文也是该段，并以 VS-02.6 的施工期 IDI 检查为关联场景；v2 明确保留施工期品控检查在交付段。报告类型与严重风险检查的规则层去向不变。（所引笔记：[QINS](../../frameworks/notes/quality-insurance.md)。） |
| [R1-D-07](r1-d.md) | 转 one-rules 或实现层，不在本轮内容层处理 | 不受影响 | 沿用：VS-03.2–VS-03.5 | —（不变） | QINS#设计风险管控、检测通知仍要求检测计划、报告和采信依据；原文已按 PRC-46 唯一归 VS-03.4，提出无整改委托时的报告引用落点问题，不依赖旧施工配合号。（所引笔记：[QINS](../../frameworks/notes/quality-insurance.md)。） |
| [R1-D-08](r1-d.md) | 驳回（反方为关键词复核，第 2 轮抽 20% 回原文复核） | 不受影响 | 沿用：VS-03 报告认可之后 | —（不变） | PMGB#项目收尾已更正到 VS-02.7；原文明确这是 PRC-36 的现有归属，并问 VS-03 检查项目的封存出口。它没有据旧 VS-02.8 断言检查必走设计收尾，原驳回保留。（所引笔记：[PMGB](../../frameworks/notes/gbt50326-2017.md)、[QINS](../../frameworks/notes/quality-insurance.md)。） |
| [R1-D-09](r1-d.md) | 用户定（汇入确认页） | 不受影响 | 沿用：VS-04.6 完成合同履行 | —（不变） | GFQ#全咨变更影响保留获指令／待定价情形；原文在 VS-04.6、OBJ-09、PRC-48 检查它，与目录一致。技术执行在 VS-02 并不替代协议变更，原用户定去向不变。（所引笔记：[GFQ](../../frameworks/notes/gf-consulting-contracts.md)。） |
| [R1-D-10](r1-d.md) | 用户定（汇入确认页） | 不受影响 | 沿用：VS-04.5 确认最终价款 | —（不变） | GFQ#造价终止结清的相关句改为 VS-04.5；原文已在该段对照仅接受“已完成”的进入条件与 PRC-68 终止协议。原终止清算路径问题不依赖旧报告段。（所引笔记：[GFQ](../../frameworks/notes/gf-consulting-contracts.md)。） |
| [R1-D-11](r1-d.md) | 用户定（汇入确认页） | 不受影响 | 沿用：VS-04.3、VS-04.4 | —（不变） | GFQ#全咨争议款项的相关句没有改部分异议／无争议余款的含义；原文以 VS-04.3、VS-04.4 和 OBJ-85 的整笔状态提出问题，不受旧终止结算号修正影响。（所引笔记：[GFQ](../../frameworks/notes/gf-consulting-contracts.md)。） |
| [R1-D-16](r1-d.md) | 驳回（反方为关键词复核，第 2 轮抽 20% 回原文复核） | 不受影响 | 沿用：VS-07.3 完成人员入职与任职 | —（不变） | LAB#劳动合同书面变更对应 PRC-136 与 OBJ-57 的生效区间；笔记用工登记已改 VS-07.3，原文同段直接检查“待入职”进入条件与在职异动，不需要改挂接。（所引笔记：[LAB](../../frameworks/notes/labor-contract.md)。） |
| [R1-D-20](r1-d.md) | 转 one-rules 或实现层，不在本轮内容层处理 | 不受影响 | 沿用：VS-09.2 变更预测；与 VS-02.0 资源活动相邻 | —（不变） | SPI26#技能产能与管道匹配有效性、收入及技能预测准确性及 KAN#资源优化情景仍是自评／能力宣称；原文已在 VS-09.2 研究预测，并将 VS-02.0 仅列为相邻资源活动。PRC-20 实际贯穿多段不使这条预测论断换段。（所引笔记：[KAN](../../frameworks/notes/kantata-psa.md)、[SPI26](../../frameworks/notes/spi-psmb-2026.md)。） |
| [R1-D-21](r1-d.md) | 用户定（汇入确认页） | 不受影响 | 沿用：VS-09.3 完成经营复盘 | —（不变） | BARR#结果先于行动、结果与项目、业务实验的相关句直接关联 PRC-181，未使用旧客户调查阶段作为复盘。原文 VS-09.3 的动作／效果区分及用户定去向保留。（所引笔记：[BARR](../../frameworks/notes/barr-prove-it.md)。） |
| [R1-D-24](r1-d.md) | 记录 | 不受影响 | 沿用：VS-11 获得复用知识；VS-11.1–VS-11.3 | —（不变） | SPI26#知识管理有效性及关卡现明确 PRC-182 已有；笔记修经营、核算和薪酬号不取消知识使用查看。原文已经是 VS-11、OBJ-107／OBJ-110 的印证。（所引笔记：[SPI26](../../frameworks/notes/spi-psmb-2026.md)。） |
| [R1-D-25](r1-d.md) | 记录 | 不受影响 | 沿用：VS-12.2 分配利润；VS-13.4 薪税申报 | —（不变） | IIT#综合所得范围、个税税率结构仍区分工资综合所得与股息红利；原文按 VS-12.2／PRC-113 与 VS-13.4／PRC-125 分开。薪税归 VS-13 的修正与原边界一致。（所引笔记：[IIT](../../frameworks/notes/iit-law.md)。） |
| [R1-D-26](r1-d.md) | 直接补（one-architecture 写回对象、流程、价值流或词表） | 不受影响 | 沿用：VS-13；VS-13.1–VS-13.5 月度循环 | —（不变） | LEAV#工资支付日期、工资支付记录仍按月核对实付；原文已在 VS-13.1 至 VS-13.5 对读流级 ends_in 只列离职，没有把旧企业合规流当发薪循环。直接补去向不变。（所引笔记：[LEAV](../../frameworks/notes/leave-wage.md)。） |
| [R1-D-27](r1-d.md) | 用户定（汇入确认页） | 不受影响 | 沿用：VS-13.6 完成离职结清 | —（不变） | LAB#经济补偿、离职证明与保管及 LEAV#离职工资结清、离职年假折算的相关句，原文均落 VS-13.6，并区分已离职与待计发／实付；笔记入职与报酬拆号不改变此业务问题。（所引笔记：[LAB](../../frameworks/notes/labor-contract.md)、[LEAV](../../frameworks/notes/leave-wage.md)。） |
| [R1-D-30](r1-d.md) | 转 one-rules 或实现层，不在本轮内容层处理 | 不受影响 | 沿用：VS-15.2 批准生效；VS-02.0 项目建立 | —（不变） | SPI26#标准交付方法覆盖率区分有模板与实际采用；笔记改的是成熟度表经营、成本等归属。原文问版本到 VS-02.0 项目实例的追溯，没有从旧号推出 VS-15.2。PRC-12 的现行归段为 VS-01.2，不能把本次重判当作批准改目录。（所引笔记：[SPI26](../../frameworks/notes/spi-psmb-2026.md)。） |
| [R1-D-32](r1-d.md) | 驳回（反方为关键词复核，第 2 轮抽 20% 回原文复核） | 不受影响 | 沿用：VS-07、VS-13、VS-14 的人事服务边界 | —（不变） | SPI26#员工推荐雇主意愿、员工离职原因的相关句仍说相邻人事流程不等于诉求闭环；原文已列 VS-07、VS-13、VS-14 三流，没有将旧 VS-06 当人事服务。诉求范围适用性和原驳回继续保留。（所引笔记：[SPI26](../../frameworks/notes/spi-psmb-2026.md)。） |
| [R1-D-35](r1-d.md) | 记录 | 不受影响 | 沿用：VS-04；VS-04.2、VS-04.3、VS-04.4 | —（不变） | GFD#阶段费用与付款的收入核对改为 VS-04.2；原文已经分 VS-04.2、VS-04.3、VS-04.4，并按 OBJ-07／OBJ-08／OBJ-83／OBJ-85／OBJ-86 分义，印证不变。（所引笔记：[GFD](../../frameworks/notes/gf-design-contracts.md)。） |
| [R1-E-01](r1-e.md) | 直接补（one-architecture 写回对象、流程、价值流或词表） | 不受影响 | 沿用：VS-01 至 VS-15，全部 61 个阶段 | —（不变） | BARR#属性数据、范围数据、时间数据及 CORR#时间线说明计数范围和起止事件，并未把这些通用方法限于旧期间流。原文直接核当前 15 流、61 段的进出条件，覆盖论断不依赖笔记旧号。（所引笔记：[BARR](../../frameworks/notes/barr-prove-it.md)、[CORR](../../frameworks/notes/corr-adwd.md)。） |
| [R1-E-02](r1-e.md) | 转 one-rules 或实现层，不在本轮内容层处理 | 不受影响 | 沿用：VS-09.1、VS-09.3；VS-15.1、VS-15.2、VS-15.3 | —（不变） | PARM#四类指标、七项特征、团队责任与 BARR#证据先于指标区分管理用途、计算依赖和来源；原文对 OBJ-100／OBJ-124 的经营及口径版本提出元数据问题，没有沿旧“成果验收”号定位，原转规则层不变。（所引笔记：[BARR](../../frameworks/notes/barr-prove-it.md)、[PARM](../../frameworks/notes/parmenter-kpi.md)。） |
| [R1-E-03](r1-e.md) | 直接补（one-architecture 写回对象、流程、价值流或词表） | 不受影响 | 沿用：VS-01.1 采集线索 | —（不变） | BARR#属性数据、范围数据、时间数据与 ADAM#累积快照的相关句要求同批范围和里程碑；原文已在 VS-01.1、OBJ-17、PRC-03 检查线索批次，不受 ADAM 收入／期间流改号影响。（所引笔记：[ADAM](../../frameworks/notes/adamson-star-schema.md)、[BARR](../../frameworks/notes/barr-prove-it.md)。） |
| [R1-E-04](r1-e.md) | 驳回（反方为关键词复核，第 2 轮抽 20% 回原文复核） | 不受影响 | 沿用：VS-01.2 赢得委托 | —（不变） | SPI26#赢单率的锚点仍是发出提案分母，对照本地赢单／丢单分母；原文已经在 VS-01.2 和 OBJ-18／OBJ-20。成熟度表的旧签约说法修正不改变这项分母比较及原驳回。（所引笔记：[SPI26](../../frameworks/notes/spi-psmb-2026.md)。） |
| [R1-E-05](r1-e.md) | 用户定（汇入确认页） | 不受影响 | 沿用：VS-01.1、VS-01.2；VS-04.1 | —（不变） | SPI26#销售周期天数与 CORR#时间线保留合格线索至签约两端；原文已分 VS-01.1／VS-01.2 与 VS-04.1，不把赢单当回签。销售周期口径仍待用户定。（所引笔记：[CORR](../../frameworks/notes/corr-adwd.md)、[SPI26](../../frameworks/notes/spi-psmb-2026.md)。） |
| [R1-E-06](r1-e.md) | 用户定（汇入确认页） | 不受影响 | 沿用：VS-01.1、VS-01.2；VS-09.3 | —（不变） | SPI26#老客户既有服务收入占比至新客户新服务收入占比仍为客户×服务的收入结构；原文用 OBJ-01、OBJ-24、OBJ-100、OBJ-106 的客户经营观察，没有把旧费用或人员号当收入来源，用户定去向不变。（所引笔记：[SPI26](../../frameworks/notes/spi-psmb-2026.md)。） |
| [R1-E-07](r1-e.md) | 用户定（汇入确认页） | 不受影响 | 沿用：VS-02.0 至 VS-02.7；VS-13.1 | —（不变） | SPI26#利用率、含批准非计费工时的综合利用率仍需桥接标准分母与本地有效／可计费条件；原文已用 VS-02 各段和 VS-13.1 考勤，未继承旧 VS-06 薪酬挂接。（所引笔记：[SPI26](../../frameworks/notes/spi-psmb-2026.md)。） |
| [R1-E-08](r1-e.md) | 用户定（汇入确认页） | 不受影响 | 沿用：VS-02.0 至 VS-02.6；VS-07.1；VS-09.2 | —（不变） | MAI#资源分配输入现落 VS-02 贯穿资源活动；SPI26#技能产能与管道匹配有效性仍为技能、产能及招聘自评。原文已联合 VS-02.0 至 VS-02.6、VS-07.1 和 VS-09.2，预测与资源阶段无须换号。（所引笔记：[MAI](../../frameworks/notes/maister-mpsf.md)、[SPI26](../../frameworks/notes/spi-psmb-2026.md)。） |
| [R1-E-09](r1-e.md) | 用户定（汇入确认页） | 不受影响 | 沿用：VS-02.1 至 VS-02.6；VS-03.5 | —（不变） | SPI26#交付准时率仍区分原／修订承诺与实际结果；原文以 OBJ-31、OBJ-35、OBJ-41 和 PRC-28 的交付／报告各段为事实位置，没有把 SPI 成熟度旧收入流当成果认可段。（所引笔记：[SPI26](../../frameworks/notes/spi-psmb-2026.md)。） |
| [R1-E-10](r1-e.md) | 直接补（one-architecture 写回对象、流程、价值流或词表） | 不受影响 | 沿用：VS-02.0 至 VS-02.7 | —（不变） | KGR2#覆盖事实表、ADAM#条件事实表及 SPI26#标准交付方法覆盖率都要求“应覆盖”集合与实际事件相配；原文已用 VS-02.0 至 VS-02.7、OBJ-37 和 PRC-28，未从旧期间流推导覆盖范围。（所引笔记：[ADAM](../../frameworks/notes/adamson-star-schema.md)、[KGR2](../../frameworks/notes/kimball-reader-2.md)、[SPI26](../../frameworks/notes/spi-psmb-2026.md)。） |
| [R1-E-11](r1-e.md) | 用户定（汇入确认页） | 不受影响 | 沿用：VS-02.7；VS-09.3 | —（不变） | BARR#直接证据、属性数据、范围数据所在调查句保留 VS-02.7 编号，只把“成果验收”改成“完成收尾”；原文原已按 PRC-37、OBJ-106 挂收尾并关联 VS-09.3，不将调查当通用交付验收。（所引笔记：[BARR](../../frameworks/notes/barr-prove-it.md)。） |
| [R1-E-12](r1-e.md) | 直接补（one-architecture 写回对象、流程、价值流或词表） | 不受影响 | 沿用：VS-03.1、VS-03.2 | —（不变） | KGR2#覆盖事实表及 BARR#范围数据说明应检与已检两集合，笔记经营月报换号不改变这方法；原文 OBJ-48／OBJ-49、PRC-41／PRC-43 已在 VS-03.1／VS-03.2。（所引笔记：[BARR](../../frameworks/notes/barr-prove-it.md)、[KGR2](../../frameworks/notes/kimball-reader-2.md)。） |
| [R1-E-13](r1-e.md) | 用户定（汇入确认页） | 不受影响 | 沿用：VS-03.3、VS-03.4、VS-03.5 | —（不变） | BARR#属性数据、范围数据与 ADAM#累积快照没有把退出一律定义为风险闭合；原文直接按 VS-03.4 的三类出口检查 OBJ-38，已区分整改、接受风险和停止跟踪，不依赖旧收入或期间号。（所引笔记：[ADAM](../../frameworks/notes/adamson-star-schema.md)、[BARR](../../frameworks/notes/barr-prove-it.md)。） |
| [R1-E-14](r1-e.md) | 用户定（汇入确认页） | 不受影响 | 沿用：VS-04.1、VS-04.6、VS-04.2；VS-09.1、VS-09.2 | —（不变） | SPI26#商机管道覆盖倍数、期初积压订单收入覆盖率的句子未改分子分母；原文明确管道、签约余额、当季收入与预测版本是不同量，使用 VS-04／VS-09 对照它们，未从旧“报价签约”号认定赢单即签约。是否另补售前维度非此次编号反转。（所引笔记：[SPI26](../../frameworks/notes/spi-psmb-2026.md)。） |
| [R1-E-16](r1-e.md) | 用户定（汇入确认页） | 不受影响 | 沿用：VS-04.2、VS-04.3、VS-04.4、VS-04.5 | —（不变） | SPI26#收入漏损率、发票重做率、实现率核查相关句仍区分损失原因、正常红冲和未开票余额；原文已用 VS-04.2 至 VS-04.5 的 OBJ-81、OBJ-82、OBJ-83、OBJ-85，不含旧风险流收入挂接。（所引笔记：[SPI26](../../frameworks/notes/spi-psmb-2026.md)。） |
| [R1-E-17](r1-e.md) | 驳回（反方为关键词复核，第 2 轮抽 20% 回原文复核） | 不受影响 | 沿用：VS-04.4；VS-09.3 | —（不变） | SPI26#应收周转天数的余额公式／发票等待时长区分与改号无关；原文明确保留 FD-39，并称图片公式为原文补核而非笔记已提取。VS-04.4、VS-09.3 本已正确，原驳回不因此反转。（所引笔记：[SPI26](../../frameworks/notes/spi-psmb-2026.md)。） |
| [R1-E-19](r1-e.md) | 直接补（one-architecture 写回对象、流程、价值流或词表） | 不受影响 | 沿用：VS-06.1、VS-06.2；VS-13.4 | —（不变） | KGR2#覆盖事实表是通用覆盖方法，原文分别核 VS-06 企业税资质与 VS-13.4 薪税申报的应办总体；不能因笔记经营报告旧号修正就将两种法定义务合并，直接补去向不变。（所引笔记：[KGR2](../../frameworks/notes/kimball-reader-2.md)。） |
| [R1-E-20](r1-e.md) | 用户定（汇入确认页） | 不受影响 | 沿用：VS-07.1、VS-07.2、VS-07.3 | —（不变） | SPI26#标准岗位招聘周期、新员工达产周期仍区分录用与达到可生产状态；原文已在 VS-07.1、VS-07.2、VS-07.3 核 OBJ-60／OBJ-56，未沿成熟度表旧人事号。（所引笔记：[SPI26](../../frameworks/notes/spi-psmb-2026.md)。） |
| [R1-E-21](r1-e.md) | 用户定（汇入确认页） | 不受影响 | 沿用：VS-08.0、VS-08.1、VS-08.2、VS-08.3 | —（不变） | SPI26#员工季度非计费费用、可计费／非计费差旅费用占比相关句仍区分归属用途与能否转收；笔记将费用从旧 VS-05 改到 VS-08，原文已用 VS-08.0 至 VS-08.3，借款也明确不当费用。（所引笔记：[SPI26](../../frameworks/notes/spi-psmb-2026.md)。） |
| [R1-E-22](r1-e.md) | 转 one-rules 或实现层，不在本轮内容层处理 | 不受影响 | 沿用：VS-09.1、VS-09.3；VS-13.2 | —（不变） | SPI26#愿景与战略理解度、领导层信任度、目标与度量一致性、员工沟通有效性及领导力综合指数仍是调查及源内差异；原文已在 VS-09 与 VS-13.2 区分组织反馈和个人绩效，未拿旧经营流号作个人奖金依据。（所引笔记：[SPI26](../../frameworks/notes/spi-psmb-2026.md)。） |
| [R1-E-23](r1-e.md) | 用户定（汇入确认页） | 不受影响 | 沿用：VS-09.1、VS-09.2、VS-09.3 | —（不变） | ADAM#平均分母与版本及 SPI26#收入及技能预测准确性的相关句仍要求保留版本；经营比较改到 VS-09 后，原文 VS-09.1 至 VS-09.3、OBJ-100／PRC-89 已一致，原用户定不变。（所引笔记：[ADAM](../../frameworks/notes/adamson-star-schema.md)、[SPI26](../../frameworks/notes/spi-psmb-2026.md)。） |
| [R1-E-24](r1-e.md) | 用户定（汇入确认页） | 不受影响 | 沿用：VS-09.3；VS-02.7 | —（不变） | MAI#合伙人利润驱动、利用率与小时收费现联看 VS-09.3；SPI26 两种人均年收入仍要求不同人员范围。原文已有 VS-09.3，VS-02.7 只作项目解释关联，并未把该段误称通用验收。（所引笔记：[MAI](../../frameworks/notes/maister-mpsf.md)、[SPI26](../../frameworks/notes/spi-psmb-2026.md)。） |
| [R1-E-25](r1-e.md) | 直接补（one-architecture 写回对象、流程、价值流或词表） | 不受影响 | 沿用：VS-10.0、VS-10.1、VS-10.2 | —（不变） | BARR#结果与项目、属性数据、范围数据仍区分办理与结果；原文直接核 OBJ-117 解释／修正／豁免及 OBJ-125 对账结果，均在 VS-10。没有继承 ADAM 或其他笔记旧期间流。（所引笔记：[BARR](../../frameworks/notes/barr-prove-it.md)。） |
| [R1-E-26](r1-e.md) | 用户定（汇入确认页） | 不受影响 | 沿用：VS-11.1、VS-11.2、VS-11.3；VS-02.7 | —（不变） | SPI26#知识管理有效性、MAI#经验复用与成长、BARR#业务实验均区分引用与效果；原文已用 VS-11.1 至 VS-11.3 和 VS-02.7 的知识／项目复盘，没有把旧经营复盘段当成项目收尾。（所引笔记：[BARR](../../frameworks/notes/barr-prove-it.md)、[MAI](../../frameworks/notes/maister-mpsf.md)、[SPI26](../../frameworks/notes/spi-psmb-2026.md)。） |
| [R1-E-27](r1-e.md) | 用户定（汇入确认页） | 不受影响 | 沿用：VS-12.1、VS-12.2、VS-12.3；VS-09.3 | —（不变） | MAI#合伙人利润驱动把 Profits/Partner 拆成利润率、生产率和人员层级结构，改为 VS-09.3 后原文经营观察落点已一致；VS-12 各段只提供出资／分配事实，原文未将现金支付当经营利润。（所引笔记：[MAI](../../frameworks/notes/maister-mpsf.md)。） |
| [R1-E-28](r1-e.md) | 驳回（反方为关键词复核，第 2 轮抽 20% 回原文复核） | 不受影响 | 沿用：VS-13.6；VS-07.3；VS-09.3 | —（不变） | SPI26#主动离职率、被动离职率、离职率的期初人数分母未改；原文已分 VS-13.6 离职、VS-07.3 任职与 VS-09.3 观察，不依赖旧人员合规号。原公式及驳回复核安排保留。（所引笔记：[SPI26](../../frameworks/notes/spi-psmb-2026.md)。） |
| [R1-E-29](r1-e.md) | 用户定（汇入确认页） | 不受影响 | 沿用：VS-13.1、VS-13.2、VS-13.3；VS-07.3；VS-06.2 | —（不变） | SPI26#员工推荐雇主意愿、职业路径清晰度、教育培训工时的相关句明确法定继续教育不等于一般人才成长；原文已区分 VS-07、VS-13 与 VS-06.2 的证书属性，不因薪酬改号而取消资质维护关联。（所引笔记：[SPI26](../../frameworks/notes/spi-psmb-2026.md)。） |
| [R1-E-30](r1-e.md) | 直接补（one-architecture 写回对象、流程、价值流或词表） | 不受影响 | 沿用：VS-13.1、VS-13.3、VS-13.4、VS-13.5、VS-13.6 | —（不变） | LEAV#工资支付记录、工资支付日期、离职工资结清关联实际支付；KGR2#覆盖事实表用于应发总体。原文已在 VS-13 的考勤、算薪、申报、发放、离职各段，原直接补去向不受旧合规号影响。（所引笔记：[KGR2](../../frameworks/notes/kimball-reader-2.md)、[LEAV](../../frameworks/notes/leave-wage.md)。） |
| [R1-E-31](r1-e.md) | 转 one-rules 或实现层，不在本轮内容层处理 | 不受影响 | 沿用：VS-14.1、VS-14.2、VS-14.3、VS-14.4 | —（不变） | CORR#时间线、ADAM#累积快照和非线性过程给的是事件时间与轮次方法；笔记修正收入、期间、经营号未限制方法只用于那些流。原文 VS-14.1 至 VS-14.4 的 IT 工单时长问题不换段。（所引笔记：[ADAM](../../frameworks/notes/adamson-star-schema.md)、[CORR](../../frameworks/notes/corr-adwd.md)。） |
| [R1-E-32](r1-e.md) | 转 one-rules 或实现层，不在本轮内容层处理 | 不受影响 | 沿用：VS-15.1、VS-15.2、VS-15.3 | —（不变） | ADAM#跨过程指标登记、星型定义清单及 BARR#属性数据、BARR#范围数据、BARR#时间数据仍要求逐指标可复算；原文在 OBJ-124、PRC-168 的 VS-15 版本治理核对，未沿旧期间或成果验收号放置元数据。（所引笔记：[ADAM](../../frameworks/notes/adamson-star-schema.md)、[BARR](../../frameworks/notes/barr-prove-it.md)。） |
| [R1-E-33](r1-e.md) | 记录 | 不受影响 | 沿用：VS-10.1；VS-04.4；VS-09.3 | —（不变） | ADAM#半可加度量的期末比较已改 VS-10；原文 VS-10.1 的 PRC-103 本已正确，并关联 VS-04.4 余额来源、VS-09.3 经营观察。半可加与版本重述的印证不变。（所引笔记：[ADAM](../../frameworks/notes/adamson-star-schema.md)。） |
| [R1-E-34](r1-e.md) | 记录 | 不受影响 | 沿用：VS-02.7；VS-09.3；VS-15.2 | —（不变） | ADAM#不可加比率及 SPI26#SPI项目毛利率相关句未改同层级组成量重算及总净额边界；原文已有 VS-09.3／VS-15.2，VS-02.7 只为项目结果关联，不依赖旧验收含义。（所引笔记：[ADAM](../../frameworks/notes/adamson-star-schema.md)、[SPI26](../../frameworks/notes/spi-psmb-2026.md)。） |
| [R1-E-35](r1-e.md) | 记录 | 不受影响 | 沿用：VS-08.2、VS-08.3；VS-05.4、VS-05.5 | —（不变） | CORR#七问框架、业务事件及 ADAM#交易标识仍区分确认事件与资金分配；原文已经把报销放在 VS-08.2／VS-08.3、供方应付放在 VS-05.4／VS-05.5，未沿 SPI 旧费用流号。（所引笔记：[ADAM](../../frameworks/notes/adamson-star-schema.md)、[CORR](../../frameworks/notes/corr-adwd.md)。） |
| [R1-E-36](r1-e.md) | 记录 | 不受影响 | 沿用：VS-11.3；VS-15.2、VS-15.3 | —（不变） | ADAM#跨过程指标登记仍支持元数据承载而非新增对象；原文的 PRC-182 现属 VS-11.3，PRC-168 属 VS-15，均没有从改动的收入或期间流复制归属。（所引笔记：[ADAM](../../frameworks/notes/adamson-star-schema.md)。） |
| [R1-E-37](r1-e.md) | 用户定（汇入确认页） | 不受影响 | 沿用：VS-09.3；VS-10.0、VS-10.2 | —（不变） | SPI26#息税折旧摊销前利润率仍为纳入项可变的非准则指标，改的是成熟度表核算／经营编号；原文已用 VS-10 的结果与 VS-09.3 的比较，未把本地经营利润直接冒充该指标。（所引笔记：[SPI26](../../frameworks/notes/spi-psmb-2026.md)。） |
| [R1-F-05](r1-f.md) | 直接补（one-architecture 写回对象、流程、价值流或词表） | 不受影响 | 沿用：VS-02.2–VS-02.6 确认方案至完成施工配合 | —（不变） | AJE#产品计价完成值的相关句由旧收入流改 VS-04，仍按本系统政策管理；原文在 VS-02.2 至 VS-02.6、OBJ-33／PRC-151 讨论的是经营产值的计量含义，明确不当收入。收入号修正不消除同名异义问题。（所引笔记：[AJE](../../frameworks/notes/ajera.md)。） |
| [R1-F-09](r1-f.md) | 直接补（one-architecture 写回对象、流程、价值流或词表） | 不受影响 | 沿用：VS-03.3 发布不符合项；VS-03.4 跟进整改；VS-02.6 完成施工配合 | —（不变） | QINS#最终风险报告、设计风险管控现归 VS-03 风险结论；原文已同时区分 VS-03.3／VS-03.4 客户资产风险与 VS-02.6 我方成果处置。OBJ-38 的风险／已证实不符合定义问题不由旧施工段造成。（所引笔记：[QINS](../../frameworks/notes/quality-insurance.md)。） |
| [R1-F-10](r1-f.md) | 记录 | 不受影响 | 沿用：VS-02.1–VS-02.6 交付阶段；VS-03.5 登记报告认可 | —（不变） | GFD#交付签收、成果审查相关句依旧分别保存送达、签收、意见、认可；PRC-28 现行归段与原文 VS-02.1 至 VS-02.6、VS-03.5 一致，三类事实分义的印证保留。（所引笔记：[GFD](../../frameworks/notes/gf-design-contracts.md)。） |
| [R1-F-12](r1-f.md) | 记录 | 不受影响 | 沿用：VS-04.1 签署合同；VS-04.2 确认收入；VS-04.3 开具发票 | —（不变） | GFD#阶段费用与付款的收入落点已改 VS-04.2；原文已经分别用 VS-04.1、VS-04.2、VS-04.3，OBJ-07／OBJ-08 分开，未把付款比例当履约义务识别结论。（所引笔记：[GFD](../../frameworks/notes/gf-design-contracts.md)。） |
| [R1-F-14](r1-f.md) | 直接补（one-architecture 写回对象、流程、价值流或词表） | 不受影响 | 沿用：VS-01.2 赢得委托；VS-02.0 开始项目；VS-04.1 签署合同 | —（不变） | AJE#计费费率卡相关句仍按人员、活动、供方和有效期区分收费规则，改动的是其他项目设置、报销、收入和期初核对句。原文是在 OBJ-26／OBJ-27 与协议价目的用途上分义，没有依旧 VS-02.2 推定费率均为预算单价。（所引笔记：[AJE](../../frameworks/notes/ajera.md)。） |
| [R1-F-17](r1-f.md) | 记录 | 不受影响 | 沿用：VS-09.1 编制计划；VS-09.2 变更预测；VS-09.3 完成经营复盘 | —（不变） | SPI26#预期收入增长、商机管道覆盖倍数、年度收入目标达成率的概念未改；成熟度表经营号改为 VS-09 后，原文 VS-09.1 至 VS-09.3 与 OBJ-100／PRC-88／PRC-89 本已相符。（所引笔记：[SPI26](../../frameworks/notes/spi-psmb-2026.md)。） |
| [R1-F-19](r1-f.md) | 记录 | 不受影响 | 沿用：VS-07.2 完成录用；VS-07.3 完成人员入职与任职 | —（不变） | LAB#劳动关系建立相关句已指 VS-07.3；原文同时列 VS-07.2 的候选阶段，并依正式 Person／Employee 区别，不把候选人并入人员解释为已实际用工。旧合规号不影响这项印证。（所引笔记：[LAB](../../frameworks/notes/labor-contract.md)。） |
| [R1-F-21](r1-f.md) | 直接补（one-architecture 写回对象、流程、价值流或词表） | 需改挂接 | VS-07.3 完成人员入职与任职；VS-13.4 完成个税及社保公积金申报缴纳；VS-13.6 完成离职结清 | —（不变） | SOC#公积金账户唯一、公积金增减员、公积金汇缴期限仍支持登记、账户与封存分义；SOC:27、61 的薪税挂接已由 VS-06 改 VS-13。v2 的 VS-06 描述与 VS-06.1 仅管增值税及附加、企业所得税，故本条公积金关联应去掉 VS-06.1，保留 VS-07.3 增员、VS-13.4 申报缴纳、VS-13.6 减员。OBJ-62 复合定义需补充及原直接补去向不变。待定：PRC-125 目录仍双挂 VS-13.4、VS-06.1，与 v2 类型边界不一致；该目录后续如何订正不在本卡改写。（所引笔记：[SOC](../../frameworks/notes/social-insurance.md)。） |
| [R1-F-23](r1-f.md) | 记录 | 不受影响 | 沿用：VS-11.1–VS-11.3 知识编制、发布、修订废除；对照 VS-02.7 完成收尾 | —（不变） | SPI26#知识管理有效性及关卡仍明确 PRC-182 已有，原文 VS-11 与 VS-02.7 已区分知识复用和项目复盘；经营复盘旧号修正不改变两对象的含义，保留印证。（所引笔记：[SPI26](../../frameworks/notes/spi-psmb-2026.md)。） |

## 计数与结论改变清单

117 条全部重判：**不受影响 116 条；需改挂接 1 条；结论改变 0 条**。分组覆盖：A 22 条、B 15 条、C 16 条、D 20 条、E 35 条、F 9 条。

结论改变逐条清单：**无（0 条）**。没有因笔记改号而将原“驳回”自动改成增补，亦未改“用户定”“转规则层”的去向。

需改挂接逐条清单：

- **R1-F-21**：去掉企业税申报阶段，公积金仍关联人员入职、薪税申报和离职结清三个阶段；保留原增补和直接补去向。现行流程目录中 `PRC-125` 的额外企业税归段与 v2 的类型边界不一致，列为待定，交负责会话处理；本文件只给出该 R1 条目按 v2 的正确范围，不替目录删段。无法仅凭两份静态文件证明这一残留由哪次笔记引用造成。

## 自测与验证边界

### 已验证

核对基线：本卡接收时的本地 `HEAD bc6f290a`；初始工作区干净，最终仅有本文件一个未跟踪文件。以下覆盖检查按 `splitlines()` 读取 TSV（末行无换行，`wc -l` 为 116，实际记录数 117）。

自测输出：

```text
覆盖：117 / 117；编号唯一、顺序与 TSV 完全一致；missing=[]；extra=[]
分组：A=22，B=15，C=16，D=20，E=35，F=9
原裁决去向：117 / 117 与 TSV 第二列逐字一致
重判：不受影响=116；需改挂接=1；结论改变=0
六列表格：117 / 117；重判枚举检查通过；新类别均不变
新价值流与阶段列：71 个显式编号逐个 grep -En 精确匹配，missing=[]
R1-E-01 全量范围展开：15 流、61 阶段，76 / 76 逐个 grep 精确匹配
本地链接：275 处，broken=[]
R1-F-21：新挂接仅 VS-07.3、VS-13.4、VS-13.6，PASS
git diff --check：退出码 0
新增文件对 /dev/null 的 --no-index --check：无空白错误诊断
```

覆盖检查比较完整编号列表、唯一性及原去向原文，不以行数相等代替逐条覆盖。编号检查仅提取“新价值流与阶段”列，另展开 E-01 的全量范围；对每个编号执行 `grep -En '^[[:space:]]*- id: <编号>([[:space:]#]|$)' ../value-streams.v2.yaml`（其中编号点号转义，执行时从仓库根传完整路径），防止短编号误中长编号。理由列中用于说明历史修正的旧编号不当作新编号验证。

普通 `git diff` 不包含未跟踪文件，所以另对本文件执行 `git diff --no-index --check /dev/null <结果文件>`，无诊断；该命令退出码 1 表示新文件相对空文件存在差异，不能误记为“无差异”或通过普通 `git diff` 就声称新增文件已检查。最终另检查全文件无行尾空白。

执行 `bash scripts/precheck.sh HEAD`，退出码 **0**；用接卡基线 HEAD 检查本卡的一个文件，未 fetch，不把继承分支相对 `origin/main` 的变化算作本卡修改。完整原始输出：

```text
precheck: base HEAD (bc6f290a), HEAD bc6f290a, 1 changed files (1 uncommitted)

SKIP  generated docs (build_all --check)     docs or backend/glossary/proposed has uncommitted changes: commit first, then run again (请先提交再运行)
PASS  legacy system name                     check_legacy_name: 无违规
SKIP  migrations immutable                   no migration, marker or Flyway setting changed
SKIP  system actors append-only              system-actors.txt and its script did not change
SKIP  acceptance guard                       no acceptance test changed
SKIP  database scripts aligned               no database script or migration changed
SKIP  docs read by builds are registered     no backend, frontend or script changed
SKIP  assertion drift (warning only)         no test file changed
SKIP  glossary: check:copy, test:scripts, term tests terms.yaml, bcs.yaml and backend/glossary unchanged
SKIP  guards: backend guard tests            no guard test or architecture list changed
SKIP  release scripts self-test, shellcheck  relevant_change.py: nothing the release scripts or the builds read changed
PASS  gitleaks (full history)                3109 commits scanned; no leaks
      note: uncommitted files are not in history; the pre-commit hook scans them on commit
SKIP  backend tests                          relevant_change.py: nothing the builds read changed
SKIP  frontend verify                        relevant_change.py: nothing the builds read changed
SKIP  SQL Server integration tests           relevant_change.py: nothing the *MssqlIT tests exercise changed

SUMMARY: 2 PASS, 0 FAIL, 13 SKIP (base HEAD) — ready
```

13 个 SKIP 的理由逐行保留：生成文档检查因本文件未提交而跳过，卡上禁止提交，未为此改动生成物或提交；其余检查对应的迁移、脚本、词表、守护或构建输入均未改。全历史密钥扫描检查 3109 个提交，无发现；它不覆盖本次未提交文件，不将其表述为工作树全量密钥验收。

### 未运行

- 未联网核验外部法规、产品现状或原始出版物；本轮回读的是卡上指定的 R1 原文、现行笔记及修正日志，没有把历史报告中的“已回原文”当作本次重新回原始出版物的证据。
- 未运行后端、前端、数据库、容器或业务验收测试；本卡无可执行产品验收用例，交付物为编号影响复核文件。
- 未执行 `build_all.py` 生成记录、登记术语、修改流程或对象正本；卡上只授权新建本文件。未提交、未推送，也未创建 PR。
