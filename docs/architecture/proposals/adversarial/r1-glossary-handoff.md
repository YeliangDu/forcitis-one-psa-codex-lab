# 第 1 轮用语与度量交接

状态：提议｜ARC-1018-ARC-16｜交接对象：one-glossary｜2026-10-17

本文件逐条承接 R1-RULING.md 的「直接补」用语事项：R1-F 共 17 条、R1-E 共 9 条。所有现定义从本工作区基线词表和信息图直接读回；没有正式词条的明确写未登记，不以草稿对象名充当词条 id。建议尚未写入 terms.yaml，未批准的新词只在本交接文件登记。C-20 已有提议状态名另列补充。

取证边界：已核实本地裁定、正方原文、反方最小补法、词表及源字段；外部资料页码沿正方已经记录的证据锚点，本卡未重新逐页核验原件。

## R1-E-01

- 词条 id：未登记；提议 id：stage_entry_count、stage_success_exit_count、stage_other_exit_count、stage_open_count、stage_elapsed_time（kind=metric；scope=domain；bc 待 one-glossary 归属）。
- 现定义：
  - 对应正式词条未登记；以下是对象稿现有定义或资料中的待定概念。
  - 61 个阶段仅有 entry / exit 等条件，词表未声明与各阶段关联的度量；见本文末尾对应表。
- 问题：61 个阶段有进入、退出条件和价值项，但没有显式度量项；〔BI〕§3.2 的事实行也未逐阶段列出进入量、退出量、未完成量和起止事件。因而不能从“有状态、有事实行”证明每段进出指标齐全；尤其跳过、停止、退回重入尚无计数约定。
- 建议定义：同一阶段实例的一次进入或重入为一轮；进入量按进入事件计轮次，成功退出与其他退出分列，期末未完为截至观察日已进入且未退出的轮次。耗时取同一轮的实际进入与退出时间，未退出轮次另报截至观察日的等待时长；跳过不伪造进入、退出或零耗时，单列跳过记录；停止、退回单列结局，重入另记轮次并保留前轮引用。附录逐段列现有条件与对象状态候选，具体成功 / 其他结局的归类须读现行流程结果，混合出口不得一概算成功。
- 边界及待定：仅形成度量提议及 61 段对应表；不改阶段门，不把统计记录立为业务对象；正式 metric/stages 登记与下游元数据接入未完成，TD-8 不能报已关闭。
- 证据锚点：[R1-E.md:17](r1-e.md)；〔VS〕全部 `entry` / `exit`；〔BI〕§3.2；`BARR#属性数据`、`BARR#范围数据`、`BARR#时间数据`：第6章，印刷69–70，EPUB `OPS/c06.xhtml`；`CORR#时间线`：第1章表1-3，印刷25 / PDF43。；反方 `/private/tmp/advrev/EF.md` 按编号。

## R1-E-03

- 词条 id：lead_conversion_rate（scope=domain；bc=presales.opportunity）。
- 现定义：
  - `terms.yaml#lead_conversion_rate`（线索转化率；bc=presales.opportunity）：期间内转为商机的线索数 ÷ 期间线索数
  - 信息图 §1 `OBJ-17`（线索）：尚未确认值得跟进的业务信息
- 问题：`lead_conversion_rate` 是“期间转为商机的线索数 ÷ 期间线索数”，未声明分母按创建、进入还是活跃线索取样。上月创建、本月转化的线索可能只进分子，故该定义尚不能复算同一批线索的转化率。
- 建议定义：以同一创建批次线索为分母，截至指定观察日其中已转为商机的线索为分子；保存批次起止日、转化时点、观察截止日与关闭结局，分子必须属于该分母。原“期间转化数 / 期间线索数”如继续保留，另标发生比，不能与批次转化率混用；旧指标是否改名或另立变体由 one-glossary 处理。
- 边界及待定：创建批次是建议口径，原公式本轮不改；确认页中需要用户定的 E-05 等不在本交接范围。
- 证据锚点：[R1-E.md:19](r1-e.md)；〔词〕`lead_conversion_rate`；〔对象〕§1 OBJ-17；`BARR#属性数据`、`BARR#范围数据`、`BARR#时间数据`：印刷69–70，`OPS/c06.xhtml`；`ADAM#累积快照`：第11章印刷278–279 / PDF305–306。；反方 `/private/tmp/advrev/EF.md` 按编号。

## R1-E-10

- 词条 id：internal_review_coverage、kickoff_completion_rate。
- 现定义：
  - `terms.yaml#internal_review_coverage`（内部评审覆盖率；bc=delivery.acceptance）：已完成内部评审的项目 ÷ 已交付项目
  - `terms.yaml#kickoff_completion_rate`（启动会完成率；bc=delivery.execution）：期间内已开启动会的项目数 ÷ 应开启动会的项目数
  - 信息图 §1 `OBJ-37`（内部评审）：我方对交付成果的校审、阶段评审与成果内审记录
- 问题：`internal_review_coverage` 为已完成内审项目 / 已交付项目，未说清内审和交付是否属于同一批、同一阶段；`kickoff_completion_rate` 虽写“应开”项目，也未给应开集合的历史来源。两者不能证明全部适用阶段已执行标准交付方法。
- 建议定义：内部评审覆盖率：在固定方法版本、适用性及豁免依据下，同一应覆盖项目 / 阶段集合中实际完成要求评审的数量除以应覆盖数量；项目与阶段口径分别列示。启动会完成率：同一历史应开集合中已开启动会的项目数除以应开项目数，保存应开判定时点、方法版本和豁免依据。首次通过率另按同一对象、同一阶段首轮评审事件计算。
- 边界及待定：不以当前已有评审或已交付项目倒推历史应覆盖总体；分母为零及缺失依据应显式标无可计算结果。
- 证据锚点：[R1-E.md:26](r1-e.md)；〔词〕两项覆盖率；〔对象〕OBJ-37 的评分与一次通过率；`KGR2#覆盖事实表`：第8章§8.13，印刷297–298 / PDF329–330；`ADAM#条件事实表`：第12章印刷298、300 / PDF325、327；`SPI26#标准交付方法覆盖率`：第11章印刷174 / PDF181、题117。；反方 `/private/tmp/advrev/EF.md` 按编号。

## R1-E-12

- 词条 id：未登记；提议 id：inspection_coverage（kind=metric；scope=domain；bc 待归属）。
- 现定义：
  - 对应正式词条未登记；以下是对象稿现有定义或资料中的待定概念。
  - 信息图 §1 `OBJ-48`（检查计划）：为某建筑系统按法规或协议排出的各类检查到期日
  - 信息图 §1 `OBJ-49`（检查）：按约定范围与检查表，在某一时点对受检对象实施的一次检查
- 问题：检查计划“产生的事实”写“—”，检查只列按次计价依据和检查工时；没有声明当期应检对象／部位／次数与实际完成集合的对应。只数已锁定检查无法识别未安排、取消或未执行的应检任务。
- 建议定义：以有效计划版本内同一期间、受检对象、部位及应检次数为应检总体；分列已安排、实际完成、取消、逾期未检数量，覆盖率的完成集合必须来自同一总体。同次检查的多次复核不重复计完成；取消保留原因与原总体，不自动从分母剔除。
- 边界及待定：计划来源仍限已定 IDI 范围（Q31a）；不得藉指标扩大到非 IDI 计划，取消是否准许调整应检总体须有版本及依据。源对象已补基础对应关系，正式指标仍待登记。
- 证据锚点：[R1-E.md:28](r1-e.md)；〔对象〕§1 OBJ-48、OBJ-49；〔BI〕§3.2 无检查覆盖事实；`KGR2#覆盖事实表`：§8.13，印刷297–298 / PDF329–330；`BARR#范围数据`：印刷69，`OPS/c06.xhtml`。；反方 `/private/tmp/advrev/EF.md` 按编号。

## R1-E-15

- 词条 id：billable_hours；未登记的提议 id：billed_hours、billed_labor_amount（kind=metric；scope=domain；bc 待归属）。
- 现定义：
  - `terms.yaml#billable_hours`（可计费工时；bc=cost.timesheet）：有效 且 关联 T&M 合同 且 在费率表范围内的工时（可计费 ⊆ 有效）
  - 信息图 §1 `OBJ-82`（开票申请）：向客户请款、要求开具发票的内部账单，含准入检查
