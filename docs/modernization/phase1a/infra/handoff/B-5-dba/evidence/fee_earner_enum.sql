-- dev 首次启动：A8 视图配置所需的「计费人员」枚举值编号（员工档案 formmain_0307.field0244 的取值与显示名，各值人数）。只出聚合，只在 a8_db 上下文出结果。
SELECT CAST(F.field0244 AS nvarchar(40)) AS enum_id, ISNULL((SELECT TOP 1 CAST(I.SHOWVALUE AS nvarchar(60)) FROM a8_db.dbo.CTP_ENUM_ITEM I WHERE CAST(I.ID AS nvarchar(40)) = CAST(F.field0244 AS nvarchar(40))), N'(no item)') AS shown, COUNT(*) AS n
  FROM a8_db.dbo.formmain_0307 F WHERE DB_NAME() = N'a8_db' GROUP BY CAST(F.field0244 AS nvarchar(40));
GO
