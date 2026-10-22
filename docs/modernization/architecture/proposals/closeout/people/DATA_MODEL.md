# 表级数据模型说明稿 · 人员组试点（B-13、B-14、B-27）

- 状态：起草（one-arch-people，2026-10-16）；全貌级，不到字段、不写 DDL；机器可读片段为同目录 `data-model.people.yaml`（格式以 `closeout/CONVENTIONS.md` §3 为准）。
- 依据：SESSION_PLAYBOOK §14.1（D1–D19、D17 细则）、§14.2（M1–M10）；NAMING_STANDARD §2.4a；ENTERPRISE_AGGREGATES_DRAFT（§2.1、§2.2 B-13 / B-14 / B-27、§4.1）；ENTERPRISE_INFO_MAP_DRAFT（OBJ-28、54–58、60、64、68–70、72、109、111）；`backend/architecture/data-access-registry.yaml` 与 `db/migration/organization_workforce`、`organization_attendance` 基线；`JOB_CATALOG_DRAFT.md` §1（origin/claude/one-architecture-adr-governance）；ADR-0033、0036、0041、0043。
- 已定口径（照用，不重议）：用户 2026-10-16 生产自成体系、一次投产、全部历史迁入（U-1–U-4）；A8 早于投产日停用（C-7）；U-12 版本生效依据为人事业务生效日（DECLARED）；Q-08 只要职位目录、不管编制；Q-03 企业微信登录，凭据与身份绑定归 platform；one-architecture 裁定（2026-10-16）AGG-61 / 62 / 64 及相关快照改写为自有人事记录，是已定事项的落地，不是待用户题。
- 读法：每表一行，列「状态」= 已建保留 / 已建改形 / 新建（相对 `db/migration` 基线）；「数据类别」按 D17 细则 1；「版本化」写 D6 维度型（生效依据）/ D15 配置型 / 不需要（限 D17 四类并写出处）；「迁入」写来源对象与通道；「分级」= 访问级别 + 个人信息标记初判（ADR-0043）。表数：B-13 目标 28（已建保留 5、改形 6、新建 17），B-14 目标 5（保留 2、新建 3），B-27 目标 11（全新建）；B-13 另有 4 张已建表退役。

## 1. B-13 organization.workforce（schema `organization_workforce`）

升级为人事记录系统后的整体形状：根表只留标识与不随时间变的列；会变的列进版本表（D17 细则 5）；版本表来源从「同步观察日（OBSERVED）」改为「业务声明日（DECLARED）」，生效日取人事业务生效日，可提前登记、补登落回真实日期，修改时间由审计承担（U-12）；`last_sync_run_id`、`wecom_user_id`、同步状态类列不再留在主数据表。

### 1.1 表清单

| 聚合 | 表 | 根 / 次级 | 状态 | 数据类别 | 主要列族 | 版本化 | 迁入 | 分级初判 |
|---|---|---|---|---|---|---|---|---|
| AGG-60 法人主体 | `legal_entities` | 根 | 已建改形（名称、状态移出到版本表，根只留代码） | 主数据 | 法人代码、创建与乐观锁 | 不需要（根不存可版本化列，D17 细则 5） | 全量：五家法人由初始化录入（管理员维护，不从外部同步；`ONE_OFF_IMPORT`） | INTERNAL / 无 |
| AGG-60 | `legal_entity_versions` | 版本 | 已建保留 | 主数据 | 生效区间、修订序号、名称、状态 | D6 维度型，DECLARED | 同上 | INTERNAL / 无 |
| AGG-117 法人归属 | `legal_entity_attributions` | 根 | 已建保留 | 主数据 | 归属对象（部门或员工）、事件序号 | 不需要（只是归属期间的聚合根） | 全量，随两张期间表 | INTERNAL / PI（员工行） |
| 同上 | `org_unit_legal_entities` | 版本 | 已建保留 | 主数据 | 生效区间、部门默认法人 | D6，DECLARED（已建，有 `EXCLUDE`） | 全量；来源 ADR-0026 D-1 的部门法人对照（来源对照） | INTERNAL / 无 |
| 同上 | `employee_legal_entities` | 版本 | 已建保留 | 主数据 | 生效区间、员工单独指定的法人 | D6，DECLARED | 全量 | INTERNAL / PI |
| 同上 | `legal_entity_attribution_corrections` | 次级 | 已建保留 | 审计类事实（只追加） | 更正对象、原期间、新结束日、理由、更正人 | 只追加 | 不迁（更正只发生在新系统内） | INTERNAL / PI |
| AGG-61 组织单元 | `org_units` | 根 | 已建改形（只留编码与标识；名称、父节点、状态、负责人等移出；删 `last_sync_run_id`） | 主数据 | 组织单元编码、乐观锁、事件序号 | 不需要（同上） | 全量；来源 A8 组织单元，经来源对照（platform.integration `source_references`），主键 UUIDv7 | INTERNAL / 无 |
| AGG-61 | `org_unit_versions` | 版本 | 已建改形（生效依据 OBSERVED → DECLARED；单元类型替换 `region_kind`、`is_delivery_unit`，见结构问题） | 主数据 | 生效区间、修订序号、名称与简称、上级、状态、单元类型、负责人 | D6 维度型，DECLARED | 全量；A8 组织变更历史的生效日来源见口径 6 | INTERNAL / 无 |
| AGG-62 人员 | `employees` | 根 | 已建改形（删 `wecom_user_id`、`last_sync_run_id`、`is_fee_earner`、`employment_status` 的版本含义，保留权限用在职二值；表名与词表的关系见新用语） | 主数据 | 工号、姓名、性别、企业邮箱、登录名、在职二值、乐观锁、来源对照标识 | 不需要（D17 第四类：个人信息最小化只留当前值；会变的进 `employment_versions`） | 全量；A8 人员，经来源对照；企业微信 userid 只在 platform.integration 来源对照 | SENSITIVE / PI（ADR-0043 拟把邮箱、性别、企业微信 ID 降为 INTERNAL，待实施 PR） |
| AGG-62 | `employment_versions` | 版本 | 新建 | 主数据 | 生效区间、用工类型、用工状态（含候选阶段、停薪留职）、入职 / 转正 / 离职日、是否收费员工 | D6 维度型，DECLARED（U-12） | 全量；A8 人员异动（信息图证 4,247 条）按人事生效日回放，缺生效日见口径 6 | SENSITIVE / PI |
| AGG-62 | `interviews` | 次级 | 新建 | 事实 | 轮次、时间、面试人、评价与结论 | 不需要（单一记录，审计可还原） | 不迁（候选阶段记录；来源待取证） | SENSITIVE / PI（SPI 交 U-11） |
| AGG-62 | `hiring_decisions` | 次级 | 新建 | 事实 | 录用结论、拟入职日、拟任职位与职级、决定人 | 不需要（同上） | 不迁（结果已在两张版本表） | SENSITIVE / PI |
| AGG-62 | `separation_requests` | 次级 | 新建 | 交易 | 申请日期、期望离职日、原因类别、状态、批准人 | 不需要（单级批准单据，审计可还原） | 全量；老系统离职申请（来源待取证） | SENSITIVE / PI（原因含健康时 SPI，交 U-11） |
| AGG-63 任职 | `assignments` | 根 | 已建改形（主键由员工 ID 的 1:1 主岗投影改为每任职槽位一行 UUIDv7，主 / 兼并行） | 主数据 | 所属员工、主兼标识、乐观锁 | 不需要（根只是槽位标识） | 全量，随版本表 | INTERNAL / PI |
| AGG-63 | `assignment_versions` | 版本 | 已建改形（生效依据 DECLARED；加职级；岗位引用改职位；主任职不重叠改为条件排他约束，INV-62） | 主数据 | 生效区间、组织单元、职位、职级、汇报线、主兼 | D6 维度型，DECLARED | 全量；A8 异动回放；旧岗位经 one-data `JOB_MAPPING` 映射到新职位 × 职级，不建 136 个岗位对照（P-031） | INTERNAL / PI |
| AGG-64 岗位（职位目录） | `jobs` | 根 | 新建（取代已建 `positions`） | 主数据 | 职位编码（跨版本不变） | 不需要（根只留编码，P-6） | 不迁：新目录初始化录入；旧岗位只经映射 | INTERNAL / 无 |
| AGG-64 | `job_versions` | 版本 | 新建 | 主数据 | 生效区间、名称（不含级别）、职位族与子族、可适用职级区间、状态、简述 | D6，DECLARED（P-6） | 不迁（同上） | INTERNAL / 无 |
| AGG-64 | `job_families`、`job_family_versions` | 次级、版本 | 新建 | 参考数据 | 族 / 子族编码与层级；名称、上级、状态 | D6，DECLARED | 不迁 | INTERNAL / 无 |
| AGG-64 | `job_grades`、`job_grade_versions` | 次级、版本 | 新建 | 参考数据 | 职级编码与序位；名称、说明、状态 | D6，DECLARED；费率表键 | 不迁（旧「岗位编码」实为职级编码，只作映射证据） | INTERNAL / 无 |
| AGG-65 招聘需求 | `recruitment_requisitions` | 根 | 新建 | 交易 | 需求单号、组织单元、职位与职级区间、状态、批准与关闭 | 不需要（单一状态，审计可还原） | 只迁未结；来源 A8 OA 用人申请（#67，只作证据，待取证） | INTERNAL / 无 |
| AGG-66 注册执业证书 | `practice_certificates` | 根 | 新建 | 主数据 | 资格类别、证书编号、机构、状态 | 不需要（单一状态；延续由期间表追加） | 全量；来源待取证 | SENSITIVE / PI |
| AGG-66 | `certificate_registration_periods` | 次级 | 新建 | 事实 | 注册期起止、学时要求、达标结论 | 只追加 | 全量 | INTERNAL / PI |
| AGG-66 | `continuing_education_records` | 次级 | 新建 | 事实 | 学时类别、学时数、完成日期、证明引用 | 只追加 | 全量 | INTERNAL / PI |
| AGG-67 劳动合同 | `labor_contracts` | 根 | 新建 | 交易 | 合同编号、用人单位、类型与期限、试用期、状态、终止原因 | 不需要（单据；续订另立） | 全量，`legacy_numbers` 对照原编号（`legacy_numbers`）；来源待取证 | SENSITIVE / PI |
| AGG-106 绩效评估 | `performance_reviews` | 根 | 新建 | 交易 | 评估单号、类型、期间、被评人与评估人、任职版本引用、结论、状态 | 不需要（已确认后不改，更正另记） | 全量；A8 月度评估三模板与试用期评估（信息图证：月度近一年 782 单、档案 2,325；试用期 139 单 / 年、档案 188），`legacy_numbers` | SENSITIVE / PI（SPI 交 U-11） |
| AGG-106 | `performance_review_documents` | 次级 | 新建 | 事实 | 单据类型（一对一 / 成长记录）、内容 | 不需要 | 全量；一对一档案 458、成长记录 2 单 / 年 | SENSITIVE / PI |
| AGG-106 | `performance_review_corrections` | 次级（S-32 新增） | 新建 | 事实（只追加） | 原评估引用、更正项、更正后结论、更正原因、依据引用 | 只追加（INV-114） | 不迁：老系统无独立更正记录 | SENSITIVE / PI |

