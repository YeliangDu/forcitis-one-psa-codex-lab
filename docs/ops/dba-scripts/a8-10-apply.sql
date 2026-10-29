-- FORCITIS one · B-5 · A8 库（a8_db）只读视图与专用账号 —— 执行脚本
-- 执行人：A8 侧 DBA。工具：sqlcmd，或 SSMS 打开「SQLCMD 模式」。不改任何 A8 基表，不写任何 A8 数据。
-- 用法：sqlcmd -S <A8 SQL Server> -d a8_db -E -b -i a8_10_apply.sql [-v READER_LOGIN=<登录名>]
-- 视图正文与 docs/data/legacy-views/seeyon-views.sql 的 schema、views 两段逐字一致（check_view_sync.py 校验）；列与
-- backend/architecture/seeyon-view-columns.yaml 逐列对应（应用启动时要求完全相等，多一列、少一列都拒绝启动）。
-- 口令：STEP 3 在服务器上随机生成，只显示一次；本文件与仓库不含任何口令。
:setvar READER_LOGIN one_a8_views_ro
:on error exit

-- ===== STEP 0 预检（只读）=====
-- 0.1 期望 0 行，有行即停止并回报
SELECT name FROM sys.schemas WHERE name = N'forcitis_one';
SELECT name FROM sys.server_principals WHERE name = N'$(READER_LOGIN)';
-- 0.2 关系类型 ID（部门负责人、区域 AR）在本实例存在：期望 2 行；不足则停止，回报本实例的实际 ID
SELECT DISTINCT OBJECTIVE1_ID FROM dbo.ORG_RELATIONSHIP WHERE OBJECTIVE1_ID IN (-1726439953579618944, -8187907605834545337);
-- 0.3「DBA 确认」项：formmain_0307 的主键列应为 ID
SELECT c.name
  FROM sys.indexes i
  JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
  JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
 WHERE i.is_primary_key = 1 AND i.object_id = OBJECT_ID(N'dbo.formmain_0307');
GO

-- ===== STEP 1–2 schema 与视图（以下至 STEP 3 与 SEEYON_VIEWS.sql 逐字一致）=====
-- ==== BLOCK: schema ====
CREATE SCHEMA forcitis_one AUTHORIZATION dbo;
GO

-- ==== BLOCK: views ====

/* 组织单元。总部/区域类别取自部门扩展属性 33 对应的枚举显示值；交付部门标识为扩展属性 32 的原始枚举 ID
 * （应用按配置的「是」枚举 ID 判断）。同一部门有多行扩展属性时取确定的一个（MAX），不使部门行翻倍。 */
CREATE VIEW forcitis_one.v_org_unit AS
SELECT U.ID,
       U.CODE,
       U.NAME,
       U.SORT_ID,
       U.PATH,
       U.IS_DELETED,
       U.IS_INTERNAL,
       U.IS_ENABLE,
       (SELECT MAX(EI.SHOWVALUE)
          FROM dbo.ADDRESSBOOK AB
          JOIN dbo.CTP_ENUM_ITEM EI ON EI.ID = AB.EXT_ATTR_33 AND EI.STATE = 1
           AND EI.REF_ENUMID = (SELECT E.ID FROM dbo.CTP_ENUM E WHERE E.ENUMNAME = N'总部/区域')
         WHERE AB.MEMBER_ID = U.ID) AS CATEGORY,
       (SELECT MAX(CAST(AB.EXT_ATTR_32 AS NVARCHAR(32)))
          FROM dbo.ADDRESSBOOK AB
         WHERE AB.MEMBER_ID = U.ID AND AB.TYPE = 'Department') AS DELIVERY_FLAG_ENUM_ID
  FROM dbo.ORG_UNIT U;
GO

/* 人员与部门的关系，只暴露两种类型：部门负责人、部门区域 AR（类型 ID 见上方约定，DBA 确认）。 */
CREATE VIEW forcitis_one.v_org_relationship AS
SELECT R.SOURCE_ID    AS SOURCE_MEMBER_ID,
       R.OBJECTIVE0_ID AS ORG_UNIT_ID,
       R.OBJECTIVE1_ID AS RELATIONSHIP_TYPE_ID,
       M.IS_DELETED    AS MEMBER_IS_DELETED
  FROM dbo.ORG_RELATIONSHIP R
  JOIN dbo.ORG_MEMBER M ON M.ID = R.SOURCE_ID
 WHERE R.OBJECTIVE1_ID IN (-1726439953579618944, -8187907605834545337);
