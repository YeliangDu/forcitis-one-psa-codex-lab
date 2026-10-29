-- DAT-1021-ARC-01 第二步前置：核 system_users 的 deleted、status 取值口径（低基数枚举的分组计数，不含个人值）。以取证账号 one_evidence_ro 执行（ADR-0037）。
SET LOCK_TIMEOUT 1000;
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
SELECT deleted, status, COUNT(*) AS accounts FROM dbo.system_users GROUP BY deleted, status ORDER BY deleted, status;
SELECT COUNT(*) AS accounts, SUM(CASE WHEN code IS NULL OR LTRIM(RTRIM(code)) = N'' THEN 1 ELSE 0 END) AS accounts_without_code,
       COUNT(DISTINCT company_code) AS company_codes, MIN(create_time) AS create_time_min, MAX(create_time) AS create_time_max FROM dbo.system_users;
END
