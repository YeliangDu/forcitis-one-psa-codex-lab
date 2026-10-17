# NAM-1017-GLO-07：交 N7 的 C 类问题

日期：2026-10-17。Codex 起草；返工第2轮。以下仅为待判断问题和推荐，不是业务或财务批准。

共 48 项：原 C3 + B 类改归 C44 + 英文未确认的宽口径“证据”1。全部保留 proposed；原始 reconcile 分类不改。每项一问，推荐仅依据本地既有 ADR、草案与用户已定边界，未联网补行业资料。

1. **招标项目**（`tender_project`，原 C 类，`masterdata.counterparty`）：招标项目是否独立登记并维护标段、采购方式，还是只存外部招标编号？ 推荐：保留独立外部引用登记的候选，由售前业务确认维护范围。
2. **销项发票**（`output_invoice`，原 C 类，`receivables.wip`）：销项发票是否作为独立单据维护开具与红字更正，还是仅引用外部结果？ 推荐：区分发票对象与开票事实，维护范围交税务与财务确认。
3. **供方协议**（`supplier_agreement`，原 C 类，`procurement.subcontracting`）：分包、采购、租赁、保险、居间、劳务派遣是否共用供方协议，还是分别管理？ 推荐：身份共用、协议类型与条款分开作为候选，交采购与合同负责方确认。
4. **应用组织变更**（`apply_org_change_set`，B 类改归 C，`organization.workforce`）：应用组织变更时，哪些离职必须挂起等待放行，其余变更是否照常生效？ 推荐：按 ADR-0039 保留逐人挂起、其余照常应用的候选，交业务与身份负责方确认。
5. **待放行离职**（`separation_hold`，B 类改归 C，`organization.workforce`）：哪些人员离职需要先挂起，谁核对后才能放行？ 推荐：按 ADR-0027/0028 的安全持有人保护边界复核，不把离职挂起当成人事离职生效。
6. **个人信息**（`personal_information`，B 类改归 C，`platform.audit`）：“个人信息”的系统范围是否采用原提议引用的法律定义？ 推荐：保留法律原意，由合规与业务确认范围，设计方只负责后续技术标记。
7. **敏感个人信息**（`sensitive_personal_information`，B 类改归 C，`platform.audit`）：哪些资料应归入“敏感个人信息”，是否沿用原提议的法定范围？ 推荐：保留原提议的法定范围与 SENSITIVE_PI 候选，由合规确认分类。
8. **迁入占用**（`migration_period_reservation`，B 类改归 C，`accounting.period`）：迁入期间的占用是在登记后就挡关账，还是仅提交中才挡关账？ 推荐：以 ADR-0036 第6条“只有提交中挡关账”为推荐；原提议“未解除均挡”的冲突须财务确认后修订。
9. **迁入标记**（`migration_marker`，B 类改归 C，`operations.facts`）：带迁入标记的事实是否不再由本系统补生成会计分录？ 推荐：按 ADR-0036 保留原生与迁入区分，免补分录的适用范围交财务确认。
10. **迁入过账通道**（`migration_posting_channel`，B 类改归 C，`operations.facts`）：迁入过账可写哪些期间，是否只供迁入用例调用并不发事件？ 推荐：按 ADR-0036 保留专用入口候选，期间与财务闸门由财务确认。
11. **我方参与记录**（`own_participation_record`，B 类改归 C，`masterdata.counterparty`）：我方在一个建设工程中的参与角色是否只记为线索，且允许关联多个我方项目？ 推荐：按原草案保留多条线索记录，交工程与交付业务确认。
12. **项目 WBS**（`project_wbs`，B 类改归 C，`delivery.execution`）：项目 WBS 是否一个项目一棵树，并分阶段、子项和工作包三类节点？ 推荐：沿现有草案作为推荐，节点与合同范围或变更的关系交交付业务确认。
13. **开票与收入额度**（`billing_revenue_limit`，B 类改归 C，`receivables.wip`）：开票上限和收入上限是否共用一个按协议管理的额度对象？ 推荐：两条上限分别核对付款条件与履约义务分摊价格，由业务与财务确认是否共用载体。
14. **客户认可证据类型开放配置**（`acceptance_evidence_type_admission`，B 类改归 C，`receivables.wip`）：哪些客户认可证据类型可以触发收入确认，是否按版本逐类开放？ 推荐：按 ADR-0047 的逐类开放候选，历史迁入不得作为原生触发类型，交财务确认。
15. **发票入账占用**（`invoice_posting_reservation`，B 类改归 C，`accounting.tax`）：同一张进项发票被费用或应付入账使用时，是否共用一个可入账余额限制？ 推荐：按原提议累计占用不超过可入账金额，由税务与财务确认含税及冲销口径。
16. **核销占用**（`receivables_collection__cash_application_reservation`，B 类改归 C，`receivables.collection`）：回款分配到应收余额时，是否先占用再转成核销记录？ 推荐：保留原提议“占用合计不超应收余额”，由收款与财务确认时点。
17. **核销占用**（`payables_payment__cash_application_reservation`，B 类改归 C，`payables.payment`）：付款分配到应付余额时，是否先占用再转成核销记录？ 推荐：保留原提议“占用合计不超应付余额”，由付款与财务确认时点。
18. **供方协议履约额度**（`supplier_agreement_performance_limit`，B 类改归 C，`procurement.subcontracting`）：供方履约累计确认额是否不得超过含已生效变更的协议金额？ 推荐：按原提议分别核对协议额与履约确认额，交采购与财务确认。
19. **分包变更已生效**（`subcontract_change_activated`，B 类改归 C，`procurement.subcontracting`）：分包变更何时算已生效，是否在此时更新供方协议履约额度？ 推荐：按原提议以审批通过且生效为候选，交采购与合同负责方确认。
20. **人工成本分配结果**（`labor_cost_allocation_result`，B 类改归 C，`cost.costing`）：人工成本分配结果是否每个期间只允许一份有效结果？ 推荐：保留人工成本“分配”与间接费用“分摊”的候选区别，由财务确认来源与更正方式。
21. **收付权责科目映射表**（`cash_accrual_account_mapping`，B 类改归 C，`accounting.ledger`）：收付实现制与权责发生制科目的对应是否独立于业务事件到凭证模板的映射？ 推荐：按 ADR-0022 保留两类映射分开的候选，交财务确认名称及粒度。
22. **控制科目授权表**（`control_account_authorization`，B 类改归 C，`accounting.ledger`）：控制科目允许的事实类型和入账通道是否按版本共同授权？ 推荐：按 ADR-0022 的控制科目约束复核，由财务确认授权维度。
23. **无异议已声明**（`no_objection_declared`，B 类改归 C，`delivery.acceptance`）：项目经理的无异议声明能证明什么，是否可参与视为同意的判定？ 推荐：声明仅作为证据输入，不自动等同客户认可或收入确认，交合同与财务确认。
24. **设计变更主张状态**（`design_change_claim_status`，B 类改归 C，`delivery.acceptance`）：设计变更是否仅标待主张或不主张，已经向客户主张后在哪里记录？ 推荐：主张事实由合同侧变更或索赔记录，交付侧状态范围由业务确认。
25. **认可日**（`acceptance_date`，B 类改归 C，`delivery.acceptance`）：不同客户认可证据各取哪一天作为认可日，并归入哪个收入期间？ 推荐：按 ADR-0047 逐证据类型列日期依据，由财务确认，不能由技术观察时间代替。
26. **审查期**（`review_period`，B 类改归 C，`delivery.acceptance`）：审查期从何日起算，修改后是否重新起算，期满无异议时如何处理？ 推荐：先按具体合同与 ADR-0047 核对，由合同负责方确认，不统一推定全部合同。
27. **期满记录**（`expiry_record`，B 类改归 C，`delivery.acceptance`）：审查期届满是否单独留记录，记录应包含哪些起算参数与证明？ 推荐：按原提议保存计算依据，视为同意的最终判定仍交合同与收入侧确认。
28. **客户认可证据类型**（`acceptance_evidence_type`，B 类改归 C，`delivery.acceptance`）：客户认可的八类证据及历史迁入是否作为同一类型目录管理？ 推荐：按 ADR-0047 保留候选目录与须附证据规则，逐类由业务及财务确认。
29. **往来单位银行账户已变更**（`counterparty_bank_account_changed`，B 类改归 C，`masterdata.counterparty`）：往来单位收款账户变更后，是否立即拦截向旧账户付款？ 推荐：按原草案保留账户变更事实与旧账户拦截候选，交资金与付款负责方确认。
30. **投标已中标**（`bid_won`，B 类改归 C，`presales.opportunity`）：收到中标通知时，是否就认定投标已中标并将商机记为赢单？ 推荐：区分中标通知与签约事实，具体赢单时点交售前业务确认。
31. **投标未中标**（`bid_lost`，B 类改归 C，`presales.opportunity`）：投标何时确认未中标，退还保证金由这个事实触发是否合适？ 推荐：以可核实的未中标结果为依据，保证金退还条件另由合同及财务确认。
32. **商机已输单**（`opportunity_lost`，B 类改归 C，`presales.opportunity`）：商机输单与我方主动放弃如何区分，“丢单”是否只作口语别名？ 推荐：保留对手赢与我方退出的区别，业务确认措辞，售前投入转费用另经财务确认。
33. **商机已放弃**（`opportunity_abandoned`，B 类改归 C，`presales.opportunity`）：我方不参与或中途退出商机时，是否统一记录为商机已放弃？ 推荐：保留主动退出与输单分开的候选，费用处理时点交财务确认。
34. **协议已终止**（`agreement_terminated`，B 类改归 C，`contract.agreement`）：协议提前终止与自然到期如何区分，何时停止开票并进入清算？ 推荐：按合同终止事实区分到期，开票和清算条件由合同与财务共同确认。
35. **协议新基线已生效**（`agreement_baseline_activated`，B 类改归 C，`contract.agreement`）：协议新基线何时生效，是否同时更新付款条件与履约义务？ 推荐：基线版本与生效依据保留可追溯，业务与财务分别确认受影响事项。
36. **协议项目关联已登记**（`agreement_project_linked`，B 类改归 C，`contract.agreement`）：协议与项目关联是否只记录生效期间，并供成本归属使用？ 推荐：保留关联有效期候选，交合同、交付与成本负责方确认用途和粒度。
37. **项目已建立**（`project_established`，B 类改归 C，`delivery.execution`）：项目建立后，是否触发售前投入转入项目成本？ 推荐：建立事实与成本转入条件分别确认，不用旧表单叫法决定业务含义。
38. **项目已结项**（`project_closed`，B 类改归 C，`delivery.execution`）：项目满足哪些收尾条件才算已结项，何时进行结项清算？ 推荐：按交付收尾检查确认结项，并与客户竣工结算分开，财务确认清算条件。
39. **项目已终止**（`project_terminated`，B 类改归 C，`delivery.execution`）：项目提前终止是否必须已有成本，若尚无成本如何记录？ 推荐：终止按实际业务决定，成本有无另列，交交付与财务一起确认。
40. **项目已取消**（`project_cancelled`，B 类改归 C，`delivery.execution`）：项目取消与执行中终止是否一定以尚无成本和已有成本为界？ 推荐：建议按业务阶段及决定原因区分，成本作为独立事实，由业务与财务确认。
41. **快照日**（`snapshot_date`，B 类改归 C，`organization.workforce`）：组织快照日是同步开始日，还是员工任职等事实的实际生效日？ 推荐：技术观察日与人事声明生效日分开，版本生效依据交人事确认。
42. **在职判定**（`employment_verdict`，B 类改归 C，`organization.workforce`）：快照未列出或不可用时，系统是否仍将员工判为离职？ 推荐：保留四种判定结果，未知不冒充离职，交人事与身份负责方确认。
43. **登录名**（`workforce__login_name`，B 类改归 C，`platform.integration`）：组织源中没有手机号登录名，是否足以认定员工离职？ 推荐：建议只记登录标识缺失，人事离职应有明确依据，交人事确认后修订。
44. **员工任职快照**（`workforce__staff_employee`，B 类改归 C，`organization.workforce`）：同步比较以员工连同任职为一条记录，是否会漏掉多任职或任职变更？ 推荐：先确认员工与任职的业务粒度，再确定快照结构。
45. **关系**（`workforce__relationship`，B 类改归 C，`platform.integration`）：来源中的部门负责人和区域应收负责人关系分别代表什么职责？ 推荐：按职责分别确认语义，来源类型 ID 只在 ACL 映射，不据配置直接批准业务角色。
46. **离职观察时间**（`separation_observed_at`，B 类改归 C，`platform.integration`）：首次观察到离职的提交时刻是否只表示系统观察，而不代表人事离职日？ 推荐：保留原提议的两者区分及“离职日期/separation_date”禁用写法，由人事确认。
47. **法人归属对象**（`attribution_subject`，B 类改归 C，`organization.workforce`）：组织单元或员工在同一时点是否只能归属一个法人，按什么日期判断？ 推荐：按 ADR-0033 的单一归属候选，交人事与财务确认有效期。
48. **证据**（`acceptance_evidence`，漏登31 英文待确认，`delivery.acceptance`）：宽口径“证据”已定，英文是否应采用 Evidence 或其他名称，而不限定为 AcceptanceEvidence？ 推荐：保留已定中文及宽口径定义；候选英文待业务确认，不能随命名设计转正。

另有技术类维持提议12项，原因与退出条件见 [B 类逐项复核](B_CLASS_DECISIONS.md)；不把等改名或缺出处的问题当作已批准业务结论。
