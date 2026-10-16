-- one-data Q-02 岗位与职级取证第 2 步（续）：老系统岗位、两边对照、员工档案里职级候选列的分布与和岗位名称职级代码的一致性（取证账号，只出聚合；人数 1–2 写「< 3」）。只在 a8_db 上下文出结果。
WITH rp AS (SELECT sp.id, sp.code, sp.name, sp.status, CAST(sp.deleted AS nvarchar(5)) AS deleted FROM [ruoyi-vue-pro].dbo.system_post sp WHERE DB_NAME() = N'a8_db'),
     rup AS (SELECT up.user_id, up.post_id FROM [ruoyi-vue-pro].dbo.system_user_post up WHERE DB_NAME() = N'a8_db' AND CAST(up.deleted AS nvarchar(5)) IN (N'0', N'false')),
     ap AS (SELECT P.ID, P.CODE, P.NAME, P.IS_DELETED FROM a8_db.dbo.ORG_POST P WHERE DB_NAME() = N'a8_db')
SELECT sec, label, n FROM (
    SELECT N'B' AS sec, N'system_post rows' AS label, CAST(COUNT(*) AS nvarchar(20)) AS n FROM rp
    UNION ALL SELECT N'B', N'system_post rows status=' + ISNULL(CAST(status AS nvarchar(10)), N'null') + N' deleted=' + deleted, CAST(COUNT(*) AS nvarchar(20)) FROM rp GROUP BY status, deleted
    UNION ALL SELECT N'B', N'system_post (deleted=0) that have at least one user', CAST(COUNT(DISTINCT rup.post_id) AS nvarchar(20)) FROM rup JOIN rp ON rp.id = rup.post_id AND rp.deleted IN (N'0', N'false')
    UNION ALL SELECT N'B', N'users with at least one post', CAST(COUNT(DISTINCT user_id) AS nvarchar(20)) FROM rup
    UNION ALL SELECT N'B', N'users with two or more posts', CASE WHEN c BETWEEN 1 AND 2 THEN N'< 3' ELSE CAST(c AS nvarchar(20)) END FROM (SELECT COUNT(*) AS c FROM (SELECT user_id FROM rup GROUP BY user_id HAVING COUNT(*) >= 2) t) u
    UNION ALL SELECT N'C', N'A8 posts (all rows) whose CODE equals a live system_post code exactly', CAST(COUNT(*) AS nvarchar(20)) FROM ap WHERE EXISTS (SELECT 1 FROM rp WHERE rp.deleted IN (N'0', N'false') AND rp.code COLLATE Latin1_General_BIN2 = ap.CODE COLLATE Latin1_General_BIN2)
    UNION ALL SELECT N'C', N'A8 posts (all rows) whose NAME equals a live system_post name exactly', CAST(COUNT(*) AS nvarchar(20)) FROM ap WHERE EXISTS (SELECT 1 FROM rp WHERE rp.deleted IN (N'0', N'false') AND rp.name COLLATE Latin1_General_BIN2 = ap.NAME COLLATE Latin1_General_BIN2)
    UNION ALL SELECT N'C', N'A8 posts (all rows) with no code match', CAST(COUNT(*) AS nvarchar(20)) FROM ap WHERE NOT EXISTS (SELECT 1 FROM rp WHERE rp.deleted IN (N'0', N'false') AND rp.code COLLATE Latin1_General_BIN2 = ap.CODE COLLATE Latin1_General_BIN2)
    UNION ALL SELECT N'C', N'A8 posts (all rows) with no name match', CAST(COUNT(*) AS nvarchar(20)) FROM ap WHERE NOT EXISTS (SELECT 1 FROM rp WHERE rp.deleted IN (N'0', N'false') AND rp.name COLLATE Latin1_General_BIN2 = ap.NAME COLLATE Latin1_General_BIN2)
    UNION ALL SELECT N'C', N'live system_post with no code match in ORG_POST', CAST(COUNT(*) AS nvarchar(20)) FROM rp WHERE rp.deleted IN (N'0', N'false') AND NOT EXISTS (SELECT 1 FROM ap WHERE ap.CODE COLLATE Latin1_General_BIN2 = rp.code COLLATE Latin1_General_BIN2)
    UNION ALL SELECT N'C', N'live system_post with no name match in ORG_POST', CAST(COUNT(*) AS nvarchar(20)) FROM rp WHERE rp.deleted IN (N'0', N'false') AND NOT EXISTS (SELECT 1 FROM ap WHERE ap.NAME COLLATE Latin1_General_BIN2 = rp.name COLLATE Latin1_General_BIN2)
    UNION ALL SELECT N'C', N'live system_post rows', CAST(COUNT(*) AS nvarchar(20)) FROM rp WHERE rp.deleted IN (N'0', N'false')
) r ORDER BY sec, label;
GO
WITH am AS (
    SELECT M.ID, M.CODE, M.ORG_POST_ID, M.ORG_LEVEL_ID
      FROM a8_db.dbo.ORG_MEMBER M JOIN a8_db.dbo.ORG_UNIT U ON U.ID = M.ORG_DEPARTMENT_ID
     WHERE DB_NAME() = N'a8_db' AND M.IS_DELETED = 0 AND M.IS_ENABLE = 1 AND M.STATE = 1 AND (M.IS_INTERNAL = 1 OR M.IS_INTERNAL IS NULL)
       AND M.CODE IS NOT NULL AND M.CODE <> N'' AND U.PATH LIKE N'00000001%'),
