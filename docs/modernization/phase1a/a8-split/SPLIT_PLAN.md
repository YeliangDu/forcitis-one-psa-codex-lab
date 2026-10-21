# IMP-1020-PAR-01 切分方案：按 ADR-0055 拆 A8 同步、改本地人事命令

- 作者：one-1a-par，2026-10-20；依据 ADR-0055 全文、基线 main `8cddaee3`；盘点明细见同目录 `INVENTORY.md`（opus 只读盘点，每条带文件:行号；“有/没有”断言已 grep，未核实处原文标注）。
- 状态：one-coordinator 2026-10-20 已批；R、F 先做（R = #489），A、B 及其依赖项等 one-architecture 的 ADR-0055 补篇（owner 2026-10-21 裁定，见 #491 评论）。在制 ≤1，按下表顺序一单元一 PR。

## 1. 关键事实

1. `organization_workforce.employees` 现在只由同步（`ApplySourceSnapshot`）和登录补建（`LoginMissRegistration`）写入：**先有人事命令，才能删同步**。
2. identity 的离职收回、停用应急账号、开账号全订阅 `OrgSnapshotChanged`；现在的收回走旧表 `account_roles`，`account_role_grants`（#476 已建）在 main 上没有 Java 读写。
3. `revocation_sync_run_id` 已在 identity 基线（随 #476 合入），基线开放期可直接改名为 `revocation_separation_command_id`。
4. `OrgDirectory` 不能删，只改实现（cost、approval、identity 约 25 处在用；仅 `findEmployeeByLogin` 补建、`employmentOf` 读同步运行判断快照过期两个方法碰外部）。
5. 两处与 ADR-0055 直接冲突的现状：①应急登录在快照不可用时告警放行（`LoginAsEmergencyAdmin.java:109`），ADR-0055 决定 3.1 要求本地记录缺失即拒；②`sync_run_held_separations` 以 `ON DELETE CASCADE` 挂在 `sync_runs` 上，删 `sync_runs` 前须先有新载体。
6. ADR 要求的“放行后 0 名超管只能双人在场走运维命令”在 main 上没有实现（只有 `ReleaseSyncRunHolds.java:39` 报错文字提到）。

## 2. 切分

| 序 | PR | 依赖 | 涉表（one-data 签发） | 安全项（不交 Codex） | 可交 Codex |
|---|---|---|---|---|---|
| 0 | 文档：架构裁缺口（第 3 节）；新人事命令卡；改写 ADR-0034-IMPL PR-3、F-1、WF-REINSTATE、W2 | — | 设计签发 | — | — |
| R | `revocation_sync_run_id` 改名（含 CHECK、触发器、3 个测试） | 可立即做，须早于 C | 是 | 是 | 否 |
| F | 删老待办（含 OpenAPI、web/h5 前端同批） | 无 | 否 | 否 | 可 |
| G | 删部门简称刷新与 `LegacyOrgNames` | 先裁 `short_name` 由谁维护 | 视裁定 | 否 | 可 |
| A | 人事命令契约先行（事件、命令 ID、生效日） | 0 | 否 | 是 | 否 |
| B | 人事命令实现、待放行离职新载体、单条放行、0 名超管运维命令 | A、W2 设计 | 是 | 是 | 否 |
| C | identity 改订阅新事件（即 ADR-0034-IMPL PR-3 重定范围），同批删旧订阅 | B、R、PR-2 | 否 | 是 | 否 |
| D1 | 在职判定本地化：删 `SnapshotFreshness`，应急登录缺失即拒，替身同批改 | C | 否 | 是 | 否 |
| D2 | 删同步：表、事件、接口、权限、配置、前端、登记、随删测试 | D1 | 是 | 否 | 可（opus 终审） |
| E | 删登录补建与 `EmployeeRegistered` | C | 否 | 是 | 否 |
| H | 先删致远 SSO；签名票据与两张票据表等身份生命周期研究与 T-13 | 研究与 T-13 | 后半是 | 是 | 否 |
| I | seeyon 改为一次性迁入适配器，`OrgReconciliation` 改迁入核对 | ADR-0036 迁入卡 | 视设计 | 否 | 部分可 |

最先可做、与待裁定无关的：**R**（改名）与 **F**（删老待办）。其余等第 3 节裁定。

## 3. 待裁定（原样列出，不由本会话裁定）

交 one-architecture：
1. 人事命令的表结构、聚合、命令 ID 的类型与生成方、事件契约；离职、复职、变更命令是否共用一个 ID 空间。
2. 待放行离职新载体的结构（ADR-0055:68 只说“改挂离职命令”）。
3. “停薪留职”无对应在职状态（现仅 EMPLOYED / SEPARATED），是否收回授予、吊销会话。
4. ADR 只列人员类命令，无组织单元与岗位维护命令；F-1 来源②（部门撤销或合并）因此无触发源。
5. F-1 的 `trigger_sync_run_id`、`closure_sync_run_id`、`expiry_held_sync_run_id` 三列怎么改。
6. ADR-0034 第 272 行“依据同步观察日（OBSERVED）”未被 ADR-0055 取代，如何处置。
7. `employees.login_name`（“为空即离职”）、`separated_at`、`last_sync_run_id`（`employees` 与 `org_units` 都有）撤同步后的去向；运维命令里按 A8 登录名查人的分支。
8. `org_units.short_name` 删 `LegacyOrgNames` 后由谁维护（前端组织页在用）。
9. 登录时补开账号（`LoginAdmission.java:59`）留不留。
10. 致远 SSO 能否不等身份生命周期研究先删；签名票据、两张票据表、mssql 测试镜像的去留。
11. 哪些改迁入适配器（seeyon 一套、`OrgReconciliation`），由哪张卡承担。
12. 来源对照“投产后全表只读”的落地方式（触发器、授权还是应用层）。
13. ADR-0055:98 引用的 ADR-0037 条号与原文对不上（决定 5 是“账号”，启动校验在决定 4）。
14. 审计主体类型 `SYNC_RUN` 与已有审计条目的处置。

交 one-coordinator：
15. W2（员工与岗位版本化）的负责会话：ADR 写交 one-data，one-1a-next-5 画像写“不碰 W2”；D2 删表后 DATA-REPAIR-workforce 的 WF-01/02/07/08/13/14 与 SD-1 失去对象，须 one-data 重判。
16. 文档与台账滞后（owner 2026-10-21 归 A0，已订正）：`process/1a/组织与人员同步.md` 已标退役；`TASKS.md` 由 `tasks/` 片段生成，`IMP-1017-PAR-01/02` 状态已订正为已合并；`ADR-0034-IMPL.md` 卡头状态已改。

## 4. 衔接

- ADR-0034-IMPL：PR-2 照常推进；PR-3 改成上表 C，卡里 A-10、A-13、验收 G4-01 重写。
- WF-REINSTATE（one-1a-next-5，分支 `claude/one-1a-next-5-docs4`）：依赖同步内复职判定，已不成立，需撤或改为复职命令事件。
- F-1：排在 C 之后。
- D1 会删 `EmergencyLoginIntegrationTest:209` 的“快照不可用放行”断言，依据 ADR-0055:66，PR 中单列理由（CLAUDE.md 第 5 条）。
