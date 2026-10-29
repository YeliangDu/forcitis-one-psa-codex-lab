-- B31P 第三批①：WF_PROCESS_TEMPLETE、CTP_NODE_PERMISSION、CTP_FORMULA、ORG_POST、ORG_ROLE 的列名与类型，另 CTP_TEMPLATE 的 FORM_APPID、WORKFLOW_ID 列与 CAP_FORM_DEFINITION 的主键。只读元数据。one_evidence_ro（ADR-0037 第 7 项）。
SET LOCK_TIMEOUT 1000;
SELECT TOP (2000) TABLE_NAME AS table_name, COLUMN_NAME AS column_name, DATA_TYPE AS data_type, CHARACTER_MAXIMUM_LENGTH AS maximum_length, IS_NULLABLE AS is_nullable
  FROM INFORMATION_SCHEMA.COLUMNS
 WHERE TABLE_SCHEMA = N'dbo' AND TABLE_NAME IN (N'WF_PROCESS_TEMPLETE', N'CTP_NODE_PERMISSION', N'CTP_FORMULA', N'ORG_POST', N'ORG_ROLE')
 ORDER BY TABLE_NAME, ORDINAL_POSITION;
SELECT t.name AS table_name, i.name AS index_name, i.is_primary_key, ic.key_ordinal, c.name AS column_name
  FROM sys.tables AS t JOIN sys.indexes AS i ON i.object_id = t.object_id
  JOIN sys.index_columns AS ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id AND ic.is_included_column = 0
  JOIN sys.columns AS c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
 WHERE DB_NAME() = N'a8_db' AND t.name IN (N'WF_PROCESS_TEMPLETE', N'CTP_NODE_PERMISSION', N'CTP_FORMULA', N'CTP_TEMPLATE', N'CAP_FORM_DEFINITION') AND (i.is_primary_key = 1 OR i.is_unique = 1 OR c.name IN (N'FORM_APPID', N'WORKFLOW_ID', N'TEMPLATE_ID', N'TEMPLETE_ID'))
 ORDER BY t.name, i.index_id, ic.key_ordinal;
