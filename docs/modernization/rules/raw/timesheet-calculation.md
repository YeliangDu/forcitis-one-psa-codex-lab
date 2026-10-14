### RULE-01: Approved-hours cost and billing amounts (rate × approved hours)
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourApproveStatusService.java:241-271`
**Plain English:** When a timesheet line is approved, the system works out its standard cost and its billing value by multiplying the rate saved at submission by the approved hours, rounded half-up to 3 decimals.
**Specification:**
  Given a line with standard cost rate 312.500, bill rate 480.125 and approved hours 3.3
  When the line (or the whole timesheet) moves to Approved (通过)
  Then standard_cost_amount = 1,031.250 and bill_rate_amount = 1,584.413 (480.125 × 3.3 = 1,584.4125, rounded HALF_UP to 3 dp)
**Parameters:** scale = 3; rounding = HALF_UP; hours basis = `approval_hours`
**Edge cases handled:** rates are not looked up again at approval (no drift between submit and approve); rate or hours null gives a null amount; lines with no works date or no approved hours are skipped; `approved_time` is only filled when empty; historical back-fill passes the real approval time; running it twice gives the same result. It is applied both per approver (path A, only that approver's lines) and for the whole timesheet when it ends (path B).
**Suspected defect:** Rates carry a unit (`standard_cost_unit` / `bill_rate_unit`, documented as HOUR/DAY in `ManHourItemDO.java:102-128`), but the formula always multiplies by hours. A rate whose unit is DAY would be overstated by about 7.5×.
**Confidence:** Medium — the formula is explicit. SME: "Can any standard-cost or bill-rate detail have unit = DAY? If so, should the amount be rate × approved hours ÷ 7.5?"

### RULE-02: Rate lookup by employee grade, salary band and city on the work date
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/TimeSheetRateServiceImpl.java:62-104`
**Plain English:** A person's cost rate and bill rate for a day come from their HR transfer record valid on that day: job level, salary grade and city category together pick exactly one standard-cost rate and one bill rate.
**Specification:**
  Given employee 1001 whose transfer record on 2026-03-02 has jobLevel T3, salaryGrade 05, cityCategory SH, and a standard-cost detail "T3|05|SH" effective 2026-01-01 to (open-ended)
  When rates are resolved for works date 2026-03-02
  Then that detail's rate, unit and scheme/detail IDs are returned, the bill rate (plus its billable flag) is resolved the same way, and each amount = rate × hours (HALF_UP, 3 dp)
**Parameters:** match key = `jobLevelCode|salaryGradeNo|cityCategoryCode`; effective window is inclusive at both ends and a null end means open-ended (`TimeSheetRateCacheService.java:375-380`); AMOUNT_SCALE = 3
**Edge cases handled:** submission is blocked (error 503) if the transfer record is missing or ambiguous, any of the 3 key fields is blank, a rate has no match, more than one detail matches, or no detail's window covers the date. Both the cost rate and the bill rate must match. On a miss the cache is force-reloaded once, throttled.
**Confidence:** High

### RULE-03: Rates, company code and cost centre are fixed at submission
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:1670-1696`
**Plain English:** When a timesheet is saved or submitted, each line records the cost and bill rates, the accounting company (from the HR transfer record) and the cost centre (the employee's department). Amounts are left for approval.
**Specification:**
  Given employee 1001 in dept D-17 whose transfer record on 2026-03-02 has company_code C01
  When the timesheet line for 2026-03-02 is saved
  Then the line stores company_code = C01, cost_center_id = D-17, plus the 9 rate fields (2 scheme IDs, 2 detail IDs, 2 rates, 2 units, billable flag); standard_cost_amount and bill_rate_amount stay empty until approval (RULE-01)
**Parameters:** hours passed to rate lookup = approvalHours if present, else declareHours, else 0 (only a placeholder)
**Edge cases handled:** lines with no works date are skipped; a missing company_code stops creation of the timesheet header (`insertManHour`, 1611-1616)
**Confidence:** High

### RULE-04: Human-days split of a person's approved day across projects
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourEndServiceImpl.java:57-109`
**Plain English:** Each approved line becomes a fraction of a person-day: its hours divided by the person's total approved hours that day. The divisor is never less than 7.5, so a short day does not count as a full person-day.
**Specification:**
  Given employee 1001 on 2026-03-02 with approved hours Project A 6.0 and Project B 3.0 (total 9.0)
  When the `calculateHumanDaysJob` batch runs
  Then human_days = 6/9 = 0.6667 for A and 3/9 = 0.3333 for B
  And if the employee had only Project A 4.0 that day, human_days = 4/7.5 = 0.5333
