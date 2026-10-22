-- DAT-1021-ARC-01 第二步（one-data，2026-10-21）：判别「跨法人 24 个月为 0」的成因。
-- 事实：视图 psa_employee_transfer_record 只有 company_code = 1001 一个取值，所以视图里本来就不可能出现跨法人。改查 system_users 自带的 company_code。
-- 只读 SELECT，one_evidence_ro，SET LOCK_TIMEOUT 1000，不用 NOLOCK；输出只有公司代码、月份与计数、间隔分桶，不选任何工号、姓名、证件号。
SET LOCK_TIMEOUT 1000;

-- Q1 公司代码分布：每个公司代码的账号数、已删除数、停用数（status<>0 视为停用，口径以 one-ops 核实为准）、不同工号数。
SELECT company_code, COUNT(*) AS accounts,
       SUM(CASE WHEN deleted = 1 THEN 1 ELSE 0 END) AS deleted_accounts,
       SUM(CASE WHEN status <> 0 THEN 1 ELSE 0 END) AS disabled_accounts,
       COUNT(DISTINCT NULLIF(LTRIM(RTRIM(code)), '')) AS distinct_codes
  FROM [ruoyi-vue-pro].dbo.system_users
 GROUP BY company_code
 ORDER BY accounts DESC;

-- Q2 同一工号出现在两个以上公司代码下的人数（成因 c 的直接证据；工号为空的账号不计）。
WITH k AS (
  SELECT NULLIF(LTRIM(RTRIM(code)), '') AS person_key, company_code, create_time, update_time, deleted, status
    FROM [ruoyi-vue-pro].dbo.system_users
)
SELECT COUNT(*) AS people_with_multiple_company_codes
  FROM (SELECT person_key FROM k WHERE person_key IS NOT NULL GROUP BY person_key HAVING COUNT(DISTINCT company_code) > 1) AS t;

-- Q3 若 Q2 > 0：按「后一个账号建立月」计数，并按（前一账号最后更新 → 后一账号建立）的间隔分桶：0 天、1–7 天、更长、间隔为负（先建后停）。
WITH k AS (
  SELECT NULLIF(LTRIM(RTRIM(code)), '') AS person_key, company_code, create_time, update_time,
         LAG(company_code) OVER (PARTITION BY NULLIF(LTRIM(RTRIM(code)), '') ORDER BY create_time) AS prev_company,
         LAG(update_time)  OVER (PARTITION BY NULLIF(LTRIM(RTRIM(code)), '') ORDER BY create_time) AS prev_update
    FROM [ruoyi-vue-pro].dbo.system_users
   WHERE NULLIF(LTRIM(RTRIM(code)), '') IS NOT NULL
)
SELECT FORMAT(create_time, 'yyyy-MM') AS ym,
       SUM(CASE WHEN DATEDIFF(day, prev_update, create_time) < 0 THEN 1 ELSE 0 END)  AS gap_negative,
       SUM(CASE WHEN DATEDIFF(day, prev_update, create_time) = 0 THEN 1 ELSE 0 END)  AS gap_0_days,
       SUM(CASE WHEN DATEDIFF(day, prev_update, create_time) BETWEEN 1 AND 7 THEN 1 ELSE 0 END) AS gap_1_7_days,
       SUM(CASE WHEN DATEDIFF(day, prev_update, create_time) > 7 THEN 1 ELSE 0 END)  AS gap_longer
  FROM k
 WHERE prev_company IS NOT NULL AND company_code <> prev_company AND create_time >= '2024-11-01'
 GROUP BY FORMAT(create_time, 'yyyy-MM')
 ORDER BY ym;

-- Q4 同一问题的全历史总数（不限 24 个月），判断是否有过：同上口径只出一个数。
WITH k AS (
  SELECT NULLIF(LTRIM(RTRIM(code)), '') AS person_key, company_code,
         LAG(company_code) OVER (PARTITION BY NULLIF(LTRIM(RTRIM(code)), '') ORDER BY create_time) AS prev_company
    FROM [ruoyi-vue-pro].dbo.system_users
)
SELECT COUNT(*) AS all_time_company_code_changes_by_code FROM k WHERE person_key IS NOT NULL AND prev_company IS NOT NULL AND company_code <> prev_company;
