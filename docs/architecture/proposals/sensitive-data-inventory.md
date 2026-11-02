# 附件：公司 L3 及以上数据清单（ADR-0080）

本表是 ADR-0080 的附件，随 ADR-0080 审批。登记源仍是 `backend/architecture/data-classification.txt`（ADR-0043 第 2 项），本表不是登记源：列建出来时按 ADR-0043 登记，再回填本表「状态」。级别写法：L1–L4 = PUBLIC / INTERNAL / SENSITIVE / RESTRICTED（ADR-0043 第 1 项第 1 条），标记 NONE / PI / SPI（同项；U-13 改名后照改）。只列数据类别与聚合，不含任何行级数据。

列说明：
- **明细可见**：持有该资源 `-sensitive:read`（ADR-0026 第 7 项第 2 条）的候选角色，按 ADR-0080 决定 2.1 逐人双人授予；各阶段实际角色按 CC-P10「各阶段可见角色逐阶段问用户」（`docs/architecture/cross-cutting-matrix.md:124`）在建造卡确认。「本人」= ADR-0080 决定 3 的本人查看。
- **只见合计**：持有合计权限（决定 2.2）或经审批参与看到单据头合计（决定 2.4）的角色。
- **状态**：已登记（行号）/ 规划（聚合号，未建列）/ 候选（是否 L3 待用户 Q1）/ 存疑（标原因）。
- 聚合号与 BC 出自 `docs/architecture/ssot/aggregates/planned.yaml`（生成文件，源 `enterprise-aggregates-draft.md` §2.2）与 ADR-0051 决定 3 表（`docs/adr/ADR-0051.md:49-76`）；BC 写 ADR-0051 的标识，其中 `masterdata.counterparty`、`masterdata.catalog`、`presales.opportunity` 尚未写入 `contexts.yaml`（已核实 `contexts.yaml` 无这三项）。

## A. 已登记的 L3 / L4 行（`data-classification.txt`，main `b9226d1b5`）

这些都是平台与人员标识类数据，不是本 ADR 所说的「业务敏感数据」，列出是为说明它们不另加合计权限。

| # | 数据项 | 现登记 → ADR-0043 判定 | BC | 明细可见 | 只见合计 | 来源 | 状态 |
|---|---|---|---|---|---|---|---|
| A1 | 员工邮箱、性别、姓名、企业微信 ID | SENSITIVE → INTERNAL + PI（U-1 已答，实施 PR 未合入） | organization.workforce | 通讯录 L2（ADR-0026 第 1 项表） | — | 登记第 6、7、19、21 行；ADR-0043:58-61 | 已登记，待改级 |
| A2 | 工号、登录名 | SENSITIVE + PI（U-2 暂不定） | organization.workforce | 现经 `sensitive-response-exceptions.txt` 例外出参 | — | 登记第 20、22 行；ADR-0043:62-63 | 已登记 |
| A3 | 最近登录 IP、审计来源地址 | SENSITIVE + PI | platform.identity、platform.audit | 安全审计员（ADR-0026 第 1 项表审计行，D-11） | — | 登记第 8、18 行；ADR-0043:64-65 | 已登记 |
| A4 | 安全材料（state、票据摘要、TOTP 计步、密钥引用、应急账号名、浏览器标识摘要） | SENSITIVE + NONE / PI | platform.identity | 无人读（不出参） | — | 登记第 11–14、16、25 行；ADR-0043:69-74 | 已登记 |
| A5 | 口令哈希、TOTP 密钥、会话内容、会话标识与主键 | RESTRICTED（L4） | platform.identity | 不出库（ADR-0026 第 7 项第 1 条） | — | 登记第 9、10、15、32、33 行 | 已登记 |
| A6 | 会话主体、收件人代码 | SENSITIVE → 代理键 / INTERNAL + PI（U-4） | platform.identity、platform.integration | — | — | 登记第 17、23、24 行；ADR-0043:66-68 | 已登记，待改级 |

## B. 人力资源（规划，ADR-0079 数据归属）