### 1.2 不属聚合的技术表（已建，不放进 yaml）

| 表 | 现状 | 建议处置 |
|---|---|---|
| `sync_runs`、`sync_diffs`、`sync_run_held_separations` | 外部组织源同步的运行记录、差异与暂停的离职名单（注册表 `AGGREGATE_ROOT` / `AGGREGATE_CHILD`，词表 `sync_run` 为 ACL 聚合） | 同步的前提是外部组织源，用户已定 A8 不接（U-1、U-7），自成体系后无运行时同步。建议：基线开放期内直接移除这三张表及相关事件（`sync_run_*`）、`last_sync_run_id` 列和对应守护登记；迁入期的批次、暂存、拒收、核对由 `platform_integration` 的迁入登记承担（ADR-0036 第 3、7 条），不需要保留 B-13 的运行表。若 one-architecture 认为切换窗口内仍需「迁入前后差异」，只留 `sync_diffs` 的迁入期用途并在窗口结束后清除。待裁定，见结构问题 1 |
| `positions` | 岗位快照（`SNAPSHOT`），含 `DISABLED` / `DELETED` 同步状态 | 退役，由 `jobs` + `job_versions` 取代；根本不同（岗位 = 职能 × 职级的旧叫法，新目录职位与职级是两维，Q-08） |

### 1.3 关系概要

- 同 schema 外键（子表随根写入）：版本表 → 根（`legal_entity_versions`、`org_unit_versions`、`employment_versions`、`assignment_versions`、`job_versions`、`job_family_versions`、`job_grade_versions`）；`assignments` → `employees`（保留外键，同聚合不同；见口径）；`certificate_registration_periods` → `practice_certificates`；`performance_review_documents` → `performance_reviews`。
- 同 BC 跨聚合按 ID 引用，不建外键，由聚合间 api 校验：`assignment_versions` → 组织单元、职位、职级、员工；`org_unit_versions.head_employee_id` → 员工；`interviews` / `hiring_decisions` → 招聘需求、职位；`labor_contracts` → 法人、员工；`performance_reviews.assignment_version_id`（记录评估时所用任职版本，D7）。
- 按基线 `assignment_versions` 的现有外键（员工、组织单元）和 `assignments` 的外键（员工、组织单元、岗位）是否保留，取决于 D3「一个聚合的表同 schema、跨聚合只按 ID 引用」：同 BC 内跨聚合允许外键，但版本表对「过去的版本指向当时的单元与人」已走 `fk_exceptions`（基线 R-1b 的做法），建议维持。
- 跨 BC：B-13 不引用其他业务 BC（聚合稿 §4.1：只依赖 platform.audit、platform.integration）；被 B-04 / 05 / 06 / 07 / 08 … 按 ID 引用（员工、组织单元、法人）。platform.integration：员工与组织单元的来源对照（`source_references`），不存企业微信凭据；platform.identity：登录绑定、会话，由 `Employed` / `Separated` 事件驱动。

### 1.4 三个法人口径（Q-09 / 聚合稿 Q27）

聚合稿与信息图把员工的法人口径写为三个关系：劳动合同用人单位（`labor_contracts.employing_legal_entity_id`，AGG-67）、数据范围法人（已建 `employee_legal_entities` / `org_unit_legal_entities`，ADR-0026 D-1）、个税扣缴义务人（B-15 的薪酬档案 AGG-93 与申报 AGG-95，不在 B-13 表里）。任务说明里举例的「社保缴纳主体」不在信息图的三口径里，核对结果见口径 7。三者分别记录，不互相派生；不一致的预警是读模型 / 定时核对（跨 B-13、B-15 读取），不新增 B-13 表；预警归属与读取方向见 NOTES 接缝问题 2。

