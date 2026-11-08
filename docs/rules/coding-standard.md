# Coding standard details — FORCITIS one

Core page: `docs/rules/coding-core.md`. Look rules up by id (CS-x.y). "(new)" = this file is the source; a rule or part marked "(new)" takes effect only after user approval (§10 Q-1).

## 0. Scope and precedence

**Scope**: all new code (backend, migrations and SQL, `apps/web`, `apps/h5`, tests, CI and delivery scripts), people and agents alike. Naming and DDL: `docs/rules/naming-standard.md` (NAMING_STANDARD); data design: `docs/rules/data-design-checklist.md` (DATA_DESIGN_CHECKLIST); where a row summarizes one of them, that source governs. Process: `CLAUDE.md`, `docs/rules/rules.md`, `docs/rules/playbooks/`; review: `scripts/review/PROMPT.md`.

**Precedence**: the user's own words (`docs/decisions/`) > adopted ADRs > specs, rule cards, `contract.md` > this file, NAMING_STANDARD, DATA_DESIGN_CHECKLIST > existing code. Code that disagrees with this file is a defect, fixed by a defect card or refactor batch (CS-7.3).

**Check column** (grammar of `scripts/guards/check_rules_enforced.py`): parts separated by ";", notes in (parentheses): `Guard: <enforcers>` (NAMING_STANDARD §4 guard ids or `backticked` tests, scripts, ESLint rules, `pnpm` scripts, CI jobs); `planned: <ids>` (designed in §8 or NAMING_STANDARD §4; review covers it until built); `manual: review` (first-round review per `scripts/review/PROMPT.md`).

## 1. General

| # | Rule | Check |
|---|---|---|
| CS-1.1 | **No code comment if avoidable**: one line only for an unseen "why"; no restatement, history, author/date, commented-out code, TODO without number; a comment contradicting code is must-fix | manual: review |
| CS-1.2 | **Legacy code is evidence only, never copied** (no snippets, structure, paths, names such as `psa_`, `system_`, `bpm_`, `/admin-api/`, yudao, pinyin abbreviations); equivalence shown by tests named by RULE-id; exceptions only approved anti-corruption layers | Guard: `LegacyShapeTest`, G40; manual: review (specifically for "legacy shape") |
| CS-1.3 | **No external or legacy system names in domain modules**; only in `platform.integration` adapters | Guard: `LegacyShapeTest`, `ExternalConnectionIsolationTest`, G1 |
| CS-1.4 | **Glossary first**: look up identifiers, UI text, table/column names in `docs/glossary/terms.yaml`, else register a proposed term at once (ADR-0018) with usage scope and bc, kind only DDD tactical categories, event = aggregate name + past-tense business verb, command = imperative verb, the same name in different bcs registered separately (`<bc>__` prefix, listed as homonyms), error-code Chinese names globally unique; business wording follows what the user or finance confirmed and no unconfirmed accounting-type proposal is left at a phase exit; rename PR merges before forbidding merges (forbidding first needs allow-list with expiry and owner); dialects and homonyms go to GitHub Issues | Guard: `GlossaryTest` (stock in `backend/glossary/code-term-baseline.txt` may only shrink), `ErrorCodeGlossaryTest`, G48, `scripts/guards/term_rules.py`, `pnpm check:copy` (frontend) |
| CS-1.5 | **Naming and location follow NAMING_STANDARD**; no merge while a guard fails | Guard: G1–G33, G37–G48, D1–D3, F1–F14; planned: G34–G36 |
| CS-1.6 | Replicate legacy behavior on read-only production evidence, not guesses | manual: review |
| CS-1.7 | **Missing or invalid input fails and alerts, never skipped silently** (unless a rule card allows) | Guard: `CurrentUserGuardTest` (legal entity not hard-coded or defaulted); manual: review (the rest) |
| CS-1.8 | Implement only what the current phase needs | manual: review |
| CS-1.9 | What can be done right now is not deferred; deferring needs "why later is worth more" written | manual: review |
| CS-1.10 | **Contract first**: a changed interface path, required field or table name updates OpenAPI and architecture document in the same commit | Guard: `api-contract`, `pnpm verify` (frontend); manual: review (PR template); planned: G-C1 (cross-version compatibility) |
| CS-1.11 | **Strong types**: typed IDs, value objects, state enums; no `Object` / `Map<String, Object>` in contracts, no `any` in frontend | Guard: G16, G17, G21–G24, G31, G32, F7 |
| CS-1.12 | **Narrow modules**: only `api` and `events`; total `api` methods ≤ 12; frontend module exports ≤ 8 | Guard: G1–G4, G30, F1–F4, F12, F13 |
| CS-1.13 | Generated code only in `generated/`, never hand-edited | Guard: `pnpm check:generated` |
| CS-1.14 | **System names**: FORCITIS one (new), 「老系统」 / the legacy system (old); legacy identifiers and the user's words stay as they are | manual: review |
| CS-1.15 | One top-level type per file; file over 1000 lines must be fixed; aim ≤ 400 per file, ≤ 60 per method (suggestion) | Guard: `scripts/guards/file_lines.py` (1000 lines); manual: review (one top-level type; ≤ 400 / ≤ 60 advice) |

