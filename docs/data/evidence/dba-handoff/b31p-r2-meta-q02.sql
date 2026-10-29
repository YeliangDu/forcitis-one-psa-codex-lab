-- B31P 第二批：①五张表的列名与类型；②按名称模式的表名清单（只要名字）；③COL_SUMMARY 行数估计与全部索引结构（为 Q04-A 分批定位）；④Q02 已知模板（名称限本地已出现的非个人化名称）。
-- 只读元数据与模板定义，不读实例。one_evidence_ro（ADR-0037 第 7 项）。a8_db 表用三段名，DB_NAME() 守卫只在 a8_db 出数据批；表名清单两库各出。
SET LOCK_TIMEOUT 1000;
SELECT TOP (2000) TABLE_NAME AS table_name, COLUMN_NAME AS column_name, DATA_TYPE AS data_type, CHARACTER_MAXIMUM_LENGTH AS maximum_length, IS_NULLABLE AS is_nullable
  FROM INFORMATION_SCHEMA.COLUMNS
 WHERE TABLE_SCHEMA = N'dbo' AND TABLE_NAME IN (N'CTP_TEMPLATE', N'CAP_FORM_DEFINITION', N'CTP_ENUM', N'CTP_ENUM_ITEM', N'COL_SUMMARY')
 ORDER BY TABLE_NAME, ORDINAL_POSITION;
SELECT TOP (1000) TABLE_NAME AS table_name, COUNT_BIG(*) AS column_count
  FROM INFORMATION_SCHEMA.COLUMNS
 WHERE TABLE_SCHEMA = N'dbo'
   AND (TABLE_NAME LIKE N'WF[_]%' OR TABLE_NAME LIKE N'CTP[_]%' OR TABLE_NAME LIKE N'CAP[_]%' OR TABLE_NAME = N'ORG_POST'
        OR TABLE_NAME LIKE N'ORG[_]ROLE%' OR TABLE_NAME LIKE N'QRTZ[_]%' OR TABLE_NAME LIKE N'XXL[_]JOB%' OR TABLE_NAME LIKE N'JK[_]%')
 GROUP BY TABLE_NAME ORDER BY TABLE_NAME;
SELECT t.name AS table_name, SUM(p.row_count) AS estimated_rows
  FROM sys.dm_db_partition_stats AS p JOIN sys.tables AS t ON t.object_id = p.object_id
 WHERE DB_NAME() = N'a8_db' AND t.name IN (N'COL_SUMMARY', N'CTP_TEMPLATE') AND p.index_id IN (0, 1)
 GROUP BY t.name;
SELECT i.name AS index_name, i.type_desc, i.is_unique, ic.key_ordinal, ic.is_included_column, c.name AS column_name
  FROM sys.tables AS t JOIN sys.indexes AS i ON i.object_id = t.object_id
  JOIN sys.index_columns AS ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
  JOIN sys.columns AS c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
 WHERE DB_NAME() = N'a8_db' AND t.name = N'COL_SUMMARY' ORDER BY i.index_id, ic.is_included_column, ic.key_ordinal;
SELECT TOP (100) t.ID AS template_id, t.SUBJECT AS template_name, t.STATE AS state_code, t.IS_DELETE AS delete_flag
  FROM [a8_db].dbo.CTP_TEMPLATE AS t
 WHERE DB_NAME() = N'a8_db' AND t.SUBJECT IN (N'用印申请', N'需求工单', N'周年贺卡', N'IT资产领用申请', N'IT资产采购申请', N'感恩节活动', N'创新项目发布申请', N'新增报价')
 ORDER BY t.ID;
