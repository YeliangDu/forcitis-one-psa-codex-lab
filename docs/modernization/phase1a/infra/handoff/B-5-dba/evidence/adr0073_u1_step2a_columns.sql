-- ADR-0073 U1 第二步 a：五个对象的全部列、对象类型、视图引用的底表（只读元数据，不取值）。以取证账号 one_evidence_ro 执行（ADR-0037）。
SET LOCK_TIMEOUT 1000;
SELECT DB_NAME() AS database_name, c.TABLE_NAME, c.COLUMN_NAME, c.DATA_TYPE
  FROM INFORMATION_SCHEMA.COLUMNS c
 WHERE (DB_NAME() = N'a8_db' AND c.TABLE_SCHEMA = N'dbo' AND c.TABLE_NAME IN (N'AI_ORG_MEMBER_CHANGE', N'AI_ORG_CHANGE_RECORD', N'ODS_MEMBER_CHANGE', N'ODS_ORG_CHANGE'))
    OR (DB_NAME() = N'ruoyi-vue-pro' AND c.TABLE_SCHEMA = N'dbo' AND c.TABLE_NAME = N'psa_employee_transfer_record')
 ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION;
SELECT DB_NAME() AS database_name, o.name AS object_name, o.type_desc
  FROM sys.objects o
 WHERE (DB_NAME() = N'a8_db' AND o.name IN (N'AI_ORG_MEMBER_CHANGE', N'AI_ORG_CHANGE_RECORD', N'ODS_MEMBER_CHANGE', N'ODS_ORG_CHANGE'))
    OR (DB_NAME() = N'ruoyi-vue-pro' AND o.name = N'psa_employee_transfer_record');
SELECT DB_NAME() AS database_name, OBJECT_NAME(d.referencing_id) AS view_name, d.referenced_database_name, d.referenced_schema_name, d.referenced_entity_name
  FROM sys.sql_expression_dependencies d
 WHERE DB_NAME() = N'ruoyi-vue-pro' AND d.referencing_id = OBJECT_ID(N'dbo.psa_employee_transfer_record');
