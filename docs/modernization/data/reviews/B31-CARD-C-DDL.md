# B31 卡 C 员工活动申请 DDL 草案复核

- 复核：one-data，2026-10-22；草案：claude/one-1a-admin-card-c @890e8cd8b，`architecture/proposals/closeout/people/b31-rules/DDL_DRAFT_CARD_C.sql`、`B31_CARD_C_DDL_NOTES.md`；依据：`B31_PHYSICAL_MODEL_RULINGS.md` §14、`B31-CARD-A-DDL.md`、`STATE_TRANSITIONS_DESIGN.md`、`FUNCTION_SEARCH_PATH_PINNING.md`。
- 结论：**数据设计：通过，附三项必改（M1–M3）**；改完在表结构 PR 的预期 diff 里体现，我复核实际 diff 后签表结构 PR。

## 必改

- **M1 三个守护函数都固定 `search_path`**：`guard_employee_activity_request_insert`、`guard_employee_activity_request_change`、`guard_employee_activity_participant_insert`（以及启用时的 3.4 函数）加 `SET search_path = pg_catalog, pg_temp`（`FUNCTION_SEARCH_PATH_PINNING.md` 规则；函数体已全部带 schema 限定）。
- **M2 `version` 默认值**（T-D5）：根表 `version bigint NOT NULL DEFAULT 1`，`ck_employee_activity_requests_version` 改 `version >= 1`。插入触发器强制 `= 1`，默认 0 永远用不上且与「新建保存后为 1」自相矛盾；`event_count` 默认 0 不变。
- **M3 `state_transitions` 第 0 节**：同意收窄（T-D1）；验收测试必须含 NULL 注入样例：`∅ → CONCLUDED`、`∅ → CANCELLED`（非 `MIGRATION`）对 `EmployeeActivityRequest` 被拒，`∅ → CONCLUDED` 带 `cause_kind = 'MIGRATION'` 被接受，`ConfigSetVersion` 的 `∅ → CONFIRMED` 被拒。

## T-D 逐条裁定

| 编号 | 裁定 |
|---|---|
| T-D1 DRAFT 不落库 | 同意（待 one-rules T-R7 确认业务无草稿；若有，回来改，不是我这边的阻断） |
| T-D2 `org_unit_id` | 同意加，口径同 `cost_timesheet.timesheets.org_unit_id`（创建时申请人所在组织单元，不随调岗变）；它是数据范围列，列注释已写 |
| T-D3 业务键幂等 `submission_key` | 同意；长期有效的去重靠业务键，不靠 24 小时窗口 |
| T-D4 三个守护触发器 | 都要；参与人窗口收窄到 `version = 1` 同意，**不用 `xmin` 比较**（环绕与不可验证）。注意：参与人插入守护的 `FOR SHARE` 需要运行角色对根表有 `UPDATE`，运行角色有，不要回收 |
| T-D5 | 见 M2 |
| T-D6 `numbering_rule_version_id` NOT NULL | 同意；迁入需要时追加迁移加 `origin_kind` |
| T-D7 `mutability` | `CONDITIONAL_UPDATE`，`condition` 如草案所写 |
| T-D8 周年自动生成行的 `submission_key` | 本卡 NOT NULL 不变。卡 D 为自动行**合成确定性键**（由周年员工、入职日期、周年日期、活动类型派生的摘要，格式须满足本表 `ck_…_submission_key`，即 16–64 位 `[A-Za-z0-9_-]`），不放宽为可空；卡 D 另加四元组唯一。本卡不为它留列 |
| T-D9 至少一名参与人 | 不启用 3.4，应用层保证；有测试覆盖。不是「能落成约束就落成」的反例：延迟约束触发器未经验证，且迁入若要放宽会多一处例外 |
| T-D10 保留期 | `BUSINESS_RECORD`（同工时单）作暂定；**保留期须用户认可（D12），认可前注册表该项不进表结构 PR**——我把它放进待决定收件箱，由调度提 |
| T-D11 数据分级与审计内容表 | 员工 ID 列沿用先例不登记；`description_text`、`cancel_reason` 登记 INTERNAL + PI 提示；`audit-text-writers.txt` 的内容表新增一行由我在 REG-3 之后与 one-1a-admin 一起做 |
| T-D12 办结人/办结时间 | 不建列，读 `state_transitions` |
| T-D13 预期行数 | 申请：约 10³–10⁴ 行/年；参与人：按人均 20–50 名参与者估约 10⁵–10⁶ 行/年（回写注册表时按此填，投产后用真实量校正） |

## 核对

- 约束 NULL 三值：自查表逐项成立；`ck_…_cancellation` 四列联动正确，终态 `CANCELLED` 之后再改被更新守护拒（`CANCELLED → CANCELLED` 不在合法流转内）。
- 插入守护：配置版本 `CONFIRMED` + 区间含业务日期 + 条目启用 + 重登引用必为已取消；`daterange(from, to)` 默认半开，与卡 A 一致。`FOR SHARE` 锁配置版本行需要运行角色对 `config_set_versions` 有 `UPDATE`，卡 A 只回收 `DELETE`、`TRUNCATE`，成立。
- 索引：列表、数据范围、"我提交的"、复合外键前导均覆盖；重登部分唯一索引可服务外键查找（`= $1` 蕴含 `IS NOT NULL`）。
- 权限回收文本与注册表清单同意；`db_scripts_aligned.py` 要求三处一致、单表一条的写法正确。

数据设计：通过（one-data，2026-10-22，依据 @890e8cd8b；范围：M1–M3 改后的 `employee_activity_requests`、`employee_activity_participants`、第 0 节收窄与三个守护触发器；T-D10 保留期待用户认可）。

## T-D10 已由用户定（2026-10-22 18:49，收件箱 2026-10-22-one-data-card-c-retention，用户原文「A」，intent `2026-10-22-205226-one-data-A`）
`employee_activity_requests`、`employee_activity_participants`、`state_transitions`（本 schema 的员工活动申请聚合部分）保留期 = 10 年后归档到对象存储、在线按期删除（`BUSINESS_RECORD`，同审批四表与工时单）。注册表该项可进卡 C 表结构 PR；到期清理作业随审批、工时的归档一起做，不在卡 C 范围。
