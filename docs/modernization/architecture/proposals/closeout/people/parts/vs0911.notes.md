# vs0911 NOTES 片段：VS-09（PRC-88、89）、VS-11（PRC-107）、B-18 AGG-79/80、B-30 AGG-107

按 `closeout/CONVENTIONS.md` §4 小节。每条标 [流程] 或 [数据]。读模型 PRC-90（出具经营月报，目录已去「并复盘」；复盘写入由 PRC-181 承接）属 reporting，不在本组目录；它读 AGG-79、AGG-80、AGG-100（processes.v1.yaml 第 1760 行），B-18 与 reporting 之间走读模型，不加 B-18 的出向边。

## 接缝问题

1. [流程][数据] AGG-79 引用列缺人员：销售预测承诺的目标值按人员 × 期间 × 指标（PRC-89 步 1，信息图 M-8），聚合稿 §2.2 AGG-79 引用列只有 AGG-61 组织单元和 platform.approval。B-18 → B-13（api）边已有，只需把 AGG-62 人员补进引用列，不加边。表里 `plan_target_values` 已按此建。
2. [数据] 预算占用方单据的引用：AGG-80 的占用由 B-11（AGG-55 供方协议）、B-12（AGG-59 费用报销）调用。B-18 不能依赖下游，所以占用行里的占用方单据 ID 只能是「不校验」引用；聚合稿 AGG-80 引用列只写了 AGG-79，「不校验」清单（§4.1）也没有这两项。请 one-architecture 补登记并更新「不校验」计数。表里未放进 `references`。
3. [流程] 审批结果回调方向：PRC-88 步 2–5 经 platform.approval 审批，结果回调「通过 / 驳回经营计划审批」调 B-18 的命令，而 §4.1 的边是 B-18 → platform.approval（api），回调方向相反。AGG-12 等已用同样写法（intent 114200 F-2），这是各组共性，本稿不改边，请 one-architecture 在审批回调的统一约定里一并说明。
4. [流程] 项目复盘提炼的触发：v1 PRC-107 步 2 写 `on_event: 项目复盘已发布`，好像 B-30 订阅 B-04 的事件。聚合稿 AGG-103 说订阅方是 B-04 自身，由复盘编制人决定后调 B-30 的创建命令；B-04 → B-30（api）边已有，方向一致。所以本稿触发写 `type: call`（调用方 B-04），不写 event，B-30 不订阅任何事件。
5. [数据] 外部知识平台文件夹引用走 platform.integration 来源对照后，B-30 若要读取来源对照以展示外部链接，需要 B-30 → platform.integration（api）边，聚合稿 §4.1 中 B-30 只依赖 B-13。若不展示外部链接（只作迁入来源），则无需加边，请定。加边的层位影响：platform.integration 在 L0，B-30 在 L3，不改层。
6. [流程] 新原则（QC 修订）下审批回调同接缝问题 3：PRC-88 步 4、5 写 `cross_bc: {mode: event, direction: in, bc: platform.approval, name: 审批已通过 / 审批已驳回}`，而 §4.1 只有 B-18 → platform.approval（api）。沿用 CONVENTIONS §5 的统一写法，不改边；请 one-architecture 在审批回调约定里确认事件方向。
7. [事件] S-42：聚合稿 AGG-77 `BusinessFactAttributed` 订阅方列写了 B-18，本组骨架无承接。预算消耗由 B-11、B-12 经既有 api 调用 AGG-80「消耗预算占用」承接（单据状态变化即消耗，见接缝问题 2），不由过账事实反推（反推会让 B-18 依赖 B-16 的过账时点，且与单据侧调用重复消耗）。建议 one-architecture 改聚合稿删去 AGG-77 订阅方列中的 B-18；若 one-architecture 与 finance 组另有设计须保留，则需在 B-18 骨架补承接与边 B-18 → B-16（事件）。
8. [流程] PRC-181 步 1 由会计期间关账触发：事件 `AccountingPeriodClosed` 由 B-19（AGG-81）发布，B-18 要订阅，而聚合稿 §4.1 的 B-18 依赖只有 B-13、platform.approval，没有 B-18 → B-19。B-19 在 L3、B-18 在 L5，方向向上游，B-19 只依赖 B-13 与 platform.audit，加边不成环、不改层。请 one-architecture 补边 B-18 → B-19（事件）并更新 AGG-79 引用列；若不加边，则触发只能用 `time`（B-18 向 platform.scheduling 登记），步骤 1 的「复盘期间尚未关账」就要靠读期间状态（api，同样要边）。综合复盘的 time 触发已登记 platform.scheduling，该平台边按 CONVENTIONS §5 登记。
9. [流程][数据] PRC-182（知识条目使用情况）需要引用方的事件负载带所引用的知识条目版本 ID：报价版本已生效（AGG-12，B-03）、服务产品版本已发布（AGG-03，B-02）、交付物已签发（AGG-27，B-28）。聚合稿 §2.2 这三个聚合的引用列已有「AGG-107 参考的知识条目版本（api）」，但事件列只写「仅分析」，没写负载含引用的知识条目版本。请 one-architecture 在三个事件上补「负载含所参考的知识条目版本 ID 清单」，reporting 才能不读业务交易表算引用次数；不加订阅边（reporting 订阅全部领域事件，ADR-0023）。引用发生在草拟阶段而事件在生效或签发时，故统计的是「用于已生效报价、已发布产品、已签发交付物」的引用，这是口径取舍，写在 PRC-182 的 desc。

