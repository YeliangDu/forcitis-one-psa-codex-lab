-- one-data Q-02 补充取证第 1 步：员工档案里枚举字段的显示名从哪来（只看表与列的元数据，不读数据）。只在 a8_db 上下文出结果。
SELECT c.TABLE_NAME, c.COLUMN_NAME, c.DATA_TYPE
  FROM a8_db.INFORMATION_SCHEMA.COLUMNS c
 WHERE DB_NAME() = N'a8_db' AND c.TABLE_NAME IN (N'CTP_ENUM_ITEM', N'CTP_ENUM', N'CTP_ENUM_ITEM_REF')
 ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION;
GO
SELECT N'legacy col' AS k, c.TABLE_NAME, c.COLUMN_NAME, c.DATA_TYPE
  FROM [ruoyi-vue-pro].INFORMATION_SCHEMA.COLUMNS c
 WHERE DB_NAME() = N'a8_db' AND ((c.TABLE_NAME = N'mdm_bill_rate_detail' AND c.COLUMN_NAME IN (N'job_level_code', N'salary_grade_no'))
    OR (c.TABLE_NAME = N'psa_employee_transfer_record' AND c.COLUMN_NAME = N'job_level_code') OR (c.TABLE_NAME = N'psa_time_sheet' AND c.COLUMN_NAME IN (N'post_id', N'deleted')));
GO
