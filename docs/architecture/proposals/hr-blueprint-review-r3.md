复审结论：必改
commit：484279bea5a072ced4cbd53e548b195ef58a5e9d
复审者：opus-首轮-adr0079-r3

patch-id：a2c778c76bb89a1027844ec0f69da4fec284d7f3

Impact: docs only (ADR-0079 adoption, contexts.yaml SSOT, two proposal attachments). No code, tables or events. Readers are future workforce/leave/payroll cards and agents that treat the adopted, frozen ADR and contexts.yaml as authority. Once adopted the body is frozen, so wording errors can only be fixed later by erratum or a new ADR.

Scope: `git diff eb967b3c9 484279bea` (4 files, +55/-18). Spec: intent `2026-11-02-141959-one-lead-用户粘贴逐题判定表-1A-可采纳.md` (found only as an untracked file in worktree one-lead-a3209a), plus the lead's relay of the conditions.

## Must-fix

1. Q4 / invariant 10: the art.43 condition is misdirected and weakened.
   - Where: docs/adr/ADR-0079.md:103 (invariant 10), «并记录工会书面回复或处理结果».
   - Evidence: the user asked for «第43条事先通知工会理由并书面通知处理结果». Under labour contract law art.43 the employer must study the union's opinion and notify the union in writing of how it handled it. The written notice goes from the employer to the union.
   - Problem: the ADR records a «工会书面回复» (a reply from the union) and joins it with «或». Recording only the union's reply would satisfy the text, so the employer's written notice of the handling result, which is the user's third element, becomes optional.
   - Fix: «……已事先将理由通知工会；工会提出意见的，记录其意见及单位书面通知工会的处理结果». Drop the «或».

2. Q6: the citation is wrongly attributed to the user and does not cover attendance.
   - Where: docs/adr/ADR-0079.md:128, «（工资支付暂行规定第六条，用户判定表所引）».
   - Evidence: the intent says only «考勤用人单位举证，书面记录保存两年» and cites no article. The repo note LEAV#工资支付记录 (docs/architecture/frameworks/notes/leave-wage.md:58) records art.6 as the rule for wage payment records (amount, time, recipient), not attendance.
   - Problem: the frozen ADR states a user citation that is not in the record and applies an article whose subject differs.
   - Fix: write the user's own words, e.g. «（用户判定表：考勤由用人单位举证，书面记录保存两年；法条依据未核实）». Alternatively cite a verified source and remove «用户判定表所引».

3. Invariant 6: a confirmation the user was asked for is now treated as given.
   - Where: docs/adr/ADR-0079.md:102.
   - Evidence: the diff deletes «按正式员工理解（采纳时请用户确认）» and replaces it with «P-T4「其余都参保」指正式员工». The questions file still asks for this confirmation under «不在本页», and the intent's answer covers only Q1–Q10, so the user never confirmed it.
   - Problem: this decides for the user (baseline 3).
   - Fix: either relay the one-line confirmation to the user and cite the new intent, or keep the sentence as a pending item in 后果「未定」.

4. Q7 contradicts C16 inside the same ADR.
   - Where: docs/adr/ADR-0079.md:129, «个人所得税法第九条全员全额扣缴».
   - Evidence: C16 (line 51) cites the key IIT#全员全额申报. That key's note (docs/architecture/frameworks/notes/iit-law.md:37) places 全员全额扣缴申报 at IIT-01 第十条第二款. Art.9 defines the taxpayer and the withholding agent.
   - Problem: the ADR flags «60 号公告» as unverified but presents art.9 as fact.
   - Fix: cite IIT#全员全额申报 (art.10 para 2) and add «判定表引第九条», or mark the art.9 citation unverified the same way as «60 号公告».

5. 证据 points to a file that does not exist.
   - Where: docs/adr/ADR-0079.md:167, «采纳前条件修订另经 opus 核对（`r3.md`）».
   - Evidence: `git ls-tree -r 484279bea docs/architecture/proposals` lists hr-blueprint-review-r1.md and r2.md only. No r3 exists in any ref (`git log --all -- '*hr-blueprint-review-r3*'` is empty).
   - Problem: the frozen ADR would hold a dangling path. The «r2.md» short form is also ambiguous beside the full r1 path.
   - Fix: commit this review as docs/architecture/proposals/hr-blueprint-review-r3.md in the same PR, with the outcome of these must-fixes, or remove the reference. Use full file names.

6. contexts.yaml: `organization.leave` has `blueprint_bc: B-27`, which contradicts ADR-0051 and the file header.
   - Where: docs/architecture/ssot/domain/contexts.yaml:47.
   - Evidence: the header (line 2) defines blueprint_bc as the 《边界蓝图 v2.3》 number. ADR-0051 decision 3 (line 88 of ADR-0051) says `organization.leave` registers «蓝图未覆盖», and that mappings are entered by the implementing PR after checking the blueprint text. B-27 is ADR-0051's own table row id, not a v2.3 id.
   - Fix: set `blueprint_bc: null`. The evidence line already carries B-27.
   - Note: contract.agreement B-05 and administration B-31 have the same problem but are outside this diff; see suggestions.