| # | 数据项 | 级别 + 标记 | BC | 明细可见 | 只见合计 | 来源 | 状态 |
|---|---|---|---|---|---|---|---|
| B1 | 薪资档案：约定工资及历次调薪（AGG-93） | L3 + PI（SPI 待 U-11，拿不准按 SPI） | organization.payroll | 薪酬管理专员；本人 | 薪资审批人（单据头） | ADR-0079:78；ADR-0043:81（U-5）；ADR-0026 第 7 项第 1 条 | 规划 |
| B2 | 月度薪酬结算的人员薪酬行、工资条（AGG-73） | L3 + PI（同上） | organization.payroll | 薪酬管理专员；本人（工资条） | 薪资审批人：法人 × 月合计；财务：应付薪酬一笔（B19） | ADR-0079:78、83；用户 intent `2026-11-02-171539` | 规划 |
| B3 | 社保公积金缴费基数与个人、单位缴费额（AGG-74） | L3 + PI | organization.payroll | 薪酬管理专员；人事专员（增减员，存疑：是否需金额）；本人 | 财务：缴款合计（AGG-95） | ADR-0079:78；planned.yaml AGG-74 | 规划；人事专员是否见基数待卡 |
| B4 | 个税预扣明细与申报（AGG-95 个人部分） | L3 + PI | organization.payroll | 薪酬管理专员；本人 | 财务：缴款合计 | ADR-0079:78 | 规划 |
| B5 | 专项附加扣除声明（含配偶、子女、老人信息，AGG-120） | L3 + SPI（含不满十四周岁） | organization.payroll | 薪酬管理专员；本人（自己填报） | — | ADR-0079:118（不变量 8）；planned.yaml AGG-120 | 规划；受益人表与级别交 U-11 |
| B6 | 激励方案与个人奖金核定（AGG-75、76） | L3 + PI | organization.payroll | 薪酬管理专员；本人（自己的奖金） | 奖金审批人：合计 | ADR-0079:78（C17） | 规划；方案规则本身是否 L3 存疑（规则不含个人值时可 L2） |
| B7 | 证件号（含民族、户籍） | L3 + SPI | organization.workforce | 人事专员；薪酬管理专员经 workforce api 读（个税、社保）；本人 | — | ADR-0079:75、118；ADR-0043:79 | 规划；受 U-14 阻塞；字段加密见 Q4 |
| B8 | 员工本人银行账户 / 工资卡 | L3 + SPI（金融账户） | organization.workforce | 人事专员（登记）；薪酬管理专员与代发经办人经 api 读（ADR-0079:83）；本人 | — | ADR-0079:75、83；ADR-0043:80 | 规划；代发经办人见 Q3；字段加密见 Q4 |
| B9 | 私人手机、私人邮箱、住址、生日 | L3 + PI | organization.workforce | 人事专员；本人 | — | ADR-0043:78（U-5）；ADR-0026 第 7 项第 1 条 | 规划 |
| B10 | 医疗期、病假凭证、体检、职业病、工伤、孕产信息（只存日期、状态与文件 ID） | L3 + SPI | organization.workforce、organization.leave | 人事专员（工伤、医疗期）；考勤管理员（病假凭证，存疑）；本人 | — | ADR-0079:118（不变量 8）、C21 | 规划；考勤管理员是否见凭证待卡 |
| B11 | 定位打卡、外勤定位轨迹 | L3 + SPI（行踪轨迹） | organization.leave | 考勤管理员（存疑）；本人 | 部门负责人：出勤统计（不含位置） | ADR-0079:118 | 规划 |
| B12 | 个人实际人工成本、日薪（按人 × 项目的人工成本分配行，AGG-41） | L3 + PI（派生自薪资） | cost.costing | 成本会计（候选）；项目经理只见按职级标准费率计的个人行 | 项目经理、部门负责人：项目、部门人工成本合计 | ADR-0026 第 7 项第 1 条（日薪）；CC-B06 用户原话「按职级标准费率看到个人行」（`cross-cutting-matrix.md:186`） | 规划；存疑：合计在小项目上可反推个人（ADR-0080 Q2） |
| B13 | 绩效评估结论（AGG-106） | 候选 L3 + PI | organization.workforce | 评估人、人事专员；本人 | 部门负责人：分布 | planned.yaml AGG-106 | 候选（Q1） |
| B14 | 解除与违纪的依据、证据 | 候选 L3 + PI | organization.workforce | 人事专员；本人 | — | ADR-0079 不变量 10、C20 | 候选（Q1） |

## C. 业务与财务（规划；除 C1、C3 外是否 L3 待用户 Q1）

