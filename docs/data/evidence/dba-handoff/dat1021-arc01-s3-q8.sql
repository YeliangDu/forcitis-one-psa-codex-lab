-- DAT-1021-ARC-01 第三步 Q8：含 company_code 列的各表该列的不同取值个数与前 6 个取值的行数（公司代码不是个人信息；表名来自 s3 第一遍的 INFORMATION_SCHEMA 列表）。以取证账号 one_evidence_ro 执行（ADR-0037）。
SET LOCK_TIMEOUT 1000;
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.annual_bonus_detail GROUP BY company_code)
SELECT TOP (6) N'annual_bonus_detail' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.mdm_accounting_period GROUP BY company_code)
SELECT TOP (6) N'mdm_accounting_period' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.mdm_bill_rate GROUP BY company_code)
SELECT TOP (6) N'mdm_bill_rate' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.mdm_bill_rate_detail GROUP BY company_code)
SELECT TOP (6) N'mdm_bill_rate_detail' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.mdm_standard_cost_rate GROUP BY company_code)
SELECT TOP (6) N'mdm_standard_cost_rate' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.mdm_standard_cost_rate_detail GROUP BY company_code)
SELECT TOP (6) N'mdm_standard_cost_rate_detail' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.monthly_bonus_accrual GROUP BY company_code)
SELECT TOP (6) N'monthly_bonus_accrual' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_carry_batch GROUP BY company_code)
SELECT TOP (6) N'psa_carry_batch' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_carry_period_progress GROUP BY company_code)
SELECT TOP (6) N'psa_carry_period_progress' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_collect_recon GROUP BY company_code)
SELECT TOP (6) N'psa_collect_recon' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_cost_allocation GROUP BY company_code)
SELECT TOP (6) N'psa_cost_allocation' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_cost_collection_batch GROUP BY company_code)
SELECT TOP (6) N'psa_cost_collection_batch' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_cost_entry_error GROUP BY company_code)
SELECT TOP (6) N'psa_cost_entry_error' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_cost_ledger GROUP BY company_code)
SELECT TOP (6) N'psa_cost_ledger' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_cost_recon_disposition GROUP BY company_code)
SELECT TOP (6) N'psa_cost_recon_disposition' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_cost_source_claim GROUP BY company_code)
SELECT TOP (6) N'psa_cost_source_claim' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_cost_summary GROUP BY company_code)
SELECT TOP (6) N'psa_cost_summary' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_cost_summary_daily GROUP BY company_code)
SELECT TOP (6) N'psa_cost_summary_daily' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_cost_summary_daily_bak_orphans_20261012 GROUP BY company_code)
SELECT TOP (6) N'psa_cost_summary_daily_bak_orphans_20261012' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_cost_summary_entertainment GROUP BY company_code)
SELECT TOP (6) N'psa_cost_summary_entertainment' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_cost_summary_entertainment_bak_orphans_20261012 GROUP BY company_code)
SELECT TOP (6) N'psa_cost_summary_entertainment_bak_orphans_20261012' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_cost_summary_history GROUP BY company_code)
SELECT TOP (6) N'psa_cost_summary_history' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_cost_summary_history_bak_orphans_20261012 GROUP BY company_code)
SELECT TOP (6) N'psa_cost_summary_history_bak_orphans_20261012' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_cost_summary_new GROUP BY company_code)
SELECT TOP (6) N'psa_cost_summary_new' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_cost_summary_notratified GROUP BY company_code)
SELECT TOP (6) N'psa_cost_summary_notratified' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_cost_summary_payment GROUP BY company_code)
SELECT TOP (6) N'psa_cost_summary_payment' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_cost_summary_payment_bak_orphans_20261012 GROUP BY company_code)
SELECT TOP (6) N'psa_cost_summary_payment_bak_orphans_20261012' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_cost_summary_special GROUP BY company_code)
SELECT TOP (6) N'psa_cost_summary_special' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_cost_summary_special_bak_orphans_20261012 GROUP BY company_code)
SELECT TOP (6) N'psa_cost_summary_special_bak_orphans_20261012' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_cost_summary_timesheet GROUP BY company_code)
SELECT TOP (6) N'psa_cost_summary_timesheet' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_cost_summary_travel GROUP BY company_code)
SELECT TOP (6) N'psa_cost_summary_travel' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_cost_summary_travel_bak_orphans_20261012 GROUP BY company_code)
SELECT TOP (6) N'psa_cost_summary_travel_bak_orphans_20261012' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_cost_summary_zero_hour_salary GROUP BY company_code)
SELECT TOP (6) N'psa_cost_summary_zero_hour_salary' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_employee_transfer_record GROUP BY company_code)
SELECT TOP (6) N'psa_employee_transfer_record' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_labor_reclass_run GROUP BY company_code)
SELECT TOP (6) N'psa_labor_reclass_run' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_labor_reclass_voucher_reg GROUP BY company_code)
SELECT TOP (6) N'psa_labor_reclass_voucher_reg' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_month_statement_approval GROUP BY company_code)
SELECT TOP (6) N'psa_month_statement_approval' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_oh_cost_pool GROUP BY company_code)
SELECT TOP (6) N'psa_oh_cost_pool' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_opening_range_run GROUP BY company_code)
SELECT TOP (6) N'psa_opening_range_run' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_opening_revenue_event GROUP BY company_code)
SELECT TOP (6) N'psa_opening_revenue_event' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_opportunity_expense GROUP BY company_code)
SELECT TOP (6) N'psa_opportunity_expense' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_pl_ledger GROUP BY company_code)
SELECT TOP (6) N'psa_pl_ledger' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_platform_statement_source_run GROUP BY company_code)
SELECT TOP (6) N'psa_platform_statement_source_run' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_pm_cuvalue GROUP BY company_code)
SELECT TOP (6) N'psa_pm_cuvalue' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_revenue_posting_rule GROUP BY company_code)
SELECT TOP (6) N'psa_revenue_posting_rule' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_time_sheet GROUP BY company_code)
SELECT TOP (6) N'psa_time_sheet' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_time_sheet_works GROUP BY company_code)
SELECT TOP (6) N'psa_time_sheet_works' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_time_sheet_works_error GROUP BY company_code)
SELECT TOP (6) N'psa_time_sheet_works_error' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_voucher GROUP BY company_code)
SELECT TOP (6) N'psa_voucher' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_wip_cost_account GROUP BY company_code)
SELECT TOP (6) N'psa_wip_cost_account' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_wip_entry GROUP BY company_code)
SELECT TOP (6) N'psa_wip_entry' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_wip_manual_writeoff_list GROUP BY company_code)
SELECT TOP (6) N'psa_wip_manual_writeoff_list' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_wip_movement GROUP BY company_code)
SELECT TOP (6) N'psa_wip_movement' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.psa_wip_period_balance GROUP BY company_code)
SELECT TOP (6) N'psa_wip_period_balance' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
WITH g AS (SELECT company_code, COUNT(*) AS n FROM dbo.system_users GROUP BY company_code)
SELECT TOP (6) N'system_users' AS table_name, (SELECT COUNT(*) FROM g) AS distinct_codes, company_code, n AS row_count FROM g ORDER BY n DESC;
END
