# Naming, package structure and architecture guards — FORCITIS one modernisation

Scope: all new code. The user's request: 「可以在现代化过程中规范命名吗？将原代码的命名和编码规范化」; 「命名要符合英文、行业、规范的习惯，agent 开发友好。而且要考虑 archunit 和包结构来保证目录结构不会让 agent 犯错。」; 「一些基本的原则：深模块、窄接口，要给 agent 约束，避免错代码」. The new system is a rebuild: old names and directories are never reused. Three layers: every English name comes from the glossary (§2.1); a fixed package structure decides where code goes (§3); Modulith and ArchUnit fail misplaced code, wrong names and boundary crossings in CI (§4). Related: `docs/adr/ADR-0003.md`, `ADR-0004.md`, `ADR-0005.md`, `ADR-0006.md`. `DATA_DESIGN_CHECKLIST`, `NAMING_STANDARD`, `CODING_STANDARD` are short names of `docs/rules/data-design-checklist.md`, `naming-standard.md`, `coding-standard.md`; tests and ADRs cite them as short name plus section number.

## 0. Basic principles

- **Deep module**: one module = one BC; internals are invisible outside (G1).
- **Narrow interface**: two exits only, `api` (interfaces and records, no internal types) and `events` (records of facts); ≤ 12 api methods per module (G2–G4); beyond that, deepen the module.
- **Constraints for agents**: names located by concept, language and usage (§2.1); code at fixed places (§3); each module has a `package-info.java` (responsibility, owned schema, allowed dependencies).
- **Error-proofing**: wrong code fails at compile time or in CI, not in review.

In the personal Lab, historical commit references may be read only if their objects are already in the Lab. Report missing evidence; do not fetch it from the original repository.

## 1. (removed: history; see `git show 59315c148^:docs/rules/naming-standard.md`)

## 2. Naming rules: English, industry-idiomatic, predictable

### 2.1 Word source

Principles, the three-layer model, composition, lifecycle, renaming and multi-language structure are **ADR-0053 decisions 1, 2, 3, 5, 6**. Word-source entries: `docs/glossary/terms.yaml` (and merge sources), `docs/glossary/bcs.yaml`, the ACL classification data (decision 1.3). Unimplemented schema design (concept / designation fields, N3 seven-decision table, alias mapping, verb and template registers, message catalog, planned-guard summary): `docs/glossary/glossary-schema-design.md`. Field names follow "N3 decisions" (`docs/glossary/arch-naming/n3-decisions.md`) and the "registration plan" (`docs/glossary/arch-naming/naming-atoms-registration-plans.md`), both removed 2026-11-04, read at commit `c0001f8890`. `GlossaryTest`, `scripts/guards/term_rules.py` and `NamingSurfaceTest` still read the current term-table shape; guards for the concept / designation structure, role selection and projection consistency are **planned** (one-quality, before the new structure switches); review checks against the sources meanwhile.

#### 2.1.1 Concept entries and designation entries

Owner: one-glossary. Adopted machine values (N3 decisions; a role not enabled that is missing does not fail):
- `locale`: `zh-CN / en / und` (`und` = undetermined, not a UI language; English is `en`, no regional tag). Pairing: `zh-CN / canonical` (Chinese canonical name), `en / code_name` (internal English code name).
- `role`: `canonical / code_name` in use; `terse / verbose / column_header / documentation / ui_label` reserved, not enabled.
- `usage_status`: `preferred / permitted / forbidden` only. `approval_status`: `proposed / pending_confirmation / approved`.

`forbidden` or `pending_confirmation` may not be referenced; `proposed` builds a warning (decision 5.2). A surface takes the unique `preferred` designation of its language and role; `permitted` is never a fallback.

#### 2.1.2 Usage roles and AN-05

Owner: one-glossary defines roles, one-architecture which role each surface takes. Role-selection guards: planned (before switch).
- Canonical name `zh-CN / canonical`, for now also the Chinese UI text; enable `ui_label` only when a concept needs a UI wording different from it.
- Internal English name `en / code_name`, ASCII (existing ASCII and word-split checks).
- English UI name (AN-05): registered separately from the code name even if the word is the same; no English UI is delivered now, so it need not be completed.
- Abbreviation: boolean `abbreviation` on a designation, not a role; register the full name first; not assumed a permitted alias.
- Old names, dialects, aliases: by `source_type` and the two statuses, not a role; old-name sources `old-code / legacy-code / legacy-table` (existing forbidden-spelling guard).

#### 2.1.3 Source language and current delivery language

Source language `zh-CN`. UI language enabled (AN-04): `zh-CN` only (PC, H5, server-side human text, print, reports, notifications); F19 and designation / message completeness planned. The English code name does not enable an English UI and the UI never falls back to it. British vs American spelling awaits a user ruling (N4 Q13); until then new code follows the N4 candidate.

#### 2.1.4 Projection from designations to naming surfaces

Owner: one-architecture. A projection finds the entry by concept `id`, selects the unique `en / code_name / preferred` name (Chinese: `zh-CN / canonical / preferred`) and checks `approval_status` per §2.1.1; a missing entry or designation, non-unique preferred or unusable status fails (ADR-0053 decisions 1.5, 2.3, 3, 4, 8).

| Naming surface | Format | Guard |
|---|---|---|
| Code identifiers | PascalCase types, camelCase fields / methods, UPPER_SNAKE_CASE constants; affixes per §2.2; never split a concept id | `ArchitectureRulesTest`, `GlossaryTest`; role selection planned |
| schema, table, column, constraint, index | schema from the path below; table = registered plural in snake_case; column = qualifier + §2.4 class word; constraints / indexes per §2.4; no separate snake designation | `DdlNamingTest`, `GlossaryTest`; plural planned |
| Event name, payload key | object + controlled verb slots (§2.1.5); type PascalCase, keys camelCase | `ArchitectureRulesTest`, `GlossaryTest`, `NamingSurfaceTest`; slot equality planned |
| API path, parameters | subdomain slug + kebab-case registered plural; action from the controlled actions; parameters camelCase; a BC never stands for the subdomain | `NamingSurfaceTest` (G41), `ApplicationIntegrationTest`; controlled forms planned |
| Permission code | `{subdomain}.{BC}:{kebab resource}:{standard action}`; resource singular as in the existing catalog, not the API plural | `NamingSurfaceTest` (G43); concept-reference projection planned |
| UI text, message key | nouns take the preferred `zh-CN / canonical` designation; key `{subdomain}.{BC}.{singular kebab resource}.{lowercase dotted usage}`, cross-BC `kernel.{singular kebab resource}.{lowercase dotted usage}` | `check-copy-glossary.mjs / OutwardTextGuardTest` (old wording only); key, role, placeholders planned |

