-- 工时填报义务取证 · 老系统「工作日最低时数（工时+请假 ≥ 7.5h）」「整天请假不得发送」「非工作日是否有工时」的生产汇总。以取证账号 one_evidence_ro 执行（ADR-0037 第 7 项，只读）。
-- 只出汇总：分组计数、比例、小时分档；绝不取人名、工号、用户 ID、单据 ID、请假备注（remarks）等明细（人员标识只在临时表内关联，输出只有计数与不同个数）。
-- 工具会在 a8_db 与 ruoyi-vue-pro 各跑一次；数据批次用 DB_NAME() 守卫，只在 ruoyi-vue-pro 出结果。只含 SELECT（含 SELECT INTO 会话内临时表）。
-- 日历 psa_calendar（varchar 取值）：workday '1' 工作日 / '2' 休息日；weekend '1' 周末；holiday_legal '1' 法定节假日。休息日分类：法定（holiday_legal='1'）优先，其次周末（weekend='1'），其余为「平日休息」（非法定）。weekend='1' 且 workday='1' 为补班，算工作日。
-- 请假 psa_leave_records_month（视图）：begintime / endtime 时间段、duration 整型小时（仅哺乳假等有值）、finishedflag 0 审批中 1 通过（老代码不过滤）。请假分钟按 LeaveRecordsMonthServiceImpl.getLeaveMinutes 口径：仅工作日；duration 有值按 duration*60，否则与 09:00-12:00、13:30-18:00 两段的重叠分钟。
-- 在职口径取老代码 CommonServiceImpl 业务选人：system_users.status = 0 且 deleted = '0'。
-- 统计日范围：窗内且早于今天。窗 12m = 近 12 个月；since0501 = 2026-05-01 之后；since0901 = 2026-09-01 之后（请假视图 9 月前几乎无记录，此窗用来看有请假数据覆盖的时段）。
-- Q0 列元数据与视图范围；Q1 休息日有工时；Q2 工作日有请假；Q3 工作日已有工时单的当日合计小时分档（Q3c 按月）；Q4 在职员工缺填估计；Q5 行类型 x 工作包类型。
-- Q0 列元数据（只列元数据，不读数据）
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE
  FROM INFORMATION_SCHEMA.COLUMNS
 WHERE DB_NAME() = N'ruoyi-vue-pro' AND TABLE_SCHEMA = N'dbo'
   AND ((TABLE_NAME = N'psa_leave_records_month' AND COLUMN_NAME IN (N'user_id', N'type_name', N'begintime', N'endtime', N'duration', N'finishedflag'))
     OR (TABLE_NAME = N'psa_calendar' AND COLUMN_NAME IN (N'datetime', N'workday', N'weekend', N'holiday_legal'))
     OR (TABLE_NAME = N'psa_time_sheet' AND COLUMN_NAME IN (N'id', N'person_id', N'works_date', N'deleted', N'status', N'approve_status'))
     OR (TABLE_NAME = N'psa_time_sheet_works' AND COLUMN_NAME IN (N'tsheet_id', N'type', N'work_package_type', N'declare_hours', N'works_date', N'deleted'))
     OR (TABLE_NAME = N'system_users' AND COLUMN_NAME IN (N'id', N'status', N'deleted')))
 ORDER BY TABLE_NAME, ORDINAL_POSITION;
GO
-- Q0b 请假视图范围：总行数、不同人数、起止、按月分布（只计数）
SELECT CONVERT(char(7), begintime, 120) AS begin_month, COUNT(*) AS leave_rows, COUNT(DISTINCT user_id) AS leave_users,
       SUM(CASE WHEN duration IS NOT NULL THEN 1 ELSE 0 END) AS with_duration, SUM(CASE WHEN finishedflag = 1 THEN 1 ELSE 0 END) AS finished
  FROM [ruoyi-vue-pro].dbo.psa_leave_records_month
 WHERE DB_NAME() = N'ruoyi-vue-pro'
 GROUP BY CONVERT(char(7), begintime, 120)
 ORDER BY 1;