## 结构问题

1. [流程] B-30 评审按聚合内审批（`engine: aggregate`），不经 platform.approval，因为 B-30 没有到 platform.approval 的边。若日后评审要多级或会签而必须走 platform.approval，补边 B-30 → platform.approval 的影响（按聚合稿 §4.1 层位估算，未跑 `check` 脚本，请 one-architecture 验证）：platform.approval 在 L4，且只依赖 B-13、B-14 与平台（聚合稿第 860 行），所以不成环；但 B-30 由 L3 升到 L5，被它引用的 B-02（L5 → L6）、B-03（L6 → L7）、B-04（L7 → L8）、B-28（L8 → L9）都要升一层，逐级带动其下游，层数由 L0–L14 变为约 L0–L15。BC 数、聚合数不变，边数 +1。本稿建议暂不补边（见待用户定 2）。
2. [流程][数据] 「预算调整」两处口径不一致：`terms.yaml` 的预算调整（budget_adjusted）定义为「产生预算基线新版本的受控变更」，聚合稿 AGG-80 的命令「调整预算」是账户级命令（→ BudgetAdjusted）。若按词表，调整预算就是 AGG-79 的新版本，要走经营计划审批；若按聚合稿，账户额度可由财务直接调。本稿在 PRC-88 步 8 判断点写成「调整改变基线额度或目标值 → 回步 1 出新版本，否则账户级调整」，是折中；请 one-architecture 与 one-glossary 定。
3. [流程] 预测的审批：VS-09.2 的 notes 写「新版本经审批成为新基线后可再次进入本段」，但 PRC-89 的 human_orchestration 为 false，AGG-79 的「编制预测版本」命令只产生内部事件，没有基线化与审批。三者不一致。本稿按 PRC-89 现状（不审批，每次滚动即新版本）写，请定（待用户定 4）。
4. [数据] 经营动作与复盘的归属：OBJ-100 定义含「动作」，VS-09.3 退出写「经营动作写入计划」，但聚合稿 AGG-79 内含列只写目标值。`terms.yaml` 却有经营复盘（business_review，aggregate）、经营动作（action_item，entity）、承诺清单（commitment_list，aggregate）三个 operations.planning 条目，聚合稿没有对应聚合。本稿暂在 AGG-79 下建 `action_items` 表承载动作，复盘和承诺清单未建表。（已按 one-architecture 给号 PRC-181 的指示处理，见本节第 11 条：经营复盘先放 AGG-79 作次级，动作改归复盘。）原提问：动作并入 AGG-79 版本；或新增聚合「经营复盘」（含动作）；承诺清单是否对应 PRC-89 的每周销售预测承诺（目标类型「承诺」）。
5. [数据] AGG-107 内含列写了「外部知识平台文件夹引用」。D16 要求外部 ID 只作来源对照，CLAUDE.md 规则 1 要求领域模块不出现外部系统名。建议聚合稿删去该内含项，改为「迁入来源对照（platform.integration）」；本稿表里没有这个列。
6. [数据] 词表与 BC 名：`terms.yaml` 里 budget_baseline（预算基线，aggregate）、budget_reserved、budget_adjusted 的 bc 仍是已合并撤销的 `operations.budgeting`；预算基线在聚合稿里是 AGG-79 的类型版本而不是聚合，预算账户（AGG-80）在词表里没有条目。交 one-glossary 用 new_term_issue.py 登记，本稿不自行裁定。
7. [数据] AGG-80 在拦截方案下才是「额度占用」技术聚合；预警方案下它更像普通次级聚合，聚合稿「对应」列届时需改写（待用户定 1）。
8. [流程] **B-30 无到 platform.approval 的边的问题保留**（新原则 QC 修订）：评审知识条目保留为人工审批（专业判断，见步 4 `why_human`），仍按聚合内单级写；AR-11（责任人就是起草人）、AR-12（责任人缺位）在聚合内只能靠「系统把审批人改指其上级」实现，没有审批平台的加审与代批能力；若用户要求加审必须走 platform.approval，则补边的影响见本节第 1 条。
9. [流程][数据] **候选规则卡（交 one-rules）**——按新原则由规则或不变量守卫、不设人工审批的点：
   - PRC-88 步 8 账户级预算调整：只能在该预算基线已定额度总额内调剂，超出或要改目标值即拒绝并提示走新版本（INV-101 路径）；调整留审计，事后抽查。原稿「账户级调整是否要审批、金额阈值」改为不审批，取消一个潜在审批。
   - PRC-88 步 6：审批通过后自动基线化，不设人工确认（审批已是人的判断）。
   - PRC-88 步 7：目标类型为「预算」才自动分流到开立预算账户，其他类型到基线化结束。
   - PRC-88 步 3：编制人不得审批自己的计划，责任人就是编制人时改指其上级（AR-11，guard approval，保留加审）；责任人缺位（AR-12）同。
   - PRC-107 步 3：类别、适用范围、有效期缺一不得提交评审（必填校验）；案例类含客户信息须脱敏确认（待用户定 6）。
   - PRC-107 步 4：评审人不得是起草人本人（拒绝）；已发布版本不得修改（INV-115，已有）。
   - PRC-89 步 1：逾期未报预测的提醒与升级（预警，不拒绝），期限待用户定 4。
   - AR-03 超出预算：占用超可用额度的处理与待用户定 1 同题，规则卡取「预警」（AR-03 已标 guard rule），待用户定后出卡。
