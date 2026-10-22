-- 工作日历 17 天核查 第一步：工时与审批表的列名类型与索引（只读元数据）。one_evidence_ro（ADR-0037 第 7 项）。
SET LOCK_TIMEOUT 1000;
SELECT c.TABLE_NAME AS table_name, c.COLUMN_NAME AS column_name, c.DATA_TYPE AS data_type
  FROM [ruoyi-vue-pro].INFORMATION_SCHEMA.COLUMNS AS c
 WHERE DB_NAME() = N'ruoyi-vue-pro' AND c.TABLE_SCHEMA = N'dbo' AND c.TABLE_NAME IN (N'bpm_task_ext', N'psa_time_sheet', N'psa_time_sheet_works')
   AND (c.COLUMN_NAME LIKE N'%date%' OR c.COLUMN_NAME LIKE N'%time%' OR c.COLUMN_NAME LIKE N'%status%' OR c.COLUMN_NAME LIKE N'%result%' OR c.COLUMN_NAME LIKE N'%deleted%' OR c.COLUMN_NAME LIKE N'%hours%' OR c.COLUMN_NAME LIKE N'%approve%' OR c.COLUMN_NAME = N'id')
 ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION;
SELECT t.name AS table_name, i.name AS index_name, ic.key_ordinal, c.name AS column_name
  FROM sys.tables AS t JOIN sys.indexes AS i ON i.object_id = t.object_id
  JOIN sys.index_columns AS ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.is_included_column = 0
  JOIN sys.columns AS c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
 WHERE DB_NAME() = N'ruoyi-vue-pro' AND t.name IN (N'bpm_task_ext', N'psa_time_sheet', N'psa_time_sheet_works') ORDER BY t.name, i.index_id, ic.key_ordinal;
SELECT t.name AS table_name, SUM(p.row_count) AS estimated_rows FROM sys.dm_db_partition_stats AS p JOIN sys.tables AS t ON t.object_id = p.object_id
 WHERE DB_NAME() = N'ruoyi-vue-pro' AND p.index_id IN (0, 1) AND t.name IN (N'bpm_task_ext', N'psa_time_sheet', N'psa_time_sheet_works') GROUP BY t.name;
