-- FORCITIS one · C-6 老系统只读导出（SQL Server，老库 ruoyi-vue-pro）。只有 SELECT；执行前须用户逐项确认（ADR-0026 D-9 已授权老角色导出）。
-- 执行通道：经云助手只读通道，在会话内存 / 服务器临时目录生成文件，导出文件只存本机 ~/.modernize/psa/，不进仓库；临时目录用后删除。
-- 列名先由 STEP 1 核对；与下面查询不符时停止，改查询并留档，不猜列。
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;

-- ===== STEP 1 列核对（只读元数据）=====
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE
  FROM INFORMATION_SCHEMA.COLUMNS
 WHERE TABLE_SCHEMA = 'dbo'
   AND TABLE_NAME IN ('system_role', 'system_user_role', 'system_role_menu', 'system_menu', 'psa_calendar')
 ORDER BY TABLE_NAME, ORDINAL_POSITION;

-- ===== STEP 2 老角色与权限（ADR-0026 第 8 项；导出到 legacy-roles-<日期>.json）=====
-- 2.1 角色定义（含范围类型与 data_scope_dept_ids，RULE-349 的「非自定义类型但带部门 id」由差异报告单列）
SELECT r.id, r.name, r.code, r.data_scope, r.data_scope_dept_ids, r.status
  FROM dbo.system_role r
 WHERE r.deleted = 0
 ORDER BY r.id;

-- 2.2 角色持有人（只导出用户 id，不导出姓名、手机号）
SELECT ur.user_id, ur.role_id
  FROM dbo.system_user_role ur
  JOIN dbo.system_role r ON r.id = ur.role_id AND r.deleted = 0
 WHERE ur.deleted = 0
 ORDER BY ur.role_id, ur.user_id;

-- 2.3 角色的菜单权限串（对照新 Permission，对不上的不授予）
SELECT rm.role_id, m.id AS menu_id, m.type, m.permission, m.component
  FROM dbo.system_role_menu rm
  JOIN dbo.system_menu m ON m.id = rm.menu_id AND m.deleted = 0
  JOIN dbo.system_role r ON r.id = rm.role_id AND r.deleted = 0
 WHERE rm.deleted = 0
 ORDER BY rm.role_id, m.id;

-- 2.4 计数（导出后与文件行数核对，记入留档）
SELECT (SELECT COUNT(*) FROM dbo.system_role WHERE deleted = 0) AS roles,
       (SELECT COUNT(*) FROM dbo.system_user_role WHERE deleted = 0) AS holdings,
       (SELECT COUNT(*) FROM dbo.system_role_menu WHERE deleted = 0) AS grants;

-- ===== STEP 3 工作日历 2016–2026（D-1a-114：4,018 天；导出到 legacy-calendar-<日期>.csv）=====
-- 新系统只存例外日：周一至周五的非工作日 → HOLIDAY；周六日的工作日 → MAKE_UP_WORKDAY
-- 依据（2026-10-17 取证，legacy-read 登记；列：holiday* 是节日编码、holiday_cn 等是中文名，workday 是「是否上班」，均为 varchar）：
--   workday：'1' 上班、'2' 休息，无别的取值；周一至五 2663 天为 '1'、207 天为 '2'，周六日 65 天为 '1'、1083 天为 '2'。
--   周一至五的 207 个休息日中 198 个带法定假日编码（春节 11、除夕 15、元旦 22、清明 44、劳动节 55、端午 66、中秋 77、国庆 88），
--   另 9 个无编码（holiday = '10'），全部落在 2022–2025 年 1–2 月（春节假期的尾部日期，编码没有带到）；非法定节日（情人节、植树节等）的周一至五全部 workday = '1'。
--   所以 HOLIDAY 以 workday = '2' 为准，法定假日编码印证其中 198 个，9 个无编码日期按春节假期尾部处理（推断，导入后与国务院公告核对）。
-- 2026-10-17 前的版本写成 workday = 0 判 HOLIDAY（取值域写错），导出里一条 HOLIDAY 都没有，已改。
-- DATEDIFF 对 1900-01-01（周一）取模，与 @@DATEFIRST 无关：0..4 为周一至周五
SELECT CAST(c.[datetime] AS date) AS calendar_date,
       c.workday,
       CASE
         WHEN DATEDIFF(day, '19000101', CAST(c.[datetime] AS date)) % 7 < 5 AND c.workday = '2' THEN 'HOLIDAY'
         WHEN DATEDIFF(day, '19000101', CAST(c.[datetime] AS date)) % 7 >= 5 AND c.workday = '1' THEN 'MAKE_UP_WORKDAY'
       END AS day_kind
  FROM dbo.psa_calendar c
 WHERE c.[datetime] >= '20160101' AND c.[datetime] < '20270101'
 ORDER BY c.[datetime];

-- 3.1 完整性：期望 4018 行、无重复日期、每年天数为 365 / 366；每年 HOLIDAY 大于 0，MAKE_UP_WORKDAY 与两类合计都有计数
SELECT YEAR(c.[datetime]) AS calendar_year, COUNT(*) AS days, COUNT(DISTINCT CAST(c.[datetime] AS date)) AS distinct_days,
       SUM(CASE WHEN DATEDIFF(day, '19000101', CAST(c.[datetime] AS date)) % 7 < 5 AND c.workday = '2' THEN 1 ELSE 0 END) AS holidays,
       SUM(CASE WHEN DATEDIFF(day, '19000101', CAST(c.[datetime] AS date)) % 7 >= 5 AND c.workday = '1' THEN 1 ELSE 0 END) AS make_up_workdays,
       SUM(CASE WHEN c.workday NOT IN ('1', '2') OR c.workday IS NULL THEN 1 ELSE 0 END) AS unexpected_workday_values
  FROM dbo.psa_calendar c
 WHERE c.[datetime] >= '20160101' AND c.[datetime] < '20270101'
 GROUP BY YEAR(c.[datetime])
 ORDER BY calendar_year;

-- ===== STEP 4 TIMESHEET 回放导出 =====
-- 格式与取数口径以 docs/data/timesheet-replay.md「导出文件」与「执行步骤」为准（当前定义版本的实例样本、当时的组织快照、
-- bpm_task_assign_rule、流程变量、工时行类型集合与实际审批人序列），文件写新词汇；本文件不重复其查询，避免两处漂移。
