复审结论：必改
commit：a0224c2d930b1f56acf10dedc2c7a1a4cae1bc29
复审者：opus-首轮-adr0079-r4

patch-id：6d6acd49c0ae51633e751a2fcc71dd5000c080ee

Impact: docs only. Files: ADR-0079 adoption, contexts.yaml SSOT, and three proposal attachments plus the r3 record. No code, tables, events or checks. The readers are future workforce, leave and payroll cards and agents, which treat the adopted, frozen ADR and contexts.yaml as authority. Once the ADR is adopted, wording errors can be fixed only by erratum or a new ADR. Scope: `git diff origin/main...a0224c2d9` (merge-base a6cf8fb57, 6 files, +156/-38).

## Must-fix

1. Invariant 13 presents a lead default for cross-entity transfer as part of the legal rule.
   - Where: docs/adr/ADR-0079.md:112. The text reads «同一用人单位只约定一次试用期（……法务答复 `142458`）：停薪留职或长病假后返岗、违法解除经裁决恢复、跨法人调动，均不设试用期；离职结清后再入职允许设试用期……（lead 默认，用户可改）».
   - Evidence: intent 2026-11-02-142458 (in worktree one-lead-a3209a) says of cross-entity transfer only «跨法人调动不建议约定». It is a recommendation, not a prohibition. The lead's own rule "no probation for cross-entity transfers" is a default. The premise of the clause, «同一用人单位», also does not cover a different employer. The marker «（lead 默认，用户可改）» follows the semicolon, so it binds only to the rehire clause.
   - Problem: a lead decision is recorded as a legal answer and is not marked as the lead's (baseline 3). Agents will implement it as a hard legal rule.
   - Fix: take cross-entity transfer out of the «均不设» list. Write it separately as «跨法人调动：法务不建议约定，lead 默认不设（用户可改）», or put one «lead 默认，用户可改» marker in front of both lead defaults.

2. The payout-failure rule is attributed to the finance answer, but the record does not hold it, and it conflicts with the ADR's own payroll lifecycle.
   - Where: docs/adr/ADR-0079.md:74. The text reads «月度薪资的「已发放」由付款结果置位，银行退票或代发失败由付款作废重发、薪资回到异常处理（财务答复 `142458`；……）».
   - Evidence, record: intent 142458 holds only the routing answer (payout goes through payables.payment, and the citations to ADR-0016, AGG-52 and ADR-0026). It says nothing about who sets «已发放», about voiding and reissuing, or about payroll going back to exception handling.
   - Evidence, lifecycle: ADR-0079:83 (lifecycle item 5) lists the states 起草 → 计算 → 审核 → 批准 → 发放 → 过账, has no exception state, and says that after approval errors are corrected only by adjustment in a later month. Invariant 7 (line 106) forbids rewriting approved payroll.
   - Evidence, planned.yaml: AGG-73 (planned.yaml:1136) has no exception state. AGG-52 (planned.yaml:757-759) publishes only «应付已结清» to B-15. The bank-return path («冲回应付核销», on «付款已退票») is internal to B-10, so no event tells payroll that a payout bounced.
   - Problem: the frozen ADR cites a source for text that is not in it. «薪资回到异常处理» can also be read as moving an approved payroll back to an earlier state, which contradicts lifecycle item 5 and invariant 7. No planned event carries it.
   - Fix (choose one):
     - If finance said this: append the finance wording to intent 142458.
     - Otherwise: mark the sentence «lead 设计».
   - In both cases, rephrase so it does not reopen approved payroll. For example: «退票或代发失败的人员在 payroll 记为待重发，已批准薪资不改写（同第 3 条第 5 项）；所需付款侧事件随 payroll 卡补入 planned.yaml AGG-52». Stay within the size budget (see the size note).