## 2. Java / Spring (DDD in practice)

### 2.1 Structure and boundaries (summary)

| # | Rule | Check |
|---|---|---|
| CS-2.1 | Package `com.forcitis.one.<subdomain>.<BC>.{api,events,domain,application,infrastructure.persistence,web}`; suffixes per NAMING_STANDARD §2.2 | Guard: G6–G15, G26 |
| CS-2.2 | One class per use case, verb-object name, single public `execute` | Guard: G27; manual: review (the entry name `execute`) |
| CS-2.3 | **Transactions only in application; one service writes one aggregate's repository**; exceptions in `backend/architecture/multi-aggregate-exceptions.txt` with reason | Guard: G11, `SingleAggregateGuardTest` |
| CS-2.4 | **State machine inside the aggregate**: state changes only via domain methods, illegal transition throws | Guard: `StateWriteGuardTest`, `RepositoryWriteGuardTest`, G16 |
| CS-2.5 | **Events come from the aggregate**: immutable past-tense record, not ending Event / Request / Command, only IDs and value objects, in the event catalog; business date from parameter or aggregate state | Guard: G5, G29, `EventBusinessDateGuardTest` |
| CS-2.6 | **Concurrency**: mutable aggregate has `version` or a precondition update | Guard: `RepositoryWriteGuardTest` (an unconditional write to a version table must be registered) |
| CS-2.7 | **Aggregates reference each other by typed ID**; no bare `String` / `long` across modules; cross-module documents use `kernel.DocumentReference` | Guard: G31, `DocumentReferenceGuardTest` |
| CS-2.8 | **Read models separate from commands**: projections via a `*Queries` port | Guard: `ReadModelGuardTest` |
| CS-2.9 | **Authorization**: every interface method has `@PreAuthorize` or `@PublicEndpoint(reason)`; no caller identity in request records; only identity configures Spring Security | Guard: G28, G33 |
| CS-2.10 | No field injection, `System.out`, JUL, `java.util.Date`, `@PermitAll`, `BigDecimal.equals`, `new BigDecimal(double)`, `Fix*` / `Demo*` / `Mock*` / `Test*` / `Sample*` in main code | Guard: G18, G24, G26 |
| CS-2.11 | Workflow engine only in `platform.approval.infrastructure`; accounting subjects only in `accounting.ledger`; posting only via `operations.facts` | Guard: G19; planned: G34–G36 |
| CS-2.12 | Text shown to people only in `*Texts` classes; every segmented Chinese word is a registered term or allowed non-business word | Guard: `OutwardTextGuardTest` |
| CS-2.13 | Responses do not expose `SENSITIVE` / `RESTRICTED` columns directly | Guard: `SensitiveColumnExposureGuardTest` |

### 2.2 Language-level rules (new)

**CS-2.20 Output ID value objects with `value()`** (new): a typed ID written to a response, event, log key, cursor, audit detail or SQL parameter uses `id.value()`, never `String.valueOf(id)`, `toString()` or concatenation; a response record's ID field uses the ID's value type directly (the contract is what springdoc actually outputs; version upgrades go through an ADR); errors are RFC 9457 problem details (ADR-0041).

Check: planned: G-CS1

