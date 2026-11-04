## 勘误与历史偏差

### 1. 历史偏差（规则生效前）

用户 2026-10-14：「只补必要的，其余承认既成事实 (Recommended)」。以下改动发生在本规则生效前，直接改了已采纳 ADR 的正文。除 ADR-0016 的追加段由 ADR-0024 处理外，其余承认为既成事实，正文不动，只在此登记。本表不再追加。

| ADR | 改动 | 提交 | 原因与用户原话 | 处置 |
|---|---|---|---|---|
| ADR-0009 第 6 条 | 划线删除「只读校验老系统令牌（身份桥接）」，改为签名票据 | 5a9c69c | Phase 1a 检查点 2，用户：Q-1「1a 删除桥接 (Recommended)」 | 承认既成事实 |
| ADR-0009 第 6 条 | 追加「补充」：老系统后端在免密入口修复前不得作为签名票据签发方 | 2517ef3 | 用户：「老系统先不管，确保新系统没有这些缺陷」；「取消桥接 (Recommended)」 | 承认既成事实 |
| ADR-0016 后果第 4 条之后 | 追加「修订提议」段（收入确认单不设双源期） | 1cd7219 | 用户 H-1：「迁入新系统，不设双源期 (Recommended)」 | 由 ADR-0024 部分取代；ADR-0024 采纳时同一提交删除该段并标注 |
| ADR-0018 决定第 2 条 | 追加用语确认第二轮的记录（P / N / A 组结论） | 507b9bd、8d6f9d0 | 用户：用语确认「开一个会话集中确认 (Recommended)」，在确认页上逐组确认 | 承认既成事实（属于记录，不改变决定） |
| ADR-0020 第 7 条、第 8 条 | 第 7 条由「版本化（待确认）」改为已移入新仓库；新增第 8 条总调度会话 | 62da1fc | 用户：「采纳并移入新仓库」；「新开 psa-coordinator 会话负责调度」 | 承认既成事实 |

### 2. 勘误（不改正文）

只登记不改变决定含义的问题（规则见「ADR 生命周期与变更规则」第 6 节）。每条一行，按编号追加；one-coordinator 核对后合并；拿不准是否改变含义的由 one-architecture 判定。

