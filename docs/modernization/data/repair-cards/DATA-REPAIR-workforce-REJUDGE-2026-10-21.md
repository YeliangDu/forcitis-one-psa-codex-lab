# DATA-REPAIR-workforce 重判（ADR-0055：同步退役后失去对象的条目）

- 作者：one-data，2026-10-21。起因：one-coordinator 派工；切分方案 PR-D2 删同步表（`sync_runs`、`sync_diffs`、`sync_run_held_separations`、两个 `last_sync_run_id`），`DATA-REPAIR-workforce.md` 的 WF-01/02/07/08/13/14 与 SD-1 失去对象（切分方案第 3 节第 15 条）。依据：ADR-0055 决定 6（「`sync_runs`、`sync_diffs`、`sync_run_held_separations` 随同步用途撤，管理员待放行离职记录按决定 3 保留；已建同步……在投产前删去或仅改为迁入适配器」）。
- 状态：重判结论，待并入主卡（分支同步 main 后在 `DATA-REPAIR-workforce.md` 各条加「已取消」状态行；本页先作依据）。

## 1. 逐条结论

| 条目 | 原内容 | 重判 | 理由与动作 |
|---|---|---|---|
| WF-01 | `sync_runs` 状态与原因/时间字段成对 CHECK（四条） | **取消，随表删除** | 表随 PR-D2 删。取消后不做；若 D2 迟于首次部署，基线开放期库可重建，不产生迁移负担 |
| WF-02 | `sync_runs` 其余约束（released_kind_codes、计数、长度） | **取消** | 同上 |
| WF-07 | `sync_diffs.issue_kind` 无 CHECK、与 `change_kind` 无成对 | **取消**；D2 须同时删 `ddl-naming-baseline.txt:8`（该列的待补登记） | 表删后基线行变成「已修好却没删」，会让 `DdlNamingTest` 失败，D2 的清单要写上 |
| WF-08 | 登录路径热点查询（`max(finished_at)`）无合适索引 | **取消** | 热点本身随 `SnapshotFreshness`（PR-D1）删除；替代的新热点是「员工在业务日的有效版本」，索引在 W2 设计里（`ex_employee_versions_overlap` 的 gist、`uk_…_revision`），不另起条目 |
| WF-13 | `sync_runs.released_by_run_id` 无外键；`last_sync_run_id` 指针悬空 | **取消** | 表与两个 `last_sync_run_id` 列一并删；`employees.last_sync_run_id` 随 W2 删，`org_units.last_sync_run_id` 由 D2 删（D2 清单须列：这两列与注册表 `fk_exceptions` 的对应行） |
| WF-14 | `sync_diffs` 登记 APPEND_ONLY，库内未禁 UPDATE | **取消** | 表删 |
| SD-1（高，§6 不含表） | `sync_diffs.subject_key` → 类别 + `subject_id uuid`（O15、ADR-0033 第 7 项第 2 条） | **取消（对象消失）；要求带入迁入核对设计** | `sync_diffs` 不再建。ADR-0055 决定 5 保留的迁入核对（`OrgReconciliation` 改迁入核对）若要落差异明细表，其「对象」列必须一开始就按类别 + `subject_id uuid` 设计，不写 `subject_key text`；写进迁入核对卡（切分方案 I）的数据设计要求 |

## 2. 连带受影响但不在派工清单里的条目（请一并确认）

| 条目 | 重判 | 理由 |
|---|---|---|
| WF-03 `sync_run_held_separations` 注册表保留期 | **取消并改写** | 该表随同步用途撤；待放行离职的新载体由 one-architecture 定结构（切分方案 3.2），其保留期随载体另判 |
| O15 缺项 4（held → `separation_hold`） | **由新载体设计承接** | 载体结构未定，数据设计待 one-architecture 的裁定 |
| O15 缺项 3（`sync_*` 迁 `platform_integration`） | **取消** | 不再建设（ADR-0055 决定 6 对 ADR-0039 决定 2、6 的取代） |
| O15 缺项 5（`employees` 删 `login_name`、`wecom_user_id`，建 `external_identities`） | **保留，并入 W2** | 企业微信绑定仍需要外部身份对照（ADR-0055 决定 2）；W2 设计 2.1 已写 |
| WF-10 的 sync_* 登记缺口、WF-11 的 `sync_diffs.detail` 长度 | **取消相应部分** | 表删；WF-10、WF-11 其余部分保留 |
| WF-06 `assignments` 的 `position_id` 可空、本人不是自己的直属领导 | **改写：约束搬到 `assignment_versions`** | W2 设计 2.4 建议去除 `assignments`（版本表为唯一来源）；若复核选保留 `assignments`，则原条目不变 |
| P-1、E-1、E-2、LV-1(b) | **由 W2 设计承接** | 见 `W2_EMPLOYEE_POSITION_VERSIONING_DESIGN.md` |
| WF-04、WF-05、WF-09、WF-12 | **不受影响** | 不涉同步表（WF-05 的 `org_units` 状态与 `revoked_at` 成对约束若 org_units 根表日后退成身份表，随 org_units 版本化再议） |

## 3. 给 PR-D2 的清单（删表时必须同批删的登记）

- 表：`sync_runs`、`sync_diffs`、`sync_run_held_separations`（先有新载体，PR-B）；列：`employees.last_sync_run_id`、`org_units.last_sync_run_id`。
- 登记：`data-access-registry.yaml` 的对应条目与 `fk_exceptions`；`ddl-naming-baseline.txt` 中与这些表有关的行（含 WF-07 提到的第 8 行、`sync_*` 相关改名行）；`data-classification.txt` 相关列；`primary-key-exemptions.txt`、`status-write-exceptions.txt`、`repository-id-exceptions.txt` 中涉及同步的行；`one_db_roles_schemas.sql` §4b 无涉及。
- 审计主体类型 `SYNC_RUN` 与既有审计条目的处置（切分方案 3.14）：数据侧建议保留枚举值直到存量审计条目过保留期，不回改历史条目；新写入不再用。

## 4. 影响面小结

- 取消 7 条（WF-01、02、07、08、13、14，SD-1），改写 3 条（WF-03、WF-06 视 2.4 复核结论、O15 缺项 4），保留并入 W2 3 条（O15 缺项 5、P-1、E-1/E-2/LV-1(b)）。
- 主卡「级别统一分级」的合计（高 20、中 91、低 70，`BASELINE_TABLE_REVIEW.md`）相应减少，合并主卡更新时重算。
