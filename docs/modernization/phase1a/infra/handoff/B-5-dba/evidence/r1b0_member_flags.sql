-- R-1b-0 · A8 人员标志列取证（只出分组计数与列元数据，不取任何人员值）。以取证账号执行；在两库上下文各跑一次，人员部分只在 a8_db 出结果。
-- 范围同 SeeyonViewSql.MEMBER_SCOPE，根路径取 IDENTITY_ORG_RULES 的 A8_DEPT_ROOT_PATH = '00000001'（one.env 未配置）；
-- 登录名同 v_org_member：ORG_PRINCIPAL 按 LOGIN_NAME 排序取第一行。另列「任一登录行非空」以防第一行为空而其他行非空。
SELECT name AS database_name, is_read_committed_snapshot_on, snapshot_isolation_state_desc
  FROM sys.databases
 WHERE name IN (N'a8_db', N'ruoyi-vue-pro') AND DB_NAME() = N'a8_db'
 ORDER BY name;
GO
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, NUMERIC_PRECISION, IS_NULLABLE
  FROM a8_db.INFORMATION_SCHEMA.COLUMNS
 WHERE DB_NAME() = N'a8_db' AND TABLE_SCHEMA = N'dbo'
   AND ((TABLE_NAME = N'ORG_MEMBER' AND COLUMN_NAME IN (N'STATE', N'IS_DELETED', N'IS_ENABLE'))
     OR (TABLE_NAME = N'ORG_PRINCIPAL' AND COLUMN_NAME = N'LOGIN_NAME'))
 ORDER BY TABLE_NAME, COLUMN_NAME;
GO
SELECT STATE, IS_DELETED, IS_ENABLE, login_name_present, any_login_name_present, COUNT(*) AS members
  FROM (SELECT M.STATE, M.IS_DELETED, M.IS_ENABLE,
               CASE WHEN P.LOGIN_NAME IS NOT NULL AND P.LOGIN_NAME <> N'' THEN 1 ELSE 0 END AS login_name_present,
               CASE WHEN A.n > 0 THEN 1 ELSE 0 END AS any_login_name_present
          FROM a8_db.dbo.ORG_MEMBER M
          LEFT JOIN a8_db.dbo.ORG_UNIT U ON U.ID = M.ORG_DEPARTMENT_ID
         OUTER APPLY (SELECT TOP 1 PP.LOGIN_NAME FROM a8_db.dbo.ORG_PRINCIPAL PP WHERE PP.MEMBER_ID = M.ID ORDER BY PP.LOGIN_NAME) P
         OUTER APPLY (SELECT COUNT(*) AS n FROM a8_db.dbo.ORG_PRINCIPAL Q
                       WHERE Q.MEMBER_ID = M.ID AND Q.LOGIN_NAME IS NOT NULL AND Q.LOGIN_NAME <> N'') A
         WHERE DB_NAME() = N'a8_db'
           AND M.ID IS NOT NULL AND (M.IS_INTERNAL = 1 OR M.IS_INTERNAL IS NULL) AND M.CODE IS NOT NULL AND M.CODE <> N''
           AND M.ORG_DEPARTMENT_ID IS NOT NULL AND U.PATH LIKE N'00000001%') X
 GROUP BY STATE, IS_DELETED, IS_ENABLE, login_name_present, any_login_name_present
 ORDER BY STATE, IS_DELETED, IS_ENABLE, login_name_present, any_login_name_present;
GO
