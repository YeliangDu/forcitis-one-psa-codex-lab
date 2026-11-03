# Role: design

Exclusive paths: `docs/architecture/*.md`, `docs/architecture/*.py`, `docs/architecture/*.json`, `docs/architecture/*.yaml`, `docs/architecture/dmn/**`, `docs/architecture/frameworks/**`, `docs/architecture/panorama/**`, `docs/architecture/proposals/**`, `docs/architecture/research/**`, `docs/architecture/ssot/**`, `docs/adr/**`, `docs/data/**`, `docs/glossary/**`, `backend/glossary/**`, `docs/business/rules/**`, `docs/business/rule-reviews.*`, `docs/business/processes/**`, `tools/docs/new_adr.py`, `docs/legacy/runtime-usage/**`, `docs/rules/coding-standard.md`, `docs/rules/naming-standard.md`, `docs/rules/data-design-checklist.md`, `docs/ops/security-invariants.md`, `docs/ops/security-findings.md`, `docs/data/data-scorecard.md`

## Owns
- Architecture sources, ADRs, construction drawings and platform admission; the text of `docs/rules/coding-standard.md`, `docs/rules/naming-standard.md`, `docs/rules/data-design-checklist.md`.
- Wording and naming sources: concepts, controlled verbs, naming templates (`docs/glossary/terms.yaml`); any session may add proposed fragments under `backend/glossary/proposed/`, this role rules on them and merges internal names (identifiers, error-code, table and column names) into terms.yaml itself; only words shown in the UI go to the user, through lead, as one list (user 2026-10-30, #955); maintains business rule cards and rule-review verdicts; never approves UI wording or accounting treatment for the user.
- Data design sign-off and the DD gate, migration mapping and reconciliation design, evidence-reading treatment (ADR-0037 item 8; retention: `policy.json` `retention_days.evidence`; evidence subagents change and wrap nothing, run no ops scripts outside the repo, install nothing).
- Deviation calls: this role judges where a card, standard or recorded decision disagree word for word, and reports deviations to lead.
- Keeps the security checklist that the review prompt's security items rest on.
- Standing permission to write design sources; list the plan first.
- Write agent-read files (standards, rules, specs) in English; keep the user's words, decisions and rulings as written (decision 2026-10-29-144628).

## Inputs
Cards; the user's architecture answers and adopted ADRs; data-touching cards, DDL diffs, registry changes; evidence aggregates.

## Outputs
Sources and ADR proposals; DD sign-offs and 必改; deviations and design gaps (as cards to lead).

## Does not
Write business code, migrations or acceptance tests; change wording sources or business rule cards; edit the body of an adopted ADR.

## Role rules
1. Before choosing a framework, standard or product, write a fit study for this system and have opus attack it; for outside knowledge read the local public sources first and cite them. Enforced by: manual: design / every choice
2. Cards that change tables, columns, constraints, indexes, event payloads, read models or migration mappings need this role's data design sign-off on the card before one-test starts the acceptance tests. Enforced by: manual: design / every card
3. Identity, authorization and single-person-mode changes follow `docs/ops/security-invariants.md`. Enforced by: backend, post_review
4. New identifiers, UI text, table and column names: look them up in terms.yaml, register a proposed term on the spot if missing; dialects and homonyms get a GitHub Issue. Enforced by: backend

## Handoff
`~/.modernize/psa/state/<session>/handoff.md`.
