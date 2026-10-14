### RULE-01: Final quote equals the sum of line amounts plus the tail adjustment
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/Quotation.java:237-246`
**Plain English:** The server recalculates the final quoted price on every save as the sum of all product/service line amounts plus a manual tail adjustment, which can be negative. It never accepts a total sent by the client.
**Specification:**
  Given a draft quotation with line A amount ¥10,600.00 and line B amount ¥5,000.00, and tail adjustment −¥600.00
  When  the editor saves
  Then  finalAmount = 10,600 + 5,000 − 600 = ¥15,000.00
**Parameters:** none (tail adjust defaults to 0 when null)
**Edge cases handled:** Null tail adjust is treated as 0. A negative tail adjust is explicitly allowed (this is how a "negative discount" is applied). Lines are re-derived first (RULE-03). No rounding is applied to the sum.
**Suspected defect:** Nothing stops the tail adjustment from wiping out most of the line total. The only limits are finalAmount > 0 at submit and clause-sum equality (RULE-10).
**Confidence:** High — explicit arithmetic.

### RULE-02: Line amount: a hand-entered discounted total overrides quantity × unit price
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/QuotationLine.java:86-93`
**Plain English:** Each line's tax-inclusive amount is the hand-entered "discounted tax-inclusive total" if one is filled in. Otherwise it is quantity × tax-inclusive unit price.
**Specification:**
  Given a line with quantity 2 and tax-inclusive unit price ¥6,200.00
  When  amountTaxDisc is empty
  Then  the line amount is ¥12,400.00
  And   when amountTaxDisc = ¥10,600.00 the line amount is ¥10,600.00 (the hand-entered value wins)
**Parameters:** none
**Edge cases handled:** Null quantity or null unit price counts as 0. A hand-entered value of 0 counts as "filled" (it is not null), so the line amount is 0.
**Confidence:** High.

### RULE-03: Line tax split, ex-tax unit price and implied discount are derived on the server
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/QuotationLine.java:107-126`
**Plain English:** On save the server recomputes each line's ex-tax amount, tax amount, ex-tax unit price and unit discount. If the line has no tax rate of its own, it uses the quotation's header tax rate.
**Specification:**
  Given a line with quantity 2, unit price ¥6,200.00, hand-entered amount ¥10,600.00, no line tax rate, and header tax rate 6%
  When  the quotation is saved
  Then  amountNotax = 10,600 ÷ 1.06 = ¥10,000.00 (rounded half-up to 2 decimals)
  And   taxAmount = 10,600 − 10,000.00 = ¥600.00 (found by subtraction, so amountNotax + taxAmount always equals the amount exactly)
  And   unitPriceNotax = 6,200 ÷ 1.06 = ¥5,849.06
  And   unitDiscount = 10,600 ÷ 12,400 = 0.8548 (rounded half-up to 4 decimals)
  And   the line's tax rate is stored as 0.06 (the inherited header rate)
**Parameters:** 2-decimal HALF_UP for amounts and unit price; 4-decimal HALF_UP for the discount ratio
**Edge cases handled:** A completely blank line (no amount, quantity or price) is left untouched. When quantity × price ≤ 0 the discount is null. When no discount total is entered, the discount is always 1. Values sent by the client for these derived columns are ignored.
**Confidence:** High.

### RULE-04: Header tax rate defaults to 6% and must be between 0 (inclusive) and 1 (exclusive)
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/Quotation.java:232-240`
**Plain English:** The quotation tax rate is taken from the request if supplied, otherwise kept from the stored value, otherwise defaults to 6%. Header and line rates outside [0, 1) are rejected (range check at lines 472-475 and 483-487).
**Specification:**
  Given a new draft (tax rate set to 0.06 at creation) and a save request with no taxRate
  When  saved
  Then  taxRate stays 0.06
  And   a save with taxRate = 1.00 or −0.01 is rejected with "税率须在 0（含）到 1（不含）之间"
**Parameters:** DEFAULT_TAX_RATE = 0.06 (`Quotation.java:28`)
**Edge cases handled:** The range check prevents division by zero in amount ÷ (1 + rate). A line rate of null falls back to the header rate.
**Suspected defect:** The seed doc limits rates to the set 0/1/3/5/6/9/13%. The server accepts any value in [0, 1), for example 0.4.
**Confidence:** High.

