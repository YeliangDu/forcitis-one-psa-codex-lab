# 派工卡 R-1b：A8 来源表与全部引用改为自有 UUIDv7，建来源对照

## 1. 卡号与类别
- 卡号：R-1b（并入 DA-1、DA-2、DA-4、DA-5、DA-6、DA-9、DA-11、DA-17 哨兵部分、B-7.1、INT-M10、INT-L12、INT-L18 余项、DDL-01/02/03/04/06/08、G-C5 事件 yaml 一半）
- 类别：结构性重构
- 批次与整合分支：Phase 1a 下一批 W1，R-1a（#125）合并后开工；可拆子 PR，建议 ① 来源对照 + 同步防腐层 + 快照表主键；② identity（user_accounts、emergency_admins、external_identities 不在本卡，见 DA-3）与「本人」判定；③ 审批（Flowable idGenerator、approval_tasks、子表、办理记录序号）；④ 其余引用列、OpenAPI / 事件 / 前端收口

## 2. 依据
- ADR：ADR-0033 自有标识与来源对照（已采纳，#119）全文，尤其「本人」判定清单、来源对照状态列、法人代码、postSeq、员工 ID 与用户 ID 两类引用；ADR-0034 标识形态（授予表留给 W2）
- 用户原话：「改为自有 UUIDv7 + A8 ID 对照列 (Recommended)」「主键统一」「也加，自有表零例外 (Recommended)」「认可对照表 (Recommended)」、迁入批次「迁入批次 / MigrationBatch」（intent 见 #107、#113、#120）
- 审计：数据设计审计 DA 各条（`~/.modernize/psa/audit/DATA_AUDIT_2026-10-15.md`）、DDL 基线审计 DDL-01..08（`DDL_AUDIT_2026-10-15.md`）、原则核对 A 1-B1
- R-1a 交接清单：`primary-key-type-baseline.txt` 中去向为 R-1b 的 13 张表；`OpenApiExportTest.OTHER_ID_NAMES`；`HANDOFF_one-1a-next.md` 与 `~/.modernize/psa/handoff/one-1a-next/r1-evidence.md`「留给 R-1b」

## 3. 范围与卡上文件
- 允许改动：开放基线中 organization_workforce、platform_identity、platform_approval、platform_integration、platform_numbering、platform_audit、cost_timesheet 的相关表与列；对应模块的 domain / application / infrastructure / web；kernel（EmployeeId 下沉、ID 类型）；`docs/modernization/events/*.yaml` 与生成物；`frontend/packages/api` 生成物与使用处；`backend/architecture/*` 登记文件
- 归属：本卡独占 `platform_integration.source_references`、快照与版本表主键、Flowable idGenerator 配置
- 不碰：account_roles / role_permissions（ADR-0034，W2）；external_identities 与收件人解析（DA-3 / DA-10 另卡）；命名改名（R-2a）；时钟默认值（R-4）

## 4. 验收测试（测试代理先写）
- 测试代理：`code-modernization:test-engineer`（opus，本卡含安全闸门），先提交，trailer `Acceptance-Test-Author: test-engineer`
- 路径：`backend/src/test/java/com/forcitis/one/acceptance/<子域>/<bc>/…`
- 测试名（示例，按 ADR-0033 守护清单补全）：
  - `R-1b A8 来源 ID 可为负数，经来源对照映射到自有 UUIDv7`
  - `R-1b 同步按来源 ID 匹配，找不到才新建`
  - `R-1b 应急登录：快照可用但查不到绑定员工即拒绝并告警`
  - `R-1b HELD 放行人在离职名单中仍被拒（操作人即本人）`
  - `R-1b 不能改自己的法人归属（操作人即本人）`
  - `R-1b 发起人不得自批（操作人即本人）`
  - `R-1b 法人代码发号前可更正、发号后禁改`
  - `R-1b OpenAPI 与事件中所有 ID 字段为 string`（OTHER_ID_NAMES 清空）

## 5. 执行者与模型
- 执行者：子代理 sonnet（各子 PR），复审 opus（high）
- 工作区：独立 worktree，分支 `claude/one-1a-next-2-r1b-*`，基线为 R-1a 合并后的 main
- 用户已允许本机临时 postgres:18 容器（用完即删、只碰自己命名的容器）

