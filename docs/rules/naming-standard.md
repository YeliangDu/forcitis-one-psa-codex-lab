# Naming, package structure and architecture guards — FORCITIS one modernisation

Date: 2026-10-14. Scope: all new code produced by `reimagine` (backend, PC, H5, database, events, API).

N3 detail update: 2026-10-17, NAM-1017-ARC-04 (rework round 2). The glossary owns word sources and designation structure; one-architecture owns projection, format and the guard list (ADR-0053 decision 7.2).

User's request, in the user's own words:
- 「可以在现代化过程中规范命名吗？将原代码的命名和编码规范化」
- 「命名要符合英文、行业、规范的习惯，agent 开发友好。而且要考虑 archunit 和包结构来保证目录结构不会让 agent 犯错。」
- 「一些基本的原则：深模块、窄接口，要给 agent 约束，避免错代码」

**Conclusion**: the new system is a rebuild (reimagine); old code names and directories are never reused. The standard lands in three layers:

1. **Word source**: every English name comes from the glossary.
2. **Structure**: a fixed package structure decides where code goes.
3. **Guards**: Modulith and ArchUnit make misplaced code, wrong names and boundary crossings fail in CI, with the failure message saying how to fix it.

Related decisions: `docs/adr/ADR-0003.md`, `ADR-0004.md`, `ADR-0005.md`, `ADR-0006.md`. In this file `DATA_DESIGN_CHECKLIST`, `NAMING_STANDARD` and `CODING_STANDARD` are short names of `docs/rules/data-design-checklist.md`, `naming-standard.md` and `coding-standard.md`; tests and ADRs cite them as short name plus section number.

## 0. Basic principles

| Principle | Meaning | How it lands |
|---|---|---|
| **Deep module** | Each module (= one BC) hides complexity inside: rate locking, person-day conversion and row-level approval of timesheets are all done inside `timesheet`; callers see one result | Module internals (domain / application / infrastructure / web) are invisible outside; verified by Modulith |
| **Narrow interface** | A module has exactly two exits: `api` (query or command interface + record) and `events` (records of facts that happened) | `api` holds only interfaces and records; signatures must not contain internal types; each module has ≤ 12 api methods in total (beyond that, deepen the module rather than widen the interface) |
| **Constraints for agents** | Names are located by concept, language and usage (§2.1); code sits at fixed places by responsibility. A failing rule says "why" and "where" | Rule text carries `because(...)`; each module has a `package-info.java` stating responsibility, owned schema and allowed dependencies; the repo has a "where does code go" cheat sheet (§3) |
| **Error-proofing** | Wrong code fails at compile time or in CI, not by review | ArchUnit + Modulith + Checkstyle / Error Prone + tests named by rule id |

## 1. (removed: history; see `git show 59315c148^:docs/rules/naming-standard.md`)

## 2. Naming rules: English, industry-idiomatic, predictable

### 2.1 Word source

This section holds only registration and projection detail. Stable principles, the three-layer model, composition, lifecycle, renaming and multi-language structure cite **ADR-0053 decisions 1, 2, 3, 5, 6** and are not copied here. The word-source entry points to `docs/glossary/terms.yaml` and its merge sources, `docs/glossary/bcs.yaml` and the ACL classification data (ADR-0053 decision 1.3). The old "ubiquitous language" document (no longer in the repo) is only a historical source, not a registration entry for new names. Current term status and meaning follow those sources; examples in this section do not rule on renaming words.

**Sources**: ADR-0053 is `docs/adr/ADR-0053.md`. "Registration plan" is `docs/glossary/arch-naming/naming-atoms-registration-plans.md`; its `naming-atoms-draft.md` §4 (DRAFT) and `naming-atoms-prep.md` "target structure" (PREP) are supplementary sources for candidate fields only. "N3 decisions" is one-glossary's `docs/glossary/arch-naming/n3-decisions.md` (branch `claude/one-glossary-bcs-systems`, commit `f22d6b0b`); the seven items of this round follow it and replace earlier pending wording and inconsistent machine values. Field names follow N3 decisions; migration mapping follows its item 3.

**Guard status**: `GlossaryTest`, `scripts/guards/term_rules.py` and `NamingSurfaceTest` still read the current term-table shape. Guards for the new concept / designation structure, role selection and projection consistency are **planned**: one-quality implements them under later naming cards, one-coordinator schedules them, and they must be in place before the new structure switches; review checks against the sources meanwhile. Sources: ADR-0053 decisions 4, 7 and migration plan items 2, 5; registration plan "landing order and exits".

The glossary schema design that is not yet implemented (concept and designation fields, N3 seven-decision table, alias migration mapping, verb and template registers, message catalog, planned-guard summary) lives in `docs/glossary/glossary-schema-design.md`.

#### 2.1.1 Concept entries and designation entries

Owner: one-glossary. The field-level design (concept fields, designation fields, derived results, N3 seven-decision cross-reference, alias migration mapping) is moved to `docs/glossary/glossary-schema-design.md` §1. Adopted machine values stay here.

**Adopted machine values** (N3 decisions; migration mapping in `docs/glossary/glossary-schema-design.md` §1; absence of a role that is not enabled does not fail):

| Item | Machine value and boundary | Basis |
|---|---|---|
| `locale` | `zh-CN / en / und`; `und` = language undetermined, not an enabled UI language; English is `en`, no region tag distinguishes British / American spelling | migration tool value; N3 decisions item 7 |
| `locale / role` pairing | `zh-CN / canonical`, `en / code_name`; for the Chinese canonical name and the internal English code name respectively | migration tool value adopted |
| `role` | `canonical / code_name` in use; `terse / verbose / column_header / documentation / ui_label` reserved, not enabled, missing registration does not fail | N3 decisions item 1 |
| `usage_status` | `preferred / permitted / forbidden` only | migration tool value; N3 decisions item 3 |
| `approval_status` | `proposed / pending_confirmation / approved` | migration tool value adopted |

Eligibility checks both statuses: `forbidden` or `pending_confirmation` may not be referenced; `proposed` builds a warning per ADR-0053 decision 5.2. Each surface takes the unique `preferred` designation of the specified language and role; `permitted` is never a fallback when none is found.

#### 2.1.2 Usage roles and AN-05

Owner: one-glossary defines roles and designations; one-architecture defines which role each surface takes. The role enumeration follows N3 decisions item 1: `canonical / code_name` in use; `terse / verbose / column_header / documentation / ui_label` reserved, not enabled, missing registration does not fail.

| Usage / name | Registration and use | Source | Guard |
|---|---|---|---|
| Canonical name | `locale: zh-CN`, `role: canonical`; for now also used for Chinese UI text | N3 decisions item 1; ADR-0053 decisions 2.2, 1.5 | planned (before switch) |
| UI name | For now `zh-CN / canonical`, no separate role; when one concept must use a UI wording different from the canonical name, enable `ui_label`; other reserved roles missing does not fail | N3 decisions item 1; ADR-0053 decisions 6.3–4; registration plan §2 | planned (before the message catalog is enabled) |
| Internal English name (code name) | **N3 values adopted**: `locale: en`, `role: code_name`, name is English ASCII | N3 decisions; ADR-0053 decisions 2.2, 6.3 | existing ASCII and word-split checks; role selection: planned |
| English UI name (AN-05) | Registered separately from the internal English name and may differ; even if the word is the same, record it as two usages. No English UI is delivered now, so English UI designations need not be completed | ANM approval record (intent `2026-10-17-084935-one-coordinator`, AN-04, AN-05); ADR-0053 decisions 6.2–3; registration plan §2 | planned (role distinction before switch; English completeness before it is enabled) |
| Abbreviation | A designation expresses an abbreviated form by boolean `abbreviation`, not a role; first register the full name and usage per §2.1.4; abbreviations are not assumed to be permitted aliases | N3 decisions item 2; ADR-0053 decisions 2.2, 8; registration plan §5 | existing word-split check cannot prove an abbreviation is authorised; planned |
| Old names, dialects, aliases | Registered by `source_type` and the two statuses, not as a usage role; mapping in `docs/glossary/glossary-schema-design.md` §1; machine values for old-name sources are `old-code / legacy-code / legacy-table` | N3 decisions item 3; ADR-0053 background "supersedes" and decisions 2.2, 5.3 | existing forbidden-spelling guard; new structure: planned |

#### 2.1.3 Source language and current delivery language

Owner: one-glossary registers languages, frontend and backend sessions use them; one-architecture maintains the projection format.

| Item | Rule | Source | Guard |
|---|---|---|---|
| Source language | `zh-CN`; **N3 values adopted**; language tags follow the rules ADR-0053 decision 6.1 cites | N3 decisions; ADR-0053 decision 6.2; registration plan §2 | planned (tags and source-language registration, before switch) |
| UI language currently enabled (AN-04) | `zh-CN` only, covering PC, H5, server-side human text, print, reports and notifications; no English UI is scheduled this phase | ANM AN-04; ADR-0053 decision 6.4; registration plan §2 | F19 Chinese config, message and designation completeness: all planned |
| English code name | `en / code_name`; does not mean an English UI is enabled, and the UI must not fall back to a code name. Locale is `en`, no spelling variants; British vs American spelling awaits a user ruling (N4 Q13); until then new code spelling follows the N4 candidate | N3 decisions item 7; ANM AN-05; ADR-0053 decisions 1.5, 6.3–4 | planned (before switch) |

#### 2.1.4 Projection from designations to naming surfaces

Owner: **one-architecture**. A projection locates the entry by concept `id`, selects the unique `name` with `locale: en / role: code_name / usage_status: preferred`, and checks approval status per §2.1.1; the Chinese canonical name is selected as `zh-CN / canonical / preferred`. A missing entry, missing required designation, non-unique preferred or unusable status fails; a role not enabled missing does not fail (N3 decisions items 1, 4). Designation selection, composition and missing-handling cite ADR-0053 decisions 1.5, 2.3, 3. The new formats in the table below belong to this section (decisions 4, 8); existing formats still point to their original sections.

