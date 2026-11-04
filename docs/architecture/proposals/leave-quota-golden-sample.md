# Leave quota golden sample (organization.leave, W2)

Status: draft for sign-off, 2026-11-04, one-design, card #1279. Companion of `organization-leave.md` (the BC spec).
Basis: intent `docs/decisions/2026-11-02-162741-one-lead-同意-旧系统对照-用来计算预期值.md` (user: 「同意，旧系统对照，用来计算预期值以及对照验证」): 5–8 synthetic people × one leave year, expected values computed by the legacy system, real data never enters the repo or a session, comparison on aggregates only.

## 0. What the repo already knows about the legacy quota: nothing usable

Read on origin/main 2026-11-04 (verified by search): no document describes how the legacy system computed leave entitlement, accrual, carry-over or rounding. The only legacy leave object described is a read-only leave record view used for timesheet minimum hours (`docs/data/evidence/dba-handoff/timesheet-filing-duty.sql`). No evidence SQL touches a balance, quota or entitlement table. Statutory rules exist as a law note (`docs/architecture/frameworks/notes/leave-wage.md`), but the company's own entitlement standard is not recorded anywhere. So the golden sample has two jobs: (1) discover how the legacy system works, (2) fix expected values for the synthetic cases. Step 1 comes first and is the long pole.

## 1. The ten computations the user must run (aggregates only)

Rules for every query: read-only evidence account only; no names, employee numbers, login names, ids or free text in the output; every result cell that counts fewer than 5 people is reported as `<5` (or merged into a neighbouring band); output as CSV with the headers given; if a query needs a table the user cannot name, answer step L0 first and tell lead the names, do not guess.

| id | question the legacy system answers | what to run (output columns) |
|---|---|---|
| L0 | Where does the legacy system keep leave data? | Metadata only, no rows: every table or view whose name or column names suggest leave, vacation, holiday, quota, balance, entitlement, carry-over, overtime, comp time. Output: `object_name, column_name, data_type, row_count` (row count per object, no content). |
| L1 | Which leave types exist and which count against a quota? | One row per leave type: `leave_type_label, is_paid_flag_or_equivalent, counts_against_quota_flag_or_equivalent, unit (hour/day/half-day), request_count_last_12_months`. Type labels are business labels, not personal data. |
| L2 | What is the entitlement rule? | The entitlement configuration itself (rules, not people): the rule or parameter table that maps service years (or position, city, entity) to days, with its version dates. Output the configuration rows as they are; if there is no configuration table and quotas are typed per person, say so and run L3. |
| L3 | What entitlement values appear per service-year band? | Group by `service_years_at_year_start` (whole years, 0–30+, and say how the legacy system defines service years: company start date, or cumulative including other employers) and report `people_count, min_days, max_days, avg_days, count_with_fraction_zero, count_with_fraction_half, count_with_other_fraction` for the annual-leave type, leave year 2025 and 2026. |
| L4 | How is the first year prorated and rounded? | People hired in 2025: group by `hire_month`; report `people_count, min_days, max_days, avg_days, count_with_other_fraction`. Same for 2026 hires so far. |
| L5 | How is a separation year handled? | People separated in 2025: group by `separation_month`; report `people_count, avg_entitlement_days, avg_used_days, count_used_more_than_entitlement, avg_excess_days`; and whether any payout or deduction for the excess exists (count of records, no amounts per person). |
| L6 | Is there carry-over, with what cap and expiry? | For the 2025→2026 boundary: `people_with_carry_over, min, max, avg carried days`; the carry-over expiry date field (distribution by month) or "none"; whether unused days vanish at year end (count of people whose year-end balance was > 0 and whose next-year opening balance is 0). |
| L7 | Can the balance go negative? | `count_people_with_negative_balance_at_any_time_2025, min_balance_days`; `count_approved_requests_exceeding_balance` and the setting or flag that allows it, if the legacy system has one (leave type level or global). |
| L8 | What does unpaid leave do to the entitlement? | People with unpaid-leave days in 2025, grouped by band of unpaid days (0, 1–14, 15–59, 60+): `people_count, avg_entitlement_days` split by same service-year band as L3 so the difference is visible; also whether the legacy system removes the whole year's entitlement above some threshold (report the threshold if configured). |
| L9 | What happens when someone moves between legal entities? | People whose legal entity changed in 2025: `people_count, avg_entitlement_before_move, avg_entitlement_after_move, count_balance_reset_to_zero, count_balance_carried`. If the legacy system has one entitlement per entity, say how many people hold two. |
| L10 | How does comp time (补休) behave? | Comp-time grants in 2025: `grant_count, avg_hours_granted`; validity or expiry rule (field and distribution of validity days); `count_expired_unused`, `count_converted_to_pay_flag_or_equivalent`; whether granting needs an approved overtime record. |

