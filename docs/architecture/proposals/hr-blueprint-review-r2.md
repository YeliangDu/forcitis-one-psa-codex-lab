复审结论：必改
commit：ff18df1e39798e4914431ad87cb62bbc41b5f4ae
复审者：claude-opus-5-5-首轮-adversarial-0079-r2

patch-id：30606acf7c01e362045c542452eedb1fb698342c

Impact: this is a docs-only diff (ADR-0079, proposed, plus four proposal attachments; base origin/main ac6c97b62). It has no code callers. Every later card in organization.workforce / attendance / leave / payroll / administration, and the payroll -> payables.payment / operations.facts edges, will be built against it. The findings below were checked against adopted ADRs (0021, 0036, 0043, 0051, 0064, 0068), planned.yaml, docs/decisions, PPL_TRACE, the statute notes and a8-templates.tsv.

## Part 1: Round-1 must-fix status (M1-M13 against 08a0d18a3)

| # | Verdict | Evidence at ff18df1e3 |
|---|---|---|
| M1 payroll -> finance direction | Fixed | §2 now names payroll -> payables.payment (submit payable, api), AGG-77 (posting, api) and subscriptions to their completion events. "财务不订阅 payroll". §4 has no finance subscriber. Matches ADR-0051 7.2.7 and AGG-73 (planned.yaml:1141-1142). The open finance item behind this is new must-fix N2. |
| M2 SI vs IIT entity | Fixed | Invariant 4 splits IIT/payroll (payer, IIT#扣缴义务人), SI/HF (employing unit, SOC#个人社保登记) and data scope/cost (ADR-0073), with mismatch as a warning only (Q-09). Walkthrough step 7 agrees. Residual conflict with AGG-95 is in N5. |
| M3 ADR-0073 vs employing unit | Fixed | C5 lists the three changes separately. §4 has a separate row for data-scope entity (AGG-117) and puts employing-unit change under contract events. The AGG-117 row now names the wrong subscribers (N4). |
| M4 cross-entity contract decided | Fixed | It is now Q1, with no claim in §2-5 (gaps C5 "处理方式按 Q1"). Q1's options still have problems (suggestion S2). |
| M5 handover vs Q-D | Partly | ADR C18, §2, inv. 11 and §6 now say 交接不做 (Q-D). Walkthrough "断点汇总" item 5 still lists 交接 among the follow-up items that "没有承接方". That implies it should be built, against Q-D (intent 2026-10-16-082311: "离职交接 … 不做"). See N8. |
| M6 pre-separation trigger | Fixed in the ADR; conflicts with planned | §2 bullet: downstream reads registered separations through a workforce read api, with no new event, so ADR-0068 1.9 is unchanged. The example (administration creates asset return at registration) contradicts planned AGG-115 and is not listed under 需调整 (N5). |
| M7 employment types | Partly | Employment type is added in C3, §2, lifecycle 3 and invariants 5/6. The dispatched-worker case is still unscoped for SI and pay (N6). |
| M8 外勤 / 调薪 | Fixed | 外勤 is in C10 plus Q2. 调薪 is in C14 (payroll). §2: the contract wage clause references the salary-file version and is not stored twice. |
| M9 counts | Fixed | All counts at :147 and in C2, C8 and the questions match a8-templates.tsv (see Checked). Alternative 1 is re-argued on boundaries. |
| M10 Q9/Q10 overturn | Fixed | Q9B and Q10B say "须另立 ADR 移出人事模块". Invariant 9 and 后果 give the split trigger. The answers arrive before adoption, so §2 is revised rather than overturned. |
| M11 SPI owner | Partly | ID number and household/address go to workforce, bank account to payroll, and the U-11/U-14 gate is in inv. 8. Putting the bank account in payroll breaks two adopted ADRs and employee payments (N1). |
| M12 statutory completeness | Partly | Covered: 职工名册 (C3), protected-status and union check (inv. 10), P13-12 as Q3. Not covered: no owner for the non-compete agreement itself (LAB#保密与竞业限制), and Q3 option B has no owner (N7). |
| M13 citations | Fixed | Inv. 9 now cites AGG-70/72/73, all with platform.approval S-42 (planned.yaml:1081, 1105, 1142). Inv. 6 separates obligation start from receipt. C1 cites ADR-0068 §4 for org units and positions, and AGG-64/J-series for grades. Inv. 8 has U-11 for legal and U-14 for the user. Walkthrough step 3: three-year term allows up to six months (Art. 19 "三年以上" includes 三年). Step 13: certificate at termination, 15 days for file and SI transfer. |

## Part 2: New must-fix

### N1. The employee bank account is placed in payroll. This contradicts ADR-0021/0036, cuts off reimbursement payments, and is missing from the search record
- Where: ADR-0079.md §2 table, payroll row ("员工收款银行账户"); inv. 8.
- Evidence:
  - ADR-0036:135: "员工银行卡按 ADR-0021 第 1 条属员工档案主数据，随 6e（自建 HRM）迁入". ADR-0021:18 also lists "员工档案与银行卡" as master data.
  - enterprise-info-map.md:273: employee reimbursements and loans use the employee as the counterparty, with no counterparty record.
  - payables.payment (AGG-53, planned.yaml:775) takes bank accounts from AGG-01, which has no employee entries.
  - ADR-0051 7.1: payables.payment is L12 and organization.payroll is L13. ADR-0051 7.2.7: payroll -> payables.payment, "不反向".
  - Replay: `git grep -l 银行卡 ac6c97b62 -- 'docs/adr/ADR-*.md'` returns 0021 0022 0036 0037 0043. None of these is in the search record (readme-lifecycle §9.2: a hit that is incompatible, not listed and not in 取代 fails review).
- Scenario: payables.payment pays an employee reimbursement or loan (ADR-0022 "员工借还款 进新系统") and needs the payee's bank account. It can only get it by reading payroll, which is a reverse edge and a cycle. The alternative is a second copy, which breaks §2 "一个对象只由一个上下文写".
- Fix: put the employee bank account in workforce, as ADR-0021/0036 do. It sits behind the same U-11/U-14 gate, and payroll and payables.payment read it through the api (both are downstream of workforce, L2). Alternatively, supersede ADR-0036 explicitly. Add 银行卡/银行账户 to the search record.

### N2. Routing payroll disbursement through payables.payment is still open with finance, but the ADR states it as decided
- Where: §2 dependency sentence; C14; gaps C14 ("发放经 `payables.payment`"); walkthrough step 7; §8 parks P13-10 (薪酬发放动作) as a card-level item.
- Evidence: ADR-0051 后果 4 (ADR-0051.md:227): "薪酬代发是否经 `payables.payment` 待财务确认（§7 第 14 条）". No intent in docs/decisions confirms it (`grep -l 代发 docs/decisions/*.md` finds only an unrelated approval-hook file). CLAUDE.md baseline 3 (never decide for the user).
- Instability: if finance answers "no", payroll's outbound edge changes (for example to accounting.cashbank). §2 and the walkthrough are then wrong, and the 2.7 citation stops covering it.
- Fix: list it under 后果 未定 as a finance confirmation, with the boundary consequence of each answer, or cite a finance ruling. Do not leave P13-10 as a card-level parameter question.

### N3. Invariant 10 fixes the answer to Q4
- Where: ADR-0079.md §5 inv. 10 ("单方解除须记录解除依据，并记录已核实法定禁止情形与工会通知"). It has no 【待】 tag, although C18 carries 【待】Q3、Q4.
- Evidence: hr-blueprint-questions.md Q4. Option A is word-for-word inv. 10. Option B is "只记录解除依据，不做核实确认".
- Scenario: the user picks Q4 B, and an invariant in §5 has to be rewritten. That is a decision taken for the user (baseline 3) and conflicts with card #975's "must not need to be overturned".
- Fix: keep only "记录解除依据" in inv. 10 and mark the verified-status/union record 【待】Q4. Or drop Q4 if it is treated as a legal duty (Art. 42/43), and then say so with the source.

### N4. §4 names the wrong subscribers for the data-scope entity change, against the AGG-117 it cites
- Where: §4 row "数据范围法人变更（ADR-0073，【规】AGG-117 已草名） | workforce | identity、成本".
- Evidence: planned.yaml:1032 (AGG-117 对外事件): "法人归属已变更（platform.identity：数据范围；B-16 等按日期经 api 读取，不订阅；B-15：三口径预警策略订阅，S-20）".
- Scenario: cost cards add a subscription that the plan forbids. Payroll (B-15), which must raise the Q-09 three-entity mismatch warning (inv. 4 "不一致只预警"), is missing as a subscriber, so the warning in inv. 4 has nothing to trigger it.
- Fix: subscribers are identity and payroll (warning). Cost reads by date through the api.

### N5. The gap table's "需调整" list leaves out planned.yaml conflicts. One of them files social insurance under the wrong entity
- Where: hr-blueprint-gaps.md "需调整" items 1-4.
- Evidence:
  - AGG-95 (planned.yaml:1195-1202) "个税扣缴与社保公积金申报 … 个税扣缴义务人口径" holds the SI/HF filings under the IIT withholder key. ADR inv. 4 requires SI by the employing unit.
  - AGG-115 (planned.yaml:1610): asset return is driven by the Separated (effective) event, "离职登记已撤销不订阅", with a reminder 7 days after effect. ADR §2 bullet and walkthrough step 13 create it at registration through the read api.
  - AGG-67 (planned.yaml:1008): contract events are "仅分析". ADR §4 has payroll subscribe for the SI basis.
  - AGG-74 (planned.yaml:1154) takes the SI basis from AGG-62 events, not the contract.
- Scenario: the AGG-95 card follows planned.yaml and files SI under the payer entity, which is unlawful in the Q-09 case where A employs and B pays.
- Fix: add AGG-95, AGG-115, AGG-67 and AGG-74 to 需调整, each with the correct reading, and name who changes planned.yaml.

### N6. The scope for dispatched workers is unstated. The user ruling P-T4, read literally, would enrol them in our SI
- Where: inv. 6 ("退休返聘、实习不参保（P-T4）"); C14/C15 ("按用工类型"); inv. 4.
- Evidence: P-T4 (intent 2026-10-16-162821) says "退休返聘、实习不参保，其余都参保". OBJ-56 counts 劳务派遣 as an employment type. ADR-0051 §5 puts the dispatch agreement in procurement.subcontracting. Statute (not in repo notes; labor-contract.md:83 says dispatch is "不在本组", so this is unverified against the repo): LAB Art. 58 makes the dispatching agency the employer, and the 劳务派遣暂行规定 Art. 18 has the agency pay SI.
- Scenario: a C15 card applies "其余都参保" and enrols dispatched workers under our entity, duplicating the agency. Payroll also pays wages that the agency pays. Inv. 4's "劳动合同用人单位" does not exist among our entities.
- Fix: state that for 劳务派遣, wages, SI and IIT are the agency's. Payroll excludes them, and we pay the agency through procurement.subcontracting -> payables. Workforce keeps only the person, assignment and attendance. Route the reading of P-T4's "其余" to legal and the user if in doubt.

### N7. Q3 option B leaves separation payments and their IIT without an owner and needs a forbidden edge
- Where: hr-blueprint-questions.md Q3 B ("补偿类由财务另行付款，个税另行处理").
- Evidence:
  - Economic compensation needs the 12-month average wage (LAB#补偿月工资口径, LAB#经济补偿), which is payroll data.
  - A finance context reading payroll violates ADR-0051 7.2.7 "不反向".
  - IIT on separation compensation is withheld by the paying entity in its 全员全额 return (IIT#扣缴义务人, IIT#全员全额申报). C16 gives that to payroll (AGG-95).
  - The non-compete agreement itself (LAB#保密与竞业限制) has no owner in C6 or C18.
- Scenario: the user picks B. Nobody can compute the amount without a payables -> payroll edge, and "个税另行处理" is not a lawful choice.
- Fix: rewrite B so that the amount is computed offline and entered as a payable, and payroll still withholds IIT by reading payables (payroll -> payables api, the allowed direction). Or drop B. Record the non-compete clause on the labour contract (C6).

### N8. Q8 option B (in-system e-signing) creates a module cycle that the question does not mention
- Where: hr-blueprint-questions.md Q8 B; §2 (contract in workforce).
- Evidence: ADR-0064 decision 3 (ADR-0064.md:39): "签章服务 … 不依赖任何调用方业务 BC … 只依赖 … `organization.workforce`（api、事件）". ADR-0064 guard (:123): 签章服务 must not depend on any business BC except workforce. ADR-0079 §4 already has 印章台账 subscribing to workforce certificate events.
- Scenario: the user picks B. Workforce (contract owner) calls 签章服务, which already depends on workforce, so there is a cycle. Either the contract leaves workforce, contrary to ADR-0051 §5 and AGG-67, or ADR-0064 is superseded.
- Also: B says "需选电子签名服务". The platform 签章服务 is already decided (intent 2026-10-20-201656) and paused by SGN (intent 2026-10-22-065356), and legal accepted the e-signing approach (intent 2026-10-20-224557). The option text should reference these.
- Fix: add to Q8 B "选 B 须另立 ADR（签章服务依赖 workforce，workforce 不能调用签章服务）" in the same way as Q9/Q10, and correct the service description.
- Related: the walkthrough "断点汇总" item 5 still lists 交接 (see M5). Remove it.

### N9. Q5 recommends an option that may be unlawful, presented as a business choice
- Where: hr-blueprint-questions.md Q5 C ("约定方式（A 或 B 或停缴）") and B (employee bears both employer and employee shares).
- Evidence: SOC#社保申报与告知 (social-insurance.md:44): "单位自行申报足额缴纳，代扣个人部分". SOC#个人社保登记: "不登记不免除缴费义务". The labour relation continues during 停薪留职 (ADR-0068 §3: the person stays employed). Whether contributions can be stopped by agreement, or the employer share shifted to the employee, is a legal question. The repo notes do not answer it.
- Scenario: the user picks C, which is recommended. Payroll then supports stopping contributions during an existing labour relation.
- Fix: send the lawfulness of "停缴" and of B to legal first, the same way as U-11 and rehire probation. Ask the user only about cost-bearing among the lawful options.

## Suggestions
1. readme-lifecycle §3/§8.1: slot PR #976 is still OPEN and ADR-0079 is not on origin/main, so 日期 must become the slot merge date. The trigger intent 2026-11-02-132807 is on no git ref (`git log --all` is empty). It exists only in the worktree one-lead-a3209a, and the branch claude/one-lead-intents-1030c named on the card is not on the remote (`git ls-remote`). The quote matches. Merge it before adoption.
2. Q1: the options are incomplete and partly legal. LAB#用工年限承接 (labor-contract.md:45) allows two paths: merge the years, or the original employer pays compensation and the years are not double-counted. A states "调动时不付经济补偿" as if the statute required it. "终止" is not a statutory ground for an employer-arranged move; the mechanism (consensual 解除 + new contract, or a tripartite change) is for legal. Offer the business choice as merge versus pay, and let legal confirm the mechanism.
3. Q9/Q10 cite cumulative counts only. 面试评估 has 0 in 180 days (last use 2021-12-06) and 管理层一对一 has 0 (last 2025-07-22). Give both columns, as the ADR's own legend (累计 / 近 180 天) requires.
4. Q2: P13-03 is "外勤出差" (PPL_TRACE:40), and 出差申请 is already in expense VS-08 事前申请 (intent 2026-10-16-202008). State whether a pre-approved trip also needs a leave-side 外勤 entry. Leave (L5) cannot read expense.spend (L13).
5. §2 dependency list for leave omits platform.integration (AGG-69 WeCom source). The list for payroll omits accounting.period (AGG-81, via AGG-95). It also omits the AGG-109 subscription, which is in payables.payment (already allowed) and needed for C16 non-employee IIT.
6. Q7: AGG-95 plans "platform.integration 申报端（api）". If A is answered, note that planned.yaml changes.
7. Q3 A conflicts with AGG-93 "订阅 Separated 终止薪酬档案" (payments after separation). Add it to 需调整, conditional on the answer.
8. Retiree-rehire and intern agreements (non-labour) have no owner. C6 covers only the labour contract, and OBJ-05 has no such agreement type. Put them in workforce at card level.
9. Walkthrough breakpoint 4 still says "用人法人变更没有事件名". The ADR now carries employing-unit change on the contract events, and AGG-117 has a draft name.
10. Gaps "PPL 去向" omits P09-01/02, P11-01..03 and the four PPL_B31 questions out of the 51.
11. Search record: also add 绩效 (0043, 0051), 证件号 (0037, 0043, 0064), organization.attendance (0025, 0033, 0066, 0072) and organization.administration (0060, 0061, 0064, 0067, 0074, 0076, 0077). I opened each and found them compatible, except 银行卡 (N1).
12. The intent 落点 says 确认页, and the card's deliverable 4 is a "Confirmation page". §8 says "在对话中交用户，不做在线页". Confirm with lead that this matches.
13. 迁移方案: cite P-T5 (candidate records migrate with PI protection, intent 2026-10-16-162821) for C2.

## Checked and fine
- Commit exists (`git cat-file -e`). Diff `git diff origin/main...ff18df1e3` has five files and 387 insertions. ADR size is 17,393 bytes, under 20,480. Section names and order match readme-lifecycle §5. Header field order matches §3. 类别 "边界与模块" is in §3.1. The BC ids exist in contexts.yaml (:31, :41, :59, :409, plus identity). It is one decision with the answers incorporated before adoption (§7).
- Search record replay at ac6c97b62: every listed term returns exactly the listed ADRs. 试用 36 68; PROBATION 68; 停薪留职 55 68; 复职 34 55 68 78; 劳动合同 51 55 63; 薪酬/社保/个税 21 22 25 32 36 37 43 51 54; 请假/考勤 18 51 54; 离职 19 ADRs as listed; 法人归属 26 27 33 58 68 73 77; 招聘 39 51; 执业 51 60 64; 工号 33 43 71 73 78; 派遣 51; organization.leave 51; organization.payroll 17 21 51.
- Counts against a8-templates.tsv:
  - 外勤 37,797/3,476; 补卡 18,027; 年假 14,131; 请假 9,904/804
  - 月度评估 2,516+719+244=3,479; 应聘 2,360; 一对一 1,738; 面试 1,108; 录用 450; 用人申请 211
  - 考勤确认 1,078/12; 调薪 1,070/48; 离职 873/36; 入职 754/63
  - 转正 99/36 + 停用 500; 试用期评估 113+96=209; 续签 360/19; 停薪留职 14/1
- All 27 statute keys cited in the ADR and attachments exist in the notes (grep across notes/*.md). SOC#个人社保登记 reads "不登记不免除缴费义务" (social-insurance.md:43).
- Rulings say what is claimed: Q-08 and Q-09 (134134); C-5 (072440); 152603; P-T4 (162821); Q-D (082311); 172404; 104551; 204733; 090004 (继续教育学时).
- ADR-0051 4.5 (single-level in workforce; approval->workforce edge exists) and 7.1 layers: leave L5 above workforce/attendance (L2) and approval (L4); cost.timesheet L9 above leave; payroll L13 above payables L12. §2 has no cycle except as noted in N1 and N8.
- ADR-0068: §4 org and position commands; 1.7 ten-year purge; 1.9 events on effect only. CS-3.9 exists. ADR-0043 U-14 is "暂不定，定前不处理新的 SPI" and 住址 is PI, not SPI.
- AGG-106 publishes 绩效评估已确认 to B-15 (matches §4). AGG-62 separation request is single-level (matches inv. 9). AGG-73 is keyed by the withholder (matches inv. 4).
- Legal items. Rehire probation: either answer leaves §2-5 unchanged; gaps item 2 already plans the REINSTATE tightening. U-11/U-14: inv. 8 gates columns, not contexts, and the W0 roster leaves out the ID number, so either answer is stable.
- Questions already decided elsewhere: none of Q1-Q10 has an answer in docs/decisions. Grep for 外勤, 跨法人调动, 离职结算, 经济补偿, 专项附加 and 扣缴端 found no hits. The 停薪留职 intents of 2026-10-30 cover only superadmin floors and timesheets. AGG-97 plans 员工确认, but there is no ruling, so Q6 is genuine.
- Break verification: this docs-only diff adds no guard or test, so there is nothing to break.
