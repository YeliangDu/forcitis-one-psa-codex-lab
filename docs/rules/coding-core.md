---
paths:
  - "backend/**"
---

# Coding core — FORCITIS one

Loaded for `backend/**` via the `.claude/rules` symlink (#827).
CS-x.y is in `docs/rules/coding-standard.md`, which wins on conflict.

## A. Golden files — copy these

"now:" = known deviation, #812 fixes it.

| Part | File | Pattern |
|---|---|---|
| Use case | `SubmitTimesheet:65` | `@Transactional` on execute, one aggregate, save then publish |
| Caller | `SubmitTimesheet:66` | inject `CurrentUser`, never take `UserId caller` (now: 3 other modules) |
| Data scope | `GetOrgUnit:31` | inject `OrgUnitAccess`; out of scope = not found + audit (now: a parameter) |
| Controller | `OrgUnitController:33` | convert and call the use case; return a `*Response` record on 200, `ResponseEntity` only for other status or headers (now: computes scope, takes caller) |
| Repository | `JdbcRoleRepository:117` | text-block lowercase SQL, named params, versioned update |
| Listener | `ApplyApprovalResult:41` | `@ApplicationModuleListener` in application, dedupe first |
| Time | `JdbcRoleRepository:347` | `Instant` + `BusinessZone`; JDBC `OffsetDateTime`, not `Timestamp` |
| ID | `CreateLegalEntity:29` | inject `IdGenerator` |
| Money | `ExactMoney:25` | compute in `ExactMoney`, unrounded; `round()` to `Money` HALF_UP to the minor unit; total = sum of rounded lines (FD-02/07/08) |
| Workflow | `FlowableApprovalEngine:54` | Flowable only in the adapter; callers use `ApprovalProcesses` |
| Unit test | `TimesheetStateMachineTest:62` | no Spring, fixed date |
| IT / acceptance | `AuditExportAndOwnRecordsIT:36`, `WorkCalendarMakeUpWeekendIT:38` | MockMvc + `TestPostgres.database`; `@DisplayName` starts with the card or rule id |

## B. Rules no test catches — follow them

1. Every query and list filters by data scope, fail-closed: empty scope = zero rows, unknown scope = deny, reading an id out of scope = 404 (`docs/ops/security-invariants.md` I-10).
2. Rounding happens once, in the money kernel; domain code never calls `setScale`, `divide` or picks a `RoundingMode`; weighted splits use `prorate`, conserving, largest remainder (CS-2.27, XD-08).
3. API money is `{amount: decimal string, currency: ISO 4217}` (XD-01); floats lose cents.
4. Aggregates that post or face the legal-entity × period gate carry `LegalEntityId` and `AccountingPeriod`; unknown source = fail. Config versions take effect on a period-aligned date, never retroactively (CS-7.5).
5. Inside a transaction: no outside calls, HTTP or batch computation (use the outbox); document numbers come from `platform.numbering` `DocumentNumbers` here (CS-2.25).
6. Parse external input once at the web edge (`kernel.InvalidRequestException` → 400), then trust the types, no repeated null checks (CS-2.24).
7. Missing or invalid input fails loudly, never silently skipped unless the rule card says so; no swallowed exceptions or empty `catch` (CS-1.7, CS-2.24).
8. Events: past-tense immutable records of IDs and value objects, published in the same transaction after `save` (`DomainEvents`; CS-2.5). Listeners get redeliveries; make them idempotent (`ConsumedEvents`).
9. `JdbcClient` with named `:params` and explicit columns; never concatenate values into SQL; dynamic sort columns from an allow-list (CS-3.6, CS-3.7).
10. `RESTRICTED` data never in logs or exception messages (CS-6.2). Ask the user before creating accounts, starting containers (not local Testcontainers) or calling outside systems (CS-6.8).
11. Posted facts are never updated or deleted; corrections only by reversal (CS-3.9; guard G34 planned, the modules don't exist yet).
12. Files over 1000 lines must be split; aim for ≤ 400 per file and ≤ 60 per method (CS-1.15).
13. No single-caller pass-through wrappers.
14. Comment only a non-obvious why (constraint, rule id, quirk, trade-off, unit/zone, legacy evidence); a comment contradicting the code is a bug (CS-1.1).
15. Tests: red first (a bug's regression test fails on the base); deterministic waits, no broad `never()`; fixed `Clock` and `IdGenerator` (CS-5.6, CS-5.8, CS-7.3).

## C. Stack: old → new

#810 will enforce these; the pom still has `spring-boot-starter-web` until it does.

| Don't | Use |
|---|---|
| `javax.annotation`, `javax.inject` (not JDK `javax.sql`/`crypto`) | `jakarta.*` |
| `@MockBean`, `@SpyBean`; assuming `@SpringBootTest` brings MockMvc | `@MockitoBean`, `@MockitoSpyBean`; explicit `@AutoConfigureMockMvc` |
| `RestTemplate`, `TestRestTemplate` | `RestClient`, `RestTestClient` |
| `com.fasterxml.jackson.databind`, `ObjectMapper` | `tools.jackson`, `JsonMapper` (annotations unchanged) |
| `org.springframework.lang.Nullable` | `org.jspecify.annotations.Nullable` |
| `spring-retry` | spring-context `@Retryable`; never retry non-idempotent writes |
| JPA, Hibernate | `JdbcClient` |
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `LocalDateTime`, `java.sql.Timestamp` | `Instant`, `LocalDate` |
| `Thread.sleep` in tests; Java preview features | deterministic waits; none |

## D. Guarded — a failing test names the fix

Modules talk only via `api`/`events` · layers: domain no Spring, web → application, JDBC in infrastructure · `@Transactional` only on use cases, one aggregate each · every endpoint `@PreAuthorize` or `@PublicEndpoint` · money only `kernel.Money`, no cross-currency math · audit and version rows append-only · merged migrations append-only (ADR-0038) · ASCII identifiers, terms from `docs/glossary/terms.yaml` · time only from injected `Clock`, stored as `timestamptz`, business date via `BusinessZone.today` · state changes only via aggregate methods · SQL only in `Jdbc*Repository`, own schema, registered · IDs are typed records, never ordered on · status enums, transitions in the aggregate · `POST` idempotency only for registered endpoints · `version` + `If-Match`/`ETag` · must-audit actions registered, no names in detail · `DomainException` + module `ErrorCode` · `Optional` returns, `@Nullable` response parts · events registered in asyncapi · controllers under `/api/v1/` · ITs use real PostgreSQL, assert own module only.