### 1.5 分管范围配置（LEB-06，提议；AGG-135，one-architecture 给号）

用户 2026-10-22 定分管范围由人力维护、总经理批准（intent 2026-10-22-082120）。提议在 B-13 新增一张 D15 配置表 `oversight_scope_versions`（分管范围版本 OversightScopeVersion；分管范围 OversightScope，one-glossary 定名（PR #572））：

- 列族：版本号、生效区间（起、止）、分管领导（人员 ID）、分管的组织单元集合或法人集合、批准人、批准时间、依据（分工文件引用）、录入人；
- 版本化：D15，只追加；同一组织单元在同一日期只能有一位分管领导（区间不重叠，EXCLUDE 约束），批准人不得是录入人；
- 读取：审批与升级按单据业务日期取当时生效的版本解析分管领导；缺位、版本缺失、区间重叠的处理见 `README.md` 已定口径；
- 分级 INTERNAL；迁入：老系统无分管配置，投产前按分工文件首录一版，不迁历史。

## 2. B-14 organization.attendance（schema `organization_attendance`）

| 聚合 | 表 | 根 / 次级 | 状态 | 数据类别 | 主要列族 | 版本化 | 迁入 | 分级初判 |
|---|---|---|---|---|---|---|---|---|
| AGG-68 工作日历年度 | `work_calendar_years` | 根 | 已建保留（版本化待裁，口径 1） | 配置 | 法人（空为默认）、年份、乐观锁 | 现状可变 + 审计；D17 细则 8 要求配置型，待裁 | 全量：旧工作日历 2016–2026，`ONE_OFF_IMPORT`（注册表已登记） | INTERNAL / 无 |
| AGG-68 | `work_calendar_days` | 次级 | 已建保留 | 配置 | 例外日期、日类型 | 现状整集替换，同上 | 同上 | INTERNAL / 无 |
| AGG-108 标准工时版本 | `standard_work_hour_versions` | 根（每版本一聚合） | 新建 | 配置 | 法人（可空）、生效区间、确认人与时间、依据引用 | D15 配置型 + 法人 × 区间不重叠（INV-116） | 不迁：首版初始化录入，FD-10 五家同值 450 分钟 | INTERNAL / 无 |
| AGG-108 | `standard_work_hour_day_rules` | 次级 | 新建 | 配置 | 日类型、每日标准工时（分钟） | 随版本，生效后不改 | 同上 | INTERNAL / 无 |
| AGG-108 | `standard_work_hour_date_overrides` | 次级 | 新建 | 配置 | 覆盖日期、当日标准工时、原因 | 同上 | 同上 | INTERNAL / 无 |

关系：同 schema 外键：日期表、规则表、覆盖表 → 版本根。跨 BC 按 ID：`legal_entity_id` → AGG-60（B-13）。聚合稿 §4.1 为 B-14 只登记了 → platform.audit 一条边，B-14 → B-13 的法人引用现按「不校验」类型化 ID 存放，见 NOTES 接缝问题 1。被 B-06（工时单最低时数）与 B-27（请假拆分）按 ID 与日期经 api 读取，不订阅。

## 3. B-27 organization.leave（schema 暂名 `organization_leave`，与模块名对应；新建）

聚合稿 §3.2：AGG-69 考勤记录（日常来源企业微信）、AGG-70 请假、AGG-71 假期余额账户、AGG-72 加班、AGG-97 月度考勤确认；依赖 → B-13、B-14、platform.approval、platform.integration。考勤打卡的日常来源为企业微信（保留为外部服务，打卡数据经 platform.integration 防腐层进考勤记录；用户事实 intent 2026-10-16-152603，取代聚合稿 R2 / C-46 的「来源待定」）；外部打卡记录 ID 按 D16 只作来源对照（唯一，限定来源系统），不作主键。

| 聚合 | 表 | 根 / 次级 | 状态 | 数据类别 | 主要列族 | 版本化 | 迁入 | 分级初判 |
|---|---|---|---|---|---|---|---|---|
| AGG-69 | `attendance_records` | 根 | 新建 | 事实（只追加） | 员工、日期与时刻、类型（打卡 / 外勤 / 补卡）、被补记录、来源对照、导入批次 | 只追加（INV-69） | 全量：A8 原始打卡约 79.5 万行、外勤约 3.75 万单（信息图证）；历史来自 A8（迁入，来源对照分来源系统）；日常来自企业微信打卡接口（platform.integration，外部读取登记与超时降级按 §14.3） | 待定（tbd）/ PI；位置类字段可能 SPI |
| AGG-70 | `leave_requests` | 根 | 新建 | 交易 | 请假单号、员工、假期类型、起止时刻、分钟时长、事由、状态、审批引用 | 不需要（状态由审计还原） | 全量：老系统请假档案（信息图证 24,260），`legacy_numbers`；在途按 ADR-0036 第 5 条 | 待定（tbd）/ PI（病假等可能 SPI） |
| AGG-70 | `leave_request_day_splits` | 次级 | 新建 | 事实 | 日期、计入分钟、所用标准工时版本引用、是否工作日 | 不需要（记所用版本，D7） | 全量，随请假行；迁入取当日生效版本，无则拒收 | 同上 |
| AGG-71 | `leave_balance_accounts` | 根（技术聚合） | 新建 | 事实（投影） | 员工、假期类型、年度、应享、已用、占用 | 不需要（投影，可重算并对账，D1） | 迁入请假后重算；来源余额是否存在待取证 | INTERNAL / PI |
| AGG-71 | `leave_balance_holds` | 次级 | 新建 | 事实 | 占用时长、占用状态、占用方单据引用、补休额度行引用（补休类） | 不需要（占用状态变更由审计还原） | 不迁（在途占用按 ADR-0036 第 5 条处理） | INTERNAL / PI |
| AGG-71 | `leave_comp_grants` | 次级 | 新建 | 事实（可重算投影） | 员工、加班单引用、额度时长、到期日、已用 / 占用、状态（有效 / 已转计薪 / 已休完）、转计薪时长与原因 | 不需要（状态由审计还原） | 不迁（历史调休余额是否迁入待取证，不凭推测） | INTERNAL / PI |
| AGG-72 | `overtime_requests` | 根 | 新建 | 交易 | 加班单号、员工、日期与时长、类型、补偿方式（付薪 / 补休）、状态（含已计入补休额度） | 不需要（状态由审计还原） | 全量：来源待取证（认定以考勤与审批为准，Q16） | INTERNAL / PI |
| AGG-97 | `monthly_attendance_confirmations` | 根 | 新建 | 事实（期间快照） | 员工、月份、出勤天数、状态、确认与锁定时间 | 期间快照，锁定后不改（INV-105） | 不迁：旧粒度为月 × 一级部门，无法对应人员 × 月；与「全部历史迁入」的关系待用户 | SENSITIVE / PI（SPI 交 U-11） |
| AGG-97 | `monthly_attendance_confirmation_lines` | 次级 | 新建 | 事实 | 汇总类别（请假按类型 / 加班按补偿方式）、时长、汇入单据引用 | 期间快照 | 同上 | SENSITIVE / PI |
| AGG-97 | `monthly_attendance_adjustments` | 次级 | 新建 | 事实（只追加） | 更正类别、原月份、更正时长、依据 | 只追加 | 同上 | SENSITIVE / PI |
| AGG-118 假期类型与额度规则 | `leave_types` | 根 | 新建 | 参考数据 | 类型码、名称、是否占额度 / 带薪 / 按小时 | 不需要（受控代码表，只由迁移维护，D17 第一类；若在线增改须改版本化） | 不迁：旧假期类型只经映射 | INTERNAL / 无 |
| 同上 | `leave_entitlement_rule_versions` | 版本 | 新建 | 配置 | 生效区间、确认人与时间、依据引用、城市类别、年度应享规则、结转 / 清零规则、补休有效期长度（U15 待用户定） | D15 配置型 | 不迁：首版按现行制度录入 | INTERNAL / 无 |

