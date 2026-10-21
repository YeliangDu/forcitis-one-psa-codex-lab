-- 老系统项目「重要性级别」（N/V/S）前置二：A8 表单字段里显示名含「重要」的字段（表单名、主表名、字段名），以及枚举项显示名为 N/V/S 的枚举。只读表单与枚举定义。只在 a8_db 上下文出结果。
WITH f AS (SELECT D.ID, CAST(D.NAME AS nvarchar(60)) AS form_name, CAST(D.FIELD_INFO AS nvarchar(max)) AS j FROM a8_db.dbo.CAP_FORM_DEFINITION D
            WHERE DB_NAME() = N'a8_db' AND CAST(D.FIELD_INFO AS nvarchar(max)) LIKE N'%重要%')
SELECT f.form_name, JSON_VALUE(f.j, '$.front_formmain.dbTableName') AS main_table, JSON_VALUE(x.value, '$.name') AS field_name, JSON_VALUE(x.value, '$.display') AS display, JSON_VALUE(x.value, '$.type') AS ftype
  FROM f CROSS APPLY OPENJSON(f.j, '$.front_formmain.fieldInfo') x
 WHERE JSON_VALUE(x.value, '$.display') LIKE N'%重要%'
 ORDER BY f.form_name, field_name;
GO
SELECT E.ID AS enum_id, CAST(E.ENUMNAME AS nvarchar(60)) AS enum_name, CAST(I.SHOWVALUE AS nvarchar(40)) AS item, I.SORTNUMBER AS sort
  FROM a8_db.dbo.CTP_ENUM_ITEM I JOIN a8_db.dbo.CTP_ENUM E ON E.ID = I.REF_ENUMID
 WHERE DB_NAME() = N'a8_db' AND E.ID IN (SELECT REF_ENUMID FROM a8_db.dbo.CTP_ENUM_ITEM WHERE SHOWVALUE IN (N'N', N'V', N'S') GROUP BY REF_ENUMID HAVING COUNT(DISTINCT SHOWVALUE) >= 2)
 ORDER BY E.ID, I.SORTNUMBER;
GO