| # | 数据项 | 级别 + 标记（建议） | BC | 明细可见 | 只见合计 | 来源 | 状态 |
|---|---|---|---|---|---|---|---|
| C1 | 标准成本费率表（职级 × 薪档 × 城市类别，AGG-05） | L3 + NONE | masterdata.catalog | 定价管理、财务；项目经理只见本项目个人行所用费率（CC-B06） | — | ADR-0026 第 7 项第 1 条（费率）；planned.yaml AGG-05 | 规划（W1，ADR-0054:57） |
| C2 | 计费系数版本、协议价目（AGG-06、07） | 建议 L2 + NONE（对客户报价用，销售广泛使用） | masterdata.catalog | 范围内全部 | — | planned.yaml AGG-06、07 | 候选（Q1） |
| C3 | 报价版本的成本估算与毛利（AGG-12） | L3 + NONE | presales.opportunity | 报价编制人、售前负责人、财务 | 报价审批人：毛利率与总价（单据头） | CC-P10「报价成本与毛利、费率与日薪」（`cross-cutting-matrix.md:124`） | 规划（W2） |
| C4 | 投标报价（开标前，AGG-11） | 建议 L3 + NONE（开标后降 L2） | presales.opportunity | 投标负责人、售前负责人 | — | planned.yaml AGG-11 | 候选（Q1）；按时点降级是否可行待卡 |
| C5 | 合同金额与付款条件（AGG-13） | 建议 L2 + NONE，按数据范围（I-10） | contract.agreement | 范围内 | — | planned.yaml AGG-13 | 候选（Q1） |
| C6 | 往来单位银行账户、证件号码（AGG-01） | L3 + NONE（法人）/ PI（自然人往来方） | masterdata.counterparty | 出纳、主数据管理员 | — | ADR-0026 第 7 项第 1 条（银行信息）；planned.yaml AGG-01 | 规划（W1）；自然人往来方的标记存疑 |
| C7 | 分包与采购合同金额（AGG-55、58） | 建议 L2 + NONE，按数据范围 | procurement.subcontracting | 范围内 | — | planned.yaml AGG-55 | 候选（Q1） |
| C8 | 合伙人出资（AGG-54） | 建议 L3 + PI（自然人合伙人） | payables.payment | 财务负责人 | — | planned.yaml AGG-54 | 候选（Q1） |
| C9 | 利润分配（AGG-109） | 建议 L3 + PI | payables.payment | 财务负责人 | — | planned.yaml AGG-109 | 候选（Q1） |
| C10 | 应付薪酬（法人 × 月一笔，AGG-52） | L3 合计（不含个人行） | payables.payment | — | 财务、出纳 | ADR-0079:83（明细留 payroll） | 规划（W4） |
| C11 | 薪酬过账与会计分录（AGG-77、84） | 合计：过账到法人 × 部门 × 科目，不带个人行 | operations.facts、accounting.ledger | — | 财务 | ADR-0079:83；ADR-0080 决定 2.5 | 规划；过账粒度待薪酬卡核对 |
| C12 | 银行流水（AGG-88） | 建议 L2 + NONE，财务范围；代发只出一笔 | accounting.cashbank | 财务、出纳 | — | planned.yaml AGG-88 | 候选；代发按笔回单是否入流水存疑（Q3） |
| C13 | 经营计划目标、预算账户（AGG-79、80） | 建议 L2 | operations.planning | 范围内 | — | planned.yaml AGG-79、80 | 候选（Q1） |
| C14 | 费用报销、差旅（AGG-59、131） | 建议 L2 + PI（报销人），按数据范围 | expense.spend | 本人、审批人、财务 | — | planned.yaml AGG-59 | 候选 |

## D. 分析层

ADR-0025 第 5 条：6e 的薪酬、人事类敏感对象不镜像（`docs/adr/ADR-0025.md:141`）；第 4 条：敏感事实须有专门权限（`:120`）。B 组明细不进 `reporting`；B1–B4、B12 只以满足 ADR-0080 Q2 最少人数的合计进入。ADR-0043 U-8（PI 是否进分析层明细）仍暂不定，按 D13 字面（ADR-0043:122）。

## 未核实与待补

- C 组除 C1、C3、C6 外，是否 L3 无用户决定（Q1）。
- B3、B10、B11 中人事专员、考勤管理员是否需要看到金额或凭证，待各建造卡按最小必要定。
- 聚合稿未列字段级属性，本表按聚合「内含」推定数据项；字段以各 BC 数据模型为准。
