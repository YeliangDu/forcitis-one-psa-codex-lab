### RULE-01: OH allocation – share each overhead pot by direct-labour cost share, with rounding remainder on the largest line
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/allocation/OhAllocationCalculator.java:196-352`
**Plain English:** Each timesheet ledger line gets four OH amounts: department management, department sales, company management and company sales. Each one is the OH pot times the line's share of labour cost. Any rounding remainder in a pot goes to that pot's largest line, so every pot adds back exactly.
**Specification:**
  Given delivery dept D has timesheet basis lines of 600.00, 300.00 and 100.00 (dept base 1,000.00), the company basis is 2,000.00, D's department management OH (own + service) is 100.00 and the company-tier management OH is 300.00
  When allocation is derived
  Then deptRatio = 0.6000000000 / 0.3000000000 / 0.1000000000, and deptMgmt = 60.00 / 30.00 / 10.00
   And companyRatio for the 600 line = 0.3, so companyMgmt = 300.00 × 0.3 = 90.00
  Given instead a 100.00 pot split over 3 equal lines (ratio 0.3333333333 → 33.33 each, sum 99.99)
  Then the 0.01 remainder goes to the first line with the largest value: 33.34 / 33.33 / 33.33. That line gets tail flag bit0 (dept tier); bit1 is the company tier
**Parameters:** RATIO_SCALE = 10, HALF_UP; AMOUNT_SCALE = 2, HALF_UP; tail bitmask 1 = region, 2 = HQ
**Edge cases handled:** Non-timesheet ledger lines get 0-ratio, 0-amount placeholder lines. Ties for largest line go to the earliest line. No remainder means no tail flag. If company basis is 0, companyRatio = 0. The invariant check (lines 266-304) throws if Σ lines ≠ pot per tier and nature, or if Σ allocated ≠ Σ picked pool lines. The parallel path produces identical results.
**Suspected defect:** Docs (09 rules list COST-R45) call this the "largest remainder method". The code does not do that: it puts the whole remainder on the largest-amount line.
**Confidence:** High — the formula is explicit.

### RULE-02: OH tier routing and carry-forward when the labour base is zero
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/allocation/OhAllocationCalculator.java:44-110`
**Plain English:** Each OH pool line is filed by tier (delivery-own / service / public) and by nature (management / sales):
- Public OH goes to the company pot.
- Service OH goes to its parent delivery department.
- Own OH goes to its own department, or to the successor department if one is configured.

If the target labour base is ≤ 0, the line is not allocated and waits for a later period.
**Specification:**
  Given OH line {tier = own, costOrg = 10, belongDelivery = 20, mgmt, 5,000.00}, dept 20 basis 80,000.00 and dept 10 basis 0
  When aggregated
  Then 5,000.00 lands in dept 20's own-mgmt bucket and is picked
  Given OH line {tier = service, belongDelivery = 30} and dept 30 has no timesheet basis
  Then it goes to pendingReasons (costOrg, amount), is not picked, and stays in the pool
