复审结论：必改
commit：59a6a2a728235a4ee02f2c2e94d0b747946d0882
复审者：opus-终审-对抗-1115

patch-id：ba5fc94becbb7bd7cf98330e6176f0be82782af2

影响：调动或更正员工 → platform.identity 监听器（HandleEmployeeTransferred / HandleEmployeeCorrected）→ OpenAssignmentReviews → permission_adjustments 行、PermissionAdjustmentOpened 审计、ASSIGNED 企业微信卡。已证明两层调用（监听器 → OpenAssignmentReviews → 仓储 / 网关），由单元测试跑出，四条防线由 IT 跑出。本轮为对抗审查：只给 diff 和卡片，每条必改都在临时副本里跑过（`git archive` 导出到 scratchpad，结束后已删除；未改动任何 worktree）。

## 必改

### 1. 新部门负责人没有企业微信 user id 时，监听器抛 NPE：范围复核条目丢失，事件反复重试（正确性，fail-closed 防线失效）
- 位置：`backend/src/main/java/com/forcitis/one/platform/identity/application/OpenAssignmentReviews.java:116` `new WecomCard(List.of(profile.wecomUserId()), ...)`
- 问题：`EmployeeProfile.wecomUserId` 在实际数据中可以为空。`Employee.wecomUserId` 标注了 `@Nullable`（`organization/workforce/domain/Employee.java:12`）；`JdbcOrgSnapshotRepository:102` 会写入 null；`OrgDirectoryService.profile()` 原样透传。`List.of(null)` 抛出 NullPointerException，于是 `openScopeReviewFor` 和整个监听器事务回滚：插入的条目、Opened 事件一起丢失，发布记录保持未完成，会被重新投递，每次都同样失败。这直接违背 N1-09 / 裁定 6602688198「不拒绝调动、不丢条目」。`HandleEmployeeTransferred` 走的是同一段代码，调岗路径受同样影响。
- 注释与代码矛盾（按规则另列为正确性问题）：同文件 `:108` 的 Javadoc 写「none when … the head has no WeCom user id」，而代码在这种情况下会崩溃，并没有跳过。
- 复现（已跑）：在临时副本的 `HandleEmployeeCorrectedEscalationTest` 加一条用例。负责人的 `EmployeeProfile` 的 wecomUserId 为 null，其余与 `aMoveGivesTheNewHeadAScopeReview` 相同；断言条目照常插入、不发卡。结果：`java.lang.NullPointerException at java.util.List.of … at OpenAssignmentReviews.lambda$assign$0(OpenAssignmentReviews.java:116)`，条目没有插入。「事件会被重投」这一后果按 `@ApplicationModuleListener` 的语义推断，未端到端跑（未验证）。
- 修法：与 `TaskNotifier.java:144` 的做法一致，`profile.wecomUserId() == null` 时不发卡，记一条 `ALERT P3 platform.identity` 行（只含事件 id 和条目 id），条目照常生成；同时补一条红/绿用例。

### 2. 在职员工由更正改换部门或岗位、且持有敏感角色时，不生成敏感角色复核（丢失一道防线，与已采纳的 ADR 不符）
- 位置：`HandleEmployeeCorrected.java:37-48`（部门变化只调用 `openScopeReviewFor`；只改岗位时直接返回），以及 `:15` 的 Javadoc「raises the scope review of a transfer, nothing else」。实现方自己的测试 `HandleEmployeeCorrectedEscalationTest.java:175 aMoveBetweenDepartmentsRaisesNothing` 和 `aPositionOnlyCorrectionRaisesNothing` 把这个偏离固定了下来。
- 依据（原文逐条核对过）：
  - ADR-0032 第 4 项第 3 条：触发条件是持有敏感角色的人，其 `org_unit_id` 或 `position_id` 发生变化，与来源命令无关。
  - ADR-0081 决定 3：保留 `EmployeeCorrected` 作为来源①，载荷与 `EmployeeTransferred` 同名；只在缺一侧部门时，才写「敏感角色复核与范围复核都不生成」。反过来说，前后两侧都有部门时两种都生成。
  - ADR-0082 决定 1：「每人每个更正命令一条，与调岗路径相同」。
  - f-1.md 读写表 :158：「调岗、更正……后生成条目」。
  - 卡上所有裁定都没有「更正只生成范围复核」的说法。裁定 6602688198 第 9 项只说范围复核对更正「同一规则」，第 8 项要求同一触发两种条目都生成。
