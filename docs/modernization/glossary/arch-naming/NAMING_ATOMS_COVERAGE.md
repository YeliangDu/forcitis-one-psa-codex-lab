# 命名元词语覆盖盘点第二轮（N8R）

日期：2026-10-17；独立 worktree，基线 `HEAD b99f7aa9`。只改本卡报告、题面与 coverage 脚本；未提交、未推送、未联网、未起容器、未实际登记。

## 一页摘要

**上轮十件事分流为设计方定7件、用户定3件；NM5只留3题业务含义初稿。** 已有决定直接沿用。下面按不同词形去重，替换上轮把正文和词次混在一起的粗数；每类的前20个完整例子在后文。

“真缺词”指在本卡白名单载体中确有出处、且现有登记或辅助许可没有覆盖的不同词形。事件体标记计待结构登记的受控形态；界面类计不同完整用语/模板的消息目录缺项，**这两类不是新增业务概念数**。同类内跨文件去重；中文/英文没有经确认的不擅自合为一个概念。类别可交叉，禁止相加当企业缺概念总数。

多义数=仍需消歧的不同词形数（含跨BC候选及登记重复，未全部证明业务异义）；方言数=已有forbidden/dialect/undetermined及N4旧叫法线索的不同词形数，方言是交叉列，不等于违规处数。弃用桥接不再制造假多义。

| 每类 | 真缺词数 | 多义数 | 方言数 | 一句话结论 |
| --- | --- | --- | --- | --- |
| 动词 | 100 | 39 | 52 | 业务新动作与读写技术动作分开初裁；NM1已定中文不重问。 |
| 名词·对象 | 172 | 5 | 19 | 完整对象槽和名词登记缺口；反解所得对象仍需核对定义。 |
| 名词·单据 | 11 | 2 | 1 | 按具体申请、单据登记；上位词不替代具体对象。 |
| 名词·角色 | 2 | 0 | 3 | 区分角色完整名与负责人等通用职责；财务负责人范围另见NM5。 |
| 名词·参与方 | 4 | 0 | 0 | 法人、供方等实际词形需对照首选名；客户已弃用桥接不算新多义。 |
| 状态词 | 39 | 2 | 3 | 这里只算词汇化取值的词缺口；可组合取值另补槽位。 |
| 限定词 | 7 | 0 | 0 | 设受控修饰源；已由辅助清单或规范允许的词不重复报缺。 |
| 时间与期间词 | 4 | 1 | 0 | 日期、时刻、期间和有效区间按现行规则分开。 |
| 数量与金额词 | 6 | 0 | 4 | 量值需有具体对象和单位；基数含义留NM5并经财务。 |
| 单位 | 1 | 0 | 0 | 只保留实际单位词；日历日期不当时长单位。 |
| 结果与原因词 | 9 | 1 | 0 | 原因与结论不自动变成主状态或通用动作。 |
| 事件后缀与体标记 | 57 | 0 | 0 | 已有N4文本词形，缺结构登记；不是新增业务动词或Event后缀。 |
| 界面与消息固定用语 | 318 | 0 | 0 | 完整固定用语缺消息目录登记；不能换算成业务概念数。 |
| 业务线与专业分类 | 1 | 0 | 0 | 服务产品沿既定主数据地位补登记；专业含义另问。 |
| 级别与分层 | 0 | 0 | 0 | 本范围无真缺词；不表示所有业务等级值已收齐。 |
| 方向词 | 4 | 0 | 0 | 转入转出、目标、出站按各自关系值域登记。 |
| 否定与模态词 | 2 | 0 | 0 | 否定与限制保留完整词，不统计不、可等单字残片。 |
| 表示与技术类别词 | 3 | 0 | 0 | 规范已有类别词直接沿用；缺项补技术结构。 |
| 目的与关系连接词 | 3 | 0 | 0 | 区分业务关系与模板连接词，不能改变事务边界。 |
| 命名空间与系统名 | 0 | 0 | 0 | 本卡只看路径/权限中的结构词；不把系统、模块等泛称报缺。 |
| 叙述文字（不计） | 不计 | 不计 | 不计 | ADR、文档叙述、代码/SQL注释、流程说明均不进入缺词候选。 |

下一步：设计方按[登记方案](NAMING_ATOMS_REGISTRATION_PLANS.md)补结构；业务 Admin 经 one-coordinator 审[本地NM5题面](../../rules/decide/sheets/NM5.json)。本轮没有替用户提交或发布确认页。

## 去噪口径与完整性边界

1. 只计生产代码的类型/方法/函数/枚举常量、迁移里的表与列、接口路径和字段、权限码、界面完整文案，以及三组骨架中的命令/事件/状态/用户任务名。record只取api/web/events中的接口组件；不把全部字段、参数、局部变量都算进去。接口静态契约读取本地openapi.json，不访问运行系统。
2. 不计ADR、架构说明、注释、测试/测试显示名、脚本/runbook、文件名、包名、配置/指标/日志键、设计令牌、约束/索引名，也不把骨架params、authority、actor本身当命名候选。NM5引用其中部分作为业务理解证据，不因此把它们加回统计。
3. 声明和字面量分开提取，COMMENT ON叙述剔除；骨架说明字段单列。字段内长叙述/表达式保留排除原因；括注去说明，原文仍留original。`人员:已离职`分开集合与取值。没有把一个裸的不、可、或、因切成新词。
4. common_words、standard_actions、bcs.words及现有允许清单只证明相应词形已获辅助许可，不是完整概念或组合结构已登记。规范明列的number等表示词、受控词的规则复数不重报；N4已定义的可组合状态不重复统计为新状态词。消息和体标记的结构缺口单列本类，不能用辅助放行抹掉。
5. 未知片段留在entries.unmatched，不把残片摊入某类真缺词。对象槽只收完整可读候选，排除末尾动作、明显并列或表达式；名称反解仍只是迁移工具，不能证明对象主权或语义正确。保留单独的待核分布，未做到全仓每个残片人工词性裁定。

本轮提取 **11,383处命名载体**，输入/脚本指纹 **1,126个文件**；另保存3,049处骨架说明/排除记录。这是读取与提取数，不是命名合规率。各载体分布如下。

