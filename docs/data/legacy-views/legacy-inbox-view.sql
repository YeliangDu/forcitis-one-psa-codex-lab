-- 授权部分已被 ADR-0037 取代（账号全库只读，库级 DENY 写；应用按 ADR-0037 第 4 项校验账号）；视图冻结，正本保留可执行（回滚用）。
-- ============================================================================================================
-- 老系统待办只读视图（提议，待 DBA 评审；Phase 1a · platform.approval 合并待办）
-- 依据：REIMAGINED_ARCHITECTURE.md §3.6、SECURITY_INVARIANTS.md I-1、检查点 2 Q-4（老库待办只读视图及账号，运维 / DBA 执行）
--
-- 这是一次【老系统变更登记】（架构评审 #11）：在老库 ruoyi-vue-pro（SQL Server，需 2016 及以上，用到 AT TIME ZONE）上新增
-- 1 个视图和 1 个只读账号。不改任何老表、不写任何老数据。执行人：DBA / 运维（由用户安排）。
--
-- 新系统怎么用它：
--   * 连接配置只走环境变量，不写进仓库：
--       FORCITIS_PLATFORM_INTEGRATION_LEGACY_INBOX_URL      jdbc:sqlserver://<host>:1433;databaseName=ruoyi-vue-pro;encrypt=true
--       FORCITIS_PLATFORM_INTEGRATION_LEGACY_INBOX_USERNAME psa_one_legacy_ro
--       FORCITIS_PLATFORM_INTEGRATION_LEGACY_INBOX_PASSWORD （由 DBA 生成，放密钥服务，不进任何文件）
--       FORCITIS_PLATFORM_INTEGRATION_LEGACY_INBOX_VIEW_NAME（可选，默认 dbo.v_psa_one_legacy_todo）
--       FORCITIS_PLATFORM_INTEGRATION_LEGACY_INBOX_DETAIL_URL_TEMPLATE  老系统详情页，含 {processInstanceId} 与 {taskId} 占位符
--   * 新系统每次打开待办都查询一次，不缓存（约每天 540 次）。
--   * 新系统启动时核对：该账号能 SELECT 的对象必须【恰好只有本视图】，否则拒绝启动（I-1：令牌表等一律不授权）。
--
-- 只含所需列（最小暴露）：待办人、任务号、实例号、节点 key、节点名、流程标题、创建时间（UTC）。
-- 不含：原因、表单变量、发起人以外的任何人员信息、令牌、手机号等。
-- ============================================================================================================

-- 1. 视图：result = 1 的在途任务，必须仍在老引擎的运行表里（ACT_RU_TASK），标题取自流程实例扩展表
CREATE VIEW dbo.v_psa_one_legacy_todo
AS
SELECT t.assignee_user_id                                                       AS assignee_user_id,
       t.task_id                                                                AS task_id,
       t.process_instance_id                                                    AS process_instance_id,
       t.task_def_key                                                           AS task_def_key,
       t.name                                                                   AS task_name,
       p.title                                                                  AS process_title,
       -- 老库时间为东八区本地时间；视图统一给 UTC，新系统按 UTC 读取
       CAST((t.create_time AT TIME ZONE 'China Standard Time') AT TIME ZONE 'UTC' AS datetime2(3)) AS created_at
  FROM dbo.bpm_task_ext t
  JOIN dbo.ACT_RU_TASK a
    -- 生产库两列排序规则不同（ID_ 为 Chinese_PRC_90_CI_AI，task_id 为 Chinese_PRC_CI_AS），不显式指定会报 468
    ON a.ID_ = t.task_id COLLATE DATABASE_DEFAULT
  LEFT JOIN dbo.bpm_process_instance_ext p
    ON p.process_instance_id = t.process_instance_id
   AND p.deleted = '0'
 WHERE t.result = 1                 -- 1 = 处理中（RULE-294）；已办（2/3/4/5）和知会（6，RULE-299，1b）不在 1a 范围
   AND t.deleted = '0'
   AND t.assignee_user_id IS NOT NULL;
GO

-- 2. 只读账号：只授予视图的 SELECT；不加入任何数据库角色（db_datareader 等一律不加，否则等于授权全库）
CREATE LOGIN psa_one_legacy_ro WITH PASSWORD = N'<<由 DBA 生成并保存到密钥服务；不要写进任何文件>>', CHECK_POLICY = ON;
CREATE USER  psa_one_legacy_ro FOR LOGIN psa_one_legacy_ro WITH DEFAULT_SCHEMA = dbo;
GRANT SELECT ON dbo.v_psa_one_legacy_todo TO psa_one_legacy_ro;
-- 视图与基表同属 dbo（所有权链），账号不需要、也不得获得基表的任何权限。
GO

-- 3. 验证（DBA 执行并留存结果；新系统每次启动也会做等价检查）
--    3.1 账号能 SELECT 的用户对象必须只有本视图（排除 sys / INFORMATION_SCHEMA 系统视图）：
EXECUTE AS USER = 'psa_one_legacy_ro';
SELECT TABLE_SCHEMA, TABLE_NAME
  FROM INFORMATION_SCHEMA.TABLES
 WHERE TABLE_SCHEMA NOT IN ('sys', 'INFORMATION_SCHEMA')
   AND HAS_PERMS_BY_NAME(QUOTENAME(TABLE_SCHEMA) + '.' + QUOTENAME(TABLE_NAME), 'OBJECT', 'SELECT') = 1;
--    期望：恰好 1 行 dbo.v_psa_one_legacy_todo
REVERT;
--    3.2 账号不是任何数据库角色的成员（期望：0 行，除 public）：
SELECT r.name AS role_name
  FROM sys.database_role_members m
  JOIN sys.database_principals r ON r.principal_id = m.role_principal_id
  JOIN sys.database_principals u ON u.principal_id = m.member_principal_id
 WHERE u.name = 'psa_one_legacy_ro';
GO

-- 4. 回滚（切换回退或停用合并待办时执行；新系统此时只显示新待办，legacyAvailable=false）
-- DROP USER psa_one_legacy_ro;
-- DROP LOGIN psa_one_legacy_ro;
-- DROP VIEW dbo.v_psa_one_legacy_todo;
