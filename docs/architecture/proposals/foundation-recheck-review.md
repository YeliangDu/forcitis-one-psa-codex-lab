# 地基清单重核（FOUNDATION_RECHECK.md）首轮对抗评审

状态：历史记录｜2026-10-16｜负责：one-architecture

日期：2026-10-16 · 评审对象：`FOUNDATION_RECHECK.md`（行号以该文件为准）· 对照：同目录 `FOUNDATION_RECHECK_STATUS.md`、`FOUNDATION_NEW_ITEMS.md`、`CALIBRATION_SLICE.md`、`CONSTRUCTION_PLAN_DRAFT.md` §6.1；`CROSS_CUTTING_MATRIX.md`、`CODING_STANDARD.md`、`NAMING_STANDARD.md`、`phase1a/NEXT_BATCH_PLAN.md` §2.3；ADR-0033、0035、0036；`backend/architecture/module-dependencies.txt`、`data-classification.txt`；main 只读树 `cf3b9a3e`。

## 结论

**改后再交。** 「切片是否要用」的判定大体成立，算术无误，证据抽查 6 条全部属实；但有 4 个高问题会让派工直接撞墙：F3 模块位置造成依赖环、F6 依赖写反并与 §4 构成循环等待、F0 的做法与已定规范冲突且需动共享 RDS、切片必需的通用错误码没进清单。另外本稿未提已排的 W2 K-1/K-2/K-3，等于在已批计划旁另起一套，须写明取代关系。「须用户定：无」不成立（见 H5）。

## 高

- **H1 F3 放 `platform.eventing` 会成环（行 50、60）。** 依赖图已有 `platform.eventing -> platform.identity`，而 17 个监听者里 identity 占 5 个（`ApplyConfirmedAuthorization` 等）；identity 要用共用件就得加 `platform.identity -> platform.eventing`，形成环。eventing 也没有 `api` 包。去重表又按 ADR 归各 BC schema，eventing 不能替别的 schema 写表。改法：kernel 放端口与声明注解（`ConsumedEvents` 形接口、`@IdempotentBy`），各模块 infrastructure 用同形表与共用的 JDBC 模板（可放 kernel 外的非模块工具包，或每模块一个薄实现加 `DdlNamingTest` 式同形检查）；在 F3 行写明不新增模块边。
- **H2 F6 依赖写错，并与 §4 循环（行 53、61、62）。** F6 的真实前提是 `cost.timesheet` 的迁入入口与 B-06 补齐的规则（`FOUNDATION_NEW_ITEMS.md` 行 256 原话即「排在 B-06 补齐规则之后」），不是 F1（工时核对用 `Hours`，不碰金额）也不是 F3（迁入按 G-MG4 不发事件）。行 62 又写「B-06 补充等 F6 完成」，两边互等。改法：F6 依赖改为「B-06 补齐规则 + 迁入入口契约」，与 B-06 同车道或紧随其后；行 62 删去 B-06 对 F6 的等待，只保留 B-16 对 F3 的等待。另 F6 要在依赖图加 `platform.migration -> cost.timesheet, platform.integration, platform.numbering` 三条边（`module-dependencies.txt` 现无此行），列入 F6 交付。
- **H3 F0 做法与已定规范冲突（行 32、55）。** 「建库排序规则改为 ICU」违反 DDL-11（库统一内建 C.UTF-8，`TestPostgres.java:22-28`）与 CS-3.13（显式 `COLLATE "zh-x-icu"`）；NEXT_BATCH_PLAN K-3 已写明「不需要重建库，排序时显式指定 ICU」。改库默认排序须重建 dev 库，而该实例与 it_portal 共用，属 CLAUDE.md 第 9 条须先问用户的操作。改法：F0 改为「按名检索与排序的查询显式 `COLLATE "zh-x-icu"`，索引与键集游标用同一排序规则；补一条后端守护（含中文名排序键的 `ORDER BY` 必带该 COLLATE，取代 K-3a 第 5 条「不做 SQL 守护」需在 PR 中单列理由）」；去掉「DDL 守护检查建库排序规则」。估算仍 0.5。
- **H4 漏了通用错误码（行 46-56 无此项）。** main 上 kernel 只有 `ErrorCode` 接口，没有通用错误枚举；`COST_TIMESHEET_TRANSITION_ILLEGAL` 与 `PLATFORM_APPROVAL_TRANSITION_ILLEGAL`、两个 `*_REASON_REQUIRED` 已是同义重复，违反 CS-2.24 与 G48、G-A8（R-5 未落）。切片新建 6 个 BC，每个都有状态流转与理由必填，不先建就会再复制 6 份。改法：新增一组（或并入 F1/F2 的 kernel 车道）「kernel 通用错误码 + 存量两模块改用 + R-5 守护」，约 0.5 车道周，第 1 波。
- **H5 「须用户定：无」不成立（行 10）。** 至少有：① F6 在 dev 建 `one_dev_history_loader` 账号（CLAUDE.md 第 9 条，建账号先问）；② 依赖图新增边由 IT Admin 经 CODEOWNERS 审（即用户）；③ 本稿推迟 N03、N11、B01，并改 K-1/K-2/K-3 已排范围（NEXT_BATCH_PLAN §2.3，K-1 含 `Ratio`、拆分原语、`TaxContext`），属已排计划的范围变更，须调度报用户确认或按 登记。改法：行 10 列出这三项，并注明 F0 按 H3 改后不再需要动库。