| 载体 | 出处数 |
| --- | --- |
| Permission码 | 68 |
| Web完整路径静态组合 | 68 |
| Web接口方法 | 68 |
| Web接口路径 | 74 |
| 代码函数名 | 28 |
| 代码契约类型名 | 83 |
| 代码方法名 | 2786 |
| 代码枚举常量 | 501 |
| 代码类型名 | 1064 |
| 代码类型或函数名 | 344 |
| 列名 | 529 |
| 接口字段 | 1201 |
| 接口路径 | 58 |
| 流程事件 | 1028 |
| 流程命令 | 1921 |
| 流程状态 | 628 |
| 流程用户任务名 | 460 |
| 界面文案 | 425 |
| 表名 | 49 |

运行时拼接、实际库元数据、线上消息目录、动态接口字段、非record手写序列化字段未穷举；Java/TS使用有边界的声明提取，不是完整AST。空的级别/命名空间缺词数只能说明这个白名单与词形对照里未检出，不能声称该面全覆盖。

## 十件事逐件分流：7件设计方定，3件用户定

设计方的“定”是本卡可授权作出的结构、归类与迁移规则；不会冒充业务批准或直接改正式词表。同一事项中纯技术部分已在结论中直接定下，只有残余业务含义交用户。

| 原序 | 事项 | 分流 | 本轮决定/保留问题 | 理由与条款 |
| --- | --- | --- | --- | --- |
| 1 | 客户同名与组合引用 | 设计方定 | 采用 customer_role；customer 已 deprecated 且 replaced_by=customer_role，不再算两个现行语义。保留旧 id 与桥接，不凭同名合并其他概念。 | ADR-0053决定1第1条、决定2第1条、决定5第3条；intent 2026-10-15-012200：共用往来单位身份、角色分开；terms 的两个 id 已核实。 |
| 2 | 上位名词和具体对象的粒度 | 设计方定 | 被独立引用的对象先核定义再登记；具体概念按槽位引用，不将任意长短语或字符串残片补成新概念。证据及证据关联已有决定直接沿用。 | ADR-0053决定1第3条、决定2第1条、决定3；NAMING_STANDARD §2.1、§2.2；N5已答N5-033/034。 |
| 3 | 财务负责人等职责的称呼与范围 | 用户定：NM5-01 | 称呼沿已有决定；只问公司级还是各法人职责。SH权责索引、实例表达式不是角色名，由设计方直接排除。 | ADR-0053决定7第1条；intent 2026-10-16-164324已定加审，未明确本题范围。 |
| 4 | 状态取值、集合、能力限制 | 设计方定 | 概念分类与取值分开；集合引用值域，能力作正交条件；不把每个事件变成状态。已/待/中按模板，草稿等逐条登记；可结算与审批进度分开已定。 | ADR-0053决定2第1条、决定3；NAMING_STANDARD §2.2、§2.4；N4 §3.3、NM1-17/18/19直接沿用。 |
| 5 | 限定词是否受控、计划量与计划对象 | 设计方定 | 限定词受控，声明修饰对象；计划对象、Plan动作与计划量修饰分开。代码片段不自行扩充业务含义。 | ADR-0053决定3、决定4、决定7第2条；NAMING_STANDARD §2.4。 |
| 6 | 期间、年度、业务日、时刻和生效区间 | 设计方定 | 业务日date、时刻UTC、区间与期间分开；时间单位不替代日期概念。会计年度/期间按既有定义补引用，不重新选择会计政策。 | NAMING_STANDARD §2.4、§2.6 A-7；DATA_DESIGN_CHECKLIST §14.1 D6/D15/D17；ADR-0053决定2/3。 |
| 7 | 量、金额、费率、基数及单位 | 用户定：NM5-02 | 设计方已定量值+单位/币种、不同量概念分立；三套费率及系数维度已定。只追问分摊基数的含义，具体定义经财务。 | NAMING_STANDARD §2.3/§2.4/§2.6 A-7；ADR-0053决定7第1条；intent 2026-10-15-211139、2026-10-16-134134。 |
| 8 | 结果、原因、否定和模态结构 | 设计方定 | 结果/原因/能力与生命周期分开；未发生与已否定分别定义；同形不同义先标待确认，不默认合并，也不新增万能失败动词。 | ADR-0053决定2第1条、决定3、决定5第2条；NAMING_STANDARD §2.2、G29；NM1已定否定结果直接沿用。 |
| 9 | 业务线、专业、等级与方向的值域 | 用户定：NM5-03 | 值域分立及不混作生命周期由设计方定；业务线定义、服务产品主数据地位直接沿用。只问团队字段专业是人员专业还是本次服务类别。 | ADR-0053决定2第1条、决定7第1条；terms business_line；intent 2026-10-15-215632、2026-10-16-061232。 |
| 10 | 语法、技术词、命名空间、固定文案归谁管 | 设计方定 | 业务指称归glossary并入源；bcs沿既定源；语法/技术格式归模板；完整文字归消息目录、名词用占位符，具体方案见登记方案。 | ADR-0053决定1第3条、决定3、决定4、决定6第3/4条、决定7第2条；NAMING_STANDARD唯一入口。 |

NM5-01/02/03分别对应原第3/7/9项。题中写出推荐、依据与证据不足，未把推荐当已定。职责的授权、三套费率用途、计费系数维度、服务产品主数据地位均引用已有决定；没有为凑题数重新提问。

出题前使用本地现有生成器把498条intent读成 `/tmp/n8r-decision-digest.md`，再回读相关原话；没有生成或改动卡外DECISION_DIGEST.md。题面中所引intent须经check_pages核对origin/main可见文件；这仍不代表已经获得新答复。

## 各类真缺词前20例与出处

排序：按不同出处数降序，再按词形排序。同一词只列一次；不足20个列全，不补造例子。完整载体保持原文，不截断成残片；表中词形只是检索词。每个词的全部出处、登记候选、待核与方言对照在JSON records中。

### 动词

真缺词100；多义39；方言52。业务新动作与读写技术动作分开初裁；NM1已定中文不重问。

