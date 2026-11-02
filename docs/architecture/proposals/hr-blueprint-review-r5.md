复审结论：必改
commit：c3ac7df1b2ef08e528cb0d29160f0d25c67abf22
复审者：opus-首轮-adr0079-r5

patch-id：dcddb6fcb0eabf078e0e3e1985228498b8e820bb

Impact: a design-only ADR plus attachments (no code). It binds every later HR card: workforce, leave, payroll, administration and identity. One caller layer is proven: the cards and agents that will read invariants 8, 10 and 15 and the dependency bullet in decision 2.

PR #983 head is c3ac7df1b2ef08e528cb0d29160f0d25c67abf22 (`gh pr view 983`), which matches. Commit exists.

## Must-fix

1. **The payment-failure rule is re-attributed to the finance answer. This undoes r4 must-fix 2.** (correctness of sources, baseline 5)
   - Where: docs/adr/ADR-0079.md:79. The text reads «「已发放」以付款结果为准，退票或失败的人员记待重发，不改写已批准薪资（intent `142458`）».
   - Evidence: intent 2026-11-02-142458 (one-lead-a3209a/docs/decisions/) says «失败处理有归属：银行退回、代发失败，付款侧作废重发，薪酬结算侧回退到异常状态». It does not say «记待重发» or «不改写已批准薪资». hr-blueprint-review-r4.md:18-26 flagged exactly this attribution and asked for «lead 设计» if no source exists. Commit 2abfab95b applied that label. c3ac7df1b replaced «（lead 设计）» with «（intent `142458`）».
   - Fix: restore «lead 设计» for the sentence. If the intent is cited, cite only the part it says: payment side voids and reissues; payroll side goes to an exception state.

2. **Invariant 15 is attributed to a user ruling that does not contain it.** (baseline 3 and 5)
   - Where: ADR-0079.md:121 «执业注册单位须为当前用人单位；用人单位变更时提示变更注册（【裁】`2026-10-20-204733`）», and hr-blueprint-scope.md:100 «采纳 | 用户裁定 `2026-10-20-204733`».
   - Evidence: intent 2026-10-20-204733 says only «系统登记签章人、日期、文件哈希并校验注册执业资格有效期». Intent 2026-10-16-090004, cited on C7, says only that continuing-education hours are a certificate attribute. Neither says the registration unit must equal the current employer.
   - Why it matters: in a multi-entity group, whether the registering entity must be the contract employer is a business and legal choice. The registering entity could instead be the one that holds the qualification. A card would implement it as a hard invariant.
   - Fix: cite a verified statute article, or mark the rule «lead 默认，待用户确认» and list it under 后果 / 未定 (or as a question).

3. **Invariant 10 contradicts the new invariant 8 and C11, C14 and C21.** (correctness)
   - Where: ADR-0079.md:116 ends with «系统不存健康与孕产信息».
   - Conflict: the rewritten invariant 8 (ADR-0079.md:114) lists «病假医疗期体检职业病工伤、孕产» as SPI that the system will hold after an impact assessment. C11 includes 产假 and 病假. C14 includes «生育津贴与工伤停工留薪冲抵». C21 and Q12 option A (recommended, hr-blueprint-questions.md) store the injury-determination result and the injury-leave period. A leave card that reads invariant 10 literally cannot store a 产假 record.
   - Fix: scope the clause to the dismissal check. For example: «解除记录只记已核实第四十二条情形，不存健康与孕产详情».

4. **The invariant 8 entry «外勤定位（行踪轨迹，单独同意）» is not in the cited legal answer.** (baseline 5)
   - Evidence: intent 2026-11-02-143550 lists «定位打卡与外勤 GPS（行踪轨迹）» as SPI. It has no «单独同意» and no other consent rule except the third-party one (not under art. 13(1)(2)). No 2026-11-0* intent in any worktree contains «单独同意» (searched with grep).
   - Why it matters: attaching «单独同意» to one item only also implies the other SPI items do not need it. That is a legal conclusion with no recorded source. ADR-0043:110 still lists the art. 29 / art. 13(1)(2) question as open.
   - Fix: cite the source that says it, or mark it «lead 理解，待法务确认», or drop it.

5. **Two scope exclusions are new and were not put to the user.** (baseline 3)
   - Where: ADR-0079.md:125 adds «劳动争议与仲裁（系统只留证据）» and «技能目录（技能标签作员工属性保留）» to 范围外. hr-blueprint-scope.md:92 and :107 decide them without asking.
   - Evidence: the user scoped the blueprint as «一个人员全生命周期的所有能力» (intent 2026-11-02-132807). Earlier the user said «这个 BC 后期要持续建设直至满足 HRM 的总体功能要求» (intent 2026-10-16-162821, P-01).
   - Why it matters: whether the system tracks arbitration cases or keeps a skill catalogue is a business scope choice. The skill-tag acceptance cited (OBJ-56) is about tags, not about excluding a catalogue.
   - Fix: mark both as lead proposals for the user to confirm. This can be one line next to Q11 and Q12, or a Q13 with A = out of scope, B = in scope.

Size note: the ADR is 20,401 bytes, which leaves 79 bytes under the 20,480 limit (readme-lifecycle §7). The fixes above must be offset by trims.

## Suggestions