10. [流程] **经营计划草稿作废、预算账户关闭（S-32 已解）**：S-32 裁定 AGG-79 加 `作废经营计划草稿`（编制中 → 已作废，终态，审批中须先撤回）；预算账户关闭给号 PRC-161「年末关闭预算账户」（B-18 定时事件策略，VS-09.3，命令 `关闭预算账户`，由 B-18 向 platform.scheduling 登记）。消耗预算占用、释放预算占用、预算占用仍由财务组流程（PRC-70、PRC-87）调用，不在本组骨架内。`作废经营计划草稿` 已由 S-35 归 PRC-88 放弃分支（步 9），撤 unused_ok。
11. [数据][流程] 经营复盘记录的归属（one-architecture 2026-10-16 给号 PRC-181 时指示）：经营复盘先放 AGG-79 作次级「复盘记录」，经营动作 `action_items` 改归经营复盘；本稿已据此建表（`business_reviews`、`business_review_variance_explanations`）、命令（〔缺〕创建经营复盘、记录偏差解释、确定经营动作、定稿经营复盘、完成经营动作）和两台子状态机（经营复盘：复盘中 → 已定稿 → 已闭环；经营动作：待完成 → 已完成）。提请 one-architecture 定：（a）是否保持在 AGG-79 下：理由是单一入口、事务内与计划版本关联（调整类动作引用新版本）；反面是 AGG-79 已含版本、目标值、动作、复盘，聚合变宽，复盘有自己的生命周期和责任人，与版本的基线化互不依赖；（b）若独立成聚合「经营复盘」（词表 `business_review` 已是 aggregate、`action_item` 是 entity，与独立聚合一致），则 PRC-181 的 aggregate 改指新号，命令、状态机、数据表、权责整体搬过去，其余不变，B-18 内部无新边；（c）词表 `business_review` 的 kind 与本稿暂放次级不一致，交 one-glossary 登记，本稿不自行裁定。聚合稿 AGG-79 的内含列、命令列与对外事件列须回补：内含「经营复盘记录、偏差解释、经营动作」，命令 5 个，事件 经营复盘已定稿、经营复盘已闭环（仅分析）。
12. [流程] 复盘不设审批的判断（按「能规则化就不设人审」）：偏差是否解释齐全、动作是否有责任人和期限、定稿后不可改，都是规则能守的；复盘结论本身是责任人对自己组织单元的记录，不产生资金、对外效力或不可逆后果，也不是需要他人批准的单据；动作完成由责任人自登记，逾期只提醒复盘责任人，不自动完成、不自动取消。因此 PRC-181 无 `approval`。上级对下级复盘的监督靠只读：经营月报与动作完成率（reporting）。若用户认为定稿须上级确认，则按 cost_object_owner 加一级，并写 why_human。
13. [流程] PRC-182 是读模型，归 reporting，服务 VS-11.3（修订废止）：「建议复审修订」「建议废止」只是提示，不改任何状态；修订与废止仍由 PRC-107 的人工命令完成。读模型只有查询口径、权限与页面，无命令。

## 新用语

均为领域用语，bc 为 operations.planning 或 km.knowledge；中英文名需在同 bc 内查重，跨 bc 同名互列。