| 不同词形 | 完整载体原文 | 出处文件与行 | 结构字段/声明 |
| --- | --- | --- | --- |
| 生成 | 已生成 | `.codex-inputs/skeletons/finance-state-machines.yaml:125` | machines[3].states[0] |
| 推送 | 待推送 | `.codex-inputs/skeletons/finance-processes.finance.yaml:7084` | processes[37].states[1] |
| 结清 | 已结清 | `.codex-inputs/skeletons/finance-state-machines.yaml:174` | machines[4].states[2] |
| 核定 | 已核定 | `.codex-inputs/skeletons/finance-state-machines.yaml:748` | machines[12].states[1] |
| 草拟 | 草拟 | `.codex-inputs/skeletons/finance-state-machines.yaml:525` | machines[7].states[0] |
| 试算 | 已试算 | `.codex-inputs/skeletons/finance-state-machines.yaml:52` | machines[1].states[0] |
| 认可 | 已认可 | `.codex-inputs/skeletons/revenue-state-machines.yaml:2185` | machines[41].states[6] |
| 签字 | 签字通过 | `.codex-inputs/skeletons/finance-state-machines.yaml:1137` | machines[24].transitions[3].on_event |
| 签订 | 签订劳动合同 | `.codex-inputs/skeletons/people-commands.yaml:1321` | commands[43].name |
| replay | replay | `backend/src/main/java/com/forcitis/one/platform/approval/infrastructure/replay/TimesheetApproverReplay.java:26` | method |
| 冲回 | 冲回应付核销 | `.codex-inputs/skeletons/finance-commands.yaml:289` | commands[21].name |
| 决议 | 已决议 | `.codex-inputs/skeletons/finance-state-machines.yaml:526` | machines[7].states[1] |
| 减员 | 已减员 | `.codex-inputs/skeletons/people-state-machines.yaml:1140` | machines[19].states[1].name |
| 支付 | 已支付 | `.codex-inputs/skeletons/finance-state-machines.yaml:528` | machines[7].states[3].name |
| 调拨 | 调拨资产 | `.codex-inputs/skeletons/people-commands.yaml:4639` | commands[160].name |
| set | setDraft | `frontend/apps/web/src/modules/numbering/pages/numbering-rules-page.tsx:81` | arrow |
| 增员 | 参保增员 | `.codex-inputs/skeletons/people-commands.yaml:2610` | commands[87].name |
| 定稿 | 经营复盘已定稿 | `.codex-inputs/skeletons/people-commands.yaml:3625` | commands[121].emits[0] |
| 实缴 | 部分实缴 | `.codex-inputs/skeletons/finance-processes.finance.yaml:6240` | processes[31].states[1] |
| 废止 | 已废止 | `.codex-inputs/skeletons/people-state-machines.yaml:1753` | machines[28].states[5].name |

多义/方言不同词形（完整对照见JSON）：accept〔多义〕；accepted〔多义〕；allocate〔多义〕；applied〔多义〕；apply〔多义〕；close〔多义〕；closed〔多义〕；collect〔多义〕；collected〔多义〕；declare〔方言线索〕；disable〔方言线索〕；disabled〔方言线索〕；employed〔多义〕；enable〔方言线索〕；enabled〔方言线索〕；execute〔多义〕；expire〔方言线索〕；fetch〔方言线索〕；find〔方言线索〕；issue〔多义〕；issued〔多义〕；locked〔方言线索〕；login〔方言线索〕；maintain〔方言线索〕；maintained〔方言线索〕；open〔多义〕〔方言线索〕；opened〔多义〕；process〔多义〕；read〔方言线索〕；record〔方言线索〕；reject〔多义〕；rejected〔多义〕；release〔多义〕〔方言线索〕；released〔多义〕；request〔方言线索〕；restore〔多义〕；return〔多义〕；reviewed〔方言线索〕；revoke〔多义〕；run〔方言线索〕；separated〔多义〕；sync〔方言线索〕；transfer〔多义〕；transferred〔多义〕；update〔多义〕〔方言线索〕；updated〔方言线索〕；停用〔方言线索〕；关闭〔多义〕；内审〔方言线索〕；分配 指派〔多义〕〔方言线索〕；取消〔多义〕；取消认领〔方言线索〕；叫停〔多义〕〔方言线索〕；启用〔方言线索〕；填报〔方言线索〕；复核〔方言线索〕；建立〔方言线索〕；开展〔方言线索〕；开票〔方言线索〕；拒〔多义〕；挂起〔方言线索〕；提交〔方言线索〕；撤销〔多义〕；新建〔方言线索〕；更新〔方言线索〕；更正〔方言线索〕；核准〔方言线索〕；核销〔多义〕；确认〔多义〕；离职〔多义〕；签发〔方言线索〕；签认〔方言线索〕；管理〔方言线索〕；结项〔方言线索〕；维护〔方言线索〕；缴〔方言线索〕；解除证据关联〔方言线索〕；认定〔多义〕；记录〔方言线索〕；设立〔方言线索〕；评审〔方言线索〕；起草〔方言线索〕；退〔多义〕；递交〔方言线索〕；通过〔多义〕〔方言线索〕。

### 名词·对象

真缺词172；多义5；方言19。完整对象槽和名词登记缺口；反解所得对象仍需核对定义。

