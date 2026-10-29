-- one-data Q-02 ④（续）：兼任或副岗 = Member_Post 关系里（部门 = OBJECTIVE0_ID，岗位 = OBJECTIVE1_ID）与该人在 ORG_MEMBER 里的部门、岗位不同的行。只出计数，小格写「< 3」。只在 a8_db 出结果。
WITH am AS (
    SELECT M.ID, M.ORG_POST_ID, M.ORG_DEPARTMENT_ID
      FROM a8_db.dbo.ORG_MEMBER M JOIN a8_db.dbo.ORG_UNIT U ON U.ID = M.ORG_DEPARTMENT_ID
     WHERE DB_NAME() = N'a8_db' AND M.IS_DELETED = 0 AND M.IS_ENABLE = 1 AND M.STATE = 1 AND (M.IS_INTERNAL = 1 OR M.IS_INTERNAL IS NULL)
       AND M.CODE IS NOT NULL AND M.CODE <> N'' AND U.PATH LIKE N'00000001%'),
mp AS (SELECT R.SOURCE_ID, R.OBJECTIVE0_ID AS dept_id, R.OBJECTIVE1_ID AS post_id, am.ORG_POST_ID, am.ORG_DEPARTMENT_ID
         FROM a8_db.dbo.ORG_RELATIONSHIP R JOIN am ON am.ID = R.SOURCE_ID WHERE DB_NAME() = N'a8_db' AND R.TYPE = N'Member_Post'),
per AS (SELECT SOURCE_ID, COUNT(*) AS rows_n,
               SUM(CASE WHEN post_id = ORG_POST_ID AND dept_id = ORG_DEPARTMENT_ID THEN 1 ELSE 0 END) AS primary_rows,
               SUM(CASE WHEN post_id = ORG_POST_ID AND dept_id = ORG_DEPARTMENT_ID THEN 0 ELSE 1 END) AS other_rows
          FROM mp GROUP BY SOURCE_ID)
SELECT sec, label, n FROM (
    SELECT N'M2' AS sec, N'active people with at least one Member_Post row' AS label, CAST(COUNT(*) AS nvarchar(20)) AS n FROM per
    UNION ALL SELECT N'M2', N'active people with a row equal to their ORG_MEMBER dept+post', CAST(COUNT(*) AS nvarchar(20)) FROM per WHERE primary_rows >= 1
    UNION ALL SELECT N'M2', N'active people without such a row', CASE WHEN c BETWEEN 1 AND 2 THEN N'< 3' ELSE CAST(c AS nvarchar(20)) END FROM (SELECT COUNT(*) AS c FROM per WHERE primary_rows = 0) t
    UNION ALL SELECT N'M2', N'active people with at least one other (concurrent) row', CASE WHEN c BETWEEN 1 AND 2 THEN N'< 3' ELSE CAST(c AS nvarchar(20)) END FROM (SELECT COUNT(*) AS c FROM per WHERE other_rows >= 1) t
    UNION ALL SELECT N'M2', N'other (concurrent) rows for active people', CASE WHEN c BETWEEN 1 AND 2 THEN N'< 3' ELSE CAST(c AS nvarchar(20)) END FROM (SELECT SUM(other_rows) AS c FROM per) t
    UNION ALL SELECT N'M2', N'active people with two or more other rows', CASE WHEN c BETWEEN 1 AND 2 THEN N'< 3' ELSE CAST(c AS nvarchar(20)) END FROM (SELECT COUNT(*) AS c FROM per WHERE other_rows >= 2) t
    UNION ALL SELECT N'M2', N'other rows whose post is in the same department as the main one', CASE WHEN c BETWEEN 1 AND 2 THEN N'< 3' ELSE CAST(c AS nvarchar(20)) END FROM (SELECT COUNT(*) AS c FROM mp WHERE NOT (post_id = ORG_POST_ID AND dept_id = ORG_DEPARTMENT_ID) AND dept_id = ORG_DEPARTMENT_ID) t
) r ORDER BY sec, label;
GO
