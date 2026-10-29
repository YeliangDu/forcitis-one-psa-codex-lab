-- B-31 第三步 b：formmain_0643 对应的表单定义（字段列名与显示名在 FIELD_INFO 里）。只取定义文本，不含记录数据；取回后在本机按字段抽取 (列名, 显示名) 并做个人信息模式扫描。one_evidence_ro（ADR-0037 第 7 项）。仅 a8_db 出结果。
SET LOCK_TIMEOUT 1000;
IF DB_NAME() = N'a8_db'
BEGIN
SELECT t.FORM_ID AS form_id, t.TABLE_NAME AS table_name, t.TABLE_DISPLAY AS table_display, d.NAME AS form_name, d.STATE AS state, d.DELETE_FLAG AS delete_flag, LEN(CAST(d.FIELD_INFO AS nvarchar(max))) AS field_info_length
  FROM a8_db.dbo.CAP_FORM_TABLE_INFO t JOIN a8_db.dbo.CAP_FORM_DEFINITION d ON d.ID = t.FORM_ID
 WHERE t.TABLE_NAME = N'formmain_0643';
SELECT CAST(d.FIELD_INFO AS nvarchar(max)) AS field_info
  FROM a8_db.dbo.CAP_FORM_TABLE_INFO t JOIN a8_db.dbo.CAP_FORM_DEFINITION d ON d.ID = t.FORM_ID
 WHERE t.TABLE_NAME = N'formmain_0643';
END
