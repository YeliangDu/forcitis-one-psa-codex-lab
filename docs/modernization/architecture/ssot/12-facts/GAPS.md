# 业务事实候选抽取缺口

由 extract_facts.py 生成，勿手改。

三处候选仅原样并列，不合并、不裁定是否经营事实。

## 输入路径差异

派工卡写 `docs/modernization/r0/BI_SCOPE.md`；当前仓库实际输入为 `docs/modernization/BI_SCOPE.md` §3.2。脚本固定读取实际路径，不自动回退。

## none_with_note（20）

N11-1 仅以空串、「—」或「-」判缺失，带括注值不会命中该条件。以下列出全部 none_with_note；不等同于 N11-1 的一级业务对象检查范围，也不裁定事实含义。

- OBJ-01 往来单位：— （「往来单位已合并」事件）；in_system=true；`docs/modernization/architecture/ssot/10-objects/objects.yaml:17`
- OBJ-04 集团：— （汇总维度）；in_system=false；`docs/modernization/architecture/ssot/10-objects/objects.yaml:75`
- OBJ-06 合同范围条目：— （承诺比对基准）；in_system=true；`docs/modernization/architecture/ssot/10-objects/objects.yaml:111`
- OBJ-12 风险交付授权：— （为风险交付工时、金额度量提供维度）；in_system=true；`docs/modernization/architecture/ssot/10-objects/objects.yaml:224`
- OBJ-24 服务产品：— （分析维度）；in_system=true；`docs/modernization/architecture/ssot/10-objects/objects.yaml:447`
- OBJ-25 阶段类型：— （维度）；in_system=true；`docs/modernization/architecture/ssot/10-objects/objects.yaml:465`
- OBJ-26 标准成本费率表：— （计价参数）；in_system=true；`docs/modernization/architecture/ssot/10-objects/objects.yaml:484`
- OBJ-52 外部检测：— （不进我方成本）；in_system=true；`docs/modernization/architecture/ssot/10-objects/objects.yaml:979`
- OBJ-54 法人主体：— （各事实的法人维度）；in_system=true；`docs/modernization/architecture/ssot/10-objects/objects.yaml:1017`
- OBJ-55 组织单元：— （部门维度；区域将作为数据范围维度）；in_system=true；`docs/modernization/architecture/ssot/10-objects/objects.yaml:1035`
- OBJ-57 任职：— （费率与成本按工作日当日任职取值）；in_system=true；`docs/modernization/architecture/ssot/10-objects/objects.yaml:1072`
- OBJ-61 薪酬档案：— （算薪输入）；in_system=false；`docs/modernization/architecture/ssot/10-objects/objects.yaml:1145`
- OBJ-63 专项附加扣除信息：— （算薪输入）；in_system=false；`docs/modernization/architecture/ssot/10-objects/objects.yaml:1180`
- OBJ-69 假期额度：— （余额）；in_system=false；`docs/modernization/architecture/ssot/10-objects/objects.yaml:1291`
- OBJ-82 开票申请：— （发票开具才产生事实）；in_system=true；`docs/modernization/architecture/ssot/10-objects/objects.yaml:1518`
- OBJ-89 资金账户：— （期末余额）；in_system=true；`docs/modernization/architecture/ssot/10-objects/objects.yaml:1650`
- OBJ-96 账簿：— （期间余额）；in_system=true；`docs/modernization/architecture/ssot/10-objects/objects.yaml:1779`
- OBJ-99 报表行定义：— （某期报表是单据）；in_system=true；`docs/modernization/architecture/ssot/10-objects/objects.yaml:1837`
- OBJ-102 指标定义：— （指标值是派生度量）；in_system=false；`docs/modernization/architecture/ssot/10-objects/objects.yaml:1890`
- OBJ-110 知识条目：— （不直接产生度量事实）；in_system=true；`docs/modernization/architecture/ssot/10-objects/objects.yaml:2039`

## 事件 description 原字段缺失（42）

原字段缺失时输出 description: null，并在 missing_fields 中标记；不从 title、payload 或 x-event 补写。x-event 全映射原样保留，避免推断哪些扩展字段与事实有关。

