-- 入职日期口径取证 ruoyi-vue-pro 部分（单独跑）
SET LOCK_TIMEOUT 1000;
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
-- ④ HR 与 system_users 按工号（ORG_MEMBER.CODE = system_users.code）对上
SELECT COUNT(*) AS hr_rows, SUM(CASE WHEN u.id IS NOT NULL THEN 1 ELSE 0 END) AS matched_by_code
  FROM a8_db.dbo.HR_STAFF_INFO h
  JOIN a8_db.dbo.ORG_MEMBER m ON m.ID = h.ORG_MEMBER_ID
  LEFT JOIN dbo.system_users u ON LTRIM(RTRIM(u.code)) COLLATE DATABASE_DEFAULT = LTRIM(RTRIM(m.CODE)) COLLATE DATABASE_DEFAULT AND NULLIF(LTRIM(RTRIM(u.code)), '') IS NOT NULL;
END
