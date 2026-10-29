# 平台服务适配研究 C 组：⑤待办与提醒　⑥参考数据　⑦a 中文全文检索　⑦b 现场采集

日期：2026-10-20　依据：ADR-0041 决定 6（先适配评审，不预选推荐项）　只读研究，未改仓库、未调用外部 API、未注册账号。
路径均相对仓库 `docs/`（简写 DM），后端代码相对 `backend/src/main/java/com/forcitis/one/`（简写 B）。

## 0. 先读这个：关键发现（跨项）

1. **统计局已不再公开「统计用区划代码」**（⑥）：国家统计局咨询答复称该代码属内部资料、不向社会公开，自 2024 年 10 月起只公开编制规则；县级以上代码由民政部按 2025-09-01 施行的《行政区划代码管理办法》确定并每年在国家地名信息库发布一次。「接国标行政区划」的官方源须改为民政部口径，且乡级以下无公开官方全量源（见 §3.4）。
2. **阿里云 RDS PostgreSQL 18 标准版官方插件表列有 zhparser 2.1、pg_jieba 1.2.0、pg_trgm 1.6、pg_bigm 1.2、pgroonga 4.0.5**；zhparser / pg_jieba / pg_bigm 须先加入 `shared_preload_libraries`（改参数，需重启实例，见 §5）。倚天版 pgroonga 在 PG 18 上不支持。
3. **仓库内两处口径不一致**：`B/platform/scheduling/package-info.java` 写「模块自带 `@Scheduled`+`@SchedulerLock`，且不依赖本模块（G46）」；`DM/architecture/proposals/closeout/CONVENTIONS.md:110` 写「time 触发由所属 BC 向 `platform.scheduling` 登记定时，边 BC → platform.scheduling 登记」。二者对「定时是登记制还是各自注解」说法相反，提醒方案选型前须先对齐（交 one-architecture）。
4. **「待办」一词现归 `platform.approval`**（`DM/glossary/terms.yaml` 中 `todo`、`inbox`，bc: platform.approval，定义「待处理任务」「当前用户待处理任务的列表」）。若建跨 BC 的待办中心，会与之同名异义或需改使用范围，按规则 2 应登记给 one-glossary，不自行裁定。
5. **Money 带币种但 SCALE 固定 2、跨币种运算直接拒绝**（`B/kernel/Money.java`）；「只用人民币」是文档口径（GL-124、CC-N06），代码层并未禁止其他币种构造，只对非 2 位小数币种无保护（CC-N06 已登记为地基缺口）。

---

## ⑤ 待办与提醒

### 5.1 业务需求证据

| 需求 | 出处 | 所属 BC / 建造批 |
|---|---|---|
| 审批任务时限：T+1 工作日 23:59 超时，次日 09:00 提醒，再 m 个工作日升级；实现为 ShedLock 作业 `platform.approval.deadline.enforce` | DM/REIMAGINED_ARCHITECTURE.md:93、:266、:296；DM/DECISION_DIGEST.md:74-76、:86、:91 | platform.approval，W0 |
| 合并待办（新旧）；通知意图经 approval 内部端口 → integration 发企业微信文本卡片；「出现第二个使用者时再抽出 `platform.notification`」 | DM/REIMAGINED_ARCHITECTURE.md:285-296（§3.6–3.7） | W0 |
| 敏感角色复核「到期重新生成待办条目」，到期作业每日 00:05（`@Scheduled`+ShedLock），去重键（原条目 ID，有效至） | DM/adr/ADR-0044.md:56-72；DM/adr/ADR-0026.md:117（季度复核超期提醒内控/财务负责人） | platform.identity，已有 |
| 注册执业证书、资质到期：「标记执业证书已到期」「资质有效期临近」作为进入条件；`on_time: 到期日届满` | DM/architecture/proposals/ENTERPRISE_AGGREGATES_DRAFT.md:362、:520；DM/architecture/proposals/value-streams.v2.yaml:1186（MS-20）；DM/architecture/proposals/processes.v1.yaml:786 | organization.workforce，W0 |
| 试用期评估：届满前 15 日生成、5 个工作日内完成、届满前 3 个工作日未完成升级 | DM/architecture/decision-page/PPL_CHANGES.md:56-57 | organization.workforce，W0 |
| 月度考勤逾期默认确认；28 天未确认提醒催办（只提醒、不视为认可）；工时填报提醒/锁定，「应填人员口径与催办时点待定」 | DM/architecture/decision-page/PPL_TRACE.md:88；DM/architecture/decision-page/REV_CHANGES_R3.md:29；DM/DECISION_DIGEST.md:201、:496 | organization.leave / cost.timesheet，W2–W3 |
| 保证金与押金、质保金到期可收（`RetentionDue`「质保期届满、质保金应收进入可回收状态」）；到期只作派生提示（STEP3 C-10） | DM/GLOSSARY.md:477；DM/architecture/proposals/STEP3_STAGES.md:53；DM/architecture/proposals/FIT_AUDIT_2026-10-16.md:266；DM/architecture/proposals/ENTERPRISE_AGGREGATES_DRAFT.md:478 | receivables.collection，W4 |
| 财务参数 22 项（申报提醒前 3 日、缴款提醒前 5 日、月度/年度关账提醒日、已完成协议未结算 P30D、决议后未付款 P90D、发票查验提醒 PT1H 等） | DM/architecture/decision-page/FIN_TRACE.md:63；DM/adr/ADR-0046.md:85（只在财务设定日期后提醒，不强制） | accounting.* / payables.* / expense.*，W1–W5 |
| 合同收款节点、报价有效期 | 用户列举；**在 DM 下未检到明确出处**（「有效期」仅见 AGG-02 资质、AGG-107 知识条目、费率表）。合同收款节点按 `ENTERPRISE_AGGREGATES_DRAFT.md` 的付款条件项 OBJ-07 推断为到期类，但无提醒规则文字 | contract.agreement，W3 |
| 老系统：流程「项目阶段评审提醒」（463 实例）、「项目成果内审提醒」（已停用，在途 53）是 A8 流程而非系统提醒 | DM/OA_PROCESS_INVENTORY.md:167、:170 | 说明老系统用审批流模拟提醒 |

