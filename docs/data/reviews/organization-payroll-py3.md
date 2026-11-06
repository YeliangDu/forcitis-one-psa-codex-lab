# Data design review: organization.payroll slice PY3 (bonus input manual port, one table)

Card: PY3 of `docs/architecture/proposals/organization-payroll.md` section 10 (design card #1403). Basis: that spec sections 4.5, 5, 7, 8, 9 (P-9 answered) and done-when 11, ADR-0087 decision 5.3, ADR-0043 R-2, gate `docs/data/data-design-gate.md` appendix A, at origin/main `793c4dd02`. Signed by one-design (same form as `organization-payroll-py0.md`, which lands first: PR #1425 is ahead in the merge queue; this record's schema statement depends on it). The schema `organization_payroll` is created by PY0's migration; PY3 adds its table to it.

## Scope

One table: `organization_payroll.bonus_inputs`. Not signed here: the other per-person payroll tables (profiles, enrolments, settlements and lines, disbursements), each in its own record now that P-9 is answered; `incentive_plan_versions` (not in the spec).

## What it is

The manual, approved port of ADR-0087 decision 5.3 / spec 4.5: until performance exists a bonus enters payroll as one approved input per relationship, month and reason. A row is written when the approval (`platform.approval`) of that input completes, so **a row is always an approved input**; there is no pending state. `DetermineBonus` references it; the settlement calculation reads it. When performance (AGG-106) is built the same port reads that source and this table does not change (a new `source_kind` value widens the CHECK).

## Table to object source (B1, B2)

| Table | Source | Aggregate | Registry role |
|---|---|---|---|
| `bonus_inputs` | AGG-76 bonus determination input (spec 4.5) | BonusDetermination | `AGGREGATE_ROOT`, `CONDITIONAL_UPDATE` (void only) |

Keys: UUIDv7 `id`, primary key `pk_bonus_inputs`. Audit columns `created_at`, `updated_at`; no `version` (one legal update, the void, guarded by a trigger). Cross-BC ids (`employment_relationship_id`, `paying_legal_entity_id`, `entered_by_employee_id`, `voided_by_user_id`, `approval_instance_id`): `uuid`, no foreign key (B4).

## Columns and constraints

| Column | Type | Rule |
|---|---|---|
| `id` | uuid | pk |
| `employment_relationship_id` | uuid NOT NULL | the relationship the bonus is paid for [R] (ADR-0086: per relationship); no foreign key |
| `paying_legal_entity_id` | uuid NOT NULL | the entity whose settlement carries it (the entity scope of every read, spec section 5) |
| `payroll_month_start_date` | date NOT NULL | `ck_bonus_inputs_payroll_month_start_date` `EXTRACT(DAY FROM payroll_month_start_date) = 1` |
| `bonus_amount` | numeric(19,2) NOT NULL | `ck_bonus_inputs_bonus_amount` `> 0` (a clawback is an adjustment line of a later settlement, C-8, not a negative input) |
| `bonus_currency` | text NOT NULL | `ck_bonus_inputs_bonus_currency` `= 'CNY'` (C-4) |
| `source_kind` | text NOT NULL | `ck_bonus_inputs_source_kind` `IN ('MANUAL_APPROVED')`; widening to a performance source is a lossless CHECK change |
| `reason_key` | text NOT NULL | key from the selection list (data, not a CHECK); format `~ '^[A-Z][A-Z0-9_]{1,39}$'` |
| `approval_instance_id` | uuid NOT NULL | the approval of this input (the "approval number" is mandatory); `uk_bonus_inputs_approval_instance_id` unique, so a replayed `ApprovalCompleted` finds one row |
| `entered_by_employee_id` | uuid NOT NULL | the employee who entered the input, as the approval carries it (`ApprovalCompleted.initiator` is an `EmployeeId`); separation of duties: the settlement approver (`CurrentUser.employeeId()`) cannot be this employee, done-when 11 |
| `status` | text NOT NULL | `ACTIVE`, `VOIDED` |
| `voided_by_user_id` | uuid null | 为空表示 not voided |
| `voided_at` | timestamptz null | 为空表示 not voided |
| `created_at`, `updated_at` | | audit |

Constraints:

- `ck_bonus_inputs_status` `status IN ('ACTIVE','VOIDED')`; `ck_bonus_inputs_reason_key` the format check above.
- `ck_bonus_inputs_void_columns` `(status = 'VOIDED') = (voided_at IS NOT NULL) AND (voided_at IS NULL) = (voided_by_user_id IS NULL)`.
- `uk_bonus_inputs_relationship_month_reason` partial unique `(employment_relationship_id, payroll_month_start_date, reason_key) WHERE status = 'ACTIVE'` (a voided input frees the key for a corrected one). **Difference from spec 4.5** (which says a row per relationship and payroll month; the section 8 sketch says one approved input per id): this key is my technical choice without a user decision (gate O3 wants a source: it is stated here as a design choice, and the user changes it with one constraint) and allows several reasons in the same month (for example a year-end and a project bonus) and refuses the same reason twice. The settlement sums the active rows of a month.
- Trigger `tg_bonus_inputs_change` (and `tg_bonus_inputs_truncate`): insert only as `ACTIVE`; the only update is `ACTIVE` to `VOIDED` setting `status`, `voided_by_user_id`, `voided_at`, `updated_at` and nothing else; no delete (the purge function of P-9 is the only delete path, as the owner role, like W2's `purge_employee_history`). No other write.
- Index `ix_bonus_inputs_entity_month` `(paying_legal_entity_id, payroll_month_start_date)`; the unique key above leads with `employment_relationship_id`.

## Differences from the section 8 sketch

1. `amount` becomes `bonus_amount` plus `bonus_currency` (naming standard 2.4: every `_amount` pairs with a `_currency`); `reason_key`, `source_kind`, `approval_instance_id` as sketched.
2. Added `entered_by_employee_id` (the SoD rule of done-when 11 needs the enterer), `status` with the void pair, and the key above.

These two additions are **PY3 scope the spec does not list** (spec 4.5 names only `DetermineBonus` → `BonusDetermined` and a row without status; to be added to spec 4.5 and the PY3 card): (a) `RecordApprovedBonusInput`, run when the approval of an input completes (`platform.approval` result; `entered_by_employee_id` is the `initiator` of `ApprovalCompleted`; payroll does not read approval tables; the amount and reason come from the form fields of the started request, read through the approval api by the "Approval seam" section below, which therefore holds a per-person pay figure outside this table: its retention and classification follow the approval module's rules and are an owed check for the PY3 card, not decided here); (b) `VoidBonusInput`, run by a payroll-role holder, no approval of its own because it only withdraws an unconsumed input. State machine: `ACTIVE` (initial, set by (a)) → `VOIDED` (set by (b)); Java enum equals `ck_bonus_inputs_status`. Events: (a) is the entry of `BonusDetermined`'s input; the void raises no event of its own (recorded in `state_transitions` and the audit log; nothing downstream subscribes; if the settlement needs to know it re-reads active rows, below). No other command or event changes. B3a: states, enum, commands and events agree on this list.

## Invariants to constraints (B5, O4)

| Invariant | Carrier | Reason when no constraint |
|---|---|---|
| One approved input per approval | `uk_bonus_inputs_approval_instance_id` | |
| The same reason is not entered twice for a relationship and month | `uk_bonus_inputs_relationship_month_reason` | |
| Month column holds the first day | `ck_bonus_inputs_payroll_month_start_date` | |
| Amount positive, CNY | `ck_bonus_inputs_bonus_amount`, `ck_bonus_inputs_bonus_currency` | |
| A row is never edited, only voided | `tg_bonus_inputs_change` | |
| Settlement approver is not the input's enterer (done-when 11) | | cross-aggregate; checked by `ApproveMonthlyPayroll` against `entered_by_employee_id` (the approver's `CurrentUser.employeeId()`) |
| An input is voided only while no `APPROVED` settlement of its entity and month exists (P-R3: approved amounts never change) | | cross-aggregate; checked by the void command |
| A void and a settlement approval do not race | | the void command takes `FOR UPDATE` on the settlement row of that entity and month first and is refused when it is `APPROVED`; a void after `CALCULATED` but before `APPROVED` is allowed and `ApproveMonthlyPayroll` re-reads the active rows and refuses ("recalculate") when their sum differs from the settlement's bonus lines |
| The relationship is in service and `paying_legal_entity_id` is its paying entity in that month | | another context's data; checked by the entry command |
| Input rows carry no event payload with the amount (P-R5) | | event schema contract test, not a table rule |

## Business key, class, time (O3, O6)

Business key: (`employment_relationship_id`, `payroll_month_start_date`, `reason_key`) among active rows. Class: business record, declared (an approved input is a fact). Time: a month, not an interval; no versioning (correction = void and enter again, which the approval trail records).

## Query paths (O10)

| Path | Filter | Sort | Expected rows | Page | Index |
|---|---|---|---|---|---|
| Settlement calculation: inputs of an entity and month | `paying_legal_entity_id`, `payroll_month_start_date`, `status = 'ACTIVE'` | none | tens to hundreds | none | `ix_bonus_inputs_entity_month` |
| A person's inputs (payroll view) | `employment_relationship_id`, month range | `payroll_month_start_date` desc, `id` | a few per year | cursor | `uk_bonus_inputs_relationship_month_reason` (leading column) |
| Replay lookup | `approval_instance_id` | none | 1 | none | `uk_bonus_inputs_approval_instance_id` |

Thousands of rows a year; no partition, no hot-state partial index (almost all rows are `ACTIVE`).

## Cross-cutting, ADR clauses, value objects (O13, O14, O15, O16)

- Money `numeric(19,2)` CNY (C-4); dates only; idempotency by `approval_instance_id`; legal entity id present (the paying entity, O13); no analytical fact (A1 to A8: none; the totals event feeds reporting later).
- ADR-0043 R-2: the table holds personal information, so its retention is not `PERMANENT`; the user's answer fixes the period (below). Free text: none (a key, not text). `jsonb`: none.

## Registry drafts and classification (C1 to C5)

Registry: role `AGGREGATE_ROOT`, aggregate `BonusDetermination`, business key `[employment_relationship_id, payroll_month_start_date, reason_key]`, mutability `CONDITIONAL_UPDATE` with condition "`ACTIVE` to `VOIDED` once, setting the void pair (`tg_bonus_inputs_change`); no delete except the purge function", **retention: ten years after the person's last separation, removed by the purge route** (user 2026-11-05 「P-9 a」, lead fragment `2026-11-05-154025-one-lead-P-9-a` (as relayed by lead; not verifiable in the repository until lead's batch merges)), lineage `NEW`, writers `organization.payroll`. Until the purge function covers payroll tables the registry line uses the same marker as the other per-person tables (`{policy: PERMANENT}` with the comment that the purge extension is owed, `data-access-registry.yaml` line 78 pattern); **owed**: the payroll context owns its own purge function (owner role, same trigger bypass as W2's `purge_employee_history`, which stays workforce's and never deletes in another schema), called through a port with the person's relationship ids once the person's last separation is ten years past ("last separation" is person-level and this table keys by relationship, so the purge card needs the person → relationships resolution); a later purge card, and the PY3 implementation card must not claim the retention is enforced before then. ADR-0043 R-2's test-readable reason field is not built yet; the registry line carries the user's decision source in its comment.

