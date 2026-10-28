# RULE_REVIEWS — FORCITIS one

规则评审记录，由 `/code-modernization:modernize-review psa` 写入，数据源为 `RULE_REVIEWS.json`。第一批为审批域（platform.approval，Phase 1a 前）的 28 条被标记 P0 规则，2026-10-14 由会话 psa-review-approval 评审：RULE-248/249/250/252 由用户逐条作答；其余 24 条由用户整批同意评估会话 PSA现代化工程 的建议，其中 RULE-293 由用户亲自决定。结论：正确 19 条（其中多条为「正确+修」——规则保留、缺陷在新系统中修掉，见备注），错误或不需要 9 条，待讨论 0 条。标为「错误」的规则不作为行为契约的基准，新系统按备注中的修正行为实现。

第二批为售前与报价域（Phase 1b 前）的 14 条被标记 P0 规则，2026-10-14 由会话 psa-review-quotation 评审：RULE-139/141/147/155 的业务问题由用户逐条作答（均选推荐项）；其余 10 条由用户整批同意评审建议，并同意随 145、155 修订已定规则 RULE-160 的契约（RULE-160 一并记入本表）。结论：正确 11 条（158 为原样复刻，其余为「正确+修」），错误 3 条（144、145、169），待讨论 0 条；另 RULE-160 修订。

