-- B-5 全库只读 · a8_db：one_a8_views_ro 改为全库只读（用户「新系统可以读任何旧系统数据」「两者都是」「修改权限」「全部可读，不排除」）
-- 一个事务；schema 级原有的写类 DENY 保留。回滚见 a8_rollback.sql
SET XACT_ABORT ON;
BEGIN TRANSACTION;
REVOKE SELECT ON SCHEMA::dbo FROM one_a8_views_ro;
REVOKE VIEW DEFINITION ON SCHEMA::dbo FROM one_a8_views_ro;
GRANT SELECT ON DATABASE::a8_db TO one_a8_views_ro;
DENY INSERT, UPDATE, DELETE, ALTER, EXECUTE, REFERENCES, CREATE TABLE, CREATE VIEW, CREATE PROCEDURE, CREATE FUNCTION, CREATE SCHEMA
  ON DATABASE::a8_db TO one_a8_views_ro;
COMMIT TRANSACTION;
GO
