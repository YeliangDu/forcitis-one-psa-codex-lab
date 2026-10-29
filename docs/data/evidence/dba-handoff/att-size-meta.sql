-- 附件大小分位数取证 第一步：A8 里附件类表的列名类型（只读元数据）与行数估计。one_evidence_ro（ADR-0037 第 7 项）。
SET LOCK_TIMEOUT 1000;
SELECT c.TABLE_NAME AS table_name, c.COLUMN_NAME AS column_name, c.DATA_TYPE AS data_type
  FROM [a8_db].INFORMATION_SCHEMA.COLUMNS AS c
 WHERE DB_NAME() = N'a8_db' AND c.TABLE_SCHEMA = N'dbo'
   AND (c.TABLE_NAME LIKE N'%ATTACH%' OR c.TABLE_NAME = N'CTP_FILE' OR c.TABLE_NAME LIKE N'%FILE[_]%')
   AND (c.COLUMN_NAME LIKE N'%SIZE%' OR c.COLUMN_NAME LIKE N'%LENGTH%' OR c.COLUMN_NAME IN (N'ID', N'CREATEDATE', N'CREATE_DATE', N'MIME_TYPE', N'CATEGORY', N'TYPE'))
 ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION;
SELECT t.name AS table_name, SUM(p.row_count) AS estimated_rows
  FROM sys.dm_db_partition_stats AS p JOIN sys.tables AS t ON t.object_id = p.object_id
 WHERE DB_NAME() = N'a8_db' AND p.index_id IN (0, 1) AND (t.name LIKE N'%ATTACH%' OR t.name = N'CTP_FILE')
 GROUP BY t.name ORDER BY SUM(p.row_count) DESC;