### RULE-05: Cost-tree work package value = hours × billing rate; labour cost = hours × cost rate
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/CostEstimateNode.java:74-102`
**Plain English:** For each work package the server stores a billing value of hours × billing rate and contributes hours × cost rate to labour cost. Clause and deliverable nodes simply store the amount the user typed.
**Specification:**
  Given a work package of 40 h with billing rate ¥800/h and cost rate ¥350/h
  When  the quotation is saved
  Then  the stored node amount = ¥32,000 and its labour-cost contribution = ¥14,000
  And   a clause node with amount ¥31,800 is stored as ¥31,800 (passed through unchanged)
**Parameters:** none
**Edge cases handled:** Null hours or rate counts as 0. Any amount sent by the client for a work package is ignored. Legacy "othercost" nodes contribute their amount (null = 0).
**Suspected defect:** (1) The multiplication is not rounded; for example 12.5 h × ¥833.33 = ¥10,416.625, so the value saved depends on the DB column scale. (2) The seed docs (Q5 "逐层向上汇总", PRE-R04 "自底向上汇总标价") say the tree rolls up into the price. In code, deliverable and clause amounts are never derived from their work packages, and the tree's billing total (Σ hours × billing rate) is never compared with finalAmount on the server. The roll-up exists only for display (front-end `clauseBill`).
**Confidence:** Medium — the logic is explicit, but SME question: "Should the bottom-up billing value constrain or check the final quote, or is it informational only?"

### RULE-06: BU estimated cost = labour cost (whole tree) + the 8 fixed other-cost categories
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/Quotation.java:247-251`
**Plain English:** BU cost is labour cost (summed over every work package at every level of the tree) plus eight subcontract/other cost categories: safety assessment, lighting assessment, window-cleaning machine, construction-drawing sign-off, other subcontract, information services, travel, and printing.
**Specification:**
  Given work packages totalling 120 h × ¥350/h = ¥42,000 labour, travel ¥3,000, printing ¥500, and the other categories empty
  When  saved
  Then  laborCost = ¥42,000 and buCost = ¥45,500
**Parameters:** 8 categories (`OtherCosts.java:47-58`); an empty category counts as 0
**Edge cases handled:** "othercost" nodes are removed from the tree before summing so legacy clients cannot double-count (`Quotation.java:225-228`). If the request has no other-costs object (no permission or old client), the stored values are kept.
**Confidence:** High.

### RULE-07: Gross margin is calculated on the ex-tax final quote using the header tax rate, to 4 decimals
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/Quotation.java:252-259`
**Plain English:** Gross margin = (final quote ÷ (1 + header tax rate) − BU cost) ÷ that ex-tax amount. The ex-tax amount is rounded to cents first, then the margin is rounded to 4 decimals.
**Specification:**
  Given finalAmount ¥106,000.00, taxRate 0.06, buCost ¥45,500.00
  When  saved
  Then  exTax = ¥100,000.00 and grossMargin = (100,000 − 45,500) ÷ 100,000 = 0.5450
**Parameters:** exTax scale 2 HALF_UP; margin scale 4 HALF_UP
**Edge cases handled:** When finalAmount ≤ 0 or exTax ≤ 0 the margin is null (avoids division by zero), and null is explicitly written to the DB so a stale value cannot remain. The same formula is repeated in the approval path (lines 402-409) and the submit-refresh path (lines 427-434).
**Suspected defect:** (1) The seed doc (Q6) says to divide by a fixed 1.06; the code uses the quotation's own tax rate. (2) The margin always uses the header rate even when lines carry different rates. Example: one line of ¥113,000 at 13% has a line ex-tax amount of ¥100,000.00, but the margin uses 113,000 ÷ 1.06 = ¥106,603.77. That overstates margin by about 6 percentage points compared with the sum of the lines' ex-tax amounts.
**Confidence:** High for the code behaviour. SME question: "Should margin use Σ line amountNotax instead of final ÷ (1 + header rate)?"

### RULE-08: A margin below 50% requires a written low-margin reason (BU submitter or cost-adopting approver only)
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/Quotation.java:281-298`
**Plain English:** If the calculated gross margin is below 50%, a low-margin reason must be filled in before submission or approval adoption. The error message reports the margin as a percentage with 1 decimal.
**Specification:**
  Given grossMargin 0.4000 (exTax ¥100,000, buCost ¥60,000) and an empty lowGmReason
  When  a user with cost-edit permission submits
  Then  submission is rejected with the parameter "40.0%"
  And   at grossMargin 0.5000 no reason is required
