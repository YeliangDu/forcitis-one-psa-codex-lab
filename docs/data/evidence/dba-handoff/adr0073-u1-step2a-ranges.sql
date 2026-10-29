-- ADR-0073 U1 第二步 a：五个对象的总行数与时间列最早、最晚值（只出一行聚合，不出明细）。以取证账号 one_evidence_ro 执行（ADR-0037）。
SET LOCK_TIMEOUT 1000;
IF DB_NAME() = N'a8_db'
BEGIN
SELECT N'AI_ORG_MEMBER_CHANGE' AS object_name, COUNT(*) AS total_rows, MIN(CREATE_TIME) AS create_time_min, MAX(CREATE_TIME) AS create_time_max FROM dbo.AI_ORG_MEMBER_CHANGE;
SELECT N'AI_ORG_CHANGE_RECORD' AS object_name, COUNT(*) AS total_rows, MIN(CREATE_TIME) AS create_time_min, MAX(CREATE_TIME) AS create_time_max FROM dbo.AI_ORG_CHANGE_RECORD;
SELECT N'ODS_MEMBER_CHANGE' AS object_name, COUNT(*) AS total_rows, MIN(CREATE_TIME) AS create_time_min, MAX(CREATE_TIME) AS create_time_max,
       MIN(UPDATE_TIME) AS update_time_min, MAX(UPDATE_TIME) AS update_time_max, MIN(CHANGE_TIME) AS change_time_min, MAX(CHANGE_TIME) AS change_time_max FROM dbo.ODS_MEMBER_CHANGE;
SELECT N'ODS_ORG_CHANGE' AS object_name, COUNT(*) AS total_rows, MIN(CHANGE_TIME) AS change_time_min, MAX(CHANGE_TIME) AS change_time_max FROM dbo.ODS_ORG_CHANGE;
END
IF DB_NAME() = N'ruoyi-vue-pro'
BEGIN
SELECT N'psa_employee_transfer_record' AS object_name, COUNT(*) AS total_rows, MIN(start_date) AS start_date_min, MAX(start_date) AS start_date_max,
       MIN(end_date) AS end_date_min, MAX(end_date) AS end_date_max FROM dbo.psa_employee_transfer_record;
END