关系：同 schema：日拆分、占用 → 各自根；`leave_comp_grants` → 加班单（`overtime_requests`，同 BC 按 ID，P13-05）；补休类请假的占用行引用所冲抵的额度行；`leave_requests` → `leave_balance_holds`（按 ID，占用先于单据，额度占用模式）；月度确认明细 → 请假、加班（同 BC 按 ID）；规则版本 → 假期类型。跨 BC：员工 → AGG-62（B-13，api）；`leave_request_day_splits` → AGG-108 的版本 ID（B-14，记所用版本）；日历判断工作日 → AGG-68；审批 → platform.approval 流程实例引用；考勤来源（企业微信）→ platform.integration 防腐层。B-06 工时单读取已批准与待批的请假时长（INV-65）。

## 4. 交 one-data 复核的口径

1. **工作日历是否按配置型版本化**：D17 细则 8 已定「工作日历按配置型版本化，业务管理员四眼确认」，已建 `work_calendar_years` 是可变根 + 整集替换的日期表，没有作者 / 确认人 / 决定出处 / 版本。建议：日期集合作为「修订」只追加，每次维护追加一版带确认人，当年已过日期的修改走二次确认并留版本；请裁定是改形还是以审计承担（若后者须在注册表写豁免理由与出处）。
2. **根表不存可版本化列**：`legal_entities`、`org_units`、`employees` 现根表存名称、状态、负责人、父节点等，版本表再存一遍（D1 冗余；D17 细则 5）。建议改形：根只留标识 / 编码 / 乐观锁 / 事件序号；当前值由版本表按业务日期取，另给「当前值」读模型（不存表）；`employees` 的权限用在职二值保留为同事务写入的投影并有一致性测试（D17 细则 5 前半）。
3. **生效依据改 DECLARED**：`org_unit_versions`、`assignment_versions`、`org_unit_legal_entities` 等基线注册表登记 OBSERVED；改 DECLARED 后，版本表来源由同步改为新系统自有写入（D17 细则 4：一表一来源一负责人）。迁入期版本的生效日取不到时的处理见口径 6。
4. **主任职不重叠（INV-62）**：只对主任职区间不重叠、兼职并行不设上限。建议版本表带主兼标识，用条件排除约束 `EXCLUDE USING gist (employee_id WITH =, daterange(...) WITH &&) WHERE (is_primary)`；兼任走另一条或不加约束。请确认谓词写法与 `assignments` 根 + 版本表的拆法（备选：去掉 `assignments` 根，只留版本表 + 员工 ID）。
5. **性别、邮箱、企业微信 ID 等分级**：现登记 SENSITIVE，ADR-0043 拟改 INTERNAL + PI；本稿按 ADR-0043 初判并在 reason 标注，请 one-data 一并走 ADR-0043 实施 PR；`employees` 若纳入证件号码、出生日期、私人联系方式等（见 NOTES 待用户定 1），应落到 SPI / L3，独立表、独立访问级别。
6. **迁入版本的生效日**：用户已定全量迁入，且版本以人事业务生效日为准（U-12）。A8 的员工异动（信息图证 4,247 条）有异动日期，可作任职与用工状态版本的生效日；组织单元历史、员工入职日与离职日 A8 是否提供待 one-data 取证（ADR-0037 取证登记一行）。取不到业务日期的，不得用同步观察日冒充 DECLARED，须在迁入规格写明取舍并经用户认可。
7. **三口径的核对**：核对结果——聚合稿与信息图的三口径是「劳动合同用人单位 / 数据范围法人（ADR-0026 D-1）/ 个税扣缴义务人」；任务说明举例的社保缴纳主体在 B-15 参保（AGG-74）一侧，不在这三者之内。已建 `employee_legal_entities` 对应「数据范围法人」，不是劳动合同用人单位。请 one-data 在 B-15 的表级稿确认扣缴义务人、参保主体落在哪张表。
8. **外部标识**：`employees.wecom_user_id`（现登 SENSITIVE）在 D16 下只能作来源对照列且限防腐层；建议删除，企业微信 userid 走 `platform_integration.source_references`（来源对象类为员工，已登记为 PI），登录绑定归 platform.identity（Q-03）。
9. **岗位退役与映射**：`positions` 退役，`assignment_versions` 的岗位引用改为职位 + 职级两列；旧岗位 → 新职位 × 职级的映射归 one-data `JOB_MAPPING`，迁入规格引用。
10. **大表**：`attendance_records`（约 79.5 万行起，每年增量大）按 NAMING_STANDARD §2.4a 第 8 条规划分区，主键按 ADR-0048 的分区键例外是否适用请裁定（该例外限只追加审计表）；`leave_request_day_splits` 行数随请假跨度增长。
11. **D17 豁免**：`labor_contracts`、`performance_reviews`、`recruitment_requisitions`、`practice_certificates` 等记「不需要」，依据为「单一状态且审计能完整还原」；`practice_certificates` 与 `leave_types` 是主数据 / 参考数据，请复核豁免是否成立（注册期延续已用追加表承载）。
12. **迁入范围一致性**：本稿所有「全量」均沿用「全部历史迁入」；其中来源对象未取证的（劳动合同、离职申请、加班、证书）只写了「来源待取证」，不得据此推定存在。读取列登记与 M2 对账另按 ADR-0037 办。
13. **词表中的聚合名**：`legal_entity_attribution` 聚合（词表已有）聚合号已补为 AGG-117（one-architecture 2026-10-16），见结构问题 3。

## 5. 交 one-architecture 的结构性问题

摘要（完整见 `NOTES.data.md`「结构问题」）：
1. B-13 自为记录系统的改写后形态：快照来源撤销、sync_* 退役处置、版本表改 DECLARED、`positions` 由职位目录取代（已定事项的落地改写，供汇入过渡 ADR 与聚合稿修订）。
2. AGG-64 由「岗位」一对一改为职位目录，含职位族、职级两维与各自版本化，建议拆分聚合。
3. 已建「法人归属」聚合（词表有、聚合稿无号）的编号。
4. 组织单元表上的 `regional_receivables_manager_id`（区域应收账款负责人）属应收域属性，归属请定。
5. AGG-65 招聘需求在 Q-08 后的保留范围；技能标签与技能目录无聚合；人员档案范围。
6. B-27 内含考勤原始记录与月度考勤确认，名称与范围；假期类型与额度政策无聚合。
7. AGG-106 子单据形状为推断。

## 6. 交 one-glossary 的新用语

摘要（完整含使用范围与 bc 见 `NOTES.data.md`「新用语」）：职位、职位族、职位子族、候选人、面试、录用决定、招聘需求、注册执业证书、注册期、继续教育学时记录、劳动合同、用人单位、绩效评估、用工类型、用工状态、人事业务生效日、标准工时版本、请假、假期类型、假期余额账户、额度占用、加班、考勤记录、补卡、月度考勤确认、更正行、假期额度规则；并核对「人员 / 员工 / 登记员工」三词与表名 `employees` 的关系。


---