GO
-- Q-prep 窗与日历日（只在会话内）
SELECT w.win, w.start_d
  INTO #W
  FROM (VALUES (N'12m', DATEADD(MONTH, -12, CAST(GETDATE() AS date))), (N'since0501', CAST('2026-05-01' AS date)), (N'since0901', CAST('2026-09-01' AS date))) w(win, start_d)
 WHERE DB_NAME() = N'ruoyi-vue-pro';
GO
SELECT CAST(c.datetime AS date) AS d,
       CASE WHEN c.workday = '1' THEN 1 ELSE 0 END AS is_work,
       CASE WHEN c.workday = '1' THEN N'W'
            WHEN c.holiday_legal = '1' THEN N'L'
            WHEN c.weekend = '1' THEN N'E'
            ELSE N'O' END AS cls
  INTO #Cal
  FROM [ruoyi-vue-pro].dbo.psa_calendar c
 WHERE DB_NAME() = N'ruoyi-vue-pro' AND CAST(c.datetime AS date) >= DATEADD(MONTH, -12, CAST(GETDATE() AS date)) AND CAST(c.datetime AS date) < CAST(GETDATE() AS date);
GO
-- Q-prep2 人·日工时 #S（未删除单头、未删除行；工时合计 = declare_hours 之和）
SELECT h.person_id, CAST(h.works_date AS date) AS d, SUM(ISNULL(w.declare_hours, 0)) AS decl_h
  INTO #S
  FROM [ruoyi-vue-pro].dbo.psa_time_sheet h
  LEFT JOIN [ruoyi-vue-pro].dbo.psa_time_sheet_works w ON w.tsheet_id = h.id AND w.deleted = 0
 WHERE DB_NAME() = N'ruoyi-vue-pro' AND h.deleted = 0
   AND h.works_date >= DATEADD(MONTH, -12, CAST(GETDATE() AS date)) AND h.works_date < CAST(GETDATE() AS date)
 GROUP BY h.person_id, CAST(h.works_date AS date);
GO
-- Q-prep3 人·日请假分钟 #LM（工作日；按 getLeaveMinutes 口径展开；type_name 取该人该日分钟最多的一类，仅用于类别计数）
SELECT x.user_id, x.d, SUM(x.mins) AS leave_min, MAX(x.type_name) AS type_name
  INTO #LM
  FROM (SELECT lv.user_id, c.d, lv.type_name,
               CASE WHEN lv.duration IS NOT NULL THEN CAST(lv.duration AS int) * 60
                    ELSE (CASE WHEN am.m > 0 THEN am.m ELSE 0 END) + (CASE WHEN pm.m > 0 THEN pm.m ELSE 0 END) END AS mins
          FROM [ruoyi-vue-pro].dbo.psa_leave_records_month lv
          JOIN #Cal c ON c.is_work = 1 AND lv.begintime < DATEADD(DAY, 1, CAST(c.d AS datetime)) AND lv.endtime > CAST(c.d AS datetime)
          CROSS APPLY (SELECT DATEDIFF(MINUTE, CASE WHEN lv.begintime > DATEADD(MINUTE, 540, CAST(c.d AS datetime)) THEN lv.begintime ELSE DATEADD(MINUTE, 540, CAST(c.d AS datetime)) END,
                                                CASE WHEN lv.endtime < DATEADD(MINUTE, 720, CAST(c.d AS datetime)) THEN lv.endtime ELSE DATEADD(MINUTE, 720, CAST(c.d AS datetime)) END) AS m) am
          CROSS APPLY (SELECT DATEDIFF(MINUTE, CASE WHEN lv.begintime > DATEADD(MINUTE, 810, CAST(c.d AS datetime)) THEN lv.begintime ELSE DATEADD(MINUTE, 810, CAST(c.d AS datetime)) END,
                                                CASE WHEN lv.endtime < DATEADD(MINUTE, 1080, CAST(c.d AS datetime)) THEN lv.endtime ELSE DATEADD(MINUTE, 1080, CAST(c.d AS datetime)) END) AS m) pm
         WHERE DB_NAME() = N'ruoyi-vue-pro' AND lv.endtime > lv.begintime) x
 WHERE x.mins > 0
 GROUP BY x.user_id, x.d;
