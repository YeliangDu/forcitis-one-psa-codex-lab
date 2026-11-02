-- FORCITIS one · C-3 · 迁移之后执行（版本表只追加，one_db_roles_schemas.sql §4b 的可执行版）。每次发布迁移后再执行一遍，幂等。
-- 用法：psql -v ON_ERROR_STOP=1 -h <RDS 内网地址> -U <高权限账号> -d forcitis_one_dev -f dev-db-after-migrations.sql
-- 表要迁移后才存在，所以不能并入 dev-db.sql。生产版 prod-db-after-migrations.sql 由本文件经 render-prod-sql.sh 生成，不手改。
-- 版本表上的触发器已拒绝删除、清空和改写已关闭的行；运行角色再不持有 DELETE 与 TRUNCATE（只留 SELECT、INSERT、UPDATE，UPDATE 用来关闭开放版本）。
-- 新增版本表（数据访问注册表中角色为 DIMENSION_VERSIONED 的表）时，在这里与 one_db_roles_schemas.sql §4b 同时补一项。
-- 以属主身份执行：高权限账号对属主是 INHERIT FALSE、SET TRUE，不继承属主的权限，直接 GRANT 函数会被拒
SET ROLE one_dev_owner;
DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['organization_workforce.org_unit_versions', 'organization_workforce.assignment_versions',
                             'organization_workforce.legal_entity_versions', 'organization_workforce.employee_versions',
                             'organization_workforce.org_unit_legal_entities', 'organization_workforce.employee_legal_entities',
                             'platform_identity.role_versions', 'platform_identity.account_role_grants'] LOOP
        EXECUTE format('REVOKE DELETE, TRUNCATE ON %s FROM one_dev_app', t);
    END LOOP;
END $$;

-- 单据类型登记（ADR-0066 决定 4）：行只由迁移写，运行角色只读；与 one_db_roles_schemas.sql §4b 同步。
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON platform_numbering.document_types FROM one_dev_app;

-- 人事命令只追加（ADR-0068 第 1 条 7）：触发器之外，运行角色也不持有 DELETE 与 TRUNCATE；离职满 10 年的清除经属主的 SECURITY DEFINER 函数。
REVOKE DELETE, TRUNCATE ON organization_workforce.personnel_commands FROM one_dev_app;
REVOKE DELETE, TRUNCATE ON organization_workforce.org_structure_commands FROM one_dev_app;
REVOKE DELETE, TRUNCATE ON organization_workforce.attribution_countersigns FROM one_dev_app;
REVOKE UPDATE, DELETE, TRUNCATE ON organization_workforce.attribution_countersign_bases FROM one_dev_app;
-- 待放行离职与人事状态转换（IMP-1030-PAR-B1）：待放行离职同人事命令，状态转换只追加，运行角色只留 SELECT、INSERT。
REVOKE DELETE, TRUNCATE ON organization_workforce.separation_holds FROM one_dev_app;
REVOKE UPDATE, DELETE, TRUNCATE ON organization_workforce.state_transitions FROM one_dev_app;

-- 角色版本与授权申请的自定义部门（F-1 #728）：明细只插入，触发器拒改删，运行角色也不持有 DELETE 与 TRUNCATE；与 one_db_roles_schemas.sql §4b 同步。
REVOKE DELETE, TRUNCATE ON platform_identity.role_version_custom_org_units FROM one_dev_app;
REVOKE DELETE, TRUNCATE ON platform_identity.authorization_request_custom_org_units FROM one_dev_app;

-- 文件服务（ADR-0060、0061、0070；one-data 签发 IMP-1021-COO-02 单元 3）：只追加表与状态转换表运行角色只留 SELECT、INSERT；
-- 其余表运行角色不持有 DELETE 与 TRUNCATE（保存类别根表可改名称，其余由触发器限定只改状态或确认列）；视图只读。
REVOKE UPDATE, DELETE, TRUNCATE ON platform_file.file_bindings, platform_file.file_holds, platform_file.file_preservations,
    platform_file.scan_exceptions, platform_file.scan_exception_revocations, platform_file.state_transitions FROM one_dev_app;
REVOKE DELETE, TRUNCATE ON platform_file.stored_files, platform_file.stored_file_names, platform_file.preservation_categories,
    platform_file.preservation_category_versions, platform_file.false_positive_judgments, platform_file.quarantine_read_requests FROM one_dev_app;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON platform_file.current_file_preservations FROM one_dev_app;

-- 行政事务卡 A（R6）：配置集只推进乐观锁，版本头允许确认与撤回，条目与状态转换只追加。
REVOKE INSERT, DELETE, TRUNCATE ON organization_administration.config_sets FROM one_dev_app;
REVOKE DELETE, TRUNCATE ON organization_administration.config_set_versions FROM one_dev_app;
REVOKE UPDATE, DELETE, TRUNCATE ON organization_administration.employee_activity_type_items, organization_administration.state_transitions FROM one_dev_app;
-- 工作日历（IMP-1021-PAR-02，one-data 签发更正十）：根表与版本日期表只追加，运行角色只留 SELECT、INSERT；
-- 版本表运行角色只留 SELECT、INSERT、UPDATE（UPDATE 用于确认、撤回，关闭上一版本由触发器完成），不持有 DELETE 与 TRUNCATE。
REVOKE UPDATE, DELETE, TRUNCATE ON organization_attendance.work_calendars, organization_attendance.work_calendar_version_days FROM one_dev_app;
REVOKE DELETE, TRUNCATE ON organization_attendance.work_calendar_versions FROM one_dev_app;

-- 审计对象类别登记（ADR-0069 第 14 条）：行只由迁移写，运行角色只读；与 one_db_roles_schemas.sql §4b 同步。
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON platform_audit.audit_subject_kinds FROM one_dev_app;

-- 审计月分区预建（ADR-0048）：函数是 SECURITY DEFINER、属主 one_dev_owner，基线已对 PUBLIC 撤销 EXECUTE；运行角色经预建作业调用，缺此授权则 AuditPartitionJob 每天被拒、基线 6 个月分区用尽后审计写入失败。
-- 与 one_db_roles_schemas.sql §4b 同步；函数随基线迁移建出，故在迁移之后执行。
GRANT EXECUTE ON FUNCTION platform_audit.create_audit_entry_partitions(date, date) TO one_dev_app;
GRANT EXECUTE ON FUNCTION platform_audit.drop_archived_audit_entry_partition(date) TO one_dev_audit_maintainer;
RESET ROLE;
