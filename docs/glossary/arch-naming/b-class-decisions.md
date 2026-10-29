# NAM-1017-GLO-07：返工第 2 轮逐条复核

日期：2026-10-17。Codex 起草／实现；本地未提交、未推送。**卡内修复已写回；交付门禁未通过，本卡不算完成。**

## 原话

2026-10-17，NAM-1017-GLO-07（返工第 2 轮），用户要求：“最重要的原则：设计方只能定‘技术/结构/枚举/ACL 技术概念’的英文名、kind、bc、scope；任何业务、会计、合同、法律、经营事件含义的用语都不得由设计方批准，一律保持 proposed 并进 C 类问题”。用户明确要求逐条处理复审，其中低7原文：“project_established 的定义里有‘老系统’；org_change_set 的定义用了 sync_run 已禁用的‘同步运行’：改掉。”本转录只支持这两条定义去掉旧名依赖，不代表任何新业务用语获批。

## 本轮结果

原97条转正记录逐条复核：**保留技术转正49、维持提议7、改归C41**。其余8条：原合并1撤销并维持提议，原维持4仍维持，原C3仍为C；B105合计 **转正49、维持提议12、改归C44、合并0、弃用0**，各组互斥。

C类总数48：原C3、B改归C44、宽口径“证据”英文待确认1，完整白话问题与推荐见 [C_CLASS_FOR_N7.md](c-class-for-n7.md)。业务、会计、合同、法律、经营事件词不因 kind 是 state/value_object 或已有ADR提及而由设计方批准。

词表文件从本轮开工882降至833（831 approved、2 deprecated）；相对B处理前785，为撤回“证据”1、技术新增49，净增48。`acceptance_evidence` 整条移到 proposed，保留用户已定的中文“证据”与宽口径定义，仅英文待确认，不产生获批英文。提议目录314→266：删除49、增加1；相对开工216恢复49并增加1。非错误码159→111；错误码155不变。B类精确删除49，相对GLO-06前累计删除73（A删除24+B删除49）；不把42条兼容弃用提议计成已删除。

删除分布：architecture-acl 1、data-migration 19、kernel 3、organization_workforce 4、platform-identity 9、platform-integration 13，其余本轮B删除0。基线维持102（相对B前103只删除 system_actor.used_by）；本轮未增加基线、未改计数或忽略项。

## 写回机制与逐字段边界

- B结论逐条登记 semantic_class；业务类必须 keep_proposed + escalate_to:C；所有转正技术条目都声明 technical:true 及具体技术理由（包含 aggregate/entity/business_rule/role）。缺理由、缺实质原始出处时失败。
- 转正时与审计中不可变的原提议完整快照核对。zh、definition、bc、scope、英文、snake、kind及其他非元数据字段若变化，必须有精确 from/to、原因与已核实来源；中文、定义只接受用户来源。修改目标结果而不补字段说明会失败。定义仅改 org_change_set 的禁用旧称；所有其他转正条目的中文、定义、bc、scope与原提议一致。
- project_established 保留提议，仅按上方本轮指令删除依赖旧表单叫法的定义句；原审计快照不改。离职观察时间保留提议与原禁用项，不把“离职日期/separation_date”改成可用别名。
- release、supersede、read_only_verification 中文恢复“放行／取代／只读核实”；撤销 org_sync 合并，恢复原提议“同步”；sync_run恢复B前全部字段。四条未经用户单独确认的中文事件改写撤回，恢复 ECL 预警、质量保证金到期返还、收入冲减、规则执行，英文裁定仍保留。
- 确认字符串按真实来源写：ADR-0053、CODING_STANDARD 或 NAMING_STANDARD，不再把后两者冒写成ADR决定。文件读取指定UTF-8。recon_pending__role_deleted 的 owner改为 one-coordinator，until原样保留。
- --verify 是显式全文件审计哈希核对；无关内容变化时可失败。常规构建只核引用内容。未接入 build_all 的 SELF_TESTED，也未改CI；建议 one-quality 另卡决定接入 --self-test 与内容核对，避免把全文件审计模式当普遍门禁。

## 改动的断言／测试及理由

原72个负向用例及原 self_test、sample_term 函数保留。原 test_sources 的整文件SHA断言仍仅在 verify_hashes=True 执行（上一轮已改，本轮单列）：日常核验引用内容，无关补记不应失败；显式审计仍能捕捉整文件变化。所有旧错误用例继续运行，未删除或放宽断言。

按中4撤掉“最终 terms.yaml 的约117个目标必须永远等于旧哈希”的运行期断言；替换为在真实 build_terms 的RECON写回前截取输入、从当前决定生成独立期望，再比对A66/漏登60在写回时点的序列化字节。历史兼容性另做一次性比对，结果见验证部分；后续合法词条修改不被旧最终快照误阻断。新增按zh/definition/bc/scope/en/snake/kind逐字段篡改、缺technical声明、缺理由／来源、设计方擅改中文／定义、错误字段终值与按真实规范来源确认等回归。

## 复审17项逐条处理

