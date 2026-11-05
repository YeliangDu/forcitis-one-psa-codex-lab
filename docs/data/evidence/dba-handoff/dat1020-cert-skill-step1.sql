-- #1309 step 1 (design card for skills and practice certificates; follows dat1020-discover.sql, which did not look for skills and whose result was never committed).
-- Run as one_evidence_ro (ADR-0037, ADR-0088) in the a8_db context. Aggregates only: counts, distinct counts, year buckets. No value of any column is printed.
-- Every cell that counts 1 or 2 rows is shown as '< 3'. Do not run dat1020-discover2.sql line 6 (top certificate names): free text may carry a person's name or a certificate number.
-- Step 2 (the person-holder, expiry, registered-unit and attachment counts) is written after this step returns the real column names of HR_STAFF_CERTIFICATE; the design spec marks what waits for it.
SET LOCK_TIMEOUT 1000;
GO
-- 1. Where certificates and skills live: tables and columns whose names say so, in the A8 and the legacy PSA database. Metadata only.
SELECT N'cs_tables' AS k, t.TABLE_CATALOG AS a, t.TABLE_NAME AS b, NULL AS c
  FROM a8_db.INFORMATION_SCHEMA.TABLES t
 WHERE DB_NAME() = N'a8_db' AND t.TABLE_NAME NOT LIKE N'formson%'
   AND (t.TABLE_NAME LIKE N'%cert%' OR t.TABLE_NAME LIKE N'%qualif%' OR t.TABLE_NAME LIKE N'%regist%' OR t.TABLE_NAME LIKE N'%skill%' OR t.TABLE_NAME LIKE N'%licen%' OR t.TABLE_NAME LIKE N'%train%')
UNION ALL
SELECT N'cs_tables', N'ruoyi-vue-pro', t.TABLE_NAME, NULL
  FROM [ruoyi-vue-pro].INFORMATION_SCHEMA.TABLES t
 WHERE DB_NAME() = N'a8_db'
   AND (t.TABLE_NAME LIKE N'%cert%' OR t.TABLE_NAME LIKE N'%qualif%' OR t.TABLE_NAME LIKE N'%regist%' OR t.TABLE_NAME LIKE N'%skill%' OR t.TABLE_NAME LIKE N'%licen%' OR t.TABLE_NAME LIKE N'%train%' OR t.TABLE_NAME LIKE N'%tag%');
GO
SELECT N'cs_cols' AS k, N'ruoyi-vue-pro' AS a, c.TABLE_NAME AS b, c.COLUMN_NAME + N' ' + c.DATA_TYPE AS c
  FROM [ruoyi-vue-pro].INFORMATION_SCHEMA.COLUMNS c
 WHERE DB_NAME() = N'a8_db' AND (c.TABLE_NAME LIKE N'%skill%' OR c.TABLE_NAME LIKE N'%cert%')
 ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION;
GO
SELECT N'cs_cols' AS k, N'a8_db' AS a, c.TABLE_NAME AS b, c.COLUMN_NAME + N' ' + c.DATA_TYPE AS c
  FROM a8_db.INFORMATION_SCHEMA.COLUMNS c
 WHERE DB_NAME() = N'a8_db' AND c.TABLE_NAME IN (N'HR_STAFF_CERTIFICATE', N'HR_STAFF_INFO')
 ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION;
GO
-- 2. Volume: rows per table found above, from the catalog (no table scan).
SELECT N'cs_rows' AS k, CAST(t.name AS nvarchar(128)) AS a, CAST(SUM(p.row_count) AS nvarchar(20)) AS b, NULL AS c
  FROM a8_db.sys.dm_db_partition_stats p JOIN a8_db.sys.tables t ON t.object_id = p.object_id
 WHERE DB_NAME() = N'a8_db' AND p.index_id IN (0, 1) AND t.name IN (N'HR_STAFF_CERTIFICATE', N'HR_STAFF_INFO')
 GROUP BY t.name;
GO
SELECT N'cs_rows' AS k, N'psa_pm_user_skill_tags' AS a, CAST(COUNT(*) AS nvarchar(20)) AS b, NULL AS c FROM [ruoyi-vue-pro].dbo.psa_pm_user_skill_tags WHERE DB_NAME() = N'a8_db';
GO
-- Fill rates, year buckets of the date columns and the skill-tag counts are static queries written in step 2, once the cs_cols result above names the real columns (the read-only runner refuses EXEC and dynamic SQL).
