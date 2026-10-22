-- B-31 历史用印迁入：formmain_0643 近 24 个月记录数（按 start_date 月份，只出月份与计数）及全表日期范围。one_evidence_ro（ADR-0037 第 7 项）。仅 a8_db 出结果。
SET LOCK_TIMEOUT 1000;
IF DB_NAME() = N'a8_db'
BEGIN
SELECT FORMAT(start_date, 'yyyy-MM') AS ym, COUNT(*) AS records
  FROM dbo.formmain_0643 WHERE start_date >= '2024-10-01' GROUP BY FORMAT(start_date, 'yyyy-MM') ORDER BY ym;
SELECT COUNT(*) AS total_rows, MIN(start_date) AS start_date_min, MAX(start_date) AS start_date_max, MIN(modify_date) AS modify_date_min, MAX(modify_date) AS modify_date_max FROM dbo.formmain_0643;
END
