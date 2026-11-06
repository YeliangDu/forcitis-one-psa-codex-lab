-- FORCITIS one · C-3 校验（只读；连 forcitis_one_dev 执行，结果截图或导出留档）
-- 用法：psql -v ON_ERROR_STOP=1 -h <RDS 内网地址> -U <高权限账号> -d forcitis_one_dev -f dev-db-verify.sql

-- V1 角色与属性：期望 6 行（含 history_loader，不可登录）；owner 不可登录；app / readonly / audit_maintainer 为 NOINHERIT；连接上限 migrator 2、app 25、readonly 3、audit_maintainer 1
SELECT rolname, rolcanlogin, rolinherit, rolconnlimit
  FROM pg_roles WHERE rolname LIKE 'one\_dev\_%' ORDER BY rolname;

-- V2 按角色设置：期望 migrator 含 search_path、role、lock_timeout；app 含 search_path、两个超时、work_mem；readonly 含只读与超时
SELECT r.rolname, s.setconfig
  FROM pg_db_role_setting s JOIN pg_roles r ON r.oid = s.setrole
 WHERE r.rolname LIKE 'one\_dev\_%' ORDER BY r.rolname;

-- V3 schema 属主：期望 13 行，属主均为 one_dev_owner
SELECT nspname, pg_get_userbyid(nspowner) AS owner
  FROM pg_namespace
 WHERE nspname IN ('one_migration', 'platform_identity', 'platform_approval', 'platform_integration', 'platform_audit',
                   'platform_numbering', 'platform_file', 'platform_scheduling', 'organization_workforce', 'organization_attendance', 'organization_leave', 'accounting_period', 'accounting_ledger', 'organization_payroll', 'organization_recruitment', 'organization_administration',
                   'platform_idempotency', 'cost_timesheet')
 ORDER BY nspname;

-- V4 运行角色无建对象权限：期望 0 行
SELECT n.nspname, r.rolname
  FROM pg_namespace n CROSS JOIN (VALUES ('one_dev_app'), ('one_dev_readonly'), ('one_dev_audit_maintainer')) r(rolname)
 WHERE n.nspname LIKE ANY (ARRAY['platform\_%', 'organization\_%', 'cost\_%', 'accounting\_%', 'one\_migration'])
   AND has_schema_privilege(r.rolname, n.oid, 'CREATE');

-- V5 默认权限：platform_audit 中 one_dev_app 只有 SELECT、INSERT（arwd 中只含 a、r）
SELECT n.nspname, d.defaclobjtype, d.defaclacl
  FROM pg_default_acl d JOIN pg_namespace n ON n.oid = d.defaclnamespace
 WHERE pg_get_userbyid(d.defaclrole) = 'one_dev_owner' ORDER BY n.nspname, d.defaclobjtype;

-- V6 库级权限：PUBLIC 无 CONNECT；owner 有 CREATE；四个登录角色有 CONNECT
SELECT datname, datacl FROM pg_database WHERE datname = 'forcitis_one_dev';

-- V6b 维护角色只有 platform_audit 的 USAGE 与那一个函数：以下四条均期望 0 行（最后一条是 TEMP 为 f）
SELECT nspname FROM pg_namespace
 WHERE nspname ~ '^(platform_|organization_|cost_|one_migration)' AND nspname <> 'platform_audit'
   AND has_schema_privilege('one_dev_audit_maintainer', oid, 'USAGE');
SELECT n.nspname, c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
 WHERE c.relkind IN ('r', 'p', 'v', 'm', 'S') AND n.nspname ~ '^(platform_|organization_|cost_|one_migration)'
   AND (has_table_privilege('one_dev_audit_maintainer', c.oid, 'SELECT, INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER')
        OR (c.relkind = 'S' AND has_sequence_privilege('one_dev_audit_maintainer', c.oid, 'USAGE, SELECT, UPDATE')));
