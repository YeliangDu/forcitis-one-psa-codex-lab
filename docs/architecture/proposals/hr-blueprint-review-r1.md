复审结论：必改
commit：08a0d18a31bccfaf5e4e6c36383d04be5f5d9b2e
复审者：claude-opus-5-5-首轮-adversarial-0079

patch-id：11787dc3acfb52eaf674337c39c60beb1c4d9727

Impact: docs only (ADR-0079 proposed + two proposal attachments, base ac6c97b62). No code callers. The ADR will constrain every future card in organization.workforce / attendance / leave / payroll / administration and the payroll→finance edges; findings below were proved against adopted ADRs, user rulings in docs/decisions, planned.yaml, statute notes and docs/legacy/runtime-usage/a8-templates.tsv.

## Must-fix

### M1. Event direction payroll → finance contradicts ADR-0051 decision 7.2.7 and creates a module cycle
- Where: ADR-0079.md:87 (`月度薪资批准、发放、过账请求 | payroll | 财务`), :65, :67.
- Evidence: ADR-0051 decision 7 item 2.7: "`procurement.subcontracting`、`expense.spend`、`organization.payroll` → `payables.payment`，不反向（ADR-0022 决定 7）". planned.yaml AGG-73 (line ~1141): payroll *calls* AGG-52 (submit payable, api) and AGG-77 (posting, api) and *subscribes* to their completion events; its own events are "薪酬已发放（仅分析）". Layer snapshot ADR-0051 7.1: payroll L13, payables.payment L12, accounting.ledger L5.
- Scenario: a payables/ledger subscriber to "月度薪资批准/发放" adds payables→payroll (or ledger→payroll) next to the mandated payroll→payables / payroll→ledger edges = cycle; ModularityTest would reject the first implementing card.
- Fix: row becomes "payroll → payables.payment (api: submit payable) / operations.facts or ledger (api: posting candidate); payroll subscribes to payable-settled and posting-done". Name the finance contexts in §2 instead of "财务上下文", and list payroll's full upstreams in §2 dependency sentence (payables.payment, posting pipeline, platform.approval, platform.integration).

### M2. Invariant 6 puts social insurance on the wage payer; statute and Q-09 put it on the employing unit; walkthrough contradicts the invariant
- Where: ADR-0079.md:98; walkthrough step 7 ("按用人法人核算").
- Evidence: SOC#个人社保登记 / SOC#社保申报与告知 (social-insurance.md:43-44): obligation is the 用人单位's. IIT#扣缴义务人 (iit-law.md:36): payer withholds IIT. Q-09 (intent 2026-10-16-134134; enterprise-info-map.md:317) defines three separate relations — 劳动合同用人单位, 数据范围法人, 个税扣缴义务人 — "分别记录，不一致时预警 … 这是策略，不是不变量". planned AGG-73 is keyed by 扣缴义务人口径, AGG-67 by 用人单位口径.
- Scenario: employee contracted by entity A, paid by entity B (the Q-09 case the user explicitly kept legal): invariant 6 makes B register/pay social insurance — unlawful for A; walkthrough step 7 instead computes payroll by employing entity — wrong IIT withholder. The ADR states both.
- Fix: split: IIT withholding and payroll run by 扣缴义务人 (payer); social insurance/housing fund by 用人单位 (contract party); mismatch is a warning policy per Q-09, not an invariant.