**Parameters:** daily standard hours `CommonConstants.日小时` = "7.5"; scale 4, HALF_UP
**Edge cases handled:** only person-days with at least one line still missing human_days are processed, but then every line of that day is recalculated; a divide error gives 0 (BigDecimalUtil swallows it); rest-day hours are included (they become non-chargeable later, RULE-05)
**Suspected defect:** (1) If a null `approval_hours` row follows a non-null row on the same day, `hoursValue.add(null)` throws an NPE. That aborts the run for every remaining user, because the job catches the exception and only logs it. (2) The input table `psa_time_sheet_works_end` is filled once by `syncWorkHours` (approved, not deleted at sync time). Lines soft-deleted later stay in it, can inflate the daily divisor and are still costed by RULE-05.
**Confidence:** Medium — the formula is explicit. SME: "Is a day with more than 7.5 approved hours meant to stay at 1.0 person-day in total (divisor = actual hours) rather than going above 1.0?"

### RULE-05: Project labour cost = daily wage × human days, excluding admin/support posts
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:2090-2128`
**Plain English:** Direct labour cost adds up daily wage × human days over working-day lines. Rest-day human days are counted separately as non-chargeable and carry no cost. Lines whose post code contains "A" or "S" are left out.
**Specification:**
  Given 3 closed lines: (workday, post T3, wage 1,200.00, 0.6667 hd), (rest day, post T3, 0.5 hd), (workday, post A01, 1.0 hd)
  When project labour cost is computed
  Then amount = 800.0400, humanDays = 0.6667, nonChargeableHumanDays = 0.5, manHours = approved hours of line 1 only
**Parameters:** excluded post marker: post code contains "A" or "S"; rest-day flag `FSTConstant.WORKDAY.休息日`
**Edge cases handled:** amount is not rounded; the reported dailyWage is the first non-zero wage seen; a null wage or human_days throws a RuntimeException
**Suspected defect:** `contains("A")`/`contains("S")` matches the letter anywhere in the post code, not just as a prefix. Rest-day human days for A/S posts are still added to non-chargeable days, because the rest-day check runs first. RULE-24 (budget used cost) applies neither exclusion.
**Confidence:** Medium — SME: "Should the A/S exclusion test the first letter of the post code only (post channel), and should admin rest days count as non-chargeable?"

### RULE-06: Which daily wage is used (before 2020 vs from 2020 on)
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/budget/HourlyRateServiceImpl.java:329-348`
**Plain English:** For work dated before 2020-01-01, the daily wage comes from the yearly post × department rate card. Posts above level 4 cost 0. From 2020, it comes from the person's monthly daily-wage record.
**Specification:**
  Given a line dated 2019-06-10, post T3, dept D-17
  Then wage = hourly-rate table entry for key "2019-T3-D-17"
  Given a line dated 2019-06-10, post M5
  Then wage = 0 (numeric suffix 5 > 4)
  Given a line dated 2026-03-02 for person 1001
  Then wage = psa daily_wage record "2026-03-1001", rounded HALF_UP to 2 dp
**Parameters:** cutover date 2020-01-01; post level threshold > 4 → 0; wage scale 2 dp
**Edge cases handled:** rest day → wage 0 and no lookup (`ManHourEndServiceImpl.java:203-208`); a missing record gives wage 0 (`ManHourEndServiceImpl.java:206-207`); a missing calendar entry or post fails the whole calculation with a RuntimeException (`ManHourEndServiceImpl.java:191-202`)
**Suspected defect:** `Integer.parseInt(postCode.substring(1))` throws if the post code has a non-numeric suffix.
**Confidence:** Medium — SME: "Is the pre-2020 rule 'posts with level greater than 4 carry no cost' still valid, and should a missing post-2020 wage show as 0 instead of blocking?"