**Parameters:** LOW_GROSS_MARGIN_THRESHOLD = 0.50 (front-end constant `LOW_GM_THRESHOLD` mirrors it)
**Edge cases handled:** A null margin is skipped. A whitespace-only reason counts as empty. The check applies only when the submitter can edit cost (`SubmitQuotationUseCase.java:69-71`) or when the approval step adopted the cost tree or other costs (`ApplyQuotationApprovalUseCase.java:89-91`). A sales submission skips it.
**Suspected defect:** The reason is never cleared automatically when the margin recovers. The repository comment says it "must be cleared together" (`QuotationRepositoryImpl.java:156`), but the code just saves whatever the client sends.
**Confidence:** High for the logic. SME question: "Is 50% the intended threshold, and should it be configurable or vary by business category?"

### RULE-09: Clause amounts must each be greater than 0 and add up exactly to the final quote
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/Quotation.java:334-356`
**Plain English:** The payment clauses (progress and prepayment) are a complete split of the final quote. Every clause amount must be positive, the sum must equal finalAmount to the cent, and at submit at least one clause must exist.
**Specification:**
  Given finalAmount ¥106,000.00 and clauses ¥31,800.00 (PREPAY) + ¥74,200.00 (PROGRESS)
  When  submitted
  Then  it passes
  And   with clauses ¥31,800 + ¥74,000 (sum ¥105,800) it is rejected as SUBMIT_CLAUSE_TOTAL_MISMATCH
  And   with no clauses at all, submit is rejected as SUBMIT_CLAUSE_REQUIRED
**Parameters:** exact BigDecimal equality via compareTo (no tolerance)
**Edge cases handled:** The approval-adoption path passes requireClause=false, so legacy in-flight quotations with no clauses get through. The equality check runs only when at least one clause exists and finalAmount is not null.
**Suspected defect:** The comment at `SubmitQuotationUseCase.java:60` says "合计 ≤ 最终报价", but the code requires equality. The front end warns only when the total exceeds the final quote (+0.005 tolerance), so an under-allocated split gives no warning before the hard rejection at submit.
**Confidence:** High.

### RULE-10: A clause percentage is a UI convenience; only the amount is stored, rounded to cents
**Category:** Calculation
**Priority:** P1
**Source:** `psa-front-desk-pc/src/views/project/quotation/components/CostEstimateTree.vue:486-538`
**Plain English:** In the editor a clause can be entered as a percentage of the final quote. The amount becomes round(final × pct ÷ 100, 2 decimals). The percentage itself is not saved; it is shown back as round(amount ÷ final × 100, 2 decimals).
**Specification:**
  Given finalAmount ¥100,000.01 and three clauses entered as 33.33%, 33.33% and 33.34%
  When  the percentages are committed
  Then  the amounts become ¥33,330.00, ¥33,330.00 and ¥33,340.00 (sum ¥100,000.00)
  And   the server rejects submit because the sum ≠ ¥100,000.01 (RULE-09); no front-end warning appears because the total is under, not over
**Parameters:** amount precision 2 decimals (Math.round); display percentage precision 2 decimals
**Edge cases handled:** Input is disabled while finalAmount ≤ 0. Invalid or blank input leaves the amount unchanged. Hand-typed amounts are normalised to cents.
**Suspected defect:** Rounding each percentage to cents can leave a 1-cent remainder that the exact server check rejects, and there is no "put the remainder in the last clause" rule. Note that this rule's source is outside the listed back-end paths.
**Confidence:** Medium — this is front-end-only behaviour. SME question: "When a percentage split doesn't divide evenly, which clause should absorb the remainder?"

### RULE-11: Role rates come from the master-data schemes in effect today, by role and city, newest scheme first
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/presales/quote/PositionRateQueryImpl.java:50-92`
**Plain English:** A role's billing rate and cost rate are read from separate rate schemes. Only schemes that are published (status = 2), approved (approval status = 3) and in effect today are used. Within them only role-level rows are read (salary_grade_no = '0'), filtered to the quotation's city when one is set. The scheme with the latest effective start date wins. A role that exists on only one side gets 0 on the other.
**Specification:**
  Given billing schemes A (start 2026-01-01, pd ¥900/h) and B (start 2026-07-01, pd ¥950/h), both in effect on 2026-10-14
  And   a cost scheme with pd ¥400/h, and the role dev1 present only in the cost scheme at ¥300/h
  When  rates are listed for the quotation's city
  Then  pd = {bill ¥950, cost ¥400} and dev1 = {bill ¥0, cost ¥300}
