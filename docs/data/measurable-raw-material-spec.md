# 建表规范草稿：业务库 DDL 的「可度量原料」五条

- 作者：one-data，2026-10-17（度量设计第③项，one-coordinator 派工，one-architecture 对口；依据 Kimball《数据仓库工具箱》第 3 版的事实表类型、Adamson 的星型模式、Snodgrass 的时态数据）。
- 状态：**草稿**。采纳后写入 NAMING_STANDARD 新节（建议 §2.4b）与 DATA_DESIGN_CHECKLIST §14 的建表检查；我的分支落后 main 约 1000 提交，等用户在应用里确认 origin 后同步，再改这两个共享文件（避免合并冲突）。检查脚本 `scripts/ci/measurable_ddl_scan.py` 已写好，**现在只报告**，接进 `build_all --check` 的时点见第 7 节。
- 总线矩阵对齐：行（度量事件）与列（维度）的口径由 one-architecture 定，草稿出来后我对这五条里与之相关的项（时间、币种、单位、状态转换、版本）逐项核对。

## 1. 金额带币种，数量带单位（M1）

- 规则：业务表里数值型的金额列（名字含 amount、price、fee、cost、salary、balance、total）必须在同一张表有币种列（`currency_code` 或 `<x>_currency_code`）；数量列（名字含 quantity、qty）必须有单位列（`unit_code` 或 `<x>_unit_code`），单位已写在列名里的（`_hours`、`_minutes`、`_days`、`_percent`、`_rate`）除外。
- 为什么：没有币种与单位，同一指标会在汇总时把不同量纲相加（Kimball：可加度量必须同量纲）。
- 现状：对 main 扫描 0 违反（现有 DDL 没有金额列，工时的 `submitted_hours` 单位在名字里）。提议表只有列族名，关键字粗扫不可靠，等进卡有 DDL 再扫。
- 例外：币种由法人本位币唯一确定、且表带法人列时，可在注册表写 `currency_from_legal_entity: true` 代替币种列（待 one-architecture 对总线矩阵确认后启用，脚本尚未实现该例外，实现时同步改）。

## 2. 业务事实的三个时间（M2）

- 规则：业务单据与事实表（注册表 role 为 BUSINESS_DOCUMENT，或登记了 `business_date_column`）必须同时有：**业务发生日**（注册表 `business_date_column` 指向的 `date` 列）、**记账或确认时刻**（`*_confirmed|posted|booked|submitted|approved|recorded|settled_at` 或 `_date`）、**录入时刻**（`created_at`）。
- 为什么：迟到事实、期间归属、账龄与时效都要在三个时间里选一个，缺一个就无法按口径重算。
- 现状：对 main 扫描 0 违反（工时单三个时间齐）。
- 与 ADR-0058：状态转换行另有 `occurred_on`（业务日期）与 `recorded_at`，不替代主表的三个时间。

## 3. 状态转换历史（M3，按 ADR-0058）

- 规则：有状态列（名为 `status` 或以 `_status` 结尾，且有枚举 CHECK）的表，所在 schema 必须有按 ADR-0058 模板建的 `state_transitions` 表，或在注册表写 `state_transitions_exempt: <理由>`（逐行评审）；版本表自身即历史，不需要。
- 模板、权限、清理通道、投影口径见 `docs/data/state-transitions-design.md`（第 2 版，待复核签发）。
- 现状：对 main 扫描 14 张（见 `docs/data/state-transition-tables.md`），按 ADR-0058 分批补；2 张登记例外（`sync_runs`、`outbound_deliveries`）。

## 4. 主数据按生效区间留版本（M4）

- 规则：注册表 `data_category` 为 **主数据** 且可变（MUTABLE、REPLACE_SET）的表，同 schema 必须有它的版本表（`<单数名>_versions`，维度型，生效依据 DECLARED 或 OBSERVED，D17），或在注册表写 `versioning: none:<理由>`（理由限 D17 细则 2 的四类）。
- `data_category` 为注册表新字段，五选一：**主数据 / 交易 / 参照 / 配置 / 技术**（one-coordinator 2026-10-17 同意）。未写该字段时脚本退回「按 role 的候选清单」。
- 现状：对 main 扫描 7 个候选，其中员工、岗位、工作日历、法人归属是真缺口；同步运行、授权申请、账号三个待 `data_category` 排除。

## 5. 关键关联落成外键（M5）

- 规则：列名为 `<x>_id`、且 `<x>`（或其复数）是**同 schema** 的表时，必须有 FOREIGN KEY；跨 schema 的 ID 按设计只存 ID，不报；确需不建的写进注册表 `fk_exceptions`（带理由，现行机制）。
- 现状：对 main 扫描 0 违反。
- 备注：关联不能只放在注释或自由文本里；N↔N 用关联表，两列联合唯一（门禁 B4）。

## 6. 注册表新增字段（均为可选，缺省按现行）

| 字段 | 取值 | 用途 |
|---|---|---|
| `data_category` | 主数据 / 交易 / 参照 / 配置 / 技术 | M4 判定；也是对象 ↔ 表双向核对的分类依据 |
| `state_transitions_exempt` | 理由文字 | M3 例外 |
| `versioning` | `none:<理由>` | M4 例外 |
| `business_date_column` | 列名 | 已有，M2 使用 |
| `state_transitions_legal_entity` | `required` / `none:<理由>` | ADR-0058 行为守护用（按聚合类型） |

## 7. 脚本与接入

- `scripts/ci/measurable_ddl_scan.py`：M1–M5，`--self-test`，`--root`，`--json`；复用 `baseline_ddl_scan.py` 的解析器（同目录）。对 main 的首次结果：`docs/data/measurable-ddl-scan-2026-10-17.txt`。
- 接入分两步：①**报告**（现在）：脚本只报告，不让任何检查失败；②**基线只减不增**：把 main 现有违反登记成基线（同 `ddl-naming-baseline.txt` 的做法），新违反失败，修好未删基线也失败，接进 `build_all --check` 与 `precheck.sh` 的 DDL 步骤。第②步要先同步 main 才能改这两个共享脚本，并走 PR 与 opus 复审（守护代码由 Claude 写，不交 Codex）。
- 提议表的扫描在其卡有 DDL 之后用同一脚本做，进门禁条目 O9/O10 旁新增一条「可度量原料」自测。

## 8. 待定

1. M1 的 `currency_from_legal_entity` 例外是否启用（总线矩阵口径）。
2. `data_category` 在注册表落地的时点与回填（现有 51 条要逐条填）。
3. 采纳后的条款编号与措辞。
