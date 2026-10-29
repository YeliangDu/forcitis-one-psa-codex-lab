-- one-data Q-02 ④：A8 的兼任或副岗关系。先判断 ORG_RELATIONSHIP 里 Member_Post 与 Department_Post 的列含义（只数行数，看各 OBJECTIVE 列落在哪类对象的 ID 上），再数兼任。小格写「< 3」。只在 a8_db 出结果。
WITH am AS (
    SELECT M.ID, M.ORG_POST_ID, M.ORG_DEPARTMENT_ID
      FROM a8_db.dbo.ORG_MEMBER M JOIN a8_db.dbo.ORG_UNIT U ON U.ID = M.ORG_DEPARTMENT_ID
     WHERE DB_NAME() = N'a8_db' AND M.IS_DELETED = 0 AND M.IS_ENABLE = 1 AND M.STATE = 1 AND (M.IS_INTERNAL = 1 OR M.IS_INTERNAL IS NULL)
       AND M.CODE IS NOT NULL AND M.CODE <> N'' AND U.PATH LIKE N'00000001%'),
mp AS (SELECT R.* FROM a8_db.dbo.ORG_RELATIONSHIP R WHERE DB_NAME() = N'a8_db' AND R.TYPE = N'Member_Post')
SELECT sec, label, n FROM (
    SELECT N'M1' AS sec, N'Member_Post rows' AS label, CAST(COUNT(*) AS nvarchar(20)) AS n FROM mp
    UNION ALL SELECT N'M1', N'Member_Post rows whose SOURCE_ID is an active person', CAST(COUNT(*) AS nvarchar(20)) FROM mp WHERE mp.SOURCE_ID IN (SELECT ID FROM am)
    UNION ALL SELECT N'M1', N'Member_Post rows: OBJECTIVE0_ID is a post id', CAST(COUNT(*) AS nvarchar(20)) FROM mp WHERE mp.OBJECTIVE0_ID IN (SELECT ID FROM a8_db.dbo.ORG_POST)
    UNION ALL SELECT N'M1', N'Member_Post rows: OBJECTIVE0_ID is a department id', CAST(COUNT(*) AS nvarchar(20)) FROM mp WHERE mp.OBJECTIVE0_ID IN (SELECT ID FROM a8_db.dbo.ORG_UNIT)
    UNION ALL SELECT N'M1', N'Member_Post rows: OBJECTIVE1_ID is a post id', CAST(COUNT(*) AS nvarchar(20)) FROM mp WHERE mp.OBJECTIVE1_ID IN (SELECT ID FROM a8_db.dbo.ORG_POST)
    UNION ALL SELECT N'M1', N'Member_Post rows: OBJECTIVE1_ID is a department id', CAST(COUNT(*) AS nvarchar(20)) FROM mp WHERE mp.OBJECTIVE1_ID IN (SELECT ID FROM a8_db.dbo.ORG_UNIT)
    UNION ALL SELECT N'M1', N'Member_Post rows: OBJECTIVE2_ID null', CAST(COUNT(*) AS nvarchar(20)) FROM mp WHERE mp.OBJECTIVE2_ID IS NULL
    UNION ALL SELECT N'M1', N'Member_Post rows: OBJECTIVE0_ID distinct value count (small = a flag, not an id)', CAST(COUNT(DISTINCT mp.OBJECTIVE0_ID) AS nvarchar(20)) FROM mp
    UNION ALL SELECT N'M1', N'Member_Post rows: OBJECTIVE1_ID distinct value count', CAST(COUNT(DISTINCT mp.OBJECTIVE1_ID) AS nvarchar(20)) FROM mp
    UNION ALL SELECT N'M1', N'Member_Post rows: OBJECTIVE2_ID distinct value count', CAST(COUNT(DISTINCT mp.OBJECTIVE2_ID) AS nvarchar(20)) FROM mp
    UNION ALL SELECT N'M1', N'Member_Post rows: SORT_ID distinct value count', CAST(COUNT(DISTINCT mp.SORT_ID) AS nvarchar(20)) FROM mp
) r ORDER BY sec, label;
GO
