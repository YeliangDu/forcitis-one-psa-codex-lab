# 数据架构规则卡（Phase 1a 修复批 B2）

来源：`phase1a/DATA_SCORECARD.md`（T1–T5、K1–K15、D1–D10）与用户对 T1–T5 的决定（`intent/*数据打分卡*`）。编号 DATA-RULE-nn，对应的测试名以编号开头。

### DATA-RULE-01: 版本表只追加
**Source:** 数据打分卡 T1。**Specification:** 组织单元、任职、法人、法人归属期间的变化历史只追加：开放版本（失效日为空）只能被关闭；已关闭的行不能改，任何行不能删，表不能清空；运行角色没有 DELETE 与 TRUNCATE。同一天的再次变化关闭当天版本（失效日等于生效日，空区间）并以修订序号加 1 追加新行。**Verified by:** `OrgHistoryAppendOnlyIntegrationTest`、`LegalEntityHistoryIntegrationTest`、`DataArchitectureTest`（触发器按行为核对，建库脚本的 REVOKE 清单与注册表一致）。

### DATA-RULE-02: 工作日历今天之前的日期不可修改
**Source:** 数据打分卡 T2。**Specification:** 已维护过的年份里，早于今天的日期不可新增、删除或改类别；一个年份的首次维护不受限制（还没有任何东西按它算过）。例外只给组织数据管理员，经独立用例与权限 `organization.attendance:locked-day:update`，发出 `WorkCalendarLockedDaysChanged`（每个被改日期的改前改后类别，进审计）。已算出的审批时效（`due_at`）不因日历修改而重算。**Verified by:** `WorkCalendarYearTest`、`WorkCalendarIT`。

### DATA-RULE-03: 法人版本以记录日为准，1a 不支持预约或补录
**Source:** 复审 B2-M8（二选一）。**Specification:** 法人的代码、名称、状态变化记入 `legal_entity_versions`，生效日就是修改发生的业务日；命令不带生效日，1a 不支持预约（将来日期生效）或补录（过去日期生效）。**Why:** 法人对单据、部门、员工的归属由有生效区间的归属表（ADR-0026，由管理员显式给出日期）决定，法人自身的名称与状态只是描述性历史；补录会让已按旧名称或状态发出的编号、事件与审计和历史版本对不上，预约又需要定时作业与「未生效」状态，1a 没有使用者。出现真实需求时再给命令加生效日，并新增规则卡。事件的 `effectiveFrom` 取记录日。

### DATA-RULE-04: 已处理事件的保留与幂等不依赖保留期
**Source:** 数据打分卡 T3、复审 B2-M9。**Specification:** `cost_timesheet.consumed_events` 保留 90 天后删除（作业 `cost.timesheet.consumed.events.purge`）。**Why 90 天:** 大于事件登记的归档期（30 天）和重投窗口，足以认出正常重投。**为什么删除后仍安全:** 事件发布登记里**未完成**的发布不会被自动删除，所以理论上可能在 90 天后才重投；因此幂等不只靠这张表——监听方对业务聚合按状态转换幂等（工时单只在「正在等待该轮结果」时才应用审批结果，已应用或已被取代的结果不改变任何东西，见 `ApplyApprovalResult`）。这张表是快速路径与排查依据，不是唯一防线；新增的监听方必须有同样的状态幂等。

### DATA-RULE-05: 组织对账中的悬空引用
**Source:** 数据打分卡 K6、复审 B2-H3。**Specification:** 部门负责人、区域应收负责人引用到本次快照中不存在的人，记 `HEAD_NOT_FOUND`、`REGIONAL_MANAGER_NOT_FOUND`；上级部门既不在本次快照也未曾存入，记 `PARENT_NOT_FOUND`；相应字段置空，同步继续。同 schema 的引用补可延迟外键。**生产取证:** 用户授权的只读查询（采集时间 2026-10-15T01:00:46+08:00，结果在主分支 `docs/modernization/phase1a/A8_DANGLING_REFS.md`，提交 159d993）：部门负责人 54 条引用、悬空 0；区域应收负责人 6 条、悬空 0；上级部门 366 个非根部门、悬空 0。所以对现有数据加外键不会失败；上面的悬空 ISSUE 是防止以后出现。

### DATA-RULE-06: 归档先确认、后删除
**Source:** 数据打分卡 T3，用户决定 2026-10-15（国内 OSS 归档存储）。**Specification:** 引擎历史（在线 3 年）与审计（在线 3 年）按批写入不可变对象：算 SHA-256、上传（归档存储类型、禁止覆盖、Content-MD5）、读回核对，核对通过才删除引擎中的历史；审计在线行暂不删除。归档含个人信息，只能放在国内地域，bucket、地域、前缀为配置项，缺失则启动失败；写入探针启动时与每天一次。应用不负责到期删除，由 WORM 与生命周期规则处理。细节见 `INFRA_RUNBOOK.md` §5.3。
