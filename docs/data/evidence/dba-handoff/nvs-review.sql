-- 项目级别（N/V/S）：校审记录中各级对应的审核人岗位（项目成果内审、项目阶段评审两张表单，审核人与专项审核人字段）。只出表单×角色×级别×岗位名称的记录数，1–2 条写「< 3」；不出人名。只在 a8_db 上下文出结果。
WITH lv AS (SELECT CAST(I.ID AS nvarchar(40)) AS id, CAST(I.SHOWVALUE AS nvarchar(10)) AS lvl FROM a8_db.dbo.CTP_ENUM_ITEM I WHERE DB_NAME() = N'a8_db' AND I.REF_ENUMID = -7391496778928405449),
src AS (
  SELECT N'项目成果内审' AS form, N'审核人' AS role, CAST(R.field0116 AS nvarchar(40)) AS level_id, TRY_CAST(CAST(R.approve_member_id AS nvarchar(40)) AS bigint) AS member_id FROM a8_db.dbo.formmain_0919 R WHERE DB_NAME() = N'a8_db'
  UNION ALL SELECT N'项目成果内审', N'PMO审核人', CAST(R.field0116 AS nvarchar(40)), TRY_CAST(CAST(R.field0130 AS nvarchar(40)) AS bigint) FROM a8_db.dbo.formmain_0919 R WHERE DB_NAME() = N'a8_db'
  UNION ALL SELECT N'项目阶段评审', N'审核人', CAST(R.field0115 AS nvarchar(40)), TRY_CAST(CAST(R.approve_member_id AS nvarchar(40)) AS bigint) FROM a8_db.dbo.formmain_0973 R WHERE DB_NAME() = N'a8_db'
  UNION ALL SELECT N'项目阶段评审', N'技术研发中心审核人', CAST(R.field0115 AS nvarchar(40)), TRY_CAST(CAST(R.field0135 AS nvarchar(40)) AS bigint) FROM a8_db.dbo.formmain_0973 R WHERE DB_NAME() = N'a8_db'),
j AS (SELECT s.form, s.role, ISNULL(lv.lvl, N'(无级别)') AS lvl, CASE WHEN s.member_id IS NULL THEN N'(审核人为空)' ELSE ISNULL(CAST(P.NAME AS nvarchar(80)), N'(无岗位)') END AS post
        FROM src s LEFT JOIN lv ON lv.id = s.level_id LEFT JOIN a8_db.dbo.ORG_MEMBER M ON M.ID = s.member_id LEFT JOIN a8_db.dbo.ORG_POST P ON P.ID = M.ORG_POST_ID)
SELECT form, role, lvl, post, CASE WHEN COUNT(*) BETWEEN 1 AND 2 THEN N'< 3' ELSE CAST(COUNT(*) AS nvarchar(20)) END AS n FROM j GROUP BY form, role, lvl, post ORDER BY form, role, lvl, COUNT(*) DESC;
GO
