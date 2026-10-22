# one-1a-par 交接（A 线人事与身份）

2026-10-22 晚更新（接替上午的清空交接；本文件整篇替换）。

## 在途
1. **#604 KERR**（通用错误码进 kernel）：已含 main，CI 绿，已报 one-merge，入队后不再推该分支。
2. **#611 PAR-03**（`platform-org-interfaces.txt` 及三项守护、`org_interface_notice.py`，PR 正文须写「已通知平台 owner：…」；PR 正文 edited 事件只跑轻量检查，见 CI_RUNNERS.md）：opus 首轮结论已贴 PR，CI 绿，已报入队。
3. **W2 PR-B**（IMP-1022-PAR-01，分支 `claude/one-1a-par-w2-versions`）：`employee_versions`（含 `employment_start_date`、`employment_start_basis_kind`、月日索引）、`personnel_commands`（`org_structure_command_id` 不加外键，随 `org_structure_commands` 建表批次补）、两个只追加触发器、`employees` 删 `last_sync_run_id`、术语、注册表、`PersonnelVersionTablesIntegrationTest`。one-data 已签。**本 PR 不含工号唯一键**：one-coordinator 定工号全局唯一要走新 ADR-0078（等用户采纳），PR-B 不加 `uk_employees_employee_code`，不改验收目录与 DataArchitectureTest。
   - **PR-B2（待 ADR-0078 采纳）**：工号唯一键 + 重复工号隔离路径 + 验收改动，现成实现在分支 `claude/one-1a-par-w2-employee-code-unique` @0426bf3bb（含 SnapshotReconciler 隔离、`IssueKind.DUPLICATE_EMPLOYEE_CODE`、域测试、DataArchitectureTest 放行；尚缺验收 IT 的四处改动与同号并发用例，裁定 RULING-COORD-20261022-05 已进 main，条件：精确等值断言并核列集合、LengthLimits 第 7、8 例与 RowConstraints 第 7、8 例夹具统一 `'WF11-'||uuidv7()::text`、SourceIdMatchingIT 改为断言拒收并告警）；入队前 one-ops 核各环境 `WORKFORCE_SYNC_ENABLED` 未开。
   - 领域规则待实现（不在本批）：命令不得新引入 `LEGACY_*`/`UNKNOWN` 依据，只能原样复制，需领域测试；`not_after` 违反的迁入行拒收进 migration_rejects。
   - 复审遗留已知问题：HELD 命令生效日前放行只能转 EFFECTIVE（附件触发器规则）与设计 §0.2 冲突，交 one-architecture/one-data；`aggregates.yaml` 的 Employee 聚合对应延后；触发器未按设计参数化。

## 下一步
- **迁入写入通道·日历部分**（one-coordinator 新派，排在 W2 PR-B 之后）：设计 `data/MIGRATION_WRITER_ROLE_DESIGN.md`（分支 claude/one-data-handoff-1）；迁入角色无 UPDATE，日明细守护 SECURITY DEFINER（固定 search_path、体内不看 current_user、有守护断言），CHECK CONFIRMED 必有 confirmed_by；属安全改动，opus 首轮必做，权限部分不交 Codex；判定 4.3 修正 one-architecture 已认可。
- **卡 E**（行政事务员工活动）：W2 PR-B 合入后，`organization.workforce` 补入职日期与「满周年」查询；需求见 `claude/one-1a-admin-docs @5ff4ca55d` 的 `B31_ADM02_BLUEPRINT.md` §4 与卡 E，口径见 `data/EMPLOYMENT_START_DATE_OPINION.md`；设计经 one-arch-people。
- **W2 后续批**（读取改写、命令实现、assignments 去除、position_versions、归属期间表、purge 函数）按 W2 设计第 3、8 节另批，先请 one-data 签。

## 待办池（不插队）
- W2：INV-62 与 `ex_assignment_versions_overlap` 不一致 → 先请 one-data 签；RULE-387、RULE-394 在 platform.identity 未实现，W2 组织命令之后、投产前做；`RegisteredEmployee` 对 AGG-62、`SyncRun` 去留，判定后回 one-architecture。
- 日历后续：PR-B（四眼流程 + Web/OpenAPI/前端，前提是 one-architecture 起轻量 ADR 取代 ADR-0030 第 13 行）、PR-C（使用方记版本）；旧表等 PR-B 接上后下线。
- F2 遗留 Low：`LegacyAdapterPlacementTest` 的 `moved` 无断言及措辞、`MergedInbox` 命名、前端“新系统”页签等于“全部”、`StalledExternalSourceIT.ordinarySession` 无调用方。
- PAR-03 复审遗留（已知取舍）：签名快照覆盖整个接口包；`propagate` 子包未处理；`@Nullable`（TYPE_USE）未纳入快照；CODEOWNERS 文件不存在（既有问题，交调度）。
- one-ops 侧：`PREDEPLOY_CHECKLIST.md` C-20 过时；运行账号 TEMP 权限核验。
- KERR 之后：G-7 其余同义码未收；快照 `ssot/i09-acceptance/tests.yaml`、`panorama.yaml` 旧显示名随下次扫描刷新。
- 错题本 `quality/defect-bank/one-1a-par.md` 待补 PAR-008（rebase 用错 `--onto`）、PAR-009（定向选测漏掉引用被删字段的测试类）、PAR-010（“已推送”未核对远端）、PAR-011（改 DDL 前未先跑 DdlNamingTest：多行 CHECK 被按行解析、布尔与阶段列命名；本批靠测试发现），并把 PAR-003..006 改为已落实。
- 文档分支 `claude/one-1a-par-calendar-plan` 未进 main，报“分支+提交号”由 one-merge 收。

## 工作守则
①本机只跑受影响模块测试，全量交 CI；同一时间只留一个后台构建或 Codex 任务；推送钩子会跑 precheck，改文件/提交与 `git push` 分两次 Bash 调用。②验收目录改动先发清单给 one-coordinator 出裁定、裁定进 main 后才开 PR，提交说明写 `Acceptance-Ruling:`；改断言/删用例在 PR「改断言」栏单列。③卡与 ADR/手册字面不一致交 one-architecture。④守护文件改动 PR 单列并 opus 复审。⑤陈述“已推送”前 `git fetch && git status -sb` 核对；git 写操作用绝对路径；生成产物不入库。⑥身份、授权、守护、触发器类代码自己写不交 Codex。⑦回复结尾只写状态，不向用户提问；技术问题报 one-coordinator。⑧新增列/表前先对 NAMING_STANDARD：布尔以 is_/has_ 开头，枚举列以 _kind/_status 结尾，CHECK 写成单行（DdlNamingTest 按行解析）。