Answer format: one CSV per query, file name `legacy-L<n>.csv`, delivered to the test engineer outside the repo; only the derived per-case expected values (section 3) are committed, never the CSVs.

If a legacy test or staging instance exists where synthetic people can be created, the better path is: the user (or an administrator) creates the cases in section 2 there and reads the computed quota values per case from the screen; those values are the expected values and L3–L10 then only confirm the rules. **Ask lead: does such an instance exist? No production writes ever (CLAUDE.md baseline 2).**

## 2. The synthetic cases (eight people, leave year 2026 = calendar year)

All people, dates, entities and ids are invented; names `G1`..`G8`. "Treatment T-n" refers to a business treatment the user decides (list in `organization-leave.md` section 9); the expected value of a case that depends on one stays marked pending until the user decides and the legacy value is known.

| case | situation | edge it covers | depends on |
|---|---|---|---|
| G1 | employed the whole year, service years 4 at 1 Jan, crosses no band | baseline entitlement and usage | T-1 formula |
| G2 | service years 9 on 1 Jan, completes 10 years on 15 Jun | band change inside the year: which date decides the band | T-1, T-2 |
| G3 | hired 1 Apr 2026 with 3 prior years elsewhere | first-year proration, rounding, service-years basis | T-1, T-3 rounding |
| G4 | separated 30 Sep 2026, took more days than accrued | separation proration, no claw-back, no negative | T-3, T-4 negative |
| G5 | unpaid leave 1 Jul to 31 Aug 2026 | effect of unpaid leave on entitlement | T-5 |
| G6 | moves from legal entity A to B on 1 Sep 2026 | balance follows the person or resets | T-6 |
| G7 | 5 days carried from 2025, usage in Q1 and Q4 | carry-over cap, expiry, use order (carry-over first) | T-7 |
| G8 | 8 hours rest-day overtime on 3 May 2026 granting comp time, partly used, partly expired | comp-time grant, validity, expiry | T-8 |

Each case also lists one request that exceeds the balance (to test the refusal or the allowance, T-4) and one request on a non-workday inside the range (non-workdays are not deducted; uses `organization.attendance` work calendar of the person's entity on that day).

## 3. File shape of the expected values

One YAML file per case in `backend/src/test/resources/golden/leave-quota/<case>.yaml`, written by one-test from the user's legacy results; fields:

```yaml
case: G3
inputs:
  employment: {hire_date: 2026-04-01, prior_service_years: 3, separation_date: null}
  legal_entity_periods: [{entity: A, from: 2026-04-01, to: null}]
  unpaid_leave_periods: []
  carry_in_days: 0
  requests: [{type: ANNUAL, from: 2026-06-10, to: 2026-06-12, expected_decision: ACCEPT}]
treatments: {T-1: <value>, T-3: <value>}     # the decided parameters the expected values assume
expected:
  entitlement_days: <decimal>
  used_days: <decimal>
  held_days: <decimal>
  balance_days: <decimal>
  carry_out_days: <decimal>
  request_decisions: [ACCEPT]
legacy_source: {query: L3, run_on: <date>, note: "aggregate or instance screen"}
```

Units: the golden file states quantities in minutes as well as days (`entitlement_minutes`), because leave is stored in whole minutes (`docs/business/rules/timesheet-filing-rules.md:118`); one day = the entity's standard hours for that day.

## 4. Done-when per case (for the test engineer)

For each case G1..G8: (1) the acceptance test opens the year's account through the public commands only (hire, separation, unpaid leave, transfer events are delivered through the real events, not SQL); (2) every `expected` field equals the value read back through the read API; (3) re-delivering any input event changes nothing (idempotent); (4) the audit entry of each balance-changing command exists with the register text from the spec; (5) a case whose treatment is still pending is not written yet, and its absence is listed in the test report, not skipped silently.

Case-specific checks: G2 band change takes effect on the date the treatment says; G4 balance never below zero after separation; G6 totals across the move equal the single-entity total (or the reset, per T-6); G7 carry-over is consumed before the new year's days and expires at the T-7 date; G8 grant, partial use and expiry produce three ledger lines and the expired amount is reported to the payroll seam (W4) as an event, not paid here.
