-- E-B6（one-architecture 经 one-coordinator，2026-10-16）：近 12 个月报销、付款、采购、开票、分包金额分布（件数与 P50/P75/P90/P95/P99/最大），供审批阈值建议。只出聚合，无个人信息。
-- 金额取表单的含税或本次金额字段（文本转数值，非数值/空/≤0 另计），时间取单据创建时间；only a8_db 上下文出结果。
WITH u AS (
  SELECT N'报销' AS grp, N'日常费用报销单' AS form, CAST(TRY_CAST(REPLACE(REPLACE(CAST(F.field0100 AS nvarchar(60)), N',', N''), N' ', N'') AS decimal(18,2)) AS float) AS amt, F.finishedflag AS fin FROM a8_db.dbo.formmain_0602 F WHERE DB_NAME() = N'a8_db' AND F.start_date >= DATEADD(MONTH, -12, GETDATE())
  UNION ALL SELECT N'报销' AS grp, N'差旅费用报销单' AS form, CAST(TRY_CAST(REPLACE(REPLACE(CAST(F.field0102 AS nvarchar(60)), N',', N''), N' ', N'') AS decimal(18,2)) AS float) AS amt, F.finishedflag AS fin FROM a8_db.dbo.formmain_0604 F WHERE DB_NAME() = N'a8_db' AND F.start_date >= DATEADD(MONTH, -12, GETDATE())
  UNION ALL SELECT N'报销' AS grp, N'业务招待费用报销单' AS form, CAST(TRY_CAST(REPLACE(REPLACE(CAST(F.field0100 AS nvarchar(60)), N',', N''), N' ', N'') AS decimal(18,2)) AS float) AS amt, F.finishedflag AS fin FROM a8_db.dbo.formmain_1121 F WHERE DB_NAME() = N'a8_db' AND F.start_date >= DATEADD(MONTH, -12, GETDATE())
  UNION ALL SELECT N'付款' AS grp, N'付款申请单' AS form, CAST(TRY_CAST(REPLACE(REPLACE(CAST(F.field0101 AS nvarchar(60)), N',', N''), N' ', N'') AS decimal(18,2)) AS float) AS amt, F.finishedflag AS fin FROM a8_db.dbo.formmain_0424 F WHERE DB_NAME() = N'a8_db' AND F.start_date >= DATEADD(MONTH, -12, GETDATE())
  UNION ALL SELECT N'付款' AS grp, N'付款申请单-财务' AS form, CAST(TRY_CAST(REPLACE(REPLACE(CAST(F.field0101 AS nvarchar(60)), N',', N''), N' ', N'') AS decimal(18,2)) AS float) AS amt, F.finishedflag AS fin FROM a8_db.dbo.formmain_1344 F WHERE DB_NAME() = N'a8_db' AND F.start_date >= DATEADD(MONTH, -12, GETDATE())
  UNION ALL SELECT N'付款' AS grp, N'付款申请单-人事' AS form, CAST(TRY_CAST(REPLACE(REPLACE(CAST(F.field0101 AS nvarchar(60)), N',', N''), N' ', N'') AS decimal(18,2)) AS float) AS amt, F.finishedflag AS fin FROM a8_db.dbo.formmain_0644 F WHERE DB_NAME() = N'a8_db' AND F.start_date >= DATEADD(MONTH, -12, GETDATE())
  UNION ALL SELECT N'付款' AS grp, N'特殊款项付款申请单' AS form, CAST(TRY_CAST(REPLACE(REPLACE(CAST(F.field0101 AS nvarchar(60)), N',', N''), N' ', N'') AS decimal(18,2)) AS float) AS amt, F.finishedflag AS fin FROM a8_db.dbo.formmain_1211 F WHERE DB_NAME() = N'a8_db' AND F.start_date >= DATEADD(MONTH, -12, GETDATE())
  UNION ALL SELECT N'采购' AS grp, N'采购合同申请' AS form, CAST(TRY_CAST(REPLACE(REPLACE(CAST(F.field0063 AS nvarchar(60)), N',', N''), N' ', N'') AS decimal(18,2)) AS float) AS amt, F.finishedflag AS fin FROM a8_db.dbo.formmain_0616 F WHERE DB_NAME() = N'a8_db' AND F.start_date >= DATEADD(MONTH, -12, GETDATE())
  UNION ALL SELECT N'采购' AS grp, N'采购请购单(最终报价)' AS form, CAST(TRY_CAST(REPLACE(REPLACE(CAST(F.field0020 AS nvarchar(60)), N',', N''), N' ', N'') AS decimal(18,2)) AS float) AS amt, F.finishedflag AS fin FROM a8_db.dbo.formmain_1232 F WHERE DB_NAME() = N'a8_db' AND F.start_date >= DATEADD(MONTH, -12, GETDATE())
  UNION ALL SELECT N'开票' AS grp, N'项目开票申请' AS form, CAST(TRY_CAST(REPLACE(REPLACE(CAST(F.field0136 AS nvarchar(60)), N',', N''), N' ', N'') AS decimal(18,2)) AS float) AS amt, F.finishedflag AS fin FROM a8_db.dbo.formmain_0399 F WHERE DB_NAME() = N'a8_db' AND F.start_date >= DATEADD(MONTH, -12, GETDATE())
  UNION ALL SELECT N'开票' AS grp, N'首付款开票申请' AS form, CAST(TRY_CAST(REPLACE(REPLACE(CAST(F.field0133 AS nvarchar(60)), N',', N''), N' ', N'') AS decimal(18,2)) AS float) AS amt, F.finishedflag AS fin FROM a8_db.dbo.formmain_0564 F WHERE DB_NAME() = N'a8_db' AND F.start_date >= DATEADD(MONTH, -12, GETDATE())
  UNION ALL SELECT N'开票' AS grp, N'预收款单开票' AS form, CAST(TRY_CAST(REPLACE(REPLACE(CAST(F.field0262 AS nvarchar(60)), N',', N''), N' ', N'') AS decimal(18,2)) AS float) AS amt, F.finishedflag AS fin FROM a8_db.dbo.formmain_0628 F WHERE DB_NAME() = N'a8_db' AND F.start_date >= DATEADD(MONTH, -12, GETDATE())
  UNION ALL SELECT N'分包' AS grp, N'项目委外采购申请' AS form, CAST(TRY_CAST(REPLACE(REPLACE(CAST(F.field0063 AS nvarchar(60)), N',', N''), N' ', N'') AS decimal(18,2)) AS float) AS amt, F.finishedflag AS fin FROM a8_db.dbo.formmain_1349 F WHERE DB_NAME() = N'a8_db' AND F.start_date >= DATEADD(MONTH, -12, GETDATE())
  UNION ALL SELECT N'分包' AS grp, N'项目委外采购合同' AS form, CAST(TRY_CAST(REPLACE(REPLACE(CAST(F.field0063 AS nvarchar(60)), N',', N''), N' ', N'') AS decimal(18,2)) AS float) AS amt, F.finishedflag AS fin FROM a8_db.dbo.formmain_0838 F WHERE DB_NAME() = N'a8_db' AND F.start_date >= DATEADD(MONTH, -12, GETDATE())
  UNION ALL SELECT N'付款' AS grp, N'老系统 付款记录(psa_cost_summary_payment.pay_amount)' AS form, CAST(pay_amount AS float) AS amt, 1 AS fin FROM [ruoyi-vue-pro].dbo.psa_cost_summary_payment WHERE DB_NAME() = N'a8_db' AND pay_date >= DATEADD(MONTH, -12, GETDATE())
  UNION ALL SELECT N'分包' AS grp, N'老系统 分包(psa_wip_outsourcing.amount)' AS form, CAST(amount AS float) AS amt, 1 AS fin FROM [ruoyi-vue-pro].dbo.psa_wip_outsourcing WHERE DB_NAME() = N'a8_db' AND complete_date >= DATEADD(MONTH, -12, GETDATE())
),
v AS (SELECT grp, form, amt, fin FROM u),
ok AS (SELECT * FROM v WHERE amt > 0)
SELECT DISTINCT grp, form, N'all' AS scope,
       (SELECT COUNT(*) FROM v v2 WHERE v2.grp = ok.grp AND v2.form = ok.form) AS n_all,
       COUNT(*) OVER (PARTITION BY grp, form) AS n_amount_positive,
       CAST(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY amt) OVER (PARTITION BY grp, form) AS decimal(18,0)) AS p50,
       CAST(PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY amt) OVER (PARTITION BY grp, form) AS decimal(18,0)) AS p75,
       CAST(PERCENTILE_CONT(0.9) WITHIN GROUP (ORDER BY amt) OVER (PARTITION BY grp, form) AS decimal(18,0)) AS p90,
       CAST(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY amt) OVER (PARTITION BY grp, form) AS decimal(18,0)) AS p95,
       CAST(PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY amt) OVER (PARTITION BY grp, form) AS decimal(18,0)) AS p99,
       CAST(MAX(amt) OVER (PARTITION BY grp, form) AS decimal(18,0)) AS max_amt
  FROM ok
UNION ALL
SELECT DISTINCT grp, N'(合并)', N'all',
       (SELECT COUNT(*) FROM v v2 WHERE v2.grp = ok.grp), COUNT(*) OVER (PARTITION BY grp),
       CAST(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY amt) OVER (PARTITION BY grp) AS decimal(18,0)),
       CAST(PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY amt) OVER (PARTITION BY grp) AS decimal(18,0)),
       CAST(PERCENTILE_CONT(0.9) WITHIN GROUP (ORDER BY amt) OVER (PARTITION BY grp) AS decimal(18,0)),
       CAST(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY amt) OVER (PARTITION BY grp) AS decimal(18,0)),
       CAST(PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY amt) OVER (PARTITION BY grp) AS decimal(18,0)),
       CAST(MAX(amt) OVER (PARTITION BY grp) AS decimal(18,0))
  FROM ok
ORDER BY grp, form;
GO
