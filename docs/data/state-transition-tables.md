# 14 张有状态表：BC、聚合、法人归属（供 ADR-0058 起草，one-data，2026-10-17）

> 冻结（2026-11-04，#1263）：ADR-0058 起草时的扫描记录，不再更新；现行见 ADR-0058、ADR-0075。

来源：对 `origin/main` 的 M3 扫描（14 张，状态列有枚举 CHECK、所在 schema 没有 `state_transitions`；版本表自身即历史，已排除）。聚合与 role 取自 `data-access-registry.yaml`；状态值取自 CHECK（表上有时列表不全，以代码枚举为准，实现时核）。法人栏：表上有 `legal_entity_id` 列为「有」，没有则写判断与建议理由。

按 schema 分组，每个 schema 一张 `state_transitions`（ADR-0058 第 2 条）：

| schema（BC） | 表.状态列 | 聚合 | 状态值（CHECK） | 法人归属 |
|---|---|---|---|---|
| cost_timesheet（成本·工时） | timesheets.status | Timesheet | DRAFT、SUBMITTED、RETURNED、APPROVED、REJECTED | **有**（表上有 legal_entity_id） |
| organization_workforce（B-13 人员与组织） | employees.employment_status | 注册表未登记聚合（SNAPSHOT）；人员 OBJ-56 | EMPLOYED、SEPARATED | 无；归属经 `employee_legal_entities` 期间表，随时间变，不是稳定的表上列 |
| | legal_entities.status | LegalEntity | ACTIVE、INACTIVE | 就是法人本身：legal_entity_id 取自身 ID |
| | org_units.status | 未登记聚合（SNAPSHOT）；OBJ-55 | ACTIVE、DISABLED、REVOKED | 无；经 `org_unit_legal_entities` 期间表 |
| | positions.status | 未登记聚合（SNAPSHOT）；OBJ-58 | ACTIVE、DISABLED、DELETED | 无；岗位目录全公司共用 |
| | sync_runs.status | SyncRun | RUNNING、SUCCEEDED、HELD、RELEASED、FAILED | 无；技术运行，**建议登记例外** |
| platform_approval（平台·审批） | approval_requests.status | ApprovalRequest | 表上 CHECK 只列 REJECTED、RETURNED、CANCELLED（不全），实际含进行中与通过，以枚举为准 | **有**（legal_entity_id） |
| | approval_tasks.status | ApprovalRequest（次级） | PENDING、CLAIMED、COMPLETED、CLOSED | 随所属请求；任务表无法人列，转换行取请求的法人 |
| platform_identity（平台·身份） | authorization_requests.request_status | AuthorizationRequest | PENDING、CONFIRMED、REJECTED（ADR-0034-IMPL PR-3 再加 VOIDED） | 无；授权数据是全局（ADR-0026 GLOBAL），**登记理由** |
| | roles.status | Role | ENABLED、DISABLED | 无，全局；**注意**：ADR-0034-IMPL PR-2 把状态移到 `role_versions.role_status`，版本行本身就是历史，届时本行不再需要转换表 |
| | user_accounts.status | UserAccount | ENABLED、DISABLED | 无，全局，**登记理由** |
| platform_integration（平台·集成） | outbound_deliveries.status | OutboundDelivery | PENDING、SENT、SKIPPED、FAILED、DEAD_LETTER | 无；通知队列，技术性，**建议登记例外**（投递有 attempt 记录需求时另议） |
| | source_references.confirmation_status | SourceReference | PENDING、CONFIRMED、REJECTED | 无；来源对照，全局 |
| platform_numbering（平台·编号） | numbering_rule_versions.status | NumberingRule（次级） | PENDING_CONFIRMATION、CONFIRMED、SUPERSEDED | 无；编号规则按单据类型，全局（若以后按法人分规则再议） |

## 给 ADR-0058 的几点判断

1. **涉及 6 个 schema**（cost_timesheet、organization_workforce、platform_approval、platform_identity、platform_integration、platform_numbering），即 6 张 `state_transitions`，不是 14 张。
2. **建议登记例外的 2 张**：`sync_runs`（技术运行，已有 `sync_diffs` 细节；且 ADR-0054/0055 一次投产路线下运行时同步退役）、`outbound_deliveries`（队列状态，重试与死信已有 attempts 字段）。例外逐行评审，由 one-architecture 判。
3. **数据源口径变化**：`employees`、`org_units`、`positions` 目前是外部来源快照；ADR-0054/0055 把它们变成自有记录后，状态才由业务动作改变，转换记录才有意义。表项在自有化的卡里一并建，不在快照阶段补。
4. **聚合未登记**：`employees`、`org_units`、`positions` 在注册表没有 `aggregate`。建转换表前先在 `aggregates.yaml` 与注册表补聚合（我建议分别为 Employee、OrgUnit、Position）。
5. **法人列**：转换表的 `legal_entity_id` 允许为空，空时必须在注册表登记理由（上表「登记理由」栏）。工时、审批请求有稳定法人列；其余按上表。
6. **之后新增的有状态聚合**：F-1 的 `permission_adjustments`（状态六值）、提议表里约 129 个表项有「状态」列族，都要带转换记录；由守护在建表时拦。
7. **扫描脚本已按 ADR-0058 改**：M3 现在检查「schema 有 `state_transitions` 表，或注册表 `state_transitions_exempt: <理由>`」，版本表豁免；第一版的 `status_history` 登记选项已删。
