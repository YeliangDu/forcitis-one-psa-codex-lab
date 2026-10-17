-- FORCITIS one · C-3 · 迁移之后执行（版本表只追加，one_db_roles_schemas.sql §4b 的可执行版）。每次发布迁移后再执行一遍，幂等。
-- 用法：psql -v ON_ERROR_STOP=1 -h <RDS 内网地址> -U <高权限账号> -d forcitis_one_dev -f c3_dev_db_after_migrations.sql
-- 表要迁移后才存在，所以不能并入 c3_dev_db.sql。生产版 c9_prod_db_after_migrations.sql 由本文件经 render_prod_sql.sh 生成，不手改。
-- 版本表上的触发器已拒绝删除、清空和改写已关闭的行；运行角色再不持有 DELETE 与 TRUNCATE（只留 SELECT、INSERT、UPDATE，UPDATE 用来关闭开放版本）。
-- 新增版本表（数据访问注册表中角色为 DIMENSION_VERSIONED 的表）时，在这里与 one_db_roles_schemas.sql §4b 同时补一项。
DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['organization_workforce.org_unit_versions', 'organization_workforce.assignment_versions',
                             'organization_workforce.legal_entity_versions',
                             'organization_workforce.org_unit_legal_entities', 'organization_workforce.employee_legal_entities'] LOOP
        EXECUTE format('REVOKE DELETE, TRUNCATE ON %s FROM one_dev_app', t);
    END LOOP;
END $$;

-- 审计月分区预建（ADR-0048）：函数是 SECURITY DEFINER、属主 one_dev_owner，基线已对 PUBLIC 撤销 EXECUTE；运行角色经预建作业调用，缺此授权则 AuditPartitionJob 每天被拒、基线 6 个月分区用尽后审计写入失败。
-- 与 one_db_roles_schemas.sql §4b 同步；函数随基线迁移建出，故在迁移之后执行。
GRANT EXECUTE ON FUNCTION platform_audit.create_audit_entry_partitions(date, date) TO one_dev_app;