| 中文 | 建议英文 snake | 范围 | bc | 说明 |
|---|---|---|---|---|
| 〔缺〕提交经营计划审批 | submit_business_plan_for_approval | 领域 | operations.planning | PRC-88 步 2；命令 |
| 〔缺〕通过经营计划审批 | approve_business_plan | 领域 | operations.planning | 审批平台回调命令，写法同「通过报价评审」 |
| 〔缺〕驳回经营计划审批 | reject_business_plan | 领域 | operations.planning | 同上 |
| 关闭预算账户 | close_budget_account | 领域 | operations.planning | AGG-80 命令，收敛新增，S-32 由 PRC-161 年末触发；账户终态「已关闭」（closed） |
| 作废经营计划草稿 | void_business_plan_draft | 领域 | operations.planning | AGG-79 命令，S-32 新增；编制中 → 已作废 |
| 已作废（经营计划版本状态） | voided | 领域 | operations.planning | AGG-79 状态；与其他 bc 的「已作废」同名时按 bc 分立 |
| 预算账户 | budget_account | 领域 | operations.planning | AGG-80；词表无条目 |
| 预算占用行 | budget_reservation | 领域 | operations.planning | 表 `budget_reservations`；与词表事件 budget_reserved（预算占用）关系待 one-glossary 核 |
| 预算调整记录 | budget_account_adjustment | 领域 | operations.planning | 表 `budget_account_adjustments`；与词表 budget_adjusted（预算调整，事件）同源 |
| 经营计划版本 | business_plan_version | 领域 | operations.planning | 词表有经营计划、经营计划基线，无版本；基线是已审批生效的版本 |
| 知识条目 | knowledge_entry | 领域 | km.knowledge | AGG-107 |
| 知识条目版本 | knowledge_entry_version | 领域 | km.knowledge | |
| 知识条目评审记录 | knowledge_entry_review | 领域 | km.knowledge | |
| 适用范围 | applicability_scope | 领域 | km.knowledge | 业务线、项目类型等取值；取值集合待补 |
| 有效期 | validity_period | 领域 | km.knowledge | 词表若已有同名条目需互列 |
| 知识类别 | knowledge_category | 领域 | km.knowledge | 标准做法 / 技术案例 / 规范解读（信息图 K-1 举例，取值待补） |
| 〔缺〕撤回经营计划审批 | withdraw_business_plan_approval | 领域 | operations.planning | PRC-88 步 3 的 `withdraw`：审批中编制人撤回提交；聚合稿 AGG-79 无此命令，平台侧需有取消审批实例的能力（同人员组请假撤销的接缝问题） |
| 创建知识条目 | create_knowledge_entry | 领域 | km.knowledge | AGG-107 命令；技术专家主动创建或经 B-04 提炼入口调用 |
| 起草知识条目 | draft_knowledge_entry | 领域 | km.knowledge | AGG-107 命令 |
| 提交知识条目评审 | submit_knowledge_entry_for_review | 领域 | km.knowledge | AGG-107 命令；类别、适用范围、有效期必填校验 |
| 评审知识条目 | review_knowledge_entry | 领域 | km.knowledge | AGG-107 命令，聚合内单级评审；也是审批单据类型名（知识条目评审） |
| 知识条目评审 | knowledge_entry_review_approval | 领域 | km.knowledge | 审批单据类型名，engine aggregate；与表「知识条目评审记录」不同义，英文名区分 |
| 发布知识条目 | publish_knowledge_entry | 领域 | km.knowledge | AGG-107 命令 |
| 修订知识条目 | revise_knowledge_entry | 领域 | km.knowledge | AGG-107 命令，产生新版本 |
| 废止知识条目 | deprecate_knowledge_entry | 领域 | km.knowledge | AGG-107 命令 |
| 开立预算账户 | open_budget_account | 领域 | operations.planning | AGG-80 命令 |
| 调整预算 | adjust_budget | 领域 | operations.planning | AGG-80 命令；与词表预算调整（产生基线新版本的受控变更）口径差异见结构问题 2 |
| 编制预测版本 | compile_forecast_version | 领域 | operations.planning | AGG-79 命令 |
| 编制现金流预测 | compile_cash_flow_forecast | 领域 | operations.planning | AGG-79 命令 |

`action_items` 表名沿用词表的经营动作（action_item），不是新词。
PRC-181 / PRC-182 新增（均为领域用语，bc operations.planning；PRC-182 读模型的名词归 reporting，未新增词）。同 bc 内中英文名已查重，词表已有的在说明列标明：