小结：已有证据的提醒源至少 8 类、分属 6 个以上 BC；**前三类（审批超时、权限复核到期、证书/试用期）在 W0 即出现**。已建现状：`platform.integration` 已有企业微信卡片队列与出站投递（`B/platform/integration/application/QueueWecomCard.java`、`DeliverWecomCards.java`、`domain/OutboundDelivery.java`、`RetrySchedule.java`），`platform.scheduling` 仅是「各模块自带作业」的约定包。

各模块各自做的代价（现状推演）：每个提醒源要自写 ① 到期扫描作业（ShedLock 名 `{子域}.{bc}.{name}`）② 提前量与升级参数的配置与版本 ③ 收件人解析（人员在职、汇报线、离职移交）④ 去重键与重试 ⑤ 非生产白名单（RULE-375）⑥ 运维页与告警。审批已把这套做了一遍（RULE-377..384），ADR-0044 又做了一遍（去重键不同，ADR-0044 决定 3）。

### 5.2 可选做法（并列，不排序）

**A　统一提醒规则 + 待办中心（平台层）**
- 要求：业务 BC **推送**「到期登记」（`schedule/cancel(subject 引用, 规则码, 触发时刻, 收件人策略, 去重键)`），平台按登记触发、去重、投递并汇总成待办视图；平台不得反查业务表（依赖方向：业务 → 平台，ADR-0051 骨架、CONVENTIONS:110）。合并视图要含审批待办（现归 platform.approval）与 ADR-0044 条目。
- 代价：开发量中（新模块或并入现有；估算约一个 BC 规模，无实测）；费用无新增云产品；运维一张登记表+一个触发作业+一个待办读模型；安全面：待办内容含单据摘要，须沿用 ADR-0026 范围规则与 `Masked` 约定，否则跨 BC 汇总会成为越权出口。要求各 BC 在到期日变更（合同变更、证书延续）时撤销并重登记，漏撤会产生误提醒。
- 收益：提醒参数、工作日历、升级、去重、非生产白名单、投递告警只写一次；用户有「我的待办」单一入口（brief 已要求待办数量与老系统一致，DM/DECISION_DIGEST.md:85）；新增提醒源成本低。
- 把握：模式本身成熟（Flowable 自身也是「登记 job + 执行器」）；本系统细节（登记制与 G46 冲突、待办用语、跨 BC 范围控制）未验证。

**B　各模块自带提醒 + 企业微信消息**
- 要求：每个 BC 自写 `@Scheduled`+ShedLock 作业，到点调 `platform.integration` 的企业微信卡片队列（已建）。这正是 `package-info` 与 ADR-0002/0044 当前实际做法。
- 代价：每源开发量小（约为 A 的单源接入量的 1.5–2 倍，因要自带参数与去重），但 8+ 源叠加后重复件多；费用无；运维：作业数线性增长，需每个作业各自的失败告警；安全面：无集中出口，反而不易越权，但各自实现收件人解析易出不一致（如离职人员仍被提醒）。无统一待办视图，除审批外用户只能在企业微信卡片或各业务页看到提醒。
- 收益：符合 CS-1.8「只实现当前阶段」，无新模块；BC 完全掌握语义；W0 即可交付。
- 把握：最高（已有先例，ADR-0044 已落地）。代价集中在后期重构：提醒源增多后再抽平台层要改所有已建作业。

**C　用 Flowable 定时边界事件承载**
- 要求：每个到期事项起一个流程实例（或在已有审批实例上挂非中断计时边界，BPMN_STYLE.md:31），Flowable 异步执行器触发；工作日历要自定义 `BusinessCalendar`（Flowable 官方文档只说明 `businessCalendarName` 指向引擎配置里定义的日历，未见内置节假日支持）。
- 代价：保证金、证书、试用期这类**事项等待数月至数年**，每项常驻一个流程实例与定时 job，引擎表（`platform_approval` schema）膨胀、历史表入归档范围；到期日变更要取消重建；Flowable 定时仅在启用异步执行器时触发（官方文档）；引擎时间是否取注入 Clock 尚是未决项 FLW-1（DM/phase1a/NEXT_BATCH_PLAN.md:166）；ADR-0007 决定 3 限定 BPMN 只编排人工步骤、超时、退回，业务判定不进引擎；审批时限已在设计中选用 ShedLock 作业而非 Flowable 计时（REIMAGINED_ARCHITECTURE.md:266）。
- 收益：与审批人工任务同一机制时天然契合（任务超时、升级、退回）；GUIDING_PRINCIPLES.md:61 已写「流程负责超时催办（定时边界事件）」。
- 把握：对「审批任务内部的超时」把握高；对「聚合属性到期日」（保证金、证书、质保金到期）不匹配——这些日期是业务事实，真相源应在聚合（INV 约束），引擎计时器会成为第二份真相。

### 5.3 落点与时点
- A：新建平台模块（模块名需查词表登记，不在此拟定；`待办`一词须先解决同名异义，见 §0-4），依赖 `platform.integration`（投递）、`platform.approval`（读审批待办，**注意 approval 不得反向依赖**）；或并入 `platform.integration`（已持有卡片队列）。最迟在 **W0 末**定契约，因为第二、三个使用者（证书/试用期、ADR-0044）已在 W0。
- B：无新模块；约定写入 CODING_STANDARD 即可，W0 起各 BC 自办。
- C：并入 `platform.approval`；只适用审批内超时。