- 问题：`billable_hours` 是资格集合，不是已计费小时。〔BI〕开票记录和回款分配有金额，但没有声明一笔工时的计费数量、暂缓／减免、部分计费和账单行关联，无法仅由可计费小时复算实际已计费小时或小时实现收费。
- 建议定义：可计费工时维持资格集合；已计费小时为截至观察时点已确认商业计费明细分配给工时来源的小时数，支持部分计费及冲回，按来源与账单行去重。工时计费金额来自相同分配行及其币种；暂缓、减免与未计费余量分别留据，沿账单行关联税票；税票金额、收入、回款分别勾稽，不直接互代。
- 边界及待定：正式商业计费确认时点随现有账单流程核定；B-03 已补 OBJ-82 的分配组成，F-13 的内部开税票申请与对客请款语义仍待 one-glossary 对齐。
- 证据锚点：[R1-E.md:31](r1-e.md)；〔对象〕§2.5 已区分售前派生价与合同计费费率卡；`SIL2#计费小时`、`SIL2#账单金额`：第7章 Star Schema for Professional Services / Time Entry Fact，图7.12，EPUB `14_chapter_7.html`；`UAE#计费状态与明细关联`、`UAE#计费资格及复核增量待核`：UA-BI印刷＝PDF23、29–30。；反方 `/private/tmp/advrev/EF.md` 按编号。

## R1-E-18

- 词条 id：aging、aging_bucket、payable；未登记的提议 id：payable_aging（kind=metric）。
- 现定义：
  - `terms.yaml#aging`（账龄；bc=receivables.collection）：应收与 WIP 共用的分桶口径（0-30/30-90/90-180/180+）
  - `terms.yaml#aging_bucket`（账龄区间；bc=receivables.collection）：应收与 WIP 共用的分桶口径（0-30/30-90/90-180/180+）
  - `terms.yaml#payable`（应付款项；bc=payables.payment）：应付账款与其他应付款的往来款项，与应收款项对称
  - 信息图 §1 `OBJ-85`（往来款项）：对相对方的无条件收款权或付款义务的开放项；一个对象，以方向区分收付（BIZBOK Payment）
- 问题：供方准入、履约、应付和支付链齐备，但〔BI〕期间快照只列应收账龄，没有单列供方未清应付的截止期间、账龄计算日、发票日／到期日；〔词〕`aging` 仅称应收与WIP共用。不能由“一笔付款”事实证明付款前的未付和逾期结构可重建。
- 建议定义：供方应付账龄：在指定业务截止期间和观察日，按每笔应付开放项的未付余额，依据声明的开单日或到期日基准分桶；分列未到期、逾期、部分付款、争议及质保金；预付单列，不自动抵销未付余额。保留合同、履约、原应付、付款分配及历史更正引用。
- 边界及待定：原应收 / WIP 的已定分桶不扩改；供方桶边界及账龄计算基准是待核参数，不从单源产品例子代入。
- 证据锚点：[R1-E.md:34](r1-e.md)；〔BI〕§3.2 付款及期间快照行；`UAE#查询供方未清往来`、`UAE#账龄日期与应付查询待核`：UA-AP印刷＝PDF60–66，UA-AR71–72、74–75。；反方 `/private/tmp/advrev/EF.md` 按编号。

## R1-E-19

- 词条 id：未登记；提议 id：statutory_obligation_timely_completion_rate（kind=metric；bc 待归属）。
- 现定义：
  - 对应正式词条未登记；以下是对象稿现有定义或资料中的待定概念。
  - 信息图 §1 `OBJ-92`（法定申报）：法人对税务、社保等机关的周期申报与缴款（外部权威登记）
  - 信息图 §1 `OBJ-64`（注册执业证书）：员工持有的注册建筑师、注册结构工程师等执业资格（外部权威登记）
  - 信息图 §1 `OBJ-65`（资质证书）：企业承接某类业务所需的资质（设计、施工、检测等）；我方与供应商的资质是同一对象
- 问题：申报缴款和资质延续有状态，但未定义期间内“应申报／应缴／应续证”的有效总体及截止日对应指标。只数已申报、已缴或仍有效的证书，会漏掉尚无办理记录的法定义务；降级、撤销、到期也是本段出口，不能算成功完成。
- 建议定义：按义务类别、责任主体及期间，以已确认的应申报、应缴及应续证清单和各自有效期限版本为总体，分别统计应办、按期成功、逾期未办与异常结局；按期率分子取同一总体在适用截止日以前成功完成者。申报和缴款分别计算，降级、撤销、到期单列，不算延续成功；不适用项注明依据。
- 边界及待定：不以系统内已有申请数倒推法定义务；法定期限数值不在本次设定。
- 证据锚点：[R1-E.md:35](r1-e.md)；〔VS〕VS-06.1、VS-06.2 及 VS-13.4；〔对象〕§1 OBJ-92、OBJ-65；`KGR2#覆盖事实表`：第8章§8.13，印刷297–298 / PDF329–330。此来源支持覆盖比较方法，不提供本企业税期、证书续期数值。；反方 `/private/tmp/advrev/EF.md` 按编号。

## R1-E-25

- 词条 id：reconciliation_deviation、reconciliation_deviation_case；未登记的提议 id：reconciliation_case_close_rate、reconciliation_case_correction_rate（kind=metric）。
- 现定义：
  - `terms.yaml#reconciliation_deviation`（勾稽差异；bc=operations.policy）：不同口径间对账的差额（三偏差术语之一）
  - `terms.yaml#reconciliation_deviation_case`（勾稽差异案；bc=operations.policy）：超容差差异的生命周期载体（解释/修正/豁免三选一关闭）
  - 信息图 §1 `OBJ-117`（勾稽差异案）：对超容差核对差异逐案登记、解释或修正、申请豁免并结案的业务记录
  - 信息图 §1 `OBJ-125`（外部法定账对账（暂名，待 one-glossary 定名））：按法人与期间核对收付口径账簿与外部法定账的余额与发生额，留存差异处置与签字结论的业务记录
- 问题：勾稽差异案已有金额与解释／修正／豁免结局，外部法定账对账也有签字结果；当前没有区分“关闭率”与“已修正差异比例”的度量定义。按已关数量或正负差异净额判断质量，会掩盖豁免、解释及相互抵消的大额差异。
- 建议定义：在同一发现批次、原始案件集合及观察日内，关闭率为已解释、已修正、已豁免案件合计数 / 原始案件数；修正率单列已修正案件数 / 同一原始案件数，并标明分母。分别展示原始有符号净差、绝对差异总额、解释 / 修正 / 豁免 / 未关数量与结案耗时；同一差异的重复发现和重述以原案引用去重。
- 边界及待定：关闭后的修正不重开原案（沿 OBJ-117）；阈值、豁免权限、关账条件不改；“修正率”建议以案件数计算，金额改善另列，不能净额抵销后代替。
- 证据锚点：[R1-E.md:41](r1-e.md)；〔对象〕§1 OBJ-117、OBJ-125；〔流程〕PRC-169；`BARR#结果与项目`：第5章印刷38，`BARR#属性数据`、`BARR#范围数据`：第6章印刷69，EPUB `OPS/c05.xhtml`、`OPS/c06.xhtml`。来源支持区分办理与效果，不规定本地容差。；反方 `/private/tmp/advrev/EF.md` 按编号。

## R1-E-30

- 词条 id：未登记；提议 id：payroll_timely_full_payment_rate（kind=metric；scope=domain；bc=organization.payroll）。
- 现定义：
  - 对应正式词条未登记；以下是对象稿现有定义或资料中的待定概念。
  - 信息图 §1 `OBJ-73`（月度薪酬结算（原「算薪运行」））：某法人某月一次薪酬计算、审核与发放
- 问题：考勤锁定、算薪批准、申报、发放和离职结清有状态，但没有“应发薪人员×期间”的准时足额支付定义。算薪已批准或付款指令成功不直接等于员工在约定日收到足额工资，月度薪酬事实也不能识别应发但无发放记录的人。
- 建议定义：以应发薪人员 × 发薪期间的清单为总体，按约定支付日及适用节假日调整依据，逐人核对已确认应实付额与银行实付结果 / 领取依据；只有截至应付日已足额实付者计入按期足额分子。单列逾期未付、部分支付、离职未结数量及未付金额，应发但无付款记录者仍进入总体；个人清单送达另留记录。
- 边界及待定：付款指令受理、算薪批准不代表足额收到；B-24 已补薪酬行支付状态与资金引用，E-30 仍是待登记指标，不能用整期已发放代替逐人验证。
- 证据锚点：[R1-E.md:46](r1-e.md)；〔VS〕VS-13.5 价值项“按时足额收到工资”；`LEAV#工资支付记录`、`LEAV#工资支付日期`、`LEAV#离职工资结清`：《工资支付暂行规定》第6、7、9条；`KGR2#覆盖事实表`：§8.13，印刷297–298 / PDF329–330。；反方 `/private/tmp/advrev/EF.md` 按编号。

