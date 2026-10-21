-- ADR-0073 U1 取数第二步 b：员工跨法人变动与组织单元法人变动的按月计数（one-data，2026-10-21）。
-- 只读 SELECT，账号 one_evidence_ro，SET LOCK_TIMEOUT 1000，不用 NOLOCK；输出只有月份与计数，不选任何姓名、工号、ID 明细。
-- 窗口：ruoyi 视图取 2024-11-01 至今（近 24 个完整月加当月）；A8 变动表 2023-12-28 后无记录，另取其最后 24 个月（2022-01 至 2023-12）作量级参照。
SET LOCK_TIMEOUT 1000;

-- Q1（ruoyi-vue-pro）：同一 user_id 相邻区间 company_code 不同 = 一次跨法人变动，记在新区间 start_date 所在月。
WITH r AS (
  SELECT user_id, company_code, start_date,
         LAG(company_code) OVER (PARTITION BY user_id ORDER BY start_date, end_date) AS prev_company
    FROM [ruoyi-vue-pro].dbo.psa_employee_transfer_record
)
SELECT FORMAT(start_date, 'yyyy-MM') AS ym,
       SUM(CASE WHEN prev_company IS NOT NULL AND company_code <> prev_company THEN 1 ELSE 0 END)       AS cross_entity_changes,
       COUNT(DISTINCT CASE WHEN prev_company IS NOT NULL AND company_code <> prev_company THEN user_id END) AS people_changed,
       SUM(CASE WHEN prev_company IS NOT NULL AND company_code = prev_company THEN 1 ELSE 0 END)         AS same_entity_transfers,
       SUM(CASE WHEN company_code IS NULL THEN 1 ELSE 0 END) AS rows_without_company
  FROM r
 WHERE start_date >= '2024-11-01'
 GROUP BY FORMAT(start_date, 'yyyy-MM')
 ORDER BY ym;

-- Q2（a8_db，量级参照）：ODS_MEMBER_CHANGE 同一 MEMBER_ID 按 CHANGE_TIME 相邻两条的 ORG_ACCOUNT_ID 不同。
WITH m AS (
  SELECT MEMBER_ID, ORG_ACCOUNT_ID, CHANGE_TIME,
         LAG(ORG_ACCOUNT_ID) OVER (PARTITION BY MEMBER_ID ORDER BY CHANGE_TIME, ID) AS prev_account
    FROM a8_db.dbo.ODS_MEMBER_CHANGE
)
SELECT FORMAT(CHANGE_TIME, 'yyyy-MM') AS ym,
       SUM(CASE WHEN prev_account IS NOT NULL AND ORG_ACCOUNT_ID <> prev_account THEN 1 ELSE 0 END) AS cross_entity_changes,
       COUNT(*) AS change_rows
  FROM m
 WHERE CHANGE_TIME >= '2022-01-01' AND CHANGE_TIME < '2024-01-01'
 GROUP BY FORMAT(CHANGE_TIME, 'yyyy-MM')
 ORDER BY ym;

-- Q3（a8_db，量级参照）：ODS_ORG_CHANGE 同一 ORG_ID 按 CHANGE_TIME 相邻两条的 ORG_ACCOUNT_ID 不同 = 组织单元所属单位变动。
WITH o AS (
  SELECT ORG_ID, ORG_ACCOUNT_ID, CHANGE_TIME,
         LAG(ORG_ACCOUNT_ID) OVER (PARTITION BY ORG_ID ORDER BY CHANGE_TIME, ID) AS prev_account
    FROM a8_db.dbo.ODS_ORG_CHANGE
)
SELECT FORMAT(CHANGE_TIME, 'yyyy-MM') AS ym,
       SUM(CASE WHEN prev_account IS NOT NULL AND ORG_ACCOUNT_ID <> prev_account THEN 1 ELSE 0 END) AS org_entity_changes,
       COUNT(*) AS change_rows
  FROM o
 WHERE CHANGE_TIME >= '2022-01-01' AND CHANGE_TIME < '2024-01-01'
 GROUP BY FORMAT(CHANGE_TIME, 'yyyy-MM')
 ORDER BY ym;
