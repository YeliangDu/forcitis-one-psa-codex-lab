# 附件：公司 L3 及以上数据清单与后续卡（ADR-0080）

本表是 ADR-0080 的附件，随 ADR-0080 于 2026-11-02 采纳（intent `2026-11-02-184918-one-lead-8题采纳推荐.md`，Q1–Q8 全按推荐）。登记源仍是 `backend/architecture/data-classification.txt`（四段格式，ADR-0043 第 2 项），本表不是登记源：列建出来时按 ADR-0043 登记，再回填「状态」。级别：L1–L4 = PUBLIC / INTERNAL / SENSITIVE / RESTRICTED；标记 NONE / PI / SPI。只列数据类别与聚合，不含行级数据。

列说明：
- **明细可见**：持有该资源 `-sensitive:read`（ADR-0026 第 7 项第 2 条）的候选角色，按 ADR-0080 决定 2.1 逐人双人授予；实际角色由建造卡逐阶段问用户（CC-P10，`docs/architecture/cross-cutting-matrix.md:124`）。「本人」= ADR-0080 决定 3。
- **只见合计**：持合计权限（决定 2.2）或经审批参与看到单据头合计（决定 2.4）的角色；均受 k = 5（决定 2.3，Q2 (b)）约束；少于 5 人的薪酬审批处理人可见金额并留审计。
- **状态**：已登记（行号）/ 规划（聚合号）/ 存疑。C 组级别按 Q1 答复（ADR-0080 决定 1）。
- 聚合号与 BC 出自 `docs/architecture/ssot/aggregates/planned.yaml` 与 ADR-0051 决定 3 表（`docs/adr/ADR-0051.md:49-76`）；`masterdata.counterparty`、`masterdata.catalog`、`presales.opportunity` 尚未写入 `contexts.yaml`（已核实）。
- 标记依据：工资、日薪为 PI（intent `2026-11-02-143550` 的 HR 敏感个人信息清单不含工资，ADR-0080 决定 7.2）；SPI 项按 U-14 (c) 分项放行（intent `2026-11-02-163040`），建列前先做影响评估并告知，评估报告路径写入登记理由（ADR-0043 R-4）。

## A. 已登记的 L3 / L4 行（`data-classification.txt`，b60842623）

均为平台与人员标识类数据，不另设合计权限。

| # | 数据项 | 登记 | BC | 明细可见 | 来源 | 状态 |
|---|---|---|---|---|---|---|
| A1 | 登录名 | SENSITIVE + PI | organization.workforce | 现无出参（`sensitive-response-exceptions.txt` 无此项） | 登记第 24 行；不在 intent `162028` 内，维持 | 已登记 |
| A2 | 最近登录 IP、审计来源地址 | SENSITIVE + PI | platform.identity、platform.audit | 本人（本人登录记录）；安全审计员（例外文件第 3、4 行） | 登记第 12、22 行 | 已登记 |
| A3 | 安全材料（TOTP 计步、state 摘要、票据摘要、密钥引用、应急账号名、浏览器标识摘要） | SENSITIVE + NONE / PI | platform.identity | 无人读 | 登记第 15–18、20、29 行 | 已登记 |
| A4 | 会话主体 | SENSITIVE + NONE（豁免，代理键） | platform.identity | 无人读 | 登记第 21 行 | 已登记 |
| A5 | 口令哈希、TOTP 密钥、会话内容、会话标识与主键 | RESTRICTED | platform.identity | 不出库 | 登记第 13、14、19、36、37 行 | 已登记 |

工号（第 26 行）、邮箱、性别、姓名、企业微信 ID、收件人代码已登 INTERNAL + PI，不在本表。

## B. 人力资源（规划，ADR-0079 数据归属）