| 中文 | 建议英文 snake | 范围 | bc | 说明 |
|---|---|---|---|---|
| 〔缺〕创建经营复盘 | create_business_review | 领域 | operations.planning | AGG-79 命令，PRC-181 步 1；系统在期间关账后自动执行 |
| 〔缺〕记录偏差解释 | record_variance_explanation | 领域 | operations.planning | 命令；偏差用语沿词表目标偏差（target_variance） |
| 偏差解释 | variance_explanation | 领域 | operations.planning | 表 `business_review_variance_explanations`；词表有目标偏差 / 经营偏差 / 勾稽差异三偏差，无「偏差解释」；与勾稽差异案的「解释」不同义（那是数据管道对账的关闭方式，B-17） |
| 〔缺〕确定经营动作 | decide_action_item | 领域 | operations.planning | 命令；经营动作沿用词表 action_item |
| 〔缺〕定稿经营复盘 | finalize_business_review | 领域 | operations.planning | 命令；定稿后偏差解释与动作清单不可改 |
| 〔缺〕完成经营动作 | complete_action_item | 领域 | operations.planning | 命令 |
| 经营复盘已定稿 | BusinessReviewFinalized | 领域 | operations.planning | 事件（仅分析） |
| 经营复盘已闭环 | BusinessReviewClosed | 领域 | operations.planning | 事件（仅分析） |
| 已定稿（经营复盘状态）、已闭环（经营复盘状态）、复盘中、待完成、已完成（经营动作状态） | finalized、closed、in_review、pending、completed | 领域 | operations.planning | 状态；与其他 bc 同名状态按 bc 分立 |
| 调整经营计划（经营动作类型） | adjust_business_plan | 领域 | operations.planning | 动作类型取值，触发 PRC-88 的第二个触发；其余类型取值集合待补 |
| 经营复盘 | business_review | 领域 | operations.planning | 词表已有（kind: aggregate）；本稿暂放 AGG-79 次级，不一致见结构问题 11 |
| 知识条目使用情况 | knowledge_entry_usage | 领域（读模型） | reporting | PRC-182 页面与读模型名；词表无此条；建议复审修订、建议废止两个提示码沿流程骨架 |

## 权责对照条目

engine 为 aggregate 的专业复核，approver 写类型级角色，权责对照表登 `resolve: type`：

| 流程 | 步 | 对象 × 命令 | engine | approver | 权责对照表登记 |
|---|---|---|---|---|---|
| PRC-107 | 4 | 知识条目 × 评审知识条目 | aggregate | SH-E4（技术专家，技术委员会的专业负责人） | A: {role: SH-E4, resolve: type}；加审 AR-11（评审人就是起草人）、AR-12（专业角色缺位）由聚合内改指上级 |

PRC-88 步 3 经营计划审批走 platform.approval，approver 仍为 cost_object_owner（实例级 A），不在此登 type。

## 格式问题

1. [流程] 一个流程有多个入口时，触发与步骤的对应无字段。PRC-89 两个时间触发分别进步 1 与步 2；PRC-107 的触发进步 1 与步 6、7（QC 修订后改由发布步骤的判断进入；并入试点 VS-11 后另有定时复审触发，修订产生的新版本从步 2 重新进入）。本稿用步骤上的 `on_time` 加 `triggers` 的名称表示。建议 `triggers` 项加可选 `enters: <步骤序号>`。
2. [数据] 业务单据的版本（经营计划版本、知识条目版本）沿用 D15 形状，但 `versioned` 只有 `append_only`，不能表示「基线 / 发布前行可变、之后不可改」。本稿写 `append_only` 并在 reason 说明。建议加取值，或借用人员组格式问题 6 提议的 `version_basis`。
3. [流程] 发布后等待定时事件（PRC-107 步 5 发布之后的修订、废止分支，现为步 6、7）没有「等待」写法，本稿把定时分支写进 decision，并在步骤上注 `on_time`。建议 decision 项加 `wait: <事件或期限>`。
4. [流程] `scenarios`（验收场景编号）由谁分配未说明，本稿未填。
5. [数据] `sensitivity` 取值：CONVENTIONS 为小写，`data-classification.txt` 为大写，本稿用小写。人员组 NOTES 已提同一问题。
6. [流程] 同一角色在 roles 里重复（SH-M1 既 initiator 又 approver，SH-E4 同）；人员组格式问题 5 已提，请确认允许。
7. [流程] `withdraw` 字段 bpmn_gen 目前不画：PRC-88 的「已撤回」结局在生成图上没有结束事件，脚本只出 3 个结束事件（已基线化、预算账户已开立、已调整），已撤回写在 yaml 与场景里。请确认生成器是否支持 `withdraw`。
8. [流程] 一个流程有多入口时（PRC-89 的现金流预测、PRC-107 的有效期届满），QC 修订把现金流预测改为接在当期销售预测之后，把有效期届满改为发布后的定时分支，以消除「没有入口顺序流」警告；这是格式提案 `triggers[].enters` 的替代写法，不是业务变更。
9. [流程] `segregation.roles` 对同一角色内的两个人（编制人与审批人同属 SH-M1 或 SH-E4）只能写单个角色加规则文字，请确认写法。
10. [流程] 决策项 `goto: PRC-NN` 在 bpmn_gen 里等同结束；PRC-181 步 4 含调整类动作时转 PRC-88，而其余动作的跟踪仍要继续，没有并行写法。本稿写 `goto: PRC-88` 并在 desc 说明，生成图上该路径结束于「已转调整计划」，跟踪步骤只在无调整类动作的路径上。建议加 `also: PRC-NN` 之类的并行启动写法，或判断项允许 `goto: [5, PRC-88]`。
11. [流程] 读模型段：rebuild.py 原只认 processes、event_policies、single_aggregate 三节，本次加认 `read_models`，PRC-182 写在 `read_models:` 节；字段沿 revenue 组 PRC-22、PRC-62 的写法（`reads`、`events`、`query`、`dimensions`、`permission`、`page`、`scope`、`migration`、`refresh`），另加 `metrics`、`suggestions`（提示码与规则）。请确认读模型字段清单。
12. [流程] 触发项 `triggers[].entry` 已被人员组其他流程使用（PRC-181 沿用），格式问题 1 的提议可视为已采纳。

