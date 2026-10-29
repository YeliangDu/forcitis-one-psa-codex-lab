# A8（a8_db）访问登记表——跨阶段

日期：2026-10-14 · 来源：architecture-critic（opus）对 GUIDING_PRINCIPLES §9a 的专项核对（会话 psa-1a-platform-bridge-reimagine）。

用途：§9a 要求「每个阶段的出口条件证明本阶段涉及的权责事实没有遗留在老系统或 OA」。本表登记老系统对 A8 的全部读取与写入，作为跨阶段台账：每个阶段出口时，本阶段对应行的状态改为「已停用」（老路径）或「已迁入」（新系统自有事实）。新系统可只读 A8 全库（ADR-0037），读取只在 `platform.integration` 内（A8 读取与致远验票在 `platform.integration.infrastructure.seeyon` 适配器，REIMAGINED_ARCHITECTURE §2.1，用户 2026-10-14：「改名并移到 integration」）；运行时读取的表与列见外部读取登记 `backend/architecture/external-read-registry.yaml`（视图 DDL `docs/data/legacy-views/seeyon-views.sql`，视图冻结，ADR-0037 第 6 项）。A8 数据不作为权责事实的输入（ADR-0037 第 2 项第 3 条）。生日（#6）不在 1a 白名单。

| 状态列约定 | 含义 |
|---|---|
| 1a 白名单 | 新系统运行时经 A8 只读视图读取（仅组织 / 身份列） |
| 运行时读取（核对 / 迁入） | 新系统按 ADR-0037 只读 A8 基表，用于取证、迁入、对账与等价核对，不作为权责事实的输入；表与列登记在外部读取登记中 |
| 待迁入 | 权责事实，须在所列阶段改为新系统自有事实，老路径停用 |
| 待决定 | §9a 未分类，需用户决定 |

## 清单

路径前缀：
- J = `/Users/yeliang/Projects/FORCITIS/psa/psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/java/cn/iocoder/yudao/module/fst/`
- M = `/Users/yeliang/Projects/FORCITIS/psa/psa-backend/yudao-module-fst/yudao-module-fst-biz/src/main/resources/db/migration/`
- O = `/Users/yeliang/Projects/FORCITIS/psa/docs/ops/`

### 运行时 Java

| # | 位置 | 读取的 A8 表 | 分类 | 新系统去向 |
|---|---|---|---|---|
| 1 | J`dal/mysql/a8/A8DeptMapper.java:35-48` | `ORG_UNIT`、`ADDRESSBOOK`（扩展属性 32/33）、`CTP_ENUM`、`CTP_ENUM_ITEM` | 组织 | 1a workforce 视图 |
| 2 | J`…/A8DeptMapper.java:72-78` | `ORG_RELATIONSHIP`、`ORG_MEMBER`（部门负责人） | 组织 | 1a |
| 3 | J`…/A8DeptMapper.java:92-98` | `ORG_RELATIONSHIP`、`ORG_MEMBER`（区域 AR） | 组织（职责角色） | 1a |
| 4 | J`dal/mysql/a8/A8UserMapper.java:24-40` | `ORG_MEMBER`、`ORG_PRINCIPAL`、`ORG_UNIT`、`formmain_0307`（`field0234`、`field0284`、`FIELD0244`） | 身份/组织；`FIELD0244` 是权责输入维度 | 1a，按列授权、改名并在事实上快照（见问题 1） |
| 5 | J`dal/mysql/a8/A8PostMapper.java:19-22` | `ORG_POST` | 组织 | 1a |
| 6 | J`dal/mysql/a8/A8MemberBirthdayMapper.java:21-24` | `ORG_MEMBER.EXT_ATTR_21` | 人员敏感信息 | 推迟到费用阶段，不进 1a 视图 |
| 7 | J`infrastructure/cost/projectcost/prepostingcheck/PrePostingCheckOaMapper.java:27-47` | `formmain_0858`/`formson_0859`、`formmain_1318`/`formson_1319`、`ORG_MEMBER` | 权责（人工成本） | Phase 2/3 成本：薪资结果导入为自有事实（H-3） |
| 8 | J`…/PrePostingCheckOaMapper.java:50-53` | `ORG_MEMBER`（工号） | 身份 | 改调 workforce 的 api |
| 9 | J`service/a8/A8ServiceImpl.java:29,63`；`dal/dataobject/a8/CustomerAndSupplierDO.java:11` | `T_U8C_BD_CUBASDOC`（客商，推送 U8C） | 客商主数据（§9a 未分类） | 合同或客商阶段由新系统自有，向 U8C 推送（M-5） |
| 10 | J`service/a8/A8ServiceImpl.java:51-52` | `ORG_MEMBER`（写，死代码） | — | 不延续（N-1） |
| 11 | J`infrastructure/cost/projectcost/ingest/AnnualBonusDetailViewReader.java:52,68,84` | OA 侧视图 `annual_bonus_detail` | 权责（年终奖成本） | Phase 3 成本：计提和发放在新系统产生 |