**Parameters:** status = 2, approval_status = 3, salary_grade_no = '0', effective_end_date null = open-ended (`BillRateMapper.java:19-26`, `BillRateDetailMapper.java:24-30`)
**Edge cases handled:** Quotations without a city (legacy) take, for each role, the row with the smallest city code. Rows with a null rate are skipped. With no scheme in effect the list is empty.
**Suspected defect:** Rates are taken as of the server's current date (`LocalDate.now()`), not the quotation's creation or submit date. Every save, submit or approval adoption re-prices the tree at that day's rates. Two schemes with the same start date are resolved by DB row order, which is not deterministic.
**Confidence:** High for the mechanics. SME question: "Should a quotation lock rates at creation or submission, or always use the rates in effect today?"

### RULE-12: Server rates always overwrite client rates; at submission every work package must have a fully priced role
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/presales/quote/QuotationCostRateResolver.java:38-103`
**Plain English:** Every work package's billing and cost rates are replaced with the master-data rates for its role (RULE-11). In draft mode a work package with no role keeps its hours but its rates are cleared. In submission/approval mode any work package with no role, or a role without both a positive billing rate and a positive cost rate, is rejected.
**Specification:**
  Given a work package with role pd, 40 h, and a client-sent billing rate of ¥1,200/h, where master data gives pd = ¥950 / ¥400
  When  saved or submitted
  Then  the node is stored with billRate ¥950 and costRate ¥400 (value ¥38,000, labour ¥16,000)
  And   a work package with role dev1 (bill ¥0) is rejected at submit with QUOTATION_POSITION_RATE_UNAVAILABLE
**Parameters:** rate must be > 0 on both sides
**Edge cases handled:** The role dropdown shows only fully priced roles (`ListPositionRatesUseCase.java:34-36`, `PositionRate.java:50-56`).
**Suspected defect:** (1) PRE-R02 ("billing rates may be discounted, hours may not be changed") is not implemented: there is no rate discount, any client rate is overwritten, and hours are freely editable by cost editors. (2) The duplicate-rate check (lines 54-57) can never fire, because the query already removes duplicates with putIfAbsent.
**Confidence:** High for the code. SME question: "Is a billing-rate discount still required (PRE-R02), and at which level: line, work package, or whole quotation?"

### RULE-13: At submit, cost and margin are recalculated from the current rates before approval starts
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/presales/quote/SubmitQuotationUseCase.java:51-81`
**Plain English:** On submit, the stored cost tree is re-priced at today's master rates, then labour cost, BU cost and gross margin are recalculated (`Quotation.applyAuthoritativeCostNodes`) and saved, so the approval is based on server-calculated figures.
**Specification:**
  Given a draft saved last month with pd at ¥900/h × 100 h (labour ¥90,000), where pd is now ¥950/h
  When  the BU user submits
  Then  laborCost becomes ¥95,000 and buCost and grossMargin are recalculated before the approval process is started
**Parameters:** none
**Edge cases handled:** A submitter with cost-edit permission uses strict resolution. A sales submitter (no cost-edit permission) uses draft resolution: work packages without a role are allowed and their rates cleared.
**Suspected defect:** A sales submission can therefore go to approval with labour cost understated (unpriced work packages count as 0) and margin overstated. The safeguards are the approval cost-required check (RULE-15) and the BU approver's adoption.
**Confidence:** High.

