-- ARCH-D5 工时审批路径方案核对 · 老系统工时审批取人的生产汇总取证。以取证账号 one_evidence_ro 执行（ADR-0037 第 7 项，只读）。
-- 只出汇总：分组计数、合计、比例、耗时分档；绝不取人名、工号、用户 ID、单据 ID、项目名、文字意见等明细（审批人 ID 只在临时表内比对，输出只有计数与不同个数）。
-- 工具会在 a8_db 与 ruoyi-vue-pro 各跑一次；全部三段名都指向 ruoyi-vue-pro，数据批次用 DB_NAME() 守卫，只在 ruoyi-vue-pro 出结果。
-- 时间窗：工时日期近 12 个月（win=12m）与 2026-05-01 之后（win=since0501，A8 停用后）；bpm 取近 180 天。
-- 行类型 type：0 项目、1 售前、2 OH、3 混合（CommonConstants.ManHourType）；行审批状态 approve_status：0 待发起 1 审批中 2 驳回 3 通过 4 取消。
-- Q0 列元数据；Q1 类型分布；Q2 每单类型数与组合；Q3 类型 x 工作包类型；Q4 审批人核对；Q5 行审批结果；Q6 bpm 节点任务耗时。
-- Q0 列元数据（只列元数据，不读数据）
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE
  FROM INFORMATION_SCHEMA.COLUMNS
 WHERE DB_NAME() = N'ruoyi-vue-pro' AND TABLE_SCHEMA = N'dbo'
   AND ((TABLE_NAME = N'psa_time_sheet' AND COLUMN_NAME IN (N'id', N'creator', N'approve_status', N'status', N'deleted', N'works_date', N'person_id'))
     OR (TABLE_NAME = N'psa_time_sheet_works' AND COLUMN_NAME IN (N'id', N'tsheet_id', N'type', N'work_package_type', N'approval', N'approve_status', N'declare_hours', N'approval_hours', N'works_date', N'deleted', N'manager_id', N'project_code', N'task_id', N'work_package_id'))
     OR (TABLE_NAME = N'system_dept' AND COLUMN_NAME IN (N'id', N'leader_user_id', N'deleted'))
     OR (TABLE_NAME = N'system_users' AND COLUMN_NAME IN (N'id', N'dept_id', N'leader_user_id', N'deleted'))
     OR (TABLE_NAME = N'psa_crm_opportunity' AND COLUMN_NAME IN (N'id', N'code', N'pdc_dept_id'))
     OR (TABLE_NAME = N'psa_pm_project' AND COLUMN_NAME IN (N'id', N'code', N'manager_id'))
     OR (TABLE_NAME = N'psa_pm_plan_task' AND COLUMN_NAME IN (N'id', N'project_id', N'manager_id', N'type', N'deleted'))
     OR (TABLE_NAME = N'psa_crm_leads' AND COLUMN_NAME IN (N'id', N'code', N'pdc_dept_id'))
     OR (TABLE_NAME = N'psa_bd_customer' AND COLUMN_NAME IN (N'id', N'code', N'pdc_dept_id'))
     OR (TABLE_NAME = N'psa_crm_customervisit' AND COLUMN_NAME IN (N'id', N'pdc_dept_id', N'opportunity_id', N'leads_id', N'customer_id'))
     OR (TABLE_NAME = N'bpm_task_ext' AND COLUMN_NAME IN (N'id', N'task_id', N'result', N'end_time', N'create_time', N'process_instance_id', N'name', N'deleted'))
     OR (TABLE_NAME = N'ACT_HI_TASKINST' AND COLUMN_NAME IN (N'ID_', N'PROC_DEF_ID_', N'TASK_DEF_KEY_', N'NAME_', N'START_TIME_', N'END_TIME_', N'ASSIGNEE_', N'PROC_INST_ID_')))
 ORDER BY TABLE_NAME, ORDINAL_POSITION;