**CS-2.21 No business order by ID order** (new): order by a sequence column or business time with `id` only as tie-breaker (NAMING_STANDARD §2.4a item 2); no `ORDER BY id` for "earlier/later", `max(id)` as "latest", comparing IDs, tests asserting ID order.

Check: planned: G-CS2; manual: review

**CS-2.22 Value objects, records, immutability** (summary + new): value object = `@ValueObject` record validated in its compact constructor (`IllegalArgumentException` = programming error; input is validated at the web boundary); records copy collections with `List.copyOf` / `Set.copyOf` / `Map.copyOf`, no arrays, no `Date`; aggregates and entities are not records, `private` fields, no setters; `api` / `events` hold only interfaces, records, enums, typed IDs; no mutable `public static`.

Check: Guard: G2, G5, G21, G22, G32; planned: G-CS3 (collection copy); manual: review (the rest)

**CS-2.23 Null handling** (new): non-null by default in domain, application, api (`Objects.requireNonNull(x, "x")`); "may find nothing" returns `Optional<T>`, return-value only (response records excepted); collections and pages return empty, never `null`; "no value" in a response record is `@Nullable` from `org.jspecify.annotations`, `org.springframework.lang` annotations banned.

Check: Guard: `NullableResponseGuardTest` (boxed-type components must be `@Nullable`), `LegacyApiGuardTest` (LA-9); planned: G-CS3 (the rest)

**CS-2.24 Exception classification** (summary + new): business rejection throws `kernel.DomainException` (or subclass) with the module's `kernel.ErrorCode` enum (`{SUBDOMAIN}_{BC}_{REASON}`) and an `ErrorKind`; **common error codes only in `kernel`**; messages are for logs and no sensitive value (CS-6.2); the caller sees only the error code, the kind and extension fields the exception declares displayable; web-boundary input failure uses `kernel.InvalidRequestException` (400); programming errors use `IllegalArgumentException` / `IllegalStateException` / `NullPointerException`, never business rejection; unavailable external system uses `kernel.ExternalSystemUnavailableException`; no swallowed exception, no empty `catch`.

Check: Guard: G48, `ErrorCodeGlossaryTest`; planned: G-CS4 (no new `RuntimeException`, empty `catch`); manual: review (the rest)

**CS-2.25 Transaction boundary**: `@Transactional` only on application use cases, one aggregate each (CS-2.3); no external call, HTTP or batch computation inside (use the outbox); business numbers allocated in the use case's own transaction.

Check: Guard: G11, `SingleAggregateGuardTest`; manual: review (external calls inside a transaction)

**CS-2.26 Time only through an injected `Clock`**: no `now()`, `Clock.system*`, `System.currentTimeMillis` in business code; domain holds no `Clock`, application passes instant and business day (`kernel.BusinessZone`); SQL and migrations do not read the database clock (NAMING_STANDARD G25, §2.4a item 3; R-4). Exception: Spring Session table expiry via `identity.infrastructure.persistence.SessionStoreClock` until C1.

Check: Guard: `TimeSourceGuardTest`, `EventBusinessDateGuardTest` (migration-file scanning comes with R-4)

**CS-2.27 Money only through `Money`** (K-1 in progress): amount = number + currency; no bare `BigDecimal` amount outside kernel, no `double` / `float`; rounding only in the money kernel; splitting conserving, cross-currency arithmetic rejected; on the interface a decimal string plus currency (G-M5); quantities use value objects such as `Hours`.

Check: Guard: G17, G23, G24 (the new K-1 guards ship with K-1); manual: review (the rest)

**CS-2.28 Injection and visibility** (summary + new): constructor injection (no `@Autowired` on a single constructor), `private final` fields; web and infrastructure classes package-private, others too unless used from another package; configuration via `@ConfigurationProperties("forcitis.<subdomain>.<bc>…")` records; no static mutable singletons or config/time reads in static initialization.

Check: Guard: G6, G18, G46; manual: review (default package-private, no static mutable state)

**CS-2.29 Domain does not reference external-system types** (new): `Source*` types of `platform.integration.api` appear only in `platform.integration` and the consuming module's anti-corruption translation point, never in aggregates, entities, value objects, `api`, `events`. Translation point: §10 Q-4.

Check: planned: G-CS5

## 3. SQL and migrations

