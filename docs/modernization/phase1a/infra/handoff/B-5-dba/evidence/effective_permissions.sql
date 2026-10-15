-- ADR-0037 第 4 项 (a)(c) · 以被检账号登录：库级有效权限、所属角色、可访问的库（只出名称）
-- P1 库级有效权限
SELECT permission_name FROM fn_my_permissions(NULL, 'DATABASE') ORDER BY permission_name;
GO
-- P1b 服务器级有效权限
SELECT permission_name FROM fn_my_permissions(NULL, 'SERVER') ORDER BY permission_name;
GO
-- P2 角色（应只有 public）
SELECT name, type FROM sys.user_token WHERE name <> SUSER_SNAME() ORDER BY name;
GO
-- P3 有用户的库
SELECT name FROM sys.databases WHERE HAS_DBACCESS(name) = 1 ORDER BY name;
GO