### Flyway 跨库视图（列当前生效的版本）

| # | 位置 | 读取的 A8 表 | 分类 | 新系统去向 |
|---|---|---|---|---|
| 12 | M`V20260920190100__rebuild_psa_pm_contract_a8_by_db_name.sql:51` | `formmain_0051`、`formmain_0374`（回签日期）、`formmain_0238`（商机） | 权责（合同） | 合同模块（1b 报价之后的合同阶段） |
| 13 | M`V20260920190000__rebuild_psa_pm_contract_term_a8_by_db_name.sql:51` | `formson_0296`、`formson_0376`、`formmain_0051`、`CTP_ENUM_ITEM` | 权责（合同条款） | 同上 |
| 14 | M`V20260914101057__rebuild_psa_pm_invoicing_add_type.sql:48` | `formmain_0428`、`formson_0567`、`formson_1033` | 权责（开票） | 应收模块（Phase 3） |
| 15 | M`V20260920190300__rebuild_psa_pm_collection_a8_by_db_name.sql:49` | `formmain_0569`、`formson_0601`、`formson_1033` | 权责（回款） | 应收模块（Phase 3） |
| 16 | M`V20260914101058__create_psa_pm_ar_bad_debt_view.sql:45` | `formmain_1104`/`formson_1105` | 权责（坏账） | 应收模块（Phase 3） |
| 17 | M`V20260916203100__rebuild_psa_pm_contract_asset_impairment_invoiced_by_net_amount.sql:95` | `formmain_1375`/`formson_1376`、`formmain_1104`/`formson_1105`、`formmain_0051` | 权责（合同资产减值） | 收入/应收（Phase 3） |
| 18 | M`V20260920190200__rebuild_monthly_salary_calculation_a8_by_db_name.sql:50` | `formmain_0858`/`formson_0859`、`formmain_1318`/`formson_1319`、`ORG_MEMBER` | 权责（人工成本，算薪完成闸） | Phase 2/3 成本（H-3） |
| 19 | M`V20260922160000__rebuild_psa_daily_rate_manual_dedup_a8_by_db_name.sql:69` | 同上 | 权责（工时成本单价） | 成本模块自有的人员成本率 |
| 20 | M`V20260930120000__create_psa_employee_bank_card_view.sql:47` | `formmain_0307`/`formson_0478`、`CTP_ENUM_ITEM` | 人员敏感信息/付款支撑 | 费用阶段单独决定（M-5） |
| 21 | M`V20261008175200__create_psa_oa_accrual_detail_view.sql:56` | `formmain_1338`/`formson_1339`、`formson_0735` | 权责（预提费用） | 费用/成本阶段迁入，关闭 OA→U8C 抛凭证 |
| 22 | M`V20261008175230__create_psa_oa_payment_detail_view.sql:61` | `formmain_0644`/`formson_0645`、`formson_0735` | 权责（付款） | 付款/应付阶段 |
| 23 | M`V20261008175240__create_psa_oa_interface_dept_view.sql:36` | `LEXMISCIF_DEPTAUX` | 账务映射（§9a 未分类） | 新系统总账映射表（M-5） |