## 6. 禁止事项
- 不改业务语义（「本人」判定只换比较的 ID 类型，不放宽）；不放宽、不删除断言
- 领域与其他模块不出现 Source*Id；任何模块不以员工编号作键
- 不依赖跨实例的 ID 顺序（需要次序用序号或时刻）

## 7. 预期表结构 diff（必填）
- 两个空库分别执行改动前、后基线，`pg_dump --schema-only`（带 / 不带权限）与种子 `--data-only` 对比
- 预期差异只能是：上述表主键与引用列改 uuid、新增 source_references、`legacy_numbers` 多来源与 `migration_batch_id`、审批子表引用 `approval_request_id`、去哨兵、DDL-01..08 所列约束与索引、注释
- 不得出现：其他表的列、约束、默认值变化

## 8. 交付物与自查
- 交付物：基线、代码、测试、守护（主键类型守护无 R-1b 基线行；LegacyShapeTest 禁领域层 Source*；Error Prone CollectionIncompatibleType / EqualsIncompatibleType 为 error，先验证与 JDK 25 兼容；ID 输出守护覆盖新 ID）、OpenAPI、事件目录、前端、「框架管理表」清单、`primary-key-type-baseline.txt` 只剩 ADR-0034 两行
- 自查：`scripts/precheck.sh origin/main`（若已合并）或 CI 同款各脚本；全量 `scripts/ci/backend_verify.sh`、前端 `pnpm verify`
- 报告分开写「已验证」与「未运行」

## 9. 复审要求
- opus：表结构 diff 只含声明改动；「本人」判定每条有反向测试；同步匹配与负数 ID；OpenAPI / 事件无数字 ID
- Blocker / High 合并前修完并由 opus 复核

## 10. 报告去向
- 向 one-1a-next-2 报告，抄 one-coordinator

## 2026-10-15 修订（one-1a-next-2）

本卡改按 P0–P6 拆分子 PR（one-coordinator 同意），各子 PR 的范围、前置、执行 / 复审模型与拆分图 C1–C11 的处理见 `phase1a/NEXT_BATCH_PLAN.md` 第 7.2 节。§1 的 ①–④ 与 ADR-0033:210 的三段拆法作废；§3 至 §9 的要求逐个子 PR 适用，每个子 PR 附预期表结构 diff，OpenAPI 与前端生成物随子 PR 提交。以下补 #129 规则条文核对带来的验收点（不改上文）：

- **G-A1**：路径参数与查询参数里的 ID 也是字符串；前端生成的 `validators.ts` 不得再把 ID 定义为数字。P2、P3b、P4 各自处理，P5 收尾。
- **G-C5**：事件与出站负载扁平，不出现 `{value: …}`（P1）。契约如实修正（201、`If-Match`、`ETag`、具名枚举、`info`）不在本卡，归新卡 H-1。
- **`legacy_numbers`（G-MG1 / G-MG6，P3a）**：只追加；具名唯一约束（含来源系统）；来源系统列加 CHECK；`document_id` 改 uuid；`import_batch` 改为 `migration_batch_id uuid NOT NULL`，不建外键（跨 schema），列注释引 ADR-0036；删除提议术语 `import_batch`，并在 `migration_batch` 的 `forbidden` 列出 `import_batch`、`ImportBatch`、「导入批次」。§7 预期表结构 diff 相应补上只追加约束与具名唯一约束。
- **来源系统取值**按 ADR-0033:99（组织源、老系统、核算系统）定名并登记术语；kernel 已有的 `SourceSystem`（事件来源）不得复用，新词用 `ExternalSource`。
- **「本人」判定清单补入**（C4）：工时单本人检查（提交、读取、取消、撤回）、`SessionCurrentUser` 用用户 ID 查员工法人、撤回时发起人的比较；P1、P2 各加反向集成测试（员工 ID 与用户 ID 取不同值）。
- **系统操作的 `actorId`**（C5，P2）：复用信封已有的 `actorType`，不新增 `actorKind`；opus 复审裁定。
- **老系统人员、部门对照的一次性导入**（P6）：读取明细须先经用户逐阶段批准，PR 只放计数与差异条数。
