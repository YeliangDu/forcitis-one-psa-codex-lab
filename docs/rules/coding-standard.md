# Coding standard details — FORCITIS one

The core page is `docs/rules/coding-core.md`; look rules up in this file by id (CS-x.y).

Authority: user request of 2026-10-15 (intent `2026-10-15-014844-one-coordinator-…`): A merge into this file as the single source, B add a language-level standard, C pre-delivery self-check commands, D work-card template:「A–D 全做」.

## 0. Scope and precedence

**Scope**: all new code in the FORCITIS one repo: backend (Java / Spring Boot / Spring Modulith), database migrations and SQL, frontend (React + TypeScript; PC `apps/web`, H5 `apps/h5` (Taro)), tests, CI and delivery scripts. It binds people and agents (Claude sessions, subagents) alike.

**Division of the standards** (three standards plus process and guidance):

| Document | Covers | Does not cover |
|---|---|---|
| This file `docs/rules/coding-standard.md` | **How to write code**: general practice, language-level rules, tests, logging and secrets, pre-delivery self-check. Code rules of the former METHODS M5 and M6 are merged into the rows (source column says "former METHODS M5" etc.); entry point is the core page `docs/rules/coding-core.md` | Naming and DDL details, session process |
| `docs/rules/naming-standard.md` (short: NAMING_STANDARD) | **What to call things, where to put them**: word source, class-name suffixes, package structure, column-name class words, constraint and index names, DDL design principles (§2.4a), guards G1–G48 / F1–F14 / D1–D3 | Style and process |
| `docs/rules/data-design-checklist.md` (short: DATA_DESIGN_CHECKLIST) | **How to design data**: DDD and data-architecture checklist §14, §14.1, migration-in §14.2, integration §14.3 | Concrete coding style |
| `CLAUDE.md`, `docs/rules/rules.md` (process), `docs/rules/playbooks/` (steps per card type) | Hardest-rule summary and pointers, session and delivery process; review follows `scripts/review/PROMPT.md` | Details (this file and the two above rule) |

**Precedence** (high to low): the user's own words (`docs/decisions/`) > adopted ADRs > specs, rule cards, `contract.md` > this file, `docs/rules/naming-standard.md`, `docs/rules/data-design-checklist.md` (each within its scope) > existing code. Existing code is evidence, not precedent: code that disagrees with this file is a defect (handle it by a defect card or refactor batch per §7.3, not as a drive-by change).

**How this file cites original rules**: every rule names its source. Where the source is one of the other two documents, this file only summarizes and **the source's meaning governs**; when this file's wording differs from the source, follow the source and correct this file. Where the source says "former METHODS M5" etc., the full rule text is in that row (METHODS.md is withdrawn). New coding rules (those marked "new" in §2–§6) have this file as source and take effect after user approval (see §10 open questions).

**Check column** (format checked by `scripts/guards/check_rules_enforced.py`): parts separated by ";", notes in (parentheses):
- `Guard: <enforcers>`: guard ids of NAMING_STANDARD §4 or `backticked` tests, scripts, ESLint rules, `pnpm` scripts or CI jobs that exist and run automatically;
- `planned: <ids>`: a guard designed in §8 or NAMING_STANDARD §4 planned guards; until implemented, review covers it;
- `manual: review`: first-round review checks it item by item per `scripts/review/PROMPT.md`.

## 1. General