3. The approval evidence and the 证据 section cite a review file that does not exist, and claim it is already fixed.
   - Where: docs/adr/ADR-0079.md:24 («`hr-blueprint-review-r1.md` 至 `hr-blueprint-review-r4.md`») and :170 («采纳修订核对两轮，均已修订；记录 … 至 `hr-blueprint-review-r4.md`»).
   - Evidence: `git ls-tree -r --name-only a0224c2d9 docs/architecture/proposals` lists r1, r2 and r3 only. This review, r4, has not been committed, and its must-fixes are still open.
   - Problem: this is the same dangling-path defect as r3 must-fix 5. The frozen header also claims that a round is resolved before that round has happened.
   - Fix: commit this review as docs/architecture/proposals/hr-blueprint-review-r4.md in the PR, with a line on how each must-fix was handled. Otherwise drop r4 from both places.

4. The 用户原话 and 批准证据 sources cannot be opened from any ref.
   - Where: docs/adr/ADR-0079.md:18-23, and 背景 (intent 132807).
   - Evidence: `git log --all -- 'docs/decisions/2026-11-02-141959*' 'docs/decisions/2026-11-02-142458*' 'docs/decisions/2026-11-02-132807*'` is empty. The three files exist only as untracked files in worktree one-lead-a3209a (`git status --short docs/decisions` there shows `??`). readme-lifecycle §3 (line 50) requires «每项一个可打开的出处».
   - Problem: an adopted ADR whose approval cannot be verified fails open on the approval check (baseline 5).
   - Fix: the lead lands the three intents on main before this PR merges, or the PR carries them. Make this a merge precondition.

Size note: the ADR is 20,405 bytes, which leaves 75 bytes under the 20,480 limit (readme-lifecycle §6, line 124: generation fails above it for non-proposal status). Fixes 1 and 2 must shrink other text by the same amount. Candidates:
- The 检索记录 line, which is long and partly stale (see suggestions).
- The 用户原话 quotes.

## Suggestions

- Invariant 13, rehire: legal said «通说可以但看地方口径，原岗原待遇且工龄连续时被认定关系未切断风险更高». The ADR says «允许» with no caveat. Put that risk into the warning text, e.g. «提示曾在同一法人任职及地方口径风险».
- Invariant 13 needs workforce to tell rehire apart from restoration by award: both arrive as REINSTATE (ADR-0068:42). The card will need a reinstatement reason or kind. Return from unpaid leave is UNPAID_LEAVE_END, not REINSTATE. Worth one line in the gaps C4 row.
- 用户原话, second item: the quote puts «工资代发……» before «复职能否……». The source has the reverse order, so «……» suggests an order that is not in the source. The quoted text is legal and finance wording that the user forwarded. It is acceptable under «用户转来», but could say so.
- 证据 检索记录 (line 169) still reads «0068（相容，复职试用期交法务）». That is point-in-time, but now stale. Shortening it to «0068（相容）» also frees bytes.
- contexts.yaml `organization.leave` zh «假勤» differs from ADR-0051 B-27 «请假与加班» (ADR-0051:65). «假勤» also echoes the legacy «假勤薪酬» name. Use ADR-0051's name, or hand the name to one-glossary as ADR-0051 后果 5 does.
- contexts.yaml `organization.incentive` is now `status: retired` but keeps `basis: legacy-evidence` and `review: 待按目标架构重审`. Drop the review line, since a retired entry needs no re-derivation.
- Optionally note in 背景 or 后果 that this ADR closes ADR-0051 后果 4's open item «薪酬代发是否经 payables.payment 待财务确认».
- The committed r3 record has no outcome lines for its must-fixes. One line each would help later readers.

## Checked and fine

- r3 must-fix 1 (invariant 10, art.43): fixed. Line 109 now reads «已事先将理由通知工会，并记录工会意见与单位书面通知工会的处理结果». The written notice runs from the employer to the union, and there is no «或».
- r3 must-fix 2 (Q6 citation): fixed. Line 131 reads «用户判定：考勤由单位举证；法条依据未核实», and the art.6 attribution is gone.
- r3 must-fix 3 (dispatched workers): fixed.
  - Line 105 reads «P-T4「其余都参保」是否含派遣人员待用户确认». The interim «确认前本系统不为其参保、发薪» rests on art.58, not on an assumed user answer.
  - 后果 未定 (line 163) lists it.
  - questions.md «不在本页» still asks for it.
