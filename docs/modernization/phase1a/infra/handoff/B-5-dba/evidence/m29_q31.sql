-- M-29 贡献毛利率分母改净服务收入前后值取证（one-rules 提供，来源 origin/claude/one-rules-context-2@3653a70e8 r0/m29_baseline_readonly_queries.sql）：Q31
-- ops 改动：每条外包 IF DB_NAME()=ruoyi-vue-pro；加 SET LOCK_TIMEOUT 30000；Q01 补一个缺失的右括号。one_evidence_ro（ADR-0037 第 7 项），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- Q31 居间费预提明细（对照：词表定为非转付成本，仅确认 Q20 INFO居间 类量级）：公司合计，按窗口
-- 依赖：psa_wip_commission（WIPCommissionDO：record_date, amount, notax_amount）。
-- 不确定度：中。record_date 为居间记录日期；是否与 source_type=6（信息服务）同一事实未核。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
WITH w AS (
    SELECT CAST('20250101' AS date) AS a_from, CAST('20260101' AS date) AS a_to,
           CAST('20260101' AS date) AS b_from, DATEADD(DAY, 1, CAST(GETDATE() AS date)) AS b_to
)
SELECT x.win_label, COUNT_BIG(*) AS row_cnt,
       SUM(ISNULL(m.amount, 0)) AS amount_gross, SUM(ISNULL(m.notax_amount, 0)) AS amount_notax
FROM dbo.psa_wip_commission m
CROSS JOIN w
CROSS APPLY (SELECT CASE WHEN m.record_date >= w.a_from AND m.record_date < w.a_to THEN N'a_2025整年'
                         WHEN m.record_date >= w.b_from AND m.record_date < w.b_to THEN N'b_2026至今' END AS win_label) x
WHERE m.record_date >= w.a_from AND m.record_date < w.b_to
GROUP BY x.win_label
ORDER BY x.win_label;
END
