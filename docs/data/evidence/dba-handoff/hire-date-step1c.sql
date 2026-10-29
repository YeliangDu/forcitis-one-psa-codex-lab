-- 入职日期口径取证 ④：HR_STAFF_INFO.WORK_STARTING_DATE 与视图最早 start_date 的差距分桶（只出个数）。匹配键：HR.ORG_MEMBER_ID → ORG_MEMBER.CODE = system_users.code；视图 user_id = system_users.id。one_evidence_ro（ADR-0037 第 7 项）。仅 ruoyi-vue-pro 出结果。
SET LOCK_TIMEOUT 1000;
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
WITH first_start AS (SELECT user_id, MIN(start_date) AS first_start FROM dbo.psa_employee_transfer_record GROUP BY user_id),
j AS (
  SELECT h.WORK_STARTING_DATE AS hr_date, f.first_start
    FROM a8_db.dbo.HR_STAFF_INFO h
    JOIN a8_db.dbo.ORG_MEMBER m ON m.ID = h.ORG_MEMBER_ID
    JOIN dbo.system_users u ON LTRIM(RTRIM(u.code)) COLLATE DATABASE_DEFAULT = LTRIM(RTRIM(m.CODE)) COLLATE DATABASE_DEFAULT AND NULLIF(LTRIM(RTRIM(u.code)), '') IS NOT NULL
    JOIN first_start f ON f.user_id COLLATE DATABASE_DEFAULT = CAST(u.id AS nvarchar(40)) COLLATE DATABASE_DEFAULT)
SELECT COUNT(*) AS matched_to_view,
       SUM(CASE WHEN hr_date IS NULL THEN 1 ELSE 0 END) AS hr_date_null,
       SUM(CASE WHEN hr_date IS NOT NULL AND ABS(DATEDIFF(day, hr_date, first_start)) = 0 THEN 1 ELSE 0 END) AS diff_0_days,
       SUM(CASE WHEN hr_date IS NOT NULL AND ABS(DATEDIFF(day, hr_date, first_start)) BETWEEN 1 AND 30 THEN 1 ELSE 0 END) AS diff_1_30_days,
       SUM(CASE WHEN hr_date IS NOT NULL AND ABS(DATEDIFF(day, hr_date, first_start)) > 30 THEN 1 ELSE 0 END) AS diff_over_30_days,
       SUM(CASE WHEN hr_date IS NOT NULL AND hr_date < first_start THEN 1 ELSE 0 END) AS hr_date_earlier,
       SUM(CASE WHEN hr_date IS NOT NULL AND hr_date > first_start THEN 1 ELSE 0 END) AS hr_date_later
  FROM j;
END
