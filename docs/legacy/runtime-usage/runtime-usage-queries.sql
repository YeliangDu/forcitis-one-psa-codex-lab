-- PSA 运行时使用量 —— 只读查询，在【生产库】执行（SQL Server 2016，DMS 即可）
-- 目的：界定「真正在跑的代码」，用于现代化评估的规模与范围。不含任何写操作。
-- 结果请各自导出 CSV，放到 psa-modernization/analysis/psa/runtime/ 下，文件名见每段注释。

-- 0) 先确认访问日志开着、保留多久（若最早日期很近，说明有清理任务，窗口按实际天数理解）
--    → 导出为 runtime/00_log_window.csv
SELECT MIN(begin_time) AS earliest, MAX(begin_time) AS latest, COUNT_BIG(*) AS rows_total
FROM infra_api_access_log WITH (NOLOCK);

-- 1) 近 180 天各接口调用量（按服务 + 方法 + URL；路径变量的归一化在本地脚本里做）
--    → 导出为 runtime/01_api_usage.csv
SELECT
    application_name,
    request_method,
    request_url,
    COUNT_BIG(*)                          AS calls,
    COUNT(DISTINCT user_id)               AS distinct_users,
    MIN(begin_time)                       AS first_seen,
    MAX(begin_time)                       AS last_seen,
    AVG(CAST(duration AS BIGINT))         AS avg_ms,
    MAX(duration)                         AS max_ms,
    SUM(CASE WHEN result_code <> 0 THEN 1 ELSE 0 END) AS errors
FROM infra_api_access_log WITH (NOLOCK)
WHERE begin_time >= DATEADD(DAY, -180, GETDATE())
GROUP BY application_name, request_method, request_url
ORDER BY calls DESC;

-- 2) 启用中的菜单 / 按钮权限，及其被多少角色授权（判断哪些页面仍对用户开放）
--    → 导出为 runtime/02_menus.csv
SELECT
    m.id, m.parent_id, m.name, m.type, m.path, m.component, m.permission, m.status,
    (SELECT COUNT(*) FROM system_role_menu rm WITH (NOLOCK)
      WHERE rm.menu_id = m.id AND rm.deleted = 0) AS granted_roles
FROM system_menu m WITH (NOLOCK)
WHERE m.deleted = 0
ORDER BY m.parent_id, m.sort;

-- 3) 定时任务近 180 天执行情况（XXL-JOB 在独立库时请在 xxl_job 库执行；表名按实际调整）
--    → 导出为 runtime/03_jobs.csv
-- SELECT i.job_desc, i.executor_handler, i.trigger_status,
--        COUNT(l.id) AS runs, MAX(l.trigger_time) AS last_run,
--        SUM(CASE WHEN l.handle_code <> 200 THEN 1 ELSE 0 END) AS failures
-- FROM xxl_job_info i LEFT JOIN xxl_job_log l
--   ON l.job_id = i.id AND l.trigger_time >= DATEADD(DAY, -180, GETDATE())
-- GROUP BY i.job_desc, i.executor_handler, i.trigger_status
-- ORDER BY runs DESC;

-- 4) 各业务表行数（判断哪些表在用、数据迁移体量）
--    → 导出为 runtime/04_table_rows.csv
SELECT t.name AS table_name, SUM(p.rows) AS row_count
FROM sys.tables t
JOIN sys.partitions p ON p.object_id = t.object_id AND p.index_id IN (0, 1)
GROUP BY t.name
ORDER BY row_count DESC;