- **Schema path**: match the BC reference exactly against `bcs.yaml` `bcs[].id` with `kind: bc` (`kind: kernel` is not projected; its schema follows ADR-0051 decision 2); the subdomain is the `kind: subdomain` entry by its `subdomain`. Each fragment is that entry's `designations[].name` selected as `en / code_name / preferred`, snake_case, joined by `_`; `id` and `subdomain` are references only. A missing field, invalid reference or unusable designation fails, no fallback. `bcs.yaml` has no `designations` yet; the projection is enabled after they are filled.
- **Controlled plural**: a table or API resource needs a registered plural for that concept and surface; unregistered fails, no automatic `s` (`Analysiss`, `Staffs` are counter-examples).
- **Message-key usage**: `[a-z]+` segments joined by `.`; resource slot singular kebab-case, e.g. `cost.timesheet.timesheet.submit.succeeded`, `kernel.document.version.conflict`. `DOTTED` in `NamingSurfaceTest` checks job / metric and config keys, not message keys, and excludes kebab resources.
- **Not projected**: value-stream and value-stage names are `zh-CN canonical` only, never code identifiers, tables, events or message keys; value-stream names are verb-object result views (「获得内部服务」), stage names verb-object (「提交申请」) with the verb in the controlled verb table (NAM-1017-ARC-04); guard planned.

**Length and abbreviation** (one-architecture owns format, one-glossary abbreviation designations):
- **N3-L1**: every PostgreSQL identifier (schema, table, column, constraint, index, view, trigger, function, sequence) is at most **63 bytes** UTF-8 of the final output, prefixes, suffixes and separators included; schema and object counted separately; never rely on truncation. Enforced by `DdlNamingTest#migrationAndBaselineIdentifiersFit63Bytes`.
- **N3-L2**: default to the full name; use a registered abbreviation only when a standard or industry abbreviation is registered for that surface, or the full projection exceeds 63 bytes (record the full projection and byte count first, replace at the registered position, re-check). **Collisions are scoped by schema**: table, view, index and sequence share the relation-name space (the index a `pk / uk` constraint creates also occupies one; a constraint and its own same-name index count once); constraint names are unique within a table. If it cannot be shortened, hand it to one-glossary / one-architecture; no truncation, initials or random suffix. Guard planned (registration source, collisions; before DDL is applied); review meanwhile.
- **N3-L3**: an abbreviation is the designation's boolean `abbreviation`, registered with concept `id`, full designation, text, language, surfaces and decision source; projection replaces only the registered part and keeps class words and constraint / index prefixes. In class names it is a word (`WbsNode / PocValue / ArItem`, not `WBSNode`); these spellings do not permit every same-shape abbreviation. Word-split and ASCII checks cover part; authorisation planned (before switch).

#### 2.1.5 Event wording

A command is a base-form verb + object; an event is object + past participle of a fact, no `Event / Request / Command` suffix (`SubmitTimesheet → TimesheetSubmitted`; Chinese takes the registered designation). Guards: `ArchitectureRulesTest`, `GlossaryTest`, `scripts/guards/term_rules.py` (G5, G29, `docs/rules/coding-standard.md` CS-1.4); slot-rendering equality planned. Register designs: `docs/glossary/glossary-schema-design.md` §2.

#### 2.1.6 UI message catalog

Not implemented: source `docs/glossary/messages/zh-CN.yaml` (to be created), design in `docs/glossary/glossary-schema-design.md` §3, key format §2.1.4. Guard planned before the catalog is enabled; `check-copy-glossary.mjs / OutwardTextGuardTest` is no message-key acceptance.

#### 2.1.7 Summary of planned guards

Moved to `docs/glossary/glossary-schema-design.md` §4.

### 2.2 Spelling

- English ASCII identifiers only; Chinese only in UI text, i18n and comments (G15). Abbreviations are words (`WbsNode`; §2.1.4 N3-L2–L3).
- Class suffixes are **only** these, each with one location:

| Suffix / form | Location |
|---|---|
| Aggregate, entity, value object: noun, no suffix (`Timesheet`, `Money`) | `<module>.domain` |
| `*Repository` (interface) | `<module>.domain` |
| `Jdbc*Repository` (implementation, package-private) | `<module>.infrastructure.persistence` |
| Use case: verb-object, no suffix, one class each (`SubmitTimesheet`) | `<module>.application` |
| `*Queries` / `*Commands` (interface) | `<module>.api` |
| Event: past tense, no `Event` suffix, record (`TimesheetApproved`) | `<module>.events` |
| `*Controller` (package-private) | `<module>.web` |
| `*Request` / `*Response` (record) | `<module>.web` |
| `*Client` / `*Gateway` (anti-corruption layer, `U8cVoucherClient`) | `integration.<system>` |
| Status enum `*Status` (enum name stored in the database) | `<module>.domain` |
| **Forbidden** anywhere: `Util(s)`, `Helper`, `Manager`, `Info`, `Data`, `Impl`, `DO`, `ManHour` | |

### 2.3 Database, API, frontend

- **PostgreSQL** (`DdlNamingTest`, D2): one BC one schema `<subdomain>_<bc>` (`cost_timesheet`); tables snake_case plural, no `psa_` prefix; external reference `<object>_id`; audit columns `created_at`, `updated_at`, `version`; booleans `is_` / `has_`; amounts `<name>_amount` + `<name>_currency`; status = enum name (`text` + `CHECK`). No generic `created_by` / `updated_by`: a state change's operator is `state_transitions.actor_id` (ADR-0058), other actions go to audit (`platform_audit`); a column only for an operator with business meaning, named for the action (`confirmed_by`, `released_by`) (one-architecture 2026-10-22). Flyway: `V{yyyyMMddHHmmss}__{module}_{description}.sql` in the module's directory, English snake_case description (Chinese in the first-line comment).
- **HTTP**: `/api/v{n}/{subdomain}/{plural resource}` (ADR-0008, `/api/v1/cost/timesheets`; second segment is the **subdomain** slug, not a BC), kebab-case; actions `:submit` / `:approve`; **all JSON (interfaces, event payloads, external delivery) is camelCase**, snake_case only in the database; error codes segmented by module; common errors (illegal state transition, concurrent modification, reason required, past withdrawal cut-off, not logged in…) are defined once in `kernel` (user 2026-10-15: 「通用错误码只在公共基础定义」). Detail: §2.6.
- **Frontend**: `src/modules/<module>/{pages,components,hooks,api}`; API client generated from OpenAPI, no hand-written URLs; TypeScript strict; components PascalCase, hooks `useXxx`, files kebab-case.

### 2.4 Column-name class words, constraints and indexes (after DAMA / ISO 11179)

Column name = qualifier + class word (ISO 11179); the class word decides the type and may stand alone (`code`, `name`, `status`); `id` and `version` also stand alone. A document number is `_number`, not `_no`.