- 阻断1：生成依赖纳入明确交付清单并做临时目录导出式验证；不以含未跟踪文件的工作区生成冒充干净导出。用户要求不提交，本轮不能声称现有 git archive HEAD 已含新增文件，负责会话须将清单文件纳入后续提交。
- 阻断2：撤销擅改中文、撤销org_sync合并；保留原提议词汇，check:copy通过，未改读取器或allow list。
- 高1：97条全量重判，业务一律回提议并交C，技术转正49逐条声明边界。
- 高2：离职观察时间退回提议，恢复原forbidden，与expected_separation_date的既有决定一致。
- 高3：转正与原提议字段强核对，逐字段原因与来源缺失即失败，附篡改负向自测。
- 中1：三条中文恢复；ui_released_state/trigger_mode/held/release/org_sync全部保持提议，不以命名ADR当实质出处。
- 中2：held/release/workforce__sync_diff/trial_commit/migration_batch_status__effective全部保持提议，分别保留BC或scope待复核原因；未固化不一致归属。
- 中3：未改build_all/CI；报告明确--verify的审计边界，建议交one-quality决定接入方式。
- 中4：兼容自测从最终词表固定哈希改为写回时点的当前决定期望，不阻碍后续合法词条修改。
- 中5：role_deleted兼容提议owner改为one-coordinator，期限未变。
- 低1：现有“第13期”可把单字“第／期”加入词汇集合的缺口仍在；建议one-quality另卡补“第X客户／甲期乙”等负向例、让单字只能走明确连接词策略；按本卡限制不改copy-glossary.mjs。
- 低2：证据note的“。；”双标点修好，整条移回提议，宽口径内容保持。
- 低3：原test_sources哈希断言限显式审计模式的改动与理由已在上节单列。
- 低4：effective/current/held/undetermined/relationship/ticket/recipient等裸通用snake与“判定不了／当前／关系”交后续命名复核；本轮不强改。其中held和relationship已留提议，其余技术词按原定义登记并在下表标边界。
- 低5：读取文件明确UTF-8，confirmation按来源写，另有规范来源的正向断言。
- 低6：GL B2-B09、NM4-09的原始答复确有defaulted:true；历史被作为用户批次裁定核对，此字段本身只证明按默认推荐提交，不证明逐题主动点选。本轮保留该事实和原业务答复来源，需负责会话确认平台语义；上一轮将ECL中文展开超出原话，本轮撤回展开及自定中文事件名，恢复ECL预警。
- 低7：project_established删除依赖旧表单叫法的句子且仍proposed；org_change_set把禁用“同步运行ID”改为“同步记录ID”，逐字段引用上方本轮指令。

## B105逐项复核

原始完整提议、目标完整字段与逐字段变更均见 [decisions-recon-b.yaml](../decisions-recon-b.yaml)。下列编号与原报告105条一致；本表“原转正”共97条，其他8条也逐一列出。

