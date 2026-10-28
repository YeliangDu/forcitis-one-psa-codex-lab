# 人事命令契约对照（SPLIT_PLAN A，#737）

- 依据：ADR-0068（含证据附件「事件契约」）、ADR-0069、ADR-0073、ADR-0055 决定 3；表 `organization_workforce.personnel_commands` 已在基线（#632）。
- 范围：命令 ID 类型、领域事件与负载、生效日、事件目录登记。用例、仓储、接口、作业、撤同步归 #738 及后续卡（C、D、E）。
- 路径缩写：`M/` = `backend/src/main/java/com/forcitis/one/organization/workforce/`；`T/` = `backend/src/test/java/com/forcitis/one/organization/workforce/`；`WF` = `backend/src/main/resources/db/migration/organization_workforce/V20261014131000__organization_workforce_baseline.sql`；`EV` = `docs/modernization/events/workforce.yaml`；`GL` = `backend/glossary/proposed/organization_workforce.yaml`。

## 1. 逐条对照

| 条款 | 代码现在怎样 | 位置 |
|---|---|---|
| ADR-0068 1.1 人事命令是员工聚合内的实体，表 `personnel_commands` | 表已在基线；本卡不建实体与仓储（#738） | WF:573-616 |
| ADR-0068 1.2 `personnel_command_id uuid`，UUIDv7，应用层经 `IdGenerator` 生成，客户端不能提供 | `PersonnelCommandId(UUID value)`，在 workforce api，拒绝空值与全零 UUID；生成方与幂等归 #738 | `M/api/PersonnelCommandId.java:12-15`；WF:574 |
| ADR-0068 1.2 所有种类共用一个 ID 空间，其他表只按 ID 引用 | 八个事件都用同一个 `PersonnelCommandId` | `M/events/*.java`；`T/PersonnelCommandContractTest.java:187` |
| ADR-0068 1.3 种类（含停薪留职开始、结束）；1.4 状态 | 只在表的 CHECK 中；Java 枚举随实现（#738） | WF:600-603 |
| ADR-0068 1.5 生效日是人事业务生效日、新状态的第一天；离职为最后工作日次日 | 负载 `effectiveDate` 为 `LocalDate`（按日）；离职口径写在事件说明与目录描述，换算（命令入参）归 #738 | `M/events/EmployeeSeparated.java:9-13`；EV:622；WF:577、607 |
| ADR-0068 1.5 命令行不存姓名、邮箱、性别 | 事件负载只有 ID、枚举与日期，测试逐个检查 | `T/PersonnelCommandContractTest.java:193` |
| ADR-0068 1.9 命令生效时才发布；录入与撤回有意不发，在注册表登记 | 尚无发布方（#738）；「有意不发」在目录分片中以注释登记（见待决 Q7） | EV:493-494 |
| ADR-0068 1.9 信封以员工为聚合，`businessDate` 为生效日，`occurredAt` 为 `applied_at` | 目录 `aggregate: Employee`、`businessDate` 声明为人事业务生效日，测试封装信封时核对；`occurredAt` 由发布方填（#738） | EV:495 起；`T/PersonnelCommandContractTest.java:211、220` |
| ADR-0068 1.9 负载 `personnelCommandId`、`employeeId`、`effectiveDate` | 七个事件照此三项 | `M/events/EmployeeHired.java:10` 等 |
| ADR-0068 1.9 调岗另带触发字段与前后部门 | `triggerFieldKinds`（`TriggerFieldKind`：部门、岗位）、`previousOrgUnitId`、`newOrgUnitId`；列表不可变 | `M/events/EmployeeTransferred.java:17-22`；`M/api/TriggerFieldKind.java:5` |
| ADR-0068 1.9 事件名 `EmployeeHired`、`EmployeeRegularized`、`EmployeeTransferred`、`EmployeeSeparated`、`EmployeeReinstated`、`SeparationHeld`、`SeparationHoldReleased` | 七个记录都在 `events` 包；测试断言带人事命令 ID 的事件恰为这七个加更正 | `M/events/`；`T/PersonnelCommandContractTest.java:60、187` |
| ADR-0068 1.9 `EmployeeCorrected` 只在改变截至今天的状态时发，负载为两个 ID 加受影响区间 | `affectedFrom`、`affectedTo`（可空）；发布条件归 #738 | `M/events/EmployeeCorrected.java:15-16` |
| ADR-0068 1.9 停薪留职的事件随第 3 条定名 | 未定名，未建（Q1） | — |
| ADR-0068 1.9 取代 `OrgSnapshotChanged`、`EmployeeRegistered` | 两者仍在，删除归 C、E（ADR-0069 第 9 条） | `M/events/OrgSnapshotChanged.java`、`EmployeeRegistered.java` |
| ADR-0068 1.10 契约在 A，实现在 B；复职由 `EmployeeReinstated` 承载 | 本卡只有契约；`EmployeeReinstated` 已建 | `M/events/EmployeeReinstated.java` |
| ADR-0068 2.2 需暂停时转待放行，不发 `EmployeeSeparated` | `SeparationHeld` 已建；不发离职事件由发布方保证（#738） | `M/events/SeparationHeld.java` |
| ADR-0068 2.3 放行后按原生效日执行 | `SeparationHoldReleased.effectiveDate` 为原生效日 | `M/events/SeparationHoldReleased.java` |
| ADR-0068 2.5 B 不接线入口、identity 在 C 订阅 | 目录 `consumers` 只有审计，`noConsumer` 写明 | EV:495 起 |
| ADR-0068 第 5 条 F-1 订阅调岗、离职、更正 | 三个事件已建，订阅归 F-1 | EV:567、622、694 |
| ADR-0068 第 6 条 精度按日，依据人事业务生效日（DECLARED） | 生效日一律 `LocalDate`，测试拒绝时刻类型 | `T/PersonnelCommandContractTest.java:201` |
| ADR-0068 后果：待登记用语 | 八个事件与触发字段登记为提议术语；其余用语见 Q10 | GL:310-451 |
| ADR-0055 决定 3.3 管理员离职暂停载体改为离职命令 | `SeparationHeld`、`SeparationHoldReleased` 按人事命令 ID 引用 | 同上 |
| ADR-0055 决定 3.5 `revocation_separation_command_id` 引用离职命令 | 该列由 R 改名（已合入）；`EmployeeSeparated.personnelCommandId` 即其来源 | `M/events/EmployeeSeparated.java:13` |
| ADR-0055 决定 4 去重键（人，人事变更命令 ID，触发字段） | 调岗负载带人事命令 ID 与触发字段 | `M/events/EmployeeTransferred.java:17-18` |
| ADR-0069 第 9 条 E 同批删除 `EmployeeRegistered` | 不在本卡 | — |
| ADR-0073 3.2 人事命令增 `CHANGE_LEGAL_ENTITY`，`TRANSFER` 可带 `target_legal_entity_id` | 表 CHECK 尚无此种类与列；不在本卡（Q2） | WF:600 |
| ADR-0073 3.6 待会签 `AWAITING_COUNTERSIGN` | 表 CHECK 尚无此状态（Q2） | WF:601 |
| ADR-0073 影响面：「法人归属已变更」事件，名称交 one-glossary | 未定名，未建（Q2） | — |

