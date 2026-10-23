-- M-29 基线补充（第二批）B01（one-rules 提供，来源 origin/claude/one-rules-context-2@943876a76 r0/m29b_baseline_supplement_queries.sql）。ops 仅加库名守卫与 SET LOCK_TIMEOUT 30000。one_evidence_ro（ADR-0037 第 5 项第 4 条），默认 READ COMMITTED，不用 NOLOCK。
SET LOCK_TIMEOUT 30000;
-- B01 列名核对：本批用到而 Q01 未核对的表与列（只取元数据）
-- 依据：Flyway 迁移与 DO 类；psa_project / system_dept 等基础表不在仓库 DDL 内，列名来自 DO。
-- 不确定度：低。缺列的查询先不跑。
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = N'dbo'
  AND (   (TABLE_NAME = N'psa_pl_ledger'     AND COLUMN_NAME IN (N'source_cost_ledger_id', N'source_id', N'source_code', N'trigger_type', N'pl_type', N'source_type', N'business_date', N'period_id', N'notax_amount', N'amount', N'project_id', N'deleted', N'reversal_of_id'))
       OR (TABLE_NAME = N'psa_cost_ledger'   AND COLUMN_NAME IN (N'id', N'source_id', N'source_code', N'source_type', N'entry_type', N'entry_status', N'fee_type', N'person_id', N'term_number', N'term_name_snap', N'approval_hours', N'business_date', N'period_id', N'notax_amount', N'project_id', N'deleted'))
       OR (TABLE_NAME = N'psa_oh_cost_pool'  AND COLUMN_NAME IN (N'source_type', N'business_date', N'period_id', N'notax_amount', N'alloc_status', N'pool_line_type', N'deleted'))
       OR (TABLE_NAME = N'psa_wip_entry'     AND COLUMN_NAME IN (N'cost_ledger_id', N'project_id', N'wip_stage', N'wip_status', N'notax_amount', N'entered_period_id', N'written_off_at', N'terminated_at', N'deleted'))
       OR (TABLE_NAME = N'psa_wip_movement'  AND COLUMN_NAME IN (N'wip_entry_id', N'cost_ledger_id', N'project_id', N'movement_type', N'notax_amount', N'period_id', N'create_time', N'deleted'))
       OR (TABLE_NAME = N'psa_wip_period_balance' AND COLUMN_NAME IN (N'period_id', N'company_code', N'wip_stage', N'wip_status', N'balance', N'snapshot_at', N'deleted'))
       OR (TABLE_NAME = N'psa_pm_project'    AND COLUMN_NAME IN (N'initiation_date', N'status', N'region'))
       OR (TABLE_NAME = N'psa_pm_contract'   AND COLUMN_NAME IN (N'id', N'project_id', N'back_date', N'deleted'))
       OR (TABLE_NAME = N'psa_pm_contract_term' AND COLUMN_NAME IN (N'contract_id', N'code', N'name', N'amount', N'final_amount', N'deleted'))
       OR (TABLE_NAME = N'psa_pm_cuvalue'    AND COLUMN_NAME IN (N'term_number', N'term_depict'))
       OR (TABLE_NAME = N'psa_contract_clause' AND COLUMN_NAME IN (N'project_id', N'term_code', N'deleted'))
       OR (TABLE_NAME = N'psa_contract_term_poc_snapshot' AND COLUMN_NAME IN (N'term_code', N'period', N'poc', N'deleted'))
       OR (TABLE_NAME = N'mdm_accounting_period' AND COLUMN_NAME IN (N'period_sequence', N'closed_time', N'company_code')))
ORDER BY TABLE_NAME, COLUMN_NAME;
END