## R1-F-02

- 词条 id：opportunity；客户服务请求未登记（提议 id 待 one-glossary 查重）。
- 现定义：
  - `terms.yaml#opportunity`（商机；bc=presales.opportunity）：立项跟进的销售机会，评估→报价→承诺→转化的全生命周期对象
  - 信息图 §1 `OBJ-17`（线索）：尚未确认值得跟进的业务信息
  - 信息图 §1 `OBJ-18`（商机）：针对特定客户、特定项目要争取的机会；售前工时与费用的归集目标
  - 信息图 §1 `OBJ-19`（招标项目）：客户发起的一次采购活动（公告、招标文件、截止、标段）
- 问题：资料将 RFI、RFP、RFQ 单独表达为专业服务请求，同一需求可对应多次请求；“线索／商机／招标项目”三行尚未说明这种对外请求分别由谁承载。不能因都是售前信息就把 RFQ 直接解释为招标项目。此为单源对应缺口，不证明需要新对象。
- 建议定义：客户服务请求（提议名）：客户为一项需要发出的信息征询、方案征询或询价的一轮请求，保留请求条目与响应对应。线索是待核实业务信息，商机是我方争取机会，招标项目是外部采购活动；三者不与 RFI / RFP / RFQ 自动同义。按 A-02 在商机组成记录保留轮次，招标类引用外部采购资料；是否存在不能由组成记录承载的场景再交用户。
- 边界及待定：A-02 只补追溯，不改变外部采购对象已定定义或新增请求对象。
- 证据锚点：[R1-F.md:18](r1-f.md)；[SIL2](../../frameworks/notes/silverston-dmrb2.md) `SIL2#专业服务请求`：第7章 Orders / Professional Services Requests and Quotes / Professional Services Requests，图7.5；`14_chapter_7.html` 正文列 RFI、RFP、RFQ。；反方 `/private/tmp/advrev/EF.md` 按编号。

## R1-F-03

- 词条 id：work_package、delivery_package。
- 现定义：
  - `terms.yaml#work_package`（工作包；bc=delivery.execution）：WBS 下的工作单元分组
  - `terms.yaml#delivery_package`（交付包；bc=delivery.execution）：WBS 下的工作单元分组
  - 信息图 §1 `OBJ-30`（WBS 节点）：项目工作分解的节点：阶段（第一层，引用阶段类型）、子项、工作包（末级，可记工时）
- 问题：词表 `work_package / WorkPackage` 与 `delivery_package / DeliveryPackage` 的定义仍同为“WBS 下的工作单元分组”，但工作包的正式含义是 WBS 最底层。对象稿已把工作包限定为末级，§3 又把旧交付包交给 WBS 节点类型；两个英文词条仍不能据定义区分
- 建议定义：work_package：WBS 最底层、可以估算及管理成本和工期、可记工时的工作单元。delivery_package 不再与全部工作包逐字同义；按 C-19 和已定 WBS 节点类型映射处理其旧称或类型，具体保留 / 弃用方案由 one-glossary 定名。
- 边界及待定：不新增对象或 WBS 层级，不直接给 delivery_package 选择新的业务含义。
- 证据锚点：[R1-F.md:19](r1-f.md)；[WBS3](../../frameworks/notes/pmi-wbs3.md) `WBS3#工作包`：§2.1、§2.2.5，印刷 p12、29／PDF p29、46；[PMB6](../../frameworks/notes/pmbok6-zh.md) `PMB6#工作分解`：§5.4；原文术语表 Work Package 明确“最低层”。；反方 `/private/tmp/advrev/EF.md` 按编号。

## R1-F-04

- 词条 id：estimate_to_complete、estimate_at_completion。
- 现定义：
  - `terms.yaml#estimate_to_complete`（ETC；bc=delivery.execution）：剩余完工估算
  - `terms.yaml#estimate_at_completion`（完工估算；bc=delivery.execution）：EAC = 实际成本 AC + 完工尚需估算 ETC
  - 信息图 §1 `OBJ-33`（完工估算）：对工作包或项目剩余工时（ETC）与完工总成本（EAC）的估计及其审批；成本更新表（项目总监在各阶段门处填写）是完工估算的更新，不改预算基线（用户答复（完整性检查（intent 072440））C-8 / M-12）；周完工百分比作输入属性（M-22）
- 问题：OBJ-33 把 ETC 写成“剩余工时”，而词表 `EstimateAtCompletion` 是 AC + ETC。成本 AC 不能直接与小时相加；Unanet 也分别列 ETC Units 与 ETC Amount。因此缺的是 ETC 的数量／工时和金额两个含义的明确对应，不是判定现有计算已经算错。
- 建议定义：ETC：截至估算日完成剩余工作尚需的估算，明确区分人工工时、非人工数量与成本金额，各自保存单位、费率 / 价格版本及换算依据。EAC = AC + ETC 中 AC、ETC 均为同币种成本金额；工时或数量输入须经明确的价格依据换算后进入金额估算。
- 边界及待定：不把成本预测当收入确认；币种、估算时点及范围须一致。
- 证据锚点：[R1-F.md:20](r1-f.md)；[PMB6](../../frameworks/notes/pmbok6-zh.md) `PMB6#挣值`：§7.4.2.2“预测”，EPUB `OEBPS/text00002.html`，EAC＝AC＋自下而上 ETC；[UAE](../../frameworks/notes/unanet-ae.md) `UAE#ETC及完成进度历史`：PM 印刷／PDF p12，分别定义 ETC Units、ETC Amount。；反方 `/private/tmp/advrev/EF.md` 按编号。

## R1-F-05

- 词条 id：earned_value；经营产值暂用名未登记（提议 id 待定）。
- 现定义：
  - `terms.yaml#earned_value`（挣值；bc=delivery.execution）：截至某时点已完成工作的预算价值（EV；GB/T 50358 称赢得值）
  - 信息图 §1 `OBJ-33`（完工估算）：对工作包或项目剩余工时（ETC）与完工总成本（EAC）的估计及其审批；成本更新表（项目总监在各阶段门处填写）是完工估算的更新，不改预算基线（用户答复（完整性检查（intent 072440））C-8 / M-12）；周完工百分比作输入属性（M-22）
- 问题：PRC-151 的“经营产值”向完工估算追加 progress_estimates，却未定义是完成百分比、已完成工作的预算价值，还是对客服务价值。词表只明确 `EarnedValue` 是已完成工作的预算价值，且将裸“产值”列为收入确认的禁用旧称。Ajera 又用 earned value 表示按费率计的投入价值，说明
- 建议定义：earned_value 仍为截至指定时点已完成工作的预算价值，单位为金额，声明预算版本及币种；PRC-151 的暂用名应明确为“指定项目 / 工作包、估算日、所据基准及单位的管理进展估计”，完成比例与金额分别记录。只有确认以已完成工作预算价值计量后才能映为 EV；对客服务价值、按费率计投入价值与收入确认各自说明。
- 边界及待定：PRC-151 的价值基准、单位尚无已定证据，本条保留待定，不选择百分比、预算价值或对客价值，不改变 C-03 / GL-026。
- 证据锚点：[R1-F.md:21](r1-f.md)；[PMB6](../../frameworks/notes/pmbok6-zh.md) `PMB6#挣值`：§7.4.2.2“挣值分析”；[AJE](../../frameworks/notes/ajera.md) `AJE#产品计价完成值`：实施指南印刷／PDF p65，Using earned value analysis。本系统：PRC-151 的 primary_object_note；词条 `earned_value`、`revenue_recognition`。；反方 `/private/tmp/advrev/EF.md` 按编号。

## R1-F-06

- 词条 id：未登记；资源需求 / 资源分配的正式 id 待 one-glossary 查重。
- 现定义：
  - 对应正式词条未登记；以下是对象稿现有定义或资料中的待定概念。
  - 信息图 §1 `OBJ-44`（资源需求）：项目在某时段对某角色、职级或专业的工时需求，可未指定到人
  - 信息图 §1 `OBJ-45`（资源分配）：具名员工在某时段对某工作包的计划工时