### 5.4 适配结论
**现在需要**——限于「决定口径」：W0 已有 ≥3 个提醒源，REIMAGINED_ARCHITECTURE.md:296 设的「第二个使用者即抽 platform.notification」触发条件实际已满足，故须在 W0 前选定 A 或 B（并对齐 §0-3 的登记制冲突）。若选 B，则实现「现在」不新增平台模块，但须约定参数、去重键、收件人解析的共用小工具，以免 8 个源各写一套；若选 A，W0 同批建。统一待办视图（含非审批提醒）可「以后需要」（W3 前），因为审批合并待办已在 W0 建。
- 条件式：提醒源 ≤3 个且均无「聚合属性到期日」→ B 足够；出现保证金/证书/质保金等聚合到期日并需「我的待办」→ A 的收益显现；C 只用于审批任务内部超时。

### 5.5 未核实 / 把握不大
- 报价有效期、合同收款节点提醒：无出处（§5.1）。
- 企业微信应用消息限制（官方页，一手）：textcard 标题 ≤128、描述 ≤512 字符、url ≤2048 字节；touser ≤1000；同应用对同成员每分钟 ≤30 次、每小时 ≤1000 次，超出丢弃；每应用每日人次 = 账号上限数 ×200；建议避开整点与半点（投递延迟）；页面未提延时/定时发送，须自行排程。该页尾部约 12k 字符未读取。335 人规模远低于上限，但「候选组每人一张卡片」类广播要按人次核算。
- 估算人天为经验估计，无实测。
- Flowable 8 对 `businessCalendarName` 的细节仅读到官方 BPMN 页片段；高级章节页未讲业务日历，未能确认内置节假日支持。

---

## ⑥ 参考数据（币种汇率、税率、行政区划、银行、标准规范库等）

### 6.1 业务需求证据与现状

| 项 | 证据 | 判断 |
|---|---|---|
| 币种 | `B/kernel/Money.java`（币种+2 位小数，跨币种运算拒绝）；ADR-0016:36（Money=金额+币种）；DM/GL_COMPLIANCE_CHECKLIST.md:40（只用人民币，外币折算暂不需要）；DM/U8C_GL_PRACTICE.md:53、:186（U8C 币种档案 2 个，凭证只用人民币，无汇率方案实际使用，无汇兑损益）；DM/rules/GL_RULES.md GL-124；DM/CROSS_CUTTING_MATRIX.md:67（CC-N06） | 现只需 CNY；币种表是 ISO 4217，不是业务数据 |
| 汇率 | 费用报销只记人民币、不折算（U-43，DM/architecture/decision-page/FIN_TRACE.md:153）；F10-10 汇兑重估本期不做（DM/architecture/decision-page/FIN_CHANGES.md:13）；CC-N07：R0 入口「确认手工汇率 CSV 的用途；有外币则新开 ADR」（DM/CROSS_CUTTING_MATRIX.md:68；该 CSV 见 DM/BI_SCOPE.md:64，**用途未见结论**）；现金流量表「汇率变动影响」行老系统无公式（DM/rules/GL_RULES.md GL-051、GL-056） | 现无；是否有外币合同见 §6.3 |
| 税率 | 开票/进项按单据税率（DM/BUSINESS_RULES.md:2533 校验 0–1；ADR-0016:27 含税存储另存不含税额与税额）；税额计算口径 FQ-27 待决（DM/CROSS_CUTTING_MATRIX.md:69 CC-N08）；个税税率表、社保公积金比例在 AGG-119 薪酬规则版本（ENTERPRISE_AGGREGATES_DRAFT.md:557、INV-139）；增值税/附加税/所得税在 accounting.tax（ADR-0051 B-23） | 需要，但已被「各 BC 的配置型版本」覆盖一部分 |
| 行政区划 | DM 下无「行政区划」字样；间接需求：费率按「城市类别」（AGG-05，ENTERPRISE_AGGREGATES_DRAFT.md:374）、假期按「城市」应享（AGG-118，:543）、往来单位「境内/境外」（SSOT.yaml:104）、建设工程所在位置（AGG-33） | 需求弱，且多为「类别」而非行政区划编码 |
| 银行 | 往来单位银行账户（AGG-01）、员工收款账户（FIN_TRACE.md:155）、资金账户（AGG-87）；发票含购买方开户行与账号（RESPONSIBILITY_MATRIX.yaml:4331，敏感） | 开户行名称/联行号字典：需求有但文档无具体字段要求 |
| 标准规范库 | 无独立对象；证据/检查引用规范（AEC_OBJECTS_INSPECTION_EVIDENCE.md 引〔检测办法〕等）；知识条目 AGG-107「规范解读」（ENTERPRISE_INFO_MAP_DRAFT.md:176）；GLOSSARY.md:520「知识资产」 | 若要「规范编号+名称+状态」引用字典可并入 km.knowledge；全文入库有版权问题（§6.4） |
| 工作日历 | AGG-68 已在 organization.attendance，老日历 2016–2026 迁入+年度录入（DM/DECISION_DIGEST.md:443） | 已有先例：外部权威发布的数据由所属 BC 自管 |
| 配置型版本 | AGG-05/06/07、108、118、119、123、124、133、82/85/94 等十余个「配置型版本，生效区间不重叠、取不到须失败告警」（ENTERPRISE_AGGREGATES_DRAFT.md:248、:271、:460、:508、:543、:557、:635） | 业务语义参数已确立「各 BC 自管、版本化」写法 |

各 BC 各管的代价：币种表、税率表、银行字典、区划表在多个 BC 重复建（如 masterdata.counterparty、expense.spend、accounting.cashbank、organization.payroll 各要银行与区划），且同一数据多处有版本差。