1. `org_change_set` · 组织变更集（原转正）：**转正**；英文 `OrgChangeSet`，kind=value_object，scope=domain，bc=organization.workforce。只登记迁入解析、暂存、拒收或对照的技术结构与枚举；不改变业务数据范围、财务签字规则和过账口径。 原据：ADR-0039。
2. `apply_org_change_set` · 应用组织变更（原转正）：**改归C，保持proposed**。涉及人事生效、在职或法人归属含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ADR-0039。
3. `separation_hold` · 待放行离职（原转正）：**改归C，保持proposed**。涉及人事生效、在职或法人归属含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ADR-0028；ADR-0039。
4. `personal_information` · 个人信息（原转正）：**改归C，保持proposed**。涉及法律定义及敏感分级，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：个人信息保护法第四条；ADR-0043。
5. `sensitive_personal_information` · 敏感个人信息（原转正）：**改归C，保持proposed**。涉及法律定义及敏感分级，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：个人信息保护法第二十八条；ADR-0043。
6. `migration_batch` · 迁入批次（原转正）：**转正**；英文 `MigrationBatch`，kind=aggregate，scope=acl，bc=platform.integration。只登记迁入解析、暂存、拒收或对照的技术结构与枚举；不改变业务数据范围、财务签字规则和过账口径。 原据：用户 2026-10-15 批次术语统一；ADR-0036 历史数据迁入。
7. `migration_reject` · 迁入拒收（原转正）：**转正**；英文 `MigrationReject`，kind=entity，scope=acl，bc=platform.integration。只登记迁入解析、暂存、拒收或对照的技术结构与枚举；不改变业务数据范围、财务签字规则和过账口径。 原据：ADR-0036 历史数据迁入。
8. `migration_reject_reason__unmappable_value` · 取值无法映射（原转正）：**转正**；英文 `UnmappableValue`，kind=state，scope=acl，bc=platform.integration。只登记迁入解析、暂存、拒收或对照的技术结构与枚举；不改变业务数据范围、财务签字规则和过账口径。 原据：ADR-0036 历史数据迁入。
9. `migration_reject_reason__required_missing` · 必填缺失（原转正）：**转正**；英文 `RequiredMissing`，kind=state，scope=acl，bc=platform.integration。只登记迁入解析、暂存、拒收或对照的技术结构与枚举；不改变业务数据范围、财务签字规则和过账口径。 原据：ADR-0036 历史数据迁入。
10. `migration_reject_reason__number_conflict` · 编号冲突（原转正）：**转正**；英文 `NumberConflict`，kind=state，scope=acl，bc=platform.integration。只登记迁入解析、暂存、拒收或对照的技术结构与枚举；不改变业务数据范围、财务签字规则和过账口径。 原据：ADR-0036 历史数据迁入。
11. `migration_reject_reason__original_missing` · 原行缺失（原转正）：**转正**；英文 `OriginalMissing`，kind=state，scope=acl，bc=platform.integration。只登记迁入解析、暂存、拒收或对照的技术结构与枚举；不改变业务数据范围、财务签字规则和过账口径。 原据：ADR-0036 历史数据迁入。
12. `migration_reject_reason__legal_entity_out_of_scope` · 法人不在范围（原转正）：**转正**；英文 `LegalEntityOutOfScope`，kind=state，scope=acl，bc=platform.integration。只登记迁入解析、暂存、拒收或对照的技术结构与枚举；不改变业务数据范围、财务签字规则和过账口径。 原据：ADR-0036 历史数据迁入。
13. `migration_reject_reason__trial_commit_failed` · 试提交失败（原转正）：**转正**；英文 `TrialCommitFailed`，kind=state，scope=acl，bc=platform.integration。只登记迁入解析、暂存、拒收或对照的技术结构与枚举；不改变业务数据范围、财务签字规则和过账口径。 原据：ADR-0036 历史数据迁入。
14. `migration_reject_disposition__rerun_after_fix` · 修正后重跑（原转正）：**转正**；英文 `RerunAfterFix`，kind=state，scope=acl，bc=platform.integration。只登记迁入解析、暂存、拒收或对照的技术结构与枚举；不改变业务数据范围、财务签字规则和过账口径。 原据：ADR-0036 历史数据迁入。
15. `migration_reject_disposition__excluded` · 确认不迁（原转正）：**转正**；英文 `Excluded`，kind=state，scope=acl，bc=platform.integration。只登记迁入解析、暂存、拒收或对照的技术结构与枚举；不改变业务数据范围、财务签字规则和过账口径。 原据：ADR-0036 历史数据迁入。
16. `migration_period_reservation` · 迁入占用（原转正）：**改归C，保持proposed**。涉及会计期间、分录或过账口径，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ADR-0036 历史数据迁入。
17. `migration_marker` · 迁入标记（原转正）：**改归C，保持proposed**。涉及会计期间、分录或过账口径，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ADR-0036 历史数据迁入。
18. `migration_posting_channel` · 迁入过账通道（原转正）：**改归C，保持proposed**。涉及会计期间、分录或过账口径，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ADR-0036 历史数据迁入。
19. `migration_staging` · 迁入暂存（原转正）：**转正**；英文 `MigrationStaging`，kind=entity，scope=acl，bc=platform.integration。只登记迁入解析、暂存、拒收或对照的技术结构与枚举；不改变业务数据范围、财务签字规则和过账口径。 原据：ADR-0036 历史数据迁入。
20. `trial_commit` · 试提交（原转正）：**维持提议**。归属尚未厘清：trial_commit 在 platform.migration，同组迁入结构在 platform.integration；保留提议等迁入编排归属复核。 原据：ADR-0036 历史数据迁入。
21. `migration_batch_status__staging` · 暂存中（原转正）：**转正**；英文 `Staging`，kind=state，scope=acl，bc=platform.integration。只登记迁入解析、暂存、拒收或对照的技术结构与枚举；不改变业务数据范围、财务签字规则和过账口径。 原据：ADR-0036 历史数据迁入。
22. `migration_batch_status__awaiting_tie_out` · 待核对（原转正）：**转正**；英文 `AwaitingTieOut`，kind=state，scope=acl，bc=platform.integration。只登记迁入解析、暂存、拒收或对照的技术结构与枚举；不改变业务数据范围、财务签字规则和过账口径。 原据：ADR-0036 历史数据迁入。
23. `migration_batch_status__committing` · 提交中（原转正）：**转正**；英文 `Committing`，kind=state，scope=acl，bc=platform.integration。只登记迁入解析、暂存、拒收或对照的技术结构与枚举；不改变业务数据范围、财务签字规则和过账口径。 原据：ADR-0036 历史数据迁入。
24. `migration_batch_status__effective` · 已生效（原转正）：**维持提议**。原 scope=domain 与同组迁入状态的 acl 不一致；本轮不固化，待命名归属复核。 原据：ADR-0036 历史数据迁入。
25. `migration_batch_status__reversed` · 已冲销（原转正）：**转正**；英文 `Reversed`，kind=state，scope=acl，bc=platform.integration。只登记迁入解析、暂存、拒收或对照的技术结构与枚举；不改变业务数据范围、财务签字规则和过账口径。 原据：ADR-0036 历史数据迁入。
26. `migration_batch_status__abandoned` · 已放弃（原转正）：**转正**；英文 `Abandoned`，kind=state，scope=acl，bc=platform.integration。只登记迁入解析、暂存、拒收或对照的技术结构与枚举；不改变业务数据范围、财务签字规则和过账口径。 原据：ADR-0036 历史数据迁入。
27. `source_reference_status` · 对照状态（原转正）：**转正**；英文 `SourceReferenceStatus`，kind=state，scope=acl，bc=platform.integration。只登记迁入解析、暂存、拒收或对照的技术结构与枚举；不改变业务数据范围、财务签字规则和过账口径。 原据：ADR-0036 历史数据迁入。
28. `source_reference_status__current` · 当前（原转正）：**转正**；英文 `Current`，kind=state，scope=acl，bc=platform.integration。只登记迁入解析、暂存、拒收或对照的技术结构与枚举；不改变业务数据范围、财务签字规则和过账口径。 原据：ADR-0036 历史数据迁入。
29. `source_row_digest` · 来源行摘要（原转正）：**转正**；英文 `SourceRowDigest`，kind=value_object，scope=acl，bc=platform.integration。只登记迁入解析、暂存、拒收或对照的技术结构与枚举；不改变业务数据范围、财务签字规则和过账口径。 原据：ADR-0036 历史数据迁入。
30. `own_participation_record` · 我方参与记录（原转正）：**改归C，保持proposed**。涉及业务对象、合同权利义务、会计或经营事件含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-33。
31. `project_wbs` · 项目 WBS（原转正）：**改归C，保持proposed**。涉及业务对象、合同权利义务、会计或经营事件含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-20；PMBOK 工作分解结构（WBS）；GB/T 50326。
32. `billing_revenue_limit` · 开票与收入额度（原转正）：**改归C，保持proposed**。涉及业务对象、合同权利义务、会计或经营事件含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-44；企业会计准则第 14 号——收入（履约义务）。
33. `acceptance_evidence_type_admission` · 客户认可证据类型开放配置（原转正）：**改归C，保持proposed**。涉及业务对象、合同权利义务、会计或经营事件含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-92；ADR-0047 第 4 条。
34. `invoice_posting_reservation` · 发票入账占用（原转正）：**改归C，保持proposed**。涉及业务对象、合同权利义务、会计或经营事件含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-89。
35. `receivables_collection__cash_application_reservation` · 核销占用（原转正）：**改归C，保持proposed**。涉及业务对象、合同权利义务、会计或经营事件含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-49、AGG-50。
36. `payables_payment__cash_application_reservation` · 核销占用（原转正）：**改归C，保持proposed**。涉及业务对象、合同权利义务、会计或经营事件含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-52、AGG-53。
37. `supplier_agreement_performance_limit` · 供方协议履约额度（原转正）：**改归C，保持proposed**。涉及业务对象、合同权利义务、会计或经营事件含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-58。
38. `subcontract_change_activated` · 分包变更已生效（原转正）：**改归C，保持proposed**。涉及业务对象、合同权利义务、会计或经营事件含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-56。
39. `labor_cost_allocation_result` · 人工成本分配结果（原转正）：**改归C，保持proposed**。涉及业务对象、合同权利义务、会计或经营事件含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-41；企业会计准则第 1 号——存货（成本分配）。
40. `cash_accrual_account_mapping` · 收付权责科目映射表（原转正）：**改归C，保持proposed**。涉及业务对象、合同权利义务、会计或经营事件含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-85；ADR-0022 第 2 条；企业会计准则——基本准则（权责发生制）。
41. `control_account_authorization` · 控制科目授权表（原转正）：**改归C，保持proposed**。涉及业务对象、合同权利义务、会计或经营事件含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-94；ADR-0022 第 3 条。
42. `no_objection_declared` · 无异议已声明（原转正）：**改归C，保持proposed**。涉及业务对象、合同权利义务、会计或经营事件含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-27。
43. `design_change_claim_status` · 设计变更主张状态（原其他结论）：**改归C，保持proposed**。涉及业务对象、合同权利义务、会计或经营事件含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-30。
44. `acceptance_date` · 认可日（原转正）：**改归C，保持proposed**。涉及业务对象、合同权利义务、会计或经营事件含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ADR-0047 第 2、3 条；企业会计准则第 14 号——收入。
45. `review_period` · 审查期（原转正）：**改归C，保持proposed**。涉及业务对象、合同权利义务、会计或经营事件含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ADR-0047 第 5 条；GF-2015-0209 第 8.1 条。
46. `expiry_record` · 期满记录（原转正）：**改归C，保持proposed**。涉及业务对象、合同权利义务、会计或经营事件含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ADR-0047 第 5 条。
47. `acceptance_evidence_type` · 客户认可证据类型（原转正）：**改归C，保持proposed**。涉及业务对象、合同权利义务、会计或经营事件含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ADR-0047 第 2 条。
48. `counterparty_bank_account_changed` · 往来单位银行账户已变更（原转正）：**改归C，保持proposed**。涉及业务对象、合同权利义务、会计或经营事件含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-01。
49. `bid_won` · 投标已中标（原转正）：**改归C，保持proposed**。涉及业务对象、合同权利义务、会计或经营事件含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-11。
50. `bid_lost` · 投标未中标（原转正）：**改归C，保持proposed**。涉及业务对象、合同权利义务、会计或经营事件含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-11。
51. `opportunity_lost` · 商机已输单（原转正）：**改归C，保持proposed**。涉及业务对象、合同权利义务、会计或经营事件含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-10。
52. `opportunity_abandoned` · 商机已放弃（原转正）：**改归C，保持proposed**。涉及业务对象、合同权利义务、会计或经营事件含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-10。
53. `agreement_terminated` · 协议已终止（原转正）：**改归C，保持proposed**。涉及业务对象、合同权利义务、会计或经营事件含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-13。
54. `agreement_baseline_activated` · 协议新基线已生效（原转正）：**改归C，保持proposed**。涉及业务对象、合同权利义务、会计或经营事件含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-13。
55. `agreement_project_linked` · 协议项目关联已登记（原转正）：**改归C，保持proposed**。涉及业务对象、合同权利义务、会计或经营事件含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-13。
56. `project_established` · 项目已建立（原转正）：**改归C，保持proposed**。涉及业务对象、合同权利义务、会计或经营事件含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-19。
57. `project_closed` · 项目已结项（原转正）：**改归C，保持proposed**。涉及业务对象、合同权利义务、会计或经营事件含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-19。
58. `project_terminated` · 项目已终止（原其他结论）：**改归C，保持proposed**。涉及业务对象、合同权利义务、会计或经营事件含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-19。
59. `project_cancelled` · 项目已取消（原其他结论）：**改归C，保持proposed**。涉及业务对象、合同权利义务、会计或经营事件含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-19。
60. `snapshot_date` · 快照日（原转正）：**改归C，保持proposed**。涉及人事生效、在职或法人归属含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：docs/architecture/events/event-analytics-fields.md。
61. `ui_released_state` · 已放行（原转正）：**维持提议**。原提议无实质出处；ADR-0053 只定命名规则，不能单独支持此状态转正。 原据：无实质出处。
62. `ui_external_system` · 外部系统（原其他结论）：**维持提议**。外部系统是系统概念，现行 kind 没有系统类；待 bcs/系统目录接入，保留原提议。 原据：无实质出处。
63. `trigger_mode` · 触发方式（原转正）：**维持提议**。原提议无实质出处，触发方式的值域未有本条可追溯依据，维持提议。 原据：无实质出处。
64. `held` · 被中止（原转正）：**维持提议**。原提议无实质出处且 bc 与 SyncRun 所在 organization.workforce 不一致，维持提议。 原据：无实质出处。
65. `release` · 放行（原转正）：**维持提议**。恢复原中文“放行”；原提议无实质出处且 bc 与 SyncRun 不一致，保留提议等待归属复核。 原据：无实质出处。
66. `org_sync` · 同步（原其他结论）：**维持提议**。撤销与 sync_run 的合并；原提议无实质出处、bc 待复核，且当前前端只读取 zh，别名不能替代“同步”。 原据：无实质出处。
67. `system_actor` · 系统操作主体（原转正）：**转正**；英文 `SystemActor`，kind=role，scope=kernel，bc=kernel。只登记系统作业身份、外部登录凭据、绑定、运行配置或投递/只读核实的 ACL 技术概念。 原据：ADR-0033 第 7 项第 1 条；phase1a/NEXT_BATCH_PLAN.md 7.2 C5。
68. `messaging_account` · 即时通讯账号（原转正）：**转正**；英文 `MessagingAccount`，kind=value_object，scope=kernel，bc=kernel。只登记系统作业身份、外部登录凭据、绑定、运行配置或投递/只读核实的 ACL 技术概念。 原据：ADR-0033 第 3 项第 6 条；phase1a/cards/R-1b-P3b.md 第 13 节。
69. `external_login_name` · 外部登录名（原转正）：**转正**；英文 `ExternalLoginName`，kind=value_object，scope=kernel，bc=kernel。只登记系统作业身份、外部登录凭据、绑定、运行配置或投递/只读核实的 ACL 技术概念。 原据：ADR-0033 第 3 项第 6 条；phase1a/cards/R-1b-P3b.md 第 13 节。
70. `employment_verdict` · 在职判定（原转正）：**改归C，保持proposed**。涉及人事生效、在职或法人归属含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ADR-0033 第 3 项第 7 条。
71. `login_lookup` · 登录查人（原其他结论）：**维持提议**。临时登录查询结构仍在使用，待 C1 完成外部身份迁移后再决定退役，保持提议。 原据：ADR-0033 第 4 项。
72. `workforce__sync_diff` · 同步差异（原转正）：**维持提议**。原 bc=platform.integration 与 SyncRun 的 organization.workforce 不一致；保持提议等归属复核。 原据：RULE-373。
73. `workforce__shrink_guard` · 缩水保护（原转正）：**转正**；英文 `ShrinkGuard`，kind=business_rule，scope=acl，bc=organization.workforce。只登记同步差异比较、数据隔离及异常中止的技术结构，不批准人事生效含义。 原据：RULE-358；RULE-366。
74. `workforce__held_run` · 被中止的同步（原转正）：**转正**；英文 `HeldRun`，kind=value_object，scope=acl，bc=organization.workforce。只登记同步差异比较、数据隔离及异常中止的技术结构，不批准人事生效含义。 原据：RULE-358。
75. `workforce__isolated_row` · 被隔离的行（原转正）：**转正**；英文 `IsolatedRow`，kind=value_object，scope=acl，bc=organization.workforce。只登记同步差异比较、数据隔离及异常中止的技术结构，不批准人事生效含义。 原据：RULE-365。
76. `workforce__broken_org_tree` · 断裂的组织树（原转正）：**转正**；英文 `BrokenOrgTree`，kind=value_object，scope=acl，bc=organization.workforce。只登记同步差异比较、数据隔离及异常中止的技术结构，不批准人事生效含义。 原据：RULE-354。
77. `workforce__login_name` · 登录名（原转正）：**改归C，保持proposed**。涉及人事生效、在职或法人归属含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：RULE-363。
78. `workforce__staff_employee` · 员工任职快照（原转正）：**改归C，保持proposed**。涉及人事生效、在职或法人归属含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：REIMAGINED_ARCHITECTURE.md §3.3。
79. `workforce__relationship` · 关系（原转正）：**改归C，保持proposed**。涉及人事生效、在职或法人归属含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：RULE-356。
80. `separation_observed_at` · 离职观察时间（原转正）：**改归C，保持proposed**。涉及人事生效、在职或法人归属含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：docs/business/rules/workforce-history-rules.md。
81. `attribution_subject` · 法人归属对象（原转正）：**改归C，保持proposed**。涉及人事生效、在职或法人归属含义，设计方无权批准；按原字段保持提议，交业务 Admin，涉及会计的另经财务确认。 原据：ADR-0033 第 7 项第 4 条（DA-5）。
82. `signing_key_code` · 签名密钥编码（原转正）：**转正**；英文 `SigningKeyCode`，kind=value_object，scope=acl，bc=platform.identity。只登记系统作业身份、外部登录凭据、绑定、运行配置或投递/只读核实的 ACL 技术概念。 原据：ADR-0033 影响面「ticket_issuers.key_id 改名」。
83. `ticket` · 票据（原转正）：**转正**；英文 `Ticket`，kind=entity，scope=acl，bc=platform.identity。只登记系统作业身份、外部登录凭据、绑定、运行配置或投递/只读核实的 ACL 技术概念。 原据：REIMAGINED_ARCHITECTURE.md §3.1 §3.2。
84. `ticket_issuer` · 票据签发方（原转正）：**转正**；英文 `TicketIssuer`，kind=aggregate，scope=acl，bc=platform.identity。只登记系统作业身份、外部登录凭据、绑定、运行配置或投递/只读核实的 ACL 技术概念。 原据：REIMAGINED_ARCHITECTURE.md §3.1 §3.2。
85. `external_identity` · 外部身份（原转正）：**转正**；英文 `ExternalIdentity`，kind=entity，scope=acl，bc=platform.identity。只登记系统作业身份、外部登录凭据、绑定、运行配置或投递/只读核实的 ACL 技术概念。 原据：adr/ADR-0033.md。
86. `provider_kind` · 身份提供方类别（原转正）：**转正**；英文 `ProviderKind`，kind=state，scope=acl，bc=platform.identity。只登记系统作业身份、外部登录凭据、绑定、运行配置或投递/只读核实的 ACL 技术概念。 原据：adr/ADR-0033.md。
87. `external_subject` · 外部主体标识（原转正）：**转正**；英文 `ExternalSubject`，kind=value_object，scope=acl，bc=platform.identity。只登记系统作业身份、外部登录凭据、绑定、运行配置或投递/只读核实的 ACL 技术概念。 原据：adr/ADR-0033.md。
88. `single_operator_mode` · 单人运维模式（原转正）：**转正**；英文 `SingleOperatorMode`，kind=state，scope=domain，bc=platform.identity。只登记系统作业身份、外部登录凭据、绑定、运行配置或投递/只读核实的 ACL 技术概念。 原据：docs/adr/ADR-0030.md。
89. `single_operator_relaxation` · 单人运维放宽项（原转正）：**转正**；英文 `SingleOperatorRelaxation`，kind=state，scope=domain，bc=platform.identity。只登记系统作业身份、外部登录凭据、绑定、运行配置或投递/只读核实的 ACL 技术概念。 原据：docs/adr/ADR-0030.md。
90. `super_admin` · 超级管理员（原转正）：**转正**；英文 `SuperAdmin`，kind=value_object，scope=domain，bc=platform.identity。只登记系统作业身份、外部登录凭据、绑定、运行配置或投递/只读核实的 ACL 技术概念。 原据：docs/adr/ADR-0030.md。
91. `outbound_delivery` · 外发投递（原转正）：**转正**；英文 `OutboundDelivery`，kind=aggregate，scope=acl，bc=platform.integration。只登记系统作业身份、外部登录凭据、绑定、运行配置或投递/只读核实的 ACL 技术概念。 原据：REIMAGINED_ARCHITECTURE.md §3.7 §3.9。
92. `recipient` · 收件人（原转正）：**转正**；英文 `Recipient`，kind=role，scope=acl，bc=platform.integration。只登记系统作业身份、外部登录凭据、绑定、运行配置或投递/只读核实的 ACL 技术概念。 原据：SECURITY_INVARIANTS.md I-6。
93. `source_reference` · 来源对照（原转正）：**转正**；英文 `SourceReference`，kind=aggregate，scope=acl，bc=platform.integration。只登记系统作业身份、外部登录凭据、绑定、运行配置或投递/只读核实的 ACL 技术概念。 原据：adr/ADR-0033.md。
94. `source_system` · 来源系统（原转正）：**转正**；英文 `SourceSystem`，kind=state，scope=acl，bc=platform.integration。只登记系统作业身份、外部登录凭据、绑定、运行配置或投递/只读核实的 ACL 技术概念。 原据：adr/ADR-0033.md。
95. `source_object_kind` · 来源对象类别（原转正）：**转正**；英文 `SourceObjectKind`，kind=state，scope=acl，bc=platform.integration。只登记系统作业身份、外部登录凭据、绑定、运行配置或投递/只读核实的 ACL 技术概念。 原据：adr/ADR-0033.md。
96. `rejected_state` · 已拒绝（原转正）：**转正**；英文 `RejectedState`，kind=state，scope=acl，bc=platform.integration。只登记系统作业身份、外部登录凭据、绑定、运行配置或投递/只读核实的 ACL 技术概念。 原据：adr/ADR-0033.md。
97. `registration` · 登记（原其他结论）：**维持提议**。代码仍使用 Registration；先由 platform.integration 完成 N7 改名，再审 RegisterSourceReference。 原据：adr/ADR-0033.md。
98. `supersede` · 取代（原转正）：**转正**；英文 `SupersedeSourceReference`，kind=command，scope=acl，bc=platform.integration。只登记系统作业身份、外部登录凭据、绑定、运行配置或投递/只读核实的 ACL 技术概念。 原据：adr/ADR-0033.md。
99. `evidence` · 取证（原其他结论）：**维持提议**。只读取证是治理活动，现行值对象类别不适合；等待治理动作目录，保持提议。 原据：adr/ADR-0037.md。
100. `read_only_verification` · 只读核实（原转正）：**转正**；英文 `VerifyReadOnlyConnection`，kind=command，scope=acl，bc=platform.integration。只登记系统作业身份、外部登录凭据、绑定、运行配置或投递/只读核实的 ACL 技术概念。 原据：adr/ADR-0037.md；docs/ops/predeploy-checklist.md C-14。
101. `read_only_verification_result` · 只读核实结果（原转正）：**转正**；英文 `ReadOnlyVerificationResult`，kind=state，scope=acl，bc=platform.integration。只登记系统作业身份、外部登录凭据、绑定、运行配置或投递/只读核实的 ACL 技术概念。 原据：adr/ADR-0037.md。
102. `read_only_verification_result__passed` · 核实通过（原转正）：**转正**；英文 `Passed`，kind=state，scope=acl，bc=platform.integration。只登记系统作业身份、外部登录凭据、绑定、运行配置或投递/只读核实的 ACL 技术概念。 原据：adr/ADR-0037.md。
103. `read_only_verification_result__violated` · 确认违规（原转正）：**转正**；英文 `Violated`，kind=state，scope=acl，bc=platform.integration。只登记系统作业身份、外部登录凭据、绑定、运行配置或投递/只读核实的 ACL 技术概念。 原据：adr/ADR-0037.md。
104. `read_only_verification_result__undetermined` · 判定不了（原转正）：**转正**；英文 `Undetermined`，kind=state，scope=acl，bc=platform.integration。只登记系统作业身份、外部登录凭据、绑定、运行配置或投递/只读核实的 ACL 技术概念。 原据：adr/ADR-0037.md。
105. `overridden_grant` · 被拒绝覆盖的授权（原转正）：**转正**；英文 `OverriddenGrant`，kind=value_object，scope=acl，bc=platform.integration。只登记系统作业身份、外部登录凭据、绑定、运行配置或投递/只读核实的 ACL 技术概念。 原据：adr/ADR-0037.md；SQL Server 权限层级中 DENY 优先于 GRANT（Microsoft 文档 Permissions Hierarchy）。