- 问题：两个对象当前分别是人员工时需求和具名员工计划工时；Unanet 的 Allocation、Resource Schedule 还包括非人工数量、费用与供应商。因此本系统“资源”实际是人力范围，不能用产品的广义资源安排来宣称已经包含外部检测或非人工安排。单源范围提示，不认定这些业务必须纳入。
- 建议定义：本系统资源需求限项目在某时段对人力角色、职级、专业的工时需求；资源分配限具名员工在某时段对工作包的计划工时。外部产品的广义资源安排还可涵盖非人工数量、费用及供方，不能据此认为本系统已有这些范围。
- 边界及待定：只补人力限定；非人工排程是否需要及如何承载不在本轮裁定。
- 证据锚点：[R1-F.md:22](r1-f.md)；[UAE](../../frameworks/notes/unanet-ae.md) `UAE#岗位及费用需求`、`UAE#具名资源安排`、`UAE#非人工资源安排待核`：PM 印刷／PDF p12–13，人工为 Hours／Employee，非人工为 Units／Vendor。；反方 `/private/tmp/advrev/EF.md` 按编号。

## R1-F-07

- 词条 id：未登记；客户活动、执行事项（提议名）分别查重定 id。
- 现定义：
  - 对应正式词条未登记；以下是对象稿现有定义或资料中的待定概念。
  - 信息图 §1 `OBJ-30`（WBS 节点）：项目工作分解的节点：阶段（第一层，引用阶段类型）、子项、工作包（末级，可记工时）
  - 信息图 §1 `OBJ-105`（客户活动）：对往来单位客户角色开展的一次拜访或跟进，含计划、变更与结果反馈
- 问题：Unanet Activity 是可分派、完成、记时、归档的执行事项；OBJ-105 是一次客户拜访或跟进。两者不能因中文都含“活动”而视作同义。PRC-21 只明确推进工作包，尚未说明小于工作包的事项是否需要这种独立办理语义。
- 建议定义：客户活动为对客户角色的一次拜访或跟进；工作包为 WBS 末级的交付工作单元。执行事项（提议名）指工作包内可分派、设期限并跟踪完成的小事项，是否在本系统适用仍待核，不能把客户活动直接映成该 Activity。若未来两个 bc 使用同名 Activity，分别立词条并互列 homonyms。
- 边界及待定：不建立执行事项对象；R1-A-14 已驳回，不能由同名澄清恢复该功能范围。
- 证据锚点：[R1-F.md:23](r1-f.md)；[UAE](../../frameworks/notes/unanet-ae.md) `UAE#执行事项`、`UAE#工作包下执行事项待核`：PM 印刷／PDF p64–69，Working with Activities，p64 定义 Activity Card、WBS View、Time Entry、Archive。；反方 `/private/tmp/advrev/EF.md` 按编号。

## R1-F-08

- 词条 id：未登记；延期申请的正式 id 待 one-glossary 查重。
- 现定义：
  - 对应正式词条未登记；以下是对象稿现有定义或资料中的待定概念。
  - 信息图 §1 `OBJ-41`（延期申请）：因发包人原因要求顺延工期的申请及答复
- 问题：OBJ-41 将一般名称“延期申请”限定为发包人原因。FIDIC 的 Extension of Time 还包括变更和 Exceptional Event 等事由，不能把其全部映到仅发包人原因的定义。该单源反例只证明现定义较窄，不能把范本事由或天数直接变成全系统规则。
- 建议定义：本稿延期申请专指因发包人原因提出的工期顺延申请及其答复；所据协议、事由和时限版本须可追溯。FIDIC Extension of Time 含更多事由，映射到本稿时须声明范围差异，不能无条件等同。
- 边界及待定：采用范围限定的最小补法；不增加其他事由，不将范本示例天数设成全系统规则。
- 证据锚点：[R1-F.md:24](r1-f.md)；[FIDW](../../frameworks/notes/fidic-white-book-2017.md) `FIDW#延期申请`：4.4.1(a)–(d)、4.4.2–4.4.3，印刷 p13／PDF p36；现有 OBJ-41 所据 GF0209 6.3.1 本来就是“因发包人原因”。；反方 `/private/tmp/advrev/EF.md` 按编号。

## R1-F-09

- 词条 id：nonconformity。
- 现定义：
  - `terms.yaml#nonconformity`（不符合项；bc=delivery.acceptance）：我方成果或客户资产（含第三方工作）未满足规定要求的事项，类型区分我方成果与客户资产；识别、确认、处置、关闭的生命周期；原「质量问题」「发现」并入
  - 信息图 §1 `OBJ-38`（不符合项（原质量问题；原发现 OBJ-51 并入，用户 2026-10-15 Q32））：我方成果或客户资产中识别出的缺陷、不符合、隐患或技术风险，须处置或报告客户
- 问题：正式词条 `nonconformity / Nonconformity` 已定义为“未满足规定要求的事项”，但 OBJ-38 仍把隐患和技术风险无条件纳入同一名称。上海细则以风险作为报告与处置对象，未将其定义成 Nonconformity；由此提出待核情形：若尚无未满足要求的证据，这类风险是否仍能按正式不符合项定义登记
- 建议定义：正式不符合项指有证据表明未满足规定要求的事项；OBJ-38 的统一处置记录另含待核隐患、技术风险，记录类型及是否已经证实不符合应分别声明，不能由风险未消除反推不符合已经证实。风险 / 隐患进入统一记录时保留识别依据与证实程度。
- 边界及待定：保留 Q32 的对象合并决定；不从对象中删除风险类型，定义、类型名和证实判据交 one-glossary 对齐；若需改变用户决定，转用户。
- 证据锚点：[R1-F.md:25](r1-f.md)；[QINS](../../frameworks/notes/quality-insurance.md) `QINS#最终风险报告`、`QINS#设计风险管控`：上海细则第七条（四）、第八条，印刷／PDF p6–8，分别要求风险报告与风险处置；本系统词条 `nonconformity` 对照 OBJ-38、§2.10。；反方 `/private/tmp/advrev/EF.md` 按编号。

## R1-F-11

- 词条 id：change_order、change_order_draft、contract_modification。
- 现定义：
  - `terms.yaml#change_order`（变更；bc=contract.agreement）：合同范围/价格/工期的正式修改（增补协议是落地形式之一）
  - `terms.yaml#change_order_draft`（变更意向；bc=contract.agreement）：变更的识别登记态（影响评估的前置）
  - `terms.yaml#contract_modification`（合同变更（会计口径）；bc=contract.agreement）：经批准的合同范围或价格变更在收入核算上的处理事实（单独合同 / 原合同终止新立 / 累积追加调整）
  - 信息图 §1 `OBJ-09`（协议变更）：经双方批准的范围、价格或工期修改
- 问题：OBJ-09 定义是“经双方批准的……修改”，生命周期却包括已提出、影响评估、审批。FIDIC 另有范围指令已发、价款尚未谈妥而先行执行的路径。这些候选、指令、已定价修改均称“变更”时，定义缺了批准程度的区分，不能由出现一条变更记录便断言双方已定价。
- 建议定义：协议变更记录覆盖拟议修改、影响评估、审批及生效后的范围 / 价格 / 工期修改；已生效修改与拟议修改按批准程度区分。范围指令与定价结果分别留据，形成记录不等于双方已经批准价格；正式修改形成新基线的条件仍按现行约定。
- 边界及待定：不新增先行执行未定价状态或授权；R1-D-09、R1-B-05 属用户定，不通过词条替其拍板。
- 证据锚点：[R1-F.md:27](r1-f.md)；[FIDW](../../frameworks/notes/fidic-white-book-2017.md) `FIDW#变更`、`FIDW#变更价款与先行指令`：5.1.3、5.2.1–5.2.4，印刷 p14–15／PDF p37–38；词条 `change_order` 定义为合同正式修改。；反方 `/private/tmp/advrev/EF.md` 按编号。

## R1-F-13

- 词条 id：billing_request。
- 现定义：
  - `terms.yaml#billing_request`（开票申请；bc=receivables.wip）：准入检查→开票→移交 AR 的流程载体
  - 信息图 §1 `OBJ-82`（开票申请）：向客户请款、要求开具发票的内部账单，含准入检查
  - 信息图 §1 `OBJ-83`（增值税发票）：由税务平台登记、以平台结果为准的发票；我方开具（销项）与取得（进项）是同一对象
