-- DAT-1021-ARC-01 第三步（one-data，2026-10-21）：system_users 中 company_code 为空的 52 个账号，员工法人归属能否从别处对上（ADR-0022 的五家法人）。
-- 只读，one_evidence_ro，SET LOCK_TIMEOUT 1000，不用 NOLOCK；输出只有计数与公司代码/单位 ID 分布（不是个人信息），不选工号、姓名。
-- 来源 origin/claude/one-data-handoff-1（one-data）；ops 仅加库名守卫（工具对两库各跑一次），Q8 因工具禁 EXEC 分两遍：本文件只列表名，逐表聚合见 dat1021_arc01_s3_q8.sql。
SET LOCK_TIMEOUT 1000;

IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
-- Q5 空值账号中，与某个有公司代码的账号同工号的有几个（工号为空的不计）。
SELECT COUNT(*) AS null_company_accounts_sharing_code_with_coded_account
  FROM [ruoyi-vue-pro].dbo.system_users n
 WHERE n.company_code IS NULL AND NULLIF(LTRIM(RTRIM(n.code)), '') IS NOT NULL
   AND EXISTS (SELECT 1 FROM [ruoyi-vue-pro].dbo.system_users c
                WHERE c.company_code IS NOT NULL AND LTRIM(RTRIM(c.code)) = LTRIM(RTRIM(n.code)));

-- Q6（两库排序规则可能不同，两侧加 COLLATE DATABASE_DEFAULT，语义不变）空值账号与 A8 人员按工号对上的个数，及对上的人在 A8 的单位分布（单位 ID 不是个人信息）。
SELECT m.ORG_ACCOUNT_ID, COUNT(*) AS matched_null_company_accounts
  FROM [ruoyi-vue-pro].dbo.system_users n
  JOIN a8_db.dbo.ORG_MEMBER m ON LTRIM(RTRIM(m.CODE)) COLLATE DATABASE_DEFAULT = LTRIM(RTRIM(n.code)) COLLATE DATABASE_DEFAULT
 WHERE n.company_code IS NULL AND NULLIF(LTRIM(RTRIM(n.code)), '') IS NOT NULL
 GROUP BY m.ORG_ACCOUNT_ID;

-- Q7（元数据）system_dept 的全部列名与类型；若有公司类列，下一步按部门取空值账号的法人。
SELECT COLUMN_NAME, DATA_TYPE FROM [ruoyi-vue-pro].INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'system_dept' ORDER BY ORDINAL_POSITION;
SELECT TABLE_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE COLUMN_NAME = 'company_code' ORDER BY TABLE_NAME;
END
