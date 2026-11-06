# One strong type per command kind, and declarations kept in their own module (#1585)

Design card #1585. Draft ADRs: ADR-0090 (pattern A, typed command kinds) and ADR-0091 (pattern B, declarations in their own module), Chinese, in `docs/adr`; two ADRs because lifecycle section 7 says one decision per ADR and the two patterns share no code, guard or step (first-round review of the one-ADR draft). This file holds the facts, the options and the done-when lines for both; the ADRs hold the decision text. Evidence marks: **[V]** read on origin/main (head b7ebe7ab2, 2026-11-06) by the command shown; **[U]** not verified.

## 1. The one thing to decide

Two patterns turn compile-time errors into runtime or review errors and are merge-conflict magnets: (A) one aggregate and one request carry many command kinds with optional fields; (B) central registries (permission, system role, error code, audit subject kind) that every slice appends to. Decide the target shape of each and an incremental path with no big-bang rewrite and no table change unless shown necessary. This is a technical decision (playbook: the design session chooses); no business question arises. The adoption of the ADR is the user's.

## 2. Facts, pattern A (kinds with optional fields)

Window for every commit count below: commits that touch the path, `git log --since=2026-10-30 --oneline -- <path>` on origin/main [V]. The lead's count (31 / 29 / 24) and an independent count from 11-01 (26 / 30 / 23) differ from mine only by window and merge handling; the order of magnitude is the same.

| # | Fact | Evidence |
|---|---|---|
| A1 | Three kind enums drive commands: `PersonnelCommandKind` (14), `OrgStructureCommandKind` (9), `CountersignCommandKind` (23: the union of the other two, a read-side classification, used in 4 files, no `switch`) | [V] `workforce/api/*CommandKind.java`; `grep` of the enum bodies |
| A2 | Personnel: the internal command `EnterPersonnelCommand` has **24 components**, all but kind and two others `@Nullable`, and **three telescoping constructors** besides the canonical one; the HTTP record `EnterPersonnelCommandRequest` is a second wide record; the frontend holds a third list `KIND_FIELDS` per kind | [V] `workforce/api/EnterPersonnelCommand.java`, `web/EnterPersonnelCommandRequest.java`, `frontend/apps/web/src/modules/workforce/model/personnel-command.ts` |
| A3 | The rule "which fields a kind takes" is written once as code in `PersonnelCommand` (a `switch (kind)` of 8 arms plus about ten `if (kind == ...)` tests, lines 117 to 365), once as the `ck_personnel_commands_*` CHECKs at the database, and once as data in the frontend `KIND_FIELDS`; the Java rule and the frontend list are tied by nothing | [V] `workforce/domain/PersonnelCommand.java`; frontend file above; CHECK names [U] (not re-read) |
| A4 | Size and churn of the personnel path: `JdbcPersonnelCommandRepository` 653 lines (49 KB), `PersonnelCommand` 618 lines, `PersonnelCommandService` 465 lines; 17 files name the kind; commits since 10-30: repository 30, service 31, aggregate 23 | [V] `wc`, `git log` |
| A5 | Org structure: `EnterOrgStructureCommand` has 15 components (12 nullable), the HTTP record repeats them; `JdbcOrgStructureCommandRepository` 650 lines (48 KB), `OrgStructureCommand` 447 lines; 16 files name the kind. **Its field set depends on the pair (target kind, command kind), not on the command kind alone**: a position takes only `CREATE` and `DISABLE`; a `CREATE` of an org unit requires the delivery flag and allows short name, region, parent and legal entity; a `CREATE` of a position allows none of these (`OrgStructureCommand.java` lines 138 to 180, read by the reviewer) | [V] |
| A6 | Defects of this class already happened: #1579 (the six acting fields and the reason missing on the HTTP record, so `ACTING_START` answered 422) and #1489 (the frontend `KIND_FIELDS` missed a HIRE field) | [V] card text; `EnterPersonnelCommand` has 24 components against 18 on the HTTP record |
| A7 | What is shared across the kinds and must stay one thing: the status machine (`PENDING`, `HELD`, `EFFECTIVE`, `WITHDRAWN`, awaiting countersign), the due-date application job `ApplyDueCommand`, the countersign freeze, audit, idempotency, one table per family (`personnel_commands`, `org_structure_commands`) with one nullable column per optional field | [V] `PersonnelCommand`, `ApplyDueCommand`, `OrgCountersignFreeze`; table names [U] (the schema was not re-read for this card) |
| A8 | Sealed interfaces are already used on main (`LoginLookup`, `PostingPeriod`, `RoleChangeResponse`); no `@JsonTypeInfo` or `@JsonSubTypes` appears anywhere in main code, so a discriminated HTTP body (OpenAPI `oneOf`) would be the first | [V] `grep -rn "sealed" `, `grep -rln "JsonSubTypes\|JsonTypeInfo"` (no match) |
| A9 | Other kind enums: `PermissionAdjustmentKind` 2, `AuthorizationRequestKind` 3, `CountersignRequestKind` 2, `AssignmentKind` 2, `ChangeKind` 7 (each at most one `switch`; field use not read to the end [U]); **`PayrollRuleRowKind` has 7 values and kind-dependent optional columns** (`applied_rate`, `quick_deduction_*`, `flat_*`, `rounding_kind`, `party_kind`, the bounds; payroll baseline lines 42 to 72), so by the criterion of section 4 it qualifies and is the next candidate after the workforce families. `HIRE` itself has two shapes: a new person (with a profile) and an existing employee with another employer | [V] counts; the reviewer's reading of the payroll baseline and the golden-sample support code |

