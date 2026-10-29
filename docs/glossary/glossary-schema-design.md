# Glossary schema design (not yet implemented)

Moved from `docs/rules/naming-standard.md` §2.1.1, §2.1.5, §2.1.6, §2.1.7 (card #811). Everything here is design: the guards named are planned, not implemented. Implemented rules stay in `docs/rules/naming-standard.md` (§2.1.1 machine values, §2.1.2–2.1.4, §2.1.5 event wording).

Terms used below:
- "N3 decisions" = `docs/glossary/arch-naming/n3-decisions.md` (branch `claude/one-glossary-bcs-systems`, commit `f22d6b0b`; items cited as `@ 7e4eb96b`). Field names follow it. The migration mapping follows its item 3.
- "registration plan" = `docs/glossary/arch-naming/naming-atoms-registration-plans.md`. `naming-atoms-draft.md` §4 (DRAFT) and `naming-atoms-prep.md` "target structure" (PREP) are supplementary sources for candidate fields only.
- ADR-0053 = `docs/adr/ADR-0053.md`. Its decisions are cited, not copied.
- Owner of concept/designation structure: one-glossary. "Designation" (指称) = the ADR's term for what this file also calls a name entry (叫法条目).
- Do not design the serialisation with the old `zh / en / snake` three fields. Adopted field names and values apply; the rest of the serialised shape is for one-glossary to decide.

## 1. Concept entries and designation entries (was §2.1.1)

| Layer | Field or item | Rule and open boundary | Source | Guard |
|---|---|---|---|---|
| concept | `id`, `kind` | The concept main entry keeps these two fields. Concept `id` is the reference key: permanent, carries no meaning; there is no `concept_ref`, every reference writes `id`. A BC as a concept still uses the `bcs.yaml` `id`. `kind` follows the DDD classification; meta-words use a separate `atom_kind`. | N3 decisions item 4; ADR-0053 decisions 1.1–2, 2.1; registration plan "shared structure and duties"; DRAFT §4 | existing `scripts/guards/term_rules.py` checks id, kind; new structure: planned |
| concept | `bc / scope / definition` | Registers the owning scope and the definition; value domains follow the term table and `bcs.yaml`. | ADR-0053 decision 2.1; registration plan "shared structure and duties" | existing `scripts/guards/term_rules.py` checks scope; new structure: planned |
| concept | `standard_ref / relations / status` | Standard basis, concept relations, concept status. The edge structure of relations and how `homonyms / upstream_term` migrate are for one-glossary to decide. | ADR-0053 decisions 2.1, 5.1 and impact; registration plan "shared structure and duties"; DRAFT §4 | existing guards check only the old structure; planned |
| concept / meta-word merge rows | `id / atom_kind / designations / decision_ref` | Merge rows reference the concept `id` directly, no separate reference key. Whether `decision_ref` is required per row is for one-glossary to decide. `atom_kind` does not replace `kind`. | N3 decisions item 4; registration plan "shared structure and duties", §3–6; ADR-0053 decision 2 | planned (before switch) |
| designation | `locale / name / role` | **N3 values adopted**: language, name, usage role. Language and role are separate columns; role choice per naming-standard §2.1.2. Do not put snake, interface or permission strings in the language field. | N3 decisions; ADR-0053 decisions 2.2–3, 6.1 | planned (before switch) |
| designation | `usage_status / approval_status / source_type` | **N3 values adopted**: usage status, approval status, source type. They must not share one `status`; aliases and dialects must not be pushed into the usage role. Machine values: naming-standard §2.1.1. | N3 decisions; ADR-0053 decisions 2.2, 5.2 | existing forbidden-word check is not a check of the new status combinations; planned |
| designation | `abbreviation` | Boolean attribute: whether the designation is an abbreviated form. It does not occupy a usage role (role = usage, attribute = form). | N3 decisions item 2 | planned (before switch) |
| designation | replacement reference after rename, source, effective / retired time | Replacement reference per the ADR. Time fields come from PREP candidates; field names, required conditions and the boundary with concept status are for one-glossary to decide. | ADR-0053 decision 5.3; PREP "target structure" | planned (before rename migration) |
| derived | snake / camel / kebab, per-surface products | Produced per naming-standard §2.1.4. Old fields remain only as a compatibility projection; the switch compares item by item `zh / en / snake / aliases / forbidden`. | ADR-0053 decision 2.3 and migration plan item 2; registration plan "shared structure and duties", "landing order and exits" | planned (old vs new projection comparison, before switch) |

### N3 seven decisions (cross-reference)

Source: `docs/glossary/arch-naming/n3-decisions.md @ 7e4eb96b`. Item 5 uses the field skeleton below first; check against the formal structure once NAM-1017-GLO-06 delivers it.

| Item | Decision |
|---|---|
| 1. Roles and UI usage | `canonical / code_name` in use; `terse / verbose / column_header / documentation / ui_label` reserved, not enabled, absence does not fail. For now UI text takes `zh-CN / canonical`; enable `ui_label` only when one concept truly needs a UI wording different from the canonical name. |
| 2. Abbreviation | Boolean attribute `abbreviation` on the designation, not a role; the same abbreviation may serve code names and UI. |
| 3. `aliases / forbidden` migration mapping | Mapping table below. |
| 4. Concept reference | No `concept_ref`; concept `id` is the reference key, every reference writes `id`; BC concepts follow `bcs.yaml` `id`. |
| 5. Verbs, templates, message catalog | Field skeletons in §2 and §3 below (all to be created); check against the formal structure once NAM-1017-GLO-06 delivers it. |
| 6. Message status and missing translation | `proposed / approved / deprecated / retired`, consistent with the concept lifecycle; fallback for missing translation per ADR-0053 decision 6.4; a runtime fallback must raise an alert. |
| 7. English locale | Use `en`; British vs American spelling awaits a user ruling (N4 Q13); until then new code spelling follows the N4 candidate and locale does not distinguish spelling variants. |

### Alias / forbidden spelling migration mapping

N3 decisions item 3 (revised), `@ 7e4eb96b`. `source_type` keeps the old type value unchanged; the semantic classification is looked up from `source_type` by the naming rules.

| Old input | Mapping |
|---|---|
| `deprecated`, or `type` in `dialect / forbidden / old-code / legacy-code / legacy-table` | `usage_status: forbidden`; `source_type` = original type value |
| any other type (e.g. `standard-zh`, `kpi-symbol`) | `usage_status: permitted`; `source_type` = original type value |
| status `undetermined` | `approval_status: pending_confirmation` |
| otherwise | `approval_status` follows the concept status |

## 2. Verb and template registers (was §2.1.5, parts)

Owner: one-glossary registers word forms, one-architecture sets slots and format, one-quality takes the guards. Verbs and templates use the skeletons below (N3 decisions item 5); **check against the formal structure once NAM-1017-GLO-06 delivers it**. A file not yet created does not mean the rule is machine-enforced.

| Item | Rule | Source | Guard |
|---|---|---|---|
| Verb register | `docs/glossary/verbs.yaml` (to be created), each entry: `id` (V number, permanent), `zh`, `en` (base form), `en_past_participle`, `snake`, `standard_action`, `merged_from` (merged synonyms), `status`. Meaning, word form and the mapping to the permission standard action are checked item by item. | N3 decisions item 5; ADR-0053 decisions 3, 7.1; registration plan §1; N4 §4 | planned (before the verb source is enabled and compositions migrate) |
| Template register | `docs/glossary/naming-templates.yaml` (to be created): slots `{object, verb, aspect, complement}`; per target (command, event, state) a Chinese rendering form, an English rendering form and samples; concept references write `id` directly. `perfective / pending / progressive` are aspect markers, not business objects. Complements, prepositional tails and connecting fragments must appear only at positions the template allows. | N3 decisions items 4–5; ADR-0053 decision 3; registration plan §1, §5 | existing `GlossaryTest / term_rules.py` recognise some past tenses and prepositional tails; slot legality: planned |
| Lexicalised facts and counter-examples | `BecameOverdue` is registered by the lexicalised-fact template; `WrittenOff / SubmittedForApproval` are recognised by the current word forms. With `Locked` inside the object and `Changed` at the event tail, do not judge both as the main event verb by suffix alone; an unregistered exception must not be let through by a suffix rule. | ADR-0053 decision 3; registration plan §1; current `GlossaryTest.pastTense` | existing form check covers part; object / main-verb distinction and exception references: planned (before composition migration) |

## 3. UI message catalog (was §2.1.6)

Owner: frontend/backend sessions write the catalog, one-glossary reviews business designations, one-architecture sets the shared contract, one-quality implements the checks. Source location: **`docs/glossary/messages/zh-CN.yaml` (to be created)**; frontend and backend generate their formats from the same source. Per N3 decisions items 5–6 the skeleton is **message key → `{text, status, note}`**; **check against the formal structure once NAM-1017-GLO-06 delivers it**; the card that wrote this rule did not create the file. Sources: N3 decisions; registration plan §2; ADR-0053 decisions 6.4, 7. Guard: planned (before the catalog is enabled and new pages adopt it); implementation belongs to later naming cards.

| Field | Content | Source | Guard |
|---|---|---|---|
| message key (map key) | Stable usage key of naming-standard §2.1.4; value is `{text, status, note}`; format examples are not registrations. | N3 decisions item 5; naming-standard §2.1.4 | planned: unique, registered, use site exists |
| `text` | Whole message text with `{noun:<concept id>}` and `{verb:<V number>}` placeholders; no literal business nouns. Concepts are referenced by `id`; for now nouns take `zh-CN / canonical`. Use the CLDR / ICU message format cited by the ADR; do not register whole sentences as business concepts; do not take program expressions `${...}` as terms. | N3 decisions items 1, 4–5; registration plan §2; ADR-0053 decisions 6.4–5 | planned: pattern parsing, reference exists, role usable, pattern and parameter types agree; grammar version and library undecided |
| `status` | `proposed / approved / deprecated / retired`, consistent with the concept lifecycle; not mixed with the designation usage status or approval status. | N3 decisions item 6; ADR-0053 decisions 5–6 | planned (before the catalog is enabled) |
| `note` | Message remark; required conditions follow the formal NAM-1017-GLO-06 structure. | N3 decisions item 5 | planned (before the catalog is enabled) |

When a designation or message is missing for a language, the runtime falls back to the source language per ADR-0053 decision 6.4 and alerts (log or metric), and never shows the key name; the build checks completeness for enabled languages and a gap fails (N3 decisions item 6). Regional formats cite ADR-0053 decision 6.5; the language scope validated in this phase is in naming-standard §2.1.3. Messages cover frontend, H5, server-side human text and print / report / notification; logs, test text and comments are not registered as messages. Existing `copy-allow-list.yaml`, `*Texts` and the Chinese word-segmentation guards are only migration input and do not prove complete messages are registered. Guard: switching `check-copy-glossary.mjs / OutwardTextGuardTest` to verify the message catalog is **planned**; a pass of the current checks must not substitute for message-key acceptance.

## 4. Summary of planned guards (was §2.1.7)

Summarises the N3 gaps of that round; nothing here is implemented. Owner cards are for one-coordinator to schedule; one-quality and the surface-owning sessions implement; the guards must be in place before the trigger threshold, and review checks the matching clause meanwhile.

| Item | Trigger threshold | Owner card |
|---|---|---|
| Concept and designation new structure: stable id, language / role, two statuses, unique preferred, forbidden and pending-confirmation not referable (naming-standard §2.1.1–3) | before the new structure switch | undecided (one-quality, one-glossary) |
| Forward projection equal item by item to old `zh / en / snake / aliases / forbidden`; no name parsed from an id (naming-standard §2.1.1, §2.1.4) | before the new structure and readers switch | undecided (one-quality) |
| Schema `bcs[].subdomain / designations` reference, designation selection, failure on missing (naming-standard §2.1.4) | before the new schema projection is enabled | undecided (one-quality, one-glossary) |
| Controlled plural registration, failure on missing, `Analysis / Staff` counter-examples (naming-standard §2.1.4) | before new table-name / API-resource projection is enabled | undecided (one-quality) |
| 63 bytes, abbreviation authorisation, in-schema relation-name and in-table constraint-name collisions including the same-name index of `pk / uk` (N3-L1–L3) | before new-name / rename DDL is applied | undecided (one-quality) |
| Verb word form, slot / aspect / template legality, lexicalised exceptions, rendering equality (§2 above) | before the verb source is enabled and compositions migrate | undecided (one-quality, one-glossary) |
| Message key: singular resource, dotted lowercase usage, `kernel` shared prefix, uniqueness and use-site correspondence (naming-standard §2.1.4; §3 above) | before the catalog is enabled and new pages adopt it | undecided (one-quality, frontend/backend sessions) |
| Message pattern, placeholder type / concept / role, no hard-coded text, completeness for enabled languages; runtime fallback with alert (§3 above) | before the catalog is enabled and new pages adopt it; before any new language is enabled | undecided (one-quality, frontend/backend sessions) |
| Chinese config F19, code names vs English UI names kept by usage (naming-standard §2.1.2–3, §4a U4) | before the first deployment of Chinese config; role separation before the new structure switch, English completeness before the English UI is enabled | undecided (frontend session, one-quality) |
| Forbidden-source designation projection reproducible, old/new comparison consistent (the old "§6" old→new snapshot was removed from naming-standard; it is to be replaced by this projection: generated from designations with `source_type` in `old-code / legacy-code / legacy-table` and `usage_status: forbidden`, ADR-0053 background "supersedes" and decision 5) | before the glossary source switch | undecided (one-glossary, one-quality) |
