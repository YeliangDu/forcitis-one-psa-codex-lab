-- M-29 贡献毛利率分母改净服务收入前后值取证（one-rules 提供，来源 origin/claude/one-rules-context-2@3653a70e8 r0/m29_baseline_readonly_queries.sql）：Q30
-- ops 改动：每条外包 IF DB_NAME()=ruoyi-vue-pro；加 SET LOCK_TIMEOUT 30000；Q01 补一个缺失的右括号。one_evidence_ro（ADR-0037 第 7 项），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- Q30 委外费预提明细（老系统委外单据表，与成本台账 source_type=5 对账）：（窗口 × 上级部门 × 交付部门）及合计
-- 依赖：psa_wip_outsourcing（WIPOutsourcingDO：dept_id, complete_date, amount, notax_amount, project_write_off_flag）；system_dept。
-- 口径：窗口按 complete_date（委外交付确认日）；dept_id 为交付部门，直接取，不经项目。amount 含税、notax_amount 不含税。
-- 不确定度：中。notax_amount 在 DO 里默认 0，存量行可能未回填（生产形态未核实）：notax_zero_cnt 报告其个数。
--       project_write_off_flag（0 未结转、1 已结转）把「已确认未结转」与「已结转」分开。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
WITH w AS (
    SELECT CAST('20250101' AS date) AS a_from, CAST('20260101' AS date) AS a_to,
           CAST('20260101' AS date) AS b_from, DATEADD(DAY, 1, CAST(GETDATE() AS date)) AS b_to
),
dd AS (
    SELECT sd.id AS dept_id,
           ISNULL(sp.name, N'(无上级部门)') AS parent_name,
           ISNULL(sd.name, N'(无部门)') AS dept_name
    FROM dbo.system_dept sd
    LEFT JOIN dbo.system_dept sp ON sp.id = sd.parent_id
)
SELECT x.win_label,
       GROUPING_ID(dd.parent_name, dd.dept_name) AS lvl,
       dd.parent_name, dd.dept_name,
       COUNT_BIG(*) AS row_cnt,
       COUNT(DISTINCT o.project_id) AS project_cnt,
       SUM(ISNULL(o.amount, 0)) AS amount_gross,
       SUM(ISNULL(o.notax_amount, 0)) AS amount_notax,
       SUM(CASE WHEN ISNULL(o.notax_amount, 0) = 0 THEN 1 ELSE 0 END) AS notax_zero_cnt,
       SUM(CASE WHEN o.project_write_off_flag = 1 THEN ISNULL(o.notax_amount, 0) ELSE 0 END) AS notax_carried
FROM dbo.psa_wip_outsourcing o
CROSS JOIN w
LEFT JOIN dd ON dd.dept_id = o.dept_id
CROSS APPLY (SELECT CASE WHEN o.complete_date >= w.a_from AND o.complete_date < w.a_to THEN N'a_2025整年'
                         WHEN o.complete_date >= w.b_from AND o.complete_date < w.b_to THEN N'b_2026至今' END AS win_label) x
WHERE o.complete_date >= w.a_from AND o.complete_date < w.b_to
GROUP BY x.win_label, ROLLUP(dd.parent_name, dd.dept_name)
ORDER BY x.win_label, dd.parent_name, dd.dept_name;
END