<!-- 合并自 parts/b15data.DATA_MODEL.md -->

## B-15 organization.payroll（schema 暂名 `organization_payroll`）

- 状态：起草（b15data，2026-10-16）；全貌级；机器可读片段 `b15data.data-model.yaml`（CONVENTIONS §3，含 `pi` 字段）。
- 现状：`db/migration` 下尚无 `organization_payroll` 基线（只有 `organization_workforce`、`organization_attendance`），B-15 全部表新建；schema 名随 BC 定。
- 依据：聚合稿 AGG-73–76、93、95 行与 §4.1（B-15 → B-10、B-13、B-16、B-19、B-27、platform.approval、platform.integration）；信息图 OBJ-61、62、73、74、92、104；SAMPLE §3（AGG-74、93 已定稿，照用）；D6、D7、D13、D15、D16、D17（SESSION_PLAYBOOK §14.1）；ADR-0036、0043、0026 第 7 项；用户已定：薪酬新系统自算、个税公司代扣代缴、考勤来源企业微信（intent 2026-10-16-152603）；全部历史迁入（U-1–U-4）；退休返聘与实习不参保（T-4）。
- 读法：「迁入」栏的「待取证」= 老系统是否有对应数据、粒度如何，尚未取证，不推定存在；取证按 ADR-0037 登记一行，明细与个人信息不进会话与仓库。分级栏 = 访问级别 / 个人信息标记（ADR-0043）。薪酬是否敏感个人信息交 U-11，拿不准按 SPI（ADR-0043 第 2 项），故个人工资类表登记 spi。
- 与外部聚合的关系：应付只存 B-10 AGG-52 的 ID（法人 × 月一笔实发合计，明细留在 AGG-73）；过账只存 B-16 AGG-77 的 ID；员工、法人、绩效评估、考勤确认、利润分配、会计期间按 ID 引用，不建外键。

| 聚合 | 表 | 状态 | D17 类别 | 版本化与依据 | 迁入 | 分级与理由 |
|---|---|---|---|---|---|---|
| AGG-73 月度薪酬结算 | `payroll_settlements`（根） | 新建 | 交易 | 不需要：单据，状态由审计还原，更正走冲销另立结算（D17 第二类）；业务唯一：法人 × 月 × 正常类型 | 全量；老系统薪酬来源待取证；先迁原行后迁冲销行（ADR-0036 第 3、7 条） | SENSITIVE / 无：法人月度工资汇总；个人明细在行表 |
| AGG-73 | `payroll_settlement_rule_refs`（关联） | 新建 | 事实 | 期间快照：记所用规则版本 ID（D7、D17 细则 6） | 迁入历史结算无规则版本，不造引用 | INTERNAL / 无 |
| AGG-73 | `payroll_settlement_lines`（次级） | 新建 | 事实 | 期间快照，批准后不改；记所用档案 / 参保 / 考勤确认版本 ID（D7） | 全量，待取证；迁入行版本引用可空并标来源 | SENSITIVE / spi：个人工资（个人信息保护法第 28 条、ADR-0043，U-11 ③） |
| AGG-73 | `payroll_line_items`（次级） | 新建 | 事实 | 期间快照；计算明细 / 项目行 | 全量，待取证；粒度不足合并单行并标注 | SENSITIVE / spi，同上 |
| AGG-74 社保与公积金参保 | `insurance_enrollments`（根） | 新建（SAMPLE 已定稿） | 主数据 | 不需要：根只留标识与当前状态，会变的进版本表（D17 细则 5） | 全量，待取证；退休返聘与实习不建行（T-4） | SENSITIVE / pi |
| AGG-74 | `insurance_enrollment_versions`（版本） | 新建（SAMPLE 已定稿，增列族「参保地」「回执引用」） | 主数据 | D6 维度型，DECLARED（办理业务日） | 全量，待取证 | SENSITIVE / spi：缴费基数属个人财产信息 |
| AGG-75 激励方案版本 | `incentive_plans`（根） | 新建 | 主数据 | 不需要：根只留编码与类型 | 全量；多半在制度文件而非结构化数据，待取证 | INTERNAL / 无 |
| AGG-75 | `incentive_plan_versions`（版本） | 新建 | 配置 | D15 配置型（生效区间、确认人、decision_ref，不追溯），生效依据 DECLARED | 全量，待取证；取不到结构化规则时只迁方案名称与生效期并引附件 | INTERNAL / 无 |
| AGG-75 | `incentive_plan_version_quotas`（次级） | 新建 | 配置 | 随版本，生效后不改 | 同上 | INTERNAL / 无：额度到法人 / 组织单元，不到个人 |
| AGG-76 奖金核定 | `bonus_determinations`（根） | 新建 | 交易 | 不需要：单据，状态由审计还原；记所用方案版本 ID（D7） | 全量，待取证；原编号经 `legacy_numbers`（ADR-0036） | SENSITIVE / spi：个人奖金 |
| AGG-76 | `bonus_determination_evidences`（关联） | 新建 | 事实 | 不需要：随核定行 | 全量，随核定行 | SENSITIVE / pi：只存绩效评估 ID |
| AGG-93 人员薪酬档案 | `payroll_profiles`（根） | 新建（SAMPLE 已定稿） | 主数据 | 不需要：根只留所属人员（D17 第四类，个人信息最小化） | 全量，待取证 | SENSITIVE / pi |
| AGG-93 | `payroll_profile_versions`（版本） | 新建（SAMPLE 已定稿，增列族「调薪依据引用」） | 主数据 | D6 维度型，DECLARED；调薪 = 新版本 | 全量；信息图证 OA 调薪申请 #60 只作证据，来源待取证 | SENSITIVE / spi：薪资与发薪账户（金融账户），个人信息保护法第 28 条、ADR-0043 最高访问级别 |
| AGG-120 专项附加扣除声明（S-32 自 AGG-93 拆出） | `payroll_special_deduction_declarations`（根） | 新建 | 主数据 | D6 维度型：年度内可修订，追加新生效区间 | 全量；个税端下载件是否留存待取证 | SENSITIVE / spi |
| 同上 | `payroll_special_deduction_items`（次级） | 新建 | 主数据 | 随申报；记所用扣除标准版本 ID（D7） | 同上 | SENSITIVE / spi：含受益人关系与未成年子女信息（第 28 条，U-11） |
| AGG-95 个税扣缴与社保公积金申报 | `tax_social_filings`（根） | 新建 | 交易 | 不需要：单据，状态由审计还原，更正另立申报（自引用） | 全量；政府端是权威登记，老系统只可能有登记行，待取证，缺口入对账 | SENSITIVE / 无：法人级税费汇总 |
| AGG-95 | `tax_social_filing_lines`（次级） | 新建 | 事实 | 期间快照，提交后不改 | 同上 | SENSITIVE / spi：含证件号码 |
| AGG-95 | `tax_social_filing_receipts`（次级） | 新建 | 事实 | 只追加：外部回执事实 | 同上 | INTERNAL / 无 |
| AGG-95 | `tax_social_payments`（次级） | 新建 | 事实 | 只追加，冲销另记 | 同上 | INTERNAL / 无 |
| AGG-119 | `payroll_rule_versions`（根，每版本一聚合，同 AGG-108 写法） | 新建（提议） | 配置 | D15 配置型 + 类别 × 范围 × 生效区间不重叠 | 不迁：首版按现行法规与公司制度初始化录入；历史版本回补待用户定 | INTERNAL / 无 |
| 同上 | `income_tax_brackets`（次级） | 新建 | 配置 | 随版本 | 同上 | INTERNAL / 无 |
| 同上 | `tax_deduction_standards`（次级） | 新建 | 配置 | 随版本 | 同上 | INTERNAL / 无 |
| 同上 | `contribution_rate_rules`（次级） | 新建 | 配置 | 随版本 | 同上 | INTERNAL / 无 |
| 同上 | `payroll_calculation_parameters`（次级） | 新建 | 配置 | 随版本 | 同上 | INTERNAL / 无 |