SELECT roleid::regrole FROM pg_auth_members WHERE member = 'one_dev_audit_maintainer'::regrole;
SELECT has_database_privilege('one_dev_audit_maintainer', 'forcitis_one_dev', 'TEMP') AS can_temp;

-- V6c 运行账号与只读账号没有 TEMP（临时表不能顶替未限定名），运行账号 search_path 的第一项是 pg_catalog 且没有任何库级覆盖（ALTER ROLE … IN DATABASE … SET search_path 优先于全局设置）：期望两行均带标签且为 t
SELECT 'V6c_no_temp' AS tag, NOT (has_database_privilege('one_dev_app', 'forcitis_one_dev', 'TEMP') OR has_database_privilege('one_dev_readonly', 'forcitis_one_dev', 'TEMP')) AS ok;
SELECT 'V6c_search_path' AS tag,
       EXISTS (SELECT 1 FROM pg_db_role_setting s JOIN pg_roles r ON r.oid = s.setrole
                WHERE r.rolname = 'one_dev_app' AND s.setdatabase = 0 AND 'search_path=pg_catalog, platform_approval' = ANY (s.setconfig))
       AND NOT EXISTS (SELECT 1 FROM pg_db_role_setting s JOIN pg_roles r ON r.oid = s.setrole, unnest(s.setconfig) AS c
                WHERE r.rolname = 'one_dev_app' AND s.setdatabase <> 0 AND c LIKE 'search_path=%') AS ok;

-- V7 连接预算：本实例全部有上限角色之和，须小于 max_connections 减保留（superuser_reserved_connections 与 RDS 保留）
SELECT sum(rolconnlimit) AS limited_total FROM pg_roles WHERE rolconnlimit > 0;
SHOW max_connections;
SHOW superuser_reserved_connections;

-- V8 迁移后补查（首次 Flyway 与 Flowable 安装之后再跑）：全部表属主为 one_dev_owner；ACT_* 位于 platform_approval
SELECT schemaname, count(*) AS tables, count(*) FILTER (WHERE tableowner <> 'one_dev_owner') AS foreign_owner
  FROM pg_tables WHERE schemaname NOT IN ('pg_catalog', 'information_schema') GROUP BY schemaname ORDER BY schemaname;
SELECT schemaname, count(*) FROM pg_tables WHERE tablename LIKE 'act\_%' GROUP BY schemaname;

-- V9 审计分区预建函数仅运行角色可执行：迁移前函数不存在，两行均为 NULL；迁移与 after_migrations 之后期望 one_dev_app = t、one_dev_readonly = f
SELECT r.rolname, has_function_privilege(r.rolname, to_regprocedure('platform_audit.create_audit_entry_partitions(date,date)'), 'EXECUTE') AS can_execute
  FROM (VALUES ('one_dev_app'), ('one_dev_readonly')) r(rolname);
-- 维护角色只能删除已归档分区：期望 drop = t、create = f
SELECT has_function_privilege('one_dev_audit_maintainer', to_regprocedure('platform_audit.drop_archived_audit_entry_partition(date)'), 'EXECUTE') AS can_drop,
       has_function_privilege('one_dev_audit_maintainer', to_regprocedure('platform_audit.create_audit_entry_partitions(date,date)'), 'EXECUTE') AS can_create;

-- V10 运行角色不是属主角色的成员（SEC-1021-COO-01）：编号版本表的「无作者行只由迁移写」靠这一点——触发器只放行属主角色的成员，运行账号若是成员，就能绕过编号四眼。
-- 任一运行角色是成员则本条抛错，ON_ERROR_STOP 使校验失败；期望不抛错。
DO $$
DECLARE r text;
BEGIN
    FOREACH r IN ARRAY ARRAY['one_dev_app', 'one_dev_readonly', 'one_dev_audit_maintainer'] LOOP
        IF pg_has_role(r, 'one_dev_owner', 'MEMBER') THEN
            RAISE EXCEPTION 'V10 failed: runtime role % is a member of one_dev_owner', r;
        END IF;
    END LOOP;
