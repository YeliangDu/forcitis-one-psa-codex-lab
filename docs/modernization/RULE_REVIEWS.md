# RULE_REVIEWS — psa

规则评审记录，由 `/code-modernization:modernize-review psa` 写入，数据源为 `RULE_REVIEWS.json`。第一批为审批域（platform.approval，Phase 1a 前）的 28 条被标记 P0 规则，2026-10-14 由会话 psa-review-approval 评审：RULE-248/249/250/252 由用户逐条作答；其余 24 条由用户整批同意评估会话 PSA现代化工程 的建议，其中 RULE-293 由用户亲自决定。结论：正确 19 条（其中多条为「正确+修」——规则保留、缺陷在新系统中修掉，见备注），错误或不需要 9 条，待讨论 0 条。标为「错误」的规则不作为行为契约的基准，新系统按备注中的修正行为实现。

第二批为售前与报价域（Phase 1b 前）的 14 条被标记 P0 规则，2026-10-14 由会话 psa-review-quotation 评审：RULE-139/141/147/155 的业务问题由用户逐条作答（均选推荐项）；其余 10 条由用户整批同意评审建议，并同意随 145、155 修订已定规则 RULE-160 的契约（RULE-160 一并记入本表）。结论：正确 11 条（158 为原样复刻，其余为「正确+修」），错误 3 条（144、145、169），待讨论 0 条；另 RULE-160 修订。