表数：24 张（8 个聚合：AGG-73 四、AGG-74 二、AGG-75 三、AGG-76 二、AGG-93 二、AGG-120 二、AGG-95 四、规则版本 五，合计 24），全部新建。

### 关系概要

- 同聚合内外键：行表 → 结算根；项目行 → 薪酬结算行；规则引用 → 结算根；回执、缴款、明细 → 申报根；扣除明细 → 声明根（AGG-120）。
- 同 BC 跨聚合按 ID（D3，不建外键）：声明 → 档案（AGG-120 → AGG-93，按 ID）；结算行 → 档案版本（AGG-93）、参保版本（AGG-74）；项目行 → 奖金核定（AGG-76）；结算 → 规则版本；申报 → 结算（AGG-73）；核定 → 方案版本（AGG-75）。
- 跨 BC 按 ID：B-13（员工 AGG-62、法人 AGG-60、组织单元 AGG-61、绩效评估 AGG-106）；B-27（月度考勤确认 AGG-97）；B-10（应付 AGG-52、利润分配 AGG-109）；B-16（过账 AGG-77）；B-19（会计期间 AGG-81）。
- 业务唯一与排他约束（交 one-data 在 DDL 阶段落）：结算（法人 × 月 × 正常类型）；参保版本（人员 × 区间不重叠）；档案版本（人员 × 区间不重叠）；规则版本（类别 × 范围 × 区间不重叠）；申报（扣缴义务人 × 类别 × 所属期，更正除外）。
- 个人信息保护：spi 表在首次读取、收集或处理前完成影响评估（ADR-0043 第 5 项，U-10）；SPI 与 L3 不进日志、事件、审计 detail 原值、企业微信卡片、分析层明细（ADR-0043 第 4、7 项）；事件只带自有 ID。保留期（D12）与 ADR-0043 R-2 不得 PERMANENT，待 one-data 登记。

### 迁入要点（ADR-0036）

- 用户已定全部历史迁入、生产自成体系；老系统薪酬数据是否存在、粒度如何均「待取证」，下列均为迁入形态设计，不是对来源的断言。
- 历史月度结算只迁结果（行与项目行），不重算；迁入行不带规则版本引用，以来源对照和迁入批次标识（ADR-0036 第 3 条），与总账薪酬类科目做核对（第 7、8 条）。
- 在途：状态为已批准未发放的结算，按 ADR-0036 第 5 条清空或以草稿迁入；不启动流程实例。
- 申报历史以政府端回执为权威，老系统登记行迁入后与回执核对，缺口入对账，不补造回执。


---

<!-- 合并自 parts/vs0911.DATA_MODEL.md -->

## 表级数据模型说明稿 · 计划与预算（B-18）、知识（B-30）

- 状态：起草（vs0911，2026-10-16）；全貌级，不到字段；机器可读片段为同目录 `vs0911.data-model.yaml`（格式以 `closeout/CONVENTIONS.md` §3 为准）。
- 依据：SESSION_PLAYBOOK §14.1（D6、D13、D15、D16、D17）、§14.2（M5）；ENTERPRISE_AGGREGATES_DRAFT（§2.2 B-18、B-30，INV-101、102、115，§4.1）；ENTERPRISE_INFO_MAP_DRAFT（OBJ-100、101、110）；`terms.yaml`；`data-classification.txt`；ADR-0036、0041、0043。
- 已定口径（照用）：生产自成体系、一次投产、全部历史迁入（用户 2026-10-16 U-1–U-4）；配置与版本按 D15（只追加、生效日、确认人、不追溯）；外部 ID 只作来源对照（D16）。
- 状态列：`db/migration` 基线下没有 `operations_planning`、`km_knowledge`，本稿全部为新建。schema 名按 NAMING_STANDARD 为 `operations_planning`、`km_knowledge`，由 one-architecture 在 `contexts.yaml` 登记。

## 1. B-18 operations.planning

| 聚合 | 表 | 根 / 次级 | 数据类别 | 主要列族 | 版本化 | 迁入 | 分级初判 |
|---|---|---|---|---|---|---|---|
| AGG-79 经营计划版本 | `business_plans` | 根 | 主数据 | 计划编码、目标类型、计划周期 | 不需要（根只留标识，D17 细则 5） | 全量；来源待取证（信息图 OBJ-100 记老系统有 `business_plan` 等，无生产量） | internal / 无 |
| AGG-79 | `business_plan_versions` | 版本 | 业务单据版本 | 版本号、状态、基线生效日、确认人与时间、审批实例引用、被取代版本引用 | D15 形状，只追加（基线后行不可改，INV-101） | 全量；已基线版本原样迁为基线；编制中、审批中按当前状态迁入；待取证 | internal / 无 |
| AGG-79 | `plan_target_values` | 次级 | 事实 | 指标、期间、目标值、组织单元、人员 | 不需要（随版本，基线后不可改） | 全量；销售预测在途 1,212 单迁为只读历史（M-8） | internal / pi（按人员的行） |
| AGG-79 | `business_reviews` | 次级 | 业务单据 | 复盘组织单元、复盘期间、复盘类型、所复盘基线版本引用、状态、定稿人与时间 | 不需要（定稿后不可改，状态变迁由审计还原） | 不迁；投产后起记，老系统来源待取证 | internal / pi |
| AGG-79 | `business_review_variance_explanations` | 次级 | 事实 | 指标、偏差快照、偏差解释、记录人与时间 | 不需要（随复盘，定稿后不可改） | 不迁，同上 | internal / pi |
| AGG-79 | `action_items` | 次级 | 事实 | 经营动作、动作类型、责任人、期限、状态、完成说明、来源偏差解释引用、新版本引用 | 不需要（状态变迁由审计还原） | 不迁，同上 | internal / pi |
| AGG-80 预算账户 | `budget_accounts` | 根 | 交易（额度账户） | 账户编码、预算主体、预算期间、当前额度、已占用与已消耗合计、状态、乐观锁 | 不需要（额度变动记在调整表） | 全量；待取证 | internal / 无 |
| AGG-80 | `budget_account_adjustments` | 次级 | 审计类事实（只追加） | 调整额、前后额度、原因、调整人 | 只追加 | 全量；待取证 | internal / 无 |
| AGG-80 | `budget_reservations` | 次级 | 交易 | 占用标识、占用额、状态（占用、消耗、释放）、占用方类型与单据 ID | 不需要（一行一次占用，状态变迁由审计还原） | 全量（已结的占用、消耗、释放行也迁）；待取证 | internal / 无 |

### 口径

