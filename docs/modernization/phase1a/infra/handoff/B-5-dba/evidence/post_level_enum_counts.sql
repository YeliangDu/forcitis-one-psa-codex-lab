-- one-data Q-02 补充取证：①在职人员在员工档案四个字段上的值分布（枚举取显示名）；②老系统职级列的取值与行数；③工时表引用的岗位在 A8 的归类。只在 a8_db 上下文出结果；人数 1–2 写「< 3」，不给人。
WITH am AS (
    SELECT M.ID, M.CODE, M.ORG_POST_ID
      FROM a8_db.dbo.ORG_MEMBER M JOIN a8_db.dbo.ORG_UNIT U ON U.ID = M.ORG_DEPARTMENT_ID
     WHERE DB_NAME() = N'a8_db' AND M.IS_DELETED = 0 AND M.IS_ENABLE = 1 AND M.STATE = 1 AND (M.IS_INTERNAL = 1 OR M.IS_INTERNAL IS NULL)
       AND M.CODE IS NOT NULL AND M.CODE <> N'' AND U.PATH LIKE N'00000001%'),
x AS (
    SELECT am.ID, F.f294, F.f242, F.f641, F.f233
      FROM am OUTER APPLY (SELECT TOP 1 CAST(FM.field0294 AS nvarchar(200)) AS f294, CAST(FM.field0242 AS nvarchar(200)) AS f242,
                                  CAST(FM.field0641 AS nvarchar(200)) AS f641, CAST(FM.field0233 AS nvarchar(200)) AS f233
                             FROM a8_db.dbo.formmain_0307 FM WHERE FM.field0217 = am.CODE ORDER BY FM.ID DESC) F),
c AS (SELECT N'field0242 职业通道' AS col, f242 AS v, 1 AS en FROM x UNION ALL SELECT N'field0641 薪等', f641, 1 FROM x
      UNION ALL SELECT N'field0233 职称', f233, 1 FROM x UNION ALL SELECT N'field0294 能力等级', f294, 0 FROM x),
d AS (SELECT c.col, CASE WHEN c.v IS NULL OR LEN(c.v) = 0 THEN N'(empty)'
                         WHEN c.en = 0 THEN c.v
                         ELSE ISNULL((SELECT TOP 1 CAST(i.SHOWVALUE AS nvarchar(100)) FROM a8_db.dbo.CTP_ENUM_ITEM i WHERE CAST(i.ID AS nvarchar(40)) = c.v), N'(unmatched enum value)') END AS shown
        FROM c WHERE DB_NAME() = N'a8_db'),
tsp AS (SELECT ts.post_id, COUNT(*) AS rows_all, SUM(CASE WHEN ts.deleted = 0 THEN 1 ELSE 0 END) AS rows_live
          FROM [ruoyi-vue-pro].dbo.psa_time_sheet ts WHERE DB_NAME() = N'a8_db' GROUP BY ts.post_id),
tsc AS (SELECT t.post_id, t.rows_all, t.rows_live, sp.code,
               ap.ID AS apid, CASE WHEN ap.ID IS NULL THEN 0 ELSE 1 END AS matched,
               CASE WHEN CAST(ap.IS_DELETED AS nvarchar(5)) IN (N'1', N'true') THEN 1 ELSE 0 END AS a_del,
               (SELECT COUNT(*) FROM am WHERE am.ORG_POST_ID = ap.ID) AS people
          FROM tsp t LEFT JOIN [ruoyi-vue-pro].dbo.system_post sp ON sp.id = t.post_id
          OUTER APPLY (SELECT TOP 1 P.ID, P.IS_DELETED FROM a8_db.dbo.ORG_POST P WHERE P.CODE COLLATE Latin1_General_BIN2 = sp.code COLLATE Latin1_General_BIN2 ORDER BY P.ID) ap)
SELECT sec, label, n FROM (
    SELECT N'A' AS sec, col + N' = ' + shown AS label, CASE WHEN COUNT(*) BETWEEN 1 AND 2 THEN N'< 3' ELSE CAST(COUNT(*) AS nvarchar(20)) END AS n FROM d GROUP BY col, shown
    UNION ALL SELECT N'B', N'mdm_bill_rate_detail.job_level_code = ' + ISNULL(job_level_code, N'(null)'), CASE WHEN COUNT(*) BETWEEN 1 AND 2 THEN N'< 3' ELSE CAST(COUNT(*) AS nvarchar(20)) END
      FROM [ruoyi-vue-pro].dbo.mdm_bill_rate_detail WHERE DB_NAME() = N'a8_db' GROUP BY job_level_code
    UNION ALL SELECT N'B', N'mdm_bill_rate_detail.salary_grade_no = ' + ISNULL(salary_grade_no, N'(null)'), CASE WHEN COUNT(*) BETWEEN 1 AND 2 THEN N'< 3' ELSE CAST(COUNT(*) AS nvarchar(20)) END
      FROM [ruoyi-vue-pro].dbo.mdm_bill_rate_detail WHERE DB_NAME() = N'a8_db' GROUP BY salary_grade_no
    UNION ALL SELECT N'B', N'psa_employee_transfer_record.job_level_code = ' + ISNULL(job_level_code, N'(null)'), CASE WHEN COUNT(*) BETWEEN 1 AND 2 THEN N'< 3' ELSE CAST(COUNT(*) AS nvarchar(20)) END
      FROM [ruoyi-vue-pro].dbo.psa_employee_transfer_record WHERE DB_NAME() = N'a8_db' GROUP BY job_level_code
    UNION ALL SELECT N'C', N'time sheet distinct post_id (incl. null as one)', CAST(COUNT(*) AS nvarchar(20)) FROM tsp WHERE DB_NAME() = N'a8_db'
    UNION ALL SELECT N'C', N'class=' + cls + N' : distinct posts', CAST(COUNT(*) AS nvarchar(20)) FROM (SELECT CASE WHEN post_id IS NULL THEN N'1 null post_id' WHEN code IS NULL THEN N'2 no system_post row' WHEN matched = 0 THEN N'3 no A8 post with same code' WHEN a_del = 1 THEN N'4 A8 deleted post' WHEN people = 0 THEN N'5 A8 live post without active people' ELSE N'6 A8 live post with active people' END AS cls FROM tsc) z WHERE DB_NAME() = N'a8_db' GROUP BY cls
    UNION ALL SELECT N'C', N'class=' + cls + N' : time sheet rows (all / not deleted)', CAST(SUM(rows_all) AS nvarchar(20)) + N' / ' + CAST(SUM(rows_live) AS nvarchar(20)) FROM (SELECT rows_all, rows_live, CASE WHEN post_id IS NULL THEN N'1 null post_id' WHEN code IS NULL THEN N'2 no system_post row' WHEN matched = 0 THEN N'3 no A8 post with same code' WHEN a_del = 1 THEN N'4 A8 deleted post' WHEN people = 0 THEN N'5 A8 live post without active people' ELSE N'6 A8 live post with active people' END AS cls FROM tsc) z GROUP BY cls
) r ORDER BY sec, label;
GO
