-- 授权部分已被 ADR-0037 取代（账号全库只读，库级 DENY 写；应用按 ADR-0037 第 4 项校验账号）；视图冻结，正本保留可执行（回滚用）。
-- ============================================================================================================
-- 老系统部门简称只读视图（提议，待 DBA 评审；Phase 1a · organization.workforce 部门简称，经 platform.integration 读取）
-- 依据：REIMAGINED_ARCHITECTURE.md §3.3、SECURITY_INVARIANTS.md I-1、评审结论 M4（老短名改读专用视图，与老待办同一套「账号恰好只能读一个对象」校验）
--
-- 这是一次【老系统变更登记】：在老库（SQL Server）上新增 1 个视图和 1 个只读账号。不改任何老表、不写任何老数据。执行人：DBA / 运维。
--
-- 新系统怎么用它：
--   * 连接配置只走环境变量，不写进仓库：LEGACY_DB_RO_URL、LEGACY_DB_RO_USERNAME、LEGACY_DB_RO_PASSWORD（由 DBA 生成，放密钥服务）
--   * 配置键 forcitis.platform.integration.legacy.view-name（默认 dbo.v_one_legacy_org_short_name）
--   * 每天读一次全量（约 03:30），只读两列：部门 id 与简称。
--   * 新系统启动时核对：该账号能 SELECT 的对象必须【恰好只有本视图】，否则拒绝启动（I-1：令牌表等一律不授权）。
--
-- 只含所需列（最小暴露）：id、short_name。不含名称、层级、人员、令牌、手机号等。
-- ============================================================================================================

-- 1. 视图：部门 id 与简称（未删除的部门）
CREATE VIEW dbo.v_one_legacy_org_short_name
AS
SELECT d.id         AS id,
       d.short_name AS short_name
  FROM dbo.system_dept d
 WHERE d.deleted = '0';
GO

-- 2. 只读账号：只授予视图的 SELECT；不加入任何数据库角色（db_datareader 等一律不加，否则等于授权全库）
CREATE LOGIN one_legacy_names_ro WITH PASSWORD = N'<<由 DBA 生成并保存到密钥服务；不要写进任何文件>>', CHECK_POLICY = ON;
CREATE USER  one_legacy_names_ro FOR LOGIN one_legacy_names_ro WITH DEFAULT_SCHEMA = dbo;
GRANT SELECT ON dbo.v_one_legacy_org_short_name TO one_legacy_names_ro;
-- 视图与基表同属 dbo（所有权链），账号不需要、也不得获得基表的任何权限。
GO

-- 3. 验证（DBA 执行并留存结果；新系统每次启动也会做等价检查）
--    3.1 账号能 SELECT 的用户对象必须只有本视图（排除 sys / INFORMATION_SCHEMA 系统视图）：
EXECUTE AS USER = 'one_legacy_names_ro';
SELECT TABLE_SCHEMA, TABLE_NAME
  FROM INFORMATION_SCHEMA.TABLES
 WHERE TABLE_SCHEMA NOT IN ('sys', 'INFORMATION_SCHEMA')
   AND HAS_PERMS_BY_NAME(QUOTENAME(TABLE_SCHEMA) + '.' + QUOTENAME(TABLE_NAME), 'OBJECT', 'SELECT') = 1;
--    期望：恰好 1 行 dbo.v_one_legacy_org_short_name
REVERT;
--    3.2 账号不是任何数据库角色的成员（期望：0 行，除 public）：
SELECT r.name AS role_name
  FROM sys.database_role_members m
  JOIN sys.database_principals r ON r.principal_id = m.role_principal_id
  JOIN sys.database_principals u ON u.principal_id = m.member_principal_id
 WHERE u.name = 'one_legacy_names_ro';
GO