### RULE-07: Daily declared-hours cap of 15 and positive hours per line
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:1196-1211`
**Plain English:** Every line must have more than 0 declared hours, and the lines for one date may not add up to more than 15 hours.
**Specification:**
  Given lines for 2026-03-02 of 8.0h and 7.5h (total 15.5)
  When the timesheet is saved or submitted
  Then it is rejected with "2026-03-02 填报工时大于15小时！"; a total of exactly 15.0 is accepted
**Parameters:** max daily hours = 15; minimum per line > 0
**Edge cases handled:** totals are grouped by each line's works date
**Suspected defect:** The message says "不能小于0" (cannot be less than 0), but `compareTo(ZERO) < 1` also rejects exactly 0. A null declareHours throws an NPE in `merge` before the check.
**Confidence:** High

### RULE-08: On working days, hours plus leave must reach 7.5
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:1215-1226`
**Plain English:** On a calendar working day, declared hours plus approved leave hours for that date must be at least 7.5.
**Specification:**
  Given a working day with declared 5.0h and leave 150 min (2.5000h)
  When saved
  Then accepted (total 7.5)
  And with leave 149 min (2.4833h, total 7.4833) it is rejected "工时合计（工时+请假）不得低于7.5小时！"
**Parameters:** minimum 7.5 h; leave minutes ÷ 60 at scale 4 HALF_UP
**Edge cases handled:** compares hours directly instead of rounding to minutes (so 7.499 cannot round up to 450 min); non-working days are exempt; a date missing from the calendar is treated as a working day (RULE-17)
**Suspected defect:** The 7.5 is a literal here, not the shared `CommonConstants.日小时` constant, so the two can drift apart.
**Confidence:** High

### RULE-09: Full-day leave threshold (450 minutes)
**Category:** Policy
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:858-868`
**Plain English:** A day with 450 or more minutes of leave counts as a full leave day. A timesheet for that day cannot be submitted, though a draft can still be saved.
**Specification:**
  Given employee 1001 has 450 min of leave on 2026-03-02
  When they submit (发送) a timesheet for that date
  Then it is rejected "2026-03-02 为整天请假，无需填报工时"
**Parameters:** full-day leave = 450 min (= 7.5 h × 60)
**Edge cases handled:** draft save (暂存) is not blocked; the same threshold makes a withdrawn timesheet deletable (non-working day OR leave ≥ 450 min, `isManHourDeletable` 1561-1569)
**Confidence:** High

### RULE-10: Remaining hours (ETC) of a project task package
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:1258-1266`
**Plain English:** A task package's remaining hours are its explicit ETC hours if set; otherwise planned hours minus actual hours. The result is never negative.
**Specification:**
  Given etc_hours null, planned 40.0, actual 32.5
  Then remaining = 7.5
  Given etc_hours = −3
  Then remaining = 0
**Parameters:** floor 0; null planned/actual treated as 0
**Edge cases handled:** an explicit ETC overrides the planned-actual formula even when it is lower
**Confidence:** High

### RULE-11: Submission cannot exceed a task package's remaining hours
**Category:** Validation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:1275-1310`
**Plain English:** All project-task lines on one timesheet that point at the same task package must not add up to more than its remaining hours. On resubmission, lines already approved on the original are subtracted so they are not counted twice.
**Specification:**
  Given package P has remaining 7.5h and the timesheet has two lines on P of 4h each (8h)
  When saved
  Then it is rejected "…剩余工时 7.5 小时，本次填报合计 8 小时已超出，请先申请 ETC 调增！"
  And if this is a resubmission whose original already had 3h approved on P, requested = 8 − 3 = 5 ≤ 7.5, so it is accepted
**Parameters:** applies only to work-package type 项目任务
**Edge cases handled:** packages missing from the new task-package table (legacy rows) are skipped; the original's approved rows are keyed by task_id, falling back to work_package_id
**Confidence:** High

### RULE-12: Which project task packages a person can log time to
**Category:** Policy
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:2402-2411`
**Plain English:** A person can only log to task packages assigned to them that still have remaining hours, are DOING or REWORK, are not obsolete, and have not passed their planned end date as of the work date.
**Specification:**
  Given package P assigned to 1001, status DOING, etc_hours null, planned 40, actual 40, plan_end 2026-03-31
  When 1001 opens the timesheet for 2026-03-02
  Then P is not offered (no remaining hours); with actual 32 it is offered with remainingHours 8
**Parameters:** statuses {DOING, REWORK}; remaining check `etc_hours > 0 OR (etc_hours IS NULL AND planned_hours > COALESCE(actual_hours,0))`; date check `plan_end_date IS NULL OR plan_end_date >= worksDate`
**Edge cases handled:** a planned start in the future does not hide the package; the reference date is the work date, not today, so back-filling works; the same list is re-checked on save (`assertWorkSpacesAccessible`)
**Confidence:** High