### 6.2 可选做法（并列）

**A　平台参考数据模块（版本化、生效区间）**
- 要求：集中存放「外部权威发布的、多 BC 共用的」码表：币种(ISO 4217)、区划、银行、税率表（法定）、标准规范目录；每行带来源、发布日期、生效/失效日期（PG 18 `WITHOUT OVERLAPS` 时态约束，STACK_DECISION.md §2 已列为选用理由）、只追加；以 `api` 提供按日期取值，取不到失败并告警（INV-116 写法）。须解决命名与归属：ADR-0051 已有 `masterdata` 命名空间（目前仅 counterparty、catalog），参考数据也可落在其下新 BC 而非 platform（需 one-architecture 判；本报告不裁定）。
- 代价：开发量小到中（通用时态表+装载工具+管理页，估算）；费用无；运维：数据更新流程要有负责人（谁、何时、从哪取）；安全面低（非敏感，银行联行号公开信息；个人银行账户不属参考数据）。
- 收益：一次建、版本口径一致、BI 与总账读同一版；防止各 BC 出现不同税率版本。
- 把握：模式成熟（工作日历已先例）；风险在「哪些算参考数据」边界，易把业务配置（费率、假期规则）也吞进来，应只收外部权威源数据。

**B　各 BC 各管**
- 要求：按现有「配置型版本」写法，需要者自建。
- 代价：重复；但每份只含本 BC 要的子集，开发量最小、W 批次无阻塞；风险是同一事实多版本（如两个 BC 对同一银行行名不同）。
- 收益：符合 CS-1.8，当前仅需 CNY 与少量税率，无额外基础设施。
- 把握：高；但 ADR-0051 BC 集中度（accounting.tax、expense、cashbank、counterparty 都用银行）会使重复提早出现。

**C　接外部数据源**
- 汇率：中国外汇交易中心（chinamoney.com.cn）受人民银行授权每日计算并发布人民币汇率中间价（一手：该站「汇率中间价」页，列 25 个币种，页面未说明发布时间、是否有下载/接口、使用条款）；会计准则第 19 号应用指南称即期汇率通常为人民银行当日公布的中间价（二手：德勤/CPA 协会转载；一手原文在财政部官网，本次未取到第七条原文）。
- 行政区划：**国家统计局不再公开具体统计用区划代码**（一手：国家统计局咨询答复，stats.gov.cn/hd/lyzx/zxgk/202509/t20250903_1960996.html；搜索摘要提及自 2024 年 10 月起）；县级以上代码由民政部确定并每年在国家地名信息库发布，省级民政每半年发布乡级代码（一手：新华社报道《行政区划代码管理办法》，2025-09-01 施行）。官方「全国行政区划信息查询平台」入口本次未能核实。第三方 GB/T 2260 数据包（如 packagist、npm）非官方，需与官方核对。
- 税率：增值税法自 2026-01-01 施行，税率 13%/9%/6% 三档（二手：央视/人民网等报道；一手全文在国家税务总局法规库 fgk.chinatax.gov.cn，本次未取到条文）；老《增值税暂行条例》及实施细则是否同时废止、第十五条外币折合规则是否沿用，**未核实**。
- 银行联行号：搜索只找到各地人民银行分行的行号管理办法（行号由参与机构经「行名行号管理系统」申报），**未找到人民银行公开、可下载的官方全量行号表**；第三方 App 非官方。
- 标准规范：全国标准信息公共服务平台（std.samr.gov.cn，国家标准全文公开系统已并入，二手：新华社/证券时报报道）公开国家标准目录与全文；行业标准、地方标准、团体标准不一定公开；版权条款未核实。
- 要求：定时取数作业、对外调用（须先问用户，规则 9）、格式与许可核验、失败告警；官方无 API 的多为网页或文件，需人工审核。
- 代价：自动抓取网页要处理反爬与结构变更（stats.gov.cn 直接抓取返回 403，本研究亲历）；用户语境下对外调用须先批；公开源常无服务级别承诺。
- 收益：数据新；汇率这类每日变化项靠它才准。
- 把握：汇率有明确官方发布方；区划与银行无稳定官方机读源（上述）；税率变化少、人工录入更稳。

### 6.3 待用户决定：「合同与应收是否需要外币」

背景：干系人有「海外业主和开发商」「海外本地持牌伙伴」（DM/architecture/ssot/01-stakeholders/SSOT.yaml:85、:470）；老系统 U8C 币种档案 2 个但无外币业务、无汇兑（U8C_GL_PRACTICE.md:53、:186）；老 BI 有财务手工汇率 CSV 用途不明（BI_SCOPE.md:64）；运营报表里海外业务线「因调整变更为 0」（DM/r0/structure/ops.md:238）。CC-N07 已约定「有外币则新开 ADR」。