| # | Rule | Check |
|---|---|---|
| CS-3.1 | Naming of schema, table, column, constraint, index, Flyway files (NAMING_STANDARD §2.3, §2.4) | Guard: `DdlNamingTest`, `NamingSurfaceTest`, `GlossaryTest` |
| CS-3.2 | Ten DDL design items of NAMING_STANDARD §2.4a | Guard: `DdlNamingTest`, `DataArchitectureTest` (per item); manual: review (the rest) |
| CS-3.3 | **Data design follows all of DATA_DESIGN_CHECKLIST §14.1** | Guard: `DataArchitectureTest`, `DdlNamingTest`, `PrimaryKeyTypeGuardTest`; manual: review (the rest) |
| CS-3.4 | **Own tables use a UUIDv7 key** from `kernel.IdGenerator`; external IDs only as source-reference columns, mapped to own enums in the anti-corruption layer | Guard: `PrimaryKeyTypeGuardTest` |
| CS-3.5 | **Migration baseline** (ADR-0038): while marker `backend/architecture/migration-baseline-open` exists, edit the one baseline per schema and attach an "expected table-structure diff" (`pg_dump --schema-only` of two empty DBs in hosted CI, independently reviewed by Codex); a separate PR removes the marker before first production deployment, then migrations are append-only | Guard: `scripts/guards/migrations_immutable.py`; manual: review (diff) |
| CS-3.6 | SQL only in `Jdbc*Repository` in `infrastructure.persistence` (`JdbcClient`, hand-written mapping, no ORM), own schema only; classes and tables registered in `architecture/data-access-registry.yaml` | Guard: G10, `DataArchitectureTest`, `WorkforceSchemaGuardTest` |
| CS-3.7 | **Bind parameters** (`:name`); dynamic sort columns and table names only from a whitelist enum (new) | Guard: `scripts/guards/sql_values.py` |
| CS-3.8 | SQL does not read the database clock; DDL writes no clock defaults (R-4) | Guard: `TimeSourceGuardTest` (SQL strings; migration scan comes with R-4) |
| CS-3.9 | **Posted facts are never changed or deleted**, audit and version tables are append-only; correct only by reversal: a reverse fact with a new posting sequence number, original row kept and linked both ways, redone on the correct basis; a correction after close lands in the open period marked with the original period; new config versions, rule corrections and data migrations never write back; a correction after close or period reopening needs finance approval (ADR-0016 item 2) | Guard: `DataArchitectureTest` (scorecard D1: append-only triggers on log and version tables); planned: G34 |
| CS-3.10 | List queries only index-covered cursor pagination (`cursor` + `limit`, default 50, max 200), no in-memory paging or full scan; sort key falls back to `id` (CS-2.21) | manual: review (EXPLAIN spot check) |
| CS-3.11 | Database-creation scripts (C-3, C-9) agree with the reference DDL and migration schema list | Guard: `scripts/guards/db_scripts_aligned.py` |
| CS-3.12 | No analytics on transaction tables; summaries and trends via `reporting` (ADR-0025) | manual: review |
| CS-3.13 | **SQL sorting by Chinese writes `COLLATE "zh-x-icu"`** (database default is C.UTF-8), also in a keyset cursor comparison and its index | manual: review (no reliable static check: SQL text does not say which sort key holds Chinese; K-3a ruling item 5) |

## 4. Frontend (React + TypeScript; H5 is Taro)