GO
-- Q-prep 行级临时表 #L（只在会话内，不输出）：近 12 个月、未删除的工时行，附带规则推算出的应审批人，用于与行上 approval 比对
SELECT w.id AS wid, w.tsheet_id, h.person_id, w.type, w.work_package_type AS wpt, w.works_date, w.declare_hours,
       w.approve_status AS st, w.approval,
       CASE WHEN w.works_date >= '2026-05-01' THEN 1 ELSE 0 END AS w0501,
       u.leader_user_id AS sub_leader, d.leader_user_id AS sub_dept_leader,
       pm.exp_pm, pl.leader AS pdc_leader,
       CASE w.type WHEN 0 THEN pm.exp_pm WHEN 2 THEN u.leader_user_id WHEN 1 THEN pl.leader WHEN 3 THEN pl.leader END AS exp_rule
  INTO #L
  FROM [ruoyi-vue-pro].dbo.psa_time_sheet_works w
  JOIN [ruoyi-vue-pro].dbo.psa_time_sheet h ON h.id = w.tsheet_id AND h.deleted = 0
  LEFT JOIN [ruoyi-vue-pro].dbo.system_users u ON u.id = h.person_id
  LEFT JOIN [ruoyi-vue-pro].dbo.system_dept d ON d.id = u.dept_id
  OUTER APPLY (SELECT COALESCE(
        CASE WHEN w.work_package_type = 0 THEN COALESCE(
            (SELECT TOP 1 p.manager_id FROM [ruoyi-vue-pro].dbo.psa_pm_plan_task t JOIN [ruoyi-vue-pro].dbo.psa_pm_project p ON p.id = t.project_id
              WHERE t.id = COALESCE(w.task_id, w.work_package_id)),
            (SELECT TOP 1 t.manager_id FROM [ruoyi-vue-pro].dbo.psa_pm_plan_task t WHERE t.id = COALESCE(w.task_id, w.work_package_id)),
            w.manager_id) END,
        (SELECT TOP 1 p.manager_id FROM [ruoyi-vue-pro].dbo.psa_pm_project p
          WHERE p.code = LTRIM(RTRIM(w.project_code)) AND LTRIM(RTRIM(w.project_code)) NOT IN (N'', N'--'))) AS exp_pm) pm
  OUTER APPLY (SELECT CASE
        WHEN w.work_package_type = 6 THEN COALESCE(
            (SELECT TOP 1 v.pdc_dept_id FROM [ruoyi-vue-pro].dbo.psa_crm_customervisit v WHERE v.id = w.work_package_id),
            (SELECT TOP 1 o.pdc_dept_id FROM [ruoyi-vue-pro].dbo.psa_crm_customervisit v JOIN [ruoyi-vue-pro].dbo.psa_crm_opportunity o ON o.id = v.opportunity_id WHERE v.id = w.work_package_id),
            (SELECT TOP 1 l.pdc_dept_id FROM [ruoyi-vue-pro].dbo.psa_crm_customervisit v JOIN [ruoyi-vue-pro].dbo.psa_crm_leads l ON l.id = v.leads_id WHERE v.id = w.work_package_id),
            (SELECT TOP 1 c.pdc_dept_id FROM [ruoyi-vue-pro].dbo.psa_crm_customervisit v JOIN [ruoyi-vue-pro].dbo.psa_bd_customer c ON c.id = v.customer_id WHERE v.id = w.work_package_id))
        WHEN LTRIM(RTRIM(w.project_code)) IN (N'', N'--') THEN NULL
        WHEN w.work_package_type IN (2, 3) THEN (SELECT TOP 1 o.pdc_dept_id FROM [ruoyi-vue-pro].dbo.psa_crm_opportunity o WHERE o.code = LTRIM(RTRIM(w.project_code)))
        WHEN w.work_package_type = 4 THEN (SELECT TOP 1 l.pdc_dept_id FROM [ruoyi-vue-pro].dbo.psa_crm_leads l WHERE l.code = LTRIM(RTRIM(w.project_code)))
        WHEN w.work_package_type = 5 THEN (SELECT TOP 1 c.pdc_dept_id FROM [ruoyi-vue-pro].dbo.psa_bd_customer c WHERE c.code = LTRIM(RTRIM(w.project_code)))
        END AS dept_id) pd
  OUTER APPLY (SELECT TOP 1 dd.leader_user_id AS leader FROM [ruoyi-vue-pro].dbo.system_dept dd WHERE dd.id = pd.dept_id) pl
 WHERE DB_NAME() = N'ruoyi-vue-pro' AND w.deleted = 0
   AND w.works_date >= DATEADD(MONTH, -12, CAST(GETDATE() AS date));
