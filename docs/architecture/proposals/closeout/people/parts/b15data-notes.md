# b15data 笔记（B-15 organization.payroll 表级数据模型）

## 接缝问题

1. AGG-76 → AGG-73 的「已发放」：奖金核定状态「已发放（经月度薪酬结算）」由 AGG-73 的「薪酬已发放」同 BC 事件置位；表上不存结算引用，只在薪酬项目行存奖金核定 ID。聚合稿 AGG-76 的引用列无「订阅 AGG-73」，同 BC 无需边，但应补一行事件订阅说明。
2. 个税申报需证件号码，B-13 `employees` 目前不含证件号（B-13 NOTES 待用户定 1 未决）。本稿让 `tax_social_filing_lines` 在生成时快照证件类型与号码（列级 SPI）。若人员档案不收证件号，B-15 无处可取；B-15 → B-13 的 api 读取证件号须待该题定后补。
3. 发薪：AGG-52 应付薪酬是法人 × 月一笔实发合计，收款方是代发银行或代发批次，而 B-10 付款要求收款方为往来单位账户；员工发薪账户在 AGG-93 版本，不进 B-10。银行代发明细文件由谁生成、是否经 platform.integration，聚合稿未写；见待用户定 5。
4. AGG-95 订阅 AGG-109「利润分配已支付」：聚合稿写「沿已有边 B-15 → B-10」。CONVENTIONS §2 规则 2 字面「event 由发布方 → 订阅方」，而 §4.1 的箭头是「依赖方 → 被依赖方」，同一事件方向两种读法。本稿 `cross_bc` 无需写，但请 one-architecture 在 CONVENTIONS 写明事件的箭头语义。
5. AGG-73 读 AGG-97「月度考勤已锁定」：B-15 → B-27 边存在（api、事件），无问题。AGG-95 读 AGG-81 会计期间：B-15 → B-19 存在。均已核对。
6. 扣缴义务人是三口径之一：`payroll_profile_versions.withholding_legal_entity_id` 与 `payroll_settlement_lines.withholding_legal_entity_id` 分别在档案与结算行，结算行是批准时快照；PRC-145 预警读档案版本，不读结算行。无新边。

## 结构问题

1. 「薪酬规则版本」聚合号：个税税率表、起征点与专项附加扣除标准、社保公积金比例与基数上下限、计算口径参数，配置型 D15，每版本一聚合（同 AGG-108 写法），5 张表。依据：薪酬自算后聚合稿 AGG-73 对「薪酬汇总导入」依赖作废，缺规则配置（人员组 NOTES 铺开备忘）。请 one-architecture 给号，并在 AGG-73、AGG-93、AGG-74、AGG-95 的引用列补「薪酬规则版本（api）」；聚合稿 B-15 聚合数 6 → 7。本稿 aggregate 写「AGG-119」。
2. 规则版本是否一类一表：本稿用一个 `payroll_rule_versions`（带规则类别）+ 四张子表，一版本只含一类，使个税法修订与各地社保年度调整可独立生效。备选：四类各自一个根（四个聚合）。推荐前者；请 one-architecture 与 one-data 确认。
3. 迁入历史结算无规则版本：全部历史迁入但无法复算，历史结算行、项目行只迁结果，所用版本引用留空并标「迁入结果」。D7 要求用到可配置规则时记版本 ID，此处是迁入行的例外，需 one-data 确认不违反守护；备选：给迁入行一条「迁入占位规则版本」。
4. 专项附加扣除拆表：声明（年度）+ 明细两表，放 AGG-93。明细含受益人（含未成年子女）信息，个人信息保护法第 28 条明列未成年人信息为敏感；是否抽到独立表、独立访问级别，交 U-11 与 one-data。
5. AGG-73 的聚合边界：薪酬结算行与项目行同聚合（单据、次级），一法人一月可达数百至千行，批准前整体修改；若行数使乐观锁冲突频繁，需拆「按人员的计算」子聚合。现按全貌级保持一个聚合，规模待 one-data 评估（NAMING_STANDARD §2.4a 第 8 条）。
6. AGG-73 的发放登记：`payroll_settlements` 内含发放日与银行回单引用；标记薪酬已发放为聚合命令。若银行代发需要批次与退票粒度（退票时付款失败、薪酬保持已批准，聚合稿阶段 6），可能要独立「代发批次」次级表，待待用户定 5。
7. AGG-95 的「更正」：用自引用 `corrects_filing_id` 另立申报，而非原申报加更正行；与聚合稿「申报、缴款、更正」三类一致，请确认。
8. AGG-75 的个人额度：方案版本额度到法人 / 组织单元，不到个人；个人额度即 AGG-76 的核定金额。若方案需提取比例累计（销售提成）跨期结转，需另立累计表，待方案梳理。
9. 引用 AGG-61 组织单元：额度表引用 B-13 AGG-61，B-15 → B-13 边存在，但聚合稿 AGG-75 引用列只写 platform.approval，需补。