| 编号 | ADR 与位置 | 问题 | 正确读法 | 出处 | 登记日期 |
|---|---|---|---|---|---|
| E-001 | ADR-0011 决定第 2–4 条 | 头部状态已为「全部定稿」，正文仍残留 3 处【提议】标记（c4935a7 初版） | 以头部状态为准，【提议】标记无效 | 用户 2026-10-14「全部定稿」 | 2026-10-14 |
| E-002 | ADR-0033「取代」行 | 状态行已记 U-4 已答（拒绝并告警），「取代」行仍残留「（待用户决定 U-4）」（474de44e） | 以状态行为准 | intent `2026-10-15-020346-one-coordinator-ADR-0033-#119-与.md`（拒绝并告警）；one-coordinator 2026-10-15 | 2026-10-15 |
| E-003 | ADR-0051 决定 3 BC 表中的中文名 | 中文名以 `docs/glossary/bcs.yaml` 为准（用户 2026-10-16 按推荐改名；模块标识不变，不改变决定含义） | 以 `bcs.yaml` 为准 | 用户 2026-10-16 按推荐改名；收件箱 `2026-10-16-one-glossary-bc-names` | 2026-10-16 |
| E-004 | ADR-0030 第 2 节表第 1 行「审计记 `witness=none`」 | 审计 detail 的键名按 U-7b（操作人、见证人一律记员工 ID，不记姓名）与 `AuditDetailPolicy` 禁止模式，落地为 `witnessId=none`；含义（未设见证人并写标注）不变 | 读作 `witnessId=none` | one-architecture 判定落地细化（2026-10-16，one-1a-par 实现 SINGLE-OP P2 时提出）；U-7b 用户答「改写员工 ID，阅读时再解析姓名」（收件箱 `2026-10-16-one-data-u7b-attestation-names`） | 2026-10-16 |
| E-005 | ADR-0030 第 1 节第 7 条、第 2 节表第 6 行「原因文本：标注」 | SINGLE-OP P2（#437）中「模式开启且剩余超管恰为 1」时原因文本下限放宽为 1 已生效，但暂未写标注审计：放宽判定在只读的 `AdministratorSeparationReview`（无审计上下文），补标注须改 organization.workforce，P2 卡 §3 禁止 | 决定不变，分阶段落地：枚举值 `SUPER_ADMIN_MINIMUM_ONE` 保留、P2-20 锁三值；后续小卡在 `ReleaseSyncRunHolds` 放行事务内写标注（与放行正常审计同一事务、subjectKey 同为该同步运行、detail = `SUPER_ADMIN_MINIMUM_ONE`，仅模式开启且剩余超管恰为 1 时），在下一阶段出口前或下一个单人模式放宽项上线前合入，以先到者为准；补齐前按标注查询会漏这一类放宽 | one-architecture 裁定 H1（2026-10-16，one-coordinator 转 one-1a-par 与 opus 提议） | 2026-10-16 |
| E-006 | ADR-0031 背景、决定（「变更单」）、与现有词条的关系、守护第 10 条与证据节对 `terms.yaml` 的行号引用（`:94`、`:1172`、`:2532`、`:3232`、`:4716`、`:5134`、`:5146-5157`、`:5150-5157`） | 词表重排后行号已失效（如 `:5134` 现为 `status` 行），按行号无法定位原指的词条 | 按词条 id 读：`:94` → `business_fact_restated`「事实重述」；`:1172` → `change_order`「变更」；`:2532` → `cost_adjustment`「成本调整」；`:3232` → `period_adjustment`「期间调整」；`:4716` → `contract_modification`「合同变更（会计口径）」；`:5134` → `reversing_entry`「冲销」；`:5146-5157` 与 `:5150-5157` → `reversing_entry` 下禁用的方言「红冲」「红字冲销」「RedReversal」。今后引用词表一律写 id，不写行号 | ADR-0053 独立首轮复审第 9 条（2026-10-17）；one-architecture 判定为失效引用、不改变决定含义 | 2026-10-17 |
| E-007 | ADR-0036 证据节「`period_status` 只有 OPEN / CLOSING / CLOSED（`docs/glossary/terms.yaml:5067`）」 | 词表重排后行号已失效 | 按词条 id 读：`period_status`「期间状态」 | ADR-0053 首轮复审后起草者核查（2026-10-17）；one-architecture 判定为失效引用、不改变决定含义 | 2026-10-17 |
| E-008 | ADR-0055 决定 7 第 1 句「ADR-0037 决定 5 的运行时读取…决定 6、8 的登记与启动检查」 | 所引 ADR-0037 条号有误：决定 5 是账号，运行时读取在决定 1 第 1 条，启动校验在决定 4 | 读作：ADR-0037 决定 1 第 1 条的运行时读取从生产依赖撤销；决定 4 的启动校验与决定 6、8 的登记随同步及老待办调用方清理；决定 5 的账号按该句后半退出。头部「取代」未列决定 1、4 属效力问题，由 ADR-0068「取代」行承接 | owner 2026-10-21 PR #491 评论（A0 第 13 条）；one-architecture 判定为勘误 | 2026-10-21 |
| E-009 | ADR-0058 决定第 4 条（转换记录列）与第 5 条（表形状）中的列名 `aggregate_type`、`sequence_no`、`from_state`、`to_state`、`occurred_on` | 命名守护 DdlNamingTest 在 `platform_file` 首次建表时拦下：代码列须带 `_code`，序号列写全称，日期列带 `_date`，状态列用 `status`；改名不改含义 | 依次读作 `aggregate_type_code`、`transition_sequence_number`、`from_status`、`to_status`、`occurred_on_date`（序号列避开词条「流水号」） | one-data `docs/data/state-transitions-design.md`「列名更正」与「再更正」节（2026-10-21）；one-architecture 判定不改变决定含义 | 2026-10-21 |
| E-010 | ADR-0058 决定 1 字段 `cause`（取值 业务命令 / 冲销 / 更正 / 迁入 / 系统作业）；另：E-009「ADR 与位置」栏写「决定第 4 条」 | 枚举列按命名守护带 `_kind`（DdlNamingTest），模板列名已为 `cause_kind`，取值集合以各 schema 的 CHECK 为准；E-009 所指列名实在决定 1，不在决定 4（ADR-0075 证据节已指出）。改名与改位置都不改含义 | `cause` 读作 `cause_kind`，五个取值的英文代码与中文名以 one-glossary 登记为准（one-data 候选 `BUSINESS_COMMAND`、`REVERSAL`、`CORRECTION`、`MIGRATION`、`SYSTEM_JOB`）；E-009 的位置读作「决定 1（转换记录列）与决定 5（表形状）」 | one-data `docs/data/adr0058-amendment-input.md` 第 1、3 行（claude/one-data-handoff-1 @4dc3396a1）；one-architecture 判定不改变决定含义（2026-10-22） | 2026-10-22 |
| E-011 | ADR-0032 第 4 项影响面「审计」行与 ADR-0044 影响面「审计」行的待调整权限审计动作名（`platform.identity.permission-adjustment.opened`、`kept`/`upheld`、`adjusted`、`closed`、`escalated`、`expired`、`regenerated`） | 事件驱动的审计由 `AuditDomainEvents` 写入，动作名即事件类型名；`must-audit-actions.yaml` 已登记 115 个动作（不含 planned），其中 53 个用事件类型名，ADR 的短横线名从未被任何事件驱动的审计采用；为此加按事件改名的机制没有用户可见的收益 | 以事件类型名登记：`platform.identity.PermissionAdjustmentOpened`、`…Upheld`、`…Adjusted`、`…ClosedBySeparation`、`…Expired`、`…Escalated`；`regenerated` 即 `Opened` 且 `originKind = UPHOLD_EXPIRY`（带原条目 ID），`closed`（原因 `separation`）即 `ClosedBySeparation`；`viewed` 无事件，仍为手写动作 `platform.identity.permission-adjustment.viewed` | one-design 2026-11-03，#730 评论 6602101721（lead 转 one-dev-identity PR #1115 的问题；技术命名，无用户选择） | 2026-11-03 |
| E-012 | ADR-0079 决定 4 表第 6 行「请假批准与撤销、加班批准、月度考勤锁定」的订阅方「payroll、cost.timesheet」 | 照字面读，cost.timesheet 订阅加班批准，与用户决定 FD2-05（工时与加班审批不关联，RULE-412）相冲；ADR-0079 的批准证据未引用 FD2-05 | 与 RULE-412 合读：cost.timesheet 不订阅加班批准；该行其余部分照字面读。timesheet 订阅哪些请假事件、是否订阅月度考勤锁定，不由本勘误定，见 `organization-leave.md` 与对应守护卡 | 用户 2026-10-16 FD2-05（intent `2026-10-16-054259-one-rules-工时填报义务决策页-FD2-6.md`）；`docs/business/rules/overtime-seam-rules.md` RULE-412；#1295 评审笔记第 4 节（若 lead 判为改变含义，改走新 ADR；判定人 lead） | 2026-11-04 |
| E-013 | ADR-0079 附件 `hr-blueprint-gaps.md`、`hr-blueprint-scope.md`、`hr-blueprint-questions.md`、`hr-blueprint-walkthrough.md`、`hr-blueprint-review-r1.md` 至 `r6.md` 的路径 | PR #1219 已把这些文件从 `docs/architecture/proposals/` 删除，ADR 正文与证据节仍按原路径引用 | 到提交 `fb798a0d1^` 读：`git show fb798a0d1^:docs/architecture/proposals/<文件名>`；ADR-0079 的决定不因文件移出而变 | PR #1219（删除）、`git show fb798a0d1^` | 2026-11-04 |

