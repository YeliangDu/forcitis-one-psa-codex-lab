# one-1a-par 交接（A 线人事与身份）

2026-10-23 交接（清空接续；本文件整篇替换）。

## 已合入 main
#509、#519、#547（日历版本化迁移）、#595（F2 老待办残留）、#604（KERR，通用错误码进 kernel）、#611（PAR-03 平台所用组织接口清单与守护）。#624（ADR-0078 工号唯一）已采纳合入。

## 在途
1. **#632 W2 PR-B**（IMP-1022-PAR-01，分支 `claude/one-1a-par-w2-versions`，head `19be09d1a`，已合最新 main，本地受影响测试通过）：`employee_versions`、`personnel_commands`、两个只追加触发器（已固定 search_path）、`employees` 删 `last_sync_run_id`、术语、注册表、测试。opus 与 sonnet 复审结论已贴 PR 评论。**下一步**：CI 全绿后向 one-coordinator 报“#632 head 19be09d1a CI 绿，请重批”（head 变过，不直报 one-merge）；条件：CI 全绿、复审结论已贴（已满足）。
2. **PR-B2**（工号唯一键 + 重复工号隔离路径 + 验收改动，裁定 RULING-COORD-20261022-05 已进 main，ADR-0078 已采纳）：现成实现在分支 `claude/one-1a-par-w2-employee-code-unique` @0426bf3bb（含 `uk_employees_employee_code`、SnapshotReconciler 隔离、`IssueKind.DUPLICATE_EMPLOYEE_CODE`、域测试、DataArchitectureTest 放行），需基于最新 main 重建分支并补：
   - ADR-0078「迁移方案」「守护规则」第 1、2 条：唯一约束、**库层禁改工号**（发号后禁改，触发器）、守护改写，同在一个 PR；
   - 验收目录改动（裁定 -05 第①～④项）：`EmployeeCodeNotKeyIT.theEmployeeCodeIsInNoKey` 改精确等值断言（键清单恰为 `organization_workforce.employees uk_employees_employee_code (u)`，列集合恰为 `(employee_code)`、非复合、无 WHERE；`nothingFetchesByTheEmployeeCode` 与注入用例原样保留）；`DataArchitectureTest.theEmployeeCodeIsNoKey` 同口径，`injectedEmployeeCodeKeysAreCaught` 改为能认出多余唯一键或外键；`WorkforceLengthLimitsIT` 第 7、8 例与 `WorkforceRowConstraintsIT.wf11BlankTextIsRefused` 第 7、8 例夹具工号改 `'WF11-' || uuidv7()::text`（第 7 例原 `'WF11L'` 一并改）；`SourceIdMatchingIT.twoPeopleWithTheSameCodeBothGetAnEntry` 改为断言第二人被隔离并写 `DUPLICATE_EMPLOYEE_CODE` 例外、第一人不受影响、重跑一致（口径见 `claude/one-data-handoff-1` 的 `data/reviews/W2-PR-B.md` 末节）；裁定登记路径含 `EmployeeCodeUniquenessIT` 新路径；提交说明写 `Acceptance-Ruling: RULING-COORD-20261022-05`（与 `Co-Authored-By` 之间不留空行）；
   - 新增验收用例“同号并发→该次同步 FAILED”：**派 sonnet test-engineer 子代理独立写，不给它看实现**，提交带 `Acceptance-Test-Author: test-engineer`（只能改验收目录）；
   - PR 正文写明：重复工号隔离路径（SnapshotReconciler）及对应测试，opus 复审须确认该路径成立（dev 的 A8 同步开关为 true 且不关，入队走裁定 -05“拒收路径已实现”一条）；「改断言」栏单列 ①②④ 与理由；
   - 先 opus 首轮复审（守护文件与触发器），再 sonnet 复核，结论贴 PR。

