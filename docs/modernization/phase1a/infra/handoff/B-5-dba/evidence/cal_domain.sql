-- 工作日历取证：老系统 psa_calendar 的列与取值域（只出列元数据与分组计数，不含任何人员信息）。以取证账号 one_evidence_ro 执行（ADR-0037 第 7 项，只读）。
-- 工具在 a8_db 与 ruoyi-vue-pro 各跑一次，DB_NAME() 守卫只在 ruoyi-vue-pro 出结果。
SELECT COLUMN_NAME, DATA_TYPE FROM [ruoyi-vue-pro].INFORMATION_SCHEMA.COLUMNS
 WHERE DB_NAME() = N'ruoyi-vue-pro' AND TABLE_SCHEMA = N'dbo' AND TABLE_NAME = N'psa_calendar' ORDER BY ORDINAL_POSITION;
SELECT c.workday, c.weekend, c.holiday,
       CASE WHEN DATEDIFF(day, '19000101', CAST(c.[datetime] AS date)) % 7 < 5 THEN 'weekday' ELSE 'weekend' END AS dow_class,
       COUNT(*) AS days
  FROM [ruoyi-vue-pro].dbo.psa_calendar c
 WHERE DB_NAME() = N'ruoyi-vue-pro' AND c.[datetime] >= '20160101' AND c.[datetime] < '20270101'
 GROUP BY c.workday, c.weekend, c.holiday,
          CASE WHEN DATEDIFF(day, '19000101', CAST(c.[datetime] AS date)) % 7 < 5 THEN 'weekday' ELSE 'weekend' END
 ORDER BY dow_class, c.workday, c.weekend, c.holiday;