| # | 数据项 | 级别 + 标记 | BC | 明细可见 | 只见合计 | 来源 | 状态 |
|---|---|---|---|---|---|---|---|
| B1 | 薪资档案：约定工资及历次调薪（AGG-93） | L3 + PI | organization.payroll | 薪酬管理专员；本人 | 调薪审批：按决定 2.4 | ADR-0079:78；ADR-0026 第 7 项第 1 条 | 规划（W4） |
| B2 | 月度薪酬行、工资条（AGG-73） | L3 + PI | organization.payroll | 薪酬管理专员；本人 | 薪资审批人：法人 × 月合计；财务：应付薪酬一笔（C10） | ADR-0079:78、83；intent `2026-11-02-171539` | 规划（W4） |
| B3 | 社保公积金基数与缴费额（AGG-74） | L3 + PI | organization.payroll | 薪酬管理专员；本人 | 财务：缴款合计 | planned.yaml AGG-74 | 规划；人事专员是否见基数存疑 |
| B4 | 个税预扣明细（AGG-95 个人部分） | L3 + PI | organization.payroll | 薪酬管理专员；本人 | 财务：缴款合计 | ADR-0079:78 | 规划 |
| B5 | 专项附加扣除声明（含配偶、子女、老人，AGG-120） | L3 + SPI | organization.payroll | 薪酬管理专员；本人 | — | ADR-0079:118；intent `163040`（不满十四周岁须监护人确认，不收大病医疗） | 规划 |
| B6 | 个人奖金核定（AGG-76）；激励方案规则（AGG-75） | 奖金 L3 + PI；规则不含个人值时 L2 | organization.payroll | 薪酬管理专员；本人 | 奖金审批：按决定 2.4 | ADR-0079:78（C17） | 规划 |
| B7 | 证件号（不收民族、户籍，除非有规章） | L3 + SPI | organization.workforce | 人事专员；薪酬管理专员经 api 读（个税、社保）；本人 | — | ADR-0079:75、118；intent `163040` | 规划；字段加密（决定 6.2） |
| B8 | 员工本人银行账户 / 工资卡 | L3 + SPI | organization.workforce | 人事专员（登记）；payroll 与 payables 经 workforce api 读（ADR-0079:83），payables 侧限出纳凭代发明细导出权限（决定 7.5）；本人 | — | ADR-0079:75、83；ADR-0043:80 | 规划；字段加密（决定 6.2） |
| B9 | 私人手机、私人邮箱、住址、生日 | L3 + PI | organization.workforce | 人事专员；本人 | — | ADR-0043:78 | 规划 |
| B10 | 医疗期、病假证明与期间、体检、职业病、工伤、孕产（只存日期、状态、文件 ID） | L3 + SPI | organization.workforce、organization.leave | 人事专员；考勤管理员（病假证明，存疑）；本人 | — | ADR-0079:118、C21；intent `163040`（病假只存证明与期间） | 规划 |
| B11 | 定位打卡、外勤定位轨迹 | L3 + SPI | organization.leave | 考勤管理员（存疑）；本人 | 部门负责人：出勤统计（不含位置） | ADR-0079:118；intent `163040`（须规章、限工作时间） | 规划 |
| B12 | 日薪、按人 × 项目的人工成本分配行（AGG-41）、按人薪酬过账事实（AGG-77） | L3 + PI（同 B1） | cost.costing、operations.facts | 成本核算系统任务；项目经理只见按职级标准费率计的个人行；如要让成本会计等人员看按人分配行，属偏离「只有薪酬管理专员可见」，须另问用户 | 项目经理、部门负责人：项目真实人工成本合计（受 k） | ADR-0043:81；intent `2026-10-14-222541`；planned.yaml AGG-41 | 规划；总账按标准成本法（决定 2.5，待财务确认） |
| B13 | 绩效评估结论（AGG-106） | 候选 L3 + PI（Q1 未涉及，建卡时问） | organization.workforce | 评估人、人事专员；本人 | 部门负责人：分布 | planned.yaml AGG-106 | 候选，建卡时问用户 |
| B14 | 解除与违纪的依据、证据 | 候选 L3 + PI（Q1 未涉及，建卡时问） | organization.workforce | 人事专员；本人 | — | ADR-0079 不变量 10、C20 | 候选，建卡时问用户 |
| B15 | 生物识别打卡（人脸、指纹模板） | L3 + SPI | organization.leave | 无人读（模板留设备端为宜）；本人 | — | ADR-0079:118；intent `163040`（单独同意并提供替代方式） | 规划；是否采用待 W2 卡 |
| B16 | 录用审批中的拟定工资（ADR-0079 C2） | L3 + PI | organization.workforce | 招聘负责人、人事专员；录用审批人按决定 2.4 | — | ADR-0079 C2；同劳动合同只引用薪资档案（ADR-0079:82）是否适用存疑 | 规划；建议录用只引用薪资档案版本 |

## C. 业务与财务（规划；级别按 Q1 答复）