## 中

- **M1 未交代与 W2 K-1/K-2/K-3 的关系（全文）。** 三者写入范围已定为 `kernel/money/`、`kernel/time/`，K-2 还含「把 workforce、numbering 的现有版本表迁到基座」。改法：§1 加一句「本批取代 K-1/K-2/K-3 未完成部分」，F1、F2 的模块栏写出子包 `kernel.money`、`kernel.time`（行 60 的「分包」即落到这两个子包），沿用 K 卡的验收要点。
- **M2 F2 守护会误伤存量（行 49）。** 「版本表必须用共用区间类型」一上线，`org_unit_versions`、`assignment_versions`、`legal_entity_versions`、编号规则版本、`platform_integration` 生效表（`EffectiveAttributionPeriods`、`OrgHistory`、`JdbcNumberingRuleRepository` 等）立即失败。改法：要么按 K-2 把存量迁到基座（估算加 0.5–1），要么守护带登记豁免清单且只减不增；二选一写进 F2。另 P09 扩到 `OrgUnitId` 需先把它下沉到 kernel（现不在 kernel），写进 F2。
- **M3 F5 做不了它写的接线（行 52）。** 人员、工时单、过账查询的读路径分别由 B-13、B-06（main 无 web 控制器）、B-16（模块不存在）在切片中新建；「调岗离职生成待调整权限」依赖 B-13 的人事事件。地基车道无处可接。改法：F5 只交「范围组件 + 读路径须声明范围或登记豁免的守护 + 自动授予禁止规则」，接线与待调整权限移到 B-13、B-06、B-16 的验收项；估算降到 1–1.5。
- **M4 P04 的理由与用户原话相反（行 27、52）。** CC-B06 记用户「按职级标准费率看到个人行」，标准费率是可见级；`data-classification.txt` 也没有任何费率列。切片真正要掩码的是 `employees.display_name`、`employee_code`、`email` 等已登记 SENSITIVE 的列（人员页与工时单都要展示），现有 `SensitiveColumnExposureGuardTest` 会直接拦。改法：理由改为「人员与工时页出参含 SENSITIVE 个人信息」，掩码按 K-3 写的两级（职级标准费率可见、个人真实薪资掩码）。位置裁定：掩码是随调用者权限变化的行为，不属 kernel（NAMING_STANDARD 行 218「kernel 不得放业务行为」）；kernel 只放标注（如 `@Sensitive(level)`，同 `@PublicEndpoint` 先例），出参序列化与按 `kernel.CallerPermissions` 判定放 `platform.web`，不增模块边。
- **M5 切片碰到的审批与权限共用点没列（行 46-56）。** `platform_approval.approval_requests.document_type` 是 `CHECK IN ('QUOTE','TIMESHEET','JOURNAL_VOUCHER')`，`ProcessKey`、`kernel.DocumentType` 都是封闭枚举；费率表版本批准（INV-35）须新增单据类型、流程定义与编号规则。权限码散在 identity 基线种子里，6 个 BC 都要加；F7 的 `useCan` 前提是 OpenAPI 为每个操作声明权限码（`FOUNDATION_NEW_ITEMS.md` 行 183「若尚未声明」）。改法：增一小组「审批与权限登记」：新单据类型的登记方式（或至少一次性补齐切片要的类型）、权限码按模块登记的写法与 OpenAPI 声明守护，约 0.5–1 车道周，第 1 波，F7 依赖它。
- **M6 并行波次共用文件冲突未处理（行 60-61）。** F1、F2、H4 都改 `ArchitectureRulesTest.java`；F1、F0 都改 `DdlNamingTest`；几乎所有组都改 `terms.yaml`、`openapi.json`、`data-classification.txt`。改法：按 CS-7.6 同一基线，并在派工卡写「新守护各建独立测试类，不往 `ArchitectureRulesTest` 追加」；`openapi.json` 与 `terms.yaml` 冲突按 rebase 后重生成处理，写进派工卡。
- **M7 F7 依赖不全（行 54、61、63）。** 审批嵌入件依赖 M5；表单库选型研究是 F7 的前置，行 63 只说「F7 开工前定」，未列为依赖。F1 只需先出金额 JSON 契约（CS-1.10），不必等 F1 全部完成。改法：F7 依赖改为「F1 金额契约、F4 分页、M5、表单库选型」。F5 对 F4 的依赖查无实据，可删。