## 待用户定

1. **预算账户超可用额度时拦截还是预警**（INV-102，〔支研〕问题 11，信息图 OBJ-101 一直未上确认页；影响 B-11 供方协议、B-12 费用报销的命令前置、AGG-80 是否为额度占用技术聚合、B-18 的深浅）：
   - A 拦截：占用超可用额度就拒绝，采购或报销单据提交失败；需孤儿占用释放与对账兜底。
   - B 预警：占用照记，超额放行并预警留痕给财务与管理层；不阻断业务。
   - C 超额须特批：超额占用不直接拒绝，转 platform.approval 特批，批准后放行（B-18 → platform.approval 边已有）。
   - 推荐 B。依据：一次投产，首期没有预算执行的历史习惯可对照；拦截会在投产首期阻断采购与报销，并带来占用补偿与对账的实现成本（CS-1.8 只实现当前阶段）；命令返回「是否超额」标志，之后升级为 A 或 C 不破契约（CS-1.10）。财政部《管理会计应用指引第 200 号》与内控基本规范对预算控制有刚性与柔性之分，信息图所引条文我没有逐条核对，不作为推荐的依据。老系统预算超额时的现行行为未取证，取证不需要用户，可在你定之前补。
2. **知识条目的评审人与层级**（QC 修订：已按新原则改为归属责任人单级评审，见本节第 7 条；以下为原提问）：A 技术委员会（SH-E4）单级，聚合内审批，不补边（推荐，贴合聚合稿 K-1「审批人」一项）；B 管理层（SH-M1）单级；C 多级或会签，须补边 B-30 → platform.approval，影响见结构问题 1。
3. **经营计划审批的层级与审批人**（QC 修订：审批人改为归属组织单元责任人、单级，账户级调整不审批，见本节第 7 条；以下为原提问）：谁审批（管理层 SH-M1 的哪一级，例如执委会）、是否多级、编制人与审批人是否必须分离（不相容职务，可加 `segregation`）；预算账户级调整（预算调整）是否要审批、金额阈值多少。推荐：计划与预算基线单级由执委会批准，编制人不得审批自己的计划；账户级调整由财务提交，超阈值走审批，阈值由你定。
4. **滚动预测**：新版本是否要审批成基线（结构问题 3；推荐不审批，预测是预期不是承诺）；逾期未报的升级对象与期限（本稿暂写 7 天升级给管理层，未经确认）；项目经理（SH-E2）是否参与预测（VS-09.2 参与者含 SH-E2，但骨架无其动作）。
5. **知识条目历史迁入**：库内没有知识条目实体，「全部历史迁入」对条目是空集；A8 文档中心有 567 份文档（近 24 个月新增 39，使用已衰减，内容未读），项目档案有 203 条知识平台文件夹引用。选项：a 不转条目，只把 203 条文件夹引用迁为来源对照、挂项目，条目从零建（推荐，文档没有经过评审，不是知识条目的历史）；b 由技术专家筛选部分文档，以「起草」状态导入后走评审；c 567 份全部以草稿导入（噪音大）。
6. **案例类知识条目的客户信息**：技术案例可能带客户、项目、人员信息。选项：a 发布前评审项含脱敏确认（推荐）；b 按项目类型或业务线限制可见范围；c 不限制。结果决定 `knowledge_entry_versions` 的 sensitivity 与 pi 取值（现为 tbd）。
7. **取消与保留的审批一览**：取消 0 个已有人工审批节点，取消 1 个潜在审批（账户级预算调整不设审批，规则守卫）；保留 2 个（经营计划审批走 platform.approval；知识条目评审走聚合内），审批人一律 `cost_object_owner`——经营计划的归属对象是组织单元，其责任人即部门负责人（公司级为管理层负责人）；知识条目的归属对象是其专业类别对应的组织单元，责任人即技术委员会的专业负责人。原待用户定 2、3 的「评审人是技术委员会还是管理层」「审批人由谁」随之按谁负责谁批改定，层级固定为单级，异常加审只引 AR-11、AR-12；是否仍要多级或会签请用户在确认页复核。
8. **审批与评审超时**：PRC-88 步 3 的待办逾期由 platform.approval 统一提醒与升级，不在本流程重复建模；PRC-107 步 4 聚合内评审：并入试点 VS-11 后暂写 P10D 非中断升级给管理层负责人（SH-M1），10 天为估值。请用户定期限与升级对象（原推荐 14 天提醒归属责任人、21 天升级其上级，只预警不自动通过）；评审中撤回与逾期中断由 one-architecture 回补 AGG-107。
9. **基线化是否需人确认**：PRC-88 步 6 改为审批通过后自动基线化。选项：a 自动（推荐，审批已是判断，少一次交接）；b 保留人工基线化，以便选择生效日。
10. **财务（SH-E6）在经营复盘中是否留系统内步骤**（PRC-181）：VS-09.3 参与者含财务。a 不留：口径说明走经营月报与线下沟通，复盘记录只由责任人写（推荐，首期 CS-1.8 只实现当前阶段）；b 留一步「提供偏差口径说明」，由财务在系统内登记，复盘责任人定稿前须看到。
11. **经营动作的取消与延期**：定稿后动作清单不可改，更正另记新动作；动作到期未完成只提醒。a 首期不做取消与延期，需要时另记新动作并在说明里写旧动作作废（推荐）；b 加「取消经营动作」「延期经营动作」命令，延期须留原因，取消须上级知会。
12. **PRC-182 的统计窗口与提前量**：「长期无人引用」的统计窗口与「有效期将满」的提前量。推荐窗口 12 个月、提前量 30 天；无依据的经验值，请用户定。
13. **偏差容忍度**：PRC-181 步 2 超出容忍度的偏差才必须解释，容忍度按指标设（绝对值或百分比）。定值之前所有偏差都必须解释（失败而不静默）；推荐由用户给出缺省百分比，其余指标可在管理页覆盖。