**Parameters:** tier codes 0 own / 1 service / 2 public; nature 0 = management, otherwise sales
**Edge cases handled:** Public OH is deferred when the company basis is ≤ 0. A null target dept means deferral. A successor is used only if belongDeliveryId ≠ costOrgId (#1548).
**Confidence:** High.

### RULE-03: OH basis is timesheet (source_type 0) not-tax cost; period 13 uses the full-year base
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/cost/projectcost/allocation/AllocationOrchestrator.java:108-146`
**Plain English:** The allocation denominator is the period's direct-labour ledger cost. For the 13th (annual adjustment) period it is the sum of periods 1–12. Each pool line's tier is re-resolved from its cost centre's mapping as of the period-end date.
**Specification:**
  Given period 2026-13 for company C and timesheet ledger rows across 2026-01..12 totalling 12,000,000.00
  When the allocation is built
  Then the basis = 12,000,000.00 (readByYear), not just the 13th-period rows
  Given a pool line whose cost centre has no mapping as of the period-end
  Then the build fails with IllegalStateException
**Parameters:** basis filter `source_type == 0` (LedgerBasisRow.java:37-39); as-of date = period end
**Edge cases handled:** REVERSAL pool lines are taken out of tiering and handled by RULE-04.
**Suspected defect:** Docs COST-R39/R40 still say "wpt0/1 work-package hours". Code uses source_type = 0 because work_package_type is never populated (#875).
**Confidence:** High.

### RULE-04: Reversing an OH accrual mirrors the original allocation in proportion
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/allocation/ReversalMirrorCalculator.java:31-90`
**Plain English:** A negative (reversal) OH pool line is spread back over the lines of the original period's effective allocation, for the same nature. Each line's dept and company parts are scaled by reversal ÷ original nature total. The remainder goes to the largest line's company column.
**Specification:**
  Given the original-period management allocation is line A (dept 300.00, company 200.00) and line B (dept 400.00, company 100.00), total 1,000.00
  When reversal pool line −100.00 (mgmt) is mirrored
  Then A = (−30.00, −20.00) and B = (−40.00, −10.00), Σ = −100.00
**Parameters:** SCALE = 2, HALF_UP
**Edge cases handled:**
- Original nature total = 0 throws.
- Lines with 0 for that nature are skipped.
- All lines skipped throws.
- Σ ≠ reversal throws.
- A missing original pool line or effective batch throws (orchestrator 165-176).

**Suspected defect:** Docs COST-R44 say "分摊无独立红冲入口" (allocation has no separate reversal). The code does have this reversal-mirror path.
**Confidence:** High.

### RULE-05: Timesheet three-element (salary/social/fund) cent rounding anchored to the rounded total
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/ingest/TimesheetElementRounding.java:24-53`
**Plain English:** The three labour elements are each rounded to cents. Any cent difference from round(total) goes to the element with the largest raw absolute value, so the elements always add up to the rounded total.
**Specification:**
  Given salary 100.004, social 20.004 and fund 10.004
  When normalised
  Then total = 130.01 and the elements are 100.01 / 20.00 / 10.00 (the 0.01 remainder goes to salary)
**Parameters:** scale 2, HALF_UP; tie priority salary > social > fund
**Edge cases handled:** null is treated as 0. Negatives are compared by absolute value. Elements that net to 0 are later dropped (CostSummaryViewReader.java:433-441). Timesheet amount equals not-tax amount (no input VAT).
**Confidence:** High.

### RULE-06: Person-month rounding true-up to the payroll accrual
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/rounding/PersonMonthRoundingCalculator.java:16-74`
**Plain English:** For each person, salary month and labour element, payroll accrual minus landed cost is posted to the largest landed row. This only happens when the gap is small enough to be explained by per-row rounding.
**Specification:**
  Given person P in 2026-07 with 3 salary rows landed totalling 2,999.99 and accrual 3,000.00
  When the true-up runs
  Then the bound = 3 × 0.005 + 0.005 = 0.02, and +0.01 goes to the largest row (ties go to the lowest rowId)
  Given the gap is 0.03 instead
  Then it is skipped as "business gap"
**Parameters:** PER_ROW_TOLERANCE 0.005, BASE_TOLERANCE 0.005; adjustment rounded to 2 dp HALF_UP
**Edge cases handled:** Skip when:
- the person-month has open exceptions or exemptions;
- there is no accrual row;
- approved hours ≤ 0;
- diff = 0;
- the line is not a labour cost type.

**Confidence:** High.

### RULE-07: Year-end bonus is split to project terms by the person's annual hours (cent-exact largest remainder)
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/ingest/AnnualBonusSplitCalculator.java:66-123`
**Plain English:** Each term's share = bonus × (person's hours on that term ÷ person's total project hours for the year). Gross and not-tax amounts are split separately. Each share is floored to the cent, then leftover cents go one at a time to the largest fractional remainders.
**Specification:**
  Given a bonus of 1,000.00 (gross = not-tax) and hours 1 / 1 / 1 on three terms
  When split
  Then floor = 333.33 × 3 (99,999 cents), and the 1 leftover cent goes to index 0, giving 333.34 / 333.33 / 333.33
**Parameters:** intermediate scale 10, HALF_UP; cents via FLOOR; tie order = lower index
**Edge cases handled:**
- Empty basis or total hours ≤ 0 throws. The caller routes these to the OH pool (CostRoutingService.java:209-211).
- Source amounts are pre-rounded to cents.
- A conservation assertion is enforced.

**Confidence:** High.

### RULE-08: Cost routing chain and how expense nature is set
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/ingest/CostRoutingService.java:101-235`
**Plain English:** Every cost source line is routed in a fixed order to exactly one destination (sink). OH nature is SALES when fee_type = 1, otherwise MANAGEMENT. The order is:
1. Already ingested → SKIP.
2. Exempt flag → EXEMPT.
3. Zero-hour salary → OH pool.
4. Monthly bonus with fee_type 0 → error.
5. Year-end bonus with fee_type 0: whitelisted level with a person → LEDGER (split); otherwise → OH pool.
6. Opportunity: lost → pool; won and project exists → attach to the project; otherwise → in transit.
7. fee_type 0: with a term → LEDGER; without a term → error.
8. fee_type 1/2 → pool.
9. Any other fee_type → error.
**Specification:**
  Given a year-end bonus line with fee_type 0 and level "m5"
  When routed
  Then OH pool, nature MANAGEMENT
  Given level " t2 " with personId set
  Then LEDGER (split by RULE-07)
**Parameters:** annual-bonus ledger levels default `T1,T2,T3,M3,M4` (CostProjectCostBeanConfig.java:75, config `fst.cost.collection.annual-bonus.ledger-levels`); fee_type 0 project / 1 sales / 2 management
**Edge cases handled:** Levels are trimmed and upper-cased before comparison. The pool tier is resolved from the cost centre, not the cost org (#1203).
**Confidence:** High.

### RULE-09: Zero-value cost never enters WIP; negative cost does
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/cost/projectcost/carry/CarryCandidateReaderImpl.java:42-58`
**Plain English:** Candidates to enter WIP are pending ledger rows of the period and company with not-tax ≠ 0 (this also excludes NULL). Negative amounts, such as reversals, are carried into WIP.
**Specification:**
  Given ledger rows with not-tax 0.00, −120.00 and 500.00 in period P
  When WIP-enter candidates are read
  Then −120.00 and 500.00 are selected; 0.00 is not
**Parameters:** `entry_status = 0`, `notax_amount <> 0`
**Edge cases handled:** Zero-value timesheet elements are dropped at ingest (CostSummaryViewReader.java:433-441). The expense flow allows negative and zero values, while the timesheet flow rejects negatives (CostValidationService.java:170-191).
**Suspected defect:** Docs COST-R52 select candidates by `period_id ≤ current`. The code uses `period_id = current` (`.eq`).
**Confidence:** Medium. Question for the SME: should late ledger rows from earlier open periods be picked up by the current WIP-enter batch?

### RULE-10: WIP stage on entry and stage advance (POC exactly 100 and contract returned)
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/carry/CarrySelectorImpl.java:53-124`
**Plain English:** On entry:
- Contract not returned → stage 1 (pre-contract).
- Returned and term POC = 100 at the period's month-end snapshot → stage 3 (completed).
- Otherwise → stage 2 (in progress).

Advances: 1→3 or 1→2 only once the contract is back; 2→3 when POC = 100.
**Specification:**
  Given a term with contract returned and month-end POC 99
  When entering WIP for 2026-07
  Then stage 2
  Given a stage-1 entry with POC 100 but contract not returned
  When advancing
  Then not selected (stays at 1)
**Parameters:** POC_COMPLETED = 100 (integer, strict equality); snapshot key `YYYY-MM`
**Edge cases handled:** Null POC is treated as not complete. For the 13th or adjustment period the month is null, so POC is null.
**Confidence:** High.

### RULE-11: WIP write-off selection – one revenue confirmation releases one whole WIP entry; leftovers go cost-only or wait
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/carry/CarrySelectorImpl.java:126-237`
**Plain English:** Write-off works per project term, considering only in-WIP entries. Stage 4 (impairment) is excluded. Eligible terms are those that are completed (stage 3), belong to a terminated project, or are on the manual list.

Pairing: the i-th unpaired confirmed revenue (dated ≤ period end) is paired with the i-th WIP entry, and the entry's full amount moves (FULL). Leftover entries:
- Terminated project or manual list → cost-only.
- Normal terms → cost-only if any pairing happened or the term is fully confirmed; cost-only with reason 2 if the term amount is 0; otherwise parked.
**Specification:**
  Given a stage-3 term with WIP entries 1,000.00 / 200.00 / 50.00 and one unpaired revenue CV-1
  When write-off is selected
  Then 1,000.00 is FULL paired with CV-1; 200.00 and 50.00 are COST_ONLY tagged "CV-1"; all three go 3·0 → 3·1
  Given the same term with 0 revenues and not fully confirmed
  Then all three are parked
**Parameters:** reason codes 1 = terminated, 2 = zero-amount term, 3 = manual; channel priority terminated > manual > normal
**Edge cases handled:**
- Terminated and manual lines keep their original stage and snapshot the real backed/POC values.
- Revenue left over after the WIP entries run out is not posted alone.
- An empty manual-directive date returns no manual matches.

**Suspected defect:** Docs COST-R60 (write-off cutoff by business_date ≤ latest confirmation date) are not implemented. Pairing is by count, so one confirmation of any size releases a whole entry and pulls every other entry on the term into cost-only.
**Confidence:** Medium. Question for the SME: should pairing be by amount or by business-date cutoff, rather than one revenue confirmation per WIP entry?

### RULE-12: Posting rule matching and voucher-line rendering
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/voucher/PostingRuleEngine.java:185-286`
**Plain English:** A rule is picked in this order: same carry_type → non-default rules whose `token==N && …` trigger matches → default rule. Within each level, an exact company match beats a company-null rule, and more than one candidate is an error. Each rule line takes its amount from the fact's amount_formula token, and lines with a null or 0 amount are dropped. Every voucher must balance.
**Specification:**
  Given carry_type 2, a fact with to_wip_stage = 3 and not-tax 800.00 for company 01, and two default rules (company 01 and null)
  When rendered
  Then the company-01 rule is used, Dr main cost 800.00 / Cr WIP 800.00, and the voucher balances
  Given a fact with no revenue tokens
  Then the revenue lines are omitted (cost-only voucher)
**Parameters:** dynamic subject prefix `DYN-`; supplier-required subjects default {A123} (config `fst.cost.carry.supplier-required-subjects`)
**Edge cases handled:**
- An unresolved DYN subject throws.
- A133-style supplier subject without a supplier aux throws.
- A missing required aux throws (null_mode 0), or is skipped / defaulted.
- Summary placeholders {periodName}, {deliveryCenter}, {term} are filled, with missing values blanked.

**Suspected defect:** Docs COST-R54 describe 3-level matching on (carry_type, source_type, company). The code matches on carry_type → trigger condition → company. Docs also say "同一 carry_type 应唯一" (one rule per carry_type, design doc line 205).
**Confidence:** High.

### RULE-13: Vouchers are aggregated per company × voucher word × department
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/voucher/PostingRuleEngine.java:84-181`
**Plain English:** Per-line vouchers are merged into one voucher per (company, voucher word, department aux). Within it, lines with the same subject, debit/credit side and aux signature are summed across terms. Each merged voucher must balance when posting; preview may show unbalanced vouchers.
**Specification:**
  Given 3 write-off lines for dept D (Dr 6401 100.00 / 200.00 / 300.00, each with matching Cr)
  When aggregated
  Then one voucher for D with Dr 6401 600.00 and the summed credits, with batchLineId = null
**Parameters:** strictBalance = true for posting, false for preview
**Edge cases handled:** Lines with no department aux group under the "" key. The preview and posting paths share the same grouping key.
**Confidence:** High.

### RULE-14: Write-off voucher amounts and credit account derivation
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/cost/projectcost/carry/PostingVoucherDrafterImpl.java:507-568`
**Plain English:** The cost leg uses the batch line's not-tax amount. The credit account comes from the cost-type → U8C account mapping in the fee_type '0' (project cost) tier only, and must be unambiguous. The not-tax amount goes to the prepay token if the resolved account is a supplier-required subject, otherwise to the expense token. A FULL write-off also brings the paired confirmation's tax-inclusive amount (contract asset), not-tax amount (revenue) and tax.
**Specification:**
  Given a FULL line with not-tax 800.00 and paired cuValue {complete 1,060.00, no_tax 1,000.00, tax 60.00}
  When the fact is built
  Then tokens = notax_amount 800.00, expense_notax_amount 800.00, complete_amount 1,060.00, TBD_REVENUE_NOTAX 1,000.00, TBD_TAX 60.00
  Given cost type X maps to two different accounts under fee_type 0
  Then X is treated as unmapped and the batch fails with a readable error
**Parameters:** `FEE_TYPE_PROJECT_COST = "0"` (lines 652-672); tokens as listed
**Edge cases handled:** Department falls back to the ledger's name snapshot for the summary only. A missing paired cuValue throws.
**Confidence:** High.

### RULE-15: Revenue-return red reversal – reverse the whole original cost, re-render the voucher and negate it
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/cost/projectcost/carry/PostingVoucherDrafterImpl.java:125-209`
**Plain English:** For each revenue return (cuvalue type 1), the original write-off lines with `paired_cu_value_code` = the return's origin_code (same company) are reversed at their full original not-tax amount. The red voucher is re-derived from the current forward rules, aggregated per return slip × department, and every amount is negated. WIP goes 3·1 → 3·0, and the P&L ledger gets a negated copy of the forward line.
**Specification:**
  Given original FULL line 800.00 and COST_ONLY line 200.00, both tagged CV-1, and return R1 with origin CV-1 for −300.00 not-tax
  When red reversal is posted
  Then both lines are reversed (−800.00, −200.00), giving one red voucher per dept with all amounts negated; the summary is "红冲·{period}·{dept}·产值退回冲销（产值单号 CV-1）"
**Parameters:** carry_type REVENUE_RED_REVERSAL; P&L reversal trigger_type 2 / pl_type 9 (PlLedgerDO.java:129-157)
**Edge cases handled:** No origin_code, no match, or cross-company only → not reversible. A missing origin line throws.
**Suspected defect:**
- The return's own amount is never used: a partial return reverses the entire cost.
- ReversalLinkResolverImpl.java:45/112 comments say only FULL lines carry the code. In fact COST_ONLY lines also carry it (WriteOffLine.java:78-83), so they are reversed too.
- The red voucher uses the current rules and mappings, while the P&L ledger copies the original account (PlLedgerDO.java:140). The two can diverge if the mapping changed.

**Confidence:** Medium. Question for the SME: should a partial revenue return reverse cost in proportion to the returned amount, or always reverse all linked cost?

### RULE-16: WIP aging impairment flag after 720 days
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/cost/projectcost/carry/WipImpairmentSweepService.java:69-175`
**Plain English:** After a write-off batch posts, every in-WIP entry at stage 1, 2 or 3 is checked. Days are counted from the end of the period it entered WIP to the end of the current period; at ≥ 720 days it moves to stage 4 (impairment). This is frozen: it is excluded from every write-off channel and writes movement type 7 with no voucher.
**Specification:**
  Given an entry that entered in a period ending 2024-06-30, with the current period ending 2026-06-30 (730 days)
  When the 2026-06 write-off batch posts
  Then stage → 4 and a movement is recorded with the entry's amount
**Parameters:** `fst.cost.wip.carry.impairment-aging-days`, default 720 (values ≤ 0 fall back to 720)
**Edge cases handled:** A missing period end means no sweep. A missing entered period end is never treated as aged. A CAS guard prevents double flips.
**Confidence:** High.

### RULE-17: WIP period balance and report increase/decrease use the absolute movement amount
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/cost/projectcost/wipsnapshot/WipPeriodSnapshotMapper.java:53-113`
**Plain English:** An entry's balance at a period's close = Σ |not-tax| of increase movements (enter WIP, write-off reversal) − Σ |not-tax| of decrease movements (write-off, termination, exemption). Aging = period end − earliest business date of entries with a positive balance.
**Specification:**
  Given entry E entered with +500.00 and later written off
  Then balance = 500 − 500 = 0
  Given entry F entered with −120.00 (negative cost is allowed per RULE-09)
  Then balance = +120.00
**Parameters:** direction map in WipMovementDirection.java:15-28; cutoff = `create_time ≤ period closed_time`
**Edge cases handled:** Neutral movements (advance, reclass, impairment) only set stage and status.
**Suspected defect:** ABS() turns negative WIP entries into positive balances. WipReportMapper.java:52-61 (increase/decrease) does the same, so WIP balance and the report overstate WIP by twice any negative cost entered.
**Confidence:** Medium. Question for the SME: should negative cost entries reduce WIP? If so, ABS should only normalise the sign of red-reversal and decrease movements.

### RULE-18: Three-tier project profit and rates
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/cost/projectcost/profitreport/QueryProjectProfitUseCase.java:551-618`
**Plain English:**
- Gross = revenue − main cost (P&L ledger, written-off basis) − opportunity selling cost.
- BU profit = gross − dept mgmt − dept sales OH.
- Full profit = BU − HQ mgmt − HQ sales OH.
- Each rate = profit × 100 ÷ revenue (2 dp); a project's contribution rate = its full profit ÷ the filtered total full profit.
**Specification:**
  Given revenue 1,000,000.00, main cost 600,000.00, opp 20,000.00, dept OH 50,000.00 + 10,000.00 and HQ OH 30,000.00 + 5,000.00
  Then gross 380,000.00 (38.00%), BU 320,000.00 (32.00%), full 285,000.00 (28.50%)
**Parameters:** rate scale 2, HALF_UP; revenue 0 gives rate 0.00
**Edge cases handled:** Term rows carry opp = 0. A full profit < 0 is flagged as a loss. Results are not cached while allocation is incomplete.
**Confidence:** High.

### RULE-19: Splitting opportunity cost across terms – drawer and export use different formulas
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/cost/projectcost/profitreport/QueryProjectProfitUseCase.java:151-295`
**Plain English:**
- The detail drawer splits project opportunity cost by term contract amount, with the remainder on the last term. If all contract amounts are missing or the total is ≤ 0, it splits evenly.
- The export Sheet2 always splits evenly across rows, and those rows include the "(未指定条款)" (unassigned-term) bucket.
**Specification:**
  Given opp 10,000.00 and terms T1 (contract 300,000) and T2 (contract 100,000)
  Then the drawer shows T1 7,500.00 / T2 2,500.00, while the export shows 5,000.00 / 5,000.00
**Parameters:** scale 2, HALF_UP; remainder goes to the last row
**Edge cases handled:** With no terms, the drawer keeps the opportunity "carry" row.
**Suspected defect:** The drawer comment (line 153) claims it matches Sheet2's fallback, but Sheet2 never weights by contract amount. The two outputs give different gross profit for the same term.
**Confidence:** Medium. Question for the SME: which basis should the export use?

### RULE-20: Contract asset and receivable composition; zeroed for closed or terminated projects
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/cost/projectcost/profitreport/QueryProjectProfitUseCase.java:661-721`
**Plain English:**
- Net invoiced = invoiced − credit notes.
- Contract asset = confirmed value incl. tax − net invoiced; net = asset − impairment.
- AR = net invoiced − collected (can be negative, meaning advance receipt); net = AR − bad debt.
- All six figures show 0 for projects that are 已关闭 (closed) or 终止 (terminated).
**Specification:**
  Given confirmed value 1,060,000.00, invoiced 800,000.00, flushed 50,000.00, collected 600,000.00 and impairment 10,000.00
  Then net invoiced 750,000.00, contract asset 310,000.00 (net 300,000.00), AR 150,000.00
**Parameters:** suppressed statuses: FSTConstant.ProjectStatus.已关闭 and 终止
**Edge cases handled:** Missing extras count as 0.
**Confidence:** High.

### RULE-21: Labour reclass – U8C credit vs PSA landed cost, by department, with tolerance
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/laborreclass/LaborReclassDiffCalculator.java:49-278`
**Plain English:** For each labour element, PSA landed cost per person is split across that person's salary-archive departments in proportion to the archive amounts (remainder on the last row). The difference = U8C credit − PSA, per U8C department. The line blocks posting unless |diff| ≤ tolerance or cause ⑧ applies (U8C higher, both sides non-zero, no PSA gap); ⑧ is non-blocking and the excess stays in U8C.
**Specification:**
  Given dept D with U8C 50,000.00, PSA 49,999.40 and tolerance 1.00
  Then diff 0.60, cause ROUNDING, non-blocking
  Given U8C 52,000.00 and PSA 50,000.00 with no unapproved-hours gap
  Then diff 2,000.00, cause RESIDUAL_IN_U8C, non-blocking
**Parameters:** `fst.cost.labor-reclass.diff-tolerance` default 1.00 (LaborReclassPreviewService.java:86-97); MATCH_EPS 0.01; scale 2, HALF_UP
**Edge cases handled:** Causes for no department, unmapped OA department, not collected, unapproved timesheet ⑦, archive mismatch and people with no archive (blocking row).
**Confidence:** High.

### RULE-22: Labour reclass voucher – reverse U8C credit in full, debit PSA buckets, absorb small differences
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/laborreclass/LaborReclassVoucherAssembler.java:27-131`
**Plain English:**
- Credits (account × dept) are reversed in full. For ⑧ rows, the excess is first cut from the largest credit rows.
- Debits = PSA cells by bucket (project / sales / management) × account × dept.
- Non-blocking, non-⑧ differences are debited to the element's difference account.
- A negative net amount moves to the other side, and 0 amounts are dropped.
**Specification:**
  Given credit 6602/D 52,000.00 and a ⑧ row of 2,000.00
  Then the reversal line = Dr 6602/D 50,000.00 (52,000 − 2,000)
**Parameters:** bucket summaries "按工时重分类-项目 / 销售 / 管理"; scale 2, HALF_UP
**Edge cases handled:** Cells with no account or department mapping are skipped (the problem is already recorded).
**Confidence:** High.

### RULE-23: OA→U8C credit matching for labour reclass
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/cost/projectcost/laborreclass/OaCreditResolver.java:42-166`
**Plain English:** Each OA accrual row is matched to a line of its referenced U8C voucher in three rounds:
1. Same amount and account, preferring the interface-mapped department and then the psa_dept_u8c department.
2. Interface-merged groups whose sum equals one line; that line is split back per OA row, with the remainder on the last row.
3. A unique remaining candidate.

Unclaimed lines on whitelisted vouchers are kept; lines on other vouchers are excluded.
**Specification:**
  Given OA rows 3,000.00 + 2,000.00 for interface dept A03 and U8C line A03 for 5,000.10
  Then the credits are 3,000.00 and 2,000.10
**Parameters:** amount compare at scale 2, HALF_UP
**Edge cases handled:**
- Rows in the wrong period are flagged as a problem.
- A payment-only voucher with no labour lines is skipped.
- Forms on external payroll accounts with no lines are skipped.
- Ambiguous matches are flagged.

**Confidence:** High.

### RULE-24: Legacy WIP/cost carry-forward – labour = person-days × daily wage; project bonus 5%
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/wip/WipServiceImpl.java:735-766`
**Plain English:** On the old WIP carry screens (`/fst/accounting/wip`, still exposed):
- WIP amount for a term = sum of 7 cost categories (labour, daily, travel, payment request, special payment, outsourcing, info service).
- Project bonus = confirmed amount × 5%.
- Labour cost per row = person-days × daily wage, rounded to 2 dp (CarryForwardServiceImpl.java:535-548).
**Specification:**
  Given confirmed amount 200,000.00 and term costs labour 80,000.004 + daily 5,000.00
  Then project bonus 10,000.00 and WIP 85,000.00
**Parameters:** `FSTConstant.项目奖金比例 = 0.05` (yudao-module-fst-api FSTConstant.java:282); HALF_UP 2 dp; currency hardcoded "CNY"
**Edge cases handled:** A null daily wage is treated as 0. Rows with WIP amount 0 are filtered out.
**Suspected defect:** These parallel legacy carry-forward endpoints coexist with the new carry engine (RULE-11 to RULE-15), so the two can produce conflicting postings.
**Confidence:** Medium. Question for the SME: are the legacy `/fst/accounting/wip` and `/cost` carry-forward endpoints retired, and is the 5% bonus still policy?

### RULE-25: Legacy carry voucher – debit = Σ credits per company × U8C dept × subject
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/wip/CarryForwardServiceImpl.java:134-197`
**Plain English:** For each U8C department in a company, the legacy voucher has one credit line per subject (sum of amounts, 0 skipped) and one debit line on the fixed WIP or COST debit subject equal to the sum of the credits. Each line is rounded to 2 dp independently.
**Specification:**
  Given dept D with subjects 5001 (100.004) and 5002 (200.004)
  Then credits are 100.00 and 200.00, and the debit is round(300.008) = 300.01, which is unbalanced by 0.01
**Parameters:** debit subjects U8cSubjectEnum.WIP_借 / COST_借; HALF_UP 2 dp
**Edge cases handled:** Subjects with a zero total are skipped.
**Suspected defect:** The debit is rounded from the unrounded sum while each credit is rounded separately, so the voucher can be off by a few cents.
**Confidence:** High.

---
**Coverage:** I read in full: OhAllocationCalculator, ReversalMirrorCalculator, PersonMonthRoundingCalculator, TimesheetElementRounding, AnnualBonusSplitCalculator, CostRoutingService, CostValidationService, PostingRuleEngine, VoucherDraft, CarrySelectorImpl, WriteOffLine, PostingVoucherDrafterImpl, the LaborReclass Diff/Resolver/Assembler classes, WipImpairmentSweepService, QueryProjectProfitUseCase (lines 120–722), AllocationOrchestrator, WipPeriodSnapshotMapper and the relevant sections of WipReportMapper, ReversalLinkResolverImpl, PlLedgerDO, and the legacy carry segments in WipServiceImpl and CarryForwardServiceImpl.
I only grep-skimmed: the opening/* use cases, prepostingcheck/*, recon/*, presalescost/*, OpportunityExpense, ExpensePostingCheckServiceImpl, CostCarryForwardServiceImpl, the rest of WipServiceImpl (aging and balance pages), and the openspec specs (not opened).
I may have missed: opening/initial-balance (期初) carry-bridge amount logic, monthly-bonus accrual reversal netting, recon tolerances and disposition math, OH pool expense-nature override, and the legacy WIP aging and ending-balance calculations (WipServiceImpl ~1240–1600). No credentials were found, and no instruction-like text aimed at automated tools was found in the code I read.