| # | Rule | Check |
|---|---|---|
| CS-4.1 | **Strict types**: `strict`, `noUncheckedIndexedAccess`, `exactOptionalPropertyTypes`, `verbatimModuleSyntax`; no `any`; no non-null `!` (new) | Guard: `pnpm typecheck`, F7; planned: G-CS7 (non-null assertion) |
| CS-4.2 | **Interface calls only via the generated client** (no axios, `fetch`, `XMLHttpRequest`, hand-written `/api` URLs); only `modules/<m>/api` imports `@forcitis/api` | Guard: F5, F10, `pnpm check:generated` |
| CS-4.3 | Modules: `app → modules(index) → shared`; across modules only via `modules/<m>/index.ts`; no `export *`; `model/` and `shared/lib` pure | Guard: F1–F4, F9, F12, F13, `pnpm check:structure` |
| CS-4.4 | ASCII identifiers, kebab-case files, PascalCase components, hooks `useXxx` in `hooks/` | Guard: F6, F8, `pnpm check:structure` |
| CS-4.5 | **UI text from the glossary** (`zh` in `docs/glossary/terms.yaml`); non-business words in `frontend/scripts/copy-allow-list.yaml` | Guard: `pnpm check:copy` |
| CS-4.6 | **Errors only via `describeFailure`** of `@forcitis/core`; every `ErrorCode` in `error-messages.ts` has text or explicit `null`; pages never show server `detail`, exception message (new) or English; failures show an actionable message | Guard: `pnpm typecheck` (`Record<ErrorCode, …>` exhaustive); planned: G-CS7 (pages reading message directly) |
| CS-4.7 | No direct antd or Taro primitives in business modules, no inline styles or hard-coded colors; tokens read only in `packages/design-system` (ADR-0013) | Guard: F14, `frontend/stylelint.config.js` |
| CS-4.8 | No `dangerouslySetInnerHTML`; external links via `safe-url` (SEC-008) | Guard: F11; manual: review (`safe-url`) |
| CS-4.9 | No `console.log`, use the shared logger | Guard: `no-restricted-properties` |
| CS-4.10 | Date-time and business day only via `format-date-time` and `business-day` of `@forcitis/core`; money formatting via the K-1 function (G-M6) | manual: review (G-M6 comes with K-1) |
| CS-4.11 | H5 and PC follow the same rules | Guard: F14 (H5 already wired); manual: review (the rest) |
| CS-4.12 | **`localeCompare` carries `'zh'`, `Intl.DateTimeFormat` carries `timeZone`** | Guard: `no-restricted-syntax`, `pnpm check:lint-config` |

## 5. Testing

| # | Rule | Check |
|---|---|---|
| CS-5.1 | **Module-level integration tests first** (HTTP → authorization → use case → domain → real PostgreSQL via Testcontainers); no mocks of own modules, external systems via WireMock or stand-in; unit tests only for boundaries | manual: review |
| CS-5.2 | **Test names cite a basis number**: `@DisplayName` starts with a rule / difference / ADR / review / intent number; every `contract.md` item and ADR clause delivered maps to a test | manual: review (`scripts/guards/rule_tests.py` maps business rules to tests) |
| CS-5.3 | **Acceptance tests separate from implementation**: one-test freezes the tests-only head before implementation; a read-only author may supply a patch to lead with author identity preserved; the implementer does not modify the assertions | Guard: `scripts/guards/acceptance_guard.py`, `acceptance_rulings.py`, `acceptance_reports.py` |
| CS-5.4 | **Never loosen or delete an assertion to pass**; a needed change lists its reason in report and PR | manual: review (PR template) |
| CS-5.5 | A test asserts only this module's effects | Guard: `TestIsolationGuardTest` |
| CS-5.6 | **Deterministic waiting**: bounded deterministic wait for async, no broad `never()`; no `Thread.sleep` (new) | Guard: `LegacyApiGuardTest#NO_THREAD_SLEEP`; planned: G-CS8 (broad `never()`) |
| CS-5.7 | **Tests contain no secret literals** | Guard: `gitleaks` (pre-commit, CI) |
| CS-5.8 | (new) Tests use a fixed `Clock` and `IdGenerator`, not runtime instant or ID order | manual: review |
| CS-5.9 | **A new guard carries negative samples** (injects an error, is blocked, message says where to put it / what to name it); every exception-list line has a reason and a new exception needs review | manual: review |
| CS-5.10 | **Review produces failing tests** for Blocker / High / Medium where possible, named by the review number | manual: review |
| CS-5.11 | Use GitHub-hosted Actions for affected checks; at the integration head run applicable guards and full backend verification when required (CS-7.6) | Guard: `scripts/ci/light.sh` |

## 6. Logging, security and secrets