| 不同词形 | 完整载体原文 | 出处文件与行 | 结构字段/声明 |
| --- | --- | --- | --- |
| 证据 | 采集证据 | `.codex-inputs/skeletons/revenue-commands.yaml:433` | commands[33].name |
| 客户活动 | 计划客户活动 | `.codex-inputs/skeletons/revenue-commands.yaml:2467` | commands[183].name |
| 条目 | 创建知识条目 | `.codex-inputs/skeletons/people-commands.yaml:3855` | commands[131].name |
| 保证金 | 保证金已缴 | `.codex-inputs/skeletons/revenue-commands.yaml:1604` | commands[116].emits[0] |
| 知识条目 | 创建知识条目 | `.codex-inputs/skeletons/people-commands.yaml:3855` | commands[131].name |
| 线索 | 登记线索 | `.codex-inputs/skeletons/revenue-commands.yaml:2098` | commands[155].name |
| 申报 | 提交申报 | `.codex-inputs/skeletons/finance-commands.yaml:1443` | commands[107].name |
| 请假 | 提交请假 | `.codex-inputs/skeletons/people-commands.yaml:1477` | commands[48].name |
| it 需求 | 提交 IT 需求 | `.codex-inputs/skeletons/people-commands.yaml:4215` | commands[145].name |
| 市场活动 | 计划市场活动 | `.codex-inputs/skeletons/revenue-commands.yaml:2067` | commands[152].name |
| 薪酬档案 | 建立薪酬档案 | `.codex-inputs/skeletons/people-commands.yaml:2965` | commands[98].name |
| 资源 | 提出资源需求 | `.codex-inputs/skeletons/revenue-commands.yaml:2797` | commands[208].name |
| 报价版本 | 编制报价版本 | `.codex-inputs/skeletons/revenue-commands.yaml:2372` | commands[176].name |
| 出资实缴 | 登记出资实缴 | `.codex-inputs/skeletons/finance-commands.yaml:412` | commands[30].name |
| 阶段 | 开始阶段 | `.codex-inputs/skeletons/revenue-commands.yaml:2636` | commands[196].name |
| 后继版本 | 后继版本已生效 | `.codex-inputs/skeletons/finance-processes.finance.yaml:7672` | processes[42].transitions[2].on_event |
| 服务产品版本 | 发布服务产品版本 | `.codex-inputs/skeletons/revenue-commands.yaml:1915` | commands[141].name |
| 资产 | 处置资产 | `.codex-inputs/skeletons/finance-commands.yaml:1520` | commands[113].name |
| 资金账户 | 开立资金账户 | `.codex-inputs/skeletons/finance-commands.yaml:1232` | commands[91].name |
| 项目复盘 | 编制项目复盘 | `.codex-inputs/skeletons/revenue-commands.yaml:3137` | commands[235].name |

多义/方言不同词形（完整对照见JSON）：ar〔方言线索〕；employed〔多义〕；h5〔方言线索〕；separated〔多义〕；wip〔方言线索〕；业务摘要〔方言线索〕；产值〔方言线索〕；会计期间〔多义〕；决定〔方言线索〕；合同履约成本〔方言线索〕；坏账〔方言线索〕；工时审批〔方言线索〕；已升级〔方言线索〕；核销占用〔多义〕；离职〔多义〕；签证〔方言线索〕；管理模式〔方言线索〕；红冲〔方言线索〕；考勤〔方言线索〕；范围待复核〔方言线索〕；行〔方言线索〕；规则版本〔方言线索〕；轮次〔方言线索〕；预收〔方言线索〕。

### 名词·单据

真缺词11；多义2；方言1。按具体申请、单据登记；上位词不替代具体对象。

| 不同词形 | 完整载体原文 | 出处文件与行 | 结构字段/声明 |
| --- | --- | --- | --- |
| 劳动合同 | 提交劳动合同 | `.codex-inputs/skeletons/people-commands.yaml:1228` | commands[40].name |
| 账单 | 处理账单差异 | `.codex-inputs/skeletons/finance-commands.yaml:2102` | commands[155].name |
| 凭证 | 制单手工凭证 | `.codex-inputs/skeletons/finance-commands.yaml:1079` | commands[80].name |
| 发票 | 红冲发票 | `.codex-inputs/skeletons/revenue-commands.yaml:1353` | commands[98].name |
| 手工凭证 | 复核手工凭证 | `.codex-inputs/skeletons/finance-commands.yaml:1097` | commands[81].name |
| 差旅超标订单 | 批准差旅超标订单 | `.codex-inputs/skeletons/finance-commands.yaml:2149` | commands[159].name |
| 差旅订单 | 归属差旅订单 | `.codex-inputs/skeletons/finance-commands.yaml:2077` | commands[153].name |
| 履行协议 | 开始履行协议 | `.codex-inputs/skeletons/revenue-commands.yaml:3462` | commands[259].name |
| 差旅月账单 | 差旅月账单已核对 | `.codex-inputs/skeletons/finance-commands.yaml:2101` | commands[154].emits[0] |
| 分包协议 | 分包协议已生效 | `.codex-inputs/skeletons/finance-commands.yaml:561` | commands[40].emits[0] |
| 付款申请 | 提交直接付款申请 | `.codex-inputs/skeletons/finance-processes.finance.yaml:9268` | processes[52].steps[1].command |

多义/方言不同词形（完整对照见JSON）：contract〔多义〕；会计凭证〔方言线索〕；合同〔多义〕。

### 名词·角色

真缺词2；多义0；方言3。区分角色完整名与负责人等通用职责；财务负责人范围另见NM5。

| 不同词形 | 完整载体原文 | 出处文件与行 | 结构字段/声明 |
| --- | --- | --- | --- |
| 商机负责人 | 变更商机负责人 | `.codex-inputs/skeletons/revenue-commands.yaml:2190` | commands[162].name |
| 负责人 | 变更商机负责人 | `.codex-inputs/skeletons/revenue-commands.yaml:2190` | commands[162].name |

多义/方言不同词形（完整对照见JSON）：代理〔方言线索〕；办理人〔方言线索〕；参与人〔方言线索〕。

### 名词·参与方

真缺词4；多义0；方言0。法人、供方等实际词形需对照首选名；客户已弃用桥接不算新多义。

| 不同词形 | 完整载体原文 | 出处文件与行 | 结构字段/声明 |
| --- | --- | --- | --- |
| 供方 | 确认供方履约 | `.codex-inputs/skeletons/finance-commands.yaml:625` | commands[46].name |
| 发包人 | 确认发包人资料 | `.codex-inputs/skeletons/revenue-commands.yaml:3113` | commands[233].name |
| 业主 | 登记业主委托外部检测 | `.codex-inputs/skeletons/revenue-commands.yaml:198` | commands[15].name |
| 法人 | 查看法人、组织与职位现状 | `.codex-inputs/skeletons/people-processes.people.yaml:108` | processes[0].steps[0].command |

### 状态词

真缺词39；多义2；方言3。这里只算词汇化取值的词缺口；可组合取值另补槽位。