END $$;

-- V11 每个自定义函数固定 search_path（FUNCTION_SEARCH_PATH_PINNING.md R-SP-1、R-SP-3）：pg_catalog 在前、pg_temp 在最后，中间至多本函数所在 schema（非 public，且除属主外无人可在该 schema 建对象）；SECURITY DEFINER 函数 PUBLIC 不可执行。
-- 与 DataArchitectureTest 的主守护同一套判定；期望 0 行（扩展成员不计），有行则本条抛错，ON_ERROR_STOP 使校验失败。
DO $$
DECLARE bad text;
BEGIN
    SELECT string_agg(p.oid::regprocedure::text, ', ') INTO bad
      FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
     WHERE n.nspname NOT IN ('pg_catalog', 'information_schema', 'pg_toast') AND p.prokind IN ('f', 'p')
       AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.classid = 'pg_proc'::regclass AND d.objid = p.oid AND d.deptype = 'e')
       AND (NOT EXISTS (SELECT 1 FROM unnest(p.proconfig) c
                         WHERE c = 'search_path=pg_catalog, pg_temp'
                            OR (c = 'search_path=pg_catalog, ' || n.nspname || ', pg_temp' AND n.nspname <> 'public'
                                AND NOT EXISTS (SELECT 1 FROM aclexplode(n.nspacl) a WHERE a.privilege_type = 'CREATE' AND a.grantee <> n.nspowner)))
            OR (p.prosecdef AND has_function_privilege('public', p.oid, 'EXECUTE')));
    IF bad IS NOT NULL THEN
        RAISE EXCEPTION 'V11 failed: functions without a pinned search_path or executable by PUBLIC: %', bad;
    END IF;
END $$;

-- V12 SECURITY DEFINER 函数的属主不是超级用户（R-SP-3）：定义者权限就是属主权限，超级用户属主等于函数体可做任何事。
-- 部署库上期望 0 行；测试容器的迁移账号是超级用户，所以这条不进 DataArchitectureTest。
DO $$
DECLARE bad text;
BEGIN
    SELECT string_agg(p.oid::regprocedure::text, ', ') INTO bad
      FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace JOIN pg_roles r ON r.oid = p.proowner
     WHERE n.nspname NOT IN ('pg_catalog', 'information_schema', 'pg_toast') AND p.prosecdef AND r.rolsuper;
    IF bad IS NOT NULL THEN
        RAISE EXCEPTION 'V12 failed: SECURITY DEFINER functions owned by a superuser: %', bad;
    END IF;
END $$;


