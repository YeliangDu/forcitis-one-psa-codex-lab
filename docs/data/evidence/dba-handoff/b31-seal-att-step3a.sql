-- B-31 第三步 a：①窗口内用印附件 CTP_FILE.FILE_SIZE 大于 100 MB 的个数与分位数（只出数字）；②表单定义相关表的列元数据与行数估计（为定位字段显示名）。one_evidence_ro（ADR-0037 第 7 项）。仅 a8_db 出结果。
SET LOCK_TIMEOUT 1000;
IF DB_NAME() = N'a8_db'
BEGIN
WITH s AS (
  SELECT c.FILE_SIZE AS sz,
         PERCENTILE_CONT(0.5)  WITHIN GROUP (ORDER BY c.FILE_SIZE) OVER () AS p50,
         PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY c.FILE_SIZE) OVER () AS p95
    FROM a8_db.dbo.formmain_0643 f
    JOIN a8_db.dbo.CTP_ATTACHMENT a ON a.ATT_REFERENCE = f.ID
    JOIN a8_db.dbo.CTP_FILE c ON c.ID = a.FILE_URL
   WHERE f.start_date >= '2024-10-01')
SELECT COUNT(*) AS files, SUM(CASE WHEN sz > 104857600 THEN 1 ELSE 0 END) AS files_over_100mb, MAX(sz) AS max_size, MAX(p50) AS median_size, MAX(p95) AS p95_size FROM s;
SELECT c.TABLE_NAME AS table_name, c.COLUMN_NAME AS column_name, c.DATA_TYPE AS data_type
  FROM INFORMATION_SCHEMA.COLUMNS AS c
 WHERE c.TABLE_SCHEMA = N'dbo' AND (c.TABLE_NAME LIKE N'CAP_FORM%' OR c.TABLE_NAME LIKE N'FORM[_]%')
   AND c.TABLE_NAME NOT LIKE N'%LOG%' AND c.TABLE_NAME NOT LIKE N'%HISTORY%' AND c.TABLE_NAME NOT LIKE N'%DATA%'
 ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION;
SELECT t.name AS table_name, SUM(p.row_count) AS estimated_rows
  FROM sys.dm_db_partition_stats AS p JOIN sys.tables AS t ON t.object_id = p.object_id
 WHERE p.index_id IN (0, 1) AND (t.name LIKE N'CAP_FORM%' OR t.name LIKE N'FORM[_]%')
 GROUP BY t.name ORDER BY t.name;
END