| # | Rule | Source | Check |
|---|---|---|---|
| CS-1.1 | **Write no code comment if you can avoid it**: default is none; write one line only where the "why" cannot be seen (counter-intuitive constraint, standard clause, external quirk, performance or concurrency trade-off, unit/precision/time zone, evidence source of replicated legacy behavior (aggregate conclusion and evidence ticket only, no detail rows)). Do not write restatement, history, author/date, commented-out code, TODO without a number, or Javadoc on self-evident parameters; a public contract documents only what the signature cannot express. A comment that disagrees with the code is a must-fix correctness defect; a superfluous comment is a suggestion (grades in `scripts/review/PROMPT.md`) | This file (former METHODS M5) | manual: review |
| CS-1.2 | **Legacy code is evidence only, never copied**: the legacy repos (psa, A8, U8C and their tables) are used only to extract rules, write file:line references and decide equivalence; new code is written only from specs, rule cards, ADRs and `docs/glossary/terms.yaml`, without copying legacy snippets, class structure, table structure, interface paths or names (including `psa_`, `system_`, `bpm_` table names, `/admin-api/`, yudao package names and base classes, pinyin abbreviations); equivalence is shown by tests named by RULE-id. Exceptions only for approved anti-corruption layers, after review | This file (former METHODS M5) | Guard: `LegacyShapeTest`, G40; manual: review (specifically for "legacy shape") |
| CS-1.3 | **Domain modules contain no external or legacy system names**; external systems appear only inside adapters in `platform.integration` | CLAUDE.md; DATA_DESIGN_CHECKLIST §14 item 8 | Guard: `LegacyShapeTest`, `ExternalConnectionIsolationTest`, G1 |
| CS-1.4 | **Look up new identifiers, UI text and table/column names in `docs/glossary/terms.yaml` first**; the glossary governs wording: if not found, register a proposed term on the spot, with usage scope (domain, kernel, acl) and bc; kind takes only DDD tactical categories; aggregate roots and domain events both need entries; event = aggregate name + past-tense business verb, command = imperative verb; within one bc check both Chinese and English names for duplicates; the same name in different bcs is registered separately (id gets a `<bc>__` prefix) and listed in each other's homonyms; error-code Chinese names are globally unique. Business wording follows what the user or finance confirmed; no unconfirmed accounting-type proposal is left at a phase exit; for a rename, the rename PR merges first and forbidden merges after; forbidding first requires an allow-list with an expiry date and an owning session. Dialects and homonyms found in documents are logged as GitHub Issues, not ruled on by oneself | This file (former METHODS M5, M6); ADR-0018 | Guard: `GlossaryTest` (its checks G37–G42: G40 aggregate roots, G41 domain events, looked up in the glossary by bc plus class name, G42 generic technical class names; the existing stock is in `backend/glossary/code-term-baseline.txt`, may only shrink), `ErrorCodeGlossaryTest`, G48 (R-5 Chinese-name uniqueness: error codes globally unique, others unique within one bc), `scripts/guards/term_rules.py` (term-registration rules, B1), `pnpm check:copy` (frontend) |
| CS-1.5 | **Naming and location follow `docs/rules/naming-standard.md`**; no merge while a guard fails | CLAUDE.md; NAMING_STANDARD §4 | Guard: G1–G33, G37–G48, D1–D3, F1–F14; planned: G34–G36 |
| CS-1.6 | **Replicate legacy behavior on evidence from production data or logs (read-only), not on guesses** | This file (former METHODS M5) | manual: review |
| CS-1.7 | **Missing or invalid input fails and alerts, never skipped silently** (except where a rule card explicitly allows) | This file (former METHODS M5); DATA_DESIGN_CHECKLIST §14 item 5, G-T2 | Guard: `CurrentUserGuardTest` (legal entity must not be hard-coded or defaulted); manual: review (the rest) |
| CS-1.8 | **Implement only what the current phase needs** (governs scope; CS-1.9 governs quality) | This file (former METHODS M5) | manual: review |
| CS-1.9 | **What can be done right at the start is not left for a later phase**; deferring requires writing "why doing it later is worth more" | This file (former METHODS M5) | manual: review |
| CS-1.10 | **Contract first**: when an interface path, required field or table name changes, update OpenAPI and the architecture document in the same commit | This file (former METHODS M5) | Guard: `api-contract` (export assertion), `pnpm verify` (frontend); manual: review (PR template); planned: G-C1 (cross-version compatibility; with the snapshot moved out the checked-in contract is no longer compared byte for byte, see `frontend/packages/api/README.md`) |
| CS-1.11 | **Strong types**: typed IDs, value objects, state enums, sealed result types; `Object` / `Map<String, Object>` forbidden in contracts; `any` forbidden in the frontend | `docs/architecture/guiding-principles.md` §10 | Guard: G16, G17, G21–G24, G31, G32, F7 |
| CS-1.12 | **Deep modules, narrow interfaces**: a module exposes only `api` and `events`; total `api` methods ≤ 12; a frontend module exports ≤ 8 through `index.ts` | NAMING_STANDARD §0 | Guard: G1–G4, G30, F1–F4, F12, F13 |
| CS-1.13 | **Generated code lives only in `generated/` and is never hand-edited**; regenerate after changing the generator or source contract | NAMING_STANDARD §5 | Guard: `pnpm check:generated` |
| CS-1.14 | **System names**: write FORCITIS one for the new system and 「老系统」 for the legacy system (English: the legacy system); do not use old abbreviations for a system; legacy paths, table names, interfaces, code identifiers, repo names, session names and the user's own words stay as they are | CLAUDE.md (user, 2026-10-14) | manual: review |
| CS-1.15 | One top-level type per file; a file over 1000 lines must be fixed (hard limit, consistent with `scripts/review/PROMPT.md`); aim for ≤ 400 lines per file and ≤ 60 lines per method, exceeding that is a review suggestion | NAMING_STANDARD §5 | Guard: `scripts/guards/file_lines.py` (1000 lines); manual: review (one top-level type; ≤ 400 / ≤ 60 advice) |

## 2. Java / Spring (DDD in practice)

### 2.1 Structure and boundaries (summary)

| # | Rule | Source | Check |
|---|---|---|---|
| CS-2.1 | Package structure `com.forcitis.one.<subdomain>.<BC>.{api,events,domain,application,infrastructure.persistence,web}`; class-name suffixes only as written in the NAMING_STANDARD §2.2 table | NAMING_STANDARD §2.2, §3 | Guard: G6–G15, G26 |
| CS-2.2 | **One class per use case**, verb-object name, single public method `execute` | NAMING_STANDARD §2.2, §5 | Guard: G27; manual: review (the entry name `execute`) |
| CS-2.3 | **Transaction boundary only in application**; **an application service writes the repository of only one aggregate**; exceptions are registered in `backend/architecture/multi-aggregate-exceptions.txt` with a reason | NAMING_STANDARD G11; DATA_DESIGN_CHECKLIST §14 item 1, G-T1 | Guard: G11, `SingleAggregateGuardTest` |
| CS-2.4 | **State machine inside the aggregate**: state changes only through domain methods, an illegal transition throws a domain exception; application / infrastructure / SQL never write state directly | DATA_DESIGN_CHECKLIST §14 item 2 | Guard: `StateWriteGuardTest`, `RepositoryWriteGuardTest`, G16 |
| CS-2.5 | **Events are produced by the aggregate**: immutable record, past tense, not ending in Event / Request / Command, payload only IDs and value objects, registered in the event catalog; the business date on an event comes from a parameter or aggregate state | DATA_DESIGN_CHECKLIST §14 item 3; G5, G29 | Guard: G5, G29, `EventBusinessDateGuardTest` |
| CS-2.6 | **Concurrency**: a mutable aggregate carries `version` (optimistic lock) or uses an update with a precondition on the prior state | DATA_DESIGN_CHECKLIST §14 item 4 | Guard: `RepositoryWriteGuardTest` (an unconditional write to a version table must be registered) |
| CS-2.7 | **Aggregates reference each other by typed ID**, never holding the other's object; no bare `String` / `long` as a cross-module identifier; business documents across modules use `kernel.DocumentReference` | DATA_DESIGN_CHECKLIST §14 item 6 | Guard: G31, `DocumentReferenceGuardTest` |
| CS-2.8 | **Read models separate from commands**: query projections are read through a `*Queries` port, not through a repository that loads and saves aggregates | DATA_DESIGN_CHECKLIST §14 item 9 | Guard: `ReadModelGuardTest` |
| CS-2.9 | **Authorization**: every interface method has `@PreAuthorize` or `@PublicEndpoint(reason)`; request records carry no caller-identity field; only identity configures Spring Security | NAMING_STANDARD G28, G33 | Guard: G28, G33 |
| CS-2.10 | No field injection, `System.out`, JUL, `java.util.Date`, `@PermitAll`; no `BigDecimal.equals` and no `new BigDecimal(double)`; no `Fix*` / `Demo*` / `Mock*` / `Test*` / `Sample*` in main code | NAMING_STANDARD G18, G24, G26 | Guard: G18, G24, G26 |
| CS-2.11 | The workflow engine only in `platform.approval.infrastructure`; accounting subjects only in `accounting.ledger`; posting only through `operations.facts` | NAMING_STANDARD G19, G34–G36 | Guard: G19; planned: G34–G36 |
| CS-2.12 | Server-side text shown to people (cards, to-do titles, messages) lives only in `*Texts` classes; after Chinese word segmentation every word must be a registered term or an allowed non-business word | Review M-16 | Guard: `OutwardTextGuardTest` |
| CS-2.13 | web / api responses do not expose `SENSITIVE` / `RESTRICTED` columns directly | NAMING_STANDARD §2.5 | Guard: `SensitiveColumnExposureGuardTest` |