## 新用语

| 中文名 | 建议英文名 | 使用范围 | bc | 说明 |
|---|---|---|---|---|
| 月度薪酬结算 | payroll_settlement | 领域 | organization.payroll | 聚合稿 AGG-73；词表查无 |
| 薪酬结算行 | payroll_settlement_line（PayrollSettlementLine，one-glossary 定名（PR #572），原称「薪酬行」） | 领域 | organization.payroll | 聚合稿「人员薪酬行（工资条是单据）」；「工资条」是对员工的展示单据，不另登记聚合 |
| 薪酬项目行 | payroll_line_item | 领域 | organization.payroll | 计算明细 |
| 参保 | insurance_enrollment | 领域 | organization.payroll | AGG-74；表名 SAMPLE 已定 |
| 激励方案 / 激励方案版本 | incentive_plan / incentive_plan_version | 领域 | organization.payroll | AGG-75 |
| 奖金核定 | bonus_determination | 领域 | organization.payroll | AGG-76 |
| 人员薪酬档案 | payroll_profile | 领域 | organization.payroll | AGG-93；「薪档」已有词（standard cost rate 定价键，cost.costing），注意区分 |
| 专项附加扣除 | special_additional_deduction | 领域 | organization.payroll | 法定名；声明 `payroll_special_deduction_declaration` |
| 个税扣缴义务人 | withholding_legal_entity（列名）| 领域 | organization.payroll | 三口径之一，已见于 NOTES 与 SAMPLE |
| 个税与社保公积金申报 | tax_social_filing | 领域 | organization.payroll | AGG-95；表名暂用 filing，不叫 return，与 B-18 法定申报 AGG-90 区分，建议 one-glossary 统一「申报」一词 |
| 申报回执 | filing_receipt | 领域 | organization.payroll | 外部权威回执 |
| 缴款登记 | tax_social_payment | 领域 | organization.payroll | |
| 薪酬规则版本 | payroll_rule_version | 领域 | organization.payroll | 提议新聚合 |
| 个税税率表 / 级距 | income_tax_bracket | 领域 | organization.payroll | 法定参数 |
| 扣除标准 | tax_deduction_standard | 领域 | organization.payroll | |
| 缴费比例规则 | contribution_rate_rule | 领域 | organization.payroll | 社保公积金比例与基数上下限 |
| 薪酬计算参数 | payroll_calculation_parameter | 领域 | organization.payroll | 月计薪天数、加班倍数等 |
| 〔缺〕命令 | — | — | — | 聚合稿 AGG-75、76、93、95 命令列已有；规则版本的「编制 / 生效薪酬规则版本」命令与事件「薪酬规则版本已生效」〔缺〕，随聚合号补 |

「同一 bc 内中英文名都查重」：词表中无薪酬、社保、个税相关词（只有「薪档」「标准费率」），B-15 内未发现重名。

## 格式问题