- 问题：OBJ-82 同时被定义为“向客户请款”和“要求开具发票的内部账单”，词表 `BillingRequest` 则止于开票并移交应收。Unanet 的商业账单可以生成、过账、送给客户；FIDIC 咨询费账单还有自己的提交及到期含义。税务开票申请通过，不足以表示对客请款已发出；现定义缺这两个用途的明确对应。
- 建议定义：开票申请为要求内部办理税务开票并移交应收的流程载体；对客请款账单是向客户主张支付的单据，须另有签发、送达及所据协议证据。两者可按既有组成记录关联，但内部开票准入通过不能证明请款已向客户发出；OBJ-82 上两个用途的承载对应由 one-glossary 与负责会话核定。
- 边界及待定：不改开票先决条件、P-1 例外或应收来源；不新增商业账单对象。
- 证据锚点：[R1-F.md:29](r1-f.md)；[UAE](../../frameworks/notes/unanet-ae.md) `UAE#商业账单复核`、`UAE#商业账单及销售分录`：BI 印刷／PDF p6、22–26；[FIDW](../../frameworks/notes/fidic-white-book-2017.md) `FIDW#报酬与付款条件`：附件3、7.2.1，印刷 pf、18–19／PDF p16、41–42。；反方 `/private/tmp/advrev/EF.md` 按编号。

## R1-F-14

- 词条 id：standard_cost_rate_card、bill_rate_card；计费系数的正式 id 待查重。
- 现定义：
  - `terms.yaml#standard_cost_rate_card`（标准费率；bc=cost.costing）：薪酬脱敏后的版本化政策费率（财务维护）
  - `terms.yaml#bill_rate_card`（计费费率卡；bc=contract.agreement）：对客计费费率卡（与成本费率卡 StandardCostRateCard 对称）
  - 信息图 §1 `OBJ-26`（标准成本费率表）：财务维护的版本化人工单价表；用于签约后交付的预算、完工估算与管理视图，并是计费费率的基数
  - 信息图 §1 `OBJ-27`（计费系数）：售前把标准成本费率换算为计费费率的乘数，版本化
- 问题：对客履约计费费率不是当然由标准成本费率乘系数得到：Unanet 分列 Pay、Job Cost、Bill Rates；Ajera 的计费表还支持按人、活动、供方及成本加价选价。现有售前派生单价与正式 `BillRateCard` 仍不能按同名视作同一个概念。这是 §2.5、C-20 的已知问题复核，不是新发现费率对象。
- 建议定义：标准成本费率为政策人工成本单价；售前估价单价按已定标准成本费率与计费系数版本形成；协议约定的对客履约收费单价由相应协议计费约定及有效费率版本决定，不当然等于售前派生单价。BillRateCard 对应对客收费约定，与前两者按使用场景、有效期及来源版本区分。
- 边界及待定：保留 §2.5、C-20 与用户已定售前乘数决策；合同执行计价扩充属于 R1-B-02 用户定。
- 证据锚点：[R1-F.md:30](r1-f.md)；[UAE](../../frameworks/notes/unanet-ae.md) `UAE#费率方案`、`UAE#费率版本及覆盖`：AD 印刷／PDF p41–43；[AJE](../../frameworks/notes/ajera.md) `AJE#计费费率卡`：实施指南印刷／PDF p34。；反方 `/private/tmp/advrev/EF.md` 按编号。

## R1-F-15

- 词条 id：expense_claim。
- 现定义：
  - `terms.yaml#expense_claim`（报销单；bc=expense.spend）：引用已批申请（或政策豁免）的费用确认载体
  - 信息图 §1 `OBJ-88`（费用报销）：员工为公务垫付后经审核由公司偿付并确认的费用
- 问题：OBJ-88 定义限定为“员工为公务垫付后……公司偿付”，却把“公司直付”列为属性取值，且统一列出“应付（员工）”。公司卡消费由公司向发卡机构结算时，并不存在员工垫付这一事实。Ajera 的公司卡例子反证这两类支出不能都解释为“员工垫付报销”。Unanet 这里只印证员工偿付，不能凑作公司直付的第二个反证源；故按单源增
- 建议定义：费用确认载体应显式区分员工垫付后由公司偿付，与公司向服务商 / 发卡机构直接结算两类；保留实际付款方、被偿付人或应付债权人及结算依据。若两类共用 OBJ-88，定义不得只写员工垫付；若公司直付另有承载，须列其与 OBJ-88 的对应边界。
- 边界及待定：本轮仅提议判别含义，不选择拆合或扩名；R1-B-20 属用户定，不据单源反例推定所有公司卡场景已经适用。
- 证据锚点：[R1-F.md:31](r1-f.md)；[UAE](../../frameworks/notes/unanet-ae.md) `UAE#费用申报及报销`：AD 印刷／PDF p28，Expense Sheets 导入 Employee Reimbursables；Ajera 实施指南原文锚点 Phase 3 / Bank register，印刷／PDF p55：费用报告中的公司卡项进入卡账户登记；Phase 2 / Bank accounts，p30 分列公司银行及信用卡账户。资料身份见 [ajera.md](../../frameworks/notes/ajera.md)「附：资料与核对边界」；这是回原文补出的定位，不冒用其他 AJE 概念锚点。；反方 `/private/tmp/advrev/EF.md` 按编号。

## R1-F-16

- 词条 id：未登记；供方商业请款单（提议名）正式 id 待 one-glossary 查重。
- 现定义：
  - 对应正式词条未登记；以下是对象稿现有定义或资料中的待定概念。
  - 信息图 §1 `OBJ-87`（供方履约确认）：对分包方或采购供应商某期已完成工作或已到货物的确认
  - 信息图 §1 `OBJ-83`（增值税发票）：由税务平台登记、以平台结果为准的发票；我方开具（销项）与取得（进项）是同一对象
  - 信息图 §1 `OBJ-85`（往来款项）：对相对方的无条件收款权或付款义务的开放项；一个对象，以方向区分收付（BIZBOK Payment）
- 问题：Unanet Vendor Invoice 是供应商提出金额、编号及应付的商业账单，不是中国进项增值税发票。现有流程名只有履约确认、查验税票、提交应付，未说明供应商商业请款单是履约确认的单据、应付的依据还是税票。不能仅凭 Invoice 同词将其全映到 OBJ-83。
- 建议定义：供方商业请款单为供方就某期履约提交的编号、金额及支付主张单据，关联履约确认与应付依据；中国增值税进项发票为税务平台登记的税票。两者分别保存身份及对应关系，不能按 Invoice 一词自动互代。
- 边界及待定：建议先用 OBJ-87 的单据及 OBJ-85 来源关系承载，是否足够待负责会话核对，不据产品新增对象或改应付准入规则。
- 证据锚点：[R1-F.md:32](r1-f.md)；[UAE](../../frameworks/notes/unanet-ae.md) `UAE#供方账单及采购分录`：AP 印刷／PDF p10–13，Purchase Journal / Vendor Invoice，p10 的账单标识由应付账户、供应商、账单号组成。；反方 `/private/tmp/advrev/EF.md` 按编号。

## R1-F-18

- 词条 id：receivable、payable、retention_money；统一往来款项上位词 id 待查重。
- 现定义：
  - `terms.yaml#receivable`（应收款项；bc=receivables.collection）：应收账款与其他应收款的往来款项；开票产生是它的一种来源；不含质量保证金、保证金（各自是聚合）
  - `terms.yaml#payable`（应付款项；bc=payables.payment）：应付账款与其他应付款的往来款项，与应收款项对称
  - `terms.yaml#retention_money`（质量保证金；bc=receivables.collection）：按合同质保条款拆分的独立应收类型（RET，F127C/D、F181）
  - 信息图 §1 `OBJ-85`（往来款项）：对相对方的无条件收款权或付款义务的开放项；一个对象，以方向区分收付（BIZBOK Payment）
- 问题：OBJ-85 总定义为“无条件收款权或付款义务”，类型却含预收、预付及可能仍附履约条件的质保金。准则上的合同负债与仍附其他条件的合同资产，不能仅凭放入同一开放项对象就叫作无条件收款权。统一往来款项的定义缺少覆盖这些类型的上位含义。
- 建议定义：往来款项为按相对方、方向及款项类型跟踪应收、应付、预收、预付和担保等结算余额与清偿情况的开放项；结算跟踪的共同承载不决定会计分类。无条件收款权、合同资产、合同负债分别按其准则条件识别，质保金是否仍附履约条件须按协议及财务结论确定。
- 边界及待定：保留一个对象加方向及现有类型；不裁定 F-03、FD-12、C-11 / C-12，也不改销售应收由蓝字发票产生的规则。
- 证据锚点：[R1-F.md:34](r1-f.md)；[CAS14](../../frameworks/notes/cas14-2017.md) 原文第四十一条，印刷／PDF p18–19：合同资产、合同负债、无条件应收分开定义；对象稿 §2.4 自记客户扣留价款“属合同资产或应收，待 F-03”；词条 `retention_money` 注记同样保留条件差别。；反方 `/private/tmp/advrev/EF.md` 按编号。

## R1-F-20

