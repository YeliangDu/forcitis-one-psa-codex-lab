-- one-data Q-02 补充（元数据）：员工档案表（formmain_0307）表单定义里与职级、岗位、级别相关的字段：显示名、列名、类型。只读表单定义，不读人员数据。只在 a8_db 出结果。
WITH t AS (SELECT CAST(d.FIELD_INFO AS nvarchar(max)) AS s FROM a8_db.dbo.CAP_FORM_DEFINITION d WHERE DB_NAME() = N'a8_db' AND d.ID = -3418526626173990689),
     p AS (SELECT CHARINDEX(N'"display":"', s) AS pos, s FROM t
           UNION ALL SELECT CHARINDEX(N'"display":"', s, pos + 1), s FROM p WHERE CHARINDEX(N'"display":"', s, pos + 1) > 0),
     f AS (SELECT pos,
                  SUBSTRING(s, pos + 11, CHARINDEX(N'"', s, pos + 11) - (pos + 11)) AS display_name,
                  SUBSTRING(s, CHARINDEX(N'"columnName":"', s, pos) + 14, CHARINDEX(N'"', s, CHARINDEX(N'"columnName":"', s, pos) + 14) - (CHARINDEX(N'"columnName":"', s, pos) + 14)) AS column_name,
                  SUBSTRING(s, CHARINDEX(N'"type":"', s, pos) + 8, CHARINDEX(N'"', s, CHARINDEX(N'"type":"', s, pos) + 8) - (CHARINDEX(N'"type":"', s, pos) + 8)) AS field_type
             FROM p WHERE pos > 0)
SELECT display_name, column_name, field_type FROM f
 WHERE display_name LIKE N'%级%' OR display_name LIKE N'%职%' OR display_name LIKE N'%岗%' OR display_name LIKE N'%等%' OR display_name LIKE N'%层%' OR display_name LIKE N'%序列%'
 ORDER BY display_name OPTION (MAXRECURSION 3000);
GO