### RULE-13: Task package actual hours = total approved hours (recalculated in full)
**Category:** Calculation
**Priority:** P0
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/api/project/task/ProjectTaskApiImpl.java:73-105`
**Plain English:** After a timesheet is approved, each project task package's actual hours are recalculated as the sum of all approved, non-deleted lines on it. Actual start and end dates become the earliest and latest work dates.
**Specification:**
  Given package P has approved, non-deleted lines of 4.0h (2026-03-02) and 3.5h (2026-03-05), plus a soft-deleted approved line of 2.0h
  When the timesheet approval result is processed
  Then actual_hours = 7.5, true_start_date = 2026-03-02, true_end_date = 2026-03-05
**Parameters:** line approve_status = 通过 (3); deleted = 0
**Edge cases handled:** lines with null task_id are matched by work_package_id; this is a full recalculation, not an increment, so it is safe to repeat; opportunity tasks follow the old progress path (`TaskServiceImpl.updateProgress`: progress 100 means completed, keeping the later end date)
**Confidence:** High

### RULE-14: Nightly check of task-package actual hours
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/trigger/task/TaskActualHoursReconJob.java:46-82`
**Plain English:** A job re-adds approved hours per task package. Where the total differs from the stored actual hours, it re-runs the recalculation from RULE-13 in batches of 200.
**Specification:**
  Given package P stores actual_hours 5.0 but approved lines total 7.5
  When `taskActualHoursReconJob` runs
  Then P is flagged as drifted and recalculated to 7.5
**Parameters:** batch size 200; null actual treated as 0
**Edge cases handled:** lines with null task_id use work_package_id; lines with null approved hours are skipped
**Suspected defect:** Unlike RULE-13, this job does not filter `deleted = 0` (lines 53-56). Soft-deleted approved lines raise its expected total, so packages with superseded versions are flagged on every run. The recalculation then writes the lower, correct value, so the same packages are "repaired" every night and the fix count in the log is misleading.
**Confidence:** High

### RULE-15: Mobile (H5) submission records approved hours = declared hours
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/manhour/ManHourServiceImpl.java:463-474`
**Plain English:** On the mobile submission path, the single hours value the employee enters is stored as both declared and approved hours. Approvers accept or reject lines but do not change hours.
**Specification:**
  Given an H5 submission with workPackage.approvalHours = 6.5
  When it is saved
  Then declare_hours = 6.5 and approval_hours = 6.5 on the line
**Parameters:** none
**Edge cases handled:** none
**Suspected defect:** The seed glossary distinguishes declared from approved hours, but no code in scope lets an approver change approved hours.
**Confidence:** Medium — SME: "Should approvers ever be able to reduce approved hours below declared hours? If so, where in the business process does that happen?"

### RULE-16: Working-day calendar with fallback when a date is missing
**Category:** Policy
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/resource/ResourceServiceImpl.java:1099-1106`
**Plain English:** A date is a working day if the company calendar marks it workday = 1. If the date is not in the calendar, resource planning falls back to Monday-Friday.
**Specification:**
  Given 2026-10-01 (national holiday) is in the calendar with workday = 2
  Then it is not a working day
  Given 2027-03-06 (Saturday) is not in the loaded calendar
  Then it is not a working day; 2027-03-08 (Monday) is
**Parameters:** workday code 1 = working, 2 = rest; fallback is day-of-week < 6
**Edge cases handled:** the timesheet path (`ManHourServiceImpl.isWorkday` 1548-1558) treats any missing date, including weekends, as a working day; labour costing (`ManHourEndServiceImpl` 197-202) fails on a missing date
**Suspected defect:** There are three different fallbacks for the same question. Also, `CalendarServiceImpl.getCalendarMap(DateTime)` (51-63) loads only the current and previous year, so 6- or 12-month forecasts that run into next year ignore next year's holidays.
**Confidence:** Medium — SME: "Which fallback is the business rule when the calendar is not maintained, and is next year's calendar supposed to be loaded in advance?"

