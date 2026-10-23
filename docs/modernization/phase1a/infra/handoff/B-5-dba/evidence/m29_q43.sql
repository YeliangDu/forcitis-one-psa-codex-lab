-- M-29 贡献毛利率分母改净服务收入前后值取证（one-rules 提供，来源 origin/claude/one-rules-context-2@3653a70e8 r0/m29_baseline_readonly_queries.sql）：Q43
-- ops 改动：每条外包 IF DB_NAME()=ruoyi-vue-pro；加 SET LOCK_TIMEOUT 30000；Q01 补一个缺失的右括号。one_evidence_ro（ADR-0037 第 5 项第 4 条），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- Q43 列名探测：业务库里与「可转收/代垫/计费」相关的列（只取元数据，不取数据）
-- 依赖：INFORMATION_SCHEMA.COLUMNS。
-- 用途：Q41/Q42 没命中时，看有没有专门的标识列（如 reimbursable、billable、advance、rebill）。
-- 不确定度：高（列名猜测）。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = N'dbo'
  AND (TABLE_NAME LIKE N'psa[_]cost[_]%' OR TABLE_NAME LIKE N'psa[_]pm[_]%' OR TABLE_NAME LIKE N'psa[_]wip[_]%'
       OR TABLE_NAME = N'psa_opportunity_expense' OR TABLE_NAME LIKE N'psa[_]term[_]%')
  AND (COLUMN_NAME LIKE N'%reimb%' OR COLUMN_NAME LIKE N'%advance%' OR COLUMN_NAME LIKE N'%rebill%'
       OR COLUMN_NAME LIKE N'%billable%' OR COLUMN_NAME LIKE N'%pass%' OR COLUMN_NAME LIKE N'%charge%'
       OR COLUMN_NAME LIKE N'%customer_bear%' OR COLUMN_NAME LIKE N'%client%')
ORDER BY TABLE_NAME, COLUMN_NAME;
END
