-- B-5 全库只读 · 老系统库：psa_one_legacy_ro、one_legacy_names_ro 改为全库只读（依据同 a8_apply.sql）
-- 一个事务；原有视图授权保留（已被库级 SELECT 覆盖）。回滚见 legacy_rollback.sql
SET XACT_ABORT ON;
BEGIN TRANSACTION;
GRANT SELECT ON DATABASE::[ruoyi-vue-pro] TO psa_one_legacy_ro, one_legacy_names_ro;
DENY INSERT, UPDATE, DELETE, ALTER, EXECUTE, REFERENCES, CREATE TABLE, CREATE VIEW, CREATE PROCEDURE, CREATE FUNCTION, CREATE SCHEMA
  ON DATABASE::[ruoyi-vue-pro] TO psa_one_legacy_ro, one_legacy_names_ro;
COMMIT TRANSACTION;
GO