## 每步在哪里完成

本组三个流程没有线下（offline）或外部系统（external）步骤，没有必填证据项（QC 修订后步骤已重排）。

| 流程 | 步 | 命令 | 谁 | 在哪里 | 页面或备注 |
|---|---|---|---|---|---|
| PRC-88 | 1 | 编制经营计划版本 | SH-M1 管理层 | in_system | 经营计划编辑页 |
| PRC-88 | 2 | 〔缺〕提交经营计划审批 | SH-M1 管理层 | in_system | 经营计划编辑页；企业微信待办通知责任人 |
| PRC-88 | 3 | 经营计划审批 | 归属组织单元责任人（SH-M1） | in_system | platform.approval 审批待办；可撤回 |
| PRC-88 | 4 | 〔缺〕驳回经营计划审批 | platform | automatic | 回调，退回步 1 |
| PRC-88 | 5 | 〔缺〕通过经营计划审批 | platform | automatic | 回调 |
| PRC-88 | 6 | 基线化经营计划 | system | automatic | 审批通过后自动 |
| PRC-88 | 7 | 开立预算账户 | SH-E6 财务 | in_system | 预算账户管理页 |
| PRC-88 | 8 | 调整预算 | SH-E6 财务 | in_system | 预算账户调整页；预算期内出现调整需求时 |
| PRC-89 | 1 | 编制预测版本 | SH-E5 市场和客户经理 | in_system | 销售预测填报页；每周 |
| PRC-89 | 2 | 编制现金流预测 | SH-E6 财务 | in_system | 现金流预测编辑页；预测周期末 |
| PRC-107 | 1 | 创建知识条目 | SH-E4 技术专家（或经 B-04 提炼入口的 SH-E2） | in_system | 知识条目编辑页；提炼入口页面在 B-04 |
| PRC-107 | 2 | 起草知识条目 | SH-E4 技术专家 | in_system | 知识条目编辑页 |
| PRC-107 | 3 | 提交知识条目评审 | SH-E4 技术专家 | in_system | 知识条目详情页 |
| PRC-107 | 4 | 评审知识条目 | 归属专业组织单元责任人（SH-E4） | in_system | 知识条目评审页；聚合内审批 |
| PRC-107 | 5 | 发布知识条目 | SH-E4 技术专家 | in_system | 知识条目详情页 |
| PRC-107 | 6 | 修订知识条目 | SH-E4 技术专家 | in_system | 有效期届满前定时提醒复审或适用范围变化时；人工操作，本实例以已修订结束 |
| PRC-107 | 7 | 废止知识条目 | SH-E4 技术专家 | in_system | 复审认为不再适用时；人工操作 |
| PRC-181 | 1 | 〔缺〕创建经营复盘 | system | automatic | 会计期间已关账（B-19 事件）或综合复盘到期（定时）；通知复盘责任人待办 |
| PRC-181 | 2 | 〔缺〕记录偏差解释 | SH-M1 复盘责任人 | in_system | 经营复盘编辑页；偏差数据取自经营月报（PRC-90） |
| PRC-181 | 3 | 〔缺〕确定经营动作 | SH-M1 复盘责任人 | in_system | 经营复盘编辑页；通知动作责任人待办 |
| PRC-181 | 4 | 〔缺〕定稿经营复盘 | SH-M1 复盘责任人 | in_system | 经营复盘编辑页；含调整类动作转 PRC-88 |
| PRC-181 | 5 | 〔缺〕完成经营动作 | SH-M1 动作责任人 | in_system | 经营动作跟踪页；到期未完成非中断提醒复盘责任人 |
| PRC-182 | — | 读模型（无命令） | SH-E4 查看 | in_system | 知识条目使用情况页；reporting 订阅事件增量更新 |


