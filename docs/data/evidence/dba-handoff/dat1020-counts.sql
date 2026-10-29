-- DAT-1020-COO-01 计数：用印申请（formmain_0642）近 24 个月按印章类型 × 印章归属公司分组；员工档案职称与专业类别分布（1–2 的小格写「< 3」）；老系统合同与项目省份、送审标记、币种列的元数据与计数。只出计数，无个人数据。只在 a8_db 上下文出结果。
SELECT N'seal_group' AS k, ISNULL(CAST(E1.SHOWVALUE AS nvarchar(60)), N'(null)') AS a, ISNULL(CAST(E2.SHOWVALUE AS nvarchar(60)), N'(null)') AS b, CAST(COUNT(*) AS nvarchar(20)) AS c, CAST(SUM(CASE WHEN F.finishedflag = 1 THEN 1 ELSE 0 END) AS nvarchar(20)) AS d
  FROM a8_db.dbo.formmain_0642 F
  LEFT JOIN a8_db.dbo.CTP_ENUM_ITEM E1 ON CAST(E1.ID AS nvarchar(40)) = CAST(F.field0041 AS nvarchar(40))
  LEFT JOIN a8_db.dbo.CTP_ENUM_ITEM E2 ON CAST(E2.ID AS nvarchar(40)) = CAST(F.field0075 AS nvarchar(40))
 WHERE DB_NAME() = N'a8_db' AND F.field0054 >= DATEADD(MONTH, -24, GETDATE())
 GROUP BY ISNULL(CAST(E1.SHOWVALUE AS nvarchar(60)), N'(null)'), ISNULL(CAST(E2.SHOWVALUE AS nvarchar(60)), N'(null)') ORDER BY COUNT(*) DESC;
GO
SELECT N'seal_total' AS k, CAST(COUNT(*) AS nvarchar(20)) AS a, CAST(SUM(CASE WHEN field0054 >= DATEADD(MONTH, -24, GETDATE()) THEN 1 ELSE 0 END) AS nvarchar(20)) AS b, CAST(SUM(CASE WHEN field0054 IS NULL THEN 1 ELSE 0 END) AS nvarchar(20)) AS c,
  CAST(SUM(CASE WHEN field0054 >= DATEADD(MONTH, -24, GETDATE()) AND field0041 IS NULL THEN 1 ELSE 0 END) AS nvarchar(20)) AS d FROM a8_db.dbo.formmain_0642 WHERE DB_NAME() = N'a8_db';
GO
SELECT N'seal_other_dim' AS k, N'用印分类' AS a, ISNULL(CAST(E.SHOWVALUE AS nvarchar(60)), N'(null)') AS b, CAST(COUNT(*) AS nvarchar(20)) AS c, NULL AS d
  FROM a8_db.dbo.formmain_0642 F LEFT JOIN a8_db.dbo.CTP_ENUM_ITEM E ON CAST(E.ID AS nvarchar(40)) = CAST(F.field0072 AS nvarchar(40))
 WHERE DB_NAME() = N'a8_db' AND F.field0054 >= DATEADD(MONTH, -24, GETDATE()) GROUP BY ISNULL(CAST(E.SHOWVALUE AS nvarchar(60)), N'(null)');
GO
SELECT N'title_dist' AS k, ISNULL(CAST(E.SHOWVALUE AS nvarchar(60)), N'(null)') AS a, CASE WHEN COUNT(*) < 3 THEN N'< 3' ELSE CAST(COUNT(*) AS nvarchar(20)) END AS b, NULL AS c, NULL AS d
  FROM a8_db.dbo.formmain_0307 F LEFT JOIN a8_db.dbo.CTP_ENUM_ITEM E ON CAST(E.ID AS nvarchar(40)) = CAST(F.field0233 AS nvarchar(40))
 WHERE DB_NAME() = N'a8_db' GROUP BY ISNULL(CAST(E.SHOWVALUE AS nvarchar(60)), N'(null)') ORDER BY COUNT(*) DESC;
GO
SELECT N'specialty_dist' AS k, ISNULL(CAST(E.SHOWVALUE AS nvarchar(60)), N'(null)') AS a, CASE WHEN COUNT(*) < 3 THEN N'< 3' ELSE CAST(COUNT(*) AS nvarchar(20)) END AS b, NULL AS c, NULL AS d
  FROM a8_db.dbo.formmain_0307 F LEFT JOIN a8_db.dbo.CTP_ENUM_ITEM E ON CAST(E.ID AS nvarchar(40)) = CAST(F.field0575 AS nvarchar(40))
 WHERE DB_NAME() = N'a8_db' GROUP BY ISNULL(CAST(E.SHOWVALUE AS nvarchar(60)), N'(null)') ORDER BY COUNT(*) DESC;
