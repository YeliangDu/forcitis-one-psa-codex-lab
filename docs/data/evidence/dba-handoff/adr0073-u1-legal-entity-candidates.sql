-- ADR-0073 U1 法人归属变动频率 · 第一步：候选表与列清单（只读元数据，库、表、列、类型与近似行数，不取任何值）。以取证账号 one_evidence_ro 执行（ADR-0037）。
-- 工具在 a8_db 与 ruoyi-vue-pro 各跑一次。
SET LOCK_TIMEOUT 1000;
SELECT DB_NAME() AS database_name, c.TABLE_SCHEMA, c.TABLE_NAME, c.COLUMN_NAME, c.DATA_TYPE
  FROM INFORMATION_SCHEMA.COLUMNS c
 WHERE LOWER(c.COLUMN_NAME) LIKE '%legal%' OR LOWER(c.COLUMN_NAME) LIKE '%company%' OR LOWER(c.COLUMN_NAME) LIKE '%corp%'
    OR LOWER(c.COLUMN_NAME) LIKE '%account%' OR LOWER(c.COLUMN_NAME) LIKE '%entity%' OR LOWER(c.COLUMN_NAME) LIKE '%subsidiary%'
    OR LOWER(c.COLUMN_NAME) LIKE '%dept%' OR LOWER(c.COLUMN_NAME) LIKE '%org%'
 ORDER BY c.TABLE_SCHEMA, c.TABLE_NAME, c.ORDINAL_POSITION;
SELECT DB_NAME() AS database_name, s.name AS table_schema, t.name AS table_name, SUM(p.rows) AS approx_rows
  FROM sys.tables t
  JOIN sys.schemas s ON s.schema_id = t.schema_id
  JOIN sys.partitions p ON p.object_id = t.object_id AND p.index_id IN (0, 1)
 WHERE LOWER(t.name) LIKE '%member%' OR LOWER(t.name) LIKE '%employee%' OR LOWER(t.name) LIKE '%staff%' OR LOWER(t.name) LIKE '%user%'
    OR LOWER(t.name) LIKE '%dept%' OR LOWER(t.name) LIKE '%org%' OR LOWER(t.name) LIKE '%unit%' OR LOWER(t.name) LIKE '%history%'
    OR LOWER(t.name) LIKE '%his%' OR LOWER(t.name) LIKE '%log%' OR LOWER(t.name) LIKE '%change%' OR LOWER(t.name) LIKE '%transfer%'
 GROUP BY s.name, t.name
 ORDER BY s.name, t.name;