| 影响面 | 口径一：合同与应收只用人民币（海外业主按人民币计价结算） | 口径二：允许外币合同与应收 |
|---|---|---|
| Money / 数据模型 | 保持；可在 DDL 加 `CHECK (currency='CNY')` 之类守护 | 需「原币金额 + 币种 + 汇率 + 本位币金额」成对存；SCALE 按币种小数位（CC-N06 已推迟到出现外币）；汇率不进事件、由语义层折算（EAF:73） |
| 核算（权责） | 无汇兑；沿用 GL-124 | 须按记账本位币折算：初始确认用交易日即期汇率或近似汇率，期末货币性项目按资产负债表日即期汇率折算、差额计入当期损益（一手：会计准则第 19 号，转载见 CPA 协会/德勤 PDF）；收入确认、合同资产、应收账龄、减值（ECL）、回款汇兑差均要有汇率；收入确认与过账须保存所用汇率（可追溯，ADR-0016 事实只追加） |
| 参考数据 | 无汇率表 | 需汇率表：来源（如外汇交易中心中间价）、取数日、版本；决定「用哪天的汇率」是会计政策，须财务书面确认（准则只给出选择） |
| 开票 | 人民币开票，无折算 | 增值税销售额须折成人民币（老《实施细则》第十五条：可选销售额发生当天或当月 1 日中间价，一年内不变——二手搜索摘要，且现行条文是否因增值税法施行而变化**未核实**）；开票与回款汇率不同产生差额；零税率/免税等跨境税务处理**未核实**，须交税务确认 |
| U8C 凭证 | 币种仅人民币 | 凭证须带币种与汇率；U8C 需配置外币核算科目与汇率方案（IUFO「汇率维护」仅存在、未使用）；期末汇兑损益结转（现老系统无）；与 ADR-0022 推送口径、GL-124 规则卡须重写 |
| 报表 | 现金流量表「汇率变动影响」行置 0（老无公式） | 该行要计算；报表行定义版本（AGG-86）变化 |
| 总账/期间 | 无 | 期末重估作业入 accounting.period 关账清单；关账后不可反结账（ADR-0022 决定 10、12）→ 须先定重估时点 |
| 阶段影响 | 无 | 触及 W1 过账管道、W3 开票收入、W4 收付、W5 报表；改动面宽，宜在 W1 前决定，晚决则返工过账结构 |

可再细分的中间口径（供用户选）：口径二-轻：外币合同仅「记原币、结算时按实收人民币」，不做期末重估（会计上是否允许取决于准则「近似汇率」与财务制度，须财务确认）。本报告不替用户选。

### 6.4 落点与时点
- A：并入或新建（`masterdata` 命名空间下新 BC，或 `platform.*` 新模块）——归属待 one-architecture；需要前置：税率（W1 起 accounting.tax/开票前）、币种（W1 过账前，实际只有 CNY）、银行（W1 counterparty）。
- 币种汇率：口径一则**不需要**汇率表；口径二则 W1 前必须定。
- 行政区划：若只是「城市类别」则不需要区划表，由 masterdata.catalog 的类别配置承担。

### 6.5 适配结论
- 币种：**现在需要**（ISO 4217 校验，代码层即可，无需模块）。
- 汇率：**不需要**（口径一）/ **现在需要**（口径二）——条件取决于 §6.3 用户决定，且应先核清手工汇率 CSV 用途（CC-N07 R0 项）。
- 税率（法定，13/9/6 等）：**以后需要**——在 accounting.tax/开票前（W3 前）按版本化建；个税/社保等已归 AGG-119，由 organization.payroll 自管。
- 行政区划：**不需要**（现无编码需求，只有城市类别；若未来需要，须用民政部口径，统计局口径已不公开）。
- 银行：**以后需要**（W1 往来单位银行账户若要校验开户行/联行号）；官方机读源未找到，可能只能人工维护或采购。
- 标准规范库：**不需要**作为平台参考数据；若要引用规范目录，随 km.knowledge 做，条目只存编号/名称/状态，全文不入库（版权）。
- 总体：是否建「平台参考数据模块」取决于上表有几项落入「现在/以后需要」：币种、税率、银行三项且多 BC 共用时 A 有收益；仅币种+税率两项则 B 够用。

### 6.6 未核实 / 把握不大
- 统计局「内部资料不公开」一手页已读到，但「自 2024 年 10 月」的时点来自该页转述；民政部官方查询平台入口未能打开核实。
- 外汇交易中心页面的发布时间、历史数据接口、使用条款均未在抓取内容中；PBOC 一手公告页未取。
- 财政部 CAS 19 条文原文未取（二手：德勤、CPA 协会 PDF 摘要）。
- 增值税法条文、《实施条例》对外币折合的规定、增值税暂行条例是否废止：未读到一手全文。
- 联行号官方全量表是否可获取：未找到。
- 手工汇率 CSV 用途：文档未结案。

---

## ⑦a 中文全文检索

### 7a.1 业务需求证据
- 直接检索需求弱：DM 下「全文检索」仅出现在对 ADR 文本的评审步骤（非业务）；业务侧「可检索」出现于：知识资产「版本化、可检索、可评价」（DM/GLOSSARY.md:520）；退役归档「须可检索、有备份、防篡改」（ADR-0054 决定 3-11）；「原编号检索」（DM/REIMAGINED_ARCHITECTURE.md:79，精确查）。
- 排序与拼音：CC-I01「中文排序与检索」，库用内建 C.UTF-8 建库，无 `COLLATE`，人员/部门列表按码位排序；XD-03 已答「按拼音」（DM/CROSS_CUTTING_MATRIX.md:139）。
- 名称选择器类场景（往来单位、项目、人员 335 人、约 1,300 组织人员规模见 ADR-0026:47、367 部门）：规模为千到万行，非海量。
- 老系统登录按 username 精确查、无模糊（DM/process/1a/登录与会话.md:81）。
- 知识条目（AGG-107）与证据摘要（AGG-38）会是文本检索的主要对象；老系统无对应能力的使用数据（未取证，无「搜索」使用量）。

结论：需求形态是 ① 名称/编号的包含匹配与拼音首字母（选择器）② 知识条目正文的关键词检索（km.knowledge W1 薄版）③ 归档的元数据检索。无海量日志类检索（API 日志进 SLS，ADR-0019）。

### 7a.2 可选做法

