-- E-B6 前置五：报销、付款、采购、开票、分包相关表单的主表名与金额类字段（显示名含 金额/合计/总额/价税/含税）。只读表单定义，不读单据。只在 a8_db 上下文出结果。
WITH f AS (
  SELECT D.ID, CAST(D.NAME AS nvarchar(60)) AS form_name, CAST(D.FIELD_INFO AS nvarchar(max)) AS j
    FROM a8_db.dbo.CAP_FORM_DEFINITION D
   WHERE DB_NAME() = N'a8_db' AND D.ID IN (3547895782509333077, 8749464370691260872, 6109032112401239568, -2749981258151869731, 6967957742569147411, -8905197581101938467,
         -814531593431068699, 5727922956768333153, -4562474352996495532, 944653463331360583, 704542893954646637, 2320790073470960763, 6378679244412633962, -3399409258315102798))
SELECT f.form_name, JSON_VALUE(f.j, '$.front_formmain.dbTableName') AS main_table, JSON_VALUE(x.value, '$.name') AS field_name, JSON_VALUE(x.value, '$.display') AS display, JSON_VALUE(x.value, '$.type') AS ftype
  FROM f CROSS APPLY OPENJSON(f.j, '$.front_formmain.fieldInfo') x
 WHERE JSON_VALUE(x.value, '$.display') LIKE N'%金额%' OR JSON_VALUE(x.value, '$.display') LIKE N'%合计%' OR JSON_VALUE(x.value, '$.display') LIKE N'%总额%'
    OR JSON_VALUE(x.value, '$.display') LIKE N'%价税%' OR JSON_VALUE(x.value, '$.display') LIKE N'%含税%'
 ORDER BY f.form_name, field_name;
GO