| 不同词形 | 完整载体原文 | 出处文件与行 | 结构字段/声明 |
| --- | --- | --- | --- |
| 草稿 | 草稿 | `.codex-inputs/skeletons/finance-processes.finance.yaml:7588` | processes[42].states[0] |
| 被取代 | 被取代 | `.codex-inputs/skeletons/people-state-machines.yaml:1751` | machines[28].states[4].name |
| 草拟 | 草拟 | `.codex-inputs/skeletons/finance-state-machines.yaml:525` | machines[7].states[0] |
| 未开放 | 未开放 | `.codex-inputs/skeletons/finance-state-machines.yaml:805` | machines[14].states[0] |
| 在用 | 在用 | `.codex-inputs/skeletons/finance-state-machines.yaml:1323` | machines[30].states[0] |
| 实缴 | 实缴（到账） | `.codex-inputs/skeletons/finance-processes.finance.yaml:6241` | processes[31].states[2] |
| 无冲突 | 无冲突 | `.codex-inputs/skeletons/revenue-processes.revenue.yaml:11689` | single_aggregate[3].states[1] |
| 有冲突不得承接 | 有冲突不得承接 | `.codex-inputs/skeletons/revenue-processes.revenue.yaml:11691` | single_aggregate[3].states[3] |
| 未关闭 | 未关闭 | `.codex-inputs/skeletons/finance-processes.finance.yaml:8027` | processes[46].states[0] |
| 死信 | 死信 | `.codex-inputs/skeletons/finance-processes.finance.yaml:7087` | processes[37].states[4] |
| 被没收 | 被没收 | `.codex-inputs/skeletons/revenue-processes.revenue.yaml:11805` | single_aggregate[4].states[3] |
| 被索赔 | 被索赔 | `.codex-inputs/skeletons/revenue-processes.revenue.yaml:11808` | single_aggregate[4].states[6] |
| 认缴 | 认缴 | `.codex-inputs/skeletons/finance-processes.finance.yaml:6239` | processes[31].states[0] |
| 部分冲抵 | 部分冲抵 | `.codex-inputs/skeletons/finance-processes.finance.yaml:9446` | processes[53].states[1] |
| 部分实缴 | 部分实缴 | `.codex-inputs/skeletons/finance-processes.finance.yaml:6240` | processes[31].states[1] |
| 部分结清 | 部分结清 | `.codex-inputs/skeletons/finance-state-machines.yaml:173` | machines[4].states[1] |
| dead letter | DEAD_LETTER | `backend/src/main/java/com/forcitis/one/platform/integration/domain/DeliveryStatus.java:8` | DeliveryStatus |
| 不主张 | 不主张 | `.codex-inputs/skeletons/revenue-state-machines.yaml:2277` | machines[43].states[5] |
| 不再受托 | 不再受托 | `.codex-inputs/skeletons/revenue-state-machines.yaml:9` | machines[0].states[2].name |
| 候选人 | 人员:候选人 | `.codex-inputs/skeletons/people-state-machines.yaml:193` | machines[5].states[0].state_set=人员 |

多义/方言不同词形（完整对照见JSON）：employed〔多义〕；separated〔多义〕；停用〔方言线索〕；离职〔方言线索〕；草案〔方言线索〕。

### 限定词

真缺词7；多义0；方言0。设受控修饰源；已由辅助清单或规范允许的词不重复报缺。

| 不同词形 | 完整载体原文 | 出处文件与行 | 结构字段/声明 |
| --- | --- | --- | --- |
| 外部 | 建议外部检测 | `.codex-inputs/skeletons/revenue-commands.yaml:186` | commands[14].name |
| 标准 | 发布标准工时版本 | `.codex-inputs/skeletons/people-commands.yaml:2193` | commands[73].name |
| internal | doFilterInternal | `backend/src/main/java/com/forcitis/one/platform/identity/infrastructure/security/CsrfCookieFilter.java:15` | method |
| 内部 | 内部审批已通过 | `.codex-inputs/skeletons/revenue-state-machines.yaml:596` | machines[9].states[3] |
| 最终 | 确认最终价款 | `.codex-inputs/skeletons/revenue-commands.yaml:705` | commands[52].name |
| 默认 | 默认确认月度考勤 | `.codex-inputs/skeletons/people-commands.yaml:2075` | commands[69].name |
| 自动 | 自动通过 | `frontend/packages/core/src/approval/labels.ts:45` | copy |

### 时间与期间词

真缺词4；多义1；方言0。日期、时刻、期间和有效区间按现行规则分开。

| 不同词形 | 完整载体原文 | 出处文件与行 | 结构字段/声明 |
| --- | --- | --- | --- |
| 月度 | 生成月度考勤 | `.codex-inputs/skeletons/people-commands.yaml:1997` | commands[66].name |
| 期间 | 校验目标期间 | `.codex-inputs/skeletons/finance-processes.finance.yaml:3346` | processes[16].steps[0].command |
| 会计年度 | 开立会计年度 | `.codex-inputs/skeletons/finance-commands.yaml:925` | commands[68].name |
| since | secondsSinceLastCheck | `backend/src/main/java/com/forcitis/one/platform/integration/infrastructure/connection/ReadOnlyAccounts.java:132` | method |

多义/方言不同词形（完整对照见JSON）：会计期间〔多义〕。

### 数量与金额词

真缺词6；多义0；方言4。量值需有具体对象和单位；基数含义留NM5并经财务。

| 不同词形 | 完整载体原文 | 出处文件与行 | 结构字段/声明 |
| --- | --- | --- | --- |
| 余额 | 占用余额 | `.codex-inputs/skeletons/people-commands.yaml:1627` | commands[53].name |
| 费率 | 编制费率版本 | `.codex-inputs/skeletons/revenue-commands.yaml:1968` | commands[145].name |
| 系数 | 编制计费系数版本 | `.codex-inputs/skeletons/revenue-commands.yaml:2010` | commands[148].name |
| 价款 | 确认最终价款 | `.codex-inputs/skeletons/revenue-commands.yaml:705` | commands[52].name |
| revenue | RevenueRecognized | `.codex-inputs/skeletons/revenue-commands.yaml:1143` | commands[82].emits[0] |
| 基数 | 调整缴费基数 | `.codex-inputs/skeletons/people-commands.yaml:2709` | commands[90].name |

多义/方言不同词形（完整对照见JSON）：ar〔方言线索〕；wip〔方言线索〕；合同履约成本〔方言线索〕；坏账〔方言线索〕。