第三批为身份与组织域（platform.identity、organization.workforce，Phase 1a 入口条件）的 45 条被标记规则（其中 P0 31 条，规则卡见 `rules/IDENTITY_ORG_RULES.md`），2026-10-14 由会话 psa-review-identity-org 评审：RULE-323（小程序是否保留）、RULE-331/332（会话时长）、RULE-349（「本人及下属」深度）的业务问题由用户逐条作答（均选推荐项）；其余条目由用户整批同意评审建议。结论：正确 16 条（均为「正确+修」），错误 29 条（新系统不复刻老行为，按备注实现），待讨论 0 条。建议依据检查点 1 已批准的 C1a-01..20 与舍弃项 D-01..D-09、ADR-0009/0011/0019、SECURITY_FINDINGS。

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
| RULE-316 | 正确 | 2026-10-14 11:00 | Password login: same error for unknown user and wrong password; disabled users refused | 正确+修（整批同意评审建议）：只保留管理员应急账号（强口令 + MFA + 锁定，C1a-04）；未知账号与错误口令仍报同一错误；员工不再口令登录（D-02）。 |
| RULE-317 | 错误 | 2026-10-14 11:00 | Login form: account and password are 4–16 characters; account is letters and digits only | 错误（整批同意评审建议）：员工无口令；应急账号口令至少 12 位并要求复杂度。 |
| RULE-318 | 错误 | 2026-10-14 11:00 | Passwordless "customization" login issues real tokens for any username or user id | 错误（整批同意评审建议）：免密端点不迁移（D-04），外部调用方改用签名、短时效、一次性票据并服务端校验，拒绝停用用户与超管（C1a-03、SEC-001）。 |
| RULE-319 | 错误 | 2026-10-14 11:00 | A8 login trusts the memberId in the request and redirects with tokens in the URL | 错误（整批同意评审建议）：A8 入口统一走致远服务端验票（RULE-320），不信任请求中的 memberId，令牌不进 URL。 |
| RULE-322 | 正确 | 2026-10-14 11:00 | WeCom H5 OAuth login maps the WeCom userid to `we_com_id` exactly | 正确+修（整批同意评审建议）：按 we_com_id 精确映射保留；映射后必须「启用且在职」；删 local 环境固定用户后门；密钥由配置注入并轮换（SEC-006）。 |
| RULE-323 | 正确 | 2026-10-14 11:00 | WeCom mini-program login (jscode2session) uses the same `we_com_id` mapping | 正确+修（用户作答）：小程序保留（问题「保留」）——与 H5 共用 we_com_id 精确映射与「启用且在职」校验；密钥由配置注入。 |
| RULE-324 | 错误 | 2026-10-14 11:00 | PC WeCom web login guesses the user from the email local part or an "f-stripped" id | 错误（整批同意评审建议）：不按邮箱前缀或去掉 f 猜人，只按 we_com_id 精确映射（与 H5 同一键），未映射拒绝；令牌不进 URL。 |
| RULE-325 | 错误 | 2026-10-14 11:00 | WeCom social auto-binding is written under a different user type than it is read | 错误（整批同意评审建议）：社交绑定表不迁移（D-03），身份只用 we_com_id。 |
| RULE-326 | 错误 | 2026-10-14 11:00 | Only password and Seeyon logins check the disabled status | 错误（整批同意评审建议）：所有登录入口在建会话前过同一道检查：启用且未删除（identity BC 一处实现）。 |
| RULE-327 | 错误 | 2026-10-14 11:00 | Resigned (soft-deleted) users are kept out only by the lookup filter | 错误（整批同意评审建议）：显式检查「已离职」并返回明确业务错误，不依赖 ORM 逻辑删除过滤，不抛空指针或越界。 |
| RULE-328 | 错误 | 2026-10-14 11:00 | SMS login: the code must be valid and the mobile must exist | 错误（整批同意评审建议）：短信登录不迁移（D-01，ADR-0019「短信不迁移」）。 |
| RULE-329 | 错误 | 2026-10-14 11:00 | KM token: anonymous caller, memberId used as a mobile number or a legacy-system id | 错误（整批同意评审建议）：跳转 KM 须已登录，KM 身份取自会话；签名票据、HTTPS；集成密钥移到配置并轮换（SEC-002）。 |
| RULE-331 | 错误 | 2026-10-14 11:00 | Every login creates a refresh token and an access token for client "default" | 错误（用户作答）：不再签发令牌对，改服务端会话（ADR-0009 §2，Spring Session JDBC + HttpOnly/SameSite Cookie）；时长为配置项（见 332）。 |
| RULE-332 | 错误 | 2026-10-14 11:00 | Access-token expiry slides to now + 10 h on every check | 错误（用户作答）：会话时长「空闲 10h + 上限 24h」——10 小时无活动过期，登录 24 小时后无论是否活跃都须重新登录；均为配置项；令牌不打印到日志。 |
| RULE-333 | 错误 | 2026-10-14 11:00 | Refresh revokes sibling access tokens but never rotates the refresh token | 错误（整批同意评审建议）：新系统无刷新令牌；登录时轮换会话 ID；新系统不读、不写、不校验老令牌表（C1a-06 已删除，用户『取消桥接』）。 |
| RULE-335 | 错误 | 2026-10-14 11:00 | Four SSO paths put access and refresh tokens in the redirect URL | 错误（整批同意评审建议）：令牌不进 URL；SSO 回跳用一次性 code，在服务端换取 HttpOnly 会话 Cookie。 |
| RULE-336 | 错误 | 2026-10-14 11:00 | Services trust the `login-user` header, treat a bad token as anonymous, and ignore user type | 错误（整批同意评审建议）：身份只来自服务端会话（CurrentUser），不信任任何外部身份请求头；无效会话返回 401 而非匿名；按接口族校验用户类型；mock 登录只限本地。 |
| RULE-337 | 错误 | 2026-10-14 11:00 | The gateway does no authentication | 错误（整批同意评审建议）：默认拒绝集中在 platform.identity 一处（G28/G33）；匿名遍历全部接口返回 401（C1a-07）。 |
| RULE-338 | 错误 | 2026-10-14 11:00 | Disabling, deleting or a resignation via sync does not revoke live tokens | 错误（整批同意评审建议）：停用、删除、离职（含同步）、角色变更时即时吊销该用户全部会话；解析会话时校验用户状态。 |
| RULE-344 | 错误 | 2026-10-14 11:00 | Every user without a role silently gets role 2 whenever the user cache reloads | 错误（整批同意评审建议）：不再在缓存刷新时隐式补授角色 2（C1a-08）；A8 新建在职员工时显式授予一次基础角色并审计；管理员移除后不补回；离职或已删除员工不授。 |
| RULE-345 | 正确 | 2026-10-14 11:00 | Role maintenance: reserved code, unique name and code, system roles frozen, default scope ALL | 正确+修（整批同意评审建议）：保留 super_admin 预留码、名称与编码唯一、系统角色冻结、删角色连带删关联；新角色默认数据范围 SELF（最小权限），不再默认 ALL。 |
| RULE-349 | 正确 | 2026-10-14 11:00 | Business data scope = union over roles; ALL anywhere means unrestricted | 正确+修（用户作答）：各角色并集保留；只计启用角色；自定义部门只在 DEPT_CUSTOM 下生效；排除已离职用户；「本人及下属」只算直接下属（问题「只算直接下属」，与老系统一致）；以显式 AccessScope（法人 × 部门 × 项目角色）表达。 |
| RULE-350 | 错误 | 2026-10-14 11:00 | Row filtering applies only to opt-in methods with `@DataOwner` fields | 错误（整批同意评审建议）：不用注解开启的隐式 SQL 改写；AccessScope 显式传入仓储，异常即拒绝（fail-closed）（ADR-0009 §4、ADR-0019）。 |
| RULE-351 | 错误 | 2026-10-14 11:00 | An empty data scope fails open | 错误（整批同意评审建议）：空数据范围返回零行，不放行。 |
| RULE-352 | 错误 | 2026-10-14 11:00 | A second, different data-scope engine guards system user and dept writes | 错误（整批同意评审建议）：合并为一套数据范围模型（同 349），读写同一口径。 |
| RULE-355 | 正确 | 2026-10-14 11:00 | A8 dept "disabled" test and how it maps to legacy-system status and deleted | 正确+修（整批同意评审建议）：停用判定保留；NULL 的处理写成显式规则（仍视为有效，同步报告列出 NULL 行，与 A8 负责人确认）；「停用」与「撤销」分为两个状态，不再混同。 |
| RULE-356 | 正确 | 2026-10-14 11:00 | A8 is authoritative for dept leader, regional AR, category and delivery flag | 正确+修（整批同意评审建议）：A8 为部门负责人、区域 AR、类别、交付标识的权威来源；关系类型 ID 等改为配置并在启动时校验；多名负责人时取 ID 最小者并告警给数据管理员（确定性、不阻塞）。 |
| RULE-357 | 正确 | 2026-10-14 11:00 | Dept sync: insert, update on change, soft-delete what A8 no longer returns, revive | 正确+修（整批同意评审建议）：A8 部门随 A8 新增、更新、撤销、恢复；FORCITIS one 不再有手工部门（D-06）；FORCITIS one 自有列不被同步覆盖（按所有权矩阵，ADR-0011）。 |
| RULE-359 | 错误 | 2026-10-14 11:00 | Manual dept edits: parent valid, no cycles, unique name, no delete with children | 错误（整批同意评审建议）：组织数据在新系统只读，不提供手工维护部门（D-06，C1a-10）；树完整性由同步校验。 |
| RULE-360 | 正确 | 2026-10-14 11:00 | "Dept and below" = recursive children from a cache that includes deleted depts | 正确+修（整批同意评审建议）：「本部门及以下」递归保留；计算权限范围时排除已撤销部门，历史报表可包含。 |
| RULE-361 | 错误 | 2026-10-14 11:00 | A8 user sync scope uses a substring match, unlike the dept prefix rule | 错误（整批同意评审建议）：人员同步范围改用与部门同步相同的前缀规则（PATH 以 00000001 开头），人员不指向未同步部门。 |
| RULE-362 | 正确 | 2026-10-14 11:00 | A new A8 person becomes a legacy-system user with username = phone and a default password | 正确+修（整批同意评审建议）：A8 新人照旧建用户（ID 沿用 A8 ID）；不设本地口令（无共享默认口令）；创建时即写入 we_com_id。 |
| RULE-363 | 正确 | 2026-10-14 11:00 | Resignation = A8 login name is null → legacy-system user soft-deleted | 正确+修（整批同意评审建议）：统一「有登录名」判定（null 与空串都视为无）；离职时吊销会话、收回角色；离职后 HR 字段继续更新以保留历史。 |
| RULE-364 | 正确 | 2026-10-14 11:00 | Rehire and phone change restore and re-enable the legacy-system user | 正确+修（整批同意评审建议）：复职（A8 重新有登录名）时恢复；换手机号不覆盖 FORCITIS one 的手工停用（启用状态由 FORCITIS one 拥有）；用户名跟随 A8 登录名同步。 |
| RULE-365 | 错误 | 2026-10-14 11:00 | Per-field update semantics in user sync are asymmetric | 错误（整批同意评审建议）：A8 权威列按值同步并显式写 NULL（汇报线清空也同步）；单行缺部门或岗位时隔离该行并告警，不中断整批。 |
| RULE-366 | 错误 | 2026-10-14 11:00 | Users that drop out of the A8 result set are never deactivated | 错误（整批同意评审建议）：不在 A8 结果中的人员视为离职候选，经与部门同步相同的 80% 缩水保护后停用并吊销会话。 |
| RULE-367 | 错误 | 2026-10-14 11:00 | Post sync only adds and renames | 错误（整批同意评审建议）：岗位同步状态与删除（停用 / 删除同步到 FORCITIS one），保证「岗位必须启用」的校验有意义。 |
| RULE-368 | 错误 | 2026-10-14 11:00 | Manual user maintenance: unique username, mobile and email; enabled dept and posts | 错误（整批同意评审建议）：不提供手工维护人员（D-06）；身份键为 A8 ID，唯一性在全部行上保证（含已离职）。 |
| RULE-369 | 错误 | 2026-10-14 11:00 | Excel user import: per-row validation, optional update, init password | 错误（整批同意评审建议）：Excel 导入用户不迁移（D-06），不设共享初始口令。 |
| RULE-370 | 错误 | 2026-10-14 11:00 | Password change rules | 错误（整批同意评审建议）：员工无口令；仅应急账号可改口令，改后吊销其其他会话（ADR-0009）。 |
| RULE-372 | 正确 | 2026-10-14 11:00 | Two separate leader concepts: person leader and dept leader | 正确+修（整批同意评审建议）：「直属领导」（人员 leader）与「部门负责人」两个概念保留，每个流程写明用哪一个；同步时校验存在、在职、非本人、无环，不合格的报告数据管理员；审批取人按 RULE-250 往上推一级。 |
| RULE-373 | 正确 | 2026-10-14 11:00 | Org-change events: field diff after commit; dept sync events miss fields and repeat deletes | 正确+修（整批同意评审建议）：提交后按字段差异发组织变更事件保留；事件字段与同步字段用同一清单；已删除的不重复发 DELETED；经事件发布登记表投递（C1a-11）。 |
| RULE-374 | 正确 | 2026-10-14 11:00 | WeCom message recipients are resolved through `we_com_id` | 正确+修（整批同意评审建议）：按 we_com_id 解析收件人保留；所有发送处空值安全（缺失只告警跳过）；输出「在职但无 we_com_id」数据质量报告。 |
| RULE-375 | 正确 | 2026-10-14 11:00 | Non-production task messages go to one hard-coded person; agentId chosen by environment | 正确+修（整批同意评审建议）：非生产环境重定向保留；测试收件人改为配置的白名单，agentId 按环境配置，不写死个人。 |
| RULE-376 | 错误 | 2026-10-14 11:00 | WeCom card callback takes the acting user from the button key, not from FromUserName | 错误（整批同意评审建议）：操作人按 FromUserName → we_com_id → 用户确定，且必须是该任务处理人；回调 token 与 AES key 移到配置并轮换（SEC-006）。 |

