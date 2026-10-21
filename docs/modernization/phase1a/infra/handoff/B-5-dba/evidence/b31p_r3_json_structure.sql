-- B31P 第三批②续：FIELD_INFO 不是 XML（看起来是 JSON）。在 SQL Server 内用 OPENJSON 递归，只输出键路径（数组下标记为 []）、值类型与出现次数；不输出任何值。one_evidence_ro（ADR-0037 第 7 项）。
SET LOCK_TIMEOUT 1000;
SELECT t.ID AS template_id, ISJSON(CONVERT(nvarchar(max), d.FIELD_INFO)) AS is_json, LEFT(LTRIM(CONVERT(nvarchar(max), d.FIELD_INFO)), 1) AS first_char
  FROM [a8_db].dbo.CTP_TEMPLATE AS t JOIN [a8_db].dbo.CAP_FORM_DEFINITION AS d ON d.ID = t.FORM_APPID
 WHERE DB_NAME() = N'a8_db' AND t.ID IN (5779151688961574431, -803037765431705053);
WITH base AS (SELECT t.ID AS template_id, CONVERT(nvarchar(max), d.FIELD_INFO) AS j
                FROM [a8_db].dbo.CTP_TEMPLATE AS t JOIN [a8_db].dbo.CAP_FORM_DEFINITION AS d ON d.ID = t.FORM_APPID
               WHERE DB_NAME() = N'a8_db' AND t.ID IN (5779151688961574431, -803037765431705053) AND ISJSON(CONVERT(nvarchar(max), d.FIELD_INFO)) = 1),
     walk AS (SELECT b.template_id, CAST(N'$.' + o.[key] AS nvarchar(1000)) AS path, CAST(0 AS int) AS depth, o.[type] AS value_type, o.[value] AS v
                FROM base AS b CROSS APPLY OPENJSON(b.j) AS o
               UNION ALL
              SELECT w.template_id, CAST(w.path + N'.' + CASE WHEN w.value_type = 4 THEN N'[]' ELSE o.[key] END AS nvarchar(1000)), w.depth + 1, o.[type], o.[value]
                FROM walk AS w CROSS APPLY OPENJSON(w.v) AS o WHERE w.value_type IN (4, 5) AND w.depth < 8)
SELECT template_id, path, value_type, COUNT_BIG(*) AS occurrences FROM walk GROUP BY template_id, path, value_type ORDER BY template_id, path OPTION (MAXRECURSION 20);
