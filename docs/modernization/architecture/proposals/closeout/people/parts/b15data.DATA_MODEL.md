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
| AGG-93 | `payroll_comp_leave_conversions`（次级，S-41） | 新建 | 事实 | 只追加：补休转加班工资登记，按额度行 ID 幂等，不存金额 | 不迁：迁入不触发（ADR-0036 M7），迁入补休余额待取证 | SENSITIVE / pi |
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

表数：25 张（8 个聚合：AGG-73 四、AGG-74 二、AGG-75 三、AGG-76 二、AGG-93 三、AGG-120 二、AGG-95 四、规则版本 五，合计 25；AGG-93 增 S-41 的补休转加班工资登记），全部新建。

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
