# DATA-REPAIR-workforce 重判（ADR-0055：同步退役后失去对象的条目）——第 3 版

- 作者：one-data，2026-10-21。第 1 版 @ae8e4621；独立复核 R-01…R-04 已改（`R-W2设计复核-2026-10-21.md` @13dd137e）；改后复核剩余两处（`repository-id-exceptions.txt:3`、三个 SyncRun 错误码）已补（`R2-W2设计改后复核-2026-10-21.md`）。
- 起因：one-coordinator 派工；切分方案 PR-D2 删同步表（`sync_runs`、`sync_diffs`、`sync_run_held_separations`、两个 `last_sync_run_id`），`DATA-REPAIR-workforce.md` 的 WF-01/02/07/08/13/14 与 SD-1 失去对象（切分方案第 3 节第 15 条）。依据：ADR-0055 决定 6、ADR-0068 第 2、7 条。
- **前提（R-04）**：以下「取消」成立的前提是 **D1、D2 在基线关闭前落地**。基线开放期可重建库、不产生迁移负担；基线关闭后再删表要按 ADR-0038 追加迁移，且 `sync_run_held_separations` 的 `ON DELETE CASCADE` 要在迁移里先断言行数为 0（ADR-0068 第 2 条 5）。
- 状态：重判结论，待并入主卡（分支同步 main 后在 `DATA-REPAIR-workforce.md` 各条加「已取消」状态行；本页先作依据）。

## 1. 逐条结论

| 条目 | 原内容 | 重判 | 理由与动作 |
|---|---|---|---|
| WF-01 | `sync_runs` 状态与原因/时间字段成对 CHECK（四条） | **取消，随表删除** | 表随 PR-D2 删（前提见上） |
| WF-02 | `sync_runs` 其余约束 | **取消** | 同上 |
| WF-07 | `sync_diffs.issue_kind` 无 CHECK | **取消**；D2 同批删基线登记行（第 3 节） | 不删会「已修好却没删」 |
| WF-08 | 登录路径热点查询无合适索引 | **取消** | 热点随 `SnapshotFreshness`（PR-D1）删除；替代热点（员工在业务日的版本、任职路径）由 W2 设计第 3.4、4 节承接 |
| WF-13 | `sync_runs.released_by_run_id` 无外键；`last_sync_run_id` 悬空 | **取消** | 表与两个 `last_sync_run_id` 一并删（归属见第 3 节） |
| WF-14 | `sync_diffs` 登记 APPEND_ONLY，库内未禁 UPDATE | **取消** | 表删 |
| SD-1 | `sync_diffs.subject_key` → 类别 + `subject_id uuid` | **取消（对象消失）；要求带入迁入核对设计**（见第 4 节） | |

## 2. 连带条目

| 条目 | 重判 | 理由 |
|---|---|---|
| WF-03 `sync_run_held_separations` 注册表保留期 | **取消并改写** | 表随同步用途撤；新载体 `separation_holds`（ADR-0068 第 2 条）的保留期随命令表，随 `reviews/ADR-0068.md` 审查 |
| O15 缺项 3（`sync_*` 迁 `platform_integration`） | **取消** | 不再建设 |
| O15 缺项 4（held → `separation_hold`） | **由 ADR-0068 第 2 条承接** | 数据设计意见见 `reviews/ADR-0068.md` |
| O15 缺项 5（`employees` 删 `login_name`、`wecom_user_id`） | **保留，并入 W2** | `login_name` 随 D2 删，**不**进 `external_identities`，进来源对照/迁入暂存（W-12）；`wecom_user_id` 随 DA-3 落地同批删 |
| WF-10 的 sync_* 登记缺口、WF-11 的 `sync_diffs.detail` 长度 | **取消相应部分** | 表删；其余保留（R-04：合并主卡重算级别合计时计入这两条的部分取消） |
| WF-06 `assignments` 的 `position_id` 可空、本人不是自己的直属领导 | **改写：约束搬到 `assignment_versions`（已确定）** | 复核 2.1 结论：`assignments` 去除；约束写法见 W2 设计第 3.4 节（`position_id` 对 `COMMAND` 行必填，迁入行允许空；`reports_to_employee_id` 不等于本人） |
| P-1、E-1、E-2 | **由 W2 设计承接** | |
| **LV-1(b)** 法人代码跨时间排他约束 | **由 W2 设计第 3.5 节承接（R-03）** | 代码移到根表 `legal_entities.code`，`UNIQUE`，发号后不可改（DATA-RULE-03），版本表只留名称与状态，不需要 `EXCLUDE (code …)` |
| WF-04、WF-05、WF-09、WF-12 | **不受影响** | WF-05（`org_units` 状态与 `revoked_at` 成对约束）若 `org_units` 根表日后收窄（W2 设计第 9 节），随该设计再议 |

## 3. 给 PR-D2 的同批删除清单（R-01，核对 origin/main 的行号，复核补齐漏项）