### M3. C5/§4/walkthrough conflate ADR-0073's legal-entity attribution with the employing entity
- Where: ADR-0079.md:37 (C5 "用人法人变更 … 【ADR】0073"), :81, :89; walkthrough step 10 (cross-entity transfer gated by #892–#896); gaps table row C5.
- Evidence: ADR-0073 decision 3 changes `employee_legal_entities` = 成本法人 / data-scope attribution (finance countersign). planned.yaml AGG-117: "Q-09 三口径中的「数据范围法人」"; its event "法人归属已变更" is already drafted (so "尚未命名" at :89 is inaccurate for that one). The employing entity lives on the labor contract (AGG-67 "用人单位口径").
- Scenario: HR moves a person's data-scope entity via ADR-0073 command → blueprint fires "用人法人变更" and walkthrough step 10 terminates the labour contract — a cost re-attribution would end a labour contract.
- Fix: C5 lists three distinct changes (data-scope/cost attribution = ADR-0073 command; employing entity = contract end + new contract under C6; withholding entity = payroll), each with its own event and subscriber.

### M4. Cross-entity transfer contract handling is decided by the ADR, but it is an open user question
- Where: ADR-0079.md:73 ("跨法人调动在同一生效日终止旧合同、订立新合同"); walkthrough step 10.
- Evidence: docs/decisions/decision-pages/archive/PPL_TRACE.md lists "P07-05 跨法人调动合同" as a user page question (PPL_VS07); no answer exists (no ppl answers file; `grep -rl P07-05 docs/` hits only PPL_TRACE). Statute options differ materially: LAB#用工年限承接 (labor-contract.md:45) merges service years when the move is not the employee's choice; termination vs tripartite change drives economic compensation (LAB#经济补偿), probation (LAB#试用期限制) and open-ended-contract eligibility (LAB#无固定期限条件). CLAUDE.md baseline 3: never decide for the user.
- Fix: move it to §8 as a question (with the service-year consequence), or cite a ruling.

### M5. "离职交接" contradicts user ruling Q-D (out of scope)
- Where: ADR-0079.md:50 (C18 "交接"), :63 (administration "离职交接等行政事项"), :103 (invariant 11 "交接"); gaps row C18 "交接归 administration"; walkthrough step 13.
- Evidence: intent 2026-10-16-082311 Q-D (all recommended): "资质维护纳入，工商年报与统计、离职交接 … 不做"; question text (decision-pages/archive/STEP12.json Q-D): "离职交接状态 … 不做的各项留在线下，将来要做须回第 ② 步补对象".
- Fix: drop 交接 from C18/§2/inv. 11 (keep it in §6 out of scope citing Q-D), or raise it to the user as a reversal of Q-D. IT-asset return (administration, AGG-115) is a different capability and may stay.

### M6. Pre-separation work cannot be triggered: events fire only on effect, invariant 11 makes every follow-up post-effective
- Where: ADR-0079.md:81, :103.
- Evidence: ADR-0068 decision 1 item 9: "命令生效时才发布，录入与撤回只写审计（在事件注册表登记为有意不发）"; item 5: separation effective = day after last working day, and identity closes the account on effect (ADR-0079 walkthrough 13). IT-asset return must finish by the last working day (PPL_TRACE V-15: generated at registration, cancelled on withdrawal); LAB Art. 50 (LAB#离职证明与保管) ties certificate/transfer to the termination date.
- Scenario: last day 31 Aug registered on 1 Aug; administration first learns on 1 Sep 00:00 when the person can no longer log in; asset-return task cannot be done before leaving; withdrawal of a registered separation has no signal at all.
- Fix: blueprint must say how downstream learns of a *registered* separation (workforce read api for pending separations, or a registered/withdrawn event — the latter needs a superseding ADR on ADR-0068 1.9; say which).

### M7. Employment types already ruled by the user are absent; several invariants are false for them
- Where: whole §1/§3/§5; C3 "实际用工日建立劳动关系", invariants 4, 5, 8.
- Evidence: enterprise-info-map.md:119 OBJ-56 人员: "与我方法人存在劳动或劳务关系的自然人 … 用工类型：正式 / 实习 / 返聘 / 劳务派遣（用户 2026-10-15 Q01 …）"; planned AGG-62 内含 "用工类型"; intent 2026-10-16-162821 P-T4 "退休返聘、实习不参保，其余都参保"; ADR-0051 decision 5: 劳务派遣协议 belongs to procurement.subcontracting; Q-12 (intent 134134) excludes interns/outsourced from headcount. Legacy: 实习员工入职 template exists (a8-templates.tsv).
- Scenario: a retiree rehire has a 劳务 relation, no labour contract, no social insurance: invariant 4 raises "未签书面合同满一个月" forever; payroll (C15) enrols him in social insurance contrary to P-T4; a dispatched worker gets a labour contract with us although the employer is the agency.
- Fix: add employment type as a first-class dimension of the employee in C3 and §3, scope invariants 4/5/8 and C15 by type, and state who holds the dispatch agreement (procurement.subcontracting) vs the person record (workforce).

### M8. Highest-volume HR processes in the cited production evidence have no capability/owner (外勤, 调薪)
- Where: §1 capability list; §8; evidence line :147 itself lists 调薪 48 / 1,070.
- Evidence (docs/legacy/runtime-usage/a8-templates.tsv, total / last_180d): 外勤申请表 37,797 / 3,476 (largest HR flow, 4x 日常请假); 调薪申请单 1,070 / 48. 外勤 is pending user question P13-03 (PPL_TRACE); 调薪审批 is pending P13-13. Salary is a mandatory contract clause (LAB#劳动合同必备条款, labor-contract.md:36) and a wage change is a written contract change (LAB#劳动合同书面变更), while §2 says workforce "不持有 工资" and planned AGG-93 puts 调整薪酬档案 in payroll — two writers of the agreed wage with no rule.
- Fix: add C for 外勤/出差 attendance exemption (owner leave or attendance; link to expense travel) and C for 调薪 (owner payroll's salary file, with the contract-change record in workforce referencing it, or vice versa — state which is authoritative); add P13-03 and P13-13 to §8.

### M9. Production evidence misquoted to justify a rejected alternative
- Where: ADR-0079.md:160 ("老系统无独立招聘、绩效模板"); gaps rows C2 ("「未进人才库」累计 12 次") and C8 ("老系统无独立绩效模板").
- Evidence (same TSV): 应聘登记表 2,360, 面试评估表 1,108, 录用审批表 450, 用人申请表 211 (recruiting); 月度评估表-交付人员 2,516, -BU支持人员 719, -总部人员 244, 管理层一对一 1,738, 试用期评估流程 113+96 (performance; AGG-106 itself names 管理层一对一、成长记录).
- Fix: correct the counts; re-argue alternative 1 on boundaries (ADR-0051 decision 3/4.5), not on "small volume".

### M10. Q9/Q10 answers can overturn the boundary; options are not bounded
- Where: ADR-0079.md:34, :40, :115, :160.
- Evidence: ADR-0051 后果 4: "招聘需求若要多级审批须移出 `organization.workforce`"; decision 4.5/7.3: workforce must not depend on platform.approval (existing edge approval→workforce). Performance (AGG-106) is in workforce under the same constraint.
- Scenario: user answers Q9 "full recruiting with multi-level approval" or Q10 "calibrated multi-level performance review" → recruiting/performance must leave workforce → §2 and alternative 1 overturned, contrary to card #975 "must not need to be overturned".
- Fix: state in Q9/Q10 that options needing multi-level approval move the capability out of workforce under a new ADR, or limit options to single-level.

### M11. Ownership of the SPI identity data is unassigned although payroll, social insurance and the statutory roster need it
- Where: §2 table; invariant 9 (:101).
- Evidence: IIT#扣缴义务人 ("识别号须核实"), SOC#个人社保登记, LAB#劳动关系建立 ("须建立职工名册") all need the ID number; ADR-0022 background lists 员工银行卡 as master data entering the new system; ADR-0043 table: 证件号 SPI, 银行卡 SPI, 工资 PI/SPI pending.
- Scenario: if workforce owns ID numbers, the U-14 gate (no new SPI before legal answer) blocks workforce (W0); if payroll owns them, workforce cannot produce the roster and payroll writes person master data — both contradict §2 "一个对象只由一个上下文写".
- Fix: name the owner of 证件号, 银行账户, 户籍/住址 in §2 and which gate applies to which context.

### M12. Statutory obligations whose keys are in the repo notes are missing (card asks for completeness)
- 职工名册: LAB#劳动关系建立 (cited by C3 and inv. 4) says "须建立职工名册"; no capability.
- 竞业限制 monthly compensation after separation: LAB#保密与竞业限制; plus 代通知金 LAB#代通知金基数, 经济补偿 LAB#经济补偿, 用工年限承接 LAB#用工年限承接. C18 only says "经济补偿依据"; no context pays anyone after separation — planned AGG-93 ends the salary file on `Separated`, and pending user question P13-12 "离职结算项进不进薪酬" is not in §8.
- Protected statuses (LAB Art. 42: no Art. 40/41 dismissal during medical period, pregnancy/maternity/nursing, work injury) and union notice before unilateral dismissal (LAB Art. 43): not in notes (unverified against repo notes), but they are key invariants on the single SEPARATE command.
- Fix: add 职工名册 to C3, a post-separation payments capability with owner, P13-12 to §8, and the protected-status check as an invariant (or explicitly out of scope with reason).

### M13. Citation and statute misstatements
- Invariant 10 (:102) cites ADR-0051 4.5 for "请假、加班、薪资走 platform.approval"; 4.5 says only the workforce single-level part, and 7.3's approval list (AGG-05,12,13,15,17,22,40,45,59,75,79) has no leave/overtime/payroll-run aggregate. Correct source is planned.yaml AGG-70/72/73 (S-42).
- Invariant 8 (:100) "生效与停止以经办回执为准" cited to SOC#个人社保登记, whose text is "不登记不免除缴费义务": the obligation starts at 用工 regardless of the receipt. Scenario: late registration → no deduction for the gap months → arrears. Split obligation start (用工日) from filing status (receipt).
- C1 (:33) cites ADR-0068 第 4 条 for 职级; ADR-0068 §4 has only "岗位：新建与停用，不做职责与编制"; the grade scheme is pending (AGG-64 "职位族与职级方案待 J 系列确认页"; intent 134134 Q-02 "待安排岗位体系重梳工作后定"; ADR-0029 shelved). Cite AGG-64/J-series and list it under 后果 未定.
- Invariant 9 (:101) "须法务答复 U-11、U-14": U-14 is a user choice (ADR-0043 待用户决定 U-14 "暂不定"), not a legal answer.
- Walkthrough step 3: "三年，试用期不超过二个月（LAB#试用期限制）" — Art. 19 caps 二个月 only for 一年以上不满三年; a three-year term falls under 三年以上 → up to six months.
- Walkthrough step 13: "15 日内出具离职证明" — LAB#离职证明与保管: certificate at termination; the 15 days apply to file and social-insurance transfer.

## Suggestions
1. Header BC omits `organization.leave` (owner of C10–C12) because contexts.yaml still lacks it although ADR-0051 decision 1.2 required registering it at adoption; register it first (ADR-0051 obligation, not new) and add `cost.timesheet`, `payables.payment`. 影响面 :119 calls leave "新建上下文", contradicting :55 "不新增人事上下文".
2. Slot PR #976 is still open; ADR-0079 is not on main, so the number is not yet reserved and 日期 must become the slot merge date (readme-lifecycle §3, §8.1).
3. Trigger intent `2026-11-02-132807-…` exists only as an uncommitted file in worktree one-lead-a3209a (not on main or any pushed branch); the quote matches. Make sure it merges before adoption.
4. §8 says "题面、选项与推荐见确认页" with no path; the page is not in the diff, so options cannot be reviewed. Add its path.
5. Q2 is a legal reading of LAB Art. 19 (same employer, probation once) → route to 法务 like U-11 rather than a business choice; Q4 is bounded by statute (employer obligation continues while the relation subsists) — present only the cost-bearing choice. Q6 is bounded by ADR-0051 7.2.7 (payroll submits payables) and ADR-0051 后果 4 ("待财务确认").
6. Search record (§9.2) covers the replayed terms correctly but omits objects the ADR decides on: 离职 (19 ADRs incl. 0026/0027/0034/0055/0064), 法人归属 (0026, 0073 — see M3), 招聘 (0039), 执业 (0060, 0064), 工号 (0033), 派遣 (0051 decision 5).
7. readme-lifecycle §7 one decision per ADR: the ten answers will be written into §8 of the same ADR; consider recording them as rulings referenced by the ADR.
8. Lifecycle 3 "同一人同一日至多一份生效合同" ignores LAB Art. 69 (part-time with more than one employer, each group entity is an employer); scope it per employing entity.
9. §4 says leave and cost.timesheet subscribe to calendar/standard-hours changes; planned AGG-108 says "仅分析, S-42 … 经 api 读，不订阅".
10. Evidence understates leave volume (年假申请表 14,131, 特殊假别 1,472, 补卡 18,027, 销假 174, 年假延期 333 not counted) and 转正 (old template 转正申请表-停用 500 → 599 cumulative).
11. Not placed: work injury (停工留薪期 pay), maternity leave and 生育津贴 offset in payroll, medical period, annual IIT reconciliation handling (IIT#预扣与年度汇算 is cited but no capability), IIT withholding for non-employee payees (AGG-95 already subscribes to 利润分配已支付 for shareholder dividends — payroll's IIT scope is wider than employees), 年休假跨年/未休补偿 (LEAV#年休假跨年, LEAV#条例未休报酬), 人事异动表 (258), capacity/availability feed to delivery resource planning.
12. Retention: ADR-0068 1.7 purges employee history at 10 years after separation; contracts (2 years), wage records (2 years), IIT deduction evidence (5 years), PIA records (3 years) and posted payroll facts (never deleted, CS-3.9) live in other contexts — state each context owns its retention and how purge affects references.
13. Sick-leave reasons and medical certificates make leave hold health data (PPL_TRACE: 病假事由 → U-11); invariant 9 should cover leave too.
14. Workforce growth: card-level aggregate control is acknowledged in 后果; add a criterion that triggers a split ADR (e.g., any capability needing platform.approval).

## Checked and fine
- Header order and fields (状态/日期/档位/起草/类别/BC/阶段) per readme-lifecycle §3; 类别 "边界与模块" in §3.1; BC values all present in docs/architecture/ssot/domain/contexts.yaml (workforce L59, attendance L31, administration L41, identity L87, payroll L409); 阶段 W0/W2/W4 present in transitions.yaml (L110/124/136) and match ADR-0054 decision 1 (W0 人事, W2 请假与加班, W4 薪酬).
- Section names and order match §5; size 16,622 bytes < 20,480.
- All cited statute keys exist: LAB#劳动关系建立/书面劳动合同/无固定期限条件/试用期限制/劳动合同书面变更/离职证明与保管/经济补偿/未签书面合同责任; LEAV#年休假档位/年假公历年度/新进年假折算/离职年假折算/加班工资分类/工资支付记录/工资支付日期/离职工资结清/工资合法代扣/年休假资格; SOC#个人社保登记/社保申报与告知/公积金增减员/公积金汇缴期限; IIT#扣缴义务人/全员全额申报/预扣与年度汇算/附加扣除信息提交; PIPL#保存期限/保护影响评估 (grep of the five notes files). Content mismatches listed in M2, M13.
- Search record replay (`git grep -l <term> ac6c97b62 -- 'docs/adr/ADR-*.md'`): hits equal the listed ADRs for every listed term.
- Legacy counts at :147 match a8-templates.tsv for the named templates (日常请假 804/9,904; 员工入职 63/754; 员工离职申请 36/873; 试用期员工转正申请 36/99; 调薪 48/1,070; 月度考勤确认 12/1,078; 续签意向 19/360; 停薪留职 1/14).
- ADR-0051: B-13/B-14/B-27/B-15 split and aggregate lists (decision 3 table), 4.5 single-level approvals, incentive merged into payroll — matches §2. ADR-0068 1.3 phase column PROBATION/REGULAR and §3 unpaid leave — matches C4, C13, alternative 2. ADR-0078 decision 4 (复职沿用工号) — matches inv. 3/C19. ADR-0055 decision 2 (workforce is SoR incl. 劳动合同) — matches. ADR-0064 decision 5 allows subscribing to certificate events once AGG-66 publishes them — compatible with §4.
- Rulings: 134134 Q-08 (no 编制), 2026-10-28-172404 (主任职不重叠，兼职不限), 152603 (WeCom clock-in, payroll in-system, company withholds IIT), 2026-10-22-104551 (overtime threshold), 2026-10-20-204733 (practice-seal check needs certificate record) — all say what the ADR claims.
- Dependency directions leave→workforce/attendance/approval, timesheet→leave, administration→workforce, identity→workforce: no cycle against module-dependencies.txt and the ADR-0051 layer snapshot (only M1 creates one).
- Break verification: no line of defence (guard/test) is added by this docs-only diff; nothing to break.