-- V13 迁入写入角色（ADR-0036）：窗口外 NOLOGIN、非超级用户/CREATEROLE/CREATEDB；不是属主角色的成员；运行、只读、维护角色都不是属主或迁入角色的成员，迁入角色也不是它们（含迁移角色）的成员；
-- 迁入角色对两张版本表（含列级）无 UPDATE、无 DELETE/TRUNCATE，对库无 CREATE、TEMP，对所有 FORCITIS one schema 无 CREATE；它不属于任何角色，成员行只有执行账号那一行仅 ADMIN。任一不满足则抛错；期望不抛错。
-- 建库后、迁移之前跑时版本表与 schema 尚不存在，表与 schema 两项会跳过并给出 NOTICE：迁移与 after_migrations 之后必须再跑一次。
DO $$
DECLARE r text; t text; bad text; x pg_roles%ROWTYPE;
BEGIN
    SELECT * INTO x FROM pg_roles WHERE rolname = 'one_dev_history_loader';
    IF NOT FOUND THEN RAISE EXCEPTION 'V13 failed: role one_dev_history_loader missing'; END IF;
    IF x.rolcanlogin OR x.rolsuper OR x.rolcreaterole OR x.rolcreatedb OR x.rolbypassrls OR x.rolreplication THEN
        RAISE EXCEPTION 'V13 failed: one_dev_history_loader must be NOLOGIN without SUPERUSER/CREATEROLE/CREATEDB/BYPASSRLS/REPLICATION';
    END IF;
    IF pg_has_role('one_dev_history_loader', 'one_dev_owner', 'MEMBER') THEN
        RAISE EXCEPTION 'V13 failed: one_dev_history_loader is a member of one_dev_owner';
    END IF;
    FOREACH r IN ARRAY ARRAY['one_dev_app', 'one_dev_readonly', 'one_dev_audit_maintainer'] LOOP
        IF pg_has_role(r, 'one_dev_history_loader', 'MEMBER') OR pg_has_role(r, 'one_dev_owner', 'MEMBER') THEN
            RAISE EXCEPTION 'V13 failed: runtime role % is a member of one_dev_history_loader or one_dev_owner', r;
        END IF;
    END LOOP;
    -- 反向：迁入角色不属于任何角色（含运行类、迁移角色、RDS 高权限账号与内建 pg_* 角色），防止经 SET ROLE 或继承绕过
    SELECT string_agg(roleid::regrole::text, ', ') INTO bad FROM pg_auth_members WHERE member = 'one_dev_history_loader'::regrole;
    IF bad IS NOT NULL THEN
        RAISE EXCEPTION 'V13 failed: one_dev_history_loader is a member of %', bad;
    END IF;
    -- 它的成员行只允许建角色时自动产生的、仅 ADMIN 的那一行（inherit、set 均为假）；其余一律不许，免得被抢建或被人拉进别的账号
    SELECT string_agg(member::regrole::text, ', ') INTO bad FROM pg_auth_members
     WHERE roleid = 'one_dev_history_loader'::regrole AND (inherit_option OR set_option);
    IF bad IS NOT NULL THEN
        RAISE EXCEPTION 'V13 failed: one_dev_history_loader has members with INHERIT or SET: %', bad;
    END IF;
    IF has_database_privilege('one_dev_history_loader', 'forcitis_one_dev', 'CREATE, TEMP') THEN
        RAISE EXCEPTION 'V13 failed: one_dev_history_loader can CREATE or TEMP in database forcitis_one_dev';
    END IF;
    FOREACH t IN ARRAY ARRAY['platform_numbering.numbering_rule_versions', 'organization_attendance.work_calendar_versions'] LOOP
        IF to_regclass(t) IS NULL THEN
            RAISE NOTICE 'V13: % does not exist yet, table privilege check skipped', t;
        ELSIF has_table_privilege('one_dev_history_loader', t, 'UPDATE, DELETE, TRUNCATE') OR has_any_column_privilege('one_dev_history_loader', t, 'UPDATE') THEN
            RAISE EXCEPTION 'V13 failed: one_dev_history_loader can UPDATE/DELETE/TRUNCATE %', t;
        END IF;
    END LOOP;
    FOR t IN SELECT nspname FROM pg_namespace
              WHERE nspname LIKE 'platform\_%' OR nspname LIKE 'organization\_%' OR nspname LIKE 'cost\_%' OR nspname = 'one_migration' LOOP
        IF has_schema_privilege('one_dev_history_loader', t, 'CREATE') THEN
            RAISE EXCEPTION 'V13 failed: one_dev_history_loader can CREATE in schema %', t;
        END IF;
    END LOOP;
END $$;
-- V13b 迁入角色的全部成员行：除建角色时自动产生的执行账号那一行（PG 16：仅 ADMIN；RDS 上是 one_admin，admin t、inherit f、set f）外不应有任何其他行——
-- 触发器按 pg_has_role(..., 'MEMBER') 判定，只有 ADMIN 的成员行也算成员。容器里用超级用户建角色不产生该行，期望 0 行。
SELECT m.member::regrole AS member, m.admin_option, m.inherit_option, m.set_option
  FROM pg_auth_members m WHERE m.roleid = 'one_dev_history_loader'::regrole ORDER BY 1;
