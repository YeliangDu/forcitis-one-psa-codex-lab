-- DAT-1021-ARC-01 第一步：判别「跨法人 24 个月为 0」的成因。①列名清单（只出库、表、列、类型）②低基数聚合（公司代码、单位 ID 的计数，不含个人值）。以取证账号 one_evidence_ro 执行（ADR-0037）。
SET LOCK_TIMEOUT 1000;
SELECT DB_NAME() AS database_name, c.TABLE_NAME, c.COLUMN_NAME, c.DATA_TYPE
  FROM INFORMATION_SCHEMA.COLUMNS c
 WHERE c.TABLE_SCHEMA = N'dbo'
   AND ((DB_NAME() = N'ruoyi-vue-pro' AND (c.TABLE_NAME = N'system_users' OR LOWER(c.TABLE_NAME) LIKE '%employee%' OR LOWER(c.TABLE_NAME) LIKE '%staff%' OR LOWER(c.TABLE_NAME) LIKE '%hr%'))
     OR (DB_NAME() = N'a8_db' AND c.TABLE_NAME IN (N'ORG_MEMBER', N'HR_STAFF_INFO')))
 ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION;
SELECT DB_NAME() AS database_name, c.TABLE_NAME, c.COLUMN_NAME
  FROM INFORMATION_SCHEMA.COLUMNS c
 WHERE LOWER(c.COLUMN_NAME) LIKE '%id_card%' OR LOWER(c.COLUMN_NAME) LIKE '%idcard%' OR LOWER(c.COLUMN_NAME) LIKE '%cert%'
    OR LOWER(c.COLUMN_NAME) LIKE '%identity%' OR LOWER(c.COLUMN_NAME) LIKE '%emp_no%' OR LOWER(c.COLUMN_NAME) LIKE '%employee_no%'
    OR LOWER(c.COLUMN_NAME) LIKE '%job_no%' OR LOWER(c.COLUMN_NAME) LIKE '%staff_no%' OR LOWER(c.COLUMN_NAME) LIKE '%member_code%'
    OR LOWER(c.COLUMN_NAME) LIKE '%work_no%'
 ORDER BY c.TABLE_NAME, c.COLUMN_NAME;
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
SELECT company_code, COUNT(*) AS row_count, COUNT(DISTINCT user_id) AS distinct_users, MIN(start_date) AS start_date_min, MAX(end_date) AS end_date_max
  FROM dbo.psa_employee_transfer_record GROUP BY company_code ORDER BY company_code;
END
IF DB_NAME() = N'a8_db'
BEGIN
SELECT COUNT(DISTINCT ORG_ACCOUNT_ID) AS org_unit_distinct_account_ids FROM dbo.ORG_UNIT;
SELECT ORG_ACCOUNT_ID, COUNT(*) AS row_count, SUM(CASE WHEN IS_ENABLE = 0 THEN 1 ELSE 0 END) AS disabled_rows, SUM(CASE WHEN IS_DELETED <> 0 THEN 1 ELSE 0 END) AS deleted_rows
  FROM dbo.ORG_MEMBER GROUP BY ORG_ACCOUNT_ID ORDER BY ORG_ACCOUNT_ID;
END