## 实现方式变更（不改结论）

| Rule | 日期 | 用户决定（原话） | 实现方式 |
|---|---|---|---|
| RULE-275 | 2026-10-14 | 「退回即结束本轮，重提开新一轮 (Recommended)」（Phase 1a 复审 M3，经 psa-coordinator 询问） | 退回发起人＝以「已退回」结束本轮审批（仍要求原因、仍经 BEFORE 钩子）；不再使用发起人假首节点与令牌回移 |
| RULE-287 | 2026-10-14 | 同上 | 重提＝在同一单据上开新一轮（`start_rounds` 轮次 +1，幂等键含轮次）；审批历史按轮次保留，可逐轮查看 |
| RULE-280 | 2026-10-14 | 「留空并如实审计 (Recommended)」（第二波复审 Nit，经 psa-coordinator 询问） | 审批通过时意见为空就保持为空，不再补「同意」；审计如实记录 reason=null，界面上显示「（未填写意见）」（只用于展示，不写入数据）。驳回、退回仍须填写原因；RULE-302 的系统自动通过记为系统动作，原因用固定文案。依据：proposals/RULE-280_DEFAULT_REASON.md |
| RULE-385 | 正确 | 2026-10-14 19:49 | A closed legal-entity attribution period is never rewritten; a wrong end date is corrected by an append-only correction record | 正确（用户作答，2026-10-14 经 one-coordinator：「单独的更正记录表 (Recommended)」）：部门 / 员工法人归属期间只追加，已关闭期间不改写（T1）。结束日写错时，由组织数据管理员经独立用例追加更正记录（被更正期间、新结束日、原因、操作人），发 LegalEntityAttributionCorrected 并入审计；解析某日法人时合并期间表与更正记录；不追溯已写入单据的法人，需改单据法人的走冲销重做。规则卡：rules/LEGAL_ENTITY_ATTRIBUTION_RULES.md。 |