GO
-- Q1a 休息日有工时的人·日：占全部有工时人·日比例；按休息日类别（L 法定、E 周末、O 平日休息非法定）与合计；当日工时分档 ≤4、>4 且 ≤7.5、>7.5
SELECT w.win, ISNULL(CAST(c.cls AS nvarchar(20)), N'ALL_NONWORK') AS day_class, COUNT(*) AS person_days, COUNT(DISTINCT s.person_id) AS persons,
       (SELECT COUNT(*) FROM #S s2 WHERE s2.d >= w.start_d) AS all_filed_person_days,
       CAST(100.0 * COUNT(*) / NULLIF((SELECT COUNT(*) FROM #S s2 WHERE s2.d >= w.start_d), 0) AS decimal(6, 2)) AS pct_of_all_filed,
       SUM(CASE WHEN s.decl_h <= 4 THEN 1 ELSE 0 END) AS h_le4,
       SUM(CASE WHEN s.decl_h > 4 AND s.decl_h <= 7.5 THEN 1 ELSE 0 END) AS h_4_to_7p5,
       SUM(CASE WHEN s.decl_h > 7.5 THEN 1 ELSE 0 END) AS h_gt7p5,
       CAST(SUM(s.decl_h) AS decimal(18, 1)) AS hours_sum
  FROM #W w
  JOIN #S s ON s.d >= w.start_d
  JOIN #Cal c ON c.d = s.d AND c.is_work = 0
 GROUP BY GROUPING SETS ((w.win, w.start_d, c.cls), (w.win, w.start_d))
 ORDER BY w.win, c.cls;
GO
-- Q1b 全部有工时人·日按日类别（含工作日 W）的分布，作为分母参照
SELECT w.win, c.cls AS day_class, COUNT(*) AS person_days, CAST(SUM(s.decl_h) AS decimal(18, 1)) AS hours_sum
  FROM #W w
  JOIN #S s ON s.d >= w.start_d
  JOIN #Cal c ON c.d = s.d
 GROUP BY w.win, c.cls
 ORDER BY w.win, c.cls;
GO
-- Q2a 工作日有请假记录的人·日：其中同日有工时单 / 无工时单；整天请假（>= 450 分钟）日的同口径拆分；另给请假分钟分档与审批中（finishedflag 不拆，视图无逐日标志）
SELECT w.win, COUNT(*) AS leave_person_days, COUNT(DISTINCT l.user_id) AS leave_persons,
       SUM(CASE WHEN s.person_id IS NOT NULL THEN 1 ELSE 0 END) AS with_sheet,
       SUM(CASE WHEN s.person_id IS NULL THEN 1 ELSE 0 END) AS without_sheet,
       SUM(CASE WHEN l.leave_min >= 450 THEN 1 ELSE 0 END) AS fullday_leave_days,
       SUM(CASE WHEN l.leave_min >= 450 AND s.person_id IS NOT NULL THEN 1 ELSE 0 END) AS fullday_with_sheet,
       SUM(CASE WHEN l.leave_min >= 450 AND s.person_id IS NULL THEN 1 ELSE 0 END) AS fullday_without_sheet,
       SUM(CASE WHEN l.leave_min < 450 AND s.person_id IS NOT NULL THEN 1 ELSE 0 END) AS partial_with_sheet,
       SUM(CASE WHEN l.leave_min < 450 AND s.person_id IS NULL THEN 1 ELSE 0 END) AS partial_without_sheet
  FROM #W w
  JOIN #LM l ON l.d >= w.start_d
  LEFT JOIN #S s ON s.person_id = l.user_id AND s.d = l.d
 GROUP BY w.win
 ORDER BY w.win;
GO
-- Q2b 整天请假日有工时单者：该日工时分档与请假类别分布（类别是计数维度，不含个人信息）
SELECT w.win, l.type_name, COUNT(*) AS fullday_with_sheet_days,
       SUM(CASE WHEN s.decl_h < 7.5 THEN 1 ELSE 0 END) AS h_lt7p5, SUM(CASE WHEN s.decl_h >= 7.5 THEN 1 ELSE 0 END) AS h_ge7p5
  FROM #W w
  JOIN #LM l ON l.d >= w.start_d AND l.leave_min >= 450
  JOIN #S s ON s.person_id = l.user_id AND s.d = l.d
 GROUP BY w.win, l.type_name
 ORDER BY w.win, COUNT(*) DESC;
GO
-- Q2c 工作日有请假的人·日按请假类别（与是否有工时单）
SELECT w.win, l.type_name, COUNT(*) AS leave_person_days,
       SUM(CASE WHEN s.person_id IS NOT NULL THEN 1 ELSE 0 END) AS with_sheet,
       SUM(CASE WHEN l.leave_min >= 450 THEN 1 ELSE 0 END) AS fullday
  FROM #W w
  JOIN #LM l ON l.d >= w.start_d
  LEFT JOIN #S s ON s.person_id = l.user_id AND s.d = l.d
 GROUP BY w.win, l.type_name
 ORDER BY w.win, COUNT(*) DESC;
GO
-- Q3 工作日已有工时单的人·日：当日「工时+请假」小时分档 <7.5、=7.5、>7.5 且 <=15、>15；并给仅工时（不含请假）的同分档；有请假的人·日单列
SELECT w.win, COUNT(*) AS filed_workdays,
       SUM(CASE WHEN s.decl_h + ISNULL(l.leave_min, 0) / 60.0 < 7.5 THEN 1 ELSE 0 END) AS total_lt7p5,
       SUM(CASE WHEN s.decl_h + ISNULL(l.leave_min, 0) / 60.0 = 7.5 THEN 1 ELSE 0 END) AS total_eq7p5,
       SUM(CASE WHEN s.decl_h + ISNULL(l.leave_min, 0) / 60.0 > 7.5 AND s.decl_h + ISNULL(l.leave_min, 0) / 60.0 <= 15 THEN 1 ELSE 0 END) AS total_gt7p5_le15,
       SUM(CASE WHEN s.decl_h + ISNULL(l.leave_min, 0) / 60.0 > 15 THEN 1 ELSE 0 END) AS total_gt15,
       SUM(CASE WHEN s.decl_h < 7.5 THEN 1 ELSE 0 END) AS declared_lt7p5,
       SUM(CASE WHEN s.decl_h = 7.5 THEN 1 ELSE 0 END) AS declared_eq7p5,
       SUM(CASE WHEN s.decl_h > 7.5 AND s.decl_h <= 15 THEN 1 ELSE 0 END) AS declared_gt7p5_le15,
       SUM(CASE WHEN s.decl_h > 15 THEN 1 ELSE 0 END) AS declared_gt15,
       SUM(CASE WHEN l.leave_min IS NOT NULL THEN 1 ELSE 0 END) AS with_leave,
       SUM(CASE WHEN l.leave_min IS NOT NULL AND s.decl_h < 7.5 THEN 1 ELSE 0 END) AS with_leave_declared_lt7p5
  FROM #W w
  JOIN #S s ON s.d >= w.start_d
  JOIN #Cal c ON c.d = s.d AND c.is_work = 1
  LEFT JOIN #LM l ON l.user_id = s.person_id AND l.d = s.d
 GROUP BY w.win
 ORDER BY w.win;
GO
-- Q3b 工作日已有工时单的人·日：仅工时在 7.5 附近的细分（<6、6-7、7-7.5 不含、=7.5、7.5-8、8-9、>9 且 <=15），看 7.5 闸实际聚集
SELECT w.win,
       SUM(CASE WHEN s.decl_h < 6 THEN 1 ELSE 0 END) AS lt6, SUM(CASE WHEN s.decl_h >= 6 AND s.decl_h < 7 THEN 1 ELSE 0 END) AS h6_7,
       SUM(CASE WHEN s.decl_h >= 7 AND s.decl_h < 7.5 THEN 1 ELSE 0 END) AS h7_7p5, SUM(CASE WHEN s.decl_h = 7.5 THEN 1 ELSE 0 END) AS eq7p5,
       SUM(CASE WHEN s.decl_h > 7.5 AND s.decl_h < 8 THEN 1 ELSE 0 END) AS h7p5_8, SUM(CASE WHEN s.decl_h >= 8 AND s.decl_h < 9 THEN 1 ELSE 0 END) AS h8_9,
       SUM(CASE WHEN s.decl_h >= 9 AND s.decl_h <= 15 THEN 1 ELSE 0 END) AS h9_15, SUM(CASE WHEN s.decl_h > 15 THEN 1 ELSE 0 END) AS gt15
  FROM #W w
  JOIN #S s ON s.d >= w.start_d
  JOIN #Cal c ON c.d = s.d AND c.is_work = 1
 GROUP BY w.win
 ORDER BY w.win;
GO
-- Q3c 按工时月份：工作日有工时单人·日、其中「工时+请假」< 7.5 的数、休息日有工时的人·日数（看老规则生效前后）
SELECT CONVERT(char(7), s.d, 120) AS works_month,
       SUM(CASE WHEN c.is_work = 1 THEN 1 ELSE 0 END) AS filed_workdays,
       SUM(CASE WHEN c.is_work = 1 AND s.decl_h + ISNULL(l.leave_min, 0) / 60.0 < 7.5 THEN 1 ELSE 0 END) AS total_lt7p5,
       SUM(CASE WHEN c.is_work = 1 AND s.decl_h < 7.5 THEN 1 ELSE 0 END) AS declared_lt7p5,
       SUM(CASE WHEN c.is_work = 0 THEN 1 ELSE 0 END) AS filed_nonwork_days
  FROM #S s
  JOIN #Cal c ON c.d = s.d
  LEFT JOIN #LM l ON l.user_id = s.person_id AND l.d = s.d
 GROUP BY CONVERT(char(7), s.d, 120)
 ORDER BY 1;
GO
-- Q4 在职员工（status=0 且 deleted='0'）的工作日缺填估计。person_start 为该人首张未删除工时单日期（任意日期）；
--    口径 A = 在职员工 x 窗内全部工作日；口径 B = 只算 person_start 起的工作日（排除入职前，无单者不计）；
--    miss = 当日无工时单且无请假；miss_leave_only = 无工时单但有请假。
SELECT w.win, N'A_all_workdays' AS basis, COUNT(*) AS person_workdays,
       SUM(CASE WHEN s.person_id IS NOT NULL THEN 1 ELSE 0 END) AS filed,
       SUM(CASE WHEN s.person_id IS NULL AND l.user_id IS NOT NULL THEN 1 ELSE 0 END) AS miss_leave_only,
       SUM(CASE WHEN s.person_id IS NULL AND l.user_id IS NULL THEN 1 ELSE 0 END) AS miss,
       CAST(100.0 * SUM(CASE WHEN s.person_id IS NULL AND l.user_id IS NULL THEN 1 ELSE 0 END) / COUNT(*) AS decimal(6, 2)) AS miss_pct,
       COUNT(DISTINCT u.id) AS active_persons
  FROM #W w
  JOIN #Cal c ON c.d >= w.start_d AND c.is_work = 1
  JOIN [ruoyi-vue-pro].dbo.system_users u ON u.status = 0 AND u.deleted = '0'
  LEFT JOIN #S s ON s.person_id = u.id AND s.d = c.d
  LEFT JOIN #LM l ON l.user_id = u.id AND l.d = c.d
 WHERE DB_NAME() = N'ruoyi-vue-pro'
 GROUP BY w.win
UNION ALL
SELECT w.win, N'B_from_first_sheet', COUNT(*),
       SUM(CASE WHEN s.person_id IS NOT NULL THEN 1 ELSE 0 END),
       SUM(CASE WHEN s.person_id IS NULL AND l.user_id IS NOT NULL THEN 1 ELSE 0 END),
       SUM(CASE WHEN s.person_id IS NULL AND l.user_id IS NULL THEN 1 ELSE 0 END),
       CAST(100.0 * SUM(CASE WHEN s.person_id IS NULL AND l.user_id IS NULL THEN 1 ELSE 0 END) / NULLIF(COUNT(*), 0) AS decimal(6, 2)),
       COUNT(DISTINCT u.id)
  FROM #W w
  JOIN #Cal c ON c.d >= w.start_d AND c.is_work = 1
  JOIN [ruoyi-vue-pro].dbo.system_users u ON u.status = 0 AND u.deleted = '0'
  JOIN (SELECT person_id, MIN(works_date) AS first_d FROM [ruoyi-vue-pro].dbo.psa_time_sheet WHERE deleted = 0 GROUP BY person_id) f ON f.person_id = u.id AND f.first_d <= c.d
  LEFT JOIN #S s ON s.person_id = u.id AND s.d = c.d
  LEFT JOIN #LM l ON l.user_id = u.id AND l.d = c.d
 WHERE DB_NAME() = N'ruoyi-vue-pro'
 GROUP BY w.win
 ORDER BY 1, 2;
GO
-- Q4b 在职员工人数、其中从无工时单的人数、窗内至少一张单的人数（只计数）
SELECT w.win, COUNT(*) AS active_persons,
       SUM(CASE WHEN f.person_id IS NULL THEN 1 ELSE 0 END) AS never_filed,
       SUM(CASE WHEN x.person_id IS NOT NULL THEN 1 ELSE 0 END) AS filed_in_win
  FROM #W w
  JOIN [ruoyi-vue-pro].dbo.system_users u ON u.status = 0 AND u.deleted = '0'
  LEFT JOIN (SELECT DISTINCT person_id FROM [ruoyi-vue-pro].dbo.psa_time_sheet WHERE deleted = 0) f ON f.person_id = u.id
  LEFT JOIN (SELECT DISTINCT s.person_id, w2.win FROM #S s JOIN #W w2 ON s.d >= w2.start_d) x ON x.person_id = u.id AND x.win = w.win
 WHERE DB_NAME() = N'ruoyi-vue-pro'
 GROUP BY w.win
 ORDER BY w.win;
GO
-- Q5 工时行：行类型 type x 工作包类型 work_package_type 的行数与工时合计（看有无请假类行；类型含义见 CommonConstants，0 项目 1 售前 2 OH 3 混合）
SELECT w.win, ISNULL(CAST(r.type AS varchar(10)), 'NULL') AS row_type, ISNULL(CAST(r.work_package_type AS varchar(10)), 'NULL') AS work_package_type,
       COUNT(*) AS row_cnt, CAST(SUM(ISNULL(r.declare_hours, 0)) AS decimal(18, 1)) AS hours_sum
  FROM #W w
  JOIN [ruoyi-vue-pro].dbo.psa_time_sheet_works r ON r.works_date >= w.start_d AND r.works_date < CAST(GETDATE() AS date) AND r.deleted = 0
  JOIN [ruoyi-vue-pro].dbo.psa_time_sheet h ON h.id = r.tsheet_id AND h.deleted = 0
 WHERE DB_NAME() = N'ruoyi-vue-pro'
 GROUP BY w.win, r.type, r.work_package_type
 ORDER BY w.win, r.type, r.work_package_type;
GO