## 3. Facts, pattern B (central registries)

| # | Fact | Evidence |
|---|---|---|
| B1 | `Permission` (130 lines) lists every permission **twice**: an enum constant and a string constant in `Permission.Codes`; commits since 10-30: 16 (all since 11-01) | [V] `platform/identity/api/Permission.java` |
| B2 | `SystemRoleDeclaration` (the six system roles with their permission sets and a declaration revision number, ADR-0034 decision 1 item 7): 14 commits since 10-30 | [V] |
| B3 | `WorkforceError` (58 lines, one enum for the whole workforce module): 15 commits since 10-30. Error enums are already one per module (15 `*Error` files), so this is a per-module hot file, not a cross-module registry | [V] |
| B4 | `AuditSubjectKind`: 6 commits since 10-30; 63 files name it (the card said 25 values; the enum file has 52 lines) | [V] |
| B5 | Permission codes are copied across modules: 8 constant-holder classes (`AttendancePermissions`, two `RecruitmentPermissions` in web and application, `WorkforcePermissions`, `AccountingPeriodPermissions`, `FilePermissions`, `IntegrationPermissions`, `AuditPermissions`) beside `SensitivePermissions` and the two port files (`CallerPermissions`, `AuthorityCallerPermissions`) that the name pattern also matches; `PersonnelCommandService.PERMISSION` duplicates `WorkforcePermissions`; 54 files hold `@PreAuthorize`, 132 annotation lines build strings | [V] `find ... -name "*Permissions.java"`; `grep -rl "@PreAuthorize"` |
| B6 | Kernel ports take `String`: `CallerPermissions.holds(String)`, `AccountRoles.holds(UserId, String)`, while identity has a typed `RoleCode` record (no `PermissionCode` exists). Only 5 files outside `platform.identity` mention `RoleCode`, so the typed role code mostly stays inside identity | [V] `kernel/CallerPermissions.java`, `kernel/AccountRoles.java`, `platform/identity/api/RoleCode.java`; `grep -rl RoleCode` |
| B7 | Consistency of codes rests on `NamingSurfaceTest` G43 (it reflects over `Permission.Codes` and `Permission.values()` and checks format and that every `@PreAuthorize` code is catalogued) and reflection tests | [V] `NamingSurfaceTest.java` lines 167 to 199 |
| B8 | An annotation value must be a compile-time constant, so a typed `PermissionCode` record alone cannot go into `@PreAuthorize`; a string constant stays for the annotation | Java rule [V]; hence option B-ii below keeps one constant per permission |