## 导出文件归属

这些文档是来源材料，不因复制而变成新的业务批准。交付时以新增文件清单为准，不依赖相邻worktree。

- `reconcile-decided.json`：../modest-torvalds-293503/docs/glossary/arch-naming/reconcile-decided.json；输入SHA-256 `cb93f7f6dd1ab7d71a663f6490bf3b6d52e808016c9349b749bd79c45fd30431`。
- `RECONCILE_DECIDED.md`：../modest-torvalds-293503/docs/glossary/arch-naming/reconcile-decided.md；输入SHA-256 `fa0501093d58c3811dac882efb1870817479153c3e6941729743e55d37f2616b`。
- `RECON_WRITEBACK.md`：开工已有未跟踪输入；指定上游无此文件，本轮保留原文；输入SHA-256 `27ee5006033deb55b813ce3f24f517a3143218e0e223b831ad8f8677ff428617`。
- `N3_DECISIONS.md`：../modest-torvalds-293503/docs/glossary/arch-naming/n3-decisions.md；输入SHA-256 `105b4a83f357d146c4502a5219a1839e53fe965a3af0e715e990d3aafda856cc`。
- `N4_VERBS_STATES.md`：../modest-torvalds-293503/docs/glossary/arch-naming/n4-verbs-states.md；输入SHA-256 `7dbdb5648652a30c2279f05d257b8e97ed77aec4e160fe94afeb5c770883b47b`。
- `N9_WRITEBACK.md`：../modest-torvalds-293503/docs/glossary/arch-naming/n9-writeback.md；输入SHA-256 `94e05ec07ab3da07f32e875ad7c42ce63ba6fd84c90a38130648e5287066423f`。
- `NAMING_ATOMS_REGISTRATION_PLANS.md`：../modest-torvalds-293503/docs/glossary/arch-naming/naming-atoms-registration-plans.md；输入SHA-256 `3dcd42ba6c1acc2ad1c03d121f0f88e80cda9a00d0f83f07d256b00e608a8462`。
- `NAMING_ATOMS_COVERAGE.md`：../modest-torvalds-293503/docs/glossary/arch-naming/naming-atoms-coverage.md；输入SHA-256 `ef9ed80a6f47d202bad05cbe6ddb24ee254899941b3e6c91b2fd0d101400f16e`。
- `NAMING_ATOMS_PREP.md`：../modest-torvalds-293503/docs/glossary/arch-naming/naming-atoms-prep.md；输入SHA-256 `a408af22d2d299d33a56d2fb3fe56f8a254148ff1d039af3aaadfe297961448d`。
- `NAMING_ATOMS_DRAFT.md`：../modest-torvalds-293503/docs/glossary/arch-naming/naming-atoms-draft.md；输入SHA-256 `8ca19fa558dde9be19cd7f894aa510824e703208f99aa5e8e71b9a24fae11f8f`。