pl AS (SELECT P.ID, CASE WHEN P.NAME LIKE N'T[0-9]%' THEN LEFT(P.NAME, PATINDEX(N'%[^0-9]%', SUBSTRING(P.NAME, 2, 50) + N'x')) END AS lvl FROM a8_db.dbo.ORG_POST P WHERE DB_NAME() = N'a8_db'),
x AS (
    SELECT am.ID, pl.lvl, F.f294, F.f242, F.f413, F.f241, F.f233, F.f641, am.ORG_POST_ID
      FROM am LEFT JOIN pl ON pl.ID = am.ORG_POST_ID
      OUTER APPLY (SELECT TOP 1 CAST(FM.field0294 AS nvarchar(200)) AS f294, CAST(FM.field0242 AS nvarchar(200)) AS f242, CAST(FM.field0413 AS nvarchar(200)) AS f413,
                          CAST(FM.field0241 AS nvarchar(200)) AS f241, CAST(FM.field0233 AS nvarchar(200)) AS f233, CAST(FM.field0641 AS nvarchar(200)) AS f641
                     FROM a8_db.dbo.formmain_0307 FM WHERE FM.field0217 = am.CODE ORDER BY FM.ID DESC) F),
c AS (SELECT N'f294 能力等级(text)' AS col, f294 AS v FROM x UNION ALL SELECT N'f242 职业通道(select)', f242 FROM x UNION ALL SELECT N'f413 职务级别(level)', f413 FROM x
      UNION ALL SELECT N'f233 职称(select)', f233 FROM x UNION ALL SELECT N'f641 薪等(select)', f641 FROM x)
SELECT sec, label, n FROM (
    SELECT N'E' AS sec, N'active people matched to a 0307 row' AS label, CAST(COUNT(*) AS nvarchar(20)) AS n FROM x WHERE f241 IS NOT NULL OR f294 IS NOT NULL OR f413 IS NOT NULL OR f242 IS NOT NULL
    UNION ALL SELECT N'E', col + N': non-empty', CAST(SUM(CASE WHEN v IS NOT NULL AND LEN(v) > 0 THEN 1 ELSE 0 END) AS nvarchar(20)) FROM c GROUP BY col
    UNION ALL SELECT N'E', col + N': distinct non-empty values', CAST(COUNT(DISTINCT CASE WHEN LEN(v) > 0 THEN v END) AS nvarchar(20)) FROM c GROUP BY col
    UNION ALL SELECT N'E', col + N': value looks like T+digits', CASE WHEN s BETWEEN 1 AND 2 THEN N'< 3' ELSE CAST(s AS nvarchar(20)) END
      FROM (SELECT col, SUM(CASE WHEN v LIKE N'T[0-9]%' THEN 1 ELSE 0 END) AS s FROM c GROUP BY col) d
    UNION ALL SELECT N'E', N'f294 能力等级 vs post level code: ' + k, CASE WHEN s BETWEEN 1 AND 2 THEN N'< 3' ELSE CAST(s AS nvarchar(20)) END
      FROM (SELECT CASE WHEN lvl IS NULL THEN N'post has no level code' WHEN f294 IS NULL OR LEN(f294) = 0 THEN N'post code, column empty' WHEN f294 = lvl THEN N'equal' ELSE N'different' END AS k, COUNT(*) AS s FROM x GROUP BY
              CASE WHEN lvl IS NULL THEN N'post has no level code' WHEN f294 IS NULL OR LEN(f294) = 0 THEN N'post code, column empty' WHEN f294 = lvl THEN N'equal' ELSE N'different' END) e
    UNION ALL SELECT N'E', N'0307 field0241 (post) vs ORG_MEMBER post id: ' + k, CASE WHEN s BETWEEN 1 AND 2 THEN N'< 3' ELSE CAST(s AS nvarchar(20)) END
      FROM (SELECT CASE WHEN f241 IS NULL OR LEN(f241) = 0 THEN N'field0241 empty' WHEN f241 = CAST(ORG_POST_ID AS nvarchar(40)) THEN N'field0241 = org post' ELSE N'field0241 differs' END AS k, COUNT(*) AS s FROM x GROUP BY
              CASE WHEN f241 IS NULL OR LEN(f241) = 0 THEN N'field0241 empty' WHEN f241 = CAST(ORG_POST_ID AS nvarchar(40)) THEN N'field0241 = org post' ELSE N'field0241 differs' END) e
) r ORDER BY sec, label;
GO
