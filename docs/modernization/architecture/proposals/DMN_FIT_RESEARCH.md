# DMN 引入适配研究
状态：草稿｜2026-10-17｜负责：one-architecture

## 1. 推荐与适用边界

**推荐本轮不新增、启用 DMN 决策执行链；继续采用所属 BC 的代码或 D15 版本化配置表与表驱动测试。** 已有证据足以开展真实决策表研究，但不足以裁定引擎上线。下一次选型的优先样本是 `accounting.ledger` 的科目映射，备选样本是工时类型到审核节点的映射；先用同一份已批准规则和样例比较 D15 与 DMN，再决定是否部分引入。此处是研究建议，不是批准、不撤销既有 ADR，也不授权实现。

依据与把握：

- **已核实，高把握**：15 个代码决策点有登记；当前源码中的大部分是组织关系查询、安全控制、不变量或计算。将其整体迁移不能直接获得业务人员维护政策的收益（§3）。
- **已核实，高把握**：三个本地分支快照有 555 个显式 `decision` 块、1,251 条分支，包含单向连接与人工结果，不能等同于 555 张业务决策表。附录逐块列出，没有只采样主流程。
- **已核实，高把握**：D15 已要求 DMN 在内的配置按版本、生效日与批准记录管理；DMN 不是 D15 的替代治理方案。现有编号模块提供可复用的版本治理做法，但不是现成通用决策平台（§4）。
- **已核实，中等把握**：Flowable DMN 8.0.0 制品已在本机，具备 Spring、测试、求值与历史 API；当前构建只直接声明 process starter，未直接声明 DMN starter。没有本 worktree 构建或 DMN 启动、求值证据，不能由缓存推出兼容成功（§5）。
- **待证据**：目标映射表的财务批准完整行数、实际政策变更次数、业务维护者的编辑需求与可用性结果、两方案开发和运行代价。故不能声称已经证明节省工时、降低错误率或性能足够（§6）。

## 2. 方法、证据范围与既有决定

按 [FRAMEWORKS.md §7](../FRAMEWORKS.md) 的触发条件与 ADR-0041 决定 6，逐项比较要求、代价、收益、把握；按 [METHOD.md 接缝 1](../METHOD.md) 与 ADR-0050 决定 9 区分不变式、判定、人工编排、计算。代码、登记表和流程稿分别作为实现证据、目录证据和设计证据，不混作运行验收。

### 2.1 本次证据编号

| 编号 | 本地来源、版本或定位 | 能证明什么 |
|---|---|---|
| E1 | 工作树 HEAD `1e2ec33c4c0fb5c92a4100a943ad2d7bf1269c3a`；开工 `git status --short` 为空 | 本卡代码基线与初始无改动状态 |
| E2 | [decisions.yaml](../dmn/decisions.yaml)，`DEC-01` 至 `DEC-15` | “15 个代码决策点”的登记处；不是全部代码条件覆盖清单 |
| E3 | `origin/claude/one-arch-revenue-closeout` → `5b31ba2794e3558f3f70d1ac84c6d2884e1a714e`；`docs/modernization/architecture/proposals/closeout/revenue/processes.revenue.yaml` | 收入组流程提议快照；附录 A 的行号均来自该对象 |
| E4 | `origin/claude/one-arch-finance-closeout` → `2bec63a04151c03cc18e91e0124971d2b673b709`；`docs/modernization/architecture/proposals/closeout/finance/processes.finance.yaml` | 财务组流程提议快照；附录 B 的行号均来自该对象 |
| E5 | `origin/claude/one-arch-people-closeout` → `379f248396c2cede61c0f385bc1b573a61146339`；`docs/modernization/architecture/proposals/closeout/people/processes.people.yaml` | 人员组流程提议快照；附录 C 的行号均来自该对象 |
| E6 | [SESSION_PLAYBOOK §12.2、§14.1 D15](../../SESSION_PLAYBOOK.md)；[DataArchitectureTest.java](../../../../backend/src/test/java/com/forcitis/one/DataArchitectureTest.java) `d10`（431–449） | 配置治理要求及现有结构守护；不证明全部配置运行行为正确 |
| E7 | [backend/pom.xml](../../../../backend/pom.xml):4–7、25；`~/.m2/repository/org/flowable/*/8.0.0/` 的 POM、JAR 元数据与类签名 | 声明版本、缓存制品能力；不证明当前依赖解析结果或引擎启用 |
| E8 | [TIMESHEET_ROUTING_RULES.md](../../rules/TIMESHEET_ROUTING_RULES.md) RULE-388–392；[BID_DECISION_RULES.md](../../rules/BID_DECISION_RULES.md) RULE-414–415；[GL_RULES.md](../../rules/GL_RULES.md) GL-060–067、GL-082 | 具体政策与尚待确定项；其中旧规则仅作证据，目标以已批准规则与 ADR 为准 |

E3–E5 只用 `git show <完整提交>:<路径>` 读取，不 checkout、不 fetch。它们是本机可见快照，不声称是远端此刻最新状态。流程中的 `pending`、`〔缺〕`、经验估计均保留其提议性质。本研究不联网、不连接生产、不安装依赖、不起容器、不修改验收测试。

### 2.2 ADR 约束与差异

| 来源 | 原决定及对本研究的约束 |
|---|---|
| [ADR-0007](../../adr/src/ADR-0007.md) 决定 1–4 | Flowable 8 在 `platform.approval` 内部；BPMN 只编排；判定可用 DMN 或所属模块预计算 `routingFlags`；在途排空。选了 BPMN 引擎不等于已启用 DMN |
| [ADR-0016](../../adr/src/ADR-0016.md) 决定 1–5 | 单一过账管道、事实只追加、期间闸门；科目映射只归 `accounting.ledger`，原文要求 DMN 按版本生效；DMN 不用于改变收入确认原则或 WIP 与开票一致性边界 |
| [ADR-0012](../../adr/src/ADR-0012.md) 决定 1–2 | 模块级集成测试为主；决策表另补纯逻辑边界测试 |
| [ADR-0021](../../adr/src/ADR-0021.md) 决定 3、后果 | 早期路线写审批路由、科目映射用 DMN；迁移证据须按目标规则重新核对，不能照搬旧表 |
| [ADR-0022](../../adr/src/ADR-0022.md) 决定 2、后果“版本化配置” | 科目表、映射、授权、报表行、结转模板版本化；期间首日生效；冲销按原分录逐行取反，重做用适用新版本。**口径转换规则按代码类，映射表按配置类**，不能借 DMN 把会计计算全部改为在线配置 |
| [ADR-0031](../../adr/src/ADR-0031.md) 审批与迁移相关条款 | 更正路径的政策可含单据性质、冲销类别、跨期输入；在途按发起版本完成。随后被取代的期间政策以现行 ADR 为准 |
| [ADR-0041](../../adr/src/ADR-0041.md) 决定 1、6、影响面 | DMN 延至第一张真实决策表时与 D15 比较、另开 ADR；未直接删除 ADR-0016 决定 3，因此若最终用 D15 替代科目映射 DMN，仍须正式处理该条 |
| [ADR-0050](../../adr/src/ADR-0050.md) 决定 9、19、与 ADR-0041 关系表 | 已采纳，继续保留 DMN 推迟，判定暂用版本化配置或代码。METHOD / FRAMEWORKS 仍有旧方法文字，冲突以已采纳 ADR 为准 |
| [ADR-0052](../../adr/src/ADR-0052.md) 状态行 | 当前为“提议”，不能把其方法调整当作已经生效；同目录 ADR-0054–0056 也须按各自状态使用 |

E2 表头“DMN 1.6”和“按能力地图重新推导”、METHOD 接缝编号，与后续已采纳决定不完全一致；`target: dmn-planned` 只是意向。本卡报告差异，不改登记表。Flowable 本地解析器证据见 §5.2，不沿用表头把 1.6 当实施版本。

## 3. 逐项适配

### 3.1 判据与列口径

“适合”表示规则可以形成业务可读、输入输出清楚的决策表，**不表示已经证明需要 DMN 引擎**；小型一一映射仍可能更适合 D15。“不适合”表示本研究不建议把该判断迁出原来的领域、安全或编排职责。“待证据”表示要先补政策条目、阈值、适用范围或确定性语义，不能用假定凑表。

规则条数分别计量：代码登记引用的规则 ID 数；流程 `decision` 中的分支数（含 `otherwise`）。复杂谓词没有擅自拆成可执行规则；分支数不等于 DMN 行数。单向 `otherwise` 也列入，以便完整复核。“业务人员需看懂”和“版本/留痕”列是研究建议，不是已有编辑权限或已实现的声明。

**频率 F0：未见可量化的业务政策变更记录，频率未知。** 所有代码点及附录条目默认 F0；本次未把 Git 修改次数、流程发生量、`frequency_ref`、分支概率 `p`、每月算薪、每年维护日历当作政策改动频率。PRC-119 的年度维护、PRC-123 的年度申报是维护/申报周期的设计证据，不能证明规则每年改变。需要配置版本历史和批准记录后，才可计算变更次数。

### 3.2 15 个现有代码决策点

代码路径以 `backend/src/main/java/com/forcitis/one/` 为前缀；行号以 E1 当前源码为准。表中规则数是 E2 的不同依据数，括号再给源码结构量级；这些点相互重叠，禁止相加当作独立规则总量。

| 点 | 当前证据与规则量 | 结论及原因 | 业务可读需求（建议） | 版本与留痕（建议）；频率 |
|---|---|---|---|---|
| DEC-01 候选审批人按类型取人 | `platform/approval/domain/AssigneeResolver.java` `collect`；6 个 RULE（249/250/253/254/258/262），4 种 AssignmentRule | 不适合整体移入。类型分派后还要查组织/角色、过滤在职、员工转账号；可表格化的是“哪类单据选哪种指派策略”，不是目录查询 | 审批管理员需看懂策略，业务可看结果 | 策略若可变应版本化，任务保存实际候选/策略版本；F0 |
| DEC-02 自批回避与上收 | 同文件 `resolve`；1 个 RULE-307；自批允许、其他候选、唯一候选上收等路径 | 不适合。防自批与候选集合操作继续代码守护，不能由政策表关闭强制控制 | 控制说明与上收原因需可读 | 允许自批的例外应受控；保留上收原因、实际人选；F0 |
| DEC-03 无人时跳过/非法输入/兜底 | 同文件 `resolve`、`fallback`；3 个 RULE（300/250/254），SKIPPED/INVALID_INPUT/ADMINISTRATOR_FALLBACK/UNRESOLVED 4 类结果 | 不适合。输入错误与合法无人不能混同；顺序会影响是否静默跳过 | 管理员需看懂拒绝与兜底原因 | 节点 `skipWhenEmpty` 配置需可追溯，执行结果留痕；F0 |
| DEC-04 汇报线不全上推 | 同文件 `initiatorSupervisor`；1 个 RULE-250；按 level 遍历并防环 | 不适合。图遍历、在职过滤与最近候选选择，决策表仍需调用同一算法 | 管理员可读解析链 | 组织历史与任务人选快照；不靠 DMN 版本替代组织版本；F0 |
| DEC-05 部门负责人就近上溯 | 同文件 `departmentHead`、`headFrom`；2 个 RULE（253/257）；本部门+祖先链 | 不适合。启用状态、在职状态、排除集合与最近祖先查询 | 管理员可读来源部门 | 组织有效期与解析链留痕；F0 |
| DEC-06 审核节点路由旗标 | `cost/timesheet/domain/Timesheet.java:152` 当前只置 PROJECT_HOURS；`processes/cost_timesheet_approval.bpmn20.xml:17–50` 3 个节点；E2 引 RULE-300，E8 新规则 RULE-388–392 共 5 条 | **适合决策表**。目标是项目/售前/间接 3 种类型到旗标/审核职责映射，外加未知类型拒绝；这是目标规则与代码现状的差距，不能声称 3 类已实现。3 行映射本身尚不足以证明需要 DMN | 业务负责人和审批管理员需看懂，实际取人仍由目录解析 | 路由政策版本、发起时输入及选定节点需锁定；F0 |
| DEC-07 同内容重复审批自动通过 | `platform/approval/application/TaskRegistrar.java:41`，`ApprovalRequest.isRepeatApprover`；1 个 RULE-302；开关+唯一候选+本节点同 ContentHash 已通过 3 个合取条件 | 不适合。历史与内容哈希一致性校验；拆到表中反而多一套状态/输入适配 | 需展示自动通过的理由 | 节点开关可版本化；保存被复用审批及哈希依据；F0 |
| DEC-08 撤回/撤销截止 | `platform/approval/application/Retraction.java`；`domain/ApprovalRequest.java:254`；3 个 RULE（268/269/309），状态、发起人、截止、原因校验 | 不适合。授权、聚合状态与原子执行控制；可撤回展示和命令继续共用校验 | 发起人需看懂不能撤回原因 | 截止节点配置可跟随流程版本，动作与拒绝留痕；F0 |
| DEC-09 审批时限计划 | `platform/approval/application/DeadlinePlanner.java:31`；2 个 RULE（383/382），3 个计划时刻 | 不适合整体移入。工作日历推算属于计算；可配置的是 deadlineWorkdays/escalationWorkdays | 管理员需看懂期限参数 | 参数版本+日历版本+实际计划时刻；F0 |
| DEC-10 谁能操作任务 | `platform/approval/domain/TaskActors.java:11`；RULE-276、I-7；空人/受理人/候选人 3 类路径 | 不适合。安全控制，不能通过业务编辑规则扩大权限 | 管理员需可解释 | 权限策略随代码发布，任务操作与拒绝留痕；F0 |
| DEC-11 角色数据范围 | `platform/identity/domain/AccessScopeCalculator.java` `calculate`、`orgUnitReach`；3 个 RULE（349/350/351），6 类 AccessScopeKind | 不适合。安全边界、集合并集与组织子树；未授权不能变为全量 | 授权管理员需读懂授权清单 | 授权历史和范围变更审计；范围算法仍代码；F0 |
| DEC-12 敏感授权第二人确认/授予上限 | `platform/identity/application/PrivilegeCeiling.java`、`domain/SensitivePermissions.java`；2 个 ADR（0026 §6、0027 §3），敏感权限集合当前 6 项（不是 6 条决策规则） | 不适合。权限上限、管理角色与四眼控制；敏感集合也不得任意在线改 | 安全管理员需读懂约束 | 按安全变更治理、双人确认、授予/拒绝留痕；F0 |
| DEC-13 非生产收件人白名单 | `platform/integration/domain/RecipientPolicy.java:15`；1 个 RULE-375；全发/白名单内/名单外 3 类 | 不适合。环境防误发控制与集合过滤 | 运维需读懂白名单结果 | 环境配置变更与投递 SKIPPED 留痕；F0 |
| DEC-14 组织同步缩水保护 | `organization/workforce/domain/ShrinkGuard.java:11`；2 个 RULE（358/366），组织/员工 2 个计数应用同一阈值公式 | 不适合整体移入。比较式固定，阈值是参数；20 的示例不能当实际配置或变更频率 | 管理员需看懂阈值和被拦原因 | 阈值调整按参数治理，保留前后计数与本次参数；F0 |
| DEC-15 员工/部门当日法人 | `organization/workforce/domain/LegalEntityResolution.java:16`；1 个 RULE-385；本人优先、祖先最近归属、无归属 3 类 | 不适合。按日期查归属与防环遍历，关键是历史数据正确 | 人力需看懂归属依据 | 归属/任职版本与取值业务日留痕；F0 |

E2 的部分行号已经漂移；DEC-01 当前最终返回账号 `UserId` 集合，中间候选是 `EmployeeId`，不能直接把登记中的“员工集合”作为新 DMN 对外契约。以上逐项核对的是源码，引用测试路径不表示本卡运行过测试。

### 3.3 三组流程的适配结论与补充政策点

附录 A–C 对 **所有顶层集合**（主流程、事件策略、单聚合维护、读模型）递归枚举 `decision`；额外枚举 `guard` 与 `checks` 的每一项。每个块列完整条件与去向、分支数、结论、理由和业务可读/版本/留痕建议。显式分支为空的流程也单列，避免误报“没有决策”。

下表补充散落于描述、配置内容或 `approval` 的政策；它们与附录的分支可能重合，**不计入 555 个块的数量**。行数未知就写未知，未从政策种类数或流程概率推定。

| 逐项候选/位置（E3/E4/E5） | 已知条目量与缺口 | 适配结论 | 业务可读、版本与留痕需求（建议）；频率 |
|---|---|---|---|
| PRC-93 生成分录、PRC-100 映射表与授权表、PRC-135 补映射 | GL-067 记录旧 dev 备份 9 条规则、30 分录行、33 个辅助核算；并非目标批准映射行数。目标条件、冲突处理及财务签字全集未见 | **适合决策表**，优先比较样本。静态科目/账簿对应可用关系表；多维条件选择科目或模板才增加 DMN 价值。分录金额计算、双账原子写、授权拦截仍代码 | 财务需读懂并批准；D15、期间首日、分录引用映射版本和命中行；F0 |
| PRC-183 参投异常加审，RULE-414/415 | 3 个初始异常示例（利润/产能/独立性）+正常/放弃；阈值、容量计量和加审人待定 | **待证据**。可表格化异常是否加审；负责人是否参投与专业独立性硬拦截不能交表自动放行 | 经营负责人需读；政策版本和本次命中依据；F0 |
| PRC-57 开票准入金额/关联方加审，AR-01/02/07 | 显式 4 分支混含准入不变量；单据类型阈值表完整行数未知 | **待证据**。拆出“需加审/级次”政策；生效协议、可开票余额、往来单位有效性仍领域守护 | 财务/业务需读；阈值版本、业务日、加审理由；F0 |
| PRC-69 供方类别是否需资质 | 客户免准入、供方免资质/需资质 3 路；供方类别清单未知 | **待证据**。候选是类别→证书要求表；资格核验、有效期和停用状态继续原模块控制 | 采购/资质岗需读；要求版本+实际证书引用；F0 |
| PRC-86 员工借款额度与未结借款 | 5 分支含 3 种操作入口；额度、规则卡待补 | **待证据**。只研究限额参数/等级表；入口与未结余额不变量不迁移 | 财务需读；额度版本与拒绝原因；F0 |
| PRC-37 满意度低分提醒 | 2 分支，阈值明确写待定 | **待证据**；单阈值优先 D15 参数 | 客服/质量需读；阈值版本与提醒记录；F0 |
| PRC-151 经营产值抽查 | 2 分支，抽查比例政策，真实行数未知 | **待证据**；比例参数优先 D15；随机抽取须在确定性选样算法中固定种子/样本标识，不能让 DMN 内生成随机数 | 部门负责人需读；比例版本与样本/选样依据；F0 |
| PRC-13 费率/系数/协议价目 | 职级×薪档×城市、业务线×客户分级；有效版本查找，实际行数未知 | **不适合整体迁 DMN**；结构化价格与适用期先用版本表；只有复杂优先级择价政策另行评估 | 定价岗需读；引用确切价目版本与舍入规则；F0 |
| PRC-52 证据类型开放、PRC-53 确认收入 | 已开放证据类型集合，行数未知；附录有准入 2 分支 | **不适合整体迁移**；类型清单/批准登记用 D15；证据真实性、额度与收入事实守护保留 | 财务/审计需读；类型开放批准、证据与事实引用；F0 |
| PRC-54 视为同意期满认定 | 7 个显式判断块，含竞速、声明日期、退回二次校验 | **不适合整体迁移**；审查期限可版本化，定时与退回竞速、当轮一致性、法定/审计前置不靠政策表放开 | 交付/财务需解释；合同参数、轮次、声明与认定事件留痕；F0 |
| PRC-73 进项用途、PRC-77 税费申报 | 现有分支是人工税务结论/外部回执；税务政策分类行数未知 | **待证据**仅对另行批准的用途/税目选择表；申报计算、平台查验与法定约束不迁表 | 财税需读；版本、证据与回执留痕；F0 |
| PRC-75 付款预警、PRC-87 超预算提醒 | “提前付款”“账户新近变更”“预算不足”；“新近”的窗口取值未知 | **待证据**仅对预警等级/加审表；三人分离、账户归属、余额占用继续代码 | 财务需读；预警政策版本和支付事实记录；F0 |
| PRC-97/98 分摊规则，PRC-165 成本核算参数 | 分摊基数、组织引用；售前超期 N 默认 6 月为设计参数，不是变更记录 | **不适合整体迁移**；参数/基数选择可用 D15；分摊守恒、尾差、零基数、期间门禁用代码 | 财务需读；规则版本及各运行引用；F0 |
| PRC-166 折旧政策、PRC-174 差旅补贴 | 方法/年限/残值率、差旅类型×天数档位；实际行数未知 | **待证据**仅对复杂分类择档；简单类别参数表用 D15，折旧与补贴金额计算留代码 | 财务需读；生效版本与运算结果引用；F0 |
| PRC-175 直接付款豁免类别 | RULE-407 清单与来源对象限制，行数未知；显式 5 分支为混合判断 | **待证据**仅对豁免类别选择；不得由配置跳过付款来源硬约束 | 财务需读；清单批准版本与命中依据；F0 |
| PRC-84/130 参保适用类别 | 退休返聘/实习不参保，2 路（其余参与）；不在此重新核定政策正确性 | **适合决策表**，但小型类别成员表已足够；参保实际办理仍人工与记录 | 人力需读；适用类别版本与办理依据；F0 |
| PRC-113 股息红利扣缴适用性 | 法人/机构与自然人 2 路；合伙所得性质在 notes 中仍有问题 | **待证据**；必须补所得性质与主体分类，不能把二分默认当完整税务政策 | 人力/财税需读；版本及所得性质引用；F0 |
| PRC-115/116/117 假勤免审批/证明/补登/透支政策 | 显式每块 2–3 路；U14/U15 等阈值/期限、假期类型全集未知 | **待证据**。可表格化“类型×天数×材料→需人审/材料”；余额占用、时间冲突、已开始不可撤销等不变量保留 | 人力/员工需读；政策版本、申请快照与命中行；F0 |
| PRC-119 工作日历与标准工时 | 年度维护/按法规调整，标准工时版本与区间唯一 | **不适合整体迁移**。版本主数据与日历查询；年度维护不是规则每年修改的证据 | 人力/全员可读；日历依据文件和工时版本；F0 |
| PRC-121/122 奖金方案/核定 | 方案额度、评估类型、缺版本拒绝；具体档位未知 | **待证据**仅对评估类别/档位→方案资格，奖金计算和已核定结果不追溯仍代码 | 人力/业务需读；方案批准版本与核定输入；F0 |
| PRC-123/124/125/180 薪酬、个税、社保规则 | 规则类别/范围版本存在于提议稿；税档/比例/基数/折算项目全集未知 | **待证据**仅对纯分类或税档表；累计计税、舍入、期间结算、追补、敏感数据保护不整体交给 DMN | 人力/财税需读；参数版本+最小必要输入，个人明细禁止写普通引擎日志；F0 |
| PRC-139 用印免复审、PRC-142/143/144 类型级审批 | 已通过来源且份数不超→免重复审；IT 类别、岗位资产标准、活动类型等行数未知 | **待证据**仅对类别→需审批/标准配置政策；来源已批准、份数上限、资产无占用仍不变量 | 行政/IT 需读；类别政策版本与实际申请依据；F0 |
| PRC-160/162 年度假期应享/结转/清零 | 按类型×适用范围生效规则；实际档位与公式未知 | **待证据**仅对资格/档位；账户占用与结转守恒、折算金额/天数仍代码 | 人力/员工需读；账户绑定规则版本，不重算已结算额度；F0 |
| PRC-182 知识条目复审/废止建议 | 引用量、有效期、窗口；“引用多/长期无人”阈值未定 | **待证据**；仅提示等级可成表，不代替专家评审或自动废止 | 知识负责人需读；阈值版本与推荐依据；F0 |
| PRC-22 阶段门、PRC-62 到期提示、PRC-154 三口径对照；PRC-90/106 报表读取 | 出口条件/届满日/期间状态/对账条件；不是政策维护频率 | **不适合整体迁移**。报表与预警投影、状态/证据核对；门预警不能被误变成可编辑审批闸门 | 业务/财务需读检查项；来源事实与口径版本可追溯；F0 |

## 4. DMN 与 D15 版本化配置表逐项对比

比较对象是“所属 BC 的 D15 配置表 + 类型化选择器 + 表驱动测试”与“同样遵守 D15 的 DMN 模型 + 引擎适配器”，避免把引擎历史当成完整业务审计。

| 维度 | D15 配置表 + 表驱动测试 | DMN + D15 | 对本系统的判断 |
|---|---|---|---|
| 表达力 | 枚举/类别、区间、优先级的简单组合易用 SQL/类型化选择器；复杂多表依赖要编写逻辑 | 显式输入列、输出列、命中策略和规则依赖有望统一表达；本地 Flowable 有 Decision/DecisionService 与命中策略类型，具体表达式能力未做求值验证 | 工时 3 类映射、单阈值、成员集合用 D15 足够；多维科目/资格政策才值得比较 |
| 版本 | 版本号+不可变内容+作者/确认记录，可与业务事实同事务锁定 | 引擎部署/决策版本与 D15 政策版本是两套标识，须建立不可变对应；不能直接选 latest | 增加部署 ID、决策定义 ID、内容摘要、输入输出契约版本关联工作 |
| 生效日期 | 按业务日选所属 BC 的有效版本，校验重叠/缺口/已关账边界 | 引擎部署次序不等于业务生效日；仍须由 D15 选择正确模型，固定发起版本或业务事件版本 | 期间首日、在途锁定、不追溯要求完全保留 |
| 审计 | 需记录版本、业务对象、输入摘要/可保留快照、命中行、输出、原因 | Flowable 提供 audit trail、history 查询与执行 JSON，但不含本系统批准出处、受控生效、保留策略的完整保证 | 两方案都要接 `platform.audit` 与事实追溯；引擎日志可关闭/可清理，不能代替只追加事实与审计 |
| 测试 | 参数化样例、边界、区间重叠与空洞、零命中/多命中、旧版重放；类型可在编译期约束 | 同一业务样例加 XML/模型解析、命中策略、变量/结果转换、引擎升级、重部署版本锁定等测试 | DMN 增加一层语义/适配测试；不给出“模型校验即正确”的结论 |
| 业务人员看懂/编辑 | 受控表单可直接列条件/结果，另建编辑和预览；已有表结构不等于已有编辑界面 | 可表格化评审；本地未见可用模型编辑器及其授权/许可/培训验证 | 两者都要批准、预览、差异和回放；没有业务自助编辑需求证据时不把低代码当已得收益 |
| 迁移 | 从已批准目标规则建初版，来源对照、逐行样例与版本引用 | 另需模型生成/导入、内容摘要、历史引用、候选求值旁路比对、回退路径 | 不直接导入旧表达式/旧科目；未批准或 `TBD-*` 不作为可执行模型 |
| 架构与事务 | 所属 BC 持有政策和选择器；接口只传类型化输入输出 | 本地 API 使用 `Map<String,Object>`，必须封在 infrastructure，领域与 api 不得泄露引擎类型；科目映射归 ledger 与 G19 冲突须 ADR 解决 | 不能仅加一个 starter 就视为完成；见 §7 条件性 ADR 要点 |
| 运维与供应链 | 复用现有 PostgreSQL、Flyway、配置审计；仍需批准与查询索引 | 新增引擎模块、表注册、DDL/升级/备份清理、语义兼容矩阵与漏洞/许可核查 | 无新增外部服务的 Flowable 嵌入式候选较容易研究；未实测性能/体积，不填收益数字 |
| 既有决定一致性 | 符合 ADR-0041/0050 当前延期安排；若最终替代 ADR-0016 的科目映射 DMN 要求须新 ADR | 符合 ADR-0007 的可选方向、ADR-0016 的原映射方向；启用仍须 ADR-0041 触发后的新选型 ADR | 两条路线都须处理具体冲突，没有“用 DMN 就天然合规”的捷径 |

**D15 的本地实现边界**：`backend/architecture/data-access-registry.yaml:347–360` 将 `platform_numbering.numbering_rules` 记为 CONFIG_VERSIONED，指向 `numbering_rule_versions`。`NumberingRule.inForceOn` 按业务日筛选已确认版本；`TemplateVersion` 有版本、生效日、作者、业务/财务确认。基线 SQL `platform_numbering/V20261014100100__platform_numbering_baseline.sql:94–165` 限制内容改写、删除与既有确认撤回，允许补确认及受限状态变化。因此它并非“任何列绝对不可 UPDATE”的通用模板；D10 检查的字段/触发器结构也不证明生效区间、批准出处或每次决策回放均正确。目标规则仍需逐个设计，不宣称现有编号模块已满足所有决策场景。

不引入 DMN 时，同样需求的落点：业务用版本化条件/结果表评审；批准后只追加政策版本和决定出处；由所属 BC 按业务日期选择并固定版本；选择器只做明确的分类/区间匹配，零命中/多命中按已批准策略失败并告警；结果保存命中行及版本引用；原子校验、状态转换、Money/Hours 运算仍在领域；以同一套业务样例做表驱动测试与历史回放。它需要补齐的编辑器/追溯等能力不能写成“现状已经全部实现”。

## 5. 候选引擎：只按本地材料核对

### 5.1 比较结果

下文 `M` 表示 `/Users/yeliang/.m2/repository`，`G` 表示 `/Users/yeliang/.gradle/caches`。本机扫描匹配 `flowable|camunda|operaton|drools|kie-dmn` 的 JAR/POM：M 216 个，G 0 个；其中 Camunda/Operaton 0，Drools 3 个 POM，`kie-dmn` 0。扫描未命中只表示本次范围内缺证，不表示产品没有能力。

| 候选 | 版本证据 | 许可证据与边界 | Spring Boot / Modulith 集成 | 测试方式 | 审计留痕 |
|---|---|---|---|---|---|
| Flowable DMN | 当前 POM process starter 为 8.0.0；本地 DMN Engine/API/Spring/AutoConfigure 8.0.0 JAR/POM 齐，DMN starter POM 齐；未在项目解析/启动 | `flowable-root/8.0.0/*.pom:410–415` 声明 Apache v2；DMN Engine MANIFEST 声明 8.0.0、Apache 2.0 URL；所查 API/Engine/Spring/AutoConfigure JAR 未见独立 LICENSE/NOTICE。尚非全依赖许可审核 | 本地 standalone DMN starter、Spring 配置与自动装配入口可证；供应方 POM 基线 Boot 4.0.2 / Spring 7.0.3，项目 Boot 4.1.1 / Modulith 2.1.1 / Java 25 的 DMN 组合未测；无专门 Modulith 适配器证据 | 本地有 JUnit Jupiter 扩展、部署注解、Spring 扩展，及独立求值 API；本卡未运行 | 历史查询、执行 JSON、规则级 audit trail、PostgreSQL 建表 SQL 可证；实际留存、权限、清理与本系统事务未测 |
| Camunda 7 DMN | 本地无相应制品；具体可选版本、维护期限待联网核实 | 待联网核实对应 DMN 制品、编辑器、发行版的许可正文与适用范围；不凭产品名填许可 | 待联网核实嵌入/服务形态、Boot 4.1.1 / Java 25 依赖矩阵、Modulith 封装及事务边界 | 待联网核实该版本独立求值/JUnit API、模型一致性测试能力 | 待联网核实独立引擎与平台历史差异、规则命中内容、持久化和保留能力 |
| Camunda 8 DMN | 本地无相应制品；具体组件/版本待联网核实 | 待联网分别核实 DMN 评估组件、平台服务、编辑器及自托管/商业使用条件 | 待联网核实独立评估还是平台调用、客户端与服务器版本、网络/事务/部署要求；不可套用 Camunda 7 集成结论 | 待联网核实模型求值与平台集成测试所需组件、是否可离线测试 | 待联网核实命中轨迹、关联业务 ID、历史查询、导出及清理 |
| Operaton DMN | 本地无相应制品；版本与支持周期待联网核实 | 待联网核实 DMN 模块与依赖/编辑器许可，不由来源项目推定 | 待联网核实 Boot / Java / Spring 版本、嵌入入口、Modulith 与事务方式 | 待联网核实独立 DMN API、JUnit 与模型验证 | 待联网核实历史表/API、规则命中轨迹、保留与审计集成 |
| Drools DMN | 仅 `org/drools/drools-bom/{7.63.0.Final,9.44.0.Final}` 与 `org/kie/drools-parent/9.44.0.Final` POM；未见 DMN 实现 JAR/POM；不能把 BOM 版本当已可用 DMN 版本 | parent POM:28–34 声明 Apache 2.0，:199 列 kie-dmn 模块；只证明 parent 声明，目标 DMN 制品/依赖/编辑器许可待联网核实 | 待联网核实选定 DMN 组件、Boot 4.1.1 / Java 25 兼容矩阵、嵌入配置、与规则会话组件的关系；Modulith 仍须模块内适配 | 待联网核实独立模型求值/JUnit/TCK 入口和所需运行依赖 | 待联网核实是否内建持久历史、事件监听轨迹内容及落本系统审计的方式 |

### 5.2 Flowable 的可重放本地证据

- **实际声明**：`backend/pom.xml:25` 为 `flowable-spring-boot-starter-process:8.0.0`；没有直接声明 DMN starter。process starter 同版 POM:16–32 是流程模块；`flowable-engine` POM:89–92 引 DMN API；AutoConfigure POM:43–46 的 `flowable-dmn-spring-configurator` 为 optional。**API 存在、自动装配代码存在、DMN Engine 已加载是三层不同证据。** 当前 worktree 无 `backend/target`；不运行 Maven，故没有 resolved dependency tree 或成品 classpath 结论。
- **模块入口**：`M/org/flowable/flowable-spring-boot-starter-dmn/8.0.0/flowable-spring-boot-starter-dmn-8.0.0.pom:12–32` 明确 standalone starter，依赖 AutoConfigure、DMN Engine、DMN Spring、JDBC。`SpringDmnEngineConfiguration` 有 `setDataSource`、`setTransactionManager`、`setDeploymentResources`；`DmnEngineFactoryBean` 是 Spring FactoryBean。可据此设计本地嵌入适配，但不能据签名认定事务已集成成功。
- **启用条件**：AutoConfigure JAR 的 `AutoConfiguration.imports` 有 `DmnEngineAutoConfiguration`、`DmnEngineServicesAutoConfiguration`；`ConditionalOnDmnEngine` 的字节码要求 DmnEngine、SpringDmnEngineConfiguration 在类路径且启用属性满足；metadata 中 `flowable.dmn.enabled`、`deploy-resources` 默认 true。以后补入依赖可能自动部署模型，不能未经配置审查直接加入主应用。
- **格式上限证据**：`flowable-dmn-xml-converter-8.0.0.jar` 带 `org/flowable/impl/dmn/parser/dmn.xsd`、`DMN12.xsd`、`DMN13.xsd` 与相应 DI schema；`DmnXMLConstants.DMN_NAMESPACE` 为 `https://www.omg.org/spec/DMN/20191111/MODEL/`。与仓库 `FIT_REVIEW_VERIFY.md:60` 的 1.1–1.3 结论吻合。没有本地 1.4–1.6 schema，**本卡未运行解析兼容测试，也不据此宣称完整支持 1.3 全部语义**。不得把 E2 的 1.6 作为默认模型版本。
- **测试入口**：Engine JAR 中 `org.flowable.dmn.engine.test.FlowableDmnTest`、`FlowableDmnExtension`（JUnit Jupiter callbacks/ParameterResolver）、`DmnDeployment.resources`；Spring JAR 中 `FlowableDmnSpringExtension`。API 的 `DmnRepositoryService.createDeployment` 与 builder `addClasspathResource(...).deploy()` 可部署样例；`DmnDecisionService.createExecuteDecisionBuilder()` 与 `ExecuteDecisionBuilder.variables(...).executeDecision()` 可求值。纯语义测试和真实 PostgreSQL 集成测试仍须分别设计，不把测试 API 存在等同测试通过。
- **审计入口**：API JAR 的 `ExecuteDecisionBuilder.executeDecisionWithAuditTrail()`、`DmnHistoryService.createHistoricDecisionExecutionQuery()`；`DmnHistoricDecisionExecution` 有 decisionDefinitionId、deploymentId、decisionVersion、executionJson、起止时刻。AuditContainer 有输入、输出、规则执行集合；RuleExecutionAuditContainer 有规则编号。Engine JAR 的 `org/flowable/dmn/db/create/flowable.postgres.create.dmn.sql:7` 定义 `ACT_DMN_HI_DECISION_EXECUTION` 及 `EXECUTION_JSON_`。metadata 中 history-enabled 默认 true，同时 builder 存在 `disableHistory()`，故必须守护审计不能被调用方关闭，并解决敏感输入最小化与保留期。

