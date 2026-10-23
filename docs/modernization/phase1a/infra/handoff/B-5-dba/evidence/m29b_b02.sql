-- M-29 基线补充（第二批）B02（one-rules 提供，来源 origin/claude/one-rules-context-2@943876a76 r0/m29b_baseline_supplement_queries.sql）。ops 仅加库名守卫与 SET LOCK_TIMEOUT 30000。one_evidence_ro（ADR-0037 第 5 项第 4 条），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- B02 奖金源视图是否在本库可见：annual_bonus_detail、monthly_bonus_accrual（只取对象名与列名）
-- 依据：两个视图是 OA 侧建的、非 Flyway（AnnualBonusDetailViewDO / MonthlyBonusAccrualViewDO 注释）；是否在 ruoyi-vue-pro 库内可见未核实。
-- 用途：决定 B11、B12 能不能跑。不含 level 列（物理列名带不可见字符），其余列照出。
-- 不确定度：中。可能在别的库或别的 schema（本条不限 schema，把 schema 带出来）。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
SELECT c.TABLE_SCHEMA, c.TABLE_NAME, c.COLUMN_NAME, c.DATA_TYPE
FROM INFORMATION_SCHEMA.COLUMNS c
WHERE c.TABLE_NAME IN (N'annual_bonus_detail', N'monthly_bonus_accrual')
ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION;
END