### Flyway 外的读取（运维脚本 / SQL Agent 作业）

| # | 位置 | 读取的 A8 表 | 分类 | 新系统去向 |
|---|---|---|---|---|
| 24 | O`cost-summary-ratify-date/alter_base_view_psa_cost_summary.sql:4,9` 修改的基座视图 `psa_cost_summary`（视图定义不在仓库，文件里列不出 A8 表名） | 7 个分支：日常、差旅、招待、付款申请、人事、财务、特殊付款 | 权责（费用/付款成本） | 费用模块（Phase 2/3） |
| 25 | O`oa-cuvalue-import/create_job_prod.sql:41-48,70-77` | `formson_0583`、`formmain_0581`、`formmain_0051`、`formson_1033` → `psa_pm_cuvalue` | 权责（收入确认） | 迁入新系统；取消 ADR-0016 的只读接入（H-1） |
| 26 | O`oa-opportunity-sync/02_alter_view_T_FST_OPPORTUNITY_B_pre.sql:46`（生产环境在 BI 库） | `formmain_0238` | 商机（尚不是权责事实） | 1b 商机/报价自有 |
| 27 | O`oa-opportunity-sync/05_jobsteps_prod.sql:10,20` | `formmain_0219`（客户） | 客商主数据 | 同 #9 |

### 仅生产 SQL Agent、无仓库脚本（R0 Q11 发现，2026-10-14）

来源：生产只读查询 `msdb` 作业元数据。只取了作业名、步骤名、目标库、调度、最近运行状态，以及步骤正文中引用的库表名，未取正文（`docs/legacy/bi/bi-usage-summary.md` §3；原始结果在仓库外 `~/.modernize/psa/bi-inputs/runtime/bi_09_*.tsv`、`bi_10_*.tsv`）。这些作业在 psa 仓库中没有脚本，只存在于生产 SQL Agent，全部启用，最近一次运行均为 2026-10-14 且成功。「同步商机档案到 PSA」作业即 #26 / #27 的生产作业，不另编号。

| # | 作业（步骤数，调度） | 读取（A8 / BI 视图） | 写入（老系统） | 分类 | 新系统去向 |
|---|---|---|---|---|---|
| 28 | `同步成本明细到PSA`（3 步：插入、更新项目已发生成本明细；委外和居间付款申请不结转；每小时） | `formmain_0051`（项目档案）、`formson_0640`（付款申请）、`formson_1226`（特殊付款），以及 BI 视图 `T_FST_PAY`（A8 报销、付款七类单据 + 老系统项目） | `psa_pm_pay` | **权责**（项目已发生成本：费用、付款） | Phase 4 / 6c：改为 `expense.spend`、应付与付款 BC 的自有事实经过账管道归集；步骤 3「委外和居间付款申请不结转」是隐含业务规则，须在 6c 规则抽取中登记 |
| 29 | `同步项目档案到PSA`（2 步：导入、更新；每 30 分钟） | `formmain_0051`、`formmain_0219`、`formmain_0238`、`formmain_0457`、`formmain_0763`、`formmain_0919`、`formmain_0973`，以及 BI 视图 `T_FST_PROJECT` | `psa_pm_project` | **权责参照**（项目档案中的合同金额、交付部门、条款等合同基线属性）+ 项目主数据 | Phase 2 / 6a：项目在新系统立项（`delivery.execution`），合同基线属性归 `contract.baseline`（6a）；Phase 2 起不得再以 A8 项目档案覆盖新系统项目 |
| 30 | `同步项目预算到PSA`（7 步：预算、人工成本明细、其他费用明细的插入与更新，补人天为空的明细；每 10 分钟） | `formmain_0051`、`formmain_0574`（项目预算编制），A8 库内视图 `T_FST_PSA_BUDGET_WORKS`、`T_FST_PSA_BUDGET_WORKS_COST` | `psa_pm_budget`、`psa_pm_budget_works`、`psa_pm_budget_works_cost` | **权责相关**（项目成本预算：成本控制基线，不过账；与老系统自己的 PM_BUDGET 形成双入口，RULE-182 / 239） | Phase 2：预算只在新系统编制（`delivery.execution`），A8「项目预算编制 / 变更」模板随之停用（OA_PROCESS_INVENTORY #79、#80） |
| 31 | 同步工时档案（4 步：插入、删除、更新工时档案，更新无效交付工时分录；每小时） | 前 3 步只在老系统库内加工（`psa_time_sheet*`、`psa_pm_project`、`psa_pm_plan_task`）；第 4 步读 BI 视图 `T_FST_INVALID_DELIVERY`（由 A8 项目档案与风险客户派生） | `psa_time_sheet_works_end` | **权责**（工时成本归类：无效交付标记影响工时成本去向） | Phase 2：工时成本归类规则改由 `cost.timesheet` 依据新系统项目与风险客户事实判定，不再读 BI 视图；作业名「A8 工时明细档案同步到 PSA」与实际不符（不直读 A8） |
| 32 | `同步商机档案到PSA-pre`、`同步项目档案到PSA-pre`（每小时 / 每 30 分钟） | 预发布 A8 库 `a8_db_pre` 的 `formmain_0219`、`0051`、`0238`、`0457`、`0763`、`0919`、`0973` | 预发布老系统库 `ruoyi-vue-pre` | 否（预发布环境，非生产数据） | 随 #27、#29 停用；须确认预发布库在生产实例上的存续与权限 |

