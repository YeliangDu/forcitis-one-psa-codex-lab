-- FORCITIS one · C-6 STEP 0 计数（只读，只有计数，不含明细）。经 legacy_probe_run.sh 执行。
SELECT (SELECT COUNT(*) FROM dbo.system_role WHERE deleted = 0) AS roles,
       (SELECT COUNT(*) FROM dbo.system_user_role WHERE deleted = 0) AS holdings,
       (SELECT COUNT(*) FROM dbo.system_role_menu WHERE deleted = 0) AS grants;

SELECT YEAR(c.[datetime]) AS calendar_year, COUNT(*) AS days, COUNT(DISTINCT CAST(c.[datetime] AS date)) AS distinct_days
  FROM dbo.psa_calendar c
 WHERE c.[datetime] >= '20160101' AND c.[datetime] < '20270101'
 GROUP BY YEAR(c.[datetime])
 ORDER BY calendar_year;

SELECT p.VERSION_ AS definition_version, p.ID_ AS definition_id
  FROM ACT_RE_PROCDEF p
 WHERE p.KEY_ = 'TIMESHEET' AND p.VERSION_ = (SELECT MAX(VERSION_) FROM ACT_RE_PROCDEF WHERE KEY_ = 'TIMESHEET');

SELECT CONVERT(char(7), h.END_TIME_, 120) AS ended_month, COUNT(*) AS finished_instances
  FROM ACT_HI_PROCINST h
  JOIN ACT_RE_PROCDEF p ON p.ID_ = h.PROC_DEF_ID_
 WHERE p.KEY_ = 'TIMESHEET' AND p.VERSION_ = (SELECT MAX(VERSION_) FROM ACT_RE_PROCDEF WHERE KEY_ = 'TIMESHEET')
   AND h.END_TIME_ IS NOT NULL
 GROUP BY CONVERT(char(7), h.END_TIME_, 120)
 ORDER BY ended_month;

SELECT SUM(CASE WHEN h.END_TIME_ IS NOT NULL THEN 1 ELSE 0 END) AS finished_last_90_days,
       SUM(CASE WHEN h.END_TIME_ IS NULL THEN 1 ELSE 0 END) AS unfinished_any_age
  FROM ACT_HI_PROCINST h
  JOIN ACT_RE_PROCDEF p ON p.ID_ = h.PROC_DEF_ID_
 WHERE p.KEY_ = 'TIMESHEET' AND p.VERSION_ = (SELECT MAX(VERSION_) FROM ACT_RE_PROCDEF WHERE KEY_ = 'TIMESHEET')
   AND (h.END_TIME_ >= DATEADD(day, -90, GETDATE()) OR h.END_TIME_ IS NULL);
