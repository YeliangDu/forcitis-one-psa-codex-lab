-- 入职日期口径取证 ruoyi-vue-pro 部分（单独跑）
SET LOCK_TIMEOUT 1000;
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
-- 视图 user_id 与 system_users 的对应键探测：user_id 等于 id 或 code 的个数
SELECT COUNT(*) AS users, SUM(CASE WHEN i.id_hit = 1 THEN 1 ELSE 0 END) AS user_id_matches_id, SUM(CASE WHEN i.code_hit = 1 THEN 1 ELSE 0 END) AS user_id_matches_code
  FROM (SELECT e.user_id,
               (SELECT COUNT(*) FROM dbo.system_users s WHERE CAST(s.id AS nvarchar(40)) COLLATE DATABASE_DEFAULT = e.user_id COLLATE DATABASE_DEFAULT) AS id_hit,
               (SELECT COUNT(*) FROM dbo.system_users s WHERE LTRIM(RTRIM(s.code)) COLLATE DATABASE_DEFAULT = e.user_id COLLATE DATABASE_DEFAULT) AS code_hit
          FROM (SELECT DISTINCT user_id FROM dbo.psa_employee_transfer_record) e) i;
END