- `N4_COVERAGE.md`：从指定相邻worktree复制的引用闭包；SHA-256 `ba9c4c7c1153d77d2edff4a6befeb3fe5e1e237007ffa6eab5b269ed38479e50`。
- `N5_NOUN_JUDGMENT.md`：从指定相邻worktree复制的引用闭包；SHA-256 `26de0311a82ec9a8995c8d4e66e888e96033c88b6df525e90c73f5e9e985f865`。

N9原文中两处临时输入链接改为本分支已有ADR/CAPINFO路径，正文决定未改。RECONCILE_DECIDED.md指向卡外coverage脚本的链接属于历史盘点入口，本卡不复制卡外脚本；生成只读取已纳入的reconcile-decided.json。

## 已验证

- `build_terms.py` 两次退出0，逐字节一致：833条（831 approved、2 deprecated），SHA-256 `54e1cff7608a6115a62c44c403c996a9941bf2614c8e17bc486353f19bb5b796`。102项存量问题，基线外新增0。
- 两个入口 `apply_recon.py --self-test` 与 `apply_recon_selftest.py` 均退出0：原72个负向用例、新增82个负向用例，合计154；四种结论正向样例、无关内容变动可通过、写回时点对照均通过。`--verify` 核验原97个用户来源、3个设计来源及1个本轮指令转录。该转录只授权两个id的definition，不能用于改中文或批准业务含义。
- 一次性历史兼容证明：使用上轮保留的 `9800ecae` 原脚本、原决定与同一729条RECON前输入，旧/新 apply_operations 都生成785条，序列化字节完全一致。
- 机制完整SHA-256：`10e029ab84a5d8d9f554d8e70a4c2e8b51fc05c2bbb47ebc8de8f477b076276e`。A66保护60目标（仅role_deleted例外），字节SHA `0562a30065c14528e2781b86428040fa69f39f9efe2f4f8f03c598f10d1ed3ae`；漏登60保护56目标（role_deleted及acceptance_evidence例外），字节SHA `53e395cdf7694fe76cc830c26ca3cbb78386d3f2cf66940dd8088f2ef800b573`。当前既有785目标中，只有role_deleted的note变化，acceptance_evidence移回提议，其余784中783个目标内容相同。此为一次性证明，不把旧最终词表锁进后续自测。
- 干净导出式验证：`git ls-files -z` 的已跟踪文件，取当前工作区内容，加明确的13个新增文件，逐文件复制到 `/tmp/nam-1017-glo-07-r2/export`（3828个文件；不复制.git、.codex-inputs、node_modules、target、其他未列入的未跟踪文件）。在没有相邻worktree的该目录运行build_terms退出0，生成字节与本地完全相同。清单含每个文件SHA-256：`/tmp/nam-1017-glo-07-r2/export-manifest.json`；新增清单 `added-files.txt`。未用git stash、未创建提交或改Git索引。新增文件尚未git add，清单证明的是待收集交付集，不能声称当前 `git archive HEAD` 已包括它们。
- 卡上后端命令 `cd backend && ./mvnw -o test-compile surefire:test -Dtest=GlossaryTest`：5 tests，0 failure/error/skip。JAVA_HOME用已安装Java25，MAVEN_ARGS=-o；未跑可能起容器的test生命周期。precheck的词表相关4类共21项也通过。
- 按用户给的ln命令复用glo-pr已安装依赖后，当前工作区直接 `node scripts/check-copy-glossary.mjs`：95文件通过；precheck内check:copy再次通过。`test:scripts` 37项通过。没有修改copy-glossary.mjs或allow list。
- 在同一导出代码副本建立真实node_modules目录，内部仅链接本机已安装依赖，工作区包引用指向副本本身；完整前端verify的check:generated、typecheck、check:lint-config、lint、check:structure、check:copy、test:scripts、test均通过。仅build未完成，详见下节。
- 精确删除49个B源id；与B处理前文件逐项核对，其余提议及顶层字段不变，只有本轮明确允许的project_established定义修正和新增证据提议。README由build_proposed_index生成。原C3保持不变；全部B改归C条目均不在approved中。
- `git diff --check`通过；build_terms.py、build_all.py、前端切词脚本、CI、Java及验收测试均未改。gitleaks8.30.1全历史2943个提交无发现；29个交付文件另经gitleaks定向扫描，11.45MB、无发现（日志 `/tmp/nam-1017-glo-07-r2/gitleaks-delivery.log`）。