GO
-- Q-prep2 按时间窗展开 #LW（win=12m 为全部，win=since0501 为 2026-05-01 之后）
SELECT x.win, L.*
  INTO #LW
  FROM #L L
  JOIN (VALUES (N'12m'), (N'since0501')) x(win) ON (x.win = N'12m') OR (x.win = N'since0501' AND L.w0501 = 1);
GO
-- Q1 行类型分布：各类型行数、工时合计（declare_hours）、占比
SELECT win, ISNULL(CAST(type AS varchar(10)), 'NULL') AS row_type, COUNT(*) AS row_cnt,
       CAST(SUM(ISNULL(declare_hours, 0)) AS decimal(18, 1)) AS hours_sum,
       CAST(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (PARTITION BY win) AS decimal(6, 2)) AS row_pct,
       CAST(100.0 * SUM(ISNULL(declare_hours, 0)) / NULLIF(SUM(SUM(ISNULL(declare_hours, 0))) OVER (PARTITION BY win), 0) AS decimal(6, 2)) AS hours_pct
  FROM #LW
 GROUP BY win, type
 ORDER BY win, type;
GO
-- Q2a 每张工时单（按单头）含几种行类型：单据数与占比
SELECT win, tsheet_id,
       MAX(CASE WHEN type = 0 THEN 1 ELSE 0 END) AS h0, MAX(CASE WHEN type = 1 THEN 1 ELSE 0 END) AS h1,
       MAX(CASE WHEN type = 2 THEN 1 ELSE 0 END) AS h2, MAX(CASE WHEN type = 3 THEN 1 ELSE 0 END) AS h3,
       MAX(CASE WHEN type IS NULL THEN 1 ELSE 0 END) AS hn
  INTO #S
  FROM #LW
 GROUP BY win, tsheet_id;