**A　PostgreSQL 内置全文检索 + 中文分词扩展（zhparser 或 pg_jieba）**
- 事实：阿里云 RDS PostgreSQL 官方扩展表 PG 18 标准版 zhparser 2.1、pg_jieba 1.2.0；倚天版同；二者须预加载 `shared_preload_libraries` 否则创建失败（一手：help.aliyun.com 扩展列表，中、英文两版各读一次，值一致）。zhparser 依赖 SCWS 词典，2.1 起自定义词存于库内 `zhparser.zhprs_custom_word` 表（需超级用户，改后须重连，一手：GitHub README）；pg_jieba 上游 README 只写「在 PG 9.6 与 11 测试」，BSD-3，依赖 cppjieba（一手：GitHub，**上游对 PG 18 无声明，阿里云列 1.2.0 支持是其自己的发布**）。PG 内置全文检索架构（parser/dictionary/configuration）见官方文档，官方页未讨论中文分词。
- 要求：改参数组（预加载，需重启，生产窗口）；dev/测试容器需带同扩展——**测试用 Testcontainers 镜像为官方 postgres 镜像，不含这些扩展**，要新镜像并登记 `backend/architecture/test-images.txt`（规则 9）；Flyway 迁移里 `CREATE EXTENSION` 需 RDS 有权限（RDS 高权限账号）。
- 代价：开发小到中（`tsvector` 生成列 + GIN 索引 + 查询工具）；费用 0；运维：扩展随内核小版本升降；分词词典维护（专业词：幕墙、龙骨、铝单板、检测规范号等需自定义词）；安全面：扩展为 C 代码，在数据库进程内，供应商维护。
- 收益：事务一致（索引随提交）、无同步链路、权限复用（RLS/范围谓词同库）；契合 ADR-0002 「不增组件」。
- 把握：官方列表一致；**实际中文分词质量、专业术语召回、与 C.UTF-8 排序规则及 Flowable/迁移的兼容未实测**。

**B　pg_trgm 模糊匹配**
- 事实：阿里云 PG 18 支持 pg_trgm 1.6，无需预加载（同上一手）。官方 pg_trgm 文档：忽略非字母数字字符提取三元组；GIN/GiST 索引支持 `%` 相似、`LIKE '%x%'`、`ILIKE`、正则；无可提取三元组的模式退化为全索引扫描；文档**未讨论多字节/CJK 与 locale**（一手：postgresql.org/docs/18/pgtrgm.html）。故中文能否产出三元组取决于库的字符分类，本库用内建 C.UTF-8（CC-I01）——**须实测**，此处不下结论。
- 要求：建库 locale 与排序规则确认；列上建 GIN 索引。
- 代价：开发小；费用 0；无扩展预加载，无重启；无词典维护；对短名称（往来单位、项目）的「包含」匹配够用；对「按语义/关键词检索长文本」召回差（无分词、无排序相关度）。
- 收益：最简，适合选择器；拼音首字母需另存拼音列（另一个字段，由应用生成，选型另议）。
- 把握：对 ASCII 把握高；对中文需实测。

**C　独立检索服务（阿里云 OpenSearch / Elasticsearch）**
- 事实：阿里云「OpenSearch」是一族产品（行业算法版、高性能搜索版、向量检索版、检索引擎版（经典版已停）、LLM 智能问答版，另有 AI 搜索开放平台）；「阿里云 Elasticsearch」是另一产品，计费页目录含包年包月/按量付费、按节点与存储计费（一手：官方帮助中心概览页；**具体单价与最小节点数本次未取到**）。
- 要求：数据同步链路（CDC/事件）、独立权限模型、对外网络与 KMS；新增组件违反 ADR-0002 取向（「每多一个组件多一份运维与安全面」）。
- 代价：开发中到大；费用：持续（节点/存储，量级未核实）；运维：同步延迟与对账；安全面：全文副本含敏感信息（薪酬、个人信息 ADR-0043）须重做授权，RLS 不覆盖。
- 收益：分词/相关度/高亮/同义词成熟；适合大量非结构化文档。
- 把握：本系统规模下收益低，评审结论 C-9 同类思路「同库更简单」（ADR-0025 备选「一开始就上独立分析实例」被否）。

**D　不做**
- 代价：选择器只能前缀/精确，用户体验差；知识库(km.knowledge)失去「可检索」声明（GLOSSARY.md:520）。可用 `LIKE`/btree 满足小表（`B/kernel/SmallTable.java` 的小表不分页上限约定，小表本就要走全量列表）。

### 7a.3 落点与时点
- 非独立平台模块：检索是各 BC 查询侧能力，工具类（分词配置、`tsvector` 生成、拼音列）可放 platform 共用小工具或 CODING_STANDARD 约定。km.knowledge 在 W1（ADR-0054）。若选 A，扩展装载与预加载是**基础设施前置项**（RDS 参数组、测试镜像），需在 W0/W1 前完成，否则 Flyway 迁移无法建扩展。
- 选择器模糊匹配在 W1 往来单位。

### 7a.4 适配结论
**以后需要**（W1 km.knowledge/往来单位前定型）。理由：业务需求以「名称包含匹配 + 小规模知识正文检索」为主；B 或 D 即可覆盖选择器；知识正文检索若要中文关键词召回，A 是唯一在同库内的选项；C 与本系统规模、ADR-0002 取向不匹配。
- 条件式：若 km.knowledge 正文检索确为 W1 交付 → 需 A（并前置参数组与测试镜像）；仅选择器 → B（须先实测中文）；知识库推迟 → D。

### 7a.5 未核实
- zhparser/pg_jieba 在阿里云 PG 18 的**运行时稳定性与内核小版本要求**：官方页未写限制；上游 pg_jieba 仅声明 PG ≤11 测试。
- pg_trgm 在本库 C.UTF-8 下对中文的行为：官方文档沉默，须实测。可行方式（未执行）：在 dev 库或 Testcontainers 里 `SELECT show_trgm('幕墙检测')`，并对比 zh-x-icu 库。
- OpenSearch/Elasticsearch 价格数字、最小节点数：未取到。
- 老系统搜索实际使用数据：未取证。