| # | Rule | Check |
|---|---|---|
| CS-6.1 | SLF4J with ECS JSON, context has `correlationId` and `userId`, keys camelCase from the glossary; no `System.out`, JUL | Guard: G18; manual: review (key names) |
| CS-6.2 | `RESTRICTED` (passwords, keys, session content) in no log or exception message, never leaves the database; `SENSITIVE` (personal data, salary, daily rate, bank card): logs record only the ID | manual: review; planned: G-CS9 |
| CS-6.3 | Tests contain no secret literals (same as CS-5.7) | Guard: `gitleaks` (pre-commit, CI) |
| CS-6.4 | **Secrets never enter session records** | manual: review |
| CS-6.5 | Credentials stay out of code, logs and sessions. The personal Lab uses the job token and approved GitHub Actions secret injection; the legacy production secret sources remain outside Lab | Guard: `gitleaks`; manual: review |
| CS-6.6 | Identity, authorization, single-person-mode defenses follow I-1…I-13 of `docs/ops/security-invariants.md`; such changes add a final review by a different existing Codex resident | Guard: `SecurityInvariantsTest`; manual: review (independent final review) |
| CS-6.7 | Production configuration has no test values, fixed verification codes or mock values (I-4) | Guard: `ProductionConfigSanityTest` |
| CS-6.8 | Use approved GitHub-hosted containers for the card's tests. New accounts, external services or production calls need separate authorization through lead | manual: review |
| CS-6.9 | The historical production evidence rule (ADR-0037 item 7) does not authorize Lab production access; use approved evidence already in the Lab | manual: review |
| CS-6.10 | **Security gate**: use the assigned Lab worktree and the mandatory, version-pinned GitHub-hosted Gitleaks job. Local hook state is not CI evidence | Guard: `gitleaks` (pre-commit, CI) |

## 7. Pre-delivery self-check and PR