### RULE-17: Resource-list busyness = assigned task hours ÷ (working days × 7.5)
**Category:** Calculation
**Priority:** P2
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/resource/ResourceServiceImpl.java:134-204`
**Plain English:** On the resource list, a person's busyness is the full assigned hours of their matching tasks divided by expected hours (working days in the selected period × 7.5), rounded to 2 decimals.
**Specification:**
  Given 22 working days from 2026-03-01 to 2026-03-31 (expected 165h) and tasks totalling 180h
  Then busyness = 1.09
  And with filter "0.8,1.2" the person is included (0.8 ≤ 1.09 < 1.2)
**Parameters:** 7.5 h/day; ratio scale 2 HALF_UP; filter is lower bound inclusive, upper bound exclusive
**Edge cases handled:** begin date defaults to today; zero working days gives busyness 0
**Suspected defect:** Task hours are not prorated to the period here (RULE-19 prorates). The Gantt filter (`ganttChart` 509-516) includes the upper bound, this one excludes it.
**Confidence:** Medium — SME: "Should list busyness prorate long tasks to the selected window, as the Gantt does?"

### RULE-18: Gantt expected hours over the forecast window
**Category:** Calculation
**Priority:** P2
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/resource/ResourceServiceImpl.java:373-409`
**Plain English:** A person's expected (theoretical) hours in the Gantt = 7.5 × working days in the window. The window is the next 30 days, or from the 1st of next month through 6 or 12 months ahead.
**Specification:**
  Given window type "30天" and 21 working days in it
  Then expected hours = 157.5
  For a team (`ganttGroupChart` 597-613) with 5 members: 157.5 × 5 = 787.5
**Parameters:** 7.5 h/day; windows 30 days / 6 months / 12 months; explicit begin/end dates override
**Edge cases handled:** both window ends are counted
**Suspected defect:** "今天0点" (today 00:00) is built as `now().plusDays(1)`, i.e. tomorrow, so the window starts a day late (also at 912 and 1117). The "6个月" (6-month) branch uses `plusMonths(6)` but the comment says 5 months later.
**Confidence:** Medium — SME: "Should forecast windows start today or tomorrow?"

### RULE-19: Task hours spread evenly over working days and clipped to the window
**Category:** Calculation
**Priority:** P2
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/resource/ResourceServiceImpl.java:424-447`
**Plain English:** A task's assigned hours are spread evenly over its planned working days. Only the share that falls inside the reporting window counts toward a person's load.
**Specification:**
  Given a task with 60h planned 2026-03-02 to 2026-03-13 (10 working days), of which 4 working days fall in the window
  Then hours/day = 6.0000 and window load = 24.00
**Parameters:** per-day scale 4 HALF_UP; window total scale 2 HALF_UP
**Edge cases handled:** includes in-progress, overdue and (optionally) sent-but-not-accepted tasks; tasks starting after the window end are excluded
**Suspected defect:** A task with zero planned working days gives per-day 0 (division error swallowed), so its hours disappear from the load.
**Confidence:** High

### RULE-20: Remaining-hours estimate for overdue tasks
**Category:** Calculation
**Priority:** P2
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/resource/ResourceServiceImpl.java:456-488`
**Plain English:** For an overdue task, remaining hours are estimated from progress: approved ÷ (progress%) − approved. The Gantt bar is then pushed out by that many whole 7.5-hour working days from today.
**Specification:**
  Given approved 30h and progress 60%
  Then needed = 30 / 0.60 − 30 = 20.00h, neededDays = floor(20 / 7.5) = 2, and the bar ends 2 working days after today
**Parameters:** progress ÷ 100 at 2 dp HALF_UP; needed hours 2 dp HALF_UP; days rounded down by 7.5; safety loop limit 1,000
**Edge cases handled:** progress 0 or null makes the formula throw, so the full assigned hours are used instead
**Suspected defect:** Rounding days down makes any remaining work under 7.5h end today (0 days).
**Confidence:** Medium — SME: "Should remaining days round up, so that 3h of remaining work still takes one day?"

### RULE-21: Busyness colour bands
**Category:** Calculation
**Priority:** P2
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/resource/ResourceServiceImpl.java:876-886`
**Plain English:** Busyness % = assigned ÷ expected × 100 (2 dp). Under 100% is green, 100% up to (not including) 120% is yellow, and 120% or more is red.
**Specification:**
  Given assigned 180h and expected 165h
  Then busyness = 109.09% → yellow
  And 198h → 120.00% → red; 160h → 96.97% → green
**Parameters:** thresholds 100 and 120 (%); ratio at scale 4, then ×100 at scale 2 HALF_UP
**Edge cases handled:** expected = 0 gives 0% → green (division error swallowed)
**Confidence:** High

### RULE-22: Converting plan person-days to hours (and back)
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/resource/PlanWorksServiceImpl.java:488-517`
**Plain English:** When a plan line is created or updated, assigned hours = person-days × 7.5. When displayed, person-days = assigned hours ÷ 7.5, rounded to 2 dp.
**Specification:**
  Given a plan line of 3.5 person-days
  When it is saved
  Then assign_hours = 26.25
  And the plan page shows humanDays = 3.50 (26.25 ÷ 7.5, at line 319)