- 词条 id：position、job_grade。
- 现定义：
  - `terms.yaml#position`（岗位；bc=organization.workforce）：任职与费率适用的岗级体系
  - `terms.yaml#job_grade`（职级；bc=organization.workforce）：员工职级 T1-T7 七级（组织主数据，计费费率与折扣权限的档位依据）
  - 信息图 §1 `OBJ-58`（岗位）：职位目录中的一个职责类别（如结构设计师、项目经理），可配职级区间
- 问题：对象稿岗位是“职责类别，可配职级区间”；词表 `position / Position` 仍定义“任职与费率适用的岗级体系”，另有 `JobGrade` 表示职级。两处对职责与等级的含义未对齐。Unanet 的 Job Title 可以表示职责、级别或两者，因此不能反过来声称业界一律要求 Position 只表示职责。
- 建议定义：position：职位目录中的一个职责类别，可关联适用职级区间；不表示编制额度。job_grade 表示职级，与职责类别分别维护，任职引用岗位及适用职级。费率适用按已定费率键引用，不将岗位与职级混为一个“岗级体系”。
- 边界及待定：沿 Q26 / intent 134134 Q-08 只管职责目录、不管编制；不自行修改 JobGrade 的现有级数。
- 证据锚点：[R1-F.md:36](r1-f.md)；[UAE](../../frameworks/notes/unanet-ae.md) `UAE#职称与岗位`：AD 印刷／PDF p33，Job Title 三种用法；SP p10，Default Job Title 与 Project Role 分列；对照词条 `position`、`job_grade` 与对象稿 §2.6。；反方 `/private/tmp/advrev/EF.md` 按编号。

## R1-F-21

- 词条 id：未登记；社保参保 / 公积金缴存的正式 id 待 one-glossary 查重。
- 现定义：
  - 对应正式词条未登记；以下是对象稿现有定义或资料中的待定概念。
  - 信息图 §1 `OBJ-62`（社保与公积金参保）：员工在某参保地的险种参保、缴费基数与增减员
- 问题：OBJ-62 用“参保地、险种参保、增减员”同时定义社保与公积金；公积金原文是缴存登记及账户设立、转移、封存。公积金账户封存不等于某保险险种终止参保，复合名称的后半部分缺相应定义。未找到该对象正式英文，不擅称已有英文误译。
- 建议定义：同一人员记录按类型区分社会保险参保关系与住房公积金缴存关系：前者保留参保地、险种、缴费基数及增减员；后者保留缴存登记、个人账户设立、转移与封存及经办结果。公积金账户封存不等于保险险种终止参保。
- 边界及待定：不拆对象，不新增 OBJ 编号；两类账户与办理状态的正式命名交 one-glossary。
- 证据锚点：[R1-F.md:37](r1-f.md)；[SOC](../../frameworks/notes/social-insurance.md) `SOC#公积金账户唯一`、`SOC#公积金增减员`、`SOC#公积金汇缴期限`：住房公积金管理条例第十三至十五条，印刷／PDF p5–6；第十九条，p7。；反方 `/private/tmp/advrev/EF.md` 按编号。

## R1-F-22

- 词条 id：未登记；股东／合伙人出资（提议名）正式 id 待 one-glossary 查重。
- 现定义：
  - 对应正式词条未登记；以下是对象稿现有定义或资料中的待定概念。
  - 信息图 §1 `OBJ-91`（合伙人出资）：合伙人或股东对被投资法人的注资与退回
- 问题：OBJ-91 定义同时覆盖合伙人或股东，但对象名及两个流程名只称“合伙人出资”。Silverston 明确列出 SHAREHOLDER 角色；普通股东出资是否也受“合伙人出资”覆盖，现有中文名称窄于其明示定义。书中组织业务角色 PARTNER 不作为中国法“合伙人”的译名证据。没有正式英文词条，不把草稿译名当成已定名。
- 建议定义：股东／合伙人出资（提议名）：股东或合伙人对被投资法人的注资与依法办理的退回；主体角色应与法人及适用法律形式对应。现名“合伙人出资”的范围说明须明确覆盖普通股东，不把产品 PARTNER 角色直接当成中国法律“合伙人”。
- 边界及待定：仅交名称范围对齐，不审退资合法性，不改阶段及流程编号。
- 证据锚点：[R1-F.md:38](r1-f.md)；[SIL2](../../frameworks/notes/silverston-dmrb2.md) `SIL2#股东`、`SIL2#主体角色关系`：第7章 Person Roles，图7.1，`14_chapter_7.html`。；反方 `/private/tmp/advrev/EF.md` 按编号。

## R1-C-20 补充：既有提议状态定名

- 词条 id：未登记；员工活动登记终态「已取消」是 PRC-144 已有提议名，正式 id 待 one-glossary 查重。
- 现定义：PRC-144.stages_note 已列从已批准或已完成到提议终态已取消；信息图 OBJ-116 基线没有该路径。
- 问题：对象稿需同步现行取消路径，不能顺带将提议名升为正式状态词。
- 建议定义：对已有员工活动登记实施撤销纠错的终态；与正常已完成区分，不计 VS-14.4 服务办结退出。
- 边界及待定：信息图仅按目录同步并保留“提议状态名”标记；不恢复人工审批、不新增状态授权。
- 证据锚点：[R1-C.md:32](r1-c.md)；本系统直接证据：`process-catalog.yaml` PRC-143、PRC-144 的 `stages_note`（B31-5、B31-8及活动取消路径）；外部核对：[silverston-dmrb3.md](../../frameworks/notes/silverston-dmrb3.md) `SIL3#状态历史与规则`，`OEBPS/9781118080832c06.xhtml#c06_level1_6`、图6.6，强调状态及其历史须表达实际业务；已回原文。；PRC-144.stages_note（B31-8）。

## R1-E-01：61 个阶段与度量基础事件对应表（提议）

共用候选度量为 stage_entry_count、stage_success_exit_count、stage_other_exit_count、stage_open_count、stage_elapsed_time，均未登记。每行按“阶段实例 × 轮次 × 事件时点”对应；计数时段和观察截止日分别声明，成功退出与其他退出取实际办理结局，不能只凭门满足推断已执行。

下表只列当前条件的证据定位：进入候选是进入事件所校验的条件；退出候选是退出事件的状态依据。进入 / 退出事件仍须由真实办理记录证明；缺对象状态、派生条件及所有混合出口保持待核。跳过记不适用的实际进入和耗时并注明跳过依据；未进入阶段不算期末未完，已进入未退出者才算未完。

