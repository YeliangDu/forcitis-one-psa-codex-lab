-- E-B6 前置二：报销、付款、开票、分包相关主表的全部列名与类型，及行数（只看元数据与计数，不读数据）。只在 a8_db 上下文出结果。
SELECT c.TABLE_NAME, c.COLUMN_NAME, c.DATA_TYPE
  FROM [ruoyi-vue-pro].INFORMATION_SCHEMA.COLUMNS c
 WHERE DB_NAME() = N'a8_db'
   AND c.TABLE_NAME IN (N'psa_legacy_platform_expense_group', N'psa_platform_expense_group', N'psa_legacy_manual_expense_group', N'psa_manual_expense_group',
                        N'psa_oa_payment_detail', N'psa_cost_summary_payment', N'psa_expense_invoice', N'psa_wip_outsourcing')
 ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION;
GO
SELECT N'rows' AS k, N'psa_legacy_platform_expense_group' AS t, COUNT(*) AS n FROM [ruoyi-vue-pro].dbo.psa_legacy_platform_expense_group WHERE DB_NAME() = N'a8_db'
UNION ALL SELECT N'rows', N'psa_platform_expense_group', COUNT(*) FROM [ruoyi-vue-pro].dbo.psa_platform_expense_group WHERE DB_NAME() = N'a8_db'
UNION ALL SELECT N'rows', N'psa_legacy_manual_expense_group', COUNT(*) FROM [ruoyi-vue-pro].dbo.psa_legacy_manual_expense_group WHERE DB_NAME() = N'a8_db'
UNION ALL SELECT N'rows', N'psa_manual_expense_group', COUNT(*) FROM [ruoyi-vue-pro].dbo.psa_manual_expense_group WHERE DB_NAME() = N'a8_db'
UNION ALL SELECT N'rows', N'psa_oa_payment_detail', COUNT(*) FROM [ruoyi-vue-pro].dbo.psa_oa_payment_detail WHERE DB_NAME() = N'a8_db'
UNION ALL SELECT N'rows', N'psa_cost_summary_payment', COUNT(*) FROM [ruoyi-vue-pro].dbo.psa_cost_summary_payment WHERE DB_NAME() = N'a8_db'
UNION ALL SELECT N'rows', N'psa_expense_invoice', COUNT(*) FROM [ruoyi-vue-pro].dbo.psa_expense_invoice WHERE DB_NAME() = N'a8_db'
UNION ALL SELECT N'rows', N'psa_wip_outsourcing', COUNT(*) FROM [ruoyi-vue-pro].dbo.psa_wip_outsourcing WHERE DB_NAME() = N'a8_db';
GO