### 单位

真缺词1；多义0；方言0。只保留实际单位词；日历日期不当时长单位。

| 不同词形 | 完整载体原文 | 出处文件与行 | 结构字段/声明 |
| --- | --- | --- | --- |
| millis | millis | `backend/src/main/java/com/forcitis/one/platform/identity/infrastructure/persistence/SessionStoreClock.java:6` | method |

### 结果与原因词

真缺词9；多义1；方言0。原因与结论不自动变成主状态或通用动作。

| 不同词形 | 完整载体原文 | 出处文件与行 | 结构字段/声明 |
| --- | --- | --- | --- |
| 逾期 | 逾期 | `.codex-inputs/skeletons/revenue-state-machines.yaml:938` | machines[16].states[2] |
| 结论 | 登记拒收处置结论 | `.codex-inputs/skeletons/finance-commands.yaml:828` | commands[61].name |
| 未中标 | 未中标 | `.codex-inputs/skeletons/revenue-state-machines.yaml:1741` | machines[29].states[3].name |
| 合格 | 角色合格 | `.codex-inputs/skeletons/revenue-state-machines.yaml:1323` | machines[19].states[5] |
| 中标 | 已中标 | `.codex-inputs/skeletons/revenue-state-machines.yaml:1739` | machines[29].states[2].name |
| 冲突 | 无冲突 | `.codex-inputs/skeletons/revenue-processes.revenue.yaml:11689` | single_aggregate[3].states[1] |
| 超时 | 已超时 | `frontend/apps/h5/src/modules/approval/pages/task-detail-page.tsx:38` | jsx |
| malformed | versionMalformed | `backend/src/main/java/com/forcitis/one/platform/web/GlobalExceptionHandler.java:59` | method |
| 拒收 | 登记拒收处置结论 | `.codex-inputs/skeletons/finance-commands.yaml:828` | commands[61].name |

多义/方言不同词形（完整对照见JSON）：离职〔多义〕。

### 事件后缀与体标记

真缺词57；多义0；方言0。已有N4文本词形，缺结构登记；不是新增业务动词或Event后缀。

| 不同词形 | 完整载体原文 | 出处文件与行 | 结构字段/声明 |
| --- | --- | --- | --- |
| 已 | 已付 | `.codex-inputs/skeletons/finance-state-machines.yaml:749` | machines[12].states[2].name |
| 中 | 编制中 | `.codex-inputs/skeletons/finance-processes.finance.yaml:7791` | processes[44].states[0] |
| 待 | 待查 | `.codex-inputs/skeletons/revenue-processes.revenue.yaml:11688` | single_aggregate[3].states[0] |
| attributed | LegalEntityAttributed | `backend/src/main/java/com/forcitis/one/organization/workforce/events/LegalEntityAttributed.java:10` | type |
| closed | CLOSED | `backend/src/main/java/com/forcitis/one/platform/approval/api/ApprovalTaskStatus.java:8` | ApprovalTaskStatus |
| signed | ContractSigned | `.codex-inputs/skeletons/finance-processes.finance.yaml:157` | processes[1].triggers[0].name |
| accepted | OpportunityAccepted | `.codex-inputs/skeletons/revenue-commands.yaml:2227` | commands[164].emits[0] |
| confirmed | CONFIRMED | `backend/src/main/java/com/forcitis/one/platform/identity/domain/AuthorizationRequestStatus.java:5` | AuthorizationRequestStatus |
| approved | APPROVED | `backend/src/main/java/com/forcitis/one/cost/timesheet/api/TimesheetStatus.java:8` | TimesheetStatus |
| rejected | REJECTED | `backend/src/main/java/com/forcitis/one/cost/timesheet/api/TimesheetStatus.java:9` | TimesheetStatus |
| restated | BusinessFactRestated | `.codex-inputs/skeletons/finance-commands.yaml:814` | commands[59].emits[0] |
| returned | RETURNED | `backend/src/main/java/com/forcitis/one/cost/timesheet/api/TimesheetStatus.java:7` | TimesheetStatus |
| released | RELEASED | `backend/src/main/java/com/forcitis/one/organization/workforce/domain/SyncRunStatus.java:7` | SyncRunStatus |
| issued | InvoiceIssued | `.codex-inputs/skeletons/revenue-commands.yaml:1316` | commands[94].emits[0] |
| pending | PENDING | `backend/src/main/java/com/forcitis/one/platform/approval/api/ApprovalTaskStatus.java:5` | ApprovalTaskStatus |
| 已被 | 已被取代 | `.codex-inputs/skeletons/people-state-machines.yaml:1462` | machines[25].states[2].name |
| submitted | SUBMITTED | `backend/src/main/java/com/forcitis/one/cost/timesheet/api/TimesheetStatus.java:6` | TimesheetStatus |
| registered | EmployeeRegistered | `backend/src/main/java/com/forcitis/one/organization/workforce/events/EmployeeRegistered.java:8` | type |
| adjusted | BudgetAdjusted | `.codex-inputs/skeletons/people-commands.yaml:3700` | commands[124].emits[0] |
| changed | OrgSnapshotChanged | `backend/src/main/java/com/forcitis/one/organization/workforce/events/OrgSnapshotChanged.java:14` | type |

### 界面与消息固定用语

真缺词318；多义0；方言0。完整固定用语缺消息目录登记；不能换算成业务概念数。

