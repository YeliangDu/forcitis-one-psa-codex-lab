-- M-29 基线补充（第二批）B15（one-rules 提供，来源 origin/claude/one-rules-context-2@943876a76 r0/m29b_baseline_supplement_queries.sql）。ops 仅加库名守卫与 SET LOCK_TIMEOUT 30000。one_evidence_ro（ADR-0037 第 5 项第 4 条），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- B15 同 B14，费用改取发生口径（成本台账）
-- 依赖：同 B14，但费用取 psa_cost_ledger（project_id, source_type, notax_amount, business_date, entry_status, deleted）。
-- 口径：entry_status = 0、business_date 在 2025；收入同 B14。
-- 不确定度：中。成本台账发生口径与进损益口径差异大（见 M29_BASELINE_RESULT.md），两条并看。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
WITH w AS (SELECT CAST('20250101' AS date) AS y_from, CAST('20260101' AS date) AS y_to),
sel AS (
    SELECT DISTINCT c.project_id
    FROM dbo.psa_pm_contract_term t
    JOIN dbo.psa_pm_contract c ON c.id = t.contract_id AND c.deleted = 0
    WHERE t.deleted = 0 AND t.name LIKE N'%据实结算%' AND c.project_id IS NOT NULL
),
rev AS (
    SELECT cu.project_id, SUM(ISNULL(cu.no_tax_amount, 0)) AS rev_notax
    FROM dbo.psa_pm_cuvalue cu CROSS JOIN w
    WHERE cu.[type] IN (0, 1) AND cu.complete_date >= w.y_from AND cu.complete_date < w.y_to
    GROUP BY cu.project_id
),
cl AS (
    SELECT l.project_id,
           SUM(CASE WHEN l.source_type = 1 THEN l.notax_amount ELSE 0 END) AS odc_daily,
           SUM(CASE WHEN l.source_type = 2 THEN l.notax_amount ELSE 0 END) AS odc_travel,
           SUM(CASE WHEN l.source_type = 3 THEN l.notax_amount ELSE 0 END) AS odc_payreq,
           SUM(CASE WHEN l.source_type = 4 THEN l.notax_amount ELSE 0 END) AS odc_special,
           SUM(CASE WHEN l.source_type = 7 THEN l.notax_amount ELSE 0 END) AS odc_hospitality,
           SUM(CASE WHEN l.source_type = 5 THEN l.notax_amount ELSE 0 END) AS outsourcing,
           SUM(CASE WHEN l.source_type = 6 THEN l.notax_amount ELSE 0 END) AS info_service,
           SUM(CASE WHEN l.source_type IN (0, 12) THEN l.notax_amount ELSE 0 END) AS dl_hours,
           SUM(CASE WHEN l.source_type = 9 THEN l.notax_amount ELSE 0 END) AS dl_bonus
    FROM dbo.psa_cost_ledger l CROSS JOIN w
    WHERE l.deleted = 0 AND l.entry_status = 0 AND l.business_date >= w.y_from AND l.business_date < w.y_to
    GROUP BY l.project_id
),
allp AS (SELECT project_id FROM rev UNION SELECT project_id FROM cl),
grp AS (
    SELECT N'a_据实结算项目' AS scope, a.project_id FROM allp a JOIN sel ON sel.project_id = a.project_id
    UNION ALL
    SELECT N'b_公司全部项目', a.project_id FROM allp a
)
SELECT g.scope,
       (SELECT COUNT_BIG(*) FROM sel) AS sel_project_cnt_all,
       COUNT_BIG(*) AS project_cnt,
       SUM(CASE WHEN r.project_id IS NOT NULL THEN 1 ELSE 0 END) AS project_with_rev_cnt,
       SUM(ISNULL(r.rev_notax, 0)) AS rev_notax,
       SUM(ISNULL(p.odc_daily, 0)) AS odc_daily, SUM(ISNULL(p.odc_travel, 0)) AS odc_travel,
       SUM(ISNULL(p.odc_payreq, 0)) AS odc_payreq, SUM(ISNULL(p.odc_special, 0)) AS odc_special,
       SUM(ISNULL(p.odc_hospitality, 0)) AS odc_hospitality,
       SUM(ISNULL(p.odc_daily, 0) + ISNULL(p.odc_travel, 0) + ISNULL(p.odc_payreq, 0) + ISNULL(p.odc_special, 0) + ISNULL(p.odc_hospitality, 0)) AS odc_total,
       SUM(ISNULL(p.outsourcing, 0)) AS outsourcing, SUM(ISNULL(p.info_service, 0)) AS info_service,
       SUM(ISNULL(p.dl_hours, 0)) AS dl_hours, SUM(ISNULL(p.dl_bonus, 0)) AS dl_bonus,
       SUM(ISNULL(p.odc_daily, 0) + ISNULL(p.odc_travel, 0) + ISNULL(p.odc_payreq, 0) + ISNULL(p.odc_special, 0) + ISNULL(p.odc_hospitality, 0))
         / NULLIF(SUM(ISNULL(r.rev_notax, 0)), 0) AS odc_to_rev_ratio,
       SUM(ISNULL(p.outsourcing, 0)) / NULLIF(SUM(ISNULL(r.rev_notax, 0)), 0) AS outsourcing_to_rev_ratio
FROM grp g
LEFT JOIN rev r ON r.project_id = g.project_id
LEFT JOIN cl p ON p.project_id = g.project_id
GROUP BY g.scope
ORDER BY g.scope;
END
