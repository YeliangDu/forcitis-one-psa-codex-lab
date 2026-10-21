-- DAT-1020-COO-01 前置三：用印申请与员工档案表单的字段显示名、合同条款与送审相关表的列名、e 签宝在 CIP_LOG 与 ECONTRACT 表中的计数。只看元数据与聚合。只在 a8_db 上下文出结果。
SELECT N'f0642' AS k, JSON_VALUE(x.value, '$.name') AS a, JSON_VALUE(x.value, '$.display') AS b, JSON_VALUE(x.value, '$.type') AS c
  FROM a8_db.dbo.CAP_FORM_DEFINITION D CROSS APPLY OPENJSON(CAST(D.FIELD_INFO AS nvarchar(max)), '$.front_formmain.fieldInfo') x WHERE DB_NAME() = N'a8_db' AND D.ID = 3996442246046958045;
GO
SELECT N'f0642_main' AS k, JSON_VALUE(CAST(D.FIELD_INFO AS nvarchar(max)), '$.front_formmain.dbTableName') AS a, NULL AS b, NULL AS c FROM a8_db.dbo.CAP_FORM_DEFINITION D WHERE DB_NAME() = N'a8_db' AND D.ID IN (3996442246046958045, -3418526626173990689);
GO
SELECT N'f0307' AS k, JSON_VALUE(x.value, '$.name') AS a, JSON_VALUE(x.value, '$.display') AS b, JSON_VALUE(x.value, '$.type') AS c
  FROM a8_db.dbo.CAP_FORM_DEFINITION D CROSS APPLY OPENJSON(CAST(D.FIELD_INFO AS nvarchar(max)), '$.front_formmain.fieldInfo') x WHERE DB_NAME() = N'a8_db' AND D.ID = -3418526626173990689
   AND (JSON_VALUE(x.value, '$.display') LIKE N'%注册%' OR JSON_VALUE(x.value, '$.display') LIKE N'%执业%' OR JSON_VALUE(x.value, '$.display') LIKE N'%资格%' OR JSON_VALUE(x.value, '$.display') LIKE N'%证书%' OR JSON_VALUE(x.value, '$.display') LIKE N'%职称%' OR JSON_VALUE(x.value, '$.display') LIKE N'%专业%' OR JSON_VALUE(x.value, '$.display') LIKE N'%证%');
GO
SELECT N'cols' AS k, c.TABLE_NAME AS a, c.COLUMN_NAME AS b, c.DATA_TYPE AS c FROM [ruoyi-vue-pro].INFORMATION_SCHEMA.COLUMNS c WHERE DB_NAME() = N'a8_db' AND c.TABLE_NAME IN (N'psa_deliverable_submission', N'T_FST_CONTTRACTTERMS', N'psa_pm_contract_term', N'psa_contract_clause') ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION;
GO
SELECT N'cip_esign' AS k, CAST(COUNT(*) AS nvarchar(20)) AS a, CAST(MIN(CREATE_DATE) AS nvarchar(30)) AS b, CAST(MAX(CREATE_DATE) AS nvarchar(30)) AS c FROM a8_db.dbo.CIP_LOG
 WHERE DB_NAME() = N'a8_db' AND (CAST(LOG_ACTION AS nvarchar(max)) LIKE N'%签宝%' OR CAST(LOG_DETAIL AS nvarchar(max)) LIKE N'%签宝%' OR CAST(MESSAGE AS nvarchar(max)) LIKE N'%签宝%' OR CAST(MESSAGE_INFO AS nvarchar(max)) LIKE N'%签宝%' OR CAST(THIRD_PARTY AS nvarchar(max)) LIKE N'%签宝%'
    OR CAST(LOG_ACTION AS nvarchar(max)) LIKE N'%esign%' OR CAST(LOG_DETAIL AS nvarchar(max)) LIKE N'%esign%' OR CAST(MESSAGE AS nvarchar(max)) LIKE N'%esign%' OR CAST(EXT_ATTR1 AS nvarchar(max)) LIKE N'%esign%');
GO
SELECT N'cip_range' AS k, CAST(COUNT(*) AS nvarchar(20)) AS a, CAST(MIN(CREATE_DATE) AS nvarchar(30)) AS b, CAST(MAX(CREATE_DATE) AS nvarchar(30)) AS c FROM a8_db.dbo.CIP_LOG WHERE DB_NAME() = N'a8_db';
GO
SELECT N'econtract_n' AS k, N'ECONTRACT_SIGN_RECORD' AS a, CAST(COUNT(*) AS nvarchar(20)) AS b, NULL AS c FROM a8_db.dbo.ECONTRACT_SIGN_RECORD WHERE DB_NAME() = N'a8_db'
UNION ALL SELECT N'econtract_n', N'ECONTRACT_THIRDPARTY_INFO', CAST(COUNT(*) AS nvarchar(20)), NULL FROM a8_db.dbo.ECONTRACT_THIRDPARTY_INFO WHERE DB_NAME() = N'a8_db'
UNION ALL SELECT N'econtract_n', N'ECONTRACT_ADAPTER_CONFIG', CAST(COUNT(*) AS nvarchar(20)), NULL FROM a8_db.dbo.ECONTRACT_ADAPTER_CONFIG WHERE DB_NAME() = N'a8_db'
UNION ALL SELECT N'econtract_n', N'SIGN_RECORD', CAST(COUNT(*) AS nvarchar(20)), NULL FROM a8_db.dbo.SIGN_RECORD WHERE DB_NAME() = N'a8_db'
UNION ALL SELECT N'econtract_n', N'ECONTRACT_PROCESS_SEAL', CAST(COUNT(*) AS nvarchar(20)), NULL FROM a8_db.dbo.ECONTRACT_PROCESS_SEAL WHERE DB_NAME() = N'a8_db';
GO