**表与列**：`sync_runs`、`sync_diffs`、`sync_run_held_separations`（先有新载体，PR-B）；`employees.last_sync_run_id` 归 **W2（PR-B 同批）**，`org_units.last_sync_run_id` 归 **D2**——避免两边都以为对方会删。

**`backend/architecture/` 下的登记**（行号为 2026-10-21 的 origin/main，动手前再核一次）：
- `data-access-registry.yaml`：:69、:79（两条 `fk_exceptions`）、:89（注释）、:104-124（三张表）。
- `ddl-naming-baseline.txt`：:7（`sync_diffs.detail`）、:8（`sync_diffs.issue_kind`）、:33-35（`sync_diffs`/`sync_runs` 的主键、外键改名）、:59-61（`sync_diffs`/`sync_runs` 的索引改名）。共 8 行，不删会「已修好却没删」，漏一行守护失败。
- `multi-aggregate-exceptions.txt:6`（`ApplySourceSnapshot`）。
- `typed-text-key-exceptions.txt:5`（`sync_diffs.subject_key`，SD-1 的登记痕迹）。
- `source-type-exceptions.txt:5、:9`（`ApplySourceSnapshot`、`SyncOrgSnapshot`）。
- `unversioned-table-writes.txt:6、:7、:27`。
- `versioned-table-unconditional-writes.txt:10`（`SyncPurgeJob`）；:4 的 `updateShortNames` 视 G（部门简称）的裁定。
- `audit-text-writers.txt:12`（`RecordSyncRunRelease`）。
- `repository-id-exceptions.txt:3`（`JdbcSyncRunRepository.insertRunning`；不删守护报「已修好却没删」）；`unversioned-table-writes.txt:6、:7` 在 `JdbcSyncRunRepository` 删除后随之失效，若该仓储另有其他表的写入行需一并核。
- `primary-key-exemptions.txt:3` 只是注释，不动。
- **已核无**：`data-classification.txt` 与 `status-write-exceptions.txt` 没有同步表的行（`data-classification.txt:20-21` 是 `login_name`、`wecom_user_id`，不属 D2，属 W2/DA-3）。`one_db_roles_schemas.sql` §4b 不涉及同步表；W2 要把三张新版本表加进去。

**错误码词表**：`backend/glossary/proposed/fix-b.yaml:125`（`OrganizationWorkforceSyncRunApplyFailed`）、`organization_workforce.yaml:37`（`…SyncRunTransitionIllegal`）、`:52`（`…SyncRunNotFound`）——错误码中文名全局唯一，退役经 one-glossary，不自行删。

**文档与词表**：`docs/architecture/ddd/aggregates.yaml:93-129`（SyncRun 聚合及五个事件）；`glossary/terms.yaml` 中 SyncRun 及 `SyncRun*` 事件条目——**经 `new_term_issue.py` 交 one-glossary 退役，不自行删除**；`data/RETENTION_PROPOSAL.md:23`；DATA_DESIGN_CHECKLIST §14「已登记的例外」中 `BeginSyncRun`、`ApplySourceSnapshot` 两条；前端 `frontend/apps/web/src/modules/workforce/hooks/use-workforce.ts`（同步接口调用方，D2 已写前端，清单点名）。

**审计主体类型 `SYNC_RUN`**：#491 用户硬要求第 4 条，先完成审计类别登记表（ADR-0068 第 14 条，我的审查见 `reviews/ADR-0068.md`），再把 `SYNC_RUN` 标为 `RETIRED`；既有审计条目只追加不改写（D8）；新写入不再使用。D2 先合并时只删写入方（ADR-0068 第 14 条 2）。

## 4. SD-1 带入迁入核对设计的要求（R-02）

- **负责人**：one-data。**触发点**：迁入核对卡（切分方案 I）进入数据设计签发时；承担该卡的会话由 one-coordinator 定（切分方案 C 类第 11 条）。
- **写法**：迁入核对若要落差异明细表，其「对象」列：
  - 指向自有对象时：类别 + `subject_id uuid`；
  - 只有来源方（还没匹配或被拒收，没有自有 ID）时：引用暂存行 ID 或来源对照 ID（都是 uuid）；
  - 两者**恰好有一个非空**，用 CHECK 约束；
  - 一律不写 `text` 拼接键（O15、ADR-0033 第 7 项第 2 条）。
- D2 删除 `typed-text-key-exceptions.txt:5`，以后不再登记同类例外。

## 5. 影响面小结（R-04）

- 取消 6 条（WF-01、02、07、08、13、14）加 SD-1 的对象部分；部分取消 2 条（WF-10、WF-11 的同步部分）；取消并改写 WF-03；O15 缺项 3 取消、缺项 4 由 ADR-0068 承接；改写 WF-06（落到 `assignment_versions`）；LV-1(b) 由 W2 第 3.5 节承接；并入 W2：P-1、E-1、E-2、O15 缺项 5。
- 主卡「级别统一分级」合计（高 20、中 91、低 70，`BASELINE_TABLE_REVIEW.md`）相应减少，合并主卡时重算。