1. **计划版本的形状**：D15 原指配置类；经营计划版本是业务单据的版本，但需求相同（只追加、生效日、确认人、不追溯）。本稿 `versioned` 写 `append_only`，并在 reason 说明「基线前的状态变迁由审计还原，基线后不可改」。若 one-architecture 想给「D15 形状的业务版本」单独取值，见 notes 格式问题 2。
2. **类型「预算」与账户**：预算基线是 AGG-79 的类型「预算」版本（OBJ-101 并入 OBJ-100），逐级分解生成 AGG-80 的账户；`budget_accounts` 引用基线版本。账户额度变动只追加到调整表；调整是否同时产生基线新版本，口径不一致，见 notes 结构问题 2。
3. **拦截还是预警（待用户定 1）对表的影响**：
   - 拦截：`budget_accounts` 的当前额度与已占用合计由乐观锁保护，占用与账户在同一事务；孤儿占用（申请方写单据失败）由定时释放与对账兜底（聚合稿「额度占用」模式）；INV-102 成立。
   - 预警：占用行照记，超额不拒绝，超额预警留痕；合计列只作展示；INV-102 改为预警策略。
   - 两种方案表的形状相同，差别在约束与命令前置检查，所以本稿先按同一形状建表。
4. **占用方引用**：`budget_reservations` 的占用方单据 ID（B-11 供方协议、B-12 费用报销）只记录不校验，不进 `references`（B-18 不能依赖下游），见 notes 接缝问题 2。
5. **迁入对账**：账户当前额度 = 基线额度 + 调整合计；已占用 = 状态为占用的占用行合计；已消耗同理。迁入后按账户逐户核对这三个等式，不成立的账户拒收并登记（ADR-0036 第 3、7 条）。
6. **经营复盘与经营动作（PRC-181）**：经营复盘记录先放 AGG-79 作次级（`business_reviews`、`business_review_variance_explanations`），经营动作 `action_items` 改归经营复盘，因动作在基线后仍要登记完成，不能随版本不可改；调整类动作经 `adjusted_plan_version_id` 指向 PRC-88 产出的新版本。偏差数据以 reporting 的经营月报为准，表里只存解释与定稿时快照，不复制报表。老系统是否有复盘记录来源待取证，首期不迁入。是否独立成聚合见 notes 结构问题 11。
6. **暂存**：迁入批次、暂存与拒收由 platform.integration 的迁入登记承担（M5），业务聚合不建暂存表。

## 2. B-30 km.knowledge

| 聚合 | 表 | 根 / 次级 | 数据类别 | 主要列族 | 版本化 | 迁入 | 分级初判 |
|---|---|---|---|---|---|---|---|
| AGG-107 知识条目 | `knowledge_entries` | 根 | 主数据 | 条目编码、类别、当前已发布版本引用 | 不需要（根只留编码与类别） | 迁入集合为空（库内无知识条目实体，OBJ-110 取证）；A8 文档中心 567 份文档是否转条目见待用户定 5 | internal / 无 |
| AGG-107 | `knowledge_entry_versions` | 版本 | 业务单据版本 | 版本号、状态、标题与内容、适用范围、有效期、发布日、被取代版本引用、来源复盘 ID | D15 形状，只追加（已发布不得修改，INV-115） | 同上 | tbd（案例正文可能带客户与项目信息）/ tbd |
| AGG-107 | `knowledge_entry_reviews` | 次级 | 审计类事实（只追加） | 评审结论、意见、评审人、时间 | 只追加 | 同上 | internal / pi |

### 口径

1. **版本在内**：聚合稿写「一对一（版本在内）」，版本放聚合内的版本表；引用方（AGG-03、12、27、103）只存条目 ID 与版本，不在本 BC 建引用表。
2. **外部知识平台文件夹引用不在本 BC 的表里**：D16 要求外部 ID 只作来源对照，且领域模块不出现外部系统名。项目档案上 203 条文件夹 Id（信息图 OBJ-110 取证）迁入 platform.integration 的来源对照，来源系统限定为知识平台；知识条目若要展示外部链接，经来源对照读取，见 notes 接缝问题 5、结构问题 5。
3. **适用范围**：只存业务线、项目类型等取值，不存其他 BC 的聚合 ID（聚合稿）；取值集合是参考数据，类别与适用范围的取值表待补，暂放列族，不另建表（CS-1.8 只实现当前阶段）。
4. **敏感度 tbd**：技术案例正文可能带客户、项目、人员信息；是否要求脱敏、是否限范围可见待用户定 6。
5. **来源复盘 ID**：属 B-04 的 AGG-103，只记录不校验，不进 `references`。


---

<!-- 合并自 parts/b31.DATA_MODEL.md -->

## 表级数据模型说明稿 · B-31 organization.administration（行政事务）

- 状态：起草（b31，2026-10-16）；全貌级，不到字段、不写 DDL；机器可读片段为同目录 `b31.data-model.yaml`（格式以 `closeout/CONVENTIONS.md` §3 为准）。
- 依据：`NOTES.md`「聚合稿回补」第 1 节（AGG-113–116，用户 S-09，intent 2026-10-16-152807）；`OA_PROCESS_INVENTORY.md` 第 111–122 行（A8 模板与用量，只作证据）；SESSION_PLAYBOOK §14.1（D1、D6、D7、D15、D16、D17）、§14.2；ADR-0036、ADR-0043；用户 intent 2026-10-16-152926（IT 资产是固定资产）、144943（A8 行政流程迁入、走通用审批）、133348（一次投产、全部历史迁入）。
- schema 暂名 `organization_administration`（与模块名对应；新建，全部 13 张表为新建，基线无）。
- 读法同人员组 `DATA_MODEL.md`：「状态」全部为新建；「迁入」写来源对象与通道，来源未取证者写「待取证」；A8 早于投产日停用（C-7），在途单据按 ADR-0036 第 5 条 / U-4（切换前审结或撤回，清不完以草稿迁入沿用原编号）；主键一律 UUIDv7，A8 原编号放 `legacy_numbers`，不作主键。

## 1. 表清单

| 聚合 | 表 | 根 / 次级 | 数据类别 | 主要列族 | 版本化 | 迁入 | 分级 / PI |
|---|---|---|---|---|---|---|---|
| AGG-113 用印申请 | `seal_applications` | 根 | 交易 | 印章类别、用印法人、申请人、事由、批准份数、状态、审批实例 ID、提交轮次与 D15 配置版本引用 | 不需要（单据，审计可还原） | 全量；A8 用印申请（累计 11,570、在途 252），字段待取证 | INTERNAL / PI |
| 同上 | `seal_application_documents` | 次级 | 事实 | 文件名称、份数、文件引用 | 不需要（随单据不可变） | 全量，随申请 | INTERNAL / 无 |
| 同上 | `seal_application_source_documents` | 次级 | 事实 | 来源单据类型、来源审批实例 ID、来源载荷份数（经 platform.approval 校验，S-40） | 不需要 | 全量；A8 的关联取得方式待取证 | INTERNAL / 无 |
| 同上 | `seal_usage_records` | 次级 | 事实（只追加） | 实际用印时间、份数、经办人、扫描件引用、对应报价版本、用印法人、拟发文件与实际凭据关系 | 只追加（INV-新-P2） | 全量；A8 是否有逐次登记待取证，无则每单一行汇总 | INTERNAL / PI |
| AGG-114 IT 需求工单 | `it_requests` | 根 | 交易 | 需求类别、描述、优先级、提出人、处理人、处理结论、状态、审批实例 ID、提交轮次与 D15 配置版本引用 | 不需要 | 全量；A8 需求工单（累计 1,622、在途 105），字段待取证 | INTERNAL / PI |
| 同上 | `it_request_handlings` | 次级 | 事实（只追加） | 处理时间、处理人、处理说明 | 只追加 | 全量；逐条记录是否存在待取证 | INTERNAL / PI |
| AGG-115 IT 资产使用记录 | `it_asset_use_ledgers` | 根 | 主数据（技术根） | 资产卡片 ID（唯一）、当前使用人投影 | 不需要（根只留卡片引用） | 首次领用或迁入时建立 | INTERNAL / 无 |
| 同上 | `it_asset_use_requests` | 次级 | 交易 | 申请类型（领用 / 调拨）、使用人、调入使用人、状态、审批实例 ID、提交轮次与 D15 配置版本引用 | 不需要 | 全量；A8 领用（累计 1）、调拨（累计 139），须对应到 B-24 卡片（待取证） | INTERNAL / PI |
| 同上 | `it_asset_use_periods` | 次级 | 事实 | 使用期间起止、使用人、来源申请、归还验收 | 使用期间区间 + 条件排除约束（INV-新-P4） | 全量；来源同上 | INTERNAL / PI |
| AGG-121 IT 资产维修单（S-32 自 AGG-115 拆出） | `it_asset_repair_orders` | 根 | 交易 | 报修人、故障描述、维修结果、完成日期、维修单引用 | 不需要 | 全量；A8 报修（累计 128、在途 2） | INTERNAL / PI |
| AGG-116 员工活动申请 | `employee_activity_requests` | 根 | 交易 | 活动类型、申请人、说明、费用预估引用、状态、历史审批实例/轮次、D15 版本引用、取消原因/人/时间、重登引用 | 不需要 | 全量；A8 周年贺卡（219）、创新项目发布（31）、感恩节活动（2,106，已停用） | INTERNAL / PI |
| 同上 | `employee_activity_participants` | 次级 | 事实 | 参与人 | 不需要 | 全量，随申请 | INTERNAL / PI |
| 同上 | `employee_activity_types` | 次级（参考数据） | 参考数据 | 类型码、名称、状态、规则版本与生效日/批准人/依据 | D15 版本只追加 | 不迁，A8 模板映射到类型码 | INTERNAL / 无 |

