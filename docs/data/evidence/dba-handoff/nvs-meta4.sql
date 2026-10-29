-- 项目级别（N/V/S）前置四：枚举「商机级别」全部项与说明；项目阶段评审、项目成果内审表单里的人员类字段（审核人线索）；老系统 T_FST_PROJECT、psa_pm_project 的 level 取值分布（只出取值与个数）。只读定义与计数。只在 a8_db 上下文出结果。
SELECT I.ID AS item_id, CAST(I.SHOWVALUE AS nvarchar(40)) AS item, CAST(I.ENUMVALUE AS nvarchar(40)) AS enumvalue, I.SORTNUMBER AS sort, I.STATE AS state, CAST(I.CODE AS nvarchar(40)) AS code,
       CAST(I.DESCRIPTION AS nvarchar(200)) AS descr
  FROM a8_db.dbo.CTP_ENUM_ITEM I WHERE DB_NAME() = N'a8_db' AND I.REF_ENUMID = -7391496778928405449 ORDER BY I.SORTNUMBER;
GO
WITH f AS (SELECT D.ID, CAST(D.NAME AS nvarchar(60)) AS form_name, CAST(D.FIELD_INFO AS nvarchar(max)) AS j FROM a8_db.dbo.CAP_FORM_DEFINITION D
            WHERE DB_NAME() = N'a8_db' AND D.ID IN (SELECT ID FROM a8_db.dbo.CAP_FORM_DEFINITION WHERE CAST(NAME AS nvarchar(60)) IN (N'项目阶段评审', N'项目成果内审', N'项目立项申请', N'项目档案底表')))
SELECT f.form_name, JSON_VALUE(f.j, '$.front_formmain.dbTableName') AS main_table, JSON_VALUE(x.value, '$.name') AS field_name, JSON_VALUE(x.value, '$.display') AS display, JSON_VALUE(x.value, '$.type') AS ftype
  FROM f CROSS APPLY OPENJSON(f.j, '$.front_formmain.fieldInfo') x
 WHERE JSON_VALUE(x.value, '$.type') IN (N'member', N'department', N'post') OR JSON_VALUE(x.value, '$.display') LIKE N'%审%' OR JSON_VALUE(x.value, '$.display') LIKE N'%评%'
 ORDER BY f.form_name, field_name;
GO
SELECT N'T_FST_PROJECT.level' AS src, ISNULL(CAST(level AS nvarchar(20)), N'(null)') AS v, COUNT(*) AS n FROM [ruoyi-vue-pro].dbo.T_FST_PROJECT WHERE DB_NAME() = N'a8_db' GROUP BY level
UNION ALL SELECT N'psa_pm_project.level', ISNULL(CAST(level AS nvarchar(20)), N'(null)'), COUNT(*) FROM [ruoyi-vue-pro].dbo.psa_pm_project WHERE DB_NAME() = N'a8_db' GROUP BY level;
GO