只读复核方式：用 Python `zipfile.ZipFile` 读 JAR 指定条目；`javap -classpath <本地JAR> <类>` 读公开 API，`javap -v` 读装配条件，`javap -constants` 读命名空间。无代码反射加载、模型求值或数据库连接。本地依赖子任务的关键结论已由主执行者核对原始 POM、JAR 条目与签名；没有把代理回复直接当证据。

## 6. 本系统的代价、收益与再议条件

| 具体要求/场景 | 收益及当前证据 | 新增代价 | 把握与本轮取舍 |
|---|---|---|---|
| 科目映射由财务逐行审阅 | E8 有复杂旧映射、缺映射拒绝及财务签字需求；目标多维表可能更易解释 | 补目标表、命中策略、类型适配、政策/部署双版本、ledger 引擎隔离方案、差异回放 | 需求高把握；DMN 优于 D15 未证明。先补真表并用同样样例比较 |
| 工时三类路由 | E8 有 3 类确定映射，业务可直接评审 | 即使用 DMN 也要保留实际取人、字段授权、行级审批和候选解析 | 规则存在高把握；3 行用 D15/代码可满足，无即时引擎收益证据 |
| 人力/费用复杂资格或加审 | 多处明确写阈值、类型、版本；可避免政策散在分支里 | 先补完整适用范围/例外，再开发可读编辑、预览和批准；引擎不免除这些工作 | 目标仍待证据，不因“可配置”就定 DMN |
| 历史解释与审计 | Flowable 有规则级轨迹 API；有望降低自行记录命中细节的代码量 | 引擎历史与业务审计关联、不可变归档、清理/权限、敏感 JSON 控制与版本留存 | API 可证；真实保存和回放未测，收益为预期 |
| 升级与交付 | 同属已有 Flowable 候选，可沿现有技术方向研究 | 实际供应方基线与本项目不同；依赖树/许可/漏洞、schema、迁移、备份恢复、冷启动、吞吐与内存验证 | 无性能或人日实测，不能报 ROI 或“零成本集成” |
| 15 点整体迁移 | 只有 DEC-06 属小型明确政策映射；其余仍要原算法/事务/安全代码 | 至少增加模型、输入装配、错误映射、两层测试与追溯；复杂度未消失 | 不推荐整体迁移，高把握 |

以后重新比较时，必须拿到：一张真实批准表（全部条件/输出、负责人、适用业务日及例外）；对应变更记录（记录窗口、版本数、理由，不能用估计频率）；同一套正例、负例、边界、零/多命中及历史回放样例；两个最小实现的修改步骤/耗时记录；业务人员读表、改一项并预览的实际走查结果；依赖与许可核查；在项目实际 Boot/Modulith/Java/PG 组合上的集成与审计测试。比较维度是明确性、修改成本、验证成本与运维成本，不设没有业务依据的“超过 N 条规则就上 DMN”阈值。

为何现在不启用更有价值：真实规则表尚待补齐，候选中的安全/一致性控制又不应迁出；先拿相同业务样本比较，能检验引擎是否真的减少规则维护成本，避免同时试探业务口径与引擎语义。**政策批准、版本化、不追溯、审计、失败告警属于本期需求，不能随引擎延期而延期。** 本卡只研究；这些后续补做须另派卡，不在本卡修改其他文件。

## 7. 条件性选型 ADR 要点（供下一轮使用，不是本次决定）

本轮结论为不启用，故不新建 ADR。若真实样本比较后决定部分引入，可用下列要点起草同一份选型 ADR；不能把本节当作默认批准。

1. **决定**：限定首批决策键、所属 BC、批准规则与模型格式子集；若 Flowable 验证通过，采用经核实版本的嵌入式 DMN，初期只允许无副作用的纯判定。输入由所属 BC 准备，输出为受限枚举/ID；决策不能写库、调组织目录、发网络请求、取当前时刻或直接改变聚合状态。无匹配/多匹配的错误语义由规则表明确，不能返回部分结果或静默默认。
2. **取代/影响**：精确处理 ADR-0041 决定 1 与 ADR-0050 决定 9/19 的延期条款、ADR-0007 决定 1–3 与 ADR-0016 决定 3。科目映射归 ledger，现有 [ArchitectureRulesTest G19](../../../../backend/src/test/java/com/forcitis/one/ArchitectureRulesTest.java):218–223 禁止 approval.infrastructure 之外引用任何 `org.flowable..`；不得偷改守护。ADR 需裁定“纯 DMN 适配放 ledger.infrastructure 的窄例外”或独立技术适配模块及端口；保留科目语义在 ledger，业务模块不认识科目，不把 ledger 业务塞进 approval。两者需要比较后由用户决定。
3. **守护**：引擎类型与 Map 只在批准的 infrastructure 适配器；api/domain 不泄露；政策版本、内容摘要、部署/定义 ID 一一绑定，禁止 latest 与按部署时刻推定生效日；强制业务日/在途版本规则、有效期不重叠和缺版本失败；规则输入只用最小必要字段。审计关联业务 ID、政策版本、模型摘要、命中行/输出、关联号，且不能被 `disableHistory` 绕过；引擎表纳入数据访问注册/分级/保留/归档，模型与数据清理后仍能满足历史解释。
4. **验证**：先以财务批准表跑同一套 D15 与 DMN 样例；边界、缺输入、非法值、零/多命中、优先级、旧版回放、期间首日切换、跨期更正与原分录冲销；模型解析和安全表达式限制；真实模块事务、并发、失败告警、重复投递和部署后仍固定旧版；升级时同样例复跑。任何只通过单元测试的结果都不标运行验收。
5. **迁移**：先建批准初版与来源对照，离线生成/校验模型；同一输入做影子求值，不双写业务结果；差异逐条由负责人裁定，零差异或有明确批准后才切决策键。在途继续旧版，新业务按生效日选新版本。回退由受控开关路由到仍保留的已批准旧实现，禁止重算既有事实；已过账更正走原分录冲销与重做。最终若选择 D15 取代映射 DMN，也要明确取代 ADR-0016 相应措辞，不能长期悬置。

## 8. 待联网核实清单（本卡未联网）

| 编号 | 必须核实的具体问题 | 取得什么证据后才能收口 |
|---|---|---|
| NET-01 | Flowable 当前可选维护版本、8.0.0 安全修复/支持状态及官方制品摘要 | 官方发布/支持记录、目标制品哈希及依赖树；缓存时间和旧文档不能作当前结论 |
| NET-02 | Flowable 目标版本的 DMN 格式、FEEL/表达式子集、命中策略/决策服务限制、XML 安全与编辑器兼容 | 对应标签源码/官方手册、目标模型往返与一致性样例；不能用“有 1.3 schema”替代完整语义支持 |
| NET-03 | Flowable 目标版本的 Boot 4.1.1 / Spring / Java 25 / PostgreSQL 支持矩阵及数据库升级方式 | 官方兼容材料、相同版本组合的本地实测；Modulith 边界由本仓库验证 |
| NET-04 | Flowable DMN、传递依赖、模型编辑器的完整 LICENSE/NOTICE 与商业编辑功能边界 | 对应制品正文、依赖许可清单与组件范围；当前只证明 POM/manifest 的 Apache 声明 |
| NET-05 | Camunda 7：可选/受支持版本、DMN 标准子集、独立嵌入/Boot 入口、Java 25/Boot 4.1.1、测试 API、独立历史、维护终止时间及引擎/编辑器许可 | 官方对应版本文档、源码/POM/LICENSE/NOTICE、支持时间表与最小验证 |
| NET-06 | Camunda 8：DMN 评估组件与平台的版本、独立嵌入是否可用、运行拓扑、客户端/服务端矩阵、离线测试/历史导出、分别适用的自托管/生产/编辑器许可 | 官方组件级材料与许可正文；不能沿用 Camunda 7 的答案 |
| NET-07 | Operaton：实际 DMN 模块版本、DMN 子集、Boot/Java 兼容、嵌入和事务、测试、历史与规则轨迹、维护计划、引擎/编辑器/依赖许可 | 官方标签制品及对应文档/许可与最小验证 |
| NET-08 | Drools：实际 DMN/KIE 制品版本及依赖、DMN/FEEL/命中策略支持、Boot 4.1.1/Java 25、独立测试/TCK、轨迹和持久历史、编辑器及 DMN 制品完整许可 | 官方 DMN 实现制品/源码/文档/许可；本地 BOM 和 parent 不足以替代 |
| NET-09 | 所有候选的 CVE、传递依赖及维护成本、模型跨引擎兼容/迁移边界 | 同一时点的官方公告/组件清单和同一业务样例；不得仅比较宣传功能 |

另外待业务/本地验证而非联网解决的缺口：目标表与批准行数、变更历史、阈值与例外、业务人员编辑需求、D15/DMN 对照试验、运行/审计验收、G19 与 ledger 归属方案。联网核实产品事实不能替代这些证据。

## 9. 逐块清单的阅读方式与验证边界

附录中的 `位置` 为固定提交中的文件行号与 YAML 路径；`when→goto/outcome` 保留每个分支，`otherwise` 也保留。`适合/待证据` 只针对注释写明的政策片段，混合块剩余不变量继续原位置。清单是研究工作表，不登记新 SSOT、术语或规则，不把未定政策升级为决定。

每行的属性码都含频率 F0，并在下面明确业务可读、版本、留痕要求：

| 属性码 | 适配理由 | 业务人员是否需看懂（建议） | 是否需政策版本/留痕（建议） |
|---|---|---|---|
| N0 | 只有 otherwise，单向连接，不构成政策选择 | 需看懂流程即可，无独立表编辑需求 | 无独立政策版本；保留业务动作/所属流程版本；F0 |
| N1 | 人工选择、审批结论或业务事件的编排 | 参与人需看懂职责/路径 | 原审批/流程版本及实际人工结论留痕；F0 |
| N2 | 状态、不变量、授权、幂等、余额或证据校验 | 负责人/审计需看懂拒绝与控制原因 | 控制随批准规格/代码；引用输入配置版本并留结果；F0 |
| N3 | 外部失败/重试、超时、事件竞速或集成状态 | 经办/运维需看懂异常与处置 | 重试参数受控；保留事件/回执/轮次/错误；F0 |
| N4 | 算法、金额/日历运算、主数据/生效版本查询 | 业务需看懂口径/来源 | 参数与主数据版本，不单设 DMN 版本；保留计算/查找依据；F0 |
| D1 | 独立、确定性政策可以表格化 | 政策负责人需读懂、批准；编辑需求尚未实测 | 必须 D15、业务日选版、命中依据与结果；F0 |
| U1 | 有政策形态，但完整行集/阈值/适用范围/确定性待补 | 负责人需读懂并先补政策 | 应版本化并留痕，补齐证据前不执行；F0 |

已验证：本地规则/ADR/源码/构建声明、三组 Git 对象的递归清单、本机缓存与关键签名。未运行：Maven/Gradle、DMN 解析/求值、应用/Modulith 启动、数据库、容器、生产核对、业务编辑走查、性能测试。交付 precheck 的实际输出见 `/tmp/ARCH-DMN-result.md`；它只证明相应仓库检查状态，不证明 DMN 可上线。


## 附录 A. revenue 组完整判断清单

来源 E3：提交 `5b31ba2794e3558f3f70d1ac84c6d2884e1a714e`，文件 `docs/modernization/architecture/proposals/closeout/revenue/processes.revenue.yaml`。72 项流程登记、219 个显式判断块、430 条分支；适合 0、不适合 211、待证据 8。行号为源 YAML 节点起始行，路径索引从 1 开始；每行采用 §9 属性码。

| 判断点 | 固定对象位置 | 分支数 | 条件与去向（完整列举） | 结论 | 原因及属性 |
|---|---|---|---|---|---|
| PRC-03 steps/1/decision | L71 `processes/steps/1` | 2 | 关联往来单位（可无）与市场活动：1. 客户直接邀标或委托，无需线索 → 6；2. otherwise → 2 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-03 steps/3/decision | L104 `processes/steps/3` | 2 | 认领线索：1. 需暂缓跟进 → 4；2. otherwise → 6 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-03 steps/4/decision | L122 `processes/steps/4` | 3 | 挂起线索：1. 线索重启跟进 → 5；2. 线索无价值 → 9；3. otherwise → end / 已挂起 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-03 steps/5/decision | L147 `processes/steps/5` | 1 | 恢复线索：1. otherwise → 6 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-03 steps/6/decision | L164 `processes/steps/6` | 1 | 新建商机前读往来单位档案状态（档案已核实才算正式往来单位，P-3）与拟任负责人：1. otherwise → 7 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-03 steps/7/decision | L187 `processes/steps/7` | 4 | 新建商机：1. 新建商机被拒且不再跟进 → 9〔INV-26〕；2. 新建商机被拒，补资料后重试 → 6〔INV-26〕；3. 没有来源线索（直接邀标或委托） → end / 商机跟进中；4. otherwise → 8 | 不适合（N3） | 异常/到期/事件处理，保留原编排与集成记录；可读/版本/留痕见 N3；F0 |
| PRC-03 steps/8/decision | L222 `processes/steps/8` | 2 | 确认线索：1. 商机负责人或参与团队需调整 → 10；2. otherwise → end / 已转商机 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-03 steps/9/decision | L246 `processes/steps/9` | 1 | 关闭线索：1. otherwise → end / 线索已关闭〔INV-24〕 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-03 steps/10/decision | L269 `processes/steps/10` | 1 | 变更负责人前读新负责人：1. otherwise → 11 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-03 steps/11/decision | L288 `processes/steps/11` | 2 | 变更商机负责人：1. 参与团队需调整 → 12；2. otherwise → end / 商机已维护 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-03 steps/12/decision | L313 `processes/steps/12` | 1 | 维护商机参与团队：1. otherwise → end / 商机已维护 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-07 steps/1/decision | L393 `processes/steps/1` | 1 | 读费率、系数与协议价目的生效版本（INV-32）：1. otherwise → 2 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-07 steps/2/decision | L412 `processes/steps/2` | 2 | 编制报价版本：1. 所引费率或系数版本并非生效版本 → end / 报价未编制〔INV-32〕；2. otherwise → 3 | 不适合（N4） | 运算/生效数据查询或固定口径，保留参数/来源版本；可读/版本/留痕见 N4；F0 |
| PRC-07 steps/3/decision | L434 `processes/steps/3` | 1 | 提交报价评审：1. otherwise → 4 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-07 steps/4/decision | L459 `processes/steps/4` | 3 | 报价评审：1. 审批通过 → 5；2. 申请人已撤回评审 → end / 已撤回；3. otherwise → 6 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-07 steps/5/decision | L504 `processes/steps/5` | 1 | 通过报价评审：1. otherwise → 7 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-07 steps/6/decision | L523 `processes/steps/6` | 2 | 驳回报价评审：1. 客户仍要报价 → 2；2. otherwise → 8 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-07 steps/7/decision | L546 `processes/steps/7` | 1 | 生效报价版本：1. otherwise → end / 报价已生效〔INV-30〕 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-07 steps/8/decision | L569 `processes/steps/8` | 1 | 作废报价版本：1. otherwise → end / 报价已作废 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-08 steps/1/decision | L687 `processes/steps/1` | 1 | 读招标项目登记与联合体成员：1. otherwise → 2 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-08 steps/2/decision | L705 `processes/steps/2` | 3 | 读独立性检查结论（INV-33，D-4 b）：我方为 IDI 风险管理机构且有冲突未获书面同意的，不得递交投标、不得凭委托赢单：1. 直接委托，无招标 → 15；2. 我方为 IDI 风险管理机构且独立性检查有冲突、未获书面同意 → end / 不得投标〔INV-33〕；3. otherwise → 3 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-08 steps/3/decision | L726 `processes/steps/3` | 3 | 登记资格预审结果：1. 资格预审未通过 → 12〔INV-29〕；2. 招标文件要求缴纳投标保证金 → 4；3. otherwise → 6 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-08 steps/4/decision | L754 `processes/steps/4` | 1 | 发起付款：1. otherwise → 5 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-08 steps/5/decision | L789 `processes/steps/5` | 1 | 登记保证金缴纳：1. otherwise → 6 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-08 steps/6/decision | L815 `processes/steps/6` | 1 | P-13 投标截止临近提醒（按招标项目登记中的截止时间），到点提醒商机负责人：1. otherwise → 7 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-08 steps/7/decision | L830 `processes/steps/7` | 5 | 递交投标：1. 评标结果为中标 → 8；2. 评标结果为未中标 → 9；3. 评标阶段被宣布废标 → 10；4. 决定撤回或撤销投标 → 11；5. otherwise → end / 已递交待评标 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-08 steps/8/decision | L875 `processes/steps/8` | 1 | 登记中标：1. otherwise → 13 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-08 steps/9/decision | L895 `processes/steps/9` | 1 | 登记未中标：1. otherwise → 14 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-08 steps/10/decision | L922 `processes/steps/10` | 3 | 登记废标：1. 重新招标，另建投标 → PRC-08 / 已转重新投标；2. 不重新招标且商机丢失 → 17；3. otherwise → 12 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-08 steps/11/decision | L955 `processes/steps/11` | 4 | 撤回或撤销投标：1. 撤回发生在截止前，商机继续跟进 → end / 已撤回；2. 撤回发生在截止前，不再跟进该商机 → 12；3. 撤回发生在截止后，商机继续跟进，投标已撤回 → end / 已撤回；4. otherwise → 12 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-08 steps/12/decision | L993 `processes/steps/12` | 1 | 放弃投标：1. otherwise → 16 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-08 steps/13/decision | L1012 `processes/steps/13` | 1 | 商机赢单：1. otherwise → end / 商机已赢单 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-08 steps/14/decision | L1043 `processes/steps/14` | 1 | 登记商机丢单：1. otherwise → end / 商机已丢单 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-08 steps/15/decision | L1067 `processes/steps/15` | 1 | 商机赢单：1. otherwise → end / 商机已赢单〔INV-27〕 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-08 steps/16/decision | L1101 `processes/steps/16` | 1 | 放弃商机：1. otherwise → end / 商机已放弃 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-08 steps/17/decision | L1127 `processes/steps/17` | 1 | 登记商机丢单：1. otherwise → end / 商机已丢单 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-183 steps/1/decision | L1204 `processes/steps/1` | 1 | 读商机（委托方式、客户投资限额 / 限价）、往来单位资信、独立性检查结论与报价（预计毛利），供判断清单预填：1. otherwise → 2 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-183 steps/2/decision | L1223 `processes/steps/2` | 3 | 登记参投判断：1. 决定放弃 → 5；2. 决定参投且触发异常条件（低于毛利底线、超产能、独立性冲突） → 3；3. otherwise → 4 | 待证据（U1） | 仅评估政策选择片段；完整条目/阈值/例外待补，余额、授权、状态等硬约束继续原守护；可读/版本/留痕见 U1；F0 |
| PRC-183 steps/3/decision | L1244 `processes/steps/3` | 1 | 作出参投决策：1. otherwise → end / 商机已决定参投 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-183 steps/4/decision | L1280 `processes/steps/4` | 1 | 作出参投决策：1. otherwise → end / 商机已决定参投 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-183 steps/5/decision | L1300 `processes/steps/5` | 1 | 放弃商机：1. otherwise → end / 商机已放弃 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-13 steps/1/decision | L1384 `processes/steps/1` | 1 | 编制前读职级（费率条目键：职级 × 薪档 × 城市类别）与协议价目对应的往来单位：1. otherwise → 2 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-13 steps/2/decision | L1406 `processes/steps/2` | 1 | 编制费率版本条目并提交批准；同时发布计费系数（业务线 × 客户分级，Q-07）的一并编制；审批被驳回后退回本步修改（驳回结果以事件「审批已驳回」到达）：1. otherwise → 3 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-13 steps/3/decision | L1434 `processes/steps/3` | 3 | 费率版本批准：1. 审批通过 → 4；2. 申请人已撤回批准申请 → end / 已撤回；3. otherwise → 2 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-13 steps/4/decision | L1471 `processes/steps/4` | 1 | 批准费率版本：1. otherwise → 5 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-13 steps/5/decision | L1490 `processes/steps/5` | 4 | 生效费率版本：1. 生效区间与现有生效版本重叠，生效被拒（区间重叠） → end / 生效被拒（区间重叠）〔INV-35〕；2. 仅发布费率版本，费率版本已生效 → end / 费率版本已生效；3. 同时发布计费系数新版本 → 6；4. otherwise → 7 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-13 steps/6/decision | L1530 `processes/steps/6` | 2 | 生效计费系数版本：1. 同时发布协议价目新版本 → 7；2. otherwise → end / 计费系数版本已生效 | 不适合（N4） | 运算/生效数据查询或固定口径，保留参数/来源版本；可读/版本/留痕见 N4；F0 |
| PRC-13 steps/7/decision | L1558 `processes/steps/7` | 1 | 生效协议价目版本：1. otherwise → end / 协议价目版本已生效 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-16 steps/1/decision | L1642 `processes/steps/1` | 1 | 读商机、项目与依据证据：1. otherwise → 2 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-16 steps/2/decision | L1669 `processes/steps/2` | 1 | 申请风险交付授权：1. otherwise → 3 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-16 steps/3/decision | L1695 `processes/steps/3` | 3 | 授权审批：1. 审批通过 → 5；2. 申请人已撤回授权申请 → end / 已撤回；3. otherwise → 4 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-16 steps/4/decision | L1737 `processes/steps/4` | 1 | 审批驳回结果回调；授权不生效，不产生先行投入依据：1. otherwise → end / 授权未获批准 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-16 steps/5/decision | L1758 `processes/steps/5` | 3 | 批准风险交付授权：1. 合同已签回 → 6；2. 授权期限届满或不再签约 → 7；3. otherwise → end / 已批准 | 不适合（N3） | 异常/到期/事件处理，保留原编排与集成记录；可读/版本/留痕见 N3；F0 |
| PRC-16 steps/6/decision | L1790 `processes/steps/6` | 1 | 转正风险交付授权：1. otherwise → end / 已转正〔INV-19〕 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-16 steps/7/decision | L1813 `processes/steps/7` | 1 | 叫停风险交付授权：1. otherwise → end / 已叫停〔INV-19〕 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-17 steps/2/decision | L1925 `processes/steps/2` | 2 | 建立项目：1. 承担法人只有一个且承担部门、负责人已确定 → 3〔intent 134134 Q-06〕；2. otherwise → end / 未建立 | 不适合（N4） | 运算/生效数据查询或固定口径，保留参数/来源版本；可读/版本/留痕见 N4；F0 |
| PRC-18 steps/1/decision | L2088 `processes/steps/1` | 2 | 编制交付计划版本：1. 本版本是对客基线（需发包人批准） → 2〔INV-43〕；2. otherwise → 3 | 不适合（N4） | 运算/生效数据查询或固定口径，保留参数/来源版本；可读/版本/留痕见 N4；F0 |
| PRC-18 steps/2/decision | L2107 `processes/steps/2` | 2 | 批准交付计划：1. 发包人书面提出修改意见 → 1；2. otherwise → end / 已批准（对客） | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-18 steps/3/decision | L2151 `processes/steps/3` | 1 | 批准交付计划：1. otherwise → end | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-19 steps/3/decision | L2269 `processes/steps/3` | 3 | 项目预算审批：1. 审批通过 → 4；2. 提交人已撤回审批 → end / 已撤回；3. otherwise → 2 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-19 steps/4/decision | L2317 `processes/steps/4` | 1 | 锁定项目预算：1. otherwise → end〔INV-44〕 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-20 steps/3/decision | L2430 `processes/steps/3` | 3 | 确认资源分配：1. 分配覆盖需求的全部时段与工时 → 4；2. 需求不再需要（范围变更、改外协） → 5；3. otherwise → 1 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-20 steps/4/decision | L2471 `processes/steps/4` | 1 | 标记资源需求已满足：1. otherwise → end / 已满足 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-20 steps/5/decision | L2482 `processes/steps/5` | 1 | 关闭资源需求：1. otherwise → end / 已关闭 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-23 steps/2/decision | L2610 `processes/steps/2` | 5 | 提交工时单：1. 工时所属期间已关账（提示改走冲销或修正，不设审批） → end / 未提交〔INV-67〕；2. 所记项目已暂停、已结项、已终止或已取消 → end / 未提交〔INV-39〕；3. 所记工作包当天无权使用 → end / 未提交〔INV-66〕；4. 当日工时低于最低时数（已扣减请假） → end / 未提交〔INV-65〕；5. otherwise → 3 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-23 steps/3/decision | L2668 `processes/steps/3` | 4 | 工时审批：1. 审批通过 → 4；2. 审批人退回修改 → 5；3. 员工撤回本轮或取消审批 → 7 / 已撤回；4. otherwise → 6 / 已驳回 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-23 steps/4/decision | L2721 `processes/steps/4` | 1 | 通过工时单：1. otherwise → 8 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-23 steps/5/decision | L2739 `processes/steps/5` | 1 | 退回工时单：1. otherwise → 2 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-23 steps/6/decision | L2760 `processes/steps/6` | 1 | 驳回工时单：1. otherwise → end | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-23 steps/7/decision | L2782 `processes/steps/7` | 1 | 撤回工时单：1. otherwise → end | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-23 steps/8/decision | L2802 `processes/steps/8` | 3 | 提交过账候选：1. 目标期间已关账（路由当前开放期间并标原期间，不拒绝） → end / 已通过〔INV-92〕；2. 缺锁定费率版本（失败并告警，工时单状态不变） → end / 已通过〔INV-37〕；3. otherwise → end / 已通过 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-24 steps/1/decision | L2909 `processes/steps/1` | 4 | 暂停项目：1. 暂停原因消除且继续履行 → 2；2. 不再继续履行且已有投入 → 3；3. 不再继续履行且无任何投入 → 4；4. otherwise → end / 已暂停 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-24 steps/2/decision | L2944 `processes/steps/2` | 1 | 恢复项目：1. otherwise → end | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-24 steps/3/decision | L2965 `processes/steps/3` | 1 | 终止项目：1. otherwise → end | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-24 steps/4/decision | L2994 `processes/steps/4` | 1 | 取消项目：1. otherwise → end | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-26 steps/3/decision | L3121 `processes/steps/3` | 2 | 确认发包人资料：1. 资料合格 → end；2. otherwise → 4 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-26 steps/4/decision | L3150 `processes/steps/4` | 2 | 退回发包人资料：1. 缺陷资料影响进度且需发包人答复延期 → PRC-32；2. otherwise → 2 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-28 steps/3/decision | L3315 `processes/steps/3` | 2 | 登记内部评审结论：1. 各级校审通过 → 4〔INV-46〕；2. otherwise → 2 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-28 steps/4/decision | L3352 `processes/steps/4` | 3 | 签发交付物：1. 签发人执业证书有效且专业、等级符合 → 5〔INV-47〕；2. 签发人执业证书无效或专业、等级不符，改派持证签发人 → 4〔INV-47〕；3. otherwise → end / 未签发 | 待证据（U1） | 仅评估政策选择片段；完整条目/阈值/例外待补，余额、授权、状态等硬约束继续原守护；可读/版本/留痕见 U1；F0 |
| PRC-28 steps/5/decision | L3389 `processes/steps/5` | 4 | 登记提交轮次送达：1. 客户书面认可（签收单、审查同意意见、邮件确认、阶段签认等） → 6；2. 客户书面不同意并退回修改 → 7；3. 合同约定审查期且期满前未收到书面异议 → 8〔ADR-0047 第 5 条第 1 点〕；4. otherwise → 5 | 不适合（N3） | 异常/到期/事件处理，保留原编排与集成记录；可读/版本/留痕见 N3；F0 |
| PRC-28 steps/6/decision | L3437 `processes/steps/6` | 2 | 登记客户认可：1. 存在阻断性不符合项，登记被拒 → end / 认可登记被拒〔INV-48〕；2. otherwise → end / 已认可〔INV-48〕 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-28 steps/7/decision | L3484 `processes/steps/7` | 1 | 登记客户退回：1. otherwise → 2 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-28 steps/8/decision | L3511 `processes/steps/8` | 1 | 登记项目经理的无异议声明：1. otherwise → end / 已声明无异议 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-30 steps/2/decision | L3618 `processes/steps/2` | 2 | 批准完工估算：1. 批准 → end / 已批准；2. otherwise → 1 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-31 steps/2/decision | L3736 `processes/steps/2` | 2 | 认定变更归因与影响：1. 归因与影响已填写 → 3〔INV-54〕；2. otherwise → 2 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-31 steps/3/decision | L3756 `processes/steps/3` | 2 | 内审设计变更：1. 内审通过 → 4；2. otherwise → 1 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-31 steps/4/decision | L3785 `processes/steps/4` | 2 | 发出设计变更：1. 发包人原因且增加工作量 → 5；2. otherwise → 6 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-31 steps/5/decision | L3822 `processes/steps/5` | 1 | 标记待主张：1. otherwise → 6 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-31 steps/6/decision | L3837 `processes/steps/6` | 1 | 关闭设计变更：1. otherwise → end / 已关闭 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-31 steps/7/decision | L3854 `processes/steps/7` | 1 | 撤销设计变更：1. otherwise → end / 已撤销 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-32 steps/2/decision | L3958 `processes/steps/2` | 3 | 提交延期详细说明：1. 发包人书面批准 → 3；2. 发包人书面拒绝 → 4；3. otherwise → 5 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-32 steps/3/decision | L4006 `processes/steps/3` | 1 | 登记延期批准：1. otherwise → end | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-32 steps/4/decision | L4028 `processes/steps/4` | 1 | 登记延期拒绝：1. otherwise → end | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-32 steps/5/decision | L4050 `processes/steps/5` | 1 | 登记延期视为批准：1. otherwise → end | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-32 steps/6/decision | L4068 `processes/steps/6` | 1 | 撤回延期通知：1. otherwise → end / 已撤回 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-34 steps/2/decision | L4165 `processes/steps/2` | 3 | 确认不符合项：1. 不符合项造成返工 → 3；2. 客户书面确认让步接受 → 5〔INV-49〕；3. otherwise → 6 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-34 steps/3/decision | L4200 `processes/steps/3` | 2 | 认定返工：1. 返工认定被撤销 → 4；2. otherwise → 6 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-34 steps/4/decision | L4228 `processes/steps/4` | 1 | 撤销返工认定：1. otherwise → 6 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-34 steps/5/decision | L4246 `processes/steps/5` | 1 | 让步接受不符合项：1. otherwise → 6 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-34 steps/6/decision | L4269 `processes/steps/6` | 2 | 关闭不符合项：1. 整改（含返工）复核未通过 → 2；2. otherwise → end / 不符合项已关闭 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-36 steps/4/decision | L4421 `processes/steps/4` | 2 | 拒绝项 3 条（一、已认可成果均被收入确认行引用为证据或已登记不确认收入的结论，结论载体随 Q20；二、供方协议均已结算或已终止或已登记结算结论；三、已发布交付物与证据均已封存）；预警项 2 条（工时单未全部审批或退回未处理；无生效协议且无风险交付授权，P-16），预警只留痕不拦截：1. 三项拒绝检查均满足 → 5〔INV-40〕；2. otherwise → end / 检查未通过〔INV-40〕 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-36 steps/5/decision | L4474 `processes/steps/5` | 3 | 提交结项申请：1. 审批通过 → 6；2. 提交人已撤回结项申请 → end / 已撤回；3. otherwise → 3 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-37 steps/3/decision | L4663 `processes/steps/3` | 2 | 评分满意度调查：1. 评分低于阈值（阈值待定） → 4〔P-45〕；2. otherwise → 5 | 待证据（U1） | 仅评估政策选择片段；完整条目/阈值/例外待补，余额、授权、状态等硬约束继续原守护；可读/版本/留痕见 U1；F0 |
| PRC-37 steps/4/decision | L4686 `processes/steps/4` | 1 | 登记风险与问题：1. otherwise → 5 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-38 steps/2/decision | L4786 `processes/steps/2` | 2 | 评审项目复盘：1. 评审通过 → 3〔INV-112〕；2. otherwise → 1 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-41 steps/1/decision | L4893 `processes/steps/1` | 2 | 读协议与受检部位；协议范围不含 IDI 检查服务的，不建计划：1. 协议范围含 IDI 检查服务 → 2；2. otherwise → end / 无需检查计划 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-41 steps/4/decision | L4955 `processes/steps/4` | 2 | P-28 到期提醒与逾期未委托提醒，读模型派生，不存状态；由人工转线索，上游不订阅下游事件（符合性审查 建-12）。提醒提前量与逾期提醒周期为配置参数，P30D 为经验估计：1. 协议到期或客户不再委托 → 5；2. otherwise → end / 计划已生效 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-41 steps/5/decision | L4979 `processes/steps/5` | 1 | 标记不再受托：1. otherwise → end / 不再受托 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-43 steps/3/decision | L5117 `processes/steps/3` | 2 | 指派检查人员：1. 我方为 TIS 且负责人兼项数超过上限 → 3〔INV-56〕；2. otherwise → 4 | 待证据（U1） | 仅评估政策选择片段；完整条目/阈值/例外待补，余额、授权、状态等硬约束继续原守护；可读/版本/留痕见 U1；F0 |
| PRC-43 steps/6/decision | L5179 `processes/steps/6` | 3 | 提交检查记录：1. 有检查项既无结果也无未检原因 → 6〔INV-55〕；2. 有判定结果的检查项未引用任何证据或未检项未选原因类型 → 4〔拟规则-VS03-01〕；3. otherwise → 7 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-43 steps/7/decision | L5210 `processes/steps/7` | 2 | 审核检查记录：1. 检查项结果不被证据支撑或未检原因不充分 → 6；2. otherwise → 8 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-43 steps/10/decision | L5268 `processes/steps/10` | 1 | 关联检查报告：1. otherwise → end / 已锁定 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-44 steps/3/decision | L5405 `processes/steps/3` | 2 | 复核不符合项：1. 复核认定发现不成立 → 7；2. otherwise → 4 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-44 steps/4/decision | L5438 `processes/steps/4` | 2 | 发布不符合项：1. 未引用任何证据 → 2〔INV-50〕；2. otherwise → 5 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-44 steps/5/decision | L5467 `processes/steps/5` | 2 | 把已发布的发现与处置建议书面送达委托方（保险公司、业主等）并取得回执；重大险情当场电话或书面报告并留痕：1. 不符合项属客户资产且需第三方检测佐证 → 6；2. otherwise → end / 已发布 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-44 steps/6/decision | L5492 `processes/steps/6` | 1 | 建议外部检测：1. otherwise → end / 已发布并建议外部检测 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-44 steps/7/decision | L5509 `processes/steps/7` | 1 | 关闭不符合项：1. otherwise → end / 已关闭 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-45 steps/5/decision | L5688 `processes/steps/5` | 4 | 复查检查：1. 客户书面接受风险 → 8；2. 确认不再跟踪 → 9；3. 复查未通过 → 7；4. otherwise → 6 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-45 steps/6/decision | L5726 `processes/steps/6` | 2 | 闭合不符合项：1. 未引用复查或整改证据，或复查证据早于整改申报日 → 3〔INV-50〕；2. otherwise → end / 已闭合 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-45 steps/7/decision | L5758 `processes/steps/7` | 1 | 退回整改：1. otherwise → 2 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-45 steps/8/decision | L5780 `processes/steps/8` | 1 | 登记客户接受风险：1. otherwise → end / 客户书面接受风险 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-45 steps/9/decision | L5806 `processes/steps/9` | 1 | 停止跟踪不符合项：1. otherwise → end / 不再跟踪 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-46 steps/3/decision | L5924 `processes/steps/3` | 2 | 登记外部检测报告：1. 报告出具机构为业主委托机构且内容完整 → 4〔P-30〕；2. otherwise → 5 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-46 steps/4/decision | L5953 `processes/steps/4` | 1 | 采信外部检测报告：1. otherwise → end / 已采信 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-46 steps/5/decision | L5973 `processes/steps/5` | 1 | 不采信外部检测报告：1. otherwise → end / 未采信 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-47 steps/5/decision | L6154 `processes/steps/5` | 3 | 合同评审：1. 审批通过 → 6；2. 提交人撤回评审 → end / 已撤回；3. otherwise → 2 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-47 steps/6/decision | L6208 `processes/steps/6` | 3 | 回签：1. 我方为风险管理机构且独立性检查有冲突、无书面同意 → end / 签约被拒〔INV-33〕；2. 客户拒绝回签，或依法招标项目与中标承诺实质背离 → 2〔INV-05〕；3. otherwise → end | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-48 steps/2/decision | L6361 `processes/steps/2` | 2 | 提出协议变更：1. 现场签证事项 → 7；2. otherwise → 3 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-48 steps/4/decision | L6389 `processes/steps/4` | 3 | 协议变更审批：1. 审批通过 → 5；2. 提出人撤回变更 → end / 已撤回；3. otherwise → end / 变更已驳回 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-48 steps/5/decision | L6444 `processes/steps/5` | 2 | 登记发包人书面同意：1. 发包人拒绝，或逾期未答复 → end / 变更未获发包人同意；2. otherwise → 6 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-48 steps/6/decision | L6483 `processes/steps/6` | 1 | 生效协议变更：1. otherwise → 8 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-48 steps/7/decision | L6505 `processes/steps/7` | 2 | 确认现场签证：1. 发包人代表拒签，或逾期未签认 → end / 签证被拒签；2. otherwise → 8 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-49 steps/2/decision | L6638 `processes/steps/2` | 3 | 提出索赔：1. 对方认定索赔 → 3；2. 对方拒绝，或我方撤回 → 4；3. otherwise → end / 已提交待答复 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-49 steps/3/decision | L6677 `processes/steps/3` | 1 | 认定索赔：1. otherwise → end | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-49 steps/4/decision | L6698 `processes/steps/4` | 1 | 拒绝或撤回索赔：1. otherwise → end | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-50 steps/1/decision | L6771 `processes/steps/1` | 2 | 占用框架限额：1. 占用被拒 → end；2. otherwise → 2 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-50 steps/2/decision | L6794 `processes/steps/2` | 3 | 提交任务订单：1. 审批通过 → end / 任务订单已签署生效；2. 提交人撤回订单 → 3；3. otherwise → 3 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-54 steps/1/decision | L6944 `processes/steps/1` | 2 | P-21；读合同审查期参数与送达日，算期满日；同一交付物已有上一轮未结待办的（新一轮送达），先作废旧待办：1. 同一交付物已有上一轮未结的期满待办（重新报送后新送达） → 2；2. otherwise → 3 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-54 steps/2/decision | L6976 `processes/steps/2` | 1 | 作废上一轮的期满待办与催办（旧轮次的声明与期满不再有效），以新送达日重新计期：1. otherwise → 3 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-54 steps/3/decision | L6985 `processes/steps/3` | 2 | 事件网关二选一：到期定时与客户退回（AcceptanceReturned）谁先到谁生效；到期前收到退回则取消，不再认定（ADR-0047 第 5 条第 5 点）：1. 期满前先收到客户退回 → 4〔ADR-0047 第 5 条第 5 点〕；2. otherwise → 5 | 不适合（N3） | 异常/到期/事件处理，保留原编排与集成记录；可读/版本/留痕见 N3；F0 |
| PRC-54 steps/4/decision | L7006 `processes/steps/4` | 1 | 退回后取消期满待办；退回事件的订阅反应是读检不是命令（G-19）：1. otherwise → end / 待办已取消 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-54 steps/5/decision | L7018 `processes/steps/5` | 3 | 核对三个条件：期满、交付侧已有无异议声明、该轮未被退回（ADR-0049 决定 2）；声明日不得早于期满日：1. 声明日早于期满日 → end / 视为同意未成立〔ADR-0049 决定 1、5〕；2. 期满时已有无异议声明且该轮未被退回 → 7；3. otherwise → 6 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-54 steps/6/decision | L7038 `processes/steps/6` | 2 | 期满仍无声明：催办项目经理登记无异议声明，并向阶段门提示预警；催办期内登记声明后回到第 5 步重新核对，催办期满仍无声明则本轮不成立（之后登记声明会因「无异议已声明」重新触发）：1. 催办期内收到无异议已声明 → 5；2. otherwise → end / 视为同意未成立 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-54 steps/7/decision | L7069 `processes/steps/7` | 2 | 认定视为同意成立：1. 认定时发现该轮已被退回 → end / 视为同意未成立；2. otherwise → 8 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-57 steps/3/decision | L7249 `processes/steps/3` | 4 | 开票申请准入：1. 协议未生效且无预收例外依据 → 5〔INV-71；RULE-407；RULE-411〕；2. 开票金额超过付款条件项可开票额度 → 5〔INV-21〕；3. 开票金额超过单据类型阈值，或购买方为关联方 → 4〔AR-01、AR-02、AR-07〕；4. otherwise → 6 | 待证据（U1） | 仅评估政策选择片段；完整条目/阈值/例外待补，余额、授权、状态等硬约束继续原守护；可读/版本/留痕见 U1；F0 |
| PRC-57 steps/4/decision | L7275 `processes/steps/4` | 3 | 开票申请审批：1. 审批通过 → 6；2. 提交人撤回开票申请 → end / 已撤回；3. otherwise → 5 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-57 steps/5/decision | L7329 `processes/steps/5` | 1 | 驳回开票申请：1. otherwise → end / 已驳回 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-57 steps/6/decision | L7351 `processes/steps/6` | 4 | 开具销项发票并改写 WIP：1. 税务平台暂时不可用或应答超时（可重试） → 6；2. 税务平台业务拒绝（购买方税号错误、开票信息不全等） → 2；3. 申请人或财务在开具前主动作废申请 → 7；4. otherwise → 8 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-57 steps/7/decision | L7415 `processes/steps/7` | 1 | 作废开票申请：1. otherwise → end | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-57 steps/8/decision | L7434 `processes/steps/8` | 2 | 提交过账候选：1. 原期间所在年度已年结 → end / 暂挂待跨年处理〔ADR-0045 U-2（待定）〕；2. otherwise → end / 已开具 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-58 steps/1/decision | L7538 `processes/steps/1` | 3 | 红冲审批：1. 审批通过 → 2；2. 提交人撤回红冲申请 → end / 已撤回；3. otherwise → end / 红冲被驳回 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-58 steps/2/decision | L7598 `processes/steps/2` | 4 | 发起红字信息确认：1. 蓝字发票尚未用途确认或入账 → 3；2. 购买方在 72 小时内未确认，税务平台自动作废 → end / 红字确认已作废；3. 财务撤销红字信息确认 → end / 已撤回；4. otherwise → 3 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-58 steps/5/decision | L7689 `processes/steps/5` | 2 | 提交过账候选：1. 原期间所在年度已年结 → end / 暂挂待跨年处理〔ADR-0045 U-2（待定）〕；2. otherwise → end / 已红冲 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-60 steps/2/decision | L7814 `processes/steps/2` | 5 | 收款认领：1. 流水行无法对应任何应收 → 7；2. 付款方不是合同相对方且无委托付款函 → end / 认领被拒〔AR-09；RULE-410〕；3. 付款方不是合同相对方，附委托付款函（第三方代付） → 3〔AR-15〕；4. 收款方式为商业汇票（收票，尚未到账） → end / 已收票待兑付；5. otherwise → 4 | 不适合（N3） | 异常/到期/事件处理，保留原编排与集成记录；可读/版本/留痕见 N3；F0 |
| PRC-60 steps/3/decision | L7854 `processes/steps/3` | 2 | 第三方代付认领审批：1. 审批通过 → 4；2. otherwise → end / 认领被拒 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-60 steps/4/decision | L7904 `processes/steps/4` | 2 | 在应收上核销占用：1. 到账金额大于该笔应收未结余额，还有其他应收可核销 → 4；2. otherwise → 5 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-60 steps/6/decision | L7948 `processes/steps/6` | 2 | 提交过账候选：1. 原期间所在年度已年结 → end / 暂挂待跨年处理〔ADR-0045 U-2（待定）〕；2. otherwise → end / 已核销 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-60 steps/7/decision | L7972 `processes/steps/7` | 3 | 处置待认领流水：1. 联系到付款方并确认对应应收 → 2；2. 确认为误付或无归属，需退回付款方 → end / 待退回；3. otherwise → end / 待认领 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-61 steps/1/decision | L8087 `processes/steps/1` | 2 | 检测应收逾期：1. 无逾期应收 → end / 无逾期；2. otherwise → 2 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-61 steps/2/decision | L8109 `processes/steps/2` | 2 | 登记争议与处置分类：1. 确认款项无法收回 → 3；2. otherwise → end / 已登记处置 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-61 steps/3/decision | L8136 `processes/steps/3` | 3 | 坏账核销审批：1. 审批通过 → 4；2. 提交人撤回核销申请 → end / 已登记处置；3. otherwise → end / 坏账核销被驳回 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-61 steps/5/decision | L8218 `processes/steps/5` | 2 | 提交过账候选：1. 原期间所在年度已年结 → end / 暂挂待跨年处理〔ADR-0045 U-2（待定）〕；2. otherwise → end / 坏账已核销 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-63 steps/1/decision | L8301 `processes/steps/1` | 4 | 登记商票兑付：1. 到期日已过仍未收到兑付款 → 2；2. 到期前已贴现或背书转让（商票不再由本公司持有） → end / 商票已贴现或背书转让；3. 原期间所在年度已年结 → end / 暂挂待跨年处理〔ADR-0045 U-2（待定）〕；4. otherwise → end / 已兑付 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-66 steps/4/decision | L8481 `processes/steps/4` | 5 | 确认最终价款：1. 未能确认，修订结算书后再谈 → 3；2. 未能确认，转索赔 → PRC-49 / 转索赔；3. 未能确认，转争议处理（诉讼或仲裁另行办理） → end / 转争议处理；4. 最终价款低于协议价 → 5；5. otherwise → 6 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-66 steps/5/decision | L8523 `processes/steps/5` | 3 | 最终价款让价审批：1. 审批通过 → 6；2. 提交人撤回 → end / 已撤回；3. otherwise → 3 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-66 steps/6/decision | L8582 `processes/steps/6` | 1 | 结算协议：1. otherwise → end | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-69 steps/1/decision | L8688 `processes/steps/1` | 3 | 申请往来单位角色：1. 申请的角色为客户（客户角色不走准入） → end / 无需准入〔INV-02〕；2. 该供方类型无需资质 → 3；3. otherwise → 2 | 待证据（U1） | 仅评估政策选择片段；完整条目/阈值/例外待补，余额、授权、状态等硬约束继续原守护；可读/版本/留痕见 U1；F0 |
| PRC-69 steps/2/decision | L8716 `processes/steps/2` | 2 | 登记资质证书：1. 资质核验未通过 → end / 准入未通过；2. otherwise → 3 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-69 steps/3/decision | L8743 `processes/steps/3` | 3 | 核准往来单位角色合格：1. 合作期间资质撤销或到期未延续 → 4；2. 合作终止或严重失信 → 5；3. otherwise → end / 往来单位角色合格 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-69 steps/4/decision | L8778 `processes/steps/4` | 2 | 暂停往来单位角色：1. 暂停原因消除 → 3；2. otherwise → end / 角色已暂停 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-69 steps/5/decision | L8802 `processes/steps/5` | 1 | 停用往来单位角色：1. otherwise → end / 角色已停用〔INV-02〕 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-78 steps/1/decision | L8888 `processes/steps/1` | 1 | 发起核查前读持证法人主体：1. otherwise → 2 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-78 steps/2/decision | L8905 `processes/steps/2` | 1 | 发起资质核查：1. otherwise → 3 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-78 steps/3/decision | L8923 `processes/steps/3` | 4 | 登记核查结果：1. 核查结论为撤销，资质已撤销 → end / 资质已撤销；2. 核查结论为降级，资质已降级 → end / 资质已降级；3. 核查结论为有效且无需延续，核查通过 → end / 核查通过；4. otherwise → 4 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-78 steps/4/decision | L8959 `processes/steps/4` | 1 | 向发证机关办理延续申请并取得受理回执，新证书取得后再登记（登记见第 5 步）：1. otherwise → 5 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-78 steps/5/decision | L8983 `processes/steps/5` | 2 | 延续资质：1. 延续获批准并已取得新有效期 → end / 资质已延续；2. otherwise → 6 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-78 steps/6/decision | L9010 `processes/steps/6` | 1 | 标记资质已到期：1. otherwise → end / 资质已到期 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-86 steps/1/decision | L9105 `processes/steps/1` | 5 | 读借款人与其未结借款；借款规则：有未结借款不得再借、单笔不超限额（拟规则卡）。发放、归还、手工冲抵、到期催还是四种触发，在此分流：1. 本次触发为出纳认领员工归还流水 → 6；2. 本次触发为财务手工冲抵 → 9；3. 本次触发为借款到期检测 → 10；4. 借款人有未结借款，或申请额超过单笔限额 → end / 借款被拒〔拟规则卡（借款限额与未结余额）〕；5. otherwise → 2 | 待证据（U1） | 仅评估政策选择片段；完整条目/阈值/例外待补，余额、授权、状态等硬约束继续原守护；可读/版本/留痕见 U1；F0 |
| PRC-86 steps/2/decision | L9136 `processes/steps/2` | 3 | 员工借款审批：1. 审批通过 → 3；2. 借款人撤回申请 → end / 已撤回；3. otherwise → end / 借款被拒 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-86 steps/5/decision | L9236 `processes/steps/5` | 2 | 提交过账候选：1. 原期间所在年度已年结 → end / 暂挂待跨年处理〔ADR-0045 U-2（待定）〕；2. otherwise → end / 借款已发放 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-86 steps/8/decision | L9300 `processes/steps/8` | 2 | 提交过账候选：1. 原期间所在年度已年结 → end / 暂挂待跨年处理〔ADR-0045 U-2（待定）〕；2. otherwise → end / 借款已结清 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-86 steps/9/decision | L9326 `processes/steps/9` | 1 | 冲抵员工借款：1. otherwise → 8 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-86 steps/10/decision | L9346 `processes/steps/10` | 1 | 借款到期仍有未结余额：提醒借款人与财务催还；逾期超过期限的登记争议与处置（PRC-61 同口径），不从工资代扣（Q24，工资支付暂行规定第 15、16 条；审题 VS04-22）：1. otherwise → end / 已催还 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-140 steps/1/decision | L9430 `processes/steps/1` | 3 | 借阅人选已归档协议，在审批载荷中写借阅目的、借阅形式（原件 / 复印件）与预计归还日；审批人为借阅人所在部门负责人（待用户定）：1. 审批通过 → 2；2. 借阅人撤回申请 → end / 已撤回；3. otherwise → end / 借阅申请被驳回 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-140 steps/2/decision | L9484 `processes/steps/2` | 2 | 登记协议借出：1. 协议未归档，或同一份原件已有未归还的借出 → end / 借出被拒〔INV-125〕；2. otherwise → 3 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-140 steps/3/decision | L9517 `processes/steps/3` | 2 | 登记协议归还：1. 超过预计归还日仍未归还 → 4；2. otherwise → end | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-140 steps/4/decision | L9547 `processes/steps/4` | 1 | 标记协议借阅逾期：1. otherwise → 3 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-141 steps/2/decision | L9625 `processes/steps/2` | 2 | 归档协议：1. 原件份数或签章不全 → 1〔INV-124〕；2. otherwise → end / 已归档 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-149 steps/1/decision | L9712 `processes/steps/1` | 4 | 读原收入确认事实、累计确认与分摊价格，以及协议结算或终止状态；事件触发时先算差额。事件触发时由系统生成冲销申请，不经第 2 步人工登记：冲销额＝累计确认 − 最终价款（协议已结算）或终止后不再成立的已确认部分（协议已终止，按协议终止依据确定）；原因取「结算审减」或「协议终止」，引用协议结算或终止记录：1. 协议已结算且最终价款高于累计确认（差额为正） → PRC-53 / 转补充确认；2. 协议已结算且最终价款不低于累计确认，或协议终止后已确认部分仍成立 → end / 无需冲销；3. 协议已结算（差额为负）或协议已终止（已确认部分不再成立） → 3；4. otherwise → 2 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-149 steps/2/decision | L9748 `processes/steps/2` | 2 | 冲销收入确认：1. 冲销额超过原确认事实的未冲销余额 → end / 冲销被拒〔拟规则 REV-R2（冲销不超原确认）〕；2. otherwise → 3 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-149 steps/3/decision | L9770 `processes/steps/3` | 2 | 占用冲销额度：1. 在途冲销与已冲销合计超过原确认事实 → end / 冲销被拒〔拟规则 REV-R2（冲销不超原确认）〕；2. otherwise → 4 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-149 steps/4/decision | L9787 `processes/steps/4` | 3 | 收入冲销审批：1. 审批通过 → 5；2. 提交人撤回冲销申请 → 7；3. otherwise → 7 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-149 steps/5/decision | L9842 `processes/steps/5` | 2 | 追加冲销行并占用转实：1. 二次校验失败（原确认事实已被其他冲销占满） → 7〔拟规则 REV-R2（冲销不超原确认）〕；2. otherwise → 6 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-149 steps/6/decision | L9866 `processes/steps/6` | 2 | 提交过账候选：1. 原期间所在年度已年结 → end / 暂挂待跨年处理〔ADR-0045 U-2（待定）〕；2. otherwise → end / 收入已冲销 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-149 steps/7/decision | L9890 `processes/steps/7` | 1 | 释放收入额度占用：1. otherwise → end / 冲销已驳回 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-151 steps/1/decision | L9974 `processes/steps/1` | 2 | 填报产值估计：1. 被抽查（按抽查比例规则配置） → 2；2. otherwise → end / 产值已填报 | 待证据（U1） | 仅评估政策选择片段；完整条目/阈值/例外待补，余额、授权、状态等硬约束继续原守护；可读/版本/留痕见 U1；F0 |
| PRC-151 steps/2/decision | L10006 `processes/steps/2` | 2 | 部门负责人抽查产值估计，对照完工估算与交付进度判断合理性；仅读取并留意见，不设审批；发现偏差通知项目经理更正后重填：1. 发现明显偏差，通知项目经理更正 → 1；2. otherwise → end / 产值已抽查 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-152 steps/1/decision | L10096 `processes/steps/1` | 3 | 读付款条件项（类型、条款、状态）。条件类型：预付（签约后）、施工图审查通过、工程竣工验收、按月或按季度、结算审定、缺陷责任期届满（质保金）；成果认可型由 PRC-56 处理，不在此：1. 付款条件项已是可结算 → end / 条件已是可结算；2. 时间型节点到期日已届 → 3；3. otherwise → 2 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-152 steps/2/decision | L10119 `processes/steps/2` | 2 | 登记付款条件达成：1. 证据缺失，或证据类型与条件类型不符 → end / 登记被拒〔拟规则 PAY-R1（条件类型 → 证据类型对照）〕；2. otherwise → 3 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-153 steps/1/decision | L10245 `processes/steps/1` | 2 | 订阅 PRC-08 发出的投标未中标、已废标、已撤销（S-42 接缝）；读投标保证金与投标结果；退还与没收互斥各只一次（INV-20），已退或已没收的不再退还：1. 保证金未缴，或已退还、已没收 → end / 退还被拒〔INV-20〕；2. otherwise → 2 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-153 steps/2/decision | L10279 `processes/steps/2` | 2 | 等待招标人退款到账；约定期限内未到账则提醒（P-10：签约后 5 日未退）并转催退：1. 超过约定退还期限仍未到账 → 3；2. otherwise → 4 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-153 steps/3/decision | L10310 `processes/steps/3` | 2 | 商务向招标人催退；招标人明确不退或没收的，转没收登记（PRC-10）：1. 招标人明确不予退还或没收 → end / 转没收登记；2. otherwise → 2 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-153 steps/5/decision | L10348 `processes/steps/5` | 2 | 提交过账候选：1. 原期间所在年度已年结 → end / 暂挂待跨年处理〔ADR-0045 U-2（待定）〕；2. otherwise → end / 保证金已退 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-53 steps/1/decision | L10487 `event_policies/steps/1` | 2 | 占用收入额度：1. 证据类型未开放、证据不全，或额度不足 → end / 占用被拒〔INV-23；INV-22；INV-86〕；2. otherwise → 2 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-53 steps/2/decision | L10526 `event_policies/steps/2` | 1 | 收入确认审批：1. otherwise → PRC-148 / 审批已发起 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-91 steps/2/decision | L10736 `event_policies/steps/2` | 3 | 认定工时有效性：1. 目标期间已关账（提示改走冲销或修正，人工认定不设审批） → end / 未认定〔INV-67〕；2. 四类之和不等于填报总数 → end / 未认定〔INV-53〕；3. otherwise → end / 已认定 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-147 steps/1/decision | L10846 `event_policies/steps/1` | 3 | 冲抵员工借款：1. 员工无未结借款，冲抵额为零 → 3；2. 借款所属法人与报销承担法人不同，冲抵额为零并告警 → 3〔拟规则 FIN-R1（冲抵限同一法人）〕；3. otherwise → 2 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-147 steps/2/decision | L10879 `event_policies/steps/2` | 3 | 提交过账候选：1. 原期间所在年度已年结 → end / 暂挂待跨年处理〔ADR-0045 U-2（待定）〕；2. 净额为零 → 4；3. otherwise → 3 | 不适合（N4） | 运算/生效数据查询或固定口径，保留参数/来源版本；可读/版本/留痕见 N4；F0 |
| PRC-147 steps/3/decision | L10904 `event_policies/steps/3` | 1 | 确认应付：1. otherwise → end / 应付已确认 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-147 steps/4/decision | L10921 `event_policies/steps/4` | 1 | 标记费用报销已付：1. otherwise → end / 报销已置已付 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-148 steps/1/decision | L10975 `event_policies/steps/1` | 3 | 追加收入确认事实并占用转实：1. 审批已驳回 → 3；2. 二次校验失败（额度不足或证据已失效） → 4〔RULE-222〕；3. otherwise → 2 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-148 steps/2/decision | L11013 `event_policies/steps/2` | 2 | 提交过账候选：1. 原期间所在年度已年结 → end / 暂挂待跨年处理〔ADR-0045 U-2（待定）〕；2. otherwise → end / 收入确认事实已过账 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-148 steps/3/decision | L11037 `event_policies/steps/3` | 1 | 释放收入额度占用：1. otherwise → end / 占用已释放 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-148 steps/4/decision | L11057 `event_policies/steps/4` | 2 | 释放收入额度占用：1. 处置为释放占用 → end / 占用已释放；2. otherwise → PRC-53 / 已重新发起审批 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-150 steps/1/decision | L11151 `event_policies/steps/1` | 3 | 冲减应收：1. 找不到蓝字发票对应的应收 → end / 应收未找到；2. 红冲额超过应收未结余额（已收款后再红冲） → 2；3. otherwise → 3 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-150 steps/2/decision | L11174 `event_policies/steps/2` | 1 | 转预收或待退：1. otherwise → 3 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-150 steps/3/decision | L11188 `event_policies/steps/3` | 3 | 提交过账候选：1. 原期间所在年度已年结 → end / 暂挂待跨年处理〔ADR-0045 U-2（待定）〕；2. 红冲额超过未结余额，已转预收或待退 → end / 已转预收或待退；3. otherwise → end / 应收已冲减 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-179 steps/1/decision | L11253 `event_policies/steps/1` | 3 | 开始履行协议：1. 该项目未关联已签署生效的协议，或（关联登记触发时）关联项目尚未开始执行 → end / 无需处理；2. 关联协议已在履行中或之后状态 → end / 无需处理；3. otherwise → end / 协议已转入履行中 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-42 steps/1/decision | L12707 `single_aggregate/steps/1` | 1 | 发布检查表模板版本：1. otherwise → end / 已生效 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |

