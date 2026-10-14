-- FORCITIS one · C-3 校验（只读；连 forcitis_one_dev 执行，结果截图或导出留档）
-- 用法：psql -v ON_ERROR_STOP=1 -h <RDS 内网地址> -U <高权限账号> -d forcitis_one_dev -f c3_dev_db_verify.sql

-- V1 角色与属性：期望 4 行；owner 不可登录；app / readonly 为 NOINHERIT；连接上限 2 / 40 / 3
SELECT rolname, rolcanlogin, rolinherit, rolconnlimit
  FROM pg_roles WHERE rolname LIKE 'one\_dev\_%' ORDER BY rolname;

-- V2 按角色设置：期望 migrator 含 search_path、role、lock_timeout；app 含 search_path、两个超时、work_mem；readonly 含只读与超时
SELECT r.rolname, s.setconfig
  FROM pg_db_role_setting s JOIN pg_roles r ON r.oid = s.setrole
 WHERE r.rolname LIKE 'one\_dev\_%' ORDER BY r.rolname;

-- V3 schema 属主：期望 11 行，属主均为 one_dev_owner
SELECT nspname, pg_get_userbyid(nspowner) AS owner
  FROM pg_namespace
 WHERE nspname IN ('one_migration', 'platform_identity', 'platform_approval', 'platform_integration', 'platform_audit',
                   'platform_numbering', 'platform_scheduling', 'organization_workforce', 'organization_attendance',
                   'platform_idempotency', 'cost_timesheet')
 ORDER BY nspname;

-- V4 运行角色无建对象权限：期望 0 行
SELECT n.nspname, r.rolname
  FROM pg_namespace n CROSS JOIN (VALUES ('one_dev_app'), ('one_dev_readonly')) r(rolname)
 WHERE n.nspname LIKE ANY (ARRAY['platform\_%', 'organization\_%', 'cost\_%', 'one\_migration'])
   AND has_schema_privilege(r.rolname, n.oid, 'CREATE');

-- V5 默认权限：platform_audit 中 one_dev_app 只有 SELECT、INSERT（arwd 中只含 a、r）
SELECT n.nspname, d.defaclobjtype, d.defaclacl
  FROM pg_default_acl d JOIN pg_namespace n ON n.oid = d.defaclnamespace
 WHERE pg_get_userbyid(d.defaclrole) = 'one_dev_owner' ORDER BY n.nspname, d.defaclobjtype;

-- V6 库级权限：PUBLIC 无 CONNECT；owner 有 CREATE；三个登录角色有 CONNECT
SELECT datname, datacl FROM pg_database WHERE datname = 'forcitis_one_dev';

-- V7 连接预算：本实例全部有上限角色之和，须小于 max_connections 减保留（superuser_reserved_connections 与 RDS 保留）
SELECT sum(rolconnlimit) AS limited_total FROM pg_roles WHERE rolconnlimit > 0;
SHOW max_connections;
SHOW superuser_reserved_connections;

-- V8 迁移后补查（首次 Flyway 与 Flowable 安装之后再跑）：全部表属主为 one_dev_owner；ACT_* 位于 platform_approval
SELECT schemaname, count(*) AS tables, count(*) FILTER (WHERE tableowner <> 'one_dev_owner') AS foreign_owner
  FROM pg_tables WHERE schemaname NOT IN ('pg_catalog', 'information_schema') GROUP BY schemaname ORDER BY schemaname;
SELECT schemaname, count(*) FROM pg_tables WHERE tablename LIKE 'act\_%' GROUP BY schemaname;
