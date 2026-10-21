-- 工作日历取证 3：周一至五、workday='2' 却没有节假日编码（holiday='10'）的日期按年、按月的计数（只出计数，不含人员信息）。one_evidence_ro 只读（ADR-0037 第 7 项）。
SELECT YEAR(c.[datetime]) AS yr, MONTH(c.[datetime]) AS mon, COUNT(*) AS days
  FROM [ruoyi-vue-pro].dbo.psa_calendar c
 WHERE DB_NAME() = N'ruoyi-vue-pro' AND c.[datetime] >= '20160101' AND c.[datetime] < '20270101'
   AND DATEDIFF(day, '19000101', CAST(c.[datetime] AS date)) % 7 < 5 AND c.workday = '2' AND c.holiday = '10'
 GROUP BY YEAR(c.[datetime]), MONTH(c.[datetime]) ORDER BY yr, mon;