GO
-- Q2b 组合分布（0 项目 1 售前 2 OH 3 混合 N 类型空）
SELECT win, n_types, COUNT(*) AS sheets,
       CAST(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (PARTITION BY win) AS decimal(6, 2)) AS sheets_pct
  FROM (SELECT win, h0 + h1 + h2 + h3 + hn AS n_types FROM #S) q
 GROUP BY win, n_types
 ORDER BY win, n_types;
GO
-- Q2c 各组合的单数
SELECT win, combo, COUNT(*) AS sheets
  FROM (SELECT win, CASE WHEN h0 = 1 THEN N'0' ELSE N'' END + CASE WHEN h1 = 1 THEN N'+1' ELSE N'' END + CASE WHEN h2 = 1 THEN N'+2' ELSE N'' END
               + CASE WHEN h3 = 1 THEN N'+3' ELSE N'' END + CASE WHEN hn = 1 THEN N'+N' ELSE N'' END AS combo FROM #S) q
 GROUP BY win, combo
 ORDER BY win, COUNT(*) DESC;
GO
-- Q3 行类型 x 工作包类型（0 项目任务 1 项目 2 商机任务 3 商机 4 线索 5 客户 6 拜访计划 7 其他）交叉行数
SELECT win, ISNULL(CAST(type AS varchar(10)), 'NULL') AS row_type, ISNULL(CAST(wpt AS varchar(10)), 'NULL') AS work_package_type,
       COUNT(*) AS row_cnt, CAST(SUM(ISNULL(declare_hours, 0)) AS decimal(18, 1)) AS hours_sum
  FROM #LW
 GROUP BY win, type, wpt
 ORDER BY win, type, wpt;
GO
-- Q4a 已进入审批的行（approve_status 1 / 2 / 3）的审批人核对（按行类型）：approval 为空、等于提交人本人、不同审批人个数；
--     规则核对 rule_* = 行上 approval 对比按类型推算的应审批人（0 项目经理四级回退；1 与 3 工作包所属部门负责人；2 提交人直属上级 system_users.leader_user_id）；
--     rule_na = 推算不出（源数据缺）；subdept_* = 对比提交人所在部门负责人（system_dept.leader_user_id）。
SELECT win, ISNULL(CAST(type AS varchar(10)), 'NULL') AS row_type, COUNT(*) AS rows_in_flow,
       SUM(CASE WHEN approval IS NULL THEN 1 ELSE 0 END) AS approval_null,
       SUM(CASE WHEN approval = person_id THEN 1 ELSE 0 END) AS approver_is_submitter,
       COUNT(DISTINCT approval) AS distinct_approvers,
       SUM(CASE WHEN approval IS NOT NULL AND exp_rule IS NOT NULL AND approval = exp_rule THEN 1 ELSE 0 END) AS rule_yes,
       SUM(CASE WHEN approval IS NOT NULL AND exp_rule IS NOT NULL AND approval <> exp_rule THEN 1 ELSE 0 END) AS rule_no,
       SUM(CASE WHEN exp_rule IS NULL THEN 1 ELSE 0 END) AS rule_na,
       SUM(CASE WHEN approval IS NOT NULL AND sub_dept_leader IS NOT NULL AND approval = sub_dept_leader THEN 1 ELSE 0 END) AS subdept_yes,
       SUM(CASE WHEN approval IS NOT NULL AND sub_dept_leader IS NOT NULL AND approval <> sub_dept_leader THEN 1 ELSE 0 END) AS subdept_no,
       SUM(CASE WHEN sub_dept_leader IS NULL THEN 1 ELSE 0 END) AS subdept_na,
       SUM(CASE WHEN approval IS NOT NULL AND sub_leader IS NOT NULL AND approval = sub_leader THEN 1 ELSE 0 END) AS directleader_yes,
       SUM(CASE WHEN approval IS NOT NULL AND sub_leader IS NOT NULL AND approval <> sub_leader THEN 1 ELSE 0 END) AS directleader_no,
       SUM(CASE WHEN sub_leader IS NULL THEN 1 ELSE 0 END) AS directleader_na
  FROM #LW
 WHERE st IN (1, 2, 3)
 GROUP BY win, type
 ORDER BY win, type;
GO
-- Q4b 同上口径下，各类型行审批人是提交人本人且 / 或提交人无直属上级的交叉（OH 行升级链是否被触发）
SELECT win, ISNULL(CAST(type AS varchar(10)), 'NULL') AS row_type,
       SUM(CASE WHEN approval = person_id AND sub_leader IS NULL THEN 1 ELSE 0 END) AS self_and_no_leader,
       SUM(CASE WHEN approval = person_id AND sub_leader IS NOT NULL THEN 1 ELSE 0 END) AS self_but_has_leader,
       SUM(CASE WHEN approval <> person_id AND sub_leader IS NULL THEN 1 ELSE 0 END) AS other_and_no_leader
  FROM #LW
 WHERE st IN (1, 2, 3)
 GROUP BY win, type
 ORDER BY win, type;
GO
-- Q4c 已进入审批的行中 approval 为空的行数按工时月份分布（看 approval 列自哪个月起被填）
SELECT CONVERT(char(7), works_date, 120) AS works_month, COUNT(*) AS rows_in_flow,
       SUM(CASE WHEN approval IS NULL THEN 1 ELSE 0 END) AS approval_null
  FROM #L
 WHERE st IN (1, 2, 3)
 GROUP BY CONVERT(char(7), works_date, 120)
 ORDER BY works_month;
GO
-- Q5 行审批结果分布（approve_status 各值，含空）：按行类型的行数与占比
SELECT win, ISNULL(CAST(type AS varchar(10)), 'NULL') AS row_type, ISNULL(CAST(st AS varchar(10)), 'NULL') AS row_approve_status,
       COUNT(*) AS row_cnt,
       CAST(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (PARTITION BY win, type) AS decimal(6, 2)) AS pct_within_type
  FROM #LW
 GROUP BY win, type, st
 ORDER BY win, type, st;
GO
-- Q5b 驳回率：已决行（状态 2 或 3）中驳回行占比，按行类型
SELECT win, ISNULL(CAST(type AS varchar(10)), 'NULL') AS row_type,
       SUM(CASE WHEN st IN (2, 3) THEN 1 ELSE 0 END) AS decided_rows,
       SUM(CASE WHEN st = 2 THEN 1 ELSE 0 END) AS rejected_rows,
       CAST(100.0 * SUM(CASE WHEN st = 2 THEN 1 ELSE 0 END) / NULLIF(SUM(CASE WHEN st IN (2, 3) THEN 1 ELSE 0 END), 0) AS decimal(6, 2)) AS reject_pct
  FROM #LW
 GROUP BY win, type
 ORDER BY win, type;
GO
-- Q6a bpm：TIMESHEET 近 180 天任务按流程定义版本分布（确认 v15 是否占全部）
SELECT SUBSTRING(PROC_DEF_ID_, 1, CHARINDEX(N':', PROC_DEF_ID_, CHARINDEX(N':', PROC_DEF_ID_) + 1) - 1) AS def_key_version, COUNT(*) AS task_cnt
  FROM [ruoyi-vue-pro].dbo.ACT_HI_TASKINST
 WHERE DB_NAME() = N'ruoyi-vue-pro' AND PROC_DEF_ID_ LIKE N'TIMESHEET:%' AND START_TIME_ >= DATEADD(DAY, -180, GETDATE())
 GROUP BY SUBSTRING(PROC_DEF_ID_, 1, CHARINDEX(N':', PROC_DEF_ID_, CHARINDEX(N':', PROC_DEF_ID_) + 1) - 1)
 ORDER BY def_key_version;
GO
-- Q6b bpm：v15 各节点近 180 天任务数、耗时分档（自然时间：创建到结束）、未完成数、不同办理人个数
--     节点 Activity_0g7oxfy = manager_id，Activity_01vbv92 = area_leader，Activity_0hwqdkm = 脚本 20，Activity_19wn7op = 发起人（prod_bpm.tsv:203-206）
SELECT CASE TASK_DEF_KEY_ WHEN N'Activity_0g7oxfy' THEN N'manager_id' WHEN N'Activity_01vbv92' THEN N'area_leader'
                          WHEN N'Activity_0hwqdkm' THEN N'script20' WHEN N'Activity_19wn7op' THEN N'initiator' ELSE N'other' END AS node,
       COUNT(*) AS task_cnt,
       SUM(CASE WHEN END_TIME_ IS NULL THEN 1 ELSE 0 END) AS unfinished,
       SUM(CASE WHEN END_TIME_ IS NOT NULL AND DATEDIFF(MINUTE, START_TIME_, END_TIME_) < 1440 THEN 1 ELSE 0 END) AS lt_24h,
       SUM(CASE WHEN END_TIME_ IS NOT NULL AND DATEDIFF(MINUTE, START_TIME_, END_TIME_) >= 1440 AND DATEDIFF(MINUTE, START_TIME_, END_TIME_) < 2880 THEN 1 ELSE 0 END) AS h24_48,
       SUM(CASE WHEN END_TIME_ IS NOT NULL AND DATEDIFF(MINUTE, START_TIME_, END_TIME_) >= 2880 AND DATEDIFF(MINUTE, START_TIME_, END_TIME_) < 7200 THEN 1 ELSE 0 END) AS d2_5,
       SUM(CASE WHEN END_TIME_ IS NOT NULL AND DATEDIFF(MINUTE, START_TIME_, END_TIME_) >= 7200 THEN 1 ELSE 0 END) AS ge_5d,
       COUNT(DISTINCT ASSIGNEE_) AS distinct_assignees
  FROM [ruoyi-vue-pro].dbo.ACT_HI_TASKINST
 WHERE DB_NAME() = N'ruoyi-vue-pro' AND PROC_DEF_ID_ LIKE N'TIMESHEET:15:%' AND START_TIME_ >= DATEADD(DAY, -180, GETDATE())
 GROUP BY CASE TASK_DEF_KEY_ WHEN N'Activity_0g7oxfy' THEN N'manager_id' WHEN N'Activity_01vbv92' THEN N'area_leader'
                             WHEN N'Activity_0hwqdkm' THEN N'script20' WHEN N'Activity_19wn7op' THEN N'initiator' ELSE N'other' END
 ORDER BY node;
GO
-- Q6c bpm：v15 各节点任务结果分布（bpm_task_ext.result：1 处理中 2 通过 3 不通过 4 取消 5 退回 6 知会）
SELECT CASE h.TASK_DEF_KEY_ WHEN N'Activity_0g7oxfy' THEN N'manager_id' WHEN N'Activity_01vbv92' THEN N'area_leader'
                            WHEN N'Activity_0hwqdkm' THEN N'script20' WHEN N'Activity_19wn7op' THEN N'initiator' ELSE N'other' END AS node,
       x.result AS task_result, COUNT(*) AS task_cnt
  FROM [ruoyi-vue-pro].dbo.ACT_HI_TASKINST h
  JOIN [ruoyi-vue-pro].dbo.bpm_task_ext x ON x.task_id COLLATE DATABASE_DEFAULT = h.ID_ COLLATE DATABASE_DEFAULT
 WHERE DB_NAME() = N'ruoyi-vue-pro' AND h.PROC_DEF_ID_ LIKE N'TIMESHEET:15:%' AND h.START_TIME_ >= DATEADD(DAY, -180, GETDATE())
 GROUP BY CASE h.TASK_DEF_KEY_ WHEN N'Activity_0g7oxfy' THEN N'manager_id' WHEN N'Activity_01vbv92' THEN N'area_leader'
                               WHEN N'Activity_0hwqdkm' THEN N'script20' WHEN N'Activity_19wn7op' THEN N'initiator' ELSE N'other' END, x.result
 ORDER BY node, task_result;
GO
-- Q6d bpm：TIMESHEET 近 180 天各版本 x 节点（节点键与节点名只是流程定义里的标签）的任务数、起止日期（只到日）、耗时分档
SELECT SUBSTRING(PROC_DEF_ID_, 1, CHARINDEX(N':', PROC_DEF_ID_, CHARINDEX(N':', PROC_DEF_ID_) + 1) - 1) AS def_key_version,
       TASK_DEF_KEY_ AS node_key, NAME_ AS node_name, COUNT(*) AS task_cnt,
       CONVERT(char(10), MIN(START_TIME_), 120) AS first_start, CONVERT(char(10), MAX(START_TIME_), 120) AS last_start,
       SUM(CASE WHEN END_TIME_ IS NULL THEN 1 ELSE 0 END) AS unfinished,
       SUM(CASE WHEN END_TIME_ IS NOT NULL AND DATEDIFF(MINUTE, START_TIME_, END_TIME_) < 1440 THEN 1 ELSE 0 END) AS lt_24h,
       SUM(CASE WHEN END_TIME_ IS NOT NULL AND DATEDIFF(MINUTE, START_TIME_, END_TIME_) >= 1440 AND DATEDIFF(MINUTE, START_TIME_, END_TIME_) < 2880 THEN 1 ELSE 0 END) AS h24_48,
       SUM(CASE WHEN END_TIME_ IS NOT NULL AND DATEDIFF(MINUTE, START_TIME_, END_TIME_) >= 2880 AND DATEDIFF(MINUTE, START_TIME_, END_TIME_) < 7200 THEN 1 ELSE 0 END) AS d2_5,
       SUM(CASE WHEN END_TIME_ IS NOT NULL AND DATEDIFF(MINUTE, START_TIME_, END_TIME_) >= 7200 THEN 1 ELSE 0 END) AS ge_5d
  FROM [ruoyi-vue-pro].dbo.ACT_HI_TASKINST
 WHERE DB_NAME() = N'ruoyi-vue-pro' AND PROC_DEF_ID_ LIKE N'TIMESHEET:%' AND START_TIME_ >= DATEADD(DAY, -180, GETDATE())
 GROUP BY SUBSTRING(PROC_DEF_ID_, 1, CHARINDEX(N':', PROC_DEF_ID_, CHARINDEX(N':', PROC_DEF_ID_) + 1) - 1), TASK_DEF_KEY_, NAME_
 ORDER BY def_key_version, task_cnt DESC;
GO
