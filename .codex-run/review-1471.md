复审结论：必改
commit：cca9eaeb4ae97e5d39f09a2b270a19964f0e9290
复审者：sonnet-首轮-one-reviewer-1471

patch-id：0735b140558c688f3eeea92e4d3f82f701dc9279

## 影响面
新模块 organization.recruitment（仅 schema、表、注册行、模块边，无 Java 行为）与 platform.numbering 多一条种子单据类型；影响所有断言“种子单据类型恰为四个”的编号测试（已验证 1 层：NumberingRowRulesIT）。PR 1471 头与 sha 一致（gh pr view 核对）。

## 必改

1. backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/NumberingRowRulesIT.java:100-101 与 :115-116：本 PR 新增第五个种子单据类型 RECRUITMENT_REQUISITION（V20261105180100），但这两个既有测试仍断言种子恰为 EMPLOYEE、JOURNAL_VOUCHER、QUOTE、TIMESHEET 四个。
   复现：`cd backend && ./mvnw -o test -Dtest=NumberingRowRulesIT` 得 Tests run: 11, Failures: 2（seedsKeepTheirTemplateAndStartAtEventCountZero、numberingKeepsQuoteAndJournalVoucher，均为 “some elements were not expected”）。CI 会红，合并队列拦截。
   修法：把 RECRUITMENT_REQUISITION 加入两处期望（规则行 `RECRUITMENT_REQUISITION:0:1`，版本行 `RECRUITMENT_REQUISITION|{TYPE}-{LE}-{yyyyMM}-{seq:4}|MONTHLY|1|PENDING_CONFIRMATION|1|019a0000-0000-7000-8000-000000000505`，类型列表同步），并把 DisplayName 里 “four seed rules” 改成五个。这是随新种子行的同步更新而不是放宽断言（先例：commit 452b93216 加 EMPLOYEE 时同样改了本测试）；因它属于验收测试目录，请 lead 在放行时确认这属于预期的同步修改。
   说明：作者的自测只跑了招聘自己的测试，漏了这个既有测试。

## 建议
- data-classification.txt 中 requested_count 行的用途写成“需求人数与流程状态”，签署记录是“需求人数、期望到岗日与流程状态”；该行只含人数，现写法可以，只是与记录措辞略不同，不影响正确性。
- RecruitmentRequisitionTableIT 中 `import com.forcitis.one.platform.integration.api.WecomGateway` 夹在 java.* 之间，可整理顺序。

## 已核对无问题（带证据）
- 迁移与签署记录逐列对照（docs/data/reviews/organization-recruitment-r1.md）：18 列类型、可空、默认值（version bigint NOT NULL DEFAULT 0）、pk/两个 uk（where not null 由 UNIQUE 对 NULL 的语义承担）、requested_count / description_text / status / close_reason_kind / number 长度 CHECK 命名与规则一致；state_columns 覆盖记录列出的四组规则（关闭成对、草稿三列为空且 approved_at 为空、非草稿三列齐备、approved_at 与批准/取消/录满对应、三个时刻先后）；两个索引名与列一致；表与 18 列注释齐全，可空列均含“为空表示”。无多余对象、无外键、无申请人表、无薪资列。
- 触发器逐条核对合法写入清单：插入限 DRAFT/SUBMITTED；删除限 DRAFT；id、legal_entity_id、requested_by_employee_id、created_at 不变；DRAFT→SUBMITTED 允许改描述列；SUBMITTED→APPROVED、SUBMITTED→CLOSED(REJECTED/WITHDRAWN)、APPROVED→CLOSED(CANCELLED/FILLED) 不得改描述列与提交三列，批准后关闭保持 approved_at；CLOSED 与其余边被拒；TRUNCATE 触发器存在。DRAFT→DRAFT 不单独检查状态列，由 state_columns CHECK 保证其全为空，等价于记录要求。
- 迁移版本 V20261105180000、V20261105180100 均高于基线最高 V20261105160000；未编辑旧迁移（git diff 只含新增文件）。函数设置 search_path = pg_catalog, pg_temp。
- 编号种子：id ...405/305/505 与既有 401-404/301-304/501-504 不冲突；模板与既有三种单据一致，状态 PENDING_CONFIRMATION、source_kind SEEDED，确认前不发号；DocumentTypes.requireInForce 的测试通过。
- 注册与分类：registry 条目 role/aggregate/mutability/condition/business_key/PERMANENT/NEW 与记录一致，永久保留理由写入注释并点名决定片段 2026-11-05-163008（记录规定的临时形式）；分类 10 列与记录一致（PI 两列：requested_by_employee_id、description_text），代理键未登记。
- 模块边：module-dependencies.txt 新增 organization.recruitment -> workforce, approval, audit, numbering，无反向边（边界测试断言）；contexts.yaml 已有 organization.recruitment（schema organization_recruitment），bcs.yaml 新增同名 BC，二者一致；unbuilt-roots.yaml 与 built.yaml 同步一条；DataArchitectureTest SCHEMAS 加入新 schema。
- docs/ops 六个脚本：schema 创建、授权、REVOKE、验证列表、回滚注释各处都已加 organization_recruitment，dev 与 prod 对称。
- 未发现密钥、行级数据；测试夹具只用随机 UUID 与虚构文本；未松动或删除任何断言（NumberingRowRulesIT 本 PR 未改，故红）。
- 运行：`./mvnw -o test -Dtest=NumberingRowRulesIT,RecruitmentRequisitionTableIT,RecruitmentModuleBoundaryTest`：21 个测试，仅上述 2 个失败，招聘 IT 7 个与边界测试 3 个均绿。

### 破坏验证（临时 worktree 于 scratchpad，已删除）
| 防线 | 破坏方式 | 结果 |
|---|---|---|
| 边只改状态列（触发器 descriptive/submit 列检查） | 把 `IF descriptive_changed OR submit_columns_changed` 改为 `IF false` | 红（onlyTheSignedWritesPass） |
| TRUNCATE 拒绝触发器 | 删除 tg_recruitment_requisitions_truncate | 红（onlyTheSignedWritesPass） |
| state_columns 的时刻先后 | 删除三条先后比较 | 红（timeOrderIsEnforced） |
| 其余防线（state_columns 配对、列 CHECK、唯一约束、删除限草稿、关闭行不变） | 未逐一破坏；测试中有对应拒绝用例，且 `stateColumnsHoldWithoutTheTrigger` 关闭触发器单独验证 CHECK | 未验证破坏（仅读测试） |