## 未运行与门禁失败

**未达到0 FAIL，不算完成。** 阻断2已解除；阻断1的本地交付文件集独立生成已通过，提交归属仍待负责会话收集13个新增文件。用户要求本轮不提交，因此当前HEAD的archive条件及提交后build_all --check未验证。

默认precheck首次为6 PASS、3 FAIL、9 SKIP：pnpm12在run前尝试自动核对/安装，遇到借用的只读node_modules符号链接而失败（全程--offline，没有下载）。已用进程级 `--config.verify-deps-before-run=false` 关闭此自动安装行为，check:copy/test:scripts随后通过。pnpm12的递归任务运行记录还拒绝符号链接的根node_modules，故在临时副本中使用真实目录复用已装依赖；不改仓库配置。

完整前端verify的build随后在Taro4.3.0原生依赖中出现 `system-configuration-0.5.1 ... Attempted to create a NULL object`，命令挂起，已中止；不能报构建成功。该错误在直接Taro构建路径复现。未改源码绕过原生依赖，也未申请放宽沙箱。

另运行仓库原有 `scripts/precheck.sh --pre-push`，默认origin/main基线不缩小：**7 PASS、1 FAIL、10 SKIP，退出1**。失败是扩大运行的无容器单元测试：1224 tests，4 failures、85 errors、19 skipped。逐份XML核对：4个断言失败都因Mockito MockMaker先初始化失败，79个error同为MockMaker初始化失败，4个Socket创建被拒，2个WireMock绑定端口被拒。未删断言或改测试；这些环境错误不能算SKIP或通过。该模式跳过完整前端verify，不以它掩盖上述Taro失败。