**§9a 影响**（GUIDING_PRINCIPLES §9a、ADR-0021 第 2 条）：
1. #28–#31 是在仓库之外运行的 A8 / BI → 老系统数据通道，其中 #28、#31 承载权责事实，#29、#30 承载合同基线参照与成本控制基线。新系统中这些通道**不得以直连 A8 或读 BI 视图的方式延续**。
2. 按阶段改为新系统自有事实：
   - 项目与预算 → Phase 2（预算）/ 6a（合同基线属性）；
   - 工时档案 → Phase 2；
   - 成本明细 → Phase 4 / 6c。
   各阶段出口时，本表对应行改为「已停用」，并在生产 SQL Agent 中禁用对应作业（运维动作，经用户批准）。
3. BI 视图（`T_FST_PAY`、`T_FST_PROJECT`、`T_FST_INVALID_DELIVERY`）**处在老系统的权责数据路径上**：BI 库不只是报表读方。在上述阶段切换前，这些视图不能改为过渡镜像或废弃（BI_SCOPE §4.3 补充）。

### 写入 A8（A8 被当作存放地，不能延续）

| 位置 | 写入的 A8 表 | 写入内容 |
|---|---|---|
| O`oa-quotation-sync/sp_sync_quotation_to_oa_prod.sql`（`:7,70,133,164-165`） | `formmain_0238`、`formmain_0366`、`formson_0241/0367/0368/1178/1387` | 报价 |
| O`oa-quotation-term-sync/sp_sync_quotation_term_to_oa_prod.sql`（`:211-219`） | `formmain_1321`/`formson_1322` | 报价付款条款 |
| O`oa-cuvalue-sync/sp_sync_cuvalue_to_oa_prod.sql`（`:519-554`） | `formmain_0581`/`formson_0583`，并更新 `formson_0296`/`formson_0376` | 产值 |
| O`oa-bonus-accrual-import/import_2026_bonus_accrual_to_oa_prod.sql:3,17` | `formmain_1383`/`formson_1384` | 年终奖计提 |
| O`oa-salary-backfill/backfill_missing_salary_to_oa_prod.sql:24-82` | `formmain_1318`/`formson_1319` | 工资补录 |
| O`oa-invoice-term-link-backfill/backfill_invoice_term_link_prod.sql:15-19` | 更新 `formson_0567`/`formson_0601` | 发票和回款明细与产值、条款的关联 |