| 不同词形 | 完整载体原文 | 出处文件与行 | 结构字段/声明 |
| --- | --- | --- | --- |
| 加载中 | 加载中 | `frontend/apps/h5/src/modules/approval/pages/inbox-page.tsx:58` | copy |
| 待办加载失败 | 待办加载失败 | `frontend/apps/h5/src/modules/approval/pages/inbox-page.tsx:62` | copy |
| 登录已失效，请重新登录 | 登录已失效，请重新登录 | `frontend/packages/core/src/error-messages.ts:106` | copy |
| 通过 | 通过 | `frontend/apps/h5/src/modules/approval/components/task-action-bar.tsx:36` | jsx |
| 驳回 | 驳回 | `frontend/apps/h5/src/modules/approval/components/task-action-bar.tsx:37` | jsx |
| 保存失败 | 保存失败 | `frontend/apps/web/src/modules/attendance/pages/work-calendar-page.tsx:118` | copy |
| 内容刚刚被修改，请刷新后重试 | 内容刚刚被修改，请刷新后重试 | `frontend/packages/core/src/error-messages.ts:40` | copy |
| 取消 | 取消 | `frontend/apps/h5/src/modules/approval/components/task-action-bar.tsx:44` | jsx |
| 审批任务 | 审批任务 | `frontend/apps/h5/src/app/pages/task.config.ts:1` | copy |
| 审批任务加载失败 | 审批任务加载失败 | `frontend/apps/h5/src/modules/approval/pages/task-detail-page.tsx:26` | copy |
| 已认领 | 已认领 | `frontend/apps/h5/src/modules/approval/pages/task-detail-page.tsx:38` | copy |
| 已超时 | 已超时 | `frontend/apps/h5/src/modules/approval/pages/task-detail-page.tsx:38` | jsx |
| 待办 | 待办 | `frontend/apps/h5/src/app/pages/inbox.config.ts:1` | copy |
| 新系统 | 新系统 | `frontend/apps/h5/src/modules/approval/pages/inbox-page.tsx:108` | jsx |
| 显示更多 | 显示更多 | `frontend/apps/h5/src/modules/approval/pages/inbox-page.tsx:113` | jsx |
| 状态 | 状态 | `frontend/apps/web/src/modules/numbering/pages/numbering-rules-page.tsx:65` | copy |
| 理由（必填） | 理由（必填） | `frontend/apps/h5/src/modules/approval/components/task-action-bar.tsx:41` | copy |
| 知会 | 知会 | `frontend/apps/h5/src/modules/approval/pages/inbox-page.tsx:108` | copy |
| 确认失败 | 确认失败 | `frontend/apps/h5/src/modules/session/components/identity-confirmation.tsx:18` | copy |
| 老系统 | 老系统 | `frontend/apps/h5/src/modules/approval/pages/inbox-page.tsx:108` | copy |

### 业务线与专业分类

真缺词1；多义0；方言0。服务产品沿既定主数据地位补登记；专业含义另问。

| 不同词形 | 完整载体原文 | 出处文件与行 | 结构字段/声明 |
| --- | --- | --- | --- |
| 服务产品 | 发布服务产品版本 | `.codex-inputs/skeletons/revenue-commands.yaml:1915` | commands[141].name |

### 级别与分层

真缺词0；多义0；方言0。本范围无真缺词；不表示所有业务等级值已收齐。

本轮没有真缺词，不虚构20个例子。

### 方向词

真缺词4；多义0；方言0。转入转出、目标、出站按各自关系值域登记。

| 不同词形 | 完整载体原文 | 出处文件与行 | 结构字段/声明 |
| --- | --- | --- | --- |
| outbound | OutboundDelivery | `backend/src/main/java/com/forcitis/one/platform/integration/domain/OutboundDelivery.java:18` | type |
| 转入 | 已转入 | `.codex-inputs/skeletons/finance-state-machines.yaml:1624` | machines[40].states[1].name |
| 转出 | 已转出 | `.codex-inputs/skeletons/finance-state-machines.yaml:1232` | machines[28].states[4].name |
| 目标 | 校验目标期间 | `.codex-inputs/skeletons/finance-processes.finance.yaml:3346` | processes[16].steps[0].command |

### 否定与模态词

真缺词2；多义0；方言0。否定与限制保留完整词，不统计不、可等单字残片。

| 不同词形 | 完整载体原文 | 出处文件与行 | 结构字段/声明 |
| --- | --- | --- | --- |
| 不可 | 服务暂时不可用，请稍后重试 | `frontend/packages/core/src/failure.ts:24` | copy |
| 不得 | 有冲突不得承接 | `.codex-inputs/skeletons/revenue-processes.revenue.yaml:11691` | single_aggregate[3].states[3] |

### 表示与技术类别词

真缺词3；多义0；方言0。规范已有类别词直接沿用；缺项补技术结构。

| 不同词形 | 完整载体原文 | 出处文件与行 | 结构字段/声明 |
| --- | --- | --- | --- |
| flag | flag | `backend/src/main/java/com/forcitis/one/platform/approval/infrastructure/flowable/NodePolicyReader.java:72` | method |
| 类别 | 编制资产类别与折旧政策版本 | `.codex-inputs/skeletons/finance-commands.yaml:1808` | commands[133].name |
| 摘要 | 业务摘要 | `frontend/apps/h5/src/modules/approval/pages/task-detail-page.tsx:51` | copy |

### 目的与关系连接词

真缺词3；多义0；方言0。区分业务关系与模板连接词，不能改变事务边界。

| 不同词形 | 完整载体原文 | 出处文件与行 | 结构字段/声明 |
| --- | --- | --- | --- |
| 关联 | 已关联 | `.codex-inputs/skeletons/revenue-state-machines.yaml:316` | machines[6].states[0] |
| 用途 | 已用途确认 | `.codex-inputs/skeletons/finance-state-machines.yaml:1230` | machines[28].states[2] |
| 归属 | 读取归属对象与人员 | `.codex-inputs/skeletons/finance-processes.finance.yaml:8233` | processes[47].steps[0].command |

### 命名空间与系统名

真缺词0；多义0；方言0。本卡只看路径/权限中的结构词；不把系统、模块等泛称报缺。

本轮没有真缺词，不虚构20个例子。

## 明细引用与可复跑命令

上轮[N4_COVERAGE.md](N4_COVERAGE.md)及其25,000行三层明细、[N4_VERBS_STATES.md](N4_VERBS_STATES.md) §3–4、[N5_NOUN_JUDGMENT.md](N5_NOUN_JUDGMENT.md)均保留不改。上轮N8原始词次明细 `/tmp/n8-atoms.json`、复跑 `/tmp/n8-atoms-repeat.json` 及原报告备份 `/tmp/n8r-previous-report.md`只作历史审计，不拿来报告当前真缺词。原入口与原脚本保留，新入口不会复用旧词次汇总。

