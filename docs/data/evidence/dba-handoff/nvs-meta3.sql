-- 「商机级别」枚举（N/V/S）被哪些 A8 表单字段使用（表单名、主表名、字段名、显示名）；老系统里 N/V/S 相关列的候选（列名含 level/class/grade/rank 且表名含 project/opportunity/review/audit）。只读定义。只在 a8_db 上下文出结果。
WITH f AS (SELECT D.ID, CAST(D.NAME AS nvarchar(60)) AS form_name, CAST(D.FIELD_INFO AS nvarchar(max)) AS j FROM a8_db.dbo.CAP_FORM_DEFINITION D
            WHERE DB_NAME() = N'a8_db' AND CAST(D.FIELD_INFO AS nvarchar(max)) LIKE N'%-7391496778928405449%')
SELECT f.form_name, JSON_VALUE(f.j, '$.front_formmain.dbTableName') AS main_table, JSON_VALUE(x.value, '$.name') AS field_name, JSON_VALUE(x.value, '$.display') AS display
  FROM f CROSS APPLY OPENJSON(f.j, '$.front_formmain.fieldInfo') x
 WHERE x.value LIKE N'%-7391496778928405449%'
 ORDER BY f.form_name, field_name;
GO
SELECT c.TABLE_NAME, c.COLUMN_NAME, c.DATA_TYPE
  FROM [ruoyi-vue-pro].INFORMATION_SCHEMA.COLUMNS c
 WHERE DB_NAME() = N'a8_db' AND (c.TABLE_NAME LIKE N'%project%' OR c.TABLE_NAME LIKE N'%opportunity%' OR c.TABLE_NAME LIKE N'%review%' OR c.TABLE_NAME LIKE N'%audit%')
   AND (c.COLUMN_NAME LIKE N'%level%' OR c.COLUMN_NAME LIKE N'%class%' OR c.COLUMN_NAME LIKE N'%grade%' OR c.COLUMN_NAME LIKE N'%rank%' OR c.COLUMN_NAME LIKE N'%tier%')
 ORDER BY c.TABLE_NAME, c.COLUMN_NAME;
GO
