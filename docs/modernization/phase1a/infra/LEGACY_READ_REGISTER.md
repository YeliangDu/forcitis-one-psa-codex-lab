# 老系统与 A8 生产数据读取登记

ADR-0037 第 7 项：生产取证不再逐次请示，每次读取登记一行。明细与个人信息不进会话记录与仓库，只出计数 / 聚合，或落 ECS 数据盘；凭据值任何情况下不输出。2026-10-15 之前的读取按当时的逐次批准补登。

执行账号：`u8c` 是老系统主数据源（RDS SQL Server 的高权限账号，经老系统应用服务器的只读探针 `LegacyReadOnlyProbe` 或 `OneSql ro` 读取）；ADR-0037 起取证改用 `one_evidence_ro`，高权限账号只用于授权变更本身。

| 日期 | 执行账号 | 库与表 | 用途 | 输出形式与去向 | 落盘目录与删除时点 | 依据 |
|---|---|---|---|---|---|---|
| 2026-10-15 | u8c | ruoyi-vue-pro：`ACT_HI_VARINST`、`ACT_HI_PROCINST`、`ACT_GE_BYTEARRAY`（工时流程定义的两个变量） | 工时类型变量的序列化形式取证 | 变量名、类型、长度与序列化类名，会话内 | 不落盘 | one-coordinator 逐次批准 |
| 2026-10-15 | u8c | a8_db：岗位相关表（ADR-0029 取证） | ADR-0029 岗位口径取证 | 计数与结构，会话内 | 不落盘 | one-coordinator 逐次批准 |
| 2026-10-15 | u8c | a8_db：`formmain_0348`（离职表单，`field0031` 离职日期）、`COL_SUMMARY`（`STATE = 3`） | 离职日期来源取证 | 计数、分布与重复工号条数，会话内 | 不落盘 | one-coordinator 逐次批准 |
| 2026-10-15 | u8c | a8_db：离职规则相关表（同上表单与流程状态） | 离职规则取证 | 计数，会话内 | 不落盘 | one-coordinator 逐次批准 |
| 2026-10-15 | u8c | ruoyi-vue-pro：`INFORMATION_SCHEMA.TABLES / COLUMNS`、`dbo.system_operate_log`、`dbo.system_login_log` | 审计写入量（DDL-24 分区粒度） | 按月 COUNT(*)，会话内与跨会话消息 | 不落盘 | one-coordinator 批准（对象限定） |
| 2026-10-15 | u8c | a8_db：`dbo.ORG_MEMBER`（STATE、IS_DELETED、IS_ENABLE、ORG_DEPARTMENT_ID 作关联键）、`dbo.ORG_UNIT`（TYPE、PATH） | R-1b-0 标志列取证 | 分组计数，会话内与跨会话消息 | 不落盘 | one-coordinator 批准（对象限定） |
| 2026-10-15 | u8c | a8_db 与 ruoyi-vue-pro：`sys.database_principals`、`sys.database_permissions`、`sys.database_role_members`、`sys.schemas`；实例：`sys.server_principals`、`sys.server_permissions`、`sys.server_role_members`、`sys.fn_builtin_permissions`、`SERVERPROPERTY` | 新系统只读账号授权盘点与 ADR-0037 账号调整 | 授权行（无数据值），会话内与 B-5 README | 不落盘 | one-coordinator 批准；ADR-0037 |
| 2026-10-15 | — | 无（只读仓库 `SEEYON_VIEWS.sql` 核对视图列） | 迁入 ID 对照前的视图检查 | 会话内 | 不落盘 | — |
| 2026-10-15 | one_a8_views_ro、psa_one_legacy_ro、one_legacy_names_ro、one_evidence_ro | a8_db：`ORG_MEMBER`、`ORG_UNIT`、`INFORMATION_SCHEMA.COLUMNS`、`sys.tables`；ruoyi-vue-pro：`system_users`、`system_dept`、`system_oauth2_access_token`、`INFORMATION_SCHEMA.COLUMNS`、`sys.tables` | 授权变更后真实登录验证 | COUNT(*) 与有效权限名，B-5 README | 不落盘 | ADR-0037；one-coordinator 确认的变更清单 |
| 2026-10-15 | one_evidence_ro | a8_db 与 ruoyi-vue-pro：`INFORMATION_SCHEMA.COLUMNS` | 凭据列拒绝清单（ADR-0037 第 3 项第 4 条） | 库、表、列名，`handoff/B-5-dba/evidence/credential_columns.csv` | 不落盘 | ADR-0037 |