### RULE-14: Approval-time edits recalculate the final quote from the stored line total plus the new tail adjustment
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/Quotation.java:367-410`
**Plain English:** When an approver changes values during approval, the line total is worked back as (old final − old tail adjustment) without reloading the lines. The new final is that line total + the new tail adjustment. Labour is recalculated only if a tree is adopted; BU cost is recalculated if a tree or other costs are adopted; margin is always recalculated.
**Specification:**
  Given a pending quotation with final ¥106,000, tail ¥0, buCost ¥45,500 and tax rate 0.06
  When  the approver adopts tax rate 0.09 and no tree
  Then  final stays ¥106,000, exTax = ¥97,247.71, and grossMargin = (97,247.71 − 45,500) ÷ 97,247.71 = 0.5321
**Parameters:** same rounding as RULE-07
**Edge cases handled:** The final must still be > 0. An empty adopted tree is treated as "not adopted" (`ApplyQuotationApprovalUseCase.java:69-71`).
**Suspected defect:** (1) A tax-rate change during approval does not re-derive the stored lines' tax splits, so line amountNotax and taxAmount no longer match the header rate. (2) If a legacy row has labor_cost NULL and the approver adopts only other costs, buCost becomes other costs only: labour drops out and margin is inflated.
**Confidence:** High for the logic; Medium for how much the legacy-NULL case matters in practice.

### RULE-15: During approval clauses are locked, so the tail adjustment is effectively frozen, and BU cost must be > 0 when required
**Category:** Validation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/presales/quote/ApplyQuotationApprovalUseCase.java:65-101`
**Plain English:** An approver may change deliverables and work packages but not clauses: same count, same codes, same titles, types and amounts. After adoption the clause sum must still equal the final quote. At a node marked cost-required, BU cost must be > 0.
**Specification:**
  Given a pending quotation with final ¥106,000 split into clauses ¥31,800 + ¥74,200
  When  the approver adopts tail adjustment −¥1,000 (final becomes ¥105,000)
  Then  it is rejected as SUBMIT_CLAUSE_TOTAL_MISMATCH, because the clauses cannot be changed to match
  And   at a cost-required node with a tree whose labour is ¥0 and no other costs, it is rejected as QUOTATION_APPROVAL_COST_REQUIRED
**Parameters:** costRequired flag is set by the approval form handler (outside the listed paths)
**Edge cases handled:** Clauses are matched by code, so their order does not matter. An incoming clause without a code counts as a new clause and is rejected.
**Suspected defect:** Tail adjustment and tax/price changes are offered to approvers as adoptable fields, but whenever clauses exist any change to the final quote is guaranteed to fail.
**Confidence:** High for the code. SME question: "Should approvers be able to adjust the final price during approval, and if so, how should the clause split be rebalanced?"

### RULE-16: Users without cost-edit permission cannot change cost inputs; work-package cost fields are kept from the database
**Category:** Policy
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/application/presales/quote/SaveQuotationUseCase.java:51-164`
**Plain English:** Sales users may add or remove clauses, change clause amounts, and restructure deliverables and work packages. Existing work packages keep their stored role, hours and rates, matched by id. New work packages under existing clauses get no cost fields. Other costs are ignored. Brand-new clauses, including those created from a template, are taken as sent.
**Specification:**
  Given a stored work package id 77 with role pd and 40 h
  When  a sales user saves it with 10 h
  Then  it is saved with 40 h and is then re-priced at current rates
  And   the 8 other-cost categories keep their stored values
**Parameters:** permission `fst:quotation:cost-edit`
**Edge cases handled:** A non-empty payload with no clauses falls back to the whole stored tree. An empty array means "delete all clauses". City changes by sales are accepted.
**Suspected defect:** New clauses (those without a code) carry the hours and roles sent by the client, so sales can set labour hours by deleting a clause and re-adding it.
**Confidence:** Medium — this is inferred from the merge structure. SME question: "Should sales-created clause branches (template or manual) be allowed to carry hours, or should hours always be left blank for BU?"

### RULE-17: All cost and price inputs must be non-negative and the cost tree has at most 3 levels
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/quotation/Quotation.java:470-518`
**Plain English:** Line quantity, unit prices and amounts; node hours, billing rate, cost rate and amount; and all 8 other-cost categories must be ≥ 0. The tree depth (clause → deliverable → work package) cannot exceed 3.
**Specification:**
  Given a work package with cost rate −¥100/h
  When  saved
  Then  it is rejected with "成本节点成本费率不能为负数"
**Parameters:** maximum depth 3
**Edge cases handled:** Null values are allowed. The tail adjustment is exempt (it may be negative). Validation also runs on approval adoption and on the submit refresh.
**Confidence:** High.