Classification (`data-classification.txt`). **Mapping:** the user's word `CONFIDENTIAL` (P-9 answer; the payroll and accounting specs use it) is not a register level: the register knows `PUBLIC|INTERNAL|SENSITIVE|RESTRICTED` (`ClassificationRegister`, test line 30 rejects `CONFIDENTIAL`), and its header puts 「工资与日薪」 at L3 `SENSITIVE`. So per-person pay columns are registered `SENSITIVE`; lead confirms this mapping with the user (it is the evident reading, not a new decision). Consequence of L3: these columns are not exposed raw in web or api output (masked, or an entry in `sensitive-response-exceptions.txt` for the payroll views) and never go into event payloads (C4; P-R5 already says so). Columns that do not carry the pay figure stay at the default level.

| Column | Level | PI | Reason |
|---|---|---|---|
| `bonus_amount` | `SENSITIVE` | `PI` | 用途：one person's bonus, the pay figure of a relationship |
| `reason_key` | `SENSITIVE` | `PI` | 用途：why a person received a bonus, tied to one relationship |
| `bonus_currency`, `source_kind`, `payroll_month_start_date`, `status` | `INTERNAL` | `NONE` | 用途：qualify the pay figure; not identifying alone |
| `entered_by_employee_id`, `voided_by_user_id`, `voided_at` | `INTERNAL` | `NONE` | 用途：separation-of-duties and audit identities of staff acting in their role |