```sh
export PYTHONPATH=/Users/yeliang/.cache/uv/archive-v0/2QMrIyxguU64vOBC
python3 -B docs/modernization/glossary/coverage/run_atoms_review.py --self-test
python3 -B docs/modernization/glossary/coverage/run_atoms_review.py --out /tmp/n8r-atoms.json
python3 -B docs/modernization/glossary/coverage/render_atoms_review.py /tmp/n8r-atoms.json --out /tmp/n8r-report.md
python3 -B docs/modernization/architecture/decision-page/check_pages.py docs/modernization/rules/decide/sheets/NM5.json
python3 -B docs/modernization/rules/decide/build_decision_page.py NM5 /tmp/nm5-n8r.html
scripts/precheck.sh
```

PyYAML使用本机已有缓存，不安装依赖；路径是本机快照。根目录默认取脚本路径，可用--root指定；skeletons相对root。输出路径解析符号链接并拒绝本worktree及上层主检出仓库；缺输入退出2且不创建输出。不同启动目录应得到相同JSON。旧入口仍可复跑旧口径。

JSON：summary是每类不同词数；records保留每个词的主状态、方言交叉标记和全部完整出处；entries含原始载体、命名面、key、BC、映射、残片；excluded_prose单列不计说明；files含SHA-256，surfaces列提取分布。主状态优先多义/已登记/辅助放行/待核/方言/缺词，同一词有现行登记不因另一BC没命中而变成真缺词。

## 已验证

- N8R入口47项自测通过；原N8入口30项及N4入口自测通过。新增反例覆盖事件desc不计、内部空事件不覆盖上一条原文、src/testing与src/test不进入生产盘点。未修改验收测试或放宽断言。
- 根目录与/tmp两次完整运行，JSON逐项完全相同；1,126个输入/脚本SHA-256逐一核对，11,383处完整原文及行号核对；20类按不同词重新复算。Web完整路由为静态组合，未冒充同一行字面量。
- 18个coverage脚本完成AST字节编译及400行上限检查；旧N4/N8脚本和上轮全部读取输入与历史指纹一致。
- 实际测试仓库内输出被拒绝（退出2、原报告不变）；缺输入退出2且不创建输出；符号链接输出边界有自测。
- NM5为1组3题；使用本机已有PyYAML完整执行check_pages（含R4用语），结果1张页、0处问题；build_decision_page生成/tmp/nm5-n8r.html成功。未因缺PyYAML的跳过结果宣称完成用语检查。
- scripts/precheck.sh退出0；完整输出附后，每个SKIP理由分列说明。全历史gitleaks由precheck实际执行，未提交交付文件另作gitleaks dir --redact扫描。
- 本卡仅重写盘点报告，新增登记方案、NM5及coverage下5个扩展脚本；没有改正式词表、proposed、NAMING_STANDARD、ADR、业务代码或验收目录。未提交、未推送、未联网、未起容器。

## 未运行与不确定项

- 没有发布在线页、读取新答复或做真实手机渲染；只验证本地题面检查与HTML生成。NM5三题均未答。
- 没有实际登记词表、模板、消息目录，也没有实现登记方案的正式守护；未运行正向槽位渲染全量往返、授权/流程/财务算法验收或运行时命名穷举。
- 现有登记重复、跨BC引用与方言只给静态线索；全量残片未人工判定，未知命名面仍可能有遗漏。N4中文已确认不等于英文代码名及每处业务语义已定。
- 金额/基数/费率的实际含义须财务确认；专业目录值与财务负责人范围未提供。命名字段中的说明性长串保留在排除记录，不擅自当正确名称。

precheck每个SKIP的本卡理由：generated docs因文档未提交而跳过，用户禁止提交且不改卡外生成物；迁移/系统执行者/验收/数据库脚本/构建输入登记/断言/正式词表/后端守护均未改；后端与SQL Server集成未改相关覆盖且禁止容器；前端未改、依赖未安装，不安装或联网。卡上没有指定需转绿的业务验收用例。实际脚本输出如下，若有FAIL则本卡不算完成。

## precheck.sh输出

```text
precheck: base origin/main (9bf76677), HEAD b99f7aa9, 66 changed files (62 uncommitted)

SKIP  generated docs (build_all --check)     docs/modernization or backend/glossary/proposed has uncommitted changes: commit first, then run again (请先提交再运行)
PASS  legacy system name                     check_legacy_name: 无违规
PASS  decision pages: question rules (self-test) check_pages self-test: ok
PASS  decision pages: question rules         check_pages: 2 张页，0 处问题
SKIP  migrations immutable                   no migration, marker or Flyway setting changed
SKIP  system actors append-only              system-actors.txt and its script did not change
SKIP  acceptance guard                       no acceptance test changed
SKIP  database scripts aligned               no database script or migration changed
SKIP  docs read by builds are registered     no backend, frontend or script changed
SKIP  assertion drift (warning only)         no test file changed
SKIP  glossary: check:copy, test:scripts, term tests terms.yaml, bcs.yaml and backend/glossary unchanged
SKIP  guards: backend guard tests            no guard test or architecture list changed
PASS  release scripts self-test              deploy_gate.py self-test passed
PASS  shellcheck scripts/release             ok
PASS  gitleaks (full history)                2763 commits scanned; no leaks
      note: uncommitted files are not in history; the pre-commit hook scans them on commit
SKIP  backend tests                          not run by default; no module code changed (shared inputs, resources, build or architecture lists)
      run: (cd backend && ./mvnw -q test-compile surefire:test -Dtest='ArchitectureRulesTest,ModularityTest,ModuleDependencyTest,NamingSurfaceTest,GlossaryTest,DdlNamingTest,LegacyShapeTest,TimeSourceGuardTest,TestIsolationGuardTest' -Dsurefire.failIfNoSpecifiedTests=false)
      before delivery run everything: scripts/ci/backend_verify.sh (Testcontainers: ask the user before starting containers)
SKIP  frontend verify                        前端依赖未安装：先在 frontend/ 运行 pnpm install --frozen-lockfile --offline（只用本机缓存、不下载）；缓存缺包而失败时，再问用户是否联网安装（本次未改 frontend/，CI 仍会跑）
SKIP  SQL Server integration tests           relevant_change.py: nothing the *MssqlIT tests exercise changed

SUMMARY: 6 PASS, 0 FAIL, 12 SKIP (base origin/main) — ready
```
