-- 全历史迁入数据量估算补充：两库全部用户表的总行数、总占用与表数（TOP 400 截断时用，只读元数据）。one_evidence_ro（ADR-0037 第 7 项）。
SET LOCK_TIMEOUT 1000;
SELECT DB_NAME() AS db_name, COUNT(*) AS tables_with_rows, SUM(r.row_count) AS total_rows, SUM(r.used_kb) / 1048576.0 AS total_used_gb, SUM(r.data_kb) / 1048576.0 AS data_gb, SUM(r.index_kb) / 1048576.0 AS index_and_lob_gb
  FROM (SELECT t.object_id,
               SUM(CASE WHEN i.index_id IN (0, 1) THEN p.rows ELSE 0 END) AS row_count,
               SUM(CASE WHEN i.index_id IN (0, 1) THEN a.used_pages ELSE 0 END) * 8 AS data_kb,
               SUM(CASE WHEN i.index_id NOT IN (0, 1) THEN a.used_pages ELSE 0 END) * 8 AS index_kb,
               SUM(a.used_pages) * 8 AS used_kb
          FROM sys.tables AS t JOIN sys.indexes AS i ON i.object_id = t.object_id
          JOIN sys.partitions AS p ON p.object_id = i.object_id AND p.index_id = i.index_id
          JOIN sys.allocation_units AS a ON a.container_id = p.partition_id
         WHERE t.is_ms_shipped = 0 GROUP BY t.object_id HAVING SUM(CASE WHEN i.index_id IN (0, 1) THEN p.rows ELSE 0 END) > 0) AS r;