| Class word | Type |
|---|---|
| `_id` identifier | `uuid` (ADR-0033); only exception `platform_integration.source_references.source_id` (source's actual type, `bigint` for A8, may be negative); an external system's text identifier is `_key`, not `_id` |
| `_ids` set of identifiers (ADR-0034-IMPL ruling A-3, one-architecture 2026-10-17 took (a)) | `uuid[]` |
| `_key` business key, idempotency key, external system's text identifier (`external_subject_key`) | `text` |
| `_code` / `_codes` | `text` / `text[]` |
| `_name`; `_text`, `_description`, `_reason`, `_note`, `_title`, `_message`; `_address` (email, IP); `_ciphertext`; `_hash`; `_url` / `_path` | `text` |
| `_status`, `_kind`, `_type`, `_policy`, `_system` (external system category, e.g. `source_system`) | `text` + `CHECK` listing enum names |
| `_at` point in time / `_date` business date | `timestamptz` / `date` |
| `_amount` (pairs with a same-named `_currency`) | `numeric` |
| `_currency` (ISO 4217) | `text` / `char(3)` |
| `_qty`, `_hours`, `_days` quantity with unit | `numeric` (`_qty` may be integer) |
| `_rate` / `_pct` ratio, unit price / percentage | `numeric` |
| `_count` | `integer` / `bigint` |
| `_number` document number, sequence (round) | `text` / `integer` / `bigint` |
| `_order` | `integer` |
| `_by` operator (audit; the user account's `user_id`, ADR-0033) | `uuid` |
| `_value` numeric value (counter, start value) | `numeric` / `integer` / `bigint` |
| `is_` / `has_` (prefix) indicator | `boolean` |

**Constraints and indexes**: `pk_<table>`; `fk_<table>_<column>`; `uk_<table>_<column>` (unique constraints and unique indexes); table-level check `ck_<table>_<rule>`; exclusion `ex_<table>_<rule>`; index `ix_<table>_<column>`. Primary, foreign and unique constraints are named explicitly with `CONSTRAINT <name>`. **Every enum `CHECK` is named `ck_<table>_<column>`** (DDL-22; no unnamed column-level CHECK) and its value set matches the Java enum; existing items are renamed in the baseline DDL tightening (R-2); guard G-D12.

**Guard** `DdlNamingTest` (D2) derives the final table structure from all migrations (`ADD COLUMN`, `RENAME COLUMN`, `RENAME CONSTRAINT`, `ALTER INDEX ... RENAME TO`) and checks class words vs types and constraint / index names. Earlier violations sit in `backend/architecture/ddl-naming-baseline.txt` (each with a rename suggestion); a new violation, or a fixed line left in the baseline, fails. Framework tables are exempt: `platform_identity.sessions`, `sessions_attributes` (Spring Session JDBC), `platform_integration.event_publication` (Spring Modulith), `platform_scheduling.scheduler_locks` (ShedLock).

### 2.4a DDL design principles

(User 2026-10-15: 「全部采纳 (Recommended)」.) For all own tables. "Guard" = `DdlNamingTest` / `DataArchitectureTest` (planned ones in §4); others by per-wave review (DATA_DESIGN_CHECKLIST §14.1).

1. **Business unique constraint**: primary key always UUIDv7 (ADR-0041; `docs/architecture/frameworks.md` §9; order by a business-time column, not the id). Transaction, master-data, reference and configuration tables also need a business unique constraint (exclusion constraint for effective-interval rows) and register `business_key` in the data-access registry; pure log / event tables may be exempt with a reason. A business key is never a foreign key. Guard.
2. **Indexes**: a foreign-key column has an index starting with it (G-DDL2); every list / query path is registered (filter, sort) and index-covered, cursor sort key falls back to `id` in a composite index (G-P3 + G-DDL4, one EXPLAIN test); hot status paths use a partial index; no duplicate or redundant indexes (G-DDL3). Planned (hot-status partial index: no guard).
3. **Nulls and defaults**: `NOT NULL` by default; a nullable column's comment states the meaning of "empty" (「为空表示」 or equivalent); no empty string for null in text columns (`CHECK (col <> '')`); only technical defaults (initial `version`), business values and times written by the application. Guard.
4. **Delete**: foreign keys `ON DELETE RESTRICT`; `CASCADE` only for child rows of the same aggregate, registered; no soft-delete flag column (business delete = status or version); physical delete only for expiring technical data. Guard.
5. **Codes and text**: a `_code` column has a format CHECK (character set, case, length); case-insensitive uniqueness on the normalised value (`lower(email_address)`); free text `CHECK (char_length(col) <= n)`. Guard.
6. **Enums**: no PostgreSQL `ENUM`, uniformly `text + CHECK` (§2.4); a new value = `DROP CONSTRAINT`, `ADD CONSTRAINT … NOT VALID`, `VALIDATE`; removing a value migrates data first. Guard (ban on `CREATE TYPE ... AS ENUM`).
7. **jsonb**: only for payloads with no fixed structure (event archive, external raw text, idempotent response body); domain fields must be proper columns; each `jsonb` column registers its purpose and has a `jsonb_typeof` CHECK. Guard.
8. **Large tables**: append-only with retention over 3 years or over ten million expected rows decide partition key (time range) and archive / purge method at creation and register them. Review.
9. **Post-go-live migrations** (after the open-baseline marker is deleted): expand then contract; `CREATE INDEX CONCURRENTLY` in a separate non-transactional migration; set `lock_timeout` and `statement_timeout`; no full-table rewrite (column type change, volatile default); drop / rename a column takes two releases. Guard planned (G-DDL11).
10. **Frequently updated rows** (numbering counters, idempotency registry): may set `fillfactor`, explained in the table comment. Recommendation.

### 2.5 Column-level sensitivity classification

| Level | Meaning | Handling |
|---|---|---|
| `PUBLIC` | may go outside | — |
| `INTERNAL` | default | visible after login, by function permission |
| `SENSITIVE` | personal information, salary and daily rate, bank cards (`docs/architecture/guiding-principles.md` §6) | masked, authorised by legal entity × department × project role, access traced; not in analytics detail |
| `RESTRICTED` | passwords, keys, session content | never leaves the database, not in logs or analytics |

Registered in `backend/architecture/data-classification.txt` (`<schema.table.column> | <level> | <reason>`); unregistered = `INTERNAL`. `DdlNamingTest` requires a column whose name looks sensitive (password, key, TOTP, bank, salary, daily rate, ID document, mobile, email, birthday, gender, address, session content) to be `SENSITIVE` or `RESTRICTED`, and a registered column to exist. web / api outputs expose no `SENSITIVE` / `RESTRICTED` column (`SensitiveColumnExposureGuardTest`).

### 2.6 HTTP interface conventions

(User 2026-10-15: 「全部按推荐 (Recommended)」.) For all `/api/v{n}/…`; paths §2.3; versioning ADR-0008, ADR-0035. Planned guards G-A1–G-A9, G-C4; until then each wave's review checks them.

- **A-1 Resources and commands**: read `GET`; create `POST /{plural resource}`; replace editable fields wholesale `PUT /{plural resource}/{id}`; state transitions and business actions are commands `POST /{plural resource}/{id}:{action}` (action from `standard_actions` in `terms.yaml`), never `PUT`; `DELETE` only for deletable objects (unsubmitted draft); "void" / "revoke" is a command. A side-effect-free computation (preview, trial calculation) may be `GET /{plural resource}:{action}` or `GET /{plural resource}/{id}:{action}`.
- **A-2 Status codes**: create `201` with `Location` and `ETag`; command `200` (resource after the command) or `204`; delete `204`; read `200` with `ETag`; contract matches runtime.
- **A-3 Lists**: response `{items, nextCursor}`; parameters `cursor` (opaque, format-versioned, unsigned, encodes sort key + `id`, strictly validated, invalid → 400 `PLATFORM_WEB_CURSOR_INVALID`) and `limit` (default 50, maximum 200, single kernel constants, OpenAPI `maximum: 200`, exceeding → 400, no silent truncation). Index-based keyset pagination, never in-memory slicing (`docs/architecture/guiding-principles.md` §7). A total is `GET /{plural resource}:count` with the list's filters. Small unpaginated tables: guiding-principles §7a R-P2.
- **A-4 Filtering and sorting**: camelCase field-name filters, multiple values comma-separated, no expression language; `sort=field,-field`, whitelisted fields; an unknown parameter or non-whitelisted field → 400 `PLATFORM_WEB_REQUEST_INVALID` with extension `invalidParameters` (every `GET` under `/api`). An export uses the list's filters.
- **A-5 No `PATCH`**: use `PUT` of the editable fields or a named command.
- **A-6 Batch**: `POST /{plural resource}:{action}-batch`, at most 100 items (as guiding-principles §7a R-P2); authorisation and execution per item; `200` with per-item results in request order (id, result, ProblemDetail on failure), partial success also `200`; a malformed batch → 400, nothing executed.
- **A-7 Types**: own IDs are string uuids (`type: string, format: uuid`, also path / query parameters; opaque to the frontend; source-system IDs never enter the interface; ADR-0033 item 6 clause 2); instants ISO-8601 UTC with `Z` (no offsets, no epoch milliseconds); business dates `YYYY-MM-DD` in `Asia/Shanghai`; amount `{amount, currency}` with `amount` a decimal string; ratio a decimal string in 0–1; other decimals (hours, person-days) strings; status / type values are named `enum` schemas.
- **A-8 Idempotency and concurrency**:
  - A side-effecting `POST` accepts `Idempotency-Key`: response (status, body, `ETag`, `Location`) stored 24 hours per "user + key" and replayed verbatim; same key with another body → 422, 409 while the first is processing (a placeholder past the configured timeout may be taken over); 5xx not stored. Session and login-state endpoints (path contains `/sessions` or ends `/login-states`) store no body and do not declare the header; scope is `IdempotencyScope`, guarded by `HttpContractIT.idempotencyKeyIsOfferedExactlyWhereItIsStored`.
  - Only `POST`s in `backend/architecture/idempotent-endpoints.txt` store an answer, with no direct identifier and no `SENSITIVE` / `RESTRICTED` / personal-information column (ADR-0043 section 4, TB-02; `IdempotentEndpointGuardTest`).
  - A `PUT`, `DELETE` or command on an existing resource carries `If-Match: "<version>"`: missing → 428, mismatch → 412; the contract declares the header and 412 / 428 (ADR-0035 item 8; G-C4); the version is not in the body; a command needing none (forced logout) is a registered exemption with reason.
  - 409 means only "state does not allow the operation", not a concurrency conflict.
- **A-9 Errors**: RFC 9457 ProblemDetail, `type` and `code` from `ErrorCode`; common errors only in kernel (G48); each interface declares its error statuses; a 5xx body carries only `correlationId`, details go to the log; produced in one place.
- **A-10 Access control**: not logged in 401; no function permission 403; permission but a single resource outside the data scope 404 (existence not revealed); lists filtered by data scope without error; the scope comes from the session, never a request parameter.
- **A-11 Output**: a response body is a `*Response` record in `web`, never an application `*View` or domain object; `SENSITIVE` fields masked per §2.5, `RESTRICTED` absent.

## 3. Package structure: where code goes (agent cheat sheet)

```
com.forcitis.one
├── SubdomainModuleDetection.java   every <subdomain>.<bc> is a top-level module (ADR-0003)
├── kernel/                         shared kernel (OPEN): Money, Hours, LegalEntityId, UserId, @ValueObject, @PublicEndpoint
└── <subdomain>/                    presales | contract | delivery | cost | receivables | expense | procurement
    │                               | km | organization | accounting | platform | operations
    └── <bc>/                       one BC = one Modulith module (e.g. cost.timesheet)
        ├── package-info.java       responsibility, Chinese name, owned schema (no @ApplicationModule)
        ├── api/ events/            @NamedInterface("api") / ("events"): the only exits
        ├── domain/                 aggregate, entity, value object, domain service, *Repository port; no Spring / JDBC
        ├── application/            use cases (one public method each), the only transaction boundary
        ├── infrastructure/persistence/  Jdbc*Repository, only the <subdomain>_<bc> schema
        └── web/                    *Controller (/api/{version}/…), *Request / *Response
resources: db/migration/<subdomain>_<bc>/V<yyyyMMddHHmmss>__<subdomain>_<bc>_<description>.sql
architecture/module-dependencies.txt  approved module dependency graph; changes need review
```

| I want to write… | Put it in |
|---|---|
| business rule or calculation | `<module>.domain`, glossary noun |
| flow behind one user action | `<module>.application`, verb-object |
| query for other modules / "what happened" | `<module>.api` interface (`*Queries`) + `application` implementation / `<module>.events` record published inside the use case |
| SQL / REST interface / external system call | `infrastructure.persistence` (`Jdbc*Repository`) / `web` (`*Controller`) / `integration.<system>` (`*Client`) |
| shared value type | `kernel` (review; no business behaviour) |
| new cross-module dependency | first change `architecture/module-dependencies.txt`, pass review |
| new table / column | `db/migration/<subdomain>_<bc>/` with a Chinese COMMENT, names derived from the glossary |
| business number | `DocumentNumbers.next(...)` of `platform.numbering` inside the use case's transaction; new type in `DocumentType` |

## 4. Guard rules

Format: id | rule | enforced by; the cell follows the Check-column format of `docs/rules/coding-standard.md` §0 (checked by `scripts/guards/check_rules_enforced.py`). Planned guards follow the implemented tables; until implemented, each wave's review checks them by source.

### Backend, implemented

| # | Rule | Enforced by |
|---|---|---|
| G1 | Modules cross only via `api` / `events`; no cycles; only `allowedDependencies` | `ModularityTest` (Modulith `ApplicationModules.verify()`) |
| G2 | `api`: interfaces and records only | `apiPackagesContainOnlyInterfacesAndRecords` |
| G3 | `api` signatures leak no internals | `apiSignaturesDoNotLeakModuleInternals` |
| G4 | ≤ 12 api methods per module | `apiSurfaceStaysNarrow` |
| G5 | `events`: records only, no `Event` suffix | `eventsAreRecords` |
| G6 | web and infrastructure classes not public | `webAndInfrastructureAreNotPublic` |
| G7 | domain free of Spring, Servlet, other layers | `domainIsFrameworkFree` |
| G8 | application free of web, infrastructure | `applicationDoesNotTouchWebOrInfrastructure` |
| G9 | web calls only application | `webOnlyCallsApplication` |
| G10 | JDBC / `java.sql` only in infrastructure | `jdbcOnlyInInfrastructure` |
| G11 | `@Transactional` only in application | `transactionsOnlyInApplication` |
| G12 | `@RestController` only in web, ends `Controller` | `controllersLiveInWebAndEndWithController` |
| G13 | domain `*Repository` is an interface; implementation starts `Jdbc` | `repositoriesFollowPortAdapterNaming` |
| G14 | `Util` / `Helper` / `Manager` / `Info` / `Data` / `Impl` / `DO` / `ManHour` forbidden | `noVagueOrLegacyNames` |
| G15 | Identifiers ASCII | `identifiersAreAscii` |
| G16 | Domain `*status` field is an enum | `statusesAreEnums` |
| G17 | Outside kernel, `*amount` / `*price` / `*cost` not a bare `BigDecimal` | `ArchitectureRulesTest#moneyIsNeverABareDecimal` |
| G18 | Field injection, `System.out`, JUL, `java.util.Date`, `@PermitAll` forbidden | `generalCodingRules` |
| G19 | Flowable only in `platform.approval.infrastructure` | `flowableOnlyInsideApprovalInfrastructure` |
| G20 | Controllers map to `/api/{version}/…` | `controllersAreVersioned` |
| G21 | `@ValueObject` is a record | `valueObjectsAreRecords` |
| G22 | Value object holds no `Date`, mutable collection, array | `valueObjectsHoldNoMutableState` |
| G23 | Domain `*hours` field uses `Hours` | `hoursAndQuantitiesUseValueObjects` |
| G24 | No `BigDecimal.equals`, `new BigDecimal(double)` in domain, application | `noBigDecimalEqualsInBusinessCode` |
| G25 | No direct `now()`; inject a `Clock` | `timeComesFromAnInjectedClock` |
| G26 | No `Fix*`, `Demo*`, `Mock*`, `Test*`, `Sample*`, `*Example*` in main code | `noDebugOrDataFixCodeInMain` |
| G27 | A use case has exactly one public method | `useCasesExposeExactlyOnePublicMethod` |
| G28 | Every interface method has `@PreAuthorize` or `@PublicEndpoint(reason)` | `everyHandlerIsAuthorizedOrExplicitlyPublic` |
| G29 | No event name ending Request / Requested / Command / Query; no plain `@EventListener` | `eventsAreFactsNotRequests` |
| G30 | `@NamedInterface` only on api / events | `namedInterfacesAreOnlyApiAndEvents` |
| G31 | `*Id` field in api / events / domain is a typed-ID record | `contractsAreStronglyTyped` |
| G32 | No `Object` / `Map` in api / events | `contractsAreStronglyTyped` |
| G33 | Only identity configures Spring Security; request records carry no identity fields | `onlyIdentityConfiguresSecurity`, `requestsNeverCarryTheCallerIdentity` |
| G37 | [ADR-0018] type names, record components, enum constants, non-private methods in domain / api / events / web / kernel and migration table / column names, split into words, are in `docs/glossary/terms.yaml` or `common_words` | `GlossaryTest` (failure writes `target/glossary-draft.yaml`) |
| G38 | [ADR-0018] glossary forbidden spelling fails | `GlossaryTest` |
| G39 | [ADR-0018] a column matching a glossary term has a comment starting with its Chinese name | `GlossaryTest` (parses `COMMENT ON`) |
| G40 | [ADR-0019] no dependency on `cn.iocoder..` or yudao base classes (`BaseDO`, `CommonResult`, `TenantBaseDO`) | `LegacyShapeTest#theCodeBaseHasNoOldSystemShape` |
| G41 | HTTP path `/api/v{n}/{subdomain of the owning module}/{kebab plural resource}[/{id}][:{action}]`, resource registered | `NamingSurfaceTest` |
| G42 | OpenAPI schemas PascalCase, properties / parameters camelCase and registered; event JSON keys camelCase | `NamingSurfaceTest` (reads `/v3/api-docs`) |
| G43 | Permission code `{subdomain}.{BC}:{resource}:{action}`, action from `standard_actions`; `@PreAuthorize` codes in the catalog | `NamingSurfaceTest` |
| G44 | Flyway `V{14-digit real timestamp}__{module}_{english_snake}.sql`, directory = module schema, prefix matches, no duplicate versions | `NamingSurfaceTest` |
| G45 | Document-type enum matches seed rows one-to-one, upper snake case, registered | `NamingSurfaceTest` |
| G46 | `@Scheduled` job has ShedLock lock `{module}.{name}`; metric names literals prefixed by module; `job_type` checked; config keys `forcitis.{module}.` or framework keys | `NamingSurfaceTest` |
| G47 | Process / decision id `{subdomain}_{BC}_{snake}`, file name = id, `ProcessKey` matches processes one-to-one, user-task ids camelCase | `NamingSurfaceTest` |
| G48 | One error-code enum per module implementing `kernel.ErrorCode`, `{SUBDOMAIN}_{BC}_{REASON}`; common errors only in `kernel`; no synonyms across enums (Chinese name unique, R-5) | `NamingSurfaceTest` |
| D1 | Module dependencies registered in `architecture/module-dependencies.txt` | `ModuleDependencyTest` |
| D2 | DDL naming: file name, own-schema tables, snake_case, no old names or vague words, audit columns, amounts with currency, status `CHECK`, `is_` / `has_`, COMMENT on every column; class words vs types, constraint / index names (§2.4); sensitive-column registration (§2.5); N3-L1 length | `DdlNamingTest` |
| D3 | Data dictionary generated from metadata; every table and field commented | `ApplicationIntegrationTest#everyTableAndColumnIsDocumentedAndTheDictionaryIsGenerated` |
| G-A2 | List response `{items, nextCursor}`, parameters `cursor` / `limit`; gaps in `backend/architecture/list-paging-gaps.txt` (new violation or fixed gap left listed fails) | `ListPagingGuardTest` (K-3a) |
| G-P1 | `limit` cap from one kernel constant (`ListLimits`); OpenAPI `limit`: `minimum: 1`, `maximum: 200`, `default: 50`; no other `MAX_LIMIT` constants (§2.6 A-3) | `ListPagingGuardTest` (K-3a; gaps as G-A2) |

### Frontend, implemented (`frontend/eslint.config.js`, `frontend/stylelint.config.js`, `frontend/scripts/check-structure.mjs`; TypeScript strict)

| # | Rule | Enforced by |
|---|---|---|
| F1–F4 | One-way `app → modules(index) → shared`; cross-module only via `modules/<m>/index.ts` | `frontend/eslint.config.js` (eslint-plugin-boundaries) |
| F5 | No axios, fetch, hand-written `/api` URLs; only the generated client | `frontend/eslint.config.js` |
| F6 | ASCII identifiers; kebab-case files; no Util / Helper / Manager / Info / Data / Impl classes | `frontend/eslint.config.js` |
| F7 | No `any`; React hooks rules | `frontend/eslint.config.js` |
| F8 | hooks in `hooks/`, named `useXxx` | `frontend/eslint.config.js` |
| F9 | `model/` and `shared/lib` pure: no React, no API | `frontend/eslint.config.js` |
| F10 | Only `modules/<m>/api` imports the generated client | `frontend/eslint.config.js` |
| F11 | No `dangerouslySetInnerHTML` (SEC-008) | `frontend/eslint.config.js` |
| F12 | No `export *` | `frontend/eslint.config.js` |
| F13 | Each module's `index.ts` ≤ 8 exports | `frontend/scripts/check-structure.mjs` |
| F14 | Business modules: no direct antd, inline styles, hard-coded colours; raw tokens only in `shared/design-system` (ADR-0013) | `frontend/eslint.config.js` |

### Planned guards (not implemented)

(User 2026-10-15: 「全部按推荐 (Recommended)」.) Registered only; the rule text is at "Source". Abbreviations: **FD** = before first deployment, **PD** = before first production deployment; "as X" = same as row X; an owner is a work-unit name where an ADR or user decision fixes it, else "unscheduled" with the initial group. Overlapping guards merge into one test on implementation.

| # | Check | Source | Must exist before | Owner / status |
|---|---|---|---|---|
| G34 | `operations.facts` fact repositories only insert and query; fact tables have triggers rejecting UPDATE / DELETE | ADR-0016 | first card building `operations.facts` / `accounting.ledger` | planned (modules do not exist yet); ArchUnit + DDL test |
| G35 | only `accounting.ledger` depends on accounting subjects and subject mapping | ADR-0016 | as G34 | as G34; ArchUnit |
| G36 | a booking-touching BC submits posting candidates only via the `operations.facts` api, writes no fact tables | ADR-0016 | as G34 | as G34; D1 + ArchUnit |
| G-A1 | §2.6 A-7 own IDs `type: string, format: uuid`; frontend ID types not number | §2.6 A-7 | immediately (before handoff / reassignment UI) | R-1b |
| G-A3 | Merged into G-C4 | §2.6 A-8, ADR-0035 item 8 | see G-C4 | see G-C4 |
| G-A4 | Merged into G-C4 | §2.6 A-2, ADR-0035 item 8 | see G-C4 | see G-C4 |
| G-A5 | No `PATCH` mapping | §2.6 A-5 | before 1b | unscheduled (HTTP contract guard) |
| G-A6 | Instants `date-time` with `Z`; amounts `{amount: string, currency}` | §2.6 A-7 | before 1b | unscheduled (HTTP contract; with G-M5) |
| G-A7 | A controller returns only a `web` `*Response` | §2.6 A-11 | FD | unscheduled (HTTP contract; complements G-X6) |
| G-A8 | No common error codes outside kernel | §2.3, §2.6 A-9 | FD | R-5 (same implementation as G48) |
| G-A9 | Error responses only from the unified handler | §2.6 A-9 | before 1b | unscheduled (HTTP contract) |
| G-C1 | `scripts/ci/openapi_compat.py` (to be created): OpenAPI vs merge-base and deprecation register; a registered and expired deletion passes; any other breaking change fails and passes only with the commit trailer `Contract-Break:` (listed separately in the PR) | ADR-0035 items 3, 7, 9 | PD | unscheduled (contract evolution) |
| G-C2 | AsyncAPI shape vs merge-base: a changed payload schema, class or package name needs `schemaVersion + 1` or a new event type and marks the release "needs drain" | ADR-0035 items 3, 5, 9 | PD | as G-C1 |
| G-C3 | Drain check: for a "needs drain" release, after maintenance mode (`platform_scheduling.maintenance_modes`) unfinished event publications and dead letters are 0 (undrainable ones quarantine-registered), else deployment aborts | ADR-0035 item 6 | PD | as G-C1 (playbook: one-ops) |
| G-C4 | Truthful contract: status codes (201 with `Location`), required headers (`If-Match`, 412 / 428), `ETag`, named enums, no untyped bodies, `info` matches runtime (extends `HttpContractIT`; absorbs G-A3, G-A4); with G-C5 the two dev-time contract guards | ADR-0035 items 2, 8, §2.6 | when H-1 merges (after R-5) | H-1 |
| G-C5 | Event and outbound payloads flat: string uuid IDs, no `{value: …}` wrapper | ADR-0035 items 2, 5 | when R-1b merges | R-1b |
| G-C6 | Scheduled check of expired undeleted deprecated items (P3 reminder to the scheduling session; does not turn unrelated PRs red); a PR changing a deprecated object (deletion excepted) fails | ADR-0035 item 7 | PD | as G-C1 |
| G-C7 | `reporting` dataset contract: view column names and types match the registration | ADR-0035 | R1 | R1 work unit |
| G-P2 | SQL statement count of list and batch use cases does not grow with row count (counting DataSource decorator, budget ≤ 5) | `docs/architecture/guiding-principles.md` §7a R-P3 | before 1b | unscheduled (performance) |
| G-P3 | Registered query paths use an index in `EXPLAIN` with `enable_seqscan = off` | guiding-principles §7a, DATA_DESIGN_CHECKLIST §14.1 D19 | before 1b | as G-P2 |
| G-P4 | Three timeouts of external read-only connections take effect (test against a hanging port: fails or degrades within budget) | DATA_DESIGN_CHECKLIST §14.3 I2 | FD | as G-P2 (card of G-I1, G-I3) |
| G-P5 | Production config: connection pool, statement timeout, histogram buckets, slow-query threshold per R-P4–R-P6 | guiding-principles §7a | FD | as G-P2 |
| G-P6 | k6 load test: every interface class of R-P1 meets target | guiding-principles §7a R-P1 | Phase 2 entry | unscheduled (performance; ask before installing k6) |
| G-S1 | `scripts/ci/workflows_pinned.py` (to be created): workflow `uses:` pinned to a 40-character SHA; `pull_request_target` forbidden | docs/ops/supply-chain.md S6 | FD | unscheduled (CI supply chain) |
| G-S2 | osv-scanner vulnerability and licence check (inside an existing job) | supply-chain.md S3, S4 | FD | as G-S1 |
| G-S3 | `pom.xml` declares no custom repository, only Maven Central | supply-chain.md S1 | FD | as G-S1 |
| G-S4 | Test database image defined in one constant, with a digest | supply-chain.md S7 | FD | as G-S1 |
| G-S5 | Release includes an SBOM, written into the signed attestation | supply-chain.md S5 | PD | as G-S1 |
| G-S6 | Dedicated deployment runner pre-job hook checks run user and credential directory (legacy production, outside Lab CI) | supply-chain.md S6 | pending the dedicated-runner-user decision | as G-S1 |
| G-O1 | An `ERROR` log line begins 「ALERT P1\|P2\|P3 <subdomain>.<BC> 」 | `docs/architecture/guiding-principles.md` §11 O-2 | FD | unscheduled (observability) |
| G-O2 | No log argument from a personal-information method; unknown exceptions log only the class name | guiding-principles §11 O-3 | FD | as G-O1 |
| G-O3 | Test: every log line has a correlationId; e.g. the email of a unique-constraint conflict is not written | guiding-principles §11 O-1, O-3 | FD | as G-O1 |
| G-O4 | Production config exposes Prometheus, connection string has `logServerErrorDetail=false`; zero business metrics scraped after deployment fails | guiding-principles §11 O-3, O-4 | FD | as G-O1 (post-deployment check: one-ops) |
| G-O5 | Metrics referenced by alert rules exist in the application | guiding-principles §11 O-6 | FD | as G-O1 |
| G-O6 | Missing correlationId at event publication is counted and alerted, not recreated | guiding-principles §11 O-1 | before 1b | as G-O1 |
| G-I1 | External connections only via the integration layer's connection factory enforcing connect, query, lock-wait timeouts | DATA_DESIGN_CHECKLIST §14.3 I2 | FD | unscheduled (integration resilience) |
| G-I2 | `backend/architecture/integration-register.yaml` (to be created) matches the adapters in code | DATA_DESIGN_CHECKLIST §14.3 | FD | as G-I1 |
| G-I3 | A view hung with `WAITFOR` verifies timeouts and degradation | DATA_DESIGN_CHECKLIST §14.3 I2, I7 | FD | as G-I1 |
| G-I4 | User-path cool-down: 3 consecutive failures, no call for 60 seconds | DATA_DESIGN_CHECKLIST §14.3 I6 | before 1b | as G-I1 |
| G-I5 | One failure logs one `ERROR` with a stack trace | DATA_DESIGN_CHECKLIST §14.3 I10 | FD | as G-I1 |
| G-MG1 | `legacy_numbers`: UUIDv7 primary key, source-system column, `uk(source system, document type, original number)`, `migration_batch_id uuid NOT NULL`, append-only | ADR-0036 item 3, ADR-0033 | FD | R-1b |
| G-MG2 | Staging run twice or resumed, and a resumed commit, give the result of a single run (absorbs G-MG3) | ADR-0036 item 7 | phase first migrating in rights-and-duties facts | first migration-in phase |
| G-MG3 | Merged into G-MG2 | ADR-0036 item 7 | — | — |
| G-MG4 | Migration-in has no side effects. Test: offline context, one batch, `@RecordApplicationEvents` asserts 0 events, counters unchanged, no process, no `accounting.ledger` entry, posting-result rows = migrated facts, with injected counter-examples. Production: 0 loaded `@ApplicationModuleListener` / `@TransactionalEventListener` methods under `com.forcitis.one` at offline start, else startup refused | ADR-0036 items 1, 5 | as G-MG2 | as G-MG2 |
| G-MG5 | Offline migration-in context has no `@Scheduled` method, ShedLock job, Flowable async / timer executor, event listener, outbound worker; no module depends on the migration-in orchestration module | ADR-0036 item 1 | as G-MG2 | as G-MG2 |
| G-MG6 | Batch term unique: only 「迁入批次」 in code and tables (after R-1b renames, forbid `import_batch` / `ImportBatch` for `migration_batch`) | ADR-0036 item 3 | FD | R-1b |
| G-MG7 | Every (period × legal entity × object type) of the migration-in spec has a reconciliation row, zero difference or finance sign-off, else no commit; each batch has a quality report (ADR-0071 item 6) | ADR-0036 item 8 | as G-MG2 | as G-MG2 |
| G-MG8 | Deleted: stage-then-commit (ADR-0036 item 7) replaces the read-side visibility predicate | ADR-0036 | — | — |
| G-MG9 | Number column of a migrated document matches the original number in `legacy_numbers`; a mismatch alerts (`legacy_numbers` prevails) | ADR-0036 item 4 | as G-MG2 | as G-MG2 |
| G-MG10 | Each BC's migration-in entry is called only by `platform.migration` (ArchUnit), declares `Propagation.MANDATORY`; no `REQUIRES_NEW` on the path | ADR-0036 item 1 | as G-MG2 | as G-MG2 |
| G-MG11 | Quality rules consistent: rule file and code `MQ-*` agree both ways; spec-cited rules exist; `ACCEPTED_WITH_NOTE` only for warnings, `EXCLUDED` only for source-comparison findings, `NORMALIZED` only by registered `MQ-N` rules | ADR-0071 items 3, 5 | when W1 builds migration-in orchestration | before the first BC rehearsal |
| G-MG12 | Each quality gate (pending-reconciliation precondition, commit, effect) rejected by a constructed counter-example; migration-in database role cannot write the disposition table, in-app role has no disposition, deferral or Q5 entry permission; deferral with approver = owner rejected; a finding stays listed after trial-commit rollback; "approved not to migrate" over its cap rejected | ADR-0071 items 5, 6, 8 | as G-MG11 | as G-MG11 |
| G-D12 | §2.4 enum `CHECK` naming and value set (same test as G-DDL8) | §2.4, §2.4a item 6 | FD | R-2a |
| G-DDL1 | §2.4a item 1: `business_key` registered with the unique constraint; a foreign-key target is a primary key or a unique constraint of uuid columns only (one-architecture 2026-10-16, AP-04, WF-12) | audit 2026-10-15 G1 | FD | unscheduled (baseline DDL tightening) |
| G-DDL2 | §2.4a item 2: foreign-key column index | audit G2 | FD | as G-DDL1 |
| G-DDL3 | §2.4a item 2: no duplicate or redundant indexes | audit G3 | FD | as G-DDL1 |
| G-DDL4 | §2.4a item 2: registered query path uses an index in `EXPLAIN` (merged with G-P3; depends on G-DDL12) | DATA_DESIGN_CHECKLIST §14.1 D19; audit G4 | before 1b | as G-DDL1 |
| G-DDL5 | §2.4a item 3: nullable comment, empty-string CHECK, default whitelist | audit G5 | FD | as G-DDL1 |
| G-DDL6 | §2.4a item 4: `CASCADE` registered, no soft-delete flag, physical delete whitelist | audit G6 | FD | as G-DDL1 |
| G-DDL7 | §2.4a item 5: `_code` format CHECK, text length cap, normalised uniqueness | audit G7 | FD | as G-DDL1 |
| G-DDL8 | §2.4a item 6: no `CREATE TYPE … AS ENUM`; enum CHECK named, matches Java enum (same test as G-D12) | audit G8 | FD | as G-DDL1 |
| G-DDL9 | §2.4a item 7: `jsonb` purpose registered, `jsonb_typeof` CHECK | audit G9 | FD | as G-DDL1 |
| G-DDL10 | §2.4a item 8: scale and partition decision registered | audit G10 | FD | as G-DDL1 |
| G-DDL11 | `scripts/ci/migration_safety.py` (to be created): §2.4a item 9 (`CONCURRENTLY`, timeouts, no full-table rewrite, `NOT VALID`, expand-then-contract, no invalid indexes) | audit G11 | FD (before C-3 deletes the baseline marker) | as G-DDL1 |
| G-DDL12 | Test database collation C (as production); no invalid indexes after migration; fillfactor commented (§2.4a items 2, 10) | audit G12 | FD | as G-DDL1 |
| F15 | §4a U1 palette values | §4a U1 | before the first D-8 design-sync | unscheduled (frontend design system) |
| F16 | §4a U2 contrast | §4a U2 | before the first D-8 design-sync | as F15 |
| F17 | `eslint-plugin-jsx-a11y` rules | §4a U3 | before 1b | as F15 |
| F18 | axe in component tests | §4a U3 | before 1b | as F15 |
| F19 | antd `zhCN` and dayjs `zh-cn` set at the app root | §4a U4 | FD | as F15 |
| F20 | `check-bundle-size.mjs` (to be created; existing: `frontend/apps/h5/scripts/check-bundle.mjs`): gzip budget | §4a U8 | before 1b | as F15 |
| F21 | PC and H5 semantic-component contract types identical (type test) | §4a U7 | before 1b | as F15 |
| F22 | Query view three-state structure check | §4a U5 | before 1b | as F15 |

Other planned items (no id): SQL references only this module's schema (scan SQL text in `infrastructure.persistence`); every P0 rule has one test beginning `RULE_NNN_` (coverage against `docs/business/business-rules.md`); a new aggregate, event or table name must be in the glossary; frontend `no-restricted-imports` forbids cross-module import of internal directories.

## 4a. Frontend UI rules

(User 2026-10-15: 「全部按推荐 (Recommended)」.) PC and H5. Guards F15–F22 above.

- **U1 Brand colour and font**: VI colours as sRGB hex in the raw palette, the semantic layer references only the palette: FOR grey `#595757`, CITIS red `#CF000E` (exact, replacing approximate `oklch`, synced to the design-token master); `oklch` must fall inside sRGB. Wordmark is SVG. Fonts such as Montserrat ship with the package (OFL), files checked in before 1b.
- **U2 Contrast**: text ≥ 4.5:1 (large ≥ 3:1), non-text (icons, borders, focus ring) ≥ 3:1, on the actual token pairings; tertiary text and pending-approval status text colours are darkened to comply.
- **U3 Accessibility**: everything keyboard reachable, focus visible, focus returns to the trigger after an overlay closes; errors `role="alert"`, non-interrupting status `role="status"` (H5 likewise); icon-only buttons carry `aria-label`.
- **U4 Language and format**: source and this phase's UI language `zh-CN` only (§2.1.2–2.1.3); antd `zhCN`, dayjs `zh-cn`; date, instant, amount, quantity formatting only through core's functions (ANM AN-04, AN-05; ADR-0053 decision 6). Guard F19.
- **U5 States**: loading, empty, error through the query-view component; a 5xx error shows a copyable 「关联号」 (the correlationId of §2.6 A-9).
- **U6 No optimistic updates**: commands on documents, approvals and amounts update the UI only after server confirmation; only UI preferences without business consequence may.
- **U7 Component contract once**: semantic components (status tag, amount, approval actions) are defined once in `shared/design-system`, PC and H5 implement the same contract; core uses only React 18 APIs.
- **U8 Bundle budget** (gzip): PC entry ≤ 300 KB, single route ≤ 150 KB, H5 entry ≤ 200 KB; exceeding fails CI; a budget change needs a reason in the PR.

## 5. Agent-friendly conventions

- Each module root has a `README.md` (consistent with `package-info.java`): responsibility, owned schema, exposed api / events, allowed dependencies, related rule ids.
- One top-level type per file; one class per use case, entry method `execute`.
- Test packages mirror production; P0 rule tests are named like `RULE_012_clauseTotalMustEqualFinalAmount`.
- Generated code (OpenAPI client, jOOQ) lives only in `generated/`, never hand-edited, excluded from guards.
- A guard's failure message says where to put it / what to name it.

## 6. (removed: history; see `git show 59315c148^:docs/rules/naming-standard.md`)

## 7. Naming-surface list: everywhere in the new system that needs unified naming

Word source §2.1 (ADR-0053 decisions 1.3, 4). ✔ = a guard for the original format exists (it does not read concepts / designations or the new projection, §2.1); ○ = to be built or partly covered. Historical owners are one-glossary, one-architecture and one-quality (decision 7). In the Lab, the existing design resident owns concept and format decisions; platform owns guard tooling and the surface-owning resident supplies evidence.

| # | Naming surface | Rule | Guard | Status |
|---|---|---|---|---|
| 1 | repository, module, Java package | Product repository name `forcitis-one-psa`; personal Lab checkout identity follows the [Lab workflow](../ops/codex-lab-workflow.md). Packages: `com.forcitis.one.<subdomain>.<BC>.{api,events,domain,application,infrastructure,web}` | G1, D1 | ✔ |
| 2 | class, record, enum, method | English industry words; no vague words, old names, pinyin, `fst`, `cn.iocoder` | G12–G15, G26, G37, G38, G40 | ✔ |
| 3 | domain event | past-tense fact (`TimesheetSubmitted`) | G5, G29, G37 | ✔ |
| 4 | value object, typed ID | `Money`, `Hours`, `TimesheetId` | G16, G17, G21–G23, G31 | ✔ |
| 5 | database schema, table, column, constraint, comment | §2.1.4, §2.3–2.4; comment compared with the concept's `zh-CN` preferred name (ADR-0053 supersedes ADR-0018 decision 3) | D2, D3, G37, G39 | ✔; new-designation comparison planned (before switch) |
| 6 | Flyway file name | `V{timestamp}__{module}_{english_snake}.sql` | D2, G44 | ✔ |
| 7 | HTTP path | `/api/v{n}/{subdomain}/{kebab plural resource}[:{action}]` | G20, G41 | ✔ |
| 8 | JSON field, OpenAPI schema, event payload | camelCase; schemas PascalCase; split words registered | G42 | ✔ |
| 9 | permission code | `{subdomain}.{BC}:{resource}:{action}` | G28, G43 | ✔ |
| 10 | business-number rule code | upper snake case (`QUOTE`), registered | G45 | ✔ |
| 11 | scheduled job, lock, `job_type`, metric, config key | `{subdomain}.{BC}.{name}`; config prefix `forcitis.{subdomain}.{BC}.` | G46 | ✔ |
| 12 | BPMN process, DMN decision, user task | key prefixed by subdomain / BC; task id camelCase | G19, G47 | ✔ |
| 13 | error code | `{SUBDOMAIN}_{BC}_{REASON}` | G48 | ✔ |
| 14 | frontend PC directory, file, component, hooks | `modules/<m>`; kebab-case files, PascalCase components, `useXxx` | F1–F8, F13 | ✔ |
| 15 | design tokens | only master semantic token names (`--surface-*`, `--status-*`) | F14 | ✔ |
| 16 | UI text and message keys (PC, H5, server, print, reports, notifications) | §2.1.2–2.1.4, `docs/glossary/glossary-schema-design.md` §3 | `check-copy-glossary.mjs / OutwardTextGuardTest` (old wording) | ○ catalog, role, placeholders, completeness planned |
| 17 | H5 (Taro) | same rules as PC | none | ○ equivalent lint before Phase 2 |
| 18 | `reporting` views and BI columns | view `rpt_<topic>`, columns match the glossary `snake` | none | ○ before Phase 5 |
| 19 | external channels and anti-corruption layer | system names `wecom`, `seeyon`, `u8c`, `fenbeitong`; external fields only inside the ACL | G1 | ○ name-convention guard |
| 20 | log fields, trace attributes | camelCase keys, business keys from the glossary (guiding-principles §11 O-1) | G-O1, G-O3 (planned) | ○ |
| 21 | Git branch, commit, PR | Lab work branches use `codex/<session>-<card>-<short>`; verify the resident ID and worktree separately (`docs/rules/rules.md` ③); titles `type(scope): 中文摘要` per CS-7.8 (PR and commit end `（#<issue>）`), scope the subdomain slug; PRs and document collections per TEAM-40, 53 | review | ○ |
| 22 | concept, designation, usage role, language | §2.1.1–2.1.3 | `term_rules.py / GlossaryTest` (old structure) | ○ new schema, roles, status combinations planned |
| 23 | verb, aspect marker, event composition, template | §2.1.5, `docs/glossary/glossary-schema-design.md` §2 | existing event word-form guard | ○ forward rendering planned |
| 24 | PostgreSQL identifier length and abbreviation | §2.1.4 N3-L1–L3 | `DdlNamingTest` (N3-L1 length) | ○ abbreviation and collision checks planned |

Known blind spots of G41–G48: runtime-assembled metric names; config keys in `.yml`; Flowable `candidateGroups`; DMN files and the `job_type` table (injected cases only); test-data migration not bound by G44; event keys checked via the application's JSON mapper; an error code written as a plain string.
