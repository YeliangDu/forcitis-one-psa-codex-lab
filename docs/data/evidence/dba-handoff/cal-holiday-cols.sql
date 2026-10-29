-- 工作日历取证 2：holiday* 各列取值与 workday、周内类别的交叉分布（只出分组计数）。one_evidence_ro 只读（ADR-0037 第 7 项）；三段名使 a8_db 一侧也能编译，DB_NAME() 守卫只在 ruoyi-vue-pro 出结果。
SELECT 'holiday' AS col, c.holiday AS val, c.holiday_cn AS val_cn, c.workday, CASE WHEN DATEDIFF(day, '19000101', CAST(c.[datetime] AS date)) % 7 < 5 THEN 'weekday' ELSE 'weekend' END AS dow_class, COUNT(*) AS days
  FROM [ruoyi-vue-pro].dbo.psa_calendar c
 WHERE DB_NAME() = N'ruoyi-vue-pro' AND c.[datetime] >= '20160101' AND c.[datetime] < '20270101'
 GROUP BY c.holiday, c.holiday_cn, c.workday, CASE WHEN DATEDIFF(day, '19000101', CAST(c.[datetime] AS date)) % 7 < 5 THEN 'weekday' ELSE 'weekend' END
 ORDER BY c.holiday, c.workday, dow_class;
SELECT 'holiday_or' AS col, c.holiday_or AS val, c.holiday_or_cn AS val_cn, c.workday, CASE WHEN DATEDIFF(day, '19000101', CAST(c.[datetime] AS date)) % 7 < 5 THEN 'weekday' ELSE 'weekend' END AS dow_class, COUNT(*) AS days
  FROM [ruoyi-vue-pro].dbo.psa_calendar c
 WHERE DB_NAME() = N'ruoyi-vue-pro' AND c.[datetime] >= '20160101' AND c.[datetime] < '20270101'
 GROUP BY c.holiday_or, c.holiday_or_cn, c.workday, CASE WHEN DATEDIFF(day, '19000101', CAST(c.[datetime] AS date)) % 7 < 5 THEN 'weekday' ELSE 'weekend' END
 ORDER BY c.holiday_or, c.workday, dow_class;
SELECT 'holiday_overtime' AS col, c.holiday_overtime AS val, c.holiday_overtime_cn AS val_cn, c.workday, CASE WHEN DATEDIFF(day, '19000101', CAST(c.[datetime] AS date)) % 7 < 5 THEN 'weekday' ELSE 'weekend' END AS dow_class, COUNT(*) AS days
  FROM [ruoyi-vue-pro].dbo.psa_calendar c
 WHERE DB_NAME() = N'ruoyi-vue-pro' AND c.[datetime] >= '20160101' AND c.[datetime] < '20270101'
 GROUP BY c.holiday_overtime, c.holiday_overtime_cn, c.workday, CASE WHEN DATEDIFF(day, '19000101', CAST(c.[datetime] AS date)) % 7 < 5 THEN 'weekday' ELSE 'weekend' END
 ORDER BY c.holiday_overtime, c.workday, dow_class;
SELECT 'holiday_today' AS col, c.holiday_today AS val, c.holiday_today_cn AS val_cn, c.workday, CASE WHEN DATEDIFF(day, '19000101', CAST(c.[datetime] AS date)) % 7 < 5 THEN 'weekday' ELSE 'weekend' END AS dow_class, COUNT(*) AS days
  FROM [ruoyi-vue-pro].dbo.psa_calendar c
 WHERE DB_NAME() = N'ruoyi-vue-pro' AND c.[datetime] >= '20160101' AND c.[datetime] < '20270101'
 GROUP BY c.holiday_today, c.holiday_today_cn, c.workday, CASE WHEN DATEDIFF(day, '19000101', CAST(c.[datetime] AS date)) % 7 < 5 THEN 'weekday' ELSE 'weekend' END
 ORDER BY c.holiday_today, c.workday, dow_class;
SELECT 'holiday_legal' AS col, c.holiday_legal AS val, c.holiday_legal_cn AS val_cn, c.workday, CASE WHEN DATEDIFF(day, '19000101', CAST(c.[datetime] AS date)) % 7 < 5 THEN 'weekday' ELSE 'weekend' END AS dow_class, COUNT(*) AS days
  FROM [ruoyi-vue-pro].dbo.psa_calendar c
 WHERE DB_NAME() = N'ruoyi-vue-pro' AND c.[datetime] >= '20160101' AND c.[datetime] < '20270101'
 GROUP BY c.holiday_legal, c.holiday_legal_cn, c.workday, CASE WHEN DATEDIFF(day, '19000101', CAST(c.[datetime] AS date)) % 7 < 5 THEN 'weekday' ELSE 'weekend' END
 ORDER BY c.holiday_legal, c.workday, dow_class;
SELECT 'holiday_recess' AS col, c.holiday_recess AS val, c.holiday_recess_cn AS val_cn, c.workday, CASE WHEN DATEDIFF(day, '19000101', CAST(c.[datetime] AS date)) % 7 < 5 THEN 'weekday' ELSE 'weekend' END AS dow_class, COUNT(*) AS days
  FROM [ruoyi-vue-pro].dbo.psa_calendar c
 WHERE DB_NAME() = N'ruoyi-vue-pro' AND c.[datetime] >= '20160101' AND c.[datetime] < '20270101'
 GROUP BY c.holiday_recess, c.holiday_recess_cn, c.workday, CASE WHEN DATEDIFF(day, '19000101', CAST(c.[datetime] AS date)) % 7 < 5 THEN 'weekday' ELSE 'weekend' END
 ORDER BY c.holiday_recess, c.workday, dow_class;