| Naming surface | Selection and format | Source | Guard and boundary |
|---|---|---|---|
| Code identifiers | English code name converted by usage: PascalCase types, camelCase fields / methods, UPPER_SNAKE_CASE constants; technical prefixes/suffixes per §2.2; no word-splitting of a concept id | ADR-0053 decisions 2.3, 6.3; registration plan "shared structure and duties", §5; §2.2 | `ArchitectureRulesTest`, `GlossaryTest`; role selection and forward projection: planned |
| schema, table, column, constraint, index | schema generated from the `bcs.yaml` field path below; table name = registered plural form converted to snake_case; column = qualifier + §2.4 class word; constraints and indexes use the §2.4 templates; no separate snake designation | ADR-0053 decisions 1.2, 2.3, 4; registration plan "shared structure and duties", §5; §2.3–2.4 | `DdlNamingTest`, `GlossaryTest`; new structure, controlled plural and length: planned |
| Event name, event JSON key | Events use object and controlled-verb slots rendered to an English code name per §2.1.5; type PascalCase, payload keys camelCase | ADR-0053 decision 3; registration plan §1; §2.2–2.3 | `ArchitectureRulesTest`, `GlossaryTest`, `NamingSurfaceTest` (payload components); slot equality: planned |
| API path and parameters | subdomain code name converted to slug + registered plural form of the resource converted to kebab-case; action from the controlled actions; parameters camelCase; BC does not stand in for subdomain; client generated from OpenAPI | ADR-0053 decisions 2.3, 4; registration plan "shared structure and duties"; §2.3, §2.6 A-1, A-4 | `NamingSurfaceTest` G41 checks paths, `ApplicationIntegrationTest` checks OpenAPI naming; role selection and controlled forms: planned |
| Permission code | `{subdomain}.{BC}:{kebab resource}:{standard action}`; the resource co-refers with the API concept; follow the existing permission catalog in singular, do not copy the API plural string; verbs map to `standard_actions` before use | ADR-0053 decisions 2.3, 4; DRAFT §1 (action mapping is a candidate, must be checked); §4 G43, §7 item 9 | `NamingSurfaceTest` G43 checks existing format, resource and action; concept-reference projection: planned |
| UI text and message key | Business nouns for now take the preferred `zh-CN / canonical` designation; whole messages are registered per `docs/glossary/glossary-schema-design.md` §3. Key = `{subdomain}.{BC}.{singular kebab resource}.{lowercase dotted usage}`; cross-BC shared messages use `kernel.{singular kebab resource}.{lowercase dotted usage}`; maintained separately from business designations | N3 decisions item 1; ADR-0053 decisions 6.3–4; registration plan §2; key format is set by this section | existing `check-copy-glossary.mjs / OutwardTextGuardTest` check only old wording; key, role and placeholders: planned (before new pages adopt it) |

**Schema field path**: match the BC reference exactly against `bcs.yaml` `bcs[].id` and require `kind: bc`; entries with `kind: kernel` are not projected by this path (failing directly is expected), and kernel's schema arrangement follows ADR-0051 decision 2; reference the `kind: subdomain` entry exactly by that row's `subdomain`. The subdomain fragment is the subdomain entry's `designations[].name`, the BC fragment is the BC entry's `designations[].name`; both are selected and status-checked as `en / code_name / preferred` per this section, each converted to snake_case, joined with `_`. `id` and `subdomain` are references only; do not split the string to make a name. A missing field, invalid reference or unusable designation fails, with no fallback to `id`, `zh` or a hand-filled fragment. `bcs.yaml` has no `designations` yet, so the new projection is enabled only after they are filled; passing the current format check does not prove this path is executable.

**Controlled plural**: a table name or API resource must find a registered plural form / template for that concept and naming surface; **unregistered fails, no automatic `s`**, and no automatic English inflection rules. The plural of `Analysis` must not be generated as `Analysiss`; the collective name `Staff` must not default to `Staffs`. These are only counter-examples and do not approve their business use; the plural-registration structure follows the template pending under §2.1.1 (one-glossary decides), and the migration tool must follow once decided.

**Message-key usage**: the usage is one or more `[a-z]+` segments joined by `.`, lowercase dotted, in the same lowercase-segment style as the constants `DOTTED` and `OWN_KEY` in `NamingSurfaceTest`; JSON-field camelCase is not reused. The resource slot stays singular kebab-case, e.g. `cost.timesheet.timesheet.submit.succeeded`; a cross-BC shared example is `kernel.document.version.conflict`. The two existing regexes check job / metric keys and config keys respectively, neither is a message-key guard; full message keys are checked by the format in this section, and `DOTTED` cannot be applied directly because it excludes kebab resources.

**Objects not projected**: value-stream names and value-stage names are business-architecture names, registered only as `zh-CN canonical`, not projected to code identifiers, table / column names, event names or message keys, and do not use the command / event naming templates. Value-stream names use verb-object, result-view wording (e.g. 「获得内部服务」); stage names use verb-object (e.g. 「提交申请」), and the verb must be in the controlled verb table. Source: one-architecture and one-glossary direct line of 2026-10-17 (NAM-1017-ARC-04). Guard: planned (check that value-stream and stage-name verbs are in the controlled table).

**Illustrative examples** (they only explain projection; current entries, status and approved designations in `terms.yaml` prevail; they add no entries or renames; the plural must be separately registered):

| Word-source input | Projection example | Not adopted |
|---|---|---|
| `Timesheet` code name and registered plural | type `Timesheet`, table `timesheets`, resource `/api/v1/cost/timesheets` | other names such as `manhour`, `time_sheet` |
| `LegalEntity` code name + ID technical form | `LegalEntityId / legalEntityId / legal_entity_id` | using `companyCode` for this identifier |
| `Timesheet` object slot + registered event form of `Submit` | `TimesheetSubmitted`; payload key `legalEntityId` | an `Event` suffix on the event, payload key `legal_entity_id` |

**Word-table rename proposals handed to one-glossary**: `ManhourEstimateItem / LaborEstimateLine`, `TimeEntry / TimesheetLine`, `RateVersion / CustomerRateCard / RateCard` are for it to process through the term-issue flow; this section does not rule on them.

**Length and abbreviation** (one-architecture owns format, one-glossary owns abbreviation designations):

