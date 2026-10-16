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
| AGG-71 | `leave_balance_holds` | 次级 | 新建 | 事实 | 占用时长、占用状态、占用方单据引用 | 不需要（占用状态变更由审计还原） | 不迁（在途占用按 ADR-0036 第 5 条处理） | INTERNAL / PI |
| AGG-72 | `overtime_requests` | 根 | 新建 | 交易 | 加班单号、员工、日期与时长、类型、补偿方式、状态 | 不需要（状态由审计还原） | 全量：来源待取证（认定以考勤与审批为准，Q16） | INTERNAL / PI |
| AGG-97 | `monthly_attendance_confirmations` | 根 | 新建 | 事实（期间快照） | 员工、月份、出勤天数、状态、确认与锁定时间 | 期间快照，锁定后不改（INV-105） | 不迁：旧粒度为月 × 一级部门，无法对应人员 × 月；与「全部历史迁入」的关系待用户 | SENSITIVE / PI（SPI 交 U-11） |
| AGG-97 | `monthly_attendance_confirmation_lines` | 次级 | 新建 | 事实 | 汇总类别（请假按类型 / 加班按补偿方式）、时长、汇入单据引用 | 期间快照 | 同上 | SENSITIVE / PI |
| AGG-97 | `monthly_attendance_adjustments` | 次级 | 新建 | 事实（只追加） | 更正类别、原月份、更正时长、依据 | 只追加 | 同上 | SENSITIVE / PI |
| AGG-118 假期类型与额度规则 | `leave_types` | 根 | 新建 | 参考数据 | 类型码、名称、是否占额度 / 带薪 / 按小时 | 不需要（受控代码表，只由迁移维护，D17 第一类；若在线增改须改版本化） | 不迁：旧假期类型只经映射 | INTERNAL / 无 |
| 同上 | `leave_entitlement_rule_versions` | 版本 | 新建 | 配置 | 生效区间、确认人与时间、依据引用、城市类别、年度应享规则、结转 / 清零规则 | D15 配置型 | 不迁：首版按现行制度录入 | INTERNAL / 无 |

关系：同 schema：日拆分、占用 → 各自根；`leave_requests` → `leave_balance_holds`（按 ID，占用先于单据，额度占用模式）；月度确认明细 → 请假、加班（同 BC 按 ID）；规则版本 → 假期类型。跨 BC：员工 → AGG-62（B-13，api）；`leave_request_day_splits` → AGG-108 的版本 ID（B-14，记所用版本）；日历判断工作日 → AGG-68；审批 → platform.approval 流程实例引用；考勤来源（企业微信）→ platform.integration 防腐层。B-06 工时单读取已批准与待批的请假时长（INV-65）。

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
