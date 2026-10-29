-- R0 · Q11：BI 使用量与 T_FST_* 视图元数据——只读查询（草稿，未执行，执行前须经用户确认访问方式）
-- 用户决定（BI_SCOPE §6.1 Q11）：「同意，放在 R0 做 (Recommended)」——授权只读、只取计数，不取用户明细。
-- 访问方式（拟）：沿用已授权的 PSA 生产只读通道（docs/legacy/runtime-usage/README.md、OA_PROCESS_INVENTORY.md §1）：
--   经阿里云云助手在 PSA 应用服务器运行单文件程序，以 PSA 数据源只读连接 + READ UNCOMMITTED 访问同一 SQL Server 实例上的 BI 库；
--   程序拒绝非 SELECT 语句，单条超时 150 秒；凭据只在服务器进程内使用，不打印、不回传；临时目录用后删除。
--   若第 0 段显示该账号无权访问 BI 库，立即停止并报告，不改用任何其他凭据。
-- 产出：只回传聚合计数与元数据；结果落在仓库外 ~/.modernize/psa/bi-inputs/runtime/，仓库只写汇总结论。

-- 0) 账号能否访问 BI 库（只读探测，无数据）
SELECT DB_ID('BI') AS bi_db_id, HAS_DBACCESS('BI') AS has_access;

-- 1) 审计表的列（只取列名与类型，用于确定时间列、路径列的名称；后续查询按实际列名替换 <时间列>、<路径列>、<用户列>）
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE
FROM BI.INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME IN ('api_audit_logs', 'export_audit_logs')
ORDER BY TABLE_NAME, ORDINAL_POSITION;

-- 2) 审计窗口
SELECT 'api' AS log, MIN(<时间列>) AS earliest, MAX(<时间列>) AS latest, COUNT_BIG(*) AS rows_total
FROM BI.dbo.api_audit_logs WITH (NOLOCK)
UNION ALL
SELECT 'export', MIN(<时间列>), MAX(<时间列>), COUNT_BIG(*)
FROM BI.dbo.export_audit_logs WITH (NOLOCK);

-- 3) 按月：调用次数与去重用户数（只取计数）
SELECT CONVERT(char(7), <时间列>, 126) AS ym,
       COUNT_BIG(*) AS calls,
       COUNT(DISTINCT <用户列>) AS distinct_users
FROM BI.dbo.api_audit_logs WITH (NOLOCK)
GROUP BY CONVERT(char(7), <时间列>, 126)
ORDER BY ym;

-- 4) 近 90 天按接口路径：调用次数、去重用户数、错误数、平均耗时
--    路径去掉查询串（防止参数中带个人信息）；数字段的归一化在本地脚本中做
SELECT LEFT(<路径列>, CHARINDEX('?', <路径列> + '?') - 1) AS route,
       COUNT_BIG(*) AS calls,
       COUNT(DISTINCT <用户列>) AS distinct_users,
       SUM(CASE WHEN <状态码列> >= 400 THEN 1 ELSE 0 END) AS errors,
       AVG(CAST(<耗时列> AS bigint)) AS avg_ms
FROM BI.dbo.api_audit_logs WITH (NOLOCK)
WHERE <时间列> >= DATEADD(DAY, -90, GETDATE())
GROUP BY LEFT(<路径列>, CHARINDEX('?', <路径列> + '?') - 1)
ORDER BY calls DESC;

-- 5) 近 90 天导出：按接口路径与月份的次数、去重用户数
SELECT LEFT(<路径列>, CHARINDEX('?', <路径列> + '?') - 1) AS route,
       CONVERT(char(7), <时间列>, 126) AS ym,
       COUNT_BIG(*) AS exports,
       COUNT(DISTINCT <用户列>) AS distinct_users
FROM BI.dbo.export_audit_logs WITH (NOLOCK)
WHERE <时间列> >= DATEADD(DAY, -90, GETDATE())
GROUP BY LEFT(<路径列>, CHARINDEX('?', <路径列> + '?') - 1), CONVERT(char(7), <时间列>, 126)
ORDER BY exports DESC;

-- 6) T_FST_* 视图与表的列形状（R0「118 个对象逐个核对定义与形状」，BI_SCOPE §4.3；过渡镜像须与之同形状）
SELECT o.name AS object_name, o.type_desc, c.column_id, c.name AS column_name,
       TYPE_NAME(c.user_type_id) AS data_type, c.max_length, c.precision, c.scale, c.is_nullable
FROM BI.sys.objects o
JOIN BI.sys.columns c ON c.object_id = o.object_id
WHERE o.name LIKE 'T\_FST\_%' ESCAPE '\' AND o.type IN ('V', 'U')
ORDER BY o.name, c.column_id;

-- 7) T_FST_* 视图引用的跨库对象（只取被引用的库名、表名，不取视图定义正文）
SELECT OBJECT_NAME(d.referencing_id, DB_ID('BI')) AS view_name,
       d.referenced_database_name, d.referenced_schema_name, d.referenced_entity_name
FROM BI.sys.sql_expression_dependencies d
WHERE OBJECT_NAME(d.referencing_id, DB_ID('BI')) LIKE 'T\_FST\_%' ESCAPE '\'
ORDER BY view_name, d.referenced_database_name, d.referenced_entity_name;

-- 8) 涉及 BI 库的 SQL Agent 作业（只取名称、启用状态、最近运行；若无 msdb 权限则跳过）
SELECT j.name, j.enabled, s.database_name, MAX(h.run_date) AS last_run_date
FROM msdb.dbo.sysjobs j
JOIN msdb.dbo.sysjobsteps s ON s.job_id = j.job_id
LEFT JOIN msdb.dbo.sysjobhistory h ON h.job_id = j.job_id AND h.step_id = 0
WHERE s.database_name = 'BI' OR s.command LIKE '%T\_FST\_%' ESCAPE '\'
GROUP BY j.name, j.enabled, s.database_name
ORDER BY j.name;
