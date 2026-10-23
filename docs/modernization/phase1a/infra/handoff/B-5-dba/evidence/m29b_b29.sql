-- M-29 基线补充（第二批）B29（one-rules 提供，来源 origin/claude/one-rules-context-2@943876a76 r0/m29b_baseline_supplement_queries.sql）。ops 仅加库名守卫与 SET LOCK_TIMEOUT 30000。one_evidence_ro（ADR-0037 第 5 项第 4 条），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- B29 项目状态值分布（校验 B26～B27 里写死的三个状态值）：状态值 × 项目数 × 2025 年有收入的项目数
-- 依赖：psa_pm_project.status；psa_pm_cuvalue（project_id, complete_date, type）。
-- 用途：状态值是 bigint 哈希（来自 OA 枚举），本条只出值与计数；运维核对「值 -3117158260873541206 / -5057727321925005625 / -1296737353346166958」是否存在，
--       并凭业务常识对应到已完成 / 已关闭 / 终止（标签不在本库，本条不取标签）。
-- 不确定度：低。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
WITH w AS (SELECT CAST('20250101' AS date) AS y_from, CAST('20260101' AS date) AS y_to),
rev AS (
    SELECT cu.project_id
    FROM dbo.psa_pm_cuvalue cu CROSS JOIN w
    WHERE cu.[type] IN (0, 1) AND cu.complete_date >= w.y_from AND cu.complete_date < w.y_to
    GROUP BY cu.project_id
)
SELECT p.status,
       COUNT_BIG(*) AS project_cnt,
       SUM(CASE WHEN r.project_id IS NOT NULL THEN 1 ELSE 0 END) AS project_with_rev_2025_cnt
FROM dbo.psa_pm_project p
LEFT JOIN rev r ON r.project_id = p.id
GROUP BY p.status
ORDER BY COUNT_BIG(*) DESC;
END
