# 入职日期口径（意见稿，不签表；答 one-1a-par/one-1a-admin，卡 E 前置）

> 冻结（2026-11-04，#1263）：意见稿，不再更新；入职日期落地以 Flyway 迁移与 ADR-0068 为准。

- 日期：2026-10-22；作者：one-data；依据：W2 设计 `W2_EMPLOYEE_POSITION_VERSIONING_DESIGN.md`（第 4 版）、ADR-0068、B31_ADM02_BLUEPRINT §4 与卡 E、DAT-1021-ARC-01 取证（老系统人员数据）；取证补充第 6 节待 one-ops 结果。

## 1. 放哪：`employee_versions`，不放 `employees`

入职日期是雇佣关系这一期的属性，会因重新入职而变，按 D17 属于版本化属性，不进根表。做法：`employee_versions` 加一列 `employment_start_date date`：
- 在职版本（`employment_status = 'EMPLOYED'`）必填，离职版本为空：具名 CHECK `(employment_status = 'EMPLOYED') = (employment_start_date IS NOT NULL)`（迁入行例外见第 5 节）；
- 入职命令和复职命令（见第 2 节）写入，值 = 该命令生效日（`effective_from_date`）；之后同一段在职期间的转正、调岗、停薪留职、更正版本**原样复制**该值；
- 具名 CHECK `employment_start_date <= effective_from_date`；不可早于员工自己的最早版本之外的任何事实，不另加跨行约束（命令层保证连续性，更正走第 2 节的作废机制并重算复制值）；
- 不是由版本区间「推出来」：推导（最近一次离职之后第一个在职版本的起始日）在转正、调岗、同日修订、提前登记的版本下容易错，且无法为"某月日"查询建索引；显式列加部分索引更稳。

索引：`CREATE INDEX ix_employee_versions_employment_start_month_day ON organization_workforce.employee_versions ((EXTRACT(MONTH FROM employment_start_date)), (EXTRACT(DAY FROM employment_start_date))) WHERE employment_status = 'EMPLOYED' AND superseded_at IS NULL;`，支撑"某月日"过滤（D19）；查询在候选集上再按业务日（`effective_from_date ≤ 业务日 < effective_to_date`）取当时有效版本。

## 2. 重新入职

"重新入职取重新入职之日"：W2 命令种类里的"复职"若指**离职后再次入职**，则复职命令写一条新的 `EMPLOYED` 版本，`employment_start_date` = 复职生效日，沿用同一个 `employee_id`（身份不变、工号不变）；之前的 SEPARATED 版本保留。若"复职"指**停薪留职结束**，则它不改入职日期，重新入职需要另一个命令种类。**请 one-architecture/人事规则会话澄清 ADR-0068 里"复职"的含义**，我按"离职后再入职"写本稿，停薪留职结束只是 `is_on_unpaid_leave` 由真变假，不动入职日期。

## 3. 与 `employment_phase`、停薪留职的关系

- `employment_phase`（试用/正式）与入职日期独立：周年从入职日起算，不看阶段，满周年查询返回阶段列，由行政侧按"正式员工"口径（one-rules G-3）自己过滤；
- 停薪留职（`is_on_unpaid_leave`）不影响入职日期，也不影响满周年资格（ZN-06 照发）；
- 离职：当日已离职不含——业务日当天有效版本是 `SEPARATED`（其生效日 = 最后工作日次日）就不返回；最后工作日当天仍返回。

## 4. 满周年查询的语义（给 api 契约）

`employedWithAnniversaryOn(businessDate, cursor)`：
1. 取业务日当天有效的版本是 `EMPLOYED` 的员工；
2. `employment_start_date` 的月日等于业务日的月日；业务日是 02-28 且当年为平年时，**同时**取 02-29 入职的人（规则在 workforce 侧，不让调用方重复实现）；
3. 满 n 年且 n ≥ 1：`businessDate` 的年份 > `employment_start_date` 的年份；
4. 返回 `(employeeId, employmentStartDate, anniversaryYears, employmentPhase)`，游标分页，每页有上限（D19）；
5. 入职日期缺失（仅迁入行可能）：**单员工查询**（`currentStartOf`）明确失败 `ORGANIZATION_WORKFORCE_EMPLOYMENT_START_UNKNOWN` 422，不默认；**批量满周年查询**不返回这些员工，另给一个"入职日未知的在职员工计数与清单"查询供人事补，不让批量任务整体失败。

