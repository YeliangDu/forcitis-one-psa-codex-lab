-- B-31 历史用印迁入：用印申请档案 formmain_0643 的附件事实，第一步（只读元数据，只出列名、类型、行数估计）。one-data 经 one-coordinator 放行；以取证账号 one_evidence_ro 执行（ADR-0037 第 7 项）。仅 a8_db 出结果。
SET LOCK_TIMEOUT 1000;
-- ① formmain_0643 及名称含 0643 的表（含子表）的全部列
SELECT c.TABLE_NAME AS table_name, c.COLUMN_NAME AS column_name, c.DATA_TYPE AS data_type, c.CHARACTER_MAXIMUM_LENGTH AS max_length
  FROM INFORMATION_SCHEMA.COLUMNS AS c
 WHERE DB_NAME() = N'a8_db' AND c.TABLE_SCHEMA = N'dbo' AND c.TABLE_NAME LIKE N'%0643%'
 ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION;
-- ② 附件相关表与行数估计
SELECT t.name AS table_name, SUM(p.row_count) AS estimated_rows
  FROM sys.dm_db_partition_stats AS p JOIN sys.tables AS t ON t.object_id = p.object_id
 WHERE DB_NAME() = N'a8_db' AND p.index_id IN (0, 1) AND (t.name LIKE N'%ATTACH%' OR t.name LIKE N'%FILE%' OR t.name LIKE N'%0643%')
 GROUP BY t.name ORDER BY t.name;
-- CTP_ATTACHMENT、CTP_FILE 的全部列
SELECT c.TABLE_NAME AS table_name, c.COLUMN_NAME AS column_name, c.DATA_TYPE AS data_type, c.CHARACTER_MAXIMUM_LENGTH AS max_length
  FROM INFORMATION_SCHEMA.COLUMNS AS c
 WHERE DB_NAME() = N'a8_db' AND c.TABLE_SCHEMA = N'dbo' AND c.TABLE_NAME IN (N'CTP_ATTACHMENT', N'CTP_FILE')
 ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION;
-- 其他附件/文件类表里与记录外键、路径、校验、版本、大小、时间相关的列
SELECT c.TABLE_NAME AS table_name, c.COLUMN_NAME AS column_name, c.DATA_TYPE AS data_type
  FROM INFORMATION_SCHEMA.COLUMNS AS c
 WHERE DB_NAME() = N'a8_db' AND c.TABLE_SCHEMA = N'dbo' AND c.TABLE_NAME NOT IN (N'CTP_ATTACHMENT', N'CTP_FILE')
   AND (c.TABLE_NAME LIKE N'%ATTACH%' OR c.TABLE_NAME LIKE N'%FILE%')
   AND (c.COLUMN_NAME LIKE N'%REFERENCE%' OR c.COLUMN_NAME LIKE N'%PATH%' OR c.COLUMN_NAME LIKE N'%MD5%' OR c.COLUMN_NAME LIKE N'%HASH%'
        OR c.COLUMN_NAME LIKE N'%VERSION%' OR c.COLUMN_NAME LIKE N'%SIZE%' OR c.COLUMN_NAME LIKE N'%CREATE%' OR c.COLUMN_NAME LIKE N'%CHECK%')
 ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION;
