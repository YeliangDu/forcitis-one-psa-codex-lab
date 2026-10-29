-- B31P 元数据探查 Q03（字段物理类型）与 Q10（主键与唯一约束）：只读列定义与索引结构，不读任何值。表名取自 Q00 表级清单已确认存在者。one_evidence_ro（ADR-0037 第 7 项）。
SET LOCK_TIMEOUT 1000;
SELECT TOP (5000) TABLE_NAME AS table_name, COLUMN_NAME AS column_name, DATA_TYPE AS data_type, CHARACTER_MAXIMUM_LENGTH AS maximum_length,
       NUMERIC_PRECISION AS numeric_precision, NUMERIC_SCALE AS numeric_scale, IS_NULLABLE AS is_nullable
  FROM INFORMATION_SCHEMA.COLUMNS
 WHERE TABLE_SCHEMA = N'dbo' AND TABLE_NAME IN (N'formmain_0012', N'formmain_0642', N'formmain_0643', N'formmain_0875', N'formmain_0879', N'formmain_1051', N'formmain_1055', N'formmain_1071', N'formmain_1065', N'formmain_1067', N'formmain_1069', N'formmain_0855', N'formmain_0856', N'formmain_1239', N'formmain_1250', N'formmain_1377', N'formmain_0307', N'formmain_0515', N'formmain_0366', N'formmain_0471', N'psa_quotation_seal')
 ORDER BY TABLE_NAME, ORDINAL_POSITION;
SELECT TOP (2000) t.name AS table_name, i.name AS index_name, i.is_primary_key, i.is_unique, i.has_filter, ic.key_ordinal, c.name AS column_name
  FROM sys.tables AS t
  JOIN sys.schemas AS sc ON sc.schema_id = t.schema_id
  JOIN sys.indexes AS i ON i.object_id = t.object_id
  JOIN sys.index_columns AS ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
  JOIN sys.columns AS c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
 WHERE sc.name = N'dbo' AND t.name IN (N'formmain_0012', N'formmain_0642', N'formmain_0643', N'formmain_0875', N'formmain_0879', N'formmain_1051', N'formmain_1055', N'formmain_1071', N'formmain_1065', N'formmain_1067', N'formmain_1069', N'formmain_0855', N'formmain_0856', N'formmain_1239', N'formmain_1250', N'formmain_1377', N'formmain_0307', N'formmain_0515', N'formmain_0366', N'formmain_0471', N'psa_quotation_seal') AND (i.is_primary_key = 1 OR i.is_unique = 1) AND ic.key_ordinal > 0
 ORDER BY t.name, i.index_id, ic.key_ordinal;
