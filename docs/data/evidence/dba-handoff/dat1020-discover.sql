-- DAT-1020-COO-01 前置：CIP_LOG 列与行数、人事注册资格相关列、用印表单 formmain_0642 字段、老系统合同/币种/省份/施工图审查相关列。只看元数据与行数，不读数据。只在 a8_db 上下文出结果。
SELECT N'cip_cols' AS k, c.TABLE_NAME AS a, c.COLUMN_NAME AS b, c.DATA_TYPE AS c FROM a8_db.INFORMATION_SCHEMA.COLUMNS c WHERE DB_NAME() = N'a8_db' AND c.TABLE_NAME LIKE N'CIP[_]%' ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION;
GO
SELECT N'a8_cert_cols' AS k, c.TABLE_NAME AS a, c.COLUMN_NAME AS b, c.DATA_TYPE AS c FROM a8_db.INFORMATION_SCHEMA.COLUMNS c WHERE DB_NAME() = N'a8_db' AND c.TABLE_NAME NOT LIKE N'formson%' AND (c.COLUMN_NAME LIKE N'%cert%' OR c.COLUMN_NAME LIKE N'%regist%' OR c.COLUMN_NAME LIKE N'%qualif%') ORDER BY c.TABLE_NAME;
GO
SELECT N'a8_cert_tables' AS k, t.TABLE_NAME AS a, NULL AS b, NULL AS c FROM a8_db.INFORMATION_SCHEMA.TABLES t WHERE DB_NAME() = N'a8_db' AND (t.TABLE_NAME LIKE N'%cert%' OR t.TABLE_NAME LIKE N'%qualif%' OR t.TABLE_NAME LIKE N'%regist%');
GO
SELECT N'f0307_fields' AS k, JSON_VALUE(x.value, '$.name') AS a, JSON_VALUE(x.value, '$.display') AS b, JSON_VALUE(x.value, '$.type') AS c
  FROM a8_db.dbo.CAP_FORM_DEFINITION D CROSS APPLY OPENJSON(CAST(D.FIELD_INFO AS nvarchar(max)), '$.front_formmain.fieldInfo') x
 WHERE DB_NAME() = N'a8_db' AND JSON_VALUE(CAST(D.FIELD_INFO AS nvarchar(max)), '$.front_formmain.dbTableName') = N'formmain_0307'
   AND (JSON_VALUE(x.value, '$.display') LIKE N'%注册%' OR JSON_VALUE(x.value, '$.display') LIKE N'%执业%' OR JSON_VALUE(x.value, '$.display') LIKE N'%资格%' OR JSON_VALUE(x.value, '$.display') LIKE N'%证书%' OR JSON_VALUE(x.value, '$.display') LIKE N'%职称%' OR JSON_VALUE(x.value, '$.display') LIKE N'%专业%');
GO
SELECT N'f0642_fields' AS k, JSON_VALUE(x.value, '$.name') AS a, JSON_VALUE(x.value, '$.display') AS b, JSON_VALUE(x.value, '$.type') AS c
  FROM a8_db.dbo.CAP_FORM_DEFINITION D CROSS APPLY OPENJSON(CAST(D.FIELD_INFO AS nvarchar(max)), '$.front_formmain.fieldInfo') x
 WHERE DB_NAME() = N'a8_db' AND JSON_VALUE(CAST(D.FIELD_INFO AS nvarchar(max)), '$.front_formmain.dbTableName') = N'formmain_0642';
GO
SELECT N'f0642_cols' AS k, c.COLUMN_NAME AS a, c.DATA_TYPE AS b, NULL AS c FROM a8_db.INFORMATION_SCHEMA.COLUMNS c WHERE DB_NAME() = N'a8_db' AND c.TABLE_NAME = N'formmain_0642' AND c.COLUMN_NAME NOT LIKE N'field%';
GO
SELECT N'legacy_cols' AS k, c.TABLE_NAME AS a, c.COLUMN_NAME AS b, c.DATA_TYPE AS c
  FROM [ruoyi-vue-pro].INFORMATION_SCHEMA.COLUMNS c
 WHERE DB_NAME() = N'a8_db' AND (c.COLUMN_NAME LIKE N'%currency%' OR c.COLUMN_NAME LIKE N'%province%' OR c.COLUMN_NAME LIKE N'%cert%' OR c.COLUMN_NAME LIKE N'%regist%' OR c.COLUMN_NAME LIKE N'%review%' OR c.COLUMN_NAME LIKE N'%audit_drawing%' OR c.COLUMN_NAME LIKE N'%drawing%' OR c.COLUMN_NAME LIKE N'%qualif%')
   AND c.TABLE_NAME NOT LIKE N'act[_]%' ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION;
GO
SELECT N'legacy_tables' AS k, t.TABLE_NAME AS a, NULL AS b, NULL AS c FROM [ruoyi-vue-pro].INFORMATION_SCHEMA.TABLES t WHERE DB_NAME() = N'a8_db' AND (t.TABLE_NAME LIKE N'%contract%' OR t.TABLE_NAME LIKE N'%area%' OR t.TABLE_NAME LIKE N'%province%' OR t.TABLE_NAME LIKE N'%project%');
GO
SELECT N'cip_log_count' AS k, CAST(COUNT(*) AS nvarchar(20)) AS a, NULL AS b, NULL AS c FROM a8_db.dbo.CIP_LOG WHERE DB_NAME() = N'a8_db';
GO