| 阶段 | 进入事件所据条件 | 退出事件所据条件 | 成功 / 其他退出的核对边界 | 依据锚点 |
|---|---|---|---|---|
| VS-01.1 采集线索 | 或[OBJ-17「新建」；OBJ-16「进行中」；OBJ-105「已计划」] | 或[OBJ-17「已确认（转商机）」；OBJ-17「已关闭（无价值，原因必填）」（停止）] | 含停止 / 异常候选，逐结局分列，不能全计成功 | `value-streams.v2.yaml#VS-01.1.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-01.2 赢得委托 | 且[OBJ-18「跟进中」；OBJ-24「已启用」；OBJ-26「已生效」] | 或[OBJ-18「已赢单」；OBJ-18「丢单 / 已放弃」（停止）] | 含停止 / 异常候选，逐结局分列，不能全计成功 | `value-streams.v2.yaml#VS-01.2.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-02.0 开始项目 | 或[OBJ-18「已赢单」；OBJ-12「已批准」；OBJ-05「已签署生效」] | 且[OBJ-29「已立项 / 执行中」；OBJ-31「已批准（对客）/ 内部锁定」；OBJ-32「已锁定（成本基线）」；OBJ-30「已完成」] | 按实际结局分列；回退重入另记轮次 | `value-streams.v2.yaml#VS-02.0.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-02.1 确认概念 | pattern：entry_common；且[OBJ-42「已提供」] | pattern：exit_common | 按实际结局分列；回退重入另记轮次；须展开本流 stage_pattern | `value-streams.v2.yaml#VS-02.1.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-02.2 确认方案 | pattern：entry_common；且[OBJ-42「已提供」] | pattern：exit_common | 按实际结局分列；回退重入另记轮次；须展开本流 stage_pattern | `value-streams.v2.yaml#VS-02.2.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-02.3 完成初步设计 | pattern：entry_common；且[OBJ-42「已提供」] | pattern：exit_common | 按实际结局分列；回退重入另记轮次；须展开本流 stage_pattern | `value-streams.v2.yaml#VS-02.3.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-02.4 完成深化设计 | pattern：entry_common；且[OBJ-42「已提供」] | pattern：exit_common | 按实际结局分列；回退重入另记轮次；须展开本流 stage_pattern | `value-streams.v2.yaml#VS-02.4.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-02.5 完成招标采购配合 | pattern：entry_common | pattern：exit_common | 按实际结局分列；回退重入另记轮次；须展开本流 stage_pattern | `value-streams.v2.yaml#VS-02.5.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-02.6 完成施工配合 | pattern：entry_common | pattern：exit_common；且[OBJ-38「已关闭」] | 按实际结局分列；回退重入另记轮次；须展开本流 stage_pattern | `value-streams.v2.yaml#VS-02.6.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-02.7 完成收尾 | 且[OBJ-29「收尾中」] | 且[OBJ-107「已发布」；OBJ-106「已回收 / 已评分 / 已跟进」；OBJ-35「已封存」；OBJ-53「已封存」；OBJ-29「已结项 → 已归档」] | 按实际结局分列；回退重入另记轮次 | `value-streams.v2.yaml#VS-02.7.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-03.1 编制检查计划 | 且[OBJ-29「执行中」]；或[OBJ-48「已生效」；OBJ-49「已计划」] | 且[OBJ-49「已安排」] | 按实际结局分列；回退重入另记轮次 | `value-streams.v2.yaml#VS-03.1.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-03.2 完成现场检查 | 且[OBJ-49「已安排」] | 且[OBJ-49「已审核 → 已锁定」] | 按实际结局分列；回退重入另记轮次 | `value-streams.v2.yaml#VS-03.2.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-03.3 发布不符合项 | 且[OBJ-38「已识别」] | 且[OBJ-38「已发布」] | 按实际结局分列；回退重入另记轮次 | `value-streams.v2.yaml#VS-03.3.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-03.4 跟进整改 | 且[OBJ-38「整改中」] | 或[OBJ-38「已闭合（复查通过）」；OBJ-38「客户书面接受风险」；OBJ-38「已停止跟踪（原因必填）」] | 含停止 / 异常候选，逐结局分列，不能全计成功 | `value-streams.v2.yaml#VS-03.4.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-03.5 登记报告认可 | 且[OBJ-49「已锁定」] | 且[OBJ-35「已签发 → 已认可」] | 按实际结局分列；回退重入另记轮次 | `value-streams.v2.yaml#VS-03.5.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-04.1 签署合同 | 或[OBJ-18「已赢单」；OBJ-12「已批准」] | 且[OBJ-05「已签署生效（回签即生效，基线 1）」；OBJ-07「已生效」；OBJ-12「已转正（并入协议）」] | 按实际结局分列；回退重入另记轮次 | `value-streams.v2.yaml#VS-04.1.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-04.6 完成合同履行 | 且[OBJ-05「已签署生效 / 履行中」] | 且[OBJ-05「已完成」] | 按实际结局分列；回退重入另记轮次 | `value-streams.v2.yaml#VS-04.6.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-04.2 确认收入 | 且[OBJ-05「已签署生效 / 履行中 / 已完成」；或[OBJ-35「已认可」；且[OBJ-35「已交付（签收） / 审查中」；OBJ-35「已声明」]]] | 且[缺对象状态「收入确认事实已过账」] | 按实际结局分列；回退重入另记轮次；非对象 / 缺口条件未关闭 | `value-streams.v2.yaml#VS-04.2.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-04.3 开具发票 | 或[OBJ-07「可结算」；OBJ-82「草稿」] | 且[OBJ-82「已开票」；OBJ-83「已开具」] | 按实际结局分列；回退重入另记轮次 | `value-streams.v2.yaml#VS-04.3.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-04.4 回收款项 | 且[OBJ-85「未结 / 已部分结清 / 争议」] | 或[且[OBJ-85「已结清」；OBJ-86「已分配」]；OBJ-85「已核销（坏账）」] | 按实际结局分列；回退重入另记轮次 | `value-streams.v2.yaml#VS-04.4.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-04.5 确认最终价款 | 且[OBJ-05「已完成」] | 或[OBJ-05「已结算（结算书经双方确认）」；OBJ-05「已终止（原因必填）」（PRC-68 终止协议并清算）] | 含停止 / 异常候选，逐结局分列，不能全计成功 | `value-streams.v2.yaml#VS-04.5.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-05.1 完成供方准入 | 且[OBJ-02「已申请」] | 且[OBJ-02「合格」；OBJ-65「有效」] | 按实际结局分列；回退重入另记轮次；允许跳过，单列依据 | `value-streams.v2.yaml#VS-05.1.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-05.2 签署供方协议 | 且[OBJ-02「合格」]；或[OBJ-29「执行中」；OBJ-100「基线（不可变）」] | 且[OBJ-05「已签署生效」] | 按实际结局分列；回退重入另记轮次 | `value-streams.v2.yaml#VS-05.2.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-05.3 确认供方履约 | 且[OBJ-05「履行中 / 已签署生效」] | 且[OBJ-87「已确认」] | 按实际结局分列；回退重入另记轮次 | `value-streams.v2.yaml#VS-05.3.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-05.4 确认应付款项 | 且[OBJ-87「已确认」] | 且[OBJ-85「未结」；OBJ-83「已用途确认 → 已入账」] | 按实际结局分列；回退重入另记轮次 | `value-streams.v2.yaml#VS-05.4.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-05.5 完成付款 | 且[OBJ-85「未结 / 已部分结清」] | 且[OBJ-86「已成功 / 已核销」；OBJ-85「已结清 / 已部分结清」] | 按实际结局分列；回退重入另记轮次 | `value-streams.v2.yaml#VS-05.5.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-05.6 完成供方结算 | 且[OBJ-05「已完成」] | 或[OBJ-05「已结算」；OBJ-05「已终止（原因必填）」] | 含停止 / 异常候选，逐结局分列，不能全计成功；允许跳过，单列依据 | `value-streams.v2.yaml#VS-05.6.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-06.1 完成申报缴款 | 且[缺对象状态「法定申报期已到」] | 且[OBJ-92「已申报 → 已缴款」] | 按实际结局分列；回退重入另记轮次；非对象 / 缺口条件未关闭 | `value-streams.v2.yaml#VS-06.1.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-06.2 完成资质维护 | 或[缺对象状态「年检或核查通知已到」；缺对象状态「资质有效期临近」] | 或[OBJ-65「有效」；OBJ-65「已降级 / 已撤销 / 已到期」（停止；到期指未延续）]；且[OBJ-64「有效」] | 含停止 / 异常候选，逐结局分列，不能全计成功；非对象 / 缺口条件未关闭 | `value-streams.v2.yaml#VS-06.2.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-07.1 提出招聘需求 | 且[OBJ-58「已启用」] | 或[OBJ-60「已批准」；OBJ-60「已关闭（录满 / 取消）」] | 按实际结局分列；回退重入另记轮次；允许跳过，单列依据 | `value-streams.v2.yaml#VS-07.1.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-07.2 完成录用 | 且[OBJ-56「已登记（候选）」] | 或[OBJ-56「待入职」；OBJ-56「未录用」（停止）] | 含停止 / 异常候选，逐结局分列，不能全计成功 | `value-streams.v2.yaml#VS-07.2.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-07.3 完成人员入职与任职 | 且[OBJ-56「待入职」] | 且[OBJ-56「试用期 / 正式」；OBJ-05「已签署生效 / 履行中」；OBJ-62「在缴」] | 按实际结局分列；回退重入另记轮次 | `value-streams.v2.yaml#VS-07.3.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-08.0 批准事前申请 | 且[缺对象状态「员工有需事前申请的支出计划」] | 或[OBJ-118「已批准」；OBJ-119「已批准」] | 按实际结局分列；回退重入另记轮次；非对象 / 缺口条件未关闭；允许跳过，单列依据 | `value-streams.v2.yaml#VS-08.0.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-08.1 完成借款发放与归还 | 且[OBJ-85「未结」] | 且[OBJ-85「已结清」] | 按实际结局分列；回退重入另记轮次；允许跳过，单列依据 | `value-streams.v2.yaml#VS-08.1.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-08.2 完成报销核定 | 且[OBJ-88「已提交」] | 且[OBJ-88「已核定（所属期间确定）」；OBJ-83「已入账」] | 按实际结局分列；回退重入另记轮次 | `value-streams.v2.yaml#VS-08.2.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-08.3 完成报销付款 | 且[OBJ-88「已过账」] | 且[OBJ-85「已结清」；OBJ-86「已成功 / 已核销」；OBJ-88「已付」] | 按实际结局分列；回退重入另记轮次 | `value-streams.v2.yaml#VS-08.3.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-09.1 编制计划 | 且[OBJ-100「编制中」] | 且[OBJ-100「基线（不可变）」] | 按实际结局分列；回退重入另记轮次 | `value-streams.v2.yaml#VS-09.1.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-09.2 变更预测 | 且[OBJ-100「基线（不可变）」] | 且[OBJ-100「新版本」] | 按实际结局分列；回退重入另记轮次 | `value-streams.v2.yaml#VS-09.2.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-09.3 完成经营复盘 | 且[OBJ-97「已关账」] | 或[OBJ-100「新版本」；缺对象状态「经营复盘已闭环」] | 按实际结局分列；回退重入另记轮次；非对象 / 缺口条件未关闭 | `value-streams.v2.yaml#VS-09.3.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-10.0 执行记账结转 | 且[OBJ-97「已开放」] | 且[OBJ-98「已记账（不可逆）」；缺对象状态「会计分录待处理队列已清空、过账拒收记录已处置」] | 按实际结局分列；回退重入另记轮次；非对象 / 缺口条件未关闭 | `value-streams.v2.yaml#VS-10.0.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-10.1 关闭会计期间 | 且[OBJ-97「已开放」；OBJ-98「已记账（不可逆）」] | 且[OBJ-97「关账中 → 已关账」] | 按实际结局分列；回退重入另记轮次 | `value-streams.v2.yaml#VS-10.1.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-10.2 发布报表 | 且[OBJ-97「已关账」；OBJ-98「已记账（不可逆）」] | 且[OBJ-125「已签字」；缺对象状态「三大报表已出具」] | 按实际结局分列；回退重入另记轮次；非对象 / 缺口条件未关闭 | `value-streams.v2.yaml#VS-10.2.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-11.1 编制知识条目 | 或[OBJ-107「已发布」；OBJ-110「起草」] | 且[OBJ-110「评审」] | 按实际结局分列；回退重入另记轮次 | `value-streams.v2.yaml#VS-11.1.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-11.2 审核并发布知识条目 | 且[OBJ-110「评审」] | 且[OBJ-110「已发布」] | 按实际结局分列；回退重入另记轮次 | `value-streams.v2.yaml#VS-11.2.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-11.3 修订并废除知识条目 | 且[OBJ-110「已发布」] | 或[OBJ-110「已修订（新版本）」；OBJ-110「已废止」] | 按实际结局分列；回退重入另记轮次；允许跳过，单列依据 | `value-streams.v2.yaml#VS-11.3.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-12.1 确认收到出资 | 且[OBJ-91「已认缴」] | 且[OBJ-91「已实缴（到账）」] | 按实际结局分列；回退重入另记轮次 | `value-streams.v2.yaml#VS-12.1.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-12.2 分配利润 | 且[OBJ-97「已关账」] | 且[OBJ-112「已决议 → 已支付」] | 按实际结局分列；回退重入另记轮次 | `value-streams.v2.yaml#VS-12.2.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-12.3 退还出资 | 且[OBJ-91「已实缴（到账）」] | 或[OBJ-91「已部分退回 / 已退回」] | 按实际结局分列；回退重入另记轮次；允许跳过，单列依据 | `value-streams.v2.yaml#VS-12.3.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-13.1 确认月度考勤 | 且[OBJ-56「试用期 / 正式 / 待离职」；OBJ-28「已维护」] | 且[OBJ-111「已锁定」；OBJ-68「已批准 / 已销假」；OBJ-72「已批准 / 已补偿」] | 按实际结局分列；回退重入另记轮次 | `value-streams.v2.yaml#VS-13.1.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-13.2 完成绩效评估与奖金核定 | 或[OBJ-74「已生效」；OBJ-109「草拟」] | 或[OBJ-109「已确认」；OBJ-104「已核定」] | 按实际结局分列；回退重入另记轮次；允许跳过，单列依据 | `value-streams.v2.yaml#VS-13.2.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-13.3 完成薪酬结算 | 且[OBJ-111「已锁定」；OBJ-62「在缴」] | 且[OBJ-73「已批准」] | 按实际结局分列；回退重入另记轮次 | `value-streams.v2.yaml#VS-13.3.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-13.4 完成个税及社保公积金申报缴纳 | 且[OBJ-73「已批准 / 已发放 / 已过账」；OBJ-92「待申报」] | 且[OBJ-92「已申报 → 已缴款」] | 按实际结局分列；回退重入另记轮次 | `value-streams.v2.yaml#VS-13.4.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-13.5 完成薪酬发放 | 且[OBJ-73「已批准」] | 且[OBJ-85「已结清」；OBJ-86「已成功 / 已核销」；OBJ-73「已发放 → 已过账」] | 按实际结局分列；回退重入另记轮次 | `value-streams.v2.yaml#VS-13.5.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-13.6 完成离职结清 | 且[OBJ-56「待离职」] | 且[OBJ-56「已离职」；OBJ-62「减员（转移 / 封存）」；OBJ-05「已终止」] | 含停止 / 异常候选，逐结局分列，不能全计成功 | `value-streams.v2.yaml#VS-13.6.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-14.1 提交申请 | 且[缺对象状态「员工有需办理的内部服务事项」] | 或[OBJ-113「审批中 / 已批准」；OBJ-114「已提交 / 审批中」；OBJ-115「已通过」；OBJ-116「已批准」] | 按实际结局分列；回退重入另记轮次；非对象 / 缺口条件未关闭 | `value-streams.v2.yaml#VS-14.1.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-14.2 批准申请 | 或[OBJ-113「审批中」；OBJ-114「审批中 / 已提交」] | 或[OBJ-113「已批准」；OBJ-114「处理中」] | 按实际结局分列；回退重入另记轮次；允许跳过，单列依据 | `value-streams.v2.yaml#VS-14.2.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-14.3 完成事项办理 | 或[OBJ-113「已批准」；OBJ-114「处理中」；OBJ-115「已通过 / 在用」；OBJ-115「已报修」] | 或[OBJ-113「已用印」；OBJ-114「已解决」；OBJ-115「已领用 / 已调拨」；OBJ-115「已维修登记」] | 按实际结局分列；回退重入另记轮次；允许跳过，单列依据 | `value-streams.v2.yaml#VS-14.3.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-14.4 确认办结 | 或[OBJ-113「已用印」；OBJ-114「已解决 / 处理中 / 审批中」；OBJ-115「在用」；OBJ-115「已维修登记」；OBJ-116「已批准」] | 或[OBJ-113「已关闭」；OBJ-114「已关闭 / 已退回」；OBJ-115「已结束」；OBJ-115「已维修登记」；OBJ-116「已完成」] | 按实际结局分列；回退重入另记轮次 | `value-streams.v2.yaml#VS-14.4.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-15.1 拟订 | 且[缺对象状态「需新建或修订一版经营基准」] | 或[OBJ-50「草拟」；OBJ-78「草稿」；OBJ-99「草拟 / 财务评审」；OBJ-74「编制」；OBJ-124「版本草稿」] | 按实际结局分列；回退重入另记轮次；非对象 / 缺口条件未关闭；允许跳过，单列依据 | `value-streams.v2.yaml#VS-15.1.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-15.2 批准生效 | 或[OBJ-50「草拟」；OBJ-78「草稿 / 已批准」；OBJ-99「财务评审」；OBJ-74「审批」；OBJ-124「版本草稿」；缺对象状态「需新建或修订一版经营基准」] | 或[OBJ-50「已生效」；OBJ-78「已生效」；OBJ-99「已生效」；OBJ-74「已生效」；OBJ-28「已维护」；OBJ-95「已启用」；OBJ-58「已启用」；OBJ-55「已启用」；OBJ-89「在用」；OBJ-124「版本已生效」] | 按实际结局分列；回退重入另记轮次；非对象 / 缺口条件未关闭 | `value-streams.v2.yaml#VS-15.2.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
| VS-15.3 修订停用 | 或[OBJ-50「已生效」；OBJ-78「已生效」；OBJ-99「已生效」；OBJ-74「已生效」；OBJ-95「已启用」；OBJ-58「已启用」；OBJ-55「已启用」；OBJ-89「在用」；OBJ-124「版本已生效」] | 或[OBJ-50「已停用」；OBJ-78「被取代」；OBJ-99「被取代」；OBJ-74「已结束」；OBJ-95「已停用」；OBJ-58「已停用」；OBJ-55「已停用 / 已撤销」；OBJ-89「已销户」；OBJ-124「版本已停用」] | 含停止 / 异常候选，逐结局分列，不能全计成功；允许跳过，单列依据 | `value-streams.v2.yaml#VS-15.3.entry/exit`；R1-E-01、BI_SCOPE §3.2 |