| # | Rule | Source | Guard |
|---|---|---|---|
| N3-L1 | Every PostgreSQL identifier is at most **63 bytes**, counted as UTF-8 on the final output, with prefixes, suffixes and separators included; schema and object are checked separately, not as a combined `schema.object`. Applies to schema, table, column, constraint, index, view, trigger, function, sequence etc.; never rely on database truncation | ADR-0053 background, "guard rules to be changed in sync"; decision 4 (this section's length rule) | **planned**: `DdlNamingTest` has no length check yet; fill in before new-name / rename DDL is applied; review meanwhile |
| N3-L2 | Default to the full name; choose a registered abbreviation only when a standard or industry-idiomatic abbreviation is registered and applies to that surface, or when the full-name projection exceeds 63 bytes. First record the full-name projection and its byte count, then generate the short name at the registered replacement position and re-check length. **Collision checks are scoped by schema**: table, view, index and sequence share the relation-name space; the same-name index that a `pk / uk` constraint creates automatically also occupies a relation name, and a clash with another relation fails; a constraint and its own same-name index count once. Constraint names are unique within a table; table-level uniqueness does not replace the schema-level check of its index. If it cannot be shortened, hand it to one-glossary / one-architecture; no truncation, no initial-letter joining, no ad-hoc random suffix | ADR-0053 decisions 4, 8; registration plan §5; self-made abbreviation problem of the old code (history, now removed), §2.2 (this section adds usage conditions and collision scope) | **planned**: checks for length, registration source, in-schema relation-name and in-table constraint-name collisions; before DDL is applied, review meanwhile |
| N3-L3 | An abbreviation is expressed by the designation's boolean `abbreviation`, not a role; registration must trace to the concept `id`, the full designation, the abbreviation text, the language, applicable naming surfaces and the decision source; other field schema is for one-glossary to decide. Projection replaces only the registered part and keeps class words and constraint / index prefixes. In class names an abbreviation is a word: `WbsNode / PocValue / ArItem`, not `WBSNode`; these existing spellings do not automatically permit every same-shape abbreviation | N3 decisions items 2, 4; ADR-0053 decisions 2.2, 4, 8; registration plan §5; §2.2 | existing word-split and ASCII checks cover only part; abbreviation authorisation and projection: planned (before switch) |

#### 2.1.5 Event wording

Owner: one-glossary registers word forms, one-architecture sets slots and format, one-quality takes the guards. The verb register and template register designs (not yet created) are in `docs/glossary/glossary-schema-design.md` §2.

| Item | Rule | Source | Guard |
|---|---|---|---|
| Command and event word forms | A command is a base-form verb + object; an event is object + past tense / past participle of a fact that happened, with no `Event / Request / Command` suffix; G29's ban on `Requested / Query` endings continues. `SubmitTimesheet → TimesheetSubmitted` shows only the English word form; the Chinese still takes the registered designation, and no renaming follows from the example | ADR-0053 decision 3; registration plan §1; §2.2, §4 G5, G29; `docs/rules/coding-standard.md` CS-1.4 | `ArchitectureRulesTest`, `GlossaryTest`, `scripts/guards/term_rules.py`; slot-rendering equality: planned |

#### 2.1.6 UI message catalog

The catalog (source `docs/glossary/messages/zh-CN.yaml`, to be created; skeleton message key → `{text, status, note}`) is not implemented; its design moved to `docs/glossary/glossary-schema-design.md` §3. Message-key format is in §2.1.4. Guard: planned (before the catalog is enabled and new pages adopt it); a pass of the current `check-copy-glossary.mjs / OutwardTextGuardTest` must not substitute for message-key acceptance. The language scope validated this phase is in §2.1.3.

#### 2.1.7 Summary of planned guards

Moved to `docs/glossary/glossary-schema-design.md` §4. The N3-L1–L3 gaps stay in §2.1.4 above.

### 2.2 Spelling

- English ASCII identifiers only; Chinese appears only in UI text, i18n and comments (guarded by ArchUnit `identifiersAreAscii`).
- Abbreviations are treated as words: `WbsNode`, `PocValue`, `ArItem`, not `WBSNode`; when an abbreviation may be used and how it is registered: §2.1.4 N3-L2–L3.
- Class suffixes are **only** those in the table below; each suffix has exactly one location.

| Suffix / form | Location | Example |
|---|---|---|
| Aggregate, entity, value object: noun, no suffix | `<module>.domain` | `Timesheet`, `Quote`, `Money` |
| `*Repository` (interface) | `<module>.domain` | `TimesheetRepository` |
| `Jdbc*Repository` (implementation, package-private) | `<module>.infrastructure.persistence` | `JdbcTimesheetRepository` |
| Use case: verb-object, no suffix, one class per use case | `<module>.application` | `SubmitTimesheet`, `ApproveQuote` |
| `*Queries` / `*Commands` (interface) | `<module>.api` | `WorkPackageQueries` |
| Event: past tense, no `Event` suffix, record | `<module>.events` | `TimesheetApproved`, `QuoteApproved` |
| `*Controller` (package-private) | `<module>.web` | `TimesheetController` |
| `*Request` / `*Response` (record) | `<module>.web` | `SubmitTimesheetRequest` |
| `*Client` / `*Gateway` (anti-corruption layer) | `integration.<system>` | `U8cVoucherClient` |
| Status enum `*Status`; the enum name is stored in the database | `<module>.domain` | `TimesheetStatus.APPROVED` |
| **Forbidden** | anywhere | `Util(s)`, `Helper`, `Manager`, `Info`, `Data`, `Impl`, `DO`, `ManHour` |

### 2.3 Database, API, frontend

- **PostgreSQL**:
  - One BC, one schema; the schema name is `<subdomain>_<bc>` (e.g. `cost_timesheet`); table names are snake_case plural, no `psa_` prefix.
  - An external reference is `<object>_id`; audit columns are `created_at`, `updated_at`, `version`. For "who did it", no generic `created_by` / `updated_by` columns: the operator of a state change is recorded in `state_transitions.actor_id` (ADR-0058), other actions go to audit (`platform_audit`), and a column is created only for an operator with business meaning (e.g. `confirmed_by`, `released_by`, the column name states the action) (one-architecture 2026-10-22, from one-data's check of main-baseline practice). Boolean columns start with `is_` / `has_`.
  - Amounts are stored in pairs `<name>_amount` + `<name>_currency`; status stores the enum name (`text` + `CHECK`).
  - Flyway scripts live under each module's directory, named `V{yyyyMMddHHmmss}__{module}_{description}.sql`; the description is English snake_case (a Chinese explanation goes in the script's first-line comment).
- **HTTP**: path is `/api/v{n}/{subdomain}/{plural resource}` (ADR-0008, e.g. `/api/v1/cost/timesheets`; the second segment is the **subdomain** slug, not a BC or feature name), kebab-case; actions are written `:submit` / `:approve`; **all JSON (interfaces, domain-event payloads, external delivery) fields are camelCase**, snake_case only in the database; error codes are segmented by module; errors common across modules (illegal state transition, concurrent modification by someone else, reason required, past the withdrawal cut-off, not logged in, etc.) are defined once only in the shared base `kernel` and used directly by modules, and a module defines only its own specific errors (user 2026-10-15, on the extracted rule: 「通用错误码只在公共基础定义」). Status codes, lists, type representation, concurrency and error responses: §2.6.
  - Correction (2026-10-14): the original example `/api/v1/timesheet/timesheets` treated a BC as a subdomain, and the original rule "event payload snake_case" disagreed with interface camelCase; both are unified.
- **Frontend**: `src/modules/<module>/{pages,components,hooks,api}`; the API client is generated from OpenAPI (hand-written URLs forbidden); TypeScript strict; components PascalCase, hooks `useXxx`, file names kebab-case.

### 2.4 Column-name class words, constraints and indexes (after DAMA / ISO 11179)

**Column name = qualifier + class word** (ISO 11179 representation term). The class word decides the type. A column name may consist of the class word alone (e.g. `code`, `name`, `status`); the table name is then its object.

| Class word | Meaning | Type |
|---|---|---|
| `_id` | identifier | `bigint`, `uuid`; `text` only for external-system identifiers (e.g. WeCom userid). **In force with ADR-0033**: always `uuid`; the only exception is `platform_integration.source_references.source_id` (the source's actual type, `bigint` for A8, may be negative); an external system's text identifier does not use `_id` but `_key` (e.g. `external_identities.external_subject_key`) |
| `_ids` | set of identifiers (ADR-0034-IMPL pending ruling A-3; one-architecture 2026-10-17 took (a)) | `uuid[]` |
| `_key` | business key, idempotency key; **in force with ADR-0033**: also an external system's text identifier (e.g. `external_identities.external_subject_key`) | `text` |
| `_code` / `_codes` | code / code set | `text` / `text[]` |
| `_name` | name | `text` |
| `_text`, `_description`, `_reason`, `_note`, `_title`, `_message` | free text | `text` |
| `_address` | address (email, IP) | `text` |
| `_ciphertext` | encrypted value | `text` |
| `_status`, `_kind`, `_type`, `_policy`, `_system` | enumeration (`_system`: category of an external system, e.g. `source_system`, ADR-0033) | `text` + `CHECK` listing enum names |
| `_at` | point in time | `timestamptz` |
| `_date` | business date | `date` |
| `_amount` | amount; must pair with a same-named `_currency` | `numeric` |
| `_currency` | currency (ISO 4217) | `text` / `char(3)` |
| `_qty`, `_hours`, `_days` | quantity; quantity with unit (hours, person-days) | `numeric` (`_qty` may also be integer) |
| `_rate` / `_pct` | ratio, unit price / percentage | `numeric` |
| `_count` | count | `integer` / `bigint` |
| `_number` | number (document number), sequence (round) | `text` / `integer` / `bigint` |
| `_order` | ordering | `integer` |
| `_hash` | digest | `text` |
| `_url` / `_path` | address / path | `text` |
| `_by` | operator (audit) | `bigint`. **In force with ADR-0033**: `uuid` (the user account's `user_id`) |
| `_value` | numeric value (counter, start value) | `numeric` / `integer` / `bigint` |
| `is_` / `has_` (prefix) | indicator | `boolean` |

Two standalone column names `id` and `version` are also allowed. A document number uses `_number` (consistent with the numbering module's terms `legacy_number`, `round_number`), not `_no`.

**Constraints and indexes**: primary key `pk_<table>`; foreign key `fk_<table>_<column>`; unique `uk_<table>_<column>` (for both unique constraints and unique indexes); table-level check `ck_<table>_<rule>`; ordinary index `ix_<table>_<column>`. Primary keys, foreign keys and unique constraints must be named explicitly with `CONSTRAINT <name>`, not rely on PostgreSQL default names. **Every enum `CHECK` is named `ck_<table>_<column>`** (DDL-22; user 2026-10-15: all pending DDL-audit items answered 「全部按推荐 (Recommended)」); no unnamed column-level CHECK, because changing values after go-live requires dropping and recreating by name; the value set of a column matches the corresponding Java enum. Existing items during the open-baseline period are renamed this way and folded into the baseline DDL tightening (R-2); the guard is an extension of `DdlNamingTest` (§4 planned guard G-D12).

**Guard**: `DdlNamingTest` derives the final table structure from all migration scripts (tracking `ADD COLUMN`, `RENAME COLUMN`, `RENAME CONSTRAINT`, `ALTER INDEX ... RENAME TO`) and checks class words vs types and constraint / index names; injected samples cover each kind of error. Items that existed before the rule took effect are registered in `backend/architecture/ddl-naming-baseline.txt` (each line carries a rename suggestion); a new violation, and a baseline line that is fixed but not deleted, both fail. Tables whose column names are owned by a framework are exempt: `platform_identity.sessions`, `sessions_attributes` (Spring Session JDBC), `platform_integration.event_publication` (Spring Modulith), `platform_scheduling.scheduler_locks` (ShedLock).

### 2.4a DDL design principles

(User 2026-10-15, asked 「看看 ddl 设计上还有什么需要强化的原则？比如索引？」, chose 「全部采纳 (Recommended)」.) Applies to all own tables; framework-created tables (Spring Session, ShedLock, Modulith event registry) are exempt and registered in the guards. Items marked "guard" are checked by `DdlNamingTest` / `DataArchitectureTest`; the rest rely on per-wave review (DATA_DESIGN_CHECKLIST §14.1).

1. **Business unique constraint**: the primary key is always UUIDv7 (RFC 9562, adopted by the user 2026-10-15, ADR-0041, FRAMEWORKS §9 (intent 195827); §14.1 D16; order by a business-time column, not the ID). Transaction, master-data, reference and configuration tables also need at least one business unique constraint (e.g. document type + document number, legal-entity code, role code, source system + object type + source ID; an exclusion constraint for effective-interval rows), and `business_key` is registered in the data-access registry. Pure log and event tables may be exempt with a stated reason. References always use UUID; a business key is never a foreign key. Guard.
2. **Indexes**:
   - A foreign-key column must have an index starting with that column (PostgreSQL does not create one). Guard (G-DDL2, planned).
   - Every list / query path is registered in the registry (filter columns, sort columns) and covered by an index; the sort key of cursor pagination falls back to `id` in a composite index. Guard (§4 G-P3 and G-DDL4 merge into one EXPLAIN integration test, planned; review before it is implemented).
   - Hot status paths use a partial index (`WHERE status IN (...)`).
   - No duplicate or redundant indexes (same prefix as another index, duplicating a unique constraint). Guard (G-DDL3, planned).
3. **Nulls and defaults**: `NOT NULL` by default; a nullable column must state the business meaning of "empty" in its column comment (guard: a nullable column's comment contains 「为空表示」 or an equivalent statement). A text column must not use an empty string for null (`CHECK (col <> '')`). The database allows only technical defaults (e.g. the initial `version`); business values and times are always written explicitly by the application. Guard.
4. **Delete semantics**: foreign keys default to `ON DELETE RESTRICT`; only child rows within the same aggregate may use `CASCADE`, and must be registered. No soft-delete flag column; a business delete is expressed by status or version, and a physical delete is only for expiring technical data. Guard (CASCADE whitelist, ban on `is_deleted`-type columns).
5. **Code format and text length**: a `_code` column has a format CHECK (character set, case, length); for a case-insensitive comparison column, the unique constraint is built on the normalised value (e.g. `lower(email_address)`). Free-text columns have a length cap (`CHECK (char_length(col) <= n)`). Guard.
6. **Enum evolution**: no PostgreSQL `ENUM` type, uniformly `text + CHECK` (naming in §2.4); a new value changes the CHECK by migration (`DROP CONSTRAINT`, then `ADD CONSTRAINT … NOT VALID`, then `VALIDATE`, to avoid long lock holding), and removing a value first migrates the data. Guard (ban on `CREATE TYPE ... AS ENUM`).
7. **jsonb boundary**: `jsonb` only for payloads with no fixed structure by nature (event archive, external raw text, idempotent response body); domain fields must be proper columns; every `jsonb` column registers its purpose in the registry and has a `jsonb_typeof` CHECK. Guard.
8. **Large-table planning**: an append-only table with retention over 3 years or an expected over ten million rows (audit, event archive, process archive, etc.) decides its partition key (by time range) and archive / purge method when created and registers them in the registry; partitioning after go-live rewrites the whole table. Review.
9. **Post-go-live migration safety** (in force once the open-baseline marker is deleted): schema changes only "expand then contract"; add indexes with `CREATE INDEX CONCURRENTLY` (a separate, non-transactional migration); migrations set `lock_timeout` and `statement_timeout`; changes that rewrite the whole table are forbidden (e.g. changing a column type, adding a column with a volatile default); dropping and renaming a column take two releases. Guard (static check of migration scripts).
10. **Frequently updated rows**: tables whose rows are updated repeatedly (numbering counter cells, idempotency registry, etc.) may set `fillfactor` to reduce bloat and lock contention; a performance recommendation, used as needed and explained in the table comment.

### 2.5 Column-level sensitivity classification

| Level | Meaning | Handling |
|---|---|---|
| `PUBLIC` | may go outside | — |
| `INTERNAL` | default level | visible after login, by function permission |
| `SENSITIVE` | personal information, salary and daily rate, bank cards (GUIDING_PRINCIPLES §6) | masked display, authorised by legal entity × department × project role, access traced; not in analytics-layer detail |
| `RESTRICTED` | passwords, keys, session content | never leaves the database (used only inside the module), not in logs, not in the analytics layer |

Registered in `backend/architecture/data-classification.txt` (`<schema.table.column> | <level> | <reason>`); an unregistered column defaults to `INTERNAL`. Guard: `DdlNamingTest` requires a column whose name looks like sensitive information (password, key, TOTP, bank, salary, daily rate, ID document, mobile, email, birthday, gender, address, session content) to be registered as `SENSITIVE` or `RESTRICTED`, and a registered column must exist. web / api outputs must not expose `SENSITIVE` or `RESTRICTED` columns directly; that guard is implemented by the 1a session.

### 2.6 HTTP interface conventions

(User 2026-10-15, on the pending items of principle check A: 「全部按推荐 (Recommended)」.) Applies to all `/api/v{n}/…` interfaces; promoted from §2–3 of `docs/architecture/proposals/phase1-a-platform-conventions.md`, which are no longer current rules. Paths and naming: §2.3; interface versioning and contract evolution: ADR-0008 and ADR-0035. Guards: §4 planned guards G-A1–G-A9; until implemented, each wave's review checks them one by one.

- **A-1 Resources and commands**: read with `GET`; create on a collection with `POST /{plural resource}`; replace editable fields wholesale with `PUT /{plural resource}/{id}`; state transitions and business actions are commands `POST /{plural resource}/{id}:{action}` (action from `standard_actions` in `terms.yaml`), never `PUT` to change state; delete with `DELETE`, only for objects that may be deleted (e.g. an unsubmitted draft); a business "void" or "revoke" is a command. A read-only computation that changes no state (e.g. trial calculation, preview) may use `GET /{plural resource}:{action}` or `GET /{plural resource}/{id}:{action}`, parameters in the query string, with no side effects.
- **A-2 Status codes**: create returns `201` with `Location` and `ETag`; a command returns `200` (body is the resource after the command) or `204`; delete returns `204`; read returns `200` with `ETag`. Status codes in the contract match runtime.
- **A-3 Lists**: the response is always `{items, nextCursor}`; parameters `cursor` (opaque, carries a format version, unsigned; internally encodes the sort key + `id`; the server strictly validates structure and type, and an invalid one returns 400 `PLATFORM_WEB_CURSOR_INVALID`) and `limit`. `limit` defaults to 50 with a maximum of 200; these two values are single constants for the whole system (defined once, in kernel); OpenAPI declares `maximum: 200`; exceeding the maximum returns 400, with no silent truncation. Lists always use index-based keyset pagination, never in-memory slicing (GUIDING_PRINCIPLES §7). When a total is needed, add `GET /{plural resource}:count` with the same filter parameters as the list. The cap for small tables without pagination: GUIDING_PRINCIPLES §7a R-P2.
- **A-4 Filtering and sorting**: filter parameters are camelCase field names, multiple values comma-separated, no general expression language; sorting is `sort=field,-field`, whitelisted fields only; an unknown parameter or a field outside the whitelist returns 400 `PLATFORM_WEB_REQUEST_INVALID`, and the response extension property `invalidParameters` names the parameters (in force for every `GET` under `/api`, including single-resource reads). An export interface uses the same filter parameters as the corresponding list.
- **A-5 No `PATCH`**: partial changes use `PUT` replacing the editable fields wholesale, or a named command that expresses the business intent.
- **A-6 Batch**: written `POST /{plural resource}:{action}-batch`, at most 100 items per call (same cap as GUIDING_PRINCIPLES §7a R-P2; source: one-coordinator 2026-10-15 took the stricter of two recommended values (100 / 200), user 「全部按推荐」); authorisation and execution are per item, each item succeeds or fails on its own. The response is `200` returning per-item results in request order (object ID, result, ProblemDetail on failure); partial success also returns `200`; a malformed whole batch returns 400 and executes nothing.
- **A-7 Type representation**:
  - Own IDs are always string uuids: JSON string, OpenAPI `type: string, format: uuid` (ADR-0033 item 6 clause 2), likewise for properties, path parameters and query parameters; the frontend treats them as opaque strings, no numeric operations, no parsing; source-system IDs do not enter the interface (ADR-0033);
  - Instants are ISO-8601 UTC with `Z` (`2026-10-15T02:00:00Z`), not a `+08:00` offset, not epoch milliseconds; business dates are `YYYY-MM-DD`, the day taken in the business time zone (`Asia/Shanghai`);
  - An amount is `{amount, currency}`, `amount` a decimal string; a ratio is a decimal string in 0–1; other decimal quantities (hours, person-days) are likewise strings;
  - Status, type and similar values are named `enum` schemas in OpenAPI, not an unconstrained `string`.
- **A-8 Idempotency and concurrency**:
  - A side-effecting `POST` accepts `Idempotency-Key`: the server stores the response (status, body, `ETag`, `Location`) for 24 hours by "user + key" and replays a repeated request verbatim; the same key with a different body returns 422, and 409 while the first request is still processing; once the in-progress placeholder exceeds the configured timeout it may be taken over; 5xx is not stored. Session and login-state endpoints (path contains `/sessions`, or ends with `/login-states`) never store the response body and do not declare this header in the contract; the scope is expressed solely by `IdempotencyScope` and guarded by `HttpContractIT.idempotencyKeyIsOfferedExactlyWhereItIsStored`.
  - A `PUT`, `DELETE` or command that modifies an existing resource must carry `If-Match: "<version>"`: missing returns 428, a version mismatch returns 412; the contract declares the header required and declares 412 / 428. The version is not in the request body. A command that truly needs no version (e.g. forced logout) is registered in the guard as an exemption with its reason.
  - 409 means only "the current state does not allow the operation" (illegal state transition etc.), not a concurrency conflict.
- **A-9 Errors**: always RFC 9457 ProblemDetail, `type` and `code` taken from `ErrorCode`; common errors are defined only in kernel (§2.3, G48); each interface declares in OpenAPI the error status codes it may return; a 5xx body carries only the correlation id (correlationId), details go only to the log. Error responses are produced in one place.
- **A-10 Access control at the interface**: not logged in: 401; no function permission: 403; function permission but a single resource outside the caller's data scope: 404, not revealing whether it exists; lists are filtered by data scope without error; the data scope is decided by the session and is never a request parameter.
- **A-11 Output**: a response body is always a `*Response` record in the `web` package, never an application-layer `*View` or a domain object directly; `SENSITIVE` fields are masked per §2.5, `RESTRICTED` fields do not appear.

## 3. Package structure: where code goes (agent cheat sheet)

```
com.forcitis.one
├── OneApplication.java
├── SubdomainModuleDetection.java   module detection strategy: every <subdomain>.<bc> is a top-level module (ADR-0003)
├── kernel/                         shared kernel (OPEN module): Money, Hours, LegalEntityId, UserId, @ValueObject, @PublicEndpoint
├── <subdomain>/                    subdomain namespace (12, see 02-子域划分): presales | contract | delivery | cost | receivables
│   │                               | expense | procurement | km | organization | accounting | platform | operations
│   └── <bc>/                       one BC = one Modulith module, e.g. cost.timesheet, delivery.execution, platform.approval
│       ├── package-info.java       responsibility, the BC's Chinese name, owned schema (no @ApplicationModule annotation)
│       ├── api/                    @NamedInterface("api"): interface + record + typed enum + typed ID (narrow interface)
│       ├── events/                 @NamedInterface("events"): records of facts that happened (past tense)
│       ├── domain/                 aggregate, entity, value object, domain service, *Repository port. No Spring or JDBC
│       ├── application/            use cases (one class per use case, a single public method), the only transaction boundary
│       ├── infrastructure/
│       │   └── persistence/        Jdbc*Repository (JdbcClient + typed mapping), accesses only the <subdomain>_<bc> schema
│       └── web/                    *Controller (/api/{version}/…), *Request / *Response record
└── (resources) db/migration/<subdomain>_<bc>/V<yyyyMMddHHmmss>__<subdomain>_<bc>_<description>.sql
architecture/module-dependencies.txt  the approved module dependency graph (code form of the context map); changes need review
```

| I want to write… | Put it in | Name |
|---|---|---|
| a new business rule or calculation | `<module>.domain` (aggregate method or value object) | glossary noun |
| the flow behind one user action / interface | `<module>.application` | verb-object, e.g. `SubmitTimesheet` |
| a query for other modules | `<module>.api` interface + `application` implementation | `*Queries` |
| notify other modules "what happened" | `<module>.events` record, published inside the use case | past tense |
| SQL | `<module>.infrastructure.persistence` | `Jdbc*Repository` |
| REST interface | `<module>.web` | `*Controller` |
| calling an external system | `integration.<system>` | `*Client` |
| a shared value type | `kernel` (needs review; no business behaviour) | |
| a new cross-module dependency | first change `architecture/module-dependencies.txt` and pass review | |
| a new table / column | `db/migration/<subdomain>_<bc>/`, must carry a Chinese COMMENT | table and column names derived from the glossary |
| a business number | call `DocumentNumbers.next(...)` of `platform.numbering` inside this use case's transaction | register a new document type in `DocumentType` |

## 4. Guard rules

Rules below are implemented unless marked otherwise. Format: id | rule | enforced by. Planned (not implemented) guards are in the tables after the implemented ones; until implemented, each wave's review checks them by their source.

### Backend, implemented

| # | Rule | Enforced by |
|---|---|---|
| G1 | Modules access each other only via `api` / `events`; no cycles; dependencies point only to `allowedDependencies` | Modulith `ApplicationModules.verify()` |
| G2 | `api` holds only interfaces and records | `apiPackagesContainOnlyInterfacesAndRecords` |
| G3 | `api` signatures must not reference domain / application / infrastructure / web | `apiSignaturesDoNotLeakModuleInternals` |
| G4 | Each module has ≤ 12 api methods in total | `apiSurfaceStaysNarrow` |
| G5 | `events` hold only records, names do not end in `Event` | `eventsAreRecords` |
| G6 | web and infrastructure classes must not be public | `webAndInfrastructureAreNotPublic` |
| G7 | domain does not depend on Spring, Servlet or other layers | `domainIsFrameworkFree` |
| G8 | application does not depend on web or infrastructure | `applicationDoesNotTouchWebOrInfrastructure` |
| G9 | web calls only application | `webOnlyCallsApplication` |
| G10 | JDBC / `java.sql` appear only in infrastructure | `jdbcOnlyInInfrastructure` |
| G11 | `@Transactional` appears only in application | `transactionsOnlyInApplication` |
| G12 | `@RestController` only in web, and ends with `Controller` | `controllersLiveInWebAndEndWithController` |
| G13 | A `*Repository` in domain must be an interface; the implementation starts with `Jdbc` | `repositoriesFollowPortAdapterNaming` |
| G14 | Names such as `Util` / `Helper` / `Manager` / `Info` / `Data` / `Impl` / `DO` / `ManHour` are forbidden | `noVagueOrLegacyNames` |
| G15 | Identifiers must be ASCII | `identifiersAreAscii` |
| G16 | A `*status` field in domain must be an enum | `statusesAreEnums` |
| G17 | Outside kernel, `*amount` / `*price` / `*cost` fields must not be a bare `BigDecimal` (Money = (amount, currency)) | `ArchitectureRulesTest#moneyIsNeverABareDecimal` |
| G18 | Field injection, `System.out`, JUL, `java.util.Date`, `@PermitAll` are forbidden | `generalCodingRules` |
| G19 | Only `platform.approval.infrastructure` may use Flowable | `flowableOnlyInsideApprovalInfrastructure` |
| G20 | Controllers must map to `/api/{version}/…` | `controllersAreVersioned` |
| G21 | A `@ValueObject` must be a record | `valueObjectsAreRecords` |
| G22 | A value object must not hold `Date`, mutable collections or arrays | `valueObjectsHoldNoMutableState` |
| G23 | A `*hours` field in domain must use `Hours` | `hoursAndQuantitiesUseValueObjects` |
| G24 | `BigDecimal.equals` and `new BigDecimal(double)` are forbidden in domain and application layers | `noBigDecimalEqualsInBusinessCode` |
| G25 | Business code must not call `now()` directly; inject a `Clock` | `timeComesFromAnInjectedClock` |
| G26 | Main code must not contain `Fix*`, `Demo*`, `Mock*`, `Test*`, `Sample*`, `*Example*` | `noDebugOrDataFixCodeInMain` |
| G27 | A use-case class has exactly one public method | `useCasesExposeExactlyOnePublicMethod` |
| G28 | Every interface method has `@PreAuthorize` or `@PublicEndpoint(reason)` | `everyHandlerIsAuthorizedOrExplicitlyPublic` |
| G29 | Event names must not end in Request / Requested / Command / Query; plain `@EventListener` is forbidden | `eventsAreFactsNotRequests` |
| G30 | `@NamedInterface` only on api / events packages | `namedInterfacesAreOnlyApiAndEvents` |
| G31 | A `*Id` field in api / events / domain must be a typed-ID record | `contractsAreStronglyTyped` |
| G32 | `Object` and `Map` are forbidden in api / events | `contractsAreStronglyTyped` |
| G33 | Only identity may configure Spring Security; a request record must not contain identity fields such as userId / creator | `onlyIdentityConfiguresSecurity`, `requestsNeverCarryTheCallerIdentity` |
| G37 | [adopted (ADR-0018)] type names, record components, enum constants, non-private methods in domain / api / events / web / kernel, and table and column names in migrations, once split into words, must be registered in `docs/glossary/terms.yaml` or belong to `common_words` | `GlossaryTest` (on failure generates the draft `target/glossary-draft.yaml`) |
| G38 | [adopted (ADR-0018)] a forbidden spelling in the glossary (old name, pinyin, non-standard term) fails | `GlossaryTest` |
| G39 | [adopted (ADR-0018)] when a column name corresponds to a glossary term, the column comment must start with the term's Chinese name | `GlossaryTest` (parses `COMMENT ON` in migrations) |
| G40 | [adopted (ADR-0019)] new code must not depend on `cn.iocoder..` or yudao base classes and tools (`BaseDO`, `CommonResult`, `TenantBaseDO`, etc.) | `LegacyShapeTest#theCodeBaseHasNoOldSystemShape` |
| G41 | HTTP path is `/api/v{n}/{subdomain of the owning module}/{kebab plural resource}[/{id}][:{action}]`, resource word registered | `NamingSurfaceTest` |
| G42 | OpenAPI schema names PascalCase; properties and parameters camelCase and registered; JSON keys of serialised events camelCase | `NamingSurfaceTest` (reads `/v3/api-docs`, springdoc 3.1.1) |
| G43 | Permission code `{subdomain}.{BC}:{resource}:{action}`, action from the glossary's `standard_actions`; permission codes in `@PreAuthorize` must be in the catalog | `NamingSurfaceTest` |
| G44 | Flyway file `V{real 14-digit timestamp}__{module}_{english_snake}.sql`, directory is the module schema, prefix matches directory, versions not duplicated | `NamingSurfaceTest` |
| G45 | The document-type enum of business numbers corresponds one-to-one with seed rows, upper snake case and registered | `NamingSurfaceTest` |
| G46 | A `@Scheduled` job must have a ShedLock lock `{module}.{name}`; metric names are literals prefixed by module; `job_type` is checked; config keys start with `forcitis.{module}.` or are framework-known keys | `NamingSurfaceTest` |
| G47 | Process / decision id is `{subdomain}_{BC}_{snake}`, file name equals id, `ProcessKey` corresponds one-to-one with processes, user-task ids are camelCase | `NamingSurfaceTest` |
| G48 | Error codes: one enum per module implementing `kernel.ErrorCode`, format `{SUBDOMAIN}_{BC}_{REASON}`; common errors are defined only in `kernel`; module enums must not be synonymous with it or each other (Chinese name unique, R-5 guard) | `NamingSurfaceTest` |
| D1 | Module dependencies must be registered in `architecture/module-dependencies.txt` | `ModuleDependencyTest` |
| D2 | DDL: file-name convention, create only this schema's tables, snake_case, ban old names and vague words, audit columns, amounts with currency, status `CHECK`, boolean `is_` / `has_`, a COMMENT on every column; column class words vs types, constraint and index naming (§2.4); sensitive-column registration (§2.5) | `DdlNamingTest` |
| D3 | The data dictionary is generated from database metadata; every table and field must have a comment | `ApplicationIntegrationTest.everyTableAndColumnIsDocumented…` |
| G-A2 | List response shape is `{items, nextCursor}`, parameters `cursor` / `limit` (judged by response shape, not class name). Existing gaps are registered in `backend/architecture/list-paging-gaps.txt` (a new violation fails; a registered gap that is fixed but not deleted also fails) | `ListPagingGuardTest` (K-3a) |
| G-P1 | The `limit` cap comes from one kernel constant (`ListLimits`); every OpenAPI `limit` declares `minimum: 1`, `maximum: 200`, `default: 50`; other `MAX_LIMIT`-type constants are forbidden (§2.6 A-3, GUIDING_PRINCIPLES §7a R-P2) | `ListPagingGuardTest` (K-3a); gaps as G-A2 |

### Frontend, implemented (`frontend/eslint.config.js`, `frontend/stylelint.config.js`, `frontend/scripts/check-structure.mjs`; TypeScript strict)

| # | Rule | Enforced by |
|---|---|---|
| F1–F4 | One-way dependency `app → modules(index) → shared`; cross-module only via `modules/<m>/index.ts` | eslint-plugin-boundaries |
| F5 | axios, fetch and hand-written `/api` URLs forbidden; only the OpenAPI-generated client | ESLint (`frontend/eslint.config.js`) |
| F6 | ASCII identifiers only; kebab-case file names; class names Util / Helper / Manager / Info / Data / Impl forbidden | ESLint (`frontend/eslint.config.js`) |
| F7 | `any` forbidden; React hooks rules | ESLint (`frontend/eslint.config.js`) |
| F8 | hooks live in `hooks/` and are named `useXxx` | ESLint (`frontend/eslint.config.js`) |
| F9 | `model/` and `shared/lib` are pure logic and must not reference React or API | ESLint (`frontend/eslint.config.js`) |
| F10 | Only `modules/<m>/api` may import the generated client | ESLint (`frontend/eslint.config.js`) |
| F11 | `dangerouslySetInnerHTML` forbidden (stored XSS, SEC-008) | ESLint (`frontend/eslint.config.js`) |
| F12 | `export *` forbidden | ESLint (`frontend/eslint.config.js`) |
| F13 | Each module's `index.ts` has ≤ 8 exports | `frontend/scripts/check-structure.mjs` |
| F14 | Business modules must not import antd directly, no inline styles, no hard-coded colours; raw tokens are read only in `shared/design-system` (ADR-0013) | ESLint (`frontend/eslint.config.js`) |

### Planned guards (not implemented)

(User 2026-10-15, on the pending items of the DDL audit and principle checks A / B: 「全部按推荐 (Recommended)」.) These guards are **registered only**; status is "planned" for all. The rule text is where the "Source" column points; this table says what is checked, by when it must exist, and which work unit owns it. **Ownership is scheduled by one-1a-next-2 in its plan**: an owner already fixed by an ADR or user decision is written as the work-unit name; the others read "scheduled by one-1a-next-2", with the initial grouping in brackets for reference. Until implemented, each wave's review checks by source, item by item. Where a guard overlaps an existing one, implementation merges them into one test.

| # | Check | Source | Must exist before | Owner / status |
|---|---|---|---|---|
| G34 | fact repositories of `operations.facts` have only insert and query methods; fact tables have triggers rejecting UPDATE / DELETE | ADR-0016 | first card that builds `operations.facts` / `accounting.ledger` | planned: the modules do not exist yet; enforce as ArchUnit + DDL test (ADR-0016) |
| G35 | only `accounting.ledger` may depend on accounting subjects and subject mapping | ADR-0016 | first card that builds `operations.facts` / `accounting.ledger` | planned: the modules do not exist yet; enforce as ArchUnit (ADR-0016) |
| G36 | a booking-touching BC may submit posting candidates only through the `operations.facts` api and must not write fact tables | ADR-0016 | first card that builds `operations.facts` / `accounting.ledger` | planned: the modules do not exist yet; enforce as dependency graph D1 + ArchUnit (ADR-0016) |
| G-A1 | All own IDs in OpenAPI (properties, path parameters, query parameters) are `type: string, format: uuid`; IDs in frontend generated types are not number | §2.6 A-7 | immediately (before handoff / reassignment UI goes live) | R-1b (changed together with own UUIDv7) |
| G-A3 | Merged into G-C4 (modifying interfaces declare required `If-Match` and 412 / 428, runtime consistent; exemptions registered one by one with reasons) | §2.6 A-8, ADR-0035 item 8 | see G-C4 | see G-C4 |
| G-A4 | Merged into G-C4 (a `POST` on a collection returns 201 with `Location`) | §2.6 A-2, ADR-0035 item 8 | see G-C4 | see G-C4 |
| G-A5 | No `PATCH` mapping | §2.6 A-5 | before 1b | scheduled by one-1a-next-2 (initial: HTTP contract guard) |
| G-A6 | Instant fields are `date-time` with `Z`; amounts are `{amount: string, currency}` | §2.6 A-7 | before 1b | scheduled by one-1a-next-2 (initial: HTTP contract guard; the amount part merges with G-M5) |
| G-A7 | A controller return type may only be a `*Response` in the `web` package | §2.6 A-11 | before first deployment | scheduled by one-1a-next-2 (initial: HTTP contract guard; complements G-X6) |
| G-A8 | No common error codes defined outside kernel | §2.3, §2.6 A-9 | before first deployment | R-5 (same implementation as G48) |
| G-A9 | Error responses are produced only by the unified handler; controllers do not hand-assemble ProblemDetail | §2.6 A-9 | before 1b | scheduled by one-1a-next-2 (initial: HTTP contract guard) |
| G-C1 | `scripts/ci/openapi_compat.py` (to be created): compare OpenAPI with the merge-base; read the deprecation register; a registered and expired deletion is let through; any other breaking change fails and can be let through only by the commit trailer `Contract-Break:`, listed separately in the PR | ADR-0035 items 3, 7, 9 | before first production deployment | scheduled by one-1a-next-2 (initial: contract-evolution guard) |
| G-C2 | Event compatibility (AsyncAPI shape comparison): each message's payload schema and class name in `asyncapi.yaml` is compared with the merge-base; a change of shape, class name or package name is breaking and requires either `schemaVersion + 1` or a new event type, and marks the release "needs drain" | ADR-0035 items 3, 5, 9 | before first production deployment | same as above |
| G-C3 | Pre-deployment drain check: for a release marked "needs drain", after maintenance mode is on (database flag `platform_scheduling.maintenance_modes`), unfinished event publications and dead letters must be 0; what cannot be drained is handled by quarantine registration, otherwise the deployment aborts | ADR-0035 item 6 | before first production deployment | same as above (wired into the deployment playbook, one-ops) |
| G-C4 | Contract is truthful: OpenAPI status codes (201 with `Location`), required headers (`If-Match`, 412 / 428), `ETag`, named enums, no untyped response bodies, `info` consistent with runtime (extends `HttpContractIT`; G-A3, G-A4 merged in); one of the two contract guards in force during dev, the other is G-C5 | ADR-0035 items 2, 8, §2.6 | in force when H-1 merges (H-1 is scheduled after R-5) | H-1 |
| G-C5 | Event payloads and outbound payloads are flat: IDs are string uuids, no `{value: …}` wrapper (one of the two contract guards in force during dev, the other is G-C4) | ADR-0035 items 2, 5 | in force when R-1b merges | R-1b |
| G-C6 | Deprecation expiry: a scheduled check of expired but undeleted deprecated items, sends a P3 reminder and reports to the scheduling session (does not turn unrelated PRs red); changing a deprecated object in a PR (deletion excepted) fails | ADR-0035 item 7 | before first production deployment | scheduled by one-1a-next-2 (initial: contract-evolution guard) |
| G-C7 | `reporting` dataset contract: view column names and types match the registration; breaking changes handled per ADR-0035 | ADR-0035 | R1 | R1 work unit |
| G-P2 | The number of SQL statements of list and batch use cases does not grow with row count (a counting DataSource decorator, default budget ≤ 5) | GUIDING_PRINCIPLES §7a R-P3 | before 1b | scheduled by one-1a-next-2 (initial: performance guard) |
| G-P3 | Registered query paths use an index in `EXPLAIN` with `enable_seqscan = off` | GUIDING_PRINCIPLES §7a, DATA_DESIGN_CHECKLIST §14.1 D19 | before 1b | same as above |
| G-P4 | The three timeouts of external read-only connections take effect: an integration test against a hanging port fails or degrades within budget | DATA_DESIGN_CHECKLIST §14.3 I2 | before first deployment | same as above (same card as G-I1, G-I3) |
| G-P5 | Production config has connection pool, statement timeout, histogram buckets and slow-query threshold, with values conforming to R-P4–R-P6 | GUIDING_PRINCIPLES §7a | before first deployment | same as above |
| G-P6 | k6 load test: every interface class of R-P1 meets target (phase exit) | GUIDING_PRINCIPLES §7a R-P1 | Phase 2 entry | scheduled by one-1a-next-2 (initial: performance guard; ask per CLAUDE.md before installing k6) |
| G-S1 | `scripts/ci/workflows_pinned.py` (to be created): workflow `uses:` always pinned to a 40-character SHA; `pull_request_target` forbidden | docs/ops/supply-chain.md S6 | before first deployment | scheduled by one-1a-next-2 (initial: CI supply chain) |
| G-S2 | osv-scanner: vulnerability and licence check (merged into an existing job, no added job count) | docs/ops/supply-chain.md S3, S4 | before first deployment | same as above |
| G-S3 | `pom.xml` declares no custom repository, only Maven Central | docs/ops/supply-chain.md S1 | before first deployment | same as above |
| G-S4 | The test database image is defined in one constant, with a digest | docs/ops/supply-chain.md S7 | before first deployment | same as above |
| G-S5 | A release includes an SBOM, and the SBOM is written into the signed attestation | docs/ops/supply-chain.md S5 | before production deployment | same as above |
| G-S6 | A runner pre-job hook checks the run user and credential directory | docs/ops/supply-chain.md S6 | pending (follows the decision on a dedicated runner user) | same as above |
| G-O1 | Log scan: an `ERROR` line must begin with 「ALERT P1\|P2\|P3 <subdomain>.<BC> 」 | GUIDING_PRINCIPLES §11 O-2 | before first deployment | scheduled by one-1a-next-2 (initial: observability) |
| G-O2 | A log argument must not be the return value of a personal-information method; for an unknown exception log only the class name | GUIDING_PRINCIPLES §11 O-3 | before first deployment | same as above |
| G-O3 | An integration test asserts every log line has a correlationId, and values such as the email in a unique-constraint conflict are not written to disk | GUIDING_PRINCIPLES §11 O-1, O-3 | before first deployment | same as above |
| G-O4 | Production config exposes a Prometheus endpoint and the connection string contains `logServerErrorDetail=false`; zero business metrics scraped after deployment fails | GUIDING_PRINCIPLES §11 O-3, O-4 | before first deployment | same as above (post-deployment check belongs to one-ops) |
| G-O5 | Metrics referenced by alert rules exist in the application | GUIDING_PRINCIPLES §11 O-6 | before first deployment | same as above |
| G-O6 | Correlation-break counter: a missing correlationId at event publication is counted and alerted, not silently created anew | GUIDING_PRINCIPLES §11 O-1 | before 1b | same as above |
| G-I1 | External connections go only through the integration layer's connection factory, which enforces three timeouts: connect, query, lock wait | DATA_DESIGN_CHECKLIST §14.3 I2 | before first deployment | scheduled by one-1a-next-2 (initial: external-integration resilience) |
| G-I2 | The integration register `backend/architecture/integration-register.yaml` (to be created) matches the adapters in code | DATA_DESIGN_CHECKLIST §14.3 | before first deployment | same as above |
| G-I3 | A view hung with `WAITFOR` verifies that timeouts take effect and degradation works | DATA_DESIGN_CHECKLIST §14.3 I2, I7 | before first deployment | same as above |
| G-I4 | User-path cool-down: after 3 consecutive failures, no call for 60 seconds | DATA_DESIGN_CHECKLIST §14.3 I6 | before 1b | same as above |
| G-I5 | One failure logs only one `ERROR` with a stack trace | DATA_DESIGN_CHECKLIST §14.3 I10 | before first deployment | same as above |
| G-MG1 | `legacy_numbers` shape: UUIDv7 primary key, source-system column, `uk(source system, document type, original number)`, `migration_batch_id uuid NOT NULL`, append-only | ADR-0036 item 3, ADR-0033 | before first deployment | R-1b |
| G-MG2 | A staging run twice, or resumed after interruption, gives the same staged result as one full run; a commit resumed after interruption gives the same target result as a single commit (former G-MG3 merged in) | ADR-0036 item 7 | before the phase that first migrates in rights-and-duties facts | first migration-in phase |
| G-MG3 | Merged into G-MG2 | ADR-0036 item 7 | — | — |
| G-MG4 | Migration-in has no side effects. Test level: start only the migration-in offline context and run one batch; `@RecordApplicationEvents` asserts 0 published application events, counters unchanged, no process started, `accounting.ledger` generates no journal entry for migrated facts, rows of the posting-result table for migrated documents = rows of migrated facts, with injected counter-examples. Production level: at offline-process start the number of loaded methods under `com.forcitis.one` carrying `@ApplicationModuleListener` / `@TransactionalEventListener` must be 0, otherwise startup is refused | ADR-0036 items 1, 5 | same as above | same as above |
| G-MG5 | The context of the migration-in offline process contains no `@Scheduled` method, ShedLock job, Flowable async and timer executor, event listener, or outbound delivery worker (asserted item by item); the migration-in orchestration module is depended on by no module | ADR-0036 item 1 | same as above | same as above |
| G-MG6 | Batch term is unique: only one spelling 「迁入批次」 appears in code and table structure (after R-1b renames, add forbidden spellings `import_batch` / `ImportBatch` for `migration_batch`) | ADR-0036 item 3 | before first deployment | R-1b |
| G-MG7 | Migration-in reconciliation is complete: every (period × legal entity × object type) in the phase's migration-in spec object list has a reconciliation row, with zero difference or finance sign-off, otherwise it cannot be committed; each batch has a quality report (ADR-0071 item 6) | ADR-0036 item 8 | before the phase that first migrates in rights-and-duties facts | first migration-in phase |
| G-MG8 | Deleted: stage-then-commit (ADR-0036 item 7) replaces the read-side visibility predicate | ADR-0036 | — | — |
| G-MG9 | Number consistency: the number column of a migrated document matches the original number in `legacy_numbers`; a mismatch alerts (`legacy_numbers` prevails) | ADR-0036 item 4 | same as above | same as above |
| G-MG10 | Migration-in entry: each BC's migration-in entry may be called only by `platform.migration` (ArchUnit); the entry declares `Propagation.MANDATORY`, and there is no `REQUIRES_NEW` on the migration-in path | ADR-0036 item 1 | same as above | same as above |
| G-MG11 | Migration quality rules are consistent: the rule file and the `MQ-*` numbers in code agree both ways; rules cited by the migration-in spec exist; `ACCEPTED_WITH_NOTE` is used only for warnings, `EXCLUDED` only for source-comparison findings, `NORMALIZED` is written only by registered `MQ-N` rules | ADR-0071 items 3, 5 | when W1 builds migration-in orchestration | before the first BC rehearsal |
| G-MG12 | Migration quality gates: each of the pending-reconciliation precondition, commit gate and effect gate is rejected by a constructed counter-example; the migration-in database role has no write permission on the disposition table, the in-app migration-in role has no disposition, deferral or Q5 entry permission; a deferral rejected when approver and owner are the same person; a finding stays in the list after a trial-commit rollback; "approved not to migrate" over its cap is rejected | ADR-0071 items 5, 6, 8 | when W1 builds migration-in orchestration | before the first BC rehearsal |
| G-D12 | The enum `CHECK` is named `ck_<table>_<column>` and its value set matches the Java enum (same test as G-DDL8) | §2.4, §2.4a item 6 | before first deployment | R-2a |
| G-DDL1 | Transaction, master-data, reference and configuration tables register `business_key` in the registry and have the matching unique constraint; a foreign-key target must be a primary key or a unique constraint made only of uuid columns (a composite foreign key guarantees the same row; a business key is not a foreign key; one-architecture 2026-10-16 ruling, data-fix cards AP-04, WF-12) | §2.4a item 1; DDL audit (2026-10-15) guard G1 | before first deployment | scheduled by one-1a-next-2 (initial: baseline DDL tightening) |
| G-DDL2 | A foreign-key column has an index starting with that column | §2.4a item 2; DDL audit (2026-10-15) guard G2 | before first deployment | same as G-DDL1 |
| G-DDL3 | No duplicate or redundant indexes (same prefix, duplicating a unique constraint) | §2.4a item 2; DDL audit (2026-10-15) guard G3 | before first deployment | same as G-DDL1 |
| G-DDL4 | A query path is registered and `EXPLAIN` uses an index (merged with G-P3 into one test; depends on G-DDL12) | §2.4a item 2, DATA_DESIGN_CHECKLIST §14.1 D19; DDL audit (2026-10-15) guard G4 | before 1b | same as G-DDL1 |
| G-DDL5 | A nullable column's comment states 「为空表示」; text columns have the empty-string CHECK; defaults are only within the technical-default whitelist | §2.4a item 3; DDL audit (2026-10-15) guard G5 | before first deployment | same as G-DDL1 |
| G-DDL6 | `CASCADE` registered, soft-delete flag columns forbidden, physical delete only within a whitelist | §2.4a item 4; DDL audit (2026-10-15) guard G6 | before first deployment | same as G-DDL1 |
| G-DDL7 | A `_code` column has a format CHECK; free text has a length cap; case-insensitive uniqueness is built on the normalised value | §2.4a item 5; DDL audit (2026-10-15) guard G7 | before first deployment | same as G-DDL1 |
| G-DDL8 | `CREATE TYPE … AS ENUM` forbidden; enum CHECK named and consistent with the Java enum (same test as G-D12) | §2.4a item 6; DDL audit (2026-10-15) guard G8 | before first deployment | same as G-DDL1 |
| G-DDL9 | A `jsonb` column has a registered purpose and a `jsonb_typeof` CHECK | §2.4a item 7; DDL audit (2026-10-15) guard G9 | before first deployment | same as G-DDL1 |
| G-DDL10 | Long-retention or large tables register scale and partition decision in the registry | §2.4a item 8; DDL audit (2026-10-15) guard G10 | before first deployment | same as G-DDL1 |
| G-DDL11 | `scripts/ci/migration_safety.py` (to be created): `CONCURRENTLY`, `lock_timeout` / `statement_timeout`, no full-table rewrite, foreign keys and CHECK with `NOT VALID`, expand-then-contract, no invalid indexes | §2.4a item 9; DDL audit (2026-10-15) guard G11 | before first deployment (merged before C-3 deletes the baseline marker) | same as G-DDL1 |
| G-DDL12 | The test database collation is C (as in production); no invalid indexes after migration; fillfactor of frequently updated tables is commented | §2.4a items 2, 10; DDL audit (2026-10-15) guard G12 | before first deployment | same as G-DDL1 |
| F15 | VI colour values registered in the palette as sRGB hex; `oklch` must fall inside the sRGB gamut | §4a U1 | before the first D-8 design-sync | scheduled by one-1a-next-2 (initial: frontend design system) |
| F16 | Token-paired text and non-text contrast meet the requirement | §4a U2 | before the first D-8 design-sync | same as F15 |
| F17 | `eslint-plugin-jsx-a11y` rules | §4a U3 | before 1b | same as F15 |
| F18 | Run axe in component tests | §4a U3 | before 1b | same as F15 |
| F19 | antd `zhCN` and dayjs `zh-cn` set at the app root | §4a U4 | before first deployment | same as F15 |
| F20 | `check-bundle-size.mjs` (to be created; the existing one is `frontend/apps/h5/scripts/check-bundle.mjs`): fail when gzip size exceeds budget | §4a U8 | before 1b | same as F15 |
| F21 | The semantic-component contract types of PC and H5 are identical (type test) | §4a U7 | before 1b | same as F15 |
| F22 | Structure check of the query view's three states | §4a U5 | before 1b | same as F15 |

Other planned items (no id):
- SQL references only this module's schema (scan the SQL text in `infrastructure.persistence`);
- every P0 rule has one test beginning with `RULE_NNN_` (coverage checked against `docs/business/business-rules.md`);
- a new aggregate, event or table name must be in the glossary;
- a file over 1000 lines must be split (review must-fix, `scripts/review/PROMPT.md`); ≤ 400 lines per file and ≤ 60 lines per method are review advice only, no hard Checkstyle gate (CS-1.15);
- frontend: ESLint `no-restricted-imports` forbids cross-module import of internal directories.

## 4a. Frontend UI rules

(User 2026-10-15, on the pending items of principle check B: 「全部按推荐 (Recommended)」.) Applies to PC and H5. Guards: F15–F22 in the planned-guards table above.

- **U1 Brand colour and font**: VI colour values are written as sRGB hex into the raw palette of the design tokens, and the semantic layer references only the palette: FOR grey `#595757`, CITIS red `#CF000E` (exact values, replacing the current approximate `oklch` values, and synced to the design-token master). When using `oklch`, the value must fall inside the sRGB gamut. The brand wordmark is SVG, not font-rendered. Fonts such as Montserrat ship with the package under OFL, and the font files are checked in before 1b.
- **U2 Contrast**: text contrast ≥ 4.5:1 (large text ≥ 3:1), non-text elements (icons, borders, focus ring) ≥ 3:1, computed on the actual token pairings (text colour × its background). The tertiary text colour and the pending-approval status text colour are darkened to comply; other pairings stay.
- **U3 Accessibility**: all operations reachable by keyboard, focus visible, focus returns to the trigger after an overlay closes; error prompts `role="alert"`, non-interrupting status prompts `role="status"` (H5 likewise); icon-only buttons carry `aria-label`.
- **U4 Language and format**: source language and this phase's UI language are `zh-CN`, only Chinese is delivered this phase; designation and message structure: §2.1.2–2.1.3 and `docs/glossary/glossary-schema-design.md` §3 (was §2.1.6). antd is set to `zhCN`, dayjs to `zh-cn`; date, instant, amount and quantity formatting goes only through core's formatting functions. Source: ANM AN-04, AN-05; ADR-0053 decision 6; registration plan §2. Guard: F19 (Chinese config, planned); message catalog and language completeness are planned guards, to land before the catalog is enabled.
- **U5 Loading, empty, error states**: data views present the three states uniformly through the query-view component; a 5xx error shows a copyable 「关联号」 (the correlationId, the same value as §2.6 A-9), for matching logs when reporting a fault.
- **U6 No optimistic updates**: commands on documents, approvals and amounts update the UI only after server confirmation; only UI preferences with no business consequence may update optimistically.
- **U7 Component contract defined once**: the contract of semantic components (status tag, amount, approval actions, etc.) is defined once in `shared/design-system`, PC and H5 each implement the same contract; core uses only React 18 APIs.
- **U8 Bundle-size budget** (gzip): PC entry ≤ 300 KB, a single route ≤ 150 KB, H5 entry ≤ 200 KB; exceeding fails CI, and adjusting a budget needs a reason in the PR.

## 5. Agent-friendly conventions

- Each module root has a `README.md` (consistent with `package-info.java`) stating: responsibility, owned schema, the list of api / events exposed, allowed dependencies, and the range of related rule ids.
- One top-level type per file; one class per use case, with the entry method uniformly named `execute`.
- The package structure of tests mirrors production code one to one; P0 rule tests are named like `RULE_012_clauseTotalMustEqualFinalAmount`, so an agent can locate them by rule id.
- Generated code (OpenAPI client, jOOQ, etc.) lives only in a `generated/` directory, is never hand-edited, and is excluded from the guards.
- A guard's failure message must contain "where to put it / what to name it", so an agent can correct itself on reading the error.

## 6. (removed: history; see `git show 59315c148^:docs/rules/naming-standard.md`)

## 7. Naming-surface list: everywhere in the new system that needs unified naming

The word-source entry is §2.1 (ADR-0053 decision 1.3); the scope of naming surfaces cites ADR-0053 decision 4; this section and the detail rules together are the entry. The ✔ in the table means only that the original-format guard exists and the historical measurement; it does not mean it now reads concepts / designations or covers the new projection; new-structure coverage and planned guards are in §2.1. Status: ✔ existing-format guard; ○ to be built or partly covered. Owners: concepts, designations and controlled words belong to one-glossary; each surface's format, projection and guard list belong to one-architecture; checks are implemented by one-quality and the surface-owning sessions (ADR-0053 decision 7; registration plan "shared structure and duties").

| # | Naming surface | Rule | Guard | Status |
|---|---|---|---|---|
| 1 | repository, module, Java package | `forcitis-one-psa`; `com.forcitis.one.<subdomain>.<BC>.{api,events,domain,application,infrastructure,web}` | G1, D1, ModularityTest | ✔ |
| 2 | class, record, enum, method | English, industry-standard words; no vague words, old names, pinyin, `fst`, `cn.iocoder` | G12–G15, G26, G37, G38, G40 | ✔ |
| 3 | domain event | past-tense fact (`TimesheetSubmitted`), not ending in Event / Request / Command | G5, G29, G37 | ✔ |
| 4 | value object, typed ID | `Money`, `Hours`, `TimesheetId`… | G16, G17, G21–G23, G31 | ✔ |
| 5 | database schema, table, column, constraint, comment | §2.1.4, §2.3–2.4; column comment compared to the concept's `zh-CN` preferred standard name (ADR-0053 background "supersedes" ADR-0018 decision 3) | D2, D3, G37, G39 (existing old-projection checks) | ✔; new-designation comparison: planned (before switch) |
| 6 | Flyway file name | `V{timestamp}__{module}_{english_snake}.sql` | D2; G44 | ✔ |
| 7 | HTTP path | `/api/v{n}/{subdomain}/{kebab plural resource}[:{action}]` | G20 (version); G41 (subdomain and resource registration) | ✔ |
| 8 | JSON field, OpenAPI schema, event payload | always camelCase, schema names PascalCase, split words registered | G42 | ✔ |
| 9 | permission code | `{subdomain}.{BC}:{resource}:{action}`, action from the standard action set | G28 (typed); G43 (format) | ✔ |
| 10 | business-number rule code | upper snake case (`QUOTE`, `TIMESHEET`), registered | G45 | ✔ |
| 11 | scheduled job, lock name, job table `job_type`, metric name, config key | `{subdomain}.{BC}.{name}`; config-key prefix `forcitis.{subdomain}.{BC}.` | G46 | ✔ |
| 12 | BPMN process, DMN decision, user task | key names unified by subdomain / BC prefix; task id camelCase and registered | G19 (engine isolation); G47 | ✔ |
| 13 | error code | `{SUBDOMAIN}_{BC}_{REASON}` | G48 | ✔ |
| 14 | frontend PC directory, file, component, hooks | `modules/<m>`; kebab-case files, PascalCase components, `useXxx` | F1–F8, F13 | ✔ |
| 15 | design tokens | only master semantic token names (`--surface-*`, `--status-*`…) | F14 | ✔ |
| 16 | UI text and message keys (PC, H5, server, print, reports, notifications) | §2.1.2–2.1.3, `docs/glossary/glossary-schema-design.md` §3; whole messages enter the catalog, business-noun placeholders take designations, key format in §2.1.4 | existing `check-copy-glossary.mjs / OutwardTextGuardTest` check old wording | ○ message catalog, role, placeholders and completeness: planned (before the catalog is enabled and new pages adopt it) |
| 17 | H5 (Taro) | directory, naming and API client follow the same rules as PC | none (the guard template covers only PC) | ○ build equivalent lint for H5 before Phase 2 |
| 18 | `reporting` views and BI columns | view name `rpt_<topic>`, column names match the glossary `snake` | none | ○ fill in before Phase 5 |
| 19 | external delivery channels and anti-corruption layer | external system names unified (`wecom`, `seeyon`, `u8c`, `fenbeitong`), external fields appear only inside the anti-corruption layer | G1 (anti-corruption isolation) | ○ add a name-convention guard |
| 20 | log fields, trace attributes | structured log keys camelCase, business keys from the glossary; field set and trace keys: GUIDING_PRINCIPLES §11 O-1 | G-O1, G-O3 (planned) | ○ |
| 21 | Git branch, commit, PR | session worktree branch `claude/<session name>-<topic>` (the original text has an empty parenthesis here); commit `type(scope): 中文`, scope is the subdomain slug; PRs and document collections per TEAM-40, 53 | commit-msg gate; the rest by review | ○ the old repo's branch format is not a gate |
| 22 | concept, designation, usage role and language | §2.1.1–2.1.3; ADR-0053 decisions 2, 5–6; registration plan "shared structure and duties", §2 | existing `term_rules.py / GlossaryTest` check the old structure | ○ new schema, roles and status combinations: planned (before switch) |
| 23 | verb, aspect marker, event composition and template | §2.1.5, `docs/glossary/glossary-schema-design.md` §2; ADR-0053 decision 3; registration plan §1, §5 | existing event word-form guard | ○ forward rendering and exception references: planned (before composition migration) |
| 24 | PostgreSQL identifier length and abbreviation | §2.1.4 N3-L1–L3; ADR-0053 "guard rules to be changed in sync" | `DdlNamingTest` has no length check yet | ○ planned (before new-name / rename DDL is applied) |

**Inconsistencies found and corrected during this unification**:
- the HTTP example `/api/v1/timesheet/timesheets` treated a BC as a subdomain, changed to `/api/v1/cost/timesheets`;
- "event payload fields snake_case" disagreed with interface camelCase, unified to camelCase for all JSON;
- Flyway descriptions are explicitly English snake_case (the old repo used Chinese descriptions);
- revenue-recognition basis: the glossary's "input method and output method coexist" conflicts with the final rule ACC-R01 「统一产出法 · 客户价值确认」; ACC-R01 prevails (ADR-0016).

**Known blind spots of G41–G48**: metric names assembled at runtime; config keys in `.yml`; Flowable `candidateGroups` names; the DMN files and the `job_type` table have no real samples yet (verified only by injected cases); test-data migration is not bound by G44; the event publication registry table is not enabled, event keys are checked via the application's JSON mapper; an error code written as a plain string is not detected. To satisfy the guards the template added 4 glossary entries (cost, error-code, capacity, scheduler-lock), status proposed, entering the next terminology confirmation round.