| # | 数据项 | 级别 + 标记 | BC | 明细可见 | 只见合计 | 来源 | 状态 |
|---|---|---|---|---|---|---|---|
| C1 | 标准成本费率表（AGG-05） | L3 + NONE | masterdata.catalog | 定价管理、财务；项目经理只见本项目个人行所用费率 | — | ADR-0026 第 7 项第 1 条（费率）；CC-B06（`cross-cutting-matrix.md:186`） | 规划（W1） |
| C2 | 计费系数、协议价目（AGG-06、07） | L2（Q1） | masterdata.catalog | 范围内 | — | planned.yaml | 规划 |
| C3 | 报价成本估算与毛利（AGG-12） | L3 + NONE | presales.opportunity | 报价编制人、售前负责人、财务 | 报价审批人：毛利率与总价 | CC-P10（`cross-cutting-matrix.md:124`） | 规划（W2） |
| C4 | 投标报价（开标前，AGG-11） | L3（Q1；开标后 L2，按时点降级可行性待卡） | presales.opportunity | 投标负责人、售前负责人 | — | planned.yaml | 规划 |
| C5 | 业务合同金额与付款条件（AGG-13） | L2，按数据范围（Q1） | contract.agreement | 范围内 | — | planned.yaml；intent `143550` 的「合同金额 L3」指劳动合同金额，即工资（B1、B2），不指业务合同（ADR-0080 决定 1） | 规划 |
| C6 | 往来单位银行账户、证件号码（AGG-01） | 法人往来方 L3 + NONE；自然人往来方 L3 + SPI | masterdata.counterparty | 出纳、主数据管理员 | — | ADR-0043:36、80（拿不准按 SPI；银行卡 SPI） | 规划（W1）；自然人项须先做影响评估（决定 8.1） |
| C7 | 分包与采购合同金额（AGG-55、58） | L2，按数据范围（Q1） | procurement.subcontracting | 范围内 | — | planned.yaml | 规划 |
| C8 | 合伙人出资（AGG-54） | L3 + PI（Q1） | payables.payment | 财务负责人 | — | planned.yaml | 规划 |
| C9 | 利润分配（AGG-109） | L3 + PI（Q1） | payables.payment | 财务负责人 | — | planned.yaml | 规划 |
| C10 | 应付薪酬（法人 × 月一笔，AGG-52） | L3 合计 | payables.payment | — | 财务、出纳 | ADR-0079:83 | 规划（W4） |
| C11 | 薪酬过账分录（AGG-84） | 标准成本法：项目、部门按职级标准费率，差异按法人 × 科目（Q7 (a)，待财务确认）；总账不能掩码 | accounting.ledger | — | 财务 | ADR-0080 决定 2.5 | 规划 |
| C12 | 银行流水（AGG-88） | L2，财务范围；代发只出一笔 | accounting.cashbank | 财务、出纳 | — | planned.yaml | 规划；代发逐笔回单是否入流水存疑 |
| C13 | 经营计划目标、预算账户（AGG-79、80） | L2（Q1） | operations.planning | 范围内 | — | planned.yaml | 规划 |
| C14 | 费用报销、差旅（AGG-59、131） | L2 + PI，按数据范围（Q1） | expense.spend | 本人、审批人、财务 | — | planned.yaml | 规划 |

## D. 分析层

个人行不进 `reporting`（ADR-0043 第 5 项第 4 条、第 7 项 U-8 暂按 D13 字面，`ADR-0043.md:111、122`）；B1–B4、B12 只以满足 k 的合计进入，按 ADR-0025 第 4 条（`ADR-0025.md:120`）标敏感级。ADR-0025 第 5 条的镜像已由 ADR-0054 决定 7 撤销（`ADR-0054.md:87`），不作依据。

## E. 后续卡（不开）

| # | 标题 | 一句话 |
|---|---|---|
| 1 | 掩码组件与 `@SourceColumn` | 决定 8.1：掩码、明文审计、`SensitiveFieldExposureTest`、`toString` 检查 |
| 2 | 合计权限、k 与本人查看 | 决定 2、3：权限码声明、k 在组件内执行、payroll 表 `owner_id` 登记为主体员工 |
| 3 | 敏感导出权限 | 决定 4.3 与 G-X5 |
| 4 | 只读角色改列清单授权 | 决定 5.4：撤表级 SELECT、L3 schema 移出默认授权、C-9 校验 `has_column_privilege` |
| 5 | 生产云权限白名单、主机与对象存储 | 决定 5.1、5.3、5.8：RAM 策略、DMS / DAS、日志下载、主机固定命令、业务文件桶与导出暂存与归档桶的桶策略 |
| 6 | 生产 RDS 加密与参数 | 决定 5.2、6.1：云盘加密、`log_error_verbosity = terse`，并入 C-2 购买清单；修正 `prod-db.md:3` 的共用实例口径 |
| 7 | 老系统只读账号拒读 L3 表 | 决定 5.5：列出老系统存工资、银行卡、证件号的表，专用账号由用户持有 |
| 8 | 字段加密组件 | 决定 6.2：复用 `AesTotpSecretVault` 做法，加密钥版本、盲索引、KMS 密钥与用户离线备份 |
| 9 | 守护补充 | `ClassificationRegister` 关键字；迁移脚本禁 `GRANT`，引用 L3 列的视图与 `SECURITY DEFINER` 函数须登记；C-9 视图依赖校验 |
| 10 | 薪酬职责分离、代发与入账（W4） | 决定 2.5、7.5：新 `GrantConflict`、出纳代发导出权限、标准成本法过账（先取得财务确认） |

## 未核实与待补

- B3、B10、B11 中人事专员、考勤管理员是否需看到金额或凭证，待各卡按最小必要定。
- 聚合稿未列字段级属性，本表按聚合「内含」推定数据项；字段以各 BC 数据模型为准。