---

## ⑦b 现场采集（照片、定位、离线）

### 7b.1 业务需求证据
- 检查/检测的证据：「证据（照片、视频、测量/检测数据、现场记录、签认）带采集时间、地点、采集人与完整性校验，进入后不可修改」；证据最小内容含「地点（受检部位，可带坐标）、采集设备或来源、文件摘要（哈希）」；发布前至少一条证据，〔沪TIS〕第 12 条第（九）项把未上传照片、视频与说明列为失信（DM/architecture/proposals/AEC_OBJECTS_INSPECTION_EVIDENCE.md:53、:156、:190-204）；计次检查每次记录「时间、地点、人员、照片」（Q04_AEC_CONTRACT_MODELS.md:325）。证据是一级对象 OBJ-53、AGG-38，位于 `delivery.acceptance`（B-28，ENTERPRISE_AGGREGATES_DRAFT）；检查在 `delivery.inspection`（B-26，W5）。
- 驻场：设计师和顾问含驻场（SSOT）；外勤「外出到工地、客户现场」（PPL_CHANGES.md:83）；考勤打卡走企业微信，来源设备对接（OA_PROCESS_INVENTORY.md:379；PPL_TRACE.md:130）；A8 外勤申请档案 32,385 条、原始打卡 794,221 行（OA_PROCESS_INVENTORY.md:39）。
- 证据档案 10 年 WORM 保留（AEC_OBJECTS_INSPECTION_EVIDENCE.md:190，引 XD-04）；对象存储：`B/platform/integration/domain/ObjectStore.java`（写一次、带 sha256、不覆盖）、阿里云 OSS（ADR-0019:28）。
- 没有找到：离线需求、弱网、GPS 精度要求、水印要求的文字。检查在工地、地下室、幕墙外立面等弱网环境的风险是推演，**无文档证据**。

各模块各自做的代价：若 inspection、acceptance、attendance（外勤）、expense（报销发票拍照）各接一套企业微信 JS-SDK 签名、上传、转存、哈希，则重复：签名服务、媒体下载（media_id 仅 3 天有效）、OSS 转存与摘要、EXIF/坐标处理、权限。

### 7b.2 可选做法

**A　企业微信 JS-SDK（H5 内调用）**
- 事实（一手，企业微信开发者文档，逐页读取）：
  - 新版 wecom-jssdk 用 `ww.register` 注册企业身份与应用身份；签名在服务端用企业 / 应用 jsapi_ticket 生成；接口只能在**应用可信域名**下调用，域名须 ICP 备案并验证（path/90514）。「应用身份权限 > 企业身份权限」，各接口是否需 agentConfig 须看接口页或 `checkJsApi`（已读页未写明）。
  - `chooseImage`：`count` 默认 9（页面未写上限）、`sizeType` original/compressed、`sourceType` album/camera；返回 `localIds`；支持企业微信 iOS/Android 与微信；Android 的 localId 可直接作 img src，**iOS 须经 `getLocalImgData` 取 base64**；`isSaveToAlbum` 需企业微信 2.7.5+（path/100520）。
  - `uploadImage`：上传后 `serverId`（即 media_id），**有效期 3 天**，由后端用「获取临时素材」下载（path/100522、90254）；单次下载按块 ≤20MB（Range）；图片大小/数量限制本次未取到。
  - `getLocation`：type `wgs84`/`gcj02`；返回经纬度、`accuracy`（无单位说明）；`speed` 恒 0；页面未写权限要求与精度细节（path/100578）。
  - 页面均未提供「水印」「拍摄时间」「防篡改」选项。
- 要求：H5 部署在可信域名下（已有：one.forcitis.com，B-4 已验证）、后端签名接口（需 jsapi_ticket 缓存，Caffeine 够）、后端定时/即时转存媒体到 OSS 并计算 sha256、把坐标与 `serverId` 绑定。
- 代价：开发中；费用：仅 OSS 存储；运维：企业微信版本分布（部分参数有最低版本）；安全面：坐标与时间由客户端报告，**可被篡改**，不能当不可抵赖证据，需服务端时间戳与摘要（照片本身无可信时间/位置来源，WeCom 官方文档未提供拍摄签名）；个人位置属敏感个人信息（PIPL，DM/FRAMEWORKS.md:192 对金融账户另有说明，位置信息需处理者评估，ADR-0043 R 系列）。
- 收益：与企业微信同生态、免用户安装、拍照/定位体验一致；已有登录与可信域名。
- 把握：接口名与行为已读一手页，较高；iOS/Android 差异、实际在地下室的定位成功率、`getLocation` 在企业微信里的授权弹窗行为未实测。

**B　Taro 小程序能力（Taro 4 编译为小程序或 H5）**
- 事实：栈规定为 Taro 4 + React 作 H5（ADR-0013，企业微信内嵌）。Taro 文档（`Taro.chooseImage`、`Taro.getLocation`）两次抓取均失败（连接被拒/只返回标题），**H5 端 Taro API 实现方式、小程序端权限未核实**。MDN：浏览器 Geolocation 须 HTTPS 安全上下文、需用户授权、授权有效期由浏览器决定；**在中国大陆原生 API 的定位可能不可用（依赖 Google 的 Wi-Fi 定位），建议用百度/高德/腾讯等**（一手 MDN，Geolocation API 页）。
- 要求：若 H5 内用 `navigator.geolocation` 与 `<input type=file capture>`，不依赖企业微信接口；若另做企业微信小程序/微信小程序，须独立应用、发布审核（本报告未核实企业微信小程序承载内部应用的政策）。
- 代价：H5 自带：开发小，但定位可靠性与相机体验由 WebView 决定；小程序：新一套发布、审核、域名、账号配置，开发大。
- 收益：H5 路线与「共用 OpenAPI 客户端」同栈；小程序路线原生体验更好。
- 把握：低到中（Taro 文档未取到；大陆定位问题来自 MDN 但未在企业微信 WebView 实测）。

