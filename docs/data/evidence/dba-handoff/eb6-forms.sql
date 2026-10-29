-- E-B6 前置三：A8 表单模板里与报销、付款、采购、开票、分包相关的表单（名称含关键词）及其主表名。只出表单名与主表名与列数，不读单据。只在 a8_db 上下文出结果。
SELECT D.ID AS form_id, CAST(D.NAME AS nvarchar(80)) AS form_name
  FROM a8_db.dbo.CAP_FORM_DEFINITION D
 WHERE DB_NAME() = N'a8_db'
   AND (D.NAME LIKE N'%报销%' OR D.NAME LIKE N'%付款%' OR D.NAME LIKE N'%采购%' OR D.NAME LIKE N'%开票%' OR D.NAME LIKE N'%发票%' OR D.NAME LIKE N'%分包%' OR D.NAME LIKE N'%外包%' OR D.NAME LIKE N'%费用%' OR D.NAME LIKE N'%借款%');
GO
SELECT c.name AS column_name FROM a8_db.sys.columns c WHERE DB_NAME() = N'a8_db' AND c.object_id = OBJECT_ID(N'a8_db.dbo.CAP_FORM_DEFINITION');
GO
