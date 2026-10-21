-- E-B6 前置四：一张报销表单定义（日常费用报销单）的字段定义片段，用于确定金额字段与主表名。只读表单定义，不读单据。只在 a8_db 上下文出结果。
SELECT LEN(CAST(D.FIELD_INFO AS nvarchar(max))) AS len_field_info, SUBSTRING(CAST(D.FIELD_INFO AS nvarchar(max)), 1, 1800) AS head_part
  FROM a8_db.dbo.CAP_FORM_DEFINITION D WHERE DB_NAME() = N'a8_db' AND D.ID = 3547895782509333077;
GO
