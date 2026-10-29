-- M-29 贡献毛利率分母改净服务收入前后值取证（one-rules 提供，来源 origin/claude/one-rules-context-2@3653a70e8 r0/m29_baseline_readonly_queries.sql）：环境与列名核对
-- ops 改动：每条外包 IF DB_NAME()=ruoyi-vue-pro；加 SET LOCK_TIMEOUT 30000；Q01 补一个缺失的右括号。one_evidence_ro（ADR-0037 第 5 项第 4 条），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- Q00 环境核对：隔离级别与目标表行数量级（不取业务数据）
-- 依赖：sys.databases；sys.dm_db_partition_stats（需 VIEW DATABASE STATE，无权限则本条报错，忽略）。
-- 不确定度：低。行数只用于判断是否要拆窗口。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
SELECT DB_NAME() AS db_name,
       (SELECT is_read_committed_snapshot_on FROM sys.databases WHERE name = DB_NAME()) AS rcsi_on,
       t.name AS table_name, SUM(ps.row_count) AS approx_rows
FROM sys.tables t
JOIN sys.dm_db_partition_stats ps ON ps.object_id = t.object_id AND ps.index_id IN (0, 1)
WHERE t.name IN (N'psa_pm_cuvalue', N'psa_pl_ledger', N'psa_cost_ledger', N'psa_wip_outsourcing',
                 N'psa_wip_commission', N'psa_pm_project', N'system_dept', N'mdm_accounting_period')
GROUP BY t.name
ORDER BY t.name;
END
-- Q01 列名核对：下面各条用到的表与列是否存在（只取元数据）
-- 依据：Flyway 迁移脚本、DO 类、数据字典（路径见 M29_BASELINE_MAPPING.md）。psa_pm_cuvalue 基础列、psa_pm_project、system_dept 不在仓库 DDL 内，
--       列名来自 DO 类与字典，未对生产核对（这正是本条的用途）。
-- 用法：逐表比对期望列；缺列的查询先不跑，回报。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = N'dbo'
  AND (   (TABLE_NAME = N'psa_pm_cuvalue'        AND COLUMN_NAME IN (N'project_id', N'dept_id', N'complete_date', N'complete_amount', N'no_tax_amount', N'tax', N'rate', N'type', N'company_code', N'write_off_flag'))
       OR (TABLE_NAME = N'psa_pl_ledger'         AND COLUMN_NAME IN (N'project_id', N'period_id', N'pl_type', N'source_type', N'notax_amount', N'business_date', N'company_code', N'deleted', N'cost_org_id'))
       OR (TABLE_NAME = N'psa_cost_ledger'       AND COLUMN_NAME IN (N'project_id', N'period_id', N'entry_type', N'entry_status', N'source_type', N'cost_type_code', N'cost_type_name', N'fee_type', N'business_date', N'notax_amount', N'company_code', N'deleted'))
       OR (TABLE_NAME = N'psa_wip_outsourcing'   AND COLUMN_NAME IN (N'project_id', N'dept_id', N'complete_date', N'amount', N'notax_amount', N'write_off_flag', N'project_write_off_flag', N'cost_type_code'))
       OR (TABLE_NAME = N'psa_wip_commission'    AND COLUMN_NAME IN (N'project_id', N'dept_id', N'record_date', N'amount', N'notax_amount', N'write_off_flag', N'project_write_off_flag', N'cost_type_code'))
       OR (TABLE_NAME = N'psa_pm_project'        AND COLUMN_NAME IN (N'id', N'dept_id'))
       OR (TABLE_NAME = N'system_dept'           AND COLUMN_NAME IN (N'id', N'name', N'parent_id', N'deleted'))
       OR (TABLE_NAME = N'mdm_accounting_period' AND COLUMN_NAME IN (N'id', N'company_code', N'period_code', N'period_type', N'period_year', N'period_month', N'start_date', N'end_date', N'status'))
       OR (TABLE_NAME = N'psa_pm_contract_term'  AND COLUMN_NAME IN (N'name', N'code', N'deleted')))
ORDER BY TABLE_NAME, COLUMN_NAME;
END