### RULE-18: Cost template summary shows clause, deliverable and work-package counts and total hours; templates store no rates
**Category:** Calculation
**Priority:** P2
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/domain/presales/quote/costestimate/CostTemplate.java:106-126`
**Plain English:** Template list rows show the number of clauses, deliverables and work packages and the sum of work-package hours. Templates keep only role and hours; rates are applied when the template is used in a quotation (RULE-12).
**Specification:**
  Given a template with 2 clauses, 3 deliverables, and work packages of 40 h, 24 h and 16 h
  When  the template list is shown
  Then  the counts are 2 / 3 / 3 and total hours = 80
**Parameters:** none
**Edge cases handled:** Null hours count as 0. A PREPAY clause in a template can carry an amount (`CostTemplateNode.java:25-26`).
**Suspected defect:** The seed doc says template rates come from a front-end static table; in code, rates come from master data at use time.
**Confidence:** High.

### RULE-19: Quotation list "total hours" = sum of work-package hours
**Category:** Calculation
**Priority:** P2
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/presales/quote/QuotationQueryImpl.java:94-118`
**Plain English:** Each row in the quotation list shows the total hours across all of that quotation's work packages.
**Specification:**
  Given a quotation with work packages of 40 h, 24 h and a null-hours package
  When  the list page loads
  Then  totalHours = 64
**Parameters:** none
**Edge cases handled:** Null hours count as 0. A quotation with no work packages shows 0.
**Confidence:** High.

### RULE-20: Quotation stats card: counts by status and approved amount, limited to what the viewer can see
**Category:** Calculation
**Priority:** P2
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/presales/quote/QuotationQueryImpl.java:130-144`
**Plain English:** The stats card shows the number of draft, pending and approved quotations, plus the sum of final amounts of approved quotations. It only covers quotations where the viewer is the applicant or is listed in the visible team.
**Specification:**
  Given the viewer can see approved quotations of ¥106,000 and ¥50,000 and one approved quotation with a NULL amount
  When  the stats load
  Then  approvedAmount = ¥156,000
**Parameters:** none
**Edge cases handled:** Null amounts are skipped. The visible-team match uses the quoted id, so 1 does not match 12.
**Confidence:** High.

### RULE-21: Opportunity stats card: total, won, lost, sum of amounts, and number of quoted opportunities
**Category:** Calculation
**Priority:** P2
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/infrastructure/presales/quote/OpportunityQueryImpl.java:116-140`
**Plain English:** Within the viewer's data scope and the active filters, the card shows the number of opportunities, the number closed as won and as lost, the sum of opportunity amounts, and the number of opportunities with at least one quotation.
**Specification:**
  Given 10 opportunities in scope (3 won, 2 lost, amounts summing to ¥5,000,000, one with a null amount), 4 of which have quotations
  When  the stats load
  Then  total = 10, won = 3, lost = 2, totalAmount = ¥5,000,000, quoted = 4
**Parameters:** close status constants `FSTConstant.OppCloseStatus.赢单关闭 / 丢单关闭`
**Edge cases handled:** All figures come from one data-scoped query, so counts cannot leak company-wide numbers. Null amounts are skipped.
**Suspected defect:** "Quoted" counts quotations in any status, including drafts and rejected ones.
**Confidence:** High for the code. SME question: "Should 'quoted opportunities' count only submitted or approved quotations?"

Two things worth flagging:
- **Clause percentage is front-end only.** RULE-10's source is a Vue file, outside the listed back-end paths. I included it because it is the only implementation of the percentage-of-final-quote split.
- **No opportunity expense accounting in these paths.** I found no rule for it. `service/customer` contains only contact management, and PRE-R07/R08 (intermediary fees, cancellation on lost opportunity) are not implemented in any listed path.

**Coverage**
- **Read fully:** `domain/presales/quote/quotation/{Quotation, CostEstimateNode, QuotationLine, OtherCosts, PositionRate}.java`, `domain/presales/quote/costestimate/{CostTemplate, CostTemplateNode}.java`, `application/presales/quote/{SaveQuotation, SubmitQuotation, ApplyQuotationApproval, CreateQuotationDraft, DecideQuotation, ListPositionRates}UseCase.java` and `QuotationCostRateResolver.java`, `infrastructure/presales/quote/{QuotationRepositoryImpl, QuotationQueryImpl, OpportunityQueryImpl, PositionRateQueryImpl, QuotationCodeGeneratorImpl, CostOptionSourceImpl}.java`, the rate mapper queries, and the seed quotation and presales sections.
- **Skimmed (grep only):** `controller/admin/quotation/*`, `controller/admin/opportunity/*`, `service/customer/*` (no calculations found), `CostTemplateRepositoryImpl`, and the VO/read-model classes.
- **Possibly missed:** `QuotationApprovalFormHandler` (sets costRequired and which fields an approver can adopt; outside the listed paths), the DB column scales that decide how unrounded products such as hours × rate are stored, and front-end-only roll-ups in `editor.vue` and `CostEstimateTree.vue` beyond the clause-percentage logic.