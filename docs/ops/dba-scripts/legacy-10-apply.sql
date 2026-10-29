-- FORCITIS one · B-5 · 老系统库（ruoyi-vue-pro，SQL Server 2016+）两个只读视图与两个专用账号 —— 执行脚本
-- 执行人：老系统 RDS 的 DBA。工具：sqlcmd，或 SSMS「SQLCMD 模式」。不改任何老表，不写任何老数据。
-- 用法：sqlcmd -S <老系统 RDS 内网地址> -d ruoyi-vue-pro -U <高权限账号> -b -i legacy_10_apply.sql
-- 视图正文与 docs/data/legacy-views/legacy-inbox-view.sql、docs/data/legacy-views/legacy-org-names-view.sql 逐字一致（原校验脚本 check_view_sync.py 已于 #742 删除，改动时人工对照）。
-- 必须是两个账号：应用对每个连接启动时都校验「能 SELECT 的对象恰好只有本视图」（I-1），一个账号读两个视图会两边都拒绝启动；
-- 授权必须直接给用户，不经数据库角色（information_schema 看不到经角色得到的授权，应用据此禁止角色成员）。
-- 账号名须与应用的环境变量一致：
--   INBOX_LOGIN  → FORCITIS_PLATFORM_INTEGRATION_LEGACY_INBOX_USERNAME（合并待办，platform.approval）
--   NAMES_LOGIN  → LEGACY_DB_RO_USERNAME（部门简称，platform.integration）
:setvar INBOX_LOGIN psa_one_legacy_ro
:setvar NAMES_LOGIN one_legacy_names_ro
:on error exit

-- ===== STEP 0 预检（只读）=====
-- 0.1 期望 0 行
SELECT name FROM sys.views WHERE name IN (N'v_psa_one_legacy_todo', N'v_one_legacy_org_short_name');
SELECT name FROM sys.server_principals WHERE name IN (N'$(INBOX_LOGIN)', N'$(NAMES_LOGIN)');
-- 0.2 版本 ≥ 13（2016，AT TIME ZONE 需要）
SELECT SERVERPROPERTY('ProductMajorVersion') AS major_version;
-- 0.3 基表存在：期望 4 行
SELECT name FROM sys.tables WHERE name IN (N'bpm_task_ext', N'ACT_RU_TASK', N'bpm_process_instance_ext', N'system_dept');
GO

-- ===== STEP 1 合并待办视图（与 LEGACY_INBOX_VIEW.sql 第 1 段逐字一致）=====
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

-- ===== STEP 2 部门简称视图（与 LEGACY_ORG_NAMES_VIEW.sql 第 1 段逐字一致）=====
CREATE VIEW dbo.v_one_legacy_org_short_name
AS
SELECT d.id         AS id,
       d.short_name AS short_name
  FROM dbo.system_dept d
 WHERE d.deleted = '0';
GO

-- ===== STEP 3 两个登录名（随机口令，各只显示一次）=====
-- 托管实例不允许 T-SQL 建登录时，改在 RDS 控制台建同名登录、≥ 32 位随机口令，跳过本步。
-- CHECK_EXPIRATION 关闭：服务账号口令到期会让合并待办与简称同步中断；按 90 天由 DBA 计划轮换（docs/ops/runbooks/kms.md）。
DECLARE @logins TABLE (login_name sysname, env_name sysname);
INSERT INTO @logins VALUES (N'$(INBOX_LOGIN)', N'FORCITIS_PLATFORM_INTEGRATION_LEGACY_INBOX_PASSWORD'),
                           (N'$(NAMES_LOGIN)', N'LEGACY_DB_RO_PASSWORD');
DECLARE @shown TABLE (login_name sysname, env_name sysname, one_time_secret nvarchar(64));
DECLARE @login sysname, @env sysname, @secret nvarchar(64), @ddl nvarchar(max);
DECLARE c CURSOR LOCAL FAST_FORWARD FOR SELECT login_name, env_name FROM @logins;
OPEN c;
FETCH NEXT FROM c INTO @login, @env;
WHILE @@FETCH_STATUS = 0
BEGIN
  SET @secret = N'Aa-' + CONVERT(nvarchar(64), CRYPT_GEN_RANDOM(24), 2);
  SET @ddl = N'CREATE LOGIN ' + QUOTENAME(@login) + N' WITH PASSWORD = ' + QUOTENAME(@secret, N'''')
           + N', CHECK_POLICY = ON, CHECK_EXPIRATION = OFF';
  EXEC (@ddl);
  INSERT INTO @shown VALUES (@login, @env, @secret);
  FETCH NEXT FROM c INTO @login, @env;
END
CLOSE c; DEALLOCATE c;
SELECT login_name, env_name, one_time_secret,
       N'只显示这一次：经约定的安全渠道交运维写入 /etc/forcitis-one/one.env 的对应变量，然后关闭结果窗口；不截图、不发聊天、不进工单' AS handling
  FROM @shown;
GO

-- ===== STEP 4 库内用户与最小授权（各只授一个视图）=====
CREATE USER [$(INBOX_LOGIN)] FOR LOGIN [$(INBOX_LOGIN)] WITH DEFAULT_SCHEMA = dbo;
GRANT SELECT ON dbo.v_psa_one_legacy_todo TO [$(INBOX_LOGIN)];
CREATE USER [$(NAMES_LOGIN)] FOR LOGIN [$(NAMES_LOGIN)] WITH DEFAULT_SCHEMA = dbo;
GRANT SELECT ON dbo.v_one_legacy_org_short_name TO [$(NAMES_LOGIN)];
GO
-- 令牌表不另加 DENY：与正本脚本一致，令牌表本就不在授权内（I-1），由 legacy_20_verify.sql V3 / V4 证明

-- 下一步：legacy_20_verify.sql；并把 172.16.89.86 加入本 RDS 白名单（控制台操作，见 README）