## 低

- **L1 估算（行 9、56）。** 合计算术无误（13–16.5），新增三项与 `FOUNDATION_NEW_ITEMS.md` 一致。但：① 施工方案 §6.1「额外任务」已计迁入编排 8、事件机制 6，F6、F3 的切片部分应注明从中扣减，避免重复计；② BC 估算加 20% 测试周期，地基未加，宜注明口径；③ F1 偏宽松（`Money` 在 kernel 外零调用方），可收到 2–2.5；按 H4、M2、M5 增减后重算，预计仍在 13–17 内。
- **L2 G-M8（行 48）** 「参数与返回值不得是裸 `BigDecimal`」须限定在 domain、api、application，否则命中 `CheckCounterCapacity`、`JdbcTimesheetRepository`、`OpenApiListLimit`。
- **L3 推迟项（行 17、21、29）。** N03、N11、P08 推迟成立：切片无拆分（工时 × 费率不拆）、无比率（费率表是职级 × 薪档 × 城市类别）、无导出。但 `Ratio` 与 `Rate` 同属中间精度体系，CS-1.9 下建议 F1 先定 `Ratio` 的表示与 `_pct` 列守护（不实现运算），成本约 0.25，避免 B-02 计费系数时改契约。
- **L4 迁移基线关闭时点（行 73）。** 写明切片新表全部并入基线、关闭按 ADR-0038 在首次生产部署前，F0、F6 改基线须附预期表结构 diff（同 W1 规矩）。审计分区落库可并入 H4 同车道或单列，别只留在「待核实」。
- **L5 测试镜像与 OpenAPI 生成** 已有（`test-images.txt` 含 `postgres:18`、`mssql`；`OpenApiExportTest`、`generate:api --check`），不是缺口；ADR-0035 的 G-C1–G-C7（`openapi_compat.py` 未建）首次生产部署前生效，切片不需要，宜在推迟清单（行 67）补一行。

## 证据抽查（main `cf3b9a3e`，6 条均属实）

| 条 | 判定 | 核对 |
|---|---|---|
| N06 | 未建 | `kernel/Money.java` `SCALE = 2`，构造即 `setScale(2, HALF_UP)`，不看币种小数位 |
| P04 | 未建 | `backend/src/main`、`frontend/packages` 中 `mask|redact|desensit` 零命中 |
| C03 | 未建 | `ConsumedEvents` 只在 `cost/timesheet`；`@ApplicationModuleListener` 共 17 处、9 个文件 |
| I06 | 部分 | `list-paging-gaps.txt` 去注释后 8 行 |
| B02 | 部分 | kernel 无区间值对象；生效日期逻辑散在 workforce、integration、numbering 的 infrastructure |
| T01 | 已建 | `TimeSourceGuardTest` 存在；`TestPostgres` 固定 C.UTF-8（同时佐证 H3） |