## 2. 待决问题（本卡不定）

- **Q1** 停薪留职开始、结束的事件名与负载：ADR-0068 第 1 条第 9 项说随第 3 条定名，第 3 条未给名。
- **Q2** ADR-0073 的 `CHANGE_LEGAL_ENTITY`、`AWAITING_COUNTERSIGN`、`target_legal_entity_id` 未进 `personnel_commands`（表结构由 one-data 签发）；「法人归属已变更」事件名未定。
- **Q3** `EmployeeCorrected` 受影响区间的形态：本卡按版本表左闭右开取 `affectedFrom`（含）、`affectedTo`（不含，空为至今），ADR 未写，须 one-architecture 确认。
- **Q4** 调岗负载：ADR-0068 只写「前后部门」，本卡取生效日前一天与生效日的部门；F-1 的待调整权限另有前后岗位四列（`F-1.md` 第 237 行），是否在负载里加前后岗位未定。
- **Q5** `SeparationHeld`、`SeparationHoldReleased` 只带通用三项；是否带待放行离职 ID、放行人未定，`separation_holds` 表与其 ID 类型尚未建。`SeparationHeld` 发生时命令没有 `applied_at`，其 `occurredAt` 取什么未写。
- **Q6** 信封 `legalEntityId`：`EventDraft` 要求非平台聚合必填，员工事件取哪个法人（如生效日的成本法人）未写。
- **Q7** 「录入与撤回有意不发事件，在事件注册表登记」：仓库没有机器可读的「有意不发」登记处，本卡只在 `workforce.yaml` 写注释。
- **Q8** 目录 `publisherTest` 暂指向 `PersonnelCommandContractTest`（尚无发布方）；#738 须改为发布方的测试。
- **Q9** 离职「最后工作日次日」的换算落在命令入参，本卡只写进说明，无代码强制；由 #738 定入参形态。
- **Q10** 人事业务生效日、待生效、已撤回、入职、调岗、复职、更正、试用、正式、组织命令等用语交 one-glossary；workforce「员工转正已生效」与 `contract.agreement` 的「转正」同名异义，中英文名都不同，`homonyms` 列不上，暂写在 note。
- **Q11** `platform-org-interfaces.signatures.txt` 随新增类型更新，按 `org_interface_notice.py` PR 正文须写「已通知平台 owner」。

## 3. 验证

`backend/mvnw -o -Dtest=PersonnelCommandContractTest,EventCatalogue*Test test`；改坏事件或目录后对应测试转红，见 PR 说明。
