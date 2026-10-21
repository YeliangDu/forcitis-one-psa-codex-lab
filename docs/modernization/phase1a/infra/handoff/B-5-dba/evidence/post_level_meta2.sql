-- one-data Q-02 岗位与职级取证第 1 步补充（元数据）：ORG_LEVEL 的列、人事表单里「职级」字段的显示名与列名（取自表单定义，不读人员数据）。只在 a8_db 出结果。
SELECT c.TABLE_NAME, c.COLUMN_NAME, c.DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS c
 WHERE DB_NAME() = N'a8_db' AND c.TABLE_NAME IN (N'ORG_LEVEL', N'zfORG_POST') ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION;
GO
SELECT d.ID AS form_id, d.NAME AS form_name, d.DELETE_FLAG, d.USE_FLAG, (LEN(CAST(d.FIELD_INFO AS nvarchar(max))) - LEN(REPLACE(CAST(d.FIELD_INFO AS nvarchar(max)), N'职级', N''))) / 2 AS level_word_occurrences
  FROM a8_db.dbo.CAP_FORM_DEFINITION d WHERE DB_NAME() = N'a8_db' AND CAST(d.FIELD_INFO AS nvarchar(max)) LIKE N'%职级%';
GO
-- 职级字样前后各 160 个字符（表单定义里的字段描述：显示名、列名、枚举引用），最多取每个表单前 3 处
WITH f AS (SELECT d.ID, CAST(d.FIELD_INFO AS nvarchar(max)) AS t FROM a8_db.dbo.CAP_FORM_DEFINITION d WHERE DB_NAME() = N'a8_db' AND CAST(d.FIELD_INFO AS nvarchar(max)) LIKE N'%职级%'),
     n AS (SELECT 1 AS k UNION ALL SELECT 2 UNION ALL SELECT 3)
SELECT f.ID AS form_id, n.k AS occurrence,
       SUBSTRING(f.t, CHARINDEX(N'职级', f.t, CASE WHEN n.k = 1 THEN 1 ELSE CHARINDEX(N'职级', f.t) + 1 END) - 160, 340) AS context
  FROM f CROSS JOIN n WHERE n.k = 1;
GO