### A.2 人工责任、职责分离与时间/事件条件

这些字段与 decision 可能重合，不计入判断块/分支合计。实际审批人选择若形成独立政策，应另按 §3.3 评价；以下评价针对人工判断、强制职责分离和编排本身。

| 注记 | 位置 | 原文中的责任/条件 | 结论及属性 |
|---|---|---|---|
| PRC-07 /segregation | L334 `processes/(根)` | 报价编制人与评审人不得为同一人（建议，见 NOTES 待用户定） | 不适合（N2）；属性见 N2，F0 |
| PRC-07 steps/4/approval | L459 `processes/steps/4` | engine=platform.approval；doc_type=报价评审单；approver=cost_object_owner；why_human=需要人判断报价的价格策略、毛利水平、服务范围与风险承担是否可接受，系统只能算金额与毛利，无法替代商业判断；on_reject=6 | 不适合（N1）；属性见 N1，F0 |
| PRC-08 /segregation | L591 `processes/(根)` | 保证金付款申请人与出纳登记缴纳不得为同一人（INV-81 经办与出纳分离） | 不适合（N2）；属性见 N2，F0 |
| PRC-08 steps/4/approval | L754 `processes/steps/4` | engine=platform.approval；doc_type=投标保证金付款审批；approver=cost_object_owner；multi_level=False；why_human=保证金金额是否与招标文件要求一致、资金占用是否可承受需要人核对招标文件与资金安排，系统只能核对金额阈值与收款账户；参投判断不在此（PRC-183）。；on_reject=12 | 不适合（N1）；属性见 N1，F0 |
| PRC-08 steps/6/on_time | L815 `processes/steps/6` | 投标截止时间前的提醒提前量（P-13，具体量以规则卡为准） | 不适合（N3）；属性见 N3，F0 |
| PRC-08 steps/13/on_event | L1012 `processes/steps/13` | [{'aggregate': 'AGG-11', 'event': '投标已中标'}] | 不适合（N3）；属性见 N3，F0 |
| PRC-08 steps/14/on_event | L1043 `processes/steps/14` | [{'aggregate': 'AGG-11', 'event': '投标未中标'}] | 不适合（N3）；属性见 N3，F0 |
| PRC-183 steps/3/approval | L1244 `processes/steps/3` | engine=platform.approval；doc_type=参投例外审批；approver=cost_object_owner；multi_level=False；why_human=低于毛利底线、超产能或有独立性冲突时仍要参投，是战略取舍（客户关系、市场进入、产能调配），系统只能判断是否触发阈值，不能判断例外是否值得；on_reject=2 | 不适合（N1）；属性见 N1，F0 |
| PRC-13 /segregation | L1324 `processes/(根)` | 费率版本的发起人（财务）与批准人（管理层）分离（D6） | 不适合（N2）；属性见 N2，F0 |
| PRC-13 steps/3/approval | L1434 `processes/steps/3` | engine=platform.approval；doc_type=费率版本批准单；approver=cost_object_owner；why_human=标准成本费率与计费系数决定全公司报价与预算口径，需人判断水平是否合理、与经营策略和市场价格是否匹配，系统无法代替；on_reject=2 | 不适合（N1）；属性见 N1，F0 |
| PRC-16 /segregation | L1587 `processes/(根)` | 申请人与审批人不得为同一人（责任人即申请人时命中 AR-11，按上级代批或加审处理） | 不适合（N2）；属性见 N2，F0 |
| PRC-16 steps/3/approval | L1695 `processes/steps/3` | engine=platform.approval；doc_type=风险交付授权审批单；approver=cost_object_owner；why_human=需要人判断签约可能性、客户信用与先行投入的风险是否可承担，系统只能算额度与期限，无法评估商业风险；on_reject=4 | 不适合（N1）；属性见 N1，F0 |
| PRC-18 steps/2/approval | L2107 `processes/steps/2` | engine=external；doc_type=交付计划发包人批准；approver=SH-C6a；multi_level=False；why_human=发包人对交付计划的批准是客户方的书面意思表示，发生在系统外，本系统只登记批准文件作证据。；on_reject=1 | 不适合（N1）；属性见 N1，F0 |
| PRC-18 steps/2/timeout | L2107 `processes/steps/2` | {'after': 'P5D', 'escalate_to': 'SH-E2', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-19 /segregation | L2171 `processes/(根)` | 预算编制人与审批人不得为同一人 | 不适合（N2）；属性见 N2，F0 |
| PRC-19 steps/3/approval | L2269 `processes/steps/3` | engine=platform.approval；doc_type=项目预算审批；approver=cost_object_owner；multi_level=False；why_human=判断预算工时与非人工成本是否合理、预算毛利低于赢单报价毛利时是否接受；系统只能算毛利并预警（P-18），不能替责任人取舍；on_reject=2 | 不适合（N1）；属性见 N1，F0 |
| PRC-20 steps/3/approval | L2430 `processes/steps/3` | engine=aggregate；doc_type=资源分配确认；approver=SH-M1；multi_level=False；why_human=按人员专长、当前负荷和项目优先级判断派谁；系统只能算产能并预警（P-27），取舍由资源所在部门的负责人作出；on_reject=1 | 不适合（N1）；属性见 N1，F0 |
| PRC-23 /segregation | L2501 `processes/(根)` | 员工不得审批自己的工时单；项目经理本人填报的项目工时由上一级（部门负责人）审批 | 不适合（N2）；属性见 N2，F0 |
| PRC-23 steps/3/approval | L2668 `processes/steps/3` | engine=platform.approval；doc_type=工时审批；approver=cost_object_owner；multi_level=False；why_human=判断该员工是否确在所记项目、商机或管理事务上投入了所记工时；系统只能核对工作包授权、请假和标准工时，核对不了实际投入；on_reject=2 | 不适合（N1）；属性见 N1，F0 |
| PRC-23 steps/3/timeout | L2668 `processes/steps/3` | {'after': 'P1D', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-26 steps/1/timeout | L3077 `processes/steps/1` | {'after': '计划提供日', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-26 steps/3/approval | L3121 `processes/steps/3` | engine=aggregate；doc_type=发包人资料确认；approver=SH-E3；multi_level=False；why_human=判断发包人提供的资料是否完整、可用于设计，属专业核对，系统只能记录结论；on_reject=4 | 不适合（N1）；属性见 N1，F0 |
| PRC-28 steps/3/approval | L3315 `processes/steps/3` | engine=aggregate；doc_type=交付物校审；approver=SH-M1；multi_level=False；why_human=校审是对设计成果技术正确性与规范符合性的专业判断，系统只能核对格式与清单完整，不能代替专业审查。；on_reject=2 | 不适合（N1）；属性见 N1，F0 |
| PRC-28 steps/5/timeout | L3389 `processes/steps/5` | {'after': '合同审查期；未约定时 P15D 催签周期', 'escalate_to': 'SH-E2', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-28 steps/6/approval | L3437 `processes/steps/6` | engine=external；doc_type=交付物客户认可；approver=SH-C6a；multi_level=False；why_human=客户认可是客户方的书面意思表示（签收单、审查同意意见、确认邮件或阶段签认），发生在系统外，本系统只登记认可材料与认可日作证据，签认人身份由系统按合同登记核对。；on_reject=7 | 不适合（N1）；属性见 N1，F0 |
| PRC-30 /segregation | L3546 `processes/(根)` | 完工估算的提出人与批准人不得为同一人（项目总监提出的由部门负责人批准） | 不适合（N2）；属性见 N2，F0 |
| PRC-30 steps/2/approval | L3618 `processes/steps/2` | engine=aggregate；doc_type=完工估算批准；approver=SH-M1；multi_level=False；why_human=判断完工估算（ETC、EAC）是否合理可信；估算不进入收入确认，系统只能在超预算时预警（P-19），合理性须责任人判断；on_reject=1 | 不适合（N1）；属性见 N1，F0 |
| PRC-31 /segregation | L3655 `processes/(根)` | 变更提出人与内审人不得为同一人 | 不适合（N2）；属性见 N2，F0 |
| PRC-31 steps/3/approval | L3756 `processes/steps/3` | engine=aggregate；doc_type=设计变更内审；approver=SH-E4；multi_level=False；why_human=变更方案对其他专业和已出成果的影响，以及归因是否站得住，是专业判断，系统无法代替；聚合内专业复核，不是费用审批；on_reject=1 | 不适合（N1）；属性见 N1，F0 |
| PRC-32 steps/1/timeout | L3932 `processes/steps/1` | {'after': '合同约定通知期限（缺省 P5D，自事由发生日起）', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-32 steps/2/approval | L3958 `processes/steps/2` | engine=external；doc_type=延期申请答复；approver=SH-C6a；multi_level=False；why_human=延期答复是发包人的书面意思表示，发生在系统外，本系统只登记答复文件作证据；合同约定逾期视为批准的由期限分支处理。；on_reject=4 | 不适合（N1）；属性见 N1，F0 |
| PRC-32 steps/2/timeout | L3958 `processes/steps/2` | {'after': '合同约定答复期（缺省 P5D，自说明送达日起）', 'escalate_to': 'SH-E2', 'interrupting': True} | 不适合（N3）；属性见 N3，F0 |
| PRC-34 /segregation | L4086 `processes/(根)` | 不符合项的登记人与关闭确认人不得为同一人 | 不适合（N2）；属性见 N2，F0 |
| PRC-34 steps/2/timeout | L4165 `processes/steps/2` | {'after': '按等级体系版本配置的处置期限（暂无默认，待用户定）', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-34 steps/6/approval | L4269 `processes/steps/6` | engine=aggregate；doc_type=不符合项关闭；approver=SH-E4；multi_level=False；why_human=整改（含返工）是否确已消除问题，是专业复核，系统只能核对状态；聚合内专业复核，不是费用审批；on_reject=2 | 不适合（N1）；属性见 N1，F0 |
| PRC-36 /segregation | L4302 `processes/(根)` | 结项申请人与审批人不得为同一人 | 不适合（N2）；属性见 N2，F0 |
| PRC-36 steps/5/approval | L4474 `processes/steps/5` | engine=platform.approval；doc_type=结项申请；approver=cost_object_owner；multi_level=False；why_human=三项拒绝检查已由 INV-40 守卫，审批不再重复核对；审批人只判断两项预警（工时单未全部审批、无生效协议）能否接受，以及项目是否确已实质完成；on_reject=3 | 不适合（N1）；属性见 N1，F0 |
| PRC-37 steps/1/timeout | L4611 `processes/steps/1` | {'after': '建议 P14D 未回收提醒（建议值，待用户定）', 'escalate_to': 'SH-E2', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-38 /segregation | L4723 `processes/(根)` | 复盘评审人不得是该复盘的编制人 | 不适合（N2）；属性见 N2，F0 |
| PRC-38 steps/2/approval | L4786 `processes/steps/2` | engine=aggregate；doc_type=项目复盘评审；approver=SH-E4；multi_level=False；why_human=复盘是否客观、结论是否可复用，是专业判断，系统无法代替；聚合内专业复核，不是费用审批；on_reject=1 | 不适合（N1）；属性见 N1，F0 |
| PRC-41 steps/4/timeout | L4955 `processes/steps/4` | {'after': 'P30D', 'escalate_to': 'SH-E4', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-43 /segregation | L4998 `processes/(根)` | 检查记录的提交人与审核人不得为同一人 | 不适合（N2）；属性见 N2，F0 |
| PRC-43 steps/6/timeout | L5179 `processes/steps/6` | {'after': 'P3D', 'escalate_to': 'SH-E4', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-43 steps/7/approval | L5210 `processes/steps/7` | engine=aggregate；doc_type=检查记录审核；approver=SH-E4；why_human=证据是否足以支撑检查结论、未检原因是否合理，需要懂检查标准的人按现场情况判断，系统只能核对「有没有」，不能核对「够不够」。；on_reject=6 | 不适合（N1）；属性见 N1，F0 |
| PRC-44 /segregation | L5292 `processes/(根)` | 不符合项的登记人与复核人不得为同一人 | 不适合（N2）；属性见 N2，F0 |
| PRC-44 steps/3/approval | L5405 `processes/steps/3` | engine=aggregate；doc_type=不符合项复核；approver=SH-E4；why_human=发现是否成立、等级偏离模板映射是否有理，取决于现场情况与检查标准的理解，需要专业判断；证据齐全、等级默认值与回避已由规则处理。；on_reject=7 | 不适合（N1）；属性见 N1，F0 |
| PRC-45 steps/2/timeout | L5621 `processes/steps/2` | {'after': 'P14D', 'escalate_to': 'SH-E4', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-45 steps/5/approval | L5688 `processes/steps/5` | engine=aggregate；doc_type=整改复查结论；approver=SH-E4；why_human=整改是否真正满足该等级不符合项的要求（含部分整改能否接受），需要结合现场证据与检查标准做专业判断；证据齐全、轮次、期限与未通过后的流转已由规则处理。；on_reject=7 | 不适合（N1）；属性见 N1，F0 |
| PRC-45 steps/5/timeout | L5688 `processes/steps/5` | {'after': 'P10D', 'escalate_to': 'SH-E4', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-46 steps/2/timeout | L5894 `processes/steps/2` | {'after': 'P30D', 'escalate_to': 'SH-E4', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-47 /segregation | L5994 `processes/(根)` | 起草人与合同评审人（含加审人）不得为同一人 | 不适合（N2）；属性见 N2，F0 |
| PRC-47 steps/5/approval | L6154 `processes/steps/5` | engine=platform.approval；doc_type=合同评审单；approver=cost_object_owner；why_human=评审合同条款的法律与商务风险（责任限额、付款条件、违约与分包条款、与投标承诺的偏离）需要人按条款判断，系统只能核对金额阈值与登记信息，不能判断条款是否可接受。；on_reject=2 | 不适合（N1）；属性见 N1，F0 |
| PRC-47 steps/5/timeout | L6154 `processes/steps/5` | {'after': 'P5D', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-48 /segregation | L6271 `processes/(根)` | 变更提出人与审批人不得为同一人 | 不适合（N2）；属性见 N2，F0 |
| PRC-48 steps/4/approval | L6389 `processes/steps/4` | engine=platform.approval；doc_type=协议变更审批单；approver=cost_object_owner；why_human=变更对价款、工期和范围的影响是否合理、是否值得向发包人提出，需要项目责任人按商业判断决定；系统只能算出影响金额并按阈值分流。；on_reject=end | 不适合（N1）；属性见 N1，F0 |
| PRC-48 steps/5/approval | L6444 `processes/steps/5` | engine=external；doc_type=发包人书面同意；approver=SH-C6a；why_human=变更以发包人书面指令、补充协议或变更确认单为准，是否同意是发包人的决定，本系统只登记证据。 | 不适合（N1）；属性见 N1，F0 |
| PRC-48 steps/5/timeout | L6444 `processes/steps/5` | {'after': '合同约定答复期；未约定时 P30D', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-48 steps/7/timeout | L6505 `processes/steps/7` | {'after': '合同约定签认期；未约定时 P14D', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-48 steps/8/on_event | L6537 `processes/steps/8` | [{'aggregate': 'AGG-15', 'event': 'ChangeOrderApproved'}, {'aggregate': 'AGG-15', 'event': 'SiteVisaConfirmed'}] | 不适合（N3）；属性见 N3，F0 |
| PRC-49 steps/2/timeout | L6638 `processes/steps/2` | {'after': '合同约定索赔时限（INV-17）；未约定时 P28D 自事项发生日起', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-50 /segregation | L6720 `processes/(根)` | 订单提交人与审批人不得为同一人 | 不适合（N2）；属性见 N2，F0 |
| PRC-50 steps/2/approval | L6794 `processes/steps/2` | engine=platform.approval；doc_type=任务订单审批单；approver=cost_object_owner；why_human=任务订单的范围、工期与价格是否符合框架协议约定且能履行，需要项目责任人判断；限额占用与协议生效由规则守卫，不在审批中核对。；on_reject=3 | 不适合（N1）；属性见 N1，F0 |
| PRC-50 steps/2/timeout | L6794 `processes/steps/2` | {'after': '占用有效期（配置，待用户定）', 'escalate_to': 'SH-E5', 'interrupting': True} | 不适合（N3）；属性见 N3，F0 |
| PRC-54 steps/3/on_time | L6985 `processes/steps/3` | 合同审查期满（送达日起） | 不适合（N3）；属性见 N3，F0 |
| PRC-54 steps/6/timeout | L7038 `processes/steps/6` | {'after': 'P3D', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-57 /segregation | L7109 `processes/(根)` | 开票申请提交人与开票经办不得为同一人；开票申请提交人与审批人不得为同一人 | 不适合（N2）；属性见 N2，F0 |
| PRC-57 steps/4/approval | L7275 `processes/steps/4` | engine=platform.approval；doc_type=开票申请审批；approver=cost_object_owner；why_human=开票金额超阈值或购买方为关联方时，需要人判断开票内容与金额是否与交易实质相符、是否公允；协议依据与可开票额度已由准入规则守卫，不在审批中核对。；on_reject=5 | 不适合（N1）；属性见 N1，F0 |
| PRC-57 steps/4/timeout | L7275 `processes/steps/4` | {'after': 'P3D', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-57 steps/6/timeout | L7351 `processes/steps/6` | {'after': 'PT30M', 'escalate_to': 'SH-E6', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-57 steps/6/on_event | L7351 `processes/steps/6` | [{'aggregate': 'AGG-45', 'event': 'InvoiceEligibilityConfirmed'}] | 不适合（N3）；属性见 N3，F0 |
| PRC-58 /segregation | L7460 `processes/(根)` | 红冲申请人（开票会计）与审批人不得为同一人；红冲经办与申请人不得为同一人 | 不适合（N2）；属性见 N2，F0 |
| PRC-58 steps/1/approval | L7538 `processes/steps/1` | engine=platform.approval；doc_type=红冲申请审批单；approver=cost_object_owner；why_human=原发票是否确属错误、业务是否确已取消，需要项目责任人按业务事实判断；系统只能核对发票状态和是否已用途确认，不能判断红冲理由是否成立。；on_reject=end | 不适合（N1）；属性见 N1，F0 |
| PRC-58 steps/1/timeout | L7538 `processes/steps/1` | {'after': 'P3D', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-58 steps/2/timeout | L7598 `processes/steps/2` | {'after': 'PT72H', 'escalate_to': 'SH-E6', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-60 /segregation | L7722 `processes/(根)` | 认领收款与登记银行流水不得为同一人（职务分离，具体岗位待用户定）；第三方代付认领的经办与审批人不得为同一人 | 不适合（N2）；属性见 N2，F0 |
| PRC-60 steps/3/approval | L7854 `processes/steps/3` | engine=platform.approval；doc_type=第三方代付认领审批单；approver=cost_object_owner；why_human=须判断代付关系的真实性与合规（委托付款函是否由合同相对方出具、代付方与合同相对方的关系）；系统只能核对函件是否存在。；on_reject=end | 不适合（N1）；属性见 N1，F0 |
| PRC-60 steps/3/timeout | L7854 `processes/steps/3` | {'after': 'P3D', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-60 steps/7/timeout | L7972 `processes/steps/7` | {'after': 'P30D', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-61 /segregation | L8017 `processes/(根)` | 登记争议处置的人、核销坏账经办与审批人三者不得为同一人（审批人为协议所属项目的责任人） | 不适合（N2）；属性见 N2，F0 |
| PRC-61 steps/2/timeout | L8109 `processes/steps/2` | {'after': 'P30D', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-61 steps/3/approval | L8136 `processes/steps/3` | engine=platform.approval；doc_type=坏账核销审批单；approver=cost_object_owner；why_human=款项是否确已无法收回（可回收性）需要人结合催收记录、对方经营与诉讼状态判断，系统只能算账龄；核销一经过账不可逆。；on_reject=end | 不适合（N1）；属性见 N1，F0 |
| PRC-61 steps/3/timeout | L8136 `processes/steps/3` | {'after': 'P5D', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-63 steps/1/timeout | L8301 `processes/steps/1` | {'after': '商票到期日', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-63 steps/3/on_event | L8354 `processes/steps/3` | [{'aggregate': 'AGG-50', 'event': '商票已逾期'}] | 不适合（N3）；属性见 N3，F0 |
| PRC-66 /segregation | L8368 `processes/(根)` | 结算书编制人与最终价款让价审批人不得为同一人 | 不适合（N2）；属性见 N2，F0 |
| PRC-66 steps/3/on_event | L8464 `processes/steps/3` | [{'aggregate': 'AGG-13', 'event': '协议已完成'}] | 不适合（N3）；属性见 N3，F0 |
| PRC-66 steps/4/timeout | L8481 `processes/steps/4` | {'after': '合同约定结算确认期；未约定时 P30D', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-66 steps/5/approval | L8523 `processes/steps/5` | engine=platform.approval；doc_type=最终价款让价审批单；approver=cost_object_owner；why_human=让价或审减是否接受属商业判断（客户关系、已发生成本、争议风险），系统只能算出差额并按阈值分流。；on_reject=3 | 不适合（N1）；属性见 N1，F0 |
| PRC-66 steps/5/timeout | L8523 `processes/steps/5` | {'after': 'P5D', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-69 /segregation | L8621 `processes/(根)` | 角色申请人与核准合格人不得为同一人（项目经理申请，财务核准，建议保持） | 不适合（N2）；属性见 N2，F0 |
| PRC-78 /segregation | L8827 `processes/(根)` | 核查结果登记人（SH-E6）与延续批准人（SH-M1）分离 | 不适合（N2）；属性见 N2，F0 |
| PRC-78 steps/5/approval | L8983 `processes/steps/5` | engine=aggregate；doc_type=资质延续审批单；approver=SH-M1；why_human=需要管理层判断企业是否仍具备延续该资质所需的人员配备、业绩与经营条件，并决定是否续办，属经营决策，系统无法代替；on_reject=6 | 不适合（N1）；属性见 N1，F0 |
| PRC-78 steps/6/on_time | L9010 `processes/steps/6` | 到期日届满 | 不适合（N3）；属性见 N3，F0 |
| PRC-86 /segregation | L9033 `processes/(根)` | 借款发放经办人与付款执行人不得为同一人（付款在 B-10，具体岗位待用户定）；借款制单、审批、出纳付款三者不得为同一人 | 不适合（N2）；属性见 N2，F0 |
| PRC-86 steps/2/approval | L9136 `processes/steps/2` | engine=platform.approval；doc_type=员工借款审批单；approver=cost_object_owner；why_human=借款用途是否正当、是否确有必要事前借支，需要借款人所在部门的责任人判断；有无未结借款与限额由规则守卫，不在审批中核对。；on_reject=end | 不适合（N1）；属性见 N1，F0 |
| PRC-86 steps/2/timeout | L9136 `processes/steps/2` | {'after': 'P3D', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-140 /segregation | L9365 `processes/(根)` | 借阅人与登记借出的档案管理人不得为同一人 | 不适合（N2）；属性见 N2，F0 |
| PRC-140 steps/1/approval | L9430 `processes/steps/1` | engine=platform.approval；doc_type=合同借阅审批；approver=cost_object_owner；why_human=借阅目的（投标、诉讼、审计、结算）是否正当、是否确需借出原件而非复印件，需要借阅人所在部门的责任人判断；协议是否已归档、原件是否已外借由规则守卫。；on_reject=end | 不适合（N1）；属性见 N1，F0 |
| PRC-140 steps/1/timeout | L9430 `processes/steps/1` | {'after': 'P3D', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-140 steps/3/timeout | L9517 `processes/steps/3` | {'after': '预计归还日（取自审批载荷）', 'escalate_to': 'SH-E6', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-140 steps/4/on_time | L9547 `processes/steps/4` | 预计归还日届满且未归还 | 不适合（N3）；属性见 N3，F0 |
| PRC-141 steps/1/timeout | L9604 `processes/steps/1` | {'after': 'P30D', 'escalate_to': 'SH-E6', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-149 /segregation | L9652 `processes/(根)` | 冲销申请提交人与审批人不得为同一人 | 不适合（N2）；属性见 N2，F0 |
| PRC-149 steps/4/approval | L9787 `processes/steps/4` | engine=platform.approval；doc_type=收入冲销审批单；approver=cost_object_owner；why_human=冲销原因是否成立、应冲销还是应走更正确认、冲销金额是否准确，需要责任人和财务按业务事实判断；系统只能守冲销不超原确认。；on_reject=7 | 不适合（N1）；属性见 N1，F0 |
| PRC-149 steps/4/timeout | L9787 `processes/steps/4` | {'after': 'P5D', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-151 steps/1/timeout | L9974 `processes/steps/1` | {'after': 'P3D', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-152 /segregation | L10035 `processes/(根)` | 达成登记人与开票申请经办（PRC-57）不得为同一人 | 不适合（N2）；属性见 N2，F0 |
| PRC-153 /segregation | L10178 `processes/(根)` | 登记退还与导入银行流水不得为同一人 | 不适合（N2）；属性见 N2，F0 |
| PRC-153 steps/2/timeout | L10279 `processes/steps/2` | {'after': 'P5D', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-53 steps/2/approval | L10526 `event_policies/steps/2` | engine=platform.approval；doc_type=收入确认审批；approver=SH-E6；why_human=财务复核客户确认证据的实质（签认单、审查同意书面意见等是否真实有效、与交付物及金额是否相符）和签认人身份（是否确为合同登记的发包人代表或授权人），来源 ADR-0047 U-2 (a) 的财务复核；系统只能核对登记信息与证据格式，无法判断证据真伪和签认人身份的真实性。；on_reject=PRC-148（驳回分支释放占用） | 不适合（N1）；属性见 N1，F0 |
| PRC-53 steps/2/timeout | L10526 `event_policies/steps/2` | {'after': 'P5D', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-91 steps/1/timeout | L10712 `event_policies/steps/1` | {'after': '期间封期前提醒（提前量建议 3 个工作日，待用户定）', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-148 steps/4/timeout | L11057 `event_policies/steps/4` | {'after': 'P5D', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-10 /segregation | L11751 `single_aggregate/(根)` | 扣减、没收的登记人、审批人与 B-10 付款执行出纳三者不得为同一人 | 不适合（N2）；属性见 N2，F0 |
| PRC-10 transitions/3/approval | L11835 `single_aggregate/transitions/3` | engine=platform.approval；doc_type=保证金扣减审批单；approver=cost_object_owner；why_human=扣减以我方（或对方）违约事实成立为前提，违约事实与扣减金额需要责任人判断；系统只守「退还与没收互斥、履约保证金可部分扣减」（INV-20）。；on_reject=转移不发生，保证金保持已缴 | 不适合（N1）；属性见 N1，F0 |
| PRC-10 transitions/5/approval | L11867 `single_aggregate/transitions/5` | engine=platform.approval；doc_type=保证金没收审批单；approver=cost_object_owner；why_human=没收以违约事实成立为前提，是否构成违约、没收金额是否合理需要责任人判断；退还与没收互斥各一次（INV-20）由不变量守卫。；on_reject=转移不发生，保证金保持已缴 | 不适合（N1）；属性见 N1，F0 |
| PRC-27 commands/4/approval | L12474 `single_aggregate/commands/4` | engine=aggregate；doc_type=风险与问题接受；approver=cost_object_owner；why_human=接受残余风险是责任人对项目风险承担的判断，系统只能核对接受人和依据已填（INV-113），不能替责任人决定接受与否 | 不适合（N1）；属性见 N1，F0 |
| PRC-52 transitions/1/approval | L12744 `single_aggregate/transitions/1` | engine=external；doc_type=证据类型开放书面确认；approver=SH-E6；why_human=开放哪类客户确认证据可以触发收入确认，是财务负责人的判断，本系统只登记其书面确认与依据文件（intent 2026-10-16-162827）。 | 不适合（N1）；属性见 N1，F0 |
| PRC-68 /segregation | L12791 `single_aggregate/(根)` | 终止申请提交人与审批人不得为同一人 | 不适合（N2）；属性见 N2，F0 |
| PRC-68 transitions/1/approval | L12804 `single_aggregate/transitions/1` | engine=platform.approval；doc_type=协议终止审批单；approver=cost_object_owner；why_human=终止的法律与商务后果（违约责任、已完成工作量的结算、对价款与项目成本清算的影响）需要责任人判断；终止原因必填由不变量守卫。；on_reject=协议保持原状态 | 不适合（N1）；属性见 N1，F0 |
| PRC-68 transitions/2/approval | L12834 `single_aggregate/transitions/2` | engine=platform.approval；doc_type=协议终止审批单；approver=cost_object_owner；why_human=终止的法律与商务后果（违约责任、已完成工作量的结算、对价款与项目成本清算的影响）需要责任人判断；终止原因必填由不变量守卫。；on_reject=协议保持原状态 | 不适合（N1）；属性见 N1，F0 |

### A.3 无显式 decision 的登记项

| 流程 | 位置 | 当前评估及证据边界 |
|---|---|---|
| PRC-51 建立并更新开票与收入额度 | L10385 `event_policies` | 无显式 decision；不适合把维护/投影/状态动作整体迁入 DMN；描述中的独立政策按 §3.3 补表后重评。需读懂职责，引用配置/来源版本并留动作记录；F0 |
| PRC-56 付款条件项转为可结算 | L10584 `event_policies` | 无显式 decision；不适合把维护/投影/状态动作整体迁入 DMN；描述中的独立政策按 §3.3 补表后重评。需读懂职责，引用配置/来源版本并留动作记录；F0 |
| PRC-59 由销项发票创建应收 | L10627 `event_policies` | 无显式 decision；不适合把维护/投影/状态动作整体迁入 DMN；描述中的独立政策按 §3.3 补表后重评。需读懂职责，引用配置/来源版本并留动作记录；F0 |
| PRC-146 协议借阅逾期催还 | L10771 `event_policies` | 无显式 decision；不适合把维护/投影/状态动作整体迁入 DMN；描述中的独立政策按 §3.3 补表后重评。需读懂职责，引用配置/来源版本并留动作记录；F0 |
| PRC-01 举办市场活动 | L11284 `single_aggregate` | 无显式 decision；不适合把维护/投影/状态动作整体迁入 DMN；描述中的独立政策按 §3.3 补表后重评。需读懂职责，引用配置/来源版本并留动作记录；F0 |
| PRC-02 计划并完成客户活动 | L11364 `single_aggregate` | 无显式 decision；不适合把维护/投影/状态动作整体迁入 DMN；描述中的独立政策按 §3.3 补表后重评。需读懂职责，引用配置/来源版本并留动作记录；F0 |
| PRC-05 维护往来单位档案 | L11508 `single_aggregate` | 无显式 decision；不适合把维护/投影/状态动作整体迁入 DMN；描述中的独立政策按 §3.3 补表后重评。需读懂职责，引用配置/来源版本并留动作记录；F0 |
| PRC-06 做独立性检查 | L11672 `single_aggregate` | 无显式 decision；不适合把维护/投影/状态动作整体迁入 DMN；描述中的独立政策按 §3.3 补表后重评。需读懂职责，引用配置/来源版本并留动作记录；F0 |
| PRC-10 收付保证金与保函 | L11751 `single_aggregate` | 无显式 decision；不适合把维护/投影/状态动作整体迁入 DMN；描述中的独立政策按 §3.3 补表后重评。需读懂职责，引用配置/来源版本并留动作记录；F0 |
| PRC-12 发布服务产品与阶段类型版本 | L11990 `single_aggregate` | 无显式 decision；不适合把维护/投影/状态动作整体迁入 DMN；描述中的独立政策按 §3.3 补表后重评。需读懂职责，引用配置/来源版本并留动作记录；F0 |
| PRC-14 登记建设工程与招标项目 | L12105 `single_aggregate` | 无显式 decision；不适合把维护/投影/状态动作整体迁入 DMN；描述中的独立政策按 §3.3 补表后重评。需读懂职责，引用配置/来源版本并留动作记录；F0 |
| PRC-21 推进阶段与工作包 | L12229 `single_aggregate` | 无显式 decision；不适合把维护/投影/状态动作整体迁入 DMN；描述中的独立政策按 §3.3 补表后重评。需读懂职责，引用配置/来源版本并留动作记录；F0 |
| PRC-27 登记并处置项目风险与问题 | L12355 `single_aggregate` | 无显式 decision；不适合把维护/投影/状态动作整体迁入 DMN；描述中的独立政策按 §3.3 补表后重评。需读懂职责，引用配置/来源版本并留动作记录；F0 |
| PRC-33 采集、关联并封存证据 | L12492 `single_aggregate` | 无显式 decision；不适合把维护/投影/状态动作整体迁入 DMN；描述中的独立政策按 §3.3 补表后重评。需读懂职责，引用配置/来源版本并留动作记录；F0 |
| PRC-52 开放可触发收入确认的证据类型 | L12732 `single_aggregate` | 无显式 decision；不适合把维护/投影/状态动作整体迁入 DMN；描述中的独立政策按 §3.3 补表后重评。需读懂职责，引用配置/来源版本并留动作记录；F0 |
| PRC-68 终止协议 | L12791 `single_aggregate` | 无显式 decision；不适合把维护/投影/状态动作整体迁入 DMN；描述中的独立政策按 §3.3 补表后重评。需读懂职责，引用配置/来源版本并留动作记录；F0 |
| PRC-22 阶段门预警 | L12903 `read_models` | 无显式 decision；不适合把维护/投影/状态动作整体迁入 DMN；描述中的独立政策按 §3.3 补表后重评。需读懂职责，引用配置/来源版本并留动作记录；F0 |
| PRC-62 质量保证金到期提示 | L13040 `read_models` | 无显式 decision；不适合把维护/投影/状态动作整体迁入 DMN；描述中的独立政策按 §3.3 补表后重评。需读懂职责，引用配置/来源版本并留动作记录；F0 |
| PRC-154 收入三口径对照 | L13082 `read_models` | 无显式 decision；不适合把维护/投影/状态动作整体迁入 DMN；描述中的独立政策按 §3.3 补表后重评。需读懂职责，引用配置/来源版本并留动作记录；F0 |

## 附录 B. finance 组完整判断清单

来源 E4：提交 `2bec63a04151c03cc18e91e0124971d2b673b709`，文件 `docs/modernization/architecture/proposals/closeout/finance/processes.finance.yaml`。54 项流程登记、206 个显式判断块、557 条分支；适合 1、不适合 203、待证据 2。行号为源 YAML 节点起始行，路径索引从 1 开始；每行采用 §9 属性码。

| 判断点 | 固定对象位置 | 分支数 | 条件与去向（完整列举） | 结论 | 原因及属性 |
|---|---|---|---|---|---|
| PRC-15 steps/1/decision | L47 `processes/steps/1` | 3 | 售前投入转费用：1. 该商机已转过费用（重复事件，幂等） → end / 已转费用（幂等）〔INV-94〕；2. 该商机没有未转的售前投入 → end / 无需转费用；3. otherwise → 2 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-15 steps/2/decision | L76 `processes/steps/2` | 4 | 提交过账候选：1. 所属期间在关账中（已发起关账、未确认），本期过账不接受：返回「期间关账中、可重试」，不进拒收记录，由来源 BC 投递重试 → end / 期间关账中待重试〔INV-92（S-31 SQ-12）；ADR-0045 决定 1〕；2. 落账期间已关账且该法人有开放期间，自动落当前开放期间并标原期间 → end / 已提交过账〔ADR-0045 决定 2；INV-92；AR-05〕；3. 该法人无开放期间可落（过账管道拒收），拒收并告警 → end / 已拒收〔INV-92；拒收记录由 PRC-158 处置〕；4. otherwise → end / 已提交过账 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-15 steps/3/decision | L110 `processes/steps/3` | 1 | 标记已过账：1. otherwise → 4 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-15 steps/4/decision | L128 `processes/steps/4` | 2 | 标记售前投入已转费用：1. 该商机的售前投入已转出（转入合同履约成本或转费用二选一） → end / 已转费用（幂等）〔INV-132〕；2. otherwise → end / 售前投入已转费用 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-25 steps/1/decision | L198 `processes/steps/1` | 2 | 登记合同已签回：1. 该商机的售前投入已转出（重复事件，幂等） → end / 已转入（幂等）〔INV-132〕；2. otherwise → 3 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-25 steps/2/decision | L218 `processes/steps/2` | 2 | 登记项目已建立：1. 该商机的售前投入已转出（重复事件，幂等） → end / 已转入（幂等）〔INV-132〕；2. otherwise → 3 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-25 steps/3/decision | L238 `processes/steps/3` | 2 | 登记售前投入挂接归属：1. 合同未签回或项目未建立（两者齐备才转入，后到者触发） → end / 等待齐备〔P-43；INV-132〕；2. otherwise → 4 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-25 steps/4/decision | L258 `processes/steps/4` | 3 | 转入售前投入：1. 该商机与合同已转入过（重复事件，幂等） → end / 已转入（幂等）〔INV-94〕；2. 该商机没有未转的售前投入 → end / 无需转入；3. otherwise → 5 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-25 steps/5/decision | L275 `processes/steps/5` | 4 | 提交过账候选：1. 所属期间在关账中（已发起关账、未确认），本期过账不接受：返回「期间关账中、可重试」，不进拒收记录，由来源 BC 投递重试 → end / 期间关账中待重试〔INV-92（S-31 SQ-12）；ADR-0045 决定 1〕；2. 落账期间已关账且该法人有开放期间，自动落当前开放期间并标原期间 → end / 已提交过账〔ADR-0045 决定 2；INV-92；AR-05〕；3. 该法人无开放期间可落（过账管道拒收），拒收并告警 → end / 已拒收〔INV-92；拒收记录由 PRC-158 处置〕；4. otherwise → end / 已提交过账 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-25 steps/6/decision | L309 `processes/steps/6` | 1 | 标记已过账：1. otherwise → 7 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-25 steps/7/decision | L327 `processes/steps/7` | 1 | 标记售前投入已转入：1. otherwise → end / 售前投入已转入〔INV-132〕 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-35 steps/1/decision | L391 `processes/steps/1` | 3 | 终止清算成本：1. 该项目或协议已清算过（重复事件，幂等） → end / 已清算（幂等）〔INV-94〕；2. 没有未结转的合同履约成本或售前投入 → end / 无需清算；3. otherwise → 2 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-35 steps/2/decision | L416 `processes/steps/2` | 4 | 提交过账候选：1. 所属期间在关账中（已发起关账、未确认），本期过账不接受：返回「期间关账中、可重试」，不进拒收记录，由来源 BC 投递重试 → end / 期间关账中待重试〔INV-92（S-31 SQ-12）；ADR-0045 决定 1〕；2. 落账期间已关账且该法人有开放期间，自动落当前开放期间并标原期间 → end / 已提交过账〔ADR-0045 决定 2；INV-92；AR-05〕；3. 该法人无开放期间可落（过账管道拒收），拒收并告警 → end / 已拒收〔INV-92；拒收记录由 PRC-158 处置〕；4. otherwise → end / 已提交过账 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-35 steps/3/decision | L450 `processes/steps/3` | 1 | 标记已过账：1. otherwise → end / 清算成本已过账 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-55 steps/1/decision | L510 `processes/steps/1` | 4 | 结转合同履约成本：1. 过账事实不是收入确认类型 → end / 非收入确认事实；2. 该事实已结转过（重复事件，幂等） → end / 已结转（幂等）〔INV-94〕；3. 该合同没有可转出的合同履约成本 → end / 无需结转〔INV-88〕；4. otherwise → 2〔INV-88〕 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-55 steps/2/decision | L540 `processes/steps/2` | 4 | 提交过账候选：1. 所属期间在关账中（已发起关账、未确认），本期过账不接受：返回「期间关账中、可重试」，不进拒收记录，由来源 BC 投递重试 → end / 期间关账中待重试〔INV-92（S-31 SQ-12）；ADR-0045 决定 1〕；2. 落账期间已关账且该法人有开放期间，自动落当前开放期间并标原期间 → end / 已提交过账〔ADR-0045 决定 2；INV-92；AR-05〕；3. 该法人无开放期间可落（过账管道拒收），拒收并告警 → end / 已拒收〔INV-92；拒收记录由 PRC-158 处置〕；4. otherwise → end / 已提交过账 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-55 steps/3/decision | L574 `processes/steps/3` | 1 | 标记已过账：1. otherwise → end / 合同履约成本已结转 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-64 steps/1/decision | L637 `processes/steps/1` | 3 | 导入银行流水：1. 银行文件校验和已导入过 → end / 重复文件已忽略〔INV-85〕；2. 文件无法解析、资金账户未登记或与账户不符 → 拒收并告警 → end / 文件已拒收〔INV-85〕；3. otherwise → 2 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-64 steps/2/decision | L676 `processes/steps/2` | 5 | 提交过账候选：1. 文件中没有利息或手续费行 → end / 流水已导入；2. 所属期间在关账中（已发起关账、未确认），本期过账不接受：返回「期间关账中、可重试」，不进拒收记录，由来源 BC 投递重试 → end / 期间关账中待重试〔INV-92（S-31 SQ-12）；ADR-0045 决定 1〕；3. 所属期间已关账，自动落当前开放期间并标原期间（系统触账不拒绝） → end / 流水已导入〔ADR-0045 决定 2；INV-92；AR-05〕；4. 过账管道拒收（该法人无开放期间、借贷不平或法人不一致），拒收并告警 → end / 过账被拒收〔INV-92、INV-93；拒收记录由 PRC-158 处置〕；5. otherwise → end / 流水已导入 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-65 transitions/1/decision | L795 `processes/transitions/1` | 2 | 开立资金账户：1. 银行开户证明缺失或法人主体不存在 → end / 已拒收〔INV-85〕；2. otherwise → end / 待确认开立 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-65 transitions/2/decision | L813 `processes/transitions/2` | 2 | 确认开立资金账户：1. 确认人与开立经办为同一人，命令被拒，改由他人确认 → end / 已拒收〔INV-81（同类，提议）〕；2. otherwise → end / 已开立 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-65 transitions/3/decision | L844 `processes/transitions/3` | 1 | 冻结资金账户：1. 冻结通知来自银行或有权机关，经办人登记 → end / 已冻结 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-65 transitions/4/decision | L860 `processes/transitions/4` | 2 | 解冻资金账户：1. 解除冻结通知缺失 → end / 已拒收；2. otherwise → end / 已解冻 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-65 transitions/5/decision | L892 `processes/transitions/5` | 2 | 销户资金账户：1. 账户仍有未结付款占用或未对账流水 → end / 已拒收〔INV-85〕；2. otherwise → end / 待确认销户 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-65 transitions/6/decision | L913 `processes/transitions/6` | 2 | 确认销户资金账户：1. 确认人与销户经办为同一人，命令被拒，改由他人确认 → end / 已拒收〔INV-81（同类，提议）〕；2. otherwise → end / 已销户 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-70 steps/1/decision | L1011 `processes/steps/1` | 3 | 读取供方准入资质与项目：1. 供方单位已停用或已合并，或供方角色未合格或已停用（INV-03b、INV-04a） → end / 已放弃〔INV-03b、INV-04a、AR-08〕；2. 我方为分包人而拟对专业分包再分包（INV-11），或分包方无有效资质（INV-12） → end / 已放弃〔INV-11〕；3. otherwise → 2 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-70 steps/2/decision | L1049 `processes/steps/2` | 1 | 预算占用：1. otherwise → 3〔预算策略（警，聚合稿回补第 9 行）、AR-03〕 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-70 steps/3/decision | L1069 `processes/steps/3` | 3 | 起草供方协议：1. 分包协议未附发包方同意证据（INV-13）→ 留在草拟并告警，经办人补证后再提交（预算占用不重占） → 3〔INV-13〕；2. 经办人主动放弃该协议（含审批驳回后放弃） → 7；3. otherwise → 4 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-70 steps/5/decision | L1142 `processes/steps/5` | 2 | 生效供方协议：1. 生效前置校验未通过（INV-12、INV-13）→ 拒收并告警，退回起草补正 → 3〔INV-13〕；2. otherwise → 6 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-70 steps/7/decision | L1174 `processes/steps/7` | 2 | 释放预算占用：1. 来源为经办人撤回审批 → end / 已撤回；2. otherwise → end / 已放弃 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-71 steps/1/decision | L1242 `processes/steps/1` | 3 | 提出分包变更：1. 原供方协议未生效，或已完成、已结算、已终止 → end / 已拒收；2. 双方未达成一致，经办人放弃变更 → end / 已放弃；3. otherwise → 2 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-71 steps/3/decision | L1307 `processes/steps/3` | 4 | 生效分包变更：1. 该变更已生效，再次修改（INV-16b），须另提新变更 → end / 已拒收〔INV-16b〕；2. 变更后协议金额低于已确认的履约累计（INV-82，AGG-58 一侧校验，生效前先读） → end / 已拒收〔INV-82〕；3. 双方最终未签署变更协议，经办人放弃 → end / 已放弃；4. otherwise → 4 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-71 steps/4/decision | L1339 `processes/steps/4` | 2 | 调整预算占用：1. 变更后价款无增减（增减额为零），无需调整 → 5；2. otherwise → 5 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-71 steps/5/decision | L1356 `processes/steps/5` | 2 | 供方额度更新：1. 该变更已更新过额度（事件重复） → end / 重复事件已忽略；2. otherwise → end / 分包变更已生效 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-72 steps/1/decision | L1438 `processes/steps/1` | 2 | 读取项目状态：1. 项目已结项、已终止或已取消 → end / 已拒收〔INV-39〕；2. otherwise → 2 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-72 steps/2/decision | L1460 `processes/steps/2` | 2 | 占用履约额度：1. 履约额度不足（INV-82） → end / 已拒收〔INV-82〕；2. otherwise → 3 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-72 steps/3/decision | L1473 `processes/steps/3` | 2 | 确认供方履约：1. 确认人与该供方协议的经办人为同一人（INV-83） → 5〔INV-83〕；2. otherwise → 4 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-72 steps/4/decision | L1495 `processes/steps/4` | 4 | 提交过账候选：1. 所属期间在关账中（已发起关账、未确认），本期过账不接受：返回「期间关账中、可重试」，不进拒收记录，由来源 BC 投递重试 → end / 期间关账中待重试〔INV-92（S-31 SQ-12）；ADR-0045 决定 1〕；2. 所属期间已关账，自动落当前开放期间并标原期间（系统触账不拒绝） → end / 已确认〔ADR-0045 决定 2；INV-92；AR-05〕；3. 过账管道拒收（该法人无开放期间、借贷不平或法人不一致），拒收并告警 → end / 过账被拒收〔INV-92、INV-93；拒收记录由 PRC-158 处置〕；4. otherwise → end / 已确认 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-72 steps/5/decision | L1528 `processes/steps/5` | 1 | 释放履约额度：1. otherwise → end / 已拒收 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-72 steps/7/decision | L1571 `processes/steps/7` | 2 | 冲销供方履约确认：1. 该履约确认已冲销过 → end / 已拒收〔INV-95〕；2. otherwise → 8 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-72 steps/8/decision | L1599 `processes/steps/8` | 1 | 释放履约额度：1. otherwise → 9 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-72 steps/9/decision | L1609 `processes/steps/9` | 4 | 冲销并重过账：1. 所属期间在关账中（已发起关账、未确认），本期过账不接受：返回「期间关账中、可重试」，不进拒收记录，由来源 BC 投递重试 → end / 期间关账中待重试〔INV-92（S-31 SQ-12）；ADR-0045 决定 1〕；2. 所属期间已关账，自动落当前开放期间并标原期间（系统触账不拒绝） → end / 供方履约确认已冲销〔ADR-0045 决定 2；INV-92；AR-05〕；3. 过账管道拒收（原事实不存在或已被冲销、该法人无开放期间、借贷不平），拒收并告警 → end / 过账被拒收〔INV-92、INV-93、INV-95；拒收记录由 PRC-158 处置〕；4. otherwise → end / 供方履约确认已冲销 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-73 steps/1/decision | L1687 `processes/steps/1` | 3 | 登记进项发票：1. 同一发票代码与号码已登记 → end / 已拒收〔INV-129〕；2. 开票方不是已登记往来单位 → end / 已拒收〔AR-08〕；3. otherwise → 2 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-73 steps/2/decision | L1716 `processes/steps/2` | 4 | 查验进项发票：1. 税务平台不可用或未返回可判定结果，自动重试（每小时一次，最多 3 次） → 2〔INV-75a〕；2. 连续重试仍无可判定结果，保持已登记并转财务人工重试 → end / 查验未完成〔INV-75a〕；3. 查验不合规 → end / 已拒收〔INV-75a〕；4. otherwise → 3 | 不适合（N3） | 异常/到期/事件处理，保留原编排与集成记录；可读/版本/留痕见 N3；F0 |
| PRC-73 steps/3/decision | L1754 `processes/steps/3` | 2 | 确认进项发票用途：1. 用途为不可抵扣，需转出 → 4；2. otherwise → end / 已用途确认 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-73 steps/4/decision | L1770 `processes/steps/4` | 1 | 进项转出：1. otherwise → 5 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-73 steps/5/decision | L1784 `processes/steps/5` | 4 | 提交过账候选：1. 所属期间在关账中（已发起关账、未确认），本期过账不接受：返回「期间关账中、可重试」，不进拒收记录，由来源 BC 投递重试 → end / 期间关账中待重试〔INV-92（S-31 SQ-12）；ADR-0045 决定 1〕；2. 所属期间已关账，自动落当前开放期间并标原期间（系统触账不拒绝） → end / 已转出〔ADR-0045 决定 2；INV-92；AR-05〕；3. 过账管道拒收（该法人无开放期间、借贷不平或法人不一致），拒收并告警 → end / 过账被拒收〔INV-92、INV-93；拒收记录由 PRC-158 处置〕；4. otherwise → end / 已转出 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-74 steps/1/decision | L1872 `processes/steps/1` | 4 | 确认应付：1. 来源单据已确认过应付（幂等） → end / 重复事件已忽略；2. 发票查验不合规或已被占用（INV-74、INV-75a）→ 拒收并告警，退回供方换票，换票经 PRC-73 用途确认后由事件再入 → end / 已拒收〔INV-74、INV-75a〕；3. 来源为供方履约确认且发票未到：不确认应付，暂估已在履约确认过账时贷记（AGG-52 的占用前置不变，不做无票应付），取得发票经 PRC-73 用途确认后由事件再入 → end / 等待发票〔S-31 SQ-06②；INV-74；AGG-57 履约确认过账暂估〕；4. otherwise → 2 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-74 steps/2/decision | L1911 `processes/steps/2` | 4 | 提交过账候选：1. 所属期间在关账中（已发起关账、未确认），本期过账不接受：返回「期间关账中、可重试」，不进拒收记录，由来源 BC 投递重试 → end / 期间关账中待重试〔INV-92（S-31 SQ-12）；ADR-0045 决定 1〕；2. 所属期间已关账，自动落当前开放期间并标原期间（系统触账不拒绝） → end / 应付已确认（往来款项未结）〔ADR-0045 决定 2；INV-92；AR-05〕；3. 过账管道拒收（该法人无开放期间、借贷不平或法人不一致），拒收并告警 → end / 过账被拒收〔INV-92、INV-93；拒收记录由 PRC-158 处置〕；4. otherwise → end / 应付已确认（往来款项未结） | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-75 steps/1/decision | L2070 `processes/steps/1` | 8 | 读取收款账户与资金账户：1. 收款方不是已登记账户（INV-80），或单位已停用或合并、供应商角色未合格（INV-03c、INV-04b） → end / 已拒收〔INV-80、INV-03c、INV-04b、AR-08〕；2. 供方来源的应付所引用的供方协议无依据或未生效（AR-04） → end / 已拒收〔AR-04〕；3. 收款方不是供方协议登记的相对方或其登记账户（AR-09） → end / 已拒收〔AR-09〕；4. 付款对象为员工（报销应付、借款归还、薪酬）而无已登记收款账户 → end / 已拒收〔INV-80（员工侧）〕；5. 应付存在未决争议（AGG-52 争议状态） → end / 已拒收〔INV-130（应付争议状态拒绝付款）〕；6. 协议付款条件项未到期（提前付款），预警并在审批待办标注 → 3〔付款条件项到期日（提前付款预警，规则提示不拦；S-29 SQ-54）〕；7. 收款账户在本次付款前新近变更，且为变更后首笔付款（只预警，审批待办标「收款账户新变更」） → 2〔P-01〕；8. otherwise → 3 | 待证据（U1） | 仅评估政策选择片段；完整条目/阈值/例外待补，余额、授权、状态等硬约束继续原守护；可读/版本/留痕见 U1；F0 |
| PRC-75 steps/2/decision | L2132 `processes/steps/2` | 2 | 审批付款：1. 审批人与发起人或出纳为同一人 → 驳回 → 4〔INV-81〕；2. otherwise → 5 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-75 steps/3/decision | L2192 `processes/steps/3` | 3 | 发起付款：1. 付款超过应付未付余额 → end / 已拒收〔INV-79〕；2. 占用成功而建付款失败 → 4；3. otherwise → 2 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-75 steps/4/decision | L2214 `processes/steps/4` | 3 | 释放核销占用：1. 来源为发起人撤回 → end / 已撤回；2. 来源为审批驳回 → end / 已驳回；3. otherwise → end / 发起失败已补偿 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-75 steps/5/decision | L2239 `processes/steps/5` | 4 | 登记银行结果：1. 出纳与发起人或审批人为同一人 → 拒收并告警，改由其他出纳登记 → 5〔INV-81〕；2. 银行回单金额与分配合计不符（INV-78a）→ 拒收并告警，退回出纳更正 → 5〔INV-78a〕；3. 银行结果为失败或退票 → 6；4. otherwise → end / 付款已成功 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-75 steps/6/decision | L2286 `processes/steps/6` | 2 | 登记付款失败：1. 重新发起付款 → 3；2. otherwise → end / 付款已失败 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-75 steps/7/decision | L2309 `processes/steps/7` | 2 | 登记退票：1. 付款尚未核销或已失败，不适用退票登记 → end / 已拒收〔状态机：仅已核销可登记退票〕；2. otherwise → end / 已登记退票 | 不适合（N3） | 异常/到期/事件处理，保留原编排与集成记录；可读/版本/留痕见 N3；F0 |
| PRC-75 steps/8/decision | L2334 `processes/steps/8` | 2 | 冲回应付核销：1. 该付款的核销已冲回（事件重复，幂等） → end / 重复事件已忽略〔INV-94〕；2. otherwise → 9 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-75 steps/9/decision | L2350 `processes/steps/9` | 4 | 冲销并重过账：1. 所属期间在关账中（已发起关账、未确认），本期过账不接受：返回「期间关账中、可重试」，不进拒收记录，由来源 BC 投递重试 → end / 期间关账中待重试〔INV-92（S-31 SQ-12）；ADR-0045 决定 1〕；2. 所属期间已关账，自动落当前开放期间并标原期间（系统触账不拒绝） → end / 退票已冲回〔ADR-0045 决定 2；INV-92；AR-05〕；3. 过账管道拒收（原事实不存在或已被冲销、该法人无开放期间、借贷不平），拒收并告警 → end / 过账被拒收〔INV-92、INV-93、INV-95；拒收记录由 PRC-158 处置〕；4. otherwise → end / 退票已冲回 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-76 steps/1/decision | L2431 `processes/steps/1` | 2 | 完成供方协议：1. 累计达协议金额或额度用尽 → 2；2. otherwise → end / 未达完成条件 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-76 steps/3/decision | L2486 `processes/steps/3` | 1 | 结算供方协议：1. otherwise → end / 已结算 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-76 steps/5/decision | L2549 `processes/steps/5` | 1 | 终止供方协议：1. otherwise → end / 已终止 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-77 steps/1/decision | L2628 `processes/steps/1` | 2 | 读取销项发票与分录余额：1. 读不到销项发票、分录余额或所属期 → 拒收并告警 → end / 已拒收；2. otherwise → 2 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-77 steps/2/decision | L2662 `processes/steps/2` | 1 | 生成申报：1. otherwise → 3 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-77 steps/3/decision | L2680 `processes/steps/3` | 5 | 提交申报：1. 申报端未返回回执（不可达）→ 告警，重新提交 → 3；2. 申报端回执退回或需更正 → 5；3. 应纳税额为零或留抵（零申报、留抵结转），无需缴款 → end / 已申报无需缴款〔P-39〕；4. 提交时已过法定申报期截止日（逾期申报）：告警，滞纳金与罚款由税务端核定，缴款时一并登记 → 4〔P-39〕；5. otherwise → 4 | 不适合（N3） | 异常/到期/事件处理，保留原编排与集成记录；可读/版本/留痕见 N3；F0 |
| PRC-77 steps/4/decision | L2729 `processes/steps/4` | 2 | 登记缴款：1. 完税凭证金额与税费应付的付款结果不符 → 退回出纳核对，告警 → 4；2. otherwise → 6 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-77 steps/5/decision | L2760 `processes/steps/5` | 2 | 更正申报：1. 申报端无法更正，经办人登记放弃及原因（留痕） → end / 已放弃申报；2. otherwise → 3 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-77 steps/6/decision | L2775 `processes/steps/6` | 4 | 提交过账候选：1. 所属期间在关账中（已发起关账、未确认），本期过账不接受：返回「期间关账中、可重试」，不进拒收记录，由来源 BC 投递重试 → end / 期间关账中待重试〔INV-92（S-31 SQ-12）；ADR-0045 决定 1〕；2. 所属期间已关账，自动落当前开放期间并标原期间（系统触账不拒绝） → end / 已缴款〔ADR-0045 决定 2；INV-92；AR-05〕；3. 过账管道拒收（该法人无开放期间、借贷不平或法人不一致），拒收并告警 → end / 过账被拒收〔INV-92、INV-93；拒收记录由 PRC-158 处置〕；4. otherwise → end / 已缴款 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-87 steps/1/decision | L2889 `processes/steps/1` | 2 | 读取项目与人员：1. 所归属项目已结项、已终止或已取消（INV-39） → end / 已拒收（项目已关闭）〔INV-39b〕；2. otherwise → 2 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-87 steps/2/decision | L2925 `processes/steps/2` | 3 | 占用进项发票入账：1. 补贴行或无票凭证，不占用进项发票 → 3；2. 发票查验不合规（INV-75）或已被占用（INV-74） → end / 已拒收（发票不可用）〔INV-74、INV-75〕；3. otherwise → 3 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-87 steps/3/decision | L2957 `processes/steps/3` | 2 | 预算占用：1. 可用预算不足（只预警，审批待办标「超预算」） → 4〔预算策略（警）、AR-03〕；2. otherwise → 4 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-87 steps/4/decision | L2978 `processes/steps/4` | 2 | 提交费用报销：1. 原始凭证要素不全（INV-84） → 8〔INV-84〕；2. otherwise → 5 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-87 steps/6/decision | L3045 `processes/steps/6` | 2 | 核定费用报销：1. 纸质原件迟迟未到、重复报销或费用类别不符，核定退回 → 8；2. otherwise → 7 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-87 steps/7/decision | L3086 `processes/steps/7` | 4 | 提交过账候选：1. 过账管道拒收（该法人无开放期间、借贷不平或法人不一致），拒收并告警 → end / 过账被拒收〔INV-92、INV-93；拒收记录由 PRC-158 处置〕；2. 所属期间在关账中（已发起关账、未确认），本期过账不接受：返回「期间关账中、可重试」，不进拒收记录，由来源 BC 投递重试 → end / 期间关账中待重试〔INV-92（S-31 SQ-12）；ADR-0045 决定 1〕；3. 所属期间已关账（自动落当前开放期间并标原期间） → end / 已核定（所属期间确定，增值税发票已入账）〔ADR-0045 决定 2；INV-92〕；4. otherwise → end / 已核定（所属期间确定，增值税发票已入账） | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-87 steps/9/decision | L3134 `processes/steps/9` | 2 | 驳回释放：1. 来源为报销人撤回 → end / 已撤回；2. otherwise → end / 已退回 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-87 steps/11/decision | L3186 `processes/steps/11` | 2 | 冲销费用报销：1. 报销已付或其应付已有核销 → end / 已拒收（冲销不适用）〔状态机：仅已核定可冲销〕；2. otherwise → 12 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-87 steps/12/decision | L3213 `processes/steps/12` | 4 | 冲销并重过账：1. 所属期间在关账中（已发起关账、未确认），本期过账不接受：返回「期间关账中、可重试」，不进拒收记录，由来源 BC 投递重试 → end / 期间关账中待重试〔INV-92（S-31 SQ-12）；ADR-0045 决定 1〕；2. 所属期间已关账，自动落当前开放期间并标原期间（系统触账不拒绝） → end / 费用报销已冲销〔ADR-0045 决定 2；INV-92；AR-05〕；3. 过账管道拒收（原事实不存在或已被冲销、该法人无开放期间、借贷不平），拒收并告警 → end / 过账被拒收〔INV-92、INV-93、INV-95；拒收记录由 PRC-158 处置〕；4. otherwise → end / 费用报销已冲销 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-92 steps/1/decision | L3305 `processes/steps/1` | 6 | 校验目标期间：1. 同一幂等键已处理（重试或重复调用）→ 先于期间闸门返回原结果 → end / 幂等已返回原结果〔INV-94〕；2. 显式指定的落账期间（第 13 期通道、受控手工凭证、期末结转运行）所指期间未开放（已关账、关账中、未启用），拒绝并告警 → end / 已拒收〔INV-92；ADR-0045 守护第 3 条；S-31 SQ-32〕；3. 业务日期所在期间在关账中（已发起、未确认），本期过账不接受：返回「期间关账中、可重试」，不进拒收记录，由来源 BC 投递重试 → end / 期间关账中待重试〔INV-92（S-31 SQ-12）；ADR-0045 决定 1〕；4. 业务日期所在期间已关账且该法人有开放期间（含确认关账后重试到达的），路由到开放期间并标原期间 → 2〔INV-92〕；5. 该法人无开放期间可落 → end / 已拒收〔INV-92〕；6. otherwise → 2 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-92 steps/2/decision | L3347 `processes/steps/2` | 4 | 过账：1. 同一幂等键已处理，返回原 PostSeq，不重复追加 → end / 幂等已返回原结果〔INV-94〕；2. 借贷不平，拒收并告警 → end / 已拒收〔INV-93〕；3. 法人不一致，拒收并告警 → end / 已拒收〔INV-93〕；4. otherwise → end / 已过账 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-92 steps/3/decision | L3442 `processes/steps/3` | 6 | 冲销并重过账：1. 原事实不存在或已被冲销过，拒收并告警 → end / 已拒收〔INV-95〕；2. 该更正请求的幂等键已处理，返回原结果 → end / 幂等已返回原结果〔INV-94〕；3. 重做的新事实借贷不平或法人不一致，拒收并告警 → end / 已拒收〔INV-93〕；4. 更正所落的开放期间正在关账中，返回「期间关账中、可重试」，由来源 BC 投递重试 → end / 期间关账中待重试〔INV-92（S-31 SQ-12）〕；5. 该法人无开放期间可落，拒收并告警 → end / 已拒收〔INV-92〕；6. otherwise → end / 已更正 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-93 steps/1/decision | L3589 `processes/steps/1` | 4 | 生成分录：1. 同一事实的分录已生成（重复事件） → end / 重复事件已忽略〔INV-96〕；2. 来源不在控制科目授权表，或映射表找不到对应类别，进待处理队列并告警；补缺版本能否生效于当前开放期间首日是 U-27（待用户定） → end / 已进待处理队列〔INV-98〕；3. 事实只记权责账簿（客户确认收入、成本转入等），无收付口径分录 → end / 已记账无需推送〔D17-1〕；4. otherwise → 2 | 适合（D1） | 仅科目/账簿/模板选择可成表；幂等、控制科目授权、金额与双账事务保留；可读/版本/留痕见 D1；F0 |
| PRC-93 steps/2/decision | L3639 `processes/steps/2` | 4 | 推送与重投：1. 该凭证已推送（幂等键相同），不重复投递 → end / 已推送；2. 凭证依赖的客商、部门、人员主数据尚未同步到 U8C，挂起待重投 → end / 已挂起待重投〔推送状态「挂起」，每日定时重投（S-29 SQ-50；不新增主数据已同步事件，只做当期）〕；3. U8C 拒收，带返回码进死信并当日告警 → PRC-135；4. otherwise → end / 已推送 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-94 steps/1/decision | L3734 `processes/steps/1` | 4 | 制单手工凭证：1. 制单人放弃，作废草稿 → 5〔草稿可作废（S-29 SQ-46）〕；2. 缺理由或附件，或触及控制科目而来源通道不在授权表（INV-98） → end / 已拒收〔INV-108〕；3. 只影响一个账簿而未带差异类别代码 → end / 已拒收〔INV-97〕；4. otherwise → 2 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-94 steps/2/decision | L3769 `processes/steps/2` | 3 | 复核手工凭证：1. 复核人与制单人为同一人，命令被拒，改由他人复核 → 2〔INV-108〕；2. 复核不通过，退回制单 → 1〔INV-108〕；3. otherwise → 3 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-94 steps/3/decision | L3788 `processes/steps/3` | 3 | 手工凭证审批：1. 审批人与制单人或复核人为同一人，驳回 → 1；2. 审批驳回，退回制单 → 1；3. otherwise → 4 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-94 steps/4/decision | L3839 `processes/steps/4` | 5 | 提交过账候选：1. 显式指定的落账期间未开放（已关账、关账中或未启用），拒收并告警 → end / 已拒收〔INV-92；S-31 SQ-32〕；2. 过账管道拒收（借贷不平、法人不一致、无开放期间），拒收并告警 → end / 已拒收〔INV-93；拒收记录由 PRC-158 处置〕；3. 所属期间在关账中（已发起关账、未确认），本期过账不接受：返回「期间关账中、可重试」，凭证保持待提交，待确认关账或退回开放后再提交 → end / 期间关账中待重试〔INV-92（S-31 SQ-12）；ADR-0045 决定 1〕；4. 业务日期所在期间已关账：规则提示「将落当前开放期间并标原期间」，制单人确认后提交，不设审批 → end / 已提交过账〔ADR-0045 决定 2；INV-92；AR-05〕；5. otherwise → end / 已提交过账 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-94 steps/5/decision | L3890 `processes/steps/5` | 1 | 作废手工凭证草稿：1. otherwise → end / 已作废 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-95 steps/2/decision | L3972 `processes/steps/2` | 3 | 生成期末结转运行：1. 本法人本期间本模板已有有效运行 → end / 已有有效运行〔INV-109〕；2. 期末结转运行按（法人, 期间, 模板）显式指定期间，所指期间未开放（已关账或关账中），拒绝并告警（INV-92 写法，不是例外） → end / 已拒收〔INV-92；ADR-0045 守护第 3 条；S-31 SQ-32〕；3. otherwise → 3 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-95 steps/3/decision | L4001 `processes/steps/3` | 2 | 提交过账候选：1. 过账管道拒收（借贷不平、无开放期间），拒收并告警 → end / 已拒收〔INV-93；拒收记录由 PRC-158 处置〕；2. otherwise → end / 已过账 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-95 steps/5/decision | L4060 `processes/steps/5` | 3 | 冲销期末结转运行：1. 原运行所属期间已关账，不再冲原运行，差额进下期运行 → end / 差额进下期运行〔ADR-0045 决定 2〕；2. 该运行已冲销过 → end / 已拒收〔状态机：仅已过账可冲销〕；3. otherwise → 6 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-95 steps/6/decision | L4091 `processes/steps/6` | 4 | 冲销并重过账：1. 所属期间在关账中（已发起关账、未确认），本期过账不接受：返回「期间关账中、可重试」，不进拒收记录，由来源 BC 投递重试 → end / 期间关账中待重试〔INV-92（S-31 SQ-12）；ADR-0045 决定 1〕；2. 所属期间已关账，自动落当前开放期间并标原期间（系统触账不拒绝） → end / 期末结转已冲销〔ADR-0045 决定 2；INV-92；AR-05〕；3. 过账管道拒收（原事实不存在或已被冲销、该法人无开放期间、借贷不平），拒收并告警 → end / 过账被拒收〔INV-92、INV-93、INV-95；拒收记录由 PRC-158 处置〕；4. otherwise → end / 期末结转已冲销 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-96 steps/1/decision | L4206 `processes/steps/1` | 5 | 读取已过账事实：1. 本期已有有效的人工成本分配运行（每期间一次有效） → end / 本期已分配〔INV-89〕；2. 目标期间尚未开放（该法人无开放期间可落），拒收并告警 → end / 已拒收〔INV-92；拒收记录由 PRC-158 处置〕；3. 工时与薪酬事实未齐备（有工时无薪酬或有薪酬无工时，预警） → end / 未到分配条件〔P-41〕；4. 目标期间已关账，自动落当前开放期间并标原期间后继续分配 → 2〔ADR-0045 决定 2；INV-92；AR-05〕；5. otherwise → 2 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-96 steps/3/decision | L4260 `processes/steps/3` | 2 | 确认人工成本分配：1. 分配合计不等于实际人工成本（零容差） → 2〔INV-89〕；2. otherwise → 4 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-96 steps/4/decision | L4276 `processes/steps/4` | 5 | 提交过账候选：1. 所属期间在关账中（已发起关账、未确认），本期过账不接受：返回「期间关账中、可重试」，不进拒收记录，由来源 BC 投递重试 → end / 期间关账中待重试〔INV-92（S-31 SQ-12）；ADR-0045 决定 1〕；2. 落账期间已关账且该法人有开放期间，自动落当前开放期间并标原期间 → end / 人工成本分配已过账〔ADR-0045 决定 2；INV-92；AR-05〕；3. 该法人无开放期间可落（过账管道拒收），拒收并告警 → end / 已拒收〔INV-92；拒收记录由 PRC-158 处置〕；4. 过账管道拒收（借贷不平或法人不一致），拒收并告警 → end / 已拒收〔INV-93；拒收记录由 PRC-158 处置〕；5. otherwise → end / 人工成本分配已过账 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-96 steps/5/decision | L4317 `processes/steps/5` | 2 | 作废人工成本分配运行：1. 该运行已过账，不能作废（改走冲销） → end / 已拒收〔状态机：仅试算或已确认可作废〕；2. otherwise → end / 人工成本分配已作废 | 不适合（N4） | 运算/生效数据查询或固定口径，保留参数/来源版本；可读/版本/留痕见 N4；F0 |
| PRC-96 steps/7/decision | L4368 `processes/steps/7` | 3 | 冲销人工成本分配运行：1. 原运行所属期间已关账，不再冲原运行，差额进下期运行 → end / 差额进下期运行〔ADR-0045 决定 2〕；2. 该运行已冲销过 → end / 已拒收〔状态机：仅已过账可冲销〕；3. otherwise → 8 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-96 steps/8/decision | L4399 `processes/steps/8` | 4 | 冲销并重过账：1. 所属期间在关账中（已发起关账、未确认），本期过账不接受：返回「期间关账中、可重试」，不进拒收记录，由来源 BC 投递重试 → end / 期间关账中待重试〔INV-92（S-31 SQ-12）；ADR-0045 决定 1〕；2. 所属期间已关账，自动落当前开放期间并标原期间（系统触账不拒绝） → end / 人工成本分配已冲销〔ADR-0045 决定 2；INV-92；AR-05〕；3. 过账管道拒收（原事实不存在或已被冲销、该法人无开放期间、借贷不平），拒收并告警 → end / 过账被拒收〔INV-92、INV-93、INV-95；拒收记录由 PRC-158 处置〕；4. otherwise → end / 人工成本分配已冲销 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-97 steps/1/decision | L4483 `processes/steps/1` | 2 | 编制分摊规则版本：1. 规则引用的组织单元不存在或已失效，拒收并告警 → end / 已拒收；2. otherwise → 2 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-97 steps/2/decision | L4504 `processes/steps/2` | 2 | 批准分摊规则版本：1. 批准人与编制人为同一人，命令被拒，改由他人批准 → 2〔INV-131（S-31 SQ-10）〕；2. otherwise → 3 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-97 steps/3/decision | L4553 `processes/steps/3` | 2 | 生效分摊规则版本：1. 生效日不是期间首日或早于当前开放期间首日（不追溯），拒收并告警 → end / 已拒收〔INV-91〕；2. otherwise → end / 分摊规则已生效 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-98 steps/1/decision | L4631 `processes/steps/1` | 4 | 试算分摊运行：1. 本期间已有有效的全量分摊运行 → end / 本期已分摊〔INV-90〕；2. 期间内没有生效的分摊规则版本，拒收并告警 → end / 已拒收〔INV-91〕；3. 分摊基数为零，结转下期 → end / 已结转下期〔P-42〕；4. otherwise → 2 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-98 steps/2/decision | L4670 `processes/steps/2` | 2 | 生效分摊运行：1. 分摊合计不等于池金额 → 1〔INV-90〕；2. otherwise → end / 分摊已生效 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-98 steps/4/decision | L4728 `processes/steps/4` | 3 | 冲销分摊运行：1. 原运行所属期间已关账，不再冲原运行，差额进下期运行 → end / 差额进下期运行〔ADR-0045 决定 2〕；2. 该运行已冲销过 → end / 已拒收〔状态机：仅已过账可冲销〕；3. otherwise → 5 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-98 steps/5/decision | L4759 `processes/steps/5` | 4 | 冲销并重过账：1. 所属期间在关账中（已发起关账、未确认），本期过账不接受：返回「期间关账中、可重试」，不进拒收记录，由来源 BC 投递重试 → end / 期间关账中待重试〔INV-92（S-31 SQ-12）；ADR-0045 决定 1〕；2. 所属期间已关账，自动落当前开放期间并标原期间（系统触账不拒绝） → end / 分摊已冲销〔ADR-0045 决定 2；INV-92；AR-05〕；3. 过账管道拒收（原事实不存在或已被冲销、该法人无开放期间、借贷不平），拒收并告警 → end / 过账被拒收〔INV-92、INV-93、INV-95；拒收记录由 PRC-158 处置〕；4. otherwise → end / 分摊已冲销 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-99 steps/1/decision | L4859 `processes/steps/1` | 2 | 读取供方协议：1. 资产来源于供方协议但协议不存在或未生效，拒收并告警 → end / 已拒收；2. otherwise → 2 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-99 steps/2/decision | L4884 `processes/steps/2` | 2 | 登记公司资产：1. 法人主体、资产类别或取得日期缺失或无效（含 IT 资产类别），拒收并告警 → end / 已拒收；2. otherwise → end / 资产已建卡 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-99 steps/3/decision | L4920 `processes/steps/3` | 2 | 计提折旧与摊销：1. 本期无在用且未提足的资产卡片 → end / 本期无应计提；2. otherwise → 6 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-99 steps/5/decision | L4979 `processes/steps/5` | 3 | 处置资产：1. 资产卡片不存在，拒收并告警 → end / 已拒收；2. 资产卡片已处置（重复调用，幂等返回原结果） → end / 已过账；3. otherwise → 6 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-99 steps/6/decision | L5011 `processes/steps/6` | 5 | 提交过账候选：1. 所属期间在关账中（已发起关账、未确认），本期过账不接受：返回「期间关账中、可重试」，不进拒收记录，由来源 BC 投递重试 → end / 期间关账中待重试〔INV-92（S-31 SQ-12）；ADR-0045 决定 1〕；2. 落账期间已关账且该法人有开放期间，自动落当前开放期间并标原期间 → end / 已过账〔ADR-0045 决定 2；INV-92；AR-05〕；3. 该法人无开放期间可落（过账管道拒收），拒收并告警 → end / 已拒收〔INV-92；拒收记录由 PRC-158 处置〕；4. 过账管道拒收（借贷不平或法人不一致），拒收并告警 → end / 已拒收〔INV-93；拒收记录由 PRC-158 处置〕；5. otherwise → end / 已过账 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-100 steps/2/decision | L5105 `processes/steps/2` | 4 | 发布科目表版本：1. 批准人与起草人为同一人，命令被拒，改由他人批准 → 2〔INV-131（S-31 SQ-10）〕；2. 生效日不是期间首日，或该期间已关账，拒收并告警 → end / 已拒收〔INV-91；INV-92（显式指定期间，已关账或关账中拒绝）〕；3. 新科目表版本新增或停用了科目，须同步更新映射表 → 3；4. otherwise → end / 已发布 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-100 steps/4/decision | L5147 `processes/steps/4` | 4 | 发布映射表版本：1. 批准人与起草人为同一人，命令被拒，改由他人批准 → 4〔INV-131（S-31 SQ-10）〕；2. 生效日不是期间首日，或该期间已关账，拒收并告警 → end / 已拒收〔INV-91；INV-92（显式指定期间，已关账或关账中拒绝）〕；3. 新映射表版本改变了对账类别或控制科目归属，须同步更新授权表 → 5；4. otherwise → end / 已发布 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-100 steps/6/decision | L5192 `processes/steps/6` | 3 | 发布授权表版本：1. 批准人与起草人为同一人，命令被拒，改由他人批准 → 6〔INV-131（S-31 SQ-10）〕；2. 生效日不是期间首日，或该期间已关账，拒收并告警 → end / 已拒收〔INV-91；INV-92（显式指定期间，已关账或关账中拒绝）〕；3. otherwise → end / 已发布 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-100 steps/7/decision | L5226 `processes/steps/7` | 1 | 起草期末结转模板版本：1. otherwise → 8 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-100 steps/8/decision | L5238 `processes/steps/8` | 3 | 发布期末结转模板版本：1. 批准人与起草人为同一人，命令被拒，改由他人批准 → 8〔INV-131（S-31 SQ-10）〕；2. 生效日不是期间首日，或该期间已关账（含关账中），拒收并告警 → end / 已拒收〔INV-136；INV-92〕；3. otherwise → end / 已发布 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-100 steps/9/decision | L5266 `processes/steps/9` | 1 | 起草现金流量项目表版本：1. otherwise → 10 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-100 steps/10/decision | L5278 `processes/steps/10` | 3 | 发布现金流量项目表版本：1. 批准人与起草人为同一人，命令被拒，改由他人批准 → 10〔INV-131（S-31 SQ-10）〕；2. 生效日不是期间首日，或该期间已关账（含关账中），拒收并告警 → end / 已拒收〔INV-136；INV-92〕；3. otherwise → end / 已发布 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-101 steps/1/decision | L5366 `processes/steps/1` | 2 | 月结检查：1. 前置检查未通过 → end / 检查未通过保持开放〔INV-100；INV-135〕；2. otherwise → 2 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-101 steps/2/decision | L5418 `processes/steps/2` | 2 | 发起关账：1. 期间已关账或已在关账中，拒收并告警 → end / 已拒收〔INV-92；ADR-0045 决定 1〕；2. otherwise → 3 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-101 steps/3/decision | L5444 `processes/steps/3` | 3 | 确认关账：1. 确认人与发起人为同一人，命令被拒，改由他人确认 → 3；2. 确认时前置检查未通过 → 4〔INV-100〕；3. otherwise → end / 已关账 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-101 steps/4/decision | L5503 `processes/steps/4` | 1 | 退回开放：1. otherwise → 1 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-102 steps/1/decision | L5586 `processes/steps/1` | 3 | 读取第 13 期启用登记：1. 本年度已有生效的第 13 期启用登记 → 3〔INV-104〕；2. 财务决定本年度不启用第 13 期，12 月转月结关账 → PRC-101〔INV-104〕；3. otherwise → 2 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-102 steps/2/decision | L5613 `processes/steps/2` | 2 | 登记第 13 期启用：1. 该年度任一法人 12 月已关账，启用状态已锁定，拒收并告警 → end / 已拒收〔INV-104〕；2. otherwise → 3 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-102 steps/3/decision | L5648 `processes/steps/3` | 2 | 开放第 13 期：1. 本年度未启用第 13 期，拒收并告警 → end / 已拒收〔INV-104〕；2. otherwise → 4 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-102 steps/4/decision | L5673 `processes/steps/4` | 2 | 月结检查：1. 前置检查未通过 → end / 检查未通过保持开放〔INV-100〕；2. otherwise → 5 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-102 steps/5/decision | L5725 `processes/steps/5` | 2 | 发起年末关账：1. 12 月或第 13 期已关账，或两期未同时处于开放，拒收并告警 → end / 已拒收〔INV-99〕；2. otherwise → 6 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-102 steps/6/decision | L5751 `processes/steps/6` | 3 | 确认年末关账：1. 确认人与发起人为同一人，命令被拒，改由他人确认 → 6；2. 确认时前置检查未通过 → 7〔INV-100〕；3. otherwise → end / 年末已关账 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-102 steps/7/decision | L5807 `processes/steps/7` | 1 | 退回开放：1. otherwise → 4 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-102 steps/8/decision | L5826 `processes/steps/8` | 2 | 开立会计年度：1. 该法人该年度已开立（幂等） → end / 已拒收〔INV-94〕；2. otherwise → end / 会计年度已开立 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-103 steps/1/decision | L5892 `processes/steps/1` | 3 | 生成快照：1. 事件载荷缺法人或期间，拒收并告警（由每日补生成触发兜底） → end / 已拒收；2. 本（法人，账簿，期间）快照已存在（重复事件） → end / 重复事件已忽略〔INV-106〕；3. otherwise → end / 快照已生成 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-104 steps/1/decision | L5971 `processes/steps/1` | 2 | 读取外部法定账余额：1. U8C 余额读取失败或无响应，告警并结束，待重试 → end / 对账未完成；2. otherwise → 2 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-104 steps/2/decision | L6000 `processes/steps/2` | 2 | 登记外部记账确认：1. 记账结果对应的凭证未推送或外部键不符，不置已确认记账，作为对账差异在下一步登记 → 3〔ADR-0022 决定 8（推送幂等外部键）〕；2. otherwise → 3 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-104 steps/3/decision | L6016 `processes/steps/3` | 2 | 登记对账差异：1. 差异清零 → end / 差异已清零〔INV-107〕；2. otherwise → 4 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-104 steps/4/decision | L6040 `processes/steps/4` | 3 | 签字接受差异：1. 签字人与登记差异者为同一人，命令被拒，改由他人签字 → 4；2. 不接受差异，退回重新查明并登记 → 3；3. otherwise → end / 已签字接受〔INV-107〕 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-105 steps/1/decision | L6123 `processes/steps/1` | 2 | 编制报表行定义版本：1. 映射的科目在所引用的科目表版本中不存在，拒收并告警 → end / 已拒收；2. otherwise → 2 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-105 steps/2/decision | L6145 `processes/steps/2` | 4 | 生效报表行定义版本：1. 评审生效人与编制人为同一人，命令被拒，改由他人评审 → 2；2. 评审不通过，退回编制 → 1；3. 生效日不是期间首日，或该期间已关账（含关账中），拒收并告警 → end / 已拒收〔INV-91a〕；4. otherwise → end / 已生效 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-111 transitions/1/decision | L6297 `processes/transitions/1` | 1 | 登记出资认缴：1. otherwise → end / 出资已认缴 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-111 transitions/2/decision | L6309 `processes/transitions/2` | 2 | 登记出资实缴：1. 累计实缴将超过认缴额 → end / 已拒收〔INV-127〕；2. otherwise → end / 出资已实缴 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-111 transitions/3/decision | L6332 `processes/transitions/3` | 2 | 登记出资实缴：1. 累计实缴将超过认缴额 → end / 已拒收〔INV-127〕；2. otherwise → end / 出资已实缴 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-111 transitions/4/decision | L6355 `processes/transitions/4` | 2 | 更正出资登记：1. 更正后累计实缴为负或超过认缴额 → end / 已拒收〔INV-127〕；2. otherwise → end / 出资实缴已更正 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-111 transitions/5/decision | L6375 `processes/transitions/5` | 2 | 更正出资登记：1. 更正后累计实缴为负或超过认缴额 → end / 已拒收〔INV-127〕；2. otherwise → end / 出资实缴已更正 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-111 transitions/6/decision | L6395 `processes/transitions/6` | 2 | 更正出资登记：1. 更正后累计实缴为负或超过认缴额 → end / 已拒收〔INV-127〕；2. otherwise → end / 出资实缴已更正 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-111 transitions/7/decision | L6415 `processes/transitions/7` | 3 | 提交过账候选：1. 所属期间已关账，自动落当前开放期间并标原期间（系统触账不拒绝） → end / 出资已实缴〔ADR-0045 决定 2；INV-92；AR-05〕；2. 过账管道拒收（该法人无开放期间、借贷不平或法人不一致），拒收并告警 → end / 过账被拒收〔INV-92、INV-93；拒收记录由 PRC-158 处置〕；3. otherwise → end / 出资已实缴 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-111 transitions/8/decision | L6436 `processes/transitions/8` | 3 | 提交过账候选：1. 所属期间已关账，自动落当前开放期间并标原期间（系统触账不拒绝） → end / 出资已实缴〔ADR-0045 决定 2；INV-92；AR-05〕；2. 过账管道拒收（该法人无开放期间、借贷不平或法人不一致），拒收并告警 → end / 过账被拒收〔INV-92、INV-93；拒收记录由 PRC-158 处置〕；3. otherwise → end / 出资已实缴 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-112 steps/1/decision | L6508 `processes/steps/1` | 2 | 读取已关账年度与可分配利润：1. 所属年度未关账或可分配利润不足 → end / 已拒收〔INV-117〕；2. otherwise → 2 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-112 steps/2/decision | L6539 `processes/steps/2` | 1 | 草拟利润分配：1. otherwise → 3 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-112 steps/3/decision | L6559 `processes/steps/3` | 3 | 决议利润分配：1. 决议未通过 → end / 决议未通过；2. 决议额超过所属年度可分配利润（INV-117）→ 拒收并告警 → end / 已拒收〔INV-117〕；3. otherwise → 4 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-112 steps/4/decision | L6594 `processes/steps/4` | 1 | 提交应付利润：1. otherwise → 5 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-112 steps/5/decision | L6615 `processes/steps/5` | 4 | 提交过账候选：1. 所属期间在关账中（已发起关账、未确认），本期过账不接受：返回「期间关账中、可重试」，不进拒收记录，由来源 BC 投递重试 → end / 期间关账中待重试〔INV-92（S-31 SQ-12）；ADR-0045 决定 1〕；2. 所属期间已关账，自动落当前开放期间并标原期间（系统触账不拒绝） → end / 已决议待支付〔ADR-0045 决定 2；INV-92；AR-05〕；3. 过账管道拒收（该法人无开放期间、借贷不平或法人不一致），拒收并告警 → end / 过账被拒收〔INV-92、INV-93；拒收记录由 PRC-158 处置〕；4. otherwise → end / 已决议待支付 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-112 steps/6/decision | L6645 `processes/steps/6` | 3 | 撤销利润分配决议：1. 撤销决议未通过 → end / 撤销未通过；2. 已有支付（部分支付或已支付），不可撤销，拒收并告警 → end / 已拒收〔状态机：仅已决议可撤销〕；3. otherwise → 7 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-112 steps/7/decision | L6680 `processes/steps/7` | 2 | 〔缺〕冲销应付利润：1. 该决议的应付已有核销（已付款） → end / 已拒收〔状态机：已有支付不可撤销〕；2. otherwise → 8 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-112 steps/8/decision | L6699 `processes/steps/8` | 4 | 冲销并重过账：1. 所属期间在关账中（已发起关账、未确认），本期过账不接受：返回「期间关账中、可重试」，不进拒收记录，由来源 BC 投递重试 → end / 期间关账中待重试〔INV-92（S-31 SQ-12）；ADR-0045 决定 1〕；2. 所属期间已关账，自动落当前开放期间并标原期间（系统触账不拒绝） → end / 决议已撤销〔ADR-0045 决定 2；INV-92；AR-05〕；3. 过账管道拒收（原事实不存在或已被冲销、该法人无开放期间、借贷不平），拒收并告警 → end / 过账被拒收〔INV-92、INV-93、INV-95；拒收记录由 PRC-158 处置〕；4. otherwise → end / 决议已撤销 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-114 steps/1/decision | L6783 `processes/steps/1` | 3 | 登记出资退回：1. 申请被撤回，或减资、退伙决议未通过 → end / 申请未通过；2. 退回额超过已实缴额 → end / 已拒收〔INV-128〕；3. otherwise → 2 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-114 steps/2/decision | L6818 `processes/steps/2` | 1 | 确认应付：1. otherwise → 3 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-114 steps/3/decision | L6833 `processes/steps/3` | 6 | 提交过账候选：1. 过账管道拒收（该法人无开放期间、借贷不平或法人不一致），拒收并告警 → end / 过账被拒收〔INV-92、INV-93；拒收记录由 PRC-158 处置〕；2. 所属期间在关账中（已发起关账、未确认），本期过账不接受：返回「期间关账中、可重试」，不进拒收记录，由来源 BC 投递重试 → end / 期间关账中待重试〔INV-92（S-31 SQ-12）；ADR-0045 决定 1〕；3. 所属期间已关账且退回后已实缴余额为零，自动落当前开放期间并标原期间 → end / 已退回〔ADR-0045 决定 2；INV-92；AR-05〕；4. 所属期间已关账且退回后仍有已实缴余额，自动落当前开放期间并标原期间 → end / 部分退回〔ADR-0045 决定 2；INV-92；AR-05〕；5. 退回后已实缴余额为零 → end / 已退回；6. otherwise → end / 部分退回 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-131 steps/1/decision | L6899 `processes/steps/1` | 4 | 核销应付：1. 该付款已核销过（事件重复，按付款 ID 幂等） → end / 重复事件已忽略；2. 核销占用不存在或与分配行不一致（INV-77a）→ 告警并转人工处置 → 2〔INV-77a〕；3. 核销后未付余额大于零 → end / 应付部分核销；4. otherwise → end / 应付已结清 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-131 steps/2/decision | L6933 `processes/steps/2` | 2 | 人工处置核销差异：1. 选择转冲销付款 → end / 已人工处置；2. otherwise → end / 已人工处置 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-132 steps/1/decision | L6979 `processes/steps/1` | 5 | 置付款已核销：1. 该付款已是已核销（事件重复） → end / 重复事件已忽略；2. 所属期间在关账中（已发起关账、未确认），本期过账不接受：返回「期间关账中、可重试」，不进拒收记录，由来源 BC 投递重试 → end / 期间关账中待重试〔INV-92（S-31 SQ-12）；ADR-0045 决定 1〕；3. 所属期间已关账，自动落当前开放期间并标原期间（系统触账不拒绝） → end / 付款已核销并提交过账〔ADR-0045 决定 2；INV-92；AR-05〕；4. 过账管道拒收（该法人无开放期间、借贷不平或法人不一致），拒收并告警 → end / 过账被拒收〔INV-92、INV-93；拒收记录由 PRC-158 处置〕；5. otherwise → end / 付款已核销并提交过账 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-133 steps/1/decision | L7047 `processes/steps/1` | 4 | 标记费用报销已付：1. 应付来源不是费用报销（供方、薪酬、利润等） → end / 已忽略非报销来源；2. 读不到应付或来源报销不存在 → 拒收并告警 → end / 已拒收；3. 报销已是已付（事件重复） → end / 重复事件已忽略；4. otherwise → end / 费用报销已付 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-158 transitions/1/decision | L7224 `processes/transitions/1` | 2 | 〔缺〕重提过账候选：1. 原拒收原因未消除，重提再次被拒收 → end / 仍被拒收〔INV-92、INV-93、INV-94〕；2. otherwise → end / 已重提 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-158 transitions/2/decision | L7250 `processes/transitions/2` | 2 | 〔缺〕登记拒收处置结论：1. 未写明结案原因 → end / 已拒收〔CS-2（缺失输入失败并告警）〕；2. otherwise → end / 已结案 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-159 steps/1/decision | L7331 `processes/steps/1` | 4 | 确认应付：1. 同一申报已确认过税费应付（事件重复，按来源单据幂等） → end / 重复事件已忽略〔INV-74〕；2. 申报应缴税额为零 → end / 无需确认应付；3. 税务主管机关未登记为往来单位 → end / 已拒收〔AR-08〕；4. otherwise → 2 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-159 steps/2/decision | L7363 `processes/steps/2` | 4 | 提交过账候选：1. 所属期间在关账中（已发起关账、未确认），本期过账不接受：返回「期间关账中、可重试」，不进拒收记录，由来源 BC 投递重试 → end / 期间关账中待重试〔INV-92（S-31 SQ-12）；ADR-0045 决定 1〕；2. 所属期间已关账，自动落当前开放期间并标原期间（系统触账不拒绝） → end / 税费应付已确认〔ADR-0045 决定 2；INV-92；AR-05〕；3. 过账管道拒收（该法人无开放期间、借贷不平或法人不一致），拒收并告警 → end / 过账被拒收〔INV-92、INV-93；拒收记录由 PRC-158 处置〕；4. otherwise → end / 税费应付已确认 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-163 steps/1/decision | L7430 `processes/steps/1` | 7 | 登记利润分配支付：1. 应付来源不是利润分配 → end / 已忽略非利润分配来源；2. 读不到应付或来源利润分配 → 拒收并告警 → end / 已拒收；3. 该应付的支付已登记过（事件重复，按应付 ID 幂等） → end / 重复事件已忽略；4. 利润分配不在已决议或部分支付状态（如决议已撤销，S-29 SQ-48）→ 拒收并告警 → end / 已拒收；5. 本次支付使支付合计超过决议金额（INV-117）→ 拒收并告警 → end / 已拒收〔INV-117〕；6. 登记后仍有未支付的分配额 → end / 已部分支付；7. otherwise → end / 已支付 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-164 steps/1/decision | L7507 `processes/steps/1` | 7 | 冲回成本结转：1. 被更正的事实不是收入确认类型 → end / 非收入确认事实；2. 该事实的已配对结转已冲回过（重复事件，幂等） → end / 已冲回（幂等）〔INV-94〕；3. 该事实没有已配对的成本结转 → end / 无需冲回；4. 所属期间在关账中（已发起关账、未确认），本期过账不接受：返回「期间关账中、可重试」，不进拒收记录，由来源 BC 投递重试 → end / 期间关账中待重试〔INV-92（S-31 SQ-12）；ADR-0045 决定 1〕；5. 冲回所属期间已关账且该法人有开放期间，自动落当前开放期间并标原期间 → end / 已冲回成本结转〔ADR-0045 决定 2；INV-92；AR-05〕；6. 过账管道拒收（该法人无开放期间、借贷不平或法人不一致），拒收并告警 → end / 已拒收〔INV-92、INV-93〕；7. otherwise → end / 已冲回成本结转〔INV-88〕 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-165 transitions/1/decision | L7624 `processes/transitions/1` | 2 | 编制成本核算参数版本：1. N 缺失、不是正整数月，或生效日早于已生效版本 → end / 已拒收；2. otherwise → end / 版本草稿已编制 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-165 transitions/2/decision | L7639 `processes/transitions/2` | 3 | 发布成本核算参数版本：1. 批准人就是编制人 → end / 已拒收〔见 segregation〕；2. 财务负责人退回 → end / 已退回；3. otherwise → end / 版本已生效 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-166 transitions/1/decision | L7724 `processes/transitions/1` | 2 | 〔缺〕编制资产类别与折旧政策版本：1. 折旧方法、年限或残值率缺失或不合法，或生效日早于已生效版本 → end / 已拒收；2. otherwise → end / 版本草稿已编制 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-166 transitions/2/decision | L7739 `processes/transitions/2` | 3 | 发布资产类别与折旧政策版本：1. 批准人就是编制人 → end / 已拒收〔见 segregation〕；2. 财务负责人退回 → end / 已退回；3. otherwise → end / 版本已生效 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-167 transitions/1/decision | L7840 `processes/transitions/1` | 3 | 编制期初余额：1. 该法人与账簿已有生效的期初余额 → end / 已拒收〔INV-133〕；2. 借贷不平衡，或项目拆分合计不等于法人 × 科目余额（差额须挂未分配项目） → end / 已拒收〔INV-134〕；3. otherwise → end / 期初余额已编制 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-167 transitions/2/decision | L7873 `processes/transitions/2` | 4 | 签字确认期初余额：1. 签字人就是编制人 → end / 已拒收〔见 segregation〕；2. 借贷不平衡或拆分合计不符（编制后数据已变） → end / 已拒收〔INV-134〕；3. 财务负责人退回 → end / 已退回；4. otherwise → end / 期初余额已生效〔INV-133〕 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-168 transitions/1/decision | L7961 `processes/transitions/1` | 2 | 〔缺〕编制口径版本：1. 口径内指标定义缺失、四层映射不完整，或生效日早于已生效版本 → end / 已拒收；2. otherwise → end / 版本草稿已编制 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-168 transitions/2/decision | L7976 `processes/transitions/2` | 3 | 发布口径版本：1. 批准人就是编制人 → end / 已拒收〔见 segregation〕；2. 财务负责人退回 → end / 已退回；3. otherwise → end / 版本已生效 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-169 transitions/1/decision | L8086 `processes/transitions/1` | 2 | 登记勾稽差异案：1. 同一勾稽项已有未关闭差异案（重复登记） → end / 重复登记已忽略；2. otherwise → end / 差异案已登记 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-169 transitions/2/decision | L8103 `processes/transitions/2` | 2 | 解释勾稽差异案：1. 差异案已关闭 → end / 已拒收〔INV-135〕；2. otherwise → end / 已解释关闭 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-169 transitions/3/decision | L8121 `processes/transitions/3` | 3 | 修正勾稽差异案：1. 差异案已关闭 → end / 已拒收〔INV-135〕；2. 更正后差异仍超容差 → end / 已拒收〔INV-135〕；3. otherwise → end / 已修正关闭 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-169 transitions/4/decision | L8144 `processes/transitions/4` | 4 | 豁免勾稽差异案：1. 差异案已关闭 → end / 已拒收〔INV-135〕；2. 处置人与确认人为同一人 → end / 已拒收〔见 segregation〕；3. 财务负责人不同意 → end / 已退回；4. otherwise → end / 已豁免关闭〔INV-135〕 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-170 steps/1/decision | L8226 `processes/steps/1` | 3 | 读取归属对象与人员：1. 所归属项目已结项、已终止或已取消 → end / 已拒收（项目已关闭）〔INV-39b〕；2. 费用类别不在本期范围（只做业务招待） → end / 已拒收（类别不在范围）；3. otherwise → 2 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-170 steps/3/decision | L8283 `processes/steps/3` | 2 | 费用事前申请审批：1. 审批已驳回 → 6〔platform.approval 审批结果〕；2. otherwise → 4 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-170 steps/4/decision | L8330 `processes/steps/4` | 1 | 批准费用事前申请：1. otherwise → end / 已批准 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-170 steps/5/decision | L8350 `processes/steps/5` | 2 | 关闭费用事前申请：1. 事前申请不是已批准状态 → end / 已拒收（状态不符）；2. otherwise → end / 已关闭 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-170 steps/6/decision | L8367 `processes/steps/6` | 1 | 驳回费用事前申请：1. otherwise → end / 已驳回 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-170 steps/7/decision | L8386 `processes/steps/7` | 1 | 撤回费用事前申请：1. otherwise → end / 已撤回 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-171 steps/1/decision | L8465 `processes/steps/1` | 3 | 登记差旅订单与月账单：1. 订单已登记过（消息重复，按订单标识幂等） → end / 重复订单已忽略；2. 订单找不到对应的已批出差申请，无法自动归属 → 2；3. otherwise → end / 订单已登记 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-171 steps/2/decision | L8491 `processes/steps/2` | 1 | 归属差旅订单：1. otherwise → end / 订单已登记 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-171 steps/3/decision | L8504 `processes/steps/3` | 3 | 登记差旅订单与月账单：1. 账单已登记过（文件重复） → end / 重复账单已忽略；2. 账单无法解析或与已登记订单严重不符 → 拒收并告警 → end / 账单已拒收；3. otherwise → 4 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-171 steps/4/decision | L8534 `processes/steps/4` | 2 | 核对差旅月账单：1. 存在账单行无订单、订单无账单行、金额不符或订单已被员工报销 → 5〔INV-137〕；2. otherwise → 6 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-171 steps/5/decision | L8557 `processes/steps/5` | 2 | 〔缺〕处理账单差异：1. 服务商更正了账单 → 3；2. otherwise → 6 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-171 steps/6/decision | L8573 `processes/steps/6` | 3 | 确认差旅账单应付：1. 剔除后无可结算订单，或退改后应付为零 → end / 无需确认应付；2. 差旅服务商未作为供方准入合格 → end / 已拒收（服务商未准入）〔INV-03c、INV-04b〕；3. otherwise → 7 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-171 steps/7/decision | L8605 `processes/steps/7` | 4 | 提交过账候选：1. 所属期间在关账中（已发起关账、未确认），本期过账不接受：返回「期间关账中、可重试」，不进拒收记录，由来源 BC 投递重试 → end / 期间关账中待重试〔INV-92（S-31 SQ-12）；ADR-0045 决定 1〕；2. 所属期间已关账，自动落当前开放期间并标原期间（系统触账不拒绝） → end / 应付已确认〔ADR-0045 决定 2；INV-92；AR-05〕；3. 过账管道拒收（该法人无开放期间、借贷不平或法人不一致），拒收并告警 → end / 过账被拒收〔INV-92、INV-93〕；4. otherwise → end / 应付已确认 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-172 steps/1/decision | L8701 `processes/steps/1` | 2 | 读取归属对象与人员：1. 所归属项目已结项、已终止或已取消 → end / 已拒收（项目已关闭）〔INV-39b〕；2. otherwise → 2 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-172 steps/3/decision | L8751 `processes/steps/3` | 2 | 出差申请审批：1. 审批已驳回 → 6〔platform.approval 审批结果〕；2. otherwise → 4 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-172 steps/4/decision | L8798 `processes/steps/4` | 1 | 批准出差申请：1. otherwise → 5 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-172 steps/5/decision | L8813 `processes/steps/5` | 2 | 登记出差申请已同步：1. 推送失败（服务商不可达或拒收）→ 告警并可重试 → end / 同步失败（已告警，可重试）；2. otherwise → end / 出差申请已同步 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-172 steps/6/decision | L8844 `processes/steps/6` | 1 | 驳回出差申请：1. otherwise → end / 已驳回 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-172 steps/7/decision | L8863 `processes/steps/7` | 1 | 撤回出差申请：1. otherwise → end / 已撤回 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-173 steps/1/decision | L8933 `processes/steps/1` | 3 | 登记超标订单待审批：1. 该超标订单已登记过（消息重复，按订单标识幂等） → end / 重复事件已忽略；2. 订单对应的出差申请不存在或未批准（规则拒绝，不设人审） → 4〔〔缺〕INV 超标订单须对应已批出差申请〕；3. otherwise → 2 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-173 steps/3/decision | L9001 `processes/steps/3` | 3 | 批准差旅超标订单：1. 回写失败（服务商不可达或拒收）→ 告警并可重试 → end / 回写失败（已告警，可重试）；2. 审批结论为驳回 → end / 已驳回并回写；3. otherwise → end / 已批准并回写 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-173 steps/4/decision | L9026 `processes/steps/4` | 1 | 驳回差旅超标订单：1. otherwise → end / 已回写拒绝（无已批出差申请） | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-174 transitions/1/decision | L9114 `processes/transitions/1` | 2 | 编制出差补贴标准版本：1. 标准缺差旅类型或天数档位，或生效日早于已生效版本 → end / 已拒收；2. otherwise → end / 版本草稿已编制 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-174 transitions/2/decision | L9129 `processes/transitions/2` | 3 | 发布出差补贴标准版本：1. 批准人就是编制人 → end / 已拒收〔见 segregation〕；2. 财务负责人退回 → end / 已退回；3. otherwise → end / 版本已生效 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-175 steps/1/decision | L9214 `processes/steps/1` | 5 | 读取往来方与归属对象：1. 往来方未登记、已停用或合并，或供方角色未合格 → end / 已拒收〔INV-03c、INV-04b〕；2. 付款类别不在 RULE-407 豁免清单内（清单外照 AR-04 拒绝） → end / 已拒收〔RULE-407 豁免清单；AR-04；S-34 ④〕；3. 归属对象为项目或商机（与项目有关的付款须从项目侧来源单据发起） → end / 已拒收〔S-34 ④；聚合稿 §3.4（应付不依赖交付）〕；4. 付款对象为个人且性质为劳务报酬（外部代发） → end / 已拒收〔须经 B-15 代扣个税，不能走其他应付〕；5. otherwise → 2 | 待证据（U1） | 仅评估政策选择片段；完整条目/阈值/例外待补，余额、授权、状态等硬约束继续原守护；可读/版本/留痕见 U1；F0 |
| PRC-175 steps/3/decision | L9284 `processes/steps/3` | 3 | 直接付款审批：1. 审批已驳回 → end / 已驳回；2. 申请类型为预付款 → 5；3. otherwise → 4 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-175 steps/4/decision | L9344 `processes/steps/4` | 1 | 登记其他应付：1. otherwise → 6 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-175 steps/5/decision | L9359 `processes/steps/5` | 1 | 登记预付款：1. otherwise → end / 预付款已登记待付 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-175 steps/6/decision | L9375 `processes/steps/6` | 4 | 提交过账候选：1. 所属期间在关账中（已发起关账、未确认），本期过账不接受：返回「期间关账中、可重试」，不进拒收记录，由来源 BC 投递重试 → end / 期间关账中待重试〔INV-92（S-31 SQ-12）；ADR-0045 决定 1〕；2. 所属期间已关账，自动落当前开放期间并标原期间（系统触账不拒绝） → end / 其他应付已登记待付〔ADR-0045 决定 2；INV-92；AR-05〕；3. 过账管道拒收（该法人无开放期间、借贷不平或法人不一致），拒收并告警 → end / 过账被拒收〔INV-92、INV-93〕；4. otherwise → end / 其他应付已登记待付 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-176 transitions/1/decision | L9483 `processes/transitions/1` | 5 | 预付款冲抵应付：1. 冲抵额超过预付款余额或应付未付余额 → end / 已拒收〔INV-77a〕；2. 应付在争议中 → end / 已拒收〔INV-130〕；3. 预付款与应付的往来方不一致 → end / 已拒收〔〔缺〕INV 冲抵双方往来方一致〕；4. 冲抵后预付款余额为零 → end / 预付款已冲抵；5. otherwise → end / 预付款已部分冲抵 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-176 transitions/2/decision | L9515 `processes/transitions/2` | 4 | 提交过账候选：1. 所属期间在关账中（已发起关账、未确认），本期过账不接受：返回「期间关账中、可重试」，不进拒收记录，由来源 BC 投递重试 → end / 期间关账中待重试〔INV-92（S-31 SQ-12）；ADR-0045 决定 1〕；2. 所属期间已关账，自动落当前开放期间并标原期间（系统触账不拒绝） → end / 预付款已冲抵〔ADR-0045 决定 2；INV-92；AR-05〕；3. 过账管道拒收（该法人无开放期间、借贷不平或法人不一致），拒收并告警 → end / 过账被拒收〔INV-92、INV-93〕；4. otherwise → end / 预付款已冲抵 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |

### B.1 decision 之外的 guard 与 checks

| 判断点 | 位置 | 数量口径 | 条件 | 结论及属性 |
|---|---|---|---|---|
| PRC-101 steps/1/checks/1 | L5366 `processes/steps/1` | 1 项前置检查 | 过账队列与分录生成队列为空，且过账拒收记录已处置（B-16 队列与拒收记录经 PRC-158 处置、B-20 待处理队列） | 不适合（N2）；关账强制控制，口径参数单独版本化；属性见 N2，F0 |
| PRC-101 steps/1/checks/2 | L5366 `processes/steps/1` | 1 项前置检查 | 推送死信为空（B-20） | 不适合（N2）；关账强制控制，口径参数单独版本化；属性见 N2，F0 |
| PRC-101 steps/1/checks/3 | L5366 `processes/steps/1` | 1 项前置检查 | 损益结转完成（期末结转已过账，AGG-99） | 不适合（N2）；关账强制控制，口径参数单独版本化；属性见 N2，F0 |
| PRC-101 steps/1/checks/4 | L5366 `processes/steps/1` | 1 项前置检查 | 本期受控手工凭证无草稿、待复核、待审批（B-20，AGG-98） | 不适合（N2）；关账强制控制，口径参数单独版本化；属性见 N2，F0 |
| PRC-101 steps/1/checks/5 | L5366 `processes/steps/1` | 1 项前置检查 | 本期人工成本分配与分摊运行已办结或已处理（B-07） | 不适合（N2）；关账强制控制，口径参数单独版本化；属性见 N2，F0 |
| PRC-101 steps/1/checks/6 | L5366 `processes/steps/1` | 1 项前置检查 | 明细账与总账控制科目一致 | 不适合（N2）；关账强制控制，口径参数单独版本化；属性见 N2，F0 |
| PRC-101 steps/1/checks/7 | L5366 `processes/steps/1` | 1 项前置检查 | 现金流量标注完整 | 不适合（N2）；关账强制控制，口径参数单独版本化；属性见 N2，F0 |
| PRC-101 steps/1/checks/8 | L5366 `processes/steps/1` | 1 项前置检查 | 直接法与间接法经营活动现金流量净额相等 | 不适合（N2）；关账强制控制，口径参数单独版本化；属性见 N2，F0 |
| PRC-101 steps/1/checks/9 | L5366 `processes/steps/1` | 1 项前置检查 | 试算平衡 | 不适合（N2）；关账强制控制，口径参数单独版本化；属性见 N2，F0 |
| PRC-101 steps/1/checks/10 | L5366 `processes/steps/1` | 1 项前置检查 | 三条对账等式成立，收付账簿生产成本余额为 0 | 不适合（N2）；关账强制控制，口径参数单独版本化；属性见 N2，F0 |
| PRC-101 steps/1/checks/11 | L5366 `processes/steps/1` | 1 项前置检查 | 银行余额调节已完成（范围待用户定，SQ-52） | 不适合（N2）；关账强制控制，口径参数单独版本化；属性见 N2，F0 |
| PRC-101 steps/1/checks/12 | L5366 `processes/steps/1` | 1 项前置检查 | 勾稽差异案（AGG-126）无未关闭的超容差差异案，豁免须财务负责人确认（INV-135） | 不适合（N2）；关账强制控制，口径参数单独版本化；属性见 N2，F0 |
| PRC-102 steps/4/checks/1 | L5673 `processes/steps/4` | 1 项前置检查 | 过账队列与分录生成队列为空，且过账拒收记录已处置（B-16 队列与拒收记录经 PRC-158 处置、B-20 待处理队列） | 不适合（N2）；关账强制控制，口径参数单独版本化；属性见 N2，F0 |
| PRC-102 steps/4/checks/2 | L5673 `processes/steps/4` | 1 项前置检查 | 推送死信为空（B-20） | 不适合（N2）；关账强制控制，口径参数单独版本化；属性见 N2，F0 |
| PRC-102 steps/4/checks/3 | L5673 `processes/steps/4` | 1 项前置检查 | 损益结转完成（期末结转已过账，AGG-99） | 不适合（N2）；关账强制控制，口径参数单独版本化；属性见 N2，F0 |
| PRC-102 steps/4/checks/4 | L5673 `processes/steps/4` | 1 项前置检查 | 本期受控手工凭证无草稿、待复核、待审批（B-20，AGG-98） | 不适合（N2）；关账强制控制，口径参数单独版本化；属性见 N2，F0 |
| PRC-102 steps/4/checks/5 | L5673 `processes/steps/4` | 1 项前置检查 | 本期人工成本分配与分摊运行已办结或已处理（B-07） | 不适合（N2）；关账强制控制，口径参数单独版本化；属性见 N2，F0 |
| PRC-102 steps/4/checks/6 | L5673 `processes/steps/4` | 1 项前置检查 | 明细账与总账控制科目一致 | 不适合（N2）；关账强制控制，口径参数单独版本化；属性见 N2，F0 |
| PRC-102 steps/4/checks/7 | L5673 `processes/steps/4` | 1 项前置检查 | 现金流量标注完整 | 不适合（N2）；关账强制控制，口径参数单独版本化；属性见 N2，F0 |
| PRC-102 steps/4/checks/8 | L5673 `processes/steps/4` | 1 项前置检查 | 直接法与间接法经营活动现金流量净额相等 | 不适合（N2）；关账强制控制，口径参数单独版本化；属性见 N2，F0 |
| PRC-102 steps/4/checks/9 | L5673 `processes/steps/4` | 1 项前置检查 | 试算平衡 | 不适合（N2）；关账强制控制，口径参数单独版本化；属性见 N2，F0 |
| PRC-102 steps/4/checks/10 | L5673 `processes/steps/4` | 1 项前置检查 | 三条对账等式成立，收付账簿生产成本余额为 0 | 不适合（N2）；关账强制控制，口径参数单独版本化；属性见 N2，F0 |
| PRC-102 steps/4/checks/11 | L5673 `processes/steps/4` | 1 项前置检查 | 银行余额调节已完成（范围待用户定，SQ-52） | 不适合（N2）；关账强制控制，口径参数单独版本化；属性见 N2，F0 |
| PRC-111 transitions/2/guard | L6309 `processes/transitions/2` | 1 条 guard，非完整真值表 | 累计实缴小于认缴额（INV-127） | 不适合（N2）；属性见 N2，F0 |
| PRC-111 transitions/3/guard | L6332 `processes/transitions/3` | 1 条 guard，非完整真值表 | 累计实缴等于认缴额（INV-127） | 不适合（N2）；属性见 N2，F0 |
| PRC-135 transitions/1/guard | L7099 `processes/transitions/1` | 1 条 guard，非完整真值表 | 来源已在授权表或映射表已补齐（INV-98） | 不适合（N2）；属性见 N2，F0 |
| PRC-135 transitions/3/guard | L7126 `processes/transitions/3` | 1 条 guard，非完整真值表 | U8C 拒收，带返回码 | 不适合（N3）；属性见 N3，F0 |
| PRC-135 transitions/4/guard | L7141 `processes/transitions/4` | 1 条 guard，非完整真值表 | 财务处理原因后重投 | 不适合（N3）；属性见 N3，F0 |
| PRC-135 transitions/5/guard | L7156 `processes/transitions/5` | 1 条 guard，非完整真值表 | 凭证依赖的主数据尚未同步到 U8C | 不适合（N3）；属性见 N3，F0 |
| PRC-135 transitions/6/guard | L7171 `processes/transitions/6` | 1 条 guard，非完整真值表 | 每日定时重投成功 | 不适合（N3）；属性见 N3，F0 |
| PRC-135 transitions/7/guard | L7186 `processes/transitions/7` | 1 条 guard，非完整真值表 | 重投仍被拒收 | 不适合（N3）；属性见 N3，F0 |

### B.2 人工责任、职责分离与时间/事件条件

这些字段与 decision 可能重合，不计入判断块/分支合计。实际审批人选择若形成独立政策，应另按 §3.3 评价；以下评价针对人工判断、强制职责分离和编排本身。

| 注记 | 位置 | 原文中的责任/条件 | 结论及属性 |
|---|---|---|---|
| PRC-64 steps/1/timeout | L637 `processes/steps/1` | {'after': 'P1D', 'escalate_to': 'SH-E6', 'interrupting': False, 'pending': ['T-01']} | 不适合（N3）；属性见 N3，F0 |
| PRC-65 /segregation | L712 `processes/(根)` | 开户、解冻、销户经办与财务负责人确认不得为同一人；开户经办不得同时为付款发起人或出纳（INV-81 同类，提议） | 不适合（N2）；属性见 N2，F0 |
| PRC-65 transitions/2/approval | L813 `processes/transitions/2` | engine=aggregate；doc_type=资金账户开立确认；approver=SH-M1；multi_level=False；why_human=开户、解冻与销户改变公司资金通道，须有人对资金安全负责判断必要性、销户前余额与未结付款已清；银行证明与账户唯一性由规则核对；on_reject=end | 不适合（N1）；属性见 N1，F0 |
| PRC-65 transitions/4/approval | L860 `processes/transitions/4` | engine=aggregate；doc_type=资金账户解冻确认；approver=SH-M1；multi_level=False；why_human=开户、解冻与销户改变公司资金通道，须有人对资金安全负责判断必要性、销户前余额与未结付款已清；银行证明与账户唯一性由规则核对；on_reject=end | 不适合（N1）；属性见 N1，F0 |
| PRC-65 transitions/6/approval | L913 `processes/transitions/6` | engine=aggregate；doc_type=资金账户销户确认；approver=SH-M1；multi_level=False；why_human=开户、解冻与销户改变公司资金通道，须有人对资金安全负责判断必要性、销户前余额与未结付款已清；银行证明与账户唯一性由规则核对；on_reject=end | 不适合（N1）；属性见 N1，F0 |
| PRC-70 /segregation | L946 `processes/(根)` | 协议起草人与审批责任人不得为同一人（责任人就是起草人本人时按 AR-11 处理，待 U-21；项目采购起草与责任人角色拆分见 U-40） | 不适合（N2）；属性见 N2，F0 |
| PRC-70 steps/4/approval | L1099 `processes/steps/4` | engine=platform.approval；doc_type=供方协议审批；approver=cost_object_owner；multi_level=False；why_human=协议的价款、付款节点、范围与违约责任是否合理、值不值得签，要对该项目或部门成本负责的人判断；供方准入与资质、再分包与资质、发包方同意证据、预算余额都已由规则拦截或预警，不靠审批；on_reject=3 | 不适合（N1）；属性见 N1，F0 |
| PRC-70 steps/4/timeout | L1099 `processes/steps/4` | {'after': 'P3D', 'escalate_to': '审批人上级', 'interrupting': False, 'rule': 'RULE-377、RULE-379、RULE-380、RULE-382', 'pending': ['T-01']} | 不适合（N3）；属性见 N3，F0 |
| PRC-71 /segregation | L1198 `processes/(根)` | 变更提出人与审批责任人不得为同一人（责任人就是提出人本人时按 AR-11 处理，待 U-21、U-40） | 不适合（N2）；属性见 N2，F0 |
| PRC-71 steps/2/approval | L1262 `processes/steps/2` | engine=platform.approval；doc_type=分包变更审批；approver=cost_object_owner；multi_level=False；why_human=分包价款或范围变更改变成本承诺与付款义务，值不值得变更要对该项目成本负责的人判断；原协议状态、变更后金额不低于履约累计、生效后不可改都已由规则守；on_reject=end | 不适合（N1）；属性见 N1，F0 |
| PRC-71 steps/2/timeout | L1262 `processes/steps/2` | {'after': 'P3D', 'escalate_to': '审批人上级', 'interrupting': False, 'rule': 'RULE-377、RULE-379、RULE-380、RULE-382', 'pending': ['T-01']} | 不适合（N3）；属性见 N3，F0 |
| PRC-71 steps/5/on_event | L1356 `processes/steps/5` | [{'aggregate': 'AGG-56', 'event': '分包变更已生效'}] | 不适合（N3）；属性见 N3，F0 |
| PRC-72 /segregation | L1380 `processes/(根)` | 供方协议经办人与履约验收确认人不得为同一人（INV-83）；两者同属项目经理类角色，按人员判定 | 不适合（N2）；属性见 N2，F0 |
| PRC-72 steps/6/approval | L1540 `processes/steps/6` | engine=platform.approval；doc_type=供方履约冲销审批；approver=cost_object_owner；multi_level=False；why_human=已过账的履约确认是否确属有误、应冲销还是另行更正，要对该项目与协议负责的人判断；冲销只能取反加新事实（INV-95）、重做借贷平衡与期间由规则守，不靠审批；on_reject=end | 不适合（N1）；属性见 N1，F0 |
| PRC-73 /segregation | L1643 `processes/(根)` | 登记进项发票与确认用途不得为同一人（S-29 SQ-53：写在流程分离上，不作不变量） | 不适合（N2）；属性见 N2，F0 |
| PRC-73 steps/2/timeout | L1716 `processes/steps/2` | {'after': 'PT1H', 'escalate_to': 'SH-E6', 'interrupting': False, 'pending': ['T-01']} | 不适合（N3）；属性见 N3，F0 |
| PRC-75 /segregation | L1942 `processes/(根)` | 付款发起人与各级审批人不得为同一人（INV-81）；责任人就是发起人本人时按 AR-11 处理，待用户定；付款发起人与出纳（登记银行结果的人）不得为同一人（INV-81）；同属职能人员角色，按人员判定；各级审批人与出纳不得为同一人（INV-81）；记账由系统经 PRC-132、PRC-92 自动过账，本流程没有人工记账步骤；人工补记账走手工凭证并受其分离约束（PRC-94） | 不适合（N2）；属性见 N2，F0 |
| PRC-75 steps/2/approval | L2132 `processes/steps/2` | engine=platform.approval；doc_type=付款审批；approver=cost_object_owner；multi_level=False；why_human=提前付款、资金头寸安排与抵扣处理要有人对这笔支出负责判断；合同付款条件项是否到期、应付争议状态、应付余额、收款账户与协议相对方一致、协议依据、供方准入、不相容职务都已由规则拦截或预警，不靠审批；on_reject=3 | 不适合（N1）；属性见 N1，F0 |
| PRC-75 steps/2/timeout | L2132 `processes/steps/2` | {'after': 'P3D', 'escalate_to': '审批人上级', 'interrupting': False, 'rule': 'RULE-377、RULE-379、RULE-380、RULE-382', 'pending': ['T-01']} | 不适合（N3）；属性见 N3，F0 |
| PRC-75 steps/5/timeout | L2239 `processes/steps/5` | {'after': 'P3D', 'escalate_to': 'SH-M1', 'interrupting': False, 'pending': ['T-01']} | 不适合（N3）；属性见 N3，F0 |
| PRC-76 steps/2/approval | L2441 `processes/steps/2` | engine=platform.approval；doc_type=供方协议结算审批；approver=cost_object_owner；multi_level=False；why_human=最终结算价款是否与履约、变更和争议相符，要对该项目成本负责的人判断；已完成状态、累计履约与协议金额的关系由规则守；on_reject=end | 不适合（N1）；属性见 N1，F0 |
| PRC-76 steps/2/timeout | L2441 `processes/steps/2` | {'after': 'P30D', 'relative_to': '协议已完成', 'escalate_to': 'SH-E2', 'interrupting': False, 'pending': ['T-01'], 'rule': '已完成未结算提醒经办人'} | 不适合（N3）；属性见 N3，F0 |
| PRC-76 steps/4/approval | L2511 `processes/steps/4` | engine=platform.approval；doc_type=供方协议终止审批；approver=cost_object_owner；multi_level=False；why_human=终止未完成协议涉及违约责任与结算结论，要对该项目成本负责的人判断；协议状态与终止原因必填由规则守；on_reject=end | 不适合（N1）；属性见 N1，F0 |
| PRC-77 /segregation | L2580 `processes/(根)` | 生成申报与复核提交不得为同一人；缴款登记由出纳依付款结果登记（PRC-75 付款发起≠审批≠出纳） | 不适合（N2）；属性见 N2，F0 |
| PRC-77 steps/2/timeout | L2662 `processes/steps/2` | {'after': 'P3D', 'relative_to': '法定申报期截止日（前）', 'escalate_to': 'SH-M1', 'interrupting': False, 'pending': ['T-01']} | 不适合（N3）；属性见 N3，F0 |
| PRC-77 steps/3/approval | L2680 `processes/steps/3` | engine=aggregate；doc_type=纳税申报复核；approver=SH-M1；multi_level=False；why_human=申报内容的税额口径、差异调整与优惠适用是否正确要有人对税务合规负责判断，提交后更正有滞纳金与信用风险；销项发票与分录余额的勾稽由规则核对；on_reject=2 | 不适合（N1）；属性见 N1，F0 |
| PRC-77 steps/4/timeout | L2729 `processes/steps/4` | {'after': 'P5D', 'relative_to': '法定缴款期限（前）', 'escalate_to': 'SH-M1', 'interrupting': False, 'pending': ['T-01']} | 不适合（N3）；属性见 N3，F0 |
| PRC-87 /segregation | L2815 `processes/(根)` | 报销人 ≠ 审批人（责任人就是报销人本人时按 AR-11 处理，待用户定）；报销人 ≠ 核定人；审批人 ≠ 核定人 | 不适合（N2）；属性见 N2，F0 |
| PRC-87 steps/5/approval | L3002 `processes/steps/5` | engine=platform.approval；doc_type=费用报销审批；approver=cost_object_owner；multi_level=False；why_human=费用是否真实、是否为业务所需、金额是否合理，要对这笔费用负责的人判断；格式、发票真伪与重复、预算余额、项目状态、期间都已由规则拦截或预警，不靠审批；on_reject=8 | 不适合（N1）；属性见 N1，F0 |
| PRC-87 steps/5/timeout | L3002 `processes/steps/5` | {'after': 'P3D', 'escalate_to': '审批人上级', 'interrupting': False, 'rule': 'RULE-377、RULE-379、RULE-380、RULE-382', 'pending': ['T-01']} | 不适合（N3）；属性见 N3，F0 |
| PRC-87 steps/6/timeout | L3045 `processes/steps/6` | {'after': 'P5D', 'relative_to': '审批已通过', 'escalate_to': 'SH-M1', 'interrupting': False, 'pending': ['T-01']} | 不适合（N3）；属性见 N3，F0 |
| PRC-87 steps/10/approval | L3155 `processes/steps/10` | engine=platform.approval；doc_type=费用报销冲销审批；approver=cost_object_owner；multi_level=False；why_human=已核定并过账的报销是否确属有误、应冲销还是另行更正，要对这笔费用负责的人判断；冲销只能取反加新事实（INV-95）、重做借贷平衡与期间由规则守，不靠审批；on_reject=end | 不适合（N1）；属性见 N1，F0 |
| PRC-94 /segregation | L3690 `processes/(根)` | 制单人与复核人不得为同一人（INV-108）；审批人不得是制单人或复核人；财务负责人一级用 SH-M1 占位（ADR-0022 决定 3「财务主管审批」，待 F-01） | 不适合（N2）；属性见 N2，F0 |
| PRC-94 steps/3/approval | L3788 `processes/steps/3` | engine=platform.approval；doc_type=手工凭证审批；approver=cost_object_owner；multi_level=False；why_human=手工凭证绕开业务事实直接记账，调整理由是否成立、科目与金额是否恰当、是否属于允许手工处理的类型，要对账负责的人判断；授权表范围、借贷平衡、必填的理由与附件、差异类别、期间、制单与复核分离都已由规则拦截，不靠审批；on_reject=1 | 不适合（N1）；属性见 N1，F0 |
| PRC-94 steps/3/timeout | L3788 `processes/steps/3` | {'after': 'P3D', 'escalate_to': '审批人上级', 'interrupting': False, 'rule': 'RULE-377、RULE-379、RULE-380、RULE-382', 'pending': ['T-01']} | 不适合（N3）；属性见 N3，F0 |
| PRC-95 steps/4/approval | L4029 `processes/steps/4` | engine=platform.approval；doc_type=期末结转运行冲销审批；approver=cost_object_owner；multi_level=False；why_human=已过账的期末结转运行是否确属有误、应冲销还是另行更正，要对这笔成本负责的人判断；冲销只能取反加新事实（INV-95）、重做借贷平衡与期间由规则守，不靠审批；on_reject=end | 不适合（N1）；属性见 N1，F0 |
| PRC-96 /segregation | L4143 `processes/(根)` | 试算人与确认人不得为同一人（提议） | 不适合（N2）；属性见 N2，F0 |
| PRC-96 steps/6/approval | L4337 `processes/steps/6` | engine=platform.approval；doc_type=人工成本分配运行冲销审批；approver=cost_object_owner；multi_level=False；why_human=已过账的人工成本分配运行是否确属有误、应冲销还是另行更正，要对这笔成本负责的人判断；冲销只能取反加新事实（INV-95）、重做借贷平衡与期间由规则守，不靠审批；on_reject=end | 不适合（N1）；属性见 N1，F0 |
| PRC-97 /segregation | L4442 `processes/(根)` | 编制人不得同时为批准人（审批人不得与执行人同为 SH-E6） | 不适合（N2）；属性见 N2，F0 |
| PRC-97 steps/2/approval | L4504 `processes/steps/2` | engine=platform.approval；doc_type=分摊规则审批；approver=cost_object_owner；multi_level=False；why_human=分摊基数、档位、费率与池的划分决定各项目与部门承担多少间接成本，是管理口径的政策判断，要对间接成本池负责的人定；引用的组织单元有效、只在期间首日生效、不追溯、编制与批准分离都已由规则拦截，不靠审批；on_reject=end | 不适合（N1）；属性见 N1，F0 |
| PRC-97 steps/2/timeout | L4504 `processes/steps/2` | {'after': 'P3D', 'escalate_to': '审批人上级', 'interrupting': False, 'rule': 'RULE-377、RULE-379、RULE-380、RULE-382', 'pending': ['T-01']} | 不适合（N3）；属性见 N3，F0 |
| PRC-98 steps/3/approval | L4697 `processes/steps/3` | engine=platform.approval；doc_type=分摊运行冲销审批；approver=cost_object_owner；multi_level=False；why_human=已过账的分摊运行是否确属有误、应冲销还是另行更正，要对这笔成本负责的人判断；冲销只能取反加新事实（INV-95）、重做借贷平衡与期间由规则守，不靠审批；on_reject=end | 不适合（N1）；属性见 N1，F0 |
| PRC-99 /segregation | L4794 `processes/(根)` | 同一资产的建卡经办人与处置经办人不得为同一人（规则，由 AGG-91 命令前置校验；U-30 定后写入不变量） | 不适合（N2）；属性见 N2，F0 |
| PRC-99 steps/4/approval | L4935 `processes/steps/4` | engine=platform.approval；doc_type=资产处置审批；approver=cost_object_owner；multi_level=False；why_human=处置是否合理、处置方式与残值要对使用该资产的部门负责的人判断；资产卡片存在、未重复处置、期间闸门由规则守；on_reject=end | 不适合（N1）；属性见 N1，F0 |
| PRC-99 steps/4/timeout | L4935 `processes/steps/4` | {'after': 'P3D', 'escalate_to': '审批人上级', 'interrupting': False, 'rule': 'RULE-377、RULE-379、RULE-380、RULE-382', 'pending': ['T-01']} | 不适合（N3）；属性见 N3，F0 |
| PRC-100 /segregation | L5053 `processes/(根)` | 起草人与批准人不得为同一人（ADR-0022 决定 2，SESSION_PLAYBOOK §12） | 不适合（N2）；属性见 N2，F0 |
| PRC-100 steps/2/approval | L5105 `processes/steps/2` | engine=aggregate；doc_type=科目表版本发布批准；approver=SH-M1；multi_level=False；why_human=新版本改变记账归类或控制科目范围，影响此后全部分录，要对账务口径负责的人判断；生效日期首日、起草与批准分离、引用完整由规则守；on_reject=1 | 不适合（N1）；属性见 N1，F0 |
| PRC-100 steps/4/approval | L5147 `processes/steps/4` | engine=aggregate；doc_type=映射表版本发布批准；approver=SH-M1；multi_level=False；why_human=新版本改变记账归类或控制科目范围，影响此后全部分录，要对账务口径负责的人判断；生效日期首日、起草与批准分离、引用完整由规则守；on_reject=3 | 不适合（N1）；属性见 N1，F0 |
| PRC-100 steps/6/approval | L5192 `processes/steps/6` | engine=aggregate；doc_type=授权表版本发布批准；approver=SH-M1；multi_level=False；why_human=新版本改变记账归类或控制科目范围，影响此后全部分录，要对账务口径负责的人判断；生效日期首日、起草与批准分离、引用完整由规则守；on_reject=5 | 不适合（N1）；属性见 N1，F0 |
| PRC-100 steps/8/approval | L5238 `processes/steps/8` | engine=aggregate；doc_type=期末结转模板发布；approver=SH-M1；multi_level=False；why_human=期末结转模板决定两账口径与关账出表，发布是财务负责人一级的职责；起草人不得为批准人由规则守（INV-131）；on_reject=end | 不适合（N1）；属性见 N1，F0 |
| PRC-100 steps/10/approval | L5278 `processes/steps/10` | engine=aggregate；doc_type=现金流量项目表发布；approver=SH-M1；multi_level=False；why_human=现金流量项目表决定两账口径与关账出表，发布是财务负责人一级的职责；起草人不得为批准人由规则守（INV-131）；on_reject=end | 不适合（N1）；属性见 N1，F0 |
| PRC-101 /segregation | L5329 `processes/(根)` | 发起关账与确认关账不得为同一人（ADR-0045 决定 1；发起人、确认人写入审计记录，ADR-0046 §8.5）；确认人级别占位 SH-M1，待 F-01 | 不适合（N2）；属性见 N2，F0 |
| PRC-101 steps/3/approval | L5444 `processes/steps/3` | engine=aggregate；doc_type=月结关账确认；approver=SH-M1；multi_level=False；why_human=关账后期间不可重开、之后的更正只能落开放期间，确认前须有人对账务完整性负责并复核检查结果；前置检查（队列、死信、期末结转、借贷平衡）与发起、确认分离已由聚合规则守，剩下的是对账务是否可以定稿的判断；on_reject=4 | 不适合（N1）；属性见 N1，F0 |
| PRC-101 steps/3/timeout | L5444 `processes/steps/3` | {'after': '配置：财务设定的关账提醒日', 'escalate_to': '审批人上级', 'interrupting': False, 'pending': ['P2-01']} | 不适合（N3）；属性见 N3，F0 |
| PRC-102 /segregation | L5542 `processes/(根)` | 发起年末关账与确认年末关账不得为同一人（ADR-0045 决定 1、ADR-0046 第 5 项第 5 条；R-01 补）；确认人级别占位 SH-M1，待 F-01；启用登记人与批准人不得为同一人（AGG-96 带财务批准人，P-003 定后写入） | 不适合（N2）；属性见 N2，F0 |
| PRC-102 steps/2/approval | L5613 `processes/steps/2` | engine=aggregate；doc_type=第 13 期启用批准；approver=SH-M1；multi_level=False；why_human=是否启用第 13 期决定本年度账务结转的形态，要对账务负责的人判断；启用状态锁定与登记只追加由规则守；on_reject=end | 不适合（N1）；属性见 N1，F0 |
| PRC-102 steps/6/approval | L5751 `processes/steps/6` | engine=aggregate；doc_type=年末关账确认；approver=SH-M1；multi_level=False；why_human=年末两期同时定稿后年度不可重开，确认前须有人对年度账务完整性负责；前置检查与发起、确认分离已由聚合规则守，剩下的是年度账务可否定稿的判断；on_reject=7 | 不适合（N1）；属性见 N1，F0 |
| PRC-102 steps/6/timeout | L5751 `processes/steps/6` | {'after': '配置：财务设定的关账提醒日', 'escalate_to': '审批人上级', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-104 /segregation | L5934 `processes/(根)` | 登记对账差异者与签字接受者不得为同一人；签字人级别占位 SH-M1（ADR-0022 决定 8「财务负责人逐项签字接受」，签字人与容差待定） | 不适合（N2）；属性见 N2，F0 |
| PRC-104 steps/4/approval | L6040 `processes/steps/4` | engine=aggregate；doc_type=对账差异签字；approver=SH-M1；multi_level=False；why_human=登记后仍未清零的差异是否可以接受、原因是否查明，要对法定账负责的人判断；差异登记、已推送未记账清单与红字冲销净额核对、登记与签字分离已由规则守；on_reject=2 | 不适合（N1）；属性见 N1，F0 |
| PRC-105 /segregation | L6093 `processes/(根)` | 编制人与评审生效人不得为同一人（ADR-0022 决定 2 同类配置：起草人不得担任批准人）；评审人级别占位 SH-M1，待 F-01 | 不适合（N2）；属性见 N2，F0 |
| PRC-105 steps/2/approval | L6145 `processes/steps/2` | engine=aggregate；doc_type=报表行定义评审生效；approver=SH-M1；multi_level=False；why_human=报表行到科目的映射决定对外报表口径，要对财务报表负责的人评审；映射科目存在、只在期间首日生效、编制与评审分离由规则守；on_reject=1 | 不适合（N1）；属性见 N1，F0 |
| PRC-111 /segregation | L6230 `processes/(根)` | 登记出资认缴、实缴须经财务负责人复核（提议，U-44） | 不适合（N2）；属性见 N2，F0 |
| PRC-112 /segregation | L6459 `processes/(根)` | 草拟利润分配与登记决议证据不得为同一人（决议证据另由财务负责人复核，提议） | 不适合（N2）；属性见 N2，F0 |
| PRC-112 steps/3/approval | L6559 `processes/steps/3` | engine=aggregate；doc_type=利润分配决议；approver=SH-O1；multi_level=False；why_human=向股东或合伙人分配利润是公司治理事项，依法须股东会或合伙人决议，不由业务责任人批；可分配利润上限与决议额不超过上限已由规则守（INV-117），决议结果与证据在线下形成并登记；on_reject=end | 不适合（N1）；属性见 N1，F0 |
| PRC-112 steps/4/timeout | L6594 `processes/steps/4` | {'after': 'P90D', 'relative_to': '决议日', 'escalate_to': 'SH-M1', 'interrupting': False, 'pending': ['T-01']} | 不适合（N3）；属性见 N3，F0 |
| PRC-112 steps/6/approval | L6645 `processes/steps/6` | engine=aggregate；doc_type=利润分配决议撤销；approver=SH-O1；multi_level=False；why_human=撤销已作出的利润分配决议是公司治理事项，依法须股东会或合伙人会议决议，不由业务责任人批；是否已有支付由规则守，撤销结果与证据在线下形成并登记；on_reject=end | 不适合（N1）；属性见 N1，F0 |
| PRC-114 steps/1/approval | L6783 `processes/steps/1` | engine=external；doc_type=减资或退伙决议；approver=SH-O1；why_human=减资或退伙须股东会或合伙人会议决议，系统外形成，本系统登记结果与证据；退回额不超过已实缴额由规则守 | 不适合（N1）；属性见 N1，F0 |
| PRC-158 /timeout | L7204 `processes/(根)` | {'after': 'P3D', 'relative_to': '拒收时刻', 'escalate_to': 'SH-M1', 'interrupting': False, 'pending': ['T-01'], 'desc': '超期未处置告警财务负责人；关账前必须清零'} | 不适合（N3）；属性见 N3，F0 |
| PRC-165 /segregation | L7578 `processes/(根)` | 编制人与批准人不得为同一人（提议；INV-131 适用清单未含本聚合，见 S31-结构-9） | 不适合（N2）；属性见 N2，F0 |
| PRC-165 transitions/2/approval | L7639 `processes/transitions/2` | engine=aggregate；doc_type=成本核算参数版本发布确认；approver=SH-M1；multi_level=False；why_human=参数改变售前投入何时转费用，进而改变各期费用，须有人对口径负责；取值合法性与版本先后由规则校验；on_reject=状态保持草稿，退回编制人 | 不适合（N1）；属性见 N1，F0 |
| PRC-165 transitions/3/on_event | L7666 `processes/transitions/3` | 后继版本已生效 | 不适合（N3）；属性见 N3，F0 |
| PRC-166 /segregation | L7678 `processes/(根)` | 编制人与批准人不得为同一人（提议；INV-131 适用清单未含本聚合，见 S31-结构-9） | 不适合（N2）；属性见 N2，F0 |
| PRC-166 transitions/2/approval | L7739 `processes/transitions/2` | engine=aggregate；doc_type=资产类别与折旧政策版本发布确认；approver=SH-M1；multi_level=False；why_human=折旧政策决定资产成本如何分摊到各期，须有人对口径负责；取值合法性与版本先后由规则校验；on_reject=状态保持草稿，退回编制人 | 不适合（N1）；属性见 N1，F0 |
| PRC-166 transitions/3/on_event | L7769 `processes/transitions/3` | 后继版本已生效 | 不适合（N3）；属性见 N3，F0 |
| PRC-167 /segregation | L7781 `processes/(根)` | 编制人与签字人不得为同一人（提议；ADR-0022 决定 11 只写财务签字） | 不适合（N2）；属性见 N2，F0 |
| PRC-167 transitions/2/approval | L7873 `processes/transitions/2` | engine=aggregate；doc_type=期初余额签字确认；approver=SH-M1；multi_level=False；why_human=期初余额是投产后全部账务的起点，口径差异归因是否成立、项目拆分是否合理须有人对财务数据负责判断；借贷平衡与拆分合计由 INV-134 拦截；on_reject=状态保持编制中，退回编制人 | 不适合（N1）；属性见 N1，F0 |
| PRC-168 /segregation | L7915 `processes/(根)` | 编制人与批准人不得为同一人（提议；INV-131 适用清单未含本聚合，见 S31-结构-9） | 不适合（N2）；属性见 N2，F0 |
| PRC-168 transitions/2/approval | L7976 `processes/transitions/2` | engine=aggregate；doc_type=口径版本发布确认；approver=SH-M1；multi_level=False；why_human=口径决定管理报表与勾稽容差，须有人对口径负责；映射完整性与版本先后由规则校验；on_reject=状态保持草稿，退回编制人 | 不适合（N1）；属性见 N1，F0 |
| PRC-168 transitions/3/on_event | L8005 `processes/transitions/3` | 后继版本已生效 | 不适合（N3）；属性见 N3，F0 |
| PRC-169 /segregation | L8017 `processes/(根)` | 豁免的处置人与财务负责人确认人不得为同一人（INV-135 要求财务负责人确认，不同人为提议） | 不适合（N2）；属性见 N2，F0 |
| PRC-169 transitions/4/approval | L8144 `processes/transitions/4` | engine=aggregate；doc_type=勾稽差异豁免确认；approver=SH-M1；multi_level=False；why_human=豁免等于承认账实不符不再追查，是否可接受由对财务结果负责的人判断；不能由规则代为判定；on_reject=差异案保持未关闭 | 不适合（N1）；属性见 N1，F0 |
| PRC-170 /segregation | L8185 `processes/(根)` | 申请人 ≠ 审批人（责任人就是申请人本人时按 AR-11 处理，待用户定） | 不适合（N2）；属性见 N2，F0 |
| PRC-170 steps/3/approval | L8283 `processes/steps/3` | engine=platform.approval；doc_type=费用事前申请审批；approver=cost_object_owner；multi_level=False；why_human=招待是否必要、额度是否合理，要对这项费用负责的人判断；类别范围、项目状态已由规则拦截，超额只在报销时预警，不靠审批；on_reject=end | 不适合（N1）；属性见 N1，F0 |
| PRC-170 steps/3/timeout | L8283 `processes/steps/3` | {'after': 'P3D', 'escalate_to': '审批人上级', 'interrupting': False, 'pending': ['T-01']} | 不适合（N3）；属性见 N3，F0 |
| PRC-171 /segregation | L8417 `processes/(根)` | 核对账单的财务与在 PRC-75 发起该应付付款的人不得为同一人（INV-81 同类，提议） | 不适合（N2）；属性见 N2，F0 |
| PRC-171 steps/4/timeout | L8534 `processes/steps/4` | {'after': 'P5D', 'relative_to': '月账单已登记', 'escalate_to': 'SH-M1', 'interrupting': False, 'pending': ['T-01']} | 不适合（N3）；属性见 N3，F0 |
| PRC-172 /segregation | L8663 `processes/(根)` | 申请人 ≠ 审批人（责任人就是申请人本人时按 AR-11 处理，待用户定） | 不适合（N2）；属性见 N2，F0 |
| PRC-172 steps/3/approval | L8751 `processes/steps/3` | engine=platform.approval；doc_type=出差申请审批；approver=cost_object_owner；multi_level=False；why_human=出差是否必要、行程与时长是否合理，要对出差费用负责的人判断；项目状态已由规则拦截，超标订单另走 PRC-173；on_reject=end | 不适合（N1）；属性见 N1，F0 |
| PRC-172 steps/3/timeout | L8751 `processes/steps/3` | {'after': 'P3D', 'escalate_to': '审批人上级', 'interrupting': False, 'pending': ['T-01']} | 不适合（N3）；属性见 N3，F0 |
| PRC-172 steps/5/timeout | L8813 `processes/steps/5` | {'after': 'PT1H', 'escalate_to': 'SH-E6', 'interrupting': False, 'pending': ['T-01']} | 不适合（N3）；属性见 N3，F0 |
| PRC-173 /segregation | L8894 `processes/(根)` | 出差人 ≠ 超标订单审批人（责任人就是出差人本人时按 AR-11 处理，待用户定） | 不适合（N2）；属性见 N2，F0 |
| PRC-173 steps/2/approval | L8956 `processes/steps/2` | engine=platform.approval；doc_type=差旅超标订单审批；approver=cost_object_owner；multi_level=False；why_human=超标是否有正当业务原因，要对该次出差费用负责的人判断；差旅标准由规则判定超标，订单与出差申请的对应关系由规则核对；on_reject=3 | 不适合（N1）；属性见 N1，F0 |
| PRC-173 steps/2/timeout | L8956 `processes/steps/2` | {'after': 'PT12H', 'escalate_to': '审批人上级', 'interrupting': False, 'pending': ['T-01']} | 不适合（N3）；属性见 N3，F0 |
| PRC-174 /segregation | L9067 `processes/(根)` | 编制人与批准人不得为同一人（提议；INV-131 适用清单未含本聚合，见 S31-结构-9） | 不适合（N2）；属性见 N2，F0 |
| PRC-174 transitions/2/approval | L9129 `processes/transitions/2` | engine=aggregate；doc_type=出差补贴标准版本发布确认；approver=SH-M1；multi_level=False；why_human=补贴标准是公司对员工的支出口径，须有人对口径负责；档位完整性与版本先后由规则校验；on_reject=状态保持草稿，退回编制人 | 不适合（N1）；属性见 N1，F0 |
| PRC-174 transitions/3/on_event | L9158 `processes/transitions/3` | 后继版本已生效 | 不适合（N3）；属性见 N3，F0 |
| PRC-175 /segregation | L9170 `processes/(根)` | 申请人 ≠ 审批人（责任人就是申请人本人时按 AR-11 处理，待用户定）；申请人不得同时是 PRC-75 的付款发起人、出纳（INV-81 同类，提议） | 不适合（N2）；属性见 N2，F0 |
| PRC-175 steps/3/approval | L9284 `processes/steps/3` | engine=platform.approval；doc_type=直接付款审批；approver=cost_object_owner；multi_level=False；why_human=没有来源单据的付款是否真实、必要、对象与用途是否正确，只有对该支出负责的人能判断；往来方准入、收款账户、合同依据已由规则拦截；on_reject=end | 不适合（N1）；属性见 N1，F0 |
| PRC-175 steps/3/timeout | L9284 `processes/steps/3` | {'after': 'P3D', 'escalate_to': '审批人上级', 'interrupting': False, 'pending': ['T-01']} | 不适合（N3）；属性见 N3，F0 |
| PRC-176 /segregation | L9434 `processes/(根)` | 冲抵经办与该预付款的出纳（登记银行结果的人）不得为同一人（INV-81 同类，提议） | 不适合（N2）；属性见 N2，F0 |

### B.3 无显式 decision 的登记项

| 流程 | 位置 | 当前评估及证据边界 |
|---|---|---|
| PRC-90 出具经营月报并复盘 | L3252 `processes` | 无显式 decision；不适合把维护/投影/状态动作整体迁入 DMN；描述中的独立政策按 §3.3 补表后重评。需读懂职责，引用配置/来源版本并留动作记录；F0 |
| PRC-106 出具三大报表 | L6193 `processes` | 无显式 decision；不适合把维护/投影/状态动作整体迁入 DMN；描述中的独立政策按 §3.3 补表后重评。需读懂职责，引用配置/来源版本并留动作记录；F0 |
| PRC-135 处理会计分录待处理队列与推送重投 | L7076 `processes` | 无显式 decision；不适合把维护/投影/状态动作整体迁入 DMN；描述中的独立政策按 §3.3 补表后重评。需读懂职责，引用配置/来源版本并留动作记录；F0 |

## 附录 C. people 组完整判断清单

来源 E5：提交 `379f248396c2cede61c0f385bc1b573a61146339`，文件 `docs/modernization/architecture/proposals/closeout/people/processes.people.yaml`。47 项流程登记、130 个显式判断块、264 条分支；适合 2、不适合 117、待证据 11。行号为源 YAML 节点起始行，路径索引从 1 开始；每行采用 §9 属性码。

| 判断点 | 固定对象位置 | 分支数 | 条件与去向（完整列举） | 结论 | 原因及属性 |
|---|---|---|---|---|---|
| PRC-80 steps/1/decision | L42 `processes/steps/1` | 10 | 查看法人、组织与职位现状：1. 登记法人主体 → 2；2. 调整组织单元 → 3；3. 指定法人归属 → 4；4. 更名法人主体 → 7；5. 停用法人主体 → 8；6. 启用法人主体 → 9；7. 新建岗位 → 10；8. 修改岗位 → 11；9. 更正法人归属 → 12；10. otherwise → 5 | 不适合（N4） | 运算/生效数据查询或固定口径，保留参数/来源版本；可读/版本/留痕见 N4；F0 |
| PRC-80 steps/2/decision | L61 `processes/steps/2` | 1 | 登记法人主体：1. otherwise → end | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-80 steps/3/decision | L72 `processes/steps/3` | 2 | 调整组织单元：1. 撤销的组织单元下仍有在职任职 → 3〔INV-60〕；2. otherwise → 6 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-80 steps/4/decision | L87 `processes/steps/4` | 2 | 指定法人归属：1. 法人归属期间与同一对象已有期间重叠 → 4〔INV-新-P5〕；2. otherwise → end | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-80 steps/5/decision | L104 `processes/steps/5` | 2 | 停用岗位：1. 职位仍被在职任职引用 → 5〔INV-63〕；2. otherwise → end | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-80 steps/7/decision | L129 `processes/steps/7` | 1 | 更名法人主体：1. otherwise → end | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-80 steps/8/decision | L141 `processes/steps/8` | 1 | 停用法人主体：1. otherwise → end | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-80 steps/9/decision | L153 `processes/steps/9` | 1 | 启用法人主体：1. otherwise → end | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-80 steps/10/decision | L165 `processes/steps/10` | 1 | 新建岗位：1. otherwise → end | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-80 steps/11/decision | L177 `processes/steps/11` | 1 | 修改岗位：1. otherwise → end | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-80 steps/12/decision | L189 `processes/steps/12` | 2 | 更正法人归属：1. 更正后期间与同一对象其他期间重叠 → 12〔INV-122〕；2. otherwise → end | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-81 steps/2/decision | L233 `processes/steps/2` | 2 | 提交招聘需求：1. 提出人在批准前撤回 → 4；2. otherwise → 3 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-81 steps/3/decision | L246 `processes/steps/3` | 2 | 批准招聘需求：1. 批准 → end；2. otherwise → 4 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-82 steps/2/decision | L309 `processes/steps/2` | 2 | 记录面试：1. 面试通过并决定录用 → 3；2. otherwise → 5 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-82 steps/4/decision | L330 `processes/steps/4` | 2 | 〔缺〕登记候选人答复：1. 候选人确认入职日期 → PRC-83；2. otherwise → end | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-83 steps/1/decision | L385 `processes/steps/1` | 2 | 登记入职：1. 入职日不晚于今天 → 2；2. otherwise → 2 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-83 steps/3/decision | L412 `processes/steps/3` | 2 | 建立任职：1. 与该人员已有主任职区间重叠 → 3〔INV-62〕；2. otherwise → 4 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-83 steps/4/decision | L426 `processes/steps/4` | 2 | 提交劳动合同：1. 试用期超过法定上限 → 4〔INV-14〕；2. otherwise → 5 | 待证据（U1） | 仅评估政策选择片段；完整条目/阈值/例外待补，余额、授权、状态等硬约束继续原守护；可读/版本/留痕见 U1；F0 |
| PRC-83 steps/5/decision | L439 `processes/steps/5` | 2 | 批准劳动合同：1. 批准 → 6；2. otherwise → 4 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-83 steps/6/decision | L458 `processes/steps/6` | 2 | 签订劳动合同：1. 用工满一个月仍未签订 → 6；2. otherwise → end | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-84 steps/1/decision | L492 `processes/steps/1` | 2 | 参保增员：1. 用工类型为退休返聘或实习 → 2；2. otherwise → 2 | 适合（D1） | 用工类别到参保适用性；两路用简单 D15 成员表也可满足，不重定法规口径；可读/版本/留痕见 D1；F0 |
| PRC-84 steps/2/decision | L509 `processes/steps/2` | 1 | 建立薪酬档案：1. otherwise → end | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-85 steps/3/decision | L565 `processes/steps/3` | 2 | 办理转正：1. 公司决定不予转正 → PRC-128；2. otherwise → end | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-88 steps/1/decision | L611 `processes/steps/1` | 2 | 编制经营计划版本：1. 编制人放弃编制中的草稿 → 9；2. otherwise → 2 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-88 steps/3/decision | L634 `processes/steps/3` | 2 | 经营计划审批：1. 审批通过 → 5；2. otherwise → 4 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-88 steps/4/decision | L656 `processes/steps/4` | 1 | 〔缺〕驳回经营计划审批：1. otherwise → 1 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-88 steps/6/decision | L676 `processes/steps/6` | 2 | 基线化经营计划：1. 目标类型为预算 → 7；2. otherwise → end | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-88 steps/7/decision | L690 `processes/steps/7` | 2 | 开立预算账户：1. 预算期内出现调整需求 → 8；2. otherwise → end / 预算账户已开立 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-88 steps/8/decision | L706 `processes/steps/8` | 2 | 调整预算：1. 调整超出基线额度总额或要改目标值（规则拒绝） → 1；2. otherwise → end | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-88 steps/9/decision | L722 `processes/steps/9` | 1 | 作废经营计划草稿：1. otherwise → end | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-89 steps/1/decision | L758 `processes/steps/1` | 2 | 编制预测版本：1. 本期为现金流预测期 → 2；2. otherwise → end / 经营计划新版本已编制（预测） | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-107 steps/4/decision | L844 `processes/steps/4` | 2 | 评审知识条目：1. 评审通过 → 5；2. otherwise → 2 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-107 steps/5/decision | L870 `processes/steps/5` | 3 | 发布知识条目：1. 复审认为仍适用但内容或适用范围需更新 → 6；2. 复审认为不再适用 → 7；3. otherwise → end / 已发布 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-115 steps/1/decision | L944 `processes/steps/1` | 3 | 导入考勤记录：1. 拉取失败、批次不完整或存在无法对应人员的记录 → 2；2. 员工发现已导入日期缺卡并发起补卡 → 3；3. otherwise → end | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-115 steps/3/decision | L966 `processes/steps/3` | 2 | 补卡：1. 超出每月补卡次数上限、补卡时限，或该日并不缺卡 → 3；2. otherwise → end | 待证据（U1） | 仅评估政策选择片段；完整条目/阈值/例外待补，余额、授权、状态等硬约束继续原守护；可读/版本/留痕见 U1；F0 |
| PRC-116 steps/1/decision | L1008 `processes/steps/1` | 2 | 占用余额：1. 超出可用额度且假期类型政策不允许透支 → end〔INV-68〕；2. otherwise → 2 | 待证据（U1） | 仅评估政策选择片段；完整条目/阈值/例外待补，余额、授权、状态等硬约束继续原守护；可读/版本/留痕见 U1；F0 |
| PRC-116 steps/2/decision | L1023 `processes/steps/2` | 2 | 提交请假：1. 额度内、假期规则允许、天数不超免审批阈值且无需证明材料 → 4；2. otherwise → 3 | 待证据（U1） | 仅评估政策选择片段；完整条目/阈值/例外待补，余额、授权、状态等硬约束继续原守护；可读/版本/留痕见 U1；F0 |
| PRC-116 steps/3/decision | L1037 `processes/steps/3` | 3 | 请假审批：1. 审批驳回 → 6；2. 员工在审批中撤回 → 7；3. otherwise → 4 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-116 steps/5/decision | L1070 `processes/steps/5` | 3 | 批准转已用：1. 请假开始前员工撤销 → 7；2. 请假期间员工提前结束 → 8；3. otherwise → end | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-116 steps/6/decision | L1081 `processes/steps/6` | 1 | 〔缺〕驳回请假：1. otherwise → end | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-116 steps/7/decision | L1094 `processes/steps/7` | 2 | 撤销请假：1. 请假已开始 → 8；2. otherwise → end | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-117 steps/1/decision | L1151 `processes/steps/1` | 2 | 加班申请：1. 补登超过允许期限，或加班时段与已批准请假重叠 → 1；2. otherwise → 2 | 待证据（U1） | 仅评估政策选择片段；完整条目/阈值/例外待补，余额、授权、状态等硬约束继续原守护；可读/版本/留痕见 U1；F0 |
| PRC-117 steps/2/decision | L1164 `processes/steps/2` | 3 | 批准加班：1. 打卡记录不支持登记的加班时长 → 3；2. 补偿方式为补休 → 4；3. otherwise → end | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-117 steps/3/decision | L1177 `processes/steps/3` | 1 | 〔缺〕驳回加班：1. otherwise → end | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-117 steps/5/decision | L1198 `processes/steps/5` | 2 | 提交请假：1. 补休额度有效期届满仍有未休余额 → PRC-178 / 已移交计发；2. otherwise → end | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-118 steps/1/decision | L1238 `processes/steps/1` | 3 | 生成月度考勤：1. 当月有日期的考勤导入失败或缺批次 → end；2. 人员当月已离职（离职生效后账号已吊销，本人无法确认） → 7；3. otherwise → 2 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-118 steps/2/decision | L1254 `processes/steps/2` | 3 | 员工确认：1. 提出更正 → 5；2. 确认期限届满仍未确认 → 7；3. otherwise → 3 | 不适合（N3） | 异常/到期/事件处理，保留原编排与集成记录；可读/版本/留痕见 N3；F0 |
| PRC-118 steps/6/decision | L1298 `processes/steps/6` | 1 | 〔缺〕重新生成月度考勤：1. otherwise → 2 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-118 steps/7/decision | L1306 `processes/steps/7` | 1 | 〔缺〕默认确认月度考勤：1. otherwise → 3 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-120 steps/1/decision | L1344 `processes/steps/1` | 2 | 编制绩效评估：1. 同一人同期同类型已有已确认评估 → end〔INV-114〕；2. otherwise → 2 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-120 steps/3/decision | L1364 `processes/steps/3` | 2 | 确认绩效评估：1. 已确认评估发现错误 → 4；2. otherwise → end | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-120 steps/4/decision | L1380 `processes/steps/4` | 2 | 登记绩效更正：1. 更正的评估已被奖金核定引用 → PRC-122；2. otherwise → end | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-121 steps/1/decision | L1420 `processes/steps/1` | 2 | 编制激励方案版本：1. 生效日期早于已核定奖金所属期 → 1；2. otherwise → 2 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-121 steps/2/decision | L1430 `processes/steps/2` | 3 | 激励方案审批：1. 审批驳回 → 1；2. 编制人在审批中撤回 → end；3. otherwise → 3 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-122 steps/1/decision | L1494 `processes/steps/1` | 3 | 〔缺〕草拟奖金核定：1. 评估类型不是月度（如试用期评估），无需核定 → end；2. 没有生效的激励方案版本，或草拟金额超出方案额度，已中止 → end；3. otherwise → 2 | 不适合（N4） | 运算/生效数据查询或固定口径，保留参数/来源版本；可读/版本/留痕见 N4；F0 |
| PRC-122 steps/2/decision | L1510 `processes/steps/2` | 2 | 奖金核定审批：1. 审批驳回 → 4；2. otherwise → 3 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-122 steps/4/decision | L1542 `processes/steps/4` | 1 | 〔缺〕驳回奖金核定：1. otherwise → end | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-124 steps/1/decision | L1583 `processes/steps/1` | 3 | 计算：1. 该法人当月仍有在职人员月度考勤未锁定，待锁定 → end；2. 有人员没有生效的薪酬档案版本、应参保人员没有参保基数或没有适用的薪酬规则版本，已中止 → end；3. otherwise → 2 | 不适合（N4） | 运算/生效数据查询或固定口径，保留参数/来源版本；可读/版本/留痕见 N4；F0 |
| PRC-124 steps/2/decision | L1606 `processes/steps/2` | 2 | 提交审核：1. 核对发现需修正输入后重算 → 1；2. otherwise → 3 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-124 steps/3/decision | L1619 `processes/steps/3` | 3 | 薪酬审核：1. 审批驳回，退回重算 → 1；2. 提交人撤回审批 → 1；3. otherwise → 4 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-124 steps/5/decision | L1653 `processes/steps/5` | 2 | 提交过账候选：1. 薪酬所属会计期间已关账，路由到开放期间 → end〔INV-92〕；2. otherwise → end | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-125 steps/1/decision | L1697 `processes/steps/1` | 2 | 生成申报：1. 人员缺证件信息或申报金额与薪酬结算不一致 → end；2. otherwise → 2 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-125 steps/4/decision | L1735 `processes/steps/4` | 2 | 登记缴款：1. 申报被退回或发现差错 → 5；2. otherwise → 6 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-125 steps/5/decision | L1745 `processes/steps/5` | 1 | 更正申报：1. otherwise → 4 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-125 steps/6/decision | L1755 `processes/steps/6` | 2 | 提交过账候选：1. 所属会计期间已关账，路由到开放期间 → end〔INV-92〕；2. otherwise → end | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-125 steps/7/decision | L1768 `processes/steps/7` | 1 | 调整缴费基数：1. otherwise → 8 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-125 steps/8/decision | L1781 `processes/steps/8` | 1 | 提交申报：1. otherwise → end | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-128 steps/1/decision | L1818 `processes/steps/1` | 3 | 申请离职：1. 员工撤回申请 → 4；2. 来源为公司单方终止 → 2；3. otherwise → 5 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-128 steps/2/decision | L1832 `processes/steps/2` | 2 | 〔缺〕批准公司终止：1. 批准 → 5；2. otherwise → 3 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-128 steps/3/decision | L1853 `processes/steps/3` | 1 | 〔缺〕驳回公司终止：1. otherwise → end | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-128 steps/4/decision | L1867 `processes/steps/4` | 1 | 〔缺〕撤回离职申请：1. otherwise → end | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-128 steps/6/decision | L1890 `processes/steps/6` | 2 | 办理离职：1. 离职日前员工撤回或公司撤销 → 10；2. otherwise → 7 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-128 steps/9/decision | L1927 `processes/steps/9` | 1 | 终止劳动合同：1. otherwise → end | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-136 steps/1/decision | L1986 `processes/steps/1` | 3 | 〔缺〕提交调岗申请：1. 新职位已停用或新组织单元已撤销 → 1〔INV-63〕；2. 提交人在批准前撤回 → 3；3. otherwise → 2 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-136 steps/2/decision | L2001 `processes/steps/2` | 3 | 〔缺〕批准调岗申请：1. 批准且跨法人调动 → 4；2. 批准 → 6；3. otherwise → 3 | 不适合（N4） | 运算/生效数据查询或固定口径，保留参数/来源版本；可读/版本/留痕见 N4；F0 |
| PRC-136 steps/3/decision | L2023 `processes/steps/3` | 1 | 〔缺〕驳回调岗申请：1. otherwise → end | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-136 steps/4/decision | L2037 `processes/steps/4` | 2 | 提交劳动合同：1. 试用期超过法定上限或合同要素与已批准调岗不一致 → 4〔INV-14〕；2. otherwise → 5 | 待证据（U1） | 仅评估政策选择片段；完整条目/阈值/例外待补，余额、授权、状态等硬约束继续原守护；可读/版本/留痕见 U1；F0 |
| PRC-136 steps/6/decision | L2063 `processes/steps/6` | 2 | 〔缺〕变更任职：1. 与该人员其他主任职区间冲突 → 1〔INV-62〕；2. otherwise → 7 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-136 steps/7/decision | L2072 `processes/steps/7` | 2 | 〔缺〕任职变更生效：1. 跨法人调动 → 8；2. otherwise → 9 | 不适合（N4） | 运算/生效数据查询或固定口径，保留参数/来源版本；可读/版本/留痕见 N4；F0 |
| PRC-136 steps/10/decision | L2106 `processes/steps/10` | 2 | 标记数据范围待复核并通知区域负责人：1. 调岗引起薪酬变化或跨法人调动 → 11；2. otherwise → end | 不适合（N4） | 运算/生效数据查询或固定口径，保留参数/来源版本；可读/版本/留痕见 N4；F0 |
| PRC-136 steps/11/decision | L2119 `processes/steps/11` | 2 | 调整薪酬档案：1. 跨法人调动 → 12；2. otherwise → end | 不适合（N4） | 运算/生效数据查询或固定口径，保留参数/来源版本；可读/版本/留痕见 N4；F0 |
| PRC-137 steps/2/decision | L2191 `processes/steps/2` | 2 | 〔缺〕开通企业微信成员：1. 企业微信返回失败或 userid 已被占用 → 4；2. otherwise → 3 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-137 steps/3/decision | L2203 `processes/steps/3` | 1 | 〔缺〕绑定外部身份：1. otherwise → end | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-137 steps/4/decision | L2217 `processes/steps/4` | 2 | 〔缺〕告警开通未完成：1. 已修正并重试 → 2；2. otherwise → end | 不适合（N3） | 异常/到期/事件处理，保留原编排与集成记录；可读/版本/留痕见 N3；F0 |
| PRC-138 steps/3/decision | L2277 `processes/steps/3` | 2 | 〔缺〕删除全部角色授予：1. 离职者持有超管或安全审计员角色 → 4〔ADR-0027 第 7 点〕；2. otherwise → 5 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-138 steps/4/decision | L2289 `processes/steps/4` | 1 | 〔缺〕确认放行离职处置：1. otherwise → 5 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-138 steps/6/decision | L2311 `processes/steps/6` | 2 | 〔缺〕停用企业微信成员：1. 企业微信返回失败 → 8；2. otherwise → 7 | 不适合（N3） | 异常/到期/事件处理，保留原编排与集成记录；可读/版本/留痕见 N3；F0 |
| PRC-138 steps/7/decision | L2323 `processes/steps/7` | 1 | 〔缺〕解绑外部身份：1. otherwise → end | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-138 steps/8/decision | L2334 `processes/steps/8` | 2 | 〔缺〕登记企业微信成员手工停用：1. 已处理并重试 → 6；2. otherwise → end | 不适合（N3） | 异常/到期/事件处理，保留原编排与集成记录；可读/版本/留痕见 N3；F0 |
| PRC-139 steps/1/decision | L2396 `processes/steps/1` | 4 | 提交用印申请：1. 用印法人在申请日不是有效法人主体 → 1；2. 来源审批实例未通过或申请份数超过审批载荷份数 → 1；3. 来源审批实例已通过且份数不超审批载荷份数 → 4；4. otherwise → 2 | 待证据（U1） | 仅评估政策选择片段；完整条目/阈值/例外待补，余额、授权、状态等硬约束继续原守护；可读/版本/留痕见 U1；F0 |
| PRC-139 steps/2/decision | L2415 `processes/steps/2` | 2 | 批准用印申请：1. 审批已驳回 → 3；2. otherwise → 4 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-139 steps/3/decision | L2440 `processes/steps/3` | 1 | 驳回用印申请：1. otherwise → end / 已驳回 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-139 steps/4/decision | L2451 `processes/steps/4` | 4 | 登记用印：1. 累计登记份数超过批准份数 → 4；2. 申请未批准 → 4；3. 累计登记份数已达批准份数或经办人确认放弃剩余份数 → 5；4. otherwise → 4 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-142 steps/1/decision | L2527 `processes/steps/1` | 2 | 提交 IT 需求：1. 需求类别要求审批 → 2；2. otherwise → 3 | 待证据（U1） | 仅评估政策选择片段；完整条目/阈值/例外待补，余额、授权、状态等硬约束继续原守护；可读/版本/留痕见 U1；F0 |
| PRC-142 steps/2/decision | L2543 `processes/steps/2` | 2 | 批准 IT 需求：1. 审批已驳回 → 7；2. otherwise → 3 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-142 steps/3/decision | L2567 `processes/steps/3` | 2 | 受理 IT 需求：1. 需求不成立或无法实现 → 6；2. otherwise → 4 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-142 steps/5/decision | L2594 `processes/steps/5` | 3 | 关闭 IT 需求：1. 无处理结论 → 4；2. 提出人认为未解决 → 3；3. otherwise → end / 已关闭 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-142 steps/6/decision | L2610 `processes/steps/6` | 1 | 退回 IT 需求：1. otherwise → end / 已退回 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-143 steps/1/decision | L2680 `processes/steps/1` | 4 | 申请领用资产：1. 资产卡片不存在、已处置或不属 IT 资产类别 → 1；2. 该资产已有未归还的使用人 → 1；3. 领用的资产类别在使用人岗位标准配置内 → 3；4. otherwise → 2 | 待证据（U1） | 仅评估政策选择片段；完整条目/阈值/例外待补，余额、授权、状态等硬约束继续原守护；可读/版本/留痕见 U1；F0 |
| PRC-143 steps/2/decision | L2699 `processes/steps/2` | 2 | 批准领用：1. 审批已驳回 → 4；2. otherwise → 3 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-143 steps/3/decision | L2723 `processes/steps/3` | 2 | 登记领用：1. 该资产已有未归还的使用人 → 1；2. otherwise → end | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-144 steps/1/decision | L2803 `processes/steps/1` | 2 | 提交员工活动申请：1. 活动类型要求审批 → 2；2. otherwise → 3 | 待证据（U1） | 仅评估政策选择片段；完整条目/阈值/例外待补，余额、授权、状态等硬约束继续原守护；可读/版本/留痕见 U1；F0 |
| PRC-144 steps/2/decision | L2819 `processes/steps/2` | 2 | 批准员工活动申请：1. 审批已驳回 → 4；2. otherwise → 3 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-144 steps/3/decision | L2845 `processes/steps/3` | 1 | 完成员工活动：1. otherwise → end | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-155 steps/1/decision | L2915 `processes/steps/1` | 3 | 〔缺〕申请调拨资产：1. 资产没有当前使用人 → PRC-143；2. 调入使用人在调拨日不是在职员工 → 1；3. otherwise → 2 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-155 steps/2/decision | L2934 `processes/steps/2` | 2 | 调拨资产：1. 调出方不是该资产当前使用人 → 1；2. otherwise → end | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-156 steps/1/decision | L3002 `processes/steps/1` | 2 | 归还资产：1. 该资产没有未结束的使用期间 → 1；2. otherwise → end | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-157 steps/1/decision | L3074 `processes/steps/1` | 2 | 报修资产：1. 报修人不是该资产当前使用人 → 1；2. otherwise → 2 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-181 steps/1/decision | L3128 `processes/steps/1` | 2 | 〔缺〕创建经营复盘：1. 复盘期间尚未关账 → end / 复盘待期间关账；2. otherwise → 2 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-181 steps/2/decision | L3143 `processes/steps/2` | 1 | 〔缺〕记录偏差解释：1. otherwise → 3 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-181 steps/3/decision | L3153 `processes/steps/3` | 1 | 〔缺〕确定经营动作：1. otherwise → 4 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-181 steps/4/decision | L3164 `processes/steps/4` | 3 | 〔缺〕定稿经营复盘：1. 复盘未确定任何经营动作 → end / 经营复盘已闭环；2. 经营动作含调整经营计划 → PRC-88 / 已转调整计划；3. otherwise → 5 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-181 steps/5/decision | L3179 `processes/steps/5` | 2 | 〔缺〕完成经营动作：1. 仍有未完成的经营动作 → 5；2. otherwise → end / 经营复盘已闭环 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-113 step/decision | L3216 `event_policies/step` | 2 | 并入扣缴申报：1. 股东是法人或机构 → end / 无需扣缴；2. otherwise → end / 已并入申报 | 待证据（U1） | 所得性质与主体分类待补，不以二分默认代替完整扣缴政策；可读/版本/留痕见 U1；F0 |
| PRC-126 step/decision | L3248 `event_policies/step` | 2 | 提交应付薪酬：1. 该薪酬结算已提交过应付 → end / 应付已提交；2. otherwise → end / 应付已提交 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-127 step/decision | L3281 `event_policies/step` | 2 | 标记薪酬已发放：1. 该薪酬的过账事实已入账 → PRC-134；2. otherwise → end / 已发放 | 不适合（N4） | 运算/生效数据查询或固定口径，保留参数/来源版本；可读/版本/留痕见 N4；F0 |
| PRC-130 steps/1/decision | L3344 `event_policies/steps/1` | 2 | 终止薪酬档案：1. 人员无任何薪酬档案版本 → 2；2. otherwise → 2 | 不适合（N4） | 运算/生效数据查询或固定口径，保留参数/来源版本；可读/版本/留痕见 N4；F0 |
| PRC-130 steps/2/decision | L3356 `event_policies/steps/2` | 2 | 参保减员：1. 用工类型为退休返聘或实习 → end / 无需减员；2. otherwise → end / 社保与公积金参保已减员 | 适合（D1） | 与 PRC-84 共用同一适用政策；实际办理仍人工/记录，不重复维护政策；可读/版本/留痕见 D1；F0 |
| PRC-134 step/decision | L3393 `event_policies/step` | 2 | 标记已过账：1. 薪酬尚未发放 → end / 待发放后再置位；2. otherwise → end / 已过账 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-145 step/decision | L3428 `event_policies/step` | 2 | 比对劳动合同用人单位（AGG-67）、数据范围法人（AGG-117）、个税扣缴义务人（AGG-93），按同一业务日期取当时有效版本；不一致只预警人力，不拒绝；同一人员同一差异已有未处理预警时不重复发：1. 三口径中任意两个不一致 → end / 已预警；2. otherwise → end / 三口径一致 | 不适合（N4） | 运算/生效数据查询或固定口径，保留参数/来源版本；可读/版本/留痕见 N4；F0 |
| PRC-160 steps/1/decision | L3461 `event_policies/steps/1` | 3 | 年度结转额度账户：1. 无上年度账户（年中入职触发） → 2；2. 上年账户仍有占用中的占用行 → end / 结转待处理；3. otherwise → 2 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-161 steps/1/decision | L3501 `event_policies/steps/1` | 2 | 关闭预算账户：1. 账户仍有占用中的占用行 → end / 关闭待处理；2. otherwise → end / 预算账户已关闭 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-177 steps/1/decision | L3540 `event_policies/steps/1` | 2 | 推送员工与组织：1. 差旅服务商返回失败或回执缺失 → end / 同步失败已告警；2. otherwise → end / 已同步 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-178 steps/1/decision | L3579 `event_policies/steps/1` | 2 | 〔缺〕登记补休转加班工资：1. 加班工资基数取不到或补休额度行余额读取失败 → end / 登记待处理；2. otherwise → end / 已计入加班工资 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-178 steps/2/decision | L3593 `event_policies/steps/2` | 3 | 〔缺〕登记补休转加班工资：1. 员工无未休补休余额 → end / 无需计发；2. 加班工资基数取不到或补休额度行余额读取失败 → end / 登记待处理；3. otherwise → end / 离职余额已计入加班工资 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-79 commands/2/decision | L3653 `single_aggregate/commands/2` | 1 | 延续注册执业证书：1. otherwise → end / 证书已延续 | 不适合（N0） | 单向连接，无独立政策；可读/版本/留痕见 N0；F0 |
| PRC-119 steps/2/decision | L3727 `single_aggregate/steps/2` | 2 | 发布标准工时版本：1. 同一法人（空为默认）已有版本的生效区间重叠 → end / 版本被拒〔INV-116〕；2. otherwise → end / 标准工时版本已发布 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-123 commands/1/decision | L3766 `single_aggregate/commands/1` | 3 | 调整薪酬档案：1. 未引用调薪决定依据 → end / 调整被拒；2. 生效日早于已批准的月度薪酬所属月 → end / 薪酬档案已调整；3. otherwise → end / 薪酬档案已调整 | 不适合（N1） | 人工选择/结论或业务事件路径，保留原流程和责任；可读/版本/留痕见 N1；F0 |
| PRC-162 steps/1/decision | L3817 `single_aggregate/steps/1` | 2 | 发布假期规则版本：1. 同一假期类型同一适用范围的生效区间与既有版本重叠 → end / 版本被拒〔INV-123〕；2. otherwise → end / 假期规则版本已发布 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |
| PRC-180 steps/1/decision | L3850 `single_aggregate/steps/1` | 2 | 发布薪酬规则版本：1. 同一规则类别同一适用范围的生效区间与既有版本重叠 → end / 版本被拒〔INV-139〕；2. otherwise → end / 薪酬规则版本已发布 | 不适合（N2） | 状态/授权/证据/余额等控制或其结果分流，保留原守护；可读/版本/留痕见 N2；F0 |

### C.2 人工责任、职责分离与时间/事件条件

这些字段与 decision 可能重合，不计入判断块/分支合计。实际审批人选择若形成独立政策，应另按 §3.3 评价；以下评价针对人工判断、强制职责分离和编排本身。

| 注记 | 位置 | 原文中的责任/条件 | 结论及属性 |
|---|---|---|---|
| PRC-80 steps/6/on_event | L117 `processes/steps/6` | [{'aggregate': 'AGG-61', 'event': '组织单元已调整'}] | 不适合（N3）；属性见 N3，F0 |
| PRC-81 /segregation | L203 `processes/(根)` | 提出人与批准人不得为同一人；提出人本人就是归属组织单元的责任人时按 AR-11 由上级组织负责人批（处理方式待用户 V-18） | 不适合（N2）；属性见 N2，F0 |
| PRC-81 steps/3/approval | L246 `processes/steps/3` | engine=aggregate；doc_type=招聘需求批准；approver=SH-M1；multi_level=False；why_human=增加人员成本是部门负责人对用人取舍的管理判断；Q-08 不管编制，没有可由规则判定的人数上限，职位有效等可校验的条件已由规则拒绝（R-01）；on_reject=4 | 不适合（N1）；属性见 N1，F0 |
| PRC-82 steps/4/timeout | L330 `processes/steps/4` | {'after': 'P7D', 'escalate_to': 'SH-E1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-83 /segregation | L359 `processes/(根)` | 劳动合同的提交人（人力）与批准人（新员工归属组织单元负责人）不得为同一人 | 不适合（N2）；属性见 N2，F0 |
| PRC-83 steps/2/on_time | L400 `processes/steps/2` | 入职日到达（当天开始时） | 不适合（N3）；属性见 N3，F0 |
| PRC-83 steps/5/approval | L439 `processes/steps/5` | engine=aggregate；doc_type=劳动合同批准；approver=SH-M1；multi_level=False；why_human=合同期限、岗位与薪酬约定是否与录用决定一致、用人单位是否正确是法律与管理判断；法定试用期上限已由 INV-14 拒绝，不属人工判断；on_reject=4 | 不适合（N1）；属性见 N1，F0 |
| PRC-84 steps/1/timeout | L492 `processes/steps/1` | {'after': 'P30D', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-85 steps/1/timeout | L543 `processes/steps/1` | {'after': 'P7D', 'escalate_to': 'SH-E6', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-85 steps/3/timeout | L565 `processes/steps/3` | {'after': 'P3D', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-88 /segregation | L585 `processes/(根)` | 编制人与审批人不得为同一人；责任人就是编制人时按 AR-11 由其上级审批 | 不适合（N2）；属性见 N2，F0 |
| PRC-88 steps/3/approval | L634 `processes/steps/3` | engine=platform.approval；doc_type=经营计划审批；approver=cost_object_owner；why_human=目标值与资源取舍是否合理、是否可达，需要责任人凭经营判断，规则只能守「基线不可变」「编制人不审自己」，不能替代判断；on_reject=4 | 不适合（N1）；属性见 N1，F0 |
| PRC-88 steps/8/on_time | L706 `processes/steps/8` | 预算期内出现调整需求 | 不适合（N3）；属性见 N3，F0 |
| PRC-89 steps/1/timeout | L758 `processes/steps/1` | {'after': 'P7D', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-89 steps/1/on_time | L758 `processes/steps/1` | 每周 | 不适合（N3）；属性见 N3，F0 |
| PRC-107 /segregation | L785 `processes/(根)` | 同一知识条目版本的起草人与评审人不得为同一人（SH-E4 兼任起草与评审两种身份，按人区分）；起草人就是专业负责人时按 AR-11 由其上级评审 | 不适合（N2）；属性见 N2，F0 |
| PRC-107 steps/4/approval | L844 `processes/steps/4` | engine=aggregate；doc_type=知识条目评审；approver=SH-E4；why_human=知识内容的技术正确性、适用范围与有效期是否恰当属专业判断，规则只能校验必填项、评审人不是起草人、已发布不可改（INV-115），不能替代；on_reject=2 | 不适合（N1）；属性见 N1，F0 |
| PRC-107 steps/4/timeout | L844 `processes/steps/4` | {'after': 'P10D', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-107 steps/6/on_time | L894 `processes/steps/6` | 有效期届满前提前量到达，或适用范围变化 | 不适合（N3）；属性见 N3，F0 |
| PRC-107 steps/7/on_time | L907 `processes/steps/7` | 有效期届满 | 不适合（N3）；属性见 N3，F0 |
| PRC-116 /segregation | L984 `processes/(根)` | 申请人不得审批本人请假（申请人本身是部门负责人时，按 AR-11 由其上级审批） | 不适合（N2）；属性见 N2，F0 |
| PRC-116 steps/3/approval | L1037 `processes/steps/3` | engine=platform.approval；doc_type=请假审批；approver=cost_object_owner；why_human=超阈值长假、需证明材料的假别与透支需判断业务上能否离岗（项目人力安排）和材料真伪，规则只能校验额度、假期规则与天数，不能代替这一判断；额度内的常规请假已自动通过。；on_reject=6 | 不适合（N1）；属性见 N1，F0 |
| PRC-116 steps/3/timeout | L1037 `processes/steps/3` | {'after': 'P3D', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-116 steps/5/on_event | L1070 `processes/steps/5` | [{'aggregate': 'AGG-70', 'event': '请假已批准'}] | 不适合（N3）；属性见 N3，F0 |
| PRC-117 steps/2/on_time | L1164 `processes/steps/2` | 加班日考勤已导入 | 不适合（N3）；属性见 N3，F0 |
| PRC-117 steps/5/timeout | L1198 `processes/steps/5` | {'after': '有效期届满前（时长待用户定 U15）', 'escalate_to': 'SH-E1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-118 steps/2/timeout | L1254 `processes/steps/2` | {'after': 'P3D', 'escalate_to': 'system', 'interrupting': True} | 不适合（N3）；属性见 N3，F0 |
| PRC-118 steps/3/on_event | L1269 `processes/steps/3` | [{'aggregate': 'AGG-97', 'event': '月度考勤已确认'}] | 不适合（N3）；属性见 N3，F0 |
| PRC-118 steps/4/on_time | L1276 `processes/steps/4` | 员工确认期限届满 | 不适合（N3）；属性见 N3，F0 |
| PRC-121 /segregation | L1397 `processes/(根)` | 方案编制人不得审批本人编制的方案（AR-11 同理） | 不适合（N2）；属性见 N2，F0 |
| PRC-121 steps/2/approval | L1430 `processes/steps/2` | engine=platform.approval；doc_type=激励方案审批；approver=cost_object_owner；why_human=激励方案的规则与额度决定公司对员工的奖金承诺，涉及经营导向与预算取舍，没有客观条件可由规则代替；生效后不可追溯修改，故事先由责任人批准。；on_reject=1 | 不适合（N1）；属性见 N1，F0 |
| PRC-121 steps/3/on_time | L1456 `processes/steps/3` | 方案生效日期到达 | 不适合（N3）；属性见 N3，F0 |
| PRC-122 /segregation | L1469 `processes/(根)` | 用人经理既是评估人又是核定人时，奖金由系统按方案规则草拟、不得由其改额；核定人是被核定人本人时按 AR-11 由其上级核定 | 不适合（N2）；属性见 N2，F0 |
| PRC-122 steps/2/approval | L1510 `processes/steps/2` | engine=platform.approval；doc_type=奖金核定审批；approver=cost_object_owner；why_human=奖金按方案规则草拟只是基线，兑现前须由为该成本负责的人确认没有规则看不到的情况（如离岗、争议、业绩调整）；奖金直接增加人工成本并进入薪酬，故保留正常一级。；on_reject=4 | 不适合（N1）；属性见 N1，F0 |
| PRC-124 /segregation | L1558 `processes/(根)` | 提交审核者不得审批同一份结算（制单与审批分离，AR-11）；任何人不得审批含本人薪酬行的结算，含本人时由其上级审批（SH-E6 内部分岗：薪酬岗 / 财务主管） | 不适合（N2）；属性见 N2，F0 |
| PRC-124 steps/3/approval | L1619 `processes/steps/3` | engine=platform.approval；doc_type=月度薪酬审核；approver=cost_object_owner；why_human=月度薪酬是全员资金支付与个税扣缴的依据，规则只能校验口径一致（考勤已锁定、规则版本、法定代扣），无法判断本月薪酬总额与异常波动是否合理；属高风险，保留人审（正常一级，金额超阈值再加审）。；on_reject=1 | 不适合（N1）；属性见 N1，F0 |
| PRC-124 steps/3/timeout | L1619 `processes/steps/3` | {'after': 'P2D', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-125 /segregation | L1670 `processes/(根)` | 申报登记与缴款资金支出（出纳，B-10）不得为同一人；申报数据由系统生成，人工只做外部申报与登记回执（SH-E6 内部分岗） | 不适合（N2）；属性见 N2，F0 |
| PRC-125 steps/2/timeout | L1714 `processes/steps/2` | {'after': 'P15D', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-128 /segregation | L1793 `processes/(根)` | 公司单方终止：发起人（人力）与批准人（被终止员工所属组织单元责任人）不得为同一人 | 不适合（N2）；属性见 N2，F0 |
| PRC-128 steps/2/approval | L1832 `processes/steps/2` | engine=aggregate；doc_type=公司终止批准；approver=SH-M1；multi_level=False；why_human=单方解除的事由是否成立、证据是否充分是法律与管理判断（劳动合同法第 39、40、41 条），事实真伪规则无法判定；发起人与批准人分离由 segregation 守卫；on_reject=3 | 不适合（N1）；属性见 N1，F0 |
| PRC-128 steps/7/on_time | L1902 `processes/steps/7` | 离职日结束时（时点待用户定）；离职日不晚于登记日则登记时立即执行 | 不适合（N3）；属性见 N3，F0 |
| PRC-128 steps/8/on_time | L1917 `processes/steps/8` | 步 7 之后立即 | 不适合（N3）；属性见 N3，F0 |
| PRC-128 steps/9/on_time | L1927 `processes/steps/9` | 步 7 之后立即 | 不适合（N3）；属性见 N3，F0 |
| PRC-136 /segregation | L1959 `processes/(根)` | 调岗申请的提交人（人力）与批准人（新组织单元责任人）不得为同一人 | 不适合（N2）；属性见 N2，F0 |
| PRC-136 steps/2/approval | L2001 `processes/steps/2` | engine=aggregate；doc_type=调岗批准；approver=SH-M1；multi_level=False；why_human=调岗改变成本归属、汇报线与授权范围，接收部门是否需要、是否合适是管理判断；职位有效（INV-63）、任职不重叠（INV-62）、跨法人合同（INV-14）等可校验条件已由规则拒绝；汇报线变更会影响后续审批链，故不设免批；on_reject=3 | 不适合（N1）；属性见 N1，F0 |
| PRC-136 steps/7/on_time | L2072 `processes/steps/7` | 业务生效日到达（补登 / 当日登记则立即执行） | 不适合（N3）；属性见 N3，F0 |
| PRC-136 steps/9/on_event | L2095 `processes/steps/9` | [{'aggregate': 'AGG-63', 'event': '任职已变更'}] | 不适合（N3）；属性见 N3，F0 |
| PRC-136 steps/10/on_event | L2106 `processes/steps/10` | [{'aggregate': 'AGG-63', 'event': '任职已变更'}] | 不适合（N3）；属性见 N3，F0 |
| PRC-137 steps/4/timeout | L2217 `processes/steps/4` | {'after': 'P1D', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-137 steps/4/on_time | L2217 `processes/steps/4` | 开通失败，或入职日已到仍未绑定 | 不适合（N3）；属性见 N3，F0 |
| PRC-138 steps/8/timeout | L2334 `processes/steps/8` | {'after': 'P1D', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-139 /segregation | L2351 `processes/(根)` | 申请人不得登记自己申请的用印（〔推〕通行的印章管理职责分离）；申请人不得批准自己的用印申请（解析出的审批人就是申请人时按 AR-11 上交其上级） | 不适合（N2）；属性见 N2，F0 |
| PRC-139 steps/2/approval | L2415 `processes/steps/2` | engine=platform.approval；doc_type=用印审批；approver=cost_object_owner；why_human=无来源单据（证明、介绍信、授权委托书等）没有可由规则核对的已批准上游审批，规则无法判断文件内容是否应当盖章，须申请人所在组织单元负责人判断；有已批准来源单据的申请不进入本步（规则核对通过，不设人审）。；on_reject=3 | 不适合（N1）；属性见 N1，F0 |
| PRC-142 /segregation | L2483 `processes/(根)` | 提出人不得批准自己的 IT 需求（解析出的审批人就是提出人时按 AR-11 上交其上级） | 不适合（N2）；属性见 N2，F0 |
| PRC-142 steps/2/approval | L2543 `processes/steps/2` | engine=platform.approval；doc_type=IT需求审批；approver=cost_object_owner；why_human=需审批的类别（如权限提升）是否确有业务必要、范围是否恰当，需要懂业务的部门负责人判断；规则只能依据类别表决定是否进审批，不能判断必要性。；on_reject=7 | 不适合（N1）；属性见 N1，F0 |
| PRC-142 steps/3/timeout | L2567 `processes/steps/3` | {'after': 'P2D', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-142 steps/5/timeout | L2594 `processes/steps/5` | {'after': 'P5D', 'escalate_to': 'system', 'interrupting': True} | 不适合（N3）；属性见 N3，F0 |
| PRC-143 /segregation | L2635 `processes/(根)` | 使用人不得登记自己的领用（领用登记由资产管理员完成）；申请人不得批准自己的超标配置领用（AR-11 上交其上级） | 不适合（N2）；属性见 N2，F0 |
| PRC-143 steps/2/approval | L2699 `processes/steps/2` | engine=platform.approval；doc_type=IT资产领用审批；approver=cost_object_owner；why_human=超出岗位标准配置的设备由部门承担使用成本，是否确有业务需要要由部门负责人判断；规则只能核对是否在标准配置内，不能判断超配是否合理。；on_reject=4 | 不适合（N1）；属性见 N1，F0 |
| PRC-144 /segregation | L2759 `processes/(根)` | 申请人不得批准自己的活动申请（解析出的审批人就是申请人时按 AR-11 上交其上级） | 不适合（N2）；属性见 N2，F0 |
| PRC-144 steps/2/approval | L2819 `processes/steps/2` | engine=platform.approval；doc_type=员工活动审批；approver=cost_object_owner；why_human=举办活动占用部门工时、资源和费用，是否举办、规模多大要在团队计划内取舍，规则无法判断；费用本身在报销时另按预算规则预警。；on_reject=4 | 不适合（N1）；属性见 N1，F0 |
| PRC-155 /segregation | L2871 `processes/(根)` | 调出使用人不得登记自己的调拨（交接登记由资产管理员完成） | 不适合（N2）；属性见 N2，F0 |
| PRC-156 /segregation | L2957 `processes/(根)` | 资产管理员不得验收登记自己名下资产的归还（由另一名管理员或其负责人处理） | 不适合（N2）；属性见 N2，F0 |
| PRC-156 steps/1/timeout | L3002 `processes/steps/1` | {'after': 'P7D', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-181 steps/5/timeout | L3179 `processes/steps/5` | {'after': '各经营动作自带的期限（确定动作时登记，B-18 向 platform.scheduling 登记到期提醒）', 'escalate_to': 'system', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-130 steps/2/timeout | L3356 `event_policies/steps/2` | {'after': 'P15D', 'escalate_to': 'SH-M1', 'interrupting': False} | 不适合（N3）；属性见 N3，F0 |
| PRC-79 commands/3/on_time | L3665 `single_aggregate/commands/3` | 证书有效期届满；届满前 N 日提醒（N 待用户定） | 不适合（N3）；属性见 N3，F0 |

### C.3 无显式 decision 的登记项

| 流程 | 位置 | 当前评估及证据边界 |
|---|---|---|
| PRC-129 离职后吊销会话 | L3296 `event_policies` | 无显式 decision；不适合把维护/投影/状态动作整体迁入 DMN；描述中的独立政策按 §3.3 补表后重评。需读懂职责，引用配置/来源版本并留动作记录；F0 |
| PRC-182 知识条目使用情况 | L3867 `read_models` | 无显式 decision；不适合把维护/投影/状态动作整体迁入 DMN；描述中的独立政策按 §3.3 补表后重评。需读懂职责，引用配置/来源版本并留动作记录；F0 |

## 附录 D. 清单校验记录

使用本机已有 PyYAML 递归读取固定 Git 对象，枚举全部顶层集合中的 decision；另取 guard、checks、approval、segregation、timeout、on_event、on_time。未执行骨架生成器或业务脚本。结论是研究判断；结构数量核对不证明规则已批准或执行覆盖完成。

| 组 | 登记项 | decision 块 | 分支 | guard/checks 单项 | 人工/事件/时间注记 | 适合 / 不适合 / 待证据 |
|---|---|---|---|---|---|---|
| revenue | 72 | 219 | 430 | 0 | 121 | 0 / 211 / 8 |
| finance | 54 | 206 | 557 | 31 | 101 | 1 / 203 / 2 |
| people | 47 | 130 | 264 | 0 | 68 | 2 / 117 / 11 |

同一政策的多处引用保留，不用于收益加总。“不适合”表示不建议迁入业务可编辑模型，形式上能写成布尔表不构成迁移理由。所有条目变更频率均为 F0；补充政策点与无显式判断登记不重复计入 555 块。