- hr-blueprint-gaps.md:10, :21-23 and hr-blueprint-questions.md:59 still say ADR-0043 U-11 is pending with legal or is blocking. Invariant 8 and intent 143550 now treat U-11 as answered, so only U-14 and the impact assessment remain.
- C21 ownership «workforce；payroll» (ADR-0079.md:61) is fixed before Q12 is answered. Under 12B («只在请假里记相应假期») the owner would be leave. Consider writing it as «归属随 Q12».
- C21 cites only 工伤保险条例 art. 17. The injury-leave period itself is art. 33. Art. 17 also starts the 30 days from an occupational-disease diagnosis, and gives the employee a one-year fallback. Q12's stem mentions only the accident date.
- The Q11 option C consequence says «须另行评估……是否移出». Invariant 9 says multi-level approval must leave workforce through a new ADR. Q9 and Q10 state the consequence that way, so Q11 C understates it.
- Q11 stem: art. 4 requires «公示，或者告知劳动者»; the stem has only «公示».
- Q5 now cites «劳动合同法第二十六条». The user cited «第26条(二)», and the earlier text «第一款第二项» was more precise.
- Decision 2 shortened «财务与付款不订阅、不读 payroll» to «财务不读 payroll». ADR-0051 decision 7 item 2.7 («不反向») still governs, but the ADR no longer restates the no-subscription rule.
- hr-blueprint-scope.md cites draft file names and a local `~/Downloads` path. These are evidence pointers, not draft IDs, but no one else can replay them. Its line 3 also says «不抄编号与名称» while using the draft's directory numbers.
- «上线后重复人员合并 留卡» (scope.md:108) touches lifecycle item 1 and invariant 3 (one person, one ID, for life). Overturning those is costly, so consider listing it under 后果 / 未定 rather than only in the attachment.

## Checked and fine

- No draft-style IDs: `grep -nE 'HCM-|CMD-|EVT-|FLW-|INV-|VSL-|RMM-|G-0|[A-Z]{2,5}-[0-9]{2,}'` over the four files at c3ac7df1b. The only hits are repo IDs: ADR-, AGG-, VS-, OBJ-56, B-27, U-n and P13-n.
- Size: `git show c3ac7df1b:docs/adr/ADR-0079.md | wc -c` gives 20401, which is ≤ 20480.
- Header (readme-lifecycle §3): the fields are in order. Status is 提议. 用户原话 and 批准证据 may appear at 提议 («视状态»). BC organization.leave is registered in contexts.yaml in this PR. Phases W0, W2 and W4 exist (transitions.yaml:110, 124, 136).
- Sections (§5): the nine level-2 headings are in the required order. Category 边界与模块 is in the §3.1 enum.
- Search record (§9) replayed with `git grep -l <term> origin/main -- 'docs/adr/ADR-*.md'` (origin/main 4bdf82222), excluding 0079 itself. Every listed hit set matches: 试用/PROBATION; 停薪留职/复职; 劳动合同; 薪酬/社保/个税; 请假/考勤; 离职; 法人归属; 招聘; 执业; 工号; 派遣; 银行卡/银行账户; 规章 → 0043; 个人信息删除 → 0031; organization.leave/payroll. 工伤, 违纪, 合并分立 and 仲裁 have no hits.
- Main has ADR-0079 at 提议 (`git show origin/main:docs/adr/ADR-0079.md`), so editing the body is allowed.
- Invariant 8 against intent 143550: the two dimensions are present. The SPI list matches the intent: biometric clock-in, location clock-in and field GPS, sick leave / medical / injury, pregnancy, bank card, the third parties in special additional deductions including under-14s, and ID number including ethnicity and household registration. The salary detail correctly leaves SPI (the intent puts it at L3). PIPL citations check out against the statute text: art. 13(1)(2) is the HR-management basis; art. 28 lists under-14 data as SPI; art. 31 requires guardian consent.
- Statutes added in this revision, checked against the statute text:
  - 劳动合同法 art. 34: after a merger or split the original contract continues.
  - 劳动合同法 art. 50: keep the texts of ended contracts at least two years.
  - 劳动合同法 art. 39(2): serious breach of workplace rules is grounds for dismissal.
  - 工伤保险条例 art. 17: apply for the injury determination within 30 days.
  - Art. 69 (part-time work for several employers) is unchanged.
- Q1–Q10 conditions against intent 141959: each condition is still present.
  - Q1: 实施条例 art. 10.
  - Q2: approval trail; field work is not overtime.
  - Q3: wages paid in one sum; compensation paid when the handover is done.
  - Q4: the three records.
  - Q5: B is illegal.
  - Q6: keep two years.
  - Q7: the 60号公告 is marked unverified.
  - Q8: a scan does not replace the original.
  - Q9: notice, consent and retention period.
  - Q10: the art. 40(2) chain.
  
  Rewording only. Invariant 13 still matches intent 142458.
- The FIX-vs-card split in hr-blueprint-scope.md §一 is sound in general. The written-in items are boundaries, ownership, lifecycle stages and statutory invariants. The card items are state names, commands, payloads and read models. The workforce partitions are a technical choice that lead may make.
- Q11 and Q12 are business choices. Each states a recommendation and gives full alternatives, apart from the Q11 C wording noted under suggestions.
- contexts.yaml: organization.leave is added; the payroll basis is set to adr; incentive is retired with a note. The file is unchanged in this revision and consistent with decision 2.
- Break verification: not applicable. This revision adds or claims no executable line of defence. The guard in «需同步修改的守护规则» is deferred to the cards.
