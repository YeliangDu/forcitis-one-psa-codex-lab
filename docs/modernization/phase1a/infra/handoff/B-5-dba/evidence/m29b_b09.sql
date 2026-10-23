-- M-29 基线补充（第二批）B09（one-rules 提供，来源 origin/claude/one-rules-context-2@943876a76 r0/m29b_baseline_supplement_queries.sql）。ops 仅加库名守卫与 SET LOCK_TIMEOUT 30000。one_evidence_ro（ADR-0037 第 7 项），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- B09 奖金成本台账行的结构与源单列覆盖（只出计数）
-- 依赖：psa_cost_ledger（source_type, fee_type, entry_type, entry_status, source_id, source_code, person_id, term_number, approval_hours, project_id, business_date）。
-- 用途：(1) rows 对 src_cnt 的倍数 = 一张奖金单被拆成几条项目×条款行；(2) source_code / person_id 非空率 = 能否回到奖金源单、再到源单上的月份信息
--       （月份信息只可能在 OA 源视图，不在本表）；(3) fee_type 分布确认挂项目行只有 0。
-- 不确定度：低。人员只出个数（COUNT DISTINCT person_id），不出 id。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
SELECT YEAR(l.business_date) AS biz_year, l.source_type, l.fee_type, l.entry_type, l.entry_status,
       COUNT_BIG(*) AS row_cnt,
       COUNT(DISTINCT l.source_id) AS source_cnt,
       COUNT(DISTINCT l.person_id) AS person_cnt,
       COUNT(DISTINCT l.project_id) AS project_cnt,
       SUM(CASE WHEN l.source_code IS NULL THEN 1 ELSE 0 END) AS source_code_null_cnt,
       SUM(CASE WHEN l.person_id IS NULL THEN 1 ELSE 0 END) AS person_null_cnt,
       SUM(CASE WHEN l.term_number IS NULL THEN 1 ELSE 0 END) AS term_null_cnt,
       SUM(CASE WHEN l.approval_hours IS NULL THEN 1 ELSE 0 END) AS hours_null_cnt,
       SUM(l.notax_amount) AS notax_sum
FROM dbo.psa_cost_ledger l
WHERE l.deleted = 0 AND l.source_type IN (8, 9)
GROUP BY YEAR(l.business_date), l.source_type, l.fee_type, l.entry_type, l.entry_status
ORDER BY YEAR(l.business_date), l.source_type, l.fee_type, l.entry_type, l.entry_status;
END
