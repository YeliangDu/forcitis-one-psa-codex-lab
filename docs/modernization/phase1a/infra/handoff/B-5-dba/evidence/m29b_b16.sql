-- M-29 基线补充（第二批）B16（one-rules 提供，来源 origin/claude/one-rules-context-2@943876a76 r0/m29b_baseline_supplement_queries.sql）。ops 仅加库名守卫与 SET LOCK_TIMEOUT 30000。one_evidence_ro（ADR-0037 第 5 项第 4 条），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- B16 条款级备选（收入）：产值行上的条款名称含「据实结算」的 2025 年收入 vs 其他条款
-- 依赖：psa_pm_cuvalue.term_depict（合同条款名称，CuValueDO 注释）、no_tax_amount、type、complete_date、project_id。
-- 用途：不经合同表，直接看收入确认行上带条款名的部分。与 B14 的项目级结果对照（条款级更窄）。
-- 不确定度：中偏高。term_depict 存量可能为空或不是合同条款名原文；两条路径命中的条款集合可能不同。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
WITH w AS (SELECT CAST('20250101' AS date) AS y_from, CAST('20260101' AS date) AS y_to)
SELECT CASE WHEN cu.term_depict LIKE N'%据实结算%' THEN N'a_条款名含据实结算' ELSE N'b_其他' END AS term_class,
       COUNT_BIG(*) AS row_cnt, COUNT(DISTINCT cu.project_id) AS project_cnt,
       SUM(CASE WHEN cu.term_depict IS NULL THEN 1 ELSE 0 END) AS term_depict_null_cnt,
       SUM(ISNULL(cu.no_tax_amount, 0)) AS rev_notax
FROM dbo.psa_pm_cuvalue cu CROSS JOIN w
WHERE cu.[type] IN (0, 1) AND cu.complete_date >= w.y_from AND cu.complete_date < w.y_to
GROUP BY CASE WHEN cu.term_depict LIKE N'%据实结算%' THEN N'a_条款名含据实结算' ELSE N'b_其他' END
ORDER BY term_class;
END