- 复现（已跑）：在临时副本的同一测试类加一条用例。持有敏感角色（`business_admin`）的人收到更正，`triggerFieldKinds=[ORG_UNIT]`，前后部门都有值；断言插入一条 SENSITIVE_ROLE_REVIEW。结果：`Wanted but not invoked: permissionAdjustmentRepository.insert(...) Actually, there were zero interactions with this mock.`
- 修法：更正时前后部门都有值，就和调岗一样，对每个变化字段调用 `openFor`（只改岗位也算）。部门变化时再加范围复核。删掉 `:15` 那句 Javadoc 和两条把偏离固定下来的单元测试，改成正向用例。如果设计有意让更正不做敏感复核，需要由 one-design 留下书面裁定、修订 ADR，而不是由实现自定。

## 建议
- （未证明，只是设计疑点）调入者本人就是新部门负责人时，`recipient` 是他自己。按 f-1 不变量 19，本人的条目他读不到也办不了；按不变量 22，收件人不为空的来源①组织数据管理员又不能办。条目可能就此搁置到升级。建议请 one-design 判定这种情况是按无负责人处理（收件人为空）还是另有规则。
- `escalationDueAt` 在跨年时，如果下一年的工作日历还没维护会抛异常，这时 A-6 第 4 条「取不到法人照样建条目」不生效，监听器会一直重试。该行为属既有的「日历未维护即失败」口径，不在本 diff 的裁定范围内，记一行。

## 已核且无问题（含破坏验证）
- PR 头 = `59a6a2a728235a4ee02f2c2e94d0b747946d0882`（`gh pr view 1115 --json headRefOid`）；diff 取自 `git diff origin/main...59a6a2a72`。
- 迁移：两个新文件 `V20261103130000`、`V20261103140000` 的版本高于 main 上最高的 `V20261103120200`；没有修改或删除已合入的迁移（`git diff --diff-filter=DMR` 在 migration 目录为空）。CHECK 形状与 ADR-0082 决定 3 及更正 6599341585 第 3 点逐项一致：`IS DISTINCT FROM`、前部门为空则前岗位也必须为空、只有 SENSITIVE_ROLE_REVIEW + ORG_UNIT 允许前部门为空。integration 迁移保留了 main 上已有的全部取值，另加 `PERMISSION_ADJUSTMENT` / `PERMISSION_ADJUSTMENT_ASSIGNED`，与 Java 枚举一致。
- `WorkCalendarKey` 本来就接受 null 法人（表示默认日历），`WorkCalendarService` 去掉 `requireNonNull` 后，null 正确回落到默认日历。
- 文件行数：最大的新文件 `CorrectionUndoesSeparationReviewIT.java` 677 行，低于 1000。
- 目标 URL 写死为 `https://one.forcitis.com/`，与 `AdministratorFloorNotice`、`SeparationHoldNotice` 的现有约定一致。

破坏验证（临时副本，Testcontainers）：

| 防线 | 破坏方式 | 结果 |
|---|---|---|
| CHECK：前部门为空只允许 SENSITIVE_ROLE_REVIEW + ORG_UNIT | 把该分支改成 `OR TRUE`，并删掉前岗位那一行 | 红：`PermissionAdjustmentShapeIT` 2 条失败（`aNullBeforeUnitIs…`、`onlyAnAssignmentChangeCarriesTheTrigger`） |
| CHECK：前部门为空则前岗位必须为空 | 只删掉 `(before_org_unit_id IS NOT NULL OR before_position_id IS NULL)` | 红：`aNullBeforeUnitIsOnlyForAReinstatementOfASensitiveRoleReview` |
| 去重 `existsForTrigger` | 改为恒返回 false | 红：`aReplayGivesOneScopeReviewAndOneCard`、`anUndoneSeparationGivesOnePersonItemForAHolderOfSensitiveRoles` |
| 调岗时前部门为空：拒收并记 ALERT P2 | 把守卫改成 `if (false && …)` | 红：`aTransferWithoutItsBeforeDepartmentIsAlertedNotRetriedAndGivesNoItem` |
| 法人为空时回落默认日历（A-6 c） | 在 `WorkCalendarService.endOfWorkdayAfter` 加回 `requireNonNull(legalEntityId)` | 红：`anUnresolvableEntityStillGivesTheItemWithTheDefaultCalendarAndOneP3Alert` |
| 负责人没有企业微信 id 时不发卡、条目照建（Javadoc :108 声称的防线） | 不用破坏，现状下就是 NPE | 防线不存在，见必改 1 |
