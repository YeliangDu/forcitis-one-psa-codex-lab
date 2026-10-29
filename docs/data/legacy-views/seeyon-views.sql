-- 授权部分已被 ADR-0037 取代（账号全库只读，库级 DENY 写；应用按 ADR-0037 第 4 项校验账号）；视图冻结，正本保留可执行（回滚用）。
/*
 * SEEYON_VIEWS.sql  -  给 DBA 的提议稿（尚未执行）：A8 只读视图 DDL 与授权
 *
 * 依据：REIMAGINED_ARCHITECTURE §3.3「§9a 的机器守护」；backend/architecture/seeyon-view-columns.yaml（列白名单，逐列对应）。
 * 目的：FORCITIS one 只能读到组织 / 身份数据。合同、产值 / 收入确认、成本、费用、开票、回款、分包、付款等权责事实，
 *       生日、银行卡等敏感信息，一律不在视图中，也读不到基表。
 *
 * 约定：
 *   - 在 A8 数据库 a8_db 内执行，由 dbo 所有（AUTHORIZATION dbo），与基表同属主，使用所有权链：
 *     账号对视图有 SELECT 即可读取，对基表不需要（也被 DENY）权限。
 *   - 账号只有视图 SELECT：无 INSERT / UPDATE / DELETE / EXEC / ALTER，也不是 db_datareader 成员。
 *   - 本文件不含任何真实账号或口令。登录名与口令由 DBA 带外创建并存入密钥管理，以环境变量
 *     A8_DB_URL / A8_DB_USERNAME / A8_DB_PASSWORD 提供给应用。
 *   - 占位符：{{READER_LOGIN}} 登录名；{{READER_USER}} 库内用户名（建议同名）。
 *   - 分为四段，以「-- ==== BLOCK: <名称> ====」标记，以单独一行 GO 分批。集成测试（Maven profile legacy-it）
 *     在 SQL Server 容器里执行 views 与 account 两段，并断言：视图可读、基表读取被拒、写入被拒。
 *   - 关系类型 ID（部门负责人、区域 AR）随 A8 实例走。换库时同时修改本文件、应用配置
 *     forcitis.organization.workforce.a8.*，应用启动时校验这些 ID 在数据中存在。
 *   - 标注「DBA 确认」的列名 / 排序列，请对照生产库表结构核对。
 */

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

-- ==== BLOCK: account ====
-- 登录名由 DBA 先行创建（口令不入库、不入本文件）：
--   CREATE LOGIN [{{READER_LOGIN}}] WITH PASSWORD = N'<DBA 带外生成，存入密钥管理>', CHECK_POLICY = ON, CHECK_EXPIRATION = ON;
CREATE USER [{{READER_USER}}] FOR LOGIN [{{READER_LOGIN}}] WITH DEFAULT_SCHEMA = forcitis_one;
GO

-- 只授予视图所在 schema 的 SELECT（不是库级 SELECT，也不加入 db_datareader）。
GRANT SELECT ON SCHEMA::forcitis_one TO [{{READER_USER}}];
GO

-- 对基表（dbo）显式 DENY：读、写、执行、改结构、看定义，全部拒绝。视图经所有权链仍可读取。
DENY SELECT, INSERT, UPDATE, DELETE, EXECUTE, ALTER, REFERENCES, VIEW DEFINITION ON SCHEMA::dbo TO [{{READER_USER}}];
GO

-- 视图 schema 内只允许读：写、执行、改结构同样 DENY。
DENY INSERT, UPDATE, DELETE, EXECUTE, ALTER, REFERENCES ON SCHEMA::forcitis_one TO [{{READER_USER}}];
GO

/*
 * ==== BLOCK: legacy-short-names（另一个数据库：老系统库 ruoyi-vue-pro，仅供参考，不在 a8_db 执行）====
 * 每天刷新部门简称，只需 system_dept 的 id、short_name 两列。新系统不读任何令牌表（SECURITY_INVARIANTS I-1），
 * 账号对令牌表不授权，并显式 DENY：
 *
 *   CREATE USER [{{LEGACY_READER_USER}}] FOR LOGIN [{{LEGACY_READER_LOGIN}}];
 *   GRANT SELECT ON dbo.system_dept (id, short_name) TO [{{LEGACY_READER_USER}}];
 *   DENY  SELECT ON dbo.system_oauth2_access_token  TO [{{LEGACY_READER_USER}}];
 *   DENY  SELECT ON dbo.system_oauth2_refresh_token TO [{{LEGACY_READER_USER}}];
 *   DENY  INSERT, UPDATE, DELETE, EXECUTE ON SCHEMA::dbo TO [{{LEGACY_READER_USER}}];
 *
 * 老库「待办只读视图」属 platform.approval 的另一份登记，不在本文件。
 */