| Rule | Verdict | When | Title | Note |
|---|---|---|---|---|
| RULE-139 | 正确 | 2026-10-14 10:45 | Server rates always overwrite client rates; at submission every work package must have a fully priced role | 正确+修（用户作答）：服务端主数据费率为准；删掉永不触发的查重，改为确定性选方案（见 147）；不做标价折扣（问题1「不需要」），折扣用 RULE-146 行折后总价与尾差表达；工时仅成本编辑权限（BU）可改。 |
| RULE-140 | 正确 | 2026-10-14 10:45 | At submit, cost and margin are recalculated from the current rates before approval starts | 正确+修（整批同意评审建议）：存在未定价工作包时毛利为空并标「成本未完整」，不输出虚高毛利；成本必填节点由 BU 补全。 |
| RULE-141 | 正确 | 2026-10-14 10:45 | Cost-tree work package value = hours × billing rate; labour cost = hours × cost rate | 正确+修（用户作答）：工作包值、人工成本四舍五入到分；汇总由服务端计算；汇总标价仅参考（问题2「仅参考+审批提示」），审批单显示汇总标价、最终报价与偏差率，不约束最终报价。 |
| RULE-142 | 正确 | 2026-10-14 10:45 | Final quote equals the sum of line amounts plus the tail adjustment | 正确+修（整批同意评审建议）：公式保留，合计取整到分；审批单显示尾差金额及其占行合计比例，不设硬上限。 |
| RULE-144 | 错误 | 2026-10-14 10:45 | Gross margin is calculated on the ex-tax final quote using the header tax rate, to 4 decimals | 错误（整批同意评审建议）：不含税额 = Σ行不含税金额 + 尾差 ÷（1+表头税率），毛利按此计算；不用固定 1.06。Phase 2 的 RULE-182/239 预算毛利须用同一口径。 |
| RULE-145 | 错误 | 2026-10-14 10:45 | Approval-time edits recalculate the final quote from the stored line total plus the new tail adjustment | 错误（整批同意评审建议）：审批时重算与保存使用同一计算函数——重新加载行、按税率重拆税额；人工成本为空的老数据从成本树重算。审批中不再重算最终报价（见 155、160）。 |
| RULE-147 | 正确 | 2026-10-14 10:45 | Role rates come from the master-data schemes in effect today, by role and city, newest scheme first | 正确+修（用户作答）：费率在提交时锁定（问题3「提交时锁定」）——草稿按当天生效费率；提交时写入费率快照，审批采纳使用该快照；驳回后重新提交按当天费率刷新。同一生效开始日的方案以唯一约束禁止。 |
| RULE-155 | 正确 | 2026-10-14 10:45 | During approval clauses are locked, so the tail adjustment is effectively frozen, and BU cost must be > 0 when required | 正确+修（用户作答）：审批人不能调整最终报价（问题4「不能，需退回」）——去掉尾差、税率的采纳入口；需改价时退回发起人，由销售重拆条款后再提交。条款锁定保留；成本必填节点 BU 成本须 > 0 保留。 |
| RULE-158 | 正确 | 2026-10-14 10:45 | Conditions for submitting a quotation for approval | 正确（整批同意评审建议）：原样保留；流程实例在启动时绑定（与 RULE-167 一致），过期注释不带入。 |
| RULE-159 | 正确 | 2026-10-14 10:45 | Clause amounts must each be greater than 0 and add up exactly to the final quote | 正确+修（整批同意评审建议）：前后端同一等式校验（少分配同样提示）；「不要求条款」的豁免仅限带老数据标记的迁移在途单。 |
| RULE-160 | 正确 | 2026-10-14 10:45 | Approval-time adoption recalculates final amount and margin; quotation must stay PENDING | 正确+修（整批同意评审建议）：审批中只能采纳成本（成本树、其他成本），采纳后用与保存相同的计算重算 BU 成本与毛利；不再重算最终报价，尾差、税率不可采纳（随 145、155 修订）。 |
| RULE-168 | 正确 | 2026-10-14 10:45 | Only DRAFT or REJECTED quotations can be edited, submitted or deleted | 正确+修（整批同意评审建议）：保存也做比较并更新（状态 + 版本号），与提交、删除、审批采纳一致，杜绝并发覆盖已提交单据。 |
| RULE-169 | 错误 | 2026-10-14 10:45 | How BPM final results map to quotation outcomes | 错误（整批同意评审建议）：与 RULE-279 一致——发起人撤销/取消后单据回到草稿，不记驳回原因；仅「不通过」记为已驳回；结果用枚举。 |
| RULE-172 | 正确 | 2026-10-14 10:45 | Only the applicant can save, submit or delete a quotation | 正确+修（整批同意评审建议）：仅申请人可保存/提交/删除，超管不豁免；删除需独立删除权限，且须为申请人、单据为草稿或已驳回。 |
| RULE-173 | 正确 | 2026-10-14 10:45 | Users without cost-edit permission cannot change cost inputs; work-package cost fields are kept from the database | 正确+修（整批同意评审建议）：销售新建的条款（含模板）只带结构，角色、工时、费率一律清空，由 BU 填写。 |
| RULE-248 | 正确 | 2026-10-14 10:24 | Countersign/or-sign nodes create one task per distinct approver |  |
| RULE-249 | 错误 | 2026-10-14 10:29 | A single-approver node picks one candidate at random | 错误，改成候选组认领 |
| RULE-250 | 正确 | 2026-10-14 10:24 | Initiator-leader scripts pick the 1st or 2nd reporting-line leader | 正确+修，汇报线不完整时往上推一级；一直找不到就转给流程管理员并告警。不能空指针，也不能静默跳过 |
| RULE-252 | 正确 | 2026-10-14 10:24 | Per-row approver resolution | 正确+修。行级类型照旧。非行级类型不再取 HashSet里的「第一个」，和节点级用同一种解析方式（候选组） |
| RULE-253 | 正确 | 2026-10-14 10:29 | Approvers are resolved from the node's assignment-rule type | 正确+修（整批同意评估会话建议）：排除停用/离职人员；部门无负责人时取上级部门负责人；候选集永不含空值。 |
| RULE-254 | 错误 | 2026-10-14 10:29 | "Custom data" approvers come from form fields, with a hard-coded fallback person | 错误（整批同意评估会话建议）：去掉写死的个人兜底，取不到人转流程管理员并告警；删 System.out。 |
| RULE-265 | 错误 | 2026-10-14 10:29 | The initiator of a customized OA process is the caller-supplied `creator`, otherwise the logged-in user | 错误（整批同意评估会话建议）：发起人只能是当前登录用户（ADR-0009、G33）；老系统已由 SEC-005 补丁修复。 |
| RULE-266 | 正确 | 2026-10-14 10:29 | Duplicate-start guard: one live process per business document, reused on resubmission | 正确+修（整批同意评估会话建议）：唯一键改为（流程类型，单据 ID），数据库唯一约束保证。 |
| RULE-267 | 正确 | 2026-10-14 10:29 | A resubmission after a return must belong to the current return round | 正确（整批同意评估会话建议）：所有续跑入口都执行该检查。 |
| RULE-269 | 正确 | 2026-10-14 10:29 | Withdraw to initiator: initiator only, before cutoff, single-instance current node, not already at initiator | 正确+修（整批同意评估会话建议）：「能否撤回」的显示与实际执行用同一判断。 |
| RULE-270 | 错误 | 2026-10-14 10:29 | Batch approve acts on every user's pending tasks in each assignee's name | 错误（整批同意评估会话建议）：只能批量处理本人待办；管理员代办须显式「代理」，审计记录操作人与被代理人。 |
| RULE-276 | 正确 | 2026-10-14 10:29 | Only the assigned approver may act on a task | 正确（整批同意评估会话建议）：与 249 配套，先认领再处理，各入口口径统一。 |
| RULE-278 | 正确 | 2026-10-14 10:29 | Approval start rounds: a monotonic round number per (processKey, businessId) defines the idempotency key | 正确+修（整批同意评估会话建议）：加唯一约束（流程类型，单据 ID，轮次）消除并发重号。 |
| RULE-279 | 正确 | 2026-10-14 10:29 | Process-result handling is idempotent per instance and status (reference: quotation) | 正确+修（整批同意评估会话建议）：幂等保留；发起人撤销后单据回到草稿，不记为驳回。 |
| RULE-280 | 正确 | 2026-10-14 10:29 | Unified approve: ownership gate before any field write, fail-closed field permission, default reasons | 正确+修（整批同意评估会话建议）：字段写入与任务完成同一事务（单体+单库可做到）。 |
| RULE-288 | 错误 | 2026-10-14 10:29 | Initiator cancels the whole process | 错误（整批同意评估会话建议）：撤销不删历史，原审批人仍可见单据与审批记录。 |
| RULE-289 | 正确 | 2026-10-14 10:29 | Process-instance status and result lifecycle | 正确+修（整批同意评估会话建议）：生命周期保留，结果用枚举（已通过/已驳回/已撤销），不靠文案。 |
| RULE-291 | 正确 | 2026-10-14 10:29 | A rejection by an added co-signer is advisory only | 正确+修（整批同意评估会话建议）：补偿任务不得把建议当正式驳回发出。 |
| RULE-292 | 正确 | 2026-10-14 10:29 | Row approval: any rejected row rejects the whole submission, but all rows still finish | 正确+修（整批同意评估会话建议）：加签人在行节点上的驳回同样只算建议（与 291 一致）。 |
| RULE-293 | 正确 | 2026-10-14 10:29 | Return to initiator using the `rejected` flag | 按建议：改为「已退回」——退回发起人后其余未审行统一改为「已退回」，重提时整单重新审批（用户亲自决定）。 |
| RULE-295 | 错误 | 2026-10-14 10:29 | Reliable delivery task lifecycle (psa_reliable_delivery_task) | 错误（整批同意评估会话建议）：新系统用 Modulith 事件发布登记表，重试有默认次数与退避。 |
| RULE-300 | 正确 | 2026-10-14 10:29 | "Skip when empty" bypasses a node that has no approver | 正确+修（整批同意评估会话建议）：仅在节点显式配置「允许跳过」且确实无人时跳过；配置错误或脚本异常一律报错告警，不静默放行。 |
| RULE-302 | 正确 | 2026-10-14 10:29 | Repeat approver on the same node is auto-approved, except on rework or "reapprove on return" | 正确+修（整批同意评估会话建议）：行内容在上次审批后有变化（按内容比对）则必须重审。 |
| RULE-303 | 正确 | 2026-10-14 10:29 | Outbox redelivery, backoff and dead-letter | 正确（整批同意评估会话建议）：新系统由事件发布登记表承担，消费方必须幂等。 |
| RULE-304 | 错误 | 2026-10-14 10:29 | Reconcile job re-sends missed results from the bpm records | 错误（整批同意评估会话建议）：投递由事件发布登记表保证，不再从引擎记录反推（老做法会把自动跳过、加签建议当结果发出）。 |
| RULE-307 | 正确 | 2026-10-14 10:29 | Anti-self-approval escalates to the initiator's direct leader | 正确+修（整批同意评估会话建议）：只有在没有其他非发起人候选时才升级。 |
| RULE-308 | 错误 | 2026-10-14 10:29 | End-of-process write-back of approve_status to the business table | 错误（整批同意评估会话建议）：各业务模块通过类型化结果事件自行处理，去掉按表名拼 SQL 的通用回写。 |
| RULE-311 | 错误 | 2026-10-14 10:29 | The task-result consumer routes by business key, and a hook replaces the handler | 错误（整批同意评估会话建议）：统一为钩子与业务处理都执行、顺序固定，与流程结果处理一致。 |