GO

/* 岗位。含已停用、已删除的行，以便应用同步状态与删除。 */
CREATE VIEW forcitis_one.v_org_post AS
SELECT P.ID, P.CODE, P.NAME, P.SORT_ID, P.STATUS, P.IS_DELETED
  FROM dbo.ORG_POST P;
GO

/* 人员。一人一行：登录名取 ORG_PRINCIPAL 的确定一条；人事表按工号关联，取最新一条（DBA 确认 formmain_0307.ID 为主键）。
 * 不含生日、银行卡等敏感信息。 */
CREATE VIEW forcitis_one.v_org_member AS
SELECT M.ID,
       M.NAME,
       M.CODE,
       M.ORG_DEPARTMENT_ID                  AS ORG_UNIT_ID,
       U.PATH                               AS ORG_UNIT_PATH,
       M.ORG_POST_ID                        AS POST_ID,
       TRY_CAST(M.EXT_ATTR_37 AS BIGINT)    AS SUPERVISOR_MEMBER_ID,
       M.we_com_id                          AS WE_COM_ID,
       M.IS_INTERNAL,
       P.LOGIN_NAME,
       F.field0234                          AS ENTERPRISE_EMAIL,
       F.field0284                          AS GENDER,
       CAST(F.FIELD0244 AS NVARCHAR(32))    AS FEE_EARNER_ENUM_ID
  FROM dbo.ORG_MEMBER M
  LEFT JOIN dbo.ORG_UNIT U ON U.ID = M.ORG_DEPARTMENT_ID
 OUTER APPLY (SELECT TOP 1 PP.LOGIN_NAME
                FROM dbo.ORG_PRINCIPAL PP
               WHERE PP.MEMBER_ID = M.ID
               ORDER BY PP.LOGIN_NAME) P
 OUTER APPLY (SELECT TOP 1 FM.field0234, FM.field0284, FM.FIELD0244
                FROM dbo.formmain_0307 FM
               WHERE FM.field0217 = M.CODE
               ORDER BY FM.ID DESC) F;
GO

-- ===== STEP 3 登录名（随机口令，只显示一次）=====
-- 实例不允许 T-SQL 建登录时（托管实例），改在控制台建同名登录、≥ 32 位随机口令，跳过本步。
-- CHECK_EXPIRATION 关闭：服务账号口令到期会让组织同步中断；按 90 天由 DBA 计划轮换（runbooks/C-8）。
DECLARE @login sysname = N'$(READER_LOGIN)';
DECLARE @secret nvarchar(64) = N'Aa-' + CONVERT(nvarchar(64), CRYPT_GEN_RANDOM(24), 2);
DECLARE @ddl nvarchar(max) = N'CREATE LOGIN ' + QUOTENAME(@login) + N' WITH PASSWORD = ' + QUOTENAME(@secret, N'''')
    + N', CHECK_POLICY = ON, CHECK_EXPIRATION = OFF, DEFAULT_DATABASE = [a8_db]';
EXEC (@ddl);
SELECT @login AS login_name, @secret AS one_time_secret,
       N'只显示这一次：经约定的安全渠道交运维写入 /etc/forcitis-one/one.env（A8_DB_PASSWORD），然后关闭结果窗口；不截图、不发聊天、不进工单' AS handling;
GO

-- ===== STEP 4 库内用户与最小授权 =====
-- 只授予视图所在 schema 的 SELECT；不加入任何数据库角色（db_datareader 等一律不加）
CREATE USER [$(READER_LOGIN)] FOR LOGIN [$(READER_LOGIN)] WITH DEFAULT_SCHEMA = forcitis_one;
GO
GRANT SELECT ON SCHEMA::forcitis_one TO [$(READER_LOGIN)];
GO
-- 基表全部拒绝；视图经所有权链（同属 dbo）仍可读
DENY SELECT, INSERT, UPDATE, DELETE, EXECUTE, ALTER, REFERENCES, VIEW DEFINITION ON SCHEMA::dbo TO [$(READER_LOGIN)];
GO
DENY INSERT, UPDATE, DELETE, EXECUTE, ALTER, REFERENCES ON SCHEMA::forcitis_one TO [$(READER_LOGIN)];
GO

-- 下一步：a8_20_verify.sql