build_all --check按脚本规则因未提交而SKIP；迁移、系统操作主体目录、验收测试、数据库脚本、断言漂移、后端守护目录及SQL Server集成均因相应文件未改而SKIP；pre-push模式的全历史gitleaks及完整前端verify按该模式规则SKIP，其中gitleaks另有默认模式的全历史PASS。19个后端单测skip是测试运行自身结果，不冒充本卡已覆盖。所有SKIP原文见下。

未运行联网下载、容器、生产、业务端到端验收，未提交、不推送；所有环境失败仍保留在报告中。完整日志与复现材料在 `/tmp/nam-1017-glo-07-r2/`。

## precheck输出（仅去除行尾空格）

命令（进程级离线环境，不改仓库配置）：

```sh
PATH=/tmp/nam-1017-glo-07-r2/bin:/Users/yeliang/.pyenv/versions/3.12.13/bin:/Users/yeliang/.nvm/versions/node/v24.18.0/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
MAVEN_ARGS=-o COREPACK_ENABLE_NETWORK=0 npm_config_offline=true CI=true scripts/precheck.sh --pre-push
```

临时pnpm仅给真实已安装pnpm追加 `--offline --config.verify-deps-before-run=false`，并给子进程同样的离线/禁止自动安装环境变量；不替代命令、不改测试。