1. `versioned` 取值无「配置型（D15）」：规则 / 方案版本用 `effective_dated`，在 reason 注「D15 配置型」，与人员组 AGG-108 同。建议 CONVENTIONS 增 `config_versioned`（或把 D15 并入 effective_dated 并加 `basis` 字段）。
2. 引用未编号聚合：`references.to` 写「AGG-119」，字符串非 AGG-NN；发号后替换。
3. 次级表随版本生效区间由所属根承担，`versioned` 只能填 none，容易与「根不存可版本化列」的 none 混淆；建议加 `follows_parent`。
4. 同 BC 跨聚合引用不写 `cross_bc`（CONVENTIONS 规则），但同聚合自引用（`reverses_settlement_id`、`corrects_filing_id`）写法未规定，本稿按同聚合外键写。
5. `pi` 字段缺「受限于 U-11」的承载位置：本稿对工资类表按 ADR-0043 第 2 项登记 spi，U-11 ③ 确认为 pi 后统一降级；请 CONVENTIONS 增 `pi_pending`（或沿用 tbd + reason）。

## 待用户定

1. 个税与社保公积金申报方式（最小形态已按「系统生成申报数据、回执与缴款登记回写」建表）：
   a. 系统生成申报数据，人工到个税扣缴端 / 社保公积金端申报，回执与缴款回写（现表）；
   b. 经 platform.integration 对接扣缴端接口，系统直接申报；
   c. 委托第三方代理申报，系统收数据与回执。
   推荐 a 起步（CS-1.8 只实现当前阶段），表结构 a/b 通用，b 只多一个外部回执来源；依据 intent 2026-10-16-152603、聚合稿 §4.3.2 阶段 5 的 `platform.integration`（申报端）边。
2. 迁入历史结算是否回补历史规则版本：选项 (a) 只迁结果、不回补（推荐，依据：历史月结已发放，复算无业务用途，且老系统规则未取证）；(b) 回补 2019 年以来个税与各地社保规则版本，使历史可复算。
3. 老系统薪酬数据的取证（交 one-data，ADR-0037 取证登记一行）：是否有月度薪酬、参保、薪酬档案、奖金、个税申报的结构化数据，粒度（人员 × 月 / 法人 × 月 / 汇总）、起止年份、与总账薪酬科目的对账口径。本稿全部写「待取证」，取证前不能确定 `payroll_settlement_lines` 是否能全量迁入。
4. 专项附加扣除的受益人信息（含未成年子女）：选项 (a) 作为 AGG-93 同表列、SPI（现状）；(b) 只存扣除类别与月额，不存受益人姓名与证件，受益人信息留在个税端（推荐，最小化，个人信息保护法第 6 条）；(c) 独立表独立访问级别。依据：个人信息保护法第 28 条、ADR-0043 U-11。
5. 银行代发：选项 (a) 系统导出代发文件，财务到网银上传，回单登记发放；(b) 经 platform.integration 接入银行；(c) 员工银行账户不入库，由财务网银维护。账户已按 SAMPLE 放在 `payroll_profile_versions`（用户已定样板）；此问只问代发动作。推荐 (a)。
6. 薪酬计算口径参数（月计薪天数、加班费倍数、请假扣款口径、试用期、病假工资比例等）：需人力与财务给出现行制度；缺此，规则版本表只有骨架。
7. 规则版本变更是否四眼确认（同 D17 细则 8 的工作日历）：推荐是。
8. 个税申报用证件号码来自何处：与 B-13 NOTES 待用户定 1（人员档案范围）联动。选项：(a) B-13 `employees` 或独立档案表收证件号，B-15 申报时读取（api）；(b) B-15 自存（本稿现状：申报行快照）。推荐 (a)，申报行只留一份快照用于更正追溯。
9. 保留期（D12、ADR-0043 R-2）：薪酬、参保、申报明细按劳动法与税法的保存年限登记，不得 PERMANENT；交 one-data 与法务（U-11），本稿不定年限。