**C　离线缓存方案及其代价**
- 常见路径：IndexedDB 暂存（草稿、照片 blob、待传队列）+ 恢复网络后重传；Service Worker + Background Sync；Workbox。
- 事实：本次**未能核实** iOS WKWebView（企业微信 iOS 内置浏览器）对 Service Worker 与 IndexedDB 持久化的支持与驱逐规则；WebKit 官方 App-Bound Domains 博文只讲 JS 注入/cookie 限制，未提 Service Worker。企业微信 JS-SDK 页面未提离线。
- 要求：客户端队列、幂等重传（`platform.idempotency` 已有，ADR 幂等键）、冲突策略（证据只追加，天然适合：同一证据重复上传以 sha256 去重）、容量与清理、拍摄时间的可信记录（离线期间时间戳是设备时钟，不可信）。
- 代价：开发大（队列、重试、状态同步、冲突、测试矩阵含 iOS/Android 企业微信版本）；费用无；运维：客户端问题难复现；安全面：设备上暂存未加密的敏感照片/坐标，设备丢失风险。
- 收益：地下室/幕墙外立面等弱网现场的采集不丢；
- 把握：低（需求本身无文档证据；平台能力未核实）。

### 7b.3 落点与时点
- 采集入口的共用部分（签名服务、媒体转存、摘要、OSS 写入）：并入 `platform.integration`（已有 WeCom 网关、ObjectStore、出站投递），对业务 BC 暴露 `api`（上传令牌/媒体引用）；证据对象本身留 `delivery.acceptance`（AGG-38），检查记录在 `delivery.inspection`。
- 最早需求在 W2（delivery.acceptance 证据、驻场记录），主需求 W5（inspection）。离线若做，须在 W2 前定 H5 架构（队列/存储层），否则返工。

### 7b.4 适配结论
- 拍照与定位（企业微信 JS-SDK，A）：**以后需要**（W2 前）——证据对象明确要采集时间、地点、人与摘要，且证据是检查/认可的硬拦截（发布前至少一条）；但「采集」可先以文件上传加手填部位满足，JS-SDK 在 W2 交付。
- Taro 小程序独立发布（B 中小程序路线）：**不需要**（无文档证据，且新增发布面）。
- 离线缓存（C）：**以后需要？（条件式）**——无弱网需求证据，建议在 W2 前向项目经理/检查员取证「工地网络情况与是否需无网采集」；有强需求再定，否则不做。
- 总体：⑦b 不是 W0/W1 阻塞项。

### 7b.5 未核实 / 把握不大
- 企业微信 `chooseImage` 的 count 上限、`uploadImage` 的单张大小与频率限制、各接口是否需 `agentConfig`：官方页未写明，须逐页补读或实测。
- 企业微信应用的「地理位置」权限与用户授权流程；iOS/Android 室内/地下定位成功率。
- Taro 4 H5 端 API 实现；Taro 文档未取到。
- iOS WKWebView 下 Service Worker/IndexedDB 行为；企业微信内置 WebView 内核版本。
- 证据法层面「电子数据」可采信要求（客户端时间/坐标可篡改，服务端摘要+时间戳是否足够）：仓库引〔检测办法〕等，本报告未核法条，须交法务/one-sec。
- 位置与照片属个人信息评估范围（ADR-0043），须一并评估（未做）。

---

## 附：来源清单

一手（官方文档/法规/发布方）
- 阿里云帮助中心「RDS PostgreSQL 支持的扩展」（中文 help.aliyun.com/zh/rds/apsaradb-rds-for-postgresql/extensions-supported-by-apsaradb-rds-for-postgresql；英文 help.aliyun.com/en/...同名页）——两次读取插件版本值一致
- PostgreSQL 18 文档：pg_trgm（postgresql.org/docs/18/pgtrgm.html）、全文检索概述（.../textsearch-intro.html）
- GitHub amutu/zhparser、jaiminpan/pg_jieba README（开源项目自述）
- Flowable 官方文档 BPMN Constructs、Advanced（flowable.com/open-source/docs/bpmn/...）
- 企业微信开发者文档：path/90514、100520、100522、100578、90254、90236（发送应用消息）
- MDN Geolocation API；WebKit 博客 App-Bound Domains
- 国家统计局咨询答复 stats.gov.cn/hd/lyzx/zxgk/202509/t20250903_1960996.html；统计用区划代码编制规则页（stats.gov.cn/zt_18555/.../t20230306_1935317.htm）
- 新华社：民政部《行政区划代码管理办法》（news.cn/20250707/6f736052004d482eaf004f559fecf7df/c.html）
- 中国外汇交易中心「人民币汇率中间价」页（chinamoney.com.cn/chinese/bkccpr/）
- 阿里云 OpenSearch 帮助中心首页（alibabacloud.com/help/en/open-search/）
- 财政部/税务总局/海关总署 2019 年第 39 号公告（gov.cn/gongbao/content/2019/content_5416183.htm，仅搜索结果确认存在）

二手或仅搜索摘要（需复核）
- CAS 19 应用指南与条文（德勤、CPA 协会 PDF 转载）；增值税法税率与施行日期（央视/人民网/中国新闻网报道）；增值税暂行条例实施细则第十五条（法规汇编站摘要）；人民银行分行行号管理办法（PBC 分行文件，搜索摘要）；标准公开平台并入与数量（新华社、证券时报报道）；阿里云 Elasticsearch 计费页（只读到目录）。