**Parameters:** `FSTConstant.HUMAN_DAYS` = "7.5" (a second constant alongside `CommonConstants.日小时` = "7.5"); display scale 2 HALF_UP
**Edge cases handled:** budget roll-up (line 400) converts budget person-days the same way
**Suspected defect:** The code's own TODO at line 399 asks whether different posts work the same hours per day. The same 7.5 constant is defined in two places.
**Confidence:** Medium — SME: "Is 7.5 hours per person-day uniform for every post and region?"

### RULE-23: Average daily hours of a plan line
**Category:** Calculation
**Priority:** P2
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/resource/PlanServiceImpl.java:63-87`
**Plain English:** A plan line's average daily load = assigned hours ÷ working days between planned start and end (both included), rounded to 2 dp. Team busyness charts add these up per day.
**Specification:**
  Given 45h planned from 2026-03-02 to 2026-03-10 (7 working days)
  Then avgWorkingHours = 6.43
**Parameters:** scale 2 HALF_UP
**Edge cases handled:** lines without start or end dates are skipped; 0 working days gives 0
**Confidence:** High

### RULE-24: Default task duration from assigned hours
**Category:** Calculation
**Priority:** P2
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/resource/TaskServiceImpl.java:489-508`
**Plain English:** When tasks are re-sequenced, a task's duration in working days = assigned hours ÷ 7.5, rounded up, minimum 1. Without hours, it is the number of working days in its planned range.
**Specification:**
  Given a task with 20h assigned
  Then duration = ceil(20 / 7.5) = 3 working days
  And with 0 or null hours and plan range 2026-03-02 to 2026-03-04, duration = 3
**Parameters:** 7.5 h/day (literal); CEILING; minimum 1
**Edge cases handled:** an end date before the start date gives 1; the calendar fallback is Mon-Fri
**Confidence:** High

### RULE-25: Budget cost already used, by budget code
**Category:** Calculation
**Priority:** P1
**Source:** `psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/service/resource/TaskServiceImpl.java:1527-1535`
**Plain English:** A task's used cost is the sum of daily wage × human days of closed timesheet lines carrying the same budget code, counting working days only.
**Specification:**
  Given budget code B-01 with closed workday lines (wage 1,200.00 × 0.6667) and (wage 1,000.00 × 1.0)
  Then incurredCostsAmount = 1,800.04
**Parameters:** working days only; no rounding
**Edge cases handled:** rest days are skipped
**Suspected defect:** This is not the same as project labour cost (RULE-05): A/S posts are not excluded. It uses the resource calendar fallback (Mon-Fri) instead of the stored workday flag. A null daily wage throws an NPE.
**Confidence:** Medium — SME: "Should used budget cost exclude administrative/support posts the same way project labour cost does?"

---
**Coverage:** Read fully: `TimeSheetRateServiceImpl`, `ManHourApproveStatusService`, `ManHourEndServiceImpl`, all 4 listed trigger jobs, both `mq/consumer/approval/timesheet` handlers, `CalendarServiceImpl`, `PlanServiceImpl`. Read the calculation and validation sections of `ManHourServiceImpl` (375-512, 768-1053, 1184-2130, 2223-2424), `ResourceServiceImpl` (120-1110, 1395-1452, 1740-1875), `PlanWorksServiceImpl` and `TaskServiceImpl`. Read only the helpers these rules depend on outside the listed paths: `ProjectTaskApiImpl`, `HourlyRateServiceImpl`, `TimeSheetRateCacheService`, `BigDecimalUtil`, `SyncServiceImpl.syncWorkHours`, `ManHourCoreFieldFixer`.
**Skimmed only:** `ManHourItemServiceImpl`, `ManHourPostingCheckServiceImpl`, `ManHourRevisionService`, `ManHourBpmApplyService`, `TaskValidator`, `PlanTemplateServiceImpl`, both manhour controllers (grep found no arithmetic). Seed doc 04-工时域-业务规则.md was read up to TS-R-021.
**Possibly missed:** SQL-side totals in mapper XML (e.g. `selectProjectApprovalHours`, works_end page queries), WIP labour-cost building (`WipService.buildLaborCostRespVO`), the opportunity 90-day visibility window (`OpportunityFilterUtils`), and the incomplete `getResourceShortage` capacity/saturation numbers (always null). No credentials were found, and no instruction-like text aimed at automated tools.