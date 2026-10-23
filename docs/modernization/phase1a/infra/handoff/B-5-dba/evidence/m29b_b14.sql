-- M-29 基线补充（第二批）B14（one-rules 提供，来源 origin/claude/one-rules-context-2@943876a76 r0/m29b_baseline_supplement_queries.sql）。ops 仅加库名守卫与 SET LOCK_TIMEOUT 30000。one_evidence_ro（ADR-0037 第 5 项第 4 条），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- B14 据实结算项目 2025 年收入与直接费用（进损益口径）vs 公司全部项目
-- 依赖：psa_pm_contract_term.name / contract_id / deleted；psa_pm_contract.project_id / deleted；
--       psa_pm_cuvalue（project_id, complete_date, no_tax_amount, type）；psa_pl_ledger（project_id, source_type, pl_type, notax_amount, period_id, deleted）；mdm_accounting_period.period_year。
-- 口径：据实结算项目集合 = 有未删条款名含「据实结算」的未删合同所属项目（去重）。
--       收入 = type IN (0,1)、确认日在 2025（同 Q10）；成本 = 期间年 2025、pl_type IN (0,9)（同 Q20），按 source_type 分列：
--       ODC 日常 1、差旅 2、付款申请 3、特殊付款 4、招待 7；委外 5；居间 6；工时 0、12；奖金 9。比例列便于与公司全部项目对比。
--       a 行只含 2025 年有收入或有进损益成本的据实结算项目（project_cnt）；sel_project_cnt_all 为据实结算项目总数（含 2025 年无活动者）。
-- 不确定度：中。(1) 项目内非据实结算条款的收入与费用也在内，会稀释比例；(2) 项目数可能很小，结果可能能反推到具体项目，仅限内部使用，不外传；
--       (3) no_tax_amount 为空按 0（同 Q10）；(4) 公司全部项目行的项目含 project_id 对不上项目档案的行。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
WITH w AS (SELECT 2025 AS yr, CAST('20250101' AS date) AS y_from, CAST('20260101' AS date) AS y_to),
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
pl AS (
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
    FROM dbo.psa_pl_ledger l
    JOIN dbo.mdm_accounting_period ap ON ap.id = l.period_id
    CROSS JOIN w
    WHERE l.deleted = 0 AND l.pl_type IN (0, 9) AND ap.period_year = w.yr
    GROUP BY l.project_id
),
allp AS (SELECT project_id FROM rev UNION SELECT project_id FROM pl),
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
LEFT JOIN pl p ON p.project_id = g.project_id
GROUP BY g.scope
ORDER BY g.scope;
END