Size note: the ADR is 20,181 bytes against the 20,480 limit. The fixes above must keep it under the limit (readme-lifecycle §7), so shorten other text if needed.

## Suggestions

- Q2, line 124, «外勤不产生加班»: the user said «外勤不等于加班». «不产生» can be read as forbidding approved overtime worked during field work. Suggested wording: «外勤本身不计加班；外勤期间的加班仍按加班申请».
- Q5, line 127, and invariant 6: the user said option B (shifting the employer share) is unlawful. Whether stopping contributions altogether is lawful was the separate legal question. The business outcome, continuing payment (5A), is the same either way. Suggested wording: «照常缴纳（用户选 A）；B 不合法（用户判定表所引……）», without adding a legal conclusion about «停缴».
- Q3: no event or api is named for how payroll learns the «工作交接办结日», which is recorded in workforce after the separation event. The card should say whether payroll reads it through the workforce api or receives a new event.
- Decision 2, line 60, still says `organization.leave` «尚待在 contexts.yaml 登记». The same commit registers it. This is acceptable as point-in-time text, but «（本稿采纳时登记）» would avoid the contradiction.
- Attachments: walkthrough.md (status line «提议附件»; rows 13 and 28 «交接不做») and gaps.md C18 do not mention the new «工作交接办结日» record. The questions file «不在本页» still says «停缴交法务» and «采纳时请确认 P-T4». The header note «保留原样» covers the questions file; the other two could get one line each.
- contexts.yaml zh names: leave is «假勤» but ADR-0051 B-27 says «请假与加班». ADR-0051 (line 174, and 后果 item 5 at line 228) hands attendance and payroll name fixes to one-glossary «不自行改». ADR-0079 影响面 lists the leave, payroll-responsibility and incentive edits but not the zh renames of attendance and payroll. Coordinate with one-glossary or list them in 影响面.
- Pre-existing, outside this diff: contract.agreement `blueprint_bc: B-05` and organization.administration `B-31` use ADR-0051 row ids in a field the header defines as v2.3 ids.
- Approval evidence: intents 141959 and 132807 are untracked in worktree one-lead-a3209a and are not on origin/main. They must land on main, before or with this PR, so that 用户原话 and 批准证据 can be opened.

## Checked and fine

- Header order and format: readme-lifecycle §3 order is 状态, 日期, 档位, 起草, 类别, BC, 阶段, then 用户原话 and 批准证据 (no 取代). Both fields are multi-value lists in the required form: «原话»（YYYY-MM-DD，intent `…md`）, and 批准证据 lists an intent file and review paths. Status word «已采纳» has no annotation. Category, BC and stage values are filled.
- Sections: all nine §5 headings are present in order (`grep -n '^## '`: lines 24, 32, 136, 143, 147, 151, 156, 162, 169).
- Size: `git show 484279bea:docs/adr/ADR-0079.md | wc -c` gives 20181, under 20480.
- Q1: written faithfully. Prior-years merge, record whether compensation was paid, no counting of prior years if paid (Implementation Regulations art.10). Option A's «调动时不付经济补偿» is dropped, so there is no conflict with the condition.
- Q3: one-payment wages at termination (art.9) and compensation on handover (art.50) are kept. Q-D is respected: no handover workflow, only a date (invariant 11). Option A's «离职后的月份也能发» is not repeated in the ADR body.
- Q4: art.42's seven prohibited situations and prior notice to the union are present. The third element is wrong (must-fix 1).
- Q5: the 5A cost split and the user's articles (社保法 60, 86; 劳动合同法 26(1)(2)) are cited as given. Removing «停薪留职期间能否停缴社保（法务）» from 未定 is consistent with the user's ruling, subject to the suggestion above.
- Q6: option B text and the two-year retention are present. The citation is wrong (must-fix 2).
- Q8: scans do not replace originals, and the ledger records where the original is kept. Faithful.
- Q9: notice, consent and retention period are present. PIPL#处理前告知 and PIPL#保存期限 exist (notes/pipl.md:42-43). The retention value is deferred to the user, not decided.
- Q10: the art.40(2) chain (not competent, then training or reassignment, then still not competent) is written as record-and-link. Faithful.
- 7A: «60 号公告» is marked unverified, as the relay asked.
- contexts.yaml status values: planned for leave, retired for incentive, planned for payroll. All are allowed by the header (line 1). Incentive retired and pointing to payroll matches ADR-0051 decision 2 (line 34) and the retirement list (line 86). Leave's write set and dependencies match ADR-0079 decision 2. Payroll's responsibility matches ADR-0051 B-15 (AGG-73/74/75/76/93/95) and ADR-0079 decision 2. `basis: adr` with an `adr:` list follows the existing convention (organization.administration).
- No secrets, no row-level data (aggregate counts only), no code or test changes.
- Break verification: not applicable. This change adds no checks or lines of defence.