## 5. 迁入口径（按第 6 节取证修订）

- 入职日期**不得用迁入日代替**（W2 第 6 节"取不到用迁入日并标左截断"只适用于版本的生效起点，不适用于入职日期：用迁入日会让周年全部算错）。
- **权威来源 = 人事提供的《在职员工入职日期表》**（工号、入职日期；与 B-1a 员工法人归属表可同一张表加一列，在职约 300 人，人事核对后提供）。迁入规格用它赋值，`employment_start_basis_code = HR_PROVIDED`。老系统数据只用来**预填这张表供人事核对改正**，不直接当权威：
  - A8 `HR_STAFF_INFO.WORK_STARTING_DATE`：654 行里有值 341，个人档案里没有"入职日期"列，这是唯一像入职的日期（最小值 2005-11-05），字段含义须人事确认；用作预填，迁入时若人事表缺该员工且有此值，`LEGACY_HR_RECORD`；
  - 老系统 `psa_employee_transfer_record` 每人最早区间起始日：与上一项有值者 96% 完全一致（很可能就取自同一来源），HR 没填的 305 人里它另有来源、2017 年起激增，更像系统启用后补的**岗位区间起点**，不当入职日；仅预填，迁入时若既无人事表也无 HR 值，`LEGACY_ASSIGNMENT_START`（低可信）；
  - 账号创建时间（A8 `CREATE_TIME`、老系统 `create_time`）：2020、2023 年是批量建账，**不用**。
- `employment_start_basis_code` 取值：`DECLARED`（原生入职命令）、`HR_PROVIDED`、`LEGACY_HR_RECORD`、`LEGACY_ASSIGNMENT_START`、`UNKNOWN`（后两者以外均有值）；`UNKNOWN` 与迁入行外无来源时 `employment_start_date` 为空，仅 `change_channel_kind = 'MIGRATION'` 的行允许（具名 CHECK）。满周年批量查询只返回 `DECLARED`、`HR_PROVIDED`、`LEGACY_HR_RECORD` 三类，`LEGACY_ASSIGNMENT_START` 与 `UNKNOWN` 不返回，进"待人事确认入职日"清单（计数与清单查询），避免低可信日期触发错误的周年贺卡。
- 迁入质量规则：`MQ-M-11` 在职员工入职日期缺失或低可信（`UNKNOWN`、`LEGACY_ASSIGNMENT_START`），告警，负责人人事，期限按迁入规格；人事补录走更正命令（作废旧版本、写新版本，时间线不丢）。
- 左截断：只要有来源值就迁入，不论多早。

## 6. 老系统取证结果（one-ops 只读，ADR-0037 已登记，仅聚合）

| 事实 | 数 |
|---|---|
| A8 `HR_STAFF_INFO` | 30 列，无"入职"列；`WORK_STARTING_DATE` 非空 341/654（2005-11-05～2026-10-12）；`BEGIN_WORK_DATE` 全空 |
| A8 `ORG_MEMBER.CREATE_TIME` 在职 321 人 | 2020 年 128（A8 上线批量建账）、其余年份 8～68 不等，非入职时间 |
| 老系统调动视图每人最早起始日 | 1247 人，2005-11-05～2026-10-14；2017 年起每年 66～241 人，2021 年 241 人 |
| 老系统用户表未删除账号 | 303 人，建账年份 2023 年 166（批量建账） |
| HR 档案与老系统按工号对上 | 645 人；HR 有值的 340 人中与视图最早日期相差 0 天 328、1～30 天 0、超 30 天 12（全是 HR 日期更早） |

结论：A8 与老系统都**没有可靠的入职日期**；最像的 `WORK_STARTING_DATE` 只覆盖约一半且字段含义未确认，调动视图的日期与它同源或不可信。所以入职日期必须由人事提供，老系统数据只能预填。

## 7. 对施工卡的影响

- 卡 E（workforce 提供入职日期与满周年查询）依赖 W2 `employee_versions` 先建；`employment_start_date` 随 W2 版本表的 DDL 一并设计（W2 PR-B 的 DDL 草案补这一列和索引，我签发时一起看）。
- 卡 D（周年作业）不依赖入职日期来源问题以外的东西；入职日期整批缺失时作业不应整体失败，按第 4 节第 5 点处理。
- 词条：`hire_date` 未登记，列名 `employment_start_date`（"入职日期"，bc organization.workforce）交 one-glossary 登记，`employment_start_basis_code` 同。
