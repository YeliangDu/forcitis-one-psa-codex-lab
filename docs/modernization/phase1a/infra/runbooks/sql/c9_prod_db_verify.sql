-- FORCITIS one · C-9 校验（只读；连 forcitis_one_prod 执行，结果截图或导出留档）
-- 用法：psql -v ON_ERROR_STOP=1 -h <RDS 内网地址> -U <高权限账号> -d forcitis_one_prod -f c9_prod_db_verify.sql

-- V1 角色与属性：期望 5 行；owner 不可登录；app / readonly / audit_maintainer 为 NOINHERIT；连接上限 migrator 2、app 25、readonly 3、audit_maintainer 1
SELECT rolname, rolcanlogin, rolinherit, rolconnlimit
  FROM pg_roles WHERE rolname LIKE 'one\_prod\_%' ORDER BY rolname;

-- V2 按角色设置：期望 migrator 含 search_path、role、lock_timeout；app 含 search_path、两个超时、work_mem；readonly 含只读与超时
SELECT r.rolname, s.setconfig
  FROM pg_db_role_setting s JOIN pg_roles r ON r.oid = s.setrole
 WHERE r.rolname LIKE 'one\_prod\_%' ORDER BY r.rolname;

-- V3 schema 属主：期望 13 行，属主均为 one_prod_owner
SELECT nspname, pg_get_userbyid(nspowner) AS owner
  FROM pg_namespace
 WHERE nspname IN ('one_migration', 'platform_identity', 'platform_approval', 'platform_integration', 'platform_audit',
                   'platform_numbering', 'platform_file', 'platform_scheduling', 'organization_workforce', 'organization_attendance', 'organization_administration',
                   'platform_idempotency', 'cost_timesheet')
 ORDER BY nspname;

-- V4 运行角色无建对象权限：期望 0 行
SELECT n.nspname, r.rolname
  FROM pg_namespace n CROSS JOIN (VALUES ('one_prod_app'), ('one_prod_readonly'), ('one_prod_audit_maintainer')) r(rolname)
 WHERE n.nspname LIKE ANY (ARRAY['platform\_%', 'organization\_%', 'cost\_%', 'one\_migration'])
   AND has_schema_privilege(r.rolname, n.oid, 'CREATE');

-- V5 默认权限：platform_audit 中 one_prod_app 只有 SELECT、INSERT（arwd 中只含 a、r）
SELECT n.nspname, d.defaclobjtype, d.defaclacl
  FROM pg_default_acl d JOIN pg_namespace n ON n.oid = d.defaclnamespace
 WHERE pg_get_userbyid(d.defaclrole) = 'one_prod_owner' ORDER BY n.nspname, d.defaclobjtype;

-- V6 库级权限：PUBLIC 无 CONNECT；owner 有 CREATE；四个登录角色有 CONNECT
SELECT datname, datacl FROM pg_database WHERE datname = 'forcitis_one_prod';

-- V6b 维护角色只有 platform_audit 的 USAGE 与那一个函数：以下四条均期望 0 行（最后一条是 TEMP 为 f）
SELECT nspname FROM pg_namespace
 WHERE nspname ~ '^(platform_|organization_|cost_|one_migration)' AND nspname <> 'platform_audit'
   AND has_schema_privilege('one_prod_audit_maintainer', oid, 'USAGE');
SELECT n.nspname, c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
 WHERE c.relkind IN ('r', 'p', 'v', 'm', 'S') AND n.nspname ~ '^(platform_|organization_|cost_|one_migration)'
   AND (has_table_privilege('one_prod_audit_maintainer', c.oid, 'SELECT, INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER')
        OR (c.relkind = 'S' AND has_sequence_privilege('one_prod_audit_maintainer', c.oid, 'USAGE, SELECT, UPDATE')));
SELECT roleid::regrole FROM pg_auth_members WHERE member = 'one_prod_audit_maintainer'::regrole;
SELECT has_database_privilege('one_prod_audit_maintainer', 'forcitis_one_prod', 'TEMP') AS can_temp;

-- V7 连接预算：本实例全部有上限角色之和，须小于 max_connections 减保留（superuser_reserved_connections 与 RDS 保留）
SELECT sum(rolconnlimit) AS limited_total FROM pg_roles WHERE rolconnlimit > 0;
SHOW max_connections;
SHOW superuser_reserved_connections;

-- V8 迁移后补查（首次 Flyway 与 Flowable 安装之后再跑）：全部表属主为 one_prod_owner；ACT_* 位于 platform_approval
SELECT schemaname, count(*) AS tables, count(*) FILTER (WHERE tableowner <> 'one_prod_owner') AS foreign_owner
  FROM pg_tables WHERE schemaname NOT IN ('pg_catalog', 'information_schema') GROUP BY schemaname ORDER BY schemaname;
SELECT schemaname, count(*) FROM pg_tables WHERE tablename LIKE 'act\_%' GROUP BY schemaname;

-- V9 审计分区预建函数仅运行角色可执行：迁移前函数不存在，两行均为 NULL；迁移与 after_migrations 之后期望 one_prod_app = t、one_prod_readonly = f
SELECT r.rolname, has_function_privilege(r.rolname, to_regprocedure('platform_audit.create_audit_entry_partitions(date,date)'), 'EXECUTE') AS can_execute
  FROM (VALUES ('one_prod_app'), ('one_prod_readonly')) r(rolname);
-- 维护角色只能删除已归档分区：期望 drop = t、create = f
SELECT has_function_privilege('one_prod_audit_maintainer', to_regprocedure('platform_audit.drop_archived_audit_entry_partition(date)'), 'EXECUTE') AS can_drop,
       has_function_privilege('one_prod_audit_maintainer', to_regprocedure('platform_audit.create_audit_entry_partitions(date,date)'), 'EXECUTE') AS can_create;

-- V10 运行角色不是属主角色的成员（SEC-1021-COO-01）：编号版本表的「无作者行只由迁移写」靠这一点——触发器只放行属主角色的成员，运行账号若是成员，就能绕过编号四眼。
-- 任一运行角色是成员则本条抛错，ON_ERROR_STOP 使校验失败；期望不抛错。
DO $$
DECLARE r text;
BEGIN
    FOREACH r IN ARRAY ARRAY['one_prod_app', 'one_prod_readonly', 'one_prod_audit_maintainer'] LOOP
        IF pg_has_role(r, 'one_prod_owner', 'MEMBER') THEN
            RAISE EXCEPTION 'V10 failed: runtime role % is a member of one_prod_owner', r;
        END IF;
    END LOOP;
END $$;