| # | Rule | Check |
|---|---|---|
| CS-7.1 | **Before release collect exact-head hosted tests and `scripts/ci/light.sh`**; a tests-only draft may record the expected red. State actual PASS/FAIL/SKIP and missing checks | manual: review (PR template) |
| CS-7.2 | Fill every field of `.github/pull_request_template.md` truthfully | manual: review |
| CS-7.3 | **Defect fixes, feature completion and structural refactors (including renames) are dispatched separately, never mixed.** A defect fix is surgical: one card = one minimal change plus one regression test that fails first; only card files, report before going beyond (a defect card names review number, reproduction steps, expected changed files), small PRs merged once review passes, no accumulating big integration branches, no drive-by refactor or rename; a defect not fixable locally is marked "structural" and becomes a refactor | manual: review |
| CS-7.4 | **Record the user's spoken decisions verbatim with `python3 scripts/session/new_intent.py`**; never name the file by hand; output is not committed or edited | manual: review |
| CS-7.5 | **Change-governance self-check** (unsure: stricter, config < code < ADR): an adopted ADR changes only its status line (`docs/adr/readme-lifecycle.md`), a new ADR carries the required sections; code class (domain rules, calculations, validation, state machines, DDL): first append a draft conclusion to `revisions` in `docs/business/rule-reviews.json` (original fields unchanged, the last is current), after user confirmation change tests named by rule ID (RULE-NNN), last the implementation; config class (DMN, business numbers, operating basis, policy parameters; business basis approved by the user, accounting basis by finance): register version and effective date in `docs/ops/config-changelog.md`, versions append-only, effective by a date aligned with the accounting period, no retroactive effect, no back-dating, no recomputation (`docs/rules/playbooks/design.md` step 9) | manual: review |
| CS-7.6 | Authorized parallel work uses the same approved baseline; GitHub-hosted Actions reruns applicable guards on the integration commit | manual: review |
| CS-7.7 | **Review and enqueue**: existing independent Codex residents review the exact head per `scripts/review/PROMPT.md`; guarded changes use a different final reviewer. Lead admits through `mq.py` once hosted evidence is complete. Do not push after admission or use GitHub auto-merge | Guard: `mq.py`; manual: review (lead releases must-fix fixes) |
| CS-7.8 | (new) Titles of issue, PR, commit (a card's issue and PR use the same short name; old titles are not rewritten): `type(scope): 中文摘要`; type from the card's 类型 (impl→feat, bugfix→fix, docs/design→docs, ops→ci or chore, acceptance→test, investigation→spike), scope the subdomain or module slug, summary ≤ 30 characters stating the result, identifiers as they are. PR and commit titles end `（#<issue>）`; issue titles carry no self-made card id (user 2026-10-30 「同意」) | manual: review (check: card in the ops queue) |
| CS-7.9 | **Legacy IntelliJ troubleshooting, not a Lab verification path**: IntelliJ does not read `backend/.mvn/jvm.config` (Error Prone fails with `IllegalAccessError`): add the same `--add-exports` / `--add-opens` to Shared build process VM options, or build with Maven (ADR-0033 guard 14) | manual: review |
| CS-7.10 | Drafts under `proposals/` are not authoritative; an ADR cites only the intent number, never the user's words | manual: review |

## 8. Planned guard designs (G-CS series; distinct from naming-standard's G-C series (ADR-0035 contract guards))

Design only; each ships in its own PR with negative samples (CS-5.9), existing violations in a baseline file like `ddl-naming-baseline.txt`.

| Id | Rule | Implementation points |
|---|---|---|
| G-CS1 | CS-2.20 ID output uses `value()` | ArchUnit over `web`, `application`, `infrastructure`: `String.valueOf`, `toString()`, `StringBuilder.append`, concatenation on an `@ValueObject` record with a single component `value`; OpenAPI export has no `[value=` example, event JSON has scalar IDs |
| G-CS2 | CS-2.21 No reliance on ID order | SQL scan in `infrastructure.persistence`: **first** `ORDER BY` key `id` or `*_id`, `max(id)` / `min(id)`; ArchUnit: `compareTo` on a typed ID or `UUID`, or them in `Comparator.comparing`, in domain / application |
| G-CS3 | CS-2.22, CS-2.23 immutability and nulls | ArchUnit: `List` / `Set` / `Map` record component needs `copyOf` in the compact constructor; no `return null` in domain / application / api (`@Nullable` excluded); `Optional` not a field, parameter or collection element (`web` response records excepted); no `org.springframework.lang.Nullable` |
| G-CS4 | CS-2.24 exceptions | ArchUnit: no `new RuntimeException` / `new Exception` in domain / application; non-kernel exceptions in business modules are `DomainException` subclasses or JDK programming-error classes; source scan for empty `catch` |
| G-CS5 | CS-2.29 domain does not reference external types | ArchUnit: except `platform.integration..` and registered translation packages, no `..domain..`, `..api..`, `..events..` depends on `platform.integration.api.Source*`; exceptions in `backend/architecture/source-type-exceptions.txt` (new, may only shrink) |
| G-CS7 | CS-4.1, CS-4.6 frontend | ESLint `@typescript-eslint/no-non-null-assertion: error`; `no-restricted-syntax` forbids `.message` / `.detail` on a `catch` parameter or `error` in `pages/` and `components/`, hint `describeFailure` |
| G-CS8 | CS-5.6 deterministic waiting | Scan `src/test/java`: `never()` only with a specific method and argument matchers; `verifyNoInteractions` / `verifyNoMoreInteractions` need a registered reason |
| G-CS9 | CS-6.2 logs contain no sensitive values | ArchUnit: `Logger` `info/warn/error/debug` arguments must not hold a type or field hitting a `SENSITIVE` / `RESTRICTED` column in `data-classification.txt`; integration test with login and sync using a fixed sensitive value, asserting the log lacks it |

## 9. Conflicts and duplicates

(Removed: history, see git history.)

## 10. Open questions

| Id | Question | Recommendation |
|---|---|---|
| Q-1 | Whether to adopt the rules marked "new" (the new parts of CS-2.20–CS-2.29, CS-3.7, the non-null assertion in CS-4.1, pages not reading message in CS-4.6, the `Thread.sleep` ban in CS-5.6, CS-5.8, the format in CS-7.8) | Recommend adopting; record an intent after adoption |
| Q-2 | Accept the precedence order in §0 | Accept |
| Q-4 | Where the `Source*` translation of `SourceTerms`, `SnapshotReconciler`, `EmployeeChecks` in `organization.workforce.domain` goes | Answered by ADR-0039 item 3: translation is done in the integration layer; business modules have no translation package |
| Q-6 | Coverage threshold (lines ≥ 80%, branches ≥ 70%, `docs/architecture/guiding-principles.md` §9) is unchecked, CI runs `-Djacoco.skip=true` | Pending the user |
| Q-7 | Unverified: Testcontainers 2 `PostgreSQLContainer` package, Security 7 dropping `.and()` and `AntPathRequestMatcher`, Awaitility | After #810, into table D of the core page |
