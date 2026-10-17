-- one-data Q-02 岗位与职级取证第 2 步（ADR-0037，取证账号，只出聚合；人数 1–2 的小格写「< 3」；不出人名与单个人的职级）。只在 a8_db 上下文出结果。
-- 在职 = 同步范围内（部门 PATH 在根 00000001 下、内部或空、工号非空）、未删除、启用、STATE = 1。
WITH am AS (
    SELECT M.ID, M.CODE, M.ORG_POST_ID, M.ORG_LEVEL_ID
      FROM a8_db.dbo.ORG_MEMBER M JOIN a8_db.dbo.ORG_UNIT U ON U.ID = M.ORG_DEPARTMENT_ID
     WHERE DB_NAME() = N'a8_db' AND M.IS_DELETED = 0 AND M.IS_ENABLE = 1 AND M.STATE = 1 AND (M.IS_INTERNAL = 1 OR M.IS_INTERNAL IS NULL)
       AND M.CODE IS NOT NULL AND M.CODE <> N'' AND U.PATH LIKE N'00000001%'),
p AS (
    SELECT P.ID, P.CODE, P.NAME, P.STATUS, P.IS_DELETED,
           CASE WHEN P.NAME LIKE N'T[0-9]%' THEN LEFT(P.NAME, PATINDEX(N'%[^0-9]%', SUBSTRING(P.NAME, 2, 50) + N'x')) END AS lvl,
           (SELECT COUNT(*) FROM am WHERE am.ORG_POST_ID = P.ID) AS active_n
      FROM a8_db.dbo.ORG_POST P WHERE DB_NAME() = N'a8_db'),
pf AS (
    SELECT p.*, SUBSTRING(SUBSTRING(p.NAME, LEN(p.lvl) + 1, 200), PATINDEX(N'%[^ _·—:：、.-]%', SUBSTRING(p.NAME, LEN(p.lvl) + 1, 200) + N'x'), 200) AS func
      FROM p WHERE p.lvl IS NOT NULL)
SELECT sec, label, n FROM (
    SELECT N'A1' AS sec, N'ORG_POST rows' AS label, CAST(COUNT(*) AS nvarchar(20)) AS n FROM p
    UNION ALL SELECT N'A1', N'rows status=' + ISNULL(CAST(STATUS AS nvarchar(10)), N'null') + N' is_deleted=' + ISNULL(CAST(IS_DELETED AS nvarchar(10)), N'null'), CAST(COUNT(*) AS nvarchar(20)) FROM p GROUP BY STATUS, IS_DELETED
    UNION ALL SELECT N'A1', N'posts with >=1 active (all rows)', CAST(COUNT(*) AS nvarchar(20)) FROM p WHERE active_n > 0
    UNION ALL SELECT N'A1', N'posts with 0 active (all rows)', CAST(COUNT(*) AS nvarchar(20)) FROM p WHERE active_n = 0
    UNION ALL SELECT N'A1', N'posts with >=1 active (is_deleted=0)', CAST(COUNT(*) AS nvarchar(20)) FROM p WHERE active_n > 0 AND IS_DELETED = 0
    UNION ALL SELECT N'A1', N'posts with 0 active (is_deleted=0)', CAST(COUNT(*) AS nvarchar(20)) FROM p WHERE active_n = 0 AND IS_DELETED = 0
    UNION ALL SELECT N'A1', N'posts per active-headcount bucket ' + b, CAST(COUNT(*) AS nvarchar(20)) FROM (
        SELECT CASE WHEN active_n = 1 THEN N'1' WHEN active_n BETWEEN 2 AND 4 THEN N'2-4' WHEN active_n BETWEEN 5 AND 9 THEN N'5-9' WHEN active_n BETWEEN 10 AND 19 THEN N'10-19' ELSE N'>=20' END AS b
          FROM p WHERE active_n > 0) x GROUP BY b
    UNION ALL SELECT N'A1', N'active people without a post (null or unknown post id)', CASE WHEN c BETWEEN 1 AND 2 THEN N'< 3' ELSE CAST(c AS nvarchar(20)) END
      FROM (SELECT COUNT(*) AS c FROM am WHERE am.ORG_POST_ID IS NULL OR NOT EXISTS (SELECT 1 FROM p WHERE p.ID = am.ORG_POST_ID)) y
    UNION ALL SELECT N'A1', N'active people total', CAST(COUNT(*) AS nvarchar(20)) FROM am
    UNION ALL SELECT N'A1', N'posts whose name starts with T+digits', CAST(COUNT(*) AS nvarchar(20)) FROM p WHERE lvl IS NOT NULL
    UNION ALL SELECT N'A1', N'posts whose name does not', CAST(COUNT(*) AS nvarchar(20)) FROM p WHERE lvl IS NULL
    UNION ALL SELECT N'A1', N'distinct level codes in post names', CAST(COUNT(DISTINCT lvl) AS nvarchar(20)) FROM p WHERE lvl IS NOT NULL
    UNION ALL SELECT N'A1', N'active people at level code ' + lvl, CASE WHEN s BETWEEN 1 AND 2 THEN N'< 3' ELSE CAST(s AS nvarchar(20)) END
      FROM (SELECT lvl, SUM(active_n) AS s FROM p WHERE lvl IS NOT NULL GROUP BY lvl) z
    UNION ALL SELECT N'A1', N'distinct function names after removing the level code', CAST(COUNT(DISTINCT func) AS nvarchar(20)) FROM pf
    UNION ALL SELECT N'A4', N'ORG_RELATIONSHIP rows by TYPE ' + CAST(TYPE AS nvarchar(100)), CAST(COUNT(*) AS nvarchar(20)) FROM a8_db.dbo.ORG_RELATIONSHIP WHERE DB_NAME() = N'a8_db' GROUP BY TYPE
    UNION ALL SELECT N'L1', N'active people with ORG_LEVEL (management title) set', CASE WHEN c BETWEEN 1 AND 2 THEN N'< 3' ELSE CAST(c AS nvarchar(20)) END
      FROM (SELECT COUNT(*) AS c FROM am WHERE am.ORG_LEVEL_ID IS NOT NULL) l1
    UNION ALL SELECT N'L1', N'active people by ORG_LEVEL ' + ISNULL(l.NAME, N'(unknown level)'), CASE WHEN c BETWEEN 1 AND 2 THEN N'< 3' ELSE CAST(c AS nvarchar(20)) END
      FROM (SELECT am.ORG_LEVEL_ID, COUNT(*) AS c FROM am WHERE am.ORG_LEVEL_ID IS NOT NULL GROUP BY am.ORG_LEVEL_ID) g LEFT JOIN a8_db.dbo.ORG_LEVEL l ON l.ID = g.ORG_LEVEL_ID
) r ORDER BY sec, label;
GO
