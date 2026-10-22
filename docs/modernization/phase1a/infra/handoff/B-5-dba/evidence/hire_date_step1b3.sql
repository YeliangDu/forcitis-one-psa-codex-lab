-- 入职日期口径取证 ruoyi-vue-pro 部分（单独跑）
SET LOCK_TIMEOUT 1000;
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
-- ③ 视图每个 user_id 的最早 start_date
WITH e AS (SELECT user_id, MIN(start_date) AS first_start FROM dbo.psa_employee_transfer_record GROUP BY user_id)
SELECT YEAR(first_start) AS first_start_year, COUNT(*) AS users FROM e GROUP BY YEAR(first_start) ORDER BY first_start_year;
WITH e AS (SELECT user_id, MIN(start_date) AS first_start FROM dbo.psa_employee_transfer_record GROUP BY user_id)
SELECT COUNT(*) AS users, MIN(first_start) AS earliest_of_first_start, MAX(first_start) AS latest_of_first_start FROM e;
SELECT YEAR(create_time) AS create_year, COUNT(*) AS active_accounts FROM dbo.system_users WHERE deleted = '0' GROUP BY YEAR(create_time) ORDER BY create_year;
END