GO
SELECT N'title_prof_nonempty' AS k, CAST(COUNT(*) AS nvarchar(20)) AS a, CAST(SUM(CASE WHEN field0377 IS NOT NULL AND LEN(CAST(field0377 AS nvarchar(200))) > 0 THEN 1 ELSE 0 END) AS nvarchar(20)) AS b, CAST(SUM(CASE WHEN CAST(field0377 AS nvarchar(200)) LIKE N'%注册%' THEN 1 ELSE 0 END) AS nvarchar(20)) AS c, NULL AS d FROM a8_db.dbo.formmain_0307 WHERE DB_NAME() = N'a8_db';
GO
SELECT N'legacy_curr_cols' AS k, c.TABLE_NAME AS a, c.COLUMN_NAME AS b, c.DATA_TYPE AS c, NULL AS d FROM [ruoyi-vue-pro].INFORMATION_SCHEMA.COLUMNS c WHERE DB_NAME() = N'a8_db' AND (c.COLUMN_NAME LIKE N'%curr%' OR c.COLUMN_NAME LIKE N'%rmb%' OR c.COLUMN_NAME LIKE N'%forex%' OR c.COLUMN_NAME LIKE N'%exchange%' OR c.COLUMN_NAME LIKE N'%币%') AND (c.TABLE_NAME LIKE N'%contract%' OR c.TABLE_NAME LIKE N'%quotation%' OR c.TABLE_NAME LIKE N'%opportunity%' OR c.TABLE_NAME LIKE N'%project%' OR c.TABLE_NAME LIKE N'T[_]FST%');
GO
SELECT N'deliverable_cols' AS k, c.TABLE_NAME AS a, c.COLUMN_NAME AS b, c.DATA_TYPE AS c, NULL AS d FROM [ruoyi-vue-pro].INFORMATION_SCHEMA.COLUMNS c WHERE DB_NAME() = N'a8_db' AND c.TABLE_NAME = N'psa_deliverable' AND (c.COLUMN_NAME LIKE N'%project%' OR c.COLUMN_NAME LIKE N'id' OR c.COLUMN_NAME LIKE N'%drawing%' OR c.COLUMN_NAME LIKE N'%deleted%');
GO
SELECT N'contract_n' AS k, CAST(COUNT(*) AS nvarchar(20)) AS a, CAST(SUM(CASE WHEN create_time >= DATEADD(MONTH, -24, GETDATE()) THEN 1 ELSE 0 END) AS nvarchar(20)) AS b, CAST(SUM(CASE WHEN deleted = 0 THEN 1 ELSE 0 END) AS nvarchar(20)) AS c, CAST(SUM(CASE WHEN deleted = 0 AND create_time >= DATEADD(MONTH, -24, GETDATE()) THEN 1 ELSE 0 END) AS nvarchar(20)) AS d FROM [ruoyi-vue-pro].dbo.psa_pm_contract WHERE DB_NAME() = N'a8_db';
GO
SELECT N'drawing_flag' AS k, CAST(construction_drawing_review AS nvarchar(5)) AS a, ISNULL(CAST(drawing_category AS nvarchar(40)), N'(null)') AS b, CAST(COUNT(*) AS nvarchar(20)) AS c, CAST(SUM(CASE WHEN created_time >= DATEADD(MONTH, -24, GETDATE()) THEN 1 ELSE 0 END) AS nvarchar(20)) AS d FROM [ruoyi-vue-pro].dbo.psa_deliverable_submission WHERE DB_NAME() = N'a8_db' AND deleted = 0 GROUP BY CAST(construction_drawing_review AS nvarchar(5)), ISNULL(CAST(drawing_category AS nvarchar(40)), N'(null)');
GO
SELECT N'proj_region' AS k, ISNULL(CAST(region AS nvarchar(40)), N'(null)') AS a, CAST(COUNT(*) AS nvarchar(20)) AS b, NULL AS c, NULL AS d FROM [ruoyi-vue-pro].dbo.psa_pm_project WHERE DB_NAME() = N'a8_db' GROUP BY ISNULL(CAST(region AS nvarchar(40)), N'(null)') ORDER BY COUNT(*) DESC;
GO
SELECT N'fst_province' AS k, ISNULL(CAST(province AS nvarchar(40)), N'(null)') AS a, CAST(COUNT(*) AS nvarchar(20)) AS b, NULL AS c, NULL AS d FROM [ruoyi-vue-pro].dbo.T_FST_PROJECT WHERE DB_NAME() = N'a8_db' GROUP BY ISNULL(CAST(province AS nvarchar(40)), N'(null)') ORDER BY COUNT(*) DESC;
GO