## 结论修订（不改原结论）

上表为原结论；决定其后变化时，原结论保留，修订记在 `RULE_REVIEWS.json` 条目的 `revisions` 中，当前结论取最后一条修订（METHODS M3「修订评审结论」）。

| Rule | 修订日期 | 用户决定（原话） | 出处 | 修订后的结论 |
|---|---|---|---|---|
| RULE-362 | 2026-10-15 | 「改为自有 UUIDv7 + A8 ID 对照列 (Recommended)」「主键统一」 | ADR-0033；intent 2026-10-15-005616、2026-10-15-005633 | 正确+修：员工与用户账号各用自有 UUIDv7，A8 人员 ID 只登记在来源对照；企业微信 userid 创建时写入 `external_identities`（原结论「ID 沿用 A8 ID」） |
| RULE-368 | 2026-10-15 | 同上 | 同上 | 错误：身份键为自有 `user_id`（UUIDv7），A8 人员 ID 只在来源对照，`(来源系统, 类别, source_id)` 唯一（原结论「身份键为 A8 ID」） |
| RULE-345 | 2026-10-15 | 「两份都批准 (Recommended)」 | ADR-0034；intent 2026-10-15-020346 | 正确+修：删除角色改为关闭最后一个版本、不再开新版本，角色与版本及授予历史保留（仍有当前有效授予时拒绝）；被删除角色的编码不得复用（原结论「删角色连带删关联」） |