```text
precheck: base origin/main (ae11f603), HEAD 2ae359ba, 48 changed files (40 uncommitted)

SKIP  generated docs (build_all --check)     docs or backend/glossary/proposed has uncommitted changes: commit first, then run again (请先提交再运行)
PASS  legacy system name                     check_legacy_name: 无违规
SKIP  migrations immutable                   no migration, marker or Flyway setting changed
SKIP  system actors append-only              system-actors.txt and its script did not change
SKIP  acceptance guard                       no acceptance test changed
SKIP  database scripts aligned               no database script or migration changed
PASS  docs read by builds are registered     ok
SKIP  assertion drift (warning only)         no test file changed
PASS  glossary: frontend check:copy          copy glossary: 95 files, every Chinese text is built from registered terms and the allow l
PASS  glossary: frontend test:scripts        ℹ duration_ms 3187.702792
PASS  glossary: backend term tests             size (term round3_pending__size) -> com.forcitis.one.platform.integration.api.ArchivedOb
SKIP  guards: backend guard tests            no guard test or architecture list changed
PASS  release scripts self-test              deploy_gate.py self-test passed
PASS  shellcheck scripts/release             ok
SKIP  gitleaks (full history)                pre-push mode: CI scans the history
FAIL  pre-push: unit tests that start no container exit 1 — last lines:
      | [ERROR]   OssObjectStoreTest.<init>:35 » IllegalState Could not initialize plugin: interface org.mockito.plugins.MockMaker (alternate: null)
      | [ERROR]   ReadOnlyVerificationCommandTest.processEnvironmentWinsOverOtherSources:222->closedSqlServerUrl:61 » Socket Operation not permitted
      | [ERROR]   ReadOnlyVerificationCommandTest.silencedLogsCarryNoConnectionSettings:312->closedSqlServerUrl:61 » Socket Operation not permitted
      | [ERROR]   EventPublicationJobsTest.<init>:28 » IllegalState Could not initialize plugin: interface org.mockito.plugins.MockMaker (alternate: null)
      | [ERROR]   EventPublicationJobsTest.<init>:28 » IllegalState Could not initialize plugin: interface org.mockito.plugins.MockMaker (alternate: null)
      | [ERROR]   EventPublicationJobsTest.<init>:28 » IllegalState Could not initialize plugin: interface org.mockito.plugins.MockMaker (alternate: null)
      | [ERROR]   SeeyonTicketVerifierTest.start:30 » FatalStartup com.github.tomakehurst.wiremock.common.FatalStartupException: java.io.IOException: Failed to bind to /0.0.0.0:0
      | [ERROR]   HttpWecomClientTest.startWireMock:57 » FatalStartup com.github.tomakehurst.wiremock.common.FatalStartupException: java.io.IOException: Failed to bind to /0.0.0.0:0
      | [ERROR] Tests run: 1224, Failures: 4, Errors: 85, Skipped: 19
      | [ERROR] Failed to execute goal org.apache.maven.plugins:maven-surefire-plugin:3.5.6:test (default-cli) on project forcitis-one: There are test failures.
      | [ERROR]
      | [ERROR] See /Users/yeliang/Projects/FORCITIS/forcitis-one-psa/.claude/worktrees/codex-n12/backend/target/surefire-reports for the individual test results.
      | [ERROR] See dump files (if any exist) [date].dump, [date]-jvmRun[N].dump and [date].dumpstream.
      | [ERROR] -> [Help 1]
      | [ERROR]
      | [ERROR] To see the full stack trace of the errors, re-run Maven with the -e switch.
      | [ERROR] Re-run Maven using the -X switch to enable full debug logging.
      | [ERROR]
      | [ERROR] For more information about the errors and possible solutions, please read the following articles:
      | [ERROR] [Help 1] http://cwiki.apache.org/confluence/display/MAVEN/MojoFailureException
SKIP  frontend verify                        pre-push mode: CI runs the full verify
SKIP  SQL Server integration tests           relevant_change.py: nothing the *MssqlIT tests exercise changed

SUMMARY: 7 PASS, 1 FAIL, 10 SKIP (base origin/main) — not ready
```