- r3 must-fix 4 (Q7): fixed. Line 132 cites IIT#全员全额申报 and marks «判定表所引个税法第九条与「60 号公告」未核实».
- r3 must-fix 5 (r3 file): fixed. docs/architecture/proposals/hr-blueprint-review-r3.md is committed. r4 is now dangling (must-fix 3).
- r3 must-fix 6 (leave blueprint_bc): fixed. contexts.yaml:47 has `blueprint_bc: null`, and the evidence line carries B-27.
- Finance routing matches the sources:
  - `payroll → payables.payment` (api) plus a subscription to its completion event agrees with ADR-0051:151 (decision 7 item 2.7: «organization.payroll → payables.payment，不反向»).
  - It agrees with planned.yaml AGG-52:750 (应付薪酬 法人 × 月一笔实发合计，明细留在薪酬结算), :756 (提交应付薪酬) and :759 (应付已结清 → B-15 薪酬已发放).
  - It agrees with AGG-73:1142 (AGG-52 提交应付薪酬、订阅应付已结清).
  - Payables does not read payroll (AGG-52:761, «AGG-73 来源（不校验）»).
  - ADR-0016 (single posting pipeline, line 20) is cited correctly.
  - The «已发放由付款结果置位» part is consistent with planned.yaml; only its attribution is wrong (must-fix 2).
- Probation, the legal items that are written faithfully:
  - No second probation after unpaid leave or long sick leave.
  - None after restoration by award.
  - Rehire is allowed, with a warning and no block, marked as the lead's default.
  - Art.19 para 2 is the «只能约定一次» clause. Art.83 is the liability clause, so citing it is accurate.
- Q1–Q10 against intent 141959 are faithful, as in r3. The Q5 «不停缴（lead 转述）» is marked as the lead's.
- Header (readme-lifecycle §3, lines 36-51):
  - Order is 状态, 日期, 档位, 起草, 类别, BC, 阶段, 用户原话, 批准证据.
  - The status word «已采纳» has no annotation.
  - The 用户原话 items follow «「原话」（YYYY-MM-DD，intent `文件名.md`）».
  - 批准证据 is a list of intent and review paths.
- Sections (§5): `grep -n '^## '` gives all nine, in order, at lines 26, 34, 139, 146, 150, 154, 159, 165 and 172.
- Size: `git show a0224c2d9:docs/adr/ADR-0079.md | wc -c` gives 20405, which is ≤ 20480.
- contexts.yaml:
  - It parses; 46 ids, all unique.
  - The leave entry has `status: planned` and `basis: adr` with an `adr:` list, per the header (lines 1-3). Its write set and dependencies match ADR-0079 decision 2.
  - The payroll responsibility matches ADR-0051 B-15 (AGG-73/74/75/76/93/95).
  - `organization.incentive` → retired matches ADR-0051:86.
  - 影响面 («登记 leave，改正 payroll、incentive 条目») matches the actual yaml diff, which has no zh renames for attendance or payroll. Those renames are handed to one-glossary in gaps.md item 1, consistent with ADR-0051 后果 5.
- Attachments agree with the ADR:
  - gaps.md C4 and C14, and 需调整 1 and 2.
  - walkthrough.md rows 7, 13 and 14, and 断点 item 6.
  - The questions.md header marks it answered, and its «不在本页» lines point to invariant 13 and to `142458`.
- There are no secrets and no row-level or personal data; only aggregate counts appear. There are no code, test or acceptance changes.
- Break verification: not applicable. The change adds no checks or lines of defence, and no ADR or contexts validator exists in scripts/ (`git grep -l 批准证据 -- '*.py' '*.sh' '*.java'` returns nothing).
