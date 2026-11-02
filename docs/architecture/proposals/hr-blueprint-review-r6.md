复审结论：通过
commit：705bb8a12186b2216523f92106cf147355f4df78
复审者：claude-opus-5-5-首轮-adr0079-r6

patch-id：9ef0bc64bf88b6d91f8b00ee84d7a0d0072ddd22

Impact: docs only (ADR-0079 to adopted, plus three attachments); no code, tests, migrations or guards touched. Affects future HR cards that implement against ADR-0079 (workforce, payroll, delivery resource matching readers); zero code caller layers.

## Must-fix

None.

## Suggestions (do not block)

1. Merge order: the header cites intent `2026-11-02-155251-one-lead-11-A-12-A-13-C.md`. It is not on origin/main; it exists only on `origin/claude/one-lead-intents-1102c` (commit f975170c5). The header also cites `hr-blueprint-review-r6.md`, which does not exist yet in any tree. readme-lifecycle.md section 3 asks for each approval-evidence item to be an "可打开的出处". Land the intents PR first, or together with this one, and commit this review as r6. No guard checks this, so nothing fails if the order is wrong.
2. ADR-0079 證据 line "评审：opus 对抗两轮（必改 13 + 9）、采纳修订核对两轮（6 + 4）…r1 至 r6": the round count still describes r1–r5. Add r6 (adoption check, 0 必改) so the count matches the files cited.
3. hr-blueprint-scope.md:11 still says "决定第 1 条 C1–C21". It should now say C1–C22. The per-capability reference table in section 二 has no C22 row; the inputs for C22 only appear in the sentence at line 31.
4. hr-blueprint-scope.md:31 puts C22 in the sentence that begins "草稿完全没有、卡须从 ADR 与法规起步的", but then says C22 starts from draft 01 and 08. Give C22 its own sentence.
5. hr-blueprint-scope.md:59: after the insertion, "作为在职中的事件挂在 VS-07 之后、VS-13 之内" now reads as if it describes C22. It was meant for C13, C20 and C21. Split the sentence.
6. The Q1–Q13 conditions now sit in an attachment that is not frozen, while the ADR body is. The attachment says "改动须另立 ADR", but no guard enforces it. Consider a one-line note in the ADR naming the commit or the section-一 text as the frozen version, or accept the risk knowingly.
7. Invariants 6, 7, 10 and 11 still point to "第 8 条 Q5/Q6/Q4/Q3". These still resolve through the pointer in section 8 to section 一 of the attachment, so the references work. They are only one hop longer.

## Checked and fine

- Answers faithful. User: "11，A；12，A；13，C；" (intent 155251, read from ../one-lead-a3209a/docs/decisions/).
  - Q11 A (questions.md section 二, line 45): C20 row and section-一 Q11 match. That covers rule versions, democratic procedure and publication evidence; a dismissal for misconduct must cite the clause and the evidence; no day-to-day discipline flow.
  - Q12 A (line 49): C21 row and section-一 Q12 match. That covers accident date, the 30-day reminder, the determination result, the suspended-pay period start and end, and pay for that period; no medical content stored.
  - Q13 C (line 74, "纳入技能目录：建技能目录与员工技能评定"): C22 builds the catalogue and the assessment.
  - Labour disputes are excluded because C is the option without B. "线下办理，系统只保留证据" reuses the wording of the question set (option A) and matches the lead's 落点 in intent 155251. It does not decide anything new.
  - Training stays out of scope (section 6, unchanged).
  - Placing C22 in the workforce "执业资格与技能" partition with api reads for resource matching is a technical placement inside lead's remit. It agrees with OBJ-56 in objects.yaml:1050 ("技能目录为参考数据，供资源需求 OBJ-44 匹配").
- Nothing lost in the move. The 10 lines `- Q1…` through `- Q10…` of origin/main ADR-0079 section 8 and the first 10 lines `- Q…` of section 一 in questions.md are byte-identical (`diff` printed SAME). The dropped "待答：Q11…Q13" line is now answered. The "P13-10、P13-13 … 留各卡" line is kept in the ADR.
- The ADR says the conditions are binding: section 8 says "各题附加条件见附件…第一节，属本决定，按卡实施". The attachment heading says "ADR-0079 第 8 条的组成部分" and "随 ADR-0079 采纳生效…改动须另立 ADR".
- Adopted-status header against readme-lifecycle.md sections 2–3:
  - 状态 is "已采纳" with no annotation. 日期 is 2026-11-02.
  - The field order is 状态, 日期, 档位, 起草, 类别, BC, 阶段, 用户原话, 批准证据.
  - Each 用户原话 item is in the 「原话」（YYYY-MM-DD，intent `file.md`） form.
  - 批准证据 lists the intents 141959, 142458, 143550 and 155251 plus the review records.
  - 类别, BC and 阶段 are filled in.
- Nine sections: 背景, 决定, 影响面, 迁移方案, 回滚方案, 需同步修改的守护规则, 后果, 证据, 备选方案（未采用） (`grep '^## '`).
- Size: `git show 705bb8a12:docs/adr/ADR-0079.md | wc -c` = 19392, which is at most 20480.
- The search record can be replayed: `git grep -l <term> origin/main -- 'docs/adr/ADR-*.md'`.
  - 技能 returns 0013 and 0079. 0013 is unrelated (the frontend team's skills).
  - 劳动争议, 仲裁, 工伤 and 违纪 return only 0079 itself, which matches "无".
- Open items are still listed in 后果/未定: the legal basis of invariant 15, U-2, U-14, the reading of invariant 6 for dispatched workers, the grade scheme, and the event name for unpaid leave. Q11–Q13 were correctly removed from that list.
- ADR and attachments agree on:
  - the workforce partition name (ADR section 2 and scope section 四)
  - C20, C21 and C22 in gaps.md
  - the scope section-五 rows for Q11, Q12, Q13 and the skills catalogue
  - labour disputes under 范围外 (ADR section 6)
- Guards on the head worktree:
  - `rule_sizes.py`: 0 problems
  - `check_names.py`: 0 problems
  - `term_rules.py --check`: existing problem count unchanged at 96
- No secrets and no row-level data. The intent text and the ADR contain only aggregates and question labels.
- Break verification: not applicable. This change adds and claims no line of defence (docs only).