- organization.attendance.WorkCalendarYearMaintained：`docs/modernization/events/attendance.yaml:5`
- organization.attendance.WorkCalendarLockedDaysChanged：`docs/modernization/events/attendance.yaml:36`
- platform.identity.RoleCreated：`docs/modernization/events/identity.yaml:5`
- platform.identity.RoleRevised：`docs/modernization/events/identity.yaml:31`
- platform.identity.RoleEnabled：`docs/modernization/events/identity.yaml:57`
- platform.identity.RoleDisabled：`docs/modernization/events/identity.yaml:83`
- platform.identity.RoleDeleted：`docs/modernization/events/identity.yaml:109`
- platform.identity.AccountEnabled：`docs/modernization/events/identity.yaml:135`
- platform.identity.AccountDisabled：`docs/modernization/events/identity.yaml:161`
- platform.identity.AccountRolesReplaced：`docs/modernization/events/identity.yaml:187`
- platform.identity.AccountSeparated：`docs/modernization/events/identity.yaml:219`
- platform.identity.AccountOpened：`docs/modernization/events/identity.yaml:246`
- platform.identity.EmergencyAdminRegistered：`docs/modernization/events/identity.yaml:278`
- platform.identity.EmergencyAdminEnabled：`docs/modernization/events/identity.yaml:304`
- platform.identity.EmergencyAdminCredentialsReset：`docs/modernization/events/identity.yaml:330`
- platform.identity.EmergencyAdminDisabled：`docs/modernization/events/identity.yaml:356`
- platform.identity.AuthorizationOpened：`docs/modernization/events/identity.yaml:388`
- platform.identity.AuthorizationConfirmed：`docs/modernization/events/identity.yaml:418`
- platform.identity.AuthorizationRejected：`docs/modernization/events/identity.yaml:448`
- platform.integration.SourceReferenceRegistered：`docs/modernization/events/integration.yaml:5`
- platform.integration.SourceReferenceConfirmed：`docs/modernization/events/integration.yaml:46`
- platform.integration.SourceReferenceRejected：`docs/modernization/events/integration.yaml:87`
- platform.integration.SourceReferenceSuperseded：`docs/modernization/events/integration.yaml:128`
- platform.integration.SourceReferenceRefused：`docs/modernization/events/integration.yaml:173`
- platform.numbering.NumberingRuleConfirmed：`docs/modernization/events/numbering.yaml:5`
- platform.numbering.NumberingRuleReconfigured：`docs/modernization/events/numbering.yaml:42`
- platform.numbering.NumberingRuleConfirmationGiven：`docs/modernization/events/numbering.yaml:79`
- organization.workforce.SyncRunSucceeded：`docs/modernization/events/workforce.yaml:5`
- organization.workforce.SyncRunHeld：`docs/modernization/events/workforce.yaml:30`
- organization.workforce.SyncRunFailed：`docs/modernization/events/workforce.yaml:56`
- organization.workforce.SyncRunReleased：`docs/modernization/events/workforce.yaml:82`
- organization.workforce.SyncRunHoldReleased：`docs/modernization/events/workforce.yaml:118`
- organization.workforce.OrgSnapshotChanged：`docs/modernization/events/workforce.yaml:155`
- organization.workforce.LegalEntityRegistered：`docs/modernization/events/workforce.yaml:198`
- organization.workforce.LegalEntityRenamed：`docs/modernization/events/workforce.yaml:227`
- organization.workforce.LegalEntityActivated：`docs/modernization/events/workforce.yaml:253`
- organization.workforce.LegalEntityDeactivated：`docs/modernization/events/workforce.yaml:279`
- organization.workforce.LegalEntityCodeCorrected：`docs/modernization/events/workforce.yaml:305`
- organization.workforce.EmployeeRegistered：`docs/modernization/events/workforce.yaml:337`
- organization.workforce.LegalEntityAttributed：`docs/modernization/events/workforce.yaml:363`
- organization.workforce.LegalEntityAttributionEnded：`docs/modernization/events/workforce.yaml:403`
- organization.workforce.LegalEntityAttributionCorrected：`docs/modernization/events/workforce.yaml:443`