## 4. Options, pattern A

| Option | What it is | Gains | Costs |
|---|---|---|---|
| A-1 | A sealed interface per family with one record per **shape**: per command kind for personnel (where a kind has two shapes, such as `HIRE` of a new person and of an existing employee, two records) and per (target kind, command kind) pair for org structure (`PersonnelCommandBody permits HireNewPerson, HireExisting, Transfer, ...`); one aggregate and one table remain; the aggregate holds the body | The compiler lists the fields of each kind; a missing field is a compile error; `switch` over the sealed type needs no `default`; the HTTP body can be a discriminated union and the frontend field list can be generated | Many small records; the repository maps each record to the same wide row; HTTP `oneOf` is unproven in this repo (A8) |
| A-2 | One aggregate (and table) per kind family (hire and reinstatement; post change; legal entity; acting duty) | Smaller aggregates | Duplicates the status machine, due-date job, countersign freeze; table and migration changes; a cross-family ordering rule appears |
| A-3 | Keep the wide record, publish a per-kind field matrix as data and generate the frontend list and a validation from it | Cheapest; ends the frontend drift | The compiler still sees 24 optional fields; the backend rule and the matrix can still disagree; telescoping constructors stay |
| A-4 | Keep as is | None | The cost grows with every kind (the acting slices added six fields and one defect) |

**Chosen: A-1, entered by an incremental path** (section 6): the sealed interface starts with one permitted subtype `Wide` that is today's record, so nothing breaks; each slice that touches a kind moves that kind to its own record; the `Wide` type is frozen (a test fails when it gains a component) and disappears with the last kind. The tables do not change: a per-kind record is mapped to the same row. A-2 is rejected unless the evidence changes (it forces the table and the shared machinery to split without a measured gain). A-3 is a fallback if the HTTP `oneOf` cannot be exported (fact A8), not an alternative to A-1 inside the domain: the domain type is sealed either way; only the edge differs.

Not converted now: `CountersignCommandKind` (a classification, no fields), the 2- and 3-kind enums, `PayrollRuleRowKind` and `ChangeKind` until their kind-dependent optional fields are read (A9). Rule for later cases, stated in the ADR: a family gets per-shape records when it has more than one kind-dependent set of optional fields. State set after entry (`countersignPrevious/NewLegalEntityId`, `orgStructureCommandId`, `employmentRelationshipId`, `targetAssignmentKind`) stays on the aggregate as today (nullable there), not in the immutable body; parts shared by several records (the date pair, person data) are nested records. The sealed type is a workforce **api** type replacing `EnterPersonnelCommand` (its callers are inside workforce [V `git grep`]); the aggregate in domain takes the body.

## 5. Options, pattern B