Surrogate and reference ids (`id`, `employment_relationship_id`, `paying_legal_entity_id`, `approval_instance_id`) are unregistered (default `INTERNAL`); reads are still restricted to payroll roles and the person themselves by the scope in spec section 5, not by the column level. Bonus amounts are `PI`, not `SPI`: legal's answer on pay as SPI (ADR-0043 U-5, U-11) is not on record here; if legal says SPI, `bonus_amount` changes to `SPI` in one line.

## Comments (C1)

Table: 「粒度：每个任职关系 × 薪酬月份 × 奖金原因一行（有效行），一行就是一条已审批的奖金输入，只能作废不能改」. Every column has a comment; nullable columns state 「为空表示」 as above.

## Baseline, target, transition, rollback (T1 to T4)

New table, no legacy table of this name, no data at creation (inputs come from the approval flow; legacy history comes with the PY10 import slice, its own mapping). Rollback: drop the table (nothing references it until PY4). Expected diff for the schema PR: `organization_payroll.bonus_inputs`, its constraints, the two triggers and functions, the index, and the classification lines.

## Approval seam, process and permissions (amendment, Refs #1436)

Found while building PY3 (one-dev-identity): the record named a form payload the approval api could not return, no process, and no permissions. Lead ruling (technical): option A below (the `formFields` api read; its reviewer-found consequences are in this section; a new `platform.approval` api method should be reviewed with that module's owner). Facts checked on `origin/main` `cf959da57`.

**Starting.** A new command `RequestBonusInput` (not a table change: it writes no payroll row, so the record stays "a row is always approved") derives its request id (a UUID) from the command's idempotency key, so a client retry reuses it and does not start a second approval, and calls `ApprovalProcesses.start` with: process key `organization_payroll_bonus_input` (format `{subdomain}_{bc}_{name}`, G47; the owning context declares the constant in its api, as `TimesheetApprovalProcess` does); `document` = `DocumentReference(BONUS_INPUT, <that request id>)`, so the document id is payroll's own request id and is **not** a table key (nothing in payroll stores it; the approval's `instanceId` is what `bonus_inputs.approval_instance_id` keeps); `legalEntityId` = the paying entity; `initiator` = the caller's employee; `formFields` below; `round` = 1 (one round per request id; after a return or reject the person starts a new request); `trigger` = the command's idempotency key (there is no event to cite); `contentHash` over the field values; `summary` for the cards. A retry with the same request id is the same round and returns the same instance (the approval api is idempotent per process type, document and round). Payroll never takes an initiator from a client (I-5).

**Direct start** (a deliberate departure from the usual `DocumentSubmittedForApproval` event + `StartApprovalOnSubmission`, so the amount never travels in a broadcast event): `start` joins the command's transaction and a refusal (for example the unknown-field refusal above, or a foreign initiator) is returned to the caller synchronously as the command's error.

**Form fields**: `employmentRelationshipId`, `payrollMonthStartDate`, `bonusAmount`, `bonusCurrency`, `reasonKey`, each a single-value `ApprovalField`. **Approval-module change owed (found in review):** `StartApproval` rejects a form field the process does not use (`PLATFORM_APPROVAL_FORM_FIELD_UNKNOWN`), and `FlowableApprovalEngine.formFieldsOf` returns only fields read by an assignment rule (`FORM_FIELD_USERS`, `DEPARTMENT_HEAD`). The recommended route reads none, so these five fields would all be refused. The `platform.approval` card must let a BPMN declare data-only fields (for example a `forcitis:formField name=` extension element that `formFieldsOf` also returns); this widens lead's ruling by one approval-module change and is flagged to lead for confirmation. The alternative, payroll keeping its own pending request, is a table change and is not taken. `RecordApprovedBonusInput` validates the fields with the same rules as the table's CHECKs and rejects a malformed completion without writing (the approval stays as it is; the failure is reported to payroll operators).

**Reading the fields.** `ApprovalProcesses` gets one read: `Map<String, List<String>> formFields(ApprovalInstanceId instance)`, returning the fields as submitted at start, empty for an unknown instance; it does not lock and needs no actor (the caller is a trusted module listener, like `progress`). G4 counts only api methods that have a caller outside the module (`ArchitectureRulesTest.apiSurfaceStaysNarrow`): on `origin/main` `platform.approval` counts **4** (`progress`, `checkWithdraw`, `withdraw`, `cancel`, called by `cost.timesheet`; `start` is called only inside the module, `isFinished` and the two `ApprovalHook` methods have no outside caller). With payroll calling `start` and `formFields` it counts **6**, limit 12. `ApprovalCompleted` stays unchanged (it carries document, process key, instance id, round, outcome and initiator; adding the amount to a broadcast event would spread a pay figure to every subscriber). The listener runs only for `processKey = organization_payroll_bonus_input` and outcome `APPROVED`, idempotent by `approval_instance_id`; every other outcome writes nothing (nothing was written at start).

**Document type.** `BONUS_INPUT` is a new document type code: the numbering seed rows and the `documentType` enum of `docs/architecture/events/approval.yaml` (today `QUOTE`, `TIMESHEET`, `JOURNAL_VOUCHER`) both gain it; `ApprovalCompleted`'s payload is otherwise untouched. Owed to the implementation card with the BPMN file `processes/organization_payroll_bonus_input.bpmn20.xml`.

**Routing: lead decision 2026-11-06, pending user review:** one stage, the initiator's supervisor (`INITIATOR_SUPERVISOR` level 1), then a payroll-role holder who is not the initiator; no routing flags. The next paragraph was written before that decision and keeps the reasoning. **Routing: not decided here (as originally written).** Who approves a bonus (nodes, order, deadlines) is a business choice and is not in the spec; the BPMN shape is a countersign chain of `assignmentRule` nodes as in `cost_timesheet_approval`. Recommendation to lead for the user: one stage, the initiator's supervisor (`INITIATOR_SUPERVISOR` level 1) then a payroll-role holder who is not the initiator; no routing flags (nothing branches on data the module computes). Done-when 11 stays: the settlement approver may not be `entered_by_employee_id`. Until the user answers, the implementation card may ship the process with that chain marked as provisional in the BPMN comment only if lead accepts it; otherwise the card waits.

**Permissions** (G43: `{subdomain}.{BC}:{resource}:{action}`, action from `standard_actions`; resource singular, kebab; follow the catalogue):

| Code | Holder | Used by |
|---|---|---|
| `organization.payroll:bonus-input:submit` | payroll role | `RequestBonusInput` (starts the approval) |
| `organization.payroll:bonus-input:update` | payroll role | `VoidBonusInput` |
| `organization.payroll:bonus-input:read` | payroll role | the PY4 settlement read and the list; the person reads only their own through the scope of spec section 5 |

`RecordApprovedBonusInput` has no user permission: it runs as the system listener of `ApprovalCompleted`. Void is a business command, not a delete (A-1), and no action in `standard_actions` is named "void"; it maps to `update` because its only effect is the one legal update of the row (`ACTIVE` to `VOIDED`), as `organization.attendance:locked-day:update` does for its own state change; adding a `void` action would be a glossary change for one use and is not proposed. `delete` is not used: the row is never deleted (purge route only).

**Changed by this amendment:** one column, `entered_by_user_id` becomes `entered_by_employee_id` (the id type the approval carries; the approver side uses `CurrentUser.employeeId()`), with the invariant row and classification line following. **Not changed:** the other columns, constraints, triggers, registry lines, retention (P-9 a), events. The approval's own copy of the amount (the form fields) follows the approval module's retention (fact: the fields live only as engine process variables; `ProcessArchiveJob` keeps them online 3 years and then archives only whitelisted variables, so the approval-side copy of the amount is deleted after 3 years and never archived); whether the approval store must follow the ten-year purge of P-9 a is a **question for lead/legal outside this record**, owed before the PY3 card claims the retention is enforced.

## Events and read models (E1 to E3)

`BonusDetermined` (analytical): ids only, no amount or reason (P-R5); written in `docs/architecture/events/` with the implementing slice (catalogue rule for code-tier events). No read model, no migration mapping.

## Result

数据设计审查
- 审查：one-design，2026-11-05，依据 origin/main `793c4dd02`
- 判定：B0（阶段入口，不适用）、B1、B2、B3（状态：ACTIVE/VOIDED，上文）、B3a（状态、枚举、命令、事件一致，见上文）、B4（跨 BC 无外键）、B5、B6（无跨表引用）、B7（枚举最小集，可无损加宽）、B8（术语：`bonus_input`、`reason_key` 等词不在 `terms.yaml`，与 L1a 先例相同，作为实现卡前置，须先在 `backend/glossary/proposed/` 提出）、O1、O2（UUIDv7 主键）、O3、O4、O5、O6、O7（作废只改列对，不改写）、O8（金额与币种成对）、O9、O10、O10a、O11（千行级，无需分区）、O12、O13、O14、O15、O16、C1 至 C5（分级按上文映射）、T1 至 T4、E1 通过；A1 至 A8 不适用（无分析事实，A6 已判）
- 豁免与待用户决定：保留期与分级已由用户答复（P-9 a）；`PI` 还是 `SPI` 待法务（ADR-0043 U-5、U-11），现按 `PI`，改标记为一行改动；清除路径扩到薪酬表是后续清除卡的欠项
- 结论：通过（仅 `bonus_inputs`）
- 数据设计：通过（one-design，2026-11-05，依据 793c4dd02）
- 修订（one-design，2026-11-05，依据 cf959da57，Refs #1436）：增补「Approval seam, process and permissions」一节；仅一列改名 `entered_by_user_id` → `entered_by_employee_id`（审批携带的是 EmployeeId），其余表、约束、触发器、登记、保留期、事件均未变，上面的判定不变