PRC-181 没有线下或外部系统步骤，无必填证据项。

PRC-107 步 6、7 的「定时」指系统到期提醒（时间触发），修订与废止本身由人操作，所以 `where` 是 in_system，不是 automatic。

## 收敛自检

收敛产物（提议稿）：`vs0911.commands.yaml`、`vs0911.state-machines.yaml`、`vs0911.events.yaml`、`vs0911.raci.yaml`。自检脚本为一次性小检查（不入库），读上述四件与 `vs0911.processes.yaml`，结果如下。

| 检查 | 结果 |
|---|---|
| 四个 yaml 可被 `yaml.safe_load` 读入 | 通过 |
| 流程步骤的命令都在命令目录（步 3 经营计划审批是 platform.approval 任务，除外） | 通过，18 个步骤 |
| 命令 `used_by` 与步骤互相对得上 | 通过；两处按设计放行：〔缺〕撤回经营计划审批对应 PRC-88#3 的 `withdraw`；预算占用对应 PRC-88#7 的 cross_bc 入口声明 |
| 无孤儿命令 | 21 条命令中孤儿 1 条：〔缺〕消耗预算占用（聚合稿事件 BudgetConsumed 无命令，本片段无步骤），交 one-architecture；预算占用、释放预算占用的发起步骤在 PRC-70、PRC-87（他组，未核），未标孤儿 |
| 状态全可达 | 通过：经营计划版本 6 状态、预算占用行 3、知识条目版本 6，预算账户 1，不可达均为 0 |
| 每个非终态能到某终态 | 通过，唯一豁免：预算账户（已开立）聚合稿与数据模型都没有结束或冻结的命令与状态，标 `open_ended`，不自造终态，交 one-architecture |
| 每个改状态的流程步骤对得上一条转换 | 通过：PRC-88 步 1–2、4–8，PRC-89 步 1–2，PRC-107 步 1–7 均有转换（评审退回由步 4 评审知识条目的退回结论承接）；PRC-88#3 平台审批任务由撤回转换与步 4、5 回调转换承接 |
| 事件与命令 emits 一致，步骤 emits 都在事件目录 | 通过，8 条事件 |
| 每条命令恰有一条权责条目 | 通过，22 条条目（经营计划审批是平台任务，单列一条，approver 与骨架 cost_object_owner 一致） |

收敛中发现、需 one-architecture 裁定的点：
1. 〔缺〕命令 4 个（提交 / 撤回 / 通过 / 驳回经营计划审批；原〔缺〕退回知识条目已按试点 VS-11 并入评审知识条目）加 1 个本稿提议的〔缺〕消耗预算占用，名称未经 one-glossary。
2. 状态补充：经营计划版本补「已通过」「已编制」，知识条目版本补「已评审通过」，数据模型状态列未列；撤回后回「编制中」而流程结局记「已撤回」。
3. 预算账户无终态与冻结命令；账户状态机以外，占用行有自己的状态机（占用中 → 已消耗 / 已释放）。
4. 发令权 A 的字段名（经营计划版本.归属组织单元.负责人、知识条目.归属组织单元.负责人、预测人员.直属上级）为本稿按「谁负责谁批」写出，字段是否存在待 one-data 核；预测的 A 与逾期升级对象未经用户确认（待用户定 4）。
5. 合并同义命令：退回知识条目已并入评审知识条目（结论=退回；试点 VS-11 第 1 条，one-architecture 2026-10-17 裁定）。其余候选：编制预测版本与编制现金流预测（同形，A 与 R 不同）、创建与起草知识条目；聚合稿与流程均分列，保留。
6. 知识条目已修订事件在新版本创建时发出而非发布时，请确认。

**非中断超时（S-28）**：PRC-89 步 1 的 P7D 超时为非中断边界计时，升级提醒 SH-M1 后结束于独立结束事件「已提醒」，不汇回主线；流程结局增「已提醒」，PRC-89-S03 覆盖。
PRC-181 / PRC-182 增量（2026-10-16，S-43 给号后）：命令 +5（均为〔缺〕，均有步骤使用，无孤儿）；AGG-79 状态机由 1 台增为 3 台（经营计划版本、经营复盘、经营动作，合并时状态名带子机前缀）；事件 +2（经营复盘已定稿、经营复盘已闭环，仅分析），入向事件 +1（AccountingPeriodClosed，B-19）；权责条目 +5（无审批，A 为经营复盘.复盘组织单元.负责人）；场景 PRC-181 六条、PRC-182 四条；PRC-181 的 outcomes 四个（经营复盘已闭环、已转调整计划、复盘待期间关账、已提醒）均有场景；PRC-181 步 5 的非中断超时 → 已提醒，场景 S06。