## 2. 关系概要

- 同 schema 外键：次级表 → 各自根（子表随根写入）。
- 跨 BC 只按 ID 引用、不建外键：法人 → AGG-60、员工（申请人、经办人、使用人、报修人、处理人、参与人）→ AGG-62（B-13，api）；`it_asset_use_ledgers.fixed_asset_card_id` → AGG-91 资产卡片（B-24，api，B-31 → B-24 新边）。
- 审批：仅现行用印/受控 IT 存审批实例及轮次引用，审批过程在 platform.approval；领用/活动的实例和轮次只保留迁入历史，不再绑定新审批。
- 来源单据：`seal_application_source_documents` 记来源单据的审批实例 ID（来源审批校验不反查来源业务单据；报价版本另作可核验引用），提交时经既有边 B-31 → platform.approval 校验实例已通过、份数取自审批载荷（S-40），不读 B-03 / B-05 等业务单据；B-12 费用报销若引用员工活动申请，也只存申请 ID，B-31 不依赖 B-12。
- 被依赖：按 33e28272，由 REF-04（报价发出编排，暂名，图节点 B-32）→ B-31（api）读取实际用印登记并核验同一报价版本、法人、拟发文件和凭据；读不到或对应不符拒绝发出并告警。B-03 不依赖 B-31，不持用印投影或订阅用印事件；ADR-0057 为提议稿待用户批，正式名称与命名空间待 one-glossary；B-13 的离职交接作读模型或由 B-31 订阅 Separated 生成归还待办。

## 3. 口径与说明

1. **AGG-115 边界**：本稿取「每资产卡片一个根（`it_asset_use_ledgers`）」，使用期间、申请单为次级（维修工单已按 S-32 拆为 AGG-121）。理由：INV-新-P4 跨多张申请单，须在一个事务边界内判断；卡片是天然的一致性键。备选是以「使用期间」作根，但领用 / 调拨 / 归还单据就要分属多个聚合，排他约束无处安放。请 one-architecture 定（NOTES 结构问题 3）。
2. **IT 资产的账务不在此**：原值、折旧、报废、盘点结果在 B-24 AGG-91；B-31 不存金额。维修费用若产生，走采购 / 费控，不记在 `it_asset_repair_orders`。
3. **个人信息**（ADR-0043 第二维）：员工引用均为 PI；本 BC 未发现 SPI。工单描述与用印事由自由文本，可能夹带不宜进入的信息，输入页面应提示，不做自动扫描（待 one-data 定）。
4. **A8 未取证项**：全部迁入字段未取证；在途 252 / 105 / 38 / 4 / 75 等按 ADR-0036 第 5 条 / U-4，不启动审批实例。取证按 ADR-0037 登记一行，明细与个人信息不进会话与仓库。
5. **大小**：全部表按年增量千级以内（A8 用印累计 11,570 为最大），无需分区。

## 4. C9 答复同步的数据口径

- 表仍为原 13 张，不新增对象、表、DDL 或跨 BC 外键。机器可读片段补所用配置版本、轮次、实物交接、报价版本/法人/拟发文件/实际用印凭据关系、离职生效及固定到期时点、周年员工/入职日期/周年日期等列族。此节列族补充优先于上方原表清单的简略列举。
- 用印和 IT 原单重提保留原编号、历史轮次和意见；领用/活动取消的是新单审批路径，历史审批中/已驳回/已撤回、实例、轮次、编号全量保留；ADR-0036 U-4 不改，不因取消审批批量改草稿。
- D15 配置版本只追加，含生效日、作者、批准人、确认时间及依据；按业务日期选唯一有效版，单据存版本引用。正式负责人/加审范围/受控类别/岗位与超配/活动/周年规则及提供维护责任须上线前填，缺失、重叠、无有效版、无法执行或不满足拒绝并告警。仅引用 ARCH-B31-materials 1–8，未把清单当已提供制度。配置具体承载交 one-data 定稿。
- seal_usage_records 保留实际用印和关闭后可核验的报价版本、用印法人、拟发文件及凭据对应，INV-118/119/138 保持；只有批准不算已用印。核验由下游 REF-04 经 api 承担，B-03 无 B-31 依赖；用印已办理事件仅分析。结构查环无环不替代 ADR 批准、入口或业务验收，不在此创建新校验对象。
- it_requests 无受理提醒或代关任务，仅人工确认且有结论关闭；it_asset_use_requests 新领用全部规则通过，历史审批值只保留历史；调拨保留双方交接，同事务结束旧期间和开新期间。
- it_asset_use_periods 结束原因区分调拨/归还；仅已生效 Separated 入口的期限为离职生效后 7 个日历日，完成取消，聚合 ID + 到期时点幂等，晚到/重试不重置；手工入口无期限。调度任务由 platform.scheduling 承载，无新业务状态。
- employee_activity_requests 全部规则登记，无人工批准或金额加审。登记内容不可修改，记错或活动取消从已批准或已完成经取消员工活动登记到提议终态已取消；补取消原因、取消人、取消时间列族并留审计，原登记与完成历史不删除。重登另建记录并存被取消登记引用，自动重复/补跑仍返回原取消结果，显式重登按该引用幂等返回后继登记；取消不是 VS-14.4 正常退出。周年读 B-13 入职日期/人员事实，按业务日自动生成；员工 + 入职日期 + 周年日期 + 活动类型去重，适用、闰日、重新入职、补跑规则须填；employee_activity_types 改 D15 只追加版本。当前无运行时周年触发器；费用/报销/采购自身控制仍有效。
- data-model.people.yaml 与 DATA_MODEL.md 由手工合并维护，rebuild.py 不生成它们；本卡只替换 B-31 段，其他分片逐字不变。