### 2.2 Language-level rules (new, based on existing code style and the R-1a review)

**CS-2.20 Output ID value objects with `value()`** (new; R-1a review (PR #125 body) H-1)
- When a typed ID (`@ValueObject` records such as `UserId`, `OrgUnitId`, `SyncRunId`) is written to an interface response, event payload, log key, cursor, audit detail or SQL parameter, always take `id.value()` (when text is needed, `id.value().toString()` or `String.valueOf(id.value())`); **never call `String.valueOf(id)` / `id.toString()` or string concatenation on the ID object itself**: a record's `toString()` is `OrgUnitId[value=…]`, and changing the implementation (long → UUID) would silently change the external shape.
- A response record's ID field should use the ID's value type directly (the OpenAPI generator declares the format), not convert to a string itself. The contract is whatever springdoc actually outputs; version upgrades go through an ADR; error responses are RFC 9457 problem details (adopted by the user 2026-10-15, ADR-0041, `docs/architecture/frameworks.md` §9 (intent 195827)).
- Current state: `organization/workforce/web/*Response.java` contains patterns such as `String.valueOf(view.orgUnitId())`; check them together under R-1.

Check: planned: G-CS1

**CS-2.21 Do not express business order by ID order** (new; R-1a review (PR #125 body) H-2)
- UUIDv7 roughly increases with time and auto-increment IDs with insertion, but **neither is a business fact**: same-millisecond ids, clock rollback, bulk import and migrated-in data all break it. When order is needed, use an explicit sequence column (`_number` / `_order`, allocated in the same transaction) or "business time + `id`": business time decides the order, `id` is only a stable tie-breaker (the same for the sort key of cursor pagination, NAMING_STANDARD §2.4a item 2).
- Forbidden: `ORDER BY id` to express "earlier/later", `max(id)` as "latest", comparing two IDs to decide which came first, tests whose assertions depend on ID order.

Check: planned: G-CS2; manual: review

**CS-2.22 Value objects, records and immutability** (summary + new)
- A value object is a `@ValueObject` record; validation sits in the compact constructor, an illegal value throws `IllegalArgumentException` (it denotes only a programming error; external input is validated first at the web boundary, see CS-2.24) (§14 item 7; Guard: G21).
- A record holds no mutable state: collection components are copied in the constructor with `List.copyOf` / `Set.copyOf` / `Map.copyOf`; no arrays and no `Date` (Guard: G22; the collection copy is new, guard planned: G-CS3).
- Aggregates and entities are not records: they have identity and a lifecycle, and their state changes only through domain methods (CS-2.4); fields are `private`, no setters.
- `api` / `events` hold only interfaces, records, enums and typed IDs (Guard: G2, G5, G32).
- Constants are `static final` and immutable; no mutable `public static` fields (NAMING_STANDARD §1, legacy-code problems).

Check: Guard: G2, G5, G21, G22, G32; planned: G-CS3 (collection copy); manual: review (the rest)

**CS-2.23 Null handling** (new, boundary set from existing style)
- Non-null by default: parameters, return values and record components in domain, application and api are never `null`; at construction `Objects.requireNonNull(x, "x")`.
- A single-value query that "may find nothing" returns `Optional<T>` (existing use cases such as `Optional<OrgUnitDetailView> execute(...)`); `Optional` is **return-value only**, never a field, parameter, record component (response records excepted, see below) or collection element.
- Collections return an empty collection, never `null`; paging returns an empty page.
- A response-record component that genuinely needs "no value" is marked `@Nullable` (the generated contract then declares nullable); use `org.jspecify.annotations.Nullable` throughout; Spring's `org.springframework.lang` nullness annotations are banned (`LegacyApiGuardTest` LA-9; Spring 7 itself has moved to JSpecify).

Check: Guard: `NullableResponseGuardTest` (boxed-type components must be `@Nullable`), `LegacyApiGuardTest` (LA-9); planned: G-CS3 (the rest)

**CS-2.24 Exception classification** (summary + new)
- Business rejection: throw `kernel.DomainException` (or a subclass) carrying this module's error-code enum (implements `kernel.ErrorCode`, format `{SUBDOMAIN}_{BC}_{REASON}`) and an `ErrorKind`; `platform.web.GlobalExceptionHandler` converts uniformly to ProblemDetail. Chain: domain exception → ErrorCode → ProblemDetail (§14 item 10; Guard: G48, `ErrorCodeGlossaryTest`).
- **Common error codes are defined only in `kernel`** (illegal state transition, modified concurrently by someone else, reason required, past the withdrawal cut-off, not logged in, etc.); a module defines only errors specific to it and must not duplicate common ones or each other (NAMING_STANDARD §2.3, G48; user 2026-10-15「通用错误码只在公共基础定义」; Guard: G48, R-5).
- The exception message is written for logs; what the caller sees is only the error code, the kind, and extension fields the exception declares displayable (`DomainException` convention). No sensitive value in a message (CS-6.2).
- Input-validation failure at the web boundary (missing field, bad format, value out of range) uses `kernel.InvalidRequestException` (400) and is stopped before reaching a value object; the `IllegalArgumentException` thrown by a value-object constructor denotes only a programming error (the caller did not validate first) and is not an outward business rejection.
- Programming errors and invariant violations use `IllegalArgumentException` / `IllegalStateException` / `NullPointerException`, and these are **not** used for business rejection; an unavailable external system uses `kernel.ExternalSystemUnavailableException`.
- Never swallow an exception: after `catch`, either convert to one of the types above and rethrow, or log and degrade as the rule card explicitly says; an empty `catch` block is forbidden (new).

Check: Guard: G48, `ErrorCodeGlossaryTest`; planned: G-CS4 (no new `RuntimeException`, empty `catch`); manual: review (the rest)

**CS-2.25 Transaction boundary** (summary): `@Transactional` only on application use cases; one use case, one aggregate (CS-2.3); inside a transaction, no external-system call, no HTTP, no batch computation; external calls go through the outbox and reliable delivery (`docs/architecture/guiding-principles.md` §7); business numbers are allocated inside the use case's own transaction (NAMING_STANDARD §3).

Check: Guard: G11, `SingleAggregateGuardTest`; manual: review (external calls inside a transaction)

**CS-2.26 Time only through an injected `Clock`** (summary): business code does not call `now()`, `Clock.system*` or `System.currentTimeMillis`; domain does not hold a `Clock`, application passes in the instant and business day; the business day follows `kernel.BusinessZone`; SQL strings and migrations do not read the database clock (`now()`, `current_timestamp`, clock-type `DEFAULT`), the application writes the instant explicitly (NAMING_STANDARD G25, §2.4a item 3; R-4). Exception: the Spring Session framework table (session table) expiry comparison goes through `identity.infrastructure.persistence.SessionStoreClock`, because the framework has no clock injection point; it is withdrawn when C1 replaces it with a self-written session repository; guard `TimeSourceGuardTest`.

Check: Guard: `TimeSourceGuardTest`, `EventBusinessDateGuardTest` (migration-file scanning comes with R-4)

**CS-2.27 Money only through `Money`** (principle; K-1 money kernel is in progress, implementation follows K-1): an amount is always "number + currency", and outside kernel no bare `BigDecimal` amount field appears; no `double` / `float` for amounts or rates; rounding policy is concentrated in the money kernel in one place, domain code does not itself `setScale` / `divide` / specify `RoundingMode` (K-1 guard); splitting and allocation are conserving (parts sum to the original) and deterministic; cross-currency arithmetic is rejected; on the interface an amount is a decimal string plus currency (G-M5). Quantities likewise use value objects such as `Hours` (G23).

Check: Guard: G17, G23, G24 (the new K-1 guards ship with K-1); manual: review (the rest)

**CS-2.28 Dependency injection and visibility** (summary + new)
- Constructor injection only (no `@Autowired` on a single constructor), dependency fields `private final` (Guard: G18 forbids field injection).
- web and infrastructure classes are package-private (Guard: G6); other classes are also package-private by default, and only those that really must be used from another package are `public` (`api` / `events` types, use-case classes, domain types used by application) (new; Review).
- Configuration is injected through records using `@ConfigurationProperties("forcitis.<subdomain>.<bc>…")`, not by scattered reads of `Environment` in code; the config-key prefix is checked by G46.
- No static mutable singletons, and no reading of configuration or time in static initialization.

Check: Guard: G6, G18, G46; manual: review (default package-private, no static mutable state)

**CS-2.29 The domain layer does not reference external-system types** (new): external-origin types (`Source*` in `platform.integration.api`, e.g. `SourceUnitId`, `SourcePersonId`, `SourceOrgSnapshot`) appear only in `platform.integration` and at the consuming module's **anti-corruption translation point**; a business module's aggregates, entities, value objects, `api` and `events` neither hold nor expose them, and after translation use only their own types and enums (DATA_DESIGN_CHECKLIST §14.1 D16 "external IDs serve only as source-reference columns … used only in the sync anti-corruption layer"; §14 item 8). Current state: `SourceTerms`, `SnapshotReconciler` and `EmployeeChecks` in `organization/workforce/domain/` reference `Source*`; the user must decide where the translation point goes (§10 Q-4).

Check: planned: G-CS5

## 3. SQL and migrations

| # | Rule | Source | Check |
|---|---|---|---|
| CS-3.1 | Naming of schema, table, column, constraint, index and Flyway files | NAMING_STANDARD §2.3, §2.4 | Guard: `DdlNamingTest`, `NamingSurfaceTest` (G44), `GlossaryTest` (G37–G39) |
| CS-3.2 | Ten DDL design items: business unique constraints, foreign keys and query-path indexes, nulls and defaults (technical defaults only), delete semantics, code format and text length, `text + CHECK` enums, `jsonb` boundary, partition planning for large tables, migration safety after go-live, high-frequency-update rows | NAMING_STANDARD §2.4a; DATA_DESIGN_CHECKLIST §14.1 D18 | Guard: `DdlNamingTest`, `DataArchitectureTest` (per item); manual: review (the rest) |
| CS-3.3 | **Data design follows all items of DATA_DESIGN_CHECKLIST §14.1** | [DATA_DESIGN_CHECKLIST §14.1](data-design-checklist.md#141-data-architecture-checklist), all items | Guard: `DataArchitectureTest`, `DdlNamingTest`, `PrimaryKeyTypeGuardTest`; manual: review (the rest) |
| CS-3.4 | **Own tables use a UUIDv7 primary key**, generated by `kernel.IdGenerator`; external IDs serve only as source-reference columns; external codes are mapped to own enums in the anti-corruption layer | DATA_DESIGN_CHECKLIST §14.1 D16 | Guard: `PrimaryKeyTypeGuardTest` |
| CS-3.5 | **Migration baseline** (replaces every "Flyway append-only" while the baseline is open): until the first production deployment the baseline is open (marker `backend/architecture/migration-baseline-open`), dev may be discarded, and having run in dev does not count as applied; one baseline per schema, table-structure changes edit the baseline directly, and every table-changing PR attaches an "expected table-structure diff" (`pg_dump --schema-only` of two empty databases compared, checked by opus). Before the first production deployment a separate PR removes the marker (a migration change smuggled in fails CI); after that, merged migrations are append-only. `scripts/release/deploy_gate.py` warns for dev and blocks for prod; while open, after a baseline change the dev database is reset by the rebuild playbook | ADR-0038 (former METHODS M3) | Guard: `scripts/guards/migrations_immutable.py`; manual: review (diff) |
| CS-3.6 | SQL only in `Jdbc*Repository` in `infrastructure.persistence`, using `JdbcClient` and typed hand-written mapping, no reflective ORM; access only this module's schema; persistence classes and the tables they access are registered in `architecture/data-access-registry.yaml` | NAMING_STANDARD G10, §3; `docs/architecture/guiding-principles.md` §10; `docs/ops/security-invariants.md` I-10 | Guard: G10, `DataArchitectureTest` (registry vs SQL), `WorkforceSchemaGuardTest` |
| CS-3.7 | **Always bind parameters** (`:name` parameters), never splice values into SQL text; dynamic sort columns and dynamic table names may come only from a whitelist enum in code (new) | New (prevents SEC-class defects) | planned: G-CS6 |
| CS-3.8 | SQL does not read the database clock; DDL does not write clock defaults | R-4; NAMING_STANDARD §2.4a item 3 | Guard: `TimeSourceGuardTest` (SQL strings; migration scan comes with R-4) |
| CS-3.9 | **Posted facts are never changed or deleted**, audit and version tables are append-only; correction only by reversal: a reverse fact with a new posting sequence number, the original row kept and linked both ways, redone on the correct basis; a correction after close lands in the open period and is marked with the original period; new config versions, rule corrections and data migrations never write back; a correction after close or a period reopening needs finance approval | ADR-0016 item 2 (former METHODS M3) | Guard: `DataArchitectureTest` (scorecard D1: append-only triggers on log and version tables; DATA_DESIGN_CHECKLIST D8); planned: G34 |
| CS-3.10 | List queries use only index-covered cursor pagination (`cursor` + `limit`, default 50, max 200), no in-memory paging, no full-table scan; the sort key falls back to `id` (CS-2.21) | `docs/architecture/guiding-principles.md` §7; NAMING_STANDARD §2.4a item 2 | manual: review (EXPLAIN spot check) |
| CS-3.11 | The database-creation scripts (C-3, C-9) agree with the reference DDL and the schema list of the migration directory | `db_scripts_aligned.py` description | Guard: `scripts/guards/db_scripts_aligned.py` |
| CS-3.12 | No analytics on transaction tables; summaries and trends go through `reporting` | DATA_DESIGN_CHECKLIST §14.1 D9; ADR-0025 | manual: review |
| CS-3.13 | **SQL that sorts by Chinese writes `COLLATE "zh-x-icu"` explicitly**: the database default collation is the built-in C.UTF-8 (Chinese sorts by code point, the same in the test and dev databases, DDL-11); an `ORDER BY` that needs pinyin order carries it itself; if the sort key of keyset pagination carries that `COLLATE`, the cursor comparison and the index use the same one | `docs/architecture/cross-cutting-decisions.md` XD-03; `docs/architecture/cross-cutting-matrix.md` CC-I01, G-X2 | manual: review (no reliable static check: SQL text does not say which sort key holds Chinese; K-3a ruling item 5) |

## 4. Frontend (React + TypeScript; H5 is Taro)

| # | Rule | Source | Check |
|---|---|---|---|
| CS-4.1 | **Strict types**: `strict`, `noUncheckedIndexedAccess`, `exactOptionalPropertyTypes`, `verbatimModuleSyntax`; no `any`; no non-null assertion `!` to bypass nullability (new) | `frontend/tsconfig.base.json`; NAMING_STANDARD F7; `docs/architecture/guiding-principles.md` §10 | Guard: `pnpm typecheck`, F7; planned: G-CS7 (non-null assertion) |
| CS-4.2 | **Interface calls only through the generated client**: no axios, `fetch`, `XMLHttpRequest`, hand-written `/api` URLs; only `modules/<m>/api` may import `@forcitis/api`; types are generated from OpenAPI, interface types are not hand-written | NAMING_STANDARD §2.3, F5, F10 | Guard: F5, F10, `pnpm check:generated` |
| CS-4.3 | Module boundaries: `app → modules(index) → shared` one way; across modules only through `modules/<m>/index.ts`; no `export *`; `model/` and `shared/lib` are pure logic | F1–F4, F9, F12, F13 | Guard: F1–F4, F9, F12, F13, `pnpm check:structure` |
| CS-4.4 | Naming: ASCII identifiers, files kebab-case, components PascalCase, hooks `useXxx` and in `hooks/` | F6, F8 | Guard: F6, F8, `pnpm check:structure` |
| CS-4.5 | **UI text comes from the glossary**: business nouns use `zh` from `docs/glossary/terms.yaml`, one concept has one name across the whole site; non-business words are registered in `frontend/scripts/copy-allow-list.yaml` | NAMING_STANDARD §7 item 16; CS-1.4 | Guard: `pnpm check:copy` |
| CS-4.6 | **Error messages**: only through `describeFailure` of `@forcitis/core`; every `ErrorCode` in `error-messages.ts` decides its text or is explicitly `null` (use the generic text for the HTTP status); pages never display the server `detail`, an exception message or English directly; never swallow errors, a failure must show the user an actionable message | `packages/core/src/failure.ts`, `error-messages.ts` (existing practice); CS-1.7 | Guard: `pnpm typecheck` (`Record<ErrorCode, …>` exhaustive); planned: G-CS7 (pages reading message directly) |
| CS-4.7 | Design system: business modules do not import antd or Taro primitives directly; no inline styles or hard-coded colors; tokens are read only in `packages/design-system` | F14; ADR-0013 | Guard: F14, `frontend/stylelint.config.js` |
| CS-4.8 | Security: no `dangerouslySetInnerHTML`; external links go through `safe-url` | F11; SEC-008 | Guard: F11; manual: review (`safe-url`) |
| CS-4.9 | No `console.log`, use the shared logger | `frontend/eslint.config.js` | Guard: `no-restricted-properties` |
| CS-4.10 | Date-time and business day only through `format-date-time` and `business-day` of `@forcitis/core`; money formatting uses the shared function delivered by K-1 (G-M6) | Existing practice | manual: review (G-M6 comes with K-1) |
| CS-4.11 | H5 and PC follow the same rules | NAMING_STANDARD §7 item 17 | Guard: F14 (H5 already wired); manual: review (the rest) |
| CS-4.12 | **`localeCompare` must carry `'zh'`, `Intl.DateTimeFormat` must carry `timeZone`**: Chinese sorts by pinyin, and UI time does not vary with the browser's locale and time zone | `docs/architecture/cross-cutting-matrix.md` G-X2, CC-T02, CC-I01 | Guard: `no-restricted-syntax`, `pnpm check:lint-config` (self-test of the lint config, `frontend/scripts/check-lint-config.mjs`) |

## 5. Testing

| # | Rule | Source | Check |
|---|---|---|---|
| CS-5.1 | **Module-level integration tests first**: go HTTP → authorization → use case → domain → real PostgreSQL (Testcontainers, image as CI says, currently `postgres:18`); do not mock your own modules, isolate external systems with WireMock or a stand-in; unit tests only fill in boundaries (value objects, computations, decision tables) | `docs/architecture/guiding-principles.md` §9 | manual: review |
| CS-5.2 | **Test names cite a basis number**: `@DisplayName` begins with a rule / difference / ADR / review / intent number (existing practice, e.g. `@DisplayName("RULE-356 …")`, `"ADR-0027 …"`); every `contract.md` item and every ADR clause delivered in this phase maps to at least one test | NAMING_STANDARD §5; TEAM-69; G-T4 | manual: review (the matching guard G-T4 is planned) |
| CS-5.3 | **Acceptance tests separate from implementation**: the test agent writes and commits the acceptance tests first, the implementation agent only turns them green and must not modify them; directory convention and commit trailer are in the source | `roles/design.md` duty "independent acceptance tests", `backend/src/test/java/com/forcitis/one/acceptance/README.md` | Guard: `scripts/guards/acceptance_guard.py`, `acceptance_rulings.py` (a ruling footnote must correspond to a registered ruling), `acceptance_reports.py` |
| CS-5.4 | **Never loosen or delete an assertion to make a test pass**; if one really must change, list the reason separately in the report and PR | This file (former METHODS M5) | manual: review (PR template) |
| CS-5.5 | **A test asserts only this module's effects** | This file (former METHODS M5); DATA_DESIGN_CHECKLIST §14 G-T3 | Guard: `TestIsolationGuardTest` |
| CS-5.6 | **Deterministic waiting, no broad `never()`**: async uses a deterministic wait with an upper bound; also: tests do not use `Thread.sleep` or other time-based waits (new, 6 existing places) | This file (former METHODS M5); the `Thread.sleep` sentence is new in this file | Guard: `LegacyApiGuardTest#NO_THREAD_SLEEP`; planned: G-CS8 (broad `never()`) |
| CS-5.7 | **Tests contain no secret literals** | This file (former METHODS M5) | Guard: `gitleaks` (pre-commit, CI) |
| CS-5.8 | Tests use a fixed `Clock` and a fixed `IdGenerator`, and do not depend on runtime instant or ID order (CS-2.21, CS-2.26) | New (derived from CS-2.21, CS-2.26) | manual: review |
| CS-5.9 | **A new guard carries negative samples**: every guard has a test that "injects an error and is blocked", and its failure message says "where to put it / what to name it"; every line of an exception list carries a reason, and a new exception needs review | NAMING_STANDARD §4 "negative verification", §5; the `injection:` tests of existing guards | manual: review |
| CS-5.10 | **Review produces failing tests**: for Blocker / High / Medium give a reproducible failing test where possible, its name citing the review number; a fix counts when the test turns green | `docs/rules/rules.md` ⑤ | manual: review |
| CS-5.11 | A fix loop runs only the affected modules; before delivery and at the integration commit run the affected tests, `scripts/ci/light.sh` and all guards (parallel subagents start from the same baseline, the integration commit reruns all guards, see CS-7.6) | This file (former METHODS M5) | Guard: `scripts/ci/light.sh` (CI runs everything) |

## 6. Logging, security and secrets

| # | Rule | Source | Check |
|---|---|---|---|
| CS-6.1 | Logging uses SLF4J and outputs ECS structured JSON (`logging.structured.format.console=ecs`), context carries `correlationId` and `userId`; structured log keys are camelCase and business keys come from the glossary; no `System.out`, JUL | `backend/src/main/resources/application.properties`; NAMING_STANDARD G18, §7 item 20 | Guard: G18; manual: review (key names) |
| CS-6.2 | `RESTRICTED` (passwords, keys, session content) goes into no log, no exception message, and never leaves the database; for `SENSITIVE` (personal information, salary and daily rate, bank card) logs record only the ID, never plaintext | NAMING_STANDARD §2.5 | manual: review; planned: G-CS9 |
| CS-6.3 | **Tests contain no secret literals** (the same rule as CS-5.7, indexed here from the secrets viewpoint) | This file (former METHODS M5) | Guard: `gitleaks` (pre-commit, CI) |
| CS-6.4 | **Secrets never enter session records** | CLAUDE.md bottom lines | manual: review |
| CS-6.5 | Raw credentials live only in `~/.modernize/psa/` (mode 600) or KMS, never in any shareable file | CLAUDE.md bottom lines | Guard: `gitleaks`; manual: review |
| CS-6.6 | Identity, authorization and single-person-mode defenses follow I-1…I-13 of `docs/ops/security-invariants.md`; such changes add a final opus review (`docs/rules/rules.md` ⑤) | `docs/ops/security-invariants.md` | Guard: `SecurityInvariantsTest` (with the test each invariant names); manual: review (final opus review) |
| CS-6.7 | Production configuration contains no test values, fixed verification codes or mock values | `docs/ops/security-invariants.md` I-4 | Guard: `ProductionConfigSanityTest` (per `docs/ops/security-invariants.md`) |
| CS-6.8 | **Ask the user before creating an account, starting a container, or making an external call** (except a local temporary Testcontainers test container) | This file (former METHODS M5); TEAM-92 | manual: review |
| CS-6.9 | **Production evidence-gathering is not requested each time (two bottom lines)** | CLAUDE.md bottom lines, ADR-0037 item 7 | manual: review |
| CS-6.10 | **Security-gate installation**: running `pre-commit install` once in the main checkout applies to all workspaces (git hooks are shared by all workspaces; gitleaks and the main-checkout guard both hang on pre-commit, and without it nothing is checked); a commit in a plain clone is refused by the main-checkout guard (git-dir equal to common-dir counts as the main checkout), the way out is `git worktree add <dir> -b <branch>` in that clone; the gitleaks version used to scan full history before a report is the `rev` in `.pre-commit-config.yaml`, the same version as the gitleaks job in ci.yml | This file (former METHODS M7) | Guard: `gitleaks` (pre-commit, CI) |

## 7. Pre-delivery self-check and PR

| # | Rule | Source | Check |
|---|---|---|---|
| CS-7.1 | **Before delivery run the affected tests plus `scripts/ci/light.sh`**, open the PR only with no FAIL; the PR states the last line of light.sh and any FAIL/SKIP items | CLAUDE.md; `docs/rules/rules.md` ⑤ | manual: review (PR template) |
| CS-7.2 | Fill every field of the PR template truthfully: changed assertions / tests and reasons, new terms, OpenAPI and architecture-document sync, acceptance-test trailer, last line of light.sh and FAIL/SKIP items | `.github/pull_request_template.md` | manual: review |
| CS-7.3 | **Defect fixes, feature completion (due but not delivered) and structural refactors (including renames) are dispatched separately, never mixed in a batch or a commit.** A defect fix is surgical: one defect card (review number, reproduction steps, expected changed files) maps to one minimal change plus one regression test that fails first; change only the files on the card, report before going beyond, no drive-by refactor, rename or style unification; small PRs, merge once review passes, no accumulating big integration branches; a defect that cannot be fixed locally is marked "structural" with a reason and turned into a refactor | This file (former METHODS M5) | manual: review |
| CS-7.4 | **Record the user's spoken decisions verbatim with the script**: `python3 scripts/session/new_intent.py` (`--session`, `--category`, `--owner`, `--paths`, `--supersedes`: four are required, usage in the script header); the script generates the file name from the instant and session name, never name it by hand; generated output is not committed and not edited directly | `docs/rules/rules.md` ⑧⑨ | manual: review |
| CS-7.5 | **Change-governance self-check** (rule changes fall in three classes; when unsure take the stricter: config < code < ADR): an adopted ADR changes only its status line (`docs/adr/readme-lifecycle.md`), a new ADR carries the required sections; code class (domain rules, calculations, validation, state machines, DDL): first append a draft conclusion at the end of `revisions` in `docs/business/rule-reviews.json` (original fields unchanged, the current conclusion is the last one), then after user confirmation change the tests named by rule ID, and last the implementation; tests for code-class rules are named by rule ID (RULE-NNN); config class (DMN, business numbers, operating basis, policy parameters; the business basis is approved by the user, the accounting basis by finance): register version and effective date in `docs/ops/config-changelog.md`, config versions are append-only and never overwritten, take effect by an effective date aligned with the accounting period, no retroactive effect, no back-dating, no recomputation | This file (former METHODS M3); `docs/rules/playbooks/design.md` step 9 | manual: review |
| CS-7.6 | **Parallel subagents start from the same baseline**, the integration commit reruns all guards | This file (former METHODS M5) | manual: review |
| CS-7.7 | **Review and enqueue**: the author launches a fresh review subagent per `scripts/review/PROMPT.md` (model and scope in that file), security and production changes add a final opus review; must-fix repairs are released by the lead after reading the diff; after review passes the author enqueues with `python3 scripts/merge/mq.py enqueue <PR>`, and does not push after enqueue; no auto-merge | `docs/rules/rules.md` ⑤⑥⑨ | Guard: `mq.py` (merges only a head whose newest review says 通过); manual: review (lead releases must-fix fixes) |
| CS-7.8 | Commit messages are in Chinese, format `type(scope): summary`, scope is the subdomain slug; for the acceptance-test trailer see CS-5.3 | NAMING_STANDARD §7 item 21 | manual: review |
| CS-7.9 | **IntelliJ build**: the build process does not read `backend/.mvn/jvm.config`, and Error Prone fails to load with `IllegalAccessError`; in Settings → Build, Execution, Deployment → Compiler → Shared build process VM options add the same `--add-exports` / `--add-opens` arguments as that file, or build with Maven instead | ADR-0033 guard 14; `backend/.mvn/jvm.config` | manual: review |
| CS-7.10 | **Document references**: drafts under `proposals/` are not authoritative and no script reads them; an ADR cites only the intent number and never copies the user's words | This file (former METHODS M7) | manual: review |

## 8. Planned guard designs (G-CS series; distinct from naming-standard's G-C series (ADR-0035 contract guards))

This section is design only and is not implemented now. Each is implemented in its own PR (structural refactor batch or guard batch) with negative samples (CS-5.9); where violations already exist before landing, register a baseline file in the manner of `ddl-naming-baseline.txt`: a new violation, and a baseline line that is "fixed but not removed", both fail.

| Id | Rule | Implementation points |
|---|---|---|
| G-CS1 | CS-2.20 ID output uses `value()` | ArchUnit: scan `web`, `application`, `infrastructure` for calls to `String.valueOf(Object)`, `Object.toString()`, `StringBuilder.append(Object)` and string concatenation (argument types of `StringConcatFactory` calls); it is a violation when the argument's static type is a record with `@ValueObject` and a single component named `value`. Plus an integration assertion: the OpenAPI export contains no `[value=`-style example, and after event JSON serialization the ID field is a scalar |
| G-CS2 | CS-2.21 No reliance on ID order | Text scan of SQL in `infrastructure.persistence`: it is a violation if the **first** `ORDER BY` key is `id` or `*_id`, or `max(id)` / `min(id)` appears; `id` in a sort key is allowed only after business time or a sequence number. ArchUnit: in domain / application, calling `compareTo` on a typed ID or `UUID`, or putting them in `Comparator.comparing`, is a violation |
| G-CS3 | CS-2.22, CS-2.23 immutability and nulls | ArchUnit: a record component of type `List` / `Set` / `Map` needs a matching `copyOf` call in the compact-constructor bytecode; methods in domain / application / api must not `return null` (bytecode `ACONST_NULL` + `ARETURN`, excluding `@Nullable` returns); `Optional` must not be a field, parameter type or collection element (`web` response records excepted); importing `org.springframework.lang.Nullable` is forbidden (JSpecify throughout, see CS-2.23) |
| G-CS4 | CS-2.24 exceptions | ArchUnit: domain / application must not `new RuntimeException` / `new Exception`; a non-kernel exception thrown in a business module must be a `DomainException` subclass or a JDK programming-error class; source scan for empty `catch` blocks (`catch (...) {}` with no statement in the block) |
| G-CS5 | CS-2.29 domain does not reference external types | ArchUnit: except `platform.integration..` and registered anti-corruption translation packages, no `..domain..`, `..api..` or `..events..` may depend on `platform.integration.api.Source*`; the translation-package list and existing violations are registered in `backend/architecture/source-type-exceptions.txt` (new file, may only shrink) |
| G-CS6 | CS-3.7 SQL parameter binding | Source scan of `Jdbc*Repository`: a string argument passed to `JdbcClient.sql(...)` / `JdbcTemplate` must not be concatenated from non-`static final` variables; only in-code enums or `static final` constants may be concatenated |
| G-CS7 | CS-4.1, CS-4.6 frontend | ESLint: `@typescript-eslint/no-non-null-assertion: error`; `no-restricted-syntax` forbids accessing `.message` / `.detail` in `pages/` and `components/` (`MemberExpression[property.name=/^(message|detail)$/]` whose object comes from a `catch` parameter or `error`), with a hint to use `describeFailure` |
| G-CS8 | CS-5.6 deterministic waiting | Source scan of `src/test/java`: `never()` must be used together with a specific method and argument matchers, and `verifyNoInteractions` / `verifyNoMoreInteractions` need a registered reason (`Thread.sleep` is already caught by `LegacyApiGuardTest`) |
| G-CS9 | CS-6.2 logs contain no sensitive values | ArchUnit: arguments of `Logger` `info/warn/error/debug` calls must not contain a value whose type or field name hits a `SENSITIVE` / `RESTRICTED` column in `data-classification.txt` (snake → camel mapping); plus an integration test: run login and sync flows with a fixed sensitive value and assert the log output does not contain it |

## 9. Conflicts and duplicates

(Removed: history, see git history; C-9, still valid, is merged into CS-2.23, and C-10 is in Q-6.)

## 10. Open questions

| Id | Question | Recommendation |
|---|---|---|
| Q-1 | Whether to adopt the rules marked "new" in this file (the new parts of CS-2.20–CS-2.29, CS-3.7, the non-null assertion in CS-4.1, pages not reading message in CS-4.6, the `Thread.sleep` ban in CS-5.6, CS-5.8, the format in CS-7.8) | Recommend adopting; record an intent after adoption |
| Q-2 | Whether to accept the precedence order in §0 (user's own words > adopted ADR > specs and rule cards > the three standards > existing code) | Recommend accepting |
| Q-4 | `SourceTerms`, `SnapshotReconciler` and `EmployeeChecks` in `organization.workforce.domain` reference `Source*`: move the translation into `application` (or a new anti-corruption translation package), or register them as a sync anti-corruption-layer exception | Recommend moving to a translation class in the application layer so domain sees only its own types; go through a structural refactor batch |
| Q-6 | Coverage threshold: `docs/architecture/guiding-principles.md` §9 says "lines ≥ 80%, branches ≥ 70%", CI `backend_verify.sh` runs `-Djacoco.skip=true`, so nobody checks the threshold: restore it in CI, or state in `docs/architecture/guiding-principles.md` that it is "a review reference, not a threshold" | Pending the user's decision |
| Q-7 | Tech stack to verify (unverified, not a rule): the new package name of Testcontainers 2 `PostgreSQLContainer`; Security 7 dropping `.and()` and `AntPathRequestMatcher`; the waiting library Awaitility (not introduced) | After #810 verifies, goes into table D of the core page |
