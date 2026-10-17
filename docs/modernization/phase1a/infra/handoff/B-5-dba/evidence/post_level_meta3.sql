-- one-data Q-02 补充（元数据）：找出人事表 formmain_0307 对应的表单定义，看字段描述的写法，再定位「职级」字段。只在 a8_db 出结果。
SELECT d.ID AS form_id, d.NAME AS form_name, d.DELETE_FLAG, d.USE_FLAG, LEN(CAST(d.FIELD_INFO AS nvarchar(max))) AS field_info_len
  FROM a8_db.dbo.CAP_FORM_DEFINITION d
 WHERE DB_NAME() = N'a8_db' AND CAST(d.FIELD_INFO AS nvarchar(max)) LIKE N'%field0217%' AND CAST(d.FIELD_INFO AS nvarchar(max)) LIKE N'%field0244%';
GO
WITH f AS (SELECT d.ID, CAST(d.FIELD_INFO AS nvarchar(max)) AS t FROM a8_db.dbo.CAP_FORM_DEFINITION d
            WHERE DB_NAME() = N'a8_db' AND CAST(d.FIELD_INFO AS nvarchar(max)) LIKE N'%field0217%' AND CAST(d.FIELD_INFO AS nvarchar(max)) LIKE N'%field0244%')
SELECT f.ID AS form_id, SUBSTRING(f.t, CHARINDEX(N'field0244', f.t) - 200, 520) AS context_around_field0244 FROM f;
GO
