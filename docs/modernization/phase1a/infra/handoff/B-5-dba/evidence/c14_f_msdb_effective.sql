-- C-14 (f) H-1′ 实测：在 msdb 上下文以各账号自己的令牌取有效权限、令牌授权、拥有物（ADR-0037 第 7 项；只读，只出类别与计数，不出名称）。
-- 连接直接指向 msdb（等价于实现里的 EXEC msdb.sys.sp_executesql）；sys.user_token 即该账号在 msdb 的令牌。
-- 1 六项有效权限加 IMPERSONATE（与 MSDB_EFFECTIVE_PERMISSIONS 相同）
SELECT v.p AS permission, HAS_PERMS_BY_NAME(N'msdb', N'DATABASE', v.p) AS effective
  FROM (VALUES (N'CONTROL'), (N'ALTER'), (N'ALTER ANY ROLE'), (N'ALTER ANY USER'), (N'ALTER ANY SCHEMA'), (N'CREATE PROCEDURE')) v (p)
UNION ALL SELECT N'IMPERSONATE (dbo user)', HAS_PERMS_BY_NAME(N'dbo', N'USER', N'IMPERSONATE');
GO
-- 2 fn_my_permissions(NULL, 'DATABASE') 的权限全集：权限名与计数
SELECT permission_name, COUNT(*) AS n FROM fn_my_permissions(NULL, N'DATABASE') GROUP BY permission_name ORDER BY permission_name;
GO
-- 3 令牌内各主体的授权中，不属于「对象级 GRANT SELECT / EXECUTE」的行：授予者类别、class、state、permission 与计数
SELECT CASE WHEN p.grantee_principal_id = 0 THEN N'public' ELSE N'other token principal' END AS grantee, p.class_desc, p.state, p.permission_name, COUNT(*) AS n
  FROM sys.database_permissions p
 WHERE p.grantee_principal_id IN (SELECT principal_id FROM sys.user_token)
   AND NOT (p.class_desc = N'OBJECT_OR_COLUMN' AND p.state = 'G' AND p.permission_name IN (N'SELECT', N'EXECUTE'))
 GROUP BY CASE WHEN p.grantee_principal_id = 0 THEN N'public' ELSE N'other token principal' END, p.class_desc, p.state, p.permission_name
 ORDER BY 1, 2, 4;
GO
-- 4 令牌内对象级 EXECUTE GRANT 对应的对象：类型、是否带 EXECUTE AS、数量
SELECT o.type_desc, CASE WHEN m.execute_as_principal_id IS NULL THEN N'no EXECUTE AS' ELSE N'EXECUTE AS' END AS execute_as, COUNT(*) AS n
  FROM sys.database_permissions p
  JOIN sys.all_objects o ON o.object_id = p.major_id
  LEFT JOIN sys.all_sql_modules m ON m.object_id = o.object_id
 WHERE p.class = 1 AND p.permission_name = N'EXECUTE' AND p.state = 'G' AND p.grantee_principal_id IN (SELECT principal_id FROM sys.user_token)
 GROUP BY o.type_desc, CASE WHEN m.execute_as_principal_id IS NULL THEN N'no EXECUTE AS' ELSE N'EXECUTE AS' END;
GO
-- 5 dbo schema 上的有效权限（IMPERSONATE 已在第 1 项）
SELECT permission_name, COUNT(*) AS n FROM fn_my_permissions(N'dbo', N'SCHEMA') GROUP BY permission_name ORDER BY permission_name;
GO
-- 6 令牌里 type = 'ROLE' 的条目：已知四种角色按名称，其余只报「有、几个」
SELECT CASE WHEN name IN (N'public', N'SQLAgentOperatorRole', N'SQLAgentReaderRole', N'SQLAgentUserRole') THEN name ELSE N'(other role)' END AS role_class, COUNT(*) AS n
  FROM sys.user_token WHERE type = 'ROLE'
 GROUP BY CASE WHEN name IN (N'public', N'SQLAgentOperatorRole', N'SQLAgentReaderRole', N'SQLAgentUserRole') THEN name ELSE N'(other role)' END
 ORDER BY 1;
GO
-- 7 令牌内各主体拥有的 schema、对象、角色：拥有者类别 → 被拥有物类别：计数（拥有者按名称只出已知五种、本人用户，其余记 other）
SELECT owner_class, owned_kind, SUM(n) AS n FROM (
    SELECT CASE WHEN o.sid = SUSER_SID() THEN N'(own user)' WHEN o.name IN (N'public', N'guest', N'SQLAgentOperatorRole', N'SQLAgentReaderRole', N'SQLAgentUserRole') THEN o.name ELSE N'(other)' END AS owner_class,
           N'schema' AS owned_kind, 1 AS n
      FROM sys.schemas s JOIN sys.database_principals o ON o.principal_id = s.principal_id WHERE s.principal_id IN (SELECT principal_id FROM sys.user_token)
    UNION ALL
    SELECT CASE WHEN o.sid = SUSER_SID() THEN N'(own user)' WHEN o.name IN (N'public', N'guest', N'SQLAgentOperatorRole', N'SQLAgentReaderRole', N'SQLAgentUserRole') THEN o.name ELSE N'(other)' END,
           N'object', 1 FROM sys.objects x JOIN sys.database_principals o ON o.principal_id = x.principal_id WHERE x.principal_id IN (SELECT principal_id FROM sys.user_token)
    UNION ALL
    SELECT CASE WHEN o.sid = SUSER_SID() THEN N'(own user)' WHEN o.name IN (N'public', N'guest', N'SQLAgentOperatorRole', N'SQLAgentReaderRole', N'SQLAgentUserRole') THEN o.name ELSE N'(other)' END,
           N'role', 1 FROM sys.database_principals r JOIN sys.database_principals o ON o.principal_id = r.owning_principal_id WHERE r.owning_principal_id IN (SELECT principal_id FROM sys.user_token)
) t GROUP BY owner_class, owned_kind ORDER BY owner_class, owned_kind;
GO