## 下一步（顺序）
1. #632 绿后重批；2. PR-B2；3. **迁入写入通道·日历部分**（IMP-1022-PAR-05，设计 `data/MIGRATION_WRITER_ROLE_DESIGN.md` 在 `claude/one-data-handoff-1`；迁入角色无 UPDATE，日明细守护 SECURITY DEFINER：固定 search_path、体内不看 current_user、有守护断言；CHECK CONFIRMED 必有 confirmed_by；安全改动，权限与函数自写不交 Codex，opus 首轮必做；判定 4.3 修正 one-architecture 已认可）；4. **卡 E**（行政事务员工活动：入职日期与满周年查询，W2 PR-B 合入后；需求 `claude/one-1a-admin-docs @5ff4ca55d` 的 `B31_ADM02_BLUEPRINT.md` §4 与卡 E，口径 `data/EMPLOYMENT_START_DATE_OPINION.md`；设计经 one-arch-people）。
- 待派卡：IMP-1022-PAR-02（RULE-387/394）、-03（任职重叠 INV-62）、-04（聚合对应 RegisteredEmployee/AGG-62 与 SyncRun）、-05（迁入写入通道·日历）；对应旧记忆已删。

## 已裁/遗留
- HELD 命令生效日前放行：已裁方案 B（one-architecture，`proposals/W2_HELD_RELEASE_RULING_2026-10-22.md`）：生效日前拒绝放行并报错，到日放行即执行，作业只提醒不改状态；属 W2 命令批实现。
- 领域规则待实现（命令批）：命令不得新引入 `LEGACY_*`/`UNKNOWN` 依据，只能原样复制（领域测试）；`employment_start_date` 晚于生效日的迁入行拒收进 migration_rejects。
- 触发器未参数化；`aggregates.yaml` Employee 聚合对应（-04）；W2 设计 §9 登记“建 org_structure_commands 批次补 `personnel_commands.org_structure_command_id` 外键”由 one-data 补。
- 日历后续：PR-B（四眼流程 + Web/OpenAPI/前端，前提 one-architecture 起轻量 ADR 取代 ADR-0030 第 13 行）、PR-C（使用方记版本）。
- F2 遗留 Low：`LegacyAdapterPlacementTest` 的 `moved` 无断言及措辞、`MergedInbox` 命名、前端“新系统”页签等于“全部”、`StalledExternalSourceIT.ordinarySession` 无调用方。
- 错题本 `quality/defect-bank/one-1a-par.md` 待补 PAR-008（rebase 用错 `--onto`）、PAR-009（定向选测漏掉引用被删字段的测试类）、PAR-010（“已推送”未核对远端）、PAR-011（改 DDL 前未先跑 DdlNamingTest；落后 main 未合并就推，漏了新守护 search_path），并把 PAR-003..006 改为已落实。
- one-ops：`PREDEPLOY_CHECKLIST.md` C-20 过时；运行账号 TEMP 权限核验。

## 工作守则
①本机只跑受影响模块测试，全量交 CI；同一时间只留一个后台构建或 Codex 任务；推送钩子跑 precheck（可能因机器级重任务槽排队，重试即可），改文件/提交与 `git push` 分两次 Bash 调用。②验收目录改动先有裁定进 main，提交说明写 `Acceptance-Ruling:`；改断言/删用例在 PR「改断言」栏单列。③卡与 ADR 字面不一致交 one-architecture。④守护文件、触发器、身份授权代码自己写、opus 首轮复审。⑤陈述“已推送”前 `git fetch && git status -sb` 核对；git 写操作用绝对路径；生成产物不入库（冲突的生成文件按 main 取，build_all 再生）。⑥推前先合最新 main（sync_with_base_branch），防新守护漏网。⑦新增列/表前先对 NAMING_STANDARD：布尔以 is_/has_ 开头，枚举列以 _kind/_status 结尾，CHECK 写成单行；所有自定义函数 `SET search_path = pg_catalog, pg_temp`。⑧回复结尾只写状态；技术问题报 one-coordinator。