### 3. 性质登记（不改正文）

登记由用户决定改变的某 ADR 或其部分的性质或效力来源（如协作规则改以 `docs/rules/` 为准）。它改变的是效力而非笔误，不属勘误：每条须有用户原话与 intent；由 one-architecture 起草，one-coordinator 核对原话后合并。每条一行，按编号追加。

| 编号 | ADR 与范围 | 性质的改变 | 今后以何为准 | 用户原话与出处 | 登记日期 |
|---|---|---|---|---|---|
| N-001 | ADR-0020 全文（协作规则：会话、协议、调度） | 由 ADR 的决定改为协作规则；按「写什么、不写什么」不属于 ADR | `docs/rules/`（`rules.md`、`roles/`、`playbooks/`）；今后只改这些文件，不为其开 ADR；ADR-0020 正文与状态不动 | 用户原话为「采纳」（研究方案 P1–P7，2026-10-16，intent `2026-10-16-131918-one-coordinator-ADR-管理优化-采纳-集中解决.md`）；「协作规则归 METHODS.md（现 `docs/rules/`）」是调度转述的性质说明，非用户原话；研究报告 `docs/architecture/proposals/adr-practices-research.md` 第 29 行 | 本规则合入日 |
| N-002 | ADR-0060 决定 2 表「审计」行「detail 不写原文件名」对加密拒收的适用 | ADR-0070 决定 2 第 4 条表要求加密拒收审计记文件名（来自用户 E1 原话），但 ADR-0070「取代」栏未列此句，两份已采纳 ADR 字面冲突；用户裁定以 ADR-0070 为准 | 加密拒收的审计 detail 记原文件名，作个人信息字段（ADR-0043：INTERNAL＋PI，保留期随审计）；其余审计仍按 ADR-0060 不写原文件名；ADR-0060 头部「被部分取代」同步列入 | 用户 2026-10-21：加密拒收审计记文件名，「文件名是判断意图、串起改名重试的唯一证据」（intent `2026-10-21-170547`） | 2026-10-21 |