| Option | What it is | Gains | Costs |
|---|---|---|---|
| B-i | Each module declares its own permissions once (an interface of constants in its `api`, since G2 allows only interfaces, records and enums there, holding typed `PermissionCode` values and the string constants for annotations; each declaration carries the code, its grant mode (ADR-0032 decision 2) and the deprecated mark (ADR-0034 decision 1 item 3); plus a catalogue contribution bean); `platform.identity` collects the contributions at start into the permission catalogue; `Permission` and `Permission.Codes` shrink to the platform's own and go away | A new permission touches one module file; no copies; G43 checks the collected catalogue | Identity and the guard need a collection mechanism; every module that checks another module's permission imports it from that module's `api` (allowed by the dependency rules); one-time migration of 10 classes and 54 annotation sites |
| B-ii | As B-i, plus typed `PermissionCode` and `RoleCode` in `kernel` (the ports take them instead of `String`; identity's `RoleCode` moves or is re-exported) | Wrong-type calls fail to compile; ends the copied constants | A type in `kernel` (the shared kernel is a deliberate place, ADR-0004); annotation strings remain, one per permission, next to the typed value |
| B-iii | Generate the catalogue from annotations (scan `@PreAuthorize` strings) | No declaration to write | The catalogue is derived from usage, so a typo creates a permission; rejected |
| B-iv | Keep central | None | 16 and 14 commits in a week and the copies |

**Chosen: B-ii in two steps** (B-i first, the kernel types second). Role grants stay central: `SystemRoleDeclaration` keeps the six system roles and their permission sets with the revision number (ADR-0034 decision 1 item 7), because who may hold which permission is a security decision that should be reviewed in one place; it refers to permission codes by the typed constants where the module is visible to identity and otherwise by code string, and the start-up check fails when a granted code is not in the collected catalogue. Moving grants into modules would change the revision rule of ADR-0034 and is **not** proposed here.

Adopted-ADR constraints on pattern B (found in review): the collected catalogue never removes a code, a retired code is marked deprecated (ADR-0034 decision 1 item 3, today `Permission(String, boolean deprecated)` and `isLive`); ADR-0032 decision 2 items 1 to 3 want every code to declare a grant mode and to be listed in the central snapshot `backend/architecture/permission-grant-modes.txt`, which belongs to the user (item 5) and does not exist on main yet [V `git ls-tree origin/main`], so a new permission still touches that snapshot once it exists; this draft changes neither. **Compile-time binding is lost inside identity**: the module graph has `platform.identity -> organization.workforce, platform.integration, platform.audit` only and payroll, approval, numbering, timesheet and eventing depend on identity, so identity cannot import their constants (a cycle); `SensitivePermissions`, `AdministratorMode` and `SystemRoleDeclaration` already name those codes through `Permission.Codes` and would become strings. The answer to unverified item 3: collecting a `List<kernel interface>` needs no module edge (`ModuleDependencyTest` reads direct class dependencies only), the typed references from identity are what would. So a fourth check is required: every permission code named anywhere in identity (role grants, the sensitive set, administrator mode) is in the collected catalogue, so a typo cannot silently make a payroll code non-sensitive.

Error codes: **unchanged.** G48 and CS-2.24 (and the `NamingSurfaceTest` G48 message) say one error-code enum per module; the 15 `*Error` files are one per module, so `WorkforceError` being a hot file is the rule's consequence. Splitting it is a rule change (CS-7.5) and the user's; recorded as out of scope. `AuditSubjectKind`: leave as is (6 commits).

## 6. Incremental path (no big-bang)

| Step | What | Trigger | Prerequisite |
|---|---|---|---|
| S0 | Prototype that settles fact A8: one sealed HTTP body with `@JsonTypeInfo` and `@JsonSubTypes`; run `OpenApiExportTest` and the frontend schema generation; record whether `oneOf` and a discriminator come out and whether `Schema<'...'>` types are usable | first slice that converts a kind | ADR adopted |
| S1 | `PersonnelCommandBody` sealed with `Wide` plus the first converted kind; the freeze test on `Wide`; HTTP edge per S0 result | the next personnel slice (for example acting duty slice 2 or a hire change) | S0 |
| S2 | Each later slice that touches a kind converts it and deletes its arms from `PersonnelCommand` rules; same for org structure | every slice | S1 |
| S3 | Last kind converted: `Wide`, the three extra constructors and the frontend `KIND_FIELDS` are deleted. Needs the acceptance tests that call the old constructors to change (`GoldenSampleSupport`, `RevokeDisableFloorSupport`; 6 files name the wide records [V, reviewer]): the test session does that on its own card; until then the old constructors stay | the last conversion | S2 and the test card |
| P1 | `PermissionCode` declarations in a first module (workforce, the biggest) and the collection in identity; G43 reads the collected catalogue as well as `Permission.Codes` | the next slice that adds a permission | ADR adopted |
| P2 | Each later slice declares its new permissions in its own module; the existing ones move when a slice touches their module; copies are deleted as modules move | every slice | P1 |
| P3 | Kernel types `PermissionCode` and `RoleCode`; ports change from `String`; `Permission`, `Permission.Codes` removed when empty. 20 acceptance files import `Permission` and `DeprecatedCodeIT` sets its private field by reflection [V, reviewer]: the test session changes them on its own card, and a `Permission` facade stays until then | after most modules moved | P2 and the test card |

No step changes a table. A step that would (a new column for a kind) goes through the data design gate as usual.

## 7. Done-when (one checkable line each, for the implementing slices and the test engineer)

1. S0 prototype: `OpenApiExportTest` passes with a sealed discriminated body, and the generated frontend type is a union keyed by `commandKind` for personnel; for org structure the discriminator is the pair (target kind, command kind), expressed as one composite discriminator property or as nested unions, and the prototype shows which; `@JsonSubTypes` maps every not-yet-converted kind to `Wide` and the flat JSON of the existing HTTP acceptance bodies still parses. If `oneOf` cannot be exported, the step records the fallback (flat HTTP record converted at the edge) and the decision stays valid.
2. Each converted kind: a record with only its own components; the aggregate's rule for that kind has no `if (kind == ...)` left; a `switch` over the sealed type compiles without `default`.
3. A test fails when `Wide` gains a component or a constructor (the freeze), and passes after the last kind is converted and `Wide` is removed.
4. Existing behaviour of a converted kind is unchanged: the HTTP-level acceptance tests of that kind pass without edit (no assertion loosened). Acceptance code that calls the retired constructors (S3) or `Permission` (P3) is changed only by the test session on its own card; the implementer keeps the old forms until that card has merged.
5. A kind enum with a `KIND_FIELDS` entry in the frontend is generated from the schema after S3: no hand-written field list remains for personnel commands.
6. P1: a permission declared in a module appears in the collected catalogue with its grant mode and deprecated mark; a duplicate code across modules, a code not in the format of G43, a granted code absent from the catalogue and a permission code named in identity (role grants, sensitive set, administrator mode) absent from the catalogue each fail at start or in a guard (one test per failure); the catalogue never drops a code (a deprecated one stays). How G43, a plain unit test, reads the contributions (class scan of the declaring interfaces or a Spring context) is decided in P1.
7. P2: a new permission is declared by editing one module file (plus a role grant when one is wanted, and the ADR-0032 snapshot once that file exists); no `*Permissions` copy is created; a guard fails when a module declares a code that another module's declaration also declares.
8. P3: no kernel port takes `String` for a permission or role code; `grep "holds(String"` returns nothing.


## 8. Out of scope

Code and tables; `AuditSubjectKind` and the 2- to 7-kind enums other than `PayrollRuleRowKind` (a later candidate); splitting `WorkforceError` (needs a CS-7.5 change to G48 and CS-2.24; the user's); moving role grants into modules (touches ADR-0034 and is a security decision); the frontend page rewrite; a general plugin mechanism for modules.

## 9. Questions

None for the business. For the reviewer and the user at adoption: (1) whether the HTTP edge must be `oneOf` (S0 decides by evidence) or the flat record at the edge is acceptable; (2) whether role grants stay central (this draft: yes); (3) the acceptance-test changes of S3 and P3 on the test session's cards.

## 10. Unverified

1. The schema of `personnel_commands` and `org_structure_commands` was not re-read; the claim "no table change" rests on the mapping staying the same wide row.
2. Whether `PayrollRuleRowKind` and `ChangeKind` carry kind-dependent optional fields.
3. Answered by the reviewer from `ModuleDependencyTest`: collecting a `List<kernel interface>` needs no edge; typed references from identity to other modules would (see section 5). The collection mechanism itself is proven in P1.
4. The commit counts follow `git log` simplification on origin/main; a different window changes the numbers, not the order.
