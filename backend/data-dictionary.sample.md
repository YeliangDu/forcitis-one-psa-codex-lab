# Data dictionary (generated from PostgreSQL catalog)

> 冻结（2026-11-04，#1263）：数据字典输出的样例，不再更新；现行输出由 `ApplicationIntegrationTest` 写到 `target/data-dictionary.md`，ADR-0011 仅作历史引用。

## delivery_execution.work_packages — 任务包（WorkPackage）：WBS 最底层的工时承载单元

| column | type | nullable | description |
|---|---|---|---|
| id | bigint | NO | 任务包 ID（沿用老系统雪花 ID） |
| planned_hours | numeric | NO | 计划工时（小时，2 位小数） |
| actual_hours | numeric | NO | 实际工时（已审批，小时） |
| status | text | NO | 状态：IN_PROGRESS / DONE / LOCKED / REWORK |
| created_at | timestamp with time zone | NO | 创建时间 |
| updated_at | timestamp with time zone | NO | 更新时间 |
| version | bigint | NO | 乐观锁版本 |

## platform_numbering.numbering_counters — 编号计数器：按单据类型 × 法人 × 周期计数，与业务写入同事务递增（不跳号）

| column | type | nullable | description |
|---|---|---|---|
| document_type | text | NO | 单据类型 |
| legal_entity_id | bigint | NO | 法人主体 ID |
| period_key | text | NO | 周期键：- / 2026 / 202610 / 20261014 |
| last_value | bigint | NO | 已发出的最大流水号 |
| created_at | timestamp with time zone | NO | 创建时间 |
| updated_at | timestamp with time zone | NO | 更新时间 |

## platform_numbering.numbering_rules — 编号规则：每种单据一条，可由管理员配置

| column | type | nullable | description |
|---|---|---|---|
| document_type | text | NO | 单据类型（DocumentType 枚举名） |
| pattern | text | NO | 编号模板，如 QU-{yyyy}-{seq:5}；占位符 {LE} {yyyy} {yy} {MM} {dd} {seq:N} |
| reset_policy | text | NO | 流水号重置周期：NEVER / YEARLY / MONTHLY / DAILY |
| start_value | bigint | NO | 每个周期的起始流水号（切换时可承接老系统最大值） |
| is_active | boolean | NO | 是否启用 |
| created_at | timestamp with time zone | NO | 创建时间 |
| updated_at | timestamp with time zone | NO | 更新时间 |
| version | bigint | NO | 乐观锁版本 |
