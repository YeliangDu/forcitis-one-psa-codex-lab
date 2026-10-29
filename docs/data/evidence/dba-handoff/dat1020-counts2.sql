-- DAT-1020-COO-01 计数补充：送审标记表 psa_deliverable_submission 行数与标记分布、psa_deliverable 列名。只出计数与列名。只在 a8_db 上下文出结果。
SELECT N'sub_flag' AS k, ISNULL(CAST(construction_drawing_review AS nvarchar(5)), N'(null)') AS a, CAST(deleted AS nvarchar(5)) AS b, CAST(COUNT(*) AS nvarchar(20)) AS c, CAST(SUM(CASE WHEN created_time >= DATEADD(MONTH, -24, GETDATE()) THEN 1 ELSE 0 END) AS nvarchar(20)) AS d FROM [ruoyi-vue-pro].dbo.psa_deliverable_submission WHERE DB_NAME() = N'a8_db' GROUP BY ISNULL(CAST(construction_drawing_review AS nvarchar(5)), N'(null)'), CAST(deleted AS nvarchar(5));
GO
SELECT N'deliv_cols' AS k, c.COLUMN_NAME AS a, c.DATA_TYPE AS b, NULL AS c, NULL AS d FROM [ruoyi-vue-pro].INFORMATION_SCHEMA.COLUMNS c WHERE DB_NAME() = N'a8_db' AND c.TABLE_NAME = N'psa_deliverable';
GO